"""Bedrock holds every remodelled weapon where Java does, and bows/crossbows where vanilla Bedrock does.

Reads the shipped packs: Java item models (display + elements) and Bedrock
geometry/animations. For melee weapons third person is compared corner by corner
with Java's ItemInHandLayer and first person must be the java2bedrock mapping of
the Java first-person display. The bow and crossbow use Mojang's own Bedrock
bow/crossbow attachable unchanged (geometry, wield and pull animations) with a
sprite registered to Mojang's sprite, and Mojang's player controllers count them
as a bow / crossbow for the two-handed poses. Arithmetic checks only, not a
game-client screenshot.
"""
import argparse
import copy
import io
import itertools
import json
from pathlib import Path
import sys
import zipfile

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
from hold_math import bedrock_hand, exported_values, java2bedrock, java_hand  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
RIG = {'root': None, 'display_x': 'root', 'display_y': 'display_x', 'display_z': 'display_y'}
FAMILIES = (
    ('advance-magic', 'advance-magic-java.zip', 'advance-magic-bedrock.mcpack', 'advance_magic', 'legendary_weapons', 'legendary_'),
    ('evergarden', 'evergarden-java.zip', 'evergarden-bedrock.mcpack', 'voidscape', 'adventurer_weapons', 'adventurer_'),
)
checks = 0


def check(condition, label):
    global checks
    assert condition, label
    checks += 1


def corners(lo, hi):
    return [np.array(p, dtype=float) for p in itertools.product(*zip(lo, hi))]


def to_bedrock(p):
    return np.array([8 - p[0], p[1], p[2] - 8])


RANGED_STATES = ('', '_pulling_0', '_pulling_1', '_pulling_2', '_arrow', '_firework')
VANILLA = ROOT / 'evergarden/tools/bedrock_vanilla'
# Our state suffix -> Mojang's geometry suffix and attachable texture key.
MOJANG = {'bow': {'': ('standby', 'default'), '_pulling_0': ('pulling_0', 'bow_pulling_0'), '_pulling_1': ('pulling_1', 'bow_pulling_1'),
                  '_pulling_2': ('pulling_2', 'bow_pulling_2')},
          'crossbow': {'': ('standby', 'default'), '_pulling_0': ('pulling_0', 'crossbow_pulling_0'), '_pulling_1': ('pulling_1', 'crossbow_pulling_1'),
                       '_pulling_2': ('pulling_2', 'crossbow_pulling_2'), '_arrow': ('arrow', 'crossbow_arrow'), '_firework': ('rocket', 'crossbow_rocket')}}
SPRITES = {'standby': 'standby', 'pulling_0': 'pulling_0', 'pulling_1': 'pulling_1', 'pulling_2': 'pulling_2', 'arrow': 'arrow', 'rocket': 'firework'}


def vanilla(name):
    return json.loads((VANILLA / name).read_text(encoding='utf8'))


def silhouette(image, size=16):
    """Opaque cells of a sprite on Mojang's 16 px grid."""
    alpha = np.asarray(image.convert('RGBA'))[..., 3] > 0
    k = alpha.shape[0] // size
    return alpha.reshape(size, k, size, k).any(axis=(1, 3))


def extent(image):
    """Opaque bounding box in Mojang's 16 px units: left, top, right, bottom."""
    alpha = np.asarray(image.convert('RGBA'))[..., 3] > 0
    ys, xs = np.nonzero(alpha)
    k = alpha.shape[0] / 16
    return np.array([xs.min(), ys.min(), xs.max() + 1, ys.max() + 1]) / k


def grown(mask):
    out = mask.copy()
    for dy, dx in itertools.product((-1, 0, 1), repeat=2):
        out |= np.roll(np.roll(mask, dy, 0), dx, 1)
    return out


