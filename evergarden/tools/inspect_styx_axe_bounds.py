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
min_pt = [9999, 9999, 9999]
max_pt = [-9999, -9999, -9999]

for c in w_r['children']:
    if isinstance(c, str):
        el = els[c]
        f = el['from']
        t = el['to']
        for i in range(3):
            min_pt[i] = min(min_pt[i], f[i], t[i])
            max_pt[i] = max(max_pt[i], f[i], t[i])

print("Axe bounding box in world space (untransformed T-pose):")
print("Min:", [round(x, 2) for x in min_pt])
print("Max:", [round(x, 2) for x in max_pt])
print("Size:", [round(max_pt[i] - min_pt[i], 2) for i in range(3)])

# Also find weapon_r origin
print("weapon_r origin:", w_r['origin'])
print("axe_driver origin:", find_group(d['outliner'], 'axe_driver')['origin'])
print("hand_r origin:", find_group(d['outliner'], 'hand_r')['origin'])
print("hand_l origin:", find_group(d['outliner'], 'hand_l')['origin'])
