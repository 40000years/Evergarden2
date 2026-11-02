"""Paint the four realm boss cores (Nether / End sanctuary trophies) as 256 px item icons.

Each icon is rendered at 1024 px from shaded height fields and procedural noise, then reduced, so it sits
beside the painted Soul Orbs and armor cores: a glowing heart in a themed frame on a transparent ground.

    python evergarden/tools/realm_core_art.py     # writes art/realm-cores/textures/*.png and preview.png
"""
from pathlib import Path
import math
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'art/realm-cores'
S = 1024
IDS = ('cinder_colossus', 'soul_hound', 'crystal_moth', 'unlit_sun')
LIGHT = np.array([-0.55, -0.65, 0.52], np.float32)
LIGHT /= np.linalg.norm(LIGHT)


# ---------------------------------------------------------------- primitives
def coords():
    y, x = np.mgrid[0:S, 0:S].astype(np.float32)
    return (x + 0.5) / S * 2 - 1, (y + 0.5) / S * 2 - 1


X, Y = coords()


def box(a, r):
    if r < 1:
        return a
    k = 2 * r + 1
    p = np.pad(a, ((r + 1, r), (0, 0)), mode='edge').cumsum(0)
    a = (p[k:] - p[:-k]) / k
    p = np.pad(a, ((0, 0), (r + 1, r)), mode='edge').cumsum(1)
    return (p[:, k:] - p[:, :-k]) / k


def blur(a, r):
    """Three box passes approximate a Gaussian of the given radius."""
    r = max(1, int(round(r / 1.7)))
    for _ in range(3):
        a = box(a, r)
    return a.astype(np.float32)


