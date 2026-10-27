import json

def analyze_model_elements(name, path):
    print(f"\n==================== {name} ====================")
    with open(path, 'r', encoding='utf-8') as f:
        d = json.load(f)
    els = {e['uuid']: e for e in d['elements']}
    
    def walk(nodes, depth=0):
        for n in nodes:
            if isinstance(n, dict):
                cubes = [els[c] for c in n.get('children', []) if isinstance(c, str)]
                if cubes:
                    print('  ' * depth + f"Bone: {n['name']} ({len(cubes)} cubes)")
                    for c in cubes[:10]:
                        sz = [round(c['to'][i] - c['from'][i], 2) for i in range(3)]
                        rot = c.get('rotation', [0,0,0])
                        print('  ' * (depth + 1) + f"- {c['name']:18} sz={sz} from={[round(x,2) for x in c['from']]} to={[round(x,2) for x in c['to']]} rot={rot}")
                walk(n.get('children', []), depth + 1)
    walk(d['outliner'])

analyze_model_elements('BANSHEE BAK', 'evergarden/src/main/resources/modelengine/rivers/arkhaia_banshee_wail.bbmodel.bak')
