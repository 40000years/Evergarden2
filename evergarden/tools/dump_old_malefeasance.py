import json

with open('evergarden/src/main/resources/modelengine/rivers/arkhaia_malefeasance.bbmodel.bak', 'r', encoding='utf-8') as f:
    d = json.load(f)

elements_by_uuid = {el.get('uuid'): el for el in d.get('elements', []) if isinstance(el, dict)}

def dump_group(grp, indent=0):
    name = grp.get('name')
    origin = grp.get('origin')
    children = grp.get('children', [])
    cubes = [elements_by_uuid[c] for c in children if isinstance(c, str) and c in elements_by_uuid]
    subgroups = [c for c in children if isinstance(c, dict)]
    print("  " * indent + f"Bone: {name} (origin={origin}, {len(cubes)} cubes):")
    for cb in cubes[:5]: # show first 5
        f = cb.get('from')
        t = cb.get('to')
        sz = [round(t[i] - f[i], 2) for i in range(3)]
        rot = cb.get('rotation', [0, 0, 0])
        print("  " * (indent + 1) + f"Cube: {cb.get('name')} | size={sz} | from={f} | to={t} | rot={rot}")
    if len(cubes) > 5:
        print("  " * (indent + 1) + f"... and {len(cubes) - 5} more cubes")
    for sg in subgroups:
        dump_group(sg, indent + 1)

for root in d.get('outliner', []):
    dump_group(root)
