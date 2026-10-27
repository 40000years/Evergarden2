"""Acheron, the Crimson Sovereign: Remastered River Woe ModelEngine R4 Blueprint.

A lethal, agile phantom assassin sovereign sculpted, painted, and rigged using
Evergarden's high-fidelity procedural pipeline with authentic CRIMSON RED identity:
- Persona: Crimson Sovereign of River Acheron, dual-wielding Sorrow-Quill ruby blades.
- Signature Palette: Vibrant Crimson Red (#f54268), Blood Rose Glow, Dark Obsidian Steel.
- Dual Crimson Sorrow-Quills: Attached to weapon_r and weapon_l,
  faceted ruby stilettos with sweeping quillons and glowing hollow crimson fullers.
- Crimson Assassin Mask: Chiseled sorrow mask with red tear-streak engravings,
  piercing crimson eyes (#f54268), and swept blood-crystal horn crests.
- Asymmetric Duelist Pauldrons: Heavy spiked blade-breaker on right, articulated guard on left.
- Continuous Crimson Cuirass: Solid from y=14.0 to y=32.5 (no gaps!),
  central Acheron Ruby Core, segmented abdominal lames.
- 100% preservation of all 13 authored animation clips from arkhaia_acheron.bbmodel.bak:
  idle, walk, hover, stun,
  cast (windup/active/recover), attack (windup/active/recover),
  ritual (windup/active/recover).
- Automatic generation of arkhaia_acheron_echo.bbmodel (0.6x scale clone in red).
"""
import argparse
import copy
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
SERVA_OUT = Path('C:/Users/User/Desktop/Serva/plugins/ModelEngine/blueprints')
SCALE = 4.0
REVISION = 'acheron-crimson-v3'

# ---------------------------------------------------------------------------
# Authentic Crimson Color Palettes & Materials
# ---------------------------------------------------------------------------
CRIMSON_PLATE = ['#1a060d', '#2c0915', '#450d21', '#661331', '#8f1a45', '#bf225d', '#e0336e', '#f5427d']
BLOOD_ROSE_GLOW = ['#2e0410', '#5e0820', '#9c0f35', '#d9184a', '#f54268', '#ff6b8c', '#ffaec0', '#ffffff']
OBSIDIAN_STEEL = ['#0c0b12', '#14131d', '#1f1c2b', '#2c273c', '#3e3754', '#554b73', '#706496', '#8f80c0']
GOLD_FILIGREE = ['#1a1205', '#2c1e08', '#46300d', '#684814', '#93661d', '#c28828', '#e8ab45', '#ffd580']
RUBY_QUILL = ['#2a040e', '#50081a', '#850e2c', '#c01540', '#f5275a', '#ff5c85', '#ffa1bc', '#ffffff']
CRIMSON_SILK = ['#16050b', '#280712', '#3e0a1c', '#5a0d27', '#7c1236', '#a31947', '#cc225a', '#f54278']
EYE_RED = ['#f54268', '#ff7090', '#ffb3c5', '#ffffff']

MATERIALS = {
    'plate': Material(CRIMSON_PLATE, 'plate', accent=BLOOD_ROSE_GLOW, density=0.9),
    'trim': Material(OBSIDIAN_STEEL, 'trim', density=1.0),
    'gold': Material(GOLD_FILIGREE, 'trim', density=1.0),
    'iron': Material(OBSIDIAN_STEEL, 'plate', density=0.85),
    'cloth': Material(CRIMSON_SILK, 'cloth', trim=OBSIDIAN_STEEL, accent=BLOOD_ROSE_GLOW, density=0.85),
    'blade': Material(RUBY_QUILL, 'blade', density=1.1),
    'glow': Material(BLOOD_ROSE_GLOW, 'glow', glow=True, density=1.5),
    'eye': Material(EYE_RED, 'glow', glow=True, density=1.6),
    'runes': Material(BLOOD_ROSE_GLOW, 'runes', glow=True, density=1.4),
    'crystal': Material(RUBY_QUILL, 'gem', glow=True, density=1.3),
}

