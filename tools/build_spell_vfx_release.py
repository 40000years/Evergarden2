"""Build Advance Magic 1.37.0: lighter, sprite-led elemental spell visuals for both clients.

Players reported Bedrock freezing on earth spells and many props missing. Over the 1.36.0 JAR this:

* recompiles the native renderer and its Geyser bridge (NativeElementalVfx, MythicVisuals,
  MythicLineVisuals). Particles are limited to types Geyser draws as single Bedrock particles (1.36 sent
  DUST_PILLAR, which Geyser turns into the mace smash emitter, up to 90 at once for Stone Wall, and BLOCK,
  which Geyser plays as a whole block-break burst per particle: the dirt flood. Both now draw falling
  dust, and the Legendary scenes cap emitter-type particles for Bedrock viewers). Bedrock
  viewers get their own particle budget, a resting prop sends no update, and each cast has fewer props;
* adds tintable VFX sprites (tools/vfx_sprite_assets.py) to both packs: painted magic circles, shockwave
  rings, flares, slashes and sparks in nine tints (Java item displays, Bedrock particles);
* regenerates the Bedrock cube rig JSON with flat Molang (a short OR for its translucent materials and
  three animated-texture branches, instead of 47-deep nested ternaries) and a scale fallback, at
  Bedrock pack 2.38.0.

Every other JAR and pack entry stays byte-identical; GeyserPackCleanup's unreleased edit stays excluded.

    python tools/build_spell_vfx_release.py [--promote]
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
import vfx_sprite_assets as sprites  # noqa: E402

OUT = ROOT / 'output/spell-rework2-20261027'
BASE = ROOT / 'dist/advance-magic-1.36.0-spell-rework.jar'
BASE_SHA256 = 'bbc09d6670a78d5f867f9bfb746a94e53e63ab40f13f03dc51c21e78c3110d03'
GARDEN = ROOT / 'dist/evergarden.jar'
VERSION = '1.37.0-spell-vfx'
BEDROCK_VERSION = [2, 38, 0]
DATE = (2026, 10, 27, 0, 0, 0)
PACKS = previous.PACKS
PKG = previous.PKG
OWNED = tuple(PKG + n for n in (
    'AdvanceMagicPlugin', 'elemental/ElementalSpell', 'elemental/ElementalSpellService', 'mana/ManaService',
    'spell/MagicContext', 'spell/BedrockBlockModels', 'spell/ElementalVfx', 'spell/MythicVisuals',
    'spell/MythicLineVisuals', 'spell/NativeElementalVfx'))
KNOWN_DRIFT = previous.KNOWN_DRIFT
RIG_JSON = ('entity/legendary_block.entity.json', 'animations/legendary_block.animation.json',
            'render_controllers/legendary_block.render.json')


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def patch(data, replaced, added):
    """The pack with `replaced` entries swapped in place and `added` entries appended, all else byte-identical."""
    files, infos = previous.read_zip(data)
    clash = sorted(set(added) & set(files))
    if clash:
        raise RuntimeError('New entries already exist: ' + ', '.join(clash[:5]))
    missing = sorted(set(replaced) - set(files))
    if missing:
        raise RuntimeError('Replaced entries missing: ' + ', '.join(missing))
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED) as out:
        for info in infos:
            item = zipfile.ZipInfo(info.filename, info.date_time)
            item.external_attr = info.external_attr
            item.create_system = info.create_system
            out.writestr(item, replaced.get(info.filename, files[info.filename]), compress_type=info.compress_type, compresslevel=9)
        for name in sorted(added):
            out.writestr(zipfile.ZipInfo(name, DATE), added[name], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    packed = buffer.getvalue()
    again, _ = previous.read_zip(packed)
    expected = {**files, **replaced, **added}
    if set(again) != set(expected) or any(again[k] != expected[k] for k in expected):
        raise RuntimeError('Staged pack differs from the intended entries')
    return packed


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
        raise RuntimeError('dist/advance-magic.jar is no longer 1.36.0; rebase this release first')
    base_files, base_infos = previous.read_zip(base)

    java_pack = OUT / 'base-java.zip'
    java_pack.write_bytes(base_files['resource-packs/advance-magic-java.zip'])
    sprite_dir = OUT / 'sprites'
    if sprite_dir.exists():
        shutil.rmtree(sprite_dir)
    java_sprites, bedrock_sprites = sprites.write_assets(sprite_dir, java_pack)
    rig_dir = OUT / 'rig'
    if rig_dir.exists():
        shutil.rmtree(rig_dir)
    rig.write_assets(rig_dir)
    rig_json = {name: (rig_dir / name).read_bytes() for name in RIG_JSON}

    bedrock_files, _ = previous.read_zip(base_files['resource-packs/advance-magic-bedrock.mcpack'])
    manifest = json.loads(bedrock_files['manifest.json'])
    if manifest['header']['version'] >= BEDROCK_VERSION:
        raise RuntimeError('Bedrock version must advance from ' + str(manifest['header']['version']))
    manifest['header']['version'] = BEDROCK_VERSION
    for module in manifest['modules']:
        module['version'] = BEDROCK_VERSION
    rig_json['manifest.json'] = json.dumps(manifest, ensure_ascii=False, separators=(',', ':')).encode('utf8')

    packs = {name: base_files['resource-packs/' + name] for name in PACKS}
    packs['advance-magic-java.zip'] = patch(packs['advance-magic-java.zip'], {}, java_sprites)
    packs['advance-magic-bedrock.mcpack'] = patch(packs['advance-magic-bedrock.mcpack'], rig_json, bedrock_sprites)
    hashes_raw = base_files['resource-packs/pack-hashes.json']
    hashes = json.loads(hashes_raw)
    for name in PACKS[:2]:
        hashes[name] = hashlib.sha1(packs[name]).hexdigest()
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
    for name in ('config.yml', 'legendary-blocks.json'):
        if (ROOT / 'advance-magic/src/main/resources' / name).read_bytes() != base_files[name]:
            raise RuntimeError(name + ' changed since 1.36.0; this release does not ship resource changes')

    plugin = base_files['plugin.yml'].decode('utf8').splitlines(keepends=True)
    index = next(i for i, line in enumerate(plugin) if line.startswith('version: '))
    plugin[index] = f"version: '{VERSION}'" + plugin[index][len(plugin[index].rstrip('\r\n')):]
    replacements = {'plugin.yml': ''.join(plugin).encode('utf8')}
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
              'bedrock_version': BEDROCK_VERSION,
              'packs': {n: [len(base_files['resource-packs/' + n]), len(packs[n])] for n in PACKS[:2]},
              'sprites': {'java': len(java_sprites), 'bedrock': len(bedrock_sprites)},
              'changed_classes': sorted(n for n in owned if base_classes.get(n) != owned[n]),
              'sources': {p.relative_to(ROOT).as_posix(): sha256(p.read_bytes()) for p in sources}, 'promoted': False}
    (OUT / 'release-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps({k: report[k] for k in ('version', 'sha256', 'bedrock_version', 'packs', 'sprites')}
                     | {'changed_classes': len(report['changed_classes'])}, indent=2))


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
