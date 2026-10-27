"""Run legendary wand gameplay/geometry checks in a fresh disposable Paper server.

Uses only cached Paper input files, binds to localhost and loads no player worlds.
The generated legendary-runtime-scenes.json records actual authored display poses.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source-server', type=Path, required=True)
parser.add_argument('--magic-jar', type=Path, default=ROOT / 'dist/advance-magic.jar')
parser.add_argument('--server', type=Path)
parser.add_argument('--timeout', type=int, default=240)
args = parser.parse_args()
server = (args.server or ROOT / '.audit-plugins' / ('legendary-cinematics-' + uuid.uuid4().hex)).resolve()
if server.exists():
    parser.error(f'Disposable target already exists: {server}; choose a new --server')
server.mkdir(parents=True)
for name in ('libraries', 'versions', 'cache'):
    source = (args.source_server / name).resolve()
    if not source.is_dir():
        raise RuntimeError(f'Missing cached Paper dependency directory: {source}')
    subprocess.run(['powershell', '-NoProfile', '-Command',
                    "New-Item -ItemType Junction -Path '" + str(server / name).replace("'", "''")
                    + "' -Target '" + str(source).replace("'", "''") + "' | Out-Null"], check=True)
shutil.copy2(args.source_server / 'paper.jar', server / 'paper.jar')
if (args.source_server / 'config').is_dir():
    shutil.copytree(args.source_server / 'config', server / 'config')
plugins = server / 'plugins'
plugins.mkdir()
shutil.copy2(args.magic_jar, plugins / 'advance-magic.jar')
config = plugins / 'advance-magic'
config.mkdir()
(config / 'config.yml').write_text(
    'resource-pack:\n  enabled: false\n  url: ""\n  sha1: ""\n'
    '  host:\n    enabled: false\n    bind: 127.0.0.1\n    port: 0\n', encoding='utf8')
(server / 'eula.txt').write_text('eula=true\n', encoding='ascii')
(server / 'server.properties').write_text(
    'server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nenforce-secure-profile=false\n'
    'level-name=legendary_test\nlevel-type=minecraft:flat\ngenerate-structures=false\n'
    'view-distance=2\nsimulation-distance=2\nspawn-protection=0\n'
    'pause-when-empty-seconds=-1\nmax-tick-time=240000\n', encoding='ascii')
classes = server / 'probe-classes'
classes.mkdir()
annotation_cache = ROOT / '.audit-plugins/legendary-build-20261005/repository/org/jetbrains/annotations'
if not annotation_cache.is_dir():
    annotation_cache = Path.home() / '.m2/repository/org/jetbrains/annotations'
deps = [plugins / 'advance-magic.jar', *(server / 'versions').rglob('*.jar'),
        *(server / 'libraries').rglob('*.jar'), *annotation_cache.rglob('*.jar')]
source = Path(__file__).with_name('LegendarySpellChecks.java')
compiler_args = ['--release', '25', '-proc:none', '-encoding', 'UTF-8',
                 '-cp', os.pathsep.join(map(str, deps)), '-d', str(classes), str(source)]
argfile = server / 'probe-javac.args'
argfile.write_text('\n'.join('"' + str(v).replace('\\', '/') + '"' for v in compiler_args), encoding='utf8')
subprocess.run(['javac', '@' + str(argfile)], check=True)
(classes / 'plugin.yml').write_text(
    "name: LegendarySpellChecks\nversion: '2.0'\nmain: LegendarySpellChecks\napi-version: '26.2'\n"
    'depend: [advance-magic]\n', encoding='utf8')
subprocess.run(['jar', '--create', '--file', str(plugins / 'legendary-checks.jar'),
                '-C', str(classes), '.'], check=True)
(server / 'probe-inputs.json').write_text(json.dumps({
    'scope': 'Fresh isolated Paper world, server actors and actual display poses; no real client or production state.',
    'inputs': {str(path.resolve()): hashlib.sha256(path.read_bytes()).hexdigest()
               for path in (args.magic_jar, source)},
}, indent=2), encoding='utf8')
print('Isolated legendary server:', server, flush=True)
with (server / 'console.log').open('w', encoding='utf8') as log:
    process = subprocess.Popen(['java', '-Xms384M', '-Xmx2G', '-Dterminal.jline=false',
                                '-Dterminal.ansi=false', '-jar', 'paper.jar', '--nogui', '--noconsole'],
                               cwd=server, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT,
                               text=True, creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    try:
        process.wait(timeout=args.timeout)
    except subprocess.TimeoutExpired:
        process.stdin.write('stop\n'); process.stdin.flush()
        try:
            process.wait(timeout=30)
        except subprocess.TimeoutExpired:
            process.kill()
        raise RuntimeError('Probe timed out; inspect ' + str(server / 'console.log'))
result = server / 'legendary-result.txt'
if not result.exists():
    raise RuntimeError('Probe did not write its result; see ' + str(server / 'console.log'))
report = result.read_text(encoding='utf8').strip()
print(report, flush=True)
print('Actual display scene poses:', server / 'legendary-runtime-scenes.json', flush=True)
raise SystemExit(0 if report.startswith('PASS') else 1)
