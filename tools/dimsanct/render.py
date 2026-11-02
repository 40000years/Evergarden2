"""Vectorised voxel ray-caster for authoring previews of the dimensional sanctuaries.

It renders the exact palette array that is written into the ODS1 asset (no geometry is
invented), with Minecraft-style flood-fill block light and vanilla textures read from a
locally installed client JAR. Previews are renders, not in-game screenshots.
"""
from __future__ import annotations

import io
import math
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

CLIENT_JARS = [Path.home() / 'AppData/Roaming/.minecraft/versions/26.3/26.3.jar',
               Path.home() / '.minecraft/versions/26.3/26.3.jar']

EMISSION = {'lava': 15, 'shroomlight': 15, 'glowstone': 15, 'lantern': 15, 'soul_lantern': 10,
            'magma_block': 3, 'crying_obsidian': 10, 'sea_lantern': 15, 'end_rod': 14,
            'jack_o_lantern': 15, 'campfire': 15, 'soul_campfire': 10, 'fire': 15, 'soul_fire': 10,
            'torch': 14, 'soul_torch': 10, 'amethyst_cluster': 5, 'large_amethyst_bud': 4,
            'beacon': 15, 'ochre_froglight': 15, 'pearlescent_froglight': 15, 'verdant_froglight': 15,
            'respawn_anchor': 10, 'redstone_lamp': 15, 'copper_bulb': 15, 'candle': 3, 'conduit': 15}
EMISSIVE_FULL = {'lava', 'shroomlight', 'glowstone', 'sea_lantern', 'ochre_froglight', 'pearlescent_froglight',
                 'verdant_froglight', 'lantern', 'soul_lantern', 'end_rod', 'magma_block', 'crying_obsidian',
                 'redstone_lamp', 'beacon'}
THIN = ('chain', 'lantern', 'soul_lantern', 'end_rod', 'iron_bars', 'torch', 'soul_torch', 'candle')
FALLBACK = {'lava': (230, 110, 20), 'chest': (150, 100, 40), 'air': (0, 0, 0)}
TRANSLUCENT_TINT = {'glass': (0.85, 0.95, 1.0), 'tinted_glass': (0.3, 0.25, 0.4), 'orange_stained_glass': (1.0, 0.62, 0.25),
                    'red_stained_glass': (1.0, .3, .3), 'magenta_stained_glass': (.9, .35, .9),
                    'purple_stained_glass': (.6, .35, .85), 'cyan_stained_glass': (.3, .85, .9),
                    'light_blue_stained_glass': (.5, .75, 1.0), 'yellow_stained_glass': (1., .95, .35),
                    'black_stained_glass': (.28, .28, .3), 'white_stained_glass': (.95, .95, 1.0)}


def split_state(state: str):
    s = state.split(':', 1)[-1]
    props = {}
    if '[' in s:
        name, rest = s.split('[', 1)
        for part in rest.rstrip(']').split(','):
            if '=' in part:
                k, v = part.split('=', 1)
                props[k] = v
        return name, props
    return s, props


