"""The Crystal Moth Queen, sovereign of the abandoned Crystal City in the End.

A vast moth drawn to the city's dead light. Four wings of stained crystal (lilac and
white panes in gilded leading, each forewing marked with a diamond eyespot) carry
real shards along their margins; the pearl-and-gold armoured thorax holds a cracked
magenta heartcrystal; a ruff and crown of crystal frame glowing compound eyes;
plumed antennae curl back to crystal drops; raised mantis forelegs hook forward and
the segmented abdomen ends in a crystal stinger. Loose shards circle it and a faint
sigil marks the ground below; those aura bones loop on their own lane.
"""
import math
import random

import numpy as np

from boss_forge import Clip, Forge, Material, Track, basis, bezier, rot, unit
from realm_forge import CLIPS, build_realm

MODEL = 'crystal_moth'
TITLE = 'Crystal Moth Queen'
REVISION = 'crystal-moth-v1'
SCALE = 3.5

CRYSTAL = ['#2b1640', '#4a2a6e', '#6e44a0', '#9468c8', '#b996e6', '#d8c2f6', '#f1e8ff', '#ffffff']
PANE = ['#3d2560', '#5d3d8c', '#8361b6', '#a687d6', '#c5aeec', '#e0d2fa', '#f5eeff', '#ffffff']
PINK = ['#3d0a35', '#6e1460', '#a3238c', '#d23cb4', '#f06ed2', '#ffaee9', '#ffe6f8']
PEARL = ['#3a3044', '#5a4d63', '#7f7287', '#a69bab', '#c9c0cb', '#e4dee4', '#f6f2f4', '#ffffff']
GOLD = ['#3a2208', '#5e3a0e', '#8a5a18', '#b8812a', '#dcaa45', '#f2d27a', '#fff0b8']
NIGHT = ['#120c18', '#1d1426', '#2a1d37', '#3a284b', '#4e3663', '#664a7e', '#83639c']
MATERIALS = {
    'chitin': Material(PEARL, 'plate', accent=PINK),
    'gold': Material(GOLD, 'trim', density=1.1),
    'crystal': Material(CRYSTAL, 'crystal', glow=True, density=1.1),
    'heart': Material(PINK, 'crystal', glow=True, density=1.4),
    'wing': Material(PANE, 'mosaic', accent=PINK, trim=GOLD, density=.75, lead='#2a1840'),
    'hindwing': Material(PANE[:2] + ['#7a4fb0', '#9f6fd2', '#c39aec', '#e2c8fa', '#f6ecff', '#ffffff'], 'mosaic', accent=PINK, trim=GOLD,
                         density=.75, lead='#2a1840'),
    'shard': Material(CRYSTAL, 'crystal', glow=True, density=.9),
    'night': Material(NIGHT, 'horn'),
    'eye': Material(['#9a3cff', '#c47cff', '#e8c6ff', '#ffffff'], 'glow', glow=True, density=1.6),
    'compound': Material(['#1a0630', '#33105a', '#55208e', '#7e38c6', '#a865f0', '#d3a8ff', '#f4e6ff'], 'crystal', glow=True, density=1.8),
    'glow': Material(PINK, 'glow', glow=True, density=.7),
}
SIDES = (('r', 1), ('l', -1))
AURA = ('shards', 'sigil')
MARKERS = ('root', 'facing', 'head_forward', 'fx_heart', 'fx_eyes', 'fx_stinger', 'fx_wing_r', 'fx_wing_l')
THORAX = np.array([0, 34.0, 0])
HEAD = np.array([0, 36.6, -6.6])
WING = {  # root, outward axis, up axis of the wing plane
    'fore': (np.array([2.6, 37.6, 1.0]), unit((1, .2, .06)), unit((-.12, 1, .18))),
    'hind': (np.array([2.4, 33.6, 3.0]), unit((1, -.3, .14)), unit((.22, 1, .16))),
}
FORE = [(0, 0), (4, 2.6), (11, 5.4), (18, 7.8), (24, 9.6), (28.6, 10.6), (30.6, 9.0), (29.4, 3.4), (26.4, -2.0), (21.6, -6.0),
        (15.2, -7.6), (9.0, -6.6), (4.2, -4.2), (1.2, -1.6)]
