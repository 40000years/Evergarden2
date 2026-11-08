"""Pet Atelier: small upright companions that sit or stand and hold things (hamster, otter, squirrel...)."""
from __future__ import annotations

import math

import numpy as np

from pet_atelier import (rotation, Pet, Mark, Bands, Eye, Inset, Toward, Dots, chain, ellipsoid, slab, boxed, both,
                         either, minus, mirrored, polyline)
from pet_atelier_parts import *  # noqa: F401,F403
from pet_atelier_parts import _ear, _nose, _smile, _blush, _bow, _leaf, _all


def sitter(s, W, H, D, *, mat='fur', lower=1.0, upper=.82, chest_h=.75, lean=0.0, foot=(2.2, 1.1, 3.0), foot_mat=None,
           haunch=None, arm=1.5, arm_len=3.4, hands=(1.2, -1.0), hand_mat=None, cz=0.0, leg_lower=False, bevel=None,
           foot_out=.28, arm_out=.36, standing=0.0):
    """Upright body: a wide belly block, a narrower chest, haunches, forward feet and arms reaching to the front.

    standing lifts the body onto straight legs of that length (meerkats, jerboas)."""
    base = .5 + standing
    by = base + H * .35
    s.bones['body'] = ('root', [0, by, cz])
    b0 = bevel if bevel is not None else min(W * lower, H * .72, D * lower) * .36
    s.rbox('body', (0, by, cz), (W * lower, H * .72, D * lower), mat, b0, name='belly')
    uy = by + H * .42
    if chest_h:
        s.rbox('body', (0, uy, cz - lean), (W * upper, H * chest_h * .72, D * upper * .92), mat,
               min(W * upper, H * chest_h * .72, D * upper * .92) * .34, (lean * -4, 0, 0), name='chest')
        top = uy + H * chest_h * .36
    else:
        uy = by + H * .2
        top = by + H * .36
    hx = W * foot_out
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        hip = (side * hx, base + H * .15, cz + D * .1)
        s.bone('leg_' + sfx, 'body', hip)
        if haunch:
            hw_, hh_, hd_ = haunch
            s.rbox('leg_' + sfx, (side * (W * lower / 2 - hw_ * .3), base + hh_ / 2 + standing * .2, cz + D * .12),
                   (hw_, hh_, hd_), mat, min(hw_, hh_) * .32, group='leg', name='haunch')
        fw, fh, fl = foot
        fb = 'leg_' + sfx
        if standing:
            s.beam('leg_' + sfx, (side * hx, base + H * .2, cz), (side * hx, fh, cz - .2), fw * .8, mat, bevel=fw * .15,
                   group='leg', name='shin')
        if leg_lower:
            fb = s.bone('leg_' + sfx + '_lower', 'leg_' + sfx, (side * hx, fh, cz))
        s.rbox(fb, (side * hx, fh / 2, cz - fl * .35), (fw, fh, fl), foot_mat or mat, min(fw, fh) * .35, group='paw',
               name='foot')
    shoulder_y = top - H * .14
    hand_points = []
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        sh = (side * W * upper * arm_out, shoulder_y, cz - D * upper * .2)
        hand = (side * hands[0], shoulder_y - arm_len * .62, cz + hands[1] - D * upper * .45)
        s.bone('arm_' + sfx, 'body', sh)
        s.beam('arm_' + sfx, sh, hand, arm, mat, bevel=arm * .2, group='leg', name='arm')
        s.rbox('arm_' + sfx, hand, (arm * 1.12, arm * .95, arm * 1.1), hand_mat or mat, arm * .3, group='paw', name='hand')
        hand_points.append(hand)
    return dict(by=by, uy=uy, top=top, zf=cz - D / 2, zb=cz + D / 2, W=W, H=H, D=D, cz=cz, hands=hand_points,
                shoulder=shoulder_y)


def _berry(s, bone, c, r, mat, seeds=None, calyx='leaf', group='acc'):
    s.rbox(bone, c, (r * 2, r * 2.1, r * 2), mat, r * .6, group=group, name='berry')
    if seeds:
        s.mark(Dots(r * .62, r * .13, seeds, groups=(group,), mats=(mat,), offset=c))
    for k in range(5):
        a = math.radians(k * 72)
        s.beam(bone, (c[0], c[1] + r * .95, c[2]), (c[0] + math.cos(a) * r * .9, c[1] + r * 1.1, c[2] + math.sin(a) * r * .9),
               r * .45, calyx, .2, group=group, name='calyx')


# --------------------------------------------------------------------- hamster

