"""Regress detached full-screen Bedrock FX while preserving native hand binding.

Reads actual packaged geometry/controllers/animations, independently of the asset
generator. Projection is the established approximate screenshot calibration,
not an actual Minecraft client render.
"""
import itertools
import json
import math
from pathlib import Path
import re
import zipfile

import numpy as np
from PIL import Image
import io

ROOT=Path(__file__).resolve().parents[2]
IDS=('fire','wind','earth','ice','water','lightning','light','dark')
P=np.array([[13,-3],[-11.68,-18.42],[2.5,1.5]])
checks=0


def check(ok,label):
    global checks
    assert ok,label
    checks+=1


def document(pack,path):return json.loads(pack.read(path))


def project(points,grip,scale,lift):
    p=np.asarray(points,dtype=float).copy();ratio=scale/.48
    p[:,0]=8+ratio*(p[:,0]-8)
    p[:,1]=7.5+ratio*(p[:,1]-grip)+lift/.48
    p[:,2]=8+ratio*(p[:,2]-8)
    return (p-[8,31.25,8])@P+[813,197]


metadata=json.loads((ROOT/'advance-magic/art/elemental-staffs/metadata.json').read_text())
records={record['id']:record for record in metadata['items']}
with zipfile.ZipFile(ROOT/'advance-magic/dist/advance-magic-bedrock.mcpack') as pack:
    animations=document(pack,'animations/elemental_staffs.animation.json')['animations']
    controllers=document(pack,'render_controllers/elemental_staffs.render.json')['render_controllers']
    atlas=Image.open(io.BytesIO(pack.read('textures/elemental_staffs/materials.png'))).convert('RGBA')
    check(not any(re.search(r'textures/elemental_staffs/[^/]+_fx_\d+\.png$',name) for name in pack.namelist()),'No legacy Bedrock fullstaff effect textures in shipped pack')
    for name in IDS:
        label=name+' '
        grip=records[name]['grip'][1]
        attach=document(pack,f'attachables/elemental_staffs/{name}.json')['minecraft:attachable']['description']
        check(attach['textures']=={'default':'textures/elemental_staffs/materials'},label+'only opaque atlas is bound')
        check(attach['materials']['default']=='entity_alphatest',label+'effect material has ordinary cutout-alpha semantics')
        body=document(pack,f'models/entity/elemental_staffs/{name}.geo.json')['minecraft:geometry'][0]
        effects=document(pack,f'models/entity/elemental_staffs/{name}_fx.geo.json')['minecraft:geometry'][0]
        check(effects['bones'][:2]==body['bones'][:2],label+'effects inherit the exact physical native hand frame')
        bones=effects['bones'][2:]
        check([bone['name'] for bone in bones]==['glint_1','glint_2','glint_3'],label+'exactly three small world motes')
        ctrl=controllers[f'controller.render.advance_magic.staff_{name}_fx']
        check(ctrl['geometry']=='Geometry.fx' and ctrl['materials']==[{'*':'Material.default'}],label+'solid effect controller uses the alpha-test material')
        check(ctrl['ignore_lighting'] is True and ctrl['textures']==['Texture.default'],label+'fullbright solids without alpha inversion or animation sheets')
        check(ctrl['rebuild_animation_matrices'] is True,label+'secondary geometry rebuilds its own animated hand and mote matrices')
        check('arrays' not in ctrl,label+'no frame-sheet array remains')
        visibility={k:v for row in ctrl['part_visibility'] for k,v in row.items()}
        check(visibility=={'glint_1':'1.0','glint_2':'1.0','glint_3':'context.is_first_person == 0.0'},label+'first person renders precisely two motes')
        idle=animations[f'animation.advance_magic.staff_{name}.idle']['bones']
        check('aura' not in idle,label+'obsolete sheet animation is absent')
        first_points=[];all_points=[]
        for bone in bones:
            check(bone['parent']=='grip' and len(bone['cubes'])==1,label+bone['name']+' is one locally bound solid')
            cube=bone['cubes'][0];origin,size=cube['origin'],cube['size']
            check(all(.15<=d<=.4 for d in size),label+bone['name']+' has real bounded thickness, not a plane')
            clip=idle[bone['name']]
            check(set(clip)=={'position'} and clip['position'][0]==clip['position'][2]==0,label+bone['name']+' cannot rotate or scale into the viewport')
            check(bool(re.fullmatch(r'math\.sin\(q\.life_time \* 90 \+ (0|120|240)\) \* 0\.10',clip['position'][1])),label+bone['name']+' vertical bob is bounded to one tenth of a model pixel')
            points=[]
            for x,y,z in itertools.product(*[(origin[i],origin[i]+size[i]) for i in range(3)]):
                for bob in (-.1,.1):points.append([8-x,y-12.5+grip+bob,z+8])
            lo=np.min(points,0);hi=np.max(points,0)
            check(np.all(lo>=[4,21,5]) and np.all(hi<=[12,30,11]),label+bone['name']+' entire animated volume stays within the crown envelope')
            all_points.extend(points)
            if bone['name']!='glint_3':first_points.extend(points)
            for face in cube['uv'].values():
                u,v=map(int,face['uv']);su,sv=map(int,face['uv_size'])
                check(atlas.crop((u,v,u+su,v+sv)).getchannel('A').getextrema()==(255,255),label+'mote samples only opaque light/core palette pixels')
        first=animations[f'animation.advance_magic.staff_{name}.first']['bones']['grip']
        xy=project(first_points,grip,first['scale'][0],first['position'][1]);lo,hi=xy.min(0),xy.max(0)
        check(650<lo[0]<hi[0]<1100 and 40<lo[1]<hi[1]<300,label+'complete animated first-person FX stays in the right-hand crown region')
        check(hi[0]-lo[0]<150 and hi[1]-lo[1]<150,label+'effect envelope cannot become a screen-sized card')
        print(name,'approximate animated first-person effect bounds',lo.round(1).tolist(),hi.round(1).tolist())
print('PASS',checks,'Bedrock solid-mote, bounded-motion, material, visibility and approximate first-person safety checks')
