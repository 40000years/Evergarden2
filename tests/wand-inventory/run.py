"""Run server-authoritative wand crafting accounting against real Paper packets."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import uuid

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--paper-template', type=Path, required=True)
p.add_argument('--magic-jar', type=Path, required=True)
p.add_argument('--garden-jar', type=Path)
p.add_argument('--baseline-dynamic', action='store_true')
p.add_argument('--server', type=Path)
args = p.parse_args()
root = Path(__file__).resolve().parents[2]
server = (args.server or root / '.audit-plugins' / ('wand-inventory-' + uuid.uuid4().hex)).resolve()
if server.exists():
    p.error(f'Disposable target already exists: {server}; choose a new --server')
server.mkdir(parents=True)
print('Isolated server:', server, flush=True)
for name in ('libraries', 'versions', 'cache'):
    target = server / name
    if not target.exists():
        origin = (args.paper_template / name).resolve()
        subprocess.run(['powershell', '-NoProfile', '-Command',
            "New-Item -ItemType Junction -Path '" + str(target).replace("'", "''") +
            "' -Target '" + str(origin).replace("'", "''") + "' | Out-Null"], check=True)
shutil.copy2(args.paper_template / 'paper.jar', server / 'paper.jar')
plugins = server / 'plugins'; plugins.mkdir(exist_ok=True)
shutil.copy2(args.magic_jar, plugins / 'advance-magic.jar')
if args.garden_jar:
    shutil.copy2(args.garden_jar, plugins / 'evergarden.jar')
    garden_config=plugins / 'Evergarden'; garden_config.mkdir(exist_ok=True)
    (garden_config / 'config.yml').write_text('config-version: 2\nworld-boss:\n  visuals:\n    pack:\n      host:\n        bind: 127.0.0.1\n        port: 0\n        public-host: 127.0.0.1\n')
config = plugins / 'advance-magic'; config.mkdir(exist_ok=True)
(config / 'config.yml').write_text('config-version: 2\nresource-pack:\n  enabled: false\n  host:\n    enabled: false\n')
classes = server / 'probe-classes'; classes.mkdir(exist_ok=True)
deps = [*plugins.glob('*.jar'), root / 'dist/evergarden.jar', *(server / 'versions').rglob('*.jar'), *(server / 'libraries').rglob('*.jar'),
    *Path.home().joinpath('.m2/repository/org/jetbrains/annotations').rglob('*.jar')]
source = Path(__file__).parent
subprocess.run(['javac', '--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp', os.pathsep.join(map(str, deps)),
    '-d', str(classes), str(source / 'WandInventoryProbe.java')], check=True)
shutil.copy2(source / 'plugin.yml', classes / 'plugin.yml')
subprocess.run(['jar', '--create', '--file', str(plugins / 'wand-inventory-probe.jar'), '-C', str(classes), '.'], check=True)
(server / 'eula.txt').write_text('eula=true\n')
(server / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nview-distance=2\n'
    'simulation-distance=2\nspawn-protection=0\npause-when-empty-seconds=-1\nmax-tick-time=240000\n'
    'level-name=probe_base\nlevel-type=minecraft:flat\ngenerate-structures=false\n')
log = server / 'console.log'
with log.open('w', encoding='utf8') as out:
    command=['java', '-Xms512M', '-Xmx2G', '-Dterminal.jline=false', '-Dterminal.ansi=false']
    if args.baseline_dynamic: command.append('-Dwand.inventory.baseline=true')
    command.extend(['-jar', 'paper.jar', '--nogui', '--noconsole'])
    subprocess.run(command, cwd=server, stdout=out, stderr=subprocess.STDOUT,
        timeout=240, check=True, creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
result = server / 'wand-inventory-result.txt'
if not result.exists(): raise RuntimeError('Probe did not finish; see ' + str(log))
text = result.read_text(); print(text, flush=True)
if not text.startswith('PASS'): raise SystemExit(1)
