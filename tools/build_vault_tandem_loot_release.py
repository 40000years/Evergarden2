"""Build the missing two-seat staff reward over current dist, then promote after Paper validation.

Only reward/test GUI classes, Core of Legend lore and plugin.yml change. Resource packs and all
other classes remain byte-identical to the frozen delivery baseline.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import zipfile
import build_plugin_jars as build

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output/vault-tandem-loot-20261030'
VERSION = '3.0.0-e2.172-vault-tandem-loot'
FAMILIES = tuple('com/example/voidscape/' + name for name in
                 ('item/RelicService', 'item/VaultLootTable', 'gui/AdminTestGui', 'boss/SoulOrbs'))
PROBE = ROOT / 'evergarden/tests/VaultEconomyChecks.java'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def owned(name):
    return any(name == family + '.class' or name.startswith(family + '$') for family in FAMILIES)


def version(data):
    text, count = re.subn(r'(?m)^version:[^\r\n]*', "version: '" + VERSION + "'", data.decode('utf8'))
    if count != 1:
        raise RuntimeError('Expected one plugin version')
    return text.encode('utf8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--promote', action='store_true')
    parser.add_argument('--validation-server', type=Path, help='Reuse an existing isolated Paper fixture under output/')
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    garden, magic = ROOT / 'dist/evergarden.jar', ROOT / 'dist/advance-magic.jar'
    candidate, baseline = OUT / 'evergarden.jar', OUT / 'baseline-evergarden.jar'
    report_path = OUT / 'build-report.json'
    if args.promote:
        report = json.loads(report_path.read_text(encoding='utf8'))
        validation = (args.validation_server or OUT / 'paper').resolve()
        if not validation.is_relative_to((ROOT / 'output').resolve()):
            raise RuntimeError('Validation fixture must stay under output/')
        receipt = json.loads((validation / 'vault-economy-validation.json').read_text(encoding='utf8'))
        for path, digest in report['sources'].items():
            if sha(ROOT / path) != digest:
                raise RuntimeError('Source changed after validation: ' + path)
        if sha(garden) != report['baseline_sha256'] or sha(baseline) != report['baseline_sha256']:
            raise RuntimeError('Delivery baseline changed')
        if sha(candidate) != report['candidate_sha256'] or receipt['garden_sha256'] != sha(candidate):
            raise RuntimeError('Candidate differs from Paper validation')
        if sha(magic) != report['magic_sha256'] or receipt['magic_sha256'] != sha(magic):
            raise RuntimeError('Advance Magic differs from Paper validation')
        if receipt['probe_sha256'] != sha(PROBE) or not receipt['report'].startswith('PASS '):
            raise RuntimeError('Missing matching successful probe')
        # Retain small evidence independently of a reusable Paper fixture.
        shutil.copy2(PROBE, OUT / 'validated-VaultEconomyChecks.java')
        (OUT / 'test-receipt.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf8')
        shutil.copy2(validation / 'vault-economy-result.txt', OUT / 'vault-economy-result.txt')
        plugin = ROOT / 'evergarden/src/main/resources/plugin.yml'
        plugin.write_bytes(version(plugin.read_bytes()))
        staged = garden.with_suffix('.jar.staged')
        shutil.copy2(candidate, staged)
        os.replace(staged, garden)
        report['promoted'] = True
        report['validation'] = receipt
        report_path.write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
        print('Promoted ' + VERSION + '; ' + receipt['report'])
        return
    if not baseline.exists():
        shutil.copy2(garden, baseline)
    if sha(garden) != sha(baseline):
        raise RuntimeError('Delivery baseline changed; use a new release builder')
    sources = [ROOT / 'evergarden/src/main/java' / (family + '.java') for family in FAMILIES]
    hashes = {path.relative_to(ROOT).as_posix(): sha(path) for path in sources}
    build.OUT = OUT
    classes = OUT / 'classes'
    build.compile_sources('vault-tandem-loot', sources, [str(baseline), str(magic)]
                          + build.pinned_classpath(build.EVERGARDEN_ARGS), classes)
    if any(sha(ROOT / path) != digest for path, digest in hashes.items()):
        raise RuntimeError('Sources changed during compilation')
    compiled = {path.relative_to(classes).as_posix(): path.read_bytes() for path in classes.rglob('*.class')}
    if not compiled or any(not owned(name) for name in compiled):
        raise RuntimeError('Unexpected compiler output')
    if any(family + '.class' not in compiled for family in FAMILIES):
        raise RuntimeError('Missing compiled family')
    date = (2026, 10, 30, 0, 0, 0)
    with zipfile.ZipFile(baseline) as old, zipfile.ZipFile(candidate, 'w', zipfile.ZIP_DEFLATED) as new:
        for entry in old.infolist():
            if not owned(entry.filename) and entry.filename != 'plugin.yml':
                new.writestr(entry, old.read(entry.filename))
        for name, data in {'plugin.yml': version(old.read('plugin.yml')), **compiled}.items():
            new.writestr(zipfile.ZipInfo(name, date), data, compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(baseline) as old, zipfile.ZipFile(candidate) as new:
        if new.testzip() or len(new.namelist()) != len(set(new.namelist())):
            raise RuntimeError('Invalid candidate archive')
        preserved = [name for name in old.namelist() if not owned(name) and name != 'plugin.yml']
        if any(old.read(name) != new.read(name) for name in preserved):
            raise RuntimeError('Unrelated JAR entry changed')
    build.check_jar(candidate)
    report = {'version': VERSION, 'sources': hashes, 'baseline_sha256': sha(baseline),
              'candidate_sha256': sha(candidate), 'magic_sha256': sha(magic),
              'preserved_entries': len(preserved), 'promoted': False}
    report_path.write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
