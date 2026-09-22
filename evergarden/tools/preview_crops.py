"""Render the procedural crop artwork for review, without a game client."""
from pathlib import Path
from crop_assets import CROPS, draw_seed, draw_food, draw_crop_stage, detailed_pixels
from build_packs import png

out = Path(__file__).resolve().parents[1] / 'art' / 'crop-refresh'
out.mkdir(parents=True, exist_ok=True)
# Each row: seed, produce, then the three growth stages. Six crops per sheet.
for page in range(5):
    side = 768
    canvas = [[(23, 31, 35, 255) for _ in range(side)] for _ in range(side)]
    names = []
    for row, crop in enumerate(CROPS[page*6:page*6+6]):
        names.append(crop['title'])
        samples = [(draw_seed(crop), 'seed'), (draw_food(crop), 'food')]
        samples += [(draw_crop_stage(crop, stage), 'plant') for stage in range(3)]
        for col, (pixels, kind) in enumerate(samples):
            pixels = detailed_pixels(pixels, crop, kind)
            for y in range(64):
                for x in range(64):
                    if pixels[y][x][3]:
                        for dy in range(2):
                            for dx in range(2):
                                canvas[row*128+y*2+dy][64+col*128+x*2+dx] = pixels[y][x]
    png(out / f'tier-{page+1}.png', canvas, side)
    print(f'tier-{page+1}.png: ' + ', '.join(names))
