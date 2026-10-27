import json

with open('evergarden/src/main/resources/modelengine/rivers/arkhaia_malefeasance.bbmodel.bak', 'r', encoding='utf-8') as f:
    d = json.load(f)

def print_bone_defs(node, parent=None):
    name = node.get('name')
    origin = node.get('origin', [0, 0, 0])
    uuid_str = node.get('uuid')
    print(f'("{name}", "{parent}", {origin}, "{uuid_str}"),')
    for c in node.get('children', []):
        if isinstance(c, dict):
            print_bone_defs(c, name)

for r in d['outliner']:
    print_bone_defs(r)
