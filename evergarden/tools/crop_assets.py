"""Generates 32x32 pixel art textures, Java item/block models, and Bedrock/Geyser definitions for all 30 Evergarden crops."""
import math
from food_art import draw_crop_food

TEXTURE_SIZE = 64


def crop_uv(front, back):
    # Java mirrors the reverse face; Bedrock uses signed UV extents.
    return {front: {'uv': [0, 0], 'uv_size': [TEXTURE_SIZE, TEXTURE_SIZE]},
            back: {'uv': [TEXTURE_SIZE, 0], 'uv_size': [-TEXTURE_SIZE, TEXTURE_SIZE]}}


def detailed_pixels(source, info, kind):
    """Retain the original silhouette; draw new material detail at double resolution.

    This is a deterministic drawing pass, not bilinear enlargement: leaf veins,
    seed grain, skin pores and directional edge lighting occupy individual pixels.
    Alpha stays binary so both clients use the same cutout silhouette.
    """
    result = new_canvas(TEXTURE_SIZE)
    if kind == 'food':
        for y in range(TEXTURE_SIZE):
            for x in range(TEXTURE_SIZE):
                result[y][x] = source[y // 2][x // 2]
        return result
    shape = info['shape']
    for y in range(TEXTURE_SIZE):
        for x in range(TEXTURE_SIZE):
            sx, sy = x // 2, y // 2
            color = source[sy][sx]
            if not color[3]:
                continue
            r, g, b, _ = color
            green = g > r * 1.18 and g > b * 1.2
            outline = max(r, g, b) < 36
            if outline:
                # Colored ink is less harsh than the old almost-black outline.
                neighbours = [source[ny][nx] for nx, ny in
                              ((sx-1,sy),(sx+1,sy),(sx,sy-1),(sx,sy+1))
                              if 0 <= nx < 32 and 0 <= ny < 32
                              and source[ny][nx][3] and max(source[ny][nx][:3]) > 45]
                base = max(neighbours, key=lambda c: c[1]) if neighbours else info['sec']
                color = shade(base, 0.36 if (x+y) % 3 else 0.43)
            else:
                # Sub-pixel facets maintain deliberate pixel clusters.
                light = 1.0 + (0.055 if x % 2 == 0 else -0.025) + (0.035 if y % 2 == 0 else -0.015)
                color = shade(color, light)
                if green:
                    # Branching veins: pale midrib with smaller diagonal veins.
                    rib = (x + y // 3) % 13
                    if rib == 0:
                        color = blend(color, (190, 221, 124, 255), 0.28)
                    elif (x - y) % 11 == 0 and rib < 6:
                        color = blend(color, (157, 197, 95, 255), 0.20)
                    elif rib == 1:
                        color = shade(color, 0.86)
                elif kind == 'seed' or shape in ('ROOT_TUBER', 'ACORN', 'BULB_GARLIC', 'BAMBOO_STALK'):
                    # Fine fibres / papery shell grooves follow the long axis.
                    if (x + int(2 * math.sin(y / 9))) % 9 == 0:
                        color = shade(color, 0.78)
                    elif (x + 2*y) % 23 == 0:
                        color = blend(color, info['acc'], 0.30)
                elif shape in ('MUSHROOM', 'SPORE'):
                    if (x*7+y*11) % 31 < 3:
                        color = blend(color, info['acc'], 0.36)
                elif shape == 'CORN_EAR':
                    if y % 6 == 5 or x % 6 == 5:
                        color = shade(color, 0.80)
                    elif x % 6 == 1 and y % 6 == 1:
                        color = blend(color, (255, 246, 185, 255), 0.45)
                elif (x*13+y*7) % 47 == 0:
                    color = blend(color, info['acc'], 0.22)
                # Lit upper rim and contact shade on the lower rim.
                if y > 0 and not source[(y-1)//2][sx][3]:
                    color = blend(color, (225, 239, 170, 255), 0.20)
                if y < 63 and not source[(y+1)//2][sx][3]:
                    color = shade(color, 0.78)
            result[y][x] = color
    return result

CROPS = [
    # Tier 1: Common Farm (6 crops)
    {'id': 'mana_dew_berry', 'title': 'Blueberry', 'tier': 1, 'seed': 'beetroot_seeds', 'food': 'sweet_berries',
     'pri': (70, 82, 155), 'sec': (38, 46, 105), 'acc': (170, 180, 220), 'shape': 'BERRY_BUNCH'},
    {'id': 'chameleon_leaf', 'title': 'Lettuce', 'tier': 1, 'seed': 'wheat_seeds', 'food': 'dried_kelp',
     'pri': (105, 175, 65), 'sec': (50, 112, 42), 'acc': (180, 215, 100), 'shape': 'LEAF_FROND'},
    {'id': 'fairy_mushroom', 'title': 'Truffle', 'tier': 1, 'seed': 'beetroot_seeds', 'food': 'cookie',
     'pri': (125, 82, 52), 'sec': (72, 45, 30), 'acc': (175, 125, 82), 'shape': 'MUSHROOM'},
    {'id': 'magnetic_squash', 'title': 'Butternut Squash', 'tier': 1, 'seed': 'pumpkin_seeds', 'food': 'pumpkin_pie',
     'pri': (220, 155, 70), 'sec': (150, 92, 38), 'acc': (245, 195, 105), 'shape': 'MELON_SQUASH'},
    {'id': 'mountain_walker_bamboo', 'title': 'Leek', 'tier': 1, 'seed': 'wheat_seeds', 'food': 'carrot',
     'pri': (92, 165, 68), 'sec': (45, 105, 38), 'acc': (225, 230, 175), 'shape': 'BAMBOO_STALK'},
    {'id': 'lumberjack_acorn', 'title': 'Chestnut', 'tier': 1, 'seed': 'wheat_seeds', 'food': 'cookie',
     'pri': (175, 110, 55), 'sec': (100, 55, 25), 'acc': (225, 175, 105), 'shape': 'ACORN'},

    # Tier 2: Combat & Slaying (6 crops)
    {'id': 'blood_thorn_tomato', 'title': 'Tomato', 'tier': 2, 'seed': 'pumpkin_seeds', 'food': 'apple',
     'pri': (220, 30, 40), 'sec': (125, 15, 20), 'acc': (45, 45, 55), 'shape': 'ROUND_FRUIT'},
    {'id': 'frostbite_radish', 'title': 'Radish', 'tier': 2, 'seed': 'beetroot_seeds', 'food': 'carrot',
     'pri': (205, 55, 78), 'sec': (135, 28, 48), 'acc': (245, 225, 210), 'shape': 'ROOT_TUBER'},
    {'id': 'thunder_kernel_corn', 'title': 'Corn', 'tier': 2, 'seed': 'wheat_seeds', 'food': 'bread',
     'pri': (245, 205, 45), 'sec': (175, 130, 18), 'acc': (255, 235, 110), 'shape': 'CORN_EAR'},
    {'id': 'reapers_garlic', 'title': "Garlic", 'tier': 2, 'seed': 'beetroot_seeds', 'food': 'golden_carrot',
     'pri': (225, 215, 190), 'sec': (155, 135, 110), 'acc': (195, 175, 145), 'shape': 'BULB_GARLIC'},
    {'id': 'titan_pumpkin', 'title': 'Eggplant', 'tier': 2, 'seed': 'pumpkin_seeds', 'food': 'pumpkin_pie',
     'pri': (104, 48, 135), 'sec': (55, 24, 78), 'acc': (78, 145, 62), 'shape': 'ROUND_FRUIT'},
    {'id': 'kinetic_pea_pod', 'title': 'Green Peas', 'tier': 2, 'seed': 'wheat_seeds', 'food': 'sweet_berries',
     'pri': (110, 220, 40), 'sec': (50, 130, 20), 'acc': (215, 255, 75), 'shape': 'POD'},

    # Tier 3: Dimension & Survival (6 crops)
    {'id': 'twilight_grape', 'title': 'Grape', 'tier': 3, 'seed': 'melon_seeds', 'food': 'sweet_berries',
     'pri': (115, 50, 180), 'sec': (60, 20, 105), 'acc': (205, 135, 255), 'shape': 'BERRY_BUNCH'},
    {'id': 'void_feather_blossom', 'title': 'Red Cabbage', 'tier': 3, 'seed': 'torchflower_seeds', 'food': 'dried_kelp',
     'pri': (130, 68, 145), 'sec': (76, 38, 92), 'acc': (182, 120, 190), 'shape': 'BLOSSOM'},
    {'id': 'lodestone_gourd', 'title': 'Bottle Gourd', 'tier': 3, 'seed': 'melon_seeds', 'food': 'apple',
     'pri': (120, 165, 68), 'sec': (65, 108, 38), 'acc': (185, 205, 105), 'shape': 'ROUND_FRUIT'},
    {'id': 'abyssal_kelp', 'title': 'Celery', 'tier': 3, 'seed': 'wheat_seeds', 'food': 'dried_kelp',
     'pri': (88, 150, 65), 'sec': (45, 92, 38), 'acc': (165, 200, 112), 'shape': 'LEAF_FROND'},
    {'id': 'glider_spore', 'title': 'Broccoli', 'tier': 3, 'seed': 'beetroot_seeds', 'food': 'cookie',
     'pri': (52, 125, 58), 'sec': (25, 72, 34), 'acc': (98, 165, 76), 'shape': 'SPORE'},
    {'id': 'star_anise', 'title': 'Star Anise', 'tier': 3, 'seed': 'pitcher_pod', 'food': 'golden_carrot',
     'pri': (185, 125, 70), 'sec': (115, 70, 35), 'acc': (255, 240, 180), 'shape': 'STAR'},

    # Tier 4: Mining & Utility (6 crops)
    {'id': 'fortune_beet', 'title': 'Turnip', 'tier': 4, 'seed': 'beetroot_seeds', 'food': 'carrot',
     'pri': (220, 205, 188), 'sec': (145, 118, 135), 'acc': (118, 168, 68), 'shape': 'ROOT_TUBER'},
    {'id': 'demeters_melon', 'title': "Papaya", 'tier': 4, 'seed': 'melon_seeds', 'food': 'melon_slice',
     'pri': (235, 142, 45), 'sec': (155, 78, 25), 'acc': (70, 62, 32), 'shape': 'MELON_SQUASH'},
    {'id': 'prism_shard_carrot', 'title': 'Cassava', 'tier': 4, 'seed': 'pitcher_pod', 'food': 'golden_carrot',
     'pri': (172, 112, 66), 'sec': (100, 62, 38), 'acc': (225, 184, 125), 'shape': 'ROOT_TUBER'},
    {'id': 'goldleaf_herb', 'title': 'Spinach', 'tier': 4, 'seed': 'torchflower_seeds', 'food': 'golden_apple',
     'pri': (58, 145, 62), 'sec': (28, 88, 35), 'acc': (105, 185, 90), 'shape': 'LEAF_FROND'},
    {'id': 'soul_ward_bulb', 'title': 'Onion', 'tier': 4, 'seed': 'beetroot_seeds', 'food': 'golden_carrot',
     'pri': (220, 190, 125), 'sec': (150, 112, 68), 'acc': (238, 220, 175), 'shape': 'BULB_GARLIC'},
    {'id': 'chrono_pepper', 'title': 'Bell Pepper', 'tier': 4, 'seed': 'pumpkin_seeds', 'food': 'apple',
     'pri': (225, 165, 35), 'sec': (155, 92, 18), 'acc': (245, 205, 75), 'shape': 'PEPPER_CHILI'},

    # Tier 5: Mythic Arcana (6 crops)
    {'id': 'ancient_astral_root', 'title': 'Sweet Potato', 'tier': 5, 'seed': 'torchflower_seeds', 'food': 'golden_apple',
     'pri': (155, 72, 78), 'sec': (92, 42, 48), 'acc': (205, 115, 82), 'shape': 'ROOT_TUBER'},
    {'id': 'yggdrasil_sprout', 'title': 'Asparagus', 'tier': 5, 'seed': 'torchflower_seeds', 'food': 'golden_apple',
     'pri': (72, 155, 72), 'sec': (35, 95, 42), 'acc': (125, 190, 92), 'shape': 'SPROUT_TREE'},
    {'id': 'void_overcharge_fig', 'title': 'Fig', 'tier': 5, 'seed': 'pitcher_pod', 'food': 'golden_carrot',
     'pri': (125, 62, 120), 'sec': (72, 32, 72), 'acc': (195, 115, 145), 'shape': 'ROUND_FRUIT'},
    {'id': 'ethereal_mint', 'title': 'Mint', 'tier': 5, 'seed': 'wheat_seeds', 'food': 'apple',
     'pri': (72, 165, 105), 'sec': (35, 98, 62), 'acc': (135, 205, 145), 'shape': 'LEAF_FROND'},
    {'id': 'bloodburn_chili', 'title': 'Chili Pepper', 'tier': 5, 'seed': 'pumpkin_seeds', 'food': 'apple',
     'pri': (240, 45, 25), 'sec': (130, 15, 10), 'acc': (255, 190, 40), 'shape': 'PEPPER_CHILI'},
    {'id': 'omni_pomegranate', 'title': 'Pomegranate', 'tier': 5, 'seed': 'torchflower_seeds', 'food': 'golden_apple',
     'pri': (195, 42, 58), 'sec': (105, 18, 30), 'acc': (235, 105, 92), 'shape': 'ROUND_FRUIT'},
]


for c in CROPS:
    for k in ('pri', 'sec', 'acc'):
        if len(c[k]) == 3:
            c[k] = c[k] + (255,)

def blend(c1, c2, ratio):
    return tuple(int(round(a * (1.0 - ratio) + b * ratio)) for a, b in zip(c1[:3], c2[:3])) + (255,)

def shade(color, factor):
    return tuple(max(0, min(255, int(round(c * factor)))) for c in color[:3]) + (color[3] if len(color) > 3 else 255,)

def new_canvas(size=32):
    return [[(0, 0, 0, 0) for _ in range(size)] for _ in range(size)]


def draw_seed(info):
    """Three loose seeds with a visible seam and crop-specific shell colours."""
    p = new_canvas()
    shell = blend((174, 119, 61, 255), info['pri'], 0.48)
    edge = shade(shell, 0.40)
    # Separate silhouettes, pointed ends, and a hilum: no bag/ribbon/label.
    for cx, cy, angle, radius in ((11, 11, -0.55, 6), (22, 17, 0.60, 5), (10, 25, 0.85, 4)):
        co, si = math.cos(angle), math.sin(angle)
        for y in range(32):
            for x in range(32):
                dx, dy = x - cx, y - cy
                u, v = dx * co + dy * si, -dx * si + dy * co
                width = radius * 0.58 * (1 - 0.22 * v / radius)
                d = (u / width) ** 2 + (v / radius) ** 2
                if d <= 1:
                    p[y][x] = edge if d > 0.72 else shade(shell, 1.18 - u / radius * 0.35)
                    if abs(u + 0.5) < 0.6 and abs(v) < radius * 0.65:
                        p[y][x] = shade(info['sec'], 0.8)
                    if -2 < u < -0.5 and -radius * 0.55 < v < -radius * 0.2:
                        p[y][x] = blend(shell, info['acc'], 0.65)
    return p

def draw_food(info):
    """Draws a handcrafted 32x32 pixel art produce item for the given crop."""
    return draw_crop_food(info)


def draw_crop_stage(info, stage):
    """Draw a full-block, four-plane crop texture in the visual language of vanilla crops."""
    p = new_canvas()
    pri, sec, acc = info['pri'], info['sec'], info['acc']
    border = (20, 15, 25, 255)

    leaf_dark = (42, 105, 38, 255)
    leaf_mid = (67, 145, 48, 255)
    leaf_light = (105, 180, 65, 255)

    def ellipse(cx, cy, rx, ry, color, highlight=None):
        for yy in range(max(0, cy-ry), min(32, cy+ry+1)):
            for xx in range(max(0, cx-rx), min(32, cx+rx+1)):
                d=((xx-cx)/max(1,rx))**2+((yy-cy)/max(1,ry))**2
                if d <= 1:
                    # Rounded foliage/fruit with discrete lighting bands, rather
                    # than flat discs with a rectangular highlight.
                    nx, ny = (xx-cx)/max(1,rx), (yy-cy)/max(1,ry)
                    light = (-nx-ny)*0.20 + math.sqrt(max(0,1-d))*0.35
                    light = round(light*6)/6
                    body = shade(color, 0.76 + light)
                    p[yy][xx] = blend(body, highlight, max(0,light)*0.35) if highlight else body

    def leaf(x1, y1, x2, y2, color=leaf_mid, width=1):
        steps=max(abs(x2-x1),abs(y2-y1),1)
        for n in range(steps+1):
            xx=round(x1+(x2-x1)*n/steps); yy=round(y1+(y2-y1)*n/steps)
            for ox in range(-width,width+1):
                if 0<=xx+ox<32 and 0<=yy<32:p[yy][xx+ox]=color

    if stage == 0:
        # Recognisable juvenile anatomy, with stable per-species variation.
        # No mature fruit on newly planted seedlings.
        variant = next(i for i,c in enumerate(CROPS) if c['id'] == info['id'])
        offset = variant % 3 - 1
        young = blend(leaf_mid, pri, 0.25)
        shape = info['shape']
        if shape in ('MUSHROOM', 'SPORE'):
            for x,y,r in ((11+offset,24,4),(20,20+offset,5),(24-offset,27,3)):
                leaf(x,31,x,y,acc,1)
                ellipse(x,y,r,max(2,r-2),pri,shade(pri,1.25))
                p[y-1][x-1] = acc
        elif shape in ('CORN_EAR','BAMBOO_STALK','SPROUT_TREE'):
            count = 3 if shape == 'BAMBOO_STALK' else 2
            for n in range(count):
                x=10+n*5+offset
                top=13+n*3-variant%2
                leaf(x,31,x,top,young,1)
                leaf(x,26,x-4,top+4,leaf_dark,1)
                leaf(x,22,x+4,top+1,leaf_light,0)
        elif shape in ('ROOT_TUBER','BULB_GARLIC'):
            ellipse(16,30,3,2,blend(pri,sec,.3),acc)
            for n in range(3+variant%2):
                x=6+n*6+offset
                leaf(16,30,x,17+abs(16-x)//3,young,1)
                if shape == 'ROOT_TUBER':
                    ellipse(x,19+abs(16-x)//3,2,4,young,leaf_light)
        elif shape in ('LEAF_FROND','BLOSSOM'):
            for n in range(5):
                angle=math.radians(195+n*38+offset*6)
                x=16+round(math.cos(angle)*8)
                y=27+round(math.sin(angle)*8)
                leaf(16,31,x,y,leaf_dark,1)
                ellipse(x,y,3+variant%2,4,young,blend(leaf_light,acc,.25))
        elif shape in ('POD','MELON_SQUASH'):
            leaf(12,31,18+offset,19,young,1)
            ellipse(9,24,4,3,young,leaf_light)
            ellipse(22,20,4,3,young,leaf_light)
            leaf(18,23,24,16,leaf_light,0)
            leaf(24,16,26,18,leaf_mid,0)
        else:
            top=16+variant%4
            leaf(16,31,16+offset,top,young,1)
            for x,y in ((9-offset,24),(22+offset,20),(13,top)):
                leaf(16,28,x,y,leaf_dark,0)
                ellipse(x,y,3 if shape=='BERRY_BUNCH' else 2,2+variant%2,young,leaf_light)

    elif stage == 1:
        # Three young stems broaden naturally before the mature full-block canopy.
        for x,top in ((10,12),(16,6),(22,11)):
            leaf(x,31,x,top,leaf_mid,1)
            leaf(x,22,x-7,15,leaf_dark,1)
            leaf(x,18,x+7,12,leaf_light,1)
            ellipse(x,top,3,3,blend(leaf_light,pri,.35),leaf_light)

    elif stage == 2:
        shape=info['shape']

        if shape == 'CORN_EAR':
            # Tall stems and broad alternating leaves, like mature vanilla wheat.
            for x in (11,16,21):
                leaf(x,31,x,3,leaf_mid,1)
                for yy in range(7,18):
                    if yy%2==0:p[yy][x]=acc
                ellipse(x+(3 if x!=21 else -3),14,2,6,pri,shade(pri,1.2))
                leaf(x,25,x-7,18,leaf_dark,1);leaf(x,22,x+7,15,leaf_light,1)
        elif shape in ('MELON_SQUASH','ROUND_FRUIT'):
            # Ground vine with several large fruits; fills the lower block like potatoes.
            leaf(1,28,30,24,leaf_mid,1);leaf(6,26,13,12,leaf_dark,1);leaf(24,25,19,10,leaf_mid,1)
            for fx,fy,rx,ry in ((8,23,5,5),(22,22,5,6),(15,16,4,5)):
                ellipse(fx,fy,rx,ry,pri,shade(pri,1.25));p[fy][fx]=acc;p[fy+2][fx]=sec
            for fx,fy in ((3,19),(13,26),(28,17),(18,28)):
                leaf(fx,fy,fx+(3 if fx<16 else -3),fy-5,leaf_light,1)
        elif shape in ('MUSHROOM','SPORE'):
            # A cluster of overlapping caps avoids the sparse single-mushroom look.
            for cx,cy,rx,ry in ((8,19,7,4),(23,18,7,5),(15,10,8,5),(16,25,6,4)):
                for yy in range(cy,30):
                    if abs(yy-cy)<7:
                        p[yy][cx]=shade(acc,.75)
                        if cx+1<32:p[yy][cx+1]=shade(acc,.9)
                ellipse(cx,cy,rx,ry,pri,shade(pri,1.22))
                for sx in range(cx-rx+2,cx+rx-1,4):
                    if 0<=sx<32 and 0<=cy<32:p[cy][sx]=acc
        elif shape in ('ROOT_TUBER','BULB_GARLIC'):
            # Roots stay mostly below soil; the visible crop is a broad leafy crown.
            for base in (8,16,24):
                leaf(base,31,base,14,leaf_mid,1)
                leaf(base,25,base-7,14,leaf_dark,2);leaf(base,27,base+7,16,leaf_light,2)
                leaf(base,21,base-5,8,leaf_mid,1);leaf(base,22,base+5,7,leaf_light,1)
                ellipse(base,28,3,3,pri,shade(pri,1.2))
        elif shape in ('LEAF_FROND','BLOSSOM','SPROUT_TREE'):
            # Dense overlapping foliage for lettuce, cabbage, celery, spinach and herbs.
            for cx,cy,rx,ry,col in ((7,23,7,6,leaf_dark),(24,22,7,7,leaf_mid),(15,17,9,8,leaf_light),
                                    (8,11,6,7,leaf_mid),(23,9,6,7,leaf_dark),(16,27,10,5,leaf_mid)):
                ellipse(cx,cy,rx,ry,col,shade(col,1.18))
            if shape=='BLOSSOM':ellipse(16,14,7,7,pri,shade(pri,1.22))
        elif shape in ('PEPPER_CHILI','POD'):
            # Branching bush with clearly readable hanging peppers or pea pods.
            leaf(16,31,16,5,leaf_mid,1)
            for bx,by,side in ((16,10,-1),(16,15,1),(16,21,-1),(16,25,1)):
                leaf(bx,by,bx+side*11,by-5,leaf_mid,1)
                leaf(bx+side*3,by-1,bx+side*8,by-8,leaf_light,2)
                fx=bx+side*9;fy=by+1
                if shape=='POD':ellipse(fx,fy,2,5,pri,shade(pri,1.25))
                else:
                    ellipse(fx,fy,3,5,pri,shade(pri,1.25));p[min(31,fy+5)][fx+side]=sec
        elif shape == 'BERRY_BUNCH':
            # Full berry bush based on the dense final vanilla sweet-berry stage.
            for cx,cy,rx,ry in ((7,23,7,7),(23,23,7,7),(15,15,9,8),(7,9,6,6),(24,8,6,6)):
                ellipse(cx,cy,rx,ry,leaf_mid,leaf_light)
            for fx,fy in ((4,22),(10,18),(20,24),(26,18),(12,11),(18,9),(6,7),(26,6),(16,19)):
                ellipse(fx,fy,2,2,pri,shade(pri,1.3));p[fy+1][fx]=sec
        elif shape == 'BAMBOO_STALK':
            # Several leek-like stems spread across the whole block.
            for x,h in ((5,20),(10,27),(16,30),(22,25),(27,19)):
                for yy in range(31-h,32):
                    p[yy][x]=acc;p[yy][min(31,x+1)]=pri
                leaf(x,19,x-4,7,leaf_dark,1);leaf(x,17,x+4,4,leaf_light,1)
        elif shape == 'STAR':
            leaf(16,31,16,5,leaf_mid,1)
            for fx,fy in ((6,20),(25,20),(9,10),(23,8),(16,15)):
                leaf(16,24,fx,fy,leaf_mid,1)
                for angle in range(0,360,60):
                    rad=math.radians(angle)
                    for d in range(1,4):
                        xx=round(fx+math.cos(rad)*d);yy=round(fy+math.sin(rad)*d)
                        if 0<=xx<32 and 0<=yy<32:p[yy][xx]=pri
                p[fy][fx]=acc
        else: # ACORN and any future compact produce
            leaf(16,31,16,4,leaf_dark,1)
            for cx,cy in ((7,22),(24,21),(13,13),(21,9),(6,7)):
                ellipse(cx,cy,6,6,leaf_mid,leaf_light)
            for fx,fy in ((7,23),(24,22),(13,14),(21,10)):
                ellipse(fx,fy,3,4,pri,shade(pri,1.2));p[fy-3][fx]=sec

    # Outline pass (collect first, then apply to avoid cascading smear)
    border_pixels = []
    for y in range(1, 31):
        for x in range(1, 31):
            if p[y][x][3] > 0 and y < 29:
                for dy, dx in ((-1,0),(1,0),(0,-1),(0,1)):
                    ny, nx = y + dy, x + dx
                    if 0 <= ny < 32 and 0 <= nx < 32 and p[ny][nx][3] == 0:
                        border_pixels.append((ny, nx))
    for ny, nx in border_pixels:
        p[ny][nx] = border
    return p

def register_crop_assets(java, bedrock, textures, mappings, selectors, write_json, png):
    """Generates all 150 crop assets (30 seeds, 30 foods, 90 plant stages) into packs."""
    # Display transform for plant models in item displays and UI
    cross_display = {
        'fixed': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1.0, 1.0, 1.0]},
        'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.75, 0.75, 0.75]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
        'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0], 'scale': [0.4, 0.4, 0.4]},
        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.375, 0.375, 0.375]}
    }

    # Vanilla's crop.json uses four upright planes at 1/4 and 3/4 of the
    # block, rather than the two diagonal planes used by block/cross. This is
    # what makes wheat, carrots and potatoes form a full, dense field.
    crop_faces_x = {
        'west': {'uv': [0, 0, 16, 16], 'texture': '#crop'},
        'east': {'uv': [16, 0, 0, 16], 'texture': '#crop'}
    }
    crop_faces_z = {
        'north': {'uv': [0, 0, 16, 16], 'texture': '#crop'},
        'south': {'uv': [16, 0, 0, 16], 'texture': '#crop'}
    }
    dense_crop_elements = [
        {'from': [4, 0, 0], 'to': [4, 16, 16], 'shade': False, 'faces': crop_faces_x},
        {'from': [12, 0, 0], 'to': [12, 16, 16], 'shade': False, 'faces': crop_faces_x},
        {'from': [0, 0, 4], 'to': [16, 16, 4], 'shade': False, 'faces': crop_faces_z},
        {'from': [0, 0, 12], 'to': [16, 16, 12], 'shade': False, 'faces': crop_faces_z}
    ]

    for crop in CROPS:
        cid = crop['id']
        title = crop['title']
        base_seed = crop['seed']
        base_food = crop['food']

        # 1. Seed Item
        seed_name = 'seed_' + cid
        seed_pixels = detailed_pixels(draw_seed(crop), crop, 'seed')
        png(java / f'assets/voidscape/textures/item/{seed_name}.png', seed_pixels, size=TEXTURE_SIZE)
        png(bedrock / f'textures/items/{seed_name}.png', seed_pixels, size=TEXTURE_SIZE)
        textures['voidscape.' + seed_name] = {'textures': 'textures/items/' + seed_name}

        write_json(java / f'assets/voidscape/models/item/{seed_name}.json', {
            'parent': 'minecraft:item/generated',
            'textures': {'layer0': 'voidscape:item/' + seed_name}
        })
        write_json(java / f'assets/voidscape/items/{seed_name}.json', {
            'model': {'type': 'minecraft:model', 'model': 'voidscape:item/' + seed_name}
        })
        mappings['items'].setdefault('minecraft:' + base_seed, []).append({
            'type': 'definition',
            'model': 'voidscape:' + seed_name,
            'bedrock_identifier': 'voidscape:' + seed_name,
            'display_name': 'Seed of ' + title,
            'bedrock_options': {'icon': 'voidscape.' + seed_name, 'allow_offhand': True, 'display_handheld': False, 'creative_category': 'items'}
        })
        selectors.setdefault(base_seed, []).append({'when': 'voidscape:' + seed_name, 'model': {'type': 'minecraft:model', 'model': 'voidscape:item/' + seed_name}})

        # 2. Food Item
        food_name = 'crop_' + cid
        food_pixels = detailed_pixels(draw_food(crop), crop, 'food')
        png(java / f'assets/voidscape/textures/item/{food_name}.png', food_pixels, size=TEXTURE_SIZE)
        png(bedrock / f'textures/items/{food_name}.png', food_pixels, size=TEXTURE_SIZE)
        textures['voidscape.' + food_name] = {'textures': 'textures/items/' + food_name}

        write_json(java / f'assets/voidscape/models/item/{food_name}.json', {
            'parent': 'minecraft:item/generated',
            'textures': {'layer0': 'voidscape:item/' + food_name}
        })
        write_json(java / f'assets/voidscape/items/{food_name}.json', {
            'model': {'type': 'minecraft:model', 'model': 'voidscape:item/' + food_name}
        })
        mappings['items'].setdefault('minecraft:' + base_food, []).append({
            'type': 'definition',
            'model': 'voidscape:' + food_name,
            'bedrock_identifier': 'voidscape:' + food_name,
            'display_name': title,
            'bedrock_options': {'icon': 'voidscape.' + food_name, 'allow_offhand': True, 'display_handheld': False, 'creative_category': 'items'}
        })
        selectors.setdefault(base_food, []).append({'when': 'voidscape:' + food_name, 'model': {'type': 'minecraft:model', 'model': 'voidscape:item/' + food_name}})

        # 3. Growth stages use a head-equippable base so Bedrock registers wearable.
        for stage in (0, 1, 2):
            stage_name = f'crop_{cid}_stage_{stage}'
            stage_pixels = detailed_pixels(draw_crop_stage(crop, stage), crop, 'plant')
            # Write to both block and item texture paths for Java
            png(java / f'assets/voidscape/textures/block/{stage_name}.png', stage_pixels, size=TEXTURE_SIZE)
            png(java / f'assets/voidscape/textures/item/{stage_name}.png', stage_pixels, size=TEXTURE_SIZE)
            png(bedrock / f'textures/items/{stage_name}.png', stage_pixels, size=TEXTURE_SIZE)
            textures['voidscape.' + stage_name] = {'textures': 'textures/items/' + stage_name}

            stage_model = {
                'ambientocclusion': False,
                'textures': {'crop': 'voidscape:block/' + stage_name, 'particle': 'voidscape:block/' + stage_name},
                'elements': dense_crop_elements,
                'display': {**cross_display, 'head': {
                    # The renderer uses a small armor stand for Geyser
                    # Compensate Java's .625 head-item scale. Move the centre
                    # upward by half the added model height to retain its base.
                    'rotation': [0, 0, 0], 'translation': [0, 9.6, 0], 'scale': [3.2, 3.2, 3.2]
                }}
            }
            write_json(java / f'assets/voidscape/models/item/{stage_name}.json', stage_model)
            write_json(java / f'assets/voidscape/items/{stage_name}.json', {
                'model': {'type': 'minecraft:model', 'model': 'voidscape:item/' + stage_name}
            })
            # A crop is worn by an invisible armor stand. Geyser translates
            # armor stands and resolves this attachable, unlike Java ItemDisplay.
            geometry_id = 'geometry.voidscape.' + stage_name
            write_json(bedrock / f'models/entity/{stage_name}.geo.json', {
                'format_version': '1.16.0',
                'minecraft:geometry': [{
                    'description': {
                        'identifier': geometry_id, 'texture_width': TEXTURE_SIZE, 'texture_height': TEXTURE_SIZE,
                        'visible_bounds_width': 3, 'visible_bounds_height': 3,
                        'visible_bounds_offset': [0, 1.75, 0]
                    },
                    'bones': [{
                        # Armor geometry uses entity-space coordinates, as in
                        # vanilla helmets: the head pivot is Y=24, not Y=0.
                        # Y=0 put the entire small-stand crop below farmland.
                        'name': 'head',
                        'pivot': [0, 24, 0],
                        'cubes': [
                            # Java's head item transform adds a .625 scale that
                            # Bedrock armor geometry does not. 32 * .625 = 20.
                            # See GeyserIntegratedPack/developer_documentation.md.
                            {'origin': [-10, 24, -5.15625], 'size': [20, 20, 0.3125],
                             'uv': crop_uv('north', 'south')},
                            {'origin': [-10, 24, 4.84375], 'size': [20, 20, 0.3125],
                             'uv': crop_uv('north', 'south')},
                            {'origin': [-5.15625, 24, -10], 'size': [0.3125, 20, 20],
                             'uv': crop_uv('west', 'east')},
                            {'origin': [4.84375, 24, -10], 'size': [0.3125, 20, 20],
                             'uv': crop_uv('west', 'east')}
                        ]
                    }]
                }]
            })
            write_json(bedrock / f'attachables/{stage_name}.json', {
                'format_version': '1.10.0',
                'minecraft:attachable': {'description': {
                    'identifier': 'voidscape:' + stage_name,
                    'materials': {'default': 'entity_alphatest'},
                    'textures': {'default': 'textures/items/' + stage_name},
                    'geometry': {'default': geometry_id},
                    'render_controllers': ['controller.render.evergarden_mask']
                }}
            })
            # Keep the mapping aligned with the Java carrier item used by the
            # crop armor-stand display.
            mappings['items'].setdefault('minecraft:carved_pumpkin', []).append({
                'type': 'definition',
                'model': 'voidscape:' + stage_name,
                'bedrock_identifier': 'voidscape:' + stage_name,
                'display_name': f'{title} (Stage {stage})',
                'bedrock_options': {'icon': 'voidscape.' + stage_name, 'allow_offhand': True, 'display_handheld': False, 'creative_category': 'items'}
            })
