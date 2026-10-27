"""Cel-painted ModelEngine R4 rigs: cuboids whose faces are baked from paintings.

Every textured face is baked texel by texel. Each texel's rest-pose model position, the face
normal, the face coordinates and the part's own local coordinates go to the face's painter; the
result is supersampled, alpha-binarised and packed into one atlas. Painters can

* project a shared front/back/side illustration (`Project`), so stacked cuboids read as one
  continuous painted surface, the way the e2.148 Seraph does;
* map a painting onto a plane in the part's own frame (`PlaneLocal`), so both sides of a thin
  alpha-cut sheet keep the same silhouette;
* shade procedurally (`Shader`).

Faces carry their lighting in the paint (soft key light from the front and above) and are exported
with shade off, so Java ModelEngine shows the painted colours rather than vanilla item shading.
Geometry is in Blockbench units with the native front facing -Z; +X is the model's own right side.
"""
import base64
import hashlib
import io
import math
import uuid

import numpy as np
from PIL import Image, ImageDraw

FACES = ('north', 'south', 'east', 'west', 'up', 'down')
NORMALS = {'north': (0, 0, -1), 'south': (0, 0, 1), 'west': (-1, 0, 0), 'east': (1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}
# Minecraft FaceInfo corners as lo/hi selectors per axis: (u0, v0), (u1, v0) and (u0, v1).
CORNERS = {
    'north': ((1, 1, 0), (0, 1, 0), (1, 0, 0)),
    'south': ((0, 1, 1), (1, 1, 1), (0, 0, 1)),
    'west': ((0, 1, 0), (0, 1, 1), (0, 0, 0)),
    'east': ((1, 1, 1), (1, 1, 0), (1, 0, 1)),
    'up': ((0, 1, 0), (1, 1, 0), (0, 1, 1)),
    'down': ((0, 0, 1), (1, 0, 1), (0, 0, 0)),
}
FACE_AXES = {'north': (0, 1), 'south': (0, 1), 'east': (2, 1), 'west': (2, 1), 'up': (0, 2), 'down': (0, 2)}


# --------------------------------------------------------------------------- maths
def rot(angles):
    """Blockbench/ME element and bone order, degrees: R = Rz @ Ry @ Rx."""
    x, y, z = np.radians(angles)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    return (np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]) @ np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
            @ np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]]))


def euler(r):
    """Inverse of rot(); degrees."""
    y = math.asin(float(np.clip(-r[2, 0], -1, 1)))
    if abs(math.cos(y)) > 1e-7:
        x, z = math.atan2(r[2, 1], r[2, 2]), math.atan2(r[1, 0], r[0, 0])
    else:
        x, z = math.atan2(-r[1, 2], r[1, 1]), 0.0
    return [math.degrees(x), math.degrees(y), math.degrees(z)]


def unit(v):
    v = np.asarray(v, float)
    return v / np.linalg.norm(v)


def frame(y_axis, z_hint):
    """Rotation whose local +Y follows y_axis and whose local +Z leans toward z_hint."""
    y = unit(y_axis)
    z = np.asarray(z_hint, float) - y * (np.asarray(z_hint, float) @ y)
    if np.linalg.norm(z) < 1e-6:
        z = np.array([0, 0, 1.0]) - y * y[2]
    z = unit(z)
    return np.column_stack((np.cross(y, z), y, z))


