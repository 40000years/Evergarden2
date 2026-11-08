"""Pet Atelier rare sky and sea companions: nebula whale, glacier narwhal, astral seahorse, thunder quetzal,
paper crane, twilight moth dragon, verdant capricorn and lotus mantis."""
from __future__ import annotations

import math

import numpy as np

from pet_atelier import (rotation, Pet, Mark, Bands, Eye, Inset, Toward, Dots, chain, ellipsoid, slab, boxed, both,
                         either, minus, mirrored, polyline)
from pet_atelier_parts import *  # noqa: F401,F403
from pet_atelier_parts import _ear, _nose, _smile, _blush, _bow, _leaf, _all, _feather_fan
from pet_atelier_rare_beasts import crystal
from pet_atelier_critters import bird_body, bird_head


def star(x, y, z, r, axis=1):
    """A four-pointed sparkle in the plane across the given axis."""
    u = [i for i in range(3) if i != axis]
    c = np.array([x, y, z], float)

    def f(P):
        d = P - c
        a, b = np.abs(d[..., u[0]]), np.abs(d[..., u[1]])
        return np.minimum(np.maximum(a / r, b / (r * .28)), np.maximum(a / (r * .28), b / r)) - 1 + 0 * d[..., axis]
    return f


def swimmer(s, length, width, height, cy, mat, belly, segs=3, taper=.62, lift=0.0):
    """A whale-like body: a big front block and a tapering tail chain; returns the tail end."""
    s.bones['body'] = ('root', [0, cy, 0])
    s.rbox('body', (0, cy, -length * .12), (width, height, length * .62), mat, min(width, height) * .3, name='body')
    pts = [(0, cy - height * .05, length * .14)]
    for k in range(1, segs + 1):
        t = k / segs
        pts.append((0, cy - height * .05 + lift * t * t, length * .14 + length * .42 * t))
    widths = [width * (taper ** (k + .5)) for k in range(segs)]
    names = chain(s, 'tail', pts, widths, mat, depth=[height * (taper ** (k + .4)) for k in range(segs)])
    s.mark(Mark(slab(1, -1e9, cy - height * .18), belly, groups=('body', 'tail'), jitter=.3))
    return names, np.array(pts[-1]), -length * .12 - length * .31


def flukes(s, parent, end, span, mat, crescent=False):
    for side in (-1, 1):
        nm = 'tail_fluke_' + ('l' if side < 0 else 'r')
        s.bone(nm, parent, end)
        tip = end + np.array([side * span, .3 if not crescent else span * .35, span * .45])
        s.beam(nm, end, tip, span * .5, mat, .6, bevel=.2, group='tail', name='fluke')
        if crescent:
            tip2 = tip + np.array([-side * span * .2, span * .4, span * .2])
            s.beam(nm, tip, tip2, span * .32, mat, .5, group='tail', name='fluke_tip')


def fins(s, side, root, length, width, mat, tip_mat=None, droop=-25, back=.5, bone='wing', tip_bone=None):
    sfx = 'l' if side < 0 else 'r'
    nm = f'{bone}_{sfx}'
    s.bone(nm, 'body', root)
    root = np.array(root, float)
    mid = root + np.array([side * length * .55, math.sin(math.radians(droop)) * length * .55, back * length * .3])
    s.beam(nm, root, mid, width, mat, .6, bevel=.2, group='wing', name='fin')
    tb = tip_bone or f'{bone}_{sfx}_tip'
    s.bone(tb, nm, mid)
    tip = mid + np.array([side * length * .45, math.sin(math.radians(droop)) * length * .5, back * length * .45])
    s.beam(tb, mid, tip, width * .78, tip_mat or mat, .5, bevel=.18, group='wing', name='fin_tip')
    return tip


# ----------------------------------------------------------------- nebula whale

def _nebula_whale(stage):
    s = Pet('nebula_whale', stage, dict(skin='#3A3E8E', skin_deep='#2A2C6A', belly='#C9B6F2', groove='#A894DE',
                                        nebula='#8A5AC8', nebula_pink='#D86AB8', star='#FFFFFF', star_gold='#FFE08A',
                                        line='#B8C8FF', crystal='#B8E8FF', crystal_tip='#FFE6FF', fin='#4A4EA6',
                                        mouth='#1A1A40', blush='#E88AC8'),
            kinds=dict(skin='skin', skin_deep='skin', belly='skin', groove='skin', nebula='skin', nebula_pink='skin',
                       star='glow', star_gold='glow', line='glow', crystal='gem', crystal_tip='gem', fin='skin'))
    v = s.v
    s.scale = v(.82, .88, .94)
    L, W, H = v(12.0, 16.0, 20.0), v(7.0, 8.4, 9.6), v(6.2, 7.4, 8.4)
    cy = H * .5 + .3
    names, end, front = swimmer(s, L, W, H, cy, 'skin', 'belly', segs=v(2, 3, 3), lift=v(.6, 1.2, 1.8))
    s.bone('head', 'body', (0, cy, front + 2))
    s.rbox('body', (0, cy - H * .32, front + L * .3), (W * .86, H * .4, L * .5), 'belly', H * .12, name='belly')
    for k in range(3):
        c = (math.sin(k * 2.1) * .7, cy + H / 2 + .6 + k * .7, front + 2.6 + math.cos(k * 2.1) * .4)
        s.cube('body', c, (.7, .7, .7), 'star', (45, 0, 45), c, group='acc', name='spout_star')
    s.mark(Bands((1, 0, 0), -W, .9, .3, 'groove', groups=('body', 'tail'), mats=('belly',), facing=(0, -1, 0), min_dot=.5))
    s.mark(Eye((W * .48, cy + H * .02, front + v(2.0, 2.6, 3.0)), v(2.0, 1.9, 1.8), v(2.2, 2.1, 2.0), iris='#1A1A4A',
               iris_light='#9AD6FF', facing=(1, 0, -.35)))
    s.mark(decal(Mark(mirrored(polyline([(W * .5, cy - H * .2, front + 1), (W * .5, cy - H * .25, front + 4.5)], .2)), 'mouth',
                      groups=('body',), facing=(1, 0, 0), min_dot=-2)))
    s.mark(decal(Mark(mirrored(ellipsoid((W * .5, cy - H * .1, front + v(3.2, 4.0, 4.6)), (1.0, .5, 1.0))), 'blush',
                      groups=('body',), min_dot=-2), 1))
    # Nebula clouds and a constellation along the back.
    s.mark(Mark(either(ellipsoid((W * .3, cy + H * .4, front + L * .3), (2.6, 1.8, 3.6)),
                       ellipsoid((-W * .25, cy + H * .35, front + L * .5), (2.2, 1.6, 3.0))), 'nebula', groups=('body', 'tail'),
                jitter=.6))
    s.mark(Mark(ellipsoid((W * .1, cy + H * .45, front + L * .4), (1.6, 1.2, 2.2)), 'nebula_pink', groups=('body',), jitter=.5))
    pts = [(-1.6, cy + H / 2, front + 2.4), (.8, cy + H / 2, front + 4.4), (-.4, cy + H / 2, front + 6.8),
           (1.4, cy + H / 2, front + 8.6), (0, cy + H / 2, front + 11)][:v(3, 4, 5)]
    if s.age >= 1:
        s.mark(Mark(polyline(pts, .12), 'line', groups=('body',), facing=(0, 1, 0), min_dot=.3))
    s.mark(decal(Mark(either(*[star(p[0], p[1], p[2], .7) for p in pts]), 'star_gold', groups=('body',), facing=(0, 1, 0),
                      min_dot=.3)))
    s.mark(Dots(1.4, .16, 'star', groups=('body', 'tail'), mats=('skin',), scatter=.5, skip=.5))
    # Long pectoral fins, layered tail flukes, star crystals and (grown) a nebula crown.
    fl = v(4.0, 6.0, 8.0)
    for side in (-1, 1):
        fins(s, side, (side * W * .45, cy - H * .2, front + L * .2), fl, v(2.2, 2.6, 3.0), 'fin', 'skin_deep', droop=-28)
    flukes(s, names[-1], end, v(3.0, 4.2, 5.4), 'fin', crescent=s.age == 2)
    s.bone('crown', 'body', (0, cy + H / 2, front + L * .3))
    n = v(1, 3, 5)
    for k in range(n):
        z = front + L * .28 + k * 1.6
        x = (k % 2 - .5) * 1.4 if n > 1 else 0
        crystal(s, 'crown', (x, cy + H / 2 - .3, z), (x * 1.2, cy + H / 2 + v(1.4, 2.0, 2.6) - k * .2, z + .4),
                v(1.0, 1.1, 1.2), 'crystal', 'crystal_tip')
    return s.finish()


