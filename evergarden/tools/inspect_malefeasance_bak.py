import json

with open('evergarden/src/main/resources/modelengine/rivers/arkhaia_malefeasance.bbmodel.bak', 'r', encoding='utf-8') as f:
    data = json.load(f)

print('Outliner count:', len(data.get('outliner', [])))
print('Elements count:', len(data.get('elements', [])))
print('Textures:', [t.get('name') for t in data.get('textures', [])])

def print_tree(nodes, depth=0):
    for node in nodes:
        if isinstance(node, dict):
            child_nodes = node.get('children', [])
            cubes = [c for c in child_nodes if not isinstance(c, dict)]
            sub = [c for c in child_nodes if isinstance(c, dict)]
            pivot = node.get('origin', [0, 0, 0])
            print('  ' * depth + f"- {node.get('name')} (cubes: {len(cubes)}, groups: {len(sub)}, origin: {pivot})")
            print_tree(sub, depth + 1)

print("\n--- BONE TREE ---")
print_tree(data.get('outliner', []))

# Let's see some element details (names, boxes, etc.)
print("\n--- SAMPLE ELEMENTS ---")
for el in data.get('elements', [])[:30]:
    print(f"Name: {el.get('name')}, from: {el.get('from')}, to: {el.get('to')}, rot: {el.get('rotation')}, origin: {el.get('origin')}")
