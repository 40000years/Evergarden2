"""Pet Atelier: birds, frogs, lizards and the kelp turtle."""
from __future__ import annotations

import math

import numpy as np

from pet_atelier import (rotation, Pet, Mark, Bands, Eye, Inset, Toward, Dots, chain, ellipsoid, slab, boxed, both,
                         either, minus, mirrored, polyline)
from pet_atelier_parts import *  # noqa: F401,F403
from pet_atelier_parts import _nose, _smile, _blush, _bow, _leaf, _all


# ----------------------------------------------------------------- bird kit

def bird_body(s, W, H, D, cy, mat='feather', belly=None, cz=0.0, tail=None, tail_mat=None, legs=(1.0, 2.0),
              leg_mat='leg', foot_mat=None, wing=True, wing_mat=None, wing_tip=None, wing_bar=None, leg_lower=False):
    """An egg-shaped bird: a rounded body with a fuller breast, wings tucked at the sides, a tail and thin legs."""
    s.bones['body'] = ('root', [0, cy, cz])
    s.rbox('body', (0, cy + H * .05, cz + D * .08), (W, H * .9, D * .86), mat, min(W, H * .9, D * .86) * .38, name='body')
    s.rbox('body', (0, cy - H * .1, cz - D * .2), (W * .9, H * .8, D * .5), belly or mat, min(W * .9, H * .8, D * .5) * .38,
           name='breast')
    lt, lh = legs
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        hip = (side * W * .2, cy - H * .3, cz + .3)
        s.bone('leg_' + sfx, 'body', hip)
        foot_bone = 'leg_' + sfx
        s.beam('leg_' + sfx, hip, (hip[0], .3, hip[2] - .1), lt * .6, leg_mat, group='leg', name='shank')
        if leg_lower:
            foot_bone = s.bone('leg_' + sfx + '_lower', 'leg_' + sfx, (hip[0], lh * .3, hip[2]))
        for k in (-1, 0, 1):
            s.beam(foot_bone, (hip[0], .25, hip[2] - .1), (hip[0] + k * lt * .55, .25, hip[2] - lt * 1.3), lt * .45,
                   foot_mat or leg_mat, lt * .3, group='paw', name='toe')
    if wing:
        for side in (-1, 1):
            sfx = 'l' if side < 0 else 'r'
            sh = (side * W * .46, cy + H * .32, cz - D * .18)
            s.bone('wing_' + sfx, 'body', sh)
            c = (side * (W / 2 + .05), cy + H * .04, cz + D * .1)
            s.rbox('wing_' + sfx, c, (.9, H * .62, D * .6), wing_mat or mat, .4, (4, 0, side * -4), sh, group='wing',
                   name='wing')
            s.bone('wingtip_' + sfx, 'wing_' + sfx, (side * W / 2, cy, cz + D * .3))
            c2 = (side * (W / 2 + .02), cy - H * .06, cz + D * .42)
            s.rbox('wingtip_' + sfx, c2, (.8, H * .44, D * .36), wing_tip or wing_mat or mat, .3, (8, 0, side * -4), sh,
                   group='wing', name='primary')
        if wing_bar:
            s.mark(Bands((0, -.35, 1), cz - D, D * .16, .22, wing_bar, groups=('wing',), facing=(1, 0, 0), min_dot=-2))
    if tail:
        n, length, width = tail
        root = (0, cy + H * .18, cz + D * .44)
        s.bone('tail', 'body', root)
        for k in range(n):
            a = math.radians((k - (n - 1) / 2) * (20 if n > 1 else 0))
            start = np.array(root)
            end = start + np.array([math.sin(a) * length * .5, length * .38, math.cos(a) * length])
            s.beam('tail', start, end, width, (tail_mat or mat), .5, group='tail', name='tail_feather')
    return dict(cy=cy, cz=cz, top=cy + H / 2, zf=cz - D / 2, zb=cz + D / 2, W=W, H=H, D=D)


def bird_head(s, b, size, pos, beak, eyes, mat='feather', blush=True):
    hw, hh, hd = size
    hy, hz = pos
    s.bone('head', 'body', (0, hy - hh * .4, hz + hd * .2))
    s.rbox('head', (0, hy, hz), size, mat, min(size) * .2, name='skull')
    front = hz - hd / 2
    bw, bh, bl = beak['size']
    bmat = beak.get('mat', 'beak')
    by = hy + hh * beak.get('dy', -.12)
    s.rbox('head', (0, by + bh * .14, front - bl / 2 + .3), (bw, bh * .6, bl + .6), bmat, min(bw, bh * .6) * .3,
           group='snout', name='beak_upper')
    s.rbox('head', (0, by - bh * .26, front - bl * .36 + .3), (bw * .84, bh * .42, bl * .72 + .6), beak.get('lower', bmat),
           min(bw, bh * .42) * .25, group='snout', name='beak_lower')
    e = dict(eyes)
    ex = hw * e.pop('dx', .28)
    ey = hy + hh * e.pop('dy', .1)
    ew, eh = e.pop('w'), e.pop('h')
    s.mark(Eye((ex, ey, front), ew, eh, **e))
    if blush:
        _blush(s, hw * .36, ey - eh * .85, front, .8, .42)
    return dict(hy=hy, hz=hz, hw=hw, hh=hh, hd=hd, front=front, ex=ex, ey=ey, ew=ew, eh=eh, by=by)


