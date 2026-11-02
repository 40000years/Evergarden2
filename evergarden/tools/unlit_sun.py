"""The Unlit Sun, the dark star chained above the Void Ring Sanctuary in the End.

A sphere of obsidian voxels split into eight wedge segments around a molten core;
gold fissures craze every plate. Two front segments part like eyelids to reveal the
core's slit-pupilled eye, and the whole shell bursts apart into an eclipse in its
ultimate. Behind it a corona of black thorns and gold flares burns around an eclipse
halo; void drips hang from its underside. A tilted ring of void debris, four orbiting
monoliths bearing the sanctuary's hexagon sigil, a cloud of loose voxels and a floor
sigil all loop on their own lane.
"""
import math
import random

import numpy as np

from boss_forge import Clip, Forge, Material, Track, axis_angle, basis, euler, rot, unit
from realm_forge import CLIPS, build_realm

MODEL = 'unlit_sun'
TITLE = 'Unlit Sun'
REVISION = 'unlit-sun-v1'
SCALE = 3.6

OBSIDIAN = ['#07050c', '#0e0a18', '#171126', '#211935', '#2d2347', '#3b2f5c', '#4d3e74', '#625290']
FISSURE = ['#4a2a00', '#8a5200', '#c98a0e', '#f2b632', '#ffd970', '#fff2c0', '#ffffff']
VOIDLIGHT = ['#1a0630', '#3a0c66', '#6a1fb0', '#9a4ff0', '#cfa0ff', '#f0e0ff']
SUN = ['#7a4a00', '#c47f00', '#f0b020', '#ffd860', '#fff0b0', '#ffffff']
MATERIALS = {
    'shell': Material(OBSIDIAN, 'rock', accent=FISSURE, density=.85),
    'debris': Material(OBSIDIAN, 'rock', accent=VOIDLIGHT, density=.8),
    'pillar': Material(OBSIDIAN[1:] + ['#776aa6'], 'rock', accent=FISSURE, density=.9),
    'sunglow': Material(SUN, 'glow', glow=True, density=.5),
    'core': Material(['#c47f00', '#f0b020', '#ffd860', '#fff0b0', '#ffffff'], 'glow', glow=True, density=.35),
    'pupil': Material(['#000000', '#040208', '#0a0612', '#120a1e'], 'rock', density=.6),
    'thorn': Material(OBSIDIAN, 'horn'),
    'void': Material(VOIDLIGHT, 'glow', glow=True, density=.6),
    'halo': Material(SUN, 'glow', glow=True, density=.45),
}
C = np.array([0, 36.0, 0])
R = 14.0
SEGMENTS = 8
BANDS = 7
SIDES = (('r', 1), ('l', -1))
AURA = ('corona', 'ring', 'monoliths', 'debris', 'sigil')
MARKERS = ('root', 'facing', 'head_forward', 'fx_eye', 'fx_core', 'fx_corona')
PREVIEW = dict(close=(36, (0, 36)), clip_span=96, clip_center=(0, 34))


def skeleton(f):
    f.bone('root', None, [0, 0, 0])
    f.bone('facing', 'root', [0, 36, -30])
    f.bone('body', 'root', C)
    f.bone('hi_gaze', 'body', C)
    f.bone('head_forward', 'hi_gaze', C + (0, 0, -30))
    f.bone('shell', 'hi_gaze', C)
    f.bone('core', 'shell', C)
    for k in range(SEGMENTS):
        f.bone(f'seg{k}', 'shell', C)
    f.bone('cap_top', 'shell', C + (0, R, 0))
    f.bone('cap_bottom', 'shell', C - (0, R, 0))
    f.bone('corona', 'hi_gaze', C)
    f.bone('ring', 'body', C)
    f.bone('monoliths', 'root', C)
    f.bone('debris', 'root', C)
    f.bone('sigil', 'root', [0, .12, 0])
    f.bone('fx_eye', 'core', C + (0, 0, -R * .78))
    f.bone('fx_core', 'core', C)
    f.bone('fx_corona', 'corona', C + (0, R + 6, 2.5))


def direction(az, lat):
    return np.array([math.sin(az) * math.cos(lat), math.sin(lat), -math.cos(az) * math.cos(lat)])


