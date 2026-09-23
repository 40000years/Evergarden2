"""Validate release assets, model routing, fallback and embedded binary integrity."""
import hashlib
import json
import runpy
import struct
import sys
import zipfile
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
catalog = runpy.run_path(str(ROOT / 'tools/build_packs.py'))['spells']()
dist = ROOT / 'dist'
hashes = json.loads((dist / 'pack-hashes.json').read_text())


def check_png(data):
    assert data[:8] == b'\x89PNG\r\n\x1a\n'
    w, h, depth, color, _, _, interlace = struct.unpack('>IIBBBBB', data[16:29])
    assert w == h == 128 and depth == 8 and color == 6 and interlace == 0, 'Use 128px RGBA game textures'
    offset, compressed = 8, bytearray()
    while offset < len(data):
        size = struct.unpack('>I', data[offset:offset+4])[0]
        kind = data[offset+4:offset+8]
        content = data[offset+8:offset+8+size]
        crc = struct.unpack('>I', data[offset+8+size:offset+12+size])[0]
        assert zlib.crc32(kind + content) & 0xffffffff == crc
        if kind == b'IDAT': compressed.extend(content)
        offset += size + 12
    raw = zlib.decompress(compressed)
    stride, previous, alpha = w * 4, bytearray(w * 4), []
    for y in range(h):
        start = y * (stride + 1)
        filtering, row = raw[start], bytearray(raw[start+1:start+1+stride])
        for x in range(stride):
            a, b, c = row[x-4] if x >= 4 else 0, previous[x], previous[x-4] if x >= 4 else 0
            if filtering == 1: predictor = a
            elif filtering == 2: predictor = b
            elif filtering == 3: predictor = (a+b)//2
            elif filtering == 4:
                p = a+b-c
                predictor = min(((abs(p-a), 0, a), (abs(p-b), 1, b), (abs(p-c), 2, c)))[2]
            else:
                assert filtering == 0
                predictor = 0
            row[x] = (row[x] + predictor) & 255
        alpha.extend(row[3::4]); previous = row
    assert sum(a == 0 for a in alpha) > w*h*0.55, 'Background must really be transparent'
    assert sum(a == 255 for a in alpha) > w*h*0.025, 'Wand must have visible solid pixels'
    assert set(alpha) == {0, 255}, 'Crisp cutout without a translucent checkerboard'
    assert all(alpha[y*w+x] == 0 for y in range(h) for x in range(w) if x < 3 or x >= w-3 or y < 3 or y >= h-3), 'Safe transparent border'


for name, digest in hashes.items():
    assert hashlib.sha1((dist / name).read_bytes()).hexdigest() == digest
    with zipfile.ZipFile(dist / name) as z:
        assert z.testzip() is None
        for file in z.namelist():
            if file.endswith(('.json', '.mcmeta')): json.loads(z.read(file))
            if file.endswith('.png'): check_png(z.read(file))

with zipfile.ZipFile(dist / 'advance-magic-java.zip') as z:
    pack = json.loads(z.read('pack.mcmeta'))['pack']
    assert pack['min_format'] == [75, 0] and pack['max_format'] == [88, 0]
    assert 'assets/minecraft/items/carrot_on_a_stick.json' not in z.namelist()
    for name, _, _ in catalog:
        item = json.loads(z.read(f'assets/advance_magic/items/{name}.json'))
        assert item['model']['model'] == f'advance_magic:item/{name}'
        model = json.loads(z.read(f'assets/advance_magic/models/item/{name}.json'))
        assert model['parent'] == 'minecraft:item/handheld'
        assert model['textures']['layer0'] == f'advance_magic:item/{name}'
        assert z.read(f'assets/advance_magic/textures/item/{name}.png') == (ROOT / f'art/wands/{name}.png').read_bytes()
    for name in ('wand_repair', 'wand_damage', 'wand_cooldown'):
        item = json.loads(z.read(f'assets/advance_magic/items/{name}.json'))
        assert item['model']['model'] == f'advance_magic:item/{name}'
        assert z.read(f'assets/advance_magic/textures/item/{name}.png') == (ROOT / f'art/upgrades/{name}.png').read_bytes()

mapping = json.loads((dist / 'geyser-mappings.json').read_text())
assert mapping['format_version'] == 2
definitions = mapping['items']['minecraft:carrot_on_a_stick']
assert len(definitions) == len(catalog) == 15
assert len({row['bedrock_identifier'] for row in definitions}) == 15
with zipfile.ZipFile(dist / 'advance-magic-bedrock.mcpack') as z:
    manifest = json.loads(z.read('manifest.json'))
    assert manifest['header']['version'] == [1, 2, 0]
    assert manifest['modules'][0]['version'] == [1, 2, 0]
    assert tuple(manifest['header']['version']) > (1, 1, 40161), 'v2 Bedrock pack must supersede the Afterdeath release'
    atlas = json.loads(z.read('textures/item_texture.json'))['texture_data']
    for definition, (name, _, _) in zip(definitions, catalog):
        assert definition['model'] == 'minecraft:carrot_on_a_stick'
        assert definition['predicate'] == {'type': 'match', 'property': 'custom_model_data', 'index': 0, 'value': f'advance_magic:{name}'}
        assert definition['bedrock_options']['creative_category'] == 'equipment'
        assert z.read(atlas[definition['bedrock_options']['icon']]['textures'] + '.png') == (ROOT / f'art/wands/{name}.png').read_bytes()
    for name, material in (('wand_repair', 'prismarine_shard'), ('wand_damage', 'blaze_powder'), ('wand_cooldown', 'amethyst_shard')):
        definition = mapping['items'][f'minecraft:{material}'][0]
        assert definition['predicate']['value'] == f'advance_magic:{name}'
        assert z.read(atlas[definition['bedrock_options']['icon']]['textures'] + '.png') == (ROOT / f'art/upgrades/{name}.png').read_bytes()

guide = (dist / 'advance-magic-guide-th.png').read_bytes()
assert guide[:8] == b'\x89PNG\r\n\x1a\n' and min(struct.unpack('>II', guide[16:24])) >= 900
if '--assets-only' not in sys.argv:
    with zipfile.ZipFile(dist / 'advance-magic-1.0.0.jar') as jar:
        for name in (*hashes, 'geyser-mappings.json', 'pack-hashes.json', 'wand-preview.html', 'advance-magic-guide-th.png'):
            assert jar.read('resource-packs/' + name) == (dist / name).read_bytes(), f'Stale/missing embedded asset: {name}'
        assert not any('IntegrationChecks' in name or 'AccountingChecks' in name or name.startswith('net/minecraft/') for name in jar.namelist())
    assert (dist / 'advance-magic-1.0.0.jar').read_bytes() == (ROOT.parent / 'advance-magic.jar').read_bytes()
print('PASS: wand/core/upgrade textures, Java selectors + fallback, Geyser predicates, PNG CRCs, archives, hashes and embedded assets')
