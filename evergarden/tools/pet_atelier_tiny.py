"""Pet Atelier: shore and meadow minis (crab, snail, shrimp, dragonfly, firefly, oyster, seal)."""
from __future__ import annotations

import math

import numpy as np

from pet_atelier import (rotation, Pet, Mark, Bands, Eye, Inset, Toward, Dots, chain, ellipsoid, slab, boxed, both,
                         either, minus, mirrored, polyline)
from pet_atelier_parts import *  # noqa: F401,F403
from pet_atelier_parts import _nose, _smile, _blush, _bow, _leaf, _all


def _stalk_eye(s, bone, parent, root, height, r, mat, stalk_mat, iris, iris_light, lean=(0, 0)):
    """An eye on a stalk: a thin stem and a round bulb with a painted eye facing forward."""
    s.bone(bone, parent, root)
    top = (root[0] + lean[0], root[1] + height, root[2] + lean[1])
    s.beam(bone, root, top, r * .45, stalk_mat, group='head', name='eyestalk')
    s.rbox(bone, top, (r * 2, r * 2, r * 2), mat, r * .5, group='head', name='eyebulb')
    s.mark(Eye((abs(top[0]), top[1], top[2] - r), r * 1.5, r * 1.6, iris=iris, iris_light=iris_light))
    return top


def _spiral(cx, cy, z0, turns, r0, r1, width, axis='x'):
    """A painted spiral line on a shell's side (distance to an Archimedean spiral in the y/z plane)."""
    def f(P):
        y, z = P[..., 1] - cy, P[..., 2] - z0
        r = np.hypot(y, z)
        a = np.arctan2(y, z)
        k = (r1 - r0) / (turns * 2 * math.pi)
        best = np.full(r.shape, 1e9)
        for n in range(int(turns) + 2):
            target = r0 + k * (a + math.pi + n * 2 * math.pi)
            best = np.minimum(best, np.abs(r - target))
        return np.where(r < r1 + width, best, 1e9) - width
    return f


# -------------------------------------------------------------------------- crab

