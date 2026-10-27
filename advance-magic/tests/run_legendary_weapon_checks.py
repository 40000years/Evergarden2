"""Compile and exercise the weapon release on isolated cached Paper. No deployment."""
import argparse
import os
import re
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source-server',type=Path,required=True)
parser.add_argument('--server',type=Path,default=ROOT/'output/legendary-weapons-paper-20261016')
args=parser.parse_args()
server=args.server.resolve();source=args.source_server.resolve()
if server.exists():parser.error('Use a fresh disposable --server directory')
server.mkdir(parents=True)
for folder in ('libraries','versions','cache','config'):
    if (source/folder).exists():shutil.copytree(source/folder,server/folder)
shutil.copy2(source/'paper.jar',server/'paper.jar')
deps=list((source/'compile-dependencies').glob('*.jar'))
if not deps:raise RuntimeError('Source server must include compile-dependencies')
deps += list((server/'versions').rglob('*.jar'))+list((server/'libraries').rglob('*.jar'))
gsons=[p for p in deps if re.fullmatch(r'gson-[\d.]+\.jar',p.name)]
latest=max(gsons,key=lambda p:tuple(map(int,re.findall(r'\d+',p.name))))
deps=[latest]+[p for p in deps if p not in gsons]
def compile(sources,classes,extra=()):
    classes.mkdir();options=['--release','25','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,[*extra,*deps])),'-d',str(classes),*map(str,sources)]
    argfile=classes.parent/(classes.name+'-javac.args')
    argfile.write_text('\n'.join('"'+str(o).replace('\\','/')+'"' for o in options),encoding='utf8')
    subprocess.run(['javac','@'+str(argfile)],check=True)
classes=server/'magic-classes';compile(sorted((ROOT/'advance-magic/src/main/java').rglob('*.java')),classes)
shutil.copytree(ROOT/'advance-magic/src/main/resources',classes,dirs_exist_ok=True)
packs=classes/'resource-packs';packs.mkdir()
for name in ('advance-magic-java.zip','advance-magic-bedrock.mcpack','geyser-mappings.json','pack-hashes.json'):
    shutil.copy2(ROOT/'advance-magic/dist'/name,packs/name)
plugins=server/'plugins';plugins.mkdir();magic=plugins/'advance-magic.jar'
subprocess.run(['jar','--create','--file',str(magic),'-C',str(classes),'.'],check=True)
probe=server/'probe-classes';compile([Path(__file__).with_name('LegendaryWeaponChecks.java')],probe,[magic])
(probe/'plugin.yml').write_text("name: LegendaryWeaponChecks\nversion: '1.0'\nmain: LegendaryWeaponChecks\napi-version: '26.2'\ndepend: [advance-magic]\n",encoding='utf8')
subprocess.run(['jar','--create','--file',str(plugins/'legendary-weapon-checks.jar'),'-C',str(probe),'.'],check=True)
config=plugins/'advance-magic';config.mkdir()
(config/'config.yml').write_text('resource-pack:\n  enabled: false\n  host:\n    enabled: false\n',encoding='utf8')
(server/'eula.txt').write_text('eula=true\n')
(server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nenforce-secure-profile=false\nlevel-name=weapon_test\nlevel-type=minecraft:flat\ngenerate-structures=false\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\npause-when-empty-seconds=-1\nmax-tick-time=240000\n')
print('Isolated server:',server,flush=True)
with (server/'console.log').open('w',encoding='utf8') as log:
    p=subprocess.Popen(['java','-Xms256M','-Xmx2G','-Dterminal.jline=false','-Dterminal.ansi=false','-jar','paper.jar','--nogui','--noconsole'],cwd=server,stdout=log,stderr=subprocess.STDOUT,
        creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
    try:p.wait(timeout=180)
    except subprocess.TimeoutExpired:p.kill();p.wait();raise RuntimeError('Paper probe timed out: '+str(server/'console.log'))
result=server/'legendary-weapons-result.txt'
if not result.exists():raise RuntimeError('Missing probe result: '+str(server/'console.log'))
report=result.read_text();print(report,flush=True)
if not report.startswith('PASS'):raise SystemExit(1)
for destination in (ROOT/'dist/advance-magic.jar',ROOT/'advance-magic/target/advance-magic.jar'):
    destination.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(magic,destination)
subprocess.run(['python',str(ROOT/'advance-magic/tests/check_legendary_weapons.py')],check=True)
subprocess.run(['python',str(ROOT/'advance-magic/tests/check_packs.py')],check=True)
print('Packaged tested local release:',ROOT/'dist/advance-magic.jar',flush=True)
