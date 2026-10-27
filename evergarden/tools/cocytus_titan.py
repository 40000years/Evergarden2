"""Cocytus, the Glacial Warden: Remastered Frost Titan ModelEngine R4 Blueprint (v2).

A colossal frost titan sculpted, painted, and rigged using the high-fidelity procedural
pipeline of Evergarden boss titans, fully differentiated from Arkhaia:
- Iconic silhouette: Pale-ice titan skull mask, hanging icicle side-tusks, towering branching
  glacial antler crown, and multi-tiered stalactite beard.
- Hulking glacial monolith pauldrons with the signature radiating fan of 5 ice quill spires.
- Split tectonic ice pectoral plates framing a suspended ancient River Cocytus runic frost amulet.
- Fur-lined frost cloak dripping with hanging crystalline icicles.
- Armored shins crowned with upward knee ice spikes, 4 horizontal rime bindings, and rear heel spurs.
- Colossal segmented Glacial Greatbow with crystalline quill brackets and glowing zero-degree bowstring.
- Decoupled floating arc of hovering glacial monolith shards.
- Complete 13-clip animation suite matching CocytusEncounter.java:
  idle, walk, hover, cast (windup/active/recover), attack (windup/active/recover),
  rain (windup/active/recover), and stun.
"""
import argparse
import json
import math
from pathlib import Path
import sys

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'evergarden/tools'))

from boss_forge import (
    Clip, Forge, Material, Poser, Track,
    basis, bezier, blend, build_model, dump, ease, ease_in, ease_out,
    euler, frame2, hex_rgb, keyed, lerp, rot, slerp, unit
)

OUT = ROOT / 'evergarden/src/main/resources/modelengine/rivers'
SCALE = 3.0
REVISION = 'cocytus-glacial-titan-v2'

# ---------------------------------------------------------------------------
# Color Palettes & Materials
# ---------------------------------------------------------------------------
PALE_ICE_MASK = ['#0c253d', '#14385c', '#205282', '#3575b2', '#579ee5', '#8bc7fd', '#cce6fc', '#f5faff']
GLACIAL_ICE = ['#061628', '#0b233f', '#123762', '#1b528e', '#2976c6', '#4fa2f4', '#85c3fc', '#c7e5fc', '#ffffff']
PERMAFROST_STONE = ['#08101a', '#0e1a29', '#15253a', '#1e334e', '#2b4467', '#3c5a84', '#5175a5', '#6c94cc']
DARK_FROST_IRON = ['#080c12', '#0e1520', '#16202c', '#202d3c', '#2d3d50', '#3e526a', '#546b88', '#738dae']
FROST_STEEL = ['#162434', '#213448', '#304963', '#436383', '#5c83a9', '#7fa9d4', '#afd1fa', '#e8f3ff']
CYAN_GLOW = ['#06283b', '#0c4d6f', '#157aa9', '#24aee7', '#5cd6ff', '#a8efff', '#ffffff']
ARCTIC_FUR = ['#16202c', '#212d3b', '#303f50', '#44566b', '#5f758f', '#829bb8', '#b0c7e2', '#eaf2fa']
RIVER_CLOTH = ['#07121e', '#0d1e30', '#152d47', '#1f4064', '#2d5786', '#3f72ac', '#5b92d4']
ANTLER_RAMP = ['#071626', '#0e2741', '#163d63', '#225b90', '#3481c5', '#55aff8', '#8bd5ff', '#d6f2ff']
BLADE_RAMP = ['#0a1b2d', '#122c48', '#1c426b', '#2a5f97', '#3d83cc', '#61aaff', '#97cbfd', '#d9edff']
RUNES_RAMP = ['#061c2c', '#0c3856', '#145c8c', '#1f8bc9', '#46beff', '#9ae2ff', '#ffffff']

MATERIALS = {
    'mask': Material(PALE_ICE_MASK, 'gem', glow=True, density=1.1),
    'ice': Material(GLACIAL_ICE, 'gem', glow=True, density=1.1),
    'stone': Material(PERMAFROST_STONE, 'plate', accent=CYAN_GLOW, density=0.9),
    'iron': Material(DARK_FROST_IRON, 'plate', density=0.85),
    'steel': Material(FROST_STEEL, 'trim', density=1.0),
    'fur': Material(ARCTIC_FUR, 'cloth', density=0.9),
    'cloth': Material(RIVER_CLOTH, 'cloth', trim=FROST_STEEL, accent=CYAN_GLOW, density=0.85),
    'horn': Material(ANTLER_RAMP, 'horn', density=1.0),
    'blade': Material(BLADE_RAMP, 'blade', density=1.0),
    'glow': Material(CYAN_GLOW, 'glow', glow=True, density=1.4),
    'eye': Material(['#a8efff', '#dcf8ff', '#ffffff'], 'glow', glow=True, density=1.6),
    'runes': Material(RUNES_RAMP, 'runes', glow=True, density=1.4),
    'flame': Material(CYAN_GLOW[:-1] + ['#ffffff'], 'flame', glow=True, density=0.75),
    'bow_string': Material(['#5cd6ff', '#a8efff', '#ffffff'], 'glow', glow=True, density=1.5),
}

SIDES = (('r', 1), ('l', -1))

# ---------------------------------------------------------------------------
# Joint Pivots
# ---------------------------------------------------------------------------
def joints():
    j = {
        'pelvis': (0, 16.5, 0),
        'chest': (0, 24.5, 0),
        'neck': (0, 32.5, 0.2),
        'hi_gaze': (0, 34.5, 0.2),
        'head': (0, 34.5, 0.2),
        'jaw': (0, 33.5, -2.5),
        'collar': (0, 29.5, 1.0),
        'cape': (0, 29.0, 3.2),
        'cape_tip': (0, 15.0, 4.2),
        'halo': (0, 36.5, 3.0),
    }
    for side, s in SIDES:
        j.update({
            f'shoulder_{side}': (s * 8.2, 30.5, 0.2),
            f'arm_{side}': (s * 8.2, 30.5, 0.2),
            f'elbow_{side}': (s * 9.0, 23.0, 0.5),
            f'wrist_{side}': (s * 9.2, 15.5, 0.0),
            f'flame_{side}': (s * 8.8, 33.5, 0.4),
            f'hip_{side}': (s * 3.4, 16.0, 0.1),
            f'knee_{side}': (s * 3.6, 9.0, -0.2),
            f'ankle_{side}': (s * 3.8, 2.0, 0.3),
            f'sash_{side}': (s * 4.4, 16.5, 0.0),
        })
    return {k: np.array(v, float) for k, v in j.items()}

