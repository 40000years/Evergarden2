"""Handcrafted high-detail 32x32 pixel art produce textures for all 30 Evergarden crops.
Matches the rich palette, directional lighting, and intricate shading of Advance Magic.
"""
import math

def new_canvas(size=32):
    return [[(0, 0, 0, 0) for _ in range(size)] for _ in range(size)]

def blend(c1, c2, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(c1[i] * (1 - t) + c2[i] * t)) for i in range(3)) + (255,)

def shade(c, factor):
    return tuple(max(0, min(255, int(round(v * factor)))) for v in c[:3]) + (255,)

def put(p, x, y, c):
    ix, iy = int(round(x)), int(round(y))
    if 0 <= ix < 32 and 0 <= iy < 32:
        if len(c) == 3:
            c = c + (255,)
        p[iy][ix] = c

def fill_circle(p, cx, cy, r, fill_fn):
    ir = int(math.ceil(r))
    for dy in range(-ir, ir + 1):
        for dx in range(-ir, ir + 1):
            d2 = dx * dx + dy * dy
            if d2 <= r * r:
                c = fill_fn(dx, dy, math.sqrt(d2) / r)
                if c:
                    put(p, cx + dx, cy + dy, c)

def fill_ellipse(p, cx, cy, rx, ry, fill_fn):
    irx, iry = int(math.ceil(rx)), int(math.ceil(ry))
    for dy in range(-iry, iry + 1):
        for dx in range(-irx, irx + 1):
            nx = dx / max(1e-5, rx)
            ny = dy / max(1e-5, ry)
            d2 = nx * nx + ny * ny
            if d2 <= 1.0:
                c = fill_fn(dx, dy, math.sqrt(d2), nx, ny)
                if c:
                    put(p, cx + dx, cy + dy, c)

def apply_outline(p, dark_color=None):
    """Adds a dark, colored outline around non-transparent pixels."""
    border_pixels = []
    for y in range(32):
        for x in range(32):
            if p[y][x][3] == 0:
                # check neighbors
                neighbors = []
                for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1), (-1, -1), (-1, 1), (1, -1), (1, 1)):
                    ny, nx = y + dy, x + dx
                    if 0 <= ny < 32 and 0 <= nx < 32 and p[ny][nx][3] > 0:
                        neighbors.append(p[ny][nx])
                if neighbors:
                    if dark_color:
                        border_pixels.append((x, y, dark_color))
                    else:
                        # Auto tinted dark outline based on neighbor color
                        avg_r = sum(c[0] for c in neighbors) // len(neighbors)
                        avg_g = sum(c[1] for c in neighbors) // len(neighbors)
                        avg_b = sum(c[2] for c in neighbors) // len(neighbors)
                        edge = (max(14, int(avg_r * 0.28)), max(12, int(avg_g * 0.28)), max(18, int(avg_b * 0.28)), 255)
                        border_pixels.append((x, y, edge))
    for x, y, c in border_pixels:
        put(p, x, y, c)

# =============================================================================
# TIER 1 CROPS
# =============================================================================

def draw_blueberry(info):
    """Blueberry: Cluster of 3 plump berries with calyx star crowns, frosted bloom, and leaves."""
    p = new_canvas()
    # Green leaves at back
    LEAF_D = (30, 75, 32, 255)
    LEAF_M = (65, 145, 55, 255)
    LEAF_L = (130, 205, 80, 255)
    STEM = (90, 65, 40, 255)

    # Leaves
    for dx, dy, col in [
        (13, 8, LEAF_D), (14, 7, LEAF_M), (15, 6, LEAF_L), (16, 6, LEAF_M), (17, 7, LEAF_D),
        (20, 9, LEAF_D), (21, 8, LEAF_M), (22, 7, LEAF_L), (23, 7, LEAF_M), (24, 8, LEAF_D),
        (10, 14, LEAF_D), (9, 15, LEAF_M), (8, 16, LEAF_L), (9, 17, LEAF_M), (10, 18, LEAF_D)
    ]:
        put(p, dx, dy, col)
    # Stems
    for x, y in [(16, 7), (16, 8), (17, 9), (16, 10), (15, 11), (19, 10), (18, 11)]:
        put(p, x, y, STEM)

    # 3 Berries: Berry 1 (back right), Berry 2 (left front), Berry 3 (right front)
    berries = [
        (19, 14, 5.2), # back right
        (12, 19, 6.0), # front left
        (20, 20, 6.2)  # front right
    ]
    for bx, by, br in berries:
        def berry_shader(dx, dy, dist, nx, ny):
            # Spherical lighting from top-left (-0.5, -0.6)
            lx, ly = dx / br + 0.45, dy / br + 0.45
            light = 1.0 - math.sqrt(lx*lx + ly*ly) * 0.7
            # Base color ramp: dark indigo -> royal blue -> powdery bloom
            if light < 0.35:
                return (28, 35, 75, 255)
            elif light < 0.65:
                return (45, 65, 135, 255)
            elif light < 0.90:
                return (75, 110, 195, 255)
            elif light < 1.15:
                return (125, 165, 235, 255) # bloom
            else:
                return (210, 230, 255, 255) # specular gleam
        fill_ellipse(p, bx, by, br, br * 0.95, berry_shader)

        # 5-point star calyx indents on front berries
        cx, cy = int(bx + 1.2), int(by + 1.2)
        put(p, cx, cy, (20, 25, 55, 255))
        for ox, oy in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
            put(p, cx + ox, cy + oy, (35, 45, 90, 255))

    apply_outline(p, (18, 20, 42, 255))
    return p

def draw_lettuce(info):
    """Lettuce: Fresh, crisp round ruffled head of leafy lettuce with pale crisp heart and wavy folds."""
    p = new_canvas()
    BASE = (205, 238, 180, 255)
    LIGHT = (145, 215, 90, 255)
    MID = (78, 165, 58, 255)
    DARK = (38, 98, 40, 255)
    DEEP = (24, 65, 28, 255)

    # Draw layered outer ruffles
    for y in range(8, 26):
        for x in range(7, 26):
            dx, dy = x - 16, y - 17
            d = math.hypot(dx, dy * 1.1)
            # Ruffled perimeter using sine distortion
            ruffle = math.sin(math.atan2(dy, dx) * 7.0) * 1.8
            if d + ruffle <= 9.5:
                # Radial and vertical shading
                ratio = d / 9.5
                if y > 21 and abs(dx) < 4:
                    col = BASE # stem heart
                elif ratio < 0.35:
                    col = LIGHT
                elif ratio < 0.70:
                    col = MID if (x + y) % 4 != 0 else LIGHT
                else:
                    col = DARK
                put(p, x, y, col)

    # Leaf cup folds and curly ridges
    folds = [
        (12, 12, LIGHT), (13, 11, LIGHT), (14, 12, MID),
        (19, 12, LIGHT), (20, 13, LIGHT), (18, 13, MID),
        (10, 17, LIGHT), (11, 18, LIGHT), (10, 19, DARK),
        (22, 17, LIGHT), (21, 18, LIGHT), (22, 19, DARK),
        (15, 16, BASE),  (16, 16, BASE),  (17, 16, BASE),
        (15, 17, BASE),  (16, 18, BASE),  (16, 19, BASE),
        (14, 18, LIGHT), (17, 18, LIGHT), (13, 20, LIGHT), (18, 20, LIGHT),
        # Curly tips
        (8, 14, LIGHT), (9, 13, BASE), (23, 14, LIGHT), (24, 15, BASE),
        (16, 8, BASE), (15, 9, LIGHT), (17, 9, LIGHT)
    ]
    for x, y, c in folds:
        put(p, x, y, c)

    apply_outline(p, DEEP)
    return p

