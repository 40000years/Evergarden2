#!/usr/bin/env python3
"""Run the whale generation probe in a newly created, disposable Paper server.

Requires built Evergarden2 JARs, Java 25+, and an already bootstrapped matching
Paper distribution. No live-server files, worlds or configuration are changed.
"""
import argparse
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--paper-template", type=Path, default=Path("/tmp/evergarden2-probe"))
    parser.add_argument("--java-home", type=Path, default=Path("/opt/homebrew/opt/openjdk"))
    parser.add_argument("--timeout", type=int, default=240)
    args = parser.parse_args()
    source = Path(__file__).resolve().parent
    repo = source.parent.parent
    template = args.paper_template.resolve()
    java = args.java_home / "bin/java"
    javac = args.java_home / "bin/javac"
    jar = args.java_home / "bin/jar"
    artifacts = [repo / "evergarden/target/evergarden.jar", repo / "advance-magic/target/advance-magic.jar"]
    for path in [template / "paper.jar", java, javac, jar, *artifacts]:
        if not path.is_file():
            parser.error(f"Missing prerequisite: {path}")
    bootstrap = None
    for filename in ["server.jar", "paper.jar"]:
        candidate = template / filename
        if candidate.is_file():
            with zipfile.ZipFile(candidate) as archive:
                manifest = archive.read("META-INF/MANIFEST.MF").decode()
            if "Main-Class: io.papermc.paperclip.Main" in manifest:
                bootstrap = filename
                break
    if bootstrap is None:
        parser.error("Template needs the executable Paperclip bootstrap (server.jar or paper.jar)")
    server = Path(tempfile.mkdtemp(prefix="evergarden2-whale-probe-"))
    print(f"Disposable server and logs: {server}", flush=True)
    for dirname in ["libraries", "versions", "cache"]:
        if (template / dirname).is_dir():
            shutil.copytree(template / dirname, server / dirname)
    for filename in ["paper.jar", "server.jar"]:
        if (template / filename).is_file():
            shutil.copy2(template / filename, server / filename)
    plugins = server / "plugins"
    plugins.mkdir()
    for artifact in artifacts:
        shutil.copy2(artifact, plugins / artifact.name)
    classes = server / "probe-classes"
    classes.mkdir()
    classpath = ":".join(str(p) for p in [*artifacts, *sorted((server / "libraries").rglob("*.jar"))])
    subprocess.run([str(javac), "--release", "25", "-cp", classpath, "-d", str(classes), str(source / "SkyWhaleProbe.java")], check=True)
    shutil.copy2(source / "plugin.yml", classes / "plugin.yml")
    subprocess.run([str(jar), "--create", "--file", str(plugins / "sky-whale-probe.jar"), "-C", str(classes), "."], check=True)
    # Bind only loopback and ask the OS for an unused port. Empty worlds belong to this run.
    (server / "eula.txt").write_text("eula=true\n")
    (server / "server.properties").write_text("server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\n"
        "view-distance=2\nsimulation-distance=2\nspawn-protection=0\npause-when-empty-seconds=-1\n"
        "max-tick-time=180000\nlevel-name=probe_base\nlevel-type=minecraft:flat\ngenerate-structures=false\n")
    log = server / "probe-console.log"
    try:
        with log.open("w") as output:
            subprocess.run([str(java), "-Xms512M", "-Xmx2G", "-Dterminal.jline=false", "-Dterminal.ansi=false",
                            "-jar", bootstrap, "--nogui"], cwd=server, stdin=subprocess.DEVNULL,
                           stdout=output, stderr=subprocess.STDOUT, timeout=args.timeout, check=True)
    except (subprocess.TimeoutExpired, subprocess.CalledProcessError) as failure:
        print(log.read_text()[-12000:])
        raise SystemExit(f"Paper probe failed: {failure}; log: {log}")
    result = server / "whale-probe-result.txt"
    if not result.is_file():
        print(log.read_text()[-12000:])
        raise SystemExit(f"Probe did not produce a result: {log}")
    print(result.read_text())
    if not result.read_text().startswith("PASS\n"):
        print(log.read_text()[-12000:])
        raise SystemExit(1)


if __name__ == "__main__":
    main()
