"""Author production Six Rivers combat clips without rebuilding their sculptures.

Angles below are geometry-space degrees; serialization applies ME R4's X/Y signs.
Run this before build_packs.py. Preview V2 geometry remains the reference sculpture.
"""
import copy
import json
import math
from pathlib import Path
import uuid
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
NAMES = ['acheron', 'phlegethon', 'cocytus', 'banshee_wail', 'malefeasance', 'styx']


def uid(value):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'evergarden/river-choreography/' + value))


def groups(model):
    result = {}
    def visit(group):
        result[group['name']] = group
        for child in group['children']:
            if isinstance(child, dict):
                visit(child)
    for group in model['outliner']:
        visit(group)
    return result


def align(a, b):
    a, b = np.array(a, float), np.array(b, float)
    a /= np.linalg.norm(a); b /= np.linalg.norm(b)
    v = np.cross(a, b); c = a @ b
    k = np.array([[0, -v[2], v[1]], [v[2], 0, -v[0]], [-v[1], v[0], 0]])
    return np.eye(3) + k + k @ k / (1 + c)


def angles(r):
    return list(map(float, np.degrees([math.atan2(r[2, 1], r[2, 2]),
                                      math.asin(float(np.clip(-r[2, 0], -1, 1))), math.atan2(r[1, 0], r[0, 0])])) )


def rotation(v):
    x,y,z = np.radians(v)
    cx,sx,cy,sy,cz,sz = math.cos(x),math.sin(x),math.cos(y),math.sin(y),math.cos(z),math.sin(z)
    return np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]]) @ np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]]) @ np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])


def turn(a, b, t):
    relative = a.T @ b
    theta = math.acos(float(np.clip((np.trace(relative)-1)/2, -1, 1)))
    if theta < 1e-8:
        return a.copy()
    axis = np.array([relative[2,1]-relative[1,2],relative[0,2]-relative[2,0],relative[1,0]-relative[0,1]])/(2*math.sin(theta))
    k = np.array([[0,-axis[2],axis[1]],[axis[2],0,-axis[0]],[-axis[1],axis[0],0]])
    return a @ (np.eye(3)+math.sin(theta*t)*k+(1-math.cos(theta*t))*(k@k))


def chain(bones, side, target, pole, names=('arm_', 'forearm_', 'hand_'), orientation=None):
    s, e, h = [np.array(bones[n + side]['origin'], float) for n in names]
    target, pole = np.array(target, float), np.array(pole, float)
    a, b = np.linalg.norm(e - s), np.linalg.norm(h - e)
    d = np.linalg.norm(target - s); axis = (target - s) / d
    assert abs(a-b)-1e-7 < d <= a + b+1e-7, (side,names,target,d,a+b)
    x = (a*a - b*b + d*d) / (2*d)
    bend = pole - s - axis * ((pole - s) @ axis); bend /= np.linalg.norm(bend)
    elbow = s + axis*x + bend*math.sqrt(max(0, a*a - x*x))
    upper = align(e - s, elbow - s)
    lower = align(h - e, upper.T @ (target - elbow))
    return {names[0] + side: angles(upper), names[1] + side: angles(lower),
            names[2] + side: angles((upper @ lower).T @ (np.eye(3) if orientation is None else orientation))}


def aim_arm(bones, side, target, pole, orientation=None):
    return chain(bones,side,target,pole,orientation=orientation)


def ease(t):
    t = float(np.clip(t,0,1))
    return t*t*(3-2*t)


def mix(a,b,t):
    return np.array(a)*(1-t)+np.array(b)*t


def grounded_legs(bones,pose,positions):
    pivot=np.array(bones['pelvis']['origin']);r=rotation(pose.get('pelvis',[0,0,0]));offset=np.array(positions.get('pelvis',[0,0,0]))
    for side in ('r','l'):
        target=np.array(bones['foot_'+side]['origin']);pole=np.array([-3 if side=='r' else 3,8,-10])
        target=pivot+r.T@(target-pivot-offset);pole=pivot+r.T@(pole-pivot-offset)
        pose.update(chain(bones,side,target,pole,('leg_','shin_','foot_'),r.T))


