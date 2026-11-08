"""Build Advance Magic 1.36.0: elemental spell rework with Java/Bedrock visual parity.

Over the 1.35.0 JAR this recompiles only the classes the rework owns (spell service, catalog, mana refund,
ally rule, the native renderer and the Bedrock cube bridge), replaces config.yml and legendary-blocks.json,
and patches the Bedrock pack (2.37.0):

* the full-pose cube rig gains per-axis scale channels (sx, sy, sz), so spikes, walls, armour plates and
  wave crests drawn as Java BlockDisplays appear with the same shape on Bedrock;
* eleven block textures are added to its catalog (appended, so existing material indices stay put).

The Java pack, the Geyser mappings and every other JAR entry stay byte-identical. GeyserPackCleanup's
unreleased source edit is still excluded (the shipped class is kept).

    python tools/build_spell_rework_release.py [--promote]
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
import build_plugin_jars as build  # noqa: E402
import build_unused_cleanup_release as previous  # noqa: E402
import legendary_block_assets as rig  # noqa: E402

OUT = ROOT / 'output/spell-rework-20261027'
BASE = ROOT / 'dist/advance-magic-1.35.0-unused-cleanup.jar'
BASE_SHA256 = '8b045394a83cd2d45cd9c7fb18e1a0d7a31da4425e44148adebef8a4103a9546'
GARDEN = ROOT / 'dist/evergarden.jar'
VERSION = '1.36.0-spell-rework'
BEDROCK_VERSION = [2, 37, 0]
DATE = (2026, 10, 27, 0, 0, 0)
PACKS = previous.PACKS
PKG = previous.PKG
OWNED = tuple(PKG + n for n in (
    'AdvanceMagicPlugin', 'elemental/ElementalSpell', 'elemental/ElementalSpellService', 'mana/ManaService',
    'spell/MagicContext', 'spell/BedrockBlockModels', 'spell/ElementalVfx', 'spell/MythicVisuals', 'spell/NativeElementalVfx'))
KNOWN_DRIFT = previous.KNOWN_DRIFT
RESOURCES = ('config.yml', 'legendary-blocks.json')
RIG_JSON = ('entity/legendary_block.entity.json', 'animations/legendary_block.animation.json',
            'render_controllers/legendary_block.render.json')


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def bedrock_pack(data):
    """The shipped pack with the regenerated rig JSON, the new catalog textures and a new version."""
    files, infos = previous.read_zip(data)
    staging = OUT / 'rig'
    if staging.exists():
        shutil.rmtree(staging)
    rig.write_assets(staging)
    catalog = json.loads((ROOT / 'advance-magic/src/main/resources/legendary-blocks.json').read_text(encoding='utf8'))
    changed = {}
    for name in RIG_JSON:
        changed[name] = (staging / name).read_bytes()
    added = []
    for material in catalog:
        path = f'textures/legendary_blocks/{material}.png'
        if path not in files:
            changed[path] = (staging / path).read_bytes()
            added.append(path)
    if len(added) != 11:
        raise RuntimeError('Unexpected new rig textures: ' + str(added))
    manifest = json.loads(files['manifest.json'])
    if manifest['header']['version'] >= BEDROCK_VERSION:
        raise RuntimeError('Bedrock version must advance from ' + str(manifest['header']['version']))
    manifest['header']['version'] = BEDROCK_VERSION
    for module in manifest['modules']:
        module['version'] = BEDROCK_VERSION
    changed['manifest.json'] = json.dumps(manifest, ensure_ascii=False, separators=(',', ':')).encode('utf8')
    result = dict(files)
    result.update(changed)
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED) as out:
        for info in infos:
            item = zipfile.ZipInfo(info.filename, info.date_time)
            item.external_attr = info.external_attr
            item.create_system = info.create_system
            out.writestr(item, result[info.filename], compress_type=info.compress_type, compresslevel=9)
        for name in added:
            out.writestr(zipfile.ZipInfo(name, DATE), result[name], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    packed = buffer.getvalue()
    again, _ = previous.read_zip(packed)
    if set(again) != set(result) or any(again[k] != result[k] for k in result):
        raise RuntimeError('Staged Bedrock pack differs from the intended entries')
    if any(again[k] != files[k] for k in files if k not in changed):
        raise RuntimeError('An unrelated Bedrock entry changed')
    return packed, {'replaced': sorted(n for n in changed if n in files), 'added': added, 'bytes': [len(data), len(packed)]}


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--promote', action='store_true')
    args = parser.parse_args()
    release_dir = OUT / 'release'
    release_dir.mkdir(parents=True, exist_ok=True)
    jar_path = release_dir / 'advance-magic.jar'
    if args.promote:
        return promote(jar_path)
    base = BASE.read_bytes()
    if sha256(base) != BASE_SHA256:
        raise RuntimeError('Base JAR changed: ' + str(BASE))
    if sha256((ROOT / 'dist/advance-magic.jar').read_bytes()) != BASE_SHA256:
        raise RuntimeError('dist/advance-magic.jar is no longer 1.35.0; rebase this release first')
    base_files, base_infos = previous.read_zip(base)

    packs = {name: base_files['resource-packs/' + name] for name in PACKS}
    packs['advance-magic-bedrock.mcpack'], bedrock = bedrock_pack(packs['advance-magic-bedrock.mcpack'])
    hashes_raw = base_files['resource-packs/pack-hashes.json']
    hashes = json.loads(hashes_raw)
    hashes['advance-magic-bedrock.mcpack'] = hashlib.sha1(packs['advance-magic-bedrock.mcpack']).hexdigest()
    if hashes['advance-magic-java.zip'] != hashlib.sha1(packs['advance-magic-java.zip']).hexdigest():
        raise RuntimeError('Base pack-hashes.json does not describe the shipped Java pack')
    packs['pack-hashes.json'] = (json.dumps(hashes, indent=2) + ('\n' if hashes_raw.endswith(b'\n') else '')).encode()

    classes_dir = OUT / 'classes'
    build.OUT = OUT
    sources = sorted((ROOT / 'advance-magic/src/main/java').rglob('*.java'))
    build.compile_sources('advance-magic', sources, build.pinned_classpath(build.ADVANCE_ARGS), classes_dir)
    compiled = {p.relative_to(classes_dir).as_posix(): p.read_bytes() for p in classes_dir.rglob('*.class')}
    base_classes = {n: d for n, d in base_files.items() if n.endswith('.class')}
    for name, data in compiled.items():
        if previous.family(name, OWNED):
            continue
        if name not in base_classes:
            raise RuntimeError('Unowned class appeared in source: ' + name)
        if data != base_classes[name] and name not in KNOWN_DRIFT:
            raise RuntimeError('Unowned class differs from the shipped JAR: ' + name)
    missing = sorted(n for n in base_classes if not previous.family(n, OWNED) and n not in compiled)
    if missing:
        raise RuntimeError('Shipped classes without a source: ' + ', '.join(missing))
    owned = {n: d for n, d in compiled.items() if previous.family(n, OWNED)}

    plugin = base_files['plugin.yml'].decode('utf8').splitlines(keepends=True)
    index = next(i for i, line in enumerate(plugin) if line.startswith('version: '))
    plugin[index] = f"version: '{VERSION}'" + plugin[index][len(plugin[index].rstrip('\r\n')):]
    replacements = {'plugin.yml': ''.join(plugin).encode('utf8')}
    for name in RESOURCES:
        replacements[name] = (ROOT / 'advance-magic/src/main/resources' / name).read_bytes()
    replacements.update({'resource-packs/' + n: packs[n] for n in PACKS})

    with zipfile.ZipFile(jar_path, 'w', zipfile.ZIP_DEFLATED) as out:
        for info in base_infos:
            name = info.filename
            if previous.family(name, OWNED):
                continue
            if name in replacements:
                out.writestr(zipfile.ZipInfo(name, DATE), replacements[name],
                             compress_type=zipfile.ZIP_STORED if name.endswith('.mcpack') else zipfile.ZIP_DEFLATED)
            else:
                out.writestr(info, base_files[name])
        for name in sorted(owned):
            out.writestr(zipfile.ZipInfo(name, DATE), owned[name])
    build.check_jar(jar_path)
    release, _ = previous.read_zip(jar_path.read_bytes())
    for name, data in base_files.items():
        if previous.family(name, OWNED) or name in replacements:
            continue
        if release.get(name) != data:
            raise RuntimeError('Unrelated entry changed: ' + name)
    release_classes = {n: d for n, d in release.items() if n.endswith('.class')}
    before, after = previous.index_classes(base_classes), previous.index_classes(release_classes)
    problems = previous.linkage(before, after, release_classes)
    with zipfile.ZipFile(GARDEN) as garden:
        garden_classes = {'evergarden:' + n: garden.read(n) for n in garden.namelist() if n.endswith('.class')}
    problems += previous.linkage(before, after, garden_classes)
    if problems:
        raise RuntimeError('Linkage problems:\n' + '\n'.join(problems[:40]))
    report = {'version': VERSION, 'base_sha256': BASE_SHA256, 'sha256': sha256(jar_path.read_bytes()),
              'bedrock_version': BEDROCK_VERSION, 'bedrock': bedrock, 'owned_classes': sorted(owned),
              'changed_owned': sorted(n for n in owned if base_classes.get(n) != owned[n]),
              'linkage': {'advance_magic_classes': len(release_classes), 'evergarden_classes': len(garden_classes), 'problems': 0},
              'sources': {p.relative_to(ROOT).as_posix(): sha256(p.read_bytes()) for p in sources}, 'promoted': False}
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps({k: report[k] for k in ('version', 'sha256', 'bedrock_version')} | {'bedrock': bedrock, 'changed_classes': len(report['changed_owned'])}, indent=2))


def promote(jar_path):
    report = json.loads((OUT / 'release-build.json').read_text(encoding='utf8'))
    if sha256(jar_path.read_bytes()) != report['sha256']:
        raise RuntimeError('Release candidate changed since it was built')
    if sha256((ROOT / 'dist/advance-magic.jar').read_bytes()) != BASE_SHA256:
        raise RuntimeError('Concurrent release changed dist/advance-magic.jar')
    for path, digest in report['sources'].items():
        if sha256((ROOT / path).read_bytes()) != digest:
            raise RuntimeError('Source changed since the build: ' + path)
    gates = json.loads((OUT / 'gates.json').read_text(encoding='utf8'))
    if gates.get('candidate_sha256') != report['sha256'] or not gates.get('passed'):
        raise RuntimeError('Gates were not recorded as passing for this candidate')
    dist = ROOT / 'advance-magic/dist'
    with zipfile.ZipFile(jar_path) as release:
        for name in PACKS:
            staged = dist / (name + '.staged')
            staged.write_bytes(release.read('resource-packs/' + name))
            os.replace(staged, dist / name)
    source = ROOT / 'advance-magic/src/main/resources/plugin.yml'
    text = source.read_bytes().decode('utf8').splitlines(keepends=True)
    k = next(j for j, line in enumerate(text) if line.startswith('version: '))
    text[k] = f"version: '{VERSION}'" + text[k][len(text[k].rstrip('\r\n')):]
    source.write_bytes(''.join(text).encode('utf8'))
    for target in (ROOT / 'dist/advance-magic.jar', ROOT / 'dist' / f'advance-magic-{VERSION}.jar'):
        staged = target.with_suffix('.jar.staged')
        shutil.copy2(jar_path, staged)
        os.replace(staged, target)
    report['promoted'] = True
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print('Promoted', VERSION, report['sha256'])


if __name__ == '__main__':
    main()
