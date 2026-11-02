"""Run the DimensionalSanctuaries probe in a new, isolated Paper fixture across two boots.

Usage:
  python tests/dimensional-sanctuaries/run.py --jar output/dimensional-sanctuaries-20261029/evergarden.jar --old-world output/nether-arena-probe-isolated-20261029/arena_probe --server output/dimsanct-probe-20261029
"""
from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
import build_plugin_jars as build  # noqa: E402

parser = argparse.ArgumentParser()
parser.add_argument('--jar', type=Path, required=True)
parser.add_argument('--server', type=Path, default=ROOT / 'output/dimsanct-probe-20261029')
parser.add_argument('--old-world', type=Path, required=True)
args = parser.parse_args()

server = args.server.resolve()
if not server.is_relative_to((ROOT / 'output').resolve()):
    raise ValueError('Fixture must be inside workspace output/')
if server.exists():
    shutil.rmtree(server)
server.mkdir(parents=True, exist_ok=True)

template = ROOT / 'output/arkhaia-aggro-feedback-paper-20261014'
for name in ('libraries', 'versions', 'cache'):
    subprocess.run(['cmd', '/c', 'mklink', '/J', str(server / name), str(template / name)],
                   check=True, capture_output=True)
shutil.copy2(template / 'paper.jar', server / 'paper.jar')

plugins = server / 'plugins'
plugins.mkdir()
shutil.copy2(args.jar, plugins / 'evergarden.jar')
shutil.copy2(ROOT / 'dist/advance-magic.jar', plugins / 'advance-magic.jar')

for name in ('Evergarden', 'advance-magic'):
    folder = plugins / name
    folder.mkdir()
    (folder / 'config.yml').write_text(
        'config-version: 2\n'
        'dimensional-sanctuaries:\n'
        '  enabled: true\n'
        'resource-pack:\n'
        '  enabled: false\n'
        '  host:\n'
        '    enabled: false\n'
        'world-boss:\n'
        '  visuals:\n'
        '    pack:\n'
        '      host:\n'
        '        bind: 127.0.0.1\n'
        '        port: 0\n',
        encoding='utf8',
    )

shutil.copytree(args.old_world, server / 'arena_probe')
# Pre-existing region files in both Nether and End must be captured in old-regions and excluded.
for dim in ('the_nether', 'the_end'):
    old_region = server / f'arena_probe/dimensions/minecraft/{dim}/region/r.20.20.mca'
    old_region.parent.mkdir(parents=True, exist_ok=True)
    old_region.write_bytes(bytes(8192))

classes = server / 'probe-classes'
classes.mkdir()
cp = ';'.join([str(args.jar.resolve()), str(ROOT / 'dist/advance-magic.jar'), *build.pinned_classpath(build.EVERGARDEN_ARGS)])
options = [
    '--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp', cp.replace('\\', '/'),
    '-d', str(classes).replace('\\', '/'),
    str(Path(__file__).with_name('DimensionalSanctuariesProbe.java')).replace('\\', '/'),
]
argfile = server / 'probe-javac.args'
argfile.write_text('\n'.join('"' + s + '"' for s in options), encoding='utf8')
subprocess.run(['java', str(ROOT / 'tools/CompileJava.java'), str(argfile)], check=True)
(classes / 'plugin.yml').write_text(
    "name: DimensionalSanctuariesProbe\nversion: '1'\nmain: DimensionalSanctuariesProbe\napi-version: '26.2'\ndepend: [Evergarden]\n",
    encoding='utf8',
)
subprocess.run(['jar', '--create', '--file', str(plugins / 'dimsanct-probe.jar'), '-C', str(classes), '.'], check=True)

(server / 'eula.txt').write_text('eula=true\n', encoding='utf8')
(server / 'server.properties').write_text(
    'server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-name=arena_probe\nlevel-seed=72819345\n'
    'view-distance=2\nsimulation-distance=2\nspawn-protection=0\npause-when-empty-seconds=-1\nmax-tick-time=240000\n',
    encoding='utf8',
)
(server / 'spigot.yml').write_text('settings:\n  timeout-time: 240\n  restart-on-crash: false\n', encoding='utf8')
(server / 'bukkit.yml').write_text('settings:\n  allow-end: true\n', encoding='utf8')

launch_cp = []
with ZipFile(server / 'paper.jar') as bundle:
    for folder in ('versions', 'libraries'):
        for line in bundle.read(f'META-INF/{folder}.list').decode('utf8').splitlines():
            if line.strip():
                launch_cp.append(str((server / folder / line.split('\t')[2]).resolve()))
launch_cp += [str(Path(p).resolve()) for p in build.pinned_classpath(build.EVERGARDEN_ARGS) if Path(p).name.startswith('cumulus-')]
launch = ['-Xms512M', '-Xmx3G', '-Dterminal.jline=false', '-Dterminal.ansi=false',
          '-cp', os.pathsep.join(launch_cp), 'org.bukkit.craftbukkit.Main', '--nogui', '--noconsole']
launch_file = server / 'paper-launch.args'
launch_file.write_text('\n'.join('"' + s.replace('\\', '/') + '"' for s in launch), encoding='utf8')

for phase in (1, 2):
    with (server / f'console-{phase}.log').open('w', encoding='utf8') as log:
        subprocess.run(['java', '@' + str(launch_file)], cwd=server, stdout=log, stderr=subprocess.STDOUT,
                       timeout=600, check=True, creationflags=subprocess.CREATE_NO_WINDOW)
    report = (server / f'dimsanct-result-{phase}.txt').read_text(encoding='utf8')
    print(f'=== BOOT {phase} ===\n{report}', flush=True)
    if not report.startswith('PASS'):
        raise SystemExit(1)
