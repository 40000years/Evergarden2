"""Art previews of the actual restoration shrine block exports, not game screenshots."""
import argparse
import csv
from pathlib import Path
from preview_landmarks import LandmarkTextures
from preview_sky_whale import Renderer, PALETTE

PALETTE.update({
    'MOSSY_STONE_BRICKS': (99, 118, 84), 'MANGROVE_ROOTS': (99, 75, 48),
    'POLISHED_DEEPSLATE': (60, 60, 68),
    'CUT_COPPER': (184, 109, 79), 'EXPOSED_COPPER': (145, 135, 98),
    'CYAN_STAINED_GLASS': (39, 157, 157), 'CHISELED_QUARTZ_BLOCK': (214, 211, 199),
    'LODESTONE': (118, 123, 134),
})

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('exports', type=Path)
    parser.add_argument('--minecraft-jar', type=Path)
    parser.add_argument('--output-dir', type=Path, default=Path('docs/restoration-previews'))
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    for name, title in [('whale', 'WHALE / CRYSTAL HEART'), ('garden', 'GARDEN / LUMINOUS ROOTS'),
                        ('observatory', 'OBSERVATORY / STAR LENS')]:
        with (args.exports / f'{name}.csv').open() as source:
            blocks = {(int(x), int(y), int(z)): material for x, y, z, material in csv.reader(source)}
        Renderer(blocks, LandmarkTextures(args.minecraft_jar)).render(
            args.output_dir / f'{name}.png', title,
            'Actual altar blocks; simulated lighting. Not a game screenshot.',
            width=1200, height=1000, azimuth=35, elevation=25, shadows=False)

if __name__ == '__main__':
    main()
