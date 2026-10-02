"""Validate release assets, model routing, fallback and embedded binary integrity."""
import hashlib
import json
import re
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
service_source = (ROOT / 'src/main/java/com/example/advancemagic/pack/ResourcePackService.java').read_text()
pack_config = (ROOT / 'src/main/resources/config.yml').read_text()
assert "url: 'https://raw.githubusercontent.com/40000years/Evergarden2/0e6a864979528b50e00e48baa98e267993803ba4/advance-magic/dist/advance-magic-java.zip'" in pack_config
assert "sha1: '278eef0ea167e62d84152f57bb32c60483b62e8f'" in pack_config
assert 'host:\n    enabled: false' in pack_config
assert 'CURRENT_SHA1' not in service_source


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
            if file.endswith(('.json', '.mcmeta')): json.loads(z.read(file))
            if file.endswith('.png'):
                if 'flying_staff' in file:
                    image=z.read(file)
                    assert image[:8] == b'\x89PNG\r\n\x1a\n'
                    assert struct.unpack('>II',image[16:24]) in {(16,16),(64,64),(64,1024),(128,128)}
                elif any(name in file for name in ('judgment_seal','solar_corona','solar_orbit','chronos_dial','chronos_minute','chronos_hour','chronos_ripple')):
                    image=z.read(file)
                    assert image[:8] == b'\x89PNG\r\n\x1a\n'
                    assert struct.unpack('>II',image[16:24]) == (1024,1024)
                elif any(name in file for name in ('solar_ray','chronos_ray','chronos_echo')):
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
                    try: check_png(z.read(file), core='core_' in file)
                    except AssertionError as error: raise AssertionError(f'{file}: {error}') from error

# The new colour adapter must never reference an emitter missing from either pack.
with zipfile.ZipFile(dist / 'advance-magic-bedrock.mcpack') as magic, zipfile.ZipFile(ROOT.parent / 'evergarden/dist/evergarden-bedrock.mcpack') as garden:
    for tint in ('gold', 'white', 'orange', 'cyan', 'violet'):
        path=f'particles/mythic_{tint}.particle.json'
        assert json.loads(magic.read(path))==json.loads(garden.read(path))
        effect=json.loads(magic.read(path))['particle_effect']
        assert effect['description']['identifier']==f'advance_magic:mythic_{tint}'
        texture=effect['description']['basic_render_parameters']['texture']+'.png'
        assert magic.read(texture)==garden.read(texture)
        components=effect['components']
        assert components['minecraft:emitter_rate_instant']['num_particles']==1
        assert components['minecraft:particle_lifetime_expression']['max_lifetime']<=1
        assert all('variable.magic_size' in expression for expression in components['minecraft:particle_appearance_billboard']['size'])

