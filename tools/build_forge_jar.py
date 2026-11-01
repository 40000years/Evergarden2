"""Build dist/evergarden-forge.jar from evergarden-forge/ source.

Compiles against the pinned Paper classpath of the last Evergarden release build (the forge only
needs the Paper API), packages classes + src/main/resources and checks the JAR for compiler stubs.
The forge embeds its gem/material addon packs; menu controls use the Advance Magic icon set.
Nothing is deployed to a server.

    python tools/build_forge_jar.py            # build and copy to dist/
    python tools/build_forge_jar.py --no-copy  # leave the JAR in output/forge-build/
"""
from __future__ import annotations

import argparse
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import build_plugin_jars as plugin_jars  # noqa: E402
from release_jar_checks import check_jar  # noqa: E402

MODULE = ROOT / 'evergarden-forge'
OUT = ROOT / 'output/forge-build'
CLASSPATH_ARGS = plugin_jars.EVERGARDEN_ARGS


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--no-copy', action='store_true', help='Leave the JAR in output/forge-build/')
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    plugin_jars.OUT = OUT
    classpath = [entry for entry in plugin_jars.pinned_classpath(CLASSPATH_ARGS)
                 if not entry.replace('\\', '/').split('/')[-2:-1] == ['dist']]
    sources = sorted((MODULE / 'src/main/java').rglob('*.java'))
    classes = OUT / 'classes'
    if classes.exists():
        assert classes.resolve().is_relative_to(OUT.resolve()) and classes.name == 'classes'
        shutil.rmtree(classes)
    plugin_jars.compile_sources('evergarden-forge', sources, classpath, classes)
    jar = OUT / 'evergarden-forge.jar'
    if jar.exists():
        jar.unlink()
    with zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as archive:
        for directory in (classes, MODULE / 'src/main/resources'):
            for path in sorted(directory.rglob('*')):
                if path.is_file():
                    archive.write(path, path.relative_to(directory).as_posix())
    check_jar(jar)
    print(f'built {jar.relative_to(ROOT)} ({jar.stat().st_size} bytes)')
    if not args.no_copy:
        target = ROOT / 'dist/evergarden-forge.jar'
        shutil.copy2(jar, target)
        print(f'copied to {target.relative_to(ROOT)}')


if __name__ == '__main__':
    main()
