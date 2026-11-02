"""Build the realm-boss rigs, their manifests and preview sheets.

    python evergarden/tools/realm_bosses.py [model ...] [--preview] [--clips] [--no-write]

Writes evergarden/src/main/resources/modelengine/realm/<model>.bbmodel and -manifest.json,
the atlas to evergarden/art/realm-bosses/ and previews to previews/realm-bosses/.
Previews use Minecraft entity lighting (what Java players see through ModelEngine).
"""
import argparse
import importlib
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from boss_forge import dump  # noqa: E402
from boss_render import Rig  # noqa: E402
from realm_forge import contact_sheet, displays, render  # noqa: E402

ROOT = HERE.parents[1]
OUT = ROOT / 'evergarden/src/main/resources/modelengine/realm'
ART = ROOT / 'evergarden/art/realm-bosses'
PREVIEWS = ROOT / 'previews/realm-bosses'
MODULES = {'cinder_colossus': 'cinder_colossus', 'soul_hound': 'soul_hound', 'crystal_moth': 'crystal_moth', 'unlit_sun': 'unlit_sun'}


def build(name, write=True):
    module = importlib.import_module(MODULES[name])
    model, atlas, stats, worst, forge = module.build()
    manifest = dict(revision=module.REVISION, title=module.TITLE, cubes=len(model['elements']) - 1, bones=len(forge.bones),
                    displays=displays(model), scale=module.SCALE,
                    texture=dict(size=model['resolution']['width'], density=round(stats['density'], 2), tiles=stats['tiles'],
                                 coverage=round(stats['coverage'], 3)),
                    aura_bones=list(module.AURA), animations={a['name']: a['length'] for a in model['animations']},
                    worst_ik_reach_error=round(worst, 3))
    if write:
        OUT.mkdir(parents=True, exist_ok=True)
        ART.mkdir(parents=True, exist_ok=True)
        (OUT / f'{name}.bbmodel').write_text(dump(model) + '\n', encoding='utf8')
        (OUT / f'{name}-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf8')
        atlas.save(ART / f'{name}.png')
    print(f"{name}: {manifest['cubes']} cubes, {manifest['bones']} bones, ~{manifest['displays']} displays, "
          f"{len(model['animations'])} clips, atlas {stats['coverage']:.0%} at {stats['density']:.1f}px/unit, IK error {worst:.2f}")
    return model, module


def previews(name, model, module, clips=False):
    PREVIEWS.mkdir(parents=True, exist_ok=True)
    rig = Rig(model)
    view = getattr(module, 'PREVIEW', {})
    span, center = view.get('span'), view.get('center')
    at = view.get('time', .4)
    shots = [(25, 8, 'idle', at), (0, 4, 'idle', at), (90, 6, 'idle', at), (155, 12, 'idle', at), (-35, -12, 'idle', at),
             (35, 30, 'idle', at)]
    tiles = [render(rig, clip, t, yaw, el, 640, span, center, aura_time=6.0)[0] for yaw, el, clip, t in shots]
    contact_sheet(tiles, 3, 640, ['3/4 front', 'front', 'side', 'back', 'low', 'high']).save(PREVIEWS / f'{name}-views.png')
    hero, _ = render(rig, 'idle', at, 28, 10, 1100, span, center, aura_time=6.0)
    hero.save(PREVIEWS / f'{name}-hero.png')
    close = view.get('close')
    if close:
        tiles = [render(rig, 'idle', at, yaw, el, 640, close[0], close[1], aura_time=6.0)[0] for yaw, el in ((0, 4), (35, 8), (120, 10))]
        contact_sheet(tiles, 3, 640, ['detail front', 'detail 3/4', 'detail rear']).save(PREVIEWS / f'{name}-detail.png')
    if clips:
        lengths = {a['name']: a['length'] for a in model['animations']}
        picks = getattr(module, 'CLIP_SHOTS', [('idle', .5), ('walk', .25), ('attack_windup', 1.0), ('attack_active', 1.0),
                                                ('skill_windup', 1.0), ('skill_active', 1.0), ('ultimate_windup', 1.0),
                                                ('ultimate_active', .3), ('stun', .5), ('transition', .5), ('spawn', .3), ('spawn', .6)])
        tiles, labels = [], []
        for clip, u in picks:
            t = lengths[clip] * u
            tiles.append(render(rig, clip, t, 30, 8, 480, view.get('clip_span', span), view.get('clip_center', center), aura_time=6.0)[0])
            labels.append(f'{clip} {t:.2f}s')
        contact_sheet(tiles, 4, 480, labels).save(PREVIEWS / f'{name}-clips.png')


