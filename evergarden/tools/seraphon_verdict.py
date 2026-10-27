"""Seraphon, the Gilded Verdict: an unarmed six-winged seraph for the Supreme Sanctuary.

A floating judge of the Church of Raphion, deliberately distinct from the white Seraph
merchant at spawn: gilded filigree armour, white-and-gold silk, three pairs of wings,
a gyroscopic sun halo, a tilted orbit ring echoing the Sanctuary, and a verdict orb
hovering above the left palm. Aura bones are driven only by the looping 'aura' clip.
Body bones follow the shared boss contract (hi_gaze, head_forward, facing, limbs).
"""
import argparse
import json
import math
from pathlib import Path

import numpy as np

from boss_forge import Forge, Material, basis, bezier, build_model, dump, rot, unit

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'evergarden/src/main/resources/modelengine'
REVISION = 'seraphon-gilded-verdict-v1'
SCALE = 3.2

GOLD = ['#3a2506', '#5e3c0c', '#8a5a14', '#b57d22', '#d9a23a', '#f0c862', '#fbe39a', '#fff6d6']
PALE = ['#5b5038', '#8c7c58', '#bba97e', '#ddd0a6', '#f1e8c8', '#fffaf0']
CYAN = ['#0b3a52', '#137aa3', '#2fb6e0', '#7fe3ff', '#d8f8ff', '#ffffff']
SUN = ['#7a4a00', '#c47f00', '#f0b020', '#ffd860', '#fff0b0', '#ffffff']
MATERIALS = {
    'gold': Material(GOLD, 'plate', accent=CYAN),
    'goldtrim': Material(PALE, 'trim'),
    'silk': Material(['#4f4b45', '#77726a', '#a39e94', '#cbc6bc', '#e7e3da', '#f8f6f1', '#ffffff'], 'cloth',
                     trim=GOLD, accent=CYAN, density=.85),
    'azure': Material(['#0f2238', '#173452', '#21496f', '#2e6290', '#4783b3', '#6fa8d4'], 'cloth', trim=GOLD, density=.85),
    'feather': Material(['#6e6a62', '#a19c90', '#cfc9bb', '#ebe6da', '#fbf8f1', '#ffffff'], 'feather',
                        accent=['#8a5a14', '#d9a23a', '#f0c862', '#fbe39a'], trim=GOLD, density=.8),
    'feather_sky': Material(['#5d6a73', '#8fa2ad', '#c2d3dc', '#e2eef3', '#f6fbfd', '#ffffff'], 'feather',
                            accent=['#137aa3', '#2fb6e0', '#7fe3ff', '#d8f8ff'], trim=GOLD, density=.8),
    'feather_gold': Material(['#7a6440', '#b39a6a', '#dcc89a', '#f1e6c6', '#fcf6e6', '#ffffff'], 'feather',
                             accent=['#8a5a14', '#b57d22', '#d9a23a', '#f0c862'], trim=GOLD, density=.8),
    'skin': Material(['#9b7f74', '#c4a597', '#e3c7ba', '#f4e1d7', '#fdf1ea'], 'skin', density=1.6),
    'hair': Material(['#5e3d0a', '#8f6214', '#c38d26', '#e6b545', '#f6d77c', '#fff0b8'], 'hair'),
    'light': Material(CYAN, 'glow', glow=True, density=.8),
    'sunglow': Material(SUN, 'glow', glow=True, density=.8),
    'eye': Material(['#ffe9a8', '#fff6d8', '#ffffff'], 'glow', glow=True, density=1.6),
    'gem': Material(CYAN, 'gem', glow=True, density=1.5),
    'runes': Material(['#3a2506', '#8a5a14', '#f0b020', '#ffd860', '#fff6d6', '#ffffff'], 'runes', glow=True, density=1.4),
}
SIDES = (('r', 1), ('l', -1))
AURA = ('halo', 'halo_ring', 'orbit', 'sigil', 'orb')
MARKERS = ('root', 'facing', 'head_forward', 'hi_gaze', 'fx_core', 'fx_palm') + tuple(
    f'fx_{pair}_{side}' for pair in ('upper', 'middle', 'lower') for side in ('r', 'l'))
WINGS = ('upper', 'middle', 'lower')


def joints():
    j = dict(pelvis=(0, 20.0, 0), chest=(0, 22.6, 0), neck=(0, 31.0, .1))
    for side, s in SIDES:
        j.update({f'shoulder_{side}': (s * 5.4, 29.3, .2), f'elbow_{side}': (s * 6.2, 22.6, .5),
                  f'wrist_{side}': (s * 6.6, 16.4, 0), f'hip_{side}': (s * 2.3, 19.6, .1),
                  f'knee_{side}': (s * 2.4, 12.4, -.2), f'ankle_{side}': (s * 2.5, 5.4, .3)})
    return {k: np.array(v, float) for k, v in j.items()}


