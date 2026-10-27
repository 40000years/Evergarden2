"""Banshee Wail, the Mourning Veil: the keening spirit of the Six Rivers.

A floating widow: a dark open mourning gown over a luminous sea-foam underdress that frays
into light at the hem, a comb-borne lace mantilla streaming behind a gaunt pale face framed by
silver hair, weeping eyes and a hinged jaw for the wail, bell sleeves, a lace shawl, a soul
lantern at the hip and an amethyst soul locket echoing the veil's Soul Anchors. A crescent
halo cradles the head and three soul wisps orbit her; both loop on the aura lane.

Keeps the river contract read by RiverVisual, Geyser and the probes: hi_gaze, head,
head_forward (wail origin), jaw, veil_0..veil_5, cape/cape_tip, and arm/forearm/hand/weapon
per side, with Banshee's 15-clip table. Runtime scale 4 (RiverVisual.SCALE).
"""
import argparse
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from boss_forge import Forge, Material, bezier, build_model, dump, rot, unit

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'evergarden/src/main/resources/modelengine/rivers'
MODEL = 'arkhaia_banshee_wail'
REVISION = 'banshee-mourning-veil-v1'
SCALE = 4.0

ABYSS = ['#04090b', '#071114', '#0b1a1e', '#102429', '#152f35', '#1c3c43', '#264c53']
SEAFOAM = ['#0b4637', '#127a63', '#26b996', '#7cf4ce', '#c9fff0', '#ffffff']
SILVER = ['#1f2a2c', '#38474a', '#566a6d', '#7d9496', '#a9c0c1', '#d6e8e8', '#ffffff']
AMETHYST = ['#250b45', '#45187d', '#7434c8', '#a46cff', '#dcc8ff', '#ffffff']
LINING = ['#06100f', '#0a1a18', '#0f2623', '#15332f', '#1d433d', '#285650']
GLOW_LINING = ['#0e3a31', '#14594a', '#1f8068', '#33a888', '#59d2ad', '#8ff0d0']
MATERIALS = {
    'gown': Material(ABYSS, 'ghost', trim=SILVER, accent=SEAFOAM, density=.8),
    'mist': Material(['#08251f', '#0d3d33', '#13594a', '#1c7b65', '#2ba487', '#4fd0ad', '#94f3d6', '#e2fff6'], 'ghost', glow=True,
                     trim=['#14594a', '#1f8068', '#59d2ad', '#b8fbe6', '#ffffff'], accent=['#2ba487', '#4fd0ad', '#94f3d6', '#e2fff6', '#ffffff'],
                     density=.75),
    'lace': Material(['#050c0e', '#0a1719', '#112326', '#183035', '#213f45', '#2c5058'], 'lace', trim=SILVER, density=1.0),
    'velvet': Material(ABYSS, 'cloth', trim=SILVER, accent=SEAFOAM),
    'silver': Material(SILVER, 'plate', accent=SEAFOAM),
    'filigree': Material(SILVER[1:], 'trim'),
    'skin': Material(['#2e4f52', '#46696b', '#628a8a', '#81a8a6', '#a3c5c1', '#c7e0dc', '#e6f5f2'], 'skin', density=1.8),
    'hair': Material(['#22383b', '#3a5759', '#56797a', '#7b9e9e', '#a4c4c2', '#cbe3e0', '#eefaf8'], 'hair', density=1.2),
    'glow': Material(SEAFOAM, 'glow', glow=True, density=.8),
    'eye': Material(['#3fe0b4', '#a6fde6', '#effffb'], 'glow', glow=True, density=2.0),
    'soul': Material(AMETHYST, 'gem', glow=True, density=1.5),
    'soulflame': Material(['#2a0d52', '#5a24a8', '#8f52f0', '#c29bff', '#ece2ff', '#ffffff'], 'flame', glow=True, density=.8),
    'wisp': Material(['#0a3d31', '#11705b', '#1ca582', '#3fd6ae', '#86f2d2', '#c8fdee'], 'flame', glow=True, density=.9),
    'nail': Material(['#0a1315', '#132326', '#20363a', '#324e53', '#4a6a70', '#6d9096'], 'horn'),
}
for _m in MATERIALS.values():
    _m.ramp_hex = ['#%02x%02x%02x' % tuple(int(v) for v in c) for c in _m.ramp]
SIDES = (('r', 1), ('l', -1))
AURA = ('halo', 'wisps')
VEILS = 6


def joints():
    j = dict(pelvis=(0, 19.6, .2), chest=(0, 22.6, .2), neck=(0, 31.0, -.1))
    for side, s in SIDES:
        j.update({f'shoulder_{side}': (s * 4.7, 29.9, .3), f'elbow_{side}': (s * 6.4, 23.7, .9),
                  f'wrist_{side}': (s * 7.3, 17.9, .3)})
    return {k: np.array(v, float) for k, v in j.items()}


J = joints()
FACE_Z = -2.65
EYE_Y = 36.3
# Gown cross-section: height, elliptical radii and backward sweep.
GOWN_ROWS = ((21.4, 3.3, 2.7, .0), (11.6, 5.6, 4.8, .7), (2.6, 7.4, 6.4, 1.9))


