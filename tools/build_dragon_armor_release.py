"""Build Advance Magic 1.39.0-dragon-armor over the delivered JAR (1.38.3-forge-gear).

Adds the three Dragon Conqueror sets -- Infernal Drake, Frostwyrm and Tempest Drake -- as wearable
armor on both editions: 4x painted armor layers, a cape on every cuirass (Java's elytra layer;
cape bones on Bedrock) and a sculpted, face-baked 3D helm (tools/dragon_armor_assets.py). Stats are
twice netherite (armor, toughness, knockback resistance, durability); set powers come later.
`/magic armor give <infernal_drake|frostwyrm|tempest_drake|dragon> [piece|all] [player]`.

Owned: ArmorSetService (and its nested classes) and plugin.yml. The embedded packs gain only the
dragon sets' new entries, their Bedrock item-atlas rows, the Bedrock manifest version
(2.39.0 -> 2.40.0), their Geyser mapping rows and new pack hashes. Every other entry -- the
Seraph and Revenant sets included -- stays byte-identical.

    python tools/build_dragon_armor_release.py [--validate] [--promote]

--validate runs tests/arkhaia/run.py --armor-sets (real Paper) on the candidate and writes a
receipt; --promote ships only the candidate that receipt names.
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
from build_weapon_remodel import encode, read_zip, write_zip  # noqa: E402
import armor_set_forge as forge  # noqa: E402
import dragon_armor_assets as dragons  # noqa: E402

OUT = ROOT / 'output/dragon-armor-20261105/release'
VERSION = '1.39.0-dragon-armor'
BASE_VERSION = '1.38.3-forge-gear'
BASE_SHA = '9b6844e53937610f55a53a4e28a4755aa833266eb2592b91566714b7d76f1781'
BEDROCK_FROM, BEDROCK_TO = [2, 39, 0], [2, 40, 0]
OWNED = ('com/example/advancemagic/item/ArmorSetService',)
PACKS = ('advance-magic-java.zip', 'advance-magic-bedrock.mcpack', 'geyser-mappings.json', 'pack-hashes.json')
RECEIPT = 'dragon-armor-validation.json'
DATE = (2026, 11, 5, 0, 0, 0)
TEMPLATE = ROOT / 'output/arkhaia-aggro-feedback-me-v4-20261014'
CLASSPATH = ROOT / 'output/cocytus-balance-20261025/build/classpath.txt'
PROBES = [ROOT / 'tests/arkhaia/ArmorSetsProbe.java', ROOT / 'tests/arkhaia/run.py']


def sha(data):
    return hashlib.sha256(data).hexdigest()


def owned_class(name):
    return any(name == family + '.class' or name.startswith(family + '$') for family in OWNED)


def with_version(text):
    text, count = re.subn(r"(?m)^version:[^\r\n]*", "version: '" + VERSION + "'", text)
    if count != 1:
        raise RuntimeError('plugin.yml must contain exactly one version')
    return text


def compact(value):
    return json.dumps(value, ensure_ascii=False, separators=(',', ':')).encode('utf8')


def zip_bytes(entries):
    with tempfile.TemporaryDirectory() as temp:
        path = Path(temp) / 'pack.zip'
        write_zip(path, entries)
        return path.read_bytes()


def palette(data):
    """libimagequant palette PNG when it stays within RMSE 4/255 and alpha error 2/255 (as the pack builds do)."""
    import imagequant
    import numpy as np
    from PIL import Image
    rgba = Image.open(io.BytesIO(data)).convert('RGBA')
    try:
        quantized = imagequant.quantize_pil_image(rgba, dithering_level=1.0, max_colors=256, min_quality=0, max_quality=95)
    except RuntimeError:
        return data
    a = np.asarray(rgba).astype(np.float64)
    c = np.asarray(quantized.convert('RGBA')).astype(np.float64)
    rmse = float(np.sqrt((((a[..., :3] - c[..., :3]) * (a[..., 3:] / 255)) ** 2).mean()))
    alpha = float(np.abs(a[..., 3] - c[..., 3]).mean())
    buffer = io.BytesIO()
    quantized.save(buffer, 'PNG', optimize=True)
    return buffer.getvalue() if rmse <= 4 and alpha <= 2 and buffer.tell() < len(data) else data


def generate():
    """The dragon sets' own pack files, item-atlas rows and Geyser definitions."""
    with tempfile.TemporaryDirectory() as temp:
        java, bedrock = Path(temp) / 'java', Path(temp) / 'bedrock'
        atlas, definitions = {}, {}
        dragons.register(java, bedrock, atlas, definitions)
        prefixes = tuple(p for s in dragons.SETS for p in forge.owned(s.key))
        files = {}
        for side, folder in (('java', java), ('bedrock', bedrock)):
            files[side] = {}
            for file in sorted(folder.rglob('*')):
                if file.is_file():
                    name = file.relative_to(folder).as_posix()
                    if not name.startswith(prefixes):
                        raise RuntimeError('generator wrote outside the dragon sets: ' + name)
                    data = encode(file)
                    files[side][name] = palette(data) if name.endswith('.png') else data
    return files, atlas, definitions


