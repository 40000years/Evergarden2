"""Build deterministic Java/Bedrock elemental staff packs and shared magic assets.

The authored staff generator supplies the eight equipment items and
cross-client geometry.
"""
import hashlib
import base64
import html
import json
import shutil
import struct
import tempfile
import zipfile
import zlib
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT.parent / 'tools'))
sys.path.insert(0, str(ROOT.parent / 'evergarden/tools'))
import restoration_assets
import mythic_particles
import judgment_assets
import mythic_line_assets
import legendary_effect_assets
import legendary_light_assets
import legendary_seven_assets
import legendary_block_assets
import magic_java_atlas
import legendary_weapon_assets
import elemental_staff_assets
import wooden_staff_assets
import elemental_spell_assets
import elemental_vfx_assets
import seraph_armor_assets
import revenant_armor_assets
import skilltree_gui_assets
import menu_gui_assets
DIST = ROOT / 'dist'
BEDROCK_PACK_VERSION = [2, 33, 1]  # Retired wand-crafting core assets removed.

# Compatibility artwork is retained without adding retired wand registrations.
PRESERVED_WANDS = (
    'solar_apocalypse', 'chronos_final_hour', 'heavens_judgment',
    'verdant_sovereign', 'lunar_requiem', 'crimson_oblivion',
)
STAFF_IDS = ('fire', 'wind', 'earth', 'ice', 'water', 'lightning', 'light', 'dark')


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
                data=f.read_bytes()
                if f.suffix in ('.json','.mcmeta'):data=json.dumps(json.loads(data),ensure_ascii=False,separators=(',',':')).encode('utf8')
                z.writestr(info, data)