HIND = [(0, 0), (5.2, 2.6), (11.4, 3.6), (16.4, 2.0), (18.8, -1.6), (17.6, -5.6), (12.8, -8.2), (7.0, -7.8), (2.8, -4.8), (.6, -1.8)]
SPLIT = {'fore': 15.0, 'hind': 10.0}
PREVIEW = dict(close=(24, (0, 36)), clip_span=78, clip_center=(0, 30), time=0.0)


def wing_point(kind, s, uv):
    root, u, v = WING[kind]
    p = root + u * uv[0] + v * uv[1]
    return np.array([s * p[0], p[1], p[2]])


def skeleton(f):
    f.bone('root', None, [0, 0, 0])
    f.bone('facing', 'root', [0, 32, -30])
    f.bone('thorax', 'root', THORAX)
    f.bone('hi_gaze', 'thorax', HEAD)
    f.bone('head', 'hi_gaze', HEAD)
    f.bone('head_forward', 'head', [0, 36.6, -30])
    for side, s in SIDES:
        f.bone(f'antenna_{side}', 'head', [s * 1.3, 40.4, -9.4])
        f.bone(f'antenna_tip_{side}', f'antenna_{side}', [s * 8.4, 51.6, -8.4])
        for kind in ('fore', 'hind'):
            f.bone(f'{kind}wing_{side}', 'thorax', wing_point(kind, s, (0, 0)))
            f.bone(f'{kind}wing_tip_{side}', f'{kind}wing_{side}', wing_point(kind, s, (SPLIT[kind], 0)))
        for k, z in enumerate((-4.2, -1.0, 2.0)):
            f.bone(f'leg{k + 1}_{side}', 'thorax', [s * 2.6, 31.0, z])
            f.bone(f'shin{k + 1}_{side}', f'leg{k + 1}_{side}', leg_knee(k, s))
    f.bone('abdomen', 'thorax', [0, 31.4, 4.2])
    f.bone('abdomen2', 'abdomen', [0, 27.0, 9.4])
    f.bone('stinger', 'abdomen2', [0, 22.4, 13.2])
    f.bone('shards', 'root', [0, 30, 0])
    f.bone('sigil', 'root', [0, .12, 0])
    f.bone('fx_heart', 'thorax', [0, 33.4, -6.0])
    f.bone('fx_eyes', 'head', [0, 37.8, -10.6])
    f.bone('fx_stinger', 'stinger', [0, 14.0, 19.0])
    for side, s in SIDES:
        f.bone(f'fx_wing_{side}', f'forewing_tip_{side}', wing_point('fore', s, (29, 9)))


def leg_knee(k, s):
    return np.array([(s * 6.6, 34.4, -9.0), (s * 7.6, 29.2, -1.6), (s * 7.0, 28.6, 4.6)][k])


# ---------------------------------------------------------------------------
# Body: thorax armour, heartcrystal, ruff, abdomen


