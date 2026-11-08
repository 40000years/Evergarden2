"""Twenty hand-authored woodland companions, in three independently maturing forms.

Model-space units are sixteenths of a block. Forward is negative Z. Every sculpture
has a real head joint, individually articulated feet/arms and a segmented tail or
wings where appropriate. Shared helpers only construct anatomical primitives; the
silhouette, proportions, colours and grown accessories belong to each animal.
"""
from __future__ import annotations

import math
from pet_rare_sculpt import Sculpt, _palette


def _round(s, name, bone, center, size, mat, layers=6):
    """Rounded, overlapping voxel slabs with broad readable facets."""
    x, y, z = center
    w, h, d = size
    for i in range(layers):
        t = -1 + (i + .5) * 2 / layers
        f = math.sqrt(max(.1, 1 - t * t))
        hh = h / layers + .015
        s.box(name + '_core', bone, (x, y + t * h / 2, z), (w * f * .86, hh, d * f * .86), mat)
        s.box(name + '_wide', bone, (x, y + t * h / 2, z), (w * f, hh * .98, d * f * .58), mat)
        s.box(name + '_deep', bone, (x, y + t * h / 2, z), (w * f * .58, hh * .96, d * f), mat)


def _front(center, size, x, y, layers=6):
    cx, cy, cz = center
    w, h, d = size
    t = max(-1, min(.999999, (y - cy) * 2 / h))
    i = min(layers - 1, int((t + 1) * layers / 2))
    f = math.sqrt(max(.1, 1 - (-1 + (i + .5) * 2 / layers) ** 2))
    dx = abs(x - cx)
    return cz - d * f * (.5 if dx <= w * f * .29 else .43 if dx <= w * f * .43 else .29)


def _finish(s):
    """Place rotated geometry on the ground without clipping any decorative toe."""
    floor = 1e9
    for p in s.parts:
        start, size = p['from'], p['size']
        ax, ay, az = map(math.radians, p.get('rotation', (0, 0, 0)))
        cx, sx, cy, sy, cz, sz = math.cos(ax), math.sin(ax), math.cos(ay), math.sin(ay), math.cos(az), math.sin(az)
        origin = p.get('origin', (0, 0, 0))
        for dx in (0, size[0]):
            for dy in (0, size[1]):
                for dz in (0, size[2]):
                    x, y, z = start[0] + dx - origin[0], start[1] + dy - origin[1], start[2] + dz - origin[2]
                    yy, zz = y * cx - z * sx, y * sx + z * cx
                    xx = x * cy + zz * sy
                    floor = min(floor, xx * sz + yy * cz + origin[1])
    shift = -floor + .015
    for p in s.parts:
        p['from'][1] = round(p['from'][1] + shift, 4)
        if 'origin' in p:
            p['origin'][1] = round(p['origin'][1] + shift, 4)
    for name, (parent, pivot) in list(s.bones.items()):
        if name != 'root':
            s.bones[name] = (parent, [pivot[0], round(pivot[1] + shift, 4), pivot[2]])
    return s.finish()


def _new(stage, **palette):
    return Sculpt(stage, _palette(pupil='#32252F', eye='#5E454A', blush='#EFAEB5', cream='#FFF0D7',
                                 pink='#F3B6BC', nose='#63404A', leaf='#77A77C', vein='#567C55', **palette))


def _limb(s, name, hip, foot, width, mat='fur', paw='cream', claws=False):
    bone = s.bone(name, 'body', hip)
    knee = (hip[0] * 1.04, (hip[1] + foot[1]) * .5, (hip[2] + foot[2]) * .5 + .4 * s.scale)
    s.beam('upper_' + name, bone, hip, knee, width, mat)
    s.ball('soft_joint', bone, knee, (width * 1.06, width * 1.05, width * 1.06), mat)
    lower = s.bone(name + '_lower', bone, knee)
    s.beam('lower_' + name, lower, knee, foot, width * .78, mat)
    s.mass('plush_paw', lower, (foot[0], width * .29, foot[2] - width * .22), (width * 1.3, width * .6, width * 1.55), paw)
    for j in (-1, 0, 1):
        s.box('toe_' + name, lower, (foot[0] + j * width * .29, width * .24, foot[2] - width * .88),
              (width * .2, width * .22, width * (.42 if claws else .14)), 'claw' if claws else mat)
    return lower


def _quad(s, width, length, height, leg, mat='fur', paw='cream'):
    k = s.scale
    body_y = leg * k + height * k * .40
    center = (0, body_y, .6 * k)
    size = (width * k * (1.12, 1.04, 1)[s.age], height * k, length * k)
    s.bones['body'] = ('root', [0, body_y, 0])
    _round(s, 'rounded_torso', 'body', center, size, mat, 7)
    for side, sign in (('l', -1), ('r', 1)):
        for end, zz in (('f', -.31), ('b', .33)):
            x, z = sign * width * .32 * k, .6 * k + zz * length * k
            _limb(s, 'leg_' + end + side, (x, leg * k + .6 * k, z), (x * 1.04, .75 * k, z - .4 * k), width * .24 * k, mat, paw)
    return body_y


def _head(s, center, size, mat='fur', muzzle=True, muzzle_mat='cream', eye=.75, nose=True, eye_height=.04):
    h = s.head_scale
    size = tuple(v * h for v in size)
    s.bone('head', 'body', (center[0], center[1] - size[1] * .3, center[2] + size[2] * .25))
    _round(s, 'sculpted_head', 'head', center, size, mat, 7)
    ey = center[1] + size[1] * eye_height
    ex = size[0] * .235
    for side in (-1, 1):
        x = side * ex
        z = _front(center, size, x, ey, 7)
        e = eye * h * (1.14, 1.06, 1)[s.age]
        s.disc('velvet_eye', 'head', (x, ey, z - .10), e, e * 1.3, .25, 'pupil')
        s.box('warm_iris', 'head', (x + side * e * .13, ey - e * .12, z - .24), (e * .45, e * .63, .1), 'eye')
        s.box('large_catchlight', 'head', (x - e * .19, ey + e * .28, z - .31), (e * .3, e * .3, .08), 'shine')
        s.box('small_catchlight', 'head', (x + e * .22, ey - e * .31, z - .3), (e * .16, e * .16, .07), 'shine')
        bx, yy = x * 1.25, ey - e * .9
        bz = _front(center, size, bx, yy, 7)
        s.disc('warm_blush', 'head', (bx, yy, bz - .05), e * 1.13, e * .42, .12, 'blush')
    if muzzle:
        my = center[1] - size[1] * .21
        mz = _front(center, size, 0, my, 7) - size[2] * .075
        for side in (-1, 1):
            s.ball('velvet_muzzle', 'head', (side * size[0] * .12, my, mz), (size[0] * .34, size[1] * .31, size[2] * .24), muzzle_mat)
        tip = mz - size[2] * .12
        if nose:
            s.box('button_nose', 'head', (0, my + size[1] * .08, tip - .08), (size[0] * .15, size[1] * .10, .35), 'nose')
            s.box('nose_glint', 'head', (-size[0] * .025, my + size[1] * .10, tip - .27), (size[0] * .035, size[1] * .024, .06), 'shine')
        for side in (-1, 1):
            s.box('smiling_mouth', 'head', (side * size[0] * .045, my - size[1] * .10, tip - .03), (size[0] * .095, .12, .12), 'nose', (0, 0, side * 24))
    return size


def _ears(s, center, size, shape='round', mat='fur', inside='pink', length=2.5, width=2.3):
    x, y, z = center
    h = s.head_scale
    for side in (-1, 1):
        ex = side * size[0] * .34
        root = (x + ex, y + size[1] * .33, z + .3)
        bone = s.bone('ear_' + ('l' if side < 0 else 'r'), 'head', root)
        if shape == 'point':
            for j in range(3):
                s.box('triangular_ear', bone, (x + ex + side * j * .16 * h, root[1] + (j + .3) * length * h / 3, z + .2),
                      (width * h * (1 - j * .27), length * h / 3 + .2, 1.0 * h), mat, (0, 0, -side * 11))
                s.box('inner_ear_velvet', bone, (x + ex + side * j * .16 * h, root[1] + (j + .35) * length * h / 3, z - .32 * h),
                      (width * h * (1 - j * .27) * .55, length * h / 3, .14), inside, (0, 0, -side * 11))
        else:
            _round(s, 'round_outer_ear', bone, (root[0] + side * .25 * h, root[1] + length * h * .36, z), (width * h, length * h, .95 * h), mat, 4)
            s.disc('inner_ear', bone, (root[0] + side * .25 * h, root[1] + length * h * .38, z - .47 * h), width * h * .65, length * h * .68, .18, inside)


def _leaf(s, name, bone, root, tip, width, mat='leaf'):
    """A pointed blade with an inlaid midrib, firmly joined at its stem."""
    p = tuple((a + b) / 2 for a, b in zip(root, tip))
    s.beam(name + '_stem', bone, root, tip, .18, 'vein')
    for i, factor in enumerate((.66, 1, .6)):
        a, b = i / 3, (i + 1) / 3
        pa = tuple(root[j] + (tip[j] - root[j]) * a for j in range(3))
        pb = tuple(root[j] + (tip[j] - root[j]) * b for j in range(3))
        s.beam(name + '_blade', bone, pa, pb, width * factor, mat, .28)
    s.beam(name + '_vein', bone, (root[0], root[1], root[2] - .18), (tip[0], tip[1], tip[2] - .18), .10, 'vein')


