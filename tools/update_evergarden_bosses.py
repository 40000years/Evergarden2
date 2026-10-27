"""Ship the Evergarden main-boss rigs (Arkhaia, Seraphon) and retire the old static faction previews.

Patches evergarden/dist in place, like update_boss_orbs.py: unrelated entries stay
byte-identical. Removes every boss_arkhaia_* / boss_seraphon_* item model, attachable,
geometry and atlas from both packs and the Geyser mappings (the ModelEngine rigs replace
them), re-exports the native Arkhaia entity and adds the native Seraphon entity.
Run after evergarden/tools/arkhaia_sovereign.py and seraphon_verdict.py.
"""
from pathlib import Path
import hashlib
import json
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'evergarden/tools'))
from arkhaia_bedrock_assets import assets as boss_assets

VERSION = [3, 45, 0]
RETIRED = ('boss_arkhaia', 'boss_seraphon')


def retired(name):
    return any(tag in name for tag in RETIRED)


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, separators=(',', ':')) + '\n').encode()


def write(path, files):
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for entry, data in sorted(files.items()):
            info = zipfile.ZipInfo(entry, (2026, 10, 22, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, data)


def main():
    dist = ROOT / 'evergarden/dist'
    packs = {}
    for name in ('judge-java.zip', 'evergarden-bedrock.mcpack'):
        with zipfile.ZipFile(dist / name) as z:
            packs[name] = {n: z.read(n) for n in z.namelist() if not n.endswith('/')}
    removed = {name: sorted(n for n in files if retired(n)) for name, files in packs.items()}
    for name, files in packs.items():
        for entry in removed[name]:
            del files[entry]
    bedrock = packs['evergarden-bedrock.mcpack']
    atlas = json.loads(bedrock['textures/item_texture.json'])
    atlas['texture_data'] = {k: v for k, v in atlas['texture_data'].items() if not retired(k)}
    bedrock['textures/item_texture.json'] = json_bytes(atlas)
    native = boss_assets()
    changed = any(removed.values()) or any(bedrock.get(path) != data for path, data in native.items())
    bedrock.update(native)
    manifest = json.loads(bedrock['manifest.json'])
    current = manifest['header']['version']
    # Other releases bump this too: never go backwards, bump only when boss content changed.
    version = max(current, VERSION)
    if changed and version == current:
        version = [current[0], current[1] + 1, 0]
    manifest['header']['version'] = version
    for module in manifest['modules']:
        module['version'] = version
    bedrock['manifest.json'] = json_bytes(manifest)
    for name, files in packs.items():
        write(dist / name, files)
    mappings = json.loads((dist / 'geyser-mappings.json').read_text(encoding='utf8'))
    dropped = 0
    for item, entries in mappings['items'].items():
        kept = [e for e in entries if not retired(e.get('bedrock_identifier', ''))]
        dropped += len(entries) - len(kept)
        entries[:] = kept
    (dist / 'geyser-mappings.json').write_bytes(json_bytes(mappings))
    hashes = json.loads((dist / 'pack-hashes.json').read_text())
    for name in packs:
        hashes[name] = hashlib.sha1((dist / name).read_bytes()).hexdigest()
    (dist / 'pack-hashes.json').write_text(json.dumps(hashes, indent=2) + '\n')
    design_path = ROOT / 'evergarden/src/main/resources/judge-design.json'
    design = json.loads(design_path.read_text(encoding='utf8'))
    design.pop('faction-previews', None)
    design['bedrock-version'] = '.'.join(map(str, version))
    resources = ROOT / 'evergarden/src/main/resources/modelengine'
    design['evergarden-bosses'] = {name: json.loads((resources / f'{name}-manifest.json').read_text(encoding='utf8'))['revision']
                                   for name in ('arkhaia', 'seraphon')}
    design_path.write_bytes(json_bytes(design))
    print(f"Retired {len(removed['judge-java.zip'])} Java and {len(removed['evergarden-bedrock.mcpack'])} Bedrock static boss "
          f"entries, {dropped} Geyser mappings; native rigs: {len(native)} files ({'changed' if changed else 'unchanged'}); "
          f"Bedrock content {'.'.join(map(str, version))}")


if __name__ == '__main__':
    main()
