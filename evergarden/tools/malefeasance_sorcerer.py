"""Malefeasance, the Astral Arch-Sorceress: Remastered ModelEngine R4 Blueprint (v3).

Sculpted, painted, and rigged using the high-fidelity procedural pipeline of Evergarden bosses:
- Gorgeous, slender Anime Arch-Sorceress silhouette: porcelain skin, captivating anime eyes,
  luxurious flowing cosmic-violet hair, delicate bangs, and celestial astral circlet.
- Sleek, form-fitting high-slit silk dress (not puffy/bloated): sweetheart neckline velvet bodice,
  golden corset filigree, glowing 5-point star chest pendant, and flowing split-skirt sashes.
- Completely seamless, gapless anatomy: head connects to neck, neck to chest, chest to pelvis,
  pelvis to legs. Zero torso gaps, zero broken body lines.
- Slender bare shoulders, detached flowing bell-sleeves with lace cuffs, and high-heeled arcane lace-up boots.
- Signature Sorceress Implements: Levitating luxury Grimoire with glowing open pages and floating rune crystals,
  celestial zodiac halo behind head, cute floating astral familiar spirit, and sheer starlight back veils.
- 100% preservation of all 17 authored animations from the backup model.
"""
import argparse
import copy
import json
import math
from pathlib import Path
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'evergarden/tools'))

from boss_forge import (
    Clip, Forge, Material, Poser, Track,
    basis, bezier, blend, build_model, dump, ease, ease_in, ease_out,
    euler, frame2, hex_rgb, keyed, lerp, rot, slerp, unit, FACES, FACE_AXES,
    paint_atlas
)

OUT = ROOT / 'evergarden/src/main/resources/modelengine/rivers'
SCALE = 3.0
REVISION = 'malefeasance-sorceress-v3'

# ---------------------------------------------------------------------------
# Color Palettes & Materials
# ---------------------------------------------------------------------------
MIDNIGHT_VELVET = ('#0d0d1e', '#161730', '#222348', '#34366e')
ROYAL_AMETHYST  = ('#241233', '#3d1d57', '#5f2b8a', '#8a3ec7')
VOID_SILK       = ('#100a1c', '#1b122e', '#2c1b4d', '#422575')
CELESTIAL_GOLD  = ('#4a3412', '#8c6820', '#d8aa36', '#fae07a')
ASTRAL_CYAN     = ('#0a3040', '#106482', '#22b4d8', '#88f4ff')
STAR_GLOW       = ('#183868', '#2a62b8', '#58a0ff', '#b8dcff')
PORCELAIN_SKIN  = ('#f6e4d8', '#faece3', '#fdf5ef', '#ffffff')
COSMIC_HAIR     = ('#1b142c', '#2c1e45', '#422c66', '#644299')
LEATHER_TOME    = ('#180f24', '#26163a', '#3b2259', '#543080')
PARCHMENT_PAGE  = ('#2b261e', '#52493b', '#a69981', '#dfd5be')
TIGHTS_SHEER    = ('#14101e', '#1e182e', '#2c2242', '#3e325c')

SIDES = (('r', -1), ('l', 1))

MATERIALS = {
    'skin': Material(PORCELAIN_SKIN, 'cloth', density=1.2),
    'hair': Material(COSMIC_HAIR, 'cloth', density=1.2),
    'velvet': Material(MIDNIGHT_VELVET, 'cloth', trim=CELESTIAL_GOLD, density=1.1),
    'amethyst': Material(ROYAL_AMETHYST, 'cloth', density=1.1),
    'tights': Material(TIGHTS_SHEER, 'cloth', density=1.0),
    'gold': Material(CELESTIAL_GOLD, 'trim', density=1.2),
    'cyan': Material(ASTRAL_CYAN, 'glow', glow=True, density=1.2),
    'glow': Material(STAR_GLOW, 'glow', glow=True, density=1.2),
    'leather': Material(LEATHER_TOME, 'plate', trim=CELESTIAL_GOLD, density=1.1),
    'pages': Material(PARCHMENT_PAGE, 'cloth', density=1.0),
    'veil': Material(ASTRAL_CYAN, 'membrane', glow=True, density=1.0),
}

# ---------------------------------------------------------------------------
# Exact Joint Pivots from original Malefeasance rig
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
            f'elbow_{side}': (s * 9.0, 22, -1.4),
            f'wrist_{side}': (s * 10.0, 18, -5),
            f'hip_{side}': (s * 2.8, 15, 0),
            f'knee_{side}': (s * 2.8, 8, 0),
            f'ankle_{side}': (s * 2.8, 1.5, 0),
            f'sash_{side}': (s * 3.8, 15, 0),
        })
    j['book'] = (-10.0, 21.0, -5.2)
    return {k: np.array(v, float) for k, v in j.items()}

J = joints()

# ---------------------------------------------------------------------------
# Rig Structure Definition
# ---------------------------------------------------------------------------
def skeleton(f):
    f.bone('root', None, [0, 0, 0])
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
        f.bone(f'arm_{side}', 'chest', J[f'shoulder_{side}'])
        f.bone(f'forearm_{side}', f'arm_{side}', J[f'elbow_{side}'])
        f.bone(f'hand_{side}', f'forearm_{side}', J[f'wrist_{side}'])
        f.bone(f'weapon_{side}', f'hand_{side}', J[f'wrist_{side}'])
        f.bone(f'leg_{side}', 'pelvis', J[f'hip_{side}'])
        f.bone(f'shin_{side}', f'leg_{side}', J[f'knee_{side}'])
        f.bone(f'foot_{side}', f'shin_{side}', J[f'ankle_{side}'])
        f.bone(f'sash_{side}', 'pelvis', J[f'sash_{side}'])

    f.bone('book', 'hand_r', J['book'])

