"""Verify a real old-to-new small-structure upgrade and a further restart in a disposable world."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
for name in ('template', 'old-jar', 'new-jar', 'classpath-file', 'server'):
    parser.add_argument('--' + name, type=Path, required=True)
args = parser.parse_args(); server = args.server.resolve(); server.mkdir(parents=True, exist_ok=False)
for name in ('libraries', 'versions', 'cache'):
    source = (args.template / name).resolve(); target = server / name
    if os.name == 'nt':
        command = "New-Item -ItemType Junction -Path '" + str(target).replace("'", "''") + "' -Target '" + str(source).replace("'", "''") + "' | Out-Null"
        subprocess.run(['powershell', '-NoProfile', '-Command', command], check=True)
    else: target.symlink_to(source, target_is_directory=True)
shutil.copy2(args.template / 'paper.jar', server / 'paper.jar')
plugins = server / 'plugins'; plugins.mkdir(); shutil.copy2(ROOT / 'dist/advance-magic.jar', plugins / 'advance-magic.jar')
for name in ('Evergarden', 'advance-magic'):
    folder = plugins / name; folder.mkdir()
    (folder / 'config.yml').write_text('config-version: 2\ndimension:\n  seed: 20261013\nresource-pack:\n  enabled: false\n  host:\n    enabled: false\nworld-boss:\n  visuals:\n    pack:\n      host:\n        bind: 127.0.0.1\n        port: 0\n', encoding='utf8')
classes = server / 'probe-classes'; classes.mkdir()
classpath = os.pathsep.join([str(args.new_jar.resolve()), args.classpath_file.read_text(encoding='utf8').strip()])
options = ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp', classpath, '-d', str(classes), str(Path(__file__).with_name('SmallStructureUpgradeProbe.java').resolve())]
argfile = server / 'upgrade-javac.args'; argfile.write_text('\n'.join('"' + str(v).replace('\\', '/') + '"' for v in options), encoding='utf8')
subprocess.run(['javac', '@' + str(argfile)], check=True)
(classes / 'plugin.yml').write_text("name: SmallStructureUpgradeProbe\nversion: '1'\nmain: SmallStructureUpgradeProbe\napi-version: '26.2'\ndepend: [Evergarden]\n", encoding='utf8')
subprocess.run(['jar', '--create', '--file', str(plugins / 'small-upgrade-probe.jar'), '-C', str(classes), '.'], check=True)
(server / 'eula.txt').write_text('eula=true\n')
(server / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-name=upgrade_base\nlevel-type=minecraft:flat\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\npause-when-empty-seconds=-1\nmax-tick-time=240000\n')
(server / 'spigot.yml').write_text('settings:\n  timeout-time: 240\n  restart-on-crash: false\n')
for phase, release in enumerate((args.old_jar, args.new_jar, args.new_jar), 1):
    shutil.copy2(release, plugins / 'evergarden.jar')
    with (server / f'console-{phase}.log').open('w', encoding='utf8') as log:
        subprocess.run(['java', '-Xms512M', '-Xmx3G', '-Dterminal.jline=false', '-Dterminal.ansi=false', '-jar', 'paper.jar', '--nogui', '--noconsole'],
            cwd=server, stdout=log, stderr=subprocess.STDOUT, timeout=480, check=True, creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    report = (server / 'small-upgrade-result.txt').read_text(encoding='utf8')
    (server / 'small-upgrade-result.txt').rename(server / f'small-upgrade-result-{phase}.txt')
    print(f'Phase {phase}: {report}', flush=True)
    if not report.startswith('PASS'): raise RuntimeError(report)
