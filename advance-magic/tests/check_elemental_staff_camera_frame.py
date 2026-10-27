"""Check complete staff/visible-grip framing from shipped Bedrock geometry.

Uses the prior client-screenshot affine calibration, not a real client renderer.
The exporter is deliberately not imported. Perspective, hand bob and FOV changes
remain untested; this catches the concrete all-crowns-offscreen regression.
"""
import itertools
import json
from pathlib import Path
import zipfile

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / 'advance-magic/art/elemental-staffs'
REF = json.loads(Path(__file__).with_name('fixtures').joinpath('legendary-hand-reference.json').read_text())
P = np.array([[13, -3], [-11.68, -18.42], [2.5, 1.5]])
checks = 0


def check(ok, label):
    global checks
    assert ok, label
    checks += 1


def project(points, grip, scale, lift):
    points = np.asarray(points, dtype=float).copy()
    ratio = scale / .48
    points[:, 0] = 8 + ratio * (points[:, 0] - 8)
    points[:, 1] = 7.5 + ratio * (points[:, 1] - grip) + lift / .48
    points[:, 2] = 8 + ratio * (points[:, 2] - 8)
    return (points - [8, 31.25, 8]) @ P + [813, 197]


metadata = json.loads((ART / 'metadata.json').read_text())
with zipfile.ZipFile(ROOT / 'advance-magic/dist/advance-magic-bedrock.mcpack') as pack:
    animations = json.loads(pack.read('animations/elemental_staffs.animation.json'))['animations']
    for record in metadata['items']:
        name, grip = record['id'], record['grip'][1]
        geo = json.loads(pack.read(f'models/entity/elemental_staffs/{name}.geo.json'))['minecraft:geometry'][0]
        bones = {bone['name']: bone for bone in geo['bones']}
        # Reconstruct source coordinates independently from exported Bedrock.
        points = []
        for bone in bones.values():
            for cube in bone.get('cubes', []):
                origin, size = cube['origin'], cube['size']
                ends = [(origin[i], origin[i] + size[i]) for i in range(3)]
                for x, y, z in itertools.product(*ends):
                    points.append([8-x, y-12.5+grip, z+8])
        first = animations[f'animation.advance_magic.staff_{name}.first']['bones']
        scale, lift = first['grip']['scale'][0], first['grip']['position'][1]
        xy = project(points, grip, scale, lift)
        lo, hi = xy.min(0), xy.max(0)
        check(40 <= lo[1] <= 60 and 440 <= hi[1] <= 460, name+' complete crown and lower shaft fit the calibrated frame')
        check(500 < lo[0] < hi[0] < 1156, name+' entire solid remains in right-hand viewport')
        contact = project([[8, grip, 8]], grip, scale, lift)[0]
        check(250 < contact[1] < 400 and 700 < contact[0] < 1050, name+' physical hand contact remains visible')
        check(first['root']['scale'] == [1, 1, 1] and len(set(first['grip']['scale'])) == 1, name+' uniform scale is confined to grip child')
        check(first['grip']['position'][0] == first['grip']['position'][2] == 0, name+' framing translation follows the shaft')
        check(first['root']['rotation'][0] == REF['bedrock']['first']['rotation'][0], name+' native view orientation preserved')
        third = animations[f'animation.advance_magic.staff_{name}.third']['bones']
        check(third['grip'] == {'scale': [.9, .9, .9]}, name+' first-person fit cannot move world contact')
        check(third['root']['pivot'] == REF['bedrock']['pivot'] if 'pivot' in third['root'] else bones['root']['pivot'] == REF['bedrock']['pivot'], name+' native world frame preserved')
        print(name, 'predicted solid bounds', lo.round(1).tolist(), hi.round(1).tolist(), 'grip', contact.round(1).tolist())
print('PASS', checks, 'independent screenshot-calibrated staff camera checks (approximate; no game client rendering)')
