"""Procedural art for the Advance Magic 1.38.0 spell VFX kit.

Full-colour, element-specific sprites (no tinting, so each element keeps its own white-hot core):

* circle_<tint>: a magic circle with its own geometry per element (flame petals, wave rings, hexagonal
  snowflake, triskelion spiral, tectonic octagram, sunburst, pentagram and thorns, zigzag hexagram,
  vine and flower of life);
* glow_<tint>: a soft orb with a white core for projectile heads, cores and flashes;
* burst_<tint>: an impact burst per element (flame petals, splash crown, shard star, swirl, ground
  cracks, ray star, void, forked arcs, bloom);
* bolt_a/b/c: vertical lightning strikes; beam: a greyscale vertical shaft tinted per use;
* atlas: 16 px cells for the Bedrock particle emitters (flame and smoke flipbooks, sparks, droplets,
  shards, leaves, rocks, wisps, streaks, arcs), white so emitters tint them per element.

Everything is drawn supersampled then filtered down, so lines stay crisp with a soft glow.
"""
import math
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

TINTS = ('fire', 'water', 'ice', 'wind', 'earth', 'light', 'dark', 'lightning', 'heal')
# (white-hot core, main glow, deep edge) per tint.
PALETTE = {
    'fire': ((255, 246, 220), (255, 142, 42), (205, 46, 12)),
    'water': ((226, 250, 255), (64, 184, 255), (22, 84, 205)),
    'ice': ((246, 253, 255), (156, 228, 255), (74, 146, 232)),
    'wind': ((236, 255, 246), (112, 242, 192), (28, 172, 142)),
    'earth': ((255, 242, 204), (220, 170, 84), (124, 82, 40)),
    'light': ((255, 255, 236), (255, 216, 92), (232, 152, 30)),
    'dark': ((242, 222, 255), (172, 92, 255), (82, 20, 164)),
    'lightning': ((246, 251, 255), (124, 192, 255), (72, 92, 255)),
    'heal': ((236, 255, 236), (112, 255, 152), (30, 182, 92)),
}


class Canvas:
    """Supersampled line art: a crisp core layer and a glow layer, composed into one RGBA sprite."""

    def __init__(self, size, scale=4):
        self.size, self.scale = size, scale
        self.big = size * scale
        self.core = Image.new('L', (self.big, self.big), 0)
        self.glow = Image.new('L', (self.big, self.big), 0)
        self.fill = Image.new('L', (self.big, self.big), 0)
        self.c = ImageDraw.Draw(self.core)
        self.g = ImageDraw.Draw(self.glow)
        self.f = ImageDraw.Draw(self.fill)

    def p(self, x, y):
        """Unit coordinates (-1..1, y up) to supersampled pixels."""
        h = self.big / 2
        return (h + x * h * .96, h - y * h * .96)

    def w(self, width):
        return max(1, int(round(width * self.big / 512)))

    def line(self, points, width=2.0, glow=3.0, closed=False):
        pts = [self.p(x, y) for x, y in points]
        if closed:
            pts.append(pts[0])
        self.c.line(pts, fill=255, width=self.w(width), joint='curve')
        self.g.line(pts, fill=255, width=self.w(width * glow), joint='curve')

    def ring(self, r, width=2.0, glow=3.0, steps=360):
        self.line([(r * math.cos(a), r * math.sin(a)) for a in np.linspace(0, math.tau, steps, endpoint=False)], width, glow, True)

    def poly(self, n, r, rot=0.0, width=2.0, step=1):
        pts = [(r * math.cos(rot + math.tau * i / n), r * math.sin(rot + math.tau * i / n)) for i in range(n)]
        if step == 1:
            self.line(pts, width, closed=True)
            return
        seen = set()
        for start in range(n):
            if start in seen:
                continue
            cycle, i = [], start
            while i not in seen:
                seen.add(i)
                cycle.append(pts[i])
                i = (i + step) % n
            self.line(cycle, width, closed=True)

    def dot(self, x, y, r, glow=2.5):
        cx, cy = self.p(x, y)
        rr = r * self.big / 2
        self.c.ellipse((cx - rr, cy - rr, cx + rr, cy + rr), fill=255)
        self.g.ellipse((cx - rr * glow, cy - rr * glow, cx + rr * glow, cy + rr * glow), fill=255)

    def shape(self, points, alpha=255, outline=1.5):
        pts = [self.p(x, y) for x, y in points]
        self.f.polygon(pts, fill=alpha)
        if outline:
            self.line(points, outline, closed=True)

    def ticks(self, r0, r1, n, rot=0.0, width=1.6):
        for i in range(n):
            a = rot + math.tau * i / n
            self.line([(r0 * math.cos(a), r0 * math.sin(a)), (r1 * math.cos(a), r1 * math.sin(a))], width)

    def runes(self, r, height, count, seed, width=1.5):
        """A band of invented glyphs set tangentially around radius r."""
        rng = random.Random(seed)
        for i in range(count):
            a = math.tau * i / count
            t = (-math.sin(a), math.cos(a))
            n = (math.cos(a), math.sin(a))
            half = height / 2
            w = height * .55
            nodes = [(-w / 2, -half), (0, -half), (w / 2, -half), (-w / 2, 0), (0, 0), (w / 2, 0), (-w / 2, half), (0, half), (w / 2, half)]
            for _ in range(rng.randint(2, 4)):
                u, v = rng.sample(range(9), 2)
                pts = []
                for k in (u, v):
                    du, dv = nodes[k]
                    pts.append((n[0] * (r + dv) + t[0] * du, n[1] * (r + dv) + t[1] * du))
                self.line(pts, width, glow=2.4)
            if rng.random() < .45:
                du, dv = nodes[rng.randrange(9)]
                self.dot(n[0] * (r + dv) + t[0] * du, n[1] * (r + dv) + t[1] * du, height * .09)

    def render(self, palette, glow_radius=10, glow_strength=.85, fill_alpha=.35, core_white=.75):
        core_c, main_c, deep_c = (np.array(c, dtype=np.float64) / 255 for c in palette)
        size = (self.size, self.size)
        core = np.asarray(self.core.resize(size, Image.Resampling.LANCZOS), dtype=np.float64) / 255
        glow_img = self.glow.resize(size, Image.Resampling.LANCZOS).filter(ImageFilter.GaussianBlur(glow_radius * self.size / 512))
        glow = np.asarray(glow_img, dtype=np.float64) / 255
        wide = np.asarray(self.glow.resize(size, Image.Resampling.LANCZOS).filter(ImageFilter.GaussianBlur(glow_radius * 2.6 * self.size / 512)), dtype=np.float64) / 255
        fill = np.asarray(self.fill.resize(size, Image.Resampling.LANCZOS), dtype=np.float64) / 255
        glow = np.clip(glow * 1.6, 0, 1)
        wide = np.clip(wide * 1.4, 0, 1)
        # Colour: deep at the far glow, main in the glow, white-hot on the line cores.
        col = main_c * wide[..., None] * .5 + main_c * glow[..., None] + main_c * fill[..., None] * .8
        weight = wide * .5 + glow + fill * .8
        col = col / np.maximum(weight, 1e-6)[..., None]
        hot = core_c * core_white + main_c * (1 - core_white)
        col = col * (1 - core[..., None]) + hot * core[..., None]
        alpha = np.clip(np.maximum.reduce([core, glow * glow_strength, wide * glow_strength * .16, fill * fill_alpha]), 0, 1)
        out = np.zeros((self.size, self.size, 4))
        out[..., :3] = np.clip(col, 0, 1)
        out[..., 3] = alpha
        return Image.fromarray(np.rint(out * 255).astype(np.uint8), 'RGBA')