# --------------------------------------------------------------- glacier narwhal

def _glacier_narwhal(stage):
    s = Pet('glacier_narwhal', stage, dict(skin='#9ED4F0', skin_top='#7CBCE2', belly='#EAF8FF', spot='#6AA8D4',
                                           horn='#3A6ADC', horn_light='#9AC4FF', ice='#DDF6FF', ice_edge='#B8E6FF',
                                           fin='#86C4E8', mouth='#2A4A6A', blush='#F7A6C4', armor='#5A8AE8'),
            kinds=dict(skin='skin', skin_top='skin', belly='skin', spot='skin', horn='gem', horn_light='gem', ice='gem',
                       ice_edge='gem', fin='skin', armor='metal'))
    v = s.v
    s.scale = v(.82, .88, .94)
    L, W, H = v(11.0, 15.0, 19.0), v(5.8, 7.0, 8.0), v(5.2, 6.2, 7.0)
    cy = H * .5 + .3
    names, end, front = swimmer(s, L, W, H, cy, 'skin', 'belly', segs=3, lift=v(.4, .8, 1.2))
    s.bone('head', 'body', (0, cy, front + 2))
    s.mark(Eye((W * .3, cy + H * .1, front), v(2.0, 1.9, 1.8), v(2.2, 2.1, 2.0), iris='#14284A', iris_light='#6AB8FF'))
    s.rbox('body', (0, cy - H * .32, front + L * .3), (W * .84, H * .4, L * .5), 'belly', H * .12, name='belly')
    s.mark(decal(Mark(polyline([(-1.2, cy - H * .18, front), (0, cy - H * .26, front), (1.2, cy - H * .18, front)], .16),
                      'mouth', groups=('body',), facing=(0, 0, -1), min_dot=.5)))
    _blush(s, W * .36, cy - H * .05, front, .9, .45)
    s.mark(Mark(slab(1, cy + H * .25), 'skin_top', groups=('body', 'tail'), jitter=.4))
    s.mark(Dots(1.6, .32, 'spot', groups=('body', 'tail'), facing=(0, 1, 0), min_dot=-.2, scatter=.5, skip=.4))
    # The spiral sapphire horn, longer each stage.
    s.bone('horn', 'head', (0, cy + H * .15, front))
    hl = v(1.4, 5.0, 9.0)
    segs = v(2, 4, 7)
    prev = np.array([0, cy + H * .15, front + .2])
    for k in range(segs):
        nxt = prev + np.array([0, hl * .05, -hl / segs])
        w = v(1.6, 1.5, 1.6) * (1 - k / (segs + 1))
        s.beam('horn', prev, nxt, w, ('horn', 'horn_light')[k % 2], w, group='acc', name='horn')
        prev = nxt
    s.mark(Bands((.6, .2, 1), 0, .9, .3, 'horn_light', groups=('acc',), mats=('horn',)))
    # Crystalline fins and an ice-scaled, layered tail.
    for side in (-1, 1):
        tip = fins(s, side, (side * W * .45, cy - H * .25, front + L * .22), v(3.0, 4.2, 5.4), v(2.0, 2.4, 2.8), 'fin', 'ice',
                   droop=-30, bone='wing', tip_bone='wingtip_' + ('l' if side < 0 else 'r'))
    s.mark(Mark(_all, 'ice_edge', groups=('wing',), facing=(0, -1, 0), min_dot=.4))
    flukes(s, names[-1], end, v(2.8, 3.8, 4.8), 'fin')
    if s.age == 2:
        for k in range(3):
            c = end + np.array([0, .6 + k * .2, -.4 - k * .6])
            s.rbox(names[-1], c, (3.4 - k * .6, .5, 1.2), 'ice', .2, group='tail', name='ice_scale')
        # Sapphire armour along the back.
        for k in range(3):
            z = front + 3 + k * 2.6
            s.rbox('body', (0, cy + H / 2 + .1, z), (W * .5, .8, 2.0), 'armor', .3, (-10, 0, 0), name='armor_plate')
    return s.finish()


