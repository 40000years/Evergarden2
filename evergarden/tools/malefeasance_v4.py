"""Malefeasance v4: both forms re-sculpted on the shared river rig.

Phase 1, "the Veiled Hexer": a slender sorceress under a deep hood, pale face and burning violet-crimson
eyes above a lace veil, an A-line robe whose hem dissolves into violet wisps, a crimson underskirt in
the front slit, trailing sleeves, a tattered mantle and cape, a broken crimson sigil halo, a chained
grimoire and a watching eye.

Phase 2, "Astral Ascension": the hood is gone; long silver-violet hair with star pins, a crescent tiara,
a midnight gown sewn with gold constellations, translucent astral sleeves, six light ribbons spread
like wings, a full golden halo, a starlit grimoire and a star familiar.

Both forms keep every bone and all 17 authored clips of the original rig (retargeted by bone name from
arkhaia_malefeasance.bbmodel.bak). Shoulders, elbows and wrists sit closer to the body than before, so
the arms no longer float beside the torso; the clips are rotations and offsets about those pivots.
Gown panels sit on body bones, so the melee hitbox (BossHitbox) still covers the visible skirt.

    python evergarden/tools/malefeasance_v4.py [--out DIR]
"""
import argparse
import copy
import io
import base64
import json
import math
from pathlib import Path
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'evergarden/tools'))

import boss_forge
from boss_forge import Forge, Material, build_model, rot, _tone_to_rgb, _noise

OUT = ROOT / 'evergarden/src/main/resources/modelengine/rivers'
BACKUP = OUT / 'arkhaia_malefeasance.bbmodel.bak'
SCALE = 3.0
REVISION = 'malefeasance-v4'
SIDES = (('r', -1), ('l', 1))

# --------------------------------------------------------------------------- palettes
VOID = ('#0b0814', '#171026', '#261a3e', '#3a2a5e')
VOID_LINING = ('#050308', '#0a0710', '#120c1c', '#1c1428')
CRIMSON = ('#2c0610', '#5e0e1c', '#a81c32', '#ff5a6e')
HEX_GLOW = ('#2a0848', '#6a1aa8', '#b45cff', '#f0c8ff')
ANTIQUE_GOLD = ('#2e2210', '#5e4620', '#a07c36', '#e2c27a')
PALE_SKIN = ('#cdb8c8', '#e2d2dc', '#f2e6ec', '#fff8fb')
SHADOW_HAIR = ('#0e0a16', '#1c1430', '#2e2048', '#463268')
LACE_BLACK = ('#06040a', '#120c1a', '#22182e', '#3a2a4a')
LEATHER = ('#140c18', '#24142a', '#3a2040', '#583060')

MIDNIGHT = ('#070a1c', '#0f1636', '#1a2556', '#2c3c84')
MIDNIGHT_LINING = ('#120a2a', '#24124e', '#3c1e78', '#5e30aa')
STAR_GOLD = ('#4a3410', '#8e6a22', '#dcae40', '#fff0a8')
ASTRAL = ('#0a3040', '#1478a0', '#40d0f0', '#d8fcff')
PORCELAIN = ('#e6cfc4', '#f4e2d8', '#fcf0e8', '#ffffff')
SILVER_HAIR = ('#3a3060', '#6a5aa0', '#a898dc', '#ece6ff')
SHEER = ('#0c1a3a', '#1c3a78', '#3a78d0', '#a8e4ff')


class StarPainter(boss_forge.Painter):
    """Adds midnight cloth sewn with tiny gold stars and faint constellation lines."""

    def style_astral(self, ctx):
        rgb, alpha = self.style_cloth(ctx)
        w, h = ctx['w'], ctx['h']
        if w < 6 or h < 6:
            return rgb, alpha
        rng = np.random.default_rng(ctx['part']['seed'])
        count = max(2, int(w * h / 90))
        xs, ys = rng.integers(1, w - 1, count), rng.integers(1, h - 1, count)
        gold = np.array(boss_forge.hex_rgb(STAR_GOLD[3]), float)
        dim = np.array(boss_forge.hex_rgb(STAR_GOLD[2]), float)
        image = Image.fromarray(np.clip(rgb, 0, 255).astype('uint8'))
        draw = ImageDraw.Draw(image)
        points = list(zip(xs.tolist(), ys.tolist()))
        for a, b in zip(points[::3], points[1::3]):
            if math.dist(a, b) < max(w, h) * .45:
                draw.line([a, b], fill=tuple(int(c * .55) for c in dim), width=1)
        rgb = np.asarray(image, float)
        for i, (x, y) in enumerate(points):
            rgb[y, x] = gold if i % 4 else (255, 255, 255)
            if i % 5 == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if 0 <= x + dx < w and 0 <= y + dy < h:
                        rgb[y + dy, x + dx] = dim
        return rgb, alpha


boss_forge.Painter = StarPainter