def draw_truffle(info):
    """Truffle: Craggy textured black-brown subterranean delicacy with marbled cut slice."""
    p = new_canvas()
    cx, cy, r = 16, 17, 8.5
    for y in range(8, 27):
        for x in range(7, 26):
            dx, dy = x - cx, y - cy
            # Knobby organic shape
            wobble = math.sin(dx * 0.9) * 0.8 + math.cos(dy * 1.1) * 0.9
            if dx*dx + dy*dy * 1.05 + wobble <= r * r:
                # Earthy textured charcoal-brown
                dist = math.hypot(dx + 2.5, dy + 2.5) / r
                f = 1.0 - dist * 0.55
                # Craggy facets
                facet = ((x * 7 + y * 13) % 11) / 10.0 - 0.5
                brightness = max(0.2, min(1.3, f + facet * 0.35))
                if brightness < 0.45:
                    c = (32, 24, 20, 255)
                elif brightness < 0.75:
                    c = (58, 42, 32, 255)
                elif brightness < 1.05:
                    c = (92, 68, 48, 255)
                else:
                    c = (135, 102, 72, 255)
                put(p, x, y, c)

    # Exposed prized slice cut revealing marbled cream veins
    for y in range(13, 20):
        for x in range(14, 22):
            dx, dy = x - 17, y - 16
            if dx*dx + dy*dy <= 12:
                # Interior rich hazelnut
                c = (75, 52, 38, 255)
                # Intricate white/cream marble filigree
                if (x * 3 + y * 5) % 7 in (0, 1):
                    c = (215, 200, 175, 255)
                elif (x + y * 2) % 5 == 0:
                    c = (175, 150, 125, 255)
                put(p, x, y, c)

    apply_outline(p, (18, 14, 12, 255))
    return p

def draw_butternut_squash(info):
    """Butternut Squash: Iconic voluptuous hourglass pear shape with butterscotch skin and woody stem."""
    p = new_canvas()
    STEM = (82, 72, 38, 255)
    STEM_L = (118, 105, 55, 255)

    # Stem
    for x, y in [(15, 5), (15, 6), (16, 6), (16, 7), (16, 8)]:
        put(p, x, y, STEM)
    put(p, 15, 5, STEM_L)

    # Hourglass body: Upper bulb (cx=16, cy=12, r=4.8), Lower bulb (cx=16, cy=20, r=7.2)
    for y in range(7, 28):
        for x in range(8, 25):
            # Evaluate upper bulb & lower bulb blended together
            dx = x - 16
            if y <= 14:
                # Upper neck
                dy = y - 12
                d = (dx*dx) / (4.8*4.8) + (dy*dy) / (5.0*5.0)
            else:
                # Lower plump base
                dy = y - 20
                d = (dx*dx) / (7.2*7.2) + (dy*dy) / (6.8*6.8)
            if d <= 1.0:
                # 3D shading from top-left
                lx = dx + 3.0
                ly = (y - 15) * 0.6 + 2.0
                illum = 1.2 - math.hypot(lx, ly) / 10.0
                # Gentle vertical ribs
                rib = math.cos(dx * 0.75) * 0.12
                light = illum + rib
                if light < 0.65:
                    c = (165, 95, 30, 255)
                elif light < 0.95:
                    c = (215, 145, 55, 255)
                elif light < 1.20:
                    c = (248, 185, 95, 255)
                else:
                    c = (255, 222, 148, 255) # highlight
                put(p, x, y, c)

    apply_outline(p, (42, 25, 12, 255))
    return p

def draw_leek(info):
    """Leek: White cylindrical root shank with roots, lime midsection, and dark green fan leaves."""
    p = new_canvas()
    ROOT_D = (165, 155, 135, 255)
    WHITE_L = (252, 252, 246, 255)
    WHITE_M = (230, 232, 220, 255)
    WHITE_S = (195, 200, 185, 255)
    LIME_L = (175, 220, 115, 255)
    LIME_M = (125, 185, 75, 255)
    GREEN_L = (85, 160, 60, 255)
    GREEN_M = (48, 120, 42, 255)
    GREEN_D = (28, 75, 28, 255)

    # Fan leaves at top (y=4..14)
    # Left leaf
    for t in range(12):
        lx = int(14 - t * 0.6)
        ly = int(14 - t * 0.8)
        put(p, lx, ly, GREEN_M)
        put(p, lx + 1, ly, GREEN_L)
        put(p, lx - 1, ly, GREEN_D)
    # Right leaf
    for t in range(12):
        rx = int(17 + t * 0.65)
        ry = int(13 - t * 0.75)
        put(p, rx, ry, GREEN_M)
        put(p, rx - 1, ry, GREEN_L)
        put(p, rx + 1, ry, GREEN_D)
    # Center tall leaf
    for t in range(11):
        cx = int(15 + math.sin(t * 0.3) * 0.8)
        cy = int(12 - t * 0.9)
        put(p, cx, cy, GREEN_L)
        put(p, cx + 1, cy, GREEN_M)

    # Main cylindrical shank (y=13..27, width=6)
    for y in range(13, 28):
        for x in range(13, 19):
            dx = x - 13 # 0..5
            if y < 18:
                # Lime transition zone
                col = LIME_L if dx in (1, 2) else (LIME_M if dx == 3 else GREEN_M)
            elif y < 24:
                # Clean porcelain white
                col = WHITE_L if dx in (1, 2) else (WHITE_M if dx == 3 else WHITE_S)
            else:
                # Root base
                col = WHITE_M if dx in (1, 2) else WHITE_S
            put(p, x, y, col)

    # Delicate root whiskers at bottom (y=28..30)
    for rx, ry in [(14, 28), (14, 29), (16, 28), (16, 29), (17, 28), (17, 30), (15, 28), (15, 30)]:
        put(p, rx, ry, ROOT_D)

    apply_outline(p, (20, 35, 22, 255))
    return p

def draw_papaya(info):
    """Papaya: Elongated tropical fruit with smooth sunburst green-to-orange-yellow gradient and stem."""
    p = new_canvas()
    STEM = (55, 85, 35, 255)
    for x, y in [(15, 5), (16, 5), (16, 6), (16, 7)]:
        put(p, x, y, STEM)

    # Teardrop / oval fruit (cx=16, cy=18, rx=6.8, ry=10.0, wider near bottom)
    for y in range(7, 29):
        for x in range(8, 25):
            dx = x - 16
            dy = y - 18
            # Shape widening slightly toward bottom
            scale_x = 5.2 + (y - 7) * 0.12 if y < 20 else 6.8 - (y - 20) * 0.25
            d = (dx * dx) / (scale_x * scale_x) + (dy * dy) / (10.5 * 10.5)
            if d <= 1.0:
                # Vertical gradient: Green top -> Amber yellow -> Tropical orange base
                t_vert = (y - 7) / 21.0
                if t_vert < 0.25:
                    base_c = (75, 145, 45, 255) # forest green
                elif t_vert < 0.55:
                    base_c = (215, 185, 42, 255) # sunny gold
                else:
                    base_c = (235, 125, 32, 255) # rich papaya orange

                # Lighting from top-left
                dist = math.hypot(dx + 2.5, dy * 0.5 + 2.0) / scale_x
                f = 1.25 - dist * 0.65
                c = shade(base_c, f)
                # Highlight glint
                if dx in (-2, -1) and dy in (-3, -2):
                    c = blend(c, (255, 245, 200, 255), 0.65)
                put(p, x, y, c)

    apply_outline(p, (35, 25, 12, 255))
    return p

# =============================================================================
# TIER 2 CROPS
# =============================================================================