def _strawberry_hamster(stage):
    s = Pet('strawberry_hamster', stage, dict(fur='#F2D3A6', fur_top='#E7B57C', cream='#FFF7EA', inner='#F6B3BF',
                                              nose='#E88A9C', mouth='#6A4A40', blush='#F6A3AE', pad='#F7BAC4',
                                              berry='#E5374C', berry_dark='#BF2337', seed='#FFE07A', leaf='#68B25A',
                                              leaf_vein='#4A8E40', knit='#8FC7E8', knit_dark='#6AA8D0', sunseed='#5A4A3E'),
            kinds=dict(fur_top='fur', berry='smooth', berry_dark='smooth', seed='plain', knit='cloth', knit_dark='cloth',
                       sunseed='smooth'))
    v = s.v
    s.scale = v(.74, .78, .82)
    W, H, D = v(6.4, 7.2, 7.8), v(6.2, 7.2, 8.0), v(6.0, 6.8, 7.4)
    b = sitter(s, W, H, D, foot=(v(1.8, 2.0, 2.2), 1.0, v(2.4, 2.7, 3.0)), foot_mat='pad', hand_mat='pad',
               haunch=(v(2.4, 2.8, 3.1), v(2.6, 3.0, 3.4), v(3.2, 3.6, 4.0)), arm=v(1.3, 1.4, 1.5), arm_len=v(2.0, 2.4, 2.8),
               hands=(.8, -.6), chest_h=0)
    hw, hh, hd = v(7.6, 8.2, 8.8), v(6.4, 6.8, 7.2), v(6.0, 6.4, 6.8)
    hy = b['top'] + hh * .22
    f = face(s, b, (hw, hh, hd), pos=(hy, b['cz'] - .4), pivot=(0, hy - hh * .45, b['cz']),
             snout={'size': (v(2.6, 2.8, 3.0), v(1.6, 1.7, 1.8), v(.5, .7, .8)), 'mat': 'cream', 'dy': -.2},
             nose={'size': (.9, .6, .5)}, mouth='cat',
             eyes={'iris': '#1E1416', 'iris_light': '#5A3A34', 'w': v(1.9, 1.8, 1.7), 'h': v(2.1, 2.0, 1.9), 'dy': .08,
                   'dx': .25},
             ears={'kind': 'round', 'w': v(2.0, 2.2, 2.4), 'h': v(1.8, 2.0, 2.2), 'd': .9, 'tilt': 18, 'x': .3, 'sink': .4},
             cheeks={'size': (v(2.2, 2.4, 2.6), v(2.8, 3.0, 3.2), v(3.6, 3.8, 4.0)), 'mat': 'cream', 'dy': .24, 'out': -.2})
    s.mark(Mark(slab(1, -1e9, f['ey'] - .7), 'cream', groups=('head',), jitter=.25))
    s.mark(Mark(ellipsoid((0, b['by'], b['zf']), (W * .38, H * .55, 2.4)), 'cream', groups=('body',), jitter=.3))
    s.mark(Mark(slab(1, b['top'] - .4), 'fur_top', groups=('body',), jitter=.3))
    hands = b['hands']
    hold = (0, hands[0][1] + .2, hands[0][2] - .7)
    hz = f['hz']
    if s.age == 0:
        # Clutching one sunflower seed, a sprout on the head.
        s.rbox('arm_r', hold, (.9, 1.6, .7), 'sunseed', .3, (0, 0, 15), group='acc', name='seed')
        s.mark(Bands((1, 0, 0), -3, .45, .3, 'cream', groups=('acc',), mats=('sunseed',)))
        _leaf(s, 'head', (0, hy + hh / 2, hz), (1.0, hy + hh / 2 + 1.4, hz - .3), 1.0)
    else:
        _berry(s, 'arm_r', hold, v(0, .9, 1.1), 'berry', 'seed')
    if s.age >= 1:
        # The strawberry hood: a red cap with seeds and a leafy calyx, plus a cape on the adult.
        cap_h = v(0, 2.4, 3.2)
        s.rbox('head', (0, hy + hh / 2 - cap_h * .25, hz + .6), (hw + .6, cap_h, hd * .9), 'berry', .8, group='acc',
               name='hood')
        s.rbox('head', (0, hy + hh * .1, hz + hd * .38), (hw + .4, hh * .7, 1.4), 'berry', .5, group='acc', name='hood_back')
        s.mark(Dots(1.1, .2, 'seed', groups=('acc',), parts=('hood',), offset=(.2, .1, .3)))
        top_y = hy + hh / 2 + cap_h * .25
        for k in range(v(0, 4, 6)):
            a = math.radians(k * 360 / v(1, 4, 6) + 20)
            s.beam('head', (0, top_y + .1, hz + .4), (math.cos(a) * 1.9, top_y + .4, hz + .4 + math.sin(a) * 1.9), 1.1,
                   'leaf', .3, group='acc', name='calyx')
        s.cube('head', (0, top_y + .7, hz + .4), (.4, 1.0, .4), 'leaf_vein', group='acc', name='stem')
        # Ear holes: the ears poke through the hood.
    if s.age == 2:
        # A knitted seed satchel on a strap.
        s.rbox('body', (b['W'] / 2 + .4, b['by'] - .4, b['cz'] + .6), (1.4, 2.6, 2.8), 'knit', .4, group='acc', name='satchel')
        s.mark(Bands((0, 1, 0), -10, .55, .3, 'knit_dark', groups=('acc',), mats=('knit',)))
        s.beam('body', (b['W'] / 2 + .3, b['by'] + 1.0, b['cz'] + .6), (-b['W'] * .3, b['top'] - .2, b['zf'] + .4), .6,
               'knit_dark', .3, group='acc', name='strap')
    return s.finish()


# ------------------------------------------------------------------------ otter

def _honey_otter(stage):
    s = Pet('honey_otter', stage, dict(fur='#8E5E3E', fur_top='#7A4C30', cream='#F4DEC2', inner='#6A4430',
                                       nose='#2A1C1C', mouth='#4A2E2A', blush='#EE9C8A', honey='#F5B532',
                                       honey_dark='#D98A1A', comb='#FFD25A', scarf='#FFC43A', scarf_drip='#E08A12'),
            kinds=dict(fur_top='fur', honey='glow', honey_dark='smooth', comb='smooth', scarf='cloth', scarf_drip='cloth'))
    v = s.v
    s.scale = v(.74, .78, .82)
    W, H, D = v(5.2, 5.8, 6.2), v(6.6, 8.4, 9.8), v(4.8, 5.2, 5.6)
    b = sitter(s, W, H, D, foot=(v(1.8, 2.0, 2.1), 1.0, v(2.6, 3.0, 3.2)), lower=1.0, upper=.86, chest_h=.85,
               arm=v(1.2, 1.3, 1.4), arm_len=v(2.6, 3.2, 3.6), hands=(.7, -.4), standing=v(.6, 1.0, 1.4))
    hw, hh, hd = v(6.0, 5.8, 5.8), v(5.2, 5.0, 5.0), v(5.0, 5.0, 5.2)
    hy = b['top'] + hh * .38
    f = face(s, b, (hw, hh, hd), pos=(hy, b['cz'] - .3), pivot=(0, hy - hh * .45, b['cz']),
             snout={'size': (v(3.4, 3.4, 3.6), v(2.0, 2.0, 2.1), v(.9, 1.2, 1.4)), 'mat': 'cream', 'dy': -.24},
             nose={'size': (v(1.5, 1.5, 1.6), v(1.0, 1.0, 1.05), .6), 'dy': .35}, mouth='w',
             eyes={'iris': '#1A1212', 'iris_light': '#4A3028', 'w': v(1.8, 1.65, 1.5), 'h': v(2.0, 1.85, 1.7), 'dy': .1},
             ears={'kind': 'round', 'w': v(1.6, 1.6, 1.7), 'h': v(1.3, 1.3, 1.4), 'd': .8, 'tilt': 30, 'x': .38, 'sink': .2})
    for side in (-1, 1):
        for dy in (.1, -.35):
            s.mark(decal(Mark(ellipsoid((side * 1.1, f['sy'] + dy, f['snout_front']), (.15, .15, .7)), 'fur',
                              groups=('snout',), facing=(0, 0, -1), min_dot=.5)))
    s.mark(Mark(slab(1, -1e9, f['ey'] - .5), 'cream', groups=('head',), jitter=.2))
    s.mark(Mark(ellipsoid((0, b['uy'], b['zf']), (W * .4, H * .6, 2.4)), 'cream', groups=('body',), jitter=.35))
    # A long tapering rudder tail resting behind.
    t = b['by'] - .2
    tz = b['zb'] - .5
    pts = [(0, t, tz), (0, max(.9, t - 2.4), tz + v(2.2, 2.8, 3.2)), (0, .8, tz + v(4.2, 5.6, 6.6)),
           (0, .6, tz + v(6.0, 8.0, 9.6))]
    chain(s, 'tail', pts, [v(2.4, 2.6, 2.8), v(2.0, 2.2, 2.4), v(1.4, 1.6, 1.7)], 'fur', depth=[v(2.2, 2.4, 2.6), v(1.6, 1.8, 2.0),
                                                                                              v(1.0, 1.1, 1.2)])
    hold = (0, b['hands'][0][1] + .3, b['hands'][0][2] - .8)
    if s.age == 0:
        s.rbox('arm_r', hold, (1.6, 2.0, 1.4), 'honey', .55, group='acc', name='honey_drop')
        s.rbox('arm_r', (hold[0], hold[1] + 1.2, hold[2]), (.8, .9, .7), 'honey', .3, group='acc', name='honey_drop')
    else:
        # A hexagon honeycomb: two tiers, then three on the adult.
        tiers = v(0, 2, 3)
        for i in range(tiers):
            w = v(0, 2.8, 3.4) - i * .6
            s.rbox('arm_r', (hold[0], hold[1] + i * 1.3 - .4, hold[2]), (w, 1.4, 1.6), 'comb', .3, group='acc', name='comb')
        s.mark(Dots(.62, .2, 'honey_dark', groups=('acc',), parts=('comb',), facing=(0, 0, -1), min_dot=.5))
        s.rbox('arm_r', (hold[0] + .6, hold[1] - 1.4, hold[2] - .2), (.6, 1.2, .6), 'honey', .25, group='acc', name='drip')
    if s.age >= 1:
        ny = hy - hh / 2 + .1
        s.rbox('body', (0, ny, b['cz']), (W * .9, 1.1, D * .9), 'scarf', .3, group='acc', name='scarf')
        s.rbox('body', (W * .3, ny - 1.2, b['zf'] - .1), (1.4, v(0, 2.0, 2.8), .6), 'scarf', .25, (0, 0, 8), group='acc',
               name='scarf_tail')
        s.mark(Mark(slab(1, -1e9, ny - .2), 'scarf_drip', groups=('acc',), parts=('scarf_tail',)))
        if s.age == 2:
            s.mark(Bands((1, 0, 0), -10, 1.2, .25, 'scarf_drip', groups=('acc',), parts=('scarf_',), facing=(0, -1, 0),
                         min_dot=-2))
    return s.finish()


