import json
d = json.load(open('evergarden/src/main/resources/modelengine/rivers/arkhaia_banshee_wail.bbmodel.bak', encoding='utf-8'))
print('Animations in Banshee:', len(d.get('animations', [])))
for a in d.get('animations', []):
    print(f"- {a.get('name')}: length={a.get('length')}s loop={a.get('loop')}")