def skeleton(f):
    f.bone('root', None, [0, 0, 0])
    f.bone('pelvis', 'root', J['pelvis'])
    f.bone('chest', 'pelvis', J['chest'])
    f.bone('hi_gaze', 'chest', J['neck'])
    f.bone('head', 'hi_gaze', J['neck'])
    # Wail origin: in front of the mouth, carried by every head pose.
    f.bone('head_forward', 'head', [0, 34.0, -7.0])
    f.bone('jaw', 'head', [0, 33.95, .45])
    f.bone('hair', 'head', [0, 34.6, 1.9])
    f.bone('hair_tip', 'hair', [0, 23.4, 3.7])
    for side, s in SIDES:
        f.bone(f'lock_{side}', 'head', [s * 1.9, 37.4, -2.2])
    for n in range(VEILS):
        f.bone(f'veil_{n}', 'head', [(n - 2.5) * .8, 41.6 - abs(n - 2.5) * .45, 1.6])
    f.bone('halo', 'chest', [0, 37.2, 4.9])
    f.bone('cape', 'chest', [0, 30.0, 2.1])
    f.bone('cape_tip', 'cape', [0, 17.4, 5.0])
    for side, s in SIDES:
        f.bone(f'shoulder_{side}', 'chest', J[f'shoulder_{side}'])
        f.bone(f'arm_{side}', 'chest', J[f'shoulder_{side}'])
        f.bone(f'forearm_{side}', f'arm_{side}', J[f'elbow_{side}'])
        f.bone(f'sleeve_{side}', f'forearm_{side}', J[f'elbow_{side}'] + (0, -.4, .3))
        f.bone(f'hand_{side}', f'forearm_{side}', J[f'wrist_{side}'])
        f.bone(f'weapon_{side}', f'hand_{side}', J[f'wrist_{side}'] + (0, -1.0, -.05))
    for name, angle in (('front', 0), ('r', 75), ('back', 180), ('l', -75)):
        a = math.radians(angle)
        top, mid = GOWN_ROWS[0], GOWN_ROWS[1]
        f.bone(f'skirt_{name}', 'pelvis', [top[1] * math.sin(a) * .9, top[0], -top[2] * math.cos(a) * .9 + top[3]])
        f.bone(f'skirt_{name}_tip', f'skirt_{name}', [mid[1] * math.sin(a), mid[0], -mid[2] * math.cos(a) + mid[3]])
    f.bone('lantern', 'pelvis', [-3.5, 20.6, -1.3])
    f.bone('wisps', 'root', [0, 24.0, .4])


# ---------------------------------------------------------------------------
# Face, jaw, mantilla, hair, circlet and veil


def _layers(w, h, count):
    return [ImageDraw.Draw(img) for img in [Image.new('L', (w * 4, h * 4), 0) for _ in range(count)]]


def _mask(draw, w, h, cut=None):
    values = np.asarray(draw._image.resize((w, h), Image.Resampling.BOX), float) / 255
    return values > cut if cut is not None else values


def paint_face(ctx, rgb, mat):
    """Gaunt, grieving features on the front face: soft sunken sockets, lifted brows, lips."""
    w, h = ctx['w'], ctx['h']
    W, H = w * 4, h * 4
    cx = W / 2
    hollow, brows, lips, tears = _layers(w, h, 4)
    ey = H * ((38.0 - EYE_Y) / 4.4)
    ex = W * (.9 / 4.0)
    for s in (-1, 1):
        x = cx + s * ex
        hollow.ellipse((x - W * .14, ey - H * .07, x + W * .14, ey + H * .08), fill=200)
        hollow.ellipse((x - W * .19, ey - H * .1, x + W * .19, ey + H * .13), fill=70)
        # Grief brows: inner ends raised, outer ends falling.
        brows.line([(cx + s * W * .06, ey - H * .13), (cx + s * W * .17, ey - H * .155), (cx + s * W * .33, ey - H * .1)], fill=255, width=4)
        # Hollow cheeks under high cheekbones.
        hollow.polygon([(cx + s * W * .22, H * .64), (cx + s * W * .45, H * .58), (cx + s * W * .44, H * .84), (cx + s * W * .3, H * .8)], fill=80)
        tears.line([(x + s * W * .06, ey + H * .06), (x + s * W * .085, H * .66), (x + s * W * .06, H)], fill=255, width=3)
    hollow.ellipse((cx - W * .09, H * .7, cx + W * .09, H * .75), fill=120)
    lips.polygon([(cx - W * .17, H * .955), (cx - W * .05, H * .925), (cx, H * .94), (cx + W * .05, H * .925), (cx + W * .17, H * .955),
                  (cx, H * .985)], fill=255)
    rgb[:] = rgb * (1 - _mask(hollow, w, h)[..., None] * .3)
    rgb[_mask(brows, w, h, .35)] = np.array([40, 66, 68])
    rgb[_mask(lips, w, h, .4)] = np.array([46, 82, 86])
    rgb[_mask(tears, w, h, .3)] = np.array([96, 214, 184])


