"""Build the scoped e2.132 Vault weapon release without replacing unrelated classes or packs."""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import shutil
import zipfile

import build_plugin_jars as build
from release_jar_checks import check_jar

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output/vault-weapon-drops-20261025'
BEFORE = OUT / 'before'
BASE = BEFORE / 'dist/evergarden.jar'
VERSION = '3.0.0-e2.132-vault-weapons'
FAMILIES = ('com/example/voidscape/item/RelicService', 'com/example/voidscape/item/VaultLootTable')


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def belongs(name: str) -> bool:
    return any(name == family + '.class' or name.startswith(family + '$') for family in FAMILIES)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--promote', action='store_true', help='Replace dist only after validating the candidate on isolated Paper')
    args = parser.parse_args()
    manifest = json.loads((BEFORE / 'manifest.json').read_text(encoding='utf8'))
    if sha(BASE) != manifest['dist/evergarden.jar']:
        raise RuntimeError('The baseline backup changed')
    magic = ROOT / 'dist/advance-magic.jar'
    if sha(magic) != manifest['dist/advance-magic.jar']:
        raise RuntimeError('Advance Magic changed during this build; review its compatibility before continuing')
    release = OUT / 'release'
    release.mkdir(parents=True, exist_ok=True)
    jar = release / 'evergarden.jar'
    if args.promote:
        report = json.loads((OUT / 'release-build.json').read_text(encoding='utf8'))
        if sha(jar) != report['sha256']:
            raise RuntimeError('Tested candidate changed')
        for source, digest in report['sources'].items():
            if sha(ROOT / source) != digest:
                raise RuntimeError('Source changed after candidate build: ' + source)
        if sha(ROOT / 'dist/evergarden.jar') != manifest['dist/evergarden.jar']:
            raise RuntimeError('Current dist no longer matches the reviewed baseline')
        for target in (ROOT / 'dist/evergarden.jar', ROOT / 'dist' / f'evergarden-{VERSION}.jar'):
            staged = target.with_suffix('.jar.staged')
            shutil.copy2(jar, staged)
            os.replace(staged, target)
        report['copied_to_dist'] = True
        (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
        print('Promoted', VERSION, report['sha256'])
        return

    sources = [ROOT / 'evergarden/src/main/java' / (family + '.java') for family in FAMILIES]
    classpath = [str(BASE), str(magic)] + [entry for entry in build.pinned_classpath(build.EVERGARDEN_ARGS)
                                         if not entry.replace('\\', '/').endswith('dist/evergarden.jar')]
    build.OUT = release
    classes = release / 'classes'
    build.compile_sources('vault-weapons', sources, classpath, classes)
    metadata_path = ROOT / 'evergarden/src/main/resources/plugin.yml'
    metadata = metadata_path.read_bytes()
    if ("version: '" + VERSION + "'").encode() not in metadata:
        raise RuntimeError('Unexpected plugin version')
    with zipfile.ZipFile(BASE) as baseline, zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as result:
        for entry in baseline.infolist():
            if belongs(entry.filename) or entry.filename == 'plugin.yml':
                continue
            result.writestr(copy.copy(entry), baseline.read(entry.filename))
        for path in sorted(classes.rglob('*.class')):
            name = path.relative_to(classes).as_posix()
            if not belongs(name):
                raise RuntimeError('Unexpected compiled class: ' + name)
            result.write(path, name)
        result.writestr('plugin.yml', metadata)
    preserved = 0
    with zipfile.ZipFile(BASE) as baseline, zipfile.ZipFile(jar) as candidate:
        names = candidate.namelist()
        if candidate.testzip() or len(names) != len(set(names)):
            raise RuntimeError('Invalid JAR archive')
        for name in baseline.namelist():
            if not belongs(name) and name != 'plugin.yml':
                if baseline.read(name) != candidate.read(name):
                    raise RuntimeError('Unrelated entry changed: ' + name)
                preserved += 1
        changed = sorted(name for name in names if belongs(name) or name == 'plugin.yml')
    check_jar(jar)
    report = {'version': VERSION, 'sha256': sha(jar), 'baseline_sha256': sha(BASE),
              'advance_magic_sha256': sha(magic), 'preserved_baseline_entries': preserved,
              'changed_entries': changed,
              'sources': {str(path.relative_to(ROOT).as_posix()): sha(path) for path in [*sources, metadata_path]},
              'copied_to_dist': False}
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