J = joints()

# ---------------------------------------------------------------------------
# Skeleton Definition
# ---------------------------------------------------------------------------
def skeleton(f):
    f.bone('root', None, [0, 0, 0])
    f.bone('pelvis', 'root', J['pelvis'])
    f.bone('chest', 'pelvis', J['chest'])
    f.bone('neck', 'chest', J['neck'])
    f.bone('hi_gaze', 'neck', J['hi_gaze'])
    f.bone('head', 'hi_gaze', J['head'])
    f.bone('head_forward', 'head', [0, 34.5, -7.0])
    f.bone('jaw', 'head', J['jaw'])
    f.bone('collar', 'chest', J['collar'])
    f.bone('cape', 'chest', J['cape'])
    f.bone('cape_tip', 'cape', J['cape_tip'])
    f.bone('halo', 'chest', J['halo'])

    for side, s in SIDES:
        f.bone(f'shoulder_{side}', 'chest', J[f'shoulder_{side}'])
        f.bone(f'flame_{side}', f'shoulder_{side}', J[f'flame_{side}'])
        f.bone(f'arm_{side}', 'chest', J[f'shoulder_{side}'])
        f.bone(f'forearm_{side}', f'arm_{side}', J[f'elbow_{side}'])
        f.bone(f'hand_{side}', f'forearm_{side}', J[f'wrist_{side}'])
        f.bone(f'weapon_{side}', f'hand_{side}', J[f'wrist_{side}'])
        f.bone(f'leg_{side}', 'pelvis', J[f'hip_{side}'])
        f.bone(f'shin_{side}', f'leg_{side}', J[f'knee_{side}'])
        f.bone(f'foot_{side}', f'shin_{side}', J[f'ankle_{side}'])
        f.bone(f'sash_{side}', 'pelvis', J[f'sash_{side}'])

    f.bone('hitbox', 'root', [0, 28, 0])

# ---------------------------------------------------------------------------
# Head & Helm: The Glacial Skull & Antler Crown
# ---------------------------------------------------------------------------
def helm(f):
    b = 'head'
    # Dark permafrost cranium base
    f.box(b, (-2.8, 32.0, -2.6), (5.6, 6.2, 5.4), 'stone', label='cranium')

    # Pale Glacial Titan Skull / Faceplate (The iconic pale ice mask)
    f.box(b, (-2.5, 32.2, -3.4), (5.0, 5.6, 0.9), 'mask', label='face_mask', plain=True)

    # Chiseled Brow Ridge
    f.beam(b, (-2.6, 36.2, -3.6), (2.6, 36.2, -3.6), 0.9, 0.7, 'stone', label='brow_ridge')

    # Hollow Glowing Eyes with sunken eye cavities
    for side, s in SIDES:
        # Sunken eye cavity
        f.box(b, (s * 1.15 - 0.75, 34.7, -3.6), (1.5, 0.7, 0.3), 'iron', label='eye_socket')
        # Piercing glowing pupil
        f.cube(b, (s * 1.15, 35.0, -3.75), (1.3, 0.45, 0.2), 'eye', rot((0, 0, -s * 8)), label='eye_pupil')
        # Eye brow angle
        f.beam(b, (s * 0.2, 35.6, -3.7), (s * 2.3, 35.8, -3.5), 0.5, 0.4, 'steel', label='eye_brow')

    # Chiseled Ice Nose Ridge
    f.beam(b, (0, 36.1, -3.6), (0, 32.6, -3.8), 0.7, 0.65, 'mask', label='nose')

    # Ice Stalactite Tusks / Side Guards (Homage to original Cocytus)
    for side, s in SIDES:
        f.spike(b, (s * 2.5, 34.2, -3.2), (s * 2.3, 27.5, -3.6), 1.25, 'ice', steps=4, label='ice_tusk')
        f.cube(b, (s * 2.4, 33.5, -3.0), (0.8, 2.2, 1.2), 'steel', rot((0, -s * 15, s * 6)), label='tusk_mount')

    # Ice Beard (Jaw bone)
    jb = 'jaw'
    f.spike(jb, (0, 32.8, -2.6), (0, 26.5, -2.0), 2.2, 'ice', steps=5, depth_ratio=0.6, label='beard_center')
    for side, s in SIDES:
        f.spike(jb, (s * 1.2, 32.6, -2.5), (s * 1.5, 28.0, -1.8), 1.1, 'ice', steps=4, label='beard_inner')
        f.spike(jb, (s * 2.1, 32.2, -2.2), (s * 2.4, 29.5, -1.5), 0.85, 'ice', steps=3, label='beard_outer')

    # Towering Glacial Antler Crown (Replacing Arkhaia's ram horns!)
    f.box(b, (-3.0, 37.0, -2.8), (6.0, 1.0, 5.8), 'steel', label='crown_band')
    # Forehead diamond runestone
    f.cube(b, (0, 37.5, -3.25), (1.4, 1.4, 0.4), 'ice', rot((0, 0, 45)), label='crown_gem')
    f.cube(b, (0, 37.5, -3.35), (0.9, 0.9, 0.3), 'glow', rot((0, 0, 45)), label='crown_gem_glow')

    # Central High Spire & Mid Spire Pair
    f.spike(b, (0, 37.8, -2.8), (0, 47.0, -3.6), 1.5, 'ice', steps=5, label='crown_spire_center')
    for side, s in SIDES:
        f.spike(b, (s * 1.5, 37.6, -2.6), (s * 2.0, 43.8, -3.2), 1.1, 'ice', steps=4, label='crown_spire_mid')
        f.spike(b, (s * 2.6, 37.4, -2.4), (s * 3.4, 40.5, -2.8), 0.85, 'ice', steps=3, label='crown_spire_side')

    # Branching Glacial Antlers (Majestic crystalline ice antlers reaching up and branching out)
    for side, s in SIDES:
        ctrl = [
            (s * 2.6, 37.4, 0.0),
            (s * 6.2, 41.5, 0.4),
            (s * 9.5, 47.0, 0.2),
            (s * 11.2, 53.5, 1.2)
        ]
        pts = [bezier(ctrl, i / 10) for i in range(11)]
        widths = [2.2 * (1 - i / 10 * 0.72) for i in range(10)]
        f.taper(b, pts, widths, widths, 'horn', z_hint=(0, 0, 1), label='antler_beam', overlap=0.2)

        # Steel reinforcement rings along the antler base
        p, q = pts[2], pts[3]
        f.beam(b, p, q, widths[2] + 0.45, widths[2] + 0.45, 'steel', z_hint=(0, 0, 1), label='antler_ring')

        # Antler Branch 1: Brow tine sweeping forward and up
        f.spike(b, pts[3], pts[3] + np.array([s * 1.2, 3.2, -4.2]), 1.1, 'ice', steps=3, label='antler_brow_tine')

        # Antler Branch 2: Outer crown tine spreading wide
        f.spike(b, pts[6], pts[6] + np.array([s * 3.8, 3.8, 0.6]), 1.0, 'ice', steps=3, label='antler_outer_tine')

        # Antler Branch 3: Rear crown tine pointing back-upward
        f.spike(b, pts[8], pts[8] + np.array([s * 0.4, 4.0, 3.8]), 0.9, 'ice', steps=3, label='antler_rear_tine')

