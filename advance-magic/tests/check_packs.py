"""Validate release assets, model routing, fallback and embedded binary integrity."""
import hashlib
import io
import json
import re
import runpy
import struct
import sys
import zipfile
import zlib
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT.parent / 'tools'))
from bedrock_particle_checks import check_direction_modes
from legendary_effect_assets import PLANES as LEGENDARY_PLANES, RAYS as LEGENDARY_RAYS
from check_retired_core_cleanup import assert_no_retired, assert_mapping_cleanup
check_direction_modes(ROOT / 'dist/advance-magic-bedrock.mcpack')
builder = runpy.run_path(str(ROOT / 'tools/build_packs.py'))
catalog = builder['spells']()
staff_ids = builder['STAFF_IDS']
preserved_wands = builder['PRESERVED_WANDS']
retired_wands = tuple(name for name, _, _ in catalog if name not in preserved_wands) + (
    'invisibility_shroud', 'poison_spores', 'soul_drain', 'time_dilation')
dist = ROOT / 'dist'
for pack_name in ('advance-magic-java.zip', 'advance-magic-bedrock.mcpack'):
    with zipfile.ZipFile(dist / pack_name) as retired_check:
        assert_no_retired(retired_check)
hashes = json.loads((dist / 'pack-hashes.json').read_text())
service_source = (ROOT / 'src/main/java/com/example/advancemagic/pack/ResourcePackService.java').read_text()
pack_config = (ROOT / 'src/main/resources/config.yml').read_text()
assert "url: ''" in pack_config
assert "sha1: ''" in pack_config
assert 'host:\n    enabled: true' in pack_config
assert 'CURRENT_SHA1' not in service_source

cleanup = json.loads((ROOT.parent / 'docs/elemental-staff-cleanup.json').read_text())
for row in cleanup['removed']:
    assert not (ROOT.parent / row['path']).exists(), f'Retired asset reintroduced: {row["path"]}'
for row in cleanup['preserved_sha256']:
    assert hashlib.sha256((ROOT.parent / row['path']).read_bytes()).hexdigest() == row['sha256'], f'Preserved shared source changed: {row["path"]}'
with zipfile.ZipFile(dist / 'elemental-staff-sources.zip') as sources:
    assert sources.testzip() is None
    assert all(info.date_time == (2026, 9, 9, 0, 0, 0) for info in sources.infolist()), 'Source ZIP timestamps must be deterministic'
    models = [name for name in sources.namelist() if name.endswith('.bbmodel')]
    assert len(models) == len(staff_ids) == 8
    for path in models:
        assert sources.read(path) == (ROOT / 'art/elemental-staffs' / path).read_bytes()
        model = json.loads(sources.read(path))
        assert model['elements'] and model['textures'], f'Empty source model: {path}'
    assert json.loads(sources.read('metadata.json')) == json.loads((ROOT / 'art/elemental-staffs/metadata.json').read_bytes())
    assert sources.read('README.md') == (ROOT / 'art/elemental-staffs/README.md').read_bytes()
    assert sources.read('elemental_staff_assets.py') == (ROOT.parent / 'tools/elemental_staff_assets.py').read_bytes()


def check_png(data, core=False):
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
    assert sum(a == 0 for a in alpha) > w*h*(0.5 if core else 0.55), 'Background must really be transparent'
    assert sum(a >= 250 for a in alpha) > w*h*0.025, 'Item must have visible solid pixels'
    if not core:
        assert set(alpha) == {0, 255}, 'Crisp cutout without a translucent checkerboard'
    assert all(alpha[y*w+x] <= (8 if core else 0) for y in range(h) for x in range(w) if x < 3 or x >= w-3 or y < 3 or y >= h-3), 'Safe transparent border'


