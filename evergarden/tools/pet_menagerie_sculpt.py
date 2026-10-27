"""Twenty more wild Evergarden companions, each sculpted afresh for all three growth stages.

Same contract and units as pet_rare_sculpt (16 units = one block, the animal faces -z):
seven cute (Mochi Slime, Cloud Lamb, Moon Rabbit, Bloom Hedgehog, Coral Axolotl,
Clover Bumble, Aurora Penguin), seven fierce (Storm Raptor, Magma Rhino, Eclipse
Scorpion, Raijin Tiger, Titan Beetle, Anubis Jackal, Bone Drake) and six beautiful
(Jade Lung, Crystal Stag, Luna Moth, Celestial Koi, Starlight Peacock, Aurora Jelly).
Babies keep big heads and eyes; adults gain the full ornamental set.
"""
from __future__ import annotations

import math

from pet_rare_sculpt import Sculpt, _palette


# ---------------------------------------------------------------------------
# Shared shapes


def _front(size, center, x, y):
    """Front (-z) surface of a Sculpt.ball at (x, y), so face details sit on it."""
    w, h, d = size
    cx, cy, cz = center
    dx, dy = abs(x - cx), abs(y - cy)
    if dx <= .3 * w and dy <= .3 * h:
        return cz - .5 * d
    if dx <= .42 * w and dy <= .42 * h:
        return cz - .42 * d
    return cz - .3 * d


def _ellipsoid(s, name, bone, center, radii, mat, layers=6, cut=-1.0, top=1.0):
    """A stepped voxel ellipsoid: rounded-octagon slabs following its profile. The three
    boxes of a slab differ slightly in height so their faces are never coplanar."""
    cx, cy, cz = center
    a, b, c = radii
    for i in range(layers):
        t0, t1 = cut + (top - cut) * i / layers, cut + (top - cut) * (i + 1) / layers
        tm = (t0 + t1) / 2
        f = math.sqrt(max(.08, 1 - tm * tm))
        y, hh = cy + tm * b, (t1 - t0) * b + .04
        s.box(name + "_core", bone, (cx, y, cz), (2 * a * f * .88, hh, 2 * c * f * .88), mat)
        s.box(name + "_wide", bone, (cx, y, cz), (2 * a * f, hh * .94, 2 * c * f * .6), mat)
        s.box(name + "_deep", bone, (cx, y, cz), (2 * a * f * .6, hh * .9, 2 * c * f), mat)


def _ellipsoid_front(center, radii, x, y, layers=6, cut=-1.0, top=1.0):
    """Front (-z) surface of an _ellipsoid at (x, y)."""
    cx, cy, cz = center
    a, b, c = radii
    t = max(cut, min(top - 1e-6, (y - cy) / b))
    i = min(layers - 1, int((t - cut) / (top - cut) * layers))
    tm = cut + (top - cut) * (i + .5) / layers
    f = math.sqrt(max(.08, 1 - tm * tm))
    dx = abs(x - cx)
    if dx <= a * f * .6:
        return cz - c * f
    if dx <= a * f * .88:
        return cz - c * f * .88
    return cz - c * f * .6


def _flower(s, bone, center, r, petal, heart, petals=5, facing="front", spin=0):
    """A blossom of rotated petals around a heart, facing the viewer or the sky."""
    x, y, z = center
    for i in range(petals):
        a = i * math.tau / petals + math.pi / 2 + math.radians(spin)
        dx, dy = math.cos(a) * r * .62, math.sin(a) * r * .62
        if facing == "front":
            s.box("blossom_petal", bone, (x + dx, y + dy, z), (r * .95, r * .62, .28), petal, (0, 0, math.degrees(a)))
        else:
            s.box("blossom_petal", bone, (x + dx, y, z + dy), (r * .95, .28, r * .62), petal, (0, -math.degrees(a), 0))
    if facing == "front":
        s.box("blossom_heart", bone, (x, y, z - .2), (r * .5, r * .5, .32), heart, (0, 0, 45))
    else:
        s.box("blossom_heart", bone, (x, y + .2, z), (r * .5, .32, r * .5), heart, (0, 45, 0))


def _chain(s, name, bones, points, widths, mats, depths=None, joints=True):
    """Beams through a polyline; per-segment bone, width and material, with joint caps."""
    for i in range(len(points) - 1):
        pick = lambda v: v[i] if isinstance(v, (list, tuple)) else v
        bone, width, mat = pick(bones), pick(widths), pick(mats)
        depth = depths[i] if isinstance(depths, (list, tuple)) else depths
        s.beam(name, bone, points[i], points[i + 1], width, mat, depth)
        if joints and i:
            s.box(name + "_joint", bone, points[i], (width * .92, width * .92, (depth or width) * .92), mat)


def _leg(s, name, hip, foot, width, mat, lower_mat=None, knee=(.5, 0), lower_width=None, paw=None, paw_mat=None):
    """An upper limb bone and a separate lower bone, so walk cycles bend the knee."""
    bone = s.bone(name, "body", hip)
    t, dz = knee
    joint = (hip[0] + (foot[0] - hip[0]) * t, hip[1] + (foot[1] - hip[1]) * t, hip[2] + (foot[2] - hip[2]) * t + dz)
    s.beam("upper_limb", bone, hip, joint, width, mat)
    lower = s.bone(name + "_lower", bone, joint)
    lw = lower_width or width * .8
    s.box("knee_joint", lower, joint, (lw * 1.05, lw * 1.05, lw * 1.05), lower_mat or mat)
    top = (foot[0], foot[1] + (paw[1] if paw else 0), foot[2])
    s.beam("lower_limb", lower, joint, top, lw, lower_mat or mat)
    if paw:
        s.mass("paw", lower, (foot[0], paw[1] / 2, foot[2] - paw[2] * .18), paw, paw_mat or lower_mat or mat)
    return bone, lower


def _dot_eyes(s, bone, x, y, z, size, shine="shine", pupil="pupil", blush=True):
    """Glossy round eyes with two catch-lights, the cute-mascot face."""
    for side in (-1, 1):
        xx = side * x
        s.box("round_eye", bone, (xx, y, z - .1), (size, size * 1.32, .28), pupil)
        s.box("round_eye_soft", bone, (xx, y, z - .06), (size * 1.2, size, .26), pupil)
        s.box("eye_shine", bone, (xx - size * .2, y + size * .3, z - .3), (size * .4, size * .4, .1), shine)
        s.box("eye_shine_small", bone, (xx + size * .22, y - size * .3, z - .3), (size * .2, size * .2, .1), shine)
        if blush:
            s.box("cheek_blush", bone, (xx + side * size * 1.15, y - size * .95, z - .06), (size * 1.05, size * .4, .12), "blush")


def _smile(s, bone, y, z, width, mat="pupil"):
    for side in (-1, 1):
        s.box("tiny_smile", bone, (side * width * .3, y, z - .08), (width * .55, width * .16, .1), mat, (0, 0, side * 28))


# ---------------------------------------------------------------------------
# Cute


def _mochi(stage):
    """A wobbly sakura-mochi slime. The whole blob is the head, so it turns to look at you;
    the adult is a two-tier kagami mochi under a flowering branch."""
    s = Sculpt(stage, _palette(jelly="#F7BFD6", jelly_deep="#E992B8", mochi="#FFF6F2", leaf="#7CBF68", twig="#7B5A47",
                               petal="#FFC4DC", petal_light="#FFEAF2", petal_core="#FFD45A", blush="#FF8DB6",
                               pupil="#3A2233", shine="#FFFFFF", eye="#3A2233"))
    h = s.head_scale
    w = (9.2, 12.0, 14.4)[s.age]
    hh, d = w * .74, w * .92
    s.bones["body"] = ("root", [0, .6, 0])
    s.mass("jelly_puddle", "body", (0, .5, 0), (w * 1.06, 1.0, d * 1.04), "jelly_deep")
    s.bone("head", "body", (0, 1.0, 0))
    cy = hh * .5 + .55
    blob = (w, hh, d)
    # A squat daifuku: one soft ball with a domed cap on top.
    blob = (w, hh * .84, d)
    cy -= hh * .06
    s.ball("mochi_blob", "head", (0, cy, 0), blob, "jelly")
    s.box("mochi_dome_cap", "head", (0, cy + blob[1] * .5 + hh * .05, 0), (w * .5, hh * .12, d * .48), "jelly")
    s.box("mochi_dome_top", "head", (0, cy + blob[1] * .5 + hh * .13, 0), (w * .26, hh * .06, d * .25), "jelly")
    s.box("jelly_sheen", "head", (-w * .17, cy + blob[1] * .5 + hh * .115, -d * .1), (w * .14, .1, d * .1), "shine")
    s.box("jelly_sheen_dot", "head", (-w * .27, cy + blob[1] * .34, -d * .505), (w * .07, w * .07, .1), "shine")
    ey = cy - hh * .02
    fz = _front(blob, (0, cy, 0), w * .18, ey)
    _dot_eyes(s, "head", w * .18, ey, fz, (1.55, 1.45, 1.4)[s.age] * h)
    _smile(s, "head", ey - 1.55 * h, fz, 1.3 * h)
    top = cy + blob[1] * .5 + hh * .14
    if s.age == 2:
        # Kagami mochi: a smaller white tier on the pink one.
        _ellipsoid(s, "upper_mochi_tier", "head", (0, top - hh * .02, .3), (w * .32, hh * .42, d * .31), "mochi", layers=4, cut=0)
        top += hh * .38
    s.beam("sprig_twig", "head", (0, top - .4, .5), (.5 * h, top + (1.6 + .6 * s.age) * h, .8), .5 * h, "twig")
    stem_top = (.5 * h, top + (1.6 + .6 * s.age) * h, .8)
    for side in (-1, 1):
        s.box("sprig_leaf", "head", (side * 1.25 * h, top + .9 * h, .6), (2.3 * h, .4 * h, 1.3 * h), "leaf",
              (0, side * 18, side * -24))
    if s.age == 0:
        _flower(s, "head", (stem_top[0], stem_top[1] + .5 * h, stem_top[2] - .2), 1.5 * h, "petal", "petal_core")
    else:
        _flower(s, "head", (stem_top[0], stem_top[1] + .6 * h, stem_top[2] - .3), 2.0 * h, "petal", "petal_core")
        for side in (-1, 1):
            twig_end = (stem_top[0] + side * 2.6 * h, stem_top[1] - .2 * h, stem_top[2])
            s.beam("branch_twig", "head", (stem_top[0], stem_top[1] - 1 * h, stem_top[2]), twig_end, .35 * h, "twig")
            _flower(s, "head", (twig_end[0], twig_end[1] + .3 * h, twig_end[2] - .25), 1.3 * h,
                    "petal_light" if side < 0 else "petal", "petal_core", spin=18)
    for side in (-1, 1):
        s.ball("jelly_droplet", "body", (side * w * .56, .75, -d * .3), (1.2 * h, 1.0 * h, 1.2 * h), "jelly")
    # Fallen petals scattered on the jelly puddle.
    for i in range(2 + 2 * s.age):
        a = i * 2.39996
        s.box("fallen_petal", "body", (math.cos(a) * w * .5, 1.05, math.sin(a) * d * .5), (1.0 * h, .2, .7 * h),
              "petal" if i % 2 else "petal_light", (0, math.degrees(a), 0))
    return s.finish()


def _lamb(stage):
    """A lamb made of cumulus puffs with curling pastel horns and a bell; the grown lamb
    carries its own little rainbow over its back."""
    s = Sculpt(stage, _palette(wool="#F6F3FC", wool_shade="#DED7EF", face="#6E5C82", face_light="#8F7CA6",
                               horn="#F6C3D8", horn_deep="#DC93B6", hoof="#56466A", inner_ear="#F4B3CB",
                               ribbon="#7FB6F0", eye="#4A3A63", pink_glow="#FFB6D3", blue_glow="#A8DAFF",
                               mint_glow="#B9F6D9"))
    k, h = s.scale, s.head_scale
    leg, length, width, height = 3.6, 10.4, 8.8, 7.6
    by = leg * k + height * k * .42
    s.bones["body"] = ("root", [0, by, 0])
    _ellipsoid(s, "wool_core", "body", (0, by, 0), (width * .44 * k, height * .46 * k, length * .46 * k), "wool", layers=5)
    for i, (x, y, z) in enumerate(((-1, .1, -.3), (1, .1, -.3), (-1, .15, .32), (1, .15, .32), (0, .78, -.1),
                                   (0, .62, .45), (-.55, .72, -.42), (.55, .72, -.42), (-.6, .8, .25), (.6, .8, .25),
                                   (0, .2, .62))):
        size = (4.4 if i < 4 else 3.9) * k
        s.ball("cloud_puff", "body", (x * width * .48 * k, by + y * height * .5 * k, z * length * .5 * k),
               (size, size * .88, size), "wool" if i % 3 else "wool_shade")
    for side in (-1, 1):
        for end, zz in (("f", -1), ("b", 1)):
            x, z = side * width * .27 * k, zz * length * .3 * k
            bone = s.bone("leg_" + end + ("l" if side < 0 else "r"), "body", (x, leg * k + .6 * k, z))
            s.ball("wool_leg_cuff", bone, (x, leg * k * .95, z), (2.6 * k, 1.9 * k, 2.6 * k), "wool")
            s.box("slender_leg", bone, (x, leg * k * .45, z), (1.35 * k, leg * k * .9, 1.35 * k), "face")
            s.box("little_hoof", bone, (x, .4 * k, z - .1 * k), (1.65 * k, .8 * k, 1.85 * k), "hoof")
    hy, hz = by + 2.2 * k + 1.1 * h, -length * .5 * k - .6 * h
    s.bone("head", "body", (0, hy - 1.8 * h, hz + 1.8 * h))
    s.mass("lamb_face", "head", (0, hy - .3 * h, hz - .2 * h), (4.7 * h, 4.9 * h, 4.6 * h), "face")
    s.mass("soft_muzzle", "head", (0, hy - 1.7 * h, hz - 2.4 * h), (3.3 * h, 2.2 * h, 1.5 * h), "face_light")
    s.ball("wool_cap", "head", (0, hy + 2.2 * h, hz + .3 * h), (6 * h, 3.3 * h, 5.6 * h), "wool")
    s.ball("wool_fringe", "head", (0, hy + 1.45 * h, hz - 2.05 * h), (3.9 * h, 1.7 * h, 1.4 * h), "wool")
    s.eyes("head", 1.25 * h, hy + .05 * h, hz - 2.5 * h, .62 * h, gentle=True)
    s.box("lamb_nose", "head", (0, hy - 1.35 * h, hz - 3.2 * h), (.9 * h, .45 * h, .2), "horn_deep")
    _smile(s, "head", hy - 2.0 * h, hz - 3.1 * h, .9 * h, "hoof")
    for side in (-1, 1):
        s.box("floppy_ear", "head", (side * 3.15 * h, hy + .5 * h, hz), (2.7 * h, 1.15 * h, 1.6 * h), "face", (0, 0, -side * 24))
        s.box("floppy_inner_ear", "head", (side * 3.2 * h, hy + .48 * h, hz - .82 * h), (1.8 * h, .55 * h, .14), "inner_ear",
              (0, 0, -side * 24))
        # A ram's horn spirals back, down and forward round a centre beside the temple.
        turns = (.9, 1.45, 2.0)[s.age] * math.pi
        cx, cy, cz = side * 2.85 * h, hy + .8 * h, hz + .6 * h
        steps = 4 + 2 * s.age
        pts = []
        for j in range(steps + 1):
            phi = turns * j / steps
            r = (1.75 - .9 * j / steps) * h
            pts.append((cx + side * .35 * h * j / steps, cy + r * math.cos(phi), cz + r * math.sin(phi)))
        _chain(s, "curled_horn", "head", pts, [(1.25 - .55 * j / steps) * h for j in range(steps)],
               ["horn" if j % 2 == 0 else "horn_deep" for j in range(steps)])
    s.box("ribbon_collar", "head", (0, hy - 2.85 * h, hz + 1.6 * h), (4.7 * h, .8 * h, 3.7 * h), "ribbon")
    s.ball("collar_bell", "head", (0, hy - 3.6 * h, hz - .2 * h), (1.35 * h, 1.35 * h, 1.35 * h), "gold")
    s.box("bell_slot", "head", (0, hy - 3.95 * h, hz - .9 * h), (.75 * h, .2 * h, .1), "hoof")
    s.bone("tail", "body", (0, by + 1.2 * k, length * .5 * k))
    s.ball("puff_tail", "tail", (0, by + 1.4 * k, length * .5 * k + 1.2 * k), (2.4 * k, 2.4 * k, 2.2 * k), "wool")
    if s.age == 2:
        top = by + height * .5 * k + 1.2
        for r, mat in ((5.0, "pink_glow"), (5.85, "blue_glow"), (6.7, "mint_glow")):
            s.crescent("little_rainbow", "body", (0, top, 1.2), r, .9, .9, mat, segments=7, opening=270)
        for side in (-1, 1):
            s.ball("rainbow_cloud", "body", (side * 5.9 * math.cos(math.radians(20)), top + 5.9 * math.sin(math.radians(20)), 1.2),
                   (3.6, 2.4, 2.8), "wool")
    if s.age > 0:
        s.box("star_charm", "head", (0, hy - 4.6 * h, hz - .3 * h), (.9 * h, .9 * h, .3), "gold_light", (0, 0, 45))
    return s.finish()


