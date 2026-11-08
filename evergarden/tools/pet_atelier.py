"""Pet Atelier: clean block anatomy with 3D-painted coats for Evergarden pets.

Bodies are a few large, softly chamfered blocks. Fine detail (eyes, coat
markings, socks, bellies, stripes, blush, mouths) is painted into the texture by
sampling species-defined marks at every texel's rest-pose position on the model,
so neighbouring blocks share one continuous coat and faces read as drawn
characters instead of cubes glued onto a slab pile.

Units are Blockbench sixteenths of a block, forward is -Z and the ground is y=0.
UV orientation follows Minecraft's per-face convention (vanilla auto-UV), which
Blockbench, ModelEngine and the Bedrock codec share.
"""
from __future__ import annotations

import math
import random
import zlib

import numpy as np
from PIL import Image

FACES = ('north', 'south', 'east', 'west', 'up', 'down')
STAGES = ('baby', 'medium', 'adult')


# --------------------------------------------------------------------------- math

def rotation(v):
    x, y, z = map(math.radians, v)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    return (np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]) @ np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
            @ np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]]))


def face_frame(face, lo, hi):
    """Texel (u, v) in [0,1]^2 of a face maps to origin + u*U + v*V (cube-local); also its normal."""
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    return {
        'north': ((x1, y1, z0), (x0 - x1, 0, 0), (0, y0 - y1, 0), (0, 0, -1)),
        'south': ((x0, y1, z1), (x1 - x0, 0, 0), (0, y0 - y1, 0), (0, 0, 1)),
        'east': ((x1, y1, z1), (0, 0, z0 - z1), (0, y0 - y1, 0), (1, 0, 0)),
        'west': ((x0, y1, z0), (0, 0, z1 - z0), (0, y0 - y1, 0), (-1, 0, 0)),
        'up': ((x0, y1, z0), (x1 - x0, 0, 0), (0, 0, z1 - z0), (0, 1, 0)),
        'down': ((x0, y0, z1), (x1 - x0, 0, 0), (0, 0, z0 - z1), (0, -1, 0)),
    }[face]


def rgb(hex_colour):
    return np.array(list(bytes.fromhex(hex_colour.strip().lstrip('#')[:6])), float)


def _tufts(P):
    """A zigzag in [-1, 1]: coat boundaries break into small fur tufts instead of pixel salt."""
    z = (P[..., 0] * .9 + P[..., 1] * .55 + P[..., 2] * .8) * .62
    z2 = (P[..., 0] * -.7 + P[..., 1] * .8 + P[..., 2] * .5) * .41
    return (np.abs(z - np.floor(z) - .5) * 4 - 1) * .7 + (np.abs(z2 - np.floor(z2) - .5) * 4 - 1) * .3


def _cell_hash(cell, salt):
    h = np.sin(cell[..., 0] * 127.1 + cell[..., 1] * 311.7 + cell[..., 2] * 74.7 + salt) * 43758.5453
    return h - np.floor(h)


def _hash(P, salt=0.0):
    """Deterministic per-position noise in [0, 1)."""
    q = np.floor(P * 4.0 + .5)
    h = np.sin(q[..., 0] * 12.9898 + q[..., 1] * 78.233 + q[..., 2] * 37.719 + salt) * 43758.5453
    return h - np.floor(h)


# -------------------------------------------------------------------------- shapes
# A shape maps points (..., 3) to a signed value: negative inside, positive outside.

def ellipsoid(c, r):
    c, r = np.array(c, float), np.array(r, float)
    return lambda P: np.sqrt((((P - c) / r) ** 2).sum(-1)) - 1


def slab(axis, lo=-1e9, hi=1e9):
    """Points whose coordinate on the axis (0/1/2 or a direction) lies in [lo, hi]."""
    def f(P):
        t = P[..., axis] if isinstance(axis, int) else P @ np.array(axis, float)
        return np.maximum(lo - t, t - hi)
    return f


def boxed(lo, hi):
    lo, hi = np.array(lo, float), np.array(hi, float)
    return lambda P: np.max(np.maximum(lo - P, P - hi), axis=-1)


def both(*shapes):
    return lambda P: np.max(np.stack([s(P) for s in shapes]), axis=0)


def either(*shapes):
    return lambda P: np.min(np.stack([s(P) for s in shapes]), axis=0)


def minus(a, b):
    return lambda P: np.maximum(a(P), -b(P))


def mirrored(shape):
    """The shape and its mirror image across the creature's midline."""
    return lambda P: np.minimum(shape(P), shape(P * np.array([-1, 1, 1])))


def polyline(points, radius):
    pts = [np.array(p, float) for p in points]

    def f(P):
        best = np.full(P.shape[:-1], 1e9)
        for a, b in zip(pts, pts[1:]):
            ab = b - a
            t = np.clip(((P - a) @ ab) / max(1e-9, ab @ ab), 0, 1)
            d = np.linalg.norm(P - (a + t[..., None] * ab), axis=-1)
            best = np.minimum(best, d)
        return best - radius
    return f