# --------------------------------------------------------------------- squirrel

def _acorn_squirrel(stage):
    s = Pet('acorn_squirrel', stage, dict(fur='#C9773F', fur_top='#B5622E', cream='#FFF1DC', stripe='#F5D9B4',
                                          inner='#F2B6A2', nose='#4A2A26', mouth='#4A2A26', blush='#F49A86',
                                          acorn='#B98546', acorn_light='#D9A563', cap='#7A5634', cap_light='#9A7048',
                                          stem='#5A3E28', knit='#D96A4A', knit_light='#F2A27E'),
            kinds=dict(fur_top='fur', stripe='fur', acorn='smooth', acorn_light='smooth', cap='shell', cap_light='shell',
                       stem='wood', knit='cloth', knit_light='cloth'))
    v = s.v
    s.scale = v(.74, .78, .82)
    W, H, D = v(5.2, 5.8, 6.2), v(5.4, 6.6, 7.6), v(5.0, 5.4, 5.8)
    b = sitter(s, W, H, D, foot=(v(1.6, 1.8, 1.9), 1.0, v(2.6, 3.0, 3.4)), chest_h=.75,
               haunch=(v(2.2, 2.6, 2.9), v(2.6, 3.2, 3.6), v(3.2, 3.8, 4.2)), arm=v(1.1, 1.2, 1.3),
               arm_len=v(2.4, 2.8, 3.2), hands=(.8, -.6))
    hw, hh, hd = v(6.4, 6.4, 6.6), v(5.6, 5.4, 5.4), v(5.4, 5.6, 5.8)
    hy = b['top'] + hh * .36
    f = face(s, b, (hw, hh, hd), pos=(hy, b['cz'] - .4), pivot=(0, hy - hh * .45, b['cz']),
             snout={'size': (v(2.6, 2.6, 2.7), v(1.8, 1.8, 1.9), v(.9, 1.3, 1.6)), 'mat': 'cream', 'dy': -.24},
             nose={'size': (1.0, .7, .55)}, mouth='w',
             eyes={'iris': '#1E1210', 'iris_light': '#5A3424', 'w': v(2.0, 1.85, 1.7), 'h': v(2.3, 2.1, 1.95)},
             ears={'w': v(2.2, 2.3, 2.4), 'h': v(2.4, 2.8, 3.2), 'tilt': 10, 'lean': -6, 'x': .3})
    # Ear tufts.
    for side in (-1, 1):
        nm = 'ear_' + ('l' if side < 0 else 'r')
        tip = (side * hw * .3 + side * .2, hy + hh / 2 + v(1.9, 2.3, 2.7), f['hz'] + .5)
        s.rbox(nm, tip, (.8, v(1.0, 1.2, 1.4), .8), 'fur_top', .25, group='ear', name='ear_tuft')
    s.mark(Mark(slab(1, -1e9, f['ey'] - .6), 'cream', groups=('head',), jitter=.2))
    s.mark(Mark(mirrored(ellipsoid((f['ex'], f['ey'], f['front']), (1.4, 1.5, 1.2))), 'stripe', groups=('head',),
                facing=(0, 0, -1), min_dot=.3))
    s.mark(Mark(ellipsoid((0, b['by'] + .5, b['zf']), (W * .36, H * .55, 2.4)), 'cream', groups=('body',), jitter=.3))
    s.mark(Mark(both(slab(0, -.55, .55), slab(1, b['by'] - 1)), 'stripe', groups=('body',), facing=(0, .2, 1), min_dot=.2))
    # The plume: an S-curve rising behind the back, taller than the squirrel by adulthood.
    tz, t = b['zb'] - .4, b['by'] - .6
    pts = v([(0, t, tz), (0, t + 1.6, tz + 1.8), (0, t + 4.2, tz + 2.2), (0, t + 6.0, tz + 1.2)],
            [(0, t, tz), (0, t + 1.8, tz + 2.2), (0, t + 4.8, tz + 2.8), (0, t + 7.6, tz + 2.4), (0, t + 9.2, tz + .8)],
            [(0, t, tz), (0, t + 2.0, tz + 2.6), (0, t + 5.4, tz + 3.4), (0, t + 8.8, tz + 3.2), (0, t + 11.2, tz + 1.8),
             (0, t + 12.0, tz - .4)])
    n = len(pts) - 1
    chain(s, 'tail', pts, [v(3.4, 4.2, 4.8) * (1 - .04 * abs(i - 1)) for i in range(n)], 'fur',
          depth=[v(3.0, 3.6, 4.2)] * n)
    s.mark(Toward((0, t + 6, tz - 2), 'fur_top', groups=('tail',)))
    s.mark(Mark(_all, 'stripe', groups=('tail',), facing=(0, 0, 1), min_dot=.6))
    # The acorn: a seed for the kit, a whole acorn after, and the adult wears a knitted acorn-cap hat.
    hold = (0, b['hands'][0][1] + .3, b['hands'][0][2] - .9)
    r = v(.7, 1.1, 1.3)
    s.rbox('arm_r', hold, (r * 2, r * 2.4, r * 2), 'acorn', r * .55, group='acc', name='acorn')
    s.mark(Mark(slab(1, hold[1] + r * .4), 'acorn_light', groups=('acc',), parts=('acorn',)))
    s.rbox('arm_r', (hold[0], hold[1] + r * 1.15, hold[2]), (r * 2.3, r * 1.0, r * 2.3), 'cap', r * .35, group='acc',
           name='acorn_cap')
    s.mark(Dots(.5, .16, 'cap_light', groups=('acc',), parts=('acorn_cap', 'hat')))
    s.cube('arm_r', (hold[0], hold[1] + r * 1.85, hold[2]), (.35, .7, .35), 'stem', group='acc', name='acorn_stem')
    if s.age == 2:
        top_y = hy + hh / 2
        s.rbox('head', (0, top_y + .5, f['hz'] + .2), (hw * .7, 1.6, hd * .7), 'knit', .5, group='acc', name='hat')
        s.mark(Bands((0, 1, 0), top_y - 2, .55, .35, 'knit_light', groups=('acc',), parts=('hat',)))
        s.rbox('head', (0, top_y + 1.7, f['hz'] + .2), (1.0, 1.0, 1.0), 'knit_light', .3, group='acc', name='pompom')
    return s.finish()


