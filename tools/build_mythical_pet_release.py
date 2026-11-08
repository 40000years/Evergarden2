"""Build, validate, and promote e2.177 over the delivered e2.176 without unrelated changes.

python tools/build_mythical_pet_release.py
python tools/build_mythical_pet_release.py --validate
python tools/build_mythical_pet_release.py --promote
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import zipfile

import build_plugin_jars as build

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output/mythical-pets-release'
VERSION = '3.0.0-e2.177-mythical-pets'
BASE_SHA = '7a5b7bb307a4e24d9dd4ae5230ac827f26adb4b124283c015d874a9f1483044a'
FAMILIES = tuple('com/example/voidscape/pet/' + n for n in (
    'PetCatalog', 'NativePetRenderer', 'PetBlueprintStaging', 'PetRecord', 'PetStable', 'PetStore',
    'PetCompanions', 'PetTaming', 'PetBreeding', 'PetSystem', 'PetMenu', 'WildPetSpawns', 'PetModelPreviews')) + (
    'com/example/voidscape/boss/ArkhaiaBedrockSupport', 'com/example/voidscape/boss/JudgePackService')
RES = ROOT / 'evergarden/src/main/resources'
MYTHICAL = ('aetherius', 'ignis_draconis', 'zephyros', 'solaris', 'fenrir')
RESOURCES = {n: RES / n for n in ('config.yml', 'native-pets.json', 'native-pets.zip', 'pet-portraits.zip',
                                    'judge-design.json', 'modelengine/pets/manifest.json')}
RESOURCES.update({'modelengine/pets/everpet_' + s + '_' + age + '.bbmodel':
                  RES / ('modelengine/pets/everpet_' + s + '_' + age + '.bbmodel')
                  for s in MYTHICAL for age in ('baby', 'medium', 'adult')})
RESOURCES.update({'resource-packs/' + n: ROOT / 'evergarden/dist' / n for n in (
    'evergarden-bedrock.mcpack', 'geyser-mappings.json', 'pack-hashes.json')})
PROBES = [ROOT / 'tests/model-engine' / n for n in ('PetSystemNativeProbe.java', 'MythicalPetProbe.java')]
PROBE_MAIN = 'MythicalPetProbe'
SERVER = ROOT / 'output/pet-audit-paper-20261029-final'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def owned(name):
    return name in RESOURCES or any(name == f + '.class' or name.startswith(f + '$') for f in FAMILIES)


def version(data):
    value, count = re.subn(r'(?m)^version:[^\r\n]*', "version: '" + VERSION + "'", data.decode('utf8'))
    assert count == 1
    return value.encode('utf8')


def zip_entries(data):
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist()))
        return {n: z.read(n) for n in z.namelist() if not n.endswith('/')}


def check_assets(old):
    # The mythical additions may never regenerate or regress any of the 96 existing variants.
    for name in ('native-pets.zip', 'pet-portraits.zip'):
        before, after = zip_entries(old.read(name)), zip_entries(RESOURCES[name].read_bytes())
        assert all(after.get(n) == data for n, data in before.items()), name + ': existing entry changed'
    before = json.loads(old.read('native-pets.json'))['models']
    after = json.loads(RESOURCES['native-pets.json'].read_bytes())['models']
    assert len(after) == 111 and all(after.get(n) == data for n, data in before.items())
    bedrock = zip_entries(RESOURCES['resource-packs/evergarden-bedrock.mcpack'].read_bytes())
    old_bedrock = zip_entries(old.read('resource-packs/evergarden-bedrock.mcpack'))
    assert all(bedrock.get(n) == data for n, data in old_bedrock.items()
               if n not in ('manifest.json', 'textures/item_texture.json'))
    before_atlas = json.loads(old_bedrock['textures/item_texture.json'])['texture_data']
    atlas = json.loads(bedrock['textures/item_texture.json'])['texture_data']
    assert all(atlas.get(n) == v for n, v in before_atlas.items())
    old_maps = json.loads(old.read('resource-packs/geyser-mappings.json'))
    maps = json.loads(RESOURCES['resource-packs/geyser-mappings.json'].read_bytes())
    for material, entries in old_maps['items'].items():
        assert maps['items'][material][:len(entries)] == entries
    assert {k: v for k, v in maps.items() if k != 'items'} == {k: v for k, v in old_maps.items() if k != 'items'}
    assert sum(n.startswith('entity/everpet_') for n in bedrock) == 111
    hashes = json.loads(RESOURCES['resource-packs/pack-hashes.json'].read_bytes())
    for name, digest in hashes.items():
        assert hashlib.sha1((ROOT / 'evergarden/dist' / name).read_bytes()).hexdigest() == digest


def candidate_build():
    OUT.mkdir(parents=True, exist_ok=True)
    garden, magic = ROOT / 'dist/evergarden.jar', ROOT / 'dist/advance-magic.jar'
    assert sha(garden) == BASE_SHA, 'Delivery baseline changed; rebase this release explicitly'
    baseline = OUT / 'baseline-evergarden.jar'
    if not baseline.exists():
        shutil.copy2(garden, baseline)
    assert sha(baseline) == BASE_SHA
    sources = [ROOT / 'evergarden/src/main/java' / (f + '.java') for f in FAMILIES]
    hashes = {p.relative_to(ROOT).as_posix(): sha(p) for p in sources + list(RESOURCES.values())}
    build.OUT = OUT
    classes = OUT / 'classes'
    build.compile_sources('mythical-pets', sources, [str(baseline), str(magic)]
                          + build.pinned_classpath(build.EVERGARDEN_ARGS), classes)
    assert all(sha(ROOT / p) == h for p, h in hashes.items()), 'Source changed while compiling'
    compiled = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')}
    assert compiled and all(owned(n) for n in compiled)
    assert all(f + '.class' in compiled for f in FAMILIES)
    candidate = OUT / 'evergarden.jar'
    with zipfile.ZipFile(baseline) as old, zipfile.ZipFile(candidate, 'w', zipfile.ZIP_DEFLATED) as new:
        check_assets(old)
        # The configuration before this task was exactly the delivered file.
        assert old.read('config.yml') == (OUT / 'before/resources/config.yml').read_bytes()
        for entry in old.infolist():
            if not owned(entry.filename) and entry.filename != 'plugin.yml':
                new.writestr(entry, old.read(entry.filename))
        replaced = {'plugin.yml': version(old.read('plugin.yml')), **compiled,
                    **{n: p.read_bytes() for n, p in RESOURCES.items()}}
        for name, data in sorted(replaced.items()):
            new.writestr(zipfile.ZipInfo(name, (2026, 11, 1, 0, 0, 0)), data, compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(baseline) as old, zipfile.ZipFile(candidate) as new:
        assert new.testzip() is None and len(new.namelist()) == len(set(new.namelist()))
        preserved = [n for n in old.namelist() if not owned(n) and n != 'plugin.yml']
        assert all(old.read(n) == new.read(n) for n in preserved)
        assert set(new.namelist()) == set(old.namelist()) | set(compiled) | set(RESOURCES)
    build.check_jar(candidate)
    report = dict(version=VERSION, baseline_sha256=BASE_SHA, candidate_sha256=sha(candidate),
                  magic_sha256=sha(magic), sources=hashes, preserved_entries=len(preserved),
                  replaced=sorted(replaced), promoted=False)
    (OUT / 'build-report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(f'Built {VERSION}; {len(preserved)} unrelated entries preserved; sha256 {sha(candidate)}')


def validate():
    report = json.loads((OUT / 'build-report.json').read_text(encoding='utf8'))
    candidate, magic = OUT / 'evergarden.jar', ROOT / 'dist/advance-magic.jar'
    assert sha(candidate) == report['candidate_sha256'] and sha(magic) == report['magic_sha256']
    plugins = SERVER / 'plugins'
    # This is an existing throwaway test server with port 0 and synthetic players, never a live world.
    assert SERVER.resolve().is_relative_to((ROOT / 'output').resolve())
    for name in ('evergarden.jar', 'model-engine-trial-probe.jar'):
        backup = OUT / ('fixture-before-' + name)
        if not backup.exists():
            shutil.copy2(plugins / name, backup)
    shutil.copy2(candidate, plugins / 'evergarden.jar')
    shutil.copy2(magic, plugins / 'advance-magic.jar')
    cp = [str(candidate), str(magic)] + [str(p) for folder in (SERVER / 'libraries', SERVER / 'versions', plugins)
                                        for p in folder.rglob('*.jar') if folder != plugins or p.parent == plugins]
    classes = OUT / 'probe-classes'
    probe_hashes = {str(p.relative_to(ROOT)): sha(p) for p in PROBES}
    build.OUT = OUT
    build.compile_sources('mythical-probe', PROBES, cp, classes)
    (classes / 'plugin.yml').write_text("name: ModelEngineTrialProbe\nversion: 'mythical-pets'\nmain: "+PROBE_MAIN+"\n"
                                        "api-version: '26.2'\nsoftdepend: [ModelEngine, Evergarden, advance-magic, LevelledMobs, Geyser-Spigot, floodgate]\n", encoding='utf8')
    subprocess.run(['jar', '--create', '--file', str(plugins / 'model-engine-trial-probe.jar'), '-C', str(classes), '.'], check=True)
    result = SERVER / 'model-engine-result.txt'
    if result.exists():
        shutil.copy2(result, OUT / ('fixture-result-before-' + str(time.time_ns()) + '.txt'))
        result.unlink()
    log = OUT / ('paper-console-' + str(time.time_ns()) + '.log')
    launch_cp=[]
    with zipfile.ZipFile(SERVER / 'paper.jar') as bundle:
        for folder in ('versions', 'libraries'):
            for line in bundle.read(f'META-INF/{folder}.list').decode('utf8').splitlines():
                if line.strip():
                    target=SERVER / folder / line.split('\t')[2]
                    assert target.is_file(), target
                    launch_cp.append(str(target))
    launch_cp += [p for p in build.pinned_classpath(build.EVERGARDEN_ARGS) if Path(p).name.startswith('cumulus-')]
    options=['-Xms256M', '-Xmx3072M', '-Dterminal.jline=false', '-Dterminal.ansi=false', '-cp',
             os.pathsep.join(launch_cp), 'org.bukkit.craftbukkit.Main', '--nogui', '--noconsole']
    launch_args=OUT / 'paper-launch.args'
    launch_args.write_text('\n'.join('"'+v.replace('\\','/')+'"' for v in options), encoding='utf8')
    print('Starting isolated Paper pet system + mythical regression', flush=True)
    with log.open('w', encoding='utf8') as console:
        proc = subprocess.Popen(['java', '@'+str(launch_args)], cwd=SERVER, stdout=console,
                                stderr=subprocess.STDOUT,
                                creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
        try:
            code = proc.wait(timeout=300)
        except subprocess.TimeoutExpired:
            proc.kill();proc.wait()
            raise RuntimeError('Paper timed out: ' + str(log))
    text = result.read_text(encoding='utf8') if result.exists() else 'FAIL: missing result; ' + str(log)
    (OUT / 'pet-result.txt').write_text(text, encoding='utf8')
    receipt = dict(result=text.splitlines()[0], garden_sha256=sha(candidate), magic_sha256=sha(magic),
                   probes=probe_hashes, log=str(log))
    (OUT / 'test-receipt.json').write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf8')
    print(text.splitlines()[0])
    assert code == 0 and text.startswith('PASS'), 'Paper regression failed: ' + str(OUT / 'pet-result.txt')


def promote():
    report = json.loads((OUT / 'build-report.json').read_text(encoding='utf8'))
    receipt = json.loads((OUT / 'test-receipt.json').read_text(encoding='utf8'))
    candidate, garden, magic = OUT / 'evergarden.jar', ROOT / 'dist/evergarden.jar', ROOT / 'dist/advance-magic.jar'
    assert sha(garden) == report['baseline_sha256'], 'dist changed; refusing stale promotion'
    assert sha(candidate) == report['candidate_sha256'] == receipt['garden_sha256']
    assert sha(magic) == report['magic_sha256'] == receipt['magic_sha256']
    assert receipt['result'].startswith('PASS') and all(sha(ROOT / p) == h for p, h in receipt['probes'].items())
    assert all(sha(ROOT / p) == h for p, h in report['sources'].items()), 'Validated sources changed'
    plugin = RES / 'plugin.yml'
    plugin.write_bytes(version(plugin.read_bytes()))
    shutil.copy2(candidate, ROOT / f'dist/evergarden-{VERSION}.jar')
    staged = garden.with_suffix('.jar.staged')
    shutil.copy2(candidate, staged)
    os.replace(staged, garden)
    assert sha(garden) == report['candidate_sha256']
    report.update(promoted=True, validation=receipt)
    (OUT / 'build-report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print('Promoted ' + VERSION + ' ' + sha(garden))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--validate', action='store_true')
    parser.add_argument('--promote', action='store_true')
    args = parser.parse_args()
    promote() if args.promote else validate() if args.validate else candidate_build()