def tex_names(name: str, props: dict):
    """Return (top, side, bottom) texture file stems for a block id."""
    base = name
    for suf in ('_stairs', '_slab', '_wall', '_fence_gate', '_fence', '_pressure_plate', '_button'):
        if base.endswith(suf):
            base = base[:-len(suf)]
            break
    fix = {'nether_brick': 'nether_bricks', 'red_nether_brick': 'red_nether_bricks',
           'polished_blackstone_brick': 'polished_blackstone_bricks', 'end_stone_brick': 'end_stone_bricks',
           'purpur': 'purpur_block', 'cut_copper': 'cut_copper', 'smooth_quartz': 'quartz_block_bottom',
           'quartz': 'quartz_block_side', 'mossy_stone_brick': 'mossy_stone_bricks', 'stone_brick': 'stone_bricks',
           'blackstone_brick': 'polished_blackstone_bricks', 'magma_block': 'magma', 'chest': 'oak_planks',
           'tinted_glass': 'tinted_glass', 'quartz_block': 'quartz_block_side', 'quartz_pillar': 'quartz_pillar',
           'deepslate_tile': 'deepslate_tiles', 'deepslate_brick': 'deepslate_bricks', 'lava': 'lava_still',
           'glowstone': 'glowstone', 'barrel': 'barrel_side', 'end_rod': 'end_rod', 'bone_block': 'bone_block_side',
           'chain': 'chain', 'iron_chain': 'iron_chain', 'soul_lantern': 'soul_lantern', 'lantern': 'lantern'}
    base = fix.get(base, base)
    if base in ('basalt', 'polished_basalt', 'purpur_pillar', 'quartz_pillar', 'bone_block', 'deepslate', 'hay_block'):
        if base == 'purpur_pillar':
            return 'purpur_pillar_top', 'purpur_pillar', 'purpur_pillar_top'
        if base == 'quartz_pillar':
            return 'quartz_pillar_top', 'quartz_pillar', 'quartz_pillar_top'
        if base == 'deepslate':
            return 'deepslate_top', 'deepslate', 'deepslate_top'
        return base + '_top', base + '_side', base + '_top'
    if base.endswith('_log') or base.endswith('_stem'):
        return base + '_top', base, base + '_top'
    if base.endswith('_froglight'):
        return base + '_top', base + '_side', base + '_top'
    if base == 'smooth_basalt':
        return 'smooth_basalt', 'smooth_basalt', 'smooth_basalt'
    if base == 'quartz_block_bottom':
        return 'quartz_block_bottom', 'quartz_block_bottom', 'quartz_block_bottom'
    if base == 'shroomlight' or base == 'crying_obsidian':
        return base, base, base
    return base, base, base


class Textures:
    def __init__(self):
        self.jar = None
        for p in CLIENT_JARS:
            if p.exists():
                self.jar = zipfile.ZipFile(p)
                break
        self.cache = {}
        self.missing = set()

    def tile(self, stem: str):
        if stem in self.cache:
            return self.cache[stem]
        arr = None
        if self.jar:
            try:
                img = Image.open(io.BytesIO(self.jar.read(f'assets/minecraft/textures/block/{stem}.png'))).convert('RGBA')
                img = img.crop((0, 0, img.width, img.width)).resize((16, 16), Image.Resampling.NEAREST)
                arr = np.asarray(img).astype(np.float32)
                if stem.startswith(('grass_block_top',)):
                    arr[..., :3] *= np.array([.49, .72, .29])
            except KeyError:
                self.missing.add(stem)
        if arr is None:
            rng = np.random.default_rng(abs(hash(stem)) % 2 ** 32)
            base = np.array(FALLBACK.get(stem, (150, 145, 140)), np.float32)
            arr = np.zeros((16, 16, 4), np.float32)
            arr[..., :3] = base * rng.uniform(.9, 1.05, (16, 16, 1))
            arr[..., 3] = 255
        self.cache[stem] = arr
        return arr


