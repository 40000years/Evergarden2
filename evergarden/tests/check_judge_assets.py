"""Validate client-space geometry, texture routing and weakpoint placement of actual assets."""
import json
import math
from pathlib import Path
import struct
import zipfile
import io
from itertools import product
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
        attachment=json.loads(bedrock.read(f'attachables/{name}.json'))['minecraft:attachable']['description']
        check(attachment['identifier']=='voidscape:'+name,'helmet attachment matches mapped item')
        check(attachment['materials']['default']=='armor','helmet uses Bedrock armor material')
        check(attachment['geometry']['default']==geo['description']['identifier'],'helmet attachment references authored geometry')
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
    # This preview must be a separate item model, not a replacement witch skin.
    skin=java.read('assets/voidscape/textures/item/judge_seraph.png')
    check(skin==bedrock.read('textures/items/judge_seraph.png'),'Seraph painted atlas is identical across clients')
    atlas=Image.open(io.BytesIO(skin)).convert('RGBA')
    check(atlas.size==(1024,1024),'Seraph face, gown and feathers use the detailed 1024px atlas')
    check({i for i,n in enumerate(atlas.getchannel('A').histogram()) if n}=={0,255},'cut-out planes use binary alpha on Java and Bedrock')
    mappings=json.loads((ROOT/'dist/geyser-mappings.json').read_text())
    helmets=mappings['items']['minecraft:iron_helmet']
    broad={0:{'west','east'},1:{'up','down'},2:{'north','south'}}
    poses={}
    for pose in ('idle','cast'):
        name='judge_seraph_'+pose
        model=json.loads(java.read(f'assets/voidscape/models/item/{name}.json'))
        geo=json.loads(bedrock.read(f'models/entity/{name}.geo.json'))['minecraft:geometry'][0]
        attach=json.loads(bedrock.read(f'attachables/{name}.json'))['minecraft:attachable']['description']
        poses[pose]=model['elements']
        check(json.loads(java.read(f'assets/voidscape/items/{name}.json'))['model']['model']=='voidscape:item/'+name,'Seraph Java model is opt-in')
        check(attach['identifier']=='voidscape:'+name and attach['materials']['default']=='armor','Seraph Bedrock helmet has a unique namespace')
        check(attach['render_controllers']==['controller.render.evergarden_judge_glow'],'Bedrock ignores world shading like the flat-lit Java model')
        check(any(m.get('bedrock_identifier')=='voidscape:'+name for m in helmets),'Geyser maps only the preview helmet model')
        check(len(model['elements'])==len(geo['bones'][0]['cubes']) and 40<len(model['elements'])<=256,'gown, hair and wing geometry survives both pack builds within the client mesh budget')
        check(all(e.get('shade') is False for e in model['elements']),'painted shading replaces directional face shading')
        check(all(all(-16<=a<=b<=32 for a,b in zip(e['from'],e['to'])) for e in model['elements']),'Seraph elements stay inside legal Java model space')
        planes=[e for e in model['elements'] if any(a==b for a,b in zip(e['from'],e['to']))]
        check(len(planes)==7 and all(sum(a==b for a,b in zip(e['from'],e['to']))==1 for e in planes),'six painted wing layers and the halo are single planes')
        for e in planes:
            flat=[a==b for a,b in zip(e['from'],e['to'])].index(True)
            check(set(e['faces'])==broad[flat],'a plane draws only its two broad faces, so both sides are painted')
        wings=[e for e in planes if e['from'][2]==e['to'][2]]
        for e in wings:
            mirror=[o for o in wings if o is not e and o['from'][2]==e['from'][2] and abs(o['from'][0]-8+(e['to'][0]-8))<1e-4]
            check(len(mirror)==1 and mirror[0]['faces']['north']['uv']==[e['faces']['north']['uv'][i] for i in (2,1,0,3)],'opposite wings share one painting with mirrored UVs')
        xs=[n for e in model['elements'] for n in (e['from'][0],e['to'][0])]
        check(max(xs)-min(xs)>40,'Seraph wings span more than four body widths')
        for element,cube in zip(model['elements'],geo['bones'][0]['cubes']):
            java_from=[element['from'][0]-8,element['from'][1]+12,element['from'][2]-8]
            bedrock_from=[-(cube['origin'][0]+cube['size'][0])/1.6,(cube['origin'][1]-24)/1.6+20,cube['origin'][2]/1.6]
            check(all(abs(a-b)<1e-4 for a,b in zip(java_from,bedrock_from)),'Seraph origins match after the Bedrock X-axis conversion')
            check(all(abs((element['to'][i]-element['from'][i])*1.6-cube['size'][i])<1e-4 for i in range(3)),'Seraph dimensions match at the actual display scale')
            check(set(element['faces'])==set(cube['uv']),'the same faces are drawn on both clients')
            rotation=element.get('rotation',{'angle':0,'axis':'z','origin':[8,8,8]})
            angle=rotation['angle'];pivot=rotation['origin']
            if angle:
                check(rotation['axis'] in ('x','y','z') and angle in (-45,-22.5,22.5,45),'element angles stay legal on Java')
                # Blockbench's Bedrock exporter mirrors X: X and Y angles flip sign, Z is retained.
                check(cube['rotation']=={'x':[-angle,0,0],'y':[0,-angle,0],'z':[0,0,angle]}[rotation['axis']],'Bedrock rotation mirrors the Java angle')
                expected=[-(pivot[0]-8)*1.6,(pivot[1]-8)*1.6+24,(pivot[2]-8)*1.6]
                check(all(abs(a-b)<1e-4 for a,b in zip(expected,cube['pivot'])),'rotated parts use the same physical pivot on both clients')
            c,s=math.cos(math.radians(angle)),math.sin(math.radians(angle))
            turn={'x':[[1,0,0],[0,c,-s],[0,s,c]],'y':[[c,0,s],[0,1,0],[-s,0,c]],'z':[[c,-s,0],[s,c,0],[0,0,1]]}[rotation['axis']]
            for corner in product(*[(element['from'][i],element['to'][i]) for i in range(3)]):
                d=[corner[i]-pivot[i] for i in range(3)]
                point=[pivot[i]+sum(turn[i][j]*d[j] for j in range(3)) for i in range(3)]
                world=[(point[0]-8)*.1,(point[1]+12)*.1,(point[2]-8)*.1]
                check(-3.5<=world[0]<=3.5 and 0<=world[1]<=7 and -3.5<=world[2]<=3.5,'rotated Seraph geometry stays inside the Java culling box')
            for face,data in element['faces'].items():
                uv=data['uv'];entry=cube['uv'][face]
                start=entry['uv'];span=entry['uv_size']
                if face in ('up','down'):
                    start=[start[i]+span[i] for i in range(2)];span=[-v for v in span]
                check(all(abs(start[i]-uv[i]*64)<1e-3 and abs(span[i]-(uv[i+2]-uv[i])*64)<1e-3 for i in range(2)),'painted texture crops retain the same UV on Java and Bedrock')
    shared=[e for e in poses['idle'] if e in poses['cast']]
    check(poses['idle']!=poses['cast'] and len(shared)>=40,'cast pose keeps gown, head and hair while raising the arms and lifting the wings')
print('PASS:',checks,'Judge geometry, UV, client parity and weakpoint checks')
