"""Validate the four realm-boss rigs and their native Bedrock entities.

Checks the bone/clip contract against RealmBosses.java, ModelEngine lane rules (override
clips key every body bone; only 'aura' keys aura bones), geometry and UV validity,
alpha-cut sheets, the per-bone reach ModelEngine renders safely, the display budget,
Bedrock geometry/keyframe parity and floor clearance in every grounded clip. Offline:
not a connected-client render.

    python tests/model-engine/check_realm_bosses.py [--pack PATH-TO-evergarden-bedrock.mcpack] [--bedrock-end-textures original|brightened]
"""
import argparse
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
sys.path.insert(0, str(ROOT / 'tools'))
from boss_forge import rot  # noqa: E402
from boss_render import Rig  # noqa: E402
from realm_forge import displays  # noqa: E402
from brighten_end_boss_bedrock import GAMMA, brighten_png  # noqa: E402

RESOURCES = ROOT / 'evergarden/src/main/resources/modelengine/realm'
JAVA = (ROOT / 'evergarden/src/main/java/com/example/voidscape/boss/RealmBosses.java').read_text(encoding='utf8')
BEHAVIOR_PREFIXES = ('h_', 'b_', 'ob_', 'seg_', 'i_', 'il_', 'ir_', 'l_', 'p_', 'tag_', 'g_', 'mount', 'seat')
CLIPS = re.findall(r'"([a-z_]+)"', re.search(r'CLIPS=List\.of\((.*?)\);', JAVA, re.S).group(1))
KINDS = re.findall(r'\("([a-z_]+)","([a-z]+)","[^"]+","[^"]+"\)', JAVA)
MODELS = [m for m, _ in KINDS]
CODES = dict(KINDS)
BLUEPRINT = re.search(r'BLUEPRINT="([a-z_]+)"', JAVA).group(1)
# Clips allowed below the floor: rising out of the ground, and the hound's burrow.
UNDERGROUND = {'spawn'} | {f'soul_hound/{c}' for c in ('skill_windup', 'skill_active')}
FLYING = {'crystal_moth', 'unlit_sun'}
DISPLAY_BUDGET = 330
REACH = 30.0
checks = 0