SIDES = (('r', -1), ('l', 1))

# ---------------------------------------------------------------------------
# Exact Joint Pivots from original rig
# ---------------------------------------------------------------------------
def joints():
    j = {
        'root': (0, 0, 0),
        'pelvis': (0, 15, 0),
        'chest': (0, 24, 0),
        'neck': (0, 29, 0),
        'hi_gaze': (0, 34, 0),
        'head': (0, 34, 0),
        'head_forward': (0, 34, -7),
        'jaw': (0, 33.5, -2.5),
        'collar': (0, 28, 1),
        'cape': (0, 28, 3),
        'cape_tip': (0, 15, 4),
        'focus': (-10, 18, -5),
        'familiar': (13, 25, 0),
        'halo': (0, 35, 3),
    }
    for i, x in enumerate([-3.625, -2.175, -0.725, 0.725, 2.175, 3.625]):
        j[f'veil_{i}'] = (x, 29, 3.3)
    for side, s in SIDES:
        j.update({
            f'shoulder_{side}': (s * 7.7, 28, 0),
            f'arm_{side}': (s * 7.7, 28, 0),
            f'forearm_{side}': (s * 8.3, 21, -1),
            f'hand_{side}': (s * 8.2, 15, -2.8),
            f'weapon_{side}': (s * 8.2, 15, -2.8),
            f'leg_{side}': (s * 2.8, 15, 0),
            f'shin_{side}': (s * 2.8, 8, 0),
            f'foot_{side}': (s * 2.8, 1.5, 0),
            f'sash_{side}': (s * 3.8, 15, 0),
        })
    return {k: np.array(v, float) for k, v in j.items()}

J = joints()

# ---------------------------------------------------------------------------
# Skeleton Definition
# ---------------------------------------------------------------------------
def skeleton(f):
    f.bone('root', None, J['root'])
    f.bone('pelvis', 'root', J['pelvis'])
    f.bone('chest', 'pelvis', J['chest'])
    f.bone('neck', 'chest', J['neck'])
    f.bone('hi_gaze', 'neck', J['hi_gaze'])
    f.bone('head', 'hi_gaze', J['head'])
    f.bone('head_forward', 'head', J['head_forward'])
    f.bone('jaw', 'head', J['jaw'])
    f.bone('collar', 'chest', J['collar'])
    for i in range(6):
        f.bone(f'veil_{i}', 'collar', J[f'veil_{i}'])
    f.bone('cape', 'chest', J['cape'])
    f.bone('cape_tip', 'cape', J['cape_tip'])
    f.bone('focus', 'chest', J['focus'])
    f.bone('familiar', 'chest', J['familiar'])
    f.bone('halo', 'chest', J['halo'])

    for side, s in SIDES:
        f.bone(f'shoulder_{side}', 'chest', J[f'shoulder_{side}'])
        f.bone(f'arm_{side}', 'chest', J[f'arm_{side}'])
        f.bone(f'forearm_{side}', f'arm_{side}', J[f'forearm_{side}'])
        f.bone(f'hand_{side}', f'forearm_{side}', J[f'hand_{side}'])
        f.bone(f'weapon_{side}', f'hand_{side}', J[f'weapon_{side}'])
        f.bone(f'leg_{side}', 'pelvis', J[f'leg_{side}'])
        f.bone(f'shin_{side}', f'leg_{side}', J[f'shin_{side}'])
        f.bone(f'foot_{side}', f'shin_{side}', J[f'foot_{side}'])
        f.bone(f'sash_{side}', 'pelvis', J[f'sash_{side}'])

