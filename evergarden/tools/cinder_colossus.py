"""The Cinder Colossus, guardian of the Crown Fortress in the Nether.

A living fortress. Every limb is a cluster of hewn blackstone blocks over a molten
core, so lava glows through each gap; brick masonry marks its architecture (crown
band, rampart walk, belt). A twelve-pronged molten crown (the fortress's twelve-point
crown) rises over a visor pierced by a burning cross; bastion shoulders carry merlons
and a broken watchtower spire; shackled fists trail broken chains; a burnt war banner
hangs from the iron belt. Its rose-red heartcrystal is sealed under two back plates
that swing open when it kneels: the weak point. Rubble orbits it and the floor splits
into magma beneath; those aura bones loop on their own lane.
"""
import math
import random

import numpy as np

from boss_forge import Clip, Forge, Material, Poser, Track, axis_angle, basis, euler, rot, unit
from realm_forge import CLIPS, build_realm, chunked as _chunked


def chunked(*args, **kw):
    kw.setdefault('core', 'ember')
    kw.setdefault('gap', .32)
    if 'heat' in kw:
        kw['heat'] = kw['heat'] * .6
    return _chunked(*args, **kw)

MODEL = 'cinder_colossus'
TITLE = 'Cinder Colossus'
REVISION = 'cinder-colossus-v1'
SCALE = 3.6

STONE = ['#140f12', '#1f171b', '#2b2024', '#3a2c30', '#4b3a3d', '#5f4a4c', '#78605f', '#957a74']
LAVA = ['#4a0a03', '#861a05', '#c03207', '#ec5a0e', '#ff8f1f', '#ffc240', '#ffe98f', '#fffbe2']
IRON = ['#121014', '#1d1a20', '#2b272f', '#3d3743', '#524a59', '#6c6273', '#8d8294']
GOLD = ['#3a2208', '#5e3a0e', '#8a5a18', '#b8812a', '#dcaa45', '#f2d27a', '#fff0b8']
BANNER = ['#22050a', '#3c0a10', '#5c1018', '#7e1820', '#a02429', '#bd3a36']
HEART = ['#3d0619', '#6e0c2c', '#a8163f', '#d92e57', '#ff5f7f', '#ff9fb4', '#ffe1e9']
MATERIALS = {
    'rock': Material(STONE, 'rock', accent=LAVA),
    'brick': Material(STONE, 'brick', accent=LAVA, density=.9),
    'lava': Material(LAVA, 'lava', glow=True, density=.7),
    'magma': Material(LAVA[2:], 'glow', glow=True, density=.45),
    'ember': Material(['#3a0702', '#6a1204', '#9c2306', '#cc3f0a', '#f06614', '#ff9326'], 'glow', glow=True, density=.4),
    'iron': Material(IRON, 'trim', density=.9),
    'chain': Material(IRON, 'chain', density=1.6),
    'gold': Material(GOLD, 'trim', density=1.2),
    'banner': Material(BANNER, 'banner', trim=GOLD, accent=LAVA, density=1.1),
    'crystal': Material(HEART, 'crystal', glow=True, density=1.3),
    'eye': Material(['#ff9a2a', '#ffd36a', '#fff3c4', '#ffffff'], 'glow', glow=True, density=1.6),
}
SIDES = (('r', 1), ('l', -1))
AURA = ('debris_near', 'debris_far', 'magma')
MARKERS = ('root', 'facing', 'head_forward', 'fx_core', 'fx_eye', 'fx_crown', 'fx_mouth', 'fx_fist_r', 'fx_fist_l')
J = {k: np.array(v, float) for k, v in dict(
    pelvis=(0, 22, 1), chest=(0, 25, 1), neck=(0, 43.5, -4.0),
    shoulder_r=(15.6, 39.6, 1.2), elbow_r=(20.2, 28.2, .4), wrist_r=(21.2, 17.4, -.8),
    shoulder_l=(-15.6, 39.6, 1.2), elbow_l=(-20.2, 28.2, .4), wrist_l=(-21.2, 17.4, -.8),
    hip_r=(7.2, 20.6, 1.2), knee_r=(8.0, 11.8, -.6), ankle_r=(8.4, 3.6, .6),
    hip_l=(-7.2, 20.6, 1.2), knee_l=(-8.0, 11.8, -.6), ankle_l=(-8.4, 3.6, .6)).items()}
CHAIN = {s: np.array([s * 27.0, 19.6, 3.0]) for _, s in SIDES}
CORE = np.array([0, 34.0, 8.0])
FIST = {s: J['wrist_r' if s > 0 else 'wrist_l'] + np.array([s * .3, -6.6, -1.0]) for _, s in SIDES}
PREVIEW = dict(close=(30, (0, 47)))


def skeleton(f):
    f.bone('root', None, [0, 0, 0])
    f.bone('facing', 'root', [0, 22, -24])
    f.bone('pelvis', 'root', J['pelvis'])
    f.bone('chest', 'pelvis', J['chest'])
    f.bone('hi_gaze', 'chest', J['neck'])
    f.bone('head', 'hi_gaze', J['neck'])
    f.bone('head_forward', 'head', [0, 43.5, -16])
    f.bone('jaw', 'head', [0, 43.4, -3.0])
    f.bone('core', 'chest', CORE)
    for side, s in SIDES:
        f.bone(f'shoulder_{side}', 'chest', [s * 15.0, 41.0, 1.5])
        f.bone(f'plate_{side}', 'chest', [s * 12.8, 34.5, 9.6])
        f.bone(f'arm_{side}', 'chest', J[f'shoulder_{side}'])
        f.bone(f'forearm_{side}', f'arm_{side}', J[f'elbow_{side}'])
        f.bone(f'hand_{side}', f'forearm_{side}', J[f'wrist_{side}'])
        f.bone(f'chain_{side}', f'forearm_{side}', CHAIN[s])
    f.bone('banner', 'pelvis', [0, 22.2, -7.2])
    f.bone('banner_tip', 'banner', [0, 12.8, -7.8])
    for side, s in SIDES:
        f.bone(f'leg_{side}', 'pelvis', J[f'hip_{side}'])
        f.bone(f'shin_{side}', f'leg_{side}', J[f'knee_{side}'])
        f.bone(f'foot_{side}', f'shin_{side}', J[f'ankle_{side}'])
    f.bone('debris_near', 'root', [0, 24, 0])
    f.bone('debris_far', 'root', [0, 26, 0])
    f.bone('magma', 'root', [0, .1, 0])
    f.bone('fx_core', 'core', CORE + (0, 0, 3.0))
    f.bone('fx_eye', 'head', [0, 48.4, -11.2])
    f.bone('fx_crown', 'head', [0, 56.0, -5.2])
    f.bone('fx_mouth', 'jaw', [0, 43.0, -10.2])
    for side, s in SIDES:
        f.bone(f'fx_fist_{side}', f'hand_{side}', FIST[s] + (0, -5.0, -.5))


