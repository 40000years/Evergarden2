"""Add the three Dragon Nest dragons to the Evergarden Bedrock pack as native entities.

Until now the dragons were Java-only: the server registered voidscape:forge_dragon_<kind> with Geyser, but the
Bedrock pack never shipped those entities, so Bedrock players saw nothing. (The Nov 2 attempt, 3.69.0 in
output/dragon-bedrock-release, embedded the 4096 px atlases and weighed 50 MB; it never shipped.)

This exports each rig with the shared exporter (arkhaia_bedrock_assets.export_model, full-bright like the realm
bosses) including the twelve clips in RealmBosses.DRAGON_CLIPS order, and ships each atlas at 2048 px with a
256-colour palette and a hard alpha cut (about 0.4 MB instead of 10 MB). Geometry keeps its 4096 UV space; Bedrock
normalises UVs by the geometry's texture size, so the smaller atlas maps identically.

The pack keeps every other entry byte-identical, takes content version 3.70.0 (3.69.0 is skipped because the
abandoned 50 MB pack reached a test server with that number), and judge-design.json and pack-hashes.json follow.

    python tools/build_dragon_hunt_bedrock.py            # writes evergarden/dist (backs up the previous files)
    python tools/build_dragon_hunt_bedrock.py --check    # validate the current dist pack only
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import shutil
import sys
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'evergarden/tools'))
from arkhaia_bedrock_assets import export_model  # noqa: E402

DIST = ROOT / 'evergarden/dist'
PACK = DIST / 'evergarden-bedrock.mcpack'
HASHES = DIST / 'pack-hashes.json'
DESIGN = ROOT / 'evergarden/src/main/resources/judge-design.json'
DRAGONS = ROOT / 'evergarden/src/main/resources/modelengine/dragons'
MODELS = ('forge_dragon_fire', 'forge_dragon_storm', 'forge_dragon_ice')
CLIPS = ['idle', 'walk', 'fly', 'tail_whip', 'wing_flap', 'roar', 'takeoff', 'land', 'bite', 'claw', 'breath', 'cast']
SCALE = 5.5
TARGET = (3, 70, 0)
BASE_SHA1 = '0401d65a451f0ecc73301b30a81e33167215642d'
BACKUP = ROOT / 'output/dragon-hunt-build/bedrock-backup'
DATE = (2026, 11, 5, 0, 0, 0)


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, separators=(',', ':')) + '\n').encode()


def atlas(png: bytes) -> bytes:
    image = Image.open(io.BytesIO(png)).convert('RGBA')
    if image.size[0] > 2048:
        image = image.resize((2048, 2048 * image.size[1] // image.size[0]), Image.Resampling.LANCZOS)
    r, g, b, a = image.split()
    image = Image.merge('RGBA', (r, g, b, a.point(lambda v: 255 if v >= 128 else 0)))
    out = io.BytesIO()
    image.quantize(colors=256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE).save(out, 'PNG', optimize=True)
    return out.getvalue()


def entities() -> dict[str, bytes]:
    files = {}
    for model_id in MODELS:
        model = json.loads((DRAGONS / f'{model_id}.bbmodel').read_text(encoding='utf-8'))
        names = [clip['name'] for clip in model['animations']]
        if names != CLIPS:
            raise RuntimeError(f'{model_id} clips {names} != {CLIPS}; run evergarden/tools/dragon_attack_clips.py --write')
        native = export_model(model, model_id, SCALE, bright=True)
        geo_path = f'models/entity/{model_id}_native.geo.json'
        geo = json.loads(native[geo_path])
        geo['minecraft:geometry'][0]['description'].update(visible_bounds_width=96, visible_bounds_height=64, visible_bounds_offset=[0, 18, 0])
        native[geo_path] = json_bytes(geo)
        native[f'textures/entity/{model_id}_native.png'] = atlas(native[f'textures/entity/{model_id}_native.png'])
        files.update(native)
    return files


def check(pack: bytes) -> list[str]:
    problems = []
    with zipfile.ZipFile(io.BytesIO(pack)) as z:
        names = set(z.namelist())
        manifest = json.loads(z.read('manifest.json'))
        if tuple(manifest['header']['version']) < TARGET:
            problems.append(f"manifest version {manifest['header']['version']}")
        for model_id in MODELS:
            base = f'{model_id}_native'
            for path in (f'entity/{base}.entity.json', f'models/entity/{base}.geo.json', f'animations/{base}.animation.json',
                         f'animation_controllers/{base}.controller.json', f'render_controllers/{base}.render.json', f'textures/entity/{base}.png'):
                if path not in names:
                    problems.append('missing ' + path)
            if problems:
                continue
            entity = json.loads(z.read(f'entity/{base}.entity.json'))['minecraft:client_entity']['description']
            if entity['identifier'] != f'voidscape:{model_id}':
                problems.append(f'{model_id} identifier {entity["identifier"]}')
            controller = json.loads(z.read(f'animation_controllers/{base}.controller.json'))['animation_controllers']
            states = next(iter(controller.values()))['states']
            if list(states) != CLIPS:
                problems.append(f'{model_id} controller states {list(states)}')
            for index, clip in enumerate(CLIPS):
                wanted = {f"q.property('voidscape:clip') == {index}"}
                found = {cond for state, body in states.items() if state != clip for t in body['transitions'] for target, cond in t.items() if target == clip}
                if found != wanted:
                    problems.append(f'{model_id} transitions into {clip}: {found}')
            animations = json.loads(z.read(f'animations/{base}.animation.json'))['animations']
            for clip in CLIPS:
                if f'animation.voidscape.{model_id}.{clip}' not in animations:
                    problems.append(f'{model_id} lacks animation {clip}')
            geo = json.loads(z.read(f'models/entity/{base}.geo.json'))['minecraft:geometry'][0]
            if geo['description']['texture_width'] != 4096 or len(geo['bones']) < 60:
                problems.append(f'{model_id} geometry {geo["description"]}, {len(geo["bones"])} bones')
            texture = Image.open(io.BytesIO(z.read(f'textures/entity/{base}.png')))
            if texture.size != (2048, 2048):
                problems.append(f'{model_id} atlas {texture.size}')
        if len(pack) > 20_000_000:
            problems.append(f'pack is {len(pack)} bytes')
    return problems


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    current = PACK.read_bytes()
    if args.check:
        problems = check(current)
        print('PASS' if not problems else 'FAIL\n' + '\n'.join(problems))
        raise SystemExit(1 if problems else 0)
    if hashlib.sha1(current).hexdigest() != BASE_SHA1:
        raise RuntimeError('evergarden/dist Bedrock pack is not the expected 3.68.0 baseline; another pipeline changed it')
    BACKUP.mkdir(parents=True, exist_ok=True)
    for source in (PACK, HASHES, DESIGN):
        target = BACKUP / source.name
        if not target.exists():
            shutil.copy2(source, target)
    with zipfile.ZipFile(io.BytesIO(current)) as z:
        files = {n: z.read(n) for n in z.namelist() if not n.endswith('/')}
    added = entities()
    clash = [p for p in added if p in files]
    if clash:
        raise RuntimeError('Bedrock pack already carries dragon entries: ' + ', '.join(clash))
    files.update(added)
    manifest = json.loads(files['manifest.json'])
    old = manifest['header']['version']
    manifest['header']['version'] = list(TARGET)
    for module in manifest['modules']:
        module['version'] = list(TARGET)
    files['manifest.json'] = json_bytes(manifest)
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as out:
        for name, data in sorted(files.items()):
            info = zipfile.ZipInfo(name, DATE)
            info.compress_type = zipfile.ZIP_DEFLATED
            out.writestr(info, data)
    pack = buffer.getvalue()
    problems = check(pack)
    if problems:
        raise RuntimeError('Dragon Bedrock pack failed its checks:\n' + '\n'.join(problems))
    PACK.write_bytes(pack)
    hashes = json.loads(HASHES.read_text(encoding='utf8'))
    hashes['evergarden-bedrock.mcpack'] = hashlib.sha1(pack).hexdigest()
    HASHES.write_text(json.dumps(hashes, separators=(',', ':')) + '\n', encoding='utf8')
    version = '.'.join(map(str, TARGET))
    design = DESIGN.read_text(encoding='utf8')
    design, count = re.subn(r'("bedrock-version":\s*)"[\d.]+"', lambda m: m.group(1) + '"' + version + '"', design, count=1)
    if count != 1:
        raise RuntimeError('judge-design.json has no bedrock-version')
    DESIGN.write_text(design, encoding='utf8')
    sizes = {p: len(d) for p, d in added.items()}
    print(f'Bedrock pack {".".join(map(str, old))} -> {version}: {len(current)} -> {len(pack)} bytes, sha1 {hashes["evergarden-bedrock.mcpack"]}')
    for path, size in sorted(sizes.items()):
        print(f'  + {path} {size}')


if __name__ == '__main__':
    main()
