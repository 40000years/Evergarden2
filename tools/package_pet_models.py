"""Publish a verified local pet release, retaining the previous Evergarden JAR."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import zipfile

ROOT=Path(__file__).resolve().parents[1]
MODELS=ROOT/'evergarden/src/main/resources/modelengine/pets'
VERSION='3.0.0-e2.102-mythical-pets'


def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--build',type=Path,required=True)
    p.add_argument('--runtime',type=Path,required=True)
    p.add_argument('--release',type=Path,default=ROOT/'output/pet-models-release-20261021')
    a=p.parse_args();jar=a.build.resolve()/'evergarden.jar';runtime=a.runtime.resolve()
    report=(runtime/'model-engine-result.txt').read_text(encoding='utf8')
    assert report.startswith('PASS'),report.splitlines()[0]
    assert sha(jar)==sha(runtime/'plugins/evergarden.jar'),'Native probe must use exactly the published JAR'
    asset_report=(ROOT/'output/pet-models-asset-checks.txt').read_text(encoding='utf8')
    assert asset_report.startswith('PASS')
    manifest=json.loads((MODELS/'manifest.json').read_text(encoding='utf8'))
    assert len(manifest)==51
    with zipfile.ZipFile(jar) as z:
        assert VERSION in z.read('plugin.yml').decode()
        for entry in manifest:
            name=entry['id']+'.bbmodel';path=MODELS/name
            assert sha(path)==entry['sha256']
            assert z.read('modelengine/pets/'+name)==path.read_bytes(),name
        assert not [name for name in z.namelist() if name.endswith('.class') and b'Unresolved compilation problem' in z.read(name)]
        with zipfile.ZipFile(ROOT/'dist/evergarden.jar') as old:
            for name in old.namelist():
                if name.startswith('modelengine/rivers/') or name.startswith('resource-packs/'):
                    assert z.read(name)==old.read(name),'Existing boss/pack changed: '+name
    release=a.release.resolve();release.mkdir(parents=True,exist_ok=True)
    backup=release/'before';backup.mkdir(exist_ok=True)
    old=ROOT/'dist/evergarden.jar';saved=backup/'evergarden.jar'
    if not saved.exists():shutil.copy2(old,saved)
    with zipfile.ZipFile(release/'evergarden-pet-models.zip','w',zipfile.ZIP_DEFLATED) as z:
        for file in sorted(MODELS.glob('*.bbmodel')):z.write(file,'blueprints/'+file.name)
        z.write(MODELS/'manifest.json','manifest.json')
        z.write(ROOT/'docs/pet-models.md','README.md')
    source_files=[*(ROOT/'evergarden/src/main/java/com/example/voidscape/pet').glob('*.java'),
        ROOT/'evergarden/src/main/java/com/example/voidscape/VoidscapePlugin.java',
        ROOT/'evergarden/src/main/java/com/example/voidscape/command/VoidCommand.java',
        ROOT/'evergarden/src/main/java/com/example/voidscape/dungeon/DungeonManager.java',
        ROOT/'evergarden/src/main/java/com/example/voidscape/world/SanctuaryHabitat.java',
        ROOT/'evergarden/src/main/resources/plugin.yml',ROOT/'evergarden/src/main/resources/config.yml',
        ROOT/'evergarden/tools/pet_model_engine.py',ROOT/'evergarden/tools/pet_mythical_sculpt.py',ROOT/'evergarden/tools/pet_rare_sculpt.py',
        ROOT/'tools/preview_pet_models.py',ROOT/'tools/preview_arkhaia_rig.py',ROOT/'tools/build_pet_release.py',
        ROOT/'tools/package_pet_models.py',ROOT/'tools/try_model_engine.py',ROOT/'tools/CompileJava.java',
        ROOT/'tests/model-engine/check_pet_models.py',ROOT/'tests/model-engine/PetModelsNativeProbe.java',
        ROOT/'tests/model-engine/PetBlueprintChecks.java',ROOT/'docs/pet-models.md']
    with zipfile.ZipFile(release/'pet-models-sources.zip','w',zipfile.ZIP_DEFLATED) as z:
        for file in source_files:z.write(file,file.relative_to(ROOT).as_posix())
        for file in MODELS.glob('*'):z.write(file,file.relative_to(ROOT).as_posix())
    preview=ROOT/'previews/pet-models'
    with zipfile.ZipFile(release/'evergarden-pet-preview.zip','w',zipfile.ZIP_DEFLATED) as z:
        for file in sorted(preview.iterdir()):
            if file.suffix in ('.png','.gif','.html') and '.tmp.' not in file.name:z.write(file,file.name)
    validation=dict(version=VERSION,model_count=51,species_count=17,stages=['baby','medium','adult'],
        clips=['idle','walk','fly','sit','greet'],jar_sha256=sha(jar),previous_jar_sha256=sha(saved),
        native_report=report.splitlines()[0],asset_report=asset_report.strip(),
        runtime=str(runtime.relative_to(ROOT)),native_test_mode='bounded trial batches; unrestricted simultaneous collection requires full ModelEngine',
        supported_client='Java Edition',abilities=False,riding=False,live_server_modified=False,connected_client_playtest=False)
    (release/'release-validation.json').write_text(json.dumps(validation,indent=2)+'\n',encoding='utf8')
    shutil.copy2(jar,release/'evergarden.jar');shutil.copy2(jar,ROOT/'dist/evergarden.jar')
    target=ROOT/'evergarden/target';target.mkdir(exist_ok=True);shutil.copy2(jar,target/'evergarden.jar')
    shutil.copy2(release/'evergarden-pet-models.zip',ROOT/'dist/evergarden-pet-models.zip')
    shutil.copy2(release/'evergarden-pet-preview.zip',ROOT/'dist/evergarden-pet-preview.zip')
    shutil.copy2(release/'release-validation.json',ROOT/'dist/pet-models-manifest.json')
    shutil.copy2(runtime/'model-engine-result.txt',release/'model-engine-result.txt')
    shutil.copy2(ROOT/'output/pet-models-asset-checks.txt',release/'asset-checks.txt')
    assert sha(ROOT/'dist/evergarden.jar')==validation['jar_sha256']
    print(json.dumps(validation,indent=2))


if __name__=='__main__':main()
