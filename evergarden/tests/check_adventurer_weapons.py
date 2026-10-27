"""Rare Adventurer weapons: routing, animated VFX/icons, cube parity, sources and budgets.

Reads the shipped evergarden packs and mappings. Hold transforms are checked
separately by tests/weapon-remodel/check_weapon_hold.py. No client rendering.
"""
import argparse
import base64
import io
import json
from pathlib import Path
import sys
import zipfile

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
sys.path.insert(0, str(ROOT / 'evergarden/tools'))
from adventurer_weapon_assets import (ART, BY_ID, CATALOG, DRAW_TICKS, EMISSIVE, FULL_DRAW, RANGED_STATES, VANILLA_STATE, anchor, atlas as material_atlas,  # noqa: E402
                                      java_draw_states, make_weapon, ranged_sprite, sprite_fit)
from weapon_forge import bedrock_point  # noqa: E402

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--dist', type=Path, default=ROOT / 'evergarden/dist', help='Folder with candidate packs and mappings')
parser.add_argument('--art', type=Path, default=ART, help='Folder with the matching source art')
args = parser.parse_args()
DIST, ART = args.dist, args.art
CUBE_BUDGET = 650
checks = 0


def check(condition, label):
    global checks
    assert condition, label
    checks += 1


def document(archive, path):
    return json.loads(archive.read(path))


def models(node):
    """Every model referenced by an item definition tree."""
    if isinstance(node, dict):
        if node.get('type') == 'minecraft:model':
            yield node['model']
        for value in node.values():
            yield from models(value)
    elif isinstance(node, list):
        for value in node:
            yield from models(value)


