"""Run Evergarden scroll compatibility checks on a fresh local Paper server."""
from pathlib import Path
import argparse
import os
import shutil
import subprocess

root = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--name', required=True)
parser.add_argument('--garden', type=Path, default=root / 'dist/evergarden.jar')
parser.add_argument('--magic', type=Path, default=root / 'dist/advance-magic.jar')
args = parser.parse_args()
if not args.name.replace('-', '').replace('_', '').isalnum():
    parser.error('--name must contain only letters, numbers, hyphens or underscores')

template = root / 'output/cleanup-geyser-template'
server = root / 'output/unique-compat' / ('paper-' + args.name)
if server.exists():
    parser.error('Target already exists: ' + str(server))
server.mkdir(parents=True)
for name in ('libraries', 'versions', 'cache'):
    source = (template / name).resolve()
    if os.name == 'nt':
        command = "New-Item -ItemType Junction -Path '" + str(server / name).replace("'", "''") + "' -Target '" + str(source).replace("'", "''") + "' | Out-Null"
        subprocess.run(['powershell', '-NoProfile', '-Command', command], check=True)
    else:
        (server / name).symlink_to(source, target_is_directory=True)

shutil.copy2(template / 'paper.jar', server / 'paper.jar')
plugins = server / 'plugins'
plugins.mkdir()
shutil.copy2(args.garden, plugins / 'evergarden.jar')
shutil.copy2(args.magic, plugins / 'advance-magic.jar')
for name in ('Evergarden', 'advance-magic'):
    folder = plugins / name
    folder.mkdir()
    (folder / 'config.yml').write_text('resource-pack:\n  enabled: false\n  host:\n    enabled: false\n', encoding='utf8')

deps = [line.strip().strip('"') for line in (root / 'output/plant-removal-build/javac.args').read_text(encoding='utf8').splitlines()]
classpath = [str(plugins / 'evergarden.jar'), str(plugins / 'advance-magic.jar'), *deps[deps.index('-cp') + 1].split(os.pathsep)[1:]]
classes = server / 'probe-classes'
classes.mkdir()
source_dir = Path(__file__).parent
compiler_args = ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp', os.pathsep.join(classpath), '-d', str(classes), str(source_dir / 'UniqueCompatibilityProbe.java')]
argfile = server / 'probe-javac.args'
argfile.write_text('\n'.join('"' + value.replace('\\', '/') + '"' for value in compiler_args), encoding='utf8')
subprocess.run(['javac', '@' + str(argfile)], check=True)
shutil.copy2(source_dir / 'plugin.yml', classes / 'plugin.yml')
subprocess.run(['jar', '--create', '--file', str(plugins / 'unique-compat-probe.jar'), '-C', str(classes), '.'], check=True)

(server / 'eula.txt').write_text('eula=true\n', encoding='utf8')
(server / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nenforce-secure-profile=false\nview-distance=2\nsimulation-distance=2\nlevel-name=probe_base\nlevel-type=minecraft:flat\ngenerate-structures=false\nspawn-protection=0\npause-when-empty-seconds=-1\nmax-tick-time=240000\n', encoding='utf8')
with (server / 'console.log').open('w', encoding='utf8') as output:
    subprocess.run(['java', '-Xms512M', '-Xmx2G', '-Dterminal.jline=false', '-Dterminal.ansi=false', '-jar', 'paper.jar', '--nogui', '--noconsole'], cwd=server, stdout=output, stderr=subprocess.STDOUT, timeout=240, check=True, creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
result = server / 'unique-compat-result.txt'
if not result.exists():
    raise RuntimeError('Probe did not complete: ' + str(server / 'console.log'))
summary = result.read_text(encoding='utf8')
print(summary)
if not summary.startswith('PASS'):
    raise SystemExit(1)
