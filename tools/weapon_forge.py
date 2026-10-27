"""Shared sculpting tools and the Bedrock hand rig for held 3D weapons.

Weapons are authored in Java item-model pixels: x and z centred on 8, the
weapon pointing along +Y with its pommel near y=0, and -Z as the front face.
Outlines and volumes are rasterised on a quarter-pixel grid and merged into
cuboids, so Java models, Bedrock attachables and Blockbench sources all
receive the same boxes.

Bedrock rig
-----------
The rig follows java2bedrock's attachable layout. A root bone bound to the item
slot carries the hand base pose (third person [90,0,0] at [0,13,-3]; first
person [90,60,-40] at [4,10,4] x1.5). Three children apply the Java display
transform: display_x (rotation -rx, translation, scale), display_y (-ry) and
display_z (rz), i.e. Minecraft's Z-then-Y-then-X order with Bedrock's mirrored
X axis. Model points map to Bedrock as (8 - x, y, z - 8). In third person this
is the hold that Java's ItemInHandLayer produces for the same display
transform; bound_third_person() and java_third_person() check that arithmetic.
First person keeps java2bedrock's result for vanilla handheld translations and
maps other translations rigidly (see display_z).
"""
from __future__ import annotations

import math

import numpy as np

GRID = .25
FACES = ('north', 'south', 'east', 'west', 'up', 'down')
BOUNDS = (-8.0, -2.0, 24.0, 34.0)

# java2bedrock base poses for a bound item frame.
THIRD_ROOT = {'rotation': [90, 0, 0], 'position': [0, 13, -3]}
FIRST_ROOT = {'rotation': [90, 60, -40], 'position': [4, 10, 4], 'scale': 1.5}
HANDHELD_FIRST_Z = 1.13  # item/handheld firstperson translation Z (Java 26.3 client)
RIG_PIVOT = [0, 8, 0]
BINDING = 'q.item_slot_to_bone_name(context.item_slot)'
OFF_HAND = "context.item_slot == 'off_hand'"


def r4(value):
    return round(float(value), 4) + 0.0


