"""Import all five editable models and translate the real release with local plugins."""
import argparse
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess

ROOT=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--source-server',type=Path,required=True)
p.add_argument('--server',type=Path,default=ROOT/'output/legendary-weapons-integration-20261016')
p.add_argument('--plugin-jar',type=Path,default=ROOT/'dist/advance-magic.jar')
args=p.parse_args();source=args.source_server.resolve();server=args.server.resolve()
if server.exists():p.error('Use a fresh isolated --server')
server.mkdir(parents=True)
for name in ('libraries','versions','cache','config'):
    if (source/name).exists():shutil.copytree(source/name,server/name)
shutil.copy2(source/'paper.jar',server/'paper.jar')
plugins=server/'plugins';plugins.mkdir()
for name in ('ModelEngine.jar','Geyser-Spigot.jar','floodgate-spigot.jar'):
    shutil.copy2(source/'plugins'/name,plugins/name)
shutil.copy2(args.plugin_jar,plugins/'advance-magic.jar')
blueprints=plugins/'ModelEngine/blueprints';blueprints.mkdir(parents=True)
for model in (ROOT/'advance-magic/art/legendary-weapons').glob('*.bbmodel'):
    shutil.copy2(model,blueprints/('legendary_'+model.name))
# Preserve engine compatibility defaults from the already-validated local setup.
shutil.copy2(source/'plugins/ModelEngine/config.yml',plugins/'ModelEngine/config.yml')
geyser=plugins/'Geyser-Spigot';geyser.mkdir()
with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as sock:sock.bind(('127.0.0.1',0));udp=sock.getsockname()[1]
(geyser/'config.yml').write_text(f'bedrock:\n  address: 127.0.0.1\n  port: {udp}\nremote:\n  address: auto\n  auth-type: floodgate\nmetrics:\n  enabled: false\n',encoding='utf8')
magic=plugins/'advance-magic';magic.mkdir();(magic/'config.yml').write_text('resource-pack:\n  enabled: false\n  host:\n    enabled: false\n')
stats=plugins/'bStats';stats.mkdir();(stats/'config.yml').write_text('enabled: false\n')
deps=[*(server/'versions').rglob('*.jar'),*(server/'libraries').rglob('*.jar'),*plugins.glob('*.jar')]
gsons=[p for p in deps if re.fullmatch(r'gson-[\d.]+\.jar',p.name)]
if gsons:deps=[max(gsons,key=lambda p:tuple(map(int,re.findall(r'\d+',p.name))))]+[p for p in deps if p not in gsons]
classes=server/'probe-classes';classes.mkdir()
options=['--release','25','-proc:none','-encoding','UTF-8','-cp',os.pathsep.join(map(str,deps)),'-d',str(classes),str(Path(__file__).with_name('LegendaryWeaponIntegrationChecks.java'))]
argfile=server/'probe-javac.args';argfile.write_text('\n'.join('"'+str(v).replace('\\','/')+'"' for v in options),encoding='utf8')
subprocess.run(['javac','@'+str(argfile)],check=True)
(classes/'plugin.yml').write_text("name: LegendaryWeaponIntegrationChecks\nversion: '1'\nmain: LegendaryWeaponIntegrationChecks\napi-version: '26.2'\ndepend: [advance-magic, Geyser-Spigot, ModelEngine]\n")
subprocess.run(['jar','--create','--file',str(plugins/'weapon-integration-checks.jar'),'-C',str(classes),'.'],check=True)
(server/'eula.txt').write_text('eula=true\n')
(server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nenforce-secure-profile=false\nlevel-name=weapon_integration\nlevel-type=minecraft:flat\nview-distance=2\nsimulation-distance=2\npause-when-empty-seconds=-1\nmax-tick-time=180000\n')
print('Isolated Geyser / ModelEngine server:',server,flush=True)
with (server/'console.log').open('w',encoding='utf8') as log:
    process=subprocess.Popen(['java','-Xms256M','-Xmx2G','-Dterminal.jline=false','-Dterminal.ansi=false','-jar','paper.jar','--nogui','--noconsole'],cwd=server,stdout=log,stderr=subprocess.STDOUT,
        creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
    try:process.wait(timeout=200)
    except subprocess.TimeoutExpired:process.kill();process.wait();raise RuntimeError('Integration timed out: '+str(server/'console.log'))
result=server/'legendary-weapon-integration-result.txt'
if not result.exists():raise RuntimeError('Missing integration result: '+str(server/'console.log'))
print(result.read_text(),flush=True)
raise SystemExit(0 if result.read_text().startswith('PASS') else 1)
