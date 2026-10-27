"""Hand-shaded pet textures: one painted region for every cube face.

Faces are packed into one 512x512 atlas per model at four texels per Blockbench
unit (64 per block), falling back to three or two only if a model would not fit.
Each face gets a soft top light, a shaded lower edge, crisp outlines and a
pattern for its material: fur strokes, feather scallops, stone cracks, wood
grain, leaf clusters, scales, crystal facets or metal sheen. Eyes keep a lid
shadow and stay unlit.

Shading only varies from top to bottom of a side face and is mirror-symmetric
across it, and top faces are symmetric both ways. Java, the Bedrock exporter
(which mirrors X and turns top faces round) and the previews therefore all show
the same painting.
"""
import random

from PIL import Image

SIZE = 512
FACES = ('north', 'south', 'east', 'west', 'up', 'down')
# Horizontal and vertical texture axes of each face, as Blockbench maps them.
AXES = {'north': (0, 1), 'south': (0, 1), 'east': (2, 1), 'west': (2, 1), 'up': (0, 2), 'down': (0, 2)}

# Palette names that need a specific treatment. Anything else is classified by the
# words in its name, then painted as plain shaded colour.
KINDS = {
    'eye': 'eye', 'pupil': 'glow', 'shine': 'glow', 'glint': 'glow', 'blush': 'glow',
    'rune': 'glow', 'spots': 'glow', 'mana': 'glow', 'star': 'metal', 'amber': 'crystal',
    'gold': 'metal', 'gold_light': 'metal', 'metal': 'metal', 'silver': 'metal', 'beak': 'skin',
    'fur': 'fur', 'white': 'fur', 'lilac': 'fur', 'velvet': 'fur', 'charcoal': 'fur', 'cream': 'fur',
    'muzzle': 'fur', 'navy': 'feather', 'feather': 'feather', 'tip': 'feather', 'belly': 'feather',
    'face': 'feather', 'stone': 'stone', 'slate': 'stone', 'edge': 'stone', 'light': 'stone', 'dark': 'stone',
    'wood': 'wood', 'bark': 'wood', 'cut': 'wood', 'panel': 'wood', 'moss': 'leaf', 'leaf': 'leaf',
    'light_leaf': 'leaf', 'deep_leaf': 'leaf', 'shell': 'shell', 'emerald': 'crystal', 'crystal': 'crystal',
    'ice': 'scale', 'pale': 'skin', 'deep': 'scale', 'horn': 'crystal', 'membrane': 'membrane',
    'glass': 'crystal', 'wing': 'crystal', 'wing_edge': 'crystal', 'potion': 'crystal', 'purple': 'crystal',
    'hair': 'hair', 'dress': 'cloth', 'teal': 'cloth', 'sand': 'skin', 'skin': 'skin', 'tooth': 'skin',
    'tongue': 'skin', 'mouth': 'glow', 'nose': 'skin', 'red': 'fur', 'wine': 'stone', 'back': 'skin',
    'cyan': 'glow', 'violet': 'glow', 'mark': 'glow', 'crimson': 'fur', 'flame': 'glow', 'flame_tip': 'glow',
    'ember': 'glow', 'leather': 'skin', 'paw': 'fur', 'fang': 'skin',
}
# A few names mean different things on different animals.
SPECIES_KINDS = {
    'crystal_tortoise': {'skin': 'scale', 'pale': 'scale', 'light': 'crystal', 'deep': 'shell'},
    'abyssal_manta': {'navy': 'skin', 'belly': 'skin'},
    'chrono_fairy': {'teal': 'cloth', 'deep': 'glow'},
    'runic_golem': {'light': 'stone'},
    'blood_gargoyle': {'light': 'stone'},
    'frost_wyvern': {'pale': 'scale', 'deep': 'scale'},
    'crimson_cerberus': {'nose': 'skin'},
    'shadow_panther': {'nose': 'skin', 'teal': 'glow'},
    'mochi_slime': {'jelly': 'skin', 'jelly_deep': 'skin', 'mochi': 'skin', 'twig': 'wood'},
    'cloud_lamb': {'wool': 'fur', 'wool_shade': 'fur', 'face': 'fur', 'face_light': 'fur', 'horn': 'shell',
                   'horn_deep': 'shell', 'inner_ear': 'skin', 'ribbon': 'cloth'},
    'moon_rabbit': {'inner': 'skin', 'ribbon': 'cloth', 'mochi': 'skin', 'mochi_pink': 'skin'},
    'bloom_hedgehog': {'spine': 'hair', 'spine_dark': 'hair', 'face': 'fur', 'belly': 'fur'},
    'coral_axolotl': {'belly': 'skin', 'coral': 'stone', 'coral_orange': 'stone', 'fin': 'membrane', 'pearl': 'crystal'},
    'clover_bumble': {'fuzz': 'fur', 'fuzz_light': 'fur', 'stripe': 'fur', 'clover': 'leaf', 'clover_light': 'leaf',
                      'pollen': 'fur'},
    'aurora_penguin': {'back': 'feather', 'back_light': 'feather', 'feet': 'skin', 'cheek': 'feather', 'scarf': 'cloth',
                       'scarf_light': 'cloth'},
    'storm_raptor': {'hide': 'scale', 'hide_light': 'scale', 'belly': 'skin'},
    'magma_rhino': {'hide': 'skin', 'hide_dark': 'skin', 'plate': 'stone', 'horn_light': 'crystal'},
    'eclipse_scorpion': {'chitin': 'shell', 'chitin_light': 'shell', 'edge': 'metal', 'under': 'shell'},
    'raijin_tiger': {'stripe': 'fur', 'cloud': 'fur', 'cloud_dark': 'fur', 'drum': 'wood', 'rope': 'cloth'},
    'titan_beetle': {'sheen': 'metal', 'horn': 'metal', 'horn_tip': 'metal', 'under': 'shell', 'leg': 'shell'},
    'anubis_jackal': {'lapis': 'crystal', 'inner_ear': 'skin'},
    'bone_drake': {'bone': 'skin', 'bone_shade': 'skin', 'bone_dark': 'stone', 'horn': 'shell', 'chain': 'metal'},
    'jade_lung': {'belly': 'shell', 'mane': 'hair', 'mane_tip': 'hair', 'horn': 'wood', 'whisker': 'hair'},
    'crystal_stag': {'belly': 'fur'},
    'luna_moth': {'fluff': 'fur', 'fluff_shade': 'fur', 'wing': 'membrane', 'wing_light': 'membrane', 'antenna': 'feather'},
    'celestial_koi': {'koi_white': 'scale', 'koi_red': 'scale', 'koi_gold': 'scale', 'fin': 'membrane',
                      'fin_light': 'membrane', 'lip': 'skin', 'whisker': 'hair', 'scale_glint': 'glow'},
    'starlight_peacock': {'plume': 'feather', 'plume_light': 'feather', 'train': 'feather', 'train_dark': 'feather',
                          'eye_gold': 'metal', 'eye_blue': 'plain', 'eye_core': 'plain'},
    'aurora_jelly': {'bell': 'crystal', 'bell_light': 'crystal', 'tentacle': 'crystal'},
}
WORDS = (('eye', 'eye'), ('glow', 'glow'), ('rune', 'glow'), ('gold', 'metal'), ('metal', 'metal'),
         ('fur', 'fur'), ('feather', 'feather'), ('wing', 'membrane'), ('stone', 'stone'), ('rock', 'stone'),
         ('wood', 'wood'), ('bark', 'wood'), ('leaf', 'leaf'), ('moss', 'leaf'), ('scale', 'scale'),
         ('shell', 'shell'), ('crystal', 'crystal'), ('gem', 'crystal'), ('ice', 'crystal'), ('mane', 'hair'),
         ('hair', 'hair'), ('skin', 'skin'))


