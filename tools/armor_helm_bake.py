"""Baked voxel helms for wearable armor sets.

A helm is voxelised at a quarter head pixel in head-pixel space (Bedrock model space: x is the
wearer's left, y up, z toward the back, the face looks at -Z; the head box spans x/z -4..4 and
y 24..32). Materials only decide paint: every solid cell joins one of two merged cuboid groups
(lit plate, or glowing), so the cuboid count follows the silhouette, not the paint job.

Every visible face is then baked texel by texel, one texel per cell (the 4x density of the armor
layers): the cell's material ramp under a key light, using an analytic normal where a primitive
set one (horns, crystals) and a smoothed occupancy normal elsewhere, ambient occlusion from the
cell's surroundings, a convex-edge highlight, a specular sheen and the material's own tone
pattern. Faces with no visible texel are dropped.

Java draws the helm as an item model on the head (equippable without an asset); glowing cuboids
are unshaded with light_emission 15. Bedrock gets the same cuboids with per-face UVs in
Blockbench's convention (same face keys, up/down UVs flipped on both axes), glowing cuboids in an
emissive bone; their texels carry low alpha in the Bedrock copy of the atlas, which
entity_emissive_alpha reads as emission (Java keeps them opaque).
"""
from __future__ import annotations

from dataclasses import dataclass, field
import math

import numpy as np
from PIL import Image

from weapon_forge import greedy_boxes

