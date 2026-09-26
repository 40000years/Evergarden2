"""Generate 15 high-detail 32x32 pixel art textures for Advance Magic Cores.
Pure Python standard library (struct + zlib). No external dependencies.
"""
import math
import struct
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'art/cores'
OUTPUT.mkdir(parents=True, exist_ok=True)
EVERGARDEN_CORES = ROOT.parent / 'evergarden/art/cores'
EVERGARDEN_CORES.mkdir(parents=True, exist_ok=True)

SPELLS = [
    ('lightning_strike', 'Core of Lightning', (255, 230, 80), (255, 255, 190), (190, 150, 20), (255, 245, 120, 255)),
    ('frost_nova',        'Core of Frost',     (120, 225, 255), (220, 250, 255), (40, 130, 200), (141, 234, 255, 255)),
    ('shadow_step',       'Core of Shadows',   (150, 95, 230),  (230, 180, 255), (60, 25, 120),  (142, 105, 212, 255)),
    ('natures_bloom',     'Core of Nature',    (110, 235, 95),  (210, 255, 180), (35, 125, 30),  (141, 239, 129, 255)),
    ('earth_wall',        'Core of Earth',     (185, 145, 95),  (240, 215, 175), (100, 70, 40),  (173, 152, 119, 255)),
    ('dragons_breath',    'Core of Dragon',    (230, 95, 255),  (255, 200, 255), (120, 20, 150), (224, 127, 255, 255)),
    ('void_pull',         'Core of the Void',  (90, 75, 215),   (190, 175, 255), (35, 25, 110),  (102, 88, 201, 255)),
    ('sonic_boom',        'Core of the Warden',(11, 232, 218),  (180, 255, 250), (5, 45, 60),    (11, 232, 218, 255)),
    ('blaze_barrage',     'Core of the Blaze', (255, 165, 0),   (255, 240, 130), (160, 40, 0),   (255, 165, 0, 255)),
    ('wither_ray',        'Core of Wither',    (115, 105, 130), (230, 225, 240), (45, 38, 55),   (130, 122, 145, 255)),
    ('shulker_levitation','Core of Levitation',(225, 150, 240), (255, 220, 255), (130, 60, 150), (223, 164, 236, 255)),
    ('meteor_strike',     'Core of Meteor',    (255, 115, 40),  (255, 220, 120), (160, 40, 10),  (255, 133, 70, 255)),
    ('iron_armor',        'Core of Iron',      (200, 215, 228), (250, 252, 255), (110, 125, 140), (204, 216, 224, 255)),
    ('vex_legion',        'Core of Evocation', (180, 216, 231), (235, 245, 255), (55, 80, 110),  (180, 216, 231, 255)),
    ('guardian_beam',     'Core of the Guardian',(86, 224, 181),(210, 255, 245), (20, 85, 75),   (86, 224, 181, 255)),
]


def png(path, pixels, size=32):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    raw = b''.join(b'\0' + bytes(c for pixel in row for c in pixel) for row in pixels)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0))
                     + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


def blend(c1, c2, alpha):
    """Linear blend between two RGBA colors."""
    return tuple(int(c1[i] * (1 - alpha) + c2[i] * alpha) for i in range(4))


def scale_4x(grid32):
    grid128 = [[(0, 0, 0, 0) for _ in range(128)] for _ in range(128)]
    for y in range(32):
        for x in range(32):
            c = grid32[y][x]
            if c[3] > 0:
                solid = (c[0], c[1], c[2], 255)
                for dy in range(4):
                    for dx in range(4):
                        grid128[y * 4 + dy][x * 4 + dx] = solid
    return grid128


