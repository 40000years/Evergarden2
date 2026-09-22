"""Generate high-detail 32x32 pixel art textures for Storm Bow and Nova Bow.
Includes idle state and 3 pulling animation stages for each bow.
Matches the rich fantasy RPG palette and shading standard of Evergarden relics.
"""
import math
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'art/equipment'
OUTPUT.mkdir(parents=True, exist_ok=True)

def png(path, pixels, size=32):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    raw = b''.join(b'\0' + bytes(c for pixel in row for c in pixel) for row in pixels)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0))
                     + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))

def blend(c1, c2, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(c1[i] * (1 - t) + c2[i] * t)) for i in range(4))

def create_grid():
    return [[(0, 0, 0, 0) for _ in range(32)] for _ in range(32)]

def line(grid, x0, y0, x1, y1, color):
    dx = abs(x1 - x0)
    dy = abs(y1 - y0)
    sx = 1 if x0 < x1 else -1
    sy = 1 if y0 < y1 else -1
    err = dx - dy
    x, y = x0, y0
    while True:
        if 0 <= x < 32 and 0 <= y < 32:
            grid[y][x] = color
        if x == x1 and y == y1:
            break
        e2 = 2 * err
        if e2 > -dy:
            err -= dy
            x += sx
        if e2 < dx:
            err += dx
            y += sy


