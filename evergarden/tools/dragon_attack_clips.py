"""Author the Dragon Nest attack clips (bite, claw, breath, cast) for the three forge dragon rigs.

The rigs (modelengine/dragons/forge_dragon_<kind>.bbmodel) ship eight clips: idle, walk, fly, tail_whip,
wing_flap, roar, takeoff, land. The hunt rework needs four more, with contact frames that the server's
DragonNestBosses timings follow:

    bite    1.40s  jaws snap at 0.70s (tick 14), head shakes the prey until 1.0s
    claw    1.60s  right rake lands at 0.62s (tick 12), left rake at 0.92s (tick 18)
    breath  3.00s  inhale to 0.70s, the stream sweeps left to right from 0.95s to 2.50s (ticks 19-50)
    cast    2.60s  rises with wings spread, slams the spell down at 1.75s (tick 35)

Every clip is written in the rig's own conventions, read from the existing clips: rotation data +X dips a
forward-pointing bone (the jaw opens, a hanging leg swings back), +Y turns toward the dragon's right (+X),
+Z raises the right wing; the left side mirrors as (x, -y, -z). Values are offsets on top of the idle clip's
first frame so each rest pose (the jaw rests at 16 degrees) is preserved.

The same clips are appended to the merged ModelEngine blueprint (modelengine/realm/realm_bosses.bbmodel),
named forge_dragon_<kind>_<clip> and keyed to that rig's prefixed bones (df_/ds_/di_). Re-running replaces
clips of the same name, so the script is idempotent.

    python evergarden/tools/dragon_attack_clips.py --preview DIR   # contact sheets only
    python evergarden/tools/dragon_attack_clips.py --write         # rigs + merged blueprint
"""
from __future__ import annotations

import argparse
import json
import math
import sys
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DRAGONS = ROOT / 'evergarden/src/main/resources/modelengine/dragons'
MERGED = ROOT / 'evergarden/src/main/resources/modelengine/realm/realm_bosses.bbmodel'
KINDS = {'forge_dragon_fire': 'df', 'forge_dragon_storm': 'ds', 'forge_dragon_ice': 'di'}
NAMESPACE = uuid.UUID('6c1b9d4e-2f0a-5d8e-9a43-d7a90c1e5b21')
STEP = 0.05


def smooth(u: float) -> float:
    u = min(1.0, max(0.0, u))
    return u * u * (3 - 2 * u)


def curve(t: float, points: list[tuple[float, tuple[float, float, float]]], ease=smooth):
    """Eased piecewise path through (time, vector) control points; held flat outside them."""
    if t <= points[0][0]:
        return points[0][1]
    for (t0, a), (t1, b) in zip(points, points[1:]):
        if t0 <= t <= t1:
            u = ease((t - t0) / max(1e-9, t1 - t0))
            return tuple(a[i] + (b[i] - a[i]) * u for i in range(3))
    return points[-1][1]


def x(v: float):
    return (v, 0.0, 0.0)


def mirror(v):
    return (v[0], -v[1], -v[2])


ZERO = (0.0, 0.0, 0.0)


class Clip:
    """A clip as per-bone offset paths; left-side bones can be derived from the right with mirror=True."""

    def __init__(self, name: str, length: float):
        self.name, self.length = name, length
        self.rotation: dict[str, list] = {}
        self.position: dict[str, list] = {}
        self.jitter: dict[str, callable] = {}

    def rot(self, bone: str, *points, both=False):
        self.rotation[bone] = list(points)
        if both and bone.endswith('_r'):
            self.rotation[bone[:-2] + '_l'] = [(t, mirror(v)) for t, v in points]
        return self

    def pos(self, bone: str, *points):
        self.position[bone] = list(points)
        return self

    def shake(self, bone: str, fn):
        self.jitter[bone] = fn
        return self

    def sample(self, bone: str, channel: str, t: float):
        paths = self.rotation if channel == 'rotation' else self.position
        value = curve(t, paths[bone]) if bone in paths else ZERO
        if channel == 'rotation' and bone in self.jitter:
            value = tuple(value[i] + self.jitter[bone](t)[i] for i in range(3))
        return value


def wave(start: float, end: float, amp, cycles: float):
    def fn(t):
        if t <= start or t >= end:
            return ZERO
        u = (t - start) / (end - start)
        envelope = math.sin(math.pi * u)
        s = math.sin(2 * math.pi * cycles * u) * envelope
        return (amp[0] * s, amp[1] * s, amp[2] * s)
    return fn


