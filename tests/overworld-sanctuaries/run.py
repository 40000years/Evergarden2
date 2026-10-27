"""Create an isolated Paper fixture and exercise both boots; never touches live worlds."""
import argparse,os,shutil,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
BUILD=ROOT/'output/overworld-sanctuaries-habitat-build-20261015'
parser=argparse.ArgumentParser();parser.add_argument('--server',type=Path,default=ROOT/'output/overworld-sanctuaries-paper-20261015');args=parser.parse_args()
SERVER=args.server.resolve()
TEMPLATE=ROOT/'output/arkhaia-aggro-feedback-paper-20261014'
SERVER.mkdir(exist_ok=False)
for name in ('libraries','versions','cache'):
    subprocess.run(['powershell','-NoProfile','-Command',"New-Item -ItemType Junction -Path '"+str(SERVER/name)+"' -Target '"+str(TEMPLATE/name)+"' | Out-Null"],check=True)
shutil.copy2(TEMPLATE/'paper.jar',SERVER/'paper.jar')
plugins=SERVER/'plugins';plugins.mkdir();shutil.copy2(BUILD/'evergarden.jar',plugins/'evergarden.jar');shutil.copy2(ROOT/'dist/advance-magic.jar',plugins/'advance-magic.jar')
for name in ('Evergarden','advance-magic'):
    folder=plugins/name;folder.mkdir()
    (folder/'config.yml').write_text('config-version: 2\nresource-pack:\n  enabled: false\n  host:\n    enabled: false\nworld-boss:\n  visuals:\n    pack:\n      host:\n        bind: 127.0.0.1\n        port: 0\n',encoding='utf8')
classes=SERVER/'probe-classes';classes.mkdir();cp=(BUILD/'classpath.txt').read_text()
options=['--release','25','-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(classes),str(Path(__file__).with_name('SanctuaryProbe.java'))]
argfile=SERVER/'javac.args';argfile.write_text('\n'.join('"'+s.replace('\\','/')+'"' for s in options))
subprocess.run(['javac','@'+str(argfile)],check=True)
(classes/'plugin.yml').write_text("name: SanctuaryProbe\nversion: '1'\nmain: SanctuaryProbe\napi-version: '26.2'\ndepend: [Evergarden]\n")
subprocess.run(['jar','--create','--file',str(plugins/'sanctuary-probe.jar'),'-C',str(classes),'.'],check=True)
(SERVER/'eula.txt').write_text('eula=true\n')
(SERVER/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-name=overworld_probe\nlevel-seed=72819345\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\npause-when-empty-seconds=-1\nmax-tick-time=240000\n')
(SERVER/'spigot.yml').write_text('settings:\n  timeout-time: 240\n  restart-on-crash: false\n')
layout_snapshot=None
for phase in (1,2):
    with (SERVER/f'console-{phase}.log').open('w',encoding='utf8') as log:
        subprocess.run(['java','-Xms512M','-Xmx3G','-Dterminal.jline=false','-Dterminal.ansi=false','-jar','paper.jar','--nogui','--noconsole'],cwd=SERVER,stdout=log,stderr=subprocess.STDOUT,timeout=600,check=True,creationflags=subprocess.CREATE_NO_WINDOW)
    report=(SERVER/f'sanctuary-result-{phase}.txt').read_text();print(report,flush=True)
    if not report.startswith('PASS'):raise SystemExit(1)
    data=(plugins/'Evergarden/overworld-sanctuaries.yml').read_bytes()
    if layout_snapshot is None:layout_snapshot=data
    elif layout_snapshot!=data:raise AssertionError('Saved layout/exclusions changed after restart')
print('PASS: saved world UUID, grid, chance and old-region exclusions survived both boots',flush=True)
