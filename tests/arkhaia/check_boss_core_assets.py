"""Verify the two core textures, cross-client routes and preservation of existing pack assets."""
from pathlib import Path
from zipfile import ZipFile
from io import BytesIO
import copy
import hashlib
import json
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
DIST = ROOT / 'evergarden/dist'
WORK = ROOT / 'output/boss-core-textures-assets-20261023'
ART = ROOT / 'evergarden/art/boss-armor-cores'
checks = 0


def check(value, label):
    global checks
    assert value, label
    checks += 1


def main():
    java = ZipFile(DIST / 'evergarden-java.zip')
    bedrock = ZipFile(DIST / 'evergarden-bedrock.mcpack')
    atlas = json.loads(bedrock.read('textures/item_texture.json'))
    mapping = json.loads((DIST / 'geyser-mappings.json').read_text(encoding='utf8'))
    originals = json.loads((WORK / 'before/geyser-mappings.json').read_text(encoding='utf8'))
    ours = {'voidscape:core_of_seraph', 'voidscape:core_of_revenant'}
    filtered = copy.deepcopy(mapping)
    for base, entries in list(filtered['items'].items()):
        filtered['items'][base] = [entry for entry in entries if entry.get('model') not in ours]
        if not filtered['items'][base] and base not in originals['items']:
            del filtered['items'][base]
    check(filtered == originals, 'All existing Geyser mappings are preserved')
    for core, base in (('seraph', 'nether_star'), ('revenant', 'echo_shard')):
        name = 'core_of_' + core
        data = (ART / 'textures' / (name + '.png')).read_bytes()
        with Image.open(BytesIO(data)) as image:
            check(image.size == (256, 256) and image.mode == 'RGBA', name + ' native 256px RGBA texture')
            check(image.getextrema()[3] == (0, 255) and image.getpixel((0, 0))[3] == 0, name + ' true alpha transparency')
        check(java.read('assets/voidscape/textures/item/' + name + '.png') == data and
              bedrock.read('textures/items/' + name + '.png') == data, name + ' both clients use the authored texture')
        definition = json.loads(java.read('assets/voidscape/items/' + name + '.json'))
        model = json.loads(java.read('assets/voidscape/models/item/' + name + '.json'))
        check(definition['model']['model'] == 'voidscape:item/' + name and
              model['textures']['layer0'] == 'voidscape:item/' + name, name + ' Java model chain resolves')
        selector = json.loads(java.read('assets/minecraft/items/' + base + '.json'))['model']
        check(sum(case['when'] == 'voidscape:' + name for case in selector['cases']) == 1 and
              selector['fallback']['model'] == 'minecraft:item/' + base, name + ' fallback preserves ordinary vanilla items')
        entries = [entry for entry in mapping['items']['minecraft:' + base] if entry.get('model') == 'voidscape:' + name]
        check(len(entries) == 1 and entries[0]['bedrock_options']['icon'] == 'voidscape.' + name and
              atlas['texture_data']['voidscape.' + name]['textures'] == 'textures/items/' + name, name + ' Geyser icon resolves to Bedrock atlas')
    preserved = {}
    for name, archive, allowed in (('evergarden-java.zip', java, set()),
                                 ('evergarden-bedrock.mcpack', bedrock, {'manifest.json', 'textures/item_texture.json'})):
        with ZipFile(WORK / 'before' / name) as old:
            kept = [entry for entry in old.namelist() if not entry.endswith('/') and entry not in allowed]
            check(all(old.read(entry) == archive.read(entry) for entry in kept), name + ' existing models and textures remain byte-identical')
            preserved[name] = len(kept)
    manifest = json.loads(bedrock.read('manifest.json'))
    with ZipFile(WORK / 'before/evergarden-bedrock.mcpack') as old:
        previous = json.loads(old.read('manifest.json'))
    check(manifest['header']['version'] >= [3, 52, 0] and manifest['header']['uuid'] == previous['header']['uuid'], 'Bedrock version advances without changing pack identity')
    hashes = json.loads((DIST / 'pack-hashes.json').read_text(encoding='utf8'))
    check(all(hashlib.sha1((DIST / name).read_bytes()).hexdigest() == digest for name, digest in hashes.items()), 'Shipped pack hashes match the actual archives')
    result = {'result': 'PASS', 'checks': checks, 'preserved_entries': preserved,
              'bedrock_version': manifest['header']['version'], 'art_tool': 'built-in image_gen'}
    (WORK / 'validation.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf8')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
