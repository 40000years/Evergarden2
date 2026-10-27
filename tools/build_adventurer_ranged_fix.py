"""Regenerate or remove Bedrock ranged models while retaining unrelated pack entries."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NAMES = ('thornwhisper', 'runelock')


def target(name):
    return any(weapon in name for weapon in NAMES)


def encode(value):
    return json.dumps(value, ensure_ascii=False, separators=(',', ':')).encode('utf8')


def read_pack(backup, name):
    import io
    with zipfile.ZipFile(io.BytesIO(backup.read('evergarden/dist/' + name))) as archive:
        return {name: archive.read(name) for name in archive.namelist() if not name.endswith('/')}


def write_pack(path, entries):
    temporary = path.with_name(path.name + '.next')
    with zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 22, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
    temporary.replace(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--before', type=Path, required=True)
    parser.add_argument('--bedrock-only', action='store_true', help='Keep the approved Java ZIP exact, including archive bytes')
    parser.add_argument('--bedrock-version', default='3.48.0')
    parser.add_argument('--remove-bedrock', action='store_true', help='Fall back to native Bedrock bow/crossbow visuals')
    args = parser.parse_args()
    assert args.bedrock_only and args.remove_bedrock, 'This release preserves Java and removes the two Bedrock custom models'
    version = [int(part) for part in args.bedrock_version.split('.')]
    assert len(version) == 3
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    java, bedrock = output / 'java', output / 'bedrock'
    spec = importlib.util.spec_from_file_location('adventurer_weapon_assets', ROOT / 'evergarden/tools/adventurer_weapon_assets.py')
    generator = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(generator)
    generator.ART = output / 'art'
    generator.CATALOG = tuple(row for row in generator.CATALOG if row[0] in NAMES)
    atlas, mappings = {}, {'format_version': 2, 'items': {}}
    generator.register(java, bedrock, atlas, mappings)

    with zipfile.ZipFile(args.before) as backup:
        old_java = read_pack(backup, 'evergarden-java.zip')
        old_bedrock = read_pack(backup, 'evergarden-bedrock.mcpack')
        old_mappings = json.loads(backup.read('evergarden/dist/geyser-mappings.json'))
        hashes = json.loads(backup.read('evergarden/dist/pack-hashes.json'))
    revised_java, revised_bedrock = dict(old_java), dict(old_bedrock)
    shared = {
        'animations/adventurer_weapons.animation.json': 'animations',
        'render_controllers/adventurer_weapons.render.json': 'render_controllers',
    }
    for directory, revised in ((java, revised_java), (bedrock, revised_bedrock)):
        for file in sorted(directory.rglob('*')):
            if not file.is_file():
                continue
            name = file.relative_to(directory).as_posix()
            data = file.read_bytes()
            if directory == bedrock and name in shared:
                field = shared[name]
                previous = json.loads(revised[name])
                generated = json.loads(data)
                for key, value in generated[field].items():
                    if target(key):
                        previous[field][key] = value
                    else:
                        assert previous[field].get(key) == value, ('unrelated shared entry changed', key)
                revised[name] = encode(previous)
            elif target(name):
                revised[name] = encode(json.loads(data)) if file.suffix in ('.json', '.mcmeta') else data
            else:
                # The materials atlas is common to the full collection and must stay exact.
                assert name in revised and revised[name] == data, ('unrelated generated file changed', name)

    if args.remove_bedrock:
        for name in list(revised_bedrock):
            if target(name):
                del revised_bedrock[name]
        for name, field in shared.items():
            shared_file = json.loads(revised_bedrock[name])
            shared_file[field] = {key: value for key, value in shared_file[field].items() if not target(key)}
            revised_bedrock[name] = encode(shared_file)

    item_atlas = json.loads(revised_bedrock['textures/item_texture.json'])
    if args.remove_bedrock:
        item_atlas['texture_data'] = {
            key: value for key, value in item_atlas['texture_data'].items() if not target(key)
        }
    for key, value in atlas.items():
        assert target(key)
        item_atlas['texture_data'][key] = value
    revised_bedrock['textures/item_texture.json'] = encode(item_atlas)
    manifest = json.loads(revised_bedrock['manifest.json'])
    assert manifest['header']['version'] < version
    manifest['header']['version'] = version
    for module in manifest['modules']:
        module['version'] = version
    revised_bedrock['manifest.json'] = encode(manifest)
    if args.remove_bedrock:
        for base, entries in old_mappings['items'].items():
            old_mappings['items'][base] = [
                entry for entry in entries
                if not target(entry.get('model', '')) and not target(entry.get('bedrock_identifier', ''))
            ]
    else:
        for base, entries in mappings['items'].items():
            previous = old_mappings['items'][base]
            models = {item['model'] for item in entries}
            found = [i for i, value in enumerate(previous) if value.get('model') in models]
            assert found
            # A loaded crossbow may have additional, more specific charge predicates.
            # Replace its complete definition family while preserving other mappings.
            revised = [value for value in previous if value.get('model') not in models]
            revised[found[0]:found[0]] = entries
            old_mappings['items'][base] = revised

    changes = {}
    allowed_shared = set(shared) | {'textures/item_texture.json', 'manifest.json'}
    for name, previous, revised in (
        ('evergarden-java.zip', old_java, revised_java),
        ('evergarden-bedrock.mcpack', old_bedrock, revised_bedrock),
    ):
        if not (args.remove_bedrock and name.endswith('.mcpack')):
            assert set(previous) <= set(revised), 'No old pack entries may disappear'
        changed = [key for key in sorted(set(previous) | set(revised)) if previous.get(key) != revised.get(key)]
        assert all(target(key) or name.endswith('.mcpack') and key in allowed_shared for key in changed), changed
        changes[name] = changed
        if args.bedrock_only and name == 'evergarden-java.zip':
            assert not changed, 'The approved Java pack must remain unchanged'
            continue
        write_pack(ROOT / 'evergarden/dist' / name, revised)
        hashes[name] = hashlib.sha1((ROOT / 'evergarden/dist' / name).read_bytes()).hexdigest()
    mapping_path = ROOT / 'evergarden/dist/geyser-mappings.json'
    previous_mapping_bytes = mapping_path.read_bytes()
    if json.loads(previous_mapping_bytes) != old_mappings:
        mapping_path.write_text(json.dumps(old_mappings, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    (ROOT / 'evergarden/dist/pack-hashes.json').write_text(json.dumps(hashes, indent=2) + '\n', encoding='utf8')
    for file in generator.ART.iterdir():
        assert target(file.name)
        destination = ROOT / 'evergarden/art/adventurer-weapons' / file.name
        if args.bedrock_only:
            assert destination.read_bytes() == file.read_bytes(), 'Authored weapon art must remain unchanged'
        else:
            shutil.copy2(file, destination)
    (output / 'pack-changes.json').write_text(json.dumps(changes, indent=2) + '\n', encoding='utf8')
    action = 'Removed Thornwhisper/Runelock Bedrock models' if args.remove_bedrock else 'Updated only two ranged families'
    print(action + '; Bedrock pack ' + '.'.join(map(str, version)))
    print('Changed entries:', {key: len(value) for key, value in changes.items()})


if __name__ == '__main__':
    main()
