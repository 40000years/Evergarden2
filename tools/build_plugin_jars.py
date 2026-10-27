"""Build dist/advance-magic.jar and dist/evergarden.jar from source.

Compiles each module against the pinned dependency classpath recorded by the last
release build (its own dist JAR entry is dropped). Evergarden is compiled against the
freshly built advance-magic.jar and also takes advance-magic's pack/delivery classes,
as build_elemental_staff_release.py does. Packages classes, src/main/resources and the
module's dist packs, then runs release_jar_checks on both results. Compiles into a
fresh output folder so no stale classes survive. Nothing is deployed to a server.
"""
from __future__ import annotations

import argparse
import hashlib
import os
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from release_jar_checks import check_jar  # noqa: E402

OUT = ROOT / 'output/plugin-jars-build-20261023'
ADVANCE_ARGS = ROOT / 'output/boss-rewards-build-v3-20261021/advance-magic-javac.args'
EVERGARDEN_ARGS = ROOT / 'output/banshee-hunt-build-v1-20261015/evergarden-javac.args'
ADVANCE_PACKS = ('advance-magic-java.zip', 'advance-magic-bedrock.mcpack', 'geyser-mappings.json', 'pack-hashes.json')
EVERGARDEN_PACKS = ('evergarden-java.zip', 'judge-java.zip', 'evergarden-bedrock.mcpack', 'geyser-mappings.json', 'pack-hashes.json')


def pinned_classpath(args_file: Path) -> list[str]:
    lines = args_file.read_text(encoding='utf8').splitlines()
    value = lines[lines.index('"-cp"') + 1].strip('"')
    # The release's own dist JAR is rebuilt here; it must never be compiled against.
    return [entry for entry in value.split(';') if not entry.replace('\\', '/').endswith('dist/advance-magic.jar')]


def compile_sources(name: str, sources: list[Path], classpath: list[str], classes: Path) -> None:
    if not classes.resolve().is_relative_to((ROOT / 'output').resolve()):
        raise ValueError(f'Build classes must stay under workspace output/: {classes}')
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir(parents=True)
    options = ['--release', '25', '-proc:none', '-encoding', 'UTF-8', '-cp', ';'.join(classpath).replace('\\', '/'),
               '-d', classes.as_posix(), *[s.as_posix() for s in sources]]
    argfile = OUT / f'{name}-javac.args'
    argfile.write_text('\n'.join('"' + value + '"' for value in options), encoding='utf8')
    result = subprocess.run(['java', str(ROOT / 'tools/CompileJava.java'), str(argfile)], capture_output=True, text=True)
    errors = [line for line in (result.stderr + result.stdout).splitlines() if 'error' in line.lower()]
    print(f'{name}: javac exit {result.returncode}, {len(sources)} sources, {len(list(classes.rglob("*.class")))} classes')
    if result.returncode or 'An exception has occurred in the compiler' in result.stderr:
        raise RuntimeError(f'{name} did not compile:\n' + (result.stderr + result.stdout)[-16000:])


def package(name: str, classes: Path, module: Path, packs: tuple[str, ...], jar: Path) -> None:
    if jar.exists():
        jar.unlink()
    with zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as archive:
        for directory in (classes, module / 'src/main/resources'):
            for path in sorted(directory.rglob('*')):
                # *.bak are source backups that model tools read in place; the plugin never does.
                if path.is_file() and path.suffix != '.bak':
                    archive.write(path, path.relative_to(directory).as_posix())
        for pack in packs:
            archive.write(module / 'dist' / pack, 'resource-packs/' + pack)
        if name == 'advance-magic':
            for path in sorted((ROOT / 'advance-magic/art/elemental-staffs').glob('*.bbmodel')):
                archive.write(path, 'modelengine/elemental-staffs/' + path.name)
    check_jar(jar)


def main() -> None:
    global OUT
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--no-copy', action='store_true', help='Leave the JARs in output/ instead of replacing dist/')
    parser.add_argument('--output', type=Path, help='Build directory under workspace output/')
    args = parser.parse_args()
    if args.output:
        OUT = args.output.resolve()
    if not OUT.resolve().is_relative_to((ROOT / 'output').resolve()):
        raise ValueError('Build directory must stay under workspace output/')
    OUT.mkdir(parents=True, exist_ok=True)

    advance_sources = sorted((ROOT / 'advance-magic/src/main/java').rglob('*.java'))
    advance_jar = OUT / 'advance-magic.jar'
    compile_sources('advance-magic', advance_sources, pinned_classpath(ADVANCE_ARGS), OUT / 'advance-magic-classes')
    package('advance-magic', OUT / 'advance-magic-classes', ROOT / 'advance-magic', ADVANCE_PACKS, advance_jar)

    evergarden_sources = sorted((ROOT / 'evergarden/src/main/java').rglob('*.java'))
    evergarden_sources += sorted((ROOT / 'advance-magic/src/main/java/com/example/advancemagic/pack/delivery').glob('*.java'))
    evergarden_classpath = [advance_jar.as_posix()] + pinned_classpath(EVERGARDEN_ARGS)
    compile_sources('evergarden', evergarden_sources, evergarden_classpath, OUT / 'evergarden-classes')
    evergarden_jar = OUT / 'evergarden.jar'
    package('evergarden', OUT / 'evergarden-classes', ROOT / 'evergarden', EVERGARDEN_PACKS, evergarden_jar)

    for jar in (advance_jar, evergarden_jar):
        with zipfile.ZipFile(jar) as archive:
            version = [line for line in archive.read('plugin.yml').decode('utf8').splitlines() if line.startswith('version')][0]
            print(f'{jar.name}: {version}, {len(archive.namelist())} entries, sha1 {hashlib.sha1(jar.read_bytes()).hexdigest()}')

    if not args.no_copy:
        for jar in (advance_jar, evergarden_jar):
            target = ROOT / 'dist' / jar.name
            if target.exists():
                # Keep whatever was there so a replaced JAR can be recovered.
                previous = OUT / 'previous'
                previous.mkdir(exist_ok=True)
                shutil.copy2(target, previous / jar.name)
            shutil.copy2(jar, target)
            print('Replaced', target)


if __name__ == '__main__':
    main()
