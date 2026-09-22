"""Generate 5 high-detail 32x32 pixel art textures for Evergarden Equipment.
Pure Python standard library (struct + zlib). Matches the rich palette and shading of the cores.
Solid geometry, perfectly symmetrical curves, gap-free rasterization.
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


# =============================================================================
# 1. VOID_KEY (Evergarden Key)
# =============================================================================
def generate_void_key():
    grid = create_grid()
    def put(x, y, c):
        ix, iy = int(round(x)), int(round(y))
        if 0 <= ix < 32 and 0 <= iy < 32:
            grid[iy][ix] = c

    GOLD_SUN   = (255, 248, 180, 255)
    GOLD_HI    = (250, 225, 120, 255)
    GOLD_MID   = (225, 180, 60, 255)
    GOLD_LOW   = (165, 120, 30, 255)
    GOLD_DARK  = (100, 65, 18, 255)

    OUT        = (22, 18, 30, 255)
    VOID_DARK  = (40, 26, 65, 255)
    VOID_MID   = (95, 52, 155, 255)
    VOID_HI    = (160, 100, 230, 255)
    VOID_LIGHT = (210, 160, 255, 255)

    CYAN_WHITE = (245, 255, 255, 255)
    CYAN_LIGHT = (145, 245, 255, 255)
    CYAN_MID   = (50, 190, 225, 255)
    CYAN_DEEP  = (22, 115, 155, 255)

    bx, by = 21.0, 9.5  # Bow center

    # Outer bow ring
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - bx, y - by)
            if 3.2 <= d <= 7.3:
                angle = math.atan2(y - by, x - bx)
                if d >= 6.3:
                    if y < by or x < bx:
                        c = blend(GOLD_HI, GOLD_SUN, (7.3 - d) / 1.0)
                    else:
                        c = blend(GOLD_DARK, GOLD_LOW, (7.3 - d) / 1.0)
                    put(x, y, c)
                elif d <= 4.0:
                    put(x, y, OUT)
                else:
                    ratio = (d - 4.0) / 2.3
                    base_c = blend(VOID_MID, VOID_HI, 1.0 - ratio)
                    if math.cos(angle - 0.75) > 0.3:
                        base_c = blend(base_c, CYAN_MID, 0.45)
                    put(x, y, base_c)

    # 3 Crown Finials on Bow
    finials = [
        (25, 3, GOLD_HI), (26, 2, GOLD_SUN), (26, 3, GOLD_MID), (27, 2, OUT),
        (27, 5, GOLD_HI), (28, 5, GOLD_MID), (28, 6, GOLD_DARK), (29, 5, OUT),
        (23, 2, GOLD_HI), (23, 1, CYAN_LIGHT), (24, 2, GOLD_MID), (24, 1, OUT),
    ]
    for fx, fy, fc in finials:
        put(fx, fy, fc)

    # Floating 8-pointed star in bow core
    for i in range(-2, 3):
        put(bx + i, by, CYAN_LIGHT if abs(i) == 1 else CYAN_MID if abs(i) == 2 else CYAN_WHITE)
        put(bx, by + i, CYAN_LIGHT if abs(i) == 1 else CYAN_MID if abs(i) == 2 else CYAN_WHITE)
    put(bx - 1, by - 1, CYAN_DEEP); put(bx + 1, by - 1, CYAN_DEEP)
    put(bx - 1, by + 1, CYAN_DEEP); put(bx + 1, by + 1, CYAN_DEEP)
    put(bx, by, CYAN_WHITE)

    # Collar junction
    collar = [
        (16, 14, GOLD_HI), (17, 14, GOLD_SUN), (18, 14, GOLD_MID),
        (15, 15, GOLD_HI), (16, 15, VOID_LIGHT), (17, 15, GOLD_MID), (18, 15, GOLD_LOW),
        (14, 16, GOLD_MID), (15, 16, VOID_HI), (16, 16, GOLD_LOW), (17, 16, GOLD_DARK),
        (13, 17, GOLD_HI), (14, 17, CYAN_WHITE), (15, 17, GOLD_DARK), (16, 17, OUT),
    ]
    for cx, cy, cc in collar:
        put(cx, cy, cc)

    # Shaft (Diagonal from 14,17 down to 7,24)
    for i in range(1, 9):
        sx = 14 - i
        sy = 17 + i
        t = i / 8.0
        put(sx - 1, sy, blend(GOLD_SUN, GOLD_HI, t))
        put(sx, sy - 1, blend(GOLD_SUN, GOLD_MID, t))
        core_c = blend(CYAN_WHITE, CYAN_LIGHT, t) if i % 2 == 1 else blend(VOID_LIGHT, VOID_HI, t)
        put(sx, sy, core_c)
        put(sx + 1, sy, blend(GOLD_MID, GOLD_DARK, t))
        put(sx, sy + 1, blend(GOLD_DARK, OUT, t))

    # Shaft mid-ring collar at (10, 21)
    put(11, 20, GOLD_HI); put(12, 20, GOLD_MID)
    put(9, 21, GOLD_SUN); put(10, 21, CYAN_MID); put(11, 21, GOLD_LOW)

    # Wards / Teeth (Bottom-Left from 9,22 down to 4,27)
    teeth_data = [
        # Upper tooth
        (8, 22, GOLD_HI), (7, 22, CYAN_LIGHT), (6, 22, CYAN_MID), (5, 22, OUT),
        (8, 23, GOLD_MID), (7, 23, CYAN_WHITE), (6, 23, CYAN_DEEP), (5, 23, OUT),
        (8, 24, GOLD_LOW), (7, 24, VOID_MID), (6, 24, OUT),
        # Notch gap
        (7, 25, GOLD_MID), (6, 25, OUT),
        # Lower main stepped tooth
        (8, 26, GOLD_HI), (7, 26, GOLD_MID), (6, 26, CYAN_LIGHT), (5, 26, CYAN_WHITE), (4, 26, OUT),
        (8, 27, GOLD_DARK), (7, 27, GOLD_LOW), (6, 27, CYAN_MID), (5, 27, CYAN_DEEP), (4, 27, OUT),
        (5, 28, OUT), (6, 28, OUT), (7, 28, OUT),
    ]
    for tx, ty, tc in teeth_data:
        put(tx, ty, tc)

    return grid


# =============================================================================
# 2. RIFT_PICKAXE (Rift Excavator)
# =============================================================================
def generate_rift_pickaxe():
    grid = create_grid()
    def put(x, y, c):
        ix, iy = int(round(x)), int(round(y))
        if 0 <= ix < 32 and 0 <= iy < 32:
            grid[iy][ix] = c

    OUT          = (20, 16, 28, 255)
    NETHERITE_D  = (38, 34, 46, 255)
    NETHERITE_M  = (65, 58, 76, 255)
    NETHERITE_H  = (95, 86, 112, 255)

    GOLD_SUN     = (255, 245, 175, 255)
    GOLD_HI      = (240, 205, 80, 255)
    GOLD_MID     = (215, 170, 50, 255)
    GOLD_DARK    = (135, 95, 28, 255)

    VOID_DEEP    = (45, 20, 85, 255)
    VOID_MID     = (115, 55, 185, 255)
    VOID_HI      = (175, 105, 245, 255)

    CYAN_WHITE   = (245, 255, 255, 255)
    CYAN_LIGHT   = (145, 245, 255, 255)
    CYAN_MID     = (50, 195, 230, 255)
    CYAN_DEEP    = (25, 120, 165, 255)

    # 1. Solid Diagonal Handle from (4, 27) to (21, 10)
    for t in range(25):
        prog = t / 24.0
        cx = int(round(4 + t * (17.0 / 24.0)))
        cy = int(round(27 - t * (17.0 / 24.0)))
        if t <= 2:
            # Gold Pommel
            put(cx, cy, GOLD_SUN if t == 1 else GOLD_HI)
            put(cx - 1, cy, OUT)
            put(cx, cy + 1, GOLD_DARK)
            put(cx + 1, cy + 1, OUT)
        else:
            is_strap = (t % 4 in (1, 2)) and (t < 21)
            c_hi  = VOID_HI if is_strap else NETHERITE_H
            c_mid = VOID_MID if is_strap else NETHERITE_M
            c_low = VOID_DEEP if is_strap else NETHERITE_D

            put(cx - 1, cy, c_hi)
            put(cx, cy - 1, c_hi)
            put(cx, cy, c_mid)
            put(cx + 1, cy, c_low)
            put(cx, cy + 1, c_low)
            put(cx + 1, cy + 1, OUT)

    # Ferrule bracket under head
    for fx, fy, fc in [(19, 12, GOLD_HI), (20, 11, GOLD_SUN), (21, 12, GOLD_MID), (20, 13, OUT), (21, 13, GOLD_DARK)]:
        put(fx, fy, fc)

    # 2. Mathematically Symmetrical Pickaxe Head
    sx, sy = 21.0, 10.0
    for sign in (-1, +1):
        horn_steps = 40
        for step in range(horn_steps + 1):
            s = (step / float(horn_steps)) * 14.5
            curve = 2.4 * math.sin((s / 14.5) * math.pi)
            cx = sx + sign * s * 0.7071 + curve * 0.7071
            cy = sy + sign * s * 0.7071 - curve * 0.7071

            prog = s / 14.5
            w = int(round(1.8 * (1.0 - (s / 15.0) ** 1.2)))
            ix, iy = int(round(cx)), int(round(cy))

            # Core color
            core_c = blend(CYAN_WHITE, CYAN_LIGHT, prog) if prog > 0.6 else blend(VOID_HI, CYAN_MID, prog)
            put(ix, iy, core_c)

            # Bevels and outline
            for o in range(1, w + 1):
                # Back spine (outer curve): (o, -o)
                spine_c = blend(NETHERITE_M, VOID_DEEP, prog)
                put(ix + o, iy - o, spine_c)
                put(ix + o + 1, iy - o - 1, OUT)

                # Front edge (inner curve): (-o, o)
                edge_c = blend(CYAN_WHITE, CYAN_LIGHT, prog) if prog > 0.65 else blend(CYAN_MID, CYAN_DEEP, prog)
                put(ix - o, iy + o, edge_c)
                put(ix - o - 1, iy + o + 1, OUT)

            if step == horn_steps:
                put(ix, iy, CYAN_WHITE)
                put(ix + 1, iy - 1, CYAN_LIGHT)
                put(ix - 1, iy + 1, OUT)

    # Center socket star jewel
    put(sx, sy, CYAN_WHITE)
    put(sx - 1, sy, GOLD_SUN); put(sx + 1, sy, GOLD_MID)
    put(sx, sy - 1, GOLD_HI);  put(sx, sy + 1, GOLD_DARK)
    put(sx - 1, sy - 1, OUT);  put(sx + 1, sy + 1, OUT)

    return grid


# =============================================================================
# 3. SMELTER_PICKAXE (Smelter's Pickaxe)
# =============================================================================
def generate_smelter_pickaxe():
    grid = create_grid()
    def put(x, y, c):
        ix, iy = int(round(x)), int(round(y))
        if 0 <= ix < 32 and 0 <= iy < 32:
            grid[iy][ix] = c

    OUT          = (24, 20, 22, 255)
    BASALT_D     = (35, 30, 38, 255)
    BASALT_M     = (62, 55, 66, 255)
    BASALT_H     = (92, 84, 98, 255)

    BRASS_SUN    = (255, 235, 140, 255)
    BRASS_HI     = (245, 195, 60, 255)
    BRASS_MID    = (210, 150, 40, 255)
    BRASS_DARK   = (135, 85, 22, 255)

    HEAT_WHITE   = (255, 255, 230, 255)
    FIRE_YELLOW  = (255, 225, 60, 255)
    FIRE_ORANGE  = (255, 130, 25, 255)
    FIRE_CRIMSON = (210, 40, 18, 255)
    FIRE_DEEP    = (125, 22, 15, 255)

    # 1. Solid Handle
    for t in range(25):
        cx = int(round(4 + t * (17.0 / 24.0)))
        cy = int(round(27 - t * (17.0 / 24.0)))
        if t <= 2:
            put(cx, cy, BRASS_SUN if t == 1 else BRASS_HI)
            put(cx - 1, cy, OUT)
            put(cx, cy + 1, BRASS_DARK)
            put(cx + 1, cy + 1, OUT)
        else:
            is_vein = (t % 3 == 0) and (t < 21)
            c_hi  = FIRE_YELLOW if is_vein else BASALT_H
            c_mid = FIRE_ORANGE if is_vein else BASALT_M
            c_low = FIRE_CRIMSON if is_vein else BASALT_D

            put(cx - 1, cy, c_hi)
            put(cx, cy - 1, c_hi)
            put(cx, cy, c_mid)
            put(cx + 1, cy, c_low)
            put(cx, cy + 1, c_low)
            put(cx + 1, cy + 1, OUT)

    for fx, fy, fc in [(19, 12, BRASS_HI), (20, 11, BRASS_SUN), (21, 12, BRASS_MID), (20, 13, OUT), (21, 13, BRASS_DARK)]:
        put(fx, fy, fc)

    # 2. Symmetrical Molten Pickaxe Head
    sx, sy = 21.0, 10.0
    for sign in (-1, +1):
        horn_steps = 40
        for step in range(horn_steps + 1):
            s = (step / float(horn_steps)) * 14.5
            curve = 2.4 * math.sin((s / 14.5) * math.pi)
            cx = sx + sign * s * 0.7071 + curve * 0.7071
            cy = sy + sign * s * 0.7071 - curve * 0.7071

            prog = s / 14.5
            w = int(round(1.8 * (1.0 - (s / 15.0) ** 1.2)))
            ix, iy = int(round(cx)), int(round(cy))

            core_c = blend(HEAT_WHITE, FIRE_YELLOW, prog) if prog > 0.6 else blend(FIRE_ORANGE, FIRE_CRIMSON, prog)
            put(ix, iy, core_c)

            for o in range(1, w + 1):
                spine_c = blend(BASALT_M, FIRE_DEEP, prog)
                put(ix + o, iy - o, spine_c)
                put(ix + o + 1, iy - o - 1, OUT)

                edge_c = blend(HEAT_WHITE, FIRE_YELLOW, prog) if prog > 0.65 else blend(FIRE_ORANGE, FIRE_YELLOW, prog)
                put(ix - o, iy + o, edge_c)
                put(ix - o - 1, iy + o + 1, OUT)

            if step == horn_steps:
                put(ix, iy, HEAT_WHITE)
                put(ix + 1, iy - 1, FIRE_YELLOW)
                put(ix - 1, iy + 1, OUT)

    put(sx, sy, HEAT_WHITE)
    put(sx - 1, sy, FIRE_YELLOW); put(sx + 1, sy, FIRE_ORANGE)
    put(sx, sy - 1, BRASS_SUN);   put(sx, sy + 1, BRASS_DARK)
    put(sx - 1, sy - 1, OUT);     put(sx + 1, sy + 1, OUT)

    return grid


# =============================================================================
# 4. RIFT_BLADE (Rift Greatsword)
# =============================================================================
def generate_rift_blade():
    grid = create_grid()
    def put(x, y, c):
        ix, iy = int(round(x)), int(round(y))
        if 0 <= ix < 32 and 0 <= iy < 32:
            grid[iy][ix] = c

    OUT        = (20, 16, 28, 255)
    STEEL_SUN  = (255, 255, 255, 255)
    STEEL_HI   = (235, 244, 255, 255)
    STEEL_MID  = (160, 175, 205, 255)
    STEEL_DARK = (70, 78, 98, 255)

    GOLD_SUN   = (255, 245, 175, 255)
    GOLD_HI    = (240, 205, 80, 255)
    GOLD_MID   = (215, 170, 50, 255)
    GOLD_DARK  = (135, 95, 28, 255)

    VOID_DEEP  = (45, 18, 80, 255)
    PURPLE_MID = (145, 70, 220, 255)
    PURPLE_HI  = (205, 130, 255, 255)

    CYAN_WHITE = (245, 255, 255, 255)
    CYAN_LIGHT = (145, 245, 255, 255)
    CYAN_MID   = (50, 190, 225, 255)

    # 1. Pommel at (4, 27)
    put(4, 27, PURPLE_HI)
    put(3, 27, GOLD_HI);  put(5, 27, GOLD_DARK)
    put(4, 26, GOLD_SUN); put(4, 28, GOLD_DARK)
    put(3, 26, OUT);      put(5, 28, OUT); put(3, 28, OUT); put(5, 26, OUT)

    # 2. Solid Grip from (5, 26) to (9, 22)
    for i in range(1, 6):
        gx = 4 + i
        gy = 27 - i
        put(gx - 1, gy, GOLD_MID if i % 2 == 1 else VOID_DEEP)
        put(gx, gy, PURPLE_MID if i % 2 == 0 else VOID_DEEP)
        put(gx, gy + 1, OUT)

    # 3. Winged Crossguard at (9..13, 18..22)
    guard = [
        # Upper wing
        (8, 20, GOLD_SUN), (7, 19, GOLD_HI), (6, 18, CYAN_LIGHT), (5, 17, GOLD_SUN), (4, 17, OUT), (6, 17, OUT), (7, 18, OUT),
        (8, 21, GOLD_MID), (9, 21, CYAN_WHITE), (9, 20, GOLD_MID), (7, 20, OUT),
        # Center core gem
        (10, 21, GOLD_DARK), (10, 20, PURPLE_HI), (11, 20, GOLD_MID), (11, 19, CYAN_LIGHT),
        # Lower wing
        (11, 21, GOLD_MID), (12, 22, GOLD_MID), (13, 23, CYAN_MID), (14, 24, GOLD_DARK), (15, 24, OUT), (13, 24, OUT),
        (12, 21, GOLD_DARK), (13, 22, OUT),
    ]
    for qx, qy, qc in guard:
        put(qx, qy, qc)

    # 4. Solid, Seamless Blade from (11, 19) to (28, 2)
    for t in range(17):
        cx = 11 + t
        cy = 19 - t
        prog = t / 16.0
        w = 2 if t < 8 else 1 if t < 14 else 0

        # Center fuller (cosmic rift glow)
        fuller_c = blend(PURPLE_HI, CYAN_LIGHT, prog) if prog < 0.65 else blend(CYAN_LIGHT, CYAN_WHITE, (prog - 0.65) / 0.35)
        put(cx, cy, fuller_c)

        if w >= 1:
            # Top-left cutting bevel
            put(cx - 1, cy, STEEL_HI)
            put(cx, cy - 1, STEEL_SUN)
            put(cx - 2, cy - 1, OUT)
            put(cx - 1, cy - 2, OUT)

            # Bottom-right cutting bevel
            put(cx + 1, cy, STEEL_MID)
            put(cx, cy + 1, STEEL_DARK)
            put(cx + 2, cy + 1, OUT)
            put(cx + 1, cy + 2, OUT)

        if w >= 2:
            put(cx - 1, cy - 1, STEEL_HI)
            put(cx - 2, cy - 2, OUT)
            put(cx + 1, cy + 1, STEEL_DARK)
            put(cx + 2, cy + 2, OUT)

    # Razor tip
    put(28, 2, CYAN_WHITE)
    put(27, 2, STEEL_HI); put(28, 3, STEEL_MID)
    put(29, 2, OUT); put(28, 1, OUT); put(29, 1, OUT)

    return grid


# =============================================================================
# 5. ETERNAL_AEGIS (Grand Valkyrie Kite Shield)
# =============================================================================
def generate_eternal_aegis():
    grid = create_grid()
    def put(x, y, c):
        ix, iy = int(round(x)), int(round(y))
        if 0 <= ix < 32 and 0 <= iy < 32:
            grid[iy][ix] = c

    OUT        = (22, 18, 28, 255)
    GOLD_SUN   = (255, 248, 180, 255)
    GOLD_HI    = (245, 210, 85, 255)
    GOLD_MID   = (220, 175, 55, 255)
    GOLD_LOW   = (165, 120, 35, 255)
    GOLD_DARK  = (105, 70, 22, 255)

    NAVY_DARK  = (18, 16, 35, 255)
    NAVY_MID   = (32, 28, 60, 255)
    NAVY_LIGHT = (52, 45, 95, 255)

    CYAN_WHITE = (245, 255, 255, 255)
    CYAN_LIGHT = (145, 245, 255, 255)
    CYAN_MID   = (50, 190, 225, 255)
    CYAN_DEEP  = (22, 115, 160, 255)

    PURPLE_HI  = (210, 140, 255, 255)
    PURPLE_MID = (150, 80, 220, 255)

    cx = 15.5

    half_widths = {
        3: 2.5, 4: 5.5, 5: 8.5,
        6: 10.5, 7: 10.5, 8: 10.5, 9: 10.5, 10: 10.5,
        11: 10.5, 12: 10.5, 13: 10.5, 14: 10.5, 15: 10.5,
        16: 9.5, 17: 9.5, 18: 8.5, 19: 8.5, 20: 7.5,
        21: 7.5, 22: 6.5, 23: 5.5, 24: 4.5, 25: 3.5,
        26: 2.5, 27: 1.5, 28: 0.5
    }

    for y, hw in half_widths.items():
        x0 = int(round(cx - hw))
        x1 = int(round(cx + hw))
        for x in range(x0, x1 + 1):
            is_outer = (x == x0 or x == x1 or y == 3 or y == 28)
            is_inner = (x == x0 + 1 or x == x1 - 1 or y == 4 or y == 27)

            if is_outer:
                if x < cx or y < 10:
                    c = GOLD_SUN if (x == x0 and y < 15) else GOLD_HI
                else:
                    c = GOLD_DARK if x == x1 else GOLD_LOW
                put(x, y, c)
            elif is_inner:
                if x < cx:
                    put(x, y, GOLD_MID if y < 18 else GOLD_LOW)
                else:
                    put(x, y, GOLD_DARK if y > 18 else GOLD_LOW)
            else:
                d = math.hypot(x - cx, y - 14.5)
                ratio = min(1.0, d / 9.5)
                field_c = blend(NAVY_LIGHT, NAVY_DARK, ratio)
                if x in (15, 16):
                    field_c = blend(field_c, CYAN_DEEP, 0.35)
                put(x, y, field_c)

    # Rivets
    for rx, ry in [(6, 7), (25, 7), (5, 14), (26, 14), (8, 21), (23, 21)]:
        put(rx, ry, GOLD_SUN)
        put(rx + (1 if rx < cx else -1), ry, GOLD_DARK)

    # Central Aegis Star & Valkyrie Wings
    star_px = [
        (15, 9, CYAN_MID), (16, 9, CYAN_MID),
        (15, 10, CYAN_LIGHT), (16, 10, CYAN_LIGHT),
        (15, 11, CYAN_LIGHT), (16, 11, CYAN_LIGHT),
        (15, 18, CYAN_LIGHT), (16, 18, CYAN_LIGHT),
        (15, 19, CYAN_MID), (16, 19, CYAN_MID),
        (15, 20, CYAN_DEEP), (16, 20, CYAN_DEEP),
        (10, 14, CYAN_DEEP), (10, 15, CYAN_DEEP),
        (11, 14, CYAN_MID), (11, 15, CYAN_MID),
        (12, 14, CYAN_LIGHT), (12, 15, CYAN_LIGHT),
        (19, 14, CYAN_LIGHT), (19, 15, CYAN_LIGHT),
        (20, 14, CYAN_MID), (20, 15, CYAN_MID),
        (21, 14, CYAN_DEEP), (21, 15, CYAN_DEEP),
        (15, 14, CYAN_WHITE), (16, 14, CYAN_WHITE),
        (15, 15, CYAN_WHITE), (16, 15, CYAN_WHITE),
        (14, 14, CYAN_LIGHT), (17, 14, CYAN_LIGHT),
        (14, 15, CYAN_LIGHT), (17, 15, CYAN_LIGHT),
        (15, 13, CYAN_LIGHT), (16, 13, CYAN_LIGHT),
        (15, 16, CYAN_LIGHT), (16, 16, CYAN_LIGHT),
        (13, 12, PURPLE_HI), (18, 12, PURPLE_HI),
        (13, 17, PURPLE_MID), (18, 17, PURPLE_MID),
        (12, 11, PURPLE_MID), (19, 11, PURPLE_MID),
        (11, 12, GOLD_SUN), (10, 11, GOLD_HI), (9, 10, GOLD_MID),
        (20, 12, GOLD_SUN), (21, 11, GOLD_HI), (22, 10, GOLD_MID),
        (12, 16, GOLD_MID), (11, 17, GOLD_DARK),
        (19, 16, GOLD_MID), (20, 17, GOLD_DARK),
    ]
    for spx, spy, spc in star_px:
        put(spx, spy, spc)

    return grid


def main():
    generators = {
        'void_key': generate_void_key,
        'rift_pickaxe': generate_rift_pickaxe,
        'smelter_pickaxe': generate_smelter_pickaxe,
        'rift_blade': generate_rift_blade,
        'eternal_aegis': generate_eternal_aegis,
    }
    for name, gen_fn in generators.items():
        pixels = gen_fn()
        out_path = OUTPUT / f'{name}.png'
        png(out_path, pixels, 32)
        colors = {c for row in pixels for c in row if c[3] > 0}
        print(f'Generated {name}.png: {len(colors)} unique colors')
    try:
        import generate_bows
        generate_bows.main()
    except Exception as e:
        print(f'Note: generate_bows error: {e}')

if __name__ == '__main__':
    main()