# --------------------------------------------------------------------------- marks

class Mark:
    """Paints a colour where a shape is inside, on selected parts and facing directions."""

    def __init__(self, shape, colour, groups=None, mats=None, facing=None, min_dot=.25, jitter=0.0,
                 parts=None, not_groups=None):
        self.shape, self.colour = shape, colour
        self.groups = set(groups) if groups else None
        self.not_groups = set(not_groups) if not_groups else set()
        self.mats = set(mats) if mats else None
        self.parts = parts
        self.facing = None if facing is None else np.array(facing, float) / np.linalg.norm(facing)
        self.min_dot, self.jitter = min_dot, jitter
        self.layer = 0  # coat marks first; face decals (layer 2) and eyes (layer 3) paint over them

    def accepts(self, part, normal):
        if self.groups is not None and part['group'] not in self.groups:
            return False
        if part['group'] in self.not_groups:
            return False
        if self.mats is not None and part['material'] not in self.mats:
            return False
        if self.parts is not None and not any(part['name'].startswith(p) for p in self.parts):
            return False
        if self.facing is not None and normal @ self.facing < self.min_dot:
            return False
        return True

    def mask(self, P):
        d = self.shape(P)
        if self.jitter:
            d = d + _tufts(P) * self.jitter
        return d <= 0

    def apply(self, ctx):
        if not self.accepts(ctx.part, ctx.normal):
            return
        m = self.mask(ctx.P)
        if m.any():
            ctx.rgb[m] = ctx.colour(self.colour)
            ctx.flat[m] = ctx.flat_colour(self.colour)


class Bands(Mark):
    """Repeating rings along a direction (tail rings, tabby stripes)."""

    def __init__(self, axis, start, period, duty, colour, wobble=0.0, **kw):
        super().__init__(None, colour, **kw)
        self.axis, self.start, self.period, self.duty, self.wobble = np.array(axis, float), start, period, duty, wobble

    def mask(self, P):
        t = (P @ self.axis - self.start) / self.period
        if self.wobble:
            t = t + np.sin(P[..., 0] * 1.7 + P[..., 2] * .9) * self.wobble
        return (t >= 0) & ((t - np.floor(t)) < self.duty)


class Eye(Mark):
    """A painted eye. Centre is the right eye (x > 0); the left one is mirrored.

    style: 'round' (cute, big catch-lights), 'almond' (lidded, optional kohl wing),
    'sleepy' (half-closed under a heavy lid), 'dot' (small shiny bead).
    """

    def __init__(self, centre, width, height, iris='#5A3B2E', iris_light='#B07A4F', outline='#2A1C22',
                 pupil='#1A1117', shine='#FFFFFF', style='round', facing=(0, 0, -1), slit=False, lid=None,
                 tilt=0.0, wing=0.0, lid_colour=None, depth=2.0, lash=True):
        super().__init__(None, None, groups=('head', 'snout', 'face'))
        self.c = np.array(centre, float)
        self.w, self.h = width / 2, height / 2
        self.cols = dict(iris=iris, iris_light=iris_light, outline=outline, pupil=pupil, shine=shine,
                         lid=lid_colour)
        self.style, self.slit, self.lid, self.tilt, self.wing = style, slit, lid, tilt, wing
        self.layer = 3
        self.facing_dir = np.array(facing, float) / np.linalg.norm(facing)
        self.depth, self.lash = depth, lash

    def _local(self, P, side):
        c = self.c * np.array([side, 1, 1])
        f = self.facing_dir * np.array([side, 1, 1])
        up = np.array([0.0, 1.0, 0.0])
        up = up - f * (up @ f)
        up /= np.linalg.norm(up)
        across = np.cross(f, up)
        across /= np.linalg.norm(across)
        # a runs towards the creature's temple on each side, b runs up.
        outward = across if across[0] * side >= 0 else -across
        d = P - c
        a = d @ outward
        b = d @ up
        n = d @ f
        return a, b, n, f

    def apply(self, ctx):
        part = ctx.part
        if part['group'] not in self.groups:
            return
        for side in (1, -1):
            a, b, n, f = self._local(ctx.P, side)
            if ctx.normal @ f < .3:
                continue
            near = np.abs(n) <= self.depth
            if not near.any():
                continue
            self._paint(ctx, a / self.w, b / self.h, near, side)

    def _paint(self, ctx, u, v, near, side):
        cols = self.cols
        texel = 1.0 / (ctx.density * min(self.w, self.h))  # one texel in normalised eye units
        # Tilted eyes rise towards their outer corner.
        v = v - self.tilt * u
        r = np.sqrt(u * u + v * v)
        style = self.style
        if style == 'dot':
            inside = (r <= 1) & near
            ctx.put(inside, cols['outline'])
            ctx.put(inside & (np.hypot(u + .3, v - .35) < .38), cols['shine'])
            return
        if style == 'almond':
            # Flat upper lid, round lower lid; outer corner slightly pointed.
            lid_line = .66 + .14 * u
            body = (v <= lid_line) & (u * u + v * v <= 1)
        elif style == 'sleepy':
            lid_line = (self.lid if self.lid is not None else .05) + 0 * u
            body = (r <= 1) & (v <= lid_line)
        else:
            lid_line = None
            body = r <= 1
        body &= near
        if not body.any():
            return
        # Ring: outline everywhere on the rim; thicker lash line along the top.
        rim = body & ~(_shrink(u, v, style, lid_line, texel * 1.1, self))
        iris = body & ~rim
        # Iris: dark at the top, glowing towards the bottom.
        top = np.clip((v + 1) / 2, 0, 1)
        dark, light = rgb(cols['iris']), rgb(cols['iris_light'])
        col = dark[None, :] * top[..., None] + light[None, :] * (1 - top[..., None])
        sel = iris
        ctx.rgb[sel] = col[sel]
        ctx.flat[sel] = 1
        # Pupil.
        if self.slit:
            pupil = iris & (np.abs(u) < .22) & (np.abs(v) < .78)
        else:
            pupil = iris & (np.hypot(u * 1.0, v * .92 + .05) < .48)
        ctx.put(pupil, cols['pupil'])
        ctx.put(rim, cols['outline'])
        # Upper lash line thickens, lifting into a small flick at the outer corner.
        if self.lash:
            if lid_line is None:
                lash = body & (v > .62) & (r > 1 - texel * 2.2)
            else:
                lash = body & (v > lid_line - texel * 1.9)
            ctx.put(lash, cols['outline'])
        if self.wing:
            # Kohl wing beyond the outer corner (sphinx, fox).
            w = self.wing
            wing = near & (u > .75) & (u < 1 + w) & (np.abs(v - (.15 + (u - .75) * .55)) < texel * 1.6 + .02)
            ctx.put(wing, cols['outline'])
        if style == 'sleepy' and cols['lid']:
            lid_band = near & (r <= 1.12) & (v > lid_line) & (v < lid_line + .5)
            ctx.put(lid_band, cols['lid'])
            line = near & (r <= 1.1) & (np.abs(v - lid_line) < texel * 1.2)
            ctx.put(line, cols['outline'])
        # Catch-lights, both lit from the same side (viewer's upper left = creature's right).
        lx = side  # the outward axis flips per eye; this keeps both lights on world +x
        big = iris & (np.hypot((u - lx * .32) * 1.0, v - .34) < .27)
        small = iris & (np.hypot(u + lx * .36, v + .42) < .13)
        if style == 'sleepy':
            big = iris & (np.hypot(u - lx * .3, v + .3) < .2)
            small = small & False
        ctx.put(big, cols['shine'])
        ctx.put(small, cols['shine'])


