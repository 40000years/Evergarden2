"""Render the twelve actual generated small destinations; never invent block geometry."""
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageDraw
import render_biome_scenery as renderer


def shaped_cuboids(original, states):
    """Use exported live block states for roofs, shutters and glazing."""
    def cuboids(material, position, blocks):
        text = states.get(','.join(map(str, position)), '')
        properties = dict(p.split('=', 1) for p in text.partition('[')[2].rstrip(']').split(',') if '=' in p)
        if material.endswith('_WALL_BANNER'):
            return {'north': [(0.,0.,.875,1.,1.,1.)], 'south': [(0.,0.,0.,1.,1.,.125)],
                'east': [(0.,0.,0.,.125,1.,1.)], 'west': [(.875,0.,0.,1.,1.,1.)]}[properties.get('facing','south')]
        if material == 'CAKE': return [(.0625,0.,.0625,.9375,.5,.9375)]
        if material == 'STONECUTTER': return [(0.,0.,0.,1.,.5625,1.),(.4375,.5625,.125,.5625,1.,.875)]
        if material == 'LECTERN': return [(0.,0.,0.,1.,.125,1.),(.25,.125,.25,.75,.8125,.75),(0.,.8125,0.,1.,1.,1.)]
        if material == 'GRINDSTONE': return [(.1875,0.,.375,.375,.75,.625),(.625,0.,.375,.8125,.75,.625),(.25,.375,.1875,.75,.9375,.8125)]
        if material == 'BELL': return [(.1875,.1875,.1875,.8125,.375,.8125),(.3125,.375,.3125,.6875,.875,.6875)]
        if material == 'COMPOSTER': return [(0.,0.,0.,1.,.1875,1.),(0.,.1875,0.,.125,1.,1.),(.875,.1875,0.,1.,1.,1.),(.125,.1875,0.,.875,1.,.125),(.125,.1875,.875,.875,1.,1.)]
        if material == 'AMETHYST_CLUSTER': return [(.1875,0.,.1875,.8125,.4375,.8125),(.3125,.4375,.3125,.6875,.875,.6875)]
        if material == 'VINE':
            boxes=[]
            for face,box in {'north': (0.,0.,0.,1.,1.,.0625), 'south': (0.,0.,.9375,1.,1.,1.),
                'east': (.9375,0.,0.,1.,1.,1.), 'west': (0.,0.,0.,.0625,1.,1.)}.items():
                if properties.get(face)=='true': boxes.append(box)
            return boxes
        if material.endswith('_DOOR'):
            facing = properties.get('facing', 'south')
            if properties.get('open') == 'true': facing = ['north','east','south','west'][(['north','east','south','west'].index(facing)+1)%4]
            return {'north': [(0.,0.,.8125,1.,1.,1.)], 'south': [(0.,0.,0.,1.,1.,.1875)],
                'east': [(0.,0.,0.,.1875,1.,1.)], 'west': [(.8125,0.,0.,1.,1.,1.)]}[facing]
        if material.endswith('_BED'): return [(0.,.1875,0.,1.,.5625,1.)]
        if material == 'RAIL': return [(0.,0.,0.,1.,.0625,1.)]
        if material == 'FLOWER_POT' or material.startswith('POTTED_'):
            boxes = [(.3125,0.,.3125,.6875,.375,.6875)]
            if material.startswith('POTTED_'): boxes += [(.4375,.375,.1875,.5625,.875,.8125),(.1875,.375,.4375,.8125,.875,.5625)]
            return boxes
        if material in ('WHEAT','CARROTS','FERN','BROWN_MUSHROOM','RED_MUSHROOM','ALLIUM','PINK_TULIP','WHITE_TULIP','BLUE_ORCHID','ROSE_BUSH'):
            height = .55 if 'MUSHROOM' in material else .85
            return [(.1875,0.,.4375,.8125,height,.5625),(.4375,0.,.1875,.5625,height,.8125)]
        if material == 'FARMLAND': return [(0.,0.,0.,1.,.9375,1.)]
        if material == 'BREWING_STAND': return [(.125,0.,.125,.875,.125,.875),(.4375,.125,.4375,.5625,.875,.5625),(.1875,.125,.1875,.3125,.5,.3125),(.6875,.125,.6875,.8125,.5,.8125)]
        if material == 'CAULDRON': return [(0.,0.,0.,1.,.3125,1.),(0.,.3125,0.,.125,1.,1.),(.875,.3125,0.,1.,1.,1.),(.125,.3125,0.,.875,1.,.125),(.125,.3125,.875,.875,1.,1.)]
        if material.endswith('_STAIRS'):
            upper = properties.get('half') == 'top'
            base = (0., .5 if upper else 0., 0., 1., 1. if upper else .5, 1.)
            lo, hi = [0., 0. if upper else .5, 0.], [1., .5 if upper else 1., 1.]
            facing = properties.get('facing', 'east')
            if facing == 'east': lo[0] = .5
            elif facing == 'west': hi[0] = .5
            elif facing == 'south': lo[2] = .5
            else: hi[2] = .5
            return [base, tuple(lo + hi)]
        if material.endswith('_SLAB'):
            kind = properties.get('type', 'bottom')
            return [(0., .5 if kind == 'top' else 0., 0., 1., .5 if kind == 'bottom' else 1., 1.)]
        if material.endswith('_TRAPDOOR'):
            if properties.get('open') == 'true':
                side = properties.get('facing', 'north')
                return {'north': [(0.,0.,.8125,1.,1.,1.)], 'south': [(0.,0.,0.,1.,1.,.1875)],
                    'east': [(0.,0.,0.,.1875,1.,1.)], 'west': [(.8125,0.,0.,1.,1.,1.)]}[side]
            return [(0., .8125 if properties.get('half') == 'top' else 0., 0., 1., 1. if properties.get('half') == 'top' else .1875, 1.)]
        if material.endswith('_PANE'):
            boxes = [(.4375,0.,.4375,.5625,1.,.5625)]
            for face, box in {'north': (.4375,0.,0.,.5625,1.,.5), 'south': (.4375,0.,.5,.5625,1.,1.),
                'west': (0.,0.,.4375,.5,1.,.5625), 'east': (.5,0.,.4375,1.,1.,.5625)}.items():
                if properties.get(face) == 'true': boxes.append(box)
            return boxes
        if material.endswith('_WALL'):
            boxes = [(.25,0.,.25,.75,1.,.75)]
            for face, box in {'north': (.3125,0.,0.,.6875,.8125,.5), 'south': (.3125,0.,.5,.6875,.8125,1.),
                'west': (0.,0.,.3125,.5,.8125,.6875), 'east': (.5,0.,.3125,1.,.8125,.6875)}.items():
                if properties.get(face, 'none') != 'none': boxes.append(box)
            return boxes
        return original(material, position, blocks)
    return cuboids