# ---------------------------------------------------------------------------
# Torso & Core: Split Glacial Pectoral Plates & River Amulet
# ---------------------------------------------------------------------------
def torso(f):
    c = 'chest'
    f.box(c, (-1.8, 30.5, -1.6), (3.6, 2.4, 3.4), 'iron', label='neck_joint')

    # Permafrost stone torso core
    tiers = (
        (22.0, 24.5, 7.8, 5.4, 0.2),
        (24.5, 27.2, 9.4, 5.8, 0.3),
        (27.2, 30.0, 11.2, 6.4, 0.4),
        (30.0, 32.5, 12.4, 6.6, 0.5)
    )
    for i, (y0, y1, w, d, zc) in enumerate(tiers):
        f.box(c, (-w / 2, y0, zc - d / 2), (w, y1 - y0, d), 'stone' if i >= 2 else 'iron',
              label='torso_plate', engrave='scales' if i >= 2 else 'lines')

    # Abdomen segmented plating with frost-iron brackets
    for i, w in enumerate((6.2, 7.2, 8.2)):
        f.cube(c, (0, 22.8 + i * 1.3, -2.5 - i * 0.12), (w, 1.2, 0.7), 'stone', rot((-8, 0, 0)), label='abdomen', plain=True)
        f.cube(c, (0, 22.3 + i * 1.3, -2.75 - i * 0.12), (w + 0.2, 0.25, 0.45), 'steel', rot((-8, 0, 0)), label='abdomen_trim')

    # Split Glacial Pectoral Flaps (Homage to original Cocytus)
    for side, s in SIDES:
        # Thick angled glacial ice plate
        f.cube(c, (s * 3.4, 28.6, -3.4), (5.2, 4.8, 1.2), 'ice', rot((-6, -s * 16, -s * 4)), label='split_ice_pectoral')
        # Frost-steel reinforcing border
        f.cube(c, (s * 3.4, 28.6, -3.7), (5.5, 0.6, 1.4), 'steel', rot((-6, -s * 16, -s * 4)), label='pectoral_border')
        # Radiating cyan frost cracks
        f.beam(c, (s * 1.2, 28.5, -4.0), (s * 4.8, 30.2, -3.8), 0.28, 0.18, 'glow', label='frost_crack')
        f.beam(c, (s * 1.4, 27.5, -4.0), (s * 4.2, 26.5, -3.8), 0.25, 0.18, 'glow', label='frost_crack')

    # Central Hanging Frost Amulet / River Cocytus Wailing Core
    # Torc chains extending from neck
    f.beam(c, (-2.4, 31.0, -3.0), (0, 27.2, -3.6), 0.5, 0.4, 'steel', label='amulet_chain_l')
    f.beam(c, (2.4, 31.0, -3.0), (0, 27.2, -3.6), 0.5, 0.4, 'steel', label='amulet_chain_r')
    # Suspended runic frost medallion
    f.cube(c, (0, 26.6, -3.9), (2.4, 3.4, 0.8), 'runes', rot((0, 0, 0)), label='frost_amulet')
    f.cube(c, (0, 26.6, -4.1), (1.5, 2.2, 0.5), 'glow', rot((0, 0, 0)), label='amulet_glow')
    f.cube(c, (0, 24.6, -3.8), (0.8, 1.6, 0.6), 'ice', rot((0, 0, 45)), label='amulet_drop')

    # Back spine ridge with 4 vertebral ice stalagmites
    f.box(c, (-4.8, 24.0, 3.4), (9.6, 7.5, 1.0), 'iron', label='backplate', engrave='scales')
    f.beam(c, (0, 31.5, 4.0), (0, 23.5, 3.9), 1.0, 0.8, 'steel', label='spine')
    for y, h in ((24.4, 1.6), (26.4, 2.2), (28.4, 2.8), (30.4, 2.0)):
        f.spike(c, (0, y, 4.1), (0, y + 0.6, 4.1 + h), 0.85, 'ice', steps=3, label='spine_icicle')

