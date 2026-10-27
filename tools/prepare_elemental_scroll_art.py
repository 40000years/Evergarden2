"""Extract authored scroll sprites from the eight transparent source atlases.

The source images and prompts are retained in advance-magic/art/elemental-scrolls.
This step only isolates, crops and downsamples artwork; pack builds consume the
resulting checked-in icons and never need an image-generation service.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

from elemental_spell_assets import catalog

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'advance-magic/art/elemental-scrolls'
ELEMENTS = ('fire', 'water', 'ice', 'wind', 'earth', 'light', 'dark', 'lightning')
SIZE = 128


def separators(mask: Image.Image, count: int):
    """Find the transparent gutters, including atlases with taller final rows."""
    xs = list(mask.resize((mask.width, 1), Image.Resampling.BOX).tobytes())
    ys = list(mask.resize((1, mask.height), Image.Resampling.BOX).tobytes())

    def cut(density, low, high, target):
        size = len(density)
        return min(range(round(size * low), round(size * high)),
                   key=lambda i: (density[i], abs(i - size * target)))

    columns = [0] + [cut(xs, centre - .05, centre + .05, centre)
                     for centre in (.25, .5, .75)] + [mask.width]
    rows = [0, cut(ys, .23, .44, 1 / 3)]
    if count > 8:
        rows.append(cut(ys, .55, .76, 2 / 3))
    rows.append(mask.height)
    return columns, rows


def extract(sheet: Image.Image, index: int, grid):
    """Flood-fill the scroll's opaque body, retaining its original alpha edge.

    Source atlases have varying finial heights, so a rigid grid crop would cut
    some crowns or collect a piece of the next row. Cell centres identify the
    objects; their connected silhouettes determine the actual crop bounds.
    """
    x = round((index % 4 + .5) * sheet.width / 4)
    y = round((index // 4 + .5) * sheet.height / 3)
    alpha = sheet.getchannel('A')
    mask = alpha.point(lambda value: 255 if value >= 128 else 0)
    columns, rows = grid
    column, row = index % 4, index // 4
    zone = Image.new('L', sheet.size)
    ImageDraw.Draw(zone).rectangle((columns[column], rows[row], columns[column + 1] - 1, rows[row + 1] - 1), fill=255)
    mask = ImageChops.multiply(mask, zone)
    if mask.getpixel((x, y)) != 255:
        raise ValueError(f'Cell {index + 1} has no opaque scroll at its centre')
    ImageDraw.floodfill(mask, (x, y), 128, thresh=0)
    body = mask.point(lambda value: 255 if value == 128 else 0)
    bounds = body.getbbox()
    if bounds is None:
        raise ValueError(f'Cell {index + 1} has no connected artwork')
    left, top, right, bottom = bounds
    if right - left > sheet.width * .32 or bottom - top > sheet.height * .46:
        raise ValueError(f'Cell {index + 1} touches another scroll: {bounds}')
    if right - left < sheet.width * .12 or bottom - top < sheet.height * .18:
        raise ValueError(f'Cell {index + 1} only selected a small detached piece: {bounds}')
    # Two pixels retain original antialiasing without a neighbouring sprite.
    edge = ImageChops.multiply(body.filter(ImageFilter.MaxFilter(5)), zone)
    sprite = sheet.copy()
    sprite.putalpha(ImageChops.multiply(alpha, edge))
    bounds = sprite.getbbox()
    sprite = sprite.crop(bounds)
    ratio = 120 / max(sprite.size)
    sprite = sprite.resize(tuple(max(1, round(v * ratio)) for v in sprite.size), Image.Resampling.LANCZOS)
    icon = Image.new('RGBA', (SIZE, SIZE))
    icon.alpha_composite(sprite, ((SIZE - sprite.width) // 2, (SIZE - sprite.height) // 2))
    return icon, bounds


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--element', choices=ELEMENTS, help='Prepare a single element while reviewing art')
    args = parser.parse_args()
    selected = (args.element,) if args.element else ELEMENTS
    rows = catalog(ROOT / 'advance-magic')
    output = ART / 'icons'
    output.mkdir(parents=True, exist_ok=True)
    manifest = {'version': 2, 'texture_size': SIZE, 'mode': 'RGBA', 'icons': []}
    for element in selected:
        source = ART / 'source' / f'{element}.png'
        with Image.open(source) as opened:
            sheet = opened.convert('RGBA')
        spells = [spell for spell in rows if spell['element'] == element]
        grid = separators(sheet.getchannel('A').point(lambda value: 255 if value >= 128 else 0), len(spells))
        for index, spell in enumerate(spells):
            icon, bounds = extract(sheet, index, grid)
            path = output / f'{spell["id"]}.png'
            icon.save(path, optimize=True)
            manifest['icons'].append({**spell, 'file': 'icons/' + path.name,
                'source': 'source/' + source.name, 'source_size': list(sheet.size),
                'source_crop': list(bounds), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
        print(f'{element}: {len(spells)} isolated 128px scrolls')
    if not args.element:
        hashes = {row['sha256'] for row in manifest['icons']}
        if len(hashes) != len(rows):
            raise ValueError('Every spell must have a distinct authored icon')
        (ART / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
        print(f'Prepared all {len(rows)} unique icons and source crop manifest')


if __name__ == '__main__':
    main()
