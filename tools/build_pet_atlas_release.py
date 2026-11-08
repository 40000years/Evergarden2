"""Evergarden e2.201: Java pet textures without their empty space, so the items atlas fits every GPU.

    python tools/build_pet_atlas_release.py             build the candidate over the released e2.200
    python tools/build_pet_atlas_release.py --promote   replace dist/evergarden.jar (after validation.json says PASS)

The Pet Atelier's 171 large pet textures were 1024x1024 with about five sixths left transparent. Minecraft stitches
every item texture into one atlas, which outgrew 16384x16384 (most AMD, Intel and laptop GPUs): those Java players
got FAILED_RELOAD for the boss and pet pack. tools/pet_native_repack.py moves every face's pixel rectangle, unchanged,
onto a 512x512 (or 1024x512) canvas and rewrites the UVs, which check_native_pet_models.py verifies face by face
against the blueprints. Changes against the e2.200 baseline:
- native-pets.zip: the repacked pack, identical to evergarden/src/main/resources/native-pets.zip;
- modelengine/pets/blueprint-textures.json: blueprints now restore their PNGs from the bundled Bedrock pack, which
  still holds those exact bytes (native-pets.zip no longer does);
- plugin.yml carries the new version.
No class changes. Every other entry, including the Bedrock and Java resource packs, stays byte-identical.
Nothing is published or deployed.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import struct
import sys
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
import pet_blueprint_slim as slim  # noqa: E402
import pet_native_repack as repack  # noqa: E402
from release_jar_checks import check_jar  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'output/pet-atlas-release'
VERSION = '3.0.1-e2.201-pet-atlas'
BASE_VERSION = '3.0.1-e2.200-slim-blueprints'
BASE_SHA256 = '3a510356a634ab18778d0126b0273b96281e0074287bc4d5146ca5cca81daf15'   # released, with pack-mirror.properties
DATE = (2026, 11, 8, 0, 0, 0)
NATIVE = ROOT / 'evergarden/src/main/resources/native-pets.zip'


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def version(data):
    text, count = re.subn(r'(?m)^version:[^\r\n]*', "version: '" + VERSION + "'", data.decode('utf8'))
    if count != 1:
        raise RuntimeError('Expected one plugin version')
    return text.encode('utf8')


def baseline():
    OUT.mkdir(parents=True, exist_ok=True)
    garden, jar = ROOT / 'dist/evergarden.jar', OUT / 'baseline-evergarden.jar'
    if not jar.exists():
        if sha(garden) != BASE_SHA256:
            raise RuntimeError('dist/evergarden.jar is not the released ' + BASE_VERSION)
        shutil.copy2(garden, jar)
    if sha(jar) != BASE_SHA256:
        raise RuntimeError('Baseline copy changed')
    return jar


def item_atlas_pixels(packs):
    """Texture area that lands in the Java items atlas (every textures/item, effect and armor PNG; top pack wins)."""
    sprites = {}
    for data in packs:
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            for name in archive.namelist():
                parts = name.split('/')
                if name.endswith('.png') and len(parts) > 3 and parts[0] == 'assets' and parts[2] == 'textures' \
                        and parts[3] in ('item', 'effect', 'armor'):
                    head = archive.read(name)[:24]
                    sprites[name] = struct.unpack('>II', head[16:24])
    return sum(w * h for w, h in sprites.values())


def candidate():
    jar = baseline()
    native = NATIVE.read_bytes()
    target = OUT / 'evergarden.jar'
    with zipfile.ZipFile(jar) as old:
        names = old.namelist()
        old_native = old.read('native-pets.zip')
        rebuilt, report = repack.repack_zip(old_native)
        if rebuilt != native:
            raise RuntimeError('The source native-pets.zip is not the repack of the released one')
        bedrock = old.read(slim.ARCHIVE)
        index = slim.reindex(old.read(slim.INDEX), bedrock)
        replaced = {'plugin.yml': version(old.read('plugin.yml')), 'native-pets.zip': native, slim.INDEX: index}
        # Read before writing: writestr(info, ...) moves the old archive's ZipInfo offsets to the new file.
        judge_parts = [old.read('resource-packs/judge-java.zip'), native, old.read('pet-portraits.zip')]
        before_parts = [judge_parts[0], old_native, judge_parts[2]]
        core = [old.read('resource-packs/evergarden-java.zip')]
        with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as new:
            for info in old.infolist():
                if info.filename not in replaced:
                    new.writestr(info, old.read(info.filename))
            for name, data in sorted(replaced.items()):
                new.writestr(zipfile.ZipInfo(name, DATE), data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    with zipfile.ZipFile(jar) as old, zipfile.ZipFile(target) as new:
        if new.testzip() or len(new.namelist()) != len(set(new.namelist())) or set(new.namelist()) != set(names):
            raise RuntimeError('Invalid candidate archive')
        preserved = [n for n in names if n not in replaced]
        if any(old.read(n) != new.read(n) for n in preserved):
            raise RuntimeError('Unrelated JAR entry changed')
        entries = {n: new.read(n) for n in new.namelist() if slim._blueprint(n) or n in (slim.INDEX, slim.ARCHIVE)}
        restored = slim.verify(entries)
    check_jar(target)
    with zipfile.ZipFile(ROOT / 'dist/advance-magic.jar') as magic:
        core.append(magic.read('resource-packs/advance-magic-java.zip'))
    atlas = {'before': item_atlas_pixels(core + before_parts), 'after': item_atlas_pixels(core + judge_parts),
             'limit_16384': 16384 * 16384,
             'note': 'Evergarden, Advance Magic and the bundled Judge pack; ModelEngine rig textures and vanilla items come on top'}
    result = {'version': VERSION, 'baseline_sha256': sha(jar), 'candidate_sha256': sha(target), 'native_sha256': sha(NATIVE),
              'magic_sha256': sha(ROOT / 'dist/advance-magic.jar'), 'baseline_bytes': jar.stat().st_size,
              'candidate_bytes': target.stat().st_size, 'restored_blueprints': restored, 'preserved_entries': len(preserved),
              'repack': {k: v for k, v in report.items() if k != 'detail'}, 'items_atlas_pixels': atlas, 'promoted': False}
    (OUT / 'build-report.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf8')
    print(json.dumps(result, indent=2))


def promote():
    report = json.loads((OUT / 'build-report.json').read_text(encoding='utf8'))
    receipt = json.loads((OUT / 'validation.json').read_text(encoding='utf8'))
    if receipt.get('result') != 'PASS':
        raise RuntimeError('Validation did not pass')
    if not sha(OUT / 'evergarden.jar') == report['candidate_sha256'] == receipt['garden_sha256']:
        raise RuntimeError('Candidate changed after validation')
    if sha(ROOT / 'dist/evergarden.jar') != report['baseline_sha256'] or sha(NATIVE) != report['native_sha256']:
        raise RuntimeError('dist/evergarden.jar or the source native pack changed after the build')
    for path in (ROOT / 'dist/evergarden.jar', ROOT / f'dist/evergarden-{VERSION}.jar'):
        staged = path.with_suffix('.jar.staged')
        shutil.copy2(OUT / 'evergarden.jar', staged)
        os.replace(staged, path)
    plugin = ROOT / 'evergarden/src/main/resources/plugin.yml'
    plugin.write_text(re.sub(r"(?m)^version:[^\r\n]*", "version: '" + VERSION + "'", plugin.read_text(encoding='utf8')),
                      encoding='utf8')
    report.update(promoted=True, validation=receipt)
    (OUT / 'build-report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print('Promoted ' + VERSION)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--promote', action='store_true')
    args = parser.parse_args()
    promote() if args.promote else candidate()


if __name__ == '__main__':
    main()