def _flower(s, bone, centre, r, petals, petal_mat, disc_mat, n=10, facing='up', group='acc', tilt=0.0):
    """A daisy-like flower: a disc and a ring of petals, lying flat (up) or facing forward."""
    x, y, z = centre
    if facing == 'up':
        s.rbox(bone, centre, (r * 1.2, r * .45, r * 1.2), disc_mat, r * .18, (tilt, 0, 0), centre, group=group, name='disc')
        for k in range(n):
            a = math.radians(k * 360 / n)
            c = (x + math.cos(a) * r * 1.05, y - .05, z + math.sin(a) * r * 1.05)
            s.cube(bone, c, (r * .9, r * .25, r * .42), petals if k % 2 == 0 else petal_mat, (tilt, -math.degrees(a), 0),
                   centre, group=group, name='petal')
    else:
        s.rbox(bone, centre, (r * 1.2, r * 1.2, r * .45), disc_mat, r * .18, group=group, name='disc')
        for k in range(n):
            a = math.radians(k * 360 / n)
            c = (x + math.cos(a) * r * 1.05, y + math.sin(a) * r * 1.05, z + .05)
            s.cube(bone, c, (r * .9, r * .42, r * .25), petals if k % 2 == 0 else petal_mat, (0, 0, math.degrees(a)), c,
                   group=group, name='petal')


# ------------------------------------------------------------------------ duck

def _sunflower_duck(stage):
    s = Pet('sunflower_duck', stage, dict(feather='#FFE07A', belly='#FFF2B8', wing='#F7CC55', wing_tip='#EBB43E',
                                          beak='#F59A3A', beak_low='#E2802A', leg='#F28C2A', blush='#FFA48C',
                                          petal='#FFC21A', petal2='#FFAA14', disc='#7A4A22', seedling='#7CB85A',
                                          leaf='#6CAE52', leaf_vein='#4E8E40'),
            kinds=dict(feather='feather', belly='feather', wing='feather', wing_tip='feather', beak='smooth',
                       beak_low='smooth', leg='smooth', petal='leaf', petal2='leaf', disc='scale'))
    v = s.v
    s.scale = v(.8, .84, .88)
    W, H, D = v(5.0, 5.6, 6.2), v(4.2, 4.6, 5.0), v(5.8, 7.2, 8.4)
    cy = v(3.0, 3.4, 3.8)
    b = bird_body(s, W, H, D, cy, belly='belly', legs=(v(1.3, 1.4, 1.5), v(1.6, 1.8, 2.0)), leg_mat='leg',
                  wing_mat='wing', wing_tip='wing_tip', tail=(v(1, 3, 5), v(1.4, 1.8, 2.4), v(1.3, 1.2, 1.2)),
                  tail_mat='wing', leg_lower=True)
    # A short neck lifts the head over the breast.
    s.rbox('body', (0, b['top'] + .2, b['zf'] + 1.6), (W * .5, 2.2, 2.4), 'feather', .6, name='neck')
    hw, hh, hd = v(5.8, 5.6, 5.6), v(5.2, 5.0, 5.0), v(5.0, 5.0, 5.2)
    hy = b['top'] + hh * .52
    f = bird_head(s, b, (hw, hh, hd), (hy, b['zf'] + hd * .3),
                  {'size': (v(3.2, 3.4, 3.6), v(1.5, 1.6, 1.7), v(1.4, 2.0, 2.4)), 'lower': 'beak_low', 'dy': -.2},
                  {'iris': '#1C1410', 'iris_light': '#4A3424', 'w': v(1.9, 1.75, 1.6), 'h': v(2.1, 1.95, 1.8), 'dx': .27})
    top_y = hy + hh / 2
    if s.age == 0:
        for a in (-30, 0, 30):
            ar = math.radians(a)
            c = (math.sin(ar) * 1.0, top_y + .7, f['hz'] + math.cos(ar) * .2)
            s.cube('head', c, (.7, 1.4, .4), 'petal', (0, 0, -a), c, group='acc', name='petal')
    elif s.age == 1:
        s.beam('head', (0, top_y - .2, f['hz']), (0, top_y + 1.2, f['hz']), .3, 'seedling', group='acc', name='stem')
        s.rbox('head', (0, top_y + 1.5, f['hz']), (1.4, 1.2, 1.4), 'petal', .35, group='acc', name='bud')
        s.rbox('head', (0, top_y + 1.0, f['hz']), (1.5, .5, 1.5), 'leaf', .2, group='acc', name='sepal')
    else:
        # The sunflower sun hat.
        _flower(s, 'head', (0, top_y + .35, f['hz'] + .2), 2.0, 'petal', 'petal2', 'disc', n=12)
        s.mark(Dots(.5, .14, 'petal2', groups=('acc',), parts=('disc',)))
    return s.finish()


# ------------------------------------------------------------------------ chick