def physical_pose(model,kind,lift,impact):
    bones=groups(model);pose={};positions={}
    if kind=='styx':
        s=np.array(bones['hand_r']['origin']);h=np.array(bones['hand_l']['origin']);mid=(s+h)/2
        axis=(h-s)/np.linalg.norm(h-s);edge=np.array([axis[1],-axis[0],0.])
        def axe_angle(degrees):
            a=math.radians(degrees);d=np.array([0,math.cos(a),-math.sin(a)]);e=np.array([0,-math.sin(a),-math.cos(a)])
            return np.outer(d,axis)+np.outer(e,edge)+np.outer(np.cross(d,e),np.cross(axis,edge))
        ready=axe_angle(-12);strike=axe_angle(78)
        r=turn(np.eye(3),ready,lift) if impact==0 else turn(ready,strike,impact)
        center=mix(mid,[2,37,-6],lift) if impact==0 else mix([2,37,-6],[3,26,-12],impact)
        pose.update(axe_driver=angles(r),pelvis=[-22*impact,0,0],chest=[-5*lift-15*impact,0,0],head=[15*impact,0,0])
        positions.update(axe_driver=list(center-mid),pelvis=[0,-2*impact,0])
        for side in ('r','l'):
            grip=np.array(bones['hand_'+side]['origin'])-mid
            if side=='l':grip=grip-axis*9.6*lift
            target=center+r@grip
            pole=[-16 if side=='r' else 16,27,-12]
            pose.update(aim_arm(bones,side,target,pole,r))
        grounded_legs(bones,pose,positions)
    elif kind=='phlegethon':
        pose.update(pelvis=[-65*impact,0,0],chest=[-3*lift-7*impact,0,0],head=[20*impact,0,0])
        positions['pelvis']=[0,-6*impact,0]
        for side,sign in [('r',-1),('l',1)]:
            shrug=2*lift*(1-impact)
            if impact==0:
                target=mix(bones['hand_'+side]['origin'],[sign*4,40,-1],lift)
                target[0]+=sign*4*math.sin(math.pi*lift)
                target[2]-=6*math.sin(math.pi*lift)
            else:
                target=mix([sign*4,40,-1],[sign*5,26,-10],impact)
                target[0]+=sign*2*math.sin(math.pi*impact)
                target[2]=-1-9*ease(impact/.45)
            target[1]-=shrug
            pole=mix(bones['forearm_'+side]['origin'],[sign*16,33,-4],lift)
            pose.update(aim_arm(bones,side,target,pole))
            positions['arm_'+side]=[0,shrug,0];positions['shoulder_'+side]=[0,shrug,0]
        grounded_legs(bones,pose,positions)
    else:
        # Archery stance: the torso turns side-on. Each elbow stays outside its
        # own ribs instead of drawing a forearm across the middle of the chest.
        pose.update(chest=[0,90*lift,0],head=[0,-90*lift,0])
        for side in ('r','l'):
            target=([17.5,29,-4.5] if side=='l' else [-6-2*impact,29,-5])
            target=mix(bones['hand_'+side]['origin'],target,lift)
            pole=mix(bones['forearm_'+side]['origin'],[-15 if side=='r' else 15,26,-8],lift)
            pose.update(aim_arm(bones,side,target,pole))
    return pose,positions


