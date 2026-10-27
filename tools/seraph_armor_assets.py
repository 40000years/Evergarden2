"""Seraph armor: a white-and-gold paladin set worn like vanilla armor on Java and Bedrock.

Java (resource format 75+)
  Cuirass, greaves and sabatons carry `equippable` with the equipment asset
  `advance_magic:seraph`; the client draws its humanoid / humanoid_leggings
  layers on its own armor model, so the armor moves with every limb. The
  layers are painted here at 4x (256x128) on the vanilla 64x32 armor layout.
  The helm is an equippable head item without an asset: the client then
  draws its item model on the head, which allows a real 3D helm (horns,
  crest, crown spikes, visor) built from the cuboids below.

Bedrock (Geyser 2.12)
  Custom item definitions declare the equippable slot and protection value,
  so Bedrock players can wear the pieces. Attachables draw the same textures
  on boxes identical to Java's armor model (bones named like the player's,
  box UV on the 64x32 layout) and the helm from the same cuboids.

Helm cuboids are authored in head pixels in Bedrock model space: the head box
spans x/z -4..4 and y 24..32, the face is -Z and the wearer's right is -X.
"""
from __future__ import annotations

import itertools
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

import armor_set_forge as forge
from armor_set_forge import ArmorSet, K
from weapon_forge import Forge

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'advance-magic/art/seraph-armor'
W, H = 64 * K, 32 * K      # 4x the vanilla 64x32 armor layout

# (id, title, base item, Geyser slot, Bukkit slot, protection)
PIECES = (
    ('helm', 'Seraph Helm', 'netherite_helmet', 'head', 3),
    ('chestplate', 'Seraph Cuirass', 'netherite_chestplate', 'chest', 8),
    ('leggings', 'Seraph Greaves', 'netherite_leggings', 'legs', 6),
    ('boots', 'Seraph Sabatons', 'netherite_boots', 'feet', 3),
)

# ---------------------------------------------------------------------------
# Palette

WHITE = {'hi': (255, 255, 255), 'base': (231, 235, 243), 'mid': (210, 216, 229), 'shade': (176, 186, 205), 'deep': (128, 139, 163)}
GOLD = {'hi': (255, 240, 170), 'base': (231, 176, 66), 'mid': (196, 137, 42), 'shade': (142, 92, 30), 'deep': (92, 57, 20)}
NAVY = {'hi': (58, 74, 120), 'base': (31, 40, 72), 'mid': (25, 32, 58), 'shade': (18, 23, 44), 'deep': (10, 13, 26)}
CLOTH = {'hi': (255, 255, 255), 'base': (240, 242, 247), 'mid': (222, 227, 238), 'shade': (190, 198, 216), 'deep': (150, 160, 184)}
BLUE = (106, 214, 255)
BLUE_DEEP = (30, 120, 214)

# Helm material tiles (16px in a 128px atlas), shared with Bedrock.
HELM_PALETTE = {
    'white': '#e8ecf3', 'white_mid': '#cfd6e3', 'white_dark': '#a4afc4',
    'gold': '#e2ad44', 'gold_light': '#ffe7a1', 'gold_dark': '#93601f',
    'black': '#121621', 'navy': '#1f2848',
    'eye': '#ffc65a', 'gem': '#6ad6ff',
}
HELM_GLOW = {'eye', 'gem'}


# ---------------------------------------------------------------------------
# Painting helpers (face-local pixel space at scale K)


