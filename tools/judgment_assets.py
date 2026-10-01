"""World-space Judgment beam: native Java beacon texture and a Bedrock emitter."""
import shutil
from pathlib import Path

ART=Path(__file__).resolve().parents[1]/'advance-magic/art/effects'


def register_java(java, write_json):
    for variant in range(2):
        name=f'judgment_seal_{variant}'
        texture=java/f'assets/advance_magic/textures/effect/{name}.png'
        texture.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(ART/f'{name}.png',texture)
        write_json(java/f'assets/advance_magic/models/effect/{name}.json', {
            'ambientocclusion':False,'textures':{'seal':f'advance_magic:effect/{name}'},
            'elements':[{'from':[0,7.99,0],'to':[16,8.01,16],'shade':False,
                         'faces':{side:{'texture':'#seal','uv':[0,0,16,16]} for side in ('up','down')}}]})
        write_json(java/f'assets/advance_magic/items/{name}.json', {
            'model':{'type':'minecraft:model','model':f'advance_magic:effect/{name}'}})
    # Both layers use the game's own beacon texture. Constant tint supplies gold.
    faces = {side: {'texture': '#beam', 'uv': [0, 0, 16, 16], 'tintindex': 0}
             for side in ('north', 'south', 'east', 'west')}
    write_json(java / 'assets/advance_magic/models/effect/judgment_beam.json', {
        'ambientocclusion': False,
        'textures': {'beam': 'minecraft:entity/beacon_beam', 'particle': 'minecraft:entity/beacon_beam'},
        'elements': [
            {'from': [0, 0, 0], 'to': [16, 16, 16], 'shade': False, 'faces': faces},
            {'from': [5, 0, 5], 'to': [11, 16, 11], 'shade': False,
             'faces': {side: dict(face, tintindex=1) for side, face in faces.items()}}]})
    write_json(java / 'assets/advance_magic/items/judgment_beam.json', {
        'model': {'type': 'minecraft:model', 'model': 'advance_magic:effect/judgment_beam',
                  'tints': [{'type': 'minecraft:constant', 'value': 0xFFE55C},
                            {'type': 'minecraft:constant', 'value': 0xFFF9DA}]}})


def register_bedrock(bedrock, write_json, png):
    for variant in range(2):
        name=f'judgment_seal_{variant}'
        texture=bedrock/f'textures/particle/{name}.png'
        texture.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(ART/f'{name}.png',texture)
        write_json(bedrock/f'particles/{name}.particle.json', {
            'format_version':'1.10.0','particle_effect':{
                'description':{'identifier':f'advance_magic:{name}',
                    'basic_render_parameters':{'material':'particles_blend','texture':f'textures/particle/{name}'}},
                'components':{
                    'minecraft:emitter_lifetime_once':{'active_time':0.01},
                    'minecraft:emitter_rate_instant':{'num_particles':1},
                    'minecraft:emitter_shape_point':{'offset':[0,0,0],'direction':[0,1,0]},
                    'minecraft:particle_lifetime_expression':{'max_lifetime':0.22},
                    'minecraft:particle_initial_speed':0,
                    'minecraft:particle_initial_spin':{'rotation':'variable.seal_rotation','rotation_rate':0},
                    'minecraft:particle_motion_dynamic':{},
                    'minecraft:particle_appearance_billboard':{
                        'size':['variable.seal_diameter * 0.5','variable.seal_diameter * 0.5'],
                        'facing_camera_mode':'emitter_transform_xz',
                        'uv':{'texture_width':1024,'texture_height':1024,'uv':[0,0],'uv_size':[1024,1024]}}}}})
    # A narrow longitudinal beacon-style ribbon. UV crops its transparent border.
    pixels = []
    for y in range(128):
        row = []
        for x in range(128):
            if not 48 <= x < 80:
                row.append((0, 0, 0, 0))
            else:
                center = abs(x - 63.5)
                if center < 5:
                    row.append((255, 255, 231, 245))
                else:
                    stripe = 12 if (x + y // 5) % 7 < 2 else 0
                    row.append((255, 223 + stripe, 65 + stripe, 170 if center < 12 else 80))
        pixels.append(row)
    png(bedrock / 'textures/particle/judgment_beam.png', pixels)
    write_json(bedrock / 'particles/judgment_beam.particle.json', {
        'format_version': '1.10.0', 'particle_effect': {
            'description': {'identifier': 'advance_magic:judgment_beam',
                            'basic_render_parameters': {'material': 'particles_blend',
                                                        'texture': 'textures/particle/judgment_beam'}},
            'components': {
                'minecraft:emitter_lifetime_once': {'active_time': 0.01},
                'minecraft:emitter_rate_instant': {'num_particles': 1},
                'minecraft:emitter_shape_point': {'offset': [0, 0, 0], 'direction': [0, 0, 0]},
                'minecraft:particle_lifetime_expression': {'max_lifetime': 0.25},
                'minecraft:particle_initial_speed': 0,
                'minecraft:particle_motion_dynamic': {},
                'minecraft:particle_appearance_billboard': {
                    'size': ['variable.beam_width * 0.5', 'variable.beam_height * 0.5'],
                    'facing_camera_mode': 'lookat_y',
                    'uv': {'texture_width': 128, 'texture_height': 128,
                           'uv': [48, 0], 'uv_size': [32, 128]}}}}})
