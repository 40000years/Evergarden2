"""Build Heaven's Descent beam cannon + slim packs (1.29.0) over the delivered 1.28.0 JAR.

Compiles only this task's combat and visual classes from a snapshot, swaps in the slimmed
resource packs, Geyser mappings and pack hashes from advance-magic/dist (built by
build_light_lance_packs.py), replaces plugin.yml and config.yml from source, and keeps every
other 1.28.0 entry byte-identical.

    python tools/build_light_lance_release.py [--no-copy]
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
OUT = ROOT / 'output/light-lance-20261025/release'
BASE = ROOT / 'dist/advance-magic-1.28.0-legendary-spectacle.jar'
BASE_SHA256 = '6bab6c25f60020e118aa39e95121fe6d2d2dacf6141045234dfcc7a2234d5e6a'
VERSION = '1.29.0-light-lance'
OWNED = (
    'com/example/advancemagic/elemental/ElementalSpell.java',
    'com/example/advancemagic/elemental/ElementalSpellService.java',
    'com/example/advancemagic/spell/ElementalVfx.java',
    'com/example/advancemagic/spell/MythicLineVisuals.java',
    'com/example/advancemagic/spell/MythicVisuals.java',
)
PACKS = ('advance-magic-java.zip', 'advance-magic-bedrock.mcpack', 'geyser-mappings.json', 'pack-hashes.json')


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--no-copy', action='store_true', help='Leave the JAR in output/ instead of replacing dist/')
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    if sha(BASE.read_bytes()) != BASE_SHA256:
        raise RuntimeError('The delivered 1.28.0 baseline changed unexpectedly.')
    hashes_file = json.loads((ROOT / 'advance-magic/dist/pack-hashes.json').read_text(encoding='utf8'))
    for name, digest in hashes_file.items():
        if hashlib.sha1((ROOT / 'advance-magic/dist' / name).read_bytes()).hexdigest() != digest:
            raise RuntimeError('pack-hashes.json does not match ' + name)
    manifest = None
    with zipfile.ZipFile(ROOT / 'advance-magic/dist/advance-magic-bedrock.mcpack') as pack:
        manifest = json.loads(pack.read('manifest.json'))
    if manifest['header']['version'] != [2, 31, 0]:
        raise RuntimeError('Run build_light_lance_packs.py first (expected Bedrock pack 2.31.0)')
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
    for name in PACKS:
        original = ROOT / 'advance-magic/dist' / name
        replacements['resource-packs/' + name] = original.read_bytes()
        hashes[original.relative_to(ROOT).as_posix()] = sha(replacements['resource-packs/' + name])
    build.OUT = OUT
    classes = OUT / 'classes'
    shutil.rmtree(classes, ignore_errors=True)
    build.compile_sources('light-lance', sources, [BASE.as_posix()] + build.pinned_classpath(build.ADVANCE_ARGS), classes)
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
            # The packs are already deflated archives; storing them avoids a second pass.
            result.writestr(zipfile.ZipInfo(name, date_time=(2026, 10, 25, 0, 0, 0)), data,
                            compress_type=zipfile.ZIP_STORED if name.endswith(('.zip', '.mcpack')) else zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(jar) as release:
        if release.testzip() is not None or len(release.namelist()) != len(set(release.namelist())):
            raise RuntimeError('Invalid release archive')
        if ('version: ' + VERSION).encode() not in release.read('plugin.yml'):
            raise RuntimeError('Unexpected plugin version')
        for name in PACKS:
            if release.read('resource-packs/' + name) != replacements['resource-packs/' + name]:
                raise RuntimeError('Embedded pack differs: ' + name)
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
        'baseline': BASE.relative_to(ROOT).as_posix(), 'baseline_sha256': BASE_SHA256, 'baseline_size': BASE.stat().st_size,
        'compiled_classes': len(list(classes.rglob('*.class'))), 'preserved_baseline_entries': preserved,
        'source_sha256': hashes, 'copied_to_dist': not args.no_copy,
    }
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps({k: v for k, v in report.items() if k != 'source_sha256'}, indent=2))


if __name__ == '__main__':
    main()