# ---------------------------------------------------------------- astral seahorse

def _astral_seahorse(stage):
    s = Pet('astral_seahorse', stage, dict(skin='#B9A2E8', skin_light='#D8C8FA', plate='#A28ADC', belly='#F2E8FF',
                                           fin='#E6D8FF', fin_edge='#9AB8FF', star='#FFE89A', moon='#FFF2C4',
                                           moon_shade='#F2D88A', mouth='#4A3A7A', blush='#F7A6D8', crest='#FFE08A'),
            kinds=dict(skin='scale', skin_light='scale', plate='shell', belly='scale', fin='membrane', fin_edge='membrane',
                       star='glow', moon='glow', moon_shade='smooth', crest='metal'))
    v = s.v
    s.scale = v(.82, .88, .94)
    cy = v(7.0, 8.4, 9.8)
    s.bones['body'] = ('root', [0, cy, 0])
    bw, bh, bd = v(4.4, 5.0, 5.6), v(5.6, 6.8, 8.0), v(4.4, 5.0, 5.6)
    s.rbox('body', (0, cy, .4), (bw, bh, bd), 'skin', 1.2, name='body')
    s.rbox('body', (0, cy - .2, -bd * .2), (bw * .8, bh * .8, bd * .6), 'belly', 1.0, name='belly')
    s.mark(Bands((0, 1, 0), cy - bh, 1.0, .2, 'plate', groups=('body',), mats=('belly',)))
    for k in range(3):
        c = (0, cy + bh * .3 - k * bh * .3, .4 + bd / 2 + .2)
        s.rbox('body', c, (1.2, 1.2, 1.0), 'plate', .3, (0, 0, 45), c, name='dorsal_knob')
    # Head angled forward on a curved neck, with a tube snout.
    hw, hh, hd = v(4.6, 4.8, 5.0), v(4.8, 5.0, 5.2), v(4.8, 5.0, 5.2)
    hy, hz = cy + bh * .5 + hh * .35, -.6
    s.bone('head', 'body', (0, hy - hh * .4, hz + .6))
    s.rbox('head', (0, hy, hz), (hw, hh, hd), 'skin', 1.2, name='skull')
    front = hz - hd / 2
    sl = v(1.4, 2.0, 2.6)
    s.beam('head', (0, hy - hh * .15, front + .6), (0, hy - hh * .3, front - sl), 1.6, 'skin_light', 1.6, bevel=.3,
           group='snout', name='snout')
    s.mark(Eye((hw * .3, hy + hh * .1, front + .4), v(2.0, 1.9, 1.8), v(2.2, 2.1, 2.0), iris='#3A2A7A', iris_light='#C6A6FF',
               facing=(.35, 0, -1)))
    _blush(s, hw * .4, hy - hh * .12, front, .7, .38)
    # The star crown.
    s.bone('crown', 'head', (0, hy + hh / 2, hz))
    n = v(1, 3, 5)
    for k in range(n):
        a = math.radians((k - (n - 1) / 2) * 26)
        base = (math.sin(a) * 1.2, hy + hh / 2 - .2, hz + .4)
        tip = (math.sin(a) * 2.4, base[1] + v(1.2, 1.8, 2.4) * (1 - abs(k - (n - 1) / 2) * .18), base[2] + .6)
        s.beam('crown', base, tip, .6, 'crest', group='acc', name='crown_spike')
        s.rbox('crown', tip, (.9, .9, .9), 'star', .25, (0, 0, 45), tip, group='acc', name='crown_star')
    # The curled tail, wound round a crescent moon.
    turns = v(3, 4, 5)
    pts = [(0, cy - bh * .4, .8)]
    r = v(2.0, 2.4, 2.8)
    for k in range(1, turns + 1):
        a = math.radians(-20 + k * 70)
        pts.append((0, cy - bh * .5 - r * .9 * math.sin(min(a, math.pi / 2)) - (k * .5 if k > 2 else 0),
                    .8 + r * (1 - math.cos(a)) * (1 if k < 4 else -.3)))
    pts = [(0, max(.7, y), z) for (_, y, z) in pts]
    names = chain(s, 'tail', pts, [v(2.6, 3.0, 3.4) * (1 - .14 * k) for k in range(turns)], ['skin', 'plate'] * 3)
    if s.age >= 1:
        end = np.array(pts[-1])
        mc = end + np.array([0, -.2, -1.2])
        for k in range(5):
            a = math.radians(200 + k * 35)
            c = mc + np.array([0, math.sin(a) * 1.4, math.cos(a) * 1.4])
            s.cube(names[-1], c, (.6, .7 - abs(k - 2) * .1, 1.1), 'moon', (math.degrees(a) + 90, 0, 0), c, group='acc',
                   name='moon')
    # A veil dorsal fin and little star fins by the cheeks.
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        root = (side * bw * .35, cy + bh * .2, bd * .5)
        s.bone('wing_' + sfx, 'body', root)
        for k in range(v(2, 3, 4)):
            p = (side * (bw * .35 + .3), cy + bh * .3 - k * 1.1, bd * .55 + k * .2)
            s.beam('wing_' + sfx, p, (p[0] + side * v(1.4, 2.0, 2.6), p[1] + .6, p[2] + v(1.4, 2.0, 2.6)), 1.0, 'fin', .3,
                   group='wing', name='veil')
        s.bone('wingtip_' + sfx, 'head', (side * hw * .5, hy - hh * .1, hz))
        s.beam('wingtip_' + sfx, (side * hw * .5, hy - hh * .1, hz), (side * (hw * .5 + 1.4), hy + .3, hz + .8), .9, 'fin_edge',
               .3, group='wing', name='cheek_fin')
    s.mark(Dots(1.2, .18, 'star', groups=('body', 'tail'), mats=('skin',), scatter=.5, skip=.5))
    return s.finish()


# ------------------------------------------------------------------ quetzal