def _seashell_crab(stage):
    s = Pet('seashell_crab', stage, dict(shell_skin='#F4A27E', shell_top='#E98A66', belly='#FFE2CC', claw='#F28E6A',
                                         claw_tip='#D9634A', leg='#F4A27E', eyestalk='#F4A27E', eyebulb='#FFF4EC',
                                         conch='#FFF0F2', conch_pink='#F6C2D0', conch_line='#E89AB2', pearl='#FFFFFF',
                                         mouth='#8A3A30', blush='#FF9A9A', wave='#FFD6C2'),
            kinds=dict(shell_skin='shell', shell_top='shell', belly='shell', claw='shell', claw_tip='shell', leg='shell',
                       eyestalk='shell', eyebulb='smooth', conch='shell', conch_pink='shell', conch_line='shell',
                       pearl='gem', wave='shell'))
    v = s.v
    s.scale = v(.8, .84, .88)
    W, H, D = v(6.4, 7.4, 8.4), v(2.8, 3.2, 3.6), v(4.6, 5.2, 5.8)
    cy = v(2.4, 2.8, 3.2)
    s.bones['body'] = ('root', [0, cy, 0])
    s.rbox('body', (0, cy, -.4), (W, H, D), 'shell_skin', min(W, H, D) * .3, name='carapace')
    s.mark(Mark(slab(1, -1e9, cy - H * .2), 'belly', groups=('body',), jitter=.2))
    s.bone('head', 'body', (0, cy, -D / 2))
    front = -.4 - D / 2
    s.mark(decal(Mark(polyline([(-.9, cy - .3, front), (0, cy - .55, front), (.9, cy - .3, front)], .16), 'mouth',
                      groups=('body',), facing=(0, 0, -1), min_dot=.5)))
    s.mark(decal(Mark(mirrored(ellipsoid((W * .3, cy - .1, front), (.7, .4, 1.4))), 'blush', groups=('body',),
                      facing=(0, 0, -1), min_dot=.3), 1))
    er = v(1.2, 1.2, 1.25)
    for side in (-1, 1):
        _stalk_eye(s, 'eyestalk_' + ('l' if side < 0 else 'r'), 'head', (side * W * .2, cy + H * .4, front + .8),
                   v(1.6, 2.0, 2.4), er, 'eyebulb', 'eyestalk', '#1A1416', '#4A3A44', lean=(side * .3, -.3))
    # The spiral conch it wears, coiling higher with age.
    turns = v(1, 2, 3)
    base_y = cy + H * .3
    cz = .8
    for k in range(turns + 1):
        sz = v(5.6, 6.4, 7.2) * (1 - k * .24)
        hh = v(2.4, 2.6, 2.8) * (1 - k * .14)
        y = base_y + sum(v(2.4, 2.6, 2.8) * (1 - j * .14) * .82 for j in range(k)) + hh / 2
        s.rbox('body', (0, y, cz + k * .5), (sz, hh, sz * .95), 'conch' if k % 2 == 0 else 'conch_pink', min(sz, hh) * .3,
               (0, k * 25, 0), (0, y, cz + k * .5), group='acc', name='conch')
    s.mark(Bands((0, 1, .3), base_y, v(2.2, 2.2, 2.3), .14, 'conch_line', groups=('acc',), parts=('conch',)))
    if s.age == 2:
        s.rbox('body', (0, base_y + 1.2, cz - v(0, 0, 3.5)), (1.4, 1.4, 1.0), 'pearl', .4, group='acc', name='pearl')
    # Unequal claws: a little one and a big show claw.
    for side, big in ((-1, False), (1, True)):
        sfx = 'l' if side < 0 else 'r'
        sh = (side * W * .42, cy, -.4 - D * .3)
        s.bone('arm_' + sfx, 'body', sh)
        k = (v(1.4, 1.9, 2.4) if big else v(1.0, 1.2, 1.4))
        el = (side * (W * .5 + 1.0), cy - .2, -.4 - D * .5 - .6)
        s.beam('arm_' + sfx, sh, el, .9, 'claw', group='leg', name='arm')
        c = (el[0] + side * .2, el[1] + k * .3, el[2] - k * .9)
        s.rbox('arm_' + sfx, c, (k * 1.5, k * 1.6, k * 2.0), 'claw', k * .4, group='leg', name='claw')
        s.rbox('arm_' + sfx, (c[0] - side * k * .3, c[1] + k * .45, c[2] - k * 1.4), (k * .7, k * .6, k * 1.4), 'claw_tip',
               k * .2, (0, side * 12, 0), group='leg', name='pincer')
        s.rbox('arm_' + sfx, (c[0] - side * k * .3, c[1] - k * .35, c[2] - k * 1.2), (k * .6, k * .5, k * 1.1), 'claw_tip',
               k * .18, (0, side * 8, 0), group='leg', name='pincer')
        if big and s.age == 2:
            s.mark(Bands((0, 1, 0), 0, .9, .3, 'wave', groups=('leg',), parts=('claw',)))
    # Three pairs of walking legs.
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        for k in range(3):
            nm = f'leg_{sfx}{k}'
            z = -.4 - D * .1 + k * D * .28
            hip = (side * W * .45, cy - .3, z)
            knee = (side * (W * .5 + 1.6), cy + .6, z + k * .2)
            foot = (side * (W * .5 + 2.6), .2, z + k * .4)
            s.bone(nm, 'body', hip)
            s.beam(nm, hip, knee, .7, 'leg', group='leg', name='leg')
            s.bone(nm + '_lower', nm, knee)
            s.beam(nm + '_lower', knee, foot, .6, 'leg', group='leg', name='leg')
    return s.finish()


# ------------------------------------------------------------------------- snail