def _rabbit(stage):
    """The moon rabbit who pounds mochi: an upright snow-white rabbit with ruby eyes. The kit
    hugs a mochi ball; the grown rabbit swings its wooden mallet under a crescent halo."""
    s = Sculpt(stage, _palette(fur="#F7F4FD", fur_shade="#DCD3EF", cream="#FFF4E6", inner="#F5A8C7", nose="#EE88AE",
                               eye="#D0457C", wood="#BC8D5A", wood_dark="#7A5434", ribbon="#4E63C9",
                               moon_glow="#FFF2C2", mochi="#FFFFFF", mochi_pink="#FFC9DD"))
    k, h = s.scale, s.head_scale
    by = 5.4 * k
    s.bones["body"] = ("root", [0, by, 0])
    s.ball("round_rabbit_body", "body", (0, by + .6 * k, .3 * k), (7.4 * k, 8.6 * k, 7 * k), "fur")
    s.ball("cream_belly", "body", (0, by + .1 * k, -2.6 * k), (5 * k, 6.2 * k, 2 * k), "cream")
    for side in (-1, 1):
        leg = s.bone("leg_l" if side < 0 else "leg_r", "body", (side * 2.7 * k, 3.3 * k, 1.2 * k))
        s.ball("big_haunch", leg, (side * 3.1 * k, 2.9 * k, 1.3 * k), (3.2 * k, 4.8 * k, 5.6 * k), "fur")
        s.mass("long_hind_foot", leg, (side * 2.9 * k, .65 * k, -1.2 * k), (2.5 * k, 1.3 * k, 5.4 * k), "fur_shade")
        s.box("foot_pad", leg, (side * 2.9 * k, .3 * k, -3.3 * k), (1.3 * k, .3 * k, .9 * k), "inner")
        arm = s.bone("arm_l" if side < 0 else "arm_r", "body", (side * 2.6 * k, by + 2.6 * k, -2.2 * k))
        s.beam("little_arm", arm, (side * 2.6 * k, by + 2.6 * k, -2.2 * k), (side * 1.7 * k, by - .1 * k, -3.6 * k), 1.5 * k, "fur")
        s.ball("front_paw", arm, (side * 1.7 * k, by - .4 * k, -3.8 * k), (1.7 * k, 1.5 * k, 1.7 * k), "fur")
    hy = by + 4.6 * k + 2.7 * h
    hz = -.4 * k
    s.bone("head", "body", (0, hy - 2.6 * h, hz))
    head = (7.4 * h, 6.4 * h, 6.4 * h)
    s.ball("rabbit_head", "head", (0, hy, hz), head, "fur")
    fz = _front(head, (0, hy, hz), 0, hy)
    s.ball("cheek_puffs", "head", (0, hy - 1.5 * h, fz + 1.1 * h), (5.6 * h, 2.6 * h, 2.2 * h), "fur")
    s.eyes("head", 1.65 * h, hy + .45 * h, fz, .8 * h, gentle=True)
    s.box("rabbit_nose", "head", (0, hy - .85 * h, fz - .25), (1.0 * h, .6 * h, .3), "nose")
    _smile(s, "head", hy - 1.35 * h, fz - .2, .85 * h, "wood_dark")
    for side in (-1, 1):
        for j in range(2):
            s.box("whisker", "head", (side * 3.0 * h, hy - (1.0 + .45 * j) * h, fz + .4), (2.2 * h, .14 * h, .1), "fur_shade",
                  (0, side * 15, side * (8 - 16 * j)))
    ear = (3.8, 5.6, 7.2)[s.age] * h
    for side in (-1, 1):
        base = (side * 1.55 * h, hy + 2.7 * h, hz + .4 * h)
        if s.age == 0 and side > 0:
            # One kit ear still flops over.
            s.box("floppy_long_ear", "head", (side * 3.2 * h, hy + 2.9 * h, hz + .4 * h), (ear, 1.9 * h, 1.1 * h), "fur", (0, 0, -side * 18))
            s.box("floppy_inner_ear", "head", (side * 3.25 * h, hy + 2.85 * h, hz - .18 * h), (ear * .75, .95 * h, .15), "inner",
                  (0, 0, -side * 18))
            continue
        tip_bend = 18 if s.age == 2 and side < 0 else 0
        s.box("tall_ear", "head", (base[0] + side * .3 * h, base[1] + ear * .5, base[2]), (2.0 * h, ear, 1.15 * h), "fur", (0, 0, -side * 7))
        s.box("tall_inner_ear", "head", (base[0] + side * .3 * h, base[1] + ear * .48, base[2] - .62 * h), (1.0 * h, ear * .78, .15), "inner",
              (0, 0, -side * 7))
        if tip_bend:
            s.box("bent_ear_tip", "head", (base[0] - .2 * h, base[1] + ear + .6 * h, base[2] + .8 * h), (1.8 * h, 2.2 * h, 1.0 * h), "fur", (-tip_bend * 2, 0, 0))
    if s.age == 0:
        s.ball("hugged_mochi", "body", (0, by - .2 * k, -4.4 * k), (3.6 * k, 3.0 * k, 2.6 * k), "mochi")
        s.box("mochi_blush_dot", "body", (0, by + .1 * k, -5.75 * k), (1.1 * k, .9 * k, .12), "mochi_pink", (0, 0, 45))
    else:
        # A kine mallet held diagonally in the right paw; greet swings it.
        hand = (1.7 * k, by - .4 * k, -3.9 * k)
        reach = (3.2 if s.age == 2 else 2.4) * k
        top = (hand[0] + reach * .38, hand[1] + reach * 1.9, hand[2] - .5 * k)
        s.beam("mallet_handle", "arm_r", (hand[0] - .5 * k, hand[1] - 1.2 * k, hand[2]), top, .62 * k, "wood_dark")
        tilt = math.degrees(math.atan2(-(top[0] - hand[0]), top[1] - hand[1]))
        size = (2.6 if s.age == 2 else 2.0) * k
        s.box("mallet_head", "arm_r", top, (size * 2.0, size, size), "wood", (0, 0, tilt))
        for end in (-1, 1):
            off = (math.cos(math.radians(tilt)) * size * .82 * end, math.sin(math.radians(tilt)) * size * .82 * end)
            s.box("mallet_band", "arm_r", (top[0] + off[0], top[1] + off[1], top[2]), (size * .26, size * 1.06, size * 1.06), "wood_dark", (0, 0, tilt))
    s.bone("tail", "body", (0, by - .6 * k, 3.6 * k))
    s.ball("cotton_tail", "tail", (0, by - .4 * k, 4.2 * k), (2.6 * k, 2.6 * k, 2.4 * k), "mochi")
    if s.age > 0:
        s.box("ribbon_scarf", "head", (0, hy - 3.25 * h, hz + .1 * h), (5.2 * h, .9 * h, 4.6 * h), "ribbon")
        s.box("ribbon_knot", "head", (1.6 * h, hy - 3.4 * h, hz - 2.4 * h), (1.1 * h, 1.1 * h, .5 * h), "ribbon", (0, 0, 45))
    if s.age == 2:
        s.crescent("crescent_moon", "head", (-4.6 * h, hy + 3.6 * h, hz + 1.2 * h), 2.4 * h, 1.5 * h, .8, "moon_glow", segments=6, opening=-20)
        s.box("moon_star", "head", (-6.6 * h, hy + 6.2 * h, hz + 1.2 * h), (.8 * h, .8 * h, .4), "moon_glow", (0, 0, 45))
    return s.finish()


def _hedgehog(stage):
    """A round hedgehog whose spines end in pink petals. Flowers grow across its back as it
    ages; the adult wears a whole blossom crown."""
    s = Sculpt(stage, _palette(spine="#8A6450", spine_dark="#62463A", spine_tip="#F4B6CF", face="#F3DFC6",
                               belly="#FFF1DE", nose="#3A2A2A", petal="#FFC0D9", petal_white="#FFF4F9",
                               petal_core="#FFD45A", leaf="#79B866", eye="#2B1E2A", pupil="#2B1E2A"))
    k, h = s.scale, s.head_scale
    leg = 1.7
    by = leg * k + 3.4 * k
    s.bones["body"] = ("root", [0, by, 0])
    body = (9.8 * k, 7.2 * k, 11.4 * k)
    bc, br = (0, by - .6 * k, .4 * k), (body[0] * .5, body[1] * .6, body[2] * .5)
    _ellipsoid(s, "round_body", "body", bc, br, "spine_dark", layers=6, cut=-.55)
    s.ball("soft_belly", "body", (0, by - 1.6 * k, -.4 * k), (7.2 * k, 3.4 * k, 9.6 * k), "belly")
    # Quills lie back over the dome in staggered rows like brushed fur, each tipped petal-pink.
    rows, cols = (4, 5, 6)[s.age], (5, 7, 9)[s.age]
    for r in range(rows):
        v = r / (rows - 1)
        for c in range(cols):
            u = ((c + (.5 if r % 2 else 0)) / (cols - .5)) * 2 - 1
            ang = u * 1.35
            z = bc[2] - br[2] * .62 + v * br[2] * 1.45
            ring = math.sqrt(max(.15, 1 - ((z - bc[2]) / br[2]) ** 2))
            x = bc[0] + math.sin(ang) * br[0] * ring * .92
            y = bc[1] + math.cos(ang) * br[1] * ring * .92
            nx, ny = math.sin(ang), math.cos(ang)
            length = (3.0 + .8 * ((r + c) % 2)) * k
            tip = (x + nx * length * .5, y + ny * length * .45, z + length * .8)
            s.beam("back_quill", "body", (x, y, z), tip, 1.15 * k, "spine" if (r + c) % 2 else "spine_dark", .8 * k)
            s.box("petal_quill_tip", "body", tip, (.9 * k, .9 * k, .9 * k), "spine_tip", (45, 0, 45))
    hy, hz = by - .2 * k + .6 * h, -5.3 * k - .5 * h
    s.bone("head", "body", (0, hy - .6 * h, hz + 2 * h))
    face = (6.0 * h, 5.0 * h, 4.6 * h)
    s.ball("hedgehog_face", "head", (0, hy, hz), face, "face")
    s.box("snout", "head", (0, hy - .9 * h, hz - 2.9 * h), (2.6 * h, 2.0 * h, 2.2 * h), "face")
    s.box("snout_tip", "head", (0, hy - .85 * h, hz - 4.1 * h), (1.7 * h, 1.4 * h, .9 * h), "face")
    s.ball("button_nose", "head", (0, hy - .55 * h, hz - 4.7 * h), (1.25 * h, 1.05 * h, .8 * h), "nose")
    _dot_eyes(s, "head", 1.45 * h, hy + .55 * h, _front(face, (0, hy, hz), 1.45 * h, hy + .55 * h), 1.05 * h)
    for side in (-1, 1):
        s.ball("round_ear", "head", (side * 2.4 * h, hy + 2.3 * h, hz + .5 * h), (1.7 * h, 1.6 * h, .9 * h), "face")
        s.box("inner_ear", "head", (side * 2.4 * h, hy + 2.3 * h, hz + .02 * h), (.9 * h, .85 * h, .12), "spine_tip")
    for side in (-1, 1):
        for end, zz in (("f", -1), ("b", 1)):
            x, z = side * 3.0 * k, zz * 3.4 * k + .4 * k
            bone = s.bone("leg_" + end + ("l" if side < 0 else "r"), "body", (x, leg * k + .8 * k, z))
            s.box("stubby_leg", bone, (x, leg * k * .55 + .2 * k, z), (1.7 * k, leg * k + .6 * k, 1.8 * k), "spine_dark")
            s.box("tiny_foot", bone, (x, .35 * k, z - .5 * k), (1.8 * k, .7 * k, 2.3 * k), "face")
    s.bone("tail", "body", (0, by - .4 * k, 6 * k))
    s.box("stub_tail", "tail", (0, by - .4 * k, 6.1 * k), (1.4 * k, 1.2 * k, 1.4 * k), "spine")
    crown = [(0, .78, -.15)] if s.age == 0 else [(-.32, .7, 0), (.34, .68, .25), (0, .8, -.2)] if s.age == 1 else \
            [(-.38, .66, -.15), (.4, .66, -.05), (0, .83, -.32), (-.22, .74, .38), (.25, .72, .42)]
    for i, (u, v, w) in enumerate(crown):
        center = (u * body[0], by + v * body[1] + .9 * k, w * body[2] * .5 + .4 * k)
        r = ((2.6, 1.9, 1.7)[s.age]) * k * (1.35 if s.age == 0 else 1)
        _flower(s, "body", center, r, "petal" if i % 2 == 0 else "petal_white", "petal_core", facing="up", spin=i * 20)
        s.box("flower_leaf", "body", (center[0] + r * .7, center[1] - .25 * k, center[2] + r * .4), (r * .9, .25, r * .5),
              "leaf", (0, -30 - i * 25, 0))
    if s.age == 0:
        s.box("head_leaf", "head", (0, hy + 2.75 * h, hz + .3 * h), (1.9 * h, .3 * h, 1.1 * h), "leaf", (0, 30, 0))
    return s.finish()


def _axolotl(stage):
    """A pink axolotl with branching coral for gills and a wide smile; the adult wears a coral
    crown and a pearl, trailing little bubbles."""
    s = Sculpt(stage, _palette(skin="#F7A9C4", skin_light="#FFCFE0", belly="#FFE5EE", freckle="#E883AE", coral="#FF6B6E",
                               coral_orange="#FF9F4A", coral_tip="#FFD2A3", fin="#FBC6DA", eye="#2A1A2A",
                               pupil="#2A1A2A", mouth="#B5536F", pearl="#F7FCFF", bubble_glow="#C2F7FF"))
    k, h = s.scale, s.head_scale
    leg = 1.8
    by = leg * k + 1.9 * k
    s.bones["body"] = ("root", [0, by, 0])
    s.ball("long_body", "body", (0, by, .6 * k), (6.4 * k, 3.8 * k, 10.4 * k), "skin")
    s.ball("pale_belly", "body", (0, by - 1.2 * k, .3 * k), (5.4 * k, 1.6 * k, 9.4 * k), "belly")
    for i in range(2 + s.age * 2):
        a = i * 2.39996
        s.box("freckle", "body", (math.cos(a) * 2.2 * k, by + 1.92 * k, (i - 1.5) * 1.8 * k), (.8 * k, .1, .8 * k), "freckle", (0, 45, 0))
    hy, hz = by + .9 * k + .3 * h, -4.6 * k - 1.6 * h
    s.bone("head", "body", (0, hy - .6 * h, hz + 2.4 * h))
    head = (8.6 * h, 4.4 * h, 5.6 * h)
    s.ball("wide_head", "head", (0, hy, hz), head, "skin")
    fz = hz - head[2] * .5
    for side in (-1, 1):
        ex = side * 3.0 * h
        s.box("bead_eye", "head", (ex, hy + .8 * h, hz - 2.1 * h), (1.2 * h, 1.3 * h, 1.2 * h), "eye")
        s.box("bead_eye_shine", "head", (ex - side * .2 * h, hy + 1.15 * h, hz - 2.75 * h), (.45 * h, .45 * h, .1), "shine")
        s.box("cheek_blush", "head", (side * 2.9 * h, hy - .5 * h, fz + .3), (1.4 * h, .5 * h, .12), "blush")
    # A wide content smile across the front of the face.
    s.box("smile_line", "head", (0, hy - .55 * h, fz - .05), (3.6 * h, .22 * h, .1), "mouth")
    for side in (-1, 1):
        s.box("smile_corner", "head", (side * 2.0 * h, hy - .3 * h, fz - .05), (.7 * h, .22 * h, .1), "mouth", (0, 0, side * 35))
    branches = (2, 3, 3)[s.age]
    for side in (-1, 1):
        for j in range(branches):
            ang = math.radians(-35 + j * 35 if branches == 3 else -20 + j * 40)
            root = (side * 3.6 * h, hy + .4 * h + j * .25 * h, hz + 1.2 * h)
            length = (2.4, 3.2, 4.0)[s.age] * h
            tip = (root[0] + side * math.cos(ang) * length * .75, root[1] + math.sin(ang) * length * .6 + length * .45, root[2] + length * .45)
            s.beam("coral_gill", "head", root, tip, .8 * h, "coral" if j % 2 == 0 else "coral_orange")
            for t, twig in ((.55, 1), (.8, -1)):
                mid = tuple(root[a] + (tip[a] - root[a]) * t for a in range(3))
                end = (mid[0] + side * .5 * h, mid[1] + twig * .2 * h + .9 * h, mid[2] + (.9 if twig > 0 else -.3) * h)
                s.beam("coral_twig", "head", mid, end, .5 * h, "coral_orange" if j % 2 == 0 else "coral")
                s.box("coral_polyp", "head", end, (.7 * h, .7 * h, .7 * h), "coral_tip", (45, 0, 45))
            s.box("coral_polyp", "head", tip, (.85 * h, .85 * h, .85 * h), "coral_tip", (45, 0, 45))
    for side in (-1, 1):
        for end, zz in (("f", -1), ("b", 1)):
            x, z = side * 3.2 * k, zz * 3.4 * k + .5 * k
            bone = s.bone("leg_" + end + ("l" if side < 0 else "r"), "body", (x, by - .6 * k, z))
            foot = (side * 4.6 * k, .35 * k, z - .4 * k)
            s.beam("splayed_leg", bone, (x, by - .6 * k, z), foot, 1.25 * k, "skin_light")
            for toe in range(3):
                s.box("little_finger", bone, (foot[0] + side * .3 * k + (toe - 1) * .45 * k, .2 * k, foot[2] - .9 * k),
                      (.35 * k, .3 * k, 1.1 * k), "skin_light", (0, (toe - 1) * -25, 0))
    s.bone("tail", "body", (0, by + .2 * k, 5.4 * k))
    pts = [(0, by + .2 * k, 5.2 * k), (.5 * k, by + .3 * k, 8.4 * k), (1.3 * k, by + .6 * k, 11.2 * k)]
    s.bone("tail_tip", "tail", pts[1])
    s.beam("flat_tail", "tail", pts[0], pts[1], 1.9 * k, "skin", 3.2 * k)
    s.beam("flat_tail", "tail_tip", pts[1], pts[2], 1.3 * k, "skin", 2.6 * k)
    for i, (a, b) in enumerate(((pts[0], pts[1]), (pts[1], pts[2]))):
        bone = "tail" if i == 0 else "tail_tip"
        s.beam("tail_fin", bone, (a[0], a[1] + 1.4 * k, a[2]), (b[0], b[1] + (1.1 if i == 0 else .7) * k, b[2]), .4 * k, "fin", 1.8 * k)
        s.beam("tail_fin_low", bone, (a[0], a[1] - 1.2 * k, a[2]), (b[0], b[1] - .8 * k, b[2]), .4 * k, "fin", 1.2 * k)
    for i in range(3 + s.age):
        s.box("dorsal_fin", "body", (0, by + 2.1 * k, (-2.2 + i * 1.7) * k), (.35 * k, (1.0 + .2 * (i % 2)) * k, 1.7 * k), "fin")
    if s.age == 2:
        for i, x in enumerate((-1.5, 0, 1.5)):
            s.beam("coral_crown", "head", (x * h, hy + 1.8 * h, hz + .2 * h), (x * 1.3 * h, hy + (3.4 + (i == 1)) * h, hz + .6 * h),
                   .55 * h, "coral" if i == 1 else "coral_orange")
            s.box("crown_polyp", "head", (x * 1.3 * h, hy + (3.6 + (i == 1)) * h, hz + .6 * h), (.75 * h, .75 * h, .75 * h), "coral_tip", (45, 0, 45))
        s.ball("pearl", "head", (0, hy - 2.1 * h, hz - .6 * h), (1.3 * h, 1.3 * h, 1.3 * h), "pearl")
        for i, (x, y, z) in enumerate(((2.2, 5.6, -1.4), (3.4, 7.4, -.6), (2.6, 9.0, .3))):
            s.box("bubble", "head", (x * h, hy + (y - 3.6) * h, hz + z * h), ((.9 - .2 * i) * h,) * 3, "bubble_glow")
    return s.finish()


