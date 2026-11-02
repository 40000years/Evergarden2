"""The Soul-Eater Hound, guardian of the Soul Bone Temple in the Nether.

A colossal wolf that is nothing but bone and soul fire. The long wedge skull glows
cyan through slanted sockets and an open throat; the ribcage burns with soul flame
and splays open when it howls; a ridge of dark spines runs from the nape to a bladed
tail; clods of soul soil still cling to it from the catacombs it digs through; a
broken spiked temple collar trails its chain; every paw burns. A floor sigil of soul
fire and a ring of wandering wisps loop on their own lane.
"""
import math
import random

import numpy as np

from boss_forge import Clip, Forge, Material, Poser, Track, axis_angle, basis, bezier, euler, rot, unit
from realm_forge import CLIPS, build_realm

MODEL = 'soul_hound'
TITLE = 'Soul-Eater Hound'
REVISION = 'soul-hound-v1'
SCALE = 2.8

BONE = ['#2a2019', '#4a3c2f', '#6f5f4b', '#968569', '#bcae90', '#d9cdb1', '#ece4d0', '#faf6ec']
DARK = ['#120c0a', '#1f1612', '#2e211b', '#402e24', '#553d30', '#6f503f', '#8a6552']
SOIL = ['#160e0a', '#24170f', '#342116', '#462d1e', '#5a3a27', '#704a33', '#8a5e42']
SOUL = ['#032a36', '#05485b', '#08778f', '#16a9c9', '#4ad8f2', '#a2f3ff', '#f0fdff']
FLAME = ['#031b24', '#053043', '#085166', '#0d7a95', '#1aa6c4', '#4fd2ec', '#a8f1ff']
IRON = ['#111216', '#1c1d23', '#2a2c33', '#3b3e47', '#50545f', '#696e7b', '#8a8f9d']
MATERIALS = {
    'bone': Material(BONE, 'bone', accent=SOUL),
    'dark': Material(DARK, 'horn'),
    'soil': Material(SOIL, 'rock', accent=SOUL, density=.8),
    'socket': Material(['#040507', '#090b0f', '#0f1218', '#171b22'], 'rock', density=.6),
    'soul': Material(SOUL, 'glow', glow=True, density=.6),
    'soulfire': Material(FLAME, 'flame', glow=True, density=.8),
    'eye': Material(['#5fe3f5', '#b6f6ff', '#ffffff'], 'glow', glow=True, density=1.6),
    'iron': Material(IRON, 'trim', density=.9),
    'chain': Material(IRON, 'chain', density=1.6),
}
SIDES = (('r', 1), ('l', -1))
AURA = ('sigil', 'wisps')
MARKERS = ('root', 'facing', 'head_forward', 'fx_eyes', 'fx_mouth', 'fx_heart', 'fx_tail', 'fx_paw_r', 'fx_paw_l')
J = {k: np.array(v, float) for k, v in dict(
    pelvis=(0, 20.5, 14.0), spine=(0, 22.0, 2.0), chest=(0, 23.0, -10.0), neck=(0, 25.5, -19.0), head=(0, 28.0, -26.0),
    jaw=(0, 26.4, -28.0), tail1=(0, 22.2, 21.5), tail2=(0, 23.4, 29.5), tail3=(0, 22.4, 37.0), tail4=(0, 20.0, 43.5)).items()}
for _side, _s in SIDES:
    J.update({f'shoulder_{_side}': np.array([_s * 7.4, 20.0, -13.0]), f'elbow_{_side}': np.array([_s * 7.8, 11.4, -10.2]),
              f'wrist_{_side}': np.array([_s * 7.8, 3.8, -13.4]), f'hip_{_side}': np.array([_s * 7.0, 20.4, 15.4]),
              f'knee_{_side}': np.array([_s * 7.6, 12.8, 10.2]), f'hock_{_side}': np.array([_s * 7.6, 7.0, 18.6]),
              f'heel_{_side}': np.array([_s * 7.6, 2.2, 16.4])})
PREVIEW = dict(close=(30, (0, 27)), clip_span=100, clip_center=(0, 20))


def skeleton(f):
    f.bone('root', None, [0, 0, 0])
    f.bone('facing', 'root', [0, 16, -50])
    f.bone('pelvis', 'root', J['pelvis'])
    f.bone('spine', 'pelvis', J['spine'])
    f.bone('chest', 'spine', J['chest'])
    f.bone('ribs_r', 'chest', [1.6, 28.6, -10.0])
    f.bone('ribs_l', 'chest', [-1.6, 28.6, -10.0])
    f.bone('neck', 'chest', J['neck'])
    f.bone('chain', 'neck', [0, 20.6, -21.0])
    f.bone('hi_gaze', 'neck', J['head'])
    f.bone('head', 'hi_gaze', J['head'])
    f.bone('head_forward', 'head', [0, 28.0, -54])
    f.bone('jaw', 'head', J['jaw'])
    for side, s in SIDES:
        f.bone(f'fore_{side}', 'chest', J[f'shoulder_{side}'])
        f.bone(f'foreleg_{side}', f'fore_{side}', J[f'elbow_{side}'])
        f.bone(f'forepaw_{side}', f'foreleg_{side}', J[f'wrist_{side}'])
        f.bone(f'thigh_{side}', 'pelvis', J[f'hip_{side}'])
        f.bone(f'shank_{side}', f'thigh_{side}', J[f'knee_{side}'])
        f.bone(f'hock_{side}', f'shank_{side}', J[f'hock_{side}'])
        f.bone(f'hindpaw_{side}', f'hock_{side}', J[f'heel_{side}'])
    f.bone('tail1', 'pelvis', J['tail1'])
    f.bone('tail2', 'tail1', J['tail2'])
    f.bone('tail3', 'tail2', J['tail3'])
    f.bone('tail4', 'tail3', J['tail4'])
    f.bone('sigil', 'root', [0, .12, 0])
    f.bone('wisps', 'root', [0, 0, 0])
    f.bone('fx_eyes', 'head', [0, 29.8, -33.0])
    f.bone('fx_mouth', 'jaw', [0, 25.0, -38.0])
    f.bone('fx_heart', 'chest', [0, 21.6, -10.0])
    f.bone('fx_tail', 'tail4', [0, 17.0, 54.0])
    for side, s in SIDES:
        f.bone(f'fx_paw_{side}', f'forepaw_{side}', [s * 7.8, .8, -17.0])


