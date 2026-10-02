"""Continuous world-space celestial planes, clock hands and attack ribbons."""
import shutil
import copy
import json
from pathlib import Path

ART=Path(__file__).resolve().parents[1]/'advance-magic/art/effects'
PLANES=('solar_corona','solar_orbit','chronos_dial','chronos_minute','chronos_hour','chronos_ripple')
RAYS=('solar_ray','chronos_ray','chronos_echo')


def register_java(java,write_json):
    for name in PLANES+RAYS:
        texture=java/f'assets/advance_magic/textures/effect/{name}.png'
        texture.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(ART/f'{name}.png',texture)
        faces=('north','south') if name in PLANES else ('north','south','east','west')
        write_json(java/f'assets/advance_magic/models/effect/{name}.json',{
            'ambientocclusion':False,'textures':{'line':f'advance_magic:effect/{name}'},
            'elements':[{'from':[0,0,7.99] if name in PLANES else [0,0,0],
                         'to':[16,16,8.01] if name in PLANES else [16,16,16], 'shade':False,
                         'faces':{side:{'texture':'#line','uv':[0,0,16,16]} for side in faces}}]})
        write_json(java/f'assets/advance_magic/items/{name}.json',{
            'model':{'type':'minecraft:model','model':f'advance_magic:effect/{name}'}})


def register_bedrock(bedrock,write_json):
    for name in PLANES+RAYS:
        texture=bedrock/f'textures/particle/{name}.png'
        texture.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(ART/f'{name}.png',texture)
        size=1024 if name in PLANES else 256
        write_json(bedrock/f'particles/{name}.particle.json',{
            'format_version':'1.10.0','particle_effect':{
                'description':{'identifier':f'advance_magic:{name}',
                    # Fixed world planes must be visible from below and behind.
                    # particles_blend explicitly disables culling and preserves
                    # the PNG's translucent strokes; keep the known working material.
                    'basic_render_parameters':{'material':'particles_blend','texture':f'textures/particle/{name}'}},
                'components':{
                    'minecraft:emitter_lifetime_once':{'active_time':.01},
                    'minecraft:emitter_rate_instant':{'num_particles':1},
                    # The packet's emitter stays near each viewer. The particle
                    # itself is born at its real world-space plane/ray midpoint.
                    'minecraft:emitter_shape_point':{'offset':[
                        'variable.line_offset_x','variable.line_offset_y','variable.line_offset_z'],'direction':[0,0,0]},
                    # Redraw every four/two ticks. Longer lifetimes leave a second
                    # old dial/hand/ray on screen and make Bedrock look doubled.
                    'minecraft:particle_lifetime_expression':{'max_lifetime':.20 if name in PLANES else .10},
                    'minecraft:particle_initial_speed':0,
                    'minecraft:particle_initial_spin':{'rotation':'variable.line_rotation' if name in PLANES else 0,'rotation_rate':0},
                    'minecraft:particle_motion_dynamic':{},
                    'minecraft:particle_appearance_billboard':{
                        'size':['variable.line_width * 0.5','variable.line_height * 0.5'],
                        # For rays the custom vector is the long Y axis, not the
                        # billboard normal. This anchors both ends for all viewers.
                        'facing_camera_mode':'direction_z' if name in PLANES else 'direction_y',
                        # The engine enum is "custom"; "custom_direction" names
                        # the vector field only (see Mojang's schema and shriek).
                        'direction':{'mode':'custom','custom_direction':[
                            'variable.line_normal_x','variable.line_normal_y','variable.line_normal_z']},
                        'uv':{'texture_width':size,'texture_height':size,'uv':[0,0],'uv_size':[size,size]}}}}})
        # A horizontal plane has no projected world-up vector. Give it an
        # explicit XZ basis instead of passing a singular direction_z normal.
        particle=json.loads((bedrock/f'particles/{name}.particle.json').read_text())
        flat=copy.deepcopy(particle)
        flat['particle_effect']['description']['identifier']=f'advance_magic:{name}_flat'
        billboard=flat['particle_effect']['components']['minecraft:particle_appearance_billboard']
        if name in PLANES:
            billboard['facing_camera_mode']='emitter_transform_xz'
            del billboard['direction']
        write_json(bedrock/f'particles/{name}_flat.particle.json',flat)
