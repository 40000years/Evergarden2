"""Validate client-space geometry, texture routing and weakpoint placement of actual assets."""
import json
import math
from pathlib import Path
import struct
import zipfile
import io
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
shape=json.loads((ROOT/'src/main/resources/judge-shape.yml').read_text())
checks=0
def check(value,label):
    global checks
    assert value,label
    checks+=1
with zipfile.ZipFile(ROOT/'dist/judge-java.zip') as java,zipfile.ZipFile(ROOT/'dist/evergarden-bedrock.mcpack') as bedrock:
    check(java.testzip() is None and bedrock.testzip() is None,'archive integrity')
    check(struct.unpack('>II',java.read('assets/voidscape/textures/item/judge_atlas.png')[16:24])==(1024,1024),'high resolution atlas')
    check(java.read('assets/voidscape/textures/item/judge_atlas.png')==bedrock.read('textures/items/judge_atlas.png'),'shared client artwork')
    for tint in ['cyan','violet','amber','red']:
        particle=json.loads(bedrock.read(f'particles/judge_{tint}.particle.json'))['particle_effect']
        check(particle['description']['identifier']=='voidscape:judge_'+tint,'owned particle namespace')
        check(particle['description']['basic_render_parameters']['texture']+'.png' in bedrock.namelist(),'original glint reference')
    for name in ['judge_seal','judge_orbit','judge_sanctuary','judge_lane','judge_ray']:
        png=java.read(f'assets/voidscape/textures/effect/{name}.png')
        check(png==bedrock.read(f'textures/particle/{name}.png'),'spell texture parity')
        im=Image.open(io.BytesIO(png)).convert('RGBA')
        check(im.size==(1024,1024) and im.getextrema()[3][0]==0 and im.getextrema()[3][1]==255,'high resolution transparent spell planes')
        model=json.loads(java.read(f'assets/voidscape/models/effect/{name}.json'))
        check(set(model['elements'][0]['faces'])=={'north','south'} and not model['elements'][0]['shade'],'unshaded double-sided seals')
        check(json.loads(java.read(f'assets/voidscape/items/{name}.json'))['model']['model']=='voidscape:effect/'+name,'direct spell model routing')
        for flat in [False,True]:
            particle=json.loads(bedrock.read(f'particles/{name}{"_flat" if flat else ""}.particle.json'))['particle_effect']
            components=particle['components'];billboard=components['minecraft:particle_appearance_billboard']
            check(particle['description']['basic_render_parameters']['material']=='particles_blend','translucent Bedrock strokes visible from either side')
            check(billboard['facing_camera_mode']==('emitter_transform_xz' if flat else 'direction_z'),'stable world-space plane basis')
            check(components['minecraft:particle_lifetime_expression']['max_lifetime']==.20,'four-tick refresh does not leave duplicate circles')
    for name,mesh in shape.items():
        model=json.loads(java.read(f'assets/voidscape/models/item/{name}.json'))
        geo=json.loads(bedrock.read(f'models/entity/{name}.geo.json'))['minecraft:geometry'][0]
        definition=json.loads(java.read(f'assets/voidscape/items/{name}.json'))
        check(definition['model']['model']=='voidscape:item/'+name,'direct Java routing')
        check(geo['bones'][0]['name']=='head' and geo['bones'][0]['pivot']==[0,24,0],'Bedrock helmet anchor')
        check(len(model['elements'])==len(geo['bones'][0]['cubes'])==len(mesh['cubes']),'mesh count parity')
        bounds=mesh.get('bounds',{});java_width=bounds.get('java-width',36);java_height=bounds.get('java-height',36)
        desc=geo['description'];offset=desc['visible_bounds_offset']
        for authored,j,b in zip(mesh['cubes'],model['elements'],geo['bones'][0]['cubes']):
            for axis in range(3):
                check(-16<=j['from'][axis]<j['to'][axis]<=32,'Java legal element coordinates')
                actual_j=(j['from'][axis]-8)*mesh['scale']/16+mesh['center'][axis]
                actual_b=b['origin'][axis]/16+(mesh['center'][1]-1.5 if axis==1 else 0)
                check(abs(actual_j-authored['from'][axis])<1e-4 and abs(actual_b-actual_j)<1e-4,'world geometry parity at actual display scales')
                check(abs((j['to'][axis]-j['from'][axis])*mesh['scale']/16-b['size'][axis]/16)<1e-4,'world dimension parity')
                low=0 if axis==1 else -java_width/2;high=java_height if axis==1 else java_width/2
                check(low<=authored['from'][axis] and authored['from'][axis]+authored['size'][axis]<=high,f'{name}: Java culling contains axis {axis}')
                span=desc['visible_bounds_height'] if axis==1 else desc['visible_bounds_width']
                check(offset[axis]-span/2<=b['origin'][axis]/16 and (b['origin'][axis]+b['size'][axis])/16<=offset[axis]+span/2,f'{name}: Bedrock culling contains axis {axis}')
            check(all(0<=v<=16 for f in j['faces'].values() for v in f['uv']),'UV inside atlas')
        check(geo['description']['visible_bounds_width']>=32,'large Bedrock culling bounds')
    # The palm gem and the entire attackable lower palm lie inside the real 20-size slime.
    for name in ['judge_left','judge_right']:
        for c in shape[name+'_light']['cubes']:
            p=c['from'];s=c['size']
            check(p[0]>=-5.2 and p[0]+s[0]<=5.2 and p[1]>=0 and p[1]+s[1]<=10.4 and p[2]>=-5.2 and p[2]+s[2]<=5.2,'palm weakpoint within real hitbox')
    c=shape['judge_heart']['center']
    check(math.isclose(c[1],.52*8/2),'heart centered inside exposed core')
print('PASS:',checks,'Judge geometry, UV, client parity and weakpoint checks')
