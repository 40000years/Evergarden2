"""Build Evergarden e2.136: Thornwhisper / Runelock held on Bedrock like the vanilla bow and crossbow.

e2.134 brought the redesigned bow and crossbow back. e2.136 gives Bedrock Mojang's own bow/crossbow
attachable (texture mesh, wield, first-person pull) with sprites of the sculpts, the player controllers
that count them as a bow / crossbow (two-handed draw, crossbow charge and aim), and Geyser definitions
with use_animation bow / crossbow (RangedBedrockAnimation). Java is unchanged. (e2.135 was map-trades.)

Over a delivered Evergarden JAR (--base, default the current dist/evergarden.jar) this:
* regenerates the adventurer weapon family and replaces ONLY the two ranged weapons' pack files (stale
  ones are dropped), plus the shared Bedrock animation / render-controller files after checking every
  melee entry in them is unchanged;
* replaces their Bedrock icons and Geyser mappings (standby + loaded arrow/rocket definitions);
* bumps the Bedrock pack to 3.57.0 and recompiles AdventurerWeaponService and RangedBedrockAnimation;
* keeps every other JAR and pack entry byte-identical.

    python tools/build_ranged_weapons_release.py [--base JAR] [--promote]
"""
import argparse
import hashlib
import importlib.util
import io
import json
import os
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
import build_plugin_jars as build  # noqa: E402

OUT = ROOT / 'output/ranged-bedrock-hold-20261025'
VERSION = '3.0.0-e2.136-ranged-bedrock-hold'
BEDROCK_VERSION = [3, 57, 0]
RANGED = ('thornwhisper', 'runelock')
SHARED = ('animations/adventurer_weapons.animation.json', 'render_controllers/adventurer_weapons.render.json')
# Ranged-only pack files whose names do not carry a weapon name.
EXTRA = ('animation_controllers/adventurer_ranged_player.controller.json',)
OWNED = ('com/example/voidscape/item/AdventurerWeaponService', 'com/example/voidscape/item/RangedBedrockAnimation')
DATE = (2026, 10, 25, 0, 0, 0)


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


