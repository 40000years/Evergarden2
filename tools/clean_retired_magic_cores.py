"""Remove only the 21 retired crafting-core resources from the delivered packs, without rebuilding live art."""
from __future__ import annotations
import copy
import hashlib
import json
import os
from pathlib import Path
import zipfile

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'output/retired-core-cleanup-20261025'
BEFORE=OUT/'before'
SUFFIXES=('lightning_strike','frost_nova','shadow_step','natures_bloom','earth_wall','dragons_breath',
          'void_pull','sonic_boom','blaze_barrage','wither_ray','shulker_levitation','meteor_strike',
          'iron_armor','vex_legion','guardian_beam','solar_apocalypse','chronos_final_hour',
          'heavens_judgment','verdant_sovereign','lunar_requiem','crimson_oblivion')
MODELS={'advance_magic:core_'+name for name in SUFFIXES}
ICONS={'advance_magic.core_'+name for name in SUFFIXES}
VERSIONS={'advance-magic':[2,33,1],'evergarden':[3,57,1]}

def sha(data):return hashlib.sha256(data).hexdigest()
def encode(value):return (json.dumps(value,ensure_ascii=False,separators=(',',':'))+'\n').encode('utf8')
def retired_paths(bedrock):
    if bedrock:return {'textures/items/core_'+name+'.png' for name in SUFFIXES}
    return {'assets/advance_magic/'+folder+'/core_'+name+'.'+extension
            for name in SUFFIXES for folder,extension in (('items','json'),('models/item','json'),('textures/item','png'))}

def clean_pack(module,name,baseline):
    with zipfile.ZipFile(baseline) as z:
        infos=z.infolist();old={entry.filename:z.read(entry.filename) for entry in infos}
    bedrock=name.endswith('.mcpack');removed=retired_paths(bedrock)
    if not removed.issubset(old):raise RuntimeError('Baseline retired resources differ: '+name)
    values={key:data for key,data in old.items() if key not in removed};changed=[]
    if bedrock:
        atlas=json.loads(values['textures/item_texture.json']);textures=atlas['texture_data']
        if not ICONS.issubset(textures):raise RuntimeError('Missing baseline core atlas entries')
        atlas['texture_data']={key:value for key,value in textures.items() if key not in ICONS}
        values['textures/item_texture.json']=encode(atlas);changed.append('textures/item_texture.json')
        manifest=json.loads(values['manifest.json']);version=VERSIONS[module]
        if manifest['header']['version']>=version:raise RuntimeError('Version must advance: '+name)
        manifest['header']['version']=version
        for part in manifest['modules']:part['version']=version
        values['manifest.json']=encode(manifest);changed.append('manifest.json')
    selector='assets/minecraft/items/heart_of_the_sea.json'
    if selector in values:
        data=json.loads(values[selector]);model=data['model']
        cases=model.get('cases',[]);kept=[];count=0
        for case in cases:
            when=case.get('when');matches=when if isinstance(when,list) else [when]
            remaining=[value for value in matches if value not in MODELS]
            count+=len(matches)-len(remaining)
            if remaining:
                row=dict(case);row['when']=remaining if isinstance(when,list) else remaining[0];kept.append(row)
        if count:
            if count!=21:raise RuntimeError('Unexpected selector core count')
            if kept:model['cases']=kept
            else:data['model']=model['fallback']
            values[selector]=encode(data);changed.append(selector)
    staged=OUT/'packs'/module/name;staged.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(staged,'w',zipfile.ZIP_DEFLATED) as z:
        for info in infos:
            if info.filename in values:z.writestr(copy.copy(info),values[info.filename])
    with zipfile.ZipFile(staged) as z:
        if z.testzip() or len(z.namelist())!=len(set(z.namelist())):raise RuntimeError('Invalid staged archive')
        if set(z.namelist())!=set(values):raise RuntimeError('Staged paths differ')
        for key,data in values.items():
            if z.read(key)!=data:raise RuntimeError('Staged resource differs: '+key)
            if key not in changed and data!=old[key]:raise RuntimeError('Unrelated resource changed: '+key)
            if key.endswith(('.json','.lang')) and any(model.encode() in data for model in MODELS):
                raise RuntimeError('Dangling retired model: '+key)
    return staged,{'removed':sorted(removed),'modified':changed,'preserved_entries':len(values)-len(changed),
                   'sha256':sha(staged.read_bytes()),'baseline_sha256':sha(baseline.read_bytes())}

def main():
    manifest=json.loads((BEFORE/'manifest.json').read_text(encoding='utf8'))
    report={'retired_models':sorted(MODELS),'bedrock_versions':VERSIONS,'packs':{},'mappings':{},'files':{}}
    staged=[]
    for module in VERSIONS:
        hashes=json.loads((BEFORE/module/'dist/pack-hashes.json').read_text(encoding='utf8'))
        for name in (module+'-java.zip',module+'-bedrock.mcpack'):
            relative=f'{module}/dist/{name}';target=ROOT/relative
            if sha(target.read_bytes())!=manifest[relative]:raise RuntimeError('Current pack changed: '+relative)
            path,details=clean_pack(module,name,BEFORE/relative)
            report['packs'][relative]=details;staged.append((target,path));hashes[name]=hashlib.sha1(path.read_bytes()).hexdigest()
        relative=f'{module}/dist/geyser-mappings.json';target=ROOT/relative
        if sha(target.read_bytes())!=manifest[relative]:raise RuntimeError('Current mappings changed: '+relative)
        mapping=json.loads((BEFORE/relative).read_bytes());removed=[]
        for material,rows in list(mapping['items'].items()):
            kept=[]
            for row in rows:
                if row.get('model') in MODELS or row.get('bedrock_identifier') in MODELS:removed.append(row)
                else:kept.append(row)
            if kept:mapping['items'][material]=kept
            else:del mapping['items'][material]
        if len(removed)!=(21 if module=='advance-magic' else 0):raise RuntimeError('Unexpected retired mapping count')
        path=OUT/'packs'/module/'geyser-mappings.json'
        # Preserve even JSON formatting where no definitions needed removal.
        path.write_bytes(encode(mapping) if removed else (BEFORE/relative).read_bytes());staged.append((target,path))
        report['mappings'][relative]={'removed_count':len(removed),'sha256':sha(path.read_bytes())}
        relative=f'{module}/dist/pack-hashes.json';target=ROOT/relative
        if sha(target.read_bytes())!=manifest[relative]:raise RuntimeError('Current hashes changed: '+relative)
        path=OUT/'packs'/module/'pack-hashes.json';path.write_bytes(encode(hashes));staged.append((target,path))
    # Recheck every target before installing any staged file, preserving concurrent releases.
    for target,path in staged:
        relative=target.relative_to(ROOT).as_posix()
        if sha(target.read_bytes())!=manifest[relative]:raise RuntimeError('Concurrent artifact change: '+relative)
        report['files'][relative]={'baseline_sha256':manifest[relative],'sha256':sha(path.read_bytes())}
    for target,path in staged:
        temporary=target.with_suffix(target.suffix+'.core-cleanup-staged');temporary.write_bytes(path.read_bytes());os.replace(temporary,target)
    (OUT/'resource-cleanup.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'removed_core_types':len(MODELS),'pack_versions':VERSIONS,
        'removed_entries':{name:len(row['removed']) for name,row in report['packs'].items()},
        'removed_geyser_definitions':{name:row['removed_count'] for name,row in report['mappings'].items()}},indent=2))

if __name__=='__main__':main()