def paint_jaw(ctx, rgb, mat):
    """Lower lip over a shadowed, pointed chin."""
    w, h = ctx['w'], ctx['h']
    lip, shade = _layers(w, h, 2)
    lip.ellipse((w * 2 - w * .75, -h * .4, w * 2 + w * .75, h * 4 * .26), fill=255)
    for s in (-1, 1):
        shade.polygon([(w * 2 + s * w * 2, 0), (w * 2 + s * w * 2, h * 4), (w * 2 + s * w * 1.0, h * 4)], fill=200)
    rgb[:] = rgb * (1 - _mask(shade, w, h)[..., None] * .3)
    rgb[_mask(lip, w, h, .5)] = np.array([52, 90, 94])


def head(f):
    b = 'head'
    f.box(b, (-2.0, 33.6, FACE_Z), (4.0, 4.4, 4.3), 'skin', label='head', face=paint_face)
    f.cube(b, (0, 35.4, FACE_Z - .14), (.4, 1.35, .34), 'skin', rot((-9, 0, 0)), label='nose')
    for side, s in SIDES:
        f.cube(b, (s * 1.76, 34.55, -1.2), (.55, 2.2, 2.6), 'skin', rot((0, 0, s * 13)), label='cheek')
        f.cube(b, (s * .9, EYE_Y, FACE_Z - .03), (.78, .2, .06), 'eye', rot((0, 0, -s * 8)), label='eyes')
        # Luminous tears running from the outer corners down the cheeks.
        f.taper(b, [(s * 1.12, EYE_Y - .2, FACE_Z - .05), (s * 1.22, 34.9, FACE_Z - .06), (s * 1.12, 33.75, FACE_Z + .02)],
                [.15, .1], [.06, .06], 'glow', z_hint=(0, 0, -1), label='tear')
    f.cube(b, (0, 33.62, -1.45), (2.3, .42, 1.9), 'glow', label='throat')
    j = 'jaw'
    f.box(j, (-1.5, 32.1, -2.45), (3.0, 1.66, 3.05), 'skin', label='jaw', face=paint_jaw)
    f.box(j, (-1.0, 31.72, -2.28), (2.0, .62, 2.0), 'skin', label='chin')
    f.box(b, (-.75, 29.7, -.95), (1.5, 3.4, 1.5), 'skin', label='neck')
    # Lace choker with an amethyst cameo.
    f.ring(b, (0, 31.1, -.2), .98, .5, .22, 'lace', segments=10, normal=(0, 1, 0))
    f.cube(b, (0, 31.0, -1.24), (.62, .78, .26), 'soul', label='cameo')
    f.cube(b, (0, 31.0, -1.16), (.86, 1.0, .18), 'filigree', label='cameo_frame')


def comb_outline(base_y, z):
    """Peineta silhouette: an arched crest of seven spires above a straight base."""
    pts = [(-2.2, base_y, z)]
    for k in range(15):
        a = math.radians(168 - k * 12)
        r = 1.0 if k % 2 == 0 else .86
        lift = 1.0 + .14 * math.cos(a) ** 2 * (k % 2 == 0)
        pts.append((math.cos(a) * 2.35 * r, base_y + math.sin(a) * 3.7 * r * lift, z))
    pts.append((2.2, base_y, z))
    return pts


def mantilla(f):
    """Mourning mantilla: a pierced silver comb behind the crown carrying the lace veil,
    which falls along both sides of the face and down the back (veil_0..5)."""
    b = 'head'
    f.box(b, (-2.3, 37.85, -2.35), (4.6, .55, 4.25), 'velvet', label='veil_cap', folds=1)
    f.box(b, (-1.8, 38.3, -1.85), (3.6, .38, 3.35), 'velvet', label='veil_cap', folds=1)
    f.box(b, (-2.35, 32.6, .95), (4.7, 5.7, .95), 'velvet', label='veil_back', folds=2)
    f.sheet(b, comb_outline(38.05, 1.35), 'silver', thickness=.26, up=(0, 1, 0), facing=(0, 0, -1), label='comb',
            engrave='vine', runes=True)
    for x, y in ((0, 41.15), (-1.35, 40.35), (1.35, 40.35)):
        f.cube(b, (x, y, 1.12), (.5, .5, .18), 'glow', rot((0, 0, 45)), label='comb_gem')
    for side, s in SIDES:
        pts = [(s * 2.05, 41.0, 1.25), (s * 2.5, 38.2, 1.75), (s * 3.25, 30.2, 2.5), (s * 3.05, 29.7, .5), (s * 2.7, 33.2, -1.55),
               (s * 2.3, 37.7, -2.0)]
        f.sheet(b, pts, 'lace', thickness=.16, up=(0, 1, 0), facing=(s, .15, 0), label='mantilla_side', folds=2,
                edge={2: ('scallop', .04, 2), 3: ('scallop', .04, 2)})
        f.beam(b, (s * 2.32, 37.7, -2.06), (s * 2.72, 33.2, -1.62), .2, .2, 'filigree', label='mantilla_edge')
        f.beam(b, (s * 2.72, 33.2, -1.62), (s * 3.07, 29.8, .45), .2, .2, 'filigree', label='mantilla_edge')


