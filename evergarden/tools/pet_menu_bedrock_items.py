"""Register Java pet portrait items as Geyser Bedrock chest-menu items.

The Java menu selects ``evergarden:menu_pet_*`` item models, not the older
``advance_magic:menu_pet_*`` keys. Use the Java portrait archive as the single
source of truth so all species, stages and disabled portraits match.
"""
import io
import json
import zipfile


def register(files, atlas, mappings, portrait_archive):
    """Add portrait textures and mappings to in-memory Bedrock pack components."""
    if not isinstance(portrait_archive, bytes):
        portrait_archive = portrait_archive.read_bytes()
    entries = mappings['items'].setdefault('minecraft:paper', [])
    prefix = 'evergarden:menu_pet_'
    entries[:] = [entry for entry in entries if not entry.get('model', '').startswith(prefix)]
    count = 0
    with zipfile.ZipFile(io.BytesIO(portrait_archive)) as source:
        for entry in sorted(source.namelist()):
            if not entry.startswith('assets/evergarden/items/menu_pet_') or not entry.endswith('.json'):
                continue
            name = entry.rsplit('/', 1)[1][:-5]
            item = json.loads(source.read(entry))
            model = item['model']['model']
            assert model == 'evergarden:item/menu/' + name.removeprefix('menu_'), entry
            image = 'assets/evergarden/textures/item/menu/' + name.removeprefix('menu_') + '.png'
            icon = 'evergarden.' + name
            texture = 'textures/items/evergarden_menu/' + name
            files[texture + '.png'] = source.read(image)
            atlas['texture_data'][icon] = {'textures': texture}
            entries.append({'type': 'definition', 'model': 'evergarden:' + name,
                            'bedrock_identifier': 'evergarden:' + name,
                            'display_name': 'Pet',
                            'bedrock_options': {'icon': icon, 'allow_offhand': False,
                                                'display_handheld': False}})
            count += 1
    if not count:
        raise ValueError('No pet portrait item models found in Java archive')
    return count
