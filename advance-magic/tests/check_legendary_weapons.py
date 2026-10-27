"""Check cross-client geometry parity, routing, animation and model dependencies."""
import base64
import io
import json
import math
import itertools
import numpy as np
from pathlib import Path
import sys
import zipfile
from PIL import Image

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
from legendary_weapon_assets import IDS,make_weapon
from bedrock_particle_checks import check_direction_modes

dist=ROOT/'advance-magic/dist'
mapping=json.loads((dist/'geyser-mappings.json').read_text())
rows={row['model']:row for values in mapping['items'].values() for row in values}
checks=0
def check(value,label):
    global checks
    assert value,label
    checks+=1

with zipfile.ZipFile(dist/'advance-magic-java.zip') as java,zipfile.ZipFile(dist/'advance-magic-bedrock.mcpack') as bedrock:
    def j(pack,path):return json.loads(pack.read(path))
    item_atlas=j(bedrock,'textures/item_texture.json')['texture_data']
    animations=j(bedrock,'animations/legendary_weapons.animation.json')['animations']
    controllers=j(bedrock,'render_controllers/legendary_weapons.render.json')['render_controllers']
    for name in IDS:
        source=make_weapon(name)
        item=j(java,f'assets/advance_magic/items/legendary_{name}.json')['model']
        check(item['property']=='minecraft:display_context' and item['cases'][0]['when']=='gui','GUI model routing '+name)
        fallback=item['fallback']
        check(fallback['model']==f'advance_magic:item/legendary_weapons/{name}','held 3D routing '+name)
        model=j(java,f'assets/advance_magic/models/item/legendary_weapons/{name}.json')
        check(len(model['elements'])==len(source.cubes)+3,'3D body + front/back/side VFX '+name)
        check(all(-16<=v<=32 for e in model['elements'] for p in ('from','to') for v in e[p]),'Java element bounds '+name)
        check(all(e['light_emission']==15 for e in model['elements'][-3:]),'emissive held FX '+name)
        for texture in model['textures'].values():check(bool(java.read('assets/'+texture.replace(':','/textures/',1)+'.png')),'resolved Java texture '+texture)
        base='assets/advance_magic/textures/item/legendary_weapons/'+name
        for kind in ('fx','icon'):
            strip=Image.open(io.BytesIO(java.read(base+'_'+kind+'.png'))).convert('RGBA')
            check(strip.size==(128,1024),'8 square frames '+name+kind)
            check(j(java,base+'_'+kind+'.png.mcmeta')['animation']['frametime']==2,'10fps playback '+name+kind)
            frames=[strip.crop((0,f*128,128,(f+1)*128)) for f in range(8)]
            check(len({f.tobytes() for f in frames})>=4,'VFX visibly changes '+name+kind)
            check(all(f.getchannel('A').getextrema()==(0,255) for f in frames),'transparent/opaque cutout '+name+kind)
            check(all(f.getbbox()[0]>0 and f.getbbox()[2]<128 for f in frames),'unclipped icon/FX border '+name+kind)
        row=rows[f'advance_magic:legendary_{name}']
        attach=j(bedrock,f'attachables/legendary_weapons/{name}.json')['minecraft:attachable']['description']
        check(row['bedrock_identifier']==attach['identifier'],'Geyser attachable link '+name)
        check(row['bedrock_options']['allow_offhand'],'both hands '+name)
        check(bedrock.read(item_atlas[row['bedrock_options']['icon']]['textures']+'.png')==
            (ROOT/f'advance-magic/art/legendary-weapons/{name}_icon.png').read_bytes(),'Bedrock icon with signature FX '+name)
        geo=j(bedrock,f'models/entity/legendary_weapons/{name}.geo.json')['minecraft:geometry'][0]
        cubes=[c for b in geo['bones'] for c in b.get('cubes',[])]
        check(len(cubes)==len(source.cubes),'body cube parity '+name)
        source_boxes=sorted(tuple(round(v,4) for v in c['from']+c['to']) for c in source.cubes)
        converted=[]
        for c in cubes:
            ox,oy,oz=c['origin'];sx,sy,sz=c['size']
            converted.append(tuple(round(v,4) for v in (8-ox-sx,oy,oz+8,8-ox,oy+sy,oz+8+sz)))
        check(sorted(converted)==source_boxes,'exact shared body geometry '+name)
        check(geo['bones'][0]['binding']=='q.item_slot_to_bone_name(context.item_slot)','native slot binding '+name)
        for key,clip in attach['animations'].items():check(clip in animations,'resolved animation '+clip)
        for controller in attach['render_controllers']:check(controller in controllers,'resolved render controller '+controller)
        for tex in attach['textures'].values():check(bool(bedrock.read(tex+'.png')),'resolved attachable texture '+tex)
        fx=controllers[f'controller.render.advance_magic.legendary_{name}_fx']
        check(len(fx['arrays']['textures']['Array.arcs'])==8,'Bedrock 8-frame playback '+name)
        check('q.life_time * 10' in fx['textures'][0],'native clock '+name)
        for suffix in ('','_flat'):
            particle=j(bedrock,f'particles/legendary_{name}_sigil{suffix}.particle.json')['particle_effect']
            check(particle['description']['identifier']==f'advance_magic:legendary_{name}_sigil{suffix}','equip circle bridge '+name+suffix)
        bb=json.loads((ROOT/f'advance-magic/art/legendary-weapons/{name}.bbmodel').read_text())
        check(len([e for e in bb['elements'] if e['name']!='hitbox'])==len(source.cubes),'editable visual model parity '+name)
        check(bb['animations'][0]['name']=='idle' and bb['animations'][0]['loop']=='loop','editable idle clip '+name)
        check(Image.open(io.BytesIO(base64.b64decode(bb['textures'][0]['source'].split(',')[1]))).size==(128,128),'embedded model texture '+name)
    check(rows['advance_magic:legendary_mjolnir'] in mapping['items']['minecraft:mace'],'hammer uses native mace mapping')
    check(not any(r['model']=='advance_magic:legendary_mjolnir' for r in mapping['items'].get('minecraft:netherite_axe',[])),'old axe mapping removed')
    check(rows['advance_magic:legendary_longinus'] in mapping['items']['minecraft:netherite_sword'],'Longinus uses nonthrowable melee base')
    check('components' not in rows['advance_magic:legendary_longinus'],'Longinus has no consumable charge')
    attach=j(bedrock,'attachables/legendary_weapons/longinus.json')['minecraft:attachable']['description']
    check('raise_first' not in attach['animations'] and 'pre_animation' not in attach['scripts'],'Longinus has no throwing animation')
    check(rows['advance_magic:legendary_vajra'] in mapping['items']['minecraft:netherite_sword'],'Vajra accepts native sword enchantments')
    check(bool(java.read('assets/advance_magic/items/legendary_longinus_rain.json')),'authored rain spear Java model')
    for suffix in ('','_flat'):
        rain=j(bedrock,f'particles/legendary_longinus_rain{suffix}.particle.json')['particle_effect']
        check(rain['description']['identifier']==f'advance_magic:legendary_longinus_rain{suffix}','native Bedrock rain spear')
        check(bool(bedrock.read(rain['description']['basic_render_parameters']['texture']+'.png')),'rain spear texture resolves')
    check_direction_modes(dist/'advance-magic-bedrock.mcpack')
print('PASS',checks,'legendary weapon asset checks')
