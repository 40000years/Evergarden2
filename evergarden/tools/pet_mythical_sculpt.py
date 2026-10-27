"""Authored Blockbench sculptures for Evergarden's five mythical companions.

All dimensions are Blockbench pixels (16 px = one Minecraft block).  The
three ages have different anatomy, ornaments and plumage, rather than using a
display scale.  Parts remain attached to named animation bones.  This module
has no exporter or third-party dependencies.
"""

from math import atan2, degrees, sqrt


SPECIES = ("aetherius", "ignis_draconis", "zephyros", "solaris", "fenrir")
STAGES = ("baby", "medium", "adult")
NAMES = {
    "aetherius": "Aetherius · Celestial Unicorn",
    "ignis_draconis": "Ignis Draconis · Primordial Flame Dragon",
    "zephyros": "Zephyros · Storm Griffin",
    "solaris": "Solaris · Eternal Sun Phoenix",
    "fenrir": "Fenrir · Void Wolf",
}


class Sculpt:
    def __init__(self, species, stage, palette):
        self.species, self.stage = species, stage
        self.palette, self.parts = palette, []
        self.bones = {"root": (None, [0, 0, 0])}
        self.counter = 0

    def bone(self, name, parent, pivot):
        self.bones[name] = (parent, list(pivot))
        return name

    def box(self, bone, name, center, size, material, rotation=None, origin=None):
        self.counter += 1
        part = {
            "bone": bone,
            "name": "%s_%03d" % (name, self.counter),
            "from": [round(center[i] - size[i] / 2, 4) for i in range(3)],
            "size": [round(v, 4) for v in size],
            "material": material,
        }
        if rotation and any(rotation):
            part["rotation"] = [round(v, 4) for v in rotation]
            part["origin"] = list(origin or center)
        self.parts.append(part)

    def link(self, bone, name, start, end, width, depth, material):
        delta = [end[i] - start[i] for i in range(3)]
        length = sqrt(sum(v * v for v in delta))
        if length < .001:
            return
        center = [(start[i] + end[i]) / 2 for i in range(3)]
        rotation = [degrees(atan2(delta[2], sqrt(delta[0] ** 2 + delta[1] ** 2))),
                    0, -degrees(atan2(delta[0], delta[1]))]
        self.box(bone, name, center, (width, length, depth), material, rotation)

    def volume(self, bone, name, center, size, material, rotation=None, origin=None):
        """Broad continuous anatomy with shallow bevels, rather than a square block."""
        x, y, z = center
        w, h, d = size
        pivot = origin or center
        self.box(bone, name, center, (w, h * .74, d * .90), material, rotation, pivot)
        self.box(bone, name + "_side", center, (w * .88, h * .74, d), material, rotation, pivot)
        for side in (-1, 1):
            self.box(bone, name + "_bevel", (x, y + side * h * .435, z),
                     (w * .83, h * .14, d * .84), material, rotation, pivot)

    def eye(self, side, x, y, z, size, bone="head"):
        """Inset jewel eyes, a readable dark iris and a tiny catchlight."""
        self.box(bone, "eye_socket", (side * x, y, z), (.30, size * 1.23, size * 1.10), "shade")
        self.box(bone, "eye_jewel", (side * (x + .17), y, z - .05), (.25, size, size * .77), "eye")
        self.box(bone, "eye_pupil", (side * (x + .31), y - .03, z - .14), (.12, size * .78, size * .29), "pupil")
        self.box(bone, "eye_catchlight", (side * (x + .39), y + size * .23, z - size * .25),
                 (.13, size * .24, size * .21), "spark")

    def front_eyes(self, width, y, z, size, bone="head"):
        """Wrap expressive eyes onto the front face for player-facing poses."""
        for side in (-1, 1):
            x = side * width * .32
            rotation, pivot = (0, -side * 25, 0), (x, y, z)
            self.box(bone, "forward_eye_socket", (x, y, z), (size * .94, size * 1.12, .26), "shade", rotation, pivot)
            self.box(bone, "forward_eye_jewel", (x, y, z - .16), (size * .76, size * .91, .15), "eye", rotation, pivot)
            self.box(bone, "forward_eye_pupil", (x - side * size * .06, y - .03, z - .27),
                     (size * .30, size * .76, .10), "pupil", rotation, pivot)
            self.box(bone, "forward_eye_catchlight", (x - side * size * .17, y + size * .23, z - .35),
                     (size * .24, size * .24, .09), "spark", rotation, pivot)

    def point(self, bone, name, start, end, width, material, steps=3, depth=None):
        """Overlapping tapered crystal, horn, feather or fur lock."""
        for i in range(steps):
            a, b = i / steps, (i + 1.13) / steps
            p = [start[j] + (end[j] - start[j]) * a for j in range(3)]
            q = [start[j] + (end[j] - start[j]) * min(b, 1) for j in range(3)]
            taper = (1 - i / steps) ** .75
            self.link(bone, name, p, q, max(.25, width * taper),
                      max(.25, (depth or width) * taper), material)

    def finish(self, height):
        # Work in generous sculpting coordinates, then place each independently
        # authored age in its intended Minecraft size class.  A cub is roughly
        # one block high; an adult's rideable torso is about 1.5 blocks high,
        # with its head, crown and horns rising above the rider's saddle.
        units = {"baby": .65, "medium": .82, "adult": .90}[self.stage]
        self.bones = {name: (parent, [round(v * units, 5) for v in pivot])
                      for name, (parent, pivot) in self.bones.items()}
        for part in self.parts:
            for key in ("from", "size", "origin"):
                if key in part:
                    part[key] = [round(v * units, 5) for v in part[key]]
        return {"bones": self.bones, "parts": self.parts, "palette": self.palette,
                "height": height * units, "name": NAMES[self.species], "stage": self.stage}


def _legs(s, *, x, front, back, hip_y, upper, lower, paw, kind="fur", cuffs=False):
    """Four separately articulated legs with an actual hock and planted feet."""
    for side, suffix in ((-1, "l"), (1, "r")):
        for front_leg, z, tag in ((True, front, "f"), (False, back, "b")):
            bone = "leg_" + tag + suffix
            # The hind hock projects backwards while the foreleg is straighter.
            knee_z = z + (-.35 if front_leg else 1.7)
            knee_y = hip_y * (.48 if front_leg else .56)
            s.bone(bone, "body", (side * x, hip_y, z))
            s.link(bone, "forearm" if front_leg else "haunch", (side * x, hip_y, z),
                   (side * x, knee_y, knee_z), upper, upper * 1.10, "coat")
            if not front_leg:
                s.volume(bone, "rounded_haunch", (side * x, hip_y - upper * .42, z),
                      (upper * 1.26, upper * 1.45, upper * 1.42), "coat", (8, 0, side * -4))
            low = bone + "_lower"
            s.bone(low, bone, (side * x, knee_y, knee_z))
            s.link(low, "shin", (side * x, knee_y, knee_z), (side * x, paw[1], z - .35),
                   lower, lower * .95, "coat")
            if kind == "hoof":
                s.box(low, "gilded_fetlock", (side * x, paw[1] + .85, z - .35),
                      (paw[0] * .94, 1.15, paw[2] * .87), "gold")
                s.box(low, "celestial_hoof", (side * x, paw[1] / 2, z - .50), paw, "hoof")
                s.box(low, "hoof_glint", (side * x, .75, z - .51 - paw[2] / 2),
                      (paw[0] * .58, .45, .13), "gem")
            else:
                s.box(low, "soft_paw", (side * x, paw[1] / 2, z - .65), paw, "coat")
                for toe in (-1, 0, 1):
                    s.box(low, "talon" if kind == "scale" else "claw",
                          (side * x + toe * paw[0] * .27, .5, z - .75 - paw[2] / 2),
                          (.55 if s.stage == "baby" else .8, .8, 1.25), "claw", (-9, 0, 0))
                if cuffs:
                    s.box(low, "ankle_band", (side * x, paw[1] + 1.1, z - .2),
                          (lower + .55, .65, lower + .55), "gold")


