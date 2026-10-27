"""Validate the Evergarden main-boss rigs (Arkhaia, Seraphon) and their delivered Bedrock entities.

Checks the bone/clip contract against the Java tables, ModelEngine lane rules (override
clips key every body bone; only the 'aura' clip keys aura bones), geometry and UV
validity, alpha-cut sheets, Bedrock geometry/keyframe parity, floor clearance in every
clip, Arkhaia's sword contact on the damage tick and Seraphon's float. Offline checks:
not a connected-client render or FPS test.
"""
import base64
import io
import json
import math
import re
import sys
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'evergarden/tools'))
from boss_render import Rig  # noqa: E402

RESOURCES = ROOT / 'evergarden/src/main/resources/modelengine'
JAVA = (ROOT / 'evergarden/src/main/java/com/example/voidscape/boss/ArkhaiaBedrockSupport.java').read_text(encoding='utf8')
TIMING = dict(line.split('=', 1) for line in (RESOURCES / 'arkhaia-combat.properties').read_text().splitlines()
              if line and not line.startswith('#'))
BEHAVIOR_PREFIXES = ('h_', 'b_', 'ob_', 'seg_', 'i_', 'il_', 'ir_', 'l_', 'p_', 'tag_', 'g_', 'mount', 'seat')
checks = 0


def check(ok, label):
    global checks
    assert ok, label
    checks += 1


def java_list(name):
    return re.findall(r'"([a-z_]+)"', re.search(name + r'=List\.of\((.*?)\);', JAVA, re.S).group(1))


SPECS = {
    'arkhaia': dict(clips=java_list('CLIPS'), aura={'dread_halo', 'flame_r', 'flame_l', 'ember_ring'},
                    required={'root', 'facing', 'pelvis', 'chest', 'hi_gaze', 'head', 'head_forward', 'arm_r', 'forearm_r', 'hand_r',
                              'sword', 'sword_tip', 'arm_l', 'forearm_l', 'hand_l', 'leg_r', 'shin_r', 'foot_r', 'leg_l', 'shin_l',
                              'foot_l', 'wing_r', 'wing_tip_r', 'wing_l', 'wing_tip_l', 'cloak', 'cloak_tip'},
                    airborne={'hover', 'portals_windup', 'portals_active', 'eclipse_windup', 'eclipse_active'}),
    'seraphon': dict(clips=java_list('SERAPHON_CLIPS'), aura={'halo', 'halo_ring', 'orbit', 'sigil', 'orb'},
                     required={'root', 'facing', 'pelvis', 'chest', 'hi_gaze', 'head', 'head_forward', 'arm_r', 'forearm_r', 'hand_r',
                               'arm_l', 'forearm_l', 'hand_l', 'leg_r', 'shin_r', 'foot_r', 'leg_l', 'shin_l', 'foot_l'}
                     | {f'wing_{p}{k}_{s}' for p in ('upper', 'middle', 'lower') for k in ('', '_mid', '_tip') for s in 'rl'},
                     airborne=set()),
}


def groups(model):
    found = {}

    def visit(g):
        found[g['name']] = g
        for c in g['children']:
            if isinstance(c, dict):
                visit(c)
    for g in model['outliner']:
        visit(g)
    return found


with zipfile.ZipFile(ROOT / 'evergarden/dist/evergarden-bedrock.mcpack') as pack:
    bedrock_files = {n: pack.read(n) for n in pack.namelist() if '_native' in n and n.split('/')[-1].split('_native')[0] in SPECS}

