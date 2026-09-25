"""Build deterministic Java/Bedrock wand packs using the project's pixel-art asset style.

The Java enum is the single source for identifiers, titles and gem colors.
No third-party libraries or downloaded textures are needed.
"""
import hashlib
import base64
import html
import json
import re
import shutil
import struct
import tempfile
import zipfile
import zlib
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT.parent / 'tools'))
import restoration_assets
DIST = ROOT / 'dist'
BEDROCK_PACK_VERSION = [1, 6, 0]  # Correct held attachable transforms for Bedrock.


def spells():
    source = (ROOT / 'src/main/java/com/example/advancemagic/spell/Spell.java').read_text(encoding='utf8')
    rows = re.findall(r'(\w+)\("([^"]+)", Material\.(\w+), (\d+), (\d+), 0x([0-9A-F]+)\)', source)
    assert len(rows) == 15, 'The spell catalog must contain exactly 15 spells'
    return [(name.lower(), title, int(color, 16)) for name, title, core, mana, cd, color in rows]


UPGRADES = (
    ('wand_repair', 'prismarine_shard', 'Wand Repair Core'),
    ('wand_damage', 'blaze_powder', 'Wand Damage Core'),
    ('wand_cooldown', 'amethyst_shard', 'Wand Cooldown Core'),
)


def write_json(path, value, line_ending='\n'):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(((json.dumps(value, ensure_ascii=False, indent=2) + '\n').replace('\n', line_ending)).encode('utf8'))


def write_java_json(path, value):
    # Keep the published Java ZIP byte-for-byte stable; its SHA-1 is pinned in config.
    write_json(path, value, '\r\n')


def wand(color, variant):
    pixels = [[(0, 0, 0, 0) for _ in range(32)] for _ in range(32)]
    gem = ((color >> 16) & 255, (color >> 8) & 255, color & 255, 255)
    dark = (27, 22, 40, 255)
    gold = (224, 183, 108, 255)

    def dot(x, y, color, width=1):
        for yy in range(y, y + width):
            for xx in range(x, x + width):
                if 0 <= xx < 32 and 0 <= yy < 32:
                    pixels[yy][xx] = color

    # Diagonal handle follows minecraft:item/handheld so the gem points forward.
    for n in range(20):
        dot(3 + n, 26 - n, dark, 3)
        dot(4 + n, 27 - n, (96, 65, 92, 255))
    for n in (3, 7, 13):
        dot(4 + n, 26 - n, gold, 2)
    # Distinct crystal, halo and fork crowns, with one color per spell.
    cx, cy = 24, 7
    for y in range(-5, 6):
        for x in range(-5, 6):
            distance = abs(x) + abs(y)
            if variant % 3 == 0:
                fill = distance <= 5
            elif variant % 3 == 1:
                fill = 3 <= distance <= 6 or distance <= 1
            else:
                fill = abs(x) == 4 or y == 4 or (abs(x) <= 1 and abs(y) <= 2)
            if fill:
                dot(cx + x, cy + y, dark)
                if distance < 5 or variant % 3:
                    dot(cx + x, cy + y, gem)
    dot(23, 5, (241, 255, 250, 255), 2)
    dot(21, 11, gold, 2)
    return pixels


def png(path, pixels):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    size = len(pixels)
    raw = b''.join(b'\0' + bytes(c for pixel in row for c in pixel) for row in pixels)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0))
                     + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


