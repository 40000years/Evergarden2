"""Paint the three Dragon Nest scales (fire, frost, lightning) as 256 px item icons.

Same pipeline as the realm boss cores (realm_core_art.py): rendered at 1024 px from a shaded height field and
procedural noise, then reduced. Each icon is one great overlapping dragon scale, a shield-shaped plate with a raised
keel and rows of smaller scales, edged in the dragon's element: molten cracks for Vharok, rime facets for
Glaceryn, a forked lightning vein for Vaelthyr.

    python evergarden/tools/dragon_scale_art.py     # writes art/dragon-scales/textures/*.png and preview.png
"""
from pathlib import Path
import math
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from realm_core_art import S, X, Y, Canvas, blur, noise, poly, ramp, shade, stroke, bezier  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'art/dragon-scales'
IDS = ('fire', 'ice', 'storm')


def plate_outline(cx=0.0, cy=0.0, w=0.66, h=0.86, tilt=-0.18):
    """A dragon scale: broad rounded crown, curved flanks closing to a point."""
    pts = []
    for i in range(0, 181, 4):  # crown: upper half ellipse
        a = math.radians(180 + i)
        pts.append((math.cos(a) * w, -0.15 + math.sin(a) * h * 0.62))
    pts += bezier((w, -0.15), (w * 0.98, 0.32), (w * 0.42, 0.62), (0.0, h))[1:]
    pts += bezier((0.0, h), (-w * 0.42, 0.62), (-w * 0.98, 0.32), (-w, -0.15))[1:]
    c, s = math.cos(tilt), math.sin(tilt)
    return [(cx + px * c - py * s, cy + px * s + py * c) for px, py in pts]


def local(tilt=-0.18):
    c, s = math.cos(-tilt), math.sin(-tilt)
    return X * c - Y * s, X * s + Y * c


