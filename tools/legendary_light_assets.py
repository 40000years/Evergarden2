"""Heavenly Light artwork shared by Java displays and Bedrock particles.

Keep the texture names, native plane orientation and line_* variables aligned
with MythicLineVisuals.  Each Bedrock UV uses its source image's actual dimensions
so high resolution art can be revised without silently cropping it.
"""
from __future__ import annotations

import copy
import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'advance-magic/art/effects/legendary-light'
PLANES = (
    'elemental_legendary_light_sigil',
    'elemental_legendary_light_halo',
)
RAYS = ('elemental_legendary_light_beacon', 'elemental_legendary_light_shot')
MOTE_TEXTURE = 'elemental_legendary_light_mote'
MOTE_PARTICLE = 'elemental_legendary_light_motes'
TEXTURES = PLANES + RAYS + (MOTE_TEXTURE,)
PARTICLE_LIFETIME = .26
MOTES_PER_FRAME = 24


def source_texture(name):
    if name not in TEXTURES:
        raise ValueError(f'Unknown Legendary Light texture: {name}')
    path = ART / (name + '.png')
    with Image.open(path) as image:
        image.load()
        if image.mode != 'RGBA':
            raise ValueError(f'Effect texture must be RGBA: {path}')
        width, height = image.size
        if min(width, height) < 64:
            raise ValueError(f'Effect texture is too small: {path} {image.size}')
        alpha = image.getchannel('A')
        low, high = alpha.getextrema()
        if low != 0 or high == 0 or alpha.getbbox() is None:
            raise ValueError(f'Effect requires artwork with transparent surroundings: {path}')
    return path, width, height


def register_java(java, write_json):
    for name in PLANES + RAYS:
        source, _, _ = source_texture(name)
        texture = java / f'assets/advance_magic/textures/effect/{name}.png'
        texture.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, texture)
        # Full brightness is set by the ItemDisplay at runtime. Four unshaded
        # sides make tall ray ribbons readable around their whole long axis.
        faces = ('north', 'south') if name in PLANES else ('north', 'south', 'east', 'west')
        write_json(java / f'assets/advance_magic/models/effect/{name}.json', {
            'ambientocclusion': False,
            'textures': {'line': f'advance_magic:effect/{name}'},
            'elements': [{
                'from': [0, 0, 7.99] if name in PLANES else [0, 0, 0],
                'to': [16, 16, 8.01] if name in PLANES else [16, 16, 16], 'shade': False,
                'faces': {side: {'texture': '#line', 'uv': [0, 0, 16, 16]}
                          for side in faces},
            }],
        })
        write_json(java / f'assets/advance_magic/items/{name}.json', {
            'model': {'type': 'minecraft:model', 'model': f'advance_magic:effect/{name}'},
        })


def register_bedrock(bedrock, write_json):
    for name in PLANES + RAYS:
        source, width, height = source_texture(name)
        texture = bedrock / f'textures/particle/{name}.png'
        texture.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, texture)
        particle = {
            'format_version': '1.10.0',
            'particle_effect': {
                'description': {
                    'identifier': f'advance_magic:{name}',
                    'basic_render_parameters': {
                        'material': 'particles_blend' if name in PLANES else 'particles_add',
                        'texture': f'textures/particle/{name}',
                    },
                },
                'components': {
                    'minecraft:emitter_lifetime_once': {'active_time': .01},
                    'minecraft:emitter_rate_instant': {'num_particles': 1},
                    'minecraft:emitter_shape_point': {
                        'offset': ['variable.line_offset_x', 'variable.line_offset_y', 'variable.line_offset_z'],
                        'direction': [0, 0, 0],
                    },
                    # The server redraws at four tick intervals. Native spin
                    # carries the phase continuously between those updates; the
                    # overlap shares that phase instead of stacking static copies.
                    'minecraft:particle_lifetime_expression': {'max_lifetime': PARTICLE_LIFETIME},
                    'minecraft:particle_initial_speed': 0,
                    'minecraft:particle_initial_spin': {
                        'rotation': 'variable.line_rotation' if name in PLANES else 0,
                        'rotation_rate': 'variable.line_rotation_rate' if name in PLANES else 0,
                    },
                    'minecraft:particle_motion_dynamic': {},
                    'minecraft:particle_appearance_billboard': {
                        'size': ['variable.line_width * 0.5', 'variable.line_height * 0.5'],
                        'facing_camera_mode': 'direction_z' if name in PLANES else 'direction_y',
                        'direction': {'mode': 'custom', 'custom_direction': [
                            'variable.line_normal_x', 'variable.line_normal_y', 'variable.line_normal_z',
                        ]},
                        'uv': {'texture_width': width, 'texture_height': height,
                               'uv': [0, 0], 'uv_size': [width, height]},
                    },
                },
            },
        }
        write_json(bedrock / f'particles/{name}.particle.json', particle)
        if name not in PLANES:
            continue
        flat = copy.deepcopy(particle)
        flat['particle_effect']['description']['identifier'] = f'advance_magic:{name}_flat'
        billboard = flat['particle_effect']['components']['minecraft:particle_appearance_billboard']
        billboard['facing_camera_mode'] = 'emitter_transform_xz'
        del billboard['direction']
        write_json(bedrock / f'particles/{name}_flat.particle.json', flat)
    register_motes(bedrock, write_json)


