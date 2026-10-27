"""Record the exact tested release and review artifacts; fail on mismatched payloads."""
import hashlib,json,zipfile
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]
BUILD=ROOT/'output/overworld-sanctuaries-v5-build-20261015'
SERVER=ROOT/'output/overworld-sanctuaries-v5-verified-20261015'
UPGRADE=ROOT/'output/overworld-sanctuaries-v5-v4-verified-20261015'
PREVIEWS=ROOT/'previews/overworld-sanctuaries-v5-final'
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def record(path):return {'path':path.relative_to(ROOT).as_posix(),'sha256':digest(path),'bytes':path.stat().st_size}
def main():
    jar=BUILD/'evergarden.jar'
    assert jar.read_bytes()==(ROOT/'dist/evergarden.jar').read_bytes()==(SERVER/'plugins/evergarden.jar').read_bytes()==(UPGRADE/'plugins/evergarden.jar').read_bytes()
    reports=[SERVER/f'sanctuary-result-{i}.txt' for i in (1,2)]
    assert all(p.read_text().startswith('PASS\n') for p in reports)
    reports+=[UPGRADE/f'upgrade-result-{i}.txt' for i in (1,2,3)]
    reports+=[ROOT/f'output/overworld-sanctuaries-v5-v{v}-verified-20261015/upgrade-result-{i}.txt' for v in (1,2,3) for i in (1,2,3)]
    assert all(p.read_text().startswith('PASS\n') for p in reports)
    asset_root=ROOT/'evergarden/src/main/resources/overworld-sanctuaries'
    assets=sorted(asset_root.rglob('*.ows'))
    with zipfile.ZipFile(jar) as archive:
        for p in assets:assert archive.read('overworld-sanctuaries/'+p.relative_to(asset_root).as_posix())==p.read_bytes()
    images=sorted(PREVIEWS.glob('*.png'))
    for path in images:
        with Image.open(path) as im:im.verify()
    geometry=json.loads((PREVIEWS/'manifest.json').read_text())
    manifest={'version':'3.0.0-e2.79-sanctuary-structural-polish','client_date':'2026-10-15',
        'jar':record(jar),'previous_jar':record(BUILD/'previous-evergarden.jar'),
        'runtime':'Paper 26.3 build 152; Java 25','reports':[record(p) for p in reports],
        'geometry_validation':record(PREVIEWS/'geometry-validation.json'),
        'structural_validation':record(PREVIEWS/'structural-validation.json'),
        'material_audit':record(PREVIEWS/'material-audit.json'),
        'total_authored_blocks':sum(info['blocks'] for info in geometry.values()),'destinations':6,'hidden_chests':48,
        'occupied_spaces':sum(len(info['design']['occupied_rooms']) for info in geometry.values()),
        'rare_accent_blocks':{name:info['design']['rare_materials'] for name,info in geometry.items()},
        'loot':'Vanilla End City; 15% per eligible gear item, one enchant raised to vanilla cap + 1',
        'teleport_search':'128 cells by default; optional 24-256; cancellable; same production placement rules',
        'assets':[record(p) for p in assets],'images':[record(p) for p in images],
        'gallery':record(PREVIEWS/'index.html'),
        'scope':'Overworld architecture, interiors, treasure and arena space; no boss entities/combat',
        'visual_limit':'Actual block renders with simulated lighting; similarity is not quantified.'}
    (BUILD/'release-manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf8')
    print('PASS: exact tested JAR delivered; v1-v5 assets, 48 caches, fourteen Paper boots, connected assemblies and rare-material budgets recorded.')
if __name__=='__main__':main()
