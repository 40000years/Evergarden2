"""Styx, the Dread Executioner: Remastered Blood Warlord ModelEngine R4 Blueprint (v2).

A colossal dread warlord sculpted, painted, and rigged using Evergarden's high-fidelity procedural pipeline:
- Persona: Dread Executioner & Warlord of River Styx, clad in heavy Netherite dreadplate armor,
  bloodsteel trim, dark obsidian iron, and glowing crimson soul-runes.
- Dread Great-Helm: Slanted intimidating blood-glow eye slits, heavy iron jaw guard, high crest,
  and dual massive sweeping demon ram horns.
- Colossal Flared Pauldrons: Multi-tiered jagged executioner shoulder guards with demon skull motifs.
- Dreadplate Cuirass: Layered muscular chest plates, central River Styx Sanguine Soul Core,
  segmented abdominal lames, and spinal vertebrae ridge.
- Tattered Sanguine Mantle: Shredded executioner cloak flowing with torn hem strips.
- SIGNATURE WEAPON: Colossal Terrifying Executioner's Great-Axe (ขวานยักษ์สุดน่ากลัว):
  * Massive faceted obsidian-iron haft passing through both hand grips.
  * Horned demon skull pommel with rear spike and dangling severed chain links.
  * Demonic beast skull socket collar with glowing crimson eyes.
  * Colossal curved bearded executioner crescent blade with razor bloodsteel bevels,
    serrated beard barb, and deep fuller channels pulsing with glowing sanguine runes.
  * Rear executioner cleaver pick / armor-cracker hook.
  * Crown spearhead thrusting blade with flared side parrying flukes.
- 100% preservation of all 28 authored combat animations from arkhaia_styx.bbmodel.bak:
  idle, walk, hover, stun,
  cast (windup/active/recover), attack (windup/active/recover),
  slash (windup/active/recover), reverse (windup/active/recover),
  thrust (windup/active/recover), dash (windup/active/recover),
  blood (windup/active/recover), berserk (windup/active/recover).
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
REVISION = 'styx-executioner-v2'

# ---------------------------------------------------------------------------
# Color Palettes & Materials
# ---------------------------------------------------------------------------
DREAD_NETHERITE = ['#08070b', '#100e16', '#1a1622', '#262030', '#382f45', '#4e4260', '#695a7f', '#8775a2']
BLOOD_STEEL = ['#150407', '#25070d', '#3d0c16', '#5d1222', '#851a31', '#b32342', '#d9395a', '#f5607e', '#ffa4b6']
OBSIDIAN_IRON = ['#060608', '#0c0d12', '#14161f', '#1f222e', '#2c3040', '#3d4256', '#535972', '#6e7592']
DARK_GOLD_RIVET = ['#1a1205', '#2c1e08', '#46300d', '#684814', '#93661d', '#c28828', '#e8ab45', '#ffd580']
DREAD_BONE = ['#14100c', '#231b14', '#362a1e', '#4f3d2c', '#6f563e', '#967657', '#bea07e', '#e2cca7', '#f4ebd8']
BLOOD_GLOW = ['#260408', '#520810', '#8c0f1c', '#cf192b', '#ff2e43', '#ff6b7b', '#ffa8b3', '#ffffff']
WARLORD_CAPE = ['#0a0305', '#160508', '#25080e', '#3a0c16', '#521120', '#70172c', '#92203b', '#b52f4c']
CHAIN_IRON = ['#0e0f12', '#16181d', '#20222a', '#2c2f3a', '#3d4150', '#54596b', '#71778d']
RUNES_BLOOD = ['#1f0307', '#48070f', '#7e0e1a', '#bd1728', '#f8283d', '#ff6b7d', '#ffffff']
EYE_RED = ['#ff2a3f', '#ff6878', '#ffaeb7', '#ffffff']

MATERIALS = {
    'plate': Material(DREAD_NETHERITE, 'plate', accent=BLOOD_GLOW, density=0.9),
    'bloodsteel': Material(BLOOD_STEEL, 'blade', density=1.0),
    'iron': Material(OBSIDIAN_IRON, 'plate', density=0.85),
    'chain': Material(CHAIN_IRON, 'plate', density=0.9),
    'gold': Material(DARK_GOLD_RIVET, 'trim', density=1.0),
    'horn': Material(DREAD_BONE, 'horn', density=1.0),
    'cloth': Material(WARLORD_CAPE, 'cloth', trim=BLOOD_STEEL, accent=BLOOD_GLOW, density=0.85),
    'glow': Material(BLOOD_GLOW, 'glow', glow=True, density=1.4),
    'eye': Material(EYE_RED, 'glow', glow=True, density=1.6),
    'runes': Material(RUNES_BLOOD, 'runes', glow=True, density=1.4),
    'blade': Material(BLOOD_STEEL, 'blade', density=1.1),
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
        'axe_driver': (-2.1, 19.7, -6),
        'weapon_r': (-6.5, 14.5, -6),
    }
    for i, x in enumerate([-3.625, -2.175, -0.725, 0.725, 2.175, 3.625]):
        j[f'veil_{i}'] = (x, 29, 3.3)
    for side, s in SIDES:
        j.update({
            f'shoulder_{side}': (s * 7.7, 28, 0),
            f'arm_{side}': (s * 7.7, 28, 0),
            f'forearm_{side}': (-9.5 if side == 'r' else 8.8, 22 if side == 'r' else 24, -3),
            f'hand_{side}': (-6.5 if side == 'r' else 2.3, 14.5 if side == 'r' else 24.9, -6),
            f'weapon_{side}': (s * 2.3 if side == 'l' else -6.5, 24.9 if side == 'l' else 14.5, -6),
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

    # Weapon driver and axe
    f.bone('axe_driver', 'chest', J['axe_driver'])
    f.bone('weapon_r', 'axe_driver', J['weapon_r'])

    # Limbs
    for side, s in SIDES:
        f.bone(f'shoulder_{side}', 'chest', J[f'shoulder_{side}'])
        f.bone(f'arm_{side}', 'chest', J[f'arm_{side}'])
        f.bone(f'forearm_{side}', f'arm_{side}', J[f'forearm_{side}'])
        f.bone(f'hand_{side}', f'forearm_{side}', J[f'hand_{side}'])
        if side == 'l':
            f.bone('weapon_l', 'hand_l', J['hand_l'])
        f.bone(f'leg_{side}', 'pelvis', J[f'leg_{side}'])
        f.bone(f'shin_{side}', f'leg_{side}', J[f'shin_{side}'])
        f.bone(f'foot_{side}', f'shin_{side}', J[f'foot_{side}'])
        f.bone(f'sash_{side}', 'pelvis', J[f'sash_{side}'])

# ---------------------------------------------------------------------------
# Helm & Skull: Dread Executioner Mask & Sweeping Demon Horns
# ---------------------------------------------------------------------------
def helm(f):
    b = 'head'
    # Dark Netherite Great-Helm Cranium
    f.box(b, (-3.2, 32.5, -3.0), (6.4, 6.2, 6.2), 'plate', label='cranium')

    # Angled Executioner Forehead & Brow Guard
    f.beam(b, (-3.3, 37.0, -3.2), (3.3, 37.0, -3.2), 1.1, 0.8, 'iron', label='brow_ridge')
    f.cube(b, (0, 37.2, -3.4), (2.0, 1.8, 0.5), 'bloodsteel', rot((0, 0, 45)), label='forehead_crest')
    f.cube(b, (0, 37.2, -3.5), (1.0, 1.0, 0.4), 'glow', rot((0, 0, 45)), label='forehead_rune')

    # Piercing Slanted Blood-Red Eye Slits
    for side, s in SIDES:
        # Sunken eye socket cavity
        f.box(b, (s * 1.3 - 0.7, 34.8, -3.25), (1.4, 0.65, 0.35), 'iron', label='eye_socket')
        # Piercing glowing eye slit angled menacingly
        f.cube(b, (s * 1.3, 35.0, -3.35), (1.3, 0.35, 0.2), 'eye', rot((0, 0, -s * 12)), label='eye_slit')
        # Heavy brow slant
        f.beam(b, (s * 0.3, 35.6, -3.3), (s * 2.6, 35.9, -3.1), 0.6, 0.4, 'plate', label='brow_slant')

    # Cheeks & Nose Guard
    f.beam(b, (0, 37.0, -3.3), (0, 33.2, -3.6), 0.85, 0.7, 'bloodsteel', label='nasal_ridge')
    for side, s in SIDES:
        f.cube(b, (s * 2.3, 33.8, -2.8), (1.8, 2.6, 1.2), 'plate', rot((0, -s * 18, 0)), label='cheek_guard')

    # Jaw Guard (jaw bone)
    jb = 'jaw'
    f.box(jb, (-2.5, 31.8, -3.2), (5.0, 2.0, 2.6), 'iron', label='jaw_base')
    f.beam(jb, (-2.2, 32.2, -3.4), (2.2, 32.2, -3.4), 0.7, 0.5, 'bloodsteel', label='chin_bar')
    # Executioner breathing grill slits
    for i in [-1.2, -0.4, 0.4, 1.2]:
        f.beam(jb, (i, 32.8, -3.35), (i, 31.8, -3.35), 0.25, 0.3, 'plate', label='grill_slit')

    # High Warlord Crest along the top ridge
    f.spike(b, (0, 38.5, -2.2), (0, 42.5, -0.5), 1.2, 'bloodsteel', steps=3, label='helm_crest_front')
    f.spike(b, (0, 38.5, -0.5), (0, 43.5, 1.5), 1.1, 'bloodsteel', steps=3, label='helm_crest_mid')
    f.spike(b, (0, 38.2, 1.5), (0, 41.5, 3.2), 0.9, 'bloodsteel', steps=3, label='helm_crest_rear')

    # Massive Swept Demon Ram Horns
    for side, s in SIDES:
        # Horn socket ring
        f.cube(b, (s * 3.0, 36.5, -0.5), (1.2, 2.2, 2.2), 'iron', rot((0, -s * 20, s * 10)), label='horn_socket')
        f.cube(b, (s * 3.1, 36.5, -0.5), (0.8, 1.8, 1.8), 'gold', rot((0, -s * 20, s * 10)), label='horn_socket_ring')

        # 4-stage sweeping demon horn curve
        horn_pts = [
            np.array([s * 3.2, 36.5, -0.5]),
            np.array([s * 6.8, 38.0, 0.8]),
            np.array([s * 9.5, 42.0, 1.8]),
            np.array([s * 8.0, 47.5, 0.5]),
            np.array([s * 6.5, 50.5, -1.2])
        ]
        widths = [2.2, 1.7, 1.3, 0.85]
        depths = [2.0, 1.6, 1.2, 0.75]
        f.taper(b, horn_pts, widths, depths, 'horn', z_hint=(0, 0, 1), label='demon_horn', overlap=0.2)

        # Barbed iron ridge along horn curve
        f.beam(b, horn_pts[1], horn_pts[2], widths[1] * 0.45, depths[1] * 0.45, 'bloodsteel', label='horn_barb_mid')
        f.spike(b, horn_pts[2], horn_pts[2] + np.array([s * 1.5, 1.2, 1.2]), 0.75, 'bloodsteel', steps=2, label='horn_spike_spur')

# ---------------------------------------------------------------------------
# Neck & Gorget
# ---------------------------------------------------------------------------
def neck_gorget(f):
    n = 'neck'
    f.box(n, (-2.0, 27.5, -2.0), (4.0, 3.2, 4.0), 'iron', label='neck_core')
    # Flared Executioner Gorget (Plate collar protecting throat)
    f.box(n, (-3.2, 28.0, -3.2), (6.4, 2.5, 1.2), 'plate', label='gorget_front')
    f.box(n, (-3.2, 28.0, 2.0), (6.4, 2.5, 1.2), 'plate', label='gorget_rear')
    for side, s in SIDES:
        f.box(n, (s * 3.2 - (1.2 if s > 0 else 0), 28.0, -2.5), (1.2, 2.5, 5.0), 'plate', label='gorget_side')
        # Gold collar studs
        f.cube(n, (s * 2.0, 29.5, -3.3), (0.45, 0.45, 0.3), 'gold', label='gorget_stud')

# ---------------------------------------------------------------------------
# Pauldrons: Colossal Flared Executioner Shoulder Guards
# ---------------------------------------------------------------------------
def pauldrons(f):
    for side, s in SIDES:
        sb = f'shoulder_{side}'
        # Base shoulder bell cap
        f.box(sb, (s * 6.2 - (3.5 if s < 0 else 0), 26.5, -3.2), (3.5, 4.0, 6.4), 'plate', label='shoulder_cap')

        # Massive High Flared Wing Guard (Sweeping up and out)
        f.cube(sb, (s * 8.8, 30.8, 0.0), (4.8, 5.6, 6.8), 'plate', rot((0, 0, -s * 22)), label='pauldron_wing')
        f.cube(sb, (s * 9.2, 31.2, 0.0), (4.4, 5.8, 7.0), 'bloodsteel', rot((0, 0, -s * 22)), label='pauldron_trim')

        # Razor Blade Spires on Pauldron Apex (3 tiered upward spires)
        f.spike(sb, (s * 9.5, 33.5, -2.0), (s * 12.5, 39.0, -2.5), 1.2, 'bloodsteel', steps=3, label='pauldron_spire_front')
        f.spike(sb, (s * 10.2, 34.0, 0.0), (s * 13.8, 41.5, 0.0), 1.4, 'bloodsteel', steps=4, label='pauldron_spire_mid')
        f.spike(sb, (s * 9.5, 33.5, 2.0), (s * 12.5, 39.0, 2.5), 1.2, 'bloodsteel', steps=3, label='pauldron_spire_rear')

        # Demonic Skull Emblem on Front Face of Pauldron
        f.cube(sb, (s * 8.6, 29.5, -3.6), (2.4, 2.6, 0.8), 'plate', rot((0, -s * 15, -s * 18)), label='pauldron_skull')
        f.cube(sb, (s * 8.6, 29.8, -3.9), (1.6, 0.6, 0.3), 'glow', rot((0, -s * 15, -s * 18)), label='pauldron_skull_eyes')

        # Lower Articulated Lames (protecting upper arm)
        f.cube(sb, (s * 7.8, 25.0, 0.0), (3.2, 2.2, 5.8), 'iron', rot((0, 0, -s * 12)), label='pauldron_lame_1')
        f.cube(sb, (s * 7.5, 23.2, 0.0), (2.8, 2.0, 5.2), 'iron', rot((0, 0, -s * 8)), label='pauldron_lame_2')

# ---------------------------------------------------------------------------
# Torso & Cuirass: Dreadplate & Sanguine Soul Core
# ---------------------------------------------------------------------------
def torso(f):
    c = 'chest'
    # Core Muscular Torso Chassis - Continuous from y=14.0 up to y=32.5 (No gaps!)
    tiers = [
        (14.0, 16.8, 7.8, 5.8, 0.1),   # Waist Fauld Base (overlaps pelvis war belt)
        (16.8, 19.8, 8.4, 6.0, 0.15),  # Lower Abdomen / Navel
        (19.8, 23.0, 9.4, 6.4, 0.2),   # Mid Abdomen / Obliques
        (23.0, 26.2, 10.6, 6.7, 0.3),  # Upper Abdomen / Solar Plexus
        (26.2, 29.5, 12.2, 7.1, 0.4),  # Lower Pectorals / Ribcage
        (29.5, 32.5, 13.2, 7.3, 0.5)   # Upper Pectorals / Clavicle
    ]
    for i, (y0, y1, w, d, zc) in enumerate(tiers):
        f.box(c, (-w / 2, y0, zc - d / 2), (w, y1 - y0, d), 'plate' if i >= 3 else 'iron',
              label='torso_tier', engrave='lines' if i >= 3 else None)

    # Articulated Dreadplate Abdominal Lames (Continuous segmented plates from y=14.5 to y=24.5)
    ab_lames = [
        (14.5, 7.2, 0.75),
        (16.2, 7.6, 0.8),
        (17.9, 8.2, 0.85),
        (19.8, 8.8, 0.9),
        (21.7, 9.4, 0.95),
        (23.6, 10.0, 1.0)
    ]
    for i, (y_pos, w, h) in enumerate(ab_lames):
        f.cube(c, (0, y_pos + h / 2, -2.8 - i * 0.1), (w, h, 0.85), 'plate', rot((-8, 0, 0)), label=f'ab_plate_{i}')
        f.cube(c, (0, y_pos + 0.15, -3.05 - i * 0.1), (w + 0.3, 0.25, 0.55), 'bloodsteel', rot((-8, 0, 0)), label=f'ab_trim_{i}')

    # Flank / Kidney Armor Plates (Covering sides between y=14.2 and y=22.5)
    for side, s in SIDES:
        f.cube(c, (s * 4.3, 17.5, 0.1), (1.1, 7.0, 5.8), 'plate', rot((0, 0, -s * 6)), label='flank_guard')
        f.cube(c, (s * 4.5, 17.5, 0.1), (0.9, 6.8, 6.0), 'bloodsteel', rot((0, 0, -s * 6)), label='flank_trim')

    # Layered Dread Pectoral Plates
    for side, s in SIDES:
        f.cube(c, (s * 3.6, 28.8, -3.5), (5.5, 4.6, 1.3), 'plate', rot((-6, -s * 16, -s * 5)), label='pectoral_plate')
        f.cube(c, (s * 3.6, 28.8, -3.8), (5.8, 0.7, 1.5), 'bloodsteel', rot((-6, -s * 16, -s * 5)), label='pectoral_rim')
        # Sanguine rib groove
        f.beam(c, (s * 1.2, 27.6, -4.1), (s * 5.2, 29.4, -3.9), 0.3, 0.2, 'glow', label='rib_glow')

    # Central River Styx Sanguine Soul Core
    # Dark iron torc chains
    f.beam(c, (-2.5, 31.2, -3.2), (0, 27.5, -3.8), 0.55, 0.45, 'chain', label='core_chain_l')
    f.beam(c, (2.5, 31.2, -3.2), (0, 27.5, -3.8), 0.55, 0.45, 'chain', label='core_chain_r')
    # Blood diamond gem framed by dark iron ring
    f.ring(c, (0, 27.0, -4.0), 1.6, 0.4, 0.4, 'iron', segments=12, normal=(0, 0, 1), label='core_ring')
    f.cube(c, (0, 27.0, -4.1), (2.2, 2.2, 0.7), 'runes', rot((0, 0, 45)), label='soul_gem')
    f.cube(c, (0, 27.0, -4.25), (1.4, 1.4, 0.5), 'glow', rot((0, 0, 45)), label='soul_gem_glow')

    # Heavy Backplate & Full Spinal Vertebrae Column (Continuous from y=14.0 to y=32.0)
    f.box(c, (-5.2, 14.0, 3.4), (10.4, 18.0, 1.2), 'iron', label='backplate')
    f.beam(c, (0, 31.8, 4.2), (0, 14.2, 4.0), 1.2, 0.9, 'bloodsteel', label='spine_beam')
    for y, h in ((14.8, 1.2), (17.2, 1.5), (19.6, 1.8), (22.0, 2.0), (24.5, 2.2), (26.8, 2.6), (29.1, 2.8), (31.2, 2.2)):
        f.spike(c, (0, y, 4.2), (0, y + 0.4, 4.2 + h), 0.95, 'bloodsteel', steps=3, label=f'spine_spur_{int(y)}')

# ---------------------------------------------------------------------------
# Mantle: Shredded Executioner Cloak (cape & cape_tip)
# ---------------------------------------------------------------------------
def mantle(f):
    cb = 'cape'
    # Shoulder Cowl / Mantle drape
    f.box(cb, (-6.0, 25.5, 3.8), (12.0, 3.2, 1.2), 'cloth', label='cape_cowl')
    # Main Upper Cloak - Continuous from y=14.0 to y=26.0
    f.box(cb, (-5.6, 14.0, 4.0), (11.2, 11.8, 0.6), 'cloth', label='cape_upper')
    for side, s in SIDES:
        f.cube(cb, (s * 5.2, 19.8, 4.2), (0.6, 11.5, 0.7), 'bloodsteel', label='cape_trim_upper')

    # Lower Tattered Cloak (cape_tip) - Continuous from y=8.0 to y=15.0
    tb = 'cape_tip'
    f.box(tb, (-5.4, 8.0, 4.2), (10.8, 7.2, 0.5), 'cloth', label='cape_lower')
    # 5 Hanging Shredded Tattered Strips
    strip_xs = [-4.0, -2.0, 0.0, 2.0, 4.0]
    lengths = [7.5, 9.0, 9.8, 8.8, 7.2]
    for i, (sx, lg) in enumerate(zip(strip_xs, lengths)):
        f.spike(tb, (sx, 8.2, 4.25), (sx + (0.3 if i % 2 == 0 else -0.3), 8.2 - lg, 4.5),
                1.4, 'cloth', steps=4, depth_ratio=0.35, label=f'cape_strip_{i}')

# ---------------------------------------------------------------------------
# Arms & Executioner Gauntlets
# ---------------------------------------------------------------------------
def arms_gauntlets(f):
    # Right arm & gauntlet
    # arm_r origin: [-7.7, 28, 0]
    ar = 'arm_r'
    f.box(ar, (-9.5, 22.0, -2.2), (3.6, 6.0, 4.4), 'plate', label='bicep_r')
    f.cube(ar, (-9.6, 24.5, -2.4), (3.8, 0.8, 4.8), 'bloodsteel', label='bicep_trim_r')

    # forearm_r origin: [-9.5, 22, -3]
    far = 'forearm_r'
    f.box(far, (-8.5, 15.0, -5.2), (3.4, 7.0, 4.4), 'plate', label='forearm_r')
    # Elbow Couter with vicious blade fin
    f.spike(far, (-9.5, 21.0, -2.0), (-12.5, 21.5, -1.0), 1.4, 'bloodsteel', steps=3, label='elbow_spur_r')
    # Blade-catcher vambrace ridge
    f.beam(far, (-8.8, 15.5, -5.5), (-8.8, 21.5, -4.5), 0.7, 0.8, 'bloodsteel', label='vambrace_ridge_r')

    # hand_r origin: [-6.5, 14.5, -6] (Firmly gripping axe haft!)
    hr = 'hand_r'
    f.box(hr, (-7.8, 13.0, -7.2), (3.0, 3.2, 2.8), 'iron', label='hand_r_core')
    # Spiked knuckle plate
    f.box(hr, (-8.0, 13.5, -7.5), (3.2, 1.2, 0.6), 'bloodsteel', label='knuckle_plate_r')
    for kx in [-7.5, -6.8, -6.1, -5.4]:
        f.spike(hr, (kx, 14.0, -7.5), (kx, 14.0, -8.3), 0.4, 'bloodsteel', steps=2, label='knuckle_spike_r')
    # Finger wraps curling around haft
    f.cube(hr, (-6.5, 14.5, -5.2), (2.4, 2.6, 1.4), 'plate', label='fingers_wrap_r')

    # Left arm & gauntlet
    # arm_l origin: [7.7, 28, 0]
    al = 'arm_l'
    f.box(al, (5.9, 22.0, -2.2), (3.6, 6.0, 4.4), 'plate', label='bicep_l')
    f.cube(al, (5.8, 24.5, -2.4), (3.8, 0.8, 4.8), 'bloodsteel', label='bicep_trim_l')

    # forearm_l origin: [8.8, 24, -3]
    fal = 'forearm_l'
    f.box(fal, (4.5, 23.0, -5.2), (4.4, 3.5, 4.4), 'plate', label='forearm_l')
    # Elbow Couter with vicious blade fin
    f.spike(fal, (9.2, 24.5, -2.0), (12.2, 25.0, -1.0), 1.4, 'bloodsteel', steps=3, label='elbow_spur_l')
    # Blade-catcher vambrace ridge
    f.beam(fal, (7.5, 23.5, -5.5), (3.5, 24.5, -5.5), 0.7, 0.8, 'bloodsteel', label='vambrace_ridge_l')

    # hand_l origin: [2.3, 24.9, -6] (Gripping upper shaft!)
    hl = 'hand_l'
    f.box(hl, (1.0, 23.4, -7.2), (3.0, 3.0, 2.8), 'iron', label='hand_l_core')
    # Spiked knuckle plate
    f.box(hl, (1.0, 25.4, -7.5), (3.0, 1.0, 0.6), 'bloodsteel', label='knuckle_plate_l')
    for kx in [1.4, 2.0, 2.6, 3.2]:
        f.spike(hl, (kx, 25.8, -7.5), (kx, 25.8, -8.3), 0.4, 'bloodsteel', steps=2, label='knuckle_spike_l')
    # Finger wraps
    f.cube(hl, (2.3, 24.9, -5.2), (2.4, 2.4, 1.4), 'plate', label='fingers_wrap_l')

# ---------------------------------------------------------------------------
# Waist, Tassets, Legs & Sabatons
# ---------------------------------------------------------------------------
def waist_and_legs(f):
    p = 'pelvis'
    # Heavy Dreadplate Pelvis Core - Solid pelvic armature connecting to thighs and torso
    f.box(p, (-4.3, 11.2, -2.9), (8.6, 5.6, 5.8), 'iron', label='pelvis_core')
    # Inner Waist Joint Column (Inserts up into lower chest faulds to prevent any disconnection during tilts)
    f.box(p, (-3.8, 14.5, -2.7), (7.6, 4.0, 5.4), 'plate', label='pelvis_upper_joint')

    # Heavy Dread War Belt
    f.box(p, (-4.8, 14.0, -3.3), (9.6, 2.8, 6.6), 'plate', label='war_belt')
    f.cube(p, (0, 15.4, -3.5), (2.8, 2.4, 0.9), 'bloodsteel', label='belt_skull_buckle')
    f.cube(p, (0, 15.4, -3.7), (1.8, 0.6, 0.3), 'glow', label='buckle_rune')
    for bx in [-3.5, -2.0, 2.0, 3.5]:
        f.cube(p, (bx, 15.4, -3.4), (0.5, 0.5, 0.3), 'gold', label='belt_rivet')

    # Front Armored Groin Codpiece / Center Fauld Plate
    f.box(p, (-1.8, 9.5, -3.2), (3.6, 5.5, 1.2), 'plate', label='groin_fauld')
    f.spike(p, (0, 9.5, -3.2), (0, 7.8, -3.2), 1.4, 'bloodsteel', steps=2, label='groin_spur')

    # Side Tassets (sash_r and sash_l)
    for side, s in SIDES:
        sb = f'sash_{side}'
        # Hanging layered thigh tasset plates
        f.box(sb, (s * 4.6 - (1.0 if s < 0 else 0), 8.5, -2.8), (1.0, 7.2, 5.6), 'plate', label='tasset_main')
        f.cube(sb, (s * 4.8, 11.5, 0.0), (0.9, 6.5, 6.0), 'bloodsteel', label='tasset_trim')
        # Tasset spiked bottom tooth
        f.spike(sb, (s * 4.6, 8.5, 0.0), (s * 4.6, 6.0, 0.0), 1.1, 'bloodsteel', steps=2, label='tasset_spur')

    # Legs, Shin, Greaves & Sabatons
    for side, s in SIDES:
        lb = f'leg_{side}'
        # Thigh cuisse plate (leg bone) - Reaching all the way up to y=15.3 (no gap to pelvis!)
        f.box(lb, (s * 2.8 - 1.8, 7.8, -2.2), (3.6, 7.5, 4.4), 'plate', label='thigh_plate')
        f.cube(lb, (s * 2.8, 11.5, -2.4), (3.8, 0.8, 4.6), 'bloodsteel', label='thigh_rim')
        # Inner thigh joint collar
        f.box(lb, (s * 2.8 - 1.6, 13.5, -2.0), (3.2, 2.0, 4.0), 'iron', label='thigh_hip_joint')

        # Shin & Greave (shin bone)
        shb = f'shin_{side}'
        f.box(shb, (s * 2.8 - 1.8, 1.5, -2.2), (3.6, 6.5, 4.4), 'iron', label='shin_core')
        # Massive Poleyn (Knee Cop) with vicious forward spike
        f.cube(shb, (s * 2.8, 7.8, -2.6), (3.8, 2.4, 2.0), 'plate', rot((-10, 0, 0)), label='knee_cop')
        f.spike(shb, (s * 2.8, 7.8, -2.8), (s * 2.8, 8.5, -5.2), 1.2, 'bloodsteel', steps=3, label='knee_spike')
        # Front Greave Ridge
        f.beam(shb, (s * 2.8, 2.0, -2.6), (s * 2.8, 7.0, -2.6), 0.9, 0.8, 'bloodsteel', label='greave_ridge')
        # Calf flare
        f.cube(shb, (s * 2.8, 4.0, 2.4), (3.8, 4.2, 1.2), 'plate', label='calf_guard')

        # Foot & Sabaton (foot bone)
        fb = f'foot_{side}'
        # Armored war sabaton
        f.box(fb, (s * 2.8 - 1.9, 0.0, -4.5), (3.8, 2.0, 6.8), 'plate', label='sabaton')
        # Pointed steel toe cap
        f.spike(fb, (s * 2.8, 0.2, -4.5), (s * 2.8, 0.4, -6.5), 1.4, 'bloodsteel', steps=2, depth_ratio=0.6, label='toe_blade')
        # Rear heel spur
        f.spike(fb, (s * 2.8, 0.8, 2.2), (s * 2.8, 1.2, 4.5), 0.8, 'iron', steps=2, label='heel_spur')

# ---------------------------------------------------------------------------
# SIGNATURE WEAPON: The Colossal Terrifying Great-Axe (weapon_r)
# ---------------------------------------------------------------------------
def executioner_great_axe(f):
    """Sculpts the terrifying Colossal Executioner's Great-Axe under weapon_r."""
    w = 'weapon_r'

    # The haft runs along the line from pommel through hand_r and hand_l to socket:
    # Pommel: [-11.5, 8.5, -6]
    # Hand_r: [-6.5, 14.5, -6]
    # Hand_l: [2.3, 24.9, -6]
    # Socket: [10.5, 34.5, -6]
    # Spear tip: [15.0, 39.8, -6]
    p_pommel = np.array([-11.5, 8.5, -6.0])
    p_hand_r = np.array([-6.5, 14.5, -6.0])
    p_hand_l = np.array([2.3, 24.9, -6.0])
    p_socket = np.array([10.5, 34.5, -6.0])
    p_tip    = np.array([15.0, 39.8, -6.0])

    # 1. Main Obsidian-Iron Haft Beams
    f.beam(w, p_pommel, p_hand_r, 1.4, 1.4, 'iron', z_hint=(0, 0, 1), label='haft_lower')
    f.beam(w, p_hand_r, p_hand_l, 1.4, 1.4, 'iron', z_hint=(0, 0, 1), label='haft_mid')
    f.beam(w, p_hand_l, p_socket, 1.5, 1.5, 'iron', z_hint=(0, 0, 1), label='haft_upper')
    f.beam(w, p_socket, p_tip, 1.3, 1.3, 'bloodsteel', z_hint=(0, 0, 1), label='haft_socket_core')

    # 2. Hand Grip Reinforced Wraps & Rings
    # Grip R (at right hand)
    f.beam(w, p_hand_r - np.array([1.2, 1.4, 0]), p_hand_r + np.array([1.2, 1.4, 0]), 1.7, 1.7, 'bloodsteel', z_hint=(0, 0, 1), label='grip_r_wrap')
    f.ring(w, p_hand_r - np.array([1.4, 1.6, 0]), 1.05, 0.4, 0.4, 'gold', normal=(0.64, 0.77, 0), label='grip_r_ring_bot')
    f.ring(w, p_hand_r + np.array([1.4, 1.6, 0]), 1.05, 0.4, 0.4, 'gold', normal=(0.64, 0.77, 0), label='grip_r_ring_top')

    # Grip L (at left hand)
    f.beam(w, p_hand_l - np.array([1.2, 1.4, 0]), p_hand_l + np.array([1.2, 1.4, 0]), 1.7, 1.7, 'bloodsteel', z_hint=(0, 0, 1), label='grip_l_wrap')
    f.ring(w, p_hand_l - np.array([1.4, 1.6, 0]), 1.05, 0.4, 0.4, 'gold', normal=(0.64, 0.77, 0), label='grip_l_ring_bot')
    f.ring(w, p_hand_l + np.array([1.4, 1.6, 0]), 1.05, 0.4, 0.4, 'gold', normal=(0.64, 0.77, 0), label='grip_l_ring_top')

    # Barbed Chain Wrappings along the haft between grips
    f.ring(w, (p_hand_r + p_hand_l) / 2 - np.array([1.5, 1.8, 0]), 0.95, 0.35, 0.35, 'chain', normal=(0.64, 0.77, 0), label='haft_chain_1')
    f.ring(w, (p_hand_r + p_hand_l) / 2, 0.95, 0.35, 0.35, 'chain', normal=(0.64, 0.77, 0), label='haft_chain_2')
    f.ring(w, (p_hand_r + p_hand_l) / 2 + np.array([1.5, 1.8, 0]), 0.95, 0.35, 0.35, 'chain', normal=(0.64, 0.77, 0), label='haft_chain_3')

    # 3. Demonic Skull Pommel & Severed Chains (Bottom End)
    # Faceted skull pommel block
    f.cube(w, p_pommel, (2.6, 2.6, 2.4), 'plate', rot((0, 0, -40)), label='pommel_skull')
    f.cube(w, p_pommel + np.array([-0.3, -0.3, 0]), (1.8, 1.8, 2.6), 'bloodsteel', rot((0, 0, -40)), label='pommel_rim')
    # Rear armor-breaker spike jutting backward-downward from pommel
    f.spike(w, p_pommel, p_pommel + np.array([-3.2, -3.8, 0]), 1.2, 'bloodsteel', steps=3, label='pommel_spike')
    # 3 Dangling Severed Chain Links hanging under gravity from pommel ring
    chain_link_1 = p_pommel + np.array([0, -2.0, 0])
    chain_link_2 = p_pommel + np.array([0, -3.8, 0])
    chain_link_3 = p_pommel + np.array([0, -5.6, 0])
    f.ring(w, chain_link_1, 0.9, 0.3, 0.3, 'chain', normal=(1, 0, 0), label='pommel_chain_link_1')
    f.ring(w, chain_link_2, 0.9, 0.3, 0.3, 'chain', normal=(0, 0, 1), label='pommel_chain_link_2')
    f.ring(w, chain_link_3, 0.9, 0.3, 0.3, 'chain', normal=(1, 0, 0), label='pommel_chain_link_3')
    # Hanging severed spike hook on the end of the chain
    f.spike(w, chain_link_3, chain_link_3 + np.array([0, -1.8, 0]), 0.6, 'bloodsteel', steps=2, label='chain_terminal_hook')

    # 4. Demonic Beast Skull Socket / Axe Collar (Where haft meets blade)
    f.cube(w, p_socket, (4.4, 4.8, 3.2), 'plate', rot((0, 0, -40)), label='beast_socket_main')
    f.cube(w, p_socket, (4.8, 5.2, 2.6), 'bloodsteel', rot((0, 0, -40)), label='beast_socket_trim')
    # Glowing Beast Eyes on socket (+Z and -Z faces)
    f.cube(w, p_socket + np.array([0, 0, 1.65]), (1.8, 0.6, 0.25), 'eye', rot((0, 0, -40)), label='socket_eye_front')
    f.cube(w, p_socket + np.array([0, 0, -1.65]), (1.8, 0.6, 0.25), 'eye', rot((0, 0, -40)), label='socket_eye_back')
    # Gold mounting rivets on collar
    f.ring(w, p_socket - np.array([1.5, 1.8, 0]), 1.8, 0.4, 0.4, 'gold', normal=(0.64, 0.77, 0), label='socket_collar_ring_bot')
    f.ring(w, p_socket + np.array([1.5, 1.8, 0]), 1.8, 0.4, 0.4, 'gold', normal=(0.64, 0.77, 0), label='socket_collar_ring_top')

    # 5. Colossal Bearded Executioner Blade (Massive menacing curved cleaver)
    # The blade extends forward (+X, +Y, -Y) from socket:
    # Backbone / Central Blade Spine (Heavy dark plate)
    spine_pts = [
        p_socket + np.array([1.5, -0.5, 0]),
        np.array([13.5, 33.0, -6.0]),
        np.array([15.5, 32.5, -6.0]),
        np.array([17.5, 33.0, -6.0])
    ]
    f.beam(w, spine_pts[0], spine_pts[1], 3.2, 2.2, 'plate', label='blade_spine_1')
    f.beam(w, spine_pts[1], spine_pts[2], 2.8, 1.8, 'plate', label='blade_spine_2')
    f.beam(w, spine_pts[2], spine_pts[3], 2.4, 1.6, 'plate', label='blade_spine_3')

    # Executioner Lower Beard (Dropping down into a cruel barbed hook)
    beard_pts = [
        np.array([11.8, 31.0, -6.0]),
        np.array([11.2, 27.5, -6.0]),
        np.array([9.8, 24.5, -6.0]),
        np.array([8.2, 23.5, -6.0])  # Vicious inward barb hook!
    ]
    f.beam(w, beard_pts[0], beard_pts[1], 2.6, 1.6, 'bloodsteel', label='blade_beard_root')
    f.beam(w, beard_pts[1], beard_pts[2], 2.2, 1.4, 'bloodsteel', label='blade_beard_drop')
    f.spike(w, beard_pts[2], beard_pts[3], 1.6, 'bloodsteel', steps=3, label='blade_beard_hook_barb')
    # Serrated teeth on inner curve of beard
    f.spike(w, np.array([10.5, 28.5, -6.0]), np.array([8.8, 29.0, -6.0]), 0.8, 'bloodsteel', steps=2, label='beard_serration_1')
    f.spike(w, np.array([9.8, 26.0, -6.0]), np.array([8.2, 26.2, -6.0]), 0.75, 'bloodsteel', steps=2, label='beard_serration_2')

    # Outer Cleaver Belly & Razor Cutting Arc (The main cutting edge)
    # Points defining the sweeping executioner crescent curve from lower barb to upper horn
    crescent_pts = [
        np.array([8.2, 23.5, -6.0]),   # Lower barb tip
        np.array([12.0, 25.5, -6.0]),  # Lower cutting swell
        np.array([16.5, 28.5, -6.0]),  # Lower-mid cleaver
        np.array([19.8, 33.0, -6.0]),  # Peak forward belly
        np.array([19.0, 38.0, -6.0]),  # Upper cleaver
        np.array([16.5, 42.5, -6.0]),  # Upper horn swell
        np.array([14.2, 45.0, -6.0])   # Upper razor horn tip!
    ]
    # Sculpt the outer razor cutting edge bevels along the crescent
    for i in range(len(crescent_pts) - 1):
        p_a, p_b = crescent_pts[i], crescent_pts[i + 1]
        f.beam(w, p_a, p_b, 1.8, 0.7, 'blade', label=f'razor_edge_segment_{i}')
        # Secondary mid-bevel thickening the blade inward
        mid_inward = (p_a + p_b) / 2 + np.array([-2.2, 0, 0])
        f.beam(w, (p_a + p_b) / 2, mid_inward, 1.6, 1.2, 'bloodsteel', label=f'blade_bevel_inner_{i}')

    # Upper Horn Finial & Reinforcement Truss
    f.spike(w, crescent_pts[5], crescent_pts[6], 1.5, 'bloodsteel', steps=3, label='upper_horn_finial')
    f.beam(w, crescent_pts[4], np.array([13.5, 37.0, -6.0]), 2.2, 1.4, 'plate', label='upper_blade_truss')
    f.beam(w, crescent_pts[5], np.array([12.5, 38.5, -6.0]), 2.0, 1.4, 'plate', label='upper_blade_truss_2')

    # Central Web / Blade Core (Solid heavy plate filling the axe head)
    f.cube(w, (14.5, 33.0, -6.0), (6.5, 7.5, 1.4), 'plate', label='axe_blade_web_main')
    f.cube(w, (13.5, 29.0, -6.0), (5.0, 6.0, 1.4), 'plate', label='axe_blade_web_lower')
    f.cube(w, (13.8, 37.5, -6.0), (5.0, 5.5, 1.4), 'plate', label='axe_blade_web_upper')

    # Glowing Sanguine Fuller Blood Channels (Carved deeply in blade faces)
    # Running along the curved fuller from socket out towards belly
    fuller_pts_1 = [
        p_socket + np.array([2.5, 0, 0]),
        np.array([14.5, 34.0, -6.0]),
        np.array([17.5, 33.5, -6.0])
    ]
    f.beam(w, fuller_pts_1[0], fuller_pts_1[1], 0.8, 1.6, 'glow', label='blood_fuller_1')
    f.beam(w, fuller_pts_1[1], fuller_pts_1[2], 0.7, 1.6, 'runes', label='blood_fuller_2')

    fuller_pts_lower = [
        np.array([12.5, 31.0, -6.0]),
        np.array([14.5, 28.5, -6.0])
    ]
    f.beam(w, fuller_pts_lower[0], fuller_pts_lower[1], 0.7, 1.55, 'glow', label='blood_fuller_low')

    fuller_pts_upper = [
        np.array([13.0, 36.0, -6.0]),
        np.array([15.5, 39.5, -6.0])
    ]
    f.beam(w, fuller_pts_upper[0], fuller_pts_upper[1], 0.7, 1.55, 'glow', label='blood_fuller_high')

    # 6. Menacing Rear Executioner Cleaver Pick / Skull-Cracker Hook
    # Protruding rearward from the socket (towards -X, +Y):
    pick_pts = [
        p_socket + np.array([-1.5, 1.0, 0]),
        np.array([6.5, 37.5, -6.0]),
        np.array([4.2, 39.5, -6.0]),
        np.array([3.0, 38.0, -6.0])  # Vicious armor-piercing claw!
    ]
    f.beam(w, pick_pts[0], pick_pts[1], 2.2, 1.8, 'bloodsteel', label='rear_pick_root')
    f.beam(w, pick_pts[1], pick_pts[2], 1.8, 1.4, 'bloodsteel', label='rear_pick_shaft')
    f.spike(w, pick_pts[2], pick_pts[3], 1.4, 'bloodsteel', steps=2, label='rear_pick_barb')
    # Pick reinforcement gusset
    f.beam(w, pick_pts[1], p_socket + np.array([-0.5, -1.5, 0]), 1.5, 1.4, 'plate', label='rear_pick_gusset')

    # 7. Crown Spearhead Thrusting Finial
    # Sits at the apex of the haft above the socket
    spear_base = p_tip
    spear_apex = p_tip + np.array([3.8, 4.5, 0])
    f.spike(w, spear_base, spear_apex, 1.8, 'bloodsteel', steps=3, label='crown_spearhead')
    # Twin side parrying flukes / wings
    f.spike(w, spear_base + np.array([0.8, 1.0, 0]), spear_base + np.array([0.2, 3.2, 0]), 0.8, 'bloodsteel', steps=2, label='crown_fluke_l')
    f.spike(w, spear_base + np.array([1.4, 0.4, 0]), spear_base + np.array([3.4, 0.8, 0]), 0.8, 'bloodsteel', steps=2, label='crown_fluke_r')

