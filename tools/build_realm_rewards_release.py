"""Build the realm reward release over the delivered Evergarden JAR.

- The Seraph sells maps to the four Nether / End sanctuaries (plain feathers, same price as an Overworld
  sanctuary) and buys each realm boss core for five Golden Seraph Feathers.
- The realm boss drop becomes a textured core (Java item model + Bedrock icon); older untextured Soul Orbs
  are upgraded in place.
- Every contributor rolls their own spoils: Nether bosses a rare weapon (50%) and four Netherite Ingots
  (25%); End bosses an over-cap enchanted book (50%) two levels above boss treasure, usable on an anvil.

Only the owned classes, the Evergarden item packs (Java + Bedrock, Bedrock version +1), Geyser mappings,
pack hashes, judge-design.json and plugin.yml change; every other entry stays byte-identical.

    python evergarden/tools/realm_core_art.py
    python tools/build_realm_rewards_release.py [--validate] [--promote] [--rebase]
"""
import argparse
import hashlib
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
sys.path.insert(0, str(ROOT / 'evergarden/tools'))
import build_plugin_jars as build  # noqa: E402
from build_realm_bosses_release import MODEL_ENGINE, SERVER, sha, snapshot, with_version  # noqa: E402
from boss_orb_assets import item  # noqa: E402

OUT = ROOT / 'output/realm-rewards-20261029'
SLUG = 'realm-rewards'
FIRST_FREE = 166
PACKAGE = 'com/example/voidscape/'
OWNED = ('boss/RealmCores', 'boss/RealmRewards', 'boss/RealmSanctuaryEncounters', 'trade/SeraphShop', 'trade/SanctuaryMaps')
CORES = {'cinder_colossus': 'Core of Cinder Colossus', 'soul_hound': 'Core of Soul-Eater Hound',
         'crystal_moth': 'Core of Crystal Moth Queen', 'unlit_sun': 'Core of Unlit Sun'}
ART = ROOT / 'evergarden/art/realm-cores/textures'
DIST = ROOT / 'evergarden/dist'
TEMPLATE = ROOT / 'output/arkhaia-aggro-feedback-me-v4-20261014'
RECEIPT = 'realm-rewards-validation.json'
PROBES = [ROOT / 'tests/model-engine/RealmBossesNativeProbe.java', ROOT / 'evergarden/tests/SanctuaryMapChecks.java']
DATE = (2026, 10, 29, 0, 0, 0)


def owned(name):
    return any(name == PACKAGE + f + '.class' or name.startswith(PACKAGE + f + '$') for f in OWNED)


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, separators=(',', ':')) + '\n').encode()


def rezip(files):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for entry, data in sorted(files.items()):
            info = zipfile.ZipInfo(entry, DATE)
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, data)
    return buffer.getvalue()


