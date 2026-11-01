"""Check every Forge Java model, Geyser item definition and identical Bedrock texture."""
import hashlib,io,json,re,sys,zipfile
from pathlib import Path
from PIL import Image

ROOT=Path(__file__).resolve().parents[2]
jar=Path(sys.argv[1]) if len(sys.argv)>1 else ROOT/'output/forge-build/evergarden-forge.jar'
with zipfile.ZipFile(jar) as z:
    version=[int(v) for v in re.search(r"version: '(\d+)\.(\d+)\.(\d+)",z.read('plugin.yml').decode()).groups()]
    java_bytes=z.read('resource-packs/forge-java.zip');bedrock_bytes=z.read('resource-packs/forge-bedrock.mcpack')
    hashes=json.loads(z.read('resource-packs/pack-hashes.json'));maps=json.loads(z.read('resource-packs/geyser-mappings.json'))
assert hashlib.sha1(java_bytes).hexdigest()==hashes['forge-java.zip']
assert hashlib.sha1(bedrock_bytes).hexdigest()==hashes['forge-bedrock.mcpack']
models=set();images=set()
with zipfile.ZipFile(io.BytesIO(java_bytes)) as java,zipfile.ZipFile(io.BytesIO(bedrock_bytes)) as bedrock:
    assert java.testzip() is None and bedrock.testzip() is None
    atlas=json.loads(bedrock.read('textures/item_texture.json'))['texture_data']
    manifest=json.loads(bedrock.read('manifest.json'));assert manifest['header']['version']==version
    assert manifest['modules'][0]['version']==version
    for base,items in maps['items'].items():
        assert base.startswith('minecraft:')
        for row in items:
            key=row['model'];assert key.startswith('evergarden_forge:') and key not in models;models.add(key)
            path=key.split(':',1)[1]
            model=json.loads(java.read('assets/evergarden_forge/items/'+path+'.json'))['model']
            assert model=={'type':'minecraft:model','model':'evergarden_forge:item/'+path}
            item=json.loads(java.read('assets/evergarden_forge/models/item/'+path+'.json'))
            assert item['parent']=='minecraft:item/generated' and item['textures']['layer0']=='evergarden_forge:item/'+path
            png=java.read('assets/evergarden_forge/textures/item/'+path+'.png')
            icon=row['bedrock_options']['icon'];tex=atlas[icon]['textures']
            assert png==bedrock.read(tex+'.png')
            image=Image.open(io.BytesIO(png)).convert('RGBA');assert image.size==((64,64) if version>=[0,5,1] else (32,32))
            alpha=image.getextrema()[3];assert alpha[0]==0 and alpha[1]>=240 and image.getbbox() is not None
            if version>=[0,5,1]:
                left,top,right,bottom=image.getbbox()
                assert 0<left<right<64 and 0<top<bottom<64,'Clipped item: '+key
            images.add(hashlib.sha256(png).hexdigest())
    assert len(models)==len(atlas)==len(images)==46
    expected={f'evergarden_forge:gem/{kind}_{tier}' for kind in ('fire','water','wind','earth','lightning','light','dark','life') for tier in range(1,6)}
    expected|={'evergarden_forge:material/'+kind for kind in ('common','uncommon','rare','epic','mythic')};expected.add('evergarden_forge:gem_dust')
    assert models==expected
print('PASS: 46 unique original icons; all Java models and Geyser mappings resolve; Java/Bedrock pixels match; ZIP SHA-1s match')
