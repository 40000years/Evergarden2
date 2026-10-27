import json

with open('evergarden/src/main/resources/modelengine/rivers/arkhaia_styx.bbmodel.bak', 'r', encoding='utf-8') as f:
    d = json.load(f)

print("Meta:", d.get('meta'))
print("Elements count:", len(d.get('elements', [])))
print("Textures:", [t.get('name') for t in d.get('textures', [])])

def print_tree(nodes, depth=0):
    for node in nodes:
        if isinstance(node, dict):
            child_nodes = node.get('children', [])
            cubes = [c for c in child_nodes if not isinstance(c, dict)]
            sub = [c for c in child_nodes if isinstance(c, dict)]
            pivot = node.get('origin', [0, 0, 0])
            print('  ' * depth + f"- {node.get('name')} (cubes: {len(cubes)}, groups: {len(sub)}, origin: {pivot}, uuid: {node.get('uuid')})")
            print_tree(sub, depth + 1)

print("\n--- BONE TREE ---")
print_tree(d.get('outliner', []))

print("\n--- ANIMATIONS ---")
for anim in d.get('animations', []):
    name = anim.get('name')
    length = anim.get('length')
    animators = list(anim.get('animators', {}).keys())
    print(f"Anim: {name:16} | length: {length:4}s | animated bones: {len(animators)}")