# ---------------------------------------------------------------------- meerkat

def _dandelion_meerkat(stage):
    s = Pet('dandelion_meerkat', stage, dict(fur='#D9B98A', fur_top='#C49F6C', cream='#F6E8CF', dark='#4A3A30',
                                             inner='#4A3A30', nose='#2A1E1C', mouth='#4A3A30', blush='#F2A890',
                                             stem='#6FA556', puff='#FFFFFF', puff_core='#F2E6B8', bud='#F7D23A',
                                             leaf='#7BB85E', leaf_vein='#5A9446', scarf='#8CC66C'),
            kinds=dict(fur_top='fur', stem='leaf', puff='glow', puff_core='plain', bud='smooth', scarf='leaf'))
    v = s.v
    s.scale = v(.74, .78, .82)
    W, H, D = v(5.0, 5.0, 5.2), v(6.0, 7.6, 9.0), v(4.4, 4.4, 4.6)
    b = sitter(s, W, H, D, foot=(v(1.6, 1.6, 1.7), .9, v(2.4, 2.6, 2.8)), lower=v(1.1, 1.0, .96), upper=.82, chest_h=.95,
               arm=v(1.0, 1.0, 1.1), arm_len=v(2.6, 3.0, 3.4), hands=(.8, -.2), standing=v(.8, 1.8, 2.8))
    hw, hh, hd = v(5.6, 5.2, 5.2), v(4.8, 4.4, 4.4), v(4.8, 5.0, 5.2)
    hy = b['top'] + hh * .38
    f = face(s, b, (hw, hh, hd), pos=(hy, b['cz'] - .3), pivot=(0, hy - hh * .45, b['cz']),
             snout={'size': (v(2.2, 2.2, 2.2), v(1.7, 1.7, 1.7), v(1.4, 1.8, 2.2)), 'mat': 'cream', 'dy': -.24},
             nose={'size': (1.0, .7, .55)}, mouth='w',
             eyes={'iris': '#1A1210', 'iris_light': '#5A3A2A', 'w': v(1.8, 1.6, 1.5), 'h': v(2.0, 1.8, 1.65)},
             ears={'kind': 'round', 'w': v(1.4, 1.4, 1.5), 'h': v(1.2, 1.2, 1.3), 'd': .8, 'tilt': 60, 'x': .46,
                   'sink': 1.6, 'mat': 'dark'})
    # Dark spectacle patches around the eyes.
    s.mark(Mark(mirrored(ellipsoid((f['ex'], f['ey'] - .1, f['front']), (1.25, 1.35, 1.4))), 'dark', groups=('head',),
                facing=(0, 0, -1), min_dot=.2))
    s.mark(Mark(slab(1, -1e9, f['ey'] - 1.1), 'cream', groups=('head',), jitter=.2))
    s.mark(Mark(ellipsoid((0, b['uy'], b['zf']), (W * .36, H * .65, 2.2)), 'cream', groups=('body',), jitter=.3))
    s.mark(Bands((0, 0, 1), b['cz'] - 1, 1.0, .35, 'fur_top', groups=('body',), facing=(0, .3, 1), min_dot=.3, wobble=.1))
    # A thin tail used as a prop, dark-tipped.
    t, tz = b['by'] - .8, b['zb'] - .4
    pts = [(0, t, tz), (0, 1.4, tz + v(1.6, 2.2, 2.6)), (0, .5, tz + v(3.2, 4.6, 5.6))]
    chain(s, 'tail', pts, [v(1.3, 1.4, 1.5), v(1.0, 1.0, 1.1)], ['fur', 'dark'])
    # The dandelion: a bud, a flower going to seed, then a full seed globe.
    hand = b['hands'][1]
    s.bone('dandelion', 'arm_r', hand)
    stem_top = (hand[0] + v(2.4, 3.2, 4.0), hand[1] + v(3.4, 4.8, 6.0), hand[2] + .4)
    s.beam('dandelion', (hand[0], hand[1] - .8, hand[2]), stem_top, .32, 'stem', group='acc', name='dandelion_stem')
    if s.age == 0:
        s.rbox('dandelion', stem_top, (1.2, 1.5, 1.2), 'bud', .35, group='acc', name='bud')
        s.rbox('dandelion', (stem_top[0], stem_top[1] - .6, stem_top[2]), (1.3, .7, 1.3), 'leaf', .25, group='acc',
               name='sepal')
    else:
        r = v(0, 1.5, 2.2)
        s.rbox('dandelion', stem_top, (r * .8, r * .8, r * .8), 'puff_core', .2, group='acc', name='puff_core')
        n = v(0, 10, 18)
        for k in range(n):
            # Fibonacci sphere of seed parachutes.
            yv = 1 - 2 * (k + .5) / n
            rad = math.sqrt(max(0, 1 - yv * yv))
            th = k * 2.39996
            d = np.array([math.cos(th) * rad, yv, math.sin(th) * rad])
            p = np.array(stem_top) + d * r
            s.beam('dandelion', np.array(stem_top) + d * r * .3, p, .14, 'puff_core', group='acc', name='seed_stalk')
            s.rbox('dandelion', p, (.62, .62, .62), 'puff', .18, group='acc', name='seed_puff')
    if s.age >= 1:
        ny = hy - hh / 2 + .1
        s.rbox('body', (0, ny, b['cz']), (W * .9, 1.0, D * .92), 'scarf', .3, group='acc', name='scarf')
        _leaf(s, 'body', (-W * .25, ny - .2, b['zf'] + .1), (-W * .25 - .6, ny - 2.2, b['zf'] - .4), 1.5)
        if s.age == 2:
            _leaf(s, 'body', (-W * .05, ny - .2, b['zf'] + .1), (-W * .05 + .4, ny - 2.6, b['zf'] - .5), 1.4)
    return s.finish()


# -------------------------------------------------------------------- chinchilla

