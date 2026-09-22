"""Generate the 4 reworked wand textures (sonic_boom, blaze_barrage, guardian_beam, vex_legion).
Uses pure Python standard library (struct + zlib). Matches check_png assertions in check_packs.py.
"""
import math
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'art/wands'
OUTPUT.mkdir(parents=True, exist_ok=True)


def read_png(path):
    data = path.read_bytes()
    offset, compressed = 8, bytearray()
    while offset < len(data):
        size = struct.unpack('>I', data[offset:offset+4])[0]
        kind = data[offset+4:offset+8]
        if kind == b'IDAT': compressed.extend(data[offset+8:offset+8+size])
        offset += size + 12
    raw = zlib.decompress(compressed)
    w, h, stride = 128, 128, 128 * 4
    img = []
    prev = bytearray(stride)
    for y in range(h):
        filt = raw[y * (stride + 1)]
        row = bytearray(raw[y * (stride + 1) + 1 : (y + 1) * (stride + 1)])
        for x in range(stride):
            a = row[x - 4] if x >= 4 else 0
            b = prev[x]
            c = prev[x - 4] if x >= 4 else 0
            if filt == 1: p = a
            elif filt == 2: p = b
            elif filt == 3: p = (a + b) // 2
            elif filt == 4:
                pe = a + b - c
                p = min(((abs(pe - a), 0, a), (abs(pe - b), 1, b), (abs(pe - c), 2, c)))[2]
            else: p = 0
            row[x] = (row[x] + p) & 255
        prev = row
        img.append(row)
    return img


def write_png(path, grid):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    raw = b''.join(b'\0' + bytes(c for pixel in row for c in pixel) for row in grid)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 128, 128, 8, 6, 0, 0, 0))
                     + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


def get_base_shaft():
    # Use frost_nova's base diagonal handle up to x=68, y=58
    fn = read_png(ROOT / 'art/wands/frost_nova.png')
    grid = [[(0, 0, 0, 0) for _ in range(128)] for _ in range(128)]
    for y in range(128):
        for x in range(128):
            if fn[y][x * 4 + 3] == 255:
                # Keep shaft pixels that belong to handle/pommel (below and to the left of the head)
                if x + y >= 118:
                    r, g, b = fn[y][x*4], fn[y][x*4+1], fn[y][x*4+2]
                    grid[y][x] = (r, g, b, 255)
    return grid