# ---------------------------------------------------------------------------
# Gorgeous Head, Face, Hair & Astral Tiara
# ---------------------------------------------------------------------------
def head_and_hair(f):
    b = 'head'

    # 1. Base Head & Porcelain Face (World space: Y=31.6 to 36.8, center Y=34.2)
    # This cuboid holds the custom-painted anime sorceress face on its north face!
    f.box(b, (-2.5, 31.6, -2.4), (5.0, 5.2, 4.6), 'skin', label='sorceress_head_base')

    # 2. Luxurious Cosmic Hair
    # Crown & Top Hair
    f.box(b, (-2.7, 36.2, -2.5), (5.4, 1.2, 5.0), 'hair', label='hair_crown')

    # Front Bangs framing the forehead
    f.box(b, (-2.4, 34.6, -2.7), (4.8, 1.8, 0.5), 'hair', label='hair_bangs')
    # Soft center wisps
    f.cube(b, (0.0, 34.4, -2.75), (1.4, 1.4, 0.4), 'hair', rot((0, 0, 0)), label='bangs_center')
    f.cube(b, (-1.3, 34.2, -2.75), (1.0, 1.6, 0.4), 'hair', rot((0, 0, 10)), label='bangs_wisp_r')
    f.cube(b, (1.3, 34.2, -2.75), (1.0, 1.6, 0.4), 'hair', rot((0, 0, -10)), label='bangs_wisp_l')

    # Long Twin Side Locks cascading down past jawline and shoulders (Y=30.0 down to 26.5)
    for side, s in SIDES:
        # Upper side hair along temples
        f.box(b, (s * 2.3 if s > 0 else s * 2.3 - 0.7, 31.8, -2.5), (0.7, 4.6, 4.8), 'hair', label=f'hair_temple_{side}')
        # Flowing front side lock (falls in front of shoulders)
        f.beam(b, (s * 2.4, 34.5, -2.5), (s * 2.2, 28.5, -2.2), 0.7, 0.6, 'hair', label=f'side_lock_upper_{side}')
        f.beam(b, (s * 2.2, 28.5, -2.2), (s * 2.0, 24.5, -1.8), 0.6, 0.45, 'hair', label=f'side_lock_lower_{side}')
        # Golden ribbon tie on side lock
        f.cube(b, (s * 2.2, 28.4, -2.2), (0.8, 0.4, 0.8), 'gold', rot((0, 0, 0)), label=f'hair_ribbon_{side}')

    # Cascading Back Hair (Layered waves flowing down the back from 36.5 down to 25.0)
    f.box(b, (-2.7, 31.5, 2.0), (5.4, 5.0, 0.8), 'hair', label='hair_back_upper')
    # Mid back hair wave
    f.box(b, (-2.9, 27.5, 2.1), (5.8, 4.4, 0.8), 'hair', label='hair_back_mid')
    # Lower back hair tips
    f.box(b, (-2.6, 24.0, 2.2), (5.2, 3.8, 0.7), 'hair', label='hair_back_lower')

    # 3. Celestial Astral Circlet / Tiara
    f.beam(b, (-2.6, 36.2, -2.6), (2.6, 36.2, -2.6), 0.45, 0.35, 'gold', label='tiara_band_front')
    for side, s in SIDES:
        f.beam(b, (s * 2.6, 36.2, -2.6), (s * 2.6, 36.2, 0.0), 0.4, 0.35, 'gold', label=f'tiara_band_side_{side}')
        # Curved golden winglet at temples
        f.spike(b, (s * 2.5, 36.2, -1.8), (s * 3.4, 38.5, -1.0), 0.45, 'gold', steps=2, label=f'tiara_wing_{side}')
    # Center Forehead Astral Star-Gem (Teardrop cyan gem)
    f.cube(b, (0, 36.4, -2.85), (0.9, 1.2, 0.35), 'gold', rot((0, 0, 45)), label='tiara_gem_bezel')
    f.cube(b, (0, 36.4, -2.95), (0.6, 0.85, 0.25), 'cyan', rot((0, 0, 45)), label='tiara_gem_core')

    # Jaw / Chin Wrap (`jaw` bone)
    jb = 'jaw'
    # Delicate feminine jawline support
    f.box(jb, (-1.8, 31.6, -2.2), (3.6, 1.2, 3.8), 'skin', label='jaw_contour')

    # 4. Astral Zodiac Halo (`halo` on `chest`)
    h = 'halo'
    halo_center = np.array([0.0, 36.5, 3.2])
    radius = 5.2
    # 16-Segment Luminous Circular Ring
    for i in range(16):
        a0 = i * math.tau / 16
        a1 = (i + 1) * math.tau / 16
        p0 = halo_center + np.array([math.cos(a0) * radius, math.sin(a0) * radius, 0])
        p1 = halo_center + np.array([math.cos(a1) * radius, math.sin(a1) * radius, 0])
        f.beam(h, p0, p1, 0.32, 0.32, 'gold', label='halo_ring_seg')

    # Inner Astral Glyphs Ring
    radius_inner = 3.6
    for i in range(12):
        a0 = i * math.tau / 12
        a1 = (i + 1) * math.tau / 12
        p0 = halo_center + np.array([math.cos(a0) * radius_inner, math.sin(a0) * radius_inner, 0])
        p1 = halo_center + np.array([math.cos(a1) * radius_inner, math.sin(a1) * radius_inner, 0])
        f.beam(h, p0, p1, 0.22, 0.22, 'glow', label='halo_inner_seg')

    # 4 Cardinal Astral Star Pointers (North, South, East, West)
    for k in range(4):
        ang = k * math.pi / 2
        p_pt = halo_center + np.array([math.cos(ang) * (radius + 0.8), math.sin(ang) * (radius + 0.8), 0])
        f.cube(h, p_pt, (0.8, 0.8, 0.35), 'gold', rot((0, 0, 45)), label='halo_pointer_bezel')
        f.cube(h, p_pt, (0.5, 0.5, 0.4), 'cyan', rot((0, 0, 45)), label='halo_pointer_gem')

