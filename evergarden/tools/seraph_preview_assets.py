"""Celestial Seraph mesh and painted atlas, authored in Minecraft model units.

One canonical mesh exports to Java item elements and Bedrock helmet geometry.
Thin tapered gown and bodice tiers sample shared front/back/side paintings, so the
stacked cuboids read as one draped surface. Wings, fringe, hair tips and halo are
alpha-cut painted planes instead of stepped blocks.
"""
import io
import json
import math
import numpy as np
from PIL import Image, ImageDraw

PREFIX = 'judge_seraph_'
SCALE = 1.6
CENTER_Y = 20
ATLAS_SIZE = 1024
REVISION = 'seraph-celestial-v3'
FACES = ('north', 'south', 'east', 'west', 'up', 'down')
# Minecraft FaceInfo: the corner that receives (u0, v0) and the one that receives (u1, v1).
CORNERS = {'north': ((1, 1, 0), (0, 0, 0)), 'south': ((0, 1, 1), (1, 0, 1)),
           'west': ((0, 1, 0), (0, 0, 1)), 'east': ((1, 1, 1), (1, 0, 0)),
           'up': ((0, 1, 0), (1, 1, 1)), 'down': ((0, 0, 1), (1, 0, 0))}

IVORY, SHADOW = (247, 245, 239), (212, 216, 228)
GOLD, GOLD_LIGHT, GOLD_DARK = (212, 170, 86), (247, 219, 148), (146, 106, 50)
SKIN, SKIN_SHADE = (248, 226, 212), (231, 194, 180)
HAIR, HAIR_SHADE, HAIR_DEEP = (238, 242, 248), (200, 210, 226), (164, 178, 204)
FEATHER, FEATHER_SHADE, FEATHER_LINE = (253, 253, 251), (210, 219, 234), (164, 179, 204)
GEM, GEM_DEEP = (128, 214, 230), (36, 112, 150)

HIP, SHOULDER = 16.6, 24.8
WING_ROOT, WING_WIDTH = 1.0, 21.0
WING_LAYERS = {'wing_a': (14.5, 39.8, 3.55), 'wing_b': (24.5, 39.8, 3.25), 'wing_c': (30.5, 40.0, 2.95)}


def skirt(t):
    """Width, depth and z centre of the gown, t = 0 at the hip and 1 at the hem."""
    return 4.05 + 4.1 * t ** 1.2, 2.8 + 2.55 * t ** 1.25, .05 + .42 * t ** 1.4


BODICE = [(16.6, 3.95, 2.75), (17.4, 3.62, 2.55), (18.6, 3.72, 2.6), (20.0, 4.15, 2.8),
          (21.5, 4.65, 2.95), (23.0, 4.95, 2.9), (24.8, 5.15, 2.7)]


def bodice(y):
    ys = [k[0] for k in BODICE]
    return float(np.interp(y, ys, [k[1] for k in BODICE])), float(np.interp(y, ys, [k[2] for k in BODICE]))


# --------------------------------------------------------------------------- painting

def mix(a, b, t):
    t = np.clip(t, 0, 1)[..., None] if isinstance(t, np.ndarray) else max(0., min(1., t))
    return np.asarray(a, float) * (1 - t) + np.asarray(b, float) * t


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def finish(rgba):
    """Binary alpha, with transparent texels recoloured so mipmaps never darken the edges."""
    data = np.asarray(rgba, dtype=np.float32).copy()
    solid = data[..., 3] >= 128
    data[..., 3] = np.where(solid, 255, 0)
    if solid.any() and not solid.all():
        data[~solid, :3] = data[solid, :3].mean(axis=0)
    return Image.fromarray(np.clip(data + .5, 0, 255).astype(np.uint8), 'RGBA')


def downsample(image, factor):
    """Premultiplied box filter: anti-aliased colour without dark fringes."""
    data = np.asarray(image, dtype=np.float32)
    h, w = data.shape[0] // factor, data.shape[1] // factor
    data = data[:h * factor, :w * factor].reshape(h, factor, w, factor, 4)
    alpha = data[..., 3:4]
    colour = (data[..., :3] * alpha).sum(axis=(1, 3)) / np.maximum(alpha.sum(axis=(1, 3)), 1e-3)
    return np.concatenate([colour, alpha.mean(axis=(1, 3))], axis=-1)


class Sheet:
    """A painted atlas region; `project` maps an unrotated model point to local pixels."""
    def __init__(self, image, project=None):
        self.image, self.project, self.origin = image, project, None

    def uv(self, face, lo, hi):
        ox, oy = self.origin
        if self.project is None:
            w, h = self.image.size
            return [ox + .5, oy + .5, ox + w - .5, oy + h - .5]
        corners = [[(lo, hi)[c][i] for i, c in enumerate(corner)] for corner in CORNERS[face]]
        (u0, v0), (u1, v1) = (self.project(p) for p in corners)
        return [ox + u0, oy + v0, ox + u1, oy + v1]


