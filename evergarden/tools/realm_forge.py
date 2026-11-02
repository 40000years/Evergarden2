"""Shared toolkit for the four realm bosses that guard the Nether and End sanctuaries.

Builds on boss_forge, the Arkhaia/Seraphon technique: oriented cuboids instead of
grid blocks, alpha-cut sheets for thin silhouettes, every face painted texel by texel
from a dark-to-light palette ramp with ordered dithering, bevels, carving and glowing
inlays, mirrored twins sharing texels, aura bones on their own looping lane.

The realm surfaces added here: lava-seamed masonry, chiselled rock split by glowing
fissures (optionally voxel-tiled), porous bone with soul cracks, faceted crystal,
stained-crystal wing mosaics with eyespots, molten flow, chain links, burnt banners.
Previews are lit like Minecraft entities, because ModelEngine drops the shade flag
and every face is shaded directionally in game.
"""
import math
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

import boss_forge
from boss_forge import FACES, Painter, _erode, _noise, _tone_to_rgb, build_model, hex_rgb, rot, unit

CLIPS = ('idle', 'walk', 'attack_windup', 'attack_active', 'attack_recover', 'skill_windup', 'skill_active', 'skill_recover',
         'ultimate_windup', 'ultimate_active', 'ultimate_recover', 'stun', 'transition', 'spawn')


def _dilate(mask, steps=1):
    image = Image.fromarray((np.asarray(mask) * 255).astype('uint8'))
    for _ in range(steps):
        image = image.filter(ImageFilter.MaxFilter(3))
    return np.asarray(image) > 127


def _voronoi(w, h, count, rng):
    """Nearest and second-nearest seed distance for every texel (cell index, d1, d2, seed xs, seed ys)."""
    xs = np.array([rng.uniform(0, w) for _ in range(count)])
    ys = np.array([rng.uniform(0, h) for _ in range(count)])
    gy, gx = np.mgrid[0:h, 0:w] + .5
    d = (gx[..., None] - xs) ** 2 + (gy[..., None] - ys) ** 2
    idx = np.argmin(d, axis=2)
    d1 = np.take_along_axis(d, idx[..., None], 2)[..., 0]
    np.put_along_axis(d, idx[..., None], np.inf, 2)
    d2 = d.min(axis=2)
    return idx, np.sqrt(d1), np.sqrt(d2), xs, ys


