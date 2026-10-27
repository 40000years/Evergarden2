"""Validate complete, editable, non-duplicated pet rigs and growth forms."""
import base64
import hashlib
import io
import json
import math
import sys
from pathlib import Path
from PIL import Image

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'evergarden/tools'))
from pet_model_engine import SPECIES,STAGES,MYTHICAL,build_model
FOLDER=ROOT/'evergarden/src/main/resources/modelengine/pets'
checks=0


def check(ok,label):
    global checks
    assert ok,label
    checks+=1


def main():
    manifest=json.loads((FOLDER/'manifest.json').read_text(encoding='utf8'))
    # The mythical five keep their species ids but ship no blueprints until the legendary tier opens.
    total=(len(SPECIES)-len(MYTHICAL))*len(STAGES)
    check(len(manifest)==total==96,'manifest has every obtainable species and growth stage')
    check(len(list(FOLDER.glob('*.bbmodel')))==total,'no missing or unexpected blueprint')
    check(not any(entry['species'] in MYTHICAL for entry in manifest)
          and not any(path.name.startswith(tuple(f'everpet_{s}_' for s in MYTHICAL)) for path in FOLDER.glob('*.bbmodel')),
          'no mythical blueprint is shipped')
    hashes=set();silhouettes={}
    for entry in manifest:
        path=FOLDER/(entry['id']+'.bbmodel');raw=path.read_bytes();model=json.loads(raw)
        check(hashlib.sha256(raw).hexdigest()==entry['sha256'],entry['id']+' manifest hash')
        check(model['model_identifier']==entry['id']==model['name'],'model identifier agrees')
        check(model['meta']['evergarden_pet_species']==entry['species'],'species metadata')
        check(model['meta']['evergarden_pet_stage']==entry['stage'],'stage metadata')
        check(model['meta']['evergarden_animation_position_x']=='inverted','R4 translation convention')
        check(entry['sha256'] not in hashes,'not a duplicate blueprint');hashes.add(entry['sha256'])
        texture=Image.open(io.BytesIO(base64.b64decode(model['textures'][0]['source'].split(',')[1])))
        check(texture.size==(512,512),'embedded atlas correct size')
        check(len(texture.getcolors(512*512))>16,'painted material atlas')
        elements={e['uuid']:e for e in model['elements']};owned=[];bones={};names=set()
        check(len(elements)==len(model['elements']),'unique element UUIDs')
        def visit(group,ancestors=()):
            check(group['uuid'] not in ancestors,'acyclic hierarchy')
            check(group['name'] not in names,'unique bone names');names.add(group['name']);bones[group['uuid']]=group
            check(all(math.isfinite(float(v)) for v in group.get('origin',[0,0,0])),'finite bone pivot')
            for child in group['children']:
                if isinstance(child,dict):visit(child,ancestors+(group['uuid'],))
                else:check(child in elements,'bone references existing cube');owned.append(child)
        for group in model['outliner']:visit(group)
        check(len(owned)==len(set(owned))==len(elements),'each cube belongs to exactly one bone')
        visual=[e for e in elements.values() if e['name']!='hitbox']
        check(len(visual)>=35,'substantial sculpted form')
        for e in visual:
            check(all(math.isfinite(v) for field in ('from','to','origin','rotation') for v in e[field]),'finite cube transform')
            check(all(b>a for a,b in zip(e['from'],e['to'])),'positive cube dimensions')
            check(set(e['faces'])=={'north','south','east','west','up','down'},'six textured faces')
            for face in e['faces'].values():check(face['texture']==0 and all(0<=v<=512 for v in face['uv']),'valid atlas UV')
        check({a['name'] for a in model['animations']}=={'idle','walk','fly','sit','greet'},'five preview clips')
        for clip in model['animations']:
            check(clip['override'],'full skeleton override avoids additive pose drift')
            check(len(clip['animators'])==len(bones)-1,'all visible bones animated')
            changed=False
            for key,animator in clip['animators'].items():
                check(key in bones,'animation references existing bone')
                times=[f['time'] for f in animator['keyframes']]
                check(min(times)==0 and max(times)==clip['length'],'clip boundary keys')
                for f in animator['keyframes']:
                    check(0<=f['time']<=clip['length'],'key inside clip')
                    vals=[float(v) for v in f['data_points'][0].values()]
                    check(all(math.isfinite(v) for v in vals),'finite animation transform')
                    changed|=any(abs(v)>.001 for v in vals)
            check(changed,'animation has real skeletal movement')
        silhouettes[(entry['species'],entry['stage'])]=(len(visual),visual)
        regenerated,_=build_model(entry['species'],entry['stage'])
        check(regenerated==model,'reproducible source matches shipped blueprint')
        if entry['species']=='arcane_nine_tails':
            check(len([n for n in names if n.startswith('tail') and not any(s in n for s in ('tip','end'))])>=9,'nine distinct articulated tails')
        if entry['species']=='crimson_cerberus':check(len([n for n in names if n.startswith('head')])>=3,'three separate heads')
    for species in (s for s in SPECIES if s not in MYTHICAL):
        forms=[silhouettes[(species,s)] for s in STAGES]
        # Scaling alone would preserve all cube dimensions divided by head width.
        signatures=[]
        for count,parts in forms:
            extent=max(e['to'][1] for e in parts)
            signatures.append([(e['name'],tuple(round((b-a)/extent,3) for a,b in zip(e['from'],e['to']))) for e in parts])
        check(signatures[0]!=signatures[1]!=signatures[2],species+' has authored growth proportions')
    report=f'PASS: {checks} pet asset checks / {total} original models / {total*5} skeletal clips'
    print(report)
    output=ROOT/'output/pet-models-asset-checks.txt';output.parent.mkdir(exist_ok=True);output.write_text(report+'\n',encoding='utf8')


if __name__=='__main__':main()