# ---------------------------------------------------------------------------
# Master Sculpt Assembly
# ---------------------------------------------------------------------------
def sculpt():
    f = Forge('arkhaia_styx', MATERIALS)
    skeleton(f)
    helm(f)
    neck_gorget(f)
    pauldrons(f)
    torso(f)
    mantle(f)
    arms_gauntlets(f)
    waist_and_legs(f)
    executioner_great_axe(f)
    return f

# ---------------------------------------------------------------------------
# Animation Pipeline: 100% Lossless Retargeting of All 28 Authored Clips
# ---------------------------------------------------------------------------
def load_and_retarget_animations(bak_path, new_model_bones):
    """Retargets all 28 animation clips from backup model to new bone UUIDs by bone name."""
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
# Build & Export
# ---------------------------------------------------------------------------
def build():
    f = sculpt()
    # Hitbox matches the original model: [-5, 0, -3] to [5, 36, 3]
    hitbox = [[-5, 0, -3], [5, 36, 3]]
    meta = dict(
        evergarden_revision=REVISION,
        evergarden_scale=SCALE
    )
    # Assemble model geometry, procedural texture atlas, and bone structure
    model, atlas, stats = build_model(
        f, [], 'arkhaia_styx', 'styx', hitbox,
        meta=meta, size=1024, density=8.0
    )

    # Retarget the 28 authored animation clips from the backup model
    bak_path = ROOT / 'evergarden/src/main/resources/modelengine/rivers/arkhaia_styx.bbmodel.bak'
    bone_map = {}
    def scan_bones(nodes):
        for n in nodes:
            if isinstance(n, dict):
                bone_map[n['name']] = n
                scan_bones(n.get('children', []))
    scan_bones(model['outliner'])

    retargeted = load_and_retarget_animations(bak_path, bone_map)
    model['animations'] = retargeted

    return model, atlas, stats

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--out', type=Path, default=OUT)
    args = parser.parse_args()

    model, atlas, stats = build()
    args.out.mkdir(parents=True, exist_ok=True)
    target = args.out / 'arkhaia_styx.bbmodel'
    target.write_text(dump(model) + '\n', encoding='utf8')

    # Save to server blueprint directory as well if available
    if SERVA_OUT.exists():
        serva_target = SERVA_OUT / 'arkhaia_styx.bbmodel'
        serva_target.write_text(dump(model) + '\n', encoding='utf8')
        print(f"Also wrote to Serva blueprint: {serva_target}")

    art = ROOT / 'evergarden/art/arkhaia-rivers'
    art.mkdir(parents=True, exist_ok=True)
    atlas.save(art / 'styx_titan.png')

    print(f"Styx Remastered v2: {len(model['elements']) - 1} cubes, {len(sculpt().bones)} bones, "
          f"{len(model['animations'])} clips, atlas {stats['coverage']:.0%} coverage at {stats['density']:.1f}px/unit")

if __name__ == '__main__':
    main()
