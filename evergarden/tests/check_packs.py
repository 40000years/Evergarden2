"""Check model routing, Geyser coexistence, six mask models and release integrity."""
import hashlib
import json
import zipfile
import struct
import re
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(root.parent / 'tools'))
from release_jar_checks import check_jar
from bedrock_particle_checks import check_direction_modes
from check_retired_core_cleanup import assert_no_retired, assert_mapping_cleanup
dist = root / 'dist'
check_direction_modes(dist / 'evergarden-bedrock.mcpack')
mapping = json.loads((dist / 'geyser-mappings.json').read_text())
other = json.loads((root.parent / 'advance-magic/dist/geyser-mappings.json').read_text())
assert_mapping_cleanup(mapping)
assert_mapping_cleanup(other)
for pack_name in ('evergarden-java.zip', 'evergarden-bedrock.mcpack'):
    with zipfile.ZipFile(dist / pack_name) as retired_check:
        assert_no_retired(retired_check)

def check_bedrock_references(pack_paths):
    """Resolve attachable references across the packs served together, including vanilla exceptions."""
    documents = []
    files = set()
    for path in pack_paths:
        with zipfile.ZipFile(path) as pack:
            files.update(pack.namelist())
            documents.extend((name, json.loads(pack.read(name))) for name in pack.namelist() if name.endswith('.json'))
    documents = [(name, data) for name, data in documents if isinstance(data, dict)]
    geometry_ids = {geo['description']['identifier'] for _, data in documents for geo in data.get('minecraft:geometry', [])}
    controllers = {key for _, data in documents for key in data.get('render_controllers', {})}
    animations = {key for _, data in documents for key in data.get('animations', {})}
    for name, data in documents:
        atlas = data.get('texture_data', {})
        for key, entry in atlas.items():
            references = entry['textures']
            for ref in references if isinstance(references, list) else [references]:
                assert ref + '.png' in files, ('Missing atlas texture', name, key, ref)
        description = data.get('minecraft:attachable', {}).get('description')
        if description is None:
            continue
        for ref in description.get('geometry', {}).values():
            assert ref in geometry_ids, ('Missing attachable geometry', name, ref)
        for ref in description.get('render_controllers', []):
            if isinstance(ref, dict):
                ref = next(iter(ref))
            assert ref in controllers, ('Missing render controller', name, ref)
        for ref in description.get('textures', {}).values():
            assert ref + '.png' in files or ref == 'textures/misc/enchanted_item_glint', ('Missing attachable texture', name, ref)
        for ref in description.get('animations', {}).values():
            assert ref in animations or ref in ('animation.bow.wield', 'animation.bow.wield_first_person_pull'), ('Missing animation', name, ref)

check_bedrock_references([dist / 'evergarden-bedrock.mcpack', root.parent / 'advance-magic/dist/advance-magic-bedrock.mcpack'])
definitions = [d for group in mapping['items'].values() for d in group]
identifiers = {d['bedrock_identifier'] for d in definitions}
other_ids = {d['bedrock_identifier'] for group in other['items'].values() for d in group}
relic_expected = {"voidscape:" + name for name in (
    "void_key", "rift_pickaxe", "smelter_pickaxe",
    "scroll_eternity", "scroll_limit_break", "scroll_unique",
    "thorn_mask", "thorn_crown", "astral_mask", "astral_crown", "chrono_mask", "chrono_crown"
)}
assert len(definitions) == len(identifiers)
assert relic_expected.issubset(identifiers), f"Missing relics: {relic_expected - identifiers}"
retired = {"voidscape:" + name for name in ("rift_blade", "storm_bow", "nova_bow", "eternal_aegis")}
assert not identifiers & retired, f"Retired relic mappings remain: {identifiers & retired}"
# Arkhaia and Seraphon are ModelEngine / native Geyser entities; no static boss item models remain.
assert not [i for i in identifiers if i.startswith(('voidscape:boss_arkhaia_', 'voidscape:boss_seraphon_'))], 'Retired static boss models remain'
with zipfile.ZipFile(dist / 'evergarden-bedrock.mcpack') as pack:
    for boss in ('arkhaia', 'seraphon'):
        entity = json.loads(pack.read(f'entity/{boss}_native.entity.json'))['minecraft:client_entity']['description']
        assert entity['identifier'] == 'voidscape:' + boss and 'aura' in entity['scripts']['animate'], boss
    assert not [n for n in pack.namelist() if 'boss_arkhaia' in n or 'boss_seraphon' in n]