def body(f):
    b = 'thorax'
    f.box(b, (-3.9, 30.6, -5.0), (7.8, 7.2, 9.6), 'chitin', label='thorax', engrave='scales', runes=True)
    f.box(b, (-4.1, 33.6, -5.2), (8.2, .55, 10.0), 'gold', label='thorax_band')
    f.box(b, (-3.0, 37.6, -4.0), (6.0, 1.6, 7.6), 'chitin', label='thorax_top', engrave='lines')
    for side, s in SIDES:
        f.cube(b, (s * 3.9, 35.6, .6), (1.4, 4.6, 6.4), 'chitin', rot((0, 0, -s * 14)), label='wing_mount', engrave='chevron')
        f.beam(b, (s * 4.4, 38.0, -2.4), (s * 4.4, 38.0, 3.8), .6, .6, 'gold', label='mount_trim')
    # Heartcrystal in a gilded claw setting, cracked and glowing.
    f.cube(b, (0, 33.4, -5.4), (3.6, 3.6, 2.2), 'heart', rot((0, 0, 45)), label='heart', bands=2)
    for k in range(4):
        a = math.radians(45 + k * 90)
        f.beam(b, (math.cos(a) * 1.9, 33.4 + math.sin(a) * 1.9, -5.4), (math.cos(a) * 3.2, 33.4 + math.sin(a) * 3.2, -5.0), .5, .5, 'gold',
               label='heart_claw')
    f.box(b, (-2.6, 30.4, -5.6), (5.2, .6, .8), 'gold', label='heart_base')
    # Crystal ruff fanning around the neck.
    for k in range(11):
        a = math.pi * (k / 10) - math.pi / 2
        d = np.array([math.sin(a), math.cos(a) * .8 + .25, -.35])
        base = np.array([0, 36.4, -4.6]) + np.array([d[0] * 3.0, d[1] * 2.2, 0])
        length = 4.2 + 2.2 * math.cos(a) ** 2
        f.spike(b, base, base + unit(d) * length, 1.1 if k % 2 else 1.4, 'crystal', steps=3, label='ruff', bands=2)
    # Back crest of crystals between the wing roots.
    for k, (y, z, h) in enumerate(((38.6, -2.0, 4.6), (38.4, 1.0, 5.8), (37.4, 3.6, 4.2))):
        f.spike(b, (0, y, z), (0, y + h, z + 1.6), 1.6, 'crystal', steps=3, label='crest', bands=2)
    ab = (('abdomen', [(31.4, 4.2), (29.6, 6.6), (27.6, 8.6)], 6.4), ('abdomen2', [(27.0, 9.4), (25.2, 11.0), (23.4, 12.4)], 4.6))
    for bone, rings, w0 in ab:
        for k, (y, z) in enumerate(rings):
            w = w0 - k * .7
            f.cube(bone, (0, y, z), (w, w * .9, 2.4), 'chitin', rot((-40 - k * 6, 0, 0)), label=f'segment{bone[-1]}{k}', engrave='lines')
            f.cube(bone, (0, y - .9, z + .7), (w + .2, .45, 2.5), 'gold', rot((-40 - k * 6, 0, 0)), label=f'segment_trim{bone[-1]}{k}')
            for side, s in SIDES:
                f.spike(bone, (s * w * .45, y, z), (s * (w * .45 + 2.4), y + .8, z + 1.2), .9, 'crystal', steps=2, label='side_shard')
    f.taper('stinger', [(0, 22.6, 13.0), (0, 19.6, 15.4), (0, 16.6, 17.4), (0, 13.4, 19.2)], [2.4, 1.5, .6], [2.4, 1.5, .6], 'crystal',
            z_hint=(1, 0, 0), label='stinger', overlap=.3, bands=2)