def physical_clips(model,kind):
    result=[]
    for name,length in [('attack_windup',.8),('attack_active',.4),('attack_recover',.7),('cast_windup',1),('cast_active',.5),('cast_recover',.7)]:
        poses=[];positions=[];previous={}
        for n in range(round(length*80)+1):
            t=round(n/80,6)
            if name.endswith('windup'):
                lift=ease(t/(length*.65));impact=0
                if name.startswith('cast') and kind!='cocytus':impact=ease((t/length-.8)/.2)
            elif name.endswith('active'):
                lift=1;impact=ease(t/.2) if name.startswith('attack') or kind=='cocytus' else 1
            else:
                # Lift clear of the chest on the way back, then lower the weapon.
                impact=1-ease(t/(length*.5));lift=1-ease((t/length-.5)/.5)
            p,pos=physical_pose(model,kind,lift,impact)
            for bone,v in p.items():
                if bone in previous:
                    candidates=[v,[v[0]+180,180-v[1],v[2]+180]]
                    candidates=[[a+360*round((b-a)/360) for a,b in zip(candidate,previous[bone])] for candidate in candidates]
                    v=min(candidates,key=lambda candidate:sum((a-b)**2 for a,b in zip(candidate,previous[bone])))
                    p[bone]=v
                previous[bone]=v
            poses.append((t,p));positions.append((t,pos))
        result.append(clip(model,name,length,poses,positions))
    return result


def clip(model, name, length, poses, positions=()):
    animators = {}
    for bone, group in groups(model).items():
        if bone == 'hitbox':
            continue
        keys = []
        for channel, samples in [('rotation', poses), ('position', positions)]:
            for time, pose in samples:
                value = pose.get(bone, [0, 0, 0])
                if channel == 'rotation':
                    value = [-value[0], -value[1], value[2]]
                elif channel == 'position':
                    # R4 geometry flips X/Z; its animation translation parser
                    # flips only Z. Encode X so both land in the same space.
                    value = [-value[0], value[1], value[2]]
                keys.append(dict(channel=channel, time=time, data_points=[dict(zip('xyz', map(str, value)))],
                                 interpolation='linear', uuid=uid(f'{model["name"]}/{name}/{bone}/{channel}/{time}')))
        animators[group['uuid']] = dict(name=bone, type='bone', keyframes=keys)
    return dict(name=name, uuid=uid(model['name'] + '/' + name), length=length, loop='once',
                override=True, snapping=20, animators=animators)


