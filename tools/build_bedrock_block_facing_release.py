"""Build Advance Magic 1.29.4 (Bedrock Legendary blocks: facing, transparency, face shading) over 1.29.3.

* The Bedrock cube actor now spawns facing 180 degrees, so bone space lands in the mirrored-X frame its
  Molang encodes; at yaw 0 every prop was turned 180 degrees around its spawn point.
* Leaves cut out and ice/glass blend instead of rendering solid through emissive-alpha.
* Java's item face shading is baked into the full-bright cube faces.

Only MythicVisuals, plugin.yml, the legendary rig files of the Bedrock pack (version 2.33.0) and the pack
hashes change; the Java pack and every other entry stay byte-identical.

    python tools/build_bedrock_block_facing_release.py --output output/<fresh-folder> [--promote]
"""
import argparse
import copy
import hashlib
import io
import json
import shutil
import zipfile
from pathlib import Path

import build_plugin_jars as build
import legendary_block_assets as assets

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'output/legendary-bedrock-facing-20261025/before/advance-magic.jar'
BASE_SHA = 'a13271cc29a8896136a162caf3b934fb66164a7b83e27793c4e98dc9622dbd40'
VERSION = '1.29.4-bedrock-block-facing'
PACK_VERSION = [2, 33, 0]
OWNED = ('com/example/advancemagic/spell/MythicVisuals',)
RIG = ('textures/legendary_blocks/', 'entity/legendary_block.', 'models/entity/legendary_block.', 'animations/legendary_block.',
       'render_controllers/legendary_block.', 'materials/legendary_blocks.')
sha = lambda data: hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--promote', action='store_true', help='Copy the built JAR and pack into dist/ and advance-magic/dist/')
    args = parser.parse_args()
    out = args.output.resolve()
    if out.exists() or not out.is_relative_to((ROOT / 'output').resolve()):
        parser.error('Choose a fresh output/ build folder')
    if sha(BASE.read_bytes()) != BASE_SHA:
        raise RuntimeError('The 1.29.3 baseline backup changed unexpectedly.')
    out.mkdir(parents=True)
    sources = []
    for prefix in OWNED:
        path = ROOT / 'advance-magic/src/main/java' / (prefix + '.java')
        snapshot = out / 'source' / (prefix + '.java')
        snapshot.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, snapshot)
        sources.append(snapshot)
    build.OUT = out
    build.compile_sources('bedrock-block-facing', sources, [BASE.as_posix()] + build.pinned_classpath(build.ADVANCE_ARGS), out / 'classes')
    generated = assets.write_assets(out / 'bedrock')
    with zipfile.ZipFile(BASE) as old:
        pack_io = io.BytesIO()
        with zipfile.ZipFile(io.BytesIO(old.read('resource-packs/advance-magic-bedrock.mcpack'))) as previous, zipfile.ZipFile(pack_io, 'w') as pack:
            manifest = json.loads(previous.read('manifest.json'))
            manifest['header']['version'] = PACK_VERSION
            for module in manifest['modules']:
                module['version'] = PACK_VERSION
            for entry in previous.infolist():
                if entry.filename != 'manifest.json' and not entry.filename.startswith(RIG):
                    pack.writestr(copy.copy(entry), previous.read(entry.filename))
            pack.writestr('manifest.json', json.dumps(manifest, separators=(',', ':')), compress_type=zipfile.ZIP_DEFLATED)
            for path in sorted((out / 'bedrock').rglob('*')):
                if path.is_file():
                    pack.write(path, path.relative_to(out / 'bedrock').as_posix(), compress_type=zipfile.ZIP_DEFLATED)
        bedrock = pack_io.getvalue()
        (out / 'advance-magic-bedrock.mcpack').write_bytes(bedrock)
        pack_hashes = json.loads(old.read('resource-packs/pack-hashes.json'))
        pack_hashes['advance-magic-bedrock.mcpack'] = hashlib.sha1(bedrock).hexdigest()
        hashes_bytes = (json.dumps(pack_hashes, indent=2) + '\n').encode()
        resources = {'plugin.yml': (ROOT / 'advance-magic/src/main/resources/plugin.yml').read_bytes(),
                     'resource-packs/advance-magic-bedrock.mcpack': bedrock, 'resource-packs/pack-hashes.json': hashes_bytes}
        if ('version: ' + VERSION).encode() not in resources['plugin.yml']:
            raise RuntimeError('plugin.yml is not ' + VERSION)
        replaced = lambda name: any(name == p + '.class' or name.startswith(p + '$') for p in OWNED)
        jar = out / 'advance-magic.jar'
        with zipfile.ZipFile(jar, 'w') as release:
            for entry in old.infolist():
                if entry.filename not in resources and not replaced(entry.filename):
                    release.writestr(copy.copy(entry), old.read(entry.filename))
            for path in sorted((out / 'classes').rglob('*.class')):
                name = path.relative_to(out / 'classes').as_posix()
                if not replaced(name):
                    raise RuntimeError('Unexpected compiled class: ' + name)
                release.write(path, name, compress_type=zipfile.ZIP_DEFLATED)
            for name, data in resources.items():
                release.writestr(name, data, compress_type=zipfile.ZIP_DEFLATED)
        with zipfile.ZipFile(jar) as release:
            changed = sorted(n for n in release.namelist() if n not in old.namelist() or release.read(n) != old.read(n))
            if not all(n in resources or replaced(n) for n in changed):
                raise RuntimeError('Unrelated entry changed: ' + ', '.join(changed))
            if release.read('resource-packs/advance-magic-java.zip') != old.read('resource-packs/advance-magic-java.zip'):
                raise RuntimeError('Java pack changed')
            if release.testzip() is not None or len(release.namelist()) != len(set(release.namelist())):
                raise RuntimeError('Invalid release archive')
    build.check_jar(jar)
    digest = sha(jar.read_bytes())
    if args.promote:
        for target, data in ((ROOT / 'advance-magic/dist/advance-magic-bedrock.mcpack', bedrock), (ROOT / 'advance-magic/dist/pack-hashes.json', hashes_bytes)):
            staged = target.with_name(target.name + '.staged')
            staged.write_bytes(data)
            staged.replace(target)
        for target in (ROOT / 'dist/advance-magic.jar', ROOT / 'dist' / ('advance-magic-' + VERSION + '.jar')):
            staged = target.with_name(target.name + '.staged')
            shutil.copy2(jar, staged)
            staged.replace(target)
    report = {'version': VERSION, 'sha256': digest, 'baseline_sha256': BASE_SHA, 'changed_entries': changed,
              'bedrock_pack_version': PACK_VERSION, 'bedrock_pack_sha1': hashlib.sha1(bedrock).hexdigest(),
              'translucent_materials': generated['translucent'], 'face_shade': generated['face_shade'],
              'java_pack_unchanged': True, 'promoted': args.promote}
    (out / 'build-manifest.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps({k: v for k, v in report.items() if k != 'changed_entries'}, indent=2))


if __name__ == '__main__':
    main()