# ---------------------------------------------------------------------------
# The shell: eight wedge segments of voxel plates, two caps, and the core inside


def shell(f, rng):
    step = math.tau / SEGMENTS
    lat_edges = np.linspace(math.radians(-78), math.radians(78), BANDS + 1)
    for k in range(SEGMENTS):
        az = step * (k + .5)
        for j in range(BANDS):
            lat = (lat_edges[j] + lat_edges[j + 1]) / 2
            d = direction(az, lat)
            north = direction(az, lat + .01) - direction(az, lat - .01)
            r = basis(north, d)
            widest = max(math.cos(lat_edges[j]), math.cos(lat_edges[j + 1])) if lat_edges[j] * lat_edges[j + 1] > 0 else 1.0
            width = 2 * R * widest * math.sin(step / 2) + .5
            height = 2 * R * math.sin((lat_edges[j + 1] - lat_edges[j]) / 2) + .3
            center = C + d * (R - 1.3)
            hot = .55 if j in (2, 3, 4) else .35
            f.cube(f'seg{k}', center, (width, height, 2.6), 'shell', r, label=f'plate{j}', heat=hot, tiles=1.6, facet=2.6)
            # Raised voxels on the plate share its rotation, so they cost no extra display.
            for _ in range(rng.randint(1, 3)):
                u, v = rng.uniform(-.32, .32) * width, rng.uniform(-.32, .32) * height
                size = rng.choice((1.4, 1.8, 2.2))
                f.cube(f'seg{k}', center + r @ np.array([u, v, 1.3 + .45]), (size, size, .9), 'shell', r, label='voxel', heat=.15,
                       tiles=1.6)
    for bone, sign in (('cap_top', 1), ('cap_bottom', -1)):
        rr = math.cos(math.radians(78)) * R
        f.cube(bone, C + (0, sign * (R - 1.2), 0), (rr * 2 + 1.2, 2.4, rr * 2 + 1.2), 'shell', rot((0, 22.5, 0)), label='cap', heat=.4,
               tiles=1.6)
        f.cube(bone, C + (0, sign * (R + .3), 0), (rr * 1.3, 1.0, rr * 1.3), 'shell', rot((0, 22.5, 0)), label='cap_voxel', heat=.2,
               tiles=1.6)
    # Void drips hanging from the underside.
    for k in range(7):
        a = math.tau * k / 7 + .3
        base = C + np.array([math.cos(a) * 3.6, -R + .6, math.sin(a) * 3.6])
        length = 5.0 + (k % 3) * 3.0
        f.spike('cap_bottom', base, base + (math.cos(a) * 1.2, -length, math.sin(a) * 1.2), 1.6 - (k % 3) * .25, 'void', steps=3,
                label='drip')


def core(f):
    b = 'core'
    size = 2 * (R - 2.9) / math.sqrt(3) * 1.18
    for i, r in enumerate((rot((0, 0, 0)), rot((45, 0, 45)), rot((0, 45, 45)), rot((45, 45, 0)), rot((0, 45, 0)), rot((45, 0, 0)))):
        f.cube(b, C, (size, size, size), 'core', r, label='core')
    # The pupil: a black slit across the face of the core.
    front = R - 3.4
    f.box(b, (-.9, C[1] - R * .55, C[2] - front - .3), (1.8, R * 1.1, .6), 'pupil', label='pupil')
    f.box(b, (-.35, C[1] - R * .42, C[2] - front - .5), (.7, R * .84, .3), 'void', label='pupil_glint')


# ---------------------------------------------------------------------------
# Corona: eclipse halo, gold ring, black thorns and flares


