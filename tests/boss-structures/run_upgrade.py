"""Verify v1-to-v2 architecture migration over partially generated real Paper worlds."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess

ROOT=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--template',type=Path,required=True)
p.add_argument('--old-jar',type=Path,required=True)
p.add_argument('--garden-jar',type=Path,required=True)
p.add_argument('--server',type=Path,required=True)
p.add_argument('--classpath-file',type=Path,required=True)
p.add_argument('--probe',default='BossArchitectureUpgradeProbe')
p.add_argument('--result-prefix',default='architecture-upgrade-result')
p.add_argument('--repeat-new',action='store_true')
args=p.parse_args();server=args.server.resolve();server.mkdir(parents=True,exist_ok=False)
for name in ('libraries','versions','cache'):
    source=(args.template/name).resolve();target=server/name
    subprocess.run(['powershell','-NoProfile','-Command',"New-Item -ItemType Junction -Path '"+str(target).replace("'","''")+"' -Target '"+str(source).replace("'","''")+"' | Out-Null"],check=True)
shutil.copy2(args.template/'paper.jar',server/'paper.jar')
plugins=server/'plugins';plugins.mkdir();shutil.copy2(ROOT/'dist/advance-magic.jar',plugins/'advance-magic.jar')
for name in ('Evergarden','advance-magic'):
    folder=plugins/name;folder.mkdir()
    config='config-version: 2\nresource-pack:\n  enabled: false\n  host:\n    enabled: false\nworld-boss:\n  visuals:\n    pack:\n      host:\n        bind: 127.0.0.1\n        port: 0\n'
    if name=='Evergarden':
        config+='structures:\n  boss-structures:\n    arkhaia-castle:\n      chance: 0.30\n    seraphon-valkyrie:\n      chance: 0.30\n    seraphon-sanctum:\n      chance: 0.30\n'
        if args.probe=='BossInteriorUpgradeProbe':
            # Guarantee both old chests so deletion and inventory preservation are independent.
            config+='  treasure:\n    second-chest-chance: 1.0\n'
    (folder/'config.yml').write_text(config,encoding='utf8')
classes=server/'probe-classes';classes.mkdir()
cp=os.pathsep.join([str(args.garden_jar.resolve()),args.classpath_file.read_text(encoding='utf8')])
opts=['--release','25','-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(classes),str(Path(__file__).with_name(args.probe+'.java').resolve())]
argfile=server/'javac.args';argfile.write_text('\n'.join('"'+v.replace('\\','/')+'"' for v in opts),encoding='utf8')
subprocess.run(['javac','@'+str(argfile)],check=True)
(classes/'plugin.yml').write_text("name: "+args.probe+"\nversion: '1'\nmain: "+args.probe+"\napi-version: '26.2'\ndepend: [Evergarden, advance-magic]\n",encoding='utf8')
subprocess.run(['jar','--create','--file',str(plugins/'architecture-upgrade-probe.jar'),'-C',str(classes),'.'],check=True)
(server/'eula.txt').write_text('eula=true\n')
(server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-name=architecture_upgrade\nlevel-type=minecraft:flat\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\npause-when-empty-seconds=-1\nmax-tick-time=240000\n')
(server/'spigot.yml').write_text('settings:\n  timeout-time: 240\n  restart-on-crash: false\n')
for phase,jar in enumerate((args.old_jar,args.garden_jar,*([args.garden_jar] if args.repeat_new else [])),1):
    shutil.copy2(jar,plugins/'evergarden.jar')
    with (server/f'console-{phase}.log').open('w',encoding='utf8') as log:
        subprocess.run(['java','-Xms512M','-Xmx3G','-Dterminal.jline=false','-Dterminal.ansi=false','-jar','paper.jar','--nogui','--noconsole'],cwd=server,stdout=log,stderr=subprocess.STDOUT,timeout=300,check=True,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
    report=(server/(args.result_prefix+'.txt')).read_text(encoding='utf8');print(f'Phase {phase}:\n'+report,flush=True)
    (server/(args.result_prefix+'.txt')).rename(server/(args.result_prefix+'-'+str(phase)+'.txt'))
    if not report.startswith('PASS'):raise SystemExit(1)