def polar(r, a):
    return (r * math.cos(a), r * math.sin(a))


# --- magic circles ----------------------------------------------------------------------------------

def circle_frame(c, seed, rune_r=.80, outer=.97):
    c.ring(outer, 2.6)
    c.ring(outer - .035, 1.2)
    c.runes(rune_r + .045, .075, 30, seed)
    c.ring(rune_r - .02, 1.6)
    c.ring(rune_r + .11, 1.2)


def circle(tint, size=384):
    c = Canvas(size)
    seed = TINTS.index(tint) * 97 + 11
    if tint == 'fire':
        circle_frame(c, seed)
        for i in range(8):
            a = math.tau * i / 8
            pts = []
            for k in range(25):
                u = k / 24
                w = .11 * math.sin(math.pi * u) ** .8 * (1 - .35 * u)
                r = .3 + .42 * u
                pts.append(polar(r, a + w * math.sin(math.pi * u * 1.0) * (1 if k < 13 else 1)))
            left = [polar(.3 + .42 * k / 24, a - .14 * math.sin(math.pi * k / 24) ** .8 * (1 - .4 * k / 24)) for k in range(25)]
            right = [polar(.3 + .42 * k / 24, a + .14 * math.sin(math.pi * k / 24) ** .8 * (1 - .4 * k / 24)) for k in range(25)]
            c.shape(left + right[::-1], alpha=150, outline=1.6)
        c.poly(8, .62, math.pi / 8, 1.4, step=3)
        c.ring(.3, 2.2)
        for i in range(24):
            a = math.tau * i / 24
            r1 = .27 if i % 2 else .22
            c.line([polar(.13, a), polar(r1, a + .08)], 1.5)
        c.dot(0, 0, .08)
    elif tint == 'water':
        circle_frame(c, seed)
        c.line([polar(.68 + .025 * math.sin(a * 12), a) for a in np.linspace(0, math.tau, 720, endpoint=False)], 1.8, closed=True)
        c.line([polar(.6 + .02 * math.sin(a * 12 + math.pi), a) for a in np.linspace(0, math.tau, 720, endpoint=False)], 1.4, closed=True)
        for i in range(6):
            a0 = math.tau * i / 6
            # A breaking crest: an arc that curls back into a small spiral at its tip.
            arc = [polar(.5 - .0 * t, a0 + t * .7) for t in np.linspace(0, 1, 40)]
            tip = polar(.5, a0 + .7)
            curl = [(tip[0] + .07 * (1 - u) * math.cos(a0 + .7 + math.pi / 2 + u * 5.5), tip[1] + .07 * (1 - u) * math.sin(a0 + .7 + math.pi / 2 + u * 5.5)) for u in np.linspace(0, 1, 40)]
            c.line(arc + curl, 1.8)
        c.ring(.38, 1.4)
        c.ring(.26, 1.8)
        drop = [(.1 * math.cos(t), -.03 + .1 * math.sin(t)) for t in np.linspace(-.35, math.pi + .35, 40)][::-1]
        drop = [(x, -y - .02) for x, y in drop] + [(0, .17)]
        c.shape([(x, y) for x, y in drop], 170, 1.6)
    elif tint == 'ice':
        circle_frame(c, seed)
        c.poly(6, .7, math.pi / 6, 2.0)
        c.poly(6, .62, 0, 1.2)
        for i in range(6):
            a = math.tau * i / 6 + math.pi / 2
            c.line([(0, 0), polar(.6, a)], 2.0)
            for r, l in ((.22, .1), (.36, .13), (.5, .09)):
                base = polar(r, a)
                for s in (-1, 1):
                    tip = (base[0] + l * math.cos(a + s * .9), base[1] + l * math.sin(a + s * .9))
                    c.line([base, tip], 1.5)
            tip = polar(.66, a)
            c.shape([polar(.58, a - .05), tip, polar(.58, a + .05), polar(.54, a)], 160, 1.2)
        c.poly(6, .15, math.pi / 6, 1.8)
        c.dot(0, 0, .04)
    elif tint == 'wind':
        circle_frame(c, seed)
        for i in range(3):
            a0 = math.tau * i / 3
            c.line([polar(.06 + .64 * (t / 6.0), a0 + t) for t in np.linspace(0, 6.0, 200) if .06 + .64 * t / 6 < .7], 2.2)
            c.line([polar(.1 + .55 * (t / 6.0), a0 + t + .35) for t in np.linspace(.4, 6.0, 160)], 1.0)
        for i in range(12):
            a = math.tau * i / 12
            c.line([polar(.73, a + t) for t in np.linspace(0, .32, 20)], 1.6)
            c.dot(*polar(.73, a + .34), .012)
        c.ring(.12, 1.6)
    elif tint == 'earth':
        circle_frame(c, seed)
        c.poly(4, .72, math.pi / 4, 2.4)
        c.poly(4, .72, 0, 2.4)
        c.poly(8, .5, math.pi / 8, 1.4)
        c.poly(4, .36, math.pi / 4, 2.0)
        for i in range(8):
            a = math.tau * i / 8
            cx, cy = polar(.61, a)
            c.shape([(cx + .045 * math.cos(a + k * math.pi / 2), cy + .045 * math.sin(a + k * math.pi / 2)) for k in range(4)], 170, 1.2)
        for i in range(4):
            a = math.tau * i / 4 + math.pi / 4
            c.line([polar(.36, a), polar(.13, a)], 1.6)
        c.poly(4, .13, 0, 2.0)
        c.dot(0, 0, .035)
    elif tint == 'light':
        circle_frame(c, seed)
        for i in range(24):
            a = math.tau * i / 24
            c.line([polar(.7, a), polar(.97 if i % 2 == 0 else .9, a)], 1.6 if i % 2 == 0 else 1.0)
        c.poly(12, .68, 0, 1.4, step=5)
        c.ring(.32, 2.0)
        for i in range(12):
            a = math.tau * i / 12 + math.pi / 12
            c.shape([polar(.2, a - .12), polar(.31, a), polar(.2, a + .12)], 150, 1.0)
        c.ring(.18, 1.6)
        c.line([(-.1, 0), (.1, 0)], 2)
        c.line([(0, -.1), (0, .1)], 2)
        c.dot(0, 0, .04)
    elif tint == 'dark':
        circle_frame(c, seed)
        for i in range(20):
            a = math.tau * i / 20
            c.shape([polar(.79, a - .07), polar(.66, a), polar(.79, a + .07)], 170, 1.0)
        c.poly(5, .6, math.pi / 2, 2.0, step=2)
        c.ring(.6, 1.4)
        for i in range(5):
            a = math.tau * i / 5 + math.pi / 2 + math.pi / 5
            cx, cy = polar(.47, a)
            outer = [(cx + .07 * math.cos(t), cy + .07 * math.sin(t)) for t in np.linspace(.6, math.tau - .6, 30)]
            c.line(outer, 1.4)
        eye = [(.2 * math.cos(t), .1 * math.sin(t) * (1 if t < math.pi else 1)) for t in np.linspace(0, math.tau, 80)]
        c.line([(x, y * (1.0)) for x, y in eye], 1.8, closed=True)
        c.dot(0, 0, .055)
    elif tint == 'lightning':
        c.ring(.97, 2.6)
        zig = []
        for i in range(48):
            a = math.tau * i / 48
            zig.append(polar(.9 if i % 2 == 0 else .84, a))
        c.line(zig, 1.8, closed=True)
        c.runes(.76, .07, 26, seed)
        c.ring(.7, 1.6)
        c.poly(6, .66, math.pi / 2, 1.8, step=2)
        c.poly(6, .38, 0, 1.2)
        bolt = [(-.05, .3), (.07, .05), (-.02, .05), (.06, -.3), (-.08, -.02), (.01, -.02)]
        c.shape(bolt, 200, 1.6)
    elif tint == 'heal':
        circle_frame(c, seed)
        vine = [polar(.68 + .03 * math.sin(a * 10), a) for a in np.linspace(0, math.tau, 600, endpoint=False)]
        c.line(vine, 1.6, closed=True)
        for i in range(20):
            a = math.tau * i / 20 + .1
            base = polar(.68 + .03 * math.sin(a * 10), a)
            tip_a = a + (.16 if i % 2 else -.16)
            tip = polar(.6 if i % 2 else .76, tip_a)
            mid1 = ((base[0] + tip[0]) / 2 + .02 * math.cos(a), (base[1] + tip[1]) / 2 + .02 * math.sin(a))
            mid2 = ((base[0] + tip[0]) / 2 - .02 * math.cos(a), (base[1] + tip[1]) / 2 - .02 * math.sin(a))
            c.shape([base, mid1, tip, mid2], 170, .9)
        for i in range(6):
            cx, cy = polar(.17, math.tau * i / 6)
            c.line([(cx + .17 * math.cos(t), cy + .17 * math.sin(t)) for t in np.linspace(0, math.tau, 90)], 1.3, closed=True)
        c.ring(.34, 1.8)
        c.ring(.17, 1.2)
        c.dot(0, 0, .04)
    return c.render(PALETTE[tint])