def _teacup_chick(stage):
    s = Pet('teacup_chick', stage, dict(feather='#FFE173', belly='#FFF0A8', wing='#FFD653', beak='#F5A13A', leg='#F29A3A',
                                        blush='#FFA08A', cup='#FFFBF4', cup_shade='#EFE8E0', rim='#E4B54A', flower='#F49AB4',
                                        flower2='#9AC4F0', leaf='#7CBA62', leaf_vein='#5A9A4A', tea='#B88A4A',
                                        shell='#FFF8EC', inside='#E9DCCB'),
            kinds=dict(feather='feather', belly='feather', wing='feather', beak='smooth', leg='smooth', cup='smooth',
                       cup_shade='smooth', rim='metal', flower='plain', flower2='plain', tea='leaf', shell='shell',
                       inside='smooth'))
    v = s.v
    s.scale = v(0.96, 1.008, 1.056)
    R = v(6.0, 6.6, 7.2)
    cup_h = v(3.6, 4.0, 4.4)
    cw = R + 2.0
    cy = cup_h * .5 + R * .46
    s.bones['body'] = ('root', [0, cup_h / 2, 0])
    wall = 'cup' if s.age else 'shell'
    # The cup: four walls around the chick, a foot and a handle (an eggshell for the hatchling).
    for x, z, w, d in ((0, -cw / 2 + .5, cw, 1.0), (0, cw / 2 - .5, cw, 1.0), (-cw / 2 + .5, 0, 1.0, cw), (cw / 2 - .5, 0, 1.0, cw)):
        s.rbox('body', (x, cup_h / 2 + .3, z), (w, cup_h, d), wall, .3, group='acc', name='cup')
    s.rbox('body', (0, .7, 0), (cw - 1.2, 1.2, cw - 1.2), 'inside', .3, group='acc', name='cup_floor')
    s.rbox('body', (0, .25, 0), (cw * .6, .5, cw * .6), 'cup_shade' if s.age else 'shell', .2, group='acc', name='cup_foot')
    if s.age == 0:
        for k in range(8):
            a = math.radians(k * 45)
            c = (math.cos(a) * (cw / 2 - .5), cup_h + .55, math.sin(a) * (cw / 2 - .5))
            s.cube('body', c, (.9, .9, .9), 'shell', (45, -k * 45, 45), c, group='acc', name='shell_point')
    else:
        if s.age == 2:
            for x, z, w, d in ((0, -cw / 2 + .5, cw + .2, 1.2), (0, cw / 2 - .5, cw + .2, 1.2), (-cw / 2 + .5, 0, 1.2, cw),
                               (cw / 2 - .5, 0, 1.2, cw)):
                s.cube('body', (x, cup_h + .45, z), (w, .45, d), 'rim', group='acc', name='cup_rim')
        hx = cw / 2
        for a, c2 in (((hx, cup_h * .8, 0), (hx + 1.5, cup_h * .7, 0)), ((hx + 1.5, cup_h * .7, 0), (hx + 1.4, cup_h * .3, 0)),
                      ((hx + 1.4, cup_h * .3, 0), (hx, cup_h * .25, 0))):
            s.beam('body', a, c2, .7, 'cup', .7, group='acc', name='handle')
        s.mark(Dots(1.6, .42, 'flower', groups=('acc',), parts=('cup',), offset=(.4, .7, .2)))
        if s.age == 2:
            s.mark(Dots(1.6, .3, 'flower2', groups=('acc',), parts=('cup',), offset=(1.2, 1.5, 1.0)))
    # The fluffy chick: one round ball that is also its face.
    s.bone('head', 'body', (0, cy - R * .3, 0))
    s.rbox('head', (0, cy, 0), (R, R * .92, R * .88), 'feather', R * .36, name='chick')
    for dx, dz in ((-.45, 0), (.45, .2), (0, -.3)):
        s.beam('head', (dx, cy + R * .4, dz), (dx * 1.8, cy + R * .4 + 1.3, dz - .3), .5, 'feather', group='head', name='fluff')
    front = -R * .44
    s.rbox('head', (0, cy - R * .05, front - .4), (v(1.6, 1.7, 1.8), 1.1, 1.3), 'beak', .3, group='snout', name='beak')
    s.mark(Eye((R * .25, cy + R * .1, front), v(1.8, 1.7, 1.6), v(2.1, 2.0, 1.9), iris='#1A1210', iris_light='#4A3020'))
    _blush(s, R * .36, cy - R * .14, front, .85, .45)
    s.mark(Mark(slab(1, -1e9, cy - R * .2), 'belly', groups=('head',), jitter=.3))
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        sh = (side * R * .44, cy - R * .05, 0)
        s.bone('wing_' + sfx, 'body', sh)
        # Wings lift in a little wave, higher with age.
        tip = (side * (R * .5 + v(1.4, 2.2, 2.8)), cy + v(.4, 1.4, 2.4), -.2)
        s.beam('wing_' + sfx, sh, tip, v(1.6, 1.8, 2.0), 'wing', 2.2, bevel=.4, group='wing', name='wing')
        s.bone('leg_' + sfx, 'body', (side * 1.2, 1.4, 0))
    if s.age == 2:
        _leaf(s, 'body', (-cw * .3, cup_h + .4, cw * .3), (-cw * .3 - 1.4, cup_h + 2.2, cw * .3 + .4), 1.2, 'tea')
        _leaf(s, 'body', (-cw * .3, cup_h + .4, cw * .3), (-cw * .3 + .4, cup_h + 2.6, cw * .3 + .8), 1.1, 'leaf')
    elif s.age == 1:
        _leaf(s, 'head', (R * .1, cy + R * .44, 0), (R * .1 + 1.2, cy + R * .44 + 1.4, -.3), 1.0)
    return s.finish()


# ---------------------------------------------------------------------- sparrow

