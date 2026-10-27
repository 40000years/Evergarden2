"""Arkhaia, the Dreadrose Sovereign: sculpt, paint and animate the world-boss rig.

Replaces the original faction-preview mesh. The bone and clip contract used by the
combat AI, BossHitbox and the Bedrock bridge is preserved: body bones keep their
names, 'sword'/'sword_tip' carry the blade, 'hi_gaze' owns head tracking, 'facing'
marks the native front and all 24 clips keep the lengths in arkhaia-combat.properties.
Aura bones (dread halo, shoulder flames, floor sigil) are animated only by the
separate looping 'aura' clip, so they keep turning while combat clips change.
"""
import argparse
import copy
import json
import math
from pathlib import Path

import numpy as np

from boss_forge import Forge, Material, basis, bezier, build_model, dump, rot, unit

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'evergarden/src/main/resources/modelengine'
REVISION = 'arkhaia-dreadrose-sovereign-v1'
SCALE = 3.2
TIMING = dict(line.split('=', 1) for line in (OUT / 'arkhaia-combat.properties').read_text().splitlines()
              if line and not line.startswith('#'))


def timing(action):
    return [int(value) / 20 for value in TIMING[action].split(',')]


GHOST = ['#2a0a52', '#4b14a0', '#7a2fe6', '#a560ff', '#cc9cff', '#ecd8ff', '#ffffff']
CRIMSON_GLOW = ['#3a0410', '#6e0a20', '#a3122f', '#d42445', '#ff5a72', '#ffb3bf']
STEEL = ['#25223a', '#3b3656', '#565073', '#77709a', '#9d96c0', '#c8c2e6', '#efeaff']
CRIMSON = ['#1f040b', '#3a0814', '#5e0f22', '#8a1a33', '#b62c47', '#de4d63']
MATERIALS = {
    'obsidian': Material(['#0d0914', '#171022', '#211830', '#2c2140', '#3a2c54', '#4d3c6c', '#66528c', '#8a74b4'],
                         'plate', accent=GHOST),
    'blackiron': Material(['#0e0d13', '#17151f', '#211e2c', '#2c283b', '#3a354d', '#4c4663', '#615a7d'], 'plate',
                          density=.9),
    'steel': Material(STEEL, 'trim'),
    'crimson': Material(CRIMSON, 'plate'),
    'cloth': Material(['#0b0810', '#130e1b', '#1c1527', '#271d35', '#342747', '#45345c'], 'cloth', trim=STEEL,
                      accent=CRIMSON_GLOW, density=.85),
    'membrane': Material(['#100a16', '#1b1026', '#281636', '#371d49', '#4a265f', '#62317a', '#7f4399'], 'membrane',
                         accent=['#6e0a20', '#c2234a'], density=.75),
    'horn': Material(['#0b0910', '#15111d', '#211a2c', '#2e253d', '#41354f', '#5a4a6c', '#7c6a92', '#a08cb8'], 'horn'),
    'ghostflame': Material(GHOST, 'glow', glow=True, density=.8),
    'flame': Material(GHOST[:-1] + ['#f6eaff'], 'flame', glow=True, density=.7),
    'runes': Material(['#160828', '#2c0d54', '#5a1bb0', '#9a52ff', '#e1c6ff', '#ffffff'], 'runes', glow=True, density=1.4),
    'eye': Material(['#b9a8ff', '#e6deff', '#ffffff'], 'glow', glow=True, density=1.6),
    'rose': Material(CRIMSON_GLOW, 'gem', glow=True, density=1.5),
    'banner': Material(CRIMSON, 'cloth', trim=STEEL, density=1.2),
    'leather': Material(['#100c14', '#1a1420', '#251c2e', '#31263c', '#3e3049', '#4d3c5a'], 'leather'),
    'blade': Material(['#0e0c14', '#1b1824', '#2b2738', '#433d56', '#625a7c', '#8c84a8', '#c4bde0'], 'blade'),
}

SIDES = (('r', 1), ('l', -1))
AURA = ('dread_halo', 'flame_r', 'flame_l', 'ember_ring')
MARKERS = ('root', 'facing', 'head_forward', 'sword_tip', 'hi_gaze', 'fx_core', 'fx_eyes', 'fx_blade', 'fx_wing_r', 'fx_wing_l',
           'fx_fan_r', 'fx_fan_l')


def joints():
    j = dict(pelvis=(0, 18.5, 0), chest=(0, 21.5, 0), neck=(0, 31.2, .2))
    for side, s in SIDES:
        j.update({f'shoulder_{side}': (s * 7.0, 29.0, .3), f'elbow_{side}': (s * 7.6, 21.6, .6),
                  f'wrist_{side}': (s * 7.8, 14.6, 0), f'hip_{side}': (s * 3.0, 18.2, .2),
                  f'knee_{side}': (s * 3.2, 10.4, -.3), f'ankle_{side}': (s * 3.3, 2.3, .4),
                  f'wing_{side}': (s * 2.6, 27.8, 4.6), f'wing_elbow_{side}': (s * 10.5, 33.0, 8.4),
                  f'wing_wrist_{side}': (s * 19.0, 36.0, 9.6)})
    return {k: np.array(v, float) for k, v in j.items()}


J = joints()
GRIP_X, GRIP_Z, FIST_Y = 7.75, -.35, 12.9
TIP = np.array([GRIP_X, -9.0, GRIP_Z - .45])