def _bumble(stage):
    """A round bumblebee in a clover hat, always hovering on glassy wings; the queen grows a
    tiny gold crown and pollen baskets."""
    s = Sculpt(stage, _palette(fuzz="#FFCB45", fuzz_light="#FFE6A0", stripe="#3B2C35", wing="#CDEEFF", wing_edge="#FFFFFF",
                               clover="#58B65E", clover_light="#8ED989", eye="#22161E", pupil="#22161E",
                               pollen="#FFA43A", stinger="#3B2C35", blush="#FF9EB8"))
    k, h = s.scale, s.head_scale
    by = 5.6 * k + 2.4
    s.bones["body"] = ("root", [0, by, 0])
    s.ball("fuzzy_thorax", "body", (0, by + .3 * k, .4 * k), (5.6 * k, 5.4 * k, 5.2 * k), "fuzz_light")
    abdomen = (6.8 * k, 6.2 * k, 7.6 * k)
    ac = (0, by - .4 * k, 4.6 * k)
    s.ball("round_abdomen", "body", ac, abdomen, "fuzz")
    for i, z in enumerate((-.12, .18, .42)):
        if s.age == 0 and i == 2:
            continue
        scale = 1 - abs(z) * .5
        s.box("dark_stripe", "body", (0, ac[1], ac[2] + z * abdomen[2]), (abdomen[0] * scale * 1.02, abdomen[1] * scale * 1.02, .9 * k), "stripe")
    s.bone("tail", "body", (0, ac[1], ac[2] + abdomen[2] * .45))
    s.beam("little_stinger", "tail", (0, ac[1] - .2 * k, ac[2] + abdomen[2] * .44), (0, ac[1] - .7 * k, ac[2] + abdomen[2] * .44 + 1.6 * k), .6 * k, "stinger")
    hy, hz = by + 1.4 * k + 1.6 * h, -2.3 * k - .8 * h
    s.bone("head", "body", (0, hy - 2.4 * h, hz + 2 * h))
    head = (8.0 * h, 7.2 * h, 7.0 * h)
    s.ball("round_bee_head", "head", (0, hy, hz), head, "fuzz")
    s.box("fuzzy_crown", "head", (0, hy + head[1] * .5 + .3 * h, hz), (head[0] * .5, .7 * h, head[2] * .48), "fuzz")
    s.ball("fluffy_collar", "head", (0, hy - 3.1 * h, hz + 1.8 * h), (6.4 * h, 2.2 * h, 5.0 * h), "fuzz_light")
    ey = hy + .1 * h
    _dot_eyes(s, "head", 1.85 * h, ey, _front(head, (0, hy, hz), 1.85 * h, ey), (1.9, 1.75, 1.6)[s.age] * h)
    _smile(s, "head", ey - 2.1 * h, _front(head, (0, hy, hz), 0, ey - 2.1 * h), 1.1 * h, "stripe")
    for side in (-1, 1):
        root = (side * 1.4 * h, hy + 3.2 * h, hz - .4 * h)
        mid = (side * 2.3 * h, hy + 5.0 * h, hz - 1.0 * h)
        end = (side * 3.1 * h, hy + 5.6 * h, hz - .4 * h)
        _chain(s, "antenna", "head", [root, mid, end], .38 * h, "stripe")
        s.ball("antenna_tip", "head", end, (1.0 * h, 1.0 * h, 1.0 * h), "stripe")
    top = hy + head[1] * .5 + .65 * h
    leaves = 2 if s.age == 0 else 4
    for i in range(leaves):
        a = math.radians(45 + i * (180 if leaves == 2 else 90))
        r = (1.9, 2.0, 2.3)[s.age] * h
        for lobe in (-1, 1):
            la = a + lobe * .38
            s.box("clover_leaf", "head", (math.cos(la) * r, top + .3 * h + i * .03, hz + math.sin(la) * r),
                  (r * 1.1, .35 * h, r * .95), "clover" if i % 2 == 0 else "clover_light", (0, -math.degrees(la), -14))
    s.beam("clover_stem", "head", (0, top + .2 * h, hz), (.3 * h, top + 1.3 * h, hz + .9 * h), .3 * h, "clover")
    for side, name in ((-1, "wing_l"), (1, "wing_r")):
        root = (side * 1.6 * k, by + 2.5 * k, 1.0 * k)
        s.bone(name, "body", root)
        span = (5.6, 7.4, 9.2)[s.age]
        # Glassy wings raised in a V behind the head, readable from the front.
        for lobe, (size, lift, back, tilt) in enumerate(((1.0, 3.2, 1.0, 34), (.66, 1.4, 2.4, 58))):
            w, l = span * .5 * size, span * .82 * size
            c = (side * (1.4 * k + w * .42), by + 2.4 * k + lift * k, root[2] + back * k)
            rot = (0, side * -12, side * tilt)
            s.box("glass_wing", name, c, (w, l, .25), "wing", rot, c)
            s.box("glass_wing_round", name, c, (w * 1.18, l * .7, .2), "wing", rot, c)
            s.box("wing_edge_glint", name, (c[0], c[1], c[2] - .2), (w * .2, l * .76, .1), "wing_edge", rot, c)
    for side in (-1, 1):
        leg = s.bone("leg_l" if side < 0 else "leg_r", "body", (side * 1.4 * k, by - 1.8 * k, .2 * k))
        for j in range(3):
            z = (-.8 + j * 1.1) * k
            s.beam("dangling_leg", leg, (side * 1.4 * k, by - 1.6 * k, z), (side * 2.0 * k, by - 3.6 * k, z - .3 * k), .55 * k, "stripe")
            if s.age == 2 and j == 2:
                s.ball("pollen_basket", leg, (side * 2.1 * k, by - 3.5 * k, z - .3 * k), (1.4 * k, 1.4 * k, 1.4 * k), "pollen")
    if s.age == 2:
        cx = 1.8 * h
        s.box("queen_crown_band", "head", (-cx, top + .5 * h, hz - .8 * h), (2.6 * h, .7 * h, 2.6 * h), "gold", (0, 20, 12))
        for j in range(3):
            s.box("queen_crown_point", "head", (-cx + (j - 1) * .85 * h, top + 1.25 * h, hz - 1.95 * h), (.5 * h, .9 * h, .3 * h),
                  "gold_light", (0, 20, 12 + (j - 1) * 12))
    return s.finish()


def _penguin(stage):
    """A penguin of the aurora: a grey fluffy chick, a scarfed youngster, and a gold-cheeked
    emperor crowned with aurora plumes and an aurora ribbon."""
    s = Sculpt(stage, _palette(back="#283255", back_light="#394674", belly="#F4F7FF", beak="#F2A03D", feet="#F2A03D",
                               cheek="#FFCF66", scarf="#E8476B", scarf_light="#FF7D96", eye="#1D1A2A",
                               pupil="#1D1A2A", mint_glow="#7EF3C9", cyan_glow="#64D7F6", violet_glow="#A38EFF"))
    if s.age == 0:
        s.palette.update(back="#AAB3C6", back_light="#C9D0DE")
    k, h = s.scale, s.head_scale
    by = 1.4 * k + 5.0 * k
    s.bones["body"] = ("root", [0, by, 0])
    body = (8.6 * k, 10.2 * k, 7.8 * k)
    s.ball("egg_body", "body", (0, by, 0), body, "back")
    s.ball("white_belly", "body", (0, by - .6 * k, -2.5 * k), (6.6 * k, 8.4 * k, 3.2 * k), "belly")
    if s.age == 0:
        for i in range(5):
            a = i * 1.26
            s.box("down_tuft", "body", (math.cos(a) * 3.8 * k, by + (i - 2) * 1.4 * k, math.sin(a) * 3.4 * k + .6 * k),
                  (1.4 * k, 1.0 * k, 1.4 * k), "back_light", (0, a * 40, 20))
    hy = by + 4.4 * k + 2.4 * h
    hz = -.6 * k
    s.bone("head", "body", (0, hy - 2.6 * h, hz))
    head = (7.0 * h, 6.2 * h, 6.4 * h)
    s.ball("round_head", "head", (0, hy, hz), head, "back" if s.age else "belly")
    fz = _front(head, (0, hy, hz), 0, hy)
    if s.age == 0:
        s.ball("chick_cap", "head", (0, hy + 1.9 * h, hz + .4 * h), (7.2 * h, 2.6 * h, 6.6 * h), "eye")
        s.box("head_fluff", "head", (0, hy + 3.4 * h, hz), (1.2 * h, 1.6 * h, 1.2 * h), "back_light", (0, 45, 20))
    else:
        s.disc("white_face_mask", "head", (0, hy - .3 * h, fz + .25), 5.0 * h, 3.6 * h, .4, "belly")
    s.eyes("head", 1.55 * h, hy + .25 * h, fz - .05, .72 * h, gentle=s.age == 0)
    s.box("upper_beak", "head", (0, hy - 1.05 * h, fz - .75 * h), (1.6 * h, .8 * h, 1.7 * h), "beak")
    s.box("beak_tip", "head", (0, hy - 1.35 * h, fz - 1.55 * h), (.9 * h, .55 * h, .7 * h), "beak")
    if s.age == 2:
        for side in (-1, 1):
            s.box("emperor_cheek", "head", (side * 3.35 * h, hy - .6 * h, hz - .3 * h), (.5 * h, 2.4 * h, 2.4 * h), "cheek", (0, 0, side * 10))
            s.box("cheek_glow_line", "body", (side * 2.9 * k, by + 3.2 * k, -3.2 * k), (.4 * k, 2.6 * k, .4 * k), "cheek", (20, 0, -side * 12))
    for side, name in ((-1, "wing_l"), (1, "wing_r")):
        root = (side * 4.0 * k, by + 2.6 * k, 0)
        s.bone(name, "body", root)
        s.box("flipper", name, (side * 4.6 * k, by - .3 * k, .2 * k), (1.1 * k, 6.4 * k, 2.8 * k), "back", (0, 0, side * 14))
        s.box("flipper_edge", name, (side * 4.95 * k, by - .4 * k, -1.2 * k), (.6 * k, 5.6 * k, .3), "back_light", (0, 0, side * 14))
    for side in (-1, 1):
        leg = s.bone("leg_l" if side < 0 else "leg_r", "body", (side * 1.9 * k, 1.6 * k, -.4 * k))
        s.box("orange_foot", leg, (side * 1.9 * k, .45 * k, -1.4 * k), (2.6 * k, .9 * k, 3.4 * k), "feet")
        for toe in (-1, 1):
            s.box("toe_split", leg, (side * 1.9 * k + toe * .6 * k, .55 * k, -3.15 * k), (.14, .7 * k, .3 * k), "beak")
    s.bone("tail", "body", (0, by - 3.6 * k, 3.4 * k))
    s.box("wedge_tail", "tail", (0, by - 4.0 * k, 4.2 * k), (2.6 * k, .9 * k, 2.4 * k), "back", (-25, 0, 0))
    if s.age > 0:
        s.box("knit_scarf", "head", (0, hy - 3.1 * h, hz + .2 * h), (6.4 * h, 1.3 * h, 6.2 * h), "scarf")
        s.box("scarf_stripe", "head", (0, hy - 3.1 * h, hz - 2.9 * h), (6.5 * h, .4 * h, .3), "scarf_light")
    if s.age == 1:
        s.box("scarf_tail", "head", (2.6 * h, hy - 4.6 * h, hz - 2.4 * h), (1.6 * h, 3.0 * h, .5 * h), "scarf", (0, 0, -12))
    if s.age == 2:
        # Aurora ribbon streaming behind, and three plumes of light on the crown.
        pts = [(2.4 * h, hy - 3.4 * h, hz + 2.8 * h), (3.8 * k, by + 4.6 * k, 5.4 * k), (4.4 * k, by + 4.0 * k, 9.0 * k),
               (4.0 * k, by + 5.2 * k, 12.2 * k), (4.6 * k, by + 6.6 * k, 14.6 * k)]
        _chain(s, "aurora_ribbon", "head", pts, [1.2, 1.1, .95, .8], ["mint_glow", "cyan_glow", "violet_glow", "mint_glow"], depths=[.4, .4, .4, .4])
        for i, (mat, lean) in enumerate((("mint_glow", -22), ("cyan_glow", 0), ("violet_glow", 22))):
            root = ((i - 1) * 1.1 * h, hy + 2.8 * h, hz + .6 * h)
            tip = ((i - 1) * 2.2 * h, hy + (6.0 - abs(i - 1) * .7) * h, hz + 2.8 * h)
            s.beam("aurora_plume", "head", root, tip, .6 * h, mat)
            s.box("plume_spark", "head", tip, (.8 * h, .8 * h, .8 * h), mat, (45, 0, 45))
    return s.finish()


# ---------------------------------------------------------------------------
# Fierce


def _raptor(stage):
    """A feathered storm raptor: a fuzzy big-eyed chick, then a lean hunter with lightning
    stripes, sickle claws and a crackling zigzag crest."""
    s = Sculpt(stage, _palette(hide="#3E5272", hide_light="#5F7698", feather="#3F7FD6", feather_light="#6FA9F0",
                               belly="#C9D6E7", stripe_glow="#82ECFF", claw="#ECE6D6", tooth="#F4EEDD",
                               eye="#FFD23F", mouth="#3B1F2A"))
    k, h = s.scale, s.head_scale
    hip_y = 6.4 * k
    by = hip_y + 1.2 * k
    s.bones["body"] = ("root", [0, by, 0])
    s.mass("lean_torso", "body", (0, by + .6 * k, -.6 * k), (5.4 * k, 5.8 * k, 9.2 * k), "hide")
    s.mass("pale_chest", "body", (0, by - .4 * k, -3.6 * k), (4.0 * k, 4.4 * k, 2.6 * k), "belly")
    s.mass("feather_mantle", "body", (0, by + 3.2 * k, -.8 * k), (4.6 * k, 1.2 * k, 7.6 * k), "feather")
    for side in (-1, 1):
        for i in range(2 + s.age):
            z = (-2.4 + i * 2.2) * k
            pts = [(side * 2.74 * k, by + 1.8 * k, z), (side * 2.76 * k, by + .7 * k, z + .7 * k), (side * 2.76 * k, by - .2 * k, z + .1 * k)]
            for a, b in zip(pts, pts[1:]):
                s.beam("lightning_stripe", "body", a, b, .4 * k, "stripe_glow", .1)
    neck_base = (0, by + 2.0 * k, -4.4 * k)
    hy, hz = by + 4.6 * k + .8 * h, -6.6 * k - .6 * h
    s.beam("feathered_neck", "body", neck_base, (0, hy - 1.2 * h, hz + 1.6 * h), 3.2 * k, "hide", 3.4 * k)
    s.bone("head", "body", (0, hy - 1.2 * h, hz + 1.6 * h))
    snout = (2.0, 3.2, 4.2)[s.age] * h
    s.mass("raptor_skull", "head", (0, hy, hz), (4.6 * h, 4.2 * h, 4.6 * h), "hide")
    s.box("long_snout", "head", (0, hy - .5 * h, hz - 2.3 * h - snout * .5), (3.2 * h, 2.4 * h, snout), "hide")
    s.box("lower_jaw", "head", (0, hy - 1.95 * h, hz - 2.1 * h - snout * .45), (2.8 * h, .9 * h, snout * .9), "belly")
    s.box("mouth_line", "head", (0, hy - 1.45 * h, hz - 2.3 * h - snout * .5), (3.25 * h, .2 * h, snout * .96), "mouth")
    if s.age:
        for i in range(2 + s.age):
            for side in (-1, 1):
                s.box("raptor_tooth", "head", (side * 1.35 * h, hy - 1.7 * h, hz - 2.6 * h - i * snout / (2 + s.age)),
                      (.3 * h, (.45 + .15 * s.age) * h, .3 * h), "tooth")
    s.eyes("head", 1.55 * h, hy + .55 * h, hz - 2.3 * h, .78 * h, gentle=s.age == 0)
    for side in (-1, 1):
        if s.age:
            s.box("hunter_brow", "head", (side * 1.5 * h, hy + 1.65 * h, hz - 2.35 * h), (1.8 * h, .45 * h, .5 * h), "hide_light", (0, 0, side * 14))
        s.box("nostril", "head", (side * .8 * h, hy + .35 * h, hz - 2.35 * h - snout + .1), (.35 * h, .3 * h, .2), "mouth")
    crest = (2, 4, 5)[s.age]
    for i in range(crest):
        z = hz - 1.0 * h + i * 1.25 * h
        y = hy + 2.2 * h + (1.2 if i % 2 == 0 else .2) * h * (1 + .3 * s.age)
        s.box("zigzag_crest", "head", (0, y, z), (.5 * h, 2.2 * h * (1 + .25 * s.age), 1.0 * h), "stripe_glow" if s.age else "feather_light",
              (35 if i % 2 == 0 else -35, 0, 0))
    if s.age == 0:
        s.box("chick_fluff", "head", (0, hy + 2.4 * h, hz + .4 * h), (2.2 * h, 1.4 * h, 2.2 * h), "feather_light", (0, 45, 0))
    for side in (-1, 1):
        arm = s.bone("arm_l" if side < 0 else "arm_r", "body", (side * 2.4 * k, by + 1.0 * k, -3.4 * k))
        hand = (side * 3.0 * k, by - 1.6 * k, -4.6 * k)
        s.beam("feathered_arm", arm, (side * 2.4 * k, by + 1.0 * k, -3.4 * k), hand, 1.2 * k, "hide")
        for j in range(2 + s.age):
            s.box("arm_feather", arm, (side * 3.1 * k, by - (.3 + j * .7) * k, (-3.0 + j * .9) * k), (.3 * k, 1.0 * k, 3.4 * k),
                  "feather" if j % 2 else "feather_light", (-20, 0, side * 8))
        for c in (-1, 1):
            s.box("hand_claw", arm, (hand[0] + c * .3 * k, hand[1] - .5 * k, hand[2] - .4 * k), (.25 * k, .9 * k, .25 * k), "claw", (-30, 0, 0))
    for side in (-1, 1):
        x = side * 2.0 * k
        hip = (x, hip_y + 1.0 * k, 1.2 * k)
        knee = (x * 1.05, 3.6 * k, -.8 * k)
        ankle = (x * 1.08, 1.4 * k, 1.6 * k)
        leg = s.bone("leg_l" if side < 0 else "leg_r", "body", hip)
        s.mass("powerful_thigh", leg, (x, hip_y * .82, .4 * k), (2.6 * k, 4.4 * k, 3.8 * k), "hide")
        lower = s.bone(("leg_l" if side < 0 else "leg_r") + "_lower", leg, knee)
        s.beam("raptor_shin", lower, knee, ankle, 1.3 * k, "hide_light")
        s.beam("raptor_foot", lower, ankle, (x * 1.08, .4 * k, -.4 * k), 1.0 * k, "hide_light")
        for toe in (-1, 1):
            s.box("toe_claw", lower, (x + toe * .5 * k, .35 * k, -1.2 * k), (.4 * k, .5 * k, 1.4 * k), "claw")
        # The famous sickle claw is held up off the ground.
        s.box("sickle_claw", lower, (x * 1.02, 1.2 * k, -.9 * k), (.4 * k, (.8 + .5 * s.age) * k, 1.6 * k), "claw", (-50, 0, 0))
    s.bone("tail", "body", (0, by + 1.0 * k, 3.8 * k))
    pts = [(0, by + 1.0 * k, 3.6 * k), (0, by + 1.4 * k, 7.0 * k), (.4 * k, by + 1.9 * k, 10.2 * k)]
    s.bone("tail_tip", "tail", pts[2])
    _chain(s, "counterweight_tail", "tail", pts, [2.6 * k, 1.9 * k], "hide")
    tail_end = (.9 * k, by + 2.3 * k, 13.6 * k)
    s.beam("counterweight_tail", "tail_tip", pts[2], tail_end, 1.3 * k, "hide")
    for j in range(3 + s.age):
        s.box("tail_feather_fan", "tail_tip", (tail_end[0] + (j - (2 + s.age) / 2) * .9 * k, tail_end[1], tail_end[2] + .6 * k),
              (.5 * k, .3 * k, (2.8 + .6 * s.age) * k), "feather" if j % 2 else "feather_light", (0, (j - (2 + s.age) / 2) * -14, 0))
    if s.age:
        for i in range(s.age + 1):
            z = (5.2 + i * 2.6) * k
            s.box("tail_lightning_band", "tail" if z < 10 * k else "tail_tip", (0, by + 1.4 * k + i * .4 * k, z), (2.3 * k - i * .3 * k, .3 * k, .5 * k),
                  "stripe_glow", (0, 0, 20 if i % 2 else -20))
    return s.finish()