def head(f):
    b = 'head'
    f.box(b, (-2.9, 34.8, -11.6), (5.8, 5.2, 5.2), 'chitin', label='mask', engrave='lines')
    f.cube(b, (0, 34.0, -11.4), (4.0, 2.8, 2.8), 'chitin', rot((24, 0, 0)), label='muzzle', plain=True)
    f.beam(b, (0, 40.2, -10.6), (0, 34.6, -12.4), 1.0, .7, 'gold', z_hint=(1, 0, 0), label='mask_ridge')
    f.box(b, (-1.6, 35.2, -12.05), (3.2, 2.6, .5), 'night', label='face_plate')
    for side, s in SIDES:
        # Bulging faceted compound eyes, a pupil-glow at their centre.
        f.cube(b, (s * 3.2, 37.8, -10.4), (3.6, 3.8, 3.8), 'compound', rot((0, s * 24, s * 8)), label='compound_eye', bands=4)
        f.cube(b, (s * 3.9, 38.0, -12.0), (1.2, 1.2, .6), 'eye', rot((0, s * 24, 45)), label='eye_glint')
        f.taper(b, [(s * 1.3, 34.2, -12.2), (s * 2.6, 32.6, -14.6), (s * 2.2, 31.0, -16.8), (s * .6, 30.6, -17.6)], [1.3, .9, .5],
                [1.1, .8, .45], 'night', z_hint=(0, 1, 0), label='mandible', overlap=.25)
        f.beam(b, (s * 2.8, 40.0, -11.6), (s * .4, 39.4, -12.0), .5, .5, 'gold', label='mask_trim')
    for k, (dx, h) in enumerate(((0, 6.2), (1.6, 4.4), (-1.6, 4.4), (2.8, 3.0), (-2.8, 3.0))):
        f.spike(b, (dx, 39.6, -9.8 + abs(dx) * .3), (dx * 1.5, 39.6 + h, -8.6 + abs(dx) * .6), 1.3 if k == 0 else 1.0, 'crystal', steps=3,
                label='crown', bands=2)
    for side, s in SIDES:
        root, mid = np.array([s * 1.3, 40.4, -9.4]), np.array([s * 8.4, 51.6, -8.4])
        shaft = [bezier([root, (s * 3.2, 46.8, -12.0), mid], i / 4) for i in range(5)]
        f.taper(f'antenna_{side}', shaft, [.6, .55, .5, .45], [.6, .55, .5, .45], 'night', z_hint=(0, 0, 1), label='antenna', overlap=.2)
        tip = [bezier([mid, (s * 13.6, 55.0, -2.0), (s * 14.4, 50.6, 4.4)], i / 4) for i in range(5)]
        f.taper(f'antenna_tip_{side}', tip, [.45, .4, .35, .3], [.45, .4, .35, .3], 'night', z_hint=(0, 0, 1), label='antenna', overlap=.2)
        f.cube(f'antenna_tip_{side}', tip[-1] + (0, -.8, .4), (1.4, 2.0, 1.4), 'crystal', rot((0, 45, 0)), label='antenna_drop')
        # Plumes: comb sheets along each antenna.
        for bone, pts, scale in ((f'antenna_{side}', shaft, 1.0), (f'antenna_tip_{side}', tip, .8)):
            for i in range(len(pts) - 1):
                a, c = pts[i], pts[i + 1]
                along = unit(c - a)
                side_dir = unit(np.cross(along, (0, 0, 1)) if abs(along[2]) < .9 else np.cross(along, (1, 0, 0)))
                comb = []
                for j in range(7):
                    u = j / 6
                    p = a + (c - a) * u
                    comb.append(p + side_dir * (1.6 * scale if j % 2 else .3))
                comb += [c - side_dir * .2, a - side_dir * .2]
                f.sheet(bone, comb, 'night', thickness=.06, label='plume')


def legs(f):
    for side, s in SIDES:
        for k, z in enumerate((-4.2, -1.0, 2.0)):
            hip = np.array([s * 2.6, 31.0, z])
            knee = leg_knee(k, s)
            tip = [(s * 4.4, 31.0, -14.6), (s * 8.6, 20.6, -2.6), (s * 7.6, 19.6, 7.6)][k]
            f.box(f'leg{k + 1}_{side}', hip - .9, (1.8, 1.8, 1.8), 'gold', label='coxa')
            f.beam(f'leg{k + 1}_{side}', hip, knee, 1.1, 1.1, 'night', label='femur')
            f.cube(f'leg{k + 1}_{side}', knee, (1.3, 1.3, 1.3), 'gold', rot((0, 45, 0)), label='knee')
            f.taper(f'shin{k + 1}_{side}', [knee, (knee + np.array(tip)) / 2, tip], [.85, .55], [.85, .55], 'night', z_hint=(0, 0, 1),
                    label='tibia', overlap=.2)
            if k == 0:
                # Mantis forelegs: a serrated hook.
                for j in range(3):
                    p = knee + (np.array(tip) - knee) * (.3 + j * .22)
                    f.spike(f'shin1_{side}', p, p + (0, -1.4, -.4), .5, 'crystal', steps=2, label='hook_tooth')
            else:
                f.spike(f'shin{k + 1}_{side}', tip, np.array(tip) + (0, -1.6, -.6), .55, 'night', steps=2, label='claw')


# ---------------------------------------------------------------------------
# Wings: stained-crystal membranes, gilded veins and crystal margins