def _cracks(w, h, rng, count, steps=(5, 12), step_len=(1.2, 2.4), branch=.25, wiggle=.55, ss=3):
    """Branching random-walk fissures, about one texel wide."""
    img = Image.new('L', (w * ss, h * ss), 0)
    draw = ImageDraw.Draw(img)

    def walk(x, y, a, n, depth):
        pts = [(x, y)]
        for _ in range(n):
            a += rng.gauss(0, wiggle)
            length = rng.uniform(*step_len)
            x, y = x + math.cos(a) * length, y + math.sin(a) * length
            pts.append((x, y))
            if depth < 2 and rng.random() < branch:
                walk(x, y, a + rng.choice((-1, 1)) * rng.uniform(.5, 1.2), max(2, n // 2), depth + 1)
        draw.line([(px * ss, py * ss) for px, py in pts], fill=255, width=ss)

    for _ in range(count):
        walk(rng.uniform(0, w), rng.uniform(0, h), rng.uniform(0, math.tau), rng.randint(*steps), 0)
    return np.asarray(img.resize((w, h), Image.Resampling.BOX)) > 70


def _streaks(w, h, seed, along_scale):
    """Noise stretched vertically: flowing lava and dripping light."""
    short = _noise(w, max(2, h // 6), seed, max(1.5, along_scale))
    return np.asarray(Image.fromarray((short * 255).astype('uint8')).resize((w, h), Image.Resampling.BILINEAR), float) / 255


class RealmPainter(Painter):
    """boss_forge's painter plus the realm surfaces."""

    def _seed(self, ctx):
        return ctx['part']['seed'] * 7 + FACES.index(ctx['face']) * 101

    def _glow_seams(self, rgb, mask, accent, hot=-2, bleed=.5):
        """Paint glowing lines: a hot core, a saturated halo and a dim outer bleed into the surface."""
        rgb = rgb.astype(float)
        outer = _dilate(mask, 2) & ~_dilate(mask, 1)
        halo = _dilate(mask, 1) & ~mask
        rgb[outer] = rgb[outer] * (1 - bleed * .45) + accent[1] * bleed * .45
        rgb[halo] = rgb[halo] * (1 - bleed) + accent[max(1, len(accent) // 2 - 1)] * bleed
        rgb[mask] = accent[hot]
        return rgb

    # -- masonry ---------------------------------------------------------------
    def style_brick(self, ctx):
        """Blackstone courses: staggered bricks with chipped bevels; heat turns mortar into lava."""
        mat, w, h, p, rng = ctx['mat'], ctx['w'], ctx['h'], ctx['paint'], ctx['rng']
        tpu = self._texels_per_unit(ctx)
        seed = self._seed(ctx)
        bu, bv = p.get('brick', (2.4, 1.2))
        if ctx['face'] in ('up', 'down'):
            bv = bu * .75
        bw, bh = max(4, int(round(bu * tpu))), max(3, int(round(bv * tpu)))
        ys, xs = np.mgrid[0:h, 0:w]
        table = np.random.default_rng(seed).random((h // bh + 2, w // bw + 3))
        shift = (np.random.default_rng(seed + 1).random(h // bh + 2) * bw).astype(int)
        row = ys // bh
        sx = xs + shift[row]
        col = sx // bw
        bx, by = sx % bw, ys % bh
        tone = .47 + (table[row, col] - .5) * .28
        tone = tone + (_noise(w, h, seed, 1.6) - .5) * .12 + (_noise(w, h, seed + 7, 5.0) - .5) * .1
        if ctx['side']:
            tone = tone + (.5 - ctx['fy']) * .08
        mortar = (bx == 0) | (by == 0)
        tone = np.where((bx == 1) & ~mortar, tone + .12, tone)
        tone = np.where((by == 1) & ~mortar, tone + .15, tone)
        tone = np.where(bx == bw - 1, tone - .1, tone)
        tone = np.where(by == bh - 1, tone - .16, tone)
        pits = _noise(w, h, seed + 3, 1.0) > .8
        tone = np.where(pits & ~mortar, tone - .15, tone)
        tone = np.where(mortar, .08 + _noise(w, h, seed + 4, 2.0) * .07, tone)
        tone = self._bevel(ctx, tone, .16, .22)
        rgb = self._finish(ctx, tone, mat.ramp).astype(float)
        heat = p.get('heat', .3)
        if mat.accent is not None and heat > 0:
            field = _noise(w, h, seed + 11, max(3.0, tpu * p.get('heat_scale', 2.4)))
            if ctx['side']:
                field = field + (ctx['fy'] - .5) * p.get('heat_bias', 0)
            level = np.clip((field - (1 - heat)) / max(heat, 1e-3), 0, 1)
            hot = mortar & (level > 0)
            lava = _tone_to_rgb(.35 + level * .65, mat.accent, False)
            halo = _dilate(hot, 1) & ~hot
            rgb[halo] = rgb[halo] * .55 + mat.accent[1] * .45
            rgb[hot] = lava[hot]
            count = int(heat * w * h / max(70.0, (tpu * 3.2) ** 2))
            if count:
                fissure = _cracks(w, h, rng, count, steps=(3, 7), step_len=(max(1.0, tpu * .4), max(1.5, tpu * .8)), branch=.2)
                fissure &= (level > .2) & ~hot
                rgb = self._glow_seams(rgb, fissure, mat.accent, hot=-2, bleed=.45)
        return rgb, None

    # -- chiselled rock ----------------------------------------------------------
    def style_rock(self, ctx):
        """Hewn rock in tilted facets; heat splits it with glowing fissures. Optional voxel tiles and glyph."""
        mat, w, h, p, rng = ctx['mat'], ctx['w'], ctx['h'], ctx['paint'], ctx['rng']
        tpu = self._texels_per_unit(ctx)
        seed = self._seed(ctx)
        facet = max(3.0, p.get('facet', 2.2) * tpu)
        count = int(np.clip(w * h / (facet * facet), 2, 64))
        cell, d1, d2, cx, cy = _voronoi(w, h, count, rng)
        crand = np.array([rng.random() for _ in range(count)])
        tilt = np.array([rng.uniform(-1, 1) for _ in range(count)])
        ys, xs = np.mgrid[0:h, 0:w] + .5
        lean = -((xs - cx[cell]) + (ys - cy[cell])) / facet
        tone = .45 + (crand[cell] - .5) * .22 + tilt[cell] * lean * .07
        tone = tone + (_noise(w, h, seed, 1.5) - .5) * .12 + (_noise(w, h, seed + 2, 4.0) - .5) * .1
        tone = np.where((d2 - d1) < 1.0, tone - .15, tone)
        tile = p.get('tiles')
        if tile:
            tp = max(3, int(round(tile * tpu)))
            gx, gy = np.mgrid[0:h, 0:w][1] % tp, np.mgrid[0:h, 0:w][0] % tp
            grid = (gx == 0) | (gy == 0)
            tone = np.where(grid, tone - .17, tone)
            tone = np.where(((gx == 1) | (gy == 1)) & ~grid, tone + .09, tone)
            tone = np.where(((gx == tp - 1) | (gy == tp - 1)) & ~grid, tone - .06, tone)
        tone = self._bevel(ctx, tone, .18, .24)
        rgb = self._finish(ctx, tone, mat.ramp).astype(float)
        heat = p.get('heat', 0)
        if mat.accent is not None and heat > 0 and min(w, h) >= 10:
            expected = heat * w * h / max(60.0, (tpu * 3.4) ** 2)
            count = int(expected) + (rng.random() < expected % 1)
            if count:
                steps = (max(3, int(min(w, h) / max(tpu, 1) * .8)), max(4, int(min(w, h) / max(tpu, 1) * 1.6)))
                fissure = _cracks(w, h, rng, count, steps=steps, step_len=(max(1.0, tpu * .45), max(1.8, tpu * .9)), branch=.22,
                                  wiggle=.32)
                rgb = self._glow_seams(rgb, fissure, mat.accent, hot=-2 if heat > .6 else -3, bleed=.6)
        glyph = p.get('glyph')
        if glyph and mat.accent is not None and ctx['face'] in glyph and min(w, h) >= 10:
            rgb = self._glyph(ctx, rgb, mat)
        return rgb, None

    def _glyph(self, ctx, rgb, mat):
        """Hexagonal void sigil, as carved on the sanctuary's floating pillars."""
        w, h = ctx['w'], ctx['h']
        img = Image.new('L', (w * 4, h * 4), 0)
        d = ImageDraw.Draw(img)
        cx, cy, r = w * 2, h * 4 * .5, min(w, h) * 4 * .34
        for scale, width in ((1.0, 5), (.55, 4)):
            pts = [(cx + math.cos(math.pi / 6 + k * math.pi / 3) * r * scale, cy + math.sin(math.pi / 6 + k * math.pi / 3) * r * scale)
                   for k in range(6)]
            d.polygon(pts, outline=255, width=width)
        d.ellipse((cx - r * .16, cy - r * .16, cx + r * .16, cy + r * .16), fill=255)
        mask = np.asarray(img.resize((w, h), Image.Resampling.BOX)) > 90
        return self._glow_seams(rgb, mask, mat.accent, hot=-1, bleed=.6)

    # -- bone ---------------------------------------------------------------------
    def style_bone(self, ctx):
        """Weathered bone: growth lines, pores, soot and hairline cracks; soul light seeps through."""
        mat, w, h, p, rng = ctx['mat'], ctx['w'], ctx['h'], ctx['paint'], ctx['rng']
        tpu = self._texels_per_unit(ctx)
        seed = self._seed(ctx)
        along = ctx['fy'] if ctx['side'] else ctx['fx']
        tone = .64 + (_noise(w, h, seed, 1.4) - .5) * .08 + (_noise(w, h, seed + 1, 5.0) - .5) * .16
        tone = tone + np.sin(along * h * .9 + _noise(w, h, seed + 2, 3.0) * 4) * .03
        tone = np.where(_noise(w, h, seed + 5, .9) > .86, tone - .13, tone)
        tone = tone - np.clip(_noise(w, h, seed + 6, max(2.0, tpu * 1.2)) - .58, 0, 1) * .38
        if ctx['side']:
            tone = tone - ctx['fy'] ** 3 * .14
        count = max(0, int(w * h / max(60.0, (tpu * 4) ** 2)))
        if count:
            dark = _cracks(w, h, rng, count, steps=(2, 5), step_len=(max(1.0, tpu * .3), max(1.4, tpu * .6)), branch=.15)
            tone = np.where(dark, tone - .3, tone)
        tone = self._bevel(ctx, tone, .14, .22)
        rgb = self._finish(ctx, tone, mat.ramp, dither=False).astype(float)
        soul = p.get('soul', 0)
        if soul and mat.accent is not None:
            count = max(1, int(soul * w * h / max(50.0, (tpu * 3.2) ** 2)))
            seams = _cracks(w, h, rng, count, steps=(3, 8), step_len=(max(1.0, tpu * .4), max(1.6, tpu * .8)), branch=.25)
            rgb = self._glow_seams(rgb, seams, mat.accent, hot=-2, bleed=.5)
        return rgb, None

    # -- crystal ------------------------------------------------------------------
    def style_crystal(self, ctx):
        """Faceted crystal: bright tip, slanted facet bands with lit edges, inner glow and glints."""
        mat, w, h, p, rng = ctx['mat'], ctx['w'], ctx['h'], ctx['paint'], ctx['rng']
        fx, fy = ctx['fx'], ctx['fy']
        if ctx['part']['shape'] is not None:
            along = ctx['t']
        elif ctx['side']:
            along = 1 - fy
        else:
            along = np.full_like(fx, .75)
        bands = p.get('bands', 3)
        slope = rng.uniform(-.8, .8)
        u = (fx + (fy - .5) * slope) * bands
        frac = u - np.floor(u)
        tone = .34 + along * .36 + (np.floor(u) % 2) * .11 + (1 - np.abs(fx - .5) * 2) * .1
        tone = tone + (_noise(w, h, self._seed(ctx), 2.0) - .5) * .1
        tone = np.where(frac < max(.07, bands * 1.2 / max(w, 1)), tone + .28, tone)
        for _ in range(int(w * h / 140) + 1):
            tone[rng.randrange(h), rng.randrange(w)] = 1.2
        tone = self._bevel(ctx, tone, .3, .1)
        return self._finish(ctx, tone, mat.ramp), None

    # -- stained crystal wings --------------------------------------------------
    def style_mosaic(self, ctx):
        """Wing glass: crystal panes in gilded leading, a diamond eyespot and radial veins."""
        mat, w, h, p, rng = ctx['mat'], ctx['w'], ctx['h'], ctx['paint'], ctx['rng']
        tpu = self._texels_per_unit(ctx)
        cell_px = max(4.0, p.get('cell', 1.4) * tpu)
        count = int(np.clip(w * h / (cell_px ** 2), 6, 150))
        cell, d1, d2, cx, cy = _voronoi(w, h, count, rng)
        s, t = ctx['s'], ctx['t']
        root = p.get('root', (0, 0))
        dist = np.clip(np.hypot(s - root[0], t - root[1]), 0, 1)
        crand = np.array([rng.random() for _ in range(count)])
        ys, xs = np.mgrid[0:h, 0:w] + .5
        lean = (-(xs - cx[cell]) - (ys - cy[cell])) / cell_px
        tone = .42 + crand[cell] * .36 + dist * .14 + lean * .13 + (_noise(w, h, self._seed(ctx), 2.0) - .5) * .06
        rgb = _tone_to_rgb(tone, mat.ramp, True).astype(float)
        eye = p.get('eye')
        if eye and mat.accent is not None:
            ex, ey, rx, ry = eye
            e = np.abs(s - ex) / rx + np.abs(t - ey) / ry
            bands = ((0, .22, mat.accent, .98), (.22, .4, mat.accent, .62), (.4, .5, mat.trim, .15),
                     (.5, .7, mat.accent, .28), (.7, .78, mat.ramp, 1.0))
            for lo, hi, ramp, level in bands:
                zone = (e >= lo) & (e < hi)
                shaded = _tone_to_rgb(np.full((h, w), level) + lean * .1 + (crand[cell] - .5) * .1, ramp, True)
                rgb[zone] = shaded[zone]
        veins = p.get('veins', 0)
        if veins and mat.trim is not None:
            angle = np.arctan2(t - root[1], s - root[0])
            line = np.abs(np.sin(angle * veins * 2)) < (1.4 / max(8.0, dist * min(w, h) * math.pi))
            rgb[line & (dist > .08)] = mat.trim[3]
        lead = (d2 - d1) < p.get('lead', 1.15)
        rgb[lead] = np.array(hex_rgb(mat.options['lead']), float) if 'lead' in mat.options else mat.trim[1]
        if ctx['part']['shape'] is not None:
            outer = ctx['alpha'] & ~_erode(ctx['alpha'], 1)
            inner = _erode(ctx['alpha'], 1) & ~_erode(ctx['alpha'], 2)
            rgb[inner] = mat.trim[-2]
            rgb[outer] = mat.trim[1]
        return rgb, None

    # -- molten flow -------------------------------------------------------------
    def style_lava(self, ctx):
        """Lava running downhill: streaks along the fall, hot core, drifting dark crust."""
        mat, w, h, p = ctx['mat'], ctx['w'], ctx['h'], ctx['paint']
        seed = self._seed(ctx)
        if ctx['face'] in ('up', 'down'):
            streak = _noise(w, h, seed, 3.0)
        else:
            streak = _streaks(w, h, seed, max(1.5, w / 5))
        core = 1 - np.abs(ctx['s'] - .5) * 2 if p.get('stream') else np.full((h, w), .6)
        tone = .32 + streak * .45 + core * .22
        crust = _noise(w, h, seed + 9, 2.5) > p.get('crust', .74)
        tone = np.where(crust, .06 + _noise(w, h, seed + 10, 1.2) * .14, tone)
        return self._finish(ctx, tone, mat.ramp), None

    # -- fire ---------------------------------------------------------------------
    def style_flame(self, ctx):
        """Pixel fire: a dark rim, a bright heart that rises from the base, flicker streaks along the rise."""
        mat, w, h = ctx['mat'], ctx['w'], ctx['h']
        if ctx['part']['shape'] is None:
            return super().style_flame(ctx)
        depth = np.zeros((h, w))
        m = ctx['alpha']
        for _ in range(4):
            m = _erode(m, 1)
            depth += m
        streak = _streaks(w, h, self._seed(ctx), max(1.5, w / 7))
        t = ctx['t']
        tone = .18 + depth / 4 * .5 + (1 - t) * .22 + (streak - .5) * .22
        tone = np.where(depth == 0, .12 + (1 - t) * .1, tone)
        return self._finish(ctx, tone, mat.ramp, dither=True), None

    # -- chain -------------------------------------------------------------------
    def style_chain(self, ctx):
        """One forged link: an oval ring cut out of a thin plate."""
        mat, p = ctx['mat'], ctx['paint']
        fx, fy = ctx['fx'], ctx['fy']
        e = ((fx - .5) / .5) ** 2 + ((fy - .5) / .5) ** 2
        alpha = (e <= 1.0) & (e >= p.get('hole', .42) ** 2)
        ring = np.sqrt(np.clip(e, 0, 1))
        tone = .42 + (.5 - fy) * .3 + (.5 - fx) * .14 + (1 - np.abs(ring - .72) * 4) * .12
        tone = tone + (_noise(ctx['w'], ctx['h'], self._seed(ctx), 1.2) - .5) * .12
        return self._finish(ctx, tone, mat.ramp), alpha

    # -- burnt war banner ---------------------------------------------------------
    def style_banner(self, ctx):
        """Woven banner whose frayed hem smoulders."""
        rgb, alpha = self.style_cloth(ctx)
        mat = ctx['mat']
        if mat.accent is not None and ctx['part']['shape'] is not None:
            edge = ctx['alpha'] & ~_erode(ctx['alpha'], 2) & (ctx['t'] < ctx['paint'].get('burn', .3))
            ember = edge & ~_erode(ctx['alpha'], 1)
            rgb = rgb.astype(float)
            rgb[edge] = rgb[edge] * .35 + mat.accent[1] * .65
            rgb[ember] = mat.accent[-2]
        return rgb, alpha

    def _emblem(self, ctx, rgb, emblem, mat):
        if emblem.get('kind') != 'crown':
            return super()._emblem(ctx, rgb, emblem, mat)
        w, h = ctx['w'], ctx['h']
        img = Image.new('L', (w * 4, h * 4), 0)
        d = ImageDraw.Draw(img)
        cx, cy = w * 2, h * 4 * emblem.get('y', .62)
        r = min(w, h) * 4 * emblem.get('r', .3)
        # A fortress crown over a downward blade.
        d.polygon([(cx - r, cy), (cx - r, cy - r * .9), (cx - r * .55, cy - r * .45), (cx, cy - r * 1.1),
                   (cx + r * .55, cy - r * .45), (cx + r, cy - r * .9), (cx + r, cy)], fill=255)
        d.rectangle((cx - r, cy + r * .12, cx + r, cy + r * .3), fill=255)
        d.polygon([(cx - r * .14, cy + r * .4), (cx + r * .14, cy + r * .4), (cx, cy + r * 1.7)], fill=255)
        d.rectangle((cx - r * .45, cy + r * .4, cx + r * .45, cy + r * .52), fill=255)
        mask = np.asarray(img.resize((w, h), Image.Resampling.BOX)) > 110
        rgb[mask] = mat.trim[-1]
        edge = _dilate(mask, 1) & ~mask
        rgb[edge] = mat.trim[1]


def build_realm(forge, clips, identifier, hitbox, meta, size=1024, density=8.0):
    """boss_forge.build_model painted with the realm surfaces."""
    original = boss_forge.Painter
    boss_forge.Painter = RealmPainter
    try:
        return build_model(forge, clips, identifier, identifier, hitbox, meta=meta, size=size, density=density)
    finally:
        boss_forge.Painter = original


def displays(model):
    """ModelEngine renders one item display per distinct (bone, element rotation)."""
    elements = {e['uuid']: e for e in model['elements']}
    pairs = set()

    def visit(group):
        for child in group['children']:
            if isinstance(child, dict):
                visit(child)
            else:
                pairs.add((group['name'], tuple(round(v, 2) for v in elements[child].get('rotation', [0, 0, 0]))))
    for group in model['outliner']:
        if group['name'] != 'hitbox':
            visit(group)
    return len(pairs)


# ---------------------------------------------------------------------------
# Construction helpers


def chunked(f, bone, center, size, r, rng, div=(2, 2, 2), gap=.45, jitter=.35, push=.3, mat='rock', core='magma',
            label='chunk', skip=None, hot=.4, **paint):
    """A volume built from separate hewn blocks over a molten core that glows through the gaps.

    Every block shares the volume's rotation, so ModelEngine renders the cluster as one display.
    Sizes are quantised to quarter units so equal blocks share painted texels.
    """
    center, size, r = np.asarray(center, float), np.asarray(size, float), np.asarray(r, float)

    def splits(n):
        # Uneven cuts, re-drawn for every course, so seams stagger like masonry instead of crossing.
        if n == 1:
            return [0.0, 1.0]
        cuts = sorted(rng.uniform(.3, .7) if n == 2 else (k + rng.uniform(.3, .7)) / n for k in range(n - 1))
        return [0.0] + cuts + [1.0]

    ys = splits(div[1])
    made = []
    for j in range(div[1]):
        xs, zs = splits(div[0]), splits(div[2])
        for i in range(div[0]):
            for k in range(div[2]):
                idx = (i, j, k)
                edge = [div[a] > 1 and idx[a] in (0, div[a] - 1) for a in range(3)]
                if not any(edge) and all(d > 2 for d in div):
                    continue
                if skip and skip(i, j, k):
                    continue
                lo = np.array([xs[i], ys[j], zs[k]]) * size - size / 2
                hi = np.array([xs[i + 1], ys[j + 1], zs[k + 1]]) * size - size / 2
                outward = np.array([(-1 if idx[a] == 0 else 1) if edge[a] else 0 for a in range(3)], float)
                block = hi - lo - gap + np.array([rng.uniform(-jitter, jitter * .4) for _ in range(3)])
                block = np.maximum(np.round(block * 4) / 4, .5)
                off = outward * rng.uniform(.05, push) + np.array([rng.uniform(-.12, .12) for _ in range(3)]) * (1 - np.abs(outward))
                fired = dict(paint)
                if 'heat' in fired:
                    # Only some blocks are split by fissures; the rest stay dark so the seams read.
                    fired['heat'] = fired['heat'] * 1.8 if rng.random() < hot else 0
                made.append(f.cube(bone, center + r @ ((lo + hi) / 2 + off), block, mat, r, label=label, **fired))
    if core:
        f.cube(bone, center, np.maximum(size - gap * 2.4, .4), core, r, label=label + '_core')
    return made


# ---------------------------------------------------------------------------
# Posing helpers


def pose_dict(rotations, positions=None):
    """{bone: rot} plus {bone: pos} -> boss_forge pose."""
    out = {}
    for bone, r in rotations.items():
        out[bone] = [float(v) for v in r]
    for bone, p in (positions or {}).items():
        r = out.get(bone, [0.0, 0.0, 0.0])
        out[bone] = {'rot': list(r), 'pos': [float(v) for v in p]}
    return out


def mix(a, b, u):
    return {k: np.asarray(a.get(k, np.zeros(3)), float) * (1 - u) + np.asarray(b.get(k, np.zeros(3)), float) * u for k in set(a) | set(b)}


# ---------------------------------------------------------------------------
# Previews under Minecraft entity lighting


L0 = unit([.2, 1, -.7])
L1 = unit([-.2, 1, .7])


def mc_light(n, facing=0.0):
    """Minecraft's two entity lights, for a mob facing `facing` degrees (0 = north = native front)."""
    a = math.radians(facing)
    r = np.array([[math.cos(a), 0, math.sin(a)], [0, 1, 0], [-math.sin(a), 0, math.cos(a)]])
    return min(1.0, .4 + .6 * (max(0.0, float(n @ (r.T @ L0))) + max(0.0, float(n @ (r.T @ L1)))))


def render(rig, clip='idle', time=0.0, yaw=25.0, elevation=8.0, size=640, span=None, center=None, facing=0.0,
           aura_time=None, background=((26, 22, 30), (58, 46, 52)), glow=.35, ss=2, shadow=True):
    """Orthographic true view (yaw 0 faces the native front), shaded like a Minecraft entity."""
    from boss_render import FACE_QUADS, NORMALS, _raster
    matrices = rig.matrices(clip, time, None, time if aura_time is None else aura_time)
    big = size * ss
    az, el = math.radians(yaw), math.radians(elevation)
    eye = np.array([-math.sin(az) * math.cos(el), math.sin(el), -math.cos(az) * math.cos(el)])
    right = unit(np.cross(-eye, [0, 1, 0]))
    up = np.cross(right, -eye)
    cuboids = list(rig.cuboids(matrices))
    if span is None or center is None:
        pts = np.concatenate([w for _, w, _ in cuboids])
        sx, sy = pts @ right, pts @ up
        span = span or max(sx.max() - sx.min(), sy.max() - sy.min()) * 1.1
        center = center if center is not None else ((sx.max() + sx.min()) / 2, (sy.max() + sy.min()) / 2)
    scale = big / span
    top, bottom = np.array(background[0], float), np.array(background[1], float)
    canvas = (top + (bottom - top) * np.linspace(0, 1, big)[:, None, None]) * np.ones((big, big, 3))
    if shadow:
        yy, xx = np.mgrid[0:big, 0:big]
        gx = big / 2 - center[0] * scale
        gy = big / 2 + center[1] * scale
        ell = ((xx - gx) / (big * .3)) ** 2 + ((yy - gy) / (big * .3 * max(.1, math.sin(el)))) ** 2
        canvas *= 1 - np.clip(1 - ell, 0, 1)[..., None] ** 1.4 * .45
    bloom = np.zeros((big, big, 3))
    depth = np.full((big, big), -1e9)
    tex = rig.texture
    th, tw = tex.shape[:2]
    rw, rh = rig.model['resolution']['width'], rig.model['resolution']['height']
    for e, _, bone in cuboids:
        lo, hi = np.array(e['from'], float), np.array(e['to'], float)
        r_el = rot(e.get('rotation', [0, 0, 0]))
        m = matrices[bone]
        pivot = np.array(e.get('origin', [0, 0, 0]), float)
        emissive = not e.get('shade', True)
        for face, data in e['faces'].items():
            if data.get('texture') is None:
                continue
            n = m[:3, :3] @ (r_el @ np.array(NORMALS[face], float))
            if n @ eye <= 1e-6:
                continue
            quad = np.array([[hi[i] if c else lo[i] for i, c in enumerate(v)] for v in FACE_QUADS[face]])
            quad = ((quad - pivot) @ r_el.T + pivot) @ m[:3, :3].T + m[:3, 3]
            px = (quad @ right - center[0]) * scale + big / 2
            py = big / 2 - (quad @ up - center[1]) * scale
            pz = quad @ eye
            u0, v0, u1, v1 = data['uv']
            us = np.array([u0, u0, u1, u1]) * tw / rw
            vs = np.array([v0, v1, v1, v0]) * th / rh
            bright = mc_light(n, facing)
            for tri in ((0, 1, 2), (0, 2, 3)):
                _raster(canvas, bloom, depth, px[list(tri)], py[list(tri)], pz[list(tri)], us[list(tri)], vs[list(tri)],
                        tex, bright, emissive)
    image = Image.fromarray(np.clip(canvas, 0, 255).astype(np.uint8))
    if glow:
        g = Image.fromarray(np.clip(bloom, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(big / 110))
        image = Image.fromarray(np.clip(np.asarray(image, float) + np.asarray(g, float) * glow, 0, 255).astype(np.uint8))
    return image.resize((size, size), Image.Resampling.LANCZOS), dict(span=span, center=center)


def contact_sheet(tiles, cols, size, labels=None):
    from PIL import ImageFont
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new('RGB', (cols * size, rows * size), (20, 18, 24))
    try:
        font = ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf', max(14, size // 26))
    except OSError:
        font = None
    for i, tile in enumerate(tiles):
        x, y = (i % cols) * size, (i // cols) * size
        sheet.paste(tile, (x, y))
        if labels:
            ImageDraw.Draw(sheet).text((x + 10, y + 8), labels[i], font=font, fill=(236, 226, 214))
    return sheet


def rng(seed):
    return random.Random(seed)


__all__ = ['CLIPS', 'RealmPainter', 'build_realm', 'contact_sheet', 'displays', 'hex_rgb', 'mc_light', 'mix', 'pose_dict',
           'render', 'rng']
