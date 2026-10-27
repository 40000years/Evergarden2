"""Export generated legendary cutouts to the existing 128px wand texture format.

This only normalizes canvas size and alpha for Minecraft; the original generated
artwork is preserved in art/wands/legendary/generated. No checkerboard removal,
recoloring, largest-component filtering, or changes to the designs are performed.
Requires Pillow. Ordinary wand artwork has been retired; this exporter remains
available only for the preserved legendary source artwork.
"""

import argparse
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
WANDS = ROOT / 'art/wands'
STAGING = WANDS / 'legendary'
NAMES = (
    ('verdant_sovereign', 'Verdant Sovereign', '05'),
    ('lunar_requiem', 'Lunar Requiem', '06'),
    ('crimson_oblivion', 'Crimson Oblivion', '07'),
)


def normalize(source: Path) -> Image.Image:
    with Image.open(source) as original:
        image = original.convert('RGBA')
    alpha = image.getchannel('A').point(lambda value: 255 if value >= 128 else 0)
    if alpha.getextrema() != (0, 255):
        raise ValueError(f'{source}: expected a genuinely transparent cutout')
    image.putalpha(alpha)
    bounds = image.getbbox()
    if bounds is None:
        raise ValueError(f'{source}: empty cutout')
    cutout = image.crop(bounds)
    scale = 112 / max(cutout.size)
    size = tuple(max(1, round(edge * scale)) for edge in cutout.size)
    cutout = cutout.resize(size, Image.Resampling.NEAREST)
    final = Image.new('RGBA', (128, 128))
    final.paste(cutout, ((128 - size[0]) // 2, (128 - size[1]) // 2))
    # Canonical zero RGB for fully transparent pixels, matching the existing art.
    pixels = final.load()
    for y in range(128):
        for x in range(128):
            if not pixels[x, y][3]:
                pixels[x, y] = (0, 0, 0, 0)
    validate(final, source)
    return final


def validate(image: Image.Image, source: Path) -> None:
    alpha = image.getchannel('A').tobytes()
    if image.size != (128, 128) or image.mode != 'RGBA':
        raise ValueError(f'{source}: expected 128x128 RGBA')
    if set(alpha) != {0, 255}:
        raise ValueError(f'{source}: alpha must be binary')
    opaque = sum(value == 255 for value in alpha)
    if not 0.025 < opaque / len(alpha) < 0.45:
        raise ValueError(f'{source}: unsuitable item occupancy {opaque / len(alpha):.3f}')
    for y in range(128):
        for x in range(128):
            if (x < 3 or x >= 125 or y < 3 or y >= 125) and alpha[y * 128 + x]:
                raise ValueError(f'{source}: unsafe transparent border')


def font(size: int, bold: bool = False) -> ImageFont.ImageFont:
    candidates = [
        Path('C:/Windows/Fonts') / ('segoeuib.ttf' if bold else 'segoeui.ttf'),
        Path('/usr/share/fonts/truetype/dejavu') / ('DejaVuSans-Bold.ttf' if bold else 'DejaVuSans.ttf'),
    ]
    for path in candidates:
        if path.is_file():
            return ImageFont.truetype(str(path), size)
    return ImageFont.load_default(size=size)


def make_preview() -> None:
    board = Image.new('RGB', (1360, 910), '#0c1220')
    draw = ImageDraw.Draw(board)
    draw.text((32, 22), 'ADVANCE MAGIC | LEGENDARY WANDS', font=font(30, True), fill='#f7d98c')
    draw.text((34, 64), 'Final 128 x 128 RGBA textures | 3x nearest-neighbor view | transparent cutouts', font=font(18), fill='#b1bccf')
    for index, (item_id, title, number) in enumerate(NAMES):
        x, y = 28 + index * 444, 106
        draw.rounded_rectangle((x, y, x + 416, y + 486), radius=14, fill='#171f2e', outline='#a48642', width=2)
        sprite = Image.open(WANDS / f'{item_id}.png').convert('RGBA').resize((384, 384), Image.Resampling.NEAREST)
        board.paste(sprite, (x + 16, y + 10), sprite)
        draw.text((x + 20, y + 400), f'{number}  {title}', font=font(24, True), fill='#f7e6b5')
        draw.text((x + 20, y + 442), f'{item_id}.png', font=font(16), fill='#a9bdd9')
    draw.text((34, 620), 'EXISTING WAND STYLE / SCALE REFERENCE', font=font(20, True), fill='#c2cfde')
    existing = ('shulker_levitation', 'heavens_judgment', 'solar_apocalypse', 'chronos_final_hour')
    for index, item_id in enumerate(existing):
        x, y = 76 + index * 324, 666
        sprite = Image.open(WANDS / f'{item_id}.png').convert('RGBA').resize((192, 192), Image.Resampling.NEAREST)
        board.paste(sprite, (x, y), sprite)
        draw.text((x - 8, y + 202), item_id.replace('_', ' ').title(), font=font(15), fill='#a9bdd9')
    board.save(STAGING / 'texture-preview.png', optimize=True)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-dir', type=Path, default=STAGING / 'generated')
    args = parser.parse_args()
    STAGING.mkdir(parents=True, exist_ok=True)
    reports = []
    for item_id, _, _ in NAMES:
        source = args.source_dir / f'{item_id}.png'
        image = normalize(source)
        destination = WANDS / f'{item_id}.png'
        image.save(destination, optimize=True)
        alpha = image.getchannel('A').tobytes()
        reports.append({'id': item_id, 'texture': str(destination.relative_to(ROOT)), 'size': list(image.size), 'mode': image.mode, 'bounds': list(image.getbbox()), 'opaque_pixels': sum(value == 255 for value in alpha), 'bytes': destination.stat().st_size})
    make_preview()
    print(json.dumps(reports, indent=2))


if __name__ == '__main__':
    main()
