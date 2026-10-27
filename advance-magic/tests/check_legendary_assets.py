"""Verify the legendary artwork and item routes survive both packs and release JARs."""
import argparse
from contextlib import ExitStack
import hashlib
import io
import json
from pathlib import Path
import runpy
import struct
import sys
from xml.etree import ElementTree as ET
import zipfile

from PIL import Image

MODULE=Path(__file__).resolve().parents[1]
ROOT=MODULE.parent
sys.path.insert(0,str(ROOT/'tools'))
from legendary_effect_assets import ART, PLANES, RAYS

NEW_WANDS=('verdant_sovereign','lunar_requiem','crimson_oblivion')
TINTS={'emerald':0x58FFB1,'jade':0x12B76A,'silver':0xD8F3FF,'crimson':0xFF315B,'ruby':0xA41B52}


def document(archive,path):
    return json.loads(archive.read(path))


def png(data,size,min_alpha=248):
    assert data[:8]==b'\x89PNG\r\n\x1a\n', 'Expected native PNG'
    w,h,depth,color,*_=struct.unpack('>IIBBBBB',data[16:29])
    assert (w,h,depth,color)==(size,size,8,6), 'Expected RGBA at the authored resolution'
    with Image.open(io.BytesIO(data)) as image:
        image.load()
        assert image.mode=='RGBA'
        alpha=image.getchannel('A')
        low,high=alpha.getextrema()
        assert low==0 and high>=min_alpha, f'Require transparent background and bright visible art; alpha range={low,high}, minimum bright alpha={min_alpha}'


def verdant_ray_profile(data):
    # The narrow discharge deliberately caps opacity at .94 so crossed beam
    # faces retain transparent shoulders. Its hot axis must remain bright and
    # continuous along the full length, including both model endpoints.
    with Image.open(io.BytesIO(data)) as image:
        alpha=image.getchannel('A')
        for y in range(image.height):
            assert min(alpha.getpixel((127,y)),alpha.getpixel((128,y)))>=236, 'Verdant beam axis must remain continuously bright'
            assert max(alpha.getpixel((96,y)),alpha.getpixel((159,y)))<=64, 'Verdant beam shoulders must remain translucent beside the hot axis'
        assert sum(alpha.histogram()[180:])>=image.height*12, 'Verdant beam needs a visible core, not a single sparse bright pixel'


