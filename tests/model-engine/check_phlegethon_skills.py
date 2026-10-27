"""Check authored limbs, held boulder clearance and per-model Bedrock indexes."""
import json
from pathlib import Path
import sys
import zipfile
import numpy as np

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
import preview_arkhaia_minions_v2 as rig
from update_river_choreography import groups
from check_river_body_clearance import inspect,intersects,obb

model=json.loads((ROOT/'evergarden/src/main/resources/modelengine/rivers/arkhaia_phlegethon.bbmodel').read_text())
bones=groups(model);study=rig.Study(1).sculpt()
cores=[p for p in study.parts if p['bone'] in ('chest','head') and p['label']==p['bone'] and p['size'][2]>=4]
checks=0
for clip,end in [('throw_windup',1),('throw_active',.2)]:
    for t in np.linspace(0,end,81):
        matrices=rig.matrices(model,clip,float(t))
        hands=[(matrices['hand_'+side]@np.append(bones['hand_'+side]['origin'],1))[:3] for side in ('r','l')]
        rock=(sum(hands)/2,np.eye(3),np.array([3.6]*3)) # 1.8-block display at scale four
        assert 1.99<=np.linalg.norm(hands[1]-hands[0])*.25<=2.51,'palms lose boulder grip';checks+=1
        for body in cores:
            assert not intersects(rock,obb(body,matrices[body['bone']])),(clip,float(t),'boulder crosses',body['bone'])
            checks+=1
result=inspect('phlegethon');assert result['collisions']==0,result
with zipfile.ZipFile(ROOT/'evergarden/dist/evergarden-bedrock.mcpack') as pack:
    controller=json.loads(pack.read('animation_controllers/arkhaia_phlegethon_native.controller.json'))['animation_controllers']
    states=next(iter(controller.values()))['states']
    for index,animation in enumerate(model['animations']):
        for other,rule in [(k,v) for transition in states[animation['name']]['transitions'] for k,v in transition.items()]:
            expected=next(i for i,a in enumerate(model['animations']) if a['name']==other)
            assert rule==f"q.property('voidscape:clip') == {expected}";checks+=1
out=ROOT/'previews/phlegethon-skills';out.mkdir(exist_ok=True)
(out/'clearance.json').write_text(json.dumps({'limbs':result,'held-rock-and-protocol-checks':checks},indent=2))
print(f'PASS {checks} held-boulder grip, torso/head clearance and Bedrock clip-index checks; {result["checks"]} limb checks')
