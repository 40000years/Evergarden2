"""Add the wearable armor sets and the menu textures (Skill Tree, staff, pets) to Advance Magic's dist packs.

Generates every set in SETS with armor_set_forge plus the menu modules in MENUS, replaces
only their own entries in advance-magic-java.zip / advance-magic-bedrock.mcpack,
merges their icons into the Bedrock item atlas and their rows into
geyser-mappings.json, sets the Bedrock pack version and rewrites pack-hashes.json.
Every other pack entry must stay byte-identical. Build the plugin JARs afterwards
with build_plugin_jars.py.

    python tools/build_armor_sets.py --version 2.22.0
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
sys.path.insert(0, str(ROOT / 'evergarden/tools'))
from build_weapon_remodel import encode, read_zip, write_zip  # noqa: E402
import armor_set_forge as forge  # noqa: E402
import revenant_armor_assets  # noqa: E402
import seraph_armor_assets  # noqa: E402
import menu_gui_assets  # noqa: E402
import skilltree_gui_assets  # noqa: E402

DIST = ROOT / 'advance-magic/dist'
SETS = (seraph_armor_assets.SET, revenant_armor_assets.SET)
MENUS = (skilltree_gui_assets, menu_gui_assets)
OWNED = tuple(prefix for s in SETS for prefix in forge.owned(s.key)) + tuple(prefix for m in MENUS for prefix in m.owned())
IDS = tuple(f'{forge.NS}:{s.key}_' for s in SETS) + tuple(f'{m.NS}:{m.PREFIX}' for m in MENUS)


def compact(value) -> bytes:
    return json.dumps(value, ensure_ascii=False, separators=(',', ':')).encode('utf8')


def generate():
    with tempfile.TemporaryDirectory() as temp:
        java, bedrock = Path(temp) / 'java', Path(temp) / 'bedrock'
        atlas, definitions = {}, {}
        for s in SETS:
            forge.register(s, java, bedrock, atlas, definitions)
        for menu in MENUS:
            menu.register(java, bedrock, atlas, definitions)
        files = {}
        for side, folder in (('java', java), ('bedrock', bedrock)):
            files[side] = {}
            for file in sorted(folder.rglob('*')):
                if file.is_file():
                    name = file.relative_to(folder).as_posix()
                    assert name.startswith(OWNED), ('generator wrote outside the sets', name)
                    files[side][name] = encode(file)
    return files, atlas, definitions


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--version', required=True, help='Bedrock pack version, e.g. 2.21.0 (never lower than the current one)')
    args = parser.parse_args()
    version = [int(v) for v in args.version.split('.')]
    files, atlas, definitions = generate()
    java_name, bedrock_name = 'advance-magic-java.zip', 'advance-magic-bedrock.mcpack'
    old_java, old_bedrock = read_zip((DIST / java_name).read_bytes()), read_zip((DIST / bedrock_name).read_bytes())
    java = {n: d for n, d in old_java.items() if not n.startswith(OWNED)} | files['java']
    bedrock = {n: d for n, d in old_bedrock.items() if not n.startswith(OWNED)} | files['bedrock']

    item_atlas = json.loads(bedrock['textures/item_texture.json'])
    keys = tuple(f'{forge.NS}.{s.key}_' for s in SETS) + tuple(f'{m.NS}.{m.PREFIX}' for m in MENUS)
    item_atlas['texture_data'] = {k: v for k, v in item_atlas['texture_data'].items() if not k.startswith(keys)} | atlas
    bedrock['textures/item_texture.json'] = compact(item_atlas)
    manifest = json.loads(bedrock['manifest.json'])
    current = manifest['header']['version']
    # Clients cache by UUID + version; a re-run with the same number is only safe before release.
    assert version >= current, f'{version} is lower than the current {current}'
    manifest['header']['version'] = version
    for module in manifest['modules']:
        module['version'] = version
    bedrock['manifest.json'] = compact(manifest)
    for name, old, new in ((java_name, old_java, java), (bedrock_name, old_bedrock, bedrock)):
        untouched = [n for n in old if not n.startswith(OWNED) and n not in ('textures/item_texture.json', 'manifest.json')]
        assert all(new[n] == old[n] for n in untouched), (name, 'unrelated entries changed')
        write_zip(DIST / name, new)

    mappings = json.loads((DIST / 'geyser-mappings.json').read_text(encoding='utf8'))
    for material, rows in list(mappings['items'].items()):
        mappings['items'][material] = [r for r in rows if not r.get('bedrock_identifier', '').startswith(IDS)]
    for material, rows in definitions.items():
        mappings['items'].setdefault(material, []).extend(rows)
    (DIST / 'geyser-mappings.json').write_text(json.dumps(mappings, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    hashes = json.loads((DIST / 'pack-hashes.json').read_text(encoding='utf8'))
    for name in (java_name, bedrock_name):
        hashes[name] = hashlib.sha1((DIST / name).read_bytes()).hexdigest()
    (DIST / 'pack-hashes.json').write_text(json.dumps(hashes, indent=2) + '\n', encoding='utf8')
    print(f"{len(SETS)} armor sets + menus: {len(files['java'])} Java + {len(files['bedrock'])} Bedrock entries; "
          f"Bedrock {'.'.join(map(str, current))} -> {'.'.join(map(str, version))}")
    print(json.dumps(hashes, indent=2))


if __name__ == '__main__':
    main()