def face_painter(kind):
    """North-face painter for the head: anime face, phase-specific eyes and shading."""
    def paint(ctx, rgb, mat):
        w, h = ctx['w'], ctx['h']
        k = 4
        W, H = w * k, h * k
        base = Image.fromarray(np.clip(rgb, 0, 255).astype('uint8')).convert('RGBA').resize((W, H), Image.Resampling.BICUBIC)
        layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        if kind == 'hex':
            # The hood shades the brow; a cold violet cast under the eyes.
            shade = Image.new('L', (W, H), 0)
            ImageDraw.Draw(shade).rectangle([0, 0, W, int(H * .34)], fill=150)
            shade = shade.filter(ImageFilter.GaussianBlur(H * .08))
            layer.paste((40, 20, 60, 255), (0, 0), shade)
            iris_top, iris_low, glow = (120, 20, 60), (255, 70, 120), (255, 120, 220, 110)
            lash, brow = (18, 8, 24, 255), (40, 20, 50, 230)
        else:
            blush = Image.new('RGBA', (W, H), (0, 0, 0, 0))
            bd = ImageDraw.Draw(blush)
            for cx in (.28, .72):
                bd.ellipse([W * (cx - .12), H * .58, W * (cx + .12), H * .68], fill=(255, 150, 165, 90))
            layer = Image.alpha_composite(layer, blush.filter(ImageFilter.GaussianBlur(W * .03)))
            d = ImageDraw.Draw(layer)
            iris_top, iris_low, glow = (60, 30, 130), (90, 220, 255), None
            lash, brow = (40, 26, 70, 255), (90, 80, 140, 220)

        def eye(cx, flip):
            cy, ew, eh = H * .5, W * .25, H * .24
            box = [cx - ew / 2, cy - eh / 2, cx + ew / 2, cy + eh / 2]
            if glow:
                halo = Image.new('RGBA', (W, H), (0, 0, 0, 0))
                ImageDraw.Draw(halo).ellipse([box[0] - ew * .2, box[1] - eh * .3, box[2] + ew * .2, box[3] + eh * .3], fill=glow)
                layer.alpha_composite(halo.filter(ImageFilter.GaussianBlur(W * .03)))
            d.ellipse(box, fill=(250, 246, 255, 255) if kind != 'hex' else (255, 220, 236, 255))
            iw, ih = ew * .72, eh * .98
            for step in range(int(ih)):
                t = step / max(1, ih - 1)
                col = tuple(int(iris_top[i] + (iris_low[i] - iris_top[i]) * t) for i in range(3)) + (255,)
                half = iw / 2 * math.sqrt(max(0, 1 - (2 * t - 1) ** 2))
                y = cy - ih / 2 + step
                d.line([(cx - half, y), (cx + half, y)], fill=col)
            pw = iw * .34
            d.ellipse([cx - pw / 2, cy - ih * .28, cx + pw / 2, cy + ih * .12], fill=(14, 6, 24, 255))
            if kind == 'hex':
                d.line([(cx, cy - ih * .3), (cx, cy + ih * .2)], fill=(255, 90, 140, 255), width=max(1, k))
            # Upper lash line with a sharp outer wing; thin lower line.
            top = cy - eh / 2
            sx = 1 if not flip else -1
            pts = [(cx - sx * ew * .55, top + eh * .38), (cx - sx * ew * .25, top - eh * .02), (cx, top - eh * .08),
                   (cx + sx * ew * .3, top), (cx + sx * ew * .62, top + eh * .16)]
            d.line(pts, fill=lash, width=max(2, int(eh * .17)), joint='curve')
            d.polygon([(cx + sx * ew * .45, top + eh * .02), (cx + sx * ew * .75, top - eh * .12), (cx + sx * ew * .55, top + eh * .2)], fill=lash)
            d.arc([cx - ew * .34, cy + eh * .18, cx + ew * .34, cy + eh * .5], 20, 160, fill=(90, 50, 90, 170), width=max(1, k))
            hx = cx - sx * iw * .2
            d.ellipse([hx - ew * .1, cy - ih * .32, hx + ew * .1, cy - ih * .08], fill=(255, 255, 255, 255))
            d.ellipse([cx + sx * iw * .2 - ew * .05, cy + ih * .12, cx + sx * iw * .2 + ew * .05, cy + ih * .24], fill=(230, 245, 255, 210))
        eye(W * .3, False)
        eye(W * .7, True)
        d.arc([W * .16, H * .27, W * .44, H * .41], 205, 340, fill=brow, width=max(2, int(H * .03)))
        d.arc([W * .56, H * .27, W * .84, H * .41], 200, 335, fill=brow, width=max(2, int(H * .03)))
        d.line([(W * .505, H * .63), (W * .49, H * .68)], fill=(200, 150, 160, 200), width=max(1, k))
        if kind != 'hex':
            d.arc([W * .43, H * .73, W * .57, H * .8], 15, 165, fill=(205, 110, 128, 255), width=max(2, int(H * .02)))
        out = Image.alpha_composite(base, layer).resize((w, h), Image.Resampling.LANCZOS)
        rgb[...] = np.asarray(out.convert('RGB'), float)
    return paint


