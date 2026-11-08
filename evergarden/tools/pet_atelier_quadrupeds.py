"""Pet Atelier: four-legged companions (dogs, foxes, cats, capybaras and kin)."""
from __future__ import annotations

import math

import numpy as np

from pet_atelier import (rotation, Pet, Mark, Bands, Eye, Inset, Toward, Dots, quad_legs, chain, ellipsoid, slab, boxed,
                         both, either, minus, mirrored, polyline)
from pet_atelier_parts import *  # noqa: F401,F403
from pet_atelier_parts import (_torso, _head, _ear, _nose, _smile, _blush, _bow, _pumpkin, _leaf, _all, _feather_fan,
                               heart, quad_body, face, round_ear, limb, folded_wing)

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
    s.scale = v(.74, .78, .8)
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


def _cherries(s, bone, top, r, pair=True, group='acc'):
    """One or two glossy cherries hanging from a shared stem with a leaf."""
    x, y, z = top
    offsets = (-.75, .75) if pair else (0,)
    for dx in offsets:
        c = (x + dx * r * 1.3, y - r * 2.6, z)
        s.beam(bone, (x, y, z), (c[0], c[1] + r * .8, z), .28, 'stem', group=group, name='cherry_stem')
        s.rbox(bone, c, (r * 2, r * 1.9, r * 2), 'cherry', r * .55, group=group, name='cherry')
        s.mark(Mark(ellipsoid((c[0] + r * .4, c[1] + r * .4, c[2] - r), (r * .35, r * .3, .6)), '#FFE3E6', groups=(group,),
                    mats=('cherry',), facing=(0, 0, -1)))
    _leaf(s, bone, (x, y, z), (x + 1.5 * r + .6, y + 1.0 * r, z - .2), 1.1 * r + .3)


def _ringed_tail(s, pts, widths, mats):
    names = chain(s, 'tail', pts, widths, mats)
    return names


# ------------------------------------------------------------------- red panda

def _cherry_red_panda(stage):
    s = Pet('cherry_red_panda', stage, dict(fur='#C9562C', fur_top='#B5461F', dark='#3B2522', cream='#FFF6EA',
                                            inner='#FFF6EA', ring='#E9A266', tear='#A23F21', nose='#2A1C1E',
                                            mouth='#4A2A2A', blush='#F49A8A', cherry='#D9283F', stem='#5C7A3A',
                                            leaf='#6CAA4E', leaf_vein='#4B8238', bamboo='#B9C46A', bamboo_ring='#8C9A44',
                                            strap='#7A5A3C'),
            kinds=dict(fur_top='fur', ring='fur', tear='fur', cherry='smooth', stem='plain', bamboo='wood',
                       bamboo_ring='wood', strap='cloth'))
    v = s.v
    s.scale = v(.72, .75, .78)
    b = quad_body(s, v(8.0, 10.4, 12.4), v(5.6, 6.2, 6.6), v(5.0, 5.4, 5.8), v(2.6, 3.4, 4.0), v(2.1, 2.2, 2.3),
                  paw='dark', thigh=(v(2.6, 2.9, 3.1), v(3.0, 3.4, 3.8), v(3.0, 3.4, 3.8)))
    f = face(s, b, (v(7.8, 7.8, 8.0), v(6.4, 6.2, 6.2), v(5.8, 5.8, 6.0)), rise=v(.3, .38, .42), fwd=v(.05, .12, .16),
             snout={'size': (v(3.4, 3.4, 3.5), v(2.2, 2.3, 2.4), v(1.0, 1.5, 1.9)), 'mat': 'cream'},
             nose={'size': (v(1.4, 1.5, 1.6), v(.9, 1.0, 1.05), .8)},
             eyes={'iris': '#3A2219', 'iris_light': '#A0603A', 'tilt': .06},
             ears={'w': v(3.0, 3.2, 3.3), 'h': v(2.8, 3.0, 3.2), 'tilt': v(26, 24, 22), 'lean': -4, 'inner': 'cream', 'x': .33},
             cheeks={'size': (v(1.7, 1.8, 1.9), v(2.4, 2.4, 2.5), v(3.4, 3.5, 3.6)), 'mat': 'cream', 'dy': .28})
    hy, hh, hw, front, ex, ey = f['hy'], f['hh'], f['hw'], f['front'], f['ex'], f['ey']
    # The red panda face: white cheeks and brows, russet tear marks, dark legs and belly.
    s.mark(Mark(mirrored(ellipsoid((ex * 1.05, ey + f['eh'] * .85, front), (.75, .5, 1.2))), 'cream', groups=('head',),
                facing=(0, 0, -1)))
    s.mark(Mark(mirrored(ellipsoid((hw * .3, hy - hh * .3, front + .5), (hw * .24, hh * .2, 3.0))), 'cream',
                groups=('head',), jitter=.2))
    s.mark(Mark(mirrored(polyline([(ex, ey - f['eh'] * .6, front), (ex * .7, f['sy'] - .3, front - .2)], .32)), 'tear',
                groups=('head', 'snout'), facing=(0, 0, -1), min_dot=.4))
    s.mark(Mark(slab(1, -1e9, b['cy'] - b['H'] * .12), 'dark', groups=('body',), jitter=.45))
    s.mark(Mark(slab(1, b['cy'] + b['H'] * .36), 'fur_top', groups=('body',), jitter=.4))
    s.mark(Mark(_all, 'dark', groups=('leg', 'paw')))
    s.mark(Mark(slab(1, hy + hh / 2 + v(2.0, 2.2, 2.4)), 'dark', groups=('ear',), mats=('fur',)))
    # A big ringed tail that sweeps up.
    t, tz = b['top'] - .6, b['zb'] - .4
    pts = v([(0, t, tz), (0, t - 1.4, tz + 2.2), (0, t - .6, tz + 4.2), (0, t + 1.0, tz + 5.4)],
            [(0, t, tz), (0, t - 1.8, tz + 2.4), (0, t - 1.4, tz + 4.8), (0, t + .2, tz + 6.6), (0, t + 2.2, tz + 7.6),
             (0, t + 4.0, tz + 7.6)],
            [(0, t, tz), (0, t - 2.2, tz + 2.6), (0, t - 2.0, tz + 5.4), (0, t - .4, tz + 7.8), (0, t + 2.0, tz + 9.2),
             (0, t + 4.6, tz + 9.4), (0, t + 6.4, tz + 8.2)])
    n = len(pts) - 1
    _ringed_tail(s, pts, [v(2.8, 3.1, 3.4) * (1 - .05 * i) for i in range(n)], ['fur', 'ring'] * 4)
    # Cherries on a ribbon; bamboo leaf; the adult carries a little bamboo satchel.
    ny = hy - hh / 2 + .1
    s.rbox('body', (0, ny, b['zf'] + 1.4), (b['W'] * .98, 1.1, 2.8), 'cherry', .3, group='acc', name='collar')
    _cherries(s, 'body', (b['W'] * .2, ny - .3, b['zf'] - .1), v(.55, .65, .7), pair=s.age == 2)
    if s.age >= 1:
        _leaf(s, 'body', (-b['W'] * .2, ny, b['zf'] + .1), (-b['W'] * .2 - 1.4, ny - 1.6, b['zf'] - .6), 1.2)
    if s.age == 2:
        bz, by = b['cz'] + 1.0, b['cy'] + .4
        s.rbox('body', (b['W'] / 2 + .7, by, bz), (1.4, 3.2, 3.6), 'bamboo', .3, group='acc', name='satchel')
        s.mark(Bands((0, 1, 0), by - 3, 1.1, .16, 'bamboo_ring', groups=('acc',), mats=('bamboo',)))
        s.beam('body', (b['W'] / 2 + .5, by + 1.6, bz), (-b['W'] / 2 - .1, b['top'] + .1, b['zf'] + 2.4), .7, 'strap',
               .4, group='acc', name='strap')
    return s.finish()