def _lotus_snail(stage):
    s = Pet('lotus_snail', stage, dict(body='#F4E6CE', body_light='#FFF4E2', shell='#F6B4C8', shell_light='#FFD2DE',
                                       spiral='#D9839E', petal='#FFC2D4', petal_tip='#F27FA0', petal_white='#FFF0F4',
                                       pod='#F2D45A', eyestalk='#F4E6CE', mouth='#8A5A4A', blush='#F49AAA',
                                       dew='#CFF2FF'),
            kinds=dict(body='skin', body_light='skin', shell='shell', shell_light='shell', spiral='shell', petal='plain',
                       petal_tip='plain', petal_white='plain', pod='smooth', eyestalk='skin', dew='gem'))
    v = s.v
    s.scale = v(.8, .84, .88)
    L, W, H = v(8.4, 10.4, 12.4), v(4.0, 4.6, 5.2), v(2.2, 2.6, 3.0)
    s.bones['body'] = ('root', [0, H / 2, 0])
    # A long soft foot, rising into the head at the front.
    s.rbox('body', (0, H / 2, .6), (W, H, L), 'body', min(W, H) * .3, name='foot')
    s.bone('head', 'body', (0, H, -L * .3))
    hw, hh, hd = v(4.6, 4.8, 5.2), v(4.4, 4.6, 4.8), v(3.6, 3.8, 4.0)
    hy = H + hh * .3
    hz = .6 - L / 2 + hd * .4
    s.rbox('head', (0, hy, hz), (hw, hh, hd), 'body', 1.0, name='skull')
    front = hz - hd / 2
    s.mark(Eye((hw * .24, hy + hh * .1, front), v(1.7, 1.6, 1.5), v(1.9, 1.8, 1.7), iris='#2A1A1E', iris_light='#7A4A5A'))
    s.mark(decal(Mark(polyline([(-.8, hy - hh * .25, front), (0, hy - hh * .34, front), (.8, hy - hh * .25, front)], .15),
                      'mouth', groups=('head',), facing=(0, 0, -1), min_dot=.5)))
    _blush(s, hw * .36, hy - hh * .16, front, .7, .38)
    s.mark(Mark(slab(1, -1e9, H * .35), 'body_light', groups=('body',), jitter=.2))
    s.rbox('body', (0, .35, .6 + L * .1), (W + .6, .7, L * .8), 'body_light', .25, name='foot_frill')
    s.rbox('body', (W * .3, H + .4, .6 + L * .42), (.8, .9, .8), 'dew', .25, group='acc', name='dew')
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        root = (side * hw * .25, hy + hh / 2 - .3, hz + .3)
        s.bone('eyestalk_' + sfx, 'head', root)
        top = (root[0] + side * .5, root[1] + v(1.6, 2.2, 2.8), root[2] - .2)
        s.beam('eyestalk_' + sfx, root, top, .55, 'eyestalk', group='head', name='antenna')
        s.rbox('eyestalk_' + sfx, top, (.9, .9, .9), 'body_light', .3, group='head', name='antenna_tip')
    # The spiral shell, lotus pink, with a painted coil on both sides.
    R = v(3.4, 4.4, 5.4)
    sy, sz = H + R * .85, .6 + L * .12
    s.bone('shell', 'body', (0, H, sz))
    for k, (f, dz, dy) in enumerate(((1.0, 0, 0), (.62, -.2, .25), (.34, -.25, .4))):
        s.rbox('shell', (0, sy + R * dy, sz + R * dz), (W * .95 * (1.0 if k == 0 else 1.06), R * 2 * f, R * 2 * f),
               'shell' if k != 1 else 'shell_light', R * f * .5, group='acc', name='shell')
    s.mark(Mark(_spiral(0, sy, sz, v(1.5, 2.0, 2.5), .2, R * .95, .22), 'spiral', groups=('acc',), parts=('shell',),
                facing=(1, 0, 0), min_dot=-2))
    # A lotus blooming on top, more layers as it grows.
    top_y = sy + R
    layers = v(1, 2, 3)
    for layer in range(layers):
        n = 6 + layer * 2
        r = v(1.0, 1.6, 2.2) * (1 - layer * .25) + .6
        for k in range(n):
            a = math.radians(k * 360 / n + layer * 20)
            tilt = 30 - layer * 18 if s.age else 70
            c = (math.cos(a) * r * .5, top_y + .4 + layer * .45, sz + math.sin(a) * r * .5)
            s.cube('shell', c, (r * .55, r * 1.1, .35), ('petal', 'petal_white', 'petal')[layer], (tilt, -math.degrees(a) + 90, 0),
                   c, group='acc', name='lotus_petal')
    s.mark(Mark(slab(1, top_y + .9 + layers * .5), 'petal_tip', groups=('acc',), parts=('lotus_petal',)))
    if s.age == 2:
        s.rbox('shell', (0, top_y + .8, sz), (1.2, .7, 1.2), 'pod', .3, group='acc', name='seed_pod')
        for k in range(3):
            s.rbox('shell', (math.cos(k * 2.1) * R * .8, sy + math.sin(k * 2.1) * R * .6, sz + R * .3), (.6, .8, .6), 'dew',
                   .2, group='acc', name='dew')
    return s.finish()


# ------------------------------------------------------------------------ shrimp

