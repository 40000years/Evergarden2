"""Pet Atelier: the seventeen rare companions."""
from __future__ import annotations

import math

import numpy as np

from pet_atelier import (rotation, Pet, Mark, Bands, Eye, Inset, Toward, Dots, quad_legs, chain, ellipsoid, slab, boxed,
                         both, either, minus, mirrored, polyline)
from pet_atelier_parts import *  # noqa: F401,F403
from pet_atelier_parts import (_torso, _head, _ear, _nose, _smile, _blush, _bow, _pumpkin, _leaf, _all, _feather_fan,
                               heart)

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
