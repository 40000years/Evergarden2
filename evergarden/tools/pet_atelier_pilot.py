"""Pet Atelier pilot: six companions rebuilt as clean blocks with painted coats.

Each species owns its silhouette, face and markings; the kit only supplies
anatomy primitives (pet_atelier). Stage values are chosen per form with s.v().
"""
from __future__ import annotations

import math

import numpy as np

from pet_atelier import (rotation, Pet, Mark, Bands, Eye, Inset, Toward, Dots, quad_legs, chain, ellipsoid, slab, boxed,
                         both, either, minus, mirrored, polyline)

SPECIES = ('peach_shiba', 'buttercream_corgi', 'pumpkin_fox', 'lavender_cat', 'cocoa_capybara', 'moonstone_sphinx')


# ----------------------------------------------------------------------- shared bits

def _torso(s, length, width, height, cy, cz, mat='fur', chest=1.06, rump=.96, bevel=None):
    """Chest and rump blocks: a body with a readable front and back, not a single brick."""
    b = bevel if bevel is not None else min(width, height) * .2
    s.rbox('body', (0, cy + height * .02, cz - length * .22), (width * chest, height * chest, length * .58), mat, b,
           name='chest')
    s.rbox('body', (0, cy - height * .02, cz + length * .22), (width * rump, height * rump, length * .6), mat, b,
           name='rump')
    return cz - length / 2, cz + length / 2, cy + height / 2, cy - height / 2


def _head(s, centre, size, pivot, mat='fur', bevel=None):
    s.bone('head', 'body', pivot)
    w, h, d = size
    s.rbox('head', centre, size, mat, bevel if bevel is not None else min(size) * .17, name='skull')
    return centre[2] - d / 2  # front plane


def _ear(s, side, root, w, h, d, mat, inner, tilt=12, lean=-8, fold=0.0, inner_scale=.62, inner_lift=.0,
         inner_mat_kind='inner'):
    """A pointed ear: a block base with a diamond roof (the lower half of the diamond hides in the base).

    w is the width at the base, h the height to the tip; fold tips the ear forward (puppy ears)."""
    name = 'ear_' + ('l' if side < 0 else 'r')
    s.bone(name, 'head', root)
    root = np.array(root, float)
    R = rotation((lean - fold, 0, -side * tilt))
    rot = (lean - fold, 0, -side * tilt)
    base_h = max(.0, h - w / 2)
    a = w / math.sqrt(2)

    def place(local):
        return root + R @ np.array(local, float)
    for scale, z_off, mat_, grp in ((1.0, 0.0, mat, 'ear'), (inner_scale, -d / 2 - .06, inner, 'ear')):
        ww, aa = w * scale, a * scale
        bh = base_h * scale
        lift = inner_lift if scale < 1 else 0
        dd = d if scale == 1 else .3
        if bh > .2:
            c = place((0, bh / 2 + lift, z_off + (0 if scale == 1 else 0)))
            s.cube(name, c, (ww, bh + .02, dd), mat_, rot, c, group=grp, name='ear_base' if scale == 1 else 'ear_inner')
        c = place((0, bh + lift, z_off))
        s.cube(name, c, (aa, aa, dd), mat_, (rot[0], 0, rot[2] + 45), c, group=grp,
               name='ear_tip' if scale == 1 else 'ear_inner')


def _nose(s, centre, size, mat='nose'):
    s.rbox('head', centre, size, mat, min(size) * .3, group='nose', name='nose')
    x, y, z = centre
    # A small wet highlight on the upper left of the nose.
    s.mark(Mark(ellipsoid((x + size[0] * .18, y + size[1] * .22, z - size[2] / 2), (size[0] * .16, size[1] * .16, .6)),
                '#F4E8EA', groups=('nose',), facing=(0, .4, -1), min_dot=.3))


def _smile(s, x, y, z, width, depth, r, colour='mouth', drop=None):
    """An ω mouth hanging from the nose."""
    drop = drop if drop is not None else depth
    pts_r = [(0, y, z), (0, y - drop, z), (width * .45, y - drop - depth * .35, z), (width, y - drop * .55, z)]
    pts_l = [(-p[0], p[1], p[2]) for p in pts_r]
    s.mark(Mark(either(polyline(pts_r, r), polyline(pts_l, r)), colour, groups=('snout', 'head'), facing=(0, 0, -1),
                min_dot=.5))


def _blush(s, x, y, z, w, h, colour='blush'):
    s.mark(Mark(mirrored(ellipsoid((x, y, z), (w, h, 1.4))), colour, groups=('head', 'snout'), facing=(0, 0, -1),
                min_dot=.3))


# ---------------------------------------------------------------------------- shiba