def _berry(s, name, bone, center, radius, mat='accent', seed=False):
    s.ball(name, bone, center, (radius * 2, radius * 1.9, radius * 1.8), mat)
    x, y, z = center
    if seed:
        for i, (dx, dy) in enumerate(((-.42, .18), (.35, .35), (0, -.35))):
            s.box('berry_seed', bone, (x + dx * radius, y + dy * radius, z - radius * .86), (.16, .25, .1), 'cream', (0, 0, i * 23))
    else:
        s.box('berry_shine', bone, (x - radius * .26, y + radius * .25, z - radius * .86), (radius * .32, radius * .22, .09), 'shine')
    s.box('berry_calyx', bone, (x, y + radius * .92, z), (radius * .65, .23, radius * .65), 'leaf', (0, 45, 0))


def _tail(s, points, widths, mats, depth=1, name='tail', puffy=False):
    """Articulated, continuous tail. Widths refer to a complete segment diameter."""
    for i, (a, b) in enumerate(zip(points, points[1:])):
        bone = s.bone(name if i == 0 else name + '_tip' if i == len(points) - 2 else name + '_' + str(i),
                      'body' if i == 0 else name if i == 1 else name + '_' + str(i - 1), a)
        w = widths[i] if isinstance(widths, (list, tuple)) else widths
        mat = mats[i % len(mats)] if isinstance(mats, (list, tuple)) else mats
        s.beam('tail_segment', bone, a, b, w, mat, w * depth)
        s.ball('tail_joint', bone, a, (w * .98, w * .98, w * depth * .98), mat)
        if puffy:
            mid = tuple((a[j] + b[j]) / 2 for j in range(3))
            s.ball('fluffy_tail_segment', bone, mid, (w * 1.12, w * 1.12, max(w * depth, abs(b[2] - a[2]) * 1.25)), mat)
    return name + '_tip'


def _scarf(s, y, z, width, mat='accent', style='bow'):
    k = s.scale
    s.mass('soft_collar', 'body', (0, y, z), (width, 1.1 * k, 1.15 * k), mat)
    if style == 'bow':
        for side in (-1, 1):
            s.mass('bow_loop', 'body', (side * 1.0 * k, y, z - .7 * k), (1.5 * k, 1.3 * k, .6 * k), mat)
            s.box('ribbon_tail', 'body', (side * .7 * k, y - 1.2 * k, z - .75 * k), (.7 * k, 1.9 * k, .35 * k), mat, (0, 0, side * 18))
        s.ball('bow_knot', 'body', (0, y, z - .9 * k), (.9 * k, 1.0 * k, .8 * k), 'cream')
    else:
        for i in range(2 + s.age):
            s.box('knitted_scarf', 'body', (width * .25, y - (i + .5) * .6 * k, z - .65 * k - i * .1), (1.25 * k, .7 * k, .35), mat)
            s.box('scarf_stitch', 'body', (width * .25 - .3 * k, y - (i + .5) * .6 * k, z - .86 * k - i * .1), (.13, .25, .08), 'cream')


def _hamster(stage):
    s = _new(stage, fur='#EAD0A9', shadow='#C9AA85', accent='#EB687B', seed='#FFE1A6')
    k, h = s.scale, s.head_scale
    by = (3.2, 4.2, 5.4)[s.age]
    s.bones['body'] = ('root', [0, by, 0])
    _round(s, 'pear_shaped_hamster', 'body', (0, by, .5 * k), (8.6 * k, 9 * k, 7.5 * k), 'fur', 7)
    c = (0, by + 3 * h, -1.9 * k)
    size = _head(s, c, (8.3, 6.8, 6), eye=.92)
    _ears(s, c, size, length=2.3, width=2.6)
    for side, n in ((-1, 'l'), (1, 'r')):
        _limb(s, 'leg_' + n, (side * 2.1 * k, 2.3 * k, .5 * k), (side * 2.5 * k, .7 * k, -.9 * k), 1.65 * k)
        bone = s.bone('arm_' + n, 'body', (side * 3 * k, by + .6 * k, -1 * k))
        s.ball('little_holding_paw', bone, (side * 2.5 * k, by - .2 * k, -3.2 * k), (2.3 * k, 2 * k, 2.4 * k), 'cream')
        s.ball('full_cheek_pouch', 'head', (side * size[0] * .38, c[1] - 1.3 * h, c[2] - .7 * h), (3.1 * h, 3.5 * h, 4 * h), 'fur')
        for j in range(2 + s.age):
            s.box('cheek_freckle', 'head', (side * (2.7 + .35 * j) * h, c[1] - 1.2 * h + (j % 2) * .4, c[2] - 2.4 * h), (.18, .18, .1), 'shadow')
    # The fruit is worn as a little rounded cape; it is not an extra floating head.
    _round(s, 'strawberry_backpack', 'body', (0, by + .5 * k, 3.1 * k), ((4.7, 5.7, 6.7)[s.age] * k, 6.3 * k, 3.6 * k), 'accent', 6)
    for j in range(4 + 3 * s.age):
        a = j * 2.4
        x = math.sin(a) * 2.1 * k
        yy = by + (j % 3 - 1) * 1.2 * k
        s.box('strawberry_cape_seed', 'body', (x, yy, 4.75 * k), (.28 * k, .5 * k, .16), 'seed', (0, 0, 18))
    for j in range(2 + s.age):
        ang = (j - (1 + s.age) / 2) * .7
        _leaf(s, 'fruit_collar', 'body', (0, by + 3.3 * k, 2.6 * k), (math.sin(ang) * 3.0 * k, by + 2.5 * k, 2.6 * k + math.cos(ang) * 1.8 * k), 1.1 * k)
    _berry(s, 'held_strawberry', 'body', (0, by - .1 * k, -3.1 * k), 1.7 * k, seed=True)
    s.box('tiny_hamster_tooth', 'head', (0, c[1] - 2.1 * h, c[2] - 3.4 * h), (.8 * h, .8 * h, .3), 'shine')
    if s.age == 2:
        for i in range(4):
            s.box('woven_pouch', 'body', (3.6 * k, by - .8 * k + i * .35, .4 * k), (1.1, .25, 2.2), 'shadow')
    return _finish(s)


def _capybara(stage):
    s = _new(stage, fur='#A98564', dark='#745642', accent='#B66F49', bean='#623D35')
    k, h = s.scale, s.head_scale
    by = _quad(s, 8.6, 13.3, 7.5, 3.3, paw='dark')
    c = (0, by + 2.1 * h, -5.1 * k)
    size = _head(s, c, (7.2, 6.2, 8.3), muzzle=False, eye=.65, eye_height=.24)
    _ears(s, c, size, length=1.6, width=1.7, inside='dark')
    s.mass('broad_square_snout', 'head', (0, c[1] - 1.0 * h, c[2] - 3.9 * h), (6.8 * h, 3.4 * h, 3.0 * h), 'fur')
    for side in (-1, 1):
        s.box('wide_nostril', 'head', (side * 2.1 * h, c[1] - .8 * h, c[2] - 5.46 * h), (.55 * h, .3 * h, .12), 'dark')
        s.box('relaxed_eyelid', 'head', (side * size[0] * .235, c[1] + .75 * h, _front(c, size, side * size[0] * .235, c[1] + .75 * h, 7) - .1), (1.5 * h, .23 * h, .2), 'dark')
        s.box('snout_smile', 'head', (side * .5 * h, c[1] - 2.05 * h, c[2] - 5.5 * h), (1.1 * h, .13, .1), 'dark')
        for i in range(3 + s.age):
            s.box('cocoa_coat_strand', 'body', (side * (3.9 * k), by + .3 * k, (-2 + i * 1.4) * k), (.22, 1.1 * k, .7 * k), 'accent')
    # A cocoa pod saddle, with longitudinal ribs growing more pronounced.
    _round(s, 'cocoa_pod_satchel', 'body', (0, by + 3.7 * k, 2.4 * k), (4.1 * k, 3.3 * k, (5.4 + s.age) * k), 'accent', 5)
    for side in (-1, 1):
        for j in range(2 + s.age):
            s.beam('cocoa_pod_rib', 'body', (side * (1 + j * .25) * k, by + 4.1 * k, .0 * k), (side * (1 + j * .25) * k, by + 4.25 * k, 4.7 * k), .2 * k, 'bean')
    _leaf(s, 'cocoa_sprout', 'head', (0, c[1] + size[1] * .46, c[2] + .3), (1.8 * h, c[1] + size[1] * .46 + 1.4 * k, c[2] + .8), 1.0 * k)
    if s.age:
        for j in range(s.age + 1):
            s.ball('cocoa_bean', 'body', (2.0 * k, by + 4.5 * k, (1 + j) * k), (.9 * k, .65 * k, 1.2 * k), 'bean')
    return _finish(s)