def effects(java,bedrock,garden_java,garden_bedrock):
    runtime='\n'.join(path.read_text(encoding='utf8') for path in (MODULE/'src/main/java').rglob('*.java'))
    assert '"lunar_crescent"' not in runtime, 'Retired crescent must not be called by a live spell'
    for archive in (java,bedrock):
        assert all('lunar_crescent' not in name for name in archive.namelist()), 'Retired Lunar artwork must not reach the client'
    for name in PLANES+RAYS:
        size=1024 if name in PLANES else 256
        source=(ART/f'{name}.png').read_bytes()
        png(source,size,min_alpha=236 if name=='verdant_ray' else 248)
        if name=='verdant_ray':
            verdant_ray_profile(source)
        svg=ET.parse(ART/f'{name}.svg').getroot()
        assert svg.tag=='{http://www.w3.org/2000/svg}svg'
        assert svg.attrib['viewBox']==f'0 0 {size} {size}'
        assert not list(svg.iter('{http://www.w3.org/2000/svg}image')), 'Editable vectors must retain authored geometry'
        texture=f'assets/advance_magic/textures/effect/{name}.png'
        assert java.read(texture)==source, f'Java texture mismatch: {name}'
        model_path=f'assets/advance_magic/models/effect/{name}.json'
        model=document(java,model_path)
        assert model_path not in garden_java.namelist()
        assert model['ambientocclusion'] is False and model['textures']['line']==f'advance_magic:effect/{name}'
        sides={'north','south'} if name in PLANES else {'north','south','east','west'}
        for element in model['elements']:
            assert element['shade'] is False and set(element['faces'])==sides
            assert all(face['texture']=='#line' for face in element['faces'].values())
        item_path=f'assets/advance_magic/items/{name}.json'
        item=document(java,item_path)
        assert item_path not in garden_java.namelist()
        assert item['model']=={'type':'minecraft:model','model':f'advance_magic:effect/{name}'}
        texture=f'textures/particle/{name}.png'
        assert bedrock.read(texture)==source, f'Bedrock texture mismatch: {name}'
        for suffix in ('','_flat'):
            path=f'particles/{name}{suffix}.particle.json'
            particle=document(bedrock,path)
            assert path not in garden_bedrock.namelist()
            effect=particle['particle_effect']
            assert effect['description']['identifier']==f'advance_magic:{name}{suffix}'
            assert effect['description']['basic_render_parameters']=={
                'material':'particles_blend','texture':f'textures/particle/{name}'}
            c=effect['components'];billboard=c['minecraft:particle_appearance_billboard']
            assert c['minecraft:emitter_rate_instant']['num_particles']==1
            assert c['minecraft:emitter_shape_point']['offset']==[
                'variable.line_offset_x','variable.line_offset_y','variable.line_offset_z']
            assert c['minecraft:particle_lifetime_expression']['max_lifetime']==.20, 'Four-tick frames require a continuous .20s ribbon/plane'
            assert c['minecraft:particle_initial_speed']==0
            flat=bool(suffix) and name in PLANES
            expected='emitter_transform_xz' if flat else 'direction_z' if name in PLANES else 'direction_y'
            assert billboard['facing_camera_mode']==expected
            assert billboard['size']==['variable.line_width * 0.5','variable.line_height * 0.5']
            if flat:
                assert 'direction' not in billboard, 'Horizontal seals need an explicit XZ basis'
            else:
                assert billboard['direction']=={'mode':'custom','custom_direction':[
                    'variable.line_normal_x','variable.line_normal_y','variable.line_normal_z']}, 'Use native custom enum and the original normal/axis'
            assert billboard['uv']=={'texture_width':size,'texture_height':size,'uv':[0,0],'uv_size':[size,size]}
            spin=c['minecraft:particle_initial_spin']
            assert spin=={'rotation':'variable.line_rotation' if name in PLANES else 0,'rotation_rate':0}
    # A lower- or higher-priority Evergarden pack must not hide Advance Magic's
    # original effect sprite registration, including the legacy beacon overlay.
    for archive in (java,):
        sources=document(archive,'assets/minecraft/atlases/items.json')['sources']
        assert {'type':'minecraft:directory','source':'effect','prefix':'effect/'} in sources
        assert any(s.get('sprite')=='minecraft:entity/beacon_beam' for s in sources)
    for tint,color in TINTS.items():
        path=f'particles/mythic_{tint}.particle.json'
        effect=document(bedrock,path)['particle_effect']
        assert path not in garden_bedrock.namelist()
        assert effect['description']['identifier']==f'advance_magic:mythic_{tint}'
        expected=[((color>>shift)&255)/255 for shift in (16,8,0)]+[1]
        assert effect['components']['minecraft:particle_appearance_tinting']['color']==expected
        texture=effect['description']['basic_render_parameters']['texture']+'.png'
        assert texture not in garden_bedrock.namelist()


