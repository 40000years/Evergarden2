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

for bname in ['head', 'chest', 'halo', 'veil_0', 'veil_1', 'sash_l', 'sash_r', 'pelvis']:
    grp = find_group(d['outliner'], bname)
    cubes = [els[c] for c in grp['children'] if isinstance(c, str)]
    print(f"=== {bname} ({len(cubes)} cubes) ===")
    for c in cubes[:5]:
        print(f"  {c['name']:20} sz={[round(c['to'][i]-c['from'][i], 2) for i in range(3)]} from={c['from']}")