J = joints()
# Wing pairs: root, elbow, wrist, and the direction the flight feathers hang.
WING = {
    'upper': ((1.4, 29.6, 3.4), (6.0, 36.0, 5.6), (10.0, 42.6, 6.6), (1.0, -.32, .12), 'feather_sky', 1.0),
    'middle': ((2.0, 27.2, 3.6), (10.6, 30.6, 6.2), (19.8, 32.4, 7.0), (.12, -1.0, .1), 'feather', 1.15),
    'lower': ((1.7, 23.4, 3.4), (6.6, 17.9, 5.4), (10.4, 11.4, 6.0), (.86, -.48, .1), 'feather_gold', .85),
}


def skeleton(f):
    f.bone('root', None, [0, 0, 0])
    f.bone('facing', 'root', [0, 18, -16])
    f.bone('sigil', 'root', [0, .12, 0])
    f.bone('pelvis', 'root', J['pelvis'])
    f.bone('orbit', 'pelvis', [0, 21.0, .4])
    f.bone('chest', 'pelvis', J['chest'])
    f.bone('hi_gaze', 'chest', J['neck'])
    f.bone('head', 'hi_gaze', J['neck'])
    f.bone('head_forward', 'head', [0, 31.0, -7.0])  # level with the neck pivot: gaze direction marker
    f.bone('hair', 'head', [0, 35.0, 2.2])
    f.bone('hair_tip', 'hair', [0, 26.0, 3.6])
    f.bone('halo', 'chest', [0, 36.4, 5.6])
    f.bone('halo_ring', 'chest', [0, 36.4, 5.6])
    for side, s in SIDES:
        f.bone(f'shoulder_{side}', 'chest', J[f'shoulder_{side}'])
        f.bone(f'arm_{side}', 'chest', J[f'shoulder_{side}'])
        f.bone(f'forearm_{side}', f'arm_{side}', J[f'elbow_{side}'])
        f.bone(f'sleeve_{side}', f'forearm_{side}', J[f'elbow_{side}'] + (0, -1.2, .6))
        f.bone(f'hand_{side}', f'forearm_{side}', J[f'wrist_{side}'])
        for pair in WINGS:
            r, e, w = WING[pair][:3]
            m = lambda v: [s * v[0], v[1], v[2]]
            f.bone(f'wing_{pair}_{side}', 'chest', m(r))
            f.bone(f'wing_{pair}_mid_{side}', f'wing_{pair}_{side}', m(e))
            f.bone(f'wing_{pair}_tip_{side}', f'wing_{pair}_mid_{side}', m(w))
    f.bone('orb', 'hand_l', J['wrist_l'] + (2.6, -1.0, -.05))
    for side, s in SIDES:
        f.bone(f'leg_{side}', 'pelvis', J[f'hip_{side}'])
        f.bone(f'shin_{side}', f'leg_{side}', J[f'knee_{side}'])
        f.bone(f'foot_{side}', f'shin_{side}', J[f'ankle_{side}'])
        f.bone(f'skirt_{side}', 'pelvis', (s * 3.6, 20.4, .2))
        f.bone(f'skirt_tip_{side}', f'skirt_{side}', (s * 4.6, 11.4, .2))
        f.bone(f'sash_{side}', 'pelvis', (s * 1.6, 20.6, 3.0))
        f.bone(f'sash_tip_{side}', f'sash_{side}', (s * 2.2, 10.6, 4.4))
    f.bone('skirt_front', 'pelvis', (0, 20.6, -2.6))
    f.bone('skirt_front_tip', 'skirt_front', (0, 11.6, -3.2))
    f.bone('skirt_back', 'pelvis', (0, 20.6, 2.6))
    f.bone('skirt_back_tip', 'skirt_back', (0, 11.0, 3.6))
    # Invisible emitter markers for the server-side aura (core light, palm, feather tips).
    f.bone('fx_core', 'chest', (0, 26.6, -3.5))
    f.bone('fx_palm', 'hand_r', J['wrist_r'] + (-.9, -1.1, -.1))
    for side, s in SIDES:
        for pair in WINGS:
            r, e, w, hang, _, size = WING[pair]
            w, e, hang = np.array(w), np.array(e), unit(np.array(hang))
            tip = w + unit(w - e) * 6 * size + hang * 3.5 * size
            f.bone(f'fx_{pair}_{side}', f'wing_{pair}_tip_{side}', [s * tip[0], tip[1], tip[2]])


# ---------------------------------------------------------------------------
# Head, face, crown and hair