def edges(outline):
    """Leading edge (root to tip) and trailing edge (root to tip) of a wing outline."""
    tip = max(range(len(outline)), key=lambda i: outline[i][0])
    lead = outline[:tip + 1]
    trail = [outline[0]] + outline[tip:][::-1]
    return lead, sorted(trail)


def wings(f):
    for side, s in SIDES:
        for kind, outline, mat, eye in (('fore', FORE, 'wing', (.62, .5, .2, .3)), ('hind', HIND, 'hindwing', (.7, .45, .26, .3))):
            base, tip = f'{kind}wing_{side}', f'{kind}wing_tip_{side}'
            split = SPLIT[kind]
            lead, trail = edges(outline)
            top = float(np.interp(split, [p[0] for p in lead], [p[1] for p in lead])) - .15
            bottom = float(np.interp(split, [p[0] for p in trail], [p[1] for p in trail])) + .15
            inner = order([p for p in outline if p[0] < split] + [(split + .4, bottom), (split + .4, top)])
            outer = order([(split - .4, top), (split - .4, bottom)] + [p for p in outline if p[0] > split])
            up = WING[kind][2] * np.array([s, 1, 1])
            for bone, poly, piece in ((base, inner, 'in'), (tip, outer, 'out')):
                kw = dict(eye=eye) if (piece == 'out') == (kind == 'fore') else {}
                f.sheet(bone, [wing_point(kind, s, q) for q in poly], mat, thickness=.12, up=up, facing=(0, 0, s),
                        label=f'{kind}_{piece}', root=(0.0, .5), cell=1.5 if kind == 'fore' else 1.3, **kw)
            veins(f, kind, s, base, tip, outline, split)
            margin(f, kind, s, tip, outline, split)
        # A long crystal streamer trailing from each hindwing.
        start = wing_point('hind', s, (18.0, -4.6))
        f.taper(f'hindwing_tip_{side}', [start, start + (s * 1.4, -4.6, 2.2), start + (s * 1.8, -9.6, 4.6), start + (s * 1.2, -13.0, 6.4)],
                [1.8, 1.2, .5], [1.0, .7, .3], 'crystal', z_hint=(0, 0, 1), label='streamer', overlap=.25, bands=2)


def order(points):
    c = np.mean(points, axis=0)
    return sorted(points, key=lambda p: math.atan2(p[1] - c[1], p[0] - c[0]))


def veins(f, kind, s, base, tip, outline, split):
    """Gilded veins: a thick leading costa and radial veins fanning from the root."""
    lead, _ = edges(outline)
    for a, b in zip(lead, lead[1:]):
        bone = base if (a[0] + b[0]) / 2 < split else tip
        f.beam(bone, wing_point(kind, s, a), wing_point(kind, s, b), .8 if kind == 'fore' else .6, .7, 'gold', label='costa')
    targets = [q for q in outline[len(edges(outline)[0]):] if q[0] > 2]
    for p in targets[::1]:
        mid = (p[0] * .5, p[1] * .5 + (1.0 if p[1] > 0 else -.6))
        f.beam(base, wing_point(kind, s, (0.6, 0)), wing_point(kind, s, mid), .38, .3, 'gold', label='vein')
        if p[0] > split:
            m2 = (split + .2, mid[1] + (p[1] - mid[1]) * (split - mid[0]) / max(1e-6, p[0] - mid[0]))
            f.beam(base, wing_point(kind, s, mid), wing_point(kind, s, m2), .32, .26, 'gold', label='vein')
            f.beam(tip, wing_point(kind, s, m2), wing_point(kind, s, p), .3, .24, 'gold', label='vein')
        else:
            f.beam(base, wing_point(kind, s, mid), wing_point(kind, s, p), .32, .26, 'gold', label='vein')


def margin(f, kind, s, bone, outline, split):
    """Crystal shards jutting from the outer margin, along the wing plane."""
    root, u, v = WING[kind]
    edge = [p for p in outline if p[0] > split + 1] if kind == 'fore' else [p for p in outline if p[0] > split - 2]
    c = np.mean(outline, axis=0)
    for i, p in enumerate(edge):
        out = unit(np.array(p, float) - c)
        base = wing_point(kind, s, p)
        tip_uv = np.array(p) + out * (3.4 if i % 2 == 0 else 2.2)
        f.spike(bone, base, wing_point(kind, s, tip_uv), 1.4 if i % 2 == 0 else 1.0, 'shard', steps=2, label='margin_shard', bands=2,
                z_hint=np.cross(u, v) * np.array([s, 1, 1]))