def faces(u, v, w, h, d):
    """Vanilla box UV net in base units: name -> (x, y, w, h)."""
    return {'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h),
            'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h)}


class Face:
    """One face of the armor net, painted at K pixels per armor pixel."""

    def __init__(self, w, h, seed):
        self.w, self.h = w * K, h * K
        self.px = np.zeros((self.h, self.w, 4), float)
        self.rng = np.random.default_rng(seed)

    # -- fills ---------------------------------------------------------------
    def plate(self, x0, y0, x1, y1, pal=WHITE, light=.0, bevel=True):
        """A lacquered plate: soft top-lit gradient, bevel highlight and shadow."""
        x0, y0, x1, y1 = (int(round(v)) for v in (x0, y0, x1, y1))
        if x1 <= x0 or y1 <= y0:
            return
        hgt = y1 - y0
        t = np.linspace(0, 1, hgt)[:, None]
        base, mid, shade = (np.array(pal[k], float) for k in ('base', 'mid', 'shade'))
        col = np.where(t[..., None] < .55, base + (mid - base) * (t[..., None] / .55), mid + (shade - mid) * ((t[..., None] - .55) / .45))
        col = np.repeat(col, x1 - x0, axis=1)
        col *= 1 + light
        col += self.rng.normal(0, 2.2, col.shape[:2])[..., None]
        self.px[y0:y1, x0:x1, :3] = col
        self.px[y0:y1, x0:x1, 3] = 255
        if bevel and hgt > 3 and x1 - x0 > 3:
            self.px[y0, x0:x1, :3] = pal['hi']
            self.px[y0:y1, x0, :3] = np.array(pal['hi']) * .5 + np.array(pal['base']) * .5
            self.px[y1 - 1, x0:x1, :3] = pal['deep']
            self.px[y0:y1, x1 - 1, :3] = np.array(pal['shade']) * .6 + np.array(pal['deep']) * .4

    def mail(self, x0, y0, x1, y1):
        """Dark undersuit with a fine chainmail glint."""
        for y in range(int(y0), int(y1)):
            for x in range(int(x0), int(x1)):
                c = NAVY['base']
                if (x // 2 + y // 2) % 2 == 0 and y % 2 == 0:
                    c = NAVY['hi']
                elif (x + y) % 4 == 3:
                    c = NAVY['shade']
                self.px[y, x, :3] = c
                self.px[y, x, 3] = 255

    def clear(self, x0, y0, x1, y1):
        self.px[int(y0):int(y1), int(x0):int(x1)] = 0

    # -- strokes -------------------------------------------------------------
    def _draw(self, fn):
        layer = Image.new('RGBA', (self.w, self.h))
        fn(ImageDraw.Draw(layer))
        a = np.asarray(layer, float)
        m = a[..., 3] > 0
        self.px[m] = a[m]

    def gold(self, points, width=2, emboss=True):
        """Raised gold wire: shadow below, body, highlight on its upper edge."""
        pts = [tuple(p) for p in points]
        if emboss:
            self._draw(lambda d: d.line([(x + .6, y + 1) for x, y in pts], fill=GOLD['deep'], width=width, joint='curve'))
        self._draw(lambda d: d.line(pts, fill=GOLD['base'], width=width, joint='curve'))
        if width >= 2:
            self._draw(lambda d: d.line([(x, y - width / 2 + .5) for x, y in pts], fill=GOLD['hi'], width=1))

    def band(self, y0, y1, x0=0, x1=None, pal=GOLD):
        x1 = self.w if x1 is None else x1
        self.plate(x0, y0, x1, y1, pal, bevel=True)

    def curl(self, cx, cy, r, flip=False, turns=1.35, width=1):
        """A filigree scroll: a tightening gold spiral with an outward tail."""
        pts = []
        steps = 40
        for i in range(steps + 1):
            t = i / steps
            ang = (t * turns) * math.tau * (-1 if flip else 1) + math.pi
            rad = r * (1 - .78 * t)
            pts.append((cx + math.cos(ang) * rad, cy + math.sin(ang) * rad))
        self.gold(pts, width=width + 1, emboss=False)

    def rune(self, cx, cy, r):
        """Engraved circle rune: ring, inner cross and four ticks."""
        def draw(d):
            d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=GOLD['mid'], width=1)
            d.ellipse((cx - r * .55, cy - r * .55, cx + r * .55, cy + r * .55), outline=GOLD['shade'], width=1)
            d.line((cx, cy - r, cx, cy + r), fill=GOLD['mid'], width=1)
            d.line((cx - r, cy, cx + r, cy), fill=GOLD['mid'], width=1)
        self._draw(draw)

    def sun(self, cx, cy, r, gem=True, rays=12):
        """Gold sunburst boss with a glowing blue heart."""
        def draw(d):
            for i in range(rays):
                a = math.tau * i / rays - math.pi / 2
                ln = r * (1.75 if i % 2 == 0 else 1.35)
                w = 2 if i % 2 == 0 else 1
                d.line((cx + math.cos(a) * r * .7, cy + math.sin(a) * r * .7, cx + math.cos(a) * ln, cy + math.sin(a) * ln),
                       fill=GOLD['base'], width=w)
            d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=GOLD['mid'], outline=GOLD['deep'])
            d.ellipse((cx - r * .78, cy - r * .85, cx + r * .62, cy + r * .5), fill=GOLD['base'])
            d.ellipse((cx - r * .55, cy - r * .7, cx + r * .1, cy - r * .1), fill=GOLD['hi'])
            if gem:
                g = max(1.4, r * .42)
                d.ellipse((cx - g, cy - g, cx + g, cy + g), fill=BLUE_DEEP)
                d.ellipse((cx - g * .6, cy - g * .7, cx + g * .2, cy), fill=BLUE)
        self._draw(draw)

    def boss(self, cx, cy, r):
        """Round gold medallion with a ring and blue jewel (pauldron / knee bosses)."""
        def draw(d):
            d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=GOLD['shade'])
            d.ellipse((cx - r + 1, cy - r + 1, cx + r - 1, cy + r - 1), fill=GOLD['base'])
            d.ellipse((cx - r + 1, cy - r + 1, cx + r * .3, cy + r * .1), fill=GOLD['hi'])
            d.ellipse((cx - r * .55, cy - r * .55, cx + r * .55, cy + r * .55), fill=GOLD['mid'])
            g = r * .32
            d.ellipse((cx - g, cy - g, cx + g, cy + g), fill=BLUE_DEEP)
            d.point((cx - g * .4, cy - g * .4), fill=BLUE)
        self._draw(draw)

    def stud(self, cx, cy, r=1.5):
        self._draw(lambda d: d.polygon([(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)], fill=GOLD['base'], outline=GOLD['deep']))

    def image(self, mirror=False):
        im = Image.fromarray(np.clip(self.px, 0, 255).astype(np.uint8), 'RGBA')
        return im.transpose(Image.Transpose.FLIP_LEFT_RIGHT) if mirror else im