# --------------------------------------------------------------------- raccoon

def _maple_raccoon(stage):
    s = Pet('maple_raccoon', stage, dict(fur='#8F8B95', fur_top='#77737F', mask='#34303A', cream='#F2EEF0',
                                         inner='#F2EEF0', dark='#3E3A44', ring='#3E3A44', nose='#211D24',
                                         mouth='#3A2E34', blush='#E8A2A6', maple='#E4572E', maple_dark='#B8381E',
                                         leaf_vein='#A3311A', stem='#6E4A2C', pack='#8A5A34', pack_dark='#6A4226',
                                         roll='#5E8F6A', buckle='#E3B54C'),
            kinds=dict(fur_top='fur', mask='fur', ring='fur', maple='leaf', maple_dark='leaf', leaf_vein='leaf',
                       stem='plain', pack='cloth', pack_dark='cloth', roll='cloth', buckle='metal'))
    v = s.v
    s.scale = v(.75, .78, .8)
    b = quad_body(s, v(8.4, 10.8, 12.8), v(6.0, 6.6, 7.2), v(5.4, 6.0, 6.4), v(2.6, 3.3, 3.9), v(2.2, 2.3, 2.4),
                  paw='dark', chest=1.0, rump=1.06, thigh=(v(2.8, 3.1, 3.4), v(3.2, 3.6, 4.0), v(3.2, 3.6, 4.0)))
    f = face(s, b, (v(7.6, 7.6, 7.8), v(6.2, 6.0, 6.0), v(5.8, 5.8, 6.0)), rise=v(.28, .34, .38), fwd=v(.05, .12, .15),
             snout={'size': (v(3.0, 3.0, 3.0), v(2.1, 2.1, 2.2), v(1.4, 2.2, 2.8)), 'mat': 'cream'},
             nose={'size': (v(1.4, 1.4, 1.5), v(.9, .95, 1.0), .8)},
             eyes={'iris': '#7A4A1C', 'iris_light': '#F0B85A', 'outline': '#16121A', 'tilt': .05},
             ears={'w': v(2.8, 2.9, 3.0), 'h': v(2.6, 2.8, 3.0), 'tilt': v(18, 16, 14), 'inner': 'cream', 'x': .31},
             cheeks={'size': (v(1.6, 1.7, 1.8), v(2.2, 2.3, 2.4), v(3.2, 3.4, 3.6)), 'mat': 'cream', 'dy': .3, 'tilt': 12},
             blush=False)
    hy, hh, hw, front, ex, ey = f['hy'], f['hh'], f['hw'], f['front'], f['ex'], f['ey']
    # The bandit mask, white brows and muzzle.
    s.mark(Mark(either(mirrored(ellipsoid((ex, ey - .15, front), (1.75, 1.35, 1.6))),
                       ellipsoid((0, ey + .1, front), (1.4, .7, 1.6))), 'mask', groups=('head',), facing=(0, 0, -1), min_dot=.2))
    s.mark(Mark(mirrored(ellipsoid((ex * 1.05, ey + 1.75, front), (1.3, .45, 1.2))), 'cream', groups=('head',),
                facing=(0, 0, -1)))
    s.mark(Mark(slab(1, -1e9, hy - hh * .3), 'cream', groups=('head',), jitter=.2))
    s.mark(Mark(slab(1, b['cy'] + b['H'] * .34), 'fur_top', groups=('body',), jitter=.4))
    s.mark(Mark(slab(1, -1e9, b['cy'] - b['H'] * .3), 'cream', groups=('body',), jitter=.4))
    s.mark(Mark(slab(1, -1e9, v(1.6, 2.0, 2.4)), 'dark', groups=('leg', 'paw'), jitter=.3))
    s.mark(Mark(slab(1, hy + hh / 2 + v(1.9, 2.1, 2.3)), 'dark', groups=('ear',), mats=('fur',)))
    # Ringed tail: five bands, held up in a soft curve.
    t, tz = b['top'] - .4, b['zb'] - .3
    pts = v([(0, t, tz), (0, t - 1.0, tz + 2.0), (0, t - .2, tz + 3.8), (0, t + 1.0, tz + 4.8)],
            [(0, t, tz), (0, t - 1.2, tz + 2.2), (0, t - .8, tz + 4.4), (0, t + .4, tz + 6.2), (0, t + 1.8, tz + 7.2)],
            [(0, t, tz), (0, t - 1.6, tz + 2.6), (0, t - 1.2, tz + 5.0), (0, t + .2, tz + 7.2), (0, t + 2.0, tz + 8.6),
             (0, t + 3.8, tz + 9.0)])
    n = len(pts) - 1
    chain(s, 'tail', pts, [v(2.8, 3.2, 3.5) * (1 - .06 * i) for i in range(n)], ['fur', 'ring'] * 4)
    # Maple leaves: a sprig on the pup, a leaf cape on the youngster, cape plus hiking pack on the adult.
    if s.age == 0:
        _maple(s, 'head', (hw * .15, hy + hh / 2 + .1, f['hz']), 1.2, -20)
    else:
        n = v(0, 3, 5)
        for i in range(n):
            a = (i - (n - 1) / 2) * 30
            x = math.sin(math.radians(a)) * b['W'] * .42
            _maple(s, 'body', (x, b['top'] + .2, b['zf'] + 2.6 + abs(a) * .03), v(0, 1.3, 1.5), a, flat=True)
    if s.age == 2:
        pz, py = b['cz'] + 1.6, b['top'] + 1.6
        s.rbox('body', (0, py, pz), (b['W'] * .76, 3.2, 4.0), 'pack', .5, group='acc', name='backpack')
        s.rbox('body', (0, py + 2.0, pz - .2), (b['W'] * .9, 1.4, 1.6), 'roll', .5, group='acc', name='bedroll')
        s.rbox('body', (0, py - .2, pz - 2.1), (b['W'] * .5, 1.8, .6), 'pack_dark', .2, group='acc', name='pocket')
        s.cube('body', (0, py + .2, pz - 2.45), (.8, .6, .2), 'buckle', group='acc', name='buckle')
    return s.finish()