MERGED = 'realm_bosses'
CODES = {'cinder_colossus': 'cc', 'soul_hound': 'sh', 'crystal_moth': 'cm', 'unlit_sun': 'us'}


def merged_bone(code, name):
    """Bone name inside the merged blueprint; the head tracker keeps ModelEngine's hi_ behaviour."""
    return f'hi_{code}' if name == 'hi_gaze' else f'{code}_{name}'


def merge(write=True):
    """All four rigs in one ModelEngine blueprint, so they use a single import slot.

    ModelEngine builds with a model cap import twelve models in all and the main bosses with
    internal_fire already take eleven. Every rig keeps its own texture, its bones are prefixed
    with its code and its clips with its model id; the showcase hides the other three rigs'
    bones on each spawned instance. The per-rig blueprints stay the source for the server-side
    bone math and the Bedrock entities.
    """
    import uuid as uuidlib
    elements, outliner, textures, animations = [], [], [], []
    hitbox = [[0, 0, 0], [0, 0, 0]]
    for index, name in enumerate(MODULES):
        code = CODES[name]
        model = json.loads((OUT / f'{name}.bbmodel').read_text(encoding='utf8'))

        def uid(old, _name=name):
            return str(uuidlib.uuid5(uuidlib.NAMESPACE_URL, f'evergarden/{MERGED}/{_name}/{old}'))
        for e in model['elements']:
            if e['name'] == 'hitbox':
                hitbox = [[min(a, b) for a, b in zip(hitbox[0], e['from'])], [max(a, b) for a, b in zip(hitbox[1], e['to'])]]
                continue
            e = dict(e, uuid=uid(e['uuid']), name=f"{code}_{e['name']}",
                     faces={f: (dict(d, texture=index) if d.get('texture') is not None else d) for f, d in e['faces'].items()})
            elements.append(e)

        def group(g):
            return dict(g, name=merged_bone(code, g['name']), uuid=uid(g['uuid']),
                        children=[group(c) if isinstance(c, dict) else uid(c) for c in g['children']])
        outliner += [group(g) for g in model['outliner'] if g['name'] != 'hitbox']
        texture = dict(model['textures'][0], name=f'{name}.png', id=str(index), uuid=uid('texture'))
        textures.append(texture)
        for clip in model['animations']:
            animators = {uid(k): dict(a, name=merged_bone(code, a['name'])) for k, a in clip['animators'].items()}
            for a in animators.values():
                a['keyframes'] = [dict(f, uuid=uid(f['uuid'])) for f in a['keyframes']]
            animations.append(dict(clip, name=f"{name}_{clip['name']}", uuid=uid(clip['uuid']), animators=animators))
    hit_uuid = str(uuidlib.uuid5(uuidlib.NAMESPACE_URL, f'evergarden/{MERGED}/hitbox/cube'))
    elements.append(dict(name='hitbox', uuid=hit_uuid, type='cube', **{'from': hitbox[0]}, to=hitbox[1], origin=[0, 0, 0], faces={}))
    outliner.append(dict(name='hitbox', uuid=str(uuidlib.uuid5(uuidlib.NAMESPACE_URL, f'evergarden/{MERGED}/hitbox')), origin=[0, 30, 0],
                         children=[hit_uuid], export=True))
    data = dict(meta=dict(format_version='4.10', model_format='free', box_uv=False, evergarden_animation_position_x='inverted',
                          evergarden_revision='realm-bosses-merged-v1'),
                name=MERGED, model_identifier=MERGED, resolution=dict(width=1024, height=1024), elements=elements,
                outliner=outliner, textures=textures, animations=animations)
    manifest = dict(revision='realm-bosses-merged-v1', models=list(MODULES), codes=CODES, cubes=len(elements) - 1,
                    displays=displays(data), clips=len(animations))
    if write:
        (OUT / f'{MERGED}.bbmodel').write_text(dump(data) + '\n', encoding='utf8')
        (OUT / f'{MERGED}-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf8')
    print(f"{MERGED}: {manifest['cubes']} cubes, ~{manifest['displays']} displays, {len(textures)} textures, {len(animations)} clips")
    return data


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('models', nargs='*', default=list(MODULES))
    parser.add_argument('--preview', action='store_true')
    parser.add_argument('--clips', action='store_true')
    parser.add_argument('--no-write', action='store_true')
    parser.add_argument('--merge-only', action='store_true', help='Only rebuild realm_bosses.bbmodel from the written rigs')
    args = parser.parse_args()
    if not args.merge_only:
        for name in args.models:
            model, module = build(name, not args.no_write)
            if args.preview or args.clips:
                previews(name, model, module, args.clips)
    if not args.no_write:
        merge()


if __name__ == '__main__':
    main()