def paste(canvas, face, rect):
    x, y, _, _ = rect
    canvas.alpha_composite(face.image(), (x * K, y * K))


# ---------------------------------------------------------------------------
# Layer 1 (humanoid): cuirass on body + arms, sabatons on the legs


def cuirass_front(f):
    w, h = f.w, f.h  # 32 x 48
    f.mail(0, 0, w, h)
    f.plate(0, 0, w, 30, WHITE)
    f.band(0, 4)                                   # gorget rim
    f.gold([(1, 5), (1, 27)], 2)                   # side edges of the breastplate
    f.gold([(w - 2, 5), (w - 2, 27)], 2)
    # Pectoral plates meet in a shallow V above the sternum.
    f.gold([(2, 21), (8, 25), (w / 2, 28)], 2)
    f.gold([(w - 2, 21), (w - 8, 25), (w / 2, 28)], 2)
    f.gold([(w / 2, 4), (w / 2, 7)], 2)
    # Filigree scrolls sweep from the shoulders toward the emblem.
    f.curl(8.5, 12.5, 4.6)
    f.curl(w - 8.5, 12.5, 4.6, flip=True)
    f.gold([(4, 6), (6.5, 9)], 1, emboss=False)
    f.gold([(w - 4, 6), (w - 6.5, 9)], 1, emboss=False)
    f.sun(w / 2, 15, 4.2)
    # Abdomen lames over mail, each edged in gold with a centre stud.
    for k, y in enumerate((30, 35, 40)):
        inset = 2 + k
        f.plate(inset, y, w - inset, y + 4, WHITE, light=-.02 * k)
        f.gold([(inset, y + 4), (w - inset - 1, y + 4)], 1, emboss=False)
        f.stud(w / 2 - .5, y + 2, 1.2)
    # Gold belt with a sun buckle across the hips.
    f.band(44, 48)
    f.sun(w / 2, 45.5, 2.6, rays=8)


