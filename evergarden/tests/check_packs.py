"""Check model routing, Geyser coexistence, six wearable models and release integrity."""
import hashlib
import json
import zipfile
import struct
from pathlib import Path

root = Path(__file__).resolve().parents[1]
dist = root / 'dist'
mapping = json.loads((dist / 'geyser-mappings.json').read_text())
other = json.loads((root.parent / 'advance-magic/dist/geyser-mappings.json').read_text())
definitions = [d for group in mapping['items'].values() for d in group]
identifiers = {d['bedrock_identifier'] for d in definitions}
other_ids = {d['bedrock_identifier'] for group in other['items'].values() for d in group}
relic_expected = {"voidscape:" + name for name in (
    "void_key", "rift_pickaxe", "smelter_pickaxe", "storm_bow", "nova_bow", "rift_blade", "eternal_aegis",
    "scroll_eternity", "scroll_limit_break", "scroll_unique", "astral_dust", "key_shard", "repair_stone", "void_elixir",
    "thorn_mask", "thorn_crown", "astral_mask", "astral_crown", "chrono_mask", "chrono_crown"
)}
assert len(definitions) == len(identifiers)
assert relic_expected.issubset(identifiers), f"Missing relics: {relic_expected - identifiers}"
assert len(identifiers) == 171, f"Expected 171 identifiers including the azure portal, got {len(identifiers)}"
assert not identifiers & other_ids, 'Duplicate Geyser custom item IDs across plugins'
hashes = json.loads((dist / 'pack-hashes.json').read_text())
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
    content_hash = hashlib.sha256()
    for name in sorted(n for n in bedrock.namelist() if not n.endswith('/') and n != 'manifest.json'):
        content_hash.update(name.encode('utf8') + b'\0' + bedrock.read(name))
    expected_version = [3, 7, int(content_hash.hexdigest()[:7], 16) % 60000 + 1]
    assert manifest['header']['version'] == expected_version
    assert manifest['modules'][0]['version'] == expected_version
    with zipfile.ZipFile(root.parent / 'advance-magic/dist/advance-magic-bedrock.mcpack') as magic:
        magic_atlas = json.loads(magic.read('textures/item_texture.json'))['texture_data']
        assert magic_atlas.keys() <= atlas.keys(), 'Evergarden must also expose the Advance Magic icons'
        for key, entry in magic_atlas.items():
            if key.startswith('advance_magic.core_'):
                continue
            assert bedrock.read(entry['textures'] + '.png') == magic.read(entry['textures'] + '.png')
    assert not any('nether_portal' in name or name.endswith('/portal.png') for name in java.namelist())
    assert 'textures/blocks/portal.png' not in bedrock.namelist()
    assert json.loads(java.read('assets/voidscape/textures/item/azure_portal.png.mcmeta'))['animation']['frametime'] == 2
    assert 'USE_UV_ANIM' in json.loads(bedrock.read('materials/evergarden_portal.material'))['materials']['evergarden_portal:entity_alphablend']['+defines']
    assert 'assets/minecraft/items/carved_pumpkin.json' not in java.namelist()
    for base, entries in mapping['items'].items():
        selector_path = 'assets/minecraft/items/' + base.split(':')[1] + '.json'
        cases = set()
        if selector_path in java.namelist():
            selector = json.loads(java.read(selector_path))['model']
            assert selector['property'] == 'minecraft:custom_model_data' and 'fallback' in selector
            cases = {c['when'] for c in selector['cases']}
        for entry in entries:
            name = entry['bedrock_identifier'].split(':')[1]
            # Crops and head models use direct item_model components; relics
            # additionally support the legacy custom-model-data selector.
            if name.startswith(('seed_', 'crop_')):
                assert entry['model'] == entry['bedrock_identifier']
                assert 'predicate' not in entry
            else:
                assert entry['model'] == base
            if not name.startswith(('seed_', 'crop_')) and not name.endswith(('_mask', '_crown')):
                assert entry['predicate']['value'] in cases
            assert json.loads(java.read(f'assets/voidscape/items/{name}.json'))['model']
            java_texture = java.read(f'assets/voidscape/textures/item/{name}.png')
            bedrock_texture = bedrock.read(atlas[entry['bedrock_options']['icon']]['textures'] + '.png')
            if name in ('storm_bow', 'nova_bow'):
                assert struct.unpack('>II', java_texture[16:24]) == (32, 32)
                assert struct.unpack('>II', bedrock_texture[16:24]) == (16, 16)
                attachment = json.loads(bedrock.read(f'attachables/{name}.json'))['minecraft:attachable']['description']
                assert 'wield_first_person_pull' in attachment['animations']
                for stage in range(3):
                    frame = bedrock.read(f'textures/items/{name}_pulling_{stage}.png')
                    assert struct.unpack('>II', frame[16:24]) == (16, 16)
            else:
                assert java_texture == bedrock_texture
            if name.startswith(('seed_', 'crop_')):
                assert struct.unpack('>II', java.read(f'assets/voidscape/textures/item/{name}.png')[16:24]) == (64, 64), name
            if name.endswith(('_mask', '_crown')) or '_stage_' in name:
                model = json.loads(java.read(f'assets/voidscape/models/item/{name}.json'))
                assert 'head' in model['display']
                if name.endswith(('_mask', '_crown')):
                    assert len(model['elements']) >= 4
                attachment = json.loads(bedrock.read(f'attachables/{name}.json'))['minecraft:attachable']['description']
                assert attachment['identifier'] == entry['bedrock_identifier']
                geometry = json.loads(bedrock.read(f'models/entity/{name}.geo.json'))['minecraft:geometry'][0]
                assert geometry['description']['identifier'] == attachment['geometry']['default']
                if '_stage_' not in name:
                    assert 'item_slot_to_bone_name' in geometry['bones'][0]['binding']
                if '_stage_' in name:
                    assert geometry['bones'][0]['name'] == 'head'
                    assert geometry['bones'][0]['pivot'] == [0, 24, 0]
                    assert base in ('minecraft:carved_pumpkin', 'minecraft:iron_helmet'), 'Plant attachables require a head-equippable base'
                    # Match vanilla crop.json density: two X planes plus two Z planes.
                    assert len(geometry['bones'][0]['cubes']) == 4
                    assert geometry['description']['texture_width'] == geometry['description']['texture_height'] == 64
                    # Box UV previously sampled only the top of the sprite,
                    # clipping the sprout drawn in its lower half.
                    plane_faces = [('north', 'south'), ('north', 'south'), ('west', 'east'), ('west', 'east')]
                    for cube, faces in zip(geometry['bones'][0]['cubes'], plane_faces):
                        # Small stand at -0.65, geometry in pixels /16, scale .5.
                        # The base must be above the farmland surface (-1/16).
                        assert abs(-0.8125 + cube['origin'][1] / 16 * 0.5 + 1 / 16) < 1e-6
                        assert cube['uv'][faces[0]] == {'uv': [0, 0], 'uv_size': [64, 64]}, name
                        assert cube['uv'][faces[1]] == {'uv': [64, 0], 'uv_size': [-64, 64]}, name
                        assert cube['size'][1] == 20, name
                        assert model['display']['head']['scale'] == [3.2, 3.2, 3.2], name
                        assert model['display']['head']['translation'] == [0, 9.6, 0], name
    assert json.loads(java.read('assets/minecraft/items/bow.json'))['model']['fallback']['type'] == 'minecraft:condition'
    assert json.loads(java.read('assets/minecraft/items/shield.json'))['model']['fallback']['on_false']['model']['type'] == 'minecraft:shield'
    assert json.loads(java.read('assets/minecraft/items/shield.json'))['model']['fallback']['on_true']['model']['type'] == 'minecraft:shield'
    assert json.loads(java.read('assets/minecraft/items/shield.json'))['model']['fallback']['transformation']['scale'] == [1.0, -1.0, -1.0]
with zipfile.ZipFile(dist / 'evergarden-3.0.0.jar') as jar:
    for filename in (*hashes, 'geyser-mappings.json', 'pack-hashes.json'):
        assert jar.read('resource-packs/' + filename) == (dist / filename).read_bytes()
print('PASS: 20 model selectors, six Java/Bedrock wearable models, vanilla fallbacks, no cross-plugin Geyser ID collisions, hashes and embedded assets')