# ---------------------------------------------------------------------------
# Head & Mask: Crimson Sorrow Mask & Swept Blood-Crystal Horns
# ---------------------------------------------------------------------------
def head_mask(f):
    b = 'head'
    # Crimson Dreadplate Cranium
    f.box(b, (-2.7, 32.5, -2.6), (5.4, 6.0, 5.4), 'plate', label='cranium')

    # Crimson Faceplate Mask with Dark Obsidian Brow
    f.box(b, (-2.4, 32.2, -3.3), (4.8, 5.8, 0.8), 'plate', label='crimson_mask')
    f.beam(b, (-2.6, 36.6, -3.4), (2.6, 36.6, -3.4), 0.9, 0.6, 'trim', label='brow_bar')

    # Piercing Glowing Blood-Rose Eyes (#f54268) with Crimson Tear Channels
    for side, s in SIDES:
        # Eye socket
        f.box(b, (s * 1.15 - 0.6, 34.6, -3.4), (1.2, 0.55, 0.3), 'iron', label='eye_socket')
        # Piercing glowing eye slit
        f.cube(b, (s * 1.15, 34.8, -3.45), (1.1, 0.3, 0.2), 'eye', rot((0, 0, -s * 15)), label='eye_slit')
        # Crimson sorrow tear streak running down cheek
        f.beam(b, (s * 1.15, 34.6, -3.42), (s * 1.05, 32.2, -3.42), 0.22, 0.15, 'glow', label='crimson_tear')

    # Swept Crimson Ram / Blood-Crystal Horns
    for side, s in SIDES:
        horn_pts = [
            np.array([s * 2.3, 36.8, -0.4]),
            np.array([s * 5.4, 39.5, 0.8]),
            np.array([s * 7.8, 43.5, 1.8]),
            np.array([s * 6.8, 48.0, 1.2])
        ]
        f.taper(b, horn_pts, [1.6, 1.3, 0.9], [1.4, 1.1, 0.75], 'blade', z_hint=(0, 0, 1), label='crimson_horn')
        # Gold ring around horn base
        f.beam(b, horn_pts[0], horn_pts[1], 0.5, 0.4, 'gold', label='horn_gold_ring')

    # Central High Assassin Crest
    f.spike(b, (0, 38.0, -1.8), (0, 42.8, 0.2), 1.2, 'plate', steps=3, label='crown_crest')
    f.spike(b, (0, 38.2, 0.2), (0, 41.5, 2.2), 0.95, 'plate', steps=2, label='crown_crest_rear')

    # Jaw Guard (jaw bone)
    jb = 'jaw'
    f.box(jb, (-2.0, 31.8, -3.1), (4.0, 1.8, 1.8), 'plate', label='jaw_guard')
    f.spike(jb, (0, 32.0, -3.2), (0, 30.2, -3.3), 0.95, 'blade', steps=2, label='chin_blade')

