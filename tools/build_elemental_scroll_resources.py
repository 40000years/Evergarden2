"""Update only the 58 scroll textures in the existing Java and Bedrock packs.

Preserves every other pack entry byte-for-byte (apart from Bedrock's cache
version), retaining the existing item models, atlas and Geyser mappings.
"""
from __future__ import annotations

import ast
import hashlib
import io
import json
from pathlib import Path
import zipfile

from PIL import Image
from elemental_spell_assets import ART, _texture, catalog

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / 'advance-magic/dist'


def read(path):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError(f'Duplicate ZIP entries in {path}')
        return {name: archive.read(name) for name in names}


def write(path, values):
    temporary = path.with_name(path.name + '.scrolls.tmp')
    with zipfile.ZipFile(path) as old, zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as new:
        for info in old.infolist():
            new.writestr(info, values[info.filename], compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    actual = read(temporary)
    if actual != values:
        raise ValueError(f'Pack round trip changed entries: {path}')
    temporary.replace(path)


def main():
    rows = catalog(ROOT / 'advance-magic')
    textures = {}
    for spell in rows:
        _texture(spell)  # Enforce the production dimensions, mode and alpha contract.
        textures[spell['id']] = (ART / 'icons' / (spell['id'] + '.png')).read_bytes()
    if len({hashlib.sha256(data).digest() for data in textures.values()}) != 58:
        raise ValueError('The 58 spells must have distinct textures')
    tree = ast.parse((ROOT / 'advance-magic/tools/build_packs.py').read_text(encoding='utf8'))
    version = next(ast.literal_eval(node.value) for node in tree.body if isinstance(node, ast.Assign)
                   and any(isinstance(target, ast.Name) and target.id == 'BEDROCK_PACK_VERSION' for target in node.targets))
    mappings_path = DIST / 'geyser-mappings.json'
    mappings_before = mappings_path.read_bytes()
    paper = json.loads(mappings_before)['items']['minecraft:paper']
    report = {'scrolls': len(rows), 'texture_size': [128, 128], 'bedrock_version': version, 'packs': {}}
    pending = []
    for side, filename, prefix in (
        ('java', 'advance-magic-java.zip', 'assets/advance_magic/textures/item/'),
        ('bedrock', 'advance-magic-bedrock.mcpack', 'textures/items/'),
    ):
        path = DIST / filename
        old = read(path)
        new = dict(old)
        changed = set()
        for spell in rows:
            name = 'scroll_' + spell['id']
            texture_path = prefix + name + '.png'
            if texture_path not in old:
                raise ValueError(f'Missing existing scroll route: {texture_path}')
            new[texture_path] = textures[spell['id']]
            changed.add(texture_path)
            if side == 'java':
                model = json.loads(old[f'assets/advance_magic/models/item/{name}.json'])
                definition = json.loads(old[f'assets/advance_magic/items/{name}.json'])
                if model['textures']['layer0'] != 'advance_magic:item/' + name or definition['model']['model'] != 'advance_magic:item/' + name:
                    raise ValueError(f'Unexpected Java model route: {name}')
            else:
                atlas = json.loads(old['textures/item_texture.json'])['texture_data']
                if atlas['advance_magic.' + name]['textures'] != 'textures/items/' + name:
                    raise ValueError(f'Unexpected Bedrock texture route: {name}')
                mapped = [row for row in paper if row.get('model') == 'advance_magic:' + name]
                if len(mapped) != 1 or mapped[0]['bedrock_options']['icon'] != 'advance_magic.' + name:
                    raise ValueError(f'Unexpected Geyser mapping: {name}')
        if side == 'bedrock':
            manifest = json.loads(old['manifest.json'])
            if version < manifest['header']['version']:
                raise ValueError('Bedrock resource version cannot decrease')
            manifest['header']['version'] = version
            for module in manifest['modules']:
                module['version'] = version
            new['manifest.json'] = json.dumps(manifest, ensure_ascii=False, separators=(',', ':')).encode('utf8')
            changed.add('manifest.json')
        untouched = set(old) - changed
        if set(old) != set(new) or any(old[name] != new[name] for name in untouched):
            raise ValueError(f'Unrelated resources changed in {filename}')
        report['packs'][side] = {'file': filename, 'scroll_textures': 58,
            'unchanged_other_entries': len(untouched), 'changed_entries': sorted(name for name in changed if old[name] != new[name])}
        pending.append((path, new))
    # Complete all route and data preflights before replacing either pack.
    for path, values in pending:
        write(path, values)
    if mappings_path.read_bytes() != mappings_before:
        raise ValueError('Geyser mappings changed unexpectedly')
    java, bedrock = (read(DIST / name) for name in ('advance-magic-java.zip', 'advance-magic-bedrock.mcpack'))
    for spell in rows:
        name = 'scroll_' + spell['id'] + '.png'
        a, b = java['assets/advance_magic/textures/item/' + name], bedrock['textures/items/' + name]
        if a != b or a != textures[spell['id']]:
            raise ValueError(f'Client textures differ: {name}')
        with Image.open(io.BytesIO(a)) as icon:
            bounds = icon.getchannel('A').getbbox()
            if bounds is None or min(bounds[:2]) < 3 or max(bounds[2:]) > 125:
                raise ValueError(f'Artwork touches the item edge: {name}')
    report['geyser_mappings_unchanged'] = True
    hashes = {name: hashlib.sha1((DIST / name).read_bytes()).hexdigest()
              for name in ('advance-magic-java.zip', 'advance-magic-bedrock.mcpack')}
    (DIST / 'pack-hashes.json').write_text(json.dumps(hashes, indent=2) + '\n', encoding='utf8')
    report['sha1'] = hashes
    output = ROOT / 'output/elemental-scroll-redesign'
    output.mkdir(parents=True, exist_ok=True)
    (output / 'validation.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(f'Validated all 58 distinct 128px scrolls, matching Java/Bedrock bytes and existing routes.')
    print('Unchanged other entries: ' + ', '.join(f'{side}={data["unchanged_other_entries"]}' for side, data in report['packs'].items()))
    print('Geyser mappings unchanged. Bedrock version: ' + '.'.join(map(str, version)))
    print(json.dumps(hashes, indent=2))


if __name__ == '__main__':
    main()
