"""Banshee Wail (banshee-mourning-veil) contract, motion and Bedrock checks on the shipped blueprint.

Offline: evaluates the exact .bbmodel keyframes with the preview renderer's rig. It checks the
river contract RiverVisual, Geyser and the probes rely on, clearance below the identity seal,
the wail/curse choreography, and that the dist Bedrock pack carries this exact rig.
"""
import json
import re
import sys
import zipfile
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'evergarden/tools'))
from arkhaia_bedrock_assets import export_model  # noqa: E402
from arkhaia_river_assets import bedrock_scale  # noqa: E402
from boss_forge import rot  # noqa: E402
from boss_render import Rig  # noqa: E402

MODEL = 'arkhaia_banshee_wail'
# ArkhaiaBedrockSupport.riverClips(MODEL) order and RiverVisual.frame lengths.
CLIPS = {'idle': 2.4, 'walk': 1.0, 'hover': 2.0, 'cast_windup': 1.0, 'cast_active': .5, 'cast_recover': .7, 'attack_windup': .8,
         'attack_active': .4, 'attack_recover': .7, 'stun': 1.5, 'curse_windup': 1.0, 'curse_active': .5, 'curse_recover': .7,
         'reveal': 1.0, 'reform': 1.0}
AURA = {'halo', 'wisps'}
SCALE = 4.0
SEAL_HEIGHT = 3.075 * SCALE  # RiverVisual.haloHeight(BANSHEE_WAIL), blocks above the feet
checks = 0


def check(ok, label):
    global checks
    assert ok, label
    checks += 1


model = json.loads((ROOT / f'evergarden/src/main/resources/modelengine/rivers/{MODEL}.bbmodel').read_text(encoding='utf8'))
rig = Rig(model)
clips = {c['name']: c for c in model['animations']}
check(model['meta'].get('evergarden_revision') == 'banshee-mourning-veil-v1', 'mourning-veil revision shipped')
check(model['model_identifier'] == MODEL and model['resolution'] == {'width': 1024, 'height': 1024}, 'identifier and 1024 atlas')

# Clip table: Geyser indexes the non-aura clips in file order.
combat = [c['name'] for c in model['animations'] if c['name'] != 'aura']
check(combat == list(CLIPS), f'clip order matches the Java table: {combat}')
java = (ROOT / 'evergarden/src/main/java/com/example/voidscape/boss/ArkhaiaBedrockSupport.java').read_text(encoding='utf8')
extra = re.search(r'arkhaia_banshee_wail"\)\)\{\s*List<String> clips=new ArrayList<>\(RIVER_CLIPS\.subList\(0,10\)\);clips\.addAll\(List\.of\(([^)]*)\)\)', java)
check(extra and [s.strip().strip('"') for s in extra.group(1).split(',')] == combat[10:], 'Java Banshee clip table unchanged')
for name, length in CLIPS.items():
    check(abs(clips[name]['length'] - length) < 1e-6, f'{name} length {length}s matches RiverVisual')
    check(clips[name]['override'] and (clips[name]['loop'] == 'loop') == (name in ('idle', 'walk', 'hover')), f'{name} override/loop')
check('aura' in clips and clips['aura']['loop'] == 'loop', 'aura loops on its own lane')

# Bones: the river contract and complete keying.
bones = set(rig.groups) - {'hitbox'}
required = {'root', 'pelvis', 'chest', 'hi_gaze', 'head', 'head_forward', 'jaw', 'cape', 'cape_tip', *(f'veil_{n}' for n in range(6)),
            *(f'{b}_{s}' for b in ('shoulder', 'arm', 'forearm', 'hand', 'weapon') for s in 'rl')}
check(required <= bones, f'contract bones present: {sorted(required - bones)}')
check(rig.groups['head_forward'][1] == 'head' and rig.groups['head'][1] == 'hi_gaze', 'wail origin follows the head and gaze')
check(len(bones) <= 48, f'{len(bones)} bones keeps the river light')
body = bones - AURA
for name in CLIPS:
    keyed = {a['name'] for a in clips[name]['animators'].values()}
    check(keyed == body, f'{name} keys every body bone and no aura bone')
check({a['name'] for a in clips['aura']['animators'].values()} == AURA, 'aura clip keys only aura bones')

# Loops close without a seam.
for name in ('idle', 'walk', 'hover'):
    a, b = rig.matrices(name, 0.0), rig.matrices(name, CLIPS[name] - 1e-6)
    check(max(np.abs(a[k] - b[k]).max() for k in a) < 1e-3, f'{name} loops seamlessly')


def extents(clip, t):
    m = rig.matrices(clip, t, aura_time=t * 3.7)
    pts = np.concatenate([w for _, w, _ in rig.cuboids(m)])
    return pts.min(axis=0), pts.max(axis=0), m


face_z = min(e['from'][2] for e in model['elements'] if e['name'].startswith('head_'))
for name, length in CLIPS.items():
    for t in np.linspace(0, length, 13):
        lo, hi, m = extents(name, float(t))
        check(hi[1] * SCALE / 16 < SEAL_HEIGHT - .4, f'{name} {t:.2f}s stays under the identity seal ({hi[1] * SCALE / 16:.2f} blocks)')
        check(lo[1] > -.3, f'{name} {t:.2f}s stays above the floor ({lo[1]:.2f})')
        origin = rig.point(m, 'head_forward')
        head = rig.point(m, 'head', (0, 35.5, face_z))
        check(origin[1] * SCALE / 16 > 2 and np.linalg.norm(origin[[0, 2]]) < 16 * 16 / SCALE, f'{name} wail origin usable by castOrigin')
        check(np.linalg.norm(origin - head) < 9, f'{name} wail origin stays at the mouth')

