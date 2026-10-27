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

chest = find_bone(d['outliner'], 'chest')
cubes = [els[c] for c in chest.get('children', []) if isinstance(c, str)]
print(f"Chest bone has {len(cubes)} cubes:")
for i, el in enumerate(cubes):
    sz = [round(el['to'][k] - el['from'][k], 2) for k in range(3)]
    if sz[0] > 1.0 or sz[1] > 1.0: # major elements
        print(f"{i:03d}: {el['name']:25} sz={sz} from={[round(x,2) for x in el['from']]} to={[round(x,2) for x in el['to']]} rot={el.get('rotation')}")