# ---------------------------------------------------------------------------
# Torso & Cuirass: Crimson Dreadplate & Ruby Soul Core
# ---------------------------------------------------------------------------
def torso(f):
    c = 'chest'
    # Solid, continuous torso tiers from y=14.0 to y=32.5 (Zero gaps!)
    tiers = [
        (14.0, 16.8, 7.2, 5.4, 0.1),   # Waist Fauld Base (overlaps pelvis war belt)
        (16.8, 19.8, 7.8, 5.6, 0.15),  # Lower Abdomen
        (19.8, 23.0, 8.8, 6.0, 0.2),   # Mid Abdomen / Obliques
        (23.0, 26.2, 10.2, 6.4, 0.3),  # Upper Abdomen / Solar Plexus
        (26.2, 29.5, 11.6, 6.8, 0.4),  # Lower Pectorals
        (29.5, 32.5, 12.6, 7.0, 0.5)   # Upper Pectorals / Clavicle
    ]
    for i, (y0, y1, w, d, zc) in enumerate(tiers):
        f.box(c, (-w / 2, y0, zc - d / 2), (w, y1 - y0, d), 'plate' if i >= 2 else 'iron',
              label='torso_tier', engrave='lines' if i >= 2 else None)

    # Articulated Crimson Abdominal Lames (Continuous from y=14.5 to y=24.5)
    ab_lames = [
        (14.5, 6.6, 0.75),
        (16.2, 7.0, 0.8),
        (17.9, 7.6, 0.85),
        (19.8, 8.2, 0.9),
        (21.7, 8.8, 0.95),
        (23.6, 9.4, 1.0)
    ]
    for i, (y_pos, w, h) in enumerate(ab_lames):
        f.cube(c, (0, y_pos + h / 2, -2.6 - i * 0.1), (w, h, 0.8), 'plate', rot((-8, 0, 0)), label=f'ab_plate_{i}')
        f.cube(c, (0, y_pos + 0.15, -2.85 - i * 0.1), (w + 0.3, 0.25, 0.5), 'trim', rot((-8, 0, 0)), label=f'ab_trim_{i}')

    # Flank Guards (Covering sides between y=14.2 and y=22.5)
    for side, s in SIDES:
        f.cube(c, (s * 4.0, 17.5, 0.1), (0.9, 7.0, 5.2), 'plate', rot((0, 0, -s * 6)), label='flank_guard')
        f.cube(c, (s * 4.2, 17.5, 0.1), (0.7, 6.8, 5.4), 'blade', rot((0, 0, -s * 6)), label='flank_trim')

    # Layered Crimson Pectoral Plates
    for side, s in SIDES:
        f.cube(c, (s * 3.3, 28.6, -3.2), (5.0, 4.4, 1.2), 'plate', rot((-6, -s * 16, -s * 5)), label='pectoral_plate')
        f.cube(c, (s * 3.3, 28.6, -3.5), (5.3, 0.6, 1.4), 'trim', rot((-6, -s * 16, -s * 5)), label='pectoral_rim')
        # Glowing crimson rib rune
        f.beam(c, (s * 1.1, 27.5, -3.8), (s * 4.6, 29.2, -3.6), 0.25, 0.18, 'glow', label='rib_glow')

    # Central River Acheron Sanguine Soul Core
    # Gold torc chains
    f.beam(c, (-2.2, 31.0, -3.0), (0, 27.2, -3.5), 0.5, 0.4, 'gold', label='core_chain_l')
    f.beam(c, (2.2, 31.0, -3.0), (0, 27.2, -3.5), 0.5, 0.4, 'gold', label='core_chain_r')
    # Blood Rose Diamond Gem framed by obsidian steel bezel
    f.ring(c, (0, 26.8, -3.7), 1.5, 0.35, 0.35, 'trim', segments=12, normal=(0, 0, 1), label='core_ring')
    f.cube(c, (0, 26.8, -3.8), (2.0, 2.0, 0.65), 'runes', rot((0, 0, 45)), label='ruby_gem')
    f.cube(c, (0, 26.8, -3.95), (1.2, 1.2, 0.45), 'glow', rot((0, 0, 45)), label='ruby_gem_glow')

    # Heavy Backplate & Articulated Spine (y=14.0 to y=32.0)
    f.box(c, (-4.8, 14.0, 3.2), (9.6, 17.5, 1.1), 'iron', label='backplate')
    f.beam(c, (0, 31.5, 4.0), (0, 14.2, 3.8), 1.1, 0.85, 'plate', label='spine_beam')
    for y, h in ((14.8, 1.1), (17.2, 1.4), (19.6, 1.6), (22.0, 1.8), (24.5, 2.0), (26.8, 2.3), (29.1, 2.4), (31.2, 1.8)):
        f.spike(c, (0, y, 4.0), (0, y + 0.4, 4.0 + h), 0.85, 'blade', steps=2, label=f'spine_spur_{int(y)}')