def ranged(java, bedrock, atlas, animations, controllers, name, title):
    """Bow / crossbow: one authored model per draw state on Java, a sprite of it per state on Bedrock, static icons, loaded definitions."""
    category, states = BY_ID[name][5], java_draw_states(name)

    def suffix(state):
        return '' if state == 'standby' else '_' + state

    item = document(java, f'assets/voidscape/items/adventurer_{name}.json')['model']
    check(item['property'] == 'minecraft:display_context' and item['cases'][0]['when'] == 'gui', name + ': GUI icon routing')
    held, icons = set(models(item['fallback'])), set(models(item['cases'][0]['model']))
    check(held == {f'voidscape:item/adventurer_weapons/{name}{suffix(s)}' for s in states}, name + ': held model for every draw state')
    check(icons == {f'voidscape:item/adventurer_weapons/{name}{suffix(s)}_icon' for s in states}, name + ': hotbar icon for every draw state')
    text = json.dumps(item)
    if category == 'bow':
        check('minecraft:use_duration' in text and '0.65' in text and '0.9' in text and '"scale": 0.05' in text, name + ': vanilla bow pull thresholds')
    else:
        check('minecraft:crossbow/pull' in text and 'minecraft:charge_type' in text and '0.58' in text, name + ': vanilla crossbow pull and charge thresholds')
    identifier = f'voidscape:adventurer_{name}'
    base_item, row = rows[identifier]
    check(base_item == 'minecraft:' + BY_ID[name][4] and row['display_name'] == title, name + ': Geyser base item and title')
    check(row['bedrock_options']['allow_offhand'] and row['bedrock_options']['display_handheld'], name + ': both hands, handheld')
    check(isinstance(atlas[row['bedrock_options']['icon']]['textures'], str), name + ': one static Bedrock UI texture (no frame atlas)')
    check(bedrock.read(atlas[row['bedrock_options']['icon']]['textures'] + '.png') == (ART / f'{name}_icon.png').read_bytes(),
          name + ': Bedrock icon is the shipped art')
    attach = document(bedrock, f'attachables/adventurer_weapons/{name}.json')['minecraft:attachable']['description']
    check(attach['identifier'] == identifier, name + ': attachable matches mapping')
    first, second = DRAW_TICKS[category]
    script = ' '.join(attach['scripts']['pre_animation'])
    check('q.main_hand_item_use_duration > 0.0' in script and f'< {first} ?' in script and f'< {second} ?' in script,
          name + ': draw frame from elapsed use ticks')
    check(f') / {FULL_DRAW[category]:.1f}, 0.0, 1.0);' in script, name + ': first-person full-draw shake at Java full power')
    keys = {state: VANILLA_STATE[category][state][1] for state in states}
    controller = controllers[attach['render_controllers'][0]]
    check(controller['geometry'] == 'Array.held_geometry[v.draw_frame]', name + ': geometry follows the draw frame')
    check(controller['arrays']['geometries']['Array.held_geometry'] == ['Geometry.' + keys[s] for s in states[:4]],
          name + ': standby + three pull geometries')
    for clip in attach['animations'].values():
        check(clip in animations, name + ': resolves ' + clip)
    tex = material_atlas()
    fit = sprite_fit(name, [make_weapon(name, s) for s in states])
    for state in states:
        source = make_weapon(name, state)
        check(0 < len(source.cubes) <= CUBE_BUDGET, f'{name} {state}: {len(source.cubes)} cubes within budget')
        model = document(java, f'assets/voidscape/models/item/adventurer_weapons/{name}{suffix(state)}.json')
        check(len(model['elements']) == len(source.cubes), f'{name} {state}: Java model is the sculpt, no VFX planes')
        check(all(-16 <= v <= 32 for e in model['elements'] for p in ('from', 'to') for v in e[p]), f'{name} {state}: Java element bounds')
        check(all(e.get('light_emission') == 15 for e, c in zip(model['elements'], source.cubes) if c['mat'] in EMISSIVE),
              f'{name} {state}: emissive parts glow')
        sprite = Image.open(io.BytesIO(bedrock.read(attach['textures'][keys[state]] + '.png'))).convert('RGBA')
        check(sprite.tobytes() == ranged_sprite(source, tex, fit).tobytes(), f'{name} {state}: Bedrock sprite is a current render of the sculpt')
        geo_ids = {g['description']['identifier'] for g in document(bedrock, f'models/entity/adventurer_weapons/{name}.geo.json')['minecraft:geometry']}
        check(attach['geometry'][keys[state]] in geo_ids, f'{name} {state}: attachable resolves its geometry')
        icon = Image.open(io.BytesIO(java.read(f'assets/voidscape/textures/item/adventurer_weapons/{name}{suffix(state)}_icon.png'))).convert('RGBA')
        box = icon.getbbox()
        check(icon.size == (128, 128) and box[0] > 0 and box[2] < 128, f'{name} {state}: static 128 px icon, not clipped')
    if category == 'crossbow':
        for charge, state in (('arrow', 'arrow'), ('rocket', 'firework')):
            loaded = f'{identifier}_loaded_{charge}'
            base_item, row = rows[loaded]
            check(base_item == 'minecraft:crossbow' and row['predicate'] == {'type': 'match', 'property': 'charge_type', 'value': charge},
                  f'{name}: {charge} charge_type definition')
            alias = document(bedrock, f'attachables/adventurer_weapons/{name}_loaded_{charge}.json')['minecraft:attachable']['description']
            shown = controllers[alias['render_controllers'][0]]
            check(alias['identifier'] == loaded and shown['geometry'] == f'Geometry.{keys[state]}' and shown['textures'][0] == f'Texture.{keys[state]}',
                  f'{name}: loaded {charge} shows its loaded sprite')
            check(alias['scripts'] == attach['scripts'] and alias['animations'] == attach['animations'], f'{name}: loaded {charge} is held like the crossbow')
            check(len(bedrock.read(atlas[row['bedrock_options']['icon']]['textures'] + '.png')) > 0, f'{name}: loaded {charge} icon resolves')
    bb = json.loads((ART / f'adventurer_{name}.bbmodel').read_text(encoding='utf8'))
    check(len([e for e in bb['elements'] if e['name'] != 'hitbox']) == len(make_weapon(name, states[3]).cubes), name + ': editable Blockbench source parity')
    check(json.loads((ART / f'{name}.geometry.json').read_text(encoding='utf8'))['cubes'] == make_weapon(name).cubes, name + ': source geometry file is current')


