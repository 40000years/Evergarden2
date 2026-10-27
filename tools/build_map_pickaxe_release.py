"""Build Evergarden e2.140 (e2.139 was the first build): 3D relic pickaxes and the Seraph map shop fix.

e2.140 renames the pickaxes' Bedrock bone from "body" (a player bone, so Bedrock glued the model to the
wearer's torso) to "tool". Rebuilding over e2.139 recompiles SanctuaryMaps to the same bytes.

* Rift Excavator and Smelter's Pickaxe become 3D models (evergarden/tools/relic_pickaxe_assets.py) on Java
  and Bedrock, with rendered 3D icons; their Geyser mappings are unchanged.
* SanctuaryMaps says why a map cannot be offered, and offers a boss temple the player already reached
  when no new one is in range (bosses respawn).

Over a delivered Evergarden JAR (--base, default dist/evergarden.jar) this replaces only the pickaxe pack
entries, bumps the Bedrock pack to 3.59.0, recompiles SanctuaryMaps and keeps every other JAR and pack
entry byte-identical.

    python tools/build_map_pickaxe_release.py [--base JAR] [--promote]
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

OUT = ROOT / 'output/pickaxe-bone-20261026'
VERSION = '3.0.0-e2.140-pickaxe-bedrock-fix'
BEDROCK_VERSION = [3, 59, 0]
OWNED = ('com/example/voidscape/trade/SanctuaryMaps',)
NAMES = ('rift_pickaxe', 'smelter_pickaxe')
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


def replaced(name):
    return any(name == p + '.class' or name.startswith(p + '$') for p in OWNED)


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
    java_dir, bedrock_dir = work / 'java', work / 'bedrock'
    import relic_pickaxe_assets
    atlas = {}
    relic_pickaxe_assets.register(java_dir, bedrock_dir, atlas)

    with zipfile.ZipFile(base) as jar:
        old = {name: jar.read('resource-packs/' + name) for name in ('evergarden-java.zip', 'evergarden-bedrock.mcpack', 'geyser-mappings.json', 'pack-hashes.json')}
        design_raw = jar.read('judge-design.json')
        base_plugin = jar.read('plugin.yml').decode('utf8')
    java, java_infos = read_zip(old['evergarden-java.zip'])
    bedrock, bedrock_infos = read_zip(old['evergarden-bedrock.mcpack'])
    changed = {'java': [], 'bedrock': []}
    for folder, entries, key in ((java_dir, java, 'java'), (bedrock_dir, bedrock, 'bedrock')):
        for path in sorted(folder.rglob('*')):
            if path.is_file():
                name = path.relative_to(folder).as_posix()
                entries[name] = path.read_bytes()
                changed[key].append(name)
    item_atlas = json.loads(bedrock['textures/item_texture.json'])
    for key, value in atlas.items():
        if item_atlas['texture_data'].get(key) != value:
            raise RuntimeError('Pickaxe icon routing differs: ' + key)
    mappings = json.loads(old['geyser-mappings.json'])
    rows = {row['bedrock_identifier'] for row in mappings['items'].get('minecraft:netherite_pickaxe', [])}
    if rows != {'voidscape:' + n for n in NAMES}:
        raise RuntimeError(f'Unexpected netherite pickaxe mappings: {rows}')
    manifest = json.loads(bedrock['manifest.json'])
    manifest['header']['version'] = BEDROCK_VERSION
    for entry in manifest['modules']:
        entry['version'] = BEDROCK_VERSION
    bedrock['manifest.json'] = json.dumps(manifest, ensure_ascii=False, separators=(',', ':')).encode('utf8')

    java_bytes, bedrock_bytes = write_zip(java, java_infos), write_zip(bedrock, bedrock_infos)
    hashes = json.loads(old['pack-hashes.json'])
    hashes['evergarden-java.zip'] = hashlib.sha1(java_bytes).hexdigest()
    hashes['evergarden-bedrock.mcpack'] = hashlib.sha1(bedrock_bytes).hexdigest()
    hashes_bytes = (json.dumps(hashes, indent=2) + '\n').replace('\n', '\r\n').encode()
    design = json.loads(design_raw)
    design_bytes = design_raw.replace(f'"bedrock-version": "{design["bedrock-version"]}"'.encode(),
                                      ('"bedrock-version": "%d.%d.%d"' % tuple(BEDROCK_VERSION)).encode())
    if json.loads(design_bytes)['bedrock-version'] != '%d.%d.%d' % tuple(BEDROCK_VERSION):
        raise RuntimeError('judge-design.json version not updated')
    lines = base_plugin.splitlines(keepends=True)
    if not lines[1].startswith('version: '):
        raise RuntimeError('Unexpected plugin.yml layout')
    lines[1] = f"version: '{VERSION}'" + lines[1][len(lines[1].rstrip('\r\n')):]
    plugin = ''.join(lines).encode('utf8')

    classes = work / 'classes'
    build.OUT = work
    sources = [ROOT / 'evergarden/src/main/java' / (p + '.java') for p in OWNED]
    classpath = [base.as_posix(), (ROOT / 'dist/advance-magic.jar').as_posix()] + build.pinned_classpath(build.EVERGARDEN_ARGS)
    build.compile_sources('map-pickaxe', sources, classpath, classes)

    replacements = {'plugin.yml': plugin, 'judge-design.json': design_bytes,
                    'resource-packs/evergarden-java.zip': java_bytes, 'resource-packs/evergarden-bedrock.mcpack': bedrock_bytes,
                    'resource-packs/pack-hashes.json': hashes_bytes}
    jar_path = work / 'evergarden.jar'
    with zipfile.ZipFile(base) as previous, zipfile.ZipFile(jar_path, 'w', zipfile.ZIP_DEFLATED) as result:
        for entry in previous.infolist():
            if replaced(entry.filename) or entry.filename in replacements:
                continue
            result.writestr(entry, previous.read(entry.filename))
        for path in sorted(classes.rglob('*.class')):
            name = path.relative_to(classes).as_posix()
            if not replaced(name):
                raise RuntimeError('Unexpected class ' + name)
            result.writestr(zipfile.ZipInfo(name, DATE), path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
        for name, data in replacements.items():
            result.writestr(zipfile.ZipInfo(name, DATE), data,
                            compress_type=zipfile.ZIP_STORED if name.endswith(('.zip', '.mcpack')) else zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(jar_path) as release, zipfile.ZipFile(base) as previous:
        if release.testzip() is not None or len(release.namelist()) != len(set(release.namelist())):
            raise RuntimeError('Invalid release archive')
        for info in previous.infolist():
            if not replaced(info.filename) and info.filename not in replacements and release.read(info.filename) != previous.read(info.filename):
                raise RuntimeError('Unrelated entry changed: ' + info.filename)
    for label, before, after in (('java', old['evergarden-java.zip'], java_bytes), ('bedrock', old['evergarden-bedrock.mcpack'], bedrock_bytes)):
        a, _ = read_zip(before)
        b, _ = read_zip(after)
        for name, data in a.items():
            if name not in changed[label] and name != 'manifest.json' and b.get(name) != data:
                raise RuntimeError(f'{label}: {name} changed')
        if any(n not in changed[label] for n in set(b) - set(a)):
            raise RuntimeError(f'{label}: unexpected new entries')
    build.check_jar(jar_path)
    for name, data in (('evergarden-java.zip', java_bytes), ('evergarden-bedrock.mcpack', bedrock_bytes),
                       ('geyser-mappings.json', old['geyser-mappings.json']), ('pack-hashes.json', hashes_bytes)):
        (work / name).write_bytes(data)
    digest = sha(jar_path.read_bytes())
    if args.promote:
        for name in ('evergarden-java.zip', 'evergarden-bedrock.mcpack', 'geyser-mappings.json', 'pack-hashes.json'):
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
              'java_changed': changed['java'], 'bedrock_changed': changed['bedrock'], 'promoted': args.promote}
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps({k: report[k] for k in ('version', 'sha256', 'bedrock_version', 'promoted')}, indent=2))


if __name__ == '__main__':
    main()
