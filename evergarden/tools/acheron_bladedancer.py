"""Acheron, the Weeping Bladedancer: the crimson duelist of the Six Rivers.

A lean, hooded blade-dancer replaces the boxy crimson sovereign: a porcelain sorrow mask
weeping blood-light under a crimson cowl, swept-back ruby horns, a long scarf and split
coat tails that stream with every move, light layered plate over a dark wrapped body, rib
lames around a ruby soul core, bladed vambraces and two curved ruby Sorrow-Quills.

Keeps the river contract read by RiverVisual, BossHitbox, Geyser and the probes: the
original bone names (hi_gaze, head, head_forward, jaw, collar, veil_0..5, cape/cape_tip,
focus, familiar, halo and shoulder/arm/forearm/hand/weapon/leg/shin/foot/sash per side,
right side on -X), the 13-clip table in RIVER_CLIPS order, Acheron's 1.2 s spin with
both weapon bones held out for the quill fans, and runtime scale 4. Also writes the echo
blueprint: 60% geometry, pivots and translations, no halo bone, identical UVs and a
recoloured afterimage texture.
"""
import argparse
import base64
import copy
import io
import json
import math
import uuid
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from boss_forge import Forge, Material, bezier, build_model, dump, rot, unit

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'evergarden/src/main/resources/modelengine/rivers'
MODEL = 'arkhaia_acheron'
ECHO = 'arkhaia_acheron_echo'
REVISION = 'acheron-bladedancer-v1'
SCALE = 4.0
ECHO_SCALE = .6

CRIMSON = ['#1c060d', '#2f0914', '#4b0d22', '#6d1130', '#951841', '#c01f52', '#e3356b', '#ff6b93']
BLOOD = ['#2e0410', '#5e0820', '#9c0f35', '#d9184a', '#f54268', '#ff7a98', '#ffc2d0', '#ffffff']
UMBRA = ['#07050a', '#0e0a12', '#160f1b', '#211622', '#2d1d2c', '#3b2537', '#4d2f45', '#643b57']
GOLD = ['#1a1005', '#2f1d08', '#4d300d', '#755015', '#a6741f', '#d29b31', '#f2c45a', '#ffe7a3']
RUBY = ['#2a040e', '#55091c', '#8a0f2f', '#c41744', '#f5275a', '#ff6188', '#ffadc2', '#ffffff']
SILK = ['#18050b', '#2b0812', '#430b1c', '#600f29', '#841538', '#ab1d4a', '#d1295e', '#f2457a']
PORCELAIN = ['#3b2a2e', '#5e4a4f', '#8a7378', '#b39fa3', '#d5c6c8', '#ece2e3', '#fbf6f6']
HORN = ['#100508', '#1f0910', '#350d1a', '#521327', '#721937', '#962449', '#bd3561']
MATERIALS = {
    'plate': Material(CRIMSON, 'plate', accent=BLOOD, density=1.0),
    'umbra': Material(UMBRA, 'cloth', trim=GOLD, density=.9, folds=2),
    'wrap': Material(UMBRA, 'leather', density=1.0),
    'gold': Material(GOLD, 'trim', density=1.1),
    'silk': Material(SILK, 'cloth', trim=GOLD, accent=BLOOD, density=.9),
    'veil': Material(SILK, 'ghost', trim=GOLD, accent=BLOOD, density=.85),
    'mask': Material(PORCELAIN, 'skin', density=2.2),
    'horn': Material(HORN, 'horn', density=1.2),
    'blade': Material(RUBY, 'blade', density=1.3),
    'glow': Material(BLOOD, 'glow', glow=True, density=1.0),
    'eye': Material(['#ff4a6e', '#ff9db2', '#ffe3ea', '#ffffff'], 'glow', glow=True, density=2.4),
    'gem': Material(RUBY, 'gem', glow=True, density=1.6),
}
SIDES = (('r', -1), ('l', 1))
SCARF = (('veil_0', 'veil_1', 'veil_2'), ('veil_3', 'veil_4', 'veil_5'))


def joints():
    j = dict(pelvis=(0, 16.4, .2), chest=(0, 21.6, .4), neck=(0, 29.6, .2), head=(0, 30.8, 0))
    for side, s in SIDES:
        j.update({f'shoulder_{side}': (s * 5.4, 28.3, .3), f'elbow_{side}': (s * 6.5, 22.5, .7),
                  f'wrist_{side}': (s * 6.9, 17.0, -.1), f'grip_{side}': (s * 6.95, 15.9, -.35),
                  f'hip_{side}': (s * 2.2, 15.9, .2), f'knee_{side}': (s * 2.45, 8.9, -.35),
                  f'ankle_{side}': (s * 2.6, 1.7, .55)})
    return {k: np.array(v, float) for k, v in j.items()}