def classify(palette, textures: Textures):
    n = len(palette)
    names, kinds = [], []
    tex = np.zeros((n, 6, 16, 16, 3), np.float32)
    alpha = np.ones((n, 6, 16, 16), np.float32)
    emis = np.zeros(n, np.int32)
    full_emit = np.zeros(n, bool)
    opaque = np.zeros(n, bool)
    thin = np.zeros(n, np.int8)  # 0 solid, 1 post, 2 lantern, 3 bars/pane, 4 chain
    tint = np.ones((n, 3), np.float32)
    transl = np.zeros(n, bool)
    for i, state in enumerate(palette):
        if state in ('ods:keep', 'minecraft:air'):
            names.append(state)
            continue
        name, props = split_state(state)
        names.append(name)
        t_top, t_side, t_bot = tex_names(name, props)
        axis = props.get('axis', 'y')
        faces = []
        for f in range(6):  # +x -x +y -y +z -z
            if f == 2:
                stem = t_top
            elif f == 3:
                stem = t_bot
            else:
                stem = t_side
            if axis == 'x' and f in (0, 1) and t_top != t_side:
                stem = t_top
            if axis == 'z' and f in (4, 5) and t_top != t_side:
                stem = t_top
            if axis in ('x', 'z') and f in (2, 3) and t_top != t_side:
                stem = t_side
            faces.append(textures.tile(stem))
        for f in range(6):
            tex[i, f] = faces[f][..., :3]
            alpha[i, f] = faces[f][..., 3] / 255.0
        emis[i] = EMISSION.get(name, 0)
        full_emit[i] = name in EMISSIVE_FULL
        if name in TRANSLUCENT_TINT:
            transl[i] = True
            tint[i] = TRANSLUCENT_TINT[name]
        elif name.endswith('_pane') or name == 'iron_bars':
            thin[i] = 3
        elif name in ('lantern', 'soul_lantern', 'candle'):
            thin[i] = 2
        elif name in ('chain', 'iron_chain', 'end_rod', 'torch', 'soul_torch'):
            thin[i] = 4
        elif name.endswith('_fence') or name.endswith('_wall') or name.endswith('_fence_gate'):
            thin[i] = 1
        elif name.endswith(('_carpet', 'pressure_plate')):
            thin[i] = 5
        elif name in ('fire', 'soul_fire') or name.endswith(('_vines', 'amethyst_cluster', 'large_amethyst_bud')):
            thin[i] = 4
        opaque[i] = not (transl[i] or thin[i] != 0)
        if name == 'lava':
            opaque[i] = True
    return names, tex, alpha, emis, full_emit, opaque, thin, tint, transl


def light_volume(arr, emis, opaque, iterations=15, extra=None):
    e = emis[arr].astype(np.int16)
    if extra is not None:
        e = np.maximum(e, extra)
    transparent = ~opaque[arr]
    transparent[arr == 0] = True
    L = e.copy()
    for _ in range(iterations):
        best = L.copy()
        for axis in range(3):
            for sh in (1, -1):
                s = np.roll(L, sh, axis=axis) - 1
                if sh == 1:
                    sl = [slice(None)] * 3; sl[axis] = 0; s[tuple(sl)] = 0
                else:
                    sl = [slice(None)] * 3; sl[axis] = -1; s[tuple(sl)] = 0
                cand = np.where(transparent, s, 0)
                best = np.maximum(best, cand)
        if (best == L).all():
            break
        L = best
    return L


def font(size, bold=False):
    for p in ('C:/Windows/Fonts/segoeuib.ttf' if bold else 'C:/Windows/Fonts/segoeui.ttf',
              'C:/Windows/Fonts/arial.ttf'):
        if Path(p).exists():
            return ImageFont.truetype(p, size)
    return ImageFont.load_default(size=size)