def _cotton_chinchilla(stage):
    s = Pet('cotton_chinchilla', stage, dict(fur='#B9BDCB', fur_top='#A2A6B6', tip='#E9ECF4', cream='#F6F6FA',
                                             inner='#F2C6CE', nose='#E39AAA', mouth='#5A5464', blush='#F2B2C2',
                                             cotton='#FFFFFF', husk='#8A6A4E', stem='#6E8A4E', leaf='#86B46A',
                                             leaf_vein='#5E8A4A'),
            kinds=dict(fur_top='fur', tip='fur', cotton='glow', husk='wood', stem='wood'))
    v = s.v
    s.scale = v(.74, .78, .82)
    W, H, D = v(6.2, 7.0, 7.6), v(6.4, 7.6, 8.6), v(6.0, 6.8, 7.4)
    b = sitter(s, W, H, D, foot=(v(1.6, 1.8, 1.9), .9, v(2.2, 2.5, 2.8)), foot_mat='inner', hand_mat='inner',
               lower=1.0, upper=.86, chest_h=0, arm=v(1.0, 1.1, 1.2), arm_len=v(1.8, 2.2, 2.4), hands=(.9, -.5),
               haunch=(v(2.8, 3.2, 3.6), v(3.0, 3.4, 3.8), v(3.8, 4.2, 4.6)))
    hw, hh, hd = v(7.4, 7.8, 8.2), v(6.2, 6.4, 6.6), v(6.0, 6.2, 6.4)
    hy = b['top'] + hh * .2
    f = face(s, b, (hw, hh, hd), pos=(hy, b['cz'] - .4), pivot=(0, hy - hh * .45, b['cz']),
             snout={'size': (v(2.4, 2.4, 2.6), v(1.6, 1.6, 1.7), v(.5, .7, .8)), 'mat': 'cream', 'dy': -.22},
             nose={'size': (.9, .55, .45)}, mouth='cat',
             eyes={'iris': '#0E0E14', 'iris_light': '#3A3A4E', 'w': v(2.0, 1.9, 1.8), 'h': v(2.2, 2.1, 2.0)},
             ears={'kind': 'round', 'w': v(3.4, 3.8, 4.2), 'h': v(3.4, 3.8, 4.2), 'd': .8, 'tilt': 28, 'lean': -8,
                   'x': .3, 'sink': .6})
    for side in (-1, 1):
        for dx, dy in ((.9, .1), (1.2, -.3)):
            s.mark(decal(Mark(ellipsoid((side * dx, f['sy'] + dy, f['snout_front']), (.13, .13, .6)), 'fur_top',
                              groups=('snout',), facing=(0, 0, -1), min_dot=.5)))
    s.mark(Mark(ellipsoid((0, b['by'], b['zf']), (W * .36, H * .5, 2.6)), 'cream', groups=('body',), jitter=.3))
    if s.age == 2:
        s.mark(Bands((0, 1, 0), -5, 1.1, .3, 'tip', groups=('body', 'head'), mats=('fur',), facing=(0, .3, 1), min_dot=-.3,
                     wobble=.15))
    s.mark(Mark(slab(1, -1e9, f['ey'] - .9), 'cream', groups=('head',), jitter=.3))
    # A plume tail that curls up the back.
    t, tz = b['by'] - .8, b['zb'] - .5
    pts = [(0, t, tz), (0, t + v(1.4, 2.0, 2.4), tz + v(1.8, 2.4, 2.8)), (0, t + v(3.2, 4.4, 5.4), tz + v(1.6, 2.2, 2.6))]
    chain(s, 'tail', pts, [v(2.4, 2.8, 3.2), v(2.6, 3.2, 3.6)], 'fur')
    s.mark(Mark(slab(1, t + v(2.4, 3.4, 4.2)), 'tip', groups=('tail',), jitter=.3))
    # Cotton bolls: one held, then a crown of three.
    hold = (0, b['hands'][0][1] + .3, b['hands'][0][2] - .8)

    def boll(bone, c, r):
        s.rbox(bone, (c[0], c[1] - r * .7, c[2]), (r * 1.4, r * .7, r * 1.4), 'husk', r * .2, group='acc', name='husk')
        for dx, dy, dz in ((0, .2, 0), (-.5, 0, .2), (.5, 0, .2), (0, .05, -.5)):
            s.rbox(bone, (c[0] + dx * r, c[1] + dy * r, c[2] + dz * r), (r * 1.2, r * 1.1, r * 1.2), 'cotton', r * .4,
                   group='acc', name='cotton')
    boll('arm_r', hold, v(.9, 1.1, 1.2))
    if s.age == 2:
        for k, a in enumerate((-30, 0, 30)):
            ar = math.radians(a)
            boll('head', (math.sin(ar) * 1.8, hy + hh / 2 + .6 - abs(a) * .01, f['hz'] + math.cos(ar) * .4), .8)
    elif s.age == 1:
        _leaf(s, 'head', (0, hy + hh / 2, f['hz']), (1.1, hy + hh / 2 + 1.4, f['hz'] - .3), 1.0)
    return s.finish()


# -------------------------------------------------------------------------- pika

def _pebble_pika(stage):
    s = Pet('pebble_pika', stage, dict(fur='#9C9AA3', fur_top='#86848E', speck='#B4B2BA', cream='#F4EEE4',
                                       rim='#FFFFFF', inner='#E9B8BE', nose='#4A3E44', mouth='#4A3E44', blush='#F2A8B4',
                                       pebble='#C7C3BC', pebble_dark='#8E8A84', pebble_blue='#9FB4C4', moss='#86B46A'),
            kinds=dict(fur_top='fur', speck='fur', rim='fur', pebble='stone', pebble_dark='stone', pebble_blue='stone',
                       moss='leaf'))
    v = s.v
    s.scale = v(.74, .78, .82)
    W, H, D = v(6.6, 7.4, 8.2), v(5.6, 6.4, 7.2), v(6.6, 7.4, 8.2)
    b = sitter(s, W, H, D, foot=(v(1.6, 1.7, 1.8), .9, v(2.2, 2.4, 2.6)), foot_mat='fur', lower=1.0, upper=.88,
               chest_h=0, arm=v(1.0, 1.1, 1.2), arm_len=v(1.4, 1.6, 1.8), hands=(1.4, -.2))
    hw, hh, hd = v(7.2, 7.4, 7.6), v(6.0, 6.0, 6.2), v(5.8, 6.0, 6.2)
    hy = b['top'] + hh * .18
    f = face(s, b, (hw, hh, hd), pos=(hy, b['cz'] - .6), pivot=(0, hy - hh * .45, b['cz']),
             snout={'size': (v(2.4, 2.4, 2.5), v(1.6, 1.6, 1.7), v(.5, .7, .8)), 'mat': 'fur', 'dy': -.22},
             nose={'size': (.9, .6, .45)}, mouth='cat',
             eyes={'iris': '#121216', 'iris_light': '#40404E', 'w': v(1.9, 1.8, 1.7), 'h': v(2.1, 2.0, 1.9)},
             ears={'kind': 'round', 'w': v(2.6, 2.8, 3.0), 'h': v(2.4, 2.6, 2.8), 'd': .8, 'tilt': 22, 'x': .32, 'sink': .4})
    # White ear rims (a ring painted around the inner panel).
    s.mark(Mark(_all, 'rim', groups=('ear',), parts=('ear_round',), facing=(0, 0, -1), min_dot=.5))
    s.mark(Mark(ellipsoid((0, b['by'], b['zf']), (W * .38, H * .5, 2.6)), 'cream', groups=('body',), jitter=.3))
    s.mark(Mark(slab(1, -1e9, f['ey'] - .9), 'cream', groups=('head',), jitter=.3))
    if s.age >= 1:
        s.mark(Dots(v(0, 1.4, 1.1), v(0, .24, .2), 'speck', groups=('body', 'head'), mats=('fur',), offset=(.3, .1, .2)))
    # Pebbles carried on the back like treasures.
    stones = v([(0, 1.0, 'pebble')], [(-.9, 1.1, 'pebble'), (1.0, .9, 'pebble_dark')],
               [(-1.3, 1.2, 'pebble'), (.4, 1.4, 'pebble_blue'), (1.6, .9, 'pebble_dark'), (-.2, .8, 'moss')])
    for i, (x, r, m) in enumerate(stones):
        s.rbox('body', (x, b['top'] - .2 + r * .4, b['cz'] + 1.2 + (i % 2) * .8), (r * 1.6, r * 1.1, r * 1.4), m, r * .35,
               (0, i * 20, 0), group='acc', name='pebble')
    return s.finish()