def _starling_sparrow(stage):
    s = Pet('starling_sparrow', stage, dict(feather='#8E6246', cap='#3E4E86', belly='#F4E6D2', brow='#FFF2D8',
                                            wing='#74503A', wing_tip='#3E4E86', wing_bar='#F2E2CC', beak='#3A3438',
                                            leg='#C49A86', blush='#F2A08E', star='#FFE08A', cheek='#F4E6D2'),
            kinds=dict(feather='feather', cap='feather', belly='feather', brow='feather', wing='feather',
                       wing_tip='feather', wing_bar='feather', beak='smooth', leg='smooth', star='glow', cheek='feather'))
    v = s.v
    s.scale = v(.8, .84, .88)
    W, H, D = v(4.6, 5.0, 5.4), v(4.2, 4.6, 5.0), v(5.2, 6.4, 7.4)
    cy = v(3.0, 3.4, 3.8)
    b = bird_body(s, W, H, D, cy, belly='belly', legs=(v(1.0, 1.1, 1.1), v(1.6, 2.0, 2.4)), leg_mat='leg',
                  wing_mat='wing', wing_tip='wing_tip', wing_bar='wing_bar',
                  tail=(v(1, 3, 5), v(1.8, 2.6, 3.4), v(1.2, 1.1, 1.1)), tail_mat='wing_tip')
    hw, hh, hd = v(5.4, 5.2, 5.2), v(4.8, 4.6, 4.6), v(4.8, 4.8, 5.0)
    hy = b['top'] + hh * .32
    f = bird_head(s, b, (hw, hh, hd), (hy, b['zf'] + hd * .4),
                  {'size': (v(1.4, 1.5, 1.6), v(1.4, 1.4, 1.5), v(.9, 1.2, 1.4)), 'dy': -.12},
                  {'iris': '#121214', 'iris_light': '#3A3A50', 'w': v(1.8, 1.65, 1.5), 'h': v(2.0, 1.85, 1.7), 'dx': .28})
    s.mark(Mark(slab(1, hy + hh * .2), 'cap', groups=('head',), jitter=.25))
    s.mark(decal(Mark(mirrored(polyline([(f['ex'] - .5, f['ey'] + f['eh'] * .75, f['front']),
                                         (hw / 2 + .2, f['ey'] + f['eh'] * .55, f['front'] + 2.2)], .36)), 'brow',
                      groups=('head',), min_dot=-2), 1))
    s.mark(Mark(slab(1, -1e9, f['ey'] - .9), 'cheek', groups=('head',), jitter=.2))
    # Stars scattered across the breast, more of them as it grows.
    stars = []
    for k in range(v(2, 5, 8)):
        x = math.sin(k * 2.4) * W * .28
        y = cy - H * .12 + math.cos(k * 1.7) * H * .24
        stars.append(either(both(slab(0, x - .42, x + .42), slab(1, y - .13, y + .13)),
                            both(slab(0, x - .13, x + .13), slab(1, y - .42, y + .42))))
    s.mark(decal(Mark(either(*stars), 'star', groups=('body',), facing=(0, 0, -1), min_dot=.3)))
    return s.finish()


# ----------------------------------------------------------------------- toucan

def _papaya_toucan(stage):
    s = Pet('papaya_toucan', stage, dict(feather='#26262E', sheen='#3A4A7A', belly='#FFF4D6', beak='#F7892A',
                                         beak_mid='#FFB23A', beak_tip='#7CC24A', beak_low='#E66A1E', ring='#5AB8E8',
                                         leg='#5A8AC8', blush='#F49C8A', wing='#2E2E3A', wing_tip='#3E5AA0',
                                         tail_red='#E2453A'),
            kinds=dict(feather='feather', sheen='feather', belly='feather', beak='smooth', beak_mid='smooth',
                       beak_tip='smooth', beak_low='smooth', ring='skin', leg='smooth', wing='feather',
                       wing_tip='feather', tail_red='feather'))
    v = s.v
    s.scale = v(1., 1.05, 1.1)
    W, H, D = v(4.8, 5.2, 5.6), v(4.8, 5.4, 6.0), v(5.6, 6.6, 7.6)
    cy = v(3.4, 3.9, 4.4)
    b = bird_body(s, W, H, D, cy, belly='belly', legs=(v(1.2, 1.3, 1.4), v(1.8, 2.2, 2.6)), leg_mat='leg',
                  wing_mat='wing', wing_tip='wing_tip', tail=(v(1, 2, 3), v(2.2, 3.4, 4.6), v(1.8, 1.7, 1.7)),
                  tail_mat='feather')
    s.mark(Mark(slab(1, -1e9, cy - H * .38), 'tail_red', groups=('body',), facing=(0, -.2, -1), min_dot=.3, parts=('breast',)))
    hw, hh, hd = v(5.2, 5.0, 5.0), v(5.0, 4.8, 4.8), v(4.8, 4.8, 5.0)
    hy = b['top'] + hh * .32
    hz = b['zf'] + hd * .4
    s.bone('head', 'body', (0, hy - hh * .4, hz + hd * .2))
    s.rbox('head', (0, hy, hz), (hw, hh, hd), 'feather', 1.0, name='skull')
    front = hz - hd / 2
    s.mark(Mark(slab(1, -1e9, hy - hh * .08), 'belly', groups=('head',), jitter=.2))
    # A raised ivory throat bib between the head and the breast.
    s.rbox('body', (0, b['top'] - .3, b['zf'] + .5), (W * .7, v(1.8, 2.0, 2.2), 1.4), 'belly', .4, name='bib')
    # The papaya bill, longer each stage: orange base, a golden band, a green tip.
    s.bone('beak', 'head', (0, hy - hh * .05, front + .3))
    L = v(2.6, 4.2, 6.0)
    bh = v(2.6, 3.0, 3.4)
    base = np.array([0, hy - hh * .02, front + .4])
    tip = base + np.array([0, -L * .18, -L])
    s.beam('beak', base, tip, v(2.4, 2.5, 2.6), 'beak', bh, bevel=.5, group='snout', name='bill')
    s.beam('beak', base + np.array([0, -bh * .3, 0]), tip + np.array([0, -bh * .25, L * .15]), v(2.0, 2.1, 2.2),
           'beak_low', bh * .4, bevel=.3, group='snout', name='bill_low')
    s.mark(Mark(slab(2, -1e9, base[2] - L * .72), 'beak_tip', groups=('snout',), mats=('beak', 'beak_low')))
    s.mark(Mark(slab(2, base[2] - L * .52, base[2] - L * .38), 'beak_mid', groups=('snout',), mats=('beak',)))
    ex, ey = hw * .3, hy + hh * .14
    s.mark(Mark(mirrored(ellipsoid((ex, ey, front), (1.5, 1.65, 1.4))), 'ring', groups=('head',), facing=(0, 0, -1),
                min_dot=.2))
    s.mark(Eye((ex, ey, front), v(1.8, 1.65, 1.5), v(2.0, 1.85, 1.7), iris='#121214', iris_light='#4A5A7A'))
    _blush(s, hw * .4, ey - 1.8, front, .7, .4)
    if s.age == 2:
        s.mark(Bands((0, -.3, 1), -10, 1.4, .3, 'sheen', groups=('wing',), mats=('wing',)))
    return s.finish()