def _otter(stage):
    s = _new(stage, fur='#AA774D', dark='#754B35', accent='#E8AA3A', honey='#F5C95D')
    k, h = s.scale, s.head_scale
    by = 5.2 * k
    s.bones['body'] = ('root', [0, by, 0])
    _round(s, 'upright_otter_torso', 'body', (0, by, 1.0 * k), (6.9 * k, 10 * k, 6.4 * k), 'fur', 7)
    _round(s, 'cream_belly', 'body', (0, by, -1.8 * k), (4.8 * k, 7.7 * k, 1.3 * k), 'cream', 6)
    c = (0, by + 5.4 * h, -.5 * k)
    size = _head(s, c, (7.1, 5.7, 5.6), eye=.85)
    _ears(s, c, size, length=1.6, width=1.9, inside='dark')
    for side, n in ((-1, 'l'), (1, 'r')):
        _limb(s, 'leg_' + n, (side * 2 * k, 2.0 * k, 1.2 * k), (side * 2.5 * k, .6 * k, -.6 * k), 1.7 * k, paw='dark')
        b = s.bone('arm_' + n, 'body', (side * 2.7 * k, by + 2.5 * k, .2 * k))
        s.beam('otter_forearm', b, (side * 2.7 * k, by + 2.5 * k, .2 * k), (side * 2.0 * k, by + .1 * k, -3 * k), 1.8 * k, 'fur')
        s.ball('little_held_paw', b, (side * 1.7 * k, by + .3 * k, -3.5 * k), (1.9 * k, 2 * k, 1.9 * k), 'cream')
        for i in range(3):
            z = c[2] - size[2] * .47
            s.beam('whisker', 'head', (side * 1.7 * h, c[1] - .7 * h, z), (side * 3.9 * h, c[1] + (i - 1) * .55 * h, z + .1), .10, 'cream')
    _tail(s, [(0, 3 * k, 3.5 * k), (0, 1.8 * k, 6.1 * k), (1.1 * k, 1.2 * k, 8.2 * k), (2 * k, 1.0 * k, (9.0 + s.age) * k)], [2.6 * k, 2.0 * k, 1.0 * k], 'fur', .75)
    _scarf(s, by + 3.8 * k, -2 * k, 5.2 * k, style='knit')
    rows = 1 + s.age
    for row in range(rows):
        for j in range(3 if row % 2 == 0 else 2):
            x = (j - (1 if row % 2 == 0 else .5)) * 1.05 * k
            y = by + (row - rows / 2 + .5) * .93 * k
            s.box('honeycomb_cell', 'body', (x, y, -3.7 * k), (.99 * k, .97 * k, .8 * k), 'accent', (0, 0, 30))
            s.box('honeycomb_inset', 'body', (x, y, -4.14 * k), (.55 * k, .54 * k, .1), 'honey', (0, 0, 30))
    return _finish(s)


def _shiba(stage):
    s = _new(stage, fur='#E7AD89', dark='#BD7D64', accent='#F3A6A5', peach='#E88786')
    k, h = s.scale, s.head_scale
    by = _quad(s, 6.7, 9.8, 6.8, 4.4)
    c = (0, by + 3.1 * h, -4.1 * k)
    size = _head(s, c, (7.4, 6.3, 5.8), eye=.80)
    _ears(s, c, size, shape='point', length=(1.5, 2.8, 3.3)[s.age], width=2.4)
    for side in (-1, 1):
        s.ball('cream_cheek', 'head', (side * 2.8 * h, c[1] - 1.2 * h, c[2] - 1.2 * h), (2.8 * h, 3 * h, 2.8 * h), 'cream')
        s.box('shiba_eyebrow', 'head', (side * 1.65 * h, c[1] + 1.5 * h, _front(c, size, side * 1.65 * h, c[1] + 1.5 * h, 7) - .06), (1.05 * h, .45 * h, .18), 'cream')
    _round(s, 'shiba_chest', 'body', (0, by + .1 * k, -3.3 * k), (5.0 * k, 5.4 * k, 1.8 * k), 'cream', 5)
    radius = (1.6, 2.3, 3.1)[s.age] * k
    points = [(0, by + .7 * k, 4 * k)]
    for j in range(7):
        a = -math.pi / 2 + j * math.pi * 1.7 / 6
        points.append((.9 * k + math.cos(a) * radius, by + 2.5 * k + math.sin(a) * radius, 5.7 * k))
    _tail(s, points, [2.2 * k] * 4 + [1.9 * k, 1.6 * k, 1.1 * k], ['fur'] * 5 + ['cream'] * 2, puffy=True)
    _scarf(s, by + 2.0 * k, -3.8 * k, 5.4 * k)
    _leaf(s, 'peach_leaf', 'body', (0, by + 2.2 * k, -4.3 * k), (1.7 * k, by + 3 * k, -4.35 * k), .9 * k)
    if s.age == 2:
        _berry(s, 'peach_satchel', 'body', (3.45 * k, by - .4 * k, -.3 * k), 1.5 * k, 'peach')
        s.beam('satchel_strap', 'body', (2.4 * k, by + 2.5 * k, -2.0 * k), (3.45 * k, by + .1 * k, -.3 * k), .35, 'dark')
    return _finish(s)


def _bat(stage):
    s = _new(stage, fur='#6476B5', dark='#405084', accent='#7770B4', membrane='#A59AD8', berry='#59629C')
    k, h = s.scale, s.head_scale
    by = 6.4 * k
    s.bones['body'] = ('root', [0, by, 0])
    _round(s, 'bat_pear_torso', 'body', (0, by, 0), (5.2 * k, 6.8 * k, 4.7 * k), 'fur', 6)
    _round(s, 'bat_belly', 'body', (0, by - .1 * k, -2.0 * k), (3.6 * k, 4.7 * k, 1.2 * k), 'cream', 5)
    c = (0, by + 4.0 * h, -.2 * k)
    size = _head(s, c, (6.5, 5.8, 4.8), muzzle=True, eye=.98)
    _ears(s, c, size, shape='point', length=(3.2, 4, 4.7)[s.age], width=2.5, inside='membrane')
    for side, n in ((-1, 'l'), (1, 'r')):
        root = (side * 2.0 * k, by + 1.6 * k, 0)
        wing = s.bone('wing_' + n, 'body', root)
        elbow = (side * (4.4 + s.age) * k, by + 3.1 * k, .4 * k)
        tip = (side * (7.2 + s.age * 1.8) * k, by + 3.6 * k, .9 * k)
        s.beam('wing_upper_arm', wing, root, elbow, .6 * k, 'dark')
        outer = s.bone('wingtip_' + n, wing, elbow)
        s.beam('wing_forearm', outer, elbow, tip, .48 * k, 'dark')
        # Continuous skin fan, from the shoulder to a tapered wing tip. The
        # scalloped lower contour follows three bowed webs between the fingers.
        slices = 10 + 3 * s.age
        start_x, end_x = abs(root[0]), abs(tip[0])
        for q in range(slices):
            t = (q + .5) / slices
            xx = start_x + (end_x - start_x) * t
            top_y = root[1] + (tip[1] - root[1]) * min(1, t * 1.8)
            lower_y = by - 1.7 * k + t * 1.2 * k + max(0, (t - .72) / .28) * 4.2 * k
            lower_y += abs(math.sin(t * 3 * math.pi)) * .55 * k
            lower_y = min(top_y - .18 * k, lower_y)
            s.box('scalloped_membrane', outer, (side * xx, (top_y + lower_y) / 2, .82 * k),
                  ((end_x - start_x) / slices + .025, top_y - lower_y, .24), 'membrane')
        # Three separate long fingers support the complete fan.
        for j in range(3):
            end = (side * (4.5 + s.age + j * 1.7) * k, by - (1.9 - j * .4) * k, (1.0 + j * .2) * k)
            s.beam('wing_finger', outer, elbow, end, .26 * k, 'dark')
        s.box('wing_thumb', wing, elbow, (.45 * k, 1.1 * k, .5 * k), 'cream', (0, 0, side * 28))
        _limb(s, 'leg_' + n, (side * 1.4 * k, by - 2 * k, .2 * k), (side * 1.5 * k, .5 * k, -.2 * k), 1.0 * k, paw='dark')
    for j in range(1 + s.age):
        x = (j - s.age / 2) * 1.35 * k
        _berry(s, 'blueberry_necklace', 'body', (x, by + 1.2 * k - abs(x) * .18, -2.3 * k), .72 * k, 'berry')
    _leaf(s, 'blueberry_leaf', 'head', (0, c[1] + size[1] * .45, c[2]), (1.6 * k, c[1] + size[1] * .45 + 1.2 * k, c[2] - .1), .95 * k)
    return _finish(s)


def _ferret(stage):
    s = _new(stage, fur='#B2C9B7', dark='#718F81', accent='#81C2AC', stripe='#E7EFDE')
    k, h = s.scale, s.head_scale
    by = _quad(s, 5.2, (11.0, 14.0, 16)[s.age], 5.6, 2.8, paw='dark')
    # An arched shoulder and small inquisitive head distinguish the long ferret.
    _round(s, 'arched_neck', 'body', (0, by + 1.1 * k, -4.6 * k), (4.8 * k, 6.1 * k, 4.9 * k), 'fur', 5)
    c = (0, by + 2.6 * h, -6.4 * k)
    size = _head(s, c, (5.8, 4.6, 5.6), muzzle=True, eye=.72)
    _ears(s, c, size, length=1.5, width=1.7, inside='cream')
    for side in (-1, 1):
        s.disc('ferret_mask', 'head', (side * 1.45 * h, c[1] + .2 * h, _front(c, size, side * 1.45 * h, c[1] + .2 * h, 7) + .07), 2.3 * h, 2.1 * h, .19, 'cream')
        # Eyes on the cream patch, maintaining the connected surface.
        x = side * 1.45 * h
        z = _front(c, size, x, c[1] + .2 * h, 7)
        s.disc('mask_eye', 'head', (x, c[1] + .2 * h, z - .18), .8 * h, 1.05 * h, .2, 'pupil')
        s.box('mask_eye_shine', 'head', (x - .16 * h, c[1] + .46 * h, z - .31), (.26 * h, .26 * h, .07), 'shine')
    n = 4 + s.age
    points = [(0, by, (5.3 + s.age * .5) * k)]
    for j in range(1, n + 1):
        t = j / n
        points.append((math.sin(t * 1.5) * 2.6 * k, by + math.sin(t * math.pi) * .9 * k - 1.7 * k * t, (5.3 + s.age * .5 + t * (5 + s.age * 2)) * k))
    _tail(s, points, [(1.9 - 1.2 * j / n) * k for j in range(n)], ['fur', 'stripe', 'accent'], puffy=True)
    _scarf(s, by + 2 * k, -5.4 * k, 4.2 * k, style='knit')
    for side in (-1, 1):
        _leaf(s, 'mint_scarf_leaf', 'body', (0, by + 2.3 * k, -6.0 * k), (side * (1.5 + s.age * .3) * k, by + (2.8 + s.age * .3) * k, -6.1 * k), 1.1 * k)
    return _finish(s)