def _peach_shiba(stage):
    s = Pet('peach_shiba', stage, dict(fur='#EE9E6A', fur_top='#E28D58', cream='#FFF2DF', inner='#F7C9B8',
                                       nose='#3A2A2E', mouth='#5A3436', blush='#F6A0A0', cloth='#F48C8C',
                                       cloth_dot='#FFF4E6', cloth_hem='#D9606A', peach='#FFB08A', peach_blush='#F27C78', leaf='#79B35B',
                                       claw='#E9D8C4'),
            kinds=dict(fur_top='fur', peach='smooth', peach_blush='smooth', cloth_dot='cloth', cloth_hem='cloth'))
    v = s.v
    s.scale = v(.72, .75, .78)
    leg = v(3.0, 4.0, 5.0)
    L, W, H = v(8.0, 10.2, 12.4), v(5.8, 6.4, 7.0), v(5.0, 5.7, 6.4)
    cy = leg + H * .42
    cz = .6
    zf, zb, top, bottom = _torso(s, L, W, H, cy, cz)
    s.bones['body'] = ('root', [0, cy, cz])
    quad_legs(s, W * .3, zf + v(1.7, 2.1, 2.5), zb - v(1.8, 2.2, 2.6), cy - H * .1, cy - H * .1, v(2.1, 2.2, 2.3),
              paw='cream', thigh=(v(2.6, 3.0, 3.3), v(3.0, 3.5, 4.0), v(3.0, 3.5, 4.0)))
    # Head: big and round-cheeked for the pup, foxier with age.
    hw, hh, hd = v(7.6, 7.6, 7.8), v(6.6, 6.5, 6.6), v(6.2, 6.2, 6.4)
    hy = top + hh * v(.30, .38, .42)
    hz = zf - hd * v(.05, .12, .18)
    front = _head(s, (0, hy, hz), (hw, hh, hd), (0, hy - hh * .4, hz + hd * .2))
    sw, sh, sd = v(3.6, 3.8, 4.0), v(2.4, 2.6, 2.8), v(1.6, 2.3, 3.0)
    sy = hy - hh * .27
    s.rbox('head', (0, sy, front - sd / 2 + .4), (sw, sh, sd + .8), 'cream', .5, group='snout', name='snout')
    snout_front = front - sd + .0
    _nose(s, (0, sy + sh * .28, snout_front - .15), (v(1.5, 1.6, 1.8), v(1.0, 1.1, 1.2), .9))
    _smile(s, 0, sy + sh * .05, snout_front, v(.85, .95, 1.05), v(.45, .5, .55), .17 if s.age == 2 else .14,
           drop=v(.35, .45, .5))
    # Fluffy cream jowls widen the face.
    for side in (-1, 1):
        s.rbox('head', (side * (hw / 2 - .2), hy - hh * .26, hz - hd * .12), (v(1.6, 1.7, 1.8), v(2.2, 2.3, 2.4), v(3.0, 3.3, 3.6)),
               'fur', .45, name='jowl')
    # Fluff: cheek tufts flare sideways, a chest ruff spills under the chin.
    s.rbox('body', (0, top - v(.6, .9, 1.1), zf - .5), (W * .62, v(2.4, 2.8, 3.2), 1.8), 'cream', .4, name='ruff')
    s.rbox('body', (0, top - v(2.0, 2.5, 3.0), zf - .95), (W * .4, v(1.6, 1.9, 2.2), 1.4), 'cream', .35, name='ruff')
    # Ears: folded puppy ears that stand with age.
    ew, eh = v(3.0, 3.2, 3.4), v(2.6, 3.4, 4.2)
    for side in (-1, 1):
        _ear(s, side, (side * hw * .29, hy + hh / 2 - .5, hz + .6), ew, eh, v(1.2, 1.3, 1.4), 'fur', 'inner',
             tilt=v(18, 12, 10), lean=v(-4, -6, -8), fold=v(58, 0, 0))
    ey = hy + hh * v(.0, .03, .05)
    ex = hw * v(.23, .23, .22)
    # Urajiro: cream muzzle, cheeks, brow dots, throat, belly and tail lining.
    s.mark(Mark(mirrored(ellipsoid((hw * .3, hy - hh * .26, front + .5), (hw * .26, hh * .24, 3.6))), 'cream',
                groups=('head',), jitter=.25))
    s.mark(Mark(ellipsoid((0, hy - hh * .52, front + 1.2), (hw * .36, hh * .2, 3.0)), 'cream', groups=('head',), jitter=.2))
    s.mark(Mark(either(ellipsoid((0, cy + H * .05, zf), (W * .33, H * .62, 2.6)), slab(1, -1e9, cy - H * .28)),
                'cream', groups=('body',), jitter=.35))
    s.mark(Mark(slab(1, cy + H * .32), 'fur_top', groups=('body',), jitter=.4))
    s.mark(Mark(slab(1, hy + hh * .36), 'fur_top', groups=('head',), jitter=.3, facing=(0, 1, 0), min_dot=-.2))
    s.mark(Mark(slab(1, -1e9, v(1.7, 2.2, 2.7)), 'cream', groups=('leg', 'paw'), jitter=.45))
    s.mark(Mark(mirrored(ellipsoid((ex * 1.02, ey + v(1.55, 1.6, 1.6), front), (.55, .4, 1.2))), 'cream',
                groups=('head',), facing=(0, 0, -1)))
    _blush(s, hw * .34, ey - v(1.7, 1.75, 1.8), front, v(.85, .9, .95), .48)
    s.mark(Eye((ex, ey, front), v(1.9, 1.75, 1.6), v(2.3, 2.1, 1.9), iris='#3B2620', iris_light='#8A5A3C',
               style='round', tilt=v(0, .08, .14)))
    # The famous curled tail sits on the rump; a cream lining shows inside the curl.
    tb = (0, top - .4, zb - v(.6, .8, 1.0))
    r = v(1.5, 2.1, 2.6)
    t = v(2.2, 2.6, 3.0)
    # Side view: the curl rises behind the base, rolls forward over the top and rests on the back.
    centre = (0, tb[1] + r + .3, tb[2] - .35 * r)
    pts = [tb]
    for i, a in enumerate(v((80, 165, 250), (80, 150, 220, 290), (75, 140, 205, 270, 320))):
        ang = math.radians(a)
        pts.append((v(.0, .15, .2) * (i + 1), centre[1] - r * math.cos(ang), centre[2] + r * math.sin(ang)))
    chain(s, 'tail', pts, [t * (1 - i * .07) for i in range(len(pts) - 1)], 'fur')
    s.mark(Toward(centre, 'cream', groups=('tail',)))
    if s.age >= 1:
        # Peach bandana: a folded triangle under the chin, printed with cream dots.
        by = hy - hh / 2 - .3
        bz = zf - 1.3
        for i, (bw, bh) in enumerate(((v(0, 5.0, 5.6), 1.2), (v(0, 3.6, 4.0), 1.1), (v(0, 2.2, 2.4), 1.0))):
            s.cube('body', (0, by - i * 1.05, bz - .1 * i), (bw, bh, .9), 'cloth', group='acc', name='bandana')
        s.mark(Mark(slab(1, -1e9, by - 2.75), 'cloth_hem', groups=('acc',), mats=('cloth',)))
        s.rbox('body', (0, by + .6, zf + 1.2), (W * 1.0, 1.4, 3.4), 'cloth', .4, group='acc', name='collar')
        s.mark(Dots(1.3, .32, 'cloth_dot', groups=('acc',), mats=('cloth',), facing=(0, 0, -1), min_dot=.5))
        # Leaf tucked into the knot.
        s.beam('body', (W * .35, by + 1.0, zf + .6), (W * .35 + 1.6, by + 2.2, zf + .1), .9, 'leaf', .35, group='acc',
               name='leaf')
    if s.age == 2:
        # A little peach pouch hanging from the bandana.
        py = by - 3.6
        pz = bz - .9
        s.rbox('body', (0, py, pz), (2.6, 2.4, 2.2), 'peach', .55, group='acc', name='peach')
        s.mark(Mark(ellipsoid((.6, py - .2, pz - 1.1), (1.0, 1.1, 1.5)), 'peach_blush', groups=('acc',), mats=('peach',)))
        s.cube('body', (0, py + 1.45, pz), (.35, .8, .35), 'leaf', group='acc', name='stem')
        s.beam('body', (0, py + 1.6, pz), (1.3, py + 2.2, pz - .15), .8, 'leaf', .3, group='acc', name='leaf')
    return s.finish()


def heart(cx, cy, r):
    """A heart in the x/y plane, point down."""
    def f(P):
        X = (P[..., 0] - cx) / r
        Y = (P[..., 1] - cy) / r * 1.1 + .15
        return (X * X + Y * Y - 1) ** 3 - X * X * Y ** 3
    return f


