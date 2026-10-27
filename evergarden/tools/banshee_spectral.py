"""Banshee Wail, the Mourning Siren: Remastered Spectral Queen ModelEngine R4 Blueprint.

An ethereal, terrifying hovering spectral queen sculpted, painted, and rigged using
Evergarden's high-fidelity procedural pipeline:
- Persona: Mourning Siren & Queen of Wailing Souls, hovering ethereally above the ground.
- Head & Mask: Pale weeping porcelain sorrow mask with black tear streaks,
  open screaming wail mouth emitting sonic shockwaves, and radiating soul crystal tiara.
- Spectral Halo: Majestic levitating soul arc behind head and upper back,
  etched with orbiting wailing spirit wisps and glowing cyan soul flames.
- Mourning Veils: 6 flowing mourning veil ribbons attached under collar,
  cascading down to the mist below.
- Spectral Ghost Tail: Seamless flowing phantom shroud replacing solid legs,
  tapering into swirling soul mist.
- Slender Phantom Arms: Elongated ethereal arms with clawed curse fingers and silk drapes.
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
REVISION = 'banshee-spectral-v2'

# ---------------------------------------------------------------------------
# Color Palettes & Materials
# ---------------------------------------------------------------------------
SPECTRAL_TEAL = ['#041a24', '#093345', '#11526d', '#1b789d', '#2aa7d8', '#5cd0ff', '#a6ebff', '#ffffff']
MOURNING_SILK = ['#0c0b17', '#16142a', '#221e40', '#322c5c', '#473e80', '#6458ad', '#887bd9', '#b6aef7']
IVORY_MASK = ['#15181d', '#232830', '#363d4a', '#4f596b', '#707d94', '#9caac4', '#cbd6ed', '#f0f5ff']
SOUL_SILVER = ['#11161d', '#1d2430', '#2f3a4d', '#475773', '#677d9f', '#92a9cf', '#c8d8f5', '#ffffff']
GHOST_TAIL = ['#051820', '#0c323f', '#165468', '#24819d', '#3db2d4', '#70dbfa', '#bbf2ff', '#ffffff']
EYE_CYAN = ['#2ef0ff', '#88f7ff', '#d6fbff', '#ffffff']

MATERIALS = {
    'silk': Material(MOURNING_SILK, 'cloth', trim=SOUL_SILVER, accent=SPECTRAL_TEAL, density=0.85),
    'mask': Material(IVORY_MASK, 'gem', density=1.0),
    'silver': Material(SOUL_SILVER, 'trim', density=1.0),
    'soul': Material(SPECTRAL_TEAL, 'plate', accent=EYE_CYAN, density=0.9),
    'tail': Material(GHOST_TAIL, 'cloth', accent=EYE_CYAN, density=0.85),
    'glow': Material(SPECTRAL_TEAL, 'glow', glow=True, density=1.5),
    'eye': Material(EYE_CYAN, 'glow', glow=True, density=1.6),
    'runes': Material(SPECTRAL_TEAL, 'runes', glow=True, density=1.4),
    'halo': Material(GHOST_TAIL, 'gem', glow=True, density=1.3),
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
# Head & Screaming Maw: Weeping Sorrow Mask & Soul Tiara
# ---------------------------------------------------------------------------
def head_mask(f):
    b = 'head'
    # Pale Weeping Porcelain Cranium
    f.box(b, (-2.5, 32.5, -2.5), (5.0, 5.8, 5.0), 'mask', label='cranium')

    # Chiseled Sorrow Faceplate with Sunken Eye Cavities
    f.box(b, (-2.3, 32.2, -3.2), (4.6, 5.6, 0.8), 'mask', label='faceplate')

    # Piercing Glowing Soul Eye Slits with Black Weeping Tears
    for side, s in SIDES:
        # Sunken eye socket
        f.box(b, (s * 1.1 - 0.55, 34.6, -3.3), (1.1, 0.55, 0.3), 'silk', label='eye_socket')
        # Piercing cyan soul glow
        f.cube(b, (s * 1.1, 34.8, -3.35), (1.0, 0.3, 0.2), 'eye', rot((0, 0, -s * 12)), label='eye_glow')
        # Weeping tear streaks streaming down the face
        f.beam(b, (s * 1.1, 34.6, -3.32), (s * 1.0, 31.8, -3.32), 0.22, 0.15, 'silk', label='weeping_tear')
        f.beam(b, (s * 1.0, 31.8, -3.32), (s * 0.9, 29.5, -3.32), 0.18, 0.12, 'glow', label='tear_glow')

    # The Horrifying Screaming Maw (Open mouth shrieking sonic wails)
    f.box(b, (-1.2, 32.0, -3.35), (2.4, 1.4, 0.6), 'silk', label='screaming_mouth_cavity')
    f.cube(b, (0, 32.7, -3.45), (1.8, 0.35, 0.2), 'silver', label='upper_teeth')
    f.cube(b, (0, 32.1, -3.45), (1.6, 0.35, 0.2), 'silver', label='lower_teeth')
    # Inner sonic shockwave portal inside the mouth
    f.cube(b, (0, 32.4, -3.2), (1.4, 0.8, 0.3), 'glow', label='mouth_sonic_core')

    # Radiating Weeping Soul Tiara / Crystalline Ghost Crown
    f.box(b, (-2.7, 37.0, -2.7), (5.4, 1.2, 5.4), 'silver', label='crown_band')
    # Front soul crystal gem
    f.cube(b, (0, 37.8, -3.0), (1.4, 1.4, 0.5), 'halo', rot((0, 0, 45)), label='crown_gem')
    f.cube(b, (0, 37.8, -3.15), (0.9, 0.9, 0.35), 'glow', rot((0, 0, 45)), label='crown_gem_glow')

    # Radiating High Spectral Spired Tiara
    f.spike(b, (0, 38.0, -2.6), (0, 46.0, -3.0), 1.2, 'halo', steps=4, label='crown_spire_center')
    for side, s in SIDES:
        f.spike(b, (s * 1.4, 37.8, -2.4), (s * 2.2, 43.5, -2.6), 0.95, 'halo', steps=3, label='crown_spire_mid')
        f.spike(b, (s * 2.5, 37.5, -2.0), (s * 3.8, 41.0, -2.2), 0.8, 'halo', steps=3, label='crown_spire_side')
        # Weeping silk ribbons flowing back from hair
        f.spike(b, (s * 2.6, 36.5, 2.2), (s * 4.5, 26.0, 3.8), 1.1, 'silk', steps=4, label='hair_ribbon')

# ---------------------------------------------------------------------------
# Spectral Halo: The Levitating Arc of Wailing Souls (halo bone)
# ---------------------------------------------------------------------------
def spectral_halo(f):
    h = 'halo'
    # Great circular ethereal ring of wailing spirits hovering behind upper back
    # Origin of halo is [0, 35, 3]
    f.ring(h, (0, 35.0, 3.2), 6.5, 0.7, 0.7, 'halo', segments=20, normal=(0, 0, 1), label='soul_halo_ring')
    f.ring(h, (0, 35.0, 3.35), 6.2, 0.35, 0.35, 'glow', segments=20, normal=(0, 0, 1), label='soul_halo_glow')

    # 6 Orbiting Wailing Soul Wisps mounted symmetrically along the ring
    for i in range(6):
        angle = i * (math.tau / 6)
        cx = math.cos(angle) * 6.5
        cy = 35.0 + math.sin(angle) * 6.5
        # Soul wisp core
        f.cube(h, (cx, cy, 3.2), (1.4, 1.4, 0.8), 'glow', rot((0, 0, math.degrees(angle))), label=f'soul_wisp_{i}')
        # Spiked trailing spirit flame
        f.spike(h, (cx, cy, 3.2), (cx + math.cos(angle + 0.4) * 2.2, cy + math.sin(angle + 0.4) * 2.2, 3.2),
                0.8, 'halo', steps=2, label=f'soul_flame_{i}')

# ---------------------------------------------------------------------------
# Mourning Veils: 6 Flowing Ethereal Ribbons (veil_0 to veil_5)
# ---------------------------------------------------------------------------
def mourning_veils(f):
    # Attached under collar at y=29, cascading down to y=2
    # veils are spaced along x: [-3.625, -2.175, -0.725, 0.725, 2.175, 3.625]
    for i in range(6):
        vb = f'veil_{i}'
        vx = J[f'veil_{i}'][0]
        # Main flowing veil ribbon
        f.beam(vb, (vx, 29.0, 3.3), (vx * 1.15, 15.0, 3.8), 1.6, 0.3, 'silk', label=f'veil_upper_{i}')
        f.spike(vb, (vx * 1.15, 15.0, 3.8), (vx * 1.3, 2.0, 4.4), 1.5, 'silk', steps=4, depth_ratio=0.3, label=f'veil_lower_{i}')
        # Glowing spectral border along outer edge
        f.beam(vb, (vx + 0.65, 28.5, 3.4), (vx * 1.15 + 0.65, 14.5, 3.9), 0.22, 0.25, 'glow', label=f'veil_trim_{i}')

# ---------------------------------------------------------------------------
# Torso & Bodice: Ghostly Mourning Bodice & Weeping Soul Core
# ---------------------------------------------------------------------------
def torso(f):
    c = 'chest'
    # Continuous slender ethereal bodice from y=14.0 to y=32.5 (Zero gaps!)
    tiers = [
        (14.0, 16.8, 6.8, 5.0, 0.1),   # Waist / Corset Base (seamlessly merges with ghost tail)
        (16.8, 19.8, 7.2, 5.2, 0.15),  # Lower Bodice
        (19.8, 23.0, 8.2, 5.6, 0.2),   # Mid Bodice / Obliques
        (23.0, 26.2, 9.4, 6.0, 0.25),  # Upper Bodice / Solar Plexus
        (26.2, 29.5, 10.8, 6.4, 0.3),  # Pectorals / Bust
        (29.5, 32.5, 11.8, 6.6, 0.4)   # Clavicle / Neck line
    ]
    for i, (y0, y1, w, d, zc) in enumerate(tiers):
        f.box(c, (-w / 2, y0, zc - d / 2), (w, y1 - y0, d), 'silk',
              label='bodice_tier', engrave='lines' if i >= 3 else None)

    # Silver Filigree Corset Ribs (Continuous from y=14.5 to y=24.5)
    for i, (y_pos, w) in enumerate([(14.5, 6.2), (16.4, 6.6), (18.3, 7.2), (20.2, 7.8), (22.2, 8.4), (24.0, 9.0)]):
        f.cube(c, (0, y_pos + 0.4, -2.4 - i * 0.08), (w, 0.7, 0.6), 'silk', rot((-6, 0, 0)), label=f'corset_rib_{i}')
        f.cube(c, (0, y_pos + 0.1, -2.6 - i * 0.08), (w + 0.2, 0.2, 0.4), 'silver', rot((-6, 0, 0)), label=f'corset_trim_{i}')

    # Flank Silk Drapes (Covering sides between y=14.2 and y=22.5)
    for side, s in SIDES:
        f.cube(c, (s * 3.7, 17.5, 0.1), (0.8, 7.0, 4.8), 'silk', rot((0, 0, -s * 6)), label='flank_silk')
        f.cube(c, (s * 3.9, 17.5, 0.1), (0.6, 6.8, 5.0), 'silver', rot((0, 0, -s * 6)), label='flank_silver')

    # Central Weeping Soul Core (Hanging teardrop soul gem)
    # Silver thorn chains
    f.beam(c, (-2.0, 31.0, -2.8), (0, 27.2, -3.2), 0.4, 0.35, 'silver', label='core_chain_l')
    f.beam(c, (2.0, 31.0, -2.8), (0, 27.2, -3.2), 0.4, 0.35, 'silver', label='core_chain_r')
    # Levitating Teardrop Soul Gem
    f.cube(c, (0, 26.8, -3.4), (1.8, 2.4, 0.6), 'soul', rot((0, 0, 45)), label='soul_core_gem')
    f.cube(c, (0, 26.8, -3.55), (1.2, 1.6, 0.4), 'glow', rot((0, 0, 45)), label='soul_core_glow')
    f.spike(c, (0, 25.2, -3.4), (0, 23.2, -3.5), 0.7, 'halo', steps=2, label='soul_core_drop')

    # Ghostly Back Silk & Spinal Column (y=14.0 to y=32.0)
    f.box(c, (-4.5, 14.0, 3.0), (9.0, 17.5, 1.0), 'silk', label='back_silk')
    f.beam(c, (0, 31.5, 3.8), (0, 14.2, 3.6), 0.9, 0.7, 'silver', label='spine_beam')
    for y, h in ((15.0, 1.0), (17.5, 1.2), (20.0, 1.4), (22.5, 1.6), (25.0, 1.8), (27.5, 2.0), (30.0, 1.6)):
        f.spike(c, (0, y, 3.8), (0, y + 0.3, 3.8 + h), 0.75, 'halo', steps=2, label=f'spine_crystal_{int(y)}')

# ---------------------------------------------------------------------------
# Slender Phantom Arms & Spectral Claws
# ---------------------------------------------------------------------------
def arms_claws(f):
    for side, s in SIDES:
        # Arm (Bicep)
        ab = f'arm_{side}'
        f.box(ab, (s * 7.7 - (1.3 if s > 0 else 1.5), 22.0, -1.5), (2.8, 6.0, 3.0), 'silk', label=f'arm_silk_{side}')
        f.cube(ab, (s * 7.7, 24.5, -1.6), (3.0, 0.6, 3.4), 'silver', label=f'arm_trim_{side}')

        # Forearm
        fab = f'forearm_{side}'
        f.box(fab, (s * 10.0 - (1.3 if s > 0 else 1.5), 18.0, -2.5), (2.6, 6.5, 3.0), 'silk', label=f'forearm_silk_{side}')
        # Flared Elbow Mourning Silk Drape (Swaying ethereal sleeve)
        f.spike(fab, (s * 10.0, 23.5, 0.8), (s * 12.5, 16.5, 2.5), 1.2, 'silk', steps=3, label=f'elbow_drape_{side}')

        # Hand & Spectral Curse Claws
        hb = f'hand_{side}'
        # Slender palm
        f.box(hb, (s * 12.0 - (1.1 if s > 0 else 1.3), 18.5, -4.8), (2.2, 2.8, 2.2), 'mask', label=f'palm_{side}')
        # 4 Elongated Spectral Claws (Channelling curses & shrieks)
        for i, kx in enumerate([-0.6, -0.2, 0.2, 0.6]):
            claw_base = np.array([s * 12.0 + kx, 18.5, -4.8])
            claw_tip  = np.array([s * 12.0 + kx * 1.4, 13.5, -5.8])
            f.spike(hb, claw_base, claw_tip, 0.45, 'halo', steps=3, depth_ratio=0.6, label=f'claw_{i}_{side}')

# ---------------------------------------------------------------------------
# Spectral Ghost Tail (pelvis, sash_r, sash_l): Replacing Solid Legs
# ---------------------------------------------------------------------------
def spectral_ghost_tail(f):
    """Sculpts the twisting, ethereal spectral ghost tail replacing solid legs."""
    p = 'pelvis'
    # Solid Pelvis Waist Chassis (Connecting seamlessly into lower chest at y=14.0 to y=18.5)
    f.box(p, (-3.8, 11.5, -2.6), (7.6, 5.2, 5.2), 'silk', label='pelvis_core')
    # Inner Waist Armature (Inserts into chest)
    f.box(p, (-3.4, 14.5, -2.4), (6.8, 4.0, 4.8), 'silk', label='pelvis_upper_joint')

    # Mourning Corset Belt
    f.box(p, (-4.2, 14.2, -2.8), (8.4, 2.4, 5.6), 'silk', label='corset_belt')
    f.cube(p, (0, 15.4, -3.0), (2.2, 1.8, 0.7), 'silver', label='belt_buckle')
    f.cube(p, (0, 15.4, -3.15), (1.2, 0.8, 0.3), 'glow', label='buckle_glow')

    # Central Ghost Tail Swell (Curving backwards and tapering into soul mist)
    # The tail extends from pelvis (y=12.0) down to y=0.5!
    tail_pts = [
        np.array([0.0, 13.0, 0.0]),
        np.array([0.0, 9.5, 0.8]),
        np.array([0.0, 5.5, 2.0]),
        np.array([0.0, 1.5, 3.2]),
        np.array([0.0, -0.5, 4.2])
    ]
    f.taper(p, tail_pts, [6.5, 5.2, 3.8, 2.2], [5.5, 4.4, 3.0, 1.6], 'tail', z_hint=(0, 0, 1), label='ghost_tail_core')

    # Glowing Spectral Soul Streams radiating down along the tail
    f.spike(p, (0, 11.0, -2.2), (0, 2.0, 0.5), 1.4, 'tail', steps=4, depth_ratio=0.4, label='front_tail_drape')
    f.beam(p, (0, 11.5, -2.4), (0, 3.0, 0.2), 0.35, 0.3, 'glow', label='front_tail_stream')

    # Side Floating Shrouds (sash_r and sash_l)
    for side, s in SIDES:
        sb = f'sash_{side}'
        # Ethereal hip fins cascading down
        shroud_pts = [
            np.array([s * 3.8, 14.5, 0.0]),
            np.array([s * 4.8, 9.5, 1.0]),
            np.array([s * 4.2, 4.0, 2.2]),
            np.array([s * 3.0, 0.5, 3.5])
        ]
        f.taper(sb, shroud_pts, [1.4, 1.2, 0.8], [3.8, 3.2, 2.0], 'tail', z_hint=(0, 0, 1), label=f'side_shroud_{side}')
        # Glowing soul fringe
        f.beam(sb, shroud_pts[0] + np.array([s * 0.4, 0, 0]), shroud_pts[2] + np.array([s * 0.4, 0, 0]),
               0.25, 0.25, 'glow', label=f'side_shroud_glow_{side}')

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
    spectral_ghost_tail(f)
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
    target = args.out / 'arkhaia_banshee_wail.bbmodel'
    target.write_text(dump(model) + '\n', encoding='utf8')

    if SERVA_OUT.exists():
        (SERVA_OUT / 'arkhaia_banshee_wail.bbmodel').write_text(dump(model) + '\n', encoding='utf8')

    art = ROOT / 'evergarden/art/arkhaia-rivers'
    art.mkdir(parents=True, exist_ok=True)
    atlas.save(art / 'banshee_spectral.png')

    print(f"Banshee Wail Remastered: {len(model['elements']) - 1} cubes, {len(sculpt().bones)} bones, "
          f"{len(model['animations'])} clips, atlas {stats['coverage']:.0%} coverage at {stats['density']:.1f}px/unit")

if __name__ == '__main__':
    main()
