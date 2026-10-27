import json

with open('evergarden/src/main/resources/modelengine/rivers/arkhaia_styx.bbmodel.bak', 'r', encoding='utf-8') as f:
    d = json.load(f)

els = {e['uuid']: e for e in d['elements']}

def find_group(nodes, name):
    for n in nodes:
        if isinstance(n, dict):
            if n.get('name') == name: return n
            res = find_group(n.get('children', []), name)
            if res: return res
    return None

for bname in ['pelvis', 'chest', 'leg_r', 'leg_l']:
    grp = find_group(d['outliner'], bname)
    print(f"=== {bname} (origin {grp['origin']}) ===")
    cubes = [c for c in grp['children'] if isinstance(c, str)]
    for c in cubes:
        el = els[c]
        print(f"  {el['name']:20} y=[{el['from'][1]:.2f}, {el['to'][1]:.2f}] x=[{el['from'][0]:.2f}, {el['to'][0]:.2f}] z=[{el['from'][2]:.2f}, {el['to'][2]:.2f}]")
