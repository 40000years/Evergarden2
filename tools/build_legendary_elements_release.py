"""Build the approved seven Legendary spells over the delivered light-rain JAR.

Compile only this task's owned Java sources from a snapshot. Preserve unrelated
baseline classes and replace the four embedded pack/delivery resources together.
"""
from pathlib import Path
import hashlib
import json
import os
import shutil
import zipfile

import build_plugin_jars as build

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output/legendary-elements-release-20261024'
BASE = ROOT / 'dist/advance-magic-1.26.4-radiant-rain.jar'
VERSION = '1.27.0-legendary-elements'
OWNED = (
    'com/example/advancemagic/elemental/ElementalSpell.java',
    'com/example/advancemagic/elemental/ElementalSpellService.java',
    'com/example/advancemagic/spell/ElementalVfx.java',
    'com/example/advancemagic/spell/LegendaryEarthHand.java',
    'com/example/advancemagic/spell/MythicLineVisuals.java',
)
PACKS = ('advance-magic-java.zip', 'advance-magic-bedrock.mcpack', 'geyser-mappings.json', 'pack-hashes.json')


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    baseline_hash = sha(BASE.read_bytes())
    if baseline_hash != '9374e1cef0da240f25b9fb43982dce954b9b04d9ad6a6876d6e3f3f4520c26c6':
        raise RuntimeError('The delivered light-rain baseline changed unexpectedly.')
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
        replacements['resource-packs/' + name] = (ROOT / 'advance-magic/dist' / name).read_bytes()
    build.OUT = OUT
    classes = OUT / 'classes'
    build.compile_sources('legendary-elements', sources,
                          [BASE.as_posix()] + build.pinned_classpath(build.ADVANCE_ARGS), classes)
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
    digest = sha(jar.read_bytes())
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
        'version': VERSION, 'jar': 'dist/advance-magic-' + VERSION + '.jar',
        'sha256': digest, 'size': jar.stat().st_size,
        'baseline': BASE.relative_to(ROOT).as_posix(), 'baseline_sha256': baseline_hash,
        'compiled_sources': len(sources), 'compiled_classes': len(list(classes.rglob('*.class'))),
        'preserved_baseline_entries': preserved, 'source_sha256': hashes,
        'bedrock_pack_version': [2, 29, 0],
        'pack_sha1': json.loads(replacements['resource-packs/pack-hashes.json']),
        'texture_collection': 'advance-magic/art/effects/legendary-elements-v1/catalog.json',
        'earth_model': 'Existing Ancient Judge left hand plus emissive component, Java and Bedrock',
        'validation': 'Java 25 compilation, source review and archive packaging only; no gameplay or live GPU client tests run',
        'deployment': 'Workspace JAR only',
    }
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
