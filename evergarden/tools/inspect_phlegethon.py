import json
from pathlib import Path

path = Path('evergarden/src/main/resources/modelengine/rivers/arkhaia_phlegethon.bbmodel.bak')
data = json.loads(path.read_text(encoding='utf-8'))

def dump(nodes, parent=None):
    res = []
    for n in nodes:
        if isinstance(n, dict):
            name = n.get('name')
            orig = n.get('origin', [0, 0, 0])
            cubes = sum(1 for c in n.get('children', []) if not isinstance(c, dict))
            res.append((name, parent, orig, cubes))
            res.extend(dump(n.get('children', []), name))
    return res

bones = dump(data.get('outliner', []))
for name, parent, orig, cubes in bones:
    print(f"('{name}', '{parent}', {orig}, cubes={cubes}),")
