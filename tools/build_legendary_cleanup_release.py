"""Package the lightweight rune-reuse cleanup over the approved Fire/Wind JAR."""
from pathlib import Path
import hashlib
import json
import os
import shutil
import zipfile
import build_plugin_jars as build

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'output/legendary-cleanup-20261024/release'
BASE=ROOT/'dist/advance-magic-1.27.1-fire-wind-spectacle.jar'
VERSION='1.27.2-reused-runes'
SOURCE='com/example/advancemagic/spell/ElementalVfx.java'
SOURCES=(SOURCE,'com/example/advancemagic/spell/MythicLineVisuals.java')
PACKS=('advance-magic-java.zip','advance-magic-bedrock.mcpack','geyser-mappings.json','pack-hashes.json')


def digest(data):return hashlib.sha256(data).hexdigest()


def main():
    OUT.mkdir(parents=True,exist_ok=True)
    base_hash=digest(BASE.read_bytes())
    if base_hash!='23e20b4d093c0a704830b6ceed7594a6641b2358d9860dc1f1aeafdb470a23bb':
        raise RuntimeError('Approved Fire/Wind baseline changed unexpectedly')
    snapshots=[];source_hashes={}
    for relative in SOURCES:
        source=ROOT/'advance-magic/src/main/java'/relative
        snapshot=OUT/'source'/relative;snapshot.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,snapshot)
        snapshots.append(snapshot);source_hashes[relative]=digest(snapshot.read_bytes())
    build.OUT=OUT;classes=OUT/'classes'
    build.compile_sources('legendary-cleanup',snapshots,[BASE.as_posix()]+build.pinned_classpath(build.ADVANCE_ARGS),classes)
    replacement={'plugin.yml':(ROOT/'advance-magic/src/main/resources/plugin.yml').read_bytes()}
    for name in PACKS:replacement['resource-packs/'+name]=(ROOT/'advance-magic/dist'/name).read_bytes()
    prefixes=[relative.removesuffix('.java') for relative in SOURCES]
    jar=OUT/'advance-magic.jar';preserved=0
    with zipfile.ZipFile(BASE) as previous,zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as result:
        for entry in previous.infolist():
            if entry.filename in replacement or any(entry.filename==prefix+'.class' or entry.filename.startswith(prefix+'$') for prefix in prefixes):continue
            result.writestr(entry,previous.read(entry.filename));preserved+=1
        for path in sorted(classes.rglob('*.class')):result.write(path,path.relative_to(classes).as_posix())
        for name,data in replacement.items():result.writestr(name,data)
    with zipfile.ZipFile(jar) as z:
        if z.testzip() is not None or len(z.namelist())!=len(set(z.namelist())):raise RuntimeError('Invalid release archive')
        if ('version: '+VERSION).encode() not in z.read('plugin.yml'):raise RuntimeError('Unexpected release version')
    current=ROOT/'dist/advance-magic.jar';backup=OUT/'previous';backup.mkdir(exist_ok=True)
    if current.exists() and not (backup/current.name).exists():shutil.copy2(current,backup/current.name)
    for target in (current,ROOT/'dist'/('advance-magic-'+VERSION+'.jar')):
        staged=target.with_suffix('.jar.staged');shutil.copy2(jar,staged);os.replace(staged,target)
    cleanup=json.loads((ROOT/'output/legendary-cleanup-20261024/resource-cleanup.json').read_text())
    report={'version':VERSION,'jar':'dist/advance-magic-'+VERSION+'.jar','jar_bytes':jar.stat().st_size,
            'sha256':digest(jar.read_bytes()),'baseline_sha256':base_hash,'source_sha256':source_hashes,
            'compiled_sources':len(snapshots),'compiled_classes':len(list(classes.rglob('*.class'))),'preserved_jar_entries':preserved,
            'bedrock_version':[2,30,0],'packs':cleanup['packs'],'pack_sha1':cleanup['sha1'],
            'fire_wind_preservation':'15 original method bodies unchanged; same choreography, acceleration, props and particles',
            'shared_runes':{'water':'lunar_seal/lunar_orbit','ice':'lunar_seal/lunar_orbit','earth':'verdant_seal/verdant_halo','dark':'crimson_seal','lightning':'lunar_seal/lunar_orbit'},
            'retained_light':'Active Light scene and textures unchanged; unused mist removed',
            'combat':'All combat classes, scrolls, mana and cooldowns preserved byte-identically from Fire/Wind delivery',
            'scope':'Compilation, static source preservation and archive packaging; no gameplay or GPU client tests run'}
    (OUT/'release-build.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in report.items() if k!='packs'},indent=2))


if __name__=='__main__':main()
