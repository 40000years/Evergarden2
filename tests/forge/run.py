"""Run EvergardenForge checks on a fresh local Paper server (no other plugins).

    python tests/forge/run.py --name smoke1 [--forge output/forge-build/evergarden-forge.jar]
"""
from pathlib import Path
import argparse
import os
import shutil
import subprocess
import sys
import hashlib
import json
import socket

root = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(root / 'tools'))
import build_plugin_jars as plugin_jars  # noqa: E402

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--name', required=True)
parser.add_argument('--forge', type=Path, default=root / 'output/forge-build/evergarden-forge.jar')
parser.add_argument('--template', type=Path, default=root / 'output/entry-items-check-20261031')
parser.add_argument('--audit', action='store_true')
parser.add_argument('--with-core', action='store_true')
parser.add_argument('--with-geyser', action='store_true')
args = parser.parse_args()
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
candidate_sha=sha(args.forge)
source_hashes={str(p.relative_to(root)):sha(p) for p in list((root/'evergarden-forge/src').rglob('*'))+list(Path(__file__).parent.glob('*.java')) if p.is_file()}
if not args.name.replace('-', '').replace('_', '').isalnum():
    parser.error('--name must contain only letters, numbers, hyphens or underscores')

server = root / 'output/forge-probe' / ('paper-' + args.name)
if server.exists():
    parser.error('Target already exists: ' + str(server))
server.mkdir(parents=True)
for name in ('libraries', 'versions', 'cache'):
    source = (args.template / name).resolve()
    if os.name == 'nt':
        command = "New-Item -ItemType Junction -Path '" + str(server / name).replace("'", "''") + "' -Target '" + str(source).replace("'", "''") + "' | Out-Null"
        subprocess.run(['powershell', '-NoProfile', '-Command', command], check=True)
    else:
        (server / name).symlink_to(source, target_is_directory=True)
shutil.copy2(args.template / 'paper.jar', server / 'paper.jar')
plugins = server / 'plugins'
plugins.mkdir()
shutil.copy2(args.forge, plugins / 'evergarden-forge.jar')
forge_config=plugins/'EvergardenForge';forge_config.mkdir()
(forge_config/'config.yml').write_text('config-version: 7\nresource-pack:\n  port: 0\n  public-host: 127.0.0.1\n',encoding='utf8')
if args.with_core:
    for name in ('evergarden.jar','advance-magic.jar'):shutil.copy2(root/'dist'/name,plugins/name)
    data=plugins/'Evergarden';data.mkdir()
    (data/'config.yml').write_text('resource-pack:\n  host:\n    use-server-port: false\n    port: 0\n    public-host: 127.0.0.1\n',encoding='utf8')
if args.with_geyser:
    for name in ('Geyser-Spigot.jar','floodgate-spigot.jar'):
        shutil.copy2(root/'output/pet-audit-paper-20261029-final/plugins'/name,plugins/name)

classpath = [str(plugins / 'evergarden-forge.jar'), *plugin_jars.pinned_classpath(plugin_jars.EVERGARDEN_ARGS)]
classes = server / 'probe-classes'
classes.mkdir()
source_dir = Path(__file__).parent
compiler_args = ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp', os.pathsep.join(classpath), '-d', str(classes), str(source_dir / 'ForgeProbe.java')]
if args.audit:compiler_args.append(str(source_dir/'ForgeAuditProbe.java'))
argfile = server / 'probe-javac.args'
argfile.write_text('\n'.join('"' + value.replace('\\', '/') + '"' for value in compiler_args), encoding='utf8')
subprocess.run(['java', str(root / 'tools/CompileJava.java'), str(argfile)], check=True)
shutil.copy2(source_dir / 'plugin.yml', classes / 'plugin.yml')
if args.audit:
    descriptor=(classes/'plugin.yml').read_text().replace('main: ForgeProbe','main: ForgeAuditProbe')
    (classes/'plugin.yml').write_text(descriptor,encoding='utf8')
subprocess.run(['jar', '--create', '--file', str(plugins / 'forge-probe.jar'), '-C', str(classes), '.'], check=True)

(server / 'eula.txt').write_text('eula=true\n', encoding='utf8')
port=0
if args.with_core:
    with socket.socket() as port_probe:
        port_probe.bind(('127.0.0.1',0));port=port_probe.getsockname()[1]
(server / 'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nenforce-secure-profile=false\nview-distance=2\nsimulation-distance=2\nlevel-name=probe_base\nlevel-type=minecraft:flat\ngenerate-structures=false\nspawn-protection=0\npause-when-empty-seconds=-1\nmax-tick-time=240000\n', encoding='utf8')
with (server / 'console.log').open('w', encoding='utf8') as output:
    subprocess.run(['java', '-Xms512M', '-Xmx2G', '-Dterminal.jline=false', '-Dterminal.ansi=false', '-jar', 'paper.jar', '--nogui', '--noconsole'], cwd=server, stdout=output, stderr=subprocess.STDOUT, timeout=300, check=True, creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
result = server / 'forge-result.txt'
if not result.exists():
    raise RuntimeError('Probe did not complete: ' + str(server / 'console.log'))
summary = result.read_text(encoding='utf8')
sys.stdout.reconfigure(encoding="utf-8")
print(summary)
if not summary.startswith('PASS'):
    raise SystemExit(1)
assert sha(args.forge)==candidate_sha
assert all(sha(root/p)==h for p,h in source_hashes.items()),'Source changed during test'
(server/'forge-test-receipt.json').write_text(json.dumps(dict(result=summary.splitlines()[0],candidate_sha256=candidate_sha,
    sources=source_hashes,with_core=args.with_core,with_geyser=args.with_geyser,audit=args.audit),indent=2)+'\n',encoding='utf8')