def _raccoon(stage):
    s = _new(stage, fur='#A99C8B', dark='#4C4950', accent='#C67C53', stripe='#DED5C4', maple='#C98454')
    k, h = s.scale, s.head_scale
    by = _quad(s, 7.8, 10.0, 7.2, 3.8, paw='dark')
    c = (0, by + 3.2 * h, -4.2 * k)
    size = _head(s, c, (7.4, 6.2, 5.6), eye=.8)
    _ears(s, c, size, length=2.1, width=2.1, inside='dark')
    for side in (-1, 1):
        x, y = side * size[0] * .235, c[1] + size[1] * .04
        z = _front(c, size, x, y, 7)
        s.mass('raccoon_mask', 'head', (x, y, z + .05), (2.75 * h, 2.0 * h, .28), 'dark')
        s.disc('mask_glossy_eye', 'head', (x, y, z - .16), .78 * h, 1.04 * h, .2, 'pupil')
        s.box('mask_warm_iris', 'head', (x + side * .1 * h, y - .15 * h, z - .28), (.34 * h, .5 * h, .08), 'eye')
        s.box('mask_shine', 'head', (x - .18 * h, y + .25 * h, z - .34), (.25 * h, .25 * h, .07), 'shine')
    points = [(0, by + .5 * k, 4.2 * k)]
    for j in range(1, 6 + s.age):
        t = j / (5 + s.age)
        points.append((t * 4.0 * k, by - t * 2.5 * k, (4.2 + t * 6.8) * k))
    _tail(s, points, [(2.8 - .8 * j / (5 + s.age)) * k for j in range(5 + s.age)], ['dark', 'stripe'], puffy=True)
    # Distinct maple leaves form a fitted shoulder cape, sprouting with age.
    for j in range(3 + s.age * 2):
        a = -math.pi / 2 + j * math.pi / (2 + s.age * 2)
        root = (0, by + 3.5 * k, .1 * k)
        tip = (math.sin(a) * 4.0 * k, by + 1.7 * k, .2 * k + math.cos(a) * 4.5 * k)
        _leaf(s, 'maple_cape_lobe', 'body', root, tip, 1.9 * k, 'maple')
    if s.age:
        s.mass('trail_satchel', 'body', (4 * k, by - .1 * k, 1.4 * k), (1.5 * k, 3.0 * k, 3.0 * k), 'accent')
        s.box('satchel_flap', 'body', (4.8 * k, by + .5 * k, 1.4 * k), (.22, 1.5 * k, 2.7 * k), 'dark')
        s.box('satchel_clasp', 'body', (4.96 * k, by + .2 * k, 1.3 * k), (.2, .55 * k, .55 * k), 'gold')
    return _finish(s)


def _cat(stage):
    s = _new(stage, fur='#B6ACCE', dark='#88779E', accent='#8C79B1', petal='#BC9AD4')
    k, h = s.scale, s.head_scale
    by = _quad(s, 5.9, 9.3, 6.7, 4.5)
    c = (0, by + 3.2 * h, -4.0 * k)
    size = _head(s, c, (6.5, 5.5, 4.8), eye=.87)
    _ears(s, c, size, shape='point', length=(2.7, 3.2, 3.7)[s.age], width=2.4)
    for side in (-1, 1):
        for j in range(2 + s.age):
            s.box('cat_cheek_tuft', 'head', (side * (2.8 + j * .11) * h, c[1] - (1.0 + j * .35) * h, c[2] - .5 * h), (1.4 * h, .7 * h, 2.5 * h), 'cream', (0, 0, side * (10 + j * 12)))
        for i in range(3):
            s.beam('cat_whisker', 'head', (side * 1.7 * h, c[1] - 1 * h, c[2] - 2.6 * h), (side * 4.2 * h, c[1] - (.3 + i * .6) * h, c[2] - 2.3 * h), .075, 'cream')
    _round(s, 'silky_chest', 'body', (0, by + .2 * k, -3.2 * k), (3.9 * k, 5.1 * k, 1.8 * k), 'cream', 5)
    points = [(0, by + .6 * k, 3.8 * k), (.4 * k, by + 2.7 * k, 5.1 * k), (.9 * k, by + 5 * k, 5.7 * k)]
    if s.age:
        points += [(1.8 * k, by + 6.7 * k, 5.2 * k), (2.8 * k, by + 6.2 * k, 4.5 * k)]
    _tail(s, points, [1.55 * k] * (len(points) - 2) + [1.1 * k], ['fur', 'fur', 'dark', 'dark'], puffy=True)
    _scarf(s, by + 2.3 * k, -3.7 * k, 4.4 * k, style='knit')
    for j in range(1 + s.age):
        x = (j - s.age / 2) * .7 * k
        a = (x, by + 2.6 * k, -4.2 * k)
        b = (x + .2 * k, by + (3.9 + .35 * (j % 2)) * k, -4.3 * k)
        s.beam('lavender_stalk', 'body', a, b, .12, 'vein')
        for q in range(2 + s.age):
            for sign in (-1, 1):
                s.ball('lavender_floret', 'body', (b[0] + sign * .25 * k, b[1] - q * .35 * k, b[2]), (.5 * k, .45 * k, .5 * k), 'petal')
    return _finish(s)


def _squirrel(stage):
    s = _new(stage, fur='#BA875A', dark='#815C43', accent='#C5A170', shell='#AA7A4C')
    k, h = s.scale, s.head_scale
    by = 5.0 * k
    s.bones['body'] = ('root', [0, by, 0])
    _round(s, 'squirrel_upright', 'body', (0, by, 1.2 * k), (6.1 * k, 8.7 * k, 6.2 * k), 'fur', 7)
    _round(s, 'cream_belly', 'body', (0, by, -1.6 * k), (3.8 * k, 6.7 * k, 1.5 * k), 'cream', 5)
    c = (0, by + 4.1 * h, -.8 * k)
    size = _head(s, c, (6.6, 5.8, 5.1), eye=.88)
    _ears(s, c, size, shape='point', length=(2, 2.5, 3.2)[s.age], width=1.8)
    for side, n in ((-1, 'l'), (1, 'r')):
        _limb(s, 'leg_' + n, (side * 2.2 * k, 2 * k, 1.4 * k), (side * 2.4 * k, .5 * k, -.8 * k), 1.7 * k, paw='dark')
        b = s.bone('arm_' + n, 'body', (side * 2.7 * k, by + 1.2 * k, -.1 * k))
        s.beam('holding_forearm', b, (side * 2.7 * k, by + 1.2 * k, -.1 * k), (side * 1.7 * k, by - .4 * k, -3.2 * k), 1.3 * k, 'fur')
        s.ball('holding_fingers', b, (side * 1.6 * k, by - .2 * k, -3.5 * k), (1.4 * k, 1.5 * k, 1.3 * k), 'cream')
    # A full C-shaped tail has its own sequence of joints; bulk grows with age.
    pts = [(0, 3.2 * k, 3.3 * k), (0, 4.3 * k, 6 * k), (0, 7.5 * k, 7.7 * k), (0, 10.8 * k, 7.0 * k), (0, (12.0 + s.age) * k, 5.2 * k)]
    _tail(s, pts, [(3.1 + .5 * s.age) * k, (4.2 + .4 * s.age) * k, (4.3 + .5 * s.age) * k, 3.2 * k], ['fur', 'fur', 'fur', 'cream'], puffy=True)
    for side in (-1, 1):
        for j in range(3 + s.age):
            s.box('tail_fur_tip', 'tail_2', (side * 2.2 * k, (7.5 + j * .7) * k, (7.4 - j * .15) * k), (.85 * k, 1.4 * k, 1.0 * k), 'cream', (0, 0, side * 15))
    ac = (0, by - .4 * k, -3.3 * k)
    _round(s, 'acorn_kernel', 'body', ac, (3.1 * k, 3.4 * k, 2.8 * k), 'accent', 5)
    s.mass('acorn_cap', 'body', (0, ac[1] + 1.2 * k, ac[2]), (3.7 * k, 1.1 * k, 3.3 * k), 'shell')
    s.box('acorn_stem', 'body', (0, ac[1] + 2.0 * k, ac[2]), (.5 * k, 1 * k, .5 * k), 'dark', (0, 0, -18))
    for j in range(3 + s.age * 2):
        a = math.pi * 2 * j / (3 + s.age * 2)
        s.box('acorn_cap_scale', 'body', (math.sin(a) * 1.4 * k, ac[1] + 1.4 * k, ac[2] + math.cos(a) * 1.2 * k), (.55 * k, .25, .55 * k), 'dark', (0, j * 30, 0))
    return _finish(s)


