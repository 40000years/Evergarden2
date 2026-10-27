import json
from pathlib import Path

BLUEPRINTS = [
    Path('evergarden/src/main/resources/modelengine/rivers/arkhaia_acheron.bbmodel'),
    Path('evergarden/src/main/resources/modelengine/rivers/arkhaia_acheron_echo.bbmodel'),
    Path('evergarden/src/main/resources/modelengine/rivers/arkhaia_banshee_wail.bbmodel'),
]

for p in BLUEPRINTS:
    print(f"\nVerifying {p.name}:")
    assert p.exists(), f"Missing file: {p}"
    d = json.load(open(p, encoding='utf-8'))
    
    print(f"  Format version: {d.get('meta', {}).get('format_version')}")
    print(f"  Model identifier: {d.get('model_identifier')}")
    print(f"  Elements: {len(d.get('elements', []))}")
    print(f"  Animations: {len(d.get('animations', []))}")
    print(f"  Textures: {len(d.get('textures', []))}")
    
    # Check hitbox
    hitbox_el = [e for e in d['elements'] if e.get('name') == 'hitbox']
    print(f"  Hitbox element: {len(hitbox_el)} found")
    
    # Check bones
    bones = []
    def walk_bones(nodes):
        for n in nodes:
            if isinstance(n, dict):
                bones.append(n['name'])
                walk_bones(n.get('children', []))
    walk_bones(d['outliner'])
    print(f"  Bones count: {len(bones)}")
    assert 'root' in bones, "Root bone missing"
    assert 'pelvis' in bones, "Pelvis bone missing"
    assert 'chest' in bones, "Chest bone missing"
    assert 'head' in bones, "Head bone missing"
    assert 'sash_r' in bones, "sash_r bone missing"
    assert 'sash_l' in bones, "sash_l bone missing"
    assert 'halo' in bones, "halo bone missing"

    # Verify no NaN or None in elements
    for e in d['elements']:
        for k in ['from', 'to']:
            assert all(x is not None and not isinstance(x, str) for x in e[k]), f"Bad coord in {e.get('name')}: {e[k]}"

    print(f"  [OK] Passed all sanity checks!")