# ------------------------------------------------------------------------- toad

def _moss_toad(stage):
    s = Pet('moss_toad', stage, dict(skin='#7E8E4E', skin_top='#6A7A40', belly='#E8DCA4', wart='#5E6E36',
                                     moss='#7CB44A', moss_light='#9ACC5E', mouth='#3A3A22', blush='#E8A07A',
                                     cap='#D9433A', cap_dot='#FFF4E4', stalk='#F2E6CC', eye_bump='#7E8E4E'),
            kinds=dict(skin='skin', skin_top='skin', belly='skin', wart='skin', moss='leaf', moss_light='leaf',
                       cap='smooth', cap_dot='plain', stalk='smooth', eye_bump='skin'))
    v = s.v
    s.scale = v(.76, .8, .84)
    W, H, D = v(8.0, 9.6, 11.2), v(4.4, 5.0, 5.6), v(7.6, 8.8, 10.0)
    cy = v(2.8, 3.2, 3.6)
    s.bones['body'] = ('root', [0, cy, 0])
    s.rbox('body', (0, cy, .3), (W, H, D), 'skin', min(W, H, D) * .34, name='body')
    s.bone('head', 'body', (0, cy, -D * .2))
    s.rbox('head', (0, cy + H * .05, -D * .3), (W * .92, H * .82, D * .55), 'skin', H * .3, name='jaw')
    front = -D * .3 - D * .275
    # Bulging eyes on top.
    for side in (-1, 1):
        c = (side * W * .26, cy + H * .45, -D * .3)
        s.rbox('head', c, (v(2.6, 2.8, 3.0), v(2.2, 2.4, 2.6), v(2.4, 2.6, 2.8)), 'eye_bump', .7, name='eye_bump')
    s.mark(Eye((W * .26, cy + H * .5, -D * .3 - v(1.2, 1.3, 1.4)), v(1.9, 1.9, 1.9), v(1.8, 1.8, 1.8), iris='#2A1A10',
               iris_light='#D9A23A', style='round', lash=False, tilt=0))
    s.mark(decal(Mark(polyline([(-W * .4, cy - H * .08, front), (-W * .2, cy - H * .16, front), (W * .2, cy - H * .16, front),
                                (W * .4, cy - H * .08, front)], .17), 'mouth', groups=('head',), facing=(0, 0, -1), min_dot=.5)))
    _blush(s, W * .33, cy + H * .02, front, .9, .4)
    s.mark(Mark(slab(1, -1e9, cy - H * .22), 'belly', groups=('body', 'head'), jitter=.3))
    s.mark(Mark(slab(1, cy + H * .3), 'skin_top', groups=('body',), jitter=.4))
    s.mark(Dots(1.3, .32, 'wart', groups=('body', 'leg'), mats=('skin',), facing=(0, 1, 0), min_dot=-.2, offset=(.2, .3, .1)))
    # Low, folded legs: big haunches at the back, stubby arms in front.
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        s.bone('leg_f' + sfx, 'body', (side * W * .35, cy - H * .2, -D * .25))
        s.rbox('leg_f' + sfx, (side * W * .42, .9, -D * .36), (2.0, 1.8, 2.4), 'skin', .5, group='leg', name='arm')
        s.rbox('leg_f' + sfx, (side * W * .44, .35, -D * .48), (2.4, .7, 1.8), 'skin', .25, group='paw', name='hand')
        s.bone('leg_b' + sfx, 'body', (side * W * .4, cy - H * .1, D * .2))
        s.rbox('leg_b' + sfx, (side * (W * .5 + .2), cy - H * .15, D * .28), (v(2.6, 3.0, 3.4), v(3.0, 3.4, 3.8), v(4.4, 5.0, 5.6)),
               'skin', .8, group='leg', name='haunch')
        s.rbox('leg_b' + sfx, (side * (W * .5 + .6), .4, D * .05), (2.2, .8, 3.2), 'skin', .3, group='paw', name='foot')
    # A moss garden on its back, with mushrooms when grown.
    lumps = v([(0, .2, 1.2)], [(-1.2, .3, 1.4), (1.0, .9, 1.6), (0, -1.4, 1.2)],
              [(-1.8, .2, 1.6), (1.6, .8, 1.8), (.0, -1.6, 1.4), (-.4, 2.4, 1.2), (2.2, -1.8, 1.1)])
    for i, (x, z, r) in enumerate(lumps):
        s.rbox('body', (x, cy + H / 2 + r * .25, .3 + z), (r * 1.8, r * .9, r * 1.6), 'moss' if i % 2 == 0 else 'moss_light',
               r * .3, group='acc', name='moss')
    if s.age >= 1:
        caps = v([], [(1.0, .9)], [(1.6, .8), (-1.8, .2), (.4, 2.4)])
        for k, (x, z) in enumerate(caps):
            base = (x, cy + H / 2 + .6, .3 + z)
            hh_ = v(0, 1.4, 1.8) - k * .3
            s.cube('body', (base[0], base[1] + hh_ / 2, base[2]), (.6, hh_, .6), 'stalk', group='acc', name='stalk')
            s.rbox('body', (base[0], base[1] + hh_ + .3, base[2]), (v(0, 1.6, 2.0) - k * .3, .9, v(0, 1.6, 2.0) - k * .3),
                   'cap', .35 if s.age == 2 else .3, group='acc', name='cap')
        if s.age == 2:
            s.mark(Dots(.65, .17, 'cap_dot', groups=('acc',), parts=('cap',)))
    return s.finish()


# ------------------------------------------------------------------------- frog

