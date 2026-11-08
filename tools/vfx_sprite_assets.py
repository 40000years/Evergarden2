"""Tintable VFX sprites shared by Java item displays and Bedrock particles (Advance Magic 1.37.0).

Six greyscale sprites, each drawn in nine tints (eight elements + heal):

* sigil_a / sigil_b: the painted Legendary wind and fire sigils, converted to luminance so one
  master tints into every element's magic circle;
* ring: a soft shockwave band; flare: a glowing core with star rays; slash: a tapering crescent;
  spark: a four-point twinkle.

Java: one plane model per sprite (faces carry tintindex 0) and one item definition per tint with a
constant tint. Bedrock: per tint, three particles that multiply the same texture by that colour:
<name> (plane facing a custom normal), <name>_flat (horizontal, native spin) and <name>_bb
(camera-facing billboard). Plane particles live 0.26 s. A 4-tick redraw at 20 TPS briefly overlaps
two copies for continuity; moving sprites refresh more often and share the native particle budget.

    python tools/vfx_sprite_assets.py --output DIR [--java-pack ZIP]
"""
import argparse
import io
import json
import math
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
TINTS = {
    'fire': (255, 138, 61), 'water': (79, 195, 255), 'ice': (190, 240, 255), 'wind': (128, 255, 208),
    'earth': (214, 176, 98), 'light': (255, 226, 122), 'dark': (176, 124, 255), 'lightning': (160, 215, 255),
    'heal': (120, 255, 170),
}
SPRITES = ('sigil_a', 'sigil_b', 'ring', 'flare', 'slash', 'spark')
SOURCES = {'sigil_a': 'elemental_legendary_wind_sigil', 'sigil_b': 'elemental_legendary_fire_sigil'}


def names():
    """Every item/particle base name: vfx_<sprite>_<tint>."""
    return [f'vfx_{sprite}_{tint}' for sprite in SPRITES for tint in TINTS]


def luminance(image, size):
    """A painted coloured sigil as white-to-grey line art with its own alpha, ready to tint."""
    image = image.convert('RGBA').resize((size, size), Image.Resampling.LANCZOS)
    pixels = np.asarray(image).astype(np.float64) / 255
    lum = pixels[..., :3] @ np.array([.2126, .7152, .0722])
    # Lift mid-tones so a multiplied tint keeps the painted highlights bright.
    lum = np.clip(lum / max(1e-6, np.percentile(lum[pixels[..., 3] > .1], 98)), 0, 1) ** .7
    out = np.zeros_like(pixels)
    out[..., :3] = lum[..., None]
    out[..., 3] = pixels[..., 3]
    return Image.fromarray(np.rint(out * 255).astype(np.uint8), 'RGBA')


def field(size):
    y, x = np.mgrid[0:size, 0:size].astype(np.float64)
    c = (size - 1) / 2
    return (x - c) / c, (y - c) / c


def to_image(intensity, alpha):
    intensity = np.clip(intensity, 0, 1)
    alpha = np.clip(alpha, 0, 1)
    out = np.zeros(intensity.shape + (4,))
    out[..., :3] = intensity[..., None]
    out[..., 3] = alpha
    return Image.fromarray(np.rint(out * 255).astype(np.uint8), 'RGBA')


def ring(size=256):
    x, y = field(size)
    r = np.hypot(x, y)
    band = np.exp(-((r - .78) / .09) ** 2)
    edge = np.exp(-((r - .84) / .025) ** 2)
    inner = np.exp(-((r - .62) / .02) ** 2) * .45
    alpha = np.clip(band * .85 + edge + inner, 0, 1) * (r < .99)
    return to_image(.75 + .25 * edge, alpha)


def flare(size=256):
    x, y = field(size)
    r = np.hypot(x, y)
    a = np.arctan2(y, x)
    core = np.exp(-(r / .2) ** 2)
    halo = np.exp(-(r / .55) ** 2) * .55
    rays = np.exp(-(np.abs(np.sin(2 * a)) * r / .025) ** 2) * np.clip(1 - r, 0, 1) ** 1.6
    diag = np.exp(-(np.abs(np.cos(2 * a)) * r / .02) ** 2) * np.clip(1 - r * 1.6, 0, 1) ** 2 * .7
    alpha = np.clip(core + halo + rays + diag, 0, 1) * (r < 1)
    return to_image(.8 + .2 * core, alpha)


def slash(size=256):
    x, y = field(size)
    # A crescent: the band between two offset circles, brightest on its leading (outer) edge.
    outer = np.hypot(x, y + .35)
    inner = np.hypot(x, y + .62)
    t = np.clip((x + .95) / 1.9, 0, 1)
    taper = np.sin(np.pi * t) ** .8
    band = (outer < 1.05) & (inner > 1.0) & (y < .7)
    lead = np.exp(-((outer - 1.02) / .035) ** 2)
    body = np.clip((outer - inner * .96 + .25), 0, 1)
    alpha = np.where(band, np.clip(.35 + lead * .9, 0, 1), 0) * taper
    alpha = np.asarray(Image.fromarray(np.rint(alpha * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.6))) / 255
    return to_image(.7 + .3 * lead, np.clip(alpha + body * 0, 0, 1))


def spark(size=128):
    x, y = field(size)
    r = np.hypot(x, y)
    cross = np.exp(-((np.abs(x) * np.abs(y)) / .004)) * np.clip(1 - r, 0, 1) ** 1.2
    core = np.exp(-(r / .16) ** 2)
    alpha = np.clip(cross + core, 0, 1) * (r < 1)
    return to_image(.85 + .15 * core, alpha)