# ---------------------------------------------------------------------------
# Skull and jaw


def skull(f):
    b = 'head'
    f.box(b, (-4.0, 26.6, -31.6), (8.0, 6.4, 8.2), 'bone', label='cranium', soul=.07)
    f.box(b, (-3.2, 27.2, -23.6), (6.4, 5.0, 3.0), 'bone', label='occiput')
    f.beam(b, (0, 33.2, -21.6), (0, 32.8, -31.0), 1.3, 1.1, 'bone', z_hint=(1, 0, 0), label='crest')
    for side, s in SIDES:
        # A brow slanting down to the centre over a deep socket with a slanted soul-fire eye.
        f.beam(b, (s * 4.7, 32.8, -30.4), (s * .7, 31.3, -32.4), 1.5, 1.4, 'bone', label='brow')
        f.box(b, (s * 2.6 - 1.4, 28.4, -32.2), (2.8, 2.4, 1.4), 'socket', label='socket')
        f.cube(b, (s * 2.6, 29.7, -32.62), (2.1, 1.05, .5), 'eye', rot((0, 0, -s * 18)), label='eye')
        f.beam(b, (s * 3.8, 28.2, -31.8), (s * 6.4, 29.0, -24.4), 1.3, 1.1, 'bone', label='cheekbone')
        f.taper(b, [(s * 3.4, 32.4, -24.4), (s * 5.4, 34.4, -21.0), (s * 6.0, 36.6, -16.4), (s * 5.6, 37.6, -12.8)], [1.6, 1.1, .5],
                [1.6, 1.1, .5], 'dark', z_hint=(0, 1, 0), label='skull_horn', overlap=.25)
    # Wedge snout: snout, muzzle and nose tip, the nasal cavity lit from within.
    f.box(b, (-3.1, 25.6, -37.6), (6.2, 4.6, 6.0), 'bone', label='snout', soul=.05)
    f.box(b, (-2.4, 25.4, -43.2), (4.8, 3.8, 5.6), 'bone', label='muzzle')
    f.box(b, (-1.8, 25.8, -44.4), (3.6, 2.6, 1.3), 'bone', label='nose')
    f.beam(b, (0, 30.4, -31.8), (0, 29.3, -43.2), 1.3, .9, 'bone', z_hint=(1, 0, 0), label='nose_ridge')
    f.box(b, (-1.1, 27.0, -44.65), (2.2, 1.4, .4), 'socket', label='nostril')
    f.box(b, (-.7, 27.25, -44.8), (1.4, .8, .3), 'soul', label='nostril_glow')
    f.box(b, (-2.4, 25.1, -42.4), (4.8, 1.0, 10.6), 'soul', label='throat')
    # Upper fangs: long canines at the front, a saw of teeth behind.
    for side, s in SIDES:
        f.spike(b, (s * 2.0, 25.8, -42.6), (s * 2.0, 21.0, -42.9), 1.25, 'bone', steps=3, label='canine')
        for k in range(6):
            z = -40.8 + k * 1.55
            f.spike(b, (s * 2.75, 25.7, z), (s * 2.75, 23.6 if k % 2 else 24.4, z), .64, 'bone', steps=2, label='fang')
    f.spike(b, (0, 32.6, -22.6), (0, 35.0, -17.8), 1.7, 'dark', steps=3, label='crest_spike')


def jaw(f):
    b = 'jaw'
    for side, s in SIDES:
        f.beam(b, (s * 3.6, 26.0, -27.4), (s * 2.4, 23.6, -43.0), 1.5, 1.7, 'bone', label='mandible')
        f.spike(b, (s * 1.9, 24.0, -42.4), (s * 1.9, 27.8, -42.7), 1.05, 'bone', steps=3, label='lower_canine')
        for k in range(5):
            z = -40.6 + k * 1.9
            f.spike(b, (s * 2.55, 24.4, z), (s * 2.55, 26.0, z), .6, 'bone', steps=2, label='lower_fang')
    f.box(b, (-2.6, 22.9, -43.8), (5.2, 1.4, 2.4), 'bone', label='chin')
    f.box(b, (-2.0, 23.3, -41.6), (4.0, .8, 12.4), 'soul', label='tongue_fire')


