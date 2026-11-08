"""Scoped release: 57 companions, three rarity tiers, 171 new authored forms and powers."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import zipfile
import build_plugin_jars as build

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'output/pet-expansion-release'
RES=ROOT/'evergarden/src/main/resources'
VERSION='3.0.0-e2.198-pet-collection'
FAMILIES=tuple('com/example/voidscape/pet/'+name for name in
               ('PetCatalog','PetExpansion','PetSkills','PetRecord','PetTaming','WildPetSpawns','PetMenu'))
DATE=(2026,11,8,0,0,0)


def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def entries(data):
    with zipfile.ZipFile(io.BytesIO(data)) as archive:return {n:archive.read(n) for n in archive.namelist()}


def snapshot():
    before=OUT/'before';before.mkdir(parents=True,exist_ok=True)
    for name in ('evergarden.jar','advance-magic.jar'):
        target=before/name
        if not target.exists():shutil.copy2(ROOT/'dist'/name,target)
        if sha(target)!=sha(ROOT/'dist'/name):raise RuntimeError('Delivery baseline changed: '+name)
    return before


def compile_java(before):
    build.OUT=OUT
    sources=[ROOT/'evergarden/src/main/java'/(name+'.java') for name in FAMILIES]
    build.compile_sources('pet-collection',sources,[str(before/'evergarden.jar'),str(before/'advance-magic.jar')]
                          +build.pinned_classpath(build.EVERGARDEN_ARGS),OUT/'classes')
    return sources


def resources():
    values={name:RES/name for name in ('config.yml','pet-expansion.json','native-pets.json','native-pets.zip',
             'pet-portraits.zip','judge-design.json','modelengine/pets/manifest.json')}
    for name in ('evergarden-bedrock.mcpack','geyser-mappings.json','pack-hashes.json'):
        values['resource-packs/'+name]=ROOT/'evergarden/dist'/name
    roster=json.loads((RES/'pet-expansion.json').read_text(encoding='utf8'))['species']
    for entry in roster:
        for stage in ('baby','medium','adult'):
            name=f'modelengine/pets/everpet_{entry["id"]}_{stage}.bbmodel';values[name]=RES/name
    return values


def owned(name):return any(name==f+'.class' or name.startswith(f+'$') for f in FAMILIES)


def verify_assets(previous,values):
    roster=json.loads(values['pet-expansion.json'].read_bytes())['species']
    assert len(roster)==57 and sum(e['rarity']=='rare' for e in roster)==17
    for name in ('native-pets.zip','pet-portraits.zip'):
        old,new=entries(previous.read(name)),entries(values[name].read_bytes())
        assert all(new.get(n)==data for n,data in old.items()),name+' changed an existing entry'
    old=json.loads(previous.read('native-pets.json'))['models'];new=json.loads(values['native-pets.json'].read_bytes())['models']
    assert len(new)==282 and all(new.get(n)==data for n,data in old.items())
    old_pack=entries(previous.read('resource-packs/evergarden-bedrock.mcpack'))
    new_pack=entries(values['resource-packs/evergarden-bedrock.mcpack'].read_bytes())
    assert all(new_pack.get(n)==data for n,data in old_pack.items() if n not in ('manifest.json','textures/item_texture.json'))
    assert sum(n.startswith('entity/everpet_') for n in new_pack)==282
    with zipfile.ZipFile(values['pet-portraits.zip']) as portraits:
        assert sum(n.startswith('assets/evergarden/items/menu_pet_') for n in portraits.namelist())==564
    hashes=json.loads(values['resource-packs/pack-hashes.json'].read_bytes())
    for name,digest in hashes.items():assert hashlib.sha1((ROOT/'evergarden/dist'/name).read_bytes()).hexdigest()==digest


def candidate(before):
    sources=compile_java(before);values=resources()
    hashes={p.relative_to(ROOT).as_posix():sha(p) for p in sources+list(values.values())}
    classes={p.relative_to(OUT/'classes').as_posix():p.read_bytes() for p in (OUT/'classes').rglob('*.class')}
    assert classes and all(owned(name) for name in classes)
    target=OUT/'evergarden.jar'
    with zipfile.ZipFile(before/'evergarden.jar') as old,zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED) as new:
        verify_assets(old,values)
        plugin,count=re.subn(r'(?m)^version:[^\r\n]*',"version: '"+VERSION+"'",old.read('plugin.yml').decode('utf8'));assert count==1
        for info in old.infolist():
            if not owned(info.filename) and info.filename not in values and info.filename!='plugin.yml':new.writestr(info,old.read(info.filename))
        replacements={'plugin.yml':plugin.encode('utf8'),**classes,**{n:p.read_bytes() for n,p in values.items()}}
        for name,data in sorted(replacements.items()):new.writestr(zipfile.ZipInfo(name,DATE),data,compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(before/'evergarden.jar') as old,zipfile.ZipFile(target) as new:
        assert new.testzip() is None and len(new.namelist())==len(set(new.namelist()))
        preserved=[n for n in old.namelist() if n not in replacements and not owned(n)]
        assert all(new.read(n)==old.read(n) for n in preserved)
    assert all(sha(ROOT/p)==h for p,h in hashes.items()),'Inputs changed during packaging'
    build.check_jar(target)
    report=dict(version=VERSION,candidate_sha256=sha(target),baseline_sha256=sha(before/'evergarden.jar'),
                magic_sha256=sha(before/'advance-magic.jar'),sources=hashes,preserved_entries=len(preserved),promoted=False)
    (OUT/'build-report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
    print(f'Built {VERSION}; {len(preserved)} unrelated entries preserved; {target.stat().st_size:,} bytes')


def promote():
    report=json.loads((OUT/'build-report.json').read_text(encoding='utf8'))
    receipt=json.loads((OUT/'paper/pet-grounding-validation.json').read_text(encoding='utf8'))
    assert receipt['report'].startswith('PASS ')
    assert sha(OUT/'evergarden.jar')==report['candidate_sha256']==receipt['garden_sha256']
    assert sha(ROOT/'dist/evergarden.jar')==report['baseline_sha256']
    assert sha(ROOT/'dist/advance-magic.jar')==report['magic_sha256']==receipt['magic_sha256']
    assert receipt['probe_sha256']==sha(ROOT/'evergarden/tests/PetExpansionChecks.java')
    assert all(sha(ROOT/p)==h for p,h in report['sources'].items()),'Inputs changed after validation'
    for path in (ROOT/'dist/evergarden.jar',ROOT/'dist'/f'evergarden-{VERSION}.jar'):
        staged=path.with_suffix('.jar.staged');shutil.copy2(OUT/'evergarden.jar',staged);os.replace(staged,path)
    plugin=RES/'plugin.yml';plugin.write_text(re.sub(r'(?m)^version:[^\r\n]*',"version: '"+VERSION+"'",plugin.read_text(encoding='utf8')),encoding='utf8')
    report.update(promoted=True,validation=receipt)
    (OUT/'build-report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
    print('Promoted '+VERSION)


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--compile-only',action='store_true');parser.add_argument('--promote',action='store_true');args=parser.parse_args()
    OUT.mkdir(parents=True,exist_ok=True)
    if args.promote:promote();return
    before=snapshot()
    compile_java(before) if args.compile_only else candidate(before)


if __name__=='__main__':main()
