import json
d = json.load(open('evergarden/src/main/resources/modelengine/rivers/arkhaia_acheron.bbmodel.bak', encoding='utf-8'))
print('Animations in Acheron:', len(d.get('animations', [])))
for a in d.get('animations', []):
    print(f"- {a.get('name')}: length={a.get('length')}s loop={a.get('loop')}")