mapping = json.loads((DIST / 'geyser-mappings.json').read_text(encoding='utf8'))
rows = {row['bedrock_identifier']: (base, row) for base, values in mapping['items'].items() for row in values}
with zipfile.ZipFile(DIST / 'evergarden-java.zip') as java, zipfile.ZipFile(DIST / 'evergarden-bedrock.mcpack') as bedrock:
    atlas = document(bedrock, 'textures/item_texture.json')['texture_data']
    animations = document(bedrock, 'animations/adventurer_weapons.animation.json')['animations']
    controllers = document(bedrock, 'render_controllers/adventurer_weapons.render.json')['render_controllers']
    names = set(java.namelist()) | set(bedrock.namelist())
    for name, title, *_rest in CATALOG:
        if BY_ID[name][5] in RANGED_STATES:
            ranged(java, bedrock, atlas, animations, controllers, name, title)
            continue
        source = make_weapon(name)
        check(0 < len(source.cubes) <= CUBE_BUDGET, f'{name}: {len(source.cubes)} cubes within budget')
        item = document(java, f'assets/voidscape/items/adventurer_{name}.json')['model']
        check(item['property'] == 'minecraft:display_context' and item['cases'][0]['when'] == 'gui', name + ': GUI icon routing')
        check(item['fallback']['model'] == f'voidscape:item/adventurer_weapons/{name}', name + ': held 3D model routing')
        model = document(java, f'assets/voidscape/models/item/adventurer_weapons/{name}.json')
        check(len(model['elements']) == len(source.cubes) + 3, name + ': 3D body plus front/back/side VFX planes')
        check(all(-16 <= v <= 32 for e in model['elements'] for p in ('from', 'to') for v in e[p]), name + ': Java element bounds')
        check(all(e.get('light_emission') == 15 for e, c in zip(model['elements'], source.cubes) if c['mat'] in EMISSIVE), name + ': emissive inlays glow')
        for texture in model['textures'].values():
            check(bool(java.read('assets/' + texture.replace(':', '/textures/', 1) + '.png')), name + ': resolves ' + texture)
        base = f'assets/voidscape/textures/item/adventurer_weapons/{name}'
        for kind in ('fx', 'icon'):
            strip = Image.open(io.BytesIO(java.read(f'{base}_{kind}.png'))).convert('RGBA')
            check(strip.size == (128, 1024), f'{name}: 8 square {kind} frames')
            check(document(java, f'{base}_{kind}.png.mcmeta')['animation']['frametime'] == 2, f'{name}: 10 fps {kind}')
            frames = [strip.crop((0, f * 128, 128, (f + 1) * 128)) for f in range(8)]
            check(len({f.tobytes() for f in frames}) >= 4, f'{name}: {kind} visibly animates')
            check(all(f.getchannel('A').getextrema() == (0, 255) for f in frames), f'{name}: {kind} cutout alpha')
            if kind == 'icon':
                check(all(f.getbbox()[0] > 0 and f.getbbox()[2] < 128 for f in frames), f'{name}: icon not clipped')
        identifier = f'voidscape:adventurer_{name}'
        base_item, row = rows[identifier]
        check(base_item == 'minecraft:' + BY_ID[name][4] and row['display_name'] == title, name + ': Geyser base item and title')
        check(row['bedrock_options']['allow_offhand'] and row['bedrock_options']['display_handheld'], name + ': both hands, handheld')
        attach = document(bedrock, f'attachables/adventurer_weapons/{name}.json')['minecraft:attachable']['description']
        check(attach['identifier'] == identifier, name + ': attachable matches mapping')
        check(bedrock.read(atlas[row['bedrock_options']['icon']]['textures'] + '.png') == (ART / f'{name}_icon.png').read_bytes(), name + ': Bedrock icon is the shipped art')
        for clip in attach['animations'].values():
            check(clip in animations, name + ': resolves ' + clip)
        for controller in attach['render_controllers']:
            check(controller in controllers, name + ': resolves ' + controller)
        for texture in attach['textures'].values():
            check(bool(bedrock.read(texture + '.png')), name + ': resolves ' + texture)
        geo = document(bedrock, f'models/entity/adventurer_weapons/{name}.geo.json')['minecraft:geometry'][0]
        cubes = [c for b in geo['bones'] for c in b.get('cubes', [])]
        check(len(cubes) == len(source.cubes), name + ': Bedrock cube count matches the sculpt')
        for bone in geo['bones']:
            if bone['name'] in ('ribbon', 'focus'):
                check(bone['pivot'] == bedrock_point(anchor(name, bone['name'])), f'{name}: {bone["name"]} sways from its attachment point')
        bb = json.loads((ART / f'adventurer_{name}.bbmodel').read_text(encoding='utf8'))
        check(len([e for e in bb['elements'] if e['name'] != 'hitbox']) == len(source.cubes), name + ': editable Blockbench source parity')
        check(Image.open(io.BytesIO(base64.b64decode(bb['textures'][0]['source'].split(',')[1]))).size == (128, 128), name + ': embedded texture')
        geometry = json.loads((ART / f'{name}.geometry.json').read_text(encoding='utf8'))
        check(geometry['cubes'] == source.cubes, name + ': source geometry file is current')
print(f'PASS {checks} adventurer weapon asset checks (18 melee, bow and crossbow)')