def generate_sonic_boom():
    # Base shaft with sculk teal recoloring
    base = get_base_shaft()
    grid = [[base[y][x] for x in range(128)] for y in range(128)]

    def set_px(x, y, r, g, b):
        if 3 <= x < 125 and 3 <= y < 125:
            grid[y][x] = (r, g, b, 255)

    # Recolor handle trim with sculk teal
    for y in range(128):
        for x in range(128):
            if grid[y][x][3] == 255:
                r, g, b = grid[y][x][:3]
                # If golden/yellowish band, shift to warden sculk teal
                if r > 160 and g > 130 and b < 100:
                    grid[y][x] = (11, 232, 218, 255)
                elif r > 100 and g > 80 and b < 60:
                    grid[y][x] = (6, 120, 115, 255)

    # Warden Head & Acoustic Horn Crown (center around x=92, y=34)
    cx, cy = 92, 34
    TEAL = (11, 232, 218)
    BRIGHT_TEAL = (180, 255, 250)
    DARK_SCULK = (5, 42, 55)
    NAVY_SCULK = (15, 24, 38)
    WHITE = (255, 255, 255)

    # Collar socket at (x=68..78, y=48..58)
    for dx in range(-6, 7):
        for dy in range(-6, 7):
            if abs(dx) + abs(dy) <= 8:
                set_px(73 + dx, 53 + dy, *DARK_SCULK)
    for dx in range(-4, 5):
        for dy in range(-4, 5):
            if abs(dx) + abs(dy) <= 5:
                set_px(73 + dx, 53 + dy, *TEAL)

    # Warden Horns (two curved acoustic antennas)
    # Left horn: arcs from (74, 44) up-left to (68, 24) then curves to (72, 16)
    left_horn = [
        (76, 42), (75, 41), (74, 40), (73, 38), (72, 36), (71, 34), (70, 31),
        (69, 28), (69, 25), (70, 22), (71, 19), (73, 17), (75, 16), (77, 15)
    ]
    for hx, hy in left_horn:
        for ox in [-1, 0, 1]:
            for oy in [-1, 0, 1]:
                if abs(ox) + abs(oy) <= 1:
                    set_px(hx + ox, hy + oy, *DARK_SCULK)
        set_px(hx, hy, *TEAL)
    set_px(77, 15, *BRIGHT_TEAL); set_px(78, 15, *WHITE)

    # Right horn: arcs from (98, 48) up-right to (112, 36) then curves to (110, 26)
    right_horn = [
        (96, 46), (98, 44), (100, 42), (103, 40), (106, 37), (108, 34), (110, 31),
        (111, 28), (111, 25), (110, 22), (108, 19), (106, 17), (104, 16), (102, 15)
    ]
    for hx, hy in right_horn:
        for ox in [-1, 0, 1]:
            for oy in [-1, 0, 1]:
                if abs(ox) + abs(oy) <= 1:
                    set_px(hx + ox, hy + oy, *DARK_SCULK)
        set_px(hx, hy, *TEAL)
    set_px(102, 15, *BRIGHT_TEAL); set_px(101, 15, *WHITE)

    # Central Warden Heart / Sonic Resonance Core (giant diamond crystal)
    for dy in range(-16, 17):
        for dx in range(-16, 17):
            d = abs(dx) + abs(dy)
            if d <= 14:
                # Radial faceted lighting
                if d > 12:
                    set_px(cx + dx, cy + dy, *DARK_SCULK)
                elif d > 9:
                    set_px(cx + dx, cy + dy, *TEAL)
                elif d > 4:
                    set_px(cx + dx, cy + dy, *(BRIGHT_TEAL if dx < 0 or dy < 0 else TEAL))
                else:
                    set_px(cx + dx, cy + dy, *(WHITE if (dx <= 0 and dy <= 0) else BRIGHT_TEAL))

    # Concentric Sonic Pulse rings around the core
    for r in [18, 22]:
        for a in range(24):
            ang = a * (2 * math.pi / 24)
            px = int(cx + math.cos(ang) * r)
            py = int(cy + math.sin(ang) * r)
            if (a % 3 != 0):
                set_px(px, py, *TEAL)

    return grid