J = joints()
FACE_Z = -2.62
EYE_Y = 33.55
HEAD_SCALE = .86


def skeleton(f):
    f.bone('root', None, [0, 0, 0])
    f.bone('pelvis', 'root', J['pelvis'])
    f.bone('chest', 'pelvis', J['chest'])
    f.bone('neck', 'chest', J['neck'])
    f.bone('hi_gaze', 'neck', J['head'])
    f.bone('head', 'hi_gaze', J['head'])
    f.bone('head_forward', 'head', J['head'] + (np.array([0, 33.4, -7.0]) - J['head']) * HEAD_SCALE)
    f.bone('jaw', 'head', J['head'] + (np.array([0, 31.7, -1.2]) - J['head']) * HEAD_SCALE)
    f.bone('collar', 'chest', [0, 29.3, .9])
    for chain, s in zip(SCARF, (-1, 1)):
        f.bone(chain[0], 'collar', [s * 1.0, 29.7, 2.5])
        f.bone(chain[1], chain[0], [s * 1.3, 25.4, 3.5])
        f.bone(chain[2], chain[1], [s * 1.55, 20.9, 4.3])
    f.bone('cape', 'pelvis', [0, 19.1, 2.3])
    f.bone('cape_tip', 'cape', [0, 11.4, 3.4])
    f.bone('focus', 'chest', [-10, 18, -5])
    f.bone('familiar', 'chest', [13, 25, 0])
    f.bone('halo', 'chest', [0, 35, 3])
    for side, s in SIDES:
        f.bone(f'shoulder_{side}', 'chest', J[f'shoulder_{side}'])
        f.bone(f'arm_{side}', 'chest', J[f'shoulder_{side}'])
        f.bone(f'forearm_{side}', f'arm_{side}', J[f'elbow_{side}'])
        f.bone(f'hand_{side}', f'forearm_{side}', J[f'wrist_{side}'])
        f.bone(f'weapon_{side}', f'hand_{side}', J[f'grip_{side}'])
        f.bone(f'leg_{side}', 'pelvis', J[f'hip_{side}'])
        f.bone(f'shin_{side}', f'leg_{side}', J[f'knee_{side}'])
        f.bone(f'foot_{side}', f'shin_{side}', J[f'ankle_{side}'])
        f.bone(f'sash_{side}', 'pelvis', [s * 2.3, 17.6, -1.75])


# ---------------------------------------------------------------------------
# Mask, cowl and horns


def _layer(w, h):
    img = Image.new('L', (w * 4, h * 4), 0)
    return img, ImageDraw.Draw(img)


def _down(img, w, h):
    return np.asarray(img.resize((w, h), Image.Resampling.BOX), float) / 255


def paint_mask(ctx, rgb, mat):
    """Noh-like sorrow mask: lifted grieving brows, dark slit sockets, a thin downturned mouth
    and two streams of blood-light running from the eyes."""
    w, h = ctx['w'], ctx['h']
    W, H = w * 4, h * 4
    cx = W / 2
    ey = H * .36
    shade, ink, tears = _layer(w, h), _layer(w, h), _layer(w, h)
    for s in (-1, 1):
        x = cx + s * W * .23
        shade[1].ellipse((x - W * .2, ey - H * .1, x + W * .2, ey + H * .12), fill=110)
        ink[1].polygon([(x - s * W * .13, ey + H * .015), (x + s * W * .15, ey - H * .03), (x + s * W * .13, ey + H * .03),
                        (x - s * W * .12, ey + H * .045)], fill=255)
        ink[1].line([(cx + s * W * .05, ey - H * .1), (cx + s * W * .18, ey - H * .14), (cx + s * W * .38, ey - H * .08)], fill=255, width=5)
        tears[1].line([(x + s * W * .02, ey + H * .05), (x + s * W * .05, H * .62), (x + s * W * .02, H * .9), (x + s * W * .04, H)],
                      fill=255, width=6)
        shade[1].polygon([(cx + s * W * .2, H * .55), (cx + s * W * .46, H * .5), (cx + s * W * .45, H * .85), (cx + s * W * .3, H * .8)], fill=70)
    ink[1].line([(cx - W * .15, H * .86), (cx, H * .83), (cx + W * .15, H * .86)], fill=255, width=4)
    shade[1].line([(cx, H * .44), (cx, H * .66)], fill=60, width=6)
    rgb[:] = rgb * (1 - _down(shade[0], w, h)[..., None] * .35)
    rgb[_down(ink[0], w, h) > .4] = np.array([44, 18, 24])
    rgb[_down(tears[0], w, h) > .32] = np.array([214, 22, 64])


