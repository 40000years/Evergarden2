"""Crimson Revenant: a black-lacquer dread-knight set with glowing crimson cores.

Built to read apart from netherite's matte charcoal: blue-violet black lacquer with
cool specular rims, oxblood under-plates, gunmetal edges and crimson crystal cores
whose light bleeds into cracked veins. Depth is painted (Java draws armor layers on
flat boxes): every plate casts a soft shadow on what lies under it, carries a lit
top/left bevel and a dark lower/right one, curves with the limb (cylinder shading)
and gets a specular streak; spikes are split into a lit and a shadowed half. The
sabaton tops are cut into teeth, so with greaves worn the 0.5 px gap is real depth.

The helm is a real 3D model: a faceted, chamfered great helm with an angry V visor,
a glowing ridge seam, a crown of blades, swept side blades and mandible spikes.
"""
from __future__ import annotations

import itertools
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

import armor_set_forge as forge
from armor_set_forge import ArmorSet, K
from seraph_armor_assets import faces
from weapon_forge import Forge

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'advance-magic/art/revenant-armor'
W, H = 64 * K, 32 * K

PIECES = (
    ('helm', 'Revenant Helm', 'netherite_helmet', 'head', 3),
    ('chestplate', 'Revenant Carapace', 'netherite_chestplate', 'chest', 8),
    ('leggings', 'Revenant Greaves', 'netherite_leggings', 'legs', 6),
    ('boots', 'Revenant Sabatons', 'netherite_boots', 'feet', 3),
)

# Lacquer ramps run deep -> spec; rim is the cool edge light.
OBSIDIAN = {'deep': (6, 5, 10), 'shade': (15, 12, 22), 'base': (27, 23, 38), 'mid': (42, 36, 58), 'hi': (74, 66, 100),
            'spec': (140, 132, 182), 'rim': (186, 178, 228)}
OXBLOOD = {'deep': (26, 3, 8), 'shade': (48, 6, 15), 'base': (78, 11, 24), 'mid': (106, 18, 34), 'hi': (150, 34, 52),
           'spec': (214, 84, 96), 'rim': (240, 130, 136)}
GUNMETAL = {'deep': (22, 24, 31), 'shade': (44, 47, 58), 'base': (74, 78, 94), 'mid': (104, 110, 128), 'hi': (156, 163, 184),
            'spec': (214, 220, 236), 'rim': (240, 244, 255)}
# Edge teeth sit in shadow under their plate: a dimmer lacquer so they read as spikes, not stitching.
TEETH = {'deep': (4, 3, 7), 'shade': (10, 8, 15), 'base': (18, 15, 26), 'mid': (28, 24, 40), 'hi': (46, 40, 64),
         'spec': (70, 64, 96), 'rim': (110, 104, 150)}
UNDER = (13, 10, 17)
CRIMSON = {'halo': (176, 10, 34), 'core': (255, 52, 52), 'hot': (255, 182, 160), 'dim': (84, 4, 16)}

HELM_PALETTE = {
    'obsidian': '#1c1727', 'obsidian_mid': '#2a2338', 'obsidian_light': '#463d5e',
    'steel': '#6b7085', 'steel_light': '#adb3c7', 'oxblood': '#5c0f1f', 'void': '#060509',
    'eye': '#ff3434', 'core': '#ff5a46', 'ember': '#d01430',
}
HELM_GLOW = {'eye', 'core', 'ember'}


# ---------------------------------------------------------------------------
# Painting toolkit (face-local pixels at scale K)


def ramp(pal, value):
    """Map a light value (0 deep .. ~1.15 spec) onto the lacquer ramp."""
    stops = np.array([0, .28, .52, .72, .92, 1.15])
    cols = np.array([pal[k] for k in ('deep', 'shade', 'base', 'mid', 'hi', 'spec')], float)
    v = np.clip(value, 0, 1.15)
    out = np.empty(v.shape + (3,))
    for c in range(3):
        out[..., c] = np.interp(v, stops, cols[:, c])
    return out