def ranged_weapon(java, bedrock, namespace, folder, prefix, name, animations):
    """Bow/crossbow: Java holds the 3D model with Mojang's display; Bedrock holds it through Mojang's own
    bow/crossbow attachable (texture mesh, wield, first-person pull) with a sprite of the model."""
    from PIL import Image
    states = [s for s in RANGED_STATES if f'assets/{namespace}/models/item/{folder}/{name}{s}.json' in java.namelist()]
    check(len(states) in (4, 6), f'{name}: bow has 4 and crossbow 6 draw states ({len(states)})')
    kind = 'bow' if len(states) == 4 else 'crossbow'
    for state in states:
        model = json.loads(java.read(f'assets/{namespace}/models/item/{folder}/{name}{state}.json'))
        check(not any(f.get('texture') == '#fx' for e in model['elements'] for f in e['faces'].values()), f'{name}{state}: no held-VFX planes')
    attach = json.loads(bedrock.read(f'attachables/{folder}/{name}.json'))['minecraft:attachable']['description']
    reference = vanilla(f'attachable_{kind}.json' if kind == 'bow' else 'attachable_crossbow.entity.json')['minecraft:attachable']['description']
    geos = {g['description']['identifier']: g for g in json.loads(bedrock.read(f'models/entity/{folder}/{name}.geo.json'))['minecraft:geometry']}
    mojang = {g['description']['identifier']: g for g in vanilla(f'{kind}.geo.json')['minecraft:geometry']}
    check(len(geos) == len(mojang) == len(states), f'{name}: one texture-mesh geometry per draw state, as Mojang has')
    for state in states:
        suffix, key = MOJANG[kind][state]
        geo_id = attach['geometry'][key]
        ours, theirs = copy.deepcopy(geos[geo_id]), copy.deepcopy(mojang[f'geometry.{kind}_{suffix}'])
        ours['description'].pop('identifier'), theirs['description'].pop('identifier')
        check(ours == theirs, f"{name}{state}: Bedrock geometry is Mojang's geometry.{kind}_{suffix} unchanged")
        mesh = geos[geo_id]['bones'][0]['texture_meshes'][0]
        check(mesh['texture'] == key and key in attach['textures'], f'{name}{state}: texture mesh draws the {key} sprite')
        sprite = Image.open(io.BytesIO(bedrock.read(attach['textures'][key] + '.png')))
        check(sprite.size == (64, 64), f'{name}{state}: 64 px square sprite spans the vanilla 16 units unstretched')
        ours_mask = silhouette(sprite)
        theirs_mask = silhouette(Image.open(VANILLA / f'textures/{kind}_{SPRITES[suffix]}.png'))
        inside = (ours_mask & grown(theirs_mask)).sum() / ours_mask.sum()
        covered = (theirs_mask & grown(ours_mask)).sum() / theirs_mask.sum()
        check(inside > .95 and covered > .94, f"{name}{state}: sprite lies where Mojang's {kind} sprite lies ({inside:.2f} inside, {covered:.2f} covered)")
        edges = np.abs(extent(sprite) - extent(Image.open(VANILLA / f'textures/{kind}_{SPRITES[suffix]}.png'))).max()
        check(edges <= 1.25, f"{name}{state}: sprite spans Mojang's {kind} sprite, same size and place (edges within {edges:.2f} px)")
    for key in ('wield', 'wield_first_person_pull'):
        check(animations[attach['animations'][key]] == vanilla(f'{kind}.animation.json')['animations'][reference['animations'][key]],
              f"{name}: {key} is Mojang's {reference['animations'][key]} unchanged")
    check(attach['scripts']['animate'] == reference['scripts']['animate'], f'{name}: plays wield and the first-person pull exactly as the vanilla {kind}')
    check(set(attach['materials']) == set(reference['materials']) and attach['materials'] == reference['materials'], f'{name}: vanilla materials incl. enchant glint')
    controllers = json.loads(bedrock.read('render_controllers/adventurer_weapons.render.json'))['render_controllers']
    held = controllers[attach['render_controllers'][0]]
    keys = [MOJANG[kind][s][1] for s in states[:4]]
    check(held['arrays']['geometries']['Array.held_geometry'] == [f'Geometry.{k}' for k in keys]
          and held['arrays']['textures']['Array.held_texture'] == [f'Texture.{k}' for k in keys], f'{name}: draw frames 0-3 pick standby and pulling 0-2')
    check(held['geometry'] == 'Array.held_geometry[v.draw_frame]' and held['textures'][0] == 'Array.held_texture[v.draw_frame]', f'{name}: geometry and sprite follow the draw frame')


RANGED_IDS = {'bow': ('voidscape:adventurer_thornwhisper',),
              'crossbow': ('voidscape:adventurer_runelock', 'voidscape:adventurer_runelock_loaded_arrow', 'voidscape:adventurer_runelock_loaded_rocket'),
              'loaded': ('voidscape:adventurer_runelock_loaded_arrow', 'voidscape:adventurer_runelock_loaded_rocket')}