def _shrink(u, v, style, lid_line, t, eye):
    r = np.sqrt(u * u + v * v)
    if style == 'almond':
        return (v <= lid_line - t) & (np.sqrt(u * u + v * v) <= 1 - t)
    if style == 'sleepy':
        return (r <= 1 - t) & (v <= lid_line - t)
    return r <= 1 - t


class Inset(Mark):
    """A panel inset from the edges of a part's own (unrotated) face: inner ears, wing panes, bands."""

    def __init__(self, colour, faces=('north',), left=.5, right=.5, top=0.0, bottom=0.0, **kw):
        super().__init__(None, colour, **kw)
        self.faces, self.m = set(faces), (left, right, top, bottom)

    def apply(self, ctx):
        if ctx.face not in self.faces or not self.accepts(ctx.part, ctx.normal):
            return
        left, right, top, bottom = self.m
        m = (ctx.du >= left) & (ctx.dr >= right) & (ctx.dt >= top) & (ctx.db >= bottom)
        if m.any():
            ctx.rgb[m] = ctx.colour(self.colour)


class Toward(Mark):
    """Colours the faces of selected parts that look towards a point (the inside of a curl)."""

    def __init__(self, centre, colour, **kw):
        super().__init__(None, colour, **kw)
        self.centre = np.array(centre, float)

    def apply(self, ctx):
        if not self.accepts(ctx.part, ctx.normal):
            return
        mid = ctx.P.reshape(-1, 3).mean(0)
        if ctx.normal @ (self.centre - mid) > 0:
            ctx.rgb[...] = ctx.colour(self.colour)


class Dots(Mark):
    """A regular scatter of round dots in the plane of each face (fabric prints, spots)."""

    def __init__(self, spacing, radius, colour, offset=(0, 0, 0), scatter=0.0, skip=0.0, **kw):
        super().__init__(None, colour, **kw)
        self.sp, self.r, self.off = spacing, radius, np.array(offset, float)
        self.scatter, self.skip = scatter, skip

    def mask(self, P):
        Q = (P - self.off) / self.sp
        row = np.floor(Q[..., 1] + .5)
        Q = Q.copy()
        Q[..., 0] += (row % 2) * .5
        Q[..., 2] += (row % 2) * .5
        cell = np.floor(Q + .5)
        d = Q - cell
        r = np.full(P.shape[:-1], self.r)
        if self.scatter or self.skip:
            # Organic spots: each cell's dot drifts, changes size, or is missing.
            h1 = _cell_hash(cell, 1.7)
            h2 = _cell_hash(cell, 5.3)
            h3 = _cell_hash(cell, 9.1)
            d = d - np.stack([(h1 - .5), (h2 - .5), (h3 - .5)], -1) * self.scatter
            r = r * (.7 + .6 * h3)
            r = np.where(h2 < self.skip, -1.0, r)
        return np.sqrt((d ** 2).sum(-1)) * self.sp <= r