def _aetherius(stage):
    palette = {"coat": "#F5F1E2", "shade": "#C6C2D7", "mane": "#8462CF",
               "mane_light": "#D0A3F1", "mane_deep": "#453D83", "gold": "#D8AF59",
               "gold_dark": "#977740", "gem": "#82E6F1", "horn": "#C7C5FF",
               "hoof": "#635A9A", "eye": "#61D9E5", "pupil": "#172541",
               "spark": "#FFF8E9", "glow": "#F1D4FF"}
    s = Sculpt("aetherius", stage, palette)
    # The foal has a round barrel, short face and enormous eyes; the adult has
    # a swan neck, elongated muzzle and a cascading galaxy mane.
    p = {
        "baby": dict(w=8.6, d=13, by=10.8, bh=8.7, leg=8.4, lx=3.2, f=-4.2, b=4.1,
                     ny=17.0, nz=-5.5, hy=21.4, hz=-8.2, hw=7.4, hh=7.5, hd=7,
                     horn=3.5, tail=8.5, es=2.0),
        "medium": dict(w=11.4, d=21, by=18.9, bh=10.9, leg=16.9, lx=4.3, f=-7.0, b=7,
                       ny=26.1, nz=-8.7, hy=32.6, hz=-12.2, hw=7.5, hh=8, hd=9,
                       horn=7.2, tail=13.5, es=1.8),
        "adult": dict(w=14.4, d=28.5, by=25.8, bh=13.4, leg=23.7, lx=5.4, f=-9.6, b=9.9,
                      ny=35.1, nz=-11.0, hy=43.2, hz=-15.3, hw=8.4, hh=9, hd=11.1,
                      horn=10.7, tail=20.5, es=1.75),
    }[stage]
    w, d, by, bh = (p[k] for k in ("w", "d", "by", "bh"))
    s.bone("body", "root", (0, by, 0))
    s.volume("body", "ivory_barrel", (0, by, 0), (w, bh, d), "coat")
    s.volume("body", "rounded_rump", (0, by + .3, d * .31), (w * 1.06, bh * .87, d * .40), "coat", (-5, 0, 0))
    s.volume("body", "proud_chest", (0, by + .4, -d * .30), (w * .89, bh * 1.06, d * .32), "coat", (7, 0, 0))
    s.box("body", "belly_shadow", (0, by - bh * .43, .8), (w * .73, 1.25, d * .65), "shade")
    _legs(s, x=p["lx"], front=p["f"], back=p["b"], hip_y=p["leg"],
          upper=w * .28, lower=w * .18, paw=(w * .28, 2.1 if stage == "baby" else 2.9, w * .33), kind="hoof")
    s.bone("neck", "body", (0, by + bh * .25, -d * .31))
    s.link("neck", "arched_neck", (0, by + bh * .15, -d * .31),
           (0, p["ny"] + 3, p["nz"]), w * .62, w * .73, "coat")
    s.volume("neck", "throat", (0, p["ny"] + 1, p["nz"] - 1.2),
          (w * .53, bh * .74, w * .55), "coat", (-18, 0, 0))
    hy, hz, hw, hh, hd = (p[k] for k in ("hy", "hz", "hw", "hh", "hd"))
    s.bone("head", "neck", (0, hy - hh * .25, hz + hd * .25))
    s.volume("head", "elegant_skull", (0, hy - hh * .04, hz), (hw, hh * .77, hd * .74), "coat", (8, 0, 0))
    s.box("head", "rounded_forehead", (0, hy + hh * .34, hz + .1), (hw * .85, hh * .27, hd * .66), "coat", (8, 0, 0))
    muzzle_z = hz - hd * .44
    s.volume("head", "soft_muzzle", (0, hy - hh * .22, muzzle_z), (hw * .77, hh * .51, hd * .50), "coat", (8, 0, 0))
    s.box("head", "velvet_nose", (0, hy - hh * .26, hz - hd * .68), (hw * .71, hh * .32, 1.05), "shade", (8, 0, 0))
    s.front_eyes(hw, hy + hh * .17, hz - hd * .395, p["es"] * .84)
    if stage == "baby":
        for side in (-1, 1):
            s.box("head", "foal_soft_cheek", (side * hw * .31, hy - hh * .22, hz - hd * .13),
                  (hw * .42, hh * .39, hd * .45), "coat", (0, side * 8, side * -5))
    for side in (-1, 1):
        s.box("head", "gentle_brow", (side * hw * .45, hy + p["es"] * .79, hz - hd * .13),
              (.8, .6, 2.8), "coat", (4, 0, side * -8))
        s.box("head", "nostril", (side * hw * .25, hy - hh * .14, hz - hd * .73),
              (.7, .55, .15), "hoof")
        ear_start = (side * hw * .33, hy + hh * .40, hz + hd * .14)
        ear_end = (side * hw * .49, hy + hh * (.91 if stage == "baby" else .96), hz + hd * .25)
        s.point("head", "tall_ear", ear_start, ear_end, hw * .24, "coat", 2, hw * .29)
        s.link("head", "ear_lilac_inset", (ear_start[0], ear_start[1] + .8, ear_start[2] - .5),
               (ear_end[0], ear_end[1] - .8, ear_end[2] - .5), hw * .11, .30, "mane_light")
    hbase = (0, hy + hh * .40, hz - hd * .16)
    hend = (0, hbase[1] + p["horn"], hbase[2] - p["horn"] * .30)
    s.box("head", "horn_coronet", hbase, (hw * .40, .8, hw * .38), "gold")
    s.point("head", "faceted_astral_horn", hbase, hend, hw * .25, "horn", 3 if stage == "baby" else 5)
    for i in range(1 if stage == "baby" else 3):
        t = .18 + i * .23
        center = [hbase[k] + (hend[k] - hbase[k]) * t for k in range(3)]
        s.box("head", "horn_gold_spiral", center, (hw * .25 * (1 - t) + .15, .38, hw * .25 * (1 - t) + .15), "gold", (-16, 0, 10))
    # Separated locks create the characteristic flowing silhouette even when
    # seen without textures.  Violet and pale pink alternate like a nebula.
    count = 3 if stage == "baby" else (5 if stage == "medium" else 7)
    for i in range(count):
        t = i / max(count - 1, 1)
        x = (-1 if i % 2 == 0 else 1) * w * .17
        a = (x, hy + hh * .36 - t * bh * .65, hz + hd * .32 + t * d * .20)
        b = (x + (-1 if i % 2 == 0 else 1) * w * .13,
             hy - hh * .25 - t * bh * .83, hz + hd * .65 + t * d * .28)
        s.point("neck" if i > 1 else "head", "galaxy_mane_lock", a, b,
                w * .25, "mane" if i % 2 == 0 else "mane_light", 3, w * .25)
        if stage != "baby" and i % 2 == 0:
            s.box("neck" if i > 1 else "head", "mane_starlight", (b[0] - .3, b[1] + 2, b[2] - .3),
                  (.55, .65, .55), "spark", (0, 0, 45))
    s.point("head", "forelock", (0, hy + hh * .53, hz + .4),
            (-hw * .22, hy + hh * .10, hz - hd * .35), hw * .37, "mane_light", 3, hw * .29)
    # Curled silken tail, with a pale central strand.
    t0 = (0, by + bh * .28, d * .48)
    s.bone("tail", "body", t0)
    t1 = (0, t0[1] + p["tail"] * .17, t0[2] + p["tail"] * .42)
    t2 = (p["tail"] * .13, t0[1] - p["tail"] * .38, t0[2] + p["tail"] * .82)
    t3 = (p["tail"] * .22, t0[1] - p["tail"] * .61, t0[2] + p["tail"] * .62)
    s.link("tail", "tail_root", t0, t1, w * .22, w * .26, "coat")
    s.bone("tail_tip", "tail", t1)
    s.point("tail_tip", "flowing_tail", t1, t2, w * .46, "mane", 3, w * .48)
    s.point("tail_tip", "turned_tail_tip", t2, t3, w * .31, "mane_light", 3)
    s.point("tail_tip", "silver_tail_strand", (t1[0] - .6, t1[1], t1[2] - .5),
            (t2[0] - .6, t2[1] - .5, t2[2] - .8), w * .13, "mane_light", 3)
    # The foal wears a little star charm.  Mature forms have a ceremonial
    # celestial breastplate and a slim mount blanket that leaves the mane free.
    s.box("neck", "breast_collar", (0, by + bh * .34, -d * .48), (w * .89, 1.1, 1.15), "gold", (12, 0, 0))
    s.box("neck", "star_setting", (0, by + bh * .22, -d * .50 - .6), (w * .25, w * .25, .9), "gold", (0, 0, 45))
    s.box("neck", "star_heart", (0, by + bh * .22, -d * .50 - 1.1), (w * .16, w * .16, .35), "gem", (0, 0, 45))
    if stage != "baby":
        s.box("body", "royal_saddlecloth", (0, by + bh * .49, 1), (w * .74, 1.0, d * .40), "mane_deep")
        s.box("body", "saddlecloth_gold_edge", (0, by + bh * .53, 1), (w * .78, .45, d * .43), "gold")
        s.box("body", "lilac_cushion", (0, by + bh * .58, 1), (w * .63, 1, d * .32), "mane")
        for side in (-1, 1):
            s.box("body", "hanging_starlight_tabard", (side * w * .48, by + bh * .15, 1.0),
                  (.9, bh * .65, d * .26), "mane_deep")
            s.box("body", "tabard_gem", (side * w * .52, by + bh * .18, 1),
                  (.5, 2.1, 2.1), "gem", (45, 0, 0))
    return s.finish((hend[1] + .5) / 16)


