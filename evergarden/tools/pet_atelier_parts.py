"""Shared anatomy for Pet Atelier species: heads, ears, faces, bows, leaves, feathers."""
from __future__ import annotations

import math

import numpy as np

from pet_atelier import (rotation, Pet, Mark, Bands, Eye, Inset, Toward, Dots, quad_legs, chain, ellipsoid, slab, boxed,
                         both, either, minus, mirrored, polyline)

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


def decal(m, layer=2):
    """Lift a mark above the coat (mouths, blush, nose shine, eyebrows)."""
    m.layer = layer
    return m


def _nose(s, centre, size, mat='nose'):
    s.rbox('head', centre, size, mat, min(size) * .3, group='nose', name='nose')
    x, y, z = centre
    # A small wet highlight on the upper left of the nose.
    s.mark(decal(Mark(ellipsoid((x + size[0] * .18, y + size[1] * .22, z - size[2] / 2), (size[0] * .16, size[1] * .16, .6)),
                      '#F4E8EA', groups=('nose',), facing=(0, .4, -1), min_dot=.3)))


def _smile(s, x, y, z, width, depth, r, colour='mouth', drop=None):
    """An ω mouth hanging from the nose."""
    drop = drop if drop is not None else depth
    pts_r = [(0, y, z), (0, y - drop, z), (width * .45, y - drop - depth * .35, z), (width, y - drop * .55, z)]
    pts_l = [(-p[0], p[1], p[2]) for p in pts_r]
    s.mark(decal(Mark(either(polyline(pts_r, r), polyline(pts_l, r)), colour, groups=('snout', 'head'), facing=(0, 0, -1),
                      min_dot=.5)))


def _blush(s, x, y, z, w, h, colour='blush'):
    s.mark(decal(Mark(mirrored(ellipsoid((x, y, z), (w, h, 1.4))), colour, groups=('head', 'snout'), facing=(0, 0, -1),
                      min_dot=.3), 1))


# ---------------------------------------------------------------------------- shiba

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


# ------------------------------------------------------------------ body builders

def quad_body(s, L, W, H, leg, thick, *, cz=.5, chest=1.06, rump=.96, paw='cream', thigh=None, paw_len=None,
              lift=.42, hip=.1, inset=None, barrel=False, mat='fur', front_thick=None, back_thick=None, bevel=None,
              xw=.29):
    """A four-legged body (chest + rump, or one barrel) with articulated legs; returns key positions."""
    cy = leg + H * lift
    if barrel:
        s.rbox('body', (0, cy, cz), (W, H, L), mat, bevel if bevel is not None else min(W, H) * .28, name='barrel')
        zf, zb = cz - L / 2, cz + L / 2
    else:
        zf, zb, _, _ = _torso(s, L, W, H, cy, cz, mat, chest, rump, bevel)
    s.bones['body'] = ('root', [0, cy, cz])
    fi, bi = inset if inset else (min(2.6, L * .2), min(2.7, L * .21))
    quad_legs(s, W * xw, zf + fi, zb - bi, cy - H * hip, cy - H * hip, thick, mat, paw, paw_len, back_thick, thigh,
              front_thick)
    return dict(cy=cy, cz=cz, zf=zf, zb=zb, top=cy + H / 2, bottom=cy - H / 2, L=L, W=W, H=H)