# ------------------------------------------------------------------------- koala

def _sage_koala(stage):
    s = Pet('sage_koala', stage, dict(fur='#A4B0A6', fur_top='#8E9A90', cream='#F2F2EA', tuft='#F6F7F2',
                                      inner='#E6E6DE', nose='#1E2222', mouth='#3A403C', blush='#F2AEB6',
                                      branch='#7A5E44', leaf='#8EB89A', leaf_dark='#6E9C80', leaf_vein='#5E8A6E',
                                      pad='#4A4E4C'),
            kinds=dict(fur_top='fur', tuft='fur', branch='wood', leaf_dark='leaf', pad='smooth'))
    v = s.v
    s.scale = v(.74, .78, .82)
    W, H, D = v(7.2, 8.2, 9.0), v(6.0, 7.4, 8.6), v(6.4, 7.0, 7.6)
    b = sitter(s, W, H, D, foot=(v(2.0, 2.2, 2.4), 1.1, v(2.6, 2.9, 3.2)), lower=1.0, upper=.86, chest_h=.65,
               arm=v(1.6, 1.8, 2.0), arm_len=v(2.4, 2.8, 3.2), hands=(1.0, -.7),
               haunch=(v(2.8, 3.2, 3.6), v(3.0, 3.4, 3.8), v(3.8, 4.2, 4.6)))
    hw, hh, hd = v(7.6, 8.0, 8.4), v(6.2, 6.4, 6.6), v(5.8, 6.0, 6.2)
    hy = b['top'] + hh * .32
    f = face(s, b, (hw, hh, hd), pos=(hy, b['cz'] - .3), pivot=(0, hy - hh * .45, b['cz']),
             eyes={'iris': '#1A1E1E', 'iris_light': '#4E5A56', 'w': v(1.6, 1.5, 1.4), 'h': v(1.8, 1.7, 1.6), 'dx': .26,
                   'dy': .14},
             ears={'kind': 'round', 'w': v(3.6, 4.0, 4.4), 'h': v(3.2, 3.6, 4.0), 'd': 1.4, 'tilt': 34, 'x': .42,
                   'sink': 1.2}, mouth=None)
    # The big glossy bean nose.
    nz = f['front'] - .5
    s.rbox('head', (0, hy - hh * .12, nz), (v(1.6, 1.7, 1.8), v(2.0, 2.2, 2.4), 1.4), 'nose', .5, group='nose', name='nose')
    s.mark(decal(Mark(ellipsoid((.35, hy + hh * .0, nz - .7), (.22, .4, .5)), '#8A9494', groups=('nose',),
                      facing=(0, 0, -1))))
    s.mark(decal(Mark(polyline([(-.7, hy - hh * .38, f['front']), (0, hy - hh * .42, f['front']), (.7, hy - hh * .38, f['front'])],
                               .14), 'mouth', groups=('head',), facing=(0, 0, -1), min_dot=.5)))
    s.mark(Mark(ellipsoid((0, b['by'], b['zf']), (W * .36, H * .55, 2.6)), 'cream', groups=('body',), jitter=.3))
    # Fluffy white ear fringe.
    for side in (-1, 1):
        nm = 'ear_' + ('l' if side < 0 else 'r')
        x = side * hw * .42
        for k in range(v(2, 3, 4)):
            a = math.radians(-60 + k * 120 / max(1, v(2, 3, 4) - 1))
            c = (x + side * math.cos(a) * v(1.7, 2.0, 2.2) + side * .8, hy + hh / 2 + v(.4, .6, .8) + math.sin(a) * v(1.6, 1.8, 2.0),
                 f['hz'] - .1)
            s.rbox(nm, c, (1.3, 1.3, 1.1), 'tuft', .35, group='ear', name='ear_tuft')
    s.mark(Mark(_all, 'pad', groups=('paw',), facing=(0, -1, 0), min_dot=.5))
    # A eucalyptus branch held across the chest.
    hand = b['hands'][1]
    s.bone('branch', 'arm_r', hand)
    a = (hand[0] - v(2.6, 3.2, 3.8), hand[1] - .6, hand[2] - .3)
    c = (hand[0] + v(1.6, 2.0, 2.4), hand[1] + v(1.8, 2.4, 3.0), hand[2] - .5)
    s.beam('branch', a, c, .55, 'branch', group='acc', name='branch')
    n = v(3, 5, 7)
    for k in range(n):
        t = (k + .5) / n
        p = tuple(a[j] + (c[j] - a[j]) * t for j in range(3))
        side = 1 if k % 2 else -1
        tip = (p[0] + .3, p[1] + side * 1.4, p[2] - .3)
        _leaf(s, 'branch', p, tip, 1.0, 'leaf' if k % 2 else 'leaf_dark')
    return s.finish()


# ------------------------------------------------------------------------- sloth

