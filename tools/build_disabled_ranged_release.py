"""Remove Thornwhisper/Runelock pack assets and patch their runtime disable guards."""
from __future__ import annotations

import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
WEAPONS = ('thornwhisper', 'runelock')
PACK_NAMES = {
    'resource-packs/evergarden-java.zip': 'evergarden-java.zip',
    'resource-packs/evergarden-bedrock.mcpack': 'evergarden-bedrock.mcpack',
    'resource-packs/geyser-mappings.json': 'geyser-mappings.json',
    'resource-packs/pack-hashes.json': 'pack-hashes.json',
}
RUNTIME_CLASSES = (
    'com/example/voidscape/item/AdventurerWeaponService.class',
    'com/example/voidscape/item/AdventurerWeaponService$AdventurerWeapon.class',
)


def weapon_path(name: str) -> bool:
    return any(weapon in name.lower() for weapon in WEAPONS)


def read_zip(data: bytes) -> dict[str, bytes]:
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        return {name: archive.read(name) for name in archive.namelist() if not name.endswith('/')}


def write_zip(path: Path, entries: dict[str, bytes]) -> None:
    temporary = path.with_name(path.name + '.next')
    with zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 22, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
    temporary.replace(path)


def remove_weapon_json_families(entries: dict[str, bytes], path: str, field: str) -> None:
    document = json.loads(entries[path])
    values = document[field]
    document[field] = {key: value for key, value in values.items() if not weapon_path(key)}
    entries[path] = json.dumps(document, ensure_ascii=False, separators=(',', ':')).encode('utf8')


def remove_from_pack(data: bytes, *, platform: str, bedrock_version: list[int] | None = None) -> tuple[bytes, list[str]]:
    before = read_zip(data)
    after = dict(before)
    removed = [name for name in after if weapon_path(name)]
    for name in removed:
        del after[name]
    if platform == 'bedrock':
        remove_weapon_json_families(after, 'animations/adventurer_weapons.animation.json', 'animations')
        remove_weapon_json_families(after, 'render_controllers/adventurer_weapons.render.json', 'render_controllers')
        texture_file = 'textures/item_texture.json'
        atlas = json.loads(after[texture_file])
        texture_data = atlas['texture_data']
        atlas['texture_data'] = {key: value for key, value in texture_data.items() if not weapon_path(key)}
        after[texture_file] = json.dumps(atlas, ensure_ascii=False, separators=(',', ':')).encode('utf8')
        manifest = json.loads(after['manifest.json'])
        manifest['header']['version'] = bedrock_version
        for module in manifest['modules']:
            module['version'] = bedrock_version
        after['manifest.json'] = json.dumps(manifest, ensure_ascii=False, separators=(',', ':')).encode('utf8')
    assert removed or not any(weapon_path(name) for name in before), f'expected target assets in {platform} pack'
    for name, contents in after.items():
        if name.endswith(('.json', '.mcmeta', '.lang', '.txt')):
            assert not any(weapon.encode() in contents.lower() for weapon in WEAPONS), (platform, name, 'stale reference')
    changed = [name for name in sorted(set(before) | set(after)) if before.get(name) != after.get(name)]
    allowed = set(removed)
    if platform == 'bedrock':
        allowed |= {
            'animations/adventurer_weapons.animation.json',
            'render_controllers/adventurer_weapons.render.json',
            'textures/item_texture.json',
            'manifest.json',
        }
    assert set(removed) <= set(changed) and set(changed) <= allowed, (platform, changed)
    return encode_zip(after), changed