# ---------------------------------------------------------------------------
# Pauldrons: Glacial Monolith Boulders & Quill Fan
# ---------------------------------------------------------------------------
def mantle_and_pauldrons(f):
    col = 'collar'
    # Arctic fur mantle draped across traps
    f.box(col, (-7.0, 30.0, -3.0), (14.0, 3.6, 7.6), 'fur', label='fur_mantle')
    for side, s in SIDES:
        f.cube(col, (s * 6.2, 29.0, 0.5), (3.8, 4.2, 7.2), 'fur', rot((0, 0, -s * 18)), label='fur_shoulder')
        # Frost-iron clasp buckles
        f.cube(col, (s * 3.2, 31.2, -3.2), (1.2, 1.2, 0.4), 'steel', rot((0, 0, 0)), label='mantle_clasp')

    for side, s in SIDES:
        sh = f'shoulder_{side}'
        top = np.array([s * 9.6, 32.2, 0.3])

        # Glacial Bedrock Pauldron Boulder (Massive chunky mountain stone)
        f.cube(sh, top + np.array([s * 1.0, 0.2, 0]), (6.8, 3.8, 7.4), 'stone', rot((0, 0, -s * 15)), label='pauldron_boulder')
        # Jagged packed-ice crust on top
        f.cube(sh, top + np.array([s * 1.6, 1.8, 0]), (5.8, 2.2, 6.6), 'ice', rot((0, 0, -s * 15)), label='pauldron_ice_cap')
        # Frost-iron rim band
        f.cube(sh, top + np.array([0, -1.8, 0]), (7.2, 0.65, 7.8), 'steel', rot((0, 0, -s * 15)), label='pauldron_band')

        # The Iconic Radiating Fan of 5 Ice Quills (Cocytus Signature Crest)
        # Positioned along the rear-top crest of each shoulder
        quill_defs = [
            # (dx, dy, dz, height, width, lean_x, lean_z)
            (-s * 0.8, 2.0, -2.4, 5.2, 1.2, s * 0.5, -0.6),
            (0,        2.2, -1.2, 7.0, 1.4, s * 1.0,  0.2),
            (s * 0.5,  2.4,  0.4, 8.6, 1.6, s * 1.5,  1.2),
            (s * 0.2,  2.2,  1.8, 7.2, 1.4, s * 1.2,  2.0),
            (-s * 0.6, 2.0,  3.0, 5.4, 1.1, s * 0.6,  2.6),
        ]
        for k, (bx, by, bz, qh, qw, lx, lz) in enumerate(quill_defs):
            base = top + np.array([bx, by, bz])
            tip = base + np.array([lx, qh, lz])
            f.spike(sh, base, tip, qw, 'ice', steps=4, label=f'shoulder_quill_{k}')

        # Cold blizzard mist billows emitting between boulders
        f.cube(sh, top + np.array([-s * 0.4, 2.2, 0.3]), (2.0, 0.35, 5.0), 'glow', rot((0, 0, -s * 15)), label='mist_vent')
        fb = f'flame_{side}'
        base_f = np.array([s * 10.4, 34.8, 0.6])
        for k, (dx, dz, h, w) in enumerate(((1, 0, 4.8, 2.8), (0, 1, 4.0, 2.4), (0.7, 0.7, 3.4, 1.8))):
            axis = unit(np.array([dx, 0, dz]))
            pts = []
            for i in range(7):
                u = i / 6
                x = (u - 0.5) * w
                tall = h * (0.6 + 0.4 * math.sin(math.pi * u)) if i % 2 == 0 else h * (0.35 + 0.2 * math.sin(math.pi * u))
                pts.append(base_f + axis * x + np.array([0, tall, 0]))
            pts.append(base_f + axis * w * 0.45 + np.array([0, -0.2, 0]))
            pts.append(base_f + axis * (-w * 0.45) + np.array([0, -0.2, 0]))
            f.sheet(fb, pts[::-1], 'flame', thickness=0.06, up=(0, 1, 0), label='frost_mist', seed=700 + k + (side == 'l') * 20)

# ---------------------------------------------------------------------------
# Arms & Gauntlets
# ---------------------------------------------------------------------------
def arms(f):
    for side, s in SIDES:
        sh, el, wr = J[f'shoulder_{side}'], J[f'elbow_{side}'], J[f'wrist_{side}']
        a, fa, hb = f'arm_{side}', f'forearm_{side}', f'hand_{side}'

        # Upper arm
        f.beam(a, sh + (0, -0.5, 0), el + (0, 0.4, 0), 3.4, 3.6, 'iron', label='arm_bone')
        for y in (28.0, 25.5):
            f.cube(a, (s * 8.6, y, 0.3), (3.8, 1.1, 4.0), 'steel', rot((0, 0, -s * 8)), label='arm_ring')

        # Forearm with flared ice vambrace
        f.beam(fa, el + (0, -0.2, 0), wr + (0, 0.2, 0), 4.0, 4.4, 'stone', label='vambrace', engrave='chevron')
        # Projecting elbow icicle spikes
        f.spike(fa, el + (-s * 1.0, 0, 1.8), el + (-s * 1.5, -0.6, 4.5), 1.3, 'ice', steps=3, label='elbow_spike')
        f.beam(fa, wr + (0, 0.8, -1.8), wr + (0, 0.8, 1.8), 4.4, 0.65, 'steel', label='cuff')

        # Hand / Gauntlet with claws
        f.box(hb, (wr[0] - 1.6, wr[1] - 2.8, wr[2] - 1.6), (3.2, 2.8, 3.2), 'iron', label='gauntlet')
        for i in range(4):
            x = (i - 1.5) * 0.72
            f.cube(hb, (wr[0] + x, wr[1] - 3.2, wr[2] - 1.6), (0.6, 1.2, 0.6), 'steel', label='knuckle')
            f.spike(hb, (wr[0] + x, wr[1] - 3.6, wr[2] - 1.6), (wr[0] + x, wr[1] - 5.0, wr[2] - 1.6), 0.5, 'ice', steps=2, label='claw')

# ---------------------------------------------------------------------------
# Colossal Segmented Glacial Greatbow (weapon_l) & Arrow Fist (weapon_r)
# ---------------------------------------------------------------------------
def greatbow(f):
    b = 'weapon_l'
    wr = J['wrist_l']
    # Riser / Grip center
    f.box(b, (wr[0] - 1.4, wr[1] - 3.2, wr[2] - 1.4), (2.8, 6.4, 2.8), 'steel', label='bow_riser')
    f.cube(b, (wr[0], wr[1], wr[2] - 1.5), (1.8, 1.8, 0.7), 'ice', rot((0, 0, 45)), label='bow_core_gem')
    f.cube(b, (wr[0], wr[1], wr[2] - 1.6), (1.1, 1.1, 0.5), 'glow', rot((0, 0, 45)), label='bow_core_glow')

    # Segmented Crystalline Limbs with outer quill brackets (Iconic Cocytus look)
    ctrl_up = [
        (wr[0], wr[1] + 3.0, wr[2]),
        (wr[0] + 1.2, wr[1] + 10.5, wr[2] - 1.8),
        (wr[0] + 0.6, wr[1] + 18.5, wr[2] - 3.8),
        (wr[0] - 0.4, wr[1] + 24.0, wr[2] - 1.2),
        (wr[0] - 1.0, wr[1] + 26.5, wr[2] + 2.2),
    ]
    ctrl_dn = [
        (wr[0], wr[1] - 3.0, wr[2]),
        (wr[0] + 1.2, wr[1] - 10.5, wr[2] - 1.8),
        (wr[0] + 0.6, wr[1] - 18.5, wr[2] - 3.8),
        (wr[0] - 0.4, wr[1] - 24.0, wr[2] - 1.2),
        (wr[0] - 1.0, wr[1] - 26.5, wr[2] + 2.2),
    ]

    for part_name, ctrl, y_sign in (('upper', ctrl_up, 1), ('lower', ctrl_dn, -1)):
        pts = [bezier(ctrl, i / 10) for i in range(11)]
        widths = [2.4 * (1 - i / 10 * 0.7) for i in range(10)]
        f.taper(b, pts, widths, widths, 'ice', z_hint=(0, 0, 1), label=f'bow_limb_{part_name}', overlap=0.2)

        # Crystalline rime blades and steel brackets on outer limb curve
        for k in (2, 5, 8):
            p, q = pts[k], pts[k + 1]
            f.beam(b, p - (q - p) * 0.2, p + (q - p) * 0.4, widths[k] + 0.5, widths[k] + 0.5, 'steel',
                   z_hint=(0, 0, 1), label=f'bow_bracket_{part_name}_{k}')
            f.spike(b, p, p + np.array([0.9, y_sign * 2.0, -3.8]), 0.9, 'blade', steps=3, label=f'bow_blade_{part_name}_{k}')

        # Sharp crystalline tip blade
        tip = pts[-1]
        f.cube(b, tip, (1.3, 1.6, 1.3), 'steel', rot((0, 0, 0)), label=f'bow_nock_{part_name}')
        f.spike(b, tip, tip + np.array([-0.5, y_sign * 4.5, 1.2]), 1.1, 'blade', steps=3, label=f'bow_tip_{part_name}')

    # Shimmering condensed blizzard string
    tip_up = bezier(ctrl_up, 1.0)
    tip_dn = bezier(ctrl_dn, 1.0)
    f.beam(b, tip_up, (wr[0], wr[1], wr[2] + 2.4), 0.25, 0.25, 'bow_string', label='string_up')
    f.beam(b, tip_dn, (wr[0], wr[1], wr[2] + 2.4), 0.25, 0.25, 'bow_string', label='string_dn')

    # Right Hand weapon_r: Manifested frost arrow nock / Spiked ice talon fist
    br = 'weapon_r'
    wrr = J['wrist_r']
    f.spike(br, (wrr[0], wrr[1] - 1.5, wrr[2]), (wrr[0], wrr[1] - 7.5, wrr[2] - 0.5), 1.3, 'ice', steps=4, label='arrow_fist')
    f.cube(br, (wrr[0], wrr[1] - 4.0, wrr[2] - 0.2), (1.4, 0.5, 1.4), 'steel', label='arrow_collar')