def grid(extent_u, extent_v, ppu, ss):
    w, h = round((extent_u[1] - extent_u[0]) * ppu), round((extent_v[1] - extent_v[0]) * ppu)
    u = (np.arange(w * ss) + .5) / (ppu * ss)
    v = extent_v[1] - (np.arange(h * ss) + .5) / (ppu * ss)
    return np.meshgrid(u, v), (w, h)


BODY_X, BODY_Y, BODY_Z = (-5.0, 5.0), (-.3, 25.6), (-3.7, 3.7)


def paint_body(view):
    """Front ('F', image left = +x), back ('B') or side ('S', front at left) of gown and bodice."""
    ppu, ss = 16, 2
    across = BODY_Z if view == 'S' else BODY_X
    (U, Y), _ = grid(across, BODY_Y, ppu, ss)
    X = across[1] - U if view == 'F' else across[0] + U
    t = np.clip((HIP - Y) / HIP, 0, 1)
    w, d, zc = skirt(t)
    bw = np.interp(Y, [k[0] for k in BODICE], [k[1] for k in BODICE])
    bd = np.interp(Y, [k[0] for k in BODICE], [k[2] for k in BODICE])
    in_skirt = Y < HIP
    if view == 'S':
        half = np.where(in_skirt, d / 2, bd / 2)
        u = (X - np.where(in_skirt, zc, 0)) / half
    else:
        half = np.where(in_skirt, w / 2, bw / 2)
        u = X / half
    au = np.clip(np.abs(u), 0, 1.2)
    # Folds fan out from the waist and deepen towards the hem.
    amp = np.where(in_skirt, .2 + .8 * t ** .8, .18)
    fold = (np.sin(u * math.pi * 3.1 + .7) * .6 + np.sin(u * math.pi * 5.7 + 2.1) * .4) * amp
    shade = .12 + .26 * fold + .55 * au ** 4 + np.where(in_skirt, .1 * t ** 2, 0)
    colour = mix(IVORY, SHADOW, shade)
    deep = smooth(.5, .95, fold / np.maximum(amp, .01)) * amp * .5
    colour = mix(colour, (190, 196, 214), deep)

    def paint(mask, rgb):
        colour[mask] = mix(colour[mask], rgb, 1) if not isinstance(rgb, np.ndarray) else rgb[mask]

    gold = mix(GOLD_DARK, GOLD_LIGHT, smooth(-1, 1, np.sin(Y * 9 + X * 3)) * .6 + .3)
    # Hem band with small lozenges, and a fine second line above it.
    hem = Y < .78
    paint(hem, gold)
    lozenge = hem & ((np.abs(((u * 9) % 2) - 1) + np.abs(Y - .39) / .28) < .85)
    paint(lozenge, np.broadcast_to(np.asarray(GOLD_LIGHT, float), colour.shape))
    paint(hem & ((np.abs(Y - .08) < .05) | (np.abs(Y - .72) < .05)), np.broadcast_to(np.asarray(GOLD_DARK, float), colour.shape))
    paint((Y > .98) & (Y < 1.12), gold)
    vine = 1.85 + .32 * np.sin(u * math.pi * 6.5)
    paint((np.abs(Y - vine) < .055) & (Y < 2.4), gold)
    leaf = (np.abs(((u * 6.5) % 1) - .5) / .09 + np.abs(Y - 1.85) / .16) < 1
    paint(leaf & in_skirt, np.broadcast_to(np.asarray(GOLD_LIGHT, float), colour.shape))
    # Side seam piping on the profile, embroidered panel on the front.
    if view == 'S':
        paint((np.abs(u - .08) < .045) & (Y < SHOULDER - 1.2), gold)
    if view == 'F':
        panel = in_skirt & (np.abs(X) < .42 + .95 * t)
        colour[panel] = mix(colour[panel], (226, 236, 249), .75)
        edge = in_skirt & (np.abs(np.abs(X) - (.42 + .95 * t)) < .085) & (Y > 1.12)
        paint(edge, gold)
        for k in range(1, 9):
            yy = HIP - k * 1.8
            tt = (HIP - yy) / HIP
            for sx in (-1, 1):
                stud = np.abs(X - sx * (.42 + .95 * tt)) / .2 + np.abs(Y - yy) / .26 < 1
                paint(stud, np.broadcast_to(np.asarray(GOLD_LIGHT, float), colour.shape))
            chevron = (np.abs(Y - (yy + .5) + np.abs(X) * .55) < .06) & (np.abs(X) < .3 + .5 * tt) & in_skirt
            paint(chevron, gold)
        # Bodice: princess seams, column of studs, centre gem and a V neckline.
        top = Y >= HIP
        seam_x = .5 + .1 * np.clip((Y - 17.4) / 5, 0, 1)
        paint(top & (Y < 22.4) & (np.abs(np.abs(u) - seam_x) < .045), gold)
        for yy in (17.9, 18.8, 19.7, 20.6, 21.5):
            paint(top & (np.abs(X) / .17 + np.abs(Y - yy) / .2 < 1), np.broadcast_to(np.asarray(GOLD_LIGHT, float), colour.shape))
        gem = np.abs(X) / .45 + np.abs(Y - 22.45) / .6
        paint(gem < 1.18, gold)
        gem_colour = mix(GEM_DEEP, GEM, smooth(22.0, 22.9, Y))
        paint(gem < 1, gem_colour)
        paint((np.abs(X + .12) / .1 + np.abs(Y - 22.62) / .12) < 1, np.broadcast_to(np.asarray((240, 255, 255), float), colour.shape))
        v_edge = (Y - 23.05) * .62
        neck = (Y > 23.05) & (np.abs(X) < v_edge)
        paint(neck, mix(SKIN, SKIN_SHADE, .35 + .3 * smooth(24.8, 23.3, Y)))
        paint((Y > 23.05) & (np.abs(np.abs(X) - v_edge - .07) < .08), gold)
    if view == 'B':
        paint((Y >= HIP) & (np.abs(X) < .04), gold)
    rgba = np.concatenate([colour, np.full(colour.shape[:2] + (1,), 255.)], axis=-1)
    image = finish(downsample(rgba, ss))
    if view == 'F':
        return Sheet(image, lambda p: ((BODY_X[1] - p[0]) * ppu, (BODY_Y[1] - p[1]) * ppu))
    if view == 'B':
        return Sheet(image, lambda p: ((p[0] - BODY_X[0]) * ppu, (BODY_Y[1] - p[1]) * ppu))
    return Sheet(image, lambda p: ((p[2] - BODY_Z[0]) * ppu, (BODY_Y[1] - p[1]) * ppu))


