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

halo = find_bone(d['outliner'], 'halo')
print(f"Halo bone has {len(halo['children'])} children:")
for i, c in enumerate(halo['children']):
    if isinstance(c, str):
        el = els[c]
        sz = [round(el['to'][k] - el['from'][k], 2) for k in range(3)]
        print(f"{i:02d}: {el['name']:15} sz={sz} from={[round(x,2) for x in el['from']]} to={[round(x,2) for x in el['to']]} rot={el.get('rotation')}")