# ---------------------------------------------------------------------------
# Head: visor, jaw and the twelve-pronged crown


def head(f, rng):
    b = 'head'
    chunked(f, b, (0, 47.4, -5.4), (11.2, 8.6, 10.6), np.eye(3), rng, div=(2, 2, 2), gap=.36, jitter=.25, push=.18,
            label='skull', heat=.1)
    f.cube(b, (0, 51.7, -9.7), (13.4, 2.3, 4.6), 'rock', rot((-14, 0, 0)), label='brow', heat=.12)
    for side, s in SIDES:
        f.cube(b, (s * 6.2, 47.2, -8.0), (2.3, 7.8, 6.2), 'rock', rot((0, -s * 18, s * 5)), label='cheek', heat=.12)
        f.taper(b, [(s * 6.6, 49.8, -6.8), (s * 9.6, 51.0, -6.0), (s * 12.2, 53.2, -5.0), (s * 13.6, 56.4, -4.2)], [2.4, 1.6, .8],
                [2.4, 1.6, .8], 'rock', z_hint=(0, 0, 1), label='cheek_horn', heat=.08, overlap=.25)
    # Visor: a dark recess pierced by a burning cross.
    f.box(b, (-4.3, 43.9, -11.1), (8.6, 6.6, .55), 'iron', label='visor')
    f.box(b, (-.82, 44.0, -11.45), (1.64, 6.3, .5), 'eye', label='visor_cross')
    f.box(b, (-3.9, 47.9, -11.45), (7.8, 1.45, .5), 'eye', label='visor_bar')
    # Crown: brick band, gilded rim, molten pool and twelve flared prongs, tallest over the brow.
    f.box(b, (-6.1, 51.3, -11.3), (12.2, 2.1, 12.2), 'brick', label='crown_band', heat=.45, brick=(1.4, .75))
    f.box(b, (-6.15, 52.6, -11.35), (12.3, .55, 12.3), 'gold', label='crown_trim')
    f.box(b, (-5.0, 52.9, -10.2), (10.0, .5, 10.0), 'lava', label='crown_pool')
    center = np.array([0, 53.0, -5.2])
    for k in range(12):
        a = math.tau * k / 12
        d = np.array([math.sin(a), 0, -math.cos(a)])
        front = (1 + math.cos(a)) / 2
        major = k % 3 == 0
        height = 4.0 + 6.0 * front ** 1.3 + (3.0 if major else 0)
        flare = 1.2 + front * 1.6
        base = center + d * 5.5
        pts = [base, base + d * flare * .3 + (0, height * .3, 0), base + d * flare * .65 + (0, height * .62, 0),
               base + d * flare + (0, height, 0)]
        w0 = 2.9 if major else 2.1
        f.taper(b, pts, [w0, w0 * .62, w0 * .26], [w0 * .8, w0 * .5, w0 * .22], 'rock', z_hint=d, label='crown_prong', heat=.06,
                overlap=.3)
        f.beam(b, base + d * w0 * .43 + (0, .4, 0), pts[2] + d * w0 * .26, .42 if major else .3, .24, 'magma', label='crown_seam')
        if major:
            f.cube(b, base + d * 1.3 + (0, .9, 0), (1.0, 1.0, .4), 'eye', basis(d, (0, 1, 0)) @ rot((0, 0, 45)), label='crown_gem')


def jaw(f):
    b = 'jaw'
    f.box(b, (-4.9, 40.2, -10.7), (9.8, 3.4, 8.8), 'rock', label='jaw', heat=.12)
    f.box(b, (-3.8, 42.7, -10.0), (7.6, 1.2, 6.0), 'magma', label='maw')
    for k in range(6):
        x = -3.1 + k * 1.24
        f.spike(b, (x, 43.3, -10.2), (x, 45.0, -10.55), .72, 'rock', steps=2, label='tooth')
    for side, s in SIDES:
        f.spike(b, (s * 4.2, 42.2, -10.4), (s * 5.5, 46.2, -12.2), 1.4, 'rock', steps=3, label='tusk', heat=.06)


# ---------------------------------------------------------------------------
# Torso: stacked block courses over a molten core, rampart chest, furnace, plates