# ---------------------------------------------------------------------------
# Lower Body, Greaves & Tassets
# ---------------------------------------------------------------------------
def lower_body(f):
    p = 'pelvis'
    # Heavy permafrost iron girdle
    f.box(p, (-5.5, 14.5, -3.4), (11.0, 3.8, 6.8), 'iron', label='belt')
    f.cube(p, (0, 16.4, -3.6), (2.6, 2.6, 0.9), 'steel', rot((0, 0, 45)), label='belt_buckle')
    f.cube(p, (0, 16.4, -3.8), (1.6, 1.6, 0.6), 'ice', rot((0, 0, 45)), label='buckle_gem')

    # Left & Right glacial slab tassets
    for side, s in SIDES:
        sb = f'sash_{side}'
        top = np.array([s * 5.4, 16.5, 0.0])
        tiers = (
            (4.2, 2.4, 6.0, 16, (0.4, -1.1, 0)),
            (3.8, 2.2, 5.6, 26, (1.2, -3.0, 0.1)),
            (3.2, 2.0, 5.0, 36, (2.0, -4.8, 0.2))
        )
        for k, (w, h, d, angle, off) in enumerate(tiers):
            r = rot((0, 0, -s * angle))
            center = top + np.array([s * off[0], off[1], off[2]])
            f.cube(sb, center, (w, h, d), 'ice', r, label='tasset_slab')
            f.cube(sb, center + r @ np.array([s * (w / 2 - 0.2), -h / 2 + 0.1, 0]), (0.6, 0.4, d + 0.25), 'steel', r, label='tasset_trim')

    # Front River Cocytus wailing waves tabard
    tab = [
        (-2.6, 15.0, -3.5), (2.6, 15.0, -3.5),
        (2.4, 7.5, -3.8), (1.2, 6.2, -3.8),
        (0.0, 7.0, -3.8), (-1.2, 6.2, -3.8),
        (-2.4, 7.5, -3.8)
    ]
    f.sheet(p, tab, 'cloth', thickness=0.1, up=(0, 1, 0), label='tabard',
            emblem=dict(kind='wave', r=0.3, y=0.55, tone='trim'))

    # Legs & Boots (Featuring Original Cocytus Knee Spikes, Shin Bands & Heel Spurs)
    for side, s in SIDES:
        hip, kn, an = J[f'hip_{side}'], J[f'knee_{side}'], J[f'ankle_{side}']
        l, sh, ft = f'leg_{side}', f'shin_{side}', f'foot_{side}'

        # Thigh
        f.beam(l, hip + (0, -0.4, 0), kn + (0, 0.4, 0), 3.6, 4.0, 'iron', label='thigh')
        f.cube(l, hip + (0, -3.5, 0), (3.8, 1.8, 4.2), 'stone', label='thigh_plate', engrave='lines')

        # Greave base
        f.beam(sh, kn + (0, -0.2, 0), an + (0, 0.2, 0), 3.8, 4.2, 'stone', label='greave')

        # 1. Knee Crown of Ice (Iconic Cocytus upward knee spikes)
        f.spike(sh, kn + (0, 0.2, -1.8), kn + (0, 1.2, -5.0), 1.6, 'ice', steps=4, label='knee_spike_main')
        f.spike(sh, kn + (s * 1.0, 0.0, -1.6), kn + (s * 1.4, 0.6, -4.2), 1.0, 'ice', steps=3, label='knee_spike_side')

        # 2. 4 Horizontal Rime Bindings (Iconic Cocytus shin straps)
        for y in (7.8, 6.0, 4.4, 2.8):
            f.cube(sh, (s * 3.6, y, -0.2), (4.2, 0.65, 4.4), 'steel', label='shin_binding')
            f.cube(sh, (s * 3.6, y, -2.1), (2.0, 0.55, 0.4), 'ice', label='shin_ice_clasp')

        # 3. Rear Heel Stalactite Spurs (Iconic Cocytus rear spikes)
        for y, len_z in ((6.2, 3.2), (4.2, 3.8), (2.4, 3.0)):
            f.spike(sh, (s * 3.6, y, 1.9), (s * 3.6, y - 0.8, 1.9 + len_z), 0.95, 'ice', steps=3, label='heel_spur')

        # Heavy Ice Boot & Sabaton
        f.box(ft, (an[0] - 2.2, 0.0, an[2] - 3.4), (4.4, 3.4, 6.2), 'iron', label='boot')
        f.box(ft, (an[0] - 2.4, 0.0, an[2] - 3.6), (4.8, 0.9, 6.6), 'steel', label='boot_cleat')
        f.spike(ft, (an[0], 1.2, an[2] - 3.4), (an[0], 1.6, an[2] - 5.5), 1.0, 'ice', steps=3, label='toe_cleat')