def head(f):
    b = 'head'
    f.box(b, (-1.65, 30.95, -2.05), (3.3, 4.6, 4.15), 'umbra', label='skull', folds=1)
    f.box(b, (-1.75, 31.1, FACE_Z), (3.5, 4.4, .58), 'mask', label='mask', face=paint_mask)
    # The mask curves back past the cheekbones; a narrow brow carries the blood gem.
    for side, s in SIDES:
        f.cube(b, (s * 1.48, 32.6, -2.25), (.6, 2.6, .9), 'mask', rot((0, s * 28, s * 4)), label='mask_cheek')
        f.cube(b, (s * .62, EYE_Y + .02, FACE_Z - .06), (1.05, .2, .06), 'eye', rot((0, 0, s * 9)), label='eye_slit')
        f.taper(b, [(s * .66, EYE_Y - .2, FACE_Z - .07), (s * .74, 32.4, FACE_Z - .08), (s * .68, 31.15, FACE_Z - .02)],
                [.18, .12], [.06, .06], 'glow', z_hint=(0, 0, -1), label='tear')
    f.cube(b, (0, 34.75, FACE_Z - .06), (2.6, .3, .26), 'mask', rot((-6, 0, 0)), label='mask_brow')
    f.cube(b, (0, 35.2, FACE_Z - .05), (.56, .56, .26), 'gem', rot((0, 0, 45)), label='brow_gem')
    f.cube(b, (0, 35.2, FACE_Z + .03), (.86, .86, .2), 'gold', rot((0, 0, 45)), label='brow_setting')
    j = 'jaw'
    f.box(j, (-1.45, 30.6, -2.38), (2.9, .7, 2.3), 'mask', label='mask_chin')
    f.box(b, (-.75, 29.3, -.75), (1.5, 1.8, 1.5), 'umbra', label='neck')


def cowl(f):
    """Crimson assassin's cowl: a beaked brow over the mask, a crown that rounds off behind
    the head, a mantle over the shoulders and a long point streaming back."""
    b = 'head'
    # Side profile (y, z): beak over the brow, crown, rounded back, down to the nape. The
    # sides lean in toward the crown so the cowl narrows to a soft point from the front.
    profile = [(30.9, -2.4), (35.55, -2.55), (36.25, -3.3), (37.0, -1.7), (37.25, .3), (36.85, 2.2), (35.65, 3.25),
               (33.4, 3.45), (30.7, 2.85)]

    def side_x(y):
        return 2.22 - (y - 31) * .06
    for side, s in SIDES:
        f.sheet(b, [(s * side_x(y), y, z) for y, z in profile], 'silk', thickness=.5, up=(0, 1, 0), facing=(s, 0, 0),
                label='hood_side', folds=2)
        f.taper(b, [(s * side_x(30.95), 30.95, -2.55), (s * side_x(35.6), 35.6, -2.7), (s * (side_x(36.2) - .05), 36.2, -3.4)],
                [.24, .24], [.5, .5], 'gold', z_hint=(0, 0, -1), label='hood_trim')
    # The crown follows the profile's top edge a half-thickness inside it, then runs down the back.
    crown = [(0, 36.0, -3.25), (0, 36.74, -1.68), (0, 37.0, .28), (0, 36.62, 2.05), (0, 35.45, 3.0)]
    f.taper(b, crown, [4.0, 3.95, 3.95, 4.05], [.5, .5, .5, .5], 'silk', z_hint=(0, 1, 0), label='hood_crown', folds=2)
    f.taper(b, [(0, 35.7, 2.95), (0, 33.4, 3.2), (0, 30.8, 2.6)], [4.25, 4.4], [.5, .5], 'silk', z_hint=(0, 0, 1),
            label='hood_back', folds=2)
    f.taper(b, [(0, 36.3, -3.5), (0, 37.0, -1.85), (0, 37.27, .28), (0, 36.88, 2.15)], [.24, .24, .24], [.16, .16, .16], 'gold',
            z_hint=(0, 1, 0), label='hood_seam')
    f.beam(b, (-1.85, 36.03, -3.52), (1.85, 36.03, -3.52), .24, .3, 'gold', label='hood_trim')
    # A short mantle from the cowl over the shoulders and the long trailing point.
    f.sheet(b, [(-2.5, 31.6, 2.0), (2.5, 31.6, 2.0), (3.6, 28.6, 3.0), (0, 27.5, 3.9), (-3.6, 28.6, 3.0)], 'veil', thickness=.22,
            up=(0, 1, 0), facing=(0, .5, 1), label='hood_mantle', folds=3, edge={2: ('scallop', .04, 2), 3: ('scallop', .04, 2)},
            grade=(.3, .42))
    f.sheet(b, [(-1.8, 36.75, 1.9), (1.8, 36.75, 1.9), (1.0, 35.1, 4.7), (0, 33.3, 7.4), (-1.0, 35.1, 4.7)], 'veil', thickness=.2,
            up=(0, 1, 0), facing=(0, 1, .6), label='hood_point', folds=2, grade=(.32, .44))