def _rhino(stage):
    """A magma rhino in obsidian armour plates with lava glowing through every seam; the
    calf is soft with a single warm nub of a horn."""
    s = Sculpt(stage, _palette(hide="#685A60", hide_dark="#3A3036", plate="#352C33", horn="#4D3D63", horn_light="#8A74A8",
                               lava_glow="#FF7A2E", lava_core_glow="#FFD25C", eye="#FFB13D", hoof="#211C1F",
                               rock="#5D5357", mouth="#1C1719"))
    if s.age == 0:
        s.palette.update(hide="#76686E", plate="#5A4D54")
    k, h = s.scale, s.head_scale
    leg, length, width, height = 4.4, 14.0, 9.6, 8.2
    by = leg * k + height * k * .44
    s.bones["body"] = ("root", [0, by, 0])
    # Rounded like a boulder, its hide layered like cooled rock strata.
    _ellipsoid(s, "barrel_body", "body", (0, by, .4 * k), (width * .52 * k, height * .56 * k, length * .5 * k), "hide", layers=5)
    s.mass("shoulder_hump", "body", (0, by + 1.4 * k, -3.6 * k), (width * .92 * k, height * .78 * k, 5.4 * k), "hide")
    plates = (2, 3, 4)[s.age]
    for i in range(plates):
        z = (-3.6 + i * 9.4 / max(1, plates - 1)) * k if plates > 1 else 0
        s.mass("obsidian_back_plate", "body", (0, by + height * .5 * k + .2 * k, z), (width * .8 * k, 1.4 * k, 2.8 * k), "plate")
        if s.age:
            s.box("lava_seam", "body", (0, by + height * .5 * k + .1 * k, z + 1.55 * k), (width * .62 * k, .9 * k, .25 * k), "lava_glow")
        if s.age == 2:
            s.box("magma_vent", "body", (0, by + height * .5 * k + 1.1 * k, z), (1.0 * k, .6 * k, 1.0 * k), "lava_core_glow", (0, 45, 0))
    for side in (-1, 1):
        s.mass("shoulder_pauldron", "body", (side * width * .5 * k, by + 1.4 * k, -3.8 * k), (1.4 * k, 4.4 * k, 4.6 * k), "plate")
        for i in range(s.age * 2):
            z = (-1.4 + i * 2.1) * k
            pts = [(side * width * .505 * k, by + 1.8 * k, z), (side * width * .505 * k, by + .4 * k, z + .8 * k),
                   (side * width * .505 * k, by - 1.0 * k, z + .2 * k)]
            for a, b in zip(pts, pts[1:]):
                s.beam("lava_crack", "body", a, b, .35 * k, "lava_glow", .1)
    for side in (-1, 1):
        for end, zz in (("f", -1), ("b", 1)):
            x, z = side * width * .33 * k, zz * length * .3 * k + .4 * k
            bone = s.bone("leg_" + end + ("l" if side < 0 else "r"), "body", (x, leg * k + 1.0 * k, z))
            s.mass("pillar_leg", bone, (x, leg * k * .6, z), (3.1 * k, leg * k * 1.2, 3.2 * k), "hide")
            s.mass("rock_cuff", bone, (x, leg * k * .82, z), (3.5 * k, 1.1 * k, 3.6 * k), "rock")
            if s.age:
                s.box("leg_lava_vein", bone, (x, leg * k * .5, z - 1.62 * k), (.4 * k, leg * k * .5, .12), "lava_glow", (0, 0, 12))
            s.mass("heavy_hoof", bone, (x, .5 * k, z - .2 * k), (3.4 * k, 1.0 * k, 3.6 * k), "hoof")
            for toe in (-1, 0, 1):
                s.box("hoof_toe", bone, (x + toe * 1.05 * k, .45 * k, z - 1.95 * k), (.8 * k, .8 * k, .6 * k), "rock")
    hy, hz = by - .3 * k, -length * .5 * k - 1.9 * h
    s.bone("head", "body", (0, hy + .4 * h, hz + 2.6 * h))
    s.mass("heavy_head", "head", (0, hy, hz), (6.8 * h, 6.0 * h, 6.6 * h), "hide")
    s.mass("broad_snout", "head", (0, hy - .9 * h, hz - 3.8 * h), (5.4 * h, 4.0 * h, 2.4 * h), "hide")
    s.mass("cheek_plate", "head", (0, hy + 2.6 * h, hz + .6 * h), (5.6 * h, 1.2 * h, 4.4 * h), "plate")
    s.box("lip_line", "head", (0, hy - 2.1 * h, hz - 5.05 * h), (3.4 * h, .25 * h, .1), "mouth")
    for side in (-1, 1):
        s.box("nostril", "head", (side * 1.25 * h, hy - 1.0 * h, hz - 5.05 * h), (.6 * h, .5 * h, .1), "mouth")
        s.box("small_ear", "head", (side * 2.6 * h, hy + 3.0 * h, hz + 1.6 * h), (1.3 * h, 2.0 * h, .8 * h), "hide", (0, 0, -side * 20))
        ey, ez = hy + .9 * h, hz - 3.2 * h
        s.box("eye_socket", "head", (side * 2.2 * h, ey, ez + .05), (1.5 * h, 1.2 * h, .3), "hide_dark")
        s.box("ember_eye", "head", (side * 2.2 * h, ey, ez - .12), (1.0 * h, (.85 if s.age else 1.15) * h, .2), "eye")
        if s.age == 0:
            s.box("eye_shine", "head", (side * 2.0 * h, ey + .25 * h, ez - .25), (.35 * h, .35 * h, .1), "shine")
        else:
            s.box("glower_brow", "head", (side * 2.2 * h, ey + .85 * h, ez - .1), (1.8 * h, .45 * h, .5 * h), "plate", (0, 0, side * 14))
    horn = (1.4, 4.0, 6.8)[s.age] * h
    pts = [(0, hy + .8 * h, hz - 3.9 * h), (0, hy + .8 * h + horn * .55, hz - 4.6 * h), (0, hy + .8 * h + horn, hz - 4.0 * h - horn * .12)]
    widths = [2.9 * h, 1.8 * h]
    _chain(s, "nose_horn", "head", pts, widths, ["horn", "horn_light"])
    s.box("horn_tip", "head", pts[-1], (.8 * h, 1.2 * h, .8 * h), "horn_light")
    if s.age:
        s.box("horn_lava_core", "head", ((pts[0][0] + pts[1][0]) / 2, (pts[0][1] + pts[1][1]) / 2, (pts[0][2] + pts[1][2]) / 2 - 1.2 * h),
              (.5 * h, horn * .35, .15), "lava_glow")
        rear = [(0, hy + 2.6 * h, hz - 1.6 * h), (0, hy + 2.6 * h + horn * .42, hz - 1.9 * h)]
        s.beam("second_horn", "head", rear[0], rear[1], 1.4 * h, "horn")
    s.bone("tail", "body", (0, by + 1.4 * k, length * .5 * k + .4 * k))
    s.beam("stub_tail", "tail", (0, by + 1.4 * k, length * .5 * k + .4 * k), (0, by - .6 * k, length * .5 * k + 2.0 * k), .9 * k, "hide")
    s.box("ember_tuft", "tail", (0, by - 1.0 * k, length * .5 * k + 2.3 * k), (1.4 * k, 1.6 * k, 1.4 * k), "lava_glow" if s.age else "rock", (0, 45, 0))
    return s.finish()


def _scorpion(stage):
    """An eclipse scorpion of violet chitin. Its stinger burns amethyst; the adult's tail is
    crowned by a black sun with a golden corona."""
    s = Sculpt(stage, _palette(chitin="#2B2443", chitin_light="#4A3E6E", edge="#8573D4", amethyst_glow="#C98EFF",
                               corona_glow="#FFD27A", eye="#FF7FA6", under="#1D182D", claw_tip="#E7DEFF",
                               pupil="#140F20", eclipse="#120E1B"))
    k, h = s.scale, s.head_scale
    by = 2.6 * k + 1.2 * k
    s.bones["body"] = ("root", [0, by, 0])
    plates = 5
    for i in range(plates):
        z = (-.6 + i * 2.15) * k
        w = (6.6 - i * .35) * k
        s.mass("abdomen_plate", "body", (0, by, z), (w, 2.4 * k, 2.3 * k), "chitin" if i % 2 == 0 else "chitin_light")
        s.box("plate_rim", "body", (0, by + 1.15 * k, z + 1.0 * k), (w * .92, .3 * k, .3 * k), "edge")
        if s.age == 2 and i in (1, 3):
            s.box("amethyst_shard", "body", (0, by + 1.6 * k, z), (.9 * k, 1.6 * k, .9 * k), "amethyst_glow", (0, 45, 15))
    s.mass("pale_underside", "body", (0, by - 1.0 * k, 3.6 * k), (5.2 * k, .5 * k, 9.6 * k), "under")
    hy, hz = by + .2 * k, -3.0 * k
    s.bone("head", "body", (0, hy, hz + 1.5 * k))
    s.mass("cephalothorax", "head", (0, hy, hz), (6.4 * h, 2.8 * h, 5.0 * h), "chitin_light")
    front = hz - 2.5 * h
    if s.age == 0:
        _dot_eyes(s, "head", 1.5 * h, hy + .4 * h, front, 1.15 * h, pupil="pupil")
    else:
        for side in (-1, 1):
            s.box("main_eye", "head", (side * .9 * h, hy + 1.45 * h, hz - 1.6 * h), (.8 * h, .6 * h, .8 * h), "eye")
            for j in range(2):
                s.box("side_eye", "head", (side * (2.0 + .5 * j) * h, hy + 1.1 * h, front + (.4 + .5 * j) * h), (.45 * h, .4 * h, .45 * h), "eye")
    for side in (-1, 1):
        s.box("chelicera", "head", (side * .9 * h, hy - .6 * h, front - .5 * h), (1.0 * h, 1.0 * h, 1.2 * h), "edge")
    for side in (-1, 1):
        for i in range(4):
            bone = s.bone(("leg_f" if i < 2 else "leg_b") + ("l" if side < 0 else "r"), "body", (side * 2.6 * k, by, (-1.8 + i * 2.0) * k)) \
                if i in (0, 2) else (("leg_f" if i < 2 else "leg_b") + ("l" if side < 0 else "r"))
            z = (-1.8 + i * 2.1) * k
            hip = (side * 2.9 * k, by, z)
            knee = (side * 5.6 * k, by + 1.6 * k, z + (i - 1.5) * .5 * k)
            foot = (side * 7.2 * k, .2 * k, z + (i - 1.5) * 1.1 * k)
            s.beam("jointed_leg", bone, hip, knee, .75 * k, "chitin_light")
            s.beam("jointed_shin", bone, knee, foot, .55 * k, "chitin")
            s.box("leg_spike", bone, knee, (.6 * k, .6 * k, .6 * k), "edge", (45, 0, 45))
    claw = (1.8, 2.6, 3.4)[s.age] * h
    for side, name in ((-1, "arm_l"), (1, "arm_r")):
        shoulder = (side * 2.4 * k, hy, hz - 1.0 * k)
        s.bone(name, "body", shoulder)
        elbow = (side * 5.0 * k, hy + .8 * k, hz - 2.6 * k)
        wrist = (side * 4.2 * k, hy + .6 * k, hz - 5.6 * k)
        _chain(s, "pincer_arm", name, [shoulder, elbow, wrist], [1.2 * k, 1.0 * k], "chitin_light")
        c = (wrist[0], wrist[1], wrist[2] - claw * .4)
        s.mass("pincer_palm", name, c, (claw * .8, claw * .62, claw * .95), "chitin")
        s.box("pincer_fixed_finger", name, (c[0] + side * claw * .2, c[1], c[2] - claw * .78), (claw * .3, claw * .36, claw * .8), "edge", (0, side * 12, 0))
        s.box("pincer_moving_finger", name, (c[0] - side * claw * .22, c[1] + claw * .05, c[2] - claw * .72), (claw * .28, claw * .34, claw * .74), "chitin_light",
              (0, -side * 18, 0))
        s.box("pincer_tip", name, (c[0] + side * claw * .14, c[1], c[2] - claw * 1.2), (claw * .2, claw * .25, claw * .25), "claw_tip")
    curl = (.7, .85, 1.0)[s.age]
    pts = [(0, by + .4 * k, 9.4 * k), (0, by + (3.0 * curl) * k, 11.4 * k), (0, by + 6.6 * curl * k, 11.6 * k),
           (0, by + 9.0 * curl * k, 9.8 * k), (0, by + 9.8 * curl * k, 6.8 * k)]
    s.bone("tail", "body", pts[0])
    s.bone("tail_tip", "tail", pts[3])
    widths = [2.4 * k, 2.1 * k, 1.9 * k, 1.6 * k]
    _chain(s, "segmented_tail", ["tail", "tail", "tail", "tail_tip"], pts, widths, ["chitin", "chitin_light", "chitin", "chitin_light"])
    sting = (0, by + 9.0 * curl * k, 5.0 * k)
    s.ball("venom_bulb", "tail_tip", (0, pts[-1][1] - .2 * k, pts[-1][2] - .4 * k), (2.2 * k, 2.0 * k, 2.4 * k), "amethyst_glow" if s.age else "chitin_light")
    s.beam("barb", "tail_tip", (0, pts[-1][1] - .6 * k, pts[-1][2] - 1.4 * k), sting, .7 * k, "claw_tip")
    if s.age == 2:
        c = (0, pts[-1][1] + 1.4 * k, pts[-1][2] + 1.6 * k)
        s.disc("black_sun", "tail_tip", c, 4.0, 4.0, .4, "eclipse")
        for j in range(10):
            a = j * math.tau / 10
            s.box("golden_corona", "tail_tip", (c[0] + math.cos(a) * 2.6, c[1] + math.sin(a) * 2.6, c[2] + .1), (1.7, .5, .3), "corona_glow",
                  (0, 0, math.degrees(a) + 90))
            if j % 2 == 0:
                s.box("corona_flare", "tail_tip", (c[0] + math.cos(a) * 3.5, c[1] + math.sin(a) * 3.5, c[2] + .15), (.5, .9, .2), "corona_glow",
                      (0, 0, math.degrees(a) - 90))
    return s.finish()


