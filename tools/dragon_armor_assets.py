"""Dragon Conqueror armor: Infernal Drake, Frostwyrm and Tempest Drake.

Three boss-reward sets drawn on the wearer like vanilla armor (see armor_set_forge): two 4x armor
layers, a cape on the chestplate (Java's elytra layer, rebuilt as cape bones on Bedrock) and a
sculpted voxel helm whose every face is baked with light, occlusion and edge highlights
(armor_helm_bake). Each set has its own silhouette so it reads from across a field:

* Infernal Drake -- charred black scale plate split by molten cracks, a dragon-skull helm with
  massive C-curved horns, an ember crest and a fanged, glowing maw; a fire-edged wing cape.
* Frostwyrm -- frost-silver plate over navy, white fur ruffs and a crown of tall ice crystals
  with swept crystal frills; an icy cape that ends in hanging shards.
* Tempest Drake -- pearl plate with gold filigree over violet, lightning-bolt horns of gold,
  feathered wing fins and a crackling crest; a royal storm cape with a gold hem.

The armor layers are painted on Java's 64x32 armor layout at 4x. Depth is painted, since both
editions draw armor layers on flat boxes: plates cast soft shadows, carry lit top/left and dark
lower/right bevels, curve with the limb and catch a specular streak; dragon scales overlap like
roof tiles, each throwing a shadow on the row below.
"""
from __future__ import annotations

from dataclasses import dataclass
import itertools
import math
from pathlib import Path
import zlib

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

import armor_set_forge as forge
from armor_set_forge import ArmorSet, K
import armor_helm_bake as bake
from seraph_armor_assets import faces

ROOT = Path(__file__).resolve().parents[1]
W, H = 64 * K, 32 * K


def pieces(prefix, helm='Helm', chest='Cuirass', legs='Greaves', boots='Sabatons'):
    # Twice netherite's armor: 6 / 16 / 12 / 6 (the game clamps the total at 30 armor, 20 toughness).
    return (('helm', f'{prefix} {helm}', 'netherite_helmet', 'head', 6),
            ('chestplate', f'{prefix} {chest}', 'netherite_chestplate', 'chest', 16),
            ('leggings', f'{prefix} {legs}', 'netherite_leggings', 'legs', 12),
            ('boots', f'{prefix} {boots}', 'netherite_boots', 'feet', 6))


def pal(deep, shade, base, mid, hi, spec, rim):
    return dict(deep=deep, shade=shade, base=base, mid=mid, hi=hi, spec=spec, rim=rim)


@dataclass
class Theme:
    key: str
    plate: dict
    dark: dict
    scale: dict
    trim: dict
    cloth: dict
    under: tuple
    glow: dict          # halo, core, hot, dim
    gem: tuple          # dark, mid, light, pale
    fur: dict = None
    ice: dict = None


INFERNAL = Theme(
    key='infernal_drake',
    plate=pal((5, 4, 5), (14, 11, 12), (27, 20, 20), (44, 33, 31), (78, 58, 51), (152, 118, 100), (216, 184, 160)),
    dark=pal((3, 2, 3), (8, 6, 7), (15, 11, 11), (25, 18, 17), (42, 29, 25), (86, 60, 48), (142, 102, 80)),
    scale=pal((9, 3, 3), (22, 6, 5), (42, 10, 8), (68, 16, 10), (114, 30, 14), (196, 78, 32), (255, 140, 60)),
    trim=pal((30, 14, 6), (64, 32, 12), (112, 62, 24), (154, 94, 38), (204, 140, 64), (246, 200, 132), (255, 234, 194)),
    cloth=pal((14, 2, 2), (32, 5, 4), (58, 9, 6), (86, 14, 8), (124, 24, 12), (178, 50, 20), (230, 96, 40)),
    under=(15, 8, 8),
    glow=dict(halo=(255, 86, 8), core=(255, 164, 36), hot=(255, 238, 168), dim=(118, 22, 3)),
    gem=((130, 24, 4), (240, 92, 14), (255, 178, 52), (255, 240, 186)),
)

FROST = Theme(
    key='frostwyrm',
    plate=pal((30, 40, 74), (60, 78, 122), (108, 130, 176), (150, 172, 212), (196, 214, 240), (232, 244, 255), (255, 255, 255)),
    dark=pal((6, 10, 26), (13, 20, 46), (24, 36, 74), (37, 54, 104), (62, 88, 148), (110, 142, 204), (162, 196, 240)),
    scale=pal((22, 34, 70), (44, 64, 112), (80, 106, 158), (116, 146, 196), (164, 194, 232), (220, 236, 255), (246, 252, 255)),
    trim=pal((60, 70, 96), (104, 116, 144), (156, 168, 194), (192, 204, 226), (226, 234, 248), (248, 252, 255), (255, 255, 255)),
    cloth=pal((6, 16, 44), (12, 32, 82), (22, 58, 130), (38, 96, 176), (70, 146, 218), (140, 206, 250), (206, 240, 255)),
    under=(11, 15, 32),
    glow=dict(halo=(48, 190, 255), core=(140, 228, 255), hot=(236, 252, 255), dim=(18, 66, 140)),
    gem=((16, 74, 168), (52, 160, 238), (150, 224, 255), (236, 250, 255)),
    fur=pal((106, 118, 150), (150, 164, 196), (196, 208, 230), (222, 230, 244), (240, 245, 252), (252, 254, 255), (255, 255, 255)),
    ice=pal((26, 86, 170), (52, 140, 220), (104, 190, 244), (150, 220, 255), (200, 242, 255), (238, 252, 255), (255, 255, 255)),
)

TEMPEST = Theme(
    key='tempest_drake',
    plate=pal((66, 58, 100), (112, 104, 150), (168, 162, 200), (202, 198, 226), (228, 226, 244), (248, 246, 255), (255, 255, 255)),
    dark=pal((12, 5, 26), (26, 12, 52), (46, 22, 88), (70, 36, 128), (108, 62, 180), (162, 116, 232), (206, 168, 255)),
    scale=pal((40, 26, 76), (70, 50, 118), (110, 88, 162), (146, 126, 196), (186, 170, 226), (230, 222, 250), (250, 246, 255)),
    trim=pal((60, 34, 8), (114, 72, 16), (178, 124, 34), (216, 164, 52), (246, 204, 94), (255, 236, 160), (255, 250, 220)),
    cloth=pal((12, 4, 26), (28, 10, 58), (50, 20, 102), (76, 34, 146), (112, 58, 196), (168, 110, 240), (214, 172, 255)),
    under=(17, 10, 30),
    glow=dict(halo=(160, 76, 255), core=(214, 160, 255), hot=(248, 238, 255), dim=(64, 18, 124)),
    gem=((68, 18, 140), (144, 66, 236), (204, 150, 255), (246, 232, 255)),
)


# ---------------------------------------------------------------------------
# Painting toolkit (face-local pixels at scale K)


def ramp(p, value):
    stops = np.array([0, .28, .52, .72, .92, 1.15])
    cols = np.array([p[k] for k in ('deep', 'shade', 'base', 'mid', 'hi', 'spec')], float)
    v = np.clip(value, 0, 1.15)
    return np.stack([np.interp(v, stops, cols[:, c]) for c in range(3)], -1)


def shifted(m, dx, dy):
    out = np.zeros_like(m)
    h, w = m.shape
    dx, dy = int(dx), int(dy)
    ys, yd = (slice(0, h - dy), slice(dy, h)) if dy >= 0 else (slice(-dy, h), slice(0, h + dy))
    xs, xd = (slice(0, w - dx), slice(dx, w)) if dx >= 0 else (slice(-dx, w), slice(0, w + dx))
    out[yd, xd] = m[ys, xs]
    return out


def mirror(points, w):
    return [(w - x, y) for x, y in points]


