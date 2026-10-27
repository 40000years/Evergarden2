import json

with open('evergarden/src/main/resources/modelengine/rivers/arkhaia_malefeasance.bbmodel.bak', 'r', encoding='utf-8') as f:
    d = json.load(f)

els = {e['uuid']: e for e in d['elements']}

def dump_all_elements(node, path=""):
    name = node.get('name')
    curr_path = f"{path}/{name}" if path else name
    children = node.get('children', [])
    cubes = [elements_by_uuid[c] for c in children if isinstance(c, str) and c in elements_by_uuid]
    if cubes:
        print(f"\n[{curr_path}] (pivot={node.get('origin')}, count={len(cubes)})")
        for c in cubes:
            f = c.get('from')
            t = c.get('to')
            sz = [round(t[i] - f[i], 2) for i in range(3)]
            rot = c.get('rotation', [0, 0, 0])
            print(f"  {c.get('name'):24} sz={sz} from={f} to={t} rot={rot}")
    for ch in children:
        if isinstance(ch, dict):
            dump_all_elements(ch, curr_path)

elements_by_uuid = els
dump_all_elements(d['outliner'][0])