def main():
    DIST.mkdir(parents=True, exist_ok=True)
    target = ROOT / 'target'
    target.mkdir(exist_ok=True)
    # A fresh staging tree prevents removed assets leaking into subsequent builds.
    with tempfile.TemporaryDirectory(prefix='packs-', dir=target) as temp:
        java, bedrock = Path(temp) / 'java', Path(temp) / 'bedrock'
        write_java_json(java / 'pack.mcmeta', {'pack': {'description': f'Advance Magic | {len(STAFF_IDS)} Elemental Staffs + Amethystwood Staff', 'min_format': [75, 0], 'max_format': [88, 0]}})
        write_json(bedrock / 'manifest.json', {
            'format_version': 2,
            'header': {'name': 'Advance Magic', 'description': f'{len(STAFF_IDS)} elemental staffs + Amethystwood staff for Geyser',
                       'uuid': 'e8233d13-8c1f-4eb9-8275-4d1d32a9c193', 'version': BEDROCK_PACK_VERSION, 'min_engine_version': [1, 21, 80]},
            'modules': [{'type': 'resources', 'uuid': '540c6378-ed9a-4e3a-b121-8f5d10607f72', 'version': BEDROCK_PACK_VERSION}]})
        atlas = {}

        # 1. Preserved mythic/legendary resources, without old catalog entries.
        for name in PRESERVED_WANDS:
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
        mythic_particles.register(bedrock,write_json,png)
        judgment_assets.register_java(java,write_java_json)
        judgment_assets.register_bedrock(bedrock,write_json,png)
        mythic_line_assets.register_java(java,write_java_json)
        mythic_line_assets.register_bedrock(bedrock,write_json)
        legendary_effect_assets.register_java(java,write_java_json)
        legendary_effect_assets.register_bedrock(bedrock,write_json)
        legendary_light_assets.register_java(java,write_java_json)
        legendary_light_assets.register_bedrock(bedrock,write_json)
        legendary_block_assets.write_assets(bedrock)
        magic_java_atlas.register(java,write_java_json)
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
        # Fixed-color potion sprites avoid the clients' different vanilla potion tint layers.
        potion_definitions=[]
        for quality in ('low','medium','good','supreme'):
            name='mana_potion_'+quality
            source=ROOT / f'art/potions/{name}.png'
            for destination in (java / f'assets/advance_magic/textures/item/{name}.png',bedrock / f'textures/items/{name}.png'):
                destination.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,destination)
            write_java_json(java / f'assets/advance_magic/models/item/{name}.json',{
                'parent':'minecraft:item/generated','textures':{'layer0':f'advance_magic:item/{name}'}})
            write_java_json(java / f'assets/advance_magic/items/{name}.json',{
                'model':{'type':'minecraft:model','model':f'advance_magic:item/{name}'}})
            atlas[f'advance_magic.{name}']={'textures':f'textures/items/{name}'}
            potion_definitions.append({'type':'definition','model':f'advance_magic:{name}',
                'bedrock_identifier':f'advance_magic:{name}','display_name':'Mana Potion',
                'bedrock_options':{'icon':f'advance_magic.{name}','allow_offhand':True,'display_handheld':False}})
        upgrade_definitions['minecraft:potion']=potion_definitions
        legendary_weapon_assets.register(java, bedrock, atlas, upgrade_definitions)
        elemental_staff_assets.register(java, bedrock, atlas, upgrade_definitions)
        wooden_staff_assets.register(java, bedrock, atlas, upgrade_definitions)
        elemental_spell_assets.register(ROOT, java, bedrock, atlas, upgrade_definitions)
        elemental_vfx_assets.register(java, bedrock)
        seraph_armor_assets.register(java, bedrock, atlas, upgrade_definitions)
        revenant_armor_assets.register(java, bedrock, atlas, upgrade_definitions)
        skilltree_gui_assets.register(java, bedrock, atlas, upgrade_definitions)
        menu_gui_assets.register(java, bedrock, atlas, upgrade_definitions)
        legendary_seven_assets.register(java, bedrock, atlas, upgrade_definitions)
        write_json(bedrock / 'textures/item_texture.json', {'resource_pack_name': 'advance_magic', 'texture_name': 'atlas.items', 'texture_data': atlas})
        write_json(DIST / 'geyser-mappings.json', {
            'format_version': 2,
            'items': upgrade_definitions
        })
        icon = bedrock / 'textures/elemental_staffs/ice_icon.png'
        shutil.copyfile(icon, java / 'pack.png')
        shutil.copyfile(icon, bedrock / 'pack_icon.png')
        # Bedrock uses the pack UUID and version for cache/update identity. Keep this
        # monotonically increasing; content hashes can produce a lower revision.
        print('Bedrock content version: ' + '.'.join(map(str, BEDROCK_PACK_VERSION)))
        archive(java, DIST / 'advance-magic-java.zip')
        archive(bedrock, DIST / 'advance-magic-bedrock.mcpack')
        # Editable models are a separate deliverable, never unnecessary client
        # pack downloads. The same deterministic archive helper normalizes dates.
        sources = Path(temp) / 'elemental-staff-sources'
        authored = ROOT / 'art/elemental-staffs'
        models = sorted((authored / 'blockbench').glob('*.bbmodel'))
        if len(models) != len(STAFF_IDS):
            raise ValueError('Source bundle requires exactly eight authored .bbmodel files')
        for source in [*models, authored / 'metadata.json', authored / 'README.md']:
            destination = sources / source.relative_to(authored)
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, destination)
        shutil.copyfile(ROOT.parent / 'tools/elemental_staff_assets.py', sources / 'elemental_staff_assets.py')
        archive(sources, DIST / 'elemental-staff-sources.zip')
        wooden_sources = Path(temp) / 'wooden-staff-sources'
        wooden_art = ROOT / 'art/wooden-staff'
        for name in ('wooden_staff.bbmodel', 'metadata.json', 'materials.png', 'wood.geometry.json', 'README.md'):
            wooden_sources.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(wooden_art / name, wooden_sources / name)
        shutil.copyfile(ROOT.parent / 'tools/wooden_staff_assets.py', wooden_sources / 'wooden_staff_assets.py')
        archive(wooden_sources, DIST / 'wooden-staff-sources.zip')
        cards = []
        for name in STAFF_IDS:
            title = name.title() + ' Staff'
            data = base64.b64encode((bedrock / f'textures/elemental_staffs/{name}_icon.png').read_bytes()).decode('ascii')
            cards.append(f'<article><img alt="{html.escape(title)}" src="data:image/png;base64,{data}"><h2>{html.escape(title)}</h2><code>advance_magic:staff_{name}</code></article>')
        (DIST / 'elemental-staff-preview.html').write_text(
            '<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
            '<title>Advance Magic | Elemental Staffs</title><style>'
            'body{background:#14101d;color:#eee5ff;font:16px system-ui;max-width:1100px;margin:40px auto;padding:24px}'
            'main{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:16px;margin-bottom:40px}'
            'article{background:#221a30;border:1px solid #493758;border-radius:14px;padding:20px;text-align:center}'
            'img{width:128px;height:128px;image-rendering:pixelated}h2{font-size:17px}code{font-size:11px;color:#b9a8cd}'
            f'</style><h1>Advance Magic</h1><p>{len(STAFF_IDS)} Elemental Staffs | Java &amp; Bedrock</p>'
            f'<h2>{len(STAFF_IDS)} Elemental Staffs</h2><main>' + ''.join(cards) + '</main></html>', encoding='utf8')
        hashes = {name: hashlib.sha1((DIST / name).read_bytes()).hexdigest()
                  for name in ('advance-magic-java.zip', 'advance-magic-bedrock.mcpack')}
        write_json(DIST / 'pack-hashes.json', hashes)
        print(json.dumps(hashes, indent=2))


if __name__ == '__main__':
    main()
