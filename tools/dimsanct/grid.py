"""Dense voxel authoring grid for the dimensional sanctuaries (ODS1 assets).

Local coordinates: the site centre is x=0, z=0; y=0 is the walkable floor layer
(players stand at y=1). Index 0 of the palette means "leave the natural terrain
alone", index 1 is air. Everything else is written by the generator.
"""
from __future__ import annotations

import gzip
import json
import struct
from pathlib import Path

import numpy as np

KEEP = 'ods:keep'
AIR = 'minecraft:air'


_STEM_FIX = (
    ('_bricks_slab', '_brick_slab'),
    ('_bricks_stairs', '_brick_stairs'),
    ('_bricks_wall', '_brick_wall'),
    ('_tiles_slab', '_tile_slab'),
    ('_tiles_stairs', '_tile_stairs'),
    ('_tiles_wall', '_tile_wall'),
    ('purpur_block_slab', 'purpur_slab'),
    ('purpur_block_stairs', 'purpur_stairs'),
    ('quartz_block_slab', 'quartz_slab'),
    ('quartz_block_stairs', 'quartz_stairs'),
)


def mc(state: str) -> str:
    head, sep, tail = state.partition('[')
    ns, colon, name = head.rpartition(':')
    for bad, good in _STEM_FIX:
        if name.endswith(bad):
            name = name[:-len(bad)] + good
            break
    if name == 'chain':
        name = 'iron_chain'
    prefix = (ns + ':') if colon else 'minecraft:'
    return prefix + name + (sep + tail if sep else '')


def hash2(x, z, seed=0):
    """Deterministic integer lattice hash in [0, 1)."""
    h = (np.asarray(x, np.int64) * 374761393 + np.asarray(z, np.int64) * 668265263 + seed * 2246822519) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    h = h ^ (h >> 16)
    return (h & 0xFFFFFF) / float(0x1000000)


def hash3(x, y, z, seed=0):
    h = (np.asarray(x, np.int64) * 374761393 + np.asarray(y, np.int64) * 1103515245
         + np.asarray(z, np.int64) * 668265263 + seed * 2246822519) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    h = h ^ (h >> 16)
    return (h & 0xFFFFFF) / float(0x1000000)


def vnoise(x, z, scale, seed=0):
    """Smooth value noise in [0, 1)."""
    x = np.asarray(x, np.float64) / scale
    z = np.asarray(z, np.float64) / scale
    x0, z0 = np.floor(x), np.floor(z)
    fx, fz = x - x0, z - z0
    fx, fz = fx * fx * (3 - 2 * fx), fz * fz * (3 - 2 * fz)
    a = hash2(x0, z0, seed)
    b = hash2(x0 + 1, z0, seed)
    c = hash2(x0, z0 + 1, seed)
    d = hash2(x0 + 1, z0 + 1, seed)
    return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz


def fbm(x, z, scale, seed=0, octaves=3):
    total, amp, norm = 0.0, 1.0, 0.0
    for o in range(octaves):
        total = total + amp * vnoise(x, z, scale / (2 ** o), seed + o * 17)
        norm += amp
        amp *= 0.5
    return total / norm


