"""Pet egg icon: a pearl egg speckled in the six sanctuary colours with a gold band.

One texture serves every species; the item name and lore carry the species.
Rendered at 1024 px with simple sphere-like shading, then reduced to 256 px.
Requires Pillow and NumPy. Deterministic.
"""
import math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'art/pets/pet_egg.png'
BOSS = [(239, 53, 91), (255, 148, 31), (255, 219, 105), (111, 255, 196), (114, 228, 255), (185, 90, 255)]
SIZE = 1024


def smooth(edge0, edge1, x):
    t = np.clip((x - edge0) / (edge1 - edge0), 0, 1)
    return t * t * (3 - 2 * t)


def render():
    rng = np.random.default_rng(20261022)
    yy, xx = np.mgrid[0:SIZE, 0:SIZE].astype(np.float32)
    cx, cy, a, b = SIZE / 2, SIZE * .53, SIZE * .3, SIZE * .4
    v = (yy - cy) / b
    # Narrower crown, fuller base.
    half = a * np.sqrt(np.clip(1 - v * v, 0, 1)) * (1 + .16 * v)
    u = (xx - cx) / np.maximum(half, 1e-3)
    inside = (np.abs(u) <= 1) & (np.abs(v) <= 1)
    edge = smooth(1.0, .985, np.maximum(np.abs(u), np.abs(v) * .999))
    nz = np.sqrt(np.clip(1 - u * u * (1 - v * v) - v * v, 0, 1))
    normal = np.stack([u * np.sqrt(np.clip(1 - v * v, 0, 1)), v, nz], -1)
    normal /= np.maximum(np.linalg.norm(normal, axis=-1, keepdims=True), 1e-4)
    light = np.array([-.45, -.6, .66], np.float32); light /= np.linalg.norm(light)
    lambert = np.clip(normal @ light, 0, 1)
    # Pearl shell: cream crown to soft lavender base.
    top, bottom = np.array([252, 246, 232], np.float32) / 255, np.array([214, 196, 246], np.float32) / 255
    t = smooth(-1, 1, v)[..., None]
    shell = top * (1 - t) + bottom * t
    # Deeper shading with a lavender shadow side so the egg reads as round.
    shadow = np.array([150, 128, 200], np.float32) / 255
    lit = .3 + .8 * lambert[..., None]
    color = shell * lit + shadow * (1 - lit) * .35
    # Speckles in the six boss colours.
    spots = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0)); draw = ImageDraw.Draw(spots)
    for i in range(38):
        sv = rng.uniform(-.85, .85); su = rng.uniform(-.85, .85)
        w = a * math.sqrt(max(0, 1 - sv * sv)) * (1 + .16 * sv)
        x, y = cx + su * w, cy + sv * b
        r = rng.uniform(13, 30) * (1 - .45 * abs(su))
        c = BOSS[i % 6]
        draw.ellipse([x - r, y - r * .8, x + r, y + r * .8], fill=c + (int(rng.uniform(200, 245)),))
    spots = spots.filter(ImageFilter.GaussianBlur(2.2))
    sp = np.asarray(spots).astype(np.float32) / 255
    shade = (.55 + .55 * lambert)[..., None]
    color = color * (1 - sp[..., 3:]) + sp[..., :3] * shade * sp[..., 3:]
    # Gold band with engraved edges just below the middle.
    band = smooth(.07, .055, np.abs(v - .12))
    gold = np.array([1.0, .8, .32], np.float32) * (.45 + .75 * lambert[..., None]) + smooth(.75, 1, lambert)[..., None] * .35
    color = color * (1 - band[..., None]) + gold * band[..., None]
    rim = smooth(.008, 0, np.abs(np.abs(v - .12) - .07)) + smooth(.004, 0, np.abs(np.abs(v - .12) - .03)) * .6
    color = color * (1 - rim[..., None] * .55)
    # Prismatic gem set in the band.
    gx, gy = cx, cy + .12 * b
    gem = smooth(58, 52, np.abs(xx - gx) + np.abs(yy - gy) * 1.1)
    setting = smooth(70, 64, np.abs(xx - gx) + np.abs(yy - gy) * 1.1) - gem
    hue = np.arctan2(yy - gy, xx - gx) / math.tau + .5
    prism = np.stack([.6 + .4 * np.cos(math.tau * hue), .6 + .4 * np.cos(math.tau * (hue - 1 / 3)), .6 + .4 * np.cos(math.tau * (hue - 2 / 3))], -1)
    facet = np.where((xx - gx) * (yy - gy) > 0, 1.0, .72)[..., None]
    prism = (prism * .8 + .25) * facet
    color = color * (1 - setting[..., None]) + gold * setting[..., None] * .9
    color = color * (1 - gem[..., None]) + np.clip(prism, 0, 1) * gem[..., None]
    gem_spark = smooth(14, 4, np.sqrt((xx - gx + 18) ** 2 + (yy - gy + 18) ** 2))
    color = color + gem_spark[..., None] * .8
    # Crisp highlight and a soft rim light.
    hu, hv = u + .42, v + .5
    spec = smooth(1, .6, np.sqrt((hu / .2) ** 2 + (hv / .11) ** 2))
    color = color * (1 - spec[..., None] * .85) + spec[..., None] * .85
    rimlight = smooth(.75, 1, np.sqrt(u * u)) * smooth(-.2, .7, v) * .25
    color = color + rimlight[..., None] * np.array([.75, .7, 1.0], np.float32)
    # Thin dark outline keeps the silhouette readable at 16 px.
    outline = smooth(1.0, .955, np.maximum(np.abs(u), np.abs(v))) - edge
    color = color * (1 - np.clip(outline, 0, 1)[..., None] * .55)
    alpha = np.clip(smooth(1.02, .995, np.maximum(np.abs(u), np.abs(v))), 0, 1) * inside
    rgba = np.dstack([np.clip(color, 0, 1), alpha])
    image = Image.fromarray((rgba * 255 + .5).astype(np.uint8), 'RGBA')
    # Sparkles around the crown.
    glint = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0)); g = ImageDraw.Draw(glint)
    for x, y, r in ((cx + a * .9, cy - b * .78, 34), (cx - a * 1.05, cy - b * .3, 22), (cx + a * 1.12, cy + b * .25, 16)):
        g.polygon([(x, y - r), (x + r * .22, y), (x, y + r), (x - r * .22, y)], fill=(255, 250, 220, 255))
        g.polygon([(x - r, y), (x, y + r * .22), (x + r, y), (x, y - r * .22)], fill=(255, 250, 220, 255))
    image.alpha_composite(glint.filter(ImageFilter.GaussianBlur(1)))
    return image


def main(out=None):
    image = render().resize((256, 256), Image.Resampling.LANCZOS)
    target = Path(out) / 'pet_egg.png' if out else OUT
    target.parent.mkdir(parents=True, exist_ok=True)
    image.save(target)
    print('Pet egg:', target)


if __name__ == '__main__':
    import sys
    main(sys.argv[1] if len(sys.argv) > 1 else None)