# ---------------------------------------------------------------------------
# Pauldrons: Crimson Duelist Blade-Breakers
# ---------------------------------------------------------------------------
def pauldrons(f):
    # Right Shoulder: Heavy Spiked Duelist Blade-Breaker
    sb_r = 'shoulder_r'
    f.box(sb_r, (-9.5, 26.5, -3.0), (3.4, 4.0, 6.0), 'plate', label='shoulder_cap_r')
    f.cube(sb_r, (-10.2, 30.5, 0.0), (4.4, 5.2, 6.4), 'plate', rot((0, 0, 20)), label='pauldron_wing_r')
    f.cube(sb_r, (-10.6, 30.8, 0.0), (4.0, 5.4, 6.6), 'blade', rot((0, 0, 20)), label='pauldron_trim_r')
    # 3 upward razor crimson flukes on right pauldron
    f.spike(sb_r, (-10.8, 33.0, -1.8), (-13.5, 38.0, -2.2), 1.1, 'blade', steps=3, label='pauldron_fluke_front')
    f.spike(sb_r, (-11.2, 33.5, 0.0), (-14.5, 40.0, 0.0), 1.3, 'blade', steps=3, label='pauldron_fluke_mid')
    f.spike(sb_r, (-10.8, 33.0, 1.8), (-13.5, 38.0, 2.2), 1.1, 'blade', steps=3, label='pauldron_fluke_rear')

    # Left Shoulder: Streamlined Articulated Duelist Guard
    sb_l = 'shoulder_l'
    f.box(sb_l, (6.1, 26.5, -3.0), (3.4, 4.0, 6.0), 'plate', label='shoulder_cap_l')
    f.cube(sb_l, (8.6, 29.8, 0.0), (3.8, 4.2, 5.8), 'plate', rot((0, 0, -15)), label='pauldron_guard_l')
    f.cube(sb_l, (8.9, 30.0, 0.0), (3.6, 4.4, 6.0), 'blade', rot((0, 0, -15)), label='pauldron_trim_l')
    f.spike(sb_l, (9.0, 32.0, 0.0), (11.5, 36.5, 0.0), 1.0, 'blade', steps=2, label='pauldron_fin_l')

# ---------------------------------------------------------------------------
# Arms & Gauntlets
# ---------------------------------------------------------------------------
def arms_gauntlets(f):
    for side, s in SIDES:
        # Arm (Bicep)
        ab = f'arm_{side}'
        f.box(ab, (s * 7.7 - (1.6 if s > 0 else 1.8), 22.0, -1.8), (3.4, 6.0, 3.8), 'plate', label=f'bicep_{side}')
        f.cube(ab, (s * 7.7, 24.5, -2.0), (3.6, 0.7, 4.2), 'blade', label=f'bicep_trim_{side}')

        # Forearm
        fab = f'forearm_{side}'
        # Elbow Couter
        f.spike(fab, (s * 8.3, 22.0, 0.5), (s * 10.5, 22.5, 1.2), 1.1, 'blade', steps=2, label=f'elbow_couter_{side}')
        f.box(fab, (s * 8.3 - (1.5 if s > 0 else 1.7), 15.5, -3.8), (3.2, 6.5, 3.8), 'plate', label=f'forearm_{side}')
        # Blade-catcher ridge on outer forearm
        f.beam(fab, (s * 9.2, 16.0, -3.2), (s * 9.2, 21.0, -2.2), 0.6, 0.7, 'blade', label=f'blade_catcher_{side}')

        # Hand & Gauntlet (Reverse Assassin Grip)
        hb = f'hand_{side}'
        f.box(hb, (s * 8.2 - (1.3 if s > 0 else 1.5), 13.5, -4.0), (2.8, 2.8, 2.6), 'iron', label=f'hand_core_{side}')
        f.cube(hb, (s * 8.2, 14.2, -4.2), (3.0, 1.0, 0.6), 'blade', label=f'knuckle_plate_{side}')
        f.cube(hb, (s * 8.2, 14.5, -3.2), (2.4, 2.2, 1.4), 'plate', label=f'fingers_{side}')