def cuirass_back(f):
    w, h = f.w, f.h
    f.mail(0, 0, w, h)
    f.plate(0, 0, w, 30, WHITE)
    f.band(0, 4)
    f.gold([(w / 2, 5), (w / 2, 28)], 2)          # spine ridge
    for y in (9, 15, 21):
        f.stud(w / 2 - .5, y, 1.6)
    # Folded-wing emblem across the shoulder blades.
    for s in (-1, 1):
        cx = w / 2 + s * 3
        pts = [(cx, 8), (cx + s * 6, 6), (cx + s * 11, 7.5), (cx + s * 13, 11)]
        f.gold(pts, 2)
        f.gold([(cx + s * 1, 11), (cx + s * 7, 10), (cx + s * 11, 13.5)], 1, emboss=False)
        f.gold([(cx + s * 1.5, 14.5), (cx + s * 6.5, 14), (cx + s * 9.5, 17)], 1, emboss=False)
    f.gold([(2, 27), (w - 3, 27)], 1, emboss=False)
    for k, y in enumerate((30, 35, 40)):
        inset = 2 + k
        f.plate(inset, y, w - inset, y + 4, WHITE, light=-.02 * k)
        f.gold([(inset, y + 4), (w - inset - 1, y + 4)], 1, emboss=False)
    f.band(44, 48)


def cuirass_side(f):
    w, h = f.w, f.h  # 16 x 48
    f.mail(0, 0, w, h)
    f.plate(0, 0, w, 29, WHITE, light=-.02)
    f.band(0, 4)
    f.gold([(w / 2, 6), (w / 2, 26)], 1, emboss=False)
    f.rune(w / 2, 16, 4)
    for y in (30, 35, 40):
        f.plate(1, y, w - 1, y + 4, WHITE, light=-.04)
    f.band(44, 48)


def cuirass_top(f):
    f.plate(0, 0, f.w, f.h, WHITE)
    f.gold([(0, f.h / 2), (f.w, f.h / 2)], 2)


def pauldron_arm(f, outer=False, front=False):
    """Shoulder lames, mail gap, engraved vambrace and dark glove (one arm face)."""
    w, h = f.w, f.h  # 16 x 48
    f.mail(0, 0, w, h)
    # Three overlapping shoulder lames, largest on top.
    for k, (y0, y1) in enumerate(((0, 7), (7, 12), (12, 17))):
        f.plate(0, y0, w, y1, WHITE, light=.02 - .03 * k)
        f.gold([(0, y1 - 1), (w, y1 - 1)], 2)
    if outer:
        f.boss(w / 2, 6.5, 4.3)
    elif front:
        f.curl(w / 2, 4, 3, turns=1.1)
    # Vambrace with gold rims and a filigree scroll.
    f.plate(0, 21, w, 42, WHITE)
    f.band(21, 23)
    f.band(40, 42)
    if outer or front:
        f.curl(w / 2 + (1 if front else 0), 30, 3.6, flip=front)
        f.stud(w / 2, 36.5, 1.4)
    else:
        f.gold([(w / 2, 25), (w / 2, 38)], 1, emboss=False)
    # Gauntlet: dark glove with gold knuckle plates.
    f.plate(0, 42, w, 48, NAVY, bevel=False)
    f.gold([(1, 44), (w - 2, 44)], 1, emboss=False)
    if front or outer:
        f.gold([(2, 46.5), (w - 3, 46.5)], 1, emboss=False)


