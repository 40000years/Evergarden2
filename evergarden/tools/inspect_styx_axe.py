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
if w_r:
    print(f"=== WEAPON_R CUBES ({len(w_r['children'])}) ===")
    cubes = [els[c] for c in w_r['children'] if isinstance(c, str)]
    for c in cubes[:20]:
        f = c['from']
        t = c['to']
        sz = [round(t[i]-f[i], 2) for i in range(3)]
        print(f"  {c['name']:20} sz={sz} from={f} to={t} rot={c.get('rotation')}")
    print(f"  ... and {len(cubes)-20} more cubes")

# Inspect head
hd = find_group(d['outliner'], 'head')
if hd:
    print(f"\n=== HEAD CUBES ({len(hd['children'])}) ===")
    cubes = [els[c] for c in hd['children'] if isinstance(c, str)]
    for c in cubes[:15]:
        f = c['from']
        t = c['to']
        sz = [round(t[i]-f[i], 2) for i in range(3)]
        print(f"  {c['name']:20} sz={sz} from={f} to={t} rot={c.get('rotation')}")