def _bow(s, bone, centre, size, mat, knot, tails=True, group='acc'):
    """A ribbon bow: two angled loops, a knot and two short tails."""
    x, y, z = centre
    w, h = size
    for side in (-1, 1):
        s.rbox(bone, (x + side * w * .3, y + h * .08, z), (w * .5, h, .9), mat, .25, (0, 0, side * 14), group=group,
               name='bow_loop')
        if tails:
            s.cube(bone, (x + side * w * .16, y - h * .7, z + .1), (w * .22, h * .9, .5), mat, (0, 0, side * 18),
                   group=group, name='bow_tail')
    s.rbox(bone, (x, y + h * .05, z - .25), (w * .26, h * .62, 1.1), knot, .2, group=group, name='bow_knot')


def _pumpkin(s, bone, centre, r, group='acc'):
    x, y, z = centre
    s.rbox(bone, (x, y, z), (r * 2.2, r * 1.7, r * 2.0), 'pumpkin', r * .45, group=group, name='pumpkin')
    s.mark(Bands((1, 0, .35), x - r * 2, r * .62, .26, 'pumpkin_rib', groups=(group,), mats=('pumpkin',)))
    s.cube(bone, (x, y + r * .95, z), (r * .38, r * .6, r * .38), 'stem', (0, 0, 12), group=group, name='pumpkin_stem')


def _leaf(s, bone, root, tip, width, mat='leaf', group='acc'):
    s.beam(bone, root, tip, width, mat, .3, group=group, name='leaf')
    s.beam(bone, root, tip, width * .14, 'leaf_vein', .36, group=group, name='leaf_vein')


def _all(P):
    return np.full(P.shape[:-1], -1.0)


# ---------------------------------------------------------------------------- corgi

def _buttercream_corgi(stage):
    s = Pet('buttercream_corgi', stage, dict(fur='#F0B85A', fur_top='#E8AA4C', cream='#FFF7EA', inner='#F9C2B2',
                                             nose='#33262A', mouth='#5A3436', blush='#F7A1A1', ribbon='#FFC7D4',
                                             ribbon_knot='#FFF0C9', tongue='#F27D8A', mouth_in='#7A3440'),
            kinds=dict(fur_top='fur', ribbon='cloth', ribbon_knot='cloth'))
    v = s.v
    s.scale = v(.72, .74, .76)
    leg = v(2.3, 2.7, 3.1)
    L, W, H = v(10.5, 14.0, 17.0), v(6.6, 7.2, 7.8), v(5.6, 6.2, 6.8)
    cy = leg + H * .42
    cz = 1.0
    b = min(W, H) * .24
    # The corgi loaf: a long body with a big round rump.
    s.rbox('body', (0, cy, cz - L * .2), (W, H, L * .62), 'fur', b, name='chest')
    s.rbox('body', (0, cy + .1, cz + L * .24), (W * 1.04, H * 1.04, L * .56), 'fur', b * 1.1, name='rump')
    zf, zb, top = cz - L / 2, cz + L / 2, cy + H / 2
    s.bones['body'] = ('root', [0, cy, cz])
    quad_legs(s, W * .3, zf + v(1.8, 2.2, 2.6), zb - v(2.0, 2.4, 2.8), cy - H * .15, cy - H * .15, v(2.3, 2.5, 2.7),
              paw='cream', paw_len=v(2.7, 2.9, 3.2))
    hw, hh, hd = v(7.8, 7.9, 8.0), v(6.6, 6.5, 6.5), v(6.2, 6.2, 6.4)
    hy = top + hh * v(.26, .36, .42)
    hz = zf - hd * v(.0, .08, .14)
    front = _head(s, (0, hy, hz), (hw, hh, hd), (0, hy - hh * .4, hz + hd * .2))
    sw, sh, sd = v(3.8, 4.0, 4.2), v(2.5, 2.6, 2.8), v(1.5, 2.1, 2.7)
    sy = hy - hh * .28
    s.rbox('head', (0, sy, front - sd / 2 + .4), (sw, sh, sd + .8), 'cream', .55, group='snout', name='snout')
    snout_front = front - sd
    _nose(s, (0, sy + sh * .3, snout_front - .15), (v(1.6, 1.7, 1.8), v(1.05, 1.1, 1.2), .9))
    ey = hy + hh * .04
    ex = hw * .235
    # White blaze from the muzzle up between the eyes, white cheeks.
    s.mark(Mark(either(ellipsoid((0, hy + hh * .05, front), (hw * .11, hh * .5, 3.0)),
                       mirrored(ellipsoid((hw * .28, hy - hh * .3, front + .5), (hw * .26, hh * .22, 3.4)))),
                'cream', groups=('head',), jitter=.25))
    s.mark(Mark(either(ellipsoid((0, cy + H * .1, zf), (W * .38, H * .7, 3.2)), slab(1, -1e9, cy - H * .22)),
                'cream', groups=('body',), jitter=.4))
    s.mark(Mark(slab(1, cy + H * .36), 'fur_top', groups=('body',), jitter=.4))
    s.mark(Mark(slab(1, -1e9, v(1.6, 1.9, 2.2)), 'cream', groups=('leg', 'paw'), jitter=.4))
    if s.age >= 1:
        # The famous fluffy heart-shaped bottom.
        s.mark(Mark(heart(0, cy + H * .1, v(0, 1.9, 2.5)), 'cream', groups=('body',), facing=(0, 0, 1), min_dot=.5))
    _blush(s, hw * .34, ey - 1.75, front, .9, .48)
    s.mark(Eye((ex, ey, front), v(2.0, 1.85, 1.7), v(2.4, 2.2, 2.0), iris='#3A2621', iris_light='#9A6440', tilt=.05))
    if s.age == 0:
        _smile(s, 0, sy + sh * .06, snout_front, .9, .45, .14, drop=.38)
    else:
        # An open, happy corgi grin with a pink tongue.
        my = sy - sh * .1
        s.mark(Mark(ellipsoid((0, my, snout_front), (v(0, 1.0, 1.1), v(0, .62, .7), .9)), 'mouth_in', groups=('snout',),
                    facing=(0, 0, -1), min_dot=.5))
        s.mark(Mark(ellipsoid((0, my - .32, snout_front), (v(0, .62, .7), v(0, .38, .44), .9)), 'tongue',
                    groups=('snout',), facing=(0, 0, -1), min_dot=.5))
        _smile(s, 0, sy + sh * .06, snout_front, v(0, 1.1, 1.2), .2, .15, drop=.2)
    # Huge upright ears.
    ew, eh = v(3.6, 4.0, 4.4), v(4.0, 5.3, 6.4)
    for side in (-1, 1):
        _ear(s, side, (side * hw * .3, hy + hh / 2 - .6, hz + .8), ew, eh, v(1.2, 1.3, 1.4), 'fur', 'inner',
             tilt=v(20, 17, 15), lean=-4)
    # A stubby tail that still wags.
    chain(s, 'tail', [(0, top - .6, zb - .4), (0, top + .3, zb + 1.0)], v(2.0, 2.2, 2.4), 'fur')
    s.mark(Mark(slab(2, zb + .4), 'cream', groups=('tail',)))
    if s.age >= 1:
        # Buttercream bow on the collar; the adult's has three tiers like piped frosting.
        by = hy - hh / 2 + .3
        _bow(s, 'body', (W * .28, by, zf + .4), (v(0, 3.2, 3.8), v(0, 1.9, 2.2)), 'ribbon', 'ribbon_knot')
        s.rbox('body', (0, by - .1, zf + 1.4), (W * 1.02, 1.3, 3.0), 'ribbon', .35, group='acc', name='collar')
        if s.age == 2:
            for i, (ww, yy) in enumerate(((2.6, -1.6), (2.0, -2.7))):
                s.rbox('body', (W * .28, by + yy, zf + .45 + i * .1), (ww, 1.0, 1.0),
                       'ribbon' if i == 0 else 'ribbon_knot', .25, group='acc', name='frosting')
    return s.finish()


