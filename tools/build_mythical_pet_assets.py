"""Ship the mythical five's models in every Evergarden pack (legendary tier, e2.177 assets).

* writes the 15 mythical blueprints (pet_mythical_sculpt v3) beside the 96 shipped ones and lists
  them in modelengine/pets/manifest.json; the 96 existing blueprints are not touched;
* re-exports native-pets.json/.zip with all 111 variants (export_native_pet_models);
* adds the mythical menu portraits to pet-portraits.zip (pet_portraits keeps existing ones);
* adds the mythical Geyser entities and portrait items to evergarden-bedrock.mcpack and
  geyser-mappings.json, bumps the Bedrock content version and judge-design.json to match.

Every pre-existing entry of every archive and manifest is verified byte-identical afterwards.
Back-ups of the inputs are in output/mythical-pets-release/before.

    python tools/build_mythical_pet_assets.py
"""
import hashlib
import io
import json
import runpy
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'evergarden/tools'))
sys.path.insert(0, str(ROOT / 'tools'))
import pet_model_engine as pme  # noqa: E402
import pet_bedrock_assets  # noqa: E402
import pet_portraits  # noqa: E402

RESOURCES = ROOT / 'evergarden/src/main/resources'
DIST = ROOT / 'evergarden/dist'
BEFORE = ROOT / 'output/mythical-pets-release/before'
MYTHICAL = [s for s in pme.SPECIES if s in pme.MYTHICAL]
BEDROCK_VERSION = [3, 67, 0]
DATE = (2026, 11, 1, 0, 0, 0)


def entries(path):
    with zipfile.ZipFile(path) as archive:
        return {name: archive.read(name) for name in archive.namelist() if not name.endswith('/')}


def encode(value):
    return (json.dumps(value, ensure_ascii=False, separators=(',', ':')) + '\n').encode('utf8')


def kept(old, new, label):
    changed = [name for name, data in old.items() if new.get(name) != data]
    if changed:
        raise RuntimeError(f'{label}: {len(changed)} existing entries changed, e.g. {changed[:3]}')
    return len(set(new) - set(old))