def register_motes(bedrock, write_json):
    source, width, height = source_texture(MOTE_TEXTURE)
    texture = bedrock / f'textures/particle/{MOTE_TEXTURE}.png'
    texture.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, texture)
    # Origin is the field's FLOOR, line_width its full diameter and line_height
    # its upward height. The offset is the server's sub-block position correction.
    # Stable particle_random variables avoid frame-to-frame position jitter.
    angle = '(variable.particle_random_1 * 360 + variable.particle_age * 22)'
    radius = '(math.sqrt(variable.particle_random_2) * variable.line_width * 0.5)'
    alpha = ('variable.light_intensity * math.min(1, variable.particle_age * 8) * '
             'math.min(1, (variable.particle_lifetime - variable.particle_age) * 6)')
    colors = ['FFF4BD', 'F6C4FF', 'BDA5FF', '9DEEFF', '9CFFD0', 'FFC2D1']
    palette = [[round(int(color[index:index + 2], 16) / 255, 6) for index in (0, 2, 4)] + [alpha]
               for color in colors]
    write_json(bedrock / f'particles/{MOTE_PARTICLE}.particle.json', {
        'format_version': '1.10.0',
        'particle_effect': {
            'description': {
                'identifier': f'advance_magic:{MOTE_PARTICLE}',
                'basic_render_parameters': {'material': 'particles_blend',
                                            'texture': f'textures/particle/{MOTE_TEXTURE}'},
            },
            'components': {
                'minecraft:emitter_lifetime_once': {'active_time': .01},
                'minecraft:emitter_rate_instant': {'num_particles': MOTES_PER_FRAME},
                'minecraft:emitter_shape_point': {'offset': [0, 0, 0], 'direction': [0, 1, 0]},
                'minecraft:particle_lifetime_expression': {
                    'max_lifetime': '0.65 + variable.particle_random_4 * 0.4'},
                'minecraft:particle_initial_spin': {
                    'rotation': 'variable.particle_random_2 * 360',
                    'rotation_rate': '25 + variable.particle_random_1 * 35'},
                'minecraft:particle_motion_parametric': {
                    'relative_position': [
                        f'variable.line_offset_x + math.cos({angle}) * {radius}',
                        'variable.line_offset_y + 0.25 + variable.particle_random_3 * variable.line_height + variable.particle_age * 0.42',
                        f'variable.line_offset_z + math.sin({angle}) * {radius}',
                    ],
                },
                'minecraft:particle_appearance_billboard': {
                    'size': ['0.22 + variable.particle_random_4 * 0.12',
                             '0.22 + variable.particle_random_4 * 0.12'],
                    'facing_camera_mode': 'lookat_xyz',
                    'uv': {'texture_width': width, 'texture_height': height,
                           'uv': [0, 0], 'uv_size': [width, height]},
                },
                'minecraft:particle_appearance_tinting': {'color': {
                    'gradient': palette,
                    'interpolant': 'variable.particle_random_1',
                }},
            },
        },
    })