# ------------------------------------------------------------------------------ fox

def _pumpkin_fox(stage):
    s = Pet('pumpkin_fox', stage, dict(fur='#EC7F34', fur_top='#D3621F', cream='#FFF2E0', dark='#3E2A2C',
                                       inner='#FFE1CC', nose='#2B1E22', mouth='#4A2A2E', blush='#F7A08A',
                                       pumpkin='#F59A3A', pumpkin_rib='#D9741F', stem='#6E5236', leaf='#5E9E4A',
                                       leaf_vein='#3F7A36', leaf_dark='#4E8A3E'),
            kinds=dict(fur_top='fur', pumpkin='smooth', pumpkin_rib='smooth', stem='plain', leaf_vein='leaf',
                       leaf_dark='leaf'))
    v = s.v
    s.scale = v(.7, .74, .76)
    leg = v(3.6, 5.0, 6.2)
    L, W, H = v(7.6, 10.2, 12.2), v(5.0, 5.4, 5.8), v(4.6, 5.0, 5.4)
    cy = leg + H * .45
    cz = .4
    zf, zb, top, bottom = _torso(s, L, W, H, cy, cz)
    s.bones['body'] = ('root', [0, cy, cz])
    quad_legs(s, W * .28, zf + v(1.5, 1.9, 2.2), zb - v(1.6, 2.0, 2.3), cy - H * .1, cy - H * .1, v(1.9, 1.9, 2.0),
              paw='dark', thigh=(v(2.4, 2.7, 3.0), v(2.8, 3.4, 3.8), v(2.8, 3.4, 3.9)))
    hw, hh, hd = v(7.2, 7.0, 7.0), v(6.0, 5.7, 5.7), v(5.6, 5.6, 5.8)
    hy = top + hh * v(.3, .42, .5)
    hz = zf - hd * v(.05, .15, .2)
    front = _head(s, (0, hy, hz), (hw, hh, hd), (0, hy - hh * .4, hz + hd * .2))
    # Cheek ruff flares the face into the classic fox triangle.
    for side in (-1, 1):
        s.rbox('head', (side * (hw / 2 + .05), hy - hh * .33, hz - hd * .08),
               (v(1.2, 1.3, 1.4), v(2.0, 2.0, 2.1), v(3.0, 3.2, 3.4)), 'cream', .4, (0, 0, side * 10), name='ruff')
    sw, sh, sd = v(2.8, 2.7, 2.7), v(2.1, 2.1, 2.2), v(1.8, 2.9, 3.8)
    sy = hy - hh * .26
    s.rbox('head', (0, sy, front - sd / 2 + .4), (sw, sh, sd + .8), 'fur', .5, group='snout', name='snout')
    snout_front = front - sd
    _nose(s, (0, sy + sh * .3, snout_front - .15), (v(1.3, 1.3, 1.4), v(.9, .9, 1.0), .8))
    _smile(s, 0, sy + sh * .1, snout_front, .7, .35, .13, drop=.4)
    ey = hy + hh * .06
    ex = hw * .235
    # Fox mask: white cheeks that sweep up towards the temples; white under the muzzle.
    s.mark(Mark(lambda P: P[..., 1] - (ey - v(1.3, 1.4, 1.5) + np.abs(P[..., 0]) * .42), 'cream',
                groups=('head', 'snout'), jitter=.2))
    s.mark(Mark(either(ellipsoid((0, cy + H * .1, zf), (W * .36, H * .66, 2.8)), slab(1, -1e9, cy - H * .25)), 'cream',
                groups=('body',), jitter=.4))
    s.mark(Mark(slab(1, cy + H * .34), 'fur_top', groups=('body',), jitter=.4))
    s.mark(Mark(slab(1, -1e9, v(2.0, 2.9, 3.6)), 'dark', groups=('leg', 'paw'), jitter=.5))
    s.mark(Eye((ex, ey, front), v(1.9, 1.7, 1.6), v(2.2, 1.9, 1.75), iris='#6B3410', iris_light='#F2A93B',
               style='round' if s.age == 0 else 'almond', tilt=v(.05, .14, .2), wing=v(0, .25, .4)))
    _blush(s, hw * .33, ey - 1.7, front, .8, .42)
    # Tall ears with dark backs and tips.
    ew, eh = v(3.2, 3.4, 3.6), v(3.6, 4.6, 5.4)
    for side in (-1, 1):
        _ear(s, side, (side * hw * .28, hy + hh / 2 - .5, hz + .7), ew, eh, v(1.1, 1.2, 1.3), 'fur', 'inner',
             tilt=v(15, 12, 10), lean=-5)
    s.mark(Mark(slab(1, hy + hh / 2 + eh * .62), 'dark', groups=('ear',), mats=('fur',)))
    s.mark(Mark(_all, 'dark', groups=('ear',), mats=('fur',), facing=(0, 0, 1), min_dot=.5))
    # The great brush tail with a cream tip.
    tz = zb - .3
    t = top
    pts = v([(0, t - .8, tz), (0, t - 2.0, tz + 2.2), (0, t - .8, tz + 4.4), (0, t + .6, tz + 5.6)],
            [(0, t - .8, tz), (0, t - 2.4, tz + 2.6), (0, t - 1.6, tz + 5.4), (0, t + .4, tz + 7.4), (0, t + 1.8, tz + 8.4)],
            [(0, t - .8, tz), (0, t - 2.8, tz + 2.8), (0, t - 2.2, tz + 6.0), (0, t + .2, tz + 8.6), (0, t + 2.4, tz + 9.8)])
    chain(s, 'tail', pts, v([2.6, 3.4, 2.8], [2.8, 3.8, 4.0, 3.0], [3.0, 4.4, 4.8, 3.6]), 'fur')
    s.mark(Mark(ellipsoid(pts[-1], (6, v(2.6, 3.0, 3.6), v(2.4, 2.8, 3.4))), 'cream', groups=('tail',), jitter=.4))
    # Pumpkin-leaf mantle.
    ny = hy - hh / 2 + .2
    if s.age == 0:
        _leaf(s, 'head', (hw * .1, hy + hh / 2, hz), (hw * .32, hy + hh / 2 + 1.6, hz - .8), 1.6)
    else:
        n = v(0, 2, 4)
        for i in range(n):
            a = math.radians((i - (n - 1) / 2) * (38 if n > 2 else 50))
            root = (math.sin(a) * W * .3, ny, zf + .8)
            tip = (math.sin(a) * W * .62, ny - 2.2, zf - .6 - math.cos(a) * 1.4)
            _leaf(s, 'body', root, tip, v(0, 2.3, 2.7), 'leaf' if i % 2 == 0 else 'leaf_dark')
        s.rbox('body', (0, ny + .2, zf + 1.4), (W * 1.0, 1.2, 3.0), 'leaf_dark', .35, group='acc', name='vine_collar')
        _pumpkin(s, 'body', (0, ny - v(0, 2.6, 3.0), zf - v(0, .9, 1.1)), v(0, .85, 1.05))
    return s.finish()