TITLES = {
    'blossom-pavilion': 'BLOSSOM PAVILION', 'gardener-cottage': 'GARDENER COTTAGE',
    'spring-well': 'SPRING WELL', 'expedition-camp': 'EXPEDITION CAMP',
    'frost-chapel': 'FROST CHAPEL', 'ice-watch': 'ICE WATCH',
    'root-lodge': 'ROOT LODGE', 'moss-ruins': 'MOSS RUINS', 'herbalist-hut': 'HERBALIST HUT',
    'crescent-archive': 'CRESCENT ARCHIVE', 'star-gaze': 'STAR GAZE', 'moon-gate': 'MOON GATE',
}
MEDIUM_TITLES = {
    'orchard-estate': 'ORCHARD ESTATE', 'rose-conservatory': 'ROSE CONSERVATORY',
    'garden-watermill': 'GARDEN WATERMILL', 'travelers-inn': 'CARAVAN WAYSTATION', 'hedge-sanctuary': 'HEDGE SANCTUARY',
    'alpine-lodge': 'ALPINE PASS SHELTER', 'frozen-bathhouse': 'TERRACED HOT SPRINGS', 'icebound-outpost': 'ICEBOUND OUTPOST',
    'glacier-mine': 'GLACIER QUARRY', 'aurora-hall': 'AURORA STONE CIRCLE',
    'woodland-manor': 'RECLAIMED MANOR RUIN', 'canopy-refuge': 'CANOPY REFUGE', 'mushroom-hamlet': 'FUNGAL GATHERING CAMP',
    'overgrown-aqueduct': 'OVERGROWN AQUEDUCT', 'rootbound-shrine': 'ROOTBOUND SHRINE',
    'moon-market': 'MOON MARKET', 'astral-monastery': 'ASTRAL MONASTERY', 'tide-observatory': 'TIDE OBSERVATORY',
    'fallen-amphitheatre': 'FALLEN AMPHITHEATRE', 'lunar-bath-garden': 'LUNAR BATH GARDEN',
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('exports', type=Path)
    parser.add_argument('--output-dir', type=Path, default=Path('previews/living-biomes/structures'))
    parser.add_argument('--minecraft-jar', type=Path)
    parser.add_argument('--camera', type=float, default=45)
    parser.add_argument('--size', choices=('small','medium'), default='small')
    parser.add_argument('--cutaway', action='store_true', help='Hide roofs and front walls in the image to show actual interior blocks.')
    args = parser.parse_args(); args.output_dir.mkdir(parents=True, exist_ok=True)
    renderer.PALETTE.update({'CHERRY_PLANKS': (222, 169, 174), 'DARK_PRISMARINE': (54, 102, 96),
        'WAXED_OXIDIZED_COPPER': (70, 155, 132), 'WAXED_CUT_COPPER': (183, 108, 65),
        'CHERRY_LOG': (102, 62, 60), 'STRIPPED_CHERRY_LOG': (214, 158, 157),
        'SPRUCE_PLANKS': (126, 91, 54), 'SPRUCE_LOG': (75, 56, 35),
        'STRIPPED_SPRUCE_LOG': (123, 89, 53), 'DARK_OAK_PLANKS': (81, 58, 34),
        'MUD_BRICKS': (142, 109, 83), 'CHEST': (167, 117, 53), 'BARREL': (136, 102, 60),
        'BOOKSHELF': (147, 111, 66), 'CHERRY_SLAB': (222, 169, 174),
        'WHITE_WOOL': (236, 235, 225), 'LIGHT_BLUE_WOOL': (88, 171, 202),
        'BRICKS': (153, 94, 80), 'LANTERN': (229, 174, 78), 'GOLD_BLOCK': (237, 204, 77)})
    manifest = {'description': 'Actual generated chunk blocks; material colors and simulated lighting, not game screenshots.', 'structures': []}
    native = textures = original_cuboids = None
    if args.minecraft_jar:
        import preview_sky_whale as native
        native.PALETTE.update(renderer.PALETTE)
        textures = native.Textures(args.minecraft_jar)
        original_cuboids = native.cuboids
        manifest['description'] = 'Actual generated chunk blocks with local vanilla textures and simulated lighting; not game screenshots.'
    pictures = []
    checkpoint = args.exports.parent / 'living-checkpoint.txt'
    if not checkpoint.is_file(): checkpoint = args.exports.parent / 'preview-sites.txt'
    rotations = {}
    if checkpoint.is_file():
        for line in checkpoint.read_text(encoding='utf8').splitlines():
            row = line.split(',', 5)
            rotations[row[0]] = int(row[4])
    titles = MEDIUM_TITLES if args.size == 'medium' else TITLES
    for kind, title in titles.items():
        source = args.exports / (kind + '.csv'); blocks = renderer.load_blocks(source)
        if args.cutaway:
            rotation = rotations.get(kind, 0)
            ceiling = {'blossom-pavilion': 5, 'gardener-cottage': 4, 'expedition-camp': 3,
                'frost-chapel': 7, 'ice-watch': 14, 'root-lodge': 7, 'herbalist-hut': 4, 'crescent-archive': 7}.get(kind)
            if args.size == 'medium': ceiling = {'orchard-estate': 4, 'rose-conservatory': 4,
                'garden-watermill': 4, 'travelers-inn': 4, 'hedge-sanctuary': 4, 'alpine-lodge': 10,
                'frozen-bathhouse': 5, 'canopy-refuge': 11, 'icebound-outpost': 5, 'glacier-mine': 5,
                'woodland-manor': 4, 'mushroom-hamlet': 5, 'overgrown-aqueduct': 7, 'rootbound-shrine': 6,
                'moon-market': 4, 'astral-monastery': 6, 'tide-observatory': 5,
                'fallen-amphitheatre': 4, 'lunar-bath-garden': 4}.get(kind, 5)
            def keep(point):
                x, y, z = point
                x, z = [(x,z), (z,-x), (-x,-z), (-z,x)][rotation]
                if ceiling is None or y <= 0: return True
                if y > ceiling: return False
                if args.size == 'medium':
                    if kind == 'orchard-estate' and (-13 <= x <= 1 and z == -5 or x == 0 and -16 <= z <= -5) and y > 1: return False
                    if kind == 'rose-conservatory' and z >= -6 and y > 1: return False
                    if kind == 'garden-watermill' and (z == -4 and -15 <= x <= -3 or x == -3 and -15 <= z <= -4) and y > 1: return False
                    if kind == 'travelers-inn' and (z == -9 and -21 <= x <= -7 or x == -7 and -19 <= z <= -9) and y > 1: return False
                    if kind == 'alpine-lodge' and (z == -8 and -23 <= x <= -12 or x == -12 and -16 <= z <= -8) and y > 6: return False
                    if kind == 'alpine-lodge' and (z >= 4 and y >= 4 or x >= 10 and z <= -8 and y >= 10): return False
                    if kind == 'frozen-bathhouse' and (-22 <= x <= -10 and -13 <= z <= -8 and y > 1 or z >= 0 and y >= 4): return False
                    if kind == 'icebound-outpost' and (z == -7 and x >= 9 or x == 21 and -19 <= z <= -7 or z == -9 and x <= -10) and y > 1: return False
                    if kind == 'mushroom-hamlet' and (-18 <= x <= -8 and z >= -13 or 1 <= x <= 23 and z >= -15) and y > 1: return False
                    if kind == 'astral-monastery' and x < -9 and z < -18 and (y >= 5 or z == -19 and y > 1): return False
                    if kind == 'tide-observatory' and z >= -10 and abs((x*x+(z+10)*(z+10))**.5-10.5)<1 and y > 1: return False
                    if kind == 'fallen-amphitheatre' and z == -8 and abs(x) >= 14 and y > 1: return False
                    return True
                if kind == 'ice-watch': return not (z >= -2 and x <= 2 and y > 0)
                if kind == 'root-lodge': return not (z >= -1 and x < 4 and y > 0)
                if kind == 'crescent-archive': return not (z >= 1 and -8 <= x <= 8 and y > 0)
                if kind == 'gardener-cottage': return not ((z >= 3 and x <= 3 or z >= -1 and 3 < x <= 9) and y > 0)
                return not (z >= 2 and y > 0)
            filtered = {}
            for point, material in blocks.items():
                if not keep(point): continue
                if args.size == 'medium':
                    x, y, z = point
                    x, z = [(x,z), (z,-x), (-x,-z), (-z,x)][rotation]
                    if material.endswith('_WOOL') and y >= 4: continue
                    if kind == 'woodland-manor' and -5 <= x <= 5 and -24 <= z <= -11 and y >= 0: continue
                    if kind == 'fallen-amphitheatre' and -6 <= x <= 6 and -27 <= z <= -20 and y >= 0: continue
                filtered[point] = material
            blocks = filtered
        target = args.output_dir / (kind + '.png')
        camera = args.camera - rotations.get(kind, 0) * 90
        subtitle = '2-3 CACHES / USEFUL SUPPLIES / RARE OVER-CAP ENCHANTMENTS / IRON TIER OR BELOW'
        if args.size == 'medium': subtitle = '5-6 CACHES / 50% MORE SUPPLIES / SAME EQUIPMENT AND ENCHANT QUALITY'
        if args.cutaway: subtitle = 'INTERIOR CUTAWAY / UPPER BLOCKS AND FRONT WALLS HIDDEN FOR INSPECTION'
        if native:
            state_file = source.with_suffix('.states.json')
            native.cuboids = shaped_cuboids(original_cuboids, json.loads(state_file.read_text()) if state_file.exists() else {})
            native.Renderer(blocks, textures).render(target, title, subtitle, width=1000, height=800, azimuth=camera, elevation=26, shadows=False)
            with Image.open(target) as rendered:
                picture = rendered.convert('RGB')
            renderer.presentation(picture, title, subtitle, len(blocks), 'VANILLA TEXTURE PREVIEW')
            picture.save(target)
        else:
            renderer.render_fast(blocks, target, title, subtitle, 1000, camera, 26)
        pictures.append(target)
        manifest['structures'].append({'kind': kind, 'image': target.name,
            'source_sha256': hashlib.sha256(source.read_bytes()).hexdigest(), 'blocks': len(blocks)})
    columns = 5 if args.size == 'medium' else 3
    tile_w, tile_h = (520,416) if args.size == 'medium' else (600,480)
    sheet = Image.new('RGB', (tile_w * columns, tile_h * 4 + 90), '#edf2f6')
    draw = ImageDraw.Draw(sheet)
    draw.text((24, 16), 'EVERGARDEN / ' + ('INTERIOR CUTAWAYS' if args.cutaway else 'TWENTY MEDIUM DESTINATIONS' if args.size == 'medium' else 'TWELVE DETAILED DESTINATIONS'), fill='#293c54', font=renderer.font(30, True))
    draw.text((24, 53), 'Generated block geometry / ' + ('Five medium destinations per biome' if args.size == 'medium' else 'Three small destinations per biome'), fill='#607087', font=renderer.font(19))
    for index, picture in enumerate(pictures):
        with Image.open(picture) as image:
            sheet.paste(image.resize((tile_w, tile_h), Image.Resampling.LANCZOS), ((index % columns) * tile_w, 90 + (index // columns) * tile_h))
    sheet.save(args.output_dir / 'overview.png')
    if textures and textures.jar:
        textures.jar.close()
    (args.output_dir / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf8')
    print(args.output_dir / 'overview.png')


if __name__ == '__main__':
    main()
