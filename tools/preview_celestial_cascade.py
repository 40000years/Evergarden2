#!/usr/bin/env python3
"""Preview the Celestial Cascade from its exported block shell with the sky-whale voxel renderer.

    java -cp <classes;paper-api> tools/ExportCelestialCascade.java <dir>
    python tools/preview_celestial_cascade.py <dir>/celestial-cascade.csv --minecraft-jar <client.jar>

Plants, vines and crystals are drawn as crossed planes and slabs as half blocks; textures come from the
local Minecraft client JAR. Lighting and sky are presentation effects, not an in-game screenshot.
"""
import argparse
import csv
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
import preview_sky_whale as psw  # noqa: E402

CROSS = {'SHORT_GRASS', 'TALL_GRASS', 'FERN', 'BUSH', 'FIREFLY_BUSH', 'ALLIUM', 'AZURE_BLUET', 'OXEYE_DAISY',
         'CORNFLOWER', 'LILY_OF_THE_VALLEY', 'WHITE_TULIP', 'PINK_TULIP', 'BLUE_ORCHID', 'LILAC', 'PEONY',
         'ROSE_BUSH', 'HANGING_ROOTS', 'AMETHYST_CLUSTER', 'SEAGRASS', 'BIG_DRIPLEAF_STEM', 'VINE', 'GLOW_LICHEN',
         'CAVE_VINES', 'CAVE_VINES_PLANT', 'WILDFLOWERS'}
FLAT = {'PINK_PETALS', 'LILY_PAD', 'MOSS_CARPET', 'BIG_DRIPLEAF', 'SPORE_BLOSSOM'}
GLOW = {'CAVE_VINES|LIT', 'CAVE_VINES_PLANT|LIT', 'GLOW_LICHEN', 'AMETHYST_CLUSTER', 'SEA_LANTERN', 'SHROOMLIGHT',
        'END_ROD', 'LANTERN'}
TINT = {'SHORT_GRASS', 'TALL_GRASS', 'FERN', 'BUSH', 'LILY_PAD', 'VINE', 'GRASS_BLOCK'}
RENAME = {'CAVE_VINES_PLANT|LIT': 'cave_vines_plant_lit', 'CAVE_VINES|LIT': 'cave_vines_lit',
          'POINTED_DRIPSTONE': 'pointed_dripstone_down_tip', 'TALL_GRASS': 'tall_grass_top', 'LILAC': 'lilac_top',
          'PEONY': 'peony_top', 'ROSE_BUSH': 'rose_bush_top', 'BIG_DRIPLEAF': 'big_dripleaf_top',
          'BIRCH_WOOD': 'birch_log', 'CHERRY_WOOD': 'cherry_log', 'BIRCH_SLAB': 'birch_planks',
          'BIRCH_SLAB|TOP': 'birch_planks', 'IRON_CHAIN': 'iron_chain'}
TOP_SIDE = {'DIRT_PATH': ('dirt_path_top', 'dirt_path_side'), 'QUARTZ_PILLAR': ('quartz_pillar_top', 'quartz_pillar'),
            'CHISELED_QUARTZ_BLOCK': ('chiseled_quartz_block_top', 'chiseled_quartz_block'),
            'STRIPPED_BIRCH_LOG': ('stripped_birch_log_top', 'stripped_birch_log')}


class Textures(psw.Textures):
    def get(self, material, axis):
        key = (material, axis)
        if key in self.cache:
            return self.cache[key]
        base = material.split('|')[0]
        name = None
        if material in RENAME:
            name = RENAME[material]
        elif base in RENAME:
            name = RENAME[base]
        elif base in TOP_SIDE:
            name = TOP_SIDE[base][0 if axis == 1 else 1]
        if name is None:
            tex = super().get(base, axis)
        else:
            tex = None
            for candidate in (name, name.replace('iron_chain', 'chain')):
                try:
                    raw = self.jar.read(f'assets/minecraft/textures/block/{candidate}.png')
                    img = psw.Image.open(psw.io.BytesIO(raw)).convert('RGBA')
                    img = img.crop((0, 0, img.width, img.width)).resize((16, 16), psw.Image.Resampling.NEAREST)
                    tex = psw.np.asarray(img).astype(psw.np.float32)
                    break
                except KeyError:
                    continue
            if tex is None:
                tex = super().get(base, axis)
        tex = tex.copy()
        if base in TINT and base != 'GRASS_BLOCK':
            tex[:, :, :3] *= psw.np.array([0.49, 0.72, 0.29])
        if 'LEAVES' not in base and base in CROSS | FLAT:
            pass
        self.cache[key] = tex
        return tex