def packs(previous):
    """New pack bytes: the shipped packs plus the dragon sets."""
    files, atlas, definitions = generate()
    java_old = read_zip(previous['advance-magic-java.zip'])
    bedrock_old = read_zip(previous['advance-magic-bedrock.mcpack'])
    for side, old in (('java', java_old), ('bedrock', bedrock_old)):
        clash = sorted(set(files[side]) & set(old))
        if clash:
            raise RuntimeError(f'{side} pack already has dragon entries: {clash[:5]}')
    java = dict(java_old) | files['java']
    bedrock = dict(bedrock_old) | files['bedrock']
    item_atlas = json.loads(bedrock['textures/item_texture.json'])
    if any(k in item_atlas['texture_data'] for k in atlas):
        raise RuntimeError('item atlas already lists dragon icons')
    item_atlas['texture_data'] = item_atlas['texture_data'] | atlas
    bedrock['textures/item_texture.json'] = compact(item_atlas)
    manifest = json.loads(bedrock['manifest.json'])
    if manifest['header']['version'] != BEDROCK_FROM:
        raise RuntimeError(f"Bedrock pack is {manifest['header']['version']}, expected {BEDROCK_FROM}")
    manifest['header']['version'] = BEDROCK_TO
    for module in manifest['modules']:
        module['version'] = BEDROCK_TO
    bedrock['manifest.json'] = compact(manifest)
    for name, old, new in (('java', java_old, java), ('bedrock', bedrock_old, bedrock)):
        changed = [n for n in old if new[n] != old[n]]
        if set(changed) - {'textures/item_texture.json', 'manifest.json'}:
            raise RuntimeError(f'{name}: unrelated entries changed: {changed[:5]}')
    mappings = json.loads(previous['geyser-mappings.json'])
    ids = {r['bedrock_identifier'] for rows in definitions.values() for r in rows}
    if any(r.get('bedrock_identifier') in ids for rows in mappings['items'].values() for r in rows):
        raise RuntimeError('Geyser mappings already list dragon items')
    for material, rows in definitions.items():
        mappings['items'].setdefault(material, []).extend(rows)
    out = {'advance-magic-java.zip': zip_bytes(java), 'advance-magic-bedrock.mcpack': zip_bytes(bedrock),
           'geyser-mappings.json': (json.dumps(mappings, ensure_ascii=False, indent=2) + '\n').encode('utf8')}
    hashes = json.loads(previous['pack-hashes.json'])
    for name in ('advance-magic-java.zip', 'advance-magic-bedrock.mcpack'):
        hashes[name] = hashlib.sha1(out[name]).hexdigest()
    out['pack-hashes.json'] = (json.dumps(hashes, indent=2) + '\n').encode('utf8')
    stats = {'java_added': len(files['java']), 'bedrock_added': len(files['bedrock']), 'geyser_rows': len(ids),
             'java_bytes': len(out['advance-magic-java.zip']), 'bedrock_bytes': len(out['advance-magic-bedrock.mcpack'])}
    return out, stats


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--validate', action='store_true')
    parser.add_argument('--promote', action='store_true')
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    delivered = ROOT / 'dist/advance-magic.jar'
    baseline = OUT / 'baseline-advance-magic.jar'
    if not baseline.exists():
        if sha(delivered.read_bytes()) != BASE_SHA:
            raise RuntimeError('dist/advance-magic.jar is not the pinned ' + BASE_VERSION)
        shutil.copy2(delivered, baseline)
    if sha(baseline.read_bytes()) != BASE_SHA:
        raise RuntimeError('baseline JAR does not match the pinned ' + BASE_VERSION)
    with zipfile.ZipFile(baseline) as jar:
        plugin = jar.read('plugin.yml').decode('utf8')
        previous = {name: jar.read('resource-packs/' + name) for name in PACKS}

    new_packs, stats = packs(previous)
    classes = OUT / 'classes'
    shutil.rmtree(classes, ignore_errors=True)
    build.OUT = OUT
    sources = [ROOT / 'advance-magic/src/main/java' / (family + '.java') for family in OWNED]
    source_hashes = {p.relative_to(ROOT).as_posix(): sha(p.read_bytes()) for p in sources}
    build.compile_sources('dragon-armor', sources, [baseline.as_posix(), (ROOT / 'dist/evergarden.jar').as_posix()]
                          + build.pinned_classpath(build.ADVANCE_ARGS), classes)
    if any(sha((ROOT / p).read_bytes()) != h for p, h in source_hashes.items()):
        raise RuntimeError('Owned sources changed during compilation')

    candidate = OUT / 'advance-magic.jar'
    replaced = {'resource-packs/' + n for n in PACKS} | {'plugin.yml'}
    with zipfile.ZipFile(baseline) as previous_jar, zipfile.ZipFile(candidate, 'w', zipfile.ZIP_DEFLATED) as result:
        for info in previous_jar.infolist():
            if not owned_class(info.filename) and info.filename not in replaced:
                result.writestr(info, previous_jar.read(info.filename))
        for path in sorted(classes.rglob('*.class')):
            name = path.relative_to(classes).as_posix()
            if not owned_class(name):
                raise RuntimeError('Unexpected compiled class: ' + name)
            result.writestr(zipfile.ZipInfo(name, DATE), path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
        result.writestr(zipfile.ZipInfo('plugin.yml', DATE), with_version(plugin).encode('utf8'), compress_type=zipfile.ZIP_DEFLATED)
        for name in PACKS:
            result.writestr(zipfile.ZipInfo('resource-packs/' + name, DATE), new_packs[name], compress_type=zipfile.ZIP_DEFLATED)
    preserved = 0
    with zipfile.ZipFile(candidate) as release, zipfile.ZipFile(baseline) as previous_jar:
        if release.testzip() is not None or len(release.namelist()) != len(set(release.namelist())):
            raise RuntimeError('Invalid release archive')
        if set(previous_jar.namelist()) - set(release.namelist()):
            raise RuntimeError('Entries went missing')
        for info in previous_jar.infolist():
            if not owned_class(info.filename) and info.filename not in replaced:
                if release.read(info.filename) != previous_jar.read(info.filename):
                    raise RuntimeError('Unrelated entry changed: ' + info.filename)
                preserved += 1
    build.check_jar(candidate)
    digest = sha(candidate.read_bytes())
    probe_hashes = {p.name: sha(p.read_bytes()) for p in PROBES}

    if args.validate:
        server = OUT / f'paper-{digest[:10]}-armor-sets'
        if server.exists():
            shutil.rmtree(server)
        run = subprocess.run([sys.executable, str(ROOT / 'tests/arkhaia/run.py'), '--template', str(TEMPLATE),
                              '--garden-jar', str(ROOT / 'dist/evergarden.jar'), '--magic-jar', str(candidate),
                              '--server', str(server), '--classpath-file', str(CLASSPATH), '--armor-sets'],
                             capture_output=True, text=True, encoding='utf8', errors='replace')
        (OUT / 'validate-output.txt').write_text(run.stdout + '\n' + run.stderr, encoding='utf8')
        results = sorted(server.glob('*result*.txt')) if server.exists() else []
        report = results[0].read_text(encoding='utf8').strip() if results else 'FAIL ' + (run.stdout + run.stderr)[-2000:]
        print('armor sets:', report[:400])
        (OUT / RECEIPT).write_text(json.dumps({'sha256': digest, 'report': report, 'probes': probe_hashes}, indent=2) + '\n', encoding='utf8')
        if not report.startswith('PASS'):
            raise RuntimeError('Armor sets probe failed')
    if args.promote:
        receipt = json.loads((OUT / RECEIPT).read_text(encoding='utf8'))
        if receipt['sha256'] != digest or not receipt['report'].startswith('PASS') or receipt['probes'] != probe_hashes:
            raise RuntimeError('Candidate lacks matching Paper validation')
        if sha(delivered.read_bytes()) != BASE_SHA:
            raise RuntimeError('dist/advance-magic.jar changed since the baseline was pinned')
        source_plugin = ROOT / 'advance-magic/src/main/resources/plugin.yml'
        source_plugin.write_text(with_version(source_plugin.read_text(encoding='utf8')), encoding='utf8')
        for name in PACKS:
            target = ROOT / 'advance-magic/dist' / name
            staged = target.with_name(target.name + '.staged')
            staged.write_bytes(new_packs[name])
            os.replace(staged, target)
        for target in (ROOT / 'dist' / f'advance-magic-{VERSION}.jar', delivered):
            staged = target.with_suffix('.jar.staged')
            shutil.copy2(candidate, staged)
            os.replace(staged, target)
    report = {'version': VERSION, 'from': BASE_VERSION, 'sha256': digest, 'baseline_sha256': BASE_SHA, 'bedrock_pack': BEDROCK_TO,
              'owned': OWNED, 'sources': source_hashes, 'preserved_entries': preserved, 'packs': stats, 'promoted': args.promote}
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
