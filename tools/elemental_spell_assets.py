"""Register authored Java and Bedrock inventory icons for all elemental scrolls."""
from __future__ import annotations

import json
import re
from pathlib import Path

from PIL import Image

ART = Path(__file__).resolve().parents[1] / 'advance-magic/art/elemental-scrolls'


COLORS = {
    'fire': ('#541d20', '#e34f2c', '#ffb34d', '#fff0b0'),
    'water': ('#123753', '#168fc6', '#58d9ed', '#d9ffff'),
    'ice': ('#243e70', '#568fe0', '#a8e8ff', '#f1ffff'),
    'wind': ('#174c49', '#43b99a', '#a0f4d3', '#edfff5'),
    'earth': ('#3e4e25', '#829c3c', '#c8dd66', '#f5ffd1'),
    'light': ('#66501e', '#d2a83c', '#ffe99a', '#fff9dc'),
    'dark': ('#301d50', '#8247bb', '#d18bff', '#f3dbff'),
    'lightning': ('#373365', '#8175d4', '#c9c3ff', '#ffffff'),
}
RARITY = {'BASIC': '#d5d2c9', 'RARE': '#5be0cf', 'EPIC': '#ce79ff', 'LEGENDARY': '#ffc85b'}


def catalog(root: Path):
    source = (root / 'src/main/java/com/example/advancemagic/elemental/ElementalSpell.java').read_text(encoding='utf8')
    pattern = re.compile(r'^\s*([A-Z][A-Z0-9_]*)\("([^"]+)",Element\.(\w+),\d+,\d+,[-\d.]+,Rarity\.(\w+)', re.M)
    rows = [{'id': name.lower(), 'title': title, 'element': element.lower(), 'rarity': rarity}
            for name, title, element, rarity in pattern.findall(source)]
    if len(rows) != 58:
        raise ValueError(f'Expected 58 elemental spells, found {len(rows)}')
    return rows


def _texture(spell):
    """Load final authored artwork; preserve existing item model identifiers."""
    path = ART / 'icons' / f'{spell["id"]}.png'
    with Image.open(path) as source:
        if source.size != (128, 128) or source.mode != 'RGBA':
            raise ValueError(f'Scroll texture must be transparent 128x128 RGBA: {path}')
        if source.getchannel('A').getextrema() != (0, 255):
            raise ValueError(f'Scroll texture must have transparent padding and opaque artwork: {path}')
        return source.copy()


def _json(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf8')


def register(root: Path, java: Path, bedrock: Path, atlas: dict, definitions: dict):
    spells = catalog(root)
    rows = definitions.setdefault('minecraft:paper', [])
    for spell in spells:
        name = spell['id']; key = 'scroll_' + name; model = f'advance_magic:{key}'
        texture = _texture(spell)
        for folder, rel in ((java, Path('assets/advance_magic/textures/item') / f'{key}.png'),
                            (bedrock, Path('textures/items') / f'{key}.png')):
            target = folder / rel; target.parent.mkdir(parents=True, exist_ok=True); texture.save(target, optimize=True)
        _json(java / 'assets/advance_magic/models/item' / f'{key}.json', {
            'parent': 'minecraft:item/generated', 'textures': {'layer0': f'advance_magic:item/{key}'}})
        _json(java / 'assets/advance_magic/items' / f'{key}.json', {
            'model': {'type': 'minecraft:model', 'model': f'advance_magic:item/{key}'}})
        icon = f'advance_magic.{key}'; atlas[icon] = {'textures': f'textures/items/{key}'}
        rows[:] = [row for row in rows if row.get('model') != model]
        rows.append({'type': 'definition', 'model': model, 'bedrock_identifier': model,
                     'display_name': spell['title'] + ' Scroll',
                     'bedrock_options': {'icon': icon, 'allow_offhand': True,
                                         'display_handheld': False, 'creative_category': 'items'}})


def main():
    root = Path(__file__).resolve().parents[1] / 'advance-magic'
    out = root / 'dist' / 'elemental-scroll-icons'
    out.mkdir(parents=True, exist_ok=True)
    rows = catalog(root)
    for spell in rows: _texture(spell).save(out / (spell['id'] + '.png'), optimize=True)
    print(f'Generated {len(rows)} distinct scroll icons in {out}')
    from preview_elemental_scrolls import build
    build()


if __name__ == '__main__': main()