def noise(seed, base, octaves=5, gain=0.5):
    rng = np.random.default_rng(seed)
    total = np.zeros((S, S), np.float32)
    amp, norm = 1.0, 0.0
    for o in range(octaves):
        n = base * 2 ** o
        lattice = rng.random((n + 3, n + 3)).astype(np.float32)
        up = np.asarray(Image.fromarray(lattice, 'F').resize((S + S * 3 // n, S + S * 3 // n), Image.BICUBIC), np.float32)
        total += up[:S, :S] * amp
        norm += amp
        amp *= gain
    return total / norm


def poly(points, size=S):
    im = Image.new('L', (size, size), 0)
    ImageDraw.Draw(im).polygon([((px + 1) * S / 2, (py + 1) * S / 2) for px, py in points], fill=255)
    return np.asarray(im, np.float32) / 255


def ellipse(cx, cy, rx, ry):
    return np.clip(1 - (((X - cx) / rx) ** 2 + ((Y - cy) / ry) ** 2), 0, 1) ** 0


def disc(cx, cy, r, soft=0.004):
    d = np.hypot(X - cx, Y - cy)
    return np.clip((r - d) / soft, 0, 1)


def stroke(points, width0, width1=None, steps=200):
    """Filled ribbon along a polyline / curve, tapering from width0 to width1."""
    width1 = width0 if width1 is None else width1
    pts = np.array(points, np.float32)
    left, right = [], []
    for i in range(len(pts)):
        a, b = pts[max(0, i - 1)], pts[min(len(pts) - 1, i + 1)]
        t = b - a
        t /= max(1e-6, np.linalg.norm(t))
        n = np.array([-t[1], t[0]])
        w = width0 + (width1 - width0) * i / max(1, len(pts) - 1)
        left.append(pts[i] + n * w / 2)
        right.append(pts[i] - n * w / 2)
    return poly([tuple(p) for p in left + right[::-1]])


def bezier(p0, p1, p2, p3, n=60):
    t = np.linspace(0, 1, n)[:, None]
    p0, p1, p2, p3 = map(np.array, (p0, p1, p2, p3))
    return list(map(tuple, (1 - t) ** 3 * p0 + 3 * (1 - t) ** 2 * t * p1 + 3 * (1 - t) * t ** 2 * p2 + t ** 3 * p3))


def ramp(t, stops):
    """Piecewise-linear colour ramp: stops = [(t, (r,g,b)), ...] in 0..1 / 0..255."""
    t = np.clip(t, 0, 1)
    out = np.zeros(t.shape + (3,), np.float32)
    for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
        m = (t >= t0) & (t <= t1)
        f = ((t - t0) / max(1e-6, t1 - t0))[..., None]
        out[m] = (np.array(c0, np.float32) * (1 - f) + np.array(c1, np.float32) * f)[m]
    return out / 255


def shade(height, strength=6.0, spec_power=24, spec=0.5):
    """Lambert + Blinn specular from a height field (higher = nearer the viewer)."""
    gy, gx = np.gradient(height)
    n = np.stack([-gx * strength * S / 64, -gy * strength * S / 64, np.ones_like(height)], -1)
    n /= np.linalg.norm(n, axis=-1, keepdims=True)
    lam = np.clip((n * LIGHT).sum(-1), 0, 1)
    h = LIGHT + np.array([0, 0, 1], np.float32)
    h /= np.linalg.norm(h)
    sp = np.clip((n * h).sum(-1), 0, 1) ** spec_power * spec
    return lam, sp


class Canvas:
    def __init__(self):
        self.rgb = np.zeros((S, S, 3), np.float32)
        self.a = np.zeros((S, S), np.float32)

    def over(self, color, alpha):
        alpha = np.clip(alpha, 0, 1)[..., None]
        color = np.broadcast_to(np.asarray(color, np.float32), self.rgb.shape)
        a0 = self.a[..., None]
        out_a = alpha + a0 * (1 - alpha)
        self.rgb = (color * alpha + self.rgb * a0 * (1 - alpha)) / np.maximum(out_a, 1e-6)
        self.a = out_a[..., 0]

    def glow(self, mask, color, radius, strength=1.0):
        """Soft emissive halo: adds light where there is paint and coverage where there is none."""
        g = np.clip(blur(mask, radius) * strength, 0, 1)
        self.over(np.asarray(color, np.float32) / 255, g * 0.85)

    def add(self, light):
        """Additive light on already painted pixels (keeps alpha)."""
        self.rgb = np.clip(self.rgb + light * self.a[..., None], 0, 1)

    def outline(self, mask, color=(20, 10, 8), width=6):
        grown = np.asarray(Image.fromarray((np.clip(mask, 0, 1) * 255).astype(np.uint8)).filter(
            __import__('PIL.ImageFilter', fromlist=['MaxFilter']).MaxFilter(width * 2 + 1)), np.float32) / 255
        self.over(np.asarray(color, np.float32) / 255, np.clip(grown - mask, 0, 1) * 0.9)

    def image(self):
        rgba = np.dstack([np.clip(self.rgb, 0, 1), np.clip(self.a, 0, 1)])
        big = Image.fromarray((rgba * 255 + 0.5).astype(np.uint8), 'RGBA')
        # Premultiplied reduction keeps dark fringes off transparent edges.
        pre = np.asarray(big, np.float32) / 255
        pre[..., :3] *= pre[..., 3:]
        small = np.asarray(Image.fromarray((pre * 255 + .5).astype(np.uint8), 'RGBA').resize((256, 256), Image.LANCZOS), np.float32) / 255
        a = small[..., 3:]
        small[..., :3] = np.where(a > 1e-4, small[..., :3] / np.maximum(a, 1e-4), 0)
        return Image.fromarray((np.clip(small, 0, 1) * 255 + .5).astype(np.uint8), 'RGBA')


def orb(cv, cx, cy, r, interior, rim, deep=0.55, seed=0):
    """Glass sphere: emissive interior, darker toward the edge, fresnel rim, two specular highlights."""
    d = np.hypot(X - cx, Y - cy) / r
    m = np.clip((1 - d) * r * S / 2 / 1.5, 0, 1)
    z = np.sqrt(np.clip(1 - d ** 2, 0, 1))
    body = interior * (deep + (1 - deep) * z[..., None] ** 0.7)
    fres = (1 - z) ** 3
    body = body + np.asarray(rim, np.float32)[None, None, :] / 255 * fres[..., None] * 0.9
    cv.over(np.clip(body, 0, 1), m)
    hl = np.clip(1 - np.hypot((X - (cx - r * .38)) / (r * .34), (Y - (cy - r * .42)) / (r * .2)), 0, 1) ** 2
    hl2 = np.clip(1 - np.hypot((X - (cx + r * .42)) / (r * .12), (Y - (cy + r * .45)) / (r * .07)), 0, 1) ** 2
    cv.over((1, 1, 1), (hl * 0.75 + hl2 * 0.35) * m)
    return m


def warp_fire(seed, scale=4, twist=1.8):
    n1 = noise(seed, scale, 5)
    n2 = noise(seed + 1, scale, 5)
    # Domain warp gives swirling, flame-like structure.
    xs = np.clip(((X + (n1 - .5) * twist) + 1) / 2 * (S - 1), 0, S - 1).astype(np.int32)
    ys = np.clip(((Y + (n2 - .5) * twist) + 1) / 2 * (S - 1), 0, S - 1).astype(np.int32)
    base = noise(seed + 2, scale * 2, 5)
    return base[ys, xs]


def stone_blocks(mask, seed, cracks_color, block=0.11):
    """Chunked stone: Voronoi-like blocks with glowing seams, shaded as raised slabs."""
    rng = np.random.default_rng(seed)
    pts = rng.random((90, 2)) * 2 - 1
    best = np.full((S, S), 9.0, np.float32)
    second = np.full((S, S), 9.0, np.float32)
    for px, py in pts:
        d = np.hypot(X - px, Y - py)
        second = np.where(d < best, best, np.minimum(second, d))
        best = np.minimum(best, d)
    edge = np.clip((second - best) / 0.022, 0, 1)
    height = blur(edge * mask, 10) + noise(seed + 9, 16, 4) * 0.08
    return edge, height


# ---------------------------------------------------------------- the four cores
def cinder():
    cv = Canvas()
    cx, cy, r = 0, 0.06, 0.52
    # Furnace halo
    cv.glow(disc(cx, cy, r + .05), (255, 110, 30), 70, 1.3)
    # Rock cradle under the orb: chunked basalt with lava seams
    cradle = poly([(-0.82, 0.2), (-0.62, 0.52), (-0.3, 0.8), (0, 0.9), (0.3, 0.8), (0.62, 0.52), (0.82, 0.2),
                   (0.6, 0.32), (0.35, 0.5), (0, 0.56), (-0.35, 0.5), (-0.6, 0.32)])
    cradle = np.maximum(cradle, disc(cx, cy, r + 0.12) * (Y > cy + 0.18))
    edge, height = stone_blocks(cradle, 11, None)
    lam, sp = shade(height, 7, 16, .25)
    stone = ramp(noise(5, 8, 4) * .6 + lam * .7, [(0, (18, 14, 16)), (.45, (52, 40, 40)), (.8, (96, 78, 70)), (1, (140, 116, 98))])
    seam = (1 - edge) * cradle
    rock = stone * (edge * cradle)[..., None] + ramp(seam * 1.0, [(0, (60, 10, 0)), (.5, (255, 90, 10)), (1, (255, 220, 120))]) * seam[..., None]
    cv.glow(seam, (255, 120, 20), 10, 2.0)
    cv.over(np.clip(rock + sp[..., None], 0, 1), cradle)
    cv.outline(cradle, (16, 8, 6), 4)
    # Molten heart: dark crust veins over a white-hot centre
    f = warp_fire(21, 3, 1.6)
    centre = np.clip(1 - np.hypot(X - cx, Y - cy + .05) / r, 0, 1)
    core = np.clip(f * 1.1 - 0.3 + centre * 0.75, 0, 1) ** 1.2
    interior = ramp(core, [(0, (30, 2, 0)), (.3, (130, 14, 2)), (.55, (225, 70, 6)), (.8, (255, 160, 30)), (1, (255, 238, 170))])
    m = orb(cv, cx, cy, r, interior, (255, 150, 60), deep=0.4)
    cv.outline(m, (30, 6, 2), 4)
    # Crown of the fortress: a heavy bronze band over the upper rim with nine spikes
    crown = np.zeros((S, S), np.float32)
    for i in range(9):
        a = math.radians(-172 + i * 20.5)
        base_r = r + 0.02
        tip_r = r + (0.32 if i == 4 else 0.24 if i in (3, 5) else 0.18 if i in (2, 6) else 0.13)
        w = math.radians(8.5)
        crown = np.maximum(crown, poly([(cx + math.cos(a - w) * base_r, cy + math.sin(a - w) * base_r),
                                        (cx + math.cos(a) * tip_r, cy + math.sin(a) * tip_r),
                                        (cx + math.cos(a + w) * base_r, cy + math.sin(a + w) * base_r),
                                        (cx + math.cos(a) * (base_r - .1), cy + math.sin(a) * (base_r - .1))]))
    ang = np.degrees(np.arctan2(Y - cy, X - cx))
    band = disc(cx, cy, r + 0.07) * (1 - disc(cx, cy, r - 0.06)) * (ang < -8) * (ang > -172)
    crown = np.maximum(crown, band)
    h = blur(crown, 8)
    lam, sp = shade(h, 9, 30, .6)
    metal = ramp(lam * .9 + noise(31, 16, 3) * .2, [(0, (30, 18, 12)), (.4, (70, 44, 26)), (.75, (150, 98, 46)), (1, (240, 190, 110))])
    cv.over(np.clip(metal + sp[..., None] * np.array([1, .8, .5]), 0, 1), crown)
    cv.outline(crown, (20, 10, 4), 4)
    # Ember gems on the crown band
    for i, a in enumerate((-130, -90, -50)):
        gx, gy = cx + math.cos(math.radians(a)) * r, cy + math.sin(math.radians(a)) * r
        gem = disc(gx, gy, 0.05 if a == -90 else 0.035)
        cv.glow(gem, (255, 140, 40), 14, 1.5)
        cv.over(np.clip(ramp(1 - np.hypot(X - gx, Y - gy) / .05, [(0, (120, 10, 0)), (.6, (255, 90, 20)), (1, (255, 240, 200))]), 0, 1), gem)
    # Floating sparks
    rng = np.random.default_rng(3)
    for _ in range(14):
        sx, sy = rng.uniform(-.75, .75), rng.uniform(-.85, -.1)
        if math.hypot(sx - cx, sy - cy) < r + .1:
            continue
        cv.glow(disc(sx, sy, .012), (255, 160, 60), 6, 2)
        cv.over((1, .85, .5), disc(sx, sy, .008))
    return cv


def hound():
    cv = Canvas()
    cx, cy, r = 0, 0.08, 0.47
    cv.glow(disc(cx, cy, r), (60, 220, 255), 60, 1.2)
    # Soul fire: wavering tongues licking up behind the orb, hotter at the root
    flames = np.zeros((S, S), np.float32)
    wobble = (noise(42, 4, 3) - .5) * .18
    for dx, hgt, w, lean in ((-.24, .36, .2, -.08), (0, .58, .26, .04), (.24, .4, .2, .1), (-.4, .22, .14, -.12), (.42, .24, .14, .12)):
        base_y = cy - r * .35
        tip = (cx + dx + lean, cy - r - hgt)
        left = bezier((cx + dx - w / 2, base_y), (cx + dx - w * .65, cy - r - hgt * .2), (tip[0] - w * .45, tip[1] + hgt * .45), tip)
        right = bezier(tip, (tip[0] + w * .1, tip[1] + hgt * .55), (cx + dx + w * .7, cy - r - hgt * .1), (cx + dx + w / 2, base_y))
        flames = np.maximum(flames, poly(left + right))
    # Shift the mask sideways by a smooth noise field so the edges flicker instead of staying geometric.
    xs = np.clip(((X + wobble * np.clip(cy - Y, 0, 1) * 2) + 1) / 2 * (S - 1), 0, S - 1).astype(np.int32)
    flames = flames[np.arange(S)[:, None], xs]
    heat = np.clip((Y - (cy - r - .55)) / .75, 0, 1) * .7 + noise(41, 8, 4) * .45
    cv.glow(flames, (70, 220, 255), 22, 1.4)
    cv.over(ramp(heat, [(0, (20, 120, 200)), (.45, (60, 200, 255)), (.75, (160, 245, 255)), (1, (240, 255, 255))]), flames * .92)
    # Soul orb
    f = warp_fire(51, 3, 2.0)
    core = np.clip(f * 1.2 - .2 + (1 - np.hypot(X - cx, Y - cy) / r) * .6, 0, 1)
    interior = ramp(core, [(0, (4, 16, 40)), (.35, (10, 90, 140)), (.6, (40, 200, 255)), (.85, (150, 250, 255)), (1, (240, 255, 255))])
    m = orb(cv, cx, cy, r, interior, (150, 240, 255), deep=0.4)
    cv.outline(m, (4, 20, 30), 4)
    # Rib claws: three bones per side rise from a vertebra stack and hook over the orb like a clutching cage
    ribs = np.zeros((S, S), np.float32)
    for side in (-1, 1):
        for k, (start_deg, end_deg, rr, w) in enumerate(((98, 238, r + .03, .075), (84, 212, r + .15, .065), (70, 186, r + .27, .055))):
            pts = []
            for t in np.linspace(0, 1, 48):
                a = math.radians(start_deg + (end_deg - start_deg) * t)
                bulge = rr + .035 * math.sin(t * math.pi)
                pts.append((cx - side * math.cos(a) * bulge, cy + math.sin(a) * bulge))
            ribs = np.maximum(ribs, stroke(pts, w, w * .5))
    vertebrae = np.zeros((S, S), np.float32)
    for k in range(4):
        vy = cy + r + .06 + k * .09
        vertebrae = np.maximum(vertebrae, ellipse(cx, vy, .11 - k * .015, .045) * (np.hypot((X - cx) / (.11 - k * .015), (Y - vy) / .045) < 1))
    # Two fangs biting up in front of the orb
    fangs = np.zeros((S, S), np.float32)
    for side in (-1, 1):
        fangs = np.maximum(fangs, poly(bezier((cx + side * .3, cy + r - .02), (cx + side * .3, cy + r * .5), (cx + side * .22, cy + r * .2),
                                              (cx + side * .14, cy + .06)) + [(cx + side * .19, cy + r + .02)]))
    bone = np.maximum(np.maximum(ribs, vertebrae), fangs)
    h = blur(bone, 9) + noise(61, 24, 3) * .03
    lam, sp = shade(h, 10, 20, .35)
    tex = ramp(lam * .85 + noise(62, 10, 4) * .25, [(0, (60, 52, 44)), (.4, (150, 136, 112)), (.75, (220, 208, 182)), (1, (255, 250, 236))])
    cracks = (np.abs(noise(63, 10, 4) - .5) < .012) * bone
    tex = tex * (1 - cracks[..., None] * .6)
    cv.over(np.clip(tex + sp[..., None], 0, 1), bone)
    cv.outline(bone, (24, 30, 36), 4)
    # Cyan soul light seeping through the gaps
    cv.add(np.clip(blur(m, 25) * (1 - bone) * (1 - m), 0, 1)[..., None] * np.array([0, .25, .35]))
    return cv


def moth():
    cv = Canvas()
    cx, cy = 0, 0.04
    cv.glow(disc(cx, cy, .5), (245, 120, 255), 90, 1.0)
    # Four stained-glass wings with lead lines
    wings = []
    for side in (-1, 1):
        upper = poly([(cx, cy - .05)] + [(cx + side * x, cy + y) for x, y in
                     ((.25, -.45), (.5, -.72), (.8, -.8), (.95, -.62), (.9, -.35), (.7, -.12), (.42, -.02))])
        lower = poly([(cx, cy + .05)] + [(cx + side * x, cy + y) for x, y in
                     ((.32, .06), (.6, .2), (.72, .45), (.6, .66), (.38, .7), (.18, .5))])
        wings += [upper, lower]
    wing = np.clip(sum(wings), 0, 1)
    rng = np.random.default_rng(71)
    pts = np.concatenate([rng.random((60, 2)) * 2 - 1])
    best = np.full((S, S), 9.0, np.float32)
    second = np.full((S, S), 9.0, np.float32)
    owner = np.zeros((S, S), np.int32)
    for i, (px, py) in enumerate(pts):
        d = np.hypot(X - abs(px) * np.sign(X + 1e-6), Y - py)  # mirror cells across the body
        closer = d < best
        second = np.where(closer, best, np.minimum(second, d))
        owner = np.where(closer, i, owner)
        best = np.minimum(best, d)
    lead = np.clip(1 - (second - best) / 0.018, 0, 1)
    palette = np.array([(250, 120, 220), (180, 90, 250), (110, 220, 240), (255, 200, 110), (240, 80, 170), (140, 120, 255)], np.float32) / 255
    pane = palette[owner % len(palette)]
    tint = (0.55 + 0.45 * (1 - np.clip(best / .14, 0, 1)))[..., None]
    glass = pane * tint + np.array([1, 1, 1]) * (np.clip(1 - np.hypot(X + .4, Y + .5) / .5, 0, 1) ** 2 * .35)[..., None]
    # Eye spots on the upper wings
    for side in (-1, 1):
        ex, ey = cx + side * .62, cy - .5
        ring = np.clip(1 - np.abs(np.hypot(X - ex, Y - ey) - .1) / .025, 0, 1)
        glass = glass * (1 - ring[..., None]) + np.array([1, .85, .4]) * ring[..., None]
        dot = disc(ex, ey, .055)
        glass = glass * (1 - dot[..., None]) + np.array([.25, .1, .45]) * dot[..., None]
    rim = np.clip(wing - np.asarray(Image.fromarray((wing * 255).astype(np.uint8)).filter(
        __import__('PIL.ImageFilter', fromlist=['MinFilter']).MinFilter(17)), np.float32) / 255, 0, 1)
    lead = np.maximum(lead, rim)
    leadc = ramp(shade(blur(lead, 3), 4, 30, .5)[0], [(0, (60, 40, 10)), (.6, (200, 150, 50)), (1, (255, 230, 150))])
    col = glass * (1 - lead[..., None]) + leadc * lead[..., None]
    cv.over(np.clip(col, 0, 1), wing * (0.82 + 0.18 * lead))
    cv.outline(wing, (40, 16, 50), 4)
    # Gold filigree setting
    setting = disc(cx, cy, .3) * (1 - disc(cx, cy, .23))
    for i in range(8):
        a = math.radians(i * 45 + 22.5)
        setting = np.maximum(setting, poly([(cx + math.cos(a - .12) * .28, cy + math.sin(a - .12) * .28),
                                            (cx + math.cos(a) * .4, cy + math.sin(a) * .4),
                                            (cx + math.cos(a + .12) * .28, cy + math.sin(a + .12) * .28)]))
    lam, sp = shade(blur(setting, 6), 9, 30, .7)
    gold = ramp(lam, [(0, (70, 40, 10)), (.5, (190, 140, 50)), (.85, (250, 215, 120)), (1, (255, 250, 220))])
    cv.over(np.clip(gold + sp[..., None], 0, 1), setting)
    cv.outline(setting, (40, 20, 6), 3)
    # Faceted heart crystal: eight kite facets around a table
    gem_r = .23
    facets = []
    table = [(cx + math.cos(math.radians(a)) * gem_r * .45, cy + math.sin(math.radians(a)) * gem_r * .45) for a in range(0, 360, 45)]
    outer = [(cx + math.cos(math.radians(a + 22.5)) * gem_r, cy + math.sin(math.radians(a + 22.5)) * gem_r) for a in range(0, 360, 45)]
    gem = np.zeros((S, S, 3), np.float32)
    gem_m = poly(outer)
    for i in range(8):
        f = poly([table[i], outer[i - 1], outer[i], table[(i + 1) % 8]])
        a = math.radians(i * 45 + 22.5)
        lit = .5 + .5 * (-math.cos(a) * .7 - math.sin(a) * .7)
        c = ramp(np.full((S, S), lit, np.float32), [(0, (120, 20, 90)), (.5, (240, 80, 190)), (1, (255, 200, 240))])
        gem = gem * (1 - f[..., None]) + c * f[..., None]
    t = poly(table)
    gem = gem * (1 - t[..., None]) + ramp(1 - np.hypot(X - cx + .03, Y - cy + .03) / (gem_r * .5), [(0, (210, 60, 170)), (1, (255, 225, 250))]) * t[..., None]
    cv.glow(gem_m, (255, 110, 230), 20, 1.5)
    cv.over(np.clip(gem, 0, 1), gem_m)
    cv.outline(gem_m, (60, 10, 50), 3)
    for sx, sy, s in ((-.08, -.1, .05), (.12, .08, .03)):
        star = np.clip(1 - (np.abs(X - cx - sx) / s + np.abs(Y - cy - sy) / (s * .18)), 0, 1) + np.clip(1 - (np.abs(X - cx - sx) / (s * .18) + np.abs(Y - cy - sy) / s), 0, 1)
        cv.over((1, 1, 1), np.clip(star, 0, 1))
    return cv


def sun():
    cv = Canvas()
    cx, cy, r = 0, 0.02, 0.42
    # Corona: curved golden flame-blades around the dead sun, long and short alternating
    cv.glow(disc(cx, cy, r + .04), (255, 180, 50), 70, 1.1)
    rays = np.zeros((S, S), np.float32)
    for i in range(12):
        a = math.radians(i * 30 + 15)
        length = r + (.46 if i % 2 == 0 else .3)
        w = math.radians(13 if i % 2 == 0 else 10)
        bend = math.radians(14)
        rays = np.maximum(rays, poly([(cx + math.cos(a - w) * (r - .02), cy + math.sin(a - w) * (r - .02)),
                                      (cx + math.cos(a - w * .2 + bend * .5) * (r + (length - r) * .55), cy + math.sin(a - w * .2 + bend * .5) * (r + (length - r) * .55)),
                                      (cx + math.cos(a + bend) * length, cy + math.sin(a + bend) * length),
                                      (cx + math.cos(a + w * .9 + bend * .4) * (r + (length - r) * .45), cy + math.sin(a + w * .9 + bend * .4) * (r + (length - r) * .45)),
                                      (cx + math.cos(a + w) * (r - .02), cy + math.sin(a + w) * (r - .02))]))
    lam, sp = shade(blur(rays, 7), 8, 30, .6)
    gold = ramp(lam * .8 + np.clip(1 - (np.hypot(X - cx, Y - cy) - r) / .45, 0, 1) * .4,
                [(0, (90, 40, 0)), (.5, (220, 140, 30)), (.85, (255, 210, 90)), (1, (255, 250, 210))])
    cv.over(np.clip(gold + sp[..., None], 0, 1), rays)
    cv.outline(rays, (40, 18, 0), 3)
    # Eclipse ring
    ring = disc(cx, cy, r + .07) * (1 - disc(cx, cy, r + .025))
    lam, sp = shade(blur(ring, 4), 8, 40, .8)
    cv.glow(ring, (255, 220, 120), 12, 1.6)
    cv.over(np.clip(ramp(lam, [(0, (120, 70, 10)), (.6, (255, 200, 80)), (1, (255, 255, 230))]) + sp[..., None], 0, 1), ring)
    # Obsidian sphere with molten cracks
    d = np.hypot(X - cx, Y - cy) / r
    z = np.sqrt(np.clip(1 - d ** 2, 0, 1))
    n = noise(81, 5, 5)
    cracks = np.clip(1 - np.abs(n - .5) / .018, 0, 1) * (z > .05)
    sphere = disc(cx, cy, r)
    lam = np.clip(((X - cx) / r * LIGHT[0] + (Y - cy) / r * LIGHT[1] + z * LIGHT[2]), 0, 1)
    rock = ramp(lam * .7 + noise(82, 20, 3) * .2, [(0, (6, 4, 10)), (.5, (28, 20, 40)), (.85, (70, 56, 90)), (1, (140, 120, 170))])
    rock = rock * (1 - cracks[..., None]) + ramp(cracks, [(0, (120, 50, 0)), (.6, (255, 170, 40)), (1, (255, 245, 190))]) * cracks[..., None]
    hv = LIGHT + np.array([0, 0, 1], np.float32)
    hv /= np.linalg.norm(hv)
    spec = np.clip((X - cx) / r * hv[0] + (Y - cy) / r * hv[1] + z * hv[2], 0, 1) ** 60 * .9
    cv.over(np.clip(rock + spec[..., None], 0, 1), sphere)
    cv.add(blur(cracks * sphere, 6)[..., None] * np.array([.5, .3, .05]))
    cv.outline(sphere, (10, 6, 2), 3)
    # The slit eye: molten iris, black vertical pupil
    iris = np.clip(1 - (((X - cx) / .17) ** 2 + ((Y - cy) / .1) ** 2), 0, 1)
    eye = (iris > 0).astype(np.float32)
    cv.glow(eye, (255, 200, 60), 18, 1.8)
    cv.over(ramp(iris ** .6, [(0, (200, 80, 0)), (.5, (255, 180, 40)), (1, (255, 250, 200))]), eye)
    pupil = (((X - cx) / .025) ** 2 + ((Y - cy) / .095) ** 2 < 1).astype(np.float32)
    cv.over((0.03, 0.01, 0.02), pupil)
    cv.over((1, 1, 1), disc(cx - .05, cy - .035, .018))
    return cv


PAINTERS = {'cinder_colossus': cinder, 'soul_hound': hound, 'crystal_moth': moth, 'unlit_sun': sun}


def main():
    (OUT / 'textures').mkdir(parents=True, exist_ok=True)
    icons = {}
    for core in IDS:
        image = PAINTERS[core]().image()
        image.save(OUT / 'textures' / f'realm_core_{core}.png', optimize=True)
        icons[core] = image
    # Preview: the full icons, then at inventory size (32 px, nearest) on a dark slot colour.
    sheet = Image.new('RGBA', (4 * 280 + 20, 380), (24, 26, 34, 255))
    for i, core in enumerate(IDS):
        sheet.alpha_composite(icons[core], (20 + i * 280, 20))
        small = icons[core].resize((32, 32), Image.LANCZOS).resize((96, 96), Image.NEAREST)
        slot = Image.new('RGBA', (96, 96), (139, 139, 139, 255))
        slot.alpha_composite(small)
        sheet.alpha_composite(slot, (100 + i * 280, 280))
    sheet.save(OUT / 'preview.png')
    print('wrote', ', '.join(f'realm_core_{c}.png' for c in IDS))


if __name__ == '__main__':
    main()
