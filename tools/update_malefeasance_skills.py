"""Author Malefeasance's book-led rituals, portal steps and rebirth without resculpting."""
import base64
import hashlib
import io
import json
import math
from pathlib import Path
import sys
import zipfile
from PIL import Image,ImageDraw
import numpy as np
from update_river_choreography import groups,aim_arm,clip,ease,mix

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'evergarden/tools'))
from arkhaia_bedrock_assets import export_model
from malefeasance_assets import assets


def ritual(model,lift,release):
    bones=groups(model)
    pose={'chest':[-5*lift,0,0],'head':[-10*lift,0,0],
          'cape':[-10*lift,0,0],'cape_tip':[-14*lift,0,0],
          'book':[-10*lift,0,12*release],'familiar':[0,35*lift,12*release]}
    for side,sign in [('r',-1),('l',1)]:
        ready=[sign*14,31 if side=='r' else 35,-5]
        target=mix(bones['hand_'+side]['origin'],ready,lift)
        pose.update(aim_arm(bones,side,target,[sign*18,27,-5]))
    return pose


def author(model):
    additions=[]
    for prefix in ('sky','portal'):
        for stage,length in [('windup',1),('active',.5),('recover',.7)]:
            poses=[];positions=[];previous={}
            for n in range(round(length*80)+1):
                t=round(n/80,6)
                if stage=='windup':lift=ease(t/.75);release=0
                elif stage=='active':lift=1;release=ease(t/.15)
                else:lift=1-ease(t/length);release=1-ease(t/.35)
                p=ritual(model,lift,release);pos={}
                if prefix=='portal':
                    p['chest']=[-5*lift,18*lift,0];p['cape']=[-22*lift,0,0];p['cape_tip']=[-30*lift,0,0]
                    # A subtle rise and stretched mantle, rather than an untracked body teleport.
                    pos['root']=[0,.8*lift,0]
                for bone,value in p.items():
                    if bone in previous:
                        candidates=[value,[value[0]+180,180-value[1],value[2]+180]]
                        candidates=[[a+360*round((b-a)/360) for a,b in zip(v,previous[bone])] for v in candidates]
                        p[bone]=min(candidates,key=lambda v:sum((a-b)**2 for a,b in zip(v,previous[bone])))
                    previous[bone]=p[bone]
                poses.append((t,p));positions.append((t,pos))
            additions.append(clip(model,prefix+'_'+stage,length,poses,positions))
    poses=[];positions=[]
    for n in range(161):
        t=n/80;lift=ease(t/.8)*(1-ease((t-1.4)/.6))
        poses.append((t,ritual(model,lift,0)));positions.append((t,{'root':[0,2.8*math.sin(math.pi*t/2),0]}))
    additions.append(clip(model,'rebirth',2,poses,positions))
    names={a['name'] for a in additions};model['animations']=[a for a in model['animations'] if a['name'] not in names]+additions


def patch_packs(model):
    dist=ROOT/'evergarden/dist'
    def read(name):
        with zipfile.ZipFile(dist/name) as z:return {n:z.read(n) for n in z.namelist() if not n.endswith('/')}
    java=read('judge-java.zip');bedrock=read('evergarden-bedrock.mcpack')
    encode=lambda x:(json.dumps(x,separators=(',',':'))+'\n').encode()
    extra_java,extra_bedrock=assets(encode,java,bedrock);java.update(extra_java);bedrock.update(extra_bedrock)
    bedrock.update(export_model(model,'arkhaia_malefeasance',4))
    manifest=json.loads(bedrock['manifest.json']);version=max(manifest['header']['version'],[3,36,0]);manifest['header']['version']=version
    for module in manifest['modules']:module['version']=version
    bedrock['manifest.json']=encode(manifest)
    for name,files in [('judge-java.zip',java),('evergarden-bedrock.mcpack',bedrock)]:
        with zipfile.ZipFile(dist/name,'w',zipfile.ZIP_DEFLATED) as z:
            for key,value in files.items():z.writestr(key,value)
    hashes=json.loads((dist/'pack-hashes.json').read_text())
    for name in ('judge-java.zip','evergarden-bedrock.mcpack'):hashes[name]=hashlib.sha1((dist/name).read_bytes()).hexdigest()
    (dist/'pack-hashes.json').write_text(json.dumps(hashes,indent=2)+'\n')


def preview(model):
    import preview_arkhaia_minions_v2 as rig
    out=ROOT/'previews/malefeasance-dimensional';out.mkdir(parents=True,exist_ok=True)
    texture=Image.open(io.BytesIO(base64.b64decode(model['textures'][0]['source'].split(',')[1]))).convert('RGBA')
    rig.load(model,texture)
    board=Image.new('RGB',(1600,860),(23,21,34));d=ImageDraw.Draw(board)
    for row,(name,t) in enumerate([('sky_active',.2),('rebirth',1)]):
        for column,yaw in enumerate([0,90,180,270]):
            board.paste(rig.rig.render(name,t,yaw=yaw,elevation=5,size=400,span=60),(column*400,row*430))
            d.text((column*400+8,row*430+402),f'{name} yaw={yaw}',fill='white')
    board.save(out/'model-review.png')
    # These are texture/geometry previews, not screenshots from a connected game client.
    from malefeasance_assets import seal,beam
    effects=Image.new('RGBA',(1440,720),(14,9,25,255))
    for i,color in enumerate([(174,72,255),(255,36,83)]):
        glyph=Image.open(io.BytesIO(seal(color))).resize((600,600))
        beam_image=Image.open(io.BytesIO(beam(color))).resize((180,650))
        effects.alpha_composite(beam_image,(i*720+270,40));effects.alpha_composite(glyph,(i*720+60,60))
    effects.convert('RGB').save(out/'dimensional-seals.png')
    print(out)


if __name__=='__main__':
    path=ROOT/'evergarden/src/main/resources/modelengine/rivers/arkhaia_malefeasance.bbmodel'
    model=json.loads(path.read_text(encoding='utf8'));author(model)
    path.write_text(json.dumps(model,separators=(',',':'))+'\n',encoding='utf8')
    patch_packs(model);preview(model)
    print('Malefeasance: 17 clips, dimensional sky/portal/rebirth, purple/crimson effects, Bedrock 3.36.0')
