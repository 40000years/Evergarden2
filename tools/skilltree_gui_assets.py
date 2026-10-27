"""Skill Tree menu textures, shared by Java and Bedrock.

SkillTreeService draws every skill as one volume-style row on a dark panel:

    [icon] [-] [meter x5] [+] [rank badge]

Each menu element is a paper item with item_model advance_magic:skilltree_<name>;
Geyser maps the same ids so Bedrock shows identical icons. The meter rises like a
volume control. A skill has 20 ranks in four tiers of five; each cell takes the
colour of the tier that last filled it (green, blue, purple, orange) and the whole
meter turns gold at 20. A white rim marks cells filled since the menu opened (still
undoable); hollow sockets are empty ranks, hatched sockets a locked skill. The top
row adds the Arcane level badge, the points orb and an XP ring.

    python tools/skilltree_gui_assets.py      # preview sheets in previews/skilltree-gui/
"""
from __future__ import annotations

import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
NS = 'advance_magic'
PREFIX = 'skilltree_'
BASE = 'minecraft:paper'
S = 32
SS = 8  # glyph supersampling

Y, X = np.mgrid[0:S, 0:S] + .5

# Skill id -> badge color, as SkillTreeService.tint(): elements follow their staffs.
SKILLS = {
    'power': 0xff9f43, 'mana': 0xb388ff, 'regen': 0x7fdbff, 'efficiency': 0x5c7cfa, 'healing': 0xff7eb6,
    'fire': 0xff6a18, 'wind': 0x9fffe2, 'earth': 0xb9df42, 'ice': 0xa6ecff,
    'water': 0x25aaff, 'lightning': 0xd7b8ff, 'light': 0xffe5a1, 'dark': 0xb94cff,
}
HEIGHTS = (7, 11, 15, 19, 23)
# Tier colours, as SkillTreeService.TIER_COLORS / GOLD.
TIERS = ((94, 232, 122), (79, 180, 255), (180, 124, 255), (255, 154, 60))
GOLD = (255, 209, 102)
RANKS = 20
ORB_MAX = 99
LEVEL_MAX = 99


def rgb(value):
    return ((value >> 16) & 255, (value >> 8) & 255, value & 255)


def mix(a, b, t):
    return tuple(x * (1 - t) + y * t for x, y in zip(a, b))


# ---------------------------------------------------------------------------
# Signed-distance painting at 32 px (crisp, anti-aliased edges)


def cov(d):
    return np.clip(.5 - d, 0, 1)


def rrect(x0, y0, x1, y1, r):
    cx, cy, hx, hy = (x0 + x1) / 2, (y0 + y1) / 2, (x1 - x0) / 2 - r, (y1 - y0) / 2 - r
    qx, qy = np.abs(X - cx) - hx, np.abs(Y - cy) - hy
    return np.hypot(np.maximum(qx, 0), np.maximum(qy, 0)) + np.minimum(np.maximum(qx, qy), 0) - r


def circle(cx, cy, r):
    return np.hypot(X - cx, Y - cy) - r


def vgrad(top, bottom, y0=0, y1=S):
    t = np.clip((Y - y0) / (y1 - y0), 0, 1)[..., None]
    return np.asarray(top, float) * (1 - t) + np.asarray(bottom, float) * t


def rgrad(inner, outer, cx, cy, r):
    t = np.clip(np.hypot(X - cx, Y - cy) / r, 0, 1)[..., None]
    return np.asarray(inner, float) * (1 - t) + np.asarray(outer, float) * t


class Canvas:
    def __init__(self):
        self.px = np.zeros((S, S, 4))

    def fill(self, coverage, color, alpha=1.0):
        a = np.clip(coverage * alpha, 0, 1)
        src = np.broadcast_to(np.asarray(color, float) / 255, (S, S, 3))
        dst_a = self.px[..., 3]
        out = a + dst_a * (1 - a)
        rgb_ = src * a[..., None] + self.px[..., :3] * (dst_a * (1 - a))[..., None]
        self.px[..., :3] = rgb_ / np.maximum(out, 1e-6)[..., None]
        self.px[..., 3] = out
        return self

    def glow(self, coverage, color, radius=2.0, alpha=.45):
        """Soft halo from a coverage mask (box-blurred twice)."""
        m = coverage.copy()
        k = max(1, int(round(radius)))
        for _ in range(2):
            p = np.pad(m, k, mode='constant')
            c = np.cumsum(np.cumsum(p, 0), 1)
            c = np.pad(c, ((1, 0), (1, 0)))
            n = 2 * k + 1
            m = (c[n:, n:] - c[:-n, n:] - c[n:, :-n] + c[:-n, :-n]) / (n * n)
        return self.fill(np.clip(m * 1.6, 0, 1), color, alpha)

    def image(self):
        return Image.fromarray((np.clip(self.px, 0, 1) * 255 + .5).astype(np.uint8), 'RGBA')