def torso(f, rng):
    c = 'chest'
    tiers = (((0, 25.0, 1.4), (12.0, 5.0, 10.6), (2, 1, 2)), ((0, 30.4, 2.0), (19.0, 6.0, 13.6), (3, 1, 2)),
             ((0, 37.0, 2.6), (28.0, 7.6, 16.0), (3, 2, 2)))
    for i, (center, size, div) in enumerate(tiers):
        # The middle front column stays open: the furnace burns in that gap.
        chunked(f, c, center, size, np.eye(3), rng, div=div, jitter=.4, push=.3, label=f'torso{i}', heat=.14,
                skip=(lambda i_, j, k: div[0] == 3 and i_ == 1 and k == 0) if i else None)
    for side, s in SIDES:
        f.cube(c, (s * 6.4, 37.6, -6.6), (11.4, 8.6, 2.6), 'rock', rot((-7, -s * 12, -s * 3)), label='rampart', heat=.12)
        f.cube(c, (s * 7.0, 31.2, -6.2), (9.0, 4.4, 2.2), 'rock', rot((-12, -s * 14, s * 2)), label='rampart_low', heat=.12)
        f.beam(c, (s * 1.5, 42.0, -7.9), (s * 11.8, 40.9, -5.6), .6, .55, 'iron', label='rampart_trim')
    f.box(c, (-1.5, 25.6, -7.9), (3.0, 16.8, 1.6), 'magma', label='furnace')
    for side, s in SIDES:
        f.beam(c, (s * 1.95, 42.6, -7.9), (s * 1.75, 25.4, -7.5), .6, .65, 'iron', label='furnace_bar')
    for y in (28.6, 32.6, 36.6):
        f.box(c, (-2.15, y, -8.3), (4.3, .55, .55), 'iron', label='furnace_rung')
    f.sheet(c, [(-1.4, 25.7, -8.0), (1.4, 25.7, -8.0), (1.8, 23.0, -6.9), (1.4, 21.4, -6.6), (-1.3, 21.4, -6.6), (-1.8, 23.0, -6.9)],
            'lava', thickness=.2, up=(0, 1, 0), facing=(0, 0, -1), label='lavafall', stream=True)
    for side, s in SIDES:
        f.sheet(c, [(s * 11.6, 33.2, -6.2), (s * 12.7, 33.2, -6.0), (s * 12.4, 28.4, -5.4), (s * 12.0, 27.0, -5.3)],
                'lava', thickness=.16, up=(0, 1, 0), facing=(0, 0, -1), label='lavadrip', stream=True)
    # Rampart walk along the hunch, merlon by merlon.
    f.box(c, (-11.6, 40.8, 7.0), (23.2, .6, 3.6), 'iron', label='back_ledge')
    for k in range(7):
        x = -10.2 + k * 3.4
        f.box(c, (x - 1.05, 41.3, 7.4), (2.1, 2.6, 2.6), 'brick', label='back_merlon', heat=.25, brick=(1.0, .7))
    f.box(c, (-3.8, 29.8, 9.6), (7.6, 8.8, 1.6), 'magma', label='socket')


def plates(f, rng):
    """Two back plates hinged at their outer edges; they swing open over the heartcrystal."""
    for side, s in SIDES:
        b = f'plate_{side}'
        r = rot((4, s * 6, 0))
        chunked(f, b, (s * 6.6, 34.4, 11.4), (12.2, 14.0, 2.8), r, rng, div=(2, 2, 1), gap=.4, jitter=.3, push=.2,
                label='plate', heat=.25, core=None)
        f.beam(b, (s * .8, 40.9, 12.9), (s * .8, 27.9, 12.6), .55, .5, 'magma', label='plate_seam')
        f.beam(b, (s * 12.8, 40.6, 10.6), (s * 12.8, 28.2, 10.4), .8, .8, 'iron', label='hinge')
        for y in (38.4, 32.4):
            f.spike(b, (s * 8.2, y, 12.6), (s * 10.6, y + 2.4, 16.8), 1.7, 'rock', steps=3, label='plate_spike', heat=.15)


def heart(f):
    b = 'core'
    f.cube(b, CORE, (5.8, 5.8, 5.8), 'crystal', rot((45, 35, 0)), label='heart', bands=2)
    shards = (((0, .2, 1), 6.8, 2.1), ((.55, .45, .7), 5.6, 1.7), ((-.55, .45, .7), 5.6, 1.7), ((.45, -.5, .75), 5.0, 1.5),
              ((-.45, -.5, .75), 5.0, 1.5), ((0, .9, .45), 5.4, 1.6), ((.85, .05, .55), 4.4, 1.3), ((-.85, .05, .55), 4.4, 1.3),
              ((.2, -.85, .5), 4.0, 1.2))
    for d, length, width in shards:
        d = unit(d)
        base = CORE + d * 1.8
        f.spike(b, base, base + d * length * 1.35, width * 1.25, 'crystal', steps=3, label='shard', bands=2)


# ---------------------------------------------------------------------------
# Shoulders, arms, shackles, chains and fists


def pauldrons(f, rng):
    for side, s in SIDES:
        b = f'shoulder_{side}'
        top = np.array([s * 17.6, 43.0, 1.6])
        r = rot((0, 0, -s * 14))
        chunked(f, b, top, (11.6, 7.4, 13.2), r, rng, div=(2, 2, 2), jitter=.35, push=.3, label='pauldron', heat=.2)
        r2 = rot((0, 0, -s * 42))
        chunked(f, b, top + (s * 4.2, -5.2, -.2), (7.6, 5.6, 11.6), r2, rng, div=(1, 2, 2), gap=.4, jitter=.3, push=.25,
                label='pauldron_low', heat=.2)
        for k in range(4):
            f.cube(b, top + r @ np.array([-s * 1.8, 5.0, -4.5 + k * 3.0]), (2.3, 2.6, 2.0), 'brick', r, label='pauldron_merlon',
                   heat=.3, brick=(1.0, .7))
        base = top + r @ np.array([s * 3.0, 3.6, 3.6])
        f.cube(b, base + (0, .9, 0), (3.6, 1.8, 3.6), 'brick', r, label='spire_base', heat=.3, brick=(1.0, .7))
        f.taper(b, [base + (0, 1.6, 0), base + (s * 1.2, 5.2, 1.0), base + (s * 2.4, 8.4, 1.8), base + (s * 3.4, 11.6, 2.6)],
                [2.6, 1.7, .8], [2.6, 1.7, .8], 'rock', z_hint=(0, 0, 1), label='spire', heat=.3, overlap=.25)