def _maple(s, bone, centre, r, angle=0, flat=False, group='acc'):
    """A five-pointed maple leaf: five blades radiating from a stem."""
    x, y, z = centre
    for i, (a, k) in enumerate(((0, 1.0), (52, .85), (-52, .85), (104, .55), (-104, .55))):
        ang = math.radians(a + angle)
        if flat:
            tip = (x + math.sin(ang) * r * 1.6 * k, y + .1, z - math.cos(ang) * r * 1.6 * k)
        else:
            tip = (x + math.sin(ang) * r * 1.6 * k, y + math.cos(ang) * r * 1.6 * k, z)
        s.beam(bone, (x, y, z), tip, r * .9 * k, 'maple' if i < 3 else 'maple_dark', .3, group=group, name='maple')
    s.beam(bone, (x, y, z), (x, y - (0 if flat else r * .9), z + (r * .9 if flat else 0)), .25, 'stem', group=group,
           name='maple_stem')


# ---------------------------------------------------------------------- ferret

def _mint_ferret(stage):
    s = Pet('mint_ferret', stage, dict(fur='#A9DECA', fur_top='#8CCBB4', dark='#5FA48C', mask='#6FB39B', cream='#FBF5E6',
                                       inner='#F6C2C6', nose='#E58C9C', mouth='#3E5A50', blush='#F4A8B6',
                                       leaf='#5DB37E', leaf_vein='#3E8A5C', ribbon='#F2F0D9'),
            kinds=dict(fur_top='fur', mask='fur', ribbon='cloth'))
    v = s.v
    s.scale = v(.72, .76, .8)
    # A long, low, arched body: the rump rides higher than the chest.
    L, W, H, leg = v(9.0, 12.2, 15.0), v(4.4, 4.6, 4.8), v(4.0, 4.2, 4.4), v(1.9, 2.3, 2.7)
    cy, cz = leg + H * .42, .8
    s.rbox('body', (0, cy, cz - L * .26), (W, H, L * .5), 'fur', 1.0, name='chest')
    s.rbox('body', (0, cy + v(.4, .6, .8), cz + L * .1), (W * 1.02, H * 1.04, L * .42), 'fur', 1.0, name='arch')
    s.rbox('body', (0, cy + .1, cz + L * .33), (W * .98, H * .98, L * .36), 'fur', 1.0, name='rump')
    s.bones['body'] = ('root', [0, cy, cz])
    zf, zb, top = cz - L / 2, cz + L / 2, cy + H / 2
    quad_legs(s, W * .3, zf + 1.6, zb - 1.7, cy - H * .1, cy - H * .1, v(1.7, 1.8, 1.9), paw='dark')
    b = dict(cy=cy, zf=zf, zb=zb, top=top, W=W, H=H, L=L)
    f = face(s, b, (v(5.8, 5.6, 5.6), v(5.0, 4.8, 4.8), v(5.4, 5.6, 5.8)), rise=v(.18, .2, .22), fwd=v(.2, .3, .35),
             snout={'size': (v(2.8, 2.7, 2.7), v(2.0, 1.9, 1.9), v(1.4, 2.0, 2.4)), 'mat': 'cream', 'dy': -.24},
             nose={'size': (v(1.1, 1.1, 1.2), v(.75, .75, .8), .6)},
             eyes={'iris': '#1E2A2A', 'iris_light': '#4C7D74', 'w': v(1.7, 1.55, 1.45), 'h': v(2.0, 1.8, 1.7)},
             ears={'kind': 'round', 'w': v(2.0, 2.0, 2.1), 'h': v(1.7, 1.7, 1.8), 'd': .9, 'tilt': 24, 'x': .34,
                   'sink': .3})
    hy, hh, hw, front, ex, ey = f['hy'], f['hh'], f['hw'], f['front'], f['ex'], f['ey']
    s.mark(Mark(slab(1, -1e9, ey - .6), 'cream', groups=('head',), jitter=.2))
    s.mark(Mark(either(mirrored(ellipsoid((ex, ey, front), (1.35, .95, 1.4))), ellipsoid((0, ey + .5, front), (.7, .5, 1.4))),
                'mask', groups=('head',), facing=(0, 0, -1), min_dot=.2))
    s.mark(Mark(slab(1, -1e9, cy - H * .2), 'cream', groups=('body',), jitter=.4))
    s.mark(Mark(slab(1, cy + H * .38), 'fur_top', groups=('body',), jitter=.4))
    s.mark(Mark(_all, 'dark', groups=('leg', 'paw')))
    t, tz = top - .9, zb - .3
    pts = v([(0, t, tz), (0, t - .6, tz + 2.0), (0, t - .4, tz + 3.6)],
            [(0, t, tz), (0, t - .8, tz + 2.2), (0, t - .5, tz + 4.4), (0, t + .4, tz + 6.0)],
            [(0, t, tz), (0, t - 1.0, tz + 2.4), (0, t - .8, tz + 4.8), (0, t + .4, tz + 6.8), (0, t + 1.8, tz + 8.0)])
    n = len(pts) - 1
    chain(s, 'tail', pts, [v(2.4, 2.3, 2.3) * (1 - .07 * i) for i in range(n)], ['fur', 'dark'] * 3)
    if s.age >= 1:
        ny = hy - hh / 2 + .2
        s.rbox('body', (0, ny, zf + 1.6), (W * 1.06, 1.1, 2.8), 'ribbon', .3, group='acc', name='scarf')
        for side in ((1,) if s.age == 1 else (-1, 1)):
            _leaf(s, 'body', (side * W * .25, ny - .2, zf + .3), (side * (W * .25 + 1.2), ny - 1.9, zf - .5), 1.5)
    return s.finish()


# ---------------------------------------------------------------------- ermine