# ------------------------------------------------------------------------------ cat

def _lavender_cat(stage):
    s = Pet('lavender_cat', stage, dict(fur='#B7A0E2', fur_top='#9A82CF', stripe='#8B72C2', cream='#F1E9FC',
                                        inner='#F7B8CF', nose='#E68AA6', mouth='#5B3F63', blush='#F4A4C4',
                                        ribbon='#6E4BB0', flower='#8E63D6', flower_light='#B795F0', stem='#6E9E5A'),
            kinds=dict(fur_top='fur', stripe='fur', ribbon='cloth', flower='plain', flower_light='plain', stem='leaf'))
    v = s.v
    s.scale = v(.72, .76, .78)
    leg = v(3.0, 4.2, 5.2)
    L, W, H = v(7.2, 9.6, 11.6), v(4.8, 5.0, 5.2), v(4.4, 4.7, 5.0)
    cy = leg + H * .45
    cz = .6
    zf, zb, top, bottom = _torso(s, L, W, H, cy, cz, chest=1.04, rump=1.0)
    s.bones['body'] = ('root', [0, cy, cz])
    quad_legs(s, W * .28, zf + v(1.5, 1.8, 2.0), zb - v(1.5, 1.8, 2.0), cy - H * .1, cy - H * .1, v(1.8, 1.8, 1.85),
              paw='cream', thigh=(v(2.3, 2.6, 2.8), v(2.8, 3.2, 3.6), v(2.8, 3.2, 3.6)))
    hw, hh, hd = v(7.6, 7.2, 7.0), v(6.4, 6.0, 5.8), v(5.8, 5.6, 5.6)
    hy = top + hh * v(.32, .42, .48)
    hz = zf - hd * v(.08, .16, .2)
    front = _head(s, (0, hy, hz), (hw, hh, hd), (0, hy - hh * .4, hz + hd * .2))
    # Round cat cheeks, wider than the skull.
    for side in (-1, 1):
        s.rbox('head', (side * (hw / 2 + .1), hy - hh * .25, hz - hd * .05),
               (v(1.8, 1.9, 2.0), v(2.6, 2.5, 2.5), v(3.6, 3.6, 3.8)), 'fur', .55, name='cheek')
    sw, sh, sd = v(3.4, 3.2, 3.2), v(2.0, 2.0, 2.0), v(.7, .9, 1.1)
    sy = hy - hh * .28
    s.rbox('head', (0, sy, front - sd / 2 + .4), (sw, sh, sd + .8), 'cream', .45, group='snout', name='muzzle')
    snout_front = front - sd
    _nose(s, (0, sy + sh * .32, snout_front - .1), (v(1.1, 1.1, 1.1), v(.75, .75, .8), .6))
    _smile(s, 0, sy + sh * .12, snout_front, .75, .32, .12, drop=.3)
    for side in (-1, 1):
        for dx, dy in ((.95, .05), (1.25, -.35), (1.0, -.7)):
            s.mark(Mark(ellipsoid((side * dx, sy + dy, snout_front), (.16, .16, .8)), 'stripe', groups=('snout',),
                        facing=(0, 0, -1), min_dot=.5))
    ey = hy + hh * .05
    ex = hw * .235
    s.mark(Mark(either(ellipsoid((0, cy + H * .1, zf), (W * .36, H * .7, 2.8)), slab(1, -1e9, cy - H * .25)), 'cream',
                groups=('body',), jitter=.4))
    s.mark(Mark(mirrored(ellipsoid((hw * .3, hy - hh * .32, front + .6), (hw * .22, hh * .18, 3.0))), 'cream',
                groups=('head',), jitter=.2))
    # Tabby: an M on the brow, saddle stripes and tail rings.
    for x0 in (-1.0, 0.0, 1.0):
        s.mark(Mark(polyline([(x0 * hw * .14, hy + hh * .5, front), (x0 * hw * .12, hy + hh * .26, front)], .26), 'stripe',
                    groups=('head',), facing=(0, 0, -1), min_dot=.5))
    s.mark(Bands((0, 0, 1), zf + 1, v(1.8, 2.2, 2.4), .3, 'stripe', wobble=.08, groups=('body',), facing=(0, 1, 0),
                 min_dot=-.4))
    s.mark(Mark(slab(1, -1e9, v(1.4, 1.8, 2.1)), 'cream', groups=('leg', 'paw'), jitter=.3))
    s.mark(Eye((ex, ey, front), v(2.2, 2.0, 1.85), v(2.5, 2.2, 2.0), iris='#B5821C', iris_light='#F7D35E',
               slit=s.age > 0, style='round' if s.age == 0 else 'almond', tilt=v(.04, .12, .16)))
    _blush(s, hw * .33, ey - 1.75, front, .85, .42)
    ew, eh = v(3.0, 3.1, 3.2), v(3.0, 3.6, 4.1)
    for side in (-1, 1):
        _ear(s, side, (side * hw * .3, hy + hh / 2 - .5, hz + .4), ew, eh, v(1.0, 1.1, 1.1), 'fur', 'inner',
             tilt=v(14, 11, 9), lean=-6)
    # Question-mark tail.
    tz = zb - .3
    t = top - .6
    pts = v([(0, t, tz), (0, t + 1.8, tz + 1.2), (0, t + 3.6, tz + 1.0), (0, t + 4.3, tz - .2)],
            [(0, t, tz), (0, t + 2.2, tz + 1.4), (0, t + 4.8, tz + 1.7), (0, t + 6.6, tz + 1.0), (0, t + 7.0, tz - .6)],
            [(0, t, tz), (0, t + 2.4, tz + 1.6), (0, t + 5.4, tz + 2.0), (0, t + 7.8, tz + 1.4), (0, t + 8.6, tz - .3),
             (0, t + 7.9, tz - 1.6)])
    chain(s, 'tail', pts, v([1.8, 1.7, 1.6], [1.7, 1.6, 1.5, 1.45], [1.7, 1.6, 1.5, 1.45, 1.4]), 'fur')
    s.mark(Bands((0, 1, 0), t + 1.4, v(1.2, 1.4, 1.5), .4, 'stripe', groups=('tail',)))
    # Lavender collar with a flower sprig.
    if s.age >= 1:
        ny = hy - hh / 2 + .2
        s.rbox('body', (0, ny, zf + 1.3), (W * 1.04, 1.1, 2.8), 'ribbon', .3, group='acc', name='collar')
        n = v(0, 3, 6)
        for i in range(n):
            y = ny + .4 + i * .55
            for side in ((-1, 1) if i % 2 == 0 else (0,)):
                s.rbox('body', (W * .44 + side * .32, y, zf + .1), (.7, .7, .7),
                       'flower' if i % 2 == 0 else 'flower_light', .15, group='acc', name='lavender_bud')
        s.beam('body', (W * .44, ny - .3, zf + .2), (W * .44, ny + .5 + n * .55, zf + .1), .3, 'stem', group='acc',
               name='lavender_stem')
    return s.finish()