PANEL_TOP, PANEL_BOTTOM = (31, 26, 43), (21, 17, 30)


def base():
    c = Canvas()
    c.fill(np.ones((S, S)), vgrad(PANEL_TOP, PANEL_BOTTOM))
    return c


def plaque(c, top, bottom, outline, inset=2, radius=6, gloss=.16):
    shape = rrect(inset, inset, S - inset, S - inset, radius)
    c.fill(cov(shape - .2), (8, 6, 12), .55)                       # contact shadow
    c.fill(cov(shape), outline)
    inner = rrect(inset + 1, inset + 1, S - inset - 1, S - inset - 1, radius - 1)
    c.fill(cov(inner), vgrad(top, bottom, inset, S - inset))
    if gloss:
        c.fill(cov(rrect(inset + 2, inset + 2, S - inset - 2, S / 2, radius - 2)), (255, 255, 255), gloss)
    return c


# ---------------------------------------------------------------------------
# 5x7 pixel font (crisp digits for rank badges and the points orb)

FONT = {
    '0': ('.###.', '#...#', '#...#', '#...#', '#...#', '#...#', '.###.'),
    '1': ('..#..', '.##..', '..#..', '..#..', '..#..', '..#..', '.###.'),
    '2': ('.###.', '#...#', '....#', '...#.', '..#..', '.#...', '#####'),
    '3': ('####.', '....#', '....#', '.###.', '....#', '....#', '####.'),
    '4': ('...#.', '..##.', '.#.#.', '#..#.', '#####', '...#.', '...#.'),
    '5': ('#####', '#....', '####.', '....#', '....#', '#...#', '.###.'),
    '6': ('..##.', '.#...', '#....', '####.', '#...#', '#...#', '.###.'),
    '7': ('#####', '....#', '...#.', '..#..', '.#...', '.#...', '.#...'),
    '8': ('.###.', '#...#', '#...#', '.###.', '#...#', '#...#', '.###.'),
    '9': ('.###.', '#...#', '#...#', '.####', '....#', '...#.', '.##..'),
    '/': ('....#', '....#', '...#.', '..#..', '.#...', '#....', '#....'),
    '+': ('.....', '..#..', '..#..', '#####', '..#..', '..#..', '.....'),
    '?': ('.###.', '#...#', '....#', '...#.', '..#..', '.....', '..#..'),
    '!': ('..#..', '..#..', '..#..', '..#..', '..#..', '.....', '..#..'),
    'L': ('#....', '#....', '#....', '#....', '#....', '#....', '#####'),
    'V': ('#...#', '#...#', '#...#', '#...#', '.#.#.', '.#.#.', '..#..'),
    'E': ('#####', '#....', '#....', '####.', '#....', '#....', '#####'),
    'X': ('#...#', '#...#', '.#.#.', '..#..', '.#.#.', '#...#', '#...#'),
    'P': ('####.', '#...#', '#...#', '####.', '#....', '#....', '#....'),
}


def text_mask(text, scale, x, y):
    m = np.zeros((S, S))
    for i, ch in enumerate(text):
        rows = FONT[ch]
        gx = x + i * 6 * scale
        for r, row in enumerate(rows):
            for col, bit in enumerate(row):
                if bit == '#':
                    m[y + r * scale:y + (r + 1) * scale, gx + col * scale:gx + (col + 1) * scale] = 1
    return m


def text_width(text, scale):
    return (len(text) * 6 - 1) * scale


def draw_text(c, text, scale, y, color, shadow=(6, 4, 10), x=None):
    x = (S - text_width(text, scale)) // 2 if x is None else x
    m = text_mask(text, scale, x, y)
    if shadow:
        c.fill(np.roll(np.roll(m, 1, 0), 1, 1), shadow, .75)
    c.fill(m, color)
    return c


# ---------------------------------------------------------------------------
# Supersampled glyphs (skill sigils, tab art). Each draws into a unit box.


