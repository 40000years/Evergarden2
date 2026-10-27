"""Package the validated casting code and its in-game guide into local release JARs."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--validated-server', type=Path, required=True)
parser.add_argument('--build-directory', type=Path)
args = parser.parse_args()
validated = args.validated_server.resolve()
result = (validated / 'basic-casting-result.txt').read_text(encoding='utf8')
if not result.startswith('PASS'):
    raise RuntimeError('The supplied basic casting test server has not passed')
build = (args.build_directory or ROOT / 'output' / ('basic-release-' + uuid.uuid4().hex)).resolve()
build.mkdir(parents=True, exist_ok=False)
compiler_options = (validated / 'magic-classes-javac.args').read_text(encoding='utf8').splitlines()
classpath = compiler_options[compiler_options.index('"-cp"') + 1].strip('"')
deps = [Path(path) for path in classpath.split(os.pathsep)]
if not deps:
    raise RuntimeError('Missing validated compile dependencies')


def package(module, extra):
    classes = build / (module + '-classes')
    classes.mkdir()
    sources = sorted((ROOT / module / 'src/main/java').rglob('*.java'))
    if module == 'evergarden':
        sources += sorted((ROOT / 'advance-magic/src/main/java/com/example/advancemagic/pack/delivery').glob('*.java'))
    options = ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp', os.pathsep.join(map(str, [*extra, *deps])),
               '-d', str(classes), *map(str, sources)]
    argfile = build / (module + '-javac.args')
    argfile.write_text('\n'.join('"' + str(value).replace('\\', '/') + '"' for value in options), encoding='utf8')
    subprocess.run(['javac', '@' + str(argfile)], check=True)
    shutil.copytree(ROOT / module / 'src/main/resources', classes, dirs_exist_ok=True)
    packs = classes / 'resource-packs'
    packs.mkdir()
    names = ('advance-magic-java.zip', 'advance-magic-bedrock.mcpack', 'geyser-mappings.json', 'pack-hashes.json') if module == 'advance-magic' else (
        'evergarden-java.zip', 'judge-java.zip', 'evergarden-bedrock.mcpack', 'geyser-mappings.json', 'pack-hashes.json')
    for name in names:
        shutil.copy2(ROOT / module / 'dist' / name, packs / name)
    jar = build / (module + '.jar')
    subprocess.run(['jar', '--create', '--file', str(jar), '-C', str(classes), '.'], check=True)
    return jar


magic = package('advance-magic', [])
with zipfile.ZipFile(magic) as release, zipfile.ZipFile(validated / 'plugins/advance-magic.jar') as tested:
    classes = [name for name in release.namelist() if name.endswith('.class')]
    if set(classes) != {name for name in tested.namelist() if name.endswith('.class')}:
        raise RuntimeError('The release class catalog differs from the tested plugin')
    for name in classes:
        if release.read(name) != tested.read(name):
            raise RuntimeError('The release bytecode differs from the tested plugin: ' + name)
garden = package('evergarden', [magic])
subprocess.run(['python', str(ROOT / 'tools/release_jar_checks.py'), str(magic), str(garden)], check=True)
for module, jar in (('advance-magic', magic), ('evergarden', garden)):
    target = ROOT / module / 'target' / jar.name
    target.parent.mkdir(exist_ok=True)
    shutil.copy2(jar, target)
    shutil.copy2(jar, ROOT / 'dist' / jar.name)
(build / 'release-manifest.json').write_text(json.dumps({
    'basic_test': result.strip(),
    'scope': 'Local workspace artifacts only. No server deployment.',
    'sha256': {str(jar.relative_to(ROOT)): hashlib.sha256(jar.read_bytes()).hexdigest() for jar in (magic, garden)},
    'magic_bytecode': 'Identical to the Paper-tested plugin',
}, indent=2), encoding='utf8')
print('Packaged local releases:', magic, garden, sep='\n', flush=True)