# ---------------------------------------------------------------------------
# Slender Neck & Form-Fitting Bodice (Torso)
# ---------------------------------------------------------------------------
def neck_and_torso(f):
    # 1. Slender Porcelain Neck (`neck` at [0, 29, 0])
    # Seamless transition: Y=28.2 to 32.0 (Overlaps chest at 28.2, overlaps head at 31.8)
    f.box('neck', (-1.2, 28.2, -1.2), (2.4, 3.8, 2.4), 'skin', label='neck_column')
    # Golden Astral Choker
    f.box('neck', (-1.35, 29.2, -1.35), (2.7, 0.7, 2.7), 'gold', label='neck_choker')
    f.cube('neck', (0, 29.2, -1.5), (0.5, 0.5, 0.25), 'cyan', rot((0, 0, 45)), label='choker_gem')

    # 2. Form-Fitting Bodice (`chest` at [0, 24, 0])
    # Total chest height: Y=14.8 up to Y=28.4 (ZERO GAP to pelvis at Y=15.0!)
    c = 'chest'

    # (A) Lower Waist / Abdomen (Y=14.8 to 18.0) - Connects directly with pelvis!
    f.box(c, (-2.5, 14.8, -1.9), (5.0, 3.2, 3.8), 'velvet', label='bodice_waist_lower')

    # (B) Mid Waist / Corset (Y=17.8 to 21.0) - Slender hourglass silhouette
    f.box(c, (-2.7, 17.8, -2.0), (5.4, 3.2, 4.0), 'velvet', label='bodice_waist_mid')

    # (C) Bust & Sweetheart Neckline (Y=20.8 to 24.5) - Contoured feminine bust
    f.box(c, (-3.1, 20.8, -2.1), (6.2, 3.7, 4.2), 'velvet', label='bodice_bust_base')
    # Feminine bust curvature contours
    for side, s in SIDES:
        f.cube(c, (s * 1.5, 22.8, -2.2), (2.6, 2.2, 1.2), 'velvet', rot((-8, -s * 6, s * 4)), label=f'bust_contour_{side}')

    # (D) Upper Chest & Clavicle (Y=24.2 to 28.2) - Sweetheart neckline & porcelain skin
    f.box(c, (-3.0, 24.2, -1.9), (6.0, 4.0, 3.8), 'skin', label='chest_clavicle_skin')
    # Bodice side straps / neckline borders
    for side, s in SIDES:
        f.beam(c, (s * 3.0, 24.0, -2.0), (s * 1.8, 28.2, -1.6), 0.5, 0.4, 'velvet', label=f'neckline_strap_{side}')
        f.beam(c, (s * 3.0, 24.0, -2.0), (s * 1.8, 28.2, -1.6), 0.35, 0.25, 'gold', label=f'strap_gold_edge_{side}')

    # (E) Front Golden Corset Lacing & Astral Star Pendant
    # Center golden laces
    for y in (16.2, 18.0, 19.8):
        f.beam(c, (-1.2, y - 0.4, -2.1), (1.2, y + 0.4, -2.1), 0.25, 0.2, 'gold', label='corset_lace_1')
        f.beam(c, (-1.2, y + 0.4, -2.1), (1.2, y - 0.4, -2.1), 0.25, 0.2, 'gold', label='corset_lace_2')

    # Radiant 5-Point Astral Star Pendant centered on the sternum (Y=24.6)
    f.cube(c, (0, 24.6, -2.3), (1.4, 1.4, 0.3), 'gold', rot((0, 0, 45)), label='star_pendant_bezel')
    f.cube(c, (0, 24.6, -2.4), (0.9, 0.9, 0.3), 'cyan', rot((0, 0, 45)), label='star_pendant_gem')
    # Delicate golden necklace chain from collarbone down to star pendant
    f.beam(c, (-1.4, 28.0, -1.5), (0, 24.9, -2.25), 0.2, 0.2, 'gold', label='necklace_chain_r')
    f.beam(c, (1.4, 28.0, -1.5), (0, 24.9, -2.25), 0.2, 0.2, 'gold', label='necklace_chain_l')

    # Back bodice velvet panel
    f.box(c, (-2.8, 15.0, 1.6), (5.6, 13.0, 0.6), 'velvet', label='bodice_back')

# ---------------------------------------------------------------------------
# Pelvis, Golden Belt & High-Slit Skirt Sashes
# ---------------------------------------------------------------------------
def pelvis_and_skirt(f):
    p = 'pelvis'

    # 1. Feminine Hips & Waistline (Y=12.2 to 15.2, ZERO GAP to chest!)
    f.box(p, (-3.4, 12.2, -2.0), (6.8, 3.0, 4.0), 'velvet', label='pelvis_base')
    # Fitted underdress shorts (prevents any empty see-through angles during motion)
    f.box(p, (-3.2, 10.6, -1.9), (6.4, 2.0, 3.8), 'amethyst', label='underdress_shorts')

    # 2. Ornate Golden Astral Hip Chain & Belt
    f.box(p, (-3.5, 14.5, -2.1), (7.0, 0.8, 4.2), 'gold', label='waist_belt_band')
    # Central Crescent & Star Buckle
    f.cube(p, (0, 14.6, -2.3), (1.5, 1.5, 0.4), 'gold', rot((0, 0, 45)), label='belt_buckle_star')
    f.cube(p, (0, 14.6, -2.45), (0.8, 0.8, 0.3), 'cyan', rot((0, 0, 45)), label='belt_buckle_gem')
    # Dangling waist chains & celestial charms
    for side, s in SIDES:
        f.beam(p, (s * 0.6, 14.3, -2.2), (s * 2.2, 12.5, -2.2), 0.25, 0.2, 'gold', label=f'waist_chain_{side}')
        # Dangling star charm at hip
        f.cube(p, (s * 2.2, 12.3, -2.2), (0.7, 0.7, 0.25), 'gold', rot((0, 0, 45)), label=f'hip_charm_{side}')

    # 3. SLEEK High-Slit Skirt Panels (`sash_r` and `sash_l`)
    # NOT puffy/bloated! Thin, elegant cascading silk panels that hug the hips
    # and drape naturally along the sides and back, leaving the front slit open.
    for side, s in SIDES:
        sb = f'sash_{side}'
        # High slit skirt panels: 4 tiered overlapping strips per side
        # Width 1.6 to 1.8, thickness only 0.4 to 0.5 units!
        tiers = (
            # (dx, dy_top, dy_bot, dz, width, angle_z, angle_y)
            (s * 3.4, 15.0, 9.0, -0.6, 1.6, -s * 4, 10),    # front-side slit flap
            (s * 3.6, 15.0, 6.0, 0.2, 1.8, -s * 6, 0),      # mid-outer hip drape
            (s * 3.5, 15.0, 3.0, 1.0, 1.8, -s * 5, -10),    # rear-outer drape
            (s * 2.2, 15.0, 2.0, 1.8, 2.0, -s * 2, -20),    # back drape
        )
        for idx, (x, y0, y1, z, w, az, ay) in enumerate(tiers):
            r = rot((0, ay, az))
            center = np.array([x, (y0 + y1) / 2, z])
            h = y0 - y1
            # Midnight velvet outer drape
            f.cube(sb, center, (w, h, 0.45), 'velvet', r, label=f'skirt_strip_{side}_{idx}')
            # Golden embroidered hem at the bottom of each strip
            bot_pos = center + r @ np.array([0, -h / 2 + 0.2, 0])
            f.cube(sb, bot_pos, (w + 0.1, 0.4, 0.5), 'gold', r, label=f'skirt_gold_hem_{side}_{idx}')