def _snowdrop_ermine(stage):
    s = Pet('snowdrop_ermine', stage, dict(fur='#F7F8FC', fur_top='#E4E8F2', dark='#24222C', cream='#FFFFFF',
                                           inner='#F9C9D6', nose='#F29CB2', mouth='#5A4A58', blush='#F7B2C6',
                                           petal='#FFFFFF', petal_tip='#9FD38A', stem='#6FA35C', leaf='#7DBB68',
                                           leaf_vein='#5A9A4C', ribbon='#BFD8F2'),
            kinds=dict(fur_top='fur', petal='smooth', petal_tip='smooth', stem='leaf', ribbon='cloth'))
    v = s.v
    s.scale = v(.72, .76, .8)
    L, W, H, leg = v(8.6, 11.6, 14.2), v(4.2, 4.4, 4.6), v(3.8, 4.0, 4.2), v(1.9, 2.3, 2.7)
    cy, cz = leg + H * .42, .7
    s.rbox('body', (0, cy, cz - L * .24), (W, H, L * .52), 'fur', 1.0, name='chest')
    s.rbox('body', (0, cy + v(.3, .5, .6), cz + L * .2), (W * 1.02, H * 1.03, L * .56), 'fur', 1.0, name='rump')
    s.bones['body'] = ('root', [0, cy, cz])
    zf, zb, top = cz - L / 2, cz + L / 2, cy + H / 2
    quad_legs(s, W * .3, zf + 1.5, zb - 1.7, cy - H * .1, cy - H * .1, v(1.6, 1.7, 1.8), paw='fur')
    b = dict(cy=cy, zf=zf, zb=zb, top=top, W=W, H=H, L=L)
    f = face(s, b, (v(5.8, 5.6, 5.6), v(5.0, 4.8, 4.8), v(5.2, 5.4, 5.6)), rise=v(.2, .24, .26), fwd=v(.2, .28, .32),
             snout={'size': (v(2.6, 2.6, 2.6), v(1.8, 1.8, 1.8), v(1.0, 1.5, 1.9)), 'mat': 'cream', 'dy': -.24},
             nose={'size': (v(1.0, 1.0, 1.1), v(.7, .7, .75), .55)},
             eyes={'iris': '#151526', 'iris_light': '#4A5590', 'w': v(1.9, 1.75, 1.6), 'h': v(2.1, 1.95, 1.8)},
             ears={'kind': 'round', 'w': v(2.1, 2.1, 2.2), 'h': v(1.8, 1.8, 1.9), 'd': .9, 'tilt': 22, 'x': .34,
                   'sink': .3})
    hy, hh, hw, front = f['hy'], f['hh'], f['hw'], f['front']
    s.mark(Mark(slab(1, cy + H * .38), 'fur_top', groups=('body',), jitter=.4))
    # White coat with a black-tipped tail.
    t, tz = top - .9, zb - .3
    pts = v([(0, t, tz), (0, t - .3, tz + 2.2), (0, t + .4, tz + 3.6)],
            [(0, t, tz), (0, t - .6, tz + 2.4), (0, t - .1, tz + 4.6), (0, t + 1.2, tz + 6.0)],
            [(0, t, tz), (0, t - .8, tz + 2.6), (0, t - .4, tz + 5.2), (0, t + 1.0, tz + 7.2), (0, t + 3.0, tz + 8.2)])
    n = len(pts) - 1
    chain(s, 'tail', pts, [v(2.4, 2.2, 2.2) * (1 - .05 * i) for i in range(n)], ['fur'] * (n - 1) + ['dark'])
    # Snowdrops: a flower by the ear, then a ruff of drooping petals.
    def snowdrop(bone, root, length, droop):
        rx, ry, rz = root
        head = (rx + droop[0], ry + length, rz + droop[1])
        s.beam(bone, root, head, .3, 'stem', group='acc', name='snowdrop_stem')
        for k, (dx, dz) in enumerate(((-.45, 0), (.45, 0), (0, -.45))):
            c = (head[0] + dx, head[1] - .8, head[2] + dz)
            s.rbox(bone, c, (.7, 1.6, .7), 'petal', .2, group='acc', name='snowdrop_petal')
        s.mark(Mark(slab(1, -1e9, head[1] - 1.35), 'petal_tip', groups=('acc',), parts=('snowdrop_petal',)))
    if s.age >= 1:
        for side in ((1,) if s.age == 1 else (-1, 1)):
            nm = 'snowdrop_' + ('l' if side < 0 else 'r')
            root = (side * hw * .42, hy + hh * .3, f['hz'] + .6)
            s.bone(nm, 'head', root)
            snowdrop(nm, root, v(0, 2.2, 2.6), (side * .6, -.3))
    if s.age == 2:
        ny = hy - hh / 2 + .1
        for i in range(7):
            a = math.radians(-90 + i * 30)
            c = (math.sin(a) * W * .55, ny - .6, zf + 1.6 - math.cos(a) * 1.7)
            s.rbox('body', c, (1.3, 2.0, 1.0), 'petal', .3, (0, -math.degrees(a), 0), c, group='acc', name='ruff_petal')
        s.mark(Mark(slab(1, -1e9, ny - 1.2), 'petal_tip', groups=('acc',), parts=('ruff_petal',)))
    return s.finish()


# ----------------------------------------------------------------------- tapir

def _apricot_tapir(stage):
    s = Pet('apricot_tapir', stage, dict(fur='#F0A066', fur_top='#DE8A4E', cream='#FFF0DC', saddle='#FFE7C8',
                                         inner='#F7C9AE', nose='#6A3C2C', mouth='#6A3C2C', blush='#F29C8A',
                                         hoof='#7A4A34', spot='#FFF4E4'),
            kinds=dict(fur_top='fur', saddle='fur', spot='fur', hoof='smooth'))
    v = s.v
    s.scale = v(.8, .83, .86)
    b = quad_body(s, v(9.0, 12.0, 14.6), v(6.2, 6.8, 7.2), v(5.6, 6.2, 6.6), v(2.6, 3.6, 4.4), v(2.3, 2.5, 2.7),
                  paw='hoof', barrel=True, paw_len=v(2.4, 2.6, 2.9))
    s.rbox('body', (0, b['cy'] + .4, b['zb'] - b['L'] * .26), (b['W'] * .98, b['H'] * .96, b['L'] * .46), 'fur',
           min(b['W'], b['H']) * .3, name='rump')
    f = face(s, b, (v(6.4, 6.6, 6.8), v(5.8, 5.8, 6.0), v(6.0, 6.6, 7.2)), rise=v(.12, .14, .16), fwd=v(.3, .38, .42),
             eyes={'iris': '#2A1A16', 'iris_light': '#7A4E34', 'w': v(1.7, 1.55, 1.45), 'h': v(1.9, 1.75, 1.6),
                   'dy': .14},
             ears={'kind': 'round', 'w': v(2.4, 2.5, 2.6), 'h': v(2.2, 2.3, 2.4), 'd': 1.0, 'tilt': 20, 'x': .34,
                   'inner': 'inner'}, mouth=None)
    hy, hh, hw, front, hz = f['hy'], f['hh'], f['hw'], f['front'], f['hz']
    # The short trunk: a two-joint proboscis that curls down.
    root = (0, hy - hh * .12, front + .4)
    s.bone('snout', 'head', root)
    tl = v(1.6, 2.6, 3.4)
    tip = (0, root[1] - tl * .45, root[2] - tl)
    s.beam('snout', root, tip, v(2.6, 2.6, 2.6), 'fur', v(2.2, 2.2, 2.3), bevel=.45, group='snout', name='trunk')
    s.rbox('snout', tip, (2.3, 1.6, 1.4), 'fur', .4, group='snout', name='trunk_tip')
    s.mark(Mark(mirrored(ellipsoid((.5, tip[1] - .1, tip[2] - .7), (.26, .3, .6))), 'nose', groups=('snout',),
                facing=(0, 0, -1), min_dot=.3))
    s.mark(Mark(polyline([(-.9, hy - hh * .42, front - .1), (0, hy - hh * .47, front - .1), (.9, hy - hh * .42, front - .1)],
                         .15), 'mouth', groups=('head', 'snout'), facing=(0, 0, -1), min_dot=.4))
    s.mark(Mark(slab(1, -1e9, b['cy'] - b['H'] * .25), 'cream', groups=('body',), jitter=.4))
    s.mark(Mark(slab(1, hy - hh * .3), 'fur', groups=('head',), parts=('nothing',)))
    s.mark(Mark(mirrored(ellipsoid((hw * .5, hy + hh * .5, hz + 1.0), (1.4, .6, 1.4))), 'cream', groups=('ear',),
                mats=('fur',)))
    if s.age == 0:
        # Watermelon youngster: cream spots and dashes over the whole coat.
        s.mark(Dots(1.9, .55, 'spot', groups=('body', 'head', 'leg'), offset=(.3, .2, .1)))
        s.mark(Bands((0, .25, 1), -20, 2.4, .18, 'spot', wobble=.1, groups=('body',), facing=(1, 0, 0), min_dot=-2))
    elif s.age == 1:
        s.mark(Bands((0, .3, 1), -20, 2.2, .26, 'spot', wobble=.12, groups=('body',), jitter=.3))
        s.mark(Dots(2.2, .45, 'spot', groups=('leg', 'head'), offset=(.3, .2, .1)))
    else:
        # The grown saddle: a cream blanket over the back half.
        s.mark(Mark(both(slab(2, b['cz'] - b['L'] * .08), slab(1, b['cy'] - b['H'] * .3)), 'saddle', groups=('body',),
                    jitter=.5))
    s.mark(Mark(slab(1, -1e9, 1.0), 'hoof', groups=('paw',)))
    chain(s, 'tail', [(0, b['top'] - 1.2, b['zb'] - .4), (0, b['top'] - 2.2, b['zb'] + .8)], v(1.2, 1.3, 1.4), 'fur')
    return s.finish()