def _tiger(stage):
    """The thunder god's white tiger: storm-cloud mane, dark stripes and a lightning-tipped
    tail. The adult carries Raijin's ring of drums arching over its back."""
    s = Sculpt(stage, _palette(fur="#F3F5FB", fur_shade="#D7DDEC", stripe="#27304A", stripe_glow="#6BE5FF", muzzle="#FFFFFF",
                               nose="#E79FB8", eye="#7DF4FF", cloud="#E4EBF8", cloud_dark="#9CA8C4", drum="#B8323F",
                               drum_skin="#F1E3C6", rope="#E04A5F", claw="#E8E4D8", mouth="#3A2F3E"))
    k, h = s.scale, s.head_scale
    leg = 6.4
    by = leg * k + 2.6 * k
    s.bones["body"] = ("root", [0, by, 0])
    s.mass("broad_ribcage", "body", (0, by + .4 * k, -1.4 * k), (7.6 * k, 6.8 * k, 9.6 * k), "fur")
    s.mass("muscular_flank", "body", (0, by + .3 * k, 3.6 * k), (6.6 * k, 5.8 * k, 6.0 * k), "fur")
    s.mass("pale_underbelly", "body", (0, by - 2.6 * k, .6 * k), (5.6 * k, 1.4 * k, 11 * k), "fur_shade")
    for side in (-1, 1):
        for i in range(3 + s.age):
            z = (-4.0 + i * 2.3) * k
            x = side * (3.82 if z < 1.2 * k else 3.32) * k
            s.box("tiger_stripe", "body", (x, by + 1.2 * k, z), (.12, 3.4 * k, .7 * k), "stripe", (-18 if i % 2 else 12, 0, 0))
            if s.age == 2 and i % 2 == 0:
                s.box("stripe_spark", "body", (x * 1.005, by + 2.1 * k, z - .5 * k), (.1, .9 * k, .3 * k), "stripe_glow", (-18, 0, 0))
        for i in range(2):
            s.box("back_stripe", "body", (side * 1.6 * k, by + 3.82 * k, (-2.6 + i * 3.8) * k), (2.2 * k, .12, .6 * k), "stripe", (0, side * 25, 0))
    for side in (-1, 1):
        for end, zz in (("f", -1), ("b", 1)):
            x, z = side * 2.5 * k, (-4.2 if zz < 0 else 4.4) * k
            hip = (x, leg * k + .8 * k, z)
            foot = (x * 1.06, 0, z + (-.3 if zz < 0 else .5) * k)
            bone, lower = _leg(s, "leg_" + end + ("l" if side < 0 else "r"), hip, foot, 2.5 * k, "fur", knee=(.5, (.4 if zz < 0 else -1.0) * k),
                               lower_width=1.9 * k, paw=(2.9 * k, 1.5 * k, 3.4 * k), paw_mat="fur")
            s.box("leg_stripe", lower, (x * 1.06, leg * k * .3, z - .1 * k), (2.0 * k, .4 * k, 2.0 * k), "stripe")
            for toe in (-1, 0, 1):
                s.box("claw", lower, (foot[0] + toe * .8 * k, .3 * k, foot[2] - 2.2 * k), (.3 * k, .4 * k, .5 * k), "claw")
    hy, hz = by + 2.2 * k + 1.0 * h, -6.6 * k - 1.2 * h
    s.bone("head", "body", (0, hy - 1.6 * h, hz + 2.4 * h))
    s.mass("broad_skull", "head", (0, hy, hz), (7.4 * h, 6.0 * h, 6.0 * h), "fur")
    front = hz - 3.0 * h
    s.mass("white_muzzle", "head", (0, hy - 1.6 * h, front - .6 * h), (4.4 * h, 2.4 * h, 2.0 * h), "muzzle")
    s.box("pink_nose", "head", (0, hy - .7 * h, front - 1.65 * h), (1.4 * h, .8 * h, .4), "nose")
    s.box("mouth_line", "head", (0, hy - 2.3 * h, front - 1.62 * h), (.2 * h, .9 * h, .1), "mouth")
    s.eyes("head", 1.95 * h, hy + .55 * h, front, .82 * h, gentle=s.age == 0)
    for side in (-1, 1):
        s.box("cheek_ruff", "head", (side * 3.9 * h, hy - .9 * h, hz - .8 * h), (2.2 * h, 2.6 * h, 3.4 * h), "muzzle", (0, side * 20, side * 22))
        s.box("round_ear", "head", (side * 2.7 * h, hy + 3.3 * h, hz + .8 * h), (2.2 * h, 2.0 * h, 1.2 * h), "stripe", (0, 0, -side * 12))
        s.box("ear_white_spot", "head", (side * 2.7 * h, hy + 3.3 * h, hz + .18 * h), (1.2 * h, 1.1 * h, .15), "muzzle", (0, 0, -side * 12))
        if s.age:
            s.box("fierce_brow", "head", (side * 1.95 * h, hy + 1.55 * h, front - .1), (1.9 * h, .4 * h, .4 * h), "stripe", (0, 0, side * 12))
        for j in range(2):
            s.box("cheek_stripe", "head", (side * 3.72 * h, hy + (.4 - j * 1.0) * h, hz - .8 * h), (.12, .4 * h, 2.6 * h), "stripe", (side * 15, 0, 0))
    # The 'king' mark on the brow: three bars and a stroke.
    for j in range(3):
        s.box("king_mark", "head", (0, hy + (2.0 + j * .55) * h, front + .2 * h), ((1.6 - .2 * (j == 1)) * h, .28 * h, .2), "stripe")
    s.box("king_mark_stroke", "head", (0, hy + 2.55 * h, front + .1 * h), (.28 * h, 1.4 * h, .2), "stripe")
    puffs = (1, 5, 8)[s.age]
    for i in range(puffs):
        if s.age == 0:
            s.ball("storm_puff", "head", (0, hy + 3.4 * h, hz + .6 * h), (3.0 * h, 2.0 * h, 2.6 * h), "cloud")
            break
        a = math.pi * (i / (puffs - 1)) if puffs > 1 else math.pi / 2
        x, y = math.cos(a) * 4.6 * h, math.sin(a) * 3.0 * h
        s.ball("cloud_mane", "head", (x, hy - 1.2 * h + y, hz + 2.6 * h), (3.4 * h, 3.0 * h, 3.0 * h), "cloud" if i % 2 else "cloud_dark")
    s.bone("tail", "body", (0, by + 1.6 * k, 6.4 * k))
    pts = [(0, by + 1.6 * k, 6.4 * k), (.5 * k, by + .8 * k, 9.4 * k), (1.6 * k, by + 1.6 * k, 12.0 * k), (2.4 * k, by + 4.0 * k, 13.0 * k)]
    _chain(s, "long_tail", "tail", pts, [1.6 * k, 1.4 * k, 1.2 * k], "fur")
    for i in range(2):
        mid = tuple((pts[i + 1][a] + pts[i][a]) / 2 for a in range(3))
        s.box("tail_ring", "tail", mid, (1.7 * k, 1.7 * k, .6 * k), "stripe")
    bolt = [pts[-1], (3.4 * k, by + 5.4 * k, 12.6 * k), (2.6 * k, by + 6.2 * k, 13.2 * k), (3.8 * k, by + 7.8 * k, 12.8 * k)]
    _chain(s, "lightning_tail_tip", "tail", bolt, .9 * k, "stripe_glow" if s.age else "cloud", joints=False)
    if s.age == 2:
        c = (0, by + 4.0, 1.6)
        r = 8.4
        for j in range(9):
            a0, a1 = math.radians(15 + j * 150 / 9), math.radians(15 + (j + 1) * 150 / 9)
            s.beam("drum_ring", "body", (math.cos(a0) * r, c[1] + math.sin(a0) * r, c[2]), (math.cos(a1) * r, c[1] + math.sin(a1) * r, c[2]), .55, "gold")
        for j in range(5):
            a = math.radians(25 + j * 130 / 4)
            p = (math.cos(a) * r, c[1] + math.sin(a) * r, c[2])
            s.mass("taiko_drum", "body", p, (2.3, 2.3, 1.7), "drum")
            s.disc("drum_skin", "body", (p[0], p[1], p[2] - .9), 1.9, 1.9, .2, "drum_skin")
            s.box("drum_tomoe", "body", (p[0], p[1], p[2] - 1.05), (.6, .6, .1), "stripe", (0, 0, 45))
            s.box("drum_spark", "body", (p[0] * 1.18, p[1] + (p[1] - c[1]) * .18, p[2]), (.4, .9, .4), "stripe_glow", (0, 0, 30))
    return s.finish()


def _beetle(stage):
    """A titan beetle in iridescent teal armour. Its wing cases open to fly; the adult carries
    a Hercules pair of gold-tipped horns and glowing rune lines."""
    s = Sculpt(stage, _palette(shell="#1D3B3C", shell_light="#2F6C67", sheen="#5CC6B8", horn="#1E1B1B", horn_tip="#E0B54C",
                               under="#2B2321", leg="#23302F", membrane="#CBB27C", eye="#F6D45C",
                               pupil="#120E0E", rune_glow="#7FFFD9"))
    k, h = s.scale, s.head_scale
    by = 1.5 * k + 2.0 * k
    s.bones["body"] = ("root", [0, by, 0])
    s.mass("under_body", "body", (0, by - .6 * k, 2.0 * k), (6.6 * k, 2.6 * k, 9.4 * k), "under")
    s.mass("pronotum_shield", "body", (0, by + .8 * k, -2.6 * k), (7.4 * k, 4.4 * k, 5.0 * k), "shell")
    s.box("pronotum_sheen", "body", (0, by + 3.05 * k, -2.8 * k), (4.6 * k, .1, 3.0 * k), "sheen")
    for side, name in ((-1, "wing_l"), (1, "wing_r")):
        s.bone(name, "body", (side * .2 * k, by + 2.3 * k, -.2 * k))
        c = (side * 1.95 * k, by + .9 * k, 3.6 * k)
        s.ball("domed_elytron", name, c, (3.9 * k, 3.8 * k, 8.6 * k), "shell_light")
        s.box("elytron_sheen", name, (c[0] - side * .2 * k, by + 2.75 * k, c[2] - .6 * k), (1.2 * k, .1, 5.4 * k), "sheen")
        s.box("folded_membrane", name, (c[0] * .9, by - .2 * k, c[2] + 4.3 * k), (2.8 * k, .2, 1.6 * k), "membrane")
        if s.age == 2:
            for j in range(3):
                s.box("gold_rune_line", name, (c[0] + side * .6 * k, by + 2.6 * k - j * .05, c[2] - 2.0 * k + j * 1.8 * k), (.3 * k, .1, 1.2 * k),
                      "rune_glow", (0, side * 30 * (1 if j % 2 else -1), 0))
    s.box("elytra_seam", "body", (0, by + 2.75 * k, 3.4 * k), (.25 * k, .2, 8.0 * k), "under")
    hy, hz = by + .1 * k, -5.6 * k
    s.bone("head", "body", (0, hy, hz + 1.2 * k))
    head = (4.6 * h, 3.4 * h, 3.6 * h)
    s.mass("beetle_head", "head", (0, hy, hz - .6 * h), head, "shell")
    front = hz - .6 * h - head[2] * .5
    if s.age == 0:
        _dot_eyes(s, "head", 1.3 * h, hy + .4 * h, front, 1.05 * h)
    else:
        for side in (-1, 1):
            s.box("compound_eye", "head", (side * 2.2 * h, hy + .3 * h, hz - 1.4 * h), (.9 * h, 1.2 * h, 1.3 * h), "eye")
            s.box("eye_glint", "head", (side * 2.68 * h, hy + .7 * h, hz - 1.6 * h), (.1, .35 * h, .35 * h), "shine")
    for side in (-1, 1):
        a = (side * 1.4 * h, hy + .9 * h, front + .2 * h)
        b = (side * 2.6 * h, hy + 2.6 * h, front - 1.6 * h)
        s.beam("antenna", "head", a, b, .3 * h, "leg")
        s.box("antenna_club", "head", b, (.7 * h, .8 * h, .7 * h), "horn")
        s.box("mandible", "head", (side * 1.0 * h, hy - 1.2 * h, front - .5 * h), (.6 * h, .6 * h, 1.3 * h), "horn", (0, side * 20, 0))
    if s.age == 0:
        s.box("horn_nub", "head", (0, hy + 1.8 * h, front + .4 * h), (1.0 * h, 1.4 * h, 1.0 * h), "horn")
    else:
        size = (.7, 1.0)[s.age - 1]
        lower = [(0, hy + .4 * h, front), (0, hy + 2.0 * h * size, front - 3.0 * h * size), (0, hy + 3.4 * h * size, front - 4.6 * h * size)]
        _chain(s, "head_horn", "head", lower, [1.4 * h, 1.0 * h], "horn")
        s.box("horn_tip", "head", lower[-1], (.8 * h, 1.1 * h, .8 * h), "horn_tip", (35, 0, 0))
        if s.age == 2:
            upper = [(0, by + 3.0 * k, -3.8 * k), (0, by + 5.2 * k, -6.6 * k), (0, by + 5.4 * k, -9.8 * k), (0, by + 4.2 * k, -11.6 * k)]
            _chain(s, "thorax_horn", "body", upper, [1.9 * k, 1.5 * k, 1.1 * k], "horn")
            s.box("horn_tip", "body", upper[-1], (1.0 * k, 1.3 * k, 1.0 * k), "horn_tip", (-40, 0, 0))
            for j in range(3):
                p = tuple(upper[1][a] + (upper[2][a] - upper[1][a]) * (j + .5) / 3 for a in range(3))
                s.box("horn_tooth", "body", (p[0], p[1] - .9 * k, p[2]), (.6 * k, .8 * k, .6 * k), "horn", (45, 0, 0))
            for side in (-1, 1):
                s.box("horn_gold_band", "body", (0, by + 5.0 * k, -6.2 * k), (2.0 * k, .4 * k, .8 * k), "horn_tip", (30, 0, 0))
    for side in (-1, 1):
        for i in range(3):
            name = ("leg_f" if i < 2 else "leg_b") + ("l" if side < 0 else "r")
            z = (-2.4 + i * 2.6) * k
            bone = s.bone(name, "body", (side * 2.6 * k, by - .4 * k, z)) if i in (0, 2) else name
            hip = (side * 2.9 * k, by - .9 * k, z)
            knee = (side * 4.3 * k, by - .3 * k, z + (i - 1) * .6 * k)
            foot = (side * 4.7 * k, .25 * k, z + (i - 1) * 1.2 * k)
            s.beam("beetle_femur", bone, hip, knee, 1.25 * k, "leg")
            s.beam("beetle_tibia", bone, knee, foot, 1.0 * k, "leg")
            s.box("tarsal_hook", bone, foot, (.5 * k, .5 * k, 1.0 * k), "horn", (0, side * 20, 0))
            s.box("tibia_spur", bone, knee, (.5 * k, .5 * k, .5 * k), "horn_tip" if s.age == 2 else "leg", (45, 0, 45))
    return s.finish()


