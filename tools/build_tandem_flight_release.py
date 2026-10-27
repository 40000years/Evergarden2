"""Scoped tandem flight release over the current, backed-up production artifacts."""
from pathlib import Path
import argparse,copy,hashlib,io,json,os,shutil,zipfile
import build_plugin_jars as build

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'output/tandem-flight-20261026'
BEFORE=OUT/'before'
RELEASE=OUT/'release'
MODULES={
    'advance-magic':('1.32.0-tandem-flight',tuple('com/example/advancemagic/item/'+n for n in ['FlyingStaffService','MagicItemMenu','WandService'])),
    'evergarden':('3.0.0-e2.145-tandem-flight',('com/example/voidscape/gui/AdminTestGui',)),
}
PACKS=build.ADVANCE_PACKS
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def owned(n,families):return any(n==f+'.class' or n.startswith(f+'$') for f in families)
def patch_zip(data,updates):
    out=io.BytesIO()
    with zipfile.ZipFile(io.BytesIO(data)) as old,zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
        for entry in old.infolist():
            z.writestr(copy.copy(entry),updates.get(entry.filename,old.read(entry.filename)))
        for n,b in updates.items():
            if n not in old.namelist():z.writestr(n,b)
    return out.getvalue()
def encoded(obj):return (json.dumps(obj,ensure_ascii=False,indent=2)+'\n').encode('utf8')
def packs():
    art=ROOT/'advance-magic/art/flying-staff';directory=RELEASE/'packs';directory.mkdir(exist_ok=True)
    for typ,filename in [('java',PACKS[0]),('bedrock',PACKS[1])]:
        old=(BEFORE/'advance-magic/dist'/filename).read_bytes()
        updates={p.relative_to(art/typ).as_posix():p.read_bytes() for p in (art/typ).rglob('*') if p.is_file() and 'flying_staff_tier2' in p.as_posix()}
        if typ=='bedrock':
            with zipfile.ZipFile(io.BytesIO(old)) as z:
                manifest=json.loads(z.read('manifest.json'));atlas=json.loads(z.read('textures/item_texture.json'))
            for section in [manifest['header'],*manifest['modules']]:section['version']=[2,34,0]
            manifest['header']['description']+=' + Aetherwing two-seat flying staff'
            updates['manifest.json']=encoded(manifest)
            atlas['texture_data']['advance_magic.flying_staff_tier2']={'textures':'textures/items/flying_staff_tier2'}
            updates['textures/item_texture.json']=encoded(atlas)
        (directory/filename).write_bytes(patch_zip(old,updates))
    mappings=json.loads((BEFORE/'advance-magic/dist/geyser-mappings.json').read_text())
    authored=json.loads((art/'geyser-mappings.json').read_text())
    for material,rows in authored['items'].items():
        mappings['items'].setdefault(material,[]).extend(row for row in rows if 'flying_staff_tier2' in row['model'])
    (directory/'geyser-mappings.json').write_bytes(encoded(mappings))
    hashes={name:hashlib.sha1((directory/name).read_bytes()).hexdigest() for name in PACKS[:2]}
    (directory/'pack-hashes.json').write_bytes(encoded(hashes))
    return {name:(directory/name).read_bytes() for name in PACKS}
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--promote',action='store_true');args=parser.parse_args()
    RELEASE.mkdir(exist_ok=True);baseline=json.loads((BEFORE/'manifest.json').read_text())
    if args.promote:
        report=json.loads((OUT/'release-build.json').read_text())
        asset_check=json.loads((OUT/'asset-validation.json').read_text())
        assert asset_check['passed']>0 and asset_check['candidate_magic_sha256']==sha(RELEASE/'advance-magic.jar')
        receipts=[(json.loads((OUT/folder/'vault-weapon-validation.json').read_text()),probe) for folder,probe in [('paper-tier2-final','FlyingStaffTierTwoChecks.java'),('paper-gems-final','StaffGemChecks.java')]]
        for receipt,probe in receipts:
            assert receipt['report'].startswith('PASS')
            assert receipt['magic_sha256']==sha(RELEASE/'advance-magic.jar') and receipt['garden_sha256']==sha(RELEASE/'evergarden.jar')
            assert receipt['probe_sha256']==sha(ROOT/'advance-magic/tests'/probe)
        controls=json.loads((OUT/'paper-controls-final/flying-staff-controls-result.json').read_text())
        assert not controls['failures'] and not controls['runtime_errors'] and not controls['sources_changed_during_run']
        flight_manifest=json.loads((OUT/'paper-controls-final/artifact-manifest.json').read_text())
        assert flight_manifest['sha256']['advance-magic.jar']==sha(RELEASE/'advance-magic.jar')
        for path,digest in report['sources'].items():assert sha(ROOT/path)==digest,path
        for n,(version,_) in MODULES.items():
            assert sha(ROOT/'dist'/f'{n}.jar')==baseline[f'dist/{n}.jar'],'Concurrent release changed '+n
            assert sha(RELEASE/f'{n}.jar')==report[n]['sha256']
        for filename in PACKS:assert sha(ROOT/'advance-magic/dist'/filename)==baseline['advance-magic/dist/'+filename]
        for n,(version,_) in MODULES.items():
            for filename in [n+'.jar',n+'-'+version+'.jar']:
                dest=ROOT/'dist'/filename;temp=dest.with_suffix('.staged');shutil.copy2(RELEASE/(n+'.jar'),temp);os.replace(temp,dest)
            meta=ROOT/n/'src/main/resources/plugin.yml';lines=meta.read_text(encoding='utf8').splitlines();lines=["version: '"+version+"'" if l.startswith('version:') else l for l in lines];meta.write_text('\n'.join(lines)+'\n',encoding='utf8')
        for filename in PACKS:shutil.copy2(RELEASE/'packs'/filename,ROOT/'advance-magic/dist'/filename)
        report['promoted']=True;(OUT/'release-build.json').write_bytes(encoded(report));print('Promoted both tested plugins and resource packs');return
    report={'promoted':False,'sources':{}}
    resources=packs()
    for name,(version,families) in MODULES.items():
        sources=[ROOT/name/'src/main/java'/(f+'.java') for f in families];base=BEFORE/'dist'/(name+'.jar')
        assert sha(base)==baseline['dist/'+name+'.jar']
        cp=([str(RELEASE/'advance-magic.jar')] if name=='evergarden' else [])+[str(base)]+build.pinned_classpath(build.ADVANCE_ARGS if name=='advance-magic' else build.EVERGARDEN_ARGS)
        build.OUT=RELEASE;classes=RELEASE/(name+'-classes');build.compile_sources(name,sources,cp,classes)
        updates={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}
        assert all(owned(n,families) for n in updates)
        with zipfile.ZipFile(base) as z:
            meta=z.read('plugin.yml').decode('utf8');lines=["version: '"+version+"'" if l.startswith('version:') else l for l in meta.splitlines()]
        updates['plugin.yml']=('\n'.join(lines)+'\n').encode()
        if name=='advance-magic':updates.update({'resource-packs/'+n:b for n,b in resources.items()})
        # No stale inner classes when the owned class families shrink.
        out=io.BytesIO()
        with zipfile.ZipFile(base) as z,zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as target:
            for entry in z.infolist():
                if not owned(entry.filename,families):target.writestr(copy.copy(entry),updates.pop(entry.filename,z.read(entry.filename)))
            for n,b in updates.items():target.writestr(n,b)
        jar=RELEASE/(name+'.jar');jar.write_bytes(out.getvalue());build.check_jar(jar)
        with zipfile.ZipFile(base) as a,zipfile.ZipFile(jar) as b:
            preserved=[n for n in a.namelist() if not owned(n,families) and n!='plugin.yml' and not(name=='advance-magic' and n.startswith('resource-packs/'))]
            assert all(a.read(n)==b.read(n) for n in preserved)
        report[name]={'sha256':sha(jar),'version':version,'preserved_entries':len(preserved)}
        report['sources'].update({p.relative_to(ROOT).as_posix():sha(p) for p in sources})
    report['sources'].update({p.relative_to(ROOT).as_posix():sha(p) for p in (ROOT/'advance-magic/art/flying-staff').rglob('*') if p.is_file() and ('flying_staff_tier2' in p.as_posix() or p.name in ['geyser-mappings.json','item_texture.json'])})
    (OUT/'release-build.json').write_bytes(encoded(report));print(json.dumps({n:report[n] for n in MODULES},indent=2))
if __name__=='__main__':main()
