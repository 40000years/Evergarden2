"""Pack assets for the Advance Magic 1.38.0 spell VFX kit (art from tools/vfx_kit_textures.py).

Java: one flat plane model and item definition per sprite (full colour; the greyscale beam gets one
constant-tint item per element tint).

Bedrock, all driven by Molang variables in a single SpawnParticleEffect packet:

* advance_magic:fxs_<sprite>_<mode> plays one sprite for its whole life on the client: size eases
  from fx_s0 to fx_s1 over fx_grow of its life, spins fx_spin degrees per second, fades in over fx_in
  and out over fx_out seconds (and, with fx_collapse, shrinks while fading, as the Java display does).
  Modes: bb (camera-facing), flat (horizontal), plane (normal fx_nx/ny/nz), vert (turns about Y).
  Sprites are the kit art plus the 1.37 tintable vfx_* sprites, coloured by fx_r/g/b;
* advance_magic:kit_<element>_<kind>[_<layer>] are particle emitters drawn from a 16 px atlas: an
  instant burst (three layers), a ground ring, a rising column, a swirl (funnel or drain), a projectile
  trail, a rain and a line (k_dx/k_dy/k_dz to the far end, k_w wide), scaled by k_r (radius), k_n (count),
  k_t (seconds), k_h (height), k_s (size) and k_dir (funnel or drain);
* advance_magic:fx_<sprite>[_flat|_bb] are 0.26 s planes like the 1.37 vfx_* ones, for tracked sprites
  that a scene redraws (the beam gets one per tint).

    python tools/vfx_kit_assets.py --output DIR
"""
import argparse
import io
import json
import math
from pathlib import Path

import vfx_kit_textures as art
from vfx_sprite_assets import encode

TINTS = art.TINTS
LEGACY = ('vfx_sigil_a', 'vfx_sigil_b', 'vfx_ring', 'vfx_flare', 'vfx_slash', 'vfx_spark')
MODES = ('bb', 'flat', 'plane', 'vert')
KINDS = ('burst', 'ring', 'rise', 'swirl', 'trail', 'rain', 'line')


def dumps(value):
    return json.dumps(value, separators=(',', ':')).encode()


# --- animated sprites --------------------------------------------------------------------------------

def sprite_particle(name, texture, width, height, mode):
    grow = 'math.clamp(variable.particle_age / math.max(0.01, variable.fx_grow * variable.particle_lifetime), 0, 1)'
    fade_out = 'math.clamp((variable.particle_lifetime - variable.particle_age) / math.max(0.01, variable.fx_out), 0, 1)'
    fade_in = 'math.clamp(variable.particle_age / math.max(0.01, variable.fx_in), 0, 1)'
    collapse = f'(variable.fx_collapse > 0 ? {fade_out} : 1)'
    size = f'math.lerp(variable.fx_s0, variable.fx_s1, {grow}) * {collapse} * 0.5'
    billboard = {'size': [size, f'{size} * variable.fx_aspect'],
                 'uv': {'texture_width': width, 'texture_height': height, 'uv': [0, 0], 'uv_size': [width, height]}}
    if mode == 'bb':
        billboard['facing_camera_mode'] = 'rotate_xyz'
    elif mode == 'flat':
        billboard['facing_camera_mode'] = 'emitter_transform_xz'
    elif mode == 'vert':
        billboard['facing_camera_mode'] = 'rotate_y'
    else:
        billboard['facing_camera_mode'] = 'direction_z'
        billboard['direction'] = {'mode': 'custom', 'custom_direction': ['variable.fx_nx', 'variable.fx_ny', 'variable.fx_nz']}
    return {'format_version': '1.10.0', 'particle_effect': {
        'description': {'identifier': f'advance_magic:fxs_{name}_{mode}',
                        'basic_render_parameters': {'material': 'particles_blend', 'texture': f'textures/particle/{texture}'}},
        'components': {
            'minecraft:emitter_lifetime_once': {'active_time': 0.01},
            'minecraft:emitter_rate_instant': {'num_particles': 1},
            'minecraft:emitter_shape_point': {'offset': ['variable.fx_ox', 'variable.fx_oy', 'variable.fx_oz'], 'direction': [0, 0, 0]},
            'minecraft:particle_lifetime_expression': {'max_lifetime': 'variable.fx_life'},
            'minecraft:particle_initial_speed': 0,
            'minecraft:particle_initial_spin': {'rotation': 'variable.fx_rot', 'rotation_rate': 'variable.fx_spin'},
            'minecraft:particle_motion_dynamic': {},
            'minecraft:particle_appearance_billboard': billboard,
            'minecraft:particle_appearance_tinting': {'color': ['variable.fx_r', 'variable.fx_g', 'variable.fx_b',
                                                                f'variable.fx_a * {fade_in} * {fade_out}']}}}}


# --- emitter kit -------------------------------------------------------------------------------------

AGE = 'variable.particle_age / variable.particle_lifetime'
R1, R2, R3, R4 = ('variable.particle_random_1', 'variable.particle_random_2', 'variable.particle_random_3', 'variable.particle_random_4')


def cell(col, row):
    return {'texture_width': 128, 'texture_height': 128, 'uv': [col * 16, row * 16], 'uv_size': [16, 16]}


