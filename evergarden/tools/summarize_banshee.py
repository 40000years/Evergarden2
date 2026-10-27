import json

d = json.load(open('evergarden/src/main/resources/modelengine/rivers/arkhaia_banshee_wail.bbmodel.bak', encoding='utf-8'))
els = {e['uuid']: e for e in d['elements']}

def summarize_bone(b, depth=0):
    name = b['name']
    cubes = [els[c] for c in b.get('children', []) if isinstance(c, str)]
    sub_bones = [c for c in b.get('children', []) if isinstance(c, dict)]
    origin = b.get('origin', [0, 0, 0])
    rot = b.get('rotation', [0, 0, 0])
    print('  ' * depth + f"Bone: {name:15} cubes={len(cubes):3d} origin={origin} rot={rot}")
    for sb in sub_bones:
        summarize_bone(sb, depth + 1)

for root_b in d['outliner']:
    if isinstance(root_b, dict):
        summarize_bone(root_b)
