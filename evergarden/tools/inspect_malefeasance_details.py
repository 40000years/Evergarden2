import json

with open('evergarden/src/main/resources/modelengine/rivers/arkhaia_malefeasance.bbmodel.bak', 'r', encoding='utf-8') as f:
    data = json.load(f)

elements_by_uuid = {el.get('uuid'): el for el in data.get('elements', []) if isinstance(el, dict)}

def find_group(nodes, name):
    for n in nodes:
        if isinstance(n, dict):
            if n.get('name') == name:
                return n
            found = find_group(n.get('children', []), name)
            if found:
                return found
    return None

def inspect_group(group, name):
    print(f"\n=== GROUP: {name} (pivot: {group.get('origin')}) ===")
    for c in group.get('children', []):
        if isinstance(c, str):
            el = elements_by_uuid.get(c)
            if el:
                print(f"  Cube: {el.get('name')}, from={el.get('from')}, to={el.get('to')}, rot={el.get('rotation')}, origin={el.get('origin')}")
        elif isinstance(c, dict):
            print(f"  Subgroup: {c.get('name')}")

for grp_name in ['head', 'collar', 'cape', 'shoulder_r', 'shoulder_l', 'sash_r', 'sash_l', 'pelvis']:
    g = find_group(data.get('outliner', []), grp_name)
    if g:
        inspect_group(g, grp_name)