def hair(f):
    # Curtain of silver hair parted at the brow, framing the face from under the veil.
    for side, s in SIDES:
        o = np.array(f.at(f'lock_{side}'))
        f.sheet(f'lock_{side}', [o + (-s * 1.75, .3, -.3), o + (s * .45, .3, -.1), o + (s * .55, -3.2, .35), o + (s * .2, -5.6, .6),
                                 o + (-s * .35, -3.0, .1)], 'hair', thickness=.3, up=(0, 1, 0), facing=(0, 0, -1), label='hair_fringe')
        for k, (dx, dz, length) in enumerate(((.15, .2, 14.4), (.6, .8, 12.2))):
            top = o + (s * dx, -1.2, dz)
            f.sheet(f'lock_{side}', [top + (-s * .45, .3, -.15), top + (s * .55, .3, -.1), top + (s * 1.2, -length * .5, -.9),
                                     top + (s * .9, -length, -1.2), top + (s * .3, -length * .55, -.95)], 'hair', thickness=.28,
                    up=(0, 1, 0), facing=(s, 0, -.8), label='hair_lock')
    # Long hair falling from under the veil, in two articulated lengths.
    for k, x in enumerate((-2.0, -1.0, 0, 1.0, 2.0)):
        top_z = 2.0 + abs(x) * .05
        f.sheet('hair', [(x - .7, 34.9, top_z), (x + .7, 34.9, top_z), (x * 1.3 + .85, 24.0, 3.6),
                         (x * 1.3, 23.0, 3.75), (x * 1.3 - .85, 24.0, 3.6)], 'hair', thickness=.3, up=(0, 1, 0),
                facing=(0, 0, 1), label='hair_strand')
        bottom = 12.8 - (k % 2) * 1.6 - (k == 2) * 1.0
        f.sheet('hair_tip', [(x * 1.3 - .9, 24.2, 3.6), (x * 1.3 + .9, 24.2, 3.6), (x * 1.6 + .55, bottom + 1.8, 5.2),
                             (x * 1.6 + .1, bottom, 5.4), (x * 1.6 - .45, bottom + 1.8, 5.2)], 'hair', thickness=.28,
                up=(0, 1, 0), facing=(0, 0, 1), label='hair_strand')


def circlet(f):
    b = 'head'
    c = np.array([0, 37.6, -.42])
    f.ring(b, c, 2.55, .3, .26, 'silver', segments=20, normal=(0, 1, 0), plain=True)
    # Crescent moon on the brow, horns up, with a sea-foam tear gem hanging onto the forehead.
    mid = np.array([0, 38.55, -2.98])
    arc = [mid + np.array([math.sin(a) * 1.15, -math.cos(a) * 1.0 + .3, 0]) for a in np.linspace(-2.05, 2.05, 9)]
    widths = [.16, .3, .42, .5, .5, .42, .3, .16]
    for p, q, wdt in zip(arc, arc[1:], widths):
        f.beam(b, p, q, wdt, .24, 'silver', z_hint=(0, 0, -1), label='crescent', plain=True)
    f.cube(b, (0, 37.62, -3.0), (.46, .46, .2), 'filigree', rot((0, 0, 45)), label='brow_setting')
    f.cube(b, (0, 37.02, -2.86), (.3, .5, .14), 'glow', rot((0, 0, 45)), label='brow_gem')
    f.beam(b, (0, 37.5, -2.9), (0, 37.2, -2.88), .06, .06, 'filigree', label='brow_thread')


def veil(f):
    """Lace mourning veil falling from the comb, cut into six streaming lengths."""
    for n in range(VEILS):
        b = f'veil_{n}'
        x0 = (n - 2.5) * .8
        x1 = (n - 2.5) * 1.5
        top = 41.9 - abs(n - 2.5) * .5
        drop = 22.6 - (1.4 if n in (1, 4) else 0) - (2.4 if n in (2, 3) else 0)
        f.sheet(b, [(x0 - .5, top, 1.55), (x0 + .5, top, 1.55),
                    (x1 + .85, drop + 1.2, 4.2 + abs(x1) * .1), (x1, drop, 4.4), (x1 - .85, drop + 1.2, 4.2 + abs(x1) * .1)],
                'lace', thickness=.12, up=(0, 1, 0), facing=(0, 0, 1), edge={2: ('scallop', .05, 2), 3: ('scallop', .05, 2)},
                label='veil', folds=2)


# ---------------------------------------------------------------------------
# Bodice, soul locket, mourning chains, shawl and cape