def items(java,bedrock,garden_java,garden_bedrock):
    builder=runpy.run_path(str(MODULE/'tools/build_packs.py'))
    catalog=builder['spells']()
    assert len(catalog)==21
    mapping=json.loads((MODULE/'dist/geyser-mappings.json').read_text(encoding='utf8'))['items']
    wands=mapping['minecraft:carrot_on_a_stick'];cores=mapping['minecraft:heart_of_the_sea']
    assert len(wands)==8 and len(cores)==21
    assert {row['model'] for row in wands}=={f'advance_magic:staff_{name}' for name in builder['STAFF_IDS']}
    assert {row['model'] for row in cores}=={f'advance_magic:core_{name}' for name,_,_ in catalog}
    atlas=document(bedrock,'textures/item_texture.json')['texture_data']
    garden_atlas=document(garden_bedrock,'textures/item_texture.json')['texture_data']
    garden_mapping=json.loads((ROOT/'evergarden/dist/geyser-mappings.json').read_text(encoding='utf8'))['items']
    retired_item_ids={f'advance_magic:{name}' for name,_,_ in catalog}
    staff_item_ids={f'advance_magic:staff_{name}' for name in builder['STAFF_IDS']}
    assert not any(row['model'] in retired_item_ids|staff_item_ids for rows in garden_mapping.values() for row in rows), 'Evergarden must not register duplicate magic equipment'
    for name,_,_ in catalog:
        assert f'assets/advance_magic/models/item/{name}.json' not in garden_java.namelist()
        assert f'assets/advance_magic/textures/item/{name}.png' not in garden_java.namelist()
        assert f'textures/items/{name}.png' not in garden_bedrock.namelist()
        assert f'advance_magic.{name}' not in garden_atlas
    assert not any('elemental_staffs/' in path for path in garden_java.namelist()+garden_bedrock.namelist()), 'Staff art is owned by Advance Magic'
    for name,_,_ in catalog:
        asset='core_'+name;source=(MODULE/f'art/cores/{asset}.png').read_bytes();png(source,128)
        item=document(java,f'assets/advance_magic/items/{asset}.json')
        assert item['model']=={'type':'minecraft:model','model':f'advance_magic:item/{asset}'}
        model=document(java,f'assets/advance_magic/models/item/{asset}.json')
        assert model['parent']=='minecraft:item/generated' and model['textures']['layer0']==f'advance_magic:item/{asset}'
        assert java.read(f'assets/advance_magic/textures/item/{asset}.png')==source
        row=next(d for d in cores if d['model']==f'advance_magic:{asset}')
        assert row['bedrock_identifier']==f'advance_magic:{asset}' and 'predicate' not in row
        key=row['bedrock_options']['icon']
        assert bedrock.read(atlas[key]['textures']+'.png')==source
        garden_texture=garden_bedrock.read(garden_atlas[key]['textures']+'.png')
        modern=name in builder['PRESERVED_WANDS']
        png(garden_texture,128 if modern else 32)
        assert document(garden_java,f'assets/advance_magic/items/{asset}.json')==item
        if name in NEW_WANDS:
            assert garden_texture==source
            assert garden_java.read(f'assets/advance_magic/textures/item/{asset}.png')==source
    # Keep every existing mythic/legendary texture and direct model reference
    # available, without resurrecting the retired per-spell equipment catalog.
    for name in builder['PRESERVED_WANDS']:
        source=(MODULE/f'art/wands/{name}.png').read_bytes();png(source,128)
        assert java.read(f'assets/advance_magic/textures/item/{name}.png')==source
        assert bedrock.read(f'textures/items/{name}.png')==source
        assert document(java,f'assets/advance_magic/items/{name}.json')['model']['model']==f'advance_magic:item/{name}'
        assert not any(row['model']==f'advance_magic:{name}' for rows in mapping.values() for row in rows)
    manifest=document(bedrock,'manifest.json')
    assert manifest['header']['version']==builder['BEDROCK_PACK_VERSION']
    assert all(module['version']==builder['BEDROCK_PACK_VERSION'] for module in manifest['modules'])
    assert document(java,'pack.mcmeta')['pack']['description']=='Advance Magic | 8 Elemental Staffs'



def release(assets_only):
    for module,name in ((MODULE,'advance-magic'),(ROOT/'evergarden','evergarden')):
        dist=module/'dist'
        hashes=json.loads((dist/'pack-hashes.json').read_text(encoding='utf8'))
        for filename,digest in hashes.items():
            data=(dist/filename).read_bytes()
            assert hashlib.sha1(data).hexdigest()==digest, f'Stale hash: {name}/{filename}'
            with zipfile.ZipFile(io.BytesIO(data)) as archive:
                assert archive.testzip() is None
                assert len(archive.namelist())==len(set(archive.namelist())), 'Duplicate archive entries'
        if assets_only:
            continue
        jar_path=ROOT/f'dist/{name}.jar'
        assert jar_path.read_bytes()==(module/f'target/{name}.jar').read_bytes(), f'Stale release JAR: {name}'
        with zipfile.ZipFile(jar_path) as jar:
            assert jar.testzip() is None
            for filename in (*hashes,'geyser-mappings.json','pack-hashes.json'):
                assert jar.read('resource-packs/'+filename)==(dist/filename).read_bytes(), f'Stale embedded asset: {name}/{filename}'


def main():
    parser=argparse.ArgumentParser(__doc__)
    parser.add_argument('--assets-only',action='store_true',help='Validate rebuilt packs before release JARs are copied')
    args=parser.parse_args()
    with ExitStack() as stack:
        packs=[stack.enter_context(zipfile.ZipFile(path)) for path in (
            MODULE/'dist/advance-magic-java.zip',MODULE/'dist/advance-magic-bedrock.mcpack',
            ROOT/'evergarden/dist/evergarden-java.zip',ROOT/'evergarden/dist/evergarden-bedrock.mcpack')]
        effects(*packs);items(*packs)
    release(args.assets_only)
    print('PASS: 13 live PNG/SVG effects, retired Lunar crescent excluded, Java/Bedrock art without duplicate Evergarden effects, native particle modes and .20s lifetimes, 5 tints, eight staff routes, 21 core routes, preserved legendary wand art, archive hashes'
          + (' (assets only)' if args.assets_only else ' and exact embedded release JARs'))


if __name__=='__main__':main()
