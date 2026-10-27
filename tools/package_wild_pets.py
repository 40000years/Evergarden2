"""Publish the exact isolated-server-tested wild pet release, preserving prior assets."""
import hashlib
import json
from pathlib import Path
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / 'output/wild-pets-build-v3-20261021/evergarden.jar'
BASE = ROOT / 'output/model-restore-release-20261021/evergarden.jar'
RELEASE = ROOT / 'output/wild-pets-release-20261021'
EXPECTED = '035aa46a82ddf3ccc1e3cb16084bfef2d36d8078270da43fce621dfef5c89588'
VERSION = '3.0.0-e2.105-wild-pets'
CASES = [
    ('output/wild-pets-native-final-v3-20261021', 1631),
    ('output/wild-pets-boss-restore-final-v3-20261021', 209),
]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    assert sha(JAR) == EXPECTED
    cases = []
    for directory, count in CASES:
        runtime = ROOT / directory
        result = (runtime / 'model-engine-result.txt').read_text(encoding='utf8')
        assert result.startswith('PASS:') and f'/ {count} checks' in result.splitlines()[0]
        assert sha(runtime / 'plugins/evergarden.jar') == EXPECTED, 'Tested JAR differs'
        cases.append(dict(runtime=directory, checks=count, result=result.splitlines()[0]))
    assets_path = ROOT / 'output/native-pet-assets-validation-20261021.json'
    assets = json.loads(assets_path.read_text(encoding='utf8'))
    assert assets['result'] == 'PASS' and assets['checks'] == 170699
    with zipfile.ZipFile(JAR) as new, zipfile.ZipFile(BASE) as old:
        assert new.testzip() is None and VERSION.encode() in new.read('plugin.yml')
        for name in ('plugin.yml', 'config.yml', 'native-pets.json', 'native-pets.zip'):
            assert new.read(name) == (ROOT / 'evergarden/src/main/resources' / name).read_bytes()
        preserved = 0
        for name in old.namelist():
            if not name.endswith('.class') and name not in ('plugin.yml', 'config.yml'):
                assert old.read(name) == new.read(name), 'Existing asset changed: ' + name
                preserved += 1
        models = [n for n in new.namelist() if n.startswith('modelengine/') and n.endswith('.bbmodel')]
        pets = [n for n in models if n.startswith('modelengine/pets/')]
        assert len(models) == 59 and len(pets) == 51
        for name in ('ModelMeleeImpact', 'ArkhaiaHitFeedback'):
            entry = 'com/example/voidscape/boss/' + name + '.class'
            assert old.read(entry) == new.read(entry), 'Melee impact code changed'
        assert b'ModelMeleeImpact' in new.read('com/example/voidscape/boss/WorldBossManager.class')
        assert not [n for n in new.namelist() if n.endswith('.class')
                    and b'Unresolved compilation problem' in new.read(n)]
        native = json.loads(new.read('native-pets.json'))
        assert len(native['models']) == 36
        assert hashlib.sha256(new.read('native-pets.zip')).hexdigest() == assets['pack_sha256']
        for definition in native['models'].values():
            original = new.read('modelengine/pets/everpet_' + definition['species'] + '_' + definition['stage'] + '.bbmodel')
            assert hashlib.sha256(original).hexdigest() == definition['source_sha256']

    RELEASE.mkdir(parents=True, exist_ok=True)
    before = RELEASE / 'before'
    before.mkdir(exist_ok=True)
    backup = before / 'evergarden.jar'
    if backup.exists():
        assert sha(backup) == sha(BASE)
    else:
        shutil.copy2(BASE, backup)
    destination = ROOT / 'dist/evergarden.jar'
    if destination.exists() and sha(destination) not in (sha(BASE), EXPECTED):
        other = before / ('evergarden-dist-' + sha(destination) + '.jar')
        if not other.exists():
            shutil.copy2(destination, other)

    sources = [
        'evergarden/src/main/java/com/example/voidscape/VoidscapePlugin.java',
        'evergarden/src/main/java/com/example/voidscape/pet/WildPetSpawns.java',
        'evergarden/src/main/java/com/example/voidscape/pet/NativePetRenderer.java',
        'evergarden/src/main/java/com/example/voidscape/pet/PetCatalog.java',
        'evergarden/src/main/java/com/example/voidscape/boss/JudgePackService.java',
        'evergarden/src/main/java/com/example/voidscape/command/VoidCommand.java',
        'evergarden/src/main/java/com/example/voidscape/dungeon/DungeonManager.java',
        'evergarden/src/main/java/com/example/voidscape/boss/WorldBossManager.java',
        'evergarden/src/main/java/com/example/voidscape/boss/SanctuaryEncounters.java',
        'evergarden/src/main/resources/config.yml', 'evergarden/src/main/resources/plugin.yml',
        'evergarden/src/main/resources/native-pets.json', 'evergarden/src/main/resources/native-pets.zip',
        'tools/export_native_pet_models.py', 'tests/model-engine/check_native_pet_models.py',
        'tests/model-engine/WildPetsNativeProbe.java', 'tests/model-engine/ModelRestoreProbe.java',
        'tools/try_model_engine.py', 'tools/build_pet_release.py', 'tools/CompileJava.java',
        'tools/package_wild_pets.py', 'docs/wild-pets.md',
    ]
    with zipfile.ZipFile(RELEASE / 'wild-pets-sources.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
        for name in sources:
            archive.write(ROOT / name, name)
    validation = dict(version=VERSION, jar_sha256=EXPECTED, previous_jar_sha256=sha(BASE),
        native_cases=cases, native_checks=sum(c['checks'] for c in cases), asset_checks=assets['checks'],
        eligible_species=12, native_variants=36, mythical_natural_spawns=False,
        stage_weights=dict(baby=85, medium=13, adult=2), attempt_interval_seconds=45,
        chance_per_eligible_player=.12, world_cap=16, nearby_player_cap=2,
        original_modelengine_assets_changed=False, preserved_boss_models=8, preserved_pet_blueprints=51,
        preserved_resource_entries=preserved, bundled_base_packs_changed=False, served_judge_pack_extended=True,
        native_pet_modelengine_slots=0, melee_impact_helper_classes_changed=False,
        inherited_workspace_boss_out_of_arena_reminders_preserved=True,
        java_pack_required=True, bedrock_native_pet_models=False, connected_client_visual_review=False,
        live_server_modified=False)
    manifest = RELEASE / 'release-validation.json'
    manifest.write_text(json.dumps(validation, indent=2) + '\n', encoding='utf8')
    for directory, _ in CASES:
        runtime = ROOT / directory
        shutil.copy2(runtime / 'model-engine-result.txt', RELEASE / (runtime.name + '-result.txt'))
    shutil.copy2(assets_path, RELEASE / assets_path.name)
    shutil.copy2(ROOT / 'docs/wild-pets.md', RELEASE / 'wild-pets.md')
    for path in (RELEASE / 'evergarden.jar', destination, ROOT / 'evergarden/target/evergarden.jar'):
        path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(JAR, path)
        assert sha(path) == EXPECTED
    shutil.copy2(manifest, ROOT / 'dist/wild-pets-manifest.json')
    print(json.dumps(validation, indent=2))


if __name__ == '__main__':
    main()