def _corgi(stage):
    s = _new(stage, fur='#E2B16C', dark='#BB874D', accent='#F2D394', icing='#FFF7E8')
    k, h = s.scale, s.head_scale
    by = _quad(s, 7.2, 13.4, 7.0, 2.7)
    c = (0, by + 3 * h, -5.4 * k)
    size = _head(s, c, (7.8, 6.7, 5.9), eye=.86)
    _ears(s, c, size, shape='point', length=(3.6, 4.1, 4.5)[s.age], width=3.3)
    s.mass('white_forehead_blaze', 'head', (0, c[1] + 1.1 * h, _front(c, size, 0, c[1] + 1.1 * h, 7) - .05), (1.4 * h, 3.1 * h, .17), 'cream')
    for side in (-1, 1):
        s.ball('corgi_cheek', 'head', (side * 2.8 * h, c[1] - 1.2 * h, c[2] - 1.0 * h), (2.8 * h, 3.3 * h, 3.4 * h), 'cream')
        for j in range(3 + s.age):
            s.box('chest_fluff', 'body', (side * (.3 + j * .45) * k, by + .8 * k - j * .55 * k, -5.1 * k), (1.3 * k, 1.8 * k, 1.5 * k), 'cream', (0, 0, side * (10 + j * 5)))
    _tail(s, [(0, by + .7 * k, 5.6 * k), (0, by + 1.5 * k, 7 * k)], 1.5 * k, 'cream', puffy=True)
    # Heart-shaped plush rump and a generous pastry-coloured bow.
    for side in (-1, 1):
        s.ball('heart_rump_lobe', 'body', (side * 1.3 * k, by + .4 * k, 6.3 * k), (2.8 * k, 3.1 * k, 1.8 * k), 'cream')
    _scarf(s, by + 1.5 * k, -5.8 * k, 6.0 * k)
    if s.age:
        for side in (-1, 1):
            s.mass('buttercream_bow_fold', 'body', (side * 1.25 * k, by + 1.6 * k, -6.5 * k), (1.4 * k, .55 * k, .3), 'icing')
    if s.age == 2:
        s.ball('bow_butter_rosette', 'body', (0, by + 1.6 * k, -6.85 * k), (1.2, 1.3, .6), 'icing')
        for i in range(4):
            a = i * math.pi / 2
            s.box('rosette_fold', 'body', (math.cos(a) * .44, by + 1.6 + math.sin(a) * .44, -7.18), (.5, .3, .18), 'accent', (0, 0, i * 90))
    return _finish(s)


def _pangolin(stage):
    s = _new(stage, fur='#C8B79B', dark='#827A70', accent='#A3A295', scale_light='#C1C0AD', claw='#EEE5CE')
    k, h = s.scale, s.head_scale
    by = _quad(s, 7.2, 11.5, 7.4, 2.4, paw='dark')
    c = (0, by + .7 * h, -5.3 * k)
    size = _head(s, c, (4.9, 4.3, 6.9), eye=.70, muzzle=False, eye_height=.26)
    s.mass('long_tapered_snout', 'head', (0, c[1] - .8 * h, c[2] - 3.9 * h), (2.9 * h, 2.3 * h, 3.9 * h), 'fur')
    s.mass('narrow_snout_tip', 'head', (0, c[1] - 1 * h, c[2] - 5.6 * h), (1.5 * h, 1.6 * h, 1.1 * h), 'dark')
    for side in (-1, 1):
        for z in (-3.3, 4.1):
            for j in range(3):
                s.box('curved_digging_claw', 'leg_' + ('f' if z < 0 else 'b') + ('l' if side < 0 else 'r') + '_lower',
                      (side * (2.25 + (j - 1) * .32) * k, .42 * k, (z - 1.1) * k), (.25 * k, .32 * k, 1.4 * k), 'claw', (8, 0, 0))
    rows = (4, 5, 7)[s.age]
    for row in range(rows):
        z = (-4 + row * 8.5 / max(1, rows - 1)) * k
        for col in range(-2, 3):
            x = (col + (.18 if row % 2 else -.18)) * 1.25 * k
            yy = by + math.sqrt(max(.1, 1 - (x / (3.9 * k)) ** 2)) * 3.25 * k
            # Deliberate overlapping shingle rows make a curved protective shell.
            s.mass('rounded_pebble_shingle', 'body', (x, yy, z), (1.7 * k, .95 * k, 2.0 * k), 'scale_light' if (row + col) % 3 == 0 else 'accent')
            if s.age == 2:
                s.box('shingle_ridge', 'body', (x, yy + .48 * k, z - .1 * k), (.23, .13, .9), 'dark')
    n = (4, 6, 8)[s.age]
    pts = [(0, by + .4 * k, 5.0 * k)]
    for j in range(1, n + 1):
        t = j / n
        pts.append((math.sin(t * 1.4) * 2.5 * k, by - t * 2.2 * k, (5 + t * (6 + s.age)) * k))
    _tail(s, pts, [(2.9 - 2.1 * j / n) * k for j in range(n)], 'accent', .65)
    for j, p in enumerate(pts[1:]):
        bone = 'tail_tip' if j == n - 1 else 'tail' if j == 0 else 'tail_' + str(j)
        w = (2.8 - 2 * j / n) * k
        s.mass('tail_armour_plate', bone, (p[0], p[1] + w * .4, p[2]), (w * 1.1, .6 * k, 1.5 * k), 'scale_light' if j % 2 else 'accent')
    return _finish(s)


def _frog_anatomy(s, center_y, width, depth, height, rough=False):
    k, h = s.scale, s.head_scale
    c = (0, center_y, 0)
    sz = (width * k * (1.1, 1.03, 1)[s.age], height * k, depth * k)
    s.bones['body'] = ('root', [0, center_y, 0])
    _round(s, 'round_amphibian_torso', 'body', c, sz, 'fur', 7)
    hc = (0, center_y + height * .19 * k, -depth * .22 * k)
    s.bone('head', 'body', (0, center_y, -depth * .1 * k))
    _round(s, 'amphibian_face', 'head', hc, (width * .95 * h, height * .76 * h, depth * .62 * h), 'fur', 6)
    _round(s, 'pale_chin', 'head', (0, hc[1] - height * .19 * h, hc[2] - depth * .27 * h), (width * .69 * h, height * .39 * h, depth * .19 * h), 'cream', 5)
    for side, name in ((-1, 'l'), (1, 'r')):
        ex, ey, ez = side * width * .29 * h, hc[1] + height * .32 * h, hc[2] - depth * .10 * h
        _round(s, 'bulging_eye_socket', 'head', (ex, ey, ez), (3.0 * h, 3.0 * h, 2.8 * h), 'fur', 4)
        s.disc('frog_eye', 'head', (ex, ey, ez - 1.38 * h), 1.6 * h, 1.8 * h, .28, 'pupil')
        s.box('frog_iris', 'head', (ex + side * .14 * h, ey - .19 * h, ez - 1.55 * h), (.74 * h, .8 * h, .10), 'eye')
        s.box('frog_shine', 'head', (ex - .32 * h, ey + .4 * h, ez - 1.62 * h), (.5 * h, .5 * h, .08), 'shine')
        s.disc('frog_blush', 'head', (ex * .97, ey - 1.7 * h, hc[2] - depth * .35 * h), 1.1 * h, .36 * h, .18, 'blush')
        front = s.bone('leg_f' + name, 'body', (side * width * .35 * k, center_y, -depth * .25 * k))
        paw = (side * width * .42 * k, .6 * k, -depth * .45 * k)
        s.beam('frog_forearm', front, (side * width * .35 * k, center_y, -depth * .25 * k), paw, 1.1 * k, 'fur')
        rear = s.bone('leg_b' + name, 'body', (side * width * .36 * k, center_y - .3 * k, depth * .19 * k))
        s.ball('folded_hind_thigh', rear, (side * width * .46 * k, center_y * .6, depth * .20 * k), (3.5 * k, 3.5 * k, 4.4 * k), 'fur')
        rearfoot = (side * width * .51 * k, .5 * k, depth * .14 * k)
        s.beam('folded_shin', rear, (side * width * .60 * k, center_y * .6, depth * .32 * k), rearfoot, .95 * k, 'fur')
        for bone, p, count in ((front, paw, 3), (rear, rearfoot, 4)):
            for j in range(count):
                end = (p[0] + side * (.5 + j * .35) * k, .25 * k, p[2] - (.9 + (j % 2) * .35) * k)
                s.beam('splayed_frog_finger', bone, p, end, .30 * k, 'fur')
                s.ball('round_finger_pad', bone, end, (.56 * k, .35 * k, .6 * k), 'accent')
        s.box('frog_nostril', 'head', (side * .7 * h, hc[1] + .15 * h, hc[2] - depth * .315 * h), (.21, .21, .15), 'nose')
    for j in range(5):
        x = (j - 2) * width * .10 * h
        y = hc[1] - height * .22 * h + (abs(j - 2) * .08)
        s.box('gentle_frog_smile', 'head', (x, y, hc[2] - depth * .37 * h), (width * .102 * h, .14, .12), 'nose', (0, 0, (j - 2) * 4))
    return hc


def _toad(stage):
    s = _new(stage, fur='#97A16A', dark='#627444', accent='#B8BD80', moss='#668853', mushroom='#D89C70')
    k = s.scale
    by = 3.9 * k
    hc = _frog_anatomy(s, by, 10.2, 9.3, 6.9, rough=True)
    for row in range(3 + s.age):
        for col in range(-2, 3):
            x, z = col * 1.65 * k, (row * 1.2 - .5) * k
            y = by + math.sqrt(max(.1, 1 - (x / (5.4 * k)) ** 2)) * 3.0 * k
            s.ball('moss_cushion', 'body', (x, y, z), ((1.2 + .15 * (row % 2)) * k, 1.0 * k, 1.5 * k), 'moss' if (row + col) % 2 else 'accent')
            if s.age == 2 and (row + col) % 3 == 0:
                s.box('moss_sprig', 'body', (x, y + .7 * k, z), (.25, 1.0, .25), 'moss', (0, 0, col * 12))
    for j in range(1 + s.age):
        x, z = (j - s.age / 2) * 2.5 * k, 1.8 * k + j * .5 * k
        y = by + 3.0 * k
        s.box('mushroom_stalk', 'body', (x, y + .65 * k, z), (.45 * k, 1.6 * k, .45 * k), 'cream')
        s.mass('mushroom_cap', 'body', (x, y + 1.55 * k, z), (1.8 * k, .8 * k, 1.7 * k), 'mushroom')
        for side in (-1, 1):
            s.box('mushroom_dot', 'body', (x + side * .42 * k, y + 1.96 * k, z), (.32 * k, .12, .32 * k), 'cream')
    return _finish(s)