def cells(cols, row):
    """A random pick among atlas cells on one row."""
    choice = f'math.floor({R2} * {len(cols)})'
    expr = str(cols[-1] * 16)
    for i in range(len(cols) - 2, -1, -1):
        expr = f'({choice} == {i} ? {cols[i] * 16} : {expr})'
    return {'texture_width': 128, 'texture_height': 128, 'uv': [expr, row * 16], 'uv_size': [16, 16]}


def flipbook(row, frames=8, fps=12, stretch=True, loop=False, start=0):
    return {'texture_width': 128, 'texture_height': 128, 'flipbook': {
        'base_UV': [start * 16, row * 16], 'size_UV': [16, 16], 'step_UV': [16, 0], 'frames_per_second': fps,
        'max_frame': frames, 'stretch_to_lifetime': stretch, 'loop': loop}}


def gradient(*stops):
    return {'interpolant': AGE, 'gradient': {f'{t:.2f}': [round(v, 3) for v in c] for t, c in stops}}


# Atlas cells by name: uv dicts.
FLAME = flipbook(0)
SMOKE = flipbook(1)
TWINKLE = flipbook(2, frames=4, fps=10, stretch=False, loop=True)
GLINT = cells((4, 5, 6, 7), 2)
DOT, EMBER, DROP, BUBBLE = cell(0, 3), cell(1, 3), cell(2, 3), cell(3, 3)
SNOW = cells((4, 5), 3)
SHARD = cells((6, 7), 3)
LEAF = cells((0, 1), 4)
ROCK = cells((2, 3, 4), 4)
PUFF = cell(5, 4)
WISP = cells((6, 7), 4)
STREAK, ARC, RING_CELL, HEART, CROSS, PETAL, FEATHER, RUNE = (cell(i, 5) for i in range(8))
SPARKS = flipbook(6, frames=8, fps=24, stretch=False, loop=True)
RUNES = cells(tuple(range(8)), 7)


def layer(uv, material, count, life, size, color, shape, speed=0, accel=(0, 0, 0), drag=0, spin=None, collide=None,
          size_end=None, parametric=None, rate=None):
    """One particle emitter definition. `count` is per instant burst; `rate` (per second) makes it continuous."""
    size0 = f'({size}) * variable.k_s'
    size_expr = size0 if size_end is None else f'math.lerp({size}, {size_end}, {AGE}) * variable.k_s'
    components = {}
    if rate is None:
        components['minecraft:emitter_lifetime_once'] = {'active_time': 0.05}
        components['minecraft:emitter_rate_instant'] = {'num_particles': f'math.max(1, math.floor(({count}) * variable.k_n))'}
    else:
        components['minecraft:emitter_lifetime_once'] = {'active_time': 'math.max(0.05, variable.k_t)'}
        components['minecraft:emitter_rate_steady'] = {'spawn_rate': f'({rate}) * variable.k_n', 'max_particles': 400}
    components.update(shape)
    components['minecraft:particle_lifetime_expression'] = {'max_lifetime': life}
    if parametric:
        components['minecraft:particle_motion_parametric'] = parametric
    else:
        components['minecraft:particle_initial_speed'] = speed
        motion = {'linear_acceleration': list(accel)}
        if drag:
            motion['linear_drag_coefficient'] = drag
        components['minecraft:particle_motion_dynamic'] = motion
    if spin:
        components['minecraft:particle_initial_spin'] = {'rotation': f'{R3} * 360', 'rotation_rate': f'({R4} - 0.5) * {spin * 2}'}
    if collide:
        components['minecraft:particle_motion_collision'] = {'enabled': True, 'collision_drag': collide.get('drag', 2),
                                                             'coefficient_of_restitution': collide.get('bounce', .3),
                                                             'collision_radius': .05, 'expire_on_contact': collide.get('expire', False)}
    components['minecraft:particle_appearance_billboard'] = {'size': [size_expr, size_expr], 'facing_camera_mode': 'rotate_xyz', 'uv': uv}
    components['minecraft:particle_appearance_tinting'] = {'color': color}
    return material, components


def sphere(radius='variable.k_r * 0.3', direction='outwards', offset=(0, 0, 0)):
    return {'minecraft:emitter_shape_sphere': {'offset': list(offset), 'radius': radius, 'surface_only': False, 'direction': direction}}


def disc(radius='variable.k_r', direction='outwards', offset=(0, 0, 0), surface=False):
    return {'minecraft:emitter_shape_disc': {'offset': list(offset), 'radius': radius, 'plane_normal': 'y', 'surface_only': surface, 'direction': direction}}


def fade(r, g, b, a=1.0, end=None):
    """A colour whose alpha falls to zero over the particle's life."""
    er, eg, eb = end or (r, g, b)
    return gradient((0, (r, g, b, a)), (.6, ((r + er) / 2, (g + eg) / 2, (b + eb) / 2, a * .85)), (1, (er, eg, eb, 0)))


ADD, BLEND = 'particles_add', 'particles_blend'
OUT = sphere()
SPEED = f'(1.6 + 1.4 * {R1}) * math.sqrt(math.max(0.25, variable.k_r)) * 2'
# Spiral funnel (k_dir > 0) or inward drain (k_dir < 0) around the emitter.
P = AGE
SWIRL = {'relative_position': [
    f'math.cos({R1} * 360 + variable.particle_age * 260 * (variable.k_dir < 0 ? 1.6 : 1)) * variable.k_r * (variable.k_dir < 0 ? (1 - 0.85 * {P}) : (0.3 + 0.7 * {P}))',
    f'(variable.k_dir < 0 ? 0.1 + 0.25 * {R2} : variable.k_h * {P})',
    f'math.sin({R1} * 360 + variable.particle_age * 260 * (variable.k_dir < 0 ? 1.6 : 1)) * variable.k_r * (variable.k_dir < 0 ? (1 - 0.85 * {P}) : (0.3 + 0.7 * {P}))']}


