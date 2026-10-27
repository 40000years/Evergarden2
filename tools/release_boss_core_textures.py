"""Publish the tested textured boss cores and combined legend shop locally."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib
import json
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / 'output/boss-core-textures-build-20261023'
BASELINE = ROOT / 'output/boss-armor-exchange-release-20261023'
TRIAL = ROOT / 'output/boss-core-textures-paper-v1-20261023'
RELEASE = ROOT / 'output/boss-core-textures-release-20261023'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    subprocess.run([sys.executable, str(ROOT / 'tests/arkhaia/check_boss_core_assets.py')], check=True)
    report = (TRIAL / 'arkhaia-result.txt').read_text(encoding='utf8').strip()
    assert report.startswith('PASS '), report
    assert 'All RegionFile I/O tasks to complete' in (TRIAL / 'console.log').read_text(encoding='utf8')
    assets = json.loads((ROOT / 'output/boss-core-textures-assets-20261023/validation.json').read_text(encoding='utf8'))
    validation = {'paper_result': report, 'pure_rule_checks': 67143, 'assets': assets,
                  'shop': 'legends', 'cores_per_selected_piece': 1, 'live_server_modified': False,
                  'connected_client_playtest': False, 'artifacts': {}}
    for name in ('advance-magic', 'evergarden'):
        jar = BUILD / (name + '.jar')
        assert sha(jar) == sha(TRIAL / 'plugins' / jar.name)
        with ZipFile(BASELINE / jar.name) as old, ZipFile(jar) as new:
            stable = [entry for entry in old.namelist() if entry.startswith('modelengine/')]
            if name == 'advance-magic':
                stable += [entry for entry in old.namelist() if entry.endswith('.class') or entry.startswith('resource-packs/')]
            else:
                stable += [entry for entry in old.namelist() if '/WorldBossTemple' in entry or entry.endswith(('/WorldBossManager.class', '/ArkhaiaBoss.class', '/SeraphonBoss.class'))]
                assert '3.0.0-e2.125-boss-core-textures' in new.read('plugin.yml').decode('utf8')
                for pack in ('evergarden-java.zip', 'judge-java.zip', 'evergarden-bedrock.mcpack', 'geyser-mappings.json', 'pack-hashes.json'):
                    assert new.read('resource-packs/' + pack) == (ROOT / 'evergarden/dist' / pack).read_bytes()
            assert all(old.read(entry) == new.read(entry) for entry in stable), name + ' unrelated behavior/assets changed'
        validation['artifacts'][jar.name] = {'sha256': sha(jar), 'bytes': jar.stat().st_size}
    RELEASE.mkdir(parents=True, exist_ok=True)
    for name in validation['artifacts']:
        target = ROOT / 'dist' / name
        if target.exists() and not (RELEASE / 'before' / name).exists():
            (RELEASE / 'before').mkdir(exist_ok=True)
            shutil.copy2(target, RELEASE / 'before' / name)
        shutil.copy2(BUILD / name, RELEASE / name)
        shutil.copy2(BUILD / name, target)
        assert sha(target) == validation['artifacts'][name]['sha256']
    sources = ['evergarden/src/main/java/com/example/voidscape/boss/BossArmorCores.java',
               'evergarden/src/main/java/com/example/voidscape/trade/SeraphShop.java',
               'evergarden/src/main/resources/plugin.yml', 'evergarden/tools/boss_orb_assets.py',
               'tests/arkhaia/BossArmorExchangeProbe.java', 'tests/arkhaia/check_boss_core_assets.py',
               'tools/update_boss_armor_core_assets.py', 'tools/release_boss_core_textures.py',
               'docs/boss-armor-exchange.md', 'docs/seraph-trader.md', 'docs/armor-combat.md', 'README.md']
    sources += [path.relative_to(ROOT).as_posix() for path in (ROOT / 'evergarden/art/boss-armor-cores').rglob('*') if path.is_file()]
    with ZipFile(RELEASE / 'sources-and-art.zip', 'w', ZIP_DEFLATED) as archive:
        for source in sources:
            archive.write(ROOT / source, source)
    shutil.copy2(TRIAL / 'arkhaia-result.txt', RELEASE / 'paper-result.txt')
    payload = json.dumps(validation, indent=2) + '\n'
    (RELEASE / 'validation.json').write_text(payload, encoding='utf8')
    (ROOT / 'dist/boss-core-textures-manifest.json').write_text(payload, encoding='utf8')
    print(payload)


if __name__ == '__main__':
    main()
