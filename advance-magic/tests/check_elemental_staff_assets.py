"""Validate authored staff exports, animation dependencies and physical hand fit.

These inspect exported assets and native coordinate fixtures; they are not an
in-game rendering test or a ModelEngine server import test.
"""
import base64
import io
import json
import math
from pathlib import Path
import zipfile

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / 'advance-magic/art/elemental-staffs'
DIST = ROOT / 'advance-magic/dist'
IDS = ('fire', 'wind', 'earth', 'ice', 'water', 'lightning', 'light', 'dark')
REF = json.loads(Path(__file__).with_name('fixtures').joinpath('legendary-hand-reference.json').read_text())
checks = 0


def check(value, message):
    global checks
    assert value, message
    checks += 1


def document(pack, path):
    return json.loads(pack.read(path))


def picture(data, size):
    with Image.open(io.BytesIO(data)) as im:
        check(im.mode == 'RGBA' and im.size == size, 'Native RGBA PNG at authored dimensions')
        im.verify()
    with Image.open(io.BytesIO(data)) as im:
        return im.convert('RGBA')


def rotate(point, angles):
    # Independently evaluate the native ItemTransform Rx Ry Rz matrix.
    x, y, z = point
    a, b, c = [math.radians(v) for v in angles]
    return [
        math.cos(b) * math.cos(c) * x - math.cos(b) * math.sin(c) * y + math.sin(b) * z,
        (math.cos(a) * math.sin(c) + math.sin(a) * math.sin(b) * math.cos(c)) * x
        + (math.cos(a) * math.cos(c) - math.sin(a) * math.sin(b) * math.sin(c)) * y
        - math.sin(a) * math.cos(b) * z,
        (math.sin(a) * math.sin(c) - math.cos(a) * math.sin(b) * math.cos(c)) * x
        + (math.sin(a) * math.cos(c) + math.cos(a) * math.sin(b) * math.sin(c)) * y
        + math.cos(a) * math.cos(b) * z,
    ]


def near(a, b):
    return all(abs(x - y) < .00002 for x, y in zip(a, b))


def normalized(values):
    return tuple(round(v, 4) for v in values)


def hand_value(value, left):
    if not isinstance(value, str):
        return value
    condition, branches = value.split('?')
    check(condition.strip() == "context.item_slot == 'off_hand'", 'Native hand condition')
    yes, no = branches.split(':')
    return float(yes if left else no)


metadata = json.loads((ART / 'metadata.json').read_text())
check(tuple(row['id'] for row in metadata['items']) == IDS, 'Exactly eight elemental staff IDs in the agreed order')
check(metadata['ordinary_cycle'] == list(IDS[:5]), 'Five-element cycle')
check(metadata['special_cycle'] == list(IDS[5:]), 'Three special-element cycle')
check(metadata['modelengine_version'] == '4.1.1', 'ModelEngine target')
check(metadata['base_item'] == 'minecraft:carrot_on_a_stick', 'Established casting input item')
mapping = json.loads((DIST / 'geyser-mappings.json').read_text())['items']
rows = mapping['minecraft:carrot_on_a_stick']
check([row['model'] for row in rows] == [f'advance_magic:staff_{name}' for name in IDS]+['advance_magic:staff_wood'], 'Eight ordered elemental models followed by the neutral starter staff')
check(len({row['bedrock_identifier'] for row in rows}) == 9, 'Nine unique Bedrock staff identifiers')