# ---------------------------------------------------------------------------
# Neck, collar and chain


def neck(f):
    b = 'neck'
    for k in range(4):
        u = k / 3
        c = J['neck'] * (1 - u) + J['head'] * u + np.array([0, -.6, 2.2 - u * 1.4])
        f.cube(b, c, (3.4, 3.0, 2.0), 'bone', rot((-18, 0, 0)), label='cervical')
        f.spike(b, c + (0, 1.4, .3), c + (0, 4.8 - u, 2.4), .9, 'dark', steps=2, label='cervical_spine', depth_ratio=2.0)
        for side, s in SIDES:
            f.spike(b, c + (s * 1.6, 0, 0), c + (s * 3.4, -.4, .8), .8, 'bone', steps=2, label='cervical_wing')
    # Broken temple collar: an iron band with spikes, its chain dangling.
    c = np.array([0, 25.6, -21.2])
    f.ring(b, c, 4.4, 1.3, 1.1, 'iron', segments=12, normal=(0, .3, -1))
    for k in range(6):
        a = math.tau * k / 6 + math.pi / 6
        d = np.array([math.cos(a), math.sin(a) * .95, .3 * math.sin(a)])
        f.spike(b, c + d * 4.6, c + d * 7.8, 1.05, 'iron', steps=2, label='collar_spike')
    f.cube('chain', (0, 20.6, -21.0), (1.6, 1.6, 1.6), 'iron', label='collar_ring')
    for k in range(5):
        f.cube('chain', (0, 19.2 - k * 1.9, -21.0), (1.4, 2.5, .34), 'chain', rot((0, 90 if k % 2 else 0, 0)), label='chain_link',
               faces=('north', 'south'))


# ---------------------------------------------------------------------------
# Spine, ribcage, soul fire and pelvis


def spine(f):
    # Vertebrae with a ridge of dark spines: tallest over the shoulders, shortening to the hips.
    for bone, z0, z1, count, top, height in (('chest', -18.0, -4.0, 5, 28.6, 9.6), ('spine', -1.0, 9.0, 3, 28.2, 6.4),
                                            ('pelvis', 12.0, 19.0, 2, 27.6, 4.2)):
        for k in range(count):
            u = k / max(1, count - 1)
            z = z0 + (z1 - z0) * u
            y = top - (1.4 if bone == 'pelvis' else 0) * u
            f.box(bone, (-1.9, y - 1.6, z - 1.2), (3.8, 3.0, 2.4), 'bone', label='vertebra')
            for side, s in SIDES:
                f.box(bone, (s * 1.9 - (s < 0) * 1.6, y - 1.0, z - .5), (1.6, .9, 1.0), 'bone', label='transverse')
            h = height * (1 - .3 * u) if bone != 'chest' else height * (1 - .3 * abs(u - .3) * 2)
            f.spike(bone, (0, y + 1.0, z), (0, y + 1.0 + h, z + h * .5), 1.0, 'dark', steps=3, label='dorsal_spine', depth_ratio=2.2)
        if count > 1:
            f.beam(bone, (0, top - .2, z0), (0, top - .2 - (1.4 if bone == 'pelvis' else 0), z1), 2.2, 2.2, 'bone', z_hint=(0, 1, 0),
                   label='spinal_cord')
    rng = random.Random(9)
    for bone, c in (('chest', (2.8, 29.0, -14.0)), ('chest', (-3.2, 28.6, -6.0)), ('spine', (3.0, 28.4, 4.0)),
                    ('pelvis', (-3.4, 27.4, 13.0)), ('chest', (-6.2, 25.8, -15.4)), ('pelvis', (4.6, 25.6, 17.4))):
        f.cube(bone, c, (rng.uniform(2.6, 3.6), rng.uniform(1.6, 2.4), rng.uniform(2.6, 3.8)), 'soil',
               rot((rng.uniform(-20, 20), rng.uniform(0, 60), rng.uniform(-20, 20))), label='clod', heat=.4)


def ribs(f):
    for side, s in SIDES:
        b = f'ribs_{side}'
        for k in range(8):
            u = k / 7
            z = -17.6 + u * 14.6
            size = .76 + .34 * math.sin(math.pi * min(1, u * 1.15))
            ctrl = [(s * 1.7, 28.5, z), (s * 10.2 * size, 29.6, z + .4), (s * 11.8 * size, 19.4, z + 1.0),
                    (s * 6.6 * size, 14.4, z + 1.6), (s * 2.1, 14.0 + (1 - size) * 3, z + 1.9)]
            pts = [bezier(ctrl, i / 4) for i in range(5)]
            f.taper(b, pts, [.9, .85, .75, .62], [.8, .78, .7, .6], 'bone', z_hint=(0, 0, 1), label=f'rib{k % 3}', overlap=.3,
                    soul=.1)
    b = 'chest'
    f.beam(b, (0, 14.2, -18.4), (0, 14.6, -2.2), 2.2, 1.4, 'bone', z_hint=(0, 1, 0), label='sternum')
    for side, s in SIDES:
        f.cube(b, (s * 5.2, 25.6, -14.4), (1.2, 7.4, 5.8), 'bone', rot((-14, 0, s * 18)), label='scapula', soul=.06)
    # The heart of soul fire burning inside the ribs.
    f.cube(b, (0, 21.0, -10.0), (3.2, 3.2, 3.2), 'soul', rot((45, 35, 0)), label='soul_heart')
    for k, (dx, dz, h, w) in enumerate(((1, 0, 12.0, 8.0), (0, 1, 11.0, 10.4), (.7, .7, 10.0, 7.2), (-.7, .7, 9.4, 7.0),
                                        (1, .25, 7.4, 9.0))):
        axis = unit(np.array([dx, 0, dz]))
        f.sheet(b, flame_outline(np.array([0, 15.0, -10.0]), axis, h, w, k), 'soulfire', thickness=.06, up=(0, 1, 0), label='rib_fire',
                seed=410 + k)


