"""Evergarden e2.200: ship each pet texture once and stop re-reading pet blueprints on every startup.

    python tools/build_pet_blueprint_slim_release.py --srccheck   unmodified pet staging sources == shipped classes?
    python tools/build_pet_blueprint_slim_release.py              build the candidate over e2.199
    python tools/build_pet_blueprint_slim_release.py --promote    replace dist/evergarden.jar (after validation)

Changes against the e2.199 baseline:
- the 282 modelengine/pets/*.bbmodel entries lose their embedded base64 PNGs (tools/pet_blueprint_slim.py);
  modelengine/pets/blueprint-textures.json maps them back to native-pets.zip;
- PetBlueprintInstaller / PetBlueprintStaging are recompiled and PetBlueprints is new;
- plugin.yml carries the new version.
Every other entry, including native-pets.zip and the resource packs, stays byte-identical, so pack hashes
and the pack mirror are unaffected. Nothing is published or deployed.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sys
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_plugin_jars as build  # noqa: E402
import pet_blueprint_slim as slim  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output/pet-blueprint-slim'
VERSION = '3.0.1-e2.200-slim-blueprints'
BASE_VERSION = '3.0.1-e2.199-pet-atelier'
BASE_SHA256 = '87c6f2d171ba9464a2b842324fc885188162112b4e8eacc11330f867f7cf08df'
DATE = (2026, 11, 8, 0, 0, 0)
PET = 'com/example/voidscape/pet/'
FAMILIES = (PET + 'PetBlueprintInstaller', PET + 'PetBlueprintStaging', PET + 'PetBlueprints')
SHIPPED = FAMILIES[:2]


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def owned(name, families=FAMILIES):
    return any(name == f + '.class' or name.startswith(f + '$') for f in families)


def version(data):
    text, count = re.subn(r'(?m)^version:[^\r\n]*', "version: '" + VERSION + "'", data.decode('utf8'))
    if count != 1:
        raise RuntimeError('Expected one plugin version')
    return text.encode('utf8')


def baseline():
    OUT.mkdir(parents=True, exist_ok=True)
    garden, jar = ROOT / 'dist/evergarden.jar', OUT / 'baseline-evergarden.jar'
    if not jar.exists():
        # dist/evergarden-<BASE_VERSION>.jar is an earlier build under the same version; the GitHub release
        # resources-e2.199-am1.41.0 ships this exact JAR, so pin its hash instead.
        if sha(garden) != BASE_SHA256:
            raise RuntimeError('dist/evergarden.jar is not the released ' + BASE_VERSION)
        shutil.copy2(garden, jar)
    if sha(garden) != sha(jar):
        raise RuntimeError('dist/evergarden.jar changed since the baseline was taken; use a new release builder')
    return jar


def compile_families(jar, families):
    sources = [ROOT / 'evergarden/src/main/java' / (f + '.java') for f in families]
    hashes = {p.relative_to(ROOT).as_posix(): sha(p) for p in sources}
    build.OUT = OUT
    classes = OUT / 'classes'
    build.compile_sources('pet-blueprint-slim', sources,
                          [str(jar), str(ROOT / 'dist/advance-magic.jar')] + build.pinned_classpath(build.EVERGARDEN_ARGS), classes)
    if any(sha(ROOT / p) != d for p, d in hashes.items()):
        raise RuntimeError('Sources changed during compilation')
    return {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')}, hashes


def srccheck():
    jar = baseline()
    compiled, _ = compile_families(jar, SHIPPED)
    with zipfile.ZipFile(jar) as old:
        shipped = {n: old.read(n) for n in old.namelist() if owned(n, SHIPPED)}
    differs = sorted(n for n in set(shipped) | set(compiled) if shipped.get(n) != compiled.get(n))
    print(json.dumps({'srccheck': 'DIFFERENT' if differs else 'IDENTICAL', 'classes': len(compiled), 'differs': differs}, indent=2))
    if differs:
        raise SystemExit(1)


def candidate():
    jar = baseline()
    compiled, hashes = compile_families(jar, FAMILIES)
    if any(not owned(n) for n in compiled) or any(f + '.class' not in compiled for f in FAMILIES):
        raise RuntimeError('Unexpected compiler output: ' + ', '.join(sorted(compiled)))
    target = OUT / 'evergarden.jar'
    with zipfile.ZipFile(jar) as old:
        names = old.namelist()
        blueprints = {n: old.read(n) for n in names if slim._blueprint(n)}
        reduced, stats = slim.slim({**blueprints, slim.ARCHIVE: old.read(slim.ARCHIVE)})
        if stats['blueprints'] != len(blueprints) or len(blueprints) != 282:
            raise RuntimeError(f'Expected all 282 pet blueprints to slim: {stats}')
        replaced = {'plugin.yml': version(old.read('plugin.yml')), **compiled, **reduced}
        stale = [n for n in names if owned(n) and n not in compiled]
        if stale:
            raise RuntimeError('Owned classes disappeared: ' + ', '.join(stale))
        with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as new:
            for info in old.infolist():
                if info.filename not in replaced:
                    new.writestr(info, old.read(info.filename))
            for name, data in sorted(replaced.items()):
                new.writestr(zipfile.ZipInfo(name, DATE), data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    with zipfile.ZipFile(jar) as old, zipfile.ZipFile(target) as new:
        if new.testzip() or len(new.namelist()) != len(set(new.namelist())):
            raise RuntimeError('Invalid candidate archive')
        preserved = [n for n in old.namelist() if n not in replaced]
        if any(old.read(n) != new.read(n) for n in preserved):
            raise RuntimeError('Unrelated JAR entry changed')
        entries = {n: new.read(n) for n in new.namelist() if slim._blueprint(n) or n in (slim.INDEX, slim.ARCHIVE)}
        restored = slim.verify(entries)
        index = json.loads(entries[slim.INDEX])
        if any(index['models'][n[len(slim.PREFIX):-8]]['sha256'] != hashlib.sha256(old.read(n)).hexdigest() for n in blueprints):
            raise RuntimeError('Index does not record the shipped originals')
    build.check_jar(target)
    report = {'version': VERSION, 'sources': hashes, 'baseline_sha256': sha(jar), 'candidate_sha256': sha(target),
              'magic_sha256': sha(ROOT / 'dist/advance-magic.jar'), 'baseline_bytes': jar.stat().st_size,
              'candidate_bytes': target.stat().st_size, 'restored_blueprints': restored, 'slim': stats,
              'preserved_entries': len(preserved), 'replaced_classes': sorted(compiled), 'promoted': False}
    (OUT / 'build-report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps({k: v for k, v in report.items() if k != 'sources'}, indent=2))


def promote():
    report = json.loads((OUT / 'build-report.json').read_text(encoding='utf8'))
    receipt = json.loads((OUT / 'validation.json').read_text(encoding='utf8'))
    if receipt.get('result') != 'PASS':
        raise RuntimeError('Validation did not pass')
    if not sha(OUT / 'evergarden.jar') == report['candidate_sha256'] == receipt['garden_sha256']:
        raise RuntimeError('Candidate changed after validation')
    if sha(ROOT / 'dist/evergarden.jar') != report['baseline_sha256']:
        raise RuntimeError('dist/evergarden.jar is no longer the baseline')
    if any(sha(ROOT / p) != d for p, d in report['sources'].items()):
        raise RuntimeError('Sources changed after validation')
    for path in (ROOT / 'dist/evergarden.jar', ROOT / f'dist/evergarden-{VERSION}.jar'):
        staged = path.with_suffix('.jar.staged')
        shutil.copy2(OUT / 'evergarden.jar', staged)
        os.replace(staged, path)
    plugin = ROOT / 'evergarden/src/main/resources/plugin.yml'
    plugin.write_text(re.sub(r"(?m)^version:[^\r\n]*", "version: '" + VERSION + "'", plugin.read_text(encoding='utf8')),
                      encoding='utf8')
    report.update(promoted=True, validation=receipt)
    (OUT / 'build-report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print('Promoted ' + VERSION)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    group = parser.add_mutually_exclusive_group()
    group.add_argument('--srccheck', action='store_true')
    group.add_argument('--promote', action='store_true')
    args = parser.parse_args()
    srccheck() if args.srccheck else promote() if args.promote else candidate()


if __name__ == '__main__':
    main()
