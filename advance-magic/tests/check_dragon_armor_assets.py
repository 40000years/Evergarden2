"""Check the Dragon Conqueror armor sets inside an Advance Magic JAR's embedded packs.

    python advance-magic/tests/check_dragon_armor_assets.py [--jar dist/advance-magic.jar] [--base JAR]

Java: item definitions, icon and 3D helm models (bounds, UVs, glow flags), equipment asset with
the cape (wings) layer, 4x textures. Bedrock: attachables, geometries (per-face UVs inside the
atlas, cape bones under `body`), render controllers, item atlas, emissive helm texels. Geyser rows
with doubled protection. Java and Bedrock helms carry the same cuboids. With --base, every entry
that is not a dragon entry must be byte-identical to the base JAR's packs.
"""
import argparse
import io
import json
import sys
import zipfile
from pathlib import Path

from PIL import Image
import numpy as np

ROOT = Path(__file__).resolve().parents[2]
NS = 'advance_magic'
SETS = ('infernal_drake', 'frostwyrm', 'tempest_drake')
PIECES = ('helm', 'chestplate', 'leggings', 'boots')
PROTECTION = {'helm': 6, 'chestplate': 16, 'leggings': 12, 'boots': 6}
PLAYER_BONES = {'root', 'waist', 'body', 'head', 'hat', 'cape', 'rightArm', 'leftArm', 'rightLeg', 'leftLeg', 'rightItem', 'leftItem',
                'jacket', 'rightSleeve', 'leftSleeve', 'rightPants', 'leftPants', 'helmet', 'rightLegging', 'leftLegging', 'body'}
BOUND = {'head', 'body', 'rightArm', 'leftArm', 'rightLeg', 'leftLeg'}
passed = 0


def check(condition, label):
    global passed
    if not condition:
        raise AssertionError(label)
    passed += 1


def packs(jar):
    with zipfile.ZipFile(jar) as z:
        def inner(name):
            with zipfile.ZipFile(io.BytesIO(z.read('resource-packs/' + name))) as p:
                return {n: p.read(n) for n in p.namelist() if not n.endswith('/')}
        return inner('advance-magic-java.zip'), inner('advance-magic-bedrock.mcpack'), json.loads(z.read('resource-packs/geyser-mappings.json'))


def image(data):
    return Image.open(io.BytesIO(data))


