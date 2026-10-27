"""Boot an isolated copy of a local Paper runtime with the official Model Engine trial."""
import argparse,os,shutil,subprocess,socket,hashlib,zipfile
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--source-server',type=Path,required=True)
p.add_argument('--plugin',type=Path,required=True)
p.add_argument('--test-directory',type=Path,required=True)
p.add_argument('--garden-jar',type=Path)
p.add_argument('--magic-jar',type=Path)
p.add_argument('--boss-orbs',action='store_true')
p.add_argument('--pets',action='store_true')
p.add_argument('--model-restore',action='store_true',help='Seed the old 51-pet installation and verify normal startup restores every boss')
p.add_argument('--model-restore-species',default='',help='Explicit species to stage during --model-restore, e.g. aetherius')
p.add_argument('--wild-pets',action='store_true',help='Verify all nonmythical wild pets, rarity, protected spawns and display cleanup')
p.add_argument('--pet-system',action='store_true',help='Verify the Seraph exchange, taming, companions, growth, persistence and breeding')
p.add_argument('--pet-facing',action='store_true',help='Verify actual mounted display rotations and visible forward travel on Paper')
p.add_argument('--adventurer-ranged',action='store_true',help='Verify native Thornwhisper and Runelock use packets, continuous draw, loading and shooting')
p.add_argument('--wild-terrain',action='store_true',help='Diagnose natural wild-pet spots on a copy of the real world (needs --world-copy)')
p.add_argument('--world-copy',action='store_true',help='Copy the source world and Evergarden data files (never the live config) into the trial')
p.add_argument('--arkhaia',action='store_true')
p.add_argument('--boss-readiness',action='store_true',help='Corrupt one river blueprint and verify Arkhaia/Seraphon stay available')
p.add_argument('--river-poses',action='store_true')
p.add_argument('--malefeasance',action='store_true')
p.add_argument('--styx',action='store_true')
p.add_argument('--previous-blueprint',type=Path)
p.add_argument('--previous-managed',action='store_true')
args=p.parse_args();source=args.source_server.resolve();trial=args.test_directory.resolve()
assert source.is_dir() and args.plugin.is_file()
assert trial!=source and not trial.exists(), 'Use a fresh test directory'
trial.mkdir(parents=True)
for name in ('libraries','versions','cache'):
    shutil.copytree(source/name,trial/name)
paper=max(source.glob('paper-*.jar'),key=lambda f:f.stat().st_mtime,default=source/'paper.jar')
shutil.copy2(paper,trial/'paper.jar')
plugins=trial/'plugins';plugins.mkdir()
for jar in (source/'plugins').glob('*.jar'):
    if 'modelengine' not in jar.name.lower():shutil.copy2(jar,plugins/jar.name)
shutil.copy2(args.plugin,plugins/'ModelEngine.jar')
if args.garden_jar:shutil.copy2(args.garden_jar,plugins/'evergarden.jar')
if args.magic_jar:shutil.copy2(args.magic_jar,plugins/'advance-magic.jar')
if args.model_restore:
    assert args.garden_jar, '--model-restore requires --garden-jar'
    # Reproduce an existing e2.102/e2.103 installation before the new plugin
    # onLoad executes. Never modify ModelEngine or its license/import limits.
    with zipfile.ZipFile(args.garden_jar) as bundle:
        pets=[name for name in bundle.namelist() if name.startswith('modelengine/pets/everpet_') and name.endswith('.bbmodel')]
        assert len(pets)==51, 'Expected the existing complete pet collection'
        for name in pets:
            data=bundle.read(name);model=plugins/'ModelEngine/blueprints'/Path(name).name
            model.parent.mkdir(parents=True,exist_ok=True);model.write_bytes(data)
            digest=hashlib.sha256(data).hexdigest()
            for marker in (plugins/'Evergarden/modelengine/pets'/f'{model.stem}.sha256',plugins/'ModelEngine/evergarden-pets'/f'{model.stem}.sha256'):
                marker.parent.mkdir(parents=True,exist_ok=True);marker.write_text(digest)
    (trial/'model-restore-species.txt').write_text(args.model_restore_species,encoding='utf8')
if args.previous_blueprint:
    model=plugins/'ModelEngine/blueprints/arkhaia.bbmodel';model.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(args.previous_blueprint,model)
    if args.previous_managed:
        marker=plugins/'Evergarden/modelengine/arkhaia.sha256';marker.parent.mkdir(parents=True,exist_ok=True)
        marker.write_text(hashlib.sha256(model.read_bytes()).hexdigest())
if args.world_copy:
    shutil.copytree(source/'world',trial/'model_engine_trial',ignore=shutil.ignore_patterns('session.lock'))
    data=plugins/'Evergarden';data.mkdir(exist_ok=True)
    for f in (source/'plugins/Evergarden').glob('*.yml'):
        if f.name!='config.yml':shutil.copy2(f,data/f.name)
