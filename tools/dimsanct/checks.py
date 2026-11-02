"""Invariant checks shared by every dimensional sanctuary asset."""
from __future__ import annotations

from collections import deque
import zipfile

import numpy as np

from .grid import Grid
from .render import CLIENT_JARS, split_state

_VALID_BLOCKS: set[str] | None = None


def _valid_blocks() -> set[str]:
    global _VALID_BLOCKS
    if _VALID_BLOCKS is None:
        for jar in CLIENT_JARS:
            if jar.exists():
                with zipfile.ZipFile(jar) as zf:
                    _VALID_BLOCKS = {
                        n.split('/')[-1][:-5]
                        for n in zf.namelist()
                        if n.startswith('assets/minecraft/blockstates/') and n.endswith('.json')
                    }
                break
        else:
            _VALID_BLOCKS = set()
    return _VALID_BLOCKS

NONBLOCKING = {'air', 'lantern', 'soul_lantern', 'iron_chain', 'chain', 'end_rod_x', 'candle', 'torch', 'soul_torch',
               'short_grass', 'vine', 'weeping_vines', 'fire', 'soul_fire', 'light'}
CARPET_SUFFIX = ('_carpet', 'pressure_plate', '_button')
FLUID = {'lava', 'water'}


def classes(palette):
    n = len(palette)
    passable = np.zeros(n, bool)   # body can occupy this cell
    support = np.zeros(n, bool)    # can be stood on
    for i, s in enumerate(palette):
        if s == 'ods:keep':
            continue
        name, props = split_state(s)
        if name in NONBLOCKING or name.endswith(CARPET_SUFFIX):
            passable[i] = True
            support[i] = name.endswith(CARPET_SUFFIX) and False
        elif name in FLUID:
            passable[i] = False
        else:
            support[i] = True
        if name == 'air':
            passable[i] = True
    return passable, support


def reach(g: Grid, start, limit=4_000_000):
    """Flood-fill walkable feet positions from start=(x, y, z) local feet coordinate."""
    passable, support = classes(g.palette)
    a = g.a
    P, S = passable[a], support[a]
    nx, ny, nz = a.shape
    sx, sy, sz = start[0] - g.x0, start[1] - g.y0, start[2] - g.z0
    seen = np.zeros(a.shape, bool)
    if not (P[sx, sy, sz] and P[sx, sy + 1, sz] and S[sx, sy - 1, sz]):
        raise AssertionError(f'start {start} is not standable')
    dq = deque([(sx, sy, sz)])
    seen[sx, sy, sz] = True
    while dq:
        x, y, z = dq.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            tx, tz = x + dx, z + dz
            if not (0 <= tx < nx and 0 <= tz < nz):
                continue
            for dy in (1, 0, -1, -2, -3):
                ty = y + dy
                if not (1 <= ty < ny - 2):
                    continue
                if seen[tx, ty, tz] or not (P[tx, ty, tz] and P[tx, ty + 1, tz] and S[tx, ty - 1, tz]):
                    continue
                if dy == 1 and not P[x, y + 2, z]:
                    continue
                if dy < 0 and not all(P[tx, y + k, tz] for k in range(0, 2)):
                    continue
                seen[tx, ty, tz] = True
                dq.append((tx, ty, tz))
    return seen


def check(g: Grid, meta: dict, name: str):
    problems = []
    arena = meta['arena']
    ar, ah = arena['radius'], arena['height']
    # 1. arena is a clear volume with a continuous floor
    disc = g.R <= ar
    for dy in range(1, ah + 1):
        layer = g.a[:, dy - g.y0, :]
        bad = disc & (layer != 1)
        if bad.any():
            problems.append(f'arena not clear at y={dy}: {int(bad.sum())} cells')
            break
    floor = g.a[:, 0 - g.y0, :]
    weak = disc & (floor <= 1)
    if weak.any():
        problems.append(f'arena floor has {int(weak.sum())} holes')
    # 2. reachability from the entrance mouth
    m = meta['mouth']
    seen = reach(g, (m['x'], m['y'], m['z']))
    def stood(x, y, z):
        i, j, k = x - g.x0, y - g.y0, z - g.z0
        return bool(seen[i, j, k])
    land = meta['landing']
    if not stood(land['x'], land['y'], land['z']):
        problems.append('landing is not reachable on foot from the entrance')
    if not stood(arena['x'], arena['y'] + 1, arena['z']):
        problems.append('arena centre is not reachable on foot from the entrance')
    for x, y, z, tier in g.chests:
        ok = False
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for dy in (0, 1, -1):
                tx, ty, tz = x + dx, y + dy, z + dz
                if 1 <= ty - g.y0 < g.ny - 2 and 0 <= tx - g.x0 < g.nx and 0 <= tz - g.z0 < g.nz and stood(tx, ty, tz):
                    ok = True
        if not ok:
            problems.append(f'chest at {(x, y, z)} cannot be reached on foot')
    # 3. liquids must be sealed horizontally and below
    palette = g.palette
    lava_ids = [i for i, s in enumerate(palette) if split_state(s)[0] in FLUID]
    if lava_ids:
        mask = np.isin(g.a, lava_ids)
        # Air above an open basin is fine (liquid never climbs); sides and underside must be sealed.
        bad = np.zeros_like(mask)
        for axis, sh in ((0, 1), (0, -1), (2, 1), (2, -1), (1, 1)):
            nb = np.roll(g.a, sh, axis=axis)
            edge = [slice(None)] * 3
            edge[axis] = 0 if sh == 1 else -1
            nb[tuple(edge)] = 0
            bad |= mask & ((nb == 0) | (nb == 1))
        if bad.any():
            xs, ys, zs = np.nonzero(bad)
            problems.append(f'{int(bad.sum())} liquid cells can leak (e.g. {(xs[0] + g.x0, ys[0] + g.y0, zs[0] + g.z0)})')
    # 4. chest cells really are chests + all palette block IDs exist in 26.3
    valid = _valid_blocks()
    if valid:
        used_ids = set(int(i) for i in np.unique(g.a) if i > 1)
        for i in sorted(used_ids):
            bname, _ = split_state(g.palette[i])
            if bname not in valid:
                problems.append(f'unknown 26.3 block type in palette: {g.palette[i]}')
    for x, y, z, tier in g.chests:
        if not g.get(x, y, z).startswith('minecraft:chest'):
            problems.append(f'chest list entry {(x, y, z)} is {g.get(x, y, z)}')
    # 5. bounds sanity
    solid_y = np.nonzero((g.a > 1).any(axis=(0, 2)))[0]
    top = int(solid_y.max()) + g.y0
    bottom = int(solid_y.min()) + g.y0
    meta['top'] = top
    meta['bottom'] = bottom
    if problems:
        raise AssertionError(name + ': ' + '; '.join(problems))
    return {'reachable_cells': int(seen.sum()), 'top': top, 'bottom': bottom, 'chests': len(g.chests)}
