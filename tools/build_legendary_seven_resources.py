"""Register active Fire/Wind art and reused boss hands; preserve unrelated bytes.

Use clean_legendary_resources.py to remove shelved historical draft entries.
"""
from __future__ import annotations

import hashlib
import json
import shutil
import tempfile
import zipfile
from pathlib import Path

from legendary_seven_assets import (BEDROCK_VERSION, HANDS, TEXTURES, ROOT, register,
                                    source_texture, write_json)

DIST = ROOT / 'advance-magic/dist'
OUT = ROOT / 'output/legendary-seven-pack-20261024'
PACKS = {'java': 'advance-magic-java.zip', 'bedrock': 'advance-magic-bedrock.mcpack'}


def encode(value):
    return (json.dumps(value, ensure_ascii=False, separators=(',', ':')) + '\n').encode('utf8')


def read_zip(path):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or archive.testzip() is not None:
            raise ValueError(f'Invalid source archive: {path}')
        return {name: archive.read(name) for name in names}


def stage_zip(path, old, new):
    temporary = path.with_name(path.name + '.legendary-seven.tmp')
    with zipfile.ZipFile(path) as previous, zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED) as archive:
        for info in previous.infolist():
            archive.writestr(info, new[info.filename])
        for name in sorted(set(new) - set(old)):
            archive.writestr(name, new[name])
    if read_zip(temporary) != new:
        raise ValueError(f'Incomplete staged pack: {path}')
    return temporary


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    old = {side: read_zip(DIST / name) for side, name in PACKS.items()}
    mapping_path = DIST / 'geyser-mappings.json'
    mapping_bytes = mapping_path.read_bytes()
    mappings = json.loads(mapping_bytes)
    inventory = json.loads(old['bedrock']['textures/item_texture.json'])
    atlas = inventory['texture_data']
    with tempfile.TemporaryDirectory(prefix='legendary-seven-', dir=OUT) as temporary:
        java, bedrock = Path(temporary) / 'java', Path(temporary) / 'bedrock'
        controller = 'render_controllers/judge.json'
        if controller in old['bedrock']:
            (bedrock / 'render_controllers').mkdir(parents=True, exist_ok=True)
            (bedrock / controller).write_bytes(old['bedrock'][controller])
        register(java, bedrock, atlas, mappings['items'])
        generated = {side: {path.relative_to(folder).as_posix(): path.read_bytes()
                            for path in folder.rglob('*') if path.is_file()}
                     for side, folder in (('java', java), ('bedrock', bedrock))}
    generated['bedrock']['textures/item_texture.json'] = encode(inventory)
    manifest = json.loads(old['bedrock']['manifest.json'])
    original_uuid = manifest['header']['uuid']
    versions = [manifest['header']['version']] + [module['version'] for module in manifest['modules']]
    if any(version > BEDROCK_VERSION for version in versions):
        raise ValueError('Refusing to lower the current Bedrock pack version')
    manifest['header']['version'] = BEDROCK_VERSION
    for module in manifest['modules']:
        module['version'] = BEDROCK_VERSION
    generated['bedrock']['manifest.json'] = encode(manifest)
    report = {'bedrock_version': BEDROCK_VERSION, 'preserved_bedrock_uuid': original_uuid,
              'textures': {}, 'packs': {}, 'reused_hand_models': list(HANDS)}
    for name in TEXTURES:
        path, width, height = source_texture(name)
        data = path.read_bytes()
        if generated['java'][f'assets/advance_magic/textures/effect/{name}.png'] != data or generated['bedrock'][f'textures/particle/{name}.png'] != data:
            raise ValueError('Native image bytes changed: ' + name)
        report['textures'][name] = {'dimensions': [width, height], 'sha256': hashlib.sha256(data).hexdigest()}
    pending = []
    try:
        for side, filename in PACKS.items():
            new = {**old[side], **generated[side]}
            untouched = set(old[side]) - set(generated[side])
            if any(old[side][name] != new[name] for name in untouched):
                raise ValueError('Unrelated resources changed')
            scroll_prefix = 'assets/advance_magic/textures/item/scroll_' if side == 'java' else 'textures/items/scroll_'
            scrolls = [name for name in old[side] if name.startswith(scroll_prefix) and name.endswith('.png')]
            if len(scrolls) != 58 or any(old[side][name] != new[name] for name in scrolls):
                raise ValueError('The 58 scroll textures must be preserved')
            light = [name for name in old[side] if 'elemental_legendary_light_' in name]
            if any(old[side][name] != new[name] for name in light):
                raise ValueError('Approved Light artwork or definitions changed')
            pending.append((DIST / filename, stage_zip(DIST / filename, old[side], new)))
            report['packs'][side] = {'file': filename, 'added_entries': sorted(set(new) - set(old[side])),
                                     'replaced_entries': sorted(name for name in generated[side] if name in old[side] and old[side][name] != new[name]),
                                     'preserved_other_entries': len(untouched), 'preserved_scroll_textures': len(scrolls),
                                     'preserved_light_entries': len(light)}
        backup = OUT / 'baseline'
        backup.mkdir(exist_ok=True)
        for path in [DIST / filename for filename in PACKS.values()] + [mapping_path, DIST / 'pack-hashes.json']:
            if not (backup / path.name).exists():
                shutil.copy2(path, backup / path.name)
        for target, temporary in pending:
            temporary.replace(target)
    finally:
        for _, temporary in pending:
            temporary.unlink(missing_ok=True)
    # All old mapping rows remain byte-value-equivalent except the four selected hands.
    previous_mapping = json.loads(mapping_bytes)
    hand_ids = {f'voidscape:{name}' for name in HANDS}
    for item, rows in previous_mapping['items'].items():
        for row in rows:
            if row.get('model') not in hand_ids and row not in mappings['items'].get(item, []):
                raise ValueError('Unrelated Geyser mapping was removed')
    staged_mapping = mapping_path.with_suffix('.json.legendary-seven.tmp')
    staged_mapping.write_bytes(encode(mappings))
    staged_mapping.replace(mapping_path)
    hashes = {filename: hashlib.sha1((DIST / filename).read_bytes()).hexdigest() for filename in PACKS.values()}
    (DIST / 'pack-hashes.json').write_text(json.dumps(hashes, indent=2) + '\n', encoding='utf8')
    report['sha1'] = hashes
    report['geyser_mapping_sha256'] = hashlib.sha256(mapping_path.read_bytes()).hexdigest()
    (OUT / 'resource-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps({'textures': len(TEXTURES), 'reused_hand_models': len(HANDS), 'bedrock_version': BEDROCK_VERSION,
                      'sha1': hashes, 'report': str(OUT / 'resource-build.json')}, indent=2))


if __name__ == '__main__':
    main()