def materials(phase):
    if phase == 1:
        return {
            'face': Material(PALE_SKIN, 'skin', density=3.0),
            'skin': Material(PALE_SKIN, 'skin', density=1.2),
            'hair': Material(SHADOW_HAIR, 'hair', density=1.3),
            'robe': Material(VOID, 'cloth', trim=CRIMSON, density=1.1),
            'robe_ghost': Material(VOID, 'ghost', trim=CRIMSON, accent=HEX_GLOW, density=1.0),
            'under': Material(CRIMSON, 'cloth', trim=ANTIQUE_GOLD, density=1.0),
            'hood': Material(VOID, 'cloth', trim=CRIMSON, density=1.0),
            'veil': Material(LACE_BLACK, 'cloth', trim=CRIMSON, density=2.4),
            'gold': Material(ANTIQUE_GOLD, 'trim', density=1.2),
            'crimson': Material(CRIMSON, 'glow', glow=True, density=1.2),
            'glow': Material(HEX_GLOW, 'glow', glow=True, density=1.2),
            'gem': Material(CRIMSON, 'gem', glow=True, density=1.5),
            'leather': Material(LEATHER, 'leather', density=1.1),
            'pages': Material(CRIMSON, 'runes', glow=True, density=1.2),
            'glove': Material(LACE_BLACK, 'plate', density=1.2),
        }
    return {
        'face': Material(PORCELAIN, 'skin', density=3.0),
        'skin': Material(PORCELAIN, 'skin', density=1.2),
        'hair': Material(SILVER_HAIR, 'hair', density=1.3),
        'robe': Material(MIDNIGHT, 'astral', trim=STAR_GOLD, density=1.2),
        'robe_ghost': Material(MIDNIGHT, 'astral', trim=STAR_GOLD, density=1.1),
        'under': Material(MIDNIGHT_LINING, 'cloth', trim=STAR_GOLD, density=1.0),
        'sheer': Material(SHEER, 'ghost', trim=STAR_GOLD, accent=ASTRAL, glow=True, density=1.0),
        'gold': Material(STAR_GOLD, 'trim', density=1.2),
        'cyan': Material(ASTRAL, 'glow', glow=True, density=1.2),
        'glow': Material(ASTRAL, 'glow', glow=True, density=1.2),
        'gem': Material(ASTRAL, 'gem', glow=True, density=1.5),
        'leather': Material(MIDNIGHT_LINING, 'leather', density=1.1),
        'pages': Material(ASTRAL, 'runes', glow=True, density=1.2),
        'glove': Material(PORCELAIN, 'skin', density=1.2),
    }


# --------------------------------------------------------------------------- rig
def joints():
    j = {
        'root': (0, 0, 0), 'pelvis': (0, 15, 0), 'chest': (0, 24, 0), 'neck': (0, 29, 0),
        'hi_gaze': (0, 34, 0), 'head': (0, 34, 0), 'head_forward': (0, 34, -7), 'jaw': (0, 33.5, -2.5),
        'collar': (0, 28, 1), 'cape': (0, 28, 3), 'cape_tip': (0, 15, 4),
        'focus': (-6.8, 18, -4.6), 'familiar': (10, 27, 1.5), 'halo': (0, 35, 3), 'book': (-7.2, 20.4, -4.4),
    }
    for i, x in enumerate([-3.625, -2.175, -0.725, 0.725, 2.175, 3.625]):
        j[f'veil_{i}'] = (x, 29, 3.3)
    for side, s in SIDES:
        j.update({f'shoulder_{side}': (s * 5.0, 27.6, 0), f'elbow_{side}': (s * 5.9, 22.2, -.4),
                  f'wrist_{side}': (s * 6.5, 18.2, -2.8), f'hip_{side}': (s * 1.6, 15, 0),
                  f'knee_{side}': (s * 1.7, 8, 0), f'ankle_{side}': (s * 1.7, 1.5, 0), f'sash_{side}': (s * 3.8, 15, 0)})
    return {k: np.array(v, float) for k, v in j.items()}


J = joints()


def skeleton(f):
    f.bone('root', None, [0, 0, 0])
    f.bone('pelvis', 'root', J['pelvis'])
    f.bone('chest', 'pelvis', J['chest'])
    f.bone('neck', 'chest', J['neck'])
    f.bone('hi_gaze', 'neck', J['hi_gaze'])
    f.bone('head', 'hi_gaze', J['head'])
    f.bone('head_forward', 'head', J['head_forward'])
    f.bone('jaw', 'head', J['jaw'])
    f.bone('collar', 'chest', J['collar'])
    for i in range(6):
        f.bone(f'veil_{i}', 'collar', J[f'veil_{i}'])
    f.bone('cape', 'chest', J['cape'])
    f.bone('cape_tip', 'cape', J['cape_tip'])
    f.bone('focus', 'chest', J['focus'])
    f.bone('familiar', 'chest', J['familiar'])
    f.bone('halo', 'chest', J['halo'])
    for side, s in SIDES:
        f.bone(f'arm_{side}', 'chest', J[f'shoulder_{side}'])
        f.bone(f'forearm_{side}', f'arm_{side}', J[f'elbow_{side}'])
        f.bone(f'hand_{side}', f'forearm_{side}', J[f'wrist_{side}'])
        f.bone(f'weapon_{side}', f'hand_{side}', J[f'wrist_{side}'])
        f.bone(f'shoulder_{side}', 'chest', J[f'shoulder_{side}'])
        f.bone(f'leg_{side}', 'pelvis', J[f'hip_{side}'])
        f.bone(f'shin_{side}', f'leg_{side}', J[f'knee_{side}'])
        f.bone(f'foot_{side}', f'shin_{side}', J[f'ankle_{side}'])
        f.bone(f'sash_{side}', 'pelvis', J[f'sash_{side}'])
    f.bone('book', 'hand_r', J['book'])


# --------------------------------------------------------------------------- shared helpers
def ellipse(a, rx, rz, y, shift=0.0):
    return np.array([rx * math.sin(a), y, -rz * math.cos(a) + shift])