def element_kit(element):
    """{suffix: (material, components)} for one element's emitters."""
    L = {}
    rise_speed = f'variable.k_h * (0.9 + 0.5 * {R1})'
    if element == 'fire':
        L['burst_a'] = layer(FLAME, ADD, 26, f'0.45 + 0.35 * {R2}', '0.32 + 0.12 * ' + R3, fade(1, .95, .75, 1, (.6, .12, .03)), OUT, SPEED, (0, 3, 0), 3, size_end='0.12')
        L['burst_b'] = layer(EMBER, ADD, 18, f'0.8 + 0.6 * {R2}', '0.07', fade(1, .75, .3, 1, (1, .3, .05)), sphere('variable.k_r * 0.25'), f'(3 + 3 * {R1}) * math.sqrt(math.max(0.25, variable.k_r))', (0, -5, 0), 1, collide={'bounce': .4})
        L['burst_c'] = layer(SMOKE, BLEND, 8, f'1.3 + 0.6 * {R2}', '0.45', fade(.28, .24, .22, .55, (.12, .12, .12)), sphere('variable.k_r * 0.4'), '0.9 * variable.k_r', (0, 1.4, 0), 1.6, size_end='1.25')
        L['ring'] = layer(FLAME, ADD, 34, f'0.5 + 0.2 * {R2}', '0.4', fade(1, .9, .6, 1, (.7, .15, .03)), disc('0.3', 'outwards', (0, .15, 0), True), '2.6 * variable.k_r', (0, 1.5, 0), 2.6, size_end='0.15')
        L['rise_a'] = layer(FLAME, ADD, 0, f'0.6 + 0.4 * {R2}', '0.35', fade(1, .9, .6, 1, (.6, .12, .03)), disc(direction=[0, 1, 0]), rise_speed, (0, 1, 0), 1.2, size_end='0.1', rate=40)
        L['rise_b'] = layer(EMBER, ADD, 0, f'1 + 0.6 * {R2}', '0.06', fade(1, .7, .25, 1, (1, .25, .05)), disc(direction=[0, 1, 0]), f'{rise_speed} * 1.4', (0, -1, 0), .6, rate=16)
        L['swirl'] = layer(FLAME, ADD, 0, f'1.0 + 0.5 * {R2}', '0.38', fade(1, .9, .55, 1, (.7, .15, .03)), sphere('0.01'), parametric=SWIRL, size_end='0.18', rate=55)
        L['trail'] = layer(FLAME, ADD, 5, f'0.3 + 0.25 * {R2}', '0.22', fade(1, .9, .6, 1, (.7, .2, .05)), sphere('0.12'), '0.4', (0, 1.5, 0), 2, size_end='0.06')
        L['rain'] = layer(EMBER, ADD, 0, '1.2', '0.09', fade(1, .7, .3, 1, (1, .3, .05)), disc(offset=(0, 'variable.k_h', 0), direction=[0, -1, 0]), '6', (0, -4, 0), 0, collide={'expire': True}, rate=30)
    elif element == 'water':
        L['burst_a'] = layer(DROP, BLEND, 30, f'0.6 + 0.4 * {R2}', '0.09 + 0.04 * ' + R3, fade(.78, .93, 1, 1, (.45, .78, 1)), sphere('variable.k_r * 0.3', 'outwards', (0, -.3, 0)), f'(3 + 3 * {R1}) * math.sqrt(math.max(0.25, variable.k_r))', (0, -12, 0), .5, collide={'expire': True})
        L['burst_b'] = layer(DOT, BLEND, 10, f'0.8 + 0.4 * {R2}', '0.45', fade(.88, .96, 1, .5, (.8, .92, 1)), sphere('variable.k_r * 0.5'), '0.7 * variable.k_r', (0, .4, 0), 2, size_end='0.95')
        L['burst_c'] = layer(BUBBLE, BLEND, 10, f'0.6 + 0.5 * {R2}', '0.13', fade(.85, .97, 1, .9), sphere('variable.k_r * 0.4'), '0.5', (0, 1.5, 0), 1.5)
        L['ring'] = layer(DOT, BLEND, 30, f'0.55 + 0.2 * {R2}', '0.35', fade(.85, .96, 1, .7, (.5, .8, 1)), disc('0.3', 'outwards', (0, .15, 0), True), '2.6 * variable.k_r', (0, .8, 0), 2.6, size_end='0.7')
        L['rise_a'] = layer(DROP, BLEND, 0, f'0.8 + 0.4 * {R2}', '0.09', fade(.8, .94, 1, 1, (.5, .8, 1)), disc(direction=[0, 1, 0]), f'{rise_speed} * 1.6', (0, -9, 0), 0, rate=45)
        L['rise_b'] = layer(BUBBLE, BLEND, 0, f'0.8 + 0.6 * {R2}', '0.12', fade(.85, .97, 1, .9), disc(direction=[0, 1, 0]), rise_speed, (0, .5, 0), .8, rate=14)
        L['swirl'] = layer(DOT, BLEND, 0, f'1.2 + 0.4 * {R2}', '0.3', fade(.7, .9, 1, .8, (.35, .7, 1)), sphere('0.01'), parametric=SWIRL, rate=60)
        L['trail'] = layer(DROP, BLEND, 4, f'0.35 + 0.2 * {R2}', '0.08', fade(.8, .94, 1, 1, (.5, .8, 1)), sphere('0.12'), '0.5', (0, -6, 0), 1)
        L['rain'] = layer(DROP, BLEND, 0, '0.9', '0.08', fade(.75, .9, 1, .9, (.6, .85, 1)), disc(offset=(0, 'variable.k_h', 0), direction=[0, -1, 0]), '9', (0, -6, 0), 0, collide={'expire': True}, rate=70)
    elif element == 'ice':
        L['burst_a'] = layer(SHARD, BLEND, 22, f'0.8 + 0.5 * {R2}', '0.16 + 0.08 * ' + R3, fade(.96, 1, 1, 1, (.6, .85, 1)), OUT, f'(2.5 + 3 * {R1}) * math.sqrt(math.max(0.25, variable.k_r))', (0, -9, 0), .6, spin=400, collide={'bounce': .3})
        L['burst_b'] = layer(SNOW, BLEND, 16, f'1.0 + 0.6 * {R2}', '0.1', fade(1, 1, 1, 1, (.8, .92, 1)), sphere('variable.k_r * 0.6'), '0.7', (0, -.6, 0), 1.5, spin=120)
        L['burst_c'] = layer(PUFF, BLEND, 8, f'1.0 + 0.4 * {R2}', '0.5', fade(.92, .97, 1, .45, (.8, .9, 1)), sphere('variable.k_r * 0.4'), '0.6 * variable.k_r', (0, .2, 0), 2, size_end='1.3')
        L['ring'] = layer(SNOW, BLEND, 30, f'0.6 + 0.2 * {R2}', '0.14', fade(1, 1, 1, 1, (.7, .88, 1)), disc('0.3', 'outwards', (0, .15, 0), True), '2.6 * variable.k_r', (0, .5, 0), 2.6, spin=200)
        L['rise_a'] = layer(SNOW, BLEND, 0, f'1.2 + 0.6 * {R2}', '0.1', fade(1, 1, 1, 1, (.75, .9, 1)), disc(direction=[0, 1, 0]), f'{rise_speed} * 0.6', (0, 0, 0), .4, spin=90, rate=30)
        L['rise_b'] = layer(GLINT, ADD, 0, f'0.5 + 0.4 * {R2}', '0.12', fade(.85, .95, 1, .9), disc(direction=[0, 1, 0]), rise_speed, (0, 0, 0), 1, rate=12)
        L['swirl'] = layer(SNOW, BLEND, 0, f'1.3 + 0.4 * {R2}', '0.12', fade(1, 1, 1, 1, (.7, .88, 1)), sphere('0.01'), parametric=SWIRL, spin=200, rate=70)
        L['trail'] = layer(SNOW, BLEND, 4, f'0.5 + 0.3 * {R2}', '0.08', fade(1, 1, 1, 1, (.7, .88, 1)), sphere('0.12'), '0.3', (0, -.5, 0), 1, spin=200)
        L['rain'] = layer(SNOW, BLEND, 0, '2.2', '0.11', fade(1, 1, 1, 1, (.85, .94, 1)), disc(offset=(0, 'variable.k_h', 0), direction=[0, -1, 0]), '1.8', (0, -.4, 0), .2, spin=90, rate=60)
    elif element == 'wind':
        L['burst_a'] = layer(ARC, BLEND, 22, f'0.35 + 0.2 * {R2}', '0.45 + 0.2 * ' + R3, fade(1, 1, 1, .95, (.45, 1, .75)), OUT, f'(4 + 3 * {R1}) * math.sqrt(math.max(0.25, variable.k_r))', (0, 0, 0), 3.5, spin=700)
        L['burst_b'] = layer(LEAF, BLEND, 10, f'0.9 + 0.5 * {R2}', '0.16', fade(.55, .95, .55, 1, (.3, .8, .4)), OUT, f'(2 + 2 * {R1}) * math.sqrt(math.max(0.25, variable.k_r))', (0, 1.2, 0), 1.5, spin=500)
        L['burst_c'] = layer(PUFF, BLEND, 6, f'0.8 + 0.4 * {R2}', '0.45', fade(.95, 1, .97, .35), sphere('variable.k_r * 0.4'), '0.8 * variable.k_r', (0, .4, 0), 2.2, size_end='1.1')
        L['ring'] = layer(WISP, BLEND, 28, f'0.4 + 0.2 * {R2}', '0.4', fade(1, 1, 1, .9, (.5, 1, .8)), disc('0.3', 'outwards', (0, .2, 0), True), '2.8 * variable.k_r', (0, .6, 0), 2.8, spin=400)
        L['rise_a'] = layer(WISP, BLEND, 0, f'0.6 + 0.3 * {R2}', '0.35', fade(1, 1, 1, .85, (.5, 1, .8)), disc(direction=[0, 1, 0]), f'{rise_speed} * 1.3', (0, 0, 0), 1, spin=500, rate=36)
        L['rise_b'] = layer(LEAF, BLEND, 0, f'1.0 + 0.5 * {R2}', '0.14', fade(.55, .95, .55, 1, (.3, .8, .4)), disc(direction=[0, 1, 0]), rise_speed, (0, 0, 0), .8, spin=500, rate=10)
        L['swirl'] = layer(WISP, BLEND, 0, f'0.9 + 0.4 * {R2}', '0.42', fade(1, 1, 1, .9, (.5, 1, .8)), sphere('0.01'), parametric=SWIRL, spin=300, rate=70)
        L['trail'] = layer(WISP, BLEND, 3, f'0.3 + 0.2 * {R2}', '0.3', fade(1, 1, 1, .85, (.5, 1, .8)), sphere('0.12'), '0.6', (0, 0, 0), 2, spin=600)
        L['rain'] = layer(LEAF, BLEND, 0, '1.6', '0.15', fade(.55, .95, .55, 1, (.3, .8, .4)), disc(offset=(0, 'variable.k_h', 0), direction=[0, -1, 0]), '2', (1, -.5, 0), .3, spin=500, rate=24)
    elif element == 'earth':
        rock = ['0.5 + 0.18 * ' + R4, '0.42 + 0.12 * ' + R4, '0.33 + 0.08 * ' + R4, f'math.clamp((variable.particle_lifetime - variable.particle_age) * 4, 0, 1)']
        L['burst_a'] = layer(ROCK, BLEND, 20, f'1.0 + 0.6 * {R2}', '0.14 + 0.1 * ' + R3, rock, sphere('variable.k_r * 0.3', 'outwards', (0, -.45, 0)), f'(3 + 4 * {R1}) * math.sqrt(math.max(0.25, variable.k_r))', (0, -14, 0), .3, spin=500, collide={'bounce': .35, 'drag': 1.5})
        L['burst_b'] = layer(SMOKE, BLEND, 14, f'1.2 + 0.6 * {R2}', '0.6', fade(.62, .53, .41, .6, (.5, .45, .38)), disc('variable.k_r * 0.5', 'outwards', (0, .2, 0)), '1.5 * variable.k_r', (0, .45, 0), 2.5, size_end='1.6')
        L['burst_c'] = layer(DOT, BLEND, 18, f'0.6 + 0.4 * {R2}', '0.06', fade(.75, .62, .45, 1, (.5, .42, .32)), OUT, f'(4 + 2 * {R1}) * math.sqrt(math.max(0.25, variable.k_r))', (0, -10, 0), .5, collide={'expire': True})
        L['ring'] = layer(SMOKE, BLEND, 30, f'0.8 + 0.3 * {R2}', '0.5', fade(.65, .56, .44, .65, (.55, .48, .4)), disc('0.3', 'outwards', (0, .15, 0), True), '2.4 * variable.k_r', (0, .3, 0), 2.4, size_end='1.1')
        L['rise_a'] = layer(ROCK, BLEND, 0, f'1.4 + 0.6 * {R2}', '0.12 + 0.08 * ' + R3, rock, disc(direction=[0, 1, 0]), f'{rise_speed} * 0.5', (0, 0, 0), .6, spin=120, rate=14)
        L['rise_b'] = layer(DOT, BLEND, 0, f'1.0 + 0.6 * {R2}', '0.07', fade(.8, .7, .5, .9, (.6, .5, .38)), disc(direction=[0, 1, 0]), f'{rise_speed} * 0.7', (0, 0, 0), .8, rate=24)
        L['swirl'] = layer(SMOKE, BLEND, 0, f'1.2 + 0.5 * {R2}', '0.45', fade(.85, .74, .52, .55, (.6, .5, .38)), sphere('0.01'), parametric=SWIRL, size_end='0.8', rate=45)
        L['trail'] = layer(DOT, BLEND, 4, f'0.4 + 0.3 * {R2}', '0.07', fade(.7, .6, .45, 1, (.5, .43, .35)), sphere('0.12'), '0.4', (0, -4, 0), 1)
        L['rain'] = layer(ROCK, BLEND, 0, '1.0', '0.12 + 0.08 * ' + R3, rock, disc(offset=(0, 'variable.k_h', 0), direction=[0, -1, 0]), '7', (0, -10, 0), 0, spin=400, collide={'expire': True}, rate=18)
    elif element == 'light':
        L['burst_a'] = layer(GLINT, ADD, 18, f'0.3 + 0.15 * {R2}', '0.32', fade(1, 1, .9, 1, (1, .8, .3)), sphere('variable.k_r * 0.1'), f'7 * math.sqrt(math.max(0.25, variable.k_r))', (0, 0, 0), 6, size_end='0.06')
        L['burst_b'] = layer(TWINKLE, ADD, 22, f'0.7 + 0.5 * {R2}', '0.17', fade(1, .97, .8, 1, (1, .8, .35)), sphere('variable.k_r * 0.7'), '0.3', (0, .6, 0), 1)
        L['burst_c'] = layer(DOT, ADD, 10, f'1.0 + 0.5 * {R2}', '0.22', fade(1, .9, .55, .7, (1, .75, .25)), sphere('variable.k_r * 0.5'), '0.2', (0, .8, 0), 1)
        L['ring'] = layer(TWINKLE, ADD, 30, f'0.5 + 0.2 * {R2}', '0.2', fade(1, .97, .8, 1, (1, .8, .3)), disc('0.3', 'outwards', (0, .15, 0), True), '2.6 * variable.k_r', (0, .5, 0), 2.6)
        L['rise_a'] = layer(TWINKLE, ADD, 0, f'0.8 + 0.5 * {R2}', '0.15', fade(1, .97, .8, 1, (1, .8, .35)), disc(direction=[0, 1, 0]), rise_speed, (0, 0, 0), .8, rate=32)
        L['rise_b'] = layer(DOT, ADD, 0, f'1.0 + 0.5 * {R2}', '0.2', fade(1, .9, .55, .6, (1, .75, .25)), disc(direction=[0, 1, 0]), f'{rise_speed} * 0.6', (0, 0, 0), .6, rate=14)
        L['swirl'] = layer(TWINKLE, ADD, 0, f'1.2 + 0.4 * {R2}', '0.16', fade(1, .97, .8, 1, (1, .8, .3)), sphere('0.01'), parametric=SWIRL, rate=50)
        L['trail'] = layer(TWINKLE, ADD, 4, f'0.35 + 0.25 * {R2}', '0.13', fade(1, .97, .8, 1, (1, .8, .35)), sphere('0.12'), '0.2', (0, .4, 0), 1)
        L['rain'] = layer(TWINKLE, ADD, 0, '1.4', '0.14', fade(1, .97, .8, 1, (1, .82, .4)), disc(offset=(0, 'variable.k_h', 0), direction=[0, -1, 0]), '3', (0, -.5, 0), .3, rate=40)
    elif element == 'dark':
        L['burst_a'] = layer(WISP, BLEND, 18, f'0.5 + 0.3 * {R2}', '0.38 + 0.12 * ' + R3, fade(.78, .5, 1, .9, (.22, .05, .38)), sphere('variable.k_r * 0.9', 'inwards'), f'2.4 * math.sqrt(math.max(0.25, variable.k_r))', (0, 0, 0), 1.5, spin=400)
        L['burst_b'] = layer(EMBER, ADD, 20, f'0.4 + 0.3 * {R2}', '0.07', fade(.85, .6, 1, 1, (.4, .1, .8)), OUT, f'4 * math.sqrt(math.max(0.25, variable.k_r))', (0, 0, 0), 2.5)
        L['burst_c'] = layer(SMOKE, BLEND, 8, f'1.2 + 0.5 * {R2}', '0.5', fade(.2, .08, .3, .6, (.05, .02, .08)), sphere('variable.k_r * 0.4'), '0.6 * variable.k_r', (0, .6, 0), 1.8, size_end='1.2')
        L['ring'] = layer(WISP, BLEND, 28, f'0.55 + 0.2 * {R2}', '0.4', fade(.75, .45, 1, .9, (.25, .05, .4)), disc('0.3', 'outwards', (0, .15, 0), True), '2.6 * variable.k_r', (0, .3, 0), 2.6, spin=300)
        L['rise_a'] = layer(WISP, BLEND, 0, f'0.8 + 0.4 * {R2}', '0.35', fade(.7, .4, 1, .85, (.2, .05, .35)), disc(direction=[0, 1, 0]), rise_speed, (0, 0, 0), 1, spin=300, rate=30)
        L['rise_b'] = layer(EMBER, ADD, 0, f'0.8 + 0.5 * {R2}', '0.06', fade(.85, .6, 1, 1, (.4, .1, .8)), disc(direction=[0, 1, 0]), f'{rise_speed} * 1.2', (0, 0, 0), .8, rate=16)
        L['swirl'] = layer(WISP, BLEND, 0, f'1.2 + 0.4 * {R2}', '0.4', fade(.72, .42, 1, .9, (.2, .04, .35)), sphere('0.01'), parametric=SWIRL, spin=300, rate=55)
        L['trail'] = layer(WISP, BLEND, 3, f'0.4 + 0.2 * {R2}', '0.28', fade(.72, .42, 1, .85, (.2, .04, .35)), sphere('0.12'), '0.3', (0, 0, 0), 2, spin=500)
        L['rain'] = layer(EMBER, ADD, 0, '1.5', '0.07', fade(.85, .6, 1, 1, (.4, .1, .8)), disc(offset=(0, 'variable.k_h', 0), direction=[0, -1, 0]), '2.5', (0, -.5, 0), .2, rate=30)
    elif element == 'lightning':
        L['burst_a'] = layer(SPARKS, ADD, 14, f'0.14 + 0.12 * {R2}', '0.45 + 0.25 * ' + R3, fade(.95, .98, 1, 1, (.5, .7, 1)), sphere('variable.k_r * 0.6'), '0', (0, 0, 0), 0, spin=0)
        L['burst_b'] = layer(EMBER, ADD, 26, f'0.3 + 0.3 * {R2}', '0.05', fade(1, 1, 1, 1, (.5, .8, 1)), sphere('variable.k_r * 0.15'), f'(5 + 4 * {R1}) * math.sqrt(math.max(0.25, variable.k_r))', (0, -8, 0), 1.2, collide={'expire': True})
        L['burst_c'] = layer(GLINT, ADD, 6, '0.15', '0.6', fade(.85, .92, 1, .8, (.5, .7, 1)), sphere('variable.k_r * 0.3'), '0', (0, 0, 0), 0, size_end='1.2')
        L['ring'] = layer(SPARKS, ADD, 26, f'0.25 + 0.15 * {R2}', '0.45', fade(.95, .98, 1, 1, (.5, .7, 1)), disc('0.3', 'outwards', (0, .2, 0), True), '3.2 * variable.k_r', (0, 0, 0), 3.2)
        L['rise_a'] = layer(SPARKS, ADD, 0, f'0.15 + 0.15 * {R2}', '0.5', fade(.95, .98, 1, 1, (.5, .7, 1)), disc(offset=(0, '0.2 + variable.k_h * 0.5 * ' + R3, 0), direction=[0, 1, 0]), '0', (0, 0, 0), 0, rate=40)
        L['rise_b'] = layer(EMBER, ADD, 0, f'0.4 + 0.3 * {R2}', '0.05', fade(1, 1, 1, 1, (.5, .8, 1)), disc(direction=[0, 1, 0]), f'{rise_speed} * 1.5', (0, -6, 0), .5, rate=24)
        L['swirl'] = layer(SPARKS, ADD, 0, f'0.5 + 0.3 * {R2}', '0.4', fade(.95, .98, 1, 1, (.5, .7, 1)), sphere('0.01'), parametric=SWIRL, rate=60)
        L['trail'] = layer(SPARKS, ADD, 3, f'0.12 + 0.1 * {R2}', '0.3', fade(.95, .98, 1, 1, (.5, .7, 1)), sphere('0.15'), '0', (0, 0, 0), 0)
        L['rain'] = layer(EMBER, ADD, 0, '0.6', '0.05', fade(1, 1, 1, 1, (.5, .8, 1)), disc(offset=(0, 'variable.k_h', 0), direction=[0, -1, 0]), '12', (0, -6, 0), 0, collide={'expire': True}, rate=40)
    elif element == 'heal':
        L['burst_a'] = layer(LEAF, BLEND, 14, f'1.0 + 0.6 * {R2}', '0.15', fade(.6, 1, .6, 1, (.3, .9, .45)), sphere('variable.k_r * 0.5'), '1.2', (0, 1.2, 0), 1.2, spin=400)
        L['burst_b'] = layer(TWINKLE, ADD, 22, f'0.8 + 0.5 * {R2}', '0.17', fade(.85, 1, .85, 1, (.4, 1, .6)), sphere('variable.k_r * 0.6'), '0.4', (0, 1, 0), 1)
        L['burst_c'] = layer(HEART, BLEND, 4, f'1.0 + 0.4 * {R2}', '0.2', fade(1, .55, .65, 1, (1, .45, .6)), sphere('variable.k_r * 0.4'), '0.3', (0, 1.2, 0), 1)
        L['ring'] = layer(TWINKLE, ADD, 28, f'0.6 + 0.2 * {R2}', '0.2', fade(.85, 1, .85, 1, (.4, 1, .6)), disc('0.3', 'outwards', (0, .15, 0), True), '2.4 * variable.k_r', (0, .8, 0), 2.4)
        L['rise_a'] = layer(LEAF, BLEND, 0, f'1.2 + 0.5 * {R2}', '0.14', fade(.6, 1, .6, 1, (.3, .9, .45)), disc(direction=[0, 1, 0]), f'{rise_speed} * 0.7', (0, 0, 0), .6, spin=300, rate=16)
        L['rise_b'] = layer(TWINKLE, ADD, 0, f'0.9 + 0.5 * {R2}', '0.15', fade(.85, 1, .85, 1, (.4, 1, .6)), disc(direction=[0, 1, 0]), rise_speed, (0, 0, 0), .8, rate=30)
        L['swirl'] = layer(TWINKLE, ADD, 0, f'1.3 + 0.4 * {R2}', '0.15', fade(.85, 1, .85, 1, (.4, 1, .6)), sphere('0.01'), parametric=SWIRL, rate=45)
        L['trail'] = layer(TWINKLE, ADD, 3, f'0.5 + 0.3 * {R2}', '0.13', fade(.85, 1, .85, 1, (.4, 1, .6)), sphere('0.12'), '0.2', (0, .6, 0), 1)
        L['rain'] = layer(TWINKLE, ADD, 0, '1.6', '0.14', fade(.85, 1, .85, 1, (.45, 1, .6)), disc(offset=(0, 'variable.k_h', 0), direction=[0, -1, 0]), '2.2', (0, -.4, 0), .2, rate=40)
    return L