# ---------------------------------------------------------------------------
# Dual Crimson Sorrow-Quills (weapon_r & weapon_l)
# ---------------------------------------------------------------------------
def sorrow_quill_blades(f):
    """Sculpts the twin razor Ruby Sorrow-Quills under weapon_r and weapon_l."""
    for side, s in SIDES:
        wb = f'weapon_{side}'
        p_grip = np.array([s * 8.2, 14.5, -3.0])

        # 1. Hilt & Grip
        # Central obsidian grip
        f.beam(wb, p_grip + np.array([0, 1.8, 0]), p_grip - np.array([0, 1.8, 0]), 0.8, 0.8, 'iron', label=f'hilt_grip_{side}')
        # Pommel with ruby gem
        f.cube(wb, p_grip + np.array([0, 2.2, 0]), (1.4, 1.2, 1.4), 'gold', rot((0, 0, 45)), label=f'pommel_{side}')
        f.cube(wb, p_grip + np.array([0, 2.2, 0]), (0.8, 0.8, 0.8), 'crystal', rot((0, 0, 45)), label=f'pommel_gem_{side}')

        # 2. Swept Crimson Quillons / Guard
        p_guard = p_grip - np.array([0, 1.6, 0])
        f.beam(wb, p_guard + np.array([-s * 2.2, 0.4, 0]), p_guard + np.array([s * 2.8, -0.2, 0]), 0.7, 0.9, 'blade', label=f'crossguard_{side}')
        # Outer swept parrying prong
        f.spike(wb, p_guard + np.array([s * 2.8, -0.2, 0]), p_guard + np.array([s * 3.8, 1.2, 0]), 0.6, 'blade', steps=2, label=f'quillon_spur_{side}')

        # 3. Primary Ruby Sorrow-Quill Blade (Pointing downwards / forward)
        p_forte = p_guard - np.array([0, 0.6, 0])
        p_mid   = p_guard - np.array([0, 6.5, 0])
        p_tip   = p_guard - np.array([0, 13.5, 0])

        # Multi-segment crystalline ruby blade
        f.beam(wb, p_forte, p_mid, 1.6, 0.6, 'blade', label=f'blade_upper_{side}')
        f.spike(wb, p_mid, p_tip, 1.4, 'blade', steps=4, depth_ratio=0.5, label=f'blade_stiletto_tip_{side}')

        # Flared side parrying teeth at the forte
        f.spike(wb, p_forte, p_forte + np.array([s * 1.5, -2.0, 0]), 0.6, 'blade', steps=2, label=f'blade_tooth_{side}')

        # Glowing Crimson Blood-Rose Fuller (#f54268)
        f.beam(wb, p_forte - np.array([0, 0.5, 0]), p_mid + np.array([0, 0.5, 0]), 0.45, 0.7, 'glow', label=f'blade_fuller_{side}')
        f.beam(wb, p_mid, p_tip + np.array([0, 2.5, 0]), 0.35, 0.65, 'runes', label=f'blade_runes_{side}')