def arms(f, rng):
    for side, s in SIDES:
        sh, el, wr = J[f'shoulder_{side}'], J[f'elbow_{side}'], J[f'wrist_{side}']
        a, fa = f'arm_{side}', f'forearm_{side}'
        r = basis(el - sh, (0, 0, 1))
        chunked(f, a, (sh + el) / 2 + (0, .4, 0), (7.8, np.linalg.norm(el - sh) + 1.4, 8.2), r, rng, div=(2, 2, 2),
                label='upper_arm', heat=.18)
        f.cube(a, el + (0, 1.4, 0), (6.0, 3.0, 6.0), 'magma', rot((0, 45, 0)), label='elbow_glow')
        r = basis(wr - el, (0, 0, 1))
        chunked(f, fa, (el + wr) / 2 + (0, .2, 0), (9.2, np.linalg.norm(wr - el) + 1.6, 9.8), r, rng, div=(2, 2, 2),
                label='forearm', heat=.2)
        f.spike(fa, el + (s * 1.2, .6, 3.8), el + (s * 2.6, -.6, 8.8), 2.3, 'rock', steps=3, label='elbow_spike', heat=.15)
        # Shackle band and the broken chain it trails.
        f.cube(fa, wr + r @ np.array([0, 2.4, 0]), (11.4, 2.2, 11.6), 'iron', r, label='shackle')
        f.cube(fa, wr + r @ np.array([0, 4.6, 0]), (10.8, .8, 11.0), 'iron', r, label='shackle_ring')
        f.cube(fa, CHAIN[s] + (0, .5, 0), (1.6, 2.2, 2.2), 'iron', label='shackle_eye')
        ch = f'chain_{side}'
        for k in range(6):
            size = (1.7, 2.8, .4) if k < 5 else (1.3, 1.9, .34)
            f.cube(ch, CHAIN[s] + (0, -1.2 - k * 2.05, 0), size, 'chain', rot((0, 90 if k % 2 else 0, 0)), label='chain_link',
                   faces=('north', 'south'))
        fist(f, side, s, rng)


def fist(f, side, s, rng):
    b = f'hand_{side}'
    c = FIST[s]
    chunked(f, b, c, (12.4, 11.2, 12.8), np.eye(3), rng, div=(2, 2, 2), jitter=.3, push=.25, label='fist', heat=.25)
    for k in range(4):
        x = c[0] - 4.6 + k * 3.07
        f.cube(b, (x, c[1] - 2.3, c[2] - 6.9), (2.7, 3.7, 3.0), 'rock', label='knuckle', heat=.1)
        f.cube(b, (x, c[1] - 6.1, c[2] - 3.0), (2.7, 2.0, 6.4), 'rock', label='finger', heat=.08)
        f.spike(b, (x, c[1] - .7, c[2] - 8.2), (x, c[1] - .1, c[2] - 10.4), 1.15, 'rock', steps=2, label='knuckle_spike', heat=.05)
    f.box(b, (c[0] - 5.8, c[1] - 4.2, c[2] - 6.7), (11.6, 2.6, .6), 'magma', label='knuckle_glow')
    f.cube(b, (c[0] - s * 6.8, c[1] + .9, c[2] - 3.4), (2.8, 5.6, 3.4), 'rock', rot((0, 0, s * 10)), label='thumb', heat=.1)
    for k in (-1, 1):
        f.spike(b, (c[0] + k * 2.8, c[1] + 4.8, c[2] + 4.2), (c[0] + k * 3.6, c[1] + 7.4, c[2] + 9.2), 2.0, 'rock', steps=3,
                label='fist_spike', heat=.08)
    f.sheet(b, [(c[0] - 1.7, c[1] - 7.6, c[2] - 7.6), (c[0] + 1.1, c[1] - 7.6, c[2] - 7.6), (c[0] + .7, c[1] - 9.8, c[2] - 7.5),
                (c[0] - 1.0, c[1] - 11.4, c[2] - 7.5)], 'lava', thickness=.14, up=(0, 1, 0), facing=(0, 0, -1), label='fist_drip',
            stream=True)


# ---------------------------------------------------------------------------
# Hips, banner and legs


