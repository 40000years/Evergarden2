"""Build the seven-element Legendary spectacle over the delivered 1.27.2 JAR.

Compiles only this task's three visual classes from a snapshot, keeps every other
1.27.2 entry byte-identical (resource packs included: the props are vanilla
blocks and particles), and replaces plugin.yml and config.yml from source.

    python tools/build_legendary_spectacle_release.py [--no-copy]
"""
import argparse
import hashlib
import json
import os
import shutil
import zipfile
from pathlib import Path

import build_plugin_jars as build

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output/legendary-spectacle-20261025/release'
BASE = ROOT / 'dist/advance-magic-1.27.2-reused-runes.jar'
BASE_SHA256 = '3e80cdd0b4a53f82a8e523414e23e653189568422f4745cfd0f9b6508ae098dd'
VERSION = '1.28.0-legendary-spectacle'
OWNED = (
    'com/example/advancemagic/spell/ElementalVfx.java',
    'com/example/advancemagic/spell/MythicLineVisuals.java',
    'com/example/advancemagic/spell/MythicVisuals.java',
)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--no-copy', action='store_true', help='Leave the JAR in output/ instead of replacing dist/')
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    if sha(BASE.read_bytes()) != BASE_SHA256:
        raise RuntimeError('The delivered 1.27.2 baseline changed unexpectedly.')
    sources, hashes = [], {}
    for relative in OWNED:
        original = ROOT / 'advance-magic/src/main/java' / relative
        snapshot = OUT / 'source' / relative
        snapshot.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(original, snapshot)
        sources.append(snapshot)
        hashes[original.relative_to(ROOT).as_posix()] = sha(snapshot.read_bytes())
    replacements = {}
    for name in ('plugin.yml', 'config.yml'):
        original = ROOT / 'advance-magic/src/main/resources' / name
        replacements[name] = original.read_bytes()
        hashes[original.relative_to(ROOT).as_posix()] = sha(replacements[name])
    build.OUT = OUT
    classes = OUT / 'classes'
    build.compile_sources('legendary-spectacle', sources, [BASE.as_posix()] + build.pinned_classpath(build.ADVANCE_ARGS), classes)
    prefixes = tuple(relative.removesuffix('.java') for relative in OWNED)

    def replaced(name):
        return any(name == prefix + '.class' or name.startswith(prefix + '$') for prefix in prefixes)

    jar = OUT / 'advance-magic.jar'
    with zipfile.ZipFile(BASE) as previous, zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as result:
        preserved = 0
        for entry in previous.infolist():
            if replaced(entry.filename) or entry.filename in replacements:
                continue
            result.writestr(entry, previous.read(entry.filename))
            preserved += 1
        for path in sorted(classes.rglob('*.class')):
            result.write(path, path.relative_to(classes).as_posix())
        for name, data in replacements.items():
            result.writestr(name, data)
    with zipfile.ZipFile(jar) as release:
        if release.testzip() is not None or len(release.namelist()) != len(set(release.namelist())):
            raise RuntimeError('Invalid release archive')
        if ('version: ' + VERSION).encode() not in release.read('plugin.yml'):
            raise RuntimeError('Unexpected plugin version')
    build.check_jar(jar)
    digest = sha(jar.read_bytes())
    if not args.no_copy:
        previous_dir = OUT / 'previous'
        previous_dir.mkdir(exist_ok=True)
        current = ROOT / 'dist/advance-magic.jar'
        if current.exists() and not (previous_dir / current.name).exists():
            shutil.copy2(current, previous_dir / current.name)
        for target in (current, ROOT / 'dist' / ('advance-magic-' + VERSION + '.jar')):
            staged = target.with_suffix('.jar.staged')
            shutil.copy2(jar, staged)
            os.replace(staged, target)
    report = {
        'version': VERSION, 'sha256': digest, 'size': jar.stat().st_size,
        'baseline': BASE.relative_to(ROOT).as_posix(), 'baseline_sha256': BASE_SHA256,
        'compiled_classes': len(list(classes.rglob('*.class'))), 'preserved_baseline_entries': preserved,
        'source_sha256': hashes, 'copied_to_dist': not args.no_copy,
    }
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