CELL = .25
LO = (-12.0, 19.0, -12.0)
HI = (12.0, 43.0, 12.0)
FACES = ('north', 'south', 'east', 'west', 'up', 'down')
# Java face -> outward normal in head space (Java's model x is the head's -x: mx = 8 - 2x).
NORMAL = {'north': (0, 0, -1), 'south': (0, 0, 1), 'west': (1, 0, 0), 'east': (-1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}
# Patch layout per face, following Minecraft FaceInfo corners: (column axis, step), (row axis, step).
LAYOUT = {'north': ((0, 1), (1, -1)), 'south': ((0, -1), (1, -1)), 'west': ((2, 1), (1, -1)),
          'east': ((2, -1), (1, -1)), 'up': ((0, -1), (2, 1)), 'down': ((0, -1), (2, -1))}
STOPS = np.array([0, .28, .52, .72, .92, 1.15])
RAMP_KEYS = ('deep', 'shade', 'base', 'mid', 'hi', 'spec')
GLOW_ALPHA = 64          # Bedrock: 1 - 64/255 of the texel is emission


def unit(v):
    v = np.asarray(v, float)
    return v / np.linalg.norm(v)


LIGHT = unit((-.42, .78, -.46))   # above, in front, from the wearer's right (the viewer's left)
VIEW = unit((0, .22, -1))
HALF = unit(LIGHT + VIEW)


def blur(a, sigma):
    """Separable Gaussian blur of a 3D array (zero outside)."""
    r = max(1, int(math.ceil(sigma * 2.5)))
    k = np.exp(-.5 * (np.arange(-r, r + 1) / sigma) ** 2)
    k /= k.sum()
    out = a.astype(np.float32)
    for axis in range(3):
        pad = [(0, 0)] * 3
        pad[axis] = (r, r)
        p = np.pad(out, pad)
        acc = np.zeros_like(out)
        for i, w in enumerate(k):
            sl = [slice(None)] * 3
            sl[axis] = slice(i, i + out.shape[axis])
            acc += np.float32(w) * p[tuple(sl)]
        out = acc
    return out


def ramp(pal, value):
    cols = np.array([pal[k] for k in RAMP_KEYS], float)
    v = np.clip(value, 0, 1.15)
    return np.stack([np.interp(v, STOPS, cols[:, c]) for c in range(3)], -1)


def smooth_path(points, samples=10):
    """Catmull-Rom through the control points: a dense, smooth polyline."""
    p = np.asarray(points, float)
    if len(p) < 3:
        return p
    ext = np.vstack([2 * p[0] - p[1], p, 2 * p[-1] - p[-2]])
    out = []
    for i in range(1, len(ext) - 2):
        p0, p1, p2, p3 = ext[i - 1:i + 3]
        for t in np.linspace(0, 1, samples, endpoint=False):
            t2, t3 = t * t, t * t * t
            out.append(.5 * (2 * p1 + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3))
    out.append(p[-1])
    return np.array(out)


@dataclass
class Field:
    sl: tuple
    mask: np.ndarray
    along: np.ndarray = None      # 0 at the root .. 1 at the tip
    normal: np.ndarray = None
    rel: np.ndarray = None        # distance / local radius
    extra: dict = field(default_factory=dict)


class VoxelHelm:
    def __init__(self, name, lo=LO, hi=HI, cell=CELL):
        self.name, self.cell = name, cell
        self.lo, self.hi = np.array(lo, float), np.array(hi, float)
        self.n = np.round((self.hi - self.lo) / cell).astype(int)
        self.axes = [self.lo[i] + (np.arange(self.n[i]) + .5) * cell for i in range(3)]
        self.labels = np.zeros(tuple(self.n), np.int16)
        self.normal = np.zeros(tuple(self.n) + (3,), np.float32)
        self.tone = np.zeros(tuple(self.n), np.float32)
        self.materials = []

    # -- grid access -------------------------------------------------------
    def grid(self, lo=None, hi=None):
        if lo is None:
            sl = (slice(None),) * 3
        else:
            lo = np.maximum(np.asarray(lo, float), self.lo)
            hi = np.minimum(np.asarray(hi, float), self.hi)
            i0 = np.clip(np.floor((lo - self.lo) / self.cell).astype(int), 0, self.n)
            i1 = np.clip(np.ceil((hi - self.lo) / self.cell).astype(int), 0, self.n)
            sl = tuple(slice(int(a), int(b)) for a, b in zip(i0, i1))
        X, Y, Z = np.meshgrid(*(ax[s] for ax, s in zip(self.axes, sl)), indexing='ij')
        return sl, X, Y, Z

    def label(self, mat):
        if mat not in self.materials:
            self.materials.append(mat)
        return self.materials.index(mat) + 1

    def put(self, mat, sl, mask, normal=None, tone=None, over=False):
        """Paint cells; the first writer wins unless `over`. mat=None carves."""
        L = self.labels[sl]
        m = np.asarray(mask, bool)
        if not over:
            m = m & (L == 0)
        if mat is None:
            L[m] = 0
            self.normal[sl][m] = 0
            self.tone[sl][m] = 0
            return m
        L[m] = self.label(mat)
        if normal is not None:
            self.normal[sl][m] = np.asarray(normal, np.float32)[m]
        if tone is not None:
            self.tone[sl][m] = np.broadcast_to(np.asarray(tone, np.float32), m.shape)[m]
        return m

    def solid(self):
        return self.labels > 0

    def skin(self, depth=1):
        """Solid cells within `depth` cells of open space (6-connected)."""
        s = self.solid()
        core = s.copy()
        out = np.zeros_like(s)
        for _ in range(depth):
            inner = core.copy()
            for axis in range(3):
                for step in (1, -1):
                    shifted = np.roll(core, step, axis)
                    edge = [slice(None)] * 3
                    edge[axis] = 0 if step == 1 else -1
                    shifted[tuple(edge)] = False
                    inner &= shifted
            out |= core & ~inner
            core = inner
        return out

    def recolor(self, mat, mask, depth=2, tone=None, normal=None):
        """Repaint only the outer skin of the solid inside `mask` (full-grid mask)."""
        m = np.asarray(mask, bool) & self.skin(depth)
        return self.put(mat, (slice(None),) * 3, m, normal=normal, tone=tone, over=True)

    # -- primitives ---------------------------------------------------------
    def tube(self, points, r0, r1, power=1.0, samples=10, smooth=True):
        """A tapered tube along a smooth 3D curve: mask, arc position and radial normal."""
        pts = smooth_path(points, samples) if smooth else np.asarray(points, float)
        seg = np.linalg.norm(np.diff(pts, axis=0), axis=1)
        cum = np.concatenate([[0], np.cumsum(seg)])
        R = max(r0, r1)
        sl, X, Y, Z = self.grid(pts.min(0) - R - self.cell, pts.max(0) + R + self.cell)
        P = np.stack([X, Y, Z], -1)
        best = np.full(X.shape, np.inf)
        along = np.zeros(X.shape)
        foot = np.zeros(P.shape)
        for i, (a, b) in enumerate(zip(pts, pts[1:])):
            ab = b - a
            t = np.clip(((P - a) @ ab) / max(ab @ ab, 1e-9), 0, 1)
            f = a + t[..., None] * ab
            d = np.linalg.norm(P - f, axis=-1)
            better = d < best
            best = np.where(better, d, best)
            along = np.where(better, (cum[i] + t * seg[i]) / cum[-1], along)
            foot = np.where(better[..., None], f, foot)
        radius = r0 + (r1 - r0) * along ** power
        mask = best <= radius
        normal = (P - foot) / np.maximum(best, 1e-6)[..., None]
        return Field(sl, mask, along, normal, best / np.maximum(radius, 1e-6))

    def crystal(self, base, tip, radius, tip_len, hint=(0, 0, -1), twist=0.0, flare=.08):
        """A hexagonal crystal with a pointed tip and crisp facet normals."""
        base, tip = np.asarray(base, float), np.asarray(tip, float)
        axis = tip - base
        length = float(np.linalg.norm(axis))
        a = axis / length
        e1 = np.cross(a, hint)
        if np.linalg.norm(e1) < 1e-6:
            e1 = np.cross(a, (1, 0, 0))
        e1 = unit(e1)
        e2 = np.cross(a, e1)
        c, s = math.cos(twist), math.sin(twist)
        e1, e2 = c * e1 + s * e2, -s * e1 + c * e2
        R = radius * (1 + flare)
        sl, X, Y, Z = self.grid(np.minimum(base, tip) - R - self.cell, np.maximum(base, tip) + R + self.cell)
        P = np.stack([X, Y, Z], -1) - base
        h = P @ a
        q = P - h[..., None] * a
        qx, qy = q @ e1, q @ e2
        angles = np.arange(6) * math.pi / 3
        proj = np.stack([qx * math.cos(t) + qy * math.sin(t) for t in angles], -1)
        k = proj.argmax(-1)
        hexn = proj.max(-1)
        shaft = length - tip_len
        rad = np.where(h < shaft, radius * (1 + flare * (1 - h / max(shaft, 1e-6))), radius * np.clip((length - h) / tip_len, 0, 1))
        mask = (h >= 0) & (h <= length) & (hexn <= rad)
        fn = np.cos(angles)[k][..., None] * e1 + np.sin(angles)[k][..., None] * e2
        tilt = np.where(h >= shaft, radius / tip_len, 0)[..., None]
        normal = fn + tilt * a
        normal /= np.linalg.norm(normal, axis=-1, keepdims=True)
        return Field(sl, mask, h / length, normal, hexn / np.maximum(rad, 1e-6), {'facet': k})

    def blade(self, origin, u, v, outline, thickness, bevel=.6):
        """A flat blade: a 2D outline (u, v) in the plane through origin, beveled toward its edges."""
        from weapon_forge import even_odd
        o, u, v = np.asarray(origin, float), unit(u), unit(v)
        w = unit(np.cross(u, v))
        pts = np.asarray(outline, float)
        corners = [o + p[0] * u + p[1] * v for p in pts]
        span = thickness + self.cell
        lo = np.min(corners, 0) - span
        hi = np.max(corners, 0) + span
        sl, X, Y, Z = self.grid(lo, hi)
        P = np.stack([X, Y, Z], -1) - o
        pu, pv, pw = P @ u, P @ v, P @ w
        inside = even_odd(pu, pv, [tuple(p) for p in pts])
        d = np.full(pu.shape, np.inf)
        for a, b in zip(pts, np.roll(pts, -1, 0)):
            ab = b - a
            t = np.clip(((pu - a[0]) * ab[0] + (pv - a[1]) * ab[1]) / max(ab @ ab, 1e-9), 0, 1)
            d = np.minimum(d, np.hypot(pu - a[0] - t * ab[0], pv - a[1] - t * ab[1]))
        half = np.maximum(thickness / 2 * np.clip(d / bevel, 0, 1), self.cell * .55)
        mask = inside & (np.abs(pw) <= half)
        normal = np.where(pw[..., None] >= 0, w, -w) * np.ones(P.shape)
        return Field(sl, mask, None, normal, None, {'edge': d, 'u': pu, 'v': pv})


# ---------------------------------------------------------------------------
# Baking


@dataclass
class Box:
    lo: tuple
    hi: tuple
    glow: bool
    faces: dict          # java face -> (x, y, w, h) in atlas pixels


@dataclass
class Baked:
    boxes: list
    java: Image.Image
    bedrock: Image.Image
    size: int


def _face_cells(face, i0, i1, j0, j1, k0, k1):
    """Cell indices of one box face in patch order (rows, cols) and the outside neighbours."""
    lo, hi = (i0, j0, k0), (i1, j1, k1)
    (ca, cs), (ra, rs) = LAYOUT[face]
    normal = NORMAL[face]
    axis = [i for i in range(3) if normal[i]][0]
    sign = normal[axis]
    fixed = hi[axis] - 1 if sign > 0 else lo[axis]
    cols = np.arange(lo[ca], hi[ca])[::cs]
    rows = np.arange(lo[ra], hi[ra])[::rs]
    R, C = np.meshgrid(rows, cols, indexing='ij')
    idx = [None, None, None]
    idx[ca], idx[ra], idx[axis] = C, R, np.full(C.shape, fixed)
    nb = [a.copy() for a in idx]
    nb[axis] = nb[axis] + sign
    return idx, nb, normal


def bake(helm: VoxelHelm, materials: dict, seed=1):
    """Merge the helm into cuboids and paint every visible face. materials: name -> spec."""
    lab = helm.labels
    solid = lab > 0
    glow_ids = [i + 1 for i, m in enumerate(helm.materials) if materials[m].get('glow')]
    glowing = np.isin(lab, glow_ids)
    group = np.where(solid, np.where(glowing, 2, 1), 0).astype(np.int16)
    boxes = greedy_boxes(group)
    O = solid.astype(np.float32)
    B1 = blur(O, 1.15)
    B2 = blur(O, 2.6)
    G = np.stack(np.gradient(B1), -1)
    n_shape = np.array(lab.shape)

    def at(a, idx):
        inside = np.ones(idx[0].shape, bool)
        for d in range(3):
            inside &= (idx[d] >= 0) & (idx[d] < n_shape[d])
        cl = [np.clip(idx[d], 0, n_shape[d] - 1) for d in range(3)]
        return a[cl[0], cl[1], cl[2]], inside

    faces = []    # (box index, face, idx, shape)
    for b, ((i0, j0, k0), (i1, j1, k1), g) in enumerate(boxes):
        for face in FACES:
            idx, nb, normal = _face_cells(face, i0, i1, j0, j1, k0, k1)
            occ, inside = at(solid, nb)
            exposed = ~(occ & inside)
            if exposed.any():
                faces.append((b, face, idx, nb, exposed))

    # Shade every texel of every kept face in one vectorised pass.
    I = np.concatenate([f[2][0].ravel() for f in faces])
    J = np.concatenate([f[2][1].ravel() for f in faces])
    K = np.concatenate([f[2][2].ravel() for f in faces])
    NB = [np.concatenate([f[3][d].ravel() for f in faces]) for d in range(3)]
    FN = np.concatenate([np.broadcast_to(np.array(NORMAL[f[1]], float), f[2][0].shape + (3,)).reshape(-1, 3) for f in faces])
    mats = lab[I, J, K]
    user = helm.normal[I, J, K].astype(float)
    grad = -G[I, J, K].astype(float)
    has_user = np.linalg.norm(user, axis=1) > .5
    gl = np.linalg.norm(grad, axis=1)
    smooth = np.where(gl[:, None] > 1e-4, grad / np.maximum(gl, 1e-9)[:, None], FN)
    n = np.where(has_user[:, None], user, smooth)
    n = n * .72 + FN * .28
    n /= np.linalg.norm(n, axis=1, keepdims=True)
    out_density, _ = at(B2, NB)
    edge_density = B1[I, J, K]
    tone = helm.tone[I, J, K].astype(float)
    pos = np.stack([helm.axes[0][I], helm.axes[1][J], helm.axes[2][K]], -1)
    rng = np.random.default_rng(seed)
    noise = rng.normal(0, 1, len(I))
    colors = np.zeros((len(I), 3))
    alpha = np.full(len(I), 255.0)
    ndl = n @ LIGHT
    for mi, name in enumerate(helm.materials):
        sel = mats == mi + 1
        if not sel.any():
            continue
        spec = materials[name]
        if spec.get('glow'):
            g = spec['glow']
            t = np.clip(np.where(tone[sel] != 0, tone[sel], spec.get('level', .55)) + noise[sel] * .03, 0, 1)
            stops = np.array([0, .5, 1])
            cols = np.array([g['dim'], g['core'], g['hot']], float)
            colors[sel] = np.stack([np.interp(t, stops, cols[:, c]) for c in range(3)], -1)
            alpha[sel] = GLOW_ALPHA
            continue
        d = np.clip(ndl[sel], -1, 1)
        value = spec.get('ambient', .36) + spec.get('diffuse', .56) * np.clip(d, 0, 1) + .1 * n[sel, 1] - .08 * np.clip(-d, 0, 1)
        ao = np.clip(1 - spec.get('ao', 1.5) * (out_density[sel] - .3), .42, 1.12)
        value = value * ao
        value += spec.get('rim', .55) * np.clip(.5 - edge_density[sel], 0, .28)
        value += tone[sel] + noise[sel] * spec.get('grain', .018)
        # Polished metal mirrors a bright sky and a dark ground, with a crisp horizon line.
        metal = spec.get('metal', 0)
        if metal:
            ny = n[sel, 1]
            value += metal * (np.clip(ny * 1.3 + .2, 0, 1) ** 1.6 - .32)
        col = ramp(spec['ramp'], value)
        sheen = np.clip(n[sel] @ HALF, 0, 1) ** spec.get('gloss', 20) * spec.get('spec', .5)
        if metal:
            sheen += metal * .7 * np.exp(-((n[sel, 1] - .22) / .09) ** 2) * np.clip(1.2 - out_density[sel] * 2, 0, 1)
        sheen += np.clip(.5 - edge_density[sel], 0, .25) * spec.get('edge_spec', .9) * np.clip(n[sel, 1] + .4, 0, 1)
        col = col + (np.asarray(spec['ramp']['spec'], float) - col) * np.clip(sheen, 0, 1)[:, None]
        if 'tint' in spec:
            col = spec['tint'](col, pos[sel], n[sel], tone[sel])
        colors[sel] = col
    rgba = np.concatenate([np.clip(colors, 0, 255), alpha[:, None]], 1)

    # Shelf-pack the patches (1 texel gutter of repeated edge texels).
    sizes = [f[2][0].shape for f in faces]
    order = sorted(range(len(faces)), key=lambda i: (-sizes[i][0], -sizes[i][1]))
    area = sum((h + 2) * (w + 2) for h, w in sizes)
    size = 64
    while True:
        while size * size < area * 1.08:
            size *= 2
        x = y = shelf = 0
        place = {}
        ok = True
        for i in order:
            h, w = sizes[i]
            if x + w + 2 > size:
                x, y, shelf = 0, y + shelf, 0
            if y + h + 2 > size or w + 2 > size:
                ok = False
                break
            place[i] = (x + 1, y + 1)
            x += w + 2
            shelf = max(shelf, h + 2)
        if ok:
            break
        size *= 2
    atlas = np.zeros((size, size, 4), np.uint8)
    offsets = np.cumsum([0] + [h * w for h, w in sizes])
    result = [Box(lo=tuple(float(helm.lo[d] + (b0[d]) * helm.cell) for d in range(3)),
                  hi=tuple(float(helm.lo[d] + (b1[d]) * helm.cell) for d in range(3)), glow=g == 2, faces={})
              for b0, b1, g in boxes]
    for i, (b, face, idx, nb, exposed) in enumerate(faces):
        h, w = sizes[i]
        patch = rgba[offsets[i]:offsets[i + 1]].reshape(h, w, 4)
        px, py = place[i]
        padded = np.pad(patch, ((1, 1), (1, 1), (0, 0)), mode='edge')
        atlas[py - 1:py + h + 1, px - 1:px + w + 1] = np.round(padded).astype(np.uint8)
        result[b].faces[face] = (px, py, w, h)
    kept = [r for r in result if r.faces]
    bedrock = Image.fromarray(atlas, 'RGBA')
    java_px = atlas.copy()
    java_px[..., 3] = np.where(java_px[..., 3] > 0, 255, 0)
    return Baked(kept, Image.fromarray(java_px, 'RGBA'), bedrock, size)


# ---------------------------------------------------------------------------
# Export


def java_model(b: Baked, texture, credit, display):
    s = 16 / b.size
    elements = []
    for box in b.boxes:
        (x0, y0, z0), (x1, y1, z1) = box.lo, box.hi
        e = {'from': [8 - 2 * x1, 8 + 2 * (y0 - 31), 8 + 2 * z0], 'to': [8 - 2 * x0, 8 + 2 * (y1 - 31), 8 + 2 * z1],
             'faces': {face: {'uv': [px * s, py * s, (px + w) * s, (py + h) * s], 'texture': '#helm'}
                       for face, (px, py, w, h) in box.faces.items()}}
        if box.glow:
            e.update(shade=False, light_emission=15)
        assert all(-16 <= v <= 32 for v in e['from'] + e['to']), e
        elements.append(e)
    return {'credit': credit, 'ambientocclusion': False, 'textures': {'helm': texture, 'particle': texture},
            'display': {'head': display}, 'elements': elements}


def bedrock_uv(face, px, py, w, h):
    if face in ('up', 'down'):
        return {'uv': [px + w, py + h], 'uv_size': [-w, -h]}
    return {'uv': [px, py], 'uv_size': [w, h]}


def bedrock_geometry(b: Baked, identifier):
    body, glow = [], []
    for box in b.boxes:
        cube = {'origin': list(box.lo), 'size': [round(h - l, 4) for l, h in zip(box.lo, box.hi)],
                'uv': {face: bedrock_uv(face, *rect) for face, rect in box.faces.items()}}
        (glow if box.glow else body).append(cube)
    return {'format_version': '1.16.0', 'minecraft:geometry': [{
        'description': {'identifier': identifier, 'texture_width': b.size, 'texture_height': b.size,
                        'visible_bounds_width': 4, 'visible_bounds_height': 4.5, 'visible_bounds_offset': [0, 1.6, 0]},
        'bones': [{'name': 'head', 'pivot': [0, 24, 0], 'cubes': body},
                  {'name': 'helm_glow', 'parent': 'head', 'pivot': [0, 24, 0], 'cubes': glow}]}]}


def preview_faces(b: Baked, offset):
    """(lo, hi, faces, glow) in the offline preview's true-view space (x mirrored like Java)."""
    ox, oy = offset
    for box in b.boxes:
        (x0, y0, z0), (x1, y1, z1) = box.lo, box.hi
        faces = {face: {'uv': [ox + px, oy + py, ox + px + w, oy + py + h], 'texture': 0}
                 for face, (px, py, w, h) in box.faces.items()}
        yield (-x1, y0, z0), (-x0, y1, z1), faces, box.glow