def _thunder_quetzal(stage):
    s = Pet('thunder_quetzal', stage, dict(feather='#1FA06A', feather_dark='#168050', belly='#FF7A6A', wing='#18905E',
                                           wing_tip='#0E6A46', gold='#F2C84A', crest='#7AE8FF', crest2='#FFE84A',
                                           beak='#F2C84A', leg='#5A6A4A', blush='#FF9A8A', ribbon='#1FB070',
                                           ribbon_tip='#7AE8FF'),
            kinds=dict(feather='feather', feather_dark='feather', belly='feather', wing='feather', wing_tip='feather',
                       gold='metal', crest='glow', crest2='glow', beak='smooth', leg='smooth', ribbon='feather',
                       ribbon_tip='feather'))
    v = s.v
    s.scale = v(.82, .88, .94)
    W, H, D = v(5.0, 5.6, 6.0), v(5.0, 5.6, 6.0), v(5.8, 7.0, 8.0)
    cy = v(3.6, 4.0, 4.4)
    b = bird_body(s, W, H, D, cy, belly='belly', legs=(v(1.1, 1.2, 1.3), v(1.6, 1.9, 2.2)), leg_mat='leg',
                  wing_mat='wing', wing_tip='wing_tip', wing_bar='gold' if s.age == 2 else None, tail=None)
    hw, hh, hd = v(5.4, 5.2, 5.2), v(5.0, 4.8, 4.8), v(4.8, 4.8, 5.0)
    hy = b['top'] + hh * .3
    f = bird_head(s, b, (hw, hh, hd), (hy, b['zf'] + hd * .4),
                  {'size': (v(1.6, 1.7, 1.8), v(1.5, 1.5, 1.6), v(1.0, 1.3, 1.6)), 'dy': -.12},
                  {'iris': '#0E2A1A', 'iris_light': '#5AE8B0', 'w': v(1.9, 1.75, 1.6), 'h': v(2.1, 1.95, 1.8)})
    s.mark(Mark(slab(1, -1e9, b['cy'] - b['H'] * .05), 'belly', groups=('body',), mats=('feather',), jitter=.3))
    # The lightning crest: zigzag plumes.
    s.bone('crown', 'head', (0, hy + hh / 2, f['hz']))
    n = v(2, 3, 5)
    for k in range(n):
        a = (k - (n - 1) / 2) * 18
        ar = math.radians(a)
        p0 = np.array([math.sin(ar) * 1.2, hy + hh / 2 - .3, f['hz'] + .6])
        h = v(1.6, 2.4, 3.2) * (1 - abs(k - (n - 1) / 2) * .15)
        p1 = p0 + np.array([math.sin(ar) * .6, h * .45, .8])
        p2 = p1 + np.array([math.sin(ar) * .4, h * .1, -.6])
        p3 = p2 + np.array([math.sin(ar) * .4, h * .45, .9])
        for a_, b_ in ((p0, p1), (p1, p2), (p2, p3)):
            s.beam('crown', a_, b_, .7, 'crest' if k % 2 else 'crest2', .5, group='acc', name='lightning')
    # Ribbon tail streamers: two, then four, gold-tipped.
    s.bone('tail', 'body', (0, cy + H * .1, b['zb'] - .6))
    streams = v(1, 2, 4)
    L = v(4.0, 8.0, 12.0)
    for k in range(streams):
        x = (k - (streams - 1) / 2) * .9
        pts = [np.array([x, cy + H * .1, b['zb'] - .6])]
        for j in range(1, 4):
            pts.append(pts[0] + np.array([x * .3 * j, -j * L * .1 + math.sin(j + k) * .5, j * L / 3]))
        bone = 'tail'
        for j, (a_, b_) in enumerate(zip(pts, pts[1:])):
            if j > 0:
                bone = s.bone(f'tail_{j}' if j < 3 else 'tail_3', 'tail' if j == 1 else f'tail_{j - 1}', a_) if k == 0 else (
                    f'tail_{j}' if j < 3 else 'tail_3')
            s.beam(bone, a_, b_, 1.2, 'ribbon' if j < 2 else 'ribbon_tip', .4, group='tail', name='streamer')
    s.mark(Mark(slab(2, b['zb'] + L * .8), 'gold', groups=('tail',)))
    # Gold-edged wing feathers when grown.
    return s.finish()


# --------------------------------------------------------------------- crane