def _dragon_wings(s, p):
    """Hand-built stepped membranes with exposed finger spars and hooked tips."""
    span, by, d = p["span"], p["by"], p["d"]
    baby = s.stage == "baby"
    for side, suffix in ((-1, "l"), (1, "r")):
        shoulder = (side * p["w"] * .37, by + p["bh"] * .41, -d * .17)
        elbow = (side * span * .49, shoulder[1] + span * .35, shoulder[2] + span * .10)
        wrist = (side * span * .90, shoulder[1] + span * .53, shoulder[2] + span * .17)
        wing = s.bone("wing_" + suffix, "body", shoulder)
        tip = s.bone("wingtip_" + suffix, wing, elbow)
        s.link(wing, "wing_upper_arm", shoulder, elbow, p["w"] * .20, p["w"] * .22, "coat")
        s.link(tip, "wing_leading_edge", elbow, wrist, p["w"] * .14, p["w"] * .15, "coat")
        s.box(tip, "wing_wrist_gem", wrist, (p["w"] * .16, p["w"] * .18, p["w"] * .16), "lava")
        s.point(tip, "wing_thumb_hook", wrist,
                (wrist[0] + side * span * .08, wrist[1] + span * .15, wrist[2] - span * .12),
                p["w"] * .13, "horn", 2)
        # Five narrow connected strips describe a bat wing without flat giant
        # rectangles; stepped trailing edges make each scallop readable.
        strips = 4 if baby else 7
        for j in range(strips):
            t = j / max(strips - 1, 1)
            xx = span * (.20 + .77 * t)
            top_y = shoulder[1] + span * (.12 + .45 * t)
            bottom_y = shoulder[1] + span * (-.19 + .30 * t)
            z0 = shoulder[2] + span * (.22 + .10 * t)
            length = top_y - bottom_y
            # Broad vertical panels form a swept bat-wing fan.  Unlike a
            # paper-thin horizontal strip, the membrane reads from the front
            # and the normal three-quarter view used in the pet collection.
            s.box(wing if t < .42 else tip, "ember_membrane", (side * xx, (top_y + bottom_y) / 2, z0),
                  (span / strips * 1.20, length, .65 if baby else .9), "membrane", (0, 0, side * 18))
            if not baby and j % 2 == 0:
                s.box(wing if t < .42 else tip, "membrane_inner_glow", (side * xx, (top_y + bottom_y) / 2, z0 - .51),
                      (span / strips * .72, length * .71, .14), "wing_glow", (0, 0, side * 18))
        for j in range(3 if baby else 4):
            t = j / (2 if baby else 3)
            end = (side * span * (.28 + .68 * t), shoulder[1] + span * (-.22 + .30 * t),
                   shoulder[2] + span * (.21 + .10 * t))
            s.link(tip, "wing_finger_spar", wrist, end, p["w"] * .065, p["w"] * .065, "horn")
            s.point(tip, "finger_tip", end,
                    (end[0] + side * .6, end[1] - 1.2, end[2] + 1.3), p["w"] * .065, "horn", 2)


