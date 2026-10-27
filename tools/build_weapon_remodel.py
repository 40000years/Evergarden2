"""Regenerate the legendary and rare weapon families and patch only their pack entries.

Reads the current dist packs, swaps in the regenerated weapon files, bumps each
Bedrock manifest version and rewrites pack-hashes.json. Every other pack entry
must stay byte-identical, and the Geyser mappings must not change; the script
asserts both. With --patch-jars it also replaces the embedded resource packs in
dist/advance-magic.jar and dist/evergarden.jar, copying all other JAR entries.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import re
import shutil
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
DATE = (2026, 10, 23, 0, 0, 0)

FAMILIES = {
    'evergarden': {
        'generator': ROOT / 'evergarden/tools/adventurer_weapon_assets.py',
        'dist': ROOT / 'evergarden/dist', 'jar': ROOT / 'dist/evergarden.jar',
        'java': 'evergarden-java.zip', 'bedrock': 'evergarden-bedrock.mcpack',
        'owned': ('assets/voidscape/items/adventurer_', 'assets/voidscape/models/item/adventurer_weapons/',
                  'assets/voidscape/textures/item/adventurer_weapons/', 'attachables/adventurer_weapons/',
                  'models/entity/adventurer_weapons/', 'textures/adventurer_weapons/',
                  'animations/adventurer_weapons.animation.json', 'render_controllers/adventurer_weapons.render.json'),
        'atlas': 'voidscape.adventurer_',
    },
    'advance-magic': {
        'generator': ROOT / 'tools/legendary_weapon_assets.py',
        'dist': ROOT / 'advance-magic/dist', 'jar': ROOT / 'dist/advance-magic.jar',
        'java': 'advance-magic-java.zip', 'bedrock': 'advance-magic-bedrock.mcpack',
        'owned': ('assets/advance_magic/items/legendary_', 'assets/advance_magic/items/longinus_projectile.json',
                  'assets/advance_magic/models/item/legendary_weapons/', 'assets/advance_magic/textures/item/legendary_weapons/',
                  'assets/advance_magic/models/effect/legendary_', 'assets/advance_magic/textures/effect/legendary_',
                  'attachables/legendary_weapons/', 'models/entity/legendary_weapons/', 'textures/legendary_weapons/',
                  'particles/legendary_', 'textures/particle/legendary_', 'entity/longinus_projectile.entity.json',
                  'animations/longinus_projectile.animation.json', 'animations/legendary_weapons.animation.json',
                  'render_controllers/legendary_weapons.render.json'),
        'atlas': 'advance_magic.legendary_',
    },
}


def read_zip(data: bytes) -> dict[str, bytes]:
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        return {name: archive.read(name) for name in archive.namelist() if not name.endswith('/')}


def write_zip(path: Path, entries: dict[str, bytes]) -> None:
    temporary = path.with_name(path.name + '.next')
    with zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, DATE)
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
    temporary.replace(path)


def encode(path: Path) -> bytes:
    data = path.read_bytes()
    if path.suffix in ('.json', '.mcmeta'):
        data = json.dumps(json.loads(data), ensure_ascii=False, separators=(',', ':')).encode('utf8')
    return data


def owned(family: dict, name: str) -> bool:
    return name.startswith(family['owned'])


def load(path: Path, module_name: str):
    spec = importlib.util.spec_from_file_location(module_name, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = module
    spec.loader.exec_module(module)
    return module


def generate(key: str, family: dict, work: Path, old_bedrock: dict[str, bytes]):
    java, bedrock, art = work / key / 'java', work / key / 'bedrock', work / key / 'art'
    module = load(family['generator'], 'adventurer_weapon_assets' if key == 'evergarden' else 'legendary_weapon_assets')
    real_art = module.ART
    module.ART = art
    atlas = {}
    if key == 'evergarden':
        mappings = {'format_version': 2, 'items': {}}
        module.register(java, bedrock, atlas, mappings)
        rows = [row for values in mappings['items'].values() for row in values]
    else:
        # Sigil particles are cloned from the existing solar corona definitions.
        for suffix in ('', '_flat'):
            name = f'particles/solar_corona{suffix}.particle.json'
            (bedrock / name).parent.mkdir(parents=True, exist_ok=True)
            (bedrock / name).write_bytes(old_bedrock[name])
        definitions = {}
        module.register(java, bedrock, atlas, definitions)
        for suffix in ('', '_flat'):
            (bedrock / f'particles/solar_corona{suffix}.particle.json').unlink()
        rows = [row for values in definitions.values() for row in values]
    files = {'java': {}, 'bedrock': {}}
    for side, folder in (('java', java), ('bedrock', bedrock)):
        for file in sorted(folder.rglob('*')):
            if file.is_file():
                name = file.relative_to(folder).as_posix()
                assert owned(family, name), (key, 'generator wrote outside its family', name)
                files[side][name] = encode(file)
    return files, atlas, rows, art, real_art


def patch_pack(family: dict, old: dict[str, bytes], new: dict[str, bytes]) -> tuple[dict[str, bytes], list[str], list[str]]:
    revised = {name: data for name, data in old.items() if not owned(family, name)}
    revised.update(new)
    removed = sorted(name for name in old if owned(family, name) and name not in new)
    added = sorted(name for name in new if name not in old)
    return revised, removed, added


def bump(version: list[int]) -> list[int]:
    return [version[0], version[1] + 1, 0]


def design_version(data: bytes, version: list[int]) -> bytes:
    """Only the Judge diagnostics' Bedrock version string changes (as update_river_bedrock.py does)."""
    text = data.decode('utf8')
    updated = re.sub(r'("bedrock-version":\s*)"[\d.]+"', lambda m: m.group(1) + '"' + '.'.join(map(str, version)) + '"', text, count=1)
    assert updated != text or f'"{".".join(map(str, version))}"' in text
    return updated.encode('utf8')


