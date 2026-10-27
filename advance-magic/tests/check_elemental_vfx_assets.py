"""Check the exact client assets embedded in the elemental VFX release."""
from pathlib import Path
import argparse
import hashlib
import io
import json
import re
import sys
import zipfile
from PIL import Image

ROOT=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--plugin-jar',type=Path,default=ROOT/'dist/advance-magic-1.16.0-rare-spell-vfx.jar')
args=parser.parse_args()
sys.path.insert(0,str(ROOT/'tools'))
from bedrock_particle_checks import check_direction_modes

checks=0
def check(value,label):
    global checks
    if not value:raise AssertionError(label)
    checks+=1

java_path=ROOT/'advance-magic/dist/advance-magic-java.zip'
bedrock_path=ROOT/'advance-magic/dist/advance-magic-bedrock.mcpack'
check_direction_modes(bedrock_path)
manifest=json.loads((ROOT/'advance-magic/art/elemental-vfx/manifest.json').read_text(encoding='utf8'))
source=(ROOT/'advance-magic/src/main/java/com/example/advancemagic/elemental/ElementalSpell.java').read_text(encoding='utf8')
ids={n.lower() for n in re.findall(r'^\s*(\w+)\("[^"]+",Element\.',source,re.M)}
check(len(ids)==58 and ids==set(manifest['spells']),'all 58 live catalog spells have authored VFX')
asset_ids=[asset['id'] for asset in manifest['assets']]
check(len(asset_ids)>=98 and len(set(asset_ids))==len(asset_ids),'at least 98 assets with unique IDs')
check({'elemental_'+spell for spell in ids}.issubset(asset_ids),'all 58 spell signatures remain present')
check({'elemental_'+element+'_'+part for element in ('fire','water','ice','wind','earth','light','dark','lightning')
       for part in ('head','trail','burst','mark','facet')}.issubset(asset_ids),'all 40 shared elemental components remain present')
ultimates={name.lower() for name in re.findall(r'^\s*(\w+)\("[^"\n]+",Element\.\w+,[^\n]+Rarity\.LEGENDARY,true,',source,re.M)}
check(len(ultimates)==10 and {'elemental_rare_'+spell for spell in ultimates}.issubset(asset_ids),'all 10 Ultimate heroes resolve to authored artwork')
accents={'fire':'plume','water':'crest','ice':'shards','wind':'ribbon','earth':'fragments','light':'rays','dark':'fragments','lightning':'forks'}
check({'elemental_rare_'+element+'_'+part for element,part in accents.items()}.issubset(asset_ids),'all eight moving high-tier accents resolve')
check({'elemental_rare_'+element+'_ground' for element in accents}.issubset(asset_ids),'all eight high-tier ground effects resolve')
with zipfile.ZipFile(java_path) as java,zipfile.ZipFile(bedrock_path) as bedrock:
    for asset in manifest['assets']:
        name=asset['id'];key='advance_magic:'+name
        item=json.loads(java.read(f'assets/advance_magic/items/{name}.json'))
        model_key=item['model']['model'];ns,path=model_key.split(':',1)
        check(f'assets/{ns}/models/{path}.json' in java.namelist(),'item resolves model '+key)
        model=json.loads(java.read(f'assets/{ns}/models/{path}.json'))
        texture_key=model['textures']['art'];ns,path=texture_key.split(':',1)
        png=java.read(f'assets/{ns}/textures/{path}.png')
        check(png==bedrock.read(f'textures/particle/{name}.png'),'identical source artwork on Java/Bedrock '+name)
        with Image.open(io.BytesIO(png)) as im:
            check(im.mode=='RGBA' and im.size==(256,256),'RGBA 256px '+name)
            alpha=im.getchannel('A');check(alpha.getextrema()[0]==0 and alpha.getextrema()[1]>100,'transparent background and visible strokes '+name)
        particle=json.loads(bedrock.read(f'particles/{name}.particle.json'))['particle_effect']
        check(particle['description']['identifier']==key,'correct Bedrock identifier '+key)
        check(particle['description']['basic_render_parameters']['material']=='particles_blend','double-sided translucent material '+name)
        shape=particle['components']['minecraft:emitter_shape_point']['offset']
        check(shape==['variable.line_offset_x','variable.line_offset_y','variable.line_offset_z'],'viewer-near emitter retains world offset '+name)
        if not name.endswith('_trail'):
            flat=json.loads(bedrock.read(f'particles/{name}_flat.particle.json'))['particle_effect']
            check(flat['components']['minecraft:particle_appearance_billboard']['facing_camera_mode']=='emitter_transform_xz','flat plane has nonsingular XZ basis '+name)
        for face in model['elements'][0]['faces'].values():check(face['texture']=='#art','valid model texture alias '+name)
    bedrock_manifest=json.loads(bedrock.read('manifest.json'))
    check(bedrock_manifest['header']['version']==[2,17,0],'Bedrock cache version increased')
    check(all(module['version']==[2,17,0] for module in bedrock_manifest['modules']),'all Bedrock modules use the new cache version')
with zipfile.ZipFile(args.plugin_jar) as jar:
    for path in (java_path,bedrock_path):check(jar.read('resource-packs/'+path.name)==path.read_bytes(),'JAR embeds exact validated '+path.name)
hashes=json.loads((ROOT/'advance-magic/dist/pack-hashes.json').read_text(encoding='utf8'))
for path in (java_path,bedrock_path):check(hashlib.sha1(path.read_bytes()).hexdigest()==hashes[path.name],'advertised SHA-1 matches '+path.name)
print('PASS',checks,'elemental VFX asset references, cross-client textures and embedded packs')