for name, spec in SPECS.items():
    model = json.loads((RESOURCES / f'{name}.bbmodel').read_text(encoding='utf8'))
    manifest = json.loads((RESOURCES / f'{name}-manifest.json').read_text(encoding='utf8'))
    bones = groups(model)
    check(model['meta']['evergarden_animation_position_x'] == 'inverted', f'{name}: R4 translation convention flag')
    check(spec['required'] <= set(bones), f'{name}: contract bones {spec["required"] - set(bones)}')
    check(spec['aura'] <= set(bones), f'{name}: aura bones present')
    check(not [b for b in bones if b != 'hi_gaze' and b != 'hitbox' and b.startswith(BEHAVIOR_PREFIXES)], f'{name}: no accidental ME bone behaviors')
    uuids = [e['uuid'] for e in model['elements']] + [g['uuid'] for g in bones.values()]
    check(len(uuids) == len(set(uuids)), f'{name}: unique element/bone ids')
    # --- clips and ModelEngine lane rules -------------------------------------------
    clips = {c['name']: c for c in model['animations']}
    check([c['name'] for c in model['animations']] == spec['clips'] + ['aura'], f'{name}: clip order matches the Java/Bedrock table')
    check(all(c['override'] for c in model['animations']), f'{name}: every clip overrides lower lanes for the bones it keys')
    body = {n for n, g in bones.items() if n not in spec['aura'] and n not in ('hitbox', 'hi_gaze', 'root', 'facing', 'head_forward', 'sword_tip')
            and not n.startswith('fx_')}
    for clip in spec['clips']:
        keyed = {a['name']: a for a in clips[clip]['animators'].values()}
        check(not (set(keyed) & spec['aura']) and 'hi_gaze' not in keyed, f'{name}/{clip}: aura and gaze stay unkeyed')
        check(body <= set(keyed), f'{name}/{clip}: keys every body bone {body - set(keyed)}')
        for bone in body:
            frames = [f for f in keyed[bone]['keyframes'] if f['channel'] == 'rotation']
            check(len(frames) >= 2 and frames[0]['time'] == 0 and abs(frames[-1]['time'] - clips[clip]['length']) < 1e-6,
                  f'{name}/{clip}/{bone}: full-length rotation track')
    aura = {a['name'] for a in clips['aura']['animators'].values()}
    check(aura == spec['aura'] and clips['aura']['loop'] == 'loop', f'{name}: aura loop keys only aura bones')
    if name == 'arkhaia':
        for action in ('light', 'heavy', 'rift', 'nova', 'portals', 'eclipse'):
            wind, active, impact, recovery, _ = [int(v) / 20 for v in TIMING[action].split(',')]
            for phase, length in (('windup', wind), ('active', active), ('recover', recovery)):
                check(abs(clips[f'{action}_{phase}']['length'] - length) < 1e-9, f'{action}_{phase}: combat clock length')
        check(abs(clips['stun']['length'] - int(TIMING['stun']) / 20) < 1e-9, 'stun: combat clock length')
    # --- geometry, UVs and alpha-cut sheets -----------------------------------------
    texture = Image.open(io.BytesIO(base64.b64decode(model['textures'][0]['source'].split(',', 1)[1]))).convert('RGBA')
    alpha = np.asarray(texture)[..., 3]
    size = model['resolution']['width']
    check(texture.size == (size, size) == (1024, 1024), f'{name}: 1024 atlas')
    sheets = 0
    for e in model['elements']:
        if e['name'] == 'hitbox':
            continue
        values = e['from'] + e['to'] + e['origin'] + e['rotation']
        check(all(math.isfinite(v) for v in values) and all(a < b for a, b in zip(e['from'], e['to'])), f'{name}: finite cuboid {e["name"]}')
        textured = {f: d for f, d in e['faces'].items() if d.get('texture') is not None}
        check(set(e['faces']) == {'north', 'south', 'east', 'west', 'up', 'down'}, f'{name}: six declared faces {e["name"]}')
        for face, d in textured.items():
            u0, v0, u1, v1 = d['uv']
            check(0 <= min(u0, u1) and max(u0, u1) <= size and 0 <= min(v0, v1) and max(v0, v1) <= size, f'{name}: UV inside atlas')
        if len(textured) == 2:
            sheets += 1
            u0, v0, u1, v1 = textured['south']['uv']
            region = alpha[int(v0):int(v1), int(min(u0, u1)):int(max(u0, u1))]
            check(region.size and region.max() == 255, f'{name}: sheet {e["name"]} has visible texels')
    check(sheets >= 30, f'{name}: alpha-cut sheets present ({sheets})')
    check(manifest['cubes'] == len(model['elements']) - 1 and manifest['revision'] in model['meta']['evergarden_revision'],
          f'{name}: manifest matches blueprint')
    # --- Bedrock parity -------------------------------------------------------------
    get = lambda path: json.loads(bedrock_files[path])
    entity = get(f'entity/{name}_native.entity.json')['minecraft:client_entity']['description']
    geometry = get(f'models/entity/{name}_native.geo.json')['minecraft:geometry'][0]
    animations = get(f'animations/{name}_native.animation.json')['animations']
    controller = get(f'animation_controllers/{name}_native.controller.json')['animation_controllers'][f'controller.animation.voidscape.{name}']
    render = get(f'render_controllers/{name}_native.render.json')['render_controllers'][f'controller.render.voidscape.{name}']
    check(entity['identifier'] == f'voidscape:{name}' and entity['scripts']['animate'] == ['combat', 'look', 'aura'], f'{name}: entity layers')
    check(render.get('ignore_lighting') is True, f'{name}: full-bright like the Java rig')
    check(bedrock_files[f'textures/entity/{name}_native.png'] == base64.b64decode(model['textures'][0]['source'].split(',', 1)[1]),
          f'{name}: same atlas on both clients')
    check(list(controller['states']) == spec['clips'], f'{name}: controller states follow the property index')
    for index, clip in enumerate(spec['clips']):
        others = controller['states'][clip]['transitions']
        check(len(others) == len(spec['clips']) - 1 and all(list(t.values())[0] == f"q.property('voidscape:clip') == {spec['clips'].index(list(t)[0])}"
                                                             for t in others), f'{name}/{clip}: property transitions')
    geo_bones = {b['name']: b for b in geometry['bones']}
    elements = {e['uuid']: e for e in model['elements']}
    for gname, g in bones.items():
        if gname == 'hitbox':
            continue
        b = geo_bones['gaze' if gname == 'hi_gaze' else gname]
        check(np.allclose(b['pivot'], [-g['origin'][0] * 3.2, g['origin'][1] * 3.2, g['origin'][2] * 3.2]), f'{name}/{gname}: pivot')
        owned = [elements[c] for c in g['children'] if isinstance(c, str)]
        check(len(owned) == len(b['cubes']), f'{name}/{gname}: cube ownership')
        for e, cube in zip(owned, b['cubes']):
            expected = {f for f, d in e['faces'].items() if d.get('texture') is not None}
            check(set(cube['uv']) == expected, f'{name}/{gname}: only textured faces exported')
    for clip in model['animations']:
        exported = animations[f'animation.voidscape.{name}.{clip["name"]}']
        check(exported['animation_length'] == clip['length'], f'{name}/{clip["name"]}: Bedrock length')
        for animator in clip['animators'].values():
            for frame in animator['keyframes']:
                v = [float(frame['data_points'][0][k]) for k in 'xyz']
                if frame['channel'] == 'position':
                    v = [v[0] * 3.2, v[1] * 3.2, v[2] * 3.2]
                actual = exported['bones'][animator['name']][frame['channel']][str(frame['time'])]
                check(np.allclose(actual.get('post', actual) if isinstance(actual, dict) else actual, v, atol=1e-4),
                      f'{name}/{clip["name"]}/{animator["name"]}: converted keyframe')
    # --- poses: finite, floor clearance, contact and float --------------------------
    rig = Rig(model)
    for clip in spec['clips']:
        length = clips[clip]['length']
        lowest = math.inf
        for t in np.linspace(0, length, 13):
            matrices = rig.matrices(clip, float(t), aura_time=0)
            check(all(np.isfinite(m).all() for m in matrices.values()), f'{name}/{clip}: finite transforms')
            for e, world, bone in rig.cuboids(matrices):
                if bone in spec['aura']:
                    continue
                lowest = min(lowest, float(world[:, 1].min()))
        floor = -5.0 if clip in spec['airborne'] else -.6
        check(lowest >= floor, f'{name}/{clip}: geometry stays above the floor ({lowest:.2f})')
        if name == 'seraphon' and clip in ('idle', 'walk', 'hover', 'cast_windup', 'smite_active', 'wings_active'):
            check(lowest > .5, f'seraphon/{clip}: floats above the ground ({lowest:.2f})')
    if name == 'arkhaia':
        for action in ('light', 'heavy'):
            wind, active, impact, recovery, _ = [int(v) / 20 for v in TIMING[action].split(',')]
            tip = rig.point(rig.matrices(action + '_active', impact), 'sword_tip') * 3.2 / 16
            check(-tip[2] > 3.5 and 1.0 < tip[1] < 2.3 and abs(tip[0]) < .45, f'{action}: contact meets a standing player in front {tip}')
            back = rig.point(rig.matrices(action + '_windup', wind), 'sword_tip') * 3.2 / 16
            check(back[2] > 1 and back[1] > 5, f'{action}: windup holds the blade behind the crown {back}')
print(f'PASS {checks} Evergarden main-boss checks: contract, lanes, geometry, alpha sheets, Bedrock parity, floor, contact, float')