def draw_tomato(info):
    """Tomato: Plump, glossy crimson beefsteak tomato with green star calyx and bright specular gleam."""
    p = new_canvas()
    cx, cy, rx, ry = 16, 18, 8.5, 7.8

    # Red tomato body
    for y in range(10, 27):
        for x in range(7, 26):
            dx = x - cx
            dy = y - cy
            # Subtle 3-lobed indentation at top
            indent = 0.8 if y < 14 and abs(dx) < 2 else 0.0
            if (dx*dx) / (rx*rx) + (dy*dy + indent) / (ry*ry) <= 1.0:
                # Spherical lighting from (-3, -3)
                lx = dx + 3.0
                ly = dy + 3.0
                dist = math.hypot(lx, ly) / 9.0
                light = 1.3 - dist * 0.85
                # Rich tomato red ramp
                if light < 0.65:
                    c = (145, 18, 28, 255)
                elif light < 0.95:
                    c = (215, 32, 38, 255)
                elif light < 1.20:
                    c = (252, 75, 62, 255)
                else:
                    c = (255, 145, 125, 255)
                put(p, x, y, c)

    # Specular white gloss arc
    for sx, sy in [(12, 13), (13, 13), (14, 13), (12, 14), (13, 14)]:
        put(p, sx, sy, (255, 240, 240, 255))

    # Star-shaped 5-pointed green calyx on top (y=7..13)
    CALYX_D = (32, 85, 30, 255)
    CALYX_L = (85, 185, 65, 255)
    STEM = (50, 115, 40, 255)
    # Stem
    for y in range(6, 11):
        put(p, 16, y, STEM)
        if y <= 8:
            put(p, 15, y, CALYX_L)
    # 5 Calyx leaves radiating outward
    for ox, oy, col in [
        (-4, 1, CALYX_D), (-3, 1, CALYX_L), (-2, 0, CALYX_L), (-1, 0, CALYX_D),
        (4, 1, CALYX_D), (3, 1, CALYX_L), (2, 0, CALYX_L), (1, 0, CALYX_D),
        (-2, 2, CALYX_D), (-2, 3, CALYX_L),
        (2, 2, CALYX_D), (2, 3, CALYX_L),
        (0, 3, CALYX_D), (0, 2, CALYX_L)
    ]:
        put(p, 16 + ox, 10 + oy, col)

    apply_outline(p, (38, 12, 16, 255))
    return p

def draw_radish(info):
    """Radish: Round crimson-magenta bulb with crisp white taproot tip and fresh leafy green top."""
    p = new_canvas()
    LEAF_D = (32, 85, 35, 255)
    LEAF_M = (65, 155, 55, 255)
    LEAF_L = (125, 215, 85, 255)

    # Leaf tops (y=4..13)
    for ox, oy, col in [
        (-3, 0, LEAF_L), (-2, 1, LEAF_M), (-2, 2, LEAF_D),
        (0, -2, LEAF_L), (0, -1, LEAF_M), (0, 1, LEAF_D),
        (3, 0, LEAF_L), (2, 1, LEAF_M), (2, 2, LEAF_D),
        (-1, 3, LEAF_M), (1, 3, LEAF_M), (0, 4, LEAF_D)
    ]:
        put(p, 16 + ox, 8 + oy, col)

    # Bulb (cx=16, cy=18, r=7.2, tapering down to y=27)
    for y in range(12, 29):
        for x in range(9, 24):
            dx = x - 16
            dy = y - 18
            # Shape round at top, conical taproot at bottom
            w = 7.0 * math.sqrt(max(0.0, 1.0 - (dy / 7.0)**2)) if dy < 2 else max(0.8, 6.8 - (dy - 2) * 0.85)
            if abs(dx) <= w:
                lx = dx + 2.5
                ly = dy + 2.5
                dist = math.hypot(lx, ly) / 7.5
                light = 1.25 - dist * 0.7
                if y >= 22:
                    # Clean porcelain white root tip
                    c = (252, 252, 255, 255) if light > 0.9 else (205, 215, 230, 255)
                else:
                    # Brilliant fuchsia/crimson
                    if light < 0.65:
                        c = (145, 18, 55, 255)
                    elif light < 0.95:
                        c = (215, 38, 85, 255)
                    elif light < 1.15:
                        c = (250, 85, 135, 255)
                    else:
                        c = (255, 175, 205, 255) # highlight
                put(p, x, y, c)

    # Slender root whisker
    put(p, 16, 29, (195, 205, 218, 255))
    put(p, 17, 30, (175, 185, 200, 255))

    apply_outline(p, (38, 14, 25, 255))
    return p

def draw_corn(info):
    """Corn: Golden ear of corn with neat rows of yellow kernels, wrapped in pale green husk leaves."""
    p = new_canvas()
    HUSK_D = (45, 110, 40, 255)
    HUSK_M = (85, 165, 65, 255)
    HUSK_L = (155, 215, 105, 255)
    SILK = (215, 195, 120, 255)

    # Golden silk at top (y=4..8)
    for sx, sy in [(15, 5), (16, 4), (17, 5), (15, 6), (16, 7), (18, 6)]:
        put(p, sx, sy, SILK)

    # Corn kernels body (y=8..23, width=7)
    for y in range(8, 24):
        w = 3.5 if (y in (8, 9, 22, 23)) else 4.2
        for x in range(int(16 - w), int(16 + w) + 1):
            dx = x - 16
            # Kernel grid alternating
            is_kernel_center = ((x + (y % 2)) % 2 == 0) and (y % 2 == 0)
            is_crevice = ((x + (y % 2)) % 2 == 1) or (y % 2 == 1)
            # Cylindrical light
            light = 1.2 - abs(dx + 1.0) * 0.22
            if is_kernel_center:
                c = (255, 245, 135, 255) if light > 1.0 else (255, 215, 55, 255)
            elif is_crevice:
                c = (195, 135, 20, 255)
            else:
                c = (245, 185, 35, 255)
            put(p, x, y, c)

    # Husk leaves peeling open from base (y=16..28)
    # Left husk
    for t in range(9):
        hx = int(12 - t * 0.35)
        hy = int(18 + t * 1.1)
        put(p, hx, hy, HUSK_M)
        put(p, hx + 1, hy, HUSK_L)
        put(p, hx - 1, hy, HUSK_D)
    # Right husk
    for t in range(9):
        hx = int(20 + t * 0.35)
        hy = int(18 + t * 1.1)
        put(p, hx, hy, HUSK_M)
        put(p, hx - 1, hy, HUSK_L)
        put(p, hx + 1, hy, HUSK_D)
    # Husk base stem
    for y in range(26, 29):
        put(p, 15, y, HUSK_D); put(p, 16, y, HUSK_M)

    apply_outline(p, (32, 25, 10, 255))
    return p

def draw_garlic(info):
    """Garlic: Full bulb showing individual plump cloves in papery ivory skin, dried neck and root hairs."""
    p = new_canvas()
    cx, cy = 16, 18
    STEM = (165, 142, 108, 255)
    STEM_L = (215, 195, 165, 255)
    ROOT = (185, 165, 135, 255)

    # Dried neck stem at top (y=6..12)
    for y in range(6, 13):
        put(p, 16, y, STEM)
        if y <= 9:
            put(p, 17, y, STEM_L)

    # 4 distinct bulbous cloves forming the bulb
    # Left clove, Center-left clove, Center-right clove, Right clove
    clove_specs = [
        (11.5, 19, 3.8, 5.0, -0.2),
        (14.5, 19.5, 3.5, 5.8, -0.05),
        (17.5, 19.5, 3.5, 5.8, 0.05),
        (20.5, 19, 3.8, 5.0, 0.2)
    ]
    for clx, cly, crx, cry, rot in clove_specs:
        fill_ellipse(p, clx, cly, crx, cry, lambda dx, dy, dist, nx, ny:
            (255, 255, 250, 255) if (dx < -0.5 and dy < -1.0) else (
                (238, 235, 222, 255) if dist < 0.75 else (185, 175, 160, 255)
            )
        )

    # Distinct clove groove crevice lines
    for gy in range(14, 24):
        put(p, 13, gy, (150, 135, 120, 255))
        put(p, 16, gy, (145, 130, 115, 255))
        put(p, 19, gy, (155, 140, 125, 255))

    # Roots at base (y=24..27)
    for rx, ry in [(14, 25), (15, 26), (16, 25), (17, 26), (18, 25), (16, 27)]:
        put(p, rx, ry, ROOT)

    apply_outline(p, (38, 32, 28, 255))
    return p

