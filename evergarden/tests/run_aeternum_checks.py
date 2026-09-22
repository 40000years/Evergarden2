"""Run focused checks on a disposable Paper server using locally installed dependencies."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import uuid

parser = argparse.ArgumentParser()
parser.add_argument("--source-server", type=Path, required=True)
parser.add_argument("--with-geyser", action="store_true")
parser.add_argument("--fresh-geyser", action="store_true", help="Test automatic installation with no existing packs/mappings")
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
server = root / ".audit-plugins/aeternum-compat-20260921"
if args.fresh_geyser:
    args.with_geyser = True
    server = root / (".audit-plugins/aeternum-auto-" + uuid.uuid4().hex)
server.mkdir(parents=True, exist_ok=True)
for name in ("libraries", "versions", "cache"):
    target = server / name
    if not target.exists():
        source = (args.source_server / name).resolve()
        subprocess.run(["powershell", "-NoProfile", "-Command",
                        "New-Item -ItemType Junction -Path '" + str(target).replace("'", "''")
                        + "' -Target '" + str(source).replace("'", "''") + "' | Out-Null"], check=True)
plugins = server / "plugins"
plugins.mkdir(exist_ok=True)
for name in ("evergarden.jar", "advance-magic.jar"):
    shutil.copy2(root / name, plugins / name)
shutil.copy2(args.source_server / "plugins/AeternumSeasons-4.7.jar", plugins)
for name in ("Evergarden", "advance-magic"):
    folder = plugins / name
    folder.mkdir(exist_ok=True)
    auto_install = "true" if args.fresh_geyser else "false"
    (folder / "config.yml").write_text("config-version: 2\nresource-pack:\n  enabled: false\n  geyser:\n    auto-install: " + auto_install + "\n", encoding="utf-8")
if args.with_geyser:
    for name in ("Geyser-Spigot.jar", "floodgate-spigot.jar"):
        shutil.copy2(args.source_server / "plugins" / name, plugins / name)
    geyser = plugins / "Geyser-Spigot"
    geyser.mkdir(exist_ok=True)
    config = (args.source_server / "plugins/Geyser-Spigot/config.yml").read_text(encoding="utf-8")
    config = config.replace("port: 19132", "port: 19233")
    if args.fresh_geyser:
        config = config.replace("enable-custom-content: true", "enable-custom-content: false")
    (geyser / "config.yml").write_text(config, encoding="utf-8")
    if not args.fresh_geyser:
        for name in ("packs", "custom_mappings"):
            shutil.copytree(args.source_server / "plugins/Geyser-Spigot" / name, geyser / name, dirs_exist_ok=True)
    else:
        (server / "check-fresh-geyser").write_text("true")
classes = server / "check-classes"
classes.mkdir(exist_ok=True)
deps = [*plugins.glob("*.jar"), *(server / "versions").rglob("*.jar"), *(server / "libraries").rglob("*.jar")]
subprocess.run(["javac", "-proc:none", "-encoding", "UTF-8", "-cp", os.pathsep.join(map(str, deps)),
                "-d", str(classes), str(Path(__file__).with_name("AeternumCompatibilityChecks.java"))], check=True)
(classes / "plugin.yml").write_text("name: AeternumCompatibilityChecks\nversion: '1.0'\nmain: AeternumCompatibilityChecks\napi-version: '26.2'\ndepend: [Evergarden, AeternumSeasons, advance-magic]\n", encoding="utf-8")
subprocess.run(["jar", "--create", "--file", str(plugins / "compat-checks.jar"), "-C", str(classes), "."], check=True)
shutil.copy2(args.source_server / "eula.txt", server / "eula.txt")
(server / "server.properties").write_text("server-ip=127.0.0.1\nserver-port=25594\nonline-mode=false\nlevel-name=compat_test\nlevel-type=minecraft:flat\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\nmax-tick-time=120000\n", encoding="utf-8")
shutil.copy2(next(args.source_server.glob("paper-*.jar")), server / "paper.jar")
result = server / "compatibility-result.txt"
if result.exists():
    result.unlink()
with (server / "console.log").open("w", encoding="utf-8") as log:
    subprocess.run(["java", "-Xms256M", "-Xmx1536M", "-Dterminal.jline=false", "-Dterminal.ansi=false", "-jar", "paper.jar", "--nogui", "--noconsole"],
                   cwd=server, stdout=log, stderr=subprocess.STDOUT, timeout=240, check=True)
report = result.read_text(encoding="utf-8")
print(report)
print("Server log:", server / "console.log")
raise SystemExit(1 if "FAIL " in report else 0)