def generate_blaze_barrage():
    base = get_base_shaft()
    grid = [[base[y][x] for x in range(128)] for y in range(128)]

    def set_px(x, y, r, g, b):
        if 3 <= x < 125 and 3 <= y < 125:
            grid[y][x] = (r, g, b, 255)

    # Recolor handle with netherite & fiery blaze gold
    for y in range(128):
        for x in range(128):
            if grid[y][x][3] == 255:
                r, g, b = grid[y][x][:3]
                if r > 140 and g > 110:
                    grid[y][x] = (255, 170, 0, 255)
                elif r > 80 and g > 60:
                    grid[y][x] = (190, 70, 0, 255)

    cx, cy = 92, 34
    GOLD_FLAME = (255, 225, 80)
    BLAZE_ORANGE = (255, 140, 0)
    MAGMA_RED = (180, 30, 0)
    DARK_NETHERITE = (38, 22, 18)
    WHITE = (255, 255, 255)

    # Socket mount
    for dx in range(-6, 7):
        for dy in range(-6, 7):
            if abs(dx) + abs(dy) <= 7:
                set_px(73 + dx, 53 + dy, *DARK_NETHERITE)
    for dx in range(-4, 5):
        for dy in range(-4, 5):
            if abs(dx) + abs(dy) <= 4:
                set_px(73 + dx, 53 + dy, *GOLD_FLAME)

    # 3 Orbiting Blaze Rods
    rods = [
        # Top-left rod
        [(76, 26), (78, 28), (80, 30), (82, 32), (84, 34), (86, 36)],
        # Top-right rod
        [(96, 16), (98, 18), (100, 20), (102, 22), (104, 24), (106, 26)],
        # Bottom-right rod
        [(102, 38), (104, 40), (106, 42), (108, 44), (110, 46), (112, 48)]
    ]
    for rod in rods:
        for rx, ry in rod:
            for ox in [-1, 0, 1]:
                for oy in [-1, 0, 1]:
                    set_px(rx + ox, ry + oy, *DARK_NETHERITE)
            set_px(rx, ry, *GOLD_FLAME)
            set_px(rx+1, ry, *BLAZE_ORANGE)

    # Central Fiery Sunburst Sphere
    for dy in range(-15, 16):
        for dx in range(-15, 16):
            d = math.hypot(dx, dy)
            if d <= 14:
                if d > 12:
                    set_px(cx + dx, cy + dy, *DARK_NETHERITE)
                elif d > 9:
                    set_px(cx + dx, cy + dy, *MAGMA_RED)
                elif d > 5:
                    set_px(cx + dx, cy + dy, *BLAZE_ORANGE)
                elif d > 2:
                    set_px(cx + dx, cy + dy, *GOLD_FLAME)
                else:
                    set_px(cx + dx, cy + dy, *WHITE)

    # Fire bursts / flame tendrils projecting outwards
    spikes = [(0, -18), (0, 18), (-18, 0), (18, 0), (-13, -13), (13, -13), (-13, 13), (13, 13)]
    for sx, sy in spikes:
        for step in range(1, 6):
            t = step / 5.0
            px = int(cx + sx * t)
            py = int(cy + sy * t)
            set_px(px, py, *(GOLD_FLAME if step > 3 else WHITE))

    return grid


def generate_guardian_beam():
    base = get_base_shaft()
    grid = [[base[y][x] for x in range(128)] for y in range(128)]

    def set_px(x, y, r, g, b):
        if 3 <= x < 125 and 3 <= y < 125:
            grid[y][x] = (r, g, b, 255)

    # Recolor handle with ocean prismarine & copper
    for y in range(128):
        for x in range(128):
            if grid[y][x][3] == 255:
                r, g, b = grid[y][x][:3]
                if r > 140 and g > 110:
                    grid[y][x] = (86, 224, 181, 255)
                elif r > 80 and g > 60:
                    grid[y][x] = (25, 100, 90, 255)

    cx, cy = 92, 34
    PRISMARINE = (86, 224, 181)
    LIGHT_FOAM = (210, 255, 245)
    DARK_SEA = (18, 55, 52)
    CORAL_ORANGE = (255, 115, 60)
    WHITE = (255, 255, 255)
    BLACK = (15, 18, 24)

    # Collar socket
    for dx in range(-6, 7):
        for dy in range(-6, 7):
            if abs(dx) + abs(dy) <= 7:
                set_px(73 + dx, 53 + dy, *DARK_SEA)
    for dx in range(-4, 5):
        for dy in range(-4, 5):
            if abs(dx) + abs(dy) <= 4:
                set_px(73 + dx, 53 + dy, *PRISMARINE)

    # 4 Guardian Defensive Spikes (Coral Orange)
    spikes = [
        # Up-left
        [(76, 20), (78, 22), (80, 24), (82, 26)],
        # Up-right
        [(102, 14), (104, 16), (106, 18), (108, 20)],
        # Down-right
        [(112, 38), (110, 36), (108, 34), (106, 32)],
        # Down-left
        [(82, 44), (84, 42), (86, 40), (88, 38)]
    ]
    for sp in spikes:
        for sx, sy in sp:
            for ox in [-1, 0, 1]:
                for oy in [-1, 0, 1]:
                    set_px(sx + ox, sy + oy, *DARK_SEA)
            set_px(sx, sy, *CORAL_ORANGE)

    # Central Guardian Prism Eye (Octagonal Jewel)
    for dy in range(-14, 15):
        for dx in range(-14, 15):
            if max(abs(dx), abs(dy)) + min(abs(dx), abs(dy)) * 0.5 <= 13:
                d = math.hypot(dx, dy)
                if d > 11:
                    set_px(cx + dx, cy + dy, *DARK_SEA)
                elif d > 7:
                    set_px(cx + dx, cy + dy, *PRISMARINE)
                elif d > 4:
                    set_px(cx + dx, cy + dy, *LIGHT_FOAM)
                else:
                    set_px(cx + dx, cy + dy, *WHITE)

    # Cyclopean Eye Pupil & Laser Core
    for dx in range(-2, 3):
        for dy in range(-2, 3):
            set_px(cx + dx, cy + dy, *BLACK)
    set_px(cx, cy, *CORAL_ORANGE)
    set_px(cx - 1, cy - 1, *WHITE)

    # Laser beam flare protruding towards top-right
    for dist in range(12, 22):
        bx = cx + int(dist * 0.7)
        by = cy - int(dist * 0.7)
        set_px(bx, by, *LIGHT_FOAM)
        set_px(bx + 1, by, *PRISMARINE)
        set_px(bx, by - 1, *PRISMARINE)

    return grid