class Forge:
    """Collects cuboids as {name, from, to, mat, bone}."""

    def __init__(self, name):
        self.name, self.cubes = name, []

    def box(self, x0, y0, z0, x1, y1, z1, mat, bone='weapon'):
        if x1 - x0 < 1e-4 or y1 - y0 < 1e-4 or z1 - z0 < 1e-4:
            return None
        cube = {'name': f'{mat}_{len(self.cubes):03}',
                'from': [r4(x0), r4(y0), r4(z0)], 'to': [r4(x1), r4(y1), r4(z1)],
                'mat': mat, 'bone': bone}
        self.cubes.append(cube)
        return cube

    # -- 2D reliefs --------------------------------------------------------
    def relief(self, shape, layers, bone='weapon', z=8.0, cell=GRID):
        """Extrude an outline as bevelled layers.

        `layers` run from the thin outer edge to the thick core as
        (inset, half_depth, mat). A cell joins a layer when it lies at least
        `inset` pixels inside the outline, so bevels keep an even width at
        points and in concave notches. A half depth may be (front, back),
        where the front is the -Z side.
        """
        filled, xs, ys = rasterise(shape, cell)
        depth = inner_distance(filled, cell)
        for k, (inset, half, mat) in enumerate(layers):
            front, back = half if isinstance(half, (tuple, list)) else (half, half)
            # Each layer keeps only its own band, so no two boxes share a face plane.
            region = filled & (depth >= inset - 1e-6)
            if k + 1 < len(layers):
                region &= depth < layers[k + 1][0] - 1e-6
            for x0, y0, x1, y1 in mask_rectangles(region, xs, ys, cell):
                self.box(x0, y0, z - front, x1, y1, z + back, mat, bone)
        return filled

    def plate(self, shape, z0, z1, mat, bone='weapon', cell=GRID):
        """A flat extrusion between two depths."""
        filled, xs, ys = rasterise(shape, cell)
        for x0, y0, x1, y1 in mask_rectangles(filled, xs, ys, cell):
            self.box(x0, y0, z0, x1, y1, z1, mat, bone)

    def stroke(self, points, width, z0, z1, mat, bone='weapon', cell=GRID):
        """An even-width polyline, rasterised into merged strips."""
        self.plate(lambda X, Y: near_polyline(X, Y, points, width / 2), z0, z1, mat, bone, cell)

    # -- 3D volumes --------------------------------------------------------
    def volume(self, material, lo, hi, cell=.25, bone='weapon'):
        """Voxelise material(X, Y, Z) -> [(name, mask), ...] and merge greedily.

        Earlier entries win where masks overlap. Bounds snap to the cell grid
        so volumes line up with reliefs and boxes.
        """
        lo = [math.floor(v / cell) * cell for v in lo]
        hi = [math.ceil(v / cell) * cell for v in hi]
        axes = [np.arange(lo[i] + cell / 2, hi[i] - 1e-9, cell) for i in range(3)]
        X, Y, Z = np.meshgrid(*axes, indexing='ij')
        labels = np.zeros(X.shape, dtype=np.int16)
        palette = []
        for name, mask in material(X, Y, Z):
            palette.append(name)
            labels[np.asarray(mask, dtype=bool) & (labels == 0)] = len(palette)
        for (i0, j0, k0), (i1, j1, k1), label in greedy_boxes(labels):
            self.box(lo[0] + i0 * cell, lo[1] + j0 * cell, lo[2] + k0 * cell,
                     lo[0] + i1 * cell, lo[1] + j1 * cell, lo[2] + k1 * cell, palette[label - 1], bone)

    def tube(self, points, radius, mat, cell=.25, bone='weapon'):
        """A round tube along a 3D polyline (curved prongs, roots, straps)."""
        pts = np.asarray(points, dtype=float)
        lo, hi = pts.min(0) - radius - cell, pts.max(0) + radius + cell
        self.volume(lambda X, Y, Z: [(mat, segment_distance(X, Y, Z, pts) <= radius)], lo, hi, cell, bone)

    def rod(self, y0, y1, width, mat, x=8.0, z=8.0, bone='weapon'):
        """Octagonal rod along +Y from three non-overlapping boxes."""
        a, b = width / 2, width * .3
        self.box(x - a, y0, z - b, x + a, y1, z + b, mat, bone)
        self.box(x - b, y0, z + b, x + b, y1, z + a, mat, bone)
        self.box(x - b, y0, z - a, x + b, y1, z - b, mat, bone)

    def wrap(self, y0, y1, width, base, bands, pitch=.6, x=8.0, z=8.0, bone='weapon'):
        """Grip core with raised wrap bands that alternate between materials."""
        self.rod(y0, y1, width, base, x, z, bone)
        y, k = y0 + .2, 0
        while y + .3 <= y1 - .15:
            self.rod(y, y + .3, width + .2, bands[k % len(bands)], x, z, bone)
            y += pitch
            k += 1

    def cone(self, base, direction, length, radius, mat, tip_radius=.08, cell=.25, bone='weapon'):
        """A tapered spike from `base` along `direction` (any 3D direction)."""
        base = np.asarray(base, dtype=float)
        axis = np.asarray(direction, dtype=float) / np.linalg.norm(direction)
        end = base + axis * length
        lo = np.minimum(base, end) - radius - cell
        hi = np.maximum(base, end) + radius + cell
        self.volume(lambda X, Y, Z: [(mat, cone_mask(X, Y, Z, base, axis, length, radius, tip_radius))], lo, hi, cell, bone)

    def jewel(self, x, y, r, setting, body, facet, glint='white', z=8.0, depth=1.0, shape='kite', bone='weapon'):
        """A set gemstone: metal bezel, body, lighter table facet and a glint."""
        if shape == 'round':
            outline = [(x + r * math.cos(a), y + r * math.sin(a)) for a in np.linspace(0, math.tau, 24, endpoint=False)]
        elif shape == 'diamond':
            outline = [(x, y + r * 1.1), (x + r * .78, y), (x, y - r * 1.1), (x - r * .78, y)]
        elif shape == 'oval':
            outline = [(x + r * .72 * math.cos(a), y + r * 1.05 * math.sin(a)) for a in np.linspace(0, math.tau, 24, endpoint=False)]
        else:
            outline = [(x, y + r * 1.15), (x + r * .85, y + r * .25), (x + r * .52, y - r * .85),
                       (x - r * .52, y - r * .85), (x - r * .85, y + r * .25)]
        self.relief(outline, [(0, depth * .5, setting), (min(.3, r * .3), depth * .74, body),
                              (min(.3, r * .3) + r * .34, depth * .86, facet)], bone, z)
        g = max(.25, r * .22)
        self.box(x - r * .42, y + r * .18, z - depth * .92, x - r * .42 + g, y + r * .18 + g, z + depth * .92, glint, bone)


