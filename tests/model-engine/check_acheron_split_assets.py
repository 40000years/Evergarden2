"""Independently validate echo geometry, animation scaling and Bedrock clip routing."""
import json,re,sys,zipfile
from pathlib import Path
import numpy as np
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
import preview_arkhaia_minions_v2 as rig
import check_bedrock_facing as decoded
folder=ROOT/'evergarden/src/main/resources/modelengine/rivers'
main=json.loads((folder/'arkhaia_acheron.bbmodel').read_text())
echo=json.loads((folder/'arkhaia_acheron_echo.bbmodel').read_text())
protocol=(ROOT/'evergarden/src/main/java/com/example/voidscape/boss/ArkhaiaBedrockSupport.java').read_text()
clips=re.findall('"([a-z_]+)"',re.search(r'RIVER_CLIPS=List.of\((.*?)\);',protocol).group(1))
assert [a['name'] for a in main['animations']]==[a['name'] for a in echo['animations']]==clips
checks=1
def groups(model):
    result={}
    def visit(g):
        result[g['name']]=g
        for c in g['children']:
            if isinstance(c,dict):visit(c)
    for g in model['outliner']:visit(g)
    return result
mg,eg=groups(main),groups(echo)
assert 'halo' in mg and 'halo' not in eg;checks+=1
for name,g in eg.items():
    assert np.allclose(g['origin'],np.array(mg[name]['origin'])*.6);checks+=1
original={e['uuid']:e for e in main['elements']}
for cube in echo['elements']:
    source=original[cube['uuid']]
    for key in ('from','to','origin'):
        if key in cube:assert np.allclose(cube[key],np.array(source[key])*.6);checks+=1
    assert cube['faces']==source['faces'];checks+=1
with zipfile.ZipFile(ROOT/'evergarden/dist/evergarden-bedrock.mcpack') as pack:
    assert json.loads(pack.read('manifest.json'))['header']['version']>=[3,33,0]
    entity=json.loads(pack.read('entity/arkhaia_acheron_echo_native.entity.json'))['minecraft:client_entity']['description']
    assert entity['identifier']=='voidscape:arkhaia_acheron_echo';checks+=2
    geometry=json.loads(pack.read('models/entity/arkhaia_acheron_echo_native.geo.json'))['minecraft:geometry'][0]
    assert not any(b['name']=='halo' for b in geometry['bones']);checks+=1
    decoded.bones={b['name']:b for b in geometry['bones']}
    animations=json.loads(pack.read('animations/arkhaia_acheron_echo_native.animation.json'))['animations']
    decoded.clips={k.replace('arkhaia_acheron_echo','arkhaia'):v for k,v in animations.items()}
    decoded.point=lambda p:np.array([-p[0],p[1],p[2]])/4
    controller=json.loads(pack.read('animation_controllers/arkhaia_acheron_echo_native.controller.json'))['animation_controllers']
    states=next(iter(controller.values()))['states'];assert list(states)==clips;checks+=1
    for index,name in enumerate(clips):
        for other,state in states.items():
            if other!=name:assert {name:f"q.property('voidscape:clip') == {index}"} in state['transitions'];checks+=1
    for clip in echo['animations']:
        for t in np.linspace(0,clip['length'],5):
            expected=rig.matrices(echo,clip['name'],float(t),(0,0));actual=decoded.transforms(clip['name'],float(t),(0,0))
            for bone,matrix in expected.items():
                if bone=='hitbox':continue
                assert np.allclose(actual['gaze' if bone=='hi_gaze' else bone],matrix,atol=1e-7),clip['name']+'/'+bone;checks+=1
print(f'PASS {checks} Acheron ritual, echo scale/halo removal and decoded Bedrock pose/index checks')