# ------------------------------------------------------------------------ boar

def _chestnut_boar(stage):
    s = Pet('chestnut_boar', stage, dict(fur='#9A5F3A', fur_top='#7E4A2C', mane='#5A3622', cream='#E9C7A0',
                                         stripe='#D9B184', inner='#E9A898', snout_pink='#E88A86', nose='#6A2E30',
                                         mouth='#4A2A22', blush='#E99A86', tusk='#FFF4DC', hoof='#3E2A22'),
            kinds=dict(fur_top='fur', mane='hair', stripe='fur', snout_pink='skin', tusk='smooth', hoof='smooth'))
    v = s.v
    s.scale = v(.8, .83, .86)
    b = quad_body(s, v(9.0, 11.8, 14.2), v(6.2, 6.8, 7.4), v(5.6, 6.2, 6.8), v(2.4, 3.2, 3.8), v(2.2, 2.4, 2.6),
                  paw='hoof', chest=1.12, rump=.9, paw_len=v(2.2, 2.4, 2.6))
    f = face(s, b, (v(7.0, 7.0, 7.2), v(5.8, 5.8, 6.0), v(5.8, 6.4, 7.0)), rise=v(.08, .1, .1), fwd=v(.25, .3, .32),
             snout={'size': (v(3.6, 3.8, 4.0), v(2.8, 3.0, 3.2), v(1.6, 2.4, 3.0)), 'mat': 'fur', 'dy': -.22},
             eyes={'iris': '#2A1612', 'iris_light': '#7A4630', 'w': v(1.7, 1.5, 1.4), 'h': v(1.9, 1.7, 1.55), 'dy': .1},
             ears={'w': v(2.6, 2.7, 2.8), 'h': v(2.6, 2.8, 3.0), 'tilt': v(30, 32, 34), 'lean': 8, 'x': .32},
             mouth=None)
    hy, hh, hw, front, sf, sy = f['hy'], f['hh'], f['hw'], f['front'], f['snout_front'], f['sy']
    # Pink snout disc with nostrils, a small smile under it.
    s.mark(Mark(_all, 'snout_pink', groups=('snout',), facing=(0, 0, -1), min_dot=.6))
    s.mark(Mark(mirrored(ellipsoid((.65, sy + .1, sf), (.32, .45, .8))), 'nose', groups=('snout',), facing=(0, 0, -1),
                min_dot=.6))
    s.mark(Mark(polyline([(-1.4, sy - f['sh'] * .5, sf + .6), (0, sy - f['sh'] * .62, sf + .6), (1.4, sy - f['sh'] * .5, sf + .6)],
                         .16), 'mouth', groups=('snout',), facing=(0, -.3, -1), min_dot=.2))
    s.mark(Mark(slab(1, -1e9, b['cy'] - b['H'] * .28), 'cream', groups=('body',), jitter=.45))
    s.mark(Mark(slab(1, b['cy'] + b['H'] * .3), 'fur_top', groups=('body',), jitter=.45))
    if s.age == 0:
        # Piglet stripes run nose to tail.
        s.mark(Bands((0, 1, 0), b['cy'] - b['H'], 1.15, .38, 'stripe', groups=('body',), facing=(1, 0, 0), min_dot=-2,
                     wobble=.04))
    # Bristle mane along the spine, thicker with age.
    n = v(2, 4, 6)
    for i in range(n):
        z = b['zf'] + 1.0 + i * v(1.6, 1.7, 1.7)
        h = v(1.2, 1.8, 2.4) * (1 - i / (n + 2))
        s.rbox('body', (0, b['top'] + h / 2 - .3, z), (v(1.8, 2.2, 2.6), h + .6, 2.0), 'mane', .35, (-14, 0, 0),
               name='mane')
    s.mark(Mark(slab(1, hy + hh * .2), 'mane', groups=('head',), facing=(0, 1, 0), min_dot=.5))
    if s.age >= 1:
        for side in (-1, 1):
            base = (side * f['sw'] * .45, sy - f['sh'] * .25, sf + v(0, 1.0, 1.3))
            mid = (side * (f['sw'] * .55 + .3), sy + v(0, .5, .9), sf + v(0, .4, .5))
            tip = (side * (f['sw'] * .5 + .2), sy + v(0, 1.2, 2.2), sf + v(0, .6, .9))
            s.beam('head', base, mid, v(0, .5, .7), 'tusk', group='acc', name='tusk')
            if s.age == 2:
                s.beam('head', mid, tip, .55, 'tusk', group='acc', name='tusk')
    chain(s, 'tail', [(0, b['top'] - 1.0, b['zb'] - .3), (0, b['top'] - 2.2, b['zb'] + .7)], .8, 'fur')
    s.rbox('tail', (0, b['top'] - 2.6, b['zb'] + .9), (1.1, 1.2, 1.1), 'mane', .25, group='tail', name='tail_tuft')
    return s.finish()


