"""Publish the locally tested boss-armor exchange JAR pair; never deploy a server."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib
import json
import shutil

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / 'output/boss-armor-exchange-build-20261023'
BASELINE = ROOT / 'output/armor-sets-paper-v3-20261023/plugins'
TRIAL = ROOT / 'output/boss-armor-exchange-paper-v7-20261023'
RELEASE = ROOT / 'output/boss-armor-exchange-release-20261023'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    report = (TRIAL / 'arkhaia-result.txt').read_text(encoding='utf8').strip()
    assert report.startswith('PASS '), report
    assert 'All RegionFile I/O tasks to complete' in (TRIAL / 'console.log').read_text(encoding='utf8')
    validation = {'paper_result': report, 'pure_rule_checks': 67143,
                  'live_server_modified': False, 'connected_client_playtest': False,
                  'drops_per_victory': 1, 'cores_per_selected_piece': 1, 'artifacts': {}}
    for name in ('advance-magic', 'evergarden'):
        jar = BUILD / (name + '.jar')
        assert sha(jar) == sha(TRIAL / 'plugins' / jar.name), name
        with ZipFile(BASELINE / jar.name) as old, ZipFile(jar) as new:
            assets = [n for n in old.namelist() if n.startswith(('resource-packs/', 'modelengine/'))]
            assert all(old.read(n) == new.read(n) for n in assets), name
            if name == 'evergarden':
                untouched = [n for n in old.namelist() if '/WorldBossTemple' in n or n.endswith('/WorldBossManager.class')]
                assert all(old.read(n) == new.read(n) for n in untouched)
                assert 'com/example/voidscape/boss/BossArmorCores.class' in new.namelist()
                assert '3.0.0-e2.124-boss-armor-exchange' in new.read('plugin.yml').decode('utf8')
            else:
                classes = [n for n in old.namelist() if n.endswith('.class')]
                assert all(old.read(n) == new.read(n) for n in classes), 'Advance Magic behavior must stay unchanged'
            version = next(line for line in new.read('plugin.yml').decode('utf8').splitlines() if line.startswith('version:'))
        validation['artifacts'][jar.name] = {'sha256': sha(jar), 'bytes': jar.stat().st_size,
                                           'version': version, 'unchanged_pack_and_model_entries': len(assets)}
    RELEASE.mkdir(parents=True, exist_ok=True)
    (ROOT / 'dist').mkdir(exist_ok=True)
    for name in validation['artifacts']:
        target = ROOT / 'dist' / name
        if target.exists() and not (RELEASE / 'before' / name).exists():
            (RELEASE / 'before').mkdir(exist_ok=True)
            shutil.copy2(target, RELEASE / 'before' / name)
        shutil.copy2(BUILD / name, RELEASE / name)
        shutil.copy2(BUILD / name, target)
    sources = [
        'evergarden/src/main/java/com/example/voidscape/boss/BossArmorCores.java',
        'evergarden/src/main/java/com/example/voidscape/boss/ArkhaiaBoss.java',
        'evergarden/src/main/java/com/example/voidscape/boss/SeraphonBoss.java',
        'evergarden/src/main/java/com/example/voidscape/trade/SeraphShop.java',
        'evergarden/src/main/java/com/example/voidscape/trade/SeraphTrader.java',
        'evergarden/src/main/resources/plugin.yml', 'tests/arkhaia/BossArmorExchangeProbe.java',
        'tests/arkhaia/run.py', 'docs/boss-armor-exchange.md', 'docs/armor-combat.md',
        'docs/seraph-trader.md', 'README.md', 'tools/release_boss_armor_exchange.py',
    ]
    with ZipFile(RELEASE / 'sources.zip', 'w', ZIP_DEFLATED) as archive:
        for source in sources:
            archive.write(ROOT / source, source)
    shutil.copy2(TRIAL / 'arkhaia-result.txt', RELEASE / 'paper-result.txt')
    payload = json.dumps(validation, indent=2) + '\n'
    (RELEASE / 'validation.json').write_text(payload, encoding='utf8')
    (ROOT / 'dist/boss-armor-exchange-manifest.json').write_text(payload, encoding='utf8')
    print(payload)


if __name__ == '__main__':
    main()
