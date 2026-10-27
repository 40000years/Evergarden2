"""Sculpt, paint and rig the Evergarden main bosses as editable ModelEngine R4 blueprints.

Geometry is authored in Blockbench units (1/16 block before the runtime scale) with the
native front facing -Z, the convention shared by Arkhaia and the Six Rivers rigs.
Every cuboid owns painted texels: faces are packed into one atlas at a constant texel
density, so bevels, engraving, runes and feather/membrane/flame silhouettes stay crisp.
Thin sheets carry alpha-cut silhouettes on their two broad faces only.

Animation poses are geometry-space Euler degrees (R = Rz @ Ry @ Rx). Export applies the
R4 conventions: rotation X/Y negated, translation X negated (meta flag 'inverted').
"""
import base64
import hashlib
import io
import json
import math
import random
import uuid

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

FACES = ('north', 'south', 'east', 'west', 'up', 'down')
# Texture axes of each face, as (u axis, v axis) indices into the cuboid size.
FACE_AXES = {'north': (0, 1), 'south': (0, 1), 'east': (2, 1), 'west': (2, 1), 'up': (0, 2), 'down': (0, 2)}
BAYER = np.array([[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]) / 16 - .5


def rot(angles):
    """Blockbench/ME cuboid and bone order, degrees: R = Rz @ Ry @ Rx."""
    x, y, z = np.radians(angles)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    return (np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]) @ np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
            @ np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]]))


def euler(r):
    """Inverse of rot(); returns degrees."""
    y = math.asin(float(np.clip(-r[2, 0], -1, 1)))
    if abs(math.cos(y)) > 1e-7:
        x, z = math.atan2(r[2, 1], r[2, 2]), math.atan2(r[1, 0], r[0, 0])
    else:
        x, z = math.atan2(-r[1, 2], r[1, 1]), 0.0
    return [math.degrees(x), math.degrees(y), math.degrees(z)]


def unit(v):
    v = np.asarray(v, float)
    return v / np.linalg.norm(v)


def basis(y_axis, z_hint):
    """Rotation whose local +Y follows y_axis and local +Z leans toward z_hint."""
    y = unit(y_axis)
    z = np.asarray(z_hint, float) - y * (np.asarray(z_hint, float) @ y)
    if np.linalg.norm(z) < 1e-6:
        z = np.array([0, 0, 1.]) - y * y[2]
        if np.linalg.norm(z) < 1e-6:
            z = np.array([1., 0, 0]) - y * y[0]
    z = unit(z)
    return np.column_stack((np.cross(y, z), y, z))


def axis_angle(axis, degrees):
    a = unit(axis); t = math.radians(degrees)
    k = np.array([[0, -a[2], a[1]], [a[2], 0, -a[0]], [-a[1], a[0], 0]])
    return np.eye(3) + math.sin(t) * k + (1 - math.cos(t)) * (k @ k)


def bezier(points, t):
    pts = [np.asarray(p, float) for p in points]
    while len(pts) > 1:
        pts = [a * (1 - t) + b * t for a, b in zip(pts, pts[1:])]
    return pts[0]


def hex_rgb(value):
    value = value.lstrip('#')
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


class Material:
    """A palette ramp (dark to light) plus the painter used for its faces."""

    def __init__(self, ramp, style='plate', glow=False, density=1.0, trim=None, accent=None, **options):
        self.ramp = np.array([hex_rgb(c) for c in ramp], float)
        self.style, self.glow, self.density = style, glow, density
        self.trim = np.array([hex_rgb(c) for c in trim], float) if trim else None
        self.accent = np.array([hex_rgb(c) for c in accent], float) if accent else None
        self.options = options


class Forge:
    """Collects bones, oriented cuboids and alpha-cut sheets for one model."""

    def __init__(self, name, materials, seed=0):
        self.name, self.materials, self.seed = name, materials, seed
        self.bones = {}
        self.parts = []
        self.stack = [(np.zeros(3), np.eye(3))]

    # Bones ---------------------------------------------------------------
    def bone(self, name, parent, origin):
        assert name not in self.bones and (parent is None or parent in self.bones), name
        self.bones[name] = (parent, [float(v) for v in origin])

    def at(self, name):
        return np.array(self.bones[name][1])

    # Local assembly frames ----------------------------------------------
    def push(self, origin, r=np.eye(3)):
        o, base = self.stack[-1]
        self.stack.append((o + base @ np.asarray(origin, float), base @ r))

    def pop(self):
        self.stack.pop()

    def _world(self, center, r):
        o, base = self.stack[-1]
        return o + base @ np.asarray(center, float), base @ r

    # Primitives ------------------------------------------------------------
    def cube(self, bone, center, size, mat, r=np.eye(3), label=None, faces=FACES, shape=None, **paint):
        assert bone in self.bones, bone
        assert mat in self.materials, mat
        size = np.maximum(np.asarray(size, float), .02)
        c, rr = self._world(center, np.asarray(r, float))
        # Mirrored twins (same label, material and size) paint identically and share texels.
        twin = f"{(label or bone).rstrip('_rl')}/{mat}/{np.round(size, 2).tolist()}/{shape is not None}"
        seed = paint.pop('seed', int(hashlib.sha1(twin.encode()).hexdigest()[:8], 16) + self.seed)
        self.parts.append(dict(bone=bone, center=c, size=size, r=rr, mat=mat, label=label or bone,
                               faces=tuple(faces), shape=shape, paint=paint, seed=seed))
        return self.parts[-1]

    def box(self, bone, lo, size, mat, rot_deg=(0, 0, 0), **kw):
        """Blockbench style from/size, rotated about its own center."""
        size = np.asarray(size, float)
        return self.cube(bone, np.asarray(lo, float) + size / 2, size, mat, rot(rot_deg), **kw)

    def beam(self, bone, a, b, width, depth=None, mat='plate', z_hint=(0, 0, 1), **kw):
        """Cuboid whose local +Y runs from a to b."""
        a, b = np.asarray(a, float), np.asarray(b, float)
        length = float(np.linalg.norm(b - a))
        return self.cube(bone, (a + b) / 2, [width, length, depth if depth is not None else width], mat,
                         basis(b - a, z_hint), **kw)

    def taper(self, bone, points, widths, depths=None, mat='plate', z_hint=(0, 0, 1), overlap=.15, **kw):
        """Chain of beams with stepped widths: horns, spikes, fingers and claws."""
        depths = depths or widths
        made = []
        for i, (a, b) in enumerate(zip(points, points[1:])):
            a, b = np.asarray(a, float), np.asarray(b, float)
            d = unit(b - a)
            made.append(self.beam(bone, a - d * overlap * (i > 0), b + d * overlap * .5, widths[i], depths[i], mat, z_hint, **kw))
        return made

    def spike(self, bone, base, tip, width, mat='plate', steps=3, z_hint=(0, 0, 1), depth_ratio=1.0, **kw):
        base, tip = np.asarray(base, float), np.asarray(tip, float)
        points = [base + (tip - base) * (i / steps) for i in range(steps + 1)]
        widths = [width * (1 - i / steps * .78) for i in range(steps)]
        return self.taper(bone, points, widths, [w * depth_ratio for w in widths], mat, z_hint, **kw)

    def sheet(self, bone, outline, mat, thickness=.14, up=None, edge=None, label=None, facing=None, **paint):
        """Planar alpha-cut sheet bounded by a 3D outline (points roughly coplanar).

        The sheet's texture space is s (local +X) and t (local +Y); `up` orients +Y.
        `facing` picks which side is the south (outer) face, e.g. a cape's back.
        Only the two broad faces are textured; side strips stay untextured.
        """
        o, base = self.stack[-1]
        pts = np.array([o + base @ np.asarray(p, float) for p in outline])
        center = pts.mean(axis=0)
        _, _, vt = np.linalg.svd(pts - center)
        normal = unit(vt[2])
        if facing is not None and normal @ (base @ np.asarray(facing, float)) < 0:
            normal = -normal
        up_axis = np.asarray(up if up is not None else pts[0] - center, float)
        up_axis = base @ up_axis if up is not None else up_axis
        y = up_axis - normal * (up_axis @ normal)
        if np.linalg.norm(y) < 1e-6:
            y = np.cross(normal, [1, 0, 0])
        y = unit(y)
        x = np.cross(y, normal)
        r = np.column_stack((x, y, normal))
        local = (pts - center) @ r
        lo, hi = local[:, :2].min(axis=0), local[:, :2].max(axis=0)
        size = np.array([hi[0] - lo[0], hi[1] - lo[1], thickness])
        mid = (lo + hi) / 2
        world_center = center + x * mid[0] + y * mid[1]
        poly = [((p[0] - lo[0]) / max(size[0], 1e-6), (p[1] - lo[1]) / max(size[1], 1e-6)) for p in local]
        self.stack.append((np.zeros(3), np.eye(3)))
        part = self.cube(bone, world_center, size, mat, r, label=label, faces=('north', 'south'),
                         shape=dict(polygon=poly, edge=edge or {}), **paint)
        self.stack.pop()
        return part

    def ring(self, bone, center, radius, width, depth, mat, segments=16, normal=(0, 0, 1), phase=0, **kw):
        """Polygonal ring of beams in the plane perpendicular to `normal`."""
        n = unit(normal)
        a = unit(np.cross(n, [0, 1, 0]) if abs(n[1]) < .9 else np.cross(n, [1, 0, 0]))
        b = np.cross(n, a)
        c = np.asarray(center, float)
        pts = [c + (a * math.cos(phase + math.tau * i / segments) + b * math.sin(phase + math.tau * i / segments)) * radius
               for i in range(segments + 1)]
        for p, q in zip(pts, pts[1:]):
            self.beam(bone, p, q, width, depth, mat, z_hint=n, **kw)


