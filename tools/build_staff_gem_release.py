"""Build the paired, scoped two-socket staff Gem release; preserve all other baseline entries."""
from pathlib import Path
import argparse, copy, hashlib, json, os, shutil, zipfile
import build_plugin_jars as build

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'output/staff-gems-20261026'
MODULES={
    'advance-magic': ('1.31.0-staff-gems','advance-magic',tuple('com/example/advancemagic/'+name for name in (
        'elemental/StaffGemService','elemental/StaffSpellService','elemental/SpellMenu','item/WandService','item/MagicItemMenu'))),
    'evergarden': ('3.0.0-e2.141-staff-gems','evergarden',('com/example/voidscape/world/WhaleTreasure',
        'com/example/voidscape/item/RelicService','com/example/voidscape/item/VaultLootTable')),
}
GEM_VALIDATION='paper-gems-v2'
REGRESSIONS=[('paper-levels','advance-magic/tests/SpellLevelChecks.java'),('paper-vault','evergarden/tests/VaultWeaponChecks.java')]

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def owned(name,families):return any(name==family+'.class' or name.startswith(family+'$') for family in families)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--promote',action='store_true');args=parser.parse_args()
    before=OUT/'before';manifest=json.loads((before/'manifest.json').read_text(encoding='utf8'))
    release=OUT/'release';release.mkdir(exist_ok=True)
    if args.promote:
        report=json.loads((OUT/'release-build.json').read_text(encoding='utf8'))
        receipt=json.loads((OUT/GEM_VALIDATION/'vault-weapon-validation.json').read_text(encoding='utf8'))
        if not receipt['report'].startswith('PASS') or receipt['probe_sha256']!=sha(ROOT/'advance-magic/tests/StaffGemChecks.java'):
            raise RuntimeError('Missing matching Paper Gem validation')
        for key,name in [('magic_sha256','advance-magic'),('garden_sha256','evergarden')]:
            jar=release/(name+'.jar')
            if sha(jar)!=report[name]['sha256'] or receipt[key]!=sha(jar):raise RuntimeError('Candidate changed after testing: '+name)
            if sha(ROOT/'dist'/(name+'.jar'))!=manifest['dist/'+name+'.jar']:raise RuntimeError('Another release replaced dist; rebase first: '+name)
        for folder,probe in REGRESSIONS:
            checked=json.loads((OUT/folder/'vault-weapon-validation.json').read_text(encoding='utf8'))
            if (not checked['report'].startswith('PASS') or checked['probe_sha256']!=sha(ROOT/probe)
                or checked['magic_sha256']!=report['advance-magic']['sha256'] or checked['garden_sha256']!=report['evergarden']['sha256']):
                raise RuntimeError('Missing final candidate regression validation: '+folder)
        for source,digest in report['sources'].items():
            if sha(ROOT/source)!=digest:raise RuntimeError('Source changed after build: '+source)
        for name,(version,module,_) in MODULES.items():
            for target in [ROOT/'dist'/(name+'.jar'),ROOT/'dist'/(name+'-'+version+'.jar')]:
                staged=target.with_suffix('.jar.staged');shutil.copy2(release/(name+'.jar'),staged);os.replace(staged,target)
            metadata=ROOT/module/'src/main/resources/plugin.yml'
            lines=metadata.read_bytes().decode('utf8').splitlines(keepends=True)
            index=next(i for i,line in enumerate(lines) if line.startswith('version:'))
            ending=lines[index][len(lines[index].rstrip('\r\n')):];lines[index]=f"version: '{version}'"+ending
            metadata.write_bytes(''.join(lines).encode('utf8'))
        report['promoted']=True;(OUT/'release-build.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
        print('Promoted both tested JARs');return
    report={'promoted':False,'sources':{}}
    for name,(version,module,families) in MODULES.items():
        base=before/'dist'/(name+'.jar')
        if sha(base)!=manifest['dist/'+name+'.jar']:raise RuntimeError('Baseline backup changed')
        sources=[ROOT/module/'src/main/java'/(family+'.java') for family in families]
        classes=release/(name+'-classes');build.OUT=release
        classpath=[str(base)]
        if name=='evergarden':classpath+=[str(release/'advance-magic.jar')]
        classpath+=build.pinned_classpath(build.ADVANCE_ARGS if name=='advance-magic' else build.EVERGARDEN_ARGS)
        build.compile_sources('staff-gems-'+name,sources,classpath,classes)
        metadata=ROOT/module/'src/main/resources/plugin.yml'
        with zipfile.ZipFile(base) as previous:
            lines=previous.read('plugin.yml').decode('utf8').splitlines(keepends=True)
        index=next(i for i,line in enumerate(lines) if line.startswith('version:'))
        ending=lines[index][len(lines[index].rstrip('\r\n')):];lines[index]=f"version: '{version}'"+ending
        jar=release/(name+'.jar')
        with zipfile.ZipFile(base) as previous,zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as output:
            for entry in previous.infolist():
                if not owned(entry.filename,families) and entry.filename!='plugin.yml':output.writestr(copy.copy(entry),previous.read(entry.filename))
            for path in sorted(classes.rglob('*.class')):
                entry=path.relative_to(classes).as_posix()
                if not owned(entry,families):raise RuntimeError('Unexpected compiled class '+entry)
                output.write(path,entry)
            output.writestr('plugin.yml',''.join(lines).encode('utf8'))
        preserved=0
        with zipfile.ZipFile(base) as previous,zipfile.ZipFile(jar) as output:
            if output.testzip() or len(output.namelist())!=len(set(output.namelist())):raise RuntimeError('Invalid JAR')
            for entry in previous.namelist():
                if not owned(entry,families) and entry!='plugin.yml':
                    if output.read(entry)!=previous.read(entry):raise RuntimeError('Unrelated entry changed '+entry)
                    preserved+=1
        build.check_jar(jar)
        report[name]={'version':version,'sha256':sha(jar),'baseline_sha256':sha(base),'preserved_entries':preserved}
        report['sources'].update({path.relative_to(ROOT).as_posix():sha(path) for path in [*sources,metadata]})
    (OUT/'release-build.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8');print(json.dumps(report,indent=2))

if __name__=='__main__':main()
