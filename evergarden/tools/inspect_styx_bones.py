import json

with open('evergarden/src/main/resources/modelengine/rivers/arkhaia_styx.bbmodel.bak', 'r', encoding='utf-8') as f:
    d = json.load(f)

bones = {}
def walk(nodes, parent=None):
    for n in nodes:
        if isinstance(n, dict):
            name = n['name']
            bones[name] = {
                'parent': parent,
                'origin': n.get('origin', [0,0,0]),
                'cubes': len([c for c in n.get('children', []) if not isinstance(c, dict)]),
                'uuid': n['uuid']
            }
            walk(n.get('children', []), name)
walk(d['outliner'])

print(f'Total bones: {len(bones)}')
for name, info in bones.items():
    print(f"{name:16} parent={str(info['parent']):12} origin={info['origin']} cubes={info['cubes']}")