def corona(f):
    b = 'corona'
    z = 3.2
    halo = []
    for k in range(40):
        a = math.tau * k / 40
        r = R + (9.0 if k % 4 == 0 else 6.0 if k % 2 == 0 else 3.8)
        halo.append(C + np.array([math.cos(a) * r, math.sin(a) * r, z + 1.0]))
    f.sheet(b, halo, 'halo', thickness=.1, up=(0, 1, 0), facing=(0, 0, -1), label='eclipse_halo')
    f.ring(b, C + (0, 0, z), R + 1.4, .9, .7, 'sunglow', segments=32, normal=(0, 0, 1))
    for k in range(20):
        a = math.tau * k / 20 + math.pi / 20
        d = np.array([math.cos(a), math.sin(a), 0])
        length = 10.5 if k % 2 == 0 else 6.0
        f.spike(b, C + d * (R + 1.2) + (0, 0, z - .2), C + d * (R + 1.2 + length) + (0, 0, z - .4), 1.8 if k % 2 == 0 else 1.2, 'thorn',
                steps=4, z_hint=(0, 0, 1), label='thorn')
    for k in range(10):
        a = math.tau * k / 10
        d = np.array([math.cos(a), math.sin(a), 0])
        f.beam(b, C + d * (R + 2.2) + (0, 0, z + .4), C + d * (R + 14.0) + (0, 0, z + .4), .45, .3, 'sunglow', z_hint=(0, 0, 1),
               label='flare')


# ---------------------------------------------------------------------------
# Aura: debris ring, monoliths, loose voxels, floor sigil


def aura(f, rng):
    n = unit(np.array([.32, 1, .18]))
    f.ring('ring', C, 25.0, 2.4, .9, 'debris', segments=30, normal=n, heat=.4, tiles=1.4)
    f.ring('ring', C, 22.4, .5, .4, 'void', segments=30, normal=n, phase=.1)
    a_ax = unit(np.cross(n, [1, 0, 0]))
    b_ax = np.cross(n, a_ax)
    for k in range(16):
        t = math.tau * k / 16 + rng.uniform(-.1, .1)
        p = C + (a_ax * math.cos(t) + b_ax * math.sin(t)) * rng.uniform(23.5, 27.5) + n * rng.uniform(-1.4, 1.4)
        s = rng.uniform(1.4, 2.8)
        f.cube('ring', p, (s, s, s), 'debris', rot((rng.uniform(0, 90), rng.uniform(0, 90), 0)), label=f'ring_chunk{k % 3}', heat=.5,
               tiles=1.4)
    for k in range(4):
        a = math.tau * k / 4 + math.pi / 4
        c = C + np.array([math.cos(a) * 34, (-6 if k % 2 else 7), math.sin(a) * 34])
        f.box('monoliths', c - (1.8, 7.0, 1.8), (3.6, 14.0, 3.6), 'pillar', label='monolith', heat=.12, tiles=1.2,
              glyph=('north', 'south', 'east', 'west'))
        f.spike('monoliths', c + (0, 7.0, 0), c + (0, 10.4, 0), 3.0, 'pillar', steps=3, label='monolith_cap', heat=.1)
        f.spike('monoliths', c - (0, 7.0, 0), c - (0, 12.6, 0), 4.6, 'debris', steps=3, label='monolith_root', heat=.45)
        f.beam('monoliths', c - (0, 12.6, 0), c - (0, 22.0, 0), .9, .9, 'sunglow', label='monolith_beam')
    for k in range(14):
        p = C + np.array([rng.uniform(-26, 26), rng.uniform(-18, 22), rng.uniform(-26, 26)])
        if np.linalg.norm(p - C) < R + 6:
            p = C + unit(p - C) * (R + 8)
        s = rng.uniform(1.0, 2.2)
        f.cube('debris', p, (s, s, s), 'debris', rot((rng.uniform(0, 90), rng.uniform(0, 90), 0)), label=f'voxel_bit{k % 3}', heat=.3,
               tiles=1.0)
    s0 = np.array([0, .12, 0])
    f.ring('sigil', s0, 18.0, .45, .1, 'void', segments=30, normal=(0, 1, 0))
    for k in range(6):
        a = math.tau * k / 6
        f.beam('sigil', s0 + np.array([math.cos(a), 0, math.sin(a)]) * 18.0,
               s0 + np.array([math.cos(a + math.tau / 6), 0, math.sin(a + math.tau / 6)]) * 18.0, .4, .1, 'void', z_hint=(0, 1, 0),
               label='sigil_hex')


def sculpt():
    for m in MATERIALS.values():
        m.ramp_hex = ['#%02x%02x%02x' % tuple(int(v) for v in c) for c in m.ramp]
    f = Forge(MODEL, MATERIALS, seed=4040)
    rng = random.Random(404)
    skeleton(f)
    shell(f, rng)
    core(f)
    corona(f)
    aura(f, rng)
    return f


