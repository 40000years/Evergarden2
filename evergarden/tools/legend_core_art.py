"""Core of Legend: the six sanctuary orbs fused into one prismatic relic.

The painted Cocytus trophy supplies the frame silhouette and lighting so the
new core stays in the same hand-painted collection. Its glass is replaced by a
procedural six-armed nebula in the six boss colours around a faceted star, the
silver trim becomes polished gold and the frame gems turn prismatic.
Requires Pillow and NumPy. Deterministic: the same seed renders the same file.
"""
import math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'art/boss-orbs'
SOURCE = ART / 'originals/cocytus.png'
# Hue order around the wheel: Acheron, Phlegethon, Styx, Banshee, Cocytus, Malefeasance.
BOSS = np.array([(239, 53, 91), (255, 148, 31), (255, 219, 105), (111, 255, 196), (114, 228, 255), (185, 90, 255)], np.float32) / 255
SEED = 20261021


def smooth(edge0, edge1, x):
    t = np.clip((x - edge0) / (edge1 - edge0), 0, 1)
    return t * t * (3 - 2 * t)


def value_noise(shape, cells, rng):
    h, w = shape
    grid = rng.random((cells + 2, cells + 2)).astype(np.float32)
    ys = np.linspace(0, cells, h, endpoint=False)[:, None]
    xs = np.linspace(0, cells, w, endpoint=False)[None, :]
    y0, x0 = ys.astype(int), xs.astype(int)
    fy, fx = smooth(0, 1, ys - y0), smooth(0, 1, xs - x0)
    a, b = grid[y0, x0], grid[y0, x0 + 1]
    c, d = grid[y0 + 1, x0], grid[y0 + 1, x0 + 1]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def fbm(shape, rng, base=4, octaves=5):
    total, amp, norm = np.zeros(shape, np.float32), 1.0, 0.0
    for octave in range(octaves):
        total += amp * value_noise(shape, base * 2 ** octave, rng)
        norm += amp; amp *= .5
    return total / norm


def reconstruct(seed, mask):
    """Grow seed through 4-connected mask pixels until stable (geodesic dilation)."""
    region = seed & mask
    while True:
        grown = region.copy()
        grown[1:] |= region[:-1]; grown[:-1] |= region[1:]
        grown[:, 1:] |= region[:, :-1]; grown[:, :-1] |= region[:, 1:]
        grown &= mask
        if (grown == region).all():
            return region
        region = grown


def palette(phase):
    """Continuous colour around the six boss hues; phase in turns."""
    t = (phase % 1) * 6
    i = np.floor(t).astype(int) % 6
    f = smooth(0, 1, t - np.floor(t))[..., None]
    return BOSS[i] * (1 - f) + BOSS[(i + 1) % 6] * f


