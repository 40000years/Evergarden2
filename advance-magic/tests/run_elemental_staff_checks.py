"""Boot a disposable localhost Paper server for runtime or actual ME/Geyser tests."""
from pathlib import Path
import argparse
import json
import os
import re
import shutil
import socket
import subprocess

ROOT=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--source-server',type=Path,required=True)
p.add_argument('--server',type=Path,required=True)
p.add_argument('--plugin-jar',type=Path,required=True)
p.add_argument('--garden-jar',type=Path)
p.add_argument('--mode',choices=('runtime','integration'),default='runtime')
p.add_argument('--compiler-process',action='store_true')
a=p.parse_args();source=a.source_server.resolve();server=a.server.resolve()
server.mkdir(parents=True,exist_ok=False)
for name in ('libraries','versions','cache'):
    origin=source/name
    if os.name=='nt':
        subprocess.run(['powershell','-NoProfile','-Command',
            "New-Item -ItemType Junction -Path '"+str(server/name).replace("'","''")+"' -Target '"+str(origin).replace("'","''")+"' | Out-Null"],check=True)
    else:
        (server/name).symlink_to(origin,target_is_directory=True)
if (source/'config').exists():shutil.copytree(source/'config',server/'config')
shutil.copy2(source/'paper.jar',server/'paper.jar')
plugins=server/'plugins';plugins.mkdir()
shutil.copy2(a.plugin_jar,plugins/'advance-magic.jar')
if a.garden_jar:
    shutil.copy2(a.garden_jar,plugins/'evergarden.jar')
    garden=plugins/'Evergarden';garden.mkdir()
    (garden/'config.yml').write_text('config-version: 2\ndimension:\n  world-name: staff_garden\nresource-pack:\n  enabled: false\n  host:\n    enabled: false\noverworld-sanctuaries:\n  enabled: false\narkhaia:\n  model-engine:\n    enabled: false\nworld-boss:\n  visuals:\n    pack:\n      enabled: false\nstructures:\n  exploration:\n    enabled: false\n  sky-whale:\n    enabled: false\n  observatory:\n    enabled: false\n  hanging-garden:\n    enabled: false\n  world-boss-temple:\n    enabled: false\n',encoding='utf8')
if a.mode=='integration':
    for name in ('ModelEngine.jar','Geyser-Spigot.jar','floodgate-spigot.jar'):
        shutil.copy2(source/'plugins'/name,plugins/name)
    models=plugins/'ModelEngine/blueprints';models.mkdir(parents=True)
    for path in (ROOT/'advance-magic/art/elemental-staffs').glob('*.bbmodel'):
        shutil.copy2(path,models/path.name)
    shutil.copy2(source/'plugins/ModelEngine/config.yml',plugins/'ModelEngine/config.yml')
    geyser=plugins/'Geyser-Spigot';geyser.mkdir()
    with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as sock:
        sock.bind(('127.0.0.1',0));udp=sock.getsockname()[1]
    (geyser/'config.yml').write_text(f'bedrock:\n  address: 127.0.0.1\n  port: {udp}\nremote:\n  address: auto\n  auth-type: floodgate\nmetrics:\n  enabled: false\n',encoding='utf8')
magic=plugins/'advance-magic';magic.mkdir()
(magic/'config.yml').write_text('resource-pack:\n  enabled: false\n  host:\n    enabled: false\n',encoding='utf8')
stats=plugins/'bStats';stats.mkdir();(stats/'config.yml').write_text('enabled: false\n')
deps=[*(server/'versions').rglob('*.jar'),*(server/'libraries').rglob('*.jar'),*plugins.glob('*.jar')]
gsons=[path for path in deps if re.fullmatch(r'gson-[\d.]+\.jar',path.name)]
if gsons:
    newest=max(gsons,key=lambda path:tuple(map(int,re.findall(r'\d+',path.name))))
    deps=[newest]+[path for path in deps if path not in gsons]
classes=server/'probe-classes';classes.mkdir()
main='ElementalStaffIntegrationChecks' if a.mode=='integration' else 'ElementalStaffChecks'
options=['--release','25','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,deps)),'-d',str(classes),str(Path(__file__).with_name(main+'.java'))]
if a.mode=='integration':
    options.append(str(Path(__file__).with_name('ElementalStaffAmbientChecks.java')))
argfile=server/'probe-javac.args';argfile.write_text('\n'.join('"'+str(v).replace('\\','/')+'"' for v in options),encoding='utf8')
compiler_command=['java',str(ROOT/'tools/CompileJava.java'),str(argfile)] if a.compiler_process else ['javac','@'+str(argfile)]
subprocess.run(compiler_command,check=True)
dependencies='[advance-magic, Geyser-Spigot, ModelEngine]' if a.mode=='integration' else '[advance-magic]'
if a.garden_jar:dependencies=dependencies[:-1]+', Evergarden]'
(classes/'plugin.yml').write_text(f"name: {main}\nversion: '1'\nmain: {main}\napi-version: '26.2'\ndepend: {dependencies}\n")
subprocess.run(['jar','--create','--file',str(plugins/'staff-checks.jar'),'-C',str(classes),'.'],check=True)
(server/'eula.txt').write_text('eula=true\n')
(server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nenforce-secure-profile=false\nlevel-name=staff_test\nlevel-type=minecraft:flat\ngenerate-structures=false\nview-distance=2\nsimulation-distance=2\npause-when-empty-seconds=-1\nmax-tick-time=180000\n')
print('Isolated '+a.mode+' server: '+str(server),flush=True)
with (server/'console.log').open('w',encoding='utf8') as log:
    process=subprocess.Popen(['java','-Xms256M','-Xmx2G','-Dterminal.jline=false','-Dterminal.ansi=false','-jar','paper.jar','--nogui','--noconsole'],cwd=server,stdout=log,stderr=subprocess.STDOUT,
        creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
    try:process.wait(timeout=220)
    except subprocess.TimeoutExpired:
        process.kill();process.wait();raise RuntimeError('Probe timed out: '+str(server/'console.log'))
result=server/('elemental-staff-integration-result.txt' if a.mode=='integration' else 'elemental-staff-result.txt')
if not result.exists():raise RuntimeError('Missing result: '+str(server/'console.log'))
print(result.read_text(),flush=True)
raise SystemExit(0 if result.read_text().startswith('PASS') else 1)
