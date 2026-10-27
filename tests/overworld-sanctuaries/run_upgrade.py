"""Three real Paper boots: delivered v1 -> furnished v2 -> v2 restart."""
import argparse,shutil,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];BUILD=ROOT/'output/overworld-sanctuaries-v5-build-20261015'
p=argparse.ArgumentParser();p.add_argument('--server',type=Path,required=True);p.add_argument('--baseline-version',type=int,choices=(1,2,3,4),default=4);args=p.parse_args();server=args.server.resolve();server.mkdir(exist_ok=False)
# Pick concrete changed masonry and removed resource-block positions from the two
# shipped assets. The runtime assertions independently inspect the actual world.
import sys
sys.path.insert(0,str(ROOT/'tools'))
from preview_overworld_sanctuaries import read_asset
assets=ROOT/'evergarden/src/main/resources/overworld-sanctuaries';samples=[];container_sentinels=[]
for i,name in enumerate(('acheron','phlegethon','cocytus','banshee','malefeasance','styx')):
    old=read_asset(assets/f'v{args.baseline_version}'/(name+'.ows'));new=read_asset(assets/(name+'.ows'))
    if len(container_sentinels)<2:
        for pos,mat in old.items():
            if mat in ('BARREL','FURNACE') and new.get(pos,'AIR')!=mat:
                container_sentinels.append(','.join(map(str,(i,*pos,new.get(pos,'AIR')))))
                if len(container_sentinels)==2:break
    mined=next((pos for pos,mat in old.items() if mat=='GOLD_BLOCK' and new.get(pos,'AIR')!='AIR'),None) if i==5 else None
    if i==5 and mined is None:mined=next(pos for pos,mat in old.items() if 6<=pos[1]<=30 and new.get(pos,'AIR') not in ('AIR',mat) and mat in ('DEEPSLATE_BRICKS','POLISHED_BLACKSTONE_BRICKS','ACACIA_PLANKS','CHISELED_POLISHED_BLACKSTONE'))
    if mined:(server/'mine-sentinel.txt').write_text(','.join(map(str,mined)))
    changed=[(pos,mat) for pos,mat in new.items() if mat!=old.get(pos,'AIR') and old.get(pos) not in ('BARREL','FURNACE','BLAST_FURNACE','CHEST') and 8<=pos[1]<=25 and -80<=pos[2]<=-32 and abs(pos[0])<75 and mat in ('POLISHED_DIORITE','DEEPSLATE_BRICKS','POLISHED_BLACKSTONE_BRICKS','STRIPPED_DARK_OAK_LOG','POLISHED_BASALT','CYAN_TERRACOTTA','WHITE_CONCRETE')]
    assert changed,name
    for pos,mat in changed[::max(1,len(changed)//12)][:12]:samples.append(','.join(map(str,(i,*pos,mat))))
    removed=[(pos,new.get(pos,'AIR')) for pos,mat in old.items() if mat in ('GOLD_BLOCK','IRON_BLOCK','RAW_IRON_BLOCK','RAW_COPPER_BLOCK') and pos!=mined]
    for pos,mat in removed[::max(1,len(removed)//10)][:10]:samples.append(','.join(map(str,(i,*pos,mat))))
(server/'upgrade-samples.txt').write_text('\n'.join(samples))
if len(container_sentinels)<2:
    for i,name in enumerate(('acheron','phlegethon','cocytus','banshee','malefeasance','styx')):
        old=read_asset(assets/f'v{args.baseline_version}'/(name+'.ows'));new=read_asset(assets/(name+'.ows'))
        for pos,mat in old.items():
            entry=','.join(map(str,(i,*pos,new.get(pos,'AIR'))))
            if mat in ('BARREL','FURNACE') and entry not in container_sentinels:container_sentinels.append(entry)
            if len(container_sentinels)==2:break
        if len(container_sentinels)==2:break
assert len(container_sentinels)==2
(server/'container-sentinels.txt').write_text('\n'.join(container_sentinels))
template=ROOT/'output/arkhaia-aggro-feedback-paper-20261014'
for name in ('libraries','versions','cache'):
    subprocess.run(['powershell','-NoProfile','-Command',"New-Item -ItemType Junction -Path '"+str(server/name)+"' -Target '"+str(template/name)+"' | Out-Null"],check=True)
shutil.copy2(template/'paper.jar',server/'paper.jar');plugins=server/'plugins';plugins.mkdir();shutil.copy2(ROOT/'dist/advance-magic.jar',plugins/'advance-magic.jar')
for name in ('Evergarden','advance-magic'):
    folder=plugins/name;folder.mkdir();(folder/'config.yml').write_text('config-version: 2\nresource-pack:\n  enabled: false\n  host:\n    enabled: false\nworld-boss:\n  visuals:\n    pack:\n      host:\n        bind: 127.0.0.1\n        port: 0\n',encoding='utf8')
classes=server/'probe-classes';classes.mkdir();cp=(BUILD/'classpath.txt').read_text()
options=['--release','25','-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(classes),str(Path(__file__).with_name('SanctuaryUpgradeProbe.java'))]
argfile=server/'javac.args';argfile.write_text('\n'.join('"'+s.replace('\\','/')+'"' for s in options));subprocess.run(['javac','@'+str(argfile)],check=True)
(classes/'plugin.yml').write_text("name: SanctuaryUpgradeProbe\nversion: '1'\nmain: SanctuaryUpgradeProbe\napi-version: '26.2'\ndepend: [Evergarden]\n")
subprocess.run(['jar','--create','--file',str(plugins/'upgrade-probe.jar'),'-C',str(classes),'.'],check=True)
(server/'eula.txt').write_text('eula=true\n');(server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-name=upgrade_probe\nlevel-seed=72819345\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\npause-when-empty-seconds=-1\nmax-tick-time=240000\n');(server/'spigot.yml').write_text('settings:\n  timeout-time: 240\n  restart-on-crash: false\n')
snapshot=None
for phase in (1,2,3):
    baseline=ROOT/('output/overworld-sanctuaries-build-20261015/evergarden.jar' if args.baseline_version==1 else f'output/overworld-sanctuaries-v{args.baseline_version}-build-20261015/evergarden.jar')
    source=baseline if phase==1 else BUILD/'evergarden.jar'
    shutil.copy2(source,plugins/'evergarden.jar')
    with (server/f'console-{phase}.log').open('w',encoding='utf8') as log:
        subprocess.run(['java','-Xms512M','-Xmx3G',f'-Dsanctuary.baseline.version={args.baseline_version}','-Dterminal.jline=false','-Dterminal.ansi=false','-jar','paper.jar','--nogui','--noconsole'],cwd=server,stdout=log,stderr=subprocess.STDOUT,timeout=600,check=True,creationflags=subprocess.CREATE_NO_WINDOW)
    report=(server/f'upgrade-result-{phase}.txt').read_text();print(report,flush=True)
    if not report.startswith('PASS'):raise SystemExit(1)
    if phase>=2:
        data=(plugins/'Evergarden/overworld-sanctuaries.yml').read_bytes()
        if snapshot is None:snapshot=data
        else:assert data==snapshot,'v2 world placement ledger changed on restart'