def draw_eggplant(info):
    """Eggplant: Glossy deep royal-purple pear shape with large 5-pointed green calyx and specular reflection."""
    p = new_canvas()
    CALYX_D = (32, 85, 32, 255)
    CALYX_M = (65, 145, 52, 255)
    CALYX_L = (115, 195, 80, 255)
    STEM = (50, 115, 40, 255)

    # Curved stem (y=5..10)
    for sx, sy in [(17, 5), (17, 6), (16, 7), (16, 8), (16, 9)]:
        put(p, sx, sy, STEM)
    put(p, 16, 6, CALYX_L)

    # Purple body: Pear shape (narrower near top y=11, bulbous at bottom y=22)
    for y in range(11, 28):
        for x in range(8, 25):
            dx = x - 16
            dy = y - 19
            # Pear width profile
            w = 4.0 + (y - 11) * 0.45 if y < 20 else max(1.0, 8.0 - (y - 20) * 1.1)
            if abs(dx) <= w:
                lx = dx + 3.0
                ly = dy + 3.0
                dist = math.hypot(lx, ly) / 8.5
                light = 1.3 - dist * 0.8
                if light < 0.60:
                    c = (35, 12, 52, 255) # deep midnight plum
                elif light < 0.90:
                    c = (75, 22, 105, 255) # royal violet
                elif light < 1.15:
                    c = (135, 45, 185, 255) # radiant purple
                else:
                    c = (195, 95, 235, 255)
                put(p, x, y, c)

    # Specular gloss gleam along left curved shoulder
    for sx, sy in [(13, 14), (12, 15), (12, 16), (12, 17), (13, 18)]:
        put(p, sx, sy, (245, 220, 255, 255))
    put(p, 13, 15, (255, 255, 255, 255))

    # Large 5-pointed leafy calyx draping over crown (y=10..15)
    for ox, oy, col in [
        (-4, 3, CALYX_D), (-3, 2, CALYX_L), (-2, 1, CALYX_M),
        (4, 3, CALYX_D), (3, 2, CALYX_L), (2, 1, CALYX_M),
        (-2, 4, CALYX_D), (-1, 3, CALYX_L),
        (2, 4, CALYX_D), (1, 3, CALYX_L),
        (0, 4, CALYX_M), (0, 3, CALYX_L),
        (-1, 1, CALYX_M), (1, 1, CALYX_M), (0, 1, CALYX_L)
    ]:
        put(p, 16 + ox, 10 + oy, col)

    apply_outline(p, (20, 8, 30, 255))
    return p

def draw_green_peas(info):
    """Green Peas: Elegant curved pea pod split open with 4 glossy round peas nestled inside."""
    p = new_canvas()
    POD_OUTER = (38, 105, 32, 255)
    POD_INNER = (75, 155, 55, 255)
    POD_L = (145, 215, 95, 255)
    STEM = (55, 125, 42, 255)

    # Stem and curly tendril at top left (y=5..9)
    for sx, sy in [(9, 6), (10, 7), (11, 8), (12, 9)]:
        put(p, sx, sy, STEM)
    put(p, 8, 5, POD_L); put(p, 7, 6, POD_L) # curly tendril

    # Elegant sweeping curved pod body (diagonal from (11, 9) down-right to (24, 25))
    pod_curve = [
        (12, 9, 3.0), (14, 11, 4.0), (16, 14, 4.8), (18, 17, 4.8),
        (20, 20, 4.5), (22, 23, 3.8), (24, 25, 2.0)
    ]
    for cx, cy, w in pod_curve:
        for ox in range(int(-w), int(w) + 1):
            for oy in range(int(-w), int(w) + 1):
                if ox*ox + oy*oy <= w*w:
                    put(p, cx + ox, cy + oy, POD_OUTER if ox < 0 else POD_INNER)

    # 4 Perfectly round glossy peas nestled in the open pod
    peas = [(14, 12), (17, 15), (19, 18), (22, 21)]
    for px, py in peas:
        fill_circle(p, px, py, 2.5, lambda dx, dy, dist:
            (255, 255, 220, 255) if (dx == -1 and dy == -1) else (
                (175, 245, 75, 255) if dist < 0.65 else (85, 185, 40, 255)
            )
        )

    apply_outline(p, (20, 45, 18, 255))
    return p

# =============================================================================
# TIER 3 CROPS
# =============================================================================

def draw_onion(info):
    """Onion: Warm golden-amber teardrop bulb with papery skin lines, tapered neck and root hairs."""
    p = new_canvas()
    STEM = (145, 88, 35, 255)
    ROOT = (215, 195, 155, 255)

    # Tapered dried neck at top (y=6..11)
    for y in range(6, 12):
        w = 1 if y < 9 else 2
        for x in range(16 - w, 16 + w + 1):
            put(p, x, y, STEM if x == 16 else (185, 120, 50, 255))

    # Round/teardrop bulb (cx=16, cy=18, rx=7.8, ry=7.5)
    for y in range(10, 26):
        for x in range(8, 25):
            dx = x - 16
            dy = y - 18
            # Teardrop shape formula
            top_taper = 1.0 + max(0.0, -dy * 0.08)
            if (dx*dx * top_taper) / (7.8*7.8) + (dy*dy) / (7.5*7.5) <= 1.0:
                # 3D lighting from top-left
                dist = math.hypot(dx + 2.5, dy + 2.0) / 7.5
                light = 1.25 - dist * 0.7
                # Onion skin striations
                striation = (abs(dx) in (0, 3, 5, 7)) and (y % 2 == 0)
                if striation:
                    c = (155, 82, 25, 255)
                elif light < 0.65:
                    c = (175, 98, 35, 255)
                elif light < 0.95:
                    c = (225, 155, 62, 255)
                elif light < 1.18:
                    c = (252, 198, 105, 255)
                else:
                    c = (255, 235, 165, 255) # glossy skin highlight
                put(p, x, y, c)

    # Roots at bottom (y=25..28)
    for rx, ry in [(15, 26), (16, 26), (17, 26), (14, 27), (16, 27), (17, 28), (15, 28)]:
        put(p, rx, ry, ROOT)

    apply_outline(p, (42, 22, 10, 255))
    return p

def draw_red_cabbage(info):
    """Red Cabbage: Dense round purple/magenta cabbage head with intricate white-violet leaf veins."""
    p = new_canvas()
    DARK = (65, 18, 75, 255)
    MID = (125, 38, 135, 255)
    LIGHT = (185, 75, 195, 255)
    VEIN = (245, 215, 252, 255)

    cx, cy, r = 16, 17, 8.5
    for y in range(8, 26):
        for x in range(7, 26):
            dx, dy = x - cx, y - cy
            ruffle = math.sin(math.atan2(dy, dx) * 6.0) * 1.0
            if math.hypot(dx, dy) + ruffle <= r:
                dist = math.hypot(dx + 2.5, dy + 2.5) / r
                f = 1.2 - dist * 0.6
                c = DARK if f < 0.7 else (MID if f < 1.05 else LIGHT)
                put(p, x, y, c)

    # Intricate white/violet branching vein network
    vein_coords = [
        # Central rib
        (16, 12), (16, 13), (16, 14), (16, 15), (16, 16), (16, 17), (16, 18), (16, 19), (16, 20),
        # Left branches
        (15, 13), (14, 12), (13, 12), (12, 11),
        (15, 15), (14, 15), (13, 14), (12, 14), (11, 15),
        (15, 17), (14, 18), (13, 18), (12, 19),
        # Right branches
        (17, 13), (18, 12), (19, 12), (20, 11),
        (17, 15), (18, 15), (19, 14), (20, 14), (21, 15),
        (17, 18), (18, 18), (19, 19), (20, 19)
    ]
    for vx, vy in vein_coords:
        put(p, vx, vy, VEIN)

    apply_outline(p, (30, 8, 38, 255))
    return p