def _coral_shrimp(stage):
    s = Pet('coral_shrimp', stage, dict(shell='#FF9E9A', shell_dark='#F07A80', belly='#FFE0D8', leg='#F7A8A0',
                                        antenna='#F07A80', fan='#FFD6CF', pearl='#FFFFFF', stripe='#FFF0EA',
                                        mouth='#8A3A40', blush='#FF8A9A'),
            kinds=dict(shell='shell', shell_dark='shell', belly='shell', leg='shell', antenna='shell', fan='membrane',
                       pearl='gem', stripe='shell'))
    v = s.v
    s.scale = v(0.96, 1.008, 1.056)
    cy = v(4.0, 4.4, 4.8)
    s.bones['body'] = ('root', [0, cy, 0])
    hw, hh, hd = v(4.6, 4.8, 5.0), v(4.2, 4.4, 4.6), v(5.0, 5.6, 6.2)
    s.rbox('body', (0, cy, -1.0), (hw, hh, hd), 'shell', 1.0, name='cephalothorax')
    s.bone('head', 'body', (0, cy, -1.0 - hd / 2))
    front = -1.0 - hd / 2
    s.rbox('head', (0, cy + hh * .35, front - 1.0), (1.0, .9, v(1.6, 2.2, 2.8)), 'shell_dark', .3, (-12, 0, 0),
           group='snout', name='rostrum')
    s.mark(Eye((hw * .26, cy + hh * .08, front), v(1.8, 1.7, 1.6), v(2.0, 1.9, 1.8), iris='#1A1218', iris_light='#5A3A4A'))
    s.mark(decal(Mark(polyline([(-.7, cy - hh * .26, front), (0, cy - hh * .34, front), (.7, cy - hh * .26, front)], .15),
                      'mouth', groups=('body',), facing=(0, 0, -1), min_dot=.5)))
    s.mark(decal(Mark(mirrored(ellipsoid((hw * .36, cy - hh * .16, front), (.7, .38, 1.4))), 'blush', groups=('body',),
                      facing=(0, 0, -1), min_dot=.3), 1))
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        root = (side * hw * .2, cy + hh * .4, front + .4)
        s.bone('eyestalk_' + sfx, 'head', root)
        s.rbox('eyestalk_' + sfx, (root[0] + side * .3, root[1] + .5, root[2] - .3), (.9, 1.0, .9), 'shell_dark', .25,
               group='head', name='eyestalk')
        s.bone('antenna_' + sfx, 'head', (side * hw * .3, cy + hh * .25, front + .2))
        a0 = np.array([side * hw * .3, cy + hh * .25, front + .2])
        L = v(5, 8, 11)
        pts = [a0, a0 + np.array([side * 1.0, 2.4, -L * .3]), a0 + np.array([side * 2.0, 3.4, L * .2]),
               a0 + np.array([side * 2.6, 2.6, L * .8])]
        for p, q in zip(pts, pts[1:]):
            s.beam('antenna_' + sfx, p, q, .28, 'antenna', group='acc', name='antenna')
    # The curled, banded tail: segments arching down and under, ending in a fan.
    segs = v(4, 5, 6)
    pts = []
    for k in range(segs + 1):
        a = math.radians(-10 + k * (150 / segs))
        r = v(3.6, 4.2, 4.8)
        pts.append((0, cy - r * (1 - math.cos(a)) * .55 + .2, 1.2 + r * math.sin(a) * 1.15))
    widths = [v(4.2, 4.4, 4.6) * (1 - .12 * k) for k in range(segs)]
    names = chain(s, 'tail', pts, widths, ['shell', 'shell_dark'] * 4, depth=[w * .9 for w in widths])
    s.mark(Bands((0, -.4, 1), 0, 1.1, .2, 'stripe', groups=('tail',), facing=(0, 1, 0), min_dot=-.2))
    s.mark(Mark(slab(1, -1e9, cy - 1.0), 'belly', groups=('body',), jitter=.2))
    end = np.array(pts[-1])
    s.bone('tail_fan', names[-1], end)
    fan = v(3, 3, 5)
    for k in range(fan):
        a = math.radians((k - (fan - 1) / 2) * 28)
        tip = end + np.array([math.sin(a) * v(1.8, 2.4, 3.0), -v(1.2, 1.6, 2.0), -math.cos(a) * .6 - .4])
        s.beam('tail_fan', end, tip, v(1.4, 1.6, 1.8), 'fan', .4, group='tail', name='fan')
    if s.age == 2:
        s.mark(Dots(.7, .18, 'pearl', groups=('tail',), parts=('fan',)))
    # Swimmerets along the belly.
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        for k in range(5):
            nm = f'leg_{sfx}{k}'
            z = -2.6 + k * 1.1
            hip = (side * hw * .3, cy - hh * .4, z)
            s.bone(nm, 'body', hip)
            s.beam(nm, hip, (side * hw * .45, cy - hh * .4 - v(1.2, 1.5, 1.8), z - .3), .45, 'leg', group='leg', name='leg')
    return s.finish()


# --------------------------------------------------------------------- dragonfly

