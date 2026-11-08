"""Register the three Dragon Nest scales (evergarden/tools/dragon_scale_art.py) in both Evergarden packs.

Java (evergarden-java.zip): a direct item model per scale (voidscape:dragon_scale_<kind>, set as the item's
item_model). Bedrock (evergarden-bedrock.mcpack): the icon plus its item_texture.json entry, content version
3.71.0. geyser-mappings.json: one custom-item definition per scale on minecraft:echo_shard. Every other entry stays
byte-identical; pack-hashes.json and judge-design.json follow. Run once; it refuses to add entries twice.

    python evergarden/tools/dragon_scale_art.py
    python tools/build_dragon_scale_assets.py
"""
from __future__ import annotations

import hashlib
import io
import json
import re
import shutil
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / 'evergarden/dist'
ART = ROOT / 'evergarden/art/dragon-scales/textures'
DESIGN = ROOT / 'evergarden/src/main/resources/judge-design.json'
BACKUP = ROOT / 'output/dragon-scales-build/backup'
SCALES = {'fire': 'Vharok Fire Scale', 'ice': 'Glaceryn Frost Scale', 'storm': 'Vaelthyr Storm Scale'}
TARGET = (3, 71, 0)
DATE = (2026, 11, 5, 0, 0, 0)


def jbytes(value, compact=True):
    return (json.dumps(value, ensure_ascii=False, separators=(',', ':')) if compact else json.dumps(value, indent=2)).encode() + b'\n'


def rewrite(path: Path, files: dict[str, bytes]):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as out:
        for name, data in sorted(files.items()):
            info = zipfile.ZipInfo(name, DATE)
            info.compress_type = zipfile.ZIP_DEFLATED
            out.writestr(info, data)
    path.write_bytes(buffer.getvalue())
    return buffer.getvalue()


def read(path: Path):
    with zipfile.ZipFile(path) as z:
        return {n: z.read(n) for n in z.namelist() if not n.endswith('/')}


def main():
    BACKUP.mkdir(parents=True, exist_ok=True)
    for name in ('evergarden-java.zip', 'evergarden-bedrock.mcpack', 'geyser-mappings.json', 'pack-hashes.json'):
        if not (BACKUP / name).exists():
            shutil.copy2(DIST / name, BACKUP / name)
    if not (BACKUP / DESIGN.name).exists():
        shutil.copy2(DESIGN, BACKUP / DESIGN.name)
    icons = {kind: (ART / f'dragon_scale_{kind}.png').read_bytes() for kind in SCALES}

    java = read(DIST / 'evergarden-java.zip')
    for kind in SCALES:
        name = f'dragon_scale_{kind}'
        if f'assets/voidscape/items/{name}.json' in java:
            raise RuntimeError('Java pack already has ' + name)
        java[f'assets/voidscape/textures/item/{name}.png'] = icons[kind]
        java[f'assets/voidscape/models/item/{name}.json'] = jbytes({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'voidscape:item/' + name}})
        java[f'assets/voidscape/items/{name}.json'] = jbytes({'model': {'type': 'minecraft:model', 'model': 'voidscape:item/' + name}})
    java_bytes = rewrite(DIST / 'evergarden-java.zip', java)

    bedrock = read(DIST / 'evergarden-bedrock.mcpack')
    textures = json.loads(bedrock['textures/item_texture.json'])
    for kind in SCALES:
        name = f'dragon_scale_{kind}'
        if f'textures/items/{name}.png' in bedrock:
            raise RuntimeError('Bedrock pack already has ' + name)
        bedrock[f'textures/items/{name}.png'] = icons[kind]
        textures['texture_data']['voidscape.' + name] = {'textures': 'textures/items/' + name}
    bedrock['textures/item_texture.json'] = jbytes(textures)
    manifest = json.loads(bedrock['manifest.json'])
    old = manifest['header']['version']
    if tuple(old) >= TARGET:
        raise RuntimeError(f'Bedrock pack is already {old}')
    manifest['header']['version'] = list(TARGET)
    for module in manifest['modules']:
        module['version'] = list(TARGET)
    bedrock['manifest.json'] = jbytes(manifest)
    bedrock_bytes = rewrite(DIST / 'evergarden-bedrock.mcpack', bedrock)

    mappings = json.loads((DIST / 'geyser-mappings.json').read_text(encoding='utf8'))
    entries = mappings['items'].setdefault('minecraft:echo_shard', [])
    for kind, title in SCALES.items():
        name = f'dragon_scale_{kind}'
        entries[:] = [e for e in entries if e.get('model') != 'voidscape:' + name]
        entries.append({'type': 'definition', 'model': 'voidscape:' + name, 'bedrock_identifier': 'voidscape:' + name,
                        'display_name': title, 'bedrock_options': {'icon': 'voidscape.' + name, 'allow_offhand': True,
                                                                    'display_handheld': False, 'creative_category': 'items'}})
    raw = (DIST / 'geyser-mappings.json').read_text(encoding='utf8')
    indent = 2 if raw.lstrip().startswith('{\n') else None
    (DIST / 'geyser-mappings.json').write_text((json.dumps(mappings, indent=indent, ensure_ascii=False) if indent else json.dumps(mappings, ensure_ascii=False, separators=(',', ':'))) + ('\n' if raw.endswith('\n') else ''), encoding='utf8')

    hashes = json.loads((DIST / 'pack-hashes.json').read_text(encoding='utf8'))
    hashes['evergarden-java.zip'] = hashlib.sha1(java_bytes).hexdigest()
    hashes['evergarden-bedrock.mcpack'] = hashlib.sha1(bedrock_bytes).hexdigest()
    (DIST / 'pack-hashes.json').write_text(json.dumps(hashes, separators=(',', ':')) + '\n', encoding='utf8')
    version = '.'.join(map(str, TARGET))
    design, count = re.subn(r'("bedrock-version":\s*)"[\d.]+"', lambda m: m.group(1) + '"' + version + '"', DESIGN.read_text(encoding='utf8'), count=1)
    if count != 1:
        raise RuntimeError('judge-design.json has no bedrock-version')
    DESIGN.write_text(design, encoding='utf8')
    print(f'Java pack {len(java_bytes)} bytes; Bedrock {".".join(map(str, old))} -> {version} ({len(bedrock_bytes)} bytes); 3 Geyser definitions on echo_shard')


if __name__ == '__main__':
    main()