def draw_bottle_gourd(info):
    """Bottle Gourd: Classic calabash double-bulb with celadon green skin, woody stem and smooth 3D depth."""
    p = new_canvas()
    STEM = (75, 105, 52, 255)
    for x, y in [(16, 5), (16, 6), (17, 6), (16, 7)]:
        put(p, x, y, STEM)

    # Upper bulb: cx=16, cy=11, rx=4.5, ry=3.8
    # Waist at y=14
    # Lower bulb: cx=16, cy=20, rx=7.5, ry=6.5
    for y in range(7, 28):
        for x in range(8, 25):
            dx = x - 16
            if y <= 14:
                # Upper bulb
                dy = y - 11
                d = (dx*dx) / (4.5*4.5) + (dy*dy) / (4.0*4.0)
            else:
                # Lower bulb
                dy = y - 20
                d = (dx*dx) / (7.5*7.5) + (dy*dy) / (6.8*6.8)
            if d <= 1.0:
                dist = math.hypot(dx + 2.5, (y - 15) * 0.5 + 2.0) / 8.0
                light = 1.25 - dist * 0.7
                if light < 0.65:
                    c = (85, 145, 75, 255) # celadon shadow
                elif light < 0.95:
                    c = (135, 195, 115, 255)
                elif light < 1.18:
                    c = (185, 235, 165, 255)
                else:
                    c = (235, 255, 215, 255) # specular gleam
                put(p, x, y, c)

    apply_outline(p, (28, 55, 25, 255))
    return p

def draw_celery(info):
    """Celery: Crisp bunch of ribbed pale-green stalks tightly bound at base with feathery leaf tops."""
    p = new_canvas()
    LEAF_D = (38, 105, 38, 255)
    LEAF_L = (95, 185, 65, 255)
    STALK_D = (95, 165, 80, 255)
    STALK_M = (145, 215, 125, 255)
    STALK_L = (205, 245, 180, 255)
    BASE = (235, 250, 215, 255)

    # Feathery leaf tops (y=4..12)
    for ox, oy in [
        (-4, 0), (-3, -2), (-2, -3), (-1, -1), (0, -4), (1, -2), (2, -3), (3, -1), (4, 0),
        (-3, 1), (-1, 2), (1, 1), (3, 2), (0, 0)
    ]:
        put(p, 16 + ox, 8 + oy, LEAF_L if oy < 0 else LEAF_D)

    # 3 Parallel ribbed stalks (y=11..27)
    stalks = [
        (13, STALK_D, STALK_M), # left stalk
        (16, STALK_M, STALK_L), # center stalk
        (19, STALK_D, STALK_M)  # right stalk
    ]
    for sx, col_d, col_l in stalks:
        for y in range(11, 27):
            put(p, sx - 1, y, col_d)
            put(p, sx, y, col_l)
            put(p, sx + 1, y, col_d)

    # Pale base heart (y=25..28)
    for y in range(25, 29):
        for x in range(12, 21):
            put(p, x, y, BASE if y == 28 else STALK_L)

    apply_outline(p, (25, 55, 22, 255))
    return p

def draw_broccoli(info):
    """Broccoli: Sturdy branching stalk crowned with a dense round dome of granulated dark forest florets."""
    p = new_canvas()
    STALK_D = (85, 155, 75, 255)
    STALK_L = (155, 225, 135, 255)
    FLORET_D = (22, 65, 28, 255)
    FLORET_M = (45, 115, 50, 255)
    FLORET_L = (85, 175, 75, 255)
    FLORET_HI = (135, 215, 105, 255)

    # Stalk at bottom (y=18..27, width=6 tapering to 5)
    for y in range(18, 28):
        for x in range(14, 19):
            dx = x - 14
            put(p, x, y, STALK_L if dx in (1, 2) else STALK_D)

    # Dense floret dome (y=6..19, cx=16, cy=13, r=9.5)
    for y in range(6, 20):
        for x in range(6, 27):
            dx = x - 16
            dy = y - 13
            d = math.hypot(dx, dy * 1.15)
            # Clustered floret bumps
            cluster = math.sin(dx * 1.2) * math.cos(dy * 1.2) * 1.5
            if d + cluster <= 9.8:
                dist = math.hypot(dx + 3.0, dy + 3.0) / 9.5
                f = 1.25 - dist * 0.65
                # Granulated texture from hash
                granule = ((x * 13 + y * 7) % 5) / 4.0 - 0.5
                val = f + granule * 0.35
                if val < 0.60:
                    c = FLORET_D
                elif val < 0.95:
                    c = FLORET_M
                elif val < 1.20:
                    c = FLORET_L
                else:
                    c = FLORET_HI
                put(p, x, y, c)

    apply_outline(p, (16, 42, 20, 255))
    return p

def draw_star_anise(info):
    """Star Anise: Symmetrical 8-pointed star spice pod in warm mahogany brown, each ray holding an amber seed."""
    p = new_canvas()
    cx, cy = 15.5, 15.5
    BROWN_D = (85, 42, 20, 255)
    BROWN_M = (145, 75, 35, 255)
    BROWN_L = (195, 115, 55, 255)
    SEED = (255, 205, 65, 255)
    SEED_GLINT = (255, 255, 205, 255)

    # 8 radiating points (angles: 0, 45, 90, 135, 180, 225, 270, 315 deg)
    for deg in range(0, 360, 45):
        rad = math.radians(deg)
        cos_a, sin_a = math.cos(rad), math.sin(rad)
        # Ray extends from dist 3 to 10
        for dist in range(3, 11):
            px = int(round(cx + cos_a * dist))
            py = int(round(cy + sin_a * dist))
            # Width orthogonal
            ox = int(round(-sin_a * 1.1))
            oy = int(round(cos_a * 1.1))
            put(p, px, py, BROWN_L if deg in (225, 270, 315) else BROWN_M)
            if dist <= 8:
                put(p, px + ox, py + oy, BROWN_D)
                put(p, px - ox, py - oy, BROWN_D)
            # Golden seed inside boat opening (dist=5..6)
            if dist == 6:
                put(p, px, py, SEED)
                put(p, px, py - 1, SEED_GLINT)

    # Center hub
    fill_circle(p, 16, 16, 2.5, lambda dx, dy, d: BROWN_D if d > 0.6 else BROWN_M)

    apply_outline(p, (42, 18, 10, 255))
    return p

# =============================================================================
# TIER 4 CROPS (Mining & Utility - with subtle arcane luster)
# =============================================================================

