"""Build Evergarden e2.148: the redesigned celestial Seraph trader model.

evergarden/tools/seraph_preview_assets.py now draws a slender Seraph: an 18-tier A-line gown and
fitted bodice sampling one continuous painting, a smaller face framed by silver hair with pointed
fringe and mane, three alpha-cut painted feather layers per wing and a thin tilted halo. Elements
are flat-lit (Java shade false, Bedrock ignore_lighting) so the painted shading carries the form.

Over a delivered Evergarden JAR (--base, default dist/evergarden.jar) this replaces only the five
judge_seraph_* entries in the Judge Java pack and in the Evergarden Bedrock pack, bumps the
Bedrock pack to 3.60.0 and keeps every other JAR and pack entry byte-identical. No classes change;
the item identifiers and Geyser mappings stay the same.

    python tools/build_seraph_celestial_release.py [--base JAR] [--promote]
"""
import argparse
import hashlib
import io
import json
import os
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
sys.path.insert(0, str(ROOT / 'evergarden/tools'))
import build_plugin_jars as build  # noqa: E402
import seraph_preview_assets as seraph  # noqa: E402

OUT = ROOT / 'output/seraph-celestial-20261026'
VERSION = '3.0.0-e2.148-seraph-celestial'
BEDROCK_VERSION = [3, 60, 0]
DATE = (2026, 10, 26, 0, 0, 0)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def read_zip(data):
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        return {i.filename: archive.read(i.filename) for i in archive.infolist() if not i.is_dir()}, archive.infolist()