# ---------------------------------------------------------------------------
# Aura: orbiting shards and the ground sigil


def aura(f):
    rng = random.Random(21)
    for k in range(9):
        a = math.tau * k / 9 + rng.uniform(-.2, .2)
        r = rng.uniform(20, 25)
        c = np.array([math.cos(a) * r, rng.uniform(16, 44), math.sin(a) * r])
        d = unit(np.array([rng.uniform(-.3, .3), 1, rng.uniform(-.3, .3)]))
        length = rng.uniform(3.0, 5.4)
        f.spike('shards', c - d * length / 2, c + d * length / 2, rng.uniform(1.0, 1.6), 'shard', steps=2, label='orbit_shard', bands=2)
    s0 = np.array([0, .12, 0])
    f.ring('sigil', s0, 20.0, .4, .1, 'glow', segments=30, normal=(0, 1, 0))
    for k in range(8):
        a = math.tau * k / 8
        d = np.array([math.cos(a), 0, math.sin(a)])
        q = np.array([math.cos(a + math.pi / 8), 0, math.sin(a + math.pi / 8)])
        f.beam('sigil', s0 + d * 20.0, s0 + q * 13.0, .32, .1, 'glow', z_hint=(0, 1, 0), label='sigil_star')
        f.beam('sigil', s0 + q * 13.0, s0 + np.array([math.cos(a + math.pi / 4), 0, math.sin(a + math.pi / 4)]) * 20.0, .32, .1, 'glow',
               z_hint=(0, 1, 0), label='sigil_star')


def sculpt():
    for m in MATERIALS.values():
        m.ramp_hex = ['#%02x%02x%02x' % tuple(int(v) for v in c) for c in m.ramp]
    f = Forge(MODEL, MATERIALS, seed=8181)
    skeleton(f)
    body(f)
    head(f)
    legs(f)
    wings(f)
    aura(f)
    return f


# ---------------------------------------------------------------------------
# Motion: a hovering queen. Wings beat as staggered pairs; everything else trails.


STANCE = dict(thorax=[-6, 0, 0], thorax_pos=[0, 0, 0], head=[8, 0, 0], abdomen=[6, 0, 0], abdomen2=[6, 0, 0], stinger=[4, 0, 0],
              fore=[-2, 4, 0, 0], hind=[-6, 2, 0, 0], beat=[22, 0, 0], antenna=[0, 0, 0],
              leg1=[-30, -20, 0], legs=[0, 0, 0])


def key(t, base=STANCE, **changes):
    d = dict(base)
    d.update(changes)
    return (t, d)