# --- glow orbs ---------------------------------------------------------------------------------------

def field(size):
    y, x = np.mgrid[0:size, 0:size].astype(np.float64)
    h = (size - 1) / 2
    return (x - h) / h, (h - y) / h


def to_rgba(color, alpha):
    out = np.zeros(alpha.shape + (4,))
    out[..., :3] = np.clip(color, 0, 1)
    out[..., 3] = np.clip(alpha, 0, 1)
    return Image.fromarray(np.rint(out * 255).astype(np.uint8), 'RGBA')


def glow(tint, size=128):
    core_c, main_c, deep_c = (np.array(c, dtype=np.float64) / 255 for c in PALETTE[tint])
    x, y = field(size)
    r = np.hypot(x, y)
    a = np.arctan2(y, x)
    core = np.exp(-(r / .18) ** 2)
    body = np.exp(-(r / .36) ** 2)
    halo = np.exp(-(r / .62) ** 2) * .32 * np.clip((1 - r) / .3, 0, 1)
    rays = np.exp(-(np.abs(np.sin(2 * a)) * r / .03) ** 2) * np.clip(1 - r, 0, 1) ** 2 * .7
    alpha = np.clip(core + body * .85 + halo + rays, 0, 1) * (r < 1)
    t = np.clip(core * 1.4 + rays * .6, 0, 1)[..., None]
    color = core_c * t + main_c * (1 - t)
    return to_rgba(color, alpha)