def _rain_frog(stage):
    s = Pet('rain_frog', stage, dict(skin='#7FCB8A', skin_top='#62B474', belly='#F2F6DE', mouth='#2E5A3A', blush='#F7A6A0',
                                     pad='#B6E6A8', leaf='#4FA86A', leaf_light='#6EC284', leaf_vein='#3A8A54',
                                     drop='#BFEAFF', stem='#4E8E54'),
            kinds=dict(skin='skin', skin_top='skin', belly='skin', pad='skin', leaf_light='leaf', drop='gem', stem='leaf'))
    v = s.v
    s.scale = v(.8, .84, .88)
    R = v(7.4, 8.4, 9.2)
    W, H, D = R, R * .62, R * .84
    cy = H * .5 + .3
    s.bones['body'] = ('root', [0, cy, 0])
    s.rbox('body', (0, cy, 0), (W, H, D), 'skin', min(W, H, D) * .36, name='body')
    s.bone('head', 'body', (0, cy, -D * .2))
    front = -D / 2
    # Domed eyes on top of the head.
    ey = cy + H * .5
    for side in (-1, 1):
        s.rbox('head', (side * W * .27, ey, -D * .2), (v(2.8, 3.0, 3.2), v(2.6, 2.8, 3.0), v(2.6, 2.8, 3.0)), 'skin', .8,
               name='eye_bump')
    s.mark(Eye((W * .27, ey + .1, -D * .2 - v(1.3, 1.4, 1.5)), v(2.2, 2.2, 2.2), v(2.2, 2.2, 2.2), iris='#141414',
               iris_light='#3A4A3A', lash=False))
    s.mark(decal(Mark(polyline([(-W * .32, cy + H * .02, front), (-W * .12, cy - H * .1, front), (W * .12, cy - H * .1, front),
                                (W * .32, cy + H * .02, front)], .17), 'mouth', groups=('body', 'head'), facing=(0, 0, -1),
                      min_dot=.5)))
    s.mark(decal(Mark(mirrored(ellipsoid((W * .36, cy + H * .1, front), (.9, .45, 1.4))), 'blush', groups=('body', 'head'),
                      facing=(0, 0, -1), min_dot=.3), 1))
    s.mark(Mark(ellipsoid((0, cy - H * .45, front), (W * .4, H * .5, 3.0)), 'belly', groups=('body',), jitter=.3))
    s.mark(Mark(slab(1, -1e9, cy - H * .38), 'belly', groups=('body',), facing=(0, -1, 0), min_dot=.5))
    s.mark(Mark(slab(1, cy + H * .32), 'skin_top', groups=('body',), jitter=.3))
    s.mark(Dots(1.5, .3, 'skin_top', groups=('body',), facing=(0, 1, 0), min_dot=.3, scatter=.4, skip=.3))
    # Little front hands with round toe pads; folded back legs with big feet.
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        nm = 'leg_f' + sfx
        s.bone(nm, 'body', (side * W * .3, cy - H * .2, -D * .3))
        s.rbox(nm, (side * W * .34, .8, -D * .42), (1.6, 1.6, 1.6), 'skin', .45, group='leg', name='arm')
        for k in (-1, 0, 1):
            s.rbox(nm, (side * W * .34 + k * .65, .3, -D * .42 - 1.0 - abs(k) * -.2), (.7, .55, .8), 'pad', .22,
                   group='paw', name='toe_pad')
        nm = 'leg_b' + sfx
        s.bone(nm, 'body', (side * W * .42, cy - H * .1, D * .2))
        s.rbox(nm, (side * (W * .48 + .2), cy - H * .15, D * .16), (v(2.4, 2.6, 2.8), H * .75, D * .5), 'skin', .7,
               group='leg', name='haunch')
        s.rbox(nm, (side * (W * .5 + .5), .35, -D * .12), (2.2, .7, 3.0), 'skin', .3, group='paw', name='foot')
        for k in (-1, 0, 1):
            s.rbox(nm, (side * (W * .5 + .5) + k * .8, .3, -D * .12 - 1.8), (.75, .55, .8), 'pad', .22, group='paw',
                   name='toe_pad')
    # A leaf: a sprout, an umbrella, a broad hat dripping with rain.
    top_y = ey + v(1.2, 1.3, 1.4)
    if s.age == 0:
        _leaf(s, 'head', (0, cy + H / 2, 0), (1.0, cy + H / 2 + 1.6, -.4), 1.1)
    else:
        r = v(0, 3.4, 4.8)
        stem_h = v(0, 1.4, .8)
        s.beam('head', (0, cy + H / 2 - .2, .2), (0, top_y + stem_h, .2), .35, 'stem', group='acc', name='stem')
        hat_y = top_y + stem_h
        n = 8
        for k in range(n):
            a = math.radians(k * 360 / n + 22.5)
            c = (math.cos(a) * r * .5, hat_y - (.0 if s.age == 1 else .25) * (k % 2), .2 + math.sin(a) * r * .5)
            s.cube('head', c, (r * .62, .4, r * .5), 'leaf' if k % 2 else 'leaf_light', (0, -math.degrees(a), -8),
                   (0, hat_y, .2), group='acc', name='leaf_hat')
        s.rbox('head', (0, hat_y + .25, .2), (r * .5, .5, r * .5), 'leaf_light', .2, group='acc', name='leaf_hat')
        s.mark(Mark(either(*[polyline([(0, hat_y + .5, .2), (math.cos(math.radians(k * 45)) * r, hat_y,
                                                              .2 + math.sin(math.radians(k * 45)) * r)], .12) for k in range(8)]),
                    'leaf_vein', groups=('acc',), parts=('leaf_hat',), facing=(0, 1, 0)))
        if s.age == 2:
            for k in range(5):
                a = math.radians(k * 72 + 10)
                c = (math.cos(a) * r * .95, hat_y - .9, .2 + math.sin(a) * r * .95)
                s.rbox('head', c, (.8, 1.1, .8), 'drop', .3, group='acc', name='raindrop')
    return s.finish()


