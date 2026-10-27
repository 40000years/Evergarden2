"""Record production sources, then package the exact JARs validated on isolated Paper."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import shutil
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parents[1]
VERSIONS={'advance-magic':'1.17.0-loot-only-staffs','evergarden':'3.0.0-e2.97-elemental-staff-loot'}
PACKS={'advance-magic':('advance-magic-java.zip','advance-magic-bedrock.mcpack','geyser-mappings.json','pack-hashes.json'),
       'evergarden':('evergarden-java.zip','judge-java.zip','evergarden-bedrock.mcpack','geyser-mappings.json','pack-hashes.json')}

def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def sources():
    paths=[p for module in VERSIONS for p in (ROOT/module/'src/main').rglob('*') if p.is_file()]
    paths += [ROOT/module/'dist'/name for module,names in PACKS.items() for name in names]
    paths += list((ROOT/'advance-magic/art/elemental-staffs').glob('*.bbmodel'))
    return {p.relative_to(ROOT).as_posix():digest(p) for p in sorted(paths)}
def entries(path):
    with zipfile.ZipFile(path) as z:
        return {n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if not n.endswith('/') and not n.startswith('META-INF/')}

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--record-sources',type=Path)
parser.add_argument('--snapshot',type=Path)
parser.add_argument('--system-server',type=Path)
parser.add_argument('--treasure-server',type=Path)
args=parser.parse_args()
if args.record_sources:
    args.record_sources.parent.mkdir(parents=True,exist_ok=True)
    args.record_sources.write_text(json.dumps(sources(),indent=2)+'\n',encoding='utf8')
    print('Recorded sources:',args.record_sources)
    raise SystemExit(0)
if not all((args.snapshot,args.system_server,args.treasure_server)):
    parser.error('Packaging requires --snapshot, --system-server and --treasure-server')
recorded=json.loads(args.snapshot.read_text(encoding='utf8'))
if recorded!=sources():raise RuntimeError('Production source or embedded pack changed after the validation snapshot')
system,treasure=args.system_server.resolve(),args.treasure_server.resolve()
reports={}
for name,directory,report in [('system',system,'elemental-system-result.txt'),('treasure',treasure,'elemental-treasure-result.txt')]:
    result=(directory/report).read_text(encoding='utf8').strip()
    if not result.startswith('PASS'):raise RuntimeError('Missing or failed '+name+' validation: '+result)
    reports[name]=result
magic=system/'plugins/advance-magic.jar'
if entries(magic)!=entries(treasure/'plugins/advance-magic.jar'):raise RuntimeError('Paper probes used different Advance Magic code/resources')
origins={'advance-magic':magic,'evergarden':treasure/'plugins/evergarden.jar'}
for module,path in origins.items():
    with zipfile.ZipFile(path) as z:
        version=re.search(r'^version:\s*[\'\"]?([^\'\"\r\n]+)',z.read('plugin.yml').decode('utf8'),re.M).group(1)
        if version!=VERSIONS[module]:raise RuntimeError('Unexpected plugin version: '+version)
        for pack in PACKS[module]:
            if z.read('resource-packs/'+pack)!=(ROOT/module/'dist'/pack).read_bytes():raise RuntimeError('Embedded pack differs: '+module+'/'+pack)
subprocess.run(['python',str(ROOT/'tools/release_jar_checks.py'),*map(str,origins.values())],check=True)
dest=ROOT/'dist';dest.mkdir(exist_ok=True)
artifacts={}
for module,path in origins.items():
    target=dest/(module+'-'+VERSIONS[module]+'.jar')
    shutil.copy2(path,target);shutil.copy2(path,dest/(module+'.jar'))
    artifacts[target.name]={'bytes':target.stat().st_size,'sha256':digest(target)}
manifest={'versions':VERSIONS,'validation_reports':reports,'artifacts':artifacts,
          'drop_chances':{'vault':.10,'sky_chest':.03,'overworld_chest':.01},
          'staff_tier':'APPRENTICE','element_selection':'uniform across 8 elements',
          'new_chests_only':True,'crafting_enabled':False,'deployment':'workspace artifacts only',
          'sources':recorded}
manifest_path=dest/'elemental-staff-loot-manifest.json'
manifest_path.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
bundle=dest/'elemental-staff-loot-1.17.0.zip'
with zipfile.ZipFile(bundle,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for module,path in origins.items():z.write(path,module+'.jar')
    z.write(ROOT/'docs/elemental-staff-loot.md','elemental-staff-loot.md')
    z.write(manifest_path,manifest_path.name)
with zipfile.ZipFile(bundle) as z:
    if z.testzip() is not None:raise RuntimeError('Bundle integrity check failed')
print(json.dumps({'bundle':str(bundle),'reports':reports,'artifacts':artifacts},ensure_ascii=False,indent=2))