# --- bursts ------------------------------------------------------------------------------------------

def noise1(seed, n=64):
    rng = np.random.default_rng(seed)
    return rng.random(n)


def burst(tint, size=256):
    core_c, main_c, deep_c = (np.array(c, dtype=np.float64) / 255 for c in PALETTE[tint])
    seed = TINTS.index(tint) + 5
    if tint in ('fire', 'heal'):
        x, y = field(size)
        r, a = np.hypot(x, y), np.arctan2(y, x)
        petals = 9 if tint == 'fire' else 8
        jag = noise1(seed, petals)
        phase = (a / math.tau * petals) % 1
        idx = np.floor((a / math.tau % 1) * petals).astype(int) % petals
        shape = np.sin(np.pi * phase) ** (2.2 if tint == 'fire' else 1.4)
        reach = .45 + .5 * shape * (.75 + .25 * jag[idx])
        inside = np.clip((reach - r) / .08, 0, 1)
        heat = np.clip(1 - r / np.maximum(reach, .05), 0, 1)
        core = np.exp(-(r / .2) ** 2)
        alpha = np.clip(inside * (.35 + .65 * heat) + core, 0, 1)
        col = core_c * np.clip(core * 1.4 + heat ** 3, 0, 1)[..., None] + (main_c * heat[..., None] + deep_c * (1 - heat[..., None])) * (1 - np.clip(core * 1.4 + heat ** 3, 0, 1))[..., None]
        if tint == 'heal':
            sparkle = np.zeros_like(r)
            for i in range(8):
                ang = math.tau * i / 8 + .4
                sx, sy = .78 * math.cos(ang), .78 * math.sin(ang)
                d = np.hypot(x - sx, y - sy)
                sparkle += np.exp(-(d / .03) ** 2)
            alpha = np.clip(alpha + sparkle, 0, 1)
            col = col * (1 - np.clip(sparkle, 0, 1))[..., None] + core_c * np.clip(sparkle, 0, 1)[..., None]
        return to_rgba(col, alpha * (r < 1))
    c = Canvas(size)
    rng = random.Random(seed)
    if tint == 'water':
        c.ring(.55, 2.4)
        c.ring(.3, 1.2)
        for i in range(14):
            a = math.tau * i / 14 + rng.uniform(-.1, .1)
            r = rng.uniform(.68, .86)
            c.dot(*polar(r, a), rng.uniform(.03, .055))
            c.line([polar(.56, a), polar(r - .05, a)], 1.6)
        for i in range(24):
            a = rng.uniform(0, math.tau)
            c.dot(*polar(rng.uniform(.88, .97), a), rng.uniform(.01, .02))
        return c.render(PALETTE[tint], glow_radius=8)
    if tint == 'ice':
        for i in range(8):
            a = math.tau * i / 8 + rng.uniform(-.12, .12)
            length = .95 if i % 2 == 0 else rng.uniform(.6, .75)
            w = .09 if i % 2 == 0 else .07
            c.shape([polar(.12, a - .25), polar(length * .35, a - w * 2.2 / length), polar(length, a), polar(length * .35, a + w * 2.2 / length), polar(.12, a + .25)], 190, 1.6)
            c.line([polar(.15, a), polar(length * .92, a)], 1.0)
        c.poly(6, .14, 0, 1.6)
        return c.render(PALETTE[tint], glow_radius=8, fill_alpha=.55)
    if tint == 'wind':
        for i in range(4):
            a0 = math.tau * i / 4
            pts = [polar(.08 + .86 * (t / 3.4), a0 + t) for t in np.linspace(0, 3.4, 140)]
            c.line(pts, 3.0)
            pts = [polar(.08 + .7 * (t / 3.4), a0 + t + .5) for t in np.linspace(.3, 3.0, 100)]
            c.line(pts, 1.2)
        return c.render(PALETTE[tint], glow_radius=9)
    if tint == 'earth':
        for i in range(9):
            a = math.tau * i / 9 + rng.uniform(-.15, .15)
            pts, r, ang = [(0, 0)], 0.0, a
            while r < .92:
                r += rng.uniform(.08, .15)
                ang += rng.uniform(-.22, .22)
                pts.append(polar(min(r, .95), ang))
                if rng.random() < .3:
                    fa = ang + rng.choice((-1, 1)) * rng.uniform(.3, .6)
                    c.line([pts[-1], polar(min(.95, r + rng.uniform(.1, .2)), fa)], 1.4)
            c.line(pts, 2.6)
        c.shape([polar(.13, math.tau * k / 7 + rng.uniform(-.2, .2)) for k in range(7)], 200, 1.6)
        return c.render(PALETTE[tint], glow_radius=6, fill_alpha=.7, core_white=.55)
    if tint == 'light':
        for i in range(16):
            a = math.tau * i / 16
            length = .97 if i % 2 == 0 else .64
            c.shape([polar(.06, a - .2), polar(length, a), polar(.06, a + .2)], 255, 1.2)
            c.line([polar(.08, a), polar(length * .96, a)], 1.6)
        c.ring(.4, 2.0)
        c.dot(0, 0, .14)
        return c.render(PALETTE[tint], glow_radius=10, fill_alpha=.95, core_white=.85)
    if tint == 'dark':
        x, y = field(size)
        r = np.hypot(x, y)
        void = np.clip(1 - r / .42, 0, 1) ** .6
        rim = np.exp(-((r - .44) / .05) ** 2)
        for i in range(10):
            a0 = math.tau * i / 10
            c.line([polar(.95 - .5 * (t / 2.2), a0 - t) for t in np.linspace(0, 2.2, 80)], 2.4 - .0)
        art = np.asarray(c.render(PALETTE[tint], glow_radius=8)).astype(np.float64) / 255
        col = art[..., :3] * (1 - void[..., None]) + np.array([.07, .02, .12]) * void[..., None]
        col = col * (1 - rim[..., None]) + main_c * rim[..., None]
        alpha = np.clip(np.maximum.reduce([art[..., 3], void * .92, rim]), 0, 1) * (r < 1)
        return to_rgba(col, alpha)
    if tint == 'lightning':
        for i in range(6):
            a = math.tau * i / 6 + rng.uniform(-.2, .2)
            pts, r, ang = [(0, 0)], 0.0, a
            while r < .9:
                r += rng.uniform(.1, .17)
                ang = a + rng.uniform(-.25, .25)
                pts.append(polar(min(r, .96), ang))
                if rng.random() < .45:
                    fa = ang + rng.choice((-1, 1)) * rng.uniform(.35, .7)
                    fork = [pts[-1], polar(min(.96, r + .12), fa), polar(min(.96, r + .2), fa + rng.uniform(-.3, .3))]
                    c.line(fork, 1.2)
            c.line(pts, 2.4)
        c.dot(0, 0, .07)
        return c.render(PALETTE[tint], glow_radius=9)
    raise ValueError(tint)