def skeleton(f):
    f.bone('root', None, [0, 0, 0])
    f.bone('facing', 'root', [0, 18, -16])
    f.bone('pelvis', 'root', J['pelvis'])
    f.bone('chest', 'pelvis', J['chest'])
    f.bone('hi_gaze', 'chest', J['neck'])
    f.bone('head', 'hi_gaze', J['neck'])
    f.bone('head_forward', 'head', [0, 31.2, -7.0])  # level with the neck pivot: gaze direction marker
    f.bone('dread_halo', 'chest', [0, 35.4, 7.2])
    f.bone('cloak', 'chest', [0, 30.6, 4.0])
    f.bone('cloak_mid', 'cloak', [0, 18.6, 4.9])
    f.bone('cloak_tip', 'cloak_mid', [0, 9.2, 5.6])
    for side, s in SIDES:
        f.bone(f'shoulder_{side}', 'chest', J[f'shoulder_{side}'])
        f.bone(f'flame_{side}', f'shoulder_{side}', [s * 8.0, 32.45, .6])
        f.bone(f'arm_{side}', 'chest', J[f'shoulder_{side}'])
        f.bone(f'forearm_{side}', f'arm_{side}', J[f'elbow_{side}'])
        f.bone(f'hand_{side}', f'forearm_{side}', J[f'wrist_{side}'])
        if side == 'r':
            f.bone('sword', 'hand_r', [GRIP_X, FIST_Y, GRIP_Z])
            f.bone('sword_tip', 'sword', TIP)
        f.bone(f'wing_{side}', 'chest', J[f'wing_{side}'])
        f.bone(f'wing_mid_{side}', f'wing_{side}', J[f'wing_elbow_{side}'])
        f.bone(f'wing_tip_{side}', f'wing_mid_{side}', J[f'wing_wrist_{side}'])
        f.bone(f'wing_fan_{side}', f'wing_tip_{side}', J[f'wing_wrist_{side}'])
    for side, s in SIDES:
        f.bone(f'leg_{side}', 'pelvis', J[f'hip_{side}'])
        f.bone(f'shin_{side}', f'leg_{side}', J[f'knee_{side}'])
        f.bone(f'foot_{side}', f'shin_{side}', J[f'ankle_{side}'])
    f.bone('tabard', 'pelvis', [0, 19.6, -3.5])
    f.bone('tabard_tip', 'tabard', [0, 12.6, -3.75])
    f.bone('ember_ring', 'root', [0, .12, 0])
    # Invisible emitter markers: server-side aura particles follow these through every clip.
    f.bone('fx_core', 'chest', [0, 27.35, -4.4])
    f.bone('fx_eyes', 'head', [0, 34.75, -3.8])
    f.bone('fx_blade', 'sword', [GRIP_X, 1.0, GRIP_Z])
    for side, s in SIDES:
        f.bone(f'fx_wing_{side}', f'wing_tip_{side}', [s * 30.0, 33.4, 11.2])
        f.bone(f'fx_fan_{side}', f'wing_fan_{side}', [s * 25.6, 16.4, 11.4])


# ---------------------------------------------------------------------------
# Head


def helm(f):
    b = 'head'
    f.box(b, (-2.5, 31.6, -2.5), (5.0, 6.0, 5.6), 'obsidian', label='helmet', engrave='lines')
    # Faceplate, visor and the angry eye slits.
    f.box(b, (-2.3, 32.0, -3.1), (4.6, 4.1, .7), 'obsidian', label='faceplate', plain=True)
    for side, s in SIDES:
        f.cube(b, (s * 1.0, 34.75, -3.47), (1.35, .36, .16), 'eye', rot((0, 0, -s * 13)), label='eyes')
        f.beam(b, (s * 2.55, 35.75, -3.2), (s * .2, 35.15, -3.45), .62, .62, 'steel', z_hint=(0, 0, -1), label='helmet_brow')
        f.cube(b, (s * 1.95, 32.9, -3.0), (1.5, 2.7, .55), 'obsidian', rot((0, -s * 22, s * 8)), label='helmet_cheek', plain=True)
        f.beam(b, (s * 1.25, 33.9, -3.45), (s * 2.25, 32.0, -3.25), .22, .2, 'steel', label='helmet_trim')
    f.beam(b, (0, 35.6, -3.32), (0, 32.3, -3.5), .62, .55, 'steel', label='helmet_nose')
    f.spike(b, (0, 32.5, -2.95), (0, 30.5, -2.35), 2.5, 'obsidian', steps=3, depth_ratio=.45, label='helmet_chin')
    for i in range(3):
        f.box(b, (-1.2 + i * .9, 32.55, -3.62), (.38, 1.0, .12), 'ghostflame', label='mouth_grille')
    # Neck guard plates sweeping back.
    for i, (y, w, a) in enumerate(((32.4, 4.8, -22), (31.4, 4.3, -32))):
        f.cube(b, (0, y, 3.05 + i * .35), (w, 1.25, .55), 'obsidian', rot((a, 0, 0)), label='helmet_back', plain=True)
    for side, s in SIDES:
        f.cube(b, (s * 2.62, 34.4, .3), (.36, 3.0, 4.6), 'obsidian', label='helmet_side', engrave='chevron')
        f.cube(b, (s * 2.66, 32.95, .3), (.4, .3, 4.7), 'steel', label='helmet_trim', plain=True)
    # A narrow iron crown: one tall brow thorn above a violet gem, two lesser thorns.
    f.box(b, (-2.75, 36.75, -2.85), (5.5, .75, 6.2), 'steel', label='crown_band')
    f.cube(b, (0, 37.2, -3.08), (1.05, 1.05, .32), 'ghostflame', rot((0, 0, 45)), label='crown_gem')
    f.spike(b, (0, 37.6, -2.95), (0, 42.2, -3.9), 1.15, 'horn', steps=4, label='crown_thorn')
    for side, s in SIDES:
        f.spike(b, (s * 1.45, 37.4, -2.9), (s * 1.75, 39.6, -3.5), .72, 'horn', steps=3, label='crown_thorn')
    # Great swept horns from the temples: an S-curve back and up, ringed with steel.
    for side, s in SIDES:
        ctrl = [(s * 2.35, 36.0, .4), (s * 7.4, 36.6, .2), (s * 9.6, 41.4, 3.2), (s * 8.4, 44.4, 7.6), (s * 6.4, 44.0, 10.6)]
        pts = [bezier(ctrl, i / 12) for i in range(13)]
        widths = [2.25 * (1 - i / 13) ** .85 + .22 for i in range(12)]
        f.taper(b, pts, widths, [w * .9 for w in widths], 'horn', z_hint=(0, 1, 0), label='horn', overlap=.25)
        for k in (1, 3):
            p, q = pts[k], pts[k + 1]
            f.beam(b, p + (q - p) * .2, p + (q - p) * .55, widths[k] + .5, widths[k] * .9 + .5, 'steel',
                   z_hint=(0, 1, 0), label='horn_ring')


