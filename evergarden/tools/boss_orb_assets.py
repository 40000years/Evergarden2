"""Register the six imagegen-authored sanctuary trophies in both item packs.

Also registers the Seraph's Core of Legend (rendered by legend_core_art.py from the
Cocytus trophy), the Seraph Feathers (seraph_feather_art.py) and the companion egg
(pet_egg_art.py). They use direct item models; only the heart-of-the-sea trophies add
a custom-model-data fallback route.
"""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
IDS = ('acheron', 'phlegethon', 'cocytus', 'banshee', 'malefeasance', 'styx')
TITLES = ('Acheron', 'Phlegethon', 'Cocytus', 'Banshee Wail', 'Malefeasance', 'Styx')
# name -> (texture, vanilla base, Bedrock display name, selector fallback)
EXTRAS = {
    'core_of_legend': (ROOT / 'art/boss-orbs/textures/legend.png', 'heart_of_the_sea', 'Core of Legend', True),
    'core_of_seraph': (ROOT / 'art/boss-armor-cores/textures/core_of_seraph.png', 'nether_star', 'Core of Seraph', True),
    'core_of_revenant': (ROOT / 'art/boss-armor-cores/textures/core_of_revenant.png', 'echo_shard', 'Core of Revenant', True),
    'pet_egg': (ROOT / 'art/pets/pet_egg.png', 'paper', 'Pet Egg', False),
    'seraph_feather': (ROOT / 'art/seraph/seraph_feather.png', 'feather', 'Seraph Feather', False),
    'seraph_feather_golden': (ROOT / 'art/seraph/seraph_feather_golden.png', 'feather', 'Golden Seraph Feather', False),
}


def item(java, bedrock, textures, mappings, selectors, write_json, name, data, base, title, fallback):
    for dest in (java / f'assets/voidscape/textures/item/{name}.png', bedrock / f'textures/items/{name}.png'):
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(data)
    model = {'type': 'minecraft:model', 'model': 'voidscape:item/' + name}
    write_json(java / f'assets/voidscape/models/item/{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'voidscape:item/' + name}})
    write_json(java / f'assets/voidscape/items/{name}.json', {'model': model})
    if fallback:
        selectors.setdefault(base, []).append({'when': 'voidscape:' + name, 'model': model})
    textures['voidscape.' + name] = {'textures': 'textures/items/' + name}
    entries = mappings['items'].setdefault('minecraft:' + base, [])
    entries[:] = [e for e in entries if e.get('model') != 'voidscape:' + name]
    entries.append({'type': 'definition', 'model': 'voidscape:' + name, 'bedrock_identifier': 'voidscape:' + name,
                    'display_name': title, 'bedrock_options': {'icon': 'voidscape.' + name, 'allow_offhand': True,
                    'display_handheld': False, 'creative_category': 'items'}})


def register(java, bedrock, textures, mappings, selectors, write_json):
    for boss, title in zip(IDS, TITLES):
        name = 'boss_orb_' + boss
        data = (ROOT / 'art/boss-orbs/textures' / (boss + '.png')).read_bytes()
        item(java, bedrock, textures, mappings, selectors, write_json, name, data, 'heart_of_the_sea', title + ' Soul Orb', True)
    for name, (texture, base, title, fallback) in EXTRAS.items():
        item(java, bedrock, textures, mappings, selectors, write_json, name, texture.read_bytes(), base, title, fallback)
