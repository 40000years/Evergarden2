"""Measure actual Charm particle packets and verify combat on isolated local Paper.

    python tests/forge/run_charm_particles.py --name charm-particles-1 --forge dist/evergarden-forge.jar

--snapshot records densities without enforcing the new visual limit, for an old-JAR comparison.
This runner never builds Forge or modifies the server template.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
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
    parser.add_argument("--snapshot", action="store_true")
    args = parser.parse_args()
    if not args.name.replace("-", "").replace("_", "").isalnum():
        parser.error("--name must contain only letters, numbers, hyphens or underscores")
    candidate = args.forge.resolve()
    inputs = [candidate, Path(__file__), Path(__file__).with_name("ForgeCharmParticleProbe.java")]
    hashes = {str(path.resolve().relative_to(ROOT)): sha(path) for path in inputs}
    server = ROOT / "output/forge-probe" / ("paper-" + args.name)
    if server.exists():
        parser.error("Target already exists: " + str(server))
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
    shutil.copy2(candidate, plugins / "evergarden-forge.jar")
    forge_data = plugins / "EvergardenForge"
    forge_data.mkdir()
    (forge_data / "config.yml").write_text(
        "config-version: 7\nresource-pack:\n  port: 0\n  public-host: 127.0.0.1\n"
        "elites:\n  chance: 0\n", encoding="utf8")
    classpath = [str(plugins / "evergarden-forge.jar"), *plugin_jars.pinned_classpath(plugin_jars.EVERGARDEN_ARGS)]
    classes = server / "probe-classes"
    classes.mkdir()
    compiler_args = ["--release", "25", "-proc:none", "-encoding", "UTF-8", "-cp", os.pathsep.join(classpath),
                     "-d", str(classes), str(Path(__file__).with_name("ForgeCharmParticleProbe.java"))]
    argfile = server / "probe-javac.args"
    argfile.write_text("\n".join('"' + value.replace("\\", "/") + '"' for value in compiler_args), encoding="utf8")
    subprocess.run(["java", str(ROOT / "tools/CompileJava.java"), str(argfile)], check=True)
    (classes / "plugin.yml").write_text("""name: ForgeCharmParticleProbe
version: '1.0'
main: ForgeCharmParticleProbe
api-version: '26.2'
depend: [EvergardenForge]
""", encoding="utf8")
    subprocess.run(["jar", "--create", "--file", str(plugins / "forge-charm-particle-probe.jar"), "-C", str(classes), "."], check=True)
    (server / "eula.txt").write_text("eula=true\n", encoding="utf8")
    (server / "server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nenforce-secure-profile=false\n"
        "view-distance=2\nsimulation-distance=2\nlevel-name=probe_base\nlevel-type=minecraft:flat\n"
        "generate-structures=false\nspawn-protection=0\npause-when-empty-seconds=-1\nmax-tick-time=240000\n",
        encoding="utf8")
    java_args = ["java", "-Xms512M", "-Xmx2G", "-Dterminal.jline=false", "-Dterminal.ansi=false"]
    if args.snapshot:
        java_args.append("-Dforge.particle.snapshot=true")
    with (server / "console.log").open("w", encoding="utf8") as output:
        subprocess.run([*java_args, "-jar", "paper.jar", "--nogui", "--noconsole"], cwd=server,
                       stdout=output, stderr=subprocess.STDOUT, timeout=240, check=True,
                       creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
    result = server / "forge-charm-particles-result.txt"
    if not result.exists():
        raise RuntimeError("Probe did not complete: " + str(server / "console.log"))
    summary = result.read_text(encoding="utf8")
    sys.stdout.reconfigure(encoding="utf8")
    print(summary)
    if not summary.startswith("PASS"):
        raise SystemExit(1)
    assert all(sha(ROOT / path) == digest for path, digest in hashes.items()), "Test inputs changed during run"
    (server / "forge-charm-particles-test-receipt.json").write_text(json.dumps({
        "result": summary.splitlines()[0], "forge_sha256": sha(candidate), "sources": hashes,
        "snapshot": args.snapshot, "metrics": [line for line in summary.splitlines() if line.startswith("METRIC ")],
    }, indent=2) + "\n", encoding="utf8")


if __name__ == "__main__":
    main()
