"""Check only tandem assets and prove the original release assets are preserved."""
from pathlib import Path
import hashlib,io,json,zipfile
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'output/tandem-flight-20261026'
BEFORE=OUT/'before/advance-magic/dist'
PACKS=OUT/'release/packs'
NAME='flying_staff_tier2'
count=0
def check(ok,label):
    global count
    assert ok,label
    count+=1
def main():
    for filename in ['advance-magic-java.zip','advance-magic-bedrock.mcpack']:
        with zipfile.ZipFile(BEFORE/filename) as old,zipfile.ZipFile(PACKS/filename) as new:
            check(new.testzip() is None,filename+' CRC')
            check(len(new.namelist())==len(set(new.namelist())),filename+' duplicate entries')
            for n in old.namelist():
                if n not in ['manifest.json','textures/item_texture.json']:check(old.read(n)==new.read(n),'preserved '+n)
            for n in new.namelist():
                if n.endswith('.json'):json.loads(new.read(n))
    with zipfile.ZipFile(PACKS/'advance-magic-java.zip') as java:
        base=json.loads(java.read(f'assets/advance_magic/models/item/{NAME}.json'))
        check(len(base['elements'])==90,'authored model has 90 cubes')
        for e in base['elements']:
            check(all(-16<=v<=32 for v in e['from']+e['to']),'Java element range')
            check(all(a<b for a,b in zip(e['from'],e['to'])),'nonempty cube')
        check(base['display']['head']['scale']==[1,1,1.5],'head length scale')
        for state,size in [('summon',7),('idle',8),('flight',8),('dismiss',5)]:
            route=json.loads(java.read(f'assets/advance_magic/items/{NAME}_{state}.json'))['model']
            check(len(route['entries'])==size,'frame count '+state)
            for entry in [route['fallback'],*[r['model'] for r in route['entries']]]:
                n=entry['model'].replace('advance_magic:','assets/advance_magic/models/')+'.json'
                frame=json.loads(java.read(n));check(frame['parent']==f'advance_magic:item/{NAME}','frame parent '+n)
                check(len(frame['display']['head']['scale'])==3,'frame scale '+n)
    with zipfile.ZipFile(PACKS/'advance-magic-bedrock.mcpack') as bed,zipfile.ZipFile(BEFORE/'advance-magic-bedrock.mcpack') as old:
        old_manifest=json.loads(old.read('manifest.json'));manifest=json.loads(bed.read('manifest.json'))
        check(manifest['header']['uuid']==old_manifest['header']['uuid'],'stable pack UUID')
        check(manifest['header']['version']>old_manifest['header']['version'],'cache invalidation version')
        old_atlas=json.loads(old.read('textures/item_texture.json'))['texture_data'];atlas=json.loads(bed.read('textures/item_texture.json'))['texture_data']
        check(all(atlas.get(k)==v for k,v in old_atlas.items()),'preserve all existing Bedrock atlas routes')
        for suffix in ['', '_summon','_idle','_flight','_dismiss']:
            attach=json.loads(bed.read(f'attachables/{NAME}{suffix}.json'))['minecraft:attachable']['description']
            check(attach['identifier']==f'advance_magic:{NAME}{suffix}','attachable identity')
            mode='display' if suffix else 'held';geo=json.loads(bed.read(f'models/entity/{NAME}_{mode}.geo.json'))['minecraft:geometry'][0]
            check(attach['geometry']['default']==geo['description']['identifier'],'geometry route')
            check(len(next(b for b in geo['bones'] if b['name']=='staff_root')['cubes'])==90,'geometry cube parity')
            check(attach['textures']['default']==f'textures/items/{NAME}_palette','texture route')
            for animation in attach.get('animations',{}).values():
                check(animation in json.loads(bed.read('animations/flying_staff.animation.json'))['animations'],'animation resolution')
        java_palette=Image.open(io.BytesIO(java_palette_bytes()))
        bed_palette=Image.open(io.BytesIO(bed.read(f'textures/items/{NAME}_palette.png')))
        check(java_palette.tobytes()==bed_palette.tobytes(),'identical Java/Bedrock palette')
    mappings=json.loads((PACKS/'geyser-mappings.json').read_text());old=json.loads((BEFORE/'geyser-mappings.json').read_text());added=[]
    for material,rows in old['items'].items():check(all(r in mappings['items'][material] for r in rows),'preserved mapping '+material)
    for material,rows in mappings['items'].items():
        added.extend(r for r in rows if NAME in r['model'])
        check(len([r['model'] for r in rows])==len(set(r['model'] for r in rows)),'unique model mapping '+material)
    check(len(added)==5,'five new Geyser definitions')
    hashes=json.loads((PACKS/'pack-hashes.json').read_text())
    for filename,digest in hashes.items():check(hashlib.sha1((PACKS/filename).read_bytes()).hexdigest()==digest,'pack hash '+filename)
    with zipfile.ZipFile(OUT/'release/advance-magic.jar') as jar:
        for filename in ['advance-magic-java.zip','advance-magic-bedrock.mcpack','geyser-mappings.json','pack-hashes.json']:
            check(jar.read('resource-packs/'+filename)==(PACKS/filename).read_bytes(),'embedded '+filename)
    report={'passed':count,'new_cubes':90,'new_geyser_definitions':5,'bedrock_pack_version':[2,34,0],
            'candidate_magic_sha256':hashlib.sha256((OUT/'release/advance-magic.jar').read_bytes()).hexdigest()}
    (OUT/'asset-validation.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
def java_palette_bytes():
    with zipfile.ZipFile(PACKS/'advance-magic-java.zip') as z:return z.read(f'assets/advance_magic/textures/item/{NAME}_palette.png')
if __name__=='__main__':main()