def _paper_crane(stage):
    s = Pet('paper_crane', stage, dict(paper='#FBF4E6', paper_shade='#EDE2CC', edge='#E2B54A', ink='#2E3A86',
                                       seal='#D9343A', leg='#3A3A44', beak='#E2B54A', blush='#F2A6A0'),
            kinds=dict(paper='plain', paper_shade='plain', edge='metal', ink='plain', seal='plain', leg='smooth',
                       beak='metal'))
    v = s.v
    s.scale = v(.82, .88, .94)
    cy = v(4.6, 5.4, 6.2)
    s.bones['body'] = ('root', [0, cy, 0])
    # A folded body: crisp, unbevelled planes.
    bw, bh, bd = v(3.6, 4.0, 4.4), v(3.4, 3.8, 4.2), v(5.6, 7.0, 8.4)
    s.cube('body', (0, cy, 0), (bw, bh, bd), 'paper', (0, 45, 0) if False else None, name='body')
    s.cube('body', (0, cy + bh * .45, 0), (bw * .2, bh * .5, bd * .9), 'paper_shade', name='spine_fold')
    s.cube('body', (0, cy - bh * .45, 0), (bw * .7, bh * .3, bd * .8), 'paper_shade', name='keel')
    # The folded neck rising forward in a single crease, then the head.
    s.bone('neck', 'body', (0, cy + bh * .2, -bd * .4))
    nl = v(3.0, 4.6, 6.0)
    n0 = np.array([0, cy + bh * .2, -bd * .4])
    n1 = n0 + np.array([0, nl, -nl * .45])
    s.beam('neck', n0, n1, 1.3, 'paper', 1.3, group='body', name='neck')
    s.bone('head', 'neck', n1)
    hc = n1 + np.array([0, .4, -.6])
    s.cube('head', hc, (v(2.6, 2.6, 2.8), v(2.4, 2.4, 2.6), v(2.8, 3.0, 3.2)), 'paper', name='head')
    s.beam('head', hc + np.array([0, -.2, -1.4]), hc + np.array([0, -.8, -1.4 - v(1.4, 1.8, 2.2)]), .8, 'beak', .7,
           group='snout', name='beak')
    s.cube('head', hc + np.array([0, 1.3, .2]), (1.0, .4, 1.0), 'seal', name='crest')
    s.cube('head', hc + np.array([0, -.9, .3]), (v(2.0, 2.0, 2.2), .5, v(2.2, 2.4, 2.6)), 'paper_shade', name='head_fold')
    s.mark(Eye((.7, hc[1] + .2, hc[2] - v(1.4, 1.5, 1.6)), 1.0, 1.15, iris='#1E2A6A', iris_light='#5A6AD8', style='round',
               lash=False))
    # Folded wings: two stacked triangular planes per side, raised.
    span = v(4.0, 6.0, 8.0)
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        root = np.array([side * bw * .45, cy + bh * .3, -bd * .1])
        s.bone('wing_' + sfx, 'body', root)
        mid = root + np.array([side * span * .5, span * .35, bd * .1])
        s.beam('wing_' + sfx, root, mid, .35, 'edge', bd * .7, group='wing', name='wing_plane')
        s.bone('wingtip_' + sfx, 'wing_' + sfx, mid)
        tip = mid + np.array([side * span * .5, span * .15, bd * .25])
        s.beam('wingtip_' + sfx, mid, tip, .3, 'edge', bd * .45, group='wing', name='wing_plane')
    # Folded flanks, an inner fold on each wing and a gilt leading edge on every plane.
    for side in (-1, 1):
        c = (side * bw * .42, cy, 0)
        s.cube('body', c, (.4, bh * .8, bd * .8), 'paper_shade', (0, 0, side * 18), c, name='flank_fold')
        sfx = 'l' if side < 0 else 'r'
        root = np.array([side * bw * .45, cy + bh * .3, -bd * .1])
        mid = root + np.array([side * span * .5, span * .35, bd * .1])
        tip = mid + np.array([side * span * .5, span * .15, bd * .25])
        s.beam('wing_' + sfx, root + np.array([0, -.3, .4]), mid + np.array([0, -.5, .6]), .3, 'paper_shade', bd * .45,
               group='wing', name='wing_fold')
        s.beam('wing_' + sfx, root + np.array([0, .25, -bd * .32]), mid + np.array([0, .25, -bd * .3]), .45, 'edge', .45,
               group='wing', name='wing_edge')
        s.beam('wingtip_' + sfx, mid + np.array([0, .2, -bd * .2]), tip + np.array([0, .2, -bd * .2]), .4, 'edge', .4,
               group='wing', name='wing_edge')
    s.cube('body', (bw * .51, cy - .2, bd * .2), (.2, 1.2, 1.2), 'seal', name='seal_stamp')
    s.cube('body', (0, cy - bh * .6, -bd * .2), (bw * .4, bh * .25, bd * .35), 'paper_shade', name='keel_fold')
    # Gold edges and indigo ink: a paper pane inside a gilt rim, and a few brush strokes.
    s.mark(Inset('paper', faces=('east', 'west', 'north', 'south'), left=.32, right=.32, top=.32, bottom=.32,
                 groups=('wing',)))
    if s.age >= 1:
        s.mark(Mark(either(*[polyline([(side * (bw + span * .4), cy + span * .3, -1), (side * (bw + span * .55), cy + span * .4, 1)],
                                      .3) for side in (-1, 1)]), 'ink', groups=('wing',)))
    if s.age == 2:
        s.mark(Mark(ellipsoid((0, cy, bd * .3), (3, 1.0, 1.0)), 'seal', groups=('body',), facing=(1, 0, 0), min_dot=-2))
    # The tail tuft with a seal stamp.
    s.bone('tail', 'body', (0, cy + bh * .2, bd * .45))
    t0 = np.array([0, cy + bh * .2, bd * .45])
    s.beam('tail', t0, t0 + np.array([0, v(1.4, 2.0, 2.6), v(2.0, 3.0, 4.0)]), 1.4, 'paper', 1.4, group='tail', name='tail')
    s.beam('tail', t0, t0 + np.array([-.8, v(.8, 1.2, 1.6), v(1.8, 2.6, 3.4)]), 1.0, 'paper_shade', 1.0, group='tail',
           name='tail')
    s.bone('tail_r', 'tail', t0)
    s.beam('tail_r', t0, t0 + np.array([.8, v(.6, 1.0, 1.4), v(1.6, 2.4, 3.2)]), 1.0, 'paper_shade', 1.0, group='tail',
           name='tail')
    # Thin legs.
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        hip = (side * .8, cy - bh * .4, .2)
        s.bone('leg_' + sfx, 'body', hip)
        knee = (side * .8, cy * .5, .4)
        s.beam('leg_' + sfx, hip, knee, .45, 'leg', group='leg', name='leg')
        s.bone('leg_' + sfx + '_lower', 'leg_' + sfx, knee)
        s.beam('leg_' + sfx + '_lower', knee, (side * .8, .2, .1), .4, 'leg', group='leg', name='leg')
        for k in (-1, 0, 1):
            s.beam('leg_' + sfx + '_lower', (side * .8, .15, .1), (side * .8 + k * .6, .15, -1.0), .3, 'leg', .3, group='paw',
                   name='toe')
    return s.finish()


# ---------------------------------------------------------- twilight moth dragon