def render(arr, palette, bounds, out: Path, *, cam, target, fov=70, size=(1600, 900), ambient=0.06,
           gamma=1.0, fog=(0.0035, (20, 8, 8)), title=None, subtitle=None, up=(0, 1, 0),
           exposure=1.0, max_steps=900, textures=None, extra_light=None, sky=None, tint_mul=(1, 1, 1),
           light_boost=0.0):
    """arr[x, y, z] holds palette indices; bounds=(x0, y0, z0) world-local origin of arr."""
    textures = textures or Textures()
    names, tex, alpha, emis, full_emit, opaque, thin, tint, transl = classify(palette, textures)
    light = light_volume(arr, emis, opaque, extra=extra_light)
    W, H = size
    nx, ny, nz = arr.shape
    ox, oy, oz = bounds
    cam = np.array(cam, np.float64) - np.array([ox, oy, oz], np.float64)
    tgt = np.array(target, np.float64) - np.array([ox, oy, oz], np.float64)
    fwd = tgt - cam
    fwd /= np.linalg.norm(fwd)
    right = np.cross(fwd, np.array(up, float))
    right /= np.linalg.norm(right)
    upv = np.cross(right, fwd)
    aspect = W / H
    f = math.tan(math.radians(fov) / 2)
    px = (np.arange(W) + .5) / W * 2 - 1
    py = 1 - (np.arange(H) + .5) / H * 2
    PX, PY = np.meshgrid(px * f * aspect, py * f)
    d = (fwd[None, None, :] + PX[..., None] * right + PY[..., None] * upv).reshape(-1, 3)
    d /= np.linalg.norm(d, axis=1)[:, None]
    N = d.shape[0]
    d = d.astype(np.float64)
    o = np.broadcast_to(cam, (N, 3)).copy()
    # Move rays that start outside the grid to the grid box entry
    lo = np.zeros(3)
    hi = np.array([nx, ny, nz], np.float64)
    with np.errstate(divide='ignore', invalid='ignore'):
        inv = 1.0 / np.where(np.abs(d) < 1e-12, 1e-12, d)
        t0 = (lo - o) * inv
        t1 = (hi - o) * inv
    tmin = np.minimum(t0, t1).max(axis=1)
    tmax = np.maximum(t0, t1).min(axis=1)
    inside_start = np.all((cam >= lo) & (cam < hi))
    hit_box = tmax > np.maximum(tmin, 0)
    t_start = np.where(inside_start, 0.0, np.maximum(tmin, 0.0) + 1e-4)
    alive = hit_box | inside_start
    pos = o + d * t_start[:, None]
    cell = np.floor(pos).astype(np.int64)
    cell = np.clip(cell, 0, [nx - 1, ny - 1, nz - 1])
    step = np.where(d >= 0, 1, -1).astype(np.int64)
    tdelta = np.abs(inv)
    nextb = np.where(d >= 0, cell + 1, cell).astype(np.float64)
    tmaxs = (nextb - o) * inv
    tmaxs = np.where(np.isfinite(tmaxs), tmaxs, 1e30)
    color = np.zeros((N, 3), np.float32)
    through = np.ones((N, 3), np.float32)
    bg = np.array(sky if sky is not None else fog[1], np.float32)
    done = ~alive
    dist = np.zeros(N, np.float32)
    last_axis = np.zeros(N, np.int64)
    active = np.flatnonzero(alive)
    first = True
    for it in range(max_steps):
        if active.size == 0:
            break
        c = cell[active]
        v = arr[c[:, 0], c[:, 1], c[:, 2]]
        solid_hit = np.zeros(active.size, bool)
        nonair = v > 1
        if nonair.any():
            idx = np.flatnonzero(nonair)
            vv = v[idx]
            th = thin[vv]
            t_entry = np.where(first, 0.0, 0.0)
            # exact entry point of the ray into the voxel
            ax = last_axis[active[idx]]
            tt = np.where(it == 0, t_start[active[idx]],
                          tmaxs[active[idx], ax] - tdelta[active[idx], ax])
            if it == 0:
                tt = t_start[active[idx]]
            p = o[active[idx]] + d[active[idx]] * tt[:, None]
            frac = p - c[idx]
            ok = np.zeros(idx.size, bool)
            solidlike = (th == 0) & (~transl[vv])
            ok |= solidlike
            # thin shapes
            fx, fy, fz = frac[:, 0], frac[:, 1], frac[:, 2]
            ok |= (th == 1) & (np.abs(fx - .5) < .22) & (np.abs(fz - .5) < .22)
            ok |= (th == 2) & (np.abs(fx - .5) < .19) & (np.abs(fz - .5) < .19) & (fy < .55)
            ok |= (th == 3) & ((np.abs(fx - .5) < .07) | (np.abs(fz - .5) < .07))
            ok |= (th == 4) & (np.abs(fx - .5) < .09) & (np.abs(fz - .5) < .09)
            ok |= (th == 5) & (fy < .07)
            # Translucent: tint ray and continue
            tr = transl[vv]
            if tr.any():
                through[active[idx[tr]]] *= tint[vv[tr]]
            hit_idx = idx[ok]
            solid_hit[hit_idx] = True
            if hit_idx.size:
                rid = active[hit_idx]
                vv2 = v[hit_idx]
                axh = last_axis[rid] if it > 0 else np.argmax(np.abs(d[rid]), axis=1)
                sg = step[rid, axh]
                # face index: axis 0 -> +x(0)/-x(1); axis1 -> +y(2)/-y(3); axis 2 -> +z(4)/-z(5)
                # the ray enters through the face opposite to its travel direction
                face = axh * 2 + np.where(sg > 0, 1, 0)
                pe = frac[ok]
                # texture coordinates
                u = np.where(axh == 0, pe[:, 2], pe[:, 0])
                vcoord = np.where(axh == 1, pe[:, 2], 1 - pe[:, 1])
                ui = np.clip((u * 16).astype(int), 0, 15)
                vi = np.clip((vcoord * 16).astype(int), 0, 15)
                col = tex[vv2, face, vi, ui]
                al = alpha[vv2, face, vi, ui]
                # cell the ray came from: neighbour opposite to travel
                cc = cell[rid].copy()
                cc[np.arange(rid.size), axh] -= sg
                cc = np.clip(cc, 0, [nx - 1, ny - 1, nz - 1])
                lv = light[cc[:, 0], cc[:, 1], cc[:, 2]].astype(np.float32)
                shade = np.array([.62, .62, 1.0, .5, .8, .8], np.float32)[face]
                br = np.clip((lv / 15.0) ** gamma, 0, 1) * (1 - ambient) + ambient
                br = np.clip(br + light_boost, 0, 1)
                # warm bias from strong light
                lit = col * (br * shade)[:, None]
                # emissive blocks are fully bright
                fe = full_emit[vv2]
                lit[fe] = col[fe] * np.clip(1.0 + 0.0, 0, 2)
                tw = np.stack([1.0 + .12 * (lv / 15.0), 1.0 + .0 * lv, 1.0 - .18 * (lv / 15.0)], axis=1)
                lit = lit * tw
                lit *= exposure
                lit *= np.array(tint_mul, np.float32)
                tdist = tt[ok] if it > 0 else t_start[rid]
                fogf = 1 - np.exp(-fog[0] * np.maximum(tdist, 0)) if fog else 0
                lit = lit * (1 - np.asarray(fogf)[:, None] if fog else 1) + (np.asarray(fogf)[:, None] * np.array(fog[1], np.float32) if fog else 0)
                color[rid] = lit * through[rid]
        # advance
        alive_idx = np.flatnonzero(~solid_hit)
        keep = active[alive_idx]
        if keep.size == 0:
            active = keep
            break
        tm = tmaxs[keep]
        ax = np.argmin(tm, axis=1)
        last_axis[keep] = ax
        ar = np.arange(keep.size)
        tmaxs[keep, ax] += tdelta[keep, ax]
        cell[keep, ax] += step[keep, ax]
        cc = cell[keep]
        inb = (cc[:, 0] >= 0) & (cc[:, 0] < nx) & (cc[:, 1] >= 0) & (cc[:, 1] < ny) & (cc[:, 2] >= 0) & (cc[:, 2] < nz)
        # rays that exit the grid show the background
        gone = keep[~inb]
        if gone.size:
            color[gone] = bg * through[gone]
        active = keep[inb]
        first = False
    # rays that ran out of steps
    if active.size:
        color[active] = bg
    img = np.clip(color.reshape(H, W, 3), 0, 255).astype(np.uint8)
    im = Image.fromarray(img)
    if title:
        draw = ImageDraw.Draw(im, 'RGBA')
        draw.rectangle((0, 0, W, int(H * .1)), fill=(0, 0, 0, 120))
        draw.text((int(W * .02), int(H * .012)), title, fill=(255, 240, 220, 255), font=font(int(H * .04), True))
        if subtitle:
            draw.text((int(W * .02), int(H * .06)), subtitle, fill=(220, 210, 200, 235), font=font(int(H * .022)))
    out.parent.mkdir(parents=True, exist_ok=True)
    im.save(out, optimize=True)
    return out