def kind(species, material):
    material = material.lower()
    special = SPECIES_KINDS.get(species, {})
    if material in special:
        return special[material]
    if material in KINDS:
        return KINDS[material]
    for word, value in WORDS:
        if word in material:
            return value
    return 'plain'


def pack(sizes, gutter=1):
    """Shelf-pack rectangles (tallest first) into the atlas; None when they do not fit."""
    order = sorted(range(len(sizes)), key=lambda i: (-sizes[i][1], -sizes[i][0]))
    spots = [None] * len(sizes)
    x = y = shelf = 0
    for i in order:
        w, h = sizes[i][0] + 2 * gutter, sizes[i][1] + 2 * gutter
        if x + w > SIZE:
            x, y, shelf = 0, y + shelf, 0
        if w > SIZE or y + h > SIZE:
            return None
        spots[i] = (x + gutter, y + gutter)
        x += w
        shelf = max(shelf, h)
    return spots


def _shade(rgb, f):
    # Shadows cool a little and lights warm a little, rather than only darkening.
    r, g, b = rgb
    if f < 1:
        r, g, b = r * f, g * f, b * f ** .82
    else:
        r, g, b = r * f, g * f, b * f ** .9
    return tuple(max(0, min(255, round(v))) for v in (r, g, b)) + (255,)


