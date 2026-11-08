"""Gate for the Dragon Nest hunt assets: attack clips, merged ModelEngine blueprint, Bedrock entities, server timings.

Checks, without a server:
  * every dragon rig has the twelve clips in RealmBosses.DRAGON_CLIPS order (Bedrock indexes that list);
  * the merged blueprint (realm_bosses) carries forge_dragon_<kind>_<clip> for all twelve, keyed to that rig's
    prefixed bones only, with the same keyframes as the rig (Java ModelEngine and the server hitbox agree);
  * contact frames sit where DragonNestBosses strikes: the bite's jaws reach the ground BITE_REACH ahead at BITE_SNAP,
    the claws land in the rake zone, the breath sweeps left to right, the cast slams down, the tail swings behind;
  * the Bedrock pack has a native entity per dragon with a 12-state controller and the 2048 atlas, and
    judge-design.json names the pack's version.

    python tests/model-engine/check_dragon_hunt.py
"""
from __future__ import annotations

import io
import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'evergarden/tools'))
sys.path.insert(0, str(ROOT / 'tools'))
from boss_render import Rig  # noqa: E402
import build_dragon_hunt_bedrock as bedrock  # noqa: E402

RES = ROOT / 'evergarden/src/main/resources'
JAVA = ROOT / 'evergarden/src/main/java/com/example/voidscape/boss'
CODES = {'forge_dragon_fire': 'df', 'forge_dragon_storm': 'ds', 'forge_dragon_ice': 'di'}
BLOCK = 5.5 / 16
passed = 0


def check(ok, label):
    global passed
    if not ok:
        raise AssertionError(label)
    passed += 1


def java_list(source: str, name: str) -> list[str]:
    match = re.search(name + r'\s*=\s*List\.of\(([^;]*)\);', source, re.S)
    return re.findall(r'"([^"]+)"', match.group(1))


def java_numbers(source: str) -> dict[str, float]:
    found = {}
    for name, value in re.findall(r'\b([A-Z_]+)\s*=\s*(-?\d+(?:\.\d+)?)', source):
        found.setdefault(name, float(value))
    return found


def frames(clip):
    out = {}
    for animator in clip['animators'].values():
        out[animator['name']] = sorted((k['channel'], k['time'], tuple(k['data_points'][0][a] for a in 'xyz')) for k in animator['keyframes'])
    return out


def bones(model):
    out = {}

    def walk(nodes):
        for node in nodes:
            if isinstance(node, dict):
                out[node['uuid']] = node['name']
                walk(node['children'])
    walk(model['outliner'])
    return out


def main():
    realm = (JAVA / 'RealmBosses.java').read_text(encoding='utf8')
    clips = java_list(realm, 'DRAGON_CLIPS')
    check(clips == bedrock.CLIPS, f'RealmBosses.DRAGON_CLIPS {clips}')
    hunt = (JAVA / 'DragonNestBosses.java').read_text(encoding='utf8')
    n = java_numbers(hunt)
    merged = json.loads((RES / 'modelengine/realm/realm_bosses.bbmodel').read_text(encoding='utf8'))
    merged_clips = {a['name']: a for a in merged['animations']}
    merged_bones = bones(merged)
    for model_id, code in CODES.items():
        model = json.loads((RES / f'modelengine/dragons/{model_id}.bbmodel').read_text(encoding='utf8'))
        names = [a['name'] for a in model['animations']]
        check(names == clips, f'{model_id} clip order {names}')
        rig_clips = {a['name']: a for a in model['animations']}
        for clip in clips:
            key = f'{model_id}_{clip}'
            check(key in merged_clips, f'merged blueprint lacks {key}')
            m = merged_clips[key]
            check(abs(m['length'] - rig_clips[clip]['length']) < 1e-9, f'{key} length')
            check(all(merged_bones.get(uuid, '').startswith(code + '_') for uuid in m['animators']), f'{key} keys foreign bones')
            ours = frames(rig_clips[clip])
            theirs = {name[len(code) + 1:]: value for name, value in frames(m).items()}
            check(ours == theirs, f'{key} keyframes differ from the rig')
        rig = Rig(model)

        def at(clip, time, bone):
            p = rig.point(rig.matrices(clip, time), bone)
            return p[0] * BLOCK, p[1] * BLOCK, -p[2] * BLOCK  # anatomical right, up, forward

        snap = n['BITE_SNAP'] / 20
        right, up, forward = at('bite', snap, 'fx_mouth')
        check(up < 3.5 and abs(forward - n['BITE_REACH']) < 0.6 and abs(right) < 1, f'{model_id} bite jaws {right:.1f},{up:.1f},{forward:.1f}')
        rest = at('bite', 0.42, 'fx_mouth')
        check(rest[1] > 20, f'{model_id} bite rears up first {rest}')
        for side, tick, bone, sign in (('right', 'CLAW_RIGHT', 'forefoot_r', 1), ('left', 'CLAW_LEFT', 'forefoot_l', -1)):
            r, u, f = at('claw', n[tick] / 20, bone)
            check(u < 7 and 5 <= f <= 21 and r * sign > 0, f'{model_id} {side} claw lands in the rake {r:.1f},{u:.1f},{f:.1f}')
            raised = at('claw', n[tick] / 20 - 0.17, bone)
            check(raised[1] > u + 3, f'{model_id} {side} claw is raised before it falls {raised}')
        start, end = at('breath', n['BREATH_FROM'] / 20, 'fx_mouth'), at('breath', n['BREATH_TO'] / 20, 'fx_mouth')
        check(start[0] < -5 and end[0] > 5, f'{model_id} breath sweeps left to right {start} {end}')
        check(start[1] < 14 and end[1] < 14, f'{model_id} breath stream starts low enough {start} {end}')
        cast_top, cast_slam = at('cast', n['CAST_RELEASE'] / 20 - 0.2, 'fx_mouth'), at('cast', n['CAST_RELEASE'] / 20, 'fx_mouth')
        check(cast_top[1] > 30 and cast_slam[1] < 12, f'{model_id} cast rises then slams {cast_top} {cast_slam}')
        tail = at('tail_whip', n['TAIL_CONTACT'] / 20, 'tail7')
        check(tail[2] < -25, f'{model_id} tail swings behind {tail}')
        for clip in ('bite', 'claw', 'cast', 'breath'):
            for t in [i * 0.1 for i in range(int(rig_clips[clip]['length'] * 10) + 1)]:
                low = min(at(clip, t, bone)[1] for bone in ('tail3', 'tail5', 'tail7', 'hindfoot_r', 'hindfoot_l'))
                check(low > -1.6, f'{model_id} {clip} {t:.1f}s sinks below the ground ({low:.1f})')
    pack = bedrock.PACK.read_bytes()
    problems = bedrock.check(pack)
    check(not problems, 'Bedrock pack: ' + '; '.join(problems))
    with zipfile.ZipFile(io.BytesIO(pack)) as z:
        version = '.'.join(map(str, json.loads(z.read('manifest.json'))['header']['version']))
    design = (RES / 'judge-design.json').read_text(encoding='utf8')
    check(f'"bedrock-version":"{version}"' in design.replace(' ', ''), f'judge-design.json bedrock-version != {version}')
    hashes = json.loads(bedrock.HASHES.read_text(encoding='utf8'))
    import hashlib
    check(hashes['evergarden-bedrock.mcpack'] == hashlib.sha1(pack).hexdigest(), 'pack-hashes.json is stale')
    print(f'PASS {passed} Dragon Nest hunt clips, merged blueprint, contact frames and Bedrock entities')


if __name__ == '__main__':
    main()