def _dew_dragonfly(stage):
    s = Pet('dew_dragonfly', stage, dict(body='#4CC0A0', body_dark='#2E8A74', eye='#7EF2D6', eye_dark='#2AA88A',
                                         wing='#E8FBFF', wing_vein='#9ADCE8', wing_tip='#BFEFF8', leg='#2E5A50',
                                         dew='#D8F6FF', mouth='#1E4A40', blush='#F7A6B4', ring='#F2E66A'),
            kinds=dict(body='shell', body_dark='shell', eye='gem', eye_dark='gem', wing='membrane', wing_vein='membrane',
                       wing_tip='membrane', leg='smooth', dew='gem', ring='metal'))
    v = s.v
    s.scale = v(.8, .84, .88)
    cy = 4.0
    s.bones['body'] = ('root', [0, cy, 0])
    tw = v(3.0, 3.2, 3.4)
    s.rbox('body', (0, cy, 0), (tw, tw, v(3.4, 4.0, 4.6)), 'body', .8, name='thorax')
    # Big jade compound eyes, wrapping the head.
    s.bone('head', 'body', (0, cy, -2.0))
    hw = v(5.4, 5.6, 5.8)
    s.rbox('head', (0, cy + .2, -2.6), (hw * .6, 2.6, 2.4), 'body', .6, name='face')
    for side in (-1, 1):
        s.rbox('head', (side * hw * .28, cy + .7, -2.8), (v(2.8, 2.8, 2.8), v(3.0, 3.0, 3.0), v(2.8, 2.8, 2.8)), 'eye', .8,
               name='compound_eye')
    s.mark(Dots(.6, .14, 'eye_dark', groups=('head',), mats=('eye',)))
    s.mark(Eye((hw * .28, cy + .8, -2.8 - 1.4), v(2.0, 1.9, 1.8), v(2.2, 2.1, 2.0), iris='#0E3A32', iris_light='#2ED0A8',
               outline='#0E2A24'))
    s.mark(decal(Mark(polyline([(-.6, cy - .5, -3.8), (0, cy - .75, -3.8), (.6, cy - .5, -3.8)], .14), 'mouth',
                      groups=('head',), facing=(0, 0, -1), min_dot=.5)))
    # A slim segmented abdomen.
    n = v(3, 4, 5)
    L = v(5.0, 7.0, 9.0)
    pts = [(0, cy - .2, 1.8 + L * k / n) for k in range(n + 1)]
    pts = [(p[0], p[1] - (k / n) ** 2 * .8, p[2]) for k, p in enumerate(pts)]
    chain(s, 'tail', pts, [v(1.6, 1.5, 1.5) * (1 - .1 * k) for k in range(n)], ['body', 'body_dark'] * 3)
    if s.age >= 1:
        s.mark(Bands((0, 0, 1), 1.8, L / n, .18, 'ring', groups=('tail',)))
    # Four clear veined wings, longer with age.
    wl = v(4.0, 6.0, 8.0)
    for name, side, dz, ang in (('wing_fl', -1, -.6, 4), ('wing_fr', 1, -.6, 4), ('wing_bl', -1, .8, -8), ('wing_br', 1, .8, -8)):
        root = (side * tw * .4, cy + tw * .45, dz)
        s.bone(name, 'body', root)
        tip = np.array((side * (tw * .4 + wl), cy + tw * .45 + .6, dz + math.sin(math.radians(ang)) * wl * .4))
        mid = np.array(root) + (tip - np.array(root)) * .55
        s.beam(name, root, mid, v(1.6, 1.8, 2.0), 'wing_vein', .3, group='wing', name='wing')
        s.beam(name, mid - (tip - mid) * .08, tip, v(1.9, 2.2, 2.4), 'wing_vein', .3, group='wing', name='wing')
    # Clear panes inside a darker vein rim, cross-veins, frosted tips.
    s.mark(Inset('wing', faces=('north', 'south', 'east', 'west'), left=.28, right=.28, top=.28, bottom=.28,
                 groups=('wing',)))
    s.mark(Bands((1, 0, 0), 0, 1.3, .1, 'wing_vein', groups=('wing',), facing=(0, 1, 0), min_dot=-2))
    s.mark(Mark(lambda P: (tw * .4 + wl * .84) - np.abs(P[..., 0]), 'wing_tip', groups=('wing',), parts=('xx',)))
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        for k in range(3):
            nm = f'leg_{sfx}{k}'
            hip = (side * tw * .3, cy - tw * .4, -.8 + k * .8)
            s.bone(nm, 'body', hip)
            s.beam(nm, hip, (side * tw * .55, cy - tw * .4 - 1.6, -1.2 + k * 1.0), .35, 'leg', group='leg', name='leg')
    if s.age == 2:
        s.rbox('body', (tw * .3, cy + tw * .55, 0), (.8, 1.0, .8), 'dew', .25, group='acc', name='dew')
    return s.finish()


# ---------------------------------------------------------------------- firefly

