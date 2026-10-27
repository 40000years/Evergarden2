"""Phlegethon, the Deep-Core Excavator: Remastered Badlands Mining Rig Golem ModelEngine R4 Blueprint.

A colossal industrial geothermal excavation golem sculpted, painted, and rigged using
Evergarden's high-fidelity procedural pipeline:
- Distinct identity: Ancient Badlands deep-core excavation golem, Badlands terracotta bedrock,
  rusted cast iron chassis, brushed steam-pipe brass, and molten magma/soul-fire furnace core.
- Industrial diving-rig helm with protective visor cage, tri-optic geothermal sensors, crane shackles, and steam dome.
- Great circular geothermal blast furnace boiler with 12-tooth outer brass gear bezel and 3D cast-iron cage grates.
- Twin heavy industrial steam exhaust chimneys on shoulders venting hot smoke & embers.
- Heavy excavator arms with dual hydraulic piston cylinders, outer pile-driver pressure chambers, and brass piping.
- Colossal rock-crusher pile-driver fists with tungsten-carbide chisel teeth and glowing thermal coils.
- Articulated ball-turret waist joint with dual hydraulic tilt cylinders bridging the torso seamlessly.
- Trench-treader quarry boots with serrated rock-biting cleats and glowing toe radiators.
- 100% preservation of all 16 authored combat animations from arkhaia_phlegethon.bbmodel.bak:
  idle, walk, hover, cast (slam), attack (punch), stun, kick, and throw (rock throw).
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
SCALE = 3.0
REVISION = 'phlegethon-excavator-v2'

# ---------------------------------------------------------------------------
# Color Palettes & Materials
# ---------------------------------------------------------------------------
BADLANDS_STONE = ['#26120e', '#3d1c15', '#5c2a1e', '#823b29', '#a94f36', '#cc6748', '#e88563', '#ffa88a']
CAST_IRON = ['#0a0b0e', '#121419', '#1c1e26', '#282b35', '#373b49', '#4b5062', '#646a80', '#838b9f']
BRASS_PIPING = ['#1a1006', '#2b1b0b', '#442b12', '#69421c', '#986029', '#c98239', '#e8a855', '#f8cca0', '#fff2e0']
MAGMA_CORE = ['#1a0600', '#3b0d00', '#6e1900', '#b32c00', '#ff4d00', '#ff8533', '#ffc266', '#ffffff']
SOUL_FIRE = ['#04222f', '#09435b', '#0f6e94', '#179ecd', '#32cdff', '#85e5ff', '#ffffff']
DRILL_STEEL = ['#14171e', '#20242e', '#2e3442', '#40485a', '#576279', '#75839e', '#9caecb', '#cad9f0']
EXHAUST_FLAME = ['#1f0a00', '#4a1500', '#8c2b00', '#d94c00', '#ff7b1a', '#ffb366', '#ffffff']
SMOKE_SHEET = ['#1c1a20', '#2d2a33', '#433f4a', '#5f5966', '#807987', '#a69fae', '#d0cad6']
RUNES_ORANGE = ['#1a0600', '#421100', '#7a2200', '#bf3d00', '#ff6b1a', '#ffa666', '#ffffff']

MATERIALS = {
    'terracotta': Material(BADLANDS_STONE, 'plate', accent=MAGMA_CORE, density=0.9),
    'iron': Material(CAST_IRON, 'plate', density=0.85),
    'brass': Material(BRASS_PIPING, 'trim', density=1.0),
    'core': Material(MAGMA_CORE, 'glow', glow=True, density=1.4),
    'soul': Material(SOUL_FIRE, 'glow', glow=True, density=1.5),
    'steel': Material(DRILL_STEEL, 'blade', density=1.0),
    'fire': Material(EXHAUST_FLAME, 'flame', glow=True, density=0.8),
    'smoke': Material(SMOKE_SHEET, 'flame', glow=False, density=0.85),
    'runes': Material(RUNES_ORANGE, 'runes', glow=True, density=1.4),
    'eye': Material(['#32cdff', '#85e5ff', '#ffffff'], 'glow', glow=True, density=1.6),
}

# Coordinate mapping matching original model:
# _r has negative X, _l has positive X
SIDES = (('r', -1), ('l', 1))

# ---------------------------------------------------------------------------
# Exact Joint Pivots from original rig
# ---------------------------------------------------------------------------
def joints():
    j = {
        'pelvis': (0, 15, 0),
        'chest': (0, 24, 0),
        'neck': (0, 29, 0),
        'hi_gaze': (0, 31.5, 0),
        'head': (0, 31.5, 0),
        'jaw': (0, 31.0, -2.5),
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
            f'shoulder_{side}': (s * 9.6, 28, 0),
            f'arm_{side}': (s * 9.6, 28, 0),
            f'elbow_{side}': (s * 11.0, 23, -1),
            f'wrist_{side}': (s * 11.0, 17, -4),
            f'flame_{side}': (s * 9.6, 38.5, 0),
            f'hip_{side}': (s * 2.8, 15, 0),
            f'knee_{side}': (s * 2.8, 8, 0),
            f'ankle_{side}': (s * 2.8, 1.5, 0),
            f'sash_{side}': (s * 3.8, 15, 0),
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
    f.bone('head_forward', 'head', [0, 31.5, -7])
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
# Head & Helm: Industrial Diving / Excavation Rig Visor
# ---------------------------------------------------------------------------
def helm(f):
    b = 'head'
    # Heavy hexagonal iron helmet cranium
    f.box(b, (-3.2, 30.5, -3.2), (6.4, 5.8, 6.4), 'iron', label='helmet_hull', engrave='lines')
    # Badlands mesa terracotta temple armor plates
    for side, s in SIDES:
        f.cube(b, (s * 3.2, 33.2, 0.0), (0.7, 4.4, 5.4), 'terracotta', rot((0, 0, 0)), label='temple_plate')
        # Crane lifting shackle lugs on temples
        f.cube(b, (s * 3.6, 35.8, 0.0), (0.7, 1.8, 2.2), 'iron', rot((0, 0, 0)), label='shackle_lug')
        f.cube(b, (s * 3.65, 36.5, 0.0), (0.8, 0.8, 1.2), 'brass', rot((0, 0, 0)), label='shackle_pin')

    # Top boiler dome & brass pressure cap
    f.box(b, (-2.5, 36.3, -2.5), (5.0, 1.2, 5.0), 'iron', label='boiler_dome')
    f.beam(b, (0, 36.8, 0), (0, 38.6, 0), 1.6, 1.6, 'brass', label='steam_stack')
    f.cube(b, (0, 38.4, 0), (2.2, 0.45, 2.2), 'iron', rot((0, 0, 0)), label='stack_rim')

    # Heavy Protective Visor Opening Recess
    f.box(b, (-2.6, 32.2, -3.5), (5.2, 2.8, 0.6), 'iron', label='visor_recess', plain=True)

    # Tri-Optic Geothermal Sensors (3 glowing horizontal sensors)
    f.cube(b, (0, 33.5, -3.65), (1.5, 0.6, 0.2), 'eye', rot((0, 0, 0)), label='eye_center')
    for side, s in SIDES:
        f.cube(b, (s * 1.8, 33.5, -3.6), (1.1, 0.6, 0.2), 'eye', rot((0, 0, 0)), label='eye_side')

    # Cast-Iron Protective Visor Cage Bars
    for x in (-2.0, -0.7, 0.7, 2.0):
        f.beam(b, (x, 35.0, -3.75), (x, 32.0, -3.75), 0.45, 0.4, 'iron', label='visor_bar')

    # Heavy Brass Brow & Chin Bumpers
    f.beam(b, (-2.8, 35.1, -3.65), (2.8, 35.1, -3.65), 0.65, 0.65, 'brass', label='visor_brow')
    f.beam(b, (-2.8, 31.9, -3.65), (2.8, 31.9, -3.65), 0.65, 0.65, 'brass', label='visor_chin')

    # Jaw Bone (`jaw`)
    jb = 'jaw'
    f.box(jb, (-2.8, 28.6, -3.0), (5.6, 2.4, 4.2), 'iron', label='jaw_hull')
    # Intake grill on jaw
    f.box(jb, (-1.8, 29.0, -3.2), (3.6, 1.6, 0.5), 'terracotta', label='jaw_grill', plain=True)
    for x in (-1.0, 0, 1.0):
        f.beam(jb, (x, 30.5, -3.3), (x, 29.1, -3.3), 0.35, 0.3, 'iron', label='jaw_intake_bar')

    # Dual brass hydraulic jaw pistons
    for side, s in SIDES:
        f.beam(b, (s * 3.4, 33.5, -1.0), (s * 3.0, 29.5, -2.2), 0.65, 0.65, 'brass', label='jaw_piston')
        # Side pressure gauge
        f.cube(b, (s * 3.4, 33.5, 0.8), (0.6, 1.8, 1.8), 'brass', rot((0, 0, 0)), label='pressure_gauge')
        f.cube(b, (s * 3.75, 33.5, 0.8), (0.2, 1.2, 1.2), 'core', rot((0, 0, 0)), label='gauge_needle')

# ---------------------------------------------------------------------------
# Torso & The Great Geothermal Blast Furnace
# ---------------------------------------------------------------------------
def torso(f):
    c = 'chest'
    # Neck swivel joint
    f.box('neck', (-2.2, 28.5, -2.2), (4.4, 2.5, 4.4), 'iron', label='neck_hull')
    f.box('neck', (-2.5, 29.2, -2.5), (5.0, 0.8, 5.0), 'brass', label='neck_ring')

    # Multi-layered Badlands Terracotta & Cast-Iron Torso
    tiers = (
        (21.0, 24.5, 13.0, 9.0, 0.0, 'terracotta'),
        (24.5, 29.0, 15.6, 10.4, 0.1, 'iron'),
        (29.0, 32.5, 17.2, 11.2, 0.2, 'terracotta'),
    )
    for y0, y1, w, d, zc, mat in tiers:
        f.box(c, (-w / 2, y0, zc - d / 2), (w, y1 - y0, d), mat, label='torso_layer', engrave='lines')

    # Abdomen lower overhang flange protecting the waist turret
    f.box(c, (-5.8, 20.2, -4.4), (11.6, 1.8, 8.8), 'iron', label='torso_skirt_flange')

    # Front Pectoral Terracotta Heavy Plating
    for side, s in SIDES:
        f.cube(c, (s * 4.8, 28.5, -5.2), (4.8, 4.2, 1.0), 'terracotta', rot((-4, -s * 10, -s * 3)), label='pectoral_plate')
        f.beam(c, (s * 2.4, 30.6, -5.3), (s * 7.2, 30.6, -5.0), 0.5, 0.45, 'brass', label='pectoral_trim')

    # THE GREAT CIRCULAR BLAST FURNACE BOILER (Center of Chest)
    center_y, center_z = 27.0, -5.4
    # Deep recessed firebox cavity
    f.box(c, (-4.2, center_y - 4.2, center_z), (8.4, 8.4, 1.2), 'iron', label='boiler_firebox', plain=True)
    # Molten volcanic magma & radiant soul-fire core
    f.box(c, (-3.8, center_y - 3.8, center_z + 0.35), (7.6, 7.6, 0.8), 'core', label='magma_core')
    f.cube(c, (0, center_y, center_z + 0.4), (5.2, 5.2, 0.6), 'soul', rot((0, 0, 45)), label='soul_core')
    f.cube(c, (0, center_y, center_z + 0.45), (3.2, 3.2, 0.4), 'eye', rot((0, 0, 45)), label='core_hotspot')

    # Outer Circular Brass Gear Bezel (12 polygon segments + radial teeth)
    radius = 4.4
    for i in range(12):
        a0 = i * math.tau / 12
        a1 = (i + 1) * math.tau / 12
        p0 = np.array([math.cos(a0) * radius, center_y + math.sin(a0) * radius, -5.6])
        p1 = np.array([math.cos(a1) * radius, center_y + math.sin(a1) * radius, -5.6])
        f.beam(c, p0, p1, 0.9, 0.7, 'brass', label='gear_bezel')
        mid = (p0 + p1) * 0.5
        # Gear tooth
        f.cube(c, mid + np.array([math.cos(a0 + math.pi / 12) * 0.45, math.sin(a0 + math.pi / 12) * 0.45, -0.1]),
               (0.7, 0.7, 0.4), 'brass', rot((0, 0, math.degrees(a0 + math.pi / 12))), label='gear_tooth')
        # Rivet stud
        f.cube(c, mid + np.array([0, 0, -0.15]), (0.4, 0.4, 0.3), 'steel', rot((0, 0, 0)), label='gear_rivet')

    # 7 Vertical Cast-Iron Furnace Grate Bars
    for x in (-3.0, -2.0, -1.0, 0, 1.0, 2.0, 3.0):
        y_span = math.sqrt(max(0.4, radius**2 - x**2)) * 0.92
        f.beam(c, (x, center_y - y_span, -5.85), (x, center_y + y_span, -5.85), 0.55, 0.55, 'iron', label='furnace_bar')

    # High-Pressure Brass Hydraulic Manifolds & Loops
    for side, s in SIDES:
        f.beam(c, (s * 4.6, 22.5, -4.6), (s * 7.6, 24.5, -3.8), 0.95, 0.95, 'brass', label='manifold_lo')
        f.beam(c, (s * 7.6, 24.5, -3.8), (s * 8.4, 28.5, -2.4), 0.9, 0.9, 'brass', label='manifold_mid')
        f.beam(c, (s * 8.4, 28.5, -2.4), (s * 9.4, 29.5, 0.0), 0.85, 0.85, 'brass', label='manifold_hi')

    # Abdomen Segmented Heat Radiator Grates
    for i, y in enumerate((23.8, 22.4, 21.0)):
        f.box(c, (-5.5, y, -4.7), (11.0, 0.9, 0.7), 'iron', label='radiator_grate')
        f.box(c, (-5.0, y + 0.15, -4.8), (10.0, 0.6, 0.3), 'brass', label='radiator_brass')

    # Back Heat Exchanger Fins & Boiler Exhaust Flues
    f.box(c, (-7.0, 22.0, 4.8), (14.0, 10.0, 1.6), 'iron', label='back_casing', engrave='scales')
    for x in (-4.8, -2.4, 0, 2.4, 4.8):
        f.box(c, (x - 0.3, 23.0, 5.8), (0.6, 8.5, 2.2), 'brass', label='radiator_fin')

# ---------------------------------------------------------------------------
# Shoulders & The Twin Industrial Blast Smokestacks
# ---------------------------------------------------------------------------
def mantle_and_pauldrons(f):
    col = 'collar'
    f.box(col, (-8.0, 28.5, -2.5), (16.0, 3.2, 5.5), 'iron', label='collar_frame')

    for side, s in SIDES:
        sh = f'shoulder_{side}'
        center = np.array([s * 10.2, 28.0, 0.0])

        # Excavator Crane Boom Pauldron (Heavy industrial quarry block)
        f.cube(sh, center, (7.6, 6.0, 8.8), 'iron', rot((0, 0, -s * 8)), label='pauldron_chassis')
        f.cube(sh, center + np.array([s * 0.8, 0.8, 0]), (6.8, 4.8, 8.0), 'terracotta', rot((0, 0, -s * 8)), label='pauldron_mesa')
        f.cube(sh, center + np.array([0, -2.6, 0]), (8.0, 0.9, 9.2), 'brass', rot((0, 0, -s * 8)), label='pauldron_ring')

        # The Twin Heavy Industrial Smokestack Chimneys
        chim_base = np.array([s * 10.2, 31.2, 0.0])
        # Flanged brass collar
        f.cube(sh, chim_base, (4.2, 1.0, 4.2), 'brass', rot((0, 0, 0)), label='chimney_collar')
        # Lower cylindrical smokestack
        f.beam(sh, chim_base + (0, 0.5, 0), chim_base + (0, 5.2, 0), 3.2, 3.2, 'iron', label='chimney_stack')
        # Brass reinforcement ring
        f.cube(sh, chim_base + (0, 3.2, 0), (3.6, 0.8, 3.6), 'brass', rot((0, 0, 0)), label='chimney_ring')
        # Flared exhaust rim
        f.beam(sh, chim_base + (0, 5.2, 0), chim_base + (0, 7.0, 0), 3.9, 3.9, 'iron', label='chimney_rim')
        # Glowing volcanic furnace flue
        f.cube(sh, chim_base + (0, 6.8, 0), (2.2, 0.4, 2.2), 'core', rot((0, 0, 0)), label='chimney_glow')

        # Secondary angled steam relief pipe
        f.beam(sh, chim_base + (-s * 1.8, 1.0, -1.8), chim_base + (-s * 2.8, 3.8, -2.6), 1.0, 1.0, 'brass', label='relief_pipe')

        # Exhaust smoke & ember plume sheets (flame_{side})
        fb = f'flame_{side}'
        top_vent = np.array([s * 10.2, 38.4, 0.0])
        for k, (dx, dz, h, w) in enumerate(((1, 0, 5.2, 3.0), (0, 1, 4.6, 2.6), (0.7, 0.7, 3.8, 2.0))):
            axis = unit(np.array([dx, 0, dz]))
            pts = []
            for i in range(7):
                u = i / 6
                x_off = (u - 0.5) * w
                tall = h * (0.6 + 0.4 * math.sin(math.pi * u)) if i % 2 == 0 else h * (0.35 + 0.2 * math.sin(math.pi * u))
                pts.append(top_vent + axis * x_off + np.array([0, tall, 0]))
            pts.append(top_vent + axis * w * 0.45 + np.array([0, -0.2, 0]))
            pts.append(top_vent + axis * (-w * 0.45) + np.array([0, -0.2, 0]))
            f.sheet(fb, pts[::-1], 'fire' if k == 0 else 'smoke', thickness=0.06, up=(0, 1, 0),
                    label='exhaust_plume', seed=800 + k + (side == 'l') * 30)

# ---------------------------------------------------------------------------
# Excavator Piston Arms
# ---------------------------------------------------------------------------
def arms(f):
    for side, s in SIDES:
        sh = J[f'shoulder_{side}']
        el = J[f'elbow_{side}']
        wr = J[f'wrist_{side}']
        a, fa, hb = f'arm_{side}', f'forearm_{side}', f'hand_{side}'

        # Upper Arm: Heavy Cast-Iron Structural Beam
        f.beam(a, sh + (0, -0.4, 0), el + (0, 0.4, 0), 4.8, 5.2, 'iron', label='arm_beam')
        f.cube(a, (s * 10.3, 25.5, -0.5), (5.0, 3.8, 5.4), 'terracotta', rot((0, 0, -s * 6)), label='bicep_plate')

        # Dual Hydraulic Piston Cylinders (Bicep & Tricep)
        # Front Bicep Piston
        f.beam(a, sh + (-s * 0.4, 0.5, -2.4), el + (-s * 0.6, 0.8, -2.6), 1.5, 1.5, 'brass', label='hydraulic_bicep')
        f.beam(a, el + (-s * 0.6, 0.8, -2.6), el + (-s * 0.6, -1.0, -2.8), 0.85, 0.85, 'steel', label='piston_rod_f')
        # Rear Tricep Piston
        f.beam(a, sh + (s * 0.3, 0.5, 2.2), el + (s * 0.4, 0.8, 1.8), 1.3, 1.3, 'brass', label='hydraulic_tricep')

        # Elbow Rotary Axle
        f.cube(fa, el + (0, 0.2, 0), (5.6, 3.0, 5.6), 'iron', rot((0, 0, 0)), label='elbow_joint')
        f.cube(fa, el + (s * 2.8, 0.2, 0), (0.8, 2.4, 2.4), 'brass', rot((0, 0, 0)), label='elbow_pin')
        # Heavy elbow strike spike / relief exhaust
        f.spike(fa, el + (0, 0.2, 2.4), el + (0, 0.2, 4.8), 1.5, 'steel', steps=3, label='elbow_spur')

        # Forearm: Excavator Crusher Chassis
        f.beam(fa, el + (0, -0.4, 0), wr + (0, 0.4, 0), 5.4, 5.8, 'terracotta', label='forearm_hull', engrave='chevron')
        f.beam(fa, el + (0, -0.4, 0), wr + (0, 0.4, 0), 5.6, 2.2, 'iron', label='forearm_spine')

        # Outer Pile-Driver Pressure Chamber (Mounted along side of forearm)
        f.beam(fa, el + (s * 3.0, -0.6, 0.5), wr + (s * 3.0, 0.8, -2.4), 1.6, 1.6, 'iron', label='piledriver_chamber')
        f.beam(fa, el + (s * 3.0, -0.6, 0.5), wr + (s * 3.0, 0.8, -2.4), 1.0, 1.0, 'brass', label='piledriver_sleeve')
        f.cube(fa, el + (s * 3.0, -0.4, 1.8), (1.4, 1.4, 1.2), 'brass', rot((0, 0, 0)), label='piledriver_cap')
        f.cube(fa, el + (s * 3.0, -0.4, 2.4), (0.8, 0.8, 0.4), 'core', rot((0, 0, 0)), label='piledriver_glow')

        # External Brass High-Pressure Pipes
        f.beam(fa, el + (-s * 2.4, -0.8, -1.0), wr + (-s * 2.4, 0.8, -3.6), 0.75, 0.75, 'brass', label='forearm_pipe_in')

        # Colossal Rock-Crusher Pile-Driver Fists (No held weapon)
        # Wrist Sleeve & Cuff
        f.box(hb, (wr[0] - 2.6, wr[1] - 1.2, wr[2] - 2.6), (5.2, 2.4, 5.2), 'iron', label='wrist_sleeve')
        f.box(hb, (wr[0] - 2.8, wr[1] - 0.4, wr[2] - 2.8), (5.6, 0.8, 5.6), 'brass', label='wrist_cuff')

        # Palm / Hammer-Fist Block
        f.box(hb, (wr[0] - 2.5, wr[1] - 4.6, wr[2] - 2.4), (5.0, 3.6, 4.8), 'iron', label='fist_block')

        # 4 Articulated Pneumatic Knuckle Blocks with Tungsten Chisel Teeth
        for i in range(4):
            x = wr[0] + (i - 1.5) * 1.18
            # Brass knuckle housing
            f.cube(hb, (x, wr[1] - 3.4, wr[2] - 3.6), (1.05, 2.6, 1.4), 'brass', rot((0, 0, 0)), label='knuckle_brass')
            # Steel chisel striker
            f.cube(hb, (x, wr[1] - 3.4, wr[2] - 4.2), (0.9, 2.2, 0.8), 'steel', rot((0, 0, 0)), label='knuckle_chisel')
            # Rock-splitting point
            f.spike(hb, (x, wr[1] - 3.4, wr[2] - 4.5), (x, wr[1] - 3.4, wr[2] - 5.8), 0.75, 'steel', steps=2, label='chisel_tooth')

        # Glowing Volcanic Thermal Radiator Grates on underside of fist
        f.box(hb, (wr[0] - 2.1, wr[1] - 4.8, wr[2] - 3.0), (4.2, 0.6, 3.2), 'core', label='fist_radiator')
        f.box(hb, (wr[0] - 2.3, wr[1] - 4.9, wr[2] - 3.1), (4.6, 0.4, 3.4), 'iron', label='radiator_frame')

# ---------------------------------------------------------------------------
# Pelvis, Ballast Girdle & Turret Waist Joint
# ---------------------------------------------------------------------------
def lower_body(f):
    p = 'pelvis'
    # Heavy rotary turret joint & column bridging seamlessly to chest
    f.box(p, (-4.8, 16.0, -4.0), (9.6, 5.5, 8.0), 'iron', label='turret_stanchion')
    f.box(p, (-5.2, 17.5, -4.4), (10.4, 1.2, 8.8), 'brass', label='turret_gear_ring')

    # Dual Hydraulic Tilt Cylinders (Front pelvis -> chest bridge)
    for x_s in (-1, 1):
        f.beam(p, (x_s * 3.4, 16.0, -3.8), (x_s * 3.8, 21.5, -4.2), 1.2, 1.2, 'brass', label='pelvis_tilt_piston')
        f.beam(p, (x_s * 3.8, 21.5, -4.2), (x_s * 3.8, 23.5, -4.2), 0.75, 0.75, 'steel', label='tilt_rod')

    # Heavy Quarry Ballast Girdle
    f.box(p, (-6.4, 13.6, -4.6), (12.8, 3.6, 9.2), 'terracotta', label='ballast_belt')
    f.box(p, (-6.8, 14.6, -4.8), (13.6, 1.2, 9.6), 'iron', label='belt_frame')

    # Central Excavator Groin Shield
    f.cube(p, (0, 13.4, -5.0), (3.8, 4.2, 1.0), 'iron', rot((0, 0, 0)), label='groin_shield')
    f.cube(p, (0, 13.4, -5.3), (2.4, 2.4, 0.6), 'brass', rot((0, 0, 45)), label='groin_gem')
    f.cube(p, (0, 13.4, -5.5), (1.4, 1.4, 0.4), 'core', rot((0, 0, 45)), label='groin_core')

    # Flank Skirt Tassets (sash_{side})
    for side, s in SIDES:
        sb = f'sash_{side}'
        top = np.array([s * 6.4, 15.0, 0.0])
        # Industrial armor plates
        f.cube(sb, top + np.array([s * 0.5, -1.8, 0]), (1.2, 4.4, 6.8), 'iron', rot((0, 0, -s * 12)), label='tasset_iron')
        f.cube(sb, top + np.array([s * 0.9, -1.8, 0]), (0.8, 3.8, 6.0), 'terracotta', rot((0, 0, -s * 12)), label='tasset_mesa')

    # Stanchion Legs & Trench-Treader Boots
    for side, s in SIDES:
        hip = J[f'hip_{side}']
        kn = J[f'knee_{side}']
        an = J[f'ankle_{side}']
        l, sh, ft = f'leg_{side}', f'shin_{side}', f'foot_{side}'

        # Thigh: Heavy Hydraulic Excavator Stanchion
        f.beam(l, hip + (0, -0.4, 0), kn + (0, 0.4, 0), 4.4, 4.6, 'iron', label='thigh_column')
        f.cube(l, hip + (0, -3.5, -0.2), (4.8, 4.5, 5.0), 'terracotta', rot((0, 0, 0)), label='thigh_mesa')
        f.cube(l, hip + (0, -3.5, -2.4), (5.0, 1.0, 0.6), 'brass', rot((0, 0, 0)), label='thigh_band')
        # Outer Thigh Hydraulic Lift Piston
        f.beam(l, hip + (s * 2.4, 0.0, 0.0), kn + (s * 2.4, 0.0, 0.0), 1.2, 1.2, 'brass', label='thigh_lift_piston')

        # Knee Impact Bumper
        f.cube(sh, kn + (0, 0.2, -2.4), (4.6, 2.8, 2.2), 'iron', rot((0, 0, 0)), label='knee_bumper')
        f.cube(sh, kn + (0, 0.2, -3.2), (3.4, 1.8, 0.8), 'brass', rot((0, 0, 0)), label='knee_strike_plate')

        # Shin Chassis
        f.beam(sh, kn + (0, -0.4, 0), an + (0, 0.4, 0), 4.6, 4.8, 'terracotta', label='shin_hull', engrave='chevron')

        # 4 Horizontal Brass Reinforcement Bands (Signature Phlegethon feature)
        for y in (6.8, 5.2, 3.6, 2.0):
            f.cube(sh, (an[0], y, -2.4), (4.8, 0.7, 0.7), 'brass', rot((0, 0, 0)), label='shin_band')
            f.cube(sh, (an[0], y, -2.75), (2.0, 0.45, 0.3), 'steel', rot((0, 0, 0)), label='shin_clasp')

        # Dual Rear Hydraulic Calf Dampeners
        for x_off in (-1.1, 1.1):
            f.beam(sh, kn + (x_off, -0.5, 2.2), an + (x_off, 0.5, 2.4), 1.0, 1.0, 'brass', label='hydraulic_calf')

        # Trench-Treader Quarry Boot
        # Heavy cast-iron track hull
        f.box(ft, (an[0] - 2.6, 0.0, -5.2), (5.2, 3.2, 8.8), 'iron', label='boot_hull')
        f.box(ft, (an[0] - 2.4, 1.0, -4.8), (4.8, 2.4, 8.0), 'terracotta', label='boot_mesa')

        # Front Toe Quarry Plow & Glowing Radiator Grate
        f.box(ft, (an[0] - 2.7, 0.0, -5.8), (5.4, 1.6, 1.2), 'steel', label='toe_plow')
        f.box(ft, (an[0] - 1.8, 0.6, -6.0), (3.6, 0.7, 0.4), 'core', label='toe_radiator')

        # Serrated Rock-Biting Cleat Treads (Underneath sole)
        for z in (-4.0, -1.8, 0.4, 2.4):
            f.box(ft, (an[0] - 2.8, -0.3, z), (5.6, 0.4, 1.0), 'steel', label='sole_cleat')

# ---------------------------------------------------------------------------
# Master Sculpt Function
# ---------------------------------------------------------------------------
def sculpt():
    f = Forge('arkhaia_phlegethon', MATERIALS, seed=505)
    skeleton(f)
    helm(f)
    torso(f)
    mantle_and_pauldrons(f)
    arms(f)
    lower_body(f)
    return f

# ---------------------------------------------------------------------------
# Animation Pipeline: Direct 100% Retargeting of All 16 Authored Clips
# ---------------------------------------------------------------------------
def load_and_retarget_animations(bak_path, new_model_bones):
    """Retargets every animation clip from the backup model to new bone UUIDs by bone name."""
    with open(bak_path, 'r', encoding='utf-8') as f:
        bak_data = json.load(f)

    # Map bone name to new UUID
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
        c['animators'] = new_animators
        retargeted_clips.append(c)

    return retargeted_clips

# ---------------------------------------------------------------------------
# Build & Export
# ---------------------------------------------------------------------------
def build():
    f = sculpt()
    hitbox = [[-14, 0, -14], [14, 46, 14]]
    meta = dict(
        evergarden_revision=REVISION,
        evergarden_scale=SCALE
    )
    # Build model geometry, atlas and base structure
    model, atlas, stats = build_model(
        f, [], 'arkhaia_phlegethon', 'phlegethon', hitbox,
        meta=meta, size=1024, density=8.5
    )

    # Retarget the 16 authored animation clips from the backup model
    bak_path = ROOT / 'evergarden/src/main/resources/modelengine/rivers/arkhaia_phlegethon.bbmodel.bak'
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
    target = args.out / 'arkhaia_phlegethon.bbmodel'
    target.write_text(dump(model) + '\n', encoding='utf8')

    art = ROOT / 'evergarden/art/arkhaia-rivers'
    art.mkdir(parents=True, exist_ok=True)
    atlas.save(art / 'phlegethon_rig.png')

    print(f"Phlegethon Remastered v2: {len(model['elements']) - 1} cubes, {len(sculpt().bones)} bones, "
          f"{len(model['animations'])} clips, atlas {stats['coverage']:.0%} coverage at {stats['density']:.1f}px/unit")

if __name__ == '__main__':
    main()