def archive(folder, path):
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for f in sorted(folder.rglob('*')):
            if f.is_file():
                info = zipfile.ZipInfo(f.relative_to(folder).as_posix(), (2026, 9, 9, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                z.writestr(info, f.read_bytes())


def main():
    DIST.mkdir(parents=True, exist_ok=True)
    target = ROOT / 'target'
    target.mkdir(exist_ok=True)
    # A fresh staging tree prevents removed assets leaking into subsequent builds.
    with tempfile.TemporaryDirectory(prefix='packs-', dir=target) as temp:
        java, bedrock = Path(temp) / 'java', Path(temp) / 'bedrock'
        write_java_json(java / 'pack.mcmeta', {'pack': {'description': 'Advance Magic | 15 Arcane Wands', 'min_format': [75, 0], 'max_format': [88, 0]}})
        write_json(bedrock / 'manifest.json', {
            'format_version': 2,
            'header': {'name': 'Advance Magic', 'description': '15 arcane wands for Geyser',
                       'uuid': '2a3e0ee7-a0df-4102-a03f-a81275edb570', 'version': BEDROCK_PACK_VERSION, 'min_engine_version': [1, 21, 80]},
            'modules': [{'type': 'resources', 'uuid': '071b416b-df41-4c86-9ea4-7d6c3dfc02ac', 'version': BEDROCK_PACK_VERSION}]})
        atlas, definitions, cases = {}, [], []
        core_atlas, core_definitions, core_cases = {}, [], []
        core_titles = {
            'lightning_strike': 'Core of Lightning', 'frost_nova': 'Core of Frost',
            'shadow_step': 'Core of Shadows', 'natures_bloom': 'Core of Nature',
            'earth_wall': 'Core of Earth', 'dragons_breath': 'Core of Dragon',
            'void_pull': 'Core of the Void', 'sonic_boom': 'Core of the Warden',
            'blaze_barrage': 'Core of the Blaze', 'wither_ray': 'Core of Wither',
            'shulker_levitation': 'Core of Levitation', 'meteor_strike': 'Core of Meteor',
            'iron_armor': 'Core of Iron', 'vex_legion': 'Core of Evocation', 'guardian_beam': 'Core of the Guardian'
        }

        # 1. Arcane Wands
        for index, (name, title, color) in enumerate(spells()):
            source = ROOT / f'art/wands/{name}.png'
            if not source.is_file():
                raise FileNotFoundError(f'Missing final wand texture: {source}')
            for destination in (java / f'assets/advance_magic/textures/item/{name}.png', bedrock / f'textures/items/{name}.png'):
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source, destination)
            write_java_json(java / f'assets/advance_magic/models/item/{name}.json', {
                'parent': 'minecraft:item/handheld', 'textures': {'layer0': f'advance_magic:item/{name}'}})
            write_java_json(java / f'assets/advance_magic/items/{name}.json', {
                'model': {'type': 'minecraft:model', 'model': f'advance_magic:item/{name}'}})
            atlas[f'advance_magic.{name}'] = {'textures': f'textures/items/{name}'}
            cases.append({'when': f'advance_magic:{name}', 'model': {'type': 'minecraft:model', 'model': f'advance_magic:item/{name}'}})
            definitions.append({'type': 'definition', 'model': f'advance_magic:{name}',
                                'bedrock_identifier': f'advance_magic:{name}', 'display_name': title + ' Wand',
                                'bedrock_options': {'icon': f'advance_magic.{name}', 'allow_offhand': True, 'display_handheld': True, 'creative_category': 'equipment'}})

        # 2. Magic Cores (15 Elemental Cores)
        for index, (name, title, color) in enumerate(spells()):
            core_source = ROOT / f'art/cores/core_{name}.png'
            if not core_source.is_file():
                raise FileNotFoundError(f'Missing core texture: {core_source}')
            for destination in (java / f'assets/advance_magic/textures/item/core_{name}.png', bedrock / f'textures/items/core_{name}.png'):
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(core_source, destination)
            write_java_json(java / f'assets/advance_magic/models/item/core_{name}.json', {
                'parent': 'minecraft:item/generated', 'textures': {'layer0': f'advance_magic:item/core_{name}'}})
            write_java_json(java / f'assets/advance_magic/items/core_{name}.json', {
                'model': {'type': 'minecraft:model', 'model': f'advance_magic:item/core_{name}'}})
            atlas[f'advance_magic.core_{name}'] = {'textures': f'textures/items/core_{name}'}
            core_cases.append({'when': f'advance_magic:core_{name}', 'model': {'type': 'minecraft:model', 'model': f'advance_magic:item/core_{name}'}})
            core_definitions.append({'type': 'definition', 'model': f'advance_magic:core_{name}',
                                     'bedrock_identifier': f'advance_magic:core_{name}', 'display_name': core_titles[name],
                                     'bedrock_options': {'icon': f'advance_magic.core_{name}', 'allow_offhand': True, 'display_handheld': False, 'creative_category': 'items'}})

        upgrade_definitions = {}
        for name, material, title in UPGRADES:
            source = ROOT / f'art/upgrades/{name}.png'
            if not source.is_file():
                raise FileNotFoundError(f'Missing final upgrade texture: {source}')
            for destination in (java / f'assets/advance_magic/textures/item/{name}.png', bedrock / f'textures/items/{name}.png'):
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source, destination)
            write_java_json(java / f'assets/advance_magic/models/item/{name}.json', {
                'parent': 'minecraft:item/generated', 'textures': {'layer0': f'advance_magic:item/{name}'}})
            write_java_json(java / f'assets/advance_magic/items/{name}.json', {
                'model': {'type': 'minecraft:model', 'model': f'advance_magic:item/{name}'}})
            atlas[f'advance_magic.{name}'] = {'textures': f'textures/items/{name}'}
            upgrade_definitions.setdefault(f'minecraft:{material}', []).append({
                'type': 'definition', 'model': f'advance_magic:{name}',
                'bedrock_identifier': f'advance_magic:{name}', 'display_name': title,
                'bedrock_options': {'icon': f'advance_magic.{name}', 'allow_offhand': True, 'display_handheld': False, 'creative_category': 'items'}})

        restoration_assets.register_magic(java, bedrock, atlas, upgrade_definitions)
        # Include the authored staff resources in the real packs, not as a second
        # pack that could override the existing wand atlas or Geyser mappings.
        staff = ROOT / 'art/flying-staff'
        for source in sorted((staff / 'java').rglob('*')):
            if source.is_file() and source.name not in ('pack.mcmeta', 'pack.png'):
                destination = java / source.relative_to(staff / 'java')
                destination.parent.mkdir(parents=True, exist_ok=True)
                if source.suffix in ('.json', '.mcmeta'):
                    destination.write_bytes(source.read_bytes().replace(b'\r\n', b'\n'))
                else:
                    shutil.copyfile(source, destination)
        for source in sorted((staff / 'bedrock').rglob('*')):
            if source.is_file() and source.name not in ('manifest.json', 'item_texture.json', 'pack_icon.png'):
                destination = bedrock / source.relative_to(staff / 'bedrock')
                destination.parent.mkdir(parents=True, exist_ok=True)
                if source.suffix in ('.json', '.mcmeta'):
                    destination.write_bytes(source.read_bytes().replace(b'\r\n', b'\n'))
                else:
                    shutil.copyfile(source, destination)
        staff_atlas = json.loads((staff / 'bedrock/textures/item_texture.json').read_text())['texture_data']
        atlas.update(staff_atlas)
        staff_definitions = json.loads((staff / 'geyser-mappings.json').read_text())['items']
        for material, rows in staff_definitions.items():
            upgrade_definitions.setdefault(material, []).extend(rows)
        write_json(bedrock / 'textures/item_texture.json', {'resource_pack_name': 'advance_magic', 'texture_name': 'atlas.items', 'texture_data': atlas})
        write_json(DIST / 'geyser-mappings.json', {
            'format_version': 2,
            'items': {
                'minecraft:carrot_on_a_stick': definitions,
                'minecraft:heart_of_the_sea': core_definitions,
                **upgrade_definitions
            }
        })
        shutil.copyfile(ROOT / 'art/wands/frost_nova.png', java / 'pack.png')
        shutil.copyfile(ROOT / 'art/wands/frost_nova.png', bedrock / 'pack_icon.png')
        # Bedrock uses the pack UUID and version for cache/update identity. Keep this
        # monotonically increasing; content hashes can produce a lower revision.
        print('Bedrock content version: ' + '.'.join(map(str, BEDROCK_PACK_VERSION)))
        archive(java, DIST / 'advance-magic-java.zip')
        archive(bedrock, DIST / 'advance-magic-bedrock.mcpack')
        cards = []
        for name, title, color in spells():
            data = base64.b64encode((java / f'assets/advance_magic/textures/item/{name}.png').read_bytes()).decode('ascii')
            cards.append(f'<article><img alt="{html.escape(title)} wand" src="data:image/png;base64,{data}"><h2>{html.escape(title)}</h2><code>{name}</code></article>')
        core_cards = []
        for name, title, color in spells():
            c_name = core_titles[name]
            data = base64.b64encode((java / f'assets/advance_magic/textures/item/core_{name}.png').read_bytes()).decode('ascii')
            core_cards.append(f'<article><img alt="{html.escape(c_name)}" src="data:image/png;base64,{data}"><h2>{html.escape(c_name)}</h2><code>advance_magic:core_{name}</code></article>')

        (DIST / 'wand-preview.html').write_text(
            '<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
            '<title>Advance Magic — Wands & Cores</title><style>'
            'body{background:#14101d;color:#eee5ff;font:16px system-ui;max-width:1100px;margin:40px auto;padding:24px}'
            'main{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:16px;margin-bottom:40px}'
            'article{background:#221a30;border:1px solid #493758;border-radius:14px;padding:20px;text-align:center}'
            'img{width:128px;height:128px;image-rendering:pixelated}h2{font-size:17px}code{font-size:11px;color:#b9a8cd}'
            '</style><h1>Advance Magic</h1><p>15 Arcane Wands &amp; 15 Elemental Cores · Java &amp; Bedrock Textures</p>'
            '<h2>15 Arcane Wands</h2><main>' + ''.join(cards) + '</main>'
            '<h2>15 Magic Cores (Void Vault Drops)</h2><main>' + ''.join(core_cards) + '</main></html>', encoding='utf8')
        hashes = {name: hashlib.sha1((DIST / name).read_bytes()).hexdigest()
                  for name in ('advance-magic-java.zip', 'advance-magic-bedrock.mcpack')}
        write_json(DIST / 'pack-hashes.json', hashes)
        print(json.dumps(hashes, indent=2))


if __name__ == '__main__':
    main()
