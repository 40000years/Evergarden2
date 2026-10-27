"""Verify the scoped Cerberus, Wyvern and Fairy art revision against its saved release.

Checks rendered geometry relationships and authored articulation, plus byte identity
for every pet and boss blueprint outside the nine approved growth variants.
"""
import argparse
import hashlib
import itertools
import json
import math
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parents[2]
RESOURCE_PREFIX = 'evergarden/src/main/resources/'
SPECIES = {'crimson_cerberus', 'frost_wyvern', 'chrono_fairy'}
STAGES = {'baby', 'medium', 'adult'}
EXPECTED_CHANGED = {f'everpet_{species}_{stage}.bbmodel' for species in SPECIES for stage in STAGES}
CLIPS = {'idle', 'walk', 'fly', 'sit', 'greet'}


def groups(model):
    result = {}

    def visit(group, parent=None):
        if not isinstance(group, dict):
            return
        result[group['name']] = (group, parent)
        for child in group.get('children', []):
            visit(child, group['name'])

    for group in model['outliner']:
        visit(group)
    return result


def owned(model, name, descendants=True):
    bones = groups(model)
    elements = {cube['uuid']: cube for cube in model['elements']}

    def visit(group):
        for child in group.get('children', []):
            if isinstance(child, str):
                yield elements[child]
            elif descendants:
                yield from visit(child)

    return list(visit(bones[name][0]))


def center(cube):
    return [(a + b) / 2 for a, b in zip(cube['from'], cube['to'])]


def rotate(point, degrees):
    x, y, z = map(math.radians, degrees)
    a, b, c = point
    b, c = b * math.cos(x) - c * math.sin(x), b * math.sin(x) + c * math.cos(x)
    a, c = a * math.cos(y) + c * math.sin(y), -a * math.sin(y) + c * math.cos(y)
    return [a * math.cos(z) - b * math.sin(z), a * math.sin(z) + b * math.cos(z), c]


def cube_points(cube):
    pivot = cube.get('origin', [0, 0, 0])
    for choice in itertools.product((0, 1), repeat=3):
        point = [cube['to' if choice[axis] else 'from'][axis] - pivot[axis] for axis in range(3)]
        point = rotate(point, cube.get('rotation', [0, 0, 0]))
        yield [point[axis] + pivot[axis] for axis in range(3)]


def bounds(points):
    points = list(points)
    return [min(p[axis] for p in points) for axis in range(3)], [max(p[axis] for p in points) for axis in range(3)]


def sample(frames, time):
    if not frames:
        return [0, 0, 0]
    if time <= frames[0][0]:
        return frames[0][1:]
    for a, b in zip(frames, frames[1:]):
        if time <= b[0]:
            factor = (time - a[0]) / (b[0] - a[0])
            return [a[i] * (1 - factor) + b[i] * factor for i in range(1, 4)]
    return frames[-1][1:]


def posed(point, index, definition, clip=None, time=0):
    point = list(point)
    while index >= 0:
        bone = definition['bones'][index]
        track = definition['clips'][clip]['bones'].get(str(index), {}) if clip else {}
        position = sample(track.get('position', []), time)
        motion = sample(track.get('rotation', []), time)
        origin = [value * 16 for value in bone['origin']]
        point = rotate([point[i] - origin[i] for i in range(3)],
                       [bone['rotation'][i] + motion[i] for i in range(3)])
        point = [point[i] + origin[i] + position[i] * 16 for i in range(3)]
        index = bone['parent']
    return point


def model_points(model, definition, names, clip=None, time=0, predicate=lambda cube: True):
    elements = {cube['uuid']: cube for cube in model['elements']}
    indices = {bone['name']: index for index, bone in enumerate(definition['bones'])}
    hierarchy = groups(model)
    for name in names:
        group = hierarchy[name][0]
        for child in group.get('children', []):
            if isinstance(child, str) and predicate(elements[child]):
                for point in cube_points(elements[child]):
                    yield posed(point, indices[name], definition, clip, time)