class Paint:
    """Per-face painting context handed to marks."""

    def __init__(self, P, normal, part, density, palette, face='north', edges=None):
        self.P, self.normal, self.part, self.density, self.palette = P, normal, part, density, palette
        self.face = face
        if edges is not None:
            self.du, self.dr, self.dt, self.db = edges
        base = rgb(palette[part['material']].split('>')[0])
        self.rgb = np.broadcast_to(base, P.shape).copy()
        self.flat = np.zeros(P.shape[:-1])

    def colour(self, key):
        return rgb(self.palette.get(key, key) if not key.startswith('#') else key)

    def flat_colour(self, key):
        return 0

    def put(self, mask, key):
        if mask.any():
            self.rgb[mask] = self.colour(key)
            self.flat[mask] = 1


# ---------------------------------------------------------------------------- model

class Pet:
    def __init__(self, species, stage, palette, kinds=None, density=None):
        aliases = {"child": "baby", "juvenile": "medium", "young": "medium", "grown": "adult"}
        self.species = species
        self.stage = aliases.get(stage, stage)
        if self.stage not in STAGES:
            raise ValueError('stage must be baby, medium or adult')
        self.age = STAGES.index(self.stage)
        self.palette = dict(shine='#FFFFFF', outline='#2A1C22', blush='#F4A6B4', nose='#3B2A30', mouth='#4A2E33')
        self.palette.update(palette)
        self.kinds = dict(fur='fur', cream='fur', white='fur', dark='fur', inner='skin', nose='smooth',
                          gold='metal', gem='gem', leaf='leaf', cloth='cloth', claw='smooth', pad='smooth')
        self.kinds.update(kinds or {})
        self.bones = {'root': (None, [0, 0, 0]), 'body': ('root', [0, 0, 0])}
        self.parts, self.marks = [], []
        self.rboxes = []  # unrotated chamfered blocks, so eyes can claim a flat face panel
        self.density = density or (12.0, 10.5, 9.0)[self.age]
        self.scale = 1.0  # design units -> shipped units, applied by finish()
        self.serial = 0

    # stage helpers --------------------------------------------------------
    def v(self, baby, medium, adult):
        return (baby, medium, adult)[self.age]

    # rig -----------------------------------------------------------------
    def bone(self, name, parent, pivot):
        self.bones[name] = (parent, [round(float(v), 4) for v in pivot])
        return name

    def _group(self, bone):
        for prefix, group in (('head', 'head'), ('ear', 'ear'), ('leg', 'leg'), ('tail', 'tail'), ('wing', 'wing'),
                              ('arm', 'leg')):
            if bone.startswith(prefix):
                return group
        return 'body'

    def cube(self, bone, centre, size, mat, rot=None, origin=None, group=None, name='cube'):
        self.serial += 1
        p = {'name': f'{name}_{self.serial}', 'bone': bone, 'group': group or self._group(bone),
             'from': [round(float(c - s / 2), 4) for c, s in zip(centre, size)],
             'size': [round(float(max(.1, s)), 4) for s in size], 'material': mat}
        if rot is not None and any(abs(r) > 1e-6 for r in rot):
            p['rotation'] = [round(float(r), 4) for r in rot]
            p['origin'] = [round(float(o), 4) for o in (origin if origin is not None else centre)]
        self.parts.append(p)
        return p

    def rbox(self, bone, centre, size, mat, bevel=None, rot=None, origin=None, group=None, name='block'):
        """A block with all twelve edges chamfered: three crossed boxes."""
        w, h, d = size
        b = bevel if bevel is not None else min(size) * .14
        # Deep chamfers turn a block into a plus sign; keep the faces broad.
        b = min(b, min(size) * .3)
        if b <= .05:
            return [self.cube(bone, centre, size, mat, rot, origin, group, name)]
        o = origin if origin is not None else centre
        parts = [self.cube(bone, centre, (w, h - 2 * b, d - 2 * b), mat, rot, o, group, name),
                 self.cube(bone, centre, (w - 2 * b, h, d - 2 * b), mat, rot, o, group, name),
                 self.cube(bone, centre, (w - 2 * b, h - 2 * b, d), mat, rot, o, group, name)]
        if rot is None or not any(abs(r) > 1e-6 for r in rot):
            self.rboxes.append(dict(parts=parts, centre=tuple(float(c) for c in centre), size=(w, h, d), bevel=b))
        return parts

    def _flatten_eye_panels(self):
        """Shrink a head block's chamfer where a front-facing painted eye would spill onto the recessed bevel.

        A chamfered head's front is a smaller flat panel; an eye wider than that panel breaks across the step."""
        drop = set()
        for m in self.marks:
            if not isinstance(m, Eye) or abs(m.facing_dir[0]) > .05 or m.facing_dir[2] > -.9:
                continue
            ex, ey, ez = abs(m.c[0]), m.c[1], m.c[2]
            for box in self.rboxes:
                part = box['parts'][0]
                if part['group'] not in m.groups:
                    continue
                (cx, cy, cz), (w, h, d), b = box['centre'], box['size'], box['bevel']
                front = cz - d / 2
                if abs(front - ez) > .35:
                    continue
                dx = abs(ex - abs(cx))  # centred heads, or the bulb on the eye's own side
                if not (abs(ey - cy) < h / 2 and dx < w / 2):
                    continue
                need = min(b, w / 2 - (dx + m.w) - .1, h / 2 - (abs(ey - cy) + m.h) - .1)
                if need >= b - 1e-3:
                    continue
                need = max(0.0, need)
                p0, p1, p2 = box['parts']
                if need < .06:
                    p0['from'] = [round(c - s_ / 2, 4) for c, s_ in zip(box['centre'], box['size'])]
                    p0['size'] = [round(float(s_), 4) for s_ in box['size']]
                    drop.update((id(p1), id(p2)))
                else:
                    for p, sz in ((p0, (w, h - 2 * need, d - 2 * need)), (p1, (w - 2 * need, h, d - 2 * need)),
                                  (p2, (w - 2 * need, h - 2 * need, d))):
                        p['from'] = [round(c - s_ / 2, 4) for c, s_ in zip(box['centre'], sz)]
                        p['size'] = [round(float(s_), 4) for s_ in sz]
                box['bevel'] = need
        if drop:
            self.parts = [p for p in self.parts if id(p) not in drop]

    def beam(self, bone, a, b, width, mat, depth=None, bevel=0, group=None, name='beam', extend=0.0):
        a, b = np.array(a, float), np.array(b, float)
        delta = b - a
        length = float(np.linalg.norm(delta))
        if length < 1e-3:
            return []
        rot = [math.degrees(math.asin(max(-1, min(1, delta[2] / length)))), 0,
               math.degrees(math.atan2(-delta[0], delta[1]))]
        centre = (a + b) / 2
        size = (width, length + extend, depth or width)
        if bevel:
            return self.rbox(bone, centre, size, mat, bevel, rot, centre, group, name)
        return [self.cube(bone, centre, size, mat, rot, centre, group, name)]

    def mark(self, *marks):
        self.marks.extend(marks)

    # finish ----------------------------------------------------------------
    def vertices(self, p):
        lo = np.array(p['from'], float)
        size = np.array(p['size'], float)
        corners = np.array([[lo[0] + size[0] * i, lo[1] + size[1] * j, lo[2] + size[2] * k]
                            for i in (0, 1) for j in (0, 1) for k in (0, 1)])
        if 'rotation' in p:
            o = np.array(p['origin'], float)
            corners = (corners - o) @ rotation(p['rotation']).T + o
        return corners

    def finish(self):
        self._flatten_eye_panels()
        floor = min(self.vertices(p)[:, 1].min() for p in self.parts)
        shift = -floor
        if abs(shift) > 1e-4:
            for p in self.parts:
                p['from'][1] = round(p['from'][1] + shift, 4)
                if 'origin' in p:
                    p['origin'][1] = round(p['origin'][1] + shift, 4)
            for name, (parent, pivot) in list(self.bones.items()):
                if name != 'root':
                    self.bones[name] = (parent, [pivot[0], round(pivot[1] + shift, 4), pivot[2]])
        k = self.scale
        if abs(k - 1) > 1e-6:
            for p in self.parts:
                p['from'] = [round(v * k, 4) for v in p['from']]
                p['size'] = [round(v * k, 4) for v in p['size']]
                if 'origin' in p:
                    p['origin'] = [round(v * k, 4) for v in p['origin']]
            for name, (parent, pivot) in list(self.bones.items()):
                self.bones[name] = (parent, [round(v * k, 4) for v in pivot])
        top = max(self.vertices(p)[:, 1].max() for p in self.parts)
        return {'bones': self.bones, 'parts': self.parts, 'palette': self.palette, 'marks': self.marks,
                'kinds': self.kinds, 'density': self.density, 'shift': shift, 'scale': k,
                'height': round(top / 16, 3)}


