"""Check solid forearms/palms/fingers against the sculpted torso and head.

This covers intermediate poses, which the previous blade-only check missed.
Small armor seam contact is allowed; a limb crossing the body is not.
"""
import json
from pathlib import Path
import sys
import numpy as np

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
import preview_arkhaia_minions_v2 as rig


def obb(part,matrix):
    size=np.array(part['size']);center=(matrix@np.append(np.array(part['at'])+size/2,1))[:3]
    axes=matrix[:3,:3]@rig.rig.rotation(part['rot']);half=size/2
    return center,axes,half


def intersects(a,b):
    ca,ra,ea=a;cb,rb,eb=b
    # Cheap world-space rejection before the full separating-axis test.
    if np.any(np.abs(cb-ca)>=np.abs(ra)@ea+np.abs(rb)@eb-.2):return False
    axes=np.vstack((ra.T,rb.T,np.cross(ra.T[:,None,:],rb.T[None,:,:]).reshape(9,3)))
    axes=axes[np.linalg.norm(axes,axis=1)>1e-8]
    axes/=np.linalg.norm(axes,axis=1)[:,None]
    return not np.any(np.abs(axes@(cb-ca))>=np.abs(axes@ra)@ea+np.abs(axes@rb)@eb-.2)


def inspect(name):
    model=json.loads((ROOT/f'evergarden/src/main/resources/modelengine/rivers/arkhaia_{name}.bbmodel').read_text())
    study=rig.Study(rig.NAMES.index(name)).sculpt()
    cores=[p for p in study.parts if p['bone'] in ('chest','head') and p['label']==p['bone'] and p['size'][2]>=4]
    limbs=[p for p in study.parts if p['bone'].startswith(('forearm_','hand_'))]
    failures=[];checks=0;grip_error=0
    for clip in model['animations']:
        for t in np.linspace(0,clip['length'],61):
            matrices=rig.matrices(model,clip['name'],float(t))
            bodies=[(p,obb(p,matrices[p['bone']])) for p in cores]
            for part in limbs:
                limb=obb(part,matrices[part['bone']])
                for body,bounds in bodies:
                    checks+=1
                    if intersects(limb,bounds):failures.append([clip['name'],round(float(t),4),part['label'],body['bone']])
            if name=='styx':
                grip=np.append(study.at('hand_r'),1)
                grip_error=max(grip_error,float(np.linalg.norm((matrices['hand_r']@grip-matrices['weapon_r']@grip)[:3])))
    return dict(name=name,checks=checks,collisions=len(failures),examples=failures[:12],max_grip_error_units=grip_error)


if __name__=='__main__':
    # The redesigned Acheron no longer matches the v2 study's limbs; check_river_choreography
    # checks its own shipped forearms and fists against its torso and head instead.
    report=[inspect(name) for name in rig.NAMES if name!='acheron']
    for item in report:print(item,flush=True)
    target=ROOT/'previews/river-choreography-v2/body-clearance.json'
    target.parent.mkdir(exist_ok=True);target.write_text(json.dumps(report,indent=2))
    assert not any(item['collisions'] for item in report),'body intersections found'
