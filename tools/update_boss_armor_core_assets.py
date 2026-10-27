"""Export the two authored core icons and merge only their item definitions into both packs."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED, ZipInfo
import hashlib
import json
import shutil
import sys
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'evergarden/art/boss-armor-cores'
DIST = ROOT / 'evergarden/dist'
WORK = ROOT / 'output/boss-core-textures-assets-20261023'
sys.path.insert(0, str(ROOT / 'evergarden/tools'))
from boss_orb_assets import item

CORES = {'seraph': ('nether_star', 'Core of Seraph'), 'revenant': ('echo_shard', 'Core of Revenant')}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf8')


def main():
    before = WORK / 'before'
    before.mkdir(parents=True, exist_ok=True)
    for name in ('evergarden-java.zip', 'evergarden-bedrock.mcpack', 'geyser-mappings.json', 'pack-hashes.json'):
        if not (before / name).exists():
            shutil.copy2(DIST / name, before / name)
    java, bedrock = WORK / 'java', WORK / 'bedrock'
    packs = {}
    for name in ('evergarden-java.zip', 'evergarden-bedrock.mcpack'):
        with ZipFile(DIST / name) as archive:
            packs[name] = {n: archive.read(n) for n in archive.namelist() if not n.endswith('/')}
    atlas = json.loads(packs['evergarden-bedrock.mcpack']['textures/item_texture.json'])
    mappings = json.loads((DIST / 'geyser-mappings.json').read_text(encoding='utf8'))
    selectors = {}
    for core, (base, title) in CORES.items():
        name = 'core_of_' + core
        # Mechanical game export only: preserve the authored RGBA image, add clear margins, resize.
        with Image.open(ART / 'originals' / (core + '.png')) as source:
            source = source.convert('RGBA')
            assert source.getextrema()[3][0] == 0, 'Original must have real alpha'
            source.thumbnail((224, 224), Image.Resampling.LANCZOS)
            texture = Image.new('RGBA', (256, 256))
            texture.paste(source, ((256-source.width)//2, (256-source.height)//2))
        target = ART / 'textures' / (name + '.png')
        target.parent.mkdir(parents=True, exist_ok=True)
        texture.save(target)
        item(java, bedrock, atlas['texture_data'], mappings, selectors, write_json,
             name, target.read_bytes(), base, title, True)
    for base, cases in selectors.items():
        route = 'assets/minecraft/items/' + base + '.json'
        selector = json.loads(packs['evergarden-java.zip'][route]) if route in packs['evergarden-java.zip'] else {
            'model': {'type': 'minecraft:select', 'property': 'minecraft:custom_model_data', 'index': 0,
                      'cases': [], 'fallback': {'type': 'minecraft:model', 'model': 'minecraft:item/' + base}}}
        assert selector['model']['type'] == 'minecraft:select'
        ours = {case['when'] for case in cases}
        selector['model']['cases'] = [case for case in selector['model']['cases'] if case['when'] not in ours] + cases
        write_json(java / route, selector)
    write_json(bedrock / 'textures/item_texture.json', atlas)
    manifest = json.loads(packs['evergarden-bedrock.mcpack']['manifest.json'])
    version = max(manifest['header']['version'], [3, 52, 0])
    manifest['header']['version'] = version
    for module in manifest['modules']:
        module['version'] = version
    write_json(bedrock / 'manifest.json', manifest)
    for name, folder in (('evergarden-java.zip', java), ('evergarden-bedrock.mcpack', bedrock)):
        content = packs[name]
        content.update({p.relative_to(folder).as_posix(): p.read_bytes() for p in folder.rglob('*') if p.is_file()})
        with ZipFile(DIST / name, 'w', ZIP_DEFLATED) as archive:
            for entry, data in sorted(content.items()):
                info = ZipInfo(entry, (2026, 10, 23, 0, 0, 0))
                info.compress_type = ZIP_DEFLATED
                archive.writestr(info, data)
    write_json(DIST / 'geyser-mappings.json', mappings)
    hashes = json.loads((DIST / 'pack-hashes.json').read_text(encoding='utf8'))
    for name in packs:
        hashes[name] = hashlib.sha1((DIST / name).read_bytes()).hexdigest()
    (DIST / 'pack-hashes.json').write_text(json.dumps(hashes, indent=2) + '\n', encoding='utf8')
    cards = ''.join(f'<article><h2>{title}</h2><div class="large"><img src="textures/core_of_{core}.png"></div>'
                    f'<p>32 px inventory size</p><img width="32" height="32" src="textures/core_of_{core}.png"></article>'
                    for core, (_, title) in CORES.items())
    (ART / 'preview.html').write_text('<!doctype html><meta charset="utf-8"><title>Boss Armor Cores</title>'
        '<style>body{background:#11141c;color:#eee;font:16px system-ui;padding:32px}main{display:flex;gap:32px}'
        'article{background:#242936;padding:24px;border-radius:16px;text-align:center}.large img{width:256px;height:256px}</style>'
        '<h1>Boss armor cores</h1><main>' + cards + '</main>', encoding='utf8')
    print('Two transparent 256px textures registered for Java and Bedrock ' + '.'.join(map(str, version)))


if __name__ == '__main__':
    main()
