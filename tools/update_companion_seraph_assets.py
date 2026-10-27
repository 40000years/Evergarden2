"""Merge the Core of Legend, the pet egg and client-timed Bedrock pet clips into the shipped packs.

Patches evergarden/dist in place, like update_boss_orbs.py: unrelated entries stay
byte-identical, the Bedrock content version becomes 3.42.0 so clients re-download.
"""
from pathlib import Path
import hashlib, json, sys, zipfile
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'evergarden/tools'))
from boss_orb_assets import register
from pet_bedrock_assets import assets as pet_assets

VERSION = [3, 42, 0]


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf8')


def main():
    dist = ROOT / 'evergarden/dist'
    work = ROOT / 'output/companion-seraph-pack-staging-20261022'
    java, bedrock = work / 'java', work / 'bedrock'
    for folder in (java, bedrock):
        folder.mkdir(parents=True, exist_ok=True)
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
    ours = {case['when'] for case in selectors['heart_of_the_sea']}
    cases[:] = [case for case in cases if case['when'] not in ours]
    cases.extend(selectors['heart_of_the_sea'])
    write_json(java / route, selector)
    write_json(bedrock / 'textures/item_texture.json', atlas)
    manifest = json.loads(packs['evergarden-bedrock.mcpack']['manifest.json'])
    manifest['header']['version'] = VERSION
    for module in manifest['modules']:
        module['version'] = VERSION
    write_json(bedrock / 'manifest.json', manifest)
    files = packs['evergarden-java.zip']
    files.update({p.relative_to(java).as_posix(): p.read_bytes() for p in java.rglob('*') if p.is_file()})
    pets = pet_assets()
    files = packs['evergarden-bedrock.mcpack']
    files = {n: d for n, d in files.items() if not ('everpet_' in n and n not in pets)}
    files.update(pets)
    files.update({p.relative_to(bedrock).as_posix(): p.read_bytes() for p in bedrock.rglob('*') if p.is_file()})
    packs['evergarden-bedrock.mcpack'] = files
    for name, content in packs.items():
        with zipfile.ZipFile(dist / name, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            for entry, data in sorted(content.items()):
                info = zipfile.ZipInfo(entry, (2026, 10, 22, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                z.writestr(info, data)
    write_json(dist / 'geyser-mappings.json', mappings)
    hashes = json.loads((dist / 'pack-hashes.json').read_text())
    for name in packs:
        hashes[name] = hashlib.sha1((dist / name).read_bytes()).hexdigest()
    (dist / 'pack-hashes.json').write_text(json.dumps(hashes, indent=2) + '\n')
    animations = sum(1 for n in pets if n.startswith('animations/'))
    timed = sum(1 for n in pets if n.startswith('animations/') and b'clip_time' in pets[n])
    print(f'Core of Legend + pet egg registered; {animations} pet animation files, {timed} still server-timed;'
          f' Bedrock content {".".join(map(str, VERSION))}')


if __name__ == '__main__':
    main()