def bite() -> Clip:
    c = Clip('bite', 1.4)
    k = (0.0, 0.42, 0.62, 0.70, 1.0, 1.4)
    c.pos('body', (0, ZERO), (0.42, (0, 1.5, 5)), (0.62, (0, -1.5, -9)), (0.70, (0, -2, -10)), (1.0, (0, -1, -6)), (1.4, ZERO))
    c.rot('body', (0, ZERO), (0.42, x(-7)), (0.62, x(7)), (0.70, x(8)), (1.0, x(5)), (1.4, ZERO))
    for bone, back, reach in (('neck1', -12, 34), ('neck2', -6, 12), ('neck3', -6, 12), ('neck4', -4, 8), ('neck5', -6, 6)):
        c.rot(bone, (0, ZERO), (0.42, x(back)), (0.62, x(reach)), (0.70, x(reach + 2)), (1.0, x(reach * .7)), (1.4, ZERO))
    c.rot('head', (0, ZERO), (0.42, x(-14)), (0.62, x(20)), (0.70, x(22)), (1.0, x(12)), (1.4, ZERO))
    c.shake('head', wave(0.72, 1.02, (0, 14, 6), 1.5))
    c.shake('neck5', wave(0.72, 1.02, (0, 7, 0), 1.5))
    c.rot('jaw', (0, ZERO), (0.42, x(30)), (0.60, x(36)), (0.70, x(-14)), (1.0, x(-12)), (1.25, x(4)), (1.4, ZERO))
    c.rot('wing_r', (0, ZERO), (0.42, (0, 0, 22)), (0.70, (0, 0, 10)), (1.0, (0, 0, 14)), (1.4, ZERO), both=True)
    c.rot('wingelbow_r', (0, ZERO), (0.42, (0, 0, 8)), (0.70, (0, 0, 2)), (1.4, ZERO), both=True)
    c.rot('fore_r', (0, ZERO), (0.42, x(6)), (0.65, x(-14)), (1.0, x(-10)), (1.4, ZERO), both=True)
    c.rot('foreleg_r', (0, ZERO), (0.42, x(-4)), (0.65, x(10)), (1.0, x(8)), (1.4, ZERO), both=True)
    c.rot('hind_r', (0, ZERO), (0.42, x(7)), (0.65, x(-8)), (1.0, x(-5)), (1.4, ZERO), both=True)
    c.rot('tail1', (0, ZERO), (0.42, x(9)), (0.70, x(-3)), (1.0, x(-2)), (1.4, ZERO))
    c.rot('tail2', (0, ZERO), (0.42, (-4, 6, 0)), (0.80, (3, -8, 0)), (1.4, ZERO))
    c.rot('tail3', (0, ZERO), (0.50, (-3, 8, 0)), (0.90, (2, -10, 0)), (1.4, ZERO))
    return c