def horns(f):
    """Ruby horns breaking out of the cowl at the temples and sweeping up and back."""
    b = 'head'
    for side, s in SIDES:
        pts = [(s * 2.1, 34.8, -1.5), (s * 2.75, 35.5, .1), (s * 3.1, 36.2, 2.0), (s * 3.05, 36.8, 4.1), (s * 2.7, 37.2, 6.1),
               (s * 2.2, 37.35, 7.7)]
        f.taper(b, pts, [.86, .74, .6, .44, .28], [.8, .68, .54, .4, .26], 'horn', z_hint=(0, 1, 0), label='horn')
        f.beam(b, (s * 2.12, 34.82, -1.48), (s * 2.42, 35.18, -.68), .7, .7, 'gold', label='horn_ring')
        # A thin glowing vein along the upper curve of each horn.
        f.taper(b, [(s * 2.8, 35.95, .3), (s * 3.15, 36.6, 2.1), (s * 3.1, 37.2, 4.1), (s * 2.75, 37.6, 6.0)], [.13, .11, .09],
                [.12, .1, .08], 'glow', z_hint=(0, 1, 0), label='horn_vein')


# ---------------------------------------------------------------------------
# Scarf, torso, soul core and coat


def scarf(f):
    c = 'collar'
    f.ring(c, (0, 29.75, .25), 1.55, .95, .8, 'silk', segments=10, normal=(0, 1, 0), folds=1)
    f.ring(c, (0, 29.0, .35), 1.8, .7, .6, 'silk', segments=10, normal=(0, 1, 0), phase=.3, folds=1)
    f.cube(c, (0, 29.4, 2.25), (1.4, 1.1, .8), 'silk', label='scarf_knot', folds=1)
    for chain, s in zip(SCARF, (-1, 1)):
        tops = [np.array(f.at(chain[0])), np.array(f.at(chain[1])), np.array(f.at(chain[2]))]
        ends = tops[1:] + [tops[2] + np.array([s * .25, -4.6, .85])]
        for k, (bone, top, end) in enumerate(zip(chain, tops, ends)):
            w0, w1 = (.62, .58, .54)[k], (.58, .54, .46)[k]
            tip = k == 2
            pts = [top + (-w0, .12, 0), top + (w0, .12, 0), end + (w1, -.1, 0), end + (0, -.75 if tip else -.1, .1), end + (-w1, -.1, 0)]
            f.sheet(bone, pts, 'veil', thickness=.16, up=(0, 1, 0), facing=(0, .2, 1), label='scarf_tail', folds=2,
                    edge={2: ('tatter', .06, 2), 3: ('tatter', .06, 2)} if tip else {}, fade=.45 if tip else 0,
                    grade=(.34, .46), hem=.05 if tip else None)


