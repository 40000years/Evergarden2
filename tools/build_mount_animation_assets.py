"""Update only existing Aetherius, Ignis and Fenrir animation tracks; preserve all meshes/UVs."""
from pathlib import Path
import hashlib,json,sys,zipfile

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'evergarden/tools'))
import pet_model_engine as models
import pet_bedrock_assets
import export_native_pet_models

RES=ROOT/'evergarden/src/main/resources'
BEFORE=ROOT/'output/mythical-mount-refinement/before/resources'
SPECIES=('aetherius','ignis_draconis','fenrir')
def encode(value):return (json.dumps(value,ensure_ascii=False,separators=(',',':'))+'\n').encode()
def entries(path):
    with zipfile.ZipFile(path) as z:return {n:z.read(n) for n in z.namelist()}

def main():
    manifest=json.loads((BEFORE/'modelengine/pets/manifest.json').read_bytes())
    for row in manifest:
        if row['species'] not in SPECIES:continue
        name='modelengine/pets/'+row['id']+'.bbmodel'
        model=json.loads((BEFORE/name).read_bytes());bones={}
        def visit(group,parent=None):
            if group['name']=='hitbox':return
            bones[group['name']]=(parent,group['origin'])
            for child in group['children']:
                if isinstance(child,dict):visit(child,group['name'])
        for group in model['outliner']:visit(group)
        model['animations']=models.animations(model['name'],bones,row['species'])
        # Match the established Windows blueprint serializer exactly.
        (RES/name).write_text(json.dumps(model,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf8')
        row['sha256']=hashlib.sha256((RES/name).read_bytes()).hexdigest()
    (RES/'modelengine/pets/manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    export_native_pet_models.export()
    assert (RES/'native-pets.zip').read_bytes()==(BEFORE/'native-pets.zip').read_bytes(),'Mesh pack changed'
    old=json.loads((BEFORE/'native-pets.json').read_bytes())['models'];new=json.loads((RES/'native-pets.json').read_bytes())['models']
    assert all(new[n]==v for n,v in old.items() if not any(n.startswith(s+'_') for s in SPECIES))
    pack=ROOT/'evergarden/dist/evergarden-bedrock.mcpack';files=entries(BEFORE/'resource-packs/evergarden-bedrock.mcpack')
    for name,data in pet_bedrock_assets.assets().items():
        if name.startswith('animations/') and any('everpet_'+s+'_' in name for s in SPECIES):files[name]=data
    manifest=json.loads(files['manifest.json']);version=[3,68,0]
    manifest['header']['version']=version
    for module in manifest['modules']:module['version']=version
    files['manifest.json']=encode(manifest)
    with zipfile.ZipFile(pack,'w',zipfile.ZIP_DEFLATED) as z:
        for name,data in sorted(files.items()):z.writestr(zipfile.ZipInfo(name,(2026,11,1,0,0,0)),data,compress_type=zipfile.ZIP_DEFLATED)
    hashes=ROOT/'evergarden/dist/pack-hashes.json';value=json.loads(hashes.read_bytes())
    value['evergarden-bedrock.mcpack']=hashlib.sha1(pack.read_bytes()).hexdigest();hashes.write_bytes(encode(value))
    design=json.loads((BEFORE/'judge-design.json').read_bytes());design['bedrock-version']='3.68.0'
    (RES/'judge-design.json').write_bytes(encode(design))
    print('Nine animation sets updated; Java mesh pack byte-identical; Bedrock 3.68.0')
if __name__=='__main__':main()