def _jackal(stage):
    """A black Anubis jackal in gold and lapis: towering ears, a broad usekh collar and a
    scarab diadem. The pup's ears are almost bigger than it is."""
    s = Sculpt(stage, _palette(fur="#363142", fur_light="#4F4860", muzzle="#403A4E", lapis="#2F5CC9", inner_ear="#4A3B5C",
                               nose="#121016", eye="#FFC24A", ankh_glow="#6FF0E0", claw="#C8B98F", mouth="#0F0D12"))
    k, h = s.scale, s.head_scale
    leg = 7.4
    by = leg * k + 2.6 * k
    s.bones["body"] = ("root", [0, by, 0])
    s.mass("deep_chest", "body", (0, by + .3 * k, -2.0 * k), (5.6 * k, 6.0 * k, 6.0 * k), "fur")
    s.mass("lean_waist", "body", (0, by + .5 * k, 2.8 * k), (4.6 * k, 4.6 * k, 5.4 * k), "fur")
    s.mass("haunch", "body", (0, by + .7 * k, 4.6 * k), (5.0 * k, 4.8 * k, 3.4 * k), "fur_light")
    for side in (-1, 1):
        for end, zz in (("f", -1), ("b", 1)):
            x, z = side * 1.9 * k, (-3.6 if zz < 0 else 4.6) * k
            hip = (x, leg * k + 1.0 * k, z)
            foot = (x, 0, z + (-.2 if zz < 0 else .4) * k)
            bone, lower = _leg(s, "leg_" + end + ("l" if side < 0 else "r"), hip, foot, 1.6 * k, "fur", lower_mat="fur_light",
                               knee=(.5, (.3 if zz < 0 else -1.2) * k), lower_width=1.15 * k, paw=(1.7 * k, 1.0 * k, 2.4 * k), paw_mat="fur")
            if s.age:
                s.box("gold_anklet", lower, (x, leg * k * .22, foot[2]), (1.55 * k, .5 * k, 1.55 * k), "gold")
    neck = [(0, by + 2.0 * k, -4.2 * k), (0, by + 5.0 * k, -5.6 * k)]
    s.beam("slender_neck", "body", neck[0], neck[1], 2.8 * k, "fur", 3.0 * k)
    hy, hz = by + 5.4 * k + .9 * h, -6.2 * k - .4 * h
    s.bone("head", "body", (0, hy - 1.5 * h, hz + 1.6 * h))
    s.mass("narrow_skull", "head", (0, hy, hz), (4.6 * h, 4.2 * h, 4.4 * h), "fur")
    snout = (2.0, 3.4, 4.2)[s.age] * h
    s.box("long_snout", "head", (0, hy - .9 * h, hz - 2.2 * h - snout * .5), (2.4 * h, 2.0 * h, snout), "muzzle")
    s.box("snout_bridge", "head", (0, hy - .1 * h, hz - 2.0 * h - snout * .4), (1.6 * h, .8 * h, snout * .8), "fur")
    s.box("black_nose", "head", (0, hy - .4 * h, hz - 2.2 * h - snout + .2 * h), (1.0 * h, .7 * h, .5 * h), "nose")
    s.box("mouth_line", "head", (0, hy - 1.7 * h, hz - 2.2 * h - snout * .5), (2.45 * h, .18 * h, snout * .9), "mouth")
    if s.age == 0:
        s.eyes("head", 1.35 * h, hy + .45 * h, hz - 2.2 * h, .74 * h, gentle=True)
    else:
        for side in (-1, 1):
            s.box("almond_eye", "head", (side * 1.3 * h, hy + .5 * h, hz - 2.25 * h), (1.5 * h, .8 * h, .2), "eye", (0, 0, side * -12))
            s.box("slit_pupil", "head", (side * 1.25 * h, hy + .5 * h, hz - 2.37 * h), (.3 * h, .7 * h, .1), "mouth")
            s.box("kohl_line", "head", (side * 1.75 * h, hy + .25 * h, hz - 2.3 * h), (1.4 * h, .2 * h, .15), "lapis", (0, 0, side * 14))
            s.box("brow", "head", (side * 1.3 * h, hy + 1.05 * h, hz - 2.3 * h), (1.5 * h, .35 * h, .3 * h), "fur_light", (0, 0, side * 12))
    ear = (5.0, 5.4, 6.2)[s.age] * h
    for side in (-1, 1):
        base = (side * 1.5 * h, hy + 2.0 * h, hz + .6 * h)
        s.box("towering_ear", "head", (base[0] + side * .4 * h, base[1] + ear * .5, base[2]), (2.0 * h, ear, 1.0 * h), "fur", (0, 0, -side * 8))
        s.box("tall_inner_ear", "head", (base[0] + side * .4 * h, base[1] + ear * .45, base[2] - .55 * h), (1.0 * h, ear * .75, .15), "inner_ear",
              (0, 0, -side * 8))
        s.box("ear_tip", "head", (base[0] + side * .55 * h, base[1] + ear + .35 * h, base[2]), (1.0 * h, 1.0 * h, .9 * h), "fur", (0, 0, -side * 8 + 45))
        if s.age:
            for j in range(s.age):
                s.box("gold_ear_cuff", "head", (base[0] + side * .32 * h, base[1] + (1.2 + j * 1.4) * h, base[2]), (2.2 * h, .45 * h, 1.2 * h),
                      "gold", (0, 0, -side * 8))
    # A broad usekh collar: gold bands with a lapis band between.
    rings = ((3.4, "gold"), (4.0, "lapis"), (4.6, "gold")) if s.age else ((3.4, "gold"),)
    for r, mat in rings:
        s.crescent("usekh_collar", "body", (0, by + 4.6 * k, -6.0 * k), r * k, .7 * k, .5, mat, segments=7, opening=90)
    if s.age:
        s.box("ankh_loop", "body", (0, by + .8 * k, -6.6 * k), (1.2 * k, 1.4 * k, .3), "ankh_glow", (0, 0, 45))
        s.box("ankh_bar", "body", (0, by + .1 * k, -6.6 * k), (1.6 * k, .35 * k, .3), "ankh_glow")
        s.box("ankh_stem", "body", (0, by - .7 * k, -6.6 * k), (.4 * k, 1.4 * k, .3), "ankh_glow")
    if s.age == 2:
        s.box("diadem_band", "head", (0, hy + 1.45 * h, hz), (4.8 * h, .55 * h, 4.6 * h), "gold")
        s.gem("scarab_gem", "head", 0, hy + 1.6 * h, hz - 2.5 * h, .9 * h, "lapis")
        for side in (-1, 1):
            s.box("nemes_stripe", "head", (side * 2.42 * h, hy - .6 * h, hz + .6 * h), (.3, 3.4 * h, 1.4 * h), "lapis")
    s.bone("tail", "body", (0, by + 1.6 * k, 6.2 * k))
    pts = [(0, by + 1.6 * k, 6.2 * k), (0, by - .6 * k, 8.0 * k), (.3 * k, by - 3.0 * k, 9.0 * k)]
    _chain(s, "slim_tail", "tail", pts, [1.1 * k, 1.3 * k], "fur")
    s.mass("bushy_tail_tip", "tail", (.4 * k, by - 4.0 * k, 9.2 * k), (1.8 * k, 2.4 * k, 1.8 * k), "fur_light")
    if s.age:
        s.box("tail_gold_ring", "tail", pts[1], (1.6 * k, 1.6 * k, .6 * k), "gold")
    return s.finish()


def _drake(stage):
    """A bone drake animated by soul fire: a ribcage around a burning teal heart, a horned
    skull and tattered wings. The hatchling is mostly skull, with a candle-flame of soul."""
    s = Sculpt(stage, _palette(bone="#EAE3D1", bone_shade="#BEB49C", bone_dark="#8D836F", soul_glow="#6FF3E1",
                               soul_core_glow="#CBFFF7", membrane="#3F5064", horn="#D8CEB6", eye="#7FFFF0",
                               socket="#1A1A22", chain="#5B5F6B"))
    k, h = s.scale, s.head_scale
    leg = 5.2
    by = leg * k + 2.6 * k
    s.bones["body"] = ("root", [0, by, 0])
    spine = [(0, by + 3.0 * k, -4.6 * k), (0, by + 3.2 * k, -1.6 * k), (0, by + 3.0 * k, 1.4 * k), (0, by + 2.4 * k, 4.2 * k)]
    for i, p in enumerate(spine):
        s.box("vertebra", "body", p, (1.6 * k, 1.4 * k, 2.2 * k), "bone")
        s.box("spinal_spike", "body", (p[0], p[1] + 1.0 * k, p[2]), (.5 * k, 1.2 * k, .9 * k), "bone_shade", (-25, 0, 0))
    ribs = (2, 3, 4)[s.age]
    for i in range(ribs):
        z = (-3.6 + i * 2.0) * k
        top_y = by + 2.6 * k
        for side in (-1, 1):
            pts = [(side * .6 * k, top_y, z), (side * 3.4 * k, top_y - .8 * k, z + .2 * k), (side * 3.8 * k, by - 1.2 * k, z + .5 * k),
                   (side * 1.6 * k, by - 2.8 * k, z + .2 * k)]
            _chain(s, "curved_rib", "body", pts, .7 * k, ["bone", "bone_shade", "bone"])
        s.box("sternum", "body", (0, by - 2.9 * k, z + .2 * k), (3.4 * k, .7 * k, .9 * k), "bone_shade")
    heart = (2.2, 2.8, 3.4)[s.age] * k
    s.ball("soul_heart", "body", (0, by + .2 * k, -1.8 * k), (heart, heart, heart), "soul_glow")
    s.ball("soul_heart_core", "body", (0, by + .2 * k, -1.8 * k), (heart * .55, heart * .55, heart * 1.06), "soul_core_glow")
    s.mass("pelvis", "body", (0, by + 1.2 * k, 4.2 * k), (4.2 * k, 1.6 * k, 2.6 * k), "bone_shade")
    for side in (-1, 1):
        for end, zz in (("f", -1), ("b", 1)):
            x, z = side * 2.6 * k, (-3.4 if zz < 0 else 4.2) * k
            hip = (x, by + .4 * k, z)
            bone = s.bone("leg_" + end + ("l" if side < 0 else "r"), "body", hip)
            knee = (x * 1.15, leg * k * .55, z + (-.6 if zz < 0 else -1.2) * k)
            foot = (x * 1.15, .3 * k, z + .2 * k)
            s.ball("joint_knob", bone, hip, (1.5 * k, 1.5 * k, 1.5 * k), "bone_shade")
            s.beam("thigh_bone", bone, hip, knee, .9 * k, "bone")
            s.beam("shin_bone", bone, knee, foot, .75 * k, "bone")
            for toe in (-1, 0, 1):
                s.beam("talon_bone", bone, foot, (foot[0] + toe * .6 * k, .2 * k, foot[2] - 1.5 * k), .45 * k, "bone_dark")
    neck = [spine[0], (0, by + 4.8 * k, -6.0 * k), (0, by + 6.4 * k, -6.8 * k)]
    for p in neck[1:]:
        s.box("neck_vertebra", "body", p, (1.5 * k, 1.5 * k, 1.5 * k), "bone")
    hy, hz = by + 6.6 * k + 1.4 * h, -7.6 * k - .8 * h
    s.bone("head", "body", (0, hy - 1.8 * h, hz + 1.8 * h))
    skull = (5.4 * h, 4.6 * h, 5.6 * h)
    s.mass("horned_skull", "head", (0, hy, hz), skull, "bone")
    front = hz - skull[2] * .5
    s.box("skull_snout", "head", (0, hy - .9 * h, front - 1.3 * h), (3.6 * h, 2.2 * h, 2.8 * h), "bone")
    s.box("lower_jaw", "head", (0, hy - 2.5 * h, front - .9 * h), (3.2 * h, .9 * h, 3.4 * h), "bone_shade", (8, 0, 0))
    for side in (-1, 1):
        s.box("eye_socket", "head", (side * 1.4 * h, hy + .55 * h, front - .05), (1.7 * h, 1.5 * h, .3), "socket")
        s.box("soul_eye", "head", (side * 1.4 * h, hy + .5 * h, front - .2), (.9 * h, (1.1 if s.age == 0 else .7) * h, .2), "eye")
        s.box("nasal_slit", "head", (side * .55 * h, hy - .3 * h, front - 2.72 * h), (.4 * h, .6 * h, .15), "socket")
        for j in range(2 + s.age):
            s.box("skull_tooth", "head", (side * 1.4 * h, hy - 2.1 * h, front - .4 * h - j * .8 * h), (.35 * h, .6 * h, .35 * h), "bone")
        horn = (1.2, 2.6, 4.0)[s.age] * h
        pts = [(side * 1.8 * h, hy + 1.8 * h, hz + .6 * h), (side * 2.6 * h, hy + 1.8 * h + horn * .6, hz + 1.6 * h + horn * .3),
               (side * 2.9 * h, hy + 1.6 * h + horn * .9, hz + 2.4 * h + horn * .7)]
        _chain(s, "swept_horn", "head", pts, [1.0 * h, .6 * h], "horn")
        s.box("cheek_spike", "head", (side * 2.9 * h, hy - .6 * h, hz + .8 * h), (.6 * h, .6 * h, 1.8 * h), "bone_shade", (0, side * 25, 0))
    if s.age == 0:
        for j, (dy, size, mat) in enumerate(((.9, 1.6, "soul_glow"), (2.0, 1.1, "soul_glow"), (2.8, .6, "soul_core_glow"))):
            s.box("candle_soul_flame", "head", (0, hy + skull[1] * .5 + dy * h, hz + .3 * h), (size * h, size * 1.4 * h, size * h), mat, (0, 45, 0))
    for side, name in ((-1, "wing_l"), (1, "wing_r")):
        root = (side * 1.4 * k, by + 3.2 * k, -2.6 * k)
        s.bone(name, "body", root)
        span = (5.0, 9.0, 13.0)[s.age]
        elbow = (side * (1.4 * k + span * .32), by + 3.2 * k + span * .45, -2.0 * k)
        wrist = (side * (1.4 * k + span * .62), by + 3.2 * k + span * .55, .2 * k)
        _chain(s, "wing_arm_bone", name, [root, elbow, wrist], [.8 * k, .6 * k], "bone")
        fingers = []
        for j in range(4):
            t = j / 3
            tip = (side * (1.4 * k + span * (.62 + .4 * (1 - t * .5))), by + 3.2 * k + span * (.6 - .75 * t), (1.0 + 3.8 * t) * k)
            s.beam("wing_finger_bone", name, wrist, tip, .35 * k, "bone_shade")
            fingers.append(tip)
        for j in range(3):
            if (j + s.age) % 4 == 3:
                continue
            a, b = fingers[j], fingers[j + 1]
            torn = .55 if j == 1 else .85
            c = tuple(wrist[i] * (1 - torn * .6) + (a[i] + b[i]) / 2 * torn * .6 for i in range(3))
            span_ab = math.dist(a, b)
            yaw = math.degrees(math.atan2(b[2] - a[2], abs(b[0] - a[0]) + 1e-6)) * -side
            s.box("tattered_membrane", name, c, (span_ab * .55, math.dist(wrist, a) * torn * .9, .3), "membrane", (0, yaw, side * (40 + j * 22)))
    s.bone("tail", "body", spine[-1])
    tail = [spine[-1], (0, by + 1.8 * k, 7.0 * k), (.5 * k, by + 1.4 * k, 9.8 * k), (1.2 * k, by + 2.0 * k, 12.4 * k)]
    s.bone("tail_tip", "tail", tail[2])
    for i, p in enumerate(tail[1:]):
        size = (1.4 - .25 * i) * k
        s.box("tail_vertebra", "tail" if i < 1 else "tail_tip", p, (size, size, size * 1.4), "bone")
        s.box("tail_spike", "tail" if i < 1 else "tail_tip", (p[0], p[1] + size * .7, p[2]), (size * .3, size * .8, size * .5), "bone_shade", (-25, 0, 0))
    _chain(s, "tail_spine_cord", ["tail", "tail_tip", "tail_tip"], tail, .5 * k, "bone_dark", joints=False)
    flame = (1, 2, 3)[s.age]
    for j in range(flame + 1):
        size = (1.6 - .4 * j) * k
        s.box("soul_flame", "tail_tip", (tail[-1][0] + .1 * j * k, tail[-1][1] + (.6 + 1.0 * j) * k, tail[-1][2] + .4 * k), (size, size * 1.5, size),
              "soul_glow" if j < flame else "soul_core_glow", (0, 45, 10 * j))
    if s.age == 2:
        for side in (-1, 1):
            for j in range(2):
                s.box("shoulder_wisp", "body", (side * 2.0 * k, by + (4.2 + j * 1.0) * k, (-3.4 + j * .5) * k), ((1.0 - .4 * j) * k,) * 3,
                      "soul_glow", (0, 45, 20))
    return s.finish()


# ---------------------------------------------------------------------------
# Beautiful


def _lung(stage):
    """An eastern jade dragon that swims through the air: a long undulating body with a cream
    belly, golden mane, antlers and whiskers. The adult holds a pearl and rides two clouds."""
    s = Sculpt(stage, _palette(scale="#3FA67A", scale_light="#8EDCAF", scale_dark="#2A7457", belly="#F2E3B3", mane="#F6EEDC",
                               mane_tip="#F2C14E", horn="#E9DAB0", whisker="#F7EFD8", pearl_glow="#EAFBFF",
                               cloud="#FFFFFF", cloud_shade="#DDE8F2", eye="#FFD84A", claw="#F2E9CF", mouth="#7A2E3A"))
    k, h = s.scale, s.head_scale
    base = 6.0 * k + 3.0
    length = (13, 22, 30)[s.age]
    segments = (7, 10, 13)[s.age]
    pts = []
    for i in range(segments + 1):
        t = i / segments
        pts.append((math.sin(t * math.tau * .9) * 2.2 * k * t,
                    base + math.sin(t * math.tau * 1.1 + .6) * 2.4 * k - t * 1.2 * k,
                    -2.0 * k + t * length))
    s.bones["body"] = ("root", list(pts[0]))
    split1, split2 = int(segments * .38), int(segments * .72)
    s.bone("tail", "body", pts[split1])
    s.bone("tail_tip", "tail", pts[split2])
    owner = lambda i: "body" if i < split1 else "tail" if i < split2 else "tail_tip"
    for i in range(segments):
        t = i / segments
        w = (4.8 - 3.8 * t ** 1.2) * k
        a, b = pts[i], pts[i + 1]
        bone = owner(i)
        s.beam("serpent_body", bone, a, b, w, "scale" if i % 2 == 0 else "scale_dark")
        s.box("body_joint", bone, a, (w * .95, w * .95, w * .95), "scale")
        s.beam("cream_belly_plate", bone, (a[0], a[1] - w * .42, a[2]), (b[0], b[1] - w * .42, b[2]), w * .62, "belly", w * .3)
        if i % 2 == 0 and t < .92:
            tuft = (a[0], a[1] + w * .5, a[2] + .4 * k)
            s.beam("golden_mane_tuft", bone, tuft, (tuft[0], tuft[1] + (1.2 + .4 * s.age) * k, tuft[2] + 1.4 * k), .6 * k, "mane_tip" if i % 4 == 0 else "mane")
    s.box("tail_plume", "tail_tip", pts[-1], (1.6 * k, 2.6 * k, 2.4 * k), "mane", (-30, 0, 0))
    s.box("tail_plume_tip", "tail_tip", (pts[-1][0], pts[-1][1] + 1.0 * k, pts[-1][2] + 1.4 * k), (1.0 * k, 1.8 * k, 1.6 * k), "mane_tip", (-40, 0, 0))
    for (bone_name, idx), side in [(("leg_fl", 2), -1), (("leg_fr", 2), 1), (("leg_bl", split1 + 2), -1), (("leg_br", split1 + 2), 1)]:
        p = pts[min(idx, segments)]
        w = (4.8 - 3.8 * (idx / segments) ** 1.2) * k
        parent = owner(idx)
        hip = (p[0] + side * w * .4, p[1] - w * .2, p[2])
        foot = (hip[0] + side * 1.8 * k, hip[1] - 2.4 * k, hip[2] - 1.0 * k)
        s.bones[bone_name] = (parent, [round(v, 4) for v in hip])
        s.beam("dragon_leg", bone_name, hip, foot, 1.0 * k, "scale_light")
        for toe in (-1, 0, 1):
            s.box("golden_claw", bone_name, (foot[0] + toe * .45 * k, foot[1] - .4 * k, foot[2] - .5 * k), (.3 * k, .3 * k, 1.0 * k), "claw", (-30, toe * 20, 0))
        if s.age == 2 and bone_name == "leg_fr":
            s.ball("dragon_pearl", bone_name, (foot[0] - .4 * k, foot[1] - .8 * k, foot[2] - 1.2 * k), (2.2, 2.2, 2.2), "pearl_glow")
    hy, hz = pts[0][1] + .6 * k + .6 * h, pts[0][2] - 2.2 * k - .8 * h
    s.bone("head", "body", (0, hy - .6 * h, hz + 2.6 * h))
    s.mass("dragon_brow", "head", (0, hy + .4 * h, hz), (5.0 * h, 3.8 * h, 4.4 * h), "scale")
    snout = (2.0, 3.0, 3.8)[s.age] * h
    s.mass("long_snout", "head", (0, hy - .4 * h, hz - 2.2 * h - snout * .5), (3.6 * h, 2.6 * h, snout), "scale_light")
    s.ball("nose_bulb", "head", (0, hy - .05 * h, hz - 2.2 * h - snout), (2.8 * h, 1.8 * h, 1.4 * h), "scale")
    s.box("lower_jaw", "head", (0, hy - 1.9 * h, hz - 2.0 * h - snout * .4), (3.0 * h, .9 * h, snout * .9), "belly")
    s.box("mouth_line", "head", (0, hy - 1.45 * h, hz - 2.0 * h - snout * .55), (3.65 * h, .2 * h, snout * .7), "mouth")
    s.eyes("head", 1.6 * h, hy + .7 * h, hz - 2.2 * h, .78 * h, iris="eye", gentle=s.age == 0)
    for side in (-1, 1):
        s.box("nostril_curl", "head", (side * .8 * h, hy + .55 * h, hz - 2.2 * h - snout - .5 * h), (.5 * h, .5 * h, .2), "scale_dark")
        if s.age:
            s.box("brow_ridge", "head", (side * 1.6 * h, hy + 1.65 * h, hz - 2.25 * h), (1.8 * h, .5 * h, .6 * h), "mane_tip", (0, 0, side * 10))
        # Antlers: a main beam swept back with branching tines.
        size = (.6, .9, 1.2)[s.age]
        a = (side * 1.4 * h, hy + 2.2 * h, hz + .8 * h)
        b = (side * 2.2 * h, hy + (2.2 + 2.6 * size) * h, hz + (1.8 + 1.6 * size) * h)
        c = (side * 2.6 * h, hy + (2.4 + 3.6 * size) * h, hz + (2.8 + 3.6 * size) * h)
        _chain(s, "antler", "head", [a, b, c], [.8 * h, .55 * h], "horn")
        if s.age:
            s.beam("antler_tine", "head", b, (side * 3.0 * h, hy + (2.6 + 3.8 * size) * h, hz + (1.2 + 1.6 * size) * h), .45 * h, "horn")
        # Long flowing whiskers from the snout.
        reach = (2.4, 4.2, 6.0)[s.age]
        root = (side * 1.6 * h, hy - .6 * h, hz - 2.2 * h - snout * .8)
        wpts = [root, (side * (2.6 + reach * .3) * h, hy - 1.0 * h, hz - 1.0 * h), (side * (3.0 + reach * .55) * h, hy - 2.4 * h, hz + reach * .5 * h),
                (side * (3.2 + reach * .6) * h, hy - 3.6 * h, hz + reach * h)]
        _chain(s, "flowing_whisker", "head", wpts, [.35 * h, .3 * h, .25 * h], "whisker", joints=False)
        s.box("cheek_fin", "head", (side * 2.8 * h, hy - .3 * h, hz + .6 * h), (1.0 * h, 2.4 * h, 2.2 * h), "mane", (0, side * 20, side * -20))
    for i in range(3 + s.age):
        s.beam("head_mane", "head", ((i - (2 + s.age) / 2) * .9 * h, hy + 1.8 * h, hz + 1.6 * h),
               ((i - (2 + s.age) / 2) * 1.4 * h, hy + (2.6 + .3 * s.age) * h, hz + (3.8 + .6 * s.age) * h), .7 * h, "mane" if i % 2 else "mane_tip")
    s.beam("beard", "head", (0, hy - 2.3 * h, hz - 1.8 * h), (0, hy - 3.6 * h, hz - .6 * h), .9 * h, "mane")
    if s.age == 2:
        for bone_name, idx in (("body", 3), ("tail", split2 - 1)):
            p = pts[idx]
            for j, (dx, dz, size) in enumerate(((-1.6, 0, 3.4), (1.4, .6, 3.0), (0, -1.2, 2.6))):
                s.ball("riding_cloud", bone_name, (p[0] + dx, p[1] - 4.2 - .3 * j, p[2] + dz), (size, size * .62, size * .9), "cloud" if j % 2 == 0 else "cloud_shade")
    return s.finish()