class Glyph:
    def __init__(self, box=(0, 0, 1, 1)):
        self.n = S * SS
        self.img = Image.new('L', (self.n, self.n), 0)
        self.d = ImageDraw.Draw(self.img)
        self.box = box

    def p(self, x, y):
        x0, y0, x1, y1 = self.box
        return ((x0 + (x1 - x0) * x) * self.n, (y0 + (y1 - y0) * y) * self.n)

    def w(self, v):
        return max(1, int(round(v * (self.box[2] - self.box[0]) * self.n)))

    def poly(self, pts, ink=255):
        self.d.polygon([self.p(*q) for q in pts], fill=ink)

    def ellipse(self, cx, cy, r, ink=255):
        a, b = self.p(cx - r, cy - r), self.p(cx + r, cy + r)
        self.d.ellipse([a, b], fill=ink)

    def line(self, pts, width, ink=255):
        q = [self.p(*v) for v in pts]
        self.d.line(q, fill=ink, width=self.w(width), joint='curve')
        r = self.w(width) / 2
        for x, y in (q[0], q[-1]):
            self.d.ellipse([x - r, y - r, x + r, y + r], fill=ink)

    def arc(self, cx, cy, r, start, end, width, ink=255, caps=True):
        a, b = self.p(cx - r, cy - r), self.p(cx + r, cy + r)
        self.d.arc([a, b], start, end, fill=ink, width=self.w(width))
        if caps:
            rr = self.w(width) / 2
            for ang in (start, end):
                t = math.radians(ang)
                x, y = self.p(cx + (r - width / 2) * math.cos(t), cy + (r - width / 2) * math.sin(t))
                self.d.ellipse([x - rr, y - rr, x + rr, y + rr], fill=ink)

    def mask(self):
        small = self.img.resize((S, S), Image.Resampling.BOX)
        return np.asarray(small, float) / 255


def star(g, points, outer, inner, rot=-90, cx=.5, cy=.5):
    pts = []
    for i in range(points * 2):
        r = outer if i % 2 == 0 else inner
        t = math.radians(rot + i * 180 / points)
        pts.append((cx + r * math.cos(t), cy + r * math.sin(t)))
    g.poly(pts)


def drop(g, cx, cy, r, ink=255):
    g.ellipse(cx, cy + r * .35, r, ink)
    g.poly([(cx, cy - r * 1.55), (cx - r * .93, cy + r * .05), (cx + r * .93, cy + r * .05)], ink)