def blueprints():
    manifest_path = pme.OUT / 'manifest.json'
    raw = manifest_path.read_bytes()
    existing = json.loads(raw)
    # pet_model_engine writes with write_text, which gives CRLF on Windows; so does this build.
    if (json.dumps(existing, ensure_ascii=False, indent=2) + '\n').replace('\n', '\r\n').encode('utf8') != raw:
        raise RuntimeError('manifest.json is not in pet_model_engine format')
    by_id = {entry['id']: entry for entry in existing}
    pme.ART.mkdir(parents=True, exist_ok=True)
    for species in MYTHICAL:
        for stage in pme.STAGES:
            data, texture = pme.build_model(species, stage)
            target = pme.OUT / (data['name'] + '.bbmodel')
            target.write_text(json.dumps(data, separators=(',', ':'), ensure_ascii=False) + '\n', encoding='utf8')
            texture.save(pme.ART / (data['name'] + '.png'))
            by_id[data['name']] = dict(id=data['name'], species=species, stage=stage, title=pme.TITLES[pme.SPECIES.index(species)],
                                       rarity='mythical', cubes=len(data['elements']) - 1,
                                       bones=len(data['animations'][0]['animators']), animations=[a['name'] for a in data['animations']],
                                       sha256=hashlib.sha256(target.read_bytes()).hexdigest())
    ordered = [by_id[f'everpet_{s}_{stage}'] for s in pme.SPECIES for stage in pme.STAGES]
    if [e for e in ordered if e['species'] not in pme.MYTHICAL] != existing:
        raise RuntimeError('Existing manifest entries would change')
    manifest_path.write_text(json.dumps(ordered, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    print(f'blueprints: {len(ordered)} listed ({len(ordered) - len(existing)} mythical added)')


def bedrock():
    pack_path, mappings_path = DIST / 'evergarden-bedrock.mcpack', DIST / 'geyser-mappings.json'
    files = entries(pack_path)
    old = dict(files)
    manifest = json.loads(files['manifest.json'])
    if manifest['header']['version'] >= BEDROCK_VERSION:
        raise RuntimeError(f"Bedrock pack is already {manifest['header']['version']}; pick a newer version")
    # Geyser entities: geometry, animations, client entity and texture of each mythical form.
    mythical = {name: data for name, data in pet_bedrock_assets.assets().items()
                if any(f'everpet_{s}_' in name for s in MYTHICAL)}
    if len([n for n in mythical if n.startswith('entity/')]) != 15:
        raise RuntimeError('Expected 15 mythical Bedrock entities')
    if any(name in files for name in mythical):
        raise RuntimeError('Mythical Bedrock entries already present')
    files.update(mythical)
    # Chest-menu portraits for Bedrock: the same item mapping the other pets use, appended.
    atlas = json.loads(files['textures/item_texture.json'])
    mappings = json.loads(mappings_path.read_bytes())
    paper = mappings['items'].setdefault('minecraft:paper', [])
    with zipfile.ZipFile(RESOURCES / 'pet-portraits.zip') as portraits:
        for species in MYTHICAL:
            for stage in pme.STAGES:
                for suffix in ('', '_dim'):
                    name = f'menu_pet_{species}_{stage}{suffix}'
                    icon, texture = 'evergarden.' + name, 'textures/items/evergarden_menu/' + name
                    files[texture + '.png'] = portraits.read(f'assets/evergarden/textures/item/menu/{name[5:]}.png')
                    atlas['texture_data'][icon] = {'textures': texture}
                    paper.append({'type': 'definition', 'model': 'evergarden:' + name, 'bedrock_identifier': 'evergarden:' + name,
                                  'display_name': 'Pet', 'bedrock_options': {'icon': icon, 'allow_offhand': False, 'display_handheld': False}})
    files['textures/item_texture.json'] = encode(atlas)
    manifest['header']['version'] = BEDROCK_VERSION
    for module in manifest['modules']:
        module['version'] = BEDROCK_VERSION
    files['manifest.json'] = encode(manifest)
    added = kept({k: v for k, v in old.items() if k not in ('manifest.json', 'textures/item_texture.json')}, files, 'Bedrock pack')
    with zipfile.ZipFile(pack_path, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as out:
        for name, data in sorted(files.items()):
            info = zipfile.ZipInfo(name, DATE)
            info.compress_type = zipfile.ZIP_DEFLATED
            out.writestr(info, data)
    mappings_path.write_bytes(encode(mappings))
    hashes_path = DIST / 'pack-hashes.json'
    hashes = json.loads(hashes_path.read_text(encoding='utf8'))
    hashes['evergarden-bedrock.mcpack'] = hashlib.sha1(pack_path.read_bytes()).hexdigest()
    hashes_path.write_bytes((json.dumps(hashes, separators=(',', ':')) + '\n').encode())
    design_path = RESOURCES / 'judge-design.json'
    design = json.loads(design_path.read_text(encoding='utf8'))
    design['bedrock-version'] = '.'.join(map(str, BEDROCK_VERSION))
    design_path.write_bytes(encode(design))
    print(f'bedrock: {added} entries added, version {design["bedrock-version"]}, {pack_path.stat().st_size} bytes')


def main():
    old_native = entries(RESOURCES / 'native-pets.zip')
    old_native_models = json.loads((RESOURCES / 'native-pets.json').read_text(encoding='utf8'))['models']
    old_portraits = entries(RESOURCES / 'pet-portraits.zip')
    blueprints()
    runpy.run_path(str(ROOT / 'tools/export_native_pet_models.py'), run_name='__main__')
    models = json.loads((RESOURCES / 'native-pets.json').read_text(encoding='utf8'))['models']
    if any(models.get(key) != value for key, value in old_native_models.items()) or len(models) != 111:
        raise RuntimeError('native-pets.json: existing models changed or count is not 111')
    print('native pets: +', kept(old_native, entries(RESOURCES / 'native-pets.zip'), 'native-pets.zip'), 'files')
    pet_portraits.build()
    print('portraits: +', kept(old_portraits, entries(RESOURCES / 'pet-portraits.zip'), 'pet-portraits.zip'), 'files')
    bedrock()
    # The design manifest was rewritten compactly; keep every other key as it was.
    before = json.loads((BEFORE / 'resources/judge-design.json').read_text(encoding='utf8'))
    after = json.loads((RESOURCES / 'judge-design.json').read_text(encoding='utf8'))
    if {k: v for k, v in after.items() if k != 'bedrock-version'} != {k: v for k, v in before.items() if k != 'bedrock-version'}:
        raise RuntimeError('judge-design.json changed beyond its Bedrock version')


if __name__ == '__main__':
    main()
