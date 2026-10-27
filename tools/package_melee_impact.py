"""Publish the exact locally tested melee-audio JAR with a previous-release backup."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import zipfile

ROOT=Path(__file__).resolve().parents[1]


def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--build',type=Path,required=True);p.add_argument('--runtime',type=Path,required=True)
    p.add_argument('--release',type=Path,default=ROOT/'output/melee-impact-release-20261021')
    a=p.parse_args();jar=a.build.resolve()/'evergarden.jar';runtime=a.runtime.resolve();release=a.release.resolve()
    report=(runtime/'arkhaia-result.txt').read_text(encoding='utf8');assert report.startswith('PASS'),report
    assert sha(jar)==sha(runtime/'plugins/evergarden.jar'),'Native checks must use the released bytes'
    with zipfile.ZipFile(jar) as new,zipfile.ZipFile(ROOT/'dist/evergarden.jar') as old:
        assert b'3.0.0-e2.103-melee-impact' in new.read('plugin.yml')
        preserved=0;workspace_resources=[]
        for name in old.namelist():
            if not name.endswith('.class') and name!='plugin.yml':
                if name=='config.yml' and new.read(name)!=old.read(name):
                    # Keep the user's existing altar/cooldown configuration work;
                    # it predates this audio task and matches the current source.
                    assert new.read(name)==(ROOT/'evergarden/src/main/resources/config.yml').read_bytes()
                    workspace_resources.append(name);continue
                assert new.read(name)==old.read(name),'Unrelated resource changed: '+name
                preserved+=1
        assert len([n for n in new.namelist() if n.startswith('modelengine/pets/') and n.endswith('.bbmodel')])==51
        assert not [n for n in new.namelist() if n.endswith('.class') and b'Unresolved compilation problem' in new.read(n)]
        assert new.testzip() is None
    release.mkdir(parents=True,exist_ok=True);backup=release/'before';backup.mkdir(exist_ok=True)
    if not (backup/'evergarden.jar').exists():shutil.copy2(ROOT/'dist/evergarden.jar',backup/'evergarden.jar')
    files=[
        ROOT/'evergarden/src/main/java/com/example/voidscape/boss/ModelMeleeImpact.java',
        ROOT/'evergarden/src/main/java/com/example/voidscape/boss/ArkhaiaHitFeedback.java',
        ROOT/'evergarden/src/main/java/com/example/voidscape/boss/WorldBossManager.java',
        ROOT/'evergarden/src/main/resources/plugin.yml',ROOT/'tests/arkhaia/MeleeImpactProbe.java',
        ROOT/'evergarden/src/main/resources/config.yml',
        ROOT/'tests/arkhaia/run.py',ROOT/'tools/CompileJava.java',ROOT/'tools/build_pet_release.py',
        ROOT/'tools/package_melee_impact.py',ROOT/'docs/model-melee-impact.md']
    with zipfile.ZipFile(release/'melee-impact-sources.zip','w',zipfile.ZIP_DEFLATED) as z:
        for file in files:z.write(file,file.relative_to(ROOT).as_posix())
    validation=dict(version='3.0.0-e2.103-melee-impact',jar_sha256=sha(jar),
        previous_jar_sha256=sha(backup/'evergarden.jar'),native_result=report.splitlines()[0],
        preserved_resource_entries=preserved,pet_model_count=51,resource_pack_changed=False,
        inherited_current_workspace_resources=workspace_resources,
        live_server_modified=False,connected_client_audio_review=False,runtime=str(runtime.relative_to(ROOT)))
    (release/'release-validation.json').write_text(json.dumps(validation,indent=2)+'\n',encoding='utf8')
    shutil.copy2(runtime/'arkhaia-result.txt',release/'arkhaia-result.txt')
    for dest in (release/'evergarden.jar',ROOT/'dist/evergarden.jar',ROOT/'evergarden/target/evergarden.jar'):shutil.copy2(jar,dest)
    shutil.copy2(release/'release-validation.json',ROOT/'dist/melee-impact-manifest.json')
    assert sha(ROOT/'dist/evergarden.jar')==validation['jar_sha256']
    print(json.dumps(validation,indent=2))


if __name__=='__main__':main()