def sigil(name, box=(0, 0, 1, 1)):
    g = Glyph(box)
    if name == 'power':
        star(g, 8, .44, .2)
        g.ellipse(.5, .5, .1, 0)
    elif name == 'mana':
        g.poly([(.3, .2), (.7, .2), (.9, .42), (.5, .9), (.1, .42)])
        for seg in (((.1, .42), (.9, .42)), ((.3, .2), (.42, .42), (.5, .9)), ((.7, .2), (.58, .42), (.5, .9))):
            g.line(seg, .045, 0)
    elif name == 'regen':
        g.arc(.5, .5, .4, 35, 300, .12)
        t = math.radians(35)
        tip = (.5 + .34 * math.cos(t), .5 + .34 * math.sin(t))
        g.poly([(tip[0] + .17, tip[1] - .06), (tip[0] - .1, tip[1] - .08), (tip[0] + .04, tip[1] + .17)])
        drop(g, .5, .53, .12)
    elif name == 'efficiency':
        drop(g, .5, .36, .14)
        g.line([(.24, .58), (.5, .78), (.76, .58)], .11)
        g.line([(.3, .76), (.5, .91), (.7, .76)], .09)
    elif name == 'healing':
        g.ellipse(.33, .38, .19)
        g.ellipse(.67, .38, .19)
        g.poly([(.15, .46), (.85, .46), (.5, .88)])
        g.poly([(.2, .38), (.8, .38), (.8, .5), (.2, .5)])
        g.line([(.5, .36), (.5, .62)], .07, 0)
        g.line([(.37, .49), (.63, .49)], .07, 0)
    elif name == 'fire':
        g.poly([(.5, .06), (.66, .3), (.78, .22), (.84, .5), (.8, .74), (.64, .9), (.36, .9), (.2, .74),
                (.16, .52), (.26, .3), (.36, .42), (.4, .2)])
        g.poly([(.5, .42), (.62, .6), (.62, .76), (.5, .86), (.38, .76), (.38, .6)], 0)
        g.poly([(.5, .56), (.56, .68), (.55, .78), (.5, .82), (.45, .78), (.44, .68)])
    elif name == 'wind':
        g.line([(.1, .36), (.6, .36)], .09)
        g.arc(.6, .25, .11, 270, 90 + 360, .09, caps=False)
        g.line([(.1, .56), (.74, .56)], .09)
        g.arc(.74, .69, .13, 180 + 90, 90 + 360, .09, caps=False)
        g.line([(.22, .76), (.5, .76)], .08)
    elif name == 'earth':
        g.poly([(.06, .84), (.36, .3), (.5, .52), (.66, .2), (.94, .84)])
        g.poly([(.66, .2), (.74, .36), (.68, .34), (.62, .4), (.58, .34)], 0)
        g.line([(.36, .84), (.44, .66), (.4, .58)], .05, 0)
    elif name == 'ice':
        for k in range(3):
            t = math.radians(90 + k * 60)
            dx, dy = .4 * math.cos(t), .4 * math.sin(t)
            g.line([(.5 - dx, .5 - dy), (.5 + dx, .5 + dy)], .085)
            for s in (-1, 1):
                bx, by = .5 + s * dx * .62, .5 + s * dy * .62
                for side in (-1, 1):
                    u = t + side * math.radians(55)
                    g.line([(bx, by), (bx + s * .16 * math.cos(u), by + s * .16 * math.sin(u))], .07)
    elif name == 'water':
        drop(g, .5, .5, .26)
        g.arc(.5, .63, .16, 100, 170, .06, 0, caps=False)
    elif name == 'lightning':
        g.poly([(.6, .05), (.2, .56), (.46, .56), (.36, .95), (.82, .38), (.55, .38), (.7, .05)])
    elif name == 'light':
        g.ellipse(.5, .5, .2)
        for k in range(8):
            t = math.radians(k * 45)
            g.line([(.5 + .31 * math.cos(t), .5 + .31 * math.sin(t)), (.5 + .44 * math.cos(t), .5 + .44 * math.sin(t))], .08)
    elif name == 'dark':
        g.ellipse(.48, .52, .38)
        g.ellipse(.66, .4, .32, 0)
        star(g, 4, .1, .035, cx=.76, cy=.66)
    elif name == 'sparkle':
        star(g, 4, .46, .11, rot=-90)
        star(g, 4, .16, .05, rot=-45, cx=.8, cy=.22)
    elif name == 'question':
        g.arc(.5, .36, .2, 180, 400, .12, caps=False)
        g.line([(.5 + .2 * math.cos(math.radians(40)) - .03, .36 + .2 * math.sin(math.radians(40))), (.5, .6)], .12)
        g.ellipse(.5, .8, .075)
    elif name == 'reset':
        g.arc(.5, .52, .32, 200, 500, .12, caps=False)
        g.poly([(.08, .44), (.38, .44), (.2, .2)])
    elif name == 'close':
        g.line([(.25, .25), (.75, .75)], .14)
        g.line([(.75, .25), (.25, .75)], .14)
    elif name == 'lock':
        g.arc(.5, .4, .21, 180, 360, .1, caps=False)
        g.poly([(.29, .4), (.39, .4), (.39, .5), (.29, .5)])
        g.poly([(.61, .4), (.71, .4), (.71, .5), (.61, .5)])
        g.d.rounded_rectangle([g.p(.2, .46), g.p(.8, .88)], radius=g.w(.08), fill=255)
        g.ellipse(.5, .62, .07, 0)
        g.poly([(.47, .64), (.53, .64), (.55, .78), (.45, .78)], 0)
    else:
        raise KeyError(name)
    return g.mask()


def glyph(c, mask, color, shadow=(6, 4, 10), shadow_alpha=.7):
    if shadow:
        c.fill(np.roll(np.roll(mask, 1, 0), 1, 1), shadow, shadow_alpha)
    c.fill(mask, color)
    return c


# ---------------------------------------------------------------------------
# Menu elements


def panel():
    return base()


FLOOR = 27


def track():
    c = Canvas()
    c.fill(np.ones((S, S)), vgrad((19, 15, 27), (12, 10, 18)))
    c.fill(cov(np.abs(Y - (FLOOR + 1.5)) - .5), (52, 44, 72), .9)  # baseline the bars stand on
    return c