# --------------------------------------------------------------------------- paint

def _pack(sizes, atlas, gutter=1):
    order = sorted(range(len(sizes)), key=lambda i: (-sizes[i][1], -sizes[i][0]))
    spots = [None] * len(sizes)
    x = y = shelf = 0
    for i in order:
        w, h = sizes[i][0] + 2 * gutter, sizes[i][1] + 2 * gutter
        if x + w > atlas:
            x, y, shelf = 0, y + shelf, 0
        if w > atlas or y + h > atlas:
            return None
        spots[i] = (x + gutter, y + gutter)
        x += w
        shelf = max(shelf, h)
    return spots


def _texture(ctx, kind, w, h, face, seed):
    """Material micro-texture as a brightness multiplier grid."""
    rng = np.random.default_rng(seed)
    f = np.ones((h, w))
    if kind == 'fur':
        f *= rng.choice([.985, 1, 1, 1, 1.012], size=(h, w))
        # Sparse short hanging strokes, a little darker or lighter than the coat.
        for _ in range(max(1, w * h // 34)):
            x, y = rng.integers(w), rng.integers(h)
            k = rng.integers(2, 4)
            f[y:y + k, x] *= rng.choice([.955, 1.04])
    elif kind == 'leaf':
        f *= rng.choice([.95, 1, 1.04], size=(h, w))
    elif kind == 'cloth':
        f *= rng.choice([.97, 1, 1.02], size=(h, w))
        f[1::3, :] *= .95
    elif kind == 'metal':
        if face not in ('up', 'down') and h >= 3:
            f[max(0, round(h * .25)), :] *= 1.22
            f[-1, :] *= .84
        f *= rng.choice([.98, 1, 1.02], size=(h, w))
    elif kind == 'gem':
        xs = np.abs(np.arange(w) - (w - 1) / 2) / max(1, w / 2)
        f *= np.where(xs < .3, 1.12, np.where(xs > .72, .9, 1.0))[None, :]
        if w >= 3 and h >= 3:
            f[h // 4, w // 2] *= 1.35
    elif kind == 'scale':
        yy, xx = np.mgrid[0:h, 0:w]
        f *= np.where(((yy % 4 == 3) | ((xx + (yy // 4) * 2) % 4 == 0) & (yy % 4 == 0)), .9, 1.0)
    elif kind == 'shell':
        f *= rng.choice([.97, 1, 1.03], size=(h, w))
    else:
        f *= rng.choice([.985, 1, 1.015], size=(h, w))
    return f


def paint(sculpt, species, stage, atlas_sizes=(1024,)):
    """Return (texture, per-part face UVs, density) for an atelier sculpt."""
    parts, palette = sculpt['parts'], sculpt['palette']
    marks, kinds = sculpt['marks'], sculpt['kinds']
    shift = np.array([0, sculpt.get('shift', 0), 0], float)
    k = sculpt.get('scale', 1.0)
    height = max(1.0, sculpt['height'] * 16)
    # Each bone is one mass: light falls across it from top to bottom.
    spans = {}
    for p in parts:
        lo = np.array(p['from'], float)
        hi = lo + np.array(p['size'], float)
        corners = np.array([[a, b, c] for a in (lo[0], hi[0]) for b in (lo[1], hi[1]) for c in (lo[2], hi[2])])
        if 'rotation' in p:
            o = np.array(p['origin'], float)
            corners = (corners - o) @ rotation(p['rotation']).T + o
        key = p['bone']
        a, b = spans.get(key, (1e9, -1e9))
        spans[key] = (min(a, corners[:, 1].min()), max(b, corners[:, 1].max()))
    light_tint, shadow_tint = np.array([1.025, 1.01, .975]), np.array([.93, .92, 1.0])
    target = sculpt['density']
    frames = []
    for p in parts:
        lo = np.array(p['from'], float)
        hi = lo + np.array(p['size'], float)
        frames.append([face_frame(f, lo, hi) for f in FACES])
    chosen = None
    for atlas in atlas_sizes:
        for density in (target, target * .85, target * .7, target * .55, target * .4):
            sizes = []
            for fr in frames:
                for (o, U, V, n) in fr:
                    sizes.append((max(1, round(np.linalg.norm(U) * density)), max(1, round(np.linalg.norm(V) * density))))
            spots = _pack(sizes, atlas)
            if spots is not None:
                chosen = (atlas, density, sizes, spots)
                break
        if chosen:
            break
    if not chosen:
        raise ValueError(f'{species}/{stage}: faces do not fit')
    atlas, density, sizes, spots = chosen
    image = np.zeros((atlas, atlas, 4), np.uint8)
    uvs = []
    for index, p in enumerate(parts):
        R = rotation(p['rotation']) if 'rotation' in p else np.eye(3)
        origin = np.array(p.get('origin', [0, 0, 0]), float)
        kind = kinds.get(p['material'], 'plain')
        glow = kind == 'glow'
        faces = {}
        for number, face in enumerate(FACES):
            (w, h), (x0, y0) = sizes[index * 6 + number], spots[index * 6 + number]
            o, U, V, n = frames[index][number]
            o, U, V = np.array(o, float), np.array(U, float), np.array(V, float)
            us = (np.arange(w) + .5) / w
            vs = (np.arange(h) + .5) / h
            P = o[None, None, :] + us[None, :, None] * U[None, None, :] + vs[:, None, None] * V[None, None, :]
            if 'rotation' in p:
                P = (P - origin) @ R.T + origin
            normal = R @ np.array(n, float)
            P_design = P / k - shift  # marks are authored before grounding and scaling
            lu, lv = np.linalg.norm(U), np.linalg.norm(V)
            du = np.broadcast_to((us * lu)[None, :], (h, w))
            dt = np.broadcast_to((vs * lv)[:, None], (h, w))
            ctx = Paint(P_design, normal, p, density * k, palette, face, (du / k, (lu - du) / k, dt / k, (lv - dt) / k))
            for m in sorted(marks, key=lambda m: getattr(m, 'layer', 0)):
                m.apply(ctx)
            colour = ctx.rgb
            if not glow:
                tex = _texture(ctx, kind, w, h, face, zlib.crc32(f'{species}/{stage}/{index}/{face}'.encode()))
                mult = tex
                # Ground occlusion: lower texels a touch darker, continuous across blocks.
                y = P[..., 1]
                mult = mult * (0.93 + 0.07 * np.clip(y / (0.42 * height), 0, 1))
                lo_y, hi_y = spans[p['bone']]
                t = np.clip((y - lo_y) / max(.5, hi_y - lo_y), 0, 1)[..., None]
                tint = shadow_tint * (1 - t) + light_tint * t
                mult = mult[..., None] * tint * (.94 + .1 * t)
                if normal[1] < -.5:
                    mult = mult * .88
                elif normal[1] > .5:
                    mult = mult * 1.02
                elif h >= 5 and w >= 3:
                    # A soft lit lip along the top edge of side faces, and a seated lower edge.
                    mult[0, :] *= 1.045
                    mult[-1, :] *= .94
                # Painted features (eyes, mouths) keep their drawn values.
                mult = np.where(ctx.flat[..., None] > 0, 1.0, mult)
                colour = colour * mult
            block = np.clip(colour, 0, 255).astype(np.uint8)
            image[y0:y0 + h, x0:x0 + w, :3] = block
            image[y0:y0 + h, x0:x0 + w, 3] = 255
            # Bleed one texel so mipmaps never pull in a neighbour.
            image[y0 - 1, x0:x0 + w] = image[y0, x0:x0 + w]
            image[y0 + h, x0:x0 + w] = image[y0 + h - 1, x0:x0 + w]
            image[y0 - 1:y0 + h + 1, x0 - 1] = image[y0 - 1:y0 + h + 1, x0]
            image[y0 - 1:y0 + h + 1, x0 + w] = image[y0 - 1:y0 + h + 1, x0 + w - 1]
            faces[face] = [x0, y0, x0 + w, y0 + h]
        uvs.append(faces)
    return Image.fromarray(image, 'RGBA'), uvs, density


# ------------------------------------------------------------------ anatomy kit

def quad_legs(s, xw, zf, zb, hip_y, length, thick, mat='fur', paw=None, paw_len=None, back_thick=None,
              thigh=None, front_thick=None, bevel=None):
    """Four articulated legs. Bones leg_fl/fr/bl/br with _lower knees (the walk cycle expects these)."""
    paw = paw or mat
    for end, z in (('f', zf), ('b', zb)):
        t = (front_thick or thick) if end == 'f' else (back_thick or thick)
        for side, sx in (('l', -1), ('r', 1)):
            name = f'leg_{end}{side}'
            x = sx * xw
            hip = (x, hip_y, z)
            knee_y = hip_y - length * .5
            s.bone(name, 'body', hip)
            lower = s.bone(name + '_lower', name, (x, knee_y, z))
            top = hip_y + t * .6
            s.rbox(name, (x, (top + knee_y - .3) / 2, z), (t, top - knee_y + .6, t), mat, bevel if bevel is not None else t * .16,
                   group='leg', name='leg_upper')
            if thigh and end == 'b':
                tw, th, td = thigh
                s.rbox(name, (x + sx * .1, hip_y + th * .12, z + td * .08), (tw, th, td), mat, min(tw, th, td) * .2,
                       group='leg', name='haunch')
            ph = max(.9, t * .42)
            pl = paw_len or t * 1.25
            s.rbox(lower, (x, (knee_y + .3 + ph) / 2, z), (t * .92, knee_y + .3 - ph, t * .92), mat, t * .14,
                   group='leg', name='leg_lower')
            s.rbox(lower, (x, ph / 2, z - (pl - t) / 2), (t * 1.08, ph, pl), paw, min(ph, t) * .3,
                   group='paw', name='paw')


def chain(s, base, points, widths, mats, depth=None, bevel=None, parent='body', group='tail', joints=True):
    """An articulated chain (tails, necks of feathers). Bones base, base_1.., base_tip."""
    names = []
    n = len(points) - 1
    for i in range(n):
        name = base if i == 0 else (base + '_tip' if i == n - 1 else f'{base}_{i}')
        par = parent if i == 0 else names[-1]
        s.bone(name, par, points[i])
        names.append(name)
        w = widths[i] if isinstance(widths, (list, tuple)) else widths
        d = (depth[i] if isinstance(depth, (list, tuple)) else depth) if depth is not None else w
        mat = mats[i] if isinstance(mats, (list, tuple)) else mats
        s.beam(name, points[i], points[i + 1], w, mat, d, bevel if bevel is not None else min(w, d) * .18,
               group=group, name=base + '_seg', extend=min(w, d) * .35)
    return names


def eye_pair_marks(s, **kw):
    s.mark(Eye(**kw))


# ----------------------------------------------------------------- render (preview)

def _shade(normal):
    n = normal / max(1e-9, np.linalg.norm(normal))
    up = n[1] ** 2 * (1.0 if n[1] > 0 else .55)
    return n[0] ** 2 * .68 + n[2] ** 2 * .84 + up


def render_model(model, size=560, yaw=32, elevation=14, span=None, background=(23, 21, 34), clip=None, time=0.0,
                 transformed=None):
    """Render a bbmodel dict with Minecraft UV orientation and block-style face shading."""
    import io, base64
    tex = np.asarray(Image.open(io.BytesIO(base64.b64decode(model['textures'][0]['source'].split(',')[1]))).convert('RGBA'))
    elements = {e['uuid']: e for e in model['elements']}
    cubes = []

    def walk(group, matrix):
        if group['name'] == 'hitbox':
            return
        for child in group['children']:
            if isinstance(child, dict):
                walk(child, matrix if transformed is None else transformed.get(child['name'], matrix))
            else:
                cubes.append((elements[child], matrix))
    for g in model['outliner']:
        walk(g, np.eye(4))
    az, el = math.radians(yaw), math.radians(elevation)
    # Camera in front-left of the creature, which faces -Z.
    cam = np.array([math.sin(az) * math.cos(el), math.sin(el), -math.cos(az) * math.cos(el)])
    fwd = -cam
    right = np.cross(fwd, [0, 1, 0])
    right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    polys = []
    pts_all = []
    for e, matrix in cubes:
        lo, hi = np.array(e['from'], float), np.array(e['to'], float)
        R = rotation(e.get('rotation', [0, 0, 0]))
        o = np.array(e.get('origin', [0, 0, 0]), float)
        for face in FACES:
            fo, U, V, n = face_frame(face, lo, hi)
            fo, U, V = np.array(fo, float), np.array(U, float), np.array(V, float)
            corners = np.array([fo, fo + U, fo + U + V, fo + V])
            corners = (corners - o) @ R.T + o
            corners = corners @ matrix[:3, :3].T + matrix[:3, 3]
            normal = matrix[:3, :3] @ (R @ np.array(n, float))
            pts_all.append(corners)
            if normal @ cam <= 1e-6:
                continue
            polys.append((corners, normal, e['faces'][face]['uv'], e.get('shade', True)))
    allp = np.concatenate(pts_all)
    sx, sy = allp @ right, allp @ up
    if span is None:
        span = max(sx.max() - sx.min(), sy.max() - sy.min()) * 1.1
    scale = (size - 60) / span
    cx = (sx.max() + sx.min()) / 2
    floor = sy.min()
    base_y = size * .9
    canvas = np.zeros((size, size, 3), float)
    canvas[:] = background
    depth = np.full((size, size), 1e9)
    for corners, normal, uv, shade in polys:
        scr = np.stack([(corners @ right - cx) * scale + size / 2, base_y - (corners @ up - floor) * scale], -1)
        dz = corners @ fwd
        a = scr[0]
        b = scr[1] - a
        c = scr[3] - a
        left, top = np.maximum(0, np.floor(scr.min(0)).astype(int))
        rgt, bot = np.minimum(size, np.ceil(scr.max(0) + 1).astype(int))
        if rgt <= left or bot <= top:
            continue
        det = b[0] * c[1] - b[1] * c[0]
        if abs(det) < 1e-6:
            continue
        px = np.arange(left, rgt)[None, :] + .5 - a[0]
        py = np.arange(top, bot)[:, None] + .5 - a[1]
        u = (px * c[1] - py * c[0]) / det
        v = (py * b[0] - px * b[1]) / det
        z = dz[0] + u * (dz[1] - dz[0]) + v * (dz[3] - dz[0])
        region = depth[top:bot, left:rgt]
        mask = (u >= 0) & (u <= 1) & (v >= 0) & (v <= 1) & (z < region)
        tx = np.clip((uv[0] + u * (uv[2] - uv[0])).astype(int), 0, tex.shape[1] - 1)
        ty = np.clip((uv[1] + v * (uv[3] - uv[1])).astype(int), 0, tex.shape[0] - 1)
        texels = tex[ty, tx]
        mask &= texels[..., 3] > 0
        f = (.25 + .85 * _shade(normal)) if shade else 1.0
        canvas[top:bot, left:rgt][mask] = np.clip(texels[..., :3] * f, 0, 255)[mask]
        region[mask] = z[mask]
    return Image.fromarray(canvas.astype(np.uint8))