# -- outlines ----------------------------------------------------------------
def circle(cx, cy, r, count=28, start=0.0):
    return [(cx + r * math.cos(a), cy + r * math.sin(a))
            for a in np.linspace(start, start + math.tau, count, endpoint=False)]


def mirrored(half, axis=8.0):
    """Right half from top centre clockwise to bottom centre -> symmetric outline."""
    return list(half) + [(2 * axis - x, y) for x, y in reversed(half[1:-1])]


def profile(half_width, y0, y1, step=.25, centre=8.0):
    """Outline of a blade whose half width is a function of y (pommel to tip)."""
    ys = np.arange(y0, y1 + 1e-9, step)
    middle = centre if callable(centre) else (lambda y: centre)
    return [(middle(y) - half_width(y), y) for y in ys] + [(middle(y) + half_width(y), y) for y in ys[::-1]]


def edges(left, right, y0, y1, step=.125):
    """Outline from independent left/right edge functions (curved or serrated blades)."""
    ys = np.arange(y0, y1 + 1e-9, step)
    return [(left(y), y) for y in ys] + [(right(y), y) for y in ys[::-1]]


def band(points, widths, notch=0.0):
    """Ribbon outline around a centre line, optionally with a swallow-tail notch."""
    pts = np.asarray(points, dtype=float)
    widths = widths if isinstance(widths, (list, tuple)) else [widths] * len(pts)
    left, right = [], []
    for i, p in enumerate(pts):
        a, b = pts[max(i - 1, 0)], pts[min(i + 1, len(pts) - 1)]
        t = (b - a) / (np.linalg.norm(b - a) or 1)
        n = np.array([-t[1], t[0]])
        left.append(tuple(p + n * widths[i]))
        right.append(tuple(p - n * widths[i]))
    tail = []
    if notch:
        t = (pts[-1] - pts[-2]) / np.linalg.norm(pts[-1] - pts[-2])
        tail = [tuple(pts[-1] - t * notch)]
    return left + tail + right[::-1]


def inside(shape):
    """Mask function for an outline, to combine shapes with boolean operators."""
    polygons = shape if isinstance(shape[0][0], (tuple, list)) else [shape]

    def mask(X, Y):
        filled = np.zeros(np.shape(X), dtype=bool)
        for polygon in polygons:
            filled ^= even_odd(X, Y, polygon)
        return filled
    return mask


# -- rasterising ------------------------------------------------------------
def rasterise(shape, cell=GRID, bounds=BOUNDS):
    x0, y0, x1, y1 = bounds
    xs = np.arange(x0 + cell / 2, x1, cell)
    ys = np.arange(y0 + cell / 2, y1, cell)
    X, Y = np.meshgrid(xs, ys)
    if callable(shape):
        filled = np.asarray(shape(X, Y), dtype=bool)
    else:
        polygons = shape if isinstance(shape[0][0], (tuple, list)) else [shape]
        filled = np.zeros(X.shape, dtype=bool)
        for polygon in polygons:
            filled ^= even_odd(X, Y, polygon)
    return filled, xs, ys


