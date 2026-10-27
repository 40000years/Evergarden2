"""Build the slim Evergarden release (e2.129) over the delivered e2.128 JAR.

Nothing players see changes. Over e2.128 this:
* drops the river-boss *.bbmodel.bak source backups (never read by the plugin),
* drops the mythical five's ModelEngine blueprints (no models until the legendary tier opens),
* swaps in the slimmed packs and pet portraits from build_evergarden_slim_packs.py,
* recompiles only the pet classes that now skip the mythical five,
* and keeps every other e2.128 entry byte-identical.

    python tools/build_evergarden_slim_release.py [--no-copy]
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import zipfile
from pathlib import Path

import build_plugin_jars as build

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output/evergarden-slim-20261025/release'
BASE = ROOT / 'dist/evergarden-3.0.0-e2.128-pet-menagerie.jar'
BASE_SHA256 = '696c773a2c5dc53da28a099fad147938d77e29129e9c8a1286eacf588503d007'
VERSION = '3.0.0-e2.129-slim'
OWNED = (
    'com/example/voidscape/pet/PetCatalog.java',
    'com/example/voidscape/pet/PetModelPreviews.java',
    'com/example/voidscape/pet/PetBlueprintStaging.java',
)
RESOURCES = ROOT / 'evergarden/src/main/resources'
REPLACED = {
    'plugin.yml': RESOURCES / 'plugin.yml',
    'judge-design.json': RESOURCES / 'judge-design.json',
    'pet-portraits.zip': RESOURCES / 'pet-portraits.zip',
    'modelengine/pets/manifest.json': RESOURCES / 'modelengine/pets/manifest.json',
    'resource-packs/evergarden-java.zip': ROOT / 'evergarden/dist/evergarden-java.zip',
    'resource-packs/judge-java.zip': ROOT / 'evergarden/dist/judge-java.zip',
    'resource-packs/evergarden-bedrock.mcpack': ROOT / 'evergarden/dist/evergarden-bedrock.mcpack',
    'resource-packs/pack-hashes.json': ROOT / 'evergarden/dist/pack-hashes.json',
}
DROPPED = re.compile(r'^modelengine/rivers/.+\.bak$|^modelengine/pets/everpet_(aetherius|ignis_draconis|zephyros|solaris|fenrir)_[a-z]+\.bbmodel$')


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--no-copy', action='store_true', help='Leave the JAR in output/ instead of replacing dist/')
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    if sha(BASE.read_bytes()) != BASE_SHA256:
        raise RuntimeError('The delivered e2.128 baseline changed unexpectedly.')
    hashes = json.loads((ROOT / 'evergarden/dist/pack-hashes.json').read_text(encoding='utf8'))
    for name, digest in hashes.items():
        if hashlib.sha1((ROOT / 'evergarden/dist' / name).read_bytes()).hexdigest() != digest:
            raise RuntimeError('pack-hashes.json does not match ' + name)
    if json.loads((RESOURCES / 'judge-design.json').read_text(encoding='utf8'))['bedrock-version'] != '3.55.0':
        raise RuntimeError('Run build_evergarden_slim_packs.py first (expected Bedrock 3.55.0)')
    sources, report_hashes = [], {}
    for relative in OWNED:
        original = ROOT / 'evergarden/src/main/java' / relative
        snapshot = OUT / 'source' / relative
        snapshot.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(original, snapshot)
        sources.append(snapshot)
        report_hashes[original.relative_to(ROOT).as_posix()] = sha(snapshot.read_bytes())
    replacements = {name: path.read_bytes() for name, path in REPLACED.items()}
    for name, path in REPLACED.items():
        report_hashes[path.relative_to(ROOT).as_posix()] = sha(replacements[name])
    build.OUT = OUT
    classes = OUT / 'classes'
    shutil.rmtree(classes, ignore_errors=True)
    classpath = [BASE.as_posix(), (ROOT / 'dist/advance-magic.jar').as_posix()] + build.pinned_classpath(build.EVERGARDEN_ARGS)
    build.compile_sources('evergarden-slim', sources, classpath, classes)
    prefixes = tuple(relative.removesuffix('.java') for relative in OWNED)

    def replaced_class(name):
        return any(name == prefix + '.class' or name.startswith(prefix + '$') for prefix in prefixes)

    jar = OUT / 'evergarden.jar'
    dropped = []
    with zipfile.ZipFile(BASE) as previous, zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as result:
        preserved = 0
        for entry in previous.infolist():
            if DROPPED.search(entry.filename):
                dropped.append(entry.filename)
                continue
            if replaced_class(entry.filename) or entry.filename in replacements:
                continue
            result.writestr(entry, previous.read(entry.filename))
            preserved += 1
        for path in sorted(classes.rglob('*.class')):
            result.write(path, path.relative_to(classes).as_posix())
        for name, data in replacements.items():
            info = zipfile.ZipInfo(name, date_time=(2026, 10, 25, 0, 0, 0))
            result.writestr(info, data, compress_type=zipfile.ZIP_STORED if name.endswith(('.zip', '.mcpack')) else zipfile.ZIP_DEFLATED)
    if len([n for n in dropped if n.endswith('.bak')]) != 7 or len([n for n in dropped if n.endswith('.bbmodel') and not n.endswith('.bak')]) != 15:
        raise RuntimeError('Unexpected dropped entries: ' + ', '.join(dropped))
    with zipfile.ZipFile(jar) as release:
        names = release.namelist()
        if release.testzip() is not None or len(names) != len(set(names)):
            raise RuntimeError('Invalid release archive')
        if ("version: '" + VERSION + "'").encode() not in release.read('plugin.yml'):
            raise RuntimeError('Unexpected plugin version')
        blueprints = [n for n in names if n.startswith('modelengine/pets/') and n.endswith('.bbmodel')]
        if len(blueprints) != 96:
            raise RuntimeError(f'Expected 96 pet blueprints, found {len(blueprints)}')
        for name, data in replacements.items():
            if release.read(name) != data:
                raise RuntimeError('Embedded file differs: ' + name)
    build.check_jar(jar)
    digest = sha(jar.read_bytes())
    if not args.no_copy:
        previous_dir = OUT / 'previous'
        previous_dir.mkdir(exist_ok=True)
        current = ROOT / 'dist/evergarden.jar'
        if current.exists() and not (previous_dir / current.name).exists():
            shutil.copy2(current, previous_dir / current.name)
        for target in (current, ROOT / 'dist' / ('evergarden-' + VERSION + '.jar')):
            staged = target.with_suffix('.jar.staged')
            shutil.copy2(jar, staged)
            os.replace(staged, target)
    report = {
        'version': VERSION, 'sha256': digest, 'size': jar.stat().st_size,
        'baseline': BASE.relative_to(ROOT).as_posix(), 'baseline_sha256': BASE_SHA256, 'baseline_size': BASE.stat().st_size,
        'compiled_classes': len(list(classes.rglob('*.class'))), 'preserved_baseline_entries': preserved,
        'dropped': dropped, 'source_sha256': report_hashes, 'copied_to_dist': not args.no_copy,
    }
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps({k: v for k, v in report.items() if k not in ('source_sha256', 'dropped')}, indent=2))


if __name__ == '__main__':
    main()