# ---------------------------------------------------------------------------
# Fur-Lined Frost Cloak with Hanging Icicles
# ---------------------------------------------------------------------------
def cape(f):
    c = 'cape'
    # Upper fur-backed cloak
    top_poly = [
        (-6.0, 29.0, 3.4), (6.0, 29.0, 3.4),
        (7.2, 18.0, 4.4), (-7.2, 18.0, 4.4)
    ]
    f.sheet(c, top_poly, 'cloth', thickness=0.1, up=(0, 1, 0), label='cape_upper',
            folds=4, lining=('north', ARCTIC_FUR[:4]))

    # Lower cloak with hanging crystalline icicles along hem
    ct = 'cape_tip'
    bot_poly = [
        (-7.2, 18.0, 4.4), (7.2, 18.0, 4.4),
        (8.0, 3.0, 5.0), (5.0, 1.5, 5.0),
        (2.0, 2.5, 5.0), (0.0, 1.0, 5.0),
        (-2.0, 2.5, 5.0), (-5.0, 1.5, 5.0),
        (-8.0, 3.0, 5.0)
    ]
    f.sheet(ct, bot_poly, 'cloth', thickness=0.1, up=(0, 1, 0), label='cape_lower',
            folds=5, edge={3: ('tatter', 0.8, 4), 5: ('tatter', 0.8, 4)},
            hem=0.25, lining=('north', ARCTIC_FUR[:4]))

    # Hanging crystalline icicles dripping from the cloak hem
    for x, len_y in ((-6.5, 2.4), (-4.0, 3.2), (-1.5, 2.2), (0.0, 3.8), (1.5, 2.2), (4.0, 3.2), (6.5, 2.4)):
        f.spike(ct, (x, 3.0, 5.0), (x, 3.0 - len_y, 5.0), 0.75, 'ice', steps=3, label='cape_icicle')

# ---------------------------------------------------------------------------
# Floating Glacial Monolith Constellation (Halo)
# ---------------------------------------------------------------------------
def halo(f):
    h = 'halo'
    # 7 hovering glacial monolith shards arranged in an arc behind the titan
    for i in range(7):
        a = math.radians(-120 + i * 40)
        rx = 8.5 * math.sin(a)
        ry = 36.5 + 7.5 * math.cos(a)
        center = np.array([rx, ry, 3.6])
        h_crystal = 3.6 if i % 2 == 0 else 2.6
        f.cube(h, center, (1.2, h_crystal, 0.9), 'ice', rot((10, 0, -math.degrees(a) * 0.5)), label='halo_monolith')
        f.cube(h, center, (0.7, h_crystal * 0.65, 0.5), 'glow', rot((10, 0, -math.degrees(a) * 0.5)), label='halo_core')

# ---------------------------------------------------------------------------
# Master Sculpt Function
# ---------------------------------------------------------------------------
def sculpt():
    f = Forge('arkhaia_cocytus', MATERIALS, seed=404)
    skeleton(f)
    helm(f)
    torso(f)
    mantle_and_pauldrons(f)
    arms(f)
    greatbow(f)
    lower_body(f)
    cape(f)
    halo(f)
    return f

# ---------------------------------------------------------------------------
# Kinematics & Animations (Exact Combat Contract)
# ---------------------------------------------------------------------------
def stance_keys():
    """Default Titan combat idle posture."""
    return dict(
        pelvis={'rot': [0, 0, 0], 'pos': [0, 0, 0]},
        chest=[-2, 0, 0],
        neck=[2, 0, 0],
        hi_gaze=[0, 0, 0],
        head=[0, 0, 0],
        jaw=[0, 0, 0],
        collar=[0, 0, 0],
        cape=[-6, 0, 0],
        cape_tip=[-8, 0, 0],
        halo=[0, 0, 0],
        shoulder_r=[4, 0, 2],
        arm_r=[16, -12, 14],
        forearm_r=[-24, 0, 0],
        hand_r=[-10, 0, 0],
        weapon_r=[0, 0, 0],
        flame_r=[0, 0, 0],
        shoulder_l=[6, 0, -2],
        arm_l=[22, 14, -18],
        forearm_l=[-32, 0, 0],
        hand_l=[15, 0, 0],
        weapon_l=[-10, 0, 5],
        flame_l=[0, 0, 0],
        leg_r=[-4, 0, -4],
        shin_r=[8, 0, 0],
        foot_r=[-4, 0, 4],
        sash_r=[0, 0, 0],
        leg_l=[-4, 0, 4],
        shin_l=[8, 0, 0],
        foot_l=[-4, 0, -4],
        sash_l=[0, 0, 0],
    )

def breath(t, length=2.4):
    b = math.sin(t / length * math.tau)
    return dict(
        chest=[-2 - 1.5 * b, 0, 0],
        pelvis={'rot': [0, 0, 0], 'pos': [0, 0.15 * b, 0]},
        head=[1.2 * b, 0, 0],
        arm_l=[22 + 1.2 * b, 14, -18],
        arm_r=[16 + 1.2 * b, -12, 14],
        cape=[-6 - 1.5 * b, 0, 0],
        cape_tip=[-8 - 2.5 * b, 0, 0],
        halo=[0, 0, 3.0 * math.sin(t / length * math.tau)]
    )