def dragon(name):
    return any(k in name for k in SETS)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--jar', type=Path, default=ROOT / 'dist/advance-magic.jar')
    parser.add_argument('--base', type=Path)
    args = parser.parse_args()
    java, bedrock, geyser = packs(args.jar)
    manifest = json.loads(bedrock['manifest.json'])
    check(manifest['header']['version'] >= [2, 40, 0], 'Bedrock pack version carries the dragon sets')
    item_atlas = json.loads(bedrock['textures/item_texture.json'])['texture_data']
    controllers = {}
    for name, data in bedrock.items():
        if name.startswith('render_controllers/'):
            controllers.update(json.loads(data)['render_controllers'])
    rows = {r['bedrock_identifier']: (material, r) for material, rs in geyser['items'].items() for r in rs}
    for k in SETS:
        # ---- Java
        equipment = json.loads(java[f'assets/{NS}/equipment/{k}.json'])['layers']
        check(set(equipment) == {'humanoid', 'humanoid_leggings', 'wings'}, f'{k} equipment asset has armor and cape layers')
        for layer in ('humanoid', 'humanoid_leggings', 'wings'):
            tex = image(java[f'assets/{NS}/textures/entity/equipment/{layer}/{k}.png'])
            check(tex.size == (256, 128), f'{k} {layer} texture is 4x the 64x32 layout')
        cape = np.asarray(image(java[f'assets/{NS}/textures/entity/equipment/wings/{k}.png']).convert('RGBA'))
        check(cape[8:88, 144:184, 3].mean() > 100 and cape[8:88, 96:136, 3].max() == 0, f'{k} cape paints the outer face only')
        for piece in PIECES:
            item = json.loads(java[f'assets/{NS}/items/{k}_{piece}.json'])['model']
            icon = f'{NS}:item/{k}/{piece}_icon'
            if piece == 'helm':
                check(item['type'] == 'minecraft:select' and item['fallback']['model'] == f'{NS}:item/{k}/helm'
                      and item['cases'][0]['model']['model'] == icon, f'{k} helm shows 3D on the head and the icon elsewhere')
            else:
                check(item == {'type': 'minecraft:model', 'model': icon}, f'{k} {piece} item uses its icon')
            model = json.loads(java[f'assets/{NS}/models/item/{k}/{piece}_icon.json'])
            check(f"assets/{NS}/textures/item/{k}/{piece}_icon.png" in java and model['textures']['layer0'] == icon, f'{k} {piece} icon texture')
            check(image(java[f'assets/{NS}/textures/item/{k}/{piece}_icon.png']).size == (64, 64), f'{k} {piece} icon is 64px')
        helm = json.loads(java[f'assets/{NS}/models/item/{k}/helm.json'])
        atlas = image(java[f'assets/{NS}/textures/item/{k}/helm.png']).convert('RGBA')
        check(atlas.size[0] == atlas.size[1] and helm['textures']['helm'] == f'{NS}:item/{k}/helm', f'{k} helm atlas is square and referenced')
        check(set(np.unique(np.asarray(atlas)[..., 3])) <= {0, 255}, f'{k} Java helm atlas is opaque or cut out (no emissive alpha)')
        faces = 0
        for e in helm['elements']:
            check(all(-16 <= v <= 32 for v in e['from'] + e['to']) and all(a < b for a, b in zip(e['from'], e['to'])), f'{k} helm element in bounds')
            for face, f in e['faces'].items():
                check(f['texture'] == '#helm' and all(0 <= v <= 16 for v in f['uv']) and f['uv'][0] < f['uv'][2] and f['uv'][1] < f['uv'][3],
                      f'{k} helm {face} UV inside the atlas')
                faces += 1
            if e.get('light_emission'):
                check(e['shade'] is False and e['light_emission'] == 15, f'{k} glowing helm element is full bright')
        check(400 < len(helm['elements']) < 3000, f"{k} helm cuboid count {len(helm['elements'])} is sane")
        check(helm['display']['head']['scale'] == [.8, .8, .8], f'{k} helm head transform')
        # ---- Bedrock
        geo = json.loads(bedrock[f'models/entity/{k}/helm.geo.json'])['minecraft:geometry'][0]
        tw, th = geo['description']['texture_width'], geo['description']['texture_height']
        btex = image(bedrock[f'textures/{k}/helm.png']).convert('RGBA')
        check(btex.size == (tw, th) == atlas.size, f'{k} Bedrock helm atlas matches its geometry')
        alpha = np.asarray(btex)[..., 3]
        check(((alpha > 0) & (alpha < 255)).any(), f'{k} Bedrock glow texels carry emissive alpha')
        bones = {b['name']: b for b in geo['bones']}
        check(set(bones) == {'head', 'helm_glow'} and bones['helm_glow']['parent'] == 'head', f'{k} helm bones')
        cubes = [c for b in geo['bones'] for c in b['cubes']]
        check(len(cubes) == len(helm['elements']), f'{k} Java and Bedrock helms carry the same cuboids')
        bfaces = 0
        for c in cubes:
            for face, uv in c['uv'].items():
                u0, v0 = uv['uv']
                u1, v1 = u0 + uv['uv_size'][0], v0 + uv['uv_size'][1]
                check(0 <= min(u0, u1) and max(u0, u1) <= tw and 0 <= min(v0, v1) and max(v0, v1) <= th, f'{k} Bedrock {face} UV inside the atlas')
                check((uv['uv_size'][0] < 0) == (face in ('up', 'down')), f'{k} Bedrock {face} UV orientation follows Blockbench')
                bfaces += 1
        check(bfaces == faces, f'{k} same visible faces on both editions')
        for piece in PIECES:
            att = json.loads(bedrock[f'attachables/{k}/{piece}.json'])['minecraft:attachable']['description']
            check(att['identifier'] == f'{NS}:{k}_{piece}', f'{k} {piece} attachable id')
            texture = att['textures']['default']
            check(texture + '.png' in bedrock, f'{k} {piece} attachable texture exists')
            gid = att['geometry']['default']
            g = json.loads(bedrock[f'models/entity/{k}/{piece}.geo.json'])['minecraft:geometry'][0]
            check(g['description']['identifier'] == gid, f'{k} {piece} geometry id')
            for rc in att['render_controllers']:
                (name, condition), = rc.items()
                check(name in controllers and 'main_hand' in condition, f'{k} {piece} render controller hidden while held')
            for b in g['bones']:
                check(b['name'] in BOUND or b['name'] not in PLAYER_BONES, f'{k} {piece} bone {b["name"]} is not glued to a player bone by accident')
            key = f'{NS}.{k}_{piece}'
            check(key in item_atlas and item_atlas[key]['textures'] + '.png' in bedrock, f'{k} {piece} Bedrock icon')
            material, row = rows[f'{NS}:{k}_{piece}']
            check(row['bedrock_options']['protection_value'] == PROTECTION[piece] and material.startswith('minecraft:netherite_'),
                  f'{k} {piece} Geyser row with doubled protection')
        chest = json.loads(bedrock[f'models/entity/{k}/chestplate.geo.json'])['minecraft:geometry'][0]
        mantles = [b for b in chest['bones'] if b['name'].startswith('drake_mantle')]
        check(len(mantles) == 2 and all(b['parent'] == 'body' for b in mantles) and chest['description']['texture_height'] == 64,
              f'{k} Bedrock cape hangs from the body')
        check(sorted(tuple(b['rotation']) for b in mantles) == [(15, 0, -15), (15, 0, 15)], f'{k} Bedrock cape matches the rest elytra pose')
        check(image(bedrock[f'textures/{k}/chest.png']).size == (256, 256), f'{k} Bedrock chest texture holds armor and cape')
    if args.base:
        bjava, bbedrock, bgeyser = packs(args.base)
        for name, old, new in (('java', bjava, java), ('bedrock', bbedrock, bedrock)):
            for n, data in old.items():
                if n not in ('textures/item_texture.json', 'manifest.json'):
                    check(new.get(n) == data, f'{name} {n} unchanged from base')
            check(all(dragon(n) for n in set(new) - set(old)), f'{name}: only dragon entries were added')
        old_rows = [r for rs in bgeyser['items'].values() for r in rs]
        new_rows = [r for rs in geyser['items'].values() for r in rs]
        check(all(r in new_rows for r in old_rows) and len(new_rows) - len(old_rows) == 12, 'Geyser keeps every old row and adds 12')
    print(f'PASS {passed} dragon armor asset checks')


if __name__ == '__main__':
    try:
        main()
    except AssertionError as error:
        print(f'FAIL after {passed} checks: {error}')
        sys.exit(1)
