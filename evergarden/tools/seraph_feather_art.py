"""Seraph Feather icons: a pearl-white feather tipped with gold, and the all-gold one worth ten.

Drawn barb by barb along a curved quill at 1024 px, lightly glowed, reduced to 256 px.
Requires Pillow and NumPy. Deterministic.
"""
import math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'art/seraph'
SIZE = 1024


def bezier(t, p0, p1, p2):
    a = (1 - t) ** 2; b = 2 * (1 - t) * t; c = t * t
    return (a * p0[0] + b * p1[0] + c * p2[0], a * p0[1] + b * p1[1] + c * p2[1])


def lerp(c0, c1, t):
    return tuple(int(c0[i] + (c1[i] - c0[i]) * t) for i in range(len(c0)))


def render(golden):
    rng = np.random.default_rng(20261022 + golden)
    img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    p0, p1, p2 = (250, 860), (430, 470), (800, 150)
    base = (255, 214, 120) if golden else (244, 246, 255)
    mid = (255, 190, 80) if golden else (226, 230, 250)
    tip = (255, 160, 40) if golden else (255, 204, 110)
    edge = (150, 96, 30) if golden else (196, 198, 222)
    steps = 340
    for side in (-1, 1):
        for i in range(steps):
            t = .1 + .88 * i / steps
            x, y = bezier(t, p0, p1, p2)
            dx, dy = bezier(min(1, t + .01), p0, p1, p2)
            tx, ty = dx - x, dy - y
            length = math.hypot(tx, ty) or 1
            tx, ty = tx / length, ty / length
            nx, ny = -ty * side, tx * side
            # Barbs sweep towards the tip; the vane is widest just below the middle.
            ang = math.radians(38)
            bx, by = nx * math.cos(ang) + tx * math.sin(ang), ny * math.cos(ang) + ty * math.sin(ang)
            width = (math.sin(math.pi * min(1, (t - .1) / .9)) ** .7) * (150 if side < 0 else 120)
            width *= 1 + rng.uniform(-.05, .05)
            if rng.random() < .025:
                continue  # a few natural splits in the vane
            k = (t - .1) / .88
            colour = lerp(base, mid, min(1, k * 1.6)) if k < .55 else lerp(mid, tip, (k - .55) / .45)
            shade = .86 + .14 * (i % 3 == 0)
            colour = tuple(int(c * shade) for c in colour)
            end = (x + bx * width, y + by * width)
            draw.line([(x, y), end], fill=edge + (255,), width=9)
            draw.line([(x, y), (x + bx * (width - 4), y + by * (width - 4))], fill=colour + (255,), width=6)
    # Quill.
    shaft = [bezier(t / 60, p0, p1, p2) for t in range(61)]
    quill = (205, 150, 70) if golden else (236, 214, 170)
    draw.line([(250 - 70, 860 + 95)] + shaft, fill=(120, 84, 40, 255), width=16, joint='curve')
    draw.line([(250 - 70, 860 + 95)] + shaft, fill=quill + (255,), width=9, joint='curve')
    # Soft glow behind, gold for both: these are the Seraph's own feathers.
    glow = img.filter(ImageFilter.GaussianBlur(26))
    g = np.asarray(glow).astype(np.float32)
    g[..., 0], g[..., 1], g[..., 2] = 255, 220, 140
    g[..., 3] *= .55
    halo = Image.fromarray(g.astype(np.uint8), 'RGBA')
    out = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    out.alpha_composite(halo); out.alpha_composite(img)
    sparkle = ImageDraw.Draw(out)
    for x, y, r in ((760, 330, 40), (300, 420, 26), (640, 560, 18)) if golden else ((780, 300, 34), (330, 460, 20)):
        sparkle.polygon([(x, y - r), (x + r * .22, y), (x, y + r), (x - r * .22, y)], fill=(255, 252, 230, 255))
        sparkle.polygon([(x - r, y), (x, y + r * .22), (x + r, y), (x, y - r * .22)], fill=(255, 252, 230, 255))
    return out.resize((256, 256), Image.Resampling.LANCZOS)


def main(out=None):
    folder = Path(out) if out else OUT
    folder.mkdir(parents=True, exist_ok=True)
    render(False).save(folder / 'seraph_feather.png')
    render(True).save(folder / 'seraph_feather_golden.png')
    print('Seraph feathers:', folder)


if __name__ == '__main__':
    import sys
    main(sys.argv[1] if len(sys.argv) > 1 else None)
