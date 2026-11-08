"""Pet Atelier rare beasts: kirin, clockwork red panda, sunforge lion, opal chimera, sakura baku,
rune armadillo, obsidian basilisk and ember salamander."""
from __future__ import annotations

import math

import numpy as np

from pet_atelier import (rotation, Pet, Mark, Bands, Eye, Inset, Toward, Dots, quad_legs, chain, ellipsoid, slab, boxed,
                         both, either, minus, mirrored, polyline)
from pet_atelier_parts import *  # noqa: F401,F403
from pet_atelier_parts import _ear, _nose, _smile, _blush, _bow, _leaf, _all, _feather_fan


def crystal(s, bone, base, tip, width, mat, cap=None, group='acc'):
    """A faceted crystal spike: a shaft and a narrower point."""
    base, tip = np.array(base, float), np.array(tip, float)
    mid = base + (tip - base) * .68
    s.beam(bone, base, mid, width, mat, width, group=group, name='crystal')
    s.beam(bone, mid - (tip - base) * .04, tip, width * .55, cap or mat, width * .55, group=group, name='crystal_tip')


def flower5(c, r, axis=2):
    """A five-petal blossom lying in the plane across the given axis (for painting sakura on a coat)."""
    c = np.array(c, float)
    petals = []
    u = [i for i in range(3) if i != axis]
    for k in range(5):
        a = math.radians(90 + k * 72)
        p = c.copy()
        p[u[0]] += math.cos(a) * r * .55
        p[u[1]] += math.sin(a) * r * .55
        rad = [r * .42] * 3
        rad[axis] = 1.6
        petals.append(ellipsoid(p, rad))
    return either(*petals)


def gear(s, bone, c, r, mat, teeth=8, depth=.5, rot=0.0, group='acc', axis='z'):
    """A cog: a hub disc with square teeth around it."""
    x, y, z = c
    if axis == 'z':
        s.rbox(bone, c, (r * 1.5, r * 1.5, depth), mat, r * .3, group=group, name='gear')
    else:
        s.rbox(bone, c, (depth, r * 1.5, r * 1.5), mat, r * .3, group=group, name='gear')
    for k in range(teeth):
        a = math.radians(k * 360 / teeth + rot)
        if axis == 'z':
            p = (x + math.cos(a) * r * .85, y + math.sin(a) * r * .85, z)
            s.cube(bone, p, (r * .38, r * .38, depth * .9), mat, (0, 0, math.degrees(a)), p, group=group, name='gear_tooth')
        else:
            p = (x, y + math.sin(a) * r * .85, z + math.cos(a) * r * .85)
            s.cube(bone, p, (depth * .9, r * .38, r * .38), mat, (math.degrees(a), 0, 0), p, group=group, name='gear_tooth')


# ------------------------------------------------------------------------- kirin

def _prism_kirin(stage):
    s = Pet('prism_kirin', stage, dict(fur='#F7F4FB', fur_top='#ECE6F6', cream='#FFFFFF', inner='#F6C6DA',
                                       nose='#C9A6D6', mouth='#7A5A8A', blush='#F7B0CC', hoof='#E8C25A',
                                       horn='#B9F0FF', horn_tip='#F6D2FF', mane_a='#FFB8D8', mane_b='#C8B4FF',
                                       mane_c='#A6E8FF', mane_d='#B6F2D2', scale_a='#FFD6EA', scale_b='#D6F4FF',
                                       scale_c='#E6DAFF', gold='#E8C25A', whisker='#E6D4F2'),
            kinds=dict(fur_top='fur', hoof='metal', horn='gem', horn_tip='gem', mane_a='hair', mane_b='hair',
                       mane_c='hair', mane_d='hair', scale_a='scale', scale_b='scale', scale_c='scale', whisker='hair'))
    v = s.v
    s.scale = v(.78, .84, .9)
    b = quad_body(s, v(7.4, 10.0, 12.0), v(5.0, 5.4, 5.8), v(4.6, 5.0, 5.4), v(3.6, 5.6, 7.4), v(1.8, 1.9, 2.0),
                  paw='hoof', chest=1.06, rump=1.0, paw_len=v(2.0, 2.1, 2.2),
                  thigh=(v(2.4, 2.8, 3.1), v(2.8, 3.6, 4.2), v(2.8, 3.6, 4.2)))
    nh = v(1.0, 2.4, 3.6)
    s.rbox('body', (0, b['top'] + nh / 2 - .3, b['zf'] + 1.6), (3.2, nh + 1.8, 3.4), 'fur', .7, name='neck')
    f = face(s, b, (v(6.6, 6.4, 6.4), v(5.8, 5.6, 5.6), v(5.8, 6.2, 6.6)), rise=.3 + nh / 5, fwd=v(-.05, 0, .02),
             snout={'size': (v(3.0, 3.0, 3.0), v(2.2, 2.2, 2.3), v(1.4, 2.2, 2.8)), 'mat': 'fur', 'dy': -.24},
             nose={'size': (1.3, .8, .6), 'mat': 'nose'}, mouth='w',
             eyes={'iris': '#4A2A8A', 'iris_light': '#9AE6FF', 'w': v(2.2, 2.0, 1.85), 'h': v(2.5, 2.3, 2.1),
                   'tilt': v(.04, .1, .14), 'style': 'round' if s.age == 0 else 'almond'},
             ears={'w': v(2.2, 2.2, 2.3), 'h': v(2.8, 3.0, 3.2), 'tilt': 40, 'lean': -4, 'x': .34, 'sink': .8})
    hy, hh, hw, hz, sf, sy = f['hy'], f['hh'], f['hw'], f['hz'], f['snout_front'], f['sy']
    # Dragon whiskers trailing from the muzzle.
    if s.age >= 1:
        for side in (-1, 1):
            p0 = np.array([side * f['sw'] * .45, sy + .2, sf + .6])
            p1 = p0 + np.array([side * 1.6, -.6, .8])
            p2 = p1 + np.array([side * 1.2, -1.2, 1.6])
            s.beam('head', p0, p1, .3, 'whisker', group='acc', name='whisker')
            if s.age == 2:
                s.beam('head', p1, p2, .26, 'whisker', group='acc', name='whisker')
    # The crystal horn: a bud, a long spire, then a branching crown.
    s.bone('crown', 'head', (0, hy + hh / 2, hz - .4))
    base = (0, hy + hh / 2 - .3, hz - .6)
    hl = v(1.4, 3.6, 4.6)
    crystal(s, 'crown', base, (0, base[1] + hl, base[2] - .6), v(1.2, 1.1, 1.2), 'horn', 'horn_tip')
    if s.age == 2:
        for side in (-1, 1):
            p = (side * .3, base[1] + hl * .45, base[2] - .3)
            crystal(s, 'crown', p, (side * 1.8, p[1] + 2.0, p[2] + .2), .8, 'horn', 'horn_tip')
            crystal(s, 'crown', (side * .2, base[1] + hl * .7, base[2] - .4), (side * 1.2, base[1] + hl + .6, base[2] + .3),
                    .6, 'horn_tip', 'horn')
    # A flowing rainbow mane down the neck.
    locks = v(3, 5, 8)
    cols = ('mane_a', 'mane_b', 'mane_c', 'mane_d')
    for k in range(locks):
        t = k / max(1, locks - 1)
        root = (0, hy + hh * .3 - t * (hh * .6 + nh * .7), hz + hd_off(f) + t * 2.2)
        tip = (0, root[1] - v(1.4, 2.4, 3.2), root[2] + v(1.6, 2.4, 3.2))
        s.beam('head' if t < .4 else 'body', root, tip, v(2.0, 2.2, 2.6), cols[k % 4], v(1.4, 1.6, 1.8), bevel=.3, group='acc',
               name='mane')
        if s.age == 2:
            s.beam('head' if t < .4 else 'body', root, (tip[0] + (1.2 if k % 2 else -1.2), tip[1] - .6, tip[2] + .6), 1.6,
                   cols[(k + 2) % 4], 1.2, bevel=.25, group='acc', name='mane')
    # Glass scales: soft rainbow sequins scattered along the back and haunches.
    if s.age >= 1:
        for k, col in enumerate(('scale_a', 'scale_b', 'scale_c')):
            s.mark(Dots(v(0, 2.0, 1.6), v(0, .5, .48), col, groups=('body', 'leg'), mats=('fur',), facing=(0, .6, .4),
                        min_dot=-.1, offset=(k * .7, k * .5, k * .9), scatter=.4, skip=.45))
    s.mark(Mark(slab(1, -1e9, 1.0), 'hoof', groups=('paw',)))
    # Fetlock tufts and a prism tail plume.
    for leg in ('leg_fl', 'leg_fr', 'leg_bl', 'leg_br'):
        x = b['W'] * .29 * (-1 if leg[-1] == 'l' else 1)
        z = (b['zf'] + min(2.6, b['L'] * .2)) if leg[4] == 'f' else (b['zb'] - min(2.7, b['L'] * .21))
        s.rbox(leg + '_lower', (x, 1.7, z + .5), (2.3, 1.0, 2.0), cols[('fl', 'fr', 'bl', 'br').index(leg[4:]) % 4], .3,
               group='leg', name='fetlock')
    t0 = (0, b['top'] - .5, b['zb'] - .3)
    pts = v([t0, (0, t0[1] - 1.2, t0[2] + 2.0), (0, t0[1] - .6, t0[2] + 3.4)],
            [t0, (0, t0[1] - 1.6, t0[2] + 2.4), (0, t0[1] - 1.4, t0[2] + 4.6), (0, t0[1] + .2, t0[2] + 6.0)],
            [t0, (0, t0[1] - 2.0, t0[2] + 2.6), (0, t0[1] - 2.2, t0[2] + 5.0), (0, t0[1] - .4, t0[2] + 7.2)])
    names = chain(s, 'tail', pts, 1.1, 'fur')
    end = np.array(pts[-1])
    for k in range(v(2, 3, 5)):
        a = math.radians(-50 + k * 100 / max(1, v(1, 2, 4)))
        tip = end + np.array([math.sin(a) * 1.4, -1.6 - k * .2, 1.4 + math.cos(a) * .8])
        s.beam(names[-1], end, tip, 1.2, cols[k % 4], 1.0, bevel=.2, group='tail', name='tail_plume')
    return s.finish()


