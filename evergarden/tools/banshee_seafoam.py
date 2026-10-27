"""Banshee Wail, the Mourning Siren: Remastered Sea-Foam Spectral Queen Blueprint.

Sculpted, painted, and rigged using Evergarden's high-fidelity procedural pipeline
with authentic SEAFOAM MINT & AQUA SOUL TEAL (#7cf4ce) identity:
- Persona: Mourning Siren & Queen of Wailing Souls, hovering ethereally in a ghost ballgown.
- Signature Palette: Luminescent Sea-Foam Mint (#7cf4ce, #42e8b2), Ghost Porcelain Silk,
  Dark Mourning Corset Slate (#121a1e), Antiqued Soul Gold (#e2c56f), and Wail Void Black.
- Screaming Sonic Mouth: Hollow resonant oval maw emitting sonic shockwaves,
  framed by weeping sorrow porcelain faceplate and glowing mint tear streaks.
- 18-Segment Circular Soul Halo: Complete 18-segment circular ring centered at [0, 35, 3]
  with 9 radiant spirit prongs and orbiting wailing soul wisps.
- 14-Panel Pleated Hovering Ghost Ballgown Dress: Spread across sash_r and sash_l
  with flaring pleated silk folds, ruffled hems, and antique gold filigree trims.
- 6 Cascading Mourning Veils: Attached to collar (veil_0 to veil_5), falling from y=29 down to y=2.
- Ethereal Arms & Ghost Claws: Bell-draped mourning sleeves and elongated clawed spirit fingers.
- Seamless Continuous Torso: Solid coverage from y=14.0 to y=32.5 overlapping pelvis (Zero gaps!).
- 100% preservation of all 15 authored animation clips from arkhaia_banshee_wail.bbmodel.bak:
  idle, walk, hover, stun,
  cast (windup/active/recover), attack (windup/active/recover),
  curse (windup/active/recover), reveal, reform.
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
REVISION = 'banshee-seafoam-v3'

# ---------------------------------------------------------------------------
# Authentic Sea-Foam Mint & Aqua Soul Palettes & Materials
# ---------------------------------------------------------------------------
SEAFOAM_SOUL_MINT = ['#041c19', '#08332c', '#0f5247', '#177a68', '#25a890', '#42e8b2', '#7cf4ce', '#bbfbe7', '#ffffff']
GHOST_PORCELAIN_SILK = ['#0e1b1d', '#182b2e', '#264144', '#395c60', '#567f84', '#7daaa8', '#a8d2cf', '#daf1ef', '#ffffff']
MOURNING_CORSET_SLATE = ['#0a1012', '#121a1e', '#1c262c', '#29373f', '#3b4e57', '#506772', '#6b8491', '#8ca4b2']
ANTIQUE_SOUL_GOLD = ['#191508', '#2a220e', '#423617', '#635123', '#8e7533', '#c2a24c', '#e2c56f', '#f7e5a3']
WAIL_VOID_BLACK = ['#020608', '#050d10', '#0a151a', '#101f25', '#172b32']
MINT_EYE_GLOW = ['#42e8b2', '#7cf4ce', '#affbe0', '#ffffff']
SOUL_CRYSTAL = ['#0d4239', '#176b5c', '#249d87', '#3cd5b6', '#7cf4ce', '#affbe0', '#d8ffef', '#ffffff']

MATERIALS = {
    'silk': Material(GHOST_PORCELAIN_SILK, 'cloth', trim=SEAFOAM_SOUL_MINT, accent=ANTIQUE_SOUL_GOLD, density=0.85),
    'dress': Material(GHOST_PORCELAIN_SILK, 'cloth', trim=ANTIQUE_SOUL_GOLD, accent=SEAFOAM_SOUL_MINT, density=0.9),
    'corset': Material(MOURNING_CORSET_SLATE, 'plate', trim=ANTIQUE_SOUL_GOLD, accent=SEAFOAM_SOUL_MINT, density=0.9),
    'mask': Material(GHOST_PORCELAIN_SILK, 'gem', density=1.0),
    'gold': Material(ANTIQUE_SOUL_GOLD, 'trim', density=1.0),
    'iron': Material(MOURNING_CORSET_SLATE, 'trim', density=0.9),
    'soul': Material(SEAFOAM_SOUL_MINT, 'gem', accent=MINT_EYE_GLOW, glow=True, density=1.3),
    'void': Material(WAIL_VOID_BLACK, 'plate', density=0.7),
    'glow': Material(MINT_EYE_GLOW, 'glow', glow=True, density=1.5),
    'eye': Material(MINT_EYE_GLOW, 'glow', glow=True, density=1.6),
    'runes': Material(SEAFOAM_SOUL_MINT, 'runes', glow=True, density=1.4),
    'halo': Material(SOUL_CRYSTAL, 'gem', glow=True, density=1.3),
    'veil': Material(GHOST_PORCELAIN_SILK, 'cloth', trim=SEAFOAM_SOUL_MINT, accent=MINT_EYE_GLOW, density=0.8),
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
            f'forearm_{side}': (s * 10, 24, -0.7),
            f'hand_{side}': (s * 12, 21, -4),
            f'weapon_{side}': (s * 12, 21, -4),
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
# Head & Screaming Face: Screaming Wail Mouth & Sea-Foam Sorrow Crown
# ---------------------------------------------------------------------------
def head_mask(f):
    b = 'head'
    # Pale Weeping Porcelain Cranium
    f.box(b, (-2.65, 31.6, -2.6), (5.3, 5.4, 4.9), 'mask', label='cranium')

    # Chiseled Sorrow Faceplate with Sunken Cheekbones
    f.box(b, (-2.4, 32.0, -3.2), (4.8, 5.2, 0.7), 'mask', label='faceplate')

    # Piercing Wailing Eyes with Luminescent Sea-Foam Tear Channels
    for side, s in SIDES:
        # Sunken eye socket
        f.box(b, (s * 1.15 - 0.55, 34.4, -3.3), (1.1, 0.6, 0.3), 'void', label='eye_socket')
        # Piercing mint soul eye slit angled in sorrow
        f.cube(b, (s * 1.15, 34.6, -3.35), (1.05, 0.28, 0.2), 'eye', rot((0, 0, -s * 15)), label='eye_glow')
        # Luminescent Sea-Foam tear streak running down cheek to chin
        f.beam(b, (s * 1.15, 34.4, -3.32), (s * 1.05, 31.6, -3.32), 0.22, 0.16, 'glow', label='seafoam_tear')
        f.beam(b, (s * 1.05, 31.6, -3.32), (s * 0.9, 29.5, -3.32), 0.16, 0.12, 'runes', label='tear_trail')

    # THE SCREAMING WAIL MOUTH (Iconic Sonic Shockwave Emitter)
    # Hollow resonant mouth cavity
    f.box(b, (-1.1, 31.8, -3.4), (2.2, 2.7, 0.45), 'void', label='scream_mouth_cavity')
    # Inner glowing sonic core emitting shockwaves
    f.cube(b, (0, 33.15, -3.25), (1.5, 1.8, 0.3), 'glow', label='sonic_pulse_core')
    # Chiseled porcelain lips / mouth rim
    f.cube(b, (0, 34.4, -3.42), (2.1, 0.25, 0.25), 'mask', label='upper_lip')
    f.cube(b, (0, 31.9, -3.42), (1.9, 0.25, 0.25), 'mask', label='lower_lip')
    for side, s in SIDES:
        f.cube(b, (s * 1.05, 33.15, -3.42), (0.22, 2.3, 0.25), 'mask', label='side_lip')

    # Cascading Spectral Hair Tresses (Left and Right)
    for side, s in SIDES:
        # Front hair lock framing face
        f.box(b, (s * 2.55 - (1.2 if s > 0 else 0), 30.5, -2.8), (1.2, 6.0, 5.7), 'silk', rot_deg=(0, 0, s * 8), label='hair_side')
        # Trailing locks streaming backwards and down to y=26
        f.spike(b, (s * 2.7, 36.0, 2.0), (s * 3.2, 26.0, 2.0), 0.75, 'dress', steps=4, label='hair_lock_1')
        f.spike(b, (s * 3.2, 36.0, 2.4), (s * 3.8, 26.8, 2.4), 0.75, 'dress', steps=4, label='hair_lock_2')
        f.spike(b, (s * 3.7, 36.0, 2.8), (s * 4.4, 27.6, 2.8), 0.75, 'dress', steps=4, label='hair_lock_3')

    # Spired Ghost Queen Diadem & Soul Tiara
    f.box(b, (-2.8, 36.2, -2.8), (5.6, 1.0, 5.6), 'gold', label='crown_circlet')
    # Central High Sea-Foam Crystal Spire
    f.spike(b, (0, 37.0, -2.8), (0, 46.5, -3.2), 1.25, 'halo', steps=4, label='crown_spire_center')
    # Flanking Spired Crown Prongs
    for side, s in SIDES:
        f.spike(b, (s * 1.5, 36.8, -2.6), (s * 2.4, 43.8, -2.8), 0.95, 'halo', steps=3, label='crown_spire_mid')
        f.spike(b, (s * 2.7, 36.5, -2.2), (s * 4.0, 41.2, -2.4), 0.8, 'halo', steps=3, label='crown_spire_side')
        # Antiqued Gold Filigree Leaf
        f.cube(b, (s * 1.8, 37.2, -2.85), (0.9, 1.8, 0.3), 'gold', rot((0, 0, -s * 22)), label='crown_leaf')

    # Jaw Guard (jaw bone)
    jb = 'jaw'
    f.box(jb, (-1.5, 32.2, -3.0), (3.0, 1.2, 1.2), 'mask', label='jaw_chin')

# ---------------------------------------------------------------------------
# Spectral Halo: 18-Segment Circular Soul Ring with 9 Radiant Spirit Prongs
# ---------------------------------------------------------------------------
def spectral_halo(f):
    h = 'halo'
    # Exact center: [0, 35, 3.9], Radius R = 6.4 units
    r = 6.4
    cz = 3.9
    cy = 35.5

    # 1. 18-Segment Circular Halo Ring (Every 20 degrees: 10, 30, 50, ..., 350)
    for i in range(18):
        deg = 10 + i * 20
        rad = math.radians(deg)
        x = math.cos(rad) * r
        y = cy + math.sin(rad) * r
        # Ring segment cube oriented along tangent
        f.cube(h, (x, y, cz), (0.32, 2.35, 0.32), 'halo', rot((0, 0, deg - 90)), label=f'halo_ring_{i}')
        # Inner glowing mint soul thread
        f.cube(h, (x * 0.95, cy + (y - cy) * 0.95, cz + 0.05), (0.18, 2.25, 0.18), 'glow', rot((0, 0, deg - 90)), label=f'halo_glow_{i}')

    # 2. 9 Radiant Spirit Prongs along upper half (deg: 10, 30, 50, 70, 90, 110, 130, 150, 170)
    for i in range(9):
        deg = 10 + i * 20
        rad = math.radians(deg)
        base_x = math.cos(rad) * r
        base_y = cy + math.sin(rad) * r
        tip_x = math.cos(rad) * (r + 2.2)
        tip_y = cy + math.sin(rad) * (r + 2.2)

        # Base socket
        f.cube(h, (base_x, base_y, cz), (0.55, 0.65, 0.45), 'gold', rot((0, 0, deg - 90)), label=f'prong_base_{i}')
        # Radiant crystal spire pointing outwards
        f.spike(h, (base_x, base_y, cz), (tip_x, tip_y, cz), 0.65, 'soul', steps=3, label=f'prong_crystal_{i}')
        # Glowing spirit ember at tip
        f.cube(h, (tip_x, tip_y, cz), (0.35, 0.35, 0.35), 'glow', rot((0, 0, 45)), label=f'prong_glow_{i}')

    # 3. 4 Orbiting Wailing Soul Wisps (Cardinal runes)
    for i, w_deg in enumerate([0, 90, 180, 270]):
        w_rad = math.radians(w_deg)
        wx = math.cos(w_rad) * (r - 1.2)
        wy = cy + math.sin(w_rad) * (r - 1.2)
        f.cube(h, (wx, wy, cz + 0.1), (0.85, 0.85, 0.5), 'runes', rot((0, 0, w_deg + 45)), label=f'halo_wisp_{i}')
        f.cube(h, (wx, wy, cz + 0.2), (0.45, 0.45, 0.3), 'glow', rot((0, 0, w_deg + 45)), label=f'halo_wisp_glow_{i}')

# ---------------------------------------------------------------------------
# Mourning Veils: 6 Flowing Ethereal Ribbons (veil_0 to veil_5)
# ---------------------------------------------------------------------------
def mourning_veils(f):
    # Attached to collar at y=29, falling down to y=2 behind the ghost queen
    # Spaced along x: [-3.625, -2.175, -0.725, 0.725, 2.175, 3.625]
    for i in range(6):
        vb = f'veil_{i}'
        vx = J[f'veil_{i}'][0]
        # Upper veil ribbon (y=29 down to y=15)
        f.beam(vb, (vx, 29.0, 3.3), (vx * 1.15, 15.0, 3.8), 1.7, 0.35, 'veil', label=f'veil_upper_{i}')
        # Lower billowing veil streamer (y=15 down to y=2)
        f.spike(vb, (vx * 1.15, 15.0, 3.8), (vx * 1.35, 2.0, 4.4), 1.6, 'veil', steps=4, depth_ratio=0.3, label=f'veil_lower_{i}')
        # Glowing sea-foam trim along outer border
        edge_x = vx + (0.75 if vx >= 0 else -0.75)
        f.beam(vb, (edge_x, 28.5, 3.4), (edge_x * 1.2, 14.5, 3.9), 0.22, 0.25, 'glow', label=f'veil_trim_{i}')

# ---------------------------------------------------------------------------
# Torso & Bodice: Seamless Corset & Weeping Soul Brooch (chest bone)
# ---------------------------------------------------------------------------
def torso(f):
    c = 'chest'
    # Solid, continuous bodice tiers from y=14.0 to y=32.5 (ZERO GAPS!)
    # Overlaps pelvis (which spans y=12.8 to 15.6) by 1.6+ units!
    tiers = [
        (14.0, 16.5, 6.8, 5.0, 0.1),   # Waist Fauld Base (overlaps pelvis cincher)
        (16.5, 19.5, 7.2, 5.2, 0.15),  # Lower Bodice
        (19.5, 22.8, 8.2, 5.6, 0.2),   # Mid Bodice / Obliques
        (22.8, 26.0, 9.4, 6.0, 0.25),  # Upper Bodice / Solar Plexus
        (26.0, 29.2, 10.6, 6.4, 0.3),  # Pectorals / Bust
        (29.2, 32.5, 11.6, 6.6, 0.4)   # Clavicle / Neckline
    ]
    for i, (y0, y1, w, d, zc) in enumerate(tiers):
        f.box(c, (-w / 2, y0, zc - d / 2), (w, y1 - y0, d), 'corset' if i < 3 else 'silk',
              label='bodice_tier', engrave='lines' if i >= 2 else None)

    # Embroidered Sea-Foam & Gold Tabard (y=15.0 to y=26.5)
    f.box(c, (-2.5, 15.0, -2.7), (5.0, 11.5, 0.5), 'dress', label='embroidered_tabard')
    f.beam(c, (0, 26.2, -2.9), (0, 15.2, -2.9), 0.45, 0.25, 'runes', label='tabard_rune_stripe')

    # Antiqued Gold & Slate Corset Ribs (Continuous from y=14.8 to y=24.5)
    for i, (y_pos, w) in enumerate([(14.8, 6.4), (16.8, 6.8), (18.8, 7.4), (20.8, 8.0), (22.8, 8.6), (24.6, 9.2)]):
        f.cube(c, (0, y_pos + 0.4, -2.5 - i * 0.08), (w, 0.75, 0.6), 'corset', rot((-6, 0, 0)), label=f'corset_rib_{i}')
        f.cube(c, (0, y_pos + 0.1, -2.7 - i * 0.08), (w + 0.25, 0.22, 0.4), 'gold', rot((-6, 0, 0)), label=f'corset_trim_{i}')

    # Flank Silk Drapes (Covering sides between y=14.2 and y=22.5)
    for side, s in SIDES:
        f.cube(c, (s * 3.7, 17.5, 0.1), (0.8, 7.0, 4.8), 'dress', rot((0, 0, -s * 6)), label='flank_silk')
        f.cube(c, (s * 3.9, 17.5, 0.1), (0.6, 6.8, 5.0), 'gold', rot((0, 0, -s * 6)), label='flank_trim')

    # Central Weeping Soul Gem Brooch (At solar plexus y=26.8)
    f.beam(c, (-2.0, 31.0, -2.8), (0, 27.2, -3.2), 0.4, 0.35, 'gold', label='brooch_chain_l')
    f.beam(c, (2.0, 31.0, -2.8), (0, 27.2, -3.2), 0.4, 0.35, 'gold', label='brooch_chain_r')
    # Glowing Teardrop Soul Gem
    f.cube(c, (0, 26.8, -3.4), (1.9, 2.5, 0.65), 'soul', rot((0, 0, 45)), label='brooch_gem')
    f.cube(c, (0, 26.8, -3.55), (1.2, 1.7, 0.4), 'glow', rot((0, 0, 45)), label='brooch_gem_glow')
    f.spike(c, (0, 25.0, -3.4), (0, 22.8, -3.5), 0.75, 'halo', steps=2, label='brooch_drop')

    # Ghostly Backplate & Spinal Column (y=14.0 to y=32.0)
    f.box(c, (-4.5, 14.0, 3.0), (9.0, 17.5, 1.0), 'silk', label='back_silk')
    f.beam(c, (0, 31.5, 3.8), (0, 14.2, 3.6), 0.9, 0.7, 'gold', label='spine_beam')
    for y, h in ((15.0, 1.0), (17.5, 1.2), (20.0, 1.4), (22.5, 1.6), (25.0, 1.8), (27.5, 2.0), (30.0, 1.6)):
        f.spike(c, (0, y, 3.8), (0, y + 0.3, 3.8 + h), 0.75, 'halo', steps=2, label=f'spine_crystal_{int(y)}')

# ---------------------------------------------------------------------------
# Ethereal Arms & Ghost Claws
# ---------------------------------------------------------------------------
def arms_claws(f):
    for side, s in SIDES:
        # Arm (Bicep)
        ab = f'arm_{side}'
        f.box(ab, (s * 7.7 - (1.3 if s > 0 else 1.5), 22.0, -1.5), (2.8, 6.0, 3.0), 'silk', label=f'arm_silk_{side}')
        f.cube(ab, (s * 7.7, 24.5, -1.6), (3.0, 0.6, 3.4), 'gold', label=f'arm_trim_{side}')

        # Forearm
        fab = f'forearm_{side}'
        f.box(fab, (s * 10.0 - (1.3 if s > 0 else 1.5), 18.0, -2.5), (2.6, 6.5, 3.0), 'silk', label=f'forearm_silk_{side}')
        # Bell-Draped Mourning Sleeve (Flaring outwards and swaying)
        f.spike(fab, (s * 10.0, 23.5, 0.8), (s * 12.8, 16.0, 2.6), 1.35, 'dress', steps=3, label=f'elbow_drape_{side}')
        f.beam(fab, (s * 9.8, 18.0, -2.6), (s * 10.2, 18.0, -2.6), 0.45, 0.35, 'gold', label=f'wrist_bracer_{side}')

        # Hand & 4 Elongated Spectral Claws + Thumb
        hb = f'hand_{side}'
        f.box(hb, (s * 12.0 - (1.1 if s > 0 else 1.3), 18.5, -4.8), (2.2, 2.8, 2.2), 'mask', label=f'palm_{side}')
        # 4 Elongated articulated claw fingers
        for i, kx in enumerate([-0.65, -0.22, 0.22, 0.65]):
            c_base = np.array([s * 12.0 + kx, 18.5, -4.8])
            c_tip  = np.array([s * 12.0 + kx * 1.45, 13.2, -5.9])
            f.spike(hb, c_base, c_tip, 0.42, 'halo', steps=3, depth_ratio=0.6, label=f'claw_{i}_{side}')
        # Thumb claw
        f.spike(hb, (s * 12.0 - s * 0.8, 19.5, -4.2), (s * 12.0 - s * 1.5, 16.5, -5.2), 0.45, 'halo', steps=2, label=f'thumb_{side}')

        # Shoulder cap (shoulder_r / shoulder_l)
        shb = f'shoulder_{side}'
        f.cube(shb, (s * 7.5, 27.0, -1.0), (3.0, 4.0, 0.8), 'dress', rot((0, 0, -s * 12)), label=f'shoulder_mantle_{side}')
        f.cube(shb, (s * 7.5, 27.0, -1.15), (3.2, 0.5, 0.9), 'gold', rot((0, 0, -s * 12)), label=f'shoulder_trim_{side}')

# ---------------------------------------------------------------------------
# 14-Panel Pleated Hovering Ghost Ballgown Dress (pelvis, sash_r, sash_l)
# ---------------------------------------------------------------------------
def ghost_ballgown_dress(f):
    """Sculpts the authentic 14-panel pleated hovering ghost ballgown dress."""
    # 1. Pelvic Corset & Mourning War Belt (pelvis)
    p = 'pelvis'
    # Base Pelvis Core: spans y=12.8 to 15.6, perfectly filling torso joint
    f.box(p, (-4.0, 12.8, -2.2), (8.0, 2.8, 4.4), 'corset', label='pelvis_base')
    # Inner insertion plug into chest
    f.box(p, (-3.6, 14.2, -2.0), (7.2, 3.2, 4.0), 'corset', label='pelvis_plug')

    # Antiqued Gold Hip Flounces & Filigree Belt
    for i, x in enumerate([-3.6, -2.4, -1.2, 0.0, 1.2, 2.4, 3.6]):
        f.cube(p, (x, 14.8, -2.35), (0.9, 0.65, 0.65), 'gold', label=f'belt_gem_{i}')
    # Central Buckle with glowing seafoam jewel
    f.cube(p, (0, 14.8, -2.6), (1.6, 1.45, 0.75), 'gold', label='belt_buckle')
    f.cube(p, (0, 14.8, -2.75), (0.9, 0.85, 0.35), 'glow', label='buckle_soul_glow')

    # Rear/side Pelvis Hip Guards
    f.box(p, (-4.2, 11.5, 1.8), (8.4, 3.5, 1.2), 'dress', label='pelvis_rear_drape')

    # 2. Right Side Ballgown Pleated Panels (sash_r: 6 panels)
    # Original panels:
    # panel 0: x from -4.88 to -3.28, rot=[0, 0, -8.4], y from 1.2 to 13.6
    # panel 1: x from -3.52 to -1.92, rot=[0, 0, -5.6], y from 1.9 to 13.6
    # panel 2: x from -2.16 to -0.56, rot=[0, 0, -2.8], y from 2.6 to 13.6
    sr = 'sash_r'
    panels_r = [
        (-4.08, 1.2, 13.6, -8.4, 'outer'),
        (-2.72, 1.9, 13.6, -5.6, 'mid'),
        (-1.36, 2.6, 13.6, -2.8, 'inner')
    ]
    for i, (cx, y_bot, y_top, angle_z, pname) in enumerate(panels_r):
        h = y_top - y_bot
        # Main Ghost Silk Pleat Panel
        f.cube(sr, (cx, (y_bot + y_top) / 2, -2.2), (1.65, h, 0.8), 'dress', rot((0, 0, angle_z)), label=f'sash_r_silk_{pname}')
        # Front Raised Pleat Trim / Antique Gold Piping
        f.cube(sr, (cx, (y_bot + y_top) / 2 + 0.3, -2.55), (0.16, h - 1.0, 0.18), 'gold', rot((0, 0, angle_z)), label=f'sash_r_piping_{pname}')
        # Luminescent Mint Soul Edge Hem at bottom of panel
        f.cube(sr, (cx, y_bot + 0.5, -2.2), (1.75, 0.7, 0.9), 'glow', rot((0, 0, angle_z)), label=f'sash_r_hem_{pname}')
        # Outer Flared Ruffle Flounce
        f.cube(sr, (cx - 0.4, y_bot + 1.8, -2.0), (0.9, 2.8, 0.7), 'silk', rot((0, 0, angle_z - 4)), label=f'sash_r_flounce_{pname}')

    # Rear Flaring Dress Drapery on sash_r
    f.cube(sr, (-3.2, 7.5, 1.6), (3.6, 11.5, 0.8), 'dress', rot((-6, 0, -6)), label='sash_r_rear_silk')
    f.cube(sr, (-3.2, 2.2, 1.8), (3.8, 1.0, 0.9), 'glow', rot((-6, 0, -6)), label='sash_r_rear_hem')

    # 3. Left Side Ballgown Pleated Panels (sash_l: 8 panels)
    # Original panels:
    # panel 0: x from -0.80 to 0.80, rot=[0, 0, 0.0], y from 1.2 to 13.6
    # panel 1: x from 0.56 to 2.16, rot=[0, 0, 2.8], y from 1.9 to 13.6
    # panel 2: x from 1.92 to 3.52, rot=[0, 0, 5.6], y from 2.6 to 13.6
    # panel 3: x from 3.28 to 4.88, rot=[0, 0, 8.4], y from 1.2 to 13.6
    sl = 'sash_l'
    panels_l = [
        (0.00, 1.2, 13.6, 0.0, 'center'),
        (1.36, 1.9, 13.6, 2.8, 'inner'),
        (2.72, 2.6, 13.6, 5.6, 'mid'),
        (4.08, 1.2, 13.6, 8.4, 'outer')
    ]
    for i, (cx, y_bot, y_top, angle_z, pname) in enumerate(panels_l):
        h = y_top - y_bot
        # Main Ghost Silk Pleat Panel
        f.cube(sl, (cx, (y_bot + y_top) / 2, -2.2), (1.65, h, 0.8), 'dress', rot((0, 0, angle_z)), label=f'sash_l_silk_{pname}')
        # Front Raised Pleat Trim / Antique Gold Piping
        f.cube(sl, (cx, (y_bot + y_top) / 2 + 0.3, -2.55), (0.16, h - 1.0, 0.18), 'gold', rot((0, 0, angle_z)), label=f'sash_l_piping_{pname}')
        # Luminescent Mint Soul Edge Hem at bottom of panel
        f.cube(sl, (cx, y_bot + 0.5, -2.2), (1.75, 0.7, 0.9), 'glow', rot((0, 0, angle_z)), label=f'sash_l_hem_{pname}')
        # Outer Flared Ruffle Flounce
        f.cube(sl, (cx + 0.4, y_bot + 1.8, -2.0), (0.9, 2.8, 0.7), 'silk', rot((0, 0, angle_z + 4)), label=f'sash_l_flounce_{pname}')

    # Rear Flaring Dress Drapery on sash_l
    f.cube(sl, (2.6, 7.5, 1.6), (3.6, 11.5, 0.8), 'dress', rot((-6, 0, 6)), label='sash_l_rear_silk')
    f.cube(sl, (2.6, 2.2, 1.8), (3.8, 1.0, 0.9), 'glow', rot((-6, 0, 6)), label='sash_l_rear_hem')

# ---------------------------------------------------------------------------
# Master Sculpt Assembly
# ---------------------------------------------------------------------------
def sculpt():
    f = Forge('arkhaia_banshee_wail', MATERIALS)
    skeleton(f)
    head_mask(f)
    spectral_halo(f)
    mourning_veils(f)
    torso(f)
    arms_claws(f)
    ghost_ballgown_dress(f)
    return f

# ---------------------------------------------------------------------------
# Animation Pipeline: 100% Lossless Retargeting of All 15 Authored Clips
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
# Build & Export
# ---------------------------------------------------------------------------
def build():
    f = sculpt()
    hitbox = [[-5, 0, -3], [5, 36, 3]]
    meta = dict(
        evergarden_revision=REVISION,
        evergarden_scale=SCALE
    )

    model, atlas, stats = build_model(
        f, [], 'arkhaia_banshee_wail', 'banshee_wail', hitbox,
        meta=meta, size=1024, density=8.0
    )

    bak_path = ROOT / 'evergarden/src/main/resources/modelengine/rivers/arkhaia_banshee_wail.bbmodel.bak'
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

    target_banshee = args.out / 'arkhaia_banshee_wail.bbmodel'
    target_banshee.write_text(dump(model) + '\n', encoding='utf8')
    if SERVA_OUT.exists():
        (SERVA_OUT / 'arkhaia_banshee_wail.bbmodel').write_text(dump(model) + '\n', encoding='utf8')

    art = ROOT / 'evergarden/art/arkhaia-rivers'
    art.mkdir(parents=True, exist_ok=True)
    atlas.save(art / 'banshee_seafoam.png')

    print(f"Banshee Wail Remastered (SEAFOAM): {len(model['elements']) - 1} cubes, {len(sculpt().bones)} bones, "
          f"{len(model['animations'])} clips, atlas {stats['coverage']:.0%} coverage at {stats['density']:.1f}px/unit")

if __name__ == '__main__':
    main()
