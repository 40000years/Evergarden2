"""Independently decode the delivered river rigs, not just their exporter metadata."""
import base64
import io
import json
from pathlib import Path
import sys
import zipfile
from PIL import Image

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
import preview_arkhaia_minions_v2 as source
import check_bedrock_facing as decoded
np=source.np
checks=0
with zipfile.ZipFile(ROOT/'evergarden/dist/evergarden-bedrock.mcpack') as pack,zipfile.ZipFile(ROOT/'evergarden/dist/judge-java.zip') as java:
    assert json.loads(pack.read('manifest.json'))['header']['version']>=[3,35,0]
    for name in source.NAMES:
        model_id='arkhaia_'+name
        model=json.loads((ROOT/f'evergarden/src/main/resources/modelengine/rivers/{model_id}.bbmodel').read_text(encoding='utf8'))
        approved=json.loads((ROOT/f'previews/arkhaia-minions-v2/models/{name}.bbmodel').read_text(encoding='utf8'))
        assert model['elements']==approved['elements'],'reviewed geometry changed';checks+=1
        entity=json.loads(pack.read(f'entity/{model_id}_native.entity.json'))['minecraft:client_entity']['description']
        assert entity['identifier']=='voidscape:'+model_id;checks+=1
        geometry=json.loads(pack.read(f'models/entity/{model_id}_native.geo.json'))['minecraft:geometry'][0]
        assert entity['geometry']['default']==geometry['description']['identifier'];checks+=1
        assert geometry['description']['visible_bounds_width']==28 and geometry['description']['visible_bounds_height']==28;checks+=1
        texture=base64.b64decode(model['textures'][0]['source'].split(',')[1]);assert pack.read(f'textures/entity/{model_id}_native.png')==texture;checks+=1
        png=(ROOT/f'evergarden/art/arkhaia-rivers/{name}.png').read_bytes()
        assert pack.read(f'textures/particle/river_{name}.png')==java.read(f'assets/voidscape/textures/effect/river_{name}.png')==png;checks+=1
        assert Image.open(io.BytesIO(png)).getchannel('A').getextrema()==(0,255);checks+=1
        billboard=json.loads(pack.read(f'particles/river_{name}_flat.particle.json'))['particle_effect']['components']['minecraft:particle_appearance_billboard']['uv']
        assert billboard['texture_width']==768 and billboard['uv_size']==[768,768];checks+=1
        clips=json.loads(pack.read(f'animations/{model_id}_native.animation.json'))['animations']
        assert len(clips)==len(model['animations'])+1 and all(a in clips for a in entity['animations'].values() if a.startswith('animation.'));checks+=1
        # Decode Bedrock's own channels and mirror its codec coordinates back to authoring space.
        decoded.bones={b['name']:b for b in geometry['bones']}
        decoded.clips={k.replace(model_id,'arkhaia'):v for k,v in clips.items()}
        decoded.point=lambda p:np.array([-p[0],p[1],p[2]])/4
        source.load(model,Image.open(io.BytesIO(texture)).convert('RGBA'))
        for clip in model['animations']:
            for t in np.linspace(0,clip['length'],5):
                actual=decoded.transforms(clip['name'],float(t),(0,0));expected=source.matrices(model,clip['name'],float(t),(0,0))
                for bone,matrix in expected.items():
                    if bone=='hitbox':continue
                    assert np.allclose(actual['gaze' if bone=='hi_gaze' else bone],matrix,atol=1e-7),name+'/'+clip['name']+'/'+bone;checks+=1
        for yaw,pitch in [(-45,30),(45,30),(0,-20)]:
            actual=decoded.transforms('idle',0,(yaw,pitch));expected=source.matrices(model,'idle',0,(yaw,pitch))
            assert np.allclose(actual['head'],expected['head'],atol=1e-7);checks+=1
        # Check arbitrary-axis beam rotations and local cube geometry, including fingers/handles.
        elements={e['uuid']:e for e in model['elements']}
        def visit(g):
            global checks
            if g['name']=='hitbox':return
            b=decoded.bones['gaze' if g['name']=='hi_gaze' else g['name']]
            cubes=[elements[c] for c in g['children'] if isinstance(c,str)]
            assert len(cubes)==len(b['cubes'])
            for original,cube in zip(cubes,b['cubes']):
                origin=np.array(cube['origin'])/4;size=np.array(cube['size'])/4;lo=[-origin[0]-size[0],origin[1],origin[2]]
                assert np.allclose(lo,original['from']) and np.allclose(size,np.array(original['to'])-original['from']);checks+=1
                angles=np.array(cube.get('rotation',[0,0,0]))*[-1,-1,1]
                assert np.allclose(source.rig.rotation(angles),source.rig.rotation(original.get('rotation',[0,0,0])));checks+=1
            for c in g['children']:
                if isinstance(c,dict):visit(c)
        for g in model['outliner']:visit(g)
print(f'PASS {checks} river geometry, full pose/gaze parity, arbitrary-axis cubes, textures and personal seals')