def torso(f):
    c = 'chest'
    tiers = ((22.2, 24.2, 4.0, 2.9, .2), (24.0, 26.2, 4.7, 3.2, .15), (26.0, 28.4, 5.5, 3.5, .1), (28.2, 30.3, 6.5, 3.1, .3))
    for i, (y0, y1, w, d, zc) in enumerate(tiers):
        f.box(c, (-w / 2, y0, zc - d / 2), (w, y1 - y0, d), 'velvet', label='bodice', folds=2)
    # Pale décolletage above a sweetheart neckline.
    f.box(c, (-2.3, 28.4, -1.2), (4.6, 1.9, 2.2), 'skin', label='decolletage')
    for side, s in SIDES:
        f.cube(c, (s * 1.25, 27.65, -1.55), (2.6, 2.3, .8), 'velvet', rot((-10, -s * 14, s * 8)), label='bust', folds=1)
        f.beam(c, (s * .15, 26.3, -2.02), (s * 2.55, 28.85, -1.75), .22, .22, 'filigree', label='neckline')
        f.beam(c, (s * 2.55, 28.85, -1.75), (s * 3.2, 29.9, -.9), .22, .22, 'filigree', label='neckline')
        # Corset boning under the bust.
        for k in range(3):
            x = s * (.6 + k * .62)
            f.beam(c, (x, 22.5, -1.62 + k * .08), (x * 1.08, 25.8 - k * .25, -1.95 + k * .1), .16, .14, 'filigree', label='boning')
    for k in range(5):
        y = 22.9 + k * .62
        f.beam(c, (-.55, y, -1.68 - k * .05), (.55, y + .5, -1.7 - k * .05), .12, .1, 'filigree', label='lacing')
        f.beam(c, (.55, y, -1.68 - k * .05), (-.55, y + .5, -1.7 - k * .05), .12, .1, 'filigree', label='lacing')
    # Amethyst soul locket on the sternum, caged in silver.
    heart = np.array([0, 27.0, -2.22])
    f.cube(c, heart, (1.05, 1.05, .55), 'soul', rot((0, 0, 45)), label='locket')
    f.ring(c, heart + (0, 0, -.05), .95, .14, .16, 'filigree', segments=10, normal=(0, 0, 1))
    for k in range(4):
        a = math.tau * k / 4 + math.pi / 4
        d = np.array([math.cos(a), math.sin(a), 0])
        f.beam(c, heart + d * .9, heart + d * 1.6 + (0, 0, .1), .12, .12, 'filigree', label='locket_ray')
    f.cube(c, heart + (0, 1.75, .05), (.34, .34, .3), 'glow', rot((0, 0, 45)), label='locket_glint')
    # Mourning bead chains draped from the shoulders to the locket.
    for side, s in SIDES:
        start, end = np.array([s * 3.0, 29.9, -1.1]), heart + (s * .7, .5, .08)
        sag = (start + end) / 2 + np.array([s * .2, -1.6, -.55])
        for k in range(9):
            u = (k + .5) / 9
            p = bezier([start, sag, end], u)
            f.cube(c, p, (.26, .26, .26), 'silver' if k % 3 else 'soul', rot((45, 45, 0)), label='bead')
    f.box(c, (-3.0, 22.6, 1.5), (6.0, 7.4, .5), 'velvet', label='back_panel', folds=2)


def shawl(f):
    """Lace capelet over each shoulder: three scalloped petals fanning over the arm."""
    for side, s in SIDES:
        b = f'shoulder_{side}'
        top = np.array([s * 4.2, 30.7, .3])
        f.cube(b, top + (s * .2, -.05, 0), (2.3, .7, 3.5), 'lace', rot((0, 0, -s * 20)), label='shawl_top', cell=.55)
        for k, (z, drop, splay) in enumerate(((-1.6, 3.6, -.5), (.3, 4.3, 0), (2.1, 3.7, .5))):
            root = top + np.array([-s * .2, .2, z])
            out = np.array([s * 1.0, 0, splay * .6])
            pts = [root + (0, 0, -1.25), root + (0, 0, 1.25), root + out * 2.0 + (0, -drop, 1.15 + splay * .5),
                   root + out * 2.4 + (0, -drop - .8, splay * .4), root + out * 2.0 + (0, -drop, -1.15 + splay * .5)]
            f.sheet(b, pts, 'lace', thickness=.14, up=(0, 1, 0), facing=(s, .45, splay), edge={2: ('scallop', .07, 1), 3: ('scallop', .07, 1)},
                    label='shawl', folds=2)
        f.cube(b, top + (-s * .7, -.3, -1.9), (.7, .7, .25), 'silver', rot((0, 0, 45)), label='shawl_pin')
        f.cube(b, top + (-s * .7, -.3, -2.03), (.36, .36, .14), 'glow', rot((0, 0, 45)), label='shawl_pin_gem')


def cape(f):
    """Long mourning cape from the shoulder blades, fraying into light."""
    strips = 5
    rows = (('cape', 30.0, 4.4, 2.3, 17.4, 5.8, 5.3), ('cape_tip', 17.4, 5.8, 5.3, 4.2, 7.2, 8.4))
    for bone, top, w_top, z_top, bottom, w_bot, z_bot in rows:
        tip = bone == 'cape_tip'
        for j in range(strips):
            a, b = -1 + 2 * j / strips - .02, -1 + 2 * (j + 1) / strips + .02
            bow = lambda u, w: (1 - u * u) * w * .16
            tl = np.array([a * w_top, top, z_top + bow(a, w_top)])
            tr = np.array([b * w_top, top, z_top + bow(b, w_top)])
            br = np.array([b * w_bot, bottom, z_bot + bow(b, w_bot)])
            bl = np.array([a * w_bot, bottom, z_bot + bow(a, w_bot)])
            if tip:
                br = br + (0, -(1.6 if j % 2 else .5), 0)
                bl = bl + (0, -(.5 if j % 2 else 1.6), 0)
            f.sheet(bone, [tl, tr, br, bl], 'gown', thickness=.16, up=(0, 1, 0), facing=(0, 0, 1),
                    edge={2: ('tatter', .05, 2)} if tip else {}, label='cape', folds=2, fade=.42 if tip else 0,
                    grade=(.24, .3) if tip else (.3, .4), lining=('north', LINING), hem=.04 if tip else None)


# ---------------------------------------------------------------------------
# Arms, bell sleeves and long weeping hands