def tile(size, base, shade, lines=None, band=None, seed=0):
    rng = np.random.default_rng(seed)
    w, h = size
    yy, xx = np.mgrid[0:h, 0:w] / np.array([h, w])[:, None, None]
    s = .12 + .08 * np.sin(xx * math.pi * (lines or 1) * 2) + rng.normal(0, .02, (h, w)) + .25 * (np.abs(xx - .5) * 2) ** 3
    colour = mix(base, shade, s)
    if band:
        start, rgb = band
        colour[yy >= start] = mix(rgb, GOLD_DARK, .25 * smooth(start, 1, yy[yy >= start]))
        colour[(yy >= start) & (yy < start + .04)] = GOLD_DARK
    rgba = np.concatenate([colour, np.full((h, w, 1), 255.)], axis=-1)
    return Sheet(finish(rgba))


def gold_tile():
    w = h = 32
    yy, xx = np.mgrid[0:h, 0:w] / 32
    colour = mix(GOLD_DARK, GOLD_LIGHT, .55 + .35 * np.cos((yy - .35) * math.pi * 1.6))
    colour[(np.abs(yy - .5) < .05)] = mix(GOLD_LIGHT, (255, 245, 210), .6)
    return Sheet(finish(np.concatenate([colour, np.full((h, w, 1), 255.)], axis=-1)))


def gem_tile():
    yy, xx = np.mgrid[0:16, 0:16] / 16
    colour = mix(GEM_DEEP, GEM, yy * .8 + .1)
    colour[(xx < .45) & (yy < .4) & (xx > .2) & (yy > .15)] = (236, 255, 255)
    return Sheet(finish(np.concatenate([colour, np.full((16, 16, 1), 255.)], axis=-1)))


def stroke(draw, points, colour, width):
    draw.line([tuple(p) for p in points], fill=colour, width=max(1, int(round(width))), joint='curve')


