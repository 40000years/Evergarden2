"""Package the exact habitat build tested on Paper, leaving the preceding release archived."""
import hashlib,json,shutil,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BUILD=ROOT/'output/overworld-sanctuaries-habitat-build-20261015'
GEOMETRY=ROOT/'output/overworld-sanctuaries-habitat-geometry-v3-20261015'
NATIVE=ROOT/'output/overworld-sanctuaries-habitat-native-v6-20261015'
def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def record(p):return {'path':p.relative_to(ROOT).as_posix(),'sha256':digest(p),'bytes':p.stat().st_size}
def main():
    jar=BUILD/'evergarden.jar'
    reports=[GEOMETRY/f'sanctuary-result-{i}.txt' for i in (1,2)]+[NATIVE/f'habitat-result-{i}.txt' for i in (1,2)]
    assert all(p.read_text(encoding='utf8').startswith('PASS\n') for p in reports)
    for server in (GEOMETRY,NATIVE):assert digest(jar)==digest(server/'plugins/evergarden.jar'),server
    assets=ROOT/'evergarden/src/main/resources/overworld-sanctuaries'
    with zipfile.ZipFile(jar) as z:
        for p in assets.rglob('*'):
            if p.is_file():assert z.read('overworld-sanctuaries/'+p.relative_to(assets).as_posix())==p.read_bytes(),p
        assert not any(b'Unresolved compilation problem' in z.read(n) for n in z.namelist() if n.endswith('.class'))
        assert b'3.0.0-e2.80-sanctuary-habitat' in z.read('plugin.yml')
    backup=BUILD/'previous-evergarden.jar'
    if not backup.exists():shutil.copy2(ROOT/'dist/evergarden.jar',backup)
    shutil.copy2(jar,ROOT/'dist/evergarden.jar')
    manifest={'version':'3.0.0-e2.80-sanctuary-habitat','jar':record(jar),'previous_jar':record(backup),
        'reports':[record(p) for p in reports],'runtime':'Paper 26.3 build 152, Java 25, LevelledMobs 4.5.3.2 b159',
        'architecture':'Revision 5 unchanged; six sites, eight caches and three armored guard spawners each',
        'guards':record(assets/'guards.yml'),'documentation':record(ROOT/'docs/overworld-sanctuaries-habitat.md'),
        'loot':'Armor trim rolls become real mana potions; existing eligible gear over-cap chance remains 15%; edited old caches preserved',
        'spawn_scope':'Exact authored column silhouette and structure height; natural hostiles only; native SPAWNER events retain LevelledMobs eligibility'}
    (BUILD/'release-manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf8')
    print('PASS: exact tested habitat JAR delivered; four Paper boots and all 18 guard posts recorded.')
if __name__=='__main__':main()