class Grid:
    def __init__(self, x0, x1, y0, y1, z0, z1):
        self.x0, self.x1, self.y0, self.y1, self.z0, self.z1 = x0, x1, y0, y1, z0, z1
        self.nx, self.ny, self.nz = x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1
        self.a = np.zeros((self.nx, self.ny, self.nz), np.int16)
        self.palette = [KEEP, AIR]
        self.index = {KEEP: 0, AIR: 1}
        self.X = np.arange(x0, x1 + 1)[:, None] * np.ones((1, self.nz), np.int64)
        self.Z = np.ones((self.nx, 1), np.int64) * np.arange(z0, z1 + 1)[None, :]
        self.R = np.hypot(self.X, self.Z)
        self.TH = (np.degrees(np.arctan2(self.Z, self.X)) + 360.0) % 360.0
        self.chests: list[list] = []
        self.meta: dict = {}

    # ---- palette ----
    def idx(self, state: str) -> int:
        state = mc(state) if state not in (KEEP,) else state
        i = self.index.get(state)
        if i is None:
            i = len(self.palette)
            self.palette.append(state)
            self.index[state] = i
        return i

    # ---- primitives ----
    def _ys(self, y1, y2):
        ya, yb = max(y1, self.y0), min(y2, self.y1)
        return ya - self.y0, yb - self.y0 + 1

    def box(self, x1, y1, z1, x2, y2, z2, state, only=None):
        xa, xb = max(min(x1, x2), self.x0), min(max(x1, x2), self.x1)
        za, zb = max(min(z1, z2), self.z0), min(max(z1, z2), self.z1)
        ya, yb = self._ys(min(y1, y2), max(y1, y2))
        if xa > xb or za > zb or ya >= yb:
            return
        sub = self.a[xa - self.x0:xb - self.x0 + 1, ya:yb, za - self.z0:zb - self.z0 + 1]
        self._write(sub, None, self.idx(state), only)

    def extrude(self, mask, y1, y2, state, only=None):
        """Fill a 2D (x, z) boolean mask between y1..y2 inclusive."""
        ya, yb = self._ys(y1, y2)
        if ya >= yb:
            return
        sub = self.a[:, ya:yb, :]
        self._write(sub, mask[:, None, :], self.idx(state), only)

    def where3(self, mask3, state, only=None):
        self._write(self.a, mask3, self.idx(state), only)

    def _write(self, sub, mask, value, only):
        sel = np.ones(sub.shape, bool) if mask is None else np.broadcast_to(mask, sub.shape).copy()
        if only == 'empty':
            sel &= sub <= 1
        elif only == 'solid':
            sel &= sub > 1
        elif only == 'keep':
            sel &= sub == 0
        elif only == 'air':
            sel &= sub == 1
        elif isinstance(only, (set, list, tuple)):
            sel &= np.isin(sub, [self.idx(s) for s in only])
        sub[sel] = value

    def set(self, x, y, z, state, only=None):
        if not (self.x0 <= x <= self.x1 and self.y0 <= y <= self.y1 and self.z0 <= z <= self.z1):
            return
        i = self.idx(state)
        cur = self.a[x - self.x0, y - self.y0, z - self.z0]
        if only == 'empty' and cur > 1:
            return
        self.a[x - self.x0, y - self.y0, z - self.z0] = i

    def get(self, x, y, z) -> str:
        if not (self.x0 <= x <= self.x1 and self.y0 <= y <= self.y1 and self.z0 <= z <= self.z1):
            return KEEP
        return self.palette[self.a[x - self.x0, y - self.y0, z - self.z0]]

    def add_chest(self, x, y, z, facing, tier):
        self.set(x, y, z, f'chest[facing={facing},type=single,waterlogged=false]')
        self.chests.append([int(x), int(y), int(z), int(tier)])

    # ---- 2D masks ----
    def disc(self, r, cx=0, cz=0):
        return np.hypot(self.X - cx, self.Z - cz) <= r

    def annulus(self, r1, r2, cx=0, cz=0):
        d = np.hypot(self.X - cx, self.Z - cz)
        return (d >= r1) & (d <= r2)

    def rect(self, x1, z1, x2, z2):
        return ((self.X >= min(x1, x2)) & (self.X <= max(x1, x2)) &
                (self.Z >= min(z1, z2)) & (self.Z <= max(z1, z2)))

    def wedge(self, centre_deg, half_deg):
        d = np.abs((self.TH - centre_deg + 180.0) % 360.0 - 180.0)
        return d <= half_deg

    # ---- 3D helpers ----
    def solid_mask(self):
        return self.a > 1

    def dilate(self, mask, n):
        m = mask.copy()
        for _ in range(n):
            n2 = m.copy()
            n2[1:] |= m[:-1]; n2[:-1] |= m[1:]
            n2[:, 1:] |= m[:, :-1]; n2[:, :-1] |= m[:, 1:]
            n2[:, :, 1:] |= m[:, :, :-1]; n2[:, :, :-1] |= m[:, :, 1:]
            m = n2
        return m

    def y_index(self, y):
        return y - self.y0

    # ---- output ----
    def write(self, path: Path, meta: dict):
        meta = dict(meta)
        meta['chests'] = self.chests
        # drop unused palette entries (but keep 0 and 1)
        used = np.unique(self.a)
        remap = {0: 0, 1: 1}
        palette = [KEEP, AIR]
        for i in used:
            if i > 1:
                remap[int(i)] = len(palette)
                palette.append(self.palette[i])
        lut = np.zeros(len(self.palette), np.int16)
        for old, new in remap.items():
            lut[old] = new
        arr = lut[self.a]
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open('wb') as raw, gzip.GzipFile(filename='', mode='wb', fileobj=raw, compresslevel=9, mtime=0) as f:
            f.write(b'ODS1')
            f.write(struct.pack('>hhhhhh', self.x0, self.y0, self.z0, self.nx, self.ny, self.nz))
            f.write(struct.pack('>H', len(palette)))
            for state in palette:
                enc = state.encode('utf8')
                f.write(struct.pack('>H', len(enc)))
                f.write(enc)
            # Layout contract: index = ((x * nz) + z) * ny + y, y fastest.
            flat = np.ascontiguousarray(arr.transpose(0, 2, 1)).reshape(-1)
            change = np.flatnonzero(np.diff(flat)) + 1
            starts = np.concatenate(([0], change))
            ends = np.concatenate((change, [flat.size]))
            runs = []
            for s, e in zip(starts, ends):
                value, length = int(flat[s]), int(e - s)
                while length > 0:
                    chunk = min(length, 65535)
                    runs.append((value, chunk))
                    length -= chunk
            f.write(struct.pack('>I', len(runs)))
            f.write(b''.join(struct.pack('>HH', v, c) for v, c in runs))
            blob = json.dumps(meta, separators=(',', ':'), sort_keys=True).encode('utf8')
            f.write(struct.pack('>I', len(blob)))
            f.write(blob)
        return palette, arr