# ---------------------------------------------------------------------------
# Torso, collar and back


def torso(f):
    c = 'chest'
    f.box(c, (-1.4, 29.8, -1.2), (2.8, 2.0, 2.8), 'leather', label='neck')
    # V-tapered cuirass: narrow waist widening to a broad shoulder line.
    tiers = ((21.0, 22.8, 6.4, 4.6, .2), (22.8, 24.6, 7.5, 5.0, .25), (24.6, 26.6, 8.9, 5.8, .35),
             (26.6, 28.8, 10.2, 6.2, .4), (28.8, 30.8, 10.9, 5.8, .5))
    for i, (y0, y1, w, d, zc) in enumerate(tiers):
        f.box(c, (-w / 2, y0, zc - d / 2), (w, y1 - y0, d), 'obsidian' if i >= 2 else 'blackiron', label='torso',
              engrave='lines' if i < 2 else 'chevron')
    for i, w in enumerate((5.6, 6.3, 7.0)):
        f.cube(c, (0, 21.55 + i * 1.12, -2.25 - i * .12), (w, 1.12, .6), 'obsidian', rot((-8, 0, 0)), label='abdomen', plain=True)
        f.cube(c, (0, 21.05 + i * 1.12, -2.5 - i * .12), (w + .1, .22, .35), 'steel', rot((-8, 0, 0)), label='abdomen_trim')
    # Sculpted pectoral plates meeting in a V over a ghostflame heart.
    for side, s in SIDES:
        f.cube(c, (s * 2.5, 27.5, -3.0), (4.8, 4.4, .9), 'obsidian', rot((-6, -s * 14, -s * 4)),
               label='breastplate', engrave='vine')
        f.beam(c, (s * .35, 25.2, -3.45), (s * 4.7, 26.8, -2.8), .38, .32, 'steel', label='breastplate_trim')
        f.beam(c, (s * 4.75, 26.9, -2.8), (s * 4.95, 29.6, -2.55), .34, .3, 'steel', label='breastplate_trim')
        f.beam(c, (s * .95, 27.6, -3.52), (s * 3.9, 29.5, -3.36), .24, .16, 'ghostflame', label='crack')
        f.beam(c, (s * .8, 26.7, -3.52), (s * 2.9, 25.3, -3.3), .22, .16, 'ghostflame', label='crack')
        f.cube(c, (s * 4.95, 26.0, .3), (1.0, 4.0, 4.8), 'obsidian', rot((0, 0, s * 9)), label='torso_side', engrave='lines')
    f.beam(c, (0, 29.8, -3.38), (0, 24.9, -3.52), .95, .75, 'steel', label='breastplate_keel')
    f.cube(c, (0, 27.35, -3.88), (1.95, 2.45, .7), 'ghostflame', rot((0, 0, 45)), label='core')
    for k in range(4):
        a = math.radians(45 + k * 90)
        f.beam(c, (math.cos(a) * .95, 27.35 + math.sin(a) * .95, -3.78), (math.cos(a) * 1.8, 27.35 + math.sin(a) * 1.8, -3.62),
               .34, .34, 'steel', label='core_claw')
    # High flared gorget; tall back collar crowned with spikes.
    for side, s in SIDES:
        f.cube(c, (s * 1.95, 31.35, -2.1), (2.9, 2.3, .5), 'obsidian', rot((22, -s * 26, 0)), label='collar', plain=True)
        f.cube(c, (s * 3.25, 31.6, .4), (.5, 2.7, 4.2), 'obsidian', rot((0, 0, s * 18)), label='collar', plain=True)
        f.beam(c, (s * .4, 30.5, -2.55), (s * 3.2, 32.3, -2.45), .25, .25, 'steel', label='collar_trim')
    f.cube(c, (0, 32.3, 2.95), (5.8, 3.8, .6), 'obsidian', rot((-16, 0, 0)), label='collar', engrave='chevron')
    for x in (-1.6, 1.6):
        f.spike(c, (x, 33.9, 3.4), (x * 1.35, 36.0, 4.7), .85, 'horn', label='collar_spike')
    # Back plate with a spine ridge and vertebral thorns.
    f.box(c, (-4.3, 23.3, 3.15), (8.6, 6.6, .8), 'obsidian', label='backplate', engrave='scales')
    f.beam(c, (0, 29.8, 3.75), (0, 22.6, 3.6), .8, .6, 'steel', label='spine')
    for y in (23.6, 25.3, 27.0):
        f.spike(c, (0, y, 3.85), (0, y + .7, 5.1), .6, 'horn', steps=2, label='spine_thorn')


