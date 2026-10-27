"""Build only the Legendary light rain changes over the delivered v3 JAR.

Snapshots the three owned Java sources and two plugin resources. Unrelated
classes and embedded packs are copied from the previous delivery, so concurrent
work elsewhere in the shared workspace is not swept into this release.
"""
from pathlib import Path
import hashlib
import json
import os
import shutil
import zipfile

import build_plugin_jars as build

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output/light-rain-v4-build-20261024'
BASE = ROOT / 'dist/advance-magic-1.26.3-beacon-volley.jar'
VERSION = '1.26.4-radiant-rain'
OWNED = (
    'com/example/advancemagic/elemental/ElementalSpell.java',
    'com/example/advancemagic/elemental/ElementalSpellService.java',
    'com/example/advancemagic/spell/ElementalVfx.java',
)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    baseline_hash = sha(BASE.read_bytes())
    if baseline_hash != '5933e620519026a27f6a4001f9d1bb9616597c07e9c2bed281d446104693b449':
        raise RuntimeError('The immutable v3 delivery differs from the expected baseline.')
    sources = []
    hashes = {}
    for relative in OWNED:
        source = ROOT / 'advance-magic/src/main/java' / relative
        snapshot = OUT / 'source' / relative
        snapshot.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, snapshot)
        sources.append(snapshot)
        hashes[source.relative_to(ROOT).as_posix()] = sha(snapshot.read_bytes())
    resources = {}
    for name in ('plugin.yml', 'config.yml'):
        source = ROOT / 'advance-magic/src/main/resources' / name
        snapshot = OUT / 'resources' / name
        snapshot.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, snapshot)
        resources[name] = snapshot.read_bytes()
        hashes[source.relative_to(ROOT).as_posix()] = sha(resources[name])
    build.OUT = OUT
    classes = OUT / 'classes'
    build.compile_sources('legendary-light-rain', sources,
                          [BASE.as_posix()] + build.pinned_classpath(build.ADVANCE_ARGS), classes)
    prefixes = tuple(relative.removesuffix('.java') for relative in OWNED)
    def replaced(name):
        return any(name == prefix + '.class' or name.startswith(prefix + '$') for prefix in prefixes)
    jar = OUT / 'advance-magic.jar'
    with zipfile.ZipFile(BASE) as previous, zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as result:
        preserved = 0
        for entry in previous.infolist():
            if replaced(entry.filename) or entry.filename in resources:
                continue
            result.writestr(entry, previous.read(entry.filename))
            preserved += 1
        for path in sorted(classes.rglob('*.class')):
            result.write(path, path.relative_to(classes).as_posix())
        for name, data in resources.items():
            result.writestr(name, data)
    # Archive checks are part of packaging; no gameplay test server is started.
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
        'bedrock_pack_version': [2, 28, 0], 'embedded_packs': 'Unchanged from v3',
        'damage_multiplier': 16, 'previous_damage_multiplier': 4,
        'damage_split': {'opening': .25, 'rain': .75, 'default_rain_pulses': 15},
        'damage_radius_blocks': 7, 'duration_seconds_default': 30, 'pull_strength_default': .5,
        'validation': 'Java 25 compilation and archive packaging only; no gameplay or GPU client tests run for v4',
        'deployment': 'Workspace JAR only',
    }
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