# ---------------------------------------------------------------------------
# Waist, Tassets, Legs & Sabatons
# ---------------------------------------------------------------------------
def waist_and_legs(f):
    p = 'pelvis'
    # Pelvis Core - Solid pelvic armature connecting to thighs and torso
    f.box(p, (-4.0, 11.5, -2.7), (8.0, 5.2, 5.4), 'iron', label='pelvis_core')
    # Inner Waist Joint Column (Inserts up into lower chest faulds to prevent any disconnection)
    f.box(p, (-3.5, 14.5, -2.5), (7.0, 4.0, 5.0), 'plate', label='pelvis_upper_joint')

    # Crimson War Belt
    f.box(p, (-4.4, 14.2, -3.0), (8.8, 2.4, 6.0), 'plate', label='war_belt')
    f.cube(p, (0, 15.4, -3.2), (2.4, 2.0, 0.8), 'gold', label='belt_buckle')
    f.cube(p, (0, 15.4, -3.4), (1.4, 0.5, 0.3), 'glow', label='buckle_gem')

    # Hanging Crimson Duelist Sashes / Tassets (sash_r and sash_l)
    for side, s in SIDES:
        sb = f'sash_{side}'
        # Hanging layered crimson cloth tassets
        f.box(sb, (s * 4.2 - (0.8 if s < 0 else 0), 8.5, -2.6), (0.8, 7.0, 5.2), 'cloth', label=f'tasset_main_{side}')
        f.cube(sb, (s * 4.4, 11.5, 0.0), (0.7, 6.5, 5.5), 'blade', label=f'tasset_trim_{side}')
        # Spiked bottom tooth
        f.spike(sb, (s * 4.2, 8.5, 0.0), (s * 4.2, 6.2, 0.0), 0.9, 'blade', steps=2, label=f'tasset_spur_{side}')

    # Legs, Shin, Greaves & Sabatons
    for side, s in SIDES:
        lb = f'leg_{side}'
        # Thigh cuisse plate (leg bone) - Reaching all the way up to y=15.3 (no gap to pelvis!)
        f.box(lb, (s * 2.8 - 1.6, 7.8, -2.0), (3.2, 7.5, 4.0), 'plate', label=f'thigh_plate_{side}')
        f.cube(lb, (s * 2.8, 11.5, -2.2), (3.4, 0.7, 4.2), 'blade', label=f'thigh_rim_{side}')

        # Shin & Greave (shin bone)
        shb = f'shin_{side}'
        f.box(shb, (s * 2.8 - 1.6, 1.5, -2.0), (3.2, 6.5, 4.0), 'iron', label=f'shin_core_{side}')
        # Pointed Knee Cop (Poleyn)
        f.cube(shb, (s * 2.8, 7.8, -2.4), (3.4, 2.2, 1.8), 'plate', rot((-10, 0, 0)), label=f'knee_cop_{side}')
        f.spike(shb, (s * 2.8, 7.8, -2.5), (s * 2.8, 8.4, -4.5), 1.0, 'blade', steps=2, label=f'knee_spike_{side}')
        # Greave Front Ridge
        f.beam(shb, (s * 2.8, 2.0, -2.4), (s * 2.8, 7.0, -2.4), 0.8, 0.7, 'blade', label=f'greave_ridge_{side}')

        # Foot & Sabaton (foot bone)
        fb = f'foot_{side}'
        f.box(fb, (s * 2.8 - 1.7, 0.0, -4.0), (3.4, 1.8, 6.0), 'plate', label=f'sabaton_{side}')
        f.spike(fb, (s * 2.8, 0.2, -4.0), (s * 2.8, 0.3, -5.8), 1.2, 'blade', steps=2, depth_ratio=0.5, label=f'toe_blade_{side}')

# ---------------------------------------------------------------------------
# Master Sculpt Assembly
# ---------------------------------------------------------------------------
def sculpt():
    f = Forge('arkhaia_acheron', MATERIALS)
    skeleton(f)
    head_mask(f)
    torso(f)
    pauldrons(f)
    arms_gauntlets(f)
    sorrow_quill_blades(f)
    waist_and_legs(f)
    return f

# ---------------------------------------------------------------------------
# Animation Pipeline: 100% Lossless Retargeting of All 13 Authored Clips
# ---------------------------------------------------------------------------
def load_and_retarget_animations(bak_path, new_model_bones):
    with open(bak_path, 'r', encoding='utf-8') as f:
        bak_data = json.load(f)

    name_to_uuid = {name: binfo['uuid'] for name, binfo in new_model_bones.items()}

    retargeted_clips = []
    for anim in bak_data.get('animations', []):
        c = copy.deepcopy(anim)
        old_animators = c.get('animators', {})
        new_animators = {}
        for old_uuid, animator in old_animators.items():
            bname = animator.get('name')
            if bname in name_to_uuid:
                target_uuid = name_to_uuid[bname]
                new_animators[target_uuid] = animator
            else:
                print(f"Warning: bone '{bname}' in animation '{c.get('name')}' not in new model!")
        c['animators'] = new_animators
        retargeted_clips.append(c)

    return retargeted_clips

