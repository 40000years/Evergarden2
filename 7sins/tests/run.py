"""Verify Wrath in an isolated Paper server; only cached libraries are copied."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--paper-template',type=Path,required=True)
parser.add_argument('--java-home',type=Path,required=True)
parser.add_argument('--timeout',type=int,default=180)
args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
server=Path(tempfile.mkdtemp(prefix='7sins-wrath-probe-'))
print(f'Disposable server: {server}',flush=True)
for name in ['libraries','versions','cache']:
    source=args.paper_template/name
    if source.is_dir():shutil.copytree(source,server/name)
shutil.copy2(args.paper_template/'paper.jar',server/'paper.jar')
plugins=server/'plugins';plugins.mkdir()
shutil.copy2(root/'target/7sins.jar',plugins/'7sins.jar')
config=plugins/'7sins';config.mkdir()
(config/'config.yml').write_text('wrath:\n  health: 600.0\nresource-pack:\n  url: \'\'\n  auto-send: false\n  host:\n    port: 0\n    bind: 127.0.0.1\n    public-host: 127.0.0.1\n',encoding='utf8')
classes=server/'probe-classes';classes.mkdir()
suffix='.exe' if os.name=='nt' else ''
java,javac,jar=[str(args.java_home/'bin'/f'{name}{suffix}') for name in ('java','javac','jar')]
cp=os.pathsep.join(str(p) for p in [plugins/'7sins.jar',*(server/'libraries').rglob('*.jar')])
subprocess.run([javac,'--release','25','-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(classes),str(root/'tests/WrathProbe.java'),str(root/'tests/WrathCombatChecks.java')],check=True)
subprocess.run([java,'-cp',str(classes)+os.pathsep+cp,'WrathCombatChecks'],check=True)
shutil.copy2(root/'tests/plugin.yml',classes/'plugin.yml')
subprocess.run([jar,'--create','--file',str(plugins/'wrath-probe.jar'),'-C',str(classes),'.'],check=True)
(server/'eula.txt').write_text('eula=true\n')
(server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nview-distance=2\nsimulation-distance=2\n'
    'spawn-protection=0\npause-when-empty-seconds=-1\nmax-tick-time=120000\nlevel-name=wrath_probe\nlevel-type=minecraft:flat\ngenerate-structures=false\n')
log=server/'console.log'
try:
    with log.open('w') as stream:
        subprocess.run([java,'-Xms256M','-Xmx1G','-Dterminal.jline=false','-Dterminal.ansi=false','-jar','paper.jar','--nogui'],cwd=server,
            stdin=subprocess.DEVNULL,stdout=stream,stderr=subprocess.STDOUT,timeout=args.timeout,check=True,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
    result=(server/'wrath-probe-result.txt').read_text()
    print(result,flush=True)
    if not result.startswith('PASS\n'):raise RuntimeError('Wrath probe failed')
except Exception:
    print(log.read_text(errors='replace')[-14000:]);raise