def _twilight_moth_dragon(stage):
    s = Pet('twilight_moth_dragon', stage, dict(fur='#8E7AD0', fur_light='#B8A8F0', fluff='#EDE6FF', wing='#5A4AA6',
                                                wing_edge='#2E2668', eye_spot='#FFE08A', eye_core='#2E2668',
                                                eye_ring='#F2A6E6', star='#FFFFFF', antenna='#EDE6FF', moon='#FFF2B8',
                                                inner='#D8C8FF', nose='#5A3A8A', mouth='#3A2A6A', blush='#F2A6D8',
                                                horn='#FFF2C8'),
            kinds=dict(fur_light='fur', fluff='fur', wing='membrane', wing_edge='membrane', eye_spot='glow',
                       eye_core='plain', eye_ring='glow', star='glow', antenna='hair', moon='glow', horn='smooth'))
    v = s.v
    s.scale = v(.82, .88, .94)
    W, H, D = v(5.8, 6.4, 7.0), v(5.6, 6.2, 6.8), v(6.0, 7.0, 8.0)
    cy = v(4.4, 4.8, 5.2)
    s.bones['body'] = ('root', [0, cy, 0])
    s.rbox('body', (0, cy, 0), (W, H, D), 'fur', min(W, H) * .3, name='body')
    s.rbox('body', (0, cy + H * .42, -D * .3), (W * 1.04, 2.0, 2.6), 'fluff', .8, name='ruff')
    hw, hh, hd = v(6.4, 6.4, 6.6), v(5.6, 5.4, 5.6), v(5.4, 5.6, 5.8)
    hy = cy + H * .55 + hh * .3
    f = face(s, dict(top=cy + H / 2, zf=-D / 2), (hw, hh, hd), pos=(hy, -D * .3), pivot=(0, hy - hh * .45, -D * .2),
             snout={'size': (v(3.0, 3.2, 3.4), v(2.0, 2.0, 2.1), v(1.0, 1.4, 1.8)), 'mat': 'fur_light', 'dy': -.25},
             nose={'size': (1.1, .6, .5), 'mat': 'nose'}, mouth='w',
             eyes={'iris': '#2E2668', 'iris_light': '#F2A6FF', 'w': v(2.2, 2.1, 2.0), 'h': v(2.5, 2.4, 2.2),
                   'style': 'round' if s.age == 0 else 'almond', 'tilt': .08},
             ears=None)
    hz = f['hz']
    # Feathery antennae plumes.
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        root = np.array([side * hw * .28, hy + hh / 2 - .2, hz])
        s.bone('antenna_' + sfx, 'head', root)
        tip = root + np.array([side * v(1.4, 2.0, 2.4), v(2.2, 3.0, 3.6), v(.6, 1.0, 1.4)])
        s.beam('antenna_' + sfx, root, tip, .4, 'antenna', group='acc', name='antenna')
        for k in range(v(2, 3, 5)):
            p = root + (tip - root) * (.35 + k * .15)
            s.beam('antenna_' + sfx, p, p + np.array([side * .8, .2, .5]), .35, 'fluff', .3, group='acc', name='plume')
    if s.age >= 1:
        for side in (-1, 1):
            s.beam('head', (side * hw * .2, hy + hh / 2 - .2, hz + 1.2), (side * hw * .3, hy + hh / 2 + 1.2, hz + 2.4), .7,
                   'horn', group='acc', name='horn')
    # Two pairs of moth wings with starry eye-spots.
    span = v(4.4, 6.4, 8.6)
    for pair, (name, up, size) in enumerate((('wing', .55, 1.0), ('wing_lower', -.1, .7))):
        if pair == 1 and s.age == 0:
            continue
        for side in (-1, 1):
            sfx = 'l' if side < 0 else 'r'
            root = np.array([side * W * .42, cy + H * .3 * (1 if pair == 0 else -.2), -.4 + pair * 1.6])
            s.bone(f'{name}_{sfx}', 'body', root)
            c = root + np.array([side * span * .45 * size, span * up * .5 * size, span * .2])
            s.rbox(f'{name}_{sfx}', c, (span * .9 * size, span * .8 * size, .45), 'wing', span * .2 * size,
                   (0, side * -20, side * (-14 if pair == 0 else 18)), root, group='wing', name='moth_wing')
            s.bone(f'{name}_{sfx}_tip', f'{name}_{sfx}', c)
            c2 = c + np.array([side * span * .38 * size, span * .2 * size * (1 if pair == 0 else -1), span * .15])
            s.rbox(f'{name}_{sfx}_tip', c2, (span * .55 * size, span * .6 * size, .4), 'wing', span * .16 * size,
                   (0, side * -26, side * (-24 if pair == 0 else 30)), root, group='wing', name='moth_wing')
            # The eye-spot on each upper wing.
            if pair == 0:
                spot = c + np.array([side * span * .12, span * .05, 0])
                for col, rad in (('eye_ring', span * .2), ('eye_spot', span * .14), ('eye_core', span * .06)):
                    s.mark(decal(Mark(ellipsoid(spot, (rad, rad, 3)), col, groups=('wing',), parts=('moth_wing',)), 1))
    s.mark(Inset('wing_edge', faces=('north', 'south'), left=0, right=0, top=0, bottom=0, groups=('wing',), parts=('none',)))
    s.mark(Dots(1.2, .16, 'star', groups=('wing',), scatter=.5, skip=.5))
    # Little arms, legs and a tail ending in a crescent moon.
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        s.bone('arm_' + sfx, 'body', (side * W * .38, cy + H * .1, -D * .4))
        s.rbox('arm_' + sfx, (side * W * .38, cy - H * .1, -D * .5), (1.4, 2.0, 1.4), 'fluff', .4, group='leg', name='arm')
        s.bone('leg_' + sfx, 'body', (side * W * .3, cy - H * .3, .4))
        s.rbox('leg_' + sfx, (side * W * .3, cy - H * .5, .2), (2.0, 2.0, 2.4), 'fur', .5, group='leg', name='leg')
        s.bone('leg_' + sfx + '_lower', 'leg_' + sfx, (side * W * .3, cy - H * .5, .2))
        s.rbox('leg_' + sfx + '_lower', (side * W * .3, cy - H * .5 - 1.2, -.2), (2.0, 1.0, 2.8), 'fluff', .3, group='paw',
               name='foot')
    t0 = (0, cy - H * .2, D * .45)
    pts = [t0, (0, cy - H * .4, D * .45 + v(1.6, 2.2, 2.8)), (0, cy - H * .1, D * .45 + v(3.0, 4.2, 5.4)),
           (0, cy + H * .3, D * .45 + v(3.8, 5.4, 7.0))]
    names = chain(s, 'tail', pts, [v(2.2, 2.4, 2.6), v(1.8, 2.0, 2.2), v(1.4, 1.6, 1.8)], 'fur')
    if s.age >= 1:
        end = np.array(pts[-1])
        r = v(0, 1.2, 1.6)
        for k in range(5):
            a = math.radians(160 + k * 40)
            c = end + np.array([0, math.sin(a) * r + .6, math.cos(a) * r])
            s.cube(names[-1], c, (.6, .7, 1.0), 'moon', (math.degrees(a) + 90, 0, 0), c, group='acc', name='moon')
    return s.finish()


# ------------------------------------------------------------- verdant capricorn

