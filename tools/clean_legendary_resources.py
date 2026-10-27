"""Remove shelved draft resources while preserving active Fire/Wind and shared runes."""
from pathlib import Path
import hashlib
import json
import os
import re
import shutil
import zipfile

ROOT=Path(__file__).resolve().parents[1]
DIST=ROOT/'advance-magic/dist'
OUT=ROOT/'output/legendary-cleanup-20261024'
VERSION=[2,30,0]
RETIRED=re.compile(r'elemental_legendary_(?:(?:water|ice|earth|dark|lightning)_[a-z_]+|(?:wind_vortex|light_mist)(?:_flat)?)(?:[./]|$)')


def retired(path):
    return bool(RETIRED.search(path))


def main():
    OUT.mkdir(parents=True,exist_ok=True)
    report={'bedrock_version':VERSION,'packs':{},'removed_runtime_art':['five draft element sets','unused wind vortex picture','unused legacy light mist'],
            'retained':'Fire/Wind native artwork and choreography, Light active artwork, weapon runes and existing Judge hand',
            'source_images':'Preserved in art directories; only runtime pack entries removed'}
    staged=[]
    try:
        for filename in ('advance-magic-java.zip','advance-magic-bedrock.mcpack'):
            path=DIST/filename
            with zipfile.ZipFile(path) as z:
                infos=z.infolist();old={i.filename:z.read(i.filename) for i in infos}
            removed=sorted(name for name in old if retired(name))
            values={name:data for name,data in old.items() if name not in removed}
            if filename.endswith('.mcpack'):
                manifest=json.loads(values['manifest.json'])
                if manifest['header']['version']>VERSION:raise RuntimeError('A newer Bedrock pack already exists')
                manifest['header']['version']=VERSION
                for module in manifest['modules']:module['version']=VERSION
                values['manifest.json']=(json.dumps(manifest,ensure_ascii=False,separators=(',',':'))+'\n').encode()
            temporary=path.with_suffix(path.suffix+'.cleanup-staged')
            with zipfile.ZipFile(temporary,'w',zipfile.ZIP_DEFLATED) as z:
                for info in infos:
                    if info.filename in values:z.writestr(info,values[info.filename])
            with zipfile.ZipFile(temporary) as z:
                if z.testzip() is not None:raise RuntimeError('Invalid staged pack')
                if set(z.namelist())!=set(values):raise RuntimeError('Staged pack entry mismatch')
                for name,data in values.items():
                    if z.read(name)!=data:raise RuntimeError('Staged resource differs: '+name)
            prefix='textures/items/scroll_' if filename.endswith('.mcpack') else 'assets/advance_magic/textures/item/scroll_'
            scrolls=[name for name in old if name.startswith(prefix) and name.endswith('.png')]
            if len(scrolls)!=58 or any(values.get(name)!=old[name] for name in scrolls):raise RuntimeError('Scroll icons would change')
            baseline=OUT/'pack-baseline';baseline.mkdir(exist_ok=True)
            if not (baseline/filename).exists():shutil.copy2(path,baseline/filename)
            original=OUT/'before/advance-magic/dist'/filename
            original=original if original.is_file() else path
            with zipfile.ZipFile(original) as baseline_pack:
                total_removed=sorted(name for name in baseline_pack.namelist() if retired(name))
            report['packs'][filename]={'before_bytes':original.stat().st_size,'after_bytes':temporary.stat().st_size,
                'saved_bytes':original.stat().st_size-temporary.stat().st_size,'removed_entries':total_removed,
                'removed_entries_count':len(total_removed),'preserved_scrolls':len(scrolls),
                'preserved_other_entries':len(values)-(1 if filename.endswith('.mcpack') else 0)}
            staged.append((path,temporary))
        for path,temporary in staged:os.replace(temporary,path)
    finally:
        for _,temporary in staged:temporary.unlink(missing_ok=True)
    hashes={name:hashlib.sha1((DIST/name).read_bytes()).hexdigest() for name in report['packs']}
    (DIST/'pack-hashes.json').write_text(json.dumps(hashes,indent=2)+'\n',encoding='utf-8')
    report['sha1']=hashes
    (OUT/'resource-cleanup.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({name:{key:value for key,value in data.items() if key!='removed_entries'} for name,data in report['packs'].items()},indent=2))


if __name__=='__main__':main()