def gown(f, mat, under, y_top=15.2, y_bot=.25, top=(3.1, 2.25), bot=(8.2, 6.8), slit=True, hem_edge=('tatter', .05, 3), **paint):
    """An eight-panel A-line skirt; the front panel opens into a slit over an underskirt."""
    def bone(a):
        x = math.sin(a)
        return 'sash_l' if x > .55 else 'sash_r' if x < -.55 else 'pelvis'
    for k in range(8):
        a0, a1 = (k - .5) * math.pi / 4, (k + .5) * math.pi / 4
        mid = (a0 + a1) / 2
        facing = (math.sin(mid), 0, -math.cos(mid))
        pts = lambda u0, u1, gap0=0.0, gap1=0.0: [ellipse(u0 + gap0, *top, y_top), ellipse(u1 - gap0, *top, y_top),
                                                   ellipse(u1 - gap1, *bot, y_bot, .6), ellipse(u0 + gap1, *bot, y_bot, .6)]
        if k == 0 and slit:
            # Two halves leave a slit that widens toward the hem.
            for label, (u0, u1) in (('gown_front_r', (a0, 0)), ('gown_front_l', (0, a1))):
                outline = pts(u0, u1)
                if u1 == 0:
                    outline[1], outline[2] = ellipse(-.06, *top, y_top), ellipse(-.28, *bot, y_bot, .6)
                else:
                    outline[0], outline[3] = ellipse(.06, *top, y_top), ellipse(.28, *bot, y_bot, .6)
                f.sheet('pelvis', outline, mat, thickness=.12, up=(0, 1, 0), facing=facing, label=label,
                        edge={2: hem_edge}, **paint)
            inner = [ellipse(-.4, top[0] * .95, top[1] * .95, y_top - .3), ellipse(.4, top[0] * .95, top[1] * .95, y_top - .3),
                     ellipse(.4, bot[0] * .9, bot[1] * .9, y_bot + .05, .5), ellipse(-.4, bot[0] * .9, bot[1] * .9, y_bot + .05, .5)]
            f.sheet('pelvis', inner, under, thickness=.1, up=(0, 1, 0), facing=facing, label='underskirt', folds=4, hem=.08)
            continue
        f.sheet(bone(mid), pts(a0, a1), mat, thickness=.12, up=(0, 1, 0), facing=facing, label=f'gown_{k}',
                edge={2: hem_edge}, **paint)


def bodice(f, mat, neck_mat):
    tiers = [(14.6, 16.4, 4.6, 3.0), (16.4, 18.4, 4.1, 2.8), (18.4, 20.4, 4.4, 3.0), (20.4, 22.6, 5.1, 3.3),
             (22.6, 24.6, 5.5, 3.3), (24.6, 26.6, 5.8, 3.1)]
    for y0, y1, w, d in tiers:
        f.box('chest', (-w / 2, y0, -d / 2), (w, y1 - y0, d), mat, label='bodice')
    f.box('chest', (-3.0, 26.5, -1.4), (6.0, 1.9, 2.8), neck_mat, label='bodice_shoulders')
    f.box('neck', (-.85, 28.0, -.85), (1.7, 4.0, 1.7), 'skin', label='neck_column')


def arms(f, sleeve, cuff, hand, drape, drape_paint):
    for side, s in SIDES:
        sh, el, wr = J[f'shoulder_{side}'], J[f'elbow_{side}'], J[f'wrist_{side}']
        f.cube(f'shoulder_{side}', sh + (-s * .4, .3, 0), (2.6, 1.6, 2.6), sleeve, rot((0, 0, -s * 14)), label=f'shoulder_cap_{side}')
        f.beam(f'arm_{side}', sh + (0, .2, 0), el, 1.35, 1.35, sleeve, label=f'upper_arm_{side}')
        f.beam(f'forearm_{side}', el + (0, .2, 0), wr, 1.15, 1.15, sleeve, label=f'forearm_{side}')
        f.beam(f'forearm_{side}', wr + (0, .6, 0), wr + (0, -.1, 0), 1.45, 1.45, cuff, label=f'cuff_{side}')
        # Slender hand: palm, thumb and four tapered fingers held loosely.
        f.box(f'hand_{side}', (wr[0] - .45, wr[1] - 1.5, wr[2] - .4), (.9, 1.5, .8), hand, label=f'palm_{side}')
        f.beam(f'hand_{side}', wr + (-s * .1, -.5, -.55), wr + (-s * .2, -1.3, -.95), .3, .3, hand, label=f'thumb_{side}')
        for i in range(4):
            x = wr[0] + (i - 1.5) * .22
            f.beam(f'hand_{side}', (x, wr[1] - 1.45, wr[2]), (x, wr[1] - 2.4 + abs(i - 1.5) * .15, wr[2] - .25), .2, .22, hand, label=f'finger_{side}')
        # Long trailing bell sleeve hanging from the forearm.
        outline = [el + (s * .3, .1, .55), wr + (s * .35, .4, .3), wr + (s * .45, -6.5, 1.2), el + (s * .45, -3.0, 1.6)]
        f.sheet(f'forearm_{side}', outline, drape, thickness=.08, up=(0, 1, 0), facing=(s, 0, 0), label=f'bell_sleeve_{side}',
                edge={2: ('tatter', .08, 3)}, **drape_paint)


def legs(f, boot, trim):
    for side, s in SIDES:
        f.beam(f'leg_{side}', J[f'hip_{side}'], J[f'knee_{side}'], 1.6, 1.6, boot, label=f'thigh_{side}')
        f.beam(f'shin_{side}', J[f'knee_{side}'], J[f'ankle_{side}'], 1.3, 1.3, boot, label=f'shin_{side}')
        an = J[f'ankle_{side}']
        f.box(f'foot_{side}', (an[0] - .6, 0, an[2] - 2.2), (1.2, 1.4, 2.8), boot, label=f'boot_{side}')
        f.spike(f'foot_{side}', (an[0], .45, an[2] - 2.2), (an[0], .25, an[2] - 3.4), .55, trim, steps=2, label=f'boot_toe_{side}')


def halo_ring(f, center, radius, mat, width, segments, skip=()):
    for i in range(segments):
        if i in skip:
            continue
        a0, a1 = i * math.tau / segments, (i + 1) * math.tau / segments
        p0 = center + np.array([math.cos(a0) * radius, math.sin(a0) * radius, 0])
        p1 = center + np.array([math.cos(a1) * radius, math.sin(a1) * radius, 0])
        f.beam('halo', p0, p1, width, width, mat, label='halo_segment')