# ---------------------------------------------------------------------------
# Slender Legs & High-Laced Arcane Boots
# ---------------------------------------------------------------------------
def legs_and_boots(f):
    for side, s in SIDES:
        hip = J[f'hip_{side}']
        kn = J[f'knee_{side}']
        an = J[f'ankle_{side}']
        lb, shb, ftb = f'leg_{side}', f'shin_{side}', f'foot_{side}'

        # 1. Thigh (`leg_{side}`: Y=8.2 to 14.8) - Slender legs sheathed in midnight sheer tights
        # Width: 2.2, Depth: 2.4 (slender feminine thighs, ZERO GAP with pelvis!)
        f.beam(lb, hip + (0, -0.2, 0), kn + (0, 0.4, 0), 2.3, 2.1, 'tights', label=f'thigh_sheer_{side}')
        # Delicate golden lace garter ring near top of thigh
        f.cube(lb, (s * 2.8, 13.5, 0.0), (2.5, 0.5, 2.6), 'gold', rot((0, 0, 0)), label=f'garter_band_{side}')
        f.cube(lb, (s * 2.8, 13.5, -1.35), (0.5, 0.5, 0.2), 'cyan', rot((0, 0, 45)), label=f'garter_gem_{side}')

        # 2. Knee & Shin (`shin_{side}`: Y=1.5 to 8.4) - Stylish High-Laced Boots
        # Overlaps thigh at Y=8.2 (ZERO GAP!)
        f.beam(shb, kn + (0, 0.2, 0), an + (0, 0.2, 0), 2.4, 2.2, 'velvet', label=f'boot_shin_{side}')
        # Golden knee protector crest
        f.cube(shb, (s * 2.8, 7.8, -1.2), (2.0, 1.2, 0.5), 'gold', rot((-10, 0, 0)), label=f'knee_crest_{side}')
        f.cube(shb, (s * 2.8, 7.8, -1.45), (0.6, 0.6, 0.25), 'cyan', rot((0, 0, 45)), label=f'knee_gem_{side}')

        # 4 Golden Boot Strap Buckles with diamond studs along shin
        for y in (6.5, 5.0, 3.5, 2.2):
            f.beam(shb, (s * 2.8 - 1.1, y, -1.15), (s * 2.8 + 1.1, y, -1.15), 0.35, 0.3, 'gold', label=f'boot_strap_{side}')
            f.cube(shb, (s * 2.8, y, -1.25), (0.45, 0.45, 0.2), 'gold', rot((0, 0, 45)), label=f'strap_stud_{side}')

        # 3. High-Heeled Arcane Boots (`foot_{side}`: Y=0.0 to 1.8)
        # Slender pointed-toe boots with elevated heel
        foot_center = np.array([s * 2.8, 0.9, -0.6])
        f.box(ftb, (s * 2.8 - 1.1, 0.0, -2.8), (2.2, 1.8, 3.8), 'velvet', label=f'boot_foot_{side}')
        # Sleek pointed toe
        f.spike(ftb, (s * 2.8, 0.6, -2.8), (s * 2.8, 0.4, -3.8), 0.5, 'gold', steps=2, label=f'boot_toe_point_{side}')
        # Slender arcane heel at the back
        f.beam(ftb, (s * 2.8, 0.8, 0.6), (s * 2.8, 0.0, 0.6), 0.4, 0.35, 'gold', label=f'boot_heel_{side}')
        # Glowing silent levitation rune on the sole
        f.box(ftb, (s * 2.8 - 0.9, -0.05, -2.6), (1.8, 0.15, 3.2), 'cyan', label=f'sole_rune_{side}')