def cuboids(material, position, blocks):
    base = material.split('|')[0]
    if base in CROSS:
        h = .55 if base in ('ALLIUM', 'AZURE_BLUET', 'OXEYE_DAISY', 'CORNFLOWER', 'LILY_OF_THE_VALLEY', 'WHITE_TULIP',
                            'PINK_TULIP', 'BLUE_ORCHID', 'AMETHYST_CLUSTER') else .85 if base in ('SHORT_GRASS', 'FERN', 'BUSH') else 1.
        return [(0., 0., .47, 1., h, .53), (.47, 0., 0., .53, h, 1.)]
    if base in FLAT:
        return [(0., 0., 0., 1., .07, 1.)] if base != 'SPORE_BLOSSOM' else [(.1, .93, .1, .9, 1., .9)]
    if base == 'POINTED_DRIPSTONE':
        return [(.3, 0., .3, .7, 1., .7)]
    if material.endswith('_SLAB|TOP'):
        return [(0., .5, 0., 1., 1., 1.)]
    if material.endswith('_STAIRS|TOP'):
        return [(0., .5, 0., 1., 1., 1.), (.5, 0., 0., 1., .5, 1.)]
    if base == 'DIRT_PATH':
        return [(0., 0., 0., 1., 15 / 16, 1.)]
    return psw_cuboids(base, position, blocks)


psw_cuboids = psw.cuboids
psw.cuboids = cuboids
psw.EMISSION.update(GLOW)
psw.NOT_SOLID.update(CROSS | FLAT | {'POINTED_DRIPSTONE', 'CAVE_VINES|LIT', 'CAVE_VINES_PLANT|LIT', 'DIRT_PATH'})


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('csv', type=Path)
    ap.add_argument('--minecraft-jar', type=Path, required=True)
    ap.add_argument('--output-dir', type=Path, default=Path('previews/celestial-cascade'))
    ap.add_argument('--width', type=int, default=1600)
    ap.add_argument('--view', default='hero')
    args = ap.parse_args()
    blocks = {}
    with args.csv.open() as f:
        for x, y, z, m in csv.reader(f):
            blocks[(int(x), int(y), int(z))] = m
    for p, m in list(blocks.items()):
        if m.endswith('|TOP') and (m.endswith('_SLAB|TOP') or m.endswith('_STAIRS|TOP')):
            psw.NOT_SOLID.add(m)
        if m.endswith('_SLAB') or m.endswith('_STAIRS'):
            psw.NOT_SOLID.add(m)
    renderer = psw.Renderer(blocks, Textures(args.minecraft_jar))
    w = args.width
    views = {
        'hero': dict(out='celestial-cascade-hero.png', title='THE CELESTIAL CASCADE',
                     subtitle='The highest landmark in Evergarden: crown island at y 272, Heart of the Sky to y 318.',
                     azimuth=32, elevation=17, width=w, height=round(w * .95)),
        'south': dict(out='celestial-cascade-south.png', title='THE GATE AND THE RISING SPRING',
                      subtitle='Bubble-column lift beside the southern promontory; the balcony is a safe dive to its pool.',
                      azimuth=-12, elevation=12, width=w, height=round(w * .8)),
        'north': dict(out='celestial-cascade-north.png', title='THE SPRING CRAG',
                      subtitle='The spring leaves its grotto in two terrace falls; the rope bridge leads to the crystal islet.',
                      azimuth=200, elevation=14, width=w, height=round(w * .8)),
        'crown': dict(out='celestial-cascade-crown.png', title='THE CROWN ISLAND',
                      subtitle='Spring grotto, terraces, mirror lake, rivers, and the moated Temple of the Source.',
                      azimuth=150, elevation=42, width=w, height=round(w * .75), focus=(0, 280, 2), span=130),
        'temple': dict(out='celestial-cascade-temple.png', title='TEMPLE OF THE SOURCE',
                       subtitle='Three treasure chests rest on gold beneath the ribbed dome and the Heart of the Sky.',
                       azimuth=20, elevation=16, width=w, height=round(w * .75), focus=(0, 292, 20), span=58),
        'under': dict(out='celestial-cascade-under.png', title='BENEATH THE CROWN',
                      subtitle='Glow-berry curtains, dripstone, roots, and opened amethyst geodes.',
                      azimuth=300, elevation=-14, width=w, height=round(w * .8)),
    }
    for name in args.view.split(','):
        v = dict(views[name])
        out = args.output_dir / v.pop('out')
        renderer.render(out, v.pop('title'), v.pop('subtitle'), shadows=True, **v)


if __name__ == '__main__':
    main()