def star(f, bone, at, size, rim, core, label):
    f.cube(bone, at, (size, size, size * .45), rim, rot((0, 0, 45)), label=label + '_rim')
    f.cube(bone, np.asarray(at, float) + (0, 0, -size * .2), (size * .62, size * .62, size * .4), core, rot((0, 0, 45)), label=label + '_core')


# --------------------------------------------------------------------------- phase 1
def sculpt_hexer():
    f = Forge('arkhaia_malefeasance', materials(1), seed=404)
    skeleton(f)
    # Head and the face under the hood.
    f.box('head', (-2.4, 31.6, -2.3), (4.8, 5.0, 4.5), 'face', label='sorceress_head_base', face=face_painter('hex'))
    f.box('jaw', (-1.7, 31.7, -2.0), (3.4, 1.0, 3.4), 'skin', label='jaw_contour')
    f.box('head', (-2.6, 35.2, -2.5), (5.2, 2.0, 5.0), 'hair', label='hair_cap')
    f.sheet('head', [(-2.5, 36.9, -2.55), (2.5, 36.9, -2.55), (2.5, 35.0, -2.55), (-2.5, 35.0, -2.55)], 'hair', thickness=.1,
            up=(0, 1, 0), facing=(0, 0, -1), label='bangs', edge={2: ('scallop', .22, 4)})
    # Dark silk veil over the lower face, with crimson beads.
    f.sheet('head', [(-2.3, 33.05, -2.42), (2.3, 33.05, -2.42), (2.15, 31.0, -2.5), (-2.15, 31.0, -2.5)], 'veil', thickness=.05,
            up=(0, 1, 0), facing=(0, 0, -1), label='face_veil', edge={2: ('scallop', .2, 3)}, folds=3, hem=.18)
    for x in (-1.6, -.55, .55, 1.6):
        f.cube('head', (x, 31.05, -2.55), (.22, .3, .22), 'crimson', rot((0, 0, 45)), label='veil_bead')
    # Deep hood.
    for side, s in SIDES:
        f.sheet('head', [(s * 1.6, 39.2, -3.0), (s * 1.9, 39.4, 2.5), (s * 3.25, 29.6, 3.3), (s * 3.45, 29.2, -1.6)],
                'hood', thickness=.12, up=(0, 1, 0), facing=(s, 0, 0), label=f'hood_side_{side}', folds=3, lining=('north', VOID_LINING))
        f.beam('head', (s * 1.6, 39.2, -3.05), (s * 3.45, 29.2, -1.65), .35, .3, 'gold', label=f'hood_rim_{side}')
        # Dark hair falls out of the hood over the chest.
        f.sheet('head', [(s * 1.75, 33.8, -2.6), (s * 2.55, 33.8, -2.5), (s * 2.5, 22.5, -2.2), (s * 1.6, 23.5, -2.4)],
                'hair', thickness=.1, up=(0, 1, 0), facing=(0, 0, -1), label=f'front_lock_{side}', edge={2: ('scallop', .3, 2)})
    f.sheet('head', [(-1.6, 39.2, -3.05), (1.6, 39.2, -3.05), (1.9, 39.4, 2.55), (-1.9, 39.4, 2.55)], 'hood', thickness=.12,
            up=(0, 0, -1), facing=(0, 1, 0), label='hood_top', lining=('north', VOID_LINING))
    f.sheet('head', [(-1.9, 39.4, 2.6), (1.9, 39.4, 2.6), (3.3, 29.5, 3.35), (-3.3, 29.5, 3.35)], 'hood', thickness=.12,
            up=(0, 1, 0), facing=(0, 0, 1), label='hood_back', folds=4)
    f.beam('head', (-1.6, 39.2, -3.1), (1.6, 39.2, -3.1), .4, .3, 'gold', label='hood_rim_top')
    f.spike('head', (0, 39.0, 2.0), (0, 35.0, 5.8), 1.5, 'hood', steps=3, label='hood_peak')
    f.spike('head', (0, 39.2, -2.6), (0, 40.6, -3.4), .9, 'hood', steps=2, label='hood_point')
    star(f, 'head', (0, 38.7, -3.25), .7, 'gold', 'gem', 'hood_clasp')
    # Body.
    bodice(f, 'robe', 'robe')
    f.box('chest', (-2.3, 26.6, -1.7), (4.6, 1.6, .35), 'under', label='collar_inset')
    for y in (16.0, 17.6, 19.2, 20.8):
        f.beam('chest', (-1.0, y - .35, -1.65), (1.0, y + .35, -1.65), .18, .14, 'crimson', label='corset_lace')
        f.beam('chest', (-1.0, y + .35, -1.65), (1.0, y - .35, -1.65), .18, .14, 'crimson', label='corset_lace')
    star(f, 'chest', (0, 23.6, -1.85), 1.0, 'gold', 'gem', 'amulet')
    for side, s in SIDES:
        # High vampiric collar behind the neck.
        f.sheet('collar', [(s * .4, 28.0, 1.0), (s * 3.0, 28.0, .6), (s * 3.5, 31.0, 1.5), (s * .6, 30.4, 1.6)], 'robe', thickness=.1,
                up=(0, 1, 0), facing=(0, 0, 1), label=f'high_collar_{side}', lining=('north', CRIMSON), edge={2: ('scallop', .12, 2)})
    f.box('pelvis', (-2.5, 14.0, -1.7), (5.0, 1.6, 3.4), 'robe', label='hips')
    f.box('pelvis', (-2.6, 14.6, -1.8), (5.2, .7, 3.6), 'gold', label='belt')
    star(f, 'pelvis', (0, 14.95, -1.95), .9, 'gold', 'gem', 'belt_buckle')
    for side, s in SIDES:
        f.beam('pelvis', (s * .5, 14.6, -1.9), (s * 2.0, 12.4, -2.4), .16, .16, 'gold', label=f'belt_chain_{side}')
        f.cube('pelvis', (s * 2.0, 12.1, -2.45), (.5, .7, .14), 'crimson', rot((0, 0, 0)), label=f'talisman_{side}')
    gown(f, 'robe_ghost', 'under', fade=.22, folds=5, hem=.05, lining=('north', VOID_LINING))
    legs(f, 'glove', 'gold')
    arms(f, 'robe', 'gold', 'glove', 'robe_ghost', dict(fade=.3, folds=3))
    # Tattered mantle over the shoulders.
    mantle = [((-3.0, 28.4, -1.5), (3.0, 28.4, -1.5), (3.6, 24.6, -2.0), (-3.6, 24.6, -2.0), (0, 0, -1)),
              ((-3.2, 28.4, 1.4), (3.2, 28.4, 1.4), (4.6, 23.6, 2.4), (-4.6, 23.6, 2.4), (0, 0, 1))]
    for i, (a, b, c, d, facing) in enumerate(mantle):
        f.sheet('collar', [a, b, c, d], 'robe_ghost', thickness=.1, up=(0, 1, 0), facing=facing, label=f'mantle_{i}',
                edge={2: ('tatter', .12, 4)}, fade=.25, folds=3, lining=('north', VOID_LINING))
    for side, s in SIDES:
        f.sheet(f'shoulder_{side}', [(s * 3.0, 28.5, -1.6), (s * 3.0, 28.5, 1.6), (s * 6.4, 24.6, 2.0), (s * 6.4, 24.6, -2.0)],
                'robe_ghost', thickness=.1, up=(-s, 1, 0), facing=(s, 1, 0), label=f'mantle_shoulder_{side}',
                edge={2: ('tatter', .12, 3)}, fade=.25, folds=2, lining=('north', VOID_LINING))
    # Long tattered cape.
    f.sheet('cape', [(-4.0, 28.2, 2.2), (4.0, 28.2, 2.2), (5.6, 16.0, 3.6), (-5.6, 16.0, 3.6)], 'robe', thickness=.1,
            up=(0, 1, 0), facing=(0, 0, 1), label='cape_upper', folds=4, lining=('north', VOID_LINING))
    f.sheet('cape_tip', [(-5.6, 16.0, 3.6), (5.6, 16.0, 3.6), (7.6, .4, 5.2), (-7.6, .4, 5.2)], 'robe_ghost', thickness=.1,
            up=(0, 1, 0), facing=(0, 0, 1), label='cape_lower', edge={2: ('tatter', .06, 6)}, fade=.3, folds=6, lining=('north', VOID_LINING))
    for i in range(6):
        x = J[f'veil_{i}'][0]
        f.sheet(f'veil_{i}', [(x - .45, 28.8, 3.3), (x + .45, 28.8, 3.3), (x * 1.5 + .4, 4.0, 4.6), (x * 1.5 - .4, 4.0, 4.6)], 'robe_ghost',
                thickness=.06, up=(0, 1, 0), facing=(0, 0, 1), label=f'veil_ribbon_{i}', edge={2: ('tatter', .2, 1)}, fade=.4, folds=1)
    # Broken crimson sigil halo.
    c = np.array([0, 35.6, 3.6])
    halo_ring(f, c, 4.6, 'crimson', .26, 18, skip=(4, 5, 13))
    halo_ring(f, c, 3.7, 'glow', .16, 14, skip=(2, 9))
    for i in range(6):
        a = i * math.tau / 6 + .26
        f.cube('halo', c + (math.cos(a) * 5.4, math.sin(a) * 5.4, 0), (.55, .9, .14), 'crimson', rot((0, 0, math.degrees(a) - 90)), label='halo_rune')
    # Chained grimoire.
    b = J['book']
    f.cube('book', b, (.5, 3.6, .6), 'gold', rot((15, 0, 0)), label='grimoire_spine')
    for name, s in (('left', -1), ('right', 1)):
        r = rot((15, 0, s * 32))
        center = b + r @ np.array([s * 1.5, 0, .1])
        f.cube('book', center, (2.7, 3.6, .3), 'leather', r, label=f'grimoire_cover_{name}')
        f.cube('book', center + r @ np.array([-s * .1, 0, -.25]), (2.4, 3.3, .26), 'pages', r, label=f'grimoire_pages_{name}')
        f.cube('book', center + r @ np.array([s * 1.2, 1.5, -.05]), (.5, .5, .38), 'gold', r, label=f'cover_corner_{name}')
    for i in range(3):
        f.beam('book', b + (-.6 + i * .6, -1.8, 0), b + (-.9 + i * .9, -3.6, .2), .14, .14, 'gold', label='book_chain')
    f.cube('book', b + (0, 2.3, -.8), (.5, .5, .5), 'crimson', rot((30, 20, 45)), label='floating_glyph')
    # Crimson crystals at the casting hand.
    fo = J['focus']
    for i, (dx, dy, tilt) in enumerate(((0, 0, 0), (.5, -.3, 25), (-.5, -.2, -25))):
        f.spike('focus', fo + (dx, dy - .4, 0), fo + (dx * 2, dy + 1.6, -.2), .45, 'crimson', steps=2, label=f'focus_crystal_{i}')
    # The watching eye.
    fa = J['familiar']
    f.cube('familiar', fa, (1.8, 1.8, 1.8), 'glow', rot((0, 0, 45)), label='eye_orb')
    f.cube('familiar', fa + (0, 0, -.95), (.9, .9, .2), 'crimson', rot((0, 0, 45)), label='eye_iris')
    f.cube('familiar', fa + (0, 0, -1.08), (.3, .7, .12), 'gem', rot((0, 0, 0)), label='eye_pupil')
    for i in range(4):
        a = i * math.tau / 4
        f.spike('familiar', fa + (math.cos(a) * 1.1, math.sin(a) * 1.1, 0), fa + (math.cos(a) * 2.2, math.sin(a) * 2.2, .3), .3, 'crimson', steps=1, label='eye_shard')
    return f