def pauldron_top(f):
    f.plate(0, 0, f.w, f.h, WHITE, light=.04)
    f._draw(lambda d: d.rectangle((0, 0, f.w - 1, f.h - 1), outline=GOLD['base'], width=2))
    f.boss(f.w / 2, f.h / 2, 3.2)


def sabaton(f, part):
    """Boots live in the lowest third of the leg box; the rest stays clear."""
    w, h = f.w, f.h  # 16 x 48
    f.clear(0, 0, w, h)
    f.band(30, 33)
    f.plate(0, 33, w, 45, WHITE)
    if part == 'front':
        f.gold([(w / 2, 34), (w / 2, 44)], 2)
        f.gold([(3, 39), (w / 2, 42), (w - 4, 39)], 1, emboss=False)
    elif part == 'outer':
        f.boss(w / 2, 38.5, 3.4)
    elif part == 'back':
        f.gold([(w / 2, 34), (w / 2, 43)], 1, emboss=False)
        # Small spur at the heel.
        f.plate(w / 2 - 2, 41, w / 2 + 2, 45, GOLD)
    f.band(45, 48)


def layer_one():
    canvas = Image.new('RGBA', (W, H))
    seed = itertools.count(101)
    body = faces(16, 16, 8, 12, 4)
    for name, painter in (('front', cuirass_front), ('back', cuirass_back), ('right', cuirass_side), ('left', cuirass_side),
                          ('top', cuirass_top)):
        f = Face(*body[name][2:], next(seed))
        painter(f)
        paste(canvas, f, body[name])
    bottom = Face(*body['bottom'][2:], next(seed))
    bottom.mail(0, 0, bottom.w, bottom.h)
    paste(canvas, bottom, body['bottom'])
    arm = faces(40, 16, 4, 12, 4)
    for name, kw in (('right', {'outer': True}), ('front', {'front': True}), ('left', {}), ('back', {})):
        f = Face(*arm[name][2:], next(seed))
        pauldron_arm(f, **kw)
        paste(canvas, f, arm[name])
    top = Face(*arm['top'][2:], next(seed))
    pauldron_top(top)
    paste(canvas, top, arm['top'])
    hand = Face(*arm['bottom'][2:], next(seed))
    hand.plate(0, 0, hand.w, hand.h, NAVY, bevel=False)
    paste(canvas, hand, arm['bottom'])
    leg = faces(0, 16, 4, 12, 4)
    for name, part in (('front', 'front'), ('right', 'outer'), ('left', 'inner'), ('back', 'back')):
        f = Face(*leg[name][2:], next(seed))
        sabaton(f, part)
        paste(canvas, f, leg[name])
    sole = Face(*leg['bottom'][2:], next(seed))
    sole.plate(0, 0, sole.w, sole.h, NAVY, bevel=False)
    sole.gold([(0, 1), (sole.w, 1)], 2)
    paste(canvas, sole, leg['bottom'])
    return canvas


# ---------------------------------------------------------------------------
# Layer 2 (humanoid_leggings): belt and faulds on the body, cuisses, knees, greaves


def waist(f, part):
    w, h = f.w, f.h
    f.clear(0, 0, w, h)
    f.band(30, 34)
    if part == 'front':
        f.sun(w / 2, 32, 2.6, rays=8)
        # Hip faulds either side of the tabard.
        for x0, x1 in ((0, 9), (w - 9, w)):
            f.plate(x0, 34, x1, 48, WHITE)
            f.gold([(x0, 47), (x1, 47)], 1, emboss=False)
        f.plate(10, 34, w - 10, 48, CLOTH)
        f.gold([(10, 34), (10, 48)], 1, emboss=False)
        f.gold([(w - 11, 34), (w - 11, 48)], 1, emboss=False)
        f.rune(w / 2 - .5, 41, 3)
    else:
        f.plate(0, 34, w, 48, WHITE, light=-.03)
        f.gold([(0, 47), (w, 47)], 1, emboss=False)
        if part == 'side':
            f.boss(w / 2, 40, 3)