def _lantern_firefly(stage):
    s = Pet('lantern_firefly', stage, dict(body='#2F5A44', body_light='#4A7E5E', elytra='#2A4E3E', groove='#1E3A2E',
                                           lantern='#FFD95A', lantern_glow='#FFF2A8', frame='#B9862E', wing='#E6F6E8',
                                           antenna='#2A3E34', pearl='#F4FAFF', leg='#22382C', mouth='#1A2E24',
                                           blush='#F4A6A0', face='#F2D46A'),
            kinds=dict(body='shell', body_light='shell', elytra='shell', groove='shell', lantern='glow',
                       lantern_glow='glow', frame='metal', wing='membrane', antenna='smooth', pearl='gem', leg='smooth',
                       face='skin'))
    v = s.v
    s.scale = v(.8, .84, .88)
    cy = 4.2
    s.bones['body'] = ('root', [0, cy, 0])
    tw = v(3.6, 3.8, 4.0)
    s.rbox('body', (0, cy, -.2), (tw, tw * .9, v(3.4, 3.8, 4.2)), 'body', .8, name='thorax')
    s.bone('head', 'body', (0, cy, -2.0))
    hw, hh, hd = v(4.8, 5.0, 5.2), v(4.0, 4.2, 4.4), v(3.4, 3.6, 3.8)
    s.rbox('head', (0, cy + .3, -2.2 - hd * .3), (hw, hh, hd), 'face', 1.0, name='skull')
    s.rbox('head', (0, cy + hh * .45, -2.2 - hd * .2), (hw * 1.04, 1.4, hd * .9), 'body', .4, name='pronotum')
    front = -2.2 - hd * .3 - hd / 2
    s.mark(Eye((hw * .24, cy + .45, front), v(1.8, 1.7, 1.6), v(2.0, 1.9, 1.8), iris='#14201A', iris_light='#3A6A4E'))
    s.mark(decal(Mark(polyline([(-.7, cy - hh * .22, front), (0, cy - hh * .3, front), (.7, cy - hh * .22, front)], .14),
                      'mouth', groups=('head',), facing=(0, 0, -1), min_dot=.5)))
    _blush(s, hw * .36, cy - hh * .1, front, .7, .38)
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        root = (side * hw * .22, cy + hh * .55, -2.2 - hd * .4)
        s.bone('antenna_' + sfx, 'head', root)
        mid = (root[0] + side * .8, root[1] + v(1.6, 2.0, 2.4), root[2] - .8)
        tip = (mid[0] + side * .9, mid[1] + .9, mid[2] + .2)
        s.beam('antenna_' + sfx, root, mid, .32, 'antenna', group='acc', name='antenna')
        s.beam('antenna_' + sfx, mid, tip, .3, 'antenna', group='acc', name='antenna')
        s.rbox('antenna_' + sfx, tip, (.8, .8, .8), 'pearl', .25, group='acc', name='antenna_pearl')
    # The lantern abdomen: a glowing paper lantern in a brass frame, more ribs as it grows.
    ribs = v(1, 2, 3)
    L = v(3.6, 5.0, 6.4)
    lz = 1.6 + L / 2
    s.bone('tail', 'body', (0, cy - .2, 1.6))
    s.rbox('tail', (0, cy - .4, lz), (v(3.6, 4.0, 4.4), v(3.4, 3.8, 4.2), L), 'lantern', .8, group='acc', name='lantern')
    for k in range(ribs + 1):
        z = 1.6 + .3 + (L - .6) * k / max(1, ribs)
        s.rbox('tail', (0, cy - .4, z), (v(3.9, 4.3, 4.7), v(3.7, 4.1, 4.5), .5), 'frame', .2, group='acc', name='lantern_rib')
    s.mark(Mark(ellipsoid((0, cy - .2, lz), (1.4, 1.2, L * .35)), 'lantern_glow', groups=('acc',), parts=('lantern',)))
    # Grooved wing cases lifted a little, clear wings beneath.
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        root = (side * tw * .25, cy + tw * .45, -1.2)
        s.bone('wing_' + sfx, 'body', root)
        c = (side * (tw * .32), cy + tw * .55, -1.2 + v(2.0, 2.6, 3.2))
        s.rbox('wing_' + sfx, c, (tw * .55, .7, v(4.0, 5.2, 6.4)), 'elytra', .3, (-6, side * -10, side * -14), root,
               group='wing', name='elytron')
        s.bone('wing_membrane_' + sfx, 'body', root)
        if s.age >= 1:
            c2 = (side * (tw * .5 + 1.2), cy + tw * .5, -1.2 + v(0, 2.4, 3.0))
            s.rbox('wing_membrane_' + sfx, c2, (v(0, 3.2, 4.0), .3, v(0, 3.8, 4.8)), 'wing', .2, (0, side * -30, side * -6),
                   root, group='wing', name='membrane')
    s.mark(Bands((1, 0, 0), 0, .7, .2, 'groove', groups=('wing',), mats=('elytra',), facing=(0, 1, 0), min_dot=.3))
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        for k in range(3):
            nm = f'leg_{sfx}{k}'
            hip = (side * tw * .3, cy - tw * .4, -1.2 + k * .9)
            s.bone(nm, 'body', hip)
            s.beam(nm, hip, (side * tw * .6, cy - tw * .4 - 1.8, -1.4 + k * 1.1), .4, 'leg', group='leg', name='leg')
    return s.finish()