def generate_vex_legion():
    base = get_base_shaft()
    grid = [[base[y][x] for x in range(128)] for y in range(128)]

    def set_px(x, y, r, g, b):
        if 3 <= x < 125 and 3 <= y < 125:
            grid[y][x] = (r, g, b, 255)

    # Recolor handle with illager ebony & totem gold
    for y in range(128):
        for x in range(128):
            if grid[y][x][3] == 255:
                r, g, b = grid[y][x][:3]
                if r > 140 and g > 110:
                    grid[y][x] = (195, 175, 80, 255)
                elif r > 80 and g > 60:
                    grid[y][x] = (70, 85, 105, 255)

    cx, cy = 92, 34
    GHOST_PALE = (180, 216, 231)
    GHOST_WHITE = (240, 250, 255)
    ILLAGER_NAVY = (35, 48, 68)
    TOTEM_GOLD = (235, 195, 70)
    VEX_RED = (220, 45, 45)
    WHITE = (255, 255, 255)

    # Collar socket
    for dx in range(-6, 7):
        for dy in range(-6, 7):
            if abs(dx) + abs(dy) <= 7:
                set_px(73 + dx, 53 + dy, *ILLAGER_NAVY)
    for dx in range(-4, 5):
        for dy in range(-4, 5):
            if abs(dx) + abs(dy) <= 4:
                set_px(73 + dx, 53 + dy, *TOTEM_GOLD)

    # Dual Spread Spectral Wings (left wing and right wing)
    # Left wing
    left_wing = [
        (72, 38), (70, 36), (68, 33), (66, 30), (64, 26), (64, 22), (66, 18), (70, 16),
        (72, 22), (74, 26), (76, 30), (78, 34)
    ]
    for wx, wy in left_wing:
        for ox in [-1, 0, 1]:
            for oy in [-1, 0, 1]:
                set_px(wx + ox, wy + oy, *ILLAGER_NAVY)
        set_px(wx, wy, *GHOST_PALE)
        set_px(wx+1, wy-1, *GHOST_WHITE)

    # Right wing
    right_wing = [
        (106, 42), (110, 40), (114, 37), (116, 33), (118, 28), (118, 23), (116, 18), (112, 15),
        (108, 20), (106, 25), (104, 30), (102, 35)
    ]
    for wx, wy in right_wing:
        for ox in [-1, 0, 1]:
            for oy in [-1, 0, 1]:
                set_px(wx + ox, wy + oy, *ILLAGER_NAVY)
        set_px(wx, wy, *GHOST_PALE)
        set_px(wx+1, wy-1, *GHOST_WHITE)

    # Central Spectral Vex Gem (floating ghost tear)
    for dy in range(-14, 15):
        for dx in range(-14, 15):
            d = math.hypot(dx, dy)
            if d <= 13:
                if d > 11:
                    set_px(cx + dx, cy + dy, *ILLAGER_NAVY)
                elif d > 8:
                    set_px(cx + dx, cy + dy, *GHOST_PALE)
                elif d > 4:
                    set_px(cx + dx, cy + dy, *GHOST_WHITE)
                else:
                    set_px(cx + dx, cy + dy, *WHITE)

    # Angry Vex Eye Slits
    set_px(cx - 3, cy - 1, *VEX_RED); set_px(cx - 2, cy - 1, *VEX_RED)
    set_px(cx + 2, cy - 1, *VEX_RED); set_px(cx + 3, cy - 1, *VEX_RED)
    set_px(cx - 2, cy - 2, *WHITE); set_px(cx + 2, cy - 2, *WHITE)

    # Totem gold crown halo above gem
    for i in range(-5, 6):
        set_px(cx + i, cy - 15, *TOTEM_GOLD)
        if abs(i) in (0, 3, 5):
            set_px(cx + i, cy - 17, *TOTEM_GOLD)
            set_px(cx + i, cy - 18, *WHITE)

    return grid


