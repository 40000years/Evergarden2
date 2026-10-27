"""Compile the two changed modules with the pinned, locally tested dependencies.

Outputs stay in an isolated build directory until the new asset and runtime
checks pass. This script never deploys to a running server.
"""
from pathlib import Path
import argparse
import os
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--source-server', type=Path, required=True)
p.add_argument('--output', type=Path, required=True)
p.add_argument('--module', choices=('all', 'advance-magic'), default='all')
p.add_argument('--compiler-process', action='store_true', help='Use an explicit short-lived JDK compiler process on Windows.')
p.add_argument('--compiler-args', type=Path, default=ROOT/'output/legendary-mechanics-final-paper-20261016/magic-classes-javac.args')
a = p.parse_args()
build = a.output.resolve()
build.mkdir(parents=True, exist_ok=False)
source = a.source_server.resolve()
pinned = a.compiler_args.read_text(encoding='utf8').splitlines()
deps = [Path(value) for value in pinned[pinned.index('"-cp"')+1].strip('"').split(os.pathsep)]
deps += [path for path in (source / 'plugins').glob('*.jar')
         if path.name in ('ModelEngine.jar', 'Geyser-Spigot.jar', 'floodgate-spigot.jar')]
if not deps:
    raise RuntimeError('Missing cached dependency jars')
for module in (('advance-magic', 'evergarden') if a.module == 'all' else ('advance-magic',)):
    classes = build / (module + '-classes')
    classes.mkdir()
    extra = [build / 'advance-magic.jar'] if module == 'evergarden' else []
    files = sorted((ROOT / module / 'src/main/java').rglob('*.java'))
    if module == 'evergarden':
        files += sorted((ROOT / 'advance-magic/src/main/java/com/example/advancemagic/pack/delivery').glob('*.java'))
    options = ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp',
               os.pathsep.join(map(str, [*extra, *deps])), '-d', str(classes), *map(str, files)]
    if module == 'evergarden':
        (build / 'classpath.txt').write_text(os.pathsep.join(map(str, [*extra, *deps])), encoding='utf8')
    argfile = build / (module + '-javac.args')
    argfile.write_text('\n'.join('"' + str(v).replace('\\', '/') + '"' for v in options), encoding='utf8')
    compiler_command = ['java',str(ROOT/'tools/CompileJava.java'),str(argfile)] if a.compiler_process else ['javac','@'+str(argfile)]
    compiled = subprocess.run(compiler_command, capture_output=True, text=True)
    sys.stdout.write(compiled.stdout)
    sys.stderr.write(compiled.stderr)
    if compiled.returncode or 'An exception has occurred in the compiler' in compiled.stderr:
        raise RuntimeError('javac did not finish cleanly')
    jar = build / (module + '.jar')
    packs = ('advance-magic-java.zip', 'advance-magic-bedrock.mcpack', 'geyser-mappings.json', 'pack-hashes.json') if module == 'advance-magic' else (
        'evergarden-java.zip', 'judge-java.zip', 'evergarden-bedrock.mcpack', 'geyser-mappings.json', 'pack-hashes.json')
    with zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as z:
        for directory in (classes, ROOT / module / 'src/main/resources'):
            for path in sorted(directory.rglob('*')):
                if path.is_file():
                    z.write(path, path.relative_to(directory).as_posix())
        for name in packs:
            z.write(ROOT / module / 'dist' / name, 'resource-packs/' + name)
        if module == 'advance-magic':
            for path in sorted((ROOT / 'advance-magic/art/elemental-staffs').glob('*.bbmodel')):
                z.write(path, 'modelengine/elemental-staffs/' + path.name)
    print('Built:', jar, flush=True)
