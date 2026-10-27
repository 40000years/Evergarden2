"""Production weapon clearance, two-handed grip and role-specific motion checks."""
import base64
import io
import json
from pathlib import Path
import sys
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
import preview_arkhaia_minions_v2 as rig

def acheron_production(model):
    """The redesigned bladedancer is checked on its own shipped solids, not the old study:
    quill spines/fullers never pass through the torso, hips, skull or mask in any clip, the
    quills never shift in the fists, forearms and fists never cross the body (armour seams
    within 0.2 allowed, as in check_river_body_clearance), soles stay on or above the floor
    while walking, and the idle starts square so the gaze bone aims the mask exactly."""
    owner={}
    def visit(g):
        for c in g['children']:
            if isinstance(c,dict):visit(c)
            else:owner[c]=g['name']
    for g in model['outliner']:visit(g)
    def solids(*labels,bones=()):
        out=[]
        for e in model['elements']:
            if e['name'].rsplit('_',1)[0] not in labels and not (bones and owner[e['uuid']].startswith(bones)):continue
            lo,hi=rig.np.array(e['from']),rig.np.array(e['to']);r=rig.rig.rotation(e.get('rotation',[0,0,0]));pivot=rig.np.array(e['origin'])
            out.append((owner[e['uuid']],pivot+r@((lo+hi)/2-pivot),r,(hi-lo)/2))
        return out
    def place(solid,m):
        bone,center,r,half=solid;mm=m[bone];return (mm@rig.np.append(center,1))[:3],mm[:3,:3]@r,half
    def intersects(a,b,margin=1e-5):
        ca,ra,ea=a;cb,rb,eb=b
        axes=rig.np.vstack((ra.T,rb.T,rig.np.cross(ra.T[:,None,:],rb.T[None,:,:]).reshape(9,3)))
        axes=axes[rig.np.linalg.norm(axes,axis=1)>1e-8];axes/=rig.np.linalg.norm(axes,axis=1)[:,None]
        return not rig.np.any(rig.np.abs(axes@(cb-ca))>=rig.np.abs(axes@ra)@ea+rig.np.abs(axes@rb)@eb-margin)
    blades=solids('quill_spine','quill_fuller');cores=solids('bodice','hips','skull','mask');feet=solids('sabaton','toe','heel_spur')
    limbs=solids(bones=('forearm_','hand_'))
    assert len(blades)==14 and len(cores)==7 and len(feet)==12 and len(limbs)==14;count=0
    for a in model['animations']:
        for animator in a['animators'].values():
            if animator['name'].startswith('weapon_'):
                assert all(float(v)==0 for f in animator['keyframes'] for v in f['data_points'][0].values()),'quill shifts in the fist';count+=1
        for t in rig.np.linspace(0,a['length'],17):
            m=rig.matrices(model,a['name'],float(t))
            for blade in blades:
                for core in cores:
                    assert not intersects(place(blade,m),place(core,m)),f'acheron {a["name"]} {t:.3f}: quill through {core[0]}';count+=1
        for t in rig.np.linspace(0,a['length'],61):
            m=rig.matrices(model,a['name'],float(t))
            for limb in limbs:
                for core in cores:
                    assert not intersects(place(limb,m),place(core,m),.2),f'acheron {a["name"]} {t:.3f}: {limb[0]} through {core[0]}';count+=1
    for t in rig.np.linspace(0,1,33):
        m=rig.matrices(model,'walk',float(t))
        for foot in feet:
            c,r,half=place(foot,m)
            corners=rig.np.array([[x,y,z] for x in (-1,1) for y in (-1,1) for z in (-1,1)])*half
            assert (c+corners@r.T)[:,1].min()>-.025,'foot below floor';count+=1
    for yaw in [-50,0,50]:
        m=rig.matrices(model,'idle',0,(yaw,20));forward=m['head'][:3,:3]@rig.np.array([0,0,-1]);a,p=rig.math.radians(yaw),rig.math.radians(20)
        assert rig.np.dot(forward,[rig.math.sin(a)*rig.math.cos(p),-rig.math.sin(p),-rig.math.cos(a)*rig.math.cos(p)])>.9999;count+=1
        assert rig.np.allclose(m["chest"][:3,:3],rig.np.eye(3),atol=1e-5);count+=1
    return count