def flame_outline(base, axis, height, width, variant):
    pts = []
    tongues = 4 + variant % 2
    rng = random.Random(variant * 31 + int(height * 10))
    for i in range(tongues * 2 + 1):
        u = i / (tongues * 2)
        x = (u - .5) * width + (rng.uniform(-.12, .12) * width / tongues if i % 2 == 0 else 0)
        tall = (height * (.5 + .5 * math.sin(math.pi * u) ** .8) * rng.uniform(.75, 1.0) if i % 2 == 0
                else height * (.22 + .2 * math.sin(math.pi * u)))
        pts.append(base + axis * x + np.array([0, tall, 0]))
    pts.append(base + axis * width * .4 + np.array([0, -.2, 0]))
    pts.append(base + axis * (-width * .4) + np.array([0, -.2, 0]))
    return pts[::-1]


def pelvis(f):
    b = 'pelvis'
    f.box(b, (-2.4, 23.6, 10.4), (4.8, 3.0, 10.0), 'bone', label='sacrum')
    for side, s in SIDES:
        f.cube(b, (s * 4.6, 23.4, 13.0), (1.4, 6.6, 8.4), 'bone', rot((14, 0, -s * 24)), label='ilium', soul=.06)
        f.cube(b, (s * 4.4, 17.6, 17.6), (1.2, 5.0, 4.6), 'bone', rot((-20, 0, -s * 10)), label='ischium')
        f.box(b, (s * 7.0 - 1.2, 19.2, 14.2), (2.4, 2.4, 2.4), 'bone', label='hip_socket')


# ---------------------------------------------------------------------------
# Legs, paws and tail


def leg_bone(f, bone, a, b, width, label):
    """A long bone with knobbed ends."""
    a, b = np.asarray(a, float), np.asarray(b, float)
    f.beam(bone, a, b, width, width * .9, 'bone', label=label)
    for end in (a, b):
        f.cube(bone, end, (width * 1.25, width * 1.25, width * 1.25), 'bone', rot((0, 45, 0)), label=label + '_knob')


def paw(f, bone, heel, s, flames):
    """Toes splayed forward with parallel hooked claws; soul fire licks around the wrist."""
    heel = np.asarray(heel, float)
    f.box(bone, (heel[0] - 2.4, .4, heel[2] - 4.2), (4.8, 2.4, 4.2), 'bone', label='metacarpus')
    for k in range(4):
        x = heel[0] + (-2.1 + k * 1.4) * s
        z0 = heel[2] - 2.8 - abs(k - 1.5) * .5
        f.box(bone, (x - .55, .3, z0 - 3.2), (1.1, 1.3, 3.4), 'bone', label='toe')
        f.taper(bone, [(x, 1.2, z0 - 3.1), (x, 1.0, z0 - 5.0), (x, .1, z0 - 6.2)], [.9, .55], [.95, .6], 'dark', z_hint=(1, 0, 0),
                label='claw', overlap=.2)
    f.cube(bone, (heel[0] + s * 2.6, 2.6, heel[2] - 1.0), (.8, 2.6, 1.0), 'dark', rot((-30, 0, 0)), label='dewclaw')
    for k in range(flames):
        axis = unit(np.array([math.cos(k * 1.9), 0, math.sin(k * 1.9)]))
        f.sheet(bone, flame_outline(heel + np.array([0, .6, -1.0]), axis, 6.6 - k * .9, 4.6, k), 'soulfire', thickness=.06,
                up=(0, 1, 0), label='paw_fire', seed=520 + k)