# ---------------------------------------------------------------------------
# Slender Bare Arms & Detached Flowing Bell-Sleeves
# ---------------------------------------------------------------------------
def arms_and_sleeves(f):
    for side, s in SIDES:
        sb = f'shoulder_{side}'
        ab = f'arm_{side}'
        fab = f'forearm_{side}'
        hb = f'hand_{side}'
        sh = J[f'shoulder_{side}']
        el = J[f'elbow_{side}']
        wr = J[f'wrist_{side}']

        # 1. Bare Shoulder with Golden Epaulet Mantle (`shoulder_{side}`)
        # Delicate silk & gold crescent epaulet resting gracefully on the shoulder
        epaulet_center = np.array([s * 7.7, 28.0, 0.0])
        f.cube(sb, epaulet_center, (3.4, 1.8, 4.4), 'velvet', rot((0, 0, -s * 8)), label=f'shoulder_epaulet_{side}')
        f.cube(sb, epaulet_center + np.array([0, 0.5, 0]), (3.6, 0.5, 4.6), 'gold', rot((0, 0, -s * 8)), label=f'epaulet_gold_trim_{side}')
        f.cube(sb, epaulet_center + np.array([s * 0.2, 0.5, -1.8]), (0.7, 0.7, 0.3), 'cyan', rot((0, 0, 45)), label=f'epaulet_gem_{side}')

        # 2. Slender Upper Arm (`arm_{side}`) - Bare porcelain skin with bicep armlet
        f.beam(ab, sh + (0, -0.2, 0), el + (0, 0.2, 0), 2.0, 1.9, 'skin', label=f'upper_arm_skin_{side}')
        # Golden coiled armlet band around the bicep
        f.cube(ab, (s * 8.2, 25.4, 0.0), (2.3, 0.6, 2.4), 'gold', rot((0, 0, -s * 6)), label=f'bicep_armlet_{side}')

        # 3. Forearm & Detached Flowing Bell-Sleeves (`forearm_{side}`)
        # Inner slender forearm (porcelain skin)
        f.beam(fab, el + (0, -0.2, 0), wr + (0, 0.2, 0), 2.0, 1.8, 'skin', label=f'forearm_skin_{side}')

        # Detached billowing silk bell-sleeve (flares gracefully below elbow and behind wrist)
        # Not puffy! Slender top that cascades into an elegant draping bell
        sleeve_center = np.array([s * 9.5, 18.5, 0.5])
        f.box(fab, (s * 9.5 - 1.4, 16.5, -0.8), (2.8, 5.0, 3.2), 'velvet', label=f'bell_sleeve_{side}')
        # Rich amethyst silk lining exposed in sleeve opening
        f.box(fab, (s * 9.5 - 1.2, 16.6, -0.6), (2.4, 4.8, 2.8), 'amethyst', label=f'sleeve_lining_{side}')
        # Golden filigree hem around sleeve cuff
        f.beam(fab, (s * 9.5 - 1.4, 16.5, 2.4), (s * 9.5 + 1.4, 16.5, 2.4), 0.4, 0.35, 'gold', label=f'sleeve_gold_edge_{side}')
        # Delicate white lace ruffle extending past the gold edge
        f.box(fab, (s * 9.5 - 1.2, 15.6, 0.0), (2.4, 1.0, 2.6), 'skin', label=f'lace_cuff_{side}')

        # 4. Delicate Feminine Hand (`hand_{side}`)
        # Slender wrist with golden casting bracelet
        f.cube(hb, wr + (0, 0.2, 0), (2.2, 0.8, 2.2), 'gold', rot((0, 0, 0)), label=f'wrist_bracelet_{side}')
        f.cube(hb, wr + (0, 0.2, -1.15), (0.6, 0.6, 0.25), 'cyan', rot((0, 0, 45)), label=f'bracelet_gem_{side}')

        # Slender porcelain palm & fingers
        f.box(hb, (wr[0] - 0.9, wr[1] - 2.4, wr[2] - 0.9), (1.8, 2.2, 1.8), 'skin', label=f'hand_palm_{side}')
        # Slender fingers with amethyst nail polish & glowing arcane rune ring
        for i in range(3):
            fx = wr[0] + (i - 1) * 0.55
            f.cube(hb, (fx, wr[1] - 2.8, wr[2] - 0.8), (0.35, 0.9, 0.35), 'skin', rot((0, 0, 0)), label=f'finger_{side}_{i}')
            # Ring on index finger
            if i == 0:
                f.cube(hb, (fx, wr[1] - 2.6, wr[2] - 0.8), (0.45, 0.3, 0.45), 'gold', rot((0, 0, 0)), label=f'rune_ring_{side}')
                f.cube(hb, (fx, wr[1] - 2.6, wr[2] - 1.05), (0.25, 0.25, 0.15), 'cyan', rot((0, 0, 0)), label=f'ring_gem_{side}')

# ---------------------------------------------------------------------------
# Levitating Luxury Arcane Grimoire (`book` on `hand_r`)
# ---------------------------------------------------------------------------
def grimoire(f):
    b = 'book'
    center = np.array([-10.0, 21.0, -5.2])

    # Spine of the spellbook
    f.cube(b, center, (0.7, 4.8, 0.8), 'gold', rot((15, 0, 0)), label='grimoire_spine')
    f.cube(b, center + (0, 0, -0.45), (0.6, 1.2, 0.35), 'cyan', rot((15, 0, 45)), label='spine_gem')

    # Angled Open Covers (tome is open at 35 degrees, floating before her)
    for part_side, s_sign in (('left', -1), ('right', 1)):
        r_cov = rot((15, 0, s_sign * 35))
        cov_center = center + r_cov @ np.array([s_sign * 2.0, 0, 0.15])
        # Deep violet leather hardcover
        f.cube(b, cov_center, (3.5, 4.8, 0.45), 'leather', r_cov, label=f'grimoire_cover_{part_side}')
        # Golden filigree corner brackets
        for cy in (-1.9, 1.9):
            f.cube(b, cov_center + r_cov @ np.array([s_sign * 1.3, cy, -0.15]),
                   (0.8, 0.8, 0.35), 'gold', r_cov, label=f'cover_corner_{part_side}')
        # Outer golden filigree rim
        f.cube(b, cov_center + r_cov @ np.array([s_sign * 1.5, 0, -0.1]),
               (0.4, 4.6, 0.35), 'gold', r_cov, label=f'cover_rim_{part_side}')

        # Stacked Vellum Pages
        page_center = cov_center + r_cov @ np.array([-s_sign * 0.15, 0, -0.35])
        f.cube(b, page_center, (3.1, 4.4, 0.4), 'pages', r_cov, label=f'grimoire_pages_{part_side}')
        # Glowing cyan illuminated arcane script on page face
        f.cube(b, page_center + r_cov @ np.array([0, 0, -0.25]),
               (2.4, 3.6, 0.1), 'cyan', r_cov, label=f'illuminated_runes_{part_side}')

    # 3 Glowing Arcane Rune Shards levitating gracefully above the open pages
    f.cube(b, center + (-1.2, 1.6, -1.6), (0.5, 0.9, 0.35), 'cyan', rot((20, 15, -25)), label='rune_shard_1')
    f.cube(b, center + (1.2, 2.0, -1.8), (0.5, 0.9, 0.35), 'cyan', rot((10, -20, 20)), label='rune_shard_2')
    f.cube(b, center + (0.0, 2.6, -2.1), (0.6, 1.1, 0.4), 'glow', rot((0, 0, 45)), label='rune_shard_3')

