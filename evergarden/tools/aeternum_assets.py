"""Integrates Aeternum Seasons food & crop assets into Evergarden resource pack for zero-conflict compatibility."""
import json
import pathlib
import zipfile


def register_aeternum_assets(java, bedrock, textures, mappings, selectors, write_json, fallback_overrides):
    zip_path = pathlib.Path(__file__).parent / 'aeternum_foods.zip'
    if not zip_path.exists():
        return

    with zipfile.ZipFile(zip_path, 'r') as z:
        for info in z.infolist():
            name = info.filename
            if name.startswith('assets/aeternum/'):
                dest = java / name
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_bytes(z.read(name))
            elif name.startswith('assets/minecraft/items/') and name.endswith('.json'):
                base = name.split('/')[-1][:-5]
                data = json.loads(z.read(name).decode('utf-8'))
                if 'model' in data:
                    fallback_overrides[base] = data['model']