def pauldrons(f):
    for side, s in SIDES:
        b = f'shoulder_{side}'
        top = np.array([s * 8.4, 30.6, .3])
        f.box(b, (s * 7.5 - 1.5, 28.0, -1.8), (3.0, 2.6, 4.2), 'blackiron', label='shoulder_cap')
        # Tiered, overlapping lames sloping down and outward like a gothic roof.
        tiers = ((5.0, 1.6, 6.3, 26, (.2, .85, 0)), (4.6, 1.4, 6.3, 44, (1.85, -.75, .1)), (3.9, 1.2, 5.9, 63, (2.85, -2.45, .2)))
        for k, (w, h, d, angle, off) in enumerate(tiers):
            r = rot((0, 0, -s * angle))
            center = top + np.array([s * off[0], off[1], off[2]])
            f.cube(b, center, (w, h, d), 'obsidian', r, label='pauldron', engrave='scales' if k == 0 else 'lines')
            f.cube(b, center + r @ np.array([s * (w / 2 - .25), -h / 2 + .12, 0]), (.62, .5, d + .25), 'steel', r, label='pauldron_trim')
            f.cube(b, center + r @ np.array([0, -h / 2 + .12, -d / 2]), (w, .45, .3), 'steel', r, label='pauldron_trim')
            if k == 0:
                f.cube(b, center + r @ np.array([-s * .6, h / 2 + .2, 0]), (.7, .45, d - .8), 'steel', r, label='pauldron_crest')
                f.cube(b, center + r @ np.array([-s * .6, h / 2 + .1, -d / 2 + .3]), (.9, .9, .3), 'rose', r @ rot((0, 0, 45)), label='pauldron_gem')
        for z, w, lean in ((-2.1, 1.0, -1), (.3, 1.35, 0), (2.5, 1.0, 1)):
            base = top + np.array([s * .2, 1.5, z])
            f.spike(b, base, base + np.array([s * 2.0, 5.2 - abs(z) * .5, 1.4 + lean * .7]), w, 'horn', steps=4, label='pauldron_spike')
        f.cube(b, top + np.array([-s * .4, 1.72, .3]), (1.6, .3, 4.2), 'ghostflame', rot((0, 0, -s * 18)), label='vent')
        # Ghostflame tongues rising from the vent: crossed, alpha-cut sheets.
        fb = f'flame_{side}'
        base = np.array([s * 8.9, 32.9, .6])
        for k, (dx, dz, h, w) in enumerate(((1, 0, 4.4, 2.6), (0, 1, 3.8, 2.2), (.7, .7, 3.0, 1.6))):
            axis = unit(np.array([dx, 0, dz]))
            outline = flame_outline(base, axis, h, w, k)
            f.sheet(fb, outline, 'flame', thickness=.06, up=(0, 1, 0), label='flame', seed=900 + k + (side == 'l') * 10)


def flame_outline(base, axis, height, width, variant):
    pts = []
    tongues = 3 + variant % 2
    for i in range(tongues * 2 + 1):
        u = i / (tongues * 2)
        x = (u - .5) * width
        tall = height * (.55 + .45 * math.sin(math.pi * u) ** .7) if i % 2 == 0 else height * (.35 + .25 * math.sin(math.pi * u))
        pts.append(base + axis * x + np.array([0, tall, 0]))
    pts.append(base + axis * width * .45 + np.array([0, -.2, 0]))
    pts.append(base + axis * (-width * .45) + np.array([0, -.2, 0]))
    return pts[::-1]


# ---------------------------------------------------------------------------
# Arms, gauntlets and the greatsword


def arms(f):
    for side, s in SIDES:
        sh, el, wr = J[f'shoulder_{side}'], J[f'elbow_{side}'], J[f'wrist_{side}']
        a, fa, hb = f'arm_{side}', f'forearm_{side}', f'hand_{side}'
        f.beam(a, sh + (0, -.4, 0), el + (0, .3, 0), 3.0, 3.2, 'blackiron', label='arm')
        for y in (26.4, 24.2):
            f.cube(a, (s * 7.3, y, .4), (3.5, 1.5, 3.7), 'obsidian', rot((0, 0, s * 4)), label='arm_band', plain=True)
            f.cube(a, (s * 7.3, y - .7, .4), (3.65, .32, 3.85), 'steel', rot((0, 0, s * 4)), label='arm_trim')
        f.box(fa, (s * 7.7 - 1.65, 20.8, -.6), (3.3, 2.0, 3.0), 'obsidian', label='elbow')
        f.spike(fa, (s * 7.8, 21.8, 2.0), (s * 8.4, 21.0, 4.7), 1.05, 'horn', label='elbow_spike')
        f.beam(fa, el + (0, -.5, 0), wr + (0, .9, .1), 3.0, 3.1, 'obsidian', label='forearm', engrave='chevron')
        f.box(fa, (s * 7.8 - 1.75, 15.5, -1.6), (3.5, 1.7, 3.5), 'obsidian', label='cuff', plain=True)
        f.box(fa, (s * 7.8 - 1.8, 15.35, -1.65), (3.6, .36, 3.6), 'steel', label='cuff_trim')
        for k in range(3):
            base = np.array([s * 9.2, 16.9 + k * 1.45, .6])
            f.spike(fa, base, base + np.array([s * 1.3, .6, 1.9]), .62, 'horn', steps=2, label='forearm_blade')
        f.beam(fa, (s * 6.4, 20.4, -1.62), (s * 6.6, 16.6, -1.9), .3, .2, 'ghostflame', label='forearm_rune')
        f.box(hb, (s * 7.8 - 1.3, 13.8, -1.3), (2.6, 1.4, 2.6), 'obsidian', label='hand_wrist')
    fist(f)
    claw(f)


