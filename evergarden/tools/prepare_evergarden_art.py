"""Process AI-generated pixel-art sprites into production-grade 128x128 RGBA Minecraft item textures.
Removes baked checkerboard patterns using chromatic floodfill and normalizes scale.
"""
import glob
from pathlib import Path
import numpy as np
from PIL import Image
from scipy import ndimage as ndi

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'art/equipment'
OUTPUT.mkdir(parents=True, exist_ok=True)

ITEMS = {
    'scroll_eternity': 'scroll_eternity_*.jpg',
    'scroll_limit_break': 'scroll_limit_break_*.jpg',
    'scroll_unique': 'scroll_unique_*.jpg',
    'astral_dust': 'astral_dust_*.jpg',
    'key_shard': 'key_shard_*.jpg',
    'repair_stone': 'repair_stone_*.jpg',
    'void_elixir': 'void_elixir_*.jpg'
}

BRAIN_DIR = Path(r'C:\Users\User\.gemini\antigravity-cli\brain\dc9d20cd-9bfd-461a-87f3-eaeeeb7daa03')


def cutout(source_path):
    img = Image.open(source_path).convert('RGB').resize((512, 512), Image.Resampling.LANCZOS)
    arr = np.array(img).astype(np.int16)
    chroma = arr.max(axis=2) - arr.min(axis=2)

    # Background detection: low chroma & grey range
    is_bg = (chroma < 22) & (arr.mean(axis=2) > 40)

    # Flood fill from image boundary
    struct = np.ones((3, 3), dtype=bool)
    labels, _ = ndi.label(is_bg, structure=struct)
    border_labels = set(np.unique(np.concatenate([labels[0, :], labels[-1, :], labels[:, 0], labels[:, -1]])))
    border_labels.discard(0)
    bg_final = np.isin(labels, list(border_labels))
    bg_final = ndi.binary_dilation(bg_final, iterations=2)

    fg = ~bg_final
    rgba = np.concatenate([arr.astype(np.uint8), (fg * 255).astype(np.uint8)[..., None]], axis=2)
    rgba[~fg] = 0

    res = Image.fromarray(rgba)
    bbox = res.getbbox()
    if bbox:
        res = res.crop(bbox)

    ratio = 114 / max(res.size)
    res = res.resize((round(res.width * ratio), round(res.height * ratio)), Image.Resampling.LANCZOS)

    final = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    final.paste(res, ((128 - res.width) // 2, (128 - res.height) // 2))
    return final


def main():
    for item_id, pattern in ITEMS.items():
        matches = sorted(glob.glob(str(BRAIN_DIR / pattern)))
        if not matches:
            print(f'Warning: No match found for {item_id}')
            continue
        source = Path(matches[-1])
        dest = OUTPUT / f'{item_id}.png'
        processed = cutout(source)
        processed.save(dest, optimize=True)
        print(f'Processed {item_id} -> {dest} (128x128 RGBA)')


if __name__ == '__main__':
    main()