def generate_core(spell_id, title, primary, highlight, dark_shade, glow):
    grid = [[(0, 0, 0, 0) for _ in range(32)] for _ in range(32)]

    def set_px(x, y, color):
        if 2 <= x < 30 and 2 <= y < 30:
            grid[y][x] = (color[0], color[1], color[2], 255)

    cx, cy = 15.5, 15.5

    # 1. Ornate Casing & 4 Cardinal Pinnacles (Gold + Obsidian filigree)
    GOLD = (235, 195, 85, 255)
    GOLD_LIGHT = (255, 235, 150, 255)
    GOLD_DARK = (150, 110, 35, 255)
    OBSIDIAN = (24, 20, 36, 255)
    OBSIDIAN_EDGE = (48, 40, 68, 255)

    # 4 Cardinal Relic Prongs
    prongs = [
        # Top prong (y=2..5, x=15..16)
        [(15, 2), (16, 2), (14, 3), (17, 3), (14, 4), (17, 4), (13, 5), (18, 5)],
        # Bottom prong (y=26..29, x=15..16)
        [(15, 29), (16, 29), (14, 28), (17, 28), (14, 27), (17, 27), (13, 26), (18, 26)],
        # Left prong (x=2..5, y=15..16)
        [(2, 15), (2, 16), (3, 14), (3, 17), (4, 14), (4, 17), (5, 13), (5, 18)],
        # Right prong (x=26..29, y=15..16)
        [(29, 15), (29, 16), (28, 14), (28, 17), (27, 14), (27, 17), (26, 13), (26, 18)],
    ]
    for p_group in prongs:
        for px, py in p_group:
            set_px(px, py, OBSIDIAN)
    # Highlight tips of prongs
    set_px(15, 2, GOLD_LIGHT); set_px(16, 2, GOLD)
    set_px(15, 29, GOLD_DARK); set_px(16, 29, GOLD)
    set_px(2, 15, GOLD_LIGHT); set_px(2, 16, GOLD)
    set_px(29, 15, GOLD_DARK); set_px(29, 16, GOLD)

    # Diagonal corner brace jewels
    for dx, dy in [(-8, -8), (8, -8), (-8, 8), (8, 8)]:
        gx, gy = int(cx + dx), int(cy + dy)
        set_px(gx, gy, GOLD_LIGHT if dy < 0 else GOLD_DARK)
        set_px(gx + (1 if dx < 0 else -1), gy, OBSIDIAN)
        set_px(gx, gy + (1 if dy < 0 else -1), OBSIDIAN)

    # 3. Outer Relic Ring (distance 9.2 to 11.0)
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - cx, y - cy)
            if 9.0 <= d <= 11.2:
                if 10.4 <= d <= 11.2:
                    set_px(x, y, OBSIDIAN)
                elif 9.5 <= d < 10.4:
                    set_px(x, y, GOLD_LIGHT if (y < 14 or x < 14) else GOLD_DARK)
                else:
                    set_px(x, y, OBSIDIAN_EDGE)

    # 4. Glowing Core Body (distance <= 8.8)
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - cx, y - cy)
            if d < 9.0:
                # Radial illumination gradient
                ratio = d / 9.0
                if ratio > 0.85:
                    col = (dark_shade[0], dark_shade[1], dark_shade[2], 255)
                elif ratio > 0.55:
                    t = (ratio - 0.55) / 0.30
                    col = (int(primary[0]*(1-t) + dark_shade[0]*t),
                           int(primary[1]*(1-t) + dark_shade[1]*t),
                           int(primary[2]*(1-t) + dark_shade[2]*t), 255)
                elif ratio > 0.25:
                    t = (ratio - 0.25) / 0.30
                    col = (int(highlight[0]*(1-t) + primary[0]*t),
                           int(highlight[1]*(1-t) + primary[1]*t),
                           int(highlight[2]*(1-t) + primary[2]*t), 255)
                else:
                    col = (highlight[0], highlight[1], highlight[2], 255)
                set_px(x, y, col)

    # 5. Hand-crafted elemental rune at center of the core
    WHITE = (255, 255, 255, 255)
    BLACK = (20, 16, 26, 255)
    ACCENT = highlight + (255,)

    if spell_id == 'lightning_strike':
        # Sharp jagged lightning bolt
        bolt = [(17, 10), (16, 11), (15, 12), (16, 13), (17, 13), (15, 14), (16, 14),
                (14, 15), (15, 15), (13, 16), (14, 16), (14, 17), (15, 17), (16, 17),
                (13, 18), (14, 18), (12, 19), (13, 19), (14, 19), (12, 20), (13, 20), (13, 21)]
        for bx, by in bolt:
            set_px(bx, by, WHITE)
            set_px(bx+1, by, ACCENT)

    elif spell_id == 'frost_nova':
        # 6-pointed crystalline snowflake
        for i in range(-5, 6):
            set_px(int(cx), int(cy) + i, WHITE if abs(i) <= 2 else ACCENT)
            set_px(int(cx) + i, int(cy), WHITE if abs(i) <= 2 else ACCENT)
        for i in [-3, -2, 2, 3]:
            set_px(int(cx)+i, int(cy)+i, ACCENT)
            set_px(int(cx)-i, int(cy)+i, ACCENT)
        set_px(int(cx), int(cy), WHITE)

    elif spell_id == 'shadow_step':
        # Shadow cat-eye / void portal slit
        for y in range(11, 21):
            set_px(15, y, BLACK); set_px(16, y, BLACK)
        for y in range(13, 19):
            set_px(14, y, ACCENT); set_px(17, y, ACCENT)
        set_px(15, 15, (255, 190, 255, 255))
        set_px(16, 15, (255, 190, 255, 255))

    elif spell_id == 'natures_bloom':
        # 4 blooming verdant flower petals
        for dx, dy in [(0, -3), (0, 3), (-3, 0), (3, 0)]:
            set_px(int(cx)+dx, int(cy)+dy, ACCENT)
            set_px(int(cx)+dx//2, int(cy)+dy//2, (70, 200, 60, 255))
        for dx in [-1, 0, 1]:
            for dy in [-1, 0, 1]:
                set_px(int(cx)+dx, int(cy)+dy, (255, 240, 110, 255))
        set_px(int(cx), int(cy), WHITE)

    elif spell_id == 'earth_wall':
        # Heavy fortress bedrock plate
        for y in range(12, 20):
            for x in range(12, 20):
                if (x + y) % 2 == 0:
                    set_px(x, y, (120, 90, 60, 255))
                else:
                    set_px(x, y, (210, 180, 130, 255))
        for x in range(13, 19):
            set_px(x, 11, ACCENT); set_px(x, 20, (80, 50, 25, 255))

    elif spell_id == 'dragons_breath':
        # Dragon eye & swirling ender breath
        for y in range(11, 21):
            set_px(15, y, (40, 0, 60, 255))
            set_px(16, y, (40, 0, 60, 255))
        for x in [13, 14, 17, 18]:
            set_px(x, 15, ACCENT); set_px(x, 16, ACCENT)
        set_px(15, 15, WHITE); set_px(16, 16, WHITE)

    elif spell_id == 'void_pull':
        # Cosmic gravitational vortex spiral
        spiral = [(15, 15), (16, 15), (16, 14), (15, 14), (14, 14), (14, 15), (14, 16),
                  (15, 16), (16, 16), (17, 16), (17, 15), (17, 14), (17, 13), (16, 13),
                  (15, 13), (14, 13), (13, 13), (13, 14), (13, 15), (13, 16), (13, 17)]
        for sx, sy in spiral:
            set_px(sx, sy, WHITE if (sx+sy)%2==0 else ACCENT)

    elif spell_id == 'sonic_boom':
        # Warden sonic shockwave rings and acoustic core
        TEAL = (11, 232, 218, 255)
        DARK_TEAL = (5, 45, 60, 255)
        for dx in [-3, -2, -1, 0, 1, 2, 3]:
            for dy in [-3, -2, -1, 0, 1, 2, 3]:
                d = abs(dx) + abs(dy)
                if d == 3: set_px(int(cx)+dx, int(cy)+dy, ACCENT)
                elif d == 2: set_px(int(cx)+dx, int(cy)+dy, TEAL)
                elif d <= 1: set_px(int(cx)+dx, int(cy)+dy, WHITE)
        for y in [12, 14, 17, 19]:
            set_px(11, y, DARK_TEAL); set_px(20, y, DARK_TEAL)

    elif spell_id == 'blaze_barrage':
        # Blaze rod triad and spinning fire core
        ORANGE = (255, 120, 0, 255)
        GOLD_FIRE = (255, 220, 50, 255)
        for dx in [-1, 0, 1]:
            for dy in [-1, 0, 1]:
                set_px(int(cx)+dx, int(cy)+dy, WHITE if dx*dy==0 else GOLD_FIRE)
        for r_x, r_y in [(15, 10), (11, 19), (20, 19)]:
            set_px(r_x, r_y-1, GOLD_FIRE); set_px(r_x, r_y, ORANGE); set_px(r_x, r_y+1, ORANGE)

    elif spell_id == 'wither_ray':
        # Wither skull & Nether Star glow
        # Skull forehead
        for x in range(12, 20):
            set_px(x, 12, (235, 230, 245, 255))
            set_px(x, 13, (220, 215, 230, 255))
        # Eye sockets
        for x in range(12, 20):
            set_px(x, 14, (200, 195, 210, 255))
            set_px(x, 15, (200, 195, 210, 255))
        set_px(14, 14, BLACK); set_px(15, 14, BLACK)
        set_px(17, 14, BLACK); set_px(18, 14, BLACK)
        # Nose cavity
        set_px(16, 16, BLACK)
        # Teeth / jaw
        for x in [13, 15, 17, 19]:
            set_px(x, 18, (230, 230, 240, 255))
            set_px(x-1, 18, (60, 50, 70, 255))

    elif spell_id == 'shulker_levitation':
        # Shulker shell halves + levitating pearl
        for x in range(13, 19):
            set_px(x, 11, ACCENT); set_px(x, 20, (110, 45, 130, 255))
        set_px(12, 12, ACCENT); set_px(19, 12, ACCENT)
        set_px(12, 19, (110, 45, 130, 255)); set_px(19, 19, (110, 45, 130, 255))
        # Central levitating pearl
        for dx in [-1, 0, 1]:
            for dy in [-1, 0, 1]:
                set_px(15+dx, 15+dy, WHITE if dx*dy==0 else ACCENT)

    elif spell_id == 'meteor_strike':
        # Blazing fiery meteor core with cracks
        for y in range(12, 20):
            for x in range(12, 20):
                if (x == y or x + y == 31):
                    set_px(x, y, (255, 250, 180, 255))
                elif (x * y) % 3 == 0:
                    set_px(x, y, (220, 60, 10, 255))
                else:
                    set_px(x, y, (255, 150, 30, 255))

    elif spell_id == 'iron_armor':
        # Iron knight heater shield
        for y in range(12, 16):
            for x in range(12, 20):
                set_px(x, y, (220, 230, 240, 255))
        for y in range(16, 20):
            w = 20 - y
            for x in range(16 - w, 16 + w):
                set_px(x, y, (190, 205, 215, 255))
        # Cross plate
        for y in range(12, 20): set_px(15, y, (130, 145, 160, 255)); set_px(16, y, (130, 145, 160, 255))
        for x in range(12, 20): set_px(x, 14, (130, 145, 160, 255))
        set_px(15, 14, WHITE); set_px(16, 14, WHITE)

    elif spell_id == 'vex_legion':
        # Vex winged ghost spirit
        PALE = (210, 235, 250, 255)
        for x in [14, 15, 16, 17]:
            for y in [12, 13, 14]:
                set_px(x, y, PALE)
        set_px(14, 13, (220, 40, 40, 255)); set_px(17, 13, (220, 40, 40, 255))
        set_px(15, 15, WHITE); set_px(16, 15, WHITE)
        set_px(15, 16, PALE); set_px(16, 16, PALE)
        set_px(15, 17, (120, 160, 190, 255))
        for i in range(1, 5):
            set_px(15 - i, 14 - i//2, ACCENT); set_px(16 + i, 14 - i//2, ACCENT)
            set_px(15 - i, 15 - i//2, WHITE); set_px(16 + i, 15 - i//2, WHITE)

    elif spell_id == 'guardian_beam':
        # Guardian eye with pupil and cyan laser ray
        CORAL = (255, 110, 70, 255)
        SEA = (86, 224, 181, 255)
        for dx in range(-4, 5):
            for dy in range(-3, 4):
                if abs(dx) + abs(dy) * 1.3 <= 4:
                    set_px(int(cx)+dx, int(cy)+dy, SEA)
        set_px(15, 15, BLACK); set_px(16, 15, BLACK)
        set_px(15, 14, CORAL); set_px(16, 14, CORAL)
        set_px(15, 16, WHITE); set_px(16, 16, WHITE)
        for x in range(17, 21): set_px(x, 15, ACCENT)

    # 6. Specular Glass Glint (top-left at x=11..13, y=9..11)
    set_px(11, 10, WHITE); set_px(12, 9, WHITE); set_px(12, 10, WHITE)
    set_px(13, 9, (255, 255, 255, 200))
    set_px(11, 11, (255, 255, 255, 180))

    return grid


def main():
    print(f'Generating {len(SPELLS)} Core pixel art textures in {OUTPUT} and {EVERGARDEN_CORES}...')
    valid_names = {f'core_{spell_id}.png' for spell_id, _, _, _, _, _ in SPELLS}
    # These independently authored Mythic cores are maintained as final PNG assets.
    valid_names.update({'core_solar_apocalypse.png', 'core_chronos_final_hour.png'})
    
    # Clean up legacy/unregistered cores
    for d in (OUTPUT, EVERGARDEN_CORES):
        if d.is_dir():
            for p in d.glob('core_*.png'):
                if p.name not in valid_names:
                    print(f'  [CLEANUP] Removing obsolete core: {p}')
                    p.unlink()

    for spell_id, title, primary, highlight, dark_shade, glow in SPELLS:
        pixels = generate_core(spell_id, title, primary, highlight, dark_shade, glow)
        pixels128 = scale_4x(pixels)
        out_file128 = OUTPUT / f'core_{spell_id}.png'
        png(out_file128, pixels128, size=128)
        out_file32 = EVERGARDEN_CORES / f'core_{spell_id}.png'
        png(out_file32, pixels, size=32)
        print(f'  [OK] {out_file128.name} & {out_file32.name} ({title})')
    print('All Core textures generated successfully!')


if __name__ == '__main__':
    main()