def fist(f):
    """Right gauntlet closed around the grip; the grip channel stays clear."""
    b = 'hand_r'
    x, z = GRIP_X, GRIP_Z
    f.box(b, (x + .5, 11.6, z - 1.15), (.95, 2.6, 2.3), 'obsidian', label='hands')
    for k, y in enumerate((11.95, 12.6, 13.25, 13.9)):
        f.box(b, (x - 1.0, y - .29, z - 1.07), (2.0, .58, .58), 'obsidian', label='hands', plain=True)
        f.box(b, (x - 1.07, y - .29, z - .5), (.58, .58, 1.4), 'obsidian', label='hands', plain=True)
        f.box(b, (x - .2, y - .2, z - 1.22), (.6, .4, .18), 'steel', label='knuckle')
    f.cube(b, (x - .35, 14.05, z + .62), (1.45, .55, .6), 'obsidian', rot((0, 0, -25)), label='hands')


def claw(f):
    """Left gauntlet: open, clawed casting hand."""
    b = 'hand_l'
    w = J['wrist_l']
    f.box(b, (w[0] - 1.3, 12.2, -1.3), (2.6, 2.3, 2.2), 'obsidian', label='hands', plain=True)
    f.box(b, (w[0] - 1.35, 13.6, -1.35), (2.7, .4, 2.3), 'steel', label='hands_trim')
    for k in range(4):
        z = -1.0 + k * .66
        base = np.array([w[0] + .25, 12.3, z])
        mid = base + np.array([.35, -1.6, -.15])
        tip = mid + np.array([.55, -1.35, -.25])
        f.beam(b, base, mid, .55, .55, 'obsidian', label='hands_finger')
        f.spike(b, mid, tip, .5, 'horn', steps=2, label='hands_claw')
    f.beam(b, (w[0] + .6, 12.9, -1.15), (w[0] + 1.3, 11.5, -2.3), .55, .55, 'obsidian', label='hands_finger')
    f.spike(b, (w[0] + 1.3, 11.5, -2.3), (w[0] + 1.6, 10.6, -3.2), .48, 'horn', steps=2, label='hands_claw')


def sword(f):
    """Thanasius' Ruin: a serrated greatsword with a glowing rune fuller."""
    b = 'sword'
    x, z = GRIP_X, GRIP_Z
    f.beam(b, (x, 10.9, z), (x, 14.45, z), .8, .8, 'leather', label='grip')
    f.box(b, (x - .52, 14.4, z - .52), (1.04, .7, 1.04), 'steel', label='pommel')
    f.box(b, (x - .75, 10.0, z - 1.35), (1.5, 1.1, 2.7), 'obsidian', label='guard', plain=True)
    f.cube(b, (x, 10.45, z), (1.75, .95, .95), 'rose', rot((45, 0, 0)), label='guard_gem')
    for d in (-1, 1):
        pts = [(x, 10.55, z + d * 1.2), (x, 10.9, z + d * 2.9), (x, 11.9, z + d * 4.15), (x, 13.2, z + d * 4.55)]
        f.taper(b, pts, [.95, .72, .45], [1.05, .8, .5], 'horn', z_hint=(1, 0, 0), label='guard_wing')
    f.box(b, (x - .36, 9.1, z - 1.3), (.72, .95, 2.6), 'blade', label='blade')
    widths = (3.3, 3.45, 3.35, 3.1, 2.75, 2.3, 1.7, 1.0)
    top, step = 9.1, 2.06
    for i, w in enumerate(widths):
        y1 = top - i * step
        zc = z - .12 * i
        f.box(b, (x - .26, y1 - step - .02, zc - w / 2), (.52, step + .04, w), 'blade', label='blade')
        f.box(b, (x - .29, y1 - step, zc - w / 2 - .02), (.58, step, .26), 'steel', label='blade_edge', plain=True)
    f.spike(b, (x, top - len(widths) * step + .2, z - 1.05), TIP, .9, 'blade', steps=2, z_hint=(1, 0, 0), depth_ratio=.55,
            label='blade_tip')
    f.box(b, (x - .3, 1.6, z - .32), (.6, 7.3, .64), 'runes', label='fuller')
    f.box(b, (x - .3, -4.2, z - .55), (.6, 5.8, .44), 'runes', label='fuller')
    for y, w in ((8.1, 3.4), (5.7, 3.3), (3.3, 3.1)):
        base = np.array([x, y, z + w / 2 - .25])
        f.spike(b, base, base + np.array([0, -1.6, 1.35]), .55, 'blade', steps=2, z_hint=(1, 0, 0), label='blade_tooth')


# ---------------------------------------------------------------------------
# Hips, legs, sabatons and the front tabard


