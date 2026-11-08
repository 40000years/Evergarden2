"""Authored Blockbench sculptures for Evergarden's five mythical companions (concept set v2).

Dimensions are Blockbench pixels (16 px = one block) at the final in-game size. Each age has
its own anatomy: the young are round, big-eyed and soft; adults are majestic, with layered
plumage, flames, crystals and glowing runes. Parts attach to named animation bones that
pet_model_engine animates. Palette colours may be vertical gradients '#tip>#base' (pet_paint):
on a link or blade the top of each side face is its far end.
"""

from math import atan2, cos, degrees, pi, radians, sin, sqrt


SPECIES = ("aetherius", "ignis_draconis", "zephyros", "solaris", "fenrir")
STAGES = ("baby", "medium", "adult")
NAMES = {
    "aetherius": "Aetherius · Celestial Unicorn",
    "ignis_draconis": "Ignis Draconis · Primordial Flame Dragon",
    "zephyros": "Zephyros · Storm Griffin",
    "solaris": "Solaris · Eternal Sun Phoenix",
    "fenrir": "Fenrir · Void Wolf",
}


def lerp(a, b, t):
    return [a[i] + (b[i] - a[i]) * t for i in range(3)]


def bezier(a, c, b, t):
    """Quadratic curve through a and b, pulled towards control c."""
    return [(1 - t) ** 2 * a[i] + 2 * (1 - t) * t * c[i] + t * t * b[i] for i in range(3)]


def polar(origin, length, yaw, pitch):
    """Point at `length` from origin: yaw turns from +Y towards +X, pitch tips towards +Z."""
    y, p = radians(yaw), radians(pitch)
    return [origin[0] + length * sin(y) * cos(p), origin[1] + length * cos(y) * cos(p), origin[2] + length * sin(p)]


