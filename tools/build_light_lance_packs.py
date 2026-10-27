"""Slim the Advance Magic resource packs for the Heaven's Descent beam cannon (Bedrock 2.31.0).

* Removes art nothing uses any more: the prismatic light-rain seal, halo, beams and motes
  (8.8 MB per pack), the painted Fire meteor (replaced by 3D meteors in 1.28.0) and the
  duplicate Ancient Judge hand (the Earth hand is retired; Evergarden ships its own copy).
* Adds JSON only for the beam cannon: XY/flat planes of the existing gold judgment seals and a
  longer-lived ray of the existing solar ray texture. No new image is added.
* Palette-compresses every PNG with libimagequant, keeping a file only if its alpha-weighted
  colour RMSE is at most 4/255 and mean alpha error at most 2/255 (otherwise lossless only).

Needs the imagequant module (pip install imagequant).

    python tools/build_light_lance_packs.py [--dry-run]
"""
import argparse
import hashlib
import io
import json
import os
import re
import shutil
import zipfile
from pathlib import Path

import imagequant
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / 'advance-magic/dist'
OUT = ROOT / 'output/light-lance-20261025'
BASE_SHA1 = {
    'advance-magic-java.zip': '09388e507a655b64c7699a4775b5d40d8e372d99',
    'advance-magic-bedrock.mcpack': '377062402d63380635332f221c3d1014777bd54d',
}
VERSION = [2, 31, 0]
RETIRED = re.compile(r'(elemental_legendary_light_[a-z_]+|elemental_legendary_fire_meteor(_flat)?)(\.particle)?\.(png|json)$'
                     r'|^assets/voidscape/'
                     r'|^(attachables|models/entity)/judge_(left|right)(_light)?(\.geo)?\.json$'
                     r'|^render_controllers/judge\.json$|^textures/items/judge_atlas\.png$')
MAX_RMSE, MAX_ALPHA = 4.0, 2.0
# Model atlases and icons that checks require to be pixel-identical across Java and Bedrock (whose
# copies differ in layout) get lossless compression only.
# Shipped copies that checks pin byte-for-byte to their authored source art stay untouched.
EXACT_SOURCES = (ROOT / 'advance-magic/art/legendary-weapons',)
LOSSLESS_ONLY = re.compile(r'staff|legendary_weapons|/entity/|armor|helm|pack\.png$|pack_icon\.png$')

GOLD = {  # model -> (texture, kind)
    'judgment_gold_seal': ('judgment_seal_0', 'plane'),
    'judgment_gold_halo': ('judgment_seal_1', 'plane'),
    'judgment_gold_ray': ('solar_ray', 'ray'),
}


def java_entries():
    out = {}
    for model, (texture, kind) in GOLD.items():
        out[f'assets/advance_magic/items/{model}.json'] = {'model': {'type': 'minecraft:model', 'model': f'advance_magic:effect/{model}'}}
        if kind == 'plane':
            faces = {side: {'texture': '#line', 'uv': [0, 0, 16, 16]} for side in ('north', 'south')}
            element = {'from': [0, 0, 7.99], 'to': [16, 16, 8.01], 'shade': False, 'faces': faces}
        else:
            faces = {side: {'texture': '#line', 'uv': [0, 0, 16, 16]} for side in ('north', 'south', 'east', 'west')}
            element = {'from': [0, 0, 0], 'to': [16, 16, 16], 'shade': False, 'faces': faces}
        out[f'assets/advance_magic/models/effect/{model}.json'] = {
            'ambientocclusion': False, 'textures': {'line': f'advance_magic:effect/{texture}'}, 'elements': [element]}
    return out


def bedrock_particle(model, texture, size, facing, lifetime, spin):
    appearance = {'size': ['variable.line_width * 0.5', 'variable.line_height * 0.5'], 'facing_camera_mode': facing,
                  'uv': {'texture_width': size, 'texture_height': size, 'uv': [0, 0], 'uv_size': [size, size]}}
    if facing != 'emitter_transform_xz':
        appearance['direction'] = {'mode': 'custom', 'custom_direction': ['variable.line_normal_x', 'variable.line_normal_y', 'variable.line_normal_z']}
    return {'format_version': '1.10.0', 'particle_effect': {
        'description': {'identifier': f'advance_magic:{model}', 'basic_render_parameters': {'material': 'particles_blend', 'texture': f'textures/particle/{texture}'}},
        'components': {
            'minecraft:emitter_lifetime_once': {'active_time': 0.01},
            'minecraft:emitter_rate_instant': {'num_particles': 1},
            'minecraft:emitter_shape_point': {'offset': ['variable.line_offset_x', 'variable.line_offset_y', 'variable.line_offset_z'], 'direction': [0, 0, 0]},
            # Redrawn every 0.1 s; a little overlap keeps the beam steady without doubling the seals.
            'minecraft:particle_lifetime_expression': {'max_lifetime': lifetime},
            'minecraft:particle_initial_speed': 0,
            'minecraft:particle_initial_spin': {'rotation': 'variable.line_rotation' if spin else 0, 'rotation_rate': 0},
            'minecraft:particle_motion_dynamic': {},
            'minecraft:particle_appearance_billboard': appearance}}}


