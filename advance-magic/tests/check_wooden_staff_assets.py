"""Cross-check the neutral wooden staff's authored and shipped native exports.

Coordinates and hand/camera math are evaluated independently of its generator.
These are asset/dependency checks, not a game-client rendering/import test.
"""
import base64
import io
import itertools
import json
import math
from pathlib import Path
import zipfile

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / 'advance-magic/art/wooden-staff'
DIST = ROOT / 'advance-magic/dist'
KEY = 'advance_magic:staff_wood'
ORIGINAL = ('fire', 'wind', 'earth', 'ice', 'water', 'lightning', 'light', 'dark')
FACES = {'north', 'south', 'east', 'west', 'up', 'down'}
checks = 0


def check(ok, label):
    global checks
    assert ok, label
    checks += 1


def document(path):
    return json.loads(path.read_text(encoding='utf8'))


def normalized(values):
    return tuple(round(v, 4) for v in values)


def boxes(cubes):
    return sorted(normalized(cube['from']+cube['to']) for cube in cubes)


def rotate(point, angles):
    # Native ItemTransform Rx Ry Rz; deliberately independent of the exporter.
    x, y, z = point
    a, b, c = map(math.radians, angles)
    return [
        math.cos(b)*math.cos(c)*x-math.cos(b)*math.sin(c)*y+math.sin(b)*z,
        (math.cos(a)*math.sin(c)+math.sin(a)*math.sin(b)*math.cos(c))*x
        +(math.cos(a)*math.cos(c)-math.sin(a)*math.sin(b)*math.sin(c))*y-math.sin(a)*math.cos(b)*z,
        (math.sin(a)*math.sin(c)-math.cos(a)*math.sin(b)*math.cos(c))*x
        +(math.sin(a)*math.cos(c)+math.cos(a)*math.sin(b)*math.sin(c))*y+math.cos(a)*math.cos(b)*z,
    ]


metadata = document(ART / 'metadata.json')
source = document(ART / 'wood.geometry.json')
blueprint = document(ART / 'wooden_staff.bbmodel')
cubes, grip = source['cubes'], source['grip']
check(len(cubes) == metadata['items'][0]['cuboids'] and len(cubes) > 150, 'Substantial authored solid geometry')
check(len({cube['name'] for cube in cubes}) == len(cubes), 'Unique source cube names')
check(metadata['base_item'] == 'minecraft:carrot_on_a_stick', 'Native casting input base')
check(metadata['items'][0]['element'] is None, 'Neutral starter art profile')
check(metadata['items'][0]['slots'] == {'initial': 1, 'upgraded': 2, 'maximum_rarity': 'rare'}, 'Agreed starter progression metadata')
check(blueprint == document(ART / 'blockbench/wooden_staff.bbmodel'), 'Editable sources agree')
check(len({element['uuid'] for element in blueprint['elements']}) == len(blueprint['elements']), 'Unique Blockbench IDs')
authored = [element for element in blueprint['elements'] if element['name'] != 'hitbox']
converted = sorted(normalized([element[end][axis]+grip[axis] for end in ('from', 'to') for axis in range(3)]) for element in authored)
check(converted == boxes(cubes), 'Blockbench/source exact parity')
check(any(all(element['from'][axis] <= 0 <= element['to'][axis] for axis in range(3)) for element in authored), 'Physical handle contains authoring origin')
uuids = {element['uuid'] for element in blueprint['elements']}
check(all(child in uuids for bone in blueprint['outliner'] for child in bone['children']), 'Resolved editable bone children')
with Image.open(io.BytesIO(base64.b64decode(blueprint['textures'][0]['source'].split(',')[1]))) as texture:
    check(texture.size == (128, 128), 'Embedded editable material atlas')

# All detailed facets, branches and bindings belong to a single physical solid.
def touches(a, b):
    return all(a['from'][i] <= b['to'][i]+.00011 and b['from'][i] <= a['to'][i]+.00011 for i in range(3))


seen, pending = {0}, [0]
while pending:
    current = cubes[pending.pop()]
    for index, candidate in enumerate(cubes):
        if index not in seen and touches(current, candidate):
            seen.add(index)
            pending.append(index)
check(len(seen) == len(cubes), 'Every wood, crystal, leather and metal cuboid physically connects')