# --------------------------------------------------------------------------- phase 2
def sculpt_ascension():
    f = Forge('arkhaia_malefeasance_phase2', materials(2), seed=808)
    skeleton(f)
    f.box('head', (-2.4, 31.6, -2.3), (4.8, 5.0, 4.5), 'face', label='sorceress_head_base', face=face_painter('astral'))
    f.box('jaw', (-1.7, 31.7, -2.0), (3.4, 1.0, 3.4), 'skin', label='jaw_contour')
    f.box('head', (-2.65, 34.6, -2.45), (5.3, 2.6, 5.1), 'hair', label='hair_cap')
    f.box('head', (-2.3, 37.2, -2.1), (4.6, .6, 4.4), 'hair', label='hair_crown')
    f.sheet('head', [(-2.55, 37.2, -2.6), (2.55, 37.2, -2.6), (2.55, 34.6, -2.6), (-2.55, 34.6, -2.6)], 'hair', thickness=.1,
            up=(0, 1, 0), facing=(0, 0, -1), label='bangs', edge={2: ('scallop', .22, 5)})
    for side, s in SIDES:
        f.sheet('head', [(s * 2.45, 36.8, -2.5), (s * 2.75, 36.8, 1.8), (s * 2.95, 30.6, 1.6), (s * 2.65, 31.2, -2.2)], 'hair',
                thickness=.1, up=(0, 1, 0), facing=(s, 0, 0), label=f'hair_side_{side}', edge={2: ('scallop', .2, 3)})
        f.sheet('head', [(s * 1.9, 34.6, -2.65), (s * 2.6, 34.6, -2.5), (s * 2.6, 21.8, -2.1), (s * 1.7, 22.8, -2.4)], 'hair',
                thickness=.1, up=(0, 1, 0), facing=(0, 0, -1), label=f'front_lock_{side}', edge={2: ('scallop', .35, 2)})
        f.cube('head', (s * 2.6, 34.0, -2.55), (.5, .5, .5), 'gold', rot((0, 0, 45)), label=f'lock_ring_{side}')
        f.cube('head', (s * 2.3, 36.6, -2.4), (.45, .45, .2), 'cyan', rot((0, 0, 45)), label=f'star_pin_{side}')
    # Long back hair to the waist, in three falling layers.
    for i, (x0, x1, z, low) in enumerate(((-3.0, 3.0, 2.75, 15.0), (-3.2, -.6, 3.0, 17.0), (.6, 3.2, 3.0, 16.5))):
        f.sheet('head', [(x0, 37.2, z), (x1, 37.2, z), (x1 * 1.15, low, z + 1.0), (x0 * 1.15, low, z + 1.0)], 'hair',
                thickness=.1, up=(0, 1, 0), facing=(0, 0, 1), label=f'back_hair_{i}', edge={2: ('scallop', .12, 3)})
    # Crescent tiara with a star.
    for i in range(5):
        a0, a1 = math.radians(200 + i * 28), math.radians(228 + i * 28)
        p0, p1 = np.array([math.cos(a0) * 1.3, 36.9 + math.sin(a0) * 1.3 + 1.3, -2.75]), np.array([math.cos(a1) * 1.3, 36.9 + math.sin(a1) * 1.3 + 1.3, -2.75])
        f.beam('head', p0, p1, .3, .2, 'gold', label='tiara_crescent')
    star(f, 'head', (0, 37.4, -2.85), .8, 'gold', 'gem', 'tiara_star')
    # Body.
    bodice(f, 'robe', 'skin')
    f.box('chest', (-2.9, 24.4, -1.75), (5.8, .45, 3.5), 'gold', label='neckline_trim')
    for side, s in SIDES:
        f.beam('chest', (s * 1.6, 27.9, -1.4), (0, 24.9, -1.75), .16, .16, 'gold', label=f'necklace_{side}')
    star(f, 'chest', (0, 24.7, -1.9), .9, 'gold', 'gem', 'pendant')
    for y in (16.4, 18.6, 20.8):
        star(f, 'chest', (0, y, -1.6), .45, 'gold', 'cyan', 'bodice_star')
    f.box('pelvis', (-2.5, 14.0, -1.7), (5.0, 1.6, 3.4), 'robe', label='hips')
    f.box('pelvis', (-2.6, 14.6, -1.8), (5.2, .6, 3.6), 'gold', label='belt')
    star(f, 'pelvis', (0, 14.9, -1.95), .9, 'gold', 'gem', 'belt_star')
    gown(f, 'robe_ghost', 'under', hem_edge=('scallop', .03, 4), folds=5, hem=.07, lining=('north', MIDNIGHT_LINING))
    legs(f, 'robe', 'gold')
    arms(f, 'robe', 'gold', 'glove', 'sheer', dict(fade=.35, folds=2))
    for side, s in SIDES:
        # Crescent pauldron.
        f.cube(f'shoulder_{side}', J[f'shoulder_{side}'] + (-s * .3, 1.0, 0), (2.8, .5, 2.8), 'gold', rot((0, 0, -s * 18)), label=f'pauldron_{side}')
        star(f, f'shoulder_{side}', J[f'shoulder_{side}'] + (s * .3, 1.4, -1.45), .55, 'gold', 'cyan', f'pauldron_star_{side}')
        f.cube(f'arm_{side}', J[f'shoulder_{side}'] + (s * .45, -2.4, 0), (1.6, .4, 1.6), 'gold', rot((0, 0, -s * 8)), label=f'armlet_{side}')
    # Six ribbons of light spread behind her like wings.
    for i in range(6):
        x = J[f'veil_{i}'][0]
        spread = (i - 2.5)
        tip = np.array([spread * 3.6, 10.0 + abs(spread) * 3.2, 6.5 + abs(spread) * .6])
        f.sheet(f'veil_{i}', [(x - .55, 28.8, 3.3), (x + .55, 28.8, 3.3), tip + (1.0, 0, 0), tip - (1.0, 0, 0)], 'sheer',
                thickness=.06, up=(0, 1, 0), facing=(0, 0, 1), label=f'light_ribbon_{i}', edge={2: ('scallop', .25, 1)}, fade=.45, folds=1)
    # Full golden halo with star points.
    c = np.array([0, 36.0, 3.6])
    halo_ring(f, c, 5.0, 'gold', .24, 24)
    halo_ring(f, c, 4.2, 'cyan', .14, 20)
    for i in range(8):
        a = i * math.tau / 8
        star(f, 'halo', c + (math.cos(a) * 5.0, math.sin(a) * 5.0, -.1), .7 if i % 2 == 0 else .45, 'gold', 'cyan', 'halo_star')
    # Starlit grimoire with drifting pages.
    b = J['book']
    f.cube('book', b, (.5, 3.6, .6), 'gold', rot((15, 0, 0)), label='grimoire_spine')
    for name, s in (('left', -1), ('right', 1)):
        r = rot((15, 0, s * 32))
        center = b + r @ np.array([s * 1.5, 0, .1])
        f.cube('book', center, (2.7, 3.6, .3), 'leather', r, label=f'grimoire_cover_{name}')
        f.cube('book', center + r @ np.array([-s * .1, 0, -.25]), (2.4, 3.3, .26), 'pages', r, label=f'grimoire_pages_{name}')
    for i, (dx, dy, tilt) in enumerate(((-1.4, 2.6, -20), (.2, 3.3, 10), (1.6, 2.4, 25))):
        f.cube('book', b + (dx, dy, -1.2), (.9, 1.2, .06), 'pages', rot((0, 0, tilt)), label='drifting_page')
    fo = J['focus']
    f.spike('focus', fo + (0, -.6, 0), fo + (0, 1.6, -.2), .7, 'cyan', steps=2, label='focus_prism')
    star(f, 'focus', fo + (0, 2.0, -.2), .6, 'gold', 'cyan', 'focus_star')
    # Star familiar.
    fa = J['familiar']
    f.cube('familiar', fa, (1.6, 1.6, 1.6), 'cyan', rot((0, 0, 45)), label='familiar_core')
    for i in range(4):
        a = i * math.tau / 4 + math.pi / 4
        f.spike('familiar', fa + (math.cos(a) * .7, math.sin(a) * .7, 0), fa + (math.cos(a) * 2.0, math.sin(a) * 2.0, 0), .4, 'gold', steps=1, label='familiar_point')
    for side, s in SIDES:
        f.sheet('familiar', [fa + (s * .6, .4, .5), fa + (s * 2.6, 2.0, 1.0), fa + (s * 2.9, .2, 1.1), fa + (s * .7, -.3, .6)], 'sheer',
                thickness=.05, up=(0, 1, 0), facing=(0, 0, 1), label=f'familiar_wing_{side}', fade=0, folds=1)
    return f