# ---------------------------------------------------------------------------
# Painting


def _tone_to_rgb(tone, ramp, dither=True):
    n = len(ramp)
    f = np.clip(tone, 0, 1) * (n - 1)
    if dither:
        h, w = tone.shape
        f = f + np.tile(BAYER, (h // 4 + 1, w // 4 + 1))[:h, :w] * .4
    idx = np.clip(np.floor(f + .5), 0, n - 1).astype(int)
    return ramp[idx]


def _mask(shape, w, h, supersample=4):
    """Rasterize the alpha polygon in sheet (s, t) space; t=0 is the bottom edge."""
    big = Image.new('L', (w * supersample, h * supersample), 0)
    draw = ImageDraw.Draw(big)
    poly = shape['polygon']
    edge = shape.get('edge', {})
    area = sum(a[0] * b[1] - b[0] * a[1] for a, b in zip(poly, poly[1:] + poly[:1]))
    inward = 1 if area > 0 else -1
    pts = []
    for i, (s, t) in enumerate(poly):
        nxt = poly[(i + 1) % len(poly)]
        pts.append((s, t))
        style = edge.get(i)
        if style:
            kind, amount, count = style
            for k in range(1, count * 8):
                u = k / (count * 8)
                ps = s + (nxt[0] - s) * u
                pt = t + (nxt[1] - t) * u
                dx, dy = nxt[0] - s, nxt[1] - t
                length = math.hypot(dx, dy) or 1
                # Unit normal pointing into the outline, whatever its winding.
                nx, ny = -dy / length * inward, dx / length * inward
                if kind == 'scallop':
                    bulge = amount * abs(math.sin(math.pi * u * count))
                elif kind == 'tatter':
                    rnd = random.Random(int(ps * 997 + pt * 131) + k)
                    bulge = amount * (abs(math.sin(math.pi * u * count)) ** .6) * (.35 + .65 * rnd.random())
                elif kind == 'wave':
                    bulge = amount * math.sin(math.tau * u * count)
                else:
                    bulge = 0
                pts.append((ps + nx * bulge, pt + ny * bulge))
    draw.polygon([(s * w * supersample, (1 - t) * h * supersample) for s, t in pts], fill=255)
    small = big.resize((w, h), Image.Resampling.BOX)
    return (np.asarray(small, float) / 255) >= .5


def _erode(mask, steps):
    image = Image.fromarray((mask * 255).astype('uint8'))
    for _ in range(steps):
        image = image.filter(ImageFilter.MinFilter(3))
    return np.asarray(image) > 127


def _noise(w, h, seed, scale=4.0):
    rng = np.random.default_rng(seed)
    gw, gh = int(w / scale) + 2, int(h / scale) + 2
    grid = rng.random((gh, gw))
    ys, xs = np.mgrid[0:h, 0:w] / scale
    x0, y0 = xs.astype(int), ys.astype(int)
    fx, fy = xs - x0, ys - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a = grid[y0, x0] * (1 - fx) + grid[y0, x0 + 1] * fx
    b = grid[y0 + 1, x0] * (1 - fx) + grid[y0 + 1, x0 + 1] * fx
    return a * (1 - fy) + b * fy


class Painter:
    """Paints one face. All painters return (h, w, 4) uint8."""

    def __init__(self, forge):
        self.forge = forge

    def face(self, part, face, w, h):
        mat = self.forge.materials[part['mat']]
        rng = random.Random(part['seed'] * 31 + FACES.index(face))
        ys, xs = np.mgrid[0:h, 0:w]
        fx = (xs + .5) / w
        fy = (ys + .5) / h
        # Sheet space: s runs along local +X, t along local +Y (t=0 bottom).
        if face == 'south':
            s, t = fx, 1 - fy
        elif face == 'north':
            s, t = 1 - fx, 1 - fy
        else:
            s, t = fx, 1 - fy
        alpha = np.ones((h, w), bool)
        if part['shape'] is not None and face in ('north', 'south'):
            alpha = _mask(part['shape'], w, h)
            if face == 'north':
                alpha = alpha[:, ::-1]
        ctx = dict(part=part, face=face, w=w, h=h, fx=fx, fy=fy, s=s, t=t, alpha=alpha, rng=rng, mat=mat,
                   paint=part['paint'], side=face in ('north', 'south', 'east', 'west'))
        rgb, extra_alpha = getattr(self, 'style_' + mat.style)(ctx)
        a = alpha & extra_alpha if extra_alpha is not None else alpha
        out = np.zeros((h, w, 4), np.uint8)
        out[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)
        out[..., 3] = np.where(a, 255, 0)
        return out

    # -- helpers -------------------------------------------------------------
    @staticmethod
    def _bevel(ctx, tone, light=.3, dark=.32, width=1):
        h, w = ctx['h'], ctx['w']
        if w < 3 or h < 3:
            return tone
        tone = tone.copy()
        if ctx['part']['shape'] is not None and ctx['face'] in ('north', 'south'):
            inner = _erode(ctx['alpha'], width)
            rim = ctx['alpha'] & ~inner
            # Upper rim catches light, lower rim falls into shadow.
            tone[rim & (ctx['fy'] < .5)] += light
            tone[rim & (ctx['fy'] >= .5)] -= dark * .7
            return tone
        # Crisp 1px highlight on the lit edges, a soft second step on large faces.
        tone[:width, :] += light
        tone[:, :width] += light * .5
        tone[-width:, :] -= dark
        tone[:, -width:] -= dark * .55
        if min(w, h) >= 14:
            tone[width:width * 2, width:-width] += light * .35
            tone[-width * 2:-width, width:-width] -= dark * .3
        return tone

    @staticmethod
    def _vertical(ctx, base, span):
        if ctx['face'] == 'up':
            return np.full((ctx['h'], ctx['w']), base + span * .55)
        if ctx['face'] == 'down':
            return np.full((ctx['h'], ctx['w']), base - span * .55)
        return base + span * (.5 - ctx['fy'])

    def _engrave(self, ctx, tone, mat):
        """Carved filigree: a dark groove with a lit lower lip."""
        w, h = ctx['w'], ctx['h']
        if w < 18 or h < 18 or not ctx['side'] or ctx['paint'].get('plain'):
            return tone, None
        groove = Image.new('L', (w, h), 0)
        lip = Image.new('L', (w, h), 0)
        dg, dl = ImageDraw.Draw(groove), ImageDraw.Draw(lip)
        rng = ctx['rng']
        inset = 3 if min(w, h) > 24 else 2
        if ctx['paint'].get('border'):
            dg.rectangle((inset, inset, w - 1 - inset, h - 1 - inset), outline=255)
            dl.rectangle((inset + 1, inset + 1, w - inset, h - inset), outline=255)
        pattern = ctx['paint'].get('engrave', 'vine')
        cx, cy = w / 2, h / 2
        if pattern == 'vine' and min(w, h) >= 26:
            # Mirrored scrollwork: a stem with curling fronds, like etched filigree.
            for side in (-1, 1):
                stem = []
                for k in range(48):
                    u = k / 47
                    x = cx + side * (1.5 + w * .08 * math.sin(u * math.pi * 1.5))
                    y = inset + 2 + u * (h - 2 * inset - 4)
                    stem.append((x, y))
                dg.line(stem, fill=255)
                for k in range(3):
                    u = .18 + k * .3
                    x0, y0 = stem[int(u * 47)]
                    pts = []
                    for q in range(24):
                        a = q / 23 * math.pi * 1.6
                        r = (w * .2) * (1 - q / 30)
                        pts.append((x0 + side * (r * math.sin(a)), y0 - r * .55 * (1 - math.cos(a))))
                    dg.line(pts, fill=255)
                    dl.line([(px + 1, py + 1) for px, py in pts], fill=255)
        elif pattern == 'chevron':
            for k in range(1, 4):
                y = inset + k * (h - 2 * inset) / 4
                dg.line([(inset + 1, y - h * .07), (cx, y + h * .03), (w - inset - 2, y - h * .07)], fill=255)
                dl.line([(inset + 1, y - h * .07 + 1), (cx, y + h * .03 + 1), (w - inset - 2, y - h * .07 + 1)], fill=255)
        elif pattern == 'scales':
            step = max(6, int(min(w, h) / 5))
            for row, y in enumerate(range(inset + 2, h - inset, step)):
                for x in range(inset + (row % 2) * step // 2, w - inset, step):
                    dg.arc((x - step / 2, y - step / 2, x + step / 2, y + step / 2), 10, 170, fill=255)
                    dl.arc((x - step / 2 + 1, y - step / 2 + 1, x + step / 2 + 1, y + step / 2 + 1), 10, 170, fill=255)
        elif pattern == 'ribs':
            for x in np.linspace(inset + 3, w - inset - 3, max(2, int(w / 10))):
                dg.line([(x, inset + 2), (x, h - inset - 3)], fill=255)
                dl.line([(x + 1, inset + 2), (x + 1, h - inset - 3)], fill=255)
        elif pattern == 'lines':
            for y in np.linspace(inset + 2, h - inset - 3, max(2, int(h / 9))):
                dg.line([(inset + 1, y), (w - inset - 2, y)], fill=255)
                dl.line([(inset + 1, y + 1), (w - inset - 2, y + 1)], fill=255)
        g = np.asarray(groove) > 0
        l = (np.asarray(lip) > 0) & ~g
        tone = tone.copy()
        tone[g] -= .3
        tone[l] += .16
        return tone, g

    def _finish(self, ctx, tone, ramp, dither=True):
        return _tone_to_rgb(tone, ramp, dither)

    # -- styles --------------------------------------------------------------
    def style_plate(self, ctx):
        """Lacquered armour plate: top-lit gradient, bevel, scratches, engraving."""
        mat = ctx['mat']
        n = _noise(ctx['w'], ctx['h'], ctx['part']['seed'], 3.0)
        tone = self._vertical(ctx, .46, .26) + (n - .5) * .12
        if ctx['part']['shape'] is not None:
            tone = tone + (ctx['t'] - .5) * .15
        # Diagonal specular sheen.
        sheen = np.exp(-((ctx['fx'] - ctx['fy'] * .7 - .25) ** 2) / .02)
        tone = tone + sheen * .1
        tone, grooves = self._engrave(ctx, tone, mat)
        tone = self._bevel(ctx, tone)
        rgb = self._finish(ctx, tone, mat.ramp)
        accent = ctx['paint'].get('runes')
        if accent and mat.accent is not None and grooves is not None:
            # Glowing inlay inside the carved grooves.
            rgb[grooves] = mat.accent[min(len(mat.accent) - 1, 2 + (ctx['rng'].random() > .5))]
        return rgb, None

    def style_trim(self, ctx):
        """Polished trim/filigree band with a bright centre line and rivets."""
        mat = ctx['mat']
        h, w = ctx['h'], ctx['w']
        across = ctx['fy'] if w >= h else ctx['fx']
        tone = .55 + .35 * np.cos((across - .35) * math.pi * 1.6) * .5
        tone = tone + (_noise(w, h, ctx['part']['seed'], 2.0) - .5) * .08
        tone = self._bevel(ctx, tone, .3, .35)
        if min(w, h) >= 4 and max(w, h) >= 14 and not ctx['paint'].get('plain'):
            step = 7
            along = np.arange(3, max(w, h) - 2, step)
            for p in along:
                if w >= h:
                    y = h // 2
                    tone[max(0, y - 1):y + 1, p:p + 2] = 1.05
                    tone[y:y + 2, p + 1:p + 3] -= .25
                else:
                    x = w // 2
                    tone[p:p + 2, max(0, x - 1):x + 1] = 1.05
                    tone[p + 1:p + 3, x:x + 2] -= .25
        return self._finish(ctx, tone, mat.ramp), None

    def style_glow(self, ctx):
        """Emissive core: white-hot centre, saturated rim, faceted glints."""
        mat = ctx['mat']
        if ctx['part']['shape'] is not None and ctx['face'] in ('north', 'south'):
            depth = np.zeros_like(ctx['fx'])
            m = ctx['alpha']
            for k in range(1, 6):
                m = _erode(m, 1)
                depth += m
            tone = .45 + depth / 5 * .55
        else:
            d = np.maximum(abs(ctx['fx'] - .5), abs(ctx['fy'] - .5)) * 2
            tone = 1 - d * .55
        tone = tone + (_noise(ctx['w'], ctx['h'], ctx['part']['seed'], 2.5) - .5) * .12
        return self._finish(ctx, tone, mat.ramp, dither=False), None

    def style_runes(self, ctx):
        """Dark inlaid channel carrying a line of luminous glyphs."""
        mat = ctx['mat']
        w, h = ctx['w'], ctx['h']
        tone = np.full((h, w), .32) + (_noise(w, h, ctx['part']['seed'], 2.0) - .5) * .12
        if min(w, h) >= 3:
            img = Image.new('L', (w * 4, h * 4), 0)
            d = ImageDraw.Draw(img)
            rng = ctx['rng']
            vertical = h >= w
            cell = (w if vertical else h) * 4
            count = max(1, int((h if vertical else w) * 4 / (cell * 1.35)))
            for k in range(count):
                along = (k + .5) / count * (h if vertical else w) * 4
                cx, cy = (w * 2, along) if vertical else (along, h * 2)
                r = cell * .34
                stroke = max(2, int(cell * .1))
                d.line([(cx, cy - r), (cx, cy + r)] if vertical else [(cx - r, cy), (cx + r, cy)], fill=255, width=stroke)
                for _ in range(rng.randint(1, 3)):
                    a = rng.choice((-1, 1)) * rng.uniform(.4, 1.2)
                    o = rng.uniform(-r * .7, r * .7)
                    if vertical:
                        d.line([(cx, cy + o), (cx + math.cos(a) * r, cy + o + math.sin(a) * r * .6)], fill=255, width=stroke)
                    else:
                        d.line([(cx + o, cy), (cx + o + math.sin(a) * r * .6, cy + math.cos(a) * r)], fill=255, width=stroke)
                if rng.random() < .4:
                    d.ellipse((cx - r * .45, cy - r * .45, cx + r * .45, cy + r * .45), outline=255, width=stroke)
            glyph = np.asarray(img.resize((w, h), Image.Resampling.BOX)) > 60
            tone[glyph] = 1.0
            halo = np.asarray(Image.fromarray((glyph * 255).astype('uint8')).filter(ImageFilter.MaxFilter(3))) > 0
            tone[halo & ~glyph] = np.maximum(tone[halo & ~glyph], .62)
        return self._finish(ctx, tone, mat.ramp, dither=False), None

    def style_gem(self, ctx):
        mat = ctx['mat']
        fx, fy = ctx['fx'], ctx['fy']
        facet = np.where(fx + fy < 1, .78, .52) + np.where(fx > fy, .1, -.05)
        d = np.hypot(fx - .5, fy - .5) * 2
        tone = facet - d * .25
        h, w = ctx['h'], ctx['w']
        if w >= 4 and h >= 4:
            tone[int(h * .22):int(h * .22) + 1, int(w * .25):int(w * .25) + 2] = 1.2
        tone = self._bevel(ctx, tone, .1, .3)
        return self._finish(ctx, tone, mat.ramp, dither=False), None

    def style_cloth(self, ctx):
        """Woven cloth with deep vertical folds, darker hems and an embroidered border."""
        mat = ctx['mat']
        w, h = ctx['w'], ctx['h']
        folds = ctx['paint'].get('folds', 5)
        phase = ctx['rng'].random() * 6
        fold = np.sin(ctx['fx'] * folds * math.tau / 2 + phase + np.sin(ctx['fy'] * 3) * .6)
        tone = .5 + fold * .14 + (ctx['t'] - .5) * .2
        tone = tone + (_noise(w, h, ctx['part']['seed'], 2.0) - .5) * .06
        # Weave texture.
        tone = tone + ((np.mgrid[0:h, 0:w][1] + np.mgrid[0:h, 0:w][0]) % 2) * .025
        rgb = None
        lining = ctx['paint'].get('lining')
        ramp = mat.ramp
        if lining and ctx['face'] == lining[0]:
            ramp = np.array([hex_rgb(c) for c in lining[1]], float)
        tone = self._bevel(ctx, tone, .12, .2)
        rgb = self._finish(ctx, tone, ramp)
        hem = ctx['paint'].get('hem')
        if hem and mat.trim is not None and ctx['side'] and h >= 10:
            band = (ctx['t'] < hem) & (ctx['t'] > hem * .35)
            if ctx['part']['shape'] is not None:
                band &= _erode(ctx['alpha'], 2)
            pattern = ((np.mgrid[0:h, 0:w][1] // 3 + np.mgrid[0:h, 0:w][0] // 3) % 3 == 0)
            rgb[band] = mat.trim[2]
            rgb[band & pattern] = mat.trim[-1]
            edge = (ctx['t'] < hem) & (ctx['t'] > hem - .03) | (ctx['t'] < hem * .35 + .02) & (ctx['t'] > hem * .35 - .02)
            rgb[edge] = mat.trim[1]
        emblem = ctx['paint'].get('emblem')
        if emblem and ctx['face'] in emblem.get('faces', ('north', 'south')) and mat.trim is not None and w >= 16:
            self._emblem(ctx, rgb, emblem, mat)
        return rgb, None

    @staticmethod
    def _texels_per_unit(ctx):
        ua = FACE_AXES[ctx['face']][0]
        return ctx['w'] / max(float(ctx['part']['size'][ua]), 1e-6)

    def style_lace(self, ctx):
        """Openwork lace: a staggered diamond net cut through soft folds, with solid hems and rim."""
        mat = ctx['mat']
        w, h = ctx['w'], ctx['h']
        folds = ctx['paint'].get('folds', 3)
        fold = np.sin(ctx['fx'] * folds * math.pi + ctx['rng'].random() * 6 + np.sin(ctx['fy'] * 3) * .5)
        tone = .48 + fold * .1 + (ctx['t'] - .5) * .16 + (_noise(w, h, ctx['part']['seed'], 2.0) - .5) * .06
        alpha = np.ones((h, w), bool)
        cell = max(4, int(round(self._texels_per_unit(ctx) * ctx['paint'].get('cell', .8))))
        if min(w, h) >= cell * 2 and ctx['side']:
            ys, xs = np.mgrid[0:h, 0:w]
            row = ys // cell
            cx = (xs + (row % 2) * cell / 2) % cell - cell / 2 + .5
            cy = ys % cell - cell / 2 + .5
            d = np.abs(cx) + np.abs(cy)
            hole = d < cell * .34
            # Threads around each opening catch a little more light.
            tone = tone + ((d >= cell * .34) & (d < cell * .34 + 1.2)) * .1
            solid = ~_erode(ctx['alpha'], 2) | (ctx['t'] > 1 - ctx['paint'].get('band', .08))
            if ctx['part']['shape'] is None:
                edge = np.zeros((h, w), bool)
                edge[:2, :] = edge[-2:, :] = edge[:, :2] = edge[:, -2:] = True
                solid |= edge
            alpha = ~(hole & ~solid)
        tone = self._bevel(ctx, tone, .1, .2)
        rgb = self._finish(ctx, tone, mat.ramp)
        if mat.trim is not None and ctx['part']['shape'] is not None:
            rim = ctx['alpha'] & ~_erode(ctx['alpha'], 1)
            rgb[rim] = mat.trim[2]
        return rgb, alpha

    def style_ghost(self, ctx):
        """Spectral cloth that frays into luminous wisps toward its hem (streaked alpha dissolve)."""
        mat = ctx['mat']
        w, h = ctx['w'], ctx['h']
        t = ctx['t']
        folds = ctx['paint'].get('folds', 4)
        fold = np.sin(ctx['fx'] * folds * math.tau / 2 + ctx['rng'].random() * 6 + np.sin(ctx['fy'] * 3) * .6)
        fade = ctx['paint'].get('fade', .3)
        low, high = ctx['paint'].get('grade', (.3, .42))
        tone = low + (high - low) * t + fold * ctx['paint'].get('depth', .1)
        tone = tone + (_noise(w, h, ctx['part']['seed'], 2.0) - .5) * .06
        tone = tone + ((np.mgrid[0:h, 0:w][1] + np.mgrid[0:h, 0:w][0]) % 2) * .02
        alpha = np.ones((h, w), bool)
        if fade > 0 and ctx['side'] and h >= 6:
            # Long vertical wisps: noise stretched along the cloth's fall.
            short = _noise(w, max(2, h // 7), ctx['part']['seed'] + 17, max(1.5, self._texels_per_unit(ctx) * .35))
            streak = np.asarray(Image.fromarray((short * 255).astype('uint8')).resize((w, h), Image.Resampling.BILINEAR), float) / 255
            depth = np.clip(t / fade, 0, 1)
            alpha = (depth > .12 + streak * .88) | (t >= fade)
        lining = ctx['paint'].get('lining')
        ramp = mat.ramp
        if lining and ctx['face'] == lining[0]:
            ramp = np.array([hex_rgb(c) for c in lining[1]], float)
        tone = self._bevel(ctx, tone, .1, .18)
        rgb = self._finish(ctx, tone, ramp)
        if mat.accent is not None and fade > 0:
            # The cloth thins into light: strands brighten toward their frayed tips.
            mix = np.clip(1 - t / (fade * 1.3), 0, 1) ** 1.2
            light = _tone_to_rgb(.2 + mix * .75 + fold * .04, mat.accent)
            rgb = rgb * (1 - mix[..., None]) + light * mix[..., None]
        hem = ctx['paint'].get('hem')
        if hem and mat.trim is not None and ctx['side'] and h >= 10:
            # Embroidered band just above where the cloth starts to fray.
            start = fade + .02
            band = (t > start) & (t < start + hem)
            pattern = ((np.mgrid[0:h, 0:w][1] // 3 + np.mgrid[0:h, 0:w][0] // 3) % 3 == 0)
            rgb[band] = mat.trim[1]
            rgb[band & pattern] = mat.trim[3]
        emblem = ctx['paint'].get('emblem')
        if emblem and ctx['face'] in emblem.get('faces', ('north', 'south')) and mat.trim is not None and w >= 16:
            self._emblem(ctx, rgb, emblem, mat)
        return rgb, alpha

    def _emblem(self, ctx, rgb, emblem, mat):
        w, h = ctx['w'], ctx['h']
        img = Image.new('L', (w * 4, h * 4), 0)
        d = ImageDraw.Draw(img)
        cx, cy = w * 2, h * 4 * emblem.get('y', .62)
        r = min(w, h) * 4 * emblem.get('r', .3)
        kind = emblem.get('kind', 'rose')
        if kind == 'rose':
            for k in range(5):
                a = k * math.tau / 5 - math.pi / 2
                d.ellipse((cx + math.cos(a) * r * .45 - r * .42, cy + math.sin(a) * r * .45 - r * .42,
                           cx + math.cos(a) * r * .45 + r * .42, cy + math.sin(a) * r * .45 + r * .42), outline=255, width=4)
            d.ellipse((cx - r * .22, cy - r * .22, cx + r * .22, cy + r * .22), fill=255)
            d.line([(cx, cy + r * .8), (cx, cy + r * 1.9)], fill=255, width=4)
            for side in (-1, 1):
                d.line([(cx, cy + r * 1.3), (cx + side * r * .5, cy + r * 1.05)], fill=255, width=4)
        elif kind == 'sun':
            for k in range(12):
                a = k * math.tau / 12
                d.line([(cx + math.cos(a) * r * .35, cy + math.sin(a) * r * .35), (cx + math.cos(a) * r, cy + math.sin(a) * r)],
                       fill=255, width=4 if k % 3 else 6)
            d.ellipse((cx - r * .3, cy - r * .3, cx + r * .3, cy + r * .3), outline=255, width=5)
        elif kind == 'eye':
            d.ellipse((cx - r, cy - r * .45, cx + r, cy + r * .45), outline=255, width=5)
            d.ellipse((cx - r * .3, cy - r * .3, cx + r * .3, cy + r * .3), fill=255)
            for k in range(7):
                a = -math.pi * (k + 1) / 8
                d.line([(cx + math.cos(a) * r * 1.15, cy + math.sin(a) * r * .75), (cx + math.cos(a) * r * 1.5, cy + math.sin(a) * r * 1.05)], fill=255, width=4)
        mask = np.asarray(img.resize((w, h), Image.Resampling.BOX)) > 110
        if emblem.get('tone') == 'trim' or kind != 'rose':
            rgb[mask] = mat.trim[-1]
            edge = np.asarray(Image.fromarray((mask * 255).astype('uint8')).filter(ImageFilter.MaxFilter(3))) > 0
            rgb[edge & ~mask] = mat.trim[1]
        else:
            rgb[mask] = mat.accent[2] if mat.accent is not None else mat.trim[-1]

    def style_membrane(self, ctx):
        """Leathery wing membrane: radial veins, darker root, ember rim near the edge."""
        mat = ctx['mat']
        s, t = ctx['s'], ctx['t']
        root = ctx['paint'].get('root') or (ctx['part']['shape']['polygon'][0] if ctx['part']['shape'] else (0, 1))
        dist = np.hypot(s - root[0], t - root[1])
        tone = .58 - dist * .28 + (_noise(ctx['w'], ctx['h'], ctx['part']['seed'], 5.0) - .5) * .16
        angle = np.arctan2(t - root[1], s - root[0])
        veins = np.abs(np.sin(angle * ctx['paint'].get('veins', 5) + np.sin(dist * 7) * .5))
        tone = tone - (veins < .09) * .2 + ((veins >= .09) & (veins < .16)) * .08
        inner = _erode(ctx['alpha'], 2)
        rim = ctx['alpha'] & ~inner
        tone[rim] += .2
        rgb = self._finish(ctx, tone, mat.ramp)
        if mat.accent is not None:
            ember = ctx['alpha'] & ~_erode(ctx['alpha'], 1)
            rgb[ember] = mat.accent[1]
        return rgb, None

    def style_feather(self, ctx):
        """Primary/covert feather: rachis, angled barbs, gilded or azure tip."""
        mat = ctx['mat']
        s, t = ctx['s'], ctx['t']
        across = (s - .5) * 2
        barbs = np.sin((t * 28 - np.abs(across) * 3) * math.pi)
        tone = .72 + barbs * .06 - np.abs(across) * .18 + t * .08
        tone = tone + (_noise(ctx['w'], ctx['h'], ctx['part']['seed'], 2.0) - .5) * .06
        shaft = np.abs(across) < max(.07, 1.2 / ctx['w'])
        tone[shaft] = .98
        tone = self._bevel(ctx, tone, .1, .3)
        rgb = self._finish(ctx, tone, mat.ramp)
        tip = ctx['paint'].get('tip', .78)
        if mat.accent is not None:
            band = t > tip
            grad = np.clip((t - tip) / max(1e-3, 1 - tip), 0, 1)
            accent = _tone_to_rgb(.45 + grad * .5 + barbs * .04, mat.accent)
            rgb[band] = accent[band]
            rgb[shaft & band] = mat.accent[-1]
        if mat.trim is not None and ctx['paint'].get('gild'):
            edge = ctx['alpha'] & ~_erode(ctx['alpha'], 1)
            rgb[edge & (t > .3)] = mat.trim[-2]
        return rgb, None

    def style_flame(self, ctx):
        """Ghostflame tongue: white core at the base, violet body, cool tips."""
        mat = ctx['mat']
        s, t = ctx['s'], ctx['t']
        core = 1 - np.abs(s - .5) * 2
        tone = .25 + core * .55 + (1 - t) * .35
        tone = tone + (_noise(ctx['w'], ctx['h'], ctx['part']['seed'], 2.0) - .5) * .25
        return self._finish(ctx, tone, mat.ramp, dither=False), None

    def style_horn(self, ctx):
        """Ridged horn/claw: growth rings along the length, dark root, polished tip."""
        mat = ctx['mat']
        along = ctx['fy'] if ctx['side'] else ctx['fx']
        rings = np.sin(along * ctx['h'] * .9) * .07
        tone = .35 + (1 - along) * .3 + rings + (_noise(ctx['w'], ctx['h'], ctx['part']['seed'], 2.0) - .5) * .08
        tone = self._bevel(ctx, tone, .18, .22)
        return self._finish(ctx, tone, mat.ramp), None

    def style_leather(self, ctx):
        mat = ctx['mat']
        ys, xs = np.mgrid[0:ctx['h'], 0:ctx['w']]
        wrap = ((xs + ys * 2) % 6 < 2)
        tone = .5 + (_noise(ctx['w'], ctx['h'], ctx['part']['seed'], 2.0) - .5) * .2 - wrap * .2
        tone = self._bevel(ctx, tone, .15, .25)
        return self._finish(ctx, tone, mat.ramp), None

    def style_blade(self, ctx):
        """Dark tempered steel: honed edge highlight and a central fuller groove."""
        mat = ctx['mat']
        fx, fy = ctx['fx'], ctx['fy']
        edge = np.minimum(fx, 1 - fx)
        tone = .35 + (1 - edge * 2) * .35 + (_noise(ctx['w'], ctx['h'], ctx['part']['seed'], 3.0) - .5) * .1
        tone = tone + np.exp(-((fx - fy * .4 - .2) ** 2) / .01) * .15
        tone = self._bevel(ctx, tone, .25, .2)
        return self._finish(ctx, tone, mat.ramp), None

    def style_skin(self, ctx):
        mat = ctx['mat']
        tone = self._vertical(ctx, .62, .14) + (_noise(ctx['w'], ctx['h'], ctx['part']['seed'], 4.0) - .5) * .05
        rgb = self._finish(ctx, tone, mat.ramp, dither=False)
        face = ctx['paint'].get('face')
        if face and ctx['face'] == 'north':
            face(ctx, rgb, mat)
        return rgb, None

    def style_hair(self, ctx):
        mat = ctx['mat']
        ys, xs = np.mgrid[0:ctx['h'], 0:ctx['w']]
        rng = np.random.default_rng(ctx['part']['seed'])
        strands = rng.random(ctx['w'] + 2)
        tone = .45 + strands[xs] * .25 + self._vertical(ctx, 0, .3)
        tone = tone + np.sin(ctx['fy'] * 6 + strands[xs] * 4) * .06
        tone = self._bevel(ctx, tone, .15, .25)
        return self._finish(ctx, tone, mat.ramp), None


def paint_atlas(forge, size=1024, density=6.0, gutter=1, max_tile=176):
    """Paint every textured face and pack the tiles; returns (image, uv map, density)."""
    painter = Painter(forge)
    while True:
        tiles, refs = {}, []
        for index, part in enumerate(forge.parts):
            mat = forge.materials[part['mat']]
            dens = density * mat.density * part['paint'].get('density', 1.0)
            for face in part['faces']:
                ua, va = FACE_AXES[face]
                w = int(np.clip(round(part['size'][ua] * dens), 2, max_tile))
                h = int(np.clip(round(part['size'][va] * dens), 2, max_tile))
                two_sided = part['paint'].get('lining') is not None
                if part['shape'] is not None and face == 'north' and not two_sided:
                    refs.append((index, face, 'mirror_south'))
                    continue
                pixels = painter.face(part, 'south' if part['shape'] is not None and not two_sided else face, w, h)
                key = hashlib.sha1(pixels.tobytes() + bytes([w % 256, h % 256])).hexdigest()
                tiles.setdefault(key, pixels)
                refs.append((index, face, key))
        order = sorted(tiles, key=lambda k: (-tiles[k].shape[0], -tiles[k].shape[1]))
        placed, x, y, row = {}, gutter, gutter, 0
        fits = True
        for key in order:
            h, w = tiles[key].shape[:2]
            if x + w + gutter > size:
                x, y, row = gutter, y + row + 2 * gutter, 0
            if y + h + gutter > size:
                fits = False
                break
            placed[key] = (x, y)
            x += w + 2 * gutter
            row = max(row, h)
        if fits:
            break
        density *= .93
    image = np.zeros((size, size, 4), np.uint8)
    for key, (x, y) in placed.items():
        tile = tiles[key]
        h, w = tile.shape[:2]
        # Bleed the edge texels into the gutter so mipmaps never pick up a neighbour.
        padded = np.pad(tile, ((gutter, gutter), (gutter, gutter), (0, 0)), mode='edge')
        image[y - gutter:y + h + gutter, x - gutter:x + w + gutter] = padded
    uvs = {}
    for index, face, key in refs:
        if key == 'mirror_south':
            continue
        x, y = placed[key]
        h, w = tiles[key].shape[:2]
        uvs[(index, face)] = [x, y, x + w, y + h]
    for index, face, key in refs:
        if key == 'mirror_south':
            u0, v0, u1, v1 = uvs[(index, 'south')]
            uvs[(index, face)] = [u1, v0, u0, v1]
    used = sum(t.shape[0] * t.shape[1] for t in tiles.values())
    return Image.fromarray(image, 'RGBA'), uvs, dict(density=density, tiles=len(tiles), coverage=used / size / size)


# ---------------------------------------------------------------------------
# Animation


class Clip:
    """Densely sampled pose function, simplified per channel at export."""

    def __init__(self, name, length, pose, loop=False, step=1 / 20, bones=None):
        self.name, self.length, self.pose, self.loop, self.step, self.bones = name, length, pose, loop, step, bones


def _simplify(times, values, tolerance):
    keep = [0, len(times) - 1]

    def split(a, b):
        if b - a < 2:
            return
        t0, t1 = times[a], times[b]
        worst, index = 0, None
        for i in range(a + 1, b):
            u = (times[i] - t0) / (t1 - t0)
            err = float(np.max(np.abs(values[a] * (1 - u) + values[b] * u - values[i])))
            if err > worst:
                worst, index = err, i
        if worst > tolerance:
            keep.append(index)
            split(a, index)
            split(index, b)
    split(0, len(times) - 1)
    return sorted(set(keep))


def sample_clip(clip):
    count = max(2, int(round(clip.length / clip.step)) + 1)
    times = [round(clip.length * i / (count - 1), 4) for i in range(count)]
    return times, [clip.pose(t) for t in times]


def moved_bones(samples):
    moved = set()
    for pose in samples:
        for bone, value in pose.items():
            if isinstance(value, dict) and np.any(np.abs(np.array(value.get('pos', [0, 0, 0]), float)) > 1e-6):
                moved.add(bone)
    return moved


def export_clip(clip, bone_names, uid, rotation_tolerance=.3, position_tolerance=.015, sampled=None, positioned=()):
    """Override clip keying every listed bone, so ModelEngine never blends a lower lane into it.

    Position channels are written for every bone in `positioned` (moved by any clip),
    even when this clip holds them still; otherwise a default-state idle could add its bob.
    """
    times, samples = sampled or sample_clip(clip)
    animators = {}
    keyed = clip.bones if clip.bones is not None else bone_names
    for bone in keyed:
        frames = []
        for channel, tolerance in (('rotation', rotation_tolerance), ('position', position_tolerance)):
            values = []
            for pose in samples:
                value = pose.get(bone)
                if isinstance(value, dict):
                    vec = value.get('rot' if channel == 'rotation' else 'pos', [0, 0, 0])
                elif channel == 'rotation' and value is not None:
                    vec = value
                else:
                    vec = [0, 0, 0]
                values.append(np.array(vec, float))
            values = np.array(values)
            if channel == 'position' and bone not in positioned and not np.any(np.abs(values) > 1e-6):
                continue
            for i in _simplify(times, values, tolerance):
                v = values[i]
                v = [-v[0], -v[1], v[2]] if channel == 'rotation' else [-v[0], v[1], v[2]]
                frames.append(dict(channel=channel, data_points=[dict(x=f'{v[0]:.4f}', y=f'{v[1]:.4f}', z=f'{v[2]:.4f}')],
                                   uuid=uid(f'{clip.name}/{bone}/{channel}/{times[i]}'), time=times[i], color=-1,
                                   interpolation='linear'))
        animators[uid('bone/' + bone)] = dict(name=bone, type='bone', keyframes=frames)
    return dict(uuid=uid('animation/' + clip.name), name=clip.name, loop='loop' if clip.loop else 'hold',
                override=True, length=clip.length, snapping=20, anim_time_update='', blend_weight='',
                start_delay='', loop_delay='', animators=animators)


def build_model(forge, clips, identifier, texture_name, hitbox, meta=None, size=1024, density=6.0):
    """Assemble a self-contained R4 free-format blueprint."""
    def uid(label):
        return str(uuid.uuid5(uuid.NAMESPACE_URL, f'evergarden/{identifier}/' + label))
    atlas, uvs, stats = paint_atlas(forge, size, density)
    elements, children = [], {name: [] for name in forge.bones}
    for index, part in enumerate(forge.parts):
        lo, hi = part['center'] - part['size'] / 2, part['center'] + part['size'] / 2
        faces = {}
        for face in FACES:
            if face in part['faces']:
                faces[face] = dict(uv=[round(v, 3) for v in uvs[(index, face)]], texture=0)
            else:
                faces[face] = dict(uv=[0, 0, 0, 0], texture=None)
        e = dict(name=f"{part['label']}_{index}", type='cube', uuid=uid(f'cube/{index}'),
                 **{'from': [round(float(v), 5) for v in lo]}, to=[round(float(v), 5) for v in hi],
                 origin=[round(float(v), 5) for v in part['center']],
                 rotation=[round(v, 4) for v in euler(part['r'])], box_uv=False, faces=faces,
                 visibility=True, export=True, shade=not forge.materials[part['mat']].glow, autouv=0, color=0)
        elements.append(e)
        children[part['bone']].append(e['uuid'])

    def group(name):
        return dict(name=name, uuid=uid('bone/' + name), origin=forge.bones[name][1], rotation=[0, 0, 0],
                    export=True, visibility=True, color=0,
                    children=children[name] + [group(n) for n, (parent, _) in forge.bones.items() if parent == name])
    roots = [n for n, (parent, _) in forge.bones.items() if parent is None]
    hit = uid('hitbox/cube')
    elements.append(dict(name='hitbox', uuid=hit, type='cube', **{'from': hitbox[0]}, to=hitbox[1], origin=[0, 0, 0], faces={}))
    buffer = io.BytesIO()
    atlas.save(buffer, format='PNG', optimize=True)
    names = list(forge.bones)
    sampled = {c.name: sample_clip(c) for c in clips}
    positioned = set()
    for c in clips:
        positioned |= moved_bones(sampled[c.name][1]) & set(c.bones if c.bones is not None else names)
    data = dict(meta=dict(format_version='4.10', model_format='free', box_uv=False,
                          evergarden_animation_position_x='inverted', **(meta or {})),
                name=identifier, model_identifier=identifier, resolution=dict(width=size, height=size),
                elements=elements,
                outliner=[group(r) for r in roots] + [dict(name='hitbox', uuid=uid('hitbox'), origin=[0, 30, 0], children=[hit], export=True)],
                textures=[dict(name=texture_name + '.png', namespace='', folder='', path='', id='0', uuid=uid('texture'),
                               width=size, height=size, uv_width=size, uv_height=size,
                               source='data:image/png;base64,' + base64.b64encode(buffer.getvalue()).decode())],
                animations=[export_clip(c, names, uid, sampled=sampled[c.name],
                                        positioned=positioned & set(c.bones if c.bones is not None else names)) for c in clips])
    return data, atlas, stats


# ---------------------------------------------------------------------------
# Pose helpers shared by both bosses


def ease(t):
    t = float(np.clip(t, 0, 1))
    return t * t * (3 - 2 * t)


def ease_out(t):
    t = float(np.clip(t, 0, 1))
    return 1 - (1 - t) ** 3


def ease_in(t):
    t = float(np.clip(t, 0, 1))
    return t ** 3


def blend(a, b, t):
    """Blend two poses (dicts of bone -> rot or {'rot','pos'})."""
    out = {}
    for bone in set(a) | set(b):
        va, vb = a.get(bone), b.get(bone)
        ra = np.array((va.get('rot', [0, 0, 0]) if isinstance(va, dict) else va) if va is not None else [0, 0, 0], float)
        rb = np.array((vb.get('rot', [0, 0, 0]) if isinstance(vb, dict) else vb) if vb is not None else [0, 0, 0], float)
        pa = np.array(va.get('pos', [0, 0, 0]) if isinstance(va, dict) else [0, 0, 0], float)
        pb = np.array(vb.get('pos', [0, 0, 0]) if isinstance(vb, dict) else [0, 0, 0], float)
        r = ra * (1 - t) + rb * t
        p = pa * (1 - t) + pb * t
        out[bone] = {'rot': list(r), 'pos': list(p)} if np.any(np.abs(p) > 1e-9) else list(r)
    return out


def add(pose, bone, rot_deg=(0, 0, 0), pos=(0, 0, 0)):
    value = pose.get(bone)
    r = np.array((value.get('rot', [0, 0, 0]) if isinstance(value, dict) else value) if value is not None else [0, 0, 0], float)
    p = np.array(value.get('pos', [0, 0, 0]) if isinstance(value, dict) else [0, 0, 0], float)
    r = r + np.array(rot_deg, float)
    p = p + np.array(pos, float)
    pose[bone] = {'rot': list(r), 'pos': list(p)} if np.any(np.abs(p) > 1e-9) else list(r)
    return pose


def keyed(keys, t, loop_length=None):
    """Piecewise eased interpolation of pose keys [(time, pose), ...]."""
    if loop_length is not None:
        t = t % loop_length
    if t <= keys[0][0]:
        return keys[0][1]
    for (ta, pa), (tb, pb) in zip(keys, keys[1:]):
        if ta <= t <= tb:
            return blend(pa, pb, ease((t - ta) / max(1e-9, tb - ta)))
    return keys[-1][1]


def dump(model):
    return json.dumps(model, separators=(',', ':'), ensure_ascii=False)


# ---------------------------------------------------------------------------
# Kinematics for authoring: FK over the bind skeleton and hinge-correct IK.


def frame2(a, b):
    """Rotation whose first column is a and second column is b orthogonalized against a."""
    a = unit(a)
    b = np.asarray(b, float) - a * (a @ np.asarray(b, float))
    if np.linalg.norm(b) < 1e-9:
        b = np.cross(a, [0, 0, 1]) if abs(a[2]) < .9 else np.cross(a, [1, 0, 0])
    b = unit(b)
    return np.column_stack((a, b, np.cross(a, b)))


def _channels(value):
    if value is None:
        return np.zeros(3), np.zeros(3)
    if isinstance(value, dict):
        return np.array(value.get('rot', [0, 0, 0]), float), np.array(value.get('pos', [0, 0, 0]), float)
    return np.array(value, float), np.zeros(3)


class Poser:
    """Evaluates poses on a forge skeleton exactly like the renderer and BossHitbox."""

    def __init__(self, bones):
        self.bones = bones
        self.order = []
        pending = list(bones)
        while pending:
            for name in list(pending):
                parent = bones[name][0]
                if parent is None or parent in self.order:
                    self.order.append(name)
                    pending.remove(name)

    def origin(self, name):
        return np.array(self.bones[name][1], float)

    def fk(self, pose):
        mats = {}
        for name in self.order:
            parent = self.bones[name][0]
            r_deg, p = _channels(pose.get(name))
            r = rot(r_deg)
            o = self.origin(name)
            m = np.eye(4)
            m[:3, :3] = r
            m[:3, 3] = o - r @ o + p
            mats[name] = (mats[parent] if parent else np.eye(4)) @ m
        return mats

    def world(self, mats, name, local=None):
        p = self.origin(name) if local is None else np.asarray(local, float)
        return (mats[name] @ np.append(p, 1))[:3]

    def two_bone(self, pose, names, target, pole, hinge, end_rot=None, reach=.998):
        """Place the end joint at `target` (model space); the middle joint bends toward `pole`.

        `hinge` is the bind-space bend axis of the middle joint (+X for elbows, -X for knees).
        Returns the reach error (0 when the target is reachable).
        """
        upper, lower, end = names
        mats = self.fk(pose)
        parent = mats[self.bones[upper][0]]
        inv = np.linalg.inv(parent)
        t = (inv @ np.append(target, 1))[:3]
        pl = (inv @ np.append(pole, 1))[:3]
        s, e, h = self.origin(upper), self.origin(lower), self.origin(end)
        a, b = np.linalg.norm(e - s), np.linalg.norm(h - e)
        offset = t - s
        dist = np.linalg.norm(offset)
        axis = offset / max(dist, 1e-9)
        d = float(np.clip(dist, abs(a - b) + 1e-3, (a + b) * reach))
        error = max(0.0, dist - d)
        t = s + axis * d
        x = (a * a - b * b + d * d) / (2 * d)
        height = math.sqrt(max(0.0, a * a - x * x))
        bend = pl - s - axis * ((pl - s) @ axis)
        if np.linalg.norm(bend) < 1e-6:
            bend = np.cross(axis, hinge)
        bend = unit(bend)
        elbow = s + axis * x + bend * height
        upper_dir, lower_dir = unit(elbow - s), unit(t - elbow)
        # The bend plane normal; equals cross(upper, lower) and stays defined for a straight limb.
        new_hinge = unit(np.cross(bend, axis))
        bind_dir = unit(e - s)
        u = frame2(upper_dir, new_hinge) @ frame2(bind_dir, hinge).T
        local_lower = u.T @ lower_dir
        bind_lower = unit(h - e)
        hx = unit(np.asarray(hinge, float))
        pa = unit(bind_lower - hx * (bind_lower @ hx))
        pb = unit(local_lower - hx * (local_lower @ hx))
        angle = math.degrees(math.atan2(np.cross(pa, pb) @ hx, pa @ pb))
        l = axis_angle(hx, angle)
        pose[upper] = euler(u)
        pose[lower] = euler(l)
        if end_rot is not None:
            pose[end] = euler((parent[:3, :3] @ u @ l).T @ end_rot)
        return error


def lerp(a, b, t):
    return np.asarray(a, float) * (1 - t) + np.asarray(b, float) * t


EASINGS = {'smooth': ease, 'in': ease_in, 'out': ease_out, 'linear': lambda u: float(np.clip(u, 0, 1)),
           'snap': lambda u: float(np.clip(u, 0, 1)) ** .45, 'hold': lambda u: 0.0}


class Track:
    """Keyed intents for one clip: [(time, {name: value, 'ease': kind}), ...].

    Every name is interpolated over the keys that define it. Vector channels listed
    as splines follow a Catmull-Rom curve (sword arcs); the segment easing remaps time.
    """

    def __init__(self, keys, splines=()):
        self.keys = sorted(keys, key=lambda k: k[0])
        self.splines = set(splines)

    def __call__(self, name, t, default=None):
        pts = [(k_t, d[name], d.get('ease', 'smooth')) for k_t, d in self.keys if name in d]
        if not pts:
            return default
        times = [p[0] for p in pts]
        values = [np.asarray(p[1], float) for p in pts]
        if len(pts) == 1 or t <= times[0]:
            return values[0]
        if t >= times[-1]:
            return values[-1]
        i = max(k for k in range(len(times) - 1) if times[k] <= t)
        u = EASINGS[pts[i][2]]((t - times[i]) / max(1e-9, times[i + 1] - times[i]))
        if name in self.splines and len(pts) > 2:
            return catmull(values, times, times[i] + u * (times[i + 1] - times[i]))
        return lerp(values[i], values[i + 1], u)


def slerp(a, b, t):
    """Interpolate two rotation matrices along the shortest arc."""
    rel = a.T @ b
    angle = math.acos(float(np.clip((np.trace(rel) - 1) / 2, -1, 1)))
    if angle < 1e-6:
        return a.copy()
    axis = np.array([rel[2, 1] - rel[1, 2], rel[0, 2] - rel[2, 0], rel[1, 0] - rel[0, 1]])
    if np.linalg.norm(axis) < 1e-9:
        w, v = np.linalg.eigh((rel + rel.T) / 2)
        axis = v[:, np.argmax(w)]
    return a @ axis_angle(axis, math.degrees(angle) * t)


def catmull(points, times, t):
    """Centripetal-free uniform Catmull-Rom through keyed points; clamps at the ends."""
    pts = [np.asarray(p, float) for p in points]
    if t <= times[0]:
        return pts[0]
    if t >= times[-1]:
        return pts[-1]
    i = max(k for k in range(len(times) - 1) if times[k] <= t)
    u = (t - times[i]) / max(1e-9, times[i + 1] - times[i])
    p0 = pts[i - 1] if i > 0 else pts[i]
    p1, p2 = pts[i], pts[i + 1]
    p3 = pts[i + 2] if i + 2 < len(pts) else pts[i + 1]
    return .5 * ((2 * p1) + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u + (-p0 + 3 * p1 - 3 * p2 + p3) * u ** 3)