def _verdant_capricorn(stage):
    s = Pet('verdant_capricorn', stage, dict(fur='#8ED0A8', fur_light='#C6EED4', scale='#5AB08A', scale_light='#86D0A8',
                                             horn='#8A6A4A', vine='#5A9E4A', leaf='#7CC45A', leaf_vein='#4E8A3E',
                                             weed='#3E8A5A', weed2='#6AB86A', inner='#F2C6B8', nose='#3A5A44',
                                             mouth='#2E4A38', blush='#F4A6A0', hoof='#4A5A44', fin='#9AE07A'),
            kinds=dict(fur_light='fur', scale='scale', scale_light='scale', horn='wood', vine='leaf', weed='hair',
                       weed2='hair', hoof='smooth', fin='leaf'))
    v = s.v
    s.scale = v(.82, .88, .94)
    W, H, D = v(5.2, 5.8, 6.4), v(5.0, 5.6, 6.2), v(5.6, 6.4, 7.2)
    cy = v(4.6, 5.4, 6.2)
    s.bones['body'] = ('root', [0, cy, 0])
    s.rbox('body', (0, cy, -.4), (W, H, D), 'fur', min(W, H) * .3, name='chest')
    # Front legs of a goat, tucked in mid-swim.
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        hip = (side * W * .3, cy - H * .3, -D * .25)
        s.bone('leg_f' + sfx, 'body', hip)
        knee = (side * W * .3, cy - H * .3 - v(1.6, 2.0, 2.4), -D * .4)
        s.beam('leg_f' + sfx, hip, knee, 1.7, 'fur', bevel=.3, group='leg', name='leg')
        s.bone('leg_f' + sfx + '_lower', 'leg_f' + sfx, knee)
        foot = (side * W * .3, knee[1] - v(1.2, 1.6, 2.0), knee[2] - 1.0)
        s.beam('leg_f' + sfx + '_lower', knee, foot, 1.5, 'fur', bevel=.3, group='leg', name='leg')
        s.rbox('leg_f' + sfx + '_lower', foot, (1.7, 1.0, 1.7), 'hoof', .3, group='paw', name='hoof')
    f = face(s, dict(top=cy + H / 2, zf=-.4 - D / 2), (v(5.6, 5.4, 5.4), v(5.0, 4.8, 4.8), v(5.2, 5.4, 5.6)),
             rise=v(.32, .36, .4), fwd=v(.05, .08, .1),
             snout={'size': (v(2.8, 2.8, 2.8), v(2.0, 2.0, 2.0), v(1.2, 1.6, 2.0)), 'mat': 'fur_light', 'dy': -.24},
             nose={'size': (1.1, .7, .5)}, mouth='w',
             eyes={'iris': '#1E4A2E', 'iris_light': '#9AF0B0', 'w': v(2.0, 1.85, 1.7), 'h': v(2.2, 2.05, 1.9)},
             ears={'w': v(1.8, 1.9, 2.0), 'h': v(2.2, 2.4, 2.6), 'tilt': 70, 'lean': 0, 'x': .4, 'sink': 1.2})
    hy, hh, hw, hz = f['hy'], f['hh'], f['hw'], f['hz']
    # A goatee of seaweed and branch-and-vine horns.
    s.beam('head', (0, hy - hh * .45, f['snout_front'] + 1.0), (0, hy - hh * .45 - v(.8, 1.2, 1.6), f['snout_front'] + 1.4),
           .8, 'weed', group='acc', name='goatee')
    for side in (-1, 1):
        nm = 'horn_' + ('l' if side < 0 else 'r')
        root = np.array([side * hw * .22, hy + hh / 2 - .2, hz + .3])
        s.bone(nm, 'head', root)
        prev = root
        segs = v(1, 3, 4)
        for k in range(segs):
            a = math.radians(30 + k * 32)
            nxt = prev + np.array([side * .5, math.cos(a) * 1.6, math.sin(a) * 1.6])
            s.beam(nm, prev, nxt, v(.9, .8, .9) * (1 - k * .12), 'horn', group='acc', name='horn')
            if s.age == 2 and k in (1, 2):
                _leaf(s, nm, nxt, nxt + np.array([side * 1.2, .8, -.4]), .9)
            prev = nxt
        if s.age >= 1:
            _leaf(s, nm, prev, prev + np.array([side * .4, 1.0, .6]), 1.0)
    # A seaweed mane flowing down the neck.
    for k in range(v(2, 4, 6)):
        root = (0, hy + hh * .3 - k * .9, hz + f['hd'] * .4 + k * .6)
        s.beam('body' if k > 1 else 'head', root, (0, root[1] - 2.0, root[2] + 1.6), 1.4, 'weed' if k % 2 else 'weed2', .8,
               bevel=.2, group='acc', name='mane')
    # The fish tail: scaled segments that curl up, ending in leaf fins.
    n = v(3, 4, 5)
    pts = [(0, cy - .2, D * .4)]
    for k in range(1, n + 1):
        a = math.radians(k * (90 / n))
        pts.append((0, cy - .2 - math.sin(a) * v(1.4, 2.0, 2.6) + (k * k * .25), D * .4 + k * v(1.6, 2.0, 2.4)))
    names = chain(s, 'tail', pts, [v(3.8, 4.2, 4.6) * (1 - .15 * k) for k in range(n)], ['scale', 'scale_light'] * 3)
    end = np.array(pts[-1])
    for side in (-1, 1):
        nm = 'tail_fin_' + ('l' if side < 0 else 'r')
        s.bone(nm, names[-1], end)
        for k in range(v(1, 2, 3)):
            tip = end + np.array([side * (1.4 + k * .6), 1.4 + k * 1.0, 1.4 + k * .4])
            _leaf(s, nm, end, tip, 1.6 - k * .2, 'fin', group='tail')
    # Side fins like water lily leaves.
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        root = (side * W * .48, cy + .2, D * .1)
        s.bone('wing_' + sfx, 'body', root)
        _leaf(s, 'wing_' + sfx, root, (root[0] + side * v(2.0, 2.6, 3.2), root[1] + .4, root[2] + 1.2), v(1.6, 1.9, 2.2), 'fin',
              group='wing')
    s.mark(Mark(slab(1, -1e9, cy - H * .2), 'fur_light', groups=('body',), jitter=.3))
    return s.finish()


# ------------------------------------------------------------------- lotus mantis