def hd_off(f):
    return f['hd'] * .25


# ------------------------------------------------------------- clockwork panda

def _clockwork_red_panda(stage):
    s = Pet('clockwork_red_panda', stage, dict(fur='#C24E2A', fur_top='#A83E1E', dark='#2E1E1C', cream='#FFF2E2',
                                               inner='#FFF2E2', ring='#D9A441', tear='#8E2E16', nose='#1E1414',
                                               mouth='#3A2220', blush='#F49A8A', brass='#D9A441', brass_dark='#A87A2A',
                                               copper='#C07040', steel='#9AA4B2', glass='#9FE2FF', heart='#FF6A5A'),
            kinds=dict(fur_top='fur', ring='metal', tear='fur', brass='metal', brass_dark='metal', copper='metal',
                       steel='metal', glass='gem', heart='glow'))
    v = s.v
    s.scale = v(.76, .8, .84)
    b = quad_body(s, v(8.0, 10.4, 12.4), v(5.8, 6.4, 6.8), v(5.0, 5.4, 5.8), v(2.6, 3.4, 4.0), v(2.1, 2.2, 2.3),
                  paw='dark', thigh=(v(2.6, 2.9, 3.1), v(3.0, 3.4, 3.8), v(3.0, 3.4, 3.8)))
    f = face(s, b, (v(7.8, 7.8, 8.0), v(6.4, 6.2, 6.2), v(5.8, 5.8, 6.0)), rise=v(.3, .38, .42), fwd=v(.05, .12, .16),
             snout={'size': (v(3.4, 3.4, 3.5), v(2.2, 2.3, 2.4), v(1.0, 1.5, 1.9)), 'mat': 'cream'},
             nose={'size': (v(1.4, 1.5, 1.6), v(.9, 1.0, 1.05), .8)},
             eyes={'iris': '#2A1A10', 'iris_light': '#F2B04A', 'tilt': .06},
             ears={'w': v(3.2, 3.4, 3.5), 'h': v(3.0, 3.2, 3.4), 'tilt': 26, 'lean': -4, 'inner': 'cream', 'x': .33},
             cheeks={'size': (v(1.7, 1.8, 1.9), v(2.4, 2.4, 2.5), v(3.4, 3.5, 3.6)), 'mat': 'cream', 'dy': .28})
    hy, hh, hw, front, ex, ey, hz = f['hy'], f['hh'], f['hw'], f['front'], f['ex'], f['ey'], f['hz']
    s.mark(Mark(mirrored(ellipsoid((ex * 1.05, ey + f['eh'] * .85, front), (.75, .5, 1.2))), 'cream', groups=('head',),
                facing=(0, 0, -1)))
    s.mark(Mark(mirrored(polyline([(ex, ey - f['eh'] * .6, front), (ex * .7, f['sy'] - .3, front - .2)], .32)), 'tear',
                groups=('head', 'snout'), facing=(0, 0, -1), min_dot=.4))
    s.mark(Mark(slab(1, -1e9, b['cy'] - b['H'] * .12), 'dark', groups=('body',), jitter=.45))
    s.mark(Mark(_all, 'dark', groups=('leg', 'paw')))
    s.mark(Mark(slab(1, hy + hh / 2 + 2.2), 'dark', groups=('ear',), mats=('fur',)))
    # The wind-up heart: a brass dial with a glowing heart on the chest.
    hc = (0, b['cy'] + b['H'] * .1, b['zf'] - .35)
    gear(s, 'body', hc, v(1.2, 1.5, 1.8), 'brass', teeth=v(6, 8, 10))
    s.rbox('body', (hc[0], hc[1], hc[2] - .35), (v(1.0, 1.2, 1.4),) * 2 + (.4,), 'heart', .2, group='acc', name='heart')
    # The wind-up key on the back (bone 'winder' spins in every clip).
    kc = (0, b['top'] + .2, b['cz'] + .6)
    s.bone('winder', 'body', kc)
    s.beam('winder', kc, (0, kc[1] + v(1.4, 1.8, 2.2), kc[2]), .7, 'brass_dark', group='acc', name='key_stem')
    top = kc[1] + v(1.4, 1.8, 2.2)
    for side in (-1, 1):
        s.rbox('winder', (side * v(.9, 1.1, 1.3), top + .5, kc[2]), (v(1.6, 2.0, 2.4), v(1.6, 2.0, 2.4), .5), 'brass', .4,
               group='acc', name='key_bow')
    if s.age >= 1:
        # Brass bracers on the forelegs, rivets and a copper belt under the fur.
        for leg in ('leg_fl_lower', 'leg_fr_lower'):
            x = b['W'] * .29 * (-1 if 'fl' in leg else 1)
            s.rbox(leg, (x, 2.0, b['zf'] + min(2.6, b['L'] * .2)), (2.6, 1.4, 2.6), 'brass', .3, group='acc', name='bracer')
        s.rbox('body', (0, b['cy'] + .2, b['cz'] + 1.6), (b['W'] * 1.04, 1.2, 1.4), 'copper', .3, group='acc', name='belt')
        s.mark(Dots(.8, .16, 'brass_dark', groups=('acc',), parts=('belt', 'bracer')))
    if s.age == 2:
        # Back gears that turn, and brass goggles pushed up on the brow.
        gear(s, 'body', (b['W'] / 2 + .3, b['cy'] + .8, b['zb'] - 2.6), 1.6, 'brass', teeth=10, axis='x')
        gear(s, 'body', (b['W'] / 2 + .3, b['cy'] - .6, b['zb'] - 1.0), 1.0, 'steel', teeth=8, axis='x', rot=22)
        gy = hy + hh * .36
        s.rbox('head', (0, gy, hz), (hw + .4, .9, f['hd'] + .4), 'copper', .3, group='acc', name='goggle_strap')
        for side in (-1, 1):
            s.rbox('head', (side * 1.6, gy + .2, front - .4), (2.4, 2.0, 1.2), 'brass', .5, group='acc', name='goggle')
            s.rbox('head', (side * 1.6, gy + .2, front - .9), (1.6, 1.3, .4), 'glass', .3, group='acc', name='goggle_lens')
    # Ringed tail with brass bands.
    t, tz = b['top'] - .6, b['zb'] - .4
    pts = v([(0, t, tz), (0, t - 1.4, tz + 2.2), (0, t - .6, tz + 4.2), (0, t + 1.0, tz + 5.4)],
            [(0, t, tz), (0, t - 1.8, tz + 2.4), (0, t - 1.4, tz + 4.8), (0, t + .2, tz + 6.6), (0, t + 2.2, tz + 7.6)],
            [(0, t, tz), (0, t - 2.2, tz + 2.6), (0, t - 2.0, tz + 5.4), (0, t - .4, tz + 7.8), (0, t + 2.0, tz + 9.2),
             (0, t + 4.4, tz + 9.6)])
    n = len(pts) - 1
    chain(s, 'tail', pts, [v(2.8, 3.1, 3.4) * (1 - .05 * i) for i in range(n)], ['fur', 'ring'] * 4)
    return s.finish()


