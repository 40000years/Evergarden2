"""Bedrock counterpart of the new Mythic spells' Java DustOptions points."""
TINTS = {'gold': 0xFFD34D, 'white': 0xFFF2AF, 'orange': 0xFF732D,
         'cyan': 0x72EDFF, 'violet': 0xA36BFF}


def register(bedrock, write_json, png):
    # A small hard-edged white dot, tinted by each emitter. No external textures.
    pixels = [[(255, 255, 255, 255) if (x-63.5)**2+(y-63.5)**2 <= 26**2
               else (0, 0, 0, 0) for x in range(128)] for y in range(128)]
    png(bedrock / 'textures/particle/mythic_dot.png', pixels)
    for name, rgb in TINTS.items():
        tint = [((rgb >> shift) & 255)/255 for shift in (16, 8, 0)] + [1]
        write_json(bedrock / f'particles/mythic_{name}.particle.json', {
            'format_version': '1.10.0',
            'particle_effect': {
                'description': {
                    'identifier': f'advance_magic:mythic_{name}',
                    'basic_render_parameters': {'material': 'particles_blend',
                                                'texture': 'textures/particle/mythic_dot'}},
                'components': {
                    'minecraft:emitter_lifetime_once': {'active_time': 0.01},
                    'minecraft:emitter_rate_instant': {'num_particles': 1},
                    'minecraft:emitter_shape_point': {'offset': [0, 0, 0], 'direction': [0, 0, 0]},
                    'minecraft:particle_lifetime_expression': {'max_lifetime': 0.8},
                    'minecraft:particle_initial_speed': 0,
                    'minecraft:particle_motion_dynamic': {},
                    'minecraft:particle_appearance_billboard': {
                        'size': ['variable.magic_size * 0.24', 'variable.magic_size * 0.24'],
                        'facing_camera_mode': 'rotate_xyz',
                        'uv': {'texture_width': 128, 'texture_height': 128,
                               'uv': [0, 0], 'uv_size': [128, 128]}},
                    'minecraft:particle_appearance_tinting': {'color': tint}}}})