# --------------------------------------------------------------------------- build
def retarget(model):
    backup = json.loads(BACKUP.read_text(encoding='utf8'))
    names = {}

    def scan(nodes):
        for node in nodes:
            if isinstance(node, dict):
                names[node['name']] = node['uuid']
                scan(node.get('children', []))
    scan(model['outliner'])
    clips = []
    for anim in backup.get('animations', []):
        clip = copy.deepcopy(anim)
        clip['animators'] = {names[a['name']]: copy.deepcopy(a) for a in anim.get('animators', {}).values() if a.get('name') in names}
        clips.append(clip)
    return clips


def build(phase):
    forge = sculpt_hexer() if phase == 1 else sculpt_ascension()
    identifier = 'arkhaia_malefeasance' if phase == 1 else 'arkhaia_malefeasance_phase2'
    meta = dict(creation_tool='evergarden-forge', author='Evergarden Procedural Forge', model_identifier=identifier,
                revision=f'{REVISION}-phase{phase}', scale=SCALE)
    model, atlas, stats = build_model(forge, [], identifier, identifier, hitbox=([-5, 0, -3], [5, 36, 3]), meta=meta,
                                      size=2048, density=9.0)
    model['animations'] = retarget(model)
    return model, stats


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, default=OUT)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    for phase in (1, 2):
        model, stats = build(phase)
        target = args.out / (model['model_identifier'] + '.bbmodel')
        target.write_text(json.dumps(model, indent=2), encoding='utf8')
        print(f"{target.name}: {len(model['elements'])} cubes, {len(model['animations'])} clips, "
              f"{stats['coverage'] * 100:.1f}% atlas at {stats['density']:.2f} px/unit")


if __name__ == '__main__':
    main()