def _ignis_draconis(stage):
    palette = {"coat": "#343744", "shade": "#1B2030", "scale": "#555166",
               "belly": "#A67854", "lava": "#FF892F", "glow": "#FFE3A3",
               "membrane": "#A73D36", "wing_glow": "#EA6B3E", "gold": "#C59056",
               "horn": "#C8BAA4", "claw": "#D8CFBA", "eye": "#FFBD50",
               "pupil": "#25192B", "spark": "#FFF7CF"}
    s = Sculpt("ignis_draconis", stage, palette)
    p = {
        "baby": dict(w=9.7, d=13.5, by=10, bh=9.5, leg=7.5, lx=4.0, f=-4.2, b=4.4,
                     hy=18.4, hz=-8.0, hw=9.6, hh=8.4, hd=8.5, span=10.5, horn=3.5, es=2.2),
        "medium": dict(w=14.0, d=23, by=16.0, bh=12.0, leg=12, lx=5.9, f=-7.5, b=7.7,
                       hy=29.0, hz=-13, hw=10.5, hh=9.0, hd=11.2, span=22.0, horn=6.5, es=1.9),
        "adult": dict(w=18.3, d=31.8, by=21.5, bh=14.5, leg=16.3, lx=7.4, f=-10.2, b=10.4,
                      hy=37.5, hz=-18.4, hw=12.1, hh=10.4, hd=14.0, span=33.5, horn=9.7, es=1.9),
    }[stage]
    w, d, by, bh = (p[k] for k in ("w", "d", "by", "bh"))
    s.bone("body", "root", (0, by, 0))
    s.volume("body", "obsidian_barrel", (0, by, 1), (w, bh * .84, d * .82), "coat")
    s.box("body", "rounded_spinal_cap", (0, by + bh * .39, 1), (w * .79, bh * .23, d * .76), "scale")
    s.volume("body", "armored_shoulders", (0, by + .7, -d * .25), (w * 1.07, bh * .90, d * .36), "coat", (5, 0, 0))
    s.volume("body", "tapered_hips", (0, by - .3, d * .35), (w * .85, bh * .76, d * .38), "coat", (-5, 0, 0))
    s.box("body", "warm_belly", (0, by - bh * .39, -.7), (w * .70, bh * .20, d * .73), "belly")
    for i in range(3 if stage == "baby" else 5):
        count = 3 if stage == "baby" else 5
        t = i / max(count - 1, 1)
        s.box("body", "overlapping_breast_scute", (0, by + bh * (.26 - .57 * t), -d * .455 - .30),
              (w * (.60 - .18 * t), bh * (.21 if stage == "baby" else .15), 1.25), "belly", (-5, 0, 0))
        if stage != "baby" and i % 2 == 0:
            s.box("body", "breast_ember_seam", (0, by + bh * (.28 - .57 * t), -d * .455 - 1.0),
                  (w * (.44 - .13 * t), .40, .20), "lava")
    _legs(s, x=p["lx"], front=p["f"], back=p["b"], hip_y=p["leg"], upper=w * .29,
          lower=w * .19, paw=(w * .29, 2.5 if stage == "baby" else 3.1, w * .38), kind="scale")
    s.bone("neck", "body", (0, by + bh * .20, -d * .32))
    s.link("neck", "rising_dragon_neck", (0, by + bh * .18, -d * .34),
           (0, p["hy"] - p["hh"] * .10, p["hz"] + p["hd"] * .27), w * .53, w * .58, "coat")
    for i in range(2 if stage == "baby" else 4):
        t = i / (2 if stage == "baby" else 4)
        s.box("neck", "throat_scute", (0, by + bh * .44 + t * (p["hy"] - by - bh * .35),
                                        -d * .40 - t * (abs(p["hz"]) - d * .27)),
              (w * (.44 - .07 * t), 1.15, w * .15), "belly", (-26, 0, 0))
    hy, hz, hw, hh, hd = (p[k] for k in ("hy", "hz", "hw", "hh", "hd"))
    s.bone("head", "neck", (0, hy - hh * .25, hz + hd * .29))
    s.volume("head", "dragon_skull", (0, hy - hh * .06, hz), (hw, hh * .80, hd * .70), "coat")
    s.box("head", "dragon_forehead_cap", (0, hy + hh * .34, hz + .2), (hw * .85, hh * .27, hd * .61), "scale")
    s.volume("head", "broad_soft_snout", (0, hy - hh * .17, hz - hd * .44), (hw * .84, hh * .54, hd * .62), "coat", (3, 0, 0))
    s.bone("jaw", "head", (0, hy - hh * .30, hz + 1))
    s.box("jaw", "rounded_lower_jaw", (0, hy - hh * .38, hz - hd * .31), (hw * .73, hh * .18, hd * .74), "belly")
    s.box("head", "nose_bridge", (0, hy + .35, hz - hd * .64), (hw * .43, hh * .20, hd * .23), "scale")
    s.front_eyes(hw, hy + hh * .23, hz - hd * .365, p["es"] * .86)
    for side in (-1, 1):
        s.box("head", "swept_brow", (side * hw * .46, hy + hh * .25, hz - hd * .12),
              (1.5, 1.15, hw * .44), "scale", (-8, 0, side * -9))
        s.box("head", "warm_nostril", (side * hw * .27, hy + hh * .025, hz - hd * .78),
              (1.0, .60, .18), "lava")
        horn_a = (side * hw * .34, hy + hh * .37, hz + hd * .20)
        horn_b = (side * hw * .56, hy + hh * .37 + p["horn"] * .66, hz + hd * .20 + p["horn"] * .45)
        horn_c = (side * hw * .62, hy + hh * .37 + p["horn"], hz + hd * .20 + p["horn"] * .20)
        s.point("head", "crown_horn_base", horn_a, horn_b, hw * .20, "horn", 2)
        s.point("head", "crown_horn_tip", horn_b, horn_c, hw * .13, "horn", 2)
        s.point("head", "cheek_fin", (side * hw * .44, hy - hh * .08, hz + hd * .23),
                (side * hw * .78, hy + hh * .16, hz + hd * .52), hw * .19, "scale", 2)
        if stage != "baby":
            s.point("head", "cheek_ember", (side * hw * .48, hy - hh * .02, hz + hd * .29),
                    (side * hw * .66, hy + hh * .11, hz + hd * .47), hw * .075, "lava", 2)
    s.point("head", "central_crown_ridge", (0, hy + hh * .45, hz + .1),
            (0, hy + hh * .45 + p["horn"] * .48, hz + hd * .15), hw * .19, "lava", 3)
    # Interleaved armor plates and bright seams avoid a monochrome silhouette.
    plates = 3 if stage == "baby" else (4 if stage == "medium" else 5)
    for i in range(plates):
        z = -d * .32 + i * d * .66 / max(plates - 1, 1)
        s.box("body", "dorsal_armor", (0, by + bh * .47, z), (w * .58, 1.7, d / plates * .80), "scale", (0, 0, 0))
        s.point("body", "ember_dorsal_spine", (0, by + bh * .53, z),
                (0, by + bh * .53 + w * (.17 + .10 * (1 - abs(i - plates / 2) / plates)), z + 1.5),
                w * .14, "lava", 2)
        if stage != "baby":
            for side in (-1, 1):
                s.box("body", "flank_lava_fissure", (side * w * .507, by + bh * .08, z),
                      (.25, bh * .24, d / plates * .47), "lava", (side * 19, 0, 0))
    _dragon_wings(s, p)
    # An upward hook makes a compact, friendly tail pose instead of a long
    # straight extrusion; the distal bone is independently animated.
    t0 = (0, by + 1, d * .48)
    t1 = (w * .14, by - 1.6, d * .85)
    t2 = (w * .54, by - .8, d * 1.10)
    t3 = (w * .71, by + w * .35, d * 1.12)
    t4 = (w * .54, by + w * .61, d * .98)
    s.bone("tail", "body", t0)
    s.link("tail", "tail_base", t0, t1, w * .37, w * .39, "coat")
    s.link("tail", "tail_middle", t1, t2, w * .28, w * .29, "coat")
    s.bone("tail_tip", "tail", t2)
    s.link("tail_tip", "tail_curl", t2, t3, w * .21, w * .22, "coat")
    s.point("tail_tip", "tail_upturned_tip", t3, t4, w * .17, "coat", 2)
    s.point("tail_tip", "tail_ember_blade", (t4[0], t4[1] - 1, t4[2]),
            (t4[0] - w * .12, t4[1] + w * .26, t4[2] - w * .09), w * .23, "lava", 3, w * .10)
    for i, t in enumerate((.25, .6)):
        pos = [t0[j] + (t1[j] - t0[j]) * t for j in range(3)]
        s.point("tail", "tail_ridge", pos, (pos[0], pos[1] + w * .25, pos[2] + 1.7), w * .13, "lava", 2)
    return s.finish(max(hy + hh * .4 + p["horn"], by + bh * .41 + p["span"] * .68) / 16)


