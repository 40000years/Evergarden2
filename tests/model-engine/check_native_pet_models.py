"""Verify the native export preserves every rare sculpture and authored clip."""
import base64
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / 'evergarden/src/main/resources'
MYTHICAL = {'aetherius', 'ignis_draconis', 'zephyros', 'solaris', 'fenrir'}


def main():
    checks = 0

    def verify(value, message):
        nonlocal checks
        checks += 1
        assert value, message

    manifest = json.loads((RESOURCES / 'native-pets.json').read_text(encoding='utf8'))
    models = manifest['models']
    species_count = len({m['species'] for m in models.values()})
    verify(len(models) == 3 * species_count == 96, '96 eligible variants: 32 wild species in three growth stages')
    expected_paths = set()
    total_cubes = 0
    source_hashes = {}
    pack_path = RESOURCES / 'native-pets.zip'
    with zipfile.ZipFile(pack_path) as pack:
        verify(pack.testzip() is None, 'pack CRC')
        for source_path in sorted((RESOURCES / 'modelengine/pets').glob('*.bbmodel')):
            raw = source_path.read_bytes()
            source = json.loads(raw)
            species = source['meta']['evergarden_pet_species']
            model_id = species + '_' + source['meta']['evergarden_pet_stage']
            if species in MYTHICAL:
                verify(model_id not in models, 'mythical models excluded')
                continue
            definition = models[model_id]
            digest = hashlib.sha256(raw).hexdigest()
            source_hashes[source_path.name] = digest
            verify(definition['source_sha256'] == digest, 'source provenance ' + model_id)
            cube_map = {cube['uuid']: cube for cube in source['elements']}
            groups = []

            def walk(group, parent):
                if not isinstance(group, dict) or group['name'] == 'hitbox' or group.get('visibility') is False:
                    return
                index = len(groups)
                groups.append((group, parent))
                for child in group.get('children', []):
                    if isinstance(child, dict):
                        walk(child, index)

            for group in source['outliner']:
                walk(group, -1)
            verify(len(groups) == len(definition['bones']), 'joint count ' + model_id)
            exported_count = 0
            for index, (group, parent) in enumerate(groups):
                bone = definition['bones'][index]
                verify(bone['parent'] == parent and bone['name'] == group['name'], 'joint hierarchy ' + model_id)
                verify(all(abs(a-b*16) < 1e-5 for a, b in zip(group['origin'], bone['origin'])), 'joint pivot ' + model_id)
                cubes = [cube_map[child] for child in group.get('children', []) if isinstance(child, str)
                         and cube_map[child].get('visibility', True) and cube_map[child].get('export', True)]
                verify(bool(cubes) == bool(bone['item']), 'empty joints are not render items')
                if not cubes:
                    continue
                item_path = f"assets/evergarden/items/{bone['item']}.json"
                model_path = f"assets/evergarden/models/{bone['item']}.json"
                expected_paths.update((item_path, model_path))
                item = json.loads(pack.read(item_path))
                verify(item['model'] == {'type':'minecraft:model', 'model':'evergarden:' + bone['item']}, 'item binding')
                exported = json.loads(pack.read(model_path))
                verify(len(cubes) == len(exported['elements']), 'every cube exported')
                exported_count += len(cubes)
                for cube, target in zip(cubes, exported['elements']):
                    for bound in ('from', 'to'):
                        for axis in range(3):
                            coordinate = target[bound][axis]
                            verify(-16 <= coordinate <= 32, 'valid vanilla model coordinate')
                            reconstructed = (coordinate-8)*bone['scale'] + group['origin'][axis]
                            verify(abs(reconstructed-cube[bound][axis]) < 1e-5, 'original cube bounds preserved')
                    rotation = cube.get('rotation', [0, 0, 0])
                    verify(bool(any(rotation)) == ('rotation' in target), 'rotation presence')
                    if any(rotation):
                        verify([target['rotation'][a] for a in 'xyz'] == rotation, 'exact compound cube rotation')
                        for axis in range(3):
                            value = (target['rotation']['origin'][axis]-8)*bone['scale'] + group['origin'][axis]
                            verify(abs(value-cube['origin'][axis]) < 1e-5, 'cube rotation pivot preserved')
                    for face, uv in cube['faces'].items():
                        if uv.get('texture') is None:
                            verify(face not in target['faces'], 'hidden face stays hidden')
                            continue
                        exported_face = target['faces'][face]
                        verify(exported_face['texture'] == '#' + str(uv['texture']), 'original face texture')
                        resolution = source['resolution']
                        expected = [uv['uv'][a]*16/resolution['width' if a % 2 == 0 else 'height'] for a in range(4)]
                        verify(exported_face['uv'] == expected, 'original UV rectangle')
                        verify(exported_face.get('rotation', 0) == uv.get('rotation', 0), 'original UV rotation')
            verify(exported_count == definition['cubes'], 'manifest cube count')
            total_cubes += exported_count
            for index, texture in enumerate(source['textures']):
                path = f'assets/evergarden/textures/item/pet_native/{model_id}_{index}.png'
                expected_paths.add(path)
                verify(pack.read(path) == base64.b64decode(texture['source'].split(',', 1)[1]), 'original texture bytes')
            verify(set(definition['clips']) == {'idle', 'walk', 'fly', 'sit', 'greet'}, 'all authored animations retained')
            group_indices = {group['uuid']: index for index, (group, _) in enumerate(groups)}
            for clip in source['animations']:
                native = definition['clips'][clip['name']]
                verify(native['length'] == clip['length'], 'authored clip duration')
                for uuid, animator in clip['animators'].items():
                    if uuid not in group_indices:
                        continue
                    native_track = native['bones'][str(group_indices[uuid])]
                    for channel in ('position', 'rotation'):
                        original_frames = sorted([f for f in animator.get('keyframes', []) if f['channel'] == channel], key=lambda f:f['time'])
                        verify(len(original_frames) == len(native_track[channel]), 'every animation keyframe retained')
                        for original, native_frame in zip(original_frames, native_track[channel]):
                            vector = [float(original['data_points'][0].get(axis, 0)) for axis in 'xyz']
                            vector[0] *= -1
                            if channel == 'rotation':
                                vector[1] *= -1
                            else:
                                vector = [v/16 for v in vector]
                            verify(native_frame == [original['time'], *vector], 'authored keyframe/handedness preserved')
        verify(set(pack.namelist()) == expected_paths, 'native pack contains only original pet assets')
    report = dict(result='PASS', checks=checks, variants=len(models), species=species_count, clips_per_variant=5,
                  cubes=total_cubes, item_models=sum(bool(b['item']) for m in models.values() for b in m['bones']),
                  source_hashes=source_hashes, pack_sha256=hashlib.sha256(pack_path.read_bytes()).hexdigest(),
                  minimum_java='1.21.11', modelengine_assets_modified=False, connected_client_playtest=False)
    output = ROOT / 'output/native-pet-assets-validation-20261021.json'
    output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(f"PASS {checks} checks; {len(models)} wild variants; {total_cubes} original cubes; {report['item_models']} native item bones")


if __name__ == '__main__':
    main()