def shifted(m, dx, dy):
    out = np.zeros_like(m)
    h, w = m.shape
    ys, yd = (slice(0, h - dy), slice(dy, h)) if dy >= 0 else (slice(-dy, h), slice(0, h + dy))
    xs, xd = (slice(0, w - dx), slice(dx, w)) if dx >= 0 else (slice(-dx, w), slice(0, w + dx))
    out[yd, xd] = m[ys, xs]
    return out


class Face:
    LIGHT = {'front': 1.0, 'outer': .86, 'inner': .66, 'back': .76, 'top': 1.1, 'bottom': .5}

    def __init__(self, w, h, side, seed):
        self.w, self.h = int(w * K), int(h * K)
        self.px = np.zeros((self.h, self.w, 4), float)
        self.light = self.LIGHT[side]
        self.rng = np.random.default_rng(seed)
        yy, xx = np.mgrid[0:self.h, 0:self.w]
        self.X, self.Y = xx + .5, yy + .5

    # -- masks and compositing -------------------------------------------------
    def mask(self, shape, width=None):
        img = Image.new('L', (self.w, self.h))
        d = ImageDraw.Draw(img)
        pts = [tuple(p) for p in shape]
        if width:
            d.line(pts, fill=255, width=int(width), joint='curve')
        elif len(pts) == 2:
            d.rectangle((pts[0][0], pts[0][1], pts[1][0] - 1, pts[1][1] - 1), fill=255)
        else:
            d.polygon(pts, fill=255)
        return np.asarray(img) > 127

    def over(self, color, alpha, mask=None):
        a = np.broadcast_to(np.asarray(alpha, float), (self.h, self.w)).copy()
        if mask is not None:
            a = a * mask
        a = np.clip(a, 0, 1)[..., None]
        col = np.broadcast_to(np.asarray(color, float), (self.h, self.w, 3))
        self.px[..., :3] = self.px[..., :3] * (1 - a) + col * a
        self.px[..., 3] = np.maximum(self.px[..., 3], a[..., 0] * 255)

    def add(self, color, amount):
        """Additive light (glows) on painted pixels only."""
        a = np.clip(amount, 0, 1)[..., None] * (self.px[..., 3:] > 0)
        self.px[..., :3] = np.minimum(255, self.px[..., :3] + np.asarray(color, float) * a)

    def clear(self, shape):
        self.px[self.mask(shape)] = 0

    # -- materials ---------------------------------------------------------------
    def under(self, shape=None):
        """Dark padded undersuit: a fine diagonal weave."""
        m = np.ones((self.h, self.w), bool) if shape is None else self.mask(shape)
        weave = ((self.X + self.Y) % 4 < 1.1) * .35 + ((self.X - self.Y) % 6 < 1.1) * .2
        col = np.asarray(UNDER, float)[None, None] * (1 + weave[..., None] * .9) * self.light
        a = m[..., None]
        self.px[..., :3] = np.where(a, col, self.px[..., :3])
        self.px[..., 3] = np.where(m, 255, self.px[..., 3])

    def plate(self, shape, pal=OBSIDIAN, curve=.3, spec=.3, shadow=.55, bevel=1, tilt=0.0):
        """A lacquered plate with its cast shadow, form shading, bevels and a sheen."""
        m = self.mask(shape)
        if not m.any():
            return m
        if shadow:
            for dy, a in ((2, shadow), (4, shadow * .45), (6, shadow * .2)):
                s = shifted(m, 1, dy) & ~m
                self.over((0, 0, 0), a, s & (self.px[..., 3] > 0))
        ys, xs = np.nonzero(m)
        y0, y1, x0, x1 = ys.min(), ys.max() + 1, xs.min(), xs.max() + 1
        t = (self.Y - y0) / max(1, y1 - y0)
        u = (self.X - (x0 + x1) / 2) / max(1, (x1 - x0) / 2)
        value = self.light * (1.02 - .42 * t + tilt * u) * (1 - curve * u ** 2)
        value = value + self.rng.normal(0, .015, value.shape)
        col = ramp(pal, value)
        if spec:
            band = (self.X - x0) * .55 + (self.Y - y0) - (y1 - y0) * .18
            streak = np.clip(1 - np.abs(band) / 2.2, 0, 1) * (t < .6)
            col = col + (np.asarray(pal['spec'], float) - col) * (streak * spec * self.light)[..., None]
        self.px[..., :3] = np.where(m[..., None], col, self.px[..., :3])
        self.px[..., 3] = np.where(m, 255, self.px[..., 3])
        if bevel:
            top = m & ~shifted(m, 0, 1)
            left = m & ~shifted(m, 1, 0)
            bottom = m & ~shifted(m, 0, -1)
            right = m & ~shifted(m, -1, 0)
            small = (y1 - y0) < 9
            self.over(pal['hi'] if small else pal['rim'], (.6 if small else .7) * self.light, top)
            self.over(pal['hi'], .45, left & ~top)
            self.over(pal['deep'], .9, bottom)
            self.over(pal['shade'], .75, right & ~bottom)
            inner_bottom = shifted(bottom, 0, -1) & m & ~bottom
            self.over(pal['shade'], .5, inner_bottom)
        return m

    def ridge(self, a, b, half_width, pal=OBSIDIAN):
        """A raised ridge from a to b: lit left flank, shadowed right flank, bright crest."""
        (ax, ay), (bx, by) = a, b
        nx, ny = by - ay, -(bx - ax)
        n = math.hypot(nx, ny) or 1
        nx, ny = nx / n * half_width, ny / n * half_width
        lit = [(ax - nx, ay - ny), (ax, ay), (bx, by), (bx - nx, by - ny)]
        dark = [(ax, ay), (ax + nx, ay + ny), (bx + nx, by + ny), (bx, by)]
        self.plate(lit, pal, curve=0, spec=0, shadow=.35, tilt=.25)
        self.plate(dark, pal, curve=0, spec=0, shadow=0, tilt=-.55)
        self.over(pal['rim'], .9, self.mask([a, b], width=1))

    def spike(self, a, b, tip, pal=OBSIDIAN, glow_edge=False, edges=True, lit=.35):
        """A painted spike: lit and shadowed halves split along its spine."""
        mid = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
        m = self.mask([a, b, tip])
        for dy, al in ((2, .5), (4, .22)):
            self.over((0, 0, 0), al, shifted(m, 1, dy) & ~m & (self.px[..., 3] > 0))
        self.plate([a, mid, tip], pal, curve=0, spec=.2 if edges else 0, shadow=0, bevel=0, tilt=lit)
        self.plate([mid, b, tip], pal, curve=0, spec=0, shadow=0, bevel=0, tilt=-.75)
        if edges:
            self.over(pal['rim'], .9, self.mask([a, tip], width=1))
            self.over(pal['deep'], .8, self.mask([b, tip], width=1))
        if glow_edge:
            self.vein([b, tip], 1, halo=3)

    def teeth(self, y, x0, x1, n, depth, pal=TEETH, down=True):
        """A jagged row of small spikes along a plate edge."""
        step = (x1 - x0) / n
        for i in range(n):
            a, b = x0 + i * step, x0 + (i + 1) * step
            tip = ((a + b) / 2 + step * .12, y + depth if down else y - depth)
            self.spike((a, y), (b, y), tip, pal, edges=False, lit=-.2)

    def groove(self, points, width=1):
        """An engraved line: dark cut with a lit lower lip."""
        pts = [tuple(p) for p in points]
        self.over(OBSIDIAN['deep'], .9, self.mask(pts, width=width) & (self.px[..., 3] > 0))
        self.over(OBSIDIAN['hi'], .55, self.mask([(x + .6, y + 1) for x, y in pts], width=1) & (self.px[..., 3] > 0))

    def vein(self, points, width=1, halo=4):
        """A crimson crack: dark rim, burning core and a soft halo bleeding over the plate."""
        pts = [tuple(p) for p in points]
        core = self.mask(pts, width=width)
        glow = Image.fromarray((core * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(halo))
        g = np.asarray(glow, float) / 255
        self.add(CRIMSON['halo'], g * 2.2)
        self.over(CRIMSON['dim'], .9, self.mask(pts, width=width + 2) & ~core & (self.px[..., 3] > 0))
        self.over(CRIMSON['core'], 1, core)
        self.over(CRIMSON['hot'], .55, core & shifted(core, 0, 1))

    def glow(self, cx, cy, r, strength=1.0):
        d = np.hypot(self.X - cx, self.Y - cy) / r
        self.add(CRIMSON['halo'], np.clip(1 - d, 0, 1) ** 1.6 * strength)

    def gem(self, cx, cy, r, setting=GUNMETAL):
        """A crimson crystal core in an angular gunmetal setting, lit from within."""
        self.glow(cx, cy, r * 2.6, .9)
        bezel = [(cx, cy - r * 1.45), (cx + r * 1.25, cy - r * .2), (cx + r * .8, cy + r * 1.2), (cx - r * .8, cy + r * 1.2),
                 (cx - r * 1.25, cy - r * .2)]
        self.plate(bezel, setting, curve=.2, spec=.35, shadow=.6)
        stone = [(cx, cy - r), (cx + r * .85, cy - r * .1), (cx + r * .55, cy + r * .85), (cx - r * .55, cy + r * .85),
                 (cx - r * .85, cy - r * .1)]
        self.over((150, 8, 26), 1, self.mask(stone))
        self.over((255, 64, 56), 1, self.mask([(cx, cy - r), (cx - r * .85, cy - r * .1), (cx, cy + r * .2)]))
        self.over((210, 20, 40), 1, self.mask([(cx, cy - r), (cx + r * .85, cy - r * .1), (cx, cy + r * .2)]))
        self.over((255, 120, 100), 1, self.mask([(cx - r * .3, cy - r * .35), (cx + r * .25, cy - r * .35), (cx, cy + r * .15)]))
        self.add(CRIMSON['hot'], np.clip(1 - np.hypot(self.X - cx, self.Y - cy) / (r * .55), 0, 1) * .9)
        self.over((255, 255, 255), 1, self.mask([(cx - r * .45, cy - r * .55), (cx - r * .15, cy - r * .55),
                                                 (cx - r * .3, cy - r * .3)]))

    def rivet(self, cx, cy, r=1.2):
        self.over(GUNMETAL['deep'], 1, np.hypot(self.X - cx - .5, self.Y - cy - .7) <= r)
        self.over(GUNMETAL['mid'], 1, np.hypot(self.X - cx, self.Y - cy) <= r)
        self.over(GUNMETAL['spec'], 1, np.hypot(self.X - cx + .4, self.Y - cy + .4) <= r * .45)

    def image(self):
        return Image.fromarray(np.clip(self.px, 0, 255).astype(np.uint8), 'RGBA')


def mirror(points, w):
    return [(w - x, y) for x, y in points]


def paste(canvas, face, rect):
    x, y, _, _ = rect
    canvas.alpha_composite(face.image(), (x * K, y * K))


# ---------------------------------------------------------------------------
# Layer 1: carapace (body + arms) and sabatons (leg box, lower part)


def carapace_front(f):
    w = f.w  # 32 x 48
    f.under()
    # Spined gorget whose teeth bite down over the chest.
    f.plate([(0, 0), (w, 0), (w, 5), (0, 5)], OBSIDIAN, curve=.1, spec=.2)
    f.teeth(5, 0, w, 6, 2.6)
    # Sculpted pectoral plates around a sternum channel.
    pec = [(1.5, 7.5), (14.5, 8.5), (14.5, 18), (10, 22.5), (2, 20.5)]
    f.plate(pec, curve=.45, spec=.4)
    f.plate(mirror(pec, w), curve=.45, spec=.4)
    f.groove([(4, 11), (11, 12), (12.5, 17)])
    f.groove(mirror([(4, 11), (11, 12), (12.5, 17)], w))
    # Cracks of light running out of the heart core.
    for path in ([(13, 14), (9, 12.5), (6.5, 14.5), (3.5, 13.5)], [(13.5, 17), (10.5, 19.5), (7, 18.5)]):
        f.vein(path)
        f.vein(mirror(path, w))
    f.gem(w / 2, 14.5, 4.2)
    # Chevron abdomen lames over a glowing seam.
    f.under([(13.5, 22), (18.5, 22), (18.5, 43), (13.5, 43)])
    f.vein([(w / 2, 23), (w / 2, 42)], 1, halo=2.5)
    for k, y in enumerate((23, 28, 33, 38)):
        left = [(2 + k * .6, y), (14.5, y + 2.6), (14.5, y + 6.2), (2.6 + k * .6, y + 4.0)]
        f.plate(left, curve=.1, spec=.12, tilt=.25)
        f.plate(mirror(left, w), curve=.1, spec=.12, tilt=-.35)
    # Oxblood girdle with a small core buckle.
    f.plate([(0, 43), (w, 43), (w, 48), (0, 48)], OXBLOOD, curve=.05, spec=.25)
    for x in (4, w - 4):
        f.rivet(x, 45.5)
    f.gem(w / 2, 45.6, 2.0)


def carapace_back(f):
    w = f.w
    f.under()
    f.plate([(0, 0), (w, 0), (w, 5), (0, 5)], curve=.1, spec=.15)
    f.teeth(5, 0, w, 6, 2.6)
    blade = [(1.5, 7), (13, 8), (12, 21), (2.5, 23)]
    f.plate(blade, curve=.4, spec=.35)
    f.plate(mirror(blade, w), curve=.4, spec=.35)
    for k, y in enumerate((25, 31, 37)):
        lame = [(2, y), (14, y + 1.2), (14, y + 5), (2.5, y + 4)]
        f.plate(lame, curve=.15, spec=.25)
        f.plate(mirror(lame, w), curve=.15, spec=.25)
    # Vertebrae: segmented spine ridge with light leaking between the plates.
    f.under([(13, 6), (19, 6), (19, 43), (13, 43)])
    for y in range(7, 43, 6):
        f.ridge((w / 2, y), (w / 2, y + 4.6), 2.6)
        f.glow(w / 2, y - .3, 2.6, .55)
    f.plate([(0, 43), (w, 43), (w, 48), (0, 48)], OXBLOOD, curve=.05, spec=.2)


def carapace_side(f):
    w = f.w  # 16 x 48
    f.under()
    f.plate([(0, 0), (w, 0), (w, 5), (0, 5)], curve=.1)
    f.teeth(5, 0, w, 3, 2.4)
    for k, y in enumerate((8, 15, 22, 29, 36)):
        f.plate([(1, y), (w - 1, y - 1.2), (w - 1.5, y + 5.4), (1.5, y + 6.2)], curve=.2, spec=.25)
    f.vein([(w / 2, 7), (w / 2 + 1, 42)], 1, halo=2)
    f.plate([(0, 43), (w, 43), (w, 48), (0, 48)], OXBLOOD, curve=.05)


def carapace_top(f):
    f.plate([(0, 0), (f.w, 0), (f.w, f.h), (0, f.h)], curve=.2, spec=.3)
    f.ridge((f.w / 2, 1), (f.w / 2, f.h - 1), 2)


def arm(f, part):
    """Pauldron lames with spikes, glowing gap, bladed vambrace and a clawed gauntlet."""
    w = f.w  # 16 x 48
    f.under()
    for k, (y0, y1) in enumerate(((0, 9), (8, 15), (14, 20))):
        f.plate([(0, y0), (w, y0), (w, y1 - 1), (0, y1)], curve=.35, spec=.35 - k * .08)
        if part != 'inner':
            f.teeth(y1 - .5, 0, w, 4, 2.4)
    if part == 'outer':
        f.spike((1.5, 9), (7, 9), (3.5, .2))
        f.spike((8, 8.5), (14.5, 8.5), (12.5, -.5))
        f.gem(w / 2, 12.5, 2.5)
    f.plate([(0, 23), (w, 23), (w, 41), (0, 41)], curve=.4, spec=.4)
    if part == 'outer':
        # Forearm blade fin sweeping up toward the elbow, its edge burning.
        f.spike((4.5, 40.5), (11.5, 40.5), (9, 22.5), glow_edge=True)
    elif part == 'front':
        f.groove([(3, 26), (8, 30), (13, 26)])
        f.groove([(3, 33), (8, 37), (13, 33)])
        f.vein([(w / 2, 30.5), (w / 2, 36)], 1, halo=2)
    elif part == 'back':
        f.ridge((w / 2, 24), (w / 2, 40), 2.2)
    # Clawed gauntlet: oxblood plates, finger segments, ember knuckles.
    f.plate([(0, 41), (w, 41), (w, 48), (0, 48)], OXBLOOD, curve=.3, spec=.25)
    if part in ('front', 'outer'):
        for x in (2, 6, 10, 14):
            f.groove([(x, 43.5), (x, 48)])
        for x in (4, 8, 12):
            f.glow(x, 42.6, 1.8, .7)


def pauldron_top(f):
    f.plate([(0, 0), (f.w, 0), (f.w, f.h), (0, f.h)], curve=.3, spec=.4)
    c = f.w / 2
    for a, b, tip in (((2, 6), (6, 9), (1, 1)), ((10, 9), (14, 6), (15, 1)), ((6, 4), (10, 4), (8, -1))):
        f.spike(a, b, tip)
    f.gem(c, f.h / 2 + 2, 2.2)


def sabaton(f, part):
    """Boots: the upper leg stays clear; a toothed rim cuts real depth over greaves."""
    w = f.w  # 16 x 48
    f.clear([(0, 0), (w, 48)])
    f.plate([(0, 31), (w, 31), (w, 46), (0, 46)], curve=.4, spec=.35)
    # The rim's teeth point up; between them the clear texture shows the greave below.
    f.teeth(31.2, 0, w, 4, 3.0, down=False)
    if part == 'front':
        f.ridge((w / 2, 33), (w / 2, 45.5), 2.2)
        f.plate([(1, 42), (w - 1, 42), (w - 2, 46), (2, 46)], GUNMETAL, curve=.3, spec=.5)
    elif part == 'outer':
        f.gem(w / 2, 37, 2.2)
        f.glow(w / 2, 41.5, 2.2, .5)
    elif part == 'back':
        f.spike((5, 44), (11, 44), (8, 35))
    f.plate([(0, 46), (w, 46), (w, 48), (0, 48)], GUNMETAL, curve=.1, spec=.2, shadow=0)


def layer_one():
    canvas = Image.new('RGBA', (W, H))
    seed = itertools.count(701)
    body = faces(16, 16, 8, 12, 4)
    for name, side, painter in (('front', 'front', carapace_front), ('back', 'back', carapace_back),
                                ('right', 'outer', carapace_side), ('left', 'outer', carapace_side), ('top', 'top', carapace_top)):
        f = Face(*body[name][2:], side, next(seed))
        painter(f)
        paste(canvas, f, body[name])
    f = Face(*body['bottom'][2:], 'bottom', next(seed))
    f.under()
    paste(canvas, f, body['bottom'])
    arms = faces(40, 16, 4, 12, 4)
    for name, part in (('right', 'outer'), ('front', 'front'), ('left', 'inner'), ('back', 'back')):
        f = Face(*arms[name][2:], 'front' if part == 'front' else part, next(seed))
        arm(f, part)
        paste(canvas, f, arms[name])
    f = Face(*arms['top'][2:], 'top', next(seed))
    pauldron_top(f)
    paste(canvas, f, arms['top'])
    f = Face(*arms['bottom'][2:], 'bottom', next(seed))
    f.plate([(0, 0), (f.w, 0), (f.w, f.h), (0, f.h)], OXBLOOD, curve=0, spec=0, shadow=0)
    paste(canvas, f, arms['bottom'])
    legs = faces(0, 16, 4, 12, 4)
    for name, part in (('front', 'front'), ('right', 'outer'), ('left', 'inner'), ('back', 'back')):
        f = Face(*legs[name][2:], 'front' if part == 'front' else part, next(seed))
        sabaton(f, part)
        paste(canvas, f, legs[name])
    f = Face(*legs['bottom'][2:], 'bottom', next(seed))
    f.plate([(0, 0), (f.w, 0), (f.w, f.h), (0, f.h)], GUNMETAL, curve=0, spec=0, shadow=0)
    paste(canvas, f, legs['bottom'])
    return canvas


# ---------------------------------------------------------------------------
# Layer 2: girdle with blade tassets, cuisses, core knees and spined greaves


def girdle(f, part):
    w = f.w
    f.clear([(0, 0), (w, 48)])
    f.plate([(0, 26), (w, 26), (w, 31), (0, 31)], OXBLOOD, curve=.1, spec=.3)
    f.under([(0, 31), (w, 48)])
    n = 4 if f.w > 20 else 2
    step = w / n
    for i in range(n):
        x0, x1 = i * step + .5, (i + 1) * step - .5
        f.plate([(x0, 31), (x1, 31), (x1 - 1, 44), ((x0 + x1) / 2, 48), (x0 + 1, 44)], curve=.3, spec=.3)
        f.vein([((x0 + x1) / 2, 33), ((x0 + x1) / 2, 44)], 1, halo=1.6)
    if part == 'front':
        f.gem(w / 2, 28.6, 2.2)


def leg(f, part):
    w = f.w  # 16 x 48
    f.under()
    f.plate([(0, 0), (w, 0), (w, 15), (0, 17.5)], curve=.4, spec=.4)
    if part == 'outer':
        f.spike((3, 15), (9, 14), (13, 4))
        f.groove([(2, 3), (2, 13)])
    elif part == 'front':
        f.groove([(3, 4), (8, 8), (13, 4)])
    # Knee cop: the second crimson core of the set, with a downward spike.
    f.plate([(1, 19.5), (w - 1, 19.5), (w - 2, 26), (2, 26)], curve=.45, spec=.45)
    if part == 'front':
        f.gem(w / 2, 22.6, 2.8)
        f.spike((5.5, 25.5), (10.5, 25.5), (8, 30.5))
    elif part == 'outer':
        f.rivet(w / 2, 22.6, 1.5)
    f.plate([(0, 27), (w, 27), (w, 48), (0, 48)], curve=.42, spec=.4)
    if part == 'front':
        f.ridge((w / 2, 28.5), (w / 2, 47), 2.4)
        f.vein([(w / 2 + 3, 30), (w / 2 + 3.5, 40)], 1, halo=1.8)
    elif part == 'outer':
        f.spike((4, 40), (9, 40), (13, 31))
        f.spike((4, 33), (9, 33), (13, 26.5))
    elif part == 'back':
        f.groove([(w / 2, 29), (w / 2, 46)])


def layer_two():
    canvas = Image.new('RGBA', (W, H))
    seed = itertools.count(901)
    body = faces(16, 16, 8, 12, 4)
    for name, side, part in (('front', 'front', 'front'), ('back', 'back', 'back'), ('right', 'outer', 'side'), ('left', 'outer', 'side')):
        f = Face(*body[name][2:], side, next(seed))
        girdle(f, part)
        paste(canvas, f, body[name])
    legs = faces(0, 16, 4, 12, 4)
    for name, part in (('front', 'front'), ('right', 'outer'), ('left', 'inner'), ('back', 'back')):
        f = Face(*legs[name][2:], 'front' if part == 'front' else part, next(seed))
        leg(f, part)
        paste(canvas, f, legs[name])
    f = Face(*legs['bottom'][2:], 'bottom', next(seed))
    f.under()
    paste(canvas, f, legs['bottom'])
    return canvas


# ---------------------------------------------------------------------------
# The helm


def helm():
    f = Forge('revenant_helm')

    def section(X, Y, Z, half, chamfer):
        """Chamfered octagon (encloses a skin's hat layer up to y 32.6), V visor prow and a
        faceted crown that narrows faster across than front-to-back into a crest."""
        rise = np.maximum(Y - 32.6, 0)
        hx = half - rise * 1.55
        hz = np.where(Z < 0, half + 1.1 * np.clip(1 - np.abs(X) / 3.2, 0, 1), half) - rise * .75
        return (np.abs(X) <= hx) & (np.abs(Z) <= hz) & (np.abs(X) + np.abs(Z) <= hx + hz - chamfer) & (hx > 0)

    def shell(X, Y, Z):
        body = section(X, Y, Z, 5.3, 1.4) & ~section(X, Y, Z, 4.6, 1.2) & (Y >= 23.5) & (Y <= 35.6)
        front = Z < -3.8
        brow = 28.45 + .3 * np.abs(X)
        slit = body & front & (np.abs(X) < 3.9) & (np.abs(Y - brow) < .45)
        eyes = slit & (np.abs(X) > .8) & (np.abs(X) < 2.9) & (np.abs(Y - brow) < .27)
        grille = body & front & (Y > 24.6) & (Y < 27.3) & (np.abs(X) < 2.3) & ((np.floor((X + 2.3) / .55) % 2) == 0)
        brow_band = body & front & (Y > brow + .5) & (Y < brow + 1.05)
        rim = body & (Y < 24.15)
        seam = body & (np.abs(X) < .3) & (Y > 32.9)
        cheeks = body & front & (Y < 27.4) & (np.abs(X) > 2.7)
        flank = body & (np.abs(X) > 4.6) & (Y > 25.5) & (Y < 32.4) & (np.abs(Z + .6) < .35)
        chamfer = body & ~section(X, Y, Z, 5.3, 2.2) & (Y <= 32.6)
        crest = body & (Y > 32.6)
        return [('eye', eyes), ('void', slit | grille), ('ember', seam), ('steel', brow_band | rim | flank),
                ('obsidian_light', cheeks | chamfer), ('obsidian_mid', crest), ('obsidian', body & (Y > 27)), ('obsidian_mid', body)]
    f.volume(shell, (-5.4, 23.5, -6.5), (5.4, 35.7, 5.4), cell=.25, bone='head')

    def taper(points, r0, r1, mat, cell=.25):
        pts = np.asarray(points, float)
        seg = np.linalg.norm(np.diff(pts, axis=0), axis=1)
        cum = np.concatenate([[0], np.cumsum(seg)])
        lo, hi = pts.min(0) - r0 - cell, pts.max(0) + r0 + cell

        def material(X, Y, Z):
            best = np.full(X.shape, np.inf)
            along = np.zeros(X.shape)
            P = np.stack([X, Y, Z], -1)
            for i, (a, b) in enumerate(zip(pts, pts[1:])):
                ab = b - a
                t = np.clip(((P - a) @ ab) / (ab @ ab), 0, 1)
                d = np.linalg.norm(P - (a + t[..., None] * ab), axis=-1)
                better = d < best
                best = np.where(better, d, best)
                along = np.where(better, (cum[i] + t * seg[i]) / cum[-1], along)
            return [(mat, best <= r0 + (r1 - r0) * along)]
        f.volume(material, lo, hi, cell, 'head')

    # Forehead core in a gunmetal diamond.
    f.box(-1.0, 30.2, -6.55, 1.0, 31.9, -6.1, 'steel', 'head')
    f.box(-.55, 30.5, -6.75, .55, 31.6, -6.55, 'core', 'head')
    # Crown of blades: a tall central blade raking back, two over the brow.
    taper([(0, 34.4, -2.2), (0, 37.6, -1.0), (0, 40.6, .7), (0, 42.2, 1.8)], 1.0, .1, 'obsidian_light')
    for s in (-1, 1):
        taper([(s * 1.9, 33.7, -4.9), (s * 2.4, 36.2, -4.5), (s * 2.8, 38.0, -4.0)], .62, .1, 'steel')
        # Swept side blades, then smaller ones behind them.
        taper([(s * 4.6, 31.8, -1.2), (s * 6.0, 34.0, .2), (s * 6.8, 36.6, 2.0), (s * 7.0, 38.6, 3.6)], .9, .1, 'obsidian_light')
        taper([(s * 3.6, 32.4, 2.6), (s * 4.6, 34.4, 4.8), (s * 5.0, 35.8, 6.6)], .62, .1, 'obsidian_mid')
        # Mandible spikes hooking down and forward from the jaw.
        taper([(s * 4.3, 25.2, -4.4), (s * 4.9, 23.9, -5.6), (s * 5.1, 22.9, -6.5)], .55, .1, 'steel')
    return f


SET = ArmorSet(key='revenant', pieces=PIECES, layer_one=layer_one, layer_two=layer_two, helm=helm,
               helm_palette=HELM_PALETTE, helm_glow=HELM_GLOW, art=ART, credit='Evergarden / revenant_armor_assets.py',
               atlas_seed=6613, label_color=(255, 120, 120), background=(118, 122, 136), skin=(88, 80, 96, 255))


def register(java, bedrock, item_atlas, definitions):
    forge.register(SET, java, bedrock, item_atlas, definitions)


if __name__ == '__main__':
    import sys
    sys.path.insert(0, str(ROOT / 'evergarden/tools'))
    for path in forge.previews(SET, ROOT / 'previews/revenant-armor'):
        print('Saved', path)
    print('helm cuboids:', len(helm().cubes))
