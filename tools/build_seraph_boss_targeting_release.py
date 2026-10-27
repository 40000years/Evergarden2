"""Package the Seraph boss targeting fix over the delivered slim Light Lance JAR.

Only ArmorCombatService and plugin.yml are replaced. Client packs and all other
classes are copied from the immutable 1.29.0 release. No test suite is invoked.
"""
import hashlib
import json
import os
import shutil
import zipfile
from pathlib import Path

import build_plugin_jars as build

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output/seraph-boss-targeting-20261025/release'
BASE = ROOT / 'dist/advance-magic-1.29.0-light-lance.jar'
BASE_SHA256 = '61f867146501ecc81c5c276dde93b1e9a00c4e298425912ea06b810372e0bd26'
VERSION = '1.29.1-seraph-boss-targeting'
PREFIX = 'com/example/advancemagic/item/ArmorCombatService'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    if sha(BASE.read_bytes()) != BASE_SHA256:
        raise RuntimeError('Delivered Light Lance baseline has changed')
    current = ROOT / 'dist/advance-magic.jar'
    if sha(current.read_bytes()) != BASE_SHA256:
        raise RuntimeError('Another release replaced advance-magic.jar; use its new baseline')
    OUT.mkdir(parents=True, exist_ok=True)
    original = ROOT / 'advance-magic/src/main/java' / (PREFIX + '.java')
    snapshot = OUT / 'source' / (PREFIX + '.java')
    snapshot.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(original, snapshot)
    metadata = (ROOT / 'advance-magic/src/main/resources/plugin.yml').read_bytes()
    if ('version: ' + VERSION).encode() not in metadata:
        raise RuntimeError('Source plugin version differs from this release')
    build.OUT = OUT
    classes = OUT / 'classes'
    build.compile_sources('seraph-boss-targeting', [snapshot],
                          [BASE.as_posix()] + build.pinned_classpath(build.ADVANCE_ARGS), classes)
    jar = OUT / 'advance-magic.jar'
    with zipfile.ZipFile(BASE) as previous, zipfile.ZipFile(jar, 'w') as release:
        for entry in previous.infolist():
            name = entry.filename
            if name == 'plugin.yml' or name == PREFIX + '.class' or name.startswith(PREFIX + '$'):
                continue
            release.writestr(entry, previous.read(name))
        for path in sorted(classes.rglob('*.class')):
            release.write(path, path.relative_to(classes).as_posix(), compress_type=zipfile.ZIP_DEFLATED)
        release.writestr('plugin.yml', metadata, compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(BASE) as previous, zipfile.ZipFile(jar) as release:
        changed = sorted(name for name in release.namelist()
                         if name not in previous.namelist() or release.read(name) != previous.read(name))
        if any(name != 'plugin.yml' and not name.startswith(PREFIX) for name in changed):
            raise RuntimeError('Unexpected replacement outside the targeting fix')
        packs = {name: sha(release.read(name)) for name in release.namelist()
                 if name.startswith('resource-packs/')}
        for name, digest in packs.items():
            if digest != sha(previous.read(name)):
                raise RuntimeError('Resource pack changed: ' + name)
    if sha(current.read_bytes()) != BASE_SHA256:
        raise RuntimeError('Another release replaced advance-magic.jar during compilation')
    for target in (ROOT / 'dist' / ('advance-magic-' + VERSION + '.jar'), current):
        staged = target.with_suffix('.jar.staged')
        shutil.copy2(jar, staged)
        os.replace(staged, target)
    report = {'version': VERSION, 'size': jar.stat().st_size, 'sha256': sha(jar.read_bytes()),
              'baseline': BASE.relative_to(ROOT).as_posix(), 'baseline_sha256': BASE_SHA256,
              'changed_entries': changed, 'resource_pack_sha256': packs,
              'source_sha256': sha(snapshot.read_bytes()), 'tests_run': False}
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