def legs(f):
    for side, s in SIDES:
        sh, el, wr = J[f'shoulder_{side}'], J[f'elbow_{side}'], J[f'wrist_{side}']
        leg_bone(f, f'fore_{side}', sh, el, 2.5, 'humerus')
        f.cube(f'fore_{side}', sh + (0, 1.4, -.4), (3.6, 4.0, 4.2), 'bone', rot((0, 0, s * 8)), label='shoulder_knob', soul=.05)
        f.cube(f'fore_{side}', (sh + el) / 2 + (s * 1.4, 1.0, .6), (2.4, 3.2, 3.0), 'soil', rot((10, 20, s * 14)), label='leg_clod',
               heat=.35)
        f.beam(f'foreleg_{side}', el + (s * .5, 0, 0), wr + (s * .5, .6, .4), 1.3, 1.3, 'bone', label='radius')
        f.beam(f'foreleg_{side}', el + (-s * .6, -.2, .4), wr + (-s * .6, .6, -.2), 1.1, 1.1, 'bone', label='ulna')
        f.spike(f'foreleg_{side}', el + (0, .4, 1.2), el + (0, 2.8, 4.2), 1.5, 'dark', steps=2, label='elbow_spur')
        paw(f, f'forepaw_{side}', wr, s, 3)
        hip, knee, hock, heel = J[f'hip_{side}'], J[f'knee_{side}'], J[f'hock_{side}'], J[f'heel_{side}']
        leg_bone(f, f'thigh_{side}', hip, knee, 2.8, 'femur')
        f.cube(f'thigh_{side}', (hip + knee) / 2 + (s * 1.6, .8, 1.0), (2.6, 3.6, 3.2), 'soil', rot((-12, 20, s * 12)), label='leg_clod',
               heat=.35)
        f.beam(f'shank_{side}', knee, hock, 1.9, 1.9, 'bone', label='tibia')
        f.beam(f'shank_{side}', knee + (s * .9, -.6, .4), hock + (s * .9, .6, -.2), .9, .9, 'bone', label='fibula')
        f.cube(f'hock_{side}', hock, (2.2, 2.2, 2.2), 'bone', rot((0, 45, 0)), label='hock_knob')
        f.spike(f'hock_{side}', hock + (0, .4, .9), hock + (0, 1.6, 4.6), 1.4, 'dark', steps=2, label='hock_spur')
        f.beam(f'hock_{side}', hock, heel + (0, .6, 0), 1.6, 1.6, 'bone', label='metatarsus')
        paw(f, f'hindpaw_{side}', heel, s, 2)


def tail(f):
    names = ('tail1', 'tail2', 'tail3', 'tail4')
    for i, bone in enumerate(names):
        a = J[bone]
        b = J[names[i + 1]] if i + 1 < len(names) else J['tail4'] + np.array([0, -1.8, 6.4])
        r = basis(b - a, (0, 1, 0)) @ rot((90, 0, 0))
        for k in range(3):
            u = (k + .5) / 3
            c = a + (b - a) * u
            w = 2.6 - (i * 3 + k) * .16
            f.cube(bone, c, (w, w * .9, 1.9), 'bone', r, label=f'caudal{i}')
        mid = (a + b) / 2
        f.spike(bone, mid + (0, 1.2, 0), mid + (0, 4.6 - i * .7, 2.6), max(.6, 1.0 - i * .12), 'dark', steps=2, label='tail_spine',
                depth_ratio=2.0)
    tip = J['tail4'] + np.array([0, -1.8, 6.4])
    f.taper('tail4', [tip, tip + (0, -.4, 3.6), tip + (0, -1.4, 7.6)], [2.6, 1.3], [.9, .6], 'bone', z_hint=(1, 0, 0), label='tail_blade',
            overlap=.3, soul=.35)
    for k in range(2):
        axis = unit(np.array([1, 0, .3 * (k * 2 - 1)]))
        f.sheet('tail3', flame_outline(J['tail3'] + np.array([0, 1.2, 2.4]), axis, 5.4, 5.6, k), 'soulfire', thickness=.06, up=(0, 1, 0),
                label='tail_fire', seed=600 + k)


# ---------------------------------------------------------------------------
# Aura: soul-fire floor sigil and wandering wisps


def aura(f):
    s0 = np.array([0, .12, 0])
    f.ring('sigil', s0, 24.0, .5, .1, 'soul', segments=32, normal=(0, 1, 0))
    f.ring('sigil', s0, 21.4, .28, .1, 'soul', segments=28, normal=(0, 1, 0), phase=.12)
    for k in range(10):
        a = math.tau * k / 10
        d = np.array([math.cos(a), 0, math.sin(a)])
        f.beam('sigil', s0 + d * 21.6, s0 + d * (23.8 if k % 2 else 26.6), .55, .1, 'soul', z_hint=(0, 1, 0), label='sigil_ray')
    rng = random.Random(4)
    for k in range(7):
        a = math.tau * k / 7
        r = rng.uniform(22, 28)
        c = np.array([math.cos(a) * r, rng.uniform(6, 26), math.sin(a) * r])
        f.cube('wisps', c, (1.2, 1.2, 1.2), 'eye', rot((45, 35, 0)), label='wisp')
        f.sheet('wisps', flame_outline(c + (0, -.6, 0), unit(np.array([-math.sin(a), 0, math.cos(a)])), 3.8, 2.4, k), 'soulfire',
                thickness=.06, up=(0, 1, 0), label='wisp_flame', seed=700 + k)


def sculpt():
    for m in MATERIALS.values():
        m.ramp_hex = ['#%02x%02x%02x' % tuple(int(v) for v in c) for c in m.ramp]
    f = Forge(MODEL, MATERIALS, seed=6666)
    skeleton(f)
    skull(f)
    jaw(f)
    neck(f)
    spine(f)
    ribs(f)
    pelvis(f)
    legs(f)
    tail(f)
    aura(f)
    return f


# ---------------------------------------------------------------------------
# Motion: a prowling hunter. Paws are planted by target (IK); the body moves over them.


