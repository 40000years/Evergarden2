"""Build and exercise dual-mode wands in an isolated cached Paper server (no deployment)."""
import argparse
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source-server', type=Path, required=True)
parser.add_argument('--server', type=Path)
parser.add_argument('--timeout', type=int, default=240)
args = parser.parse_args()
server = (args.server or ROOT / 'output' / ('basic-casting-' + uuid.uuid4().hex)).resolve()
if server.exists():
    parser.error('Choose a fresh disposable --server directory')
server.mkdir(parents=True)
print('Isolated basic casting server:', server, flush=True)
for name in ('libraries', 'versions', 'cache'):
    source = (args.source_server / name).resolve()
    if not source.is_dir():
        raise RuntimeError(f'Missing cached Paper dependency: {source}')
    shutil.copytree(source, server / name)
shutil.copy2(args.source_server / 'paper.jar', server / 'paper.jar')
if (args.source_server / 'config').is_dir():
    shutil.copytree(args.source_server / 'config', server / 'config')
plugins = server / 'plugins'
plugins.mkdir()
repository = ROOT / '.audit-plugins/legendary-build-20261005/repository'
if not repository.is_dir():
    repository = Path.home() / '.m2/repository'
cached = ROOT / '.audit-plugins/structure-protection-build-20261005/dependencies'
inputs = [*(cached.glob('*.jar') if cached.is_dir() else repository.rglob('*.jar')),
          *(server / 'versions').rglob('*.jar'), *(server / 'libraries').rglob('*.jar')]
# Keep javac off old read-only junctions, and prefer the server's modern Gson to Maven's historic copies.
dep_dir = server / 'compile-dependencies'
dep_dir.mkdir()
gsons = [path for path in inputs if re.fullmatch(r'gson-[\d.]+\.jar', path.name)]
newest_gson = max(gsons, key=lambda path: tuple(map(int, re.findall(r'\d+', path.name)))) if gsons else None
deps = []
seen = set()
for path in ([newest_gson] if newest_gson else []) + inputs:
    if path.name in seen or path.name in ('advance-magic.jar', 'evergarden.jar'):
        continue
    if path in gsons and path != newest_gson:
        continue
    seen.add(path.name)
    destination = dep_dir / path.name
    shutil.copy2(path, destination)
    deps.append(destination)


def compile_sources(sources, classes, classpath):
    classes.mkdir(parents=True)
    options = ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp', os.pathsep.join(map(str, classpath)),
               '-d', str(classes), *map(str, sources)]
    argfile = classes.parent / (classes.name + '-javac.args')
    argfile.write_text('\n'.join('"' + str(value).replace('\\', '/') + '"' for value in options), encoding='utf8')
    subprocess.run(['javac', '@' + str(argfile)], check=True)


classes = server / 'magic-classes'
sources = sorted((ROOT / 'advance-magic/src/main/java').rglob('*.java'))
compile_sources(sources, classes, deps)
shutil.copytree(ROOT / 'advance-magic/src/main/resources', classes, dirs_exist_ok=True)
packs = classes / 'resource-packs'
packs.mkdir()
for name in ('advance-magic-java.zip', 'advance-magic-bedrock.mcpack', 'geyser-mappings.json', 'pack-hashes.json'):
    shutil.copy2(ROOT / 'advance-magic/dist' / name, packs / name)
magic = plugins / 'advance-magic.jar'
subprocess.run(['jar', '--create', '--file', str(magic), '-C', str(classes), '.'], check=True)
subprocess.run(['python', str(ROOT / 'tools/release_jar_checks.py'), str(magic)], check=True)
probe_classes = server / 'probe-classes'
compile_sources([Path(__file__).with_name('BasicCastingChecks.java'), Path(__file__).with_name('ManaPotionChecks.java')], probe_classes, [magic, *deps])
(probe_classes / 'plugin.yml').write_text(
    "name: BasicCastingChecks\nversion: '1.0'\nmain: BasicCastingChecks\napi-version: '26.2'\ndepend: [advance-magic]\n", encoding='utf8')
subprocess.run(['jar', '--create', '--file', str(plugins / 'basic-casting-checks.jar'), '-C', str(probe_classes), '.'], check=True)
config = plugins / 'advance-magic'
config.mkdir()
(config / 'config.yml').write_text('resource-pack:\n  enabled: false\n  host:\n    enabled: false\n', encoding='utf8')
(server / 'eula.txt').write_text('eula=true\n', encoding='ascii')
(server / 'server.properties').write_text(
    'server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nenforce-secure-profile=false\n'
    'level-name=basic_test\nlevel-type=minecraft:flat\ngenerate-structures=false\n'
    'view-distance=2\nsimulation-distance=2\nspawn-protection=0\n'
    'pause-when-empty-seconds=-1\nmax-tick-time=240000\n', encoding='ascii')
with (server / 'console.log').open('w', encoding='utf8') as log:
    process = subprocess.Popen(['java', '-Xms384M', '-Xmx2G', '-Dterminal.jline=false', '-Dterminal.ansi=false',
                                '-jar', 'paper.jar', '--nogui', '--noconsole'], cwd=server,
                               stdout=log, stderr=subprocess.STDOUT,
                               creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
    try:
        process.wait(timeout=args.timeout)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait()
        raise RuntimeError('Probe timed out: ' + str(server / 'console.log'))
result = server / 'basic-casting-result.txt'
if not result.exists():
    raise RuntimeError('Probe did not finish: ' + str(server / 'console.log'))
report = result.read_text(encoding='utf8').strip()
print(report, flush=True)
print('Built release:', magic, flush=True)
print('Actual scene poses:', server / 'basic-runtime-scenes.json', flush=True)
raise SystemExit(0 if report.startswith('PASS') else 1)
