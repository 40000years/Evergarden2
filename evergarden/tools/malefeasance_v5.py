"""Malefeasance v5: both forms repainted as anime sorceresses on the shared river rig.

Phase 1, "the Sin Witch": a tall, beautiful and openly wicked sorceress in the manner of the
Seven Deadly Sins' Merlin. Long black hair over a violet cape with a towering crimson-lined
collar, amber eyes with a sly smirk, a corseted black gown split over thigh-high boots, a crescent
staff, a floating grimoire and a violet orb that drifts beside her.

Phase 2, "the Star Imp": her rebirth as a cute, smug little witch: a huge star-pinned hat, lilac
twin tails, big cyan eyes with a fang grin, a frilled midnight dress with a star-print skirt,
striped stockings, bat wings, a devil tail and a star wand.

Every face is painted (evergarden/tools/anime_forge.py): clean cel shading from front, back and
side illustrations instead of noise, with the light baked into the paint. Both forms keep every
bone and all 17 clips of the original rig (retargeted by bone name from
arkhaia_malefeasance.bbmodel.bak). Gown and torso parts sit on BossHitbox body bones; hats, capes
and props sit on bones the melee hitbox ignores.

    python evergarden/tools/malefeasance_v5.py [--out DIR] [--only 1|2]
"""
import argparse
import copy
import json
import math
from pathlib import Path
import sys

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'evergarden/tools'))

from anime_forge import (Canvas, Project, PlaneLocal, ByFace, Shader, Solid, Rig, build, rot, frame, unit,
                         smooth, mix, rgb, bezier, blur, sample)

OUT = ROOT / 'evergarden/src/main/resources/modelengine/rivers'
BACKUP = OUT / 'arkhaia_malefeasance.bbmodel.bak'
REVISION = 'malefeasance-v5'
SIDES = (('r', -1), ('l', 1))


# --------------------------------------------------------------------------- rig
def joints():
    j = {
        'root': (0, 0, 0), 'pelvis': (0, 15, 0), 'chest': (0, 24, 0), 'neck': (0, 29, 0),
        'hi_gaze': (0, 34, 0), 'head': (0, 34, 0), 'head_forward': (0, 34, -7), 'jaw': (0, 33.5, -2.5),
        'collar': (0, 28, 1), 'cape': (0, 28.4, 2.2), 'cape_tip': (0, 15, 4.0),
        'focus': (-6.0, 16.4, -4.8), 'familiar': (-8.4, 30.6, 1.2), 'halo': (0, 33.5, 4.6), 'book': (-6.4, 20.2, -4.6),
    }
    for i, x in enumerate([-3.625, -2.175, -0.725, 0.725, 2.175, 3.625]):
        j[f'veil_{i}'] = (x, 29, 3.3)
    for side, s in SIDES:
        j.update({f'shoulder_{side}': (s * 3.45, 27.9, .15), f'elbow_{side}': (s * 4.15, 22.5, -.05),
                  f'wrist_{side}': (s * 4.55, 18.3, -2.35), f'hip_{side}': (s * 1.45, 15, 0),
                  f'knee_{side}': (s * 1.55, 8, -.1), f'ankle_{side}': (s * 1.65, 1.6, .1), f'sash_{side}': (s * 3.2, 15, 0)})
    return {k: np.array(v, float) for k, v in j.items()}


J = joints()


def skeleton(rig):
    rig.bone('root', None, [0, 0, 0])
    rig.bone('pelvis', 'root', J['pelvis'])
    rig.bone('chest', 'pelvis', J['chest'])
    rig.bone('neck', 'chest', J['neck'])
    rig.bone('hi_gaze', 'neck', J['hi_gaze'])
    rig.bone('head', 'hi_gaze', J['head'])
    rig.bone('head_forward', 'head', J['head_forward'])
    rig.bone('jaw', 'head', J['jaw'])
    rig.bone('collar', 'chest', J['collar'])
    for i in range(6):
        rig.bone(f'veil_{i}', 'collar', J[f'veil_{i}'])
    rig.bone('cape', 'chest', J['cape'])
    rig.bone('cape_tip', 'cape', J['cape_tip'])
    rig.bone('focus', 'chest', J['focus'])
    rig.bone('familiar', 'chest', J['familiar'])
    rig.bone('halo', 'chest', J['halo'])
    for side, s in SIDES:
        rig.bone(f'arm_{side}', 'chest', J[f'shoulder_{side}'])
        rig.bone(f'forearm_{side}', f'arm_{side}', J[f'elbow_{side}'])
        rig.bone(f'hand_{side}', f'forearm_{side}', J[f'wrist_{side}'])
        rig.bone(f'weapon_{side}', f'hand_{side}', J[f'wrist_{side}'])
        rig.bone(f'shoulder_{side}', 'chest', J[f'shoulder_{side}'])
        rig.bone(f'leg_{side}', 'pelvis', J[f'hip_{side}'])
        rig.bone(f'shin_{side}', f'leg_{side}', J[f'knee_{side}'])
        rig.bone(f'foot_{side}', f'shin_{side}', J[f'ankle_{side}'])
        rig.bone(f'sash_{side}', 'pelvis', J[f'sash_{side}'])
    rig.bone('book', 'hand_r', J['book'])


def retarget(model):
    """The original 17 clips, rebound to this model's bone UUIDs by name."""
    backup = json.loads(BACKUP.read_text(encoding='utf8'))
    names = {}

    def scan(nodes):
        for node in nodes:
            if isinstance(node, dict):
                names[node['name']] = node['uuid']
                scan(node.get('children', []))
    scan(model['outliner'])
    clips = []
    for anim in backup.get('animations', []):
        clip = copy.deepcopy(anim)
        clip['animators'] = {names[a['name']]: copy.deepcopy(a) for a in anim.get('animators', {}).values() if a.get('name') in names}
        clips.append(clip)
    return clips


# --------------------------------------------------------------------------- shared painting helpers
def V(*hexes):
    return [rgb(h) for h in hexes]


def front_canvas(x, y, ppu):
    """A front-view canvas: image left is the model's +X, as seen from the front."""
    return Canvas((-x, x), y, ppu, flip=True)


def cel(tone, ramp, edges=(.28, .55, .8)):
    """Three-step cel shade: tone 0 = deepest shadow, 1 = highlight, with soft 0.04 transitions."""
    tone = np.asarray(tone, float)
    out = np.broadcast_to(np.asarray(ramp[0], float), tone.shape + (3,)).copy()
    for edge, colour in zip(edges, ramp[1:]):
        out = mix(out, colour, smooth(edge - .03, edge + .03, tone))
    return out


def strands(u, v, seed, density=6.0, wave=.35):
    """Hair strand field in 0..1: u across the lock, v along it."""
    rng = np.random.default_rng(seed)
    phases = rng.random(4) * 6.28
    s = (np.sin(u * density * 6.28 + np.sin(v * 1.7 + phases[0]) * wave * 6 + phases[1]) * .55
         + np.sin(u * density * 2.3 * 6.28 + v * .8 + phases[2]) * .3 + np.sin(u * density * 5.1 * 6.28 + phases[3]) * .15)
    return s * .5 + .5


# --------------------------------------------------------------------------- phase 1 palette
SKIN = V('#fff2ec', '#fadcd6', '#eab4bd', '#c98597')          # light, mid, shade, deep
HAIR = V('#07050c', '#120d1b', '#1e1630', '#30244a', '#5c4c8c', '#a596da')  # line, deep, base, mid, light, shine
LASH = rgb('#1a0d1c')
IRIS = V('#2a0e05', '#7e3a0c', '#d4801c', '#ffcf55', '#fff1c4')
LIPS = V('#4e0f22', '#8e2244', '#c3446a', '#f08aa6')
SHADOW_EYE = rgb('#7d2a72')


def lock_outline(centre, width, power=2.6, root_round=.0):
    """Polygon of a tapered lock around a centreline (root first); width is the root width."""
    pts = np.array(centre, float)
    d = np.gradient(pts, axis=0)
    d /= np.maximum(np.linalg.norm(d, axis=1, keepdims=True), 1e-9)
    n = np.stack([-d[:, 1], d[:, 0]], axis=1)
    t = np.linspace(0, 1, len(pts))
    half = width / 2 * np.clip(1 - t ** power, 0, 1) ** .7 + .01
    left, right = pts + n * half[:, None], pts - n * half[:, None]
    return [tuple(p) for p in left] + [tuple(p) for p in right[::-1]], pts, n, half