# Wail: the jaw drops and the veil streams behind the forward scream (the river choreography check).
ready, contact = rig.matrices('attack_windup', .8), rig.matrices('attack_active', .2)
for n in range(6):
    tail = np.array([(n - 2.5) * 1.5, 5, 4.2, 1])
    check((contact[f'veil_{n}'] @ tail)[2] > (ready[f'veil_{n}'] @ tail)[2] + 5, f'veil_{n} streams behind the scream')


def jaw_open(clip, t):
    m = rig.matrices(clip, t)
    rel = np.linalg.inv(m['head'][:3, :3]) @ m['jaw'][:3, :3]
    return float(np.degrees(np.arctan2(rel[2, 1], rel[1, 1])))


check(jaw_open('attack_active', .2) < -20 and jaw_open('hover', .5) > -6, 'jaw opens for the wail and rests closed')
mouth = rig.point(contact, 'head', (0, 33.6, -2.4))
check(mouth[2] < rig.point(contact, 'chest')[2], 'scream thrown forward of the chest')
# Curse: hands rise above the head, then the cape trails behind the forward release.
gather = rig.matrices('curse_windup', 1.0)
check(min(rig.point(gather, f'weapon_{s}')[1] for s in 'rl') > rig.point(gather, 'head', (0, 36.3, face_z))[1] + 1, 'curse raises both palms above the eyes')
release = rig.matrices('curse_active', .3)
check(rig.point(release, 'cape_tip')[2] > rig.point(release, 'pelvis')[2] + 2, 'cape trails behind the curse release')
check(rig.point(rig.matrices('reveal', .6), 'head', (0, 38, 0))[2] > rig.point(rig.matrices('hover', .6), 'head', (0, 38, 0))[2],
      'reveal throws the head back')

# Body clearance on the real geometry: forearms and hands never pass through the bodice, hips or head.
owner = rig.owners()


def solid(e, m):
    lo, hi = np.array(e['from'], float), np.array(e['to'], float)
    r = rot(e.get('rotation', [0, 0, 0]))
    pivot = np.array(e.get('origin', [0, 0, 0]), float)
    center = r @ ((lo + hi) / 2 - pivot) + pivot
    return m[:3, :3] @ center + m[:3, 3], m[:3, :3] @ r, (hi - lo) / 2


def overlaps(a, b, slack=.2):
    ca, ra, ea = a
    cb, rb, eb = b
    axes = np.vstack((ra.T, rb.T, np.cross(ra.T[:, None, :], rb.T[None, :, :]).reshape(9, 3)))
    axes = axes[np.linalg.norm(axes, axis=1) > 1e-8]
    axes /= np.linalg.norm(axes, axis=1)[:, None]
    return not np.any(np.abs(axes @ (cb - ca)) >= np.abs(axes @ ra) @ ea + np.abs(axes @ rb) @ eb - slack)


limbs = [e for e in model['elements'] if owner.get(e['uuid'], '').startswith(('forearm_', 'hand_'))
         and e['name'].split('_')[0] in ('forearm', 'hands', 'cuff')]
cores = [e for e in model['elements'] if e['name'].rsplit('_', 1)[0] in ('bodice', 'bust', 'decolletage', 'hips', 'head', 'jaw')]
check(len(limbs) > 40 and len(cores) > 8, 'limb and body solids found')
for name, length in CLIPS.items():
    for t in np.linspace(0, length, 31):
        m = rig.matrices(name, float(t))
        bodies = [solid(e, m[owner[e['uuid']]]) for e in cores]
        for e in limbs:
            box = solid(e, m[owner[e['uuid']]])
            check(not any(overlaps(box, body) for body in bodies), f'{name} {t:.2f}s {e["name"]} clears the body')

# Atlas: every textured face samples inside the 1024 atlas.
for e in model['elements']:
    for face in e.get('faces', {}).values():
        if face.get('texture') is not None:
            check(all(0 <= v <= 1024 for v in face['uv']), f"{e['name']} UV inside atlas")

# Bedrock: the dist pack ships this exact rig with the same clip table.
native = export_model(model, MODEL, bedrock_scale(MODEL))
with zipfile.ZipFile(ROOT / 'evergarden/dist/evergarden-bedrock.mcpack') as pack:
    for path, data in native.items():
        check(pack.read(path) == data, f'Bedrock pack carries current {path}')
    version = '.'.join(map(str, json.loads(pack.read('manifest.json'))['header']['version']))
controller = json.loads(native[f'animation_controllers/{MODEL}_native.controller.json'])['animation_controllers']
check(list(next(iter(controller.values()))['states']) == list(CLIPS), 'Bedrock controller states follow the Java table')
design = json.loads((ROOT / 'evergarden/src/main/resources/judge-design.json').read_text(encoding='utf8'))
check(design['bedrock-version'] == version, f'judge-design reports Bedrock {version}')
print(f'PASS {checks} Banshee mourning-veil contract, clearance, choreography and Bedrock checks')