def line(uv, material, life, size, color, rise=0.0, spin=None, size_end=None):
    """Particles scattered along k_dx/k_dy/k_dz from the emitter, k_w wide, drifting up by `rise` per second."""
    def axis(d, r):
        return f'variable.k_{d} * {R1} + ({r} - 0.5) * 0.3 * variable.k_w'
    parametric = {'relative_position': [axis('dx', R2), f"{axis('dy', R3)} + variable.particle_age * {rise}", axis('dz', R4)]}
    return layer(uv, material, '16', life, size, color, {'minecraft:emitter_shape_point': {'offset': [0, 0, 0], 'direction': [0, 0, 0]}},
                 parametric=parametric, spin=spin, size_end=size_end)


LINES = {
    'fire': lambda: line(FLAME, ADD, f'0.35 + 0.25 * {R2}', '0.3', fade(1, .92, .65, 1, (.7, .15, .03)), 1.2, size_end='0.1'),
    'water': lambda: line(DOT, BLEND, f'0.4 + 0.25 * {R2}', '0.22', fade(.78, .93, 1, .85, (.4, .75, 1)), -.4, size_end='0.4'),
    'ice': lambda: line(SNOW, BLEND, f'0.5 + 0.3 * {R2}', '0.1', fade(1, 1, 1, 1, (.7, .88, 1)), -.3, spin=200),
    'wind': lambda: line(WISP, BLEND, f'0.3 + 0.2 * {R2}', '0.32', fade(1, 1, 1, .9, (.5, 1, .8)), .2, spin=500),
    'earth': lambda: line(SMOKE, BLEND, f'0.6 + 0.3 * {R2}', '0.35', fade(.7, .6, .46, .6, (.55, .48, .4)), .3, size_end='0.7'),
    'light': lambda: line(TWINKLE, ADD, f'0.4 + 0.3 * {R2}', '0.16', fade(1, .97, .8, 1, (1, .8, .35)), .3),
    'dark': lambda: line(WISP, BLEND, f'0.4 + 0.3 * {R2}', '0.3', fade(.72, .42, 1, .9, (.2, .04, .35)), .2, spin=400),
    'lightning': lambda: line(SPARKS, ADD, f'0.1 + 0.12 * {R2}', '0.35', fade(.95, .98, 1, 1, (.5, .7, 1)), 0),
    'heal': lambda: line(TWINKLE, ADD, f'0.5 + 0.3 * {R2}', '0.15', fade(.85, 1, .85, 1, (.4, 1, .6)), .5),
}