# =============================================================================
# STORM BOW (Storm Verdict - ธนูพิพากษาสายฟ้า)
# =============================================================================
def generate_storm_bow(draw=0):
    grid = create_grid()

    def put(x, y, c):
        ix, iy = int(round(x)), int(round(y))
        if 0 <= ix < 32 and 0 <= iy < 32:
            grid[iy][ix] = c

    def put_sym(x, y, c):
        put(x, y, c)
        if x != y:
            put(y, x, c)

    OUT         = (18, 16, 26, 255)  # Dark shadow outline
    STEEL_DARK  = (40, 48, 64, 255)
    STEEL_MID   = (75, 90, 118, 255)
    STEEL_LIGHT = (135, 158, 192, 255)
    STEEL_SPEC  = (215, 232, 255, 255)

    GOLD_DARK   = (115, 75, 18, 255)
    GOLD_MID    = (210, 160, 42, 255)
    GOLD_HI     = (248, 212, 75, 255)
    GOLD_SUN    = (255, 248, 180, 255)

    CYAN_DARK   = (12, 58, 92, 255)
    CYAN_DEEP   = (18, 102, 150, 255)
    CYAN_MID    = (35, 180, 225, 255)
    CYAN_LIGHT  = (135, 238, 255, 255)
    CYAN_WHITE  = (230, 252, 255, 255)
    ELEC_WHITE  = (255, 255, 255, 255)

    # Tip positions: flex inwards slightly when drawn
    if draw == 0:
        tip_x, tip_y = 27, 12
    elif draw == 1:
        tip_x, tip_y = 27, 12
    elif draw == 2:
        tip_x, tip_y = 26, 13
    else:
        tip_x, tip_y = 25, 13

    # 1. Outer Dark Silhouette Border
    outer_outline = [
        (11, 8), (12, 8), (13, 7), (14, 6), (15, 5), (16, 5), (17, 5),
        (18, 3), (19, 3), (20, 2), (21, 3), (21, 4), (20, 5), # Feather spur top
        (21, 5), (22, 6), (23, 7), (24, 7), (25, 8), (26, 9), (27, 9),
        (28, 10), (29, 10), (30, 11), (30, 12), (29, 13), (28, 14) # Talon tip
    ]
    for ox, oy in outer_outline:
        put_sym(ox, oy, OUT)

    # 2. Outer Bevel & Titanium Spine (Back of Bow)
    outer_bevel = [
        (11, 9, STEEL_MID), (12, 9, GOLD_MID), (13, 8, STEEL_SPEC),
        (14, 7, STEEL_SPEC), (15, 6, STEEL_LIGHT), (16, 6, STEEL_SPEC),
        (17, 6, STEEL_LIGHT), (21, 6, STEEL_SPEC), (22, 7, STEEL_LIGHT),
        (23, 8, STEEL_MID), (24, 8, GOLD_HI), (25, 9, GOLD_SUN),
        (26, 10, GOLD_HI), (27, 10, GOLD_SUN), (28, 11, GOLD_MID),
        (29, 11, GOLD_HI), (29, 12, GOLD_DARK)
    ]
    for bx, by, bc in outer_bevel:
        put_sym(bx, by, bc)

    # 3. Thunderbird Feather Spurs (Wing blades branching off mid-limb)
    feather_spur = [
        (17, 4, GOLD_DARK), (18, 4, GOLD_HI), (19, 4, GOLD_SUN),
        (20, 3, CYAN_LIGHT), (20, 4, CYAN_WHITE),
        (18, 5, GOLD_MID), (19, 5, CYAN_MID), (20, 5, CYAN_LIGHT)
    ]
    for fx, fy, fc in feather_spur:
        put_sym(fx, fy, fc)

    # 4. Lightning Channel (Core of Limbs)
    lightning_channel = [
        (12, 10, CYAN_DEEP), (13, 9, CYAN_MID), (14, 8, CYAN_LIGHT),
        (15, 7, CYAN_WHITE), (16, 7, CYAN_LIGHT), (17, 7, CYAN_MID),
        (18, 7, CYAN_LIGHT), (19, 7, CYAN_WHITE), (20, 7, CYAN_LIGHT),
        (21, 7, CYAN_MID), (22, 8, CYAN_LIGHT), (23, 9, CYAN_MID),
        (24, 9, CYAN_WHITE), (25, 10, CYAN_LIGHT), (26, 11, CYAN_WHITE),
        (27, 11, CYAN_LIGHT), (28, 12, CYAN_MID)
    ]
    for lx, ly, lc in lightning_channel:
        put_sym(lx, ly, lc)

    # 5. Inner Belly (Facing the string)
    inner_belly = [
        (13, 10, STEEL_DARK), (14, 9, STEEL_MID), (15, 8, STEEL_DARK),
        (16, 8, STEEL_MID), (17, 8, GOLD_DARK), (18, 8, GOLD_MID),
        (19, 8, STEEL_DARK), (20, 8, STEEL_MID), (21, 8, STEEL_DARK),
        (22, 9, STEEL_MID), (23, 10, GOLD_MID), (24, 10, GOLD_HI),
        (25, 11, GOLD_MID), (26, 12, GOLD_DARK), (27, 12, tip_notch_color := GOLD_SUN)
    ]
    for ix, iy, ic in inner_belly:
        put_sym(ix, iy, ic)

    # Inner Outline Border
    inner_outline = [
        (13, 11), (14, 10), (15, 9), (16, 9), (17, 9), (18, 9),
        (19, 9), (20, 9), (21, 9), (22, 10), (23, 11), (24, 11),
        (25, 12), (26, 13), (27, 13), (28, 13)
    ]
    for iox, ioy in inner_outline:
        put_sym(iox, ioy, OUT)

    # 6. Center Riser & Grip Structure
    # Wing brackets
    riser_gold = [
        (9, 11, GOLD_HI), (9, 12, GOLD_MID), (8, 11, OUT), (8, 12, OUT),
        (10, 10, GOLD_SUN), (9, 10, OUT), (10, 9, GOLD_MID), (11, 9, GOLD_HI),
        (10, 12, STEEL_DARK), (12, 10, STEEL_DARK)
    ]
    for rx, ry, rc in riser_gold:
        put_sym(rx, ry, rc)

    # Grip leather binding
    put(11, 12, GOLD_DARK); put(12, 11, GOLD_DARK)
    put(12, 12, STEEL_DARK); put(13, 12, OUT); put(12, 13, OUT)

    # Central Tempest Core Gem (Floating pulsing diamond core at 11, 11)
    put(11, 11, ELEC_WHITE)
    put(10, 11, CYAN_LIGHT); put(11, 10, CYAN_LIGHT)
    put(12, 11, CYAN_MID);   put(11, 12, CYAN_MID)
    put(10, 10, CYAN_WHITE)
    put(9, 9, OUT)

    # 7. Bow String (Electric braided lightning filament)
    # Nock position: retreats further back towards player (bottom-right) as drawn
    nocks = [(18, 18), (20, 20), (22, 22), (25, 25)]
    nock_x, nock_y = nocks[draw]

    # Draw string from upper tip to nock, and from nock to lower tip
    line(grid, tip_x, tip_y, nock_x, nock_y, CYAN_LIGHT)
    line(grid, nock_x, nock_y, tip_y, tip_x, CYAN_LIGHT)
    # String highlight nodes
    put(nock_x, nock_y, ELEC_WHITE)
    put(tip_x, tip_y, CYAN_WHITE)
    put(tip_y, tip_x, CYAN_WHITE)

    # 8. Arrows & Energy Accumulation during Draw
    if draw == 1:
        # Initial draw: Sparking lightning bolt gathering along riser
        line(grid, 9, 9, nock_x, nock_y, CYAN_MID)
        put(9, 9, CYAN_WHITE); put(8, 8, CYAN_LIGHT)
        put(nock_x, nock_y, ELEC_WHITE)
    elif draw == 2:
        # Mid draw: Formed lightning arrow
        line(grid, 10, 10, nock_x, nock_y, CYAN_LIGHT)
        put(9, 9, ELEC_WHITE) # The arrowhead follows the string without entering the player's arm.
        put(10, 9, CYAN_LIGHT); put(9, 10, CYAN_LIGHT)
        put(11, 9, CYAN_MID);   put(9, 11, CYAN_MID)
        # Nock fletchings
        put(nock_x - 1, nock_y, GOLD_HI); put(nock_x, nock_y - 1, GOLD_HI)
        put(nock_x, nock_y, ELEC_WHITE)
    elif draw == 3:
        # FULL DRAW: Overcharged Tempest Lance!
        # Arrow shaft: Blazing pure electric core + cyan glow
        line(grid, 13, 13, nock_x - 1, nock_y - 1, ELEC_WHITE)
        for i in range(13, nock_x):
            put(i + 1, i, CYAN_MID)
            put(i, i + 1, CYAN_LIGHT)

        # Broadhead Diamond Arrowhead (At 2..5, 2..5)
        arrowhead = [
            (2, 2, ELEC_WHITE), (3, 2, CYAN_WHITE), (2, 3, CYAN_WHITE),
            (3, 3, ELEC_WHITE), (4, 3, CYAN_LIGHT), (3, 4, CYAN_LIGHT),
            (4, 2, CYAN_MID),   (2, 4, CYAN_MID),   (5, 2, CYAN_DEEP),
            (2, 5, CYAN_DEEP),  (4, 4, ELEC_WHITE), (5, 3, CYAN_LIGHT),
            (3, 5, CYAN_LIGHT), (5, 4, CYAN_MID),   (4, 5, CYAN_MID),
            # Arrowhead dark outline
            (1, 2, OUT), (2, 1, OUT), (3, 1, OUT), (1, 3, OUT),
            (5, 1, OUT), (1, 5, OUT), (6, 2, OUT), (2, 6, OUT)
        ]
        for ax, ay, *ac in arrowhead:
            put(ax + 8, ay + 8, ac[0] if ac else OUT)

        # Tempest Wing Fletchings (Gold + Cyan thunderbird feathers at nock)
        fletch = [
            (nock_x - 3, nock_y - 1, GOLD_HI), (nock_x - 4, nock_y - 1, CYAN_LIGHT),
            (nock_x - 1, nock_y - 3, GOLD_HI), (nock_x - 1, nock_y - 4, CYAN_LIGHT),
            (nock_x - 2, nock_y, GOLD_SUN),    (nock_x, nock_y - 2, GOLD_SUN),
            (nock_x - 3, nock_y + 1, OUT),     (nock_x + 1, nock_y - 3, OUT),
            (nock_x, nock_y, ELEC_WHITE),      (nock_x + 1, nock_y + 1, GOLD_DARK)
        ]
        for fx, fy, fc in fletch:
            put(fx, fy, fc)

    return grid


