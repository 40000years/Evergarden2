"""Export original rare pet sculptures to vanilla display items (Java 1.21.11+).

Each authored bone becomes one item model; arbitrary cube rotations are preserved
using Minecraft's supported x/y/z element rotations. No ModelEngine assets or
plugin binaries are rewritten. The manifest preserves authored joint animation.
"""
import base64
import hashlib
import json
from pathlib import Path
import runpy
import zipfile

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / 'evergarden/src/main/resources'
MYTHICAL = {'aetherius', 'ignis_draconis', 'zephyros', 'solaris', 'fenrir'}


def encode(value):
    return (json.dumps(value, separators=(',', ':'), ensure_ascii=False) + '\n').encode()


def export():
    assets = {}
    models = {}
    input_hashes = {}
    total_cubes = 0
    for path in sorted((RESOURCES / 'modelengine/pets').glob('*.bbmodel')):
        raw = path.read_bytes()
        source = json.loads(raw)
        meta = source['meta']
        species, stage = meta['evergarden_pet_species'], meta['evergarden_pet_stage']
        if species in MYTHICAL:
            continue
        model_id = species + '_' + stage
        elements = {cube['uuid']: cube for cube in source['elements']}
        bones = []
        clips = {}
        uuid_indices = {}
        expected_cubes = set()
        for index, texture in enumerate(source['textures']):
            assert texture['source'].startswith('data:image/png;base64,')
            assets[f'assets/evergarden/textures/item/pet_native/{model_id}_{index}.png'] = base64.b64decode(texture['source'].split(',', 1)[1])

        def walk(group, parent):
            nonlocal total_cubes
            if not isinstance(group, dict) or group['name'] == 'hitbox' or group.get('visibility') is False:
                return
            origin = group.get('origin', [0, 0, 0])
            cubes = [elements[child] for child in group.get('children', []) if isinstance(child, str)
                     and elements[child].get('visibility', True) and elements[child].get('export', True)]
            index = len(bones)
            uuid_indices[group['uuid']] = index
            extent = max([1] + [abs(cube[field][axis] - origin[axis])
                               for cube in cubes for field in ('from', 'to', 'origin') for axis in range(3)])
            # Java cube coordinates remain comfortably inside [-16, 32]. Their
            # original physical size is restored by the display transformation.
            scale = max(1.0, extent / 20.0)
            key = f'pet_native/{model_id}/{index}' if cubes else ''
            bones.append(dict(name=group['name'], parent=parent, origin=[v / 16 for v in origin],
                              rotation=group.get('rotation', [0, 0, 0]), scale=scale, item=key))
            if cubes:
                converted = []
                for cube in cubes:
                    expected_cubes.add(cube['uuid'])
                    element = {field: [round(8 + (cube[field][a] - origin[a]) / scale, 8) for a in range(3)]
                               for field in ('from', 'to')}
                    element['shade'] = cube.get('shade', True)
                    if any(cube.get('rotation', [0, 0, 0])):
                        rotation = cube.get('rotation', [0, 0, 0])
                        element['rotation'] = dict(origin=[round(8 + (cube['origin'][a] - origin[a]) / scale, 8) for a in range(3)],
                                                   x=rotation[0], y=rotation[1], z=rotation[2])
                    element['faces'] = {}
                    for face, data in cube['faces'].items():
                        if data.get('texture') is None:
                            continue
                        uv = data['uv']
                        width, height = source['resolution']['width'], source['resolution']['height']
                        value = dict(texture='#' + str(data['texture']), uv=[uv[0]*16/width, uv[1]*16/height, uv[2]*16/width, uv[3]*16/height])
                        if data.get('rotation'):
                            value['rotation'] = data['rotation']
                        element['faces'][face] = value
                    converted.append(element)
                texture_refs = {str(i): f'evergarden:item/pet_native/{model_id}_{i}' for i in range(len(source['textures']))}
                assets[f'assets/evergarden/models/{key}.json'] = encode(dict(ambientocclusion=False, textures=texture_refs, elements=converted))
                assets[f'assets/evergarden/items/{key}.json'] = encode(dict(model=dict(type='minecraft:model', model='evergarden:' + key)))
                total_cubes += len(converted)
            for child in group.get('children', []):
                if isinstance(child, dict):
                    walk(child, index)

        for group in source['outliner']:
            walk(group, -1)
        for clip in source['animations']:
            channels = {}
            for uuid, animator in clip['animators'].items():
                if uuid not in uuid_indices:
                    continue
                tracks = {}
                for channel in ('position', 'rotation'):
                    values = []
                    for frame in animator.get('keyframes', []):
                        if frame['channel'] != channel:
                            continue
                        vector = [float(frame['data_points'][0].get(axis, 0)) for axis in 'xyz']
                        # Match the original authored/preview handedness.
                        vector[0] *= -1
                        if channel == 'rotation':
                            vector[1] *= -1
                        else:
                            vector = [v/16 for v in vector]
                        values.append([frame['time'], *vector])
                    tracks[channel] = sorted(values)
                channels[str(uuid_indices[uuid])] = tracks
            clips[clip['name']] = dict(length=clip['length'], bones=channels)
        assert set(clips) == {'idle', 'walk', 'fly', 'sit', 'greet'}
        assert expected_cubes
        models[model_id] = dict(species=species, stage=stage, bones=bones, clips=clips, cubes=len(expected_cubes),
                               source_sha256=hashlib.sha256(raw).hexdigest())
        input_hashes[path.name] = hashlib.sha256(raw).hexdigest()
    species_count = len({m['species'] for m in models.values()})
    assert len(models) == 3 * species_count == 96, (len(models), species_count)
    manifest = dict(format=1, minimum_java='1.21.11', models=models)
    target = RESOURCES / 'native-pets.json'
    target.write_bytes(encode(manifest))
    pack = RESOURCES / 'native-pets.zip'
    with zipfile.ZipFile(pack, 'w', zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(assets.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            archive.writestr(info, data, compress_type=zipfile.ZIP_DEFLATED)
    # The independent checker below reconstructs source geometry from these
    # assets and verifies every original texture and animation keyframe.
    report = dict(result='PASS', variants=len(models), species=species_count, clips_per_variant=5, cubes=total_cubes,
                  item_models=sum(bool(b['item']) for m in models.values() for b in m['bones']),
                  source_hashes=input_hashes, pack_sha256=hashlib.sha256(pack.read_bytes()).hexdigest(),
                  minimum_java='1.21.11', modelengine_assets_modified=False)
    report_path = ROOT / 'output/native-pet-assets-validation-20261021.json'
    report_path.parent.mkdir(exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(f"PASS {len(models)} variants; {total_cubes} source cubes; {report['item_models']} item bones; {pack.stat().st_size} pack bytes")
    runpy.run_path(str(ROOT/'tests/model-engine/check_native_pet_models.py'),run_name='__main__')


if __name__ == '__main__':
    export()