def pose_at(tr, t, phase):
    """phase drives the wingbeat; tracks give the held shape around it."""
    th = tr('thorax', t)
    pose = {'thorax': {'rot': list(th), 'pos': list(tr('thorax_pos', t))}, 'head': list(tr('head', t))}
    amp, _, _ = tr('beat', t)
    beat = math.sin(phase)
    for i, (kind, weight, lag) in enumerate((('fore', 1.0, 0.0), ('hind', .75, .55))):
        lift, sweep, twist, fold = tr(kind, t)
        b = math.sin(phase - lag) * amp * weight
        for side, s in SIDES:
            pose[f'{kind}wing_{side}'] = [twist + math.cos(phase - lag) * amp * .12, s * sweep, s * (lift + b)]
            pose[f'{kind}wing_tip_{side}'] = [0, s * fold * .5, s * (-fold + math.sin(phase - lag - .9) * amp * .35)]
    bob = math.sin(phase + math.pi) * .4 * amp / 30
    pose['thorax']['pos'][1] += bob
    ab = tr('abdomen', t)
    pose['abdomen'] = [ab[0] + math.sin(phase * .5) * 3, ab[1], ab[2]]
    pose['abdomen2'] = list(tr('abdomen2', t) + np.array([math.sin(phase * .5 - .6) * 4, 0, 0]))
    pose['stinger'] = list(tr('stinger', t) + np.array([math.sin(phase * .5 - 1.2) * 5, 0, 0]))
    sway, curl, _ = tr('antenna', t)
    for side, s in SIDES:
        pose[f'antenna_{side}'] = [sway + math.sin(phase * .5 + s) * 4, 0, s * curl]
        pose[f'antenna_tip_{side}'] = [sway * .6 + math.sin(phase * .5 + s - .8) * 6, 0, s * curl * .6]
        l1 = tr('leg1', t)
        pose[f'leg1_{side}'] = [l1[0], s * l1[2], s * l1[1] * .3]
        pose[f'shin1_{side}'] = [l1[1], 0, 0]
        dangle, spread, _ = tr('legs', t)
        for k in (2, 3):
            pose[f'leg{k}_{side}'] = [dangle + math.sin(phase * .5 + k) * 3, 0, s * spread]
            pose[f'shin{k}_{side}'] = [dangle * .6 + math.sin(phase * .5 + k - .7) * 4, 0, 0]
    return pose