# ------------------------------------------------------------------------- capybara

def _cocoa_capybara(stage):
    s = Pet('cocoa_capybara', stage, dict(fur='#A8744C', fur_top='#8C5C3A', belly='#C4976E', snout='#946042',
                                          nose='#3E2A24', mouth='#3E2A24', blush='#E99A86', inner='#7A5034',
                                          pod='#D4772F', pod_rib='#A4501F', pod_light='#EA9C4A', stem='#6E5236',
                                          leaf='#6BA35A', leaf_vein='#4C7F40', cream='#F3E2C8'),
            kinds=dict(fur_top='fur', belly='fur', snout='fur', pod='smooth', pod_rib='smooth', pod_light='smooth',
                       stem='plain', leaf_vein='leaf'))
    v = s.v
    s.scale = v(.72, .74, .76)
    leg = v(2.1, 2.5, 2.9)
    L, W, H = v(10.0, 13.2, 16.0), v(6.8, 7.8, 8.6), v(6.2, 7.0, 7.8)
    cy = leg + H * .42
    cz = 1.2
    # One barrel of a body, slightly higher at the rump.
    s.rbox('body', (0, cy, cz), (W, H, L), 'fur', min(W, H) * .28, name='barrel')
    s.rbox('body', (0, cy + H * .1, cz + L * .18), (W * .9, H * .9, L * .55), 'fur', min(W, H) * .25, name='rump')
    zf, zb, top = cz - L / 2, cz + L / 2, cy + H / 2
    s.bones['body'] = ('root', [0, cy, cz])
    quad_legs(s, W * .32, zf + v(2.0, 2.6, 3.0), zb - v(2.2, 2.8, 3.2), cy - H * .2, cy - H * .2, v(2.4, 2.7, 3.0),
              paw='snout', paw_len=v(2.6, 2.9, 3.3))
    # A long, flat-topped box of a head with a blunt nose.
    hw, hh, hd = v(6.0, 6.2, 6.4), v(5.6, 5.8, 6.0), v(6.6, 8.0, 9.4)
    hy = top - v(.0, .4, .8)
    hz = zf - hd * .32
    front = _head(s, (0, hy, hz), (hw, hh, hd), (0, hy - hh * .3, hz + hd * .3))
    s.rbox('head', (0, hy - hh * .16, front + .9), (hw * .86, hh * .62, 2.4), 'snout', .6, group='snout', name='muzzle')
    snout_front = front - .3
    # Two nostrils high on the muzzle and a little split lip.
    s.mark(Mark(mirrored(ellipsoid((.75, hy + hh * .08, snout_front), (.32, .42, 1.0))), 'nose', groups=('snout',),
                facing=(0, 0, -1), min_dot=.5))
    my = hy - hh * .3
    s.mark(Mark(either(polyline([(0, hy - hh * .08, snout_front), (0, my, snout_front), (-.5, my - .2, snout_front),
                                 (-1.0, my + .05, snout_front)], .14),
                       polyline([(0, my, snout_front), (.5, my - .2, snout_front), (1.0, my + .05, snout_front)], .14)),
                'mouth', groups=('snout',), facing=(0, 0, -1), min_dot=.5))
    # Calm, heavy-lidded eyes set high on the sides of the head.
    s.mark(Eye((hw / 2 - .05, hy + hh * .16, front + v(1.3, 1.7, 2.1)), v(1.8, 1.7, 1.6), v(1.9, 1.8, 1.7),
               iris='#2A1A16', iris_light='#6A4630', style='sleepy', lid=v(.3, .2, .12), lid_colour='fur',
               facing=(1, .05, -.55), depth=1.4))
    s.mark(Mark(slab(1, cy + H * .3), 'fur_top', groups=('body',), jitter=.5))
    s.mark(Mark(slab(1, -1e9, cy - H * .25), 'belly', groups=('body',), jitter=.5))
    s.mark(Mark(slab(1, hy + hh * .35), 'fur_top', groups=('head',), jitter=.4))
    s.mark(Mark(mirrored(ellipsoid((hw / 2, hy - hh * .12, front + v(2.4, 3.0, 3.6)), (.8, .5, 1.2))), 'blush',
                groups=('head',), facing=(1, 0, 0), min_dot=-2))
    # Small round ears high at the back of the head.
    for side in (-1, 1):
        name = 'ear_' + ('l' if side < 0 else 'r')
        root = (side * hw * .36, hy + hh / 2 - .3, hz + hd * .3)
        s.bone(name, 'head', root)
        s.rbox(name, (root[0] + side * .2, root[1] + .7, root[2]), (v(1.5, 1.7, 1.9), v(1.5, 1.6, 1.8), .9), 'fur', .3,
               (0, 0, -side * 16), root, group='ear', name='ear')
    s.mark(Inset('inner', faces=('north',), left=.35, right=.35, top=.3, bottom=.2, groups=('ear',)))
    if s.age >= 1:
        s.mark(Mark(ellipsoid((0, cy, zf), (W * .3, H * .45, 2.4)), 'cream', groups=('body',), jitter=.4))
    # Something resting on its head, as capybaras do: a cocoa sprout, then a pod.
    top_y = hy + hh / 2
    if s.age == 0:
        s.beam('head', (0, top_y - .2, hz), (0, top_y + 1.2, hz - .1), .35, 'stem', group='acc', name='sprout')
        _leaf(s, 'head', (0, top_y + 1.1, hz), (-1.6, top_y + 2.0, hz - .2), 1.2)
        _leaf(s, 'head', (0, top_y + 1.1, hz), (1.5, top_y + 1.8, hz + .2), 1.1)
    else:
        r = v(0, 1.0, 1.3)
        pc = (0, top_y + r * .95, hz - .2)
        s.rbox('head', pc, (r * 2.0, r * 1.9, r * 2.6), 'pod', r * .45, group='acc', name='cocoa_pod')
        for end in (-1, 1):
            s.rbox('head', (pc[0], pc[1], pc[2] + end * r * 1.55), (r * 1.4, r * 1.3, r * 1.0), 'pod', r * .3,
                   group='acc', name='cocoa_pod')
            s.cube('head', (pc[0], pc[1], pc[2] + end * r * 2.15), (r * .6, r * .6, r * .5), 'pod', group='acc',
                   name='cocoa_pod')
        s.mark(Bands((1, .25, 0), -5, r * .55, .3, 'pod_rib', groups=('acc',), mats=('pod',)))
        s.mark(Mark(slab(1, pc[1] + r * .5), 'pod_light', groups=('acc',), mats=('pod',)))
        s.cube('head', (0, pc[1], pc[2] + r * 2.55), (.5, .5, .8), 'stem', group='acc', name='pod_stem')
        _leaf(s, 'head', (0, pc[1] + .3, pc[2] + r * 2.4), (1.4, pc[1] + 1.2, pc[2] + r * 3.1), 1.2)
    return s.finish()