def _base(w, h, face, material, rng):
    side = face not in ('up', 'down')
    flat = material in ('glow',)
    grid = []
    for y in range(h):
        row = []
        t = (y + .5) / h
        for x in range(w):
            if flat:
                f = 1
            elif material == 'eye':
                # A lid shadow above, a lit lower iris: eyes read rounder and brighter.
                f = .78 + .4 * t if side else 1
            elif side:
                f = 1.07 - .17 * t
            else:
                f = 1.07 if face == 'up' else .9
            if not flat and material != 'eye' and w >= 4 and h >= 4:
                edge_x, edge_y = min(x, w - 1 - x), min(y, h - 1 - y)
                if side and y == 0:
                    f *= 1.09
                elif side and y == h - 1:
                    f *= .84
                elif edge_x == 0:
                    f *= .92
                elif not side and edge_y == 0:
                    f *= 1.05 if face == 'up' else .95
            if not flat:
                f *= 1 + rng.uniform(-.022, .022)
            row.append(f)
        grid.append(row)
    return grid


def _mul(grid, x, y, f):
    if 0 <= y < len(grid) and 0 <= x < len(grid[0]):
        grid[y][x] *= f


def _pattern(grid, material, face, rng):
    h, w = len(grid), len(grid[0])
    area = w * h
    side = face not in ('up', 'down')
    if material in ('fur', 'hair'):
        # Short hanging strokes; hair runs longer and finer.
        for _ in range(max(1, area // (10 if material == 'hair' else 15))):
            x, y = rng.randrange(w), rng.randrange(h)
            f = rng.choice((.88, .92, 1.08, 1.11))
            for k in range(rng.randint(2, 6 if material == 'hair' else 4)):
                _mul(grid, x, y + k, f)
    elif material == 'feather':
        # Overlapping feather tips: little scallops with a lit rim.
        for row in range(2, h, 4):
            offset = 2 if (row // 4) % 2 else 0
            for col in range(offset - 2, w, 4):
                for dx, dy in ((0, 0), (1, 1), (2, 1), (3, 0)):
                    _mul(grid, col + dx, row + dy, .88)
                _mul(grid, col + 1, row, 1.07)
                _mul(grid, col + 2, row, 1.07)
    elif material in ('scale', 'shell'):
        step = 5 if material == 'scale' else 7
        for row in range(1, h, step - 1):
            offset = step // 2 if (row // (step - 1)) % 2 else 0
            for col in range(offset - step, w, step):
                for dx in range(step):
                    dip = 1 if 0 < dx < step - 1 else 0
                    _mul(grid, col + dx, row + dip, .85)
                    _mul(grid, col + dx, row + dip - 1, 1.08)
        if material == 'shell':
            for _ in range(area // 60):
                _mul(grid, rng.randrange(w), rng.randrange(h), 1.12)
    elif material == 'stone':
        for _ in range(max(1, area // 22)):
            _mul(grid, rng.randrange(w), rng.randrange(h), rng.choice((.9, .94, 1.06, 1.1)))
        for _ in range(area // 110):
            x, y = rng.randrange(w), rng.randrange(h)
            for _ in range(rng.randint(3, 7)):
                _mul(grid, x, y, .78)
                x += rng.choice((-1, 0, 1))
                y += rng.choice((0, 1, 1))
    elif material == 'wood':
        spacing = rng.randint(3, 5)
        for start in range(1, w, spacing):
            x = start
            for y in range(h):
                _mul(grid, x, y, .87)
                if rng.random() < .18:
                    x = max(0, min(w - 1, x + rng.choice((-1, 1))))
        for _ in range(area // 160):
            x, y = rng.randrange(1, max(2, w - 1)), rng.randrange(1, max(2, h - 1))
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                _mul(grid, x + dx, y + dy, .8)
    elif material == 'leaf':
        for _ in range(max(1, area // 9)):
            x, y = rng.randrange(w), rng.randrange(h)
            f = rng.choice((.84, .9, 1.1, 1.16))
            for dx, dy in ((0, 0), (1, 0), (0, 1)):
                _mul(grid, x + dx, y + dy, f)
    elif material == 'crystal':
        # A lit central facet between two darker ones, and a sparkle near the top.
        for y in range(h):
            for x in range(w):
                centre = abs(x - (w - 1) / 2) / max(1, w / 2)
                _mul(grid, x, y, 1.13 if centre < .3 else .92 if centre > .7 else 1)
        if w >= 3 and h >= 3:
            _mul(grid, w // 2, max(0, h // 4), 1.35)
    elif material == 'metal':
        if side and h >= 3:
            band = max(0, round(h * .28))
            for x in range(w):
                _mul(grid, x, band, 1.22)
                _mul(grid, x, h - 1, .85)
        if w >= 3 and h >= 3:
            _mul(grid, w // 2, max(0, h // 3), 1.3)
    elif material == 'membrane':
        for _ in range(max(1, w // 4)):
            x = rng.randrange(w)
            for y in range(h):
                _mul(grid, x, y, .86 if y < h * .8 else .93)
                if rng.random() < .25:
                    x = max(0, min(w - 1, x + rng.choice((-1, 1))))
    elif material == 'cloth':
        for y in range(1, h, 3):
            for x in range(w):
                _mul(grid, x, y, .95)
    elif material in ('skin', 'plain'):
        for _ in range(area // 40):
            _mul(grid, rng.randrange(w), rng.randrange(h), rng.choice((.96, 1.04)))


def _bleed(pixels, x0, y0, w, h):
    """Copy each face's border one texel outward, so mipmaps never pull in a neighbour."""
    for x in range(x0, x0 + w):
        pixels[x, y0 - 1] = pixels[x, y0]
        pixels[x, y0 + h] = pixels[x, y0 + h - 1]
    for y in range(y0 - 1, y0 + h + 1):
        pixels[x0 - 1, y] = pixels[x0, min(max(y, y0), y0 + h - 1)]
        pixels[x0 + w, y] = pixels[x0 + w - 1, min(max(y, y0), y0 + h - 1)]


def paint(species, stage, parts, palette):
    """Return the atlas and, per part, the [u1, v1, u2, v2] pixel rectangle of each face."""
    for density in (4, 3, 2):
        sizes = []
        for part in parts:
            for face in FACES:
                a, b = AXES[face]
                sizes.append((max(1, round(part['size'][a] * density)), max(1, round(part['size'][b] * density))))
        spots = pack(sizes)
        if spots is not None:
            break
    else:
        raise ValueError(f'{species}/{stage}: faces do not fit a {SIZE}px atlas')
    image = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0))
    pixels = image.load()
    uvs = []
    for index, part in enumerate(parts):
        rgb = tuple(bytes.fromhex(palette[part['material']].lstrip('#')))
        material = kind(species, part['material'])
        faces = {}
        for number, face in enumerate(FACES):
            (w, h), (x0, y0) = sizes[index * 6 + number], spots[index * 6 + number]
            rng = random.Random(f'{species}/{stage}/{index}/{face}')
            grid = _base(w, h, face, material, rng)
            _pattern(grid, material, face, rng)
            for y in range(h):
                for x in range(w):
                    pixels[x0 + x, y0 + y] = _shade(rgb, grid[y][x])
            _bleed(pixels, x0, y0, w, h)
            faces[face] = [x0, y0, x0 + w, y0 + h]
        uvs.append(faces)
    return image, uvs, density