def paint_face(ctx, rgb, mat):
    """Serene features on the front face only: brows, nose shadow, lips."""
    w, h = ctx['w'], ctx['h']
    from PIL import Image, ImageDraw
    img = Image.new('L', (w * 4, h * 4), 0)
    d = ImageDraw.Draw(img)
    lip = Image.new('L', (w * 4, h * 4), 0)
    dl = ImageDraw.Draw(lip)
    cx = w * 2
    gild = Image.new('L', (w * 4, h * 4), 0)
    dg = ImageDraw.Draw(gild)
    for s in (-1, 1):
        d.line([(cx + s * w * .55, h * 4 * .4), (cx + s * w * 1.45, h * 4 * .37)], fill=255, width=3)
        # Gilded liner sweeping out from the eyes, a sun mark on the brow.
        dg.line([(cx + s * w * .5, h * 4 * .58), (cx + s * w * 1.7, h * 4 * .52), (cx + s * w * 1.9, h * 4 * .45)], fill=255, width=3)
    dg.polygon([(cx, h * 4 * .2), (cx + w * .22, h * 4 * .29), (cx, h * 4 * .38), (cx - w * .22, h * 4 * .29)], fill=255)
    d.line([(cx, h * 4 * .52), (cx + w * .18, h * 4 * .66)], fill=180, width=3)
    dl.ellipse((cx - w * .42, h * 4 * .76, cx + w * .42, h * 4 * .83), fill=255)
    shade = np.asarray(img.resize((w, h))) / 255.0
    lips = np.asarray(lip.resize((w, h))) > 90
    rgb[:] = rgb * (1 - shade[..., None] * .28)
    rgb[lips] = np.array([196, 120, 120])
    rgb[np.asarray(gild.resize((w, h))) > 100] = np.array([226, 178, 72])


def head(f):
    b = 'head'
    f.box(b, (-2.3, 31.5, -2.35), (4.6, 5.2, 4.7), 'skin', label='head', face=paint_face)
    for side, s in SIDES:
        f.cube(b, (s * .95, 34.25, -2.42), (1.1, .34, .12), 'eye', rot((0, 0, s * 5)), label='eyes')
    # Golden diadem with a sunburst crest and a cyan verdict gem.
    f.box(b, (-2.45, 35.1, -2.5), (4.9, .55, 5.0), 'gold', label='diadem', plain=True)
    f.cube(b, (0, 35.9, -2.62), (1.0, 1.0, .3), 'gem', rot((0, 0, 45)), label='diadem_gem')
    for k in range(-3, 4):
        a = math.radians(k * 18)
        base = np.array([math.sin(a) * 1.2, 35.6, -2.55 + abs(k) * .12])
        tip = base + np.array([math.sin(a) * 2.6, math.cos(a) * (3.4 if k == 0 else 2.6 - abs(k) * .3), -.3])
        f.spike(b, base, tip, .55 if k else .7, 'gold', steps=3, z_hint=(0, 0, -1), label='crown_ray')
    # Long golden hair: crown cap, side locks framing the face, back mane.
    f.box(b, (-2.5, 35.6, -2.0), (5.0, 1.5, 4.8), 'hair', label='hair_cap')
    for side, s in SIDES:
        f.cube(b, (s * 2.45, 33.2, -.9), (.5, 4.6, 2.2), 'hair', rot((0, 0, -s * 4)), label='hair_side')
        f.cube(b, (s * 2.25, 30.6, -1.6), (.45, 3.4, 1.0), 'hair', rot((0, 0, -s * 8)), label='hair_lock')
    f.box(b, (-2.5, 31.8, 1.6), (5.0, 4.4, 1.2), 'hair', label='hair_back')
    hb = 'hair'
    for k, x in enumerate((-2.0, -.7, .7, 2.0)):
        f.sheet(hb, [(x - .8, 35.0, 2.4 + abs(x) * .1), (x + .8, 35.0, 2.4 + abs(x) * .1), (x * 1.15 + .7, 26.2, 3.6),
                     (x * 1.15, 25.4, 3.7), (x * 1.15 - .7, 26.2, 3.6)], 'hair', thickness=.35, up=(0, 1, 0), facing=(0, 0, 1),
                label='hair_strand')
    for k, x in enumerate((-1.6, 0, 1.6)):
        f.sheet('hair_tip', [(x * 1.15 - .8, 26.4, 3.6), (x * 1.15 + .8, 26.4, 3.6), (x * 1.3 + .4, 19.8, 4.6),
                             (x * 1.3, 18.9, 4.7), (x * 1.3 - .4, 19.8, 4.6)], 'hair', thickness=.3, up=(0, 1, 0),
                facing=(0, 0, 1), label='hair_strand')


# ---------------------------------------------------------------------------
# Armoured torso, feather pauldrons, sleeves and hands