def arms(f):
    for side, s in SIDES:
        sh, el, wr = J[f'shoulder_{side}'], J[f'elbow_{side}'], J[f'wrist_{side}']
        a, fa = f'arm_{side}', f'forearm_{side}'
        f.beam(a, sh + (0, -.2, 0), el + (0, .3, 0), 1.6, 1.65, 'velvet', label='arm', folds=1)
        f.beam(fa, el + (0, .3, 0), wr + (0, .5, 0), 1.15, 1.2, 'skin', label='forearm')
        f.beam(fa, el + (0, .2, 0), el + (s * .25, -2.6, -.1), 1.5, 1.55, 'velvet', label='cuff', folds=1)
        f.beam(fa, el + (s * .25, -2.6, -.1), el + (s * .28, -2.9, -.1), 1.62, 1.67, 'filigree', label='cuff_trim')
        sleeve(f, side, s)
        hand(f, side, s)


def sleeve(f, side, s):
    """Bell sleeve hanging from the elbow: dark outside, glowing lining, fraying into light."""
    b = f'sleeve_{side}'
    o = J[f'elbow_{side}'] + np.array([0, -.4, .3])
    outer = [o + (s * .2, 0, -1.0), o + (s * .9, 0, .9), o + (s * 2.6, -10.2, 2.9), o + (s * 1.6, -11.6, 1.6), o + (s * .9, -10.6, -.9)]
    back = [o + (s * .9, 0, .9), o + (-s * .9, 0, .9), o + (-s * 1.0, -10.4, 3.2), o + (s * .6, -11.8, 3.6), o + (s * 2.6, -10.2, 2.9)]
    for k, pts in enumerate((outer, back)):
        f.sheet(b, pts, 'gown', thickness=.14, up=(0, 1, 0), facing=(s, 0, .2) if k == 0 else (0, 0, 1),
                edge={2: ('tatter', .05, 2), 3: ('tatter', .05, 2)}, label='sleeve', folds=2, fade=.38, grade=(.26, .38),
                lining=('north', GLOW_LINING))


def hand(f, side, s):
    """Long pale hand, fingers trailing into glowing nails."""
    b = f'hand_{side}'
    w = J[f'wrist_{side}']
    f.box(b, (w[0] - .26, 16.15, -.75), (.52, 1.95, 1.5), 'skin', label='hands')
    f.box(b, (w[0] - .38, 17.6, -.82), (.76, .5, 1.64), 'lace', label='hands_cuff', cell=.4)
    for k in range(4):
        z = -.56 + k * .37
        base = np.array([w[0] + s * .04, 16.25, z])
        curl = .22 + k * .05
        mid = base + np.array([-s * curl, -1.8 + abs(k - 1.5) * .3, -.1])
        end = mid + np.array([-s * (curl + .22), -1.5 + abs(k - 1.5) * .25, -.16])
        f.beam(b, base, mid, .24, .26, 'skin', label='hands_finger')
        f.beam(b, mid, end, .21, .23, 'skin', label='hands_finger')
        d = unit(end - mid)
        f.beam(b, end - d * .1, end + d * .5, .22, .22, 'nail', label='hands_nail')
        f.spike(b, end + d * .45, end + d * 1.15, .17, 'glow', steps=2, label='hands_glow')
        if k == 2:
            f.box(b, (mid[0] - .17, mid[1] + .15, mid[2] - .17), (.34, .26, .34), 'silver', label='ring')
    thumb = np.array([w[0] - s * .26, 16.8, -.6])
    f.beam(b, thumb, thumb + (-s * .5, -1.3, -.8), .25, .26, 'skin', label='hands_finger')
    f.spike(b, thumb + (-s * .5, -1.3, -.8), thumb + (-s * .68, -2.05, -1.32), .21, 'nail', steps=2, label='hands_nail')
    f.spike(b, thumb + (-s * .65, -1.95, -1.25), thumb + (-s * .8, -2.5, -1.62), .15, 'glow', steps=2, label='hands_glow')


# ---------------------------------------------------------------------------
# Gown: an open dark overgown over a luminous underdress; lantern at the hip


def gown_point(angle, row, scale=1.0):
    y, rx, rz, sweep = row
    a = math.radians(angle)
    return np.array([rx * scale * math.sin(a), y, -rz * scale * math.cos(a) + sweep])


def drape(f, bone, a0, a1, rows, strips, mat, scale=1.0, tip=False, **paint):
    for j in range(strips):
        u0, u1 = a0 + (a1 - a0) * j / strips, a0 + (a1 - a0) * (j + 1) / strips
        pad = (a1 - a0) / strips * .04
        u0, u1 = u0 - pad, u1 + pad
        tl, tr = gown_point(u0, rows[0], scale), gown_point(u1, rows[0], scale)
        br, bl = gown_point(u1, rows[1], scale), gown_point(u0, rows[1], scale)
        if tip:
            br = br + (0, -(1.3 if j % 2 else .3), 0)
            bl = bl + (0, -(.3 if j % 2 else 1.3), 0)
        mid = math.radians((u0 + u1) / 2)
        f.sheet(bone, [tl, tr, br, bl], mat, thickness=.18, up=(0, 1, 0), facing=(math.sin(mid), 0, -math.cos(mid)),
                label=f'{bone.split("_tip")[0]}_{"low" if tip else "high"}', **paint)