# --- bolts and beam ----------------------------------------------------------------------------------

def bolt(seed, width=256, height=256):
    """A vertical lightning strike from top to bottom in a square sprite (narrow central band), white core and blue glow."""
    big = Image.new('L', (width * 4, height * 4), 0)
    glow = Image.new('L', (width * 4, height * 4), 0)
    dc, dg = ImageDraw.Draw(big), ImageDraw.Draw(glow)
    rng = random.Random(seed)

    def path(x0, y0, y1, amp, steps):
        pts = [(x0, y0)]
        for k in range(1, steps + 1):
            t = k / steps
            pts.append((x0 + rng.uniform(-amp, amp) * (1 - abs(2 * t - 1) * .4), y0 + (y1 - y0) * t))
        return pts

    W, H = width * 4, height * 4
    main = path(W / 2, 0, H, W * .07, 16)
    main[-1] = (W / 2, H)
    dc.line(main, fill=255, width=12, joint='curve')
    dg.line(main, fill=255, width=44, joint='curve')
    for _ in range(4):
        k = rng.randrange(3, 13)
        x0, y0 = main[k]
        fork = path(x0, y0, y0 + rng.uniform(.12, .25) * H, W * .08, 5)
        dc.line(fork, fill=200, width=5, joint='curve')
        dg.line(fork, fill=200, width=22, joint='curve')
    core = np.asarray(big.resize((width, height), Image.Resampling.LANCZOS), dtype=np.float64) / 255
    halo = np.asarray(glow.resize((width, height), Image.Resampling.LANCZOS).filter(ImageFilter.GaussianBlur(5)), dtype=np.float64) / 255
    halo = np.clip(halo * 1.8, 0, 1)
    core_c, main_c, deep_c = (np.array(c, dtype=np.float64) / 255 for c in PALETTE['lightning'])
    col = main_c * halo[..., None] * (1 - core[..., None]) + core_c * core[..., None]
    col = col / np.maximum(halo[..., None] * (1 - core[..., None]) + core[..., None], 1e-6)
    alpha = np.clip(np.maximum(core, halo * .8), 0, 1)
    return to_rgba(col, alpha)