FEET = {'fr': J['wrist_r'], 'fl': J['wrist_l'], 'hr': J['heel_r'], 'hl': J['heel_l']}
STANCE = dict(pelvis=[-2, 0, 0], pelvis_pos=[0, -1.6, 0], spine=[0, 0, 0], chest=[-3, 0, 0], neck=[-12, 0, 0], head=[8, 0, 0],
              jaw=[12, 0, 0], ribs=[0, 0, 0], tail=[-6, 0, 0], sway=[0, 0, 0],
              meta_r=[0, 0, 0], meta_l=[0, 0, 0], **{k: list(v) for k, v in FEET.items()},
              **{k + '_pitch': [0, 0, 0] for k in FEET})


def key(t, base=STANCE, **changes):
    d = dict(base)
    d.update(changes)
    return (t, d)


def feet(base=STANCE, dx=0.0, dy=0.0, dz=0.0, front=None, hind=None):
    """All four paw targets shifted together, or front/hind pairs by their own offsets."""
    out = dict(base)
    for k, v in FEET.items():
        off = np.array([0.0, dy, dz]) + (np.array(front if k[0] == 'f' else hind) if (front if k[0] == 'f' else hind) else 0)
        out[k] = list(np.asarray(v, float) + off + np.array([dx * (1 if k[1] == 'r' else -1), 0, 0]))
    return out


def aim(rest, target, damp=1.0):
    rest, target = unit(rest), unit(target)
    axis = np.cross(rest, target)
    if np.linalg.norm(axis) < 1e-7:
        return [0.0, 0.0, 0.0]
    angle = math.degrees(math.atan2(np.linalg.norm(axis), rest @ target)) * damp
    return list(np.array(euler(axis_angle(axis, angle)), float))


class Builder:
    def __init__(self, forge):
        self.poser = Poser(forge.bones)
        self.errors = []

    def pose(self, tr, t, wave=1.0):
        pose = {'pelvis': {'rot': list(tr('pelvis', t)), 'pos': list(tr('pelvis_pos', t))}}
        for bone in ('spine', 'chest', 'neck', 'head'):
            pose[bone] = list(tr(bone, t))
        pose['jaw'] = [-float(tr('jaw', t)[0]), 0, 0]
        flare = float(tr('ribs', t)[0])
        pose['ribs_r'] = [0, 0, -flare]
        pose['ribs_l'] = [0, 0, flare]
        mats = self.poser.fk(pose)
        for side, s in SIDES:
            shoulder = self.poser.world(mats, f'fore_{side}')
            self.errors.append(self.poser.two_bone(pose, (f'fore_{side}', f'foreleg_{side}', f'forepaw_{side}'), tr('f' + side, t),
                                                   shoulder + (0, -8, 14), (1, 0, 0), end_rot=rot(tr(f'f{side}_pitch', t))))
            hip = self.poser.world(mats, f'thigh_{side}')
            meta = rot(tr(f'meta_{side}', t))
            hock = np.asarray(tr('h' + side, t), float) + meta @ (J[f'hock_{side}'] - J[f'heel_{side}'])
            self.errors.append(self.poser.two_bone(pose, (f'thigh_{side}', f'shank_{side}', f'hock_{side}'), hock,
                                                   hip + (0, -6, -14), (-1, 0, 0), end_rot=meta))
            pose[f'hindpaw_{side}'] = euler(meta.T @ rot(tr(f'h{side}_pitch', t)))
        lift, swing, curl = tr('tail', t)
        sway, gust, _ = tr('sway', t)
        ph = t * math.tau * wave
        for i, bone in enumerate(('tail1', 'tail2', 'tail3', 'tail4')):
            pose[bone] = [lift * (1 - i * .15) + math.sin(ph - i * .7) * 3, swing * (1 - i * .1) + math.sin(ph * .5 - i * .9) * (6 + gust),
                          curl * .2]
        mats = self.poser.fk(pose)
        pose['chain'] = aim((0, -1, 0), mats['neck'][:3, :3].T @ unit(np.array([math.sin(ph) * .1, -1, sway * .02 + math.cos(ph) * .08])))
        return pose


def gait(t, length):
    """Diagonal-pair walk: each paw slides back while planted, then lifts and swings forward."""
    ph = t / length
    d = dict(STANCE)
    stride = 7.0
    for k, off in (('fr', 0.0), ('hl', 0.0), ('fl', .5), ('hr', .5)):
        u = (ph + off) % 1.0
        base = np.asarray(FEET[k], float)
        if u < .6:
            dz, dy, pitch = stride * (-.5 + u / .6), 0.0, 0.0
        else:
            v = (u - .6) / .4
            dz, dy, pitch = stride * (.5 - v), 5.0 * math.sin(math.pi * v), -22 * math.sin(math.pi * v)
        d[k] = list(base + (0, dy, -dz))
        d[k + '_pitch'] = [pitch if k[0] == 'f' else pitch * .5, 0, 0]
    w = ph * math.tau
    d['pelvis_pos'] = [0, -1.6 - .5 * abs(math.sin(w)), 0]
    d['pelvis'] = [-2, 3 * math.sin(w), 2 * math.sin(w)]
    d['spine'] = [0, -2 * math.sin(w), -1.5 * math.sin(w)]
    d['chest'] = [-3, -3 * math.sin(w), -1.5 * math.sin(w)]
    d['neck'] = [-14, 4 * math.sin(w), 0]
    d['head'] = [10 + 2 * math.sin(w * 2), -2 * math.sin(w), 0]
    d['tail'] = [-8, 0, 0]
    d['sway'] = [5 * math.sin(w), 4, 0]
    d['ease'] = 'linear'
    return d