# ---------------------------------------------------------------------------
# Build & Export (Both Acheron and Acheron Echo!)
# ---------------------------------------------------------------------------
def build():
    f = sculpt()
    hitbox = [[-5, 0, -3], [5, 36, 3]]
    meta = dict(
        evergarden_revision=REVISION,
        evergarden_scale=SCALE
    )
    # 1. Build Primary Acheron Model (Crimson Sovereign)
    model, atlas, stats = build_model(
        f, [], 'arkhaia_acheron', 'acheron', hitbox,
        meta=meta, size=1024, density=8.0
    )

    bak_path = ROOT / 'evergarden/src/main/resources/modelengine/rivers/arkhaia_acheron.bbmodel.bak'
    bone_map = {}
    def scan_bones(nodes):
        for n in nodes:
            if isinstance(n, dict):
                bone_map[n['name']] = n
                scan_bones(n.get('children', []))
    scan_bones(model['outliner'])

    retargeted = load_and_retarget_animations(bak_path, bone_map)
    model['animations'] = retargeted

    # 2. Build Acheron Echo Model (0.6x scale clone in red)
    echo = copy.deepcopy(model)
    echo['name'] = 'arkhaia_acheron_echo'
    echo['model_identifier'] = 'arkhaia_acheron_echo'
    echo['meta']['evergarden_echo_scale'] = 0.6

    def walk_echo(group):
        group['origin'] = [round(v * 0.6, 5) for v in group['origin']]
        for child in group.get('children', []):
            if isinstance(child, dict):
                walk_echo(child)
    for group in echo['outliner']:
        walk_echo(group)

    for cube in echo['elements']:
        for field in ('from', 'to', 'origin'):
            if field in cube:
                cube[field] = [round(v * 0.6, 5) for v in cube[field]]

    for animation in echo['animations']:
        for animator in animation.get('animators', {}).values():
            for frame in animator.get('keyframes', []):
                if frame.get('channel') == 'position':
                    for point in frame.get('data_points', []):
                        for axis in 'xyz':
                            if axis in point:
                                point[axis] = str(round(float(point[axis]) * 0.6, 5))

    return model, echo, atlas, stats

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', type=Path, default=OUT)
    args = parser.parse_args()

    model, echo, atlas, stats = build()
    args.out.mkdir(parents=True, exist_ok=True)

    # Save Acheron
    target_acheron = args.out / 'arkhaia_acheron.bbmodel'
    target_acheron.write_text(dump(model) + '\n', encoding='utf8')
    if SERVA_OUT.exists():
        (SERVA_OUT / 'arkhaia_acheron.bbmodel').write_text(dump(model) + '\n', encoding='utf8')

    # Save Acheron Echo
    target_echo = args.out / 'arkhaia_acheron_echo.bbmodel'
    target_echo.write_text(dump(echo) + '\n', encoding='utf8')
    if SERVA_OUT.exists():
        (SERVA_OUT / 'arkhaia_acheron_echo.bbmodel').write_text(dump(echo) + '\n', encoding='utf8')

    # Save Art Texture
    art = ROOT / 'evergarden/art/arkhaia-rivers'
    art.mkdir(parents=True, exist_ok=True)
    atlas.save(art / 'acheron_crimson.png')

    print(f"Acheron Remastered (CRIMSON): {len(model['elements']) - 1} cubes, {len(sculpt().bones)} bones, "
          f"{len(model['animations'])} clips, atlas {stats['coverage']:.0%} coverage at {stats['density']:.1f}px/unit")
    print(f"Acheron Echo (CRIMSON): {len(echo['elements']) - 1} cubes, {len(echo['animations'])} clips")

if __name__ == '__main__':
    main()
