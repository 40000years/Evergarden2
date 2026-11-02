"""Build the realm-boss showcase release over the exact delivered baseline.

Adds the four realm-boss rigs (Cinder Colossus, Soul-Eater Hound, Crystal Moth Queen, Unlit Sun):
ModelEngine blueprints under modelengine/realm/, their native Bedrock entities in the Evergarden
Bedrock pack (content version +1 minor), and the admin showcase that spawns them from
/evergarden test (World Boss Test > บอสประจำมิติ) or /evergarden boss model <id>. No encounter,
spawner or structure hook: the dimensional sanctuaries are untouched.

Only the owned class families, the realm blueprints, the Bedrock pack, pack-hashes.json,
judge-design.json and plugin.yml change; every other entry stays byte-identical.

    python evergarden/tools/realm_bosses.py          # regenerate the rigs first
    python tools/build_realm_bosses_release.py [--validate] [--promote]
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
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
sys.path.insert(0, str(ROOT / 'evergarden/tools'))
import build_plugin_jars as build  # noqa: E402
from arkhaia_bedrock_assets import export_model  # noqa: E402

OUT = ROOT / 'output/realm-bosses-20261029'
SLUG = 'realm-bosses'
PACKAGE = 'com/example/voidscape/'
OWNED = ('boss/RealmBosses', 'boss/BossShowcase', 'boss/BossAura', 'boss/BossHitbox', 'boss/ModelEngineArkhaia',
         'boss/ArkhaiaBedrockSupport', 'boss/WorldBossManager', 'gui/AdminTestGui', 'command/VoidCommand')
MODELS = ('cinder_colossus', 'soul_hound', 'crystal_moth', 'unlit_sun')
REALM = ROOT / 'evergarden/src/main/resources/modelengine/realm'
SERVER = Path('C:/Users/User/Desktop/Serva')
MODEL_ENGINE = ROOT / 'output/model-engine-trial-20261014/ModelEngine-4.1.1.jar'
RECEIPT = 'realm-validation.json'
DATE = (2026, 10, 29, 0, 0, 0)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def next_version(text):
    current = re.search(r"(?m)^version: '([^']+)'", text).group(1)
    number = int(re.search(r'-e2\.(\d+)', current).group(1))
    return current, f'3.0.0-e2.{number + 1}-{SLUG}'


def with_version(text, version):
    lines = text.splitlines(keepends=True)
    hits = [i for i, line in enumerate(lines) if line.startswith('version: ')]
    if len(hits) != 1:
        raise RuntimeError('plugin.yml must contain exactly one version')
    i = hits[0]
    lines[i] = f"version: '{version}'" + lines[i][len(lines[i].rstrip('\r\n')):]
    return ''.join(lines)


def snapshot(out, garden, magic):
    """Pin the delivered JARs once, refusing later baseline drift."""
    before = out / 'before'
    before.mkdir(parents=True, exist_ok=True)
    metadata = before / 'baseline.json'
    names = ('evergarden.jar', 'advance-magic.jar')
    if not metadata.exists():
        for original, name in ((garden, names[0]), (magic, names[1])):
            shutil.copy2(original, before / name)
        metadata.write_text(json.dumps({n: sha((before / n).read_bytes()) for n in names}, indent=2) + '\n', encoding='utf8')
    hashes = json.loads(metadata.read_text(encoding='utf8'))
    for name in names:
        if sha((before / name).read_bytes()) != hashes[name]:
            raise RuntimeError('Frozen baseline changed: ' + name)
    if sha(garden.read_bytes()) != hashes[names[0]]:
        raise RuntimeError('Evergarden baseline differs from the frozen snapshot; use a fresh --output')
    if sha(magic.read_bytes()) != hashes[names[1]]:
        raise RuntimeError('Advance Magic changed since the baseline snapshot')
    return before, hashes


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, separators=(',', ':')) + '\n').encode()


def bedrock_pack(base_pack):
    """Add the realm entities and bump the content version; unrelated entries stay identical."""
    with zipfile.ZipFile(io.BytesIO(base_pack)) as z:
        files = {n: z.read(n) for n in z.namelist() if not n.endswith('/')}
    for model_id in MODELS:
        manifest = json.loads((REALM / f'{model_id}-manifest.json').read_text(encoding='utf8'))
        native = export_model(json.loads((REALM / f'{model_id}.bbmodel').read_text(encoding='utf8')), model_id, manifest['scale'], bright=True)
        clash = [p for p in native if p in files]
        if clash:
            raise RuntimeError('Bedrock pack already carries realm entries: ' + ', '.join(clash))
        files.update(native)
    manifest = json.loads(files['manifest.json'])
    current = manifest['header']['version']
    version = [current[0], current[1] + 1, 0]
    manifest['header']['version'] = version
    for module in manifest['modules']:
        module['version'] = version
    files['manifest.json'] = json_bytes(manifest)
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for entry, data in sorted(files.items()):
            info = zipfile.ZipInfo(entry, DATE)
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, data)
    return buffer.getvalue(), '.'.join(map(str, current)), '.'.join(map(str, version))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--base', type=Path, default=ROOT / 'dist/evergarden.jar')
    parser.add_argument('--magic-base', type=Path, default=ROOT / 'dist/advance-magic.jar')
    parser.add_argument('--output', type=Path, default=OUT)
    parser.add_argument('--validate', action='store_true', help='Run the realm-boss Paper probe on the candidate and record a receipt')
    parser.add_argument('--promote', action='store_true', help='Promote only the matching Paper-validated candidate')
    args = parser.parse_args()
    out = args.output.resolve()
    if not out.is_relative_to((ROOT / 'output').resolve()):
        raise ValueError('Build directory must stay under workspace output/')
    out.mkdir(parents=True, exist_ok=True)
    before, baseline = snapshot(out, args.base.resolve(), args.magic_base.resolve())
    base = before / 'evergarden.jar'
    with zipfile.ZipFile(base) as archive:
        names = set(archive.namelist())
        plugin = archive.read('plugin.yml').decode('utf8')
        base_pack = archive.read('resource-packs/evergarden-bedrock.mcpack')
        hashes = json.loads(archive.read('resource-packs/pack-hashes.json'))
        design = archive.read('judge-design.json').decode('utf8')
    if 'pack-mirror.properties' not in names:
        raise RuntimeError('Baseline predates embedded public pack copies')
    if any(n.startswith('modelengine/realm/') for n in names):
        raise RuntimeError('Baseline already ships realm rigs')
    dist_pack = ROOT / 'evergarden/dist/evergarden-bedrock.mcpack'
    if dist_pack.read_bytes() != base_pack:
        raise RuntimeError('evergarden/dist Bedrock pack differs from the one in the delivered JAR; another pipeline rewrote it')
    current, version = next_version(plugin)

    families = tuple(PACKAGE + name for name in OWNED)

    def replaced(name):
        return any(name == family + '.class' or name.startswith(family + '$') for family in families)

    work = out / 'build'
    work.mkdir(parents=True, exist_ok=True)
    build.OUT = work
    sources = [ROOT / 'evergarden/src/main/java' / (PACKAGE + name + '.java') for name in OWNED]
    source_hashes = {p.relative_to(ROOT).as_posix(): sha(p.read_bytes()) for p in sources}
    classpath = [base.as_posix(), (before / 'advance-magic.jar').as_posix()] + build.pinned_classpath(build.EVERGARDEN_ARGS)
    build.compile_sources('realm-bosses', sources, classpath, work / 'classes')
    if any(sha(p.read_bytes()) != source_hashes[p.relative_to(ROOT).as_posix()] for p in sources):
        raise RuntimeError('Owned sources changed during compilation')

    pack, pack_from, pack_to = bedrock_pack(base_pack)
    (work / 'evergarden-bedrock.mcpack').write_bytes(pack)
    hashes['evergarden-bedrock.mcpack'] = hashlib.sha1(pack).hexdigest()
    design = re.sub(r'("bedrock-version":\s*)"[\d.]+"', lambda m: m.group(1) + '"' + pack_to + '"', design, count=1)
    if f'"{pack_to}"' not in design:
        raise RuntimeError('judge-design.json has no bedrock-version to bump')
    replacements = {'plugin.yml': with_version(plugin, version).encode('utf8'),
                    'resource-packs/evergarden-bedrock.mcpack': pack,
                    'resource-packs/pack-hashes.json': (json.dumps(hashes, indent=2) + '\n').encode(),
                    'judge-design.json': design.encode('utf8')}
    for model_id in MODELS:
        for suffix in ('.bbmodel', '-manifest.json'):
            replacements[f'modelengine/realm/{model_id}{suffix}'] = (REALM / f'{model_id}{suffix}').read_bytes()

    gate = subprocess.run([sys.executable, str(ROOT / 'tests/model-engine/check_realm_bosses.py'), '--pack', str(work / 'evergarden-bedrock.mcpack')],
                          capture_output=True, text=True)
    print(gate.stdout.strip() or gate.stderr.strip()[-2000:])
    if gate.returncode:
        raise RuntimeError('Realm-boss asset gate failed')

    candidate = work / 'evergarden.jar'
    with zipfile.ZipFile(base) as previous, zipfile.ZipFile(candidate, 'w', zipfile.ZIP_DEFLATED) as result:
        for info in previous.infolist():
            if not replaced(info.filename) and info.filename not in replacements:
                result.writestr(info, previous.read(info.filename))
        for path in sorted((work / 'classes').rglob('*.class')):
            name = path.relative_to(work / 'classes').as_posix()
            if not replaced(name):
                raise RuntimeError('Unexpected compiled class: ' + name)
            result.writestr(zipfile.ZipInfo(name, DATE), path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
        for name, data in replacements.items():
            result.writestr(zipfile.ZipInfo(name, DATE), data,
                            compress_type=zipfile.ZIP_STORED if name.endswith(('.zip', '.mcpack')) else zipfile.ZIP_DEFLATED)
    preserved = {}
    with zipfile.ZipFile(candidate) as release, zipfile.ZipFile(base) as previous:
        if release.testzip() is not None or len(release.namelist()) != len(set(release.namelist())):
            raise RuntimeError('Invalid release archive')
        for family in families:
            if family + '.class' not in release.namelist():
                raise RuntimeError('Missing owned primary class: ' + family)
        for info in previous.infolist():
            if not replaced(info.filename) and info.filename not in replacements:
                old = previous.read(info.filename)
                if release.read(info.filename) != old:
                    raise RuntimeError('Unrelated entry changed: ' + info.filename)
                preserved[info.filename] = sha(old)
    build.check_jar(candidate)
    digest = sha(candidate.read_bytes())
    (out / 'asset-preservation.json').write_text(json.dumps({'baseline_sha256': baseline['evergarden.jar'], 'candidate_sha256': digest,
                                                             'count': len(preserved), 'preserved_entries': preserved}, indent=2) + '\n',
                                                 encoding='utf8')
    if args.validate:
        # The user's ModelEngine build imports twelve models in all, so each rig is loaded on its own: the default
        # choice first, then each other rig with a leftover default install that must be retired.
        reports, failed = {}, []
        for choice in (None,) + MODELS[1:]:
            label = choice or 'default'
            trial = out / f'paper-{digest[:10]}-{label}'
            if trial.exists():
                shutil.rmtree(trial)
            command = [sys.executable, str(ROOT / 'tools/try_model_engine.py'), '--realm-bosses', '--source-server', str(SERVER),
                       '--plugin', str(MODEL_ENGINE), '--garden-jar', str(candidate), '--magic-jar', str(before / 'advance-magic.jar'),
                       '--test-directory', str(trial)] + (['--realm-install', choice] if choice else [])
            run = subprocess.run(command, capture_output=True, text=True)
            result = trial / 'model-engine-result.txt'
            report = result.read_text(encoding='utf8') if result.exists() else (run.stdout + run.stderr)[-3000:]
            print(f'--- {label}\n{report}')
            reports[label] = report.splitlines()[0] if report else 'FAIL'
            if run.returncode or not reports[label].startswith('PASS'):
                failed.append(label)
        receipt = {'garden_sha256': digest, 'magic_sha256': baseline['advance-magic.jar'],
                   'report': 'FAIL ' + ', '.join(failed) if failed else 'PASS ' + '; '.join(f'{k}: {v}' for k, v in reports.items()),
                   'reports': reports, 'probe_sha256': sha((ROOT / 'tests/model-engine/RealmBossesNativeProbe.java').read_bytes())}
        (out / RECEIPT).write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf8')
        if failed:
            raise RuntimeError('Realm-boss Paper probe failed: ' + ', '.join(failed))
    if args.promote:
        delivered = ROOT / 'dist/evergarden.jar'
        if not delivered.is_file() or sha(delivered.read_bytes()) != baseline['evergarden.jar']:
            raise RuntimeError('Delivered Evergarden baseline changed; rebuild over the new one')
        receipt = json.loads((out / RECEIPT).read_text(encoding='utf8'))
        if receipt['garden_sha256'] != digest or not receipt['report'].startswith('PASS'):
            raise RuntimeError('Candidate lacks matching Paper validation')
        if receipt['probe_sha256'] != sha((ROOT / 'tests/model-engine/RealmBossesNativeProbe.java').read_bytes()):
            raise RuntimeError('Paper probe changed after validation')
        if dist_pack.read_bytes() != base_pack:
            raise RuntimeError('evergarden/dist Bedrock pack changed during the build')
        source_plugin = ROOT / 'evergarden/src/main/resources/plugin.yml'
        source_plugin.write_bytes(with_version(source_plugin.read_bytes().decode('utf8'), version).encode('utf8'))
        source_design = ROOT / 'evergarden/src/main/resources/judge-design.json'
        source_design.write_bytes(re.sub(r'("bedrock-version":\s*)"[\d.]+"', lambda m: m.group(1) + '"' + pack_to + '"',
                                         source_design.read_bytes().decode('utf8'), count=1).encode('utf8'))
        for target, data in ((dist_pack, pack), (ROOT / 'evergarden/dist/pack-hashes.json', replacements['resource-packs/pack-hashes.json'])):
            staged = target.with_name(target.name + '.staged')
            staged.write_bytes(data)
            os.replace(staged, target)
        for target in (ROOT / 'dist' / f'evergarden-{version}.jar', delivered):
            staged = target.with_suffix('.jar.staged')
            shutil.copy2(candidate, staged)
            os.replace(staged, target)
    report = {'version': version, 'from': current, 'sha256': digest, 'baseline_sha256': baseline['evergarden.jar'],
              'bedrock_version': f'{pack_from} -> {pack_to}', 'owned': OWNED, 'sources': source_hashes, 'promoted': args.promote}
    (out / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