def clips(forge):
    base = stance_keys()
    out = []

    # 1. IDLE (2.4s loop)
    def idle_pose(t):
        p = dict(base)
        b = breath(t, 2.4)
        for k, v in b.items():
            if isinstance(v, dict):
                p[k] = v
            elif k in p:
                p[k] = [p[k][i] + v[i] for i in range(3)]
        return p

    out.append(Clip('idle', 2.4, idle_pose, loop=True))

    # 2. WALK (1.2s loop) - Thunderous Titan Stride
    def walk_pose(t):
        p = dict(base)
        phase = t / 1.2 * math.tau
        sw = math.sin(phase)
        # Pelvis sway & dip
        p['pelvis'] = {'rot': [2, sw * 4, -sw * 2], 'pos': [sw * 0.4, -abs(sw) * 0.35, 0]}
        p['chest'] = [-4, -sw * 4, sw * 2]
        p['head'] = [2, 0, 0]
        # Alternate legs with grounded foot placement
        p['leg_r'] = [-sw * 26, 0, -4]
        p['shin_r'] = [max(0.0, sw * 32), 0, 0]
        p['foot_r'] = [-sw * 12, 0, 4]

        p['leg_l'] = [sw * 26, 0, 4]
        p['shin_l'] = [max(0.0, -sw * 32), 0, 0]
        p['foot_l'] = [sw * 12, 0, -4]

        # Arms swing opposite to legs
        p['arm_r'] = [16 + sw * 18, -12, 14]
        p['arm_l'] = [22 - sw * 18, 14, -18]
        p['cape'] = [-10 + abs(sw) * 5, 0, 0]
        p['cape_tip'] = [-14 + abs(sw) * 8, 0, 0]
        p['halo'] = [0, 0, 5.0 * sw]
        return p

    out.append(Clip('walk', 1.2, walk_pose, loop=True))

    # 3. HOVER (2.0s loop) - Blizzard levitation
    def hover_pose(t):
        p = dict(base)
        h = math.sin(t / 2.0 * math.tau)
        p['pelvis'] = {'rot': [-6, 0, 0], 'pos': [0, 1.2 + 0.5 * h, 0]}
        p['chest'] = [6, 0, 0]
        p['head'] = [-4, 0, 0]
        p['leg_r'] = [16 + 2 * h, 0, -4]
        p['shin_r'] = [-24, 0, 0]
        p['foot_r'] = [12, 0, 4]
        p['leg_l'] = [18 - 2 * h, 0, 4]
        p['shin_l'] = [-28, 0, 0]
        p['foot_l'] = [14, 0, -4]
        p['arm_l'] = [12, 10, -28]
        p['arm_r'] = [10, -10, 24]
        p['cape'] = [-18 + 4 * h, 0, 0]
        p['cape_tip'] = [-26 + 6 * h, 0, 0]
        p['halo'] = [0, 0, 6.0 * h]
        return p

    out.append(Clip('hover', 2.0, hover_pose, loop=True))

    # 4. CAST (Blizzard / Frost Tempest): windup (1.0s), active (0.5s), recover (0.7s)
    def cast_windup(t):
        u = ease(t / 1.0)
        p = dict(base)
        p['pelvis'] = {'rot': [-u * 8, 0, 0], 'pos': [0, -u * 0.4, 0]}
        p['chest'] = [-u * 12, 0, 0]
        p['head'] = [-u * 14, 0, 0]
        p['arm_l'] = [lerp(22, 110, u), 14, -18]
        p['forearm_l'] = [lerp(-32, -15, u), 0, 0]
        p['weapon_l'] = [-10 + u * 25, 0, 5]
        p['arm_r'] = [lerp(16, 95, u), -12, 14]
        p['forearm_r'] = [lerp(-24, -20, u), 0, 0]
        p['halo'] = [0, 0, u * 15]
        return p

    def cast_active(t):
        u = ease_out(t / 0.5)
        p = dict(base)
        p['pelvis'] = {'rot': [lerp(-8, 14, u), 0, 0], 'pos': [0, -0.6, 0]}
        p['chest'] = [lerp(-12, 22, u), 0, 0]
        p['head'] = [lerp(-14, 18, u), 0, 0]
        p['arm_l'] = [lerp(110, -15, u), 14, -18]
        p['forearm_l'] = [-15, 0, 0]
        p['arm_r'] = [lerp(95, -20, u), -12, 14]
        p['forearm_r'] = [-20, 0, 0]
        p['cape'] = [lerp(-6, 18, u), 0, 0]
        p['cape_tip'] = [lerp(-8, 28, u), 0, 0]
        p['halo'] = [0, 0, 15 - u * 25]
        return p

    def cast_recover(t):
        u = ease(t / 0.7)
        p = dict(base)
        p['pelvis'] = {'rot': [lerp(14, 0, u), 0, 0], 'pos': [0, lerp(-0.6, 0, u), 0]}
        p['chest'] = [lerp(22, -2, u), 0, 0]
        p['head'] = [lerp(18, 0, u), 0, 0]
        p['arm_l'] = [lerp(-15, 22, u), 14, -18]
        p['forearm_l'] = [lerp(-15, -32, u), 0, 0]
        p['arm_r'] = [lerp(-20, 16, u), -12, 14]
        p['forearm_r'] = [lerp(-20, -24, u), 0, 0]
        p['cape'] = [lerp(18, -6, u), 0, 0]
        p['cape_tip'] = [lerp(28, -8, u), 0, 0]
        p['halo'] = [0, 0, lerp(-10, 0, u)]
        return p

    out.append(Clip('cast_windup', 1.0, cast_windup))
    out.append(Clip('cast_active', 0.5, cast_active))
    out.append(Clip('cast_recover', 0.7, cast_recover))

    # 5. ATTACK (Glacial Spike Slam / Melee Cleave): windup (0.8s), active (0.4s), recover (0.7s)
    def attack_windup(t):
        u = ease(t / 0.8)
        p = dict(base)
        p['pelvis'] = {'rot': [0, u * 18, 0], 'pos': [0, -u * 0.3, 0]}
        p['chest'] = [-u * 8, u * 14, 0]
        p['head'] = [u * 6, -u * 16, 0]
        p['arm_r'] = [lerp(16, 95, u), -24, 30]
        p['forearm_r'] = [lerp(-24, -60, u), 0, 0]
        p['hand_r'] = [-30, 0, 0]
        p['arm_l'] = [lerp(22, -10, u), 10, -22]
        return p

    def attack_active(t):
        u = ease_out(t / 0.4)
        p = dict(base)
        p['pelvis'] = {'rot': [12, lerp(18, -16, u), 0], 'pos': [0, -0.5, 0]}
        p['chest'] = [18, lerp(14, -18, u), 0]
        p['head'] = [14, lerp(-16, 12, u), 0]
        p['arm_r'] = [lerp(95, -35, u), -10, 10]
        p['forearm_r'] = [lerp(-60, -10, u), 0, 0]
        p['hand_r'] = [20, 0, 0]
        p['arm_l'] = [lerp(-10, 15, u), 14, -18]
        return p

    def attack_recover(t):
        u = ease(t / 0.7)
        p = dict(base)
        p['pelvis'] = {'rot': [lerp(12, 0, u), lerp(-16, 0, u), 0], 'pos': [0, lerp(-0.5, 0, u), 0]}
        p['chest'] = [lerp(18, -2, u), lerp(-18, 0, u), 0]
        p['head'] = [lerp(14, 0, u), lerp(12, 0, u), 0]
        p['arm_r'] = [lerp(-35, 16, u), -12, 14]
        p['forearm_r'] = [lerp(-10, -24, u), 0, 0]
        p['arm_l'] = [lerp(15, 22, u), 14, -18]
        return p

    out.append(Clip('attack_windup', 0.8, attack_windup))
    out.append(Clip('attack_active', 0.4, attack_active))
    out.append(Clip('attack_recover', 0.7, attack_recover))

    # 6. RAIN (The Iconic Skyward Frost Bow Volley): windup (1.0s), active (0.5s), recover (0.7s)
    def rain_windup(t):
        u = ease(t / 1.0)
        p = dict(base)
        p['pelvis'] = {'rot': [-u * 10, -u * 14, 0], 'pos': [0, -u * 0.3, 0]}
        p['chest'] = [-u * 14, -u * 10, 0]
        p['head'] = [-u * 22, 0, 0]
        # Extend Greatbow high upward (angled 75 degrees skyward)
        p['arm_l'] = [lerp(22, 105, u), 10, -12]
        p['forearm_l'] = [lerp(-32, -8, u), 0, 0]
        p['hand_l'] = [lerp(15, -15, u), 0, 0]
        p['weapon_l'] = [lerp(-10, -45, u), 0, 0]
        # Right hand draws string back to cheek with high tension
        p['arm_r'] = [lerp(16, 92, u), -18, 28]
        p['forearm_r'] = [lerp(-24, -95, u), 0, 0]
        p['hand_r'] = [-40, 0, 0]
        return p

    def rain_active(t):
        u = ease_out(t / 0.5)
        p = dict(base)
        # Release! Upward recoil on bow, arm snaps back
        p['pelvis'] = {'rot': [-10 + u * 4, -14, 0], 'pos': [0, -0.3, 0]}
        p['chest'] = [-14 + u * 6, -10, 0]
        p['head'] = [-22 + u * 5, 0, 0]
        # Bow kicks with recoil
        p['arm_l'] = [105 + (1 - u) * 12, 10, -12]
        p['forearm_l'] = [-8, 0, 0]
        p['weapon_l'] = [-45 - (1 - u) * 8, 0, 0]
        # Right arm snaps release
        p['arm_r'] = [lerp(92, 45, u), 10, 15]
        p['forearm_r'] = [lerp(-95, -15, u), 0, 0]
        p['hand_r'] = [10, 0, 0]
        return p

    def rain_recover(t):
        u = ease(t / 0.7)
        p = dict(base)
        p['pelvis'] = {'rot': [lerp(-6, 0, u), lerp(-14, 0, u), 0], 'pos': [0, lerp(-0.3, 0, u), 0]}
        p['chest'] = [lerp(-8, -2, u), lerp(-10, 0, u), 0]
        p['head'] = [lerp(-17, 0, u), 0, 0]
        p['arm_l'] = [lerp(105, 22, u), 14, -18]
        p['forearm_l'] = [lerp(-8, -32, u), 0, 0]
        p['weapon_l'] = [lerp(-45, -10, u), 0, 5]
        p['arm_r'] = [lerp(45, 16, u), -12, 14]
        p['forearm_r'] = [lerp(-15, -24, u), 0, 0]
        return p

    out.append(Clip('rain_windup', 1.0, rain_windup))
    out.append(Clip('rain_active', 0.5, rain_active))
    out.append(Clip('rain_recover', 0.7, rain_recover))

    # 7. STUN (1.5s once) - Heavy titan stagger
    def stun_pose(t):
        u = t / 1.5
        tremble = math.sin(t * 32.0) * 1.5
        if u < 0.35:
            # Impact blow throws back
            f_u = ease_out(u / 0.35)
            p = dict(base)
            p['pelvis'] = {'rot': [lerp(0, -18, f_u), 0, 0], 'pos': [0, -f_u * 0.8, -f_u * 1.5]}
            p['chest'] = [lerp(-2, -22, f_u) + tremble, 0, 0]
            p['head'] = [lerp(0, -28, f_u) + tremble, 0, 0]
            p['arm_l'] = [lerp(22, 45, f_u), 20, -35]
            p['arm_r'] = [lerp(16, 40, f_u), -20, 35]
            p['leg_r'] = [18, 0, -4]
            p['shin_r'] = [-32, 0, 0]
            p['leg_l'] = [-14, 0, 4]
            p['shin_l'] = [28, 0, 0]
            return p
        else:
            # Slow recovery
            f_u = ease((u - 0.35) / 0.65)
            p = dict(base)
            p['pelvis'] = {'rot': [lerp(-18, 0, f_u), 0, 0], 'pos': [0, lerp(-0.8, 0, f_u), lerp(-1.5, 0, f_u)]}
            p['chest'] = [lerp(-22, -2, f_u) + tremble * (1 - f_u), 0, 0]
            p['head'] = [lerp(-28, 0, f_u) + tremble * (1 - f_u), 0, 0]
            p['arm_l'] = [lerp(45, 22, f_u), 14, -18]
            p['arm_r'] = [lerp(40, 16, f_u), -12, 14]
            p['leg_r'] = [lerp(18, -4, f_u), 0, -4]
            p['shin_r'] = [lerp(-32, 8, f_u), 0, 0]
            p['leg_l'] = [lerp(-14, -4, f_u), 0, 4]
            p['shin_l'] = [lerp(28, 8, f_u), 0, 0]
            return p

    out.append(Clip('stun', 1.5, stun_pose))
    return out