def even_odd(X, Y, polygon):
    inside = np.zeros(X.shape, dtype=bool)
    for (ax, ay), (bx, by) in zip(polygon, list(polygon[1:]) + [polygon[0]]):
        if ay == by:
            continue
        crosses = (ay <= Y) != (by <= Y)
        at = ax + (Y - ay) * (bx - ax) / (by - ay)
        inside ^= crosses & (X < at)
    return inside


def near_polyline(X, Y, points, radius):
    best = np.full(np.shape(X), np.inf)
    for (ax, ay), (bx, by) in zip(points, points[1:]):
        dx, dy = bx - ax, by - ay
        length = dx * dx + dy * dy or 1e-9
        t = np.clip(((X - ax) * dx + (Y - ay) * dy) / length, 0, 1)
        best = np.minimum(best, np.hypot(X - ax - t * dx, Y - ay - t * dy))
    return best <= radius


def cone_mask(X, Y, Z, base, direction, length, radius, tip_radius=.08):
    """Cells inside a tapered spike; combine several masks in one volume() call."""
    base = np.asarray(base, dtype=float)
    axis = np.asarray(direction, dtype=float)
    axis = axis / np.linalg.norm(axis)
    P = np.stack([X, Y, Z], -1) - base
    s = P @ axis
    off = np.linalg.norm(P - s[..., None] * axis, axis=-1)
    allowed = radius + (tip_radius - radius) * np.clip(s / length, 0, 1)
    return (s >= 0) & (s <= length) & (off <= allowed)


def segment_distance(X, Y, Z, points):
    """Distance from grid points to a 3D polyline."""
    P = np.stack([X, Y, Z], -1)
    best = np.full(np.shape(X), np.inf)
    for a, b in zip(points, points[1:]):
        d = b - a
        t = np.clip(((P - a) @ d) / max(d @ d, 1e-9), 0, 1)
        best = np.minimum(best, np.linalg.norm(P - a - t[..., None] * d, axis=-1))
    return best


def inner_distance(filled, cell):
    """Distance from each filled cell centre to the outline."""
    padded = np.pad(filled, 1)
    edge = ~padded & (np.roll(padded, 1, 0) | np.roll(padded, -1, 0) | np.roll(padded, 1, 1) | np.roll(padded, -1, 1))
    outside = np.argwhere(edge).astype(float) - 1
    inside = np.argwhere(filled).astype(float)
    depth = np.zeros(filled.shape)
    if not len(inside):
        return depth
    best = np.empty(len(inside))
    for start in range(0, len(inside), 2048):
        chunk = inside[start:start + 2048]
        best[start:start + 2048] = np.sqrt(((chunk[:, None, :] - outside[None, :, :]) ** 2).sum(-1)).min(1)
    depth[filled] = (best - .5) * cell
    return depth


def mask_rectangles(mask, xs, ys, cell):
    """Row runs, merged upwards while a run keeps exactly the same span."""
    out, open_runs = [], {}
    for j in range(mask.shape[0]):
        row = mask[j]
        edges = np.flatnonzero(np.diff(np.concatenate(([0], row.astype(np.int8), [0]))))
        current = {}
        for a, b in zip(edges[::2], edges[1::2]):
            run = (int(a), int(b))
            current[run] = open_runs.pop(run) if run in open_runs else j
        out.extend((a, start, b, j) for (a, b), start in open_runs.items())
        open_runs = current
    out.extend((a, start, b, mask.shape[0]) for (a, b), start in open_runs.items())
    half = cell / 2
    return [(xs[a] - half, ys[j0] - half, xs[b - 1] + half, ys[j1 - 1] + half) for a, j0, b, j1 in out]


