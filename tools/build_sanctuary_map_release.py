"""Build only the Seraph map shop and locator additions over the delivered e2.132 JAR."""
from __future__ import annotations
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import shutil
import zipfile

import build_plugin_jars as build
from release_jar_checks import check_jar

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'output/seraph-sanctuary-maps-20261025'
BEFORE=OUT/'before'
BASE=BEFORE/'dist/evergarden.jar'
VERSION='3.0.0-e2.133-sanctuary-maps'
FAMILIES=tuple('com/example/voidscape/'+name for name in (
    'trade/SanctuaryMaps','trade/SeraphShop','trade/SeraphTrader','world/OverworldSanctuaries'))
RESOURCES=('plugin.yml','config.yml')

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def belongs(name):return any(name==family+'.class' or name.startswith(family+'$') for family in FAMILIES)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--promote',action='store_true',help='Promote an already tested candidate to dist/')
    args=parser.parse_args()
    manifest=json.loads((BEFORE/'manifest.json').read_text(encoding='utf8'))
    if sha(BASE)!=manifest['dist/evergarden.jar']:raise RuntimeError('Baseline backup changed')
    magic=ROOT/'dist/advance-magic.jar'
    if sha(magic)!=manifest['dist/advance-magic.jar']:raise RuntimeError('Advance Magic changed during this build')
    release=OUT/'release';release.mkdir(parents=True,exist_ok=True)
    jar=release/'evergarden.jar'
    if args.promote:
        report=json.loads((OUT/'release-build.json').read_text(encoding='utf8'))
        validation=json.loads((OUT/'runtime-validation.json').read_text(encoding='utf8'))
        if sha(jar)!=report['sha256'] or validation['candidate_sha256']!=report['sha256']:
            raise RuntimeError('Candidate differs from tested artifact')
        if not all(run['result'].startswith('PASS') for run in validation['runs']):raise RuntimeError('Runtime checks failed')
        for name,digest in report['sources'].items():
            if sha(ROOT/name)!=digest:raise RuntimeError('Source changed after candidate build: '+name)
        if sha(ROOT/'dist/evergarden.jar')!=manifest['dist/evergarden.jar']:raise RuntimeError('Delivered baseline changed')
        for target in (ROOT/'dist/evergarden.jar',ROOT/'dist'/f'evergarden-{VERSION}.jar'):
            staged=target.with_suffix('.jar.staged');shutil.copy2(jar,staged);os.replace(staged,target)
        report['copied_to_dist']=True
        (OUT/'release-build.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
        print('Promoted',VERSION,report['sha256']);return
    sources=[ROOT/'evergarden/src/main/java'/(family+'.java') for family in FAMILIES]
    cp=[str(BASE),str(magic)]+[p for p in build.pinned_classpath(build.EVERGARDEN_ARGS)
        if not p.replace('\\','/').endswith('dist/evergarden.jar')]
    build.OUT=release;classes=release/'classes'
    build.compile_sources('sanctuary-maps',sources,cp,classes)
    resources={name:(ROOT/'evergarden/src/main/resources'/name).read_bytes() for name in RESOURCES}
    if ("version: '"+VERSION+"'").encode() not in resources['plugin.yml']:raise RuntimeError('Unexpected version')
    with zipfile.ZipFile(BASE) as baseline,zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as result:
        for entry in baseline.infolist():
            if belongs(entry.filename) or entry.filename in RESOURCES:continue
            result.writestr(copy.copy(entry),baseline.read(entry.filename))
        for path in sorted(classes.rglob('*.class')):
            name=path.relative_to(classes).as_posix()
            if not belongs(name):raise RuntimeError('Unexpected compiled class: '+name)
            result.write(path,name)
        for name,data in resources.items():result.writestr(name,data)
    preserved=0
    with zipfile.ZipFile(BASE) as baseline,zipfile.ZipFile(jar) as candidate:
        names=candidate.namelist()
        if candidate.testzip() or len(names)!=len(set(names)):raise RuntimeError('Invalid JAR')
        for name in baseline.namelist():
            if not belongs(name) and name not in RESOURCES:
                if baseline.read(name)!=candidate.read(name):raise RuntimeError('Unrelated entry changed: '+name)
                preserved+=1
        changed=sorted(n for n in names if belongs(n) or n in RESOURCES)
    check_jar(jar)
    report={'version':VERSION,'sha256':sha(jar),'baseline_sha256':sha(BASE),'advance_magic_sha256':sha(magic),
        'preserved_baseline_entries':preserved,'changed_entries':changed,
        'sources':{str(p.relative_to(ROOT).as_posix()):sha(p) for p in sources+[ROOT/'evergarden/src/main/resources'/n for n in RESOURCES]},
        'copied_to_dist':False}
    (OUT/'release-build.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8')
    print(json.dumps(report,indent=2))

if __name__=='__main__':main()