def _feather_wings(s, p, phoenix=False):
    """Layered coverts over long individually articulated primary feathers."""
    span, w, by, bh, d = (p[k] for k in ("span", "w", "by", "bh", "d"))
    baby = s.stage == "baby"
    primary_count = 4 if baby else (6 if s.stage == "medium" else 7)
    covert_count = 3 if baby else (4 if s.stage == "medium" else 5)
    for side, suffix in ((-1, "l"), (1, "r")):
        root = (side * w * .40, by + bh * .36, -d * .12)
        elbow = (side * span * .49, root[1] + span * .23, root[2] + span * .12)
        wrist = (side * span * .79, root[1] + span * .32, root[2] + span * .26)
        wing = s.bone("wing_" + suffix, "body", root)
        tip = s.bone("wingtip_" + suffix, wing, elbow)
        s.link(wing, "wing_shoulder", root, elbow, w * .37, w * .37, "coat")
        s.link(tip, "wing_forearm", elbow, wrist, w * .26, w * .28, "wing")
        # Scalloped coverts sit on a second row so feather edges cast shadows.
        for j in range(covert_count):
            t = j / max(covert_count - 1, 1)
            a = (side * span * (.18 + .49 * t), root[1] + span * (.13 + .23 * t), root[2] + span * .12 * t)
            b = (side * span * (.32 + .47 * t), a[1] - span * .26, a[2] + span * (.18 - .04 * t))
            s.point(wing if t < .48 else tip, "layered_wing_covert", a, b,
                    w * .26, "gold" if phoenix and j % 2 else "wing", 2, w * .17)
        for j in range(primary_count):
            t = j / max(primary_count - 1, 1)
            a = (side * span * (.36 + .42 * t), root[1] + span * (.20 + .14 * t), root[2] + span * (.14 + .12 * t))
            b = (side * span * (.47 + .56 * t), a[1] - span * (.48 - .04 * t), a[2] + span * (.28 - .08 * t))
            material = "flame" if phoenix and j % 2 else "feather"
            s.point(tip, "long_primary_feather", a, b, w * .25, material, 3, w * .14)
            # Contrasting terminal feather pieces read as ember tips / silver
            # lightning flight edges and are real geometry, not decals.
            c = [a[k] + (b[k] - a[k]) * .77 for k in range(3)]
            s.link(tip, "iridescent_flight_tip", c, b, w * .10, w * .11,
                   "glow" if phoenix else "silver")


def _zephyros(stage):
    palette = {"coat": "#48727D", "shade": "#244451", "wing": "#A7C3CE",
               "feather": "#5B8A9D", "silver": "#DCE6EA", "mane": "#E1E8EA",
               "mane_shade": "#B3C7D0", "gold": "#D4AD61", "beak": "#E9C777",
               "claw": "#E8D9AC", "eye": "#64F0EE", "pupil": "#143442",
               "spark": "#FCFFEF", "glow": "#8AF4F3", "storm": "#4CD3DA"}
    s = Sculpt("zephyros", stage, palette)
    p = {
        "baby": dict(w=9, d=13, by=10.5, bh=9, leg=7.4, lx=3.9, f=-3.7, b=4.0,
                     hy=20.2, hz=-6.5, hw=8.5, hh=8.6, hd=7.5, span=11, es=2.15),
        "medium": dict(w=13, d=22, by=17.5, bh=11.5, leg=13.1, lx=5.6, f=-7, b=7,
                       hy=30.4, hz=-10.8, hw=9.4, hh=9.3, hd=9, span=23, es=1.95),
        "adult": dict(w=16.5, d=29.5, by=24, bh=14, leg=18.4, lx=6.9, f=-9.5, b=9.7,
                      hy=41, hz=-14.6, hw=11, hh=10.5, hd=10.5, span=34, es=1.9),
    }[stage]
    w, d, by, bh = (p[k] for k in ("w", "d", "by", "bh"))
    s.bone("body", "root", (0, by, 0))
    s.volume("body", "leonine_torso", (0, by, 1), (w, bh, d * .80), "coat")
    s.volume("body", "lion_haunches", (0, by - .7, d * .34), (w * 1.03, bh * .91, d * .41), "coat", (-7, 0, 0))
    s.volume("body", "eagle_breast", (0, by + 2.1, -d * .29), (w * .92, bh * 1.12, d * .42), "mane", (9, 0, 0))
    s.box("body", "breast_shade", (0, by - bh * .20, -d * .45), (w * .64, bh * .70, 1.6), "mane_shade", (12, 0, 0))
    _legs(s, x=p["lx"], front=p["f"], back=p["b"], hip_y=p["leg"],
          upper=w * .28, lower=w * .17, paw=(w * .28, 2.2 if stage == "baby" else 3.0, w * .35), kind="fur")
    # Avian front legs have slim gold greaves and strongly hooked talons.
    for side, suffix in ((-1, "l"), (1, "r")):
        low = "leg_f" + suffix + "_lower"
        s.box(low, "eagle_greave", (side * p["lx"], p["leg"] * .23, p["f"] - .3),
              (w * .19, p["leg"] * .28, w * .20), "gold")
        s.point("leg_f" + suffix, "feathered_foreleg", (side * p["lx"], p["leg"] - 1, p["f"] + .5),
                (side * p["lx"], p["leg"] * .58, p["f"] + w * .19), w * .25, "mane", 2)
    s.bone("neck", "body", (0, by + bh * .27, -d * .32))
    s.link("neck", "upright_ruff", (0, by + bh * .31, -d * .31),
           (0, p["hy"] - p["hh"] * .21, p["hz"] + 1.1), w * .66, w * .60, "mane")
    hy, hz, hw, hh, hd = (p[k] for k in ("hy", "hz", "hw", "hh", "hd"))
    s.bone("head", "neck", (0, hy - hh * .20, hz + hd * .26))
    s.volume("head", "eagle_skull", (0, hy, hz), (hw, hh, hd * .78), "mane")
    s.box("head", "front_face", (0, hy - .5, hz - hd * .35), (hw * .75, hh * .62, hd * .35), "silver")
    s.front_eyes(hw, hy + hh * .16, hz - hd * .54, p["es"] * .83)
    s.box("head", "golden_beak_bridge", (0, hy - hh * .13, hz - hd * .58), (hw * .51, hh * .35, hd * .45), "beak", (5, 0, 0))
    s.point("head", "curved_eagle_beak", (0, hy - hh * .10, hz - hd * .74),
            (0, hy - hh * .43, hz - hd * .84), hw * .38, "beak", 2, hw * .24)
    s.box("head", "beak_seam", (0, hy - hh * .29, hz - hd * .69), (hw * .44, .25, hd * .30), "gold")
    for side in (-1, 1):
        s.point("head", "swept_silver_eyebrow", (side * hw * .45, hy + hh * .26, hz - hd * .37),
                (side * hw * .59, hy + hh * .32, hz + hd * .19), hw * .19, "silver", 2, hw * .13)
        # Griffin ears become swept feather horns only after adolescence.
        s.point("head", "eagle_ear_tuft", (side * hw * .35, hy + hh * .37, hz + hd * .12),
                (side * hw * .61, hy + hh * (.85 if stage == "baby" else 1.00), hz + hd * .42),
                hw * .24, "wing", 3, hw * .18)
        for i in range(2 if stage == "baby" else 3):
            s.point("head", "cheek_feather", (side * hw * .39, hy + hh * (.12 - i * .16), hz + hd * .12),
                    (side * hw * (.68 - i * .035), hy + hh * (.17 - i * .17), hz + hd * .54),
                    hw * .22, "mane" if i % 2 == 0 else "mane_shade", 2, hw * .17)
    # Cascading chest feather collar, rather than a rectangular bib.
    for i in range(3 if stage == "baby" else 5):
        x = (i - (1 if stage == "baby" else 2)) * w * .15
        s.point("neck", "thunder_ruff_feather", (x, by + bh * .65, -d * .46),
                (x * 1.20, by + bh * (.10 + abs(x) / w * .25), -d * .52 - 1.2),
                w * .22, "silver" if i % 2 == 0 else "mane_shade", 2, w * .13)
    _feather_wings(s, p)
    # The lion tail curls upward and ends in a small storm feather fan.
    t0 = (0, by + bh * .05, d * .47)
    t1 = (w * .18, by - bh * .10, d * .83)
    t2 = (w * .36, by + bh * .18, d * 1.02)
    s.bone("tail", "body", t0)
    s.link("tail", "lion_tail_base", t0, t1, w * .13, w * .13, "coat")
    s.bone("tail_tip", "tail", t1)
    s.link("tail_tip", "lion_tail_curl", t1, t2, w * .11, w * .11, "coat")
    for j in range(3):
        s.point("tail_tip", "storm_tail_plume", t2,
                (t2[0] + (j - 1) * w * .13, t2[1] + bh * (.37 - abs(j - 1) * .09), t2[2] + w * .18),
                w * .19, "feather" if j != 1 else "silver", 2)
    # Stylized lightning in solid raised geometry along both flanks.
    if stage != "baby":
        for side in (-1, 1):
            x = side * w * .515
            points = [(x, by + bh * .22, d * .12), (x, by + bh * .02, d * .03),
                      (x, by + bh * .03, d * .18), (x, by - bh * .19, d * .08)]
            for a, b in zip(points, points[1:]):
                s.link("body", "flank_lightning", a, b, .35, .7, "storm")
        s.box("body", "sky_saddlecloth", (0, by + bh * .49, 2), (w * .59, 1.1, d * .30), "shade")
        s.box("body", "saddle_gilt_trim", (0, by + bh * .53, 2), (w * .64, .45, d * .34), "gold")
    s.box("neck", "storm_medallion_setting", (0, by + bh * .49, -d * .51), (w * .25, w * .25, .9), "gold", (0, 0, 45))
    s.box("neck", "storm_medallion", (0, by + bh * .49, -d * .51 - .5), (w * .15, w * .15, .35), "glow", (0, 0, 45))
    return s.finish((hy + hh) / 16)


