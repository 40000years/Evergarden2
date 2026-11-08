"""Create a scoped Bedrock pet-menu pack from the current shipped Evergarden JAR.

Outputs are staged under output/pets-bedrock-menu-assets; this does not deploy or
overwrite the release JAR. The packaging step can include these exact three files.
"""
import hashlib
import io
import json
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'evergarden/tools'))
from pet_menu_bedrock_items import register

OUT = ROOT / 'output/pets-bedrock-menu-assets'


def encode(value):
    return (json.dumps(value, ensure_ascii=False, separators=(',', ':')) + '\n').encode('utf8')


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(ROOT / 'dist/evergarden.jar') as jar:
        pack = jar.read('resource-packs/evergarden-bedrock.mcpack')
        mapping_bytes = jar.read('resource-packs/geyser-mappings.json')
        hashes = json.loads(jar.read('resource-packs/pack-hashes.json'))
        design = json.loads(jar.read('judge-design.json'))
        portraits = jar.read('pet-portraits.zip')
    with zipfile.ZipFile(io.BytesIO(pack)) as source:
        old = {name: source.read(name) for name in source.namelist() if not name.endswith('/')}
    files = dict(old)
    atlas = json.loads(files['textures/item_texture.json'])
    old_atlas = dict(atlas['texture_data'])
    mappings = json.loads(mapping_bytes)
    old_mappings = json.loads(mapping_bytes)
    count = register(files, atlas, mappings, portraits)
    assert count == 192, count
    assert all(atlas['texture_data'][key] == value for key, value in old_atlas.items())
    assert all(mappings['items'][key] == value for key, value in old_mappings['items'].items()
               if key != 'minecraft:paper')
    assert mappings['items']['minecraft:paper'][:len(old_mappings['items']['minecraft:paper'])] == old_mappings['items']['minecraft:paper']
    files['textures/item_texture.json'] = encode(atlas)
    manifest = json.loads(files['manifest.json'])
    version = list(manifest['header']['version'])
    version = [version[0], version[1] + 1, 0]
    manifest['header']['version'] = version
    for module in manifest['modules']:
        module['version'] = version
    files['manifest.json'] = encode(manifest)
    assert all(files[k] == v for k, v in old.items() if k not in {'manifest.json', 'textures/item_texture.json'})
    pack_path = OUT / 'evergarden-bedrock.mcpack'
    with zipfile.ZipFile(pack_path, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as result:
        for name, data in sorted(files.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 29, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            result.writestr(info, data)
    (OUT / 'geyser-mappings.json').write_bytes(encode(mappings))
    hashes['evergarden-bedrock.mcpack'] = hashlib.sha1(pack_path.read_bytes()).hexdigest()
    (OUT / 'pack-hashes.json').write_bytes(encode(hashes))
    design['bedrock-version'] = '.'.join(map(str, version))
    (OUT / 'judge-design.json').write_bytes(encode(design))
    print(f'PASS: {count} Java pet portraits registered as Bedrock item models; version {design["bedrock-version"]}')
    print(f'Bedrock pack SHA-1 {hashes["evergarden-bedrock.mcpack"]}; staged in {OUT}')


if __name__ == '__main__':
    main()
