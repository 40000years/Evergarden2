"""Pet Atelier release: the 57 expansion companions (171 forms) rebuilt as clean blocks with painted coats.

Baseline is the delivered dist/evergarden.jar (3.0.0-e2.198-pet-collection). Only the 57 species' blueprints,
their native display models, menu portraits and Bedrock entities change; the 37 original species and every
unrelated entry stay byte-identical, and the Bedrock pack gets a new content version so clients refetch it.
Nothing is published or deployed. Stages: assets (blueprints, native, portraits, bedrock), jar, promote.
"""
import argparse
from concurrent.futures import ProcessPoolExecutor, as_completed
import hashlib
import io
import json
import os
from pathlib import Path
import re
import runpy
import shutil
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'evergarden/tools'), str(ROOT / 'tools')]
from pet_expansion_catalog import ENTRIES, SPECIES  # noqa: E402

RES = ROOT / 'evergarden/src/main/resources'
DIST = ROOT / 'evergarden/dist'
OUT = ROOT / 'output/pet-atelier-release'
VERSION = '3.0.1-e2.199-pet-atelier'
BASELINE_VERSION = '3.0.0-e2.198-pet-collection'
DATE = (2026, 11, 8, 0, 0, 0)
STAGES = ('baby', 'medium', 'adult')


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def encode(value):
    return (json.dumps(value, ensure_ascii=False, separators=(',', ':')) + '\n').encode('utf8')


def entries(data):
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        return {n: archive.read(n) for n in archive.namelist() if not n.endswith('/')}


def write_zip(path, files, date=DATE):
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as target:
        for name, data in sorted(files.items()):
            target.writestr(zipfile.ZipInfo(name, date), data, compress_type=zipfile.ZIP_DEFLATED)


def ours(name):
    """Does a pack or archive entry belong to one of the 57 rebuilt species?"""
    return any(f'everpet_{s}_' in name or f'pet_{s}_' in name or f'/{s}_' in name.replace('pet_native/', '/')
               for s in SPECIES)


def baseline():
    before = OUT / 'before'
    before.mkdir(parents=True, exist_ok=True)
    jar = before / 'evergarden.jar'
    if not jar.exists():
        shutil.copy2(ROOT / 'dist/evergarden.jar', jar)
    with zipfile.ZipFile(jar) as archive:
        plugin = archive.read('plugin.yml').decode('utf8')
    assert f"version: '{BASELINE_VERSION}'" in plugin, 'Unexpected baseline jar'
    return jar


# ------------------------------------------------------------------------- assets

def blueprint(species):
    import build_pet_expansion_assets as bpa
    rows = bpa.blueprint(species)
    for row in rows:
        with open(RES / f"modelengine/pets/{row['id']}.bbmodel", encoding='utf8') as handle:
            row['texture_size'] = json.load(handle)['resolution']['width']
    return rows