for name, digest in hashes.items():
    assert hashlib.sha1((dist / name).read_bytes()).hexdigest() == digest
    with zipfile.ZipFile(dist / name) as z:
        assert z.testzip() is None
        for file in z.namelist():
            assert not any(token in file for token in ("armor_aegis", "armor_kavacha", "armor_aegishjalmur", "legendary_armor/", "legendary_armor_hidden")), file
            if file.endswith(('.json', '.mcmeta')): json.loads(z.read(file))
            if file.endswith('.png'):
                if 'elemental_staffs/' in file:
                    image=z.read(file)
                    assert image[:8] == b'\x89PNG\r\n\x1a\n'
                    assert struct.unpack('>II',image[16:24]) in {(128,128),(128,1024)}
                    with Image.open(io.BytesIO(image)) as png_image:
                        assert png_image.mode == 'RGBA'
                        png_image.verify()  # Verify every PNG chunk CRC, not only its signature.
                elif 'legendary_weapons/' in file or ('legendary_' in file and '_sigil' in file) or 'legendary_longinus_rain' in file:
                    image=z.read(file)
                    assert image[:8] == b'\x89PNG\r\n\x1a\n'
                    assert struct.unpack('>II',image[16:24]) in {(128,128),(128,1024),(256,256)}
                elif 'flying_staff' in file:
                    image=z.read(file)
                    assert image[:8] == b'\x89PNG\r\n\x1a\n'
                    assert struct.unpack('>II',image[16:24]) in {(16,16),(64,64),(64,1024),(128,128)}
                elif any(name in file for name in ('judgment_seal','solar_corona','solar_orbit','chronos_dial','chronos_minute','chronos_hour','chronos_ripple','shulker_prism',*LEGENDARY_PLANES)):
                    image=z.read(file)
                    assert image[:8] == b'\x89PNG\r\n\x1a\n'
                    assert struct.unpack('>II',image[16:24]) == (1024,1024)
                elif any(name in file for name in ('solar_ray','chronos_ray','chronos_echo',*LEGENDARY_RAYS)):
                    image=z.read(file)
                    assert image[:8] == b'\x89PNG\r\n\x1a\n'
                    assert struct.unpack('>II',image[16:24]) == (256,256)
                elif 'judgment_beam' in file:
                    image=z.read(file)
                    assert image[:8] == b'\x89PNG\r\n\x1a\n'
                    assert struct.unpack('>II',image[16:24]) == (128,128)
                elif 'restoration_' in file:
                    image=z.read(file)
                    assert image[:8] == b'\x89PNG\r\n\x1a\n'
                    assert struct.unpack('>II',image[16:24]) == (64,64)
                else:
                    try: check_png(z.read(file), core='core_' in file or 'mana_potion_' in file)
                    except AssertionError as error: raise AssertionError(f'{file}: {error}') from error

# Advance Magic owns its emitters; Evergarden must not override them when both packs load.
with zipfile.ZipFile(dist / 'advance-magic-bedrock.mcpack') as magic, zipfile.ZipFile(ROOT.parent / 'evergarden/dist/evergarden-bedrock.mcpack') as garden:
    for tint in ('gold', 'white', 'orange', 'cyan', 'violet', 'emerald', 'jade', 'silver', 'crimson', 'ruby'):
        path=f'particles/mythic_{tint}.particle.json'
        assert path not in garden.namelist()
        effect=json.loads(magic.read(path))['particle_effect']
        assert effect['description']['identifier']==f'advance_magic:mythic_{tint}'
        texture=effect['description']['basic_render_parameters']['texture']+'.png'
        assert magic.read(texture) and texture not in garden.namelist()
        components=effect['components']
        assert components['minecraft:emitter_rate_instant']['num_particles']==1
        assert components['minecraft:particle_lifetime_expression']['max_lifetime']<=1
        assert all('variable.magic_size' in expression for expression in components['minecraft:particle_appearance_billboard']['size'])