def _bird_body(s, by, width=7, depth=8, height=8):
    k = s.scale
    s.bones['body'] = ('root', [0, by, 0])
    _round(s, 'downy_bird_body', 'body', (0, by, .5 * k), (width * k, height * k, depth * k), 'fur', 7)
    for side, name in ((-1, 'l'), (1, 'r')):
        _limb(s, 'leg_' + name, (side * 1.6 * k, 2.1 * k, .2 * k), (side * 1.8 * k, .5 * k, -.7 * k), 1.0 * k, mat='beak', paw='beak')
        for j in range(3):
            s.box('webbed_toe', 'leg_' + name + '_lower', (side * 1.8 * k + (j - 1) * .46 * k, .24 * k, -1.55 * k), (.5 * k, .38 * k, 1.8 * k), 'beak', (0, (j - 1) * 12, 0))
        bone = s.bone('wing_' + name, 'body', (side * width * .37 * k, by + height * .13 * k, -.7 * k))
        for j in range(2 + s.age):
            s.mass('overlapping_wing_feather', bone, (side * (width * .44 + j * .08) * k, by + (.6 - j * .6) * k, (j * .85) * k), (1.6 * k, 3.7 * k, 2.2 * k), 'wing')


def _duck(stage):
    s = _new(stage, fur='#F1DA90', accent='#E2AF36', wing='#F5E6AD', beak='#DDA454', petal='#F7CB53', heart='#8D623D')
    k, h = s.scale, s.head_scale
    by = 5.0 * k
    _bird_body(s, by, 7.8, 9.4, 7.7)
    c = (0, by + 4.1 * h, -2.4 * k)
    size = _head(s, c, (6.7, 5.9, 5.2), muzzle=False, eye=.9)
    s.mass('broad_duck_bill', 'head', (0, c[1] - 1.3 * h, c[2] - 3.0 * h), (4.6 * h, 1.35 * h, 3.0 * h), 'beak')
    s.box('bill_seam', 'head', (0, c[1] - 1.4 * h, c[2] - 4.52 * h), (3.6 * h, .1, .10), 'heart')
    for side in (-1, 1):
        s.box('bill_nostril', 'head', (side * 1.1 * h, c[1] - .62 * h, c[2] - 3.7 * h), (.3, .12, .35), 'heart')
    for j in range(3 + s.age):
        x = (j - (2 + s.age) / 2) * .8 * k
        tail = s.bone('tail' if j == 0 else 'tail_' + str(j), 'body', (x, by + 1 * k, 4.0 * k))
        s.mass('fan_tail_feather', tail, (x, by + 1.7 * k, 5.0 * k), (1.4 * k, 1.6 * k, 3.0 * k), 'wing')
    flower = (0, c[1] + size[1] * .49, c[2])
    petals = (5, 8, 12)[s.age]
    for j in range(petals):
        a = j * math.tau / petals
        s.mass('sunflower_hat_petal', 'head', (flower[0] + math.cos(a) * 1.65 * h, flower[1] + .1, flower[2] + math.sin(a) * 1.65 * h), (1.8 * h, .45 * h, 1.0 * h), 'petal')
    s.ball('sunflower_hat_center', 'head', (flower[0], flower[1] + .3, flower[2]), (2.5 * h, .9 * h, 2.5 * h), 'heart')
    for j in range(4 + 2 * s.age):
        a = j * 2.4
        r = .5 + (j % 2) * .3
        s.box('sunflower_seed', 'head', (math.sin(a) * r * h, flower[1] + .76 * h, flower[2] + math.cos(a) * r * h), (.24, .13, .3), 'cream')
    return _finish(s)


def _chick(stage):
    s = _new(stage, fur='#F4DC8D', accent='#C6DAD2', wing='#FFE8A9', beak='#DAA25A', porcelain='#F0EEE3', rim='#C8AD77', flower='#C28C9C')
    k, h = s.scale, s.head_scale
    by = 4.2 * k
    _bird_body(s, by, 6.7, 6.2, 7.0)
    # A hollow-looking cup shell, fitted around the lower body, with a handle.
    _round(s, 'porcelain_eggcup', 'body', (0, 2.9 * k, .6 * k), (7.7 * k, 4.9 * k, 7.2 * k), 'porcelain', 6)
    for side in (-1, 1):
        s.mass('cup_rim_side', 'body', (side * 3.4 * k, 5.15 * k, .6 * k), (.65 * k, .7 * k, 5.8 * k), 'rim' if s.age else 'accent')
        s.mass('cup_rim_edge', 'body', (0, 5.15 * k, .6 * k + side * 3.0 * k), (6.2 * k, .7 * k, .65 * k), 'rim' if s.age else 'accent')
    for j in range(6):
        a, b = -math.pi * .6 + j * math.pi * 1.2 / 6, -math.pi * .6 + (j + 1) * math.pi * 1.2 / 6
        # A C handle is joined into the right wall at both ends.
        p = (3.3 * k + math.cos(a) * 2.0 * k, 3.9 * k + math.sin(a) * 1.5 * k, .8 * k)
        q = (3.3 * k + math.cos(b) * 2.0 * k, 3.9 * k + math.sin(b) * 1.5 * k, .8 * k)
        s.beam('porcelain_handle', 'body', p, q, .65 * k, 'porcelain')
    s.mass('cup_foot', 'body', (0, .8 * k, .6 * k), (5.2 * k, 1.0 * k, 4.9 * k), 'accent')
    c = (0, by + 4.5 * h, -.5 * k)
    size = _head(s, c, (6.3, 5.9, 5.1), muzzle=False, eye=.94)
    s.box('tiny_triangular_beak', 'head', (0, c[1] - .8 * h, c[2] - 2.75 * h), (1.35 * h, .9 * h, 1.3 * h), 'beak', (18, 0, 0))
    for j in range(2 + s.age):
        s.box('chick_head_down', 'head', ((j - (1 + s.age) / 2) * .6 * h, c[1] + 3.0 * h, c[2]), (.65 * h, 1.3 * h, .8 * h), 'fur', (0, 0, (j - 1) * -20))
    for j in range(1 + s.age):
        x = (j - s.age / 2) * 1.8 * k
        for p in range(5):
            a = p * math.tau / 5
            s.box('painted_cup_flower', 'body', (x + math.cos(a) * .44 * k, 3.4 * k + math.sin(a) * .44 * k, -2.92 * k), (.55 * k, .4 * k, .12), 'flower', (0, 0, p * 72))
        s.box('painted_flower_heart', 'body', (x, 3.4 * k, -3.02 * k), (.35 * k, .35 * k, .12), 'rim')
    if s.age == 2:
        _leaf(s, 'tea_leaf_crest', 'head', (0, c[1] + 2.8 * h, c[2]), (1.7 * h, c[1] + 4.1 * h, c[2]), 1.1 * h)
    return _finish(s)


def _fox(stage):
    s = _new(stage, fur='#D78D58', dark='#9F674B', accent='#C6A365', pumpkin='#E4A15A', leaf_dark='#7C935D')
    k, h = s.scale, s.head_scale
    by = _quad(s, 6.0, 10.5, 6.4, 4.7, paw='dark')
    c = (0, by + 2.8 * h, -4.7 * k)
    size = _head(s, c, (6.7, 5.8, 5.6), eye=.78)
    _ears(s, c, size, shape='point', length=(3.0, 3.6, 4.3)[s.age], width=2.6, inside='dark')
    for side in (-1, 1):
        for j in range(2 + s.age):
            s.box('fox_cheek_ruff', 'head', (side * (2.7 + .15 * j) * h, c[1] - .8 * h - j * .44 * h, c[2] - .4 * h), (1.5 * h, 1.4 * h, 3.0 * h), 'cream', (0, 0, side * (18 + j * 12)))
    pts = [(0, by + .1 * k, 4.5 * k), (1.2 * k, by + .4 * k, 7.1 * k), (3.0 * k, by + 1.0 * k, 9.5 * k), (4.0 * k, by + 2.3 * k, (10.2 + s.age * .5) * k)]
    _tail(s, pts, [(3.0 + .25 * s.age) * k, (3.7 + .4 * s.age) * k, 2.8 * k], ['fur', 'fur', 'cream'], puffy=True)
    for j in range(3 + s.age * 2):
        a = j * math.tau / (3 + s.age * 2)
        _leaf(s, 'pumpkin_leaf_collar', 'body', (0, by + 3.1 * k, -2.0 * k), (math.cos(a) * 3.4 * k, by + 1.4 * k + math.sin(a) * .9 * k, -2.7 * k + math.sin(a) * 1.5 * k), 1.8 * k, 'leaf_dark')
    if s.age:
        pc = (3.0 * k, by + 2.3 * k, 1.9 * k)
        s.ball('pumpkin_bag', 'body', pc, (3.6 * k, 3.3 * k, 3.5 * k), 'pumpkin')
        for j in range(5):
            a = j * math.tau / 5
            s.box('pumpkin_rib', 'body', (pc[0] + math.sin(a) * 1.5 * k, pc[1], pc[2] + math.cos(a) * 1.5 * k), (.22, 2.4 * k, .25), 'dark')
        s.box('pumpkin_stem', 'body', (pc[0], pc[1] + 1.8 * k, pc[2]), (.6 * k, 1.0 * k, .6 * k), 'leaf_dark', (0, 0, 18))
    return _finish(s)