mapping = document(DIST / 'geyser-mappings.json')['items']['minecraft:carrot_on_a_stick']
keys = [row['model'] for row in mapping]
check(keys == [f'advance_magic:staff_{name}' for name in ORIGINAL]+[KEY], 'Original eight preserved in order, neutral staff appended')
check(len(set(keys)) == len(keys), 'No Geyser model collisions')
check(len({row['bedrock_identifier'] for row in mapping}) == len(mapping), 'No Bedrock identifier collisions')
row = mapping[-1]
check(row == metadata['definitions']['minecraft:carrot_on_a_stick'][0], 'Shipped neutral mapping equals authored definition')
check(row['bedrock_options']['allow_offhand'] and row['bedrock_options']['display_handheld'], 'Both native held hands supported')

with zipfile.ZipFile(DIST / 'advance-magic-java.zip') as java, zipfile.ZipFile(DIST / 'advance-magic-bedrock.mcpack') as bedrock:
    # Every generated native resource must reach its pack unchanged in content.
    for client, pack in (('java', java), ('bedrock', bedrock)):
        for path in (ART / client).rglob('*'):
            if path.is_file():
                exported = pack.read(path.relative_to(ART / client).as_posix())
                check(json.loads(exported) == document(path) if path.suffix in ('.json', '.mcmeta') else exported == path.read_bytes(), client+' shipped resource parity: '+path.name)

    native = json.loads(java.read('assets/advance_magic/models/item/wooden_staff/wood.json'))
    check(boxes(native['elements']) == boxes(cubes), 'Java/source exact cuboid parity')
    check('fx' not in native['textures'] and len(native['elements']) == len(cubes), 'No VFX picture planes')
    for cube in native['elements']:
        check(all(-16 <= v <= 32 for end in ('from', 'to') for v in cube[end]), 'Supported Java bounds')
        check(all(cube['to'][axis] > cube['from'][axis] for axis in range(3)), 'Positive solid Java dimensions')
        check(set(cube['faces']) == FACES, 'Six textured faces')
        for face in cube['faces'].values():
            check(face['texture'] == '#body' and all(0 <= v <= 16 for v in face['uv']), 'Bounded material UV')
    for texture in native['textures'].values():
        check(bool(java.read('assets/'+texture.replace(':', '/textures/', 1)+'.png')), 'Resolved Java texture')
    item = json.loads(java.read('assets/advance_magic/items/staff_wood.json'))['model']
    check(item['type'] == 'minecraft:select' and item['property'] == 'minecraft:display_context', 'Native GUI/held model switch')
    for model in [item['fallback'], *[case['model'] for case in item['cases']]]:
        check(bool(java.read('assets/'+model['model'].replace(':', '/models/', 1)+'.json')), 'Resolved native GUI/held model')

    # Both hands preserve the same verified vanilla contact as elemental staffs.
    for first, left in itertools.product((False, True), repeat=2):
        mode = ('firstperson_' if first else 'thirdperson_')+('lefthand' if left else 'righthand')
        pose = native['display'][mode]
        angles, translation = list(pose['rotation']), list(pose['translation'])
        if left:
            angles[1] *= -1; angles[2] *= -1; translation[0] *= -1
        actual = [v+t for v, t in zip(rotate([(grip[i]-8)*pose['scale'][i] for i in range(3)], angles), translation)]
        native_scale = .68 if first else .85
        expected = [v+t for v, t in zip(rotate([-4.5*native_scale, -4.5*native_scale, 0], [0, -90, 25 if first else 55]), [1.13, 3.2, 1.13] if first else [0, 4, .5])]
        if first:
            expected = [expected[0], expected[1]+.8, expected[2]-1.5]
        if left:
            expected[0] *= -1
        check(max(abs(a-b) for a, b in zip(actual, expected)) < .00002, 'Native physical hand contact '+mode)

    attach = json.loads(bedrock.read('attachables/wooden_staff/wood.json'))['minecraft:attachable']['description']
    geometry = json.loads(bedrock.read('models/entity/wooden_staff/wood.geo.json'))['minecraft:geometry'][0]
    animations = json.loads(bedrock.read('animations/wooden_staff.animation.json'))['animations']
    controllers = json.loads(bedrock.read('render_controllers/wooden_staff.render.json'))['render_controllers']
    check(attach['identifier'] == KEY == row['bedrock_identifier'], 'Matching Bedrock native route')
    check(geometry['description']['identifier'] == attach['geometry']['default'], 'Resolved Bedrock geometry')
    check(all(clip in animations for clip in attach['animations'].values()), 'Resolved native animation clips')
    check(all(controller in controllers for controller in attach['render_controllers']), 'Resolved native render controllers')
    check(all(material in attach['materials'] for controller in controllers.values() for material in [entry[next(iter(entry))].split('.')[1] for entry in controller['materials']]), 'Resolved body/glow material aliases')
    for texture in attach['textures'].values():
        check(bool(bedrock.read(texture+'.png')), 'Resolved Bedrock body texture')
    bones = {bone['name']: bone for bone in geometry['bones']}
    check(bones['root']['pivot'] == [0, 24, 0] and bones['root']['binding'] == 'q.item_slot_to_bone_name(context.item_slot)', 'Native bound item root')
    check(bones['grip']['pivot'] == [0, 12.5, 0] and bones['grip']['parent'] == 'root', 'Separate physical grip child')
    converted = []
    for bone in geometry['bones']:
        for cube in bone.get('cubes', []):
            ox, oy, oz = cube['origin']; sx, sy, sz = cube['size']
            check(min(sx, sy, sz) > 0, 'Positive Bedrock solids')
            converted.append(normalized([8-ox-sx, oy-12.5+grip[1], oz+8, 8-ox, oy-12.5+grip[1]+sy, oz+8+sz]))
    check(sorted(converted) == boxes(cubes), 'Bedrock/source exact cuboid parity')

    first = animations['animation.advance_magic.staff_wood.first']['bones']
    third = animations['animation.advance_magic.staff_wood.third']['bones']
    check(third['grip'] == {'scale': [.9]*3}, 'World contact independent of first-person framing')
    check(first['root']['scale'] == [1]*3, 'Unscaled native binding root')
    ratio = first['grip']['scale'][0]/.48
    lift = first['grip']['position'][1]
    projected = []
    for cube in cubes:
        for x, y, z in itertools.product(*[(cube['from'][i], cube['to'][i]) for i in range(3)]):
            p = [ratio*(x-8), -23.75+ratio*(y-grip[1])+lift/.48, ratio*(z-8)]
            projected.append((813+13*p[0]-11.68*p[1]+2.5*p[2], 197-3*p[0]-18.42*p[1]+1.5*p[2]))
    lo = [min(p[i] for p in projected) for i in range(2)]
    hi = [max(p[i] for p in projected) for i in range(2)]
    check(49.9 < lo[1] < 50.1 and 449.9 < hi[1] < 450.1, 'Complete crown and shaft fit calibrated camera height')
    check(500 < lo[0] < hi[0] < 1156, 'Complete staff fits right-hand camera frame')

    atlas = json.loads(bedrock.read('textures/item_texture.json'))['texture_data']
    icon = bedrock.read(atlas[row['bedrock_options']['icon']]['textures']+'.png')
    check(icon == java.read('assets/advance_magic/textures/item/wooden_staff/wood_icon.png'), 'Identical cross-client GUI artwork')
    with Image.open(io.BytesIO(icon)) as image:
        check(image.mode == 'RGBA' and image.size == (128, 128), 'Native high-resolution GUI icon')
        l, t, r, b = image.getbbox()
        check(min(l, t, 128-r, 128-b) >= 3 and max(r-l, b-t) >= 108, 'Large recognizable icon and unclipped gutter')
    manifest = json.loads(bedrock.read('manifest.json'))
    check(manifest['header']['version'] >= [2, 18, 0], 'New item catalog refreshes Bedrock cache')

with zipfile.ZipFile(DIST / 'wooden-staff-sources.zip') as sources:
    check(json.loads(sources.read('wooden_staff.bbmodel')) == blueprint, 'Delivered editable blueprint matches actual item')
    check(json.loads(sources.read('wood.geometry.json')) == source, 'Delivered geometry source matches actual item')

print('PASS', checks, 'wood staff asset, dependency, physical grip, camera, source parity and mapping checks (no client rendering/import test)')
