"""Build both plugins and verify batched resource pack delivery using cached dependencies."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[2]
SHARED = ROOT / 'advance-magic/src/main/java/com/example/advancemagic/pack/delivery'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--compiler-options', type=Path, required=True,
                    help='A javac argument file with the cached Paper compilation classpath')
parser.add_argument('--build-directory', type=Path)
args = parser.parse_args()
build = (args.build_directory or ROOT / 'output' / ('pack-checks-' + uuid.uuid4().hex[:8])).resolve()
build.mkdir(parents=True, exist_ok=False)
old = [line.strip().strip('"') for line in args.compiler_options.read_text(encoding='utf8').splitlines()]
inputs = [Path(name) for name in old[old.index('-cp') + 1].split(os.pathsep)]
# Pack delivery on the Minecraft port compiles against the Netty that Paper bundles.
netty = next(ROOT.glob('output/*/libraries/io/netty'), None)
if netty:
    inputs += sorted(netty.glob('netty-*/*/netty-*.jar'))

# Keep the original order (in particular the current Gson before old copies).
deps = []
for index, source in enumerate(dict.fromkeys(inputs)):
    target = build / 'deps' / str(index) / source.name
    target.parent.mkdir(parents=True)
    shutil.copyfile(source, target)
    deps.append(target)
print('Prepared', len(deps), 'cached dependencies', flush=True)


def compile_sources(sources, classes, extra=()):
    classes.mkdir()
    options = ['--release', '25', '-proc:none', '-encoding', 'UTF-8',
               '-cp', os.pathsep.join(map(str, [*extra, *deps])), '-d', str(classes), *map(str, sources)]
    argfile = build / (classes.name + '-javac.args')
    argfile.write_text('\n'.join('"' + value.replace('\\', '/') + '"' for value in options), encoding='utf8')
    subprocess.run(['javac', '@' + str(argfile)], check=True)


def package(module, extra=()):
    classes = build / (module + '-classes')
    sources = sorted((ROOT / module / 'src/main/java').rglob('*.java'))
    if module == 'evergarden':
        sources += sorted(SHARED.glob('*.java'))
    compile_sources(sources, classes, extra)
    shutil.copytree(ROOT / module / 'src/main/resources', classes, dirs_exist_ok=True)
    names = ('advance-magic-java.zip', 'advance-magic-bedrock.mcpack', 'geyser-mappings.json', 'pack-hashes.json') if module == 'advance-magic' else (
        'evergarden-java.zip', 'judge-java.zip', 'evergarden-bedrock.mcpack', 'geyser-mappings.json', 'pack-hashes.json')
    packs = classes / 'resource-packs'
    packs.mkdir()
    for name in names:
        shutil.copyfile(ROOT / module / 'dist' / name, packs / name)
    jar = build / (module + '.jar')
    subprocess.run(['jar', '--create', '--file', str(jar), '-C', str(classes), '.'], check=True)
    with zipfile.ZipFile(jar) as release:
        for name in names:
            assert release.read('resource-packs/' + name) == (ROOT / module / 'dist' / name).read_bytes(), name
        assert 'com/example/advancemagic/pack/delivery/ResourcePackManager.class' in release.namelist()
    return classes, jar


magic_classes, magic = package('advance-magic')
garden_classes, garden = package('evergarden', [magic_classes])
checks = build / 'check-classes'
compile_sources([Path(__file__).with_name('ResourcePackDeliveryChecks.java'),
                 Path(__file__).with_name('RequiredPackSettingsChecks.java'),
                 ROOT / 'advance-magic/tests/PackConfigChecks.java',
                 ROOT / 'evergarden/tests/PackConfigChecks.java'], checks, [magic_classes, garden_classes])
classpath = os.pathsep.join(map(str, [checks, magic_classes, garden_classes, *deps]))
commands = [
    ['java', '-cp', classpath, 'ResourcePackDeliveryChecks', str(magic_classes), str(garden_classes)],
    ['java', '-cp', classpath, 'RequiredPackSettingsChecks'],
    ['java', '-cp', classpath, 'com.example.advancemagic.pack.PackConfigChecks'],
    ['java', '-cp', classpath, 'com.example.voidscape.pack.PackConfigChecks'],
    [sys.executable, str(ROOT / 'tools/release_jar_checks.py'), str(magic), str(garden)],
]
output = []
for command in commands:
    result = subprocess.run(command, cwd=ROOT, check=True, text=True, stdout=subprocess.PIPE)
    print(result.stdout.strip(), flush=True)
    output.append(result.stdout.strip())
(build / 'result.txt').write_text('\n'.join(output), encoding='utf8')
(build / 'release-manifest.json').write_text(json.dumps({
    'scope': 'Local artifacts only; no live server deployment',
    'checks': output,
    'sha256': {jar.name: hashlib.sha256(jar.read_bytes()).hexdigest() for jar in (magic, garden)},
}, indent=2), encoding='utf8')
print('Validated JARs:', magic, garden, sep='\n', flush=True)
