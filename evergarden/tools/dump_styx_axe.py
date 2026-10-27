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

w_r = find_group(d['outliner'], 'weapon_r')
print("Total cubes in weapon_r:", len(w_r['children']))
for c in w_r['children']:
    if isinstance(c, str):
        el = els[c]
        sz = [round(el['to'][i]-el['from'][i], 2) for i in range(3)]
        print(f"{el['name']:24} sz={sz} from={el['from']} to={el['to']} rot={el.get('rotation')}")