def draw_turnip(info):
    """Turnip: Bicolored bulb with vibrant purple shoulder, porcelain white lower half, taproot and green tops."""
    p = new_canvas()
    LEAF_D = (40, 95, 40, 255)
    LEAF_L = (110, 195, 75, 255)

    # Trimmed green stem tops (y=5..10)
    for sx, sy, col in [
        (14, 6, LEAF_L), (14, 7, LEAF_D), (14, 8, LEAF_D),
        (16, 5, LEAF_L), (16, 6, LEAF_L), (16, 7, LEAF_D), (16, 8, LEAF_D),
        (18, 6, LEAF_L), (18, 7, LEAF_D), (18, 8, LEAF_D)
    ]:
        put(p, sx, sy, col)

    # Bulb (cx=16, cy=18, r=7.8)
    for y in range(9, 28):
        for x in range(8, 25):
            dx = x - 16
            dy = y - 18
            w = 7.5 * math.sqrt(max(0.0, 1.0 - (dy / 7.5)**2)) if dy < 2 else max(0.8, 7.0 - (dy - 2) * 0.9)
            if abs(dx) <= w:
                lx = dx + 2.5
                ly = dy + 2.5
                dist = math.hypot(lx, ly) / 7.5
                light = 1.25 - dist * 0.7
                if y < 16:
                    # Vibrant purple crown
                    if light < 0.65:
                        c = (115, 25, 95, 255)
                    elif light < 0.95:
                        c = (175, 45, 145, 255)
                    elif light < 1.15:
                        c = (225, 95, 195, 255)
                    else:
                        c = (255, 165, 235, 255)
                else:
                    # Porcelain white lower half
                    if light < 0.75:
                        c = (195, 205, 218, 255)
                    elif light < 1.05:
                        c = (235, 240, 245, 255)
                    else:
                        c = (255, 255, 255, 255)
                put(p, x, y, c)

    # Winding root tip (y=28..30)
    put(p, 16, 28, (185, 195, 205, 255))
    put(p, 17, 29, (165, 175, 185, 255))
    put(p, 17, 30, (145, 155, 165, 255))

    apply_outline(p, (35, 14, 30, 255))
    return p

def draw_chestnut(info):
    """Chestnut: Glossy plump mahogany nut with matte tan hilum patch and spiky green husk fragment."""
    p = new_canvas()
    cx, cy = 16, 17
    # Spiky husk fragment at left (x=6..10)
    for hx, hy in [(7, 13), (6, 14), (7, 15), (6, 17), (7, 18), (8, 19), (7, 21)]:
        put(p, hx, hy, (145, 175, 55, 255))
        put(p, hx + 1, hy, (85, 115, 35, 255))

    # Heart-shaped nut body (cx=16, cy=17, r=7.5)
    for y in range(10, 26):
        for x in range(9, 24):
            dx = x - cx
            dy = y - cy
            # Heart/teardrop point at top
            w = 7.5 * (1.0 - ((y - 17) / 10.0)**2 if y >= 17 else 1.0 - (17 - y) * 0.08)
            if abs(dx) <= w:
                if y >= 21:
                    # Matte tan hilum base patch
                    c = (225, 185, 135, 255) if (x + y) % 2 == 0 else (195, 155, 105, 255)
                else:
                    # Polished mahogany nut shell
                    dist = math.hypot(dx + 2.5, dy + 2.5) / 7.5
                    light = 1.3 - dist * 0.75
                    if light < 0.65:
                        c = (95, 38, 18, 255)
                    elif light < 0.95:
                        c = (155, 68, 30, 255)
                    elif light < 1.18:
                        c = (215, 115, 55, 255)
                    else:
                        c = (255, 185, 115, 255)
                put(p, x, y, c)

    # Specular glint on curved shell
    put(p, 13, 13, (255, 240, 220, 255))
    put(p, 14, 13, (255, 255, 255, 255))

    apply_outline(p, (38, 15, 8, 255))
    return p

def draw_cassava(info):
    """Cassava: Rough woody bark tuber with stark chalky white starchy interior cut reveal."""
    p = new_canvas()
    BARK_D = (75, 45, 25, 255)
    BARK_M = (125, 78, 45, 255)
    BARK_L = (175, 115, 68, 255)
    FLESH_L = (255, 255, 252, 255)
    FLESH_M = (235, 238, 230, 255)
    FLESH_D = (195, 200, 190, 255)

    # Cylindrical tuber running diagonally from top-left (10, 8) down-right to (23, 23)
    for t in range(16):
        cx = 9 + t * 0.9
        cy = 8 + t * 1.0
        for ox in range(-3, 4):
            for oy in range(-3, 4):
                if ox*ox + oy*oy <= 9:
                    put(p, int(cx + ox), int(cy + oy), BARK_M if ox < 0 else BARK_D)

    # Bark fibrous texture
    for bx, by in [(10, 10), (12, 13), (14, 16), (16, 18), (18, 20), (20, 22)]:
        put(p, bx, by, BARK_L)

    # Clean circular cross-section cut at top-left revealing white starchy core
    fill_ellipse(p, 10, 10, 4.2, 4.2, lambda dx, dy, dist, nx, ny:
        (165, 135, 110, 255) if dist > 0.85 else ( # thin peel ring
            (215, 215, 205, 255) if (dx == 0 and dy == 0) else ( # center dot
                FLESH_L if (dx < 0 and dy < 0) else FLESH_M
            )
        )
    )

    apply_outline(p, (35, 20, 12, 255))
    return p

def draw_spinach(info):
    """Spinach: Bouquet of rich deep green spade-shaped leaves with delicate lighter veins."""
    p = new_canvas()
    DARK = (28, 85, 32, 255)
    MID = (55, 145, 58, 255)
    LIGHT = (115, 205, 95, 255)
    VEIN = (185, 245, 155, 255)
    STEM = (48, 120, 45, 255)

    # 3 Spade leaves (Back left, Back right, Front center)
    leaves = [
        (12, 13, 5.0, -0.3),
        (20, 13, 5.0, 0.3),
        (16, 17, 6.2, 0.0)
    ]
    for lx, ly, lr, rot in leaves:
        fill_ellipse(p, lx, ly, lr, lr * 1.25, lambda dx, dy, dist, nx, ny:
            DARK if dist > 0.75 else (MID if dist > 0.40 else LIGHT)
        )
        # Vein network
        clx, cly = int(lx), int(ly)
        for vy in range(int(ly - lr * 0.8), int(ly + lr * 0.8)):
            put(p, clx, vy, VEIN)
        put(p, clx - 2, cly - 1, VEIN); put(p, clx + 2, cly - 1, VEIN)
        put(p, clx - 2, cly + 2, VEIN); put(p, clx + 2, cly + 2, VEIN)

    # Stems gathering at base (y=22..28)
    for y in range(22, 29):
        put(p, 15, y, STEM); put(p, 16, y, LIGHT); put(p, 17, y, STEM)

    apply_outline(p, (18, 48, 22, 255))
    return p

def draw_grape(info):
    """Grape: Hanging cluster of deep royal violet grapes with frosted bloom and curling vine tendril."""
    p = new_canvas()
    STEM = (95, 65, 38, 255)
    TENDRIL = (115, 195, 75, 255)
    LEAF = (65, 155, 52, 255)

    # Vine stem and leaf at top (y=4..10)
    for sx, sy in [(16, 5), (16, 6), (16, 7), (17, 7)]:
        put(p, sx, sy, STEM)
    # Vine leaf
    for lx, ly in [(18, 5), (19, 5), (20, 6), (19, 6), (20, 7)]:
        put(p, lx, ly, LEAF)
    # Curly tendril
    for tx, ty in [(13, 6), (12, 7), (13, 8), (14, 8)]:
        put(p, tx, ty, TENDRIL)

    # Cluster of grapes arranged in inverted cone (y=8..26)
    grape_positions = [
        # Top tier
        (13, 10), (16, 10), (19, 10),
        # Second tier
        (11, 14), (14, 14), (17, 14), (20, 14),
        # Third tier
        (13, 18), (16, 18), (19, 18),
        # Fourth tier
        (14, 22), (17, 22),
        # Tip
        (16, 25)
    ]
    for gx, gy in grape_positions:
        fill_circle(p, gx, gy, 2.8, lambda dx, dy, dist:
            (215, 195, 255, 255) if (dx == -1 and dy == -1) else ( # frosted bloom
                (145, 85, 215, 255) if dist < 0.55 else (
                    (85, 38, 145, 255) if dist < 0.85 else (45, 18, 85, 255)
                )
            )
        )

    apply_outline(p, (25, 12, 45, 255))
    return p

