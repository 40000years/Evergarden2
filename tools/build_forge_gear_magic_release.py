"""Build Advance Magic 1.38.3-forge-gear over the delivered JAR (1.38.2-spell-audit).

EvergardenForge 0.6.0 can reforge legendary weapons. The held-weapon upkeep (every four ticks) used to
reset a legendary weapon's lore to exactly the skill hint, which erased the forge's affix block again and
again. It now keeps lines from other plugins and only makes sure the hint closes the lore. Only
LegendaryWeaponService (and its nested classes) and plugin.yml change; every other entry, the packs
included, stays byte-identical.

    python tools/build_forge_gear_magic_release.py [--validate] [--promote]

--validate runs advance-magic/tests/run_legendary_weapon_integration.py (real Paper + ModelEngine + Geyser)
on the candidate and writes a receipt; --promote only ships the candidate that receipt names.
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import build_plugin_jars as build  # noqa: E402

OUT = ROOT / 'output/forge-gear-20261102/magic'
VERSION = '1.38.3-forge-gear'
BASE_VERSION = '1.38.2-spell-audit'
OWNED = ('com/example/advancemagic/item/LegendaryWeaponService',)
SOURCE_SERVER = ROOT / 'output/entry-items-check-20261031'
PROBES = [ROOT / 'advance-magic/tests' / name for name in ('run_legendary_weapon_integration.py', 'LegendaryWeaponIntegrationChecks.java')]
RECEIPT = 'forge-gear-magic-validation.json'
DATE = (2026, 11, 2, 0, 0, 0)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def owned(name):
    return any(name == family + '.class' or name.startswith(family + '$') for family in OWNED)


def with_version(text):
    text, count = re.subn(r"(?m)^version:[^\r\n]*", "version: '" + VERSION + "'", text)
    if count != 1:
        raise RuntimeError('plugin.yml must contain exactly one version')
    return text


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--validate', action='store_true')
    parser.add_argument('--promote', action='store_true')
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    delivered = ROOT / 'dist/advance-magic.jar'
    baseline = OUT / 'baseline-advance-magic.jar'
    if not baseline.exists():
        with zipfile.ZipFile(delivered) as jar:
            if f"version: '{BASE_VERSION}'" not in jar.read('plugin.yml').decode('utf8'):
                raise RuntimeError('dist/advance-magic.jar is not ' + BASE_VERSION)
        shutil.copy2(delivered, baseline)
    with zipfile.ZipFile(baseline) as jar:
        plugin = jar.read('plugin.yml').decode('utf8')

    classes = OUT / 'classes'
    shutil.rmtree(classes, ignore_errors=True)
    build.OUT = OUT
    sources = [ROOT / 'advance-magic/src/main/java' / (family + '.java') for family in OWNED]
    source_hashes = {p.relative_to(ROOT).as_posix(): sha(p.read_bytes()) for p in sources}
    build.compile_sources('forge-gear-magic', sources, [baseline.as_posix(), (ROOT / 'dist/evergarden.jar').as_posix()]
                          + build.pinned_classpath(build.ADVANCE_ARGS), classes)
    if any(sha((ROOT / p).read_bytes()) != h for p, h in source_hashes.items()):
        raise RuntimeError('Owned sources changed during compilation')

    candidate = OUT / 'advance-magic.jar'
    with zipfile.ZipFile(baseline) as previous, zipfile.ZipFile(candidate, 'w', zipfile.ZIP_DEFLATED) as result:
        for info in previous.infolist():
            if not owned(info.filename) and info.filename != 'plugin.yml':
                result.writestr(info, previous.read(info.filename))
        for path in sorted(classes.rglob('*.class')):
            name = path.relative_to(classes).as_posix()
            if not owned(name):
                raise RuntimeError('Unexpected compiled class: ' + name)
            result.writestr(zipfile.ZipInfo(name, DATE), path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
        result.writestr(zipfile.ZipInfo('plugin.yml', DATE), with_version(plugin).encode('utf8'), compress_type=zipfile.ZIP_DEFLATED)
    preserved = 0
    with zipfile.ZipFile(candidate) as release, zipfile.ZipFile(baseline) as previous:
        if release.testzip() is not None or len(release.namelist()) != len(set(release.namelist())):
            raise RuntimeError('Invalid release archive')
        for info in previous.infolist():
            if not owned(info.filename) and info.filename != 'plugin.yml':
                if release.read(info.filename) != previous.read(info.filename):
                    raise RuntimeError('Unrelated entry changed: ' + info.filename)
                preserved += 1
    build.check_jar(candidate)
    digest = sha(candidate.read_bytes())
    probe_hashes = {p.name: sha(p.read_bytes()) for p in PROBES}

    if args.validate:
        server = OUT / f'paper-{digest[:10]}-legendary'
        if server.exists():
            shutil.rmtree(server)
        run = subprocess.run([sys.executable, str(PROBES[0]), '--source-server', str(SOURCE_SERVER), '--server', str(server),
                              '--plugin-jar', str(candidate)], capture_output=True, text=True, encoding='utf8', errors='replace')
        result = server / 'legendary-weapon-integration-result.txt'
        report = result.read_text(encoding='utf8').strip() if result.exists() else 'FAIL ' + (run.stdout + run.stderr)[-2000:]
        print('legendary integration:', report)
        (OUT / RECEIPT).write_text(json.dumps({'sha256': digest, 'report': report, 'probes': probe_hashes}, indent=2) + '\n', encoding='utf8')
        if not report.startswith('PASS'):
            raise RuntimeError('Legendary integration failed')
    if args.promote:
        receipt = json.loads((OUT / RECEIPT).read_text(encoding='utf8'))
        if receipt['sha256'] != digest or not receipt['report'].startswith('PASS') or receipt['probes'] != probe_hashes:
            raise RuntimeError('Candidate lacks matching Paper validation')
        if sha(delivered.read_bytes()) != sha(baseline.read_bytes()):
            raise RuntimeError('dist/advance-magic.jar changed since the baseline was pinned')
        source_plugin = ROOT / 'advance-magic/src/main/resources/plugin.yml'
        source_plugin.write_text(with_version(source_plugin.read_text(encoding='utf8')), encoding='utf8')
        for target in (ROOT / 'dist' / f'advance-magic-{VERSION}.jar', delivered):
            staged = target.with_suffix('.jar.staged')
            shutil.copy2(candidate, staged)
            os.replace(staged, target)
    report = {'version': VERSION, 'from': BASE_VERSION, 'sha256': digest, 'baseline_sha256': sha(baseline.read_bytes()),
              'owned': OWNED, 'sources': source_hashes, 'preserved_entries': preserved, 'promoted': args.promote}
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