# ---------------------------------------------------------------------------
# Build & Export
# ---------------------------------------------------------------------------
def build():
    f = sculpt()
    all_clips = clips(f)
    hitbox = [[-14, 0, -14], [14, 46, 14]]
    meta = dict(
        evergarden_revision=REVISION,
        evergarden_scale=SCALE
    )
    model, atlas, stats = build_model(
        f, all_clips, 'arkhaia_cocytus', 'cocytus', hitbox,
        meta=meta, size=1024, density=8.5
    )
    return model, atlas, stats

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', type=Path, default=OUT)
    args = parser.parse_args()

    model, atlas, stats = build()
    args.out.mkdir(parents=True, exist_ok=True)
    target = args.out / 'arkhaia_cocytus.bbmodel'
    target.write_text(dump(model) + '\n', encoding='utf8')

    art = ROOT / 'evergarden/art/arkhaia-rivers'
    art.mkdir(parents=True, exist_ok=True)
    atlas.save(art / 'cocytus_titan.png')

    print(f"Cocytus Remastered v2: {len(model['elements']) - 1} cubes, {len(sculpt().bones)} bones, "
          f"{len(model['animations'])} clips, atlas {stats['coverage']:.0%} coverage at {stats['density']:.1f}px/unit")

if __name__ == '__main__':
    main()
