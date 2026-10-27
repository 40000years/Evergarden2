"""Preview: python prev.py OUTDIR phase mode [clip time]  (mode: head | body | clips | face)"""
import sys, json, math, os
sys.path.insert(0, 'evergarden/tools')
import numpy as np
import boss_render
from boss_render import Rig, NORMALS, FACE_QUADS, _raster
from boss_forge import rot
from PIL import Image, ImageFilter


MC = os.environ.get('MC_LIGHT')
L0 = np.array([.2, 1, -.7]); L0 /= np.linalg.norm(L0)
L1 = np.array([-.2, 1, .7]); L1 /= np.linalg.norm(L1)


def mc_light(n, facing):
    # Minecraft entity lighting with the boss facing `facing` degrees (0 = north): rotate the lights into model space.
    a = math.radians(facing)
    R = np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])
    l0, l1 = R.T @ L0, R.T @ L1
    return min(1.0, .4 + .6 * (max(0, n @ l0) + max(0, n @ l1)))


def render(rig, clip='none', time=0.0, yaw=0, elevation=4, size=520, span=50, center=(0, 20), background=(52, 50, 64), bloom=True, ss=2):
    """Flat-lit preview matching shade:false; elements named glow_* bloom."""
    m_all = rig.matrices(clip, time, None, time)
    big = size * ss
    az, el = math.radians(yaw), math.radians(elevation)
    eye = np.array([-math.sin(az) * math.cos(el), math.sin(el), -math.cos(az) * math.cos(el)])
    right = np.cross(-eye, [0, 1, 0]); right /= np.linalg.norm(right)
    up = np.cross(right, -eye)
    scale = big / span
    canvas = np.zeros((big, big, 3)); canvas[:] = background
    glow = np.zeros((big, big, 3)); depth = np.full((big, big), -1e9)
    tex = rig.texture; th, tw = tex.shape[:2]
    rw, rh = rig.model['resolution']['width'], rig.model['resolution']['height']
    for e, world, bone in rig.cuboids(m_all):
        lo, hi = np.array(e['from'], float), np.array(e['to'], float)
        r_el = rot(e.get('rotation', [0, 0, 0])); m = m_all[bone]
        pivot = np.array(e.get('origin', [0, 0, 0]), float)
        emissive = e['name'].startswith('glow')
        for face, data in e['faces'].items():
            if data.get('texture') is None: continue
            n = m[:3, :3] @ (r_el @ np.array(NORMALS[face], float))
            if n @ eye <= 1e-6: continue
            quad = np.array([[hi[i] if c else lo[i] for i, c in enumerate(v)] for v in FACE_QUADS[face]])
            quad = ((quad - pivot) @ r_el.T + pivot) @ m[:3, :3].T + m[:3, 3]
            px = (quad @ right - center[0]) * scale + big / 2
            py = big / 2 - (quad @ up - center[1]) * scale
            pz = quad @ eye
            u0, v0, u1, v1 = data['uv']
            us = np.array([u0, u0, u1, u1]) * tw / rw; vs = np.array([v0, v1, v1, v0]) * th / rh
            bright = mc_light(n, float(MC)) if MC is not None else 1.0
            for tri in ((0, 1, 2), (0, 2, 3)):
                _raster(canvas, glow, depth, px[list(tri)], py[list(tri)], pz[list(tri)], us[list(tri)], vs[list(tri)], tex, bright, emissive)
    image = Image.fromarray(np.clip(canvas, 0, 255).astype(np.uint8))
    if bloom:
        g = Image.fromarray(np.clip(glow, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(big / 120))
        image = Image.fromarray(np.clip(np.asarray(image, float) + np.asarray(g, float) * .45, 0, 255).astype(np.uint8))
    return image.resize((size, size), Image.Resampling.LANCZOS)


if __name__ == '__main__':
    out, phase, mode = sys.argv[1], int(sys.argv[2]), sys.argv[3]
    clip = sys.argv[4] if len(sys.argv) > 4 else 'none'
    t = float(sys.argv[5]) if len(sys.argv) > 5 else 0
    name = 'arkhaia_malefeasance' + ('' if phase == 1 else '_phase2')
    rig = Rig(json.load(open(f'{out}/{name}.bbmodel', encoding='utf8')))
    if mode == 'head':
        views = [(0, 2, 9, (0, 33.6)), (28, 2, 9, (0, 33.6)), (70, 2, 9, (0, 33.6)), (180, 2, 9, (0, 33.6)), (0, -18, 16, (0, 30)), (35, 10, 16, (0, 30))]
        size, cols = 440, 3
    elif mode == 'face':
        views = [(0, 0, 6.2, (0, 33.4)), (22, 0, 6.2, (0, 33.4))]
        size, cols = 640, 2
    elif mode == 'body':
        views = [(0, 4, 50, (0, 21)), (32, 6, 50, (0, 21)), (90, 4, 50, (0, 21)), (180, 6, 50, (0, 21)), (-35, -14, 50, (0, 22)), (0, 2, 18, (0, 30))]
        size, cols = 520, 3
    elif mode == 'clips':
        clips = [('idle', .6), ('hover', .5), ('cast_windup', .9), ('cast_active', .25), ('attack_windup', .7), ('attack_active', .2),
                 ('sky_windup', .9), ('sky_active', .25), ('portal_active', .25), ('stun', .7), ('walk', .25), ('rebirth', 1.0)]
        tiles = [render(rig, c, tt, 25, 6, 360, 52, (0, 21)) for c, tt in clips]
        sheet = Image.new('RGB', (360 * 6, 720))
        for i, im in enumerate(tiles): sheet.paste(im, ((i % 6) * 360, (i // 6) * 360))
        sheet.save(f'{out}/p{phase}_clips.png'); print('saved'); sys.exit()
    tiles = [render(rig, clip, t, yaw, el, size, span, center) for yaw, el, span, center in views]
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new('RGB', (size * cols, size * rows))
    for i, im in enumerate(tiles): sheet.paste(im, ((i % cols) * size, (i // cols) * size))
    sheet.save(f'{out}/p{phase}_{mode}.png'); print('saved', f'{out}/p{phase}_{mode}.png')
