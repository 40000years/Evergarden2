"""Run the Nether arena probe in a new, isolated Paper fixture, twice.

Usage: python tests/nether-arena/run.py --jar output/<build>/evergarden.jar --old-world output/<fixture>/arena_probe
This never replaces dist JARs or uses a live server/world.
"""
import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
import build_plugin_jars as build

parser = argparse.ArgumentParser()
parser.add_argument('--jar', type=Path, required=True)
parser.add_argument('--server', type=Path, default=ROOT / 'output/nether-arena-probe')
parser.add_argument('--old-world', type=Path, required=True,
                    help='Existing Paper world folder to copy into the fixture as a baseline')
args = parser.parse_args()
server = args.server.resolve()
if not server.is_relative_to((ROOT / 'output').resolve()):
    raise ValueError('Fixture must be inside workspace output/')
server.mkdir(parents=True, exist_ok=False)
template = ROOT / 'output/arkhaia-aggro-feedback-paper-20261014'
for name in ('libraries', 'versions', 'cache'):
    # These immutable server files are already cached. Each world remains in the new fixture.
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
    (folder / 'config.yml').write_text('config-version: 2\nnether-arena:\n  enabled: true\nresource-pack:\n  enabled: false\n  host:\n    enabled: false\nworld-boss:\n  visuals:\n    pack:\n      host:\n        bind: 127.0.0.1\n        port: 0\n', encoding='utf8')
# Paper needs its dimension metadata as well as region files for an existing world.
# Copy an existing disposable fixture; never modify the source world.
shutil.copytree(args.old_world, server / 'arena_probe')
# A valid empty Anvil region pre-dates plugin startup. It must be excluded even
# though it is far outside spawn. It is never populated by this probe.
old_region = server / 'arena_probe/dimensions/minecraft/the_nether/region/r.20.20.mca'
old_region.parent.mkdir(parents=True, exist_ok=True)
old_region.write_bytes(bytes(8192))
classes = server / 'probe-classes'
classes.mkdir()
cp = ';'.join([str(args.jar.resolve()), str(ROOT / 'dist/advance-magic.jar'), *build.pinned_classpath(build.EVERGARDEN_ARGS)])
options = ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp', cp.replace('\\', '/'),
           '-d', str(classes).replace('\\', '/'), str(Path(__file__).with_name('NetherArenaProbe.java')).replace('\\', '/')]
argfile = server / 'probe-javac.args'
argfile.write_text('\n'.join('"' + s + '"' for s in options), encoding='utf8')
subprocess.run(['java', str(ROOT / 'tools/CompileJava.java'), str(argfile)], check=True)
(classes / 'plugin.yml').write_text("name: NetherArenaProbe\nversion: '1'\nmain: NetherArenaProbe\napi-version: '26.2'\ndepend: [Evergarden]\n")
subprocess.run(['jar', '--create', '--file', str(plugins / 'nether-arena-probe.jar'), '-C', str(classes), '.'], check=True)
(server / 'eula.txt').write_text('eula=true\n')
(server / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-name=arena_probe\nlevel-seed=72819345\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\npause-when-empty-seconds=-1\nmax-tick-time=240000\n')
(server / 'spigot.yml').write_text('settings:\n  timeout-time: 240\n  restart-on-crash: false\n')
# Paperclip's Windows ZipFS close can fail under the sandbox. Launch the exact
# already-extracted server and libraries using the bundle's declared classpath.
launch_cp = []
with ZipFile(server / 'paper.jar') as bundle:
    for folder in ('versions', 'libraries'):
        for line in bundle.read(f'META-INF/{folder}.list').decode('utf8').splitlines():
            if line.strip():
                launch_cp.append(str((server / folder / line.split('\t')[2]).resolve()))
launch_cp += [str(Path(p).resolve()) for p in build.pinned_classpath(build.EVERGARDEN_ARGS) if Path(p).name.startswith('cumulus-')]
launch = ['-Xms512M', '-Xmx3G', '-Dterminal.jline=false', '-Dterminal.ansi=false', '-cp', os.pathsep.join(launch_cp), 'org.bukkit.craftbukkit.Main', '--nogui', '--noconsole']
launch_file = server / 'paper-launch.args'
launch_file.write_text('\n'.join('"' + s.replace('\\', '/') + '"' for s in launch), encoding='utf8')
for phase in (1, 2):
    with (server / f'console-{phase}.log').open('w', encoding='utf8') as log:
        subprocess.run(['java', '@' + str(launch_file)], cwd=server, stdout=log, stderr=subprocess.STDOUT, timeout=600, check=True, creationflags=subprocess.CREATE_NO_WINDOW)
    report = (server / f'nether-arena-result-{phase}.txt').read_text(encoding='utf8')
    print(report, flush=True)
    if not report.startswith('PASS'):
        raise SystemExit(1)