# ---------------------------------------------------------------------------
# Cute Celestial Familiar Spirit (`familiar` on `chest`)
# ---------------------------------------------------------------------------
def celestial_familiar(f):
    b = 'familiar'
    center = np.array([13.0, 25.0, 0.0])

    # Glowing Luminous Spirit Orb Core
    f.cube(b, center, (2.6, 2.6, 2.6), 'cyan', rot((0, 0, 0)), label='familiar_core')
    f.cube(b, center, (2.0, 2.0, 2.0), 'glow', rot((0, 0, 45)), label='familiar_inner_star')

    # Cute tiny expressive eyes on the familiar
    f.cube(b, center + (-0.5, 0.2, -1.35), (0.4, 0.5, 0.15), 'gold', rot((0, 0, 0)), label='familiar_eye_l')
    f.cube(b, center + (0.5, 0.2, -1.35), (0.4, 0.5, 0.15), 'gold', rot((0, 0, 0)), label='familiar_eye_r')

    # Twin Translucent Celestial Fairy Wings
    for side, s in SIDES:
        w_ang = rot((15, s * 25, -s * 15))
        w_center = center + np.array([s * 1.5, 0.8, 0.6])
        f.cube(b, w_center, (2.4, 3.2, 0.2), 'veil', w_ang, label=f'familiar_wing_{side}')
        # Wing golden leading edge
        f.beam(b, center + (s * 1.0, 0.5, 0.3), w_center + (s * 1.2, 1.4, 0), 0.3, 0.25, 'gold', label=f'wing_edge_{side}')

    # Floating Starlight Ribbon Tails drifting underneath
    f.spike(b, center + (0, -1.2, 0), center + (-0.6, -3.8, 0.4), 0.5, 'cyan', steps=3, label='familiar_tail_1')
    f.spike(b, center + (0.4, -1.2, 0.2), center + (0.8, -4.2, -0.2), 0.4, 'glow', steps=3, label='familiar_tail_2')

# ---------------------------------------------------------------------------
# Collar, Ethereal Back Veils & Regal Sorceress Cape
# ---------------------------------------------------------------------------
def collar_veils_and_cape(f):
    col = 'collar'
    # Sleek Golden Collar Base at the nape of the neck (Y=28.0)
    f.box(col, (-3.8, 27.8, 1.2), (7.6, 1.6, 1.8), 'gold', label='collar_base')
    f.cube(col, (0, 28.6, 2.2), (1.2, 1.2, 0.4), 'cyan', rot((0, 0, 45)), label='collar_center_gem')

    # 6 Slender Sheer Starlight Veil Ribbons (`veil_0` to `veil_5`)
    # Positioned accurately in world coordinates from Y=28.5 down to Y=4.0
    w_ribbon = 1.1
    for i in range(6):
        vb = f'veil_{i}'
        x_base = J[f'veil_{i}'][0]
        # Thin translucent veil strip
        poly = [
            (x_base - w_ribbon / 2, 28.5, 3.1),
            (x_base + w_ribbon / 2, 28.5, 3.1),
            (x_base + w_ribbon / 2 + (i - 2.5) * 0.15, 4.0, 3.8),
            (x_base - w_ribbon / 2 + (i - 2.5) * 0.15, 4.0, 3.8)
        ]
        f.sheet(vb, poly, 'veil', thickness=0.06, up=(0, 1, 0), label=f'veil_strip_{i}',
                edge={1: ('wave', 0.2, 4), 3: ('wave', 0.2, 4)})

    # Regal Sorceress Cape (`cape`, `cape_tip`)
    cp = 'cape'
    # Upper Cape (World coordinates from Y=28.0 down to Y=17.5)
    # Sleek and majestic, sits naturally behind the shoulders without bulging
    top_poly = [
        (-4.6, 28.0, 3.2), (4.6, 28.0, 3.2),
        (5.8, 17.5, 4.0), (-5.8, 17.5, 4.0)
    ]
    f.sheet(cp, top_poly, 'velvet', thickness=0.08, up=(0, 1, 0), label='cape_upper',
            folds=4, lining=('north', ROYAL_AMETHYST[:4]))

    # Lower Cape (`cape_tip` from Y=17.5 down to Y=1.5)
    ct = 'cape_tip'
    bot_poly = [
        (-5.8, 17.5, 4.0), (5.8, 17.5, 4.0),
        (6.8, 1.5, 4.6), (4.0, 0.5, 4.6),
        (1.8, 1.2, 4.6), (0.0, 0.2, 4.6),
        (-1.8, 1.2, 4.6), (-4.0, 0.5, 4.6),
        (-6.8, 1.5, 4.6)
    ]
    f.sheet(ct, bot_poly, 'velvet', thickness=0.08, up=(0, 1, 0), label='cape_lower',
            folds=5, edge={3: ('scallop', 0.5, 5), 5: ('scallop', 0.5, 5)},
            hem=0.25, lining=('north', ROYAL_AMETHYST[:4]))

    # Hanging golden astral star beads along cape hem
    for x in (-5.2, -3.2, -1.2, 1.2, 3.2, 5.2):
        f.cube(ct, (x, 1.0, 4.7), (0.5, 0.5, 0.3), 'gold', rot((0, 0, 45)), label='cape_star_bead')
        f.cube(ct, (x, 1.0, 4.8), (0.3, 0.3, 0.2), 'cyan', rot((0, 0, 45)), label='cape_star_gem')