# -------------------------------------------------------------------------- lion

def _sunforge_lion(stage):
    s = Pet('sunforge_lion', stage, dict(fur='#D98A48', fur_top='#C47436', cream='#FFE9C8', inner='#F2B08A',
                                         nose='#7A3A2A', mouth='#5A2A20', blush='#F79A7A', ray='#F7B733', ray2='#F27E2A',
                                         ray3='#FFD86A', gold='#F2C14A', gold_dark='#C4902A', flame='#FF8A2A',
                                         flame2='#FFD24A', flame3='#FF5A2A', claw='#FFF2DA', gem='#FF5A3A'),
            kinds=dict(fur_top='fur', ray='hair', ray2='hair', ray3='hair', gold='metal', gold_dark='metal',
                       flame='glow', flame2='glow', flame3='glow', claw='smooth', gem='gem'))
    v = s.v
    s.scale = v(.78, .84, .9)
    b = quad_body(s, v(8.2, 10.8, 13.0), v(6.0, 6.6, 7.2), v(5.4, 5.8, 6.2), v(3.0, 4.4, 5.6), v(2.4, 2.4, 2.5),
                  paw='cream', chest=1.1, rump=.94, paw_len=v(3.0, 3.0, 3.1),
                  thigh=(v(3.0, 3.3, 3.6), v(3.4, 4.0, 4.6), v(3.4, 4.0, 4.6)))
    f = face(s, b, (v(7.4, 7.0, 7.2), v(6.4, 6.0, 6.2), v(6.0, 6.0, 6.4)), rise=v(.32, .38, .42), fwd=v(.05, .12, .14),
             snout={'size': (v(3.6, 3.6, 3.8), v(2.4, 2.4, 2.6), v(1.2, 1.7, 2.2)), 'mat': 'cream', 'dy': -.26},
             nose={'size': (1.5, 1.0, .8)}, mouth='w',
             eyes={'iris': '#8A3A0A', 'iris_light': '#FFD24A', 'tilt': v(.03, .1, .14),
                   'style': 'round' if s.age == 0 else 'almond'},
             ears={'kind': 'round', 'w': v(2.2, 2.2, 2.3), 'h': v(2.0, 2.0, 2.1), 'd': 1.0, 'tilt': 24, 'x': .34})
    hy, hh, hw, hz, front = f['hy'], f['hh'], f['hw'], f['hz'], f['front']
    s.mark(Mark(slab(1, -1e9, f['ey'] - .8), 'cream', groups=('head',), jitter=.2))
    s.mark(Mark(either(ellipsoid((0, b['cy'], b['zf']), (b['W'] * .36, b['H'] * .6, 2.6)), slab(1, -1e9, b['cy'] - b['H'] * .3)),
                'cream', groups=('body',), jitter=.4))
    s.mark(Mark(slab(1, b['cy'] + b['H'] * .32), 'fur_top', groups=('body',), jitter=.4))
    # The sunburst mane: rays around the face, layered and alternating gold and flame.
    s.bone('mane', 'head', (0, hy, hz))
    rings = v(1, 2, 3)
    for ring in range(rings):
        n = (10, 12, 14)[ring]
        r0 = hw * .42 + ring * .3
        L = v(1.6, 2.4, 3.2) - ring * .5
        for k in range(n):
            a = math.radians(k * 360 / n + ring * 12)
            p0 = (math.cos(a) * r0, hy + math.sin(a) * r0 * .92, hz + .6 + ring * .7)
            p1 = (math.cos(a) * (r0 + L), hy + math.sin(a) * (r0 + L) * .92, hz + 1.2 + ring * .8)
            s.beam('mane', p0, p1, v(1.6, 1.8, 2.0) - ring * .2, ('ray', 'ray2', 'ray3')[(k + ring) % 3], 1.0, bevel=.2,
                   group='acc', name='sun_ray')
    s.rbox('mane', (0, hy - .2, hz + 1.2), (hw + 1.4, hh + 1.2, 2.0), 'ray', .6, group='acc', name='mane_core')
    if s.age >= 1:
        # Molten-gold greaves, then a chest plate with a sun gem.
        for leg in ('leg_fl_lower', 'leg_fr_lower'):
            x = b['W'] * .29 * (-1 if 'fl' in leg else 1)
            s.rbox(leg, (x, 2.2, b['zf'] + min(2.6, b['L'] * .2)), (2.8, 1.6, 2.8), 'gold', .35, group='acc', name='greave')
    if s.age == 2:
        s.rbox('body', (0, b['cy'] + .2, b['zf'] - .3), (b['W'] * .74, b['H'] * .7, 1.0), 'gold', .4, group='acc',
               name='breastplate')
        s.mark(Bands((0, 1, 0), 0, 1.2, .18, 'gold_dark', groups=('acc',), parts=('breastplate',)))
        s.rbox('body', (0, b['cy'] + .4, b['zf'] - .9), (1.4, 1.4, .6), 'gem', .3, group='acc', name='sun_gem')
    # A tail ending in a three-tier flame tuft.
    t, tz = b['top'] - .6, b['zb'] - .4
    pts = v([(0, t, tz), (0, t - 1.4, tz + 2.2), (0, t - .6, tz + 3.8)],
            [(0, t, tz), (0, t - 2.0, tz + 2.6), (0, t - 1.8, tz + 5.0), (0, t - .2, tz + 6.4)],
            [(0, t, tz), (0, t - 2.4, tz + 2.8), (0, t - 2.4, tz + 5.6), (0, t - .6, tz + 7.6), (0, t + 1.4, tz + 8.4)])
    names = chain(s, 'tail', pts, [1.3] * (len(pts) - 1), 'fur')
    end = np.array(pts[-1])
    tiers = v(1, 2, 3)
    for k in range(tiers):
        c = end + np.array([0, .6 + k * 1.0, .4 - k * .2])
        sz = 2.4 - k * .6
        s.rbox(names[-1], c, (sz, sz * 1.2, sz), ('flame3', 'flame', 'flame2')[k], sz * .3, (0, 45, 0), c, group='tail',
               name='tail_flame')
    return s.finish()


