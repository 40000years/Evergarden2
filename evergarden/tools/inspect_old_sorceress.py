import json

with open('evergarden/src/main/resources/modelengine/rivers/arkhaia_malefeasance.bbmodel.bak', 'r', encoding='utf-8') as f:
    d = json.load(f)

print("Texture size:", d['textures'][0].get('name'))
# Let's inspect all cubes in head and chest
els = {e['uuid']: e for e in d['elements']}

def describe_cube(c):
    return f"{c.get('name')}: from={c.get('from')} to={c.get('to')} size={[round(c['to'][i]-c['from'][i], 2) for i in range(3)]} rot={c.get('rotation')}"

print("\n--- HEAD ELEMENTS ---")
for node in d['outliner']:
    def find_node(n, name):
        if n.get('name') == name: return n
        for ch in n.get('children', []):
            if isinstance(ch, dict):
                r = find_node(ch, name)
                if r: return r
        return None
    h = find_node(node, 'head')
    if h:
        for c in h.get('children', []):
            if isinstance(c, str):
                print(describe_cube(els[c]))

print("\n--- CHEST ELEMENTS ---")
ch = find_node(d['outliner'][0], 'chest')
if ch:
    for c in ch.get('children', []):
        if isinstance(c, str):
            print(describe_cube(els[c]))
