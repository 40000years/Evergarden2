"""Check Forge elite isolation against the real locally available LevelledMobs JAR.

    python tests/forge/run_levelledmobs.py --name lm1 --forge output/forge-build/evergarden-forge.jar

Creates an isolated local Paper server and does not modify the template or build Forge.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
import build_plugin_jars as plugin_jars  # noqa: E402


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--name", required=True)
    parser.add_argument("--forge", type=Path, default=ROOT / "output/forge-build/evergarden-forge.jar")
    parser.add_argument("--template", type=Path, default=ROOT / "output/entry-items-check-20261031")
    parser.add_argument("--levelledmobs", type=Path)
    args = parser.parse_args()
    if not args.name.replace("-", "").replace("_", "").isalnum():
        parser.error("--name must contain only letters, numbers, hyphens or underscores")
    levelled = args.levelledmobs
    if levelled is None:
        available = sorted((args.template / "plugins").glob("LevelledMobs-*.jar"))
        if len(available) != 1:
            parser.error("Provide --levelledmobs when the template has no unique LM JAR")
        levelled = available[0]
    server = ROOT / "output/forge-probe" / ("paper-" + args.name)
    if server.exists():
        parser.error("Target already exists: " + str(server))
    inputs = [args.forge, levelled, Path(__file__), Path(__file__).with_name("ForgeLevelledMobsProbe.java")]
    inputs += sorted(p for p in (ROOT / "evergarden-forge/src").rglob("*") if p.is_file())
    source_hashes = {str(p.resolve().relative_to(ROOT)): sha(p) for p in inputs}
    server.mkdir(parents=True)
    for name in ("libraries", "versions", "cache"):
        source = (args.template / name).resolve()
        if os.name == "nt":
            command = "New-Item -ItemType Junction -Path '" + str(server / name).replace("'", "''")
            command += "' -Target '" + str(source).replace("'", "''") + "' | Out-Null"
            subprocess.run(["powershell", "-NoProfile", "-Command", command], check=True)
        else:
            (server / name).symlink_to(source, target_is_directory=True)
    shutil.copy2(args.template / "paper.jar", server / "paper.jar")
    plugins = server / "plugins"
    plugins.mkdir()
    shutil.copy2(args.forge, plugins / "evergarden-forge.jar")
    shutil.copy2(levelled, plugins / "LevelledMobs.jar")
    forge_data = plugins / "EvergardenForge"
    forge_data.mkdir()
    (forge_data / "config.yml").write_text(
        "config-version: 7\nresource-pack:\n  port: 0\n  public-host: 127.0.0.1\n"
        "elites:\n  chance: 0\n", encoding="utf8")
    lm_data = plugins / "LevelledMobs"
    shutil.copytree(args.template / "plugins/LevelledMobs", lm_data,
                    ignore=shutil.ignore_patterns("logs", "*.log", "debug*"))
    settings = lm_data / "settings.yml"
    settings.write_text(settings.read_text(encoding="utf8").replace("use-update-checker: true", "use-update-checker: false"),
                        encoding="utf8")
    rules = lm_data / "rules.yml"
    original_rules = rules.read_text(encoding="utf8")
    # Keep the installed version's actual preset definitions while making this fixture deterministic.
    rules.write_text(original_rules.split("\ndefault-rule:", 1)[0] + "\n" + """default-rule:
  use-preset: [challenge-silver, lvlstrategy-random, nametag-using-numbers]
  conditions:
    worlds: ['*']
    entities: ['*']
    mob-customname-status: EITHER
    mob-tamed-status: EITHER
  settings:
    minLevel: 1
    maxLevel: 50
    construct-level: '1'
    use-custom-item-drops-for-mobs: false
custom-rules: []
""" + "\n" + re.search(r"(?m)^file-version:.*$", original_rules).group(0) + "\n", encoding="utf8")

    classpath = [str(plugins / "evergarden-forge.jar"), str(plugins / "LevelledMobs.jar"),
                 *plugin_jars.pinned_classpath(plugin_jars.EVERGARDEN_ARGS)]
    classes = server / "probe-classes"
    classes.mkdir()
    compiler_args = ["--release", "25", "-proc:none", "-encoding", "UTF-8", "-cp", os.pathsep.join(classpath),
                     "-d", str(classes), str(Path(__file__).with_name("ForgeLevelledMobsProbe.java"))]
    argfile = server / "probe-javac.args"
    argfile.write_text("\n".join('"' + value.replace("\\", "/") + '"' for value in compiler_args), encoding="utf8")
    subprocess.run(["java", str(ROOT / "tools/CompileJava.java"), str(argfile)], check=True)
    (classes / "plugin.yml").write_text("""name: ForgeLevelledMobsProbe
version: '1.0'
main: ForgeLevelledMobsProbe
api-version: '26.2'
depend: [EvergardenForge, LevelledMobs]
""", encoding="utf8")
    subprocess.run(["jar", "--create", "--file", str(plugins / "forge-levelledmobs-probe.jar"), "-C", str(classes), "."], check=True)
    (server / "eula.txt").write_text("eula=true\n", encoding="utf8")
    (server / "server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nenforce-secure-profile=false\n"
        "view-distance=2\nsimulation-distance=2\nlevel-name=probe_base\nlevel-type=minecraft:flat\n"
        "generate-structures=false\nspawn-protection=0\npause-when-empty-seconds=-1\nmax-tick-time=240000\n",
        encoding="utf8")
    with (server / "console.log").open("w", encoding="utf8") as output:
        subprocess.run(["java", "-Xms512M", "-Xmx2G", "-Dterminal.jline=false", "-Dterminal.ansi=false",
                        "-jar", "paper.jar", "--nogui", "--noconsole"], cwd=server, stdout=output,
                       stderr=subprocess.STDOUT, timeout=300, check=True,
                       creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
    result = server / "forge-levelledmobs-result.txt"
    if not result.exists():
        raise RuntimeError("Probe did not complete: " + str(server / "console.log"))
    summary = result.read_text(encoding="utf8")
    sys.stdout.reconfigure(encoding="utf8")
    print(summary)
    if not summary.startswith("PASS"):
        raise SystemExit(1)
    assert all(sha(ROOT / path) == digest for path, digest in source_hashes.items()), "Source changed during test"
    (server / "forge-levelledmobs-test-receipt.json").write_text(json.dumps({
        "result": summary.splitlines()[0], "forge_sha256": sha(args.forge),
        "levelledmobs_sha256": sha(levelled), "sources": source_hashes,
    }, indent=2) + "\n", encoding="utf8")


if __name__ == "__main__":
    main()
