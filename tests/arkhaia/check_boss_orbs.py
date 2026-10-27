"""Inspect the six final textures and both deployed pack routes without a client."""
from pathlib import Path
from io import BytesIO
import hashlib, json, zipfile
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
ids=('acheron','phlegethon','cocytus','banshee','malefeasance','styx')
dist=ROOT/'evergarden/dist';checks=0
def check(ok,label):
    global checks
    assert ok,label
    checks+=1
with zipfile.ZipFile(dist/'evergarden-java.zip') as java,zipfile.ZipFile(dist/'evergarden-bedrock.mcpack') as bedrock:
    atlas=json.loads(bedrock.read('textures/item_texture.json'))['texture_data']
    routes=json.loads(java.read('assets/minecraft/items/heart_of_the_sea.json'))['model']['cases']
    mappings=json.loads((dist/'geyser-mappings.json').read_text())['items']['minecraft:heart_of_the_sea']
    digests=set()
    for boss in ids:
        name='boss_orb_'+boss;identifier='voidscape:'+name
        source=(ROOT/'evergarden/art/boss-orbs/textures'/f'{boss}.png').read_bytes()
        check(java.read(f'assets/voidscape/textures/item/{name}.png')==source,'Java authored texture '+boss)
        check(bedrock.read(f'textures/items/{name}.png')==source,'Bedrock shares the authored texture '+boss)
        with Image.open(BytesIO(source)) as image:
            check(image.mode=='RGBA' and image.size==(256,256),'256px RGBA '+boss)
            alpha=image.getchannel('A');check(alpha.getextrema()==(0,255),'transparent margins and opaque core '+boss)
            check(image.getpixel((0,0))[3]==0,'transparent corner '+boss)
            coverage=sum(1 for a in alpha.getdata() if a>128)/65536
            check(.45<coverage<.85,'readable orb silhouette '+boss)
        digests.add(hashlib.sha256(source).hexdigest())
        model=json.loads(java.read(f'assets/voidscape/items/{name}.json'))['model']
        check(model['model']=='voidscape:item/'+name,'direct item model '+boss)
        geometry=json.loads(java.read(f'assets/voidscape/models/item/{name}.json'))
        check(geometry['textures']['layer0']=='voidscape:item/'+name,'Java model layer '+boss)
        check(any(r['when']==identifier for r in routes),'custom-model-data fallback '+boss)
        check(atlas['voidscape.'+name]['textures']=='textures/items/'+name,'Bedrock atlas '+boss)
        matches=[m for m in mappings if m.get('model')==identifier]
        check(len(matches)==1 and matches[0]['bedrock_identifier']==identifier and 'predicate' not in matches[0],'direct Geyser mapping '+boss)
    check(len(digests)==6,'six distinct authored orb textures')
    check(json.loads(bedrock.read('manifest.json'))['header']['version']==[3,38,0],'Bedrock cache version 3.38.0')
hashes=json.loads((dist/'pack-hashes.json').read_text())
for name,value in hashes.items():check(hashlib.sha1((dist/name).read_bytes()).hexdigest()==value,'fresh SHA1 '+name)
print(f'PASS {checks} boss orb pack checks')