with zipfile.ZipFile(DIST / 'advance-magic-java.zip') as java, zipfile.ZipFile(DIST / 'advance-magic-bedrock.mcpack') as bedrock:
    item_atlas = document(bedrock, 'textures/item_texture.json')['texture_data']
    animations = document(bedrock, 'animations/elemental_staffs.animation.json')['animations']
    controllers = document(bedrock, 'render_controllers/elemental_staffs.render.json')['render_controllers']
    check(not any(path.endswith('.bbmodel') for path in java.namelist() + bedrock.namelist()), 'Editable sources remain outside client downloads')
    materials_path = 'assets/advance_magic/textures/item/elemental_staffs/materials.png'
    materials = picture(java.read(materials_path), (128, 1024))
    material_frames = [materials.crop((0, f*128, 128, (f+1)*128)) for f in range(8)]
    check(len({im.tobytes() for im in material_frames}) >= 4, 'Java focus materials visibly pulse')
    check(document(java, materials_path + '.mcmeta')['animation']['frametime'] == 3, 'Native material pulse playback')
    check(picture(bedrock.read('textures/elemental_staffs/materials.png'), (128, 128)).tobytes() == material_frames[0].tobytes(), 'Shared material atlas across clients')
    for row, record in zip(rows, metadata['items']):
        name, grip = record['id'], record['grip']
        key = f'advance_magic:staff_{name}'
        check(row['bedrock_identifier'] == key, name + ' Geyser identifier')
        check(row['bedrock_options']['allow_offhand'] and row['bedrock_options']['display_handheld'], name + ' native held item in either hand')
        check('components' not in row and 'predicate' not in row, name + ' direct item-model routing without consumable behavior')
        source = json.loads((ART / 'blockbench' / record['modelengine']).read_text())
        check(source == json.loads((ART / record['modelengine']).read_text()), name + ' server blueprint and editable source agree')
        elements = [e for e in source['elements'] if e['name'] != 'hitbox']
        uuids = {e['uuid'] for e in source['elements']}
        check(len(elements) == record['cuboids'] and len(elements) > 80, name + ' substantial solid geometry')
        check(len(uuids) == len(source['elements']), name + ' unique Blockbench cube IDs')
        check(any(g['name'] == 'hitbox' for g in source['outliner']), name + ' ModelEngine hitbox helper')
        check(all(child in uuids for group in source['outliner'] for child in group['children']), name + ' resolved outliner cubes')
        check(source['animations'][0]['name'] == 'idle' and source['animations'][0]['loop'] == 'loop', name + ' looping editable animation')
        groups = {g['uuid']: g for g in source['outliner']}
        check(all(uid in groups for uid in source['animations'][0]['animators']), name + ' resolved animation bones')
        picture(base64.b64decode(source['textures'][0]['source'].split(',')[1]), (128, 128))
        check(any(all(e['from'][axis] <= 0 <= e['to'][axis] for axis in range(3)) for e in elements), name + ' authoring origin lies inside the physical leather handle')

        item = document(java, f'assets/advance_magic/items/staff_{name}.json')['model']
        check(item['type'] == 'minecraft:select' and item['property'] == 'minecraft:display_context', name + ' GUI/held split')
        check(item['cases'] == [{'when': 'gui', 'model': {'type': 'minecraft:model', 'model': f'advance_magic:item/elemental_staffs/{name}_icon'}}], name + ' dedicated large GUI icon')
        check(item['fallback']['model'] == f'advance_magic:item/elemental_staffs/{name}', name + ' authored 3D held model')
        model = document(java, f'assets/advance_magic/models/item/elemental_staffs/{name}.json')
        check(len(model['elements']) == len(elements) + 3, name + ' body plus crossed emissive VFX layers')
        check(all(-16 <= v <= 32 for e in model['elements'] for end in ('from', 'to') for v in e[end]), name + ' supported Java element bounds')
        check(all(e['light_emission'] == 15 and not e['shade'] for e in model['elements'][-3:]), name + ' fullbright VFX')
        for texture in model['textures'].values():
            check(bool(java.read('assets/' + texture.replace(':', '/textures/', 1) + '.png')), name + ' resolved Java texture')
        java_boxes = sorted(normalized(e['from'] + e['to']) for e in model['elements'][:-3])
        source_boxes = sorted(normalized([e[end][i] + grip[i] for end in ('from', 'to') for i in range(3)]) for e in elements)
        check(java_boxes == source_boxes, name + ' exact Java/Blockbench body parity')
        for first in (False, True):
            label = 'first' if first else 'third'
            for left in (False, True):
                mode = ('firstperson_' if first else 'thirdperson_') + ('lefthand' if left else 'righthand')
                pose = model['display'][mode]
                scale = pose['scale']
                check(len(set(scale)) == 1 and .5 < scale[0] <= 1, name + mode + ' uniform physical scale')
                angles, translation = list(pose['rotation']), list(pose['translation'])
                if left:
                    angles[1] *= -1; angles[2] *= -1; translation[0] *= -1
                actual = [v + t for v, t in zip(rotate([(grip[i] - 8) * scale[i] for i in range(3)], angles), translation)]
                native = REF['java'][label]
                expected = [v + t for v, t in zip(rotate([(v - 8) * native['scale'] for v in REF['java']['sprite_grip']], native['rotation']), native['translation'])]
                if first:
                    expected = [v + t for v, t in zip(expected, [0, .8, -1.5])]
                if left:
                    expected[0] *= -1
                check(near(actual, expected), name + mode + ' physical grip reaches native hand contact')

        attach = document(bedrock, f'attachables/elemental_staffs/{name}.json')['minecraft:attachable']['description']
        check(attach['identifier'] == key, name + ' attachable route')
        for texture in attach['textures'].values():
            picture(bedrock.read(texture + '.png'), (128, 128))
        for clip in attach['animations'].values():
            check(clip in animations, name + ' resolved Bedrock animation ' + clip)
        for controller in attach['render_controllers']:
            check(controller in controllers, name + ' resolved Bedrock render controller')
        geometries = []
        for suffix in ('', '_fx'):
            geo = document(bedrock, f'models/entity/elemental_staffs/{name}{suffix}.geo.json')['minecraft:geometry'][0]
            check(geo['description']['identifier'] == attach['geometry']['fx' if suffix else 'default'], name + suffix + ' resolved geometry')
            bones = {b['name']: b for b in geo['bones']}; geometries.append(bones)
            check(bones['root']['pivot'] == REF['bedrock']['pivot'], name + suffix + ' native bound item root')
            check(bones['root']['binding'] == 'q.item_slot_to_bone_name(context.item_slot)', name + suffix + ' binds to correct hand')
            check(bones['grip']['pivot'] == [0, 12.5, 0] and bones['grip']['parent'] == 'root', name + suffix + ' child scales around physical hand')
            check(all(b['parent'] == 'grip' for bone, b in bones.items() if bone not in ('root', 'grip')), name + suffix + ' shared body/FX hand frame')
        check(geometries[0]['root'] == geometries[1]['root'] and geometries[0]['grip'] == geometries[1]['grip'], name + ' effect frame cannot detach from held body')
        converted = []
        for bone in geometries[0].values():
            for cube in bone.get('cubes', []):
                ox, oy, oz = cube['origin']; sx, sy, sz = cube['size']
                check(min(sx, sy, sz) > 0, name + ' real solid cuboid body')
                converted.append(normalized([8-ox-sx, oy-12.5+grip[1], oz+8, 8-ox, oy-12.5+grip[1]+sy, oz+8+sz]))
        check(sorted(converted) == java_boxes, name + ' exact Bedrock/Java body parity')
        for label in ('first', 'third'):
            pose = animations[f'animation.advance_magic.staff_{name}.{label}']['bones']
            check(pose['root']['scale'] == [1, 1, 1], name + label + ' bound root does not scale its offset')
            check(len(set(pose['grip']['scale'])) == 1, name + label + ' no stretching')
            check("context.item_slot == 'off_hand'" in pose['root']['position'][0], name + label + ' offhand mirror')
            for left in (False, True):
                rotation = [hand_value(v, left) for v in pose['root']['rotation']]
                position = [hand_value(v, left) for v in pose['root']['position']]
                expected_rotation = list(REF['bedrock'][label]['rotation'])
                expected_position = list(REF['bedrock'][label]['position'])
                if label == 'first':
                    expected_position = [v + t for v, t in zip(expected_position, [0, 1.5, -3])]
                if left:
                    expected_rotation[1] *= -1; expected_rotation[2] *= -1; expected_position[0] *= -1
                check(near(rotation, expected_rotation) and near(position, expected_position), name + label + str(left) + ' native arm frame with independent camera framing')
        idle = animations[attach['animations']['idle']]
        check(idle['loop'] and any(b.startswith('focus') for b in idle['bones']), name + ' live floating focus animation')
        check(set(idle['bones']) <= set(geometries[0]) | set(geometries[1]), name + ' every idle target bone resolves')
        fx = controllers[f'controller.render.advance_magic.staff_{name}_fx']
        check(fx['textures'] == ['Texture.default'] and 'arrays' not in fx, name + ' restrained Bedrock VFX uses opaque material atlas')
        check(fx['materials'] == [{'*': 'Material.default'}] and fx['ignore_lighting'], name + ' alpha-test solids stay bright without alpha-as-emission')
        check(attach['materials']['default'] == 'entity_alphatest' and set(attach['textures']) == {'default'}, name + ' no retired sheet textures bound')
        check(fx['part_visibility'] == [{'glint_1': '1.0'}, {'glint_2': '1.0'}, {'glint_3': 'context.is_first_person == 0.0'}], name + ' two viewmodel motes and three world motes')

        for kind in ('fx', 'icon'):
            path = f'assets/advance_magic/textures/item/elemental_staffs/{name}_{kind}.png'
            strip = picture(java.read(path), (128, 1024))
            clip = document(java, path + '.mcmeta')['animation']
            check(clip['frametime'] == 2 and not clip['interpolate'], name + kind + ' crisp 10fps animation')
            frames = [strip.crop((0, f*128, 128, (f+1)*128)) for f in range(8)]
            check(len({f.tobytes() for f in frames}) >= 4, name + kind + ' animation visibly changes')
            for frame in frames:
                check(frame.getchannel('A').getextrema() == (0, 255), name + kind + ' transparent cutout and solid visible pixels')
                box = frame.getbbox()
                check(box and min(box[0], box[1], 128-box[2], 128-box[3]) >= (3 if kind == 'icon' else 1), name + kind + ' transparent unclipped gutter')
            if kind == 'icon':
                icon = picture(bedrock.read(item_atlas[row['bedrock_options']['icon']]['textures'] + '.png'), (128, 128))
                check(icon.tobytes() == frames[0].tobytes(), name + ' cross-client icon art agrees')
                l, t, r, b = icon.getbbox()
                check(max(r-l, b-t) >= 108, name + ' recognizable large hotbar silhouette')
            else:
                check(not any(f'textures/elemental_staffs/{name}_fx_' in path for path in bedrock.namelist()), name + ' retired Bedrock fullstaff sprite frames are not shipped')

print('PASS', checks, 'elemental staff export, dependency, animation, source parity and physical grip checks (no client/import test)')