# ---------------------------------------------------------------------------
# Motion: the shell breathes, the eye opens, the segments burst and reassemble.


STANCE = dict(body=[0, 0, 0], shell=[0, 0, 0], eye=[5, 0, 0], burst=[0, 0, 0], tumble=[0, 0, 0], core=[0, 0, 0])


def key(t, base=STANCE, **changes):
    d = dict(base)
    d.update(changes)
    return (t, d)


def pose_at(tr, t, wobble=1.0, spin=0.0):
    burst = float(tr('burst', t)[0])
    tumble = float(tr('tumble', t)[0])
    eye = float(tr('eye', t)[0])
    sh = np.array(tr('shell', t), float) + np.array([math.sin(t * 1.3) * 2.0 * wobble, spin, math.cos(t * 1.1) * 2.0 * wobble])
    pose = {'body': {'rot': [0, 0, 0], 'pos': list(np.array(tr('body', t), float) + (0, math.sin(t * 1.6) * .8 * wobble, 0))},
            'shell': list(sh), 'core': {'rot': [0, 0, 0], 'pos': list(tr('core', t))}}
    step = math.tau / SEGMENTS
    for k in range(SEGMENTS):
        az = step * (k + .5)
        out = np.array([math.sin(az), 0, -math.cos(az)])
        tangent = np.array([math.cos(az), 0, math.sin(az)])
        turn = -eye if k == 0 else eye if k == SEGMENTS - 1 else 0.0
        r = axis_angle((0, 1, 0), turn) @ axis_angle(tangent, tumble * (1 if k % 2 else -1))
        lift = (1 if k % 2 else -1) * burst * .18
        breathe = .25 * math.sin(t * 2.4 + k) * wobble
        pose[f'seg{k}'] = {'rot': euler(r), 'pos': list(out * (burst + breathe) + (0, lift, 0))}
    pose['cap_top'] = {'rot': [0, 0, tumble * .5], 'pos': [0, burst * 1.1, 0]}
    pose['cap_bottom'] = {'rot': [0, 0, -tumble * .5], 'pos': [0, -burst * 1.1, 0]}
    return pose