def _sleepy_sloth(stage):
    s = Pet('sleepy_sloth', stage, dict(fur='#A48C74', fur_top='#8E765E', face_disc='#F4E8D2', mask='#5E4838',
                                        cream='#F4E8D2', inner='#5E4838', nose='#3A2A24', mouth='#5A3E34', blush='#F2A69A',
                                        claw='#FFF4DA', pillow='#8CC07A', pillow_dark='#6EA25E', leaf_vein='#548A48'),
            kinds=dict(fur_top='fur', face_disc='fur', mask='fur', claw='smooth', pillow='cloth', pillow_dark='cloth'))
    v = s.v
    s.scale = v(.74, .78, .82)
    W, H, D = v(7.0, 8.0, 8.8), v(6.0, 7.4, 8.4), v(6.2, 6.8, 7.4)
    b = sitter(s, W, H, D, foot=(v(1.8, 2.0, 2.2), 1.0, v(2.4, 2.6, 2.8)), lower=1.0, upper=.86, chest_h=.65,
               arm=v(1.5, 1.6, 1.7), arm_len=v(3.0, 3.6, 4.2), hands=(1.2, -1.2), lean=.3,
               haunch=(v(2.6, 3.0, 3.4), v(2.8, 3.2, 3.6), v(3.6, 4.0, 4.4)))
    hw, hh, hd = v(7.0, 7.2, 7.4), v(6.0, 6.0, 6.2), v(5.8, 6.0, 6.2)
    hy = b['top'] + hh * .3
    f = face(s, b, (hw, hh, hd), pos=(hy, b['cz'] - .5), pivot=(0, hy - hh * .45, b['cz']),
             nose={'size': (1.4, .9, .6), 'dy': .5}, mouth='smile',
             eyes={'iris': '#1E1414', 'iris_light': '#4A3428', 'style': 'sleepy', 'lid': v(.2, .1, .0),
                   'lid_colour': 'mask', 'w': v(1.6, 1.5, 1.4), 'h': v(1.7, 1.6, 1.5), 'dx': .25},
             ears=None, blush=True)
    f['sy'] = hy - hh * .32
    # Pale face disc with dark sleepy stripes sweeping back from the eyes.
    s.mark(Mark(ellipsoid((0, hy - hh * .05, f['front']), (hw * .42, hh * .42, 1.8)), 'face_disc', groups=('head',),
                jitter=.2))
    s.mark(Mark(mirrored(polyline([(f['ex'] - .4, f['ey'], f['front']), (hw * .5, f['ey'] + .3, f['front'] + 1.6)], .8)),
                'mask', groups=('head',), min_dot=-2))
    s.mark(Mark(slab(1, hy + hh * .3), 'fur_top', groups=('head',), jitter=.4))
    # Long arms with three cream claws; the pillow hugged in front.
    for side, hand in zip((-1, 1), b['hands']):
        nm = 'arm_' + ('l' if side < 0 else 'r')
        for k in (-1, 0, 1):
            s.beam(nm, (hand[0] + k * .45, hand[1] - .2, hand[2] - .3), (hand[0] + k * .5, hand[1] - 1.3, hand[2] - .9), .32,
                   'claw', group='paw', name='claw')
    pc = (0, b['hands'][0][1] + .2, b['zf'] - v(.9, 1.1, 1.3))
    pw = v(4.2, 5.0, 5.8)
    s.rbox('body', pc, (pw, v(2.8, 3.2, 3.6), v(1.8, 2.0, 2.2)), 'pillow', .7, group='acc', name='pillow')
    s.mark(Mark(polyline([(-pw * .4, pc[1], pc[2] - 2), (pw * .4, pc[1], pc[2] - 2)], .14), 'leaf_vein', groups=('acc',),
                parts=('pillow',), facing=(0, 0, -1)))
    if s.age >= 1:
        for k in range(v(0, 2, 4)):
            x = -pw * .3 + k * pw * .6 / max(1, v(0, 1, 3))
            s.mark(Mark(polyline([(x, pc[1], pc[2] - 2), (x + .5, pc[1] + .9, pc[2] - 2)], .1), 'leaf_vein',
                        groups=('acc',), parts=('pillow',), facing=(0, 0, -1)))
            s.mark(Mark(polyline([(x, pc[1], pc[2] - 2), (x + .5, pc[1] - .9, pc[2] - 2)], .1), 'leaf_vein',
                        groups=('acc',), parts=('pillow',), facing=(0, 0, -1)))
    if s.age == 2:
        s.rbox('body', (0, b['top'] - .4, b['zb'] - .8), (W * .82, 2.0, 2.6), 'fur_top', .7, name='mane')
    return s.finish()


# ------------------------------------------------------------------------ jerboa

def _dusk_jerboa(stage):
    s = Pet('dusk_jerboa', stage, dict(fur='#C9A99A', fur_top='#A98AA0', cream='#FBF1EA', inner='#F2B8CC',
                                       ear_skin='#E6C2CC', nose='#E39AAA', mouth='#5A4250', blush='#F2AAC0',
                                       tuft='#3A3C8C', tuft_white='#FFFFFF'),
            kinds=dict(fur_top='fur', ear_skin='skin', tuft='fur', tuft_white='fur'))
    v = s.v
    s.scale = v(.74, .8, .84)
    W, H, D = v(5.4, 5.6, 5.8), v(5.0, 5.6, 6.2), v(5.0, 5.4, 5.8)
    b = sitter(s, W, H, D, foot=(v(1.3, 1.4, 1.5), .8, v(3.2, 4.0, 4.8)), lower=1.0, upper=.82, chest_h=.6,
               arm=v(.8, .8, .9), arm_len=v(1.6, 1.8, 2.0), hands=(.9, -.3), standing=v(.6, 1.4, 2.2),
               haunch=(v(2.6, 3.0, 3.4), v(3.4, 4.0, 4.6), v(4.0, 4.6, 5.2)), leg_lower=True)
    hw, hh, hd = v(6.4, 6.2, 6.2), v(5.4, 5.2, 5.2), v(5.4, 5.6, 5.8)
    hy = b['top'] + hh * .32
    f = face(s, b, (hw, hh, hd), pos=(hy, b['cz'] - .3), pivot=(0, hy - hh * .45, b['cz']),
             snout={'size': (v(2.4, 2.4, 2.4), v(1.6, 1.6, 1.6), v(.7, .9, 1.0)), 'mat': 'cream', 'dy': -.22},
             nose={'size': (.9, .55, .45)}, mouth='cat',
             eyes={'iris': '#16101A', 'iris_light': '#5A4070', 'w': v(2.2, 2.1, 2.0), 'h': v(2.4, 2.3, 2.2)},
             ears={'kind': 'round', 'w': v(2.4, 2.6, 2.8), 'h': v(4.4, 5.4, 6.4), 'd': .7, 'tilt': 18, 'lean': -10, 'x': .3,
                   'mat': 'ear_skin'})
    s.mark(Mark(slab(1, -1e9, f['ey'] - .8), 'cream', groups=('head',), jitter=.25))
    s.mark(Mark(ellipsoid((0, b['by'], b['zf']), (W * .38, H * .6, 2.4)), 'cream', groups=('body',), jitter=.3))
    s.mark(Mark(slab(1, b['top'] - .6), 'fur_top', groups=('body',), jitter=.3))
    s.mark(Mark(_all, 'cream', groups=('paw',)))
    # The long whip tail with a two-tone tuft.
    t, tz = b['by'] - .6, b['zb'] - .4
    L = v(7, 10, 13)
    pts = [(0, t, tz), (0, t - .6, tz + L * .3), (0, t + .2, tz + L * .62), (0, t + 1.6, tz + L * .9)]
    names = chain(s, 'tail', pts, [v(.9, .9, 1.0), .8, .7], 'fur')
    end = np.array(pts[-1])
    s.rbox(names[-1], end + np.array([0, .3, .6]), (v(1.6, 1.9, 2.2), v(1.4, 1.6, 1.8), v(2.4, 3.0, 3.6)), 'tuft', .45,
           group='tail', name='tail_tuft')
    s.rbox(names[-1], end + np.array([0, .45, v(1.6, 2.0, 2.4)]), (v(1.4, 1.6, 1.8), v(1.2, 1.4, 1.5), 1.4), 'tuft_white',
           .4, group='tail', name='tail_tuft_tip')
    return s.finish()