def check(ok, label):
    global checks
    assert ok, label
    checks += 1


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


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--pack', type=Path, default=ROOT / 'evergarden/dist/evergarden-bedrock.mcpack')
    parser.add_argument('--bedrock-end-textures', choices=('original', 'brightened'), default='original',
                        help='Require the original atlas or the deliberate Bedrock-only End brightness adjustment')
    args = parser.parse_args()
    check(MODELS == ['cinder_colossus', 'soul_hound', 'crystal_moth', 'unlit_sun'], f'RealmBosses lists the four rigs {MODELS}')
    check(len(CLIPS) == 14 and CLIPS[0] == 'idle' and CLIPS[-1] == 'spawn', 'shared clip table parsed')
    with zipfile.ZipFile(args.pack) as pack:
        bedrock = {n: pack.read(n) for n in pack.namelist() if any(n.split('/')[-1].startswith(m + '_native') for m in MODELS)}
    sources = {}
    for name in MODELS:
        model = json.loads((RESOURCES / f'{name}.bbmodel').read_text(encoding='utf8'))
        sources[name] = model
        manifest = json.loads((RESOURCES / f'{name}-manifest.json').read_text(encoding='utf8'))
        scale = manifest['scale']
        aura = set(manifest['aura_bones'])
        bones = groups(model)
        elements = {e['uuid']: e for e in model['elements']}
        check(model['meta']['evergarden_animation_position_x'] == 'inverted', f'{name}: R4 translation convention flag')
        check({'root', 'facing', 'hi_gaze', 'head_forward'} <= set(bones), f'{name}: contract bones')
        check(aura <= set(bones), f'{name}: aura bones present')
        check(not [b for b in bones if b not in ('hi_gaze', 'hitbox') and b.startswith(BEHAVIOR_PREFIXES)], f'{name}: no accidental ME bone behaviors')
        uuids = [e['uuid'] for e in model['elements']] + [g['uuid'] for g in bones.values()]
        check(len(uuids) == len(set(uuids)), f'{name}: unique element/bone ids')
        # --- clips and ModelEngine lanes -------------------------------------------------
        clips = {c['name']: c for c in model['animations']}
        check([c['name'] for c in model['animations']] == CLIPS + ['aura'], f'{name}: clip order matches RealmBosses.CLIPS')
        check(all(c['override'] for c in model['animations']), f'{name}: every clip overrides lower lanes')
        body = {n for n in bones if n not in aura and n not in ('hitbox', 'hi_gaze', 'root', 'facing', 'head_forward') and not n.startswith('fx_')}
        for clip in CLIPS:
            keyed = {a['name']: a for a in clips[clip]['animators'].values()}
            check(not (set(keyed) & aura) and 'hi_gaze' not in keyed, f'{name}/{clip}: aura and gaze stay unkeyed')
            check(body <= set(keyed), f'{name}/{clip}: keys every body bone {body - set(keyed)}')
            for bone in body:
                frames = [f for f in keyed[bone]['keyframes'] if f['channel'] == 'rotation']
                check(len(frames) >= 2 and frames[0]['time'] == 0 and abs(frames[-1]['time'] - clips[clip]['length']) < 1e-6,
                      f'{name}/{clip}/{bone}: full-length rotation track')
        keyed_aura = {a['name'] for a in clips['aura']['animators'].values()}
        check(keyed_aura == aura and clips['aura']['loop'] == 'loop', f'{name}: aura loop keys only aura bones')
        check(clips['idle']['loop'] == 'loop' and clips['walk']['loop'] == 'loop', f'{name}: idle and walk loop')
        # --- geometry, UVs, sheets, reach and budget ---------------------------------------
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
            for d in textured.values():
                u0, v0, u1, v1 = d['uv']
                check(0 <= min(u0, u1) and max(u0, u1) <= size and 0 <= min(v0, v1) and max(v0, v1) <= size, f'{name}: UV inside atlas')
            if len(textured) == 2:
                sheets += 1
                u0, v0, u1, v1 = textured['south']['uv']
                region = alpha[int(v0):int(v1), int(min(u0, u1)):int(max(u0, u1))]
                check(region.size and region.max() == 255, f'{name}: sheet {e["name"]} has visible texels')
        check(sheets >= 1, f'{name}: alpha-cut sheets present ({sheets})')
        worst = 0.0
        for g in bones.values():
            if g['name'] == 'hitbox':
                continue
            o = np.array(g['origin'], float)
            for c in g['children']:
                if isinstance(c, dict):
                    continue
                e = elements[c]
                lo, hi, r, p = np.array(e['from']), np.array(e['to']), rot(e['rotation']), np.array(e['origin'])
                for corner in np.array(np.meshgrid(*zip(lo, hi))).T.reshape(-1, 3):
                    worst = max(worst, float(np.abs((corner - p) @ r.T + p - o).max()))
        check(worst <= REACH, f'{name}: every cube within {REACH} units of its bone pivot ({worst:.1f})')
        count = displays(model)
        check(count <= DISPLAY_BUDGET and manifest['displays'] == count, f'{name}: {count} ModelEngine displays within budget')
        check(manifest['cubes'] == len(model['elements']) - 1 and manifest['revision'] in model['meta']['evergarden_revision'],
              f'{name}: manifest matches blueprint')
        # --- Bedrock parity ---------------------------------------------------------------
        get = lambda path: json.loads(bedrock[path])  # noqa: E731
        entity = get(f'entity/{name}_native.entity.json')['minecraft:client_entity']['description']
        geometry = get(f'models/entity/{name}_native.geo.json')['minecraft:geometry'][0]
        animations = get(f'animations/{name}_native.animation.json')['animations']
        controller = get(f'animation_controllers/{name}_native.controller.json')['animation_controllers'][f'controller.animation.voidscape.{name}']
        render = get(f'render_controllers/{name}_native.render.json')['render_controllers'][f'controller.render.voidscape.{name}']
        check(entity['identifier'] == f'voidscape:{name}' and entity['scripts']['animate'] == ['combat', 'look', 'aura'], f'{name}: entity layers')
        check(render.get('ignore_lighting') is True, f'{name}: full-bright like the Java rig')
        source_atlas = base64.b64decode(model['textures'][0]['source'].split(',', 1)[1])
        native_atlas = bedrock[f'textures/entity/{name}_native.png']
        if args.bedrock_end_textures == 'brightened' and name in GAMMA:
            expected = np.asarray(Image.open(io.BytesIO(brighten_png(source_atlas, GAMMA[name]))).convert('RGBA'))
            actual = np.asarray(Image.open(io.BytesIO(native_atlas)).convert('RGBA'))
            check(actual.shape == expected.shape and np.array_equal(actual, expected),
                  f'{name}: Bedrock atlas has the expected brightness adjustment')
            check(np.array_equal(actual[:, :, 3], np.asarray(texture)[:, :, 3]), f'{name}: Bedrock atlas retains alpha')
        else:
            check(native_atlas == source_atlas, f'{name}: same atlas on both clients')
        check(list(controller['states']) == CLIPS, f'{name}: controller states follow the property index')
        for clip in CLIPS:
            others = controller['states'][clip]['transitions']
            check(len(others) == len(CLIPS) - 1 and all(list(t.values())[0] == f"q.property('voidscape:clip') == {CLIPS.index(list(t)[0])}"
                                                      for t in others), f'{name}/{clip}: property transitions')
        geo_bones = {b['name']: b for b in geometry['bones']}
        for gname, g in bones.items():
            if gname == 'hitbox':
                continue
            b = geo_bones['gaze' if gname == 'hi_gaze' else gname]
            check(np.allclose(b['pivot'], [-g['origin'][0] * scale, g['origin'][1] * scale, g['origin'][2] * scale]), f'{name}/{gname}: pivot')
            owned = [elements[c] for c in g['children'] if isinstance(c, str)]
            check(len(owned) == len(b['cubes']), f'{name}/{gname}: cube ownership')
        for clip in model['animations']:
            exported = animations[f'animation.voidscape.{name}.{clip["name"]}']
            check(exported['animation_length'] == clip['length'], f'{name}/{clip["name"]}: Bedrock length')
            for animator in clip['animators'].values():
                if animator['name'] in ('gaze', 'hi_gaze'):
                    continue
                for frame in animator['keyframes']:
                    v = [float(frame['data_points'][0][k]) for k in 'xyz']
                    if frame['channel'] == 'position':
                        v = [v[0] * scale, v[1] * scale, v[2] * scale]
                    actual = exported['bones'][animator['name']][frame['channel']][str(frame['time'])]
                    check(np.allclose(actual.get('post', actual) if isinstance(actual, dict) else actual, v, atol=1e-4),
                          f'{name}/{clip["name"]}/{animator["name"]}: converted keyframe')
        # --- floor clearance ----------------------------------------------------------------
        rig = Rig(model)
        for clip in CLIPS:
            if clip in UNDERGROUND or f'{name}/{clip}' in UNDERGROUND:
                continue
            lowest = math.inf
            for t in np.linspace(0, clips[clip]['length'], 13):
                matrices = rig.matrices(clip, float(t), aura_time=0)
                check(all(np.isfinite(m).all() for m in matrices.values()), f'{name}/{clip}: finite transforms')
                for e, world, bone in rig.cuboids(matrices):
                    if bone not in aura:
                        lowest = min(lowest, float(world[:, 1].min()))
            check(lowest >= -.6, f'{name}/{clip}: geometry stays above the floor ({lowest:.2f})')
            if name in FLYING and clip in ('idle', 'walk'):
                check(lowest > 2.0, f'{name}/{clip}: hovers clear of the ground ({lowest:.2f})')
    merged_checks(sources)
    print(f'PASS {checks} realm-boss checks: contract, lanes, geometry, alpha sheets, reach, display budget, Bedrock parity, floor, merged blueprint')


