"""Approved Fire/Wind textures and reused Ancient Judge hands.

The native PNG dimensions are retained, including the non-square water artwork.
Hand geometry and atlas bytes are reused from the existing boss packs.
"""
from __future__ import annotations

import copy
import json
import shutil
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'advance-magic/art/effects/legendary-elements-v1'
SPECS = (
    ('fire', 'meteor_rain', 'meteor'),
    ('wind', 'tempest_dominion', 'vortex'),
)
TEXTURES = {f'elemental_legendary_{element}_{role}': ART / element / f'{spell}_{role}.png'
            for element, spell, hero in SPECS for role in ('sigil', hero)
            if not (element == 'wind' and role == 'vortex')}
FLAT = {name for name in TEXTURES if name.endswith('_sigil')}
HANDS = ('judge_left', 'judge_left_light', 'judge_right', 'judge_right_light')
BEDROCK_VERSION = [2, 30, 0]


def source_texture(name):
    path = TEXTURES[name]
    with Image.open(path) as image:
        image.load()
        if image.mode != 'RGBA' or min(image.size) < 64:
            raise ValueError(f'Native RGBA texture required: {path}')
        low, high = image.getchannel('A').getextrema()
        if low != 0 or high == 0:
            raise ValueError(f'True transparent artwork required: {path}')
        return path, *image.size


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf8')


def register_java(java, write_json=write_json):
    for name in TEXTURES:
        source, _, _ = source_texture(name)
        target = java / f'assets/advance_magic/textures/effect/{name}.png'
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
        write_json(java / f'assets/advance_magic/models/effect/{name}.json', {
            'ambientocclusion': False, 'textures': {'line': f'advance_magic:effect/{name}'},
            'elements': [{'from': [0, 0, 7.99], 'to': [16, 16, 8.01], 'shade': False,
                          'faces': {side: {'texture': '#line', 'uv': [0, 0, 16, 16]}
                                    for side in ('north', 'south')}}],
        })
        write_json(java / f'assets/advance_magic/items/{name}.json', {
            'model': {'type': 'minecraft:model', 'model': f'advance_magic:effect/{name}'},
        })


def register_bedrock(bedrock, write_json=write_json):
    for name in TEXTURES:
        source, width, height = source_texture(name)
        target = bedrock / f'textures/particle/{name}.png'
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
        particle = {
            'format_version': '1.10.0',
            'particle_effect': {
                'description': {'identifier': f'advance_magic:{name}',
                                'basic_render_parameters': {'material': 'particles_blend',
                                                            'texture': f'textures/particle/{name}'}},
                'components': {
                    'minecraft:emitter_lifetime_once': {'active_time': .01},
                    'minecraft:emitter_rate_instant': {'num_particles': 1},
                    'minecraft:emitter_shape_point': {
                        'offset': ['variable.line_offset_x', 'variable.line_offset_y', 'variable.line_offset_z'],
                        'direction': [0, 0, 0]},
                    'minecraft:particle_lifetime_expression': {'max_lifetime': .26},
                    'minecraft:particle_initial_speed': 0,
                    'minecraft:particle_initial_spin': {
                        'rotation': 'variable.line_rotation', 'rotation_rate': 0},
                    'minecraft:particle_motion_dynamic': {},
                    'minecraft:particle_appearance_billboard': {
                        'size': ['variable.line_width * 0.5', 'variable.line_height * 0.5'],
                        'facing_camera_mode': 'direction_z',
                        'direction': {'mode': 'custom', 'custom_direction': [
                            'variable.line_normal_x', 'variable.line_normal_y', 'variable.line_normal_z']},
                        'uv': {'texture_width': width, 'texture_height': height,
                               'uv': [0, 0], 'uv_size': [width, height]}},
                },
            },
        }
        write_json(bedrock / f'particles/{name}.particle.json', particle)
        if name in FLAT:
            flat = copy.deepcopy(particle)
            flat['particle_effect']['description']['identifier'] = f'advance_magic:{name}_flat'
            billboard = flat['particle_effect']['components']['minecraft:particle_appearance_billboard']
            billboard['facing_camera_mode'] = 'emitter_transform_xz'
            del billboard['direction']
            if name.endswith('_sigil'):
                flat['particle_effect']['components']['minecraft:particle_initial_spin']['rotation_rate'] = 'variable.line_rotation_rate'
            write_json(bedrock / f'particles/{name}_flat.particle.json', flat)


def register_hands(java, bedrock, atlas, definitions, write_json=write_json):
    """Copy exact hand assets and merge only their four inventory definitions."""
    with zipfile.ZipFile(ROOT / 'evergarden/dist/judge-java.zip') as source:
        paths = [f'assets/voidscape/{kind}/{name}.json'
                 for name in HANDS for kind in ('items', 'models/item')]
        paths.append('assets/voidscape/textures/item/judge_atlas.png')
        for relative in paths:
            target = java / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(source.read(relative))
    with zipfile.ZipFile(ROOT / 'evergarden/dist/evergarden-bedrock.mcpack') as source:
        paths = [f'{folder}/{name}{suffix}' for name in HANDS
                 for folder, suffix in (('attachables', '.json'), ('models/entity', '.geo.json'))]
        paths.append('textures/items/judge_atlas.png')
        for relative in paths:
            target = bedrock / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(source.read(relative))
        source_atlas = json.loads(source.read('textures/item_texture.json'))['texture_data']
        for name in HANDS:
            atlas['voidscape.' + name] = copy.deepcopy(source_atlas['voidscape.' + name])
        controller_path = bedrock / 'render_controllers/judge.json'
        controllers = json.loads(source.read('render_controllers/judge.json'))
        if controller_path.is_file():
            current = json.loads(controller_path.read_text(encoding='utf8'))
            current.setdefault('render_controllers', {}).update(controllers['render_controllers'])
            controllers = current
        write_json(controller_path, controllers)
    mappings = json.loads((ROOT / 'evergarden/dist/geyser-mappings.json').read_text(encoding='utf8'))
    selected = [copy.deepcopy(row) for row in mappings['items']['minecraft:iron_helmet']
                if row.get('model') in {f'voidscape:{name}' for name in HANDS}]
    if len(selected) != len(HANDS):
        raise ValueError('Existing boss hand inventory definitions are incomplete')
    rows = definitions.setdefault('minecraft:iron_helmet', [])
    ids = {row['model'] for row in selected}
    rows[:] = [row for row in rows if row.get('model') not in ids]
    rows.extend(selected)


def register(java, bedrock, atlas, definitions):
    """Full-builder entry point; atlas is texture_data, definitions is items."""
    register_java(java)
    register_bedrock(bedrock)
    register_hands(java, bedrock, atlas, definitions)