def lower_body(f, rng):
    p = 'pelvis'
    chunked(f, p, (0, 20.8, 1.0), (17.4, 5.2, 11.6), np.eye(3), rng, div=(3, 1, 2), label='hips', heat=.15)
    f.box(p, (-10.2, 22.4, -5.6), (20.4, 2.0, 13.2), 'brick', label='belt', heat=.2, brick=(1.2, .65))
    f.box(p, (-10.3, 22.2, -5.7), (20.6, .5, 13.4), 'iron', label='belt_trim')
    f.box(p, (-10.3, 24.2, -5.7), (20.6, .5, 13.4), 'iron', label='belt_trim')
    f.box(p, (-2.5, 21.2, -6.5), (5.0, 4.2, 1.0), 'gold', label='buckle')
    f.cube(p, (0, 23.3, -7.1), (1.6, 1.6, .5), 'eye', rot((0, 0, 45)), label='buckle_gem')
    for side, s in SIDES:
        for k in range(2):
            f.cube(p, (s * (10.2 + k * .8), 19.4 - k * 2.8, .8), (1.8, 5.2, 10.6), 'rock', rot((0, 0, s * (10 + k * 9))), label='tasset',
                   heat=.2)
    lining = ('north', MATERIALS['banner'].ramp_hex[:4] + ['#5a2a20', '#7a3a26'])
    f.sheet('banner', [(-3.8, 22.1, -7.15), (3.8, 22.1, -7.15), (3.6, 12.8, -7.75), (-3.6, 12.8, -7.75)], 'banner', thickness=.2,
            up=(0, 1, 0), facing=(0, 0, -1), label='banner', lining=lining, emblem=dict(kind='crown', y=.5, r=.3, faces=('south',)),
            folds=3, burn=0)
    f.sheet('banner_tip', [(-3.6, 12.8, -7.75), (3.6, 12.8, -7.75), (3.4, 5.4, -8.3), (1.4, 4.6, -8.35), (0, 3.8, -8.4),
                           (-1.5, 4.8, -8.35), (-3.4, 5.4, -8.3)], 'banner', thickness=.2, up=(0, 1, 0), facing=(0, 0, -1),
            label='banner', lining=lining, edge={2: ('tatter', .1, 2), 3: ('tatter', .1, 2), 4: ('tatter', .1, 2),
                                                 5: ('tatter', .1, 2)}, hem=.26, folds=3, burn=.45)
    for side, s in SIDES:
        hip, knee, ankle = J[f'hip_{side}'], J[f'knee_{side}'], J[f'ankle_{side}']
        lg, sh, ft = f'leg_{side}', f'shin_{side}', f'foot_{side}'
        r = basis(knee - hip, (0, 0, 1))
        chunked(f, lg, (hip + knee) / 2 + (0, .3, 0), (9.0, np.linalg.norm(knee - hip) + 1.2, 9.6), r, rng, div=(2, 2, 2),
                label='thigh', heat=.15)
        f.cube(sh, knee + (0, .8, -4.9), (7.4, 5.2, 3.6), 'rock', rot((-14, 0, 0)), label='knee', heat=.25)
        f.spike(sh, knee + (0, 1.6, -6.3), knee + (0, 2.9, -10.6), 2.4, 'rock', steps=3, label='knee_spike', heat=.15)
        r = basis(ankle - knee, (0, 0, 1))
        chunked(f, sh, (knee + ankle) / 2 + (0, .3, 0), (10.2, np.linalg.norm(ankle - knee) + 1.0, 10.8), r, rng, div=(2, 2, 2),
                gap=.45, label='shin', heat=.18)
        for k in (-1, 1):
            f.cube(sh, (knee + ankle) / 2 + (k * 4.4, -.6, -5.4), (2.0, 6.6, 1.8), 'rock', rot((-6, 0, 0)), label='buttress', heat=.15)
        chunked(f, ft, (ankle[0], 1.9, -.6), (11.8, 3.8, 13.4), np.eye(3), rng, div=(2, 1, 2), gap=.4, jitter=.2, push=.15,
                label='foot', heat=.15)
        for k in range(3):
            x = ankle[0] - 3.8 + k * 3.8
            f.cube(ft, (x, 1.5, -8.2), (3.2, 2.9, 2.8), 'rock', rot((8, 0, 0)), label='toe', heat=.2)
            f.spike(ft, (x, 1.0, -9.4), (x, .6, -11.4), 1.8, 'rock', steps=2, label='toe_claw', heat=.1)


# ---------------------------------------------------------------------------
# Aura: orbiting rubble and the cracked magma floor


def aura(f):
    rng = random.Random(77)
    for bone, count, radius, ys, sizes in (('debris_near', 7, 22.5, (12, 36), (2.2, 4.4)), ('debris_far', 6, 25.0, (10, 42), (1.8, 3.6))):
        for k in range(count):
            a = math.tau * (k + rng.random() * .4) / count
            r = radius + rng.uniform(-1.5, 1.5)
            size = [rng.uniform(*sizes) for _ in range(3)]
            f.cube(bone, (math.cos(a) * r, rng.uniform(*ys), math.sin(a) * r), size, 'rock',
                   rot((rng.uniform(0, 90), rng.uniform(0, 90), rng.uniform(0, 90))), label=f'rubble{k % 3}', heat=.55)
    for k in range(12):
        a = math.tau * k / 12 + rng.uniform(-.1, .1)
        d = np.array([math.cos(a), 0, math.sin(a)])
        p0 = d * rng.uniform(11.5, 13.5) + (0, .1, 0)
        p1 = d * rng.uniform(19, 24) + np.cross(d, [0, 1, 0]) * rng.uniform(-1.8, 1.8) + (0, .1, 0)
        f.beam('magma', p0, p1, .9, .16, 'magma', z_hint=(0, 1, 0), label='floor_crack')
    f.ring('magma', (0, .1, 0), 19.0, .6, .16, 'magma', segments=26, normal=(0, 1, 0))


def sculpt():
    for m in MATERIALS.values():
        m.ramp_hex = ['#%02x%02x%02x' % tuple(int(v) for v in c) for c in m.ramp]
    f = Forge(MODEL, MATERIALS, seed=1313)
    rng = random.Random(2026)
    skeleton(f)
    head(f, rng)
    jaw(f)
    torso(f, rng)
    plates(f, rng)
    heart(f)
    pauldrons(f, rng)
    arms(f, rng)
    lower_body(f, rng)
    aura(f)
    return f


# ---------------------------------------------------------------------------
# Motion: a slow, crushing weight. Arms are placed by wrist target (IK), legs by angle.


STANCE = dict(pelvis=[0, 0, 0], pelvis_pos=[0, 0, 0], chest=[-6, 0, 0], head=[8, 0, 0], jaw=[0, 0, 0],
              wrist_r=[21.6, 18.6, -3.6], pole_r=[10, -2, 12], hand_r=[0, 0, 0],
              wrist_l=[-21.6, 18.6, -3.6], pole_l=[-10, -2, 12], hand_l=[0, 0, 0],
              leg_r=[8, 0, 2], shin_r=[-12, 0, 0], foot_r=[0, 0, -2], leg_l=[8, 0, -2], shin_l=[-12, 0, 0], foot_l=[0, 0, 2],
              plates=[0, 0, 0], core=[0, 0, -6.6], sway=[0, 0, 0])


def key(t, base=STANCE, **changes):
    d = dict(base)
    d.update(changes)
    return (t, d)