def scale_icon(kind):
    tilt = -0.18
    cv = Canvas()
    mask = poly(plate_outline(tilt=tilt))
    lx, ly = local(tilt)
    # Height: domed plate, raised keel down the middle, rows of small overlapping scales.
    dome = blur(mask, 60)
    keel = np.exp(-(lx / 0.07) ** 2) * np.clip((ly + 0.7) / 1.4, 0, 1) * mask
    rows = 0.0 * dome
    for row in range(7):
        y0 = -0.6 + row * 0.21
        for col in range(-4, 5):
            x0 = col * 0.19 + (0.095 if row % 2 else 0)
            d = np.hypot((lx - x0) / 0.12, (ly - y0) / 0.11)
            cup = np.clip(1 - d, 0, 1) * (ly > y0 - 0.02)
            rows = np.maximum(rows, cup ** 0.6 * 0.5)
    grain = noise({'fire': 11, 'ice': 23, 'storm': 37}[kind], 10, 5)
    height = dome * 1.0 + keel * 0.55 + rows * 0.22 * mask + grain * 0.06
    lam, spec = shade(height, strength=5.0, spec_power=30, spec=0.55)
    # Light falls from the upper left; the lower point and the flanks sink into shadow.
    t = np.clip(1.05 - np.hypot(lx + 0.18, ly + 0.38) / 1.15 + (grain - 0.5) * 0.25, 0, 1)
    crevice = np.clip(rows / 0.5, 0, 1)
    if kind == 'fire':
        body = ramp(t, [(0, (26, 4, 4)), (0.35, (88, 10, 8)), (0.7, (158, 24, 12)), (1, (214, 70, 24))])
        rim, glow, vein = (255, 150, 60), (255, 110, 30), (255, 214, 120)
    elif kind == 'ice':
        body = ramp(t, [(0, (10, 28, 66)), (0.35, (36, 96, 168)), (0.7, (104, 184, 232)), (1, (206, 240, 255))])
        rim, glow, vein = (210, 246, 255), (120, 210, 255), (255, 255, 255)
    else:
        body = ramp(t, [(0, (12, 6, 34)), (0.35, (44, 18, 92)), (0.7, (88, 46, 164)), (1, (150, 112, 222))])
        rim, glow, vein = (255, 222, 120), (160, 120, 255), (255, 238, 150)
    lit = body * (0.34 + 0.8 * lam[..., None]) * (0.55 + 0.45 * crevice[..., None]) + spec[..., None] * 0.8
    cv.glow(mask, glow, 26, 0.55)
    cv.over(np.clip(lit, 0, 1), mask)
    # Element detail on the plate.
    if kind == 'fire':
        cracks = 0.0 * dome
        for seed, (a, b) in enumerate((((-0.3, -0.45), (0.1, 0.55)), ((0.32, -0.35), (-0.05, 0.4)), ((-0.45, 0.05), (0.2, 0.2)))):
            path = bezier(a, (a[0] + 0.25, a[1] + 0.3), (b[0] - 0.2, b[1] - 0.25), b)
            cracks = np.maximum(cracks, stroke(path, 0.028, 0.008))
        cracks *= mask
        cv.glow(cracks, glow, 18, 1.4)
        cv.over(np.asarray(vein, np.float32) / 255, cracks)
        sparks = (noise(5, 96, 1) > 0.93) * (1 - mask) * (np.hypot(X, Y) < 0.92)
        cv.glow(sparks.astype(np.float32), (255, 150, 60), 6, 1.2)
    elif kind == 'ice':
        facets = 0.0 * dome
        for ang in (-1.1, -0.4, 0.35, 1.05):
            path = [(0.0, -0.1), (math.sin(ang) * 0.62, -0.1 - math.cos(ang) * 0.62 * 0.9)]
            facets = np.maximum(facets, stroke(path, 0.016, 0.004))
        facets = np.maximum(facets, stroke([(0.0, -0.1), (0.0, 0.78)], 0.02, 0.005)) * mask
        cv.over(np.asarray(vein, np.float32) / 255, facets * 0.85)
        frost = np.clip((blur(mask, 6) - blur(mask, 22)) * 4, 0, 1) * (noise(9, 40, 3) > 0.45)
        cv.over(np.asarray(rim, np.float32) / 255, frost * 0.9)
        cv.glow(facets, glow, 14, 0.9)
    else:
        bolt = [(-0.05, -0.62), (0.12, -0.28), (-0.06, -0.12), (0.16, 0.18), (-0.02, 0.32), (0.08, 0.7)]
        fork = [(0.16, 0.18), (0.38, 0.3), (0.3, 0.46)]
        fork2 = [(-0.06, -0.12), (-0.32, 0.02), (-0.4, 0.22)]
        veins = (stroke(bolt, 0.05, 0.016) + stroke(fork, 0.03, 0.008) + stroke(fork2, 0.028, 0.008)).clip(0, 1) * mask
        cv.glow(veins, glow, 30, 1.6)
        cv.glow(veins, (255, 230, 140), 10, 1.2)
        cv.over(np.asarray(vein, np.float32) / 255, veins)
    edge = np.clip(mask - blur(mask, 5) * 1.0 + 0.0, 0, 1)
    cv.over(np.asarray(rim, np.float32) / 255, np.clip(edge * 3, 0, 1) * 0.85)
    cv.outline(mask, (14, 8, 10), 7)
    return cv.image()


def main():
    (OUT / 'textures').mkdir(parents=True, exist_ok=True)
    icons = []
    for kind in IDS:
        image = scale_icon(kind)
        image.save(OUT / 'textures' / f'dragon_scale_{kind}.png', optimize=True)
        icons.append(image)
        print(OUT / 'textures' / f'dragon_scale_{kind}.png')
    sheet = Image.new('RGBA', (256 * 3 + 40, 296), (28, 24, 36, 255))
    for i, icon in enumerate(icons):
        sheet.alpha_composite(icon, (10 + i * 266, 20))
    sheet.save(OUT / 'preview.png')


if __name__ == '__main__':
    main()
