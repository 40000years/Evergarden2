import json

d = json.load(open('evergarden/src/main/resources/modelengine/rivers/arkhaia_banshee_wail.bbmodel.bak', encoding='utf-8'))
els = {e['uuid']: e for e in d['elements']}

def find_bone(nodes, name):
    for n in nodes:
        if isinstance(n, dict):
            if n.get('name') == name: return n
            res = find_bone(n.get('children', []), name)
            if res: return res
    return None

for bname in ['pelvis', 'sash_r', 'sash_l']:
    b = find_bone(d['outliner'], bname)
    cubes = [els[c] for c in b.get('children', []) if isinstance(c, str)]
    print(f"\nBone {bname} ({len(cubes)} cubes):")
    for el in cubes:
        sz = [round(el['to'][k] - el['from'][k], 2) for k in range(3)]
        print(f"  {el['name']:15} sz={sz} from={[round(x,2) for x in el['from']]} to={[round(x,2) for x in el['to']]} rot={el.get('rotation')}")