def paint_face():
    """The visible face below the fringe: large calm eyes, faint blush and a small smile."""
    size, k = 128, 4
    ppu = size / 4.8
    height = round(5.0 * ppu) + 1
    canvas = Image.new('RGBA', (size * k, height * k))
    yy, xx = np.mgrid[0:height * k, 0:size * k] / (size * k)
    base = mix(SKIN, SKIN_SHADE, .08 + .5 * smooth(.62, 1, np.abs(xx - .5) * 2) + .35 * smooth(.47, .3, yy))
    blush = np.exp(-(((np.abs(xx - .5) - .25) / .085) ** 2 + ((yy - .76) / .045) ** 2))
    base = mix(base, (246, 180, 178), blush * .45)
    canvas = Image.fromarray(np.concatenate([base, np.full(base.shape[:2] + (1,), 255.)], axis=-1).astype(np.uint8), 'RGBA')
    draw = ImageDraw.Draw(canvas)

    def P(x, y):  # model units relative to the face (x right on the image, y up from the chin)
        return ((2.4 + x) * ppu * k, (5.0 - y) * ppu * k)

    def ellipse(cx, cy, rx, ry, **kw):
        (x0, y0), (x1, y1) = P(cx - rx, cy + ry), P(cx + rx, cy - ry)
        draw.ellipse((x0, y0, x1, y1), **kw)

    eye_y = 1.72
    for side in (-1, 1):
        cx = side * 1.02
        eye = Image.new('L', canvas.size)
        ImageDraw.Draw(eye).ellipse((*P(cx - .5, eye_y + .38), *P(cx + .5, eye_y - .34)), fill=255)
        layer = Image.new('RGBA', canvas.size)
        ld = ImageDraw.Draw(layer)
        ld.ellipse((*P(cx - .52, eye_y + .4), *P(cx + .52, eye_y - .36)), fill=(253, 253, 255, 255))
        ix = cx + side * -.05
        for r, rgb in ((.33, (34, 86, 132)), (.29, (58, 128, 172)), (.21, (96, 178, 210)), (.13, (40, 96, 140))):
            ld.ellipse((*P(ix - r * .86, eye_y + r * 1.15), *P(ix + r * .86, eye_y - r * 1.15)), fill=(*rgb, 255))
        ld.ellipse((*P(ix - .2, eye_y + .3), *P(ix + .2, eye_y + .05)), fill=(30, 70, 116, 255))
        ld.ellipse((*P(ix - .17, eye_y + .27), *P(ix - .02, eye_y + .1)), fill=(255, 255, 255, 255))
        ld.ellipse((*P(ix + .09, eye_y - .1), *P(ix + .17, eye_y - .18)), fill=(226, 250, 255, 255))
        canvas.paste(layer, (0, 0), Image.fromarray(np.minimum(np.asarray(eye), np.asarray(layer)[..., 3])))
        # Upper lash line with a soft outer flick; thin lower lash.
        lid = [P(cx + side * a, eye_y + .36 - .5 * (a + .08) ** 2) for a in np.linspace(-.58, .62, 18)]
        stroke(draw, lid, (74, 82, 108, 255), .16 * ppu * k)
        stroke(draw, [P(cx + side * .58, eye_y + .24), P(cx + side * .74, eye_y + .1)], (74, 82, 108, 255), .1 * ppu * k)
        stroke(draw, [P(cx + side * a, eye_y - .34 + .2 * a * a) for a in np.linspace(-.3, .45, 8)], (196, 150, 146, 255), .05 * ppu * k)
        stroke(draw, [P(cx + side * a, eye_y + .78 + .1 * (1 - a * a)) for a in np.linspace(-.45, .5, 8)], (192, 196, 210, 255), .07 * ppu * k)
    stroke(draw, [P(.06, 1.2), P(-.04, 1.02)], (220, 176, 164, 255), .06 * ppu * k)
    stroke(draw, [P(x, .62 - .07 * (1 - (x / .28) ** 2)) for x in np.linspace(-.28, .28, 9)], (204, 128, 128, 255), .07 * ppu * k)
    image = finish(downsample(canvas, k))
    return Sheet(image, lambda p: ((2.4 - p[0]) * ppu, (30.9 - p[1]) * ppu))


def hair_strands(colour_shape, U, V, phase=0.):
    """Silver hair: fine strands, cool shadow at depth and a bright halo band."""
    strands = np.sin(U * 17 + np.sin(V * 1.3 + phase) * 1.4) * .5 + np.sin(U * 41 + phase * 3) * .25
    return strands


def paint_hair(kind):
    """Hair projections. 'bangs' and 'cap_side' are alpha-cut; 'back' and 'side' carry the long mane."""
    ppu, ss = 24, 3
    if kind == 'bangs':
        ext_u, ext_v = (-2.85, 2.85), (25.6, 31.5)
    elif kind == 'cap_side':
        ext_u, ext_v = (-2.7, 2.7), (25.6, 31.5)
    elif kind == 'back':
        ext_u, ext_v = (-2.9, 2.9), (16.7, 31.6)
    else:
        ext_u, ext_v = (1.1, 3.25), (16.7, 31.6)
    (U, Y), _ = grid(ext_u, ext_v, ppu, ss)
    X = ext_u[1] - U if kind == 'bangs' else ext_u[0] + U
    alpha = np.ones_like(X)
    if kind == 'bangs':
        # Fringe: tapered locks with a slightly off-centre parting.
        locks = [(-2.35, .9, 25.9), (-1.75, .8, 28.05), (-1.15, .8, 28.45), (-.55, .75, 28.2), (-.02, .55, 28.75),
                 (.42, .75, 28.3), (1.0, .8, 28.5), (1.6, .8, 28.1), (2.3, .9, 25.9)]
        tip = np.full_like(X, 29.35)
        for cx, width, low in locks:
            dist = np.abs(X - cx) / (width / 2)
            tip = np.minimum(tip, np.where(dist < 1, low + (29.35 - low) * dist ** 1.6, 99))
        alpha = (Y > tip).astype(float)
        depth = smooth(29.4, tip, Y)
        lock_u = X
    elif kind == 'cap_side':
        low = 25.75 + .35 * np.abs(np.sin(X * 3.1)) + 1.6 * smooth(-.6, -2.4, X)
        alpha = (Y > low).astype(float)
        depth = smooth(low + 1.2, low, Y)
        lock_u = X
    else:
        # The mane ends in several long points, longest at the centre of the back.
        centre = X if kind == 'back' else (X - 2.2) * 1.3
        low = 17.0 + 1.5 * np.abs(centre) / 2.9 + .55 * np.abs(np.sin(centre * 3.4))
        alpha = (Y > low).astype(float)
        depth = smooth(low + 2.2, low, Y) * .7
        lock_u = X
    strands = hair_strands(X.shape, lock_u, Y)
    shade = .1 + .2 * strands + .38 * depth
    colour = mix(HAIR, HAIR_SHADE, shade)
    colour = mix(colour, HAIR_DEEP, smooth(.75, 1, strands) * .45)
    ring = np.exp(-((Y - 30.35 - .1 * np.sin(X * 2)) / .16) ** 2) * (.55 + .45 * np.sin(X * 9))
    colour = mix(colour, (255, 255, 255), np.clip(ring, 0, 1) * .8)
    rgba = np.concatenate([colour, alpha[..., None] * 255], axis=-1)
    image = finish(downsample(rgba, ss))
    if kind == 'bangs':
        return Sheet(image, lambda p: ((ext_u[1] - p[0]) * ppu, (ext_v[1] - p[1]) * ppu))
    if kind == 'back':
        return Sheet(image, lambda p: ((p[0] - ext_u[0]) * ppu, (ext_v[1] - p[1]) * ppu))
    return Sheet(image, lambda p: ((p[2] - ext_u[0]) * ppu, (ext_v[1] - p[1]) * ppu))