def greave(f, part):
    """One leg face: cuisse, knee cop and greave; a tabard hangs on the inner front."""
    w, h = f.w, f.h  # 16 x 48
    f.mail(0, 0, w, h)
    f.plate(0, 0, w, 19, WHITE)
    f.band(19, 21)
    f.plate(0, 21, w, 27, WHITE, light=.03)
    f.band(27, 28)
    f.plate(0, 28, w, 48, WHITE, light=-.02)
    if part == 'front':
        # The tabard: white cloth, gold edges and runes, pointed hem.
        x0 = w / 2
        f.plate(x0, 0, w, 40, CLOTH, bevel=False)
        f.gold([(x0, 0), (x0, 40)], 1, emboss=False)
        f._draw(lambda d: d.polygon([(x0, 40), (w, 40), (w, 45), (x0 + 4, 43)], fill=CLOTH['mid'], outline=GOLD['base']))
        for y in (8, 20, 32):
            f.rune(w - 3.5, y, 2.6)
        f.boss(w / 4 + .5, 24, 3)
        f.gold([(w / 4, 31), (w / 4, 46)], 1, emboss=False)
        f.gold([(1, 4), (x0 - 1, 4)], 1, emboss=False)
    elif part == 'outer':
        f.rune(w / 2, 9, 4)
        f.boss(w / 2, 24, 2.6)
        f.gold([(w / 2, 31), (w / 2, 46)], 2)
    elif part == 'back':
        f.gold([(w / 2, 2), (w / 2, 17)], 1, emboss=False)
        f.gold([(w / 2, 30), (w / 2, 46)], 1, emboss=False)
    else:
        f.gold([(w / 2, 31), (w / 2, 46)], 1, emboss=False)


def layer_two():
    canvas = Image.new('RGBA', (W, H))
    seed = itertools.count(301)
    body = faces(16, 16, 8, 12, 4)
    for name, part in (('front', 'front'), ('back', 'back'), ('right', 'side'), ('left', 'side')):
        f = Face(*body[name][2:], next(seed))
        waist(f, part)
        paste(canvas, f, body[name])
    leg = faces(0, 16, 4, 12, 4)
    for name, part in (('front', 'front'), ('right', 'outer'), ('left', 'inner'), ('back', 'back')):
        f = Face(*leg[name][2:], next(seed))
        greave(f, part)
        paste(canvas, f, leg[name])
    bottom = Face(*leg['bottom'][2:], next(seed))
    bottom.plate(0, 0, bottom.w, bottom.h, NAVY, bevel=False)
    paste(canvas, bottom, leg['bottom'])
    return canvas


# ---------------------------------------------------------------------------
# The helm