def merged_checks(sources):
    """The single ModelEngine blueprint carrying all four rigs (one import slot)."""
    merged = json.loads((RESOURCES / f'{BLUEPRINT}.bbmodel').read_text(encoding='utf8'))
    bones = groups(merged)
    names = [n for n in bones if n != 'hitbox']
    check([t['name'] for t in merged['textures']] == [f'{m}.png' for m in MODELS], 'merged: one texture per rig in RealmBosses order')
    for index, name in enumerate(MODELS):
        check(merged['textures'][index]['source'] == sources[name]['textures'][0]['source'], f'merged/{name}: atlas identical to the rig')
    owner = {}
    for n in names:
        code = n[3:] if n.startswith('hi_') else n.split('_', 1)[0]
        check(code in CODES.values(), f'merged: bone {n} carries a rig code')
        owner[n] = code
    check(sorted(n for n in names if n.startswith('hi_')) == sorted('hi_' + c for c in CODES.values()), 'merged: one hi_ head tracker per rig')
    check(not [n for n in names if not n.startswith('hi_') and n.startswith(BEHAVIOR_PREFIXES)], 'merged: no accidental ME bone behaviors')
    uuids = [e['uuid'] for e in merged['elements']] + [g['uuid'] for g in bones.values()]
    check(len(uuids) == len(set(uuids)), 'merged: unique element/bone ids')
    parent = {}
    for g in bones.values():
        for c in g['children']:
            if isinstance(c, str):
                parent[c] = g['name']
    for e in merged['elements']:
        if e['name'] == 'hitbox':
            continue
        code = owner[parent[e['uuid']]]
        used = {d['texture'] for d in e['faces'].values() if d.get('texture') is not None}
        check(used <= {list(CODES.values()).index(code)}, f'merged: {e["name"]} samples only its rig texture')
    clips = {c['name']: c for c in merged['animations']}
    for name in MODELS:
        code = CODES[name]
        source = sources[name]
        check(sum(1 for g in groups(source) if g != 'hitbox') == sum(1 for n in names if owner[n] == code), f'merged/{name}: every bone carried over')
        check(sum(1 for e in source['elements'] if e['name'] != 'hitbox') == sum(1 for e in merged['elements'] if e['name'] != 'hitbox'
                                                                                    and owner[parent[e['uuid']]] == code), f'merged/{name}: every cube carried over')
        for clip in source['animations']:
            mine = clips.get(f'{name}_{clip["name"]}')
            check(mine is not None and mine['length'] == clip['length'] and mine['loop'] == clip['loop'], f'merged/{name}_{clip["name"]}: clip carried over')
            keyed = [a['name'] for a in mine['animators'].values()]
            check(all(owner.get(k) == code for k in keyed), f'merged/{name}_{clip["name"]}: keys only its own bones')
            check(sum(len(a['keyframes']) for a in mine['animators'].values()) == sum(len(a['keyframes']) for a in clip['animators'].values()),
                  f'merged/{name}_{clip["name"]}: every keyframe carried over')
    check(len(clips) == len(MODELS) * (len(CLIPS) + 1), 'merged: clip count')


if __name__ == '__main__':
    main()