def claw() -> Clip:
    c = Clip('claw', 1.6)
    c.pos('body', (0, ZERO), (0.45, (0, 7, 2)), (0.62, (0, 3, -5)), (0.72, (0, 6, -1)), (0.92, (0, 1, -6)), (1.15, (0, 0, -3)), (1.6, ZERO))
    c.rot('body', (0, ZERO), (0.45, (-16, -10, 0)), (0.62, (-2, 12, 0)), (0.72, (-12, 10, 0)), (0.92, (0, -12, 0)), (1.15, (0, -4, 0)), (1.6, ZERO))
    # Hind legs counter the body pitch so the feet stay planted.
    c.rot('hind_r', (0, ZERO), (0.45, x(16)), (0.62, x(2)), (0.72, x(12)), (0.92, x(0)), (1.6, ZERO), both=True)
    c.rot('fore_r', (0, ZERO), (0.45, (-80, -15, 10)), (0.62, (-26, 28, 0)), (0.72, (-10, 10, 0)), (0.92, (-4, 0, 0)), (1.6, ZERO))
    c.rot('foreleg_r', (0, ZERO), (0.45, x(50)), (0.62, x(-12)), (0.80, x(4)), (1.6, ZERO))
    c.rot('forefoot_r', (0, ZERO), (0.45, x(-30)), (0.62, x(22)), (0.80, x(6)), (1.6, ZERO))
    c.rot('fore_l', (0, ZERO), (0.45, (-10, 0, 0)), (0.62, (-30, 6, -4)), (0.72, (-80, 15, -10)), (0.92, (-26, -28, 0)), (1.15, (-8, -8, 0)), (1.6, ZERO))
    c.rot('foreleg_l', (0, ZERO), (0.62, x(20)), (0.72, x(50)), (0.92, x(-12)), (1.15, x(4)), (1.6, ZERO))
    c.rot('forefoot_l', (0, ZERO), (0.62, x(-10)), (0.72, x(-30)), (0.92, x(22)), (1.15, x(6)), (1.6, ZERO))
    for i in range(4):
        c.rot(f'foretalon{i}_r', (0, ZERO), (0.45, x(-24)), (0.62, x(18)), (0.9, ZERO))
        c.rot(f'foretalon{i}_l', (0, ZERO), (0.72, x(-24)), (0.92, x(18)), (1.2, ZERO))
    c.rot('neck1', (0, ZERO), (0.45, (-8, -8, 0)), (0.62, (10, 10, 0)), (0.72, (-6, 8, 0)), (0.92, (10, -10, 0)), (1.15, (4, -3, 0)), (1.6, ZERO))
    c.rot('neck3', (0, ZERO), (0.45, (-4, -5, 0)), (0.62, (6, 6, 0)), (0.72, (-3, 5, 0)), (0.92, (6, -6, 0)), (1.6, ZERO))
    c.rot('head', (0, ZERO), (0.45, x(-6)), (0.62, x(10)), (0.72, x(-4)), (0.92, x(10)), (1.6, ZERO))
    c.rot('jaw', (0, ZERO), (0.40, x(24)), (0.62, x(14)), (0.72, x(24)), (0.92, x(16)), (1.3, x(4)), (1.6, ZERO))
    c.rot('wing_r', (0, ZERO), (0.45, (0, 0, 25)), (0.62, (0, 0, 15)), (0.72, (0, 0, 25)), (0.92, (0, 0, 10)), (1.6, ZERO), both=True)
    c.rot('wingelbow_r', (0, ZERO), (0.45, (0, 0, 10)), (0.92, (0, 0, 4)), (1.6, ZERO), both=True)
    c.rot('tail1', (0, ZERO), (0.45, (15, 6, 0)), (0.62, (3, -8, 0)), (0.72, (11, -6, 0)), (0.92, (0, 8, 0)), (1.6, ZERO))
    c.rot('tail3', (0, ZERO), (0.50, (0, 8, 0)), (0.70, (0, -10, 0)), (1.0, (0, 10, 0)), (1.6, ZERO))
    return c


def breath() -> Clip:
    c = Clip('breath', 3.0)
    sweep = lambda amp: ((0.95, (0, -amp, 0)), (2.5, (0, amp, 0)))
    c.pos('body', (0, ZERO), (0.7, (0, 1.5, 3)), (0.95, (0, -1, -4)), (2.5, (0, -1, -4)), (3.0, ZERO))
    c.rot('body', (0, ZERO), (0.7, x(-6)), (0.95, x(4)), (2.5, (4, 6, 0)), (3.0, ZERO))
    c.rot('neck1', (0, ZERO), (0.7, x(-14)), (0.95, (16, -26, 0)), (2.5, (16, 26, 0)), (2.8, (4, 6, 0)), (3.0, ZERO))
    c.rot('neck2', (0, ZERO), (0.7, x(-6)), (0.95, (6, -8, 0)), (2.5, (6, 8, 0)), (3.0, ZERO))
    c.rot('neck3', (0, ZERO), (0.7, x(-8)), (0.95, (8, -10, 0)), (2.5, (8, 10, 0)), (3.0, ZERO))
    c.rot('neck5', (0, ZERO), (0.7, x(-6)), (0.95, x(4)), (2.5, x(4)), (3.0, ZERO))
    c.rot('head', (0, ZERO), (0.7, x(-18)), (0.95, (10, -8, 0)), (2.5, (10, 8, 0)), (3.0, ZERO))
    c.rot('jaw', (0, ZERO), (0.7, x(8)), (0.95, x(36)), (2.55, x(36)), (2.85, x(2)), (3.0, ZERO))
    c.shake('jaw', wave(0.95, 2.55, (3, 0, 0), 6))
    c.shake('head', wave(0.95, 2.55, (1.5, 0, 1.2), 9))
    c.rot('wing_r', (0, ZERO), (0.7, (0, 0, 32)), (0.95, (0, 0, 18)), (2.5, (0, 0, 20)), (3.0, ZERO), both=True)
    c.rot('wingelbow_r', (0, ZERO), (0.7, (0, 0, 10)), (0.95, (0, 0, 5)), (3.0, ZERO), both=True)
    c.rot('fore_r', (0, ZERO), (0.95, x(-8)), (2.5, x(-8)), (3.0, ZERO), both=True)
    c.rot('hind_r', (0, ZERO), (0.7, x(6)), (0.95, x(-4)), (2.5, x(-4)), (3.0, ZERO), both=True)
    c.rot('tail1', (0, ZERO), (0.7, x(6)), (0.95, (-2, 6, 0)), (2.5, (-2, -6, 0)), (3.0, ZERO))
    c.rot('tail2', (0, ZERO), (0.95, (0, 6, 0)), (2.5, (0, -8, 0)), (3.0, ZERO))
    return c


