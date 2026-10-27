"""Verify the shipped effect contracts and preservation of all pre-existing model/pack assets."""
from pathlib import Path
from zipfile import ZipFile
import argparse,hashlib,io,json,math
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);args=p.parse_args()
build=args.build.resolve();manifest=json.loads((build/'build-manifest.json').read_text());checks=0
def check(ok,label):
    global checks
    if not ok:raise AssertionError(label)
    checks+=1
def unpack(data):
    with ZipFile(io.BytesIO(data)) as z:return {n:z.read(n) for n in z.namelist() if not n.endswith('/')}
with ZipFile(ROOT/'output/seraphon-release-20261023/evergarden-before.jar') as old,ZipFile(build/'evergarden.jar') as new:
    for n,h in manifest['preserved_model_files'].items():check(old.read(n)==new.read(n) and hashlib.sha256(new.read(n)).hexdigest()==h,'preserve model '+n)
    for pack,key in [('judge-java.zip','new_java_effect_entries'),('evergarden-bedrock.mcpack','new_bedrock_effect_entries')]:
        before=unpack(old.read('resource-packs/'+pack));after=unpack(new.read('resource-packs/'+pack))
        allowed=set(manifest[key])|({'manifest.json'} if pack.endswith('mcpack') else set())
        for n,data in before.items():check(n in allowed or after.get(n)==data,'preserve existing pack asset '+n)
        for name in ('seraphon_seal','seraphon_orbit','seraphon_sanctuary','seraphon_ray','seraphon_feather'):
            png=f'assets/voidscape/textures/effect/{name}.png' if pack.endswith('zip') else f'textures/particle/{name}.png'
            im=Image.open(io.BytesIO(after[png])).convert('RGBA');lo,hi=im.getchannel('A').getextrema();check(lo==0 and hi>200,'transparent bright spell texture '+name)
            if pack.endswith('zip'):
                item=json.loads(after[f'assets/voidscape/items/{name}.json']);check(item['model']['model']=='voidscape:effect/'+name,'Java item selects exact effect '+name)
            else:
                for flat in ('','_flat'):
                    particle=json.loads(after[f'particles/{name}{flat}.particle.json'])['particle_effect']
                    check(particle['description']['identifier']=='voidscape:'+name+flat,'native Bedrock effect identifier '+name+flat)
                    check(particle['components']['minecraft:particle_lifetime_expression']['max_lifetime']>=.15,'Bedrock circles persist across their refresh interval')
    hashes=json.loads(new.read('resource-packs/pack-hashes.json'))
    for name,h in hashes.items():check(hashlib.sha1(new.read('resource-packs/'+name)).hexdigest()==h,'published pack digest '+name)
    model=json.loads(new.read('modelengine/seraphon.bbmodel'));clips={c['name'] for c in model['animations']}
    for prefix in ('cast','smite','wings','verdict'):
        for stage in ('windup','active','recover'):check(prefix+'_'+stage in clips,'authored animation '+prefix+'_'+stage)
    for n in ('idle','walk','hover','spawn','transition','stun','aura'):check(n in clips,'required six-wing rig clip '+n)
# A player's entire standing footprint fits inside the advertised cyan safe circles.
for offset in range(4):
    safe=[i for i in range(12) if (i-offset)%4==0];unsafe=[i for i in range(12) if i not in safe]
    for s in safe:
        for sr in (1,2,3):
            sx,sz=math.cos(s*math.pi/6)*sr*15,math.sin(s*math.pi/6)*sr*15
            for u in unsafe:
                for ur in (1,2,3):
                    ux,uz=math.cos(u*math.pi/6)*ur*15,math.sin(u*math.pi/6)*ur*15
                    check(math.hypot(ux-sx,uz-sz)>sr*2.8+ur*3.3+.35,'cyan warning cannot overlap a damaging judgment column')
for source,h in manifest['sources'].items():check(hashlib.sha256((ROOT/source).read_bytes()).hexdigest()==h,'compiled source hash '+source)
report={'checks':checks,'artifact_sha256':hashlib.sha256((build/'evergarden.jar').read_bytes()).hexdigest(),'models_preserved':len(manifest['preserved_model_files']),'status':'PASS'}
(build/'asset-validation.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
print(f'PASS {checks} model, texture, clip, safe-petal, compatibility and pack-hash checks')
