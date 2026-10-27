import json

with open('evergarden/src/main/resources/modelengine/rivers/arkhaia_banshee_wail.bbmodel.bak', 'r', encoding='utf-8') as f:
    d = json.load(f)
els = {e['uuid']: e for e in d['elements']}

def find_group(nodes, name):
    for n in nodes:
        if isinstance(n, dict):
            if n.get('name') == name: return n
            res = find_group(n.get('children', []), name)
            if res: return res
    return None

h = find_group(d['outliner'], 'halo')
print('Halo origin:', h['origin'])
print('Halo cube count:', len(h['children']))
for c in h['children']:
    if isinstance(c, str):
        el = els[c]
        sz = [round(el['to'][i] - el['from'][i], 2) for i in range(3)]
        print(f"  {el['name']:16} sz={sz} from={[round(x,2) for x in el['from']]} rot={el.get('rotation')}")