def torso(f):
    c = 'chest'
    f.box(c, (-1.1, 29.6, -1.0), (2.2, 2.0, 2.2), 'skin', label='neck')
    tiers = ((22.0, 23.8, 5.2, 3.8, .1), (23.8, 25.6, 6.2, 4.2, .15), (25.6, 27.8, 7.6, 4.8, .2), (27.8, 30.0, 8.4, 4.6, .3))
    for i, (y0, y1, w, d, zc) in enumerate(tiers):
        f.box(c, (-w / 2, y0, zc - d / 2), (w, y1 - y0, d), 'gold' if i >= 2 else 'silk', label='torso',
              engrave='vine' if i >= 2 else None, plain=i < 2)
    for side, s in SIDES:
        f.cube(c, (s * 1.95, 26.9, -2.45), (3.4, 3.4, .8), 'gold', rot((-8, -s * 16, -s * 6)), label='breastplate',
               engrave='vine')
        f.beam(c, (s * .3, 25.3, -2.75), (s * 3.6, 26.4, -2.25), .32, .28, 'goldtrim', label='breastplate_trim')
    f.cube(c, (0, 26.6, -3.05), (1.5, 2.1, .55), 'gem', rot((0, 0, 45)), label='core')
    for k in range(8):
        a = math.tau * k / 8
        length = 1.8 if k % 2 == 0 else 1.2
        f.beam(c, (math.cos(a) * 1.0, 26.6 + math.sin(a) * 1.0, -2.95), (math.cos(a) * (1.0 + length), 26.6 + math.sin(a) * (1.0 + length), -2.85),
               .26, .2, 'sunglow', label='core_ray')
    # Open high collar like a lily, gilded at the rim.
    for side, s in SIDES:
        f.cube(c, (s * 1.8, 31.1, .7), (.4, 2.8, 3.4), 'gold', rot((0, -s * 12, s * 22)), label='collar', plain=True)
        f.cube(c, (s * 1.25, 30.9, -1.3), (1.6, 2.2, .35), 'gold', rot((18, -s * 34, 0)), label='collar', plain=True)
    f.cube(c, (0, 31.4, 2.0), (4.2, 3.2, .4), 'gold', rot((-18, 0, 0)), label='collar', engrave='lines')
    f.box(c, (-3.6, 23.0, 1.9), (7.2, 6.6, .6), 'gold', label='backplate', engrave='scales')
    # Gilded sun-disk harness where the three wing pairs mount.
    hub = np.array([0, 26.8, 3.4])
    f.cube(c, hub, (6.4, 6.4, .7), 'gold', rot((0, 0, 45)), label='wing_harness', engrave='vine')
    f.cube(c, hub + (0, 0, .2), (4.6, 4.6, .6), 'goldtrim', label='wing_harness', plain=True)
    f.cube(c, hub + (0, 0, .5), (1.6, 1.6, .5), 'gem', rot((0, 0, 45)), label='harness_gem')
    for k in range(8):
        a = math.tau * k / 8
        d = np.array([math.cos(a), math.sin(a), 0])
        f.spike(c, hub + d * 3.0 + (0, 0, .2), hub + d * (5.2 if k % 2 == 0 else 4.2) + (0, 0, .3), .8, 'gold', steps=3,
                z_hint=(0, 0, 1), label='harness_ray')


def pauldrons(f):
    """Overlapping gilded feather-plates; each lame is an alpha-cut feather shape."""
    for side, s in SIDES:
        b = f'shoulder_{side}'
        top = np.array([s * 6.0, 30.2, .2])
        f.box(b, (s * 5.6 - 1.4, 28.0, -1.6), (2.8, 2.6, 3.6), 'silk', label='shoulder_cap', plain=True)
        for k in range(4):
            a = math.radians(14 + k * 19)
            normal = np.array([s * math.sin(a), math.cos(a), 0])
            along = np.array([s * math.cos(a), -math.sin(a), 0])
            center = top + normal * 1.4 + along * (k * .9) + np.array([0, -.2 * k, .15 * k])
            r = basis(along, normal)
            f.cube(b, center, (5.6 - k * .5, 2.6, .5), 'gold', r, label='pauldron', engrave='scales' if k == 0 else 'chevron')
            f.cube(b, center + r @ np.array([0, 1.25, .2]), (5.7 - k * .5, .32, .55), 'goldtrim', r, label='pauldron_trim')
        for k in range(3):
            base = top + np.array([s * (.4 + k * .9), 1.2 - k * .5, -.8 + k * .8])
            tip = base + np.array([s * (2.4 + k * .4), 2.2 - k * .5, 1.6])
            f.spike(b, base, tip, .7, 'gold', steps=3, label='pauldron_feather')