def clips(forge):
    out = []

    def make(name, length, track, loop=False, wobble=1.0, spin=None):
        def fn(t):
            return pose_at(track, t, wobble, spin(t) if spin else 0.0)
        out.append(Clip(name, length, fn, loop=loop))

    make('idle', 4.0, Track([key(0), key(2.0, eye=[9, 0, 0]), key(4.0)]), loop=True)
    drift = dict(STANCE, shell=[-12, 0, 0], body=[0, -1.0, 0], eye=[7, 0, 0])
    make('walk', 2.0, Track([key(0, drift), key(1.0, drift, shell=[-12, 0, 4]), key(2.0, drift)]), loop=True)

    # Attack - the gaze: the eye splits open, the shell rears, then flares.
    stare = dict(STANCE, shell=[8, 0, 0], eye=[32, 0, 0], burst=[.6, 0, 0], body=[0, 1.0, 1.5])
    flare = dict(STANCE, shell=[-6, 0, 0], eye=[44, 0, 0], burst=[1.6, 0, 0], body=[0, 0, 3.0], core=[0, 0, -1.6])
    make('attack_windup', .9, Track([key(0), (.75, dict(stare, ease='out')), (.9, stare)]))
    make('attack_active', .5, Track([(0, dict(stare, ease='in')), (.12, dict(flare, ease='snap')), (.5, flare)]))
    make('attack_recover', .8, Track([(0, flare), (.8, STANCE)]))

    # Skill - ring storm: the shell spins up, segments split, then slam outward.
    spinup = dict(STANCE, eye=[12, 0, 0], burst=[2.4, 0, 0], tumble=[10, 0, 0])
    slam = dict(STANCE, eye=[20, 0, 0], burst=[6.0, 0, 0], tumble=[24, 0, 0])
    make('skill_windup', 1.2, Track([key(0), (1.0, dict(spinup, ease='out')), (1.2, spinup)]), spin=lambda t: 300 * (t / 1.2) ** 2)
    make('skill_active', .6, Track([(0, dict(spinup, ease='in')), (.15, dict(slam, ease='snap')), (.6, dict(slam, burst=[3.0, 0, 0]))]),
         spin=lambda t: 300 + 600 * t)
    make('skill_recover', .8, Track([(0, dict(slam, burst=[3.0, 0, 0])), (.8, STANCE)]), spin=lambda t: 660 + 60 * t * (1 - t / .8))

    # Ultimate - eclipse: the segments drift apart over the exposed core, then the shell explodes.
    part = dict(STANCE, eye=[24, 0, 0], burst=[5.0, 0, 0], tumble=[8, 0, 0], body=[0, 2.0, 0])
    eclipse = dict(STANCE, eye=[30, 0, 0], burst=[12.0, 0, 0], tumble=[38, 0, 0], body=[0, 3.0, 0])
    make('ultimate_windup', 2.0, Track([key(0), (1.8, dict(part, ease='out')), (2.0, part)]), wobble=.4,
         spin=lambda t: 90 * (t / 2.0) ** 2)
    make('ultimate_active', 1.6, Track([(0, dict(part, ease='in')), (.25, dict(eclipse, ease='snap')), (1.6, dict(eclipse, burst=[10.0, 0, 0]))]),
         wobble=.2, spin=lambda t: 90 + 120 * t)
    make('ultimate_recover', 1.6, Track([(0, dict(eclipse, burst=[10.0, 0, 0])), (1.3, dict(STANCE, ease='out')), (1.6, STANCE)]),
         spin=lambda t: 282 + 78 * min(1.0, t / 1.3))

    # Stun: the shell sags and loosens, the eye shut.
    sag = dict(STANCE, body=[0, -6.0, 0], shell=[-18, 0, 14], eye=[0, 0, 0], burst=[1.6, 0, 0], tumble=[10, 0, 0])
    make('stun', 2.0, Track([key(0), (.35, dict(sag, ease='out')), (1.7, sag), (2.0, STANCE)]), wobble=2.0)

    flicker = dict(STANCE, eye=[18, 0, 0], burst=[3.0, 0, 0], tumble=[16, 0, 0])
    make('transition', 3.0, Track([key(0), (.5, flicker), (1.0, dict(STANCE, burst=[.4, 0, 0])), (1.5, flicker), (2.0, STANCE),
                                   (2.5, dict(flicker, burst=[4.0, 0, 0])), (3.0, STANCE)]), spin=lambda t: 720 * (t / 3.0))

    scattered = dict(STANCE, body=[0, 12.0, 0], eye=[0, 0, 0], burst=[18.0, 0, 0], tumble=[60, 0, 0])
    make('spawn', 3.0, Track([(0, scattered), (2.2, dict(STANCE, eye=[0, 0, 0], ease='out')), (3.0, STANCE)]),
         spin=lambda t: -240 * (1 - min(1.0, t / 2.2)) ** 2)
    return out, 0.0


def aura_clip():
    def fn(t):
        u = t / 40.0
        return {'corona': [0, 0, 360 * u], 'ring': [0, 720 * u, 0],
                'monoliths': {'rot': [0, -360 * u, 0], 'pos': [0, math.sin(u * math.tau * 3) * 1.2, 0]},
                'debris': {'rot': [0, 360 * u * .5, 0], 'pos': [0, math.sin(u * math.tau * 4 + 1) * .8, 0]}, 'sigil': [0, -360 * u, 0]}
    return Clip('aura', 40.0, fn, loop=True, step=1 / 10, bones=AURA)


def build():
    f = sculpt()
    motion, worst = clips(f)
    body_bones = [b for b in f.bones if b not in AURA and b not in MARKERS and b != 'hi_gaze']
    for clip in motion:
        clip.bones = body_bones
    assert [c.name for c in motion] == list(CLIPS)
    model, atlas, stats = build_realm(f, motion + [aura_clip()], MODEL, [[-14, 22, -14], [14, 50, 14]],
                                      meta=dict(evergarden_revision=REVISION, evergarden_scale=SCALE), density=7.0)
    return model, atlas, stats, worst, f
