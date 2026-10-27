"""Export the Java Legendary block item faces onto a centred Bedrock cube rig.

The native falling-block renderer does not implement display quaternions or
display interpolation. This client entity instead consumes complete from/to
poses and interpolates position, size and shortest-path quaternion rotation.
"""
import io
import json
import math
import os
from pathlib import Path
import zipfile

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / 'advance-magic/src/main/resources/legendary-blocks.json'
TYPE = 'advance_magic:legendary_block'
FACES = ('north', 'south', 'east', 'west', 'up', 'down')
CHANNELS = ('x', 'y', 'z', 'qx', 'qy', 'qz', 'qw', 'size')
NORMALS = ((0, 0, -1), (0, 0, 1), (1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0))


def face_shade(normal):
    """Java lights block items with two fixed directions (Lighting DIFFUSE_LIGHT_0/1): 0.4 ambient + 0.6 diffuse."""
    lights = [np.array(v) / np.linalg.norm(v) for v in ((.2, 1, -.7), (-.2, 1, .7))]
    return min(1.0, .4 + .6 * sum(max(0.0, float(np.dot(light, normal))) for light in lights))


# The rig renders with ignore_lighting, so the Java face shading is baked into each face instead.
SHADE = {face: face_shade(normal) for face, normal in zip(FACES, NORMALS)}


def shaded(strip):
    pixels = np.asarray(strip).astype(np.float64)
    for index, face in enumerate(FACES):
        pixels[:, index * 16:(index + 1) * 16, :3] *= SHADE[face]
    return Image.fromarray(np.clip(np.rint(pixels), 0, 255).astype(np.uint8), 'RGBA')


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, separators=(',', ':')) + '\n', encoding='utf8')


def client_jar():
    path = Path(os.environ.get('APPDATA', Path.home() / 'AppData/Roaming')) / '.minecraft/versions/26.3/26.3.jar'
    if not path.is_file():
        raise FileNotFoundError('Java 26.3 assets are required to export the exact block item textures: ' + str(path))
    return path


def model(archive, name):
    name = name.removeprefix('minecraft:')
    data = json.loads(archive.read('assets/minecraft/models/' + name + '.json'))
    result = model(archive, data['parent']) if 'parent' in data else {}
    result = {**result, **data, 'textures': {**result.get('textures', {}), **data.get('textures', {})}}
    return result


def tint(rgb, color):
    pixels = np.asarray(rgb, dtype=np.uint8).copy()
    pixels[:, :, :3] = (pixels[:, :, :3].astype(float) * np.array(color)[None, None, :] / 255).round().astype(np.uint8)
    return Image.fromarray(pixels)


def texture(archive, name):
    if isinstance(name, dict):
        name = name['sprite']
    name = name.removeprefix('minecraft:')
    path = 'assets/minecraft/textures/' + name + '.png'
    image = Image.open(io.BytesIO(archive.read(path))).convert('RGBA')
    width = image.width
    frames = [image.crop((0, y, width, y + width)) for y in range(0, image.height, width)]
    metadata = json.loads(archive.read(path + '.mcmeta')).get('animation', {}) if path + '.mcmeta' in archive.namelist() else {}
    default = metadata.get('frametime', 1)
    timeline = [(entry, default) if isinstance(entry, int) else (entry['index'], entry.get('time', default))
                for entry in metadata.get('frames', list(range(len(frames))))]
    # Preserve vanilla keyframes/timing without a huge 6,600-row prismarine atlas.
    return frames, timeline, metadata.get('interpolate', False)