def clips(forge):
    out = []

    def make(name, length, track, loop=False, beats=None, extra=None):
        cycles = beats if beats is not None else max(1, round(length / .8))

        def fn(t):
            pose = pose_at(track, t, t / length * math.tau * cycles)
            if extra:
                extra(pose, t)
            return pose
        out.append(Clip(name, length, fn, loop=loop))

    make('idle', 2.4, Track([key(0), key(2.4)]), loop=True, beats=2)
    cruise = dict(STANCE, thorax=[-22, 0, 0], head=[16, 0, 0], abdomen=[16, 0, 0], abdomen2=[10, 0, 0], fore=[10, -6, -6, 4],
                  hind=[2, -8, -4, 4], beat=[38, 0, 0], antenna=[-14, 6, 0], legs=[24, 8, 0], leg1=[-10, -40, 0])
    make('walk', 1.2, Track([key(0, cruise), key(1.2, cruise)]), loop=True, beats=2)

    # Attack - dive slash: rise with wings high and claws open, then stoop forward and rake.
    rise = dict(STANCE, thorax=[10, 0, 0], thorax_pos=[0, 5.0, 3.0], head=[-4, 0, 0], fore=[52, 10, 0, -8], hind=[36, 6, 0, -6],
                beat=[8, 0, 0], leg1=[-80, -70, 0], antenna=[10, 10, 0])
    dive = dict(STANCE, thorax=[-36, 0, 0], thorax_pos=[0, -7.0, -12.0], head=[22, 0, 0], abdomen=[28, 0, 0], fore=[-6, -30, -8, 6],
                hind=[-10, -26, -6, 6], beat=[6, 0, 0], leg1=[30, 20, 0], legs=[30, 6, 0], antenna=[-26, 4, 0])
    make('attack_windup', .8, Track([key(0), (.65, dict(rise, ease='out')), (.8, rise)]), beats=1)
    make('attack_active', .4, Track([(0, dict(rise, ease='in')), (.2, dict(dive, ease='snap')), (.4, dive)]), beats=0)
    make('attack_recover', .8, Track([(0, dive), (.8, STANCE)]), beats=1)

    # Skill - shard volley: wings cup forward and shiver, then snap to fling their margins.
    cup = dict(STANCE, thorax=[12, 0, 0], thorax_pos=[0, 2.0, 3.0], head=[-6, 0, 0], fore=[30, 34, 10, -10], hind=[18, 30, 8, -8],
               beat=[4, 0, 0], leg1=[-60, -50, 0], antenna=[16, 14, 0])
    fling = dict(STANCE, thorax=[-14, 0, 0], thorax_pos=[0, 0, -4.0], head=[10, 0, 0], fore=[6, -40, -10, 8], hind=[-4, -36, -8, 8],
                 beat=[4, 0, 0], leg1=[0, -10, 0], antenna=[-20, 0, 0])
    make('skill_windup', 1.0, Track([key(0), (.8, dict(cup, ease='out')), (1.0, cup)]), beats=6)
    make('skill_active', .5, Track([(0, dict(cup, ease='in')), (.18, dict(fling, ease='snap')), (.5, fling)]), beats=0)
    make('skill_recover', .8, Track([(0, fling), (.8, STANCE)]), beats=1)

    # Ultimate - prismatic molt: wings wrap into a chrysalis, then burst open, body arched to the sky.
    wrap = dict(STANCE, thorax=[8, 0, 0], thorax_pos=[0, 3.0, 0], head=[-20, 0, 0], abdomen=[-10, 0, 0], abdomen2=[-12, 0, 0],
                fore=[30, 70, 20, 30], hind=[10, 66, 16, 26], beat=[2, 0, 0], leg1=[-90, -90, 0], legs=[-20, -10, 0], antenna=[30, 20, 0])
    burst = dict(STANCE, thorax=[24, 0, 0], thorax_pos=[0, 6.0, 2.0], head=[-24, 0, 0], abdomen=[-24, 0, 0], abdomen2=[-16, 0, 0],
                 stinger=[-10, 0, 0], fore=[40, -24, -16, -14], hind=[26, -26, -12, -12], beat=[6, 0, 0], leg1=[-40, -20, 0],
                 legs=[-30, 20, 0], antenna=[36, -10, 0])
    make('ultimate_windup', 1.6, Track([key(0), (1.3, dict(wrap, ease='out')), (1.6, wrap)]), beats=1)
    make('ultimate_active', 1.6, Track([(0, dict(wrap, ease='in')), (.22, dict(burst, ease='snap')), (1.6, burst)]), beats=2)
    make('ultimate_recover', 1.2, Track([(0, burst), (1.2, STANCE)]), beats=1)

    # Stun: wings droop and it sinks, fluttering weakly.
    droop = dict(STANCE, thorax=[-20, 0, 14], thorax_pos=[0, -7.5, 0], head=[20, 10, 0], abdomen=[24, 0, 0], abdomen2=[18, 0, 0],
                 fore=[-30, -10, 10, 20], hind=[-30, -10, 10, 16], beat=[10, 0, 0], leg1=[20, 30, 0], legs=[30, 20, 0], antenna=[-30, -10, 0])
    make('stun', 2.0, Track([key(0), (.4, dict(droop, ease='out')), (1.7, droop), (2.0, STANCE)]), beats=4)

    def spin(pose, t):
        pose['thorax']['rot'][1] += 360 * max(0.0, min(1.0, (t - .8) / 1.4)) ** 2 * (1 if t < 2.2 else 1)
    make('transition', 3.0, Track([key(0), (.8, dict(wrap, ease='out')), (2.2, dict(burst, ease='snap')), (3.0, STANCE)]), beats=3, extra=spin)

    high = dict(wrap, thorax_pos=[0, 18.0, 0])
    make('spawn', 3.0, Track([(0, high), (1.6, dict(high, thorax_pos=[0, 6.0, 0], ease='in')), (2.2, dict(burst, ease='snap')),
                              (3.0, STANCE)]), beats=2)
    return out, 0.0


def aura_clip():
    def fn(t):
        u = t / 24.0
        return {'shards': {'rot': [0, 360 * u, 0], 'pos': [0, math.sin(u * math.tau * 4) * 1.4, 0]}, 'sigil': [0, -360 * u, 0]}
    return Clip('aura', 24.0, fn, loop=True, step=1 / 10, bones=AURA)


def build():
    f = sculpt()
    motion, worst = clips(f)
    body_bones = [b for b in f.bones if b not in AURA and b not in MARKERS and b != 'hi_gaze']
    for clip in motion:
        clip.bones = body_bones
    assert [c.name for c in motion] == list(CLIPS)
    model, atlas, stats = build_realm(f, motion + [aura_clip()], MODEL, [[-10, 20, -12], [10, 44, 16]],
                                      meta=dict(evergarden_revision=REVISION, evergarden_scale=SCALE), density=8.0)
    return model, atlas, stats, worst, f
