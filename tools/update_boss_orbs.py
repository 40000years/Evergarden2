"""Export generated orb art and merge new items without rebuilding unrelated boss assets."""
from pathlib import Path
import hashlib, json, sys, zipfile
from PIL import Image
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'evergarden/tools'))
from boss_orb_assets import IDS, TITLES, register

def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf8')

def main():
    art = ROOT / 'evergarden/art/boss-orbs'
    (art / 'textures').mkdir(exist_ok=True)
    for boss in IDS:
        # Mechanical game-texture export only; keep the authored art and alpha unchanged.
        with Image.open(art / 'originals' / (boss + '.png')) as image:
            image.convert('RGBA').resize((256, 256), Image.Resampling.LANCZOS).save(art / 'textures' / (boss + '.png'))
    dist = ROOT / 'evergarden/dist'
    work = ROOT / 'output/boss-orb-pack-staging-20261021'
    java, bedrock = work / 'java', work / 'bedrock'
    java.mkdir(parents=True, exist_ok=True); bedrock.mkdir(parents=True, exist_ok=True)
    packs = {}
    for name in ('evergarden-java.zip', 'evergarden-bedrock.mcpack'):
        with zipfile.ZipFile(dist / name) as z:
            packs[name] = {n: z.read(n) for n in z.namelist() if not n.endswith('/')}
    atlas = json.loads(packs['evergarden-bedrock.mcpack']['textures/item_texture.json'])
    mappings = json.loads((dist / 'geyser-mappings.json').read_text(encoding='utf8'))
    selectors = {}
    register(java, bedrock, atlas['texture_data'], mappings, selectors, write_json)
    route = 'assets/minecraft/items/heart_of_the_sea.json'
    selector = json.loads(packs['evergarden-java.zip'][route])
    cases = selector['model']['cases']
    cases[:] = [case for case in cases if not str(case['when']).startswith('voidscape:boss_orb_')]
    cases.extend(selectors['heart_of_the_sea'])
    write_json(java / route, selector)
    write_json(bedrock / 'textures/item_texture.json', atlas)
    manifest = json.loads(packs['evergarden-bedrock.mcpack']['manifest.json'])
    manifest['header']['version'] = max(manifest['header']['version'], [3, 38, 0])
    for module in manifest['modules']: module['version'] = manifest['header']['version']
    write_json(bedrock / 'manifest.json', manifest)
    for name, base in (('evergarden-java.zip', java), ('evergarden-bedrock.mcpack', bedrock)):
        files = packs[name]
        files.update({p.relative_to(base).as_posix(): p.read_bytes() for p in base.rglob('*') if p.is_file()})
        with zipfile.ZipFile(dist / name, 'w', zipfile.ZIP_DEFLATED) as z:
            for n, data in files.items(): z.writestr(n, data)
    write_json(dist / 'geyser-mappings.json', mappings)
    hashes = json.loads((dist / 'pack-hashes.json').read_text())
    for name in packs: hashes[name] = hashlib.sha1((dist / name).read_bytes()).hexdigest()
    (dist / 'pack-hashes.json').write_text(json.dumps(hashes, indent=2) + '\n')
    cards = ''.join(f'<article><img src="textures/{boss}.png"><h2>{title}</h2></article>' for boss, title in zip(IDS, TITLES))
    (art / 'preview.html').write_text('<!doctype html><meta charset="utf-8"><title>Sanctuary Soul Orbs</title><style>body{background:#10131c;color:#ece9de;font:18px system-ui;padding:30px}main{display:grid;grid-template-columns:repeat(3,1fr);gap:24px}article{background:#1d2231;text-align:center;border-radius:20px;padding:24px}img{width:200px;height:200px}h2{font-size:20px}</style><h1>Sanctuary Soul Orbs</h1><main>'+cards+'</main>', encoding='utf8')
    print('Six transparent 256px orb textures; Java + Bedrock 3.38.0; direct item-model mappings')

if __name__ == '__main__': main()
