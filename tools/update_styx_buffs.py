"""Add two complete two-handed axe rituals and update only Styx's Bedrock rig."""
import hashlib,json,sys,math
from pathlib import Path
import zipfile
from update_river_choreography import physical_pose,clip,ease
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'evergarden/tools'))
from arkhaia_bedrock_assets import export_model

def author(model):
    additions=[]
    for skill in ('blood','berserk'):
        for stage,length in [('windup',1),('active',.5),('recover',.7)]:
            poses=[];positions=[];previous={}
            for n in range(round(length*80)+1):
                t=round(n/80,6)
                lift=ease(t/.8) if stage=='windup' else 1 if stage=='active' else 1-ease(t/length)
                p,pos=physical_pose(model,'styx',lift*.65,0)
                p['root']=[0,(8 if skill=='berserk' else -5)*lift,0]
                for bone,v in p.items():
                    if bone in previous:
                        candidates=[v,[v[0]+180,180-v[1],v[2]+180]]
                        candidates=[[a+360*round((b-a)/360) for a,b in zip(c,previous[bone])] for c in candidates]
                        p[bone]=min(candidates,key=lambda c:sum((a-b)**2 for a,b in zip(c,previous[bone])))
                    previous[bone]=p[bone]
                poses.append((t,p));positions.append((t,pos))
            additions.append(clip(model,skill+'_'+stage,length,poses,positions))
    names={a['name'] for a in additions};model['animations']=[a for a in model['animations'] if a['name'] not in names]+additions

if __name__=='__main__':
    path=ROOT/'evergarden/src/main/resources/modelengine/rivers/arkhaia_styx.bbmodel'
    model=json.loads(path.read_text(encoding='utf8'));author(model);path.write_text(json.dumps(model,separators=(',',':'))+'\n',encoding='utf8')
    pack=ROOT/'evergarden/dist/evergarden-bedrock.mcpack'
    with zipfile.ZipFile(pack) as z:files={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
    files.update(export_model(model,'arkhaia_styx',4))
    manifest=json.loads(files['manifest.json']);manifest['header']['version']=max(manifest['header']['version'],[3,37,0])
    for module in manifest['modules']:module['version']=manifest['header']['version']
    files['manifest.json']=(json.dumps(manifest,separators=(',',':'))+'\n').encode()
    with zipfile.ZipFile(pack,'w',zipfile.ZIP_DEFLATED) as z:
        for name,data in files.items():z.writestr(name,data)
    hashes=ROOT/'evergarden/dist/pack-hashes.json';data=json.loads(hashes.read_text());data[pack.name]=hashlib.sha1(pack.read_bytes()).hexdigest();hashes.write_text(json.dumps(data,indent=2)+'\n')
    print('Styx: 28 clips; two-handed blood / berserk rituals; Bedrock 3.37.0')