def _rainfrog(stage):
    s = _new(stage, fur='#B7C8A0', dark='#7A997B', accent='#D5D9AA', water='#ADD6DF', leaf_light='#91B89B')
    k, h = s.scale, s.head_scale
    hc = _frog_anatomy(s, 3.8 * k, 9.0, 8.2, 7.6)
    # Leaf umbrella is a fitted broad cap with visible radial veins.
    yy = hc[1] + 4.2 * h
    w = (5.0, 7.0, 9.0)[s.age] * k
    for j in range(5 + s.age):
        a = j * math.tau / (5 + s.age)
        root = (0, yy, .1 * k)
        tip = (math.sin(a) * w * .6, yy - .4 * k, .1 * k + math.cos(a) * w * .48)
        _leaf(s, 'rain_hat_leaf', 'head', root, tip, w * .34, 'leaf_light')
        if j < 1 + s.age:
            s.ball('dew_drop', 'head', (tip[0], tip[1] + .24 * k, tip[2]), (.7 * k, .95 * k, .7 * k), 'water')
            s.box('dew_glint', 'head', (tip[0] - .12 * k, tip[1] + .44 * k, tip[2] - .31 * k), (.16, .2, .08), 'shine')
    s.beam('hat_stem', 'head', (0, yy - .4 * k, 0), (.4 * k, yy + 1.0 * k, .2 * k), .4 * k, 'vein')
    for side in (-1, 1):
        for j in range(3 + s.age):
            s.ball('rainfrog_side_spot', 'body', (side * 3.9 * k, (3.2 + (j % 2) * .7) * k, (-.5 + j * .7) * k), (.6 * k, .6 * k, .5 * k), 'dark')
    return _finish(s)


def _sloth(stage):
    s = _new(stage, fur='#AFA292', dark='#7D746B', accent='#BCD09A', claw='#F3E6CB', mask='#8D7768')
    k, h = s.scale, s.head_scale
    by = 5.4 * k
    s.bones['body'] = ('root', [0, by, 0])
    _round(s, 'sloth_seated_torso', 'body', (0, by, 1.0 * k), (7.8 * k, 10.0 * k, 7.5 * k), 'fur', 7)
    c = (0, by + 4.2 * h, -.7 * k)
    size = _head(s, c, (7.8, 6.3, 5.7), eye=.7)
    _round(s, 'sloth_face_disc', 'head', (0, c[1], c[2] - 2.15 * h), (6.3 * h, 5.1 * h, 1.9 * h), 'cream', 5)
    for side, n in ((-1, 'l'), (1, 'r')):
        x = side * 1.8 * h
        y, z = c[1] + .0 * h, c[2] - 3.13 * h
        s.mass('sloth_eye_mask', 'head', (x, y, z), (2.6 * h, 1.8 * h, .22), 'mask')
        s.disc('sleepy_sloth_eye', 'head', (x, y, z - .17), .75 * h, .9 * h, .18, 'pupil')
        s.box('sleepy_eye_glint', 'head', (x - .15 * h, y + .2 * h, z - .29), (.24 * h, .22 * h, .08), 'shine')
        s.box('sleepy_eyelid', 'head', (x, y + .36 * h, z - .28), (1.0 * h, .23 * h, .1), 'mask', (0, 0, side * -9))
        arm = s.bone('arm_' + n, 'body', (side * 3.2 * k, by + 3.0 * k, .1 * k))
        elbow = (side * 4.7 * k, by - 1.5 * k, -1.0 * k)
        hand = (side * 2.0 * k, by - 2.4 * k, -3.8 * k)
        s.beam('long_sloth_upper_arm', arm, (side * 3.2 * k, by + 3.0 * k, .1 * k), elbow, 2.2 * k, 'fur')
        lower = s.bone('arm_' + n + '_lower', arm, elbow)
        s.beam('long_sloth_forearm', lower, elbow, hand, 1.9 * k, 'fur')
        s.ball('sloth_palm', lower, hand, (2.2 * k, 1.8 * k, 2.2 * k), 'fur')
        for j in range(3):
            s.box('curved_sloth_claw', lower, (hand[0] + (j - 1) * .55 * k, hand[1] - .4 * k, hand[2] - .9 * k), (.34 * k, 1.3 * k, .45 * k), 'claw', (25, 0, 0))
        _limb(s, 'leg_' + n, (side * 2.6 * k, 2.6 * k, 1.3 * k), (side * 2.6 * k, .6 * k, -.6 * k), 2.0 * k, paw='dark')
        for j in range(2 + s.age):
            s.box('long_coat_strand', 'body', (side * 3.6 * k, (6.3 - j * 1.0) * k, 1.8 * k), (.5 * k, 1.6 * k, 2 * k), 'dark', (0, 0, side * 8))
    s.ball('soft_leaf_pillow', 'body', (0, by - 2.0 * k, -3.8 * k), (5.4 * k, 3.1 * k, 2.5 * k), 'accent')
    s.beam('pillow_midrib', 'body', (-2.4 * k, by - 2.0 * k, -5.05 * k), (2.4 * k, by - 2.0 * k, -5.05 * k), .18, 'vein')
    for side in (-1, 1):
        for j in range(2 + s.age):
            root = (side * (.3 + j * .5) * k, by - 2.0 * k, -5.10 * k)
            tip = (side * (.8 + j * .5) * k, by - .95 * k, -4.9 * k)
            s.beam('pillow_leaf_vein', 'body', root, tip, .11, 'vein')
    return _finish(s)


def _redpanda(stage):
    s = _new(stage, fur='#C77D5E', dark='#6F4F43', accent='#C96978', stripe='#E7BC9E', cherry='#BC5D6F')
    k, h = s.scale, s.head_scale
    by = _quad(s, 7.5, 10.9, 7.2, 3.6, paw='dark')
    c = (0, by + 3.0 * h, -4.4 * k)
    size = _head(s, c, (7.4, 6.1, 5.8), eye=.82)
    _ears(s, c, size, shape='point', length=2.4, width=2.8, inside='cream')
    for side in (-1, 1):
        # White eyebrow and slanted tear marking are separate connected inlays.
        xx = side * 1.75 * h
        s.mass('panda_white_brow', 'head', (xx, c[1] + 1.4 * h, _front(c, size, xx, c[1] + 1.4 * h, 7) - .05), (2.0 * h, .7 * h, .18), 'cream')
        s.box('panda_dark_tear', 'head', (side * 2.15 * h, c[1] - .8 * h, _front(c, size, side * 2.15 * h, c[1] - .8 * h, 7) - .06), (.5 * h, 1.3 * h, .18), 'dark', (0, 0, side * -17))
        for j in range(2 + s.age):
            s.mass('white_panda_cheek_ruff', 'head', (side * (2.9 + .08 * j) * h, c[1] - (1.0 + .45 * j) * h, c[2] -.5 * h), (2.0 * h, 1.1 * h, 3.0 * h), 'cream')
    n = (4, 6, 8)[s.age]
    pts = [(0, by + .7 * k, 4.7 * k)]
    for j in range(1, n + 1):
        t = j / n
        pts.append((math.sin(t * 1.4) * 4.2 * k, by + .8 * math.sin(t * math.pi) * k - t * 2.1 * k, (4.7 + t * 8.5) * k))
    _tail(s, pts, [(3.1 - .9 * j / n) * k for j in range(n)], ['fur', 'stripe'], puffy=True)
    _scarf(s, by + 2 * k, -4.0 * k, 5.3 * k)
    for side in ((0,) if s.age == 0 else (-1, 1)):
        root = (0, by + 1.8 * k, -4.5 * k)
        cherry = (side * .7 * k, by + .5 * k, -4.7 * k)
        s.beam('cherry_stem', 'body', root, cherry, .14, 'vein')
        _berry(s, 'cherry_charm', 'body', cherry, .7 * k, 'cherry')
    if s.age == 2:
        for j in range(4):
            s.box('bamboo_satchel_pole', 'body', (3.8 * k, by - .1 * k, (.3 + j * .55) * k), (1.0, 3.0, .48), 'leaf')
            for yy in (-.9, .5):
                s.box('bamboo_node', 'body', (4.33 * k, by + yy, (.3 + j * .55) * k), (.12, .2, .55), 'vein')
        s.beam('panda_satchel_strap', 'body', (2.7, by + 2.5, -1.7), (3.8, by + 1.1, 1.1), .3, 'dark')
    return _finish(s)