def cast() -> Clip:
    c = Clip('cast', 2.6)
    c.pos('body', (0, ZERO), (0.8, (0, 9, 2)), (1.2, (0, 10, 2)), (1.55, (0, 11, 2)), (1.75, (0, -1, -3)), (2.1, (0, 0, -1)), (2.6, ZERO))
    c.rot('body', (0, ZERO), (0.8, x(-18)), (1.2, x(-20)), (1.55, x(-22)), (1.75, x(8)), (2.1, x(4)), (2.6, ZERO))
    c.shake('body', wave(0.8, 1.55, (0, 0, 1.6), 5))
    c.rot('hind_r', (0, ZERO), (0.8, x(18)), (1.55, x(22)), (1.75, x(-6)), (2.1, x(-3)), (2.6, ZERO), both=True)
    c.rot('fore_r', (0, ZERO), (0.8, (-40, 0, 8)), (1.55, (-50, 0, 10)), (1.75, x(12)), (2.1, x(4)), (2.6, ZERO), both=True)
    c.rot('foreleg_r', (0, ZERO), (0.8, x(30)), (1.55, x(36)), (1.75, x(-8)), (2.6, ZERO), both=True)
    c.rot('wing_r', (0, ZERO), (0.8, (-10, 0, 50)), (1.2, (-10, 0, 55)), (1.55, (-12, 0, 60)), (1.75, (0, 0, -15)), (2.1, (0, 0, 6)), (2.6, ZERO), both=True)
    c.rot('wingelbow_r', (0, ZERO), (0.8, (0, 0, 15)), (1.55, (0, 0, 18)), (1.75, (0, 0, -10)), (2.6, ZERO), both=True)
    c.rot('wingtip_r', (0, ZERO), (0.8, (0, 0, 10)), (1.55, (0, 0, 12)), (1.75, (0, 0, -6)), (2.6, ZERO), both=True)
    c.rot('neck1', (0, ZERO), (0.8, x(-22)), (1.55, x(-26)), (1.75, x(18)), (2.1, x(6)), (2.6, ZERO))
    c.rot('neck3', (0, ZERO), (0.8, x(-10)), (1.55, x(-12)), (1.75, x(8)), (2.6, ZERO))
    c.rot('head', (0, ZERO), (0.8, x(-30)), (1.55, x(-34)), (1.75, x(14)), (2.1, x(6)), (2.6, ZERO))
    c.rot('jaw', (0, ZERO), (0.8, x(32)), (1.55, x(38)), (1.75, x(42)), (2.1, x(14)), (2.6, ZERO))
    c.shake('jaw', wave(0.8, 1.55, (3, 0, 0), 4))
    c.rot('tail1', (0, ZERO), (0.8, x(18)), (1.55, x(22)), (1.75, x(-4)), (2.1, x(-2)), (2.6, ZERO))
    c.rot('tail2', (0, ZERO), (0.8, x(6)), (1.55, x(8)), (1.75, x(2)), (2.6, ZERO))
    return c


CLIPS = (bite, claw, breath, cast)


def idle_base(model) -> dict[str, dict[str, tuple]]:
    idle = next(a for a in model['animations'] if a['name'] == 'idle')
    base = {}
    for animator in idle['animators'].values():
        entry = {}
        for channel in ('rotation', 'position'):
            frames = sorted((f for f in animator['keyframes'] if f['channel'] == channel), key=lambda f: f['time'])
            if frames:
                p = frames[0]['data_points'][0]
                entry[channel] = tuple(float(p[a]) for a in 'xyz')
        base[animator['name']] = entry
    return base


def fmt(v: float) -> str:
    s = f'{v:.4f}'
    return '0.0000' if s == '-0.0000' else s