def greedy_boxes(labels):
    """Greedy 3D merge of equal labels: ((i0,j0,k0), (i1,j1,k1), label)."""
    used = np.zeros(labels.shape, dtype=bool)
    nx, ny, nz = labels.shape
    boxes = []
    for i, j, k in np.argwhere(labels > 0):
        if used[i, j, k]:
            continue
        label = labels[i, j, k]
        i1 = i + 1
        while i1 < nx and labels[i1, j, k] == label and not used[i1, j, k]:
            i1 += 1
        j1 = j + 1
        while j1 < ny and np.all(labels[i:i1, j1, k] == label) and not used[i:i1, j1, k].any():
            j1 += 1
        k1 = k + 1
        while k1 < nz and np.all(labels[i:i1, j:j1, k1] == label) and not used[i:i1, j:j1, k1].any():
            k1 += 1
        used[i:i1, j:j1, k:k1] = True
        boxes.append(((int(i), int(j), int(k)), (int(i1), int(j1), int(k1)), int(label)))
    return boxes


def tile_pixels(rgb, rng, emissive=False):
    """A 16px material tile: gentle sheen and fine grain, no hard marks."""
    rows = []
    for y in range(16):
        row = []
        for x in range(16):
            shade = 1.0 if emissive else 1.07 - .12 * (x + .5) / 16 + (.03 if y < 2 else 0)
            shade += rng.choice((-.014, 0, 0, .014))
            row.append(tuple(max(0, min(255, int(c * shade))) for c in rgb) + (255,))
        rows.append(row)
    return rows


# -- Bedrock rig -------------------------------------------------------------
def rig_bones(grip_y):
    return [{'name': 'root', 'pivot': list(RIG_PIVOT), 'binding': BINDING},
            {'name': 'display_x', 'parent': 'root', 'pivot': list(RIG_PIVOT)},
            {'name': 'display_y', 'parent': 'display_x', 'pivot': list(RIG_PIVOT)},
            {'name': 'display_z', 'parent': 'display_y', 'pivot': list(RIG_PIVOT),
             'locators': {'hand_contact': [0, r4(grip_y), 0]}}]


def bedrock_point(p):
    return [r4(8 - p[0]), r4(p[1]), r4(p[2] - 8)]


def bedrock_cube(cube, uv_px):
    """Java element -> Bedrock cube; uv_px is the material tile's [u, v, size] in pixels."""
    u, v, size = uv_px
    return {'origin': [r4(8 - cube['to'][0]), r4(cube['from'][1]), r4(cube['from'][2] - 8)],
            'size': [r4(cube['to'][i] - cube['from'][i]) for i in range(3)],
            'uv': {face: {'uv': [u, v], 'uv_size': [size, size]} for face in FACES}}


def _pick(right, left):
    right, left = r4(right), r4(left)
    return right if right == left else f'{OFF_HAND} ? {left} : {right}'


def display_z(translation_z, first):
    """Bedrock display_x Z offset for a Java display translation.

    java2bedrock negates first-person Z (-tz) without mirroring any rotation.
    That equals tz - 2 * 1.13 for vanilla handheld items (tz = 1.13), which is
    what its first-person base frame was tuned with; using that form for every
    item keeps other translations rigid, so long weapons with negative tz are
    not pushed the wrong way. Third person maps Z unchanged.
    """
    return translation_z - 2 * HANDHELD_FIRST_Z if first else translation_z


def rig_animation(display, first):
    """java2bedrock mapping of one Java view (main and off hand) onto the rig."""
    view = 'firstperson_' if first else 'thirdperson_'
    right, left = display[view + 'righthand'], display[view + 'lefthand']
    rr, lr = right.get('rotation', [0, 0, 0]), left.get('rotation', [0, 0, 0])
    rt, lt = right.get('translation', [0, 0, 0]), left.get('translation', [0, 0, 0])
    rs, ls = right.get('scale', [1, 1, 1]), left.get('scale', [1, 1, 1])
    root = FIRST_ROOT if first else THIRD_ROOT
    return {'loop': True, 'bones': {
        'root': {key: (list(value) if isinstance(value, list) else value) for key, value in root.items()},
        'display_x': {'rotation': [_pick(-rr[0], -lr[0]), 0, 0],
                      'position': [_pick(-rt[0], lt[0]), _pick(rt[1], lt[1]), _pick(display_z(rt[2], first), display_z(lt[2], first))],
                      'scale': [_pick(rs[i], ls[i]) for i in range(3)]},
        'display_y': {'rotation': [0, _pick(-rr[1], -lr[1]), 0]},
        'display_z': {'rotation': [0, 0, _pick(rr[2], lr[2])]},
    }}