def helm():
    f = Forge('seraph_helm')

    def section(X, Y, Z, half):
        """Rounded (p=4.5) cross-section with a V prow at the visor and a domed crown."""
        k = np.sqrt(np.clip(1 - (np.maximum(Y - 32.6, 0) / 2.7) ** 2, 0, 1))
        hz = np.where(Z < 0, half + .7 * np.clip(1 - np.abs(X) / 3.6, 0, 1), half)
        return (np.abs(X) / (half * k + 1e-9)) ** 4.5 + (np.abs(Z) / (hz * k + 1e-9)) ** 4.5 <= 1

    def shell(X, Y, Z):
        # Up to y 32.6 the section encloses a skin's hat layer (head box +0.5 px).
        body = section(X, Y, Z, 5.3) & ~section(X, Y, Z, 4.6) & (Y >= 23.5) & (Y <= 35.3)
        front = Z < -4.0
        slit = body & front & (np.abs(X) < 3.6) & (Y > 28.15) & (Y < 29.05)
        eyes = slit & (np.abs(X) > .9) & (np.abs(X) < 2.75) & (Y > 28.3) & (Y < 28.95)
        vents = body & front & (Y > 25.3) & (Y < 26.9) & (np.abs(X) > .9) & (np.abs(X) < 2.6) & ((np.floor((Y - 25.3) / .55) % 2) == 0)
        brow = body & (Y > 29.55) & (Y < 30.25)
        crown = body & (Y > 32.55) & (Y < 33.05)
        rim = body & (Y < 24.2)
        medal = body & (np.abs(X) > 4.7) & ((Y - 27.6) ** 2 + (Z - .4) ** 2 < 1.5 ** 2)
        gem = medal & ((Y - 27.6) ** 2 + (Z - .4) ** 2 < .62 ** 2)
        ridge = body & (np.abs(X) < .45) & (Y > 33.0)
        cheeks = body & front & (Y < 27.1) & (np.abs(X) > 3.3)
        return [('eye', eyes), ('black', slit | vents), ('gem', gem), ('gold', medal | brow | crown | rim | ridge | cheeks),
                ('white', body & (Y > 27.0)), ('white_mid', body)]
    f.volume(shell, (-5.4, 23.5, -6.1), (5.4, 35.4, 5.4), cell=.25, bone='head')

    # Nose guard standing proud of the V visor.
    f.box(-.35, 24.4, -6.25, .35, 30.3, -5.8, 'gold', 'head')

    def taper(points, r0, r1, mat, cell=.25):
        pts = np.asarray(points, float)
        seg = np.linalg.norm(np.diff(pts, axis=0), axis=1)
        cum = np.concatenate([[0], np.cumsum(seg)])
        total = cum[-1]
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
                along = np.where(better, (cum[i] + t * seg[i]) / total, along)
            radius = r0 + (r1 - r0) * along
            return [(mat, best <= radius)]
        f.volume(material, lo, hi, cell, 'head')

    for s in (-1, 1):
        # Horns: break out above the temples, flare outward, then sweep up in an S.
        horn = [(s * 5.0, 31.0, -.7), (s * 5.7, 32.1, -.85), (s * 6.3, 33.8, -1.0), (s * 6.65, 35.9, -1.0),
                (s * 6.55, 38.0, -.85), (s * 6.1, 40.0, -.55), (s * 5.6, 41.7, -.3)]
        taper(horn, .95, .14, 'gold')
        taper([(s * 5.1, 30.7, -.7), (s * 5.55, 31.4, -.8)], 1.2, 1.05, 'gold_dark')
        # Crown spikes over the brow, white with gold tips.
        taper([(s * 1.9, 31.5, -5.7), (s * 2.3, 33.6, -5.1), (s * 2.65, 35.2, -4.5)], .55, .12, 'white')
    taper([(0, 31.3, -5.9), (0, 33.6, -5.4), (0, 35.9, -4.7), (0, 37.6, -4.1)], .85, .12, 'gold')
    f.box(-.75, 30.4, -6.25, .75, 31.5, -5.8, 'gold', 'head')
    f.box(-.4, 30.6, -6.4, .4, 31.3, -6.25, 'gem', 'head')
    return f


SET = ArmorSet(key='seraph', pieces=PIECES, layer_one=layer_one, layer_two=layer_two, helm=helm,
               helm_palette=HELM_PALETTE, helm_glow=HELM_GLOW, art=ART, credit='Evergarden / seraph_armor_assets.py',
               atlas_seed=9071)


def register(java, bedrock, item_atlas, definitions):
    forge.register(SET, java, bedrock, item_atlas, definitions)


if __name__ == '__main__':
    import sys
    sys.path.insert(0, str(ROOT / 'evergarden/tools'))
    for path in forge.previews(SET, ROOT / 'previews/seraph-armor'):
        print('Saved', path)
    print('helm cuboids:', len(helm().cubes))
