"""Build e2.139 Vault balance over the delivered baseline; promote only the Paper-tested candidate."""
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
OUT = ROOT / 'output/vault-balance-20261026'
VERSION = '3.0.0-e2.139-vault-balance'
FAMILIES = ('com/example/voidscape/item/RelicService', 'com/example/voidscape/item/VaultLootTable')
RESOURCES = ('plugin.yml', 'config.yml')


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def belongs(name: str) -> bool:
    return any(name == family + '.class' or name.startswith(family + '$') for family in FAMILIES)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--promote', action='store_true')
    args = parser.parse_args()
    before = OUT / 'before'
    base = before / 'evergarden.jar'
    magic = ROOT / 'dist/advance-magic.jar'
    before.mkdir(parents=True, exist_ok=True)
    manifest_path = before / 'manifest.json'
    if not manifest_path.exists():
        shutil.copy2(ROOT / 'dist/evergarden.jar', base)
        manifest_path.write_text(json.dumps({'garden_sha256': sha(base), 'magic_sha256': sha(magic)}, indent=2) + '\n', encoding='utf8')
    manifest = json.loads(manifest_path.read_text(encoding='utf8'))
    if sha(base) != manifest['garden_sha256'] or sha(magic) != manifest['magic_sha256']:
        raise RuntimeError('Baseline or Advance Magic changed; review before building')
    release = OUT / 'release'
    release.mkdir(parents=True, exist_ok=True)
    jar = release / 'evergarden.jar'
    report_path = OUT / 'release-build.json'
    if args.promote:
        report = json.loads(report_path.read_text(encoding='utf8'))
        if sha(jar) != report['sha256']:
            raise RuntimeError('Candidate changed after build')
        for source, digest in report['sources'].items():
            if sha(ROOT / source) != digest:
                raise RuntimeError('Source changed after build: ' + source)
        for name, disabled in (('paper-active-v2', False), ('paper-disabled', True)):
            receipt = json.loads((OUT / name / 'vault-weapon-validation.json').read_text(encoding='utf8'))
            if (receipt['garden_sha256'] != sha(jar) or receipt['magic_sha256'] != sha(magic)
                    or receipt['probe_sha256'] != sha(ROOT / 'evergarden/tests/VaultWeaponChecks.java')
                    or receipt['disabled_magic'] != disabled or not receipt['report'].startswith('PASS')):
                raise RuntimeError('Candidate lacks matching Paper validation: ' + name)
        if sha(ROOT / 'dist/evergarden.jar') not in (manifest['garden_sha256'], report['sha256']):
            raise RuntimeError('Delivered JAR changed after baseline capture')
        for target in (ROOT / 'dist/evergarden.jar', ROOT / 'dist' / f'evergarden-{VERSION}.jar'):
            staged = target.with_suffix('.jar.staged')
            shutil.copy2(jar, staged)
            os.replace(staged, target)
        report['promoted'] = True
        report_path.write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
        print('Promoted', VERSION, report['sha256'])
        return

    sources = [ROOT / 'evergarden/src/main/java' / (family + '.java') for family in FAMILIES]
    classpath = [str(base), str(magic)] + [entry for entry in build.pinned_classpath(build.EVERGARDEN_ARGS)
                                         if not entry.replace('\\', '/').endswith('dist/evergarden.jar')]
    build.OUT = release
    classes = release / 'classes'
    build.compile_sources('vault-balance', sources, classpath, classes)
    resources = {name: ROOT / 'evergarden/src/main/resources' / name for name in RESOURCES}
    if ("version: '" + VERSION + "'").encode() not in resources['plugin.yml'].read_bytes():
        raise RuntimeError('Unexpected source version')
    with zipfile.ZipFile(base) as baseline, zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as result:
        for entry in baseline.infolist():
            if belongs(entry.filename) or entry.filename in RESOURCES:
                continue
            result.writestr(copy.copy(entry), baseline.read(entry.filename))
        for path in sorted(classes.rglob('*.class')):
            name = path.relative_to(classes).as_posix()
            if not belongs(name):
                raise RuntimeError('Unexpected compiled class: ' + name)
            result.write(path, name)
        for name, path in resources.items():
            result.writestr(name, path.read_bytes())
    preserved = 0
    with zipfile.ZipFile(base) as baseline, zipfile.ZipFile(jar) as candidate:
        names = candidate.namelist()
        if candidate.testzip() or len(names) != len(set(names)):
            raise RuntimeError('Invalid JAR archive')
        for name in baseline.namelist():
            if not belongs(name) and name not in RESOURCES:
                if baseline.read(name) != candidate.read(name):
                    raise RuntimeError('Unrelated entry changed: ' + name)
                preserved += 1
    check_jar(jar)
    report = {'version': VERSION, 'sha256': sha(jar), 'baseline_sha256': sha(base),
              'magic_sha256': sha(magic), 'preserved_baseline_entries': preserved,
              'changed_entries': sorted(name for name in names if belongs(name) or name in RESOURCES),
              'sources': {path.relative_to(ROOT).as_posix(): sha(path) for path in [*sources, *resources.values()]},
              'promoted': False}
    report_path.write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