def encode_zip(entries: dict[str, bytes]) -> bytes:
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 22, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
    return stream.getvalue()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, default=ROOT / 'dist/evergarden.jar')
    parser.add_argument('--runtime-classes', type=Path, required=True)
    parser.add_argument('--output-dir', type=Path, default=ROOT / 'output/disabled-ranged-release-20261022')
    parser.add_argument('--plugin-version', default='3.0.0-e2.119-temporarily-disabled-ranged')
    parser.add_argument('--bedrock-version', default='3.48.0')
    args = parser.parse_args()

    jar_path = args.jar.resolve()
    output_dir = args.output_dir.resolve()
    runtime_classes = args.runtime_classes.resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    assert jar_path.is_file() and runtime_classes.is_dir()
    for relative in RUNTIME_CLASSES:
        assert (runtime_classes / relative).is_file(), f'missing compiled class: {relative}'

    version = [int(part) for part in args.bedrock_version.split('.')]
    assert len(version) == 3
    original_jar = jar_path.read_bytes()
    original_entries = read_zip(original_jar)
    snapshot = output_dir / 'before-evergarden.jar'
    if not snapshot.exists():
        snapshot.write_bytes(original_jar)

    revised_packs: dict[str, bytes] = {}
    change_manifest: dict[str, list[str]] = {}
    for jar_entry, pack_name in list(PACK_NAMES.items())[:2]:
        platform = 'java' if pack_name.endswith('.zip') else 'bedrock'
        pack, changed = remove_from_pack(
            original_entries[jar_entry], platform=platform,
            bedrock_version=version if platform == 'bedrock' else None,
        )
        revised_packs[jar_entry] = pack
        change_manifest[pack_name] = changed
        destination = ROOT / 'evergarden/dist' / pack_name
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(pack)

    mapping = json.loads(original_entries['resource-packs/geyser-mappings.json'])
    for base, definitions in mapping['items'].items():
        mapping['items'][base] = [
            entry for entry in definitions
            if not weapon_path(entry.get('model', '')) and not weapon_path(entry.get('bedrock_identifier', ''))
        ]
    mapping_bytes = (json.dumps(mapping, ensure_ascii=False, indent=2) + '\n').encode('utf8')
    assert not any(weapon.encode() in mapping_bytes.lower() for weapon in WEAPONS)
    revised_packs['resource-packs/geyser-mappings.json'] = mapping_bytes
    mapping_path = ROOT / 'evergarden/dist/geyser-mappings.json'
    mapping_path.write_bytes(mapping_bytes)

    hashes = json.loads(original_entries['resource-packs/pack-hashes.json'])
    for name in ('evergarden-java.zip', 'evergarden-bedrock.mcpack'):
        hashes[name] = hashlib.sha1(revised_packs['resource-packs/' + name]).hexdigest()
    hashes_bytes = (json.dumps(hashes, indent=2) + '\n').encode('utf8')
    revised_packs['resource-packs/pack-hashes.json'] = hashes_bytes
    (ROOT / 'evergarden/dist/pack-hashes.json').write_bytes(hashes_bytes)

    # Use the live release JAR as the basis, so current unrelated classes and
    # Arkhaia assets stay byte-for-byte intact.
    for name, data in revised_packs.items():
        original_entries[name] = data
    plugin_yml = original_entries['plugin.yml'].decode('utf8')
    updated, count = re.subn(r"(?m)^version:\s*['\"][^'\"]+['\"]\s*$",
                             f"version: '{args.plugin_version}'", plugin_yml, count=1)
    assert count == 1
    original_entries['plugin.yml'] = updated.encode('utf8')
    for relative in RUNTIME_CLASSES:
        original_entries[relative] = (runtime_classes / relative).read_bytes()

    release_jar = output_dir / 'evergarden.jar'
    write_zip(release_jar, original_entries)
    # Validate the patched JAR contains exactly the expected release changes.
    result_entries = read_zip(release_jar.read_bytes())
    changed = sorted(name for name in original_entries if result_entries[name] != original_entries[name])
    assert not changed, changed
    allowed = set(PACK_NAMES) | {'plugin.yml', *RUNTIME_CLASSES}
    with zipfile.ZipFile(io.BytesIO(original_jar)) as old, zipfile.ZipFile(release_jar) as new:
        actual_changes = sorted(name for name in set(old.namelist()) | set(new.namelist())
                                if (old.read(name) if name in old.namelist() else None)
                                != (new.read(name) if name in new.namelist() else None))
    assert set(actual_changes) <= allowed, actual_changes
    shutil.copy2(release_jar, jar_path)
    (output_dir / 'release-validation.json').write_text(json.dumps({
        'source': str(jar_path),
        'artifact': str(release_jar),
        'changed_entries': actual_changes,
        'class_entries_changed': list(RUNTIME_CLASSES),
        'pack_changes': change_manifest,
        'java_sha1': hashes['evergarden-java.zip'],
        'bedrock_sha1': hashes['evergarden-bedrock.mcpack'],
        'bedrock_version': version,
        'plugin_version': args.plugin_version,
    }, indent=2) + '\n', encoding='utf8')
    print(f'PASS disabled ranged release: plugin {args.plugin_version}, Bedrock pack {version}')
    print(f'Changed Java entries: {len(change_manifest["evergarden-java.zip"])}; '
          f'Bedrock entries: {len(change_manifest["evergarden-bedrock.mcpack"])}')
    print('Patched only the weapon runtime classes and pack resources; preserved Arkhaia and other JAR entries.')


if __name__ == '__main__':
    main()