def paint_lock():
    """A side lock: strands with a tapering point, mapped whole onto each face."""
    w, h, ss = 20, 128, 3
    yy, xx = np.mgrid[0:h * ss, 0:w * ss] / np.array([h * ss, w * ss])[:, None, None]
    width = np.where(yy < .72, 1, np.clip((1 - yy) / .28, 0, 1) ** .8)
    alpha = (np.abs(xx - .5) * 2 < width).astype(float)
    strands = np.sin(xx * 19 + yy * 3) * .6
    colour = mix(HAIR, HAIR_SHADE, .15 + .15 * strands + .3 * (np.abs(xx - .5) * 2) ** 2)
    colour = mix(colour, (255, 255, 255), np.exp(-((yy - .12) / .03) ** 2) * .7)
    rgba = np.concatenate([colour, alpha[..., None] * 255], axis=-1)
    return Sheet(finish(downsample(rgba, ss)))


def bezier(points, t):
    n = len(points) - 1
    return sum(math.comb(n, i) * (1 - t) ** (n - i) * t ** i * np.asarray(p, float) for i, p in enumerate(points))


BONE = [(.3, 29.0), (2.0, 36.4), (6.5, 39.0), (12.0, 34.2)]


def feather(draw, P, root, angle, length, width, bend=.06, base=FEATHER, tip_tint=None, k=4):
    """One vane: quill-thin root, widest near a third, narrow outer vane and a soft point."""
    theta = math.radians(angle)
    d = np.array([math.sin(theta), -math.cos(theta)])
    n = np.array([math.cos(theta), math.sin(theta)])
    ts = np.linspace(0, 1, 34)
    centre = [np.asarray(root) + d * length * t + n * bend * length * t * (1 - t) for t in ts]

    def half(t):
        grow = min(1, t / .16) ** .55
        taper = 1 if t < .5 else max(0, (1 - t) / .5) ** .8
        return width / 2 * grow * taper
    outer = [c + n * half(t) * .78 for c, t in zip(centre, ts)]
    inner = [c - n * half(t) for c, t in zip(centre, ts)]
    for i in range(len(ts) - 1):
        t = ts[i]
        colour = mix(FEATHER_SHADE, base, smooth(0, .32, t))
        if tip_tint is not None:
            colour = mix(colour, tip_tint, smooth(.8, 1, t) * .7)
        for side, edge, darken in ((1, outer, .22), (-1, inner, 0)):
            c = mix(colour, FEATHER_SHADE, darken)
            quad = [P(*centre[i]), P(*edge[i]), P(*edge[i + 1]), P(*centre[i + 1])]
            draw.polygon(quad, fill=(*[int(v) for v in c], 255))
    # Barbs angle towards the tip; the outline separates overlapping vanes.
    for t in np.linspace(.22, .86, 8):
        c = centre[int(t * 33)]
        for edge, f in ((outer, .78), (inner, 1)):
            e = edge[min(33, int(t * 33) + 3)]
            draw.line([P(*c), P(*(c + (e - c) * .9))], fill=(226, 232, 242, 255), width=k)
    outline = [P(*p) for p in outer] + [P(*p) for p in reversed(inner)]
    draw.line(outline + [outline[0]], fill=(*FEATHER_LINE, 255), width=int(1.3 * k), joint='curve')
    draw.line([P(*c) for c in centre[:31]], fill=(236, 240, 246, 255), width=int(1.1 * k))
    draw.line([P(*c) for c in centre[:28]], fill=(198, 208, 224, 255), width=max(1, k // 2))


def paint_wing(layer):
    """Front view of the +x wing (image left = outer tip); -x reuses it mirrored by UV."""
    low, high, _ = WING_LAYERS[layer]
    ppu, k = 16, 4
    W, H = round(WING_WIDTH * ppu), round((high - low) * ppu)
    canvas = Image.new('RGBA', (W * k, H * k), (*FEATHER, 0))
    draw = ImageDraw.Draw(canvas)

    def P(o, y):
        return ((WING_WIDTH - o) * ppu * k, (high - y) * ppu * k)
    bone = lambda s: bezier(BONE, s)
    if layer == 'wing_a':
        # Primaries fan out from the hand into a pointed tip; secondaries hang from the arm.
        for i in range(10):
            f = i / 9
            feather(draw, P, bone(.52 + .48 * f) - [0, .5], 12 + 30 * f, 19 - 6 * f ** 1.3, 2.4 - .3 * f,
                    bend=.05, tip_tint=(238, 224, 194), k=k)
        for i in reversed(range(9)):
            f = i / 8
            feather(draw, P, bone(.04 + .46 * f) - [0, .4], 2 + 7 * f, 13 + 4 * f, 2.5, bend=.03, k=k)
    elif layer == 'wing_b':
        for i in reversed(range(13)):
            f = i / 12
            feather(draw, P, bone(.02 + .96 * f) - [0, .15], 3 + 30 * f ** 1.5, 6.8 - 1.2 * f, 2.2, bend=.04, k=k)
    else:
        for row, (offset, length, width) in enumerate(((.2, 3.6, 1.7), (.6, 2.4, 1.35), (.9, 1.6, 1.1))):
            count = 15 - row * 2
            for i in reversed(range(count)):
                f = i / (count - 1)
                feather(draw, P, bone(.02 + .96 * f) + [0, offset], 4 + 28 * f ** 1.5, length, width, bend=0, k=k)
    image = finish(downsample(canvas, k))
    return Sheet(image, lambda p: ((WING_WIDTH - (abs(p[0]) - WING_ROOT)) * ppu, (high - p[1]) * ppu))


def paint_halo():
    size, k = 256, 4
    yy, xx = (np.mgrid[0:size * k, 0:size * k] + .5) / (size * k) * 2 - 1
    r = np.hypot(xx, yy)
    ring = (r > .84) & (r < .95)
    inner = (r > .74) & (r < .765)
    beads = np.zeros_like(r, dtype=bool)
    for i in range(12):
        a = i * math.pi / 6
        beads |= (np.abs(xx - math.cos(a) * .8) + np.abs(yy - math.sin(a) * .8)) < .045
    alpha = (ring | inner | beads).astype(float)
    light = .5 + .5 * np.cos((np.arctan2(yy, xx) + 2.2))
    colour = mix(GOLD_DARK, GOLD_LIGHT, .35 + .55 * light)
    colour = mix(colour, (255, 248, 222), smooth(.025, 0, np.abs(r - .895)) * .8 * ring)
    rgba = np.concatenate([colour, alpha[..., None] * 255], axis=-1)
    return Sheet(finish(downsample(rgba, k)))


def sheets():
    s = {name: paint_body(name[-1]) for name in ('body_F', 'body_B', 'body_S')}
    s.update({'hair_' + k: paint_hair(k) for k in ('bangs', 'cap_side', 'back', 'side')})
    s.update({k: paint_wing(k) for k in WING_LAYERS})
    s['halo'] = paint_halo()
    s['face'] = paint_face()
    s['lock'] = paint_lock()
    s['cloth'] = tile((32, 32), IVORY, SHADOW, lines=1, seed=1)
    s['sleeve'] = tile((32, 48), IVORY, SHADOW, lines=2, seed=2)
    s['drape'] = tile((32, 48), IVORY, SHADOW, lines=2, band=(.86, GOLD), seed=3)
    s['skin'] = tile((16, 16), SKIN, SKIN_SHADE, seed=4)
    s['hair'] = tile((32, 32), HAIR, HAIR_SHADE, lines=5, seed=5)
    s['slipper'] = tile((16, 16), IVORY, SHADOW, band=(.6, GOLD), seed=6)
    s['gold'] = gold_tile()
    s['gem'] = gem_tile()
    return s


def pack(sheet_map):
    """Skyline packing with 2px edge-replicated gutters into the 1024px atlas."""
    atlas = np.zeros((ATLAS_SIZE, ATLAS_SIZE, 4), np.uint8)
    skyline = np.zeros(ATLAS_SIZE, int)
    for name, sheet in sorted(sheet_map.items(), key=lambda kv: (-kv[1].image.size[1], kv[0])):
        w, h = sheet.image.size
        gw, gh = w + 4, h + 4
        best = None
        for x in range(0, ATLAS_SIZE - gw + 1, 2):
            y = skyline[x:x + gw].max()
            if y + gh <= ATLAS_SIZE and (best is None or y < best[1]):
                best = (x, y)
        if best is None:
            raise RuntimeError('Seraph atlas overflow at ' + name)
        x, y = best
        data = np.pad(np.asarray(sheet.image), ((2, 2), (2, 2), (0, 0)), mode='edge')
        atlas[y:y + gh, x:x + gw] = data
        skyline[x:x + gw] = y + gh
        sheet.origin = (x + 2, y + 2)
    image = Image.fromarray(atlas, 'RGBA')
    data = io.BytesIO()
    image.save(data, format='PNG', optimize=True)
    return data.getvalue()


# --------------------------------------------------------------------------- mesh

def model(casting, sheet_map):
    S = sheet_map
    parts = []

    def box(lo, hi, faces, rotation=None):
        lo = [round(v, 4) for v in lo]
        hi = [round(v, 4) for v in hi]
        parts.append({'from': lo, 'to': hi, 'faces': faces, 'rotation': rotation})

    body = {'north': S['body_F'], 'south': S['body_B'], 'west': S['body_S'], 'east': S['body_S'],
            'up': S['cloth'], 'down': S['cloth']}
    every = lambda sheet: {f: sheet for f in FACES}
    # Gown: 18 slim tiers flare gently from hip to hem, sampling one continuous painting.
    tiers = 18
    for i in range(tiers):
        top, bottom = HIP - HIP * i / tiers, HIP - HIP * (i + 1) / tiers
        w, d, zc = skirt((i + .5) / tiers)
        box((-w / 2, max(0, bottom), zc - d / 2), (w / 2, top, zc + d / 2), body)
    knots = [k[0] for k in BODICE]
    for a, b in zip(knots, knots[1:]):
        w, d = (bodice(a)[0] + bodice(b)[0]) / 2, (bodice(a)[1] + bodice(b)[1]) / 2
        box((-w / 2, a, -d / 2 - .04), (w / 2, b, d / 2 - .04), body)
    box((-2.1, 16.3, -1.475), (2.1, 17.05, 1.475), every(S['gold']))
    box((-.3, 16.38, -1.62), (.3, 16.98, -1.47), every(S['gem']), {'axis': 'z', 'angle': 45, 'origin': [0, 16.68, -1.545]})

    # Neck, head, fringe cap, circlet and the long silver mane.
    box((-.7, 24.4, -.75), (.7, 26.2, .6), every(S['skin']))
    head = every(S['skin'])
    head['north'] = S['face']
    box((-2.25, 25.9, -2.3), (2.25, 30.9, 2.2), head)
    box((-2.6, 25.7, -2.5), (2.6, 31.35, 2.55), {'north': S['hair_bangs'], 'south': S['hair_back'],
        'west': S['hair_cap_side'], 'east': S['hair_cap_side'], 'up': S['hair']})
    box((-2.25, 31.35, -2.15), (2.25, 31.78, 2.2), {f: S['hair'] for f in ('north', 'south', 'east', 'west', 'up')})
    box((-2.68, 28.78, -2.58), (2.68, 29.12, 2.63), every(S['gold']))
    box((-.3, 28.62, -2.74), (.3, 29.28, -2.58), every(S['gem']), {'axis': 'z', 'angle': 45, 'origin': [0, 28.95, -2.66]})
    mane = {'north': S['hair'], 'south': S['hair_back'], 'west': S['hair_side'], 'east': S['hair_side'], 'up': S['hair']}
    box((-2.45, 24.2, 2.55), (2.45, 30.6, 3.05), mane)
    box((-2.35, 16.9, 1.3), (2.35, 26.0, 3.05), mane)
    box((-1.7, 24.5, .45), (1.7, 26.0, 1.3), {f: S['hair'] for f in ('south', 'east', 'west', 'up', 'down')})
    for side in (-1, 1):
        x0, x1 = sorted((side * 2.0, side * 2.68))
        box((x0, 21.2, -2.75), (x1, 29.8, -1.8), {f: S['lock'] for f in ('north', 'south', 'east', 'west')})

    # Arms: hands rest together at the waist; the cast pose raises both in blessing.
    sleeve, gold, skin = every(S['sleeve']), every(S['gold']), every(S['skin'])
    for side in (-1, 1):
        cx = side * 3.15
        box((cx - .75, 22.6, -.8), (cx + .75, 24.9, .8), sleeve)
        if casting:
            spin = {'axis': 'z', 'angle': -side * 45, 'origin': [cx, 23.9, 0]}
            box((cx - .58, 23.6, -.6), (cx + .58, 30.6, .6), sleeve, spin)
            box((cx - .95, 27.4, -.95), (cx + .95, 30.2, .95), every(S['drape']), spin)
            box((cx - .72, 30.1, -.72), (cx + .72, 30.5, .72), gold, spin)
            box((cx - .45, 30.4, -.32), (cx + .45, 31.7, .32), skin, spin)
        else:
            box((cx - .6, 19.9, -.62), (cx + .6, 24.6, .62), sleeve)
            turn = {'axis': 'y', 'angle': side * 45, 'origin': [cx, 19.55, 0]}
            box((cx - .55, 19.0, -3.3), (cx + .55, 20.1, .25), sleeve, turn)
            box((cx - .62, 18.93, -3.38), (cx + .62, 20.17, -3.0), gold, turn)
            box((cx - .38, 19.15, -3.85), (cx + .38, 19.95, -3.3), skin, turn)
            box((cx - .52, 15.9, -2.75), (cx + .52, 19.2, .15), every(S['drape']), turn)
    if not casting:
        box((-.8, 18.95, -3.15), (.8, 20.05, -2.3), skin)

    # Three painted feather layers per wing, joined behind the shoulder blades.
    for layer, (low, high, z) in WING_LAYERS.items():
        for side in (-1, 1):
            x0, x1 = sorted((side * WING_ROOT, side * (WING_ROOT + WING_WIDTH)))
            pivot = [side * 1.2, 30.5, z]
            rotation = {'axis': 'z', 'angle': side * 22.5, 'origin': pivot} if casting else \
                {'axis': 'y', 'angle': -side * 22.5, 'origin': pivot}
            box((x0, low, z), (x1, high, z), {'north': S[layer], 'south': S[layer]}, rotation)
    box((-3.7, 33.5, -3.3), (3.7, 33.5, 4.1), {'up': S['halo'], 'down': S['halo']},
        {'axis': 'x', 'angle': 22.5, 'origin': [0, 33.5, .4]})
    return parts


def mesh_point(point):
    return [round(8 + point[0], 5), round(8 + point[1] - CENTER_Y, 5), round(8 + point[2], 5)]


def bedrock_point(point):
    return [round(-point[0] * SCALE, 5), round((point[1] - CENTER_Y) * SCALE + 24, 5), round(point[2] * SCALE, 5)]


def bedrock_rotation(rotation):
    """Blockbench's Bedrock exporter mirrors X: X and Y angles flip sign, Z is retained."""
    angle = rotation['angle']
    return {'x': [-angle, 0, 0], 'y': [0, -angle, 0], 'z': [0, 0, angle]}[rotation['axis']]


def assets():
    java, bedrock = {}, {}
    sheet_map = sheets()
    texture = pack(sheet_map)
    java['assets/voidscape/textures/item/judge_seraph.png'] = texture
    bedrock['textures/items/judge_seraph.png'] = texture
    names = []
    for pose in ('idle', 'cast'):
        name = PREFIX + pose
        names.append(name)
        elements, cubes = [], []
        for part in model(pose == 'cast', sheet_map):
            lo, hi = part['from'], part['to']
            size = [hi[i] - lo[i] for i in range(3)]
            faces, uv = {}, {}
            for face, sheet in part['faces'].items():
                u0, v0, u1, v1 = sheet.uv(face, lo, hi)
                faces[face] = {'texture': '#skin', 'uv': [round(u0 / 64, 6), round(v0 / 64, 6), round(u1 / 64, 6), round(v1 / 64, 6)]}
                start, span = [u0, v0], [u1 - u0, v1 - v0]
                if face in ('up', 'down'):
                    start, span = [start[i] + span[i] for i in range(2)], [-v for v in span]
                uv[face] = {'uv': [round(v, 4) for v in start], 'uv_size': [round(v, 4) for v in span]}
            # Painted shading carries the form; flat lighting keeps the robe and feathers luminous.
            element = {'from': mesh_point(lo), 'to': mesh_point(hi), 'shade': False, 'faces': faces}
            cube = {'origin': bedrock_point([hi[0], lo[1], lo[2]]), 'size': [round(v * SCALE, 5) for v in size], 'uv': uv}
            if part['rotation']:
                rotation = part['rotation']
                element['rotation'] = {'angle': rotation['angle'], 'axis': rotation['axis'],
                                       'origin': mesh_point(rotation['origin']), 'rescale': False}
                cube['pivot'] = bedrock_point(rotation['origin'])
                cube['rotation'] = bedrock_rotation(rotation)
            elements.append(element)
            cubes.append(cube)
        java[f'assets/voidscape/models/item/{name}.json'] = json.dumps({
            'textures': {'skin': 'voidscape:item/judge_seraph'}, 'elements': elements,
            'display': {'fixed': {'scale': [1, 1, 1]}}}, indent=2).encode()
        java[f'assets/voidscape/items/{name}.json'] = json.dumps({'model': {'type': 'minecraft:model', 'model': 'voidscape:item/' + name}}, indent=2).encode()
        bedrock[f'models/entity/{name}.geo.json'] = json.dumps({'format_version': '1.16.0', 'minecraft:geometry': [{
            'description': {'identifier': 'geometry.voidscape.' + name, 'texture_width': ATLAS_SIZE, 'texture_height': ATLAS_SIZE,
                            'visible_bounds_width': 8, 'visible_bounds_height': 7, 'visible_bounds_offset': [0, 1.8, 0]},
            'bones': [{'name': 'head', 'pivot': [0, 24, 0], 'cubes': cubes}]}]}, indent=2).encode()
        bedrock[f'attachables/{name}.json'] = json.dumps({'format_version': '1.10.0', 'minecraft:attachable': {'description': {
            'identifier': 'voidscape:' + name, 'materials': {'default': 'armor'},
            'textures': {'default': 'textures/items/judge_seraph'},
            'geometry': {'default': 'geometry.voidscape.' + name},
            'render_controllers': ['controller.render.evergarden_judge_glow']}}}, indent=2).encode()
    return java, bedrock, names