def draw_bell_pepper(info):
    """Bell Pepper: Blocky 3-lobed sweet pepper in radiant golden-amber with indented green stem."""
    p = new_canvas()
    STEM = (52, 115, 42, 255)
    STEM_L = (115, 195, 75, 255)

    # Indented stem cavity (y=6..10)
    for sx, sy in [(16, 6), (16, 7), (16, 8), (17, 8)]:
        put(p, sx, sy, STEM)
    put(p, 16, 6, STEM_L)

    # Blocky 3-lobed body: Left lobe (11, 18), Center lobe (16, 19), Right lobe (21, 18)
    # Lobes
    lobes = [
        (11.5, 18.0, 4.5, 7.0),
        (16.0, 18.5, 4.8, 7.2),
        (20.5, 18.0, 4.5, 7.0)
    ]
    for lx, ly, lrx, lry in lobes:
        fill_ellipse(p, lx, ly, lrx, lry, lambda dx, dy, dist, nx, ny:
            (255, 235, 165, 255) if (dx < -1.0 and dy < -2.0) else (
                (252, 185, 45, 255) if dist < 0.70 else (
                    (215, 125, 22, 255) if dist < 0.90 else (155, 75, 14, 255)
                )
            )
        )

    # Vertical indent grooves separating the 3 lobes
    for gy in range(12, 25):
        put(p, 13, gy, (135, 62, 10, 255))
        put(p, 18, gy, (145, 68, 12, 255))

    apply_outline(p, (38, 18, 8, 255))
    return p

# =============================================================================
# TIER 5 CROPS (Mythic Arcana - featuring magical luminescence & legendary craft)
# =============================================================================

def draw_sweet_potato(info):
    """Sweet Potato: Tapered spindle root in copper-magenta skin, with glowing golden apricot flesh cut."""
    p = new_canvas()
    SKIN_D = (95, 28, 48, 255)
    SKIN_M = (165, 55, 78, 255)
    SKIN_L = (215, 95, 120, 255)
    FLESH_CORE = (255, 235, 120, 255)
    FLESH_L = (255, 175, 55, 255)
    FLESH_M = (225, 115, 32, 255)

    # Tapered diagonal oblong body from (8, 9) to (24, 24)
    for t in range(18):
        cx = 7.5 + t * 0.95
        cy = 8.5 + t * 0.90
        w = 5.2 * math.sin((t + 1) / 19.0 * math.pi)
        for ox in range(int(-w), int(w) + 1):
            for oy in range(int(-w), int(w) + 1):
                if ox*ox + oy*oy <= w*w:
                    put(p, int(cx + ox), int(cy + oy), SKIN_M if ox < 0 else SKIN_D)

    # Glowing diagonal interior cut reveal
    fill_ellipse(p, 14, 14, 4.0, 4.0, lambda dx, dy, dist, nx, ny:
        (135, 35, 60, 255) if dist > 0.85 else ( # skin ring
            FLESH_CORE if dist < 0.4 else (
                FLESH_L if dist < 0.75 else FLESH_M
            )
        )
    )

    # Astral motes / sparkles
    for sx, sy in [(5, 8), (26, 24), (25, 7), (8, 26)]:
        put(p, sx, sy, (235, 185, 255, 255))

    apply_outline(p, (38, 10, 22, 255))
    return p

def draw_asparagus(info):
    """Asparagus: Bundle of three fresh spears with distinct triangular purple-tipped scaled bracts."""
    p = new_canvas()
    STALK_D = (45, 115, 45, 255)
    STALK_M = (78, 165, 72, 255)
    STALK_L = (135, 215, 125, 255)
    TIP_PURPLE = (145, 65, 155, 255)
    TIP_L = (205, 135, 215, 255)
    GOLD_BAND = (245, 205, 65, 255)

    # 3 Spears (Left: x=11..13, Center: x=15..17, Right: x=19..21)
    spears = [
        (12, 7),  # left spear tip y=7
        (16, 5),  # center tall spear tip y=5
        (20, 8)   # right spear tip y=8
    ]
    for sx, tip_y in spears:
        # Cylindrical stalk down to y=27
        for y in range(tip_y + 6, 28):
            put(p, sx - 1, y, STALK_D)
            put(p, sx, y, STALK_L)
            put(p, sx + 1, y, STALK_M)

        # Scale bracts on stalk
        for by in range(tip_y + 7, 26, 4):
            put(p, sx, by, TIP_PURPLE)

        # Pointed scale-tipped spear head (from tip_y to tip_y + 6)
        for dy in range(6):
            y = tip_y + dy
            w = 1 if dy <= 2 else 2
            for ox in range(-w, w + 1):
                col = TIP_L if (ox <= 0 and dy <= 2) else (
                    TIP_PURPLE if dy <= 3 else STALK_L
                )
                put(p, sx + ox, y, col)

    # Golden mythic vine tie binding the bundle (y=19..20)
    for x in range(10, 23):
        put(p, x, 19, GOLD_BAND)
        put(p, x, 20, (185, 145, 35, 255))

    apply_outline(p, (22, 45, 22, 255))
    return p

def draw_fig(info):
    """Fig: Teardrop ripe fig with rich purple-to-green gradient, cleft opening with ruby nectar core."""
    p = new_canvas()
    STEM = (85, 105, 45, 255)
    for x, y in [(16, 5), (16, 6), (17, 6), (16, 7)]:
        put(p, x, y, STEM)

    # Teardrop body (cx=16, cy=18, rx=7.5, ry=8.2, wider at base y=21)
    for y in range(8, 27):
        for x in range(8, 25):
            dx = x - 16
            dy = y - 18
            w = 4.0 + (y - 8) * 0.35 if y < 20 else max(1.0, 8.0 - (y - 20) * 1.1)
            if abs(dx) <= w:
                # Vertical gradient: Deep purple top -> Wine red mid -> Amber-green base
                t = (y - 8) / 19.0
                if t < 0.40:
                    base_c = (65, 22, 75, 255) # royal violet
                elif t < 0.75:
                    base_c = (135, 38, 95, 255) # ripe fig magenta
                else:
                    base_c = (145, 155, 65, 255) # dusky yellow-green

                dist = math.hypot(dx + 2.5, dy + 2.0) / 8.0
                light = 1.25 - dist * 0.7
                c = shade(base_c, light)
                put(p, x, y, c)

    # Open split eye at bottom revealing glistening ruby jewel nectar
    for nx, ny in [(15, 24), (16, 24), (16, 25), (17, 24)]:
        put(p, nx, ny, (235, 42, 75, 255))
    put(p, 16, 24, (255, 165, 195, 255)) # glistening glint

    # Specular gloss on shoulder
    put(p, 13, 14, (235, 185, 245, 255))

    apply_outline(p, (30, 12, 35, 255))
    return p

