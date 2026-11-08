"""Verify Geyser can resolve every Java pet-menu portrait to the same PNG.

Usage: python tests/resource-packs/test_pet_menu_bedrock.py [release.jar]
Without an argument checks the staged asset files against the current dist JAR.
"""
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
jar_path = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'dist/evergarden.jar'
staging = ROOT / 'output/pets-bedrock-menu-assets'
with zipfile.ZipFile(jar_path) as jar:
    portraits_data = jar.read('pet-portraits.zip')
    expected_portraits = 2 * len(json.loads(jar.read('native-pets.json'))['models'])
    if len(sys.argv) > 1:
        pack_data = jar.read('resource-packs/evergarden-bedrock.mcpack')
        mapping_data = jar.read('resource-packs/geyser-mappings.json')
        hashes_data = jar.read('resource-packs/pack-hashes.json')
        design_data = jar.read('judge-design.json')
    else:
        pack_data = (staging / 'evergarden-bedrock.mcpack').read_bytes()
        mapping_data = (staging / 'geyser-mappings.json').read_bytes()
        hashes_data = (staging / 'pack-hashes.json').read_bytes()
        design_data = (staging / 'judge-design.json').read_bytes()

mappings = json.loads(mapping_data)['items']['minecraft:paper']
pet_items = [item for item in mappings if item.get('model', '').startswith('evergarden:menu_pet_')]
by_model = {item['model']: item for item in pet_items}
assert len(by_model) == len(pet_items) == expected_portraits, 'Missing or duplicate model mappings'
assert hashlib.sha1(pack_data).hexdigest() == json.loads(hashes_data)['evergarden-bedrock.mcpack']
with zipfile.ZipFile(io.BytesIO(portraits_data)) as portraits, zipfile.ZipFile(io.BytesIO(pack_data)) as pack:
    atlas = json.loads(pack.read('textures/item_texture.json'))['texture_data']
    manifest = json.loads(pack.read('manifest.json'))
    version = manifest['header']['version']
    assert all(module['version'] == version for module in manifest['modules'])
    assert '.'.join(map(str, version)) == json.loads(design_data)['bedrock-version']
    names = [n for n in portraits.namelist() if n.startswith('assets/evergarden/items/menu_pet_') and n.endswith('.json')]
    assert len(names) == len(by_model)
    for path in names:
        name = path.rsplit('/', 1)[1][:-5]
        item = by_model['evergarden:' + name]
        assert item['bedrock_identifier'] == 'evergarden:' + name
        png = atlas[item['bedrock_options']['icon']]['textures'] + '.png'
        java_png = 'assets/evergarden/textures/item/menu/' + name.removeprefix('menu_') + '.png'
        assert pack.read(png) == portraits.read(java_png), name
print(f'PASS: {len(names)} pet menu items have unique mappings and identical Java/Bedrock portraits; pack {version}')