def packs(java_zip, bedrock_pack, mappings_raw):
    """Add the four core icons to both item packs and Geyser; return new bytes and the Bedrock version change."""
    with zipfile.ZipFile(io.BytesIO(java_zip)) as z:
        java = {n: z.read(n) for n in z.namelist() if not n.endswith('/')}
    with zipfile.ZipFile(io.BytesIO(bedrock_pack)) as z:
        bedrock = {n: z.read(n) for n in z.namelist() if not n.endswith('/')}
    atlas = json.loads(bedrock['textures/item_texture.json'])
    mappings = json.loads(mappings_raw)
    selectors = {}
    with tempfile.TemporaryDirectory() as temp:
        jdir, bdir = Path(temp) / 'java', Path(temp) / 'bedrock'

        def write_json(path, data):
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(data, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf8')
        for core, title in CORES.items():
            name = 'realm_core_' + core
            if any(name in entry for entry in list(java) + list(bedrock)):
                raise RuntimeError('Packs already carry ' + name)
            item(jdir, bdir, atlas['texture_data'], mappings, selectors, write_json, name, (ART / (name + '.png')).read_bytes(),
                 'heart_of_the_sea', title, True)
        added_java = {p.relative_to(jdir).as_posix(): p.read_bytes() for p in jdir.rglob('*') if p.is_file()}
        added_bedrock = {p.relative_to(bdir).as_posix(): p.read_bytes() for p in bdir.rglob('*') if p.is_file()}
    route = 'assets/minecraft/items/heart_of_the_sea.json'
    selector = json.loads(java[route])
    if selector['model']['type'] != 'minecraft:select':
        raise RuntimeError('Unexpected heart_of_the_sea route')
    ours = {case['when'] for case in selectors['heart_of_the_sea']}
    selector['model']['cases'] = [c for c in selector['model']['cases'] if c['when'] not in ours] + selectors['heart_of_the_sea']
    java.update(added_java)
    java[route] = json_bytes(selector)
    bedrock.update(added_bedrock)
    bedrock['textures/item_texture.json'] = json_bytes(atlas)
    manifest = json.loads(bedrock['manifest.json'])
    current = manifest['header']['version']
    version = [current[0], current[1] + 1, 0]
    manifest['header']['version'] = version
    for module in manifest['modules']:
        module['version'] = version
    bedrock['manifest.json'] = json_bytes(manifest)
    mapping_bytes = (json.dumps(mappings, ensure_ascii=False, indent=2) + '\n').encode()
    return rezip(java), rezip(bedrock), mapping_bytes, '.'.join(map(str, current)), '.'.join(map(str, version)), sorted(added_java) + sorted(added_bedrock)


def asset_gate(java_zip, bedrock_pack, mappings, old_java, old_bedrock, old_mappings):
    """The cores are routed on both clients and nothing that was there before changed."""
    checks = 0
    with zipfile.ZipFile(io.BytesIO(java_zip)) as j, zipfile.ZipFile(io.BytesIO(old_java)) as oj, \
            zipfile.ZipFile(io.BytesIO(bedrock_pack)) as b, zipfile.ZipFile(io.BytesIO(old_bedrock)) as ob:
        for archive, before, changed in ((j, oj, {'assets/minecraft/items/heart_of_the_sea.json'}),
                                         (b, ob, {'textures/item_texture.json', 'manifest.json'})):
            for name in before.namelist():
                if name not in changed and not name.endswith('/'):
                    assert archive.read(name) == before.read(name), 'pack entry changed: ' + name
                    checks += 1
        selector = json.loads(j.read('assets/minecraft/items/heart_of_the_sea.json'))
        atlas = json.loads(b.read('textures/item_texture.json'))['texture_data']
        for core in CORES:
            name = 'realm_core_' + core
            png = (ART / (name + '.png')).read_bytes()
            assert j.read(f'assets/voidscape/textures/item/{name}.png') == png and b.read(f'textures/items/{name}.png') == png, name
            assert json.loads(j.read(f'assets/voidscape/items/{name}.json'))['model']['model'] == 'voidscape:item/' + name
            assert any(case['when'] == 'voidscape:' + name for case in selector['model']['cases']), name + ' selector'
            assert atlas['voidscape.' + name] == {'textures': 'textures/items/' + name}
            entries = json.loads(mappings)['items']['minecraft:heart_of_the_sea']
            assert any(e.get('model') == 'voidscape:' + name and e['bedrock_options']['icon'] == 'voidscape.' + name for e in entries)
            checks += 5
    new_map, old_map = json.loads(mappings), json.loads(old_mappings)
    new_map['items']['minecraft:heart_of_the_sea'] = [e for e in new_map['items']['minecraft:heart_of_the_sea']
                                                      if not e.get('model', '').startswith('voidscape:realm_core_')]
    assert new_map == old_map, 'unrelated Geyser mappings changed'
    return checks + 1


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--base', type=Path, default=ROOT / 'dist/evergarden.jar')
    parser.add_argument('--magic-base', type=Path, default=ROOT / 'dist/advance-magic.jar')
    parser.add_argument('--output', type=Path, default=OUT)
    parser.add_argument('--validate', action='store_true', help='Run the Paper probes and record a receipt')
    parser.add_argument('--promote', action='store_true', help='Promote only the matching Paper-validated candidate')
    parser.add_argument('--rebase', action='store_true', help='Pin the current dist JARs again')
    args = parser.parse_args()
    out = args.output.resolve()
    if not out.is_relative_to((ROOT / 'output').resolve()):
        raise ValueError('Build directory must stay under workspace output/')
    out.mkdir(parents=True, exist_ok=True)
    if args.rebase and (out / 'before').exists():
        shutil.rmtree(out / 'before')
    before, baseline = snapshot(out, args.base.resolve(), args.magic_base.resolve())
    base = before / 'evergarden.jar'
    with zipfile.ZipFile(base) as archive:
        plugin = archive.read('plugin.yml').decode('utf8')
        old_java = archive.read('resource-packs/evergarden-java.zip')
        old_bedrock = archive.read('resource-packs/evergarden-bedrock.mcpack')
        old_mappings = archive.read('resource-packs/geyser-mappings.json')
        hashes = json.loads(archive.read('resource-packs/pack-hashes.json'))
        design = archive.read('judge-design.json').decode('utf8')
    for name, data in (('evergarden-java.zip', old_java), ('evergarden-bedrock.mcpack', old_bedrock), ('geyser-mappings.json', old_mappings)):
        if (DIST / name).read_bytes() != data:
            raise RuntimeError('evergarden/dist/' + name + ' differs from the delivered JAR; another pipeline rewrote it')
    current = re.search(r"(?m)^version: '([^']+)'", plugin).group(1)
    number = max(int(re.search(r'-e2\.(\d+)', current).group(1)) + 1, FIRST_FREE)
    version = f'3.0.0-e2.{number}-{SLUG}'

    work = out / 'build'
    if (work / 'classes').exists():
        shutil.rmtree(work / 'classes')
    work.mkdir(parents=True, exist_ok=True)
    build.OUT = work
    sources = [ROOT / 'evergarden/src/main/java' / (PACKAGE + f + '.java') for f in OWNED]
    source_hashes = {p.relative_to(ROOT).as_posix(): sha(p.read_bytes()) for p in sources}
    classpath = [base.as_posix(), (before / 'advance-magic.jar').as_posix()] + build.pinned_classpath(build.EVERGARDEN_ARGS)
    build.compile_sources(SLUG, sources, classpath, work / 'classes')
    if any(sha(p.read_bytes()) != source_hashes[p.relative_to(ROOT).as_posix()] for p in sources):
        raise RuntimeError('Owned sources changed during compilation')

    java_zip, bedrock_pack, mappings, pack_from, pack_to, added = packs(old_java, old_bedrock, old_mappings)
    gate = asset_gate(java_zip, bedrock_pack, mappings, old_java, old_bedrock, old_mappings)
    print(f'asset gate: PASS {gate} checks (Bedrock {pack_from} -> {pack_to}, {len(added)} new pack files)')
    hashes['evergarden-java.zip'] = hashlib.sha1(java_zip).hexdigest()
    hashes['evergarden-bedrock.mcpack'] = hashlib.sha1(bedrock_pack).hexdigest()
    design = re.sub(r'("bedrock-version":\s*)"[\d.]+"', lambda m: m.group(1) + '"' + pack_to + '"', design, count=1)
    if f'"{pack_to}"' not in design:
        raise RuntimeError('judge-design.json has no bedrock-version to bump')
    hash_bytes = (json.dumps(hashes, indent=2) + '\n').encode()
    replacements = {'plugin.yml': with_version(plugin, version).encode('utf8'),
                    'resource-packs/evergarden-java.zip': java_zip,
                    'resource-packs/evergarden-bedrock.mcpack': bedrock_pack,
                    'resource-packs/geyser-mappings.json': mappings,
                    'resource-packs/pack-hashes.json': hash_bytes,
                    'judge-design.json': design.encode('utf8')}
    for name, data in replacements.items():
        (work / Path(name).name).write_bytes(data)

    candidate = work / 'evergarden.jar'
    with zipfile.ZipFile(base) as previous, zipfile.ZipFile(candidate, 'w', zipfile.ZIP_DEFLATED) as result:
        for info in previous.infolist():
            if not owned(info.filename) and info.filename not in replacements:
                result.writestr(info, previous.read(info.filename))
        for path in sorted((work / 'classes').rglob('*.class')):
            name = path.relative_to(work / 'classes').as_posix()
            if not owned(name):
                raise RuntimeError('Unexpected compiled class: ' + name)
            result.writestr(zipfile.ZipInfo(name, DATE), path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
        for name, data in replacements.items():
            result.writestr(zipfile.ZipInfo(name, DATE), data,
                            compress_type=zipfile.ZIP_STORED if name.endswith(('.zip', '.mcpack')) else zipfile.ZIP_DEFLATED)
    preserved = 0
    with zipfile.ZipFile(candidate) as release, zipfile.ZipFile(base) as previous:
        if release.testzip() is not None or len(release.namelist()) != len(set(release.namelist())):
            raise RuntimeError('Invalid release archive')
        for f in OWNED:
            if PACKAGE + f + '.class' not in release.namelist():
                raise RuntimeError('Missing owned class: ' + f)
        for info in previous.infolist():
            if not owned(info.filename) and info.filename not in replacements:
                if release.read(info.filename) != previous.read(info.filename):
                    raise RuntimeError('Unrelated entry changed: ' + info.filename)
                preserved += 1
    build.check_jar(candidate)
    digest = sha(candidate.read_bytes())

    if args.validate:
        reports, failed = {}, []
        trials = [('realm', [sys.executable, str(ROOT / 'tools/try_model_engine.py'), '--realm-bosses']),
                  ('readiness', [sys.executable, str(ROOT / 'tools/try_model_engine.py'), '--boss-readiness'])]
        for label, command in trials:
            trial = out / f'paper-{digest[:10]}-{label}'
            if trial.exists():
                shutil.rmtree(trial)
            run = subprocess.run(command + ['--source-server', str(SERVER), '--plugin', str(MODEL_ENGINE), '--garden-jar', str(candidate),
                                            '--magic-jar', str(before / 'advance-magic.jar'), '--test-directory', str(trial)],
                                 capture_output=True, text=True)
            result = trial / 'model-engine-result.txt'
            report = result.read_text(encoding='utf8') if result.exists() else (run.stdout + run.stderr)[-3000:]
            print(f'--- {label}\n{report}')
            reports[label] = report.splitlines()[0] if report else 'FAIL'
            if run.returncode or not reports[label].startswith('PASS'):
                failed.append(label)
        maps = out / f'paper-{digest[:10]}-maps'
        if maps.exists():
            shutil.rmtree(maps)
        run = subprocess.run([sys.executable, str(ROOT / 'evergarden/tests/run_sanctuary_map_checks.py'), '--source-server', str(TEMPLATE),
                              '--server', str(maps), '--magic-jar', str(before / 'advance-magic.jar'), '--garden-jar', str(candidate),
                              '--timeout', '600'], capture_output=True, text=True)
        print('--- maps\n' + (run.stdout + run.stderr)[-4000:])
        lines = [line for line in run.stdout.splitlines() if line.startswith(('PASS ', 'FAIL '))]
        reports['maps'] = ' | '.join(lines) or 'FAIL'
        if run.returncode or len(lines) != 2 or not all(line.startswith('PASS') for line in lines):
            failed.append('maps')
        receipt = {'garden_sha256': digest, 'magic_sha256': baseline['advance-magic.jar'], 'reports': reports,
                   'report': 'FAIL ' + ', '.join(failed) if failed else 'PASS ' + '; '.join(f'{k}: {v}' for k, v in reports.items()),
                   'probes': {p.name: sha(p.read_bytes()) for p in PROBES}, 'asset_gate': gate}
        (out / RECEIPT).write_text(json.dumps(receipt, indent=2, ensure_ascii=False) + '\n', encoding='utf8')
        if failed:
            raise RuntimeError('Paper validation failed: ' + ', '.join(failed))
    if args.promote:
        delivered = ROOT / 'dist/evergarden.jar'
        if not delivered.is_file() or sha(delivered.read_bytes()) != baseline['evergarden.jar']:
            raise RuntimeError('Delivered Evergarden JAR changed since the baseline was pinned; rerun with --rebase --validate')
        receipt = json.loads((out / RECEIPT).read_text(encoding='utf8'))
        if receipt['garden_sha256'] != digest or not receipt['report'].startswith('PASS') \
                or receipt['probes'] != {p.name: sha(p.read_bytes()) for p in PROBES}:
            raise RuntimeError('Candidate lacks matching Paper validation')
        for name, data in (('evergarden-java.zip', old_java), ('evergarden-bedrock.mcpack', old_bedrock), ('geyser-mappings.json', old_mappings)):
            if (DIST / name).read_bytes() != data:
                raise RuntimeError('evergarden/dist/' + name + ' changed during the build')
        source_plugin = ROOT / 'evergarden/src/main/resources/plugin.yml'
        source_plugin.write_bytes(with_version(source_plugin.read_bytes().decode('utf8'), version).encode('utf8'))
        source_design = ROOT / 'evergarden/src/main/resources/judge-design.json'
        source_design.write_bytes(re.sub(r'("bedrock-version":\s*)"[\d.]+"', lambda m: m.group(1) + '"' + pack_to + '"',
                                         source_design.read_bytes().decode('utf8'), count=1).encode('utf8'))
        for name in ('evergarden-java.zip', 'evergarden-bedrock.mcpack', 'geyser-mappings.json', 'pack-hashes.json'):
            target = DIST / name
            staged = target.with_name(target.name + '.staged')
            staged.write_bytes(replacements['resource-packs/' + name])
            os.replace(staged, target)
        for target in (ROOT / 'dist' / f'evergarden-{version}.jar', delivered):
            staged = target.with_suffix('.jar.staged')
            shutil.copy2(candidate, staged)
            os.replace(staged, target)
    report = {'version': version, 'from': current, 'sha256': digest, 'baseline_sha256': baseline['evergarden.jar'],
              'bedrock_version': f'{pack_from} -> {pack_to}', 'owned': OWNED, 'sources': source_hashes,
              'preserved_entries': preserved, 'promoted': args.promote}
    (out / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