# --------------------------------------------------------------------- alpaca

def _cinnamon_alpaca(stage):
    s = Pet('cinnamon_alpaca', stage, dict(fur='#D89A63', wool='#E9B985', wool_light='#F4D2A6', face_fur='#F2D0A8',
                                           inner='#C27C4E', nose='#6A3A2C', mouth='#6A3A2C', blush='#F2A08A',
                                           hoof='#5A3A2C', sash='#D9485A', sash2='#3E9C9A', sash3='#FFF2D6'),
            kinds=dict(wool='fur', wool_light='fur', face_fur='fur', hoof='smooth', sash='cloth', sash2='cloth',
                       sash3='cloth'))
    v = s.v
    s.scale = v(.78, .83, .88)
    L, W, H, leg = v(7.0, 9.4, 11.0), v(5.6, 6.2, 6.8), v(5.2, 5.8, 6.4), v(3.6, 5.6, 7.4)
    cy, cz = leg + H * .4, .6
    s.rbox('body', (0, cy, cz), (W, H, L), 'wool', min(W, H) * .32, name='fleece')
    s.rbox('body', (0, cy + .3, cz + L * .3), (W * .9, H * .9, L * .4), 'wool', min(W, H) * .3, name='rump')
    s.bones['body'] = ('root', [0, cy, cz])
    zf, zb, top = cz - L / 2, cz + L / 2, cy + H / 2
    quad_legs(s, W * .28, zf + 1.6, zb - 1.6, cy - H * .1, cy - H * .1, v(1.7, 1.8, 1.8), mat='fur', paw='hoof',
              thigh=(v(2.4, 2.7, 3.0), v(2.6, 3.2, 3.8), v(2.6, 3.2, 3.8)))
    # Fleece leggings on the upper legs.
    for end, z in (('f', zf + 1.6), ('b', zb - 1.6)):
        for side in ('l', 'r'):
            x = (-1 if side == 'l' else 1) * W * .28
            s.rbox(f'leg_{end}{side}', (x, cy - H * .1 - v(.6, 1.0, 1.4), z), (2.5, v(1.4, 2.2, 3.0), 2.5), 'wool', .5,
                   group='leg', name='legging')
    # A tall fleecy neck.
    nh = v(3.0, 5.2, 7.6)
    nz = zf + 1.4
    s.bone('neck_root', 'body', (0, top - .5, nz))
    s.rbox('body', (0, top + nh / 2 - .8, nz), (v(3.4, 3.8, 4.0), nh + 1.6, v(3.4, 3.8, 4.0)), 'wool', .8, name='neck')
    hw, hh, hd = v(4.8, 4.8, 5.0), v(4.4, 4.4, 4.6), v(5.0, 5.4, 5.8)
    hy = top + nh + hh * .25
    hz = nz - v(.8, 1.0, 1.2)
    b = dict(top=top, zf=zf)
    f = face(s, b, (hw, hh, hd), pos=(hy, hz), mat='face_fur', pivot=(0, hy - hh * .5, hz + hd * .2),
             snout={'size': (v(3.0, 3.0, 3.2), v(2.2, 2.2, 2.3), v(1.0, 1.4, 1.8)), 'mat': 'face_fur', 'dy': -.28},
             nose={'size': (1.0, .55, .5), 'dy': .32},
             eyes={'iris': '#2A1A16', 'iris_light': '#7A5038', 'w': v(1.6, 1.5, 1.45), 'h': v(1.9, 1.75, 1.65),
                   'dx': .27, 'dy': .12},
             ears={'w': v(1.6, 1.7, 1.8), 'h': v(2.8, 3.4, 3.8), 'tilt': 18, 'lean': -4, 'x': .3, 'mat': 'fur',
                   'inner': 'inner'}, mouth='w')
    # A fluffy topknot over the brow.
    s.rbox('head', (0, hy + hh / 2 + .3, hz + .2), (hw * .92, v(1.8, 2.2, 2.6), hd * .7), 'wool', .6, name='topknot')
    s.mark(Dots(1.15, .32, 'wool_light', groups=('body', 'head', 'leg'), mats=('wool',)))
    s.mark(Mark(slab(1, -1e9, cy - H * .3), 'wool_light', groups=('body',), jitter=.4))
    if s.age >= 1:
        # A knitted sash across the back.
        s.rbox('body', (0, cy + .2, cz - .6), (W * 1.04, H * 1.02, 1.8), 'sash', .5, group='acc', name='sash')
        s.mark(Bands((0, 1, 0), cy - 4, .9, .34, 'sash3', groups=('acc',), parts=('sash',)))
        if s.age == 2:
            s.mark(Bands((0, 1, 0), cy - 4.45, .9, .2, 'sash2', groups=('acc',), parts=('sash',)))
            for side in (-1, 1):
                s.beam('body', (side * W * .52, cy - .8, cz - .6), (side * W * .56, cy - 2.6, cz - .2), .5, 'sash3',
                       group='acc', name='tassel')
    chain(s, 'tail', [(0, top - 1.0, zb - .4), (0, top - 1.6, zb + 1.0)], v(1.6, 1.8, 2.0), 'wool')
    return s.finish()


# ----------------------------------------------------------------------- fawn