# ---------------------------------------------------------------------------
# Master Sculpt Function
# ---------------------------------------------------------------------------
def sculpt_malefeasance():
    f = Forge('arkhaia_malefeasance', MATERIALS, seed=108)
    skeleton(f)

    head_and_hair(f)
    neck_and_torso(f)
    pelvis_and_skirt(f)
    legs_and_boots(f)
    arms_and_sleeves(f)
    grimoire(f)
    celestial_familiar(f)
    collar_veils_and_cape(f)

    return f

# ---------------------------------------------------------------------------
# Custom Painting: Beautiful Anime Face & Textures
# ---------------------------------------------------------------------------
def paint_anime_face(img, w, h):
    """Paints a gorgeous, captivating anime arch-sorceress face onto the texture tile."""
    draw = ImageDraw.Draw(img)

    # 1. Soft Warm Porcelain Skin Shading
    for y in range(h):
        for x in range(w):
            nx = (x - w / 2) / (w / 2)
            ny = (y - h / 2) / (h / 2)
            # Ambient shading around edges
            edge_shade = max(0.0, (nx**2 + ny**2)**0.5 - 0.4) * 0.12
            # Soft shadow under bangs (top 25%)
            bangs_shade = max(0.0, (0.28 - y / h)) * 0.28
            # Soft shadow under chin
            chin_shade = max(0.0, (y / h - 0.78)) * 0.22
            total_shade = edge_shade + bangs_shade + chin_shade

            r = int(np.clip(252 * (1.0 - total_shade * 0.85), 200, 255))
            g = int(np.clip(242 * (1.0 - total_shade * 0.92), 180, 255))
            b = int(np.clip(236 * (1.0 - total_shade * 0.88), 170, 255))
            img.putpixel((x, y), (r, g, b, 255))

    # 2. Rosy Blush on Cheeks
    blush = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    b_draw = ImageDraw.Draw(blush)
    rx, ry = int(w * 0.13), int(h * 0.07)
    b_draw.ellipse([int(w * 0.28 - rx), int(h * 0.62 - ry), int(w * 0.28 + rx), int(h * 0.62 + ry)], fill=(255, 145, 155, 95))
    b_draw.ellipse([int(w * 0.72 - rx), int(h * 0.62 - ry), int(w * 0.72 + rx), int(h * 0.62 + ry)], fill=(255, 145, 155, 95))
    blush = blush.filter(ImageFilter.GaussianBlur(radius=max(1, int(w * 0.04))))
    img = Image.alpha_composite(img, blush)
    draw = ImageDraw.Draw(img)

    # 3. Captivating Anime Eyes
    def draw_eye(cx, cy, ew, eh, flip=False):
        # Sclera
        eye_box = [cx - ew / 2, cy - eh / 2, cx + ew / 2, cy + eh / 2]
        draw.ellipse(eye_box, fill=(248, 248, 254, 255))
        # Soft upper sclera shadow
        draw.chord(eye_box, start=180, end=360, fill=(220, 222, 238, 255))

        # Iris: Multi-tone gradient from deep amethyst violet to brilliant cyan
        iw = ew * 0.74
        ih = eh * 0.96
        for step in range(int(ih)):
            t = step / ih
            if t < 0.45:
                sub_t = t / 0.45
                r = int(35 + sub_t * 65)
                g = int(18 + sub_t * 22)
                b = int(65 + sub_t * 85)
            else:
                sub_t = (t - 0.45) / 0.55
                r = int(100 - sub_t * 65)
                g = int(40 + sub_t * 160)
                b = int(150 + sub_t * 90)
            angle = np.arcsin(np.clip((step - ih / 2) / (ih / 2), -1.0, 1.0))
            sw = iw * np.cos(angle)
            sy = cy - ih / 2 + step
            draw.line([(cx - sw / 2, sy), (cx + sw / 2, sy)], fill=(r, g, b, 255), width=1)

        # Deep Violet Pupil & Embedded Celestial Star Rune
        pw, ph = iw * 0.35, ih * 0.45
        draw.ellipse([cx - pw / 2, cy - ph / 2 - ih * 0.05, cx + pw / 2, cy + ph / 2 - ih * 0.05], fill=(22, 8, 42, 255))
        # Glowing cyan star cross in pupil
        draw.line([(cx - iw * 0.22, cy + ih * 0.12), (cx + iw * 0.22, cy + ih * 0.12)], fill=(190, 248, 255, 230), width=1)
        draw.line([(cx, cy + ih * 0.12 - iw * 0.22), (cx, cy + ih * 0.12 + iw * 0.22)], fill=(190, 248, 255, 230), width=1)

        # Thick, glamorous Anime Eyelashes & Eyeliner
        lash_col = (32, 20, 48, 255)
        y_arch = cy - eh / 2
        arch_pts = [
            (cx - ew * 0.55, y_arch + eh * 0.35),
            (cx - ew * 0.28, y_arch - eh * 0.06),
            (cx, y_arch - eh * 0.12),
            (cx + ew * 0.28, y_arch - eh * 0.06),
            (cx + ew * 0.60 if not flip else cx + ew * 0.52, y_arch + (eh * 0.18 if not flip else eh * 0.35))
        ]
        if flip:
            arch_pts[0] = (cx - ew * 0.60, y_arch + eh * 0.18)
        for i in range(len(arch_pts) - 1):
            draw.line([arch_pts[i], arch_pts[i + 1]], fill=lash_col, width=max(2, int(eh * 0.16)))

        # Wingtip eyeliner
        if not flip:
            draw.polygon([(cx + ew * 0.42, y_arch), (cx + ew * 0.70, y_arch - eh * 0.14), (cx + ew * 0.52, y_arch + eh * 0.18)], fill=lash_col)
        else:
            draw.polygon([(cx - ew * 0.42, y_arch), (cx - ew * 0.70, y_arch - eh * 0.14), (cx - ew * 0.52, y_arch + eh * 0.18)], fill=lash_col)

        # Soft lower eyelid line
        draw.arc([cx - ew * 0.32, cy + eh * 0.22, cx + ew * 0.32, cy + eh * 0.52], start=20, end=160, fill=(85, 55, 95, 190), width=max(1, int(eh * 0.06)))

        # Sparkling Pure White Specular Highlights
        hl1_x = cx - iw * 0.22 if not flip else cx - iw * 0.14
        hl1_y = cy - ih * 0.22
        draw.ellipse([hl1_x - ew * 0.11, hl1_y - eh * 0.13, hl1_x + ew * 0.11, hl1_y + eh * 0.13], fill=(255, 255, 255, 255))
        hl2_x = cx + iw * 0.18 if not flip else cx + iw * 0.22
        hl2_y = cy + ih * 0.18
        draw.ellipse([hl2_x - ew * 0.06, hl2_y - eh * 0.07, hl2_x + ew * 0.06, hl2_y + eh * 0.07], fill=(225, 245, 255, 220))

    # In texture tile: left is character's left eye (+X), right is character's right eye (-X)
    draw_eye(w * 0.31, h * 0.46, w * 0.25, h * 0.28, flip=False)
    draw_eye(w * 0.69, h * 0.46, w * 0.25, h * 0.28, flip=True)

    # 4. Graceful Arched Violet Eyebrows
    brow_col = (55, 35, 70, 240)
    draw.arc([w * 0.18, h * 0.23, w * 0.44, h * 0.37], start=200, end=350, fill=brow_col, width=max(2, int(h * 0.038)))
    draw.arc([w * 0.56, h * 0.23, w * 0.82, h * 0.37], start=190, end=340, fill=brow_col, width=max(2, int(h * 0.038)))

    # 5. Dainty Shaded Nose
    draw.ellipse([w * 0.48, h * 0.64, w * 0.52, h * 0.67], fill=(222, 172, 162, 220))
    draw.point((int(w * 0.49), int(h * 0.63)), fill=(255, 255, 255, 230))

    # 6. Alluring Rosy Lips with Cupid's Bow & Gloss Shine
    lip_box = [w * 0.43, h * 0.76, w * 0.57, h * 0.84]
    draw.ellipse(lip_box, fill=(222, 112, 128, 230))
    draw.line([(w * 0.44, h * 0.77), (w * 0.48, h * 0.78), (w * 0.50, h * 0.77), (w * 0.52, h * 0.78), (w * 0.56, h * 0.77)],
              fill=(165, 68, 88, 255), width=max(1, int(h * 0.022)))
    # Lip gloss shine
    draw.ellipse([w * 0.47, h * 0.80, w * 0.53, h * 0.83], fill=(255, 205, 215, 210))

    return img