def arms(f):
    for side, s in SIDES:
        sh, el, wr = J[f'shoulder_{side}'], J[f'elbow_{side}'], J[f'wrist_{side}']
        a, fa, hb = f'arm_{side}', f'forearm_{side}', f'hand_{side}'
        f.beam(a, sh + (0, -.3, 0), el + (0, .3, 0), 2.3, 2.4, 'silk', label='arm', plain=True)
        f.cube(a, (s * 5.9, 25.0, .3), (2.7, 1.4, 2.8), 'gold', label='arm_band', plain=True)
        f.beam(fa, el + (0, .2, 0), wr + (0, .6, 0), 2.2, 2.3, 'silk', label='forearm', plain=True)
        f.beam(fa, el + (s * .05, -1.2, -.1), wr + (s * .1, 1.0, -.05), 2.6, 2.7, 'gold', label='vambrace', engrave='vine')
        f.cube(fa, (s * 6.5, 17.6, 0), (2.9, .4, 2.9), 'goldtrim', label='vambrace_trim')
        f.cube(fa, (s * 6.45, 20.0, -1.4), (.7, .7, .25), 'gem', rot((0, 0, 45)), label='vambrace_gem')
        # Hanging bell sleeve (silk outside, azure lining) flowing from the elbow.
        sl = f'sleeve_{side}'
        o = el + np.array([0, -1.2, .6])
        f.sheet(sl, [o + (-s * 1.4, 0, .4), o + (s * 1.4, 0, .4), o + (s * 1.9, -8.2, 2.8), o + (0, -9.6, 3.4),
                     o + (-s * 1.6, -8.2, 2.6)], 'silk', thickness=.18, up=(0, 1, 0), facing=(s * .35, 0, 1),
                edge={2: ('wave', .03, 2), 3: ('wave', .03, 2)}, label='sleeve', hem=.2, lining=('south', MATERIALS['azure'].ramp_hex))
        hand(f, side, s)


def hand(f, side, s):
    """Elegant open hand: a palm and four slender jointed fingers plus a thumb."""
    b = f'hand_{side}'
    w = J[f'wrist_{side}']
    f.box(b, (w[0] - .55, 14.6, -.95), (1.1, 1.9, 1.9), 'skin', label='hands')
    f.box(b, (w[0] - .7, 16.0, -1.05), (1.4, .5, 2.1), 'gold', label='hands_cuff', plain=True)
    for k in range(4):
        z = -.6 + k * .42
        base = np.array([w[0] + s * .1, 14.7, z])
        mid = base + np.array([-s * .12, -1.6 + abs(k - 1.5) * .25, -.12])
        tip = mid + np.array([-s * .28, -1.2 + abs(k - 1.5) * .2, -.2])
        f.beam(b, base, mid, .36, .38, 'skin', label='hands_finger')
        f.beam(b, mid, tip, .32, .34, 'skin', label='hands_finger')
        if k == 1:
            f.box(b, (mid[0] - .22, mid[1] + .2, mid[2] - .22), (.44, .3, .44), 'gold', label='ring')
    f.beam(b, (w[0] - s * .55, 15.2, -.7), (w[0] - s * 1.0, 13.8, -1.8), .4, .42, 'skin', label='hands_finger')


# ---------------------------------------------------------------------------
# Lower body: legs en pointe, gilded faulds, layered silk skirts and sashes