def gown(f):
    p = 'pelvis'
    f.box(p, (-2.6, 19.4, -1.7), (5.2, 3.2, 3.8), 'velvet', label='hips', folds=2)
    # Silver girdle with a hanging soul charm.
    f.ring(p, (0, 21.9, .2), 2.9, .34, .3, 'silver', segments=16, normal=(0, 1, 0), plain=True)
    f.cube(p, (0, 21.6, -2.62), (.9, .9, .3), 'soul', rot((0, 0, 45)), label='girdle_gem')
    f.beam(p, (0, 21.1, -2.6), (0, 17.6, -3.0), .14, .14, 'silver', label='girdle_chain')
    f.cube(p, (0, 17.2, -3.05), (.46, .7, .2), 'glow', rot((0, 0, 45)), label='girdle_drop')
    lining = ('north', LINING)
    high, mid, low = GOWN_ROWS
    lower = (low[0] - .4, low[1], low[2], low[3])
    # Luminous underdress seen through the open front: deep at the waist, bright at the fray.
    drape(f, 'skirt_front', -42, 42, (high, mid), 4, 'mist', scale=.93, folds=4, fade=0, grade=(.46, .16), depth=.13)
    drape(f, 'skirt_front_tip', -42, 42, (mid, lower), 4, 'mist', scale=.93, tip=True, folds=4, fade=.42, grade=(.72, .46),
          depth=.13, hem=.04)
    # Dark overgown, open at the front, its hem fraying into light.
    for name, a0, a1 in (('r', 26, 128), ('back', 118, 242), ('l', 232, 334)):
        drape(f, f'skirt_{name}', a0, a1, (high, mid), 3, 'gown', folds=2, lining=lining, fade=0, grade=(.28, .4))
        drape(f, f'skirt_{name}_tip', a0, a1, (mid, low), 3, 'gown', tip=True, folds=2, fade=.4, lining=lining,
              edge={2: ('tatter', .05, 2)}, hem=.04, grade=(.24, .3))
    # Silver-edged front openings of the overgown.
    for side, s in SIDES:
        a = 26 if s > 0 else 334
        bone = 'skirt_r' if s > 0 else 'skirt_l'
        f.beam(bone, gown_point(a, high) + (0, 0, -.12), gown_point(a, mid) + (0, 0, -.12), .28, .2, 'filigree', label='gown_edge')
        f.beam(bone + '_tip', gown_point(a, mid) + (0, 0, -.12), gown_point(a, low) + (0, 4.4, -.1), .28, .2, 'filigree', label='gown_edge')


def lantern(f):
    """Soul lantern swinging from the girdle: a silver cage around an amethyst flame."""
    b = 'lantern'
    top = np.array(f.at(b))
    body = top + np.array([-.5, -4.6, -.4])
    f.beam(b, top, body + (0, 1.25, 0), .14, .14, 'silver', label='lantern_chain')
    f.box(b, body + (-.75, .9, -.75), (1.5, .35, 1.5), 'silver', label='lantern_cap', plain=True)
    f.cube(b, body + (0, 1.45, 0), (.7, .7, .7), 'silver', rot((0, 45, 0)), label='lantern_finial')
    f.box(b, body + (-.75, -1.25, -.75), (1.5, .32, 1.5), 'silver', label='lantern_cap', plain=True)
    for dx, dz in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
        f.box(b, body + (dx * .62 - .1, -1.0, dz * .62 - .1), (.2, 1.95, .2), 'filigree', label='lantern_post')
    f.cube(b, body + (0, -.15, 0), (.55, .8, .55), 'soul', rot((0, 45, 0)), label='lantern_core')
    for k in range(2):
        axis = np.array([math.cos(k * math.pi / 2), 0, math.sin(k * math.pi / 2)])
        base = body + (0, -.75, 0)
        pts = [base + axis * .5, base + axis * .22 + (0, .9, 0), base + (0, 1.45, 0), base - axis * .22 + (0, .9, 0), base - axis * .5]
        f.sheet(b, pts, 'soulflame', thickness=.05, up=(0, 1, 0), facing=np.cross(axis, (0, 1, 0)), label='lantern_flame')
    f.cube(b, body + (0, -1.6, 0), (.3, .45, .3), 'glow', rot((0, 45, 0)), label='lantern_drop')


# ---------------------------------------------------------------------------
# Aura: crescent halo cradling the head and orbiting soul wisps


def teardrop(f, bone, top, length, mat, label):
    """A hanging drop: rounded body narrowing to a fine point above it."""
    f.cube(bone, top + (0, -length * .62, 0), (length * .42, length * .42, length * .42), mat, rot((45, 0, 45)), label=label)
    f.spike(bone, top + (0, -length * .48, 0), top, length * .3, mat, steps=2, label=label)


