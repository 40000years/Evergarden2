"""Author Cocytus's upward bow draw and patch only its existing Bedrock entity assets."""
import hashlib
import json
from pathlib import Path
import sys
import zipfile
import numpy as np
from update_river_choreography import groups, aim_arm, clip, angles, rotation, ease, mix

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'evergarden/tools'))
from arkhaia_bedrock_assets import export_model


def sky_pose(model,lift,release):
    bones=groups(model)
    # Turn side-on and lean back only twenty degrees; articulated arms do the lifting.
    pose={'chest':angles(rotation([20*lift,0,0])@rotation([0,90*lift,0])),
          'head':[0,-90*lift,0]}
    orient=rotation([0,0,30*lift])
    for side in ('r','l'):
        target=[12,37,-4.5] if side=='l' else [-6-2*release,30,-5]
        target=mix(bones['hand_'+side]['origin'],target,lift)
        pole=mix(bones['forearm_'+side]['origin'],[-15 if side=='r' else 16,34,-8],lift)
        pose.update(aim_arm(bones,side,target,pole,orient))
    return pose


def author(model):
    additions=[]
    for stage,length in [('windup',1),('active',.5),('recover',.7)]:
        samples=[];previous={}
        for n in range(round(length*80)+1):
            t=round(n/80,6)
            if stage=='windup':lift=ease(t/.8);release=0
            elif stage=='active':lift=1;release=ease(t/.2)
            else:lift=1-ease(t/length);release=1-ease(t/(length*.5))
            pose=sky_pose(model,lift,release)
            for bone,value in pose.items():
                if bone in previous:
                    candidates=[value,[value[0]+180,180-value[1],value[2]+180]]
                    candidates=[[a+360*round((b-a)/360) for a,b in zip(v,previous[bone])] for v in candidates]
                    pose[bone]=min(candidates,key=lambda v:sum((a-b)**2 for a,b in zip(v,previous[bone])))
                previous[bone]=pose[bone]
            samples.append((t,pose))
        additions.append(clip(model,'rain_'+stage,length,samples))
    model['animations']=[a for a in model['animations'] if not a['name'].startswith('rain_')]+additions


def patch_pack(model):
    path=ROOT/'evergarden/dist/evergarden-bedrock.mcpack'
    with zipfile.ZipFile(path) as z:files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
    files.update(export_model(model,'arkhaia_cocytus',4))
    manifest=json.loads(files['manifest.json'])
    # Keep the version monotonic if the shared pack has advanced in another task.
    version=max(manifest['header']['version'],[3,35,0])
    manifest['header']['version']=version
    for module in manifest['modules']:module['version']=version
    files['manifest.json']=(json.dumps(manifest,separators=(',',':'))+'\n').encode()
    with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED) as z:
        for name,data in files.items():z.writestr(name,data)
    hashes=ROOT/'evergarden/dist/pack-hashes.json'
    data=json.loads(hashes.read_text());data[path.name]=hashlib.sha1(path.read_bytes()).hexdigest()
    hashes.write_text(json.dumps(data,indent=2)+'\n')


if __name__=='__main__':
    path=ROOT/'evergarden/src/main/resources/modelengine/rivers/arkhaia_cocytus.bbmodel'
    model=json.loads(path.read_text(encoding='utf8'));author(model)
    path.write_text(json.dumps(model,separators=(',',':'))+'\n',encoding='utf8')
    patch_pack(model)
    print('Cocytus: 13 animation clips; upward bow draw; Bedrock pack patched')