def main():
    print('Generating 4 reworked wand textures in', OUTPUT)
    wands = [
        ('sonic_boom', generate_sonic_boom()),
        ('blaze_barrage', generate_blaze_barrage()),
        ('guardian_beam', generate_guardian_beam()),
        ('vex_legion', generate_vex_legion())
    ]
    for name, grid in wands:
        out_path = OUTPUT / f'{name}.png'
        write_png(out_path, grid)
        # Verify check_png assertions
        data = out_path.read_bytes()
        w, h, depth, color, _, _, interlace = struct.unpack('>IIBBBBB', data[16:29])
        assert w == h == 128 and depth == 8 and color == 6 and interlace == 0
        raw = zlib.decompress(b''.join(
            data[offset+8:offset+8+struct.unpack('>I', data[offset:offset+4])[0]]
            for offset in [i for i in range(len(data)-4) if data[i+4:i+8] == b'IDAT']
        ))
        stride, previous, alpha = w * 4, bytearray(w * 4), []
        for y in range(h):
            start = y * (stride + 1)
            filtering, row = raw[start], bytearray(raw[start+1:start+1+stride])
            for x in range(stride):
                a = row[x-4] if x >= 4 else 0
                b = previous[x]
                c = previous[x-4] if x >= 4 else 0
                if filtering == 1: predictor = a
                elif filtering == 2: predictor = b
                elif filtering == 3: predictor = (a+b)//2
                elif filtering == 4:
                    p = a+b-c
                    predictor = min(((abs(p-a), 0, a), (abs(p-b), 1, b), (abs(p-c), 2, c)))[2]
                else: predictor = 0
                row[x] = (row[x] + predictor) & 255
            alpha.extend(row[3::4]); previous = row
        assert sum(a == 0 for a in alpha) > w*h*0.55, 'Background transparent'
        assert sum(a == 255 for a in alpha) > w*h*0.025, 'Solid pixels'
        assert set(alpha) == {0, 255}, 'Binary alpha'
        assert all(alpha[y*w+x] == 0 for y in range(h) for x in range(w) if x < 3 or x >= w-3 or y < 3 or y >= h-3), 'Border safe'
        solid_count = sum(a == 255 for a in alpha)
        print(f'  [OK] {out_path.name} (solid: {solid_count} px, passes check_png)')
    print('All 4 reworked wand textures generated and verified successfully!')


if __name__ == '__main__':
    main()