def patch_jar(path: Path, replacements: dict[str, bytes], plugin_version: str | None, design: list[int] | None = None) -> list[str]:
    changed = []
    temporary = path.with_name(path.name + '.next')
    with zipfile.ZipFile(path) as source, zipfile.ZipFile(temporary, 'w') as target:
        for info in source.infolist():
            data = source.read(info.filename)
            if info.filename in replacements:
                data = replacements[info.filename]
            elif info.filename == 'plugin.yml' and plugin_version:
                text = data.decode('utf8')
                data = re.sub(r"^version:.*$", f'version: {plugin_version}', text, count=1, flags=re.M).encode('utf8')
            elif info.filename == 'judge-design.json' and design:
                data = design_version(data, design)
            if data != source.read(info.filename):
                changed.append(info.filename)
                info = zipfile.ZipInfo(info.filename, DATE)
                info.compress_type = zipfile.ZIP_DEFLATED
            target.writestr(info, data)
        missing = set(replacements) - set(source.namelist())
        assert not missing, ('JAR lacks resource pack entries', missing)
    temporary.replace(path)
    return changed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True, help='New folder for the generated families and the change report')
    parser.add_argument('--patch-jars', action='store_true')
    parser.add_argument('--magic-plugin-version', help='Optional new advance-magic plugin.yml version')
    args = parser.parse_args()
    work = args.output.resolve()
    work.mkdir(parents=True, exist_ok=False)
    report = {}
    for key, family in FAMILIES.items():
        dist = family['dist']
        old_java = read_zip((dist / family['java']).read_bytes())
        old_bedrock = read_zip((dist / family['bedrock']).read_bytes())
        files, atlas, rows, art, real_art = generate(key, family, work, old_bedrock)
        java, java_removed, java_added = patch_pack(family, old_java, files['java'])
        bedrock, bedrock_removed, bedrock_added = patch_pack(family, old_bedrock, files['bedrock'])

        item_atlas = json.loads(bedrock['textures/item_texture.json'])
        keys = {name for name in item_atlas['texture_data'] if name.startswith(family['atlas'])}
        assert keys == set(atlas), (key, 'icon atlas keys must stay the same', keys ^ set(atlas))
        item_atlas['texture_data'].update(atlas)
        bedrock['textures/item_texture.json'] = json.dumps(item_atlas, ensure_ascii=False, separators=(',', ':')).encode('utf8')

        manifest = json.loads(bedrock['manifest.json'])
        version = bump(manifest['header']['version'])
        manifest['header']['version'] = version
        for module in manifest['modules']:
            module['version'] = version
        bedrock['manifest.json'] = json.dumps(manifest, ensure_ascii=False, separators=(',', ':')).encode('utf8')

        mappings = json.loads((dist / 'geyser-mappings.json').read_text(encoding='utf8'))
        current = [row for values in mappings['items'].values() for row in values
                   if row.get('bedrock_identifier', '').split(':')[-1].startswith(family['atlas'].split('.')[-1])]
        assert sorted(map(json.dumps, current)) == sorted(map(json.dumps, rows)), (key, 'Geyser mappings must not change')

        for name, old, new in ((family['java'], old_java, java), (family['bedrock'], old_bedrock, bedrock)):
            untouched = [n for n in old if not owned(family, n) and n not in ('textures/item_texture.json', 'manifest.json')]
            assert all(new[n] == old[n] for n in untouched), (key, name, 'unrelated entries changed')
            write_zip(dist / name, new)
        hashes = json.loads((dist / 'pack-hashes.json').read_text(encoding='utf8'))
        for name in (family['java'], family['bedrock']):
            hashes[name] = hashlib.sha1((dist / name).read_bytes()).hexdigest()
        (dist / 'pack-hashes.json').write_text(json.dumps(hashes, indent=2) + '\n', encoding='utf8')

        real_art.mkdir(parents=True, exist_ok=True)
        for file in sorted(art.iterdir()):
            shutil.copy2(file, real_art / file.name)
        design = ROOT / 'evergarden/src/main/resources/judge-design.json'
        if key == 'evergarden':
            design.write_bytes(design_version(design.read_bytes(), version))
        report[key] = {
            'bedrock_version': version, 'hashes': {n: hashes[n] for n in (family['java'], family['bedrock'])},
            'java_entries_written': len(files['java']), 'bedrock_entries_written': len(files['bedrock']),
            'removed': java_removed + bedrock_removed, 'added': java_added + bedrock_added,
        }
        print(f'{key}: Bedrock {".".join(map(str, version))}, {len(files["java"])} Java + {len(files["bedrock"])} Bedrock weapon entries,'
              f' removed {len(report[key]["removed"])}, added {len(report[key]["added"])}')
        if args.patch_jars:
            replacements = {f'resource-packs/{n}': (dist / n).read_bytes()
                            for n in (family['java'], family['bedrock'], 'pack-hashes.json')}
            plugin = args.magic_plugin_version if key == 'advance-magic' else None
            report[key]['jar_entries_changed'] = patch_jar(family['jar'], replacements, plugin, version if key == 'evergarden' else None)
            with zipfile.ZipFile(family['jar']) as jar:
                for name in (family['java'], family['bedrock'], 'geyser-mappings.json', 'pack-hashes.json'):
                    assert jar.read('resource-packs/' + name) == (dist / name).read_bytes(), (key, name)
            print(f'{key}: patched {family["jar"].name}: {report[key]["jar_entries_changed"]}')
    (work / 'weapon-remodel-report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')


if __name__ == '__main__':
    main()