def _solaris(stage):
    palette = {"coat": "#E98F36", "shade": "#933F3A", "wing": "#E9B74F",
               "feather": "#C44D40", "flame": "#EE823E", "gold": "#F4CF69",
               "glow": "#FFE9AE", "iridescent": "#A56BBD", "tail": "#CC4747",
               "tail_light": "#F3AC4B", "claw": "#E8CA7F", "beak": "#FFE0A1",
               "eye": "#7BE9E0", "pupil": "#503045", "spark": "#FFFCE2"}
    s = Sculpt("solaris", stage, palette)
    p = {
        "baby": dict(w=9.4, d=10.5, by=9.4, bh=11.4, hy=18.5, hz=-4.0, hw=8.6, hh=8.8, hd=7,
                     span=10.8, feet=3.5, tail=11, es=2.1),
        "medium": dict(w=12, d=15, by=18, bh=17, hy=32, hz=-6.9, hw=9.0, hh=9.4, hd=8.2,
                       span=23, feet=8.8, tail=25, es=1.9),
        "adult": dict(w=15.5, d=20.5, by=26.5, bh=23, hy=44.5, hz=-9.4, hw=10.0, hh=10.6, hd=10,
                      span=36, feet=13.8, tail=38, es=1.8),
    }[stage]
    w, d, by, bh = (p[k] for k in ("w", "d", "by", "bh"))
    s.bone("body", "root", (0, by, 0))
    s.volume("body", "phoenix_breast", (0, by + bh * .05, -.5), (w, bh * .66, d * .73), "coat", (8, 0, 0))
    s.box("body", "phoenix_shoulder_round", (0, by + bh * .36, -.15), (w * .78, bh * .25, d * .66), "gold", (8, 0, 0))
    s.box("body", "phoenix_tapered_belly", (0, by - bh * .33, -.2), (w * .73, bh * .30, d * .64), "flame", (8, 0, 0))
    s.volume("body", "rounded_back", (0, by - bh * .15, d * .24), (w * .82, bh * .82, d * .64), "feather", (-12, 0, 0))
    s.box("body", "sunlit_belly", (0, by - bh * .04, -d * .31), (w * .71, bh * .57, d * .26), "gold", (8, 0, 0))
    s.bone("neck", "body", (0, by + bh * .35, -d * .22))
    s.link("neck", "phoenix_neck", (0, by + bh * .32, -d * .23),
           (0, p["hy"] - p["hh"] * .22, p["hz"] + 1), w * .51, w * .50, "coat")
    hy, hz, hw, hh, hd = (p[k] for k in ("hy", "hz", "hw", "hh", "hd"))
    s.bone("head", "neck", (0, hy - hh * .22, hz + hd * .24))
    s.volume("head", "phoenix_skull", (0, hy, hz), (hw, hh, hd * .77), "gold")
    s.box("head", "face_mask", (0, hy - hh * .06, hz - hd * .34), (hw * .77, hh * .66, hd * .28), "coat")
    s.front_eyes(hw, hy + hh * .16, hz - hd * .49, p["es"] * .82)
    s.box("head", "upper_beak", (0, hy - hh * .13, hz - hd * .55), (hw * .42, hh * .27, hd * .45), "beak", (8, 0, 0))
    s.point("head", "hooked_beak_tip", (0, hy - hh * .10, hz - hd * .72),
            (0, hy - hh * .37, hz - hd * .83), hw * .30, "beak", 2, hw * .20)
    for side in (-1, 1):
        s.point("head", "golden_brow", (side * hw * .45, hy + hh * .26, hz - hd * .25),
                (side * hw * .55, hy + hh * .30, hz + hd * .25), hw * .14, "glow", 2)
        for j in range(2 if stage == "baby" else 3):
            s.point("head", "phoenix_cheek_feather", (side * hw * .39, hy + hh * (.10 - .17 * j), hz + hd * .14),
                    (side * hw * (.60 - .04 * j), hy + hh * (.21 - .15 * j), hz + hd * (.58 + .04 * j)),
                    hw * .20, "flame" if j % 2 else "gold", 2)
    # A three-pronged baby crest develops into the adult's open sun crown.
    crown_count = 3 if stage == "baby" else (5 if stage == "medium" else 7)
    for j in range(crown_count):
        t = (j - (crown_count - 1) / 2) / ((crown_count - 1) / 2)
        a = (t * hw * .24, hy + hh * .40, hz + hd * .16)
        b = (t * hw * .65, hy + hh * (.88 + .25 * (1 - abs(t))), hz + hd * (.38 + .18 * abs(t)))
        s.point("head", "sun_crown_flame", a, b, hw * .18, "flame" if j % 2 else "gold", 3, hw * .13)
        if stage == "adult" and j % 2 == 0:
            s.box("head", "crown_ember_jewel", (b[0], b[1] - .2, b[2]), (.75, 1.1, .65), "glow", (0, 0, -t * 22))
    # Breast feathers form a V-shaped solar motif.
    for i in range(3 if stage == "baby" else 5):
        x = (i - (1 if stage == "baby" else 2)) * w * .15
        s.point("body", "layered_breast_feather", (x, by + bh * .29, -d * .45),
                (x * .84, by - bh * (.20 - abs(x) / w * .25), -d * .49),
                w * .23, "glow" if i % 2 else "gold", 2, w * .11)
    # Two avian legs and splayed toes. Unused back leg bones are intentionally
    # absent: this is an anatomically distinct biped, not a quadruped reskin.
    for side, suffix in ((-1, "l"), (1, "r")):
        x = side * w * .24
        hip = (x, by - bh * .31, d * .10)
        knee = (x, max(2.8, p["feet"] * .57), d * .30)
        ankle = (x, 1.35, -d * .02)
        bone = s.bone("leg_f" + suffix, "body", hip)
        s.link(bone, "feathered_thigh", hip, knee, w * .23, w * .24, "feather")
        lower = s.bone("leg_f" + suffix + "_lower", bone, knee)
        s.link(lower, "golden_tarsus", knee, ankle, w * .11, w * .13, "gold")
        s.box(lower, "bird_foot", (x, .8, -d * .04), (w * .28, 1.5, w * .24), "gold")
        for toe in (-1, 0, 1):
            end = (x + toe * w * .15, .45, -d * .04 - w * (.36 - abs(toe) * .06))
            s.link(lower, "splayed_talon", (x + toe * w * .06, .8, -d * .09), end,
                   .70 if stage == "baby" else .90, .65, "claw")
    _feather_wings(s, p, phoenix=True)
    # Three fluffy juvenile ribbons become five long two-jointed adult flame
    # streamers.  Every streamer carries iridescent eye spots and gold tips.
    root = (0, by - bh * .19, d * .46)
    s.bone("tail", "body", root)
    streamers = 3 if stage == "baby" else (5 if stage == "medium" else 5)
    for j in range(streamers):
        t = (j - (streamers - 1) / 2) / ((streamers - 1) / 2)
        tl = p["tail"] * (1 - abs(t) * .17)
        a = (t * w * .15, root[1], root[2])
        b = (t * w * .64, root[1] - tl * .21, root[2] + tl * .47)
        c = (t * w * .89, max(1.8, root[1] - tl * .43), root[2] + tl * .91)
        e = (t * w * 1.03, max(2.3, root[1] - tl * .38), root[2] + tl * 1.10)
        bone = s.bone("tail_plume_%d" % j, "tail", a)
        s.point(bone, "flame_streamer_root", a, b, w * .22, "tail", 2, w * .14)
        s.point(bone, "flame_streamer_length", b, c, w * .26, "tail_light" if j % 2 else "flame", 3, w * .14)
        s.point(bone, "golden_streamer_tip", c, e, w * .21, "glow", 2, w * .12)
        if stage != "baby":
            spot = [b[k] + (c[k] - b[k]) * .40 for k in range(3)]
            s.box(bone, "iridescent_tail_ocellus", (spot[0], spot[1] + .65, spot[2]),
                  (w * .19, .35, w * .33), "iridescent", (12, 0, -t * 11))
            s.box(bone, "ocellus_sunheart", (spot[0], spot[1] + .87, spot[2]),
                  (w * .09, .25, w * .17), "gold", (12, 0, -t * 11))
    return s.finish((hy + hh * 1.15) / 16)