def ranged_player_poses(bedrock):
    """The pack's player controllers are Mojang's, with only the bow/crossbow item tests widened."""
    names = lambda ids: "query.is_item_name_any('slot.weapon.mainhand', " + ', '.join(f"'{i}'" for i in ids) + ')'
    undo = [(f"!(query.get_equipped_item_name == 'crossbow' || {names(RANGED_IDS['crossbow'])})", "query.get_equipped_item_name != 'crossbow'"),
            (f"(query.get_equipped_item_name == 'crossbow' || {names(RANGED_IDS['crossbow'])})", "query.get_equipped_item_name == 'crossbow'"),
            (f"(query.get_equipped_item_name == 'bow' || {names(RANGED_IDS['bow'])})", "query.get_equipped_item_name == 'bow'"),
            (f"(query.item_is_charged || {names(RANGED_IDS['loaded'])})", 'query.item_is_charged'),
            (names(('minecraft:bow',) + RANGED_IDS['bow']), names(('minecraft:bow',)))]
    ours = json.loads(bedrock.read('animation_controllers/adventurer_ranged_player.controller.json'))
    theirs = vanilla('player.animation_controllers.json')
    widened = []

    def same(a, b, path):
        if isinstance(a, dict):
            check(isinstance(b, dict) and list(a) == list(b), f"player controllers: {path} has Mojang's keys")
            for k in a:
                same(a[k], b[k], f'{path}/{k}')
        elif isinstance(a, list):
            check(isinstance(b, list) and len(a) == len(b), f"player controllers: {path} has Mojang's entries")
            for i, (x, y) in enumerate(zip(a, b)):
                same(x, y, f'{path}[{i}]')
        elif a != b:
            restored = a
            for new, old in undo:
                restored = restored.replace(new, old)
            check(restored == b, f'player controllers: {path} only widens the bow/crossbow item test')
            widened.append(path.rsplit('/', 1)[-1])

    same(ours, theirs, '')
    check(sorted(widened) == sorted(['default', 'hold', 'hold', 'default', 'first_person_crossbow_equipped', 'use_item_progress',
                                     'crossbow_controller', 'third_person_bow_equipped']),
          f'player controllers: the two-handed bow draw, crossbow charge/aim and first-person charge include Thornwhisper/Runelock {sorted(widened)}')