with zipfile.ZipFile(dist / 'advance-magic-java.zip') as z:
    names=('solar_corona','solar_orbit','chronos_dial','chronos_minute','chronos_hour','chronos_ripple','shulker_prism','solar_ray','chronos_ray','chronos_echo')
    with zipfile.ZipFile(ROOT.parent/'evergarden/dist/evergarden-java.zip') as garden_java, zipfile.ZipFile(dist/'advance-magic-bedrock.mcpack') as bedrock, zipfile.ZipFile(ROOT.parent/'evergarden/dist/evergarden-bedrock.mcpack') as garden_bedrock:
        for name in names:
            source=(ROOT/f'art/effects/{name}.png').read_bytes()
            texture=f'assets/advance_magic/textures/effect/{name}.png'
            assert z.read(texture)==source and texture not in garden_java.namelist()
            mesh=json.loads(z.read(f'assets/advance_magic/models/effect/{name}.json'))
            assert all(not element['shade'] for element in mesh['elements'])
            sides=set(mesh['elements'][0]['faces'])
            assert sides==({'north','south','east','west'} if name.endswith(('_ray','_echo')) else {'north','south'})
            assert bedrock.read(f'textures/particle/{name}.png')==source
            assert f'textures/particle/{name}.png' not in garden_bedrock.namelist()
            ray=name.endswith(('_ray','_echo'))
            for suffix,mode in (('', 'direction_y' if ray else 'direction_z'),('_flat','direction_y' if ray else 'emitter_transform_xz')):
                path=f'particles/{name}{suffix}.particle.json'
                particle=json.loads(bedrock.read(path))
                assert path not in garden_bedrock.namelist()
                effect=particle['particle_effect']
                assert effect['description']['identifier']==f'advance_magic:{name}{suffix}'
                assert effect['description']['basic_render_parameters']['material']=='particles_blend', 'World planes must support views from below/behind and translucent stroke alpha'
                assert effect['components']['minecraft:particle_appearance_billboard']['facing_camera_mode']==mode
                components=effect['components']
                assert components['minecraft:emitter_shape_point']['offset']==['variable.line_offset_x','variable.line_offset_y','variable.line_offset_z'], 'Nearby emitters must place every plane at its original world center'
                assert components['minecraft:particle_lifetime_expression']['max_lifetime']==(.10 if ray else .20)
                if ray:
                    assert components['minecraft:particle_initial_spin']=={'rotation':0,'rotation_rate':0}
                    assert components['minecraft:particle_appearance_billboard']['direction']['mode']=='custom'
    for variant in range(2):
        name=f'judgment_seal_{variant}'
        mesh=json.loads(z.read(f'assets/advance_magic/models/effect/{name}.json'))
        face=mesh['elements'][0]
        assert set(face['faces'])=={'up','down'} and not face['shade']
        assert abs(face['to'][1]-face['from'][1])<.03, 'Horizontal, visible from both sides'
        assert z.read(f'assets/advance_magic/textures/effect/{name}.png')==(ROOT/f'art/effects/{name}.png').read_bytes()
    beam=json.loads(z.read('assets/advance_magic/items/judgment_beam.json'))['model']
    assert beam['model']=='advance_magic:effect/judgment_beam'
    mesh=json.loads(z.read('assets/advance_magic/models/effect/judgment_beam.json'))
    assert mesh['textures']['beam']=='minecraft:entity/beacon_beam'
    assert len(mesh['elements'])==2 and all(not element['shade'] for element in mesh['elements'])
    assert all(element['from'][1]==0 and element['to'][1]==16 for element in mesh['elements']), 'Both beam layers must span the full centered item height to reach the ground'
    assert not mesh.get('display'), 'The beam must not inherit an extra item transform that shifts its endpoints'
    pack = json.loads(z.read('pack.mcmeta'))['pack']
    assert pack['min_format'] == [75, 0] and pack['max_format'] == [88, 0]
    assert 'assets/minecraft/items/carrot_on_a_stick.json' not in z.namelist()
    for name in preserved_wands:
        item = json.loads(z.read(f'assets/advance_magic/items/{name}.json'))
        assert item['model']['model'] == f'advance_magic:item/{name}'
        model = json.loads(z.read(f'assets/advance_magic/models/item/{name}.json'))
        assert model['parent'] == 'minecraft:item/handheld'
        assert 'elements' not in model, 'Preserved compatibility art retains its original flat model'
        assert model['textures']['layer0'] == f'advance_magic:item/{name}'
        assert z.read(f'assets/advance_magic/textures/item/{name}.png') == (ROOT / f'art/wands/{name}.png').read_bytes()
    for name in retired_wands:
        for folder, extension in (('items', 'json'), ('models/item', 'json'), ('textures/item', 'png')):
            assert f'assets/advance_magic/{folder}/{name}.{extension}' not in z.namelist(), f'Retired ordinary wand remains: {name}'
    for name in staff_ids:
        item = json.loads(z.read(f'assets/advance_magic/items/staff_{name}.json'))['model']
        assert item['type'] == 'minecraft:select' and item['property'] == 'minecraft:display_context'
        assert item['fallback']['model'] == f'advance_magic:item/elemental_staffs/{name}'
        model = json.loads(z.read(f'assets/advance_magic/models/item/elemental_staffs/{name}.json'))
        assert len(model['elements']) > 40, 'Each staff must contain authored 3D geometry'
        for texture in model['textures'].values():
            if texture.startswith('advance_magic:'):
                assert z.read('assets/' + texture.replace(':', '/textures/', 1) + '.png'), texture
    for name in ('wand_repair', 'wand_damage', 'wand_cooldown'):
        item = json.loads(z.read(f'assets/advance_magic/items/{name}.json'))
        assert item['model']['model'] == f'advance_magic:item/{name}'
        assert z.read(f'assets/advance_magic/textures/item/{name}.png') == (ROOT / f'art/upgrades/{name}.png').read_bytes()
    for name in ('restoration_wand','restoration_core'):
        item=json.loads(z.read(f'assets/advance_magic/items/{name}.json'))
        model=json.loads(z.read(f'assets/advance_magic/models/item/{name}.json'))
        assert item['model']['model'] == f'advance_magic:item/{name}'
        assert len(model['elements']) >= 10
    assert len(json.loads(z.read('assets/advance_magic/models/item/flying_staff.json'))['elements']) == 79
    for state,count in [('summon',7),('idle',8),('flight',8),('dismiss',5)]:
        definition=json.loads(z.read(f'assets/advance_magic/items/flying_staff_{state}.json'))['model']
        assert definition['type']=='minecraft:range_dispatch'
        assert len(definition['entries'])==count