def _lotus_mantis(stage):
    s = Pet('lotus_mantis', stage, dict(body='#8ED6A0', body_dark='#5AB07A', belly='#C6F0C8', petal='#FFB8D0',
                                        petal_tip='#F27AA6', petal_white='#FFF0F4', wing='#C6F0D8', wing_vein='#7ACB9A',
                                        eye='#4ACB7A', antenna='#5AB07A', mouth='#2E5A3A', blush='#F7A6B8', pod='#F2D45A'),
            kinds=dict(body='shell', body_dark='shell', belly='shell', petal='plain', petal_tip='plain',
                       petal_white='plain', wing='membrane', wing_vein='membrane', eye='gem', antenna='smooth',
                       pod='smooth'))
    v = s.v
    s.scale = v(.82, .88, .94)
    cy = v(5.0, 6.0, 7.0)
    s.bones['body'] = ('root', [0, cy, 0])
    # Upright thorax, long abdomen behind.
    s.rbox('body', (0, cy + 1.0, -.6), (v(2.4, 2.6, 2.8), v(4.0, 5.0, 6.0), 2.4), 'body', .6, (-15, 0, 0), name='thorax')
    s.rbox('body', (0, cy - .6, 2.4), (v(3.4, 3.8, 4.2), v(2.8, 3.0, 3.2), v(4.4, 5.6, 6.8)), 'body', .8, name='abdomen')
    s.mark(Bands((0, 0, 1), 0, .9, .2, 'body_dark', groups=('body',), parts=('abdomen',), facing=(0, 1, 0), min_dot=-.3))
    s.mark(Mark(slab(1, -1e9, cy - 1.4), 'belly', groups=('body',), parts=('abdomen',)))
    # The triangular head with big eyes.
    hw, hh, hd = v(5.4, 5.6, 5.8), v(4.4, 4.4, 4.6), v(3.8, 4.0, 4.2)
    hy, hz = cy + v(3.6, 4.4, 5.2), -1.4
    s.bone('head', 'body', (0, hy - hh * .4, hz + .6))
    s.rbox('head', (0, hy, hz), (hw, hh * .8, hd), 'body', 1.0, name='skull')
    s.rbox('head', (0, hy - hh * .38, hz - .2), (hw * .5, hh * .4, hd * .8), 'body', .5, name='jaw')
    for side in (-1, 1):
        s.rbox('head', (side * hw * .42, hy + .3, hz - .2), (v(2.2, 2.2, 2.3), v(2.6, 2.6, 2.7), v(2.4, 2.4, 2.5)), 'eye', .6,
               name='eye')
    s.mark(Eye((hw * .42, hy + .35, hz - .2 - 1.25), v(1.9, 1.8, 1.7), v(2.2, 2.1, 2.0), iris='#0E3A1E', iris_light='#9AF0B0',
               outline='#0E2A18'))
    s.mark(decal(Mark(polyline([(-.6, hy - hh * .45, hz - hd / 2 - .3), (0, hy - hh * .52, hz - hd / 2 - .3),
                                (.6, hy - hh * .45, hz - hd / 2 - .3)], .13), 'mouth', groups=('head',), min_dot=-2)))
    _blush(s, hw * .2, hy - hh * .2, hz - hd / 2, .6, .32)
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        root = (side * .5, hy + hh * .4, hz - .6)
        s.bone('antenna_' + sfx, 'head', root)
        s.beam('antenna_' + sfx, root, (side * 1.6, root[1] + v(2.4, 3.4, 4.2), root[2] - 1.2), .3, 'antenna', group='acc',
               name='antenna')
    # Lotus crown.
    if s.age >= 1:
        n = v(0, 5, 8)
        top_y = hy + hh * .4
        for k in range(n):
            a = math.radians(k * 360 / n)
            c = (math.cos(a) * .9, top_y + .5, hz + math.sin(a) * .9)
            s.cube('head', c, (.8, 1.6, .3), 'petal' if k % 2 else 'petal_white', (25, -math.degrees(a) + 90, 0), c, group='acc',
                   name='crown_petal')
        if s.age == 2:
            s.rbox('head', (0, top_y + .6, hz), (1.0, .6, 1.0), 'pod', .2, group='acc', name='pod')
    # Raptorial arms whose blades are lotus petals.
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        sh = (side * 1.3, cy + v(2.4, 3.0, 3.6), -1.4)
        s.bone('arm_' + sfx, 'body', sh)
        el = (side * 1.6, sh[1] - v(.4, .6, .8), sh[2] - v(1.6, 2.2, 2.8))
        s.beam('arm_' + sfx, sh, el, 1.1, 'body', bevel=.2, group='leg', name='arm')
        s.bone('arm_' + sfx + '_lower', 'arm_' + sfx, el)
        blade_tip = (side * 1.6, el[1] + v(1.6, 2.4, 3.0), el[2] - .4)
        layers = v(1, 2, 3)
        for k in range(layers):
            s.beam('arm_' + sfx + '_lower', el, (blade_tip[0] + side * k * .3, blade_tip[1] - k * .4, blade_tip[2] + k * .3),
                   v(1.4, 1.6, 1.8) - k * .2, ('petal', 'petal_white', 'petal')[k], .5, bevel=.15, group='leg', name='petal_blade')
    s.mark(Mark(slab(1, cy + v(4.4, 5.6, 6.6)), 'petal_tip', groups=('leg',), parts=('petal_blade',)))
    # Leaf-glass wings folded over the abdomen.
    for side in (-1, 1):
        sfx = 'l' if side < 0 else 'r'
        root = (side * .8, cy + .8, .2)
        s.bone('wing_' + sfx, 'body', root)
        wl = v(3.0, 4.6, 6.2)
        _leaf(s, 'wing_' + sfx, root, (side * 1.6, cy + .6, .2 + wl), v(2.0, 2.4, 2.8), 'wing', group='wing')
        s.bone('wingtip_' + sfx, 'wing_' + sfx, root)
        if s.age >= 1:
            _leaf(s, 'wingtip_' + sfx, root, (side * 2.6, cy + 1.2, .2 + wl * .85), v(0, 2.0, 2.4), 'wing', group='wing')
    s.palette.setdefault('leaf_vein', '#7ACB9A')
    # Four thin walking legs.
    for k, (end, z) in enumerate((('f', .6), ('b', 3.4))):
        for side in (-1, 1):
            sfx = 'l' if side < 0 else 'r'
            nm = f'leg_{end}{sfx}'
            hip = (side * 1.4, cy - 1.0, z)
            knee = (side * v(3.0, 3.6, 4.2), cy + .6, z + (-.6 if end == 'f' else .8))
            foot = (side * v(3.6, 4.4, 5.0), .2, z + (-1.0 if end == 'f' else 1.6))
            s.bone(nm, 'body', hip)
            s.beam(nm, hip, knee, .55, 'body_dark', group='leg', name='leg')
            s.bone(nm + '_lower', nm, knee)
            s.beam(nm + '_lower', knee, foot, .5, 'body_dark', group='leg', name='leg')
    return s.finish()