# -- verification helpers (shared by tests and previews) ---------------------
def rot(axis, degrees):
    c, s = math.cos(math.radians(degrees)), math.sin(math.radians(degrees))
    return {'x': np.array([[1, 0, 0], [0, c, -s], [0, s, c]]),
            'y': np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]]),
            'z': np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])}[axis]


def bedrock_rotation(angles):
    """Bedrock bone Euler angles as a right-handed matrix in Bedrock model axes.

    Blockbench imports Bedrock rotations as (-x, -y, z) in ZYX order with the
    X axis mirrored, which leaves Rz(-z) Ry(y) Rx(-x) in Bedrock's own axes.
    """
    x, y, z = angles
    return rot('z', -z) @ rot('y', y) @ rot('x', -x)


def evaluate(value, off_hand=False):
    if not isinstance(value, str):
        return float(value)
    condition, options = value.split('?')
    assert condition.strip() == OFF_HAND, value
    yes, no = options.split(':')
    return float(yes if off_hand else no)


def rig_transform(animation, point, off_hand=False):
    """Apply the rig's bones, innermost first, to a Bedrock-space model point."""
    bones = animation['bones']
    p = np.asarray(point, dtype=float)
    pivot = np.array(RIG_PIVOT, dtype=float)
    for name in ('display_z', 'display_y', 'display_x', 'root'):
        bone = bones.get(name, {})
        angles = [evaluate(v, off_hand) for v in bone.get('rotation', [0, 0, 0])]
        offset = np.array([evaluate(v, off_hand) for v in bone.get('position', [0, 0, 0])])
        scale = bone.get('scale', 1)
        scale = np.array([evaluate(v, off_hand) for v in scale]) if isinstance(scale, list) else np.full(3, float(scale))
        p = pivot + offset + bedrock_rotation(angles) @ (scale * (p - pivot))
    return p


def bound_third_person(animation, point, off_hand=False):
    """Bedrock third person in player model axes, arm hanging straight down.

    A bound bone's model point (0, 24, 0) sits at the item bone pivot
    (rightItem [-6,15,1], leftItem [6,15,1]); this is the offset that makes
    Mojang's trident pivot [0,24,0] and java2bedrock's [0,13,-3] both land in
    the fist.
    """
    item = np.array([6 if off_hand else -6, 15, 1], dtype=float)
    return item + rig_transform(animation, point, off_hand) - [0, 24, 0]


def java_third_person(display, point, left=False):
    """Java ItemInHandLayer + ItemTransform for a model point, in Bedrock player axes."""
    view = display['thirdperson_lefthand' if left else 'thirdperson_righthand']
    r = view.get('rotation', [0, 0, 0])
    t = view.get('translation', [0, 0, 0])
    s = view.get('scale', [1, 1, 1])
    f = -1 if left else 1
    applied = rot('x', r[0]) @ rot('y', f * r[1]) @ rot('z', f * r[2])
    local = applied @ (np.array(s, dtype=float) * (np.asarray(point, dtype=float) - 8)) + [f * t[0], t[1], t[2]]
    # Arm pivot, then Rx(-90) Ry(180) and (+-1, 2, -10) px to the fist.
    frame = rot('x', -90) @ rot('y', 180)
    java = np.array([5 if left else -5, 2, 0], dtype=float) + frame @ (local + [f, 2, -10])
    return np.array([java[0], 24 - java[1], java[2]])