def cell(pos, state, new=False):
    """state: 'empty', 'locked', 1-4 (tier that last filled the cell) or 'gold'."""
    c = track()
    x0, x1, h = 9, 23, HEIGHTS[pos - 1]
    shape = rrect(x0, FLOOR - h, x1, FLOOR, 1.6)
    inner = rrect(x0 + 1, FLOOR - h + 1, x1 - 1, FLOOR - 1, 1)
    if state == 'empty':
        c.fill(cov(shape), (74, 64, 98))
        c.fill(cov(inner), (31, 26, 43))
        return c
    if state == 'locked':
        c.fill(cov(shape), (46, 40, 60))
        c.fill(cov(inner), (22, 18, 30))
        c.fill(cov(inner) * (np.mod(X + Y, 4) < 1.2), (52, 45, 66))
        return c
    color = GOLD if state == 'gold' else TIERS[state - 1]
    c.glow(cov(shape), color, 2, .5 if new or state == 'gold' else .35)
    c.fill(cov(shape), mix(color, (0, 0, 0), .45))
    c.fill(cov(inner), vgrad(mix(color, (255, 255, 255), .45), mix(color, (0, 0, 0), .2), FLOOR - h, FLOOR))
    c.fill(cov(rrect(x0 + 1, FLOOR - h + 1, x1 - 1, FLOOR - h + 2.2, .8)), (255, 255, 255), .7)
    if state == 'gold':
        for sx, sy in ((x0 + 3.5, FLOOR - h + 5), (x1 - 4, FLOOR - h * .45)):
            spark = np.minimum(np.abs(X - sx) * 3 + np.abs(Y - sy), np.abs(X - sx) + np.abs(Y - sy) * 3)
            c.fill(cov(spark - 2.2), (255, 255, 240), .85)
    if new:
        c.fill(cov(np.abs(rrect(x0 - 1.5, FLOOR - h - 1.5, x1 + 1.5, FLOOR + .5, 2.4)) - .55), (255, 255, 255), .95)
    return c


def button(sign, on):
    c = base()
    if sign == '+':
        top, bottom, edge = ((104, 240, 152), (33, 163, 90), (14, 84, 46)) if on else ((76, 70, 92), (52, 47, 64), (34, 30, 44))
    else:
        top, bottom, edge = ((255, 146, 102), (212, 71, 46), (110, 30, 18)) if on else ((76, 70, 92), (52, 47, 64), (34, 30, 44))
    plaque(c, top, bottom, edge, gloss=.18 if on else .06)
    ink = (255, 255, 255) if on else (118, 109, 138)
    m = np.zeros((S, S))
    m = np.maximum(m, cov(rrect(9, 14, 23, 18, 1.2)))
    if sign == '+':
        m = np.maximum(m, cov(rrect(14, 9, 18, 23, 1.2)))
    glyph(c, m, ink, shadow=(0, 0, 0) if on else None, shadow_alpha=.35)
    return c