def semantic_cubes(cubes):
    # Cube UUIDs and numeric name suffixes are generated from insertion order.
    return [(re.sub(r'_\d+$', '', cube['name']), {k: v for k, v in cube.items() if k not in ('uuid', 'name')})
            for cube in cubes]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', type=Path, default=ROOT / 'output/pet-feedback-release-20261022/before/assets-before.zip')
    parser.add_argument('--resources', type=Path, default=ROOT / 'evergarden/src/main/resources')
    parser.add_argument('--garden-jar', type=Path)
    parser.add_argument('--output', type=Path, default=ROOT / 'output/pet-feedback-checks-20261022/structural.json')
    args = parser.parse_args()
    checks = 0
    details = {}

    def check(condition, label):
        nonlocal checks
        checks += 1
        assert condition, label

    manifest = json.loads((args.resources / 'native-pets.json').read_text(encoding='utf8'))['models']
    with zipfile.ZipFile(args.baseline) as previous:
        paths = {name for name in previous.namelist() if name.startswith(RESOURCE_PREFIX + 'modelengine/') and name.endswith('.bbmodel')}
        current_paths = {RESOURCE_PREFIX + path.relative_to(args.resources).as_posix() for path in (args.resources / 'modelengine').rglob('*.bbmodel')}
        check(paths == current_paths, 'no pet or boss blueprint was added, removed or renamed')
        check(len(paths) == 59, 'baseline includes 51 pet and 8 boss blueprints')
        changed = set()
        unchanged_pets = unchanged_bosses = 0
        for path in sorted(paths):
            relative = path.removeprefix(RESOURCE_PREFIX)
            raw = (args.resources / relative).read_bytes()
            before = previous.read(path)
            if Path(path).name in EXPECTED_CHANGED:
                check(raw != before, 'approved variant is revised: ' + path)
                changed.add(Path(path).name)
            else:
                check(raw == before, 'out-of-scope blueprint changed: ' + path)
                if '/pets/' in path:
                    unchanged_pets += 1
                else:
                    unchanged_bosses += 1
        check(changed == EXPECTED_CHANGED, 'exactly the nine approved species/stage variants change')
        check(unchanged_pets == 42 and unchanged_bosses == 8, 'all other pets and all Arkhaia/boss blueprints are byte-identical')

        for filename in sorted(EXPECTED_CHANGED):
            path = RESOURCE_PREFIX + 'modelengine/pets/' + filename
            model = json.loads((args.resources / 'modelengine/pets' / filename).read_text(encoding='utf8'))
            before = json.loads(previous.read(path))
            identity = filename.removesuffix('.bbmodel')
            species = model['meta']['evergarden_pet_species']
            stage = model['meta']['evergarden_pet_stage']
            check(species in SPECIES and stage in STAGES, identity + ': approved metadata')
            check(model['model_identifier'] == before['model_identifier'] == model['name'] == before['name'] == identity,
                  identity + ': stable public model identifiers')
            old_groups, new_groups = groups(before), groups(model)
            check(old_groups.keys() <= new_groups.keys(), identity + ': existing named joints retained')
            for name, (group, _) in old_groups.items():
                check(group['uuid'] == new_groups[name][0]['uuid'], identity + ': stable existing joint UUID ' + name)
            check({clip['name'] for clip in model['animations']} == CLIPS, identity + ': all five authored clips remain')
            check({clip['uuid'] for clip in model['animations']} == {clip['uuid'] for clip in before['animations']}, identity + ': stable animation identities')
            definition = manifest[identity.removeprefix('everpet_')]
            check(definition['source_sha256'] == hashlib.sha256((args.resources / 'modelengine/pets' / filename).read_bytes()).hexdigest(), identity + ': native renderer consumes the revised source')
            check(set(definition['clips']) == CLIPS, identity + ': native clip bindings remain complete')

            if species == 'crimson_cerberus':
                head_metrics = {}
                for name in ('head_l', 'head', 'head_r'):
                    cubes = owned(model, name)
                    nose = next(c for c in cubes if c['name'].startswith('round_nose_'))
                    skull = next(c for c in cubes if c['name'].startswith('puppy_head_core_'))
                    index = next(i for i, bone in enumerate(definition['bones']) if bone['name'] == name)
                    n, s = posed(center(nose), index, definition), posed(center(skull), index, definition)
                    angle = math.degrees(math.atan2(-(n[0] - s[0]), -(n[2] - s[2])))
                    head_metrics[name] = dict(center=s, outward_angle=angle)
                    check(n[2] < s[2], identity + ': each muzzle faces forward ' + name)
                left, middle, right = [head_metrics[name] for name in ('head_l', 'head', 'head_r')]
                check(left['outward_angle'] > 5 and right['outward_angle'] < -5 and abs(middle['outward_angle']) < 3,
                      identity + ': outer muzzles visibly splay outward instead of sharing a face plane')
                check(left['center'][0] < middle['center'][0] < right['center'][0], identity + ': three readable side-by-side heads')
                check(middle['center'][1] > max(left['center'][1], right['center'][1]) and middle['center'][2] < min(left['center'][2], right['center'][2]),
                      identity + ': central head has a distinct raised forward silhouette')
                details[identity] = head_metrics

            elif species == 'frost_wyvern':
                for side in ('l', 'r'):
                    base, tip = 'wing_' + side, 'wingtip_' + side
                    check(tip in new_groups and new_groups[tip][1] == base, identity + ': distal wing hinges from the upper wing ' + side)
                    check(bool(owned(model, base, False)) and bool(owned(model, tip, False)), identity + ': both articulated wing segments carry geometry ' + side)
                    check(abs(new_groups[tip][0]['origin'][0]) > abs(new_groups[base][0]['origin'][0]), identity + ': wrist pivot lies outward from the shoulder ' + side)
                    index = next(i for i, bone in enumerate(definition['bones']) if bone['name'] == tip)
                    folded = sample(definition['clips']['sit']['bones'][str(index)]['rotation'], definition['clips']['sit']['length'])
                    opened = [sample(definition['clips']['fly']['bones'][str(index)]['rotation'], definition['clips']['fly']['length'] * fraction) for fraction in (0, .25, .5, .75, 1)]
                    check(max(math.dist(folded, angle) for angle in opened) > 15, identity + ': wrist visibly opens for flight and folds for rest ' + side)
                wings = {'wing_l', 'wing_r', 'wingtip_l', 'wingtip_r'}
                fly_spans = []
                for fraction in (0, .25, .5, .75, 1):
                    low, high = bounds(model_points(model, definition, wings, 'fly', definition['clips']['fly']['length'] * fraction))
                    fly_spans.append(high[0] - low[0])
                low, high = bounds(model_points(model, definition, wings, 'sit', definition['clips']['sit']['length']))
                folded_span = high[0] - low[0]
                check(folded_span < max(fly_spans) * .85, identity + ': rest silhouette folds smaller than its flight span')
                head_index = next(i for i, bone in enumerate(definition['bones']) if bone['name'] == 'head')
                for clip in CLIPS:
                    for fraction in (0, .25, .5, .75, 1):
                        at = definition['clips'][clip]['length'] * fraction
                        origin = posed([0, 0, 0], head_index, definition, clip, at)
                        forward = posed([0, 0, -1], head_index, definition, clip, at)
                        check(origin[2] - forward[2] > .17, identity + ': face stays forward while wings animate ' + clip)
                        face_low, face_high = bounds(model_points(model, definition, {'head'}, clip, at,
                                                                 lambda cube: cube['name'].startswith(('eye_', 'snout_'))))
                        face_middle = [(a + b) / 2 for a, b in zip(face_low, face_high)]
                        face_half = [(b - a) * .35 for a, b in zip(face_low, face_high)]
                        membrane = model_points(model, definition, wings, clip, at,
                                                lambda cube: cube['name'].startswith('icy_wing_membrane_'))
                        check(not any(all(abs(point[axis] - face_middle[axis]) < face_half[axis] for axis in range(3)) for point in membrane),
                              identity + ': animated wing membranes do not fold across the central face ' + clip)
                details[identity] = dict(folded_span=folded_span, maximum_flight_span=max(fly_spans))

            else:
                head = owned(model, 'head')
                smile = sorted((cube for cube in head if cube['name'].startswith('fairy_smile_')), key=lambda cube: center(cube)[0])
                check(len(smile) == 3, identity + ': smile has a center and two readable upturned corners')
                centers = [center(cube) for cube in smile]
                check(centers[0][0] < centers[1][0] < centers[2][0] and min(centers[0][1], centers[2][1]) > centers[1][1], identity + ': smile corners curve upward')
                skull = next(cube for cube in head if cube['name'].startswith('soft_fairy_') and '_core_' in cube['name'])
                jaw = next(cube for cube in head if cube['name'].startswith('tapered_fairy_jaw_core_'))
                check(all(center(cube)[2] < jaw['from'][2] for cube in smile), identity + ': smile sits visibly in front of the jaw')
                hair = [cube for cube in head if any(token in cube['name'] for token in ('golden_hair_', 'swept_fringe', 'side_lock'))]
                hair_centers = [center(cube) for cube in hair]
                check(len({round(p[1], 3) for p in hair_centers}) >= 3 and len({round(p[2], 3) for p in hair_centers}) >= 3, identity + ': hair has offset volume at several heights and depths')
                low, high = bounds(point for cube in hair for point in cube_points(cube))
                check(low[0] < skull['from'][0] and high[0] > skull['to'][0] and high[2] > skull['to'][2], identity + ': hair frames both cheeks and the back of the head')
                check(any('swept_fringe' in cube['name'] for cube in hair) and any('side_lock' in cube['name'] for cube in hair), identity + ': hair includes front fringe and separate side locks')
                for wing in ('wing_l', 'wing_r'):
                    check(semantic_cubes(owned(model, wing)) == semantic_cubes(owned(before, wing)), identity + ': approved butterfly wing geometry stays unchanged ' + wing)
                hourglass = lambda m: [cube for cube in m['elements'] if 'hourglass' in cube['name'] or 'falling_time_sand' in cube['name']]
                check(semantic_cubes(hourglass(model)) == semantic_cubes(hourglass(before)), identity + ': approved hourglass geometry stays unchanged')
                details[identity] = dict(smile_pieces=len(smile), hair_parts=len(hair), hair_depth=high[2] - low[2])

        if args.garden_jar:
            with zipfile.ZipFile(args.garden_jar) as bundle:
                for path in sorted(paths):
                    relative = path.removeprefix(RESOURCE_PREFIX)
                    check(bundle.read(relative) == (args.resources / relative).read_bytes(), 'packaged blueprint matches tested source: ' + relative)
                for relative in ('native-pets.json', 'native-pets.zip'):
                    check(bundle.read(relative) == (args.resources / relative).read_bytes(), 'packaged native assets match tested source: ' + relative)
    report = dict(result='PASS', checks=checks, changed_variants=sorted(EXPECTED_CHANGED), unchanged_pet_variants=unchanged_pets,
                  unchanged_boss_blueprints=unchanged_bosses, stable_existing_joint_ids=True,
                  baseline=str(args.baseline), garden_jar=str(args.garden_jar) if args.garden_jar else None,
                  structural_geometry=details, connected_client_playtest=False)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(f'PASS {checks} scoped polish checks; 9 revised variants; 42 other pets and 8 boss blueprints byte-identical')
    print('Report:', args.output)


if __name__ == '__main__':
    main()
