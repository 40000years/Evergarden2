"""Give the Java dragon rigs the same 2048 px palette atlas the Bedrock entities use.

The three forge dragons carried 4096 x 4096 atlases (about 10 MB each). In the merged realm_bosses blueprint they made
ModelEngine's generated pack 47.5 MB, slowed every server start (zip phase 12.6 s), and put three 4096 textures into
every Java client's block atlas. The Bedrock pack already ships each atlas at 2048 px, palette-quantised with a hard
alpha cut (tools/build_dragon_hunt_bedrock.py); this copies exactly those bytes into:

  * modelengine/realm/realm_bosses.bbmodel  (Java ModelEngine; UVs keep their 1024 uv_width space)
  * modelengine/dragons/forge_dragon_<kind>.bbmodel  (server hitbox / Bedrock export source; UVs keep 4096)

Only the image and its width/height change; geometry, UV space and animations stay byte-identical. Idempotent.

    python tools/shrink_dragon_textures.py
"""
from __future__ import annotations

import base64
import io
import json
import zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'evergarden/src/main/resources/modelengine'
PACK = ROOT / 'evergarden/dist/evergarden-bedrock.mcpack'
MODELS = ('forge_dragon_fire', 'forge_dragon_storm', 'forge_dragon_ice')


def load(path: Path):
    return json.loads(path.read_text(encoding='utf-8'))


def dump(path: Path, model):
    path.write_text(json.dumps(model, ensure_ascii=False, separators=(',', ':')) + '\n', encoding='utf-8', newline='\n')


def retexture(model, name: str, png: bytes, size: tuple[int, int]) -> bool:
    texture = next(t for t in model['textures'] if t['name'] == name)
    source = 'data:image/png;base64,' + base64.b64encode(png).decode()
    if texture['source'] == source and texture['width'] == size[0]:
        return False
    texture['source'] = source
    texture['width'], texture['height'] = size
    return True


def main():
    with zipfile.ZipFile(PACK) as pack:
        atlases = {m: pack.read(f'textures/entity/{m}_native.png') for m in MODELS}
    merged_path = RES / 'realm/realm_bosses.bbmodel'
    merged = load(merged_path)
    changed = False
    for model_id, png in atlases.items():
        size = Image.open(io.BytesIO(png)).size
        if size != (2048, 2048):
            raise RuntimeError(f'{model_id}: Bedrock atlas is {size}, expected 2048')
        rig_path = RES / f'dragons/{model_id}.bbmodel'
        rig = load(rig_path)
        before = rig['resolution'], [t.get('uv_width') for t in rig['textures']]
        if retexture(rig, f'{model_id}.png', png, size):
            assert (rig['resolution'], [t.get('uv_width') for t in rig['textures']]) == before
            dump(rig_path, rig)
            print(f'{rig_path.relative_to(ROOT)}: atlas -> 2048 ({len(png)} bytes)')
        changed |= retexture(merged, f'{model_id}.png', png, size)
    if changed:
        dump(merged_path, merged)
    print(f'{merged_path.relative_to(ROOT)}: {merged_path.stat().st_size} bytes' + ('' if changed else ' (unchanged)'))


if __name__ == '__main__':
    main()