# ----------------------------------------------------------------------- chimera

def _opal_chimera(stage):
    s = Pet('opal_chimera', stage, dict(fur='#F6F4F2', fur_top='#E8E4EE', cream='#FFFFFF', mane='#F2EEF8',
                                        mane_tip='#D8C6F4', inner='#F4C6D6', nose='#B88AA6', mouth='#6A4A6A',
                                        blush='#F7B2C8', goat='#EDE6DA', goat_horn='#FFF4E0', horn_line='#D8C6A6',
                                        snake='#8ED8C4', snake_belly='#E6F8EE', snake_eye='#2A1A20', opal='#E6F2FF',
                                        opal_pink='#FFD6EC', opal_cyan='#C6F2FF', opal_violet='#D8CCFF',
                                        tongue='#F26A8A'),
            kinds=dict(fur_top='fur', mane='hair', mane_tip='hair', goat='fur', goat_horn='shell', horn_line='shell',
                       snake='scale', snake_belly='scale', opal='gem', opal_pink='gem', opal_cyan='gem', opal_violet='gem',
                       tongue='skin'))
    v = s.v
    s.scale = v(.78, .84, .9)
    b = quad_body(s, v(8.0, 10.4, 12.4), v(5.8, 6.4, 6.8), v(5.2, 5.6, 6.0), v(2.8, 4.2, 5.4), v(2.2, 2.3, 2.4),
                  paw='cream', chest=1.08, rump=.96, thigh=(v(2.8, 3.1, 3.4), v(3.2, 3.8, 4.4), v(3.2, 3.8, 4.4)))
    f = face(s, b, (v(7.2, 7.0, 7.0), v(6.2, 6.0, 6.0), v(5.8, 5.8, 6.0)), rise=v(.32, .38, .42), fwd=v(.05, .12, .14),
             snout={'size': (v(3.4, 3.4, 3.6), v(2.2, 2.3, 2.4), v(1.1, 1.6, 2.0)), 'mat': 'cream', 'dy': -.26},
             nose={'size': (1.3, .9, .7)}, mouth='w',
             eyes={'iris': '#5A3AA0', 'iris_light': '#9AF0FF', 'tilt': .08},
             ears={'kind': 'round', 'w': 2.2, 'h': 2.0, 'd': 1.0, 'tilt': 24, 'x': .34})
    hy, hh, hw, hz = f['hy'], f['hh'], f['hw'], f['hz']
    # A cloud mane, opal-tipped.
    s.rbox('head', (0, hy - .3, hz + 1.2), (hw + v(1.6, 2.4, 3.0), hh + v(1.2, 1.8, 2.4), 3.2), 'mane', 1.0, group='acc',
           name='mane')
    s.rbox('body', (0, b['top'] - .4, b['zf'] + 1.8), (b['W'] * 1.1, 3.0, 4.0), 'mane', 1.0, name='mane_collar')
    s.mark(Mark(slab(2, hz + 2.2), 'mane_tip', groups=('acc',), parts=('mane',), jitter=.4))
    # The goat: a small head on the shoulder with curling horns.
    gpos = (v(1.6, 2.0, 2.4), b['top'] + v(.8, 1.2, 1.6), b['zf'] + v(3.0, 3.6, 4.2))
    s.bone('head_goat', 'body', gpos)
    gs = v(2.6, 3.2, 3.8)
    s.rbox('head_goat', (gpos[0], gpos[1] + gs * .4, gpos[2] - .4), (gs, gs * .9, gs * 1.1), 'goat', gs * .25, group='head',
           name='goat_head')
    s.rbox('head_goat', (gpos[0], gpos[1] + gs * .25, gpos[2] - gs * .7), (gs * .6, gs * .5, gs * .6), 'goat', gs * .15,
           group='head', name='goat_snout')
    s.mark(Eye((gpos[0] + gs * .25, gpos[1] + gs * .55, gpos[2] - .4 - gs * .55), gs * .28, gs * .3, iris='#4A3A1A',
               iris_light='#E6C24A', facing=(.4, 0, -1)))
    for side in (-1, 1):
        nm = 'goat_horn_' + ('l' if side < 0 else 'r')
        hr = (gpos[0] + side * gs * .3, gpos[1] + gs * .8, gpos[2] - .2)
        s.bone(nm, 'head_goat', hr)
        r = v(.8, 1.2, 1.6)
        prev = np.array(hr)
        for k in range(v(2, 4, 6)):
            a = math.radians(k * 65)
            nxt = np.array([hr[0] + side * (.4 + k * .15), hr[1] + math.sin(a) * r + .6, hr[2] + (1 - math.cos(a)) * r])
            s.beam(nm, prev, nxt, v(.7, .8, .9) * (1 - k * .08), 'goat_horn', group='acc', name='goat_horn')
            prev = nxt
    s.mark(Bands((0, 1, 1), 0, .5, .25, 'horn_line', groups=('acc',), parts=('goat_horn',)))
    # The serpent tail with its own little head.
    t, tz = b['top'] - .8, b['zb'] - .4
    pts = v([(0, t, tz), (0, t - .6, tz + 2.0), (0, t + .8, tz + 3.4)],
            [(0, t, tz), (0, t - 1.0, tz + 2.4), (0, t + .2, tz + 4.4), (0, t + 2.2, tz + 5.4)],
            [(0, t, tz), (0, t - 1.2, tz + 2.6), (0, t + .2, tz + 4.8), (0, t + 2.6, tz + 5.8), (0, t + 4.6, tz + 5.0)])
    names = chain(s, 'tail', pts, [1.4] * (len(pts) - 1), 'snake')
    end = np.array(pts[-1])
    s.bone('head_snake', names[-1], end)
    ss = v(1.8, 2.2, 2.6)
    sc = end + np.array([0, .3, -ss * .4])
    s.rbox('head_snake', sc, (ss, ss * .8, ss * 1.3), 'snake', ss * .25, group='head', name='snake_head')
    s.mark(Eye((ss * .32, sc[1] + ss * .15, sc[2] - ss * .65), ss * .3, ss * .32, iris='#2A1A20', iris_light='#E6C24A',
               slit=True, lash=False))
    s.mark(Mark(slab(1, -1e9, -1e8), 'snake_belly', groups=('tail',)))
    s.mark(Mark(_all, 'snake_belly', groups=('tail',), facing=(0, -1, 0), min_dot=.5))
    if s.age == 2:
        s.beam('head_snake', sc + np.array([0, -ss * .25, -ss * .6]), sc + np.array([0, -ss * .3, -ss * 1.3]), .3, 'tongue',
               group='acc', name='tongue')
    # Opal glass wings.
    rows = v([(64, 2.6, 1.4, (0, 0)), (40, 2.2, 1.3, (0, .3))],
             [(78, 4.8, 1.8, (0, 0)), (60, 4.6, 1.8, (0, .3)), (42, 4.2, 1.7, (0, .6)), (24, 3.6, 1.6, (0, .9))],
             [(84, 7.6, 2.1, (0, 0)), (70, 7.4, 2.1, (0, .25)), (56, 7.0, 2.1, (0, .5)), (42, 6.4, 2.0, (0, .75)),
              (28, 5.6, 1.9, (0, 1.0)), (14, 4.6, 1.8, (0, 1.25))])
    opals = ('opal_pink', 'opal_cyan', 'opal_violet', 'opal')
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        root = (side * b['W'] * .36, b['top'] + .2, b['zf'] + v(2.6, 3.2, 3.8))
        s.bone('wing_' + sfx, 'body', root)
        _feather_fan(s, 'wing_' + sfx, root, side, [r for r in rows if r[0] >= 45], lambda i: opals[i % 4])
        s.bone('wingtip_' + sfx, 'wing_' + sfx, root)
        _feather_fan(s, 'wingtip_' + sfx, root, side, [r for r in rows if r[0] < 45], lambda i: opals[(i + 2) % 4])
    return s.finish()