def draw_mint(info):
    """Mint: Tender stem with cross-pairs of serrated aromatic leaves, ethereal veins and dewdrops."""
    p = new_canvas()
    STEM = (52, 145, 85, 255)
    LEAF_D = (35, 115, 65, 255)
    LEAF_M = (65, 175, 105, 255)
    LEAF_L = (125, 235, 165, 255)
    VEIN = (185, 255, 215, 255)
    DEW = (245, 255, 255, 255)

    # Central tender stem (y=6..27)
    for y in range(6, 28):
        put(p, 16, y, STEM)

    # Top small pair of leaves (y=7..11)
    for ox, oy, col in [
        (-2, 0, LEAF_L), (-3, -1, LEAF_M), (-4, 0, LEAF_D),
        (2, 0, LEAF_L), (3, -1, LEAF_M), (4, 0, LEAF_D),
        (0, -2, LEAF_L), (0, -3, LEAF_M)
    ]:
        put(p, 16 + ox, 8 + oy, col)

    # Middle larger pair of serrated leaves (y=11..19)
    # Left leaf
    fill_ellipse(p, 11, 14, 4.8, 3.2, lambda dx, dy, dist, nx, ny:
        VEIN if (abs(dy) < 0.6) else (LEAF_L if dx > 0 else LEAF_M)
    )
    # Right leaf
    fill_ellipse(p, 21, 14, 4.8, 3.2, lambda dx, dy, dist, nx, ny:
        VEIN if (abs(dy) < 0.6) else (LEAF_L if dx < 0 else LEAF_M)
    )
    # Serrated notches on middle leaves
    for nx, ny in [(7, 13), (7, 15), (15, 13), (15, 15), (17, 13), (17, 15), (25, 13), (25, 15)]:
        put(p, nx, ny, LEAF_D)

    # Bottom pair of leaves (y=17..25)
    fill_ellipse(p, 10, 21, 5.5, 3.8, lambda dx, dy, dist, nx, ny:
        VEIN if (abs(dy) < 0.6) else (LEAF_M if dy < 0 else LEAF_D)
    )
    fill_ellipse(p, 22, 21, 5.5, 3.8, lambda dx, dy, dist, nx, ny:
        VEIN if (abs(dy) < 0.6) else (LEAF_M if dy < 0 else LEAF_D)
    )

    # Glistening ethereal dewdrops
    put(p, 11, 13, DEW); put(p, 20, 13, DEW)

    apply_outline(p, (18, 55, 32, 255))
    return p

def draw_chili_pepper(info):
    """Chili Pepper: Slender curved fiery hot chili tapering to a sharp curling tail with glossy sheen."""
    p = new_canvas()
    STEM = (48, 125, 42, 255)
    CALYX = (75, 175, 60, 255)

    # Green stem at top (y=4..8)
    for sx, sy in [(14, 5), (14, 6), (15, 7), (15, 8)]:
        put(p, sx, sy, STEM)

    # Green flared calyx collar (y=8..10)
    for x in range(13, 19):
        put(p, x, 9, CALYX)
    put(p, 12, 10, STEM); put(p, 19, 10, STEM)

    # Long slender curving chili body (y=10..28)
    # Follows a parabolic sweep: x = 16 - (y - 10)*0.15 + (y - 10)**2 * 0.025
    for y in range(10, 28):
        t = y - 10 # 0..17
        curv = -t * 0.18 + (t * t) * 0.038
        cx = 15.5 + curv
        # Width tapers gracefully from 4.0 down to 0.5
        w = 3.8 * (1.0 - (t / 18.0)**1.2) + 0.4
        for ox in range(int(-w - 1), int(w + 2)):
            px = cx + ox
            if abs(px - cx) <= w:
                lx = ox + 1.2
                dist = abs(lx) / (w + 0.1)
                light = 1.3 - dist * 0.85
                if light < 0.65:
                    c = (155, 15, 15, 255) # deep burning crimson
                elif light < 0.95:
                    c = (225, 30, 20, 255) # vibrant fiery scarlet
                elif light < 1.20:
                    c = (255, 85, 35, 255) # flame orange
                else:
                    c = (255, 195, 95, 255) # white-gold heat highlight
                put(p, px, y, c)

    # Glossy white highlight gleam along the sweeping spine
    for hy in range(12, 20):
        t = hy - 10
        curv = -t * 0.18 + (t * t) * 0.038
        put(p, int(14.5 + curv), hy, (255, 220, 180, 255))
    put(p, int(14.5 + 2 * 0.02), 14, (255, 255, 255, 255))

    apply_outline(p, (42, 10, 10, 255))
    return p

def draw_pomegranate(info):
    """Pomegranate: Deep ruby leathery sphere with bottom crown calyx and fissure revealing sparkling jewel seeds."""
    p = new_canvas()
    cx, cy, r = 16, 16, 8.5

    # Short top stem
    for y in range(5, 8):
        put(p, 16, y, (95, 75, 45, 255))

    # Round pomegranate sphere
    for y in range(8, 25):
        for x in range(7, 26):
            dx = x - cx
            dy = y - cy
            if dx*dx + dy*dy <= r*r:
                dist = math.hypot(dx + 2.5, dy + 2.5) / r
                light = 1.25 - dist * 0.75
                if light < 0.60:
                    c = (115, 15, 32, 255) # garnet shadow
                elif light < 0.90:
                    c = (175, 28, 52, 255) # ruby rind
                elif light < 1.15:
                    c = (225, 55, 82, 255) # radiant red
                else:
                    c = (255, 135, 155, 255) # leathery highlight
                put(p, x, y, c)

    # Crown-shaped calyx teeth at the base (y=24..27)
    for cx_tooth, cy_tooth in [(13, 25), (14, 26), (16, 27), (18, 26), (19, 25)]:
        put(p, cx_tooth, cy_tooth, (135, 22, 40, 255))
        put(p, cx_tooth, cy_tooth - 1, (95, 15, 28, 255))

    # Fissure opening showing glittering ruby gemstone aril seeds
    fissure_pixels = [
        (13, 14), (14, 13), (14, 14), (15, 13), (15, 14), (15, 15),
        (16, 14), (16, 15), (17, 14), (17, 15), (18, 15), (18, 16)
    ]
    for fx, fy in fissure_pixels:
        put(p, fx, fy, (255, 45, 85, 255)) # ruby seed
    # Diamond glints on seeds
    for gx, gy in [(14, 13), (16, 14), (17, 15)]:
        put(p, gx, gy, (255, 255, 255, 255))

    apply_outline(p, (35, 8, 16, 255))
    return p

# =============================================================================
# DISPATCH TABLE
# =============================================================================

CROP_DRAWERS = {
    # Tier 1
    'mana_dew_berry': draw_blueberry,
    'chameleon_leaf': draw_lettuce,
    'fairy_mushroom': draw_truffle,
    'magnetic_squash': draw_butternut_squash,
    'mountain_walker_bamboo': draw_leek,
    'demeters_melon': draw_papaya,

    # Tier 2
    'blood_thorn_tomato': draw_tomato,
    'frostbite_radish': draw_radish,
    'thunder_kernel_corn': draw_corn,
    'reapers_garlic': draw_garlic,
    'titan_pumpkin': draw_eggplant,
    'kinetic_pea_pod': draw_green_peas,

    # Tier 3
    'soul_ward_bulb': draw_onion,
    'void_feather_blossom': draw_red_cabbage,
    'lodestone_gourd': draw_bottle_gourd,
    'abyssal_kelp': draw_celery,
    'glider_spore': draw_broccoli,
    'star_anise': draw_star_anise,

    # Tier 4
    'fortune_beet': draw_turnip,
    'lumberjack_acorn': draw_chestnut,
    'prism_shard_carrot': draw_cassava,
    'goldleaf_herb': draw_spinach,
    'twilight_grape': draw_grape,
    'chrono_pepper': draw_bell_pepper,

    # Tier 5
    'ancient_astral_root': draw_sweet_potato,
    'yggdrasil_sprout': draw_asparagus,
    'void_overcharge_fig': draw_fig,
    'ethereal_mint': draw_mint,
    'bloodburn_chili': draw_chili_pepper,
    'omni_pomegranate': draw_pomegranate,
}

def draw_crop_food(crop_info):
    """Renders 32x32 high-detail pixel art produce for any of the 30 Evergarden crops."""
    cid = crop_info.get('id')
    drawer = CROP_DRAWERS.get(cid)
    if drawer:
        return drawer(crop_info)
    raise ValueError(f"Unknown crop id: {cid}")