def kit_particle(element, suffix, material, components):
    return {'format_version': '1.10.0', 'particle_effect': {
        'description': {'identifier': f'advance_magic:kit_{element}_{suffix}',
                        'basic_render_parameters': {'material': material, 'texture': 'textures/particle/fx_atlas'}},
        'components': components}}


def kit_layers():
    """{element: [suffix, ...]} in send order, e.g. burst -> burst_a, burst_b, burst_c."""
    return {element: list(element_kit(element)) + ['line'] for element in TINTS}


# --- writer ------------------------------------------------------------------------------------------

def build(java_pack=None):
    """(java_files, bedrock_files) as {pack path: bytes}."""
    sprites = art.sprites()
    java, bedrock = {}, {}
    pngs = {name: encode(image) for name, image in sprites.items()}
    pngs['fx_atlas'] = encode(art.atlas())
    for name, png in pngs.items():
        bedrock[f'textures/particle/{name}.png'] = png
        if name == 'fx_atlas':
            continue
        java[f'assets/advance_magic/textures/effect/{name}.png'] = png
        face = {'texture': '#art', 'uv': [0, 0, 16, 16]}
        if name == 'fx_beam':
            face['tintindex'] = 0
        java[f'assets/advance_magic/models/effect/{name}.json'] = dumps({
            'ambientocclusion': False, 'textures': {'art': f'advance_magic:effect/{name}'},
            'elements': [{'from': [0, 0, 7.99], 'to': [16, 16, 8.01], 'shade': False, 'faces': {'north': face, 'south': face}}]})
        if name == 'fx_beam':
            from vfx_sprite_assets import TINTS as COLORS
            for tint, color in COLORS.items():
                java[f'assets/advance_magic/items/fx_beam_{tint}.json'] = dumps({'model': {
                    'type': 'minecraft:model', 'model': 'advance_magic:effect/fx_beam',
                    'tints': [{'type': 'minecraft:constant', 'value': (color[0] << 16) | (color[1] << 8) | color[2]}]}})
        else:
            java[f'assets/advance_magic/items/{name}.json'] = dumps({'model': {'type': 'minecraft:model', 'model': f'advance_magic:effect/{name}'}})
    for name, image in sprites.items():
        for mode in MODES:
            bedrock[f'particles/fxs_{name}_{mode}.particle.json'] = dumps(sprite_particle(name, name, image.width, image.height, mode))
    for name in LEGACY:
        size = 512 if name.startswith('vfx_sigil') else 128 if name == 'vfx_spark' else 256
        for mode in MODES:
            bedrock[f'particles/fxs_{name}_{mode}.particle.json'] = dumps(sprite_particle(name, name, size, size, mode))
    for element in TINTS:
        kit = element_kit(element)
        kit['line'] = LINES[element]()
        for suffix, (material, components) in kit.items():
            bedrock[f'particles/kit_{element}_{suffix}.particle.json'] = dumps(kit_particle(element, suffix, material, components))
    # Tracked (redrawn) planes for the kit art, in the 1.37 vfx_* format.
    from vfx_sprite_assets import particle as plane_particle, TINTS as COLORS
    for name, image in sprites.items():
        tints = COLORS.items() if name == 'fx_beam' else [(None, (255, 255, 255))]
        for tint, color in tints:
            base = name if tint is None else f'{name}_{tint}'
            for mode, suffix in (('plane', ''), ('flat', '_flat'), ('bb', '_bb')):
                bedrock[f'particles/{base}{suffix}.particle.json'] = dumps(
                    plane_particle(f'advance_magic:{base}{suffix}', f'textures/particle/{name}', image.width, color, mode))
    return java, bedrock


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    java, bedrock = build()
    for side, files in (('java', java), ('bedrock', bedrock)):
        for path, data in files.items():
            target = args.output / side / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
    print(json.dumps({'java_entries': len(java), 'bedrock_entries': len(bedrock),
                      'java_bytes': sum(map(len, java.values())), 'bedrock_bytes': sum(map(len, bedrock.values())),
                      'kit_layers': kit_layers()}, indent=1))
