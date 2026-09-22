#!/usr/bin/env python3
"""Preview the exact new landmark block exports using the existing voxel renderer."""
import argparse
import csv
from pathlib import Path
from preview_sky_whale import Renderer, Textures


class LandmarkTextures(Textures):
    def get(self, material, axis):
        return super().get(material.removeprefix('WAXED_'), axis)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('exports', type=Path)
    parser.add_argument('--minecraft-jar', type=Path)
    parser.add_argument('--output-dir', type=Path, default=Path('previews'))
    parser.add_argument('--width', type=int, default=1400)
    args = parser.parse_args()
    args.output_dir.mkdir(parents=True, exist_ok=True)
    for name, title, angle in [('observatory', 'THE CELESTIAL OBSERVATORY', 315),
                               ('hanging-garden', 'THE HANGING GARDEN', 35)]:
        with (args.exports / f'{name}.csv').open() as source:
            blocks = {(int(x), int(y), int(z)): material for x, y, z, material in csv.reader(source)}
        print(f'{name}: {len(blocks):,} actual blocks', flush=True)
        Renderer(blocks, LandmarkTextures(args.minecraft_jar)).render(
            args.output_dir / f'{name}-block-preview.png', title,
            'Actual generated blocks; simulated sky and lighting. Not a game screenshot.',
            width=args.width, height=round(args.width * 1.1),
            azimuth=angle, elevation=19, shadows=False)


if __name__ == '__main__':
    main()