def write_zip(entries, infos):
    """Keep the original entry order and dates; new entries go last."""
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED) as archive:
        done = set()
        for info in infos:
            if info.filename in entries and not info.is_dir():
                out = zipfile.ZipInfo(info.filename, info.date_time)
                out.external_attr = info.external_attr
                archive.writestr(out, entries[info.filename], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
                done.add(info.filename)
        for name in sorted(set(entries) - done):
            archive.writestr(zipfile.ZipInfo(name, DATE), entries[name], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    return buffer.getvalue()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', type=Path, default=ROOT / 'dist/evergarden.jar')
    parser.add_argument('--promote', action='store_true', help='Copy the JAR to dist/ and the packs to evergarden/dist/')
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    base = OUT / 'base.jar'
    shutil.copy2(args.base, base)
    work = OUT / 'build'
    shutil.rmtree(work, ignore_errors=True)
    work.mkdir(parents=True)
    seraph_java, seraph_bedrock, names = seraph.assets()

    with zipfile.ZipFile(base) as jar:
        old = {name: jar.read('resource-packs/' + name) for name in ('judge-java.zip', 'evergarden-bedrock.mcpack', 'geyser-mappings.json', 'pack-hashes.json')}
        design_raw = jar.read('judge-design.json')
        base_plugin = jar.read('plugin.yml').decode('utf8')
    java, java_infos = read_zip(old['judge-java.zip'])
    bedrock, bedrock_infos = read_zip(old['evergarden-bedrock.mcpack'])
    # Only existing Seraph entries are replaced; identifiers, icons and mappings stay as delivered.
    for label, entries, update in (('java', java, seraph_java), ('bedrock', bedrock, seraph_bedrock)):
        owned = {n for n in entries if 'judge_seraph' in n}
        if owned != set(update):
            raise RuntimeError(f'{label}: Seraph entries differ from the delivered pack: {sorted(owned ^ set(update))}')
        entries.update(update)
    controllers = json.loads(bedrock['render_controllers/judge.json'])['render_controllers']
    if 'controller.render.evergarden_judge_glow' not in controllers:
        raise RuntimeError('Bedrock pack lacks the unlit Judge render controller')
    item_atlas = json.loads(bedrock['textures/item_texture.json'])['texture_data']
    if any(item_atlas.get('voidscape.' + n) != {'textures': 'textures/items/judge_seraph'} for n in names):
        raise RuntimeError('Seraph icon routing differs from the delivered pack')
    mappings = json.loads(old['geyser-mappings.json'])
    rows = {row['bedrock_identifier'] for row in mappings['items'].get('minecraft:iron_helmet', [])}
    if not {'voidscape:' + n for n in names} <= rows:
        raise RuntimeError('Seraph Geyser mappings are missing')
    manifest = json.loads(bedrock['manifest.json'])
    manifest['header']['version'] = BEDROCK_VERSION
    for entry in manifest['modules']:
        entry['version'] = BEDROCK_VERSION
    bedrock['manifest.json'] = json.dumps(manifest, ensure_ascii=False, separators=(',', ':')).encode('utf8')

    java_bytes, bedrock_bytes = write_zip(java, java_infos), write_zip(bedrock, bedrock_infos)
    hashes = json.loads(old['pack-hashes.json'])
    hashes['judge-java.zip'] = hashlib.sha1(java_bytes).hexdigest()
    hashes['evergarden-bedrock.mcpack'] = hashlib.sha1(bedrock_bytes).hexdigest()
    hashes_bytes = (json.dumps(hashes, indent=2) + '\n').replace('\n', '\r\n').encode()
    design = json.loads(design_raw)
    design['bedrock-version'] = '%d.%d.%d' % tuple(BEDROCK_VERSION)
    design['admin-preview']['revision'] = seraph.REVISION
    design_bytes = (json.dumps(design, indent=2) + '\n').encode()
    lines = base_plugin.splitlines(keepends=True)
    if not lines[1].startswith('version: '):
        raise RuntimeError('Unexpected plugin.yml layout')
    lines[1] = f"version: '{VERSION}'" + lines[1][len(lines[1].rstrip('\r\n')):]
    plugin = ''.join(lines).encode('utf8')

    replacements = {'plugin.yml': plugin, 'judge-design.json': design_bytes,
                    'resource-packs/judge-java.zip': java_bytes, 'resource-packs/evergarden-bedrock.mcpack': bedrock_bytes,
                    'resource-packs/pack-hashes.json': hashes_bytes}
    jar_path = work / 'evergarden.jar'
    with zipfile.ZipFile(base) as previous, zipfile.ZipFile(jar_path, 'w', zipfile.ZIP_DEFLATED) as result:
        for entry in previous.infolist():
            if entry.filename not in replacements:
                result.writestr(entry, previous.read(entry.filename))
        for name, data in replacements.items():
            result.writestr(zipfile.ZipInfo(name, DATE), data,
                            compress_type=zipfile.ZIP_STORED if name.endswith(('.zip', '.mcpack')) else zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(jar_path) as release, zipfile.ZipFile(base) as previous:
        if release.testzip() is not None or len(release.namelist()) != len(set(release.namelist())):
            raise RuntimeError('Invalid release archive')
        if set(release.namelist()) != set(previous.namelist()):
            raise RuntimeError('JAR entry set changed')
        for info in previous.infolist():
            if info.filename not in replacements and release.read(info.filename) != previous.read(info.filename):
                raise RuntimeError('Unrelated entry changed: ' + info.filename)
    for label, before, after, owned in (('java', old['judge-java.zip'], java_bytes, seraph_java),
                                        ('bedrock', old['evergarden-bedrock.mcpack'], bedrock_bytes, seraph_bedrock)):
        a, _ = read_zip(before)
        b, _ = read_zip(after)
        if set(a) != set(b):
            raise RuntimeError(f'{label}: pack entry set changed')
        for name, data in a.items():
            if name not in owned and name != 'manifest.json' and b[name] != data:
                raise RuntimeError(f'{label}: {name} changed')
    build.check_jar(jar_path)
    for name, data in (('judge-java.zip', java_bytes), ('evergarden-bedrock.mcpack', bedrock_bytes), ('pack-hashes.json', hashes_bytes)):
        (work / name).write_bytes(data)
    digest = sha(jar_path.read_bytes())
    if args.promote:
        for name in ('judge-java.zip', 'evergarden-bedrock.mcpack', 'pack-hashes.json'):
            target = ROOT / 'evergarden/dist' / name
            staged = target.with_name(name + '.staged')
            shutil.copy2(work / name, staged)
            os.replace(staged, target)
        (ROOT / 'evergarden/src/main/resources/judge-design.json').write_bytes(design_bytes)
        source_plugin = ROOT / 'evergarden/src/main/resources/plugin.yml'
        text = source_plugin.read_bytes().decode('utf8').splitlines(keepends=True)
        text[1] = f"version: '{VERSION}'" + text[1][len(text[1].rstrip('\r\n')):]
        source_plugin.write_bytes(''.join(text).encode('utf8'))
        for target in (ROOT / 'dist/evergarden.jar', ROOT / 'dist' / ('evergarden-' + VERSION + '.jar')):
            staged = target.with_suffix('.jar.staged')
            shutil.copy2(jar_path, staged)
            os.replace(staged, target)
    report = {'version': VERSION, 'sha256': digest, 'baseline_sha256': sha(base.read_bytes()), 'bedrock_version': BEDROCK_VERSION,
              'seraph_revision': seraph.REVISION, 'java_changed': sorted(seraph_java), 'bedrock_changed': sorted(seraph_bedrock),
              'judge_java_sha1': hashes['judge-java.zip'], 'promoted': args.promote}
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps({k: report[k] for k in ('version', 'sha256', 'bedrock_version', 'promoted')}, indent=2))


if __name__ == '__main__':
    main()