def beam(width=64, height=64):
    y = np.linspace(1, -1, height)
    xs = np.linspace(-1, 1, width)
    X, Y = np.meshgrid(xs, y)
    core = np.exp(-(X / .22) ** 2)
    body = np.exp(-(X / .62) ** 2) * .7
    ends = np.clip((1 - np.abs(Y)) / .2, 0, 1)
    alpha = np.clip((core + body) * ends, 0, 1)
    intensity = np.clip(.75 + .25 * core, 0, 1)
    out = np.zeros((height, width, 4))
    out[..., :3] = intensity[..., None]
    out[..., 3] = alpha
    return Image.fromarray(np.rint(out * 255).astype(np.uint8), 'RGBA')


# --- emitter atlas -----------------------------------------------------------------------------------

CELL = 16
ATLAS_CELLS = 8


def cell_canvas(scale=8):
    return Image.new('L', (CELL * scale, CELL * scale), 0), scale


def finish_cell(img, blur=0):
    small = img.resize((CELL, CELL), Image.Resampling.LANCZOS)
    if blur:
        small = small.filter(ImageFilter.GaussianBlur(blur))
    return np.asarray(small, dtype=np.float64) / 255


def atlas_cells():
    """{(col,row): alpha array} for the 8x8 emitter atlas. White RGB, shape in alpha."""
    cells = {}
    rng = random.Random(42)
    S = CELL * 8
    # Row 0: flame flipbook, a tongue that rises, thins and breaks up.
    for f in range(8):
        img, s = cell_canvas()
        d = ImageDraw.Draw(img)
        t = f / 7
        h = S * (.9 - .45 * t)
        w = S * (.5 - .3 * t)
        cx, base = S / 2 + math.sin(f * 1.3) * S * .04, S * .95
        pts = [(cx - w / 2, base), (cx - w * .45, base - h * .45), (cx - w * .15 * (1 - t), base - h * .8), (cx + math.sin(f) * S * .05, base - h),
               (cx + w * .2, base - h * .75), (cx + w * .45, base - h * .4), (cx + w / 2, base)]
        d.polygon(pts, fill=255)
        if t > .4:
            for _ in range(int(6 * t)):
                x, y = rng.uniform(S * .2, S * .8), rng.uniform(S * .2, S * .9)
                r = S * .06
                d.ellipse((x - r, y - r, x + r, y + r), fill=0)
        cells[(f, 0)] = finish_cell(img.filter(ImageFilter.GaussianBlur(S * .03)))
    # Row 1: smoke puff flipbook, soft lumpy disc that grows and dissolves.
    for f in range(8):
        img, s = cell_canvas()
        d = ImageDraw.Draw(img)
        t = f / 7
        for k in range(7):
            a = math.tau * k / 7 + f * .2
            r = S * (.18 + .12 * t)
            x, y = S / 2 + math.cos(a) * S * .14, S / 2 + math.sin(a) * S * .14
            d.ellipse((x - r, y - r, x + r, y + r), fill=int(255 * (1 - .65 * t)))
        cells[(f, 1)] = finish_cell(img.filter(ImageFilter.GaussianBlur(S * .07)))
    # Row 2: 0-3 four-point twinkle at growing sizes; 4-7 round glints.
    for f in range(4):
        x, y = field(S)
        r = np.hypot(x, y)
        k = .015 + .01 * f
        star = np.exp(-(np.abs(x) * np.abs(y)) / k * 30) * np.clip(1 - r, 0, 1) ** (2 - f * .3)
        core = np.exp(-(r / (.12 + .04 * f)) ** 2)
        img = Image.fromarray(np.rint(np.clip(star + core, 0, 1) * 255).astype(np.uint8), 'L')
        cells[(f, 2)] = finish_cell(img)
    for f in range(4):
        x, y = field(S)
        r = np.hypot(x, y)
        g = np.exp(-(r / (.25 + .12 * f)) ** 2) + np.exp(-(r / .1) ** 2)
        cells[(4 + f, 2)] = finish_cell(Image.fromarray(np.rint(np.clip(g, 0, 1) * 255).astype(np.uint8), 'L'))
    x, y = field(S)
    r = np.hypot(x, y)

    def arr(a):
        return finish_cell(Image.fromarray(np.rint(np.clip(a, 0, 1) * 255).astype(np.uint8), 'L'))
    # Row 3: soft dot, ember, droplet, bubble, snowflake a/b, shard a/b.
    cells[(0, 3)] = arr(np.exp(-(r / .55) ** 2) * (r < 1))
    cells[(1, 3)] = arr(np.exp(-(r / .18) ** 2) + np.exp(-(r / .5) ** 2) * .5)
    drop = np.hypot(x * 1.4, (y + .25) * 1.0) < .55
    drop |= (np.abs(x) < .45 * np.clip((.75 - y) / 1.2, 0, 1)) & (y > -.25) & (y < .75)
    cells[(2, 3)] = arr(np.asarray(Image.fromarray((drop * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(3))) / 255)
    cells[(3, 3)] = arr(np.exp(-((r - .7) / .12) ** 2) + np.exp(-(np.hypot(x + .3, y - .3) / .12) ** 2) * .9)
    for col, arms in ((4, 6), (5, 6)):
        img, s = cell_canvas()
        d = ImageDraw.Draw(img)
        for k in range(arms):
            a = math.tau * k / arms + (0 if col == 4 else math.pi / 6)
            d.line((S / 2, S / 2, S / 2 + math.cos(a) * S * .45, S / 2 + math.sin(a) * S * .45), fill=255, width=int(S * .07))
            if col == 4:
                for u in (.25, .33):
                    bx, by = S / 2 + math.cos(a) * S * u, S / 2 + math.sin(a) * S * u
                    for sgn in (-1, 1):
                        d.line((bx, by, bx + math.cos(a + sgn * .8) * S * .12, by + math.sin(a + sgn * .8) * S * .12), fill=255, width=int(S * .05))
        cells[(col, 3)] = finish_cell(img.filter(ImageFilter.GaussianBlur(S * .01)))
    for col, lean in ((6, 0), (7, .3)):
        img, s = cell_canvas()
        d = ImageDraw.Draw(img)
        d.polygon([(S * (.5 + lean * .2), S * .02), (S * .66, S * .5), (S * (.5 - lean * .1), S * .98), (S * .36, S * .5)], fill=255)
        cells[(col, 3)] = finish_cell(img.filter(ImageFilter.GaussianBlur(S * .015)))
    # Row 4: leaf a/b, rock a/b/c, dust puff, wisp a/b.
    for col, bend in ((0, .15), (1, -.2)):
        img, s = cell_canvas()
        d = ImageDraw.Draw(img)
        right = [(S * (.5 + bend * math.sin(math.pi * t) + .3 * math.sin(math.pi * t) ** .8), S * (.05 + .9 * t)) for t in np.linspace(0, 1, 30)]
        left = [(S * (.5 + bend * math.sin(math.pi * t) - .3 * math.sin(math.pi * t) ** .8), S * (.05 + .9 * t)) for t in np.linspace(1, 0, 30)]
        d.polygon(right + left, fill=255)
        d.line([(S * (.5 + bend * math.sin(math.pi * t)), S * (.08 + .84 * t)) for t in np.linspace(0, 1, 12)], fill=150, width=int(S * .035))
        cells[(col, 4)] = finish_cell(img)
    for col in (2, 3, 4):
        img, s = cell_canvas()
        d = ImageDraw.Draw(img)
        n = 6 + col
        pts = [(S / 2 + math.cos(math.tau * k / n) * S * rng.uniform(.28, .45), S / 2 + math.sin(math.tau * k / n) * S * rng.uniform(.28, .45)) for k in range(n)]
        d.polygon(pts, fill=255)
        # A darker facet gives each chip some volume once tinted.
        d.polygon([pts[0], pts[1], pts[2], (S / 2, S / 2)], fill=170)
        cells[(col, 4)] = finish_cell(img)
    cells[(5, 4)] = arr(np.clip(np.exp(-(r / .6) ** 2) * (.7 + .3 * np.sin(x * 9) * np.cos(y * 7)), 0, 1))
    for col, sign in ((6, 1), (7, -1)):
        img, s = cell_canvas()
        d = ImageDraw.Draw(img)
        pts = [(S * (.15 + .7 * t), S * (.5 + sign * .28 * math.sin(math.pi * t * 1.3))) for t in np.linspace(0, 1, 30)]
        d.line(pts, fill=255, width=int(S * .12), joint='curve')
        cells[(col, 4)] = finish_cell(img.filter(ImageFilter.GaussianBlur(S * .05)))
    # Row 5: streak, arc, small ring, heart, cross, petal, feather, rune.
    cells[(0, 5)] = arr(np.exp(-(y / .14) ** 2) * np.clip(1 - np.abs(x), 0, 1) ** .6 * (.4 + .6 * np.clip((x + 1) / 2, 0, 1)))
    img, s = cell_canvas()
    d = ImageDraw.Draw(img)
    d.arc((S * .1, S * .1, S * .9, S * .9), 200, 340, fill=255, width=int(S * .1))
    cells[(1, 5)] = finish_cell(img.filter(ImageFilter.GaussianBlur(S * .02)))
    cells[(2, 5)] = arr(np.exp(-((r - .75) / .1) ** 2))
    heart = ((x ** 2 + (y - .25 * np.sqrt(np.abs(x)) * 1.3 + .1) ** 2) < .55)
    cells[(3, 5)] = finish_cell(Image.fromarray((heart * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(4)))
    cross = ((np.abs(x) < .17) & (np.abs(y) < .75)) | ((np.abs(y) < .17) & (np.abs(x) < .75))
    cells[(4, 5)] = finish_cell(Image.fromarray((cross * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(3)))
    petal = (x / .42) ** 2 + ((y - .05) / .85) ** 2 < 1
    cells[(5, 5)] = finish_cell(Image.fromarray((petal * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(4)))
    img, s = cell_canvas()
    d = ImageDraw.Draw(img)
    d.line((S * .2, S * .9, S * .75, S * .1), fill=255, width=int(S * .05))
    for k in range(9):
        t = .15 + k * .08
        px, py = S * (.2 + .55 * t), S * (.9 - .8 * t)
        d.line((px, py, px - S * .2, py - S * .05), fill=220, width=int(S * .04))
        d.line((px, py, px + S * .05, py + S * .2 * (1 - t)), fill=220, width=int(S * .04))
    cells[(6, 5)] = finish_cell(img.filter(ImageFilter.GaussianBlur(S * .01)))
    img, s = cell_canvas()
    d = ImageDraw.Draw(img)
    for seg in ((.2, .15, .8, .15), (.5, .15, .5, .85), (.25, .55, .75, .55), (.2, .85, .8, .85)):
        d.line(tuple(v * S for v in seg), fill=255, width=int(S * .09))
    cells[(7, 5)] = finish_cell(img.filter(ImageFilter.GaussianBlur(S * .02)))
    # Row 6: electric arc flipbook (jagged segments).
    for f in range(8):
        img, s = cell_canvas()
        d = ImageDraw.Draw(img)
        pts = [(S * .05, S * .5)]
        for k in range(1, 7):
            pts.append((S * (.05 + .9 * k / 6), S * (.5 + rng.uniform(-.32, .32) * (1 - abs(k / 3 - 1)) )))
        pts[-1] = (S * .95, S * .5)
        d.line(pts, fill=255, width=int(S * .07))
        cells[(f, 6)] = finish_cell(img.filter(ImageFilter.GaussianBlur(S * .015)))
    # Row 7: rune glyph variants for orbiting sigils.
    for f in range(8):
        img, s = cell_canvas()
        d = ImageDraw.Draw(img)
        nodes = [(S * u, S * v) for v in (.15, .5, .85) for u in (.2, .5, .8)]
        for _ in range(3):
            a, b = rng.sample(nodes, 2)
            d.line((*a, *b), fill=255, width=int(S * .09))
        cells[(f, 7)] = finish_cell(img.filter(ImageFilter.GaussianBlur(S * .02)))
    return cells


def atlas():
    alpha = np.zeros((CELL * ATLAS_CELLS, CELL * ATLAS_CELLS))
    for (col, row), cell in atlas_cells().items():
        alpha[row * CELL:(row + 1) * CELL, col * CELL:(col + 1) * CELL] = cell
    out = np.ones(alpha.shape + (4,))
    out[..., 3] = np.clip(alpha, 0, 1)
    return Image.fromarray(np.rint(out * 255).astype(np.uint8), 'RGBA')


def sprites():
    """{name: image} for every full-colour or greyscale sprite in the kit."""
    out = {}
    for tint in TINTS:
        out[f'fx_circle_{tint}'] = circle(tint)
        out[f'fx_glow_{tint}'] = glow(tint)
        out[f'fx_burst_{tint}'] = burst(tint)
    for i, name in enumerate(('a', 'b', 'c')):
        out[f'fx_bolt_{name}'] = bolt(100 + i * 17)
    out['fx_beam'] = beam()
    return out