def blueprints(jar, workers):
    import pet_model_engine as pme
    with zipfile.ZipFile(jar) as old:
        manifest = json.loads(old.read('modelengine/pets/manifest.json'))
        original = {n: old.read(n) for n in old.namelist() if n.startswith('modelengine/pets/') and n.endswith('.bbmodel')}
    new = {}
    with ProcessPoolExecutor(max_workers=workers) as pool:
        tasks = {pool.submit(blueprint, s): s for s in SPECIES}
        for task in as_completed(tasks):
            for row in task.result():
                new[row['id']] = row
            print('Sculpted', tasks[task], flush=True)
    assert len(new) == 171
    rows = [new.get(entry['id'], entry) for entry in manifest]
    assert [r['id'] for r in rows] == [e['id'] for e in manifest] and len(rows) == 282
    (pme.OUT / 'manifest.json').write_text(json.dumps(rows, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    for name, data in original.items():
        current = (RES / name).read_bytes()
        if ours(name):
            assert current != data, 'Atelier blueprint did not change: ' + name
        else:
            assert current == data, 'Original pet changed: ' + name
    cubes = [r['cubes'] for r in new.values()]
    (OUT / 'sculpt-report.json').write_bytes(encode(dict(species=57, variants=171, cubes=sum(cubes), min_cubes=min(cubes),
                                                         max_cubes=max(cubes), models=sorted(new.values(), key=lambda r: r['id']))))


def native(jar):
    runpy.run_path(str(ROOT / 'tools/export_native_pet_models.py'), run_name='__main__')
    with zipfile.ZipFile(jar) as old:
        old_models = json.loads(old.read('native-pets.json'))['models']
        old_zip = entries(old.read('native-pets.zip'))
    new_models = json.loads((RES / 'native-pets.json').read_bytes())['models']
    new_zip = entries((RES / 'native-pets.zip').read_bytes())
    assert set(new_models) == set(old_models) and len(new_models) == 282
    for key, value in old_models.items():
        if not any(key.startswith(s + '_') for s in SPECIES):
            assert new_models[key] == value, 'Original native model changed: ' + key
    for name, data in old_zip.items():
        if not ours(name):
            assert new_zip.get(name) == data, 'Original native asset changed: ' + name
    print('Native display models:', len(new_models), flush=True)


def portrait(species):
    import build_pet_expansion_assets as bpa
    return bpa.portrait(species)


def portraits(jar, workers):
    import build_pet_expansion_assets as bpa
    with zipfile.ZipFile(jar) as old:
        files = entries(old.read('pet-portraits.zip'))
    previous = dict(files)
    with ProcessPoolExecutor(max_workers=workers) as pool:
        tasks = {pool.submit(portrait, s): s for s in SPECIES}
        for task in as_completed(tasks):
            files.update(task.result())
            print('Portrayed', tasks[task], flush=True)
    assert set(files) == set(previous)
    assert all(files[n] == data for n, data in previous.items() if not ours(n))
    write_zip(RES / 'pet-portraits.zip', files, (1980, 1, 1, 0, 0, 0))
    bpa.gallery()


def bedrock(jar):
    import pet_bedrock_assets
    with zipfile.ZipFile(jar) as old:
        files = entries(old.read('resource-packs/evergarden-bedrock.mcpack'))
        mappings = old.read('resource-packs/geyser-mappings.json')
        hashes = json.loads(old.read('resource-packs/pack-hashes.json'))
    previous = dict(files)
    fresh = pet_bedrock_assets.assets()
    selected = {n: d for n, d in fresh.items() if any(f'everpet_{s}_' in n or n.startswith(f'textures/menu/pet_{s}_')
                                                      for s in SPECIES)}
    assert sum(n.startswith('entity/') for n in selected) == 171
    assert set(selected) <= set(files), 'Unexpected new Bedrock entries'
    files.update(selected)
    with zipfile.ZipFile(RES / 'pet-portraits.zip') as portraits_zip:
        for s in SPECIES:
            for stage in STAGES:
                for suffix in ('', '_dim'):
                    name = f'menu_pet_{s}_{stage}{suffix}'
                    target = 'textures/items/evergarden_menu/' + name + '.png'
                    assert target in files
                    files[target] = portraits_zip.read(f'assets/evergarden/textures/item/menu/{name[5:]}.png')
    manifest = json.loads(files['manifest.json'])
    version = list(manifest['header']['version'])
    version = [version[0], version[1] + 1, 0]
    manifest['header']['version'] = version
    for module in manifest['modules']:
        module['version'] = version
    files['manifest.json'] = encode(manifest)
    changed = {n for n in files if files[n] != previous.get(n)}
    assert all(ours(n) or n == 'manifest.json' for n in changed), sorted(n for n in changed if not ours(n))
    pack = DIST / 'evergarden-bedrock.mcpack'
    write_zip(pack, files)
    (DIST / 'geyser-mappings.json').write_bytes(mappings)
    hashes['evergarden-bedrock.mcpack'] = hashlib.sha1(pack.read_bytes()).hexdigest()
    (DIST / 'pack-hashes.json').write_bytes(encode(hashes))
    design = json.loads((RES / 'judge-design.json').read_bytes())
    design['bedrock-version'] = '.'.join(map(str, version))
    (RES / 'judge-design.json').write_bytes(encode(design))
    print(f'Bedrock: {len(changed)} entries refreshed, content {version}; {pack.stat().st_size:,} bytes', flush=True)


# ---------------------------------------------------------------------------- jar

def candidate(jar):
    values = {name: RES / name for name in ('native-pets.json', 'native-pets.zip', 'pet-portraits.zip', 'judge-design.json',
                                            'modelengine/pets/manifest.json')}
    for name in ('evergarden-bedrock.mcpack', 'geyser-mappings.json', 'pack-hashes.json'):
        values['resource-packs/' + name] = DIST / name
    for s in SPECIES:
        for stage in STAGES:
            name = f'modelengine/pets/everpet_{s}_{stage}.bbmodel'
            values[name] = RES / name
    hashes = {p.relative_to(ROOT).as_posix(): sha(p) for p in values.values()}
    target = OUT / 'evergarden.jar'
    with zipfile.ZipFile(jar) as old:
        plugin, count = re.subn(r"(?m)^version:[^\r\n]*", "version: '" + VERSION + "'", old.read('plugin.yml').decode('utf8'))
        assert count == 1
        assert all(n in old.namelist() for n in values), 'Every replaced entry must already exist'
        assert old.read('resource-packs/geyser-mappings.json') == values['resource-packs/geyser-mappings.json'].read_bytes()
        with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as new:
            for info in old.infolist():
                if info.filename not in values and info.filename != 'plugin.yml':
                    new.writestr(info, old.read(info.filename))
            replacements = {'plugin.yml': plugin.encode('utf8'), **{n: p.read_bytes() for n, p in values.items()}}
            for name, data in sorted(replacements.items()):
                new.writestr(zipfile.ZipInfo(name, DATE), data, compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(jar) as old, zipfile.ZipFile(target) as new:
        assert new.testzip() is None and len(new.namelist()) == len(set(new.namelist())) == len(old.namelist())
        preserved = [n for n in old.namelist() if n not in replacements]
        assert all(new.read(n) == old.read(n) for n in preserved)
        pets = [n for n in old.namelist() if n.startswith('modelengine/pets/') and n.endswith('.bbmodel') and not ours(n)]
        assert len(pets) == 111 and all(new.read(n) == old.read(n) for n in pets)
    assert all(sha(ROOT / p) == h for p, h in hashes.items()), 'Inputs changed during packaging'
    report = dict(version=VERSION, candidate_sha256=sha(target), baseline_sha256=sha(jar),
                  magic_sha256=sha(ROOT / 'dist/advance-magic.jar'), sources=hashes, preserved_entries=len(preserved),
                  promoted=False)
    (OUT / 'build-report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(f'Built {VERSION}; {len(preserved)} entries preserved byte for byte; {target.stat().st_size:,} bytes')


def promote():
    report = json.loads((OUT / 'build-report.json').read_text(encoding='utf8'))
    receipt = json.loads((OUT / 'paper/pet-grounding-validation.json').read_text(encoding='utf8'))
    assert receipt['report'].startswith('PASS ')
    # The asset gate (tests/model-engine/check_pet_models.py) only writes its PASS file on success.
    asset_gate = ROOT / 'output/pet-models-asset-checks.txt'
    assert asset_gate.exists() and asset_gate.read_text(encoding='utf8').startswith('PASS'), 'Run check_pet_models first'
    assert asset_gate.stat().st_mtime > (OUT / 'evergarden.jar').stat().st_mtime, 'check_pet_models predates this candidate'
    assert sha(OUT / 'evergarden.jar') == report['candidate_sha256'] == receipt['garden_sha256']
    # dist holds the baseline, or an earlier promotion of this same unpublished version being replaced.
    earlier = ROOT / 'dist' / f'evergarden-{VERSION}.jar'
    assert sha(ROOT / 'dist/evergarden.jar') in {report['baseline_sha256'], sha(earlier) if earlier.exists() else None}
    assert sha(ROOT / 'dist/advance-magic.jar') == report['magic_sha256'] == receipt['magic_sha256']
    assert all(sha(ROOT / p) == h for p, h in report['sources'].items()), 'Inputs changed after validation'
    for path in (ROOT / 'dist/evergarden.jar', ROOT / 'dist' / f'evergarden-{VERSION}.jar'):
        staged = path.with_suffix('.jar.staged')
        shutil.copy2(OUT / 'evergarden.jar', staged)
        os.replace(staged, path)
    plugin = RES / 'plugin.yml'
    plugin.write_text(re.sub(r"(?m)^version:[^\r\n]*", "version: '" + VERSION + "'", plugin.read_text(encoding='utf8')),
                      encoding='utf8')
    report.update(promoted=True, validation=receipt)
    (OUT / 'build-report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print('Promoted ' + VERSION)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--stage', choices=('all', 'blueprints', 'native', 'portraits', 'bedrock', 'jar', 'promote'),
                        default='all')
    parser.add_argument('--workers', type=int, default=4)
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    if args.stage == 'promote':
        promote()
        return
    jar = baseline()
    if args.stage in ('all', 'blueprints'):
        blueprints(jar, args.workers)
    if args.stage in ('all', 'native'):
        native(jar)
    if args.stage in ('all', 'portraits'):
        portraits(jar, args.workers)
    if args.stage in ('all', 'bedrock'):
        bedrock(jar)
    if args.stage in ('all', 'jar'):
        candidate(jar)


if __name__ == '__main__':
    main()