assert len(identifiers) == 41, f"Expected 41 active identifiers, got {len(identifiers)}"
assert not identifiers & {'voidscape:'+name for name in ('key_shard','repair_stone','void_elixir')}
assert not identifiers & other_ids, 'Duplicate Geyser custom item IDs across plugins'
hashes = json.loads((dist / 'pack-hashes.json').read_text())
service_source = (root / 'src/main/java/com/example/voidscape/pack/ResourcePackService.java').read_text(encoding='utf8')
pack_config = (root / 'src/main/resources/config.yml').read_text(encoding='utf8')
assert "url: ''" in pack_config and "sha1: ''" in pack_config
assert 'host:\n    enabled: true' in pack_config and 'port: 8188' in pack_config
assert 'migratePackConfig(plugin.getConfig(),sha1)' in service_source
assert 'packDigest(plugin.getConfig(),sha1)' in service_source
assert 'return DEFAULT_CDN_URL' not in service_source, 'An unavailable local bundle must not fall back to stale art'
for filename, digest in hashes.items():
    assert hashlib.sha1((dist / filename).read_bytes()).hexdigest() == digest
with zipfile.ZipFile(dist / 'evergarden-java.zip') as java, zipfile.ZipFile(dist / 'evergarden-bedrock.mcpack') as bedrock:
    for archive in (java, bedrock):
        assert archive.testzip() is None
        for name in archive.namelist():
            if name.endswith(('.json', '.mcmeta')):
                json.loads(archive.read(name))
    atlas = json.loads(bedrock.read('textures/item_texture.json'))['texture_data']
    manifest = json.loads(bedrock.read('manifest.json'))
    expected_version = [3, 45, 0]
    assert manifest['header']['version'] == expected_version
    assert manifest['modules'][0]['version'] == expected_version
    assert all(not key.startswith('advance_magic.') for key in atlas), 'Advance Magic icons belong to their own pack; retired crafting core fallback icons must be absent'
    assert not any('nether_portal' in name or name.endswith('/portal.png') for name in java.namelist())
    assert 'textures/blocks/portal.png' not in bedrock.namelist()
    for archive in (java, bedrock):
        assert not any('azure_portal' in name or 'evergarden_portal' in name for name in archive.namelist())
    assert not any(entry.get('bedrock_identifier') == 'voidscape:azure_portal'
                   for entries in mapping['items'].values() for entry in entries)
    assert 'assets/minecraft/items/carved_pumpkin.json' not in java.namelist()
    for theme in ('whale','garden','observatory'):
        name=f'restoration_altar_{theme}'
        assert f'assets/voidscape/items/{name}.json' in java.namelist()
        assert f'attachables/{name}.json' in bedrock.namelist()
        assert f'models/entity/{name}.geo.json' in bedrock.namelist()
    for base, entries in mapping['items'].items():
        selector_path = 'assets/minecraft/items/' + base.split(':')[1] + '.json'
        cases = set()
        if selector_path in java.namelist():
            selector = json.loads(java.read(selector_path))['model']
            assert selector['property'] == 'minecraft:custom_model_data' and 'fallback' in selector
            cases = {c['when'] for c in selector['cases']}
        for entry in entries:
            name = entry['bedrock_identifier'].split(':')[1]
            if name.startswith('judge_'):
                assert entry['model'] == entry['bedrock_identifier'] and 'predicate' not in entry
                continue  # Dedicated addon; meshes and atlas are checked by check_judge_assets.py.
            # Head models use direct item_model components; relics
            # additionally support the legacy custom-model-data selector.
            direct = entry['model'] == entry['bedrock_identifier']  # item_model component routes (masks, orbs, eggs, feathers)
            if name.endswith(('_mask', '_crown')) or direct:
                assert entry['model'] == entry['bedrock_identifier']
                assert 'predicate' not in entry
            else:
                assert entry['model'] == base
                assert entry['predicate']['value'] in cases
            assert json.loads(java.read(f'assets/voidscape/items/{name}.json'))['model']
            java_texture = java.read(f'assets/voidscape/textures/item/{name}.png')
            bedrock_texture = bedrock.read(atlas[entry['bedrock_options']['icon']]['textures'] + '.png')
            assert java_texture == bedrock_texture
            if name.endswith(('_mask', '_crown')):
                model = json.loads(java.read(f'assets/voidscape/models/item/{name}.json'))
                assert 'head' in model['display']
                if name.endswith(('_mask', '_crown')):
                    assert len(model['elements']) >= 4
                attachment = json.loads(bedrock.read(f'attachables/{name}.json'))['minecraft:attachable']['description']
                assert attachment['identifier'] == entry['bedrock_identifier']
                geometry = json.loads(bedrock.read(f'models/entity/{name}.geo.json'))['minecraft:geometry'][0]
                assert geometry['description']['identifier'] == attachment['geometry']['default']
                assert 'item_slot_to_bone_name' in geometry['bones'][0]['binding']
    assert 'assets/minecraft/items/bow.json' not in java.namelist()
    assert 'assets/minecraft/items/shield.json' not in java.namelist()
check_jar(root.parent / 'dist/evergarden.jar')
with zipfile.ZipFile(root.parent / 'dist/evergarden.jar') as jar:
    for filename in (*hashes, 'geyser-mappings.json', 'pack-hashes.json'):
        assert jar.read('resource-packs/' + filename) == (dist / filename).read_bytes()
print('PASS: all Bedrock references, 16 active relic/mask routes, six mask models, retired items absent, vanilla fallbacks, no cross-plugin IDs, hashes and embedded assets')