# -------------------------------------------------------------------------- baku

def _sakura_baku(stage):
    s = Pet('sakura_baku', stage, dict(fur='#FBEFF4', fur_top='#F4DCE6', cream='#FFFFFF', blossom='#F7A6C4',
                                       blossom_core='#FFE08A', petal='#FFC6DA', petal2='#F28AB2', leaf='#A6D88A',
                                       leaf_vein='#7AB466', inner='#F7A6C4', nose='#C46A8A', mouth='#8A4A6A',
                                       blush='#F79AB8', bark='#7A5A5A', hoof='#C88AA6', lotus='#9AD69A'),
            kinds=dict(fur_top='fur', blossom='plain', blossom_core='plain', petal='plain', petal2='plain', bark='wood',
                       hoof='smooth', lotus='leaf'))
    v = s.v
    s.scale = v(.78, .84, .88)
    b = quad_body(s, v(9.0, 11.6, 14.0), v(6.4, 7.0, 7.6), v(5.8, 6.4, 6.8), v(2.6, 3.4, 4.2), v(2.4, 2.5, 2.6),
                  paw='hoof', barrel=True, paw_len=v(2.4, 2.6, 2.8))
    f = face(s, b, (v(6.8, 6.8, 7.0), v(6.0, 5.8, 6.0), v(6.0, 6.4, 6.8)), rise=v(.16, .18, .2), fwd=v(.28, .34, .36),
             eyes={'iris': '#6A2A5A', 'iris_light': '#F7A6D8', 'w': v(2.0, 1.85, 1.7), 'h': v(2.2, 2.05, 1.9),
                   'style': 'sleepy' if s.age == 2 else 'round', 'lid': .2, 'lid_colour': 'fur_top'},
             ears={'kind': 'round', 'w': v(2.8, 3.2, 3.6), 'h': v(2.6, 3.0, 3.4), 'd': .7, 'tilt': 36, 'x': .38,
                   'inner': 'lotus', 'mat': 'lotus'}, mouth=None)
    hy, hh, hw, hz, front = f['hy'], f['hh'], f['hw'], f['hz'], f['front']
    s.mark(decal(Mark(polyline([(-.9, hy - hh * .42, front - .1), (0, hy - hh * .47, front - .1), (.9, hy - hh * .42, front - .1)],
                               .15), 'mouth', groups=('head',), facing=(0, 0, -1), min_dot=.4)))
    # The dream-eating trunk: three joints, curling more with age.
    curl = v(10, 30, 55)
    root = np.array([0, hy - hh * .1, front + .4])
    s.bone('trunk', 'head', root)
    tl = v(1.4, 2.0, 2.4)
    p1 = root + np.array([0, -tl * .4, -tl])
    s.beam('trunk', root, p1, 2.6, 'fur', 2.2, bevel=.45, group='snout', name='trunk')
    s.bone('trunk_1', 'trunk', p1)
    a = math.radians(curl)
    p2 = p1 + np.array([0, -tl * math.cos(a), -tl * math.sin(a) * .8])
    s.beam('trunk_1', p1, p2, 2.2, 'fur', 1.9, bevel=.4, group='snout', name='trunk')
    s.bone('trunk_2', 'trunk_1', p2)
    a2 = math.radians(curl * 2.2)
    p3 = p2 + np.array([0, -tl * .8 * math.cos(a2), tl * .8 * math.sin(a2) * .6])
    s.beam('trunk_2', p2, p3, 1.8, 'fur', 1.6, bevel=.35, group='snout', name='trunk')
    s.rbox('trunk_2', p3, (1.9, 1.4, 1.6), 'nose', .4, group='snout', name='trunk_tip')
    # Sakura blossoms painted over the coat.
    spots = []
    for k in range(v(3, 6, 9)):
        side = 1 if k % 2 else -1
        z = b['zf'] + 1.6 + (k // 2) * b['L'] / v(2.5, 4, 5.5)
        y = b['cy'] + b['H'] * (.1 + .25 * math.sin(k * 1.9))
        spots.append((side * b['W'] / 2, y, z))
    s.mark(Mark(either(*[flower5(p, v(1.2, 1.3, 1.4), axis=0) for p in spots]), 'blossom', groups=('body',),
                facing=(1, 0, 0), min_dot=-2))
    s.mark(decal(Mark(either(*[ellipsoid(p, (1.6, .3, .3)) for p in spots]), 'blossom_core', groups=('body',),
                      facing=(1, 0, 0), min_dot=-2), 1))
    s.mark(Mark(slab(1, -1e9, b['cy'] - b['H'] * .3), 'cream', groups=('body',), jitter=.4))
    # A petal mane, then a blossoming branch along the back.
    n = v(3, 5, 7)
    for k in range(n):
        t = k / max(1, n - 1)
        c = (0, b['top'] + .3 - t * .4, b['zf'] + 1.2 + t * v(2.4, 4.0, 5.6))
        s.rbox('body', c, (1.6, 1.4, 1.6), 'petal' if k % 2 else 'petal2', .4, (0, 45, 0), c, group='acc', name='mane_petal')
    if s.age >= 1:
        bz = b['cz'] + .6
        root = (0, b['top'] + .2, bz)
        s.bone('blossom_branch', 'body', root)
        tip = (v(0, 1.6, 2.4), b['top'] + v(0, 2.4, 3.6), bz + v(0, 2.4, 3.4))
        s.beam('blossom_branch', root, tip, .6, 'bark', group='acc', name='branch')
        clusters = [tip]
        if s.age == 2:
            mid = tuple((a_ + b_) / 2 for a_, b_ in zip(root, tip))
            side_tip = (mid[0] - 2.2, mid[1] + 1.6, mid[2] + .4)
            s.beam('blossom_branch', mid, side_tip, .45, 'bark', group='acc', name='branch')
            clusters.append(side_tip)
            clusters.append(mid)
        for c in clusters:
            for k in range(5):
                a = math.radians(k * 72)
                p = (c[0] + math.cos(a) * .7, c[1] + math.sin(a) * .5 + .3, c[2] + math.sin(a) * .5)
                s.rbox('blossom_branch', p, (.9, .9, .9), 'petal' if k % 2 else 'blossom', .25, group='acc', name='blossom')
            s.rbox('blossom_branch', (c[0], c[1] + .3, c[2]), (.6, .6, .6), 'blossom_core', .15, group='acc', name='blossom')
    chain(s, 'tail', [(0, b['top'] - 1.2, b['zb'] - .4), (0, b['top'] - 2.2, b['zb'] + .8)], v(1.2, 1.3, 1.4), 'fur')
    s.mark(Mark(slab(1, -1e9, 1.0), 'hoof', groups=('paw',)))
    return s.finish()


# --------------------------------------------------------------------- armadillo

def _rune_armadillo(stage):
    s = Pet('rune_armadillo', stage, dict(skin='#E6C2A6', plate='#8A6A52', plate_light='#A8846A', plate_edge='#6A4E3C',
                                          rune='#5AD8FF', copper='#D9844A', nose='#F49AA6', mouth='#5A3A2E',
                                          blush='#F49A8A', claw='#4A3A30', stone='#9A9AA4', stone_dark='#76767E',
                                          belly='#F2DCC6'),
            kinds=dict(skin='skin', plate='stone', plate_light='stone', plate_edge='stone', rune='glow', copper='metal',
                       claw='smooth', stone='stone', stone_dark='stone', belly='skin'))
    v = s.v
    s.scale = v(.78, .82, .86)
    L, W, H, leg = v(7.6, 10.0, 12.4), v(5.6, 6.2, 6.8), v(4.8, 5.4, 6.0), v(1.8, 2.2, 2.6)
    cy, cz = leg + H * .42, .4
    s.rbox('body', (0, cy, cz), (W * .9, H * .9, L), 'skin', 1.0, name='body')
    s.bones['body'] = ('root', [0, cy, cz])
    zf, zb, top = cz - L / 2, cz + L / 2, cy + H / 2
    quad_legs(s, W * .32, zf + 1.6, zb - 1.8, cy - H * .1, cy - H * .1, v(2.0, 2.2, 2.4), mat='skin', paw='skin',
              paw_len=v(2.4, 2.6, 2.8))
    for side in (-1, 1):
        x = side * W * .32
        for k in (-1, 0, 1):
            s.beam('leg_f' + ('l' if side < 0 else 'r') + '_lower', (x + k * .55, .5, zf + 1.6 - 1.0),
                   (x + k * .55, .2, zf + 1.6 - 2.0), .36, 'claw', group='paw', name='claw')
    # Banded armour: a shield over the shoulders, sliding bands, a shield over the hips.
    bands = v(3, 4, 6)
    s.rbox('body', (0, cy + H * .1, zf + L * .2), (W + .8, H * .9, L * .36), 'plate', 1.0, name='scapular')
    for k in range(bands):
        z = zf + L * .38 + k * (L * .3 / bands)
        s.rbox('body', (0, cy + H * .14, z), (W + .9, H * .92, L * .3 / bands + .3), ('plate_light', 'plate')[k % 2], .9,
               name='band')
    s.rbox('body', (0, cy + H * .1, zb - L * .17), (W + .8, H * .9, L * .34), 'plate', 1.0, name='pelvic')
    s.mark(Dots(.95, .22, 'plate_edge', groups=('body',), mats=('plate',), scatter=.2))
    s.mark(Mark(slab(1, -1e9, cy - H * .32), 'belly', groups=('body',), mats=('skin',)))
    # Glowing runes on the armour, more of them with age.
    runes = []
    for k in range(v(2, 4, 7)):
        z = zf + 1.4 + k * (L - 2.8) / max(1, v(1, 3, 6))
        y = cy + H * (.28 + .1 * (k % 2))
        for side in (-1, 1):
            x = side * (W / 2 + .45)
            runes.append(polyline([(x, y + .7, z - .4), (x, y - .6, z - .4), (x, y - .1, z + .4), (x, y + .6, z + .2)], .17))
        runes.append(polyline([(-.6, top + .8, z), (.6, top + .8, z), (0, top + .8, z + .7), (0, top + .8, z - .7)], .17))
    if s.age >= 1:
        s.mark(Mark(either(*runes), 'rune', groups=('body',), mats=('plate', 'plate_light')))
    hw, hh, hd = v(5.2, 5.2, 5.4), v(4.6, 4.6, 4.8), v(5.0, 5.4, 5.8)
    f = face(s, dict(top=top, zf=zf), (hw, hh, hd), rise=-.08, fwd=.32,
             snout={'size': (v(2.4, 2.4, 2.5), v(1.9, 1.9, 2.0), v(1.6, 2.2, 2.8)), 'mat': 'skin', 'dy': -.22},
             nose={'size': (1.3, .9, .6)}, mouth='smile', mat='skin',
             eyes={'iris': '#1E1A2A', 'iris_light': '#5AD8FF', 'w': v(1.8, 1.65, 1.5), 'h': v(2.0, 1.85, 1.7), 'dy': .12},
             ears={'kind': 'round', 'w': v(1.8, 2.0, 2.2), 'h': v(2.2, 2.6, 3.0), 'd': .7, 'tilt': 26, 'x': .34, 'sink': .3,
                   'mat': 'copper', 'inner': 'skin'})
    s.rbox('head', (0, f['hy'] + f['hh'] / 2 - .1, f['hz'] + .4), (hw * .9, 1.2, hd * .8), 'plate', .4, name='head_plate')
    # Segmented armoured tail.
    t, tz = top - 1.6, zb - .4
    n = v(3, 4, 5)
    pts = [(0, t - k * .55, tz + k * v(1.4, 1.6, 1.8)) for k in range(n + 1)]
    chain(s, 'tail', pts, [v(2.2, 2.4, 2.6) * (1 - .14 * k) for k in range(n)], ['plate', 'plate_light'] * 3)
    if s.age == 2:
        # The keystone: a rune pillar strapped to the back.
        kc = (0, top + 1.6, cz)
        s.bone('shell_keystone', 'body', (0, top, cz))
        s.rbox('shell_keystone', (0, top + .4, cz), (3.4, .8, 3.4), 'stone_dark', .3, group='acc', name='keystone_base')
        s.rbox('shell_keystone', kc, (2.2, 3.2, 2.2), 'stone', .4, group='acc', name='keystone')
        s.rbox('shell_keystone', (0, kc[1] + 1.9, cz), (2.8, .7, 2.8), 'stone_dark', .25, group='acc', name='keystone_cap')
        s.mark(Mark(either(polyline([(-.5, kc[1] + .9, cz - 1.15), (0, kc[1] - .9, cz - 1.15), (.5, kc[1] + .9, cz - 1.15)], .17),
                           polyline([(-.6, kc[1], cz - 1.15), (.6, kc[1], cz - 1.15)], .15)), 'rune', groups=('acc',),
                    parts=('keystone',)))
    return s.finish()


# ---------------------------------------------------------------------- basilisk

def _obsidian_basilisk(stage):
    s = Pet('obsidian_basilisk', stage, dict(skin='#2A2A36', skin_top='#22222C', belly='#4A4A5A', vein='#6CFFB0',
                                             crystal='#3EE8A0', crystal_tip='#B8FFE0', spike='#3A3A4A', mouth='#0E0E14',
                                             blush='#7A5A8A', claw='#14141C', hex='#36364A'),
            kinds=dict(skin='scale', skin_top='scale', belly='scale', vein='glow', crystal='gem', crystal_tip='gem',
                       spike='shell', claw='smooth', hex='scale'))
    v = s.v
    s.scale = v(0.897, 0.943, 0.989)
    L, W, H = v(8.0, 11.0, 14.0), v(5.0, 5.6, 6.2), v(3.6, 4.0, 4.4)
    cy, cz = v(3.0, 3.4, 3.8), .4
    s.bones['body'] = ('root', [0, cy, cz])
    s.rbox('body', (0, cy, cz), (W, H, L), 'skin', 1.0, name='body')
    zf, zb, top = cz - L / 2, cz + L / 2, cy + H / 2
    # Six sprawling legs.
    for end, z in (('f', zf + L * .16), ('m', cz), ('b', zb - L * .16)):
        for side in (-1, 1):
            sfx = 'l' if side < 0 else 'r'
            nm = f'leg_{end}{sfx}'
            hip = (side * W * .4, cy - .3, z)
            knee = (side * (W * .5 + v(1.4, 1.7, 2.0)), cy + .3, z)
            foot = (knee[0] + side * .3, .4, z - .4)
            s.bone(nm, 'body', hip)
            s.beam(nm, hip, knee, v(1.3, 1.4, 1.5), 'skin', bevel=.25, group='leg', name='upper')
            s.bone(nm + '_lower', nm, knee)
            s.beam(nm + '_lower', knee, foot, v(1.2, 1.3, 1.4), 'skin', bevel=.25, group='leg', name='lower')
            for k in (-1, 0, 1):
                s.beam(nm + '_lower', (foot[0], .3, foot[2]), (foot[0] + k * .6, .2, foot[2] - 1.0), .35, 'claw',
                       group='paw', name='claw')
    hw, hh, hd = v(5.4, 5.6, 5.8), v(3.8, 4.0, 4.2), v(5.2, 5.8, 6.4)
    hy, hz = cy + .6, zf - hd * .34
    s.bone('head', 'body', (0, hy - .6, hz + hd * .3))
    s.rbox('head', (0, hy, hz), (hw, hh, hd), 'skin', 1.0, name='skull')
    front = hz - hd / 2
    s.mark(Eye((hw * .3, hy + hh * .15, front + .8), v(2.1, 2.0, 1.9), v(2.0, 1.9, 1.8), iris='#0E5A3A', iris_light='#7CFFC0',
               slit=s.age > 0, outline='#060608', facing=(.3, .1, -1), tilt=.15))
    s.mark(decal(Mark(polyline([(-hw * .4, hy - hh * .25, front + 1.0), (0, hy - hh * .3, front), (hw * .4, hy - hh * .25, front + 1.0)],
                               .15), 'vein', groups=('head',), min_dot=-2)))
    # Chin spikes and a crystal crown.
    for k, x in enumerate((-1.4, 0, 1.4)[:v(1, 3, 3)]):
        s.beam('head', (x, hy - hh * .4, front + 1.6 + abs(x) * .3), (x * 1.3, hy - hh * .4 - v(.8, 1.2, 1.6), front + 2.4),
               .6, 'spike', group='acc', name='chin_spike')
    s.bone('crown', 'head', (0, hy + hh / 2, hz))
    n = v(1, 3, 5)
    for k in range(n):
        a = (k - (n - 1) / 2) * 24
        ar = math.radians(a)
        base = (math.sin(ar) * 1.6, hy + hh / 2 - .3, hz + .2 + abs(a) * .02)
        tip = (math.sin(ar) * 2.6, base[1] + v(1.4, 2.0, 2.8) * (1 - abs(k - (n - 1) / 2) * .15), base[2] + .8)
        crystal(s, 'crown', base, tip, v(.9, 1.0, 1.1), 'crystal', 'crystal_tip')
    # Mineral veins glowing through the obsidian and a hexagonal back.
    veins = [polyline([(side * W * .5, cy + H * .3, zf + 1), (side * W * .5, cy - .2, zf + L * .3),
                       (side * W * .5, cy + .5, zf + L * .55), (side * W * .5, cy - .1, zf + L * .85)], .2)
             for side in (-1, 1)]
    s.mark(Mark(either(*veins), 'vein', groups=('body',), facing=(1, 0, 0), min_dot=-2))
    s.mark(Mark(slab(1, -1e9, cy - H * .3), 'belly', groups=('body',)))
    # Dorsal ridge.
    ridge = v(3, 5, 7)
    for k in range(ridge):
        z = zf + 1.2 + k * (L - 2.0) / ridge
        h = v(.8, 1.2, 1.8) * (1 - abs(k - ridge / 2) / ridge)
        s.rbox('body', (0, top + h / 2 - .1, z), (.9, h + .4, 1.4), 'crystal' if k % 2 == 0 and s.age == 2 else 'spike', .25,
               (-20, 0, 0), name='ridge')
    t, tz = cy - .2, zb - .4
    n = v(3, 4, 5)
    pts = [(0, t - k * .3, tz + k * v(1.6, 1.9, 2.2)) for k in range(n + 1)]
    names = chain(s, 'tail', pts, [v(2.4, 2.6, 2.8) * (1 - .16 * k) for k in range(n)], 'skin')
    end = np.array(pts[-1])
    crystal(s, names[-1], end, end + np.array([0, .3, v(1.4, 2.0, 2.6)]), v(1.2, 1.3, 1.5), 'crystal', 'crystal_tip',
            group='tail')
    return s.finish()


# -------------------------------------------------------------------- salamander

def _ember_salamander(stage):
    s = Pet('ember_salamander', stage, dict(skin='#2E2628', skin_top='#241E20', crack='#FF7A2A', crack_hot='#FFC24A',
                                            belly='#5A3A30', gill='#FF6A2A', gill2='#FFB23A', gill3='#FFE07A',
                                            mouth='#FF7A2A', blush='#FF7A5A', rock='#3A3234', flame='#FF8A2A',
                                            flame2='#FFD24A', pad='#5A4446'),
            kinds=dict(skin='skin', skin_top='skin', crack='glow', crack_hot='glow', belly='skin', gill='glow',
                       gill2='glow', gill3='glow', rock='stone', flame='glow', flame2='glow', pad='skin'))
    v = s.v
    s.scale = v(0.975, 1.025, 1.075)
    L, W, H = v(7.0, 9.4, 11.6), v(5.0, 5.6, 6.2), v(3.6, 4.0, 4.4)
    cy, cz = v(2.6, 3.0, 3.4), .4
    s.bones['body'] = ('root', [0, cy, cz])
    s.rbox('body', (0, cy, cz), (W, H, L), 'skin', 1.2, name='body')
    zf, zb, top = cz - L / 2, cz + L / 2, cy + H / 2
    for end, z in (('f', zf + L * .2), ('b', zb - L * .2)):
        for side in (-1, 1):
            sfx = 'l' if side < 0 else 'r'
            nm = f'leg_{end}{sfx}'
            hip = (side * W * .4, cy - .3, z)
            knee = (side * (W * .5 + v(1.2, 1.4, 1.6)), cy + .1, z)
            s.bone(nm, 'body', hip)
            s.beam(nm, hip, knee, v(1.5, 1.6, 1.7), 'skin', bevel=.3, group='leg', name='upper')
            s.bone(nm + '_lower', nm, knee)
            foot = (knee[0], .5, z - .3)
            s.beam(nm + '_lower', knee, foot, v(1.4, 1.5, 1.6), 'skin', bevel=.3, group='leg', name='lower')
            s.rbox(nm + '_lower', (foot[0], .35, foot[2] - .6), (v(2.6, 2.8, 3.0), .7, v(2.2, 2.4, 2.6)), 'pad', .25,
                   group='paw', name='foot')
    hw, hh, hd = v(6.6, 7.0, 7.4), v(4.2, 4.4, 4.6), v(5.4, 5.6, 5.8)
    hy, hz = cy + .5, zf - hd * .32
    s.bone('head', 'body', (0, hy - .5, hz + hd * .3))
    s.rbox('head', (0, hy, hz), (hw, hh, hd), 'skin', 1.2, name='skull')
    front = hz - hd / 2
    s.mark(Eye((hw * .28, hy + hh * .12, front), v(2.2, 2.1, 2.0), v(2.4, 2.3, 2.2), iris='#8A3A0A', iris_light='#FFC24A',
               outline='#0E0A0A'))
    s.mark(decal(Mark(polyline([(-hw * .32, hy - hh * .18, front), (-hw * .1, hy - hh * .28, front), (hw * .1, hy - hh * .28, front),
                                (hw * .32, hy - hh * .18, front)], .16), 'mouth', groups=('head',), facing=(0, 0, -1), min_dot=.5)))
    _blush(s, hw * .36, hy - hh * .05, front, .8, .4)
    # Flame gills: three fronds a side, more layers as it grows.
    for side in (-1, 1):
        nm = 'gill_' + ('l' if side < 0 else 'r')
        root = (side * hw * .48, hy + hh * .1, hz + 1.0)
        s.bone(nm, 'head', root)
        layers = v(1, 2, 3)
        for layer in range(layers):
            for k in range(3):
                a = math.radians(40 - k * 40)
                L_ = v(2.0, 2.6, 3.2) - layer * .5
                tip = (root[0] + side * math.cos(a) * L_, root[1] + math.sin(a) * L_ + layer * .3, root[2] + layer * .6)
                s.beam(nm, root, tip, v(.9, 1.0, 1.1) - layer * .15, ('gill', 'gill2', 'gill3')[(k + layer) % 3], .5,
                       group='acc', name='gill')
    # Lava cracks over the charcoal hide.
    cracks = []
    for k in range(v(3, 5, 7)):
        z = zf + 1.0 + k * (L - 2) / max(1, v(2, 4, 6))
        x = math.sin(k * 2.3) * W * .3
        cracks.append(polyline([(x - .9, top, z - .6), (x, top, z), (x + .7, top, z - .9), (x + .3, top, z + .8)], .18))
    s.mark(Mark(either(*cracks), 'crack', groups=('body',), facing=(0, 1, 0), min_dot=.3))
    s.mark(Mark(slab(1, -1e9, cy - H * .3), 'belly', groups=('body',)))
    s.mark(Dots(1.4, .3, 'crack_hot', groups=('body', 'leg'), facing=(1, 0, 0), min_dot=-2, scatter=.5, skip=.6))
    if s.age == 2:
        # Volcano ridge: dark rock cones with glowing vents.
        for k in range(4):
            z = zf + 2.0 + k * (L - 3.6) / 3
            h = 1.6 + .6 * (k % 2)
            s.rbox('body', (0, top + h / 2 - .2, z), (1.8, h, 1.8), 'rock', .4, name='vent')
            s.rbox('body', (0, top + h, z), (.9, .4, .9), 'crack_hot', .1, name='vent_glow')
    # The kiln tail, flaring at the tip.
    t, tz = cy - .2, zb - .4
    n = v(3, 4, 4)
    pts = [(0, t + k * .1, tz + k * v(1.6, 1.9, 2.2)) for k in range(n + 1)]
    names = chain(s, 'tail', pts, [v(2.8, 3.0, 3.2) * (1 - .16 * k) for k in range(n)], 'skin',
                  depth=[v(2.4, 2.6, 2.8) * (1 - .16 * k) for k in range(n)])
    s.mark(Bands((0, 0, 1), tz, 1.6, .16, 'crack', groups=('tail',), facing=(0, 1, 0), min_dot=-.2))
    if s.age >= 1:
        end = np.array(pts[-1])
        for k, (dy, sz) in enumerate(((.6, 1.6), (1.6, 1.1), (2.4, .7))[:v(0, 2, 3)]):
            c = end + np.array([0, dy, .3])
            s.rbox(names[-1], c, (sz, sz * 1.3, sz), ('flame', 'flame2', 'crack_hot')[k], sz * .25, (0, 45, 0), c,
                   group='tail', name='tail_flame')
    return s.finish()