def lower_body(f):
    p = 'pelvis'
    f.box(p, (-3.8, 17.6, -2.2), (7.6, 2.8, 5.0), 'blackiron', label='pelvis')
    f.box(p, (-3.85, 19.6, -2.45), (7.7, 1.4, 5.5), 'leather', label='belt')
    f.box(p, (-3.9, 19.55, -2.5), (7.8, .35, 5.6), 'steel', label='belt_trim')
    f.box(p, (-1.15, 19.2, -3.0), (2.3, 2.1, .5), 'steel', label='buckle')
    f.cube(p, (0, 20.25, -3.32), (.95, .95, .35), 'rose', rot((0, 0, 45)), label='buckle_gem')
    for i, (w, y, a) in enumerate(((6.8, 18.7, -10), (6.1, 17.5, -15))):
        f.cube(p, (0, y, -2.9 - i * .2), (w, 1.3, .6), 'obsidian', rot((a, 0, 0)), label='fauld', plain=True)
    for side, s in SIDES:
        # Three overlapping lames per hip, flaring outward over the thigh.
        for k, (y, h, d, a) in enumerate(((18.0, 2.4, 4.8, 10), (16.25, 2.2, 4.5, 15), (14.55, 2.0, 4.1, 20))):
            r = rot((0, 0, s * a))
            center = np.array([s * (4.45 + k * .32), y, .1])
            f.cube(p, center, (.75, h, d), 'obsidian', r, label='tasset', plain=k > 0, engrave='lines')
            f.cube(p, center + r @ np.array([s * .1, -h / 2 + .15, 0]), (.85, .32, d + .1), 'steel', r, label='tasset_trim')
    f.cube(p, (0, 16.8, 3.0), (6.6, 4.4, .6), 'obsidian', rot((10, 0, 0)), label='fauld_back', plain=True)
    # Front tabard in two hanging panels, embroidered with the Dreadrose.
    f.sheet('tabard', [(-2.3, 19.6, -3.42), (2.3, 19.6, -3.42), (2.25, 12.6, -3.7), (-2.25, 12.6, -3.7)], 'cloth',
            thickness=.2, up=(0, 1, 0), facing=(0, 0, -1), label='tabard', lining=('north', MATERIALS['crimson'].ramp_hex),
            emblem=dict(kind='rose', y=.42, r=.26, faces=('south',)), folds=3)
    f.sheet('tabard_tip', [(-2.25, 12.6, -3.7), (2.25, 12.6, -3.7), (2.1, 5.2, -3.95), (0, 4.4, -3.95), (-2.1, 5.2, -3.95)],
            'cloth', thickness=.2, up=(0, 1, 0), facing=(0, 0, -1), label='tabard', edge={2: ('tatter', .08, 2), 3: ('tatter', .08, 2)},
            lining=('north', MATERIALS['crimson'].ramp_hex), hem=.24, folds=3)
    for side, s in SIDES:
        hip, knee, ankle = J[f'hip_{side}'], J[f'knee_{side}'], J[f'ankle_{side}']
        lg, sh, ft = f'leg_{side}', f'shin_{side}', f'foot_{side}'
        f.beam(lg, hip + (0, .2, 0), knee + (0, .5, 0), 3.4, 3.6, 'blackiron', label='leg')
        f.cube(lg, (s * 3.1, 14.8, -1.85), (3.4, 5.6, .8), 'obsidian', rot((4, 0, 0)), label='cuisse', engrave='chevron')
        f.cube(lg, (s * 4.75, 14.8, -.1), (.7, 5.2, 3.4), 'obsidian', rot((0, 0, s * 3)), label='cuisse', plain=True)
        f.box(sh, (s * 3.25 - 1.6, 9.3, -2.1), (3.2, 2.4, 1.6), 'obsidian', label='knee', plain=True)
        f.spike(sh, (s * 3.25, 10.6, -2.4), (s * 3.3, 11.8, -4.4), 1.15, 'horn', label='knee_spike')
        f.cube(sh, (s * 3.25, 10.5, -2.95), (.7, .7, .25), 'ghostflame', rot((0, 0, 45)), label='knee_gem')
        f.beam(sh, knee + (0, -.3, 0), ankle + (0, .5, -.2), 3.0, 3.2, 'obsidian', label='shin', engrave='ribs')
        f.beam(sh, (s * 3.25, 9.6, -1.85), (s * 3.3, 3.3, -1.75), .8, .6, 'steel', label='shin_ridge')
        f.box(sh, (s * 3.25 - 1.3, 4.6, .9), (2.6, 4.6, .8), 'obsidian', label='calf', plain=True)
        f.box(ft, (s * 3.3 - 1.4, .6, -1.1), (2.8, 2.2, 3.0), 'obsidian', label='foot', plain=True)
        f.box(ft, (s * 3.3 - 1.5, 0, -2.6), (3.0, 1.8, 2.4), 'obsidian', label='foot', plain=True)
        f.cube(ft, (s * 3.3, .7, -3.85), (2.6, 1.4, 1.8), 'obsidian', rot((8, 0, 0)), label='foot', plain=True)
        f.box(ft, (s * 3.3 - 1.55, .95, -2.65), (3.1, .3, 2.5), 'steel', label='foot_trim')
        f.spike(ft, (s * 3.3, .55, -4.6), (s * 3.3, .3, -6.3), 1.4, 'obsidian', steps=2, depth_ratio=.7, label='foot_toe')
        f.spike(ft, (s * 3.3, .9, 1.7), (s * 3.3, .35, 3.2), .7, 'horn', steps=2, label='foot_spur')


# ---------------------------------------------------------------------------
# Cape, wings, halo and floor sigil


