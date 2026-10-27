import json

with open('evergarden/src/main/resources/modelengine/rivers/arkhaia_styx.bbmodel', 'r', encoding='utf-8') as f:
    d = json.load(f)

def print_tree(nodes, depth=0):
    for node in nodes:
        if isinstance(node, dict):
            child_nodes = node.get('children', [])
            cubes = [c for c in child_nodes if not isinstance(c, dict)]
            sub = [c for c in child_nodes if isinstance(c, dict)]
            print('  ' * depth + f"- {node.get('name')}: {len(cubes)} cubes")
            print_tree(sub, depth + 1)

print_tree(d['outliner'])
print('Total elements in json:', len(d['elements']))
print('Total animations:', len(d['animations']))
for a in d['animations'][:8]:
    print(f"  {a['name']:16} {a['length']}s {len(a['animators'])} animators")