for name in ('Evergarden','advance-magic'):
    folder=plugins/name;folder.mkdir(exist_ok=True)
    config='config-version: 2\nresource-pack:\n  enabled: false\n  host:\n    enabled: false\nworld-boss:\n  visuals:\n    pack:\n      host:\n        bind: 127.0.0.1\n        public-host: 127.0.0.1\n        port: 0\n'
    if args.model_restore and args.model_restore_species:
        config+=f'pets:\n  model-preview:\n    install-all-models: false\n    install-species: [{args.model_restore_species}]\n'
    (folder/'config.yml').write_text(config,encoding='utf8')
with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as sock:
    sock.bind(('127.0.0.1',0));udp=sock.getsockname()[1]
geyser=plugins/'Geyser-Spigot';geyser.mkdir(exist_ok=True)
(geyser/'config.yml').write_text(f'bedrock:\n  address: 127.0.0.1\n  port: {udp}\nremote:\n  address: auto\n  port: 0\n  auth-type: floodgate\nmetrics:\n  enabled: false\n',encoding='utf8')
stats=plugins/'bStats';stats.mkdir();(stats/'config.yml').write_text('enabled: false\n')
classes=trial/'probe-classes';classes.mkdir()
classpath=os.pathsep.join([*(str(f) for folder in ('libraries','versions') for f in (trial/folder).rglob('*.jar')),*(str(f) for f in plugins.glob('*.jar'))])
probe='ArkhaiaModelEngineProbe' if args.arkhaia else 'ModelEngineTrialProbe'
if args.malefeasance:probe='MalefeasanceNativeProbe'
if args.styx:probe='StyxNativeProbe'
if args.boss_orbs:probe='BossOrbsNativeProbe'
if args.pets:probe='PetModelsNativeProbe'
if args.model_restore:probe='ModelRestoreProbe'
if args.wild_pets:probe='WildPetsNativeProbe'
if args.pet_system:probe='PetSystemNativeProbe'
if args.pet_facing:probe='PetFacingNativeProbe'
if args.adventurer_ranged:probe='AdventurerRangedNativeProbe'
if args.wild_terrain:probe='WildSpawnTerrainProbe'
if args.boss_readiness:probe='BossReadinessProbe'
if args.river_poses:
    assert args.arkhaia,'--river-poses requires --arkhaia'
    probe='RiverNativePoseProbe'
sources=[str(ROOT/f'tests/model-engine/{probe}.java')]
if args.pet_facing or args.adventurer_ranged:sources.append(str(ROOT/'tests/model-engine/PetSystemNativeProbe.java'))
if args.river_poses:sources.append(str(ROOT/'tests/model-engine/ArkhaiaModelEngineProbe.java'))
opts=['--release','25','-proc:none','-encoding','UTF-8','-cp',classpath,'-d',str(classes),*sources]
argfile=trial/'probe-javac.args';argfile.write_text('\n'.join('"'+str(v).replace('\\','/')+'"' for v in opts),encoding='utf8')
subprocess.run(['java',str(ROOT/'tools/CompileJava.java'),str(argfile)] if args.pets or args.model_restore or args.wild_pets or args.pet_system or args.pet_facing or args.adventurer_ranged or args.wild_terrain else ['javac','@'+str(argfile)],check=True)
(classes/'plugin.yml').write_text(f"name: ModelEngineTrialProbe\nversion: '1'\nmain: {probe}\napi-version: '26.2'\nsoftdepend: [ModelEngine, Evergarden, advance-magic, LevelledMobs, Geyser-Spigot, floodgate]\n",encoding='utf8')
subprocess.run(['jar','--create','--file',str(plugins/'model-engine-trial-probe.jar'),'-C',str(classes),'.'],check=True)
(trial/'eula.txt').write_text((source/'eula.txt').read_text())
(trial/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-name=model_engine_trial\nlevel-type=minecraft:flat\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\npause-when-empty-seconds=-1\nmax-tick-time=120000\n')
with (trial/'console.log').open('w',encoding='utf8') as log:
    subprocess.run(['java','-Xms256M','-Xmx3072M','-Dterminal.jline=false','-Dterminal.ansi=false','-jar','paper.jar','--nogui','--noconsole'],cwd=trial,stdout=log,stderr=subprocess.STDOUT,timeout=480 if args.wild_terrain else 240,check=True,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
report=(trial/'model-engine-result.txt').read_text(encoding='utf8')
print(report if args.wild_terrain else report.splitlines()[0] if (args.wild_pets or args.pet_system or args.pet_facing or args.adventurer_ranged) and report.startswith('PASS') else '\n'.join([report.splitlines()[0], *report.splitlines()[-4:]]) if args.pet_facing or args.adventurer_ranged else report,flush=True)
raise SystemExit(0 if report.startswith('PASS') else 1)
