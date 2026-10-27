"""Decode delivered Bedrock transforms and check physical forward/back directions.

Bedrock rotation import follows Blockbench's bedrock_animation codec (X/Y
inverted); ME's bbmodel channels must be decoded separately before comparison.
This checks actual blade/hand/head directions, not only exporter keyframe data.
"""
import json
from pathlib import Path
import sys
import zipfile
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
import preview_arkhaia_rig as rig
np=rig.np
with zipfile.ZipFile(ROOT/'evergarden/dist/evergarden-bedrock.mcpack') as pack:
    bones=json.loads(pack.read('models/entity/arkhaia_native.geo.json'))['minecraft:geometry'][0]['bones']
    clips=json.loads(pack.read('animations/arkhaia_native.animation.json'))['animations']
bones={b['name']:b for b in bones}
checks=0
def point(v):return np.array([-v[0],v[1],v[2]])/3.2
def sample(values,time):
    if isinstance(values,list):return np.array(values,dtype=float)
    frames=sorted((float(t),v.get('post') if isinstance(v,dict) else v) for t,v in values.items())
    if time<=frames[0][0]:return np.array(frames[0][1],dtype=float)
    for (ta,a),(tb,b) in zip(frames,frames[1:]):
        if ta<=time<=tb:
            t=(time-ta)/(tb-ta);return np.array(a)*(1-t)+np.array(b)*t
    return np.array(frames[-1][1],dtype=float)
def transforms(clip,time,gaze=(0,0)):
    matrices={};channels=clips['animation.voidscape.arkhaia.'+clip]['bones']
    def bone_matrix(name):
        if name in matrices:return matrices[name]
        bone=bones[name];pivot=point(bone['pivot']);data=channels.get(name,{})
        # This is the Bedrock IMPORT conversion, independent of our exporter.
        angles=sample(data.get('rotation',[0,0,0]),time)*np.array([-1,-1,1])
        if name=='gaze':
            expressions=clips['animation.voidscape.arkhaia.look']['bones']['gaze']['rotation']
            def evaluate(value):
                if isinstance(value,(int,float)):return value
                return (-1 if value.startswith('-') else 1)*({'v.look_yaw':gaze[0],'v.look_pitch':gaze[1]}[value.lstrip('-')])
            angles=np.array([evaluate(v) for v in expressions])*np.array([-1,-1,1])
        rotation=rig.rotation(angles);matrix=np.eye(4);matrix[:3,:3]=rotation
        matrix[:3,3]=pivot-rotation@pivot
        if 'position' in data:matrix[:3,3]+=point(sample(data['position'],time))
        if bone.get('parent'):matrix=bone_matrix(bone['parent'])@matrix
        matrices[name]=matrix;return matrix
    for name in bones:bone_matrix(name)
    return matrices
def location(name,matrices):return (matrices[name]@np.append(point(bones[name]['pivot']),1))[:3]*.2
def check(ok,label):
    global checks
    assert ok,label
    checks+=1
for action in ('light','heavy'):
    wind,active,impact,recovery,_=rig.combat_timing(action)
    tip=location('sword_tip',transforms(action+'_windup',wind))
    check(tip[2]>1 and tip[1]>5,f'{action}: windup must hold sword behind crown: {tip}')
    tip=location('sword_tip',transforms(action+'_active',impact))
    check(-tip[2]>3.5,f'{action}: contact must strike in front, not behind: {tip}')
    check(1<tip[1]<2.3 and abs(tip[0])<.45,f'{action}: contact meets forward player height: {tip}')
for action in ('rift','nova'):
    for phase in ('windup','active'):
        name=action+'_'+phase;length=clips['animation.voidscape.arkhaia.'+name]['animation_length']
        for t in ([length] if phase=='windup' else np.linspace(0,length,11)):
            hand=location('hand_l',transforms(name,float(t)))
            check(-hand[2]>1.2,f'{name}: palm must project in front, not behind: {hand}')
for yaw,pitch in ((-45,30),(45,30),(0,-20),(0,0)):
    matrices=transforms('idle',0,(yaw,pitch))
    direction=location('head_forward',matrices)-location('head',matrices);direction/=np.linalg.norm(direction)
    y,p=np.radians([yaw,pitch]);expected=np.array([np.sin(y)*np.cos(p),-np.sin(p),-np.cos(y)*np.cos(p)])
    check(float(direction@expected)>.999,f'head yaw/pitch {yaw}/{pitch}: wrong look direction {direction}')
print(f'PASS {checks} decoded Bedrock forward-direction checks: backswing behind, contact/palm forward, gaze toward target eyes')
