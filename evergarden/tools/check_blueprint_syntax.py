import json

def check_file(path):
    print("Checking:", path)
    with open(path, 'r', encoding='utf-8') as f:
        d = json.load(f)
    print("Meta:", d.get('meta'))
    print("Elements count:", len(d.get('elements', [])))
    for idx, e in enumerate(d.get('elements', [])):
        f_val = e.get('from')
        if not isinstance(f_val, list):
            print(f"Element {idx} has invalid from: {f_val} ({type(f_val)})")
        t_val = e.get('to')
        if not isinstance(t_val, list):
            print(f"Element {idx} has invalid to: {t_val} ({type(t_val)})")
        orig = e.get('origin')
        if orig is not None and not isinstance(orig, list):
            print(f"Element {idx} has invalid origin: {orig} ({type(orig)})")
    
    # Check outliner
    def check_node(n):
        if isinstance(n, dict):
            orig = n.get('origin')
            if orig is not None and not isinstance(orig, list):
                print(f"Bone {n.get('name')} has invalid origin: {orig}")
            for c in n.get('children', []):
                check_node(c)
    for n in d.get('outliner', []):
        check_node(n)
    print("Check completed successfully!")

check_file('C:/Users/User/Desktop/Serva/plugins/ModelEngine/blueprints/arkhaia_malefeasance.bbmodel')
check_file('C:/Users/User/Afterdeath/evergarden2/evergarden/src/main/resources/modelengine/rivers/arkhaia_malefeasance.bbmodel')