with zipfile.ZipFile(dist / 'advance-magic-java.zip') as z:
    names=('solar_corona','solar_orbit','chronos_dial','chronos_minute','chronos_hour','chronos_ripple','solar_ray','chronos_ray','chronos_echo')
    with zipfile.ZipFile(ROOT.parent/'evergarden/dist/evergarden-java.zip') as garden_java, zipfile.ZipFile(dist/'advance-magic-bedrock.mcpack') as bedrock, zipfile.ZipFile(ROOT.parent/'evergarden/dist/evergarden-bedrock.mcpack') as garden_bedrock:
        for name in names:
            source=(ROOT/f'art/effects/{name}.png').read_bytes()
            texture=f'assets/advance_magic/textures/effect/{name}.png'
            assert z.read(texture)==garden_java.read(texture)==source
            mesh=json.loads(z.read(f'assets/advance_magic/models/effect/{name}.json'))
            assert all(not element['shade'] for element in mesh['elements'])
            sides=set(mesh['elements'][0]['faces'])
            assert sides==({'north','south','east','west'} if name.endswith(('_ray','_echo')) else {'north','south'})
            assert bedrock.read(f'textures/particle/{name}.png')==garden_bedrock.read(f'textures/particle/{name}.png')==source
            ray=name.endswith(('_ray','_echo'))
            for suffix,mode in (('', 'direction_y' if ray else 'direction_z'),('_flat','direction_y' if ray else 'emitter_transform_xz')):
                path=f'particles/{name}{suffix}.particle.json'
                particle=json.loads(bedrock.read(path))
                assert particle==json.loads(garden_bedrock.read(path))
                effect=particle['particle_effect']
                assert effect['description']['identifier']==f'advance_magic:{name}{suffix}'
                assert effect['description']['basic_render_parameters']['material']==('particles_blend' if ray else 'particles_alpha')
                assert effect['components']['minecraft:particle_appearance_billboard']['facing_camera_mode']==mode
                components=effect['components']
                assert components['minecraft:particle_lifetime_expression']['max_lifetime']==(.10 if ray else .20)
                if ray:
                    assert components['minecraft:particle_initial_spin']=={'rotation':0,'rotation_rate':0}
                    assert components['minecraft:particle_appearance_billboard']['direction']['mode']=='custom_direction'
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
    pack = json.loads(z.read('pack.mcmeta'))['pack']
    assert pack['min_format'] == [75, 0] and pack['max_format'] == [88, 0]
    assert 'assets/minecraft/items/carrot_on_a_stick.json' not in z.namelist()
    for name, _, _ in catalog:
        item = json.loads(z.read(f'assets/advance_magic/items/{name}.json'))
        assert item['model']['model'] == f'advance_magic:item/{name}'
        model = json.loads(z.read(f'assets/advance_magic/models/item/{name}.json'))
        assert model['parent'] == 'minecraft:item/handheld'
        assert 'elements' not in model, 'Wands retain flat pixel art instead of authored 3D geometry'
        assert model['textures']['layer0'] == f'advance_magic:item/{name}'
        assert z.read(f'assets/advance_magic/textures/item/{name}.png') == (ROOT / f'art/wands/{name}.png').read_bytes()
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
assert mapping['format_version'] == 2
definitions = mapping['items']['minecraft:carrot_on_a_stick']
assert len(definitions) == len(catalog) == 18
assert len({row['bedrock_identifier'] for row in definitions}) == 18
with zipfile.ZipFile(dist / 'advance-magic-bedrock.mcpack') as z:
    manifest = json.loads(z.read('manifest.json'))
    assert manifest['header']['version'] == [2, 6, 1]
    assert manifest['modules'][0]['version'] == [2, 6, 1]
    assert tuple(manifest['header']['version']) > (1, 1, 40161), 'v2 Bedrock pack must supersede the Afterdeath release'
    atlas = json.loads(z.read('textures/item_texture.json'))['texture_data']
    effect=json.loads(z.read('particles/judgment_beam.particle.json'))['particle_effect']
    assert effect['description']['identifier']=='advance_magic:judgment_beam'
    billboard=effect['components']['minecraft:particle_appearance_billboard']
    assert billboard['facing_camera_mode']=='lookat_y'
    assert 'variable.beam_width' in billboard['size'][0] and 'variable.beam_height' in billboard['size'][1]
    with zipfile.ZipFile(ROOT.parent/'evergarden/dist/evergarden-bedrock.mcpack') as garden:
        assert json.loads(garden.read('particles/judgment_beam.particle.json'))==json.loads(z.read('particles/judgment_beam.particle.json'))
        assert garden.read('textures/particle/judgment_beam.png')==z.read('textures/particle/judgment_beam.png')
        for variant in range(2):
            name=f'judgment_seal_{variant}'
            particle=f'particles/{name}.particle.json'
            seal=json.loads(z.read(particle))['particle_effect']
            assert seal['components']['minecraft:particle_appearance_billboard']['facing_camera_mode']=='emitter_transform_xz'
            assert json.loads(garden.read(particle))==json.loads(z.read(particle))
            assert garden.read(f'textures/particle/{name}.png')==z.read(f'textures/particle/{name}.png')
    for definition, (name, _, _) in zip(definitions, catalog):
        assert definition['model'] == f'advance_magic:{name}'
        assert 'predicate' not in definition
        assert definition['bedrock_options']['creative_category'] == 'equipment'
        assert z.read(atlas[definition['bedrock_options']['icon']]['textures'] + '.png') == (ROOT / f'art/wands/{name}.png').read_bytes()
    for name, material in (('wand_repair', 'prismarine_shard'), ('wand_damage', 'blaze_powder'), ('wand_cooldown', 'amethyst_shard')):
        definition = mapping['items'][f'minecraft:{material}'][0]
        assert definition['model'] == f'advance_magic:{name}'
        assert 'predicate' not in definition
        assert z.read(atlas[definition['bedrock_options']['icon']]['textures'] + '.png') == (ROOT / f'art/upgrades/{name}.png').read_bytes()
    for definition, (name, _, _) in zip(mapping['items']['minecraft:heart_of_the_sea'], catalog):
        assert definition['model'] == f'advance_magic:core_{name}'
        assert 'predicate' not in definition
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
        for name in (*hashes, 'geyser-mappings.json', 'pack-hashes.json', 'wand-preview.html', 'advance-magic-guide-th.png'):
            assert jar.read('resource-packs/' + name) == (dist / name).read_bytes(), f'Stale/missing embedded asset: {name}'
        assert not any('IntegrationChecks' in name or 'AccountingChecks' in name or name.startswith('net/minecraft/') for name in jar.namelist())
    assert (ROOT / 'target/advance-magic.jar').read_bytes() == (ROOT.parent / 'dist/advance-magic.jar').read_bytes()
print('PASS: wand/core/upgrade textures, Java models, Geyser model mappings, PNG CRCs, archives, hashes and embedded assets')
