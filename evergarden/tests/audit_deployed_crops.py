"""Read-only audit of every crop route in an installed Geyser pack."""
import hashlib
import json
import re
import sys
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
server = Path(sys.argv[1])
source = (root / 'src/main/java/com/example/voidscape/crop/CropType.java').read_text(encoding='utf-8')
ids = re.findall(r'\b[A-Z_]+\("([a-z_]+)", CropTier\.', source)
assert len(ids) == 30
geyser = server / 'plugins/Geyser-Spigot'
definitions = {}
for path in (geyser / 'custom_mappings').glob('*.json'):
    for base, entries in json.loads(path.read_text(encoding='utf-8-sig')).get('items', {}).items():
        for entry in entries:
            key = entry['bedrock_identifier']
            assert key not in definitions, f'Duplicate identifier: {key}'
            definitions[key] = (base, entry)
with zipfile.ZipFile(geyser / 'packs/voidscape-bedrock.mcpack') as pack:
    print('Installed pack:', json.loads(pack.read('manifest.json'))['header']['version'])
    atlas = json.loads(pack.read('textures/item_texture.json'))['texture_data']
    for cid in ids:
        for stage in range(3):
            name = f'crop_{cid}_stage_{stage}'
            base, entry = definitions['voidscape:' + name]
            assert base == 'minecraft:carved_pumpkin', name
            assert entry['model'] == 'voidscape:' + name and 'predicate' not in entry, name
            attachment = json.loads(pack.read(f'attachables/{name}.json'))['minecraft:attachable']['description']
            geometry = json.loads(pack.read(f'models/entity/{name}.geo.json'))['minecraft:geometry'][0]
            assert attachment['identifier'] == 'voidscape:' + name
            assert attachment['geometry']['default'] == geometry['description']['identifier']
            bone = geometry['bones'][0]
            assert bone['name'] == 'head' and bone['pivot'] == [0, 24, 0], name
            for cube in bone['cubes']:
                assert cube['origin'][1] == 24, name
                assert all(abs(face['uv_size'][0]) == 64 and face['uv_size'][1] == 64 for face in cube['uv'].values()), name
            texture = attachment['textures']['default']
            assert texture == atlas[entry['bedrock_options']['icon']]['textures'], name
            assert len(pack.read(texture + '.png')) > 100, name
        print('PASS', cid, 'stages 0/1/2')
for label, path in [('installed', server / 'plugins/evergarden.jar'), ('pending', server / 'plugins/update/evergarden.jar'), ('built', root / 'dist/evergarden-3.0.0.jar')]:
    print(label, hashlib.sha256(path.read_bytes()).hexdigest() if path.exists() else 'absent')