def ranged(name):
    return name in EXTRA or ('adventurer' in name and any(w in name for w in RANGED))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', type=Path, default=ROOT / 'dist/evergarden.jar')
    parser.add_argument('--promote', action='store_true', help='Copy the JAR to dist/ and the packs/mappings to evergarden/dist/')
    args = parser.parse_args()
    # Work from a snapshot so another release landing in dist/ mid-build cannot mix two baselines.
    OUT.mkdir(parents=True, exist_ok=True)
    BASE = OUT / 'base.jar'
    shutil.copy2(args.base, BASE)
    BASE_SHA256 = sha(BASE.read_bytes())
    work = OUT / 'build'
    shutil.rmtree(work, ignore_errors=True)
    java_dir, bedrock_dir, art_dir = work / 'java', work / 'bedrock', work / 'art'
    spec = importlib.util.spec_from_file_location('adventurer_weapon_assets', ROOT / 'evergarden/tools/adventurer_weapon_assets.py')
    module = importlib.util.module_from_spec(spec)
    sys.modules['adventurer_weapon_assets'] = module
    spec.loader.exec_module(module)
    module.ART = art_dir
    atlas, mappings = {}, {'format_version': 2, 'items': {}}
    module.register(java_dir, bedrock_dir, atlas, mappings)

    with zipfile.ZipFile(BASE) as jar:
        old = {name: jar.read('resource-packs/' + name) for name in
               ('evergarden-java.zip', 'evergarden-bedrock.mcpack', 'geyser-mappings.json', 'pack-hashes.json')}
        design_raw = jar.read('judge-design.json')
        base_plugin = jar.read('plugin.yml').decode('utf8')
    java, java_infos = read_zip(old['evergarden-java.zip'])
    bedrock, bedrock_infos = read_zip(old['evergarden-bedrock.mcpack'])
    report = {'java_added': [], 'bedrock_added': [], 'java_removed': [], 'bedrock_removed': []}
    # The previous ranged files go; the fresh export below brings back the ones still used.
    for entries, key in ((java, 'java_removed'), (bedrock, 'bedrock_removed')):
        for name in sorted(n for n in entries if ranged(n)):
            del entries[name]
            report[key].append(name)
    for folder, entries, key in ((java_dir, java, 'java_added'), (bedrock_dir, bedrock, 'bedrock_added')):
        for path in sorted(folder.rglob('*')):
            name = path.relative_to(folder).as_posix()
            if path.is_file() and ranged(name):
                entries[name] = path.read_bytes()
                report[key].append(name)
    for name in SHARED:
        fresh, current = json.loads((bedrock_dir / name).read_text(encoding='utf8')), json.loads(bedrock[name])
        section = 'animations' if 'animations' in fresh else 'render_controllers'
        for key, value in current[section].items():
            if not any(w in key for w in RANGED) and fresh[section].get(key) != value:
                raise RuntimeError(f'{name}: melee entry {key} would change')
        extra = sorted(k for k in fresh[section] if k not in current[section] or any(w in k for w in RANGED))
        if not extra or not all(any(w in k for w in RANGED) for k in extra):
            raise RuntimeError(f'{name}: unexpected new entries {extra}')
        bedrock[name] = (bedrock_dir / name).read_bytes()
    item_atlas = json.loads(bedrock['textures/item_texture.json'])
    added_icons = {k: v for k, v in atlas.items() if any(w in k for w in RANGED)}
    for key, value in atlas.items():
        if key not in added_icons and item_atlas['texture_data'].get(key) != value:
            raise RuntimeError('Melee icon changed: ' + key)
    for key in [k for k in item_atlas['texture_data'] if any(w in k for w in RANGED) and k not in added_icons]:
        del item_atlas['texture_data'][key]
    item_atlas['texture_data'].update(added_icons)
    bedrock['textures/item_texture.json'] = json.dumps(item_atlas, ensure_ascii=False, separators=(',', ':')).encode('utf8')
    manifest = json.loads(bedrock['manifest.json'])
    manifest['header']['version'] = BEDROCK_VERSION
    for entry in manifest['modules']:
        entry['version'] = BEDROCK_VERSION
    bedrock['manifest.json'] = json.dumps(manifest, ensure_ascii=False, separators=(',', ':')).encode('utf8')

    current_mappings = json.loads(old['geyser-mappings.json'])
    for item in ('minecraft:bow', 'minecraft:crossbow'):
        others = [row for row in current_mappings['items'].get(item, []) if not any(w in row['bedrock_identifier'] for w in RANGED)]
        if others:
            raise RuntimeError(f'Baseline mappings map {item} to something else: {others}')
        current_mappings['items'].pop(item, None)
    if any(any(w in row['bedrock_identifier'] for w in RANGED) for rows in current_mappings['items'].values() for row in rows):
        raise RuntimeError('Ranged rows outside the bow/crossbow mappings')
    new_rows = {item: [row for row in rows if any(w in row['bedrock_identifier'] for w in RANGED)] for item, rows in mappings['items'].items()}
    for item, rows in new_rows.items():
        if rows:
            current_mappings['items'].setdefault(item, []).extend(rows)
    mappings_bytes = json.dumps(current_mappings, ensure_ascii=False, indent=2).encode('utf8') + b'\n'

    java_bytes, bedrock_bytes = write_zip(java, java_infos), write_zip(bedrock, bedrock_infos)
    hashes = json.loads(old['pack-hashes.json'])
    hashes['evergarden-java.zip'] = hashlib.sha1(java_bytes).hexdigest()
    hashes['evergarden-bedrock.mcpack'] = hashlib.sha1(bedrock_bytes).hexdigest()
    hashes_bytes = (json.dumps(hashes, indent=2) + '\n').replace('\n', '\r\n').encode()
    design = json.loads(design_raw)
    previous_version = design['bedrock-version']
    design_bytes = design_raw.replace(f'"bedrock-version": "{previous_version}"'.encode(),
                                      ('"bedrock-version": "%d.%d.%d"' % tuple(BEDROCK_VERSION)).encode())
    if json.loads(design_bytes)['bedrock-version'] != '%d.%d.%d' % tuple(BEDROCK_VERSION):
        raise RuntimeError('judge-design.json version not updated')
    # Only the version line changes, so a concurrent edit of the source plugin.yml cannot leak in.
    lines = base_plugin.splitlines(keepends=True)
    if not lines[1].startswith('version: '):
        raise RuntimeError('Unexpected plugin.yml layout')
    ending = lines[1][len(lines[1].rstrip('\r\n')):]
    lines[1] = f"version: '{VERSION}'" + ending
    plugin = ''.join(lines).encode('utf8')

    classes = work / 'classes'
    build.OUT = work
    sources = [ROOT / 'evergarden/src/main/java' / (p + '.java') for p in OWNED]
    classpath = [BASE.as_posix(), (ROOT / 'dist/advance-magic.jar').as_posix()] + build.pinned_classpath(build.EVERGARDEN_ARGS)
    build.compile_sources('ranged-weapons', sources, classpath, classes)

    def replaced(name):
        return any(name == p + '.class' or name.startswith(p + '$') for p in OWNED)

    replacements = {'plugin.yml': plugin, 'judge-design.json': design_bytes,
                    'resource-packs/evergarden-java.zip': java_bytes, 'resource-packs/evergarden-bedrock.mcpack': bedrock_bytes,
                    'resource-packs/geyser-mappings.json': mappings_bytes, 'resource-packs/pack-hashes.json': hashes_bytes}
    jar_path = work / 'evergarden.jar'
    with zipfile.ZipFile(BASE) as previous, zipfile.ZipFile(jar_path, 'w', zipfile.ZIP_DEFLATED) as result:
        for entry in previous.infolist():
            if replaced(entry.filename) or entry.filename in replacements:
                continue
            result.writestr(entry, previous.read(entry.filename))
        for path in sorted(classes.rglob('*.class')):
            name = path.relative_to(classes).as_posix()
            if not replaced(name):
                raise RuntimeError('Unexpected class ' + name)
            # Fixed date, so rebuilding with --promote reproduces the tested JAR byte for byte.
            result.writestr(zipfile.ZipInfo(name, DATE), path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
        for name, data in replacements.items():
            result.writestr(zipfile.ZipInfo(name, DATE), data,
                            compress_type=zipfile.ZIP_STORED if name.endswith(('.zip', '.mcpack')) else zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(jar_path) as release, zipfile.ZipFile(BASE) as previous:
        if release.testzip() is not None or len(release.namelist()) != len(set(release.namelist())):
            raise RuntimeError('Invalid release archive')
        for info in previous.infolist():
            if not replaced(info.filename) and info.filename not in replacements and release.read(info.filename) != previous.read(info.filename):
                raise RuntimeError('Unrelated entry changed: ' + info.filename)
    # Packs: everything except the ranged files, shared files, icon atlas and manifest is byte-identical.
    for label, before, after in (('java', old['evergarden-java.zip'], java_bytes), ('bedrock', old['evergarden-bedrock.mcpack'], bedrock_bytes)):
        a, _ = read_zip(before)
        b, _ = read_zip(after)
        for name, data in a.items():
            if not ranged(name) and name not in SHARED + ('textures/item_texture.json', 'manifest.json') and b.get(name) != data:
                raise RuntimeError(f'{label}: {name} changed')
        if any(not ranged(name) for name in set(b) - set(a)):
            raise RuntimeError(f'{label}: unexpected new entries')
    build.check_jar(jar_path)
    digest = sha(jar_path.read_bytes())
    for name, data in (('evergarden-java.zip', java_bytes), ('evergarden-bedrock.mcpack', bedrock_bytes),
                       ('geyser-mappings.json', mappings_bytes), ('pack-hashes.json', hashes_bytes)):
        (work / name).write_bytes(data)
    if args.promote:
        for name in ('evergarden-java.zip', 'evergarden-bedrock.mcpack', 'geyser-mappings.json', 'pack-hashes.json'):
            target = ROOT / 'evergarden/dist' / name
            staged = target.with_name(name + '.staged')
            shutil.copy2(work / name, staged)
            os.replace(staged, target)
        (ROOT / 'evergarden/src/main/resources/judge-design.json').write_bytes(design_bytes)
        source_plugin = ROOT / 'evergarden/src/main/resources/plugin.yml'
        # Bytes in and out, so Windows newline translation cannot turn the file into CRLF.
        text = source_plugin.read_bytes().decode('utf8').splitlines(keepends=True)
        if not text[1].startswith('version: '):
            raise RuntimeError('Unexpected source plugin.yml layout')
        text[1] = f"version: '{VERSION}'" + text[1][len(text[1].rstrip('\r\n')):]
        source_plugin.write_bytes(''.join(text).encode('utf8'))
        for path in sorted(art_dir.iterdir()):
            if any(path.name.startswith(w) or path.name.startswith('adventurer_' + w) for w in RANGED):
                shutil.copy2(path, ROOT / 'evergarden/art/adventurer-weapons' / path.name)
        for target in (ROOT / 'dist/evergarden.jar', ROOT / 'dist' / ('evergarden-' + VERSION + '.jar')):
            staged = target.with_suffix('.jar.staged')
            shutil.copy2(jar_path, staged)
            os.replace(staged, target)
    report.update({'version': VERSION, 'sha256': digest, 'baseline_sha256': BASE_SHA256, 'bedrock_version': BEDROCK_VERSION,
                   'icons': added_icons, 'mappings': new_rows, 'promoted': args.promote})
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps({k: report[k] for k in ('version', 'sha256', 'bedrock_version', 'promoted')}, indent=2),
          f"\n{len(report['java_added'])} Java + {len(report['bedrock_added'])} Bedrock files added")


if __name__ == '__main__':
    main()