def _chinchilla(stage):
    s = _new(stage, fur='#C4C2CE', dark='#9693A6', accent='#D6D5DF', cotton='#FFF7E9', stem='#9BA48D')
    k, h = s.scale, s.head_scale
    by = 4.8 * k
    s.bones['body'] = ('root', [0, by, 0])
    _round(s, 'round_chinchilla_body', 'body', (0, by, .8 * k), (8.5 * k, 9.0 * k, 8 * k), 'fur', 8)
    c = (0, by + 3.5 * h, -1.9 * k)
    size = _head(s, c, (7.3, 6.3, 5.7), eye=.99)
    _ears(s, c, size, length=(4, 4.4, 4.8)[s.age], width=3.4, inside='pink')
    for side, n in ((-1, 'l'), (1, 'r')):
        _limb(s, 'leg_' + n, (side * 2.3 * k, 2.3 * k, .7 * k), (side * 2.5 * k, .5 * k, -1.0 * k), 1.65 * k, paw='cream')
        arm = s.bone('arm_' + n, 'body', (side * 3.1 * k, by + .6 * k, -.9 * k))
        s.beam('tiny_forearm', arm, (side * 3.1 * k, by + .6 * k, -.9 * k), (side * 1.5 * k, by - 1 * k, -3.1 * k), 1.5 * k, 'fur')
        s.ball('tiny_holding_paw', arm, (side * 1.5 * k, by - 1 * k, -3.1 * k), (1.4 * k, 1.3 * k, 1.4 * k), 'cream')
        for j in range(3 + s.age):
            s.box('silver_cheek_fur', 'head', (side * (3.1 + .12 * j) * h, c[1] - (.6 + .38 * j) * h, c[2] + .2 * h), (1.5 * h, .7 * h, 3.2 * h), 'accent', (0, 0, side * (18 + j * 9)))
        for j in range(3):
            s.beam('fine_chinchilla_whisker', 'head', (side * 1.6 * h, c[1] - 1.2 * h, c[2] - 2.9 * h), (side * 4.8 * h, c[1] - (j * .65) * h, c[2] - 2.4 * h), .07, 'cream')
    _tail(s, [(0, by - .4 * k, 4 * k), (1.2 * k, by + 1.6 * k, 5.9 * k), (2.6 * k, by + 2.9 * k, 5.4 * k)], [2.6 * k, 2.2 * k], ['fur', 'accent'], puffy=True)
    # Held botanical cotton: baby's one bloom develops to a three-boll spray.
    for j in range(1 + s.age):
        root = (0, by - 1.0 * k, -3.25 * k)
        bloom = ((j - s.age / 2) * 1.7 * k, by + (1.0 + (j % 2) * .8) * k, -3.4 * k)
        s.beam('cotton_stem', 'body', root, bloom, .18, 'stem')
        for q in range(3):
            a = q * math.tau / 3
            s.ball('cotton_boll', 'body', (bloom[0] + math.cos(a) * .5 * k, bloom[1] + math.sin(a) * .5 * k, bloom[2]), (1.35 * k, 1.35 * k, 1.2 * k), 'cotton')
        s.box('cotton_calyx', 'body', (bloom[0], bloom[1] - .65 * k, bloom[2]), (1.1 * k, .35 * k, 1.0 * k), 'stem', (0, 0, 45))
    return _finish(s)


def _seal(stage):
    s = _new(stage, fur='#CCDDE3', dark='#91AFBE', accent='#A8CDDC', spot='#B3C7D0', water='#B7DEE6')
    k, h = s.scale, s.head_scale
    by = 3.6 * k
    s.bones['body'] = ('root', [0, by, 0])
    _round(s, 'seal_teardrop_body', 'body', (0, by, 1.2 * k), (9.0 * k, 7.3 * k, 12.3 * k), 'fur', 8)
    _round(s, 'seal_belly', 'body', (0, by - .7 * k, -2.1 * k), (6.7 * k, 4.7 * k, 4.2 * k), 'cream', 6)
    c = (0, by + 2.8 * h, -3.8 * k)
    size = _head(s, c, (7.8, 6.9, 6.7), eye=.99)
    for side, n in ((-1, 'l'), (1, 'r')):
        root = (side * 3.4 * k, by - 1.0 * k, -1.6 * k)
        fin = s.bone('arm_' + n, 'body', root)
        end = (side * (6.4 + s.age * .5) * k, .7 * k, -3.2 * k)
        s.beam('broad_front_flipper', fin, root, end, 2.0 * k, 'fur', 2.3 * k)
        s.mass('flipper_tip', fin, end, (2.3 * k, .8 * k, 2.4 * k), 'dark')
        for j in range(3):
            s.box('flipper_finger_groove', fin, (end[0] + side * .7 * k, end[1] + .42 * k, end[2] + (j - 1) * .6 * k), (.7 * k, .08, .13), 'fur')
        for j in range(3):
            s.beam('seal_whisker', 'head', (side * 2.0 * h, c[1] - 1.4 * h, c[2] - 3.5 * h), (side * 4.6 * h, c[1] - (.5 + .7 * j) * h, c[2] - 3 * h), .09, 'cream')
        for j in range(2 + s.age):
            s.disc('seal_freckle', 'head', (side * (1.6 + .4 * (j % 2)) * h, c[1] - (1.4 + .4 * (j // 2)) * h, c[2] - 3.65 * h), .18, .18, .1, 'dark')
    _tail(s, [(0, 2.1 * k, 5.5 * k), (0, 1.3 * k, 8.3 * k)], 2.7 * k, 'fur', .65)
    for side, n in ((-1, 'l'), (1, 'r')):
        tail = s.bone('tail_fluke_' + n, 'tail', (0, 1.3 * k, 8.3 * k))
        s.beam('split_tail_fluke', tail, (0, 1.3 * k, 8.3 * k), (side * (2.5 + .4 * s.age) * k, 1.1 * k, 10.2 * k), 2.8 * k, 'dark', 1.0 * k)
        for j in range(2 + s.age):
            s.box('fluke_ray', tail, (side * (1.0 + j * .45) * k, 1.65 * k, 9.7 * k), (.15, .1, 1.3 * k), 'fur', (0, side * -35, 0))
    for row in range(2 + s.age):
        for side in (-1, 1):
            s.ball('dappled_coat_spot', 'body', (side * (3.8 - .2 * row) * k, by + .7 * k, (1.1 + row * 1.2) * k), (.8 * k, .7 * k, .9 * k), 'spot')
    _scarf(s, by + 2.0 * k, -4.6 * k, 6.1 * k, style='knit')
    for j in range(1 + s.age):
        p = ((j - s.age / 2) * 1.2 * k, by + 1.2 * k, -5.4 * k)
        s.ball('bubble_collar_charm', 'body', p, (.95 * k, 1.0 * k, .8 * k), 'water')
        s.box('bubble_glint', 'body', (p[0] - .2 * k, p[1] + .25 * k, p[2] - .4 * k), (.2, .25, .08), 'shine')
    return _finish(s)


_BUILDERS = {
    'strawberry_hamster': _hamster,
    'cocoa_capybara': _capybara,
    'honey_otter': _otter,
    'peach_shiba': _shiba,
    'blueberry_bat': _bat,
    'mint_ferret': _ferret,
    'maple_raccoon': _raccoon,
    'lavender_cat': _cat,
    'acorn_squirrel': _squirrel,
    'buttercream_corgi': _corgi,
    'pebble_pangolin': _pangolin,
    'moss_toad': _toad,
    'sunflower_duck': _duck,
    'teacup_chick': _chick,
    'pumpkin_fox': _fox,
    'rain_frog': _rainfrog,
    'sleepy_sloth': _sloth,
    'cherry_red_panda': _redpanda,
    'cotton_chinchilla': _chinchilla,
    'bubble_seal': _seal,
}
SPECIES = tuple(_BUILDERS)


def build(species, stage):
    """Return exporter-ready bones, geometry and the individual material palette."""
    try:
        return _BUILDERS[species](stage)
    except KeyError as exc:
        raise ValueError('Unknown common collection A species: ' + species) from exc


def _preview():
    """Render the real exporter output in memory; never touch shared pack assets."""
    import base64
    import io
    import json
    import sys
    from pathlib import Path
    import numpy as np
    from PIL import Image, ImageDraw, ImageFont

    root = Path(__file__).resolve().parents[2]
    sys.path.insert(0, str(root / 'tools'))
    import pet_model_engine as engine
    import preview_arkhaia_rig as renderer

    output = root / 'previews'
    font = ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', 17)
    sheet = Image.new('RGB', (1500, 1340), (23, 21, 34))
    draw = ImageDraw.Draw(sheet)
    records = json.loads(Path(__file__).with_name('pet_expansion_common_a.json').read_text(encoding='utf8'))
    for index, species in enumerate(SPECIES):
        model, texture = engine.build_model(species, 'adult')
        renderer.MODEL = model
        renderer.ELEMENTS = {e['uuid']: e for e in model['elements']}
        renderer.CLIPS = {c['name']: c for c in model['animations']}
        renderer.TEXTURE = np.array(texture.convert('RGBA'))
        sculpt = build(species, 'adult')
        spans = [max(p['from'][i] + p['size'][i] for p in sculpt['parts']) - min(p['from'][i] for p in sculpt['parts']) for i in range(3)]
        span = max(19, spans[0] * 1.15, spans[1] * 1.45, spans[2] * 1.15)
        im = renderer.render('idle', 0, yaw=24, elevation=13, size=300, span=span)
        x, y = index % 5 * 300, index // 5 * 335
        sheet.paste(im, (x, y))
        draw.text((x + 12, y + 302), records[index]['display'], font=font, fill='#E5D9CA')
        print(species, len(sculpt['parts']), 'span', round(span, 1), flush=True)
    sheet.save(output / 'common_a_expansion_adults.png')
    examples = ('strawberry_hamster', 'peach_shiba', 'blueberry_bat', 'pebble_pangolin', 'teacup_chick', 'bubble_seal')
    growth = Image.new('RGB', (1260, len(examples) * 370), (23, 21, 34))
    gd = ImageDraw.Draw(growth)
    for row, species in enumerate(examples):
        adult = build(species, 'adult')
        bounds = [[min(p['from'][i] for p in adult['parts']), max(p['from'][i] + p['size'][i] for p in adult['parts'])] for i in range(3)]
        span = max(19, (bounds[0][1] - bounds[0][0]) * 1.12, bounds[1][1] * 1.4, (bounds[2][1] - bounds[2][0]) * 1.12)
        for col, stage in enumerate(('baby', 'medium', 'adult')):
            model, texture = engine.build_model(species, stage)
            renderer.MODEL = model
            renderer.ELEMENTS = {e['uuid']: e for e in model['elements']}
            renderer.CLIPS = {c['name']: c for c in model['animations']}
            renderer.TEXTURE = np.array(texture.convert('RGBA'))
            im = renderer.render('idle', 0, yaw=24, elevation=13, size=340, span=span)
            growth.paste(im, (col * 420 + 40, row * 370))
            gd.text((col * 420 + 24, row * 370 + 342), species.replace('_', ' ').title() + ' / ' + stage, font=font, fill='#E5D9CA')
    growth.save(output / 'common_a_expansion_growth.png')


if __name__ == '__main__':
    _preview()
