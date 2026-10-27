"""Publish the exact model restoration JAR checked on isolated native servers."""
import hashlib
import json
from pathlib import Path
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / 'output/model-restore-build-v1-20261021/evergarden.jar'
BASE = ROOT / 'output/melee-impact-release-20261021/evergarden.jar'
RELEASE = ROOT / 'output/model-restore-release-20261021'
EXPECTED_SHA = 'a4dd3267fdfab074a0acb940cd1cc9ce40085f045713a0190df3329d0d618da2'
VERSION = '3.0.0-e2.104-model-restore'
CASES = [
    ('output/model-restore-native-default-v3-20261021', 209),
    ('output/model-restore-native-manta-v1-20261021', 246),
]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    assert sha(BUILD) == EXPECTED_SHA, 'Build differs from the validated JAR'
    native = []
    for directory, checks in CASES:
        runtime = ROOT / directory
        result = (runtime / 'model-engine-result.txt').read_text(encoding='utf8')
        assert result.startswith('PASS: model restore'), result.splitlines()[0]
        assert f'/ {checks} checks /' in result.splitlines()[0]
        assert sha(runtime / 'plugins/evergarden.jar') == EXPECTED_SHA
        native.append(dict(runtime=directory, checks=checks, result=result.splitlines()[0]))

    with zipfile.ZipFile(BASE) as old, zipfile.ZipFile(BUILD) as new:
        assert new.testzip() is None
        assert VERSION.encode() in new.read('plugin.yml')
        assert new.read('config.yml') == (ROOT / 'evergarden/src/main/resources/config.yml').read_bytes()
        preserved = []
        for name in old.namelist():
            if name.endswith('.class') or name in ('config.yml', 'plugin.yml'):
                continue
            assert new.read(name) == old.read(name), 'Unrelated resource changed: ' + name
            preserved.append(name)
        models = [n for n in new.namelist() if n.startswith('modelengine/') and n.endswith('.bbmodel')]
        pets = [n for n in models if n.startswith('modelengine/pets/')]
        bosses = [n for n in models if n not in pets]
        assert len(pets) == 51 and len(bosses) == 8
        for name in ('ModelMeleeImpact', 'ArkhaiaHitFeedback', 'WorldBossManager'):
            entry = 'com/example/voidscape/boss/' + name + '.class'
            assert new.read(entry) == old.read(entry), 'Melee sound code changed: ' + name
        assert not [n for n in new.namelist() if n.endswith('.class')
                    and b'Unresolved compilation problem' in new.read(n)]

    RELEASE.mkdir(parents=True, exist_ok=True)
    backup = RELEASE / 'before'
    backup.mkdir(exist_ok=True)
    prior = backup / 'evergarden.jar'
    if prior.exists():
        assert sha(prior) == sha(BASE), 'Existing release backup differs'
    else:
        shutil.copy2(BASE, prior)
    destination = ROOT / 'dist/evergarden.jar'
    if destination.exists() and sha(destination) not in (EXPECTED_SHA, sha(BASE)):
        previous_dist = backup / ('evergarden-dist-' + sha(destination) + '.jar')
        if not previous_dist.exists():
            shutil.copy2(destination, previous_dist)

    sources = [
        'evergarden/src/main/java/com/example/voidscape/VoidscapePlugin.java',
        'evergarden/src/main/java/com/example/voidscape/boss/ModelEngineArkhaia.java',
        'evergarden/src/main/java/com/example/voidscape/pet/ModelEnginePetBackend.java',
        'evergarden/src/main/java/com/example/voidscape/pet/PetBlueprintStaging.java',
        'evergarden/src/main/resources/config.yml',
        'evergarden/src/main/resources/plugin.yml',
        'tests/model-engine/ModelRestoreProbe.java',
        'tests/model-engine/PetStagingChecks.java',
        'tools/try_model_engine.py', 'tools/CompileJava.java',
        'tools/build_pet_release.py', 'tools/package_model_restore.py',
        'docs/model-restoration.md', 'docs/pet-models.md',
    ]
    assert all((ROOT / name).is_file() for name in sources), 'Missing source archive input'
    with zipfile.ZipFile(RELEASE / 'model-restore-sources.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
        for name in sources:
            archive.write(ROOT / name, name)

    validation = dict(
        version=VERSION, jar_sha256=EXPECTED_SHA, previous_jar_sha256=sha(prior),
        native_cases=native, native_checks=sum(n['checks'] for n in native),
        staging_preservation_checks=13, boss_model_count=len(bosses), pet_model_count=len(pets),
        preserved_resource_entries=len(preserved), model_assets_changed=False,
        resource_pack_changed=False, melee_impact_classes_changed=False,
        default_active_pet_species=[], tested_selected_species=['abyssal_manta'],
        internal_fire_imported=True, live_server_modified=False,
        connected_client_playtest=False,
    )
    manifest = RELEASE / 'release-validation.json'
    manifest.write_text(json.dumps(validation, indent=2) + '\n', encoding='utf8')
    for directory, _ in CASES:
        runtime = ROOT / directory
        shutil.copy2(runtime / 'model-engine-result.txt', RELEASE / (runtime.name + '-result.txt'))
    staging = ROOT / 'output/model-restore-staging-checks-20261021/result.txt'
    assert staging.is_file() and '13' in staging.read_text(encoding='utf8') and 'PASS' in staging.read_text(encoding='utf8')
    shutil.copy2(staging, RELEASE / 'staging-result.txt')
    for path in (RELEASE / 'evergarden.jar', destination, ROOT / 'evergarden/target/evergarden.jar'):
        path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(BUILD, path)
        assert sha(path) == EXPECTED_SHA
    shutil.copy2(manifest, ROOT / 'dist/model-restore-manifest.json')
    print(json.dumps(validation, indent=2))


if __name__ == '__main__':
    main()