def bedrock_entries():
    out = {}
    for model, (texture, kind) in GOLD.items():
        if kind == 'plane':
            out[f'particles/{model}.particle.json'] = bedrock_particle(model, texture, 1024, 'direction_z', .15, True)
            out[f'particles/{model}_flat.particle.json'] = bedrock_particle(model + '_flat', texture, 1024, 'emitter_transform_xz', .15, True)
        else:
            out[f'particles/{model}.particle.json'] = bedrock_particle(model, texture, 256, 'direction_y', .15, False)
    return out


_cache = {}
_exact = None


def exact_sources():
    global _exact
    if _exact is None:
        _exact = {hashlib.sha1(p.read_bytes()).hexdigest() for root in EXACT_SOURCES for p in root.rglob('*.png')}
    return _exact


def compress(name, data):
    """Return (bytes, method, rmse, alpha_error) for one PNG, never larger than the original."""
    key = (hashlib.sha1(data).hexdigest(), bool(LOSSLESS_ONLY.search(name)))
    if key in _cache:
        return _cache[key]
    if key[0] in exact_sources():
        _cache[key] = (data, 'kept', 0.0, 0.0)
        return _cache[key]
    image = Image.open(io.BytesIO(data))
    rgba = image.convert('RGBA')
    best, method, rmse, alpha = data, 'kept', 0.0, 0.0
    lossless = io.BytesIO()
    rgba.save(lossless, 'PNG', optimize=True)
    if lossless.tell() < len(best):
        best, method = lossless.getvalue(), 'lossless'
    try:
        quantized = imagequant.quantize_pil_image(rgba, dithering_level=1.0, max_colors=256, min_quality=0, max_quality=95)
        a = np.asarray(rgba).astype(np.float64)
        c = np.asarray(quantized.convert('RGBA')).astype(np.float64)
        weight = a[..., 3:] / 255
        q_rmse = float(np.sqrt((((a[..., :3] - c[..., :3]) * weight) ** 2).mean()))
        q_alpha = float(np.abs(a[..., 3] - c[..., 3]).mean())
        buffer = io.BytesIO()
        quantized.save(buffer, 'PNG', optimize=True)
        if not LOSSLESS_ONLY.search(name) and q_rmse <= MAX_RMSE and q_alpha <= MAX_ALPHA and buffer.tell() < len(best):
            best, method, rmse, alpha = buffer.getvalue(), 'palette', q_rmse, q_alpha
    except RuntimeError:
        pass
    _cache[key] = (best, method, rmse, alpha)
    return _cache[key]


def source(name):
    """The 2.30.0 originals: the backup once this build has replaced advance-magic/dist."""
    backup = OUT / 'before/advance-magic/dist' / name
    return backup if backup.exists() else DIST / name