def torso(f):
    c = 'chest'
    tiers = ((21.2, 23.4, 4.1, 3.0, .45), (23.3, 25.6, 5.2, 3.5, .4), (25.5, 27.8, 6.6, 3.9, .35), (27.7, 29.8, 7.1, 3.6, .45))
    for y0, y1, w, d, zc in tiers:
        f.box(c, (-w / 2, y0, zc - d / 2), (w, y1 - y0, d), 'umbra', label='bodice', folds=2)
    # Layered crimson cuirass: two angled breast plates over a narrow gorget.
    for side, s in SIDES:
        f.cube(c, (s * 1.65, 27.35, -1.55), (3.2, 3.0, .62), 'plate', rot((-8, -s * 18, -s * 6)), label='breastplate', engrave='lines')
        f.beam(c, (s * .25, 25.95, -1.95), (s * 3.05, 28.65, -1.45), .24, .24, 'gold', label='breast_rim')
        # Rib lames curving from the sternum, the ribs of the original wraith reborn in lacquer.
        for k in range(3):
            y = 24.75 - k * 1.2
            f.beam(c, (s * .45, y, -1.96 + k * .06), (s * 2.55, y - .55, -1.55 + k * .05), .5, .42, 'plate', z_hint=(0, 0, -1),
                   label='rib_lame', plain=True)
            f.beam(c, (s * .5, y - .26, -2.08 + k * .06), (s * 2.5, y - .8, -1.66 + k * .05), .12, .12, 'glow', label='rib_glow')
    f.box(c, (-.75, 22.1, -2.05), (1.5, 6.6, .5), 'plate', label='sternum', plain=True)
    f.box(c, (-1.25, 28.9, -1.85), (2.5, 1.2, 3.2), 'plate', label='gorget')
    # Ruby soul core on the sternum, set in a dark bezel with gold rays.
    core = np.array([0, 25.95, -2.38])
    f.ring(c, core + (0, 0, .05), 1.05, .28, .3, 'umbra', segments=10, normal=(0, 0, 1))
    f.cube(c, core, (1.15, 1.15, .55), 'gem', rot((0, 0, 45)), label='soul_core')
    f.cube(c, core + (0, 0, -.12), (.62, .62, .4), 'glow', rot((0, 0, 45)), label='soul_core_glow')
    for k in range(4):
        a = math.tau * k / 4 + math.pi / 4
        d = np.array([math.cos(a), math.sin(a), 0])
        f.beam(c, core + d * 1.05, core + d * 1.75 + (0, 0, .12), .14, .14, 'gold', label='core_ray')
    # Back: a slim spine plate with swept dorsal fins.
    f.box(c, (-2.6, 22.0, 1.75), (5.2, 7.4, .55), 'plate', label='backplate', engrave='lines')
    for k, y in enumerate((23.0, 25.2, 27.4)):
        f.spike(c, (0, y, 2.2), (0, y + 1.4, 4.0 - k * .3), .55, 'horn', steps=2, label='dorsal_fin')
    # Waist: a wrapped crimson sash with a side knot.
    f.box('pelvis', (-2.1, 18.55, -1.6), (4.2, 1.2, 3.6), 'silk', label='waist_sash', folds=2)
    f.box('pelvis', (-2.15, 18.45, -1.65), (4.3, .22, 3.7), 'gold', label='waist_sash_rim')
    f.cube('pelvis', (2.0, 18.9, -1.4), (1.0, 1.1, .7), 'silk', rot((0, -30, 10)), label='sash_knot', folds=1)
    f.box('pelvis', (-2.2, 15.4, -1.45), (4.4, 3.3, 3.3), 'umbra', label='hips', folds=2)
    f.box('pelvis', (-1.0, 16.2, -1.85), (2.0, 2.0, .5), 'plate', label='fauld', plain=True)


def coat(f):
    """Split coat tails from the small of the back, fraying into crimson light."""
    for bone, top, bottom, z0, z1, frayed in (('cape', 19.3, 11.2, 2.3, 3.4, False), ('cape_tip', 11.4, 3.4, 3.4, 4.6, True)):
        for s in (-1, 1):
            a, b = s * .25, s * 2.5
            w0 = (2.3, 2.7)[frayed]
            pts = [(a, top, z0), (s * w0, top, z0 - .2), (s * (w0 + .5), bottom, z1 - .2), (a * 1.6, bottom - (1.4 if frayed else 0), z1)]
            f.sheet(bone, pts, 'veil', thickness=.18, up=(0, 1, 0), facing=(0, 0, 1), label='coat_tail', folds=2,
                    edge={2: ('tatter', .05, 2)} if frayed else {}, fade=.42 if frayed else 0, grade=(.3, .42),
                    hem=.05 if frayed else None)


def tassets(f):
    """Two silk tassets over the thighs with a gold hem and a frayed point."""
    for side, s in SIDES:
        b = f'sash_{side}'
        o = np.array(f.at(b))
        pts = [o + (-1.0, .2, -.05), o + (1.0, .2, -.05), o + (1.05, -7.4, -.35), o + (0, -8.8, -.4), o + (-1.05, -7.4, -.35)]
        f.sheet(b, pts, 'veil', thickness=.18, up=(0, 1, 0), facing=(0, 0, -1), label='tasset', folds=2,
                edge={2: ('tatter', .05, 1), 3: ('tatter', .05, 1)}, fade=.3, grade=(.32, .44), hem=.06)
        f.cube(b, o + (0, -.2, -.25), (2.2, .5, .32), 'gold', label='tasset_clasp')


# ---------------------------------------------------------------------------
# Shoulders, arms and the Sorrow-Quills


def shoulders(f):
    for side, s in SIDES:
        b = f'shoulder_{side}'
        o = J[f'shoulder_{side}']
        big = side == 'r'
        for k in range(3 if big else 2):
            y = 29.4 - k * 1.05
            f.cube(b, o + (s * (.55 + k * .35), y - o[1], 0), ((2.6 if big else 2.2) - k * .3, .62, 3.0 - k * .25), 'plate',
                   rot((0, 0, -s * (26 + k * 10))), label='pauldron', plain=k > 0)
            f.cube(b, o + (s * (.7 + k * .35), y - o[1] - .32, 0), ((2.62 if big else 2.22) - k * .3, .18, 3.02 - k * .25), 'gold',
                   rot((0, 0, -s * (26 + k * 10))), label='pauldron_rim')
        if big:
            f.spike(b, o + (s * .9, 1.35, .6), o + (s * 2.8, 3.1, 2.7), .62, 'horn', steps=3, label='pauldron_spur')