def author(model, kind):
    model['meta']['evergarden_animation_position_x']='inverted'
    # R4 runs its default idle underneath explicit states. These clips author
    # complete poses, so additive idle rotations would invalidate solved grips.
    for animation in model['animations']:animation['override']=True
    bone = groups(model)
    if kind=='styx' and 'axe_driver' not in bone:
        # Discard the old rigid assembly: the weapon now drives two independent
        # articulated arms. Both grips are solved at every authored sample.
        chest=bone['chest'];right=bone['arm_r'];left=bone['arm_l'];weapon=bone['weapon_r']
        bone['hand_r']['children'].remove(weapon)
        chest['children']=[g for g in chest['children'] if not(isinstance(g,dict) and g['name'] in ('axe_swing','axe_align','arm_r','arm_l'))]
        chest['children'].extend([right,left,dict(name='axe_driver',origin=[-2.1,19.7,-6],rotation=[0,0,0],uuid=uid('styx/axe_driver'),export=True,isOpen=True,children=[weapon])])
        bone=groups(model)
    if kind in ('styx','phlegethon','cocytus'):
        if kind=='styx':
            baseline,_=physical_pose(model,kind,0,0)
            for old in model['animations']:
                if old['name'].startswith(('cast_','attack_')):continue
                for animator in old['animators'].values():
                    name=animator['name']
                    if name.startswith(('arm_','forearm_','hand_')):
                        v=baseline[name];v=[-v[0],-v[1],v[2]]
                        for frame in animator['keyframes']:
                            if frame['channel']=='rotation':frame['data_points']=[dict(zip('xyz',map(str,v)))]
        new={a['name']:a for a in physical_clips(model,kind)}
        model['animations']=[new.get(a['name'],a) for a in model['animations']]
        return
    rest = {}
    if kind == 'acheron':
        ready = dict(arm_r=[25, 0, -72], arm_l=[25, 0, 72], forearm_r=[20, 0, 0], forearm_l=[20, 0, 0],
                     chest=[-8, -25, 0], leg_r=[12, 0, -8], leg_l=[-12, 0, 8])
        strike = dict(ready, chest=[4, 30, 0], forearm_r=[0, 0, 0], forearm_l=[0, 0, 0])
    elif kind == 'banshee_wail':
        ready = dict(arm_r=[70, -10, -45], arm_l=[70, 10, 45], forearm_r=[55, 0, 0],
                     forearm_l=[55, 0, 0], chest=[-18, 0, 0], head=[-18, 0, 0], jaw=[12, 0, 0])
        strike = dict(arm_r=[25, 0, -72], arm_l=[25, 0, 72], forearm_r=[5, 0, 0],
                      forearm_l=[5, 0, 0], chest=[15, 0, 0], head=[-10, 0, 0], jaw=[38, 0, 0])
        for i in range(6):
            ready['veil_' + str(i)] = [-12, 0, (i - 2.5) * 3]
            strike['veil_' + str(i)] = [-48, 0, (i - 2.5) * 3]
        strike.update(chest=[-8,0,0],cape=[-30,0,0],cape_tip=[-18,0,0])
    elif kind == 'malefeasance':
        ready = dict(arm_r=[65, 0, -10], forearm_r=[25, 0, 0], book=[-55, 0, 15],
                     arm_l=[110, 0, 65], forearm_l=[15, 0, 0], familiar=[0, -35, 15], chest=[-8, -12, 0])
        strike = dict(ready, arm_l=[75, 0, 65], forearm_l=[0, 0, 0], book=[-35, 0, -12],
                      familiar=[0, 35, -15], chest=[8, 15, 0])
    else:
        raise ValueError(kind)
    pos = {}
    cast_ready, cast_strike = copy.deepcopy(ready), copy.deepcopy(strike)
    active = [(0, cast_strike), (.5, cast_strike)]
    recover = cast_strike
    if kind == 'acheron':
        # Four sub-180-degree keys per turn prevent shortest-path interpolation.
        active = []
        for n in range(25):
            active.append((n / 20, dict(cast_strike, root=[0, n * 45, 0])))
        recover = dict(cast_strike, root=[0, 1080, 0])
    elif kind == 'banshee_wail':
        active = [(0, cast_strike), (.12, dict(cast_strike, jaw=[30, 0, 0])),
                  (.25, cast_strike), (.37, dict(cast_strike, jaw=[30, 0, 0])), (.5, cast_strike)]
    # Heavy casters complete the physical downswing during the last warning frames.
    cast_poses = [(0, rest), (.6, cast_ready), (.82, cast_ready), (1, cast_strike)]
    if kind in ('acheron', 'cocytus', 'banshee_wail', 'malefeasance'):
        cast_poses = [(0, rest), (.75, cast_ready), (1, cast_ready)]
        if kind != 'acheron':
            active = [(0, cast_ready), (.05, cast_strike), (.5, cast_strike)]
    new = [clip(model, 'cast_windup', 1, cast_poses),
           clip(model, 'cast_active', 1.2 if kind=='acheron' else .5, active),
           clip(model, 'cast_recover', .7, [(0, recover), (.7, dict(root=[0, 1080, 0]) if kind == 'acheron' else rest)])]
    new += [clip(model, 'attack_windup', .8, [(0, rest), (.6, ready), (.8, ready)]),
            clip(model, 'attack_active', .4, [(0, ready), (.2, strike), (.4, strike)], [(0, {}), (.2, pos), (.4, pos)]),
            clip(model, 'attack_recover', .7, [(0, strike), (.7, rest)], [(0, pos), (.7, {})])]
    by_name = {a['name']: a for a in new}
    model['animations'] = [by_name.get(a['name'], a) for a in model['animations']]


if __name__ == '__main__':
    for kind in NAMES:
        path = ROOT / f'evergarden/src/main/resources/modelengine/rivers/arkhaia_{kind}.bbmodel'
        model = json.loads(path.read_text(encoding='utf8'))
        author(model, kind)
        path.write_text(json.dumps(model, separators=(',', ':')) + '\n', encoding='utf8')
        print('Authored', kind)