def sprites(java_pack):
    with zipfile.ZipFile(java_pack) as java:
        art = {name: Image.open(io.BytesIO(java.read(f'assets/advance_magic/textures/effect/{source}.png')))
               for name, source in SOURCES.items()}
    return {'sigil_a': luminance(art['sigil_a'], 512), 'sigil_b': luminance(art['sigil_b'], 512),
            'ring': ring(), 'flare': flare(), 'slash': slash(), 'spark': spark()}


def encode(image):
    """PNG bytes, palette-compressed when the result stays within RMSE 4/255 and mean alpha error 2/255."""
    plain = io.BytesIO()
    image.save(plain, 'PNG', optimize=True)
    try:
        import imagequant
    except ImportError:
        return plain.getvalue()
    quantized = imagequant.quantize_pil_image(image, dithering_level=1.0, max_colors=256)
    original, back = np.asarray(image).astype(np.float64), np.asarray(quantized.convert('RGBA')).astype(np.float64)
    weight = original[..., 3:4] / 255
    rmse = math.sqrt((((back[..., :3] - original[..., :3]) * weight) ** 2).mean())
    if rmse > 4 or np.abs(back[..., 3] - original[..., 3]).mean() > 2:
        return plain.getvalue()
    packed = io.BytesIO()
    quantized.save(packed, 'PNG', optimize=True)
    return min(packed.getvalue(), plain.getvalue(), key=len)


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, separators=(',', ':')), encoding='utf8')


def particle(identifier, texture, size, tint, mode):
    uv = {'texture_width': size, 'texture_height': size, 'uv': [0, 0], 'uv_size': [size, size]}
    billboard = {'size': ['variable.line_width * 0.5', 'variable.line_height * 0.5'], 'uv': uv}
    spin = 0
    if mode == 'flat':
        billboard['facing_camera_mode'] = 'emitter_transform_xz'
        spin = 'variable.line_rotation_rate'
    elif mode == 'bb':
        billboard['facing_camera_mode'] = 'rotate_xyz'
    else:
        billboard['facing_camera_mode'] = 'direction_z'
        billboard['direction'] = {'mode': 'custom', 'custom_direction': ['variable.line_normal_x', 'variable.line_normal_y', 'variable.line_normal_z']}
    return {'format_version': '1.10.0', 'particle_effect': {
        'description': {'identifier': identifier, 'basic_render_parameters': {'material': 'particles_blend', 'texture': texture}},
        'components': {
            'minecraft:emitter_lifetime_once': {'active_time': 0.01},
            'minecraft:emitter_rate_instant': {'num_particles': 1},
            'minecraft:emitter_shape_point': {'offset': ['variable.line_offset_x', 'variable.line_offset_y', 'variable.line_offset_z'], 'direction': [0, 0, 0]},
            'minecraft:particle_lifetime_expression': {'max_lifetime': 0.26},
            'minecraft:particle_initial_speed': 0,
            'minecraft:particle_initial_spin': {'rotation': 'variable.line_rotation', 'rotation_rate': spin},
            'minecraft:particle_motion_dynamic': {},
            'minecraft:particle_appearance_billboard': billboard,
            'minecraft:particle_appearance_tinting': {'color': [round(c / 255, 4) for c in tint] + [1]}}}}


def write_assets(output, java_pack=None):
    """Writes java/ and bedrock/ trees under output; returns {pack path: bytes} per side."""
    output = Path(output)
    java_pack = java_pack or ROOT / 'advance-magic/dist/advance-magic-java.zip'
    art = sprites(java_pack)
    java, bedrock = {}, {}
    for sprite, image in art.items():
        png = encode(image)
        java[f'assets/advance_magic/textures/effect/vfx_{sprite}.png'] = png
        bedrock[f'textures/particle/vfx_{sprite}.png'] = png
        face = {'texture': '#art', 'uv': [0, 0, 16, 16], 'tintindex': 0}
        java[f'assets/advance_magic/models/effect/vfx_{sprite}.json'] = json.dumps({
            'ambientocclusion': False, 'textures': {'art': f'advance_magic:effect/vfx_{sprite}'},
            'elements': [{'from': [0, 0, 7.99], 'to': [16, 16, 8.01], 'shade': False,
                          'faces': {'north': face, 'south': face}}]}, separators=(',', ':')).encode()
        for tint, color in TINTS.items():
            name = f'vfx_{sprite}_{tint}'
            java[f'assets/advance_magic/items/{name}.json'] = json.dumps({'model': {
                'type': 'minecraft:model', 'model': f'advance_magic:effect/vfx_{sprite}',
                'tints': [{'type': 'minecraft:constant', 'value': (color[0] << 16) | (color[1] << 8) | color[2]}]}},
                separators=(',', ':')).encode()
            for mode, suffix in (('plane', ''), ('flat', '_flat'), ('bb', '_bb')):
                bedrock[f'particles/{name}{suffix}.particle.json'] = json.dumps(
                    particle(f'advance_magic:{name}{suffix}', f'textures/particle/vfx_{sprite}', image.width, color, mode),
                    separators=(',', ':')).encode()
    for side, files in (('java', java), ('bedrock', bedrock)):
        for path, data in files.items():
            target = output / side / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
    return java, bedrock


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--java-pack', type=Path)
    args = parser.parse_args()
    java, bedrock = write_assets(args.output, args.java_pack)
    print(json.dumps({'java_entries': len(java), 'bedrock_entries': len(bedrock),
                      'java_bytes': sum(map(len, java.values())), 'bedrock_bytes': sum(map(len, bedrock.values()))}, indent=2))
