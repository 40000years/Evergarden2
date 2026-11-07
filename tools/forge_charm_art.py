"""Draw the Evergarden Forge charm icons (27 charms + menu icons + soul orb) as 64px pixel art.

Each icon is painted at 512px with vector shapes and gradients (a temple frame plus the charm's own
emblem), reduced to 64px, given a hard alpha edge and a 1px dark outline so it reads at 16px.

    python tools/forge_charm_art.py          # writes evergarden-forge/art/charms/source/*.png + catalog
Then `python tools/build_forge_resources.py` packages them with the gems.
"""
from pathlib import Path
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'evergarden-forge/art/charms'
SRC = OUT / 'source'
S = 512
C = S / 2


def rgba(hex_, a=255):
    hex_ = hex_.lstrip('#')
    return tuple(int(hex_[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def blank():
    return Image.new('RGBA', (S, S), (0, 0, 0, 0))


def mask():
    return Image.new('L', (S, S), 0)


def radial(c0, c1, cx=C, cy=C, r=S / 2, power=1.0):
    y, x = np.mgrid[0:S, 0:S].astype(np.float32)
    t = np.clip(np.hypot(x - cx, y - cy) / r, 0, 1) ** power
    a, b = np.array(rgba(c0), np.float32), np.array(rgba(c1), np.float32)
    return Image.fromarray((a + (b - a) * t[..., None]).astype(np.uint8), 'RGBA')


def linear(c0, c1, angle=90, lo=0.0, hi=1.0):
    y, x = np.mgrid[0:S, 0:S].astype(np.float32) / S
    rad = math.radians(angle)
    t = (x - .5) * math.cos(rad) + (y - .5) * math.sin(rad) + .5
    t = np.clip((t - lo) / (hi - lo), 0, 1)
    a, b = np.array(rgba(c0), np.float32), np.array(rgba(c1), np.float32)
    return Image.fromarray((a + (b - a) * t[..., None]).astype(np.uint8), 'RGBA')


def paint(img, m, fill):
    """Composite a fill (colour or image) through a mask onto img."""
    layer = Image.new('RGBA', (S, S), rgba(fill)) if isinstance(fill, str) else fill
    img.alpha_composite(Image.composite(layer, Image.new('RGBA', (S, S), (0, 0, 0, 0)), m))


def erode(m, px):
    return m.filter(ImageFilter.MinFilter(px * 2 + 1))


def band(m, px):
    inner = erode(m, px)
    return Image.fromarray(np.clip(np.array(m, np.int16) - np.array(inner, np.int16), 0, 255).astype(np.uint8), 'L')


def shape(fn):
    m = mask()
    fn(ImageDraw.Draw(m))
    return m


def poly_mask(points):
    return shape(lambda d: d.polygon(points, fill=255))


def ellipse_mask(box):
    return shape(lambda d: d.ellipse(box, fill=255))


def star_points(cx, cy, r1, r2, n=5, rot=-90):
    pts = []
    for i in range(n * 2):
        r = r1 if i % 2 == 0 else r2
        a = math.radians(rot + i * 180 / n)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def solid(img, m, fill, rim=None, rim_px=12, light=None, light_offset=(-10, -12), light_shrink=18):
    """Fill a mask, give it a darker rim and an inner top-left highlight."""
    paint(img, m, fill)
    if light:
        inner = erode(m, light_shrink)
        shifted = Image.new('L', (S, S), 0)
        shifted.paste(inner, light_offset)
        hl = Image.fromarray(np.minimum(np.array(shifted), np.array(inner)).astype(np.uint8), 'L')
        paint(img, hl, light)
    if rim:
        paint(img, band(m, rim_px), rim)


# ---- temple frames ---------------------------------------------------------------------------

def bail(img, color='#c9c3d6', dark='#5b5468'):
    ring = ellipse_mask((C - 34, 14, C + 34, 82))
    hole = ellipse_mask((C - 16, 32, C + 16, 64))
    ring = Image.fromarray(np.clip(np.array(ring, np.int16) - np.array(hole, np.int16), 0, 255).astype(np.uint8), 'L')
    solid(img, ring, color, rim=dark, rim_px=7)


def frame_dark(img):
    bail(img)
    pts = [(C, 58), (C + 150, 150), (C + 170, 270), (C + 112, 392), (C, 486), (C - 112, 392), (C - 170, 270), (C - 150, 150)]
    outer = poly_mask(pts)
    paint(img, outer, linear('#b46cff', '#3d136b', 120))
    paint(img, band(outer, 14), '#22093a')
    inner = erode(outer, 34)
    paint(img, inner, radial('#3a1556', '#0d0414', C, 250, 230, 1.2))
    paint(img, band(inner, 8), '#7b34c9')
    # rim gems at the four corners of the shield
    for x, y in ((C, 74), (C + 160, 262), (C - 160, 262), (C, 470)):
        gem = poly_mask([(x, y - 18), (x + 16, y), (x, y + 18), (x - 16, y)])
        solid(img, gem, '#e04a7a', rim='#5a0f2c', rim_px=4, light='#ffb3cf', light_offset=(-3, -4), light_shrink=6)
    return inner


def frame_astral(img):
    bail(img, '#ffe08a', '#8a5a12')
    outer = ellipse_mask((44, 64, S - 44, S - 24))
    paint(img, outer, linear('#ffe9a6', '#a8641e', 125))
    paint(img, band(outer, 12), '#5e3a0c')
    inner = erode(outer, 30)
    paint(img, inner, radial('#24479c', '#070f2e', C - 30, 250, 260, 1.1))
    paint(img, band(inner, 7), '#d9a441')
    rng = np.random.default_rng(7)
    d = ImageDraw.Draw(img)
    for _ in range(34):
        x, y = rng.uniform(110, 400), rng.uniform(130, 440)
        if math.hypot(x - C, y - 278) > 170:
            continue
        r = rng.choice([3, 4, 5])
        d.ellipse((x - r, y - r, x + r, y + r), fill=rgba('#cfe6ff', int(rng.uniform(120, 230))))
    # notches on the gold rim like a compass
    for i in range(8):
        a = math.radians(i * 45 - 90)
        x, y = C + 214 * math.cos(a), 278 + 214 * math.sin(a)
        dot = ellipse_mask((x - 11, y - 11, x + 11, y + 11))
        solid(img, dot, '#fff3c4', rim='#8a5a12', rim_px=4)
    return inner


def frame_time(img):
    bail(img, '#e0b36a', '#6b3f12')
    cy = 278
    teeth = []
    n = 12
    for i in range(n * 2):
        a0 = math.radians(i * 180 / n - 90 - 6)
        a1 = math.radians(i * 180 / n - 90 + 6)
        r = 222 if i % 2 == 0 else 196
        teeth += [(C + r * math.cos(a0), cy + r * math.sin(a0)), (C + r * math.cos(a1), cy + r * math.sin(a1))]
    outer = poly_mask(teeth)
    paint(img, outer, linear('#ffd58a', '#7a4410', 130))
    paint(img, band(outer, 11), '#4a2608')
    inner = ellipse_mask((C - 168, cy - 168, C + 168, cy + 168))
    paint(img, inner, radial('#1f6f68', '#06201f', C - 20, cy - 20, 200, 1.1))
    paint(img, band(inner, 9), '#e8b04f')
    d = ImageDraw.Draw(img)
    for i in range(12):
        a = math.radians(i * 30 - 90)
        r0, r1 = (128, 152) if i % 3 == 0 else (140, 152)
        d.line((C + r0 * math.cos(a), cy + r0 * math.sin(a), C + r1 * math.cos(a), cy + r1 * math.sin(a)),
               fill=rgba('#f4d590', 200), width=7 if i % 3 == 0 else 4)
    return inner


FRAMES = {'dark': frame_dark, 'astral': frame_astral, 'time': frame_time}

# emblem centre (inside every frame)
EX, EY = C, 278


# ---- emblems -----------------------------------------------------------------------------------

def drop(img, x, y, s, fill='#d81e3c', rim='#4a0614', light='#ff8aa0'):
    pts = []
    for i in range(72):
        t = i / 72 * 2 * math.pi
        # teardrop with its point at the top
        pts.append((x + s * 0.78 * math.sin(t) * math.sin(t / 2), y - s * math.cos(t)))
    m = poly_mask(pts)
    m = m.filter(ImageFilter.GaussianBlur(2)).point(lambda v: 255 if v > 128 else 0)
    solid(img, m, fill, rim=rim, rim_px=9, light=light, light_offset=(-12, -6), light_shrink=int(s * .32))


def heart_mask(x, y, s):
    pts = []
    for i in range(80):
        t = i / 80 * 2 * math.pi
        px = 16 * math.sin(t) ** 3
        py = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
        pts.append((x + px * s / 16, y - py * s / 16))
    return poly_mask(pts)


def e_blood_pact(img):
    drop(img, EX, EY + 18, 118)
    drop(img, EX + 104, EY + 80, 34)
    drop(img, EX - 100, EY + 96, 26)
    h = heart_mask(EX - 6, EY + 40, 34)
    solid(img, h, '#2a0610', rim='#120208', rim_px=4)


def e_glass_shadow(img):
    shard = poly_mask([(EX - 10, EY - 140), (EX + 96, EY - 20), (EX + 30, EY + 140), (EX - 92, EY + 40)])
    solid(img, shard, linear('#f3e6ff', '#9b6ad6', 60), rim='#3a1660', rim_px=9, light='#ffffff', light_shrink=40)
    shadow = poly_mask([(EX - 10, EY - 140), (EX - 92, EY + 40), (EX + 30, EY + 140), (EX - 4, EY + 10)])
    paint(img, shadow, rgba('#1b0a2a', 180) and Image.new('RGBA', (S, S), rgba('#1b0a2a', 170)))
    d = ImageDraw.Draw(img)
    for seg in [((EX + 10, EY - 60), (EX + 30, EY - 10), (EX + 8, EY + 30), (EX + 36, EY + 80)),
                ((EX + 30, EY - 10), (EX + 70, EY - 4)), ((EX + 8, EY + 30), (EX - 40, EY + 44))]:
        d.line(seg, fill=rgba('#2a0f45'), width=8, joint='curve')


def e_grave_debt(img):
    skull = shape(lambda d: (d.ellipse((EX - 104, EY - 124, EX + 104, EY + 64), fill=255),
                             d.rounded_rectangle((EX - 62, EY + 20, EX + 62, EY + 112), 22, fill=255)))
    solid(img, skull, linear('#fff7ea', '#c9b9a0', 90), rim='#4a3b2c', rim_px=9, light='#ffffff', light_shrink=44)
    for dx in (-44, 44):
        eye = ellipse_mask((EX + dx - 32, EY - 44, EX + dx + 32, EY + 18))
        paint(img, eye, '#1a0820')
        glow = ellipse_mask((EX + dx - 10, EY - 22, EX + dx + 10, EY - 2))
        paint(img, glow, '#c04dff')
    nose = poly_mask([(EX, EY + 22), (EX + 16, EY + 52), (EX - 16, EY + 52)])
    paint(img, nose, '#1a0820')
    d = ImageDraw.Draw(img)
    for x in (-36, -12, 12, 36):
        d.line((EX + x, EY + 74, EX + x, EY + 108), fill=rgba('#4a3b2c'), width=7)


def flame_mask(x, y, w, h):
    pts = []
    for i in range(60):
        t = i / 59
        a = t * math.pi
        px = x + math.sin(a) * w * (1 - t * .55) * (1 if i < 30 else 1)
        pts.append((px, y + h * .5 - t * h * 1.0))
    left = [(2 * x - px, py) for px, py in reversed(pts)]
    return poly_mask(pts + left)


def e_last_light(img):
    candle = shape(lambda d: d.rounded_rectangle((EX - 46, EY - 6, EX + 46, EY + 132), 14, fill=255))
    solid(img, candle, linear('#f6ead2', '#b49a78', 0), rim='#4a3420', rim_px=8, light='#ffffff', light_shrink=14)
    d = ImageDraw.Draw(img)
    d.line((EX, EY - 6, EX, EY - 30), fill=rgba('#2a1a10'), width=8)
    wax = shape(lambda d: d.ellipse((EX + 14, EY - 2, EX + 40, EY + 54), fill=255))
    paint(img, wax, '#e9dcbf')
    glow = radial('#ffd27a', '#ffd27a', EX, EY - 90, 150)
    a = np.array(radial('#ffffff', '#000000', EX, EY - 90, 150, .8))[..., 0].astype(np.float32) * .55
    g = np.array(glow)
    g[..., 3] = a.astype(np.uint8)
    img.alpha_composite(Image.fromarray(g, 'RGBA'))
    solid(img, flame_mask(EX, EY - 82, 56, 130), linear('#fff6c2', '#ff7a1a', 90, .3, 1), rim='#8a2a06', rim_px=7)
    solid(img, flame_mask(EX, EY - 64, 26, 70), '#ffffff')


def e_executioner(img):
    d = ImageDraw.Draw(img)
    d.line((EX - 110, EY + 130, EX + 80, EY - 100), fill=rgba('#3a2214'), width=30)
    d.line((EX - 110, EY + 130, EX + 80, EY - 100), fill=rgba('#6a4026'), width=16)
    blade = shape(lambda d: d.pieslice((EX - 30, EY - 170, EX + 170, EY + 30), 150, 330, fill=255))
    cut = ellipse_mask((EX + 10, EY - 120, EX + 150, EY + 0))
    blade = Image.fromarray(np.clip(np.array(blade, np.int16) - np.array(cut, np.int16) // 1, 0, 255).astype(np.uint8), 'L')
    solid(img, blade, linear('#ffffff', '#8c8aa3', 135), rim='#2c2a3a', rim_px=8)
    edge = band(blade, 16)
    edge_np = np.array(edge)
    yy, xx = np.mgrid[0:S, 0:S]
    edge_np[(xx - (EX + 70)) ** 2 + (yy - (EY - 70)) ** 2 < 70 ** 2] = 0
    paint(img, Image.fromarray(edge_np, 'L'), '#c8102e')


def e_soul_harvest(img):
    solid(img, flame_mask(EX, EY + 10, 92, 230), linear('#d8fff8', '#1fb5b5', 90, .1, 1), rim='#06474f', rim_px=9)
    solid(img, flame_mask(EX, EY + 40, 50, 130), '#eafffc')
    for dx in (-26, 26):
        eye = ellipse_mask((EX + dx - 13, EY + 30, EX + dx + 13, EY + 62))
        paint(img, eye, '#05363c')
    for x, y, r in ((EX - 120, EY - 60, 14), (EX + 118, EY - 20, 11), (EX + 96, EY + 110, 9)):
        solid(img, ellipse_mask((x - r, y - r, x + r, y + r)), '#7ff7ea', rim='#0b5a60', rim_px=4)


def hood_mask(x, y, s):
    return shape(lambda d: (d.ellipse((x - 56 * s, y - 120 * s, x + 56 * s, y + 4 * s), fill=255),
                            d.polygon([(x - 92 * s, y + 130 * s), (x - 48 * s, y - 40 * s), (x + 48 * s, y - 40 * s), (x + 92 * s, y + 130 * s)], fill=255)))


def e_shade_twin(img):
    back = hood_mask(EX + 48, EY - 8, .95)
    paint(img, back, Image.new('RGBA', (S, S), rgba('#140821', 230)))
    paint(img, band(back, 7), '#6a2fb0')
    front = hood_mask(EX - 34, EY + 10, 1.0)
    solid(img, front, linear('#cfa8ff', '#5a2a9a', 90), rim='#220a3a', rim_px=9, light='#efe0ff', light_shrink=30)
    face = ellipse_mask((EX - 34 - 34, EY - 64, EX - 34 + 34, EY + 0))
    paint(img, face, '#12061f')
    for dx in (-14, 14):
        paint(img, ellipse_mask((EX - 34 + dx - 6, EY - 40, EX - 34 + dx + 6, EY - 28)), '#ff5c8a')


def e_cursed_coin(img):
    coin = ellipse_mask((EX - 120, EY - 120, EX + 120, EY + 120))
    solid(img, coin, linear('#ffe9a0', '#b0761c', 120), rim='#5a3606', rim_px=10, light='#fff6d0', light_shrink=30)
    paint(img, band(erode(coin, 26), 7), '#8a5a10')
    eye = shape(lambda d: d.polygon([(EX - 84, EY), (EX - 30, EY - 44), (EX + 30, EY - 44), (EX + 84, EY), (EX + 30, EY + 44), (EX - 30, EY + 44)], fill=255))
    eye = eye.filter(ImageFilter.GaussianBlur(10)).point(lambda v: 255 if v > 110 else 0)
    solid(img, eye, '#f4e9ff', rim='#2a0a3a', rim_px=7)
    solid(img, ellipse_mask((EX - 34, EY - 34, EX + 34, EY + 34)), radial('#d070ff', '#4a0a7a', EX, EY, 34))
    paint(img, ellipse_mask((EX - 12, EY - 12, EX + 12, EY + 12)), '#0a0210')
    paint(img, ellipse_mask((EX - 22, EY - 24, EX - 10, EY - 12)), '#ffffff')


def e_marked_prey(img):
    ring = ellipse_mask((EX - 118, EY - 118, EX + 118, EY + 118))
    paint(img, band(ring, 22), '#e8203f')
    paint(img, band(ring, 8), '#5a0614')
    inner = ellipse_mask((EX - 62, EY - 62, EX + 62, EY + 62))
    paint(img, band(inner, 14), '#ff6b80')
    d = ImageDraw.Draw(img)
    for (x0, y0, x1, y1) in ((EX, EY - 150, EX, EY - 74), (EX, EY + 74, EX, EY + 150), (EX - 150, EY, EX - 74, EY), (EX + 74, EY, EX + 150, EY)):
        d.line((x0, y0, x1, y1), fill=rgba('#3a0410'), width=26)
        d.line((x0, y0, x1, y1), fill=rgba('#ff4a64'), width=12)
    solid(img, ellipse_mask((EX - 22, EY - 22, EX + 22, EY + 22)), '#fff0f2', rim='#7a0a1e', rim_px=6)


def sparkle(img, x, y, r, fill='#fff8e0', rim='#a8741c'):
    m = poly_mask(star_points(x, y, r, r * .32, 4, -90))
    solid(img, m, fill, rim=rim, rim_px=max(3, int(r * .12)))


def star(img, x, y, r, fill=None, rim='#8a5a12', light='#ffffff'):
    m = poly_mask(star_points(x, y, r, r * .45))
    solid(img, m, fill or linear('#fff6c8', '#f0b23c', 110), rim=rim, rim_px=max(4, int(r * .12)), light=light, light_shrink=int(r * .35))


def e_constellation(img):
    nodes = [(EX - 110, EY + 70), (EX - 50, EY - 40), (EX + 30, EY + 10), (EX + 110, EY - 90), (EX + 80, EY + 110)]
    d = ImageDraw.Draw(img)
    for a, b in ((0, 1), (1, 2), (2, 3), (2, 4)):
        d.line((*nodes[a], *nodes[b]), fill=rgba('#0a2a5a'), width=16)
        d.line((*nodes[a], *nodes[b]), fill=rgba('#8fd8ff'), width=7)
    for i, (x, y) in enumerate(nodes):
        star(img, x, y, 44 if i in (1, 3) else 32)


def e_gem_resonance(img):
    d = ImageDraw.Draw(img)
    for r, a in ((150, 120), (122, 200)):
        d.arc((EX - r, EY - r, EX + r, EY + r), 200, 340, fill=rgba('#9fe8ff', a), width=10)
        d.arc((EX - r, EY - r, EX + r, EY + r), 20, 160, fill=rgba('#9fe8ff', a), width=10)
    top, mid, bot = EY - 90, EY - 30, EY + 110
    gem = poly_mask([(EX - 70, top), (EX + 70, top), (EX + 104, mid), (EX, bot), (EX - 104, mid)])
    solid(img, gem, linear('#c8f6ff', '#2a8fe0', 100), rim='#0a2f66', rim_px=9)
    d = ImageDraw.Draw(img)
    for seg in (((EX - 104, mid), (EX + 104, mid)), ((EX - 70, top), (EX - 34, mid), (EX, bot)), ((EX + 70, top), (EX + 34, mid), (EX, bot)),
                ((EX - 34, mid), (EX, top)), ((EX + 34, mid), (EX, top))):
        d.line(seg, fill=rgba('#0f4a8f', 200), width=6)
    face = poly_mask([(EX - 70, top), (EX, top), (EX - 34, mid), (EX - 104, mid)])
    paint(img, face, Image.new('RGBA', (S, S), rgba('#ffffff', 120)))


def e_orbit(img):
    d = ImageDraw.Draw(img)
    box = (EX - 150, EY - 56, EX + 150, EY + 56)
    d.arc(box, 180, 360, fill=rgba('#0a2a5a'), width=18)
    d.arc(box, 180, 360, fill=rgba('#9fd8ff'), width=8)
    planet = ellipse_mask((EX - 66, EY - 66, EX + 66, EY + 66))
    solid(img, planet, radial('#fff0b0', '#d0841c', EX - 24, EY - 24, 110), rim='#6a3a08', rim_px=9)
    d = ImageDraw.Draw(img)
    d.arc(box, 0, 180, fill=rgba('#0a2a5a'), width=18)
    d.arc(box, 0, 180, fill=rgba('#bfe9ff'), width=8)
    for ang in (200, 320, 80):
        a = math.radians(ang)
        sparkle(img, EX + 150 * math.cos(a), EY + 56 * math.sin(a), 28, '#ffffff', '#2a6ab0')


def e_falling_star(img):
    tail = poly_mask([(EX + 40, EY - 40), (EX - 150, EY + 150), (EX - 120, EY + 160), (EX + 70, EY - 10)])
    tail_l = linear('#7fd3ff', '#7fd3ff', 45)
    a = np.array(linear('#ffffff', '#000000', 225, .3, .9))[..., 0]
    t = np.array(tail_l)
    t[..., 3] = (np.array(tail) * (a / 255.0) * .9).astype(np.uint8)
    img.alpha_composite(Image.fromarray(t, 'RGBA'))
    tail2 = poly_mask([(EX + 50, EY - 60), (EX - 90, EY + 60), (EX - 76, EY + 74), (EX + 66, EY - 40)])
    t2 = np.array(Image.new('RGBA', (S, S), rgba('#ffffff')))
    t2[..., 3] = (np.array(tail2) * (a / 255.0)).astype(np.uint8)
    img.alpha_composite(Image.fromarray(t2, 'RGBA'))
    star(img, EX + 56, EY - 54, 82)
    sparkle(img, EX - 110, EY - 90, 22, '#ffffff', '#2a6ab0')
    sparkle(img, EX + 120, EY + 90, 18, '#ffffff', '#2a6ab0')


def e_astral_tether(img):
    moon = ellipse_mask((EX - 130, EY - 110, EX + 50, EY + 70))
    bite = ellipse_mask((EX - 80, EY - 140, EX + 90, EY + 30))
    moon = Image.fromarray(np.clip(np.array(moon, np.int16) - np.array(bite, np.int16), 0, 255).astype(np.uint8), 'L')
    solid(img, moon, linear('#e6f4ff', '#6aaee8', 135), rim='#123a6a', rim_px=8)
    d = ImageDraw.Draw(img)
    pts = [(EX - 40, EY + 40), (EX + 10, EY + 70), (EX + 60, EY + 40), (EX + 96, EY - 6)]
    for i, (x, y) in enumerate(pts[:-1]):
        d.ellipse((x - 22, y - 14, x + 22, y + 14), outline=rgba('#0a2a5a'), width=14)
        d.ellipse((x - 22, y - 14, x + 22, y + 14), outline=rgba('#ffd76a'), width=6)
    star(img, EX + 100, EY - 40, 58)


def e_overflow(img):
    d = ImageDraw.Draw(img)
    cup = poly_mask([(EX - 96, EY - 80), (EX + 96, EY - 80), (EX + 70, EY + 6), (EX + 20, EY + 30), (EX - 20, EY + 30), (EX - 70, EY + 6)])
    solid(img, cup, linear('#ffe9a6', '#a8641e', 120), rim='#5a3606', rim_px=9, light='#fff6d0', light_shrink=24)
    stem = shape(lambda dd: (dd.rectangle((EX - 14, EY + 26, EX + 14, EY + 96), fill=255), dd.ellipse((EX - 70, EY + 86, EX + 70, EY + 126), fill=255)))
    solid(img, stem, linear('#ffe9a6', '#a8641e', 120), rim='#5a3606', rim_px=8)
    liquid = ellipse_mask((EX - 88, EY - 96, EX + 88, EY - 64))
    solid(img, liquid, radial('#bff2ff', '#2a8fe0', EX, EY - 80, 90), rim='#0a2f66', rim_px=5)
    for x, y, s in ((EX - 104, EY - 40, 26), (EX + 108, EY - 20, 22), (EX + 116, EY + 50, 16)):
        drop(img, x, y, s, '#5ec8ff', '#0a2f66', '#e0f8ff')
    sparkle(img, EX - 30, EY - 130, 26, '#ffffff', '#2a6ab0')


def e_gravity_well(img):
    arm = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(arm)
    for k in range(3):
        pts = []
        for i in range(90):
            t = i / 89
            a = t * 3.6 + k * 2 * math.pi / 3
            r = 20 + t * 140
            pts.append((EX + r * math.cos(a), EY + r * math.sin(a) * .8))
        d.line(pts, fill=rgba('#0a1440'), width=30, joint='curve')
        d.line(pts, fill=rgba('#8c7bff'), width=16, joint='curve')
        d.line(pts[:60], fill=rgba('#d6ceff'), width=6, joint='curve')
    img.alpha_composite(arm)
    core = ellipse_mask((EX - 36, EY - 36, EX + 36, EY + 36))
    solid(img, core, radial('#ffffff', '#8c7bff', EX, EY, 36), rim='#2a1a8a', rim_px=6)


def e_starlit_path(img):
    d = ImageDraw.Draw(img)
    pts = [(EX - 130, EY + 120), (EX - 60, EY + 60), (EX - 30, EY - 10), (EX + 40, EY - 50), (EX + 120, EY - 110)]
    for i in range(len(pts) - 1):
        x0, y0 = pts[i]
        x1, y1 = pts[i + 1]
        for t in np.linspace(0, 1, 5)[:-1]:
            x, y = x0 + (x1 - x0) * t, y0 + (y1 - y0) * t
            d.ellipse((x - 8, y - 8, x + 8, y + 8), fill=rgba('#bfe9ff', 220))
    sparkle(img, EX - 120, EY + 112, 30, '#dff4ff', '#2a6ab0')
    sparkle(img, EX - 30, EY - 10, 40, '#ffffff', '#2a6ab0')
    star(img, EX + 112, EY - 104, 62)


def e_wishing_star(img):
    glow = np.array(Image.new('RGBA', (S, S), rgba('#ffe27a')))
    glow[..., 3] = (np.array(radial('#ffffff', '#000000', EX, EY, 170, .9))[..., 0] * .5).astype(np.uint8)
    img.alpha_composite(Image.fromarray(glow, 'RGBA'))
    star(img, EX, EY + 6, 136)
    sparkle(img, EX + 120, EY - 120, 30, '#ffffff', '#2a6ab0')
    sparkle(img, EX - 128, EY - 80, 20, '#ffffff', '#2a6ab0')
    sparkle(img, EX - 100, EY + 120, 24, '#ffffff', '#2a6ab0')


def e_hourglass(img):
    d = ImageDraw.Draw(img)
    glass = poly_mask([(EX - 80, EY - 120), (EX + 80, EY - 120), (EX + 18, EY), (EX + 80, EY + 120), (EX - 80, EY + 120), (EX - 18, EY)])
    glass = glass.filter(ImageFilter.GaussianBlur(8)).point(lambda v: 255 if v > 120 else 0)
    paint(img, glass, Image.new('RGBA', (S, S), rgba('#cfe9ff', 110)))
    sand_top = poly_mask([(EX - 52, EY - 70), (EX + 52, EY - 70), (EX + 10, EY - 6), (EX - 10, EY - 6)])
    paint(img, sand_top, '#ffcf6a')
    sand_bot = poly_mask([(EX - 70, EY + 110), (EX + 70, EY + 110), (EX + 40, EY + 64), (EX, EY + 50), (EX - 40, EY + 64)])
    solid(img, sand_bot, '#ffcf6a', rim='#a8641e', rim_px=5)
    d.line((EX, EY - 6, EX, EY + 52), fill=rgba('#ffe7a0'), width=6)
    paint(img, band(glass, 8), '#e8f4ff')
    for y in (EY - 134, EY + 120):
        cap = shape(lambda dd, y=y: dd.rounded_rectangle((EX - 108, y, EX + 108, y + 26), 8, fill=255))
        solid(img, cap, linear('#ffd58a', '#7a4410', 90), rim='#3a1e06', rim_px=6)
    for x in (EX - 96, EX + 96):
        post = shape(lambda dd, x=x: dd.rectangle((x - 9, EY - 112, x + 9, EY + 122), fill=255))
        solid(img, post, linear('#ffd58a', '#7a4410', 0), rim='#3a1e06', rim_px=5)


def figure_mask(x, y, s=1.0):
    return shape(lambda d: (d.ellipse((x - 34 * s, y - 120 * s, x + 34 * s, y - 52 * s), fill=255),
                            d.polygon([(x - 70 * s, y + 110 * s), (x - 44 * s, y - 40 * s), (x + 44 * s, y - 40 * s), (x + 70 * s, y + 110 * s)], fill=255)))


def e_afterimage(img):
    for dx, alpha in ((86, 90), (44, 150)):
        m = figure_mask(EX + dx, EY + 10, .95)
        paint(img, m, Image.new('RGBA', (S, S), rgba('#46d1c0', alpha)))
    front = figure_mask(EX - 30, EY + 10)
    solid(img, front, linear('#fff1c4', '#e0952a', 90), rim='#5a2e06', rim_px=9, light='#ffffff', light_shrink=26)
    d = ImageDraw.Draw(img)
    for i, y in enumerate((EY - 40, EY + 10, EY + 60)):
        d.line((EX + 60, y, EX + 140 - i * 10, y), fill=rgba('#a8fff0', 200), width=8)


def e_stagnant_time(img):
    face = ellipse_mask((EX - 110, EY - 120, EX + 110, EY + 90))
    solid(img, face, linear('#fff6dc', '#d8c08a', 90), rim='#5a3a10', rim_px=9)
    melt = shape(lambda d: (d.rounded_rectangle((EX - 70, EY + 40, EX - 34, EY + 150), 18, fill=255),
                            d.rounded_rectangle((EX + 6, EY + 50, EX + 40, EY + 124), 17, fill=255),
                            d.rounded_rectangle((EX + 56, EY + 30, EX + 84, EY + 96), 14, fill=255)))
    solid(img, melt, linear('#fff6dc', '#d8c08a', 90), rim='#5a3a10', rim_px=7)
    paint(img, face, Image.new('RGBA', (S, S), rgba('#fff6dc', 0)))
    d = ImageDraw.Draw(img)
    d.line((EX, EY - 16, EX, EY - 90), fill=rgba('#2a1a08'), width=12)
    d.line((EX, EY - 16, EX + 56, EY + 10), fill=rgba('#2a1a08'), width=10)
    d.ellipse((EX - 12, EY - 28, EX + 12, EY - 4), fill=rgba('#c9852a'))
    for i in range(12):
        a = math.radians(i * 30 - 90)
        d.ellipse((EX + 86 * math.cos(a) - 5, EY - 16 + 86 * math.sin(a) - 5, EX + 86 * math.cos(a) + 5, EY - 16 + 86 * math.sin(a) + 5), fill=rgba('#8a5a1a'))


def e_rally(img):
    d = ImageDraw.Draw(img)
    d.arc((EX - 140, EY - 140, EX + 140, EY + 140), 200, 520 - 40, fill=rgba('#0a3a36'), width=30)
    d.arc((EX - 140, EY - 140, EX + 140, EY + 140), 200, 520 - 40, fill=rgba('#46d1c0'), width=16)
    a = math.radians(200)
    x, y = EX + 140 * math.cos(a), EY + 140 * math.sin(a)
    head = poly_mask([(x - 40, y + 6), (x + 30, y - 36), (x + 26, y + 46)])
    solid(img, head, '#46d1c0', rim='#0a3a36', rim_px=7)
    h = heart_mask(EX, EY + 6, 92)
    solid(img, h, linear('#ffb38a', '#d8303a', 100), rim='#5a0a14', rim_px=9, light='#ffe0d0', light_shrink=30)


def e_momentum(img):
    for i, (dx, c0, c1) in enumerate(((-90, '#7a5a2a', '#4a3010'), (-10, '#e0a040', '#8a5410'), (70, '#fff1b8', '#f2a63a'))):
        m = poly_mask([(EX + dx - 50, EY - 110), (EX + dx + 10, EY - 110), (EX + dx + 80, EY), (EX + dx + 10, EY + 110), (EX + dx - 50, EY + 110), (EX + dx + 20, EY)])
        solid(img, m, linear(c0, c1, 0), rim='#2a1404', rim_px=8)


def e_frozen_moment(img):
    d = ImageDraw.Draw(img)
    ring = ellipse_mask((EX - 132, EY - 132, EX + 132, EY + 132))
    paint(img, band(ring, 16), '#f2c35b')
    paint(img, band(ring, 6), '#5a3a10')
    for k in range(6):
        a = math.radians(k * 60 - 90)
        x1, y1 = EX + 110 * math.cos(a), EY + 110 * math.sin(a)
        d.line((EX, EY, x1, y1), fill=rgba('#0a3a5a'), width=26)
        d.line((EX, EY, x1, y1), fill=rgba('#dff8ff'), width=12)
        for off in (.55, .78):
            bx, by = EX + 110 * off * math.cos(a), EY + 110 * off * math.sin(a)
            for s in (-1, 1):
                b = a + s * math.radians(45)
                d.line((bx, by, bx + 30 * math.cos(b), by + 30 * math.sin(b)), fill=rgba('#0a3a5a'), width=18)
                d.line((bx, by, bx + 30 * math.cos(b), by + 30 * math.sin(b)), fill=rgba('#dff8ff'), width=8)
    solid(img, ellipse_mask((EX - 24, EY - 24, EX + 24, EY + 24)), '#ffffff', rim='#2a8fb0', rim_px=6)


def e_paradox(img):
    d = ImageDraw.Draw(img)
    for k in range(10):
        a = math.radians(k * 36 + 90)
        if math.cos(a) > 0.1:
            continue
        x0, y0 = EX + 104 * math.cos(a), EY + 104 * math.sin(a)
        x1, y1 = EX + 148 * math.cos(a), EY + 148 * math.sin(a)
        d.line((x0, y0, x1, y1), fill=rgba('#5a3006'), width=24)
        d.line((x0, y0, x1, y1), fill=rgba('#ffd35a'), width=12)
    disc = ellipse_mask((EX - 96, EY - 96, EX + 96, EY + 96))
    left = poly_mask([(0, 0), (EX, 0), (EX, S), (0, S)])
    sun = Image.fromarray(np.minimum(np.array(disc), np.array(left)), 'L')
    moon = Image.fromarray(np.clip(np.array(disc, np.int16) - np.array(left, np.int16), 0, 255).astype(np.uint8), 'L')
    paint(img, sun, radial('#fff3b0', '#f0a020', EX - 40, EY - 30, 120))
    paint(img, moon, radial('#bff8f0', '#1e8a82', EX + 40, EY - 30, 130))
    for x, y, r in ((EX + 40, EY - 30, 14), (EX + 58, EY + 40, 10)):
        paint(img, ellipse_mask((x - r, y - r, x + r, y + r)), '#167068')
    paint(img, band(disc, 9), '#3a2406')
    d.line((EX, EY - 96, EX, EY + 96), fill=rgba('#3a2406'), width=7)
    sparkle(img, EX + 120, EY - 100, 24, '#dffff8', '#167068')


def e_ore_echo(img):
    d = ImageDraw.Draw(img)
    for r, a in ((150, 90), (124, 170)):
        d.arc((EX - r + 30, EY - r + 30, EX + r + 30, EY + r + 30), 280, 350, fill=rgba('#ffe2a0', a), width=9)
        d.arc((EX - r + 30, EY - r + 30, EX + r + 30, EY + r + 30), 100, 170, fill=rgba('#ffe2a0', a), width=9)
    ore = poly_mask([(EX - 10, EY - 20), (EX + 100, EY - 40), (EX + 140, EY + 60), (EX + 60, EY + 130), (EX - 30, EY + 90)])
    solid(img, ore, linear('#9a9aa8', '#4a4a58', 120), rim='#1e1e28', rim_px=8)
    for x, y, r in ((EX + 50, EY + 20, 26), (EX + 98, EY + 70, 18), (EX + 20, EY + 80, 16)):
        solid(img, poly_mask([(x, y - r), (x + r, y), (x, y + r), (x - r, y)]), '#ffd35a', rim='#7a4a06', rim_px=5)
    d.line((EX - 140, EY + 140, EX + 20, EY - 30), fill=rgba('#2a1606'), width=28)
    d.line((EX - 140, EY + 140, EX + 20, EY - 30), fill=rgba('#8a5a2a'), width=14)
    head = shape(lambda dd: dd.arc((EX - 120, EY - 150, EX + 80, EY + 50), 200, 340, fill=255, width=34))
    solid(img, head, linear('#e8f4ff', '#7a8a9a', 135), rim='#1e2a36', rim_px=7)


def e_lost_hours(img):
    d = ImageDraw.Draw(img)
    d.line((EX, EY - 150, EX, EY - 110), fill=rgba('#5a3a10'), width=18)
    crown = ellipse_mask((EX - 22, EY - 168, EX + 22, EY - 132))
    solid(img, crown, '#f2c35b', rim='#5a3a10', rim_px=6)
    case = ellipse_mask((EX - 124, EY - 116, EX + 124, EY + 132))
    solid(img, case, linear('#ffe2a0', '#9a5a14', 125), rim='#3a1e06', rim_px=9)
    face = erode(case, 24)
    paint(img, face, radial('#fffaf0', '#e0d2b0', EX - 20, EY - 20, 120))
    d = ImageDraw.Draw(img)
    for i in range(12):
        if i in (4, 5, 6):
            continue
        a = math.radians(i * 30 - 90)
        x, y = EX + 78 * math.cos(a), EY + 8 + 78 * math.sin(a)
        d.ellipse((x - 6, y - 6, x + 6, y + 6), fill=rgba('#5a3a10'))
    d.line((EX, EY + 8, EX - 30, EY - 50), fill=rgba('#2a1606'), width=10)
    d.line((EX, EY + 8, EX + 50, EY + 26), fill=rgba('#2a1606'), width=8)
    for seg in (((EX + 10, EY + 20), (EX + 40, EY + 60), (EX + 30, EY + 100)), ((EX + 40, EY + 60), (EX + 84, EY + 58))):
        d.line(seg, fill=rgba('#7a6a5a'), width=6)
    shard = poly_mask([(EX + 40, EY + 64), (EX + 90, EY + 70), (EX + 70, EY + 100)])
    paint(img, shard, '#1e1206')



# ---- 0.7.1 emblems --------------------------------------------------------------------------------

def e_shadow_step(img):
    d = ImageDraw.Draw(img)
    for dx, alpha in ((-118, 70), (-74, 120)):
        m = hood_mask(EX + dx, EY + 10, .82)
        paint(img, m, Image.new('RGBA', (S, S), rgba('#7a34c9', alpha)))
    for y, length in ((EY - 50, 120), (EY + 10, 160), (EY + 70, 110)):
        d.line((EX - 150, y, EX - 150 + length, y), fill=rgba('#c9a0ff', 170), width=9)
    front = hood_mask(EX + 30, EY + 10, .9)
    solid(img, front, linear('#2a0f45', '#0a0414', 90), rim='#b46cff', rim_px=8)
    for dx in (-13, 13):
        paint(img, ellipse_mask((EX + 30 + dx - 7, EY - 36, EX + 30 + dx + 7, EY - 22)), '#ff5c8a')


def e_crimson_wave(img):
    glow = np.array(Image.new('RGBA', (S, S), rgba('#ff3b55')))
    glow[..., 3] = (np.array(radial('#ffffff', '#000000', EX, EY, 170, .9))[..., 0] * .35).astype(np.uint8)
    img.alpha_composite(Image.fromarray(glow, 'RGBA'))
    outer = ellipse_mask((EX - 140, EY - 140, EX + 140, EY + 140))
    inner = ellipse_mask((EX - 90, EY - 190, EX + 170, EY + 70))
    blade = Image.fromarray(np.clip(np.array(outer, np.int16) - np.array(inner, np.int16), 0, 255).astype(np.uint8), 'L')
    solid(img, blade, linear('#ffc2cc', '#d0102e', 45, .25, .85), rim='#3a0410', rim_px=9, light='#ffffff', light_offset=(-6, 8), light_shrink=22)
    for x, y, r in ((EX + 96, EY + 76, 26), (EX + 140, EY + 20, 18), (EX + 40, EY + 136, 16)):
        drop(img, x, y, r)


def e_hex_brand(img):
    d = ImageDraw.Draw(img)
    glow = np.array(Image.new('RGBA', (S, S), rgba('#9a4dff')))
    glow[..., 3] = (np.array(radial('#ffffff', '#000000', EX, EY, 170, .9))[..., 0] * .45).astype(np.uint8)
    img.alpha_composite(Image.fromarray(glow, 'RGBA'))
    ring = ellipse_mask((EX - 130, EY - 130, EX + 130, EY + 130))
    paint(img, band(ring, 16), '#c98bff')
    paint(img, band(ring, 6), '#2a0a4a')
    pts = star_points(EX, EY, 118, 118, 5)[::2]
    order = [pts[0], pts[2], pts[4], pts[1], pts[3], pts[0]]
    d = ImageDraw.Draw(img)
    d.line(order, fill=rgba('#2a0a4a'), width=22, joint='curve')
    d.line(order, fill=rgba('#e6c8ff'), width=10, joint='curve')
    for x, y in pts:
        solid(img, ellipse_mask((x - 14, y - 14, x + 14, y + 14)), '#ff5ce0', rim='#4a0a4a', rim_px=4)
    solid(img, ellipse_mask((EX - 26, EY - 26, EX + 26, EY + 26)), radial('#ffffff', '#b46cff', EX, EY, 26), rim='#2a0a4a', rim_px=5)


def bat_mask(x, y, s):
    wing = [(x, y - 10 * s), (x + 40 * s, y - 60 * s), (x + 150 * s, y - 70 * s), (x + 130 * s, y - 20 * s), (x + 150 * s, y + 20 * s),
            (x + 100 * s, y + 6 * s), (x + 80 * s, y + 50 * s), (x + 40 * s, y + 20 * s), (x, y + 60 * s)]
    left = [(2 * x - px, py) for px, py in reversed(wing)]
    return shape(lambda d: (d.polygon(wing + left, fill=255), d.ellipse((x - 34 * s, y - 70 * s, x + 34 * s, y + 70 * s), fill=255),
                            d.polygon([(x - 30 * s, y - 50 * s), (x - 20 * s, y - 100 * s), (x - 4 * s, y - 60 * s)], fill=255),
                            d.polygon([(x + 30 * s, y - 50 * s), (x + 20 * s, y - 100 * s), (x + 4 * s, y - 60 * s)], fill=255)))


def e_bat_form(img):
    for x, y, s, a in ((EX - 120, EY - 110, .28, 170), (EX + 118, EY - 90, .24, 140), (EX + 100, EY + 120, .22, 120)):
        paint(img, bat_mask(x, y, s), Image.new('RGBA', (S, S), rgba('#8a5ac0', a)))
    m = bat_mask(EX, EY + 20, .95)
    solid(img, m, linear('#b98ae8', '#4a1a6a', 90), rim='#12061f', rim_px=8, light='#e6c8ff', light_shrink=12)
    for dx in (-14, 14):
        paint(img, ellipse_mask((EX + dx - 8, EY - 12, EX + dx + 8, EY + 4)), '#ff3b5c')


def e_eye_of_abyss(img):
    d = ImageDraw.Draw(img)
    for k in range(12):
        a = math.radians(k * 30)
        d.line((EX + 120 * math.cos(a), EY + 100 * math.sin(a), EX + 160 * math.cos(a), EY + 140 * math.sin(a)), fill=rgba('#c98bff', 160), width=8)
    eye = shape(lambda dd: dd.polygon([(EX - 140, EY), (EX - 60, EY - 80), (EX + 60, EY - 80), (EX + 140, EY), (EX + 60, EY + 80), (EX - 60, EY + 80)], fill=255))
    eye = eye.filter(ImageFilter.GaussianBlur(16)).point(lambda v: 255 if v > 110 else 0)
    solid(img, eye, linear('#fff6ff', '#d6c0f0', 90), rim='#2a0a3a', rim_px=9)
    solid(img, ellipse_mask((EX - 58, EY - 58, EX + 58, EY + 58)), radial('#ff9af0', '#5a0a9a', EX, EY, 60), rim='#1a0428', rim_px=6)
    paint(img, ellipse_mask((EX - 12, EY - 46, EX + 12, EY + 46)), '#0a0210')
    paint(img, ellipse_mask((EX - 34, EY - 36, EX - 16, EY - 18)), '#ffffff')


def e_starwalker(img):
    for y, c0, c1 in ((EY + 70, '#3a6ab0', '#1a3a7a'), (EY - 10, '#7fd3ff', '#2f7be0')):
        m = poly_mask([(EX - 110, y + 40), (EX, y - 60), (EX + 110, y + 40), (EX + 70, y + 70), (EX, y), (EX - 70, y + 70)])
        solid(img, m, linear(c0, c1, 90), rim='#0a1a3a', rim_px=8, light='#e0f6ff', light_shrink=18)
    star(img, EX, EY - 118, 50)
    for x, y, r in ((EX - 120, EY - 60, 18), (EX + 124, EY - 40, 14), (EX - 90, EY + 130, 12)):
        sparkle(img, x, y, r, '#ffffff', '#2a6ab0')


def e_sky_glider(img):
    pts = []
    for i in range(41):
        t = i / 40
        pts.append((EX - 120 + t * 240, EY + 110 - t * 230))
    bulge = [math.sin(i / 40 * math.pi) for i in range(41)]
    right = [(x + 70 * b, y + 40 * b) for (x, y), b in zip(pts, bulge)]
    left = [(x - 40 * b, y - 70 * b) for (x, y), b in zip(pts, bulge)]
    vane = poly_mask(left + list(reversed(right)))
    solid(img, vane, linear('#ffffff', '#8fc8f0', 135), rim='#1a3a6a', rim_px=8, light='#ffffff', light_shrink=20)
    d = ImageDraw.Draw(img)
    for i in range(6, 36, 4):
        x, y = pts[i]
        b = bulge[i]
        d.line((x, y, x + 50 * b, y + 30 * b), fill=rgba('#7aa8d0', 200), width=5)
        d.line((x, y, x - 30 * b, y - 50 * b), fill=rgba('#7aa8d0', 200), width=5)
    d.line(pts, fill=rgba('#1a3a6a'), width=14, joint='curve')
    d.line(pts, fill=rgba('#e8f4ff'), width=6, joint='curve')
    d.line((EX - 120, EY + 110, EX - 150, EY + 146), fill=rgba('#1a3a6a'), width=12)


def e_star_familiar(img):
    d = ImageDraw.Draw(img)
    d.line((EX + 40, EY + 40, EX + 150, EY + 140), fill=rgba('#0a2a5a'), width=18)
    d.line((EX + 40, EY + 40, EX + 150, EY + 140), fill=rgba('#bfe9ff'), width=8)
    glow = np.array(Image.new('RGBA', (S, S), rgba('#ffe27a')))
    glow[..., 3] = (np.array(radial('#ffffff', '#000000', EX - 10, EY - 10, 150, .9))[..., 0] * .45).astype(np.uint8)
    img.alpha_composite(Image.fromarray(glow, 'RGBA'))
    m = poly_mask(star_points(EX - 10, EY - 10, 118, 64))
    m = m.filter(ImageFilter.GaussianBlur(12)).point(lambda v: 255 if v > 120 else 0)
    solid(img, m, linear('#fff6c8', '#f0b23c', 110), rim='#8a5a12', rim_px=9, light='#ffffff', light_shrink=34)
    for dx in (-34, 14):
        paint(img, ellipse_mask((EX + dx - 11, EY - 30, EX + dx + 11, EY + 2)), '#3a2406')
        paint(img, ellipse_mask((EX + dx - 5, EY - 26, EX + dx + 3, EY - 18)), '#ffffff')
    d = ImageDraw.Draw(img)
    d.arc((EX - 34, EY - 6, EX + 14, EY + 26), 20, 160, fill=rgba('#3a2406'), width=6)
    sparkle(img, EX + 150, EY + 140, 26, '#ffffff', '#2a6ab0')


def e_lodestar(img):
    horseshoe = shape(lambda dd: dd.arc((EX - 120, EY - 70, EX + 120, EY + 170), 0, 180, fill=255, width=70))
    arms = shape(lambda dd: (dd.rectangle((EX - 120, EY - 70, EX - 50, EY + 50), fill=255), dd.rectangle((EX + 50, EY - 70, EX + 120, EY + 50), fill=255)))
    body = Image.fromarray(np.maximum(np.array(horseshoe), np.array(arms)), 'L')
    solid(img, body, linear('#ff7a8a', '#b0102e', 90), rim='#3a0410', rim_px=8, light='#ffc0c8', light_shrink=14)
    for x0 in (EX - 120, EX + 50):
        tip = shape(lambda dd, x0=x0: dd.rectangle((x0, EY - 110, x0 + 70, EY - 64), fill=255))
        solid(img, tip, linear('#ffffff', '#9aa0b0', 90), rim='#2a2a3a', rim_px=7)
    star(img, EX, EY - 40, 46)
    d = ImageDraw.Draw(img)
    for x, y in ((EX - 85, EY - 150), (EX + 85, EY - 150)):
        d.arc((x - 30, y - 20, x + 30, y + 20), 200, 340, fill=rgba('#bfe9ff', 200), width=6)


def e_chrono_anchor(img):
    ring = ellipse_mask((EX - 40, EY - 150, EX + 40, EY - 70))
    hole = ellipse_mask((EX - 20, EY - 130, EX + 20, EY - 90))
    ring = Image.fromarray(np.clip(np.array(ring, np.int16) - np.array(hole, np.int16), 0, 255).astype(np.uint8), 'L')
    solid(img, ring, linear('#ffe2a0', '#9a5a14', 120), rim='#3a1e06', rim_px=7)
    shaft = shape(lambda dd: (dd.rectangle((EX - 16, EY - 80, EX + 16, EY + 120), fill=255), dd.rectangle((EX - 80, EY - 52, EX + 80, EY - 24), fill=255)))
    arc = shape(lambda dd: dd.arc((EX - 130, EY - 60, EX + 130, EY + 140), 20, 160, fill=255, width=30))
    tips = shape(lambda dd: (dd.polygon([(EX - 138, EY + 36), (EX - 96, EY + 30), (EX - 128, EY + 76)], fill=255),
                             dd.polygon([(EX + 138, EY + 36), (EX + 96, EY + 30), (EX + 128, EY + 76)], fill=255)))
    anchor = Image.fromarray(np.maximum(np.maximum(np.array(shaft), np.array(arc)), np.array(tips)), 'L')
    solid(img, anchor, linear('#ffe2a0', '#9a5a14', 120), rim='#3a1e06', rim_px=8, light='#fff6d0', light_shrink=10)
    d = ImageDraw.Draw(img)
    d.arc((EX - 70, EY - 10, EX + 70, EY + 130), 200, 340, fill=rgba('#46d1c0', 230), width=8)
    paint(img, poly_mask([(EX + 64, EY + 2), (EX + 90, EY + 30), (EX + 50, EY + 36)]), '#46d1c0')


EMBLEMS = {
    'shadow_step': ('dark', e_shadow_step), 'crimson_wave': ('dark', e_crimson_wave), 'soul_legion': ('dark', e_soul_harvest),
    'hex_brand': ('dark', e_hex_brand), 'reaper_mark': ('dark', e_executioner), 'bat_form': ('dark', e_bat_form),
    'nightfall_veil': ('dark', e_shade_twin), 'eye_of_abyss': ('dark', e_eye_of_abyss), 'cursed_coin': ('dark', e_cursed_coin),
    'starwalker': ('astral', e_starwalker), 'sky_glider': ('astral', e_sky_glider), 'meteor_fall': ('astral', e_falling_star),
    'star_familiar': ('astral', e_star_familiar), 'celestial_cascade': ('astral', e_constellation), 'orbit': ('astral', e_orbit),
    'black_hole': ('astral', e_gravity_well), 'lodestar': ('astral', e_lodestar), 'lucky_star': ('astral', e_wishing_star),
    'time_stop': ('time', e_frozen_moment), 'hourglass': ('time', e_hourglass), 'chrono_anchor': ('time', e_chrono_anchor),
    'echo_strike': ('time', e_afterimage), 'stasis_trap': ('time', e_stagnant_time), 'overclock': ('time', e_momentum),
    'timeless': ('time', e_rally), 'vein_of_ages': ('time', e_ore_echo), 'lost_hours': ('time', e_lost_hours),
}


# ---- menu icons -----------------------------------------------------------------------------

def notch(img, color, dark, light, empty=False):
    m = poly_mask([(C, 70), (C + 170, C), (C, S - 70), (C - 170, C)])
    if empty:
        paint(img, m, '#1c1626')
        paint(img, band(m, 22), '#5a5068')
        paint(img, band(m, 8), '#2a2236')
        return
    glow = np.array(Image.new('RGBA', (S, S), rgba(color)))
    glow[..., 3] = (np.array(radial('#ffffff', '#000000', C, C, 250, .9))[..., 0] * .45).astype(np.uint8)
    img.alpha_composite(Image.fromarray(glow, 'RGBA'))
    solid(img, m, linear(light, color, 120), rim=dark, rim_px=16)
    facet = poly_mask([(C, 70), (C + 170, C), (C, C)])
    paint(img, facet, Image.new('RGBA', (S, S), rgba('#ffffff', 70)))
    shade = poly_mask([(C, S - 70), (C - 170, C), (C, C)])
    paint(img, shade, Image.new('RGBA', (S, S), rgba('#000000', 60)))
    sparkle(img, C - 60, C - 70, 30, '#ffffff', dark)


def ui_icons():
    icons = {}
    for name, (color, dark, light) in {'dark': ('#9a4dff', '#2a0a4a', '#e6c8ff'), 'astral': ('#4fb4ff', '#0a2a5a', '#e0f6ff'),
                                       'time': ('#f2b33c', '#4a2606', '#fff1c4')}.items():
        img = blank()
        notch(img, color, dark, light)
        icons['charm_ui/notch_' + name] = img
    img = blank()
    notch(img, '', '', '', empty=True)
    icons['charm_ui/notch_empty'] = img
    # empty charm slot: a faint amulet outline
    img = blank()
    m = poly_mask([(C, 58), (C + 150, 150), (C + 170, 270), (C + 112, 392), (C, 486), (C - 112, 392), (C - 170, 270), (C - 150, 150)])
    paint(img, m, Image.new('RGBA', (S, S), rgba('#2a2436', 200)))
    paint(img, band(m, 16), '#6a6080')
    d = ImageDraw.Draw(img)
    d.line((C - 40, 270, C + 40, 270), fill=rgba('#8a80a0'), width=16)
    d.line((C, 230, C, 310), fill=rgba('#8a80a0'), width=16)
    icons['charm_ui/slot_empty'] = img
    # temple badges: the frame with a single sigil
    for temple, sigil in (('dark', 'eye'), ('astral', 'star'), ('time', 'glass')):
        img = blank()
        FRAMES[temple](img)
        if sigil == 'eye':
            moon = ellipse_mask((EX - 100, EY - 100, EX + 100, EY + 100))
            bite = ellipse_mask((EX - 40, EY - 130, EX + 140, EY + 60))
            moon = Image.fromarray(np.clip(np.array(moon, np.int16) - np.array(bite, np.int16), 0, 255).astype(np.uint8), 'L')
            solid(img, moon, linear('#f0d8ff', '#9a4dff', 135), rim='#2a0a4a', rim_px=9)
            sparkle(img, EX + 56, EY - 20, 34, '#ffd8ef', '#5a0f2c')
        elif sigil == 'star':
            star(img, EX, EY, 118)
        else:
            d = ImageDraw.Draw(img)
            for angle, length, width in ((-60, 118, 30), (35, 86, 34)):
                a = math.radians(angle - 90)
                x1, y1 = EX + length * math.cos(a), EY + length * math.sin(a)
                d.line((EX, EY, x1, y1), fill=rgba('#3a1e06'), width=width + 16)
                d.line((EX, EY, x1, y1), fill=rgba('#ffd58a'), width=width)
            sparkle(img, EX, EY, 62, '#fff1c4', '#4a2606')
            solid(img, ellipse_mask((EX - 22, EY - 22, EX + 22, EY + 22)), '#46d1c0', rim='#0a3a36', rim_px=6)
        icons['charm_ui/temple_' + temple] = img
    # forge tab / header: three small pendants
    img = blank()
    d = ImageDraw.Draw(img)
    d.arc((C - 190, -120, C + 190, 250), 30, 150, fill=rgba('#c9c3d6'), width=12)
    for (x, y, temple) in ((C - 130, 260, 'dark'), (C + 130, 260, 'time'), (C, 320, 'astral')):
        sub = blank()
        FRAMES[temple](sub)
        {'dark': e_blood_pact, 'astral': e_wishing_star, 'time': e_hourglass}[temple](sub)
        sub = sub.resize((260, 260), Image.Resampling.LANCZOS)
        img.alpha_composite(sub, (int(x - 130), int(y - 140)))
    icons['charm_ui/tab'] = img
    # slot meter: a crystal gauge
    img = blank()
    frame = shape(lambda dd: dd.regular_polygon((C, C, 200), 6, rotation=0, fill=255))
    solid(img, frame, linear('#ffe9a6', '#8a5410', 120), rim='#3a1e06', rim_px=12)
    inner = erode(frame, 30)
    paint(img, inner, radial('#2a2050', '#0a0618', C, C, 200))
    for i, (color, dark, light) in enumerate((('#9a4dff', '#2a0a4a', '#e6c8ff'), ('#4fb4ff', '#0a2a5a', '#e0f6ff'), ('#f2b33c', '#4a2606', '#fff1c4'))):
        a = math.radians(i * 120 - 90)
        x, y = C + 86 * math.cos(a), C + 86 * math.sin(a)
        g = poly_mask([(x, y - 54), (x + 40, y), (x, y + 54), (x - 40, y)])
        solid(img, g, linear(light, color, 120), rim=dark, rim_px=7)
    icons['charm_ui/meter'] = img
    return icons


# ---- finishing --------------------------------------------------------------------------------

def finish(big, size=64, outline='#120a18'):
    # one transparent pixel on every side, so the outline never touches the edge
    small = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    small.paste(big.resize((size - 2, size - 2), Image.Resampling.LANCZOS), (1, 1))
    a = np.array(small)
    alpha = a[..., 3]
    solid_ = alpha >= 96
    rgb = a[..., :3].astype(np.float32)
    # un-premultiply the soft edge so edge pixels keep their colour
    keep = np.where(solid_[..., None], rgb * (255.0 / np.maximum(alpha[..., None], 1)).clip(1, 2.5), 0)
    out = np.zeros_like(a)
    out[..., :3] = np.clip(keep, 0, 255).astype(np.uint8)
    out[..., 3] = np.where(solid_, 255, 0)
    m = Image.fromarray((solid_ * 255).astype(np.uint8), 'L')
    ring = np.array(m.filter(ImageFilter.MaxFilter(3))) > 0
    edge = ring & ~solid_
    out[edge] = rgba(outline)
    return Image.fromarray(out, 'RGBA')


def charm_icon(charm_id):
    temple, emblem = EMBLEMS[charm_id]
    img = blank()
    FRAMES[temple](img)
    emblem(img)
    return img


def catalog(icons):
    font = ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', 13)
    title = ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf', 18)
    names = list(icons)
    cols = 9
    rows = math.ceil(len(names) / cols)
    sheet = Image.new('RGB', (cols * 132 + 20, 60 + rows * 150), (24, 22, 34))
    d = ImageDraw.Draw(sheet)
    d.text((14, 12), 'Evergarden Forge | Charms 0.7.1', font=title, fill='#f1e6db')
    for i, name in enumerate(names):
        x, y = 14 + (i % cols) * 132, 50 + (i // cols) * 150
        big = icons[name].resize((112, 112), Image.Resampling.NEAREST)
        sheet.paste(big, (x + 8, y), big)
        small = icons[name].resize((32, 32), Image.Resampling.NEAREST)
        sheet.paste(small, (x + 92, y + 92), small)
        d.text((x + 4, y + 118), name.split('/')[-1][:18], font=font, fill='#e1d8ec')
    sheet.save(OUT / 'catalog.png')


def main():
    SRC.mkdir(parents=True, exist_ok=True)
    for stale in SRC.glob('*.png'):
        stale.unlink()
    icons = {}
    for charm_id in EMBLEMS:
        icons['charm/' + charm_id] = finish(charm_icon(charm_id))
    for key, big in ui_icons().items():
        icons[key] = finish(big)
    for key, im in icons.items():
        im.save(SRC / (key.replace('/', '_') + '.png'))
    catalog(icons)
    print(f'Drew {len(icons)} icons into {SRC.relative_to(ROOT)}')


if __name__ == '__main__':
    main()