def draw_lock(c, centre, width, ramp, shine=(.24, .36), side=1, power=2.6, outline=.045, strands_n=2, tip_tint=None,
              alpha=1.0, view=None):
    """One anime hair lock: dark outline, base colour, a cel shadow down one side, a glossy band and
    strand lines. `view` maps (x, y) canvas-space points (default identity)."""
    tf = view or (lambda p: p)
    poly, pts, n, half = lock_outline(centre, width, power)
    m_out = c.poly_mask([tf(p) for p in lock_outline(centre, width + outline * 2, power * 1.05)[0]])
    c.put(m_out, ramp[0], alpha)
    base = c.poly_mask([tf(p) for p in poly])
    c.put(base, ramp[2], alpha)
    # Cel shadow: the lock's `side` half, from a third of the way down to the tip.
    k = len(pts)
    shadow = [tuple(p) for p in pts[k // 5:]] + [tuple(p) for p in (pts + n * half[:, None] * side * .95)[k // 5:][::-1]]
    c.put(c.poly_mask([tf(p) for p in shadow]) * base, ramp[1], alpha * .9)
    # Gloss: a thin diamond across the lock (together the locks form the 'angel ring').
    i0 = min(k - 2, int(k * (shine[0] + shine[1]) / 2))
    d = pts[min(k - 1, i0 + 1)] - pts[i0]
    d = d / max(np.linalg.norm(d), 1e-9)
    along = .045 + .12 * (shine[1] - shine[0])
    for scale, colour in ((1.0, ramp[3]), (.4, ramp[4])):
        hw = half[i0] * .9 * scale
        gloss = [pts[i0] - d * along * scale, pts[i0] + n[i0] * hw, pts[i0] + d * along * scale * .6, pts[i0] - n[i0] * hw]
        c.put(c.poly_mask([tf(tuple(p)) for p in gloss]) * base, colour, alpha)
    for j in range(strands_n):
        off = (j + 1) / (strands_n + 1) * 2 - 1
        line = [tf(tuple(p)) for p in (pts + n * half[:, None] * off * .7)[int(k * .35):int(k * .85)]]
        if len(line) > 1:
            c.put(c.line_mask(line, .022) * base, ramp[0], alpha * .55)
    if tip_tint is not None:
        tipz = [tuple(p) for p in (pts + n * half[:, None])[int(k * .75):]] + [tuple(p) for p in (pts - n * half[:, None])[int(k * .75):][::-1]]
        c.put(blur(c.poly_mask([tf(p) for p in tipz]), 3) * base, tip_tint, alpha * .45)
    return base


def curve(*points, n=28):
    return bezier(points, n)


def paint_face_hex():
    """Mature anime face: sharp half-lidded amber eyes with a winged liner, high arched brows, a sly
    berry smirk and a beauty mark. The canvas covers the head's front face."""
    c = front_canvas(2.5, (31.2, 36.4), 96)
    X, Y = c.grid()
    D = -X                                   # viewer-centric: positive to the viewer's right
    A = np.abs(D)
    # Skin: soft side shading and hair shadow from the fringe.
    tone = .82 - .2 * smooth(1.3, 2.5, A) - .3 * smooth(34.6, 35.4, Y) - .12 * smooth(33.9, 34.5, Y) * smooth(.8, 1.6, A)
    base = mix(mix(SKIN[2], SKIN[1], smooth(.45, .62, tone)), SKIN[0], smooth(.7, .86, tone))
    c.put(1.0, base)
    # Neck shadow beneath a V jawline, with a soft rim.
    jaw = 31.25 + 2.05 * np.clip(A / 2.5, 0, 1) ** 2.1
    below = smooth(jaw + .03, jaw - .06, Y)
    c.tint(below, SKIN[3], .55)
    c.tint(below * smooth(jaw - .35, jaw - .03, Y), SKIN[2], .5)
    edge = c.line_mask([(-x, 31.25 + 2.05 * (abs(x) / 2.5) ** 2.1) for x in np.linspace(-2.5, 2.5, 80)], .03)
    c.tint(edge * smooth(.15, .6, A), rgb('#b9707f'), .6)
    for s in (-1, 1):
        c.tint(blur(c.ellipse_mask((-s * 1.5, 32.72), (.5, .17)), 10), rgb('#ff8fa6'), .3)

    def eye(s):
        def P(dx, y):
            return (-s * dx, y)
        inner, outer = (.5, 33.32), (1.96, 33.66)
        upper = curve(inner, (.68, 33.78), (1.36, 33.96), outer, n=44)
        lower = curve(inner, (.86, 33.02), (1.6, 33.08), outer, n=44)
        white = c.poly_mask([P(*p) for p in upper + lower[::-1]])
        # Plum eyeshadow sweeping out past the corner, and the lid crease.
        shadow = c.poly_mask([P(.46, 33.42), P(.62, 34.0), P(1.45, 34.2), P(2.3, 33.96), P(2.0, 33.58)])
        c.tint(blur(shadow, 8) * (1 - white), SHADOW_EYE, .5)
        c.line([P(*p) for p in curve((.68, 33.96), (1.05, 34.14), (1.6, 34.14), (1.95, 33.92), n=20)], rgb('#b9707f'), .028, .7)
        lid = smooth(33.35, 33.85, Y)
        c.put(white, mix(rgb('#fff8fa'), rgb('#dccbe0'), lid))
        cx, cy, rx, ry = 1.18, 33.42, .34, .45
        iris = c.ellipse_mask(P(cx, cy), (rx, ry)) * white
        t = np.clip((cy + ry - Y) / (2 * ry), 0, 1)
        col = mix(mix(IRIS[0], IRIS[1], smooth(.05, .4, t)), mix(IRIS[2], IRIS[3], smooth(.62, .95, t)), smooth(.32, .62, t))
        c.put(iris, col)
        c.put(iris * (1 - c.ellipse_mask(P(cx, cy), (rx - .05, ry - .055))), IRIS[0], .9)
        c.put(c.ellipse_mask(P(cx, cy + .02), (.11, .23)) * white, rgb('#120404'))
        c.put(c.ellipse_mask(P(cx, cy - .26), (.2, .09)) * iris, IRIS[4], .45)
        c.put(c.ellipse_mask(P(cx - .13, cy + .17), (.1, .085)) * white, rgb('#ffffff'))
        c.put(c.ellipse_mask(P(cx + .15, cy - .16), (.045, .04)) * white, rgb('#fff1d0'))
        # A heavy upper liner thickening outward into a lifted wing, lashes and a thin lower liner.
        top = [(p[0] + .03 * (k / 43), p[1] + .05 + .1 * (k / 43) ** 1.4) for k, p in enumerate(upper)]
        c.poly([P(*p) for p in upper[::-1] + top], LASH)
        c.poly([P(1.6, 33.84), P(2.34, 34.0), P(1.96, 33.64)], LASH)
        for k, (a, b) in enumerate(((1.5, 34.0), (1.7, 33.95), (1.88, 33.86), (2.04, 33.78))):
            c.line([P(a, b), P(a + .18, b + .16 - k * .04)], LASH, .038)
        c.line([P(*p) for p in lower[16:]], rgb('#5c1f36'), .04, .8)
        c.line([P(1.98, 33.64), P(2.12, 33.56)], rgb('#5c1f36'), .035, .7)
        # Long arched brow, its inner end set low.
        brow = curve((.36, 34.12), (.9, 34.44), (1.62, 34.62), (2.2, 34.4), n=30)
        for k, (a, b) in enumerate(zip(brow, brow[1:])):
            c.line([P(*a), P(*b)], rgb('#24142a'), .08 * (1 - .7 * k / len(brow)))
    eye(1)
    eye(-1)
    # Nose, the sly smirk (raised toward the viewer's right) and a beauty mark.
    c.line([(-.02, 32.86), (.06, 32.64)], SKIN[3], .035, .85)
    c.line([(.06, 32.64), (-.04, 32.6)], SKIN[3], .03, .5)
    mouth = curve((.44, 32.0), (.12, 31.92), (-.28, 31.94), (-.56, 32.16), n=30)
    lower_lip = curve((.3, 31.94), (.12, 31.8), (-.18, 31.8), (-.38, 31.96), n=20)
    c.poly(mouth[3:-5] + lower_lip[::-1], LIPS[2])
    c.put(c.ellipse_mask((.0, 31.86), (.09, .025)), LIPS[3], .75)
    c.line(mouth, LIPS[0], .045)
    c.line([(-.56, 32.16), (-.6, 32.24)], LIPS[0], .03, .85)
    c.put(c.ellipse_mask((1.62, 32.8), (.045, .045)), rgb('#3b1a26'))
    return c


def hair_fill(c, ramp, seed, light=.5):
    """Background hair mass for a canvas: broad lock bands, not pinstripes."""
    U, Vv = c.grid()
    s = strands(U / 1.6, Vv / 5, seed, density=1.0, wave=.2)
    tone = .45 + .3 * (s - .5) + (light - .5) * .5
    return cel(tone, ramp[1:4], (.38, .62))


HAIR_RAMP = (HAIR[0], HAIR[1], HAIR[2], HAIR[4], HAIR[5])
HAIR_TIP = rgb('#5a3d86')


def paint_bangs():
    """Front of the hair shell: centre-parted curtain locks, the forehead showing between them,
    and two long side locks framing the face."""
    c = front_canvas(2.82, (31.0, 37.05), 64)
    F = lambda p: (-p[0], p[1])          # design in viewer space (x to the viewer's right)
    c.put(c.poly_mask([F(p) for p in [(-2.9, 37.1), (2.9, 37.1), (2.9, 36.2), (-2.9, 36.2)]]), HAIR[2])
    locks = [
        # (centreline root .. tip, root width, shadow side)
        ((-1.95, 36.9), (-2.55, 35.2), (-2.7, 33.0), (-2.45, 31.0), .95, -1),
        ((1.95, 36.9), (2.55, 35.2), (2.7, 33.0), (2.45, 31.0), .95, 1),
        ((-1.2, 36.8), (-1.95, 35.5), (-2.25, 34.0), (-2.15, 32.4), .95, -1),
        ((1.25, 36.8), (1.98, 35.4), (2.28, 33.9), (2.2, 32.5), .95, 1),
        ((-.55, 36.7), (-1.2, 35.7), (-1.62, 34.7), (-1.72, 33.55), .9, -1),
        ((.6, 36.7), (1.25, 35.6), (1.66, 34.6), (1.78, 33.5), .9, 1),
        ((-.12, 36.6), (-.62, 35.8), (-1.02, 35.0), (-1.18, 34.2), .82, -1),
        ((.18, 36.6), (.66, 35.8), (1.04, 35.05), (1.2, 34.3), .8, 1),
        ((.05, 36.5), (.02, 35.6), (-.12, 34.9), (-.22, 34.25), .34, -1),
    ]
    for *ctrl, width, side in locks:
        draw_lock(c, curve(*ctrl, n=40), width, HAIR_RAMP, side=side, view=F, tip_tint=HAIR_TIP)
    return c


def paint_hair_side():
    """Side of the hair shell (u = z, front at the low end): long locks over the ear."""
    c = Canvas((-2.82, 2.78), (31.0, 37.05), 48)
    c.put(c.poly_mask([(-2.9, 37.1), (2.9, 37.1), (2.9, 33.0), (-2.9, 33.0)]), HAIR[2])
    for i, z in enumerate(np.linspace(-2.5, 2.4, 7)):
        tip = 31.0 + .5 * (i % 2) + (.4 if i == 0 else 0)
        draw_lock(c, curve((z - .2, 37.2), (z + .1, 35.0), (z + .25, 33.0), (z + .1, tip), n=36), 1.15, HAIR_RAMP,
                  side=1 if i % 2 else -1, tip_tint=HAIR_TIP, shine=(.25, .36))
    return c


def paint_hair_back():
    c = Canvas((-2.82, 2.82), (31.0, 37.05), 40)
    c.put(1.0, HAIR[2])
    for i, x in enumerate(np.linspace(-2.6, 2.6, 8)):
        draw_lock(c, curve((x * .7, 37.3), (x * .9, 35.0), (x, 33.0), (x * 1.02, 30.9), n=36), 1.1, HAIR_RAMP,
                  side=1 if x > 0 else -1, shine=(.24, .34), strands_n=1)
    return c


def paint_hair_top():
    c = Canvas((-2.82, 2.82), (-2.82, 2.78), 40)
    c.put(1.0, HAIR[2])
    crown = np.array([-.2, 1.2])
    for a in np.linspace(0, 2 * math.pi, 14, endpoint=False):
        d = np.array([math.sin(a), math.cos(a)])
        draw_lock(c, curve(tuple(crown), tuple(crown + d * 1.8), tuple(crown + d * 3.6), n=20), 1.3, HAIR_RAMP,
                  power=4, strands_n=1, shine=(.5, .7))
    c.line([(-.2, 1.0), (-.25, -2.9)], HAIR[0], .06)
    return c


def round_corners(c, u0, u1, top, radius):
    """Cut the two top corners of a canvas into quarter circles (rounds a box's silhouette)."""
    U, Vv = c.grid()
    keep = np.ones_like(U)
    for corner in (u0 + radius, u1 - radius):
        dx = np.where(corner < (u0 + u1) / 2, np.minimum(U - corner, 0), np.maximum(U - corner, 0))
        dy = np.maximum(Vv - (top - radius), 0)
        keep *= (np.hypot(dx, dy) <= radius).astype(float)
    c.keep(keep)


def round_top(c, half_x, z0, z1, radius):
    U, Vv = c.grid()
    dx = np.maximum(np.abs(U) - (half_x - radius), 0)
    dz = np.maximum(np.maximum(z0 + radius - Vv, Vv - (z1 - radius)), 0)
    c.keep((np.hypot(dx, dz) <= radius).astype(float))


def strand_canvas(width, length, seed, tips=3, light=.5, ramp=None, tip_tint=None, shine=(.08, .16), spread=.0):
    """A bundle of locks in local units: u across 0..width, v along 0..length (root at the top)."""
    ramp = ramp or HAIR_RAMP
    c = Canvas((0, width), (0, length), 36)
    rng = np.random.default_rng(seed)
    order = list(range(tips))
    rng.shuffle(order)
    for i in order:
        x = (i + .5) / tips * width
        sway = (rng.random() - .5) * .5 * width / tips
        tip_y = rng.uniform(0, .08) * length
        w = width / tips * 1.45
        draw_lock(c, curve((x, length + .2), (x + sway, length * .55), (x - sway + spread * (x - width / 2), tip_y), n=48),
                  w, ramp, side=1 if i % 2 else -1, power=3.2, shine=shine, tip_tint=tip_tint, strands_n=2)
    return c


# --------------------------------------------------------------------------- phase 1 costume
BLACK = V('#08060d', '#171021', '#271d36', '#3e3159', '#6a5a96')     # line, deep, base, light, sheen
VIOLET = V('#0e0820', '#22143e', '#362260', '#533a92', '#8a72c8')
CRIMSON = V('#2c0410', '#5e0c22', '#931934', '#c93556', '#ff7f9c')
LINING = V('#16041a', '#33092e', '#521442', '#7a2462', '#b4529a')
GOLD = V('#4a2e0c', '#88601e', '#c9973a', '#efcd76', '#fff3c6')
LEATHER = V('#050408', '#0e0b14', '#1a1424', '#352a48', '#7a6aa0')
TIGHTS = V('#07060c', '#141020', '#221a34', '#3a2e58', '#6c5c98')


def cloth(tone, ramp):
    """Cel shading over a 5-colour ramp (line, deep, base, light, sheen)."""
    out = cel(tone, ramp[1:4], (.36, .64))
    return mix(out, ramp[4], smooth(.9, .98, tone))


def folds(u, count, phase=0.0, sharp=1.0):
    s = np.sin(u * count * math.pi + phase)
    return np.sign(s) * np.abs(s) ** sharp


def gold_band(c, mask, horizontal=True):
    """Gold trim with a bright centre line and dark edges."""
    c.put(mask, GOLD[2])
    inner = np.clip(mask * 2 - 1, 0, 1)
    c.put(inner, GOLD[3], .8)


def gem(c, centre, r, ramp=CRIMSON, frame=GOLD):
    x, y = centre
    c.poly([(x, y + r * 1.35), (x + r, y), (x, y - r * 1.35), (x - r, y)], frame[1])
    c.poly([(x, y + r * 1.1), (x + r * .8, y), (x, y - r * 1.1), (x - r * .8, y)], ramp[2])
    c.poly([(x, y + r * 1.1), (x + r * .8, y), (x, y)], ramp[3])
    c.ellipse((x + r * .25, y + r * .45), (r * .16, r * .16), ramp[4])


def paint_torso_front():
    c = front_canvas(3.0, (13.8, 31.8), 40)
    X, Y = c.grid()
    D = -X
    A = np.abs(D)
    # Black bodice: darker toward the flanks, a cel shadow under the bust, soft vertical boning.
    tone = .6 - .3 * smooth(1.5, 2.9, A) + .05 * folds(D, 3.2, .4)
    tone = tone - .22 * smooth(24.6, 24.1, Y) * smooth(22.8, 23.6, Y)
    tone = tone + .12 * smooth(.6, 0, np.abs(Y - 25.8)) * smooth(2.2, .8, A)
    c.put(1.0, cloth(tone, BLACK))
    # Below the belt: dark violet tights.
    c.put(smooth(16.25, 16.15, Y), cloth(.55 - .25 * smooth(1.6, 2.9, A), TIGHTS))
    # Skin: neck and a sweetheart-V neckline down to the sternum.
    neck = (A < .76) & (Y > 28.8)
    neckline = 25.7 + 3.4 * np.clip(A / 2.45, 0, 1) ** 1.25 - .5 * np.sin(np.clip(A / 2.45, 0, 1) * math.pi) * smooth(.2, .9, A)
    skin = (((A < 2.42) & (Y > neckline)) | neck).astype(float)
    stone = .78 - .25 * smooth(30.9, 31.6, Y) * (A < .76) - .2 * smooth(1.6, 2.4, A) - .15 * smooth(29.0, 28.6, Y) * neck
    c.put(skin, mix(mix(SKIN[2], SKIN[1], smooth(.5, .64, stone)), SKIN[0], smooth(.72, .84, stone)))
    for s in (-1, 1):
        c.put(c.line_mask([(-s * x, 28.25 - .18 * (x - .4) ** 2) for x in np.linspace(.42, 1.75, 16)], .045) * skin, SKIN[2], .9)
    c.put(c.line_mask([(0, 26.9), (0, 26.2)], .05) * skin, SKIN[2], .7)
    # Gold piping along the neckline.
    pts = [(-x, 25.7 + 3.4 * (abs(x) / 2.45) ** 1.25 - .5 * math.sin(abs(x) / 2.45 * math.pi) * float(smooth(.2, .9, abs(x)))) for x in np.linspace(-2.42, 2.42, 60)]
    gold_band(c, c.line_mask(pts, .16))
    # Crimson centre panel laced in gold, princess seams and the waist cincher.
    panel = c.poly_mask([(-.5, 25.75), (.5, 25.75), (.42, 19.7), (-.42, 19.7)])
    c.put(panel, cloth(.62 + .1 * folds(Y, 6, 0, .5), CRIMSON))
    for y in np.arange(20.1, 25.3, .7):
        c.put(c.line_mask([(-.36, y), (.36, y + .5)], .07) * panel, GOLD[3])
        c.put(c.line_mask([(.36, y), (-.36, y + .5)], .07) * panel, GOLD[2])
    for s in (-1, 1):
        c.put(c.line_mask([(s * .52, 25.7), (s * .44, 19.7)], .08), GOLD[2])
        seam = [(s * x, y) for x, y in curve((1.55, 25.4), (1.3, 23.4), (1.0, 21.4), (.98, 19.7), n=20)]
        c.put(c.line_mask(seam, .07), GOLD[2], .9)
    gold_band(c, c.poly_mask([(-2.1, 20.05), (2.1, 20.05), (2.1, 19.6), (-2.1, 19.6)]))
    # Gold chain belt with a crescent-and-gem buckle.
    for x in np.arange(-2.85, 2.9, .38):
        c.put(c.ellipse_mask((x, 16.72 + .04 * math.cos(x * 2)), (.22, .13)), GOLD[1])
        c.put(c.ellipse_mask((x, 16.72 + .04 * math.cos(x * 2)), (.15, .07)), GOLD[3])
    gold_band(c, c.line_mask([(-2.9, 17.12), (2.9, 17.12)], .1))
    c.ellipse((0, 16.75), (.62, .62), GOLD[1])
    c.ellipse((0, 16.75), (.52, .52), GOLD[3])
    c.ellipse((.16, 16.82), (.42, .42), BLACK[2])
    gem(c, (0, 16.72), .28)
    # Cape clasps on the shoulders, joined by a draped gold chain across the collarbones.
    for s in (-1, 1):
        c.ellipse((s * 2.32, 28.55), (.42, .42), GOLD[1])
        c.ellipse((s * 2.32, 28.55), (.34, .34), GOLD[3])
        gem(c, (s * 2.32, 28.55), .2)
    chain = [(x, 28.45 - 1.25 * (1 - (x / 2.25) ** 2)) for x in np.linspace(-2.2, 2.2, 23)]
    for p in chain:
        c.put(c.ellipse_mask(p, (.08, .06)), GOLD[3])
    gem(c, (0, 27.12), .16)
    # Choker with a teardrop gem.
    c.put(c.poly_mask([(-.8, 30.5), (.8, 30.5), (.8, 30.05), (-.8, 30.05)]), BLACK[2])
    gold_band(c, c.line_mask([(-.8, 30.47), (.8, 30.47)], .06))
    gold_band(c, c.line_mask([(-.8, 30.08), (.8, 30.08)], .06))
    gem(c, (0, 29.86), .14)
    return c


def paint_torso_side():
    """Profile (u = z, front at the low end)."""
    c = Canvas((-2.6, 2.1), (13.8, 31.8), 40)
    Z, Y = c.grid()
    tone = .5 + .1 * smooth(-1.0, -2.2, Z) - .15 * smooth(.5, 1.8, Z)
    c.put(1.0, cloth(tone, BLACK))
    c.put(smooth(16.25, 16.15, Y), cloth(tone * .95, TIGHTS))
    neck = (Y > 28.8) & (np.abs(Z + .2) < .78)
    neckline = 26.0 + 2.6 * smooth(-2.3, .2, Z)
    skin = ((Y > neckline) & (Z < .3)) | neck
    c.put(skin.astype(float), mix(SKIN[2], SKIN[1], smooth(-.5, -1.2, Z)))
    gold_band(c, c.line_mask([(z, 26.0 + 2.6 * float(smooth(-2.3, .2, z))) for z in np.linspace(-2.3, .3, 30)], .14))
    c.put(c.line_mask([(-.1, 29.0), (.1, 17.2)], .07), GOLD[2], .9)
    gold_band(c, c.poly_mask([(-2.6, 20.05), (2.1, 20.05), (2.1, 19.6), (-2.6, 19.6)]))
    for z in np.arange(-2.4, 2.1, .38):
        c.put(c.ellipse_mask((z, 16.72), (.2, .13)), GOLD[1])
        c.put(c.ellipse_mask((z, 16.72), (.13, .07)), GOLD[3])
    c.put(c.poly_mask([(-1.2, 30.5), (.6, 30.5), (.6, 30.05), (-1.2, 30.05)]), BLACK[2])
    return c


def paint_torso_back():
    c = front_canvas(3.0, (13.8, 31.8), 40)
    X, Y = c.grid()
    A = np.abs(X)
    c.put(1.0, cloth(.5 - .25 * smooth(1.5, 2.9, A), BLACK))
    c.put(smooth(16.25, 16.15, Y), cloth(.5 - .2 * smooth(1.6, 2.9, A), TIGHTS))
    c.put(((A < .76) & (Y > 28.8)).astype(float), SKIN[2])
    for y in np.arange(19.9, 28.0, .62):
        c.put(c.line_mask([(-.3, y), (.3, y + .45)], .06), GOLD[3])
        c.put(c.line_mask([(.3, y), (-.3, y + .45)], .06), GOLD[2])
    gold_band(c, c.poly_mask([(-2.1, 20.05), (2.1, 20.05), (2.1, 19.6), (-2.1, 19.6)]))
    for x in np.arange(-2.85, 2.9, .38):
        c.put(c.ellipse_mask((x, 16.72), (.22, .13)), GOLD[1])
        c.put(c.ellipse_mask((x, 16.72), (.15, .07)), GOLD[3])
    c.put(c.poly_mask([(-.8, 30.5), (.8, 30.5), (.8, 30.05), (-.8, 30.05)]), BLACK[2])
    return c


TIERS_HEX = [  # (bone, y0, y1, half width, z front, z back)
    ('chest', 27.6, 29.25, 2.72, -1.42, 1.32), ('chest', 26.4, 27.6, 2.8, -1.78, 1.4),
    ('chest', 24.6, 26.4, 2.7, -2.12, 1.36), ('chest', 23.2, 24.6, 2.42, -1.84, 1.3),
    ('chest', 21.6, 23.2, 2.18, -1.6, 1.24), ('chest', 19.7, 21.6, 1.98, -1.46, 1.18),
    ('pelvis', 18.6, 20.3, 2.04, -1.47, 1.22), ('pelvis', 17.2, 18.6, 2.44, -1.6, 1.44),
    ('pelvis', 15.6, 17.2, 2.74, -1.72, 1.64), ('pelvis', 14.0, 15.6, 2.8, -1.66, 1.58)]


def torso(rig, front, side, back, top_colour, tiers=TIERS_HEX):
    proj = ByFace(front=Project(front), back=Project(back), side=Project(side, 'x'), top=Project(front),
                  bottom=Project(front))
    for bone, y0, y1, hw, zf, zb in tiers:
        rig.box(bone, (-hw, y0, zf), (hw, y1, zb), proj, name='torso', ppu=16)
    rig.box('neck', (-.82, 28.9, -1.0), (.82, 31.7, .58), ByFace(front=Project(front), back=Project(back), side=Project(side, 'x')),
            name='neck', ppu=16)


def sleeve(rig, side, s, ramp, cuff, ppu=14):
    """Upper arm, forearm with a flared cuff, and a slender hand with dark nails."""
    sh, el, wr = J[f'shoulder_{side}'], J[f'elbow_{side}'], J[f'wrist_{side}']

    def fabric(ctx):
        t = ctx['L'][..., 1] / ctx['size'][1]
        u = ctx['U']
        tone = .52 + .12 * folds(u, 2, .3) - .18 * (ctx['face'] in ('west', 'east')) * 0
        return cloth(tone + .1 * (1 - t), ramp)
    upper = Shader(fabric)
    rig.beam(f'arm_{side}', el, sh, 1.3, 1.34, upper, extend=(.25, .55), name=f'upper_arm_{side}', ppu=ppu)
    # Gold armlet just below the shoulder.
    r_arm = frame(sh - el, (0, 0, -1))
    centre = sh + (el - sh) * .28
    rig.cube(f'arm_{side}', centre, (1.46, .32, 1.5), Shader(lambda ctx: mix(GOLD[2], GOLD[3], smooth(.3, .7, ctx['V']))), r_arm,
             name=f'armlet_{side}', ppu=20)
    rig.beam(f'forearm_{side}', wr, el, 1.12, 1.16, upper, extend=(.2, .3), name=f'forearm_{side}', ppu=ppu)
    # Flared bell cuff: crimson lining, gold rim.
    r_fore = frame(el - wr, (0, 0, -1))

    def bell(ctx):
        t = ctx['L'][..., 1] / ctx['size'][1]
        col = cloth(.55 + .12 * folds(ctx['U'], 3), ramp)
        col = mix(col, GOLD[2], smooth(.2, .17, t))
        col = mix(col, GOLD[3], smooth(.12, .09, t) * smooth(.03, .06, t))
        return col
    centre = wr + (el - wr) * .17
    rig.cube(f'forearm_{side}', centre, (1.62, 1.5, 1.66), {f: Shader(bell) for f in ('north', 'south', 'east', 'west')},
             r_fore, name=f'cuff_{side}', ppu=16)
    rig.cube(f'forearm_{side}', wr + (el - wr) * .03, (1.5, .1, 1.54), {'down': Solid(cuff[2])}, r_fore, name=f'cuff_in_{side}', ppu=16)
    # Hand along the forearm direction: palm and fingers in one block, nails painted.
    d = unit(wr - el)

    def hand(ctx):
        t = ctx['L'][..., 1] / ctx['size'][1]       # 0 at the fingertips
        col = mix(SKIN[2], SKIN[1], smooth(.0, .5, t))
        nails = (t < .1) & (ctx['face'] == 'north')
        col = np.where(nails[..., None], VIOLET[2], col)
        knuckles = np.abs(t - .45) < .03
        col = np.where(knuckles[..., None], SKIN[2] * .92, col)
        if ctx['face'] in ('north', 'south'):
            gaps = (np.abs((ctx['U'] * 4) % 1 - .5) > .44) & (t < .45)
            col = np.where(gaps[..., None], SKIN[3], col)
        return col
    rig.beam(f'hand_{side}', wr + d * 2.0, wr - d * .1, .92, .5, Shader(hand), name=f'hand_{side}', ppu=24)
    thumb_a = wr + d * .5 + np.array([-s * .45, 0, -.15])
    rig.beam(f'hand_{side}', thumb_a + d * .9 + np.array([-s * .1, 0, -.15]), thumb_a, .3, .3, Solid(SKIN[1]), name=f'thumb_{side}', ppu=24)


def legs(rig, ramp_tights, ramp_boot, boot_top=10.8):
    for side, s in SIDES:
        hip, knee, ankle = J[f'hip_{side}'], J[f'knee_{side}'], J[f'ankle_{side}']

        def leg(ctx, s=s):
            y = ctx['P'][..., 1]
            u = ctx['U']
            tone = .5 + .3 * np.exp(-((u - .42) / .16) ** 2) * (ctx['face'] != 'south') - .08
            col = cloth(tone + .06 * smooth(14, 9, y), ramp_tights)
            boot = cloth(.5 + .25 * np.exp(-((u - .35) / .12) ** 2) * (ctx['face'] in ('north', 'east', 'west')), ramp_boot)
            col = mix(col, boot, smooth(boot_top + .02, boot_top - .02, y))
            band = np.abs(y - boot_top + .18) < .2
            col = np.where(band[..., None], mix(GOLD[2], GOLD[3], smooth(-.2, .2, y - boot_top + .18)), col)
            return col
        paint = Shader(leg)
        rig.box(f'leg_{side}', (hip[0] - 1.12, 11.4, -1.14), (hip[0] + 1.12, 15.3, 1.18), paint, name=f'thigh_{side}', ppu=14)
        rig.box(f'leg_{side}', (knee[0] - .94, 7.6, -1.02), (knee[0] + .94, 11.6, 1.02), paint, name=f'thigh_low_{side}', ppu=14)
        rig.box(f'shin_{side}', (knee[0] - .78, 4.4, -.9), (knee[0] + .78, 8.1, .86), paint, name=f'knee_{side}', ppu=14)
        rig.box(f'shin_{side}', (ankle[0] - .62, 1.5, -.72), (ankle[0] + .62, 4.6, .72), paint, name=f'shin_{side}', ppu=14)

        def shoe(ctx):
            u, v = ctx['U'], ctx['V']
            col = cloth(.45 + .3 * np.exp(-((u - .4) / .15) ** 2) * (ctx['face'] != 'down'), ramp_boot)
            return col
        a = ankle
        rig.box(f'foot_{side}', (a[0] - .58, .55, -1.6), (a[0] + .58, 1.95, .62), Shader(shoe), name=f'boot_{side}', ppu=16)
        rig.box(f'foot_{side}', (a[0] - .48, .05, -2.75), (a[0] + .48, .95, -1.4), Shader(shoe), name=f'toe_{side}', ppu=16)
        rig.box(f'foot_{side}', (a[0] - .22, 0, .1), (a[0] + .22, .9, .55), Solid(GOLD[2]), name=f'heel_{side}', ppu=16)


# --------------------------------------------------------------------------- panels (skirts, capes, collars)
def edge_points(a, b, style):
    """Points from a to b (exclusive of b) along a decorated edge. style: (kind, count, depth) with
    kind 'points' (V tips), 'scallop' (round lobes) or 'tatter'; depth > 0 bulges outward."""
    a, b = np.asarray(a, float), np.asarray(b, float)
    if not style:
        return [tuple(a)]
    kind, count, depth = style
    d = b - a
    n = np.array([d[1], -d[0]]) / max(np.linalg.norm(d), 1e-9)       # right-hand normal (outward for CCW)
    out = []
    steps = count * 12
    rng = np.random.default_rng(int(abs(a[0] * 977 + a[1] * 131)) % 99991)
    jitter = rng.uniform(.6, 1.0, count + 1)
    for k in range(steps):
        t = k / steps
        f = (t * count) % 1
        i = int(t * count)
        if kind == 'points':
            bulge = depth * (1 - abs(f - .5) * 2) ** 1.4
        elif kind == 'scallop':
            bulge = depth * math.sin(f * math.pi) ** .7
        else:
            bulge = depth * jitter[i] * (1 - abs(f - .5) * 2) ** 2.2
        out.append(tuple(a + d * t + n * bulge))
    return out


def panel(rig, bone, quad, painter_fn, outward, thickness=.07, ppu=11, edges=None, name='panel', emissive=False):
    """A flat two-sided sheet through four 3D corners (top-left, top-right, bottom-right, bottom-left
    as seen from outside). painter_fn(canvas, outline) paints the outer side when outer=True."""
    q = np.array(quad, float)
    x = unit((q[1] - q[0]) + (q[2] - q[3]))
    y = (q[0] + q[1]) / 2 - (q[2] + q[3]) / 2
    y = unit(y - x * (y @ x))
    z = np.cross(x, y)
    if z @ np.asarray(outward, float) < 0:
        x = -x
        z = np.cross(x, y)
        q = q[[1, 0, 3, 2]]
    R = np.column_stack((x, y, z))
    centre = q.mean(axis=0)
    local = (q - centre) @ R
    edges = edges or {}
    # Outline (counter-clockwise in local x/y): bottom-left, bottom-right, top-right, top-left.
    corners = [local[3, :2], local[2, :2], local[1, :2], local[0, :2]]
    outline = []
    for i in range(4):
        outline += edge_points(corners[i], corners[(i + 1) % 4], edges.get(('bottom', 'right', 'top', 'left')[i]))
    return shaped(rig, bone, centre, R, outline, painter_fn, thickness, ppu, name, emissive)


def shaped(rig, bone, origin, R, outline, painter_fn, thickness=.07, ppu=11, name='sheet', emissive=False):
    """A two-sided sheet whose silhouette is `outline` (local x/y around `origin`, counter-clockwise
    seen from the local +Z side). painter_fn(canvas, outline, outer) paints each side."""
    pts = np.array(outline, float)
    lo, hi = pts.min(axis=0) - .05, pts.max(axis=0) + .05
    size = np.array([hi[0] - lo[0], hi[1] - lo[1], thickness])
    mid = (lo + hi) / 2
    world_centre = np.asarray(origin, float) + R @ np.array([mid[0], mid[1], 0])
    poly = [(p[0] - lo[0], p[1] - lo[1]) for p in pts]
    canvases = []
    for outer in (False, True):
        c = Canvas((0, size[0]), (0, size[1]), max(24, ppu * 2.5))
        painter_fn(c, poly, outer)
        c.keep(c.poly_mask(poly))
        canvases.append(c)
    painter = PlaneLocal(canvases[0], back=canvases[1], emissive=emissive)
    return rig.cube(bone, world_centre, size, {'north': painter, 'south': painter}, R, name=name, ppu=ppu)


def ellipse_point(a, rx, rz, y, shift=0.0):
    """a = 0 is the front (-Z), increasing toward +X."""
    return np.array([rx * math.sin(a), y, -rz * math.cos(a) + shift])


def fabric_painter(outer_ramp, inner_ramp, fold_count=3.0, hem_band=None, motif=None, seed=0, top_dark=.2):
    def paint(c, poly, outer):
        U, Vv = c.grid()
        h = c.v1
        u = U / c.u1
        t = Vv / h                                               # 1 at the top
        ramp = outer_ramp if outer else inner_ramp
        tone = .55 + .17 * folds(u + .07 * np.sin(t * 5 + seed), fold_count, seed, .7) - top_dark * smooth(.75, 1, t) + .08 * (1 - t)
        c.put(1.0, cloth(tone, ramp))
        if hem_band and outer:
            low = hem_band
            band = (Vv > low[0]) & (Vv < low[1])
            c.put(band.astype(float), GOLD[1])
            c.put(((Vv > low[0] + .08) & (Vv < low[1] - .08)).astype(float), GOLD[2])
            vine = np.abs(Vv - (low[0] + low[1]) / 2 - .09 * np.sin(U * 9)) < .045
            c.put(vine.astype(float), GOLD[3])
        if motif and outer:
            motif(c, U, Vv)
        # Hem piping follows the decorated outline.
        rim = c.line_mask(poly + [poly[0]], .09, caps=False)
        c.put(rim * (Vv < h * .55), GOLD[2] if outer else inner_ramp[3], .95)
    return paint


def skirt(rig, outer, inner, y_top=17.5, y_bot=.5, top=(2.95, 1.95), bot=(6.2, 5.0), shift=.8, open_front=.62,
          count=9, hem=('points', 1, .9), hem_band=(1.0, 1.5), motif=None):
    """An open-front petal skirt: panels around the back and sides, pointed hems."""
    span = 2 * math.pi - 2 * open_front
    step = span / count
    for k in range(count):
        a0 = open_front + k * step - step * .06
        a1 = open_front + (k + 1) * step + step * .06
        tl, tr = ellipse_point(a1, *top, y_top), ellipse_point(a0, *top, y_top)
        br, bl = ellipse_point(a0, *bot, y_bot, shift), ellipse_point(a1, *bot, y_bot, shift)
        mid = (a0 + a1) / 2
        bone = 'sash_l' if math.sin(mid) > .5 else 'sash_r' if math.sin(mid) < -.5 else 'pelvis'
        outward = (math.sin(mid), 0, -math.cos(mid))
        panel(rig, bone, (tl, tr, br, bl), fabric_painter(outer, inner, 2.0, hem_band, motif, seed=k), outward,
              edges={'bottom': hem}, name=f'skirt_{k}', ppu=10)


# --------------------------------------------------------------------------- phase 1 sculpt
def head_hex(rig):
    face = paint_face_hex()
    skin = SKIN
    dark = Solid(HAIR[2])
    rig.box('head', (-2.5, 31.2, -2.5), (2.5, 36.4, 2.4),
            {'north': Project(face), 'east': dark, 'west': dark, 'south': dark, 'up': dark,
             'down': Solid(rgb('#b57d8c'))}, name='face', ppu=30)
    bangs, side, back, top = paint_bangs(), paint_hair_side(), paint_hair_back(), paint_hair_top()

    def ring(ctx):
        P = ctx['P']
        inside = (np.abs(P[..., 0]) < 2.48) & (P[..., 2] < 2.38)
        out = np.ones(ctx['shape'] + (4,))
        out[..., :3] = HAIR[1]
        out[..., 3] = ~inside
        return out
    round_corners(bangs, -2.82, 2.82, 37.05, .95)
    round_corners(back, -2.82, 2.82, 37.05, .95)
    round_corners(side, -2.82, 2.78, 37.05, .95)
    round_top(top, 2.82, -2.82, 2.78, .95)
    rig.box('head', (-2.82, 31.0, -2.82), (2.82, 37.05, 2.78),
            {'north': Project(bangs), 'east': Project(side, 'x'), 'west': Project(side, 'x'),
             'south': Project(back), 'up': Project(top, 'y'), 'down': Shader(ring)}, name='hair_shell', ppu=20)


def hair_bundle(rig, bone, root, tip, width, depth, seed, ppu=14, ramp=None, tip_tint=HAIR_TIP, tips=3, z_hint=(0, 0, -1)):
    """A lock of long hair: a beam from tip to root painted with strands on all four long faces."""
    length = float(np.linalg.norm(np.asarray(root, float) - np.asarray(tip, float)))
    broad = PlaneLocal(strand_canvas(width, length, seed, tips=tips, ramp=ramp, tip_tint=tip_tint))
    narrow = PlaneLocal(strand_canvas(depth, length, seed + 1, tips=max(1, tips // 2), ramp=ramp, tip_tint=tip_tint), axes=(2, 1))
    top = Solid((ramp or HAIR_RAMP)[2])
    return rig.beam(bone, tip, root, width, depth, {'north': broad, 'south': broad, 'east': narrow, 'west': narrow, 'up': top},
                    z_hint=z_hint, name='hair', ppu=ppu)


def cape_painter(outer, inner, emblem=None):
    def paint(c, poly, is_outer):
        U, Vv = c.grid()
        h = c.v1
        t = Vv / h
        ramp = outer if is_outer else inner
        tone = .56 + .16 * folds(U / c.u1 + .05 * np.sin(t * 4), 2.5, .8, .7) - .12 * smooth(.6, 1, t) + .06 * (1 - t)
        c.put(1.0, cloth(tone - (0 if is_outer else .12), ramp))
        if emblem and is_outer:
            emblem(c, U, Vv)
        rim = c.line_mask(poly + [poly[0]], .1, caps=False)
        c.put(rim * (Vv < h - .2), GOLD[2] if is_outer else GOLD[1])
    return paint


def cape_emblem(c, U, Vv):
    """A gold crescent cradling a crimson eye, with a ring of thorns, high on the cape's back."""
    cx, cy = c.u1 / 2, c.v1 * .62
    r = np.hypot(U - cx, Vv - cy)
    c.put(((r > 1.75) & (r < 1.9)).astype(float), GOLD[2])
    a = np.arctan2(Vv - cy, U - cx)
    thorns = (r > 1.9) & (r < 1.9 + .35 * np.clip(1 - np.abs(((a / (2 * math.pi) * 16) % 1) - .5) * 4, 0, 1))
    c.put(thorns.astype(float), GOLD[1])
    moon = (np.hypot(U - cx, Vv - cy + .15) < 1.35) & (np.hypot(U - cx, Vv - cy - .35) > 1.2)
    c.put(moon.astype(float), GOLD[3])
    eye = (np.abs(U - cx) / .75) ** 2 + ((Vv - cy - .25) / .32) ** 2 < 1
    c.put(eye.astype(float), CRIMSON[2])
    c.put(((np.abs(U - cx) / .18) ** 2 + ((Vv - cy - .25) / .3) ** 2 < 1).astype(float), CRIMSON[0])
    for k in range(5):
        y = cy - 2.6 - k * .5
        c.put(c.line_mask([(cx - .9 + k * .12, y), (cx + .9 - k * .12, y)], .05), GOLD[2], .8)


def collar_painter(c, poly, is_outer):
    """Wine lining that brightens toward the rim, stiffening ribs fanning from the base, gold piping."""
    U, Vv = c.grid()
    t = Vv / c.v1
    base_x = poly[0][0]
    if is_outer:
        c.put(1.0, cloth(.48 + .08 * folds(U / c.u1, 2.0) + .14 * t, BLACK))
    else:
        tone = .3 + .5 * t ** .8 + .05 * folds(U / c.u1, 3.0, .5)
        c.put(1.0, cloth(tone, LINING))
        for k in range(5):
            tip = poly[int(len(poly) * (.3 + k * .1))]
            c.put(c.line_mask([(base_x + .3 + k * .45, .25), tip], .05), LINING[1], .55)
    rim = c.line_mask(poly + [poly[0]], .14, caps=False)
    c.put(rim * (Vv > .3), GOLD[2])
    c.put(c.line_mask(poly + [poly[0]], .05, caps=False) * (Vv > .3), GOLD[3])


def collar_wing(s):
    """Outline of one collar wing (x outward from the neck, y up): a fan with three soft points."""
    pts = curve((0, 0), (1.2, -.1), (2.4, -.15), n=8)
    pts += curve((2.4, -.15), (3.4, 2.4), (4.0, 5.2), (4.7, 8.0), n=24)
    pts += curve((4.7, 8.0), (4.0, 7.9), (3.3, 8.3), (2.6, 9.2), n=12)[1:]
    pts += curve((2.6, 9.2), (2.1, 8.7), (1.5, 8.9), (.85, 9.9), n=12)[1:]
    pts += curve((.85, 9.9), (.35, 8.6), (.1, 6.0), (0, 0), n=20)[1:-1]
    pts = [(s * x, y) for x, y in pts]
    return pts if s > 0 else pts[::-1]


def orb_canvas(radius, core, mid, rim, ring=None):
    c = Canvas((-radius, radius), (-radius, radius), 48)
    X, Y = c.grid()
    r = np.hypot(X, Y) / radius
    col = mix(mix(core, mid, smooth(.0, .6, r)), rim, smooth(.6, .98, r))
    c.put((r < 1).astype(float), col)
    c.put(c.ellipse_mask((-radius * .32, radius * .34), (radius * .22, radius * .14)), rgb('#ffffff'), .85)
    if ring is not None:
        c.put(((r > .78) & (r < .86)).astype(float), ring)
    return c


def ring_canvas(radius, width, colour, runes=None, ticks=0):
    c = Canvas((-radius, radius), (-radius, radius), 40)
    X, Y = c.grid()
    r = np.hypot(X, Y)
    c.put(((r > radius - width) & (r < radius)).astype(float), colour)
    if ticks:
        a = np.arctan2(Y, X)
        tick = (np.abs(((a / (2 * math.pi) * ticks) % 1) - .5) > .42) & (r > radius - width * 2.6) & (r < radius)
        c.put(tick.astype(float), colour)
    if runes is not None:
        runes(c, X, Y, r)
    return c


def rune_circle(c, X, Y, r, outer, colours, seed=3):
    """Concentric magic circle with a band of angular runes and an inscribed star."""
    main, second = colours
    a = np.arctan2(Y, X)
    c.put(((r > outer - .16) & (r < outer)).astype(float), main)
    c.put(((r > outer - .5) & (r < outer - .42)).astype(float), main)
    c.put(((r > outer * .62) & (r < outer * .62 + .07)).astype(float), second)
    rng = np.random.default_rng(seed)
    count = 28
    band = (r > outer - .42) & (r < outer - .16)
    k = np.floor((a + math.pi) / (2 * math.pi) * count).astype(int) % count
    f = ((a + math.pi) / (2 * math.pi) * count) % 1
    shapes = rng.integers(0, 4, count)
    rr = (r - (outer - .42)) / .26
    glyph = np.zeros_like(r, bool)
    for kind in range(4):
        sel = shapes[k] == kind
        if kind == 0:
            g = (np.abs(f - .5) < .08) | (np.abs(rr - .5) < .12) & (np.abs(f - .5) < .3)
        elif kind == 1:
            g = np.abs((f - .2) - rr * .6) < .09
        elif kind == 2:
            g = (np.abs(f - .3) < .07) | (np.abs(f - .7) < .07) & (rr > .4)
        else:
            g = np.abs(np.hypot(f - .5, (rr - .5) * .8) - .3) < .08
        glyph |= sel & g
    c.put((band & glyph & (np.abs(f - .5) < .38)).astype(float), second)
    # Inscribed pentagram.
    pts = [(math.cos(math.pi / 2 + i * 4 * math.pi / 5) * outer * .6, math.sin(math.pi / 2 + i * 4 * math.pi / 5) * outer * .6) for i in range(6)]
    c.put(c.line_mask(pts, .06), second)


def staff(rig, side, ramp, metal, gem_ramp, glow_ramp):
    """A tall crescent staff held in the hand, its foot planted a little forward."""
    wr, el = J[f'wrist_{side}'], J[f'elbow_{side}']
    d = unit(wr - el)
    fist = wr + d * .95
    bottom = np.array([fist[0] - .02, .5, -3.6])
    axis = unit(fist - bottom)
    top = fist + axis * 18.2
    R = frame(axis, (0, 0, -1))

    def shaft(ctx):
        t = ctx['L'][..., 1]
        tone = .55 + .25 * np.sin(ctx['U'] * math.pi)
        col = cloth(tone, ramp)
        col = mix(col, metal[1], (np.abs(((t * .9 + ctx['U'] * .5) % 1) - .5) < .03) * .5)
        rings = np.zeros_like(t, bool)
        for y in (2.0, 9.0, 16.5, 25.0, 33.0):
            rings |= np.abs(t - y) < .22
        return np.where(rings[..., None], mix(metal[2], metal[3], smooth(.3, .7, ctx['U'])), col)
    length = float(np.linalg.norm(top - bottom))
    rig.cube(f'weapon_{side}', (top + bottom) / 2, (.44, length, .44), Shader(shaft), R, name='staff', ppu=14)
    # Crescent head: a painted gold crescent sheet across the staff's plane, a gem at its heart.
    head = top + axis * 1.6
    c = Canvas((-2.4, 2.4), (-2.4, 2.4), 64)
    X, Y = c.grid()
    outer = np.hypot(X, Y + .3) < 2.25
    inner = np.hypot(X - .0, Y - .55) < 1.85
    moon = outer & ~inner & (Y < 1.7)
    c.put(moon.astype(float), mix(metal[2], metal[3], smooth(-2.2, .5, Y)))
    c.put(c.line_mask([(x, y) for x, y in zip(np.cos(np.linspace(3.5, 5.92, 30)) * 2.1, np.sin(np.linspace(3.5, 5.92, 30)) * 2.1 - .3)], .07), metal[4])
    for sx in (-1, 1):
        c.put(c.poly_mask([(sx * 1.55, 1.2), (sx * 2.05, 2.35), (sx * 1.25, 1.55)]), metal[2])
    c.put(c.poly_mask([(-.18, -2.4), (.18, -2.4), (.25, -1.6), (-.25, -1.6)]), metal[1])
    rig.sheet(f'weapon_{side}', head, 4.8, 4.8, PlaneLocal(c, centered=True), R, thickness=.12, name='staff_crescent', ppu=18)
    gemc = Canvas((-.8, .8), (-1.2, 1.2), 64)
    GX, GY = gemc.grid()
    diamond = np.abs(GX) / .78 + np.abs(GY) / 1.18 < 1
    gemc.put(diamond.astype(float), mix(glow_ramp[1], glow_ramp[3], smooth(-1, 1, GY - GX * .6)))
    gemc.put((diamond & (np.abs(GX) / .78 + np.abs(GY) / 1.18 > .78)).astype(float), glow_ramp[4])
    gemc.put(gemc.poly_mask([(-.2, .7), (0, .95), (.08, .5)]), rgb('#ffffff'))
    crystal = PlaneLocal(gemc, emissive=True, centered=True)
    for spin in (0, 90):
        rig.sheet(f'weapon_{side}', head + axis * .55, 1.6, 2.4, crystal, R @ rot((0, spin, 0)), thickness=.05, name='glow_staff_gem', ppu=24)


def grimoire(rig, cover, gold, page_glow):
    b = J['book']
    R = rot((-35, 0, 0))
    w, h = 1.85, 2.45

    def leather(ctx):
        u, v = ctx['U'], ctx['V']
        col = cloth(.5 + .15 * np.sin(u * 9) * .2, cover)
        frame_ = (np.minimum(np.minimum(u, 1 - u), np.minimum(v, 1 - v)) < .1)
        return np.where(frame_[..., None], gold[3], col)
    for s in (-1, 1):
        r = R @ rot((0, -s * 18, 0))
        rig.cube('book', b + R @ np.array([s * (w / 2 + .03), 0, .1]), (w, h, .16), Shader(leather), r, name='grimoire_cover', ppu=18)
    pages = Canvas((0, w - .15), (0, h - .15), 40)
    PX, PY = pages.grid()
    pages.put(1.0, mix(rgb('#cdb79a'), rgb('#8e7466'), smooth(0, w, PX)))
    for y in np.arange(.3, h - .3, .26):
        pages.put(pages.line_mask([(.2, y), (w - .4, y)], .045), page_glow[1], .8)
    ring = pages.ellipse_mask(((w - .15) / 2, (h - .15) / 2), (.5, .5)) - pages.ellipse_mask(((w - .15) / 2, (h - .15) / 2), (.4, .4))
    pages.put(ring, page_glow[3])
    paint = PlaneLocal(pages)
    for s in (-1, 1):
        r = R @ rot((0, -s * 18, 0))
        rig.cube('book', b + R @ np.array([s * (w / 2 - .02), 0, -.1]), (w - .15, h - .15, .14), {'north': paint, 'south': paint}, r,
                 name='pages', ppu=18)


def magic_circle(rig, bone, centre, radius, colours, r=None, name='glow_circle', ppu=20):
    c = ring_canvas(radius, .14, colours[0], runes=lambda cc, X, Y, rr: rune_circle(cc, X, Y, rr, radius, colours))
    painter = PlaneLocal(c, emissive=True, centered=True)
    return rig.sheet(bone, centre, 2 * radius, 2 * radius, painter, r, thickness=.04, name=name, ppu=ppu)


def orb(rig, bone, centre, radius, colours):
    core, mid, rim, ring = colours
    painter = PlaneLocal(orb_canvas(radius, core, mid, rim), emissive=True, centered=True)
    for angles in ((0, 0, 0), (0, 90, 0), (90, 0, 0)):
        rig.sheet(bone, centre, 2 * radius, 2 * radius, painter, rot(angles), thickness=.04, name='glow_orb', ppu=22)
    ring_paint = PlaneLocal(ring_canvas(radius * 1.65, .16, ring, ticks=12), emissive=True, centered=True)
    for angles in ((70, 0, 20), (70, 0, -35)):
        rig.sheet(bone, centre, radius * 3.3, radius * 3.3, ring_paint, rot(angles), thickness=.04, name='glow_orb_ring', ppu=18)


def sculpt_hex():
    rig = Rig('arkhaia_malefeasance')
    skeleton(rig)
    head_hex(rig)
    torso(rig, paint_torso_front(), paint_torso_side(), paint_torso_back(), BLACK[2])
    for side, s in SIDES:
        sleeve(rig, side, s, BLACK, CRIMSON)
    legs(rig, TIGHTS, LEATHER)
    # Long hair: five fanned locks down the back, two framing locks over the chest.
    for i, x in enumerate((-1.4, -.7, 0, .7, 1.4)):
        low = 16.6 + abs(x) * 1.1
        hair_bundle(rig, 'neck', (x, 32.6, 2.45), (x * 1.95, low, 3.95 - abs(x) * .3), 1.42, .78, 40 + i, z_hint=(0, 0, 1))
    for side, s in SIDES:
        hair_bundle(rig, 'neck', (s * 2.42, 34.5, -2.28), (s * 2.98, 22.4, -2.62), 1.02, .46, 50 + (s > 0), tips=2)
    # Open-front petal skirt, violet with a crimson lining, over split apron panels.
    skirt(rig, VIOLET, LINING, open_front=.95, count=8, hem=('points', 1, 1.1), hem_band=(1.3, 1.85))
    # Cape with a wine lining, and the towering collar.
    cape = cape_painter(BLACK, LINING)
    cape_back = cape_painter(BLACK, LINING, emblem=cape_emblem)
    upper = [((-2.35, 28.9, 2.05), (2.35, 28.9, 2.05), (2.95, 15.0, 3.75), (-2.95, 15.0, 3.75), (0, 0, 1)),
             ((2.35, 28.9, 2.05), (3.95, 28.6, .45), (5.5, 15.0, 1.9), (2.95, 15.0, 3.75), (1, 0, .6)),
             ((-3.95, 28.6, .45), (-2.35, 28.9, 2.05), (-2.95, 15.0, 3.75), (-5.5, 15.0, 1.9), (-1, 0, .6))]
    for i, (a, b, c_, d, out) in enumerate(upper):
        panel(rig, 'cape', (a, b, c_, d), cape, out, name=f'cape_{i}', ppu=9)
    lower = [((-2.95, 15.1, 3.75), (2.95, 15.1, 3.75), (3.7, .7, 5.5), (-3.7, .7, 5.5), (0, 0, 1)),
             ((2.95, 15.1, 3.75), (5.5, 15.1, 1.9), (6.9, .9, 3.6), (3.7, .7, 5.5), (1, 0, .6)),
             ((-5.5, 15.1, 1.9), (-2.95, 15.1, 3.75), (-3.7, .7, 5.5), (-6.9, .9, 3.6), (-1, 0, .6))]
    for i, (a, b, c_, d, out) in enumerate(lower):
        panel(rig, 'cape_tip', (a, b, c_, d), cape_back if i == 0 else cape, out, edges={'bottom': ('points', 3, 1.1)}, name=f'cape_low_{i}', ppu=9)
    for s in (-1, 1):
        shaped(rig, 'collar', (s * 1.8, 28.7, 2.15), rot((12, s * 35, 0)), collar_wing(s), collar_painter, ppu=12,
               name='collar')
    # Props: crescent staff, floating grimoire, spell circle, the violet orb and the rune halo.
    staff(rig, 'l', BLACK, GOLD, CRIMSON, VIOLET)
    grimoire(rig, BLACK, GOLD, CRIMSON)
    magic_circle(rig, 'focus', J['focus'], 1.5, (rgb('#d84aff'), rgb('#ff5c86')), rot((90, 0, 0)), name='glow_focus')
    orb(rig, 'familiar', J['familiar'], 1.15, (rgb('#ffffff'), rgb('#c48cff'), rgb('#5a24b0'), rgb('#ffcf70')))
    magic_circle(rig, 'halo', (0, 33.2, 4.95), 6.3, (rgb('#b26cff'), rgb('#ff6a8e')), name='glow_halo', ppu=14)
    return rig


# --------------------------------------------------------------------------- phase 2 palette
SKIN2 = V('#fff7f2', '#fde5dd', '#f4bdc3', '#d98d9e')
HAIR2 = V('#5d4c9c', '#8f7dd2', '#bcaef0', '#e6deff', '#ffffff')    # line, deep, base, light, shine
HAIR2_TIP = rgb('#86dcf4')
NAVY = V('#070a1e', '#111739', '#1d275e', '#324398', '#6a80d8')
FRILL = V('#5a5c84', '#a9abd2', '#dcdef4', '#f6f7ff', '#ffffff')
CYAN = V('#05303e', '#0c6a86', '#1cb0d2', '#6ce3f7', '#dcfcff')
PINK = V('#4a0f2e', '#a3306a', '#e2629e', '#ff9fcd', '#ffe3f2')
IRIS2 = V('#0a1850', '#123f98', '#1c8ed6', '#6ee6ff', '#e2fdff')
TIERS_IMP = [
    ('chest', 27.6, 29.1, 2.6, -1.38, 1.28), ('chest', 26.4, 27.6, 2.66, -1.64, 1.34),
    ('chest', 24.8, 26.4, 2.56, -1.88, 1.3), ('chest', 23.4, 24.8, 2.3, -1.7, 1.26),
    ('chest', 21.6, 23.4, 2.08, -1.52, 1.2), ('chest', 19.6, 21.6, 1.9, -1.4, 1.14),
    ('pelvis', 18.4, 20.0, 1.98, -1.42, 1.18), ('pelvis', 16.8, 18.4, 2.3, -1.52, 1.36),
    ('pelvis', 15.2, 16.8, 2.55, -1.62, 1.52), ('pelvis', 14.0, 15.2, 2.6, -1.56, 1.48)]


def sparkle(c, centre, size, colour, thin=.28):
    x, y = centre
    k = size * thin
    c.poly([(x, y + size), (x + k, y + k), (x + size, y), (x + k, y - k), (x, y - size), (x - k, y - k), (x - size, y), (x - k, y + k)], colour)


def star_points(cx, cy, r, inner=.45, n=5, rot0=90):
    pts = []
    for i in range(2 * n):
        a = math.radians(rot0 + i * 180 / n)
        rr = r if i % 2 == 0 else r * inner
        pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    return pts


def paint_face_imp():
    """Cute and smug: big starry cyan eyes, a fang grin and hatched blush."""
    c = front_canvas(2.8, (30.9, 37.0), 90)
    X, Y = c.grid()
    D = -X
    A = np.abs(D)
    tone = .85 - .18 * smooth(1.7, 2.8, A) - .32 * smooth(34.95, 35.7, Y)
    c.put(1.0, mix(mix(SKIN2[2], SKIN2[1], smooth(.45, .62, tone)), SKIN2[0], smooth(.7, .86, tone)))
    jaw = 30.98 + 2.4 * np.clip(A / 2.8, 0, 1) ** 2.8
    below = smooth(jaw + .03, jaw - .06, Y)
    c.tint(below, SKIN2[3], .5)
    c.tint(below * smooth(jaw - .35, jaw - .03, Y), SKIN2[2], .5)
    edge = c.line_mask([(-x, 30.98 + 2.4 * (abs(x) / 2.8) ** 2.8) for x in np.linspace(-2.8, 2.8, 80)], .03)
    c.tint(edge * smooth(.15, .6, A), rgb('#d58a9c'), .6)
    for s in (-1, 1):
        c.tint(blur(c.ellipse_mask((-s * 1.62, 32.22), (.58, .24)), 10), rgb('#ff86b0'), .5)
        for k in range(3):
            x0 = 1.34 + k * .22
            c.line([(-s * (x0 - .05), 32.36), (-s * (x0 + .07), 32.12)], rgb('#ee5f96'), .032, .75)

    def eye(s):
        def P(dx, y):
            return (-s * dx, y)
        inner, outer = (.42, 33.2), (2.02, 33.5)
        upper = curve(inner, (.56, 33.9), (1.5, 34.06), outer, n=44)
        lower = curve(inner, (.64, 32.34), (1.7, 32.32), outer, n=44)
        white = c.poly_mask([P(*p) for p in upper + lower[::-1]])
        c.put(white, mix(rgb('#ffffff'), rgb('#cfd6f2'), smooth(33.2, 33.95, Y)))
        cx, cy, rx, ry = 1.23, 33.1, .53, .68
        iris = c.ellipse_mask(P(cx, cy), (rx, ry)) * white
        t = np.clip((cy + ry - Y) / (2 * ry), 0, 1)
        col = mix(mix(IRIS2[0], IRIS2[1], smooth(.05, .35, t)), mix(IRIS2[2], IRIS2[3], smooth(.6, .95, t)), smooth(.3, .6, t))
        c.put(iris, col)
        c.put(iris * (1 - c.ellipse_mask(P(cx, cy), (rx - .06, ry - .07))), IRIS2[0], .9)
        c.put(c.ellipse_mask(P(cx, cy + .06), (.17, .28)) * white, rgb('#081030'))
        c.put(c.ellipse_mask(P(cx, cy - .36), (.32, .17)) * iris, IRIS2[4], .55)
        sx, sy = P(cx - .2, cy + .26)
        sparkle(c, (sx, sy), .24, rgb('#ffffff'))
        c.put(c.ellipse_mask(P(cx + .2, cy - .3), (.075, .075)) * white, rgb('#ffffff'))
        c.put(c.ellipse_mask(P(cx + .26, cy + .32), (.05, .05)) * white, rgb('#e8fbff'))
        top = [(p[0] + .02 * (k / 43), p[1] + .06 + .06 * (k / 43) ** 1.2) for k, p in enumerate(upper)]
        c.poly([P(*p) for p in upper[::-1] + top], rgb('#24183e'))
        c.poly([P(1.8, 33.9), P(2.24, 34.1), P(2.02, 33.48)], rgb('#24183e'))
        for k, (a, b) in enumerate(((1.86, 33.86), (2.02, 33.72))):
            c.line([P(a, b), P(a + .2, b + .1 - k * .06)], rgb('#24183e'), .04)
        c.line([P(*p) for p in lower[22:40]], rgb('#6b4a8e'), .035, .7)
        c.line([P(1.95, 33.4), P(2.1, 33.3)], rgb('#6b4a8e'), .03, .7)
        brow = curve((.62, 34.56), (1.08, 34.78), (1.6, 34.78), (1.95, 34.62), n=24)
        c.line([P(*p) for p in brow], HAIR2[0], .055)
    eye(1)
    eye(-1)
    c.line([(.04, 32.5), (-.03, 32.42)], rgb('#e09aa6'), .035, .9)
    # Open smug grin with one fang.
    top = curve((.44, 31.98), (.16, 31.88), (-.16, 31.88), (-.44, 31.98), n=24)
    bottom = curve((-.44, 31.98), (-.26, 31.56), (.26, 31.56), (.44, 31.98), n=24)
    mouth = c.poly_mask(top + bottom)
    c.put(mouth, rgb('#8a1f3c'))
    c.put(c.ellipse_mask((0, 31.66), (.24, .11)) * mouth, rgb('#ff86a8'))
    c.poly([(.3, 31.93), (.15, 31.9), (.22, 31.72)], rgb('#ffffff'))
    c.line(top, rgb('#5a1428'), .045)
    c.line(bottom, rgb('#5a1428'), .03, .8)
    for sx in (-1, 1):
        c.line([(sx * .44, 31.98), (sx * .52, 32.06)], rgb('#5a1428'), .035, .9)
    return c


HAIR2_RAMP = (HAIR2[0], HAIR2[1], HAIR2[2], HAIR2[3], HAIR2[4])


def paint_bangs_imp():
    c = front_canvas(3.15, (30.6, 37.55), 60)
    F = lambda p: (-p[0], p[1])
    c.put(c.poly_mask([F(p) for p in [(-3.2, 37.6), (3.2, 37.6), (3.2, 36.8), (-3.2, 36.8)]]), HAIR2[2])
    for sx in (-1, 1):
        draw_lock(c, curve((sx * 2.3, 37.5), (sx * 2.9, 35.4), (sx * 3.0, 33.0), (sx * 2.7, 30.7), n=40), 1.15, HAIR2_RAMP,
                  side=sx, view=F, tip_tint=HAIR2_TIP)
        draw_lock(c, curve((sx * 1.9, 37.5), (sx * 2.4, 35.6), (sx * 2.5, 33.6), (sx * 2.25, 32.0), n=40), 1.0, HAIR2_RAMP,
                  side=sx, view=F, tip_tint=HAIR2_TIP)
    tips = [.3, 0, .25, -.12, .2, .02, .3]
    for i, x in enumerate([-1.9, -1.26, -.63, 0, .63, 1.26, 1.9]):
        bend = .12 * np.sign(x) if x else -.08
        draw_lock(c, curve((x * .82, 37.5), (x * .96, 36.3), (x * 1.02, 35.4), (x * 1.04 + bend, 34.6 + tips[i]), n=36), .82,
                  HAIR2_RAMP, side=1 if x >= 0 else -1, view=F, power=2.2, tip_tint=HAIR2_TIP)
    return c


def paint_hair_side_imp():
    c = Canvas((-3.1, 2.95), (30.6, 37.55), 44)
    c.put(c.poly_mask([(-3.2, 37.6), (3.0, 37.6), (3.0, 33.5), (-3.2, 33.5)]), HAIR2[2])
    for i, z in enumerate(np.linspace(-2.8, 2.6, 7)):
        tip = 30.7 + .45 * (i % 2)
        draw_lock(c, curve((z - .2, 37.7), (z + .1, 35.2), (z + .25, 33.0), (z + .1, tip), n=36), 1.2, HAIR2_RAMP,
                  side=1 if i % 2 else -1, tip_tint=HAIR2_TIP)
    return c


def paint_hair_back_imp():
    c = Canvas((-3.15, 3.15), (30.6, 37.55), 40)
    c.put(1.0, HAIR2[2])
    for x in np.linspace(-2.9, 2.9, 8):
        draw_lock(c, curve((x * .7, 37.8), (x * .9, 35.2), (x, 33.0), (x * 1.02, 30.5), n=36), 1.2, HAIR2_RAMP,
                  side=1 if x > 0 else -1, strands_n=1, tip_tint=HAIR2_TIP)
    return c


def bow_canvas(width, height, ramp, knot=None, tails=True):
    """A ribbon bow seen from the front, centred on its knot (local units, centred canvas)."""
    c = Canvas((-width / 2, width / 2), (-height / 2, height / 2), 56)
    sx_, sy_ = width / 4.2, height / 4.2
    S = lambda pts: [(x * sx_, y * sy_) for x, y in pts]
    if tails:
        for sx in (-1, 1):
            tail = S([(sx * .1, -.1), (sx * .55, -.4), (sx * 1.15, -1.9), (sx * .8, -1.6), (sx * .55, -2.0), (sx * .2, -.4)])
            c.poly(tail, ramp[1])
            c.put(c.line_mask(tail + [tail[0]], .05), ramp[0], .8)
    for sx in (-1, 1):
        loop = S([(0, .1), (sx * .5, .9), (sx * 1.4, 1.3), (sx * 1.95, .95), (sx * 2.0, .1), (sx * 1.6, -.6), (sx * .8, -.55), (0, -.15)])
        c.poly(loop, ramp[2])
        inner = S([(sx * .35, .05), (sx * .8, .55), (sx * 1.4, .7), (sx * 1.6, .2), (sx * 1.2, -.25), (sx * .6, -.25)])
        c.poly(inner, ramp[1])
        c.put(c.line_mask(S([(sx * .5, .9), (sx * 1.4, 1.3), (sx * 1.95, .95)]), .06), ramp[3])
        c.put(c.line_mask(loop + [loop[0]], .055), ramp[0], .85)
    c.ellipse((0, 0), (.42 * sx_, .5 * sy_), ramp[2])
    c.put(c.line_mask(S([(-.25, .3), (.25, .3)]), .05), ramp[3])
    if knot is not None:
        c.poly(star_points(0, 0, .55 * sy_, .45), GOLD[2])
        c.poly(star_points(0, .04, .38 * sy_, .45), knot)
    return c


def bow(rig, bone, centre, R, width, height, ramp, knot=None, tails=True, name='bow'):
    painter = PlaneLocal(bow_canvas(width, height, ramp, knot, tails), centered=True)
    return rig.sheet(bone, centre, width, height, painter, R, thickness=.08, name=name, ppu=20)


def hat(rig, bone='head_forward'):
    """A huge midnight witch hat, tipped to one side, its point curling back; cyan band and bow."""
    R_hat = rot((-4, 0, 9))
    base = np.array([0, 36.25, .25])
    # Brim: round alpha disc painted top and bottom.
    up = Canvas((-6.5, 6.5), (-6.5, 6.5), 28)
    down = Canvas((-6.5, 6.5), (-6.5, 6.5), 28)
    X, Z = up.grid()
    r = np.hypot(X, Z)
    a = np.arctan2(Z, X)
    edge = 6.1 + .14 * np.sin(a * 6)
    disc = (r < edge).astype(float)
    tone = .52 + .25 * smooth(edge - 2.4, edge - .4, r) - .25 * smooth(3.0, 3.6, r) * 0
    up.put(disc, cloth(tone, NAVY))
    up.put(((r > edge - .3) & (r < edge - .14)).astype(float), GOLD[2])
    rng = np.random.default_rng(7)
    for _ in range(16):
        ang, rad = rng.uniform(0, 2 * math.pi), rng.uniform(3.8, 5.6)
        sparkle(up, (math.cos(ang) * rad, math.sin(ang) * rad), .16, GOLD[3])
    down.put(disc, cloth(.42 + .2 * smooth(3, edge, r), V('#06121e', '#0b2a40', '#11405e', '#1e6488', '#4aa0c8')))
    brim = ByFace(top=PlaneLocal(up, axes=(0, 2), centered=True), bottom=PlaneLocal(down, axes=(0, 2), centered=True))
    rig.cube(bone, base + R_hat @ np.array([0, .1, 0]), (13, .2, 13), {'up': brim, 'down': brim}, R_hat, name='hat_brim', ppu=12)
    def cone_painter(band):
        def paint(c, poly, outer):
            U, Vv = c.grid()
            u = U / c.u1
            tone = .52 + .14 * np.sin(u * math.pi) + .04 * Vv / max(c.v1, 1)
            col = cloth(tone, NAVY) if outer else NAVY[1]
            c.put(1.0, col)
            if not outer:
                return
            k = (np.floor(U / .8) * 7 + np.floor(Vv / .8) * 13 + int(band * 5)) % 6
            fx, fy = (U / .8) % 1, (Vv / .8) % 1
            star = (k == 0) & ((np.abs(fx - .5) + np.abs(fy - .5)) < .26) & (np.minimum(np.abs(fx - .5), np.abs(fy - .5)) < .07)
            c.put(star.astype(float), GOLD[3])
            if band:
                lo_ = min(p[1] for p in poly)
                b0, b1 = lo_ + .15, lo_ + 1.55
                c.put(((Vv > b0) & (Vv < b1)).astype(float), cloth(.55 + .15 * np.sin(u * math.pi), CYAN))
                c.put(((np.abs(Vv - b0) < .07) | (np.abs(Vv - b1) < .07)).astype(float), GOLD[3])
        return paint
    sides = 8
    # Lower cone (with the band) from the brim to a waist, then a point bending back.
    rings = [(0.0, 4.3, np.eye(3), np.zeros(3)), (5.4, 1.9, np.eye(3), np.zeros(3))]
    bend = rot((38, 0, 0))
    top_centre = np.array([0, 5.4, 0]) + bend @ np.array([0, 3.8, 0])

    def ring(h, r, k, R_=np.eye(3), centre=None):
        a = 2 * math.pi * k / sides
        c0 = np.array([0, h, 0]) if centre is None else centre
        return c0 + R_ @ np.array([r * math.sin(a), 0, -r * math.cos(a)])
    for section in range(2):
        for k in range(sides):
            if section == 0:
                bl, br = ring(0, 4.3, k + 1), ring(0, 4.3, k)
                tl, tr = ring(5.6, 1.85, k + 1), ring(5.6, 1.85, k)
            else:
                bl, br = ring(5.4, 1.95, k + 1), ring(5.4, 1.95, k)
                tl, tr = ring(0, .14, k + 1, bend, top_centre), ring(0, .14, k, bend, top_centre)
            quad = [base + R_hat @ v for v in (tl, tr, br, bl)]
            mid_a = 2 * math.pi * (k + .5) / sides
            out = R_hat @ np.array([math.sin(mid_a), .3, -math.cos(mid_a)])
            panel(rig, bone, quad, cone_painter(section == 0), out, thickness=.06, name='hat_cone', ppu=11)
    R = R_hat @ bend
    tip = base + R_hat @ top_centre + R @ np.array([0, .2, 0])
    star = Canvas((-.8, .8), (-.8, .8), 64)
    star.poly(star_points(0, 0, .78, .45), GOLD[1])
    star.poly(star_points(0, 0, .6, .45), GOLD[3])
    star.poly(star_points(0, 0, .3, .45), rgb('#fff6d0'))
    painter = PlaneLocal(star, centered=True, emissive=True)
    for spin in (0, 90):
        rig.sheet(bone, tip, 1.6, 1.6, painter, R @ rot((0, spin, 0)), thickness=.05, name='glow_hat_star', ppu=24)
    # Bow on the band, and a crescent charm dangling from the brim.
    bow(rig, bone, base + R_hat @ np.array([1.6, .95, -3.5]), R_hat @ rot((0, -12, -8)), 3.3, 2.7, CYAN, knot=rgb('#fff1a8'), name='hat_bow')
    charm = Canvas((-.7, .7), (-1.6, 1.6), 64)
    charm.line([(0, 1.6), (0, .45)], GOLD[3], .05)
    moon = charm.ellipse_mask((0, -.15), (.6, .6)) * (1 - charm.ellipse_mask((.28, .05), (.5, .5)))
    charm.put(moon, GOLD[3])
    charm.put(charm.ellipse_mask((-.3, -.1), (.12, .12)), CYAN[3])
    rig.sheet(bone, base + R_hat @ np.array([-4.9, -1.5, -2.6]), 1.4, 3.2, PlaneLocal(charm, centered=True), R_hat,
              thickness=.04, name='hat_charm', ppu=24)


def paint_torso_front_imp():
    c = front_canvas(3.0, (13.8, 31.0), 40)
    X, Y = c.grid()
    D = -X
    A = np.abs(D)
    tone = .58 - .28 * smooth(1.4, 2.8, A) + .05 * folds(D, 3, .3)
    tone = tone - .18 * smooth(24.8, 24.3, Y) * smooth(23.0, 23.8, Y)
    c.put(1.0, cloth(tone, NAVY))
    neck = (A < .8) & (Y > 28.85)
    c.put(neck.astype(float), mix(SKIN2[2], SKIN2[1], smooth(29.0, 30.6, Y)))
    # Frilled high neckline, cyan lacing with gold eyelets, a white sash at the waist.
    for k, x in enumerate(np.linspace(-2.6, 2.6, 14)):
        c.ellipse((x, 28.95), (.24, .22), FRILL[3 if k % 2 else 2])
    c.put(c.line_mask([(-2.6, 28.8), (2.6, 28.8)], .08), CYAN[2])
    for y in np.arange(20.4, 27.6, .7):
        c.put(c.line_mask([(-.32, y), (.32, y + .5)], .07), CYAN[3])
        c.put(c.line_mask([(.32, y), (-.32, y + .5)], .07), CYAN[2])
        for sx in (-1, 1):
            c.ellipse((sx * .4, y + .25), (.07, .07), GOLD[3])
    for s in (-1, 1):
        c.put(c.line_mask([(s * x, y) for x, y in curve((1.5, 26.8), (1.25, 24.2), (1.0, 22.0), (.95, 20.1), n=20)], .06), GOLD[2], .9)
    c.put(c.poly_mask([(-2.2, 20.15), (2.2, 20.15), (2.2, 19.45), (-2.2, 19.45)]), FRILL[3])
    c.put(c.line_mask([(-2.2, 19.8), (2.2, 19.8)], .05), CYAN[2], .7)
    sparkle(c, (0, 19.8), .38, GOLD[3])
    return c


def paint_torso_side_imp():
    c = Canvas((-2.4, 2.0), (13.8, 31.0), 40)
    Z, Y = c.grid()
    c.put(1.0, cloth(.5 + .1 * smooth(-1.0, -2.0, Z) - .15 * smooth(.5, 1.6, Z), NAVY))
    neck = (Y > 28.85) & (np.abs(Z + .2) < .8)
    c.put(neck.astype(float), SKIN2[2])
    for k, z in enumerate(np.linspace(-2.2, 1.9, 10)):
        c.ellipse((z, 28.95), (.24, .22), FRILL[3 if k % 2 else 2])
    c.put(c.poly_mask([(-2.4, 20.15), (2.0, 20.15), (2.0, 19.45), (-2.4, 19.45)]), FRILL[3])
    c.put(c.line_mask([(-.1, 28.6), (.1, 20.2)], .06), GOLD[2], .8)
    return c


def paint_torso_back_imp():
    c = front_canvas(3.0, (13.8, 31.0), 40)
    X, Y = c.grid()
    A = np.abs(X)
    c.put(1.0, cloth(.5 - .25 * smooth(1.4, 2.8, A), NAVY))
    c.put(((A < .8) & (Y > 28.85)).astype(float), SKIN2[2])
    for k, x in enumerate(np.linspace(-2.6, 2.6, 14)):
        c.ellipse((x, 28.95), (.24, .22), FRILL[3 if k % 2 else 2])
    c.put(c.poly_mask([(-2.2, 20.15), (2.2, 20.15), (2.2, 19.45), (-2.2, 19.45)]), FRILL[3])
    return c


def sleeve_imp(rig, side, s):
    sh, el, wr = J[f'shoulder_{side}'], J[f'elbow_{side}'], J[f'wrist_{side}']

    def puff(ctx):
        t = ctx['L'][..., 1] / ctx['size'][1]
        u = ctx['U']
        col = cloth(.5 + .2 * np.sin(u * math.pi) + .08 * folds(u, 3), NAVY)
        cuff = t < .2
        col = np.where(cuff[..., None], cloth(.6 + .15 * np.sin(u * math.pi), FRILL), col)
        col = np.where((np.abs(t - .2) < .04)[..., None], CYAN[2], col)
        k = (np.floor(ctx['L'][..., 0] / .55) * 5 + np.floor(ctx['L'][..., 1] / .55) * 3 + np.floor(ctx['L'][..., 2] / .55) * 7) % 7
        fx, fy = (ctx['L'][..., 0] / .55) % 1, (ctx['L'][..., 1] / .55) % 1
        dot = (k == 0) & (np.hypot(fx - .5, fy - .5) < .18) & (t > .26)
        return np.where(dot[..., None], GOLD[3], col)
    a, b = el + (sh - el) * .3, sh + (sh - el) * .08
    rig.beam(f'arm_{side}', a + (b - a) * .18, b - (b - a) * .1, 2.1, 2.15, Shader(puff), name=f'puff_{side}', ppu=14)
    rig.beam(f'arm_{side}', a, b + (b - a) * .06, 1.66, 1.7, Shader(puff), name=f'puff_end_{side}', ppu=14)
    skin = Shader(lambda ctx: mix(SKIN2[2], SKIN2[1], .35 + .5 * np.sin(ctx['U'] * math.pi)))
    rig.beam(f'arm_{side}', el, a, 1.1, 1.12, skin, extend=(.2, 0), name=f'upper_arm_{side}', ppu=14)
    rig.beam(f'forearm_{side}', wr, el, 1.02, 1.04, skin, extend=(.15, .2), name=f'forearm_{side}', ppu=14)
    r_fore = frame(el - wr, (0, 0, -1))

    def frill(ctx):
        t = ctx['L'][..., 1] / ctx['size'][1]
        u = ctx['U']
        col = cloth(.62 + .12 * np.sin(u * math.pi * 6), FRILL)
        col = np.where((np.abs(t - .7) < .1)[..., None], CYAN[2], col)
        alpha = (t > .18 * np.abs(np.sin(u * math.pi * 4))).astype(float)
        return np.concatenate([col, alpha[..., None]], axis=-1)
    rig.cube(f'forearm_{side}', wr + (el - wr) * .1, (1.5, .75, 1.52), {f: Shader(frill) for f in ('north', 'south', 'east', 'west')},
             r_fore, name=f'wrist_frill_{side}', ppu=20)
    d = unit(wr - el)

    def hand(ctx):
        t = ctx['L'][..., 1] / ctx['size'][1]
        col = mix(SKIN2[2], SKIN2[1], smooth(.0, .5, t))
        nails = (t < .1) & (ctx['face'] == 'north')
        col = np.where(nails[..., None], PINK[3], col)
        if ctx['face'] in ('north', 'south'):
            gaps = (np.abs((ctx['U'] * 4) % 1 - .5) > .44) & (t < .45)
            col = np.where(gaps[..., None], SKIN2[3], col)
        return col
    rig.beam(f'hand_{side}', wr + d * 1.85, wr - d * .1, .9, .5, Shader(hand), name=f'hand_{side}', ppu=24)
    thumb_a = wr + d * .5 + np.array([-s * .43, 0, -.15])
    rig.beam(f'hand_{side}', thumb_a + d * .85 + np.array([-s * .1, 0, -.15]), thumb_a, .3, .3, Solid(SKIN2[1]), name=f'thumb_{side}', ppu=24)


def legs_imp(rig, top=10.7):
    for side, s in SIDES:
        hip, knee, ankle = J[f'hip_{side}'], J[f'knee_{side}'], J[f'ankle_{side}']

        def leg(ctx):
            y = ctx['P'][..., 1]
            u = ctx['U']
            skin = mix(SKIN2[2], SKIN2[1], .3 + .55 * np.sin(u * math.pi))
            stripe = ((y * 1.6) % 1) < .5
            sock = np.where(stripe[..., None], cloth(.55 + .2 * np.sin(u * math.pi), NAVY), cloth(.6 + .2 * np.sin(u * math.pi), FRILL))
            col = mix(skin, sock, smooth(top + .03, top - .03, y))
            band = np.abs(y - top + .2) < .22
            return np.where(band[..., None], CYAN[2], col)
        paint = Shader(leg)
        rig.box(f'leg_{side}', (hip[0] - 1.02, 11.4, -1.06), (hip[0] + 1.02, 15.3, 1.1), paint, name=f'thigh_{side}', ppu=14)
        rig.box(f'leg_{side}', (knee[0] - .88, 7.6, -.96), (knee[0] + .88, 11.6, .96), paint, name=f'thigh_low_{side}', ppu=14)
        rig.box(f'shin_{side}', (knee[0] - .76, 4.4, -.86), (knee[0] + .76, 8.1, .84), paint, name=f'knee_{side}', ppu=14)
        rig.box(f'shin_{side}', (ankle[0] - .62, 1.5, -.7), (ankle[0] + .62, 4.6, .7), paint, name=f'shin_{side}', ppu=14)
        # Stocking-top bows on the outside of each thigh.
        bow(rig, f'leg_{side}', (knee[0] + s * .92, top - .1, -.2), rot((0, s * 90, 0)), 1.5, 1.2, CYAN, tails=False, name=f'sock_bow_{side}')

        def shoe(ctx):
            u = ctx['U']
            col = cloth(.45 + .32 * np.exp(-((u - .4) / .15) ** 2) * (ctx['face'] != 'down'), NAVY)
            return col
        a = ankle
        rig.box(f'foot_{side}', (a[0] - .62, .35, -1.55), (a[0] + .62, 1.75, .66), Shader(shoe), name=f'shoe_{side}', ppu=16)
        rig.box(f'foot_{side}', (a[0] - .56, 0, -2.45), (a[0] + .56, 1.05, -1.3), Shader(shoe), name=f'toe_{side}', ppu=16)
        rig.box(f'foot_{side}', (a[0] - .3, 0, .15), (a[0] + .3, .7, .62), Solid(NAVY[1]), name=f'heel_{side}', ppu=16)
        rig.box(f'foot_{side}', (a[0] - .66, 1.15, -1.25), (a[0] + .66, 1.42, -.85), Shader(lambda ctx: mix(CYAN[2], CYAN[3], ctx['V'])),
                name=f'strap_{side}', ppu=20)
        rig.box(f'foot_{side}', (a[0] + s * .62 - .14, 1.06, -1.18), (a[0] + s * .62 + .14, 1.5, -.92), Solid(GOLD[3]), name=f'buckle_{side}', ppu=20)


def ring_skirt(rig, painter, y_top, y_bot, top, bot, shift, count, hem, name, ppu=10):
    step = 2 * math.pi / count
    for k in range(count):
        a0, a1 = k * step - step * .07, (k + 1) * step + step * .07
        tl, tr = ellipse_point(a1, *top, y_top), ellipse_point(a0, *top, y_top)
        br, bl = ellipse_point(a0, *bot, y_bot, shift), ellipse_point(a1, *bot, y_bot, shift)
        mid = (a0 + a1) / 2
        bone = 'sash_l' if math.sin(mid) > .5 else 'sash_r' if math.sin(mid) < -.5 else 'pelvis'
        panel(rig, bone, (tl, tr, br, bl), painter, (math.sin(mid), 0, -math.cos(mid)), edges={'bottom': hem}, name=f'{name}_{k}', ppu=ppu)


def skirt_imp_painter(c, poly, outer):
    U, Vv = c.grid()
    t = Vv / c.v1
    u = U / c.u1
    tone = .56 + .16 * folds(u, 2, .4, .7) - .12 * smooth(.7, 1, t)
    if outer:
        col = cloth(tone, NAVY)
        k = (np.floor(U / .8) * 7 + np.floor(Vv / .8) * 13) % 5
        fx, fy = (U / .8) % 1, (Vv / .8) % 1
        star = (k == 0) & ((np.abs(fx - .5) + np.abs(fy - .5)) < .26) & (np.minimum(np.abs(fx - .5), np.abs(fy - .5)) < .07)
        col = np.where(star[..., None], GOLD[3], col)
        c.put(1.0, col)
        band = (Vv > .55) & (Vv < 1.05)
        c.put(band.astype(float), CYAN[2])
        c.put(((Vv > .62) & (Vv < .7) | (Vv > .9) & (Vv < .98)).astype(float), GOLD[3])
    else:
        c.put(1.0, cloth(tone - .1, NAVY))
    rim = c.line_mask(poly + [poly[0]], .08, caps=False)
    c.put(rim * (Vv < .9), GOLD[2] if outer else NAVY[3])


def petticoat_painter(c, poly, outer):
    U, Vv = c.grid()
    u = U / c.u1
    tone = .62 + .12 * folds(u, 4, .2, .6)
    c.put(1.0, cloth(tone, FRILL))
    # Lace eyelets just above the scalloped edge.
    holes = (np.hypot((U % .5) - .25, Vv - .55) < .09)
    c.erase(holes.astype(float))
    c.put(c.line_mask([(0, .85), (c.u1, .85)], .05), CYAN[3], .9)


def bat_wing(s):
    pts = [(0, .6), (1.0, 1.9), (2.4, 3.1), (4.0, 3.9), (5.8, 3.8), (5.2, 2.2), (4.9, .2), (4.1, .9), (3.4, -1.1),
           (2.6, .0), (1.7, -1.2), (1.0, -.1), (.2, -.4)]
    pts = [(s * x, y) for x, y in pts]
    return pts if s > 0 else pts[::-1]


def wing_painter(s):
    def paint(c, poly, outer):
        U, Vv = c.grid()
        base_x = poly[0][0] if s > 0 else poly[-1][0]
        tone = .45 + .2 * smooth(0, c.u1, np.abs(U - base_x))
        c.put(1.0, mix(cloth(tone, NAVY), PINK[1], smooth(.6, 1, np.abs(U - base_x) / c.u1) * .35))
        root = (base_x + s * .2, c.v1 * .55)
        for tip in ((4.0 * s, 3.9), (5.8 * s, 3.8), (4.9 * s, .2), (3.4 * s, -1.1), (1.7 * s, -1.2)):
            # Outline coordinates are offset by the canvas low corner; tips are given in outline space.
            tx = tip[0] - min(p[0] for p in bat_wing(s)) + .05
            ty = tip[1] - min(p[1] for p in bat_wing(s)) + .05
            c.put(c.line_mask([root, (tx, ty)], .08), NAVY[0], .9)
        c.put(c.line_mask(poly + [poly[0]], .08, caps=False), PINK[2] if outer else NAVY[3], .9)
    return paint


def tail(rig):
    pts = [(0, 16.4, 1.55), (.5, 14.6, 3.0), (1.5, 12.6, 3.9), (2.6, 11.2, 3.9)]
    for a, b in zip(pts, pts[1:]):
        rig.beam('pelvis', a, b, .38, .38, Shader(lambda ctx: cloth(.5 + .2 * np.sin(ctx['U'] * math.pi), NAVY)),
                 z_hint=(0, 0, 1), extend=(.1, .1), name='tail', ppu=16)
    heart = Canvas((-.9, .9), (-.9, .9), 64)
    X, Y = heart.grid()
    shape = ((X ** 2 + (Y * 1.15 - np.sqrt(np.abs(X)) * .62) ** 2) < .62)
    heart.put(shape.astype(float), PINK[2])
    heart.put((shape & ((X ** 2 + (Y * 1.15 - np.sqrt(np.abs(X)) * .62) ** 2) < .4)).astype(float), PINK[3])
    heart.put(heart.ellipse_mask((-.25, .25), (.12, .1)), rgb('#ffffff'))
    rig.sheet('pelvis', (3.05, 10.7, 3.8), 1.8, 1.8, PlaneLocal(heart, centered=True), rot((0, -40, 30)), thickness=.06, name='tail_heart', ppu=24)


def star_wand(rig, side):
    wr, el = J[f'wrist_{side}'], J[f'elbow_{side}']
    d = unit(wr - el)
    fist = wr + d * .9
    axis = unit(np.array([.55, .25, -.8]))
    a, b = fist - axis * 1.2, fist + axis * 6.2
    rig.beam(f'weapon_{side}', a, b, .32, .32, Shader(lambda ctx: np.where(((ctx['L'][..., 1] * 1.6 % 1) < .5)[..., None], FRILL[3], GOLD[2])),
             z_hint=(0, 1, 0), name='wand', ppu=16)
    star = Canvas((-1.7, 1.7), (-1.7, 1.7), 56)
    star.poly(star_points(0, 0, 1.65, .48), GOLD[1])
    star.poly(star_points(0, 0, 1.42, .48), GOLD[3])
    star.poly(star_points(0, 0, .95, .48), CYAN[2])
    star.poly(star_points(0, .05, .55, .48), CYAN[4])
    sparkle(star, (-.35, .45), .22, rgb('#ffffff'))
    R = frame(axis, (0, 0, -1)) @ rot((90, 0, 0))
    painter = PlaneLocal(star, centered=True, emissive=True)
    tip = b + axis * 1.25
    for spin in (0, 90):
        rig.sheet(f'weapon_{side}', tip, 3.4, 3.4, painter, frame(axis, (0, 0, -1)) @ rot((0, spin, 0)), thickness=.06, name='glow_wand_star', ppu=20)
    bow(rig, f'weapon_{side}', b - axis * .3, frame(axis, (0, 0, -1)), 1.8, 1.5, PINK, tails=True, name='wand_bow')


def star_sprite(rig):
    c = Canvas((-1.6, 1.6), (-1.6, 1.6), 64)
    c.poly(star_points(0, 0, 1.55, .5), GOLD[1])
    c.poly(star_points(0, 0, 1.36, .5), rgb('#ffe27a'))
    for sx in (-1, 1):
        c.ellipse((sx * .32, .12), (.1, .16), rgb('#2a1a3e'))
        c.ellipse((sx * .32 - .03, .18), (.035, .05), rgb('#ffffff'))
        c.ellipse((sx * .62, -.16), (.16, .08), rgb('#ff9cc0'), .8)
    c.line([(-.14, -.14), (0, -.24), (.14, -.14)], rgb('#2a1a3e'), .05)
    c.poly([(.06, -.2), (.13, -.36), (.18, -.19)], rgb('#ffffff'))
    painter = PlaneLocal(c, centered=True, emissive=True)
    centre = J['familiar']
    for spin in (0, 90):
        rig.sheet('familiar', centre, 3.2, 3.2, painter, rot((0, spin, 0)), thickness=.05, name='glow_sprite', ppu=20)
    for s in (-1, 1):
        wing = Canvas((0, 1.6), (0, 1.2), 64)
        pts = [(0, .6), (.6, 1.1), (1.5, 1.15), (1.3, .6), (1.4, .1), (.9, .35), (.6, 0), (.3, .4)]
        wing.poly(pts, NAVY[2])
        wing.put(wing.line_mask(pts + [pts[0]], .05), PINK[2])
        R = rot((0, s * -25, 0))
        centre_w = centre + np.array([s * 1.9, .7, .6])
        rig.sheet('familiar', centre_w, 1.6, 1.2, PlaneLocal(wing if s > 0 else wing), R @ (rot((0, 180, 0)) if s < 0 else np.eye(3)),
                  thickness=.04, name='sprite_wing', ppu=24)


def spellbook(rig):
    b = J['book']
    R = rot((-25, 20, 10))

    def cover(ctx):
        u, v = ctx['U'], ctx['V']
        col = cloth(.55 + .1 * np.sin(u * 7) * .2, NAVY)
        rim = np.minimum(np.minimum(u, 1 - u), np.minimum(v, 1 - v)) < .1
        col = np.where(rim[..., None], GOLD[3], col)
        if ctx['face'] in ('north', 'south'):
            st = (np.abs(u - .5) + np.abs(v - .5)) < .16
            col = np.where(st[..., None], CYAN[3], col)
        return col
    pages = Shader(lambda ctx: mix(rgb('#fff7e6'), rgb('#e2d2bc'), ((ctx['V'] * 9) % 1 > .5) * .6))
    rig.cube('book', b, (1.7, 2.3, .62), {'north': Shader(cover), 'south': Shader(cover), 'west': Shader(cover),
                                          'east': pages, 'up': pages, 'down': pages}, R, name='spellbook', ppu=18)


def round_collar(rig):
    """A frilled white collar resting on the shoulders, front and back halves, with a cyan bow."""
    for k, tilt in enumerate((-112, 112)):
        pts = []
        for th in np.linspace(0, math.pi, 50):
            rr = 3.0 + .18 * abs(math.sin(th * 7))
            pts.append((math.cos(th) * rr, math.sin(th) * rr))
        pts += [(math.cos(th) * .95, math.sin(th) * .95) for th in np.linspace(math.pi, 0, 20)]
        if tilt > 0:
            pts = [(-x, y) for x, y in pts][::-1]

        def paint(c, poly, outer):
            U, Vv = c.grid()
            cx = c.u1 / 2
            r = np.hypot(U - cx, Vv - .05)
            c.put(1.0, cloth(.62 + .12 * np.sin(np.arctan2(Vv, U - cx) * 14), FRILL))
            c.put(((r > 2.35) & (r < 2.5)).astype(float), CYAN[2])
            c.put(c.line_mask(poly + [poly[0]], .05, caps=False), FRILL[1], .8)
        shaped(rig, 'collar', (0, 29.0, -.15 if tilt < 0 else .1), rot((tilt, 0, 0)), pts, paint, ppu=16, name='round_collar')
    bow(rig, 'collar', (0, 28.1, -2.15), rot((-8, 0, 0)), 3.0, 2.5, CYAN, knot=rgb('#fff1a8'), name='chest_bow')


def head_imp(rig):
    face = paint_face_imp()
    dark = Solid(HAIR2[1])
    rig.box('head', (-2.8, 30.9, -2.7), (2.8, 37.0, 2.6),
            {'north': Project(face), 'east': dark, 'west': dark, 'south': dark, 'up': dark, 'down': Solid(rgb('#d9a0ac'))},
            name='face', ppu=28)
    bangs, side, back = paint_bangs_imp(), paint_hair_side_imp(), paint_hair_back_imp()
    round_corners(bangs, -3.15, 3.15, 37.55, 1.0)
    round_corners(back, -3.15, 3.15, 37.55, 1.0)
    round_corners(side, -3.1, 2.95, 37.55, 1.0)

    def ring(ctx):
        P = ctx['P']
        inside = (np.abs(P[..., 0]) < 2.78) & (P[..., 2] < 2.58)
        out = np.ones(ctx['shape'] + (4,))
        out[..., :3] = HAIR2[1]
        out[..., 3] = ~inside
        return out
    rig.box('head', (-3.15, 30.6, -3.1), (3.15, 37.55, 2.95),
            {'north': Project(bangs), 'east': Project(side, 'x'), 'west': Project(side, 'x'), 'south': Project(back),
             'up': Solid(HAIR2[2]), 'down': Shader(ring)}, name='hair_shell', ppu=18)
    # Twin tails: a puffed root, a long fall and a curled point, tied with cyan bows.
    for side, s in SIDES:
        segs = [((s * 3.0, 35.4, .9), (s * 4.7, 31.2, 1.35), 2.2, 1.8), ((s * 4.6, 31.8, 1.35), (s * 5.0, 22.6, 1.6), 2.3, 1.7),
                ((s * 4.95, 23.2, 1.6), (s * 4.2, 17.8, 1.3), 1.9, 1.4)]
        for k, (root, tip_, w, dpt) in enumerate(segs):
            hair_bundle(rig, 'head', root, tip_, w, dpt, 70 + k + (s > 0) * 5, ramp=HAIR2_RAMP, tip_tint=HAIR2_TIP if k == 2 else None,
                        tips=3 if k == 2 else 4, z_hint=(0, 0, -1))
        bow(rig, 'head', (s * 3.35, 35.1, .9), rot((0, s * 90, 0)), 2.6, 2.2, CYAN, knot=rgb('#fff1a8'), name=f'tail_bow_{side}')


def sculpt_imp():
    rig = Rig('arkhaia_malefeasance_phase2')
    skeleton(rig)
    head_imp(rig)
    hat(rig)
    torso(rig, paint_torso_front_imp(), paint_torso_side_imp(), paint_torso_back_imp(), NAVY[2], TIERS_IMP)
    round_collar(rig)
    for side, s in SIDES:
        sleeve_imp(rig, side, s)
    legs_imp(rig)
    ring_skirt(rig, petticoat_painter, 19.2, 10.9, (2.2, 1.55), (5.1, 4.35), .3, 10, ('scallop', 3, .3), 'petticoat')
    ring_skirt(rig, skirt_imp_painter, 19.95, 11.7, (2.3, 1.65), (5.5, 4.7), .35, 10, ('scallop', 2, .42), 'skirt')
    for side, s in SIDES:
        bone = 'veil_5' if s > 0 else 'veil_0'
        shaped(rig, bone, (s * 2.4, 27.6, 2.2), rot((0, -s * 28, s * 10)), bat_wing(s), wing_painter(s), ppu=12, name='bat_wing')
    tail(rig)
    bow(rig, 'pelvis', (0, 19.8, 1.95), rot((0, 180, 0)), 4.2, 3.4, CYAN, tails=True, name='back_bow')
    star_wand(rig, 'l')
    spellbook(rig)
    magic_circle(rig, 'focus', J['focus'], 1.4, (rgb('#46e0ff'), rgb('#ffd85a')), rot((90, 0, 0)), name='glow_focus')
    star_sprite(rig)
    return rig


# --------------------------------------------------------------------------- build
def build_phase(phase):
    rig = sculpt_hex() if phase == 1 else sculpt_imp()
    identifier = 'arkhaia_malefeasance' if phase == 1 else 'arkhaia_malefeasance_phase2'
    meta = dict(creation_tool='evergarden-anime-forge', author='Evergarden Anime Forge', model_identifier=identifier,
                revision=f'{REVISION}-phase{phase}', scale=3.0)
    model, atlas, stats = build(rig, identifier, ([-5, 0, -3], [5, 36, 3]), meta)
    model['animations'] = retarget(model)
    return model, atlas, stats


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--out', type=Path, default=OUT)
    parser.add_argument('--only', type=int, choices=(1, 2))
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    for phase in (1, 2):
        if args.only and phase != args.only:
            continue
        model, atlas, stats = build_phase(phase)
        target = args.out / (model['model_identifier'] + '.bbmodel')
        target.write_text(json.dumps(model, indent=1), encoding='utf8')
        print(f"{target.name}: {stats['cubes']} cubes, {stats['displays']} displays, {len(model['animations'])} clips, "
              f"{stats['coverage'] * 100:.1f}% atlas, png {stats['png'] / 1024:.0f} KiB, density x{stats['scale']:.2f}")


if __name__ == '__main__':
    main()