# ------------------------------------------------------------------------ gecko

def _tangerine_gecko(stage):
    s = Pet('tangerine_gecko', stage, dict(skin='#F59A3C', skin_top='#E9852A', belly='#FFF0D6', spot='#8A4422',
                                           band='#FFD99A', mouth='#7A3A1E', blush='#FF9A8A', pad='#FFC890'),
            kinds=dict(skin='skin', skin_top='skin', belly='skin', spot='skin', band='skin', pad='skin'))
    v = s.v
    s.scale = v(.76, .8, .84)
    L, W, H = v(6.4, 8.4, 10.0), v(4.6, 5.2, 5.8), v(2.8, 3.2, 3.6)
    cy = v(2.6, 2.9, 3.2)
    s.bones['body'] = ('root', [0, cy, .6])
    s.rbox('body', (0, cy, .6), (W, H, L), 'skin', min(W, H) * .4, name='body')
    # Splayed legs with round toe pads.
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        for end, z in (('f', .6 - L * .32), ('b', .6 + L * .34)):
            nm = f'leg_{end}{sfx}'
            hip = (side * W * .4, cy - .2, z)
            knee = (side * (W * .5 + v(1.2, 1.5, 1.8)), cy + .2, z - .2)
            s.bone(nm, 'body', hip)
            s.beam(nm, hip, knee, v(1.3, 1.4, 1.5), 'skin', bevel=.25, group='leg', name='upper')
            s.bone(nm + '_lower', nm, knee)
            foot = (knee[0] + side * .3, .5, knee[2] - .5)
            s.beam(nm + '_lower', knee, foot, v(1.2, 1.3, 1.4), 'skin', bevel=.25, group='leg', name='lower')
            for k in (-1, 0, 1):
                a = math.radians(k * 35 + side * (20 if end == 'f' else -40))
                c = (foot[0] + math.sin(a) * 1.0, .3, foot[2] - math.cos(a) * 1.0)
                s.rbox(nm + '_lower', c, (.75, .55, .75), 'pad', .22, group='paw', name='toe_pad')
    hw, hh, hd = v(5.6, 5.8, 6.0), v(3.8, 4.0, 4.2), v(5.0, 5.4, 5.8)
    hy = cy + v(.8, .8, .9)
    hz = .6 - L / 2 - hd * .32
    s.bone('head', 'body', (0, hy - hh * .3, hz + hd * .3))
    s.rbox('head', (0, hy, hz), (hw, hh, hd), 'skin', 1.2, name='skull')
    front = hz - hd / 2
    s.mark(Eye((hw * .32, hy + hh * .18, front + .6), v(2.0, 1.9, 1.8), v(2.2, 2.1, 2.0), iris='#3A2A10',
               iris_light='#E8C25A', slit=True, facing=(.35, .15, -1)))
    s.mark(decal(Mark(polyline([(-hw * .42, hy - hh * .2, front + 1.2), (-hw * .2, hy - hh * .28, front),
                                (hw * .2, hy - hh * .28, front), (hw * .42, hy - hh * .2, front + 1.2)], .15), 'mouth',
                      groups=('head',), min_dot=-2)))
    _blush(s, hw * .36, hy - hh * .12, front, .7, .35)
    s.mark(Mark(slab(1, -1e9, cy - H * .2), 'belly', groups=('body',), jitter=.3))
    s.mark(Mark(slab(1, -1e9, hy - hh * .3), 'belly', groups=('head',), jitter=.25))
    # A thick carrot tail, banded, curling sideways.
    t, tz = cy - .1, .6 + L / 2 - .4
    pts = v([(0, t, tz), (.4, t - .3, tz + 2.2), (1.4, t - .5, tz + 3.8)],
            [(0, t, tz), (.4, t - .3, tz + 2.6), (1.6, t - .6, tz + 4.8), (3.4, t - .8, tz + 6.0)],
            [(0, t, tz), (.4, t - .2, tz + 3.0), (1.8, t - .5, tz + 5.6), (4.0, t - .8, tz + 7.2), (6.2, t - 1.0, tz + 7.4)])
    n = len(pts) - 1
    chain(s, 'tail', pts, [v(3.6, 4.0, 4.4) * (1 - .2 * i) for i in range(n)], 'skin',
          depth=[v(2.6, 3.0, 3.2) * (1 - .2 * i) for i in range(n)])
    s.mark(Bands((0, 0, 1), tz + .8, v(1.6, 1.8, 2.0), .3, 'band', groups=('tail',), facing=(0, 1, 0), min_dot=-.3))
    if s.age >= 1:
        s.mark(Dots(v(0, 1.5, 1.2), v(0, .4, .36), 'spot', groups=('body', 'head', 'tail'), facing=(0, 1, 0), min_dot=.1,
                    offset=(.4, .2, .3), scatter=.45, skip=.25))
    if s.age == 0:
        s.mark(Bands((0, 0, 1), -2, 2.0, .3, 'band', groups=('body',), facing=(0, 1, 0), min_dot=.2))
    return s.finish()


# ----------------------------------------------------------------------- turtle

def _hex(scale, width):
    """Signed distance to a honeycomb's walls in the x/z plane (scutes)."""
    def f(P):
        x, z = P[..., 0] / scale, P[..., 2] / scale
        # Axial hex grid.
        q = (2 / 3) * x
        r = (-1 / 3) * x + (math.sqrt(3) / 3) * z
        s_ = -q - r
        rq, rr, rs = np.round(q), np.round(r), np.round(s_)
        dq, dr, ds = np.abs(rq - q), np.abs(rr - r), np.abs(rs - s_)
        fix_q = (dq > dr) & (dq > ds)
        fix_r = ~fix_q & (dr > ds)
        rq = np.where(fix_q, -rr - rs, rq)
        rr = np.where(fix_r, -rq - rs, rr)
        cx = 1.5 * rq
        cz = math.sqrt(3) * (rr + rq / 2)
        dx, dz = np.abs(x - cx), np.abs(z - cz)
        d = np.maximum(dx * .5 + dz * math.sqrt(3) / 2, dx)  # hex radius-like distance
        return (1 - d) * scale - width
    return f