def curve(points, steps=3):
    """A smooth Catmull-Rom path through the control points (necks, tails, plumes, manes, wind)."""
    pts = [points[0]] + [list(p) for p in points] + [points[-1]]
    out = []
    for i in range(1, len(pts) - 2):
        p0, p1, p2, p3 = pts[i - 1], pts[i], pts[i + 1], pts[i + 2]
        for k in range(steps):
            t = k / steps
            out.append([.5 * (2 * p1[j] + (p2[j] - p0[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t * t
                              + (3 * p1[j] - p0[j] - 3 * p2[j] + p3[j]) * t ** 3) for j in range(3)])
    out.append(list(points[-1]))
    return out


def unit(v):
    n = sqrt(sum(c * c for c in v)) or 1.0
    return [c / n for c in v]


class Sculpt:
    def __init__(self, species, stage, palette):
        self.species, self.stage = species, stage
        self.palette, self.parts = palette, []
        self.bones = {"root": (None, [0, 0, 0])}
        self.counter = 0

    def bone(self, name, parent, pivot):
        self.bones[name] = (parent, [round(v, 4) for v in pivot])
        return name

    def box(self, bone, name, center, size, material, rotation=None, origin=None):
        self.counter += 1
        part = {"bone": bone, "name": "%s_%03d" % (name, self.counter),
                "from": [round(center[i] - size[i] / 2, 4) for i in range(3)],
                "size": [round(max(v, .1), 4) for v in size], "material": material}
        if rotation and any(abs(v) > 1e-4 for v in rotation):
            part["rotation"] = [round(v, 4) for v in rotation]
            part["origin"] = [round(v, 4) for v in (origin or center)]
        self.parts.append(part)

    def link(self, bone, name, start, end, width, depth, material, extend=0.0):
        delta = [end[i] - start[i] for i in range(3)]
        length = sqrt(sum(v * v for v in delta))
        if length < .05:
            return
        center = [(start[i] + end[i]) / 2 for i in range(3)]
        rotation = [degrees(atan2(delta[2], sqrt(delta[0] ** 2 + delta[1] ** 2))), 0, -degrees(atan2(delta[0], delta[1]))]
        self.box(bone, name, center, (width, length + extend, depth), material, rotation)

    def blade(self, bone, name, base, tip, width, depth, materials, bend=(0, 0, 0), steps=3, taper=.75, round_end=True):
        """A curved, tapering strip (feather, flame tongue, fur lock, crystal) built from overlapping links."""
        if isinstance(materials, str):
            materials = [materials] * steps
        jitter = ((self.counter * 37) % 9 - 4) * .013
        base, tip = [base[0], base[1], base[2] + jitter], [tip[0], tip[1], tip[2] + jitter]
        control = [(base[i] + tip[i]) / 2 + bend[i] for i in range(3)]
        for i in range(steps):
            a, b = i / steps, min(1.0, (i + 1.15) / steps)
            p, q = bezier(base, control, tip, a), bezier(base, control, tip, b)
            k = (1 - a * taper) if round_end else (1 - a * taper * .8)
            self.link(bone, name, p, q, max(.25, width * k), max(.2, depth * (1 - a * taper * .5)),
                      materials[min(i, len(materials) - 1)])

    def chain(self, bone, name, points, widths, depths, materials):
        """Thick body segments (necks, tails) through a list of points."""
        for i in range(len(points) - 1):
            w = widths[i] if isinstance(widths, (list, tuple)) else widths
            d = depths[i] if isinstance(depths, (list, tuple)) else depths
            m = materials[i] if isinstance(materials, (list, tuple)) else materials
            self.link(bone[i] if isinstance(bone, (list, tuple)) else bone, name, points[i], points[i + 1], w, d, m, extend=min(w, d) * .55)

    def ribbon(self, bone, name, points, width, depth, materials, steps=3, taper=.6, split=None, end_width=None):
        """A flowing strand through control points: tapering links along a smooth curve."""
        if isinstance(materials, str):
            materials = [materials]
        jitter = ((self.counter * 37) % 9 - 4) * .013
        samples = curve(points, steps)
        n = len(samples) - 1
        for i in range(n):
            f = i / n
            w = width + ((end_width if end_width is not None else width * (1 - taper)) - width) * f
            d = max(.2, depth * (1 - taper * .5 * f))
            a, b = samples[i], samples[i + 1]
            self.link(split[0] if split and f >= split[1] else bone, name, [a[0], a[1], a[2] + jitter],
                      [b[0], b[1], b[2] + jitter], max(.25, w), d, materials[min(len(materials) - 1, int(f * len(materials)))],
                      extend=min(w, d) * .5)

    def plate(self, bone, name, at, normal, size, material):
        n = unit(normal)
        half = size[1] / 2
        self.link(bone, name, [at[i] - n[i] * half for i in range(3)], [at[i] + n[i] * half for i in range(3)],
                  size[0], size[2], material)

    def orb(self, bone, name, center, size, material, rotation=None, round_=.62):
        w, h, d = size
        pivot = center
        self.box(bone, name, center, (w * .88, h * .88, d * .88), material, rotation, pivot)
        self.box(bone, name + "_x", center, (w, h * round_, d * round_), material, rotation, pivot)
        self.box(bone, name + "_y", center, (w * round_, h, d * round_), material, rotation, pivot)
        self.box(bone, name + "_z", center, (w * round_, h * round_, d), material, rotation, pivot)

    def feather_patch(self, bone, name, top, width, rows, cols, length, feather_w, materials, z_step=.32,
                      drop=None, lean=.3, row_scale=1.0):
        drop = drop if drop is not None else length * .62
        for r in range(rows):
            n = cols - (r % 2)
            scale = row_scale ** r
            for c in range(n):
                x = top[0] + (c - (n - 1) / 2) * width / max(1, cols - 1 if cols > 1 else 1)
                base = (x, top[1] - r * drop, top[2] + r * z_step + (c % 2) * .09)
                tip = (x * (1 + lean * .1), base[1] - length * scale, base[2] + lean)
                self.blade(bone, name, base, tip, feather_w * scale, .45, materials, steps=2, taper=.72)

    def gem(self, bone, name, center, size, material, setting=None, rotation=(0, 0, 45)):
        if setting:
            self.box(bone, name + "_setting", center, (size * 1.45, size * 1.45, size * .45), setting, rotation)
        self.box(bone, name, [center[0], center[1], center[2] - size * .2], (size, size, size * .55), material, rotation)

    def leg(self, name, hip, knee, ankle, upper, lower, coat, foot, foot_mat, claw_mat=None, claws=3,
            thigh=None, lower_mat=None, cuff=None, parent="body"):
        bone = self.bone(name, parent, hip)
        self.link(bone, name + "_upper", hip, knee, upper, upper * 1.05, coat, extend=upper * .5)
        if thigh:
            self.orb(bone, name + "_thigh", thigh[0], thigh[1], coat, round_=.62)
        low = self.bone(name + "_lower", bone, knee)
        self.link(low, name + "_shin", knee, ankle, lower, lower, lower_mat or coat, extend=lower * .45)
        fw, fh, fd = foot
        center = (ankle[0], fh / 2, ankle[2] - fd * .18)
        self.orb(low, name + "_foot", center, foot, foot_mat, round_=.66)
        if cuff:
            self.box(low, name + "_cuff", (ankle[0], fh + .55, ankle[2]), (lower * 1.3, .8, lower * 1.3), cuff)
        if claw_mat:
            for c in range(claws):
                x = ankle[0] + (c - (claws - 1) / 2) * fw * .3
                root = (x, fh * .45, center[2] - fd * .45)
                self.blade(low, name + "_claw", root, (x, .1, root[2] - fd * .32), fw * .2, fw * .2,
                           [claw_mat], steps=2, taper=.7)
        return bone, low

    def eyes(self, bone, width, y, z, size, iris="eye", tall=1.18, yaw=24, spread=.31, lid=None, pupil=.5):
        for side in (-1, 1):
            x = side * width * spread
            rot, piv = (0, -side * yaw, 0), (x, y, z)
            h = size * tall
            self.box(bone, "eye_socket", (x, y, z + .02), (size * 1.16, h * .84, .3), "eye_rim", rot, piv)
            self.box(bone, "eye_socket", (x, y, z + .02), (size * .84, h * 1.12, .3), "eye_rim", rot, piv)
            self.box(bone, "eye_iris", (x, y, z - .14), (size * .98, h * .72, .16), iris, rot, piv)
            self.box(bone, "eye_iris", (x, y, z - .14), (size * .72, h * .96, .16), iris, rot, piv)
            self.box(bone, "eye_pupil", (x - side * size * .03, y + h * .02, z - .24),
                     (size * pupil, h * (.58 if pupil >= .4 else .78), .1), "pupil", rot, piv)
            self.box(bone, "eye_shine", (x - side * size * .2, y + h * .22, z - .32),
                     (size * .3, size * .3, .08), "shine", rot, piv)
            self.box(bone, "eye_shine_small", (x + side * size * .2, y - h * .24, z - .32),
                     (size * .15, size * .15, .08), "shine", rot, piv)
            if lid:
                self.box(bone, "eye_brow", (x, y + size * tall * .62, z - .18), (size * 1.05, size * .2, .3), lid,
                         (0, -side * yaw, side * -10), piv)

    def fierce_eye(self, bone, side, center, w, h, yaw, iris="eye_glow", socket="eye_socket", slit=True, slant=10):
        rot = (0, -side * yaw, side * slant)
        self.box(bone, "eye_socket", center, (w * 1.35, h * 1.6, .45), socket, rot, center)
        self.box(bone, "eye_glow", (center[0], center[1], center[2] - .14), (w, h, .3), iris, rot, center)
        if slit:
            self.box(bone, "eye_slit", (center[0], center[1], center[2] - .26), (w * .2, h * 1.05, .2), "pupil", rot, center)
        self.box(bone, "eye_shine", (center[0], center[1] + h * .22, center[2] - .3), (w * .18, h * .3, .12), "shine", rot, center)

    def sheet(self, bone, apex, a, b, material, rows=6, z=0.0, sag=.1):
        reach = max(sqrt(sum((a[i] - apex[i]) ** 2 for i in range(3))), sqrt(sum((b[i] - apex[i]) ** 2 for i in range(3))))
        for r in range(rows):
            t = (r + .55) / rows
            pa, pb = lerp(apex, a, t), lerp(apex, b, t)
            mid = lerp(lerp(pa, pb, .5), apex, sag * t * t)
            off = z + r * .035
            for p, q in ((pa, mid), (mid, pb)):
                self.link(bone, "membrane", [p[0], p[1], p[2] + off], [q[0], q[1], q[2] + off],
                          reach / rows * 1.45, .3, material, extend=reach / rows * .6)

    def ring(self, bone, name, center, radius, count, thickness, material, tilt=0.0, phase=0.0):
        pts = []
        for i in range(count):
            a = radians(phase + 360 * i / count)
            pts.append([center[0] + radius * cos(a), center[1] + radius * sin(a) * cos(radians(tilt)),
                        center[2] + radius * sin(a) * sin(radians(tilt))])
        for i in range(count):
            self.link(bone, name, pts[i], pts[(i + 1) % count], thickness, thickness, material, extend=thickness * .6)

    def finish(self, units=1.0):
        if units != 1.0:
            self.bones = {n: (p, [round(v * units, 5) for v in pivot]) for n, (p, pivot) in self.bones.items()}
            for part in self.parts:
                for key in ("from", "size", "origin"):
                    if key in part:
                        part[key] = [round(v * units, 5) for v in part[key]]
        top = max(p["from"][1] + p["size"][1] for p in self.parts)
        return {"bones": self.bones, "parts": self.parts, "palette": self.palette,
                "height": top / 16, "name": NAMES[self.species], "stage": self.stage}


# ---- Solaris · Eternal Sun Phoenix ------------------------------------------------------------
SOLARIS = {
    "plume": "#FFE45E>#FF9418", "plume_warm": "#FFA024>#E83E0C", "plume_deep": "#E0380E>#8C1208",
    "belly_feather": "#FFFDF4>#FFE89C", "crest_feather": "#FF4E16>#FFF078", "covert_feather": "#FFF294>#FFAC28",
    "flame_glow": "#FFF898>#FFA224>#E83808", "flame_core_glow": "#FFFFE0>#FFD84A",
    "ember_glow": "#FF8820", "sun_glow": "#FFFFD8>#FFD848", "ray_glow": "#FFFFE6>#FFB228",
    "gold": "#FFE474>#D4921C", "gold_dark": "#B87414", "beak": "#FFE868>#E89618", "beak_tip": "#D47210",
    "talon": "#E8A428>#985410", "eye": "#2898E8>#88EEFF", "eye_rim": "#2E1008", "pupil": "#0C1828",
    "shine": "#FFFFFF", "gem": "#FF3868", "blush": "#FF947E",
    "plume_cream": "#FFFDF6>#FFE8A0", "plume_gold": "#FFF078>#FFA81C", "breast_cream": "#FFFFFF>#FFF2C2",
    "leg_gold": "#F4AC2C>#A85814", "eye_dark": "#FFFCE0>#FF9E18", "eye_socket": "#481408",
}


def _phoenix_wing(s, stage, side, shoulder, k):
    """Wide, arched multi-layered phoenix wing matching the reference concept."""
    tag = "l" if side < 0 else "r"
    arm = s.bone("wing_" + tag, "body", shoulder)
    wrist = [shoulder[0] + side * k["arm"], shoulder[1] + k["arm"] * k["lift"], shoulder[2] + k["arm"] * .18]
    tip_bone = s.bone("wingtip_" + tag, arm, wrist)
    hand = [wrist[0] + side * k["arm"] * .85, wrist[1] + k["arm"] * .38, wrist[2] + k["arm"] * .15]
    s.link(arm, "wing_arm", shoulder, wrist, k["arm_w"], k["arm_w"], "plume_gold", extend=k["arm_w"] * .6)
    s.link(tip_bone, "wing_hand", wrist, hand, k["arm_w"] * .82, k["arm_w"] * .82, "plume", extend=k["arm_w"] * .5)
    s.link(arm, "wing_leading_gold", [shoulder[0], shoulder[1] + k["arm_w"] * .45, shoulder[2] - .25],
           [wrist[0], wrist[1] + k["arm_w"] * .45, wrist[2] - .25], k["arm_w"] * .36, k["arm_w"] * .55, "sun_glow")
    n = k["primaries"]
    for i in range(n):
        t = i / max(1, n - 1)
        root = lerp(wrist, hand, 1.0 - .82 * t)
        root = [root[0], root[1] - .3 * t, root[2] + .12 * i]
        yaw = side * (k["fan"][0] + (k["fan"][1] - k["fan"][0]) * t)
        length = k["primary_len"] * (1.05 - .28 * abs(t - .28))
        tip = polar(root, length, yaw, 10)
        s.blade(tip_bone, "wing_primary", root, tip, k["feather_w"] * 1.15, .55,
                ["plume_warm", "plume_deep", "plume_gold", "flame_glow"], bend=(0, 0, .7), steps=4, taper=.38)
    for i in range(k["secondaries"]):
        t = (i + .4) / k["secondaries"]
        root = lerp(shoulder, wrist, .12 + .85 * t)
        root = [root[0], root[1] - .4, root[2] + .28 + .1 * i]
        yaw = side * (k["fan"][1] - 12 + 24 * (1 - t))
        tip = polar(root, k["secondary_len"] * (.82 + .24 * t), yaw, 12)
        s.blade(arm, "wing_secondary", root, tip, k["feather_w"] * 1.1, .55,
                ["plume", "plume_warm", "plume_deep", "plume_gold"], bend=(0, 0, .5), steps=4, taper=.4)
    for row, (count, length, mats) in enumerate(((k["coverts"] + 1, k["covert_len"], ["plume_gold", "plume"]),
                                                 (k["coverts"] + 2, k["covert_len"] * .65, ["plume_cream", "plume_gold"]))):
        for i in range(count):
            t = (i + .4) / count
            root = lerp(shoulder, hand, .06 + .86 * t)
            root = [root[0], root[1] + .3 - row * .45, root[2] - .25 - row * .25]
            yaw = side * (145 - 55 * t)
            tip = polar(root, length * (1 - .15 * t), yaw, 6)
            s.blade(tip_bone if t > .55 else arm, "wing_covert", root, tip, k["covert_w"] * 1.15, .55, mats, steps=2, taper=.35)


def _solaris_young(stage):
    s = Sculpt("solaris", stage, SOLARIS)
    k = dict(by=8.2, bw=9.6, bh=9.4, bd=9.4, hy=15.4, hz=-1.6, hs=9.6, eye=2.6,
             arm=4.4, arm_w=2.2, lift=.42, fan=(58, 152), primaries=6, primary_len=6.8, feather_w=2.4,
             secondaries=4, secondary_len=5.2, coverts=3, covert_len=3.4, covert_w=2.4,
             crest=5, crest_len=5.8, tail=5, tail_len=7.4, leg=2.2)
    by, bw, bh, bd = k["by"], k["bw"], k["bh"], k["bd"]
    s.bone("body", "root", (0, by, 0))
    s.orb("body", "phoenix_body", (0, by, .4), (bw, bh, bd), "plume", round_=.64)
    front = .4 - bd / 2
    s.orb("body", "breast", (0, by - bh * .04, front + bd * .12), (bw * .82, bh * .82, bd * .36), "breast_cream", round_=.64)
    s.feather_patch("body", "breast_feather", (0, by + bh * .34, front - .6), bw * .64, 3, 4,
                    bh * .3, bw * .26, ["breast_cream", "plume_cream", "plume_gold"], z_step=.26, row_scale=.94)
    for side in (-1, 1):
        for i in range(3):
            top = (side * bw * .48, by + bh * (.26 - .2 * i), .4 - bd * .18 + i * bd * .2)
            s.blade("body", "flank_feather", top, (side * bw * .6, top[1] - bh * .32, top[2] + bd * .16), bd * .26, .55,
                    ["plume_gold", "plume_warm", "plume_deep"], steps=2, taper=.6)
    neck_base = (0, by + bh * .38, -bd * .06)
    s.bone("neck", "body", neck_base)
    hy, hz, hs = k["hy"], k["hz"], k["hs"]
    hd = hs * .92
    s.orb("neck", "neck", (0, (neck_base[1] + hy - hs * .3) / 2, (neck_base[2] + hz) / 2),
          (bw * .56, hy - hs * .3 - neck_base[1] + 2.2, bw * .58), "plume_gold", round_=.62)
    s.bone("head", "neck", (0, hy - hs * .35, hz))
    s.orb("head", "head", (0, hy, hz), (hs, hs * .94, hd), "plume", round_=.64)
    face = hz - hd / 2
    s.orb("head", "face_down", (0, hy - hs * .18, face + hd * .12), (hs * .8, hs * .52, hd * .34), "breast_cream", round_=.62)
    for side in (-1, 1):
        s.blade("head", "brow_plume", (side * hs * .16, hy + hs * .34, face - .1),
                (side * hs * .52, hy + hs * .58, hz + hd * .15), hs * .22, .5, ["plume_gold", "crest_feather"], steps=2)
        s.blade("head", "cheek_tuft", (side * hs * .44, hy - hs * .08, hz),
                (side * hs * .68, hy - hs * .3, hz + hd * .32), hs * .24, .55, ["plume_gold", "plume_warm"], steps=2)
        s.box("head", "blush", (side * hs * .32, hy - hs * .18, face - .12), (hs * .16, hs * .08, .2), "blush")
    s.eyes("head", hs, hy + hs * .06, face - .16, k["eye"], spread=.26, yaw=14)
    beak_y = hy - hs * .1
    s.box("head", "beak", (0, beak_y, face - hs * .08), (hs * .24, hs * .18, hs * .22), "beak", (12, 0, 0))
    s.blade("head", "beak_hook", (0, beak_y, face - hs * .16), (0, beak_y - hs * .18, face - hs * .28),
            hs * .18, hs * .15, ["beak", "beak_tip"], steps=2)
    for i in range(k["crest"]):
        t = i / max(1, k["crest"] - 1)
        base = ((t - .5) * hs * .42, hy + hs * .38, hz + hs * (.02 + .14 * abs(t - .5)))
        tip = polar(base, k["crest_len"] * (1.12 - .32 * abs(t - .5)), (t - .5) * 48, 38 + 20 * abs(t - .5))
        s.blade("head", "crest_plume", base, tip, hs * .22, .55,
                ["plume_gold", "crest_feather", "plume_deep", "flame_glow"], bend=(0, 1.4, .4), steps=4, taper=.65)
    for side in (-1, 1):
        _phoenix_wing(s, stage, side, (side * bw * .38, by + bh * .24, bd * .06), k)
    root = (0, by - bh * .16, bd * .4)
    s.bone("tail", "body", root)
    mid = (0, by - bh * .24, root[2] + k["tail_len"] * .38)
    s.bone("tail_tip", "tail", mid)
    for i in range(k["tail"]):
        t = i / max(1, k["tail"] - 1)
        x = (t - .5) * bw * .75
        length = k["tail_len"] * (1.05 - .32 * abs(t - .5) * 2)
        first = (x * .65, mid[1] + (.5 - abs(t - .5)) * 1.0, root[2] + length * .38)
        s.blade("tail", "tail_plume", (x * .3, root[1], root[2] - .1 + .08 * i), first, k["feather_w"], .55,
                ["plume_gold", "plume_warm"], steps=2, taper=.2)
        end = (x * 1.4, first[1] - length * .18, first[2] + length * .62)
        s.blade("tail_tip", "tail_flame_plume", first, end, k["feather_w"] * .95, .5,
                ["plume_warm", "plume_deep", "plume_gold", "flame_glow"], bend=(0, length * .12, 0), steps=4, taper=.55)
    for side in (-1, 1):
        hip = (side * bw * .22, by - bh * .3, .2)
        leg = s.bone("leg_" + ("l" if side < 0 else "r"), "body", hip)
        foot = (side * bw * .22, 1.0, -.4)
        s.orb(leg, "feathered_thigh", (side * bw * .22, hip[1] - .9, .0), (2.6, 2.8, 2.6), "plume_warm", round_=.6)
        s.link(leg, "shank", (foot[0], hip[1] - 1.4, foot[2]), foot, 1.1, 1.1, "leg_gold")
        for toe in (-1, 0, 1):
            s.blade(leg, "talon", foot, (foot[0] + toe * .95, .1, foot[2] - 1.5), .65, .65, ["talon", "gold_dark"], steps=2)
    # Baby also wears the signature radiant golden Sun Halo behind its head/shoulders.
    halo = s.bone("halo", "body", (0, by + bh * .55, bd * .45))
    center = (0, hy + .8, hz + hs * .78)
    radius = hs * .78
    s.ring(halo, "sun_halo", center, radius, 16, .65, "sun_glow")
    s.ring(halo, "sun_halo_inner", center, radius * .78, 12, .42, "ray_glow")
    for i in range(12):
        a = radians(90 + 360 * i / 12)
        reach = radius * (1.42 if i % 3 == 0 else 1.22)
        inner = [center[0] + radius * 1.02 * cos(a), center[1] + radius * 1.02 * sin(a), center[2] + .15]
        outer = [center[0] + reach * cos(a), center[1] + reach * sin(a), center[2] + .15]
        s.blade(halo, "sun_ray", inner, outer, 1.0 if i % 3 == 0 else .75, .38, ["ray_glow", "sun_glow"], steps=2, taper=.88)
    return s.finish(.68)


def _flame_wing(s, side, shoulder, wrist, hand, length, count):
    """Wide, arched multi-layered phoenix wing matching the reference concept."""
    tag = "l" if side < 0 else "r"
    arm = s.bone("wing_" + tag, "body", shoulder)
    tip_bone = s.bone("wingtip_" + tag, arm, wrist)
    s.link(arm, "wing_arm", shoulder, wrist, 3.0, 2.8, "plume_gold", extend=1.8)
    s.link(tip_bone, "wing_hand", wrist, hand, 2.4, 2.2, "plume", extend=1.4)
    s.link(arm, "wing_gold_ridge", [shoulder[0], shoulder[1] + 1.2, shoulder[2] - .4],
           [wrist[0], wrist[1] + 1.1, wrist[2] - .4], 1.2, 1.4, "sun_glow", extend=1.0)
    s.link(tip_bone, "wing_hand_ridge", [wrist[0], wrist[1] + 1.0, wrist[2] - .3],
           [hand[0], hand[1] + .8, hand[2] - .3], 1.0, 1.2, "sun_glow", extend=.8)

    def along(u):
        return lerp(shoulder, wrist, u / .55) if u < .55 else lerp(wrist, hand, (u - .55) / .45)

    def reach(u):
        return length * (.68 + .38 * sin(min(1.0, u / .82) * pi / 2)) * (1 - .08 * max(0.0, u - .82) / .18)

    # Three lush overlapping feather tiers: long crimson-to-gold primaries, fiery orange-red secondaries,
    # and bright golden upper/inner coverts.
    rows = (
        ("wing_flight", count + 4, 1.0, .2, 4.4, ["plume_warm", "plume_deep", "plume_gold", "flame_glow"], 4),
        ("wing_covert", count + 2, .64, -.25, 4.5, ["plume_gold", "plume_warm", "plume_deep"], 3),
        ("wing_lesser", count, .36, -.65, 4.2, ["plume_cream", "plume_gold", "plume"], 2),
    )
    for name, n, scale, z, width, mats, steps in rows:
        for i in range(n):
            u = (i + .25) / (n - .3)
            root = along(u)
            root = [root[0], root[1] - (1 - scale) * .5, root[2] + z + .09 * i]
            # Smooth fan from downward/backward near the body to sweeping outward/upward at the wingtip.
            yaw = side * (162 - 96 * u ** .95)
            tip = polar(root, reach(u) * scale, yaw, 14)
            bone = arm if u < .55 else tip_bone
            s.blade(bone, name, root, tip, width, .55, mats, bend=(0, 0, .55), steps=steps, taper=.42)
            if scale == 1.0 and i % 2 == 0:
                s.blade(bone, "wing_flame", tip, polar(tip, 2.8 + 1.2 * u, yaw + side * 6, 12), width * .52, .36,
                        ["flame_glow", "flame_core_glow"], steps=2, taper=.85)


def _solaris_grown(stage):
    """Eternal Sun Phoenix: wide arched golden-crimson wings, plush cream diamond breast, noble flame-crested
    head, radiant double solar halo, and a wide fan of flowing tail plumes."""
    s = Sculpt("solaris", stage, SOLARIS)
    adult = stage == "adult"
    s.bone("body", "root", (0, 17.5, 1.0))
    s.orb("body", "belly", (0, 15.6, 2.4), (9.8, 9.2, 10.0), "plume_warm", round_=.62)
    s.orb("body", "chest", (0, 20.6, -.2), (10.8, 10.4, 9.8), "plume_gold", round_=.62)
    s.orb("body", "mantle", (0, 20.2, 3.6), (9.6, 9.6, 7.0), "plume_warm", round_=.62)
    # Diamond-layered white/cream chest plumage matching the reference screenshot.
    s.orb("body", "breast_core", (0, 20.0, -3.4), (6.8, 7.8, 3.8), "breast_cream", round_=.64)
    for row, (cols, width, mat) in enumerate((
        (3, 4.6, ["breast_cream", "plume_cream"]),
        (4, 6.4, ["breast_cream", "breast_cream", "plume_cream"]),
        (5, 7.6, ["breast_cream", "plume_cream", "plume_gold"]),
        (4, 6.2, ["plume_cream", "plume_gold", "plume_warm"]),
        (3, 4.4, ["plume_gold", "plume_warm", "plume_deep"]),
    )):
        y_top = 24.0 - row * 1.95
        z_top = -4.5 + abs(row - 2) * .25
        for c in range(cols):
            u = (c - (cols - 1) / 2) / max(1, (cols - 1) / 2 if cols > 1 else 1)
            x = u * width * .5
            base = (x, y_top - (1 - abs(u)) * .45, z_top - (1 - abs(u)) * .35)
            tip = (x * .95, base[1] - 3.6, base[2] + .35)
            s.blade("body", "breast", base, tip, 2.8, .55, mat, steps=2, taper=.65)
    for i, (y, z) in enumerate(((21.4, .4), (18.2, 1.8), (15.2, 3.2))):
        for side in (-1, 1):
            for dz in (-1.6, 1.2):
                root = (side * 5.0, y + .8, z + dz)
                s.blade("body", "flank_feather", root, (side * 6.0, y - 3.8, z + dz + 2.6), 3.6, .65,
                        ["plume_gold", "plume_warm", "plume_deep"], steps=2, taper=.55)
    # Graceful S-curved phoenix neck with lush shingled golden hackles.
    if adult:
        neck = [(0, 22.8, -1.8), (0, 26.0, -5.4), (0, 28.8, -8.4), (0, 30.8, -10.8)]
    else:
        neck = [(0, 23.4, -.8), (0, 27.0, -2.4), (0, 30.2, -1.8), (0, 31.4, -2.8)]
    s.bone("neck", "body", neck[0])
    path = curve(neck, 2)
    s.chain("neck", "neck", path, [5.6 - .18 * i for i in range(len(path) - 1)],
            [5.8 - .18 * i for i in range(len(path) - 1)], "plume_gold")
    for i, p in enumerate(path[:-1]):
        f = i / max(1, len(path) - 2)
        s.blade("neck", "throat_plume", (0, p[1] + .8, p[2] - 2.3), (0, p[1] - 2.4, p[2] - 2.7),
                3.8 * (1 - .18 * f), .6, ["breast_cream", "plume_cream", "plume_gold"], steps=2, taper=.48)
        for side in (-1, 1):
            for row, yaw_off in ((0, .4), (1, 1.4)):
                s.blade("neck", "hackle", (side * (2.2 + row * .5), p[1] + 1.0 - row * .4, p[2] - .4 + row * 1.2),
                        (side * (3.4 + row * .6), p[1] - 2.8, p[2] + 1.6 + yaw_off), 2.8, .55,
                        ["plume_gold", "plume_warm", "plume_deep"], steps=2, taper=.55)
    top = neck[-1]
    s.bone("head", "neck", top)
    H = (0, top[1] + (1.6 if adult else 2.0), top[2] - 1.4)

    def hp(dx, dy, dz):
        return (H[0] + dx, H[1] + dy, H[2] + dz)

    s.orb("head", "skull", hp(0, 0, 0), (5.8, 5.4, 6.4), "plume_gold", round_=.62)
    s.orb("head", "face", hp(0, -.7, -1.6), (5.2, 3.6, 4.2), "plume", round_=.62)
    s.orb("head", "throat_cream", hp(0, -1.8, -1.2), (4.2, 2.4, 3.6), "breast_cream", round_=.6)
    s.box("head", "cere", hp(0, .2, -3.4), (2.4, 1.6, 1.4), "beak")
    s.blade("head", "beak", hp(0, .4, -3.6), hp(0, -2.0, -6.8), 2.3, 2.1, ["beak", "beak", "beak_tip"],
            bend=(0, 1.3, -.8), steps=3, taper=.78)
    s.link("head", "beak_lower", hp(0, -1.0, -3.2), hp(0, -1.7, -5.2), 1.6, .8, "beak_tip")
    for side in (-1, 1):
        s.fierce_eye("head", side, hp(side * 2.45, .5, -2.1), 1.35, .95, 34, iris="eye_dark", slit=False, slant=12)
        s.blade("head", "brow_crest", hp(side * 1.8, 1.4, -2.8), hp(side * 3.8, 3.4, 1.8), 1.6, .6,
                ["plume_gold", "crest_feather", "flame_glow"], bend=(side * .4, .8, 0), steps=3, taper=.65)
        s.blade("head", "cheek_plume", hp(side * 2.4, -.6, -.2), hp(side * 4.2, -2.2, 3.8), 2.0, .6,
                ["plume_gold", "plume_warm", "plume_deep"], steps=2, taper=.58)
    # Swept-back royal crown of fiery crest plumes.
    for i in range(7):
        t = i / 6
        base = hp((t - .5) * 3.2, 2.2 - abs(t - .5) * .8, -1.0 + abs(t - .5) * 1.6)
        length = (11.6 if adult else 9.8) * (1.08 - .36 * abs(t - .5))
        tip = polar(base, length, (t - .5) * 44, (58 if adult else 52) + 12 * abs(t - .5))
        s.blade("head", "crest_plume", base, tip, 1.8, .65,
                ["plume_gold", "crest_feather", "plume_deep", "flame_glow"], bend=(0, 1.8, -.8), steps=4, taper=.68)
    s.gem("head", "diadem_ruby", hp(0, 1.8, -3.2), 1.1, "gem", "gold", (-28, 0, 45))
    # Golden bird legs with lush feathered thighs and sharp talons.
    for side in (-1, 1):
        tag = "l" if side < 0 else "r"
        hip = (side * 3.0, 13.8, 1.8)
        s.bone("leg_" + tag, "body", hip)
        s.orb("leg_" + tag, "thigh", (side * 3.2, 11.4, 1.4), (4.6, 6.0, 5.0), "plume_warm", round_=.62)
        knee, foot = (side * 3.2, 8.6, .9), (side * 3.2, 1.4, -.2)
        s.link("leg_" + tag, "shank", knee, foot, 1.7, 1.7, "leg_gold", extend=.8)
        s.box("leg_" + tag, "leg_band", (side * 3.2, 6.0, .5), (2.3, .9, 2.3), "gold")
        for toe in (-1, 0, 1):
            s.blade("leg_" + tag, "talon", (foot[0], 1.2, foot[2]), (foot[0] + toe * 1.5, .1, foot[2] - 3.0), .9, .9,
                    ["leg_gold", "talon"], steps=2, taper=.68)
        s.blade("leg_" + tag, "hind_talon", (foot[0], 1.2, foot[2] + .4), (foot[0], .1, foot[2] + 2.2), .8, .8, ["talon"], steps=2)
    # Wide, graceful fan of flowing phoenix tail plumes sweeping back and gently down.
    root = (0, 15.2, 5.8)
    s.bone("tail", "body", root)
    s.bone("tail_tip", "tail", (0, 11.6, 15.0))
    reach = 1.0 if adult else .84
    fan_x = (0, -2.4, 2.4, -5.0, 5.0, -7.6, 7.6, -10.2, 10.2) if adult else (0, -2.4, 2.4, -5.0, 5.0, -7.6, 7.6)
    for i, x in enumerate(fan_x):
        lean = 1.0 - .035 * i
        pts = [
            (x * .2, root[1], root[2]),
            (x * .65, 13.8 - .15 * i, root[2] + 6.5 * reach),
            (x * 1.25, 10.6 - .25 * i, root[2] + 14.0 * reach * lean),
            (x * 1.85, 7.4 - .2 * i, root[2] + 21.5 * reach * lean),
            (x * 2.35, 6.2 + .15 * i, root[2] + 27.5 * reach * lean),
        ]
        s.ribbon("tail", "tail_plume", pts, 3.8, 1.2,
                 ["plume_gold", "plume_warm", "plume_deep", "plume_gold", "flame_glow"],
                 steps=3, taper=.52, split=("tail_tip", .38))
        end = pts[-1]
        s.blade("tail_tip", "tail_flame", end, (end[0] * 1.12, end[1] + 1.8, end[2] + 3.4), 2.0, .6,
                ["flame_glow", "flame_core_glow"], bend=(0, .6, 0), steps=2, taper=.8)
    # Wide, arched phoenix wings spreading outward and upward.
    for side in (-1, 1):
        shoulder = (side * 4.4, 22.8, 2.0)
        if adult:
            wrist = (shoulder[0] + side * 14.5, shoulder[1] + 6.8, shoulder[2] + 2.4)
            hand = (wrist[0] + side * 12.0, wrist[1] + 5.2, wrist[2] + 2.0)
            _flame_wing(s, side, shoulder, wrist, hand, 22.0, 14)
        else:
            wrist = (shoulder[0] + side * 11.5, shoulder[1] + 5.8, shoulder[2] + 2.0)
            hand = (wrist[0] + side * 9.5, wrist[1] + 4.4, wrist[2] + 1.6)
            _flame_wing(s, side, shoulder, wrist, hand, 17.5, 11)
    # Large radiant double-ringed Sun Halo behind the upper back and shoulders.
    halo = s.bone("halo", "body", (0, 23.0 if adult else 26.0, 3.2 if adult else 4.2))
    center = (0, 25.2 if adult else 28.0, 3.6 if adult else 4.8)
    radius = 9.2 if adult else 8.8
    s.ring(halo, "sun_halo", center, radius, 24 if adult else 20, .95, "sun_glow")
    s.ring(halo, "sun_halo_inner", center, radius * .78, 18 if adult else 16, .6, "ray_glow")
    ray_count = 16 if adult else 12
    for i in range(ray_count):
        a = radians(90 + 360 * i / ray_count)
        far = radius * (1.48 if i % 4 == 0 else (1.32 if i % 2 == 0 else 1.18))
        inner = [center[0] + radius * 1.02 * cos(a), center[1] + radius * 1.02 * sin(a), center[2] + .15]
        outer = [center[0] + far * cos(a), center[1] + far * sin(a), center[2] + .15]
        s.blade(halo, "sun_ray", inner, outer, 1.5 if i % 4 == 0 else 1.0, .45, ["ray_glow", "sun_glow"], steps=2, taper=.88)
    return s.finish(1.25 if adult else .88)


def _solaris(stage):
    return _solaris_young(stage) if stage == "baby" else _solaris_grown(stage)


# ---- Ignis Draconis · Primordial Flame Dragon ----------------------------------------------------
IGNIS = {
    "scale": "#302436>#1D1522", "scale_dark": "#1B1320>#0F0A14", "belly_plate": "#FFF498>#FF8A16>#D42808",
    "lava_plate": "#FFFFB4>#FF9618>#DC2C08", "horn_bone": "#261B2A>#140D18", "spike": "#281D2C>#150E1A",
    "membrane": "#FFE878>#FF6E14>#9E1208", "membrane_fire": "#FFF288>#FF7818>#B81808",
    "lava_glow": "#FFF69C>#FF7E14>#D82408", "ember_glow": "#FF6E18", "vein_glow": "#FFE468>#FF7414",
    "flame_glow": "#FFF488>#FF8218>#D82408", "flame_core_glow": "#FFFFD8>#FFD048",
    "eye": "#FFFFD0>#FF9E14", "eye_rim": "#10080E", "pupil": "#280804", "shine": "#FFFFFF",
    "claw": "#281D2A>#140E18", "fang": "#FFF8EC", "nostril_glow": "#FF9820",
    "ridge": "#382A40>#221828", "eye_glow": "#FFFFD8>#FFA818", "eye_socket": "#10080E",
    "rock": "#2C2030>#1B141F", "rock_dark": "#1D1522>#100B14", "magma_seam": "#FFE878>#FF6E12",
    "belly_cream": "#FFF6A0>#FF8E16>#D82C08", "horn_dark": "#261B2A>#140D18", "claw_dark": "#241A28>#120C16",
    "maw_glow": "#FFFFC8>#FF8418",
}


def _lava_lines(s, bone, side, x, y0, y1, z0, z1, count, thickness=.48):
    for i in range(count):
        t = (i + .5) / count
        z = z0 + (z1 - z0) * t
        pts = [(x + side * .1, y1, z), (x + side * .1, y1 - (y1 - y0) * .35, z + 1.1),
               (x + side * .1, y1 - (y1 - y0) * .65, z - .5), (x + side * .1, y0, z + .8)]
        for a, b in zip(pts, pts[1:]):
            s.link(bone, "lava_crack", a, b, thickness, thickness, "lava_glow", extend=thickness)


def _dragon_wing(s, stage, side, shoulder, k):
    """Spread bat-like magma wing with dark obsidian spars and glowing fiery membranes."""
    tag = "l" if side < 0 else "r"
    arm = s.bone("wing_" + tag, "body", shoulder)
    elbow = [shoulder[0] + side * k["arm"] * .85, shoulder[1] + k["arm"] * .62, shoulder[2] + k["arm"] * .18]
    tip_bone = s.bone("wingtip_" + tag, arm, elbow)
    wrist = [elbow[0] + side * k["arm"] * .78, elbow[1] + k["arm"] * .45, elbow[2] + k["arm"] * .08]
    s.link(arm, "wing_arm", shoulder, elbow, k["spar"] * 1.65, k["spar"] * 1.5, "rock", extend=k["spar"])
    s.link(tip_bone, "wing_forearm", elbow, wrist, k["spar"] * 1.35, k["spar"] * 1.3, "rock", extend=k["spar"])
    s.link(arm, "wing_arm_lava", lerp(shoulder, elbow, .15), lerp(shoulder, elbow, .88), .45, .45, "lava_glow")
    s.blade(tip_bone, "wing_thumb_claw", wrist, [wrist[0] + side * .6, wrist[1] + k["spar"] * 2.4, wrist[2] - 1.2],
            k["spar"] * 1.1, k["spar"] * 1.1, ["horn_dark", "lava_glow"], steps=2)
    fingers = []
    for yaw, reach in k["fingers"]:
        end = polar(wrist, k["finger_len"] * reach, side * yaw, 10)
        fingers.append(end)
        s.blade(tip_bone, "wing_finger", wrist, end, k["spar"] * 1.05, k["spar"] * .95,
                ["rock", "rock_dark", "horn_dark"], bend=(0, 0, .5), steps=3, taper=.55)
        s.link(tip_bone, "wing_vein", lerp(wrist, end, .08), lerp(wrist, end, .88), .36, .36, "lava_glow")
    apex = [wrist[0], wrist[1], wrist[2] + .18]
    for a, b in zip(fingers, fingers[1:]):
        s.sheet(tip_bone, apex, a, b, "membrane_fire", rows=5, z=.18, sag=.14)
    flank = [shoulder[0] + side * .8, shoulder[1] - k["arm"] * .45, shoulder[2] + k["arm"] * .65]
    s.sheet(tip_bone, apex, fingers[-1], flank, "membrane_fire", rows=4, z=.18, sag=.16)
    s.sheet(arm, [elbow[0], elbow[1], elbow[2] + .2], [shoulder[0], shoulder[1], shoulder[2] + .2], flank, "membrane_fire", rows=3, z=.1, sag=.05)


def _ignis_baby(stage):
    s = Sculpt("ignis_draconis", stage, IGNIS)
    k = dict(by=8.0, bw=10.4, bh=8.8, bd=12.4, hip=6.6, hy=14.4, hz=-7.6, hs=9.8, eye=2.6,
             leg_u=3.6, leg_l=3.0, foot=(3.8, 1.9, 4.2), arm=5.2, spar=1.05, finger_len=6.8,
             fingers=((42, 1.05), (76, 1.0), (110, .86), (140, .7)), tail=8.6, horn=4.2, spikes=5)
    by, bw, bh, bd = k["by"], k["bw"], k["bh"], k["bd"]
    s.bone("body", "root", (0, by, 0))
    s.orb("body", "dragon_chest", (0, by + bh * .08, -bd * .22), (bw, bh * 1.08, bd * .56), "rock", round_=.62)
    s.orb("body", "dragon_barrel", (0, by - bh * .02, bd * .12), (bw * .94, bh * .94, bd * .6), "rock", round_=.62)
    s.orb("body", "dragon_haunch", (0, by + bh * .04, bd * .36), (bw * .96, bh * .92, bd * .36), "rock", round_=.6)
    # Bright glowing segmented yellow-orange chest & belly plates.
    for i in range(4):
        z = -bd * .36 + i * bd * .18
        s.box("body", "belly_plate", (0, by - bh * .48, z), (bw * (.62 - .04 * i), .9, bd * .14), "lava_plate")
    for i in range(4):
        y = by + bh * (.36 - .2 * i)
        s.box("body", "chest_plate", (0, y, -bd * .5 - .25 + i * .14), (bw * (.56 - .04 * i), bh * .16, .9), "lava_plate")
    for side in (-1, 1):
        s.plate("body", "shoulder_plate", (side * bw * .48, by + bh * .18, -bd * .22), (side, .3, -.1), (3.4, 1.1, 4.2), "rock_dark")
        _lava_lines(s, "body", side, side * bw * .49, by - bh * .28, by + bh * .34, -bd * .28, bd * .38, 3, .52)
    neck_base = (0, by + bh * .34, -bd * .4)
    s.bone("neck", "body", neck_base)
    hy, hz, hs = k["hy"], k["hz"], k["hs"]
    s.chain("neck", "neck", [neck_base, (0, (neck_base[1] + hy) / 2 + 1.0, (neck_base[2] + hz) / 2 - .8), (0, hy - hs * .32, hz + hs * .2)],
            [bw * .58, bw * .52], [bw * .62, bw * .54], "rock")
    s.box("neck", "throat_glow", (0, (neck_base[1] + hy) / 2 - .6, (neck_base[2] + hz) / 2 - 2.6), (bw * .42, 2.6, .9), "lava_plate", (-22, 0, 0))
    s.bone("head", "neck", (0, hy - hs * .3, hz + hs * .15))
    hd = hs * .9
    s.orb("head", "skull", (0, hy, hz), (hs, hs * .84, hd), "rock", round_=.62)
    face = hz - hd / 2
    snout_len = hs * .38
    s.orb("head", "snout", (0, hy - hs * .18, face - snout_len * .45), (hs * .72, hs * .48, snout_len + 1.2), "rock", round_=.6)
    muzzle = face - snout_len - .5
    s.box("head", "jaw", (0, hy - hs * .4, face - snout_len * .35), (hs * .64, hs * .16, snout_len), "rock_dark")
    s.box("head", "maw_glow", (0, hy - hs * .31, face - snout_len * .38), (hs * .52, hs * .08, snout_len * .85), "lava_glow")
    for side in (-1, 1):
        s.box("head", "nostril", (side * hs * .15, hy - hs * .06, muzzle + .4), (hs * .11, hs * .08, .25), "nostril_glow")
        s.blade("head", "fang", (side * hs * .22, hy - hs * .28, muzzle + 1.1), (side * hs * .22, hy - hs * .46, muzzle + 1.0),
                hs * .09, hs * .09, ["fang"], steps=2)
        s.box("head", "brow_ridge", (side * hs * .28, hy + hs * .24, face - .25), (hs * .36, hs * .13, hs * .32), "rock_dark", (0, 0, side * -16))
        # Swept-back dark obsidian horns with blazing magma tips.
        horn_root = (side * hs * .28, hy + hs * .34, hz + hd * .08)
        tip = (side * hs * .58, horn_root[1] + k["horn"] * .65, horn_root[2] + k["horn"])
        s.blade("head", "horn", horn_root, tip, hs * .22, hs * .22,
                ["horn_dark", "horn_dark", "lava_glow", "flame_core_glow"], bend=(side * .5, k["horn"] * .22, -.5), steps=4, taper=.78)
        low_root = (side * hs * .42, hy + hs * .08, hz + hd * .18)
        s.blade("head", "horn_low", low_root, (side * hs * .72, low_root[1] + k["horn"] * .32, low_root[2] + k["horn"] * .65),
                hs * .15, hs * .15, ["horn_dark", "lava_glow"], steps=3, taper=.78)
    s.link("head", "brow_lava", (0, hy + hs * .36, face - .08), (0, hy + hs * .14, face - .08), .55, .55, "lava_glow")
    s.eyes("head", hs, hy + hs * .06, face - .16, k["eye"], spread=.27, yaw=16, lid="rock_dark", pupil=.38)
    for i in range(k["spikes"]):
        t = i / max(1, k["spikes"] - 1)
        z = -bd * .36 + t * bd * .78
        height = 1.6 + 1.4 * (1 - abs(t - .35))
        base = (0, by + bh * .48, z)
        s.blade("body", "spine_spike", base, (0, base[1] + height, z + height * .45), height * .48, .75,
                ["rock_dark", "lava_glow"], steps=2, taper=.82)
    x = bw * .35
    for tag, z, front in (("f", -bd * .3, True), ("b", bd * .3, False)):
        for side, suffix in ((-1, "l"), (1, "r")):
            hip = (side * x, k["hip"], z)
            knee = (side * (x + .3), k["hip"] * .5, z + (-.9 if front else 1.5))
            ankle = (side * (x + .3), k["foot"][1], z - (.4 if front else -.2))
            thigh = (((side * x, k["hip"] - k["leg_u"] * .2, z + (.1 if front else .4)),
                      (k["leg_u"] * .95, k["leg_u"] * 1.2, k["leg_u"] * 1.15)))
            _, low = s.leg("leg_" + tag + suffix, hip, knee, ankle, k["leg_u"] * .65, k["leg_l"] * .54, "rock",
                           k["foot"], "rock_dark", "claw_dark", 3, thigh=thigh)
            _lava_lines(s, low, side, side * (x + .3 + k["leg_l"] * .28), k["foot"][1] + .5, knee[1] - .4, z - .3, z + .3, 1, .42)
    root = (0, by + bh * .12, bd * .46)
    s.bone("tail", "body", root)
    mid = (0, by - bh * .04, root[2] + k["tail"] * .46)
    s.bone("tail_tip", "tail", mid)
    end = (0, by + bh * .06, root[2] + k["tail"])
    s.chain("tail", "tail", [root, lerp(root, mid, .5), mid], [bw * .48, bw * .4], [bw * .46, bw * .38], "rock")
    s.chain("tail_tip", "tail", [mid, lerp(mid, end, .5), end], [bw * .32, bw * .22], [bw * .32, bw * .22], ["rock", "rock_dark"])
    for side in (-1, 1):
        s.link("tail", "tail_lava", (side * bw * .2, root[1], root[2] + .8), (side * bw * .15, mid[1], mid[2] - .6), .45, .45, "lava_glow")
    for t in (.22, .52, .8):
        p = lerp(root, end, t)
        h = (2.2 - t) * .85
        s.blade("tail" if t < .46 else "tail_tip", "tail_spike", (0, p[1] + bw * .16, p[2]),
                (0, p[1] + bw * .16 + h, p[2] + h * .55), h * .52, .65, ["rock_dark", "lava_glow"], steps=2)
    for i, (yaw, length) in enumerate(((0, 1.0), (-30, .75), (30, .75))):
        base = (end[0], end[1], end[2] - .4)
        tip = polar(base, k["tail"] * .32 * length, yaw, 48)
        s.blade("tail_tip", "tail_flame", base, tip, bw * (.22 if i == 0 else .16), .6,
                ["flame_glow", "flame_core_glow"], bend=(0, 1.0, .4), steps=3, taper=.8)
    for side in (-1, 1):
        _dragon_wing(s, stage, side, (side * bw * .38, by + bh * .38, -bd * .12), k)
    return s.finish(.68)


def _ignis_grown(stage):
    """Primordial Flame Dragon: broad obsidian-armored quadruped body, blazing segmented yellow-orange chest
    plastron, magma-tipped crown of horns, massive bat-like lava wings, and a spiked magma tail."""
    s = Sculpt("ignis_draconis", stage, IGNIS)
    adult = stage == "adult"
    up = 1.0 if adult else 0.0
    P, B, C = (0, 15.2, 9.8), (0, 16.6, 1.8), (0, 19.2 + 2.8 * up, -5.8)
    s.bone("body", "root", (0, 18.5, 0))
    s.orb("body", "haunch", P, (13.4, 12.0, 10.6), "rock", round_=.62)
    s.orb("body", "barrel", B, (12.8, 11.6, 11.2), "rock", round_=.62)
    s.orb("body", "chest", C, (14.8, 14.8, 12.0), "rock", round_=.62)
    # Obsidian armor plates separated by glowing magma fissures along back and flanks.
    stations = [(C, 7.3, 7.3), (lerp(C, B, .5), 6.8, 6.6), (B, 6.3, 5.8), (lerp(B, P, .5), 6.5, 5.9), (P, 6.7, 6.0)]
    for i, (c, rx, ry) in enumerate(stations):
        for j, a in enumerate((-96, -58, -26, 26, 58, 96)):
            v = ((i * 7 + j * 5) % 11) / 11
            n = (sin(radians(a)), cos(radians(a)), (v - .5) * .25)
            at = (c[0] + rx * n[0], c[1] + ry * n[1] - .2, c[2] + (j % 2) * 1.1 - .5)
            s.plate("body", "rock_plate", at, n, (3.4 + 1.2 * v, 1.3, 3.6 + 1.0 * (1 - v)),
                    "rock_dark" if (i + j) % 2 == 0 else "rock")
    for side in (-1, 1):
        _lava_lines(s, "body", side, side * 7.2, C[1] - 4.2, C[1] + 4.4, C[2] - 2.6, C[2] + 3.4, 3, .62)
        _lava_lines(s, "body", side, side * 6.3, B[1] - 3.8, B[1] + 3.8, B[2] - 3.4, P[2] + 2.4, 4, .58)
    # Blazing segmented yellow-orange molten chest and belly plastron plates.
    belly = curve([(0, C[1] + 2.2, C[2] - 6.6), (0, C[1] - 4.4, C[2] - 4.8),
                   (0, B[1] - 5.8, B[2] + .2), (0, P[1] - 5.6, P[2] - 1.2)], 4)
    for i, (a, b) in enumerate(zip(belly, belly[1:])):
        d = [b[k] - a[k] for k in range(3)]
        w = 8.8 - .28 * i
        s.link("body", "belly_plate", [a[k] + d[k] * .12 for k in range(3)], [b[k] - d[k] * .12 for k in range(3)],
               w, 1.6, "lava_plate", extend=.18)
        s.link("body", "belly_seam", a, lerp(a, b, .18), w * .88, 1.75, "rock_dark")
    # Dorsal obsidian spines with magma bases along the spine.
    spine = curve([(0, C[1] + 7.2, C[2] + .8), (0, B[1] + 5.8, B[2]), (0, P[1] + 6.2, P[2])], 3)
    for i, p in enumerate(spine):
        h = (4.6 if i % 2 == 0 else 3.2) * (1.18 if adult else 1.0)
        s.blade("body", "back_spine", (0, p[1] - .6, p[2]), (0, p[1] + h, p[2] + h * .6), 2.4, 1.1,
                ["rock_dark", "horn_dark", "lava_glow"], steps=3, taper=.82)
        if i % 2 == 0:
            for side in (-1, 1):
                root = (side * 3.6, p[1] - 1.6, p[2] + .4)
                s.blade("body", "side_spine", root, (side * 6.2, root[1] + h * .55, root[2] + h * .48), 1.7, .85,
                        ["rock_dark", "horn_dark", "lava_glow"], steps=2, taper=.82)
    # Powerful armored dragon neck with glowing throat plates and dorsal spikes.
    if adult:
        neck = [(0, C[1] + 2.8, C[2] - 3.8), (0, C[1] + 6.4, C[2] - 8.6), (0, C[1] + 9.6, C[2] - 11.4), (0, C[1] + 11.8, C[2] - 13.8)]
    else:
        neck = [(0, C[1] + 3.4, C[2] - 3.8), (0, C[1] + 8.4, C[2] - 8.2), (0, C[1] + 13.2, C[2] - 8.8), (0, C[1] + 16.6, C[2] - 11.2)]
    s.bone("neck", "body", neck[0])
    path = curve(neck, 2)
    widths = [9.2 - 2.4 * i / (len(path) - 2) for i in range(len(path) - 1)]
    s.chain("neck", "neck", path, widths, [w * 1.05 for w in widths], "rock")
    for i, (a, b) in enumerate(zip(path, path[1:])):
        d = unit([b[k] - a[k] for k in range(3)])
        front = [0, d[2], -d[1]] if d[1] > 0 else [0, -d[2], d[1]]
        off = widths[i] * .5
        pa = [a[k] + front[k] * off + (b[k] - a[k]) * .1 for k in range(3)]
        pb = [b[k] + front[k] * off - (b[k] - a[k]) * .1 for k in range(3)]
        s.link("neck", "throat_plate", pa, pb, widths[i] * .76, 1.65, "lava_plate", extend=.16)
        back = [-front[k] for k in range(3)]
        m = lerp(a, b, .5)
        root = [m[k] + back[k] * off * .82 for k in range(3)]
        h = 3.2 - .2 * i
        s.blade("neck", "neck_spine", root, [root[0], root[1] + h * .55, root[2] + h], 2.0, .9,
                ["rock_dark", "horn_dark", "lava_glow"], steps=2, taper=.82)
        for side in (-1, 1):
            s.link("neck", "neck_seam", [side * widths[i] * .49, a[1], a[2]], [side * widths[i] * .46, b[1], b[2]], .48, .48, "lava_glow")
    # Fierce dragon head with glowing molten eyes, roaring fanged jaws, and magma-tipped horns.
    top = neck[-1]
    s.bone("head", "neck", top)
    H = (0, top[1] + (1.4 if adult else 2.0), top[2] - 2.6)

    def hp(dx, dy, dz):
        return (H[0] + dx, H[1] + dy, H[2] + dz)

    s.orb("head", "cranium", hp(0, 0, 0), (8.2, 6.8, 8.2), "rock", round_=.62)
    s.orb("head", "snout", hp(0, -.6, -6.4), (6.2, 4.4, 7.6), "rock", round_=.62)
    s.orb("head", "nose", hp(0, -.8, -10.4), (5.2, 3.6, 2.8), "rock_dark", round_=.6)
    s.blade("head", "nasal_horn", hp(0, .9, -10.2), hp(0, 3.2, -9.2), 1.3, 1.3, ["horn_dark", "lava_glow"], steps=2, taper=.8)
    s.link("head", "jaw", hp(0, -2.8, -1.2), hp(0, -4.8, -10.2), 5.2, 2.0, "rock_dark")
    s.link("head", "jaw_plate", hp(0, -3.7, -1.6), hp(0, -5.5, -9.4), 4.0, .9, "lava_plate")
    s.link("head", "maw", hp(0, -2.2, -2.0), hp(0, -3.4, -9.4), 4.2, 1.3, "maw_glow")
    s.link("head", "snout_ridge", hp(0, 2.5, -1.4), hp(0, 1.1, -9.8), 2.4, 1.1, "rock_dark")
    s.link("head", "brow_seam", hp(0, 2.8, -1.2), hp(0, 1.3, -8.8), .6, .6, "lava_glow")
    for side in (-1, 1):
        s.box("head", "nostril", hp(side * 1.4, -.2, -11.6), (1.0, .7, .35), "nostril_glow")
        for i, z in enumerate((-10.6, -8.2, -5.8)):
            s.blade("head", "fang", hp(side * (2.1 - .1 * i), -2.4, z), hp(side * (2.1 - .1 * i), -4.1 + .2 * i, z + .2),
                    .68, .68, ["fang"], steps=2)
        for z in (-9.2, -6.6):
            y = -4.5 + (z + 9.2) * .22
            s.blade("head", "fang_low", hp(side * 1.85, y, z), hp(side * 1.85, y + 1.5, z + .2), .6, .6, ["fang"], steps=2)
        s.fierce_eye("head", side, hp(side * 3.45, 1.0, -3.5), 2.2, 1.15, 36)
        s.link("head", "brow", hp(side * 2.8, 2.7, -5.2), hp(side * 3.9, 3.5, -1.4), 1.8, 1.5, "rock_dark")
        # Swept crown of dark obsidian horns with blazing molten tips.
        s.blade("head", "horn", hp(side * 2.6, 2.8, .4), hp(side * (7.2 if adult else 6.2), 7.6 if adult else 9.2, 13.6 if adult else 12.0),
                2.6, 2.6, ["horn_dark", "horn_dark", "horn_dark", "lava_glow", "flame_core_glow"],
                bend=(side * 1.1, -1.6, -1.2), steps=5, taper=.82)
        s.blade("head", "horn_mid", hp(side * 3.3, 3.6, -1.2), hp(side * (5.8 if adult else 5.4), 6.4 if adult else 8.2, 5.2 if adult else 4.8), 1.5, 1.5,
                ["horn_dark", "horn_dark", "lava_glow"], bend=(side * .5, -.4, -.6), steps=3, taper=.82)
        s.blade("head", "horn_low", hp(side * 3.7, .6, 1.8), hp(side * 8.8, 3.6, 10.2), 1.9, 1.9,
                ["horn_dark", "horn_dark", "lava_glow", "flame_core_glow"], bend=(side * .6, -.8, -.6), steps=4, taper=.82)
        for i in range(3):
            base = hp(side * 3.9, -1.4 - i * 1.1, -1.4 + i * 1.8)
            s.blade("head", "cheek_spike", base, (base[0] + side * 2.2, base[1] + .5, base[2] + 3.4), 1.1, .85,
                    ["rock_dark", "lava_glow"], steps=2, taper=.82)
    # Thick, muscular obsidian-armored legs with glowing magma fissures and 3 claws.
    for side, tag in ((-1, "l"), (1, "r")):
        shoulder = (side * 6.8, C[1] - 1.4, C[2] - .6)
        elbow = (side * 7.6, C[1] * .46 + 1.0, C[2] + 1.6)
        ankle = (side * 7.6, 2.4, C[2] - 2.2)
        _, low = s.leg("leg_f" + tag, shoulder, elbow, ankle, 5.4, 4.4, "rock", (5.4, 2.8, 6.0), "rock_dark", "claw_dark", 3,
                       thigh=((side * 7.1, C[1] - 3.6, C[2] - .6), (6.4, 8.8, 7.2)))
        s.plate("leg_f" + tag, "pauldron", (side * 8.2, C[1] + .6, C[2] - .4), (side, .45, 0), (5.8, 1.5, 6.6), "rock_dark")
        _lava_lines(s, "leg_f" + tag, side, side * 9.2, elbow[1] + 1.0, C[1] + 1.2, C[2] - 2.0, C[2] + 1.6, 2, .55)
        _lava_lines(s, low, side, side * 9.0, 3.2, elbow[1] - .4, C[2] - 2.0, C[2] + 1.0, 2, .5)
        s.blade(low, "elbow_spike", (elbow[0] + side * 1.6, elbow[1], elbow[2] + 1.2),
                (elbow[0] + side * 3.0, elbow[1] + 1.6, elbow[2] + 4.2), 1.3, 1.1, ["horn_dark", "lava_glow"], steps=2)
    for side, tag in ((-1, "l"), (1, "r")):
        name = "leg_b" + tag
        hip, knee, hock, ankle = (side * 6.6, P[1] + .4, P[2] - .2), (side * 7.6, P[1] - 6.4, P[2] - 3.6), \
            (side * 7.6, 5.6, P[2] + 1.8), (side * 7.6, 2.4, P[2] - .4)
        s.bone(name, "body", hip)
        s.orb(name, "thigh", (side * 7.0, P[1] - 2.4, P[2] - 1.0), (6.2, 9.0, 7.8), "rock", round_=.62)
        s.plate(name, "thigh_plate", (side * 9.8, P[1] - 1.6, P[2] - 1.0), (side, .4, 0), (5.4, 1.4, 5.8), "rock_dark")
        _lava_lines(s, name, side, side * 9.9, P[1] - 5.2, P[1] + .6, P[2] - 3.0, P[2] + 1.4, 2, .55)
        s.link(name, "upper", hip, knee, 5.2, 5.4, "rock", extend=2.0)
        low = s.bone(name + "_lower", name, knee)
        s.link(low, "shank", knee, hock, 4.2, 4.2, "rock", extend=1.5)
        s.link(low, "metatarsus", hock, ankle, 3.6, 3.6, "rock_dark", extend=1.2)
        _lava_lines(s, low, side, side * 9.0, 3.0, hock[1] + 1.2, P[2] - 1.4, P[2] + 1.4, 1, .48)
        s.orb(low, "hind_foot", (side * 7.6, 1.3, P[2] - 1.8), (5.0, 2.6, 6.0), "rock_dark", round_=.66)
        for c in (-1, 0, 1):
            x = side * 7.6 + c * 1.5
            s.blade(low, "hind_claw", (x, 1.2, P[2] - 4.2), (x, .1, P[2] - 6.2), 1.0, 1.0, ["claw_dark"], steps=2, taper=.7)
    # Thick, muscular spiked magma tail sweeping back in a powerful S-curve (natural both standing and flying).
    reach = 1.0 if adult else .84
    tail = [
        (0, P[1] + 1.2, P[2] + 4.2),
        (0, P[1] - 1.4, P[2] + 4.2 + 6.8 * reach),
        (0, P[1] - 4.2, P[2] + 4.2 + 13.6 * reach),
        (0, P[1] - 5.4, P[2] + 4.2 + 20.4 * reach),
        (0, P[1] - 3.8, P[2] + 4.2 + 26.8 * reach),
    ]
    s.bone("tail", "body", tail[0])
    path = curve(tail, 2)
    half = len(path) // 2
    s.bone("tail_tip", "tail", path[half])
    for i, (a, b) in enumerate(zip(path, path[1:])):
        f = i / (len(path) - 1)
        w = 7.4 - 4.8 * f
        bone = "tail" if i < half else "tail_tip"
        s.link(bone, "tail", a, b, w, w, "rock" if i % 2 else "rock_dark", extend=w * .55)
        for side in (-1, 1):
            s.link(bone, "tail_seam", (side * w * .48, a[1], a[2]), (side * w * .44, b[1], b[2]), .48, .48, "lava_glow")
        if i % 2 == 0:
            s.blade(bone, "tail_spine", (a[0], a[1] + w * .42, a[2]),
                    (a[0], a[1] + w * .42 + 3.4 - 1.8 * f, a[2] + 2.0), 1.9 - .8 * f, .85,
                    ["rock_dark", "horn_dark", "lava_glow"], steps=2, taper=.82)
    end = path[-1]
    for yaw, pitch, length, width in ((0, 22, 7.6, 3.4), (-38, 28, 5.6, 2.5), (38, 28, 5.6, 2.5), (0, 62, 4.8, 2.2)):
        s.blade("tail_tip", "tail_flame", end, polar(end, length * reach, yaw, pitch), width, 1.0,
                ["flame_glow", "flame_core_glow"], bend=(0, 1.4, .6), steps=3, taper=.82)
    # Massive bat-like dragon wings with dark obsidian spars and continuous molten lava membranes.
    span = 1.0 if adult else .86
    for side, tag in ((-1, "l"), (1, "r")):
        shoulder = (side * 5.8, C[1] + 4.8, C[2] + 2.8)
        if adult:
            elbow, wrist = (side * 14.5, C[1] + 9.8, C[2] + 5.8), (side * 24.5, C[1] + 14.4, C[2] + 7.8)
            fingers = ((38, 24.0), (68, 26.5), (98, 23.0), (128, 19.0))
        else:
            elbow, wrist = (side * 12.8, C[1] + 8.0, C[2] + 5.2), (side * 21.2, C[1] + 11.4, C[2] + 7.0)
            fingers = ((44, 20.0), (74, 22.0), (102, 19.0), (130, 16.0))
        arm = s.bone("wing_" + tag, "body", shoulder)
        tip_bone = s.bone("wingtip_" + tag, arm, elbow)
        s.link(arm, "wing_humerus", shoulder, elbow, 3.2, 3.0, "rock", extend=1.8)
        s.link(tip_bone, "wing_forearm", elbow, wrist, 2.6, 2.5, "rock", extend=1.4)
        s.link(arm, "wing_arm_seam", lerp(shoulder, elbow, .12), lerp(shoulder, elbow, .88), .55, .55, "lava_glow")
        s.link(tip_bone, "wing_fore_seam", lerp(elbow, wrist, .12), lerp(elbow, wrist, .88), .5, .5, "lava_glow")
        s.blade(tip_bone, "wing_thumb", wrist, (wrist[0] + side * 1.2, wrist[1] + 3.6, wrist[2] - 2.2), 1.5, 1.5,
                ["horn_dark", "lava_glow"], steps=2)
        ends = []
        for yaw, length in fingers:
            end = polar(wrist, length * span, side * yaw, 10)
            ends.append(end)
            s.blade(tip_bone, "wing_finger", wrist, end, 1.6, 1.4, ["rock", "rock_dark", "horn_dark"], bend=(0, 0, .7), steps=4, taper=.58)
            s.link(tip_bone, "wing_vein", lerp(wrist, end, .08), lerp(wrist, end, .9), .42, .42, "lava_glow")
            s.blade(tip_bone, "wing_tip_claw", end, polar(end, 2.0, side * yaw, 10), .9, .9, ["horn_dark", "lava_glow"], steps=1)
        apex = (wrist[0], wrist[1], wrist[2] + .25)
        flank = (side * 7.2, B[1] + 1.2, B[2] + 4.8)
        for a, b in zip(ends, ends[1:]):
            s.sheet(tip_bone, apex, a, b, "membrane_fire", rows=6, z=.25, sag=.15)
        s.sheet(tip_bone, apex, ends[-1], flank, "membrane_fire", rows=6, z=.25, sag=.18)
        s.sheet(arm, (elbow[0], elbow[1], elbow[2] + .3), (side * 6.2, C[1] + 3.8, C[2] + 3.2), flank, "membrane_fire", rows=4, z=0, sag=0)
    return s.finish(1.25 if adult else .88)


def _ignis_draconis(stage):
    return _ignis_baby(stage) if stage == "baby" else _ignis_grown(stage)


# ---- Zephyros · Storm Griffin ---------------------------------------------------------------------
ZEPHYROS = {
    "feather_white": "#FFFFFF>#EDF4FC", "feather_lilac": "#F8FBFF>#DCE8F8", "feather_ice": "#E2F6FF>#9CE0FF",
    "feather_sky": "#7CE0FF>#34A4F8", "feather_blue": "#3894FF>#165CE0", "feather_azure": "#2E8CFF>#1456D4",
    "feather_deep": "#1A4EC8>#0C2878", "feather_tip": "#1850CC>#64CEFF",
    "fur_white": "#FFFFFF>#E8F0FA", "fur_shade": "#C8DCF2>#98B8DC",
    "beak": "#FFE878>#E8A024", "beak_tip": "#C87814", "scale_gold": "#FFE274>#DC9A24",
    "horn_gold": "#FFF098>#E0A228", "talon": "#2A3654", "talon_dark": "#242C46>#101524",
    "gold": "#FFE474>#D49620", "gold_dark": "#B47A18", "storm_glow": "#ECFFFF>#58DCFF",
    "wind_glow": "#F4FFFF>#98ECFF", "gem": "#48D8FF",
    "eye": "#1868D8>#78E4FF", "eye_rim": "#0C1A33", "eye_socket": "#0C1A33", "pupil": "#07122A",
    "shine": "#FFFFFF", "blush": "#FFB2C6",
}


def _griffin_wing(s, stage, side, shoulder, k):
    """Baby griffin wing: crisp white upper coverts, sky-blue middle secondaries, royal-blue outer primaries."""
    tag = "l" if side < 0 else "r"
    arm = s.bone("wing_" + tag, "body", shoulder)
    wrist = [shoulder[0] + side * k["arm"], shoulder[1] + k["arm"] * k["lift"], shoulder[2] + k["arm"] * .2]
    tip_bone = s.bone("wingtip_" + tag, arm, wrist)
    hand = [wrist[0] + side * k["arm"] * .8, wrist[1] + k["arm"] * .35, wrist[2] + k["arm"] * .15]
    s.link(arm, "wing_arm", shoulder, wrist, k["arm_w"], k["arm_w"], "feather_white", extend=k["arm_w"] * .6)
    s.link(tip_bone, "wing_hand", wrist, hand, k["arm_w"] * .82, k["arm_w"] * .82, "feather_white", extend=k["arm_w"] * .5)
    s.link(arm, "wing_gold_edge", [shoulder[0], shoulder[1] + k["arm_w"] * .45, shoulder[2] - .28],
           [wrist[0], wrist[1] + k["arm_w"] * .45, wrist[2] - .28], k["arm_w"] * .32, k["arm_w"] * .5, "horn_gold")
    n = k["primaries"]
    for i in range(n):
        t = i / max(1, n - 1)
        root = lerp(wrist, hand, 1.0 - .8 * t)
        root = [root[0], root[1] - .3 * t, root[2] + .12 * i]
        yaw = side * (k["fan"][0] + (k["fan"][1] - k["fan"][0]) * t)
        length = k["primary_len"] * (1.05 - .28 * abs(t - .28))
        tip = polar(root, length, yaw, 8)
        s.blade(tip_bone, "wing_primary", root, tip, k["feather_w"] * 1.12, .55,
                ["feather_sky", "feather_azure", "feather_deep", "storm_glow"], bend=(0, 0, .65), steps=4, taper=.38)
    for i in range(k["secondaries"]):
        t = (i + .4) / k["secondaries"]
        root = lerp(shoulder, wrist, .12 + .85 * t)
        root = [root[0], root[1] - .45, root[2] + .3 + .1 * i]
        yaw = side * (k["fan"][1] - 10 + 22 * (1 - t))
        tip = polar(root, k["secondary_len"] * (.82 + .24 * t), yaw, 10)
        s.blade(arm, "wing_secondary", root, tip, k["feather_w"] * 1.08, .55,
                ["feather_white", "feather_sky", "feather_azure", "storm_glow"], bend=(0, 0, .5), steps=4, taper=.4)
    for row, (count, length, mats) in enumerate(((k["coverts"] + 1, k["covert_len"], ["feather_white", "feather_sky"]),
                                                 (k["coverts"] + 2, k["covert_len"] * .65, ["feather_white", "feather_white"]))):
        for i in range(count):
            t = (i + .4) / count
            root = lerp(shoulder, hand, .06 + .86 * t)
            root = [root[0], root[1] + .25 - row * .45, root[2] - .28 - row * .25]
            yaw = side * (145 - 55 * t)
            tip = polar(root, length * (1 - .15 * t), yaw, 5)
            s.blade(tip_bone if t > .55 else arm, "wing_covert", root, tip, k["covert_w"] * 1.15, .55, mats, steps=2, taper=.35)


def _zephyros_baby(stage):
    s = Sculpt("zephyros", stage, ZEPHYROS)
    k = dict(by=8.0, bw=10.0, bh=8.8, bd=11.4, hip=6.4, hy=14.8, hz=-6.4, hs=9.6, eye=2.6,
             arm=4.4, arm_w=2.2, lift=.44, fan=(56, 150), primaries=6, primary_len=6.8, feather_w=2.4,
             secondaries=4, secondary_len=5.2, coverts=3, covert_len=3.4, covert_w=2.4,
             crest=5, crest_len=5.6, leg_u=3.4, leg_l=2.8, tail=6.4)
    by, bw, bh, bd = k["by"], k["bw"], k["bh"], k["bd"]
    s.bone("body", "root", (0, by, 0))
    s.orb("body", "lion_body", (0, by - bh * .03, bd * .14), (bw * .92, bh * .9, bd * .62), "fur_white", round_=.62)
    s.orb("body", "eagle_breast", (0, by + bh * .06, -bd * .2), (bw, bh * 1.06, bd * .54), "feather_white", round_=.62)
    front = -bd * .2 - bd * .26
    s.feather_patch("body", "breast_feather", (0, by + bh * .38, front - .7), bw * .68, 3, 4,
                    bh * .3, bw * .26, ["feather_white", "feather_sky", "feather_azure"], z_step=.28, row_scale=.94)
    for side in (-1, 1):
        x = side * (bw * .46 + .1)
        pts = [(x, by + bh * .14, 0), (x, by + bh * .24, bd * .18), (x, by + bh * .08, bd * .36)]
        for a, b in zip(pts, pts[1:]):
            s.link("body", "wind_mark", a, b, .55, .45, "feather_sky", extend=.5)
    neck_base = (0, by + bh * .38, -bd * .32)
    s.bone("neck", "body", neck_base)
    hy, hz, hs = k["hy"], k["hz"], k["hs"]
    hd = hs * .9
    s.orb("neck", "neck", (0, (neck_base[1] + hy - hs * .3) / 2, (neck_base[2] + hz) / 2),
          (bw * .58, hy - hs * .3 - neck_base[1] + 2.2, bw * .6), "feather_white", round_=.62)
    s.bone("head", "neck", (0, hy - hs * .35, hz + hs * .1))
    s.orb("head", "head", (0, hy, hz), (hs, hs * .92, hd), "feather_white", round_=.64)
    face = hz - hd / 2
    s.feather_patch("head", "brow_feather", (0, hy + hs * .46, face - .25), hs * .56, 1, 3, hs * .22, hs * .22,
                    ["feather_white", "feather_sky"], lean=-.1)
    for side in (-1, 1):
        s.blade("head", "cheek_tuft", (side * hs * .44, hy - hs * .1, hz),
                (side * hs * .7, hy - hs * .32, hz + hd * .32), hs * .24, .55, ["feather_white", "feather_sky"], steps=2)
        s.blade("head", "gold_horn", (side * hs * .26, hy + hs * .38, hz + hd * .04),
                (side * hs * .48, hy + hs * .42 + k["crest_len"] * .55, hz + hd * .45), hs * .16, hs * .16,
                ["horn_gold", "horn_gold", "storm_glow"], bend=(side * .3, .6, -.4), steps=3, taper=.78)
        s.box("head", "blush", (side * hs * .33, hy - hs * .18, face - .12), (hs * .16, hs * .08, .2), "blush")
    s.eyes("head", hs, hy + hs * .06, face - .16, k["eye"], spread=.26, yaw=14, lid="feather_sky")
    beak_y = hy - hs * .12
    s.box("head", "beak", (0, beak_y, face - hs * .1), (hs * .28, hs * .2, hs * .26), "beak", (12, 0, 0))
    s.blade("head", "beak_hook", (0, beak_y + hs * .02, face - hs * .22), (0, beak_y - hs * .2, face - hs * .38),
            hs * .22, hs * .18, ["beak", "beak_tip"], steps=3)
    for i in range(k["crest"]):
        t = i / max(1, k["crest"] - 1)
        base = ((t - .5) * hs * .44, hy + hs * .38, hz + hd * (.02 + .18 * abs(t - .5)))
        tip = polar(base, k["crest_len"] * (1.16 - .34 * abs(t - .5)), (t - .5) * 50, 36 + 26 * abs(t - .5))
        s.blade("head", "crest_plume", base, tip, hs * .22, .55,
                ["feather_white", "feather_sky", "feather_azure", "feather_deep"], bend=(0, 1.6, -.5), steps=4, taper=.68)
    x = bw * .33
    for side, suffix in ((-1, "l"), (1, "r")):
        z = -bd * .3
        hip, knee, ankle = (side * x, k["hip"], z), (side * x, k["hip"] * .52, z - .8), (side * x, 1.4, z - .4)
        s.leg("leg_f" + suffix, hip, knee, ankle, k["leg_u"] * .68, k["leg_u"] * .4, "feather_white",
              (k["leg_u"] * .65, 1.5, k["leg_u"] * .55), "scale_gold", "talon_dark", 3, lower_mat="scale_gold", cuff="gold")
        z = bd * .3
        hip, knee, ankle = (side * x, k["hip"], z), (side * (x + .2), k["hip"] * .5, z + 1.4), (side * (x + .2), 1.8, z)
        s.leg("leg_b" + suffix, hip, knee, ankle, k["leg_u"] * .68, k["leg_l"] * .52, "fur_white",
              (k["leg_u"] * .72, 1.8, k["leg_u"] * .8), "scale_gold", "talon_dark", 3, lower_mat="scale_gold",
              thigh=((side * x, k["hip"] - k["leg_u"] * .2, z + .3), (k["leg_u"] * .95, k["leg_u"] * 1.2, k["leg_u"] * 1.15)))
    root = (0, by + bh * .16, bd * .44)
    s.bone("tail", "body", root)
    mid = (0, by + bh * .24, root[2] + k["tail"] * .42)
    s.bone("tail_tip", "tail", mid)
    end = (0, mid[1] + k["tail"] * .1, mid[2] + k["tail"] * .44)
    s.chain("tail", "tail", [root, mid], [bw * .24], [bw * .24], "feather_white")
    s.chain("tail_tip", "tail", [mid, end], [bw * .2], [bw * .2], "feather_sky")
    for yaw in (-34, -12, 12, 34):
        s.blade("tail_tip", "tail_feather", end, polar(end, k["tail"] * .42 * (1.12 if abs(yaw) < 20 else .92), yaw, 58),
                bw * .2, .55, ["feather_white", "feather_sky", "feather_azure", "feather_deep"], bend=(0, .6, 0), steps=3, taper=.65)
    for side in (-1, 1):
        _griffin_wing(s, stage, side, (side * bw * .4, by + bh * .38, -bd * .1), k)
    return s.finish(.68)


def _storm_wing(s, side, shoulder, wrist, hand, length, count):
    """Majestic Storm Griffin wing matching the reference concept: crisp white upper coverts, bright sky-cyan
    middle secondaries, and long royal/azure-blue outer primary flight feathers with glowing cyan tips."""
    tag = "l" if side < 0 else "r"
    arm = s.bone("wing_" + tag, "body", shoulder)
    tip_bone = s.bone("wingtip_" + tag, arm, wrist)
    s.link(arm, "wing_arm", shoulder, wrist, 3.2, 3.0, "feather_white", extend=1.8)
    s.link(tip_bone, "wing_hand", wrist, hand, 2.5, 2.4, "feather_white", extend=1.4)
    s.link(arm, "wing_gold_shoulder", [shoulder[0], shoulder[1] + 1.3, shoulder[2] - .45],
           [wrist[0], wrist[1] + 1.1, wrist[2] - .45], 1.1, 1.3, "horn_gold", extend=.9)

    def along(u):
        return lerp(shoulder, wrist, u / .55) if u < .55 else lerp(wrist, hand, (u - .55) / .45)

    def reach(u):
        return length * (.66 + .4 * sin(min(1.0, u / .82) * pi / 2)) * (1 - .08 * max(0.0, u - .82) / .18)

    rows = (
        ("wing_flight", count + 4, 1.0, .2, 4.4, ["feather_sky", "feather_azure", "feather_deep", "storm_glow"], 4),
        ("wing_covert", count + 2, .64, -.25, 4.5, ["feather_white", "feather_sky", "feather_azure"], 3),
        ("wing_lesser", count, .36, -.65, 4.2, ["feather_white", "feather_white", "feather_ice"], 2),
    )
    for name, n, scale, z, width, mats, steps in rows:
        for i in range(n):
            u = (i + .25) / (n - .3)
            root = along(u)
            root = [root[0], root[1] - (1 - scale) * .5, root[2] + z + .09 * i]
            yaw = side * (162 - 96 * u ** .95)
            tip = polar(root, reach(u) * scale, yaw, 14)
            s.blade(arm if u < .55 else tip_bone, name, root, tip, width, .55, mats, bend=(0, 0, .55), steps=steps, taper=.42)


def _zephyros_grown(stage):
    """Storm Griffin: crisp white upper plumage, V-layered white & cyan eagle chest, wide arched wings grading
    from white coverts to sky-blue secondaries and royal-blue primaries, gold-ribbed horns, and wind ribbons."""
    s = Sculpt("zephyros", stage, ZEPHYROS)
    adult = stage == "adult"
    up = 1.0 if adult else 0.0
    P, B, C = (0, 15.8, 9.2), (0, 16.8, 1.6), (0, 19.2 + 2.8 * up, -5.6)
    s.bone("body", "root", (0, 17.5, 0))
    s.orb("body", "haunch", P, (12.4, 11.4, 10.0), "feather_white", round_=.62)
    s.orb("body", "barrel", B, (11.8, 11.0, 10.6), "feather_white", round_=.62)
    s.orb("body", "chest", C, (13.6, 14.2, 11.2), "feather_white", round_=.62)
    # Shingled white & ice-blue body plumage.
    stations = [(C, 6.8, 7.1), (lerp(C, B, .5), 6.3, 6.4), (B, 5.9, 5.5), (lerp(B, P, .5), 6.0, 5.7), (P, 6.2, 5.8)]
    for i, (c, rx, ry) in enumerate(stations):
        for j, a in enumerate((-82, -50, -18, 18, 50, 82)):
            sa = 1 if a > 0 else -1
            n = (sin(radians(a)), cos(radians(a)), 0)
            tangent = (sa * cos(radians(a)), -abs(sin(radians(a))), 0)
            d = unit([tangent[0] * .55, tangent[1] * .55, .85])
            root = (c[0] + rx * n[0] + n[0] * .3, c[1] + ry * n[1] + n[1] * .3, c[2] - 1.0 + (j % 2) * .9)
            length = 6.2 + (i % 2) * 1.0
            tip = [root[k] + d[k] * length + n[k] * 1.4 for k in range(3)]
            mat = ["feather_white", "feather_ice"] if (i + j) % 2 else ["feather_white", "feather_sky"]
            s.blade("body", "coat_feather", root, tip, 4.4, .6, mat, steps=2, taper=.58)
    # V-layered eagle chest ruff in crisp white upper tiers and sky-cyan / azure-blue lower chevrons.
    row_mats = (
        ["feather_white", "feather_white", "feather_ice"],
        ["feather_white", "feather_ice", "feather_sky"],
        ["feather_white", "feather_sky", "feather_azure"],
        ["feather_sky", "feather_azure", "feather_deep"],
        ["feather_white", "feather_sky", "feather_azure"],
    )
    for row, mats in enumerate(row_mats):
        y_top = C[1] + 5.8 - row * 2.4
        z_top = C[2] - 5.8 + row * .32
        cols = 5 if row % 2 == 0 else 4
        for col in range(cols):
            u = (col - (cols - 1) / 2) / max(1, (cols - 1) / 2)
            x = u * (5.2 - row * .35)
            v_drop = (1.0 - abs(u)) * 1.45
            base = (x, y_top - v_drop, z_top - (1.0 - abs(u)) * .6)
            tip = (x * .92, base[1] - 4.6, base[2] + .4)
            s.blade("body", "chest_ruff", base, tip, 3.4, .65, mats, steps=3, taper=.62)
    # Proud griffin neck with hackle collar and royal-blue dorsal mane.
    if adult:
        neck = [(0, C[1] + 2.8, C[2] - 3.6), (0, C[1] + 6.2, C[2] - 7.4), (0, C[1] + 9.2, C[2] - 9.8)]
    else:
        neck = [(0, C[1] + 3.2, C[2] - 3.8), (0, C[1] + 7.8, C[2] - 7.0), (0, C[1] + 11.8, C[2] - 8.6)]
    s.bone("neck", "body", neck[0])
    path = curve(neck, 2)
    s.chain("neck", "neck", path, [8.2 - .4 * i for i in range(len(path) - 1)],
            [8.4 - .4 * i for i in range(len(path) - 1)], "feather_white")
    for ring, (t, radius, length, count) in enumerate(((.75, 4.1, 5.2, 9), (.35, 4.6, 5.8, 11))):
        c = curve(neck, 6)[int(t * 12)]
        for i in range(count):
            a = radians(-150 + 300 * i / (count - 1))
            root = (sin(a) * radius, c[1], c[2] - cos(a) * radius)
            tip = (sin(a) * (radius + 2.4), c[1] - length, root[2] - cos(a) * 1.5 + 1.2)
            s.blade("neck", "hackle", root, tip, 2.7, .8, ["feather_white", "feather_sky", "feather_azure"],
                    bend=(sin(a) * .5, .4, -cos(a) * .4), steps=3, taper=.68)
    top = neck[-1]
    s.bone("head", "neck", top)
    H = (0, top[1] + (1.8 if adult else 2.4), top[2] - 2.2)

    def hp(dx, dy, dz):
        return (H[0] + dx, H[1] + dy, H[2] + dz)

    s.orb("head", "skull", hp(0, 0, 0), (7.2, 6.6, 7.6), "feather_white", round_=.62)
    s.orb("head", "cheek", hp(0, -1.3, -1.4), (6.8, 4.0, 6.0), "feather_white", round_=.62)
    s.link("head", "brow", hp(-3.6, 2.0, -3.5), hp(3.6, 2.0, -3.5), 1.5, 2.5, "feather_sky")
    s.box("head", "cere", hp(0, .3, -4.2), (3.4, 2.1, 1.7), "beak")
    s.blade("head", "beak_upper", hp(0, .6, -4.4), hp(0, -2.9, -9.0), 3.1, 2.9, ["beak", "beak", "beak_tip"],
            bend=(0, 1.8, -1.2), steps=4, taper=.78)
    s.link("head", "beak_lower", hp(0, -1.4, -3.9), hp(0, -2.2, -6.8), 2.3, 1.1, "beak_tip")
    for side in (-1, 1):
        s.fierce_eye("head", side, hp(side * 3.0, .7, -3.1), 1.85, 1.2, 36, iris="eye", slit=False, slant=14)
        s.blade("head", "cheek_feather", hp(side * 3.2, -1.5, -.6), hp(side * 4.8, -3.2, 3.8), 2.2, .65,
                ["feather_white", "feather_sky", "feather_azure"], steps=2, taper=.58)
        # Swept-back golden-ribbed horns and ear plumes.
        s.blade("head", "horn", hp(side * 2.2, 2.8, -.4), hp(side * (4.6 if adult else 4.2), 6.8 if adult else 7.8, 6.2 if adult else 5.8), 1.5, 1.5,
                ["horn_gold", "horn_gold", "storm_glow"], bend=(side * .5, 1.4, -1.6), steps=4, taper=.78)
        s.blade("head", "ear_plume", hp(side * 3.0, 2.0, .6), hp(side * 6.0, 5.6, 5.4), 1.8, .7,
                ["feather_white", "feather_sky", "feather_azure"], bend=(side * .4, .8, -.4), steps=3, taper=.7)
    for i in range(7):
        t = i / 6
        base = hp((t - .5) * 3.8, 3.0 - abs(t - .5) * 1.2, -.8 + abs(t - .5) * .6)
        length = (11.2 if adult else 9.2) * (1.08 - .36 * abs(t - .5))
        tip = polar(base, length, (t - .5) * 48, 66 if adult else 56)
        s.blade("head", "crest_plume", base, tip, 2.2, .65,
                ["feather_white", "feather_sky", "feather_azure", "feather_deep"], bend=(0, 1.6, -.5), steps=4, taper=.68)
    for i in range(5):
        t = i / 4
        c = curve(neck, 4)[min(8, int((1 - t) * 8))]
        root = (0, c[1] + 1.6, c[2] + 3.6)
        tip = (0, root[1] - 2.0 - 2.0 * t, root[2] + 6.8 - t)
        for side in (-1, 1):
            s.blade("neck", "mane_feather", (side * 1.4, root[1], root[2]), (side * 3.2, tip[1], tip[2]), 2.4, .65,
                    ["feather_sky", "feather_azure", "feather_deep"], bend=(0, 1.2, 0), steps=3, taper=.68)
    # Feathered forelegs with golden scaled shanks and talons; muscular feathered hindlegs.
    for side, tag in ((-1, "l"), (1, "r")):
        shoulder, elbow, ankle = (side * 5.8, C[1] - 1.6, C[2] - .4), (side * 6.2, C[1] * .48, C[2] - 1.2), (side * 6.2, 1.8, C[2] - 2.6)
        _, low = s.leg("leg_f" + tag, shoulder, elbow, ankle, 4.8, 2.8, "feather_white", (4.2, 1.9, 4.2), "scale_gold", None,
                       lower_mat="scale_gold", thigh=((side * 6.2, C[1] - 4.0, C[2] - .8), (6.0, 8.4, 6.6)))
        for k, z in enumerate((-.8, .8)):
            s.blade("leg_f" + tag, "trouser", (side * 6.6, C[1] - 3.8 - k * 2.4, C[2] - 1.2 + z),
                    (side * 7.6, elbow[1] - 1.6, C[2] - .2 + z), 3.2, .65, ["feather_white", "feather_sky"], steps=2, taper=.58)
        for c in (-1, 0, 1):
            x = ankle[0] + c * 1.4
            s.blade(low, "talon", (x, 1.4, ankle[2] - 2.0), (x, .1, ankle[2] - 4.6), 1.1, 1.1, ["scale_gold", "talon_dark"],
                    bend=(0, .7, 0), steps=2, taper=.68)
        s.blade(low, "hind_talon", (ankle[0], 1.4, ankle[2] + 1.0), (ankle[0], .1, ankle[2] + 2.8), 1.0, 1.0, ["talon_dark"], steps=2)
    for side, tag in ((-1, "l"), (1, "r")):
        name = "leg_b" + tag
        hip, knee, hock, ankle = (side * 6.2, P[1] + .4, P[2] - .2), (side * 7.0, P[1] - 6.4, P[2] - 3.4), \
            (side * 7.0, 5.2, P[2] + 1.6), (side * 7.0, 2.0, P[2] - .4)
        s.bone(name, "body", hip)
        s.orb(name, "thigh", (side * 6.6, P[1] - 2.4, P[2] - 1.0), (5.8, 8.6, 7.6), "feather_white", round_=.62)
        for k in range(3):
            s.blade(name, "trouser", (side * 8.8, P[1] - 1.0 - k * 1.6, P[2] - 2.4 + k * 1.8),
                    (side * 9.8, P[1] - 7.4 - k * .6, P[2] - 1.4 + k * 1.8), 3.0, .65, ["feather_white", "feather_sky"], steps=2, taper=.58)
        s.link(name, "upper", hip, knee, 4.6, 4.8, "feather_white", extend=2.0)
        low = s.bone(name + "_lower", name, knee)
        s.link(low, "shank", knee, hock, 3.5, 3.5, "feather_white", extend=1.4)
        s.link(low, "metatarsus", hock, ankle, 2.8, 2.8, "scale_gold", extend=1.2)
        for c in (-1, 0, 1):
            x = side * 7.0 + c * 1.4
            s.blade(low, "hind_toe", (x, 1.2, P[2] - 1.0), (x, .1, P[2] - 4.8), 1.15, 1.15,
                    ["scale_gold", "scale_gold", "talon_dark"], bend=(0, .6, 0), steps=3, taper=.68)
    # Multi-plume white, sky-blue, and royal-blue griffin tail fan.
    root = (0, P[1] + 2.4, P[2] + 4.4)
    s.bone("tail", "body", root)
    s.bone("tail_tip", "tail", (0, root[1] + 1.8, root[2] + 9.2))
    reach = 1.0 if adult else .84
    for x, rise, length in ((0, 1.0, 1.0), (-3.6, .75, .92), (3.6, .75, .92), (-7.0, .4, .82), (7.0, .4, .82)):
        pts = [
            (x * .2, root[1], root[2]),
            (x * .65, root[1] + 4.2 * rise + .8, root[2] + 6.2 * reach),
            (x * 1.15, root[1] + 5.6 * rise, root[2] + 12.6 * reach * length),
            (x * 1.65, root[1] + 2.4 * rise, root[2] + 19.0 * reach * length),
        ]
        s.ribbon("tail", "tail_plume", pts, 4.0, 1.5,
                 ["feather_white", "feather_sky", "feather_azure", "feather_deep", "storm_glow"],
                 steps=3, taper=.52, split=("tail_tip", .4))
    # Swirling wind aura ribbons around the shoulders and flanks.
    swirls = (
        (-13.0, 16.5, 5.0, 10.5, 150, 340),
        (13.5, 20.0, 2.5, 11.5, 200, 395),
        (-9.5, C[1] + 6.5, C[2] - 2.0, 8.5, 130, 310),
        (9.5, C[1] + 6.5, C[2] - 2.0, 8.5, 210, 390),
    )
    for x, cy, cz, radius, a0, a1 in (swirls if adult else swirls[:2]):
        pts = []
        for i in range(7):
            a = radians(a0 + (a1 - a0) * i / 6)
            pts.append((x + (i - 3) * .5 * (1 if x > 0 else -1), cy + radius * sin(a) * .78, cz + radius * cos(a)))
        s.ribbon("body", "wind_ribbon", pts, 2.2, .32, ["wind_glow", "storm_glow"], steps=3, taper=.82)
    # Wide, arched Storm Griffin wings.
    for side in (-1, 1):
        shoulder = (side * 5.4, C[1] + 4.8, C[2] + 2.4)
        if adult:
            wrist = (shoulder[0] + side * 14.5, shoulder[1] + 7.2, shoulder[2] + 3.0)
            hand = (wrist[0] + side * 11.8, wrist[1] + 5.4, wrist[2] + 2.2)
            _storm_wing(s, side, shoulder, wrist, hand, 22.0, 14)
        else:
            wrist = (shoulder[0] + side * 11.5, shoulder[1] + 6.0, shoulder[2] + 2.6)
            hand = (wrist[0] + side * 9.2, wrist[1] + 4.4, wrist[2] + 1.8)
            _storm_wing(s, side, shoulder, wrist, hand, 17.5, 11)
    return s.finish(1.25 if adult else .88)


def _zephyros(stage):
    return _zephyros_baby(stage) if stage == "baby" else _zephyros_grown(stage)


# ---- Aetherius · Celestial Unicorn ---------------------------------------------------------------
AETHERIUS = {
    "coat": "#FFFFFF>#F6F0FC", "coat_shade": "#EAE0F8>#D2C0EC",
    "mane": "#FFD8F0>#E49CE0", "mane_light": "#FFF2FA>#FFD0EE",
    "crystal": "#FCEBFF>#C89EFF", "crystal_pink": "#FFE4F6>#FF78C8",
    "crystal_lilac": "#EED4FF>#AC64F4", "crystal_violet": "#ECD0FF>#A454F4",
    "crystal_blue": "#E4FFFF>#52DCFF", "crystal_cyan": "#E6FFFF>#48DCFF",
    "crystal_glow": "#FFF6FF>#E8C2FF", "gem_cyan": "#F2FFFF>#38E8FF",
    "veil_pink": "#FFE4F6>#EC96D8", "veil_lilac": "#EED8FF>#B888F4",
    "antler": "#FFFFFF>#DCEEFF", "gold": "#FFF2A8>#E4AE28", "gold_dark": "#C48E1E",
    "halo_gold": "#FFFFD8>#FFD854", "ring_glow": "#FFFFD8>#FFD44C",
    "gem": "#58E8FF", "hoof": "#FFF0A0>#DCA424", "hoof_gold": "#FFF2A8>#DCA628",
    "inner_ear": "#FFC6E4", "nose": "#F7C9DE", "star_glow": "#FFFFDC>#FFE078",
    "eye": "#2464D8>#8CE8FF", "eye_blue": "#205CD0>#88E4FF", "eye_rim": "#241448",
    "eye_socket": "#241448", "pupil": "#120A2C", "shine": "#FFFFFF", "blush": "#FFB8DA",
}


def _antler(s, bone, root, side, size, stage):
    """Baby branching crystal antler studded with sharp faceted pink, violet, and cyan crystal shards."""
    beam = (root[0] + side * size * .42, root[1] + size, root[2] + size * .34)
    s.blade(bone, "antler_beam", root, beam, size * .18 + .35, size * .18 + .35,
            ["antler", "antler", "crystal_pink"], bend=(side * size * .14, 0, -size * .1), steps=3, taper=.55)
    s.blade(bone, "antler_tip_shard", beam, (beam[0] + side * size * .12, beam[1] + size * .42, beam[2] + size * .15),
            size * .18 + .4, size * .12 + .3, ["crystal_pink", "crystal_glow"], steps=2, taper=.82)
    tines = (
        (.28, (side * .48, .36, -.18), .62, "crystal_cyan"),
        (.56, (side * .42, .44, .08), .58, "crystal_pink"),
        (.82, (side * .45, .3, .28), .48, "crystal_violet"),
    )
    for t, direction, reach, shard_mat in tines:
        p = bezier(root, (root[0] + side * size * .14, (root[1] + beam[1]) / 2, (root[2] + beam[2]) / 2 - size * .1), beam, t)
        end = (p[0] + direction[0] * size * reach * 1.65, p[1] + direction[1] * size * reach * 1.65, p[2] + direction[2] * size * reach * 1.65)
        s.blade(bone, "antler_tine", p, end, size * .15 + .35, size * .12 + .28,
                ["antler", shard_mat, shard_mat], bend=(0, size * .06, 0), steps=3, taper=.78)


def _aetherius_young(stage):
    s = Sculpt("aetherius", stage, AETHERIUS)
    k = dict(by=9.4, bw=8.8, bh=8.0, bd=12.4, hip=8.4, hy=18.2, hz=-7.8, hs=9.2, eye=2.5,
             leg_u=2.8, leg_l=2.1, antler=4.8, tail=7.6, mane=5)
    by, bw, bh, bd = k["by"], k["bw"], k["bh"], k["bd"]
    s.bone("body", "root", (0, by, 0))
    s.orb("body", "chest", (0, by + bh * .05, -bd * .24), (bw, bh * 1.05, bd * .52), "coat", round_=.62)
    s.orb("body", "barrel", (0, by - bh * .02, bd * .08), (bw * .94, bh * .94, bd * .56), "coat", round_=.62)
    s.orb("body", "rump", (0, by + bh * .06, bd * .32), (bw, bh * .96, bd * .4), "coat", round_=.62)
    s.box("body", "belly_shade", (0, by - bh * .46, bd * .05), (bw * .66, .5, bd * .6), "coat_shade")
    # Ornate golden chest collar & harness with a large glowing cyan diamond centerpiece gem.
    s.box("body", "collar", (0, by + bh * .28, -bd * .48), (bw * .9, bh * .18, 1.4), "gold", (12, 0, 0))
    s.plate("body", "chest_regalia", (0, by + bh * .04, -bd * .5 - .2), (0, .1, -1), (bw * .64, .7, bh * .52), "gold")
    s.gem("body", "pendant", (0, by + bh * .06, -bd * .5 - .65), bw * .24, "gem_cyan", "gold_dark")
    for side in (-1, 1):
        s.link("body", "flank_gold", (side * bw * .46, by + bh * .22, -bd * .36), (side * bw * .46, by + bh * .18, bd * .28), .65, .65, "gold")
        s.gem("body", "shoulder_gem", (side * (bw * .48 + .2), by + bh * .18, -bd * .2), 1.2, "crystal_pink", "gold", (0, side * 90, 45))
    x = bw * .31
    for tag, z, front in (("f", -bd * .32, True), ("b", bd * .32, False)):
        for side, suffix in ((-1, "l"), (1, "r")):
            hip = (side * x, k["hip"], z)
            knee = (side * x, k["hip"] * .5, z + (-.5 if front else 1.5))
            ankle = (side * x, 1.8, z - .2)
            thigh = None if front else ((side * x, k["hip"] - k["leg_u"] * .5, z + .3), (k["leg_u"] * 1.45, k["leg_u"] * 2.1, k["leg_u"] * 1.9))
            s.leg("leg_" + tag + suffix, hip, knee, ankle, k["leg_u"] * .88, k["leg_l"] * .8, "coat",
                  (k["leg_l"] * 1.15, 1.8, k["leg_l"] * 1.25), "hoof_gold", None, thigh=thigh, cuff="gold")
    neck_base = (0, by + bh * .34, -bd * .4)
    s.bone("neck", "body", neck_base)
    hy, hz, hs = k["hy"], k["hz"], k["hs"]
    mid = (0, (neck_base[1] + hy) / 2 + 1.2, (neck_base[2] + hz) / 2 + .5)
    s.chain("neck", "neck", [neck_base, mid, (0, hy - hs * .34, hz + hs * .18)], [bw * .52, bw * .46], [bw * .58, bw * .48], "coat")
    s.bone("head", "neck", (0, hy - hs * .3, hz + hs * .15))
    hd = hs * .88
    s.orb("head", "head", (0, hy, hz), (hs, hs * .88, hd), "coat", round_=.64)
    face = hz - hd / 2
    muzzle = hs * .32
    s.orb("head", "muzzle", (0, hy - hs * .2, face - muzzle * .42), (hs * .58, hs * .38, muzzle + 1.2), "coat", round_=.62)
    s.link("head", "nose_bridge", (0, hy + hs * .12, face + .4), (0, hy - hs * .06, face - muzzle * .75), hs * .36, hs * .22, "coat")
    for side in (-1, 1):
        s.box("head", "nostril", (side * hs * .12, hy - hs * .18, face - muzzle - .45), (hs * .08, hs * .06, .2), "nose")
        s.box("head", "blush", (side * hs * .32, hy - hs * .14, face - .12), (hs * .16, hs * .08, .2), "blush")
        ear = (side * hs * .38, hy + hs * .3, hz + hd * .12)
        tip = (side * hs * .68, hy + hs * .76, hz + hd * .24)
        s.blade("head", "ear", ear, tip, hs * .24, hs * .12, ["coat"], steps=2, taper=.65)
        s.blade("head", "inner_ear", (ear[0], ear[1] + .18, ear[2] - hs * .06), (tip[0] * .95, tip[1] - .4, tip[2] - hs * .06),
                hs * .14, .3, ["inner_ear"], steps=2, taper=.65)
        _antler(s, "head", (side * hs * .22, hy + hs * .4, hz + hd * .06), side, k["antler"], stage)
    s.eyes("head", hs, hy + hs * .06, face - .16, k["eye"], spread=.26, yaw=14)
    # Golden royal crown, central golden unicorn horn, and floating golden halo ring above the head.
    s.box("head", "diadem", (0, hy + hs * .34, face + .1), (hs * .64, hs * .12, .7), "gold")
    s.gem("head", "diadem_gem", (0, hy + hs * .38, face - .32), hs * .16, "gem_cyan", "gold_dark")
    s.blade("head", "gold_horn", (0, hy + hs * .44, face + .15), (0, hy + hs * .44 + 3.6, face - 1.4),
            hs * .16, hs * .16, ["gold", "gold", "halo_gold"], steps=3, taper=.82)
    s.ring("head", "baby_halo", (0, hy + hs * .92, hz + hd * .18), hs * .52, 14, .48, "halo_gold", tilt=72)
    s.blade("head", "forelock", (0, hy + hs * .44, hz - .2), (-hs * .1, hy + hs * .12, face - .4), hs * .38, hs * .2,
            ["mane_light", "mane"], bend=(0, .7, -.5), steps=3, taper=.5)
    for i in range(k["mane"]):
        t = i / max(1, k["mane"] - 1)
        p = bezier((0, hy + hs * .28, hz + hd * .38), (0, mid[1] + 2.2, mid[2] + 1.6), (0, neck_base[1] + bh * .1, neck_base[2] + bw * .28), t)
        for side in (-1, 1):
            end = (side * bw * .38, p[1] - bh * (.64 + .12 * t), p[2] + 2.0 + side * .25)
            mat = ["mane_light", "mane", "veil_lilac"] if (i + (side > 0)) % 2 else ["mane", "crystal_pink", "veil_pink"]
            s.blade("neck" if t > .15 else "head", "mane_lock", (side * .6, p[1] + .5, p[2]), end, 1.1, bw * .38, mat,
                    bend=(side * .9, .6, .8), steps=3, taper=.5)
    root = (0, by + bh * .32, bd * .48)
    s.bone("tail", "body", root)
    mid_t = (0, root[1] - k["tail"] * .16, root[2] + k["tail"] * .38)
    s.bone("tail_tip", "tail", mid_t)
    for side, mat in ((-1.3, ["veil_pink", "crystal_pink", "crystal_glow"]),
                      (-.45, ["veil_lilac", "crystal_cyan", "crystal_glow"]),
                      (.45, ["mane_light", "crystal_pink", "crystal_glow"]),
                      (1.3, ["veil_lilac", "crystal_violet", "crystal_glow"])):
        s.blade("tail", "tail_lock", (side * .7, root[1], root[2]), (side * 1.2, mid_t[1], mid_t[2]), bw * .32, bw * .26, mat[:2],
                steps=2, taper=.2)
        end = (side * bw * .34, mid_t[1] - k["tail"] * .42, mid_t[2] + k["tail"] * .36)
        s.blade("tail_tip", "tail_flow", (side * 1.2, mid_t[1], mid_t[2]), end, bw * .32, bw * .24, mat, bend=(side * .6, .4, 1.0),
                steps=3, taper=.65)
    return s.finish(.68)


def _crystal_antler(s, root, side, size):
    """Wide branching celestial antler studded with long, sharp faceted pink, violet, and cyan crystal shards."""
    shards = ("crystal_pink", "crystal_cyan", "crystal_violet")
    beam = [
        root,
        (root[0] + side * size * .46, root[1] + size * .22, root[2] + size * .18),
        (root[0] + side * size * .92, root[1] + size * .42, root[2] + size * .42),
        (root[0] + side * size * 1.28, root[1] + size * .58, root[2] + size * .68),
    ]
    s.ribbon("head", "antler_beam", beam, size * .16 + .55, size * .16 + .55, ["antler"], steps=2, taper=.48)
    tines = (
        (.18, 42, 34, .52),
        (.38, 52, 38, .58),
        (.58, 68, 42, .56),
        (.78, 46, 48, .50),
        (1.0, 58, 54, .44),
    )
    path = curve(beam, 4)
    for k, (t, yaw, pitch, reach) in enumerate(tines):
        p = path[min(len(path) - 1, int(t * (len(path) - 1)))]
        end = polar(p, size * reach, side * yaw, pitch)
        if t < 1.0:
            s.blade("head", "antler_tine", p, end, size * .1 + .45, size * .1 + .45, ["antler"], bend=(0, size * .06, 0), steps=2, taper=.48)
        # Long, sharp faceted crystal prism at the tip of every branch.
        shard_end = polar(end, size * .44, side * (yaw - 8), pitch + 6)
        s.blade("head", "antler_shard", lerp(p, end, .72), shard_end, size * .22 + .75, size * .14 + .48,
                [shards[k % 3], shards[k % 3], "crystal_glow"], steps=3, taper=.86)
        # Secondary angled crystal shard sprouting from the branch node.
        side_shard = polar(p, size * .36, side * (yaw + 36), pitch - 18)
        s.blade("head", "antler_shard", p, side_shard, size * .18 + .6, size * .12 + .4,
                [shards[(k + 1) % 3], "crystal_glow"], steps=2, taper=.86)


def _aetherius_grown(stage):
    """Celestial Stag-Unicorn: noble tapered deer head, golden crown & unicorn horn, wide crystal antlers
    with long faceted pink/violet/cyan shards, cascading pink/lavender mane, ornate golden breastplate with
    cyan diamond gem, symmetric slender legs, multi-plume crystal aurora tail, and golden halo rings."""
    s = Sculpt("aetherius", stage, AETHERIUS)
    adult = stage == "adult"
    up = 1.0 if adult else 0.0
    P, B, C = (0, 19.2, 7.2), (0, 18.8, 1.0), (0, 19.8 + 1.4 * up, -5.6)
    s.bone("body", "root", (0, 19.0, 0))
    s.orb("body", "chest", C, (9.2, 10.2, 9.0), "coat", round_=.62)
    s.orb("body", "barrel", B, (8.4, 9.0, 9.6), "coat", round_=.62)
    s.orb("body", "rump", P, (9.0, 9.4, 8.4), "coat", round_=.62)
    s.box("body", "belly_shade", (0, B[1] - 4.3, B[2]), (5.8, .5, 9.4), "coat_shade")
    # Ornate golden breastplate, collar, flank harness, and glowing cyan diamond centerpiece gem.
    collar = [(sin(radians(a)) * 4.0, C[1] + 4.2 - cos(radians(a)) * .8, C[2] - 2.6 - cos(radians(a)) * 2.8) for a in range(-120, 121, 30)]
    for a, b in zip(collar, collar[1:]):
        s.link("body", "collar", a, b, 1.35, 1.1, "gold", extend=.65)
    s.plate("body", "breastplate", (0, C[1] + .4, C[2] - 4.3), (0, .1, -1), (7.2, .9, 7.0), "gold")
    s.gem("body", "pendant", (0, C[1] - .8, C[2] - 5.1), 3.0 if adult else 2.4, "gem_cyan", "gold_dark")
    for side in (-1, 1):
        s.link("body", "chain", (side * 3.6, C[1] + 3.0, C[2] - 4.4), (side * 2.2, C[1] - .6, C[2] - 5.0), .8, .8, "gold_dark")
        s.link("body", "back_trim", (side * 4.2, C[1] + 4.2, C[2] + 1.0), (side * 4.0, P[1] + 4.0, P[2] - 1.0), .9, .9, "gold")
        s.link("body", "flank_strap", (side * 4.3, C[1] + 1.2, C[2] + .8), (side * 4.2, P[1] + 1.6, P[2] - .4), .75, .75, "gold")
        s.gem("body", "shoulder_gem", (side * 4.8, C[1] + 2.0, C[2] + .4), 1.5, "crystal_pink", "gold", (0, side * 90, 45))
        s.gem("body", "hip_gem", (side * 4.6, P[1] + 1.8, P[2] - .2), 1.3, "gem_cyan", "gold", (0, side * 90, 45))
    # Symmetric slender legs in base geometry (idle animation raises leg_fl gracefully).
    for tag, front in (("f", True), ("b", False)):
        for side, suffix in ((-1, "l"), (1, "r")):
            name = "leg_" + tag + suffix
            z = C[2] + .4 if front else P[2] + .2
            hip = (side * 3.1, (C[1] if front else P[1]) - 2.6, z)
            knee = (side * 3.3, 9.8 if front else 11.6, z + (-.3 if front else -2.2))
            ankle = (side * 3.3, 1.8, z - (.4 if front else .2))
            s.bone(name, "body", hip)
            if not front:
                s.orb(name, "thigh", (side * 3.4, hip[1] - 2.2, z - .6), (4.0, 7.2, 6.0), "coat", round_=.62)
            s.link(name, "upper", hip, knee, 2.8, 3.0, "coat", extend=1.4)
            low = s.bone(name + "_lower", name, knee)
            s.link(low, "shin", knee, ankle, 1.8, 1.8, "coat", extend=.8)
            s.box(low, "cuff", (ankle[0], ankle[1] + 1.5, ankle[2]), (2.4, 1.0, 2.4), "gold")
            d = unit([ankle[k] - knee[k] for k in range(3)])
            s.link(low, "hoof", ankle, [ankle[k] + d[k] * 1.7 for k in range(3)], 2.2, 2.4, "hoof_gold", extend=.4)
    # Long arched celestial neck and noble tapered deer head.
    if adult:
        neck = [(0, C[1] + 2.6, C[2] - 2.6), (0, C[1] + 6.8, C[2] - 5.8), (0, C[1] + 10.2, C[2] - 8.2)]
    else:
        neck = [(0, C[1] + 3.2, C[2] - 2.4), (0, C[1] + 8.8, C[2] - 4.4), (0, C[1] + 13.4, C[2] - 5.6)]
    s.bone("neck", "body", neck[0])
    s.chain("neck", "neck", curve(neck, 2), [5.2, 4.9, 4.5, 4.2], [5.6, 5.2, 4.8, 4.5], "coat")
    top = neck[-1]
    s.bone("head", "neck", top)
    H = (0, top[1] + (1.4 if adult else 1.8), top[2] - (1.6 if adult else 1.4))

    def hp(dx, dy, dz):
        return (H[0] + dx, H[1] + dy, H[2] + dz)

    s.orb("head", "skull", hp(0, 0, 0), (6.4, 5.8, 6.6), "coat", round_=.64)
    # Smoothly tapered deer snout & nasal bridge (no boxy 90-degree step).
    s.orb("head", "snout_mid", hp(0, -.8, -3.4), (4.8, 4.0, 4.4), "coat", round_=.62)
    s.orb("head", "muzzle", hp(0, -1.4, -5.6), (3.6, 3.0, 3.8), "coat", round_=.62)
    s.link("head", "nose_bridge", hp(0, 1.6, -1.6), hp(0, -.3, -6.8), 2.8, 1.6, "coat")
    s.box("head", "nose", hp(0, -1.0, -7.5), (1.8, 1.1, .45), "nose")
    for side in (-1, 1):
        s.fierce_eye("head", side, hp(side * 2.7, .6, -2.0), 2.1, 1.6, 30, iris="eye_blue", socket="eye_socket", slit=False, slant=-6)
        s.link("head", "lash", hp(side * 2.8, 1.8, -3.1), hp(side * 3.3, 2.1, -.6), .55, .55, "eye_socket")
        s.blade("head", "ear", hp(side * 2.5, 2.4, .9), hp(side * 7.4, 4.6, 2.6), 3.2, .95, ["coat"], steps=2, taper=.68)
        s.blade("head", "inner_ear", hp(side * 2.6, 2.4, .5), hp(side * 6.5, 4.3, 2.2), 1.9, .35, ["inner_ear"], steps=2, taper=.68)
        _crystal_antler(s, hp(side * 1.9, 2.8, .4), side, 12.2 if adult else 9.6)
    # Golden royal tiara, cyan brow gem, and spiraled golden unicorn horn.
    s.link("head", "tiara", hp(-2.7, 1.9, -1.9), hp(2.7, 1.9, -1.9), .95, .95, "gold")
    s.gem("head", "tiara_gem", hp(0, 2.3, -2.7), 1.2, "gem_cyan", "gold_dark")
    s.blade("head", "gold_horn", hp(0, 2.7, -1.6), hp(0, 6.8 if adult else 7.4, -5.2 if adult else -3.8), 1.45, 1.45,
            ["gold", "gold", "halo_gold"], steps=3, taper=.84)
    # Cascading pastel-pink and lavender mane locks down both sides of the neck.
    s.blade("head", "forelock", hp(0, 2.6, -.4), hp(.6, -.2, -3.2), 2.6, 1.1, ["mane_light", "mane"], bend=(0, .8, -.4), steps=3, taper=.5)
    path = curve(neck, 3)
    for i in range(7):
        p = path[min(len(path) - 1, len(path) - 1 - i)]
        for side in (-1, 1):
            root = (side * 1.1, p[1] + 1.6, p[2] + 2.4)
            pts = [
                root,
                (side * 3.2, root[1] - 1.6, root[2] + 2.2),
                (side * 4.2, root[1] - 5.2 - .4 * i, root[2] + 3.2),
                (side * 3.8, root[1] - 9.2 - .6 * i, root[2] + 4.8),
            ]
            mats = ["mane_light", "mane", "veil_lilac", "crystal_pink"] if (i + (side > 0)) % 2 else ["mane_light", "veil_pink", "mane", "crystal_cyan"]
            s.ribbon("neck", "mane_lock", pts, 4.0, 1.4, mats, steps=2, taper=.52)
    # Multi-plume faceted crystalline aurora tail.
    root = (0, P[1] + 2.4, P[2] + 3.8)
    s.bone("tail", "body", root)
    s.bone("tail_tip", "tail", (0, root[1] - 1.8, root[2] + 7.6))
    reach = 1.0 if adult else .84
    plumes = (
        (0, 0, ["veil_pink", "crystal_pink", "crystal_glow"]),
        (-2.8, 1.2, ["veil_lilac", "crystal_cyan", "crystal_glow"]),
        (2.8, 1.2, ["veil_lilac", "crystal_cyan", "crystal_glow"]),
        (-5.2, 2.8, ["mane_light", "crystal_pink", "crystal_violet"]),
        (5.2, 2.8, ["mane_light", "crystal_pink", "crystal_violet"]),
        (0, 3.8, ["crystal_cyan", "crystal_violet", "crystal_glow"]),
    )
    for x, drop, mats in plumes:
        pts = [
            (x * .2, root[1], root[2]),
            (x * .65, root[1] + 1.8 - drop * .25, root[2] + 5.2 * reach),
            (x * 1.05, root[1] - 1.4 - drop * .8, root[2] + 10.6 * reach),
            (x * 1.4, root[1] - 5.2 - drop, root[2] + 15.4 * reach),
            (x * 1.7, root[1] - 6.4 - drop * .7, root[2] + 19.8 * reach),
        ]
        s.ribbon("tail", "tail_veil", pts, 4.4, 1.8, mats, steps=3, taper=.52, split=("tail_tip", .36))
    # Celestial golden halo rings above the antlers/head and encircling the body.
    s.ring("head", "crown_halo", hp(0, 4.2 if adult else 6.2, 2.2 if adult else 2.4), 6.0 if adult else 5.0, 18, .65, "halo_gold", tilt=78 if adult else 68)
    rings = (((0, 23.0, 1.0), 13.5, 15, 315, 18),) if not adult else (
        ((0, 24.0, 1.0), 15.5, 0, 340, 14),
        ((0, 16.0, 2.0), 13.0, 180, 480, -12),
    )
    for center, radius, a0, a1, tilt in rings:
        pts = []
        for i in range(9):
            a = radians(a0 + (a1 - a0) * i / 8)
            pts.append((center[0] + radius * cos(a), center[1] + radius * sin(a) * sin(radians(tilt)), center[2] + radius * sin(a) * .8))
        s.ribbon("body", "halo_ring", pts, 1.0, .55, ["halo_gold", "ring_glow"], steps=3, taper=.35)
    for i, (x, y, z) in enumerate(((-9.5, 28.0, -4.0), (10.0, 29.0, 3.0), (-6.5, 25.0, 9.0), (8.5, 22.0, -8.0), (0, 30.0, 7.0))):
        if i >= (5 if adult else 3):
            break
        s.box("body", "star_sparkle", (x, y, z), (.55, 2.0, .55), "star_glow")
        s.box("body", "star_sparkle", (x, y, z), (2.0, .55, .55), "star_glow")
    return s.finish(1.25 if adult else .88)


def _aetherius(stage):
    return _aetherius_young(stage) if stage == "baby" else _aetherius_grown(stage)


# ---- Fenrir · Void Wolf ------------------------------------------------------------------------------
FENRIR = {
    "fur": "#262038>#151222", "fur_dark": "#161222>#0B0914", "fur_light": "#443A62>#28223C",
    "mane": "#322A4C>#1A152A", "fur_top": "#342C50>#1E1830", "muzzle_fur": "#3A3156>#221C34",
    "void_fur": "#221D32>#13101D", "void_fur_dark": "#171324>#0C0914",
    "fur_spike": "#3E345C>#221B34>#110D1B",
    "rune_glow": "#ECFFFF>#48DCFF", "rune_blue": "#F2FFFF>#44DCFF", "rune_cyan": "#F4FFFF>#3CE4FF",
    "vein_glow": "#DCFAFF>#34B8FF", "energy_glow": "#ECFFFF>#50D4FF",
    "crystal": "#D8FBFF>#38C8FF", "crystal_dark": "#24486E>#122640", "crystal_glow": "#F4FFFF>#78EAFF",
    "eye": "#2498F0>#B8F6FF", "eye_glow": "#F8FFFF>#58E2FF", "eye_rim": "#080610",
    "eye_socket": "#08050E", "pupil": "#08182C", "shine": "#FFFFFF",
    "claw": "#2C364C>#141A28", "claw_dark": "#262E42>#101420",
    "fang": "#FFFFFF>#E6F2FF", "nose": "#0E0B18", "inner_ear": "#4A3E68",
    "mouth": "#C83854>#681828", "blush": "#78D8FF",
}


def _rune_diamond(s, bone, center, side, size):
    """Glowing cyan double-diamond rune on a shoulder or flank."""
    x, y, z = center
    for scale, thick in ((1.0, .55), (.48, .48)):
        r = size * scale
        pts = [(x, y + r, z), (x, y, z + r * .92), (x, y - r, z), (x, y, z - r * .92)]
        for a, b in zip(pts, pts[1:] + pts[:1]):
            s.link(bone, "rune", a, b, thick, thick, "rune_blue", extend=thick)


def _vein(s, bone, top, bottom, side, width=.45):
    """Forked cyan lightning vein running down a wolf leg."""
    mid1 = lerp(top, bottom, .35)
    mid2 = lerp(top, bottom, .7)
    pts = [top, (mid1[0], mid1[1], mid1[2] + 1.0), (mid2[0], mid2[1], mid2[2] - .8), bottom]
    for a, b in zip(pts, pts[1:]):
        s.link(bone, "leg_vein", a, b, width, width, "vein_glow", extend=width)
    s.link(bone, "leg_vein", pts[1], (pts[1][0], pts[1][1] - 2.0, pts[1][2] + 2.0), width * .82, width * .82, "vein_glow", extend=width)


def _void_crystals(s, bone, base, count, size, spread, lean):
    """Jagged glowing cyan lightning/storm shards bursting from the mane and shoulders."""
    for i in range(count):
        t = i / max(1, count - 1) - .5
        root = (base[0] + t * spread[0], base[1], base[2] + t * spread[2])
        height = size * (1 - abs(t) * .62)
        tip = (root[0] + lean[0] * height + t * size * .35, root[1] + height * .72, root[2] + lean[2] * height + height * .55)
        s.blade(bone, "void_crystal", root, tip, size * .28 + .35, size * .24 + .3,
                ["crystal_dark", "crystal", "crystal_glow"], steps=3, taper=.82)


def _fenrir_young(stage):
    s = Sculpt("fenrir", stage, FENRIR)
    k = dict(by=7.8, bw=9.8, bh=8.6, bd=12.4, hip=6.6, hy=14.8, hz=-7.0, hs=10.0, eye=2.6,
             leg_u=3.6, leg_l=2.9, foot=(4.2, 2.0, 4.6), tail=8.2, ruff=9, ears=4.8)
    by, bw, bh, bd = k["by"], k["bw"], k["bh"], k["bd"]
    s.bone("body", "root", (0, by, 0))
    s.orb("body", "chest", (0, by + bh * .06, -bd * .24), (bw, bh * 1.08, bd * .54), "void_fur", round_=.62)
    s.orb("body", "barrel", (0, by - bh * .04, bd * .08), (bw * .92, bh * .9, bd * .56), "void_fur", round_=.62)
    s.orb("body", "rump", (0, by + bh * .02, bd * .32), (bw * .96, bh * .92, bd * .38), "void_fur", round_=.62)
    s.box("body", "belly_shade", (0, by - bh * .44, bd * .06), (bw * .62, .5, bd * .56), "void_fur_dark")
    # Swept-back dark wolf fur locks along the spine and chest.
    for i in range(4):
        z = -bd * .28 + i * bd * .2
        for side in (-1, 0, 1):
            base = (side * bw * .22, by + bh * .44, z)
            s.blade("body", "back_lock", base, (side * bw * .32, base[1] + 1.4, z + 3.6), 2.2, .9,
                    ["void_fur", "fur_spike"], steps=2, taper=.82)
    s.feather_patch("body", "chest_fur", (0, by + bh * .34, -bd * .5 - .8), bw * .58, 3, 4,
                    bh * .3, bw * .26, ["fur_light", "mane", "void_fur"], z_step=.28, row_scale=.94)
    # Glowing cyan diamond runes on shoulders and hips.
    for side in (-1, 1):
        _rune_diamond(s, "body", (side * (bw * .48 + .15), by + bh * .14, -bd * .22), side, 1.8)
        _rune_diamond(s, "body", (side * (bw * .47 + .15), by + bh * .08, bd * .28), side, 1.4)
        _void_crystals(s, "body", (side * bw * .34, by + bh * .42, -bd * .2), 2, 3.2, (bw * .08, 0, bd * .12), (side * .4, 0, .4))
    neck_base = (0, by + bh * .36, -bd * .42)
    for i in range(k["ruff"]):
        a = radians(-160 + 320 * i / max(1, k["ruff"] - 1))
        root = (sin(a) * bw * .46, by + bh * .34 + cos(a) * bh * .14, -bd * .4 + (1 - cos(a)) * 1.2)
        tip = (sin(a) * bw * .68, root[1] - bh * .36, root[2] + 2.8 - cos(a) * .6)
        s.blade("body", "ruff_lock", root, tip, bw * .28, bw * .2, ["void_fur", "fur_spike", "fur_light"],
                bend=(sin(a) * .8, .4, .6), steps=3, taper=.75)
    s.bone("neck", "body", neck_base)
    hy, hz, hs = k["hy"], k["hz"], k["hs"]
    s.chain("neck", "neck", [neck_base, (0, (neck_base[1] + hy) / 2, (neck_base[2] + hz) / 2), (0, hy - hs * .3, hz + hs * .2)],
            [bw * .6, bw * .54], [bw * .62, bw * .56], "void_fur")
    s.bone("head", "neck", (0, hy - hs * .3, hz + hs * .15))
    hd = hs * .88
    s.orb("head", "skull", (0, hy, hz), (hs, hs * .86, hd), "void_fur", round_=.62)
    face = hz - hd / 2
    snout = hs * .36
    s.orb("head", "muzzle", (0, hy - hs * .2, face - snout * .45), (hs * .56, hs * .42, snout + 1.2), "muzzle_fur", round_=.62)
    s.orb("head", "nose", (0, hy - hs * .08, face - snout - .35), (hs * .18, hs * .12, .9), "nose", round_=.6)
    s.box("head", "mouth_line", (0, hy - hs * .36, face - snout * .55), (hs * .42, .35, snout * .55), "void_fur_dark")
    for side in (-1, 1):
        for j in range(2):
            s.blade("head", "cheek_fur", (side * hs * .44, hy - hs * (.06 + .14 * j), hz + j * .6),
                    (side * hs * .76, hy - hs * (.28 + .12 * j), hz + hd * .42 + j * .6),
                    hs * .26, hs * .18, ["void_fur", "fur_spike"], steps=2, taper=.75)
        ear = (side * hs * .3, hy + hs * .36, hz + hd * .08)
        tip = (side * hs * .48, hy + hs * .36 + k["ears"], hz + hd * .26)
        s.blade("head", "ear", ear, tip, hs * .42, hs * .18, ["void_fur", "fur_top", "void_fur_dark"], steps=3, taper=.88)
        s.blade("head", "inner_ear", (ear[0], ear[1] + .3, ear[2] - hs * .08), (tip[0] * .95, tip[1] - 1.2, tip[2] - hs * .08),
                hs * .22, .3, ["inner_ear", "muzzle_fur"], steps=2, taper=.85)
        s.blade("head", "fang", (side * hs * .16, hy - hs * .32, face - snout * .78),
                (side * hs * .16, hy - hs * .48, face - snout * .74), hs * .08, hs * .08, ["fang"], steps=2)
    s.eyes("head", hs, hy + hs * .06, face - .16, k["eye"], spread=.26, yaw=14, lid="void_fur_dark", pupil=.36)
    s.box("head", "brow_rune", (0, hy + hs * .3, face - .12), (hs * .15, hs * .15, .35), "rune_blue", (0, 0, 45))
    for side in (-1, 1):
        s.link("head", "brow_vein", (side * hs * .08, hy + hs * .34, face - .1), (side * hs * .28, hy + hs * .44, face), .42, .3, "rune_blue")
    x = bw * .33
    for tag, z, front in (("f", -bd * .3, True), ("b", bd * .3, False)):
        for side, suffix in ((-1, "l"), (1, "r")):
            hip = (side * x, k["hip"], z)
            knee = (side * (x + .2), k["hip"] * .5, z + (-.8 if front else 1.5))
            ankle = (side * (x + .2), k["foot"][1], z - (.3 if front else -.2))
            thigh = ((side * x, k["hip"] - k["leg_u"] * .2, z + (.1 if front else .4)),
                     (k["leg_u"] * .95, k["leg_u"] * 1.22, k["leg_u"] * 1.18))
            _, low = s.leg("leg_" + tag + suffix, hip, knee, ankle, k["leg_u"] * .65, k["leg_l"] * .54, "void_fur",
                           k["foot"], "void_fur_dark", "claw_dark", 4, thigh=thigh)
            _vein(s, low, (side * (x + 1.1), knee[1] - .2, knee[2]), (side * (x + 1.0), k["foot"][1] + .6, ankle[2] - .2), side, .38)
    root = (0, by + bh * .22, bd * .46)
    s.bone("tail", "body", root)
    mid = (0, by + bh * .14, root[2] + k["tail"] * .44)
    s.bone("tail_tip", "tail", mid)
    end = (0, by - bh * .04, root[2] + k["tail"] * .9)
    s.orb("tail", "tail_base", lerp(root, mid, .55), (bw * .4, bw * .42, k["tail"] * .52), "void_fur", round_=.6)
    s.orb("tail_tip", "tail_brush", lerp(mid, end, .45), (bw * .52, bw * .52, k["tail"] * .56), "void_fur", round_=.6)
    for dx, dy in ((-1, 0), (1, 0), (0, 1), (-.7, .7), (.7, .7)):
        s.blade("tail_tip", "tail_tuft", (dx * bw * .14, end[1] + dy * bw * .14, end[2] - 1.6),
                (dx * bw * .24, end[1] + dy * bw * .2 - .6, end[2] + bw * .36), bw * .28, bw * .2, ["void_fur", "fur_spike"], steps=2, taper=.82)
    s.blade("tail_tip", "tail_crystal", (0, end[1] + bw * .12, end[2] - .6),
            (0, end[1] + bw * .12 + 2.4, end[2] + 2.8), 1.2, 1.0, ["crystal", "crystal_glow"], steps=3, taper=.8)
    return s.finish(.68)


def _fenrir_grown(stage):
    """Void/Storm Wolf: deep midnight-obsidian coat, broad predatory stance, snarling fanged jaws, slanted
    glowing cyan eyes, long swept-back razor fur locks over neck/shoulders/spine, cyan lightning shards,
    shoulder/thigh diamond runes, leg lightning veins, and swirling cyan storm energy arcs."""
    s = Sculpt("fenrir", stage, FENRIR)
    adult = stage == "adult"
    up = 1.0 if adult else 0.0
    P, B, C = (0, 17.2, 9.6), (0, 17.2 + .6 * up, 2.0), (0, 18.0 + 2.8 * up, -6.0)
    s.bone("body", "root", (0, 17.5, 0))
    s.orb("body", "chest", C, (13.4, 14.0, 11.8), "void_fur", round_=.62)
    s.orb("body", "waist", B, (10.6, 10.2, 10.2), "void_fur", round_=.62)
    s.orb("body", "hips", P, (11.6, 11.4, 9.8), "void_fur", round_=.62)
    # Long, swept-back razor-sharp wolf fur locks along the withers, shoulders, and spine
    # (pitched backward at 64..74 deg so they flow like a jagged wolf mane instead of upright cones).
    spine = curve([(0, C[1] + 6.8, C[2] - 1.2), (0, B[1] + 5.0, B[2]), (0, P[1] + 5.4, P[2] + 1.2)], 4)
    for i, p in enumerate(spine):
        f = i / (len(spine) - 1)
        grow = (1.22 if adult else 1.0) * (1.25 - .45 * f)
        for a, length in ((0, 9.6), (-32, 8.4), (32, 8.4), (-62, 6.8), (62, 6.8)):
            if abs(a) > 50 and i % 2:
                continue
            n = (sin(radians(a)), cos(radians(a)), 0)
            r = 5.8 - 1.0 * (abs(a) > 45)
            root = (p[0] + n[0] * r * .72, p[1] - 3.6 + n[1] * 3.8, p[2] - .6)
            tip = polar(root, length * grow * (1 - .08 * (i % 2)), a * .85, 68)
            s.blade("body", "fur_spike", root, tip, 3.2 - .5 * (abs(a) > 45), 1.1,
                    ["void_fur", "fur_spike", "void_fur_dark"], bend=(0, .8, .6), steps=3, taper=.86)
        # Jagged glowing cyan lightning shards emerging through the dorsal fur.
        if i % 2 == 0 and i < len(spine) - 2:
            for side in (-1, 1):
                c_root = (side * 2.4, p[1] - .4, p[2] + .2)
                c_tip = polar(c_root, (6.2 if adult else 4.8) * (1 - .25 * f), side * 24, 62)
                s.blade("body", "storm_shard", c_root, c_tip, 1.6, 1.2, ["crystal", "crystal_glow", "rune_blue"], steps=3, taper=.85)
    # Ragged dark chest and belly fur locks.
    for i in range(7):
        t = i / 6
        x = (t - .5) * 8.6
        top = (x, C[1] - 3.4 + abs(t - .5) * 2.0, C[2] - 5.4 + abs(t - .5) * 2.4)
        s.blade("body", "chest_lock", top, (x * 1.08, top[1] - 6.4, top[2] + 1.4), 3.0, 1.1,
                ["void_fur", "fur_spike", "fur_light"], steps=3, taper=.84)
    for z in (-1.2, 2.4, 5.8):
        for side in (-1, 1):
            top = (side * 3.4, B[1] - 4.2, z)
            s.blade("body", "belly_lock", top, (side * 4.2, top[1] - 3.8, z + 2.2), 2.6, 1.0,
                    ["void_fur", "fur_spike"], steps=2, taper=.84)
    # Prominent glowing cyan double-diamond runes on the shoulders and thighs.
    for side in (-1, 1):
        _rune_diamond(s, "body", (side * 7.3, C[1] + 2.4, C[2] + 1.6), side, 3.0 if adult else 2.5)
        _rune_diamond(s, "body", (side * 6.4, P[1] + 1.2, P[2] - .4), side, 2.3 if adult else 1.9)
    # Thick muscular wolf neck with a sweeping razor-layered ruff.
    if adult:
        neck = [(0, C[1] + 2.8, C[2] - 4.2), (0, C[1] + 5.4, C[2] - 7.8), (0, C[1] + 7.4, C[2] - 10.4)]
    else:
        neck = [(0, C[1] + 3.2, C[2] - 4.2), (0, C[1] + 5.6, C[2] - 7.6), (0, C[1] + 7.6, C[2] - 10.0)]
    s.bone("neck", "body", neck[0])
    s.chain("neck", "neck", neck, [8.8, 8.0], [9.0, 8.2], "void_fur")
    for ring, (t, radius, length, count) in enumerate(((.25, 5.6, 9.2, 13), (.68, 5.0, 8.0, 11))):
        c = lerp(neck[0], neck[-1], t)
        for i in range(count):
            a = radians(-160 + 320 * i / (count - 1))
            root = (sin(a) * radius * .82, c[1] + cos(a) * radius * .62, c[2] + .8)
            tip = (sin(a) * (radius + 3.8), root[1] + cos(a) * 1.8 - .6, root[2] + length * (1.22 if adult else 1.0))
            s.blade("neck", "mane_spike", root, tip, 3.4, 1.2,
                    ["void_fur", "fur_spike", "void_fur_dark"], bend=(sin(a) * .6, .5, .8), steps=3, taper=.86)
    # Fierce lupine head with wide snarling fanged jaws and blazing cyan eyes.
    top = neck[-1]
    s.bone("head", "neck", top)
    H = (0, top[1] + (1.3 if adult else 1.6), top[2] - 2.6)
    tilt = .4 if adult else .2

    def hp(dx, dy, dz):
        return (H[0] + dx, H[1] + dy + (-dz * .06 * tilt if dz < 0 else 0), H[2] + dz)

    s.orb("head", "skull", hp(0, 0, 0), (8.2, 6.8, 7.8), "void_fur", round_=.62)
    s.orb("head", "snout", hp(0, -.7, -6.2), (5.2, 3.8, 7.4), "void_fur", round_=.62)
    s.box("head", "nose", hp(0, .3, -10.0), (2.2, 1.5, 1.3), "nose")
    s.link("head", "snout_top", hp(0, 1.3, -2.8), hp(0, .8, -9.4), 2.8, 1.1, "void_fur_dark")
    s.link("head", "mouth", hp(0, -2.5, -2.0), hp(0, -3.5, -8.4), 4.0, 1.3, "mouth")
    s.link("head", "jaw", hp(0, -3.0, -1.4), hp(0, -5.6, -8.8), 4.4, 1.7, "void_fur")
    for side in (-1, 1):
        # Prominent upper and lower wolf canines + row of sharp white teeth.
        s.blade("head", "fang", hp(side * 1.65, -2.1, -8.6), hp(side * 1.65, -4.5, -8.4), .85, .85, ["fang"], steps=2)
        s.blade("head", "fang_low", hp(side * 1.5, -5.1, -7.6), hp(side * 1.5, -3.2, -7.4), .78, .78, ["fang"], steps=2)
        for z in (-6.8, -5.2, -3.8):
            s.blade("head", "tooth", hp(side * 1.8, -2.3, z), hp(side * 1.8, -3.3, z), .55, .55, ["fang"], steps=1)
        s.fierce_eye("head", side, hp(side * 3.1, 1.0, -3.4), 2.1, 1.05, 34, iris="eye_glow", slit=False, slant=16)
        s.link("head", "brow", hp(side * 1.1, 2.3, -4.2), hp(side * 4.0, 2.9, -2.4), 1.5, 1.3, "void_fur_dark")
        s.link("head", "brow_lightning", hp(side * 1.2, 2.5, -4.3), hp(side * 4.2, 3.3, -1.8), .5, .5, "rune_blue")
        # Long swept-back wolf ears and razor cheek ruffs.
        s.blade("head", "ear", hp(side * 2.6, 3.0, .4), hp(side * 4.4, 6.6 if adult else 7.8, 5.2 if adult else 4.8), 3.0, 1.1,
                ["void_fur", "fur_spike", "void_fur_dark"], steps=3, taper=.88)
        s.blade("head", "inner_ear", hp(side * 2.5, 3.2, .0), hp(side * 4.1, 5.8 if adult else 6.8, 4.4 if adult else 4.0), 1.5, .35, ["inner_ear"], steps=2, taper=.88)
        for i in range(4):
            base = hp(side * 3.6, -.4 - i * 1.1, -.8 + i * .9)
            s.blade("head", "cheek_spike", base, (base[0] + side * 3.2, base[1] - .4, base[2] + 5.2), 2.3, 1.0,
                    ["void_fur", "fur_spike", "void_fur_dark"], steps=3, taper=.88)
    s.box("head", "brow_rune", hp(0, 2.7, -4.1), (1.3, 1.3, .35), "rune_blue", (0, 0, 45))
    # Muscular wolf forelegs & hindlegs with lightning veins and 4-clawed paws.
    for side, tag in ((-1, "l"), (1, "r")):
        shoulder, elbow, ankle = (side * 6.2, C[1] - 2.0, C[2] - .4), (side * 6.6, C[1] * .48, C[2] + 1.2), (side * 6.6, 2.2, C[2] - 1.2)
        _, low = s.leg("leg_f" + tag, shoulder, elbow, ankle, 5.0, 3.8, "void_fur", (5.0, 2.5, 5.6), "void_fur_dark", "claw_dark", 4,
                       thigh=((side * 6.6, C[1] - 4.0, C[2] - .2), (5.8, 8.8, 6.8)))
        for k in range(2):
            s.blade("leg_f" + tag, "elbow_tuft", (side * 7.0, elbow[1] + 2.4 - k * 1.8, elbow[2] + 1.4),
                    (side * 8.0, elbow[1] - 1.2 - k * 1.4, elbow[2] + 5.6), 2.8, 1.0, ["void_fur", "fur_spike"], steps=2, taper=.88)
        _vein(s, "leg_f" + tag, (side * 8.8, C[1] - 1.2, C[2] - .4), (side * 8.4, elbow[1] + .4, elbow[2] + .4), side, .48)
        _vein(s, low, (side * 8.4, elbow[1] - .4, elbow[2] - .4), (side * 8.3, 3.2, ankle[2] - .6), side, .46)
    for side, tag in ((-1, "l"), (1, "r")):
        name = "leg_b" + tag
        hip, knee, hock, ankle = (side * 6.0, P[1] + .2, P[2] - .2), (side * 6.8, P[1] - 6.6, P[2] - 3.4), \
            (side * 6.8, 5.4, P[2] + 1.8), (side * 6.8, 2.2, P[2] - .2)
        s.bone(name, "body", hip)
        s.orb(name, "thigh", (side * 6.4, P[1] - 2.4, P[2] - 1.0), (5.8, 9.0, 8.0), "void_fur", round_=.62)
        for k in range(2):
            s.blade(name, "thigh_tuft", (side * 8.6, P[1] - .8 - k * 2.2, P[2] + 1.8),
                    (side * 9.6, P[1] - 5.0 - k * 1.8, P[2] + 6.4), 2.8, 1.0, ["void_fur", "fur_spike"], steps=2, taper=.88)
        s.link(name, "upper", hip, knee, 4.8, 5.0, "void_fur", extend=2.0)
        low = s.bone(name + "_lower", name, knee)
        s.link(low, "shank", knee, hock, 3.5, 3.5, "void_fur", extend=1.4)
        s.link(low, "metatarsus", hock, ankle, 3.0, 3.0, "void_fur_dark", extend=1.2)
        s.orb(low, "hind_paw", (side * 6.8, 1.2, P[2] - 1.6), (4.8, 2.5, 5.4), "void_fur_dark", round_=.66)
        for c in (-1.5, -.5, .5, 1.5):
            x = side * 6.8 + c * 1.1
            s.blade(low, "hind_claw", (x, 1.1, P[2] - 4.0), (x, .1, P[2] - 5.8), .75, .75, ["claw_dark"], steps=2, taper=.7)
        _vein(s, low, (side * 8.4, knee[1] - .4, knee[2] + .6), (side * 8.2, 3.2, ankle[2] + .4), side, .44)
    # Thick jagged wolf tail with swept fur blades and cyan lightning streaks.
    root = (0, P[1] + 2.6, P[2] + 4.0)
    s.bone("tail", "body", root)
    path = curve([root, (0, root[1] + 1.8, root[2] + 6.4), (0, root[1] + .6, root[2] + 13.0), (0, root[1] - 2.2, root[2] + 18.4)], 2)
    s.bone("tail_tip", "tail", path[len(path) // 2])
    for i, (a, b) in enumerate(zip(path, path[1:])):
        f = i / (len(path) - 1)
        bone = "tail" if i < len(path) // 2 else "tail_tip"
        s.link(bone, "tail", a, b, 4.6 - 1.6 * f, 4.6 - 1.6 * f, "void_fur", extend=1.6)
        for a2 in (-65, 0, 65, 180):
            n = (sin(radians(a2)), cos(radians(a2)), 0)
            base = (a[0] + n[0] * 1.8, a[1] + n[1] * 1.8, a[2])
            tip = (base[0] + n[0] * (3.8 - f), base[1] + n[1] * (3.8 - f), base[2] + 5.2)
            s.blade(bone, "tail_spike", base, tip, 3.0 - .8 * f, 1.2, ["void_fur", "fur_spike", "void_fur_dark"], steps=2, taper=.88)
        if i % 2 == 0:
            s.link(bone, "tail_lightning", (a[0], a[1] + 2.2, a[2]), (b[0], b[1] + 2.0, b[2] + 1.4), .48, .48, "rune_blue")
    # Crackling jagged cyan lightning bolts arcing along the shoulders, flanks, and spine.
    for side in (-1, 1):
        bolts = [
            [(side * 6.8, C[1] + 5.2, C[2] - 1.0), (side * 8.4, C[1] + 7.4, C[2] + 2.4),
             (side * 7.6, C[1] + 5.8, C[2] + 5.6), (side * 9.2, C[1] + 8.6, C[2] + 9.4)],
            [(side * 5.8, B[1] + 4.2, B[2] - 1.2), (side * 7.4, B[1] + 6.0, B[2] + 2.6),
             (side * 6.6, B[1] + 4.6, B[2] + 5.8), (side * 8.0, B[1] + 6.8, B[2] + 9.0)],
        ]
        for bolt in (bolts if adult else bolts[:1]):
            for a, b in zip(bolt, bolt[1:]):
                s.link("body", "storm_bolt", a, b, .58, .58, "rune_blue", extend=.5)
    return s.finish(1.28 if adult else .90)


def _fenrir(stage):
    return _fenrir_young(stage) if stage == "baby" else _fenrir_grown(stage)


def build(species, stage):
    if species not in SPECIES or stage not in STAGES:
        raise ValueError((species, stage))
    return globals()["_" + species](stage)