def weapon(java, bedrock, namespace, folder, prefix, name, animations):
    model = json.loads(java.read(f'assets/{namespace}/models/item/{folder}/{name}.json'))
    display = model['display']
    if not any(f.get('texture') == '#fx' for e in model['elements'] for f in e['faces'].values()):
        return ranged_weapon(java, bedrock, namespace, folder, prefix, name, animations)
    elements = model['elements'][:-3]
    check(all(e['faces'][f]['texture'] == '#fx' for e in model['elements'][-3:] for f in e['faces']), name + ': last three elements are the VFX planes')
    geos = {}
    for suffix in ('', '_fx'):
        geo = json.loads(bedrock.read(f'models/entity/{folder}/{name}{suffix}.geo.json'))['minecraft:geometry'][0]
        bones = {b['name']: b for b in geo['bones']}
        for bone, parent in RIG.items():
            check(bones[bone].get('parent') == parent and bones[bone]['pivot'] == [0, 8, 0], f'{name}{suffix}: rig bone {bone}')
        check(bones['root']['binding'] == 'q.item_slot_to_bone_name(context.item_slot)', f'{name}{suffix}: bound to the item slot')
        check(all(b['parent'] == 'display_z' for n, b in bones.items() if n not in RIG), f'{name}{suffix}: body/FX layers share the display transform')
        geos[suffix] = geo
    grip = geos['']['bones'][3]['locators']['hand_contact'][1]
    check(any(all(e['from'][i] <= v <= e['to'][i] for i, v in enumerate((8, grip, 8))) for e in elements), name + ': hand contact lies inside a solid handle')

    cubes = [(b['name'], c) for b in geos['']['bones'] for c in b.get('cubes', [])]
    source = sorted(tuple(e['from'] + e['to']) for e in elements)
    exported = sorted(tuple([round(8 - c['origin'][0] - c['size'][0], 4) + 0.0, c['origin'][1], round(c['origin'][2] + 8, 4) + 0.0,
                             round(8 - c['origin'][0], 4) + 0.0, round(c['origin'][1] + c['size'][1], 4) + 0.0,
                             round(c['origin'][2] + 8 + c['size'][2], 4) + 0.0]) for _, c in cubes)
    check(exported == source, name + ': Bedrock body is the Java model, cube for cube')

    first = animations[f'animation.{namespace}.{prefix}{name}.first']['bones']
    third = animations[f'animation.{namespace}.{prefix}{name}.third']['bones']
    for clip, is_first in ((first, True), (third, False)):
        expected = java2bedrock(display, is_first)
        for off_hand in (False, True):
            for bone, (rotation, position, scale) in expected[off_hand].items():
                got = exported_values(clip, bone, off_hand)
                ok = np.allclose(got[0], rotation, atol=1e-4) and np.allclose(got[1], position, atol=1e-4) and np.allclose(got[2], scale, atol=1e-4)
                check(ok, f'{name}: {"first" if is_first else "third"}-person {bone} is the java2bedrock map of the Java display ({"off" if off_hand else "main"} hand)')

    right = display['thirdperson_righthand']
    worst = 0.0
    for bone, cube in cubes:
        lo = np.array(cube['origin'], dtype=float)
        for corner in corners(lo, lo + cube['size']):
            java_point = np.array([8 - corner[0], corner[1], corner[2] + 8])
            worst = max(worst, float(np.abs(bedrock_hand(geos['']['bones'], third, bone, corner) - java_hand(right, java_point)).max()))
    check(worst < 1e-3, f'{name}: every corner held where Java holds it in third person (max {worst:.2e} px)')

    g, tip = np.array([8, grip, 8.0]), np.array([8, max(e['to'][1] for e in elements), 8.0])
    main = [bedrock_hand(geos['']['bones'], third, 'display_z', to_bedrock(p)) for p in (g, tip)]
    off = [bedrock_hand(geos['']['bones'], third, 'display_z', to_bedrock(p), True) for p in (g, tip)]
    mirror = np.array([-1, 1, 1])
    check(np.allclose(off[0], main[0] * mirror, atol=1e-3), name + ': off-hand grip is the mirrored fist')
    check(np.allclose(off[1] - off[0], (main[1] - main[0]) * mirror, atol=1e-3), name + ': off-hand weapon axis is mirrored')
    check(np.allclose(java_hand(display['thirdperson_lefthand'], g, True), java_hand(right, g) * mirror, atol=1e-3), name + ': Java off-hand grip agrees')
    axis = (main[1] - main[0]) / np.linalg.norm(main[1] - main[0])
    check(axis[2] < -.95, f'{name}: points forward from the hand (axis {axis.round(3).tolist()})')
    flat = bedrock_hand(geos['']['bones'], third, 'display_z', to_bedrock(g + [0, 0, 1])) - main[0]
    check(abs(flat[0] / np.linalg.norm(flat)) > .999, f'{name}: blade faces sideways with edges up/down, no roll')
    fx_bones = {b['name']: b for b in geos['_fx']['bones']}
    check(all(fx_bones[b] == {k: v for k, v in geos['']['bones'][i].items()} for i, b in enumerate(RIG)), name + ': VFX geometry uses the identical rig')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evergarden-dist', type=Path, default=ROOT / 'evergarden/dist', help='Folder with candidate Evergarden packs and mappings')
    args = parser.parse_args()
    for module, java_name, bedrock_name, namespace, folder, prefix in FAMILIES:
        dist = args.evergarden_dist if module == 'evergarden' else ROOT / module / 'dist'
        mappings = json.loads((dist / 'geyser-mappings.json').read_text(encoding='utf8'))
        names = sorted({row['bedrock_identifier'].split(':')[1][len(prefix):] for rows in mappings['items'].values() for row in rows
                        if row.get('bedrock_identifier', '').startswith(f'{namespace}:{prefix}') and '_loaded_' not in row['bedrock_identifier']})
        check(len(names) == (5 if module == 'advance-magic' else 20), f'{module}: expected weapon count {len(names)}')
        with zipfile.ZipFile(dist / java_name) as java, zipfile.ZipFile(dist / bedrock_name) as bedrock:
            animations = json.loads(bedrock.read(f'animations/{folder}.animation.json'))['animations']
            for name in names:
                weapon(java, bedrock, namespace, folder, prefix, name, animations)
            if module == 'evergarden':
                ranged_player_poses(bedrock)
        print(f'{module}: {len(names)} weapons checked')
    print(f'PASS {checks} hold checks: melee Bedrock third person equals Java for every cube corner and first person is the java2bedrock map;'
          " bow/crossbow use Mojang's Bedrock attachable, sprites and player poses (no client test)")


if __name__ == '__main__':
    main()