def lower_body(f):
    p = 'pelvis'
    f.box(p, (-2.9, 19.0, -1.8), (5.8, 3.0, 3.6), 'silk', label='pelvis', plain=True)
    f.box(p, (-3.1, 21.4, -2.0), (6.2, 1.1, 4.0), 'gold', label='belt', plain=True)
    f.cube(p, (0, 21.9, -2.15), (1.2, 1.2, .35), 'gem', rot((0, 0, 45)), label='belt_gem')
    for side, s in SIDES:
        for k in range(3):
            f.cube(p, (s * (2.3 + k * .35), 20.5 - k * 1.25, -1.9 + k * .05), (2.0, 1.3, .45), 'gold', rot((-6, 0, s * (14 + k * 6))),
                   label='fauld', plain=True)
    for side, s in SIDES:
        hip, knee, ankle = J[f'hip_{side}'], J[f'knee_{side}'], J[f'ankle_{side}']
        lg, sh, ft = f'leg_{side}', f'shin_{side}', f'foot_{side}'
        f.beam(lg, hip + (0, .2, 0), knee + (0, .4, 0), 2.7, 2.8, 'silk', label='leg', plain=True)
        f.beam(sh, knee + (0, -.2, 0), ankle + (0, .3, 0), 2.3, 2.4, 'gold', label='shin', engrave='vine')
        f.box(sh, (s * 2.4 - 1.25, 11.3, -1.5), (2.5, 2.0, 1.0), 'gold', label='knee', plain=True)
        f.cube(sh, (s * 2.4, 12.3, -2.05), (.75, .75, .25), 'gem', rot((0, 0, 45)), label='knee_gem')
        f.beam(sh, (s * 2.4, 10.8, -1.25), (s * 2.5, 6.2, -1.15), .5, .4, 'goldtrim', label='shin_ridge')
        # Pointed sabaton: the seraph floats en pointe.
        f.box(ft, (s * 2.5 - 1.0, 4.0, -.8), (2.0, 2.0, 2.0), 'gold', label='foot', plain=True)
        f.spike(ft, (s * 2.5, 4.2, -.5), (s * 2.5, 1.2, -1.4), 1.6, 'gold', steps=3, depth_ratio=.8, label='foot_toe')
    panels = (('skirt_front', 'skirt_front_tip', (0, 20.6, -2.6), 3.4, 3.2, 11.6, 4.6, (0, 0, -1), -1),
              ('skirt_back', 'skirt_back_tip', (0, 20.6, 2.6), 4.4, 5.4, 11.0, 2.4, (0, 0, 1), 1))
    for bone, tip_bone, top, w0, w1, mid_y, bottom_y, facing, zs in panels:
        z0 = top[2]
        outer = 'azure' if zs < 0 else 'silk'
        inner = MATERIALS['silk' if zs < 0 else 'azure'].ramp_hex
        f.sheet(bone, [(-w0 / 2, 20.6, z0), (w0 / 2, 20.6, z0), (w1 / 2, mid_y, z0 + zs * .7), (-w1 / 2, mid_y, z0 + zs * .7)],
                outer, thickness=.18, up=(0, 1, 0), facing=facing, label='skirt',
                emblem=dict(kind='sun', y=.5, r=.32, faces=('south',)) if bone == 'skirt_front' else None,
                lining=('north', inner), folds=3)
        f.sheet(tip_bone, [(-w1 / 2, mid_y, z0 + zs * .7), (w1 / 2, mid_y, z0 + zs * .7), (w1 / 2 + .4, bottom_y, z0 + zs * 1.6),
                           (0, bottom_y - 1.4, z0 + zs * 1.7), (-w1 / 2 - .4, bottom_y, z0 + zs * 1.6)], outer, thickness=.18,
                up=(0, 1, 0), facing=facing, edge={2: ('wave', .02, 2), 3: ('wave', .02, 2)}, label='skirt', hem=.25,
                lining=('north', inner), folds=3)
    for side, s in SIDES:
        top = np.array([s * 3.6, 20.4, .2])
        f.sheet(f'skirt_{side}', [top + (0, 0, -2.0), top + (0, 0, 2.0), top + (s * 1.6, -9.0, 2.6), top + (s * 1.6, -9.0, -2.4)],
                'silk', thickness=.18, up=(0, 1, 0), facing=(s, 0, 0), label='skirt', folds=2,
                lining=('north', MATERIALS['azure'].ramp_hex))
        f.sheet(f'skirt_tip_{side}', [top + (s * 1.6, -9.0, -2.4), top + (s * 1.6, -9.0, 2.6), top + (s * 2.2, -16.4, 3.0),
                                      top + (s * 2.0, -17.4, .2), top + (s * 2.2, -16.4, -2.6)], 'silk', thickness=.18,
                up=(0, 1, 0), facing=(s, 0, 0), edge={2: ('wave', .02, 2), 3: ('wave', .02, 2)}, label='skirt', hem=.25,
                lining=('north', MATERIALS['azure'].ramp_hex), folds=2)
        sb = np.array([s * 1.6, 20.6, 3.0])
        f.sheet(f'sash_{side}', [sb + (-.6, 0, 0), sb + (.6, 0, 0), sb + (s * .6 + .6, -10, 1.4), sb + (s * .6 - .6, -10, 1.4)],
                'azure', thickness=.14, up=(0, 1, 0), facing=(0, 0, 1), label='sash', folds=1)
        f.sheet(f'sash_tip_{side}', [sb + (s * .6 - .6, -10, 1.4), sb + (s * .6 + .6, -10, 1.4), sb + (s * 1.4 + .6, -18.6, 3.2),
                                     sb + (s * 1.4, -19.6, 3.3), sb + (s * 1.4 - .6, -18.6, 3.2)], 'azure', thickness=.14, up=(0, 1, 0),
                facing=(0, 0, 1), label='sash', folds=1, hem=.3)


# ---------------------------------------------------------------------------
# Six wings of layered, alpha-cut feathers on gilded wing-arms


def feather(f, bone, root, direction, length, width, mat, normal, label='feather', tip=.78, gild=False):
    d = unit(direction)
    n = unit(np.asarray(normal, float) - d * (np.asarray(normal, float) @ d))
    across = np.cross(d, n)
    shape = ((.5, 0), (.3, .06), (.12, .32), (.08, .66), (.26, .92), (.5, 1.0), (.68, .94), (.9, .7), (.92, .34), (.72, .06))
    pts = [np.asarray(root, float) + d * (t * length) + across * ((s - .5) * width) for s, t in shape]
    return f.sheet(bone, pts, mat, thickness=.1, up=d, facing=n, label=label, tip=tip, gild=gild)