def aim(rest, target, damp=1.0):
    rest, target = unit(rest), unit(target)
    axis = np.cross(rest, target)
    if np.linalg.norm(axis) < 1e-7:
        return [0.0, 0.0, 0.0]
    angle = math.degrees(math.atan2(np.linalg.norm(axis), rest @ target)) * damp
    return list(np.array(euler(axis_angle(axis, angle)), float))


class Builder:
    """Poses the rig; feet are levelled by world sole angle, then a ground solver keeps feet and fists above the floor."""
    GROUNDED = ('leg_r', 'leg_l', 'shin_r', 'shin_l', 'foot_r', 'foot_l')

    def __init__(self, forge):
        self.poser = Poser(forge.bones)
        self.errors = []
        signs = np.array([[x, y, z] for x in (-1, 1) for y in (-1, 1) for z in (-1, 1)], float)
        corners = {}
        for part in forge.parts:
            if part['bone'] in self.GROUNDED + ('hand_r', 'hand_l'):
                corners.setdefault(part['bone'], []).append(part['center'] + (signs * part['size'] / 2) @ part['r'].T)
        self.corners = {k: np.concatenate(v) for k, v in corners.items()}

    def lowest(self, mats, bones):
        return min(float(((mats[b][:3, :3] @ self.corners[b].T).T + mats[b][:3, 3])[:, 1].min()) for b in bones)

    def arm(self, pose, mats, tr, t, side):
        shoulder = self.poser.world(mats, f'arm_{side}')
        error = self.poser.two_bone(pose, (f'arm_{side}', f'forearm_{side}', f'hand_{side}'), np.asarray(tr(f'wrist_{side}', t), float),
                                    shoulder + tr(f'pole_{side}', t), (1, 0, 0))
        pose[f'hand_{side}'] = list(tr(f'hand_{side}', t))
        return error

    def pose(self, tr, t, wave=1.0, ground=True):
        pose = {'pelvis': {'rot': list(tr('pelvis', t)), 'pos': list(tr('pelvis_pos', t))},
                'chest': list(tr('chest', t)), 'head': list(tr('head', t)), 'jaw': [-v for v in tr('jaw', t)]}
        for side, s in SIDES:
            for part in ('leg', 'shin'):
                pose[f'{part}_{side}'] = list(tr(f'{part}_{side}', t))
        mats = self.poser.fk(pose)
        for side, s in SIDES:
            pose[f'foot_{side}'] = euler(mats[f'shin_{side}'][:3, :3].T @ rot(tr(f'foot_{side}', t)))
        if ground:
            pose['pelvis']['pos'][1] += max(0.0, -self.lowest(self.poser.fk(pose), self.GROUNDED))
        mats = self.poser.fk(pose)
        for side, s in SIDES:
            error = self.arm(pose, mats, tr, t, side)
            if ground:
                # A slam lands on the floor instead of sinking through it.
                lift = 0.0
                for _ in range(6):
                    low = self.lowest(self.poser.fk(pose), (f'hand_{side}',))
                    if low >= .15:
                        break
                    lift += .3 - low
                    shifted = lambda name, at, base=tr, up=lift: np.asarray(base(name, at), float) + ((0, up, 0) if name == f'wrist_{side}' else 0)
                    error = self.arm(pose, mats, shifted, t, side)
            self.errors.append(error)
            arm = np.array(pose[f'arm_{side}'], float)
            pose[f'shoulder_{side}'] = [float(np.clip(arm[0] * .2, -10, 18)), 0, float(np.clip(arm[2] * .3, -12, 12))]
        opening = float(tr('plates', t)[0])
        pose['plate_r'] = [0, opening, 0]
        pose['plate_l'] = [0, -opening, 0]
        pose['core'] = {'rot': [0, 0, 0], 'pos': list(tr('core', t))}
        sway, gust, _ = tr('sway', t)
        ph = t * math.tau * wave
        mats = self.poser.fk(pose)
        for side, s in SIDES:
            # Chains hang plumb from the shackle, swinging with the arm's motion.
            parent = mats[f'forearm_{side}'][:3, :3]
            pose[f'chain_{side}'] = aim((0, -1, 0), parent.T @ unit(np.array([math.sin(ph + s) * .12 + gust * .02 * s, -1,
                                                                               sway * .02 + math.cos(ph) * .1])))
        pelvis = mats['pelvis'][:3, :3]
        pose['banner'] = aim((0, -9.3, -.6), pelvis.T @ unit(np.array([0, -1, -.08 + sway * .02 + math.sin(ph) * .05])), damp=.85)
        pose['banner_tip'] = [math.sin(ph - .8) * 6 - gust * .6, 0, math.sin(ph * .5) * 3]
        return pose