def face_frames(archive, material):
    item = json.loads(archive.read('assets/minecraft/items/' + material + '.json'))['model']
    model_name = item['model'] if isinstance(item, dict) and 'model' in item else ('block/' + material)
    if ('assets/minecraft/models/' + model_name.removeprefix('minecraft:') + '.json') not in archive.namelist():
        model_name = 'block/' + material
    data = model(archive, model_name)
    faces = {face: [] for face in FACES}
    color = (255, 255, 255)
    if material == 'oak_leaves':
        value = item['tints'][0]['value'] & 0xffffff
        color = (value >> 16, (value >> 8) & 255, value & 255)
    elif material == 'grass_block':
        # Vanilla GrassColor: temperature .5, downfall 1 -> x=127, y=127.
        cmap = Image.open(io.BytesIO(archive.read('assets/minecraft/textures/colormap/grass.png'))).convert('RGB')
        color = cmap.getpixel((127, 127))
    for element in data['elements']:
        assert element['from'] == [0, 0, 0] and element['to'] == [16, 16, 16], material
        for side, face in element['faces'].items():
            reference = face['texture']
            while isinstance(reference, str) and reference.startswith('#'):
                reference = data['textures'][reference[1:]]
            frames, timeline, smooth = texture(archive, reference)
            if 'tintindex' in face:
                frames = [tint(frame, color) for frame in frames]
            faces[side].append((frames, timeline, smooth))
    timelines = [layers[0][1] for layers in faces.values()]
    animated = next((timeline for timeline in timelines if len(timeline) > 1), [(0, 1)])
    # All animated faces of each catalog item share a timeline; static overlays remain fixed.
    count = len(animated)
    images = []
    for index in range(count):
        atlas = Image.new('RGBA', (96, 16))
        for side_index, side in enumerate(FACES):
            tile = Image.new('RGBA', (16, 16))
            for frames, timeline, _ in faces[side]:
                frame_id = timeline[index % len(timeline)][0]
                tile.alpha_composite(frames[frame_id].resize((16, 16), Image.Resampling.NEAREST))
            atlas.paste(tile, (side_index * 16, 0))
        images.append(atlas)
    strip = Image.new('RGBA', (96, 16 * count))
    for i, image in enumerate(images):
        strip.paste(image, (0, i * 16))
    times = [time for _, time in animated]
    assert len(set(times)) == 1, 'Nonuniform block animation needs an explicit frame expression'
    return strip, times[0], count


def pre_animation():
    # A revision timestamps a complete pose atomically. Initial poses snap (duration=0).
    steps = ["v.changed=v.rev != q.property('advance_magic:revision');",
             'v.start=v.changed ? q.life_time : v.start;',
             "v.rev=q.property('advance_magic:revision');",
             "v.t=q.property('advance_magic:duration') > 0 ? math.clamp((q.life_time-v.start)/q.property('advance_magic:duration'),0,1) : 1;"]
    for channel in ('x', 'y', 'z', 'size'):
        steps.append(f"v.{channel}=math.lerp(q.property('advance_magic:from_{channel}'),q.property('advance_magic:to_{channel}'),v.t);")
    steps.append("v.dot=math.clamp(" + '+'.join(f"q.property('advance_magic:from_q{c}')*q.property('advance_magic:to_q{c}')" for c in 'xyzw') + ",-1,1);")
    steps += ['v.theta=math.acos(v.dot);', 'v.sine=math.sin(v.theta);',
              'v.a=v.dot > 0.9995 ? 1-v.t : math.sin((1-v.t)*v.theta)/v.sine;',
              'v.b=v.dot > 0.9995 ? v.t : math.sin(v.t*v.theta)/v.sine;']
    for c in 'xyzw':
        steps.append(f"v.q{c}=v.a*q.property('advance_magic:from_q{c}')+v.b*q.property('advance_magic:to_q{c}');")
    steps.append('v.norm=math.sqrt(v.qx*v.qx+v.qy*v.qy+v.qz*v.qz+v.qw*v.qw);')
    steps.extend(f'v.q{c}=v.q{c}/v.norm;' for c in 'xyzw')
    # atan2 with the matrix's cosine is stable near +/-90 degrees, including
    # single-precision Molang. asin(sin_y) loses the remaining tilt there.
    steps += ['v.m00=1-2*(v.qy*v.qy+v.qz*v.qz);',
              'v.m10=2*(v.qx*v.qy+v.qw*v.qz);',
              'v.m20=2*(v.qx*v.qz-v.qw*v.qy);',
              'v.cy=math.sqrt(v.m00*v.m00+v.m10*v.m10);',
              'v.rx=v.cy<0.000001 ? 0 : math.atan2(2*(v.qw*v.qx+v.qy*v.qz),1-2*(v.qx*v.qx+v.qy*v.qy));',
              'v.ry=math.atan2(-v.m20,v.cy);',
              'v.rz=v.cy<0.000001 ? math.atan2(2*(v.qw*v.qz-v.qx*v.qy),1-2*(v.qx*v.qx+v.qz*v.qz)) : math.atan2(v.m10,v.m00);']
    return steps


