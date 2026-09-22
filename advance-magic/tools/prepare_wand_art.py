"""User-authorized removal of baked checkerboards from generated wand artwork.

Development only: Pillow, NumPy and SciPy. Release builds consume committed PNGs.
Retains the largest wand silhouette, fills internal highlights, drops distant glints,
and normalizes all sprites to a sharp, transparent 128px canvas.
"""
import json
import shutil
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy import ndimage as ndi

ROOT = Path(__file__).resolve().parents[1]


def cutout(source):
    image = Image.open(source).convert('RGB').resize((512, 512), Image.Resampling.LANCZOS)
    rgb = np.array(image).astype(np.int16)
    chroma = rgb.max(axis=2) - rgb.min(axis=2)
    dark = rgb.max(axis=2) < 145
    seed = (chroma > 45) | dark
    seed = ndi.binary_opening(seed, structure=np.ones((3, 3)))
    seed = ndi.binary_closing(seed, structure=np.ones((3, 3)))
    labels, count = ndi.label(seed)
    sizes = np.bincount(labels.ravel()); sizes[0] = 0
    mask = labels == sizes.argmax()
    mask = ndi.binary_fill_holes(mask)
    rgba = np.concatenate([rgb.astype(np.uint8), (mask * 255).astype(np.uint8)[..., None]], axis=2)
    rgba[~mask] = 0
    result = Image.fromarray(rgba)
    bounds = result.getbbox()
    assert bounds is not None
    result = result.crop(bounds)
    ratio = 112 / max(result.size)
    result = result.resize((round(result.width*ratio), round(result.height*ratio)), Image.Resampling.NEAREST)
    final = Image.new('RGBA', (128, 128))
    final.paste(result, ((128-result.width)//2, (128-result.height)//2))
    return final


def main():
    catalog = json.loads((ROOT / 'art/generation-prompts.json').read_text(encoding='utf8'))
    raw = ROOT / 'art/generated'; raw.mkdir(exist_ok=True)
    output = ROOT / 'art/wands'; output.mkdir(exist_ok=True)
    board = Image.new('RGB', (1500, 1110), '#0b1425')
    draw = ImageDraw.Draw(board)
    fonts = Path('C:/Windows/Fonts')
    titlefont = ImageFont.truetype(str(fonts / 'georgiab.ttf'), 45)
    labelfont = ImageFont.truetype(str(fonts / 'segoeuib.ttf'), 18)
    smallfont = ImageFont.truetype(str(fonts / 'segoeui.ttf'), 15)
    draw.text((45, 28), 'ADVANCE MAGIC', font=titlefont, fill='#efd196')
    draw.text((48, 88), '15 ARCANE WANDS  /  FINAL IN-GAME TEXTURES  /  128 PX RGBA', font=smallfont, fill='#a7c9d5')
    for index, row in enumerate(catalog['wands']):
        source = raw / (row['id'] + '.png')
        if not source.exists():
            shutil.copyfile(row['path'], source)
        final = cutout(source)
        final.save(output / (row['id']+'.png'), optimize=True)
        x, y = 30+(index % 5)*294, 135+(index // 5)*316
        draw.rounded_rectangle((x, y, x+276, y+298), radius=16, fill='#142136', outline='#635439', width=2)
        preview = final.resize((230, 230), Image.Resampling.NEAREST)
        board.paste(preview, (x+23, y+12), preview)
        name = row['id'].replace('_', ' ').title().replace('Natures', "Nature's").replace('Dragons', "Dragon's")
        bounds = draw.textbbox((0, 0), name, font=labelfont)
        draw.text((x+(276-bounds[2])/2, y+250), name, font=labelfont, fill='#f1e5c8')
        draw.text((x+16, y+277), f'{index+1:02d}', font=smallfont, fill='#72cfc8')
    board.save(ROOT / 'dist/wand-textures-preview.png', optimize=True)
    print('Prepared 15 transparent 128px wand textures and PNG preview.')


if __name__ == '__main__':
    main()