def clips(forge):
    b = Builder(forge)
    out = []

    def make(name, length, track, loop=False, extra=None, wave=None):
        def fn(t):
            pose = b.pose(track, t, wave=wave if wave is not None else (1 / length if loop else .6))
            if extra:
                extra(pose, t)
            return pose
        out.append(Clip(name, length, fn, loop=loop))

    def breathe(length):
        def apply(pose, t):
            u = math.sin(t / length * math.tau)
            pose['chest'][0] += u * 1.2
            pose['ribs_r'][2] -= u * 2.0
            pose['ribs_l'][2] += u * 2.0
            pose['jaw'][0] -= max(0.0, u) * 5
        return apply

    # Idle: head low, sniffing the air, ribs heaving with soul fire.
    idle = Track([key(0), key(1.5, neck=[-16, 8, 0], head=[12, 6, 4]), key(3.0, neck=[-8, 0, 0], head=[4, 0, 0]),
                  key(4.5, neck=[-16, -8, 0], head=[12, -6, -4]), key(6.0)])
    make('idle', 6.0, idle, loop=True, extra=breathe(2.0))

    make('walk', 1.4, Track([(round(i * .07, 4), gait(i * .07, 1.4)) for i in range(21)]), loop=True)

    # Attack: coil back on the haunches, then lunge and snap the jaws shut.
    coil = feet(dict(STANCE, pelvis_pos=[0, -5.2, 3.0], pelvis=[4, 0, 0], chest=[-6, 0, 0], neck=[-20, 0, 0], head=[16, 0, 0],
                     jaw=[34, 0, 0], ribs=[4, 0, 0], tail=[-16, 0, 0], sway=[-4, 0, 0]))
    lunge = feet(dict(STANCE, pelvis_pos=[0, 0.0, -12.0], pelvis=[-6, 0, 0], chest=[2, 0, 0], neck=[2, 0, 0], head=[-4, 0, 0],
                      jaw=[46, 0, 0], tail=[8, 0, 0], sway=[8, 8, 0]), front=(0, 2.0, -14.0), hind=(0, 0, -6.0))
    lunge['fr_pitch'] = lunge['fl_pitch'] = [10, 0, 0]
    snap = dict(lunge, jaw=[2, 0, 0], head=[-10, 0, 0])
    make('attack_windup', .7, Track([key(0), (.6, dict(coil, ease='out')), (.7, coil)]))
    make('attack_active', .35, Track([(0, dict(coil, ease='in')), (.18, dict(lunge, ease='snap')), (.28, snap), (.35, snap)]))
    make('attack_recover', .7, Track([(0, snap), (.7, STANCE)]))

    # Skill - burrow: it claws into the soul sand and sinks, then bursts up out of the ground.
    dig = feet(dict(STANCE, pelvis_pos=[0, -6.0, 0], pelvis=[-6, 0, 0], chest=[-12, 0, 0], neck=[-26, 0, 0], head=[-8, 0, 0],
                    jaw=[10, 0, 0], tail=[-14, 10, 0]), front=(0, 0, -2.0))
    claw = dict(dig, fr=list(np.asarray(FEET['fr']) + (0, 5.0, -5.0)), fr_pitch=[-50, 0, 0])
    claw2 = dict(dig, fl=list(np.asarray(FEET['fl']) + (0, 5.0, -5.0)), fl_pitch=[-50, 0, 0])
    sunk = feet(dict(dig, pelvis_pos=[0, -26.0, 0], chest=[-18, 0, 0], neck=[-30, 0, 0]), dy=-20.0)
    burst = feet(dict(STANCE, pelvis_pos=[0, 9.0, -6.0], pelvis=[-22, 0, 0], chest=[-4, 0, 0], neck=[22, 0, 0], head=[12, 0, 0],
                      jaw=[48, 0, 0], ribs=[10, 0, 0], tail=[20, 0, 0], sway=[0, 10, 0]), front=(0, 9.0, -14.0), hind=(0, 6.0, -2.0))
    burst['fr_pitch'] = burst['fl_pitch'] = [-40, 0, 0]
    burst['hr_pitch'] = burst['hl_pitch'] = [-30, 0, 0]
    make('skill_windup', 1.0, Track([key(0), (.25, dig), (.4, claw), (.55, claw2), (.7, dig), (1.0, dict(sunk, ease='in'))]))
    make('skill_active', .6, Track([(0, sunk), (.3, dict(burst, ease='out')), (.6, burst)]))
    make('skill_recover', .8, Track([(0, burst), (.4, dict(STANCE, pelvis_pos=[0, -3, 0], ease='in')), (.8, STANCE)]))

    # Ultimate - soul howl: it settles on its haunches, throws its skull to the sky and the ribs splay open.
    sit = dict(STANCE, pelvis_pos=[0, -9.0, 1.0], pelvis=[18, 0, 0], spine=[4, 0, 0], chest=[2, 0, 0], neck=[10, 0, 0], head=[6, 0, 0],
               jaw=[10, 0, 0], tail=[-26, 24, 0], ribs=[8, 0, 0], meta_r=[56, 0, 0], meta_l=[56, 0, 0],
               hr=list(np.asarray(FEET['hr']) + (0, 1.2, -3.0)), hl=list(np.asarray(FEET['hl']) + (0, 1.2, -3.0)),
               fr=list(np.asarray(FEET['fr']) + (0, 0, -1.0)), fl=list(np.asarray(FEET['fl']) + (0, 0, -1.0)))
    howl = dict(sit, neck=[40, 0, 0], head=[24, 0, 0], jaw=[52, 0, 0], ribs=[34, 0, 0], tail=[-30, 0, 0], sway=[0, 12, 0])
    make('ultimate_windup', 1.2, Track([key(0), (1.0, dict(sit, ease='out')), (1.2, sit)]))
    make('ultimate_active', 2.0, Track([(0, sit), (.4, dict(howl, ease='out')), (1.7, howl), (2.0, dict(howl, ribs=[26, 0, 0]))]),
         extra=lambda pose, t: (pose['head'].__setitem__(1, pose['head'][1] + math.sin(t * 11) * 3 * min(1, t * 2)),
                                pose['jaw'].__setitem__(0, pose['jaw'][0] - math.sin(t * 17) * 2)))
    make('ultimate_recover', 1.0, Track([(0, dict(howl, ribs=[26, 0, 0])), (1.0, STANCE)]))

    # Stun: legs splay and it sprawls, skull on the ground.
    sprawl = dict(STANCE, pelvis_pos=[0, -6.0, 0], pelvis=[0, 0, 8], chest=[-4, 0, 10], neck=[-16, 10, 0], head=[-8, 14, 8], jaw=[20, 0, 0],
                  tail=[-24, 30, 0], ribs=[-2, 0, 0], meta_r=[50, 0, 0], meta_l=[50, 0, 0],
                  fr=list(np.asarray(FEET['fr']) + (4.0, 0, -5.0)), fl=list(np.asarray(FEET['fl']) + (-4.0, 0, -5.0)),
                  hr=list(np.asarray(FEET['hr']) + (4.0, 0, 2.0)), hl=list(np.asarray(FEET['hl']) + (-4.0, 0, 2.0)))
    make('stun', 2.0, Track([key(0), (.3, dict(sprawl, ease='out')), (1.7, sprawl), (2.0, STANCE)]),
         extra=lambda pose, t: pose['head'].__setitem__(2, pose['head'][2] + math.sin(t * 6) * 3))

    # Transition: a violent shake, ribs flaring as the soul fire boils over.
    def shake(pose, t):
        k = max(0.0, 1 - abs(t - 1.5) / 1.2)
        pose['chest'][2] += math.sin(t * 26) * 4 * k
        pose['neck'][1] += math.sin(t * 26 + 1) * 16 * k
        pose['head'][2] += math.sin(t * 26 + 2) * 12 * k
        pose['spine'][2] -= math.sin(t * 26) * 3 * k
    flared = dict(STANCE, pelvis_pos=[0, -1.0, 0], ribs=[30, 0, 0], neck=[12, 0, 0], head=[12, 0, 0], jaw=[42, 0, 0], tail=[-14, 0, 0],
                  sway=[0, 14, 0])
    make('transition', 3.0, Track([key(0), (.6, dict(flared, ease='out')), (2.4, flared), (3.0, STANCE)]), extra=shake)

    # Spawn: skull and forepaws claw up out of the ground; the body drags itself free.
    under = feet(dict(STANCE, pelvis_pos=[0, -30.0, 6.0], pelvis=[-26, 0, 0], chest=[-8, 0, 0], neck=[26, 0, 0], head=[10, 0, 0],
                      jaw=[30, 0, 0]), front=(0, 2.0, -8.0), hind=(0, -24.0, 0))
    under['fr_pitch'] = under['fl_pitch'] = [-30, 0, 0]
    climb = feet(dict(STANCE, pelvis_pos=[0, -12.0, 2.0], pelvis=[-18, 0, 0], chest=[-6, 0, 0], neck=[10, 0, 0], head=[4, 0, 0],
                      jaw=[20, 0, 0]), front=(0, 0, -4.0), hind=(0, -9.0, 0))
    make('spawn', 3.0, Track([(0, under), (1.2, dict(climb, ease='out')), (2.4, dict(STANCE, ease='out')), (3.0, STANCE)]))
    return out, max(b.errors) if b.errors else 0.0


def aura_clip():
    def fn(t):
        u = t / 30.0
        return {'sigil': [0, 360 * u, 0], 'wisps': {'rot': [0, -720 * u, 0], 'pos': [0, math.sin(u * math.tau * 5) * 1.2, 0]}}
    return Clip('aura', 30.0, fn, loop=True, step=1 / 10, bones=AURA)


def build():
    f = sculpt()
    motion, worst = clips(f)
    body = [b for b in f.bones if b not in AURA and b not in MARKERS and b != 'hi_gaze']
    for clip in motion:
        clip.bones = body
    assert [c.name for c in motion] == list(CLIPS)
    model, atlas, stats = build_realm(f, motion + [aura_clip()], MODEL, [[-12, 0, -46], [12, 36, 54]],
                                      meta=dict(evergarden_revision=REVISION, evergarden_scale=SCALE), density=9.0)
    return model, atlas, stats, worst, f