def _clover_fawn(stage):
    s = Pet('clover_fawn', stage, dict(fur='#D99C5A', fur_top='#C7854A', cream='#FFF2DE', spot='#FFF4E2',
                                       inner='#F6C2B0', nose='#3A2A28', mouth='#5A3A30', blush='#F4A08E',
                                       hoof='#5A3E30', twig='#8A6A44', clover='#79C25E', clover_dark='#5AA448',
                                       leaf_vein='#4E8A3C'),
            kinds=dict(fur_top='fur', spot='fur', hoof='smooth', twig='wood', clover='leaf', clover_dark='leaf'))
    v = s.v
    s.scale = v(.78, .82, .86)
    b = quad_body(s, v(7.0, 9.4, 11.0), v(4.8, 5.2, 5.6), v(4.6, 5.0, 5.4), v(4.2, 6.2, 7.8), v(1.5, 1.55, 1.6),
                  paw='hoof', chest=1.05, rump=1.0, paw_len=v(1.7, 1.8, 1.9),
                  thigh=(v(2.2, 2.5, 2.8), v(2.8, 3.4, 4.0), v(2.8, 3.4, 4.0)))
    nh = v(.6, 1.6, 2.4)
    s.rbox('body', (0, b['top'] + nh / 2 - .2, b['zf'] + 1.4), (2.8, nh + 1.6, 3.0), 'fur', .6, name='neck')
    f = face(s, b, (v(6.0, 5.6, 5.4), v(5.2, 4.8, 4.8), v(5.2, 5.4, 5.6)), rise=v(.32, .42, .5) + nh / 5,
             fwd=v(0, .02, .04),
             snout={'size': (v(2.6, 2.6, 2.6), v(2.0, 2.0, 2.0), v(1.4, 2.0, 2.4)), 'mat': 'fur', 'dy': -.26},
             nose={'size': (1.2, .8, .7)},
             eyes={'iris': '#2A1A16', 'iris_light': '#8A5A38', 'w': v(2.1, 1.9, 1.75), 'h': v(2.4, 2.2, 2.0)},
             ears={'w': v(2.4, 2.3, 2.3), 'h': v(3.6, 3.6, 3.6), 'tilt': v(58, 55, 52), 'lean': -2, 'x': .32,
                   'sink': .9}, mouth='w')
    hy, hh, hw, front, hz = f['hy'], f['hh'], f['hw'], f['front'], f['hz']
    s.mark(Mark(slab(1, -1e9, f['sy'] - .2), 'cream', groups=('head', 'snout'), jitter=.2))
    s.mark(Mark(ellipsoid((0, b['top'] - .4, b['zf'] + .4), (2.2, 3.6, 2.6)), 'cream', groups=('body',), jitter=.3))
    s.mark(Mark(slab(1, -1e9, b['cy'] - b['H'] * .25), 'cream', groups=('body',), jitter=.4))
    s.mark(Mark(slab(1, b['cy'] + b['H'] * .32), 'fur_top', groups=('body',), jitter=.4))
    s.mark(Mark(slab(1, -1e9, .9), 'hoof', groups=('paw',)))
    # Clover spots: soft three-lobed dapples along the back.
    spots = []
    for row, (yy, xx) in enumerate(((.42, .25), (.18, .46))):
        for k in range(v(4, 4, 5)):
            z = b['zf'] + 1.4 + k * b['L'] / v(4.5, 4.5, 5.6) + row * .7
            for side in (-1, 1):
                for lobe in ((0, .35), (-.32, -.18), (.32, -.18)):
                    spots.append(ellipsoid((side * b['W'] * xx + lobe[0] * .6, b['cy'] + b['H'] * yy + lobe[1], z + lobe[0]),
                                           (v(.45, .42, .4),) * 3))
    s.mark(Mark(either(*spots), 'spot', groups=('body',)))
    chain(s, 'tail', [(0, b['top'] - .6, b['zb'] - .3), (0, b['top'] + .4, b['zb'] + .7)], v(1.4, 1.5, 1.6), 'fur')
    s.mark(Mark(_all, 'cream', groups=('tail',), facing=(0, 0, 1), min_dot=.2))
    # Leaf antlers: tiny sprouts, then twig antlers carrying clover leaves.
    if s.age >= 1:
        for side in (-1, 1):
            nm = 'antler_' + ('l' if side < 0 else 'r')
            root = (side * hw * .2, hy + hh / 2 - .2, hz + .3)
            s.bone(nm, 'head', root)
            a1 = (root[0] + side * v(0, .5, .9), root[1] + v(0, 1.4, 2.4), root[2] + .2)
            s.beam(nm, root, a1, v(0, .5, .55), 'twig', group='acc', name='antler')
            pts = [a1]
            if s.age == 2:
                a2 = (a1[0] + side * .9, a1[1] + 1.5, a1[2] + .4)
                br = (a1[0] - side * .5, a1[1] + 1.2, a1[2] - .3)
                s.beam(nm, a1, a2, .45, 'twig', group='acc', name='antler')
                s.beam(nm, a1, br, .4, 'twig', group='acc', name='antler')
                pts = [a2, br]
            for p in pts:
                for k, ang in enumerate((0, 120, 240)):
                    a = math.radians(ang)
                    c = (p[0] + math.sin(a) * .55, p[1] + math.cos(a) * .55 + .2, p[2])
                    s.rbox(nm, c, (.9, .9, .3), 'clover' if k else 'clover_dark', .2, (0, 0, ang), c, group='acc',
                           name='clover_leaf')
    else:
        _leaf(s, 'head', (0, hy + hh / 2, hz), (.9, hy + hh / 2 + 1.3, hz - .3), .9, 'clover')
    return s.finish()


# ---------------------------------------------------------------------- lemur

def _ribbon_lemur(stage):
    s = Pet('ribbon_lemur', stage, dict(fur='#A9A5B2', fur_top='#928E9C', cream='#F7F6F8', dark='#2A2730',
                                        ring='#2E2B34', inner='#2A2730', nose='#1E1C22', mouth='#3E3A44',
                                        blush='#E8A2B4', ribbon='#C79BE8', ribbon_knot='#EBD3FF'),
            kinds=dict(fur_top='fur', ring='fur', ribbon='cloth', ribbon_knot='cloth'))
    v = s.v
    s.scale = v(.78, .82, .86)
    b = quad_body(s, v(7.2, 9.4, 11.2), v(5.0, 5.4, 5.8), v(4.8, 5.1, 5.4), v(3.0, 4.2, 5.2), v(1.8, 1.9, 2.0),
                  paw='dark', thigh=(v(2.4, 2.7, 3.0), v(3.0, 3.6, 4.0), v(3.0, 3.6, 4.0)))
    f = face(s, b, (v(6.8, 6.6, 6.6), v(5.8, 5.6, 5.6), v(5.4, 5.4, 5.6)), rise=v(.3, .38, .44), fwd=v(.05, .12, .16),
             snout={'size': (v(2.6, 2.5, 2.5), v(1.9, 1.9, 1.9), v(1.4, 2.2, 2.8)), 'mat': 'dark', 'dy': -.25},
             nose={'size': (1.1, .75, .6)},
             eyes={'iris': '#C2560E', 'iris_light': '#FFC23A', 'w': v(2.3, 2.2, 2.1), 'h': v(2.5, 2.35, 2.2),
                   'outline': '#141218'},
             ears={'w': v(2.6, 2.7, 2.8), 'h': v(2.6, 2.8, 3.0), 'tilt': v(36, 34, 32), 'lean': -2, 'x': .34, 'mat': 'cream',
                   'inner': 'inner'}, mouth=None, blush=False,
             cheeks={'size': (v(1.4, 1.5, 1.6), v(2.2, 2.4, 2.6), v(3.0, 3.2, 3.4)), 'mat': 'cream', 'dy': .2, 'tilt': -18})
    hy, hh, hw, front, ex, ey = f['hy'], f['hh'], f['hw'], f['front'], f['ex'], f['ey']
    # White face with dark triangular eye patches running into the dark muzzle.
    s.mark(Mark(_all, 'cream', groups=('head',), mats=('fur',)))
    s.mark(Mark(mirrored(polyline([(ex + .2, ey + .4, front), (ex * .45, f['sy'], front)], v(1.35, 1.3, 1.25))), 'dark',
                groups=('head',), facing=(0, 0, -1), min_dot=.2))
    s.mark(Mark(slab(1, hy + hh * .3), 'fur', groups=('head',), jitter=.3))
    s.mark(Mark(slab(1, -1e9, b['cy'] - b['H'] * .18), 'cream', groups=('body',), jitter=.4))
    s.mark(Mark(slab(1, b['cy'] + b['H'] * .34), 'fur_top', groups=('body',), jitter=.4))
    s.mark(Mark(slab(1, -1e9, v(1.2, 1.5, 1.8)), 'dark', groups=('leg', 'paw'), jitter=.2))
    # The banner tail: tall, curling at the top, ringed black and white.
    t, tz = b['top'] - .5, b['zb'] - .3
    pts = v([(0, t, tz), (0, t + 2.0, tz + 1.4), (0, t + 4.0, tz + 1.4), (0, t + 5.4, tz + .2)],
            [(0, t, tz), (0, t + 2.4, tz + 1.6), (0, t + 5.2, tz + 2.0), (0, t + 7.8, tz + 1.6), (0, t + 9.4, tz + .2),
             (0, t + 9.6, tz - 1.4)],
            [(0, t, tz), (0, t + 2.8, tz + 1.8), (0, t + 6.0, tz + 2.4), (0, t + 9.2, tz + 2.2), (0, t + 11.8, tz + 1.0),
             (0, t + 12.8, tz - .8), (0, t + 12.0, tz - 2.4)])
    n = len(pts) - 1
    chain(s, 'tail', pts, [v(2.0, 2.0, 2.1) * (1 - .04 * i) for i in range(n)], ['cream', 'ring'] * 4)
    if s.age >= 1:
        # A ribbon bow tied around the tail, ribbon-like tufts at the cheeks.
        p = pts[2]
        _bow(s, 'tail_1', (p[0], p[1] - .3, p[2] + .2), (v(0, 2.6, 3.0), v(0, 1.5, 1.7)), 'ribbon', 'ribbon_knot',
             group='acc')
    return s.finish()