def arms(f):
    for side, s in SIDES:
        sh, el, wr = J[f'shoulder_{side}'], J[f'elbow_{side}'], J[f'wrist_{side}']
        a, fa, h = f'arm_{side}', f'forearm_{side}', f'hand_{side}'
        f.beam(a, sh + (0, -.3, 0), el + (0, .35, 0), 1.45, 1.5, 'umbra', label='upper_arm', folds=1)
        f.beam(a, sh + (s * .55, -1.4, 0), el + (s * .55, 1.0, .1), .5, 1.35, 'plate', label='arm_plate', plain=True)
        f.cube(a, el + (0, .2, .35), (1.55, 1.1, 1.5), 'plate', label='couter', plain=True)
        # Elbow blade sweeping back: a duelist's parrying fin.
        f.spike(a, el + (s * .2, .4, .9), el + (s * .9, 2.6, 3.4), .5, 'horn', steps=3, label='elbow_fin')
        f.beam(fa, el + (0, -.25, 0), wr + (0, .25, 0), 1.2, 1.25, 'wrap', label='forearm')
        # Vambrace and a long blade-fin along its outside edge.
        f.beam(fa, el + (s * .1, -.9, -.1), wr + (s * .05, .55, -.15), 1.48, 1.52, 'plate', label='vambrace', engrave='lines')
        f.beam(fa, el + (s * .15, -.95, -.12), el + (s * .15, -1.2, -.12), 1.6, 1.64, 'gold', label='vambrace_rim')
        f.taper(fa, [wr + (s * .85, 1.0, .3), el + (s * 1.05, -1.0, .6), el + (s * 1.25, .9, 1.4)], [.36, .22], [.9, .5],
                'blade', z_hint=(s, 0, 0), label='arm_blade')
        f.box(h, (wr[0] - .62, wr[1] - 1.75, wr[2] - .75), (1.24, 1.9, 1.4), 'wrap', label='fist')
        f.cube(h, wr + (0, -.95, -.78), (1.32, .82, .3), 'plate', label='knuckles', plain=True)


def sorrow_quill(f, side, s):
    """Curved ruby saber in a forward grip: obsidian grip, crescent guard, glowing fuller."""
    b = f'weapon_{side}'
    g = J[f'grip_{side}']
    f.beam(b, g + (0, 1.35, 0), g + (0, -1.1, 0), .5, .5, 'wrap', label='quill_grip')
    f.cube(b, g + (0, 1.62, 0), (.6, .6, .6), 'gem', rot((45, 0, 45)), label='quill_pommel')
    f.cube(b, g + (0, 1.62, 0), (.82, .38, .82), 'gold', rot((0, 45, 0)), label='quill_pommel_ring')
    # Crescent guard cupping forward over the fist.
    guard = [g + (0, -1.15, .95), g + (0, -1.45, .2), g + (0, -1.35, -.65), g + (0, -.85, -1.35)]
    f.taper(b, guard, [.42, .46, .38], [.62, .62, .5], 'gold', z_hint=(1, 0, 0), label='quill_guard')
    # The blade: a curved spine with an alpha-cut ruby body that sweeps forward to its point.
    spine = [g + (0, -1.4, .45), g + (0, -5.6, .55), g + (0, -9.6, .05), g + (0, -12.6, -1.15), g + (0, -14.2, -2.35)]
    f.taper(b, spine, [.34, .3, .26, .2], [.36, .32, .28, .2], 'horn', z_hint=(1, 0, 0), label='quill_spine')
    edge = [g + (0, -1.5, -.75), g + (0, -5.4, -.95), g + (0, -9.2, -1.45), g + (0, -12.0, -2.15), g + (0, -14.25, -2.45)]
    outline = [tuple(p) for p in spine] + [tuple(p) for p in edge[::-1]]
    f.sheet(b, outline, 'blade', thickness=.24, up=(0, 1, 0), facing=(s, 0, 0), label='quill_blade')
    fuller = [g + (0, -2.0, -.12), g + (0, -5.5, -.2), g + (0, -9.2, -.65), g + (0, -11.6, -1.3)]
    f.taper(b, fuller, [.16, .14, .1], [.3, .3, .28], 'glow', z_hint=(1, 0, 0), label='quill_fuller')


def weapons(f):
    for side, s in SIDES:
        sorrow_quill(f, side, s)


# ---------------------------------------------------------------------------
# Legs