def write_assets(bedrock, archive_path=None):
    bedrock = Path(bedrock)
    catalog = json.loads(CATALOG.read_text())
    timings, textures, translucent = [], {}, []
    with zipfile.ZipFile(archive_path or client_jar()) as java:
        for material in catalog:
            image, duration, count = face_frames(java, material)
            image = shaded(image)
            # Partial alpha (ice, stained glass) blends like Java's translucent layer; fully clear
            # pixels (leaves) are cut out. Emissive-alpha would draw both as solid.
            alpha = np.asarray(image)[..., 3]
            translucent.append(bool(((alpha > 0) & (alpha < 255)).any()))
            target = bedrock / f'textures/legendary_blocks/{material}.png'
            target.parent.mkdir(parents=True, exist_ok=True)
            image.save(target, optimize=True)
            textures[material] = f'textures/legendary_blocks/{material}'
            timings.append((duration, count))
    def select(values):
        return '(' + ' : '.join(f"q.property('advance_magic:material') == {i} ? ({value})" for i, value in enumerate(values[:-1])) + ' : (' + str(values[-1]) + '))'
    # Cube UVs follow Blockbench's Bedrock export: top/bottom UVs have both axes flipped.
    uv = {side: {'uv': [i*16, 0], 'uv_size': [16, 16]} for i, side in enumerate(FACES)}
    for side in ('up', 'down'):
        uv[side]['uv'] = [uv[side]['uv'][0]+16, 16]
        uv[side]['uv_size'] = [-16, -16]
    write(bedrock / 'models/entity/legendary_block.geo.json', {'format_version':'1.12.0','minecraft:geometry':[{
        'description':{'identifier':'geometry.advance_magic.legendary_block','texture_width':96,'texture_height':16,
                       'visible_bounds_width':256,'visible_bounds_height':256,'visible_bounds_offset':[0,0,0]},
        'bones':[{'name':'root','pivot':[0,0,0],'cubes':[{'origin':[-8,-8,-8],'size':[16,16,16],'uv':uv}]}]}]})
    write(bedrock / 'animations/legendary_block.animation.json', {'format_version':'1.8.0','animations':{
        'animation.advance_magic.legendary_block':{'loop':True,'bones':{'root':{
            'position':['-16*v.x','16*v.y','16*v.z'], 'rotation':['-v.rx','-v.ry','v.rz'],'scale':'v.size'}}}}})
    write(bedrock / 'entity/legendary_block.entity.json', {'format_version':'1.10.0','minecraft:client_entity':{'description':{
        'identifier':TYPE,'materials':{'default':'advance_magic_legendary_blocks','translucent':'advance_magic_legendary_blocks_blend'},'textures':textures,
        'geometry':{'default':'geometry.advance_magic.legendary_block'},
        'animations':{'pose':'animation.advance_magic.legendary_block'},
        'scripts':{'initialize':['v.rev=-1;'],'pre_animation':pre_animation(),'animate':['pose']},
        'render_controllers':['controller.render.advance_magic.legendary_block']}}})
    write(bedrock / 'render_controllers/legendary_block.render.json', {'format_version':'1.8.0','render_controllers':{
        'controller.render.advance_magic.legendary_block':{
            'arrays':{'textures':{'Array.blocks':['Texture.'+name for name in catalog]},
                      'materials':{'Array.materials':['Material.default','Material.translucent']}},
            'ignore_lighting':True,'light_color_multiplier':1,
            'geometry':'Geometry.default','materials':[{'*':'Array.materials['+select([1 if flag else 0 for flag in translucent])+']'}],
            'textures':["Array.blocks[q.property('advance_magic:material')]"],
            'uv_anim':{'offset':[0,select([f'math.mod(math.floor(q.life_time*20/{duration}),{count})/{count}' for duration,count in timings])],
                       'scale':[1,select([f'1/{count}' for _,count in timings])]}}}})
    write(bedrock / 'materials/legendary_blocks.material', {'materials':{'version':'1.0.0',
        'advance_magic_legendary_blocks:entity_alphatest':{'+defines':['USE_UV_ANIM']},
        'advance_magic_legendary_blocks_blend:entity_alphablend':{'+defines':['USE_UV_ANIM']}}})
    # Write Bedrock blocks.json and terrain_texture.json to guarantee native mushroom block textures
    mushroom_blocks = {
        'minecraft:red_mushroom_block': {
            'textures': 'red_mushroom_block',
            'carried_textures': 'red_mushroom_block',
            'sound': 'wood'
        },
        'minecraft:brown_mushroom_block': {
            'textures': 'brown_mushroom_block',
            'carried_textures': 'brown_mushroom_block',
            'sound': 'wood'
        },
        'minecraft:mushroom_stem': {
            'textures': 'mushroom_stem',
            'carried_textures': 'mushroom_stem',
            'sound': 'wood'
        }
    }
    blocks_file = bedrock / 'blocks.json'
    existing_blocks = {}
    if blocks_file.is_file():
        try: existing_blocks = json.loads(blocks_file.read_text(encoding='utf8'))
        except Exception: pass
    existing_blocks['format_version'] = '1.21.0'
    for k, v in mushroom_blocks.items():
        existing_blocks[k] = v
    write(blocks_file, existing_blocks)

    terrain_tex_file = bedrock / 'textures/terrain_texture.json'
    existing_terrain = {
        'resource_pack_name': 'advance_magic',
        'texture_name': 'atlas.terrain',
        'padding': 8,
        'num_mip_levels': 4,
        'texture_data': {}
    }
    if terrain_tex_file.is_file():
        try: existing_terrain = json.loads(terrain_tex_file.read_text(encoding='utf8'))
        except Exception: pass
    tex_data = existing_terrain.setdefault('texture_data', {})
    tex_data['red_mushroom_block'] = {'textures': 'textures/blocks/red_mushroom_block'}
    tex_data['brown_mushroom_block'] = {'textures': 'textures/blocks/brown_mushroom_block'}
    tex_data['mushroom_stem'] = {'textures': 'textures/blocks/mushroom_stem'}
    tex_data['mushroom_block_inside'] = {'textures': 'textures/blocks/mushroom_block_inside'}
    write(terrain_tex_file, existing_terrain)

    with zipfile.ZipFile(archive_path or client_jar()) as java:
        for tname in ['red_mushroom_block', 'brown_mushroom_block', 'mushroom_stem', 'mushroom_block_inside']:
            source_path = f'assets/minecraft/textures/block/{tname}.png'
            dest_path = bedrock / f'textures/blocks/{tname}.png'
            dest_path.parent.mkdir(parents=True, exist_ok=True)
            dest_path.write_bytes(java.read(source_path))

    return {'materials':catalog,'texture_timings':timings,'translucent':[m for m,flag in zip(catalog,translucent) if flag],'face_shade':SHADE}


if __name__ == '__main__':
    import argparse
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    print(json.dumps(write_assets(args.output),indent=2))