def build(model_id: str, model, clip: Clip, bone_uuid: dict[str, str], prefix: str = ''):
    base = idle_base(model)
    animators = {}
    times = [round(i * STEP, 4) for i in range(int(round(clip.length / STEP)) + 1)]
    for bone, rest in base.items():
        if bone not in bone_uuid:
            raise KeyError(f'{model_id}: bone {prefix}{bone} missing from target blueprint')
        frames = []
        for channel in ('rotation', 'position'):
            keyed = (clip.rotation if channel == 'rotation' else clip.position).get(bone) or (channel == 'rotation' and bone in clip.jitter)
            if channel not in rest and not keyed:
                continue
            origin = rest.get(channel, ZERO)
            stamps = times if keyed else [0.0, clip.length]
            for t in stamps:
                offset = clip.sample(bone, channel, t)
                value = tuple(origin[i] + offset[i] for i in range(3))
                frames.append({'channel': channel,
                               'data_points': [{'x': fmt(value[0]), 'y': fmt(value[1]), 'z': fmt(value[2])}],
                               'uuid': str(uuid.uuid5(NAMESPACE, f'{model_id}/{prefix}{clip.name}/{bone}/{channel}/{t}')),
                               'time': t, 'color': -1, 'interpolation': 'linear'})
        animators[bone_uuid[bone]] = {'name': prefix + bone if prefix else bone, 'type': 'bone', 'keyframes': frames}
    return {'uuid': str(uuid.uuid5(NAMESPACE, f'{model_id}/{prefix}{clip.name}')),
            'name': (model_id + '_' + clip.name) if prefix else clip.name,
            'loop': 'hold', 'override': True, 'length': clip.length, 'snapping': 20,
            'anim_time_update': '', 'blend_weight': '', 'start_delay': '', 'loop_delay': '', 'animators': animators}


def bones(model) -> dict[str, str]:
    out = {}

    def walk(nodes):
        for node in nodes:
            if isinstance(node, dict):
                out[node['name']] = node['uuid']
                walk(node['children'])
    walk(model['outliner'])
    return out


def replace(model, clips):
    names = {c['name'] for c in clips}
    model['animations'] = [a for a in model['animations'] if a['name'] not in names] + clips


def dump(path: Path, model):
    path.write_text(json.dumps(model, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf-8', newline='\n')


def load(path: Path):
    return json.loads(path.read_text(encoding='utf-8'))


def preview(directory: Path, kinds):
    sys.path.insert(0, str(ROOT / 'evergarden/tools'))
    from boss_render import Rig, render
    from PIL import Image, ImageDraw
    directory.mkdir(parents=True, exist_ok=True)
    plan = {'bite': (0.0, 0.42, 0.62, 0.70, 0.85, 1.4), 'claw': (0.0, 0.45, 0.62, 0.72, 0.92, 1.6),
            'breath': (0.0, 0.7, 0.95, 1.7, 2.5, 3.0), 'cast': (0.0, 0.8, 1.55, 1.75, 2.1, 2.6)}
    for model_id in kinds:
        model = load(DRAGONS / f'{model_id}.bbmodel')
        replace(model, [build(model_id, model, make(), bones(model)) for make in CLIPS])
        rig = Rig(model)
        for clip, stamps in plan.items():
            for yaw, tag in ((90, 'side'), (35, 'front')):
                _, info = render(rig, 'idle', 0, yaw=yaw, elevation=8, size=64, supersample=1, bloom=False, ground=False)
                frames = []
                for t in stamps:
                    img, _ = render(rig, clip, t, yaw=yaw, elevation=8, size=360, supersample=1, bloom=False,
                                    span=info['span'] * 1.3, center=info['center'])
                    ImageDraw.Draw(img).text((8, 8), f'{clip} {t:.2f}s', fill=(240, 220, 200))
                    frames.append(img)
                sheet = Image.new('RGB', (360 * len(frames), 360))
                for i, frame in enumerate(frames):
                    sheet.paste(frame, (360 * i, 0))
                out = directory / f'{model_id}-{clip}-{tag}.png'
                sheet.save(out)
                print(out)


def write(kinds):
    merged = load(MERGED)
    merged_bones = bones(merged)
    added = []
    for model_id in kinds:
        path = DRAGONS / f'{model_id}.bbmodel'
        model = load(path)
        rig_clips = [build(model_id, model, make(), bones(model)) for make in CLIPS]
        replace(model, rig_clips)
        dump(path, model)
        code = KINDS[model_id]
        prefixed = {name[len(code) + 1:]: value for name, value in merged_bones.items() if name.startswith(code + '_')}
        merged_clips = [build(model_id, model, make(), prefixed, prefix=code + '_') for make in CLIPS]
        replace(merged, merged_clips)
        added += [c['name'] for c in merged_clips]
        print(model_id, 'clips', [a['name'] for a in model['animations']])
    dump(MERGED, merged)
    print('merged blueprint +', added)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--preview', type=Path)
    parser.add_argument('--write', action='store_true')
    parser.add_argument('--kind', choices=sorted(KINDS), action='append')
    args = parser.parse_args()
    kinds = args.kind or list(KINDS)
    if args.preview:
        preview(args.preview, kinds)
    if args.write:
        write(kinds)


if __name__ == '__main__':
    main()