def _stag(stage):
    """A moonlit stag with antlers of living crystal. The fawn is spotted with only two
    crystal buds; the adult's antlers branch wide and carry a garland of blossoms."""
    s = Sculpt(stage, _palette(fur="#EAE4F4", fur_shade="#C9C0DE", belly="#FAF7FF", fawn_spot="#FFFFFF", hoof="#6D6488",
                               crystal="#A6EAFF", crystal_deep="#6FC4F0", crystal_glow="#E4FBFF", eye="#4E77D0",
                               nose="#5B4F6E", flower="#F7B4D0", flower_core="#FFE07A", leaf="#7DBE88", lash="#3D3550"))
    if s.age == 0:
        s.palette.update(fur="#D8C2AE", fur_shade="#B9A08C")
    k, h = s.scale, s.head_scale
    leg = 8.6
    by = leg * k + 2.6 * k
    s.bones["body"] = ("root", [0, by, 0])
    s.mass("graceful_chest", "body", (0, by + .4 * k, -2.0 * k), (5.6 * k, 6.0 * k, 5.8 * k), "fur")
    s.mass("slim_barrel", "body", (0, by + .4 * k, 2.4 * k), (5.0 * k, 5.4 * k, 6.0 * k), "fur")
    s.mass("pale_belly", "body", (0, by - 2.2 * k, .2 * k), (4.0 * k, 1.2 * k, 8.6 * k), "belly")
    if s.age == 0:
        for i in range(8):
            a = i * 2.39996
            side = -1 if i % 2 else 1
            s.box("fawn_spot", "body", (side * 2.52 * k if i % 3 else math.cos(a) * 1.2 * k, by + (2.0 if i % 3 else 3.2) * k - (i % 2) * .7 * k,
                                        (-3.0 + i * .9) * k), (.12 if i % 3 else .7 * k, .7 * k, .7 * k), "fawn_spot")
    for side in (-1, 1):
        for end, zz in (("f", -1), ("b", 1)):
            x, z = side * 1.8 * k, (-3.6 if zz < 0 else 4.0) * k
            hip = (x, leg * k + 1.0 * k, z)
            foot = (x, 0, z + (-.3 if zz < 0 else .3) * k)
            bone, lower = _leg(s, "leg_" + end + ("l" if side < 0 else "r"), hip, foot, 1.5 * k, "fur", lower_mat="fur_shade",
                               knee=(.48, (.3 if zz < 0 else -1.3) * k), lower_width=1.0 * k, paw=(1.3 * k, 1.1 * k, 1.6 * k), paw_mat="hoof")
    neck = [(0, by + 2.0 * k, -4.0 * k), (0, by + 5.6 * k, -5.4 * k)]
    s.beam("elegant_neck", "body", neck[0], neck[1], 2.6 * k, "fur", 2.8 * k)
    if s.age == 2:
        s.mass("white_chest_ruff", "body", (0, by + 2.6 * k, -4.8 * k), (3.4 * k, 3.4 * k, 2.2 * k), "belly")
    hy, hz = by + 6.0 * k + 1.0 * h, -6.0 * k - .6 * h
    s.bone("head", "body", (0, hy - 1.6 * h, hz + 1.6 * h))
    s.mass("deer_head", "head", (0, hy, hz), (4.2 * h, 4.0 * h, 4.4 * h), "fur")
    snout = (1.6, 2.4, 3.0)[s.age] * h
    s.box("tapered_muzzle", "head", (0, hy - 1.0 * h, hz - 2.0 * h - snout * .5), (2.6 * h, 2.0 * h, snout), "fur")
    s.box("dark_nose", "head", (0, hy - .55 * h, hz - 2.0 * h - snout + .1 * h), (1.3 * h, .9 * h, .5 * h), "nose")
    s.box("pale_chin", "head", (0, hy - 1.95 * h, hz - 1.9 * h - snout * .45), (2.0 * h, .3 * h, snout * .8), "belly")
    s.eyes("head", 1.35 * h, hy + .4 * h, hz - 2.2 * h, .8 * h, iris="eye", gentle=True)
    for side in (-1, 1):
        s.box("eyelash", "head", (side * 1.65 * h, hy + 1.45 * h, hz - 2.3 * h), (.9 * h, .2 * h, .15), "lash", (0, 0, side * -20))
        ear = (3.6, 3.0, 2.8)[s.age] * h
        s.box("wide_ear", "head", (side * (2.4 * h + ear * .4), hy + 1.6 * h, hz + .6 * h), (ear, 1.5 * h, .8 * h), "fur", (0, 0, -side * 28))
        s.box("inner_ear", "head", (side * (2.4 * h + ear * .4), hy + 1.6 * h, hz + .15 * h), (ear * .7, .8 * h, .12), "flower", (0, 0, -side * 28))
    tips = []
    for side in (-1, 1):
        if s.age == 0:
            s.box("crystal_bud", "head", (side * 1.2 * h, hy + 2.5 * h, hz + .4 * h), (.8 * h, 1.1 * h, .8 * h), "crystal", (0, 45, 0))
            continue
        size = (0, .7, 1.0)[s.age]
        a = (side * 1.2 * h, hy + 1.9 * h, hz + .4 * h)
        b = (side * (2.4 + 1.2 * size) * h, hy + (2 + 3.4 * size) * h, hz + (.8 + 1.2 * size) * h)
        c = (side * (3.2 + 2.6 * size) * h, hy + (2.2 + 6.6 * size) * h, hz + (1.6 + 2.2 * size) * h)
        _chain(s, "crystal_antler", "head", [a, b, c], [.9 * h, .7 * h], ["crystal", "crystal_deep"])
        tines = [(b, (side * (2.0 + 1.0 * size) * h, hy + (2.6 + 5.0 * size) * h, hz - (.4 + .6 * size) * h)),
                 (c, (side * (2.6 + 2.0 * size) * h, hy + (2.6 + 8.4 * size) * h, hz + (1.0 + 1.0 * size) * h))]
        if s.age == 2:
            mid = tuple((b[i] + c[i]) / 2 for i in range(3))
            tines.append((mid, (side * 6.6 * h, hy + 7.6 * h, hz + 2.8 * h)))
            tines.append((c, (side * 7.6 * h, hy + 9.4 * h, hz + 4.4 * h)))
        for start, end in tines:
            s.beam("crystal_tine", "head", start, end, .55 * h, "crystal")
            s.box("crystal_tip_glow", "head", end, (.6 * h, .9 * h, .6 * h), "crystal_glow", (0, 45, 0))
            tips.append(end)
        s.box("crystal_tip_glow", "head", c, (.7 * h, 1.0 * h, .7 * h), "crystal_glow", (0, 45, 0))
    if s.age == 2:
        # A garland of blossoms sags between the antlers.
        left, right = (-4.0 * h, hy + 5.6 * h, hz + 1.4 * h), (4.0 * h, hy + 5.6 * h, hz + 1.4 * h)
        for j in range(5):
            t = j / 4
            p = (left[0] + (right[0] - left[0]) * t, left[1] - math.sin(t * math.pi) * 2.2 * h, left[2] - math.sin(t * math.pi) * .8 * h)
            _flower(s, "head", p, 1.0 * h, "flower", "flower_core", spin=j * 15)
            if j < 4:
                q = (left[0] + (right[0] - left[0]) * (t + .125), left[1] - math.sin((t + .125) * math.pi) * 2.2 * h, p[2] + .2)
                s.box("garland_leaf", "head", q, (1.0 * h, .5 * h, .3), "leaf", (0, 0, 30 if j % 2 else -30))
    s.bone("tail", "body", (0, by + 2.0 * k, 5.4 * k))
    s.mass("puff_tail", "tail", (0, by + 2.2 * k, 5.9 * k), (1.6 * k, 2.0 * k, 1.2 * k), "belly")
    return s.finish()


def _moth(stage):
    """A luna moth: a fluffy white body, feathery antennae and pale mint wings with long
    trailing tails. The adult's forewings carry glowing crescent moons."""
    s = Sculpt(stage, _palette(fluff="#FFFFFF", fluff_shade="#E3E8F2", wing="#BFF1CC", wing_light="#E4FBE8", wing_edge="#F6E7A6",
                               eyespot_ring="#F2A93B", eyespot_core="#5C3C91", moon_glow="#FFF5CC", antenna="#E8C47E",
                               eye="#1E1824", pupil="#1E1824", leg="#D9D2C3", blush="#FFB0C8"))
    k, h = s.scale, s.head_scale
    by = 6.4 * k + 2.6
    s.bones["body"] = ("root", [0, by, 0])
    s.ball("fluffy_thorax", "body", (0, by, .6 * k), (5.2 * k, 5.4 * k, 5.6 * k), "fluff")
    s.ball("fur_collar", "body", (0, by + .6 * k, -1.6 * k), (6.4 * k, 4.4 * k, 2.6 * k), "fluff_shade")
    for i, (z, w) in enumerate(((3.6, 3.6), (5.6, 3.0), (7.2, 2.2))):
        if s.age == 0 and i == 2:
            continue
        s.ball("soft_abdomen", "body", (0, by - .6 * k - i * .5 * k, z * k), (w * k, w * k, 2.4 * k), "fluff" if i % 2 == 0 else "fluff_shade")
    hy, hz = by + 1.6 * k + 1.8 * h, -2.0 * k - .6 * h
    s.bone("head", "body", (0, hy - 2.4 * h, hz + 1.8 * h))
    head = (6.4 * h, 5.8 * h, 5.6 * h)
    s.ball("fluffy_head", "head", (0, hy, hz), head, "fluff")
    ey = hy - .1 * h
    _dot_eyes(s, "head", 1.6 * h, ey, _front(head, (0, hy, hz), 1.6 * h, ey), (1.9, 1.75, 1.6)[s.age] * h)
    _smile(s, "head", ey - 1.9 * h, _front(head, (0, hy, hz), 0, ey - 1.9 * h), .9 * h, "eyespot_core")
    for side in (-1, 1):
        root = (side * 1.2 * h, hy + 2.6 * h, hz - .4 * h)
        tip = (side * (3.4 + .6 * s.age) * h, hy + (5.6 + .8 * s.age) * h, hz - 1.0 * h)
        s.beam("feathery_antenna", "head", root, tip, .35 * h, "antenna")
        for j in range(3 + s.age):
            t = (j + 1) / (4 + s.age)
            p = tuple(root[a] + (tip[a] - root[a]) * t for a in range(3))
            s.box("antenna_comb", "head", (p[0] + side * .55 * h, p[1] - .2 * h, p[2]), (1.2 * h * (1 - t * .5), .2 * h, .25 * h), "antenna", (0, 0, side * -35))
    span = (5.0, 8.4, 11.0)[s.age]
    for side, name in ((-1, "wing_l"), (1, "wing_r")):
        root = (side * 1.4 * k, by + 1.4 * k, 1.8 * k)
        s.bone(name, "body", root)
        # Wings open like a butterfly's behind the body, so they face whoever looks at it.
        rot = (0, side * -16, side * 24)
        c = (side * (1.4 * k + span * .46), by + 1.4 * k + span * .36, 2.0 * k)
        s.box("forewing", name, c, (span * .82, span * .56, .3), "wing", rot, c)
        s.box("forewing_round", name, c, (span * .64, span * .74, .26), "wing", rot, c)
        s.box("forewing_rim", name, (c[0], c[1], c[2] - .1), (span * .9, span * .08, .2), "wing_edge", rot, c)
        s.box("forewing_vein", name, (c[0], c[1] - span * .1, c[2] - .18), (span * .7, .25, .1), "wing_light", rot, c)
        spot = (c[0] + side * span * .06, c[1] - span * .04, c[2] - .25)
        if s.age == 2:
            s.box("moon_eyespot", name, spot, (span * .2, span * .2, .1), "eyespot_ring", rot, c)
            s.crescent("wing_crescent_moon", name, (spot[0], spot[1], spot[2] - .1), span * .07, span * .05, .1, "moon_glow", segments=5, opening=-90 * side)
        else:
            s.box("eyespot_ring", name, spot, (span * .18, span * .18, .1), "eyespot_ring", rot, c)
            s.box("eyespot_core", name, (spot[0], spot[1], spot[2] - .06), (span * .09, span * .09, .1), "eyespot_core", rot, c)
        # Hindwing below, with the luna moth's long trailing tail.
        rot2 = (0, side * -20, side * -26)
        c2 = (side * (1.4 * k + span * .3), by + 1.0 * k - span * .1, 2.4 * k)
        s.box("hindwing", name, c2, (span * .6, span * .46, .3), "wing_light", rot2, c2)
        s.box("hindwing_round", name, c2, (span * .44, span * .6, .26), "wing_light", rot2, c2)
        s.box("hindwing_rim", name, (c2[0], c2[1] - span * .26, c2[2] - .1), (span * .5, span * .07, .2), "wing_edge", rot2, c2)
        if s.age:
            reach = (0, .55, .8)[s.age] * span
            start = (c2[0] + side * span * .16, c2[1] - span * .22, c2[2] + .2)
            tail = [start, (start[0] + side * span * .08, start[1] - reach * .3, start[2] + reach * .5),
                    (start[0] + side * span * .1, start[1] - reach * .42, start[2] + reach * 1.0)]
            _chain(s, "trailing_wing_tail", name, tail, [span * .12, span * .09], ["wing_light", "wing"], depths=[.3, .3])
            s.box("wing_tail_spoon", name, (tail[-1][0], tail[-1][1] - .2, tail[-1][2] + .4), (span * .17, .3, span * .2), "wing_edge", (0, 0, side * -20))
    for side in (-1, 1):
        leg = s.bone("leg_l" if side < 0 else "leg_r", "body", (side * 1.2 * k, by - 1.8 * k, -.2 * k))
        for j in range(3):
            z = (-.8 + j * 1.0) * k
            s.beam("dangling_leg", leg, (side * 1.2 * k, by - 1.6 * k, z), (side * 1.8 * k, by - 3.4 * k, z - .3 * k), .45 * k, "leg")
    if s.age == 2:
        s.crescent("moon_tiara", "head", (0, hy + 3.4 * h, hz - .4 * h), 1.3 * h, .6 * h, .4, "moon_glow", segments=5, opening=90)
    return s.finish()