def legs(f):
    for side, s in SIDES:
        hp, kn, an = J[f'hip_{side}'], J[f'knee_{side}'], J[f'ankle_{side}']
        l, sh, ft = f'leg_{side}', f'shin_{side}', f'foot_{side}'
        f.beam(l, hp + (0, .3, 0), kn + (0, .2, 0), 1.75, 1.85, 'umbra', label='thigh', folds=1)
        f.beam(l, hp + (s * .1, -1.6, -.75), kn + (0, 1.4, -.8), 1.55, .5, 'plate', z_hint=(0, 0, -1), label='cuisse', plain=True)
        # Wrapped thigh band with gold chevrons, a nod to the first Acheron's leg wraps.
        f.beam(l, hp + (0, -2.4, 0), hp + (0, -3.5, 0), 2.0, 2.08, 'silk', label='thigh_wrap', folds=1)
        f.beam(l, hp + (0, -3.45, 0), hp + (0, -3.62, 0), 2.04, 2.12, 'gold', label='thigh_wrap_rim')
        f.cube(sh, kn + (0, .1, -.75), (1.6, 1.5, .9), 'plate', rot((-14, 0, 0)), label='poleyn', plain=True)
        f.spike(sh, kn + (0, .2, -1.15), kn + (0, .8, -2.35), .55, 'horn', steps=2, label='knee_spur')
        f.beam(sh, kn + (0, -.3, 0), an + (0, .4, 0), 1.4, 1.5, 'wrap', label='shin')
        f.beam(sh, kn + (0, -.8, -.6), an + (0, 1.1, -.55), 1.45, .55, 'plate', z_hint=(0, 0, -1), label='greave', engrave='lines')
        f.beam(sh, kn + (0, -.8, -.92), an + (0, 1.1, -.88), .3, .2, 'gold', label='greave_ridge')
        # Pointed sabaton with a heel spur.
        f.box(ft, (an[0] - .72, 0, an[2] - 2.6), (1.44, 1.45, 3.6), 'plate', label='sabaton', plain=True)
        f.spike(ft, (an[0], .55, an[2] - 2.55), (an[0], .35, an[2] - 4.5), .95, 'plate', steps=3, depth_ratio=.62, label='toe')
        f.spike(ft, (an[0], 1.2, an[2] + .95), (an[0], 1.5, an[2] + 2.3), .4, 'horn', steps=2, label='heel_spur')
        f.beam(ft, (an[0], 1.5, an[2] - 2.5), (an[0], 1.55, an[2] + .9), .22, .2, 'gold', label='sabaton_ridge')


def shrink(f, first, pivot, k):
    """Scale the parts made since `first` about `pivot`: a smaller head reads taller and quicker."""
    pivot = np.asarray(pivot, float)
    for part in f.parts[first:]:
        part['center'] = pivot + (part['center'] - pivot) * k
        part['size'] = part['size'] * k


def sculpt():
    f = Forge(MODEL, MATERIALS, seed=4127)
    skeleton(f)
    first = len(f.parts)
    head(f)
    cowl(f)
    horns(f)
    shrink(f, first, J['head'], HEAD_SCALE)
    scarf(f)
    torso(f)
    coat(f)
    tassets(f)
    shoulders(f)
    arms(f)
    weapons(f)
    legs(f)
    return f


# ---------------------------------------------------------------------------
# Echo: 60% scale, no halo, same UVs, an afterimage recolour of the atlas


def afterimage(atlas):
    """Shift the body toward a cool crimson-violet afterimage, still readable in a dark arena;
    keep the glows bright."""
    a = np.asarray(atlas.convert('RGBA'), float)
    rgb, alpha = a[..., :3], a[..., 3:]
    lum = rgb @ np.array([.299, .587, .114])
    hot = np.clip((rgb.max(axis=-1) - 170) / 60, 0, 1)[..., None]
    shadow = np.stack([lum * .85 + 34, lum * .3 + 10, lum * .62 + 26], axis=-1)
    out = shadow * (1 - hot) + np.minimum(rgb * 1.05 + (10, 0, 14), 255) * hot
    return Image.fromarray(np.concatenate([np.clip(out, 0, 255), alpha], axis=-1).astype(np.uint8), 'RGBA')