# -------------------------------------------------------------------------- sphinx

def _feather_fan(s, bone, root, side, specs, colour_for, plane_tilt=34, group='wing'):
    """Feathers fanned in a near-vertical plane that sweeps up and back from the shoulder."""
    t = math.radians(plane_tilt)
    up = np.array([side * math.sin(t), math.cos(t), 0.0])
    back = np.array([0.0, 0.0, 1.0])
    root = np.array(root, float)
    for i, (angle, length, breadth, offset) in enumerate(specs):
        a = math.radians(angle)
        d = back * math.cos(a) + up * math.sin(a)
        start = root + up * offset[0] + back * offset[1]
        s.beam(bone, start, start + d * length, .6, colour_for(i), breadth, group=group, name='feather')


def _moonstone_sphinx(stage):
    s = Pet('moonstone_sphinx', stage, dict(fur='#E9D4AF', fur_top='#D6BB8F', cream='#FFF8EC', inner='#F2B9C2',
                                            nose='#C9867E', mouth='#6A4A3A', blush='#F2AFAF', lapis='#2C4A9C',
                                            gold='#E7B54C', moon='#E3ECFF', moon_mid='#BFD0FA', moon_deep='#9AA8E8',
                                            kohl='#2A2230'),
            kinds=dict(fur_top='fur', lapis='cloth', moon='gem', moon_mid='gem', moon_deep='gem'))
    v = s.v
    s.scale = v(.72, .76, .8)
    leg = v(3.4, 5.0, 6.6)
    L, W, H = v(7.6, 10.4, 12.6), v(5.0, 5.6, 6.0), v(4.6, 5.2, 5.6)
    cy = leg + H * .45
    cz = .6
    zf, zb, top, bottom = _torso(s, L, W, H, cy, cz, chest=1.08, rump=.96)
    s.bones['body'] = ('root', [0, cy, cz])
    quad_legs(s, W * .28, zf + v(1.5, 1.9, 2.2), zb - v(1.6, 1.9, 2.2), cy - H * .1, cy - H * .1, v(1.9, 2.0, 2.1),
              paw='cream', thigh=(v(2.4, 2.8, 3.1), v(2.8, 3.6, 4.0), v(2.8, 3.6, 4.0)))
    # A proud neck lifts the head above the shoulders.
    neck_h = v(0, 1.6, 2.6)
    if neck_h:
        s.rbox('body', (0, top + neck_h / 2 - .3, zf + 1.6), (W * .6, neck_h + 1.6, 3.4), 'fur', .6, name='neck')
    hw, hh, hd = v(7.2, 6.8, 6.6), v(6.2, 5.8, 5.6), v(5.8, 5.6, 5.6)
    hy = top + neck_h + hh * .3
    hz = zf + v(-.3, .2, .5)
    front = _head(s, (0, hy, hz), (hw, hh, hd), (0, hy - hh * .4, hz + hd * .2))
    sw, sh, sd = v(3.2, 3.0, 3.0), v(2.0, 2.0, 2.0), v(.9, 1.2, 1.5)
    sy = hy - hh * .28
    s.rbox('head', (0, sy, front - sd / 2 + .4), (sw, sh, sd + .8), 'cream', .45, group='snout', name='muzzle')
    snout_front = front - sd
    _nose(s, (0, sy + sh * .3, snout_front - .1), (1.1, .75, .6))
    _smile(s, 0, sy + sh * .1, snout_front, .7, .3, .12, drop=.3)
    ey = hy + hh * .06
    ex = hw * .235
    s.mark(Mark(mirrored(ellipsoid((hw * .3, hy - hh * .3, front + .6), (hw * .22, hh * .2, 3.0))), 'cream',
                groups=('head',), jitter=.2))
    s.mark(Mark(either(ellipsoid((0, cy + H * .1, zf), (W * .34, H * .7, 2.8)), slab(1, -1e9, cy - H * .25)), 'cream',
                groups=('body',), jitter=.4))
    s.mark(Mark(slab(1, cy + H * .34), 'fur_top', groups=('body',), jitter=.4))
    # Egyptian eyes: ice-blue, kohl-lined, with a long wing.
    s.mark(Eye((ex, ey, front), v(2.1, 1.9, 1.8), v(2.3, 2.0, 1.85), iris='#2C5DA8', iris_light='#8FE0FF',
               outline='#2A2230', style='round' if s.age == 0 else 'almond', slit=s.age > 0, tilt=v(.05, .14, .18),
               wing=v(.2, .55, .75)))
    ew, eh = v(3.4, 3.7, 3.9), v(3.6, 4.6, 5.4)
    for side in (-1, 1):
        _ear(s, side, (side * hw * .3, hy + hh / 2 - .5, hz + .4), ew, eh, v(1.0, 1.1, 1.2), 'fur', 'inner',
             tilt=v(18, 16, 14), lean=-5)
    # Crescent-moon crown.
    crown_y = hy + hh / 2 + .1
    s.bone('crown', 'head', (0, crown_y, hz))
    r = v(1.0, 1.5, 2.0)
    n = v(3, 5, 7)
    for j in range(n):
        a = math.radians(200 + 140 * j / (n - 1))
        c = (math.cos(a) * r, crown_y + r + .5 + math.sin(a) * r, hz - hd * .1)
        thick = .6 + .5 * (1 - abs(j - (n - 1) / 2) / ((n - 1) / 2))
        s.cube('crown', c, (2 * r * math.tan(math.radians(140 / (n - 1) / 2)) + .45, thick, .7), 'gold',
               (0, 0, math.degrees(a) + 90), c, group='acc', name='crescent')
    s.rbox('crown', (0, crown_y + .55, hz - hd * .1 - .1), (v(1.0, 1.2, 1.5), v(1.0, 1.2, 1.5), .8), 'moon', .25,
           group='acc', name='moonstone')
    s.cube('crown', (0, crown_y + .1, hz), (hw * .78, .6, hd * .9), 'gold', group='acc', name='circlet')
    if s.age >= 1:
        # Nemes headcloth: striped lappets falling beside the face, the sphinx's signature.
        lap_h = v(0, hh * 1.05, hh * 1.3)
        for side in (-1, 1):
            s.rbox('head', (side * (hw / 2 + .3), hy - hh * .5 + lap_h * .32, hz + .6), (.9, lap_h, hd * .62), 'lapis', .3,
                   group='acc', name='nemes')
        s.rbox('head', (0, hy + hh / 2 - .1, hz + .4), (hw + .5, 1.0, hd * .86), 'lapis', .3, group='acc', name='nemes_cap')
        s.mark(Bands((0, 1, 0), hy - hh * 2, .7, .42, 'gold', groups=('acc',), parts=('nemes_',)))
        s.mark(Bands((1, 0, 0), -hw, .7, .42, 'gold', groups=('acc',), parts=('nemes_cap',)))
    # Broad collar of lapis and gold.
    if s.age >= 1:
        ny = (hy - hh / 2) - .2
        s.rbox('body', (0, ny, zf + 1.6), (W * 1.12, v(0, 1.6, 2.0), 4.0), 'lapis', .4, group='acc', name='collar')
        bib_h = v(0, 2.4, 3.2)
        s.rbox('body', (0, ny - bib_h / 2, zf - .2), (W * .82, bib_h, 1.0), 'lapis', .3, group='acc', name='collar_bib')
        s.mark(Bands((0, 1, 0), ny - 6, .8, .45, 'gold', groups=('acc',), parts=('collar',)))
        s.rbox('body', (0, ny - bib_h + .2, zf - .75), (1.2, 1.2, .6), 'moon', .25, group='acc', name='collar_gem')
    # Moonstone wings: a fan of pearl feathers that deepen to periwinkle at the tips.
    rows = v([(70, 3.4, 1.6, (0, 0)), (48, 3.2, 1.6, (0, .3)), (26, 2.8, 1.5, (0, .6))],
             [(80, 6.2, 1.9, (0, 0)), (63, 6.0, 1.9, (0, .3)), (46, 5.6, 1.9, (0, .6)), (29, 5.0, 1.8, (0, .9)),
              (13, 4.2, 1.7, (0, 1.2))],
             [(84, 9.6, 2.2, (0, 0)), (70, 9.4, 2.2, (0, .25)), (56, 9.0, 2.2, (0, .5)), (42, 8.2, 2.1, (0, .75)),
              (28, 7.2, 2.0, (0, 1.0)), (14, 6.0, 1.9, (0, 1.25)), (2, 4.8, 1.8, (0, 1.5))])
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        root = (side * W * .36, top + .2, zf + v(2.2, 2.8, 3.4))
        s.bone('wing_' + sfx, 'body', root)
        _feather_fan(s, 'wing_' + sfx, root, side, [f for f in rows if f[0] >= 45], lambda i: ('moon', 'moon_mid')[i % 2])
        s.bone('wingtip_' + sfx, 'wing_' + sfx, root)
        _feather_fan(s, 'wingtip_' + sfx, root, side, [f for f in rows if f[0] < 45], lambda i: ('moon_mid', 'moon')[i % 2])
        s.rbox('wing_' + sfx, (root[0] + side * .2, root[1] + v(.6, 1.0, 1.4), root[2] + v(.6, .9, 1.2)),
               (.9, v(1.6, 2.4, 3.0), v(2.0, 3.0, 3.8)), 'gold', .3, group='wing', name='covert')
    s.mark(Mark(slab(1, top + v(2.4, 4.6, 7.0)), 'moon_deep', groups=('wing',), mats=('moon', 'moon_mid'), jitter=.3))
    s.mark(Mark(slab(2, zf + v(5.4, 8.4, 11.4)), 'moon_deep', groups=('wing',), mats=('moon', 'moon_mid'), jitter=.3))
    # A slender tail with a moonstone tuft.
    tz = zb - .4
    t = top - .8
    pts = v([(0, t, tz), (0, t - 1.0, tz + 2.0), (0, t + .4, tz + 3.4)],
            [(0, t, tz), (0, t - 1.6, tz + 2.6), (0, t - .8, tz + 5.0), (0, t + 1.2, tz + 6.2)],
            [(0, t, tz), (0, t - 2.0, tz + 3.0), (0, t - 1.4, tz + 6.0), (0, t + 1.0, tz + 7.8), (0, t + 3.0, tz + 8.6)])
    names = chain(s, 'tail', pts, 1.4, 'fur')
    gem = v(1.6, 2.0, 2.4)
    s.rbox(names[-1], pts[-1], (gem, gem, gem), 'moon', .4, rot=(45, 0, 45), group='acc', name='tail_gem')
    if s.age == 2:
        for side in (-1, 1):
            s.rbox('leg_f' + ('l' if side < 0 else 'r') + '_lower', (side * W * .28, 2.6, zf + 2.2), (2.5, .8, 2.5), 'gold',
                   .2, group='acc', name='anklet')
    return s.finish()


BUILDERS = {'peach_shiba': _peach_shiba, 'buttercream_corgi': _buttercream_corgi, 'pumpkin_fox': _pumpkin_fox,
            'lavender_cat': _lavender_cat, 'cocoa_capybara': _cocoa_capybara, 'moonstone_sphinx': _moonstone_sphinx}


def build(species, stage):
    return BUILDERS[species](stage)