def halo(f):
    b = 'halo'
    c = np.array(f.at(b))
    tilt = rot((-12, 0, 0))
    normal = tilt @ np.array([0, 0, 1.0])
    radius = 5.9
    arc = np.linspace(math.radians(-150), math.radians(150), 41)

    def band(r_out, width, depth, a_slice=slice(None)):
        angles = arc[a_slice]
        outer, inner = [], []
        for k, a in enumerate(angles):
            u = k / (len(angles) - 1)
            wdt = width(u)
            outer.append(c + tilt @ np.array([math.sin(a) * r_out, -math.cos(a) * r_out, depth]))
            inner.append(c + tilt @ np.array([math.sin(a) * (r_out - wdt), -math.cos(a) * (r_out - wdt), depth]))
        return outer + inner[::-1]
    f.sheet(b, band(radius, lambda u: .1 + 1.15 * math.sin(math.pi * u) ** 1.25, 0), 'silver', thickness=.3,
            up=tilt @ (0, 1, 0), facing=normal * -1, label='halo_crescent', engrave='vine')
    f.sheet(b, band(radius - .55, lambda u: .05 + .2 * math.sin(math.pi * u), -.2, slice(5, -5)), 'glow', thickness=.12,
            up=tilt @ (0, 1, 0), facing=normal * -1, label='halo_inner')
    # Tears of light hanging from the crescent on fine silver threads.
    for k, a in enumerate(np.linspace(math.radians(-112), math.radians(112), 7)):
        anchor = c + tilt @ np.array([math.sin(a) * (radius + .1), -math.cos(a) * (radius + .1), -.05])
        length = .9 + (k % 2) * .8 + (k == 3) * .5
        drop = anchor + np.array([0, -length, 0])
        f.beam(b, anchor, drop, .06, .06, 'filigree', label='chime_thread')
        teardrop(f, b, drop, 1.25 if k == 3 else .95, 'soul' if k == 3 else 'glow', 'chime')
    for side in (-1, 1):
        horn = c + tilt @ np.array([side * math.sin(math.radians(148)) * radius, -math.cos(math.radians(148)) * radius, 0])
        f.cube(b, horn + tilt @ (0, .4, 0), (.42, .62, .42), 'glow', rot((0, 45, 0)), label='halo_star')


def wisps(f):
    """Three will-o'-wisps: a soul gem inside a teardrop of sea-foam flame, tail trailing the orbit."""
    b = 'wisps'
    c = np.array(f.at(b))
    n = unit(np.array([0, math.cos(math.radians(14)), math.sin(math.radians(14))]))
    a_axis = unit(np.cross(n, [1, 0, 0]))
    b_axis = np.cross(n, a_axis)
    for k in range(3):
        ang = math.tau * k / 3 + .4
        p = c + (a_axis * math.cos(ang) + b_axis * math.sin(ang)) * 10.5
        # The orbit runs toward decreasing angle (the aura clip spins -Y): the tail trails behind.
        tangent = unit(a_axis * math.sin(ang) - b_axis * math.cos(ang))
        f.cube(b, p, (.62, .62, .62), 'soul', rot((45, 35, 0)), label='wisp_core')
        for q in range(2):
            across = unit(np.cross(tangent, n)) if q == 0 else n
            outline = [p + tangent * 1.0, p + tangent * .55 + across * .75, p - tangent * .5 + across * .85,
                       p - tangent * 2.1 + across * .45, p - tangent * 3.6, p - tangent * 2.1 - across * .45,
                       p - tangent * .5 - across * .85, p + tangent * .55 - across * .75]
            f.sheet(b, outline, 'wisp', thickness=.05, up=-tangent, facing=np.cross(tangent, across), label='wisp_flame')


def sculpt():
    f = Forge(MODEL, MATERIALS, seed=5150)
    skeleton(f)
    head(f)
    mantilla(f)
    hair(f)
    circlet(f)
    veil(f)
    torso(f)
    shawl(f)
    cape(f)
    arms(f)
    gown(f)
    lantern(f)
    halo(f)
    wisps(f)
    return f


def build():
    from banshee_mourning_motion import aura_clip, clips
    f = sculpt()
    motion, worst = clips(f)
    body = [b for b in f.bones if b not in AURA]
    for clip in motion:
        clip.bones = body
    model, atlas, stats = build_model(f, motion + [aura_clip()], MODEL, MODEL, [[-5, 0, -3], [5, 36, 3]],
                                      meta=dict(evergarden_revision=REVISION, evergarden_scale=SCALE), density=9.5)
    return f, model, atlas, stats, worst


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, default=OUT, help='Folder receiving arkhaia_banshee_wail.bbmodel and its manifest')
    args = parser.parse_args()
    f, model, atlas, stats, worst = build()
    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / f'{MODEL}.bbmodel').write_text(dump(model) + '\n', encoding='utf8')
    art = ROOT / 'evergarden/art/banshee-model'
    art.mkdir(parents=True, exist_ok=True)
    atlas.save(art / f'{MODEL}.png')
    manifest = dict(revision=REVISION, cubes=len(model['elements']) - 1, bones=len(f.bones), scale=SCALE,
                    texture=dict(size=model['resolution']['width'], density=round(stats['density'], 2), tiles=stats['tiles'],
                                 coverage=round(stats['coverage'], 3)),
                    aura_bones=list(AURA), animations={a['name']: a['length'] for a in model['animations']},
                    worst_ik_reach_error=round(worst, 3))
    (args.out / f'{MODEL}-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf8')
    print(f"Banshee: {manifest['cubes']} cubes, {manifest['bones']} bones, {len(model['animations'])} clips, "
          f"atlas {stats['coverage']:.0%} at {stats['density']:.1f}px/unit, IK error {worst:.2f}")


if __name__ == '__main__':
    main()