def build(filename, report):
    path = source(filename)
    raw = path.read_bytes()
    if hashlib.sha1(raw).hexdigest() != BASE_SHA1[filename]:
        raise RuntimeError(f'{filename} is not the 2.30.0 pack this build expects')
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        old = {i.filename: z.read(i.filename) for i in z.infolist() if not i.is_dir()}
    bedrock = filename.endswith('.mcpack')
    removed = sorted(n for n in old if RETIRED.search(n))
    values = {n: d for n, d in old.items() if n not in removed}
    added = bedrock_entries() if bedrock else java_entries()
    for name, value in added.items():
        if name in values:
            raise RuntimeError('Refusing to overwrite ' + name)
        values[name] = (json.dumps(value, separators=(',', ':')) + '\n').encode()
    if bedrock:
        manifest = json.loads(values['manifest.json'])
        manifest['header']['version'] = VERSION
        for module in manifest['modules']:
            module['version'] = VERSION
        values['manifest.json'] = (json.dumps(manifest, ensure_ascii=False, separators=(',', ':')) + '\n').encode()
        textures = json.loads(values['textures/item_texture.json'])
        textures['texture_data'] = {k: v for k, v in textures['texture_data'].items() if not k.startswith('voidscape.judge_')}
        values['textures/item_texture.json'] = (json.dumps(textures, ensure_ascii=False, separators=(',', ':')) + '\n').encode()
    methods = {'palette': 0, 'lossless': 0, 'kept': 0}
    worst = 0.0
    for name in sorted(values):
        if name.endswith('.png'):
            data, method, rmse, _ = compress(name, values[name])
            values[name] = data
            methods[method] += 1
            worst = max(worst, rmse)
    validate(values, bedrock)
    staged = OUT / 'packs' / filename
    staged.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(staged, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name in sorted(values):
            info = zipfile.ZipInfo(name, date_time=(2026, 10, 25, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, values[name], compresslevel=9)
    with zipfile.ZipFile(staged) as z:
        if z.testzip() is not None or set(z.namelist()) != set(values):
            raise RuntimeError('Invalid staged pack ' + filename)
    report[filename] = {'before_bytes': len(raw), 'after_bytes': staged.stat().st_size, 'removed': removed,
                        'added': sorted(added), 'png_methods': methods, 'worst_palette_rmse': round(worst, 3)}
    return staged


def validate(values, bedrock):
    for name, data in values.items():
        if name.endswith('.json'):
            json.loads(data)
    if bedrock:
        for name, data in values.items():
            if name.startswith('particles/'):
                texture = json.loads(data)['particle_effect']['description']['basic_render_parameters']['texture']
                if texture + '.png' not in values:
                    raise RuntimeError(f'{name} needs missing {texture}.png')
            if name.startswith('attachables/') and 'judge' in name:
                raise RuntimeError('Judge attachable left behind: ' + name)
        return
    for name, data in values.items():
        if name.startswith('assets/advance_magic/items/'):
            model = json.loads(data).get('model', {}).get('model', '')
            if model.startswith('advance_magic:') and f'assets/advance_magic/models/{model.split(":", 1)[1]}.json' not in values:
                raise RuntimeError(f'{name} needs missing model {model}')
        if name.startswith('assets/advance_magic/models/effect/'):
            for texture in json.loads(data).get('textures', {}).values():
                if texture.startswith('advance_magic:') and f'assets/advance_magic/textures/{texture.split(":", 1)[1]}.png' not in values:
                    raise RuntimeError(f'{name} needs missing texture {texture}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dry-run', action='store_true', help='Stage packs under output/ without touching advance-magic/dist')
    args = parser.parse_args()
    report = {'bedrock_version': VERSION, 'thresholds': {'rmse': MAX_RMSE, 'alpha': MAX_ALPHA}}
    staged = [build(name, report) for name in BASE_SHA1]
    mappings = json.loads(source('geyser-mappings.json').read_text(encoding='utf8'))
    for item, entries in list(mappings['items'].items()):
        kept = [e for e in entries if not str(e.get('model', '')).startswith('voidscape:judge_')]
        if kept:
            mappings['items'][item] = kept
        else:
            del mappings['items'][item]
    mapping_bytes = (json.dumps(mappings, separators=(',', ':')) + '\n').encode()
    (OUT / 'packs/geyser-mappings.json').write_bytes(mapping_bytes)
    hashes = {p.name: hashlib.sha1(p.read_bytes()).hexdigest() for p in staged}
    report['sha1'] = hashes
    if not args.dry_run:
        backup = OUT / 'before/advance-magic/dist'
        backup.mkdir(parents=True, exist_ok=True)
        for name in [*BASE_SHA1, 'geyser-mappings.json', 'pack-hashes.json']:
            if not (backup / name).exists():
                shutil.copy2(DIST / name, backup / name)
        for path in staged:
            shutil.copy2(path, DIST / (path.name + '.staged'))
            os.replace(DIST / (path.name + '.staged'), DIST / path.name)
        (DIST / 'geyser-mappings.json').write_bytes(mapping_bytes)
        (DIST / 'pack-hashes.json').write_text(json.dumps(hashes, indent=2) + '\n', encoding='utf-8')
    report['copied_to_dist'] = not args.dry_run
    (OUT / 'pack-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps({k: ({kk: vv for kk, vv in v.items() if kk not in ('removed', 'added')} if isinstance(v, dict) and 'before_bytes' in v else v)
                      for k, v in report.items()}, indent=2))


if __name__ == '__main__':
    main()