def face(s, b, size, *, rise=.4, fwd=.15, pos=None, mat='fur', bevel=None, pivot=None, parent='body', snout=None,
         nose=None, mouth='w', eyes=None, ears=None, blush=True, cheeks=None):
    """Head, snout, nose, mouth, painted eyes and ears in one call; returns positions for extra marks."""
    hw, hh, hd = size
    if pos:
        hy, hz = pos
    else:
        hy, hz = b['top'] + hh * rise, b['zf'] - hd * fwd
    s.bone('head', parent, pivot or (0, hy - hh * .4, hz + hd * .2))
    s.rbox('head', (0, hy, hz), size, mat, bevel if bevel is not None else min(size) * .17, name='skull')
    front = hz - hd / 2
    f = dict(hy=hy, hz=hz, hw=hw, hh=hh, hd=hd, front=front, snout_front=front, sy=hy - hh * .27, sw=hw * .4,
             sh=hh * .3, sd=0)
    if snout:
        sw, sh, sd = snout['size']
        sy = hy + hh * snout.get('dy', -.27)
        s.rbox('head', (0, sy, front - sd / 2 + .4), (sw, sh, sd + .8), snout.get('mat', 'cream'),
               snout.get('bevel', min(sw, sh) * .2), group='snout', name='snout')
        f.update(sy=sy, sw=sw, sh=sh, sd=sd, snout_front=front - sd)
    sf, sy, sh = f['snout_front'], f['sy'], f['sh']
    if nose:
        nw, nh, nd = nose['size']
        _nose(s, (0, sy + sh * nose.get('dy', .3), sf - .15), (nw, nh, nd), nose.get('mat', 'nose'))
    if mouth:
        k = s.v(.9, .95, 1.0)
        r = .14 if s.age < 2 else .16
        if mouth == 'w':
            _smile(s, 0, sy + sh * .06, sf, .95 * k, .5 * k, r, drop=.42 * k)
        elif mouth == 'cat':
            _smile(s, 0, sy + sh * .12, sf, .72 * k, .32 * k, r * .85, drop=.3 * k)
        elif mouth == 'smile':
            s.mark(decal(Mark(polyline([(-.8 * k, sy, sf), (-.35 * k, sy - .35 * k, sf), (.35 * k, sy - .35 * k, sf),
                                        (.8 * k, sy, sf)], r), 'mouth', groups=('snout', 'head'), facing=(0, 0, -1),
                              min_dot=.5)))
        elif mouth == 'grin':
            my = sy - sh * .1
            s.mark(decal(Mark(ellipsoid((0, my, sf), (1.05 * k, .66 * k, .9)), 'mouth_in', groups=('snout', 'head'),
                              facing=(0, 0, -1), min_dot=.5)))
            s.mark(decal(Mark(ellipsoid((0, my - .32 * k, sf), (.66 * k, .4 * k, .9)), 'tongue', groups=('snout', 'head'),
                              facing=(0, 0, -1), min_dot=.5)))
            _smile(s, 0, sy + sh * .06, sf, 1.15 * k, .2, r, drop=.2)
    e = dict(eyes or {})
    ex = hw * e.pop('dx', .235)
    ey = hy + hh * e.pop('dy', .05)
    ew_, eh_ = e.pop('w', s.v(2.0, 1.85, 1.7)), e.pop('h', s.v(2.4, 2.2, 2.0))
    ez = e.pop('z', front)
    s.mark(Eye((ex, ey, ez), ew_, eh_, **e))
    f.update(ex=ex, ey=ey, ew=ew_, eh=eh_)
    if blush:
        _blush(s, hw * .34, ey - eh_ * .8, front, .85 * s.v(1.05, 1, .95), .46)
    if cheeks:
        cw, ch, cd = cheeks.get('size', (1.8, 2.5, 3.6))
        for side in (-1, 1):
            s.rbox('head', (side * (hw / 2 + cheeks.get('out', .1)), hy - hh * cheeks.get('dy', .25), hz - hd * .05),
                   (cw, ch, cd), cheeks.get('mat', mat), min(cw, ch) * .28, (0, 0, side * cheeks.get('tilt', 0)),
                   name='cheek')
    if ears:
        kind = ears.get('kind', 'point')
        for side in (-1, 1):
            x = side * hw * ears.get('x', .29)
            root = (x, hy + hh / 2 - ears.get('sink', .5), hz + ears.get('z', .5))
            w, h, d = ears['w'], ears['h'], ears.get('d', 1.2)
            if kind == 'point':
                _ear(s, side, root, w, h, d, ears.get('mat', mat), ears.get('inner', 'inner'), tilt=ears.get('tilt', 12),
                     lean=ears.get('lean', -6), fold=ears.get('fold', 0))
            else:
                round_ear(s, side, root, w, h, d, ears.get('mat', mat), ears.get('inner', 'inner'),
                          tilt=ears.get('tilt', 12), lean=ears.get('lean', -4), fold=ears.get('fold', 0))
    return f


def round_ear(s, side, root, w, h, d, mat, inner, tilt=12, lean=-4, fold=0.0):
    """A rounded ear (bears, mice; rabbits when tall): a soft block with an inner panel."""
    name = 'ear_' + ('l' if side < 0 else 'r')
    s.bone(name, 'head', root)
    root = np.array(root, float)
    R = rotation((lean - fold, 0, -side * tilt))
    rot = (lean - fold, 0, -side * tilt)
    c = root + R @ np.array([0, h / 2, 0])
    s.rbox(name, c, (w, h, d), mat, min(w, h, d) * .32, rot, c, group='ear', name='ear_round')
    ci = root + R @ np.array([0, h * .54, -d / 2 - .04])
    s.rbox(name, ci, (w * .58, h * .66, .3), inner, min(w, h) * .12, rot, ci, group='ear', name='ear_inner')


def limb(s, name, parent, top, bottom, thick, mat, paw=None, paw_len=None, lower=True, toe=0.0):
    """A leg or arm from top to bottom with an optional knee bone and a paw."""
    s.bone(name, parent, top)
    top, bottom = np.array(top, float), np.array(bottom, float)
    mid = (top + bottom) / 2
    if lower:
        s.bone(name + '_lower', name, mid)
        s.beam(name, top + (top - mid) * .15, mid + (bottom - mid) * .12, thick, mat, bevel=thick * .16, group='leg',
               name='limb_upper')
        s.beam(name + '_lower', mid, bottom, thick * .94, mat, bevel=thick * .16, group='leg', name='limb_lower')
        last = name + '_lower'
    else:
        s.beam(name, top, bottom, thick, mat, bevel=thick * .16, group='leg', name='limb')
        last = name
    if paw:
        pl = paw_len or thick * 1.2
        ph = max(.7, thick * .45)
        s.rbox(last, (bottom[0], bottom[1] + ph / 2 - thick * .25, bottom[2] - (pl - thick) / 2 - toe),
               (thick * 1.1, ph, pl), paw, min(ph, thick) * .3, group='paw', name='paw')
    return last


def folded_wing(s, side, root, length, height, mat, tip_mat=None, tips=3, thick=.7, droop=8, spread=10,
                name='wing', tip_bone=True, group='wing'):
    """A bird wing folded along the body: a covert block and layered primaries behind it."""
    sfx = 'l' if side < 0 else 'r'
    bone = f'{name}_{sfx}'
    s.bone(bone, 'body', root)
    x, y, z = root
    rot = (droop, -side * spread, 0)
    c = (x, y - height * .45, z + length * .32)
    s.rbox(bone, c, (thick, height, length * .7), mat, min(thick, height) * .3, rot, root, group=group, name='covert')
    tb = bone
    if tip_bone:
        tb = s.bone(f'{name}tip_{sfx}', bone, (x, y, z + length * .55))
    for i in range(tips):
        hh = height * (.62 - i * .12)
        cz_ = z + length * (.62 + i * .14)
        cy_ = y - height * (.5 + i * .06)
        s.rbox(tb, (x + side * .05 * i, cy_, cz_), (thick * .9, hh, length * .42), tip_mat or mat, min(thick, hh) * .3,
               rot, root, group=group, name='primary')
    return bone