def star_layer(size, cx, cy, r):
    """Faceted eight-point star: long cardinal points, short diagonals."""
    layer = Image.new('RGBA', size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    for k in range(8):
        angle = k * math.pi / 4 - math.pi / 2
        length = r * (.64 if k % 2 == 0 else .36)
        half = r * (.11 if k % 2 == 0 else .075)
        tip = (cx + math.cos(angle) * length, cy + math.sin(angle) * length)
        left = (cx + math.cos(angle - math.pi / 2) * half, cy + math.sin(angle - math.pi / 2) * half)
        right = (cx + math.cos(angle + math.pi / 2) * half, cy + math.sin(angle + math.pi / 2) * half)
        light = (255, 252, 236, 255) if k % 2 == 0 else (255, 240, 205, 235)
        shade = (232, 214, 255, 255) if k % 2 == 0 else (214, 236, 255, 225)
        draw.polygon([(cx, cy), left, tip], fill=light)
        draw.polygon([(cx, cy), tip, right], fill=shade)
    return layer


def shard_layer(size, cx, cy, r, rng):
    """Six small crystal shards, one per sanctuary, orbiting the star."""
    layer = Image.new('RGBA', size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    for k in range(6):
        angle = k * math.tau / 6 + math.pi / 6 + rng.uniform(-.12, .12)
        dist = r * rng.uniform(.6, .68)
        x, y = cx + math.cos(angle) * dist, cy + math.sin(angle) * dist
        tilt = angle + math.pi / 2 + rng.uniform(-.4, .4)
        long_, wide = r * .1, r * .036
        ax, ay = math.cos(tilt), math.sin(tilt)
        top, bottom = (x + ax * long_, y + ay * long_), (x - ax * long_ * .8, y - ay * long_ * .8)
        side_a, side_b = (x - ay * wide, y + ax * wide), (x + ay * wide, y - ax * wide)
        base = BOSS[[0, 1, 2, 3, 4, 5][k]] * 255
        bright = tuple(int(min(255, c * .55 + 140)) for c in base) + (255,)
        dark = tuple(int(c * .78) for c in base) + (255,)
        draw.polygon([top, side_a, bottom], fill=bright)
        draw.polygon([top, bottom, side_b], fill=dark)
        draw.line([top, bottom], fill=(255, 255, 255, 200), width=max(1, int(r * .006)))
    return layer


def sparkles(size, cx, cy, r, rng, count=46):
    layer = Image.new('RGBA', size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    for _ in range(count):
        rho = math.sqrt(rng.uniform(.04, .8)) * r
        angle = rng.uniform(0, math.tau)
        x, y = cx + math.cos(angle) * rho, cy + math.sin(angle) * rho
        arm = r * rng.uniform(.012, .032)
        tint = (255, 250, 228, 255) if rng.random() < .65 else (220, 236, 255, 255)
        draw.polygon([(x, y - arm), (x + arm * .28, y), (x, y + arm), (x - arm * .28, y)], fill=tint)
        draw.polygon([(x - arm, y), (x, y + arm * .28), (x + arm, y), (x, y - arm * .28)], fill=tint)
    return layer


def render():
    rng = np.random.default_rng(SEED)
    pyrng = np.random.default_rng(SEED + 1)
    source = Image.open(SOURCE).convert('RGBA')
    w, h = source.size
    base = np.asarray(source).astype(np.float32) / 255
    rgb, alpha = base[..., :3], base[..., 3]
    # Sphere fitted to the painted glass rim (top-arc least squares, 1.6 px residual).
    cx, cy, r = 626.76, 598.39, 509.64
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    u, v = (xx - cx) / r, (yy - cy) / r
    rho = np.sqrt(u * u + v * v)
    # Reach the anti-aliased rim too, or a thin Cocytus-blue outline survives.
    inside = smooth(1.03, 1.0, rho)
    z = np.sqrt(np.clip(1 - rho * rho, 0, 1))

    # --- classify the painted orb into glass, gold trim, obsidian and gems ---
    mx, mn = rgb.max(-1), rgb.min(-1)
    sat = np.where(mx > 1e-4, (mx - mn) / np.maximum(mx, 1e-4), 0)
    blue = smooth(.05, .16, rgb[..., 2] - np.maximum(rgb[..., 0], rgb[..., 1] * .92)) * smooth(.18, .32, sat)
    # Connectivity at half scale: metal touching the frame band is frame; blue
    # cut off from the open glass is a gem set into that frame.
    half = (w // 2, h // 2)
    small = lambda m: np.asarray(Image.fromarray((m * 255).astype(np.uint8)).resize(half, Image.Resampling.BILINEAR)) > 127
    solid = small((alpha > .5).astype(np.float32))
    # Trim and obsidian are neutral-to-warm; cool white glass highlights are not metal.
    lum0 = rgb @ np.array([.299, .587, .114], np.float32)
    neutral = ((rgb[..., 2] - rgb[..., 0] < .035) | (lum0 < .3)).astype(np.float32)
    blue_s = small(blue) & solid
    metal_s = ~small(blue) & small(neutral) & solid
    sy, sx = np.mgrid[0:half[1], 0:half[0]]
    glass_seed = blue_s & ((sx - cx / 2) ** 2 + (sy - cy / 2) ** 2 < (r / 2 * .55) ** 2)
    frame_seed = metal_s & (sy > (cy + r * .72) / 2)
    open_glass = reconstruct(glass_seed, blue_s)
    frame_s = reconstruct(frame_seed, metal_s)
    gem_s = blue_s & ~open_glass & (sy > cy / 2)
    big = lambda m: np.asarray(Image.fromarray((m * 255).astype(np.uint8)).resize((w, h), Image.Resampling.BILINEAR)).astype(np.float32) / 255
    frame, gem = big(frame_s) * (1 - blue) * neutral, big(gem_s) * blue
    # Glass content (white star lines, sparkles) surrounded by blue stays glass.
    glass = np.clip(inside * (1 - frame - gem), 0, 1)

    # --- prismatic nebula ---
    theta = np.arctan2(v, u)
    spiral = theta / math.tau - 1.15 * np.log(np.maximum(rho, 1e-3)) / math.tau * 1.6
    warp = fbm((h, w), rng, base=3, octaves=4)
    wisps = fbm((h, w), rng, base=5, octaves=5)
    phase = spiral * 6 + (warp - .5) * 1.4
    wave = np.cos(phase * math.tau) * .5 + .5
    arm = wave ** 2.1 * (.5 + .8 * wisps)
    hot = wave ** 10 * smooth(.95, .2, rho)
    hue = palette(spiral + (warp - .5) * .25)
    depth = .42 + .58 * z ** .6
    inner = np.clip(1 - rho, 0, 1)
    # Glowing arms over deep violet space: luminous but with real depth.
    nebula = np.array([.07, .04, .16], np.float32) * (.55 + 1.1 * inner)[..., None]
    nebula = nebula + hue * (.2 + .24 * inner * wisps)[..., None]
    nebula = nebula + hue * (arm * (1.35 - .4 * rho))[..., None]
    nebula = nebula + (hot * .42)[..., None]
    core = np.exp(-(rho / .12) ** 2) * 1.1 + np.exp(-(rho / .34) ** 2) * .38
    nebula = nebula * depth[..., None] + np.array([1, .95, .82], np.float32) * core[..., None]

    def paste(layer, blur, bloom=.9, opacity=1.0):
        nonlocal nebula
        if blur:
            glow = np.asarray(layer.filter(ImageFilter.GaussianBlur(blur))).astype(np.float32) / 255
            nebula = nebula + glow[..., :3] * glow[..., 3:] * bloom
        crisp = np.asarray(layer).astype(np.float32) / 255
        nebula = nebula * (1 - crisp[..., 3:] * opacity) + crisp[..., :3] * crisp[..., 3:] * opacity

    size = (w, h)
    paste(shard_layer(size, cx, cy, r, pyrng), 10, .85, .95)
    paste(star_layer(size, cx, cy, r), 40, .6)
    paste(sparkles(size, cx, cy, r, pyrng), 3)

    # --- glass lighting ---
    rim = (1 - z) ** 3
    # Coloured glass edge: the prism shows through the rim instead of greying it.
    nebula = nebula + (hue * .38 + .18) * (rim * smooth(.15, -.6, v))[..., None]
    nebula = nebula + np.array([1.0, .85, .55], np.float32) * (rim * .3 * smooth(.2, .8, v))[..., None]
    # Crisp window reflection upper-left, as on the other five trophies.
    hu, hv = u + .45, v + .6
    ca, sa = math.cos(-.65), math.sin(-.65)
    eu, ev = (hu * ca - hv * sa) / .2, (hu * sa + hv * ca) / .06
    spec = smooth(1, .85, np.sqrt(eu * eu + ev * ev))
    nebula = nebula * (1 - spec[..., None] * .9) + spec[..., None] * .9
    dot = smooth(.05, .03, np.sqrt((u + .66) ** 2 + (v + .34) ** 2))
    nebula = nebula * (1 - dot[..., None] * .9) + dot[..., None] * .9
    arc = smooth(.03, 0, np.abs(rho - .925)) * smooth(-.1, -.8, u + v * .45)
    nebula = nebula + arc[..., None] * .5
    nebula = np.clip(nebula, 0, 1)

    # --- frame: silver trim -> polished gold, obsidian keeps a warm violet depth ---
    lum = rgb @ np.array([.299, .587, .114], np.float32)
    trim = smooth(.3, .5, lum)
    gold = np.stack([np.clip(lum * 1.35 + .12, 0, 1), np.clip(lum * 1.08 + .02, 0, 1), np.clip(lum * .55 - .04, 0, 1)], -1)
    gold = gold + smooth(.75, .95, lum)[..., None] * np.array([.1, .12, .2], np.float32)
    obsidian = rgb * np.array([1.05, .92, 1.12], np.float32) * .95
    framed = obsidian * (1 - trim[..., None]) + gold * trim[..., None]

    # --- gems: keep painted facets, shift each gem through the prism ---
    gem_hue = palette((xx - cx) / (r * 2.6) + (yy - cy) / (r * 5) + .08)
    facet = smooth(.15, .95, lum)[..., None]
    prism = gem_hue * (.35 + .9 * facet) + facet ** 3 * .45
    # The keystone gem under the star is a white diamond with prismatic edges.
    keystone = (smooth(r * .16, r * .1, np.abs(xx - cx)) * smooth(cy + r * .45, cy + r * .55, yy))[..., None]
    diamond = np.array([.96, .95, 1.0], np.float32) * (.68 + .45 * facet) + gem_hue * .3 * (1 - facet)
    prism = np.clip(prism * (1 - keystone) + diamond * keystone, 0, 1)

    out = rgb.copy()
    out = out * (1 - frame[..., None]) + framed * frame[..., None]
    out = out * (1 - gem[..., None]) + prism * gem[..., None]
    out = out * (1 - glass[..., None]) + nebula * glass[..., None]
    result = np.dstack([np.clip(out, 0, 1), alpha])
    return Image.fromarray((result * 255 + .5).astype(np.uint8), 'RGBA')


def main(out=None):
    image = render()
    full = Path(out) / 'legend-render.png' if out else ART / 'legend-render.png'
    texture = Path(out) / 'legend.png' if out else ART / 'textures/legend.png'
    texture.parent.mkdir(parents=True, exist_ok=True)
    image.save(full)
    image.resize((256, 256), Image.Resampling.LANCZOS).save(texture)
    print('Core of Legend:', texture)


if __name__ == '__main__':
    import sys
    main(sys.argv[1] if len(sys.argv) > 1 else None)
