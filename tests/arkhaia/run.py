"""Run isolated Paper against the integrated release; no live server changes."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import socket
from zipfile import ZipFile

ROOT=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--template',type=Path,required=True)
p.add_argument('--garden-jar',type=Path,required=True)
p.add_argument('--magic-jar',type=Path,default=ROOT/'dist/advance-magic.jar')
p.add_argument('--server',type=Path,required=True)
p.add_argument('--classpath-file',type=Path,required=True)
p.add_argument('--armor-wear',action='store_true')
p.add_argument('--armor-sets',action='store_true')
p.add_argument('--armor-exchange',action='store_true')
p.add_argument('--contact',action='store_true')
p.add_argument('--hitbox',action='store_true')
p.add_argument('--seraphon',action='store_true')
p.add_argument('--seraphon-native',action='store_true')
p.add_argument('--ranged-geyser',action='store_true',help='Dump the Bedrock components Geyser registers for the bow/crossbow')
p.add_argument('--pack-order',action='store_true',help='Real Paper players: Judge follows the core packs')
p.add_argument('--pack-download',action='store_true',help='Serve the real Java packs (with ModelEngine) and download them through the game port')
p.add_argument('--pack-mirror',help='resource-pack.mirrors template for both plugins, e.g. http://127.0.0.1:PORT/{pack}/{fingerprint}.zip')
p.add_argument('--pack-unreachable',action='store_true',help='With --pack-order: hand out an unreachable address for this server, as a tunnel that carries only Minecraft would')
p.add_argument('--world-survey',action='store_true',help='Map and measure a fresh Evergarden world')
p.add_argument('--level-seed',default='72819345',help='Overworld seed for --sanctuary, --airborne and --world-survey')
p.add_argument('--unique-balance',action='store_true',help='Ancient enchant 33%% procs, cooldowns and boss exemptions')
p.add_argument('--weapon-abilities',action='store_true',help='Rare Adventurer weapon powers driven by a native player')
p.add_argument('--arkhaia-native',action='store_true',help='Arkhaia duel probe with official ModelEngine and Geyser')
p.add_argument('--acheron-split',action='store_true')
p.add_argument('--acheron-effects',action='store_true')
p.add_argument('--phlegethon',action='store_true')
p.add_argument('--banshee',action='store_true')
p.add_argument('--banshee-hunt',action='store_true')
p.add_argument('--river-trials',action='store_true')
p.add_argument('--cocytus',action='store_true')
p.add_argument('--malefeasance',action='store_true')
p.add_argument('--styx',action='store_true')
p.add_argument('--melee-impact',action='store_true')
p.add_argument('--sanctuary',action='store_true')
p.add_argument('--airborne',action='store_true')
p.add_argument('--direct-launch',action='store_true',help='Launch pre-extracted Paper from template classpath without Paperclip extraction')
p.add_argument('--prepare-only',action='store_true',help='Prepare and compile the isolated fixture without starting Java')
p.add_argument('--reward-hooks',action='store_true')
args=p.parse_args();args.pack_download=args.pack_download or args.pack_order;server=args.server.resolve();server.mkdir(parents=True,exist_ok=False)
args.banshee=args.banshee or args.banshee_hunt
for name in ('libraries','versions','cache'):
    target=server/name;source=(args.template/name).resolve()
    subprocess.run(['powershell','-NoProfile','-Command',"New-Item -ItemType Junction -Path '"+str(target).replace("'","''")+"' -Target '"+str(source).replace("'","''")+"' | Out-Null"],check=True)
shutil.copy2(args.template/'paper.jar',server/'paper.jar')
plugins=server/'plugins';plugins.mkdir()
shutil.copy2(args.garden_jar,plugins/'evergarden.jar');shutil.copy2(args.magic_jar,plugins/'advance-magic.jar')
if args.seraphon_native or args.arkhaia_native or args.ranged_geyser or args.pack_download:
    args.seraphon=args.seraphon or args.seraphon_native
    native=ROOT/'output/evergarden-bosses-me-v6-20261022/plugins'
    for name in ('ModelEngine.jar','Geyser-Spigot.jar','floodgate-spigot.jar'):
        if (native/name).is_file():shutil.copy2(native/name,plugins/name)
    if not (plugins/'ModelEngine.jar').is_file():raise FileNotFoundError('Official ModelEngine test dependency is unavailable')
    with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as sock:
        sock.bind(('127.0.0.1',0));udp=sock.getsockname()[1]
    geyser=plugins/'Geyser-Spigot';geyser.mkdir(exist_ok=True)
    (geyser/'config.yml').write_text(f'bedrock:\n  address: 127.0.0.1\n  port: {udp}\nremote:\n  address: auto\n  port: 0\n  auth-type: floodgate\nmetrics:\n  enabled: false\n',encoding='utf8')
    (server/'seraphon-native.flag').write_text('official ModelEngine + optional Geyser integration\n')
for name in ('Evergarden','advance-magic'):
    folder=plugins/name;folder.mkdir()
    if args.pack_download:
        # The plugins write their full default configs; a mirror template only overrides that one list.
        # config-version 2 stops Evergarden from replacing an older-looking config with its defaults.
        if args.pack_mirror:(folder/'config.yml').write_text('config-version: 2\nresource-pack:\n  mirrors:\n  - '+args.pack_mirror+'\n',encoding='utf8')
        continue
    (folder/'config.yml').write_text('config-version: 2\nresource-pack:\n  enabled: false\n  host:\n    enabled: false\nworld-boss:\n  visuals:\n    pack:\n      host:\n        bind: 127.0.0.1\n        port: 0\n',encoding='utf8')
classes=server/'probe-classes';classes.mkdir()
classpath=os.pathsep.join([str(args.garden_jar.resolve()),str(args.magic_jar.resolve()),args.classpath_file.read_text(encoding='utf8')])
options=['--release','25','-proc:none','-encoding','UTF-8','-cp',classpath,'-d',str(classes),*map(str,Path(__file__).parent.resolve().glob('*.java')),*map(str,(ROOT/'docs/examples').glob('Arkhaia*Goal.java'))]
argfile=server/'probe-javac.args'
argfile.write_text('\n'.join('"'+str(v).replace('\\','/')+'"' for v in options),encoding='utf8')
subprocess.run(['java',str(ROOT/'tools/CompileJava.java'),str(argfile)],check=True)
for checks in ('ArkhaiaBrainChecks','ArkhaiaSovereignChecks','SeraphonRulesChecks'):subprocess.run(['java','-cp',str(classes)+os.pathsep+str(args.garden_jar.resolve()),checks],check=True)
fixture=server/'fixture-classes';fixture.mkdir()
shutil.copy2(classes/'LevelledMobsFixture.class',fixture/'LevelledMobsFixture.class')
shutil.copytree(classes/'io',fixture/'io')
(fixture/'plugin.yml').write_text("name: LevelledMobs\nversion: 4.0.0-fixture\nmain: LevelledMobsFixture\napi-version: '26.2'\n")
subprocess.run(['jar','--create','--file',str(plugins/'LevelledMobs.jar'),'-C',str(fixture),'.'],check=True)
probe='BossHitboxProbe' if args.hitbox else 'AirborneCombatProbe' if args.airborne else 'MeleeImpactProbe' if args.melee_impact else 'StyxProbe' if args.styx else 'MalefeasanceProbe' if args.malefeasance else 'CocytusProbe' if args.cocytus else 'SanctuaryCombatProbe' if args.sanctuary else 'RiverTrialsProbe' if args.river_trials else 'BansheeHuntProbe' if args.banshee_hunt else 'BansheeProbe' if args.banshee else 'PhlegethonProbe' if args.phlegethon else 'AcheronEffectsProbe' if args.acheron_effects else 'AcheronSplitProbe' if args.acheron_split else 'BossContactProbe' if args.contact else 'BossArmorWearProbe' if args.armor_wear else 'ArkhaiaProbe'
if args.seraphon:probe='SeraphonProbe'
if args.arkhaia_native:probe='ArkhaiaDuelProbe'
if args.ranged_geyser:probe='RangedGeyserProbe'
if args.weapon_abilities:probe='AdventurerAbilityProbe'
if args.unique_balance:probe='UniqueBalanceProbe'
if args.world_survey:probe='WorldSurveyProbe'
if args.pack_download:probe='PackDownloadProbe'
if args.pack_order:probe='PackOrderProbe'
if args.armor_sets:probe='ArmorSetsProbe'
if args.armor_exchange:probe='BossArmorExchangeProbe'
if args.reward_hooks:probe='BossRewardHooksProbe'
(classes/'plugin.yml').write_text(f"name: ArkhaiaProbe\nversion: '1'\nmain: {probe}\napi-version: '26.2'\ndepend: [Evergarden, advance-magic, LevelledMobs]\n",encoding='utf8')
probe_files=['plugin.yml',*[f.name for f in classes.glob(probe+'*.class')]]
if args.seraphon:probe_files.extend(f.name for f in classes.glob('ArkhaiaProbe*.class'))
if args.armor_sets or args.armor_exchange:probe_files.extend(f.name for f in classes.glob('BossArmorWearProbe*.class'))
if args.hitbox:
    for parent in ('ArkhaiaProbe','BossContactProbe','SanctuaryProbeFixture'):probe_files.extend(f.name for f in classes.glob(parent+'*.class'))
if args.melee_impact:
    for parent in ('ArkhaiaProbe','BossContactProbe','AcheronSplitProbe'):probe_files.extend(f.name for f in classes.glob(parent+'*.class'))
if args.river_trials:
    for parent in ('ArkhaiaProbe','BossContactProbe','AcheronSplitProbe'):probe_files.extend(f.name for f in classes.glob(parent+'*.class'))
if args.cocytus:
    for parent in ('ArkhaiaProbe','BossContactProbe','AcheronSplitProbe','RiverTrialsProbe','SanctuaryProbeFixture'):probe_files.extend(f.name for f in classes.glob(parent+'*.class'))
if args.malefeasance:
    for parent in ('ArkhaiaProbe','BossContactProbe','AcheronSplitProbe','SanctuaryProbeFixture'):probe_files.extend(f.name for f in classes.glob(parent+'*.class'))
if args.styx:
    for parent in ('ArkhaiaProbe','BossContactProbe','AcheronSplitProbe','RiverTrialsProbe'):probe_files.extend(f.name for f in classes.glob(parent+'*.class'))
if args.sanctuary:
    for parent in ('ArkhaiaProbe','SkillRewardChecks','ArmorSanctuaryChecks'):probe_files.extend(f.name for f in classes.glob(parent+'*.class'))
if args.airborne:
    for parent in ('ArkhaiaProbe','SanctuaryCombatProbe','SkillRewardChecks','ArmorSanctuaryChecks'):probe_files.extend(f.name for f in classes.glob(parent+'*.class'))
if args.reward_hooks:probe_files.extend(f.name for f in classes.glob('ArkhaiaProbe*.class'))
if args.banshee_hunt:probe_files.extend(f.name for f in classes.glob('BansheeProbe*.class'))
if args.contact or args.acheron_split or args.acheron_effects or args.phlegethon or args.banshee:probe_files.extend(f.name for f in classes.glob('ArkhaiaProbe*.class'))
if args.acheron_split or args.acheron_effects or args.phlegethon or args.banshee:probe_files.extend(f.name for f in classes.glob('BossContactProbe*.class'))
if args.acheron_effects or args.phlegethon or args.banshee:probe_files.extend(f.name for f in classes.glob('AcheronSplitProbe*.class'))
subprocess.run(['jar','--create','--file',str(plugins/'arkhaia-probe.jar'),*[arg for name in probe_files for arg in ('-C',str(classes),name)]],check=True)
(server/'eula.txt').write_text('eula=true\n')
if args.pack_unreachable:(server/'pack-unreachable.flag').write_text('server-side pack HTTP is unreachable\n')
(server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-name=preview_test\nlevel-type=minecraft:flat\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\npause-when-empty-seconds=-1\nmax-tick-time=120000\n')
if args.pack_download:
    # Pack URLs carry the real game port, so this server cannot use an ephemeral one.
    with socket.socket() as sock:
        sock.bind(('127.0.0.1',0));game_port=sock.getsockname()[1]
    (server/'server.properties').write_text((server/'server.properties').read_text().replace('server-port=0','server-port=%d'%game_port))
if args.sanctuary or args.airborne or args.world_survey:
    properties=(server/'server.properties').read_text().replace('level-type=minecraft:flat\n',f'level-seed={args.level_seed}\n')
    (server/'server.properties').write_text(properties)
java_options=['-Xms256M','-Xmx3G','-Dterminal.jline=false','-Dterminal.ansi=false']
if args.direct_launch:
    launch_classpath=[]
    with ZipFile(server/'paper.jar') as bundle:
        # The patched server bundles replacements for classes also present in libraries.
        for folder in ('versions','libraries'):
            for line in bundle.read(f'META-INF/{folder}.list').decode('utf8').splitlines():
                if not line.strip():continue
                fields=line.split('\t')
                if len(fields)!=3:raise ValueError(f'Malformed Paper {folder} entry: {line}')
                target=server/folder/fields[2]
                if not target.is_file():raise FileNotFoundError(f'Pre-extracted Paper dependency missing: {target}')
                launch_classpath.append(str(target))
    # Latest menu classes reference the pure Cumulus API during enable. Expose only
    # its pinned compile dependency to the parent loader; no Geyser service is needed.
    for entry in args.classpath_file.read_text(encoding='utf8').strip().split(os.pathsep):
        dependency=Path(entry.strip())
        if dependency.name.startswith('cumulus-') and dependency.suffix.lower()=='.jar':
            if not dependency.is_file():raise FileNotFoundError(f'Cumulus API dependency missing: {dependency}')
            launch_classpath.append(str(dependency.resolve()))
    java_options.extend(['-cp',os.pathsep.join(launch_classpath),'org.bukkit.craftbukkit.Main'])
else:java_options.extend(['-jar','paper.jar'])
java_options.extend(['--nogui','--noconsole'])
# Java's argument file also keeps the expanded Windows classpath below the process command-line limit.
launch_args=server/'paper-launch.args'
launch_args.write_text('\n'.join('"'+str(v).replace('\\','/')+'"' for v in java_options),encoding='utf8')
if args.prepare_only:
    print('Prepared isolated fixture:',server,flush=True)
    raise SystemExit(0)
with (server/'console.log').open('w',encoding='utf8') as log:
    subprocess.run(['java','@'+str(launch_args)],cwd=server,stdout=log,stderr=subprocess.STDOUT,timeout=600 if args.sanctuary or args.airborne or args.seraphon_native or args.arkhaia_native else 180,check=True,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
report=(server/'arkhaia-result.txt').read_text(encoding='utf8');print(report,flush=True);print(server/'console.log',flush=True)
raise SystemExit(0 if report.startswith('PASS') else 1)