def clips(forge):
    b = Builder(forge)
    out = []

    def make(name, length, track, loop=False, extra=None, wave=None, ground=True):
        def fn(t):
            pose = b.pose(track, t, wave=wave if wave is not None else (1 / length if loop else .5), ground=ground)
            if extra:
                extra(pose, t)
            return pose
        out.append(Clip(name, length, fn, loop=loop))

    def breathe(length, depth=1.0):
        def apply(pose, t):
            u = math.sin(t / length * math.tau)
            pose['pelvis']['pos'][1] += u * .3 * depth
            pose['chest'][0] += u * 1.4 * depth
            pose['head'][0] -= u * 1.0 * depth
            pose['jaw'][0] -= max(0.0, u) * 2.0 * depth
        return apply

    # Idle: a furnace breathing; the head turns slowly over the battlefield.
    idle = Track([key(0), key(1.6, head=[6, 7, 1]), key(3.2, head=[8, 0, 0]), key(4.8, head=[6, -7, -1]), key(6.4)])
    make('idle', 6.4, idle, loop=True, extra=breathe(3.2))

    # Walk: a two-second stomp, the whole fortress rocking over each planted foot.
    def walk(t):
        ph = t / 2.0 * math.tau
        d = dict(STANCE)
        for side, s, off in (('r', 1, 0.0), ('l', -1, math.pi)):
            p = ph + off
            thigh, shin = 6 + 22 * math.sin(p), -10 - 26 * max(0.0, math.cos(p))
            d[f'leg_{side}'] = [thigh, 0, s * 2]
            d[f'shin_{side}'] = [shin, 0, 0]
            # The sole stays level while planted and tips its toes up through the swing.
            d[f'foot_{side}'] = [10 * max(0.0, math.cos(p)), 0, -s * 2]
            d[f'wrist_{side}'] = [s * 21.8, 19.4 + 1.4 * max(0.0, math.sin(p + math.pi)), -3.6 - 8 * math.sin(p + math.pi)]
        d['pelvis_pos'] = [0, -.7 * abs(math.sin(ph)), 0]
        d['pelvis'] = [0, 5 * math.sin(ph), 2.5 * math.sin(ph)]
        d['chest'] = [-9, -4 * math.sin(ph), -3 * math.sin(ph)]
        d['head'] = [10, 3 * math.sin(ph), 2 * math.sin(ph)]
        d['sway'] = [6 * math.sin(ph), 4, 0]
        d['ease'] = 'linear'
        return d
    make('walk', 2.0, Track([(round(i * .1, 4), walk(i * .1)) for i in range(21)]), loop=True)

    # Attack: the right fist cocks behind the crown, then hammers down in front.
    cock = dict(STANCE, pelvis=[0, 14, 0], chest=[8, 22, 4], head=[16, -10, 0], jaw=[10, 0, 0],
                wrist_r=[17.0, 50.0, 10.0], pole_r=[10, 6, 10], hand_r=[-30, 0, 0],
                wrist_l=[-18.0, 26.0, -14.0], pole_l=[-10, -4, 8], leg_r=[-6, 0, 2], shin_r=[-10, 0, 0], foot_r=[0, 0, -2],
                leg_l=[20, 0, -2], shin_l=[-28, 0, 0], foot_l=[0, 0, 2], sway=[-6, 4, 0])
    smash = dict(STANCE, pelvis=[0, -12, 0], pelvis_pos=[0, -1.8, 0], chest=[-30, -16, -4], head=[22, 8, 0], jaw=[16, 0, 0],
                 wrist_r=[8.0, 16.0, -24.0], pole_r=[12, 4, 10], hand_r=[-60, 0, 0],
                 wrist_l=[-19.0, 21.0, -7.0], pole_l=[-10, -2, 10], leg_r=[32, 0, 2], shin_r=[-40, 0, 0], foot_r=[0, 0, -2],
                 leg_l=[-12, 0, -2], shin_l=[-16, 0, 0], foot_l=[0, 0, 2], sway=[10, 8, 0])
    make('attack_windup', 1.1, Track([key(0), (.9, dict(cock, ease='out')), (1.1, cock)]))
    make('attack_active', .5, Track([(0, dict(cock, ease='in')), (.22, dict(smash, ease='snap')), (.5, smash)]))
    make('attack_recover', 1.0, Track([(0, smash), (.25, smash), (1.0, STANCE)]))

    # Skill: both fists rise joined over the crown, then crash down to split the ground.
    rise = dict(STANCE, pelvis_pos=[0, .8, 0], chest=[14, 0, 0], head=[24, 0, 0], jaw=[22, 0, 0],
                wrist_r=[3.5, 56.0, 2.0], pole_r=[8, 4, 8], hand_r=[0, 0, 20], wrist_l=[-3.5, 56.0, 2.0], pole_l=[-8, 4, 8],
                hand_l=[0, 0, -20], leg_r=[-2, 0, 4], shin_r=[-6, 0, 0], foot_r=[0, 0, -4], leg_l=[-2, 0, -4], shin_l=[-6, 0, 0],
                foot_l=[0, 0, 4], sway=[-8, 6, 0])
    crash = dict(STANCE, pelvis=[-8, 0, 0], pelvis_pos=[0, -3.6, 0], chest=[-36, 0, 0], head=[30, 0, 0], jaw=[14, 0, 0],
                 wrist_r=[6.5, 20.5, -23.0], pole_r=[10, 6, 10], hand_r=[-70, 0, 10], wrist_l=[-6.5, 20.5, -23.0], pole_l=[-10, 6, 10],
                 hand_l=[-70, 0, -10], leg_r=[36, 0, 6], shin_r=[-48, 0, 0], foot_r=[0, 0, -4], leg_l=[36, 0, -6],
                 shin_l=[-48, 0, 0], foot_l=[0, 0, 4], sway=[12, 10, 0])
    make('skill_windup', 1.4, Track([key(0), (1.15, dict(rise, ease='out')), (1.4, rise)]))
    make('skill_active', .6, Track([(0, dict(rise, ease='in')), (.24, dict(crash, ease='snap')), (.6, crash)]))
    make('skill_recover', 1.2, Track([(0, crash), (.35, crash), (1.2, STANCE)]))

    # Ultimate - Eruption: it kneels, fists braced, and the back plates part over the heartcrystal.
    kneel = dict(STANCE, pelvis=[-6, 0, 0], pelvis_pos=[0, -5.6, -1.0], chest=[-20, 0, 0], head=[-10, 0, 0], jaw=[6, 0, 0],
                 wrist_r=[16.0, 15.5, -16.0], pole_r=[10, 2, 10], hand_r=[-20, 0, 0], wrist_l=[-16.0, 15.5, -16.0], pole_l=[-10, 2, 10],
                 hand_l=[-20, 0, 0], leg_r=[70, 0, 6], shin_r=[-76, 0, 0], foot_r=[0, 0, -4],
                 leg_l=[-4, 0, -4], shin_l=[-86, 0, 0], foot_l=[180, 0, 4], plates=[78, 0, 0], core=[0, .8, 1.8], sway=[0, 2, 0])
    roar = dict(kneel, chest=[-8, 0, 0], head=[22, 0, 0], jaw=[30, 0, 0], plates=[84, 0, 0], core=[0, 1.2, 2.4], sway=[0, 10, 0])
    make('ultimate_windup', 1.8, Track([key(0), (.7, dict(STANCE, pelvis_pos=[0, -1.6, 0], chest=[-14, 0, 0], ease='in')),
                                        (1.5, dict(kneel, ease='out')), (1.8, kneel)]))
    make('ultimate_active', 2.4, Track([(0, kneel), (.5, dict(roar, ease='out')), (1.2, kneel), (1.7, roar), (2.4, kneel)]),
         extra=lambda pose, t: pose['chest'].__setitem__(0, pose['chest'][0] + math.sin(t * 9) * 1.2))
    make('ultimate_recover', 1.6, Track([(0, kneel), (.4, dict(kneel, plates=[40, 0, 0])), (1.6, STANCE)]))

    # Stun: the fortress sags, one knee buckling, the plates jarred loose.
    slump = dict(STANCE, pelvis=[-4, 0, 6], pelvis_pos=[0, -3.4, 0], chest=[-26, 6, 8], head=[-20, 10, 6], jaw=[12, 0, 0],
                 wrist_r=[19.0, 15.5, -11.0], pole_r=[8, -2, 10], hand_r=[-10, 0, 0], wrist_l=[-22.5, 17.0, -1.0], pole_l=[-10, -2, 10],
                 leg_r=[26, 0, 4], shin_r=[-38, 0, 0], foot_r=[0, 0, -2], leg_l=[10, 0, -4], shin_l=[-22, 0, 0], foot_l=[0, 0, 2],
                 plates=[22, 0, 0], core=[0, 0, -4.4], sway=[4, 0, 0])
    make('stun', 2.2, Track([key(0), (.35, dict(slump, ease='out')), (1.9, slump), (2.2, STANCE)]),
         extra=lambda pose, t: pose['head'].__setitem__(1, pose['head'][1] + math.sin(t * 7) * 2.0 * min(1.0, t)))

    # Transition: arms flung wide, it roars and the plates flare as the crown boils over.
    spread = dict(STANCE, chest=[16, 0, 0], head=[26, 0, 0], jaw=[32, 0, 0], pelvis_pos=[0, .6, 0],
                  wrist_r=[35.0, 38.0, -4.0], pole_r=[4, 10, 8], hand_r=[0, 0, 40], wrist_l=[-35.0, 38.0, -4.0], pole_l=[-4, 10, 8],
                  hand_l=[0, 0, -40], leg_r=[-4, 0, 8], shin_r=[-8, 0, 0], foot_r=[0, 0, -6], leg_l=[-4, 0, -8], shin_l=[-8, 0, 0],
                  foot_l=[0, 0, 6], plates=[60, 0, 0], core=[0, .6, 1.2], sway=[-8, 12, 0])
    hunch = dict(STANCE, chest=[-24, 0, 0], head=[-6, 0, 0], pelvis_pos=[0, -1.8, 0], wrist_r=[10.0, 22.0, -14.0], pole_r=[10, 0, 10],
                 wrist_l=[-10.0, 22.0, -14.0], pole_l=[-10, 0, 10], leg_r=[18, 0, 2], shin_r=[-26, 0, 0], leg_l=[18, 0, -2],
                 shin_l=[-26, 0, 0])
    make('transition', 3.4, Track([key(0), (.8, dict(hunch, ease='in')), (1.25, dict(spread, ease='snap')), (2.6, spread),
                                   (3.4, STANCE)]),
         extra=lambda pose, t: pose['chest'].__setitem__(0, pose['chest'][0] + (math.sin(t * 14) * 1.6 if 1.25 < t < 2.6 else 0)))

    # Spawn: it climbs out of the lava moat, hands first.
    sunk = dict(STANCE, pelvis_pos=[0, -46.0, -2.0], chest=[-20, 0, 0], head=[24, 0, 0], jaw=[18, 0, 0],
                wrist_r=[22.0, 2.0, -18.0], pole_r=[10, 4, 6], hand_r=[-40, 0, 0], wrist_l=[-22.0, 2.0, -18.0], pole_l=[-10, 4, 6],
                hand_l=[-40, 0, 0])
    push = dict(STANCE, pelvis_pos=[0, -19.0, -1.0], chest=[-28, 0, 0], head=[18, 0, 0], jaw=[10, 0, 0],
                wrist_r=[22.0, 15.0, -18.0], pole_r=[10, 4, 8], hand_r=[-50, 0, 0], wrist_l=[-22.0, 15.0, -18.0], pole_l=[-10, 4, 8],
                hand_l=[-50, 0, 0])
    make('spawn', 3.4, Track([(0, sunk), (1.4, dict(push, ease='out')), (2.6, dict(STANCE, ease='out')), (3.4, STANCE)]), ground=False)
    return out, max(b.errors) if b.errors else 0.0


def aura_clip():
    def fn(t):
        u = t / 48.0
        return {'debris_near': {'rot': [0, 360 * u * 2, 0], 'pos': [0, math.sin(u * math.tau * 4) * .6, 0]},
                'debris_far': {'rot': [0, -360 * u, 0], 'pos': [0, math.sin(u * math.tau * 3 + 1) * .8, 0]},
                'magma': [0, 0, 0]}
    return Clip('aura', 48.0, fn, loop=True, step=1 / 10, bones=AURA)


def build():
    f = sculpt()
    motion, worst = clips(f)
    body = [b for b in f.bones if b not in AURA and b not in MARKERS and b != 'hi_gaze']
    for clip in motion:
        clip.bones = body
    assert [c.name for c in motion] == list(CLIPS)
    model, atlas, stats = build_realm(f, motion + [aura_clip()], MODEL, [[-16, 0, -16], [16, 56, 16]],
                                      meta=dict(evergarden_revision=REVISION, evergarden_scale=SCALE), density=8.0)
    return model, atlas, stats, worst, f