def smooth(e0, e1, x):
    t = np.clip((np.asarray(x, float) - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def mix(a, b, t):
    """Blend colours; a scalar or HxW weight broadcasts over the RGB channel."""
    a, b, t = np.asarray(a, float), np.asarray(b, float), np.asarray(t, float)
    if t.ndim and t.shape[-1:] != (3,):
        t = t[..., None]
    return a * (1 - t) + b * t


def rgb(value):
    """'#rrggbb' to floats in 0..1."""
    value = value.lstrip('#')
    return np.array([int(value[i:i + 2], 16) / 255 for i in (0, 2, 4)])


def bezier(points, n=24):
    pts = [np.asarray(p, float) for p in points]
    k = len(pts) - 1
    return [sum(math.comb(k, i) * (1 - t) ** (k - i) * t ** i * p for i, p in enumerate(pts)) for t in np.linspace(0, 1, n)]


# --------------------------------------------------------------------------- canvases
class Canvas:
    """A painting over a rectangle of model space. `flip` puts the high end of `u` at the image left,
    as a front view does (the model's +X appears on the viewer's left)."""

    def __init__(self, u, v, ppu, flip=False):
        self.u0, self.u1 = u
        self.v0, self.v1 = v
        self.ppu, self.flip = float(ppu), flip
        self.W = max(1, int(round((self.u1 - self.u0) * ppu)))
        self.H = max(1, int(round((self.v1 - self.v0) * ppu)))
        self.rgba = np.zeros((self.H, self.W, 4), np.float32)

    # coordinates ------------------------------------------------------------
    def px(self, u, v):
        x = ((self.u1 - u) if self.flip else (u - self.u0)) * self.ppu
        return x, (self.v1 - v) * self.ppu

    def grid(self):
        x = (np.arange(self.W) + .5) / self.ppu
        y = (np.arange(self.H) + .5) / self.ppu
        U = (self.u1 - x) if self.flip else (self.u0 + x)
        V = self.v1 - y
        return np.meshgrid(U, V)

    # masks ------------------------------------------------------------------
    def _mask(self, draw_fn, ss=2):
        image = Image.new('L', (self.W * ss, self.H * ss), 0)
        draw_fn(ImageDraw.Draw(image), ss)
        if ss > 1:
            image = image.resize((self.W, self.H), Image.Resampling.BOX)
        return np.asarray(image, np.float32) / 255

    def poly_mask(self, pts, ss=2):
        def draw(d, k):
            d.polygon([tuple(c * k for c in self.px(*p)) for p in pts], fill=255)
        return self._mask(draw, ss)

    def line_mask(self, pts, width, ss=2, caps=True):
        def draw(d, k):
            w = max(1, int(round(width * self.ppu * k)))
            q = [tuple(c * k for c in self.px(*p)) for p in pts]
            d.line(q, fill=255, width=w, joint='curve')
            if caps:
                for x, y in (q[0], q[-1]):
                    d.ellipse((x - w / 2, y - w / 2, x + w / 2, y + w / 2), fill=255)
        return self._mask(draw, ss)

    def ellipse_mask(self, c, r, ss=2):
        def draw(d, k):
            (x0, y0), (x1, y1) = self.px(c[0] - r[0], c[1] + r[1]), self.px(c[0] + r[0], c[1] - r[1])
            d.ellipse((min(x0, x1) * k, min(y0, y1) * k, max(x0, x1) * k, max(y0, y1) * k), fill=255)
        return self._mask(draw, ss)

    # compositing ------------------------------------------------------------
    def put(self, mask, colour, alpha=1.0):
        """Paint `colour` (rgb or an HxWx3 array) over the canvas through `mask` (0..1)."""
        a = np.clip(np.asarray(mask, np.float32) * alpha, 0, 1)
        if a.ndim == 0:
            a = np.full(self.rgba.shape[:2], float(a), np.float32)
        col = np.broadcast_to(np.asarray(colour, np.float32), self.rgba.shape[:2] + (3,))
        da = self.rgba[..., 3]
        oa = a + da * (1 - a)
        safe = np.maximum(oa, 1e-6)[..., None]
        self.rgba[..., :3] = (col * a[..., None] + self.rgba[..., :3] * (da * (1 - a))[..., None]) / safe
        self.rgba[..., 3] = oa

    def tint(self, mask, colour, amount=1.0):
        """Blend existing colour toward `colour` without changing alpha."""
        a = np.clip(np.asarray(mask, np.float32) * amount, 0, 1)
        if a.ndim == 0:
            a = np.full(self.rgba.shape[:2], float(a), np.float32)
        col = np.broadcast_to(np.asarray(colour, np.float32), self.rgba.shape[:2] + (3,))
        self.rgba[..., :3] = self.rgba[..., :3] * (1 - a[..., None]) + col * a[..., None]

    def erase(self, mask):
        self.rgba[..., 3] *= 1 - np.clip(mask, 0, 1)

    def keep(self, mask):
        self.rgba[..., 3] *= np.clip(mask, 0, 1)

    def poly(self, pts, colour, alpha=1.0):
        self.put(self.poly_mask(pts), colour, alpha)

    def line(self, pts, colour, width, alpha=1.0):
        self.put(self.line_mask(pts, width), colour, alpha)

    def ellipse(self, c, r, colour, alpha=1.0):
        self.put(self.ellipse_mask(c, r), colour, alpha)

    def image(self):
        return Image.fromarray(np.clip(self.rgba * 255 + .5, 0, 255).astype(np.uint8), 'RGBA')


def blur(mask, radius_px):
    from PIL import ImageFilter
    image = Image.fromarray(np.clip(np.asarray(mask) * 255, 0, 255).astype(np.uint8))
    return np.asarray(image.filter(ImageFilter.GaussianBlur(radius_px)), np.float32) / 255


def sample(canvas, u, v):
    """Bilinear sample of a canvas at model coordinates (u, v); returns (..., 4), straight alpha."""
    x, y = canvas.px(u, v)
    x, y = np.asarray(x) - .5, np.asarray(y) - .5
    x0, y0 = np.floor(x).astype(int), np.floor(y).astype(int)
    fx, fy = (x - x0)[..., None], (y - y0)[..., None]
    H, W = canvas.H, canvas.W

    def at(yy, xx):
        inside = ((xx >= 0) & (xx < W) & (yy >= 0) & (yy < H))[..., None]
        p = canvas.rgba[np.clip(yy, 0, H - 1), np.clip(xx, 0, W - 1)]
        p = np.concatenate([p[..., :3] * p[..., 3:4], p[..., 3:4]], axis=-1)
        return np.where(inside, p, 0)
    a = at(y0, x0) * (1 - fx) + at(y0, x0 + 1) * fx
    b = at(y0 + 1, x0) * (1 - fx) + at(y0 + 1, x0 + 1) * fx
    out = a * (1 - fy) + b * fy
    alpha = out[..., 3:4]
    return np.concatenate([out[..., :3] / np.maximum(alpha, 1e-6), alpha], axis=-1)


# --------------------------------------------------------------------------- painters
class Painter:
    emissive = False

    def __call__(self, ctx):
        raise NotImplementedError


class Solid(Painter):
    def __init__(self, colour, emissive=False):
        self.colour, self.emissive = np.asarray(colour, float), emissive

    def __call__(self, ctx):
        out = np.ones(ctx['shape'] + (4,))
        out[..., :3] = self.colour
        return out


class Shader(Painter):
    """fn(ctx) -> HxWx4 (or HxWx3, opaque)."""

    def __init__(self, fn, emissive=False):
        self.fn, self.emissive = fn, emissive

    def __call__(self, ctx):
        out = np.asarray(self.fn(ctx), float)
        if out.shape[-1] == 3:
            out = np.concatenate([out, np.ones(out.shape[:-1] + (1,))], axis=-1)
        return out


class Project(Painter):
    """Samples a canvas by projecting the texel's model position along one axis.
    axis 'z': canvas u = x, v = y (front and back views); 'x': u = z, v = y; 'y': u = x, v = z."""

    def __init__(self, canvas, axis='z', emissive=False):
        self.canvas, self.axis, self.emissive = canvas, axis, emissive

    def __call__(self, ctx):
        P = ctx['P']
        if self.axis == 'z':
            return sample(self.canvas, P[..., 0], P[..., 1])
        if self.axis == 'x':
            return sample(self.canvas, P[..., 2], P[..., 1])
        return sample(self.canvas, P[..., 0], P[..., 2])


class PlaneLocal(Painter):
    """Maps a canvas onto the part's own X/Y frame (u = local x across, v = local y up, both from the
    part's low corner), so the two broad faces of a sheet share one silhouette. `back` may give a
    second painting for the south side; `axes` picks other local axes (e.g. (2, 1) for side faces)."""

    def __init__(self, canvas, back=None, axes=(0, 1), emissive=False, centered=False):
        self.canvas, self.back, self.axes, self.emissive, self.centered = canvas, back, axes, emissive, centered

    def __call__(self, ctx):
        L = ctx['L']
        canvas = self.back if (self.back is not None and ctx['face'] == 'south') else self.canvas
        u, v = L[..., self.axes[0]], L[..., self.axes[1]]
        if self.centered:
            u, v = u - ctx['size'][self.axes[0]] / 2, v - ctx['size'][self.axes[1]] / 2
        return sample(canvas, u, v)


class ByFace(Painter):
    """Chooses a painter by the face's rest-pose normal: keys 'front' (-Z), 'back', 'right' (+X),
    'left' (-X), 'top', 'bottom', with 'side' and 'default' as fallbacks."""

    def __init__(self, emissive=False, **painters):
        self.painters, self.emissive = painters, emissive

    def pick(self, n):
        axis = int(np.argmax(np.abs(n)))
        key = (('right', 'left'), ('top', 'bottom'), ('back', 'front'))[axis][0 if n[axis] > 0 else 1]
        p = self.painters
        if key in p:
            return p[key]
        if key in ('right', 'left') and 'side' in p:
            return p['side']
        return p.get('default')

    def __call__(self, ctx):
        painter = self.pick(ctx['N'])
        if painter is None:
            return np.zeros(ctx['shape'] + (4,))
        out = painter(ctx)
        ctx['emissive'] = painter.emissive
        return out


# --------------------------------------------------------------------------- rig
class Rig:
    def __init__(self, name):
        self.name = name
        self.bones = {}
        self.parts = []

    def bone(self, name, parent, origin):
        assert name not in self.bones and (parent is None or parent in self.bones), name
        self.bones[name] = (parent, [float(v) for v in origin])

    def cube(self, bone, center, size, faces, r=None, name=None, ppu=12.0, shade=False, pivot=None):
        """faces: a painter for every face, or {face: painter}; missing faces stay untextured."""
        assert bone in self.bones, bone
        size = np.maximum(np.asarray(size, float), .01)
        center = np.asarray(center, float)
        r = np.eye(3) if r is None else np.asarray(r, float)
        if isinstance(faces, Painter):
            faces = {f: faces for f in FACES}
        # Snap the rotation through Euler angles so baking uses exactly what is exported.
        angles = [round(a, 3) for a in euler(r)]
        if not any(abs(a) > 1e-6 for a in angles):
            angles = [0.0, 0.0, 0.0]
        r = rot(angles)
        pivot = center if pivot is None else np.asarray(pivot, float)
        self.parts.append(dict(bone=bone, center=center, size=size, r=r, angles=angles, pivot=pivot,
                               faces=faces, name=name or bone, ppu=float(ppu), shade=shade))
        return self.parts[-1]

    def box(self, bone, lo, hi, faces, angles=(0, 0, 0), **kw):
        lo, hi = np.asarray(lo, float), np.asarray(hi, float)
        return self.cube(bone, (lo + hi) / 2, hi - lo, faces, rot(angles), **kw)

    def beam(self, bone, a, b, width, depth, faces, z_hint=(0, 0, -1), extend=(0, 0), **kw):
        """A cuboid whose local +Y runs from a to b (extended past each end by `extend`)."""
        a, b = np.asarray(a, float), np.asarray(b, float)
        d = unit(b - a)
        a, b = a - d * extend[0], b + d * extend[1]
        return self.cube(bone, (a + b) / 2, (width, float(np.linalg.norm(b - a)), depth), faces, frame(b - a, z_hint), **kw)

    def sheet(self, bone, center, width, height, painter, r=None, thickness=.06, edges=False, **kw):
        """A thin two-sided plane in its local X/Y; `painter` is usually PlaneLocal."""
        faces = {'north': painter, 'south': painter}
        if edges:
            faces.update({f: painter for f in ('east', 'west', 'up', 'down')})
        return self.cube(bone, center, (width, height, thickness), faces, r, **kw)


# --------------------------------------------------------------------------- baking
def light(n):
    """ModelEngine ignores the shade flag, so Minecraft's own entity lighting shades every face in
    game; the paint only darkens undersides a little so they never read brighter than the sides."""
    n = unit(n)
    return 1.0 - .12 * max(0.0, -n[1])


def face_grid(part, face, ss):
    lo, hi = part['center'] - part['size'] / 2, part['center'] + part['size'] / 2
    ua, va = FACE_AXES[face]
    ppu = part['ppu']
    w = max(1, int(round(part['size'][ua] * ppu)))
    h = max(1, int(round(part['size'][va] * ppu)))
    sel = lambda c: np.array([(lo, hi)[c[i]][i] for i in range(3)])
    c00, c10, c01 = (sel(c) for c in CORNERS[face])
    U, V = np.meshgrid((np.arange(w * ss) + .5) / (w * ss), (np.arange(h * ss) + .5) / (h * ss))
    local = c00 + U[..., None] * (c10 - c00) + V[..., None] * (c01 - c00)
    R, pivot = part['r'], part['pivot']
    P = (local - pivot) @ R.T + pivot
    N = R @ np.array(NORMALS[face], float)
    return dict(P=P, N=N, U=U, V=V, L=local - lo, size=part['size'], face=face, part=part, shape=U.shape,
                ppu=ppu * ss), w, h


def bake_face(part, face, ss=3):
    ctx, w, h = face_grid(part, face, ss)
    painter = part['faces'][face]
    rgba = np.asarray(painter(ctx), float)
    a = rgba[..., 3:4]
    pm = np.concatenate([rgba[..., :3] * a, a], axis=-1).reshape(h, ss, w, ss, 4).mean(axis=(1, 3))
    alpha = pm[..., 3]
    colour = pm[..., :3] / np.maximum(alpha, 1e-6)[..., None]
    if not (painter.emissive or ctx.get('emissive')):
        colour = colour * light(ctx['N'])
    solid = alpha >= .5
    if not solid.any():
        return None
    tile = np.zeros((h, w, 4), np.uint8)
    tile[..., :3] = np.clip(colour * 255 + .5, 0, 255).astype(np.uint8)
    tile[..., 3] = np.where(solid, 255, 0)
    if not solid.all():
        tile = _bleed(tile, solid)
    return tile


def _bleed(tile, solid, steps=6):
    """Spread opaque colour into cut-out texels so mipmaps never fringe."""
    rgb_ = tile[..., :3].astype(float)
    have = solid.copy()
    for _ in range(steps):
        if have.all():
            break
        acc = np.zeros_like(rgb_)
        cnt = np.zeros(have.shape)
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            sh = np.roll(have, (dy, dx), axis=(0, 1))
            acc += np.roll(rgb_, (dy, dx), axis=(0, 1)) * sh[..., None]
            cnt += sh
        grow = ~have & (cnt > 0)
        rgb_[grow] = acc[grow] / cnt[grow][..., None]
        have |= grow
    if not have.all():
        rgb_[~have] = rgb_[have].mean(axis=0)
    out = tile.copy()
    out[..., :3] = np.clip(rgb_ + .5, 0, 255).astype(np.uint8)
    return out


def pack(tiles, size, gutter=2):
    """Skyline packing, tallest first; returns {key: (x, y)} or None when the atlas is full."""
    skyline = np.zeros(size, int)
    placed = {}
    for key, tile in sorted(tiles.items(), key=lambda kv: (-kv[1].shape[0], -kv[1].shape[1], kv[0])):
        h, w = tile.shape[:2]
        gw, gh = w + 2 * gutter, h + 2 * gutter
        best = None
        for x in range(0, size - gw + 1, 2):
            y = int(skyline[x:x + gw].max())
            if y + gh <= size and (best is None or y < best[1]):
                best = (x, y)
        if best is None:
            return None
        x, y = best
        skyline[x:x + gw] = y + gh
        placed[key] = (x + gutter, y + gutter)
    return placed


def bake(rig, size=2048, gutter=2, ss=3):
    """Bakes every textured face; returns (atlas RGBA image, {(part index, face): uv}, stats)."""
    scale = 1.0
    while True:
        tiles, refs = {}, []
        for index, part in enumerate(rig.parts):
            original = part['ppu']
            part['ppu'] = original * scale
            for face in FACES:
                if part['faces'].get(face) is None:
                    continue
                tile = bake_face(part, face, ss)
                if tile is None:
                    continue
                tag = bytes(str(tile.shape), 'ascii')
                key = hashlib.sha1(tile.tobytes() + tag).hexdigest()
                mirror = hashlib.sha1(np.ascontiguousarray(tile[:, ::-1]).tobytes() + tag).hexdigest()
                if key in tiles:
                    refs.append((index, face, key, False))
                elif mirror in tiles:
                    refs.append((index, face, mirror, True))
                else:
                    tiles[key] = tile
                    refs.append((index, face, key, False))
            part['ppu'] = original
        placed = pack(tiles, size, gutter)
        if placed is not None:
            break
        scale *= .93
    atlas = np.zeros((size, size, 4), np.uint8)
    for key, (x, y) in placed.items():
        tile = tiles[key]
        h, w = tile.shape[:2]
        atlas[y - gutter:y + h + gutter, x - gutter:x + w + gutter] = np.pad(tile, ((gutter, gutter), (gutter, gutter), (0, 0)), mode='edge')
    uvs = {}
    for index, face, key, flipped in refs:
        x, y = placed[key]
        h, w = tiles[key].shape[:2]
        uvs[(index, face)] = [x + w, y, x, y + h] if flipped else [x, y, x + w, y + h]
    used = sum(t.shape[0] * t.shape[1] for t in tiles.values())
    return Image.fromarray(atlas, 'RGBA'), uvs, dict(scale=scale, tiles=len(tiles), coverage=used / size / size)


def quantize(image, colours=256):
    """Palette PNG with binary alpha (imagequant when available): much smaller packs."""
    try:
        import imagequant
        return imagequant.quantize_pil_image(image, dithering_level=0.5, max_colors=colours, min_quality=0, max_quality=95)
    except Exception:
        return image.quantize(colours, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE)


def build(rig, identifier, hitbox, meta, size=2048, ss=3, palette=True):
    """Assemble a self-contained R4 free-format blueprint (animations left empty); returns
    (model, atlas image, stats). `displays` counts the bone/rotation groups ModelEngine renders."""
    def uid(label):
        return str(uuid.uuid5(uuid.NAMESPACE_URL, f'evergarden/{identifier}/' + label))
    atlas, uvs, stats = bake(rig, size, ss=ss)
    elements, children = [], {name: [] for name in rig.bones}
    displays = set()
    for index, part in enumerate(rig.parts):
        if not any((index, f) in uvs for f in FACES):
            continue
        lo, hi = part['center'] - part['size'] / 2, part['center'] + part['size'] / 2
        faces = {}
        for face in FACES:
            if (index, face) in uvs:
                faces[face] = dict(uv=[round(v, 3) for v in uvs[(index, face)]], texture=0)
            else:
                faces[face] = dict(uv=[0, 0, 0, 0], texture=None)
        e = dict(name=f"{part['name']}_{index}", type='cube', uuid=uid(f'cube/{index}'),
                 **{'from': [round(float(v), 5) for v in lo]}, to=[round(float(v), 5) for v in hi],
                 origin=[round(float(v), 5) for v in part['pivot']], rotation=[a + 0.0 for a in part['angles']],
                 box_uv=False, faces=faces, visibility=True, export=True, shade=bool(part['shade']), autouv=0, color=0)
        elements.append(e)
        children[part['bone']].append(e['uuid'])
        displays.add((part['bone'], tuple(e['rotation'])))

    def group(name):
        return dict(name=name, uuid=uid('bone/' + name), origin=rig.bones[name][1], rotation=[0, 0, 0],
                    export=True, visibility=True, color=0,
                    children=children[name] + [group(n) for n, (parent, _) in rig.bones.items() if parent == name])
    roots = [n for n, (parent, _) in rig.bones.items() if parent is None]
    hit = uid('hitbox/cube')
    elements.append(dict(name='hitbox', uuid=hit, type='cube', **{'from': hitbox[0]}, to=hitbox[1], origin=[0, 0, 0], faces={}))
    image = quantize(atlas) if palette else atlas
    buffer = io.BytesIO()
    image.save(buffer, format='PNG', optimize=True)
    model = dict(meta=dict(format_version='4.10', model_format='free', box_uv=False,
                           evergarden_animation_position_x='inverted', **meta),
                 name=identifier, model_identifier=identifier, resolution=dict(width=size, height=size),
                 elements=elements,
                 outliner=[group(r) for r in roots] + [dict(name='hitbox', uuid=uid('hitbox'), origin=[0, 30, 0], children=[hit], export=True)],
                 textures=[dict(name=identifier + '.png', namespace='', folder='', path='', id='0', uuid=uid('texture'),
                                width=size, height=size, uv_width=size, uv_height=size,
                                source='data:image/png;base64,' + base64.b64encode(buffer.getvalue()).decode())],
                 animations=[])
    stats.update(cubes=len(elements) - 1, displays=len(displays), png=len(buffer.getvalue()))
    return model, image.convert('RGBA'), stats