# =============================================================================
# NOVA BOW (Supernova Star Bow - ธนูสะเก็ดดาว)
# =============================================================================
def generate_nova_bow(draw=0):
    grid = create_grid()

    def put(x, y, c):
        ix, iy = int(round(x)), int(round(y))
        if 0 <= ix < 32 and 0 <= iy < 32:
            grid[iy][ix] = c

    def put_sym(x, y, c):
        put(x, y, c)
        if x != y:
            put(y, x, c)

    OUT            = (16, 12, 24, 255)  # Void shadow outline
    VOID_DEEP      = (35, 18, 54, 255)
    VOID_DARK      = (58, 28, 88, 255)
    VOID_MID       = (102, 48, 144, 255)
    VOID_LIGHT     = (158, 92, 210, 255)

    GOLD_DARK      = (125, 80, 22, 255)
    GOLD_MID       = (215, 165, 45, 255)
    GOLD_HI        = (252, 218, 80, 255)
    GOLD_SUN       = (255, 250, 190, 255)

    NEBULA_MAGENTA = (205, 40, 135, 255)
    NEBULA_PINK    = (248, 100, 190, 255)
    STAR_AMETHYST  = (190, 115, 250, 255)
    STAR_CYAN      = (105, 225, 255, 255)
    STAR_WHITE     = (255, 245, 255, 255)
    SUN_WHITE      = (255, 255, 255, 255)

    # Tip positions: flex inwards slightly when drawn
    if draw == 0:
        tip_x, tip_y = 27, 12
    elif draw == 1:
        tip_x, tip_y = 27, 12
    elif draw == 2:
        tip_x, tip_y = 26, 13
    else:
        tip_x, tip_y = 25, 13

    # 1. Outer Dark Silhouette Border
    outer_outline = [
        (11, 8), (12, 8), (13, 7), (14, 6), (15, 5), (16, 5), (17, 5),
        (18, 3), (19, 3), (20, 2), (21, 3), (21, 4), (20, 5), # Stardust crescent spire
        (21, 5), (22, 6), (23, 7), (24, 7), (25, 8), (26, 9), (27, 9),
        (28, 10), (29, 10), (30, 11), (30, 12), (29, 13), (28, 14) # Starburst tip
    ]
    for ox, oy in outer_outline:
        put_sym(ox, oy, OUT)

    # 2. Outer Bevel & Celestial Gold Trim (Back of Bow)
    outer_bevel = [
        (11, 9, GOLD_DARK), (12, 9, GOLD_MID), (13, 8, GOLD_HI),
        (14, 7, GOLD_SUN),  (15, 6, GOLD_HI),  (16, 6, GOLD_SUN),
        (17, 6, GOLD_HI),   (21, 6, GOLD_SUN), (22, 7, GOLD_HI),
        (23, 8, GOLD_MID),  (24, 8, GOLD_HI),  (25, 9, GOLD_SUN),
        (26, 10, GOLD_HI),  (27, 10, GOLD_SUN), (28, 11, GOLD_MID),
        (29, 11, GOLD_HI),  (29, 12, GOLD_DARK)
    ]
    for bx, by, bc in outer_bevel:
        put_sym(bx, by, bc)

    # 3. Astral Starburst Spire (Mid-limb celestial crescent)
    star_spire = [
        (17, 4, VOID_DARK), (18, 4, STAR_AMETHYST), (19, 4, NEBULA_PINK),
        (20, 3, STAR_WHITE), (20, 4, STAR_CYAN),
        (18, 5, GOLD_HI), (19, 5, NEBULA_MAGENTA), (20, 5, STAR_AMETHYST)
    ]
    for sx, sy, sc in star_spire:
        put_sym(sx, sy, sc)

    # 4. Nebula Void Channel & Constellation Runes (Limb Core)
    nebula_channel = [
        (12, 10, VOID_DARK), (13, 9, VOID_MID), (14, 8, STAR_AMETHYST),
        (15, 7, NEBULA_PINK), (16, 7, STAR_WHITE), (17, 7, NEBULA_MAGENTA),
        (18, 7, VOID_LIGHT), (19, 7, STAR_CYAN), (20, 7, STAR_WHITE),
        (21, 7, NEBULA_PINK), (22, 8, STAR_AMETHYST), (23, 9, VOID_MID),
        (24, 9, NEBULA_MAGENTA), (25, 10, STAR_WHITE), (26, 11, NEBULA_PINK),
        (27, 11, STAR_CYAN), (28, 12, STAR_AMETHYST)
    ]
    for lx, ly, lc in nebula_channel:
        put_sym(lx, ly, lc)

    # 5. Inner Belly (Facing the string)
    inner_belly = [
        (13, 10, VOID_DEEP), (14, 9, VOID_DARK), (15, 8, VOID_DEEP),
        (16, 8, VOID_DARK),  (17, 8, GOLD_DARK), (18, 8, GOLD_MID),
        (19, 8, VOID_DARK),  (20, 8, VOID_MID),  (21, 8, VOID_DARK),
        (22, 9, VOID_DARK),  (23, 10, GOLD_MID), (24, 10, GOLD_HI),
        (25, 11, GOLD_MID),  (26, 12, GOLD_DARK),(27, 12, GOLD_SUN)
    ]
    for ix, iy, ic in inner_belly:
        put_sym(ix, iy, ic)

    # Inner Outline Border
    inner_outline = [
        (13, 11), (14, 10), (15, 9), (16, 9), (17, 9), (18, 9),
        (19, 9), (20, 9), (21, 9), (22, 10), (23, 11), (24, 11),
        (25, 12), (26, 13), (27, 13), (28, 13)
    ]
    for iox, ioy in inner_outline:
        put_sym(iox, ioy, OUT)

    # 6. Center Riser & Supernova Star Core
    riser_gold = [
        (9, 11, GOLD_HI), (9, 12, GOLD_MID), (8, 11, OUT), (8, 12, OUT),
        (10, 10, GOLD_SUN), (9, 10, OUT), (10, 9, GOLD_MID), (11, 9, GOLD_HI),
        (10, 12, VOID_DARK), (12, 10, VOID_DARK)
    ]
    for rx, ry, rc in riser_gold:
        put_sym(rx, ry, rc)

    put(11, 12, GOLD_DARK); put(12, 11, GOLD_DARK)
    put(12, 12, VOID_DEEP); put(13, 12, OUT); put(12, 13, OUT)

    # Supernova Star Core (Pulsing 8-pointed star gem at 11, 11)
    put(11, 11, SUN_WHITE)
    put(10, 11, NEBULA_PINK);   put(11, 10, NEBULA_PINK)
    put(12, 11, NEBULA_MAGENTA); put(11, 12, NEBULA_MAGENTA)
    put(10, 10, STAR_WHITE)
    put(11, 9, STAR_CYAN);      put(9, 11, STAR_CYAN)
    put(9, 9, OUT)

    # 7. Bow String (Starlight cosmic thread)
    nocks = [(18, 18), (20, 20), (22, 22), (25, 25)]
    nock_x, nock_y = nocks[draw]

    line(grid, tip_x, tip_y, nock_x, nock_y, STAR_AMETHYST)
    line(grid, nock_x, nock_y, tip_y, tip_x, STAR_AMETHYST)
    put(nock_x, nock_y, STAR_WHITE)
    put(tip_x, tip_y, STAR_WHITE)
    put(tip_y, tip_x, STAR_WHITE)

    # 8. Arrows & Cosmic Accumulation during Draw
    if draw == 1:
        line(grid, 9, 9, nock_x, nock_y, NEBULA_MAGENTA)
        put(9, 9, STAR_WHITE); put(8, 8, NEBULA_PINK)
        put(nock_x, nock_y, STAR_WHITE)
    elif draw == 2:
        line(grid, 10, 10, nock_x, nock_y, STAR_AMETHYST)
        put(9, 9, SUN_WHITE)
        put(10, 9, NEBULA_PINK); put(9, 10, NEBULA_PINK)
        put(11, 9, STAR_CYAN);   put(9, 11, STAR_CYAN)
        put(nock_x - 1, nock_y, GOLD_HI); put(nock_x, nock_y - 1, GOLD_HI)
        put(nock_x, nock_y, SUN_WHITE)
    elif draw == 3:
        # FULL DRAW: Blazing Supernova Star Arrow!
        line(grid, 13, 13, nock_x - 1, nock_y - 1, SUN_WHITE)
        for i in range(13, nock_x):
            put(i + 1, i, NEBULA_MAGENTA)
            put(i, i + 1, STAR_AMETHYST)

        # Starburst Arrowhead (Glowing celestial sun)
        arrowhead = [
            (2, 2, SUN_WHITE), (3, 2, STAR_WHITE), (2, 3, STAR_WHITE),
            (3, 3, SUN_WHITE), (4, 3, NEBULA_PINK), (3, 4, NEBULA_PINK),
            (4, 2, STAR_CYAN), (2, 4, STAR_CYAN),   (5, 2, VOID_MID),
            (2, 5, VOID_MID),  (4, 4, SUN_WHITE),   (5, 3, NEBULA_PINK),
            (3, 5, NEBULA_PINK), (5, 4, NEBULA_MAGENTA), (4, 5, NEBULA_MAGENTA),
            # Outer dark outline
            (1, 2, OUT), (2, 1, OUT), (3, 1, OUT), (1, 3, OUT),
            (5, 1, OUT), (1, 5, OUT), (6, 2, OUT), (2, 6, OUT)
        ]
        for ax, ay, *ac in arrowhead:
            put(ax + 8, ay + 8, ac[0] if ac else OUT)

        # Astral Wing Fletchings
        fletch = [
            (nock_x - 3, nock_y - 1, GOLD_HI),     (nock_x - 4, nock_y - 1, NEBULA_PINK),
            (nock_x - 1, nock_y - 3, GOLD_HI),     (nock_x - 1, nock_y - 4, NEBULA_PINK),
            (nock_x - 2, nock_y, GOLD_SUN),        (nock_x, nock_y - 2, GOLD_SUN),
            (nock_x - 3, nock_y + 1, OUT),         (nock_x + 1, nock_y - 3, OUT),
            (nock_x, nock_y, SUN_WHITE),          (nock_x + 1, nock_y + 1, GOLD_DARK)
        ]
        for fx, fy, fc in fletch:
            put(fx, fy, fc)

    return grid


def main():
    bow_types = [
        ('storm_bow', generate_storm_bow),
        ('nova_bow', generate_nova_bow),
    ]
    for name, gen_fn in bow_types:
        for draw in range(4):
            suffix = '' if draw == 0 else f'_pulling_{draw - 1}'
            file_name = f'{name}{suffix}.png'
            pixels = gen_fn(draw)
            out_path = OUTPUT / file_name
            png(out_path, pixels, 32)
            colors = {c for row in pixels for c in row if c[3] > 0}
            print(f'Generated {file_name}: {len(colors)} colors')

if __name__ == '__main__':
    main()
