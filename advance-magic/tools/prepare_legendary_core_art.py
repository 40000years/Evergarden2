"""Export the three generated core relics to transparent 128px game sprites."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'art/cores'
CATALOG = (
    ('verdant_sovereign', 'Core of the Verdant Crown'),
    ('lunar_requiem', 'Core of the Requiem Moon'),
    ('crimson_oblivion', 'Core of Oblivion'),
)


def main():
    preview = Image.new('RGB', (1200, 510), '#0c1220')
    draw = ImageDraw.Draw(preview)
    font_path = Path('C:/Windows/Fonts/segoeuib.ttf')
    font = ImageFont.truetype(str(font_path), 20) if font_path.exists() else ImageFont.load_default(size=20)
    draw.text((24, 18), 'ADVANCE MAGIC | LEGENDARY CORES | 128 x 128 RGBA', font=font, fill='#f7d98c')
    for index, (item_id, title) in enumerate(CATALOG):
        with Image.open(ART / 'legendary/generated' / f'core_{item_id}.png') as original:
            image = original.convert('RGBA')
        alpha = image.getchannel('A').point(lambda value: 255 if value >= 128 else 0)
        assert alpha.getextrema() == (0, 255), f'{item_id}: source must contain genuine transparency'
        image.putalpha(alpha)
        image = image.crop(image.getbbox())
        scale = 96 / max(image.size)
        size = tuple(max(1, round(edge * scale)) for edge in image.size)
        image = image.resize(size, Image.Resampling.NEAREST)
        sprite = Image.new('RGBA', (128, 128))
        sprite.paste(image, ((128 - size[0]) // 2, (128 - size[1]) // 2))
        pixels = sprite.load()
        for y in range(128):
            for x in range(128):
                if not pixels[x, y][3]:
                    pixels[x, y] = (0, 0, 0, 0)
        histogram = sprite.getchannel('A').histogram()
        assert histogram[0] > 128 * 128 * .5, f'{item_id}: background must be more than half transparent'
        assert histogram[255] > 128 * 128 * .025, f'{item_id}: visible core missing'
        output = ART / f'core_{item_id}.png'
        sprite.save(output, optimize=True)
        tile = sprite.resize((384, 384), Image.Resampling.NEAREST)
        x = 8 + index * 398
        preview.paste(tile, (x, 64), tile)
        draw.text((x + 12, 466), title, font=font, fill='#f3dfb1')
        print(f'{output.relative_to(ROOT).as_posix()}: 128x128 RGBA, {output.stat().st_size} bytes')
    preview.save(ART / 'legendary/core-preview.png', optimize=True)


if __name__ == '__main__':
    main()