# ------------------------------------------------------------------------ oyster

def _pearl_oyster(stage):
    s = Pet('pearl_oyster', stage, dict(shell='#B9A2E0', shell_light='#D6C6F4', shell_edge='#FFF2DE', inner='#FFE9F0',
                                        nacre='#F2E6FF', pearl='#FFFFFF', pearl_shade='#E8ECF8', mouth='#7A5A8A',
                                        blush='#F7AFC8', rib='#A08AD0'),
            kinds=dict(shell='shell', shell_light='shell', shell_edge='shell', inner='smooth', nacre='gem', pearl='gem',
                       pearl_shade='smooth', rib='shell'))
    v = s.v
    s.scale = v(0.92, 0.966, 1.012)
    R = v(5.0, 6.2, 7.4)
    s.bones['body'] = ('root', [0, 1.0, 0])

    def valve(bone, y, hinge, angle=0.0):
        # A fan of radiating ribs with a scalloped cream edge; the upper valve is swung open about the hinge.
        R_ = rotation((angle, 0, 0))
        h = np.array([0, y, hinge[2]])

        def T(p):
            return h + R_ @ (np.array(p, float) - h)
        n = 7
        for k in range(n):
            a = math.radians(-70 + k * 140 / (n - 1))
            tip = (math.sin(a) * R, y, hinge[2] - math.cos(a) * R * 1.05)
            s.beam(bone, T((0, y, hinge[2] - .4)), T(tip), R * .5, ('shell', 'shell_light')[k % 2], .9, group='acc',
                   name='valve')
            s.rbox(bone, T(tip), (R * .38, 1.0, R * .38), 'shell_edge', .3, (angle, 0, 0), T(tip), group='acc',
                   name='valve_edge')
    hinge = (0, 1.0, R * .45)
    s.bone('shell_lower', 'body', hinge)
    valve('shell_lower', .6, hinge)
    s.rbox('shell_lower', (0, 1.1, hinge[2] - R * .5), (R * 1.4, .4, R * 1.2), 'inner', .15, group='acc', name='inner')
    s.bone('shell_upper', 'body', (0, 1.6, hinge[2]))
    valve('shell_upper', 1.6, hinge, v(20, 32, 40))
    # The pearl with a face.
    pr = v(1.6, 2.0, 2.4)
    pc = (0, 1.4 + pr, hinge[2] - R * .5)
    s.bone('head', 'body', pc)
    s.rbox('head', pc, (pr * 2, pr * 2, pr * 2), 'pearl', pr * .5, name='pearl')
    s.mark(Mark(slab(1, -1e9, pc[1] - pr * .3), 'pearl_shade', groups=('head',), jitter=.1))
    front = pc[2] - pr
    s.mark(Eye((pr * .36, pc[1] + pr * .15, front), pr * .55, pr * .62, iris='#3A2A4A', iris_light='#9A7AC0'))
    s.mark(decal(Mark(polyline([(-pr * .3, pc[1] - pr * .3, front), (0, pc[1] - pr * .42, front), (pr * .3, pc[1] - pr * .3, front)],
                               .14), 'mouth', groups=('head',), facing=(0, 0, -1), min_dot=.5)))
    _blush(s, pr * .55, pc[1] - pr * .15, front, pr * .25, pr * .14)
    if s.age == 2:
        for k in range(3):
            a = k * 2.1
            s.rbox('shell_lower', (math.cos(a) * R * .55, 1.2, hinge[2] - R * .5 + math.sin(a) * R * .3), (.8, .8, .8), 'nacre',
                   .25, group='acc', name='seed_pearl')
    return s.finish()


# -------------------------------------------------------------------------- seal