def tier_color(n):
    return GOLD if n >= RANKS else TIERS[(n - 1) // 5] if n else (84, 72, 112)


def rank_badge(n):
    c = base()
    edge = tier_color(n)
    full = n >= RANKS
    plaque(c, (86, 62, 22) if full else (44, 37, 60), (54, 38, 12) if full else (31, 26, 43), edge, gloss=.08)
    color = (255, 218, 120) if full else mix(edge, (255, 255, 255), .55) if n else (140, 130, 153)
    draw_text(c, str(n), 2, 5, color)
    draw_text(c, f'/{RANKS}', 1, 21, edge if n else (157, 140, 194), shadow=None)
    return c


def level_badge(n):
    c = base()
    plaque(c, (64, 44, 120), (36, 24, 72), (201, 167, 255), gloss=.12)
    draw_text(c, 'LV', 1, 4, (201, 167, 255), shadow=None)
    label = f'{LEVEL_MAX}+' if n > LEVEL_MAX else str(n)
    if len(label) == 3:
        return draw_text(c, label, 1, 17, (255, 255, 255))
    return draw_text(c, label, 2, 13, (255, 255, 255))


def xp_ring(tenths):
    c = base()
    r_out, r_in = 13.4, 9.6
    dist = np.hypot(X - 16, Y - 16)
    ring = np.maximum(dist - r_out, r_in - dist)
    c.fill(cov(ring), (36, 30, 50))
    if tenths:
        ang = np.mod(np.degrees(np.arctan2(X - 16, -(Y - 16))), 360)  # clockwise from 12 o'clock
        part = cov(ring) * np.clip((tenths * 36 - ang) + .5, 0, 1)
        color = GOLD if tenths >= 10 else (180, 124, 255)
        c.glow(part, color, 2, .45)
        c.fill(part, rgrad(mix(color, (255, 255, 255), .35), color, 16, 16, r_out))
    return draw_text(c, 'EXP', 1, 13, GOLD if tenths >= 10 else (226, 214, 255), shadow=(6, 4, 10))


def lock():
    c = base()
    plaque(c, (44, 37, 60), (31, 26, 43), (84, 72, 112), gloss=.06)
    return glyph(c, sigil('lock', (.16, .14, .84, .86)), (226, 186, 96))


def orb(n):
    c = base()
    lit = n > 0
    shape = circle(16, 16, 13.2)
    if lit:
        c.glow(cov(shape), (180, 140, 255), 2.5, .55)
    c.fill(cov(shape), (40, 22, 82) if lit else (40, 36, 52))
    c.fill(cov(circle(16, 16, 12.2)), rgrad((186, 146, 255), (88, 47, 192), 13, 12, 14) if lit else rgrad((78, 72, 94), (46, 42, 58), 13, 12, 14))
    c.fill(cov(circle(12, 11, 4.5)), (255, 255, 255), .28 if lit else .1)
    label = '99+' if n > ORB_MAX else str(n)
    if len(label) == 3:
        draw_text(c, label, 1, 13, (255, 255, 255))
    else:
        draw_text(c, label, 2, 9, (255, 255, 255) if lit else (150, 140, 166))
    return c


TABS = {'core': ('sparkle',), 'elements_a': ('fire', 'wind', 'earth', 'ice'), 'elements_b': ('water', 'lightning', 'light', 'dark')}


def tab(key, active):
    c = base()
    if active:
        plaque(c, (104, 72, 186), (58, 38, 112), (255, 209, 102), gloss=.14)
    else:
        plaque(c, (42, 36, 56), (30, 25, 41), (66, 57, 86), gloss=.05)
    names = TABS[key]
    if len(names) == 1:
        glyph(c, sigil(names[0], (.17, .17, .83, .83)), (255, 222, 130) if active else (170, 150, 120))
    else:
        for i, name in enumerate(names):
            x0, y0 = .16 + (i % 2) * .35, .16 + (i // 2) * .35
            tint = rgb(SKILLS[name])
            glyph(c, sigil(name, (x0, y0, x0 + .33, y0 + .33)), tint if active else mix(tint, (60, 54, 74), .55),
                  shadow_alpha=.6)
    return c


def action(kind):
    c = base()
    if kind == 'help':
        plaque(c, (96, 170, 255), (42, 99, 201), (20, 48, 110))
        return glyph(c, sigil('question', (.2, .14, .8, .86)), (255, 255, 255), (8, 26, 70), .55)
    if kind == 'reset':
        plaque(c, (255, 190, 92), (196, 111, 18), (110, 58, 6))
        return glyph(c, sigil('reset', (.18, .16, .82, .84)), (255, 255, 255), (90, 44, 0), .5)
    if kind == 'reset_armed':
        plaque(c, (255, 98, 98), (169, 29, 42), (96, 10, 20))
        glyph(c, sigil('reset', (.18, .16, .82, .84)), (255, 226, 226), (80, 0, 10), .5)
        return draw_text(c, '!', 2, 9, (255, 255, 255), shadow=(80, 0, 10))
    if kind == 'close':
        plaque(c, (120, 46, 58), (78, 24, 36), (44, 10, 18))
        return glyph(c, sigil('close', (.2, .2, .8, .8)), (255, 214, 214), (30, 0, 6), .6)
    raise KeyError(kind)


def icon(skill):
    c = base()
    tint = rgb(SKILLS[skill])
    dark = mix(tint, (8, 6, 14), .62)
    shape = circle(16, 16, 13.6)
    c.glow(cov(shape), tint, 2, .3)
    c.fill(cov(shape), mix(tint, (0, 0, 0), .75))
    c.fill(cov(circle(16, 16, 12.6)), rgrad(mix(tint, dark, .45), dark, 13, 12, 15))
    c.fill(cov(np.abs(circle(16, 16, 11.6)) - .35), mix(tint, (255, 255, 255), .2), .35)
    return glyph(c, sigil(skill, (.2, .2, .8, .8)), mix(tint, (255, 255, 255), .55), (4, 2, 8), .8)


def textures():
    """name -> (image, Bedrock display name)."""
    out = {'panel': (panel(), 'Skill Tree')}
    for pos in range(1, 6):
        out[f'cell{pos}_empty'] = (cell(pos, 'empty'), 'Skill level')
        out[f'cell{pos}_locked'] = (cell(pos, 'locked'), 'Skill level')
        for state in (1, 2, 3, 4, 'gold'):
            word = state if state == 'gold' else f't{state}'
            out[f'cell{pos}_{word}'] = (cell(pos, state), 'Skill level')
            out[f'cell{pos}_{word}_new'] = (cell(pos, state, True), 'Skill level')
    for sign, word in (('+', 'plus'), ('-', 'minus')):
        out[word] = (button(sign, True), 'Skill +' if sign == '+' else 'Skill -')
        out[f'{word}_off'] = (button(sign, False), 'Skill +' if sign == '+' else 'Skill -')
    for n in range(RANKS + 1):
        out[f'rank_{n}'] = (rank_badge(n), f'{n}/{RANKS}')
    out['lock'] = (lock(), 'Locked')
    for n in range(ORB_MAX + 1):
        out[f'orb_{n}'] = (orb(n), 'Skill points')
    out['orb_more'] = (orb(ORB_MAX + 1), 'Skill points')
    for n in range(1, LEVEL_MAX + 1):
        out[f'level_{n}'] = (level_badge(n), 'Arcane level')
    out['level_more'] = (level_badge(LEVEL_MAX + 1), 'Arcane level')
    for k in range(11):
        out[f'xp_{k}'] = (xp_ring(k), 'Arcane XP')
    for key in TABS:
        out[f'tab_{key}'] = (tab(key, False), 'Skill Tree tab')
        out[f'tab_{key}_on'] = (tab(key, True), 'Skill Tree tab')
    for kind in ('help', 'reset', 'reset_armed', 'close'):
        out[kind] = (action(kind), kind.replace('_', ' ').title())
    for skill in SKILLS:
        out[f'icon_{skill}'] = (icon(skill), skill.title())
    return {name: (c.image(), title) for name, (c, title) in out.items()}


# ---------------------------------------------------------------------------
# Pack registration


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf8')


def register(java, bedrock, item_atlas, definitions):
    rows = definitions.setdefault(BASE, [])
    for name, (image, title) in textures().items():
        ident = PREFIX + name
        for path in (java / f'assets/{NS}/textures/item/skilltree/{name}.png', bedrock / f'textures/skilltree/{name}.png'):
            path.parent.mkdir(parents=True, exist_ok=True)
            image.save(path)
        write_json(java / f'assets/{NS}/models/item/skilltree/{name}.json', {
            'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/skilltree/{name}'}})
        write_json(java / f'assets/{NS}/items/{ident}.json', {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/skilltree/{name}'}})
        item_atlas[f'{NS}.{ident}'] = {'textures': f'textures/skilltree/{name}'}
        rows.append({'type': 'definition', 'model': f'{NS}:{ident}', 'bedrock_identifier': f'{NS}:{ident}',
                     'display_name': title,
                     'bedrock_options': {'icon': f'{NS}.{ident}', 'allow_offhand': False, 'display_handheld': False}})


def owned():
    return (f'assets/{NS}/items/{PREFIX}', f'assets/{NS}/models/item/skilltree/', f'assets/{NS}/textures/item/skilltree/',
            'textures/skilltree/')


# ---------------------------------------------------------------------------
# Previews: the menu as SkillTreeService lays it out (mirrors its slot map)

PAGES = {'core': ('power', 'healing', 'mana', 'regen', 'efficiency'),
         'elements_a': ('fire', 'wind', 'earth', 'ice'),
         'elements_b': ('water', 'lightning', 'light', 'dark')}
PARENT = {'regen': ('mana', 1), 'efficiency': ('mana', 2)} | {e: ('power', 2) for e in PAGES['elements_a'] + PAGES['elements_b']}


def fills(rank, pos):
    return 0 if rank < pos else (rank - pos) // 5 + 1


def cells(rank, new, locked):
    """Texture names of the five meter cells, as SkillTreeService.cell()."""
    names = []
    for pos in range(1, 6):
        f = fills(rank, pos)
        if locked:
            names.append(f'cell{pos}_locked')
        elif not f:
            names.append(f'cell{pos}_empty')
        else:
            tier = 'gold' if rank >= RANKS else f't{f}'
            names.append(f"cell{pos}_{tier}{'_new' if f > fills(rank - new, pos) else ''}")
    return names


def layout(page, ranks, new, points, level=1, tenths=0):
    grid = ['panel'] * 54
    for i, key in enumerate(TABS):
        grid[i] = f'tab_{key}_on' if key == page else f'tab_{key}'
    grid[3] = 'level_more' if level > LEVEL_MAX else f'level_{level}'
    grid[4] = 'orb_more' if points > ORB_MAX else f'orb_{points}'
    grid[5] = f'xp_{tenths}'
    grid[6], grid[7], grid[8] = 'help', 'reset', 'close'
    for row, skill in enumerate(PAGES[page], start=1):
        rank, added = ranks.get(skill, 0), new.get(skill, 0)
        parent = PARENT.get(skill)
        locked = parent is not None and ranks.get(parent[0], 0) < parent[1]
        slot = row * 9
        grid[slot] = f'icon_{skill}'
        grid[slot + 1] = 'minus' if added else 'minus_off'
        grid[slot + 2:slot + 7] = cells(rank, added, locked)
        grid[slot + 7] = 'plus' if not locked and rank < RANKS and points > 0 else 'plus_off'
        grid[slot + 8] = 'lock' if locked else f'rank_{rank}'
    if page != 'core' and ranks.get('power', 0) < 2:
        grid[49] = 'lock'
    return grid


def render_menu(images, grid, title, scale=3):
    """Vanilla-like chest screen at GUI scale `scale`."""
    from PIL import ImageFont
    slot = 18 * scale
    pad = 7 * scale
    w, h = 9 * slot + 2 * pad, 6 * slot + pad + 17 * scale
    img = Image.new('RGBA', (w, h), (198, 198, 198, 255))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, w - 1, h - 1], outline=(0, 0, 0))
    font = ImageFont.truetype('C:/Windows/Fonts/LeelawUI.ttf', 7 * scale)
    d.text((pad + scale, 5 * scale), title, fill=(64, 64, 64), font=font)
    top = 17 * scale
    for i, name in enumerate(grid):
        x, y = pad + (i % 9) * slot, top + (i // 9) * slot
        d.rectangle([x, y, x + slot - 1, y + slot - 1], fill=(139, 139, 139))
        d.rectangle([x, y, x + slot - 1, y + scale - 1], fill=(55, 55, 55))
        d.rectangle([x, y, x + scale - 1, y + slot - 1], fill=(55, 55, 55))
        d.rectangle([x, y + slot - scale, x + slot - 1, y + slot - 1], fill=(255, 255, 255))
        d.rectangle([x + slot - scale, y, x + slot - 1, y + slot - 1], fill=(255, 255, 255))
        tile = images[name].resize((16 * scale, 16 * scale), Image.Resampling.NEAREST)
        img.alpha_composite(tile, (x + scale, y + scale))
    return img


def previews(out=ROOT / 'previews/skilltree-gui'):
    out.mkdir(parents=True, exist_ok=True)
    tex = textures()
    images = {k: v[0] for k, v in tex.items()}
    shots = [
        ('menu-core.png', 'core', {'power': 8, 'mana': 13, 'regen': 3, 'efficiency': 20, 'healing': 1}, {'power': 1, 'mana': 2}, 4, 12, 6),
        ('menu-elements-locked.png', 'elements_a', {'power': 1}, {}, 3, 2, 3),
        ('menu-elements.png', 'elements_b', {'power': 2, 'water': 17, 'lightning': 5, 'light': 9}, {'lightning': 1}, 0, 24, 9),
    ]
    for file, page, ranks, new, points, level, tenths in shots:
        render_menu(images, layout(page, ranks, new, points, level, tenths), '✦ Skill Tree · กด + เพื่ออัปสกิล').save(out / file)
    names = [n for n in tex if not n.startswith(('orb_', 'level_')) or n in ('orb_0', 'orb_7', 'orb_more', 'level_1', 'level_24', 'level_more')]
    cols = 16
    sheet = Image.new('RGBA', (cols * 72, math.ceil(len(names) / cols) * 72), (24, 20, 32, 255))
    for i, name in enumerate(names):
        sheet.alpha_composite(images[name].resize((64, 64), Image.Resampling.NEAREST), ((i % cols) * 72 + 4, (i // cols) * 72 + 4))
    sheet.save(out / 'textures.png')
    print(f'{len(tex)} textures -> {out}')


if __name__ == '__main__':
    previews()