def echo_of(model):
    echo = copy.deepcopy(model)
    echo['name'] = echo['model_identifier'] = ECHO
    removed_elements, removed_bones = set(), set()

    def discard(group):
        removed_bones.add(group['uuid'])
        for child in group['children']:
            if isinstance(child, dict):
                discard(child)
            else:
                removed_elements.add(child)

    def walk(group):
        group['origin'] = [round(v * ECHO_SCALE, 8) for v in group['origin']]
        kept = []
        for child in group['children']:
            if isinstance(child, dict):
                if child['name'] == 'halo':
                    discard(child)
                    continue
                walk(child)
            kept.append(child)
        group['children'] = kept
    for group in echo['outliner']:
        walk(group)
    echo['elements'] = [e for e in echo['elements'] if e['uuid'] not in removed_elements]
    for cube in echo['elements']:
        for field in ('from', 'to', 'origin'):
            if field in cube:
                cube[field] = [round(v * ECHO_SCALE, 8) for v in cube[field]]
    for animation in echo['animations']:
        for bone in removed_bones:
            animation['animators'].pop(bone, None)
        for animator in animation['animators'].values():
            for frame in animator['keyframes']:
                if frame['channel'] == 'position':
                    for point in frame['data_points']:
                        for axis in 'xyz':
                            point[axis] = f'{float(point[axis]) * ECHO_SCALE:.4f}'
    source = echo['textures'][0]['source'].split(',', 1)[1]
    atlas = afterimage(Image.open(io.BytesIO(base64.b64decode(source))))
    buffer = io.BytesIO()
    atlas.save(buffer, format='PNG', optimize=True)
    echo['textures'][0]['source'] = 'data:image/png;base64,' + base64.b64encode(buffer.getvalue()).decode()
    echo['meta']['evergarden_echo_scale'] = ECHO_SCALE
    return echo, atlas


def tick_turns(model, max_step=45.0, tick=.05):
    """ModelEngine blends rotation keys along the shortest arc, so a simplified 0 -> 1080 degree
    spin would play as no turn at all. Split any rotation key gap wider than `max_step`
    into tick-aligned keys; values are linear in Euler, which the export already matched."""
    for animation in model['animations']:
        for animator in animation['animators'].values():
            frames = [k for k in animator['keyframes'] if k['channel'] == 'rotation']
            frames.sort(key=lambda k: k['time'])
            added = []
            for a, b in zip(frames, frames[1:]):
                va = np.array([float(a['data_points'][0][x]) for x in 'xyz'])
                vb = np.array([float(b['data_points'][0][x]) for x in 'xyz'])
                steps = int(math.ceil(np.max(np.abs(vb - va)) / max_step - 1e-9))
                if steps < 2:
                    continue
                count = max(steps, int(round((b['time'] - a['time']) / tick)))
                for i in range(1, count):
                    u = i / count
                    when = round(a['time'] + (b['time'] - a['time']) * u, 4)
                    v = va + (vb - va) * u
                    name = f"{animation['name']}/{animator['name']}/rotation/{when}"
                    added.append(dict(channel='rotation', data_points=[dict(x=f'{v[0]:.4f}', y=f'{v[1]:.4f}', z=f'{v[2]:.4f}')],
                                      uuid=str(uuid.uuid5(uuid.NAMESPACE_URL, f'evergarden/{MODEL}/' + name)), time=when,
                                      color=-1, interpolation='linear'))
            if added:
                animator['keyframes'] = sorted(animator['keyframes'] + added, key=lambda k: (k['channel'], k['time']))


def build():
    from acheron_bladedancer_motion import clips
    f = sculpt()
    motion, errors = clips(f)
    model, atlas, stats = build_model(f, motion, MODEL, 'acheron', [[-5, 0, -3], [5, 36, 3]],
                                      meta=dict(evergarden_revision=REVISION, evergarden_scale=SCALE), density=9.0)
    tick_turns(model)
    echo, echo_atlas = echo_of(model)
    return f, model, echo, atlas, echo_atlas, stats, max(errors, default=0.0)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, default=OUT, help='Folder receiving both blueprints and the manifest')
    args = parser.parse_args()
    f, model, echo, atlas, echo_atlas, stats, worst = build()
    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / f'{MODEL}.bbmodel').write_text(dump(model) + '\n', encoding='utf8')
    (args.out / f'{ECHO}.bbmodel').write_text(dump(echo) + '\n', encoding='utf8')
    art = ROOT / 'evergarden/art/arkhaia-rivers'
    art.mkdir(parents=True, exist_ok=True)
    atlas.save(art / 'acheron_bladedancer.png')
    echo_atlas.save(art / 'acheron_bladedancer_echo.png')
    manifest = dict(revision=REVISION, cubes=len(model['elements']) - 1, bones=len(f.bones), scale=SCALE, echo_scale=ECHO_SCALE,
                    texture=dict(size=model['resolution']['width'], density=round(stats['density'], 2), tiles=stats['tiles'],
                                 coverage=round(stats['coverage'], 3)),
                    animations={a['name']: a['length'] for a in model['animations']}, worst_ik_reach_error=round(worst, 3))
    (args.out / f'{MODEL}-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf8')
    print(f"Acheron: {manifest['cubes']} cubes, {manifest['bones']} bones, {len(model['animations'])} clips, "
          f"atlas {stats['coverage']:.0%} at {stats['density']:.1f}px/unit, IK error {worst:.2f}")


if __name__ == '__main__':
    main()