def _bubble_seal(stage):
    s = Pet('bubble_seal', stage, dict(fur='#C8DCEA', fur_top='#AFC6D8', belly='#F2F8FC', spot='#9EB6CA', nose='#3A3E4A',
                                       mouth='#4A505E', blush='#F7AFC0', flipper='#A9C0D4', scarf='#7EC8F0',
                                       scarf_wave='#FFFFFF', bubble='#DDF4FF', bubble_glint='#FFFFFF'),
            kinds=dict(fur_top='fur', belly='fur', spot='fur', flipper='skin', scarf='cloth', scarf_wave='cloth',
                       bubble='gem', bubble_glint='glow'))
    v = s.v
    s.scale = v(.8, .84, .88)
    W, H, L = v(6.4, 7.6, 8.8), v(5.4, 6.4, 7.4), v(7.0, 9.0, 11.0)
    cy = H * .5 + .2
    s.bones['body'] = ('root', [0, cy, 0])
    # A plump teardrop: big chest, tapering hips.
    s.rbox('body', (0, cy, -L * .12), (W, H, L * .7), 'fur', min(W, H) * .3, name='body')
    s.rbox('body', (0, cy - H * .12, L * .28), (W * .74, H * .7, L * .45), 'fur', min(W, H) * .25, name='hips')
    hw, hh, hd = v(6.4, 6.6, 7.0), v(5.6, 5.8, 6.0), v(5.4, 5.6, 6.0)
    hy = cy + H * .32
    hz = -L * .12 - L * .35 + hd * .1
    f = face(s, dict(top=cy + H / 2, zf=-L / 2), (hw, hh, hd), pos=(hy, hz), pivot=(0, hy - hh * .4, hz + hd * .3),
             snout={'size': (v(3.0, 3.2, 3.4), v(1.8, 1.9, 2.0), v(.7, .9, 1.0)), 'mat': 'belly', 'dy': -.24},
             nose={'size': (1.3, .8, .5), 'dy': .45}, mouth='w',
             eyes={'iris': '#12141C', 'iris_light': '#3E4A6A', 'w': v(2.1, 2.0, 1.9), 'h': v(2.3, 2.2, 2.1), 'dx': .22,
                   'dy': .08},
             ears=None, bevel=min(hw, hh, hd) * .1)
    for side in (-1, 1):
        s.rbox('head', (side * f['sw'] * .3, f['sy'] - .1, f['snout_front'] + .45), (f['sw'] * .52, f['sh'] * .9, 1.2),
               'belly', .35, group='snout', name='muzzle_puff')
    s.rbox('body', (0, cy - H * .38, -L * .1), (W * .8, H * .35, L * .6), 'belly', H * .1, name='belly')
    for side in (-1, 1):
        for dy in (.15, -.25):
            s.mark(decal(Mark(ellipsoid((side * 1.15, f['sy'] + dy, f['snout_front']), (.14, .14, .7)), 'spot',
                              groups=('snout',), facing=(0, 0, -1), min_dot=.5)))
    s.mark(Mark(slab(1, -1e9, cy - H * .15), 'belly', groups=('body',), jitter=.3))
    s.mark(Mark(slab(1, -1e9, f['ey'] - 1.2), 'belly', groups=('head',), jitter=.25))
    s.mark(Dots(1.8, .35, 'spot', groups=('body',), mats=('fur',), facing=(0, 1, 0), min_dot=-.2, scatter=.5, skip=.35))
    # Front flippers spread wide; a split tail fluke behind.
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        sh = (side * W * .42, cy - H * .2, -L * .2)
        s.bone('arm_' + sfx, 'body', sh)
        tip = (side * (W * .5 + v(2.0, 2.6, 3.2)), .5, -L * .3)
        s.beam('arm_' + sfx, sh, tip, v(1.8, 2.0, 2.2), 'flipper', .8, bevel=.25, group='leg', name='flipper')
    t0 = (0, cy - H * .2, L * .45)
    s.bone('tail', 'body', t0)
    t1 = (0, .8, L * .45 + v(1.6, 2.0, 2.4))
    s.beam('tail', t0, t1, v(2.6, 2.8, 3.0), 'fur', 2.0, bevel=.4, group='tail', name='tail')
    for side in (-1, 1):
        nm = 'tail_fluke_' + ('l' if side < 0 else 'r')
        s.bone(nm, 'tail', t1)
        tip = (side * v(1.8, 2.4, 3.0), .4, t1[2] + v(1.2, 1.6, 2.0))
        s.beam(nm, t1, tip, v(1.6, 1.8, 2.0), 'flipper', .5, group='tail', name='fluke')
    if s.age >= 1:
        ny = hy - hh / 2 + .1
        s.rbox('body', (0, ny, hz + hd * .3), (W * .92, 1.2, hd * .8), 'scarf', .35, group='acc', name='scarf')
        s.mark(Bands((1, 0, 0), -10, 1.2, .3, 'scarf_wave', groups=('acc',), parts=('scarf',), wobble=.15))
        if s.age == 2:
            bc = (W * .25, ny - 1.6, hz - hd * .1)
            s.rbox('body', bc, (2.0, 2.0, 2.0), 'bubble', .55, group='acc', name='bubble')
            s.mark(decal(Mark(ellipsoid((bc[0] + .45, bc[1] + .45, bc[2] - 1.0), (.3, .3, .6)), 'bubble_glint',
                              groups=('acc',), parts=('bubble',))))
    return s.finish()