def _kelp_turtle(stage):
    s = Pet('kelp_turtle', stage, dict(skin='#9CCFA6', skin_top='#84BC90', belly='#F2EED0', shell='#3E9A7C',
                                       shell_light='#62B896', shell_rim='#2E7A62', sand='#E9D6A6', kelp='#6AA84A',
                                       kelp_dark='#4E8A3A', mouth='#2E5A44', blush='#F4A6A0', nail='#E9E2C8'),
            kinds=dict(skin='skin', skin_top='skin', belly='shell', shell='shell', shell_light='shell', shell_rim='shell',
                       sand='stone', kelp='leaf', kelp_dark='leaf', nail='smooth'))
    v = s.v
    s.scale = v(0.874, 0.92, 0.966)
    W, H, D = v(7.4, 9.0, 10.6), v(3.6, 4.4, 5.2), v(8.2, 10.0, 11.8)
    cy = v(2.4, 2.8, 3.2)
    s.bones['body'] = ('root', [0, cy, .5])
    # A domed shell: a rim, then three stepped layers of jade scutes.
    s.rbox('body', (0, cy - H * .28, .5), (W + 1.0, H * .34, D + 1.0), 'shell_rim', .5, name='shell_rim')
    for k, (fw, fh, fy) in enumerate(((1.0, .5, -.02), (.82, .36, .34), (.58, .3, .62))):
        s.rbox('body', (0, cy + H * fy, .5), (W * fw, H * fh + .4, D * (fw + .04)), 'shell_light',
               min(W * fw, H * fh + .4) * .3, name='shell')
    s.rbox('body', (0, cy - H * .48, .5), (W * .9, H * .3, D * .9), 'belly', .4, name='plastron')
    s.mark(Mark(_hex(v(1.7, 2.0, 2.3), .22), 'shell', groups=('body',), mats=('shell_light',), facing=(0, 1, 0), min_dot=-.5))
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        for end, z in (('f', .5 - D * .32), ('b', .5 + D * .32)):
            nm = f'leg_{end}{sfx}'
            s.bone(nm, 'body', (side * W * .4, cy - H * .2, z))
            s.rbox(nm, (side * (W * .5 + .4), .9, z - (.5 if end == 'f' else -.3)), (v(2.2, 2.4, 2.6), 1.8, v(2.8, 3.0, 3.4)),
                   'skin', .6, (0, side * (20 if end == 'f' else -20), 0), group='leg', name='flipper')
            s.cube(nm, (side * (W * .5 + .6), .35, z - 1.8 if end == 'f' else z + 1.2), (1.6, .5, .5), 'nail', group='paw',
                   name='nail')
    hw, hh, hd = v(5.8, 6.0, 6.2), v(4.8, 4.8, 5.0), v(4.8, 5.0, 5.2)
    hy = cy + v(.7, .8, .9)
    hz = .5 - D / 2 - hd * .3
    s.bone('head', 'body', (0, hy - hh * .2, hz + hd * .4))
    s.rbox('head', (0, hy, hz), (hw, hh, hd), 'skin', .55, name='skull')
    front = hz - hd / 2
    s.mark(Eye((hw * .22, hy + hh * .06, front), v(1.8, 1.7, 1.6), v(2.0, 1.9, 1.8), iris='#14201A', iris_light='#3A5A44'))
    s.mark(decal(Mark(polyline([(-1.0, hy - hh * .22, front), (0, hy - hh * .3, front), (1.0, hy - hh * .22, front)], .15),
                      'mouth', groups=('head',), facing=(0, 0, -1), min_dot=.5)))
    _blush(s, hw * .34, hy - hh * .12, front, .75, .38)
    s.mark(Mark(slab(1, hy + hh * .4), 'skin_top', groups=('head',), facing=(0, 1, 0), min_dot=.5))
    s.mark(Dots(1.2, .26, 'skin_top', groups=('leg', 'head'), mats=('skin',), facing=(0, 1, 0), min_dot=.3))
    chain(s, 'tail', [(0, cy - .4, .5 + D / 2 - .4), (0, .6, .5 + D / 2 + 1.4)], 1.4, 'skin')
    # The kelp garden: swaying ribbons rooted in the shell.
    stalks = v([(0, 0, 2.4)], [(-1.4, -.6, 3.0), (1.0, .4, 3.6), (0, 2.0, 2.4), (-.4, -2.4, 2.2)],
               [(-1.8, -1.0, 3.6), (1.2, .2, 4.4), (0, 2.6, 3.0), (-.6, -3.0, 2.8), (2.2, -2.2, 2.6), (-2.4, 2.0, 2.4),
                (.6, -.6, 5.0)])
    top_y = cy + H * .7
    for k, (x, z, h) in enumerate(stalks):
        nm = f'kelp_{k}'
        root = (x, top_y - .2, .5 + z)
        s.bone(nm, 'body', root)
        prev = np.array(root)
        segs = 3
        for j in range(segs):
            nxt = prev + np.array([math.sin(k + j * 1.3) * .5, h / segs, math.cos(k * 2 + j) * .3])
            s.beam(nm, prev, nxt, v(.9, 1.1, 1.2) * (1 - j * .18), 'kelp' if (j + k) % 2 == 0 else 'kelp_dark', .3,
                   group='acc', name='kelp')
            prev = nxt
    if s.age >= 1:
        s.mark(Dots(2.4, .5, 'sand', groups=('body',), parts=('shell_rim',), offset=(.5, .2, .3)))
    return s.finish()