# --------------------------------------------------------------------------- bat

def _blueberry_bat(stage):
    s = Pet('blueberry_bat', stage, dict(fur='#4A5BB8', fur_top='#3C4A9E', cream='#8E9CE8', inner='#B98AD8',
                                         membrane='#9A72D0', membrane_dark='#7650B0', bone_='#3A2E6A', nose='#E39AB4',
                                         mouth='#2A2050', blush='#F2A0C8', fang='#FFFFFF', berry='#3E4FB0',
                                         berry_bloom='#8E9CE8', leaf='#6EB06A', leaf_vein='#4E8E4E'),
            kinds=dict(fur_top='fur', membrane='membrane', membrane_dark='membrane', bone_='smooth', fang='smooth',
                       berry='smooth', berry_bloom='smooth'))
    v = s.v
    s.scale = v(.76, .8, .84)
    R = v(5.6, 6.4, 7.0)
    cy = 4.0
    s.bones['body'] = ('root', [0, cy, 0])
    s.rbox('body', (0, cy, 0), (R, R * .95, R * .85), 'fur', R * .3, name='body')
    s.rbox('body', (0, cy - R * .45, .2), (R * .7, R * .3, R * .6), 'fur', R * .1, name='belly')
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        s.bone('leg_' + sfx, 'body', (side * R * .22, cy - R * .4, .3))
        s.rbox('leg_' + sfx, (side * R * .22, cy - R * .58, .5), (1.0, 1.4, 1.2), 'fur_top', .3, group='leg', name='foot')
    hw, hh, hd = v(6.6, 6.8, 7.0), v(5.6, 5.6, 5.8), v(5.2, 5.4, 5.6)
    hy = cy + R * .4 + hh * .32
    f = face(s, dict(top=cy + R / 2, zf=-R / 2), (hw, hh, hd), pos=(hy, -.5), pivot=(0, hy - hh * .45, 0),
             snout={'size': (v(2.4, 2.4, 2.4), v(1.5, 1.5, 1.6), v(.5, .6, .7)), 'mat': 'cream', 'dy': -.22},
             nose={'size': (1.1, .6, .4), 'dy': .45}, mouth='cat',
             eyes={'iris': '#2A1E5A', 'iris_light': '#C6A4FF', 'w': v(2.0, 1.9, 1.8), 'h': v(2.2, 2.1, 2.0)},
             ears={'w': v(3.0, 3.2, 3.4), 'h': v(3.6, 4.0, 4.4), 'tilt': 22, 'lean': -4, 'x': .32, 'inner': 'inner'})
    for side in (-1, 1):
        s.mark(decal(Mark(both(slab(0, side * .45 - .16, side * .45 + .16), slab(1, f['sy'] - .9, f['sy'] - .45)), 'fang',
                          groups=('snout',), facing=(0, 0, -1), min_dot=.5)))
    s.mark(Mark(ellipsoid((0, cy, -R / 2), (R * .3, R * .4, 1.6)), 'cream', groups=('body',), jitter=.3))
    # Membrane wings: flat panes with scalloped trailing edges; finger bones are painted on.
    span = v(6.0, 8.0, 10.0)
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        sh = np.array([side * R * .4, cy + R * .25, .3])
        s.bone('wing_' + sfx, 'body', sh)
        wrist = sh + np.array([side * span * .4, span * .18, .25])
        s.bone('wingtip_' + sfx, 'wing_' + sfx, wrist)
        cols = 7
        for k in range(cols):
            t0, t1 = k / cols, (k + 1) / cols
            tm = (t0 + t1) / 2
            x0, x1 = sh[0] + side * span * t0, sh[0] + side * span * t1
            xm = (x0 + x1) / 2
            # Leading edge rises to the wrist then falls to the tip; the trailing edge dips between fingers.
            top = cy + R * .25 + span * (.18 * min(1, tm / .4) - .3 * max(0, tm - .4))
            scallop = .5 + .5 * math.cos(tm * math.tau * 1.5)
            bottom = cy - R * .3 + span * (.05 * tm) + scallop * span * .12 + span * .1 * max(0, tm - .6)
            h = max(.8, top - bottom)
            bone = 'wing_' + sfx if tm < .4 else 'wingtip_' + sfx
            s.cube(bone, (xm, bottom + h / 2, .3 + tm * 1.2), (abs(x1 - x0) + .05, h, .35),
                   'membrane' if k % 2 == 0 else 'membrane_dark', (0, -side * 12, 0), (xm, bottom + h / 2, .3),
                   group='wing', name='membrane')
        tips = [sh + np.array([side * span, span * (.18 - .18), .9]), sh + np.array([side * span * .8, -span * .2, .9]),
                sh + np.array([side * span * .5, -span * .28, .7])]
        lines = [polyline([sh, wrist], .28)] + [polyline([wrist, t], .2) for t in tips]
        s.mark(Mark(either(*lines), 'bone_', groups=('wing',), facing=(0, 0, -1), min_dot=-2))
        s.beam('wing_' + sfx, sh, wrist, .6, 'bone_', group='wing', name='wing_arm')
        s.rbox('wingtip_' + sfx, wrist + np.array([0, .4, 0]), (.7, 1.0, .7), 'bone_', .2, group='wing', name='thumb')
    # Blueberries and leaves on the chest.
    n = v(1, 2, 3)
    for k in range(n):
        c = (-.9 + k * .9 if n > 1 else 0, cy + R * .1 - (k % 2) * .6, -R / 2 - .4)
        s.rbox('body', c, (1.3, 1.3, 1.2), 'berry', .4, group='acc', name='blueberry')
        s.mark(Mark(ellipsoid((c[0] - .2, c[1] + .3, c[2] - .6), (.3, .25, .4)), 'berry_bloom', groups=('acc',),
                    parts=('blueberry',)))
    _leaf(s, 'body', (.6, cy + R * .3, -R / 2 - .2), (1.9, cy + R * .3 + 1.0, -R / 2 - .6), 1.1)
    if s.age >= 1:
        _leaf(s, 'body', (-.6, cy + R * .3, -R / 2 - .2), (-1.9, cy + R * .3 + .8, -R / 2 - .6), 1.0)
    return s.finish()