mapping = json.loads((dist / 'geyser-mappings.json').read_text())
assert_mapping_cleanup(mapping)
assert mapping['format_version'] == 2
definitions = mapping['items']['minecraft:carrot_on_a_stick']
assert len(definitions) == len(staff_ids) == 8
assert {row['model'] for row in definitions} == {f'advance_magic:staff_{name}' for name in staff_ids}
assert len({row['bedrock_identifier'] for row in definitions}) == 8
all_definitions = [row for rows in mapping['items'].values() for row in rows]
assert not any(row['model'] == f'advance_magic:{name}' for row in all_definitions for name, _, _ in catalog), 'Per-spell wands are no longer registered'
with zipfile.ZipFile(dist / 'advance-magic-bedrock.mcpack') as z:
    manifest = json.loads(z.read('manifest.json'))
    assert manifest['header']['version'] == builder['BEDROCK_PACK_VERSION'] == [2, 15, 1]
    assert manifest['modules'][0]['version'] == manifest['header']['version']
    assert tuple(manifest['header']['version']) > (1, 1, 40161), 'v2 Bedrock pack must supersede the Afterdeath release'
    atlas = json.loads(z.read('textures/item_texture.json'))['texture_data']
    potion_rows=mapping['items']['minecraft:potion']
    assert len(potion_rows)==4
    with zipfile.ZipFile(dist / 'advance-magic-java.zip') as java:
        for row,quality in zip(potion_rows,('low','medium','good','supreme')):
            name='mana_potion_'+quality
            assert row['model']==row['bedrock_identifier']==f'advance_magic:{name}'
            assert row['bedrock_options']['allow_offhand'] and not row['bedrock_options']['display_handheld']
            assert z.read(atlas[row['bedrock_options']['icon']]['textures']+'.png')==java.read(f'assets/advance_magic/textures/item/{name}.png')==(ROOT/f'art/potions/{name}.png').read_bytes()
            model=json.loads(java.read(f'assets/advance_magic/models/item/{name}.json'))
            assert model=={'parent':'minecraft:item/generated','textures':{'layer0':f'advance_magic:item/{name}'}}
            item=json.loads(java.read(f'assets/advance_magic/items/{name}.json'))
            assert item['model']=={'type':'minecraft:model','model':f'advance_magic:item/{name}'}
    effect=json.loads(z.read('particles/judgment_beam.particle.json'))['particle_effect']
    assert effect['description']['identifier']=='advance_magic:judgment_beam'
    billboard=effect['components']['minecraft:particle_appearance_billboard']
    assert billboard['facing_camera_mode']=='lookat_y'
    assert effect['components']['minecraft:emitter_shape_point']['offset']==[0,0,0]
    assert effect['components']['minecraft:particle_initial_speed']==0, 'Bedrock beam stays fixed between its two endpoints'
    assert billboard['uv']['uv'][1]==0 and billboard['uv']['uv_size'][1]==128, 'Beam texture must reach both vertical edges'
    assert 'variable.beam_width' in billboard['size'][0] and 'variable.beam_height' in billboard['size'][1]
    with zipfile.ZipFile(ROOT.parent/'evergarden/dist/evergarden-bedrock.mcpack') as garden:
        assert 'particles/judgment_beam.particle.json' not in garden.namelist()
        assert 'textures/particle/judgment_beam.png' not in garden.namelist()
        for variant in range(2):
            name=f'judgment_seal_{variant}'
            particle=f'particles/{name}.particle.json'
            seal=json.loads(z.read(particle))['particle_effect']
            assert seal['components']['minecraft:particle_appearance_billboard']['facing_camera_mode']=='emitter_transform_xz'
            assert particle not in garden.namelist()
            assert f'textures/particle/{name}.png' not in garden.namelist()
    for definition, name in zip(definitions, staff_ids):
        assert definition['model'] == definition['bedrock_identifier'] == f'advance_magic:staff_{name}'
        assert 'predicate' not in definition
        assert definition['bedrock_options']['creative_category'] == 'equipment'
        assert z.read(atlas[definition['bedrock_options']['icon']]['textures'] + '.png') == (ROOT / f'art/elemental-staffs/bedrock/textures/elemental_staffs/{name}_icon.png').read_bytes()
        attach = json.loads(z.read(f'attachables/elemental_staffs/{name}.json'))['minecraft:attachable']['description']
        assert attach['identifier'] == definition['bedrock_identifier']
        for texture in attach['textures'].values():
            assert z.read(texture + '.png'), f'Missing attachable texture: {texture}'
    for name in retired_wands:
        assert f'textures/items/{name}.png' not in z.namelist(), name
        assert f'advance_magic.{name}' not in atlas, name
    for name in preserved_wands:
        assert z.read(f'textures/items/{name}.png') == (ROOT / f'art/wands/{name}.png').read_bytes()
    for name, material in (('wand_repair', 'prismarine_shard'), ('wand_damage', 'blaze_powder'), ('wand_cooldown', 'amethyst_shard')):
        definition = mapping['items'][f'minecraft:{material}'][0]
        assert definition['model'] == f'advance_magic:{name}'
        assert 'predicate' not in definition
        assert z.read(atlas[definition['bedrock_options']['icon']]['textures'] + '.png') == (ROOT / f'art/upgrades/{name}.png').read_bytes()
    assert_mapping_cleanup(mapping)
    assert_no_retired(z)
    for name,material in (('restoration_wand','blaze_rod'),('restoration_core','prismarine_crystals')):
        definition=mapping['items'][f'minecraft:{material}'][0]
        assert definition['model'] == f'advance_magic:{name}'
        assert definition['bedrock_identifier'] == f'advance_magic:{name}'
        assert f'attachables/{name}.json' in z.namelist()
        assert f'models/entity/{name}.geo.json' in z.namelist()
    for state in ('','_summon','_idle','_flight','_dismiss'):
        name='flying_staff'+state
        material='blaze_rod' if not state else 'carved_pumpkin'
        assert any(row['model']==f'advance_magic:{name}' for row in mapping['items'][f'minecraft:{material}'])
        assert f'attachables/{name}.json' in z.namelist()
        assert f'assets/advance_magic/items/{name}.json' in zipfile.ZipFile(dist/'advance-magic-java.zip').namelist()
    assert 'animations/flying_staff.animation.json' in z.namelist()
    assert 'particles/flying_staff_trail.particle.json' in z.namelist()

guide = (dist / 'advance-magic-guide-th.png').read_bytes()
assert guide[:8] == b'\x89PNG\r\n\x1a\n' and min(struct.unpack('>II', guide[16:24])) >= 900
if '--assets-only' not in sys.argv:
    from release_jar_checks import check_jar
    check_jar(ROOT.parent / 'dist/advance-magic.jar')
    with zipfile.ZipFile(ROOT.parent / 'dist/advance-magic.jar') as jar:
        for name in (*hashes, 'geyser-mappings.json', 'pack-hashes.json'):
            assert jar.read('resource-packs/' + name) == (dist / name).read_bytes(), f'Stale/missing embedded asset: {name}'
        assert not any('IntegrationChecks' in name or 'AccountingChecks' in name or name.startswith('net/minecraft/') for name in jar.namelist())
    assert (ROOT / 'target/advance-magic.jar').read_bytes() == (ROOT.parent / 'dist/advance-magic.jar').read_bytes()
print('PASS: eight elemental staff routes, 21 retired crafting cores absent, active utility cores and legendary compatibility art preserved, effect textures, models, Geyser mappings, PNGs, archives and hashes'
      + (' (assets only)' if '--assets-only' in sys.argv else ' and embedded assets'))