def _koi(stage):
    """A celestial koi swimming on air, white with a red tancho crown and flowing butterfly
    fins; the adult wears a lotus and trails little bubbles."""
    s = Sculpt(stage, _palette(koi_white="#FFF9F2", koi_red="#E5483A", koi_gold="#F5B533", fin="#FFD7C6", fin_light="#FFF1EA",
                               eye="#1C1A24", lip="#F2B8A8", whisker="#F5D9C8", lotus="#F6A6C7", lotus_light="#FFE0EC",
                               lotus_core="#FFE07A", bubble_glow="#BDF5FF", scale_glint="#FFFFFF"))
    k, h = s.scale, s.head_scale
    by = 6.2 * k + 3.0
    s.bones["body"] = ("root", [0, by, 0])
    sections = [(-1.4, 6.2, 5.6), (1.6, 5.6, 5.0), (4.2, 4.4, 3.9), (6.4, 3.0, 2.7)]
    for i, (z, w, hh) in enumerate(sections):
        if s.age == 0 and i == 3:
            continue
        s.ball("koi_body", "body", (0, by - i * .15 * k, z * k), (w * k, hh * k, 3.4 * k), "koi_white")
    patches = [((.0, 2.7, 1.2), (3.6, .5, 2.4), "koi_red"), ((1.4, 2.3, 3.8), (2.4, .5, 2.0), "koi_gold"), ((-1.2, 2.0, 4.6), (2.0, .5, 1.6), "koi_red")]
    for i, ((x, y, z), size, mat) in enumerate(patches[:1 + s.age]):
        s.box("koi_patch", "body", (x * k, by + y * k, z * k), tuple(v * k for v in size), mat, (0, 15 * (i - 1), 0))
    hy, hz = by + .2 * k, -3.6 * k - 1.2 * h
    s.bone("head", "body", (0, hy, hz + 2.2 * h))
    head = (5.6 * h, 4.8 * h, 4.6 * h)
    s.ball("koi_head", "head", (0, hy, hz), head, "koi_white")
    s.box("tancho_crown", "head", (0, hy + head[1] * .5 - .1, hz + .2 * h), (2.6 * h, .4 * h, 2.6 * h), "koi_red", (0, 45, 0))
    front = hz - head[2] * .5
    s.box("pout_lips", "head", (0, hy - 1.2 * h, front - .5 * h), (1.8 * h, 1.1 * h, 1.0 * h), "lip")
    s.box("mouth", "head", (0, hy - 1.2 * h, front - 1.02 * h), (.9 * h, .45 * h, .1), "eye")
    s.eyes("head", 1.7 * h, hy + .55 * h, _front(head, (0, hy, hz), 1.7 * h, hy + .55 * h), .7 * h, gentle=True)
    for side in (-1, 1):
        w = [(side * .8 * h, hy - 1.3 * h, front - .6 * h), (side * 1.6 * h, hy - 2.2 * h, front - .2 * h), (side * 1.9 * h, hy - 3.0 * h, front + .6 * h)]
        _chain(s, "barbel", "head", w, .25 * h, "whisker", joints=False)
    span = (3.6, 6.0, 8.6)[s.age]
    for side, name in ((-1, "wing_l"), (1, "wing_r")):
        root = (side * 2.6 * k, by - 1.2 * k, -1.6 * k)
        s.bone(name, "body", root)
        # Butterfly-koi veils: a fan of fine flowing strands, not one plank.
        for j in range(2):
            dy = (j - .5) * span * .2
            mid = (side * (2.6 * k + span * .3), by - 1.6 * k - span * .1 + dy * .5, -1.6 * k + span * .5)
            end = (side * (2.6 * k + span * .42), by - 1.6 * k - span * .08 + dy * .6, -1.6 * k + span * 1.1)
            _chain(s, "veil_pectoral_fin", name, [root, mid, end], [span * .2, span * .15], ["fin" if j else "fin_light", "fin_light"],
                   depths=[.25, .22], joints=False)
    for i in range(3 + s.age):
        z = (-.6 + i * 1.6) * k
        height = (2.0 - abs(i - 1) * .35 + .3 * s.age) * k
        s.box("ribbon_dorsal_fin", "body", (0, by + 2.6 * k + height * .4, z + .8 * k), (.3, height, 2.0 * k), "fin" if i % 2 else "fin_light", (-30, 0, 0))
    s.bone("tail", "body", (0, by - .4 * k, 7.2 * k))
    tail_len = (3.6, 6.2, 9.0)[s.age]
    s.bone("tail_tip", "tail", (0, by - .4 * k, 7.6 * k + tail_len * .5))
    base = (0, by - .4 * k, 7.0 * k)
    strands = (3, 5, 7)[s.age]
    for j in range(strands):
        a = (j - (strands - 1) / 2) / ((strands - 1) / 2) * .75
        b = (math.sin(a * 2) * .3 * k, base[1] + math.sin(a) * tail_len * .42, base[2] + tail_len * .5)
        c = (math.sin(a * 3) * .6 * k, base[1] + math.sin(a) * tail_len * .8 - .3 * k, base[2] + tail_len * (.95 - .12 * abs(a)))
        s.beam("flowing_tail_veil", "tail", base, b, .25, "fin" if j % 2 else "fin_light", tail_len * .2)
        s.beam("flowing_tail_veil", "tail_tip", b, c, .22, "fin_light" if j % 2 else "fin", tail_len * .16)
    for side in (-1, 1):
        s.box("pelvic_fin", "body", (side * 1.6 * k, by - 2.6 * k, 3.0 * k), (.3, 1.6 * k, 2.2 * k), "fin", (-30, 0, side * 20))
    if s.age:
        for i in range(2 + 2 * s.age):
            s.box("scale_glint", "body", ((-1) ** i * 2.8 * k, by + (.6 - (i % 3) * .5) * k, (-1.0 + i * 1.3) * k), (.1, .5 * k, .5 * k), "scale_glint", (0, 0, 45))
    if s.age == 2:
        top = (0, hy + head[1] * .5 + .2, hz + .2 * h)
        for i in range(6):
            a = i * math.tau / 6
            s.box("lotus_petal", "head", (math.cos(a) * 1.2 * h, top[1] + .7 * h, top[2] + math.sin(a) * 1.2 * h), (1.0 * h, 1.8 * h, .5 * h),
                  "lotus" if i % 2 else "lotus_light", (math.sin(a) * 30, -math.degrees(a) + 90, 0))
        s.box("lotus_heart", "head", (0, top[1] + .5 * h, top[2]), (1.2 * h, .6 * h, 1.2 * h), "lotus_core", (0, 45, 0))
        for i, (x, y, z) in enumerate(((2.4, 2.2, -1.0), (3.4, 3.8, .2), (2.6, 5.4, 1.4))):
            s.box("rising_bubble", "head", (x * h, hy + y * h, hz + z * h), ((.9 - .2 * i) * h,) * 3, "bubble_glow")
    return s.finish()


def _peacock(stage):
    """A starlight peacock in royal blue with a crest of plumes. The peachick is a brown
    fluff; the adult opens a great fan of feathers whose eyes hold little stars."""
    s = Sculpt(stage, _palette(plume="#1F4FAE", plume_light="#2F7AD8", feather="#1E8270", train="#26A27C", train_dark="#176B57",
                               eye_gold="#E8C14A", eye_blue="#2A66D2", eye_core="#191338", star_glow="#FFF2B0",
                               beak="#C9C0B0", leg="#8C8798", face_white="#F4F2FF", eye="#2A1E12"))
    if s.age == 0:
        s.palette.update(plume="#B48E62", plume_light="#D2B48A", feather="#A27E58")
    k, h = s.scale, s.head_scale
    leg = 5.0
    by = leg * k + 3.0 * k
    s.bones["body"] = ("root", [0, by, 0])
    s.ball("plump_body", "body", (0, by, .4 * k), (5.6 * k, 5.8 * k, 7.6 * k), "plume")
    s.ball("breast", "body", (0, by + .6 * k, -2.6 * k), (4.6 * k, 5.0 * k, 3.2 * k), "plume_light")
    neck = [(0, by + 1.6 * k, -2.8 * k), (0, by + 4.8 * k, -3.6 * k), (0, by + 7.0 * k, -3.4 * k)]
    _chain(s, "slender_neck", "body", neck, [2.6 * k, 2.1 * k], "plume_light")
    hy, hz = by + 7.0 * k + 1.4 * h, -3.6 * k - .4 * h
    s.bone("head", "body", (0, hy - 1.6 * h, hz + .8 * h))
    head = (3.8 * h, 3.6 * h, 4.2 * h)
    s.ball("small_head", "head", (0, hy, hz), head, "plume_light")
    front = _front(head, (0, hy, hz), 1.2 * h, hy + .3 * h)
    s.eyes("head", 1.15 * h, hy + .3 * h, front, .62 * h, gentle=s.age == 0)
    if s.age:
        for side in (-1, 1):
            s.box("white_face_stripe", "head", (side * 1.9 * h, hy + .1 * h, hz - .6 * h), (.15, .5 * h, 2.4 * h), "face_white")
    s.box("beak", "head", (0, hy - .4 * h, front - .7 * h), (1.0 * h, .9 * h, 1.6 * h), "beak")
    crest = (2, 4, 5)[s.age]
    for i in range(crest):
        a = math.radians((i - (crest - 1) / 2) * 16)
        root = (0, hy + 1.6 * h, hz + .2 * h)
        tip = (math.sin(a) * 2.6 * h, hy + (1.6 + 2.8 * math.cos(a)) * h, hz + (.8 + .6 * s.age) * h)
        s.beam("crest_stalk", "head", root, tip, .2 * h, "leg")
        s.box("crest_tip", "head", tip, (.8 * h, .8 * h, .5 * h), "plume_light" if s.age else "plume", (0, 0, 45))
    for side, name in ((-1, "wing_l"), (1, "wing_r")):
        s.bone(name, "body", (side * 2.4 * k, by + 1.6 * k, -1.0 * k))
        s.ball("folded_wing", name, (side * 2.8 * k, by + .2 * k, .9 * k), (1.6 * k, 4.4 * k, 6.4 * k), "feather")
        for j in range(2 + s.age):
            s.box("barred_wing_feather", name, (side * 2.95 * k, by - 1.4 * k, (2.0 + j * 1.0) * k), (1.0 * k, .5 * k, 1.8 * k), "train_dark",
                  (-15, 0, side * 6))
    for side in (-1, 1):
        x = side * 1.5 * k
        leg_bone = s.bone("leg_l" if side < 0 else "leg_r", "body", (x, leg * k + .6 * k, .6 * k))
        s.beam("thin_leg", leg_bone, (x, leg * k + .6 * k, .6 * k), (x, .5 * k, .2 * k), .55 * k, "leg")
        for toe in (-1, 0, 1):
            s.box("toe", leg_bone, (x + toe * .45 * k, .25 * k, -.6 * k), (.3 * k, .3 * k, 1.5 * k), "leg", (0, toe * 22, 0))
    s.bone("tail", "body", (0, by + .4 * k, 3.8 * k))
    if s.age == 0:
        s.box("chick_tail_tuft", "tail", (0, by + .6 * k, 4.6 * k), (1.8 * k, 1.4 * k, 1.8 * k), "plume_light", (-25, 0, 0))
    else:
        feathers = (7, 11)[s.age - 1]
        spread = (55, 82)[s.age - 1]
        radius = (7.0, 13.0)[s.age - 1]
        base = (0, by + .6 * k, 4.0 * k)
        lean = math.radians(18)
        for ring, (count, r, mat) in enumerate(((feathers, radius, "train"), (feathers - 2, radius * .62, "train_dark"))):
            if s.age == 1 and ring == 1:
                continue
            for i in range(count):
                a = math.radians(-spread + 2 * spread * i / (count - 1))
                tip = (base[0] + math.sin(a) * r, base[1] + math.cos(a) * r * math.cos(lean), base[2] + math.cos(a) * r * math.sin(lean) + .6 * ring)
                s.beam("train_feather", "tail", base, tip, (1.0 if ring else 1.25), mat, .3)
                if ring == 0:
                    eye = (tip[0], tip[1], tip[2] - .4)
                    s.disc("feather_eye_gold", "tail", eye, 2.4, 2.8, .25, "eye_gold")
                    s.disc("feather_eye_blue", "tail", (eye[0], eye[1] - .1, eye[2] - .2), 1.5, 1.8, .2, "eye_blue")
                    s.box("feather_eye_star", "tail", (eye[0], eye[1] - .1, eye[2] - .4), (.7, .7, .12), "star_glow" if s.age == 2 else "eye_core", (0, 0, 45))
                    if s.age == 2 and i % 2 == 0:
                        s.box("starlight_mote", "tail", (eye[0] * 1.12, eye[1] + 1.4, eye[2] - .2), (.4, .4, .15), "star_glow", (0, 0, 45))
    return s.finish()


def _jelly(stage):
    """An aurora jellyfish drifting through the air: a translucent bell with a gentle face, a
    glowing heart, aurora-coloured frilled arms and fine trailing tentacles."""
    s = Sculpt(stage, _palette(bell="#A9BFFF", bell_light="#DCE6FF", rim_glow="#8FFFE3", heart_glow="#FFF0B5",
                               mint_glow="#86F7D2", pink_glow="#FFA6D9", violet_glow="#B69CFF", tentacle="#CBBBFF",
                               eye="#2A2140", pupil="#2A2140", blush="#FF9FCB"))
    k, h = s.scale, s.head_scale
    by = 7.6 * k + 3.0
    s.bones["body"] = ("root", [0, by, 0])
    s.bone("head", "body", (0, by, 0))
    layers = ((11.0, 2.6, 0), (9.6, 2.4, 2.0), (7.4, 2.0, 3.8), (4.4, 1.4, 5.2))
    for i, (w, hh, y) in enumerate(layers):
        s.ball("translucent_bell", "head", (0, by + y * k, 0), (w * k, hh * k, w * k), "bell" if i % 2 == 0 else "bell_light")
    for j in range(12):
        a = j * math.tau / 12
        s.box("glowing_rim_scallop", "head", (math.cos(a) * 5.2 * k, by - 1.2 * k, math.sin(a) * 5.2 * k), (2.0 * k, .8 * k, .7 * k), "rim_glow",
              (0, -math.degrees(a) + 90, 0))
    if s.age == 2:
        for j in range(4):
            a = j * math.tau / 4 + math.pi / 4
            s.box("clover_gonad_ring", "head", (math.cos(a) * 1.5 * k, by + 5.95 * k, math.sin(a) * 1.5 * k), (1.6 * k, .1, 1.6 * k), "heart_glow", (0, 45, 0))
    s.ball("glowing_heart", "body", (0, by - .9 * k, 0), (3.2 * k, 1.6 * k, 3.2 * k), "heart_glow")
    face_z = -4.8 * k
    ey = by + 1.6 * k
    eye = (1.7, 1.55, 1.45)[s.age] * h
    _dot_eyes(s, "head", 1.7 * h, ey, face_z, eye, pupil="pupil")
    _smile(s, "head", ey - 1.7 * h, face_z + .2, .9 * h)
    arms = (2, 3, 4)[s.age]
    length = (5.0, 8.0, 11.0)[s.age] * k
    mats = ("mint_glow", "pink_glow", "violet_glow")
    for i in range(arms):
        a = i * math.tau / arms + math.pi / 4
        bone = "tail" if i == 0 else f"tail_{i + 1}"
        root = (math.cos(a) * 1.0 * k, by - 1.2 * k, math.sin(a) * 1.0 * k)
        s.bone(bone, "body", root)
        steps = 4 + s.age
        pts = [root]
        for j in range(1, steps + 1):
            t = j / steps
            frill = .9 * k * (1 if j % 2 else -1)
            pts.append((root[0] + math.cos(a + math.pi / 2) * frill + math.cos(a) * t * 1.4 * k, root[1] - t * length,
                        root[2] + math.sin(a + math.pi / 2) * frill + math.sin(a) * t * 1.4 * k + t * 1.6 * k))
        _chain(s, "aurora_frilled_arm", bone, pts, [(1.5 - .9 * j / steps) * k for j in range(steps)], [mats[(i + j) % 3] for j in range(steps)],
               depths=[.4 * k] * steps)
    strands = (4, 6, 8)[s.age]
    for j in range(strands):
        a = (j + .5) * math.tau / strands
        bone = "tail" if j % arms == 0 else f"tail_{j % arms + 1}"
        root = (math.cos(a) * 4.6 * k, by - 1.4 * k, math.sin(a) * 4.6 * k)
        drop = length * (.8 + .3 * (j % 3))
        pts = [root, (root[0] * .96, root[1] - drop * .5, root[2] + 1.2 * k), (root[0] * .9, root[1] - drop, root[2] + 2.8 * k)]
        _chain(s, "fine_tentacle", bone, pts, .3 * k, "tentacle", joints=False)
        s.box("tentacle_spark", bone, pts[-1], (.5 * k, .5 * k, .5 * k), mats[j % 3])
    return s.finish()


_BUILDERS = {
    "mochi_slime": _mochi,
    "cloud_lamb": _lamb,
    "moon_rabbit": _rabbit,
    "bloom_hedgehog": _hedgehog,
    "coral_axolotl": _axolotl,
    "clover_bumble": _bumble,
    "aurora_penguin": _penguin,
    "storm_raptor": _raptor,
    "magma_rhino": _rhino,
    "eclipse_scorpion": _scorpion,
    "raijin_tiger": _tiger,
    "titan_beetle": _beetle,
    "anubis_jackal": _jackal,
    "bone_drake": _drake,
    "jade_lung": _lung,
    "crystal_stag": _stag,
    "luna_moth": _moth,
    "celestial_koi": _koi,
    "starlight_peacock": _peacock,
    "aurora_jelly": _jelly,
}
SPECIES = tuple(_BUILDERS)


def build(species, stage):
    """Return the sculpture contract consumed by the shared pet pack exporter."""
    try:
        builder = _BUILDERS[species]
    except KeyError as exc:
        raise ValueError(f"Unknown menagerie pet species: {species}") from exc
    return builder(stage)
