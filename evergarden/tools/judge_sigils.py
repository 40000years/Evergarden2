"""Editable crimson spell geometry, using Solar/Chronos' textured plane pipeline."""
import io
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'tools'))
from mythic_line_art import Drawing

RED, ROSE, LIGHT = '#ff243e', '#ff6476', '#ffe0c5'
NAMES = ('judge_seal', 'judge_orbit', 'judge_sanctuary', 'judge_lane', 'judge_ray')


def drawing(name):
    d = Drawing()
    if name == 'judge_lane':
        for y in (8, 1016):
            d.path([(0, y), (1024, y)], 5, RED)
        for x in range(32, 1024, 64):
            d.path([(x-16, 40), (x, 180), (x+16, 40)], 3, ROSE)
            d.path([(x-16, 984), (x, 844), (x+16, 984)], 3, ROSE)
        d.path([(0, 512), (1024, 512)], 3, LIGHT)
        return d
    if name == 'judge_ray':
        for x, w, c in ((512, 18, LIGHT), (488, 6, ROSE), (536, 6, RED)):
            d.path([(x, 0), (x, 1024)], w, c)
        return d
    green = name == 'judge_sanctuary'
    red, rose, light = ('#42ff9b', '#9affc7', '#e0ffe9') if green else (RED, ROSE, LIGHT)
    for r, w, c in ((480, 5, red), (466, 2, light), (410, 3, rose)):
        d.circle(r, w, c)
    for i in range(48):
        a = math.tau*i/48
        d.path([d.polar(a, 472), d.polar(a, 456 if i % 4 else 432)], 3, light)
    if name == 'judge_orbit':
        return d
    for r, w, c in ((392, 2, red), (302, 3, light), (288, 2, rose), (150, 3, red), (126, 2, light)):
        d.circle(r, w, c)
    d.star(12, 5, 384, math.pi/12, 3, rose)
    d.star(6, 2, 284, math.pi/6, 4, red)
    d.star(4, 1, 116, math.pi/4, 3, light)
    for i in range(12):
        a = math.tau*i/12
        # Authored angular runes and curled filigree; no font dependency.
        d.path([d.polar(a-.035, 420), d.polar(a-.035, 447), d.polar(a+.035, 447),
                d.polar(a+.035, 420), d.polar(a-.035, 433)], 3, light)
        d.path([d.polar(a-.095, 322), d.polar(a-.055, 349), d.polar(a, 332),
                d.polar(a+.055, 349), d.polar(a+.095, 322)], 2, rose)
        d.path([d.polar(a, 161), d.polar(a-.07, 220), d.polar(a, 270),
                d.polar(a+.07, 220), d.polar(a, 161)], 3, light)
    d.circle(54, 3, light)
    d.star(8, 3, 90, 0, 2, red)
    return d


def assets(json_bytes):
    java, bedrock = {}, {}
    art = Path(__file__).resolve().parents[1] / 'art/judge'
    art.mkdir(parents=True, exist_ok=True)
    for name in NAMES:
        image, svg = drawing(name).render()
        data = io.BytesIO(); image.save(data, format='PNG')
        (art / (name+'.svg')).write_text(svg, encoding='utf8')
        (art / (name+'.png')).write_bytes(data.getvalue())
        java[f'assets/voidscape/textures/effect/{name}.png'] = data.getvalue()
        java[f'assets/voidscape/models/effect/{name}.json'] = json_bytes({
            'ambientocclusion': False, 'textures': {'seal': 'voidscape:effect/'+name},
            'elements': [{'from': [0,0,7.99], 'to': [16,16,8.01], 'shade': False,
                          'faces': {side: {'texture': '#seal', 'uv': [0,0,16,16]} for side in ('north','south')}}]})
        java[f'assets/voidscape/items/{name}.json'] = json_bytes({
            'model': {'type': 'minecraft:model', 'model': 'voidscape:effect/'+name}})
        bedrock[f'textures/particle/{name}.png'] = data.getvalue()
        for flat in (False, True):
            billboard = {'size': ['variable.line_width * 0.5','variable.line_height * 0.5'],
                         'facing_camera_mode': 'emitter_transform_xz' if flat else 'direction_z',
                         'uv': {'texture_width': 1024,'texture_height':1024,'uv':[0,0],'uv_size':[1024,1024]}}
            if not flat:
                billboard['direction'] = {'mode':'custom','custom_direction':[
                    'variable.line_normal_x','variable.line_normal_y','variable.line_normal_z']}
            suffix = '_flat' if flat else ''
            bedrock[f'particles/{name}{suffix}.particle.json'] = json_bytes({
                'format_version':'1.10.0','particle_effect':{
                    'description':{'identifier':'voidscape:'+name+suffix,
                                   'basic_render_parameters':{'material':'particles_blend','texture':'textures/particle/'+name}},
                    'components':{
                        'minecraft:emitter_lifetime_once':{'active_time':.01},
                        'minecraft:emitter_rate_instant':{'num_particles':1},
                        'minecraft:emitter_shape_point':{'offset':['variable.line_offset_x','variable.line_offset_y','variable.line_offset_z'],'direction':[0,0,0]},
                        'minecraft:particle_lifetime_expression':{'max_lifetime':.20},
                        'minecraft:particle_initial_speed':0,
                        'minecraft:particle_initial_spin':{'rotation':'variable.line_rotation','rotation_rate':0},
                        'minecraft:particle_motion_dynamic':{},'minecraft:particle_appearance_billboard':billboard}}})
    return java, bedrock