class Paint:
    LIGHT = {'front': 1.0, 'outer': .86, 'inner': .66, 'back': .78, 'top': 1.1, 'bottom': .5}

    def __init__(self, w, h, side, seed, theme: Theme):
        self.w, self.h = int(w), int(h)
        self.px = np.zeros((self.h, self.w, 4), float)
        self.light = self.LIGHT[side]
        self.side = side
        self.t = theme
        self.rng = np.random.default_rng(seed)
        yy, xx = np.mgrid[0:self.h, 0:self.w]
        self.X, self.Y = xx + .5, yy + .5

    # -- masks and compositing ------------------------------------------------
    def mask(self, shape, width=None):
        img = Image.new('L', (self.w, self.h))
        d = ImageDraw.Draw(img)
        pts = [tuple(p) for p in shape]
        if width:
            d.line(pts, fill=255, width=max(1, int(round(width))), joint='curve')
        elif len(pts) == 2:
            d.rectangle((pts[0][0], pts[0][1], pts[1][0] - 1, pts[1][1] - 1), fill=255)
        else:
            d.polygon(pts, fill=255)
        return np.asarray(img) > 127

    def painted(self):
        return self.px[..., 3] > 0

    def over(self, color, alpha, mask=None):
        a = np.broadcast_to(np.asarray(alpha, float), (self.h, self.w)).copy()
        if mask is not None:
            a = a * mask
        a = np.clip(a, 0, 1)[..., None]
        col = np.broadcast_to(np.asarray(color, float), (self.h, self.w, 3))
        self.px[..., :3] = self.px[..., :3] * (1 - a) + col * a
        self.px[..., 3] = np.maximum(self.px[..., 3], a[..., 0] * 255)

    def add(self, color, amount):
        a = np.clip(amount, 0, 1)[..., None] * (self.px[..., 3:] > 0)
        self.px[..., :3] = np.minimum(255, self.px[..., :3] + np.asarray(color, float) * a)

    def clear(self, shape=None, mask=None):
        m = mask if mask is not None else self.mask(shape)
        self.px[m] = 0

    def soft(self, mask, radius):
        g = Image.fromarray((mask * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(radius))
        return np.asarray(g, float) / 255

    # -- materials --------------------------------------------------------------
    def under(self, shape=None):
        """Dark padded undersuit: fine quilted weave."""
        m = np.ones((self.h, self.w), bool) if shape is None else self.mask(shape)
        weave = ((self.X + self.Y) % 4 < 1.1) * .35 + ((self.X - self.Y) % 6 < 1.1) * .22
        col = np.asarray(self.t.under, float)[None, None] * (1 + weave[..., None] * .9) * self.light
        self.px[..., :3] = np.where(m[..., None], col, self.px[..., :3])
        self.px[..., 3] = np.where(m, 255, self.px[..., 3])

    def plate(self, shape, p=None, curve=.3, spec=.3, shadow=.55, bevel=1, tilt=0.0, mask=None, grade=.42):
        """A plate with its cast shadow, form shading, bevels and a sheen."""
        p = p or self.t.plate
        m = self.mask(shape) if mask is None else mask
        if not m.any():
            return m
        if shadow:
            for dy, a in ((2, shadow), (4, shadow * .45), (6, shadow * .2)):
                s = shifted(m, 1, dy) & ~m
                self.over((0, 0, 0), a, s & self.painted())
        ys, xs = np.nonzero(m)
        y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
        t = (self.Y - y0) / max(1, y1 - y0)
        u = (self.X - (x0 + x1) / 2) / max(1, (x1 - x0) / 2)
        value = self.light * (1.02 - grade * t + tilt * u) * (1 - curve * u ** 2)
        value = value + self.rng.normal(0, .015, value.shape)
        col = ramp(p, value)
        if spec:
            band = (self.X - x0) * .55 + (self.Y - y0) - (y1 - y0) * .18
            streak = np.clip(1 - np.abs(band) / 2.2, 0, 1) * (t < .6)
            col = col + (np.asarray(p['spec'], float) - col) * (streak * spec * self.light)[..., None]
        self.px[..., :3] = np.where(m[..., None], col, self.px[..., :3])
        self.px[..., 3] = np.where(m, 255, self.px[..., 3])
        if bevel:
            top = m & ~shifted(m, 0, 1)
            left = m & ~shifted(m, 1, 0)
            bottom = m & ~shifted(m, 0, -1)
            right = m & ~shifted(m, -1, 0)
            small = (y1 - y0) < 9
            self.over(p['hi'] if small else p['rim'], (.6 if small else .72) * self.light, top)
            self.over(p['hi'], .45, left & ~top)
            self.over(p['deep'], .9, bottom)
            self.over(p['shade'], .75, right & ~bottom)
            self.over(p['shade'], .5, shifted(bottom, 0, -1) & m & ~bottom)
        return m

    def trim_edge(self, m, p=None, width=1, top=True, bottom=False):
        """Gilded edge along a plate mask's outline."""
        p = p or self.t.trim
        edge = np.zeros_like(m)
        for dx, dy in ((0, 1), (1, 0), (-1, 0), (0, -1)):
            for k in range(1, width + 1):
                edge |= m & ~shifted(m, dx * k, dy * k)
        self.over(p['base'], 1, edge)
        lit = edge & ~shifted(m, 0, 1) if top else edge & ~shifted(m, 1, 0)
        self.over(p['spec'], .85, lit)
        self.over(p['shade'], .8, edge & ~shifted(m, 0, -1))
        return edge

    def ridge(self, a, b, half_width, p=None):
        p = p or self.t.plate
        (ax, ay), (bx, by) = a, b
        nx, ny = by - ay, -(bx - ax)
        n = math.hypot(nx, ny) or 1
        nx, ny = nx / n * half_width, ny / n * half_width
        lit = [(ax - nx, ay - ny), (ax, ay), (bx, by), (bx - nx, by - ny)]
        dark = [(ax, ay), (ax + nx, ay + ny), (bx + nx, by + ny), (bx, by)]
        self.plate(lit, p, curve=0, spec=0, shadow=.35, tilt=.25)
        self.plate(dark, p, curve=0, spec=0, shadow=0, tilt=-.55)
        self.over(p['rim'], .9, self.mask([a, b], width=1))

    def spike(self, a, b, tip, p=None, glow_edge=False, edges=True, lit=.35, shadow=True):
        p = p or self.t.plate
        mid = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
        m = self.mask([a, b, tip])
        if shadow:
            for dy, al in ((2, .5), (4, .22)):
                self.over((0, 0, 0), al, shifted(m, 1, dy) & ~m & self.painted())
        self.plate([a, mid, tip], p, curve=0, spec=.2 if edges else 0, shadow=0, bevel=0, tilt=lit)
        self.plate([mid, b, tip], p, curve=0, spec=0, shadow=0, bevel=0, tilt=-.75)
        if edges:
            self.over(p['rim'], .9, self.mask([a, tip], width=1))
            self.over(p['deep'], .8, self.mask([b, tip], width=1))
        if glow_edge:
            self.vein([b, tip], 1, halo=3)

    def teeth(self, y, x0, x1, n, depth, p=None, down=True):
        p = p or self.t.dark
        step = (x1 - x0) / n
        for i in range(n):
            a, b = x0 + i * step, x0 + (i + 1) * step
            tip = ((a + b) / 2 + step * .12, y + depth if down else y - depth)
            self.spike((a, y), (b, y), tip, p, edges=False, lit=-.2)

    def groove(self, points, width=1, p=None):
        p = p or self.t.plate
        pts = [tuple(q) for q in points]
        self.over(p['deep'], .9, self.mask(pts, width=width) & self.painted())
        self.over(p['hi'], .5, self.mask([(x + .6, y + 1) for x, y in pts], width=1) & self.painted())

    def filigree(self, points, width=1, p=None):
        """A raised gold line: bright crest with a dark underside."""
        p = p or self.t.trim
        pts = [tuple(q) for q in points]
        self.over(p['deep'], .8, self.mask([(x + .5, y + 1) for x, y in pts], width=width) & self.painted())
        self.over(p['base'], 1, self.mask(pts, width=width))
        self.over(p['spec'], .7, self.mask([(x - .3, y - .4) for x, y in pts], width=1) & self.mask(pts, width=width))

    def vein(self, points, width=1, halo=4, strength=2.1):
        """A glowing crack: dark rim, burning core and a soft halo bleeding over the plate."""
        g = self.t.glow
        pts = [tuple(q) for q in points]
        core = self.mask(pts, width=width)
        self.add(g['halo'], self.soft(core, halo) * strength)
        self.over(g['dim'], .9, self.mask(pts, width=width + 2) & ~core & self.painted())
        self.over(g['core'], 1, core)
        self.over(g['hot'], .6, core & shifted(core, 0, 1))

    def bolt(self, a, b, jag=2.4, steps=6, width=1, halo=3.2, branch=True, seed=None):
        """A lightning bolt between two points with a forked branch."""
        rng = np.random.default_rng(seed) if seed is not None else self.rng
        pts = [a]
        for i in range(1, steps):
            t = i / steps
            x = a[0] + (b[0] - a[0]) * t + (jag if i % 2 else -jag) * (.6 + .4 * rng.random())
            y = a[1] + (b[1] - a[1]) * t + rng.normal(0, .4)
            pts.append((x, y))
        pts.append(b)
        self.vein(pts, width, halo)
        if branch and len(pts) > 3:
            s = pts[len(pts) // 2]
            end = (s[0] + (jag * 2.2 if rng.random() > .5 else -jag * 2.2), s[1] + abs(b[1] - a[1]) * .3)
            self.vein([s, ((s[0] + end[0]) / 2 + rng.normal(0, .8), (s[1] + end[1]) / 2), end], 1, halo * .6, 1.3)

    def glow(self, cx, cy, r, strength=1.0):
        d = np.hypot(self.X - cx, self.Y - cy) / r
        self.add(self.t.glow['halo'], np.clip(1 - d, 0, 1) ** 1.6 * strength)

    def gem(self, cx, cy, r, setting=None, halo=2.6):
        """A crystal core in an angular setting, lit from within."""
        dark, mid, light, pale = self.t.gem
        setting = setting or self.t.trim
        self.glow(cx, cy, r * halo, .9)
        bezel = [(cx, cy - r * 1.45), (cx + r * 1.25, cy - r * .2), (cx + r * .8, cy + r * 1.2), (cx - r * .8, cy + r * 1.2),
                 (cx - r * 1.25, cy - r * .2)]
        self.plate(bezel, setting, curve=.2, spec=.35, shadow=.6)
        stone = [(cx, cy - r), (cx + r * .85, cy - r * .1), (cx + r * .55, cy + r * .85), (cx - r * .55, cy + r * .85),
                 (cx - r * .85, cy - r * .1)]
        self.over(dark, 1, self.mask(stone))
        self.over(light, 1, self.mask([(cx, cy - r), (cx - r * .85, cy - r * .1), (cx, cy + r * .2)]))
        self.over(mid, 1, self.mask([(cx, cy - r), (cx + r * .85, cy - r * .1), (cx, cy + r * .2)]))
        self.over(pale, 1, self.mask([(cx - r * .3, cy - r * .35), (cx + r * .25, cy - r * .35), (cx, cy + r * .15)]))
        self.add(self.t.glow['hot'], np.clip(1 - np.hypot(self.X - cx, self.Y - cy) / (r * .55), 0, 1) * .9)
        self.over((255, 255, 255), 1, self.mask([(cx - r * .45, cy - r * .55), (cx - r * .15, cy - r * .55), (cx - r * .3, cy - r * .3)]))

    def rivet(self, cx, cy, r=1.2, p=None):
        p = p or self.t.trim
        self.over(p['deep'], 1, np.hypot(self.X - cx - .5, self.Y - cy - .7) <= r)
        self.over(p['mid'], 1, np.hypot(self.X - cx, self.Y - cy) <= r)
        self.over(p['spec'], 1, np.hypot(self.X - cx + .4, self.Y - cy + .4) <= r * .45)

    def scales(self, shape=None, p=None, sw=7.0, sh=4.6, phase=0.0, region=None, lit=0.0, gloss=.35):
        """Dragon scales in overlapping rows; every row shadows the one below."""
        p = p or self.t.scale
        area = region if region is not None else (np.ones((self.h, self.w), bool) if shape is None else self.mask(shape))
        if not area.any():
            return
        ys = np.nonzero(area)[0]
        top, bottom = ys.min() - sh, ys.max() + 1
        rows = list(np.arange(top, bottom, sh))
        for r, y0 in reversed(list(enumerate(rows))):
            off = phase + (sw / 2 if r % 2 else 0)
            u = ((self.X - off) % sw) / sw - .5
            v = (self.Y - y0) / sh
            edge = .62 + .74 * np.sqrt(np.clip(1 - (2 * u) ** 2, 0, 1))
            m = (v >= 0) & (v <= edge) & area
            if not m.any():
                continue
            for dy, al in ((1, .55), (2, .3)):
                self.over((0, 0, 0), al, shifted(m, 1, dy) & ~m & self.painted() & area)
            s = np.clip(v / edge, 0, 1)
            value = self.light * (.46 + .52 * s ** .8) * (1 - .55 * u ** 2) + lit * u + self.rng.normal(0, .02, u.shape)
            col = ramp(p, value)
            shine = np.clip(1 - np.hypot((u + .14) / .2, (s - .7) / .16), 0, 1) * gloss
            col = col + (np.asarray(p['spec'], float) - col) * shine[..., None]
            self.px[..., :3] = np.where(m[..., None], col, self.px[..., :3])
            self.px[..., 3] = np.where(m, 255, self.px[..., 3])
            rim = m & (s > .86)
            self.over(p['hi'], .7 * self.light, rim)
            self.over(p['deep'], .95, m & ~shifted(m, 0, -1))

    def fur(self, shape, p=None, tuft=3.2, density=1.4, down=True, region=None):
        """A fluffy fur band: shaded mass, fine strands and a tufted, alpha-cut lower edge."""
        p = p or self.t.fur
        m = self.mask(shape) if region is None else region
        if not m.any():
            return
        ys, xs = np.nonzero(m)
        y0, y1 = ys.min(), ys.max() + 1
        # Tufts: round lobes hanging off the lower edge.
        lobes = np.zeros_like(m)
        edge_y = {}
        for x in range(xs.min(), xs.max() + 1):
            col = np.nonzero(m[:, x])[0]
            if len(col):
                edge_y[x] = col.max() if down else col.min()
        x = xs.min()
        while x <= xs.max():
            r = tuft * (.75 + .5 * self.rng.random())
            if x in edge_y:
                cy = edge_y[x] + (r * .3 if down else -r * .3)
                lobes |= np.hypot((self.X - x) / (r * .9), (self.Y - cy) / r) <= 1
            x += r * 1.2
        m = m | lobes
        t = np.clip((self.Y - y0) / max(1, y1 - y0 + tuft), 0, 1)
        value = self.light * (1.05 - .5 * t) + self.rng.normal(0, .05, t.shape)
        col = ramp(p, value)
        self.px[..., :3] = np.where(m[..., None], col, self.px[..., :3])
        self.px[..., 3] = np.where(m, 255, self.px[..., 3])
        n = int(m.sum() * density / 6)
        idx = np.argwhere(m)
        for k in self.rng.choice(len(idx), size=min(n, len(idx)), replace=False):
            y, x = idx[k]
            length = 2 + self.rng.random() * 3
            dx = self.rng.normal(0, .7)
            shade = self.rng.random()
            colour = p['spec'] if shade > .6 else p['shade'] if shade < .3 else p['hi']
            self.over(colour, .7, self.mask([(x, y), (x + dx, y + (length if down else -length))], width=1) & m)
        self.over(p['deep'], .5, m & ~shifted(m, 0, -1))
        self.over((0, 0, 0), .45, shifted(m, 0, 2) & ~m & self.painted())

    def shard(self, a, b, tip, p=None, glow=.6):
        """An ice crystal shard: a lit and a shadowed facet, white edge, inner glow."""
        p = p or self.t.ice or self.t.plate
        mid = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
        m = self.mask([a, b, tip])
        for dy, al in ((2, .45), (4, .2)):
            self.over((0, 0, 0), al, shifted(m, 1, dy) & ~m & self.painted())
        if glow:
            self.add(self.t.glow['halo'], self.soft(m, 2.4) * glow)
        self.plate([a, mid, tip], p, curve=0, spec=.4, shadow=0, bevel=0, tilt=.5, grade=-.15)
        self.plate([mid, b, tip], p, curve=0, spec=0, shadow=0, bevel=0, tilt=-.6, grade=.1)
        self.over(p['rim'], 1, self.mask([a, tip], width=1))
        self.over(p['deep'], .7, self.mask([b, tip], width=1))
        self.over(self.t.glow['core'], .55, self.mask([mid, tip], width=1))

    def flames(self, y, x0, x1, n, height, seed=0, up=True):
        """Licking flame tongues rising from a line (Infernal plates and hems)."""
        rng = np.random.default_rng(seed)
        g = self.t.glow
        step = (x1 - x0) / n
        for i in range(n):
            a = x0 + i * step
            h = height * (.6 + .5 * rng.random())
            lean = rng.normal(0, step * .25)
            sgn = -1 if up else 1
            outer = [(a - step * .1, y), (a + step * .35 + lean * .5, y + sgn * h * .55), (a + step * .5 + lean, y + sgn * h),
                     (a + step * .7 + lean * .3, y + sgn * h * .45), (a + step * 1.1, y)]
            inner = [(a + step * .25, y), (a + step * .45 + lean * .5, y + sgn * h * .55), (a + step * .8, y)]
            mo = self.mask(outer)
            self.add(g['halo'], self.soft(mo, 2) * 1.2)
            self.over(g['dim'], 1, mo)
            self.over(g['halo'], 1, self.mask(outer[1:4] + [(a + step * .5, y)]))
            self.over(g['core'], 1, self.mask(inner))
            self.over(g['hot'], .9, self.mask([(a + step * .4, y), (a + step * .5 + lean * .4, y + sgn * h * .3), (a + step * .65, y)]))

    def image(self):
        return Image.fromarray(np.clip(self.px, 0, 255).astype(np.uint8), 'RGBA')


def paste(canvas, face, rect):
    x, y, _, _ = rect
    canvas.alpha_composite(face.image(), (x * K, y * K))


def make(theme, rect, side, seed):
    return Paint(rect[2] * K, rect[3] * K, side, seed, theme)


# ---------------------------------------------------------------------------
# Layer 1: cuirass (body + arms) and sabatons (lower leg box)


def collar(f, part):
    t, w = f.t, f.w
    if t is FROST:
        f.plate([(0, 0), (w, 0), (w, 5), (0, 5)], t.dark, curve=.1, spec=.2)
        f.fur([(0, 0), (w, 0), (w, 6), (0, 6)], tuft=3.0)
    elif t is TEMPEST:
        m = f.plate([(0, 0), (w, 0), (w, 5.5), (0, 5.5)], t.plate, curve=.1, spec=.35)
        f.trim_edge(m)
        if part == 'front':
            f.plate([(w / 2 - 5, 0), (w / 2 + 5, 0), (w / 2, 6.5)], t.dark, curve=0, spec=.2, bevel=0)
            f.vein([(w / 2 - 3.5, 1.2), (w / 2, 5), (w / 2 + 3.5, 1.2)], 1, halo=2.2)
    else:
        f.plate([(0, 0), (w, 0), (w, 5), (0, 5)], t.dark, curve=.1, spec=.2)
        f.teeth(5, 0, w, max(3, int(w / 5.5)), 2.8, t.dark)
        f.vein([(1, 2.4), (w - 1, 2.4)], 1, halo=1.6, strength=1.2)


def emblem(f):
    """The heart of the cuirass: each set's signature crest."""
    t, w = f.t, f.w
    c = w / 2
    if t is INFERNAL:
        # Flame plates flare out of a horned dragon-heart bezel.
        for k, (dx, top) in enumerate(((5.5, 8.5), (9.5, 11.5), (13, 15))):
            for s in (-1, 1):
                f.spike((c + s * 1.5, 19.5), (c + s * (dx - 1.2), 21.5), (c + s * dx, top), t.dark, lit=.35 if s < 0 else -.4)
        f.plate([(c - 6, 12), (c - 2.5, 9.5), (c + 2.5, 9.5), (c + 6, 12), (c + 4.5, 20.5), (c, 23.5), (c - 4.5, 20.5)], t.trim,
                curve=.35, spec=.45, shadow=.65)
        f.spike((c - 5.2, 12.5), (c - 3, 10.5), (c - 6.8, 6.8), t.trim, lit=.5)
        f.spike((c + 3, 10.5), (c + 5.2, 12.5), (c + 6.8, 6.8), t.trim, lit=-.3)
        f.gem(c, 15.8, 4.0)
        for path in ([(c - 4.5, 15), (c - 8, 13.5), (c - 10, 16), (c - 13.5, 14.5)], [(c - 4, 19), (c - 7.5, 21.5), (c - 11, 20.5)]):
            f.vein(path)
            f.vein(mirror(path, w))
    elif t is FROST:
        # A hexagonal ice heart with shards radiating like a snowflake, frost runes beneath.
        for ang in (-150, -90, -30, 30, 90, 150):
            a = math.radians(ang)
            tip = (c + math.cos(a) * 11.5, 15.5 + math.sin(a) * 9.5)
            base = (c + math.cos(a) * 3.5, 15.5 + math.sin(a) * 3.0)
            nx, ny = -math.sin(a) * 1.8, math.cos(a) * 1.8
            f.shard((base[0] - nx, base[1] - ny), (base[0] + nx, base[1] + ny), tip, glow=.5)
        hexagon = [(c + 5.2 * math.cos(math.radians(30 + 60 * i)), 15.5 + 5.2 * math.sin(math.radians(30 + 60 * i))) for i in range(6)]
        m = f.plate(hexagon, t.trim, curve=.25, spec=.5, shadow=.7)
        f.trim_edge(m, t.plate)
        f.gem(c, 15.8, 3.4)
        for x in (5, w - 5):
            f.groove([(x - 2, 25), (x, 23), (x + 2, 25), (x, 27), (x - 2, 25)], 1, t.plate)
    else:
        # Gold wings spread across the chest from a violet storm gem; lightning forks below.
        for s in (-1, 1):
            for k, (dx, dy, ln) in enumerate(((3.5, 0, 12.5), (3.5, 3.2, 10.5), (3.5, 6.4, 8))):
                root = (c + s * dx, 12.5 + dy)
                tip = (c + s * (dx + ln), 9 + dy * .7)
                f.spike((root[0], root[1] + 2.4), root, tip, t.trim, lit=.45 if s < 0 else -.35)
        f.plate([(c - 3.6, 10), (c + 3.6, 10), (c + 4.4, 15.5), (c, 22), (c - 4.4, 15.5)], t.trim, curve=.3, spec=.55, shadow=.65)
        f.gem(c, 14.6, 3.2)
        f.bolt((c - 2, 22), (c - 6, 30), jag=1.6, steps=4, seed=3)
        f.bolt((c + 2, 22), (c + 6, 30), jag=1.6, steps=4, seed=4)


def cuirass_front(f):
    t, w = f.t, f.w
    f.under()
    # Pectoral plates around the emblem.
    pec = [(1.5, 7.5), (14, 8.5), (14.5, 19), (10, 23.5), (2, 21.5)]
    for shape in (pec, mirror(pec, w)):
        if t is INFERNAL:
            f.scales(shape, sw=6.5, sh=4.2, phase=1)
            m = f.mask(shape)
            f.over(t.dark['rim'], .5, m & ~shifted(m, 0, 1))
        else:
            m = f.plate(shape, t.plate, curve=.45, spec=.45)
            f.trim_edge(m, t.trim if t is TEMPEST else t.plate)
    if t is TEMPEST:
        f.filigree([(4, 11), (9, 12.5), (11.5, 17)])
        f.filigree(mirror([(4, 11), (9, 12.5), (11.5, 17)], w))
    elif t is FROST:
        f.groove([(4, 12), (10, 13), (12, 18)], 1)
        f.groove(mirror([(4, 12), (10, 13), (12, 18)], w), 1)
    collar(f, 'front')
    emblem(f)
    if t is TEMPEST:
        m = f.plate([(2.5, 23.5), (w - 2.5, 23.5), (w - 4.5, 43), (4.5, 43)], t.plate, curve=.35, spec=.4)
        f.trim_edge(m)
        for y in (28.5, 33.5, 38.5):
            f.filigree([(4 + (y - 23) * .1, y), (w / 2 - 2, y + 1.2), (w / 2 + 2, y + 1.2), (w - 4 - (y - 23) * .1, y)])
        f.bolt((w / 2, 24), (w / 2, 42), jag=1.2, steps=5, branch=False, seed=9)
        m = f.plate([(0, 43), (w, 43), (w, 48), (0, 48)], t.trim, curve=.05, spec=.35)
        for x in (4, w - 4):
            f.rivet(x, 45.5)
        f.gem(w / 2, 45.6, 2.1)
        return
    # Ventral scutes: the dragon's belly plates, narrowing toward the waist.
    f.under([(12.5, 23), (19.5, 23), (19.5, 43), (12.5, 43)])
    f.vein([(w / 2, 24), (w / 2, 42)], 1, halo=2.4)
    for k, y in enumerate((24, 28.5, 33, 37.5)):
        inset = k * .7
        left = [(1.8 + inset, y), (15, y + 1.6), (15, y + 5.4), (2.4 + inset, y + 4.6)]
        m1 = f.plate(left, t.scale if t is not TEMPEST else t.plate, curve=.12, spec=.25, tilt=.25)
        m2 = f.plate(mirror(left, w), t.scale if t is not TEMPEST else t.plate, curve=.12, spec=.25, tilt=-.35)
        if t is TEMPEST:
            f.over(t.trim['hi'], .9, (m1 | m2) & ~shifted(m1 | m2, 0, 1))
    # Girdle with a gem buckle.
    m = f.plate([(0, 43), (w, 43), (w, 48), (0, 48)], t.trim if t is not FROST else t.dark, curve=.05, spec=.35)
    for x in (4, w - 4):
        f.rivet(x, 45.5)
    f.gem(w / 2, 45.6, 2.1)


def cuirass_back(f):
    t, w = f.t, f.w
    f.under()
    collar(f, 'back')
    blade = [(1.5, 7), (13, 8), (12, 21), (2.5, 23)]
    for shape in (blade, mirror(blade, w)):
        if t is INFERNAL:
            f.scales(shape, sw=6.5, sh=4.2)
        else:
            m = f.plate(shape, t.plate, curve=.4, spec=.35)
            f.trim_edge(m, t.trim if t is TEMPEST else t.plate)
    for k, y in enumerate((25, 31, 37)):
        lame = [(2, y), (14, y + 1.2), (14, y + 5), (2.5, y + 4)]
        f.plate(lame, t.scale if t is INFERNAL else t.plate, curve=.15, spec=.25)
        f.plate(mirror(lame, w), t.scale if t is INFERNAL else t.plate, curve=.15, spec=.25)
    # Dorsal spine: ridged vertebrae with light leaking between them.
    f.under([(13, 6), (19, 6), (19, 43), (13, 43)])
    for y in range(7, 43, 6):
        f.ridge((w / 2, y), (w / 2, y + 4.6), 2.6, t.dark if t is not TEMPEST else t.trim)
        f.glow(w / 2, y - .3, 2.6, .6)
    f.plate([(0, 43), (w, 43), (w, 48), (0, 48)], t.trim if t is not FROST else t.dark, curve=.05, spec=.3)


def cuirass_side(f):
    t, w = f.t, f.w
    f.under()
    collar(f, 'side')
    if t is INFERNAL:
        f.scales([(0, 7), (w, 7), (w, 42), (0, 42)], sw=6, sh=4.2)
    else:
        for y in (8, 15, 22, 29, 36):
            m = f.plate([(1, y), (w - 1, y - 1.2), (w - 1.5, y + 5.4), (1.5, y + 6.2)], t.plate, curve=.2, spec=.3)
            if t is TEMPEST:
                f.over(t.trim['hi'], .9, m & ~shifted(m, 0, 1))
    if t is TEMPEST:
        f.bolt((w / 2, 8), (w / 2 + 1, 41), jag=1.5, steps=6, branch=False, seed=7)
    else:
        f.vein([(w / 2, 7), (w / 2 + 1, 42)], 1, halo=2)
    f.plate([(0, 43), (w, 43), (w, 48), (0, 48)], t.trim if t is not FROST else t.dark, curve=.05)


def cuirass_top(f):
    t = f.t
    if t is FROST:
        f.fur([(0, 0), (f.w, 0), (f.w, f.h), (0, f.h)], tuft=2.5)
    else:
        f.plate([(0, 0), (f.w, 0), (f.w, f.h), (0, f.h)], t.dark, curve=.2, spec=.3)
        f.ridge((f.w / 2, 1), (f.w / 2, f.h - 1), 2, t.trim if t is TEMPEST else t.dark)


def pauldron(f, part):
    """Layered pauldron lames topped by each set's signature ornament."""
    t, w = f.t, f.w
    for k, (y0, y1) in enumerate(((0, 9), (8, 15), (14, 20))):
        p = t.plate if k == 0 or t is not INFERNAL else t.dark
        m = f.plate([(0, y0), (w, y0), (w, y1 - 1), (0, y1)], p, curve=.35, spec=.38 - k * .08)
        if t is TEMPEST:
            f.trim_edge(m)
        elif t is INFERNAL and part != 'inner':
            f.teeth(y1 - .5, 0, w, 4, 2.4)
        elif t is FROST and part != 'inner':
            f.over(t.trim['spec'], .9, m & ~shifted(m, 0, 1))
    if t is FROST:
        f.fur([(0, 0), (w, 0), (w, 5), (0, 5)], tuft=2.8)
    if part == 'outer':
        if t is INFERNAL:
            # Two hooked horns rising off the shoulder.
            f.spike((1.5, 10), (6.5, 10), (2.2, -.4), t.dark, glow_edge=True)
            f.spike((8.5, 9.5), (14.5, 9.5), (13.4, -.8), t.dark, glow_edge=True)
            f.gem(w / 2, 13, 2.5)
        elif t is FROST:
            f.shard((2.5, 12.5), (7, 12.5), (3.2, 3))
            f.shard((8, 12.5), (13.5, 12.5), (12.8, 2.4))
            f.gem(w / 2, 15, 2.2)
        else:
            # Feather lames of a folded wing.
            for k, x in enumerate((2, 6, 10)):
                f.spike((x, 16), (x + 4, 16), (x + 1.2, 3 + k), t.trim, lit=.45)
            f.gem(w / 2, 16.5, 2.0)


def arm(f, part):
    t, w = f.t, f.w
    f.under()
    pauldron(f, part)
    # Vambrace: scaled forearm guard.
    if t is INFERNAL or t is FROST:
        f.scales([(0, 22.5), (w, 22.5), (w, 41), (0, 41)], sw=5.6, sh=3.8, phase=part == 'back')
    else:
        m = f.plate([(0, 23), (w, 23), (w, 41), (0, 41)], t.plate, curve=.4, spec=.45)
        f.trim_edge(m)
    m = f.mask([(0, 22.5), (w, 22.5), (w, 23.5), (0, 23.5)])
    f.over(t.trim['base'], 1, m)
    f.over(t.trim['spec'], .8, m & ~shifted(m, 0, 1))
    if part == 'outer':
        if t is FROST:
            f.shard((4.5, 40.5), (11.5, 40.5), (9, 24))
        elif t is TEMPEST:
            f.bolt((w / 2 - 1, 25), (w / 2 + 1, 39), jag=1.4, steps=4, branch=False, seed=11)
            f.spike((5, 40.5), (11, 40.5), (9, 26), t.trim, lit=.4)
        else:
            f.spike((4.5, 40.5), (11.5, 40.5), (9, 22.5), t.dark, glow_edge=True)
    elif part == 'front' and t is TEMPEST:
        f.filigree([(3, 26), (8, 29.5), (13, 26)])
        f.gem(w / 2, 34, 1.8)
    elif part == 'front':
        f.groove([(3, 26), (8, 30), (13, 26)], 1, t.dark)
        f.vein([(w / 2, 30.5), (w / 2, 37)], 1, halo=2)
    elif part == 'back':
        f.ridge((w / 2, 24), (w / 2, 40), 2.2, t.dark if t is not TEMPEST else t.trim)
    # Clawed gauntlet with glowing knuckles.
    f.plate([(0, 41), (w, 41), (w, 48), (0, 48)], t.dark, curve=.3, spec=.3)
    if part in ('front', 'outer'):
        for x in (2, 6, 10, 14):
            f.groove([(x, 43.5), (x, 48)], 1, t.dark)
        for x in (4, 8, 12):
            f.glow(x, 42.6, 1.9, .8)
            f.rivet(x, 42.6, .9)


def pauldron_top(f):
    t = f.t
    if t is FROST:
        f.fur([(0, 0), (f.w, 0), (f.w, f.h), (0, f.h)], tuft=2.6)
        f.shard((4, 9), (8, 9), (2, 1))
        f.shard((9, 9), (13, 9), (14.5, 1.5))
        return
    m = f.plate([(0, 0), (f.w, 0), (f.w, f.h), (0, f.h)], t.plate, curve=.3, spec=.45)
    if t is TEMPEST:
        f.trim_edge(m)
        for x in (3, 7, 11):
            f.spike((x, 13), (x + 3, 13), (x + 1.5, 2), t.trim, lit=.4)
    else:
        for a, b, tip in (((2, 6), (6, 9), (1, 1)), ((10, 9), (14, 6), (15, 1)), ((6, 4), (10, 4), (8, -1))):
            f.spike(a, b, tip, t.dark, glow_edge=True)
    f.gem(f.w / 2, f.h / 2 + 2, 2.2)


def sabaton(f, part):
    """Boots: the upper leg stays clear; a toothed rim cuts real depth over the greaves."""
    t, w = f.t, f.w
    f.clear([(0, 0), (w, 48)])
    if t is FROST:
        f.fur([(0, 29), (w, 29), (w, 32.5), (0, 32.5)], tuft=2.4)
    f.plate([(0, 32), (w, 32), (w, 46), (0, 46)], t.plate if t is not INFERNAL else t.dark, curve=.4, spec=.38)
    if t is INFERNAL:
        f.teeth(32.2, 0, w, 4, 3.0, t.dark, down=False)
        f.scales([(0, 34), (w, 34), (w, 42), (0, 42)], sw=5.4, sh=3.6)
    elif t is TEMPEST:
        m = f.mask([(0, 32), (w, 32), (w, 46), (0, 46)])
        f.trim_edge(m)
    if part == 'front':
        f.ridge((w / 2, 33.5), (w / 2, 44.5), 2.2, t.trim if t is not INFERNAL else t.dark)
        # Talons over the toe cap.
        f.plate([(1, 42), (w - 1, 42), (w - 2, 46), (2, 46)], t.trim, curve=.3, spec=.55)
        for x in (3.5, 8, 12.5):
            f.spike((x - 1.6, 43), (x + 1.6, 43), (x, 48), t.dark if t is not FROST else t.ice, lit=.3)
    elif part == 'outer':
        f.gem(w / 2, 37.5, 2.2)
        if t is FROST:
            f.shard((4, 44), (8.5, 44), (13, 35))
        elif t is TEMPEST:
            f.spike((4, 44), (9, 44), (14, 36), t.trim, lit=.4)
        else:
            f.spike((4, 44), (9, 44), (14, 35.5), t.dark, glow_edge=True)
    elif part == 'back':
        f.spike((5, 45), (11, 45), (8, 35), t.dark if t is not TEMPEST else t.trim)
    f.plate([(0, 46), (w, 46), (w, 48), (0, 48)], t.trim, curve=.1, spec=.25, shadow=0)


def layer_one(t: Theme):
    canvas = Image.new('RGBA', (W, H))
    seed = itertools.count(1201 + zlib.crc32(t.key.encode()) % 97)
    body = faces(16, 16, 8, 12, 4)
    for name, side, painter in (('front', 'front', cuirass_front), ('back', 'back', cuirass_back),
                                ('right', 'outer', cuirass_side), ('left', 'outer', cuirass_side), ('top', 'top', cuirass_top)):
        f = make(t, body[name], side, next(seed))
        painter(f)
        paste(canvas, f, body[name])
    f = make(t, body['bottom'], 'bottom', next(seed))
    f.under()
    paste(canvas, f, body['bottom'])
    arms = faces(40, 16, 4, 12, 4)
    for name, part in (('right', 'outer'), ('front', 'front'), ('left', 'inner'), ('back', 'back')):
        f = make(t, arms[name], 'front' if part == 'front' else part, next(seed))
        arm(f, part)
        paste(canvas, f, arms[name])
    f = make(t, arms['top'], 'top', next(seed))
    pauldron_top(f)
    paste(canvas, f, arms['top'])
    f = make(t, arms['bottom'], 'bottom', next(seed))
    f.plate([(0, 0), (f.w, 0), (f.w, f.h), (0, f.h)], t.dark, curve=0, spec=0, shadow=0)
    paste(canvas, f, arms['bottom'])
    legs = faces(0, 16, 4, 12, 4)
    for name, part in (('front', 'front'), ('right', 'outer'), ('left', 'inner'), ('back', 'back')):
        f = make(t, legs[name], 'front' if part == 'front' else part, next(seed))
        sabaton(f, part)
        paste(canvas, f, legs[name])
    f = make(t, legs['bottom'], 'bottom', next(seed))
    f.plate([(0, 0), (f.w, 0), (f.w, f.h), (0, f.h)], t.trim, curve=0, spec=0, shadow=0)
    paste(canvas, f, legs['bottom'])
    return canvas


# ---------------------------------------------------------------------------
# Layer 2: girdle, tassets over the thighs, gem knees and spined greaves


def girdle(f, part):
    t, w = f.t, f.w
    f.clear([(0, 0), (w, 48)])
    f.plate([(0, 26), (w, 26), (w, 31), (0, 31)], t.trim if t is not FROST else t.dark, curve=.1, spec=.3)
    f.under([(0, 31), (w, 48)])
    n = 4 if f.w > 20 else 2
    step = w / n
    for i in range(n):
        x0, x1 = i * step + .5, (i + 1) * step - .5
        f.plate([(x0, 31), (x1, 31), (x1 - 1, 44), ((x0 + x1) / 2, 48), (x0 + 1, 44)], t.plate, curve=.3, spec=.3)
        f.vein([((x0 + x1) / 2, 33), ((x0 + x1) / 2, 44)], 1, halo=1.6)
    if part == 'front':
        f.gem(w / 2, 28.6, 2.2)


def leg(f, part):
    t, w = f.t, f.w
    f.under()
    # Tassets hanging from the belt over the thigh, then the cuisse.
    if t is INFERNAL:
        f.scales([(0, 0), (w, 0), (w, 17.5), (0, 17.5)], sw=6, sh=4.0, phase=part == 'back')
    elif t is FROST:
        m = f.plate([(0, 0), (w, 0), (w, 15), (0, 17.5)], t.plate, curve=.4, spec=.42)
        f.over(t.trim['spec'], .9, m & ~shifted(m, 0, 1))
    else:
        m = f.plate([(0, 0), (w, 0), (w, 15), (0, 17.5)], t.plate, curve=.4, spec=.45)
        f.trim_edge(m)
        if part == 'front':
            f.filigree([(3, 4), (8, 8.5), (13, 4)])
    tasset = [(.5, 0), (w - .5, 0), (w - 1.5, 7.5), (w / 2, 10), (1.5, 7.5)]
    if part in ('front', 'outer'):
        m = f.plate(tasset, t.dark if t is not TEMPEST else t.cloth, curve=.3, spec=.3)
        f.trim_edge(m)
        f.vein([(w / 2, 1.5), (w / 2, 8)], 1, halo=1.6, strength=1.4)
    if part == 'outer':
        if t is FROST:
            f.shard((3, 15.5), (8, 15), (13.5, 5))
        elif t is TEMPEST:
            f.spike((3, 15.5), (8, 15), (13, 6), t.trim, lit=.4)
        else:
            f.spike((3, 15), (9, 14), (13, 4), t.dark, glow_edge=True)
    # Knee cop: the set's second core.
    m = f.plate([(1, 19.5), (w - 1, 19.5), (w - 2, 26.5), (2, 26.5)], t.trim if t is not INFERNAL else t.dark, curve=.45, spec=.5)
    if part == 'front':
        f.gem(w / 2, 22.8, 2.9)
        f.spike((5.5, 25.8), (10.5, 25.8), (8, 31), t.dark if t is not FROST else t.ice, lit=.3)
    elif part == 'outer':
        f.rivet(w / 2, 22.8, 1.5)
    # Greave.
    p = t.plate if t is not INFERNAL else t.dark
    m = f.plate([(0, 27), (w, 27), (w, 48), (0, 48)], p, curve=.42, spec=.42)
    if t is TEMPEST:
        f.trim_edge(m)
    if part == 'front':
        f.ridge((w / 2, 28.5), (w / 2, 47), 2.4, t.trim if t is TEMPEST else p)
        if t is TEMPEST:
            f.bolt((w / 2 + 3.5, 30), (w / 2 + 3, 42), jag=1.0, steps=4, branch=False, seed=21)
        else:
            f.vein([(w / 2 + 3, 30), (w / 2 + 3.5, 41)], 1, halo=1.8)
    elif part == 'outer':
        if t is FROST:
            f.shard((4, 40), (9, 40), (13.5, 31))
            f.shard((4, 34), (9, 34), (13, 27.5))
        elif t is TEMPEST:
            f.spike((4, 40), (9, 40), (13, 31), t.trim, lit=.4)
        else:
            f.spike((4, 40), (9, 40), (13, 31), t.dark, glow_edge=True)
            f.spike((4, 33), (9, 33), (13, 26.5), t.dark, glow_edge=True)
    elif part == 'back':
        f.groove([(w / 2, 29), (w / 2, 46)], 1, p)


def layer_two(t: Theme):
    canvas = Image.new('RGBA', (W, H))
    seed = itertools.count(1401 + zlib.crc32(t.key.encode()) % 89)
    body = faces(16, 16, 8, 12, 4)
    for name, side, part in (('front', 'front', 'front'), ('back', 'back', 'back'), ('right', 'outer', 'side'), ('left', 'outer', 'side')):
        f = make(t, body[name], side, next(seed))
        girdle(f, part)
        paste(canvas, f, body[name])
    legs = faces(0, 16, 4, 12, 4)
    for name, part in (('front', 'front'), ('right', 'outer'), ('left', 'inner'), ('back', 'back')):
        f = make(t, legs[name], 'front' if part == 'front' else part, next(seed))
        leg(f, part)
        paste(canvas, f, legs[name])
    f = make(t, legs['bottom'], 'bottom', next(seed))
    f.under()
    paste(canvas, f, legs['bottom'])
    return canvas


# ---------------------------------------------------------------------------
# Cape: the folded elytra pair, each wing cut so the two meet at the spine


def _rest(xl, yl, zl, rx=15, rz=-15):
    """Left wing texel (Java wing-local, y down) -> world x at rest: pivot x 5, xRot 15, zRot -15."""
    ax, az = math.radians(rx), math.radians(rz)
    y1 = yl * math.cos(ax) - zl * math.sin(ax)
    return 5 + xl * math.cos(az) - y1 * math.sin(az)


def cape_cut(overlap=.35):
    """Per-row visible extent of the outer (south) face, in face pixels from the shoulder edge."""
    cut = []
    for row in range(20 * K):
        v = 2 + (row + .5) / K
        yl = -1 + (v - 2) * 22 / 20
        # The face runs u 36 (x +1, shoulder) -> 46 (x -11); keep texels on this wing's side of the spine.
        lo, hi = 0.0, 10.0
        for _ in range(40):
            mid = (lo + hi) / 2
            xl = 1 - mid * 12 / 10
            if _rest(xl, yl, 3) >= -overlap:
                lo = mid
            else:
                hi = mid
        cut.append(lo * K)
    return np.array(cut)


def cape_painter(t: Theme, f: Paint, cut):
    """Paint the 40x80 outer face: shoulder edge at x 0, spine at the cut, hem at the bottom."""
    w, h = f.w, f.h
    rows = np.arange(h)
    inside = f.X < cut[np.clip(f.Y.astype(int), 0, h - 1)]
    cloth = t.cloth
    # Body of the cape: heavy cloth or membrane, vertical folds, darker toward the spine seam.
    fold = np.sin(f.X / 3.1 + f.Y * .03) * .08 + np.sin(f.X / 1.7 + 1.3) * .03
    seam = np.clip(1 - (cut[np.clip(f.Y.astype(int), 0, h - 1)] - f.X) / 6, 0, 1)
    value = .86 - .22 * (f.Y / h) + fold - .3 * seam + f.rng.normal(0, .015, f.X.shape)
    f.px[..., :3] = np.where(inside[..., None], ramp(cloth, value), 0)
    f.px[..., 3] = np.where(inside, 255, 0)
    if t is INFERNAL:
        # A dragon's wing: finger bones fan from the shoulder, the membrane scallops between their tips.
        tips = [(-2, h), (12, h - 1), (25, h - 1), (38, h - 1), (46, h)]
        bottom = np.full(w, float(h))
        xs = np.arange(w) + .5
        for (x0, y0), (x1, y1) in zip(tips, tips[1:]):
            sel = (xs >= x0) & (xs < x1)
            tt = (xs[sel] - x0) / (x1 - x0)
            bottom[sel] = y0 + (y1 - y0) * tt - 12 * np.sin(np.pi * tt)
        membrane = f.Y < bottom[np.clip(f.X.astype(int), 0, w - 1)]
        f.px[~membrane] = 0
        burn = membrane & ~shifted(membrane, 0, -2)
        f.add(t.glow['dim'], f.soft(burn, 12) * 2.4)
        f.add(t.glow['halo'], f.soft(burn, 4) * 1.8)
        f.over(t.glow['core'], 1, burn)
        f.over(t.glow['hot'], .8, burn & ~shifted(burn, 0, -1))
        bones = []
        for k, (tx, ty) in enumerate(tips[1:-1]):
            x0 = 2 + k * 1.2
            bones.append([(x0 - 2.2, 4), (x0 + 2.2, 4), (tx + .9, ty - 1), (tx - .9, ty - 1)])
        shade = np.zeros((h, w), bool)
        for b in bones:
            shade |= f.mask(b)
        f.over((0, 0, 0), np.clip(f.soft(shade, 3.5) * .9, 0, .6), ~shade & membrane)
        for b in bones:
            f.plate(b, t.dark, curve=.6, spec=.55, shadow=0, bevel=1, grade=.1)
            f.over(t.dark['rim'], .55, f.mask([((b[0][0] + b[3][0]) / 2 + .6, 6), ((b[0][0] + b[3][0]) / 2 + (b[3][0] - b[0][0]) * .5, b[3][1] - 4)], width=1))
        for k, (tx, ty) in enumerate(tips[1:-1]):
            f.spike((tx - 1.8, ty - 4), (tx + 1.8, ty - 4), (tx + .3, ty + 1), t.dark, lit=.3, shadow=False)
    elif t is FROST:
        for k, x in enumerate((6, 15, 24, 33)):
            f.groove([(x, 12 + k * 2), (x - 3, 30 + k * 3), (x + 1, 52 + k * 2)], 1, cloth)
        for (cx, cy, r) in ((9, 30, 5), (22, 46, 6), (31, 62, 4.5), (13, 58, 3.5)):
            for ang in range(0, 180, 60):
                a = math.radians(ang)
                f.over(t.glow['core'], .65, f.mask([(cx - math.cos(a) * r, cy - math.sin(a) * r), (cx + math.cos(a) * r, cy + math.sin(a) * r)], width=1))
            f.glow(cx, cy, r * 1.2, .35)
    else:
        f.bolt((8, 8), (14, 64), jag=2.6, steps=7, seed=31)
        f.bolt((26, 20), (30, 70), jag=2.2, steps=6, seed=32)
        f.filigree([(2.5, 6), (2.5, 72)], 2)
    # Shoulder mantle across the top of the cape.
    if t is FROST:
        f.fur([(0, 0), (w, 0), (w, 7), (0, 7)], tuft=3.2)
    else:
        m = f.plate([(0, 0), (w, 0), (w, 6), (0, 7)], t.trim if t is TEMPEST else t.dark, curve=.15, spec=.35)
        f.trim_edge(m)
        f.rivet(6, 3.4, 1.4)
    # The hem.
    hem = np.zeros((h, w), bool)
    if t is INFERNAL:
        pass
    elif t is FROST:
        f.px[f.Y > h - 14] = 0
        for i, x in enumerate(np.arange(0, w + 4, 4.6)):
            ln = 8 + (i * 7 % 5) * 1.3
            f.shard((x - 2.4, h - 15), (x + 2.4, h - 15), (x + .3, h - 15 + ln + 2), glow=.4)
    else:
        f.px[f.Y > h - 9] = 0
        for i, x in enumerate(np.arange(-1, w + 6, 6.5)):
            f.plate([(x - 3.4, h - 10), (x + 3.4, h - 10), (x, h - 1)], t.cloth, curve=0, spec=.2, bevel=1, shadow=.4)
        m = f.mask([(0, h - 13), (w, h - 13), (w, h - 10), (0, h - 10)])
        f.over(t.trim['base'], 1, m & (f.px[..., 3] > 0))
        f.over(t.trim['spec'], .8, m & ~shifted(m, 0, 1) & (f.px[..., 3] > 0))
    # Shoulder edge highlight and the spine seam.
    f.px[~inside] = 0
    edge = (f.px[..., 3] > 0) & ~shifted(f.px[..., 3] > 0, 1, 0)
    f.over(t.trim['hi'] if t is not INFERNAL else t.dark['rim'], .8, edge & (f.X < 3))
    seam_line = (f.px[..., 3] > 0) & ~shifted(f.px[..., 3] > 0, -1, 0)
    f.over(cloth['deep'], .9, seam_line)


def cape(t: Theme):
    """64x32 elytra layout at 4x: only the outer face and the mantle top are opaque."""
    canvas = Image.new('RGBA', (W, H))
    cut = cape_cut()
    f = Paint(10 * K, 20 * K, 'back', 77, t)
    cape_painter(t, f, cut)
    canvas.alpha_composite(f.image(), (36 * K, 2 * K))
    # Mantle top (Java names it DOWN: minY in model space): u 24..34 -> x 1..-11, v 0..2 -> z 3..-1.
    top = Paint(10 * K, 2 * K, 'top', 78, t)
    if t is FROST:
        top.fur([(0, 0), (top.w, 0), (top.w, top.h), (0, top.h)], tuft=1.2, down=False)
    else:
        top.plate([(0, 0), (top.w, 0), (top.w, top.h), (0, top.h)], t.trim if t is TEMPEST else t.dark, curve=0, spec=.3, shadow=0)
    keep = np.zeros((top.h, top.w), bool)
    for row in range(top.h):
        zl = 3 - (row + .5) / K * 2
        for col in range(top.w):
            xl = 1 - (34 - (24 + (col + .5) / K)) * 12 / 10
            keep[row, col] = _rest(xl, -1, zl) >= -.35
    top.px[~keep] = 0
    canvas.alpha_composite(top.image(), (24 * K, 0))
    return canvas


# ---------------------------------------------------------------------------
# Helms


def great_helm(h, mat, half=5.35, chamfer=1.4, bottom=23.25, crown=32.5, dome=3.0, prow=1.3, prow_w=3.0):
    """A solid chamfered great helm (it encloses a skin's hat layer) with a V-keeled visor."""
    sl, X, Y, Z = h.grid()
    rise = np.clip(Y - crown, 0, None)
    s = np.sqrt(np.clip(1 - (rise / dome) ** 2, 0, 1))
    keel = prow * np.clip(1 - np.abs(X) / prow_w, 0, 1) * np.clip((Y - bottom) / 1.5, 0, 1)
    hx = half * s
    front = (half + keel) * s
    hz = np.where(Z < 0, front, half * s)
    inside = ((np.abs(X) <= hx) & (Z <= half * s) & (Z >= -front) & (np.abs(X) + np.abs(Z) <= hx + hz - chamfer * s)
              & (Y >= bottom) & (s > 0))
    lip = (Y >= bottom - .25) & (Y < bottom + .75) & (np.abs(X) <= half + .25) & (np.abs(Z) <= half + .25) & \
          (np.abs(X) + np.abs(Z) <= 2 * half + .5 - chamfer)
    h.put(mat, sl, inside | lip)
    return X, Y, Z, front, inside, lip


def front_relief(h, mask_xy, depth, mat, behind=1, tone=None):
    """Cut `depth` cells into the front of the solid where mask_xy holds and paint the floor."""
    solid = h.solid()
    any_z = solid.any(axis=2)
    first = np.argmax(solid, axis=2)
    nz = solid.shape[2]
    K_idx = np.arange(nz)[None, None, :]
    m = np.asarray(mask_xy, bool) & any_z
    carve = m[..., None] & (K_idx >= first[..., None]) & (K_idx < first[..., None] + depth)
    floor = m[..., None] & (K_idx >= first[..., None] + depth) & (K_idx < first[..., None] + depth + behind)
    h.put(None, (slice(None),) * 3, carve, over=True)
    h.put(mat, (slice(None),) * 3, floor & solid, tone=tone, over=True)


def worley(h, mask, n=70, seed=3, lo=(-6, 23, -7), hi=(6, 36, 6)):
    """Cellular-noise border distance (F2 - F1) on the cells of `mask`; inf elsewhere."""
    rng = np.random.default_rng(seed)
    pts = rng.uniform(lo, hi, size=(n, 3))
    idx = np.nonzero(mask)
    P = np.stack([h.axes[d][idx[d]] for d in range(3)], -1)
    out = np.full(mask.shape, np.inf, np.float32)
    for start in range(0, len(P), 20000):
        d = np.sort(np.linalg.norm(P[start:start + 20000, None, :] - pts[None], axis=-1), axis=1)
        out[tuple(i[start:start + 20000] for i in idx)] = d[:, 1] - d[:, 0]
    return out


def eye_mask(X, Y, cy, x0, x1, slope, half, taper=.45):
    ax = np.abs(X)
    t = np.clip((ax - x0) / (x1 - x0), 0, 1)
    centre = cy + slope * (ax - x0)
    hh = half * (1 - taper * np.abs(2 * t - 1) ** 2)
    return (ax >= x0) & (ax <= x1) & (np.abs(Y - centre) <= hh)


def scale_tone(X, Y, Z, size=1.3, rows=.95, depth=.16):
    """Overlapping scales wrapped around the helm (cylindrical coordinates)."""
    theta = np.arctan2(X, -Z) * 5.4
    r = np.floor((40 - Y) / rows)
    v = ((40 - Y) / rows) % 1
    u = ((theta / size) + .5 * (r % 2)) % 1 - .5
    edge = .62 + .74 * np.sqrt(np.clip(1 - (2 * u) ** 2, 0, 1))
    s = np.clip(v / edge, 0, 1)
    return (s - .55) * depth * 1.1 - (v > edge * .93) * depth * 1.3 + (np.abs(s - .86) < .06) * depth * .6


def full(h, f):
    m = np.zeros(h.labels.shape, bool)
    m[f.sl] = f.mask
    return m


def seam(h, band, mat, tone, keep=None):
    """A glowing seam: carve the outer cell along `band` (full-grid mask), light the floor."""
    keep = h.solid() if keep is None else keep
    h.put(None, (slice(None),) * 3, band & h.skin(1) & keep, over=True)
    h.recolor(mat, band, depth=1, tone=tone)


def helm_infernal():
    h = bake.VoxelHelm('infernal_drake_helm')
    sl = (slice(None),) * 3
    X, Y, Z, front, shell, lip = great_helm(h, 'plate', half=5.3, chamfer=1.9, crown=31.6, dome=3.9, prow=1.35, prow_w=2.7)
    h.put('trim', sl, lip & ~shell, over=True)
    h.tone[shell] = (scale_tone(X, Y, Z, size=1.8, rows=1.3, depth=.12) - .1 * np.clip(Y - 31, 0, 4) / 4)[shell]
    X0, Y0 = X[..., 0], Y[..., 0]
    # Heavy V brow of horn sweeping back into the great horns.
    for s in (-1, 1):
        f = h.tube([(s * .3, 29.75, -6.55), (s * 2.3, 30.4, -5.85), (s * 3.9, 31.1, -4.75), (s * 4.9, 31.5, -3.0)], .52, .72)
        h.put('horn', f.sl, f.mask, normal=f.normal, tone=np.where(f.rel > .78, .12, 0), over=True)
    # Burning eyes sunk under the brow; a molten grille of fangs below the keel.
    eyes = eye_mask(X0, Y0, 28.95, .85, 3.5, .34, .34)
    front_relief(h, eyes, 3, 'eye', behind=1, tone=.75)
    grille = np.zeros_like(X0, bool)
    for k, x in enumerate((-2.1, -1.25, -.42, .42, 1.25, 2.1)):
        length = 1.9 - .28 * abs(x)
        grille |= (np.abs(X0 - x) < .19) & (Y0 < 27.0) & (Y0 > 27.0 - length)
    front_relief(h, grille, 2, 'lava', behind=1, tone=.55)
    keel = h.tube([(0, 29.3, -6.7), (0, 27.0, -6.75), (0, 24.6, -6.25)], .3, .22, smooth=False)
    h.put('horn', keel.sl, keel.mask, normal=keel.normal, over=True)
    gem = (np.abs(X0) / .62 + np.abs(Y0 - 31.45) / .95) <= 1
    setting = (np.abs(X0) / 1.05 + np.abs(Y0 - 31.45) / 1.4) <= 1
    front_relief(h, setting & ~gem, 1, 'trim')
    front_relief(h, gem, 1, 'gem', tone=.75)
    # Chin spike.
    f = h.tube([(0, 24.0, -6.0), (0, 23.0, -6.7), (0, 22.3, -7.2)], .5, .06)
    h.put('horn', f.sl, f.mask, normal=f.normal)
    # Plate seams over the dome, each split by a molten line, plus a few branching cracks.
    wob = np.sin(X * 2.1 + Z * .7) * .12
    bands = [(np.abs(X) < .2) & (Y > 31.0) & (Z > -3.4)]
    for z0 in (-1.2, 1.9):
        bands.append((np.abs(Z - z0 - .32 * (Y - 31) + wob) < .18) & (Y > 28.2))
    seam(h, shell & np.logical_or.reduce(bands) & (h.labels == h.label('plate')), 'lava', .4)
    region = shell & (Y > 25.0) & (Z > -2.0) & h.skin(2)
    crack = worley(h, region, n=18, seed=11, lo=(-6, 24, -2), hi=(6, 36, 6))
    seam(h, (crack < .13) & (h.labels == h.label('plate')), 'lava', .32)
    # Massive horns rooted in the brow: back, out, up, then hooking forward; ember tips.
    for s in (-1, 1):
        f = h.tube([(s * 4.7, 31.3, -3.4), (s * 6.2, 31.9, -1.2), (s * 8.0, 33.4, .8), (s * 9.5, 35.9, 1.9),
                    (s * 9.7, 38.9, 1.0), (s * 8.7, 41.6, -1.2)], 1.75, .12, power=.8)
        ring = np.cos(f.along * 2 * math.pi * 8) > .62
        h.put('ember', f.sl, f.mask & (f.along > .88), normal=f.normal, tone=np.clip((f.along - .88) / .12, 0, 1) * .75 + .2)
        h.put('horn', f.sl, f.mask, normal=f.normal, tone=np.where(ring, -.14, .04) + .12 * f.along)
        f = h.tube([(s * 4.0, 28.6, 3.0), (s * 5.4, 29.3, 5.2), (s * 6.2, 30.7, 7.8), (s * 6.1, 32.4, 9.9)], 1.0, .1, power=.9)
        ring = np.cos(f.along * 2 * math.pi * 5) > .6
        h.put('horn', f.sl, f.mask, normal=f.normal, tone=np.where(ring, -.14, .04))
        # Frill spikes behind the cheek guards.
        for base, tip, r in (((5.3, 24.9, -.2), (6.8, 23.6, 2.6), .5), ((5.3, 26.0, 1.4), (6.9, 25.4, 4.4), .5)):
            f = h.tube([(s * base[0], base[1], base[2]), (s * tip[0], tip[1], tip[2])], r, .06, smooth=False)
            h.put('horn', f.sl, f.mask, normal=f.normal)
    # Dorsal crest: three great back-swept spikes with ember tips.
    for z, rise, base_y in ((-1.6, 5.0, 34.9), (1.0, 4.4, 34.6), (3.4, 3.2, 33.0)):
        f = h.tube([(0, base_y - 1.2, z), (0, base_y + rise * .5, z + rise * .38), (0, base_y + rise, z + rise * 1.05)], 1.0, .08)
        h.put('ember', f.sl, f.mask & (f.along > .8), normal=f.normal, tone=np.clip((f.along - .8) / .2, 0, 1) * .7 + .25)
        h.put('horn', f.sl, f.mask, normal=f.normal, tone=.1 * f.along)
    return h


def helm_frost():
    t = FROST
    h = bake.VoxelHelm('frostwyrm_helm')
    X, Y, Z, front, shell, lip = great_helm(h, 'steel', half=5.3, chamfer=1.65, crown=32.5, dome=2.7, prow=1.7, prow_w=2.5)
    h.put('silver', (slice(None),) * 3, lip & ~shell, over=True)
    X0, Y0 = X[..., 0], Y[..., 0]
    # Navy visor field, silver brow and cyan eyes.
    visor = (np.abs(Y0 - 28.8 - .18 * np.abs(X0)) < 1.25) & (np.abs(X0) < 4.2)
    front_relief(h, visor, 0, 'navy', behind=2)
    eyes = eye_mask(X0, Y0, 28.75, .7, 3.7, .36, .3)
    front_relief(h, eyes, 2, 'eye', behind=1, tone=.75)
    for s in (-1, 1):
        f = h.tube([(s * 4.6, 30.9, -4.6), (s * 2.4, 30.3, -6.0), (s * .3, 29.75, -6.85)], .34, .3)
        h.put('silver', f.sl, f.mask, normal=f.normal, over=True)
    # Engraved jaw lines and a keel on the silver mask.
    lines = np.zeros_like(X0, bool)
    for (ax, ay), (bx, by) in (((1.2, 27.3), (3.4, 24.6)), ((-1.2, 27.3), (-3.4, 24.6)), ((0, 27.6), (0, 24.3))):
        for tt in np.linspace(0, 1, 16):
            px, py = ax + (bx - ax) * tt, ay + (by - ay) * tt
            lines |= (np.abs(X0 - px) < .15) & (np.abs(Y0 - py) < .15)
    front_relief(h, lines, 1, 'navy')
    # A beard of icicles hanging off the chin.
    for x, ln in ((-2.2, 1.3), (-1.1, 1.9), (0, 2.4), (1.1, 1.9), (2.2, 1.3)):
        zf = -(5.3 + 1.7 * max(0, 1 - abs(x) / 2.5)) + .45
        f = h.crystal((x, 24.0, zf), (x * 1.05, 24.0 - ln, zf - .15), .36, ln * .7, hint=(1, 0, 0))
        h.put('ice', f.sl, f.mask, normal=f.normal, tone=np.where(f.extra['facet'] % 2 == 0, .06, -.05))
    gem = (np.abs(X0) / .6 + np.abs(Y0 - 31.45) / 1.0) <= 1
    setting = (np.abs(X0) / 1.0 + np.abs(Y0 - 31.45) / 1.45) <= 1
    front_relief(h, setting & ~gem, 1, 'silver')
    front_relief(h, gem, 1, 'gem', tone=.75)
    # Silver crown band holding the crystals.
    band = shell & (Y > 32.3) & (Y < 33.0) & (Z < 1.5)
    h.recolor('silver', band, depth=2)
    # Fur ruff around the back of the neck.
    rng = np.random.default_rng(9)
    noise = np.sin(np.arctan2(X, Z) * 23) * .25 + np.sin(np.arctan2(X, Z) * 9 + 1) * .2
    ring = (np.maximum(np.abs(X), np.abs(Z)) <= 6.25 + noise) & (Y >= 23.0) & (Y <= 25.2 + noise * .8) & (Z > -3.4 + np.abs(X) * .1)
    sl = (slice(None),) * 3
    h.put('fur', sl, ring & ~shell, tone=(np.sin(X * 7.3 + Z * 5.1) * np.sin(Y * 9.1) * .1).astype(np.float32))
    # The crown of ice: a tall centre crystal fanned by smaller ones.
    crystals = [((0, 33.0, -2.3), (0, 42.7, -.7), .95, 2.7),
                ((1.95, 32.8, -2.7), (2.95, 40.2, -1.6), .74, 2.1), ((-1.95, 32.8, -2.7), (-2.95, 40.2, -1.6), .74, 2.1),
                ((3.7, 32.2, -2.2), (5.8, 37.9, -.9), .62, 1.7), ((-3.7, 32.2, -2.2), (-5.8, 37.9, -.9), .62, 1.7),
                ((1.3, 33.4, .5), (2.0, 39.2, 2.7), .58, 1.6), ((-1.3, 33.4, .5), (-2.0, 39.2, 2.7), .58, 1.6),
                ((0, 34.0, 1.9), (0, 37.8, 4.6), .5, 1.3)]
    for i, (base, tip, r, tl) in enumerate(crystals):
        f = h.crystal(base, tip, r, tl, twist=.3 * i)
        lit = np.where(f.extra['facet'] % 2 == 0, .06, -.05)
        h.put('ice_glow', f.sl, f.mask & (f.along > .86), normal=f.normal, tone=np.clip((f.along - .86) / .14, 0, 1) * .6 + .35)
        h.put('ice', f.sl, f.mask, normal=f.normal, tone=lit + .08 * f.along)
    # Swept crystal frills behind the temples.
    for s in (-1, 1):
        for base, tip, r, tl in (((4.9, 30.6, -.6), (7.7, 33.9, 4.9), .55, 1.6), ((5.0, 29.0, .6), (7.9, 30.7, 6.1), .5, 1.5),
                                 ((4.8, 27.4, 1.6), (6.7, 27.2, 6.3), .42, 1.3)):
            f = h.crystal((s * base[0], base[1], base[2]), (s * tip[0], tip[1], tip[2]), r, tl, hint=(0, 1, 0))
            h.put('ice', f.sl, f.mask, normal=f.normal, tone=np.where(f.extra['facet'] % 2 == 0, .06, -.05))
    h.tone[shell & (h.labels == h.label('steel'))] += (np.sin(X * 3.1 + Y * 1.7) * np.sin(Z * 2.3 - Y * .9) * .04)[shell & (h.labels == h.label('steel'))]
    return h


def helm_tempest():
    t = TEMPEST
    h = bake.VoxelHelm('tempest_drake_helm')
    X, Y, Z, front, shell, lip = great_helm(h, 'pearl', half=5.3, chamfer=1.4, crown=32.5, dome=3.05, prow=1.15, prow_w=3.2)
    sl = (slice(None),) * 3
    h.put('gold', sl, lip & ~shell, over=True)
    X0, Y0 = X[..., 0], Y[..., 0]
    # Gold brow band, crest ridge and dome filigree.
    h.recolor('gold', shell & (np.abs(Y - 30.45 - .12 * np.abs(X)) < .3) & (Z < 1.0), depth=2)
    h.recolor('gold', shell & (np.abs(X) < .3) & (Y > 30.6), depth=2)
    h.recolor('gold', shell & (np.abs(np.abs(X) - 2.6 + .35 * (Y - 31)) < .17) & (Y > 31.2) & (Z > -3.6), depth=1)
    h.recolor('gold', shell & (Y > 31.0) & (np.abs(Z - 2.4 + .4 * np.abs(X)) < .17), depth=1)
    # A violet visor over the whole face, rimmed and keeled in gold.
    visor = (Y0 > 24.15) & (Y0 < 29.95 + .12 * np.abs(X0)) & (np.abs(X0) < 4.05 - .25 * np.clip(28 - Y0, 0, 4))
    front_relief(h, visor, 0, 'violet', behind=2)
    rim = visor & ~((Y0 > 24.45) & (Y0 < 29.65 + .12 * np.abs(X0)) & (np.abs(X0) < 3.75 - .25 * np.clip(28 - Y0, 0, 4)))
    front_relief(h, rim, 0, 'gold', behind=1)
    keel = (np.abs(X0) < .2) & (Y0 < 28.0) & (Y0 > 24.2)
    front_relief(h, keel, 0, 'gold', behind=1)
    eyes = eye_mask(X0, Y0, 28.55, .8, 3.5, .33, .33)
    front_relief(h, eyes, 2, 'eye', behind=1, tone=.8)
    vents = np.zeros_like(X0, bool)
    for x in (-1.5, -.8, .8, 1.5):
        vents |= (np.abs(X0 - x) < .14) & (Y0 > 24.9) & (Y0 < 26.6 - .3 * abs(x))
    front_relief(h, vents, 1, 'storm', tone=.5)
    for s in (-1, 1):
        f = h.tube([(s * 4.4, 31.1, -4.9), (s * 2.3, 30.4, -6.0), (s * .3, 29.8, -6.45)], .3, .26)
        h.put('gold', f.sl, f.mask, normal=f.normal, over=True)
    gem = (np.abs(X0) / .65 + np.abs(Y0 - 31.6) / 1.0) <= 1
    setting = (np.abs(X0) / 1.1 + np.abs(Y0 - 31.6) / 1.5) <= 1
    front_relief(h, setting & ~gem, 1, 'gold')
    front_relief(h, gem, 1, 'gem', tone=.75)
    # Lightning-bolt horns of gold, angular and swept back.
    for s in (-1, 1):
        f = h.tube([(s * 4.5, 31.1, -1.4), (s * 6.7, 33.4, .9), (s * 6.9, 34.7, 3.0), (s * 8.9, 37.3, 5.1),
                    (s * 9.0, 38.5, 7.2), (s * 10.6, 41.6, 9.9)], 1.28, .12, power=.9, smooth=False)
        edge = f.rel > .82
        h.put('gold', f.sl, f.mask, normal=f.normal, tone=np.where(edge, .08, 0) - .05 * (np.cos(f.along * 40) > .8))
        # Great feathered wings sweeping back off the temples.
        for k, (base, length, up, width) in enumerate((((5.2, 30.4, -.4), 8.4, .78, 1.5), ((5.2, 28.8, .7), 7.0, .5, 1.35),
                                                       ((5.1, 27.2, 1.6), 5.6, .26, 1.2))):
            o = np.array([s * base[0], base[1], base[2]])
            u = bake.unit((s * .5, up, 1.0))
            v = bake.unit(np.cross(u, (s * 1.0, 0, 0)))
            if v[1] < 0:
                v = -v
            outline = [(0, -width * .45), (length * .6, -width * .65), (length, -.05), (length * .62, width * .6), (0, width * .5)]
            f = h.blade(o, u, v, outline, .6, bevel=.5)
            gild = (f.extra['edge'] < .3) | (np.abs(f.extra['v'] + .05) < .14)
            h.put('gold', f.sl, f.mask & gild, normal=f.normal)
            h.put('pearl', f.sl, f.mask, normal=f.normal, tone=-.04 * k + .06 * (f.extra['u'] / length))
    # A crackling crest blade from brow to nape.
    o = np.array([0.0, 33.6, -3.4])
    outline = [(0, -.6), (1.2, 4.4), (2.3, 3.0), (3.9, 6.0), (4.6, 4.2), (6.4, 5.0), (7.2, 2.6), (7.8, -.4), (3.5, -1.6)]
    f = h.blade(o, (0, 0, 1), (0, 1, 0), outline, .66, bevel=.45)
    rim = f.extra['edge'] < .32
    h.put('storm', f.sl, f.mask & ~rim, tone=np.clip(.35 + .12 * f.extra['v'], .3, 1), over=True)
    h.put('gold', f.sl, f.mask & rim, normal=f.normal, over=True)
    return h


def helm_specs(t: Theme, extra):
    specs = {name: dict(ramp=attr if isinstance(attr, dict) else getattr(t, attr), **kw) for name, (attr, kw) in extra.items() if attr}
    glow = {'dim': t.glow['dim'], 'core': t.glow['core'], 'hot': t.glow['hot']}
    gem = {'dim': t.gem[0], 'core': t.gem[1], 'hot': t.gem[3]}
    for name, (attr, kw) in extra.items():
        if not attr:
            specs[name] = dict(glow=gem if name == 'gem' else glow, **kw)
    return specs


INFERNAL_MATS = {'plate': ('plate', dict(spec=.6, gloss=12, ambient=.36, diffuse=.5, rim=1.0, grain=.012)), 'plate_dark': ('dark', dict(spec=.1)),
                 'trim': ('trim', dict(spec=.7, gloss=12, ambient=.4, metal=.45)),
                 'horn': (pal((6, 3, 3), (16, 7, 6), (30, 13, 10), (50, 22, 15), (86, 38, 22), (160, 84, 46), (220, 140, 80)),
                          dict(spec=.45, gloss=10, ambient=.42, rim=1.0, metal=.18)),
                 'fang': (pal((40, 30, 24), (80, 64, 52), (128, 108, 90), (170, 150, 128), (210, 194, 172), (240, 230, 214), (255, 250, 240)),
                          dict(spec=.4, ambient=.5, diffuse=.45)),
                 'eye': (None, {}), 'lava': (None, {}), 'ember': (None, {}), 'gem': (None, {})}
FROST_MATS = {'steel': ('plate', dict(spec=.55, gloss=18)), 'silver': ('trim', dict(spec=.7, gloss=16)),
              'navy': ('dark', dict(spec=.3)), 'fur': ('fur', dict(spec=.05, ao=1.1, grain=.05, ambient=.5, diffuse=.45)),
              'ice': ('ice', dict(spec=.85, gloss=10, ambient=.5, diffuse=.5, rim=.9)),
              'eye': (None, {}), 'rune': (None, {}), 'ice_glow': (None, {}), 'gem': (None, {})}
TEMPEST_MATS = {'pearl': ('plate', dict(spec=.55, gloss=16, ambient=.32, diffuse=.6, rim=.8)), 'gold': ('trim', dict(spec=.75, gloss=14, ambient=.4)),
                'violet': ('dark', dict(spec=.35)),
                'eye': (None, {}), 'storm': (None, {}), 'gem': (None, {})}


def baked(t: Theme, builder, mats):
    def run():
        return bake.bake(builder(), helm_specs(t, mats), seed=len(t.key))
    return run


def icon_placeholder():
    raise AssertionError('dragon sets use baked helms')


def make_set(t: Theme, title, builder, mats, label, background, skin):
    return ArmorSet(key=t.key, pieces=pieces(title), layer_one=lambda: layer_one(t), layer_two=lambda: layer_two(t),
                    helm=icon_placeholder, helm_palette={'unused': '#000000'}, helm_glow=set(),
                    art=ROOT / f'advance-magic/art/{t.key.replace("_", "-")}-armor', credit=f'Evergarden / dragon_armor_assets.py ({t.key})',
                    atlas_seed=0, label_color=label, background=background, skin=skin,
                    baked=baked(t, builder, mats), cape=lambda: cape(t))


INFERNAL_SET = make_set(INFERNAL, 'Infernal Drake', helm_infernal, INFERNAL_MATS, (255, 150, 70), (40, 22, 24), (70, 58, 60, 255))
FROST_SET = make_set(FROST, 'Frostwyrm', helm_frost, FROST_MATS, (150, 225, 255), (24, 34, 56), (60, 66, 84, 255))
TEMPEST_SET = make_set(TEMPEST, 'Tempest Drake', helm_tempest, TEMPEST_MATS, (210, 170, 255), (34, 24, 54), (66, 58, 84, 255))
SETS = (INFERNAL_SET, FROST_SET, TEMPEST_SET)


def register(java, bedrock, item_atlas, definitions):
    for s in SETS:
        forge.register(s, java, bedrock, item_atlas, definitions)


# ---------------------------------------------------------------------------
# Preview sheets (offline renders of the exported geometry and textures)

STAND = {'right_arm': [0, 0, 6], 'left_arm': [0, 0, -6]}


def previews(out):
    from boss_render import Rig, label, render
    out.mkdir(parents=True, exist_ok=True)
    saved = []
    lineup = []
    for s in SETS:
        rig = Rig(forge.preview_model(s, pose=STAND))
        tiles = []
        for yaw, el, title in ((0, 6, 'Front'), (35, 10, 'Three-quarter'), (90, 6, 'Side'), (155, 12, 'Back'), (-140, 18, 'Back left')):
            image, _ = render(rig, 'pose', 0, yaw=yaw, elevation=el, size=560, span=50, center=(0, 21), background=s.background)
            tiles.append(label(image, title, None, s.label_color))
        sheet = Image.new('RGB', (560 * len(tiles), 560), s.background)
        for i, t in enumerate(tiles):
            sheet.paste(t, (i * 560, 0))
        saved.append(out / f'{s.key}-turnaround.png')
        sheet.save(saved[-1])
        for yaw, el in ((-28, 9), (160, 12)):
            image, _ = render(rig, 'pose', 0, yaw=yaw, elevation=el, size=760, span=48, center=(0, 21), background=s.background)
            lineup.append((s, image))
        helm = Rig(forge.preview_model(s, ('helm',), mannequin=True, pose=STAND))
        tiles = []
        for yaw, el in ((0, 4), (-35, 12), (-90, 4), (150, 14)):
            image, _ = render(helm, 'pose', 0, yaw=yaw, elevation=el, size=520, span=25, center=(0, 32.5), background=s.background)
            tiles.append(image)
        sheet = Image.new('RGB', (520 * 4, 520), s.background)
        for i, t in enumerate(tiles):
            sheet.paste(t, (i * 520, 0))
        saved.append(out / f'{s.key}-helm.png')
        sheet.save(saved[-1])
    sheet = Image.new('RGB', (760 * 6, 760), (14, 12, 20))
    for i, (s, image) in enumerate(lineup):
        sheet.paste(label(image, s.pieces[0][1].rsplit(' ', 1)[0], None, s.label_color), (i * 760, 0))
    saved.append(out / 'dragon-lineup.png')
    sheet.save(saved[-1])
    icons = Image.new('RGBA', (4 * 140 + 20, 3 * 140 + 20), (20, 18, 28, 255))
    for row, s in enumerate(SETS):
        for col, (piece, *_rest) in enumerate(s.pieces):
            icon = forge.render_icon(s, piece).resize((128, 128), Image.Resampling.NEAREST)
            icons.alpha_composite(icon, (16 + col * 140, 16 + row * 140))
    saved.append(out / 'dragon-icons.png')
    icons.convert('RGB').save(saved[-1])
    return saved


if __name__ == '__main__':
    import sys
    sys.path.insert(0, str(ROOT / 'evergarden/tools'))
    for path in previews(ROOT / 'previews/dragon-armor'):
        print('Saved', path)
    for s in SETS:
        b = s.baked_helm()
        print(s.key, 'helm cuboids:', len(b.boxes), 'atlas', b.size)