def cape(f):
    segments = (('cloak', 30.8, 18.6, 6.0, 7.1), ('cloak_mid', 18.6, 9.2, 7.1, 7.9), ('cloak_tip', 9.2, 1.2, 7.9, 8.6))
    strips = 6

    def z_at(x, y, w):
        return 4.0 + (30.8 - y) * .058 + (1 - (x / w) ** 2) * 1.5

    for bone, top, bottom, w_top, w_bottom in segments:
        for j in range(strips):
            a, b = -1 + 2 * j / strips, -1 + 2 * (j + 1) / strips
            tl = np.array([a * w_top, top, z_at(a * w_top, top, w_top)])
            tr = np.array([b * w_top, top, z_at(b * w_top, top, w_top)])
            br = np.array([b * w_bottom, bottom, z_at(b * w_bottom, bottom, w_bottom)])
            bl = np.array([a * w_bottom, bottom, z_at(a * w_bottom, bottom, w_bottom)])
            edge = {2: ('tatter', .07, 2)} if bone == 'cloak_tip' else {}
            if bone == 'cloak_tip':
                drop = (.6 if j % 2 else 1.4)
                br = br + np.array([0, -drop * .4, 0])
                bl = bl + np.array([0, -drop * .2, 0])
            f.sheet(bone, [tl, tr, br, bl], 'cloth', thickness=.18, up=(0, 1, 0), facing=(0, 0, 1), edge=edge, label='cape',
                    lining=('north', MATERIALS['crimson'].ramp_hex), hem=.22 if bone == 'cloak_tip' else None, folds=2)
    # Dreadrose war banner stitched over the back of the cape.
    z0 = lambda y: z_at(0, y, 6.0) + .2
    f.sheet('cloak', [(-2.6, 28.6, z0(28.6)), (2.6, 28.6, z0(28.6)), (2.6, 20.4, z0(20.4)), (0, 18.4, z0(18.4)), (-2.6, 20.4, z0(20.4))],
            'banner', thickness=.12, up=(0, 1, 0), facing=(0, 0, 1), label='cape_banner', folds=1, hem=.0,
            emblem=dict(kind='rose', y=.48, r=.27, tone='trim', faces=('south',)), lining=('north', MATERIALS['crimson'].ramp_hex))
    for side, s in SIDES:
        f.cube('cloak', (s * 4.3, 30.6, 3.7), (1.6, 1.6, .55), 'steel', rot((0, 0, 45)), label='cape_clasp')
        f.cube('cloak', (s * 4.3, 30.6, 3.42), (.7, .7, .2), 'rose', rot((0, 0, 45)), label='cape_gem')


def wings(f):
    for side, s in SIDES:
        root, el, wr = J[f'wing_{side}'], J[f'wing_elbow_{side}'], J[f'wing_wrist_{side}']
        w0, w1, tip, fan = f'wing_{side}', f'wing_mid_{side}', f'wing_tip_{side}', f'wing_fan_{side}'

        def m(v):
            return np.array([s * v[0], v[1], v[2]], float)
        f.taper(w0, [root, (root + el) / 2 + m((0, .6, 0)), el], [1.9, 1.5], [1.7, 1.4], 'horn', z_hint=(0, 1, 0), label='wing_arm')
        f.box(w0, root - (1.0, 1.0, 1.0), (2.0, 2.0, 2.0), 'obsidian', label='wing_root')
        f.box(w1, el - (.9, .9, .9), (1.8, 1.8, 1.8), 'obsidian', label='wing_joint')
        f.taper(w1, [el, (el + wr) / 2 + m((0, .5, 0)), wr], [1.45, 1.15], [1.3, 1.05], 'horn', z_hint=(0, 1, 0), label='wing_arm')
        for k in (.3, .62):
            base = el + (wr - el) * k
            f.spike(w1, base + m((0, .5, 0)), base + m((-.9, 2.6, -.4)), .62, 'horn', steps=2, label='wing_spur')
        f.box(tip, wr - (.85, .85, .85), (1.7, 1.7, 1.7), 'obsidian', label='wing_joint')
        f.spike(tip, wr + m((0, .5, -.3)), wr + m((.8, 3.0, -1.3)), .9, 'horn', label='wing_thumb')
        fingers = {1: m((33.0, 35.0, 11.4)), 2: m((32.0, 25.0, 12.4)), 3: m((27.5, 15.2, 11.8)), 4: m((20.6, 9.6, 10.2))}
        for k, f_tip in fingers.items():
            bone = tip if k <= 2 else fan
            mid = wr + (f_tip - wr) * .52 + m((0, .7, .2))
            f.taper(bone, [wr, mid, f_tip], [.95, .7], [.9, .62], 'horn', z_hint=(0, 0, 1), label='wing_finger')
            f.box(bone, mid - (.45, .45, .45), (.9, .9, .9), 'obsidian', label='wing_knuckle')
            f.spike(bone, f_tip, f_tip + unit(f_tip - wr) * 1.7 + m((0, -.4, 0)), .55, 'horn', steps=2, label='wing_claw')
        f.sheet(tip, [wr, fingers[1], fingers[2]], 'membrane', thickness=.14, edge={1: ('scallop', .09, 1)},
                label='wing_membrane', veins=4)
        f.sheet(fan, [wr, fingers[2], fingers[3]], 'membrane', thickness=.14, edge={1: ('scallop', .11, 1)},
                label='wing_membrane', veins=4)
        f.sheet(fan, [wr, fingers[3], fingers[4]], 'membrane', thickness=.14, edge={1: ('scallop', .12, 1)},
                label='wing_membrane', veins=4)
        hang = m((7.6, 17.4, 7.2))
        f.sheet(w1, [el, wr, fingers[4] + m((-.6, .4, -.3)), hang], 'membrane', thickness=.14,
                edge={2: ('scallop', .1, 1), 3: ('scallop', .06, 1)}, label='wing_membrane', veins=5)
        f.sheet(w0, [root + m((.6, .6, .1)), el, hang + m((-.4, .8, -.2)), m((3.6, 21.0, 5.6))], 'membrane', thickness=.14,
                edge={2: ('scallop', .06, 1)}, label='wing_membrane', veins=3)