def wings(f):
    for side, s in SIDES:
        for pair in WINGS:
            r0, e0, w0, fd, mat, size = WING[pair]
            m = lambda v: np.array([s * v[0], v[1], v[2]], float)
            root, el, wr, hang = m(r0), m(e0), m(w0), unit(m(fd))
            b0, b1, b2 = f'wing_{pair}_{side}', f'wing_{pair}_mid_{side}', f'wing_{pair}_tip_{side}'
            edge1, edge2 = unit(el - root), unit(wr - el)
            normal = unit(np.cross(edge2, hang)) * (1 if s > 0 else -1)
            if normal[2] < 0:
                normal = -normal
            # Gilded wing-arm along the leading edge.
            f.taper(b0, [root + edge1 * 1.2, (root + el) / 2 + m((0, .4, 0)), el], [.9 * size, 1.05 * size], [.85 * size, .95 * size],
                    'gold', z_hint=(0, 0, 1), label='wing_arm')
            f.taper(b1, [el, (el + wr) / 2 + m((0, .3, 0)), wr], [1.05 * size, .9 * size], [.95 * size, .85 * size], 'gold',
                    z_hint=(0, 0, 1), label='wing_arm')
            f.box(b1, el - .7 * size, np.full(3, 1.4 * size), 'goldtrim', label='wing_joint')
            f.box(b2, wr - .6 * size, np.full(3, 1.2 * size), 'goldtrim', label='wing_joint')
            f.cube(b2, wr + normal * .5, (.8, .8, .3), 'gem', basis(edge2, normal) @ rot((0, 0, 45)), label='wing_gem')
            # Primaries fan out from the wrist, longest at the leading edge.
            count = 9
            for k in range(count):
                u = k / (count - 1)
                direction = unit(edge2 * (1 - u * .95) + hang * (u * 1.1 + .15))
                length = (11.5 - u * 3.5) * size
                feather(f, b2, wr + edge2 * .3 - normal * (.12 + u * .05) + hang * u * .4, direction, length, 1.7 * size, mat,
                        normal, tip=.74, gild=(k == 0))
            # Secondaries along the forearm, coverts layered over them.
            for k in range(7):
                u = (k + .5) / 7
                base = el + (wr - el) * u
                direction = unit(hang + edge2 * (u - .7) * .35)
                feather(f, b1, base - normal * (.08 + .04 * (k % 2)), direction, (6.0 + u * 2.0) * size, 1.6 * size, mat, normal, tip=.8)
                feather(f, b1, base + normal * .14 + hang * .3, unit(hang + edge2 * .25), 3.4 * size, 1.3 * size, mat, normal,
                        label='covert', tip=.86, gild=True)
            for k in range(5):
                u = (k + .5) / 5
                base = root + (el - root) * u
                feather(f, b0, base - normal * .08, unit(hang - edge1 * .25), (4.0 + u * 2.4) * size, 1.5 * size, mat, normal, tip=.82)
                feather(f, b0, base + normal * .14 + hang * .25, unit(hang + edge1 * .1), 2.6 * size, 1.2 * size, mat, normal,
                        label='covert', tip=.88, gild=True)


# ---------------------------------------------------------------------------
# Aura: sun halo, gyroscope ring, waist orbit, floor sigil and the verdict orb


