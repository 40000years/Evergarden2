"""Audit an exact AfterDeath JAR with native deaths on a new localhost Paper server."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess

root = Path(__file__).resolve().parents[2]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--name', required=True)
p.add_argument('--afterdeath', type=Path, default=root / 'dist/afterdeath.jar')
p.add_argument('--without-afterdeath', action='store_true')
p.add_argument('--garden', type=Path)
p.add_argument('--magic', type=Path)
p.add_argument('--soulbound', action='store_true', help='Tag the helmet as Evergarden Soulbound')
p.add_argument('--template', type=Path, default=root / '.audit-plugins/vault-security-fixed-20261003')
args = p.parse_args()
if args.soulbound and not args.garden:
    p.error('--soulbound requires --garden')
if not args.name.replace('-', '').replace('_', '').isalnum():
    p.error('--name must contain only letters, numbers, hyphens or underscores')
server = root / '.audit-plugins' / ('afterdeath-security-' + args.name)
if server.exists():
    p.error(f'Target exists: {server}; choose another --name')
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
if not args.without_afterdeath:
    shutil.copy2(args.afterdeath, plugins / 'afterdeath.jar')
for source, name, folder in ((args.garden, 'evergarden.jar', 'Evergarden'), (args.magic, 'advance-magic.jar', 'advance-magic')):
    if source:
        shutil.copy2(source, plugins / name)
        config = plugins / folder
        config.mkdir()
        (config / 'config.yml').write_text('resource-pack:\n  enabled: false\n  host:\n    enabled: false\n', encoding='utf8')
(server / 'eula.txt').write_text('eula=true\n')
(server / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nenforce-secure-profile=false\nview-distance=2\nsimulation-distance=2\nlevel-name=probe_base\nlevel-type=minecraft:flat\ngenerate-structures=false\nspawn-protection=0\npause-when-empty-seconds=-1\nmax-tick-time=240000\n')
deps = [*server.joinpath('versions').rglob('*.jar'), *server.joinpath('libraries').rglob('*.jar'), *Path.home().joinpath('.m2/repository/org/jetbrains/annotations').rglob('*.jar')]
classes = server / 'probe-classes'
classes.mkdir()
source_dir = Path(__file__).parent
compiler_args = ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp', os.pathsep.join(map(str, deps)), '-d', str(classes), str(source_dir / 'AfterdeathSecurityProbe.java')]
argfile = server / 'probe-javac.args'
argfile.write_text('\n'.join('"' + str(value).replace('\\', '/') + '"' for value in compiler_args), encoding='utf8')
subprocess.run(['javac', '@' + str(argfile)], check=True)
shutil.copy2(source_dir / 'plugin.yml', classes / 'plugin.yml')
subprocess.run(['jar', '--create', '--file', str(plugins / 'afterdeath-security-probe.jar'), '-C', str(classes), '.'], check=True)
inputs = {'afterdeath': None if args.without_afterdeath else {'source': str(args.afterdeath.resolve()), 'sha256': hashlib.sha256(args.afterdeath.read_bytes()).hexdigest()}, 'paper_sha256': hashlib.sha256((server / 'paper.jar').read_bytes()).hexdigest(), 'probe_sha256': hashlib.sha256((source_dir / 'AfterdeathSecurityProbe.java').read_bytes()).hexdigest()}
inputs['soulbound'] = args.soulbound
inputs['additional_plugins'] = {name: hashlib.sha256((plugins / name).read_bytes()).hexdigest() for name in ('evergarden.jar', 'advance-magic.jar') if (plugins / name).exists()}
(server / 'probe-inputs.json').write_text(json.dumps(inputs, indent=2))
print('Isolated server:', server, flush=True)
with (server / 'console.log').open('w', encoding='utf8') as output:
    subprocess.run(['java', '-Xms512M', '-Xmx2G', '-Dterminal.jline=false', '-Dterminal.ansi=false', '-Dprobe.soulbound=' + str(args.soulbound).lower(), '-jar', 'paper.jar', '--nogui', '--noconsole'], cwd=server, stdout=output, stderr=subprocess.STDOUT, timeout=240, check=True, creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
result = server / 'afterdeath-security-result.txt'
if not result.exists():
    raise RuntimeError('Probe did not complete; inspect ' + str(server / 'console.log'))
summary = result.read_text(encoding='utf8')
print(summary, flush=True)
if not summary.startswith('PASS'):
    raise SystemExit(1)
