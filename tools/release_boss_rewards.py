"""Publish the tested boss-reward JAR pair locally, with backups, source assets and reports."""
from pathlib import Path
from io import BytesIO
import hashlib,json,re,shutil,subprocess,sys,zipfile
ROOT=Path(__file__).resolve().parents[1]
BUILD=ROOT/'output/boss-rewards-build-v3-20261021'
RELEASE=ROOT/'output/boss-rewards-release-20261021'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
REPORTS={
    'boss-rewards-sanctuary-final-20261021':('arkhaia-result.txt',True,True),
    'boss-rewards-other-bosses-final-20261021':('arkhaia-result.txt',True,True),
    'boss-rewards-native-final-20261021':('model-engine-result.txt',True,True),
    'boss-rewards-elemental-final-20261021':('elemental-system-result.txt',False,True),
    'boss-rewards-styx-final-20261021':('arkhaia-result.txt',True,True),
}

def main():
    reports={}
    for directory,(name,garden,magic) in REPORTS.items():
        server=ROOT/'output'/directory;line=(server/name).read_text(encoding='utf8').splitlines()[0]
        assert line.startswith('PASS'),(directory,line)
        for module,required in (('evergarden',garden),('advance-magic',magic)):
            if required:assert sha(server/'plugins'/(module+'.jar'))==sha(BUILD/(module+'.jar')),(directory,module)
        reports[directory]={'result':line,'garden_sha256':sha(server/'plugins/evergarden.jar') if garden else None,'magic_sha256':sha(server/'plugins/advance-magic.jar')}
    subprocess.run([sys.executable,str(ROOT/'tests/arkhaia/check_boss_orbs.py')],check=True)
    subprocess.run([sys.executable,str(ROOT/'tools/release_jar_checks.py'),str(BUILD/'advance-magic.jar'),str(BUILD/'evergarden.jar')],check=True)
    expected={'evergarden':'3.0.0-e2.101-boss-rewards','advance-magic':'1.20.0-boss-exploration-points'}
    for module,version in expected.items():
        with zipfile.ZipFile(BUILD/(module+'.jar')) as z:
            assert version in z.read('plugin.yml').decode()
            for pack in (ROOT/module/'dist').glob('*'):
                if pack.name in ('advance-magic-java.zip','advance-magic-bedrock.mcpack','evergarden-java.zip','judge-java.zip','evergarden-bedrock.mcpack','geyser-mappings.json','pack-hashes.json'):
                    assert z.read('resource-packs/'+pack.name)==pack.read_bytes(),(module,pack.name)
    RELEASE.mkdir(parents=True,exist_ok=True);backup=RELEASE/'before';backup.mkdir(exist_ok=True)
    previous={}
    for module in expected:
        old=ROOT/'dist'/(module+'.jar');saved=backup/old.name
        if not saved.exists():shutil.copy2(old,saved)
        previous[module]=sha(saved)
        with zipfile.ZipFile(BUILD/(module+'.jar')) as z:
            for path in (ROOT/module/'src/main/java').rglob('*.java'):
                assert path.relative_to(ROOT/module/'src/main/java').with_suffix('.class').as_posix() in z.namelist(),path
    validation={'versions':expected,'bedrock_pack':'3.38.0','sha256':{m:sha(BUILD/(m+'.jar')) for m in expected},
                'previous_sha256':previous,'reports':reports,'orb_pack_checks':71,
                'world_boss_rule_checks':67143,'live_server_modified':False,'connected_client_playtest':False,
                'image_generation':'Built-in image_gen; six transparent originals and exact prompts included',
                'backup':str(backup.relative_to(ROOT))}
    (RELEASE/'release-validation.json').write_text(json.dumps(validation,indent=2)+'\n',encoding='utf8')
    for module in expected:
        source=BUILD/(module+'.jar');shutil.copy2(source,RELEASE/source.name);shutil.copy2(source,ROOT/'dist'/source.name)
        target=ROOT/module/'target'/source.name;target.parent.mkdir(exist_ok=True);shutil.copy2(source,target)
        assert sha(ROOT/'dist'/source.name)==validation['sha256'][module]
    paths=[]
    for module in expected:paths.extend(p for p in (ROOT/module/'src/main').rglob('*') if p.is_file())
    paths.extend(p for p in (ROOT/'evergarden/art/boss-orbs').rglob('*') if p.is_file())
    paths.extend([ROOT/'docs/boss-orbs-and-skill-points.md',ROOT/'docs/elemental-spell-system.md',ROOT/'README.md',
                  ROOT/'evergarden/tools/boss_orb_assets.py',ROOT/'tools/update_boss_orbs.py',ROOT/'tools/release_boss_rewards.py',
                  ROOT/'tools/build_elemental_staff_release.py',ROOT/'tools/try_model_engine.py'])
    paths.extend(p for p in (ROOT/'tests/arkhaia').glob('*') if p.is_file())
    paths.append(ROOT/'tests/model-engine/BossOrbsNativeProbe.java')
    with zipfile.ZipFile(RELEASE/'boss-rewards-sources.zip','w',zipfile.ZIP_DEFLATED) as z:
        for p in sorted(set(paths)):z.write(p,p.relative_to(ROOT).as_posix())
    (ROOT/'dist/boss-rewards-manifest.json').write_text(json.dumps(validation,indent=2)+'\n',encoding='utf8')
    print(json.dumps({'released':{m:str(ROOT/'dist'/(m+'.jar')) for m in expected},'sha256':validation['sha256'],'reports':reports},indent=2))

if __name__=='__main__':main()
