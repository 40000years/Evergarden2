"""Register authored legendary line-art planes/ribbons for both Minecraft clients."""
import copy
import shutil
from pathlib import Path

ART=Path(__file__).resolve().parents[1]/'advance-magic/art/effects/legendary'
PLANES=('verdant_seal','verdant_dragon','verdant_beam_core','verdant_halo',
        'lunar_seal','lunar_orbit','lunar_constellation',
        'crimson_seal','crimson_heart','crimson_shard')
RAYS=('verdant_ray','lunar_ray','crimson_ray')


def register_java(java,write_json):
    for name in PLANES+RAYS:
        texture=java/f'assets/advance_magic/textures/effect/{name}.png'
        texture.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(ART/f'{name}.png',texture)
        # Keep the established north/south native plane orientation: runtime
        # rotates ItemDisplays to the desired normal, and sets full brightness.
        faces=('north','south') if name in PLANES else ('north','south','east','west')
        write_json(java/f'assets/advance_magic/models/effect/{name}.json',{
            'ambientocclusion':False,'textures':{'line':f'advance_magic:effect/{name}'},
            'elements':[{'from':[0,0,7.99] if name in PLANES else [0,0,0],
                         'to':[16,16,8.01] if name in PLANES else [16,16,16],
                         'shade':False,
                         'faces':{side:{'texture':'#line','uv':[0,0,16,16]} for side in faces}}]})
        write_json(java/f'assets/advance_magic/items/{name}.json',{
            'model':{'type':'minecraft:model','model':f'advance_magic:effect/{name}'}})


def register_bedrock(bedrock,write_json):
    for name in PLANES+RAYS:
        texture=bedrock/f'textures/particle/{name}.png'
        texture.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(ART/f'{name}.png',texture)
        size=1024 if name in PLANES else 256
        particle={
            'format_version':'1.10.0','particle_effect':{
                'description':{'identifier':f'advance_magic:{name}',
                    'basic_render_parameters':{'material':'particles_blend',
                                               'texture':f'textures/particle/{name}'}},
                'components':{
                    'minecraft:emitter_lifetime_once':{'active_time':.01},
                    'minecraft:emitter_rate_instant':{'num_particles':1},
                    'minecraft:emitter_shape_point':{
                        'offset':['variable.line_offset_x','variable.line_offset_y','variable.line_offset_z'],
                        'direction':[0,0,0]},
                    # Legendary native planes and ribbons both redraw every four
                    # ticks. Equal .20s lifetimes avoid gaps or doubled geometry.
                    'minecraft:particle_lifetime_expression':{'max_lifetime':.20},
                    'minecraft:particle_initial_speed':0,
                    'minecraft:particle_initial_spin':{
                        'rotation':'variable.line_rotation' if name in PLANES else 0,'rotation_rate':0},
                    'minecraft:particle_motion_dynamic':{},
                    'minecraft:particle_appearance_billboard':{
                        'size':['variable.line_width * 0.5','variable.line_height * 0.5'],
                        'facing_camera_mode':'direction_z' if name in PLANES else 'direction_y',
                        'direction':{'mode':'custom','custom_direction':[
                            'variable.line_normal_x','variable.line_normal_y','variable.line_normal_z']},
                        'uv':{'texture_width':size,'texture_height':size,'uv':[0,0],'uv_size':[size,size]}}}}}
        write_json(bedrock/f'particles/{name}.particle.json',particle)
        # Explicit XZ basis avoids an undefined projection of world-up when a
        # horizontal seal's normal points straight upward or downward.
        flat=copy.deepcopy(particle)
        flat['particle_effect']['description']['identifier']=f'advance_magic:{name}_flat'
        if name in PLANES:
            billboard=flat['particle_effect']['components']['minecraft:particle_appearance_billboard']
            billboard['facing_camera_mode']='emitter_transform_xz'
            del billboard['direction']
        write_json(bedrock/f'particles/{name}_flat.particle.json',flat)