# -------------------------------------------------------------------- pangolin

def _pebble_pangolin(stage):
    s = Pet('pebble_pangolin', stage, dict(fur='#E2C4AE', scale='#A39886', scale_light='#C2B8A6', scale_dark='#857A6A',
                                           cream='#F2DCC8', inner='#E2A898', nose='#5A3E36', mouth='#5A3E36',
                                           blush='#F0A090', claw='#4E443C', moss='#7BAA5A'),
            kinds=dict(scale='shell', scale_light='shell', scale_dark='shell', moss='leaf'))
    v = s.v
    s.scale = v(.72, .76, .8)
    L, W, H, leg = v(8.0, 10.4, 12.4), v(5.0, 5.4, 5.8), v(4.6, 5.0, 5.4), v(2.0, 2.5, 3.0)
    cy, cz = leg + H * .42, .4
    s.rbox('body', (0, cy, cz), (W, H, L), 'fur', 1.2, name='body')
    s.bones['body'] = ('root', [0, cy, cz])
    zf, zb, top = cz - L / 2, cz + L / 2, cy + H / 2
    quad_legs(s, W * .32, zf + 1.6, zb - 1.7, cy - H * .1, cy - H * .1, v(1.8, 1.9, 2.0), paw='fur',
              paw_len=v(2.2, 2.4, 2.6))
    # Curved claws on the front feet.
    for side in (-1, 1):
        x = side * W * .32
        for k in (-1, 0, 1):
            s.beam('leg_f' + ('l' if side < 0 else 'r') + '_lower', (x + k * .45, .5, zf + 1.6 - 1.0),
                   (x + k * .45, .2, zf + 1.6 - v(1.6, 1.9, 2.2)), .32, 'claw', group='paw', name='claw')
    # Overlapping pebble scales, front to back, each lifting at its rear edge.
    rows = v(4, 5, 6)
    for i in range(rows):
        z = zf + 1.0 + i * (L - 1.4) / rows
        mat = ('scale', 'scale_light', 'scale_dark')[i % 3] if s.age else 'scale_light'
        s.rbox('body', (0, top + .1, z + .9), (W + .6, 1.4, 2.6), mat, .45, (-12, 0, 0), name='scale_row')
        for side in (-1, 1):
            s.rbox('body', (side * (W / 2 + .1), cy + H * .12, z + .9), (1.2, H * .7, 2.5), mat, .4, (-8, 0, side * 8),
                   name='scale_side')
    f = face(s, dict(top=top, zf=zf), (v(4.6, 4.4, 4.4), v(4.2, 4.0, 4.0), v(4.4, 4.6, 4.8)), rise=v(-.2, -.24, -.26),
             fwd=v(.2, .26, .3),
             snout={'size': (v(2.4, 2.4, 2.4), v(1.9, 1.9, 1.9), v(1.6, 2.8, 3.6)), 'mat': 'fur', 'dy': -.18},
             nose={'size': (1.0, .7, .5)},
             eyes={'iris': '#1E1414', 'iris_light': '#5A3A2E', 'w': v(1.5, 1.35, 1.25), 'h': v(1.7, 1.5, 1.4),
                   'dx': .26, 'dy': .12}, mouth='smile', ears=None)
    s.rbox('head', (0, f['hy'] + f['hh'] / 2 - .1, f['hz'] + .5), (f['hw'] + .4, 1.2, f['hd'] * .7), 'scale_light', .4,
           (-10, 0, 0), name='scale_cap')
    s.mark(Mark(slab(1, -1e9, cy - H * .2), 'cream', groups=('body',), mats=('fur',), jitter=.3))
    # A scaled tail, thick at the base.
    t, tz = top - 1.2, zb - .4
    pts = v([(0, t, tz), (0, t - 1.2, tz + 2.4), (0, t - 2.2, tz + 4.0)],
            [(0, t, tz), (0, t - 1.4, tz + 2.8), (0, t - 2.6, tz + 5.4), (0, t - 3.2, tz + 7.4)],
            [(0, t, tz), (0, t - 1.4, tz + 3.0), (0, t - 2.8, tz + 5.8), (0, t - 3.4, tz + 8.4), (0, t - 2.8, tz + 10.6)])
    n = len(pts) - 1
    names = chain(s, 'tail', pts, [v(3.6, 3.8, 4.0) * (1 - .14 * i) for i in range(n)], 'scale')
    for i, nm in enumerate(names):
        a, c = np.array(pts[i]), np.array(pts[i + 1])
        m = (a + c) / 2
        w = v(3.6, 3.8, 4.0) * (1 - .14 * i)
        s.rbox(nm, (m[0], m[1] + w * .45, m[2] + .4), (w + .5, 1.0, 2.2), ('scale_light', 'scale')[i % 2], .35,
               (-14, 0, 0), group='tail', name='tail_scale')
    if s.age == 2:
        s.rbox('body', (W * .2, top + 1.0, cz - 1.0), (1.8, .8, 1.6), 'moss', .3, group='acc', name='moss')
    return s.finish()