def enhance_atlas_with_sorceress_features(atlas, uvs, forge):
    """Enhances the generated atlas with custom-painted anime face and textures."""
    atlas_rgba = atlas.convert('RGBA')

    # Find the sorceress head base part index
    for index, part in enumerate(forge.parts):
        if part['label'] == 'sorceress_head_base':
            # Front face is 'north'
            key = (index, 'north')
            if key in uvs:
                u0, v0, u1, v1 = [int(round(v)) for v in uvs[key]]
                w = u1 - u0
                h = v1 - v0
                if w > 4 and h > 4:
                    face_tile = Image.new('RGBA', (w, h), (252, 240, 232, 255))
                    face_tile = paint_anime_face(face_tile, w, h)
                    atlas_rgba.paste(face_tile, (u0, v0), face_tile)
                    print(f"Applied custom anime sorceress face at [{u0}, {v0}, {u1}, {v1}] (size {w}x{h})")

    return atlas_rgba

# ---------------------------------------------------------------------------
# Animation Retargeting from Backup Rig
# ---------------------------------------------------------------------------
def load_and_retarget_animations(bak_path, new_model_bones):
    """Retargets every animation clip from the backup model to new bone UUIDs by bone name."""
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
                new_animator = copy.deepcopy(animator)
                new_animators[target_uuid] = new_animator
        c['animators'] = new_animators
        retargeted_clips.append(c)

    return retargeted_clips

# ---------------------------------------------------------------------------
# Master Build
# ---------------------------------------------------------------------------
def build(identifier='arkhaia_malefeasance_phase2'):
    forge = sculpt_malefeasance()
    meta = dict(
        creation_tool='evergarden-forge',
        author='Evergarden Procedural Forge',
        model_identifier=identifier,
        revision=REVISION,
        scale=SCALE
    )

    # 1. Sculpt Model & Atlas with high texel density (8.5 px/unit for crisp detail)
    model, atlas, stats = build_model(
        forge, [], identifier, identifier,
        hitbox=([-5, 0, -3], [5, 36, 3]), meta=meta, size=2048, density=8.5
    )

    # 2. Extract UV map from forge packing
    _, uvs, _ = paint_atlas(forge, size=2048, density=8.5)

    # 3. Enhance Atlas with exquisite Anime Face
    atlas = enhance_atlas_with_sorceress_features(atlas, uvs, forge)

    # 4. Re-encode the enhanced atlas as base64 png
    import io, base64
    buf = io.BytesIO()
    atlas.save(buf, format='PNG')
    png_bytes = buf.getvalue()
    b64_str = 'data:image/png;base64,' + base64.b64encode(png_bytes).decode('ascii')
    model['textures'][0]['source'] = b64_str

    # 5. Load and retarget all 17 authored animation clips from the backup model
    bak_path = ROOT / 'evergarden/src/main/resources/modelengine/rivers/arkhaia_malefeasance.bbmodel.bak'
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
    parser.add_argument('--id', type=str, default='arkhaia_malefeasance_phase2')
    args = parser.parse_args()

    model, atlas, stats = build(args.id)
    args.out.mkdir(parents=True, exist_ok=True)
    target = args.out / f'{args.id}.bbmodel'

    with open(target, 'w', encoding='utf-8') as f:
        json.dump(model, f, indent=2)

    print(f"Generated Remastered Sorceress: {target}")
    print(f"Stats: {len(model['elements'])} cubes, {len(model.get('animations', []))} clips, {stats['coverage']*100:.1f}% atlas coverage at {stats['density']} px/unit")

if __name__ == '__main__':
    main()