def _fenrir(stage):
    palette = {"coat": "#2B3157", "shade": "#151D35", "fur": "#474F7B",
               "mane": "#656B97", "silver": "#C0C6DC", "muzzle": "#A2A8C5",
               "rune": "#AD82F1", "glow": "#D2B2FF", "gold": "#8F91B5",
               "claw": "#BFC5DD", "eye": "#B893FF", "pupil": "#201D36",
               "spark": "#F8F2FF"}
    s = Sculpt("fenrir", stage, palette)
    p = {
        "baby": dict(w=9.3, d=12.6, by=10.0, bh=9.5, leg=7.0, lx=3.7, f=-3.9, b=4.0,
                     hy=18.3, hz=-7.0, hw=9.2, hh=8.8, hd=7.8, tail=12, es=2.15),
        "medium": dict(w=12.6, d=22.3, by=17.8, bh=11.5, leg=13.7, lx=5.0, f=-7.2, b=7.6,
                       hy=29.8, hz=-12.1, hw=10.1, hh=9.5, hd=10.5, tail=20, es=1.9),
        "adult": dict(w=16.2, d=30.0, by=25.0, bh=14.1, leg=19.1, lx=6.6, f=-10.1, b=10.3,
                      hy=40.4, hz=-16.0, hw=11.6, hh=10.6, hd=13.3, tail=28, es=1.95),
    }[stage]
    w, d, by, bh = (p[k] for k in ("w", "d", "by", "bh"))
    s.bone("body", "root", (0, by, 0))
    s.volume("body", "midnight_torso", (0, by, .5), (w, bh, d * .84), "coat")
    s.volume("body", "high_wolf_shoulders", (0, by + bh * .10, -d * .26), (w * 1.03, bh * 1.08, d * .41), "fur", (6, 0, 0))
    s.volume("body", "lean_hips", (0, by - bh * .08, d * .35), (w * .81, bh * .84, d * .36), "coat", (-7, 0, 0))
    s.box("body", "silver_belly", (0, by - bh * .40, 0), (w * .65, bh * .17, d * .57), "mane")
    _legs(s, x=p["lx"], front=p["f"], back=p["b"], hip_y=p["leg"],
          upper=w * .29, lower=w * .19, paw=(w * .31, 2.5 if stage == "baby" else 3.2, w * .38), kind="fur")
    for side, suffix in ((-1, "l"), (1, "r")):
        for tag, z in (("f", p["f"]), ("b", p["b"])):
            s.point("leg_" + tag + suffix, "leg_fur_tuft", (side * p["lx"], p["leg"] * .77, z + .3),
                    (side * (p["lx"] + w * .08), p["leg"] * .43, z + w * .22), w * .21, "fur", 2)
    s.bone("neck", "body", (0, by + bh * .25, -d * .32))
    s.link("neck", "proud_wolf_neck", (0, by + bh * .28, -d * .31),
           (0, p["hy"] - p["hh"] * .16, p["hz"] + p["hd"] * .22), w * .70, w * .74, "fur")
    hy, hz, hw, hh, hd = (p[k] for k in ("hy", "hz", "hw", "hh", "hd"))
    s.bone("head", "neck", (0, hy - hh * .24, hz + hd * .23))
    s.volume("head", "wolf_skull", (0, hy - hh * .04, hz), (hw, hh * .80, hd * .72), "coat")
    s.box("head", "rounded_wolf_forehead", (0, hy + hh * .33, hz + .2), (hw * .82, hh * .28, hd * .65), "coat")
    s.volume("head", "soft_silver_muzzle", (0, hy - hh * .21, hz - hd * .43), (hw * .70, hh * .45, hd * .61), "muzzle", (4, 0, 0))
    s.box("head", "velvet_dark_nose", (0, hy - hh * .12, hz - hd * .76), (hw * .47, hh * .23, hd * .13), "shade", (5, 0, 0))
    s.box("head", "nose_glint", (-hw * .11, hy - hh * .05, hz - hd * .832), (hw * .15, .35, .10), "fur")
    s.front_eyes(hw, hy + hh * .17, hz - hd * .375, p["es"] * .92)
    s.bone("jaw", "head", (0, hy - hh * .31, hz + .3))
    s.box("jaw", "gentle_lower_jaw", (0, hy - hh * .38, hz - hd * .32), (hw * .61, hh * .15, hd * .69), "silver")
    for side in (-1, 1):
        s.point("head", "silver_eyebrow", (side * hw * .45, hy + hh * .27, hz - hd * .27),
                (side * hw * .54, hy + hh * .30, hz + hd * .14), hw * .14, "silver", 2, hw * .10)
        # Stacked tapers form triangular ears with visible indigo interiors.
        ear0 = (side * hw * .33, hy + hh * .37, hz + hd * .10)
        ear1 = (side * hw * .47, hy + hh * (.90 if stage == "baby" else .95), hz + hd * .20)
        s.point("head", "pointed_wolf_ear", ear0, ear1, hw * .34, "fur", 3, hw * .28)
        s.point("head", "ear_void_inset", (ear0[0], ear0[1] + 1.2, ear0[2] - hw * .13),
                (ear1[0], ear1[1] - 1.2, ear1[2] - hw * .10), hw * .15, "shade", 2, .30)
        for i in range(2 if stage == "baby" else 3):
            s.point("head", "cheek_fur_lock", (side * hw * .39, hy + hh * (.04 - .15 * i), hz + hd * .10),
                    (side * hw * (.74 - .035 * i), hy - hh * (.02 + .18 * i), hz + hd * .43),
                    hw * .24, "mane" if i == 0 else "fur", 2, hw * .22)
    # Thick, many-pointed ruff has a silver outer edge and a dark inner layer.
    ruff = 3 if stage == "baby" else (4 if stage == "medium" else 5)
    for side in (-1, 1):
        for i in range(ruff):
            t = i / max(ruff - 1, 1)
            a = (side * w * (.39 + .09 * t), by + bh * (.69 - .47 * t), -d * (.34 - .10 * t))
            b = (side * w * (.67 + .05 * t), by + bh * (.40 - .70 * t), -d * (.28 - .10 * t))
            s.point("neck", "layered_lunar_ruff", a, b, w * .30,
                    "silver" if i % 2 == 0 else "mane", 3, w * .25)
    for i in range(3):
        x = (i - 1) * w * .17
        s.point("neck", "chest_fur_lock", (x, by + bh * .49, -d * .47),
                (x * .8, by - bh * (.22 - abs(i - 1) * .12), -d * .50),
                w * .27, "silver" if i == 1 else "mane", 3, w * .15)
    # A diamond forehead mark and angular crescent runes are raised luminous
    # stone inlays.  Baby has one mark; mature wolves develop flank sigils.
    s.box("head", "forehead_void_star", (0, hy + hh * .28, hz - hd * .37),
          (hw * .20, hw * .20, .35), "rune", (0, 0, 45))
    s.box("head", "forehead_star_heart", (0, hy + hh * .28, hz - hd * .40),
          (hw * .09, hw * .09, .32), "glow", (0, 0, 45))
    if stage != "baby":
        for side in (-1, 1):
            x = side * w * .513
            for z in (d * .07, d * .26):
                pts = [(x, by + bh * .22, z), (x, by + bh * .06, z - 1.2),
                       (x, by - bh * .12, z), (x, by + bh * .04, z + 1.2)]
                for a, b in zip(pts, pts[1:] + pts[:1]):
                    s.link("body", "void_flank_rune", a, b, .30, .65, "rune")
            s.link("body", "rune_constellation", (x, by + bh * .04, d * .11),
                   (x, by + bh * .04, d * .22), .28, .35, "glow")
    # A big soft tail is carried in an upward crescent; contrasted side locks
    # create a wolf silhouette rather than the dragon's narrow curled spine.
    t0 = (0, by + bh * .08, d * .47)
    t1 = (-w * .10, by + bh * .09, d * .47 + p["tail"] * .35)
    t2 = (-w * .25, by + bh * .38, d * .47 + p["tail"] * .69)
    t3 = (-w * .32, by + bh * .81, d * .47 + p["tail"] * .73)
    s.bone("tail", "body", t0)
    s.link("tail", "bushy_tail_root", t0, t1, w * .43, w * .45, "coat")
    s.bone("tail_tip", "tail", t1)
    s.link("tail_tip", "bushy_tail_crescent", t1, t2, w * .49, w * .47, "fur")
    s.point("tail_tip", "silver_tail_tip", t2, t3, w * .45, "silver", 3, w * .43)
    for side in (-1, 1):
        for i in range(2 if stage == "baby" else 3):
            t = .15 + i * .24
            a = [t1[k] + (t2[k] - t1[k]) * t for k in range(3)]
            a[0] += side * w * .14
            b = (a[0] + side * w * .17, a[1] - w * .18, a[2] + w * .18)
            s.point("tail_tip", "tail_fur_lock", a, b, w * .24, "mane" if i % 2 else "fur", 2)
    return s.finish((hy + hh * 1.02) / 16)


def build(species, stage):
    """Return a fresh, deterministic sculpture for one mythical growth form."""
    if species not in SPECIES:
        raise ValueError("Unknown mythical species: %s" % species)
    if stage not in STAGES:
        raise ValueError("Unknown growth stage: %s" % stage)
    return {"aetherius": _aetherius, "ignis_draconis": _ignis_draconis,
            "zephyros": _zephyros, "solaris": _solaris, "fenrir": _fenrir}[species](stage)


if __name__ == "__main__":
    for species in SPECIES:
        for stage in STAGES:
            model = build(species, stage)
            print("%-17s %-6s %3d cubes, %2d bones, %.2f blocks high" %
                  (species, stage, len(model["parts"]), len(model["bones"]), model["height"]))
