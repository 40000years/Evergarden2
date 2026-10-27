"""Rift Excavator and Smelter's Pickaxe: 3D on both editions, held where Java holds them.

Reads candidate packs: Java routing (GUI icon vs 3D model), the model is the sculpt, every texture
resolves; Bedrock attachable is the java2bedrock rig, cube for cube, every corner held where Java's
ItemInHandLayer holds it in third person, first person the java2bedrock map. Arithmetic, no client.

    python tests/weapon-remodel/check_relic_pickaxes.py [--dist evergarden/dist]
"""
import argparse
import io
import itertools
import json
from pathlib import Path
import sys
import zipfile

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).parent))
sys.path.insert(0, str(ROOT / 'tools'))
sys.path.insert(0, str(ROOT / 'evergarden/tools'))
from hold_math import bedrock_hand, exported_values, java2bedrock, java_hand  # noqa: E402
import relic_pickaxe_assets as relic  # noqa: E402

checks = 0


def check(ok, label):
    global checks
    assert ok, label
    checks += 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dist', type=Path, default=ROOT / 'evergarden/dist')
    args = parser.parse_args()
    mappings = json.loads((args.dist / 'geyser-mappings.json').read_text(encoding='utf8'))
    rows = {row['bedrock_identifier']: row for row in mappings['items']['minecraft:netherite_pickaxe']}
    with zipfile.ZipFile(args.dist / 'evergarden-java.zip') as java, zipfile.ZipFile(args.dist / 'evergarden-bedrock.mcpack') as bedrock:
        routing = json.loads(java.read('assets/minecraft/items/netherite_pickaxe.json'))['model']
        check(routing['property'] == 'minecraft:custom_model_data' and routing['fallback']['model'] == 'minecraft:item/netherite_pickaxe',
              'vanilla netherite pickaxes keep their own model')
        atlas = json.loads(bedrock.read('textures/item_texture.json'))['texture_data']
        animations = json.loads(bedrock.read('animations/relic_tools.animation.json'))['animations']
        controllers = json.loads(bedrock.read('render_controllers/relic_tools.render.json'))['render_controllers']
        for name in relic.PICKAXES:
            sculpt = relic.make_pickaxe(name)
            case = next(c for c in routing['cases'] if c['when'] == f'voidscape:{name}')['model']
            check(case['property'] == 'minecraft:display_context' and case['cases'][0]['when'] == 'gui'
                  and case['cases'][0]['model']['model'] == f'voidscape:item/{name}_icon' and case['fallback']['model'] == f'voidscape:item/{name}',
                  f'{name}: GUI shows the icon, hands and world show the 3D model')
            model = json.loads(java.read(f'assets/voidscape/models/item/{name}.json'))
            check(len(model['elements']) == len(sculpt.cubes), f'{name}: Java model is the sculpt ({len(sculpt.cubes)} cubes)')
            check(all(-16 <= v <= 32 for e in model['elements'] for k in ('from', 'to') for v in e[k]), f'{name}: Java element bounds')
            for texture in model['textures'].values():
                check(bool(java.read('assets/' + texture.replace(':', '/textures/', 1) + '.png')), f'{name}: Java resolves {texture}')
            icon = Image.open(io.BytesIO(java.read(f'assets/voidscape/textures/item/{name}.png')))
            box = icon.getbbox()
            check(icon.size == (128, 128) and box[0] > 0 and box[2] < 128, f'{name}: 128 px 3D icon, not clipped')
            check(bedrock.read(atlas[rows[f'voidscape:{name}']['bedrock_options']['icon']]['textures'] + '.png') == java.read(f'assets/voidscape/textures/item/{name}.png'),
                  f'{name}: Bedrock hotbar icon is the same 3D render')

            attach = json.loads(bedrock.read(f'attachables/relic_tools/{name}.json'))['minecraft:attachable']['description']
            check(attach['identifier'] == f'voidscape:{name}', f'{name}: attachable matches the Geyser definition')
            for texture in attach['textures'].values():
                check(texture.startswith('textures/misc/') or bool(bedrock.read(texture + '.png')), f'{name}: Bedrock resolves {texture}')
            check(all(c in controllers for c in attach['render_controllers']) and all(a in animations for a in attach['animations'].values()),
                  f'{name}: render controller and animations resolve')
            geo = json.loads(bedrock.read(f'models/entity/relic_tools/{name}.geo.json'))['minecraft:geometry'][0]
            check(geo['description']['identifier'] == attach['geometry']['default'], f'{name}: geometry resolves')
            bones = {b['name']: b for b in geo['bones']}
            player = {'root', 'body', 'waist', 'head', 'hat', 'cape', 'leftarm', 'rightarm', 'leftleg', 'rightleg', 'leftitem', 'rightitem',
                      'leftsleeve', 'rightsleeve', 'leftpants', 'rightpants', 'jacket'}
            clashes = [n for n, b in bones.items() if n.lower() in player and not b.get('binding')]
            check(not clashes, f'{name}: no unbound bone shares a player bone name (Bedrock would glue it to the wearer): {clashes}')
            check(bones['root'].get('binding') == 'q.item_slot_to_bone_name(context.item_slot)', f'{name}: bound to the item slot')
            cubes = [(b['name'], c) for b in geo['bones'] for c in b.get('cubes', [])]
            check(len(cubes) == len(sculpt.cubes), f'{name}: Bedrock body is the sculpt, cube for cube')
            display = model['display']
            first = animations[attach['animations']['first']]['bones']
            third = animations[attach['animations']['third']]['bones']
            for clip, is_first in ((first, True), (third, False)):
                expected = java2bedrock(display, is_first)
                for off_hand in (False, True):
                    for bone, (rotation, position, scale) in expected[off_hand].items():
                        got = exported_values(clip, bone, off_hand)
                        check(np.allclose(got[0], rotation, atol=1e-4) and np.allclose(got[1], position, atol=1e-4) and np.allclose(got[2], scale, atol=1e-4),
                              f'{name}: {"first" if is_first else "third"}-person {bone} is the java2bedrock map of the Java display')
            worst = 0.0
            for bone, cube in cubes:
                lo = np.array(cube['origin'], dtype=float)
                for corner in (np.array(p) for p in itertools.product(*zip(lo, lo + cube['size']))):
                    java_point = np.array([8 - corner[0], corner[1], corner[2] + 8])
                    worst = max(worst, float(np.abs(bedrock_hand(geo['bones'], third, bone, corner) - java_hand(display['thirdperson_righthand'], java_point)).max()))
            check(worst < 1e-3, f'{name}: every corner held where Java holds it in third person (max {worst:.2e} px)')
            grip = np.array([8, relic.GRIP_Y, 8.0])
            check(any(all(e['from'][i] <= v <= e['to'][i] for i, v in enumerate(grip)) for e in model['elements']), f'{name}: the hand closes on the handle')
    print(f'PASS {checks} relic pickaxe checks (3D on Java and Bedrock, Bedrock hold = Java; no client test)')


if __name__ == '__main__':
    main()