def dread_halo(f):
    b = 'dread_halo'
    c = np.array(f.at(b))
    f.ring(b, c, 6.3, .6, .6, 'blackiron', segments=24, normal=(0, 0, 1))
    f.ring(b, c + (0, 0, -.25), 5.45, .26, .3, 'ghostflame', segments=24, normal=(0, 0, 1), phase=.13)
    f.ring(b, c + (0, 0, -.2), 2.3, .32, .32, 'steel', segments=12, normal=(0, 0, 1))
    for k in range(12):
        a = math.tau * k / 12 + math.pi / 2
        d = np.array([math.cos(a), math.sin(a), 0])
        length = 2.8 if k % 2 == 0 else 1.6
        f.spike(b, c + d * 6.3, c + d * (6.3 + length), .9 if k % 2 == 0 else .62, 'horn', steps=3,
                z_hint=(0, 0, 1), label='halo_thorn')
    # Gothic rose window: eight tracery spokes and lancet petals between the rings.
    for k in range(8):
        a = math.tau * k / 8 + math.pi / 8
        d = np.array([math.cos(a), math.sin(a), 0])
        f.beam(b, c + d * 2.45 + (0, 0, -.25), c + d * 5.3 + (0, 0, -.25), .22, .22, 'ghostflame', z_hint=(0, 0, 1), label='halo_tracery')
        mid = math.tau * (k + .5) / 8 + math.pi / 8
        for sign in (-1, 1):
            p = c + np.array([math.cos(mid), math.sin(mid), 0]) * 2.6 + (0, 0, -.3)
            q = c + np.array([math.cos(mid + sign * .23), math.sin(mid + sign * .23), 0]) * 3.9 + (0, 0, -.3)
            r_ = c + np.array([math.cos(mid), math.sin(mid), 0]) * 4.9 + (0, 0, -.3)
            f.beam(b, p, q, .16, .16, 'ghostflame', z_hint=(0, 0, 1), label='halo_petal')
            f.beam(b, q, r_, .16, .16, 'ghostflame', z_hint=(0, 0, 1), label='halo_petal')
    for k in range(3):
        a = math.tau * k / 3 + math.pi / 2
        p = c + np.array([math.cos(a), math.sin(a), 0]) * 6.3 + (0, 0, -.35)
        f.cube(b, p, (1.15, 1.15, .5), 'rose', rot((0, 0, 45 + math.degrees(a))), label='halo_gem')


def ember_ring(f):
    b = 'ember_ring'
    f.ring(b, (0, .12, 0), 4.6, .22, .08, 'ghostflame', segments=28, normal=(0, 1, 0))
    f.ring(b, (0, .12, 0), 3.7, .14, .08, 'ghostflame', segments=24, normal=(0, 1, 0), phase=.13)
    for k in range(8):
        a = math.tau * k / 8
        d = np.array([math.cos(a), 0, math.sin(a)])
        f.beam(b, d * 3.75 + (0, .12, 0), d * 4.55 + (0, .12, 0), .5, .08, 'ghostflame', z_hint=(0, 1, 0), label='ember_rune')


def sculpt():
    for m in MATERIALS.values():
        m.ramp_hex = ['#%02x%02x%02x' % tuple(int(v) for v in c) for c in m.ramp]
    f = Forge('arkhaia', MATERIALS, seed=4242)
    skeleton(f)
    helm(f)
    torso(f)
    pauldrons(f)
    arms(f)
    sword(f)
    lower_body(f)
    cape(f)
    wings(f)
    dread_halo(f)
    ember_ring(f)
    return f


def build():
    """Sculpt, paint and animate; returns the blueprint, atlas, statistics and worst IK reach error."""
    from arkhaia_motion import aura_clip, clips
    f = sculpt()
    motion, worst = clips(f)
    body = [b for b in f.bones if b not in AURA and b not in MARKERS]
    for clip in motion:
        clip.bones = body
    model, atlas, stats = build_model(f, motion + [aura_clip()], 'arkhaia', 'arkhaia', [[-10, 0, -10], [10, 40, 10]],
                                      meta=dict(evergarden_revision=REVISION, evergarden_scale=SCALE), density=9.0)
    return model, atlas, stats, worst


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, default=OUT, help='Folder receiving arkhaia.bbmodel and its manifest')
    args = parser.parse_args()
    model, atlas, stats, worst = build()
    args.out.mkdir(parents=True, exist_ok=True)
    target = args.out / 'arkhaia.bbmodel'
    target.write_text(dump(model) + '\n', encoding='utf8')
    art = ROOT / 'evergarden/art/arkhaia-model'
    art.mkdir(parents=True, exist_ok=True)
    atlas.save(art / 'arkhaia.png')
    names = [a['name'] for a in model['animations']]
    manifest = dict(revision=REVISION, cubes=len(model['elements']) - 1, bones=len(sculpt().bones), scale=SCALE,
                    texture=dict(size=model['resolution']['width'], density=round(stats['density'], 2), tiles=stats['tiles'],
                                 coverage=round(stats['coverage'], 3)),
                    aura_bones=list(AURA), animations={a['name']: a['length'] for a in model['animations']},
                    worst_ik_reach_error=round(worst, 3))
    (args.out / 'arkhaia-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf8')
    print(f"Arkhaia: {manifest['cubes']} cubes, {manifest['bones']} bones, {len(names)} clips, "
          f"atlas {stats['coverage']:.0%} at {stats['density']:.1f}px/unit, IK error {worst:.2f}")


if __name__ == '__main__':
    main()