def aura(f):
    c = np.array(f.at('halo'))
    f.ring('halo', c, 6.0, .55, .5, 'gold', segments=28, normal=(0, 0, 1))
    f.ring('halo', c + (0, 0, -.2), 4.9, .26, .26, 'light', segments=28, normal=(0, 0, 1), phase=.11)
    f.ring('halo', c + (0, 0, -.15), 2.2, .3, .3, 'sunglow', segments=14, normal=(0, 0, 1))
    for k in range(24):
        a = math.tau * k / 24 + math.pi / 2
        d = np.array([math.cos(a), math.sin(a), 0])
        length = 3.4 if k % 2 == 0 else 1.7
        f.spike('halo', c + d * 6.1, c + d * (6.1 + length), .7 if k % 2 == 0 else .5, 'sunglow' if k % 6 == 0 else 'gold',
                steps=3, z_hint=(0, 0, 1), label='halo_ray')
    for k in range(4):
        a = math.tau * k / 4 + math.pi / 2
        p = c + np.array([math.cos(a), math.sin(a), 0]) * 6.0 + (0, 0, -.35)
        f.cube('halo', p, (1.1, 1.1, .5), 'gem', rot((0, 0, 45)), label='halo_gem')
    for k in range(12):
        a = math.tau * k / 12
        d = np.array([math.cos(a), math.sin(a), 0])
        f.beam('halo', c + d * 2.3 + (0, 0, -.2), c + d * 4.7 + (0, 0, -.2), .16, .16, 'light', z_hint=(0, 0, 1), label='halo_spoke')
    # Gyroscope ring tilted across the halo; it precesses around the vertical axis.
    tilt = unit(np.array([.9, .2, .4]))
    f.ring('halo_ring', c, 8.0, .32, .32, 'goldtrim', segments=32, normal=tilt)
    a = unit(np.cross(tilt, [0, 1, 0]))
    bvec = np.cross(tilt, a)
    for k in range(4):
        ang = math.tau * k / 4
        p = c + (a * math.cos(ang) + bvec * math.sin(ang)) * 8.0
        f.cube('halo_ring', p, (.9, .9, .9), 'sunglow', rot((45, 45, 0)), label='halo_orb')
    # Tilted orbit around the waist, as above the Sanctuary.
    o = np.array(f.at('orbit'))
    n = unit(np.array([0, math.cos(math.radians(16)), math.sin(math.radians(16))]))
    f.ring('orbit', o, 10.5, .3, .3, 'goldtrim', segments=36, normal=n)
    a = unit(np.cross(n, [1, 0, 0]))
    bvec = np.cross(n, a)
    for k in range(3):
        ang = math.tau * k / 3
        p = o + (a * math.cos(ang) + bvec * math.sin(ang)) * 10.5
        f.cube('orbit', p, (1.3, 1.3, 1.3), 'light', rot((45, 35, 0)), label='orbit_orb')
        f.cube('orbit', p, (1.8, .25, 1.8), 'goldtrim', rot((0, 45, 0)), label='orbit_band')
    # Floor sigil under the floating judge.
    s0 = np.array(f.at('sigil'))
    f.ring('sigil', s0, 6.4, .3, .08, 'sunglow', segments=32, normal=(0, 1, 0))
    f.ring('sigil', s0, 5.2, .18, .08, 'sunglow', segments=28, normal=(0, 1, 0), phase=.1)
    for k in range(12):
        ang = math.tau * k / 12
        d = np.array([math.cos(ang), 0, math.sin(ang)])
        f.beam('sigil', s0 + d * 2.0, s0 + d * (5.0 if k % 3 == 0 else 4.1), .26, .08, 'sunglow', z_hint=(0, 1, 0), label='sigil_ray')
    # Verdict orb: a caged sun hovering above the left palm.
    ob = np.array(f.at('orb'))
    f.cube('orb', ob, (1.8, 1.8, 1.8), 'sunglow', rot((45, 35, 0)), label='orb_core')
    f.ring('orb', ob, 1.8, .16, .16, 'goldtrim', segments=12, normal=(0, 1, 0))
    f.ring('orb', ob, 1.8, .16, .16, 'goldtrim', segments=12, normal=(1, 0, 0))
    f.ring('orb', ob, 1.8, .16, .16, 'goldtrim', segments=12, normal=(0, 0, 1))


def sculpt():
    for m in MATERIALS.values():
        m.ramp_hex = ['#%02x%02x%02x' % tuple(int(v) for v in c) for c in m.ramp]
    f = Forge('seraphon', MATERIALS, seed=7777)
    skeleton(f)
    head(f)
    torso(f)
    pauldrons(f)
    arms(f)
    lower_body(f)
    wings(f)
    aura(f)
    return f


def build():
    from seraphon_motion import aura_clip, clips
    f = sculpt()
    motion, worst = clips(f)
    body = [b for b in f.bones if b not in AURA and b not in MARKERS]
    for clip in motion:
        clip.bones = body
    model, atlas, stats = build_model(f, motion + [aura_clip()], 'seraphon', 'seraphon', [[-8, 0, -8], [8, 40, 8]],
                                      meta=dict(evergarden_revision=REVISION, evergarden_scale=SCALE), density=9.0)
    return model, atlas, stats, worst


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, default=OUT, help='Folder receiving seraphon.bbmodel and its manifest')
    args = parser.parse_args()
    model, atlas, stats, worst = build()
    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / 'seraphon.bbmodel').write_text(dump(model) + '\n', encoding='utf8')
    art = ROOT / 'evergarden/art/seraphon-model'
    art.mkdir(parents=True, exist_ok=True)
    atlas.save(art / 'seraphon.png')
    manifest = dict(revision=REVISION, cubes=len(model['elements']) - 1, bones=len(sculpt().bones), scale=SCALE,
                    texture=dict(size=model['resolution']['width'], density=round(stats['density'], 2), tiles=stats['tiles'],
                                 coverage=round(stats['coverage'], 3)),
                    aura_bones=list(AURA), animations={a['name']: a['length'] for a in model['animations']},
                    worst_ik_reach_error=round(worst, 3))
    (args.out / 'seraphon-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf8')
    print(f"Seraphon: {manifest['cubes']} cubes, {manifest['bones']} bones, {len(model['animations'])} clips, "
          f"atlas {stats['coverage']:.0%} at {stats['density']:.1f}px/unit, IK error {worst:.2f}")


if __name__ == '__main__':
    main()