checks = 0
for i, name in enumerate(rig.NAMES):
    model = json.loads((ROOT / f'evergarden/src/main/resources/modelengine/rivers/arkhaia_{name}.bbmodel').read_text())
    assert all(a['override'] for a in model['animations']),'complete poses must override R4 default idle'
    checks+=1
    tex = Image.open(io.BytesIO(base64.b64decode(model['textures'][0]['source'].split(',')[1]))).convert('RGBA')
    if name=='acheron':
        checks += acheron_production(model)
    else:
        study=rig.Study(i).sculpt()
        if name=='styx':
            # The left hand deliberately slides down the shaft during the lift.
            # Its distance to the shaft is checked below rather than its old bind marker.
            study.grips=[g for g in study.grips if g[0]!='l']
        report = rig.validate(study, model, tex,grip_tolerance=.05)
        checks += report['checks'] + report['blade_core_collision_samples']
    if name == 'acheron':
        clip = next(a for a in model['animations'] if a['name'] == 'cast_active')
        animator = next(a for a in clip['animators'].values() if a['name'] == 'root')
        turns = [float(f['data_points'][0]['y']) for f in animator['keyframes'] if f['channel'] == 'rotation']
        assert abs(turns[-1] - turns[0]) == 1080
        assert all(abs(b-a) <= 90 for a,b in zip(turns, turns[1:])); checks += 2
    if name == 'cocytus':
        matrices = rig.matrices(model, 'attack_windup', .8)
        # Bow is upright; draw hand is pulled back relative to bow hand.
        assert abs(matrices['weapon_l'][1,1] - 1) < 1e-6
        def hand(side):
            point = rig.Study(i).sculpt().at('hand_' + side)
            return (matrices['hand_' + side] @ rig.np.append(point, 1))[:3]
        assert hand('r')[2] - hand('l')[2] > 5; checks += 2
    if name == 'styx':
        ready = rig.matrices(model, 'attack_windup', .8)
        contact = rig.matrices(model, 'attack_active', .2)
        blade = rig.np.array([13,34,-6,1])
        a, b = ready['weapon_r'] @ blade, contact['weapon_r'] @ blade
        assert a[1] > 38 and b[1] < a[1]-12 and b[2] < -10; checks += 1
        axis=study.at('hand_l')-study.at('hand_r');axis/=rig.np.linalg.norm(axis)
        for clip in model['animations']:
            for t in rig.np.linspace(0,clip['length'],61):
                matrices=rig.matrices(model,clip['name'],float(t));inverse=rig.np.linalg.inv(matrices['weapon_r'])
                for side in ('r','l'):
                    hand=rig.np.append(study.at('hand_'+side),1)
                    local=(inverse@matrices['hand_'+side]@hand)[:3]
                    assert rig.np.linalg.norm(rig.np.cross(local-study.at('hand_r'),axis))<.05,('shaft grip',clip['name'],t,side)
                    checks+=1
    if name=='phlegethon':
        ready=rig.matrices(model,'attack_windup',.8);contact=rig.matrices(model,'attack_active',.2)
        for side in ('r','l'):
            hand=rig.np.append(study.at('hand_'+side),1)
            assert (ready['hand_'+side]@hand)[1]>39
            point=contact['hand_'+side]@hand
            assert point[1]<4.5 and point[2]<-10
            checks+=2
    if name=='banshee_wail':
        ready=rig.matrices(model,'attack_windup',.8);contact=rig.matrices(model,'attack_active',.2)
        for n in range(6):
            tail=rig.np.array([(n-2.5)*1.5,5,4.2,1]);bone='veil_'+str(n)
            assert (contact[bone]@tail)[2]>(ready[bone]@tail)[2]+5,'veil must stream behind the forward scream'
            checks+=1
    if name in ('phlegethon','styx'):
        for clip in model['animations']:
            if not clip['name'].startswith(('attack_','cast_')):continue
            for t in rig.np.linspace(0,clip['length'],61):
                matrices=rig.matrices(model,clip['name'],float(t))
                for side in ('r','l'):
                    foot=study.at('foot_'+side)
                    assert rig.np.linalg.norm((matrices['foot_'+side]@rig.np.append(foot,1))[:3]-foot)<.05,('foot slip',name,clip['name'],t)
                    checks+=1
print(f'PASS {checks} production choreography, blade clearance, grip, spin and bow draw checks')
