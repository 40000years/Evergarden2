import json

def inspect_model(name, path):
    print(f"\n==================== {name} ====================")
    with open(path, 'r', encoding='utf-8') as f:
        d = json.load(f)
    print("Meta:", d.get('meta'))
    print("Resolution:", d.get('resolution'))
    print("Elements count:", len(d.get('elements', [])))
    print("Textures:", [t.get('name') for t in d.get('textures', [])])
    
    def print_tree(nodes, depth=0):
        for node in nodes:
            if isinstance(node, dict):
                child_nodes = node.get('children', [])
                cubes = [c for c in child_nodes if not isinstance(c, dict)]
                sub = [c for c in child_nodes if isinstance(c, dict)]
                print('  ' * depth + f"- {node.get('name')} (cubes: {len(cubes)}, origin: {node.get('origin', [0,0,0])})")
                print_tree(sub, depth + 1)
                
    print("\n--- BONE TREE ---")
    print_tree(d.get('outliner', []))
    
    print("\n--- ANIMATIONS ---")
    for anim in d.get('animations', []):
        print(f"  {anim.get('name'):18} | len: {anim.get('length'):4}s | animators: {len(anim.get('animators', {}))}")

inspect_model('ACHERON', 'evergarden/src/main/resources/modelengine/rivers/arkhaia_acheron.bbmodel.bak')
inspect_model('ACHERON ECHO', 'evergarden/src/main/resources/modelengine/rivers/arkhaia_acheron_echo.bbmodel.bak')
inspect_model('BANSHEE WAIL', 'evergarden/src/main/resources/modelengine/rivers/arkhaia_banshee_wail.bbmodel.bak')
