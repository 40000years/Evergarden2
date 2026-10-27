"""Distinct, articulated Blockbench sculptures for the twelve rare Evergarden pets.

Geometry is expressed in world-space Blockbench units (16 units = one block).
Every growth stage is sculpted afresh: infants have oversized faces and paws,
adolescents grow their silhouette, and adults gain the complete ornamental set.
This module is intentionally independent of the resource-pack exporter.
"""
from __future__ import annotations

import math


class Sculpt:
    def __init__(self, stage, palette):
        aliases = {"child": "baby", "juvenile": "medium", "young": "medium", "grown": "adult"}
        self.stage = aliases.get(stage, stage)
        if self.stage not in ("baby", "medium", "adult"):
            raise ValueError("stage must be baby, medium, or adult")
        self.age = ("baby", "medium", "adult").index(self.stage)
        self.scale = (0.55, 0.78, 1.0)[self.age]
        # Young animals carry big heads on small bodies: the baby head is two thirds
        # larger relative to its body than the adult's, the adolescent's a fifth larger.
        self.head_scale = (0.92, 0.95, 1.0)[self.age]
        self.bones = {"root": (None, [0, 0, 0]), "body": ("root", [0, 0, 0])}
        self.parts = []
        self.palette = palette
        self.serial = 0

    def bone(self, name, parent, pivot):
        self.bones[name] = (parent, [round(v, 4) for v in pivot])
        return name

    def box(self, name, bone, center, size, mat, rotation=None, origin=None):
        self.serial += 1
        p = {"name": f"{name}_{self.serial}", "bone": bone,
             "from": [round(c - s / 2, 4) for c, s in zip(center, size)],
             "size": [round(max(0.06, v), 4) for v in size], "material": mat}
        if rotation:
            p["rotation"] = list(rotation)
            p["origin"] = list(origin or center)
        self.parts.append(p)

    def mass(self, name, bone, center, size, mat):
        """A broad voxel volume with shallow bevels, never a pile of thin slabs."""
        x, y, z = center
        w, h, d = size
        self.box(name+"_core", bone, (x, y, z), (w, h*.70, d), mat)
        for side in (-1, 1):
            self.box(name+"_bevel", bone, (x, y+side*h*.425, z),
                     (w*.80, h*.15+.015, d*.82), mat)

    def ball(self, name, bone, center, size, mat):
        """A soft voxel volume rounded on every edge: three crossed slabs and a chamfered core."""
        x, y, z = center
        w, h, d = size
        self.box(name+"_core", bone, (x, y, z), (w*.84, h*.84, d*.84), mat)
        self.box(name+"_wide", bone, (x, y, z), (w, h*.6, d*.6), mat)
        self.box(name+"_tall", bone, (x, y, z), (w*.6, h, d*.6), mat)
        self.box(name+"_deep", bone, (x, y, z), (w*.6, h*.6, d), mat)

    def disc(self, name, bone, center, width, height, depth, mat):
        """A flat round plate facing forward (eyes, face discs, spots): a plus of two boxes."""
        x, y, z = center
        self.box(name+"_w", bone, (x, y, z), (width, height*.62, depth), mat)
        # Slightly shallower, so the two front faces never share a plane and flicker.
        self.box(name+"_h", bone, (x, y, z + depth*.05), (width*.62, height, depth*.9), mat)

    def crescent(self, name, bone, center, radius, thickness, depth, mat, segments=5, opening=90):
        """A crescent moon of short tangent bars on an arc, horns pointing towards {@code opening} degrees."""
        x, y, z = center
        for j in range(segments):
            a = math.radians(opening + 180 - 140 / 2 + 140 * j / (segments - 1))
            length = 2 * radius * math.tan(math.radians(140 / (segments - 1) / 2)) + thickness * .6
            # Thicker in the middle of the arc, tapering to the horns.
            w = thickness * (1 - .45 * abs(j - (segments - 1) / 2) / ((segments - 1) / 2))
            self.box(name, bone, (x + math.cos(a) * radius, y + math.sin(a) * radius, z),
                     (length, w, depth), mat, (0, 0, math.degrees(a) + 90))

    def beam(self, name, bone, a, b, width, mat, depth=None):
        delta = [b[i] - a[i] for i in range(3)]
        length = math.sqrt(sum(v * v for v in delta))
        if length < 0.001:
            return
        rot = [math.degrees(math.asin(delta[2] / length)), 0,
               math.degrees(math.atan2(-delta[0], delta[1]))]
        center = [(a[i] + b[i]) / 2 for i in range(3)]
        self.box(name, bone, center, (width, length, depth or width), mat, rot)

    def gem(self, name, bone, x, y, z, size, mat, rim="gold"):
        self.box(name + "_mount", bone, (x, y, z + .1), (size * 1.3, size * 1.3, .45), rim, (0, 0, 45))
        self.box(name + "_jewel", bone, (x, y, z - .2), (size, size, .6), mat, (0, 0, 45))
        self.box(name + "_glint", bone, (x - size * .18, y + size * .2, z - .53),
                 (size * .24, size * .24, .08), "shine")

    def eyes(self, bone, x, y, z, scale=1, iris="eye", pupil="pupil", gentle=False):
        # Younger eyes are larger with rounder pupils and a second catch-light;
        # every baby blushes. Adults keep the narrower, steadier gaze.
        scale *= (1.16, 1.07, 1.0)[self.age]
        pupil_w, pupil_h = ((.8, 1.2), (.66, 1.28), (.52, 1.35))[self.age]
        for side in (-1, 1):
            xx = x * side
            self.box("eye_border", bone, (xx, y, z + .08), (1.9 * scale, 2.3 * scale, .35), pupil)
            self.box("eye_iris", bone, (xx, y, z - .12), (1.35 * scale, 1.85 * scale, .32), iris)
            self.box("eye_pupil", bone, (xx + side * .12 * scale, y - .1 * scale, z - .31),
                     (pupil_w * scale, pupil_h * scale, .16), pupil)
            glint = (.6, .54, .48)[self.age]
            self.box("eye_glint", bone, (xx - .28 * scale, y + .47 * scale, z - .42),
                     (glint * scale, glint * scale, .1), "shine")
            if self.age < 2:
                self.box("eye_glint_small", bone, (xx + .3 * scale, y - .5 * scale, z - .42),
                         (.26 * scale, .26 * scale, .1), "shine")
            if gentle or self.age == 0:
                self.box("cheek", bone, (xx * 1.08, y - 1.15 * scale, z - .05),
                         (1.25 * scale, .4 * scale, .15), "blush")

    def finish(self):
        if "head" not in self.bones:
            self.bone("head", "body", [0, 0, 0])
        top = max(p["from"][1] + p["size"][1] for p in self.parts)
        return {"bones": self.bones, "parts": self.parts, "palette": self.palette,
                "height": round(top / 16, 3)}


def _palette(**overrides):
    palette = {"shine": "#F1FEFF", "pupil": "#101326", "gold": "#DDAA47",
               "gold_light": "#FFE7A0", "blush": "#E9ACC6", "eye": "#68E5EA"}
    palette.update(overrides)
    return palette


def _quadruped(s, length, width, height, leg, body_mat, paw_mat):
    k = s.scale
    by = leg * k + height * k * .44
    s.bones["body"] = ("root", [0, by, 0])
    s.mass("rounded_body", "body", (0, by, 0), (width * k, height * k, length * k), body_mat)
    for side, xx in (("l", -1), ("r", 1)):
        for end, zz in (("f", -1), ("b", 1)):
            x, z = xx * width * .34 * k, zz * length * .31 * k
            bone = s.bone("leg_" + end + side, "body", (x, leg * k, z))
            s.mass("limb", bone, (x, leg * k * .58, z), (width * .29 * k, leg * k, width * .29 * k), body_mat)
            paw = width * (.34 if s.age == 0 else .29) * k
            s.mass("soft_paw", bone, (x, .65 * k, z - .45 * k), (paw, 1.3 * k, paw * 1.18), paw_mat)
    return by


def _fox(stage):
    s = Sculpt(stage, _palette(fur="#E9E0FA", white="#FFF9EF", lilac="#BBA0E0", shadow="#79639C",
                               mana="#77D8F4", eye="#8157BC", blush="#E8B6E9"))
    k, h = s.scale, s.head_scale
    by = _quadruped(s, 10, 6.7, 6.4, 5, "fur", "white")
    hy, hz = by + 3.5 * h, -5.4 * k
    s.bone("head", "body", (0, hy - 2 * h, hz + 1.2 * h))
    s.mass("fox_face", "head", (0, hy, hz), (7.6 * h, 6.3 * h, 5.8 * h), "fur")
    s.mass("white_muzzle", "head", (0, hy - 1.6 * h, hz - 2.4 * h), (5.4 * h, 2.5 * h, 3.3 * h), "white")
    for side in (-1, 1):
        x = 2.55 * h * side
        s.box("pointed_ear", "head", (x, hy + 4.1 * h, hz + .3 * h), (2.7 * h, 5 * h, 1.9 * h), "fur", (0, 0, side * -14))
        s.box("velvet_ear", "head", (x, hy + 4.15 * h, hz - .73 * h), (1.35 * h, 3.25 * h, .2 * h), "lilac", (0, 0, side * -14))
        s.box("cheek_tuft", "head", (side * 3.8 * h, hy - .55 * h, hz -.5 * h), (2.3 * h, 2.4 * h, 3 * h), "white", (0, side * 15, side * 25))
    s.eyes("head", 1.9 * h, hy + .2 * h, hz - 2.9 * h, h, gentle=True)
    s.box("little_nose", "head", (0, hy - 1.1 * h, hz - 4.12 * h), (1.25 * h, .7 * h, .55 * h), "shadow")
    s.gem("forehead_sigil", "head", 0, hy + 2.05 * h, hz - 2.8 * h, .8 * h, "mana", "lilac")
    # Nine distinct tails form a peacock-like mana fan behind the fox.
    for i in range(9):
        ang = (i - 4) * math.pi / 10
        spread = (6.6, 9.2, 12.6)[s.age]
        end = (math.sin(ang) * spread,
               by+1.4*k+math.cos(ang)*spread*.44+(i%2)*.6*k,
               8.4*k+math.cos(ang)*spread*.73+(i%2)*1.1*k)
        start = (math.sin(ang)*1.1*k, by-.2*k+(i%2)*.35*k, 4*k)
        bone = s.bone("tail" if i == 4 else "tail_" + str(i + 1), "body", start)
        segments = 4 + s.age
        for j in range(segments):
            t = j / segments
            u = (j + 1) / segments
            def at(v):
                return (start[0]+(end[0]-start[0])*v,
                        start[1]+(end[1]-start[1])*math.sin(v*math.pi/2),
                        start[2]+(end[2]-start[2])*v+1.4*k*math.sin(v*math.pi))
            width = (1.45 + 1.15 * math.sin(t * math.pi)) * k
            s.beam("mana_tail", bone, at(t), at(u), width, "white" if j >= segments - 2 else "fur")
        s.mass("lilac_tail_tip", bone, end, (2.1 * k, 2.6 * k, 2 * k), "lilac")
        if s.age > 0:
            s.box("tail_mana_diamond", bone, (end[0], end[1] + .1 * k, end[2] - 1.12 * k), (.7 * k, 1.1 * k, .2), "mana", (0, 0, 45))
    s.mass("chest_ruff", "body", (0, by + .6 * k, -4.5 * k), (6.4 * k, 5.5 * k, 2.7 * k), "white")
    if s.age == 2:
        for side in (-1, 1):
            for i in range(3):
                s.box("floating_mana_sigil", "body", (side * (3.5 + .5 * i), by + 2.8 - i * 1.3, -2.5 + i * 1.8), (.65, 1.3, .25), "mana", (0, 0, side * 30))
    return s.finish()


def _wyvern(stage):
    s = Sculpt(stage, _palette(ice="#8ECFE8", pale="#D8F4F8", deep="#426C98", membrane="#A9C1EE",
                               crystal="#B5F8FF", horn="#EFFDFF", eye="#54F3FA", silver="#B2C6D9"))
    k, h = s.scale, s.head_scale
    by = 5.9 * k
    s.bones["body"] = ("root", [0, by, 0])
    s.mass("relaxed_dragon_torso", "body", (0, by + 1.8 * k, 1.5 * k), (7.8 * k, 8.8 * k, 9.4 * k), "ice")
    s.mass("segmented_belly", "body", (0, by + 1.5 * k, -2.25 * k), (5.2 * k, 7.2 * k, 2.1 * k), "pale")
    for i in range(4):
        s.box("belly_scale", "body", (0, by - .8 * k + i * 1.5 * k, -3.38 * k), (4.5 * k, .2 * k, .3), "silver")
    for side in (-1, 1):
        x = side * 3.2 * k
        bone = s.bone("leg_l" if side < 0 else "leg_r", "body", (x, 4.7 * k, 2.5 * k))
        s.mass("crouched_dragon_haunch", bone, (x, 3.8 * k, 2.7 * k), (3.8 * k, 5.1 * k, 4.7 * k), "ice")
        s.beam("bent_ice_shin",bone,(x,3.5*k,2*k),(x,1.2*k,.4*k),1.8*k,"deep")
        s.mass("taloned_foot", bone, (x, .9 * k, .6 * k), (3.4 * k, 1.8 * k, 4.8 * k), "deep")
        for toe in range(3):
            s.box("ice_talon", bone, (x + (toe - 1) * .9 * k, .7 * k, -1.9 * k), (.6 * k, .7 * k, 1.6 * k), "horn")
    hy, hz = by + 7.6 * h, -4.5 * k
    s.beam("forward_relaxed_neck","body",(0,by+3.8*k,-.6*k),
           (0,hy-1.45*h,hz+1.9*h),4.3*k,"ice",4.1*k)
    s.bone("head", "body", (0, hy - 2 * h, hz + 1.5 * h))
    s.mass("wyvern_skull", "head", (0, hy, hz), (6.8 * h, 6.1 * h, 6.7 * h), "ice")
    s.mass("snout", "head", (0, hy - 1.4 * h, hz - 3 * h), (4.8 * h, 2.6 * h, 4 * h), "pale")
    s.eyes("head", 1.95 * h, hy + .45 * h, hz - 3.2 * h, .9 * h)
    for side in (-1, 1):
        s.box("nostril", "head", (side * 1.25 * h, hy -.75 * h, hz - 5 * h), (.65 * h, .4 * h, .15), "deep")
        horn = (2.5 + 1.4 * s.age) * k
        horn_points=[(side*2.4*h,hy+2.2*h,hz+h),
                     (side*2.9*h,hy+2.2*h+horn*.65,hz+2.1*h),
                     (side*3.35*h,hy+2.2*h+horn,hz+3.6*h),
                     (side*3.5*h,hy+2.1*h+horn*.96,hz+5*h)]
        for j in range(3):
            s.beam("swept_ice_horn","head",horn_points[j],horn_points[j+1],
                   (1.2-j*.34)*k,"horn")
        s.box("cheek_fin", "head", (side * 3.5 * h, hy, hz + h), (1.4 * h, 3.8 * h, 3.3 * h), "membrane", (0, side * 24, side * -25))
    wing_span = (7, 12, 17)[s.age]
    for side, name in ((-1, "wing_l"), (1, "wing_r")):
        root = (side * 3.1 * k, by + 3.8 * k, 1.2 * k)
        s.bone(name, "body", root)
        elbow = (side*(3.1*k+wing_span*.23),by+5*k,.1*k)
        wrist = (side*(3.1*k+wing_span*.5),by+5.7*k,2.6*k)
        tip = (side*(3.1*k+wing_span),by+7.1*k,5*k)
        distal=s.bone("wingtip_l" if side<0 else "wingtip_r",name,wrist)
        s.beam("wing_upper_arm", name, root, elbow, 1.4 * k, "deep")
        s.beam("wing_bent_forearm",name,elbow,wrist,1.05*k,"ice")
        s.mass("wing_wrist_joint",name,wrist,(1.6*k,1.7*k,1.65*k),"deep")
        s.beam("wing_leading_edge",distal,wrist,tip,.85*k,"ice")

        def leading(t):
            if t<.23:
                a,b,u=root,elbow,t/.23
            elif t<.5:
                a,b,u=elbow,wrist,(t-.23)/.27
            else:
                a,b,u=wrist,tip,(t-.5)/.5
            return tuple(a[i]+(b[i]-a[i])*u for i in range(3))

        def lower_edge(t):
            a=leading(t)
            scallop=.45*abs(math.sin(t*math.pi*5))
            depth=max(.5,(5.1*(1-t)**.72-scallop))*k
            return (a[0],a[1]-depth,a[2]+.75*(1-t)*k),depth

        # The shoulder reaches forward to an elbow, then sweeps backward to
        # the wrist. Only the distal half folds behind that wrist in idle.
        # The neutral mesh stays open so flight can spread the same membrane.
        for j in range(12):
            t=(j+.5)/12
            top=leading(t)
            lower,drape=lower_edge(t)
            bone=distal if j>=6 else name
            left,right=leading(max(0,t-.04)),leading(min(1,t+.04))
            yaw=-side*math.degrees(math.atan2(right[2]-left[2],abs(right[0]-left[0])))
            width=wing_span/12/max(.5,abs(math.cos(math.radians(yaw))))+.13*k
            center=(top[0],top[1]-drape/2,lower[2])
            s.box("icy_wing_membrane",bone,center,(width,drape,.45*k),"membrane",(0,yaw,0))
            s.box("crystalline_wing_edge",bone,(lower[0],lower[1]+.12*k,lower[2]-.27*k),
                  (width,.24*k,.16*k),"crystal",(0,yaw,0))
            if j % 2 == 0:
                s.box("wing_ice_speck",bone,(top[0],top[1]-drape*.46,lower[2]-.29*k),
                      (.28*k,.6*k,.13),"pale",(0,yaw,30))
        for t in (.2,.4,.63,.8,.96):
            lower,_=lower_edge(t)
            start=wrist if t>.5 else elbow
            s.beam("wing_finger",distal if t>.5 else name,start,
                   (lower[0],lower[1],lower[2]-.3*k),.35*k,"crystal")
        s.mass("wing_ice_cluster",distal,tip,(.8*k,1.25*k,.8*k),"crystal")
    start = (0, by - .5 * k, 4.1 * k)
    s.bone("tail", "body", start)
    pts = [start, (0, by - .8 * k, 8 * k), (1.3 * k, by + 1.1 * k, 12 * k), (3 * k, by + 3.3 * k, 15 * k)]
    for i in range(3):
        s.beam("dragon_tail", "tail", pts[i], pts[i+1], (2.6 - .6 * i) * k, "ice")
    s.box("tail_crystal_blade", "tail", pts[-1], (3.1 * k, 4.4 * k, 1 * k), "crystal", (0, 0, -25))
    for i in range(3 + s.age * 2):
        s.box("spinal_crystal", "body", (0, by + 6 * k - i * .65 * k, i * 1.4 * k), (.9 * k, (2.2 + .6 * (i % 2)) * k, 1.4 * k), "crystal", (-25, 0, 0))
    return s.finish()


def _owl(stage):
    """A round, fluffy star-owl: a soft ball of a body under a big head with a heart-shaped
    face and huge golden eyes. The grown sage wears round spectacles and a crescent crown."""
    s = Sculpt(stage, _palette(navy="#3A3F8F", feather="#4C4EA3", tip="#7D80D2", face="#ECE6FA",
                               belly="#A89CDB", star_glow="#FFE7A0", eye="#F4B54A", beak="#E6A94C",
                               moon_glow="#FFF2C2"))
    k, h = s.scale, s.head_scale
    by = 7.6 * k
    s.bones["body"] = ("root", [0, by, 0])
    s.ball("fluffy_owl_body", "body", (0, by, .3 * k), (12 * k, 13 * k, 11 * k), "navy")
    s.ball("lavender_breast", "body", (0, by - 1.2 * k, -4.1 * k), (8.6 * k, 9.2 * k, 3.4 * k), "belly")
    for side in (-1, 1):
        s.box("breast_fluff", "body", (side * 1.5 * k, by - 5.6 * k, -4.9 * k), (2.2 * k, 1.4 * k, 1.6 * k), "belly", (0, 0, side * 18))
    if s.age > 0:
        # A pale crescent on the breast, the mark of a star-reader.
        s.crescent("breast_crescent", "body", (0, by + 2.3 * k, -5.95 * k), 1.7 * k, .8 * k, .3, "moon_glow")
    hy, hz = by + 8.6 * h, -.4 * k
    s.bone("head", "body", (0, hy - 4 * h, hz))
    s.ball("round_owl_head", "head", (0, hy, hz), (13 * h, 10.6 * h, 11 * h), "navy")
    face_z = hz - 5.3 * h
    for side in (-1, 1):
        ex, ey = side * 2.6 * h, hy + .3 * h
        s.disc("heart_face_lobe", "head", (ex, ey - .2 * h, face_z), 5.4 * h, 6.4 * h, 1.4 * h, "face")
        # Huge round eyes: a dark rim, a golden iris, a big pupil and two catch-lights.
        size = 4.3 * h
        z = face_z - .8 * h
        s.disc("owl_eye_rim", "head", (ex, ey, z), size, size, .3, "pupil")
        s.disc("owl_eye_iris", "head", (ex, ey - .05 * size, z - .2), size * .8, size * .8, .3, "eye")
        pupil = (.56, .48, .42)[s.age]
        s.disc("owl_eye_pupil", "head", (ex + side * .04 * size, ey - .06 * size, z - .4), size * pupil, size * pupil, .2, "pupil")
        s.box("owl_eye_glint", "head", (ex - .17 * size, ey + .19 * size, z - .55), (size * .2, size * .2, .1), "shine")
        s.box("owl_eye_glint_small", "head", (ex + .16 * size, ey - .2 * size, z - .55), (size * .1, size * .1, .1), "shine")
        if s.age == 0:
            s.box("owl_blush", "head", (side * 4.3 * h, hy - 2 * h, face_z - .4 * h), (1.5 * h, .5 * h, .2), "blush")
        tuft = (2.6, 3.2, 3.8)[s.age]
        s.box("ear_tuft", "head", (side * 4.4 * h, hy + (4 + tuft * .35) * h, hz + .4 * h), (2.4 * h, tuft * h, 2.2 * h), "feather", (0, 0, side * -26))
        s.box("ear_tuft_tip", "head", (side * (5.1 + tuft * .25) * h, hy + (4.4 + tuft * .8) * h, hz + .5 * h), (1.3 * h, 1.7 * h, 1.4 * h), "tip", (0, 0, side * -36))
    s.box("hooked_beak", "head", (0, hy - 1.5 * h, face_z - .9 * h), (1.5 * h, 1.9 * h, 1.3 * h), "beak", (18, 0, 0))
    s.box("beak_tip", "head", (0, hy - 2.45 * h, face_z - 1.05 * h), (.8 * h, .7 * h, .8 * h), "beak")
    if s.age < 2:
        s.box("forehead_star", "head", (0, hy + 3.2 * h, face_z - .2 * h), (1.1 * h, 1.1 * h, .3), "star_glow", (0, 0, 45))
    for side, name in ((-1, "wing_l"), (1, "wing_r")):
        s.bone(name, "body", (side * 5.2 * k, by + 3.4 * k, .6 * k))
        s.ball("folded_wing", name, (side * 6.3 * k, by - .4 * k, .9 * k), (3.4 * k, 10.4 * k, 8.6 * k), "feather")
        for i in range(3 + s.age):
            s.box("flight_feather", name, (side * 6.5 * k, by - (4.4 + i * .55) * k, (2 + i * 1.25) * k),
                  (2.4 * k, (4.2 - i * .4) * k, 1.5 * k), "tip" if i % 2 == 0 else "feather", (side * 6, 0, side * -10))
        for i in range(2 + s.age * 2):
            s.box("wing_star", name, (side * 8.05 * k, by + (2.6 - i * 1.45) * k, (-.8 + (i % 2) * 2.4) * k), (.16, .75 * k, .75 * k), "star_glow", (45, 0, 0))
    for side in (-1, 1):
        leg = s.bone("leg_l" if side < 0 else "leg_r", "body", (side * 2.6 * k, 2.4 * k, -.4 * k))
        s.ball("fluffy_leg", leg, (side * 2.6 * k, 1.9 * k, -.5 * k), (2.9 * k, 2.6 * k, 2.9 * k), "belly")
        for toe in range(3):
            s.box("golden_talon", leg, (side * 2.6 * k + (toe - 1) * .78 * k, .45 * k, -1.9 * k), (.62 * k, .9 * k, 2.2 * k), "gold")
    s.bone("tail", "body", (0, by - 3.6 * k, 4.6 * k))
    for i in range(5):
        s.box("tail_feather", "tail", ((i - 2) * 1.3 * k, by - 5 * k, 5.6 * k), (1.6 * k, 4.2 * k, 1.1 * k),
              "tip" if i % 2 else "feather", (-35, 0, (i - 2) * -12))
    for i in range(2 + 3 * s.age):
        a = i * 2.39996
        x = math.sin(a) * 4.8 * h
        if abs(x) > 1.6 * h:
            s.box("constellation_speck", "head", (x, hy + (3.2 + 1.2 * math.cos(a)) * h, hz - 4.3 * h), (.4 * h, .4 * h, .2), "star_glow", (0, 0, 45))
    if s.age == 2:
        # Round golden spectacles over both eyes, and a crescent moon floating above.
        for side in (-1, 1):
            cx, cy = side * 2.6 * h, hy + .3 * h
            for j in range(8):
                a = j * math.pi / 4
                s.box("round_spectacle", "head", (cx + math.cos(a) * 2.45, cy + math.sin(a) * 2.45, face_z - 1.35),
                      (2.05, .34, .3), "gold", (0, 0, math.degrees(a) + 90))
            s.box("spectacle_arm", "head", (side * 5.6, cy + .2, hz - 2.6), (.3, .3, 3.6), "gold")
        s.box("spectacle_bridge", "head", (0, hy + .75, face_z - 1.35), (1.3, .34, .3), "gold")
        top = hy + 5.3 + 3.4
        s.crescent("crescent_crown", "head", (0, top, hz + .4), 2.4, 1.15, 1.1, "moon_glow", segments=6)
        s.box("crown_star", "head", (0, top + .4, hz + .4), (1.1, 1.1, .5), "star_glow", (0, 0, 45))
    return s.finish()


def _cerberus(stage):
    """A three-headed hellhound under a mane of living flame. Pups have floppy ears, short
    snouts, tongues out and only a spark of fire; the grown guardian bares fangs under
    fierce brows, spiked collars and a full burning ruff, with embers glowing through its hide."""
    s=Sculpt(stage,_palette(charcoal="#2E2632",crimson="#7C1F2D",muzzle="#E9C3B2",nose="#1D1519",
                            eye="#FFB13D",flame="#FF6A2B",flame_tip="#FFD65C",ember="#C7302F",
                            leather="#3E2229",paw="#3B2F37",fang="#F4EBDD",tongue="#E9798F"))
    s.palette["muzzle"]=("#E9C3B2","#B98E88","#5E4852")[s.age]
    k,h=s.scale,s.head_scale
    by=_quadruped(s,11.2,7.6,7.2,4.6,"charcoal","paw")
    s.ball("crimson_chest_ruff","body",(0,by+.1*k,-4.8*k),(6.4*k,6.6*k,3.6*k),"crimson")

    def flame(bone,root,tip,width):
        """A licking tuft of fire: dark ember at the root, an orange body and a yellow tip."""
        mid=tuple(root[i]+(tip[i]-root[i])*.5 for i in range(3))
        upper=tuple(root[i]+(tip[i]-root[i])*.8 for i in range(3))
        s.beam("ember_root",bone,root,mid,width,"ember")
        s.beam("living_flame",bone,mid,upper,width*.78,"flame")
        s.beam("flame_tip",bone,upper,tip,width*.5,"flame_tip")

    def splay_head(parts,pivot,side):
        """Bake the neck's outward rest pose into cubes, never bone rotation."""
        angle=math.radians(-side*12)
        cy,sy=math.cos(angle),math.sin(angle)
        scale=.92
        for part in parts:
            center=[part["from"][i]+part["size"][i]/2 for i in range(3)]
            origin=part.get("origin",center)
            rx,ry,rz=[math.radians(v) for v in part.get("rotation",(0,0,0))]
            cx,sx,cz,sz=math.cos(rx),math.sin(rx),math.cos(rz),math.sin(rz)
            cc,ss=math.cos(ry),math.sin(ry)
            old=((cz*cc,cz*ss*sx-sz*cx,cz*ss*cx+sz*sx),
                 (sz*cc,sz*ss*sx+cz*cx,sz*ss*cx-cz*sx),
                 (-ss,cc*sx,cc*cx))
            # First place the actual rotated cube center, then compose the
            # neck yaw with its existing ear/horn/jewel orientation.
            center=[sum(old[i][j]*(center[j]-origin[j]) for j in range(3))+origin[i]
                    for i in range(3)]
            delta=[(center[i]-pivot[i])*scale for i in range(3)]
            center=[pivot[0]+cy*delta[0]+sy*delta[2],
                    pivot[1]+delta[1],pivot[2]-sy*delta[0]+cy*delta[2]]
            rot=(tuple(cy*old[0][j]+sy*old[2][j] for j in range(3)),
                 old[1],tuple(-sy*old[0][j]+cy*old[2][j] for j in range(3)))
            part["size"]=[round(v*scale,4) for v in part["size"]]
            part["from"]=[round(center[i]-part["size"][i]/2,4) for i in range(3)]
            part["origin"]=[round(v,4) for v in center]
            part["rotation"]=[round(math.degrees(v),4) for v in
                              (math.atan2(rot[2][1],rot[2][2]),
                               math.asin(max(-1,min(1,-rot[2][0]))),
                               math.atan2(rot[1][0],rot[0][0]))]

    # The center head leads; the outer heads fan outward from their own necks,
    # so all three faces stay legible in a natural guard posture.
    snout=(2.6,3.3,3.9)[s.age]
    tilt=(46,24,12)[s.age]
    for side,x in ((-1,-4.9),(0,0),(1,4.9)):
        bone="head" if side==0 else ("head_l" if side<0 else "head_r")
        hy=by+(4.6 if side==0 else 3.3)*h
        hz=(-5.8 if side==0 else -4.0)*k
        xx=x*h
        pivot=(xx,hy-2.5*h,hz+1.3*h)
        s.bone(bone,"body",pivot)
        first_part=len(s.parts)
        s.ball("hound_neck",bone,(xx*.8,hy-2.6*h,hz+1.8*h),(4.2*h,4.6*h,4.6*h),"charcoal")
        s.mass("hound_skull",bone,(xx,hy,hz),(5.6*h,5.2*h,5.4*h),"charcoal")
        # A tapered snout: a broad bridge over a lighter jaw, with the nose at its tip.
        front=hz-2.4*h
        s.box("snout_bridge",bone,(xx,hy-.85*h,front-snout*h/2),(3.2*h,2.1*h,snout*h),"charcoal")
        s.box("hound_jaw",bone,(xx,hy-2.1*h,front-snout*h*.45),(2.7*h,1.2*h,snout*h*.9),"muzzle")
        s.box("hound_nose",bone,(xx,hy-.15*h,front-snout*h+.15*h),(1.5*h,.95*h,.8*h),"nose")
        s.eyes(bone,1.35*h,hy+.6*h,hz-2.75*h,.78*h)
        for ear in (-1,1):
            ex=xx+ear*1.95*h
            lean=math.radians(tilt)
            s.box("hound_ear",bone,(ex,hy+2.9*h,hz+.6*h),(1.9*h,2.6*h,1.3*h),"charcoal",(0,0,ear*-tilt))
            s.box("ear_point",bone,(ex+ear*math.sin(lean)*1.8*h,hy+(2.9+math.cos(lean)*1.8)*h,hz+.6*h),
                  (1.05*h,1.6*h,1.0*h),"charcoal",(0,0,ear*-tilt))
            s.box("ember_inner_ear",bone,(ex,hy+3.05*h,hz-.12*h),(1.05*h,1.9*h,.15),"ember",(0,0,ear*-tilt))
            if s.age>0:
                s.box("fierce_brow",bone,(xx+ear*1.35*h,hy+1.75*h,hz-2.85*h),(1.7*h,.4*h,.4*h),"charcoal",
                      (0,0,ear*(12 if s.age==2 else 5)))
        if s.age==0:
            s.box("puppy_tongue",bone,(xx+.3*h,hy-2.8*h,front-snout*h*.7),(.95*h,1.3*h,.45*h),"tongue",(14,0,0))
        else:
            for f in (-1,1):
                s.box("hound_fang",bone,(xx+f*.8*h,hy-2.75*h,front-snout*h*.85),(.42*h,(.7+.25*s.age)*h,.42*h),"fang")
        s.box("collar_band",bone,(xx*.85,hy-3.25*h,hz+1.7*h),(4.9*h,1.0*h,5.0*h),"leather")
        if s.age==0:
            if side==0:
                s.ball("pup_bell",bone,(xx,hy-4.05*h,hz-.8*h),(1.3*h,1.3*h,1.3*h),"gold")
        else:
            for a in (-.7,0,.7):
                s.box("collar_spike",bone,(xx*.85+math.sin(a)*2.5*h,hy-3.25*h,hz+1.7*h-math.cos(a)*2.55*h),
                      (.55*h,.55*h,.9*h),"gold",(0,math.degrees(-a),45))
        # Fire licks up from behind each head: a spark on a pup, a blazing ruff on a guardian.
        tufts=(1,3,5)[s.age]
        for t in range(tufts):
            a=(t-(tufts-1)/2)*.55
            root=(xx+math.sin(a)*1.9*h,hy+1.3*h,hz+2.0*h)
            tip=(xx+math.sin(a)*3.4*h,hy+(3.4+.9*s.age)*h-abs(a)*1.1*h,hz+(3.2+.5*s.age)*h)
            flame(bone,root,tip,(1.2+.15*s.age)*h)
        if side:
            splay_head(s.parts[first_part:],pivot,side)
    for i in range(1+2*s.age):
        z=(-2.6+i*1.7)*k
        flame("body",(0,by+3.1*k,z),(0,by+(5+.7*s.age)*k,z+1.5*k),(1.3+.2*s.age)*k)
    if s.age==2:
        # Embers glow through cracks in the hide.
        for side in (-1,1):
            for y,z,r in ((1.3,-1.8,35),(.2,-.5,-30),(1.1,1.0,25),(-.3,2.4,-35)):
                s.box("ember_crack","body",(side*3.83*k,by+y*k,z*k),(.14,.34*k,1.5*k),"flame",(r,0,0))
    s.bone("tail","body",(0,by+.8*k,5.2*k))
    pts=[(0,by+.8*k,5.2*k),(.8*k,by+2.6*k,7.4*k),(1.4*k,by+4.5*k,8.2*k)]
    for i in range(2):
        s.beam("hound_tail","tail",pts[i],pts[i+1],(1.6-i*.35)*k,"charcoal")
    flame("tail",pts[-1],(1.9*k,by+(6.8+.6*s.age)*k,8.7*k),(1.8+.2*s.age)*k)
    return s.finish()


def _panther(stage):
    s=Sculpt(stage,_palette(fur="#34315E",velvet="#4A467E",muzzle="#7E739C",mark="#A982FF",teal="#5CF2E1",eye="#8DFBE6",nose="#E39AC0",moon_glow="#E9F7FF"))
    k,h=s.scale,s.head_scale
    by=8.1*k
    s.bones["body"]=("root",[0,by,0])

    def rounded(name,bone,center,size,mat):
        # Broad vertical facets soften the outline without the horizontal
        # stack of slabs that made the old animal look like a tiered cake.
        x,y,z=center;w,hh,d=size
        s.box(name+"_core",bone,(x,y,z),(w*.72,hh,d*.9),mat)
        for side in (-1,1):
            s.box(name+"_side",bone,(x+side*w*.405,y-.025*hh,z),
                  (w*.2,hh*.77,d),mat)
        s.box(name+"_top",bone,(x,y+hh*.4,z),(w*.76,hh*.2,d*.82),mat)

    # A long ribcage, a tucked flank and distinct rear haunches give the
    # panther a feline weight distribution instead of four identical posts.
    rounded("feline_ribcage","body",(0,by,-1.25*k),(6.5*k,5.7*k,9.6*k),"fur")
    rounded("tucked_flank","body",(0,by+.3*k,3.3*k),(5.1*k,4.7*k,5.7*k),"fur")
    rounded("shoulder_mantle","body",(0,by+.75*k,-4*k),(6.8*k,5.9*k,4.4*k),"velvet")
    rounded("feline_neck","body",(0,by+1*k,-5.5*k),(4.9*k,4.6*k,4*k),"fur")
    for side,xx in (("l",-1),("r",1)):
        x=xx*2.1*k
        for end,front in (("f",True),("b",False)):
            z=(-4.15 if front else 4.4)*k
            hip=(x,6.7*k,z)
            bone=s.bone("leg_"+end+side,"body",hip)
            knee=(x*1.04,(3.7 if front else 4.2)*k,z+(.2 if front else -1.25)*k)
            ankle=(x*1.07,1.45*k,z+(.05 if front else .9)*k)
            rounded("fore_shoulder" if front else "rear_haunch",bone,
                    (x,6.1*k,z+(.1 if front else .35)*k),
                    ((2.25 if front else 2.9)*k,(3.8 if front else 4.7)*k,(2.6 if front else 3.9)*k),
                    "velvet" if front else "fur")
            s.beam("tapered_upper_limb",bone,hip,knee,1.65*k,"fur",1.8*k)
            s.beam("slender_lower_limb",bone,knee,ankle,1.25*k,"fur",1.4*k)
            paw=(2.3 if s.age==0 else 2.05)*k
            rounded("rounded_feline_paw",bone,(ankle[0],.66*k,ankle[2]-.52*k),
                    (paw,1.3*k,2.75*k),"velvet")
            for toe in (-1,1):
                s.box("subtle_paw_toe",bone,(ankle[0]+toe*.42*k,.55*k,ankle[2]-1.78*k),
                      (.12*k,.55*k,.08),"muzzle")
    hy,hz=by+1.5*h,-7.1*k
    s.bone("head","body",(0,hy-1.1*h,hz+1.6*h))
    rounded("feline_skull","head",(0,hy,hz),(6.9*h,5.3*h,5.7*h),"fur")
    rounded("soft_feline_chin","head",(0,hy-1.9*h,hz-1.9*h),(4.5*h,1.6*h,3*h),"fur")
    for side in (-1,1):
        rounded("soft_muzzle","head",(side*1.12*h,hy-1.15*h,hz-2.65*h),(2.65*h,1.95*h,2.15*h),"muzzle")
        s.box("rounded_cat_ear_base","head",(side*2.4*h,hy+2.9*h,hz+.55*h),
              (2.35*h,2.25*h,1.65*h),"velvet",(0,0,side*-12))
        s.box("rounded_cat_ear_crown","head",(side*2.55*h,hy+4*h,hz+.55*h),
              (1.6*h,.7*h,1.3*h),"velvet",(0,0,side*-12))
        s.box("inner_cat_ear","head",(side*2.4*h,hy+3*h,hz-.31*h),
              (1.3*h,1.5*h,.16),"mark",(0,0,side*-12))
        for i in range(2):
            s.box("temple_rune","head",(side*(3.05-i*.18)*h,hy+(.95-i*.9)*h,hz-2.5*h),
                  (.25*h,.65*h,.12),"teal",(0,0,side*30))
    s.eyes("head",1.9*h,hy+.25*h,hz-2.9*h,.78*h)
    s.box("cat_nose","head",(0,hy-.85*h,hz-3.84*h),(1.1*h,.65*h,.35),"nose")
    s.box("mouth_line","head",(0,hy-1.5*h,hz-3.73*h),(.16*h,.65*h,.1),"pupil")
    # A glowing crescent on the brow marks the shadow cat.
    s.crescent("moon_forehead","head",(0,hy+1.55*h,hz-2.95*h),.9*h,.42*h,.2,"moon_glow")
    for side in (-1,1):
        for i in range(3+s.age):
            z=(-3.8+i*1.8)*k
            x=(3.35 if z<1.5*k else 2.6)*k
            y=by+(.8 if i%2 else -.25)*k
            # Broken little crescents lie on the skin. Large framed square
            # plaques used to obscure the shoulders and read as armour.
            for dy,dz,hh,dd in ((.38,0,.18,.85),(-.3,0,.17,.65),(0,.45,.55,.16)):
                s.box("shadow_rosette_stroke","body",(side*x,y+dy*k,z+dz*k),
                      (.09,hh*k,dd*k),"mark")
            if i%2==0:
                s.box("rosette_mana_spark","body",(side*(x+.04*k),y,z-.08*k),
                      (.08,.24*k,.25*k),"teal")
    start=(0,by+.15*k,5.6*k)
    s.bone("tail","body",start)
    pts=[start,(.55*k,by-.1*k,8*k),(1.7*k,by+.1*k,10.4*k),
         (3.25*k,by+1*k,12.1*k),(4.6*k,by+2.4*k,12.3*k),
         (5*k,by+3.7*k,11.5*k),(4.4*k,by+4.3*k,10.45*k)]
    for i in range(len(pts)-1):
        s.beam("curved_feline_tail","tail",pts[i],pts[i+1],(1.32-i*.13)*k,"fur")
        if i in (2,4):
            s.box("tail_luminous_band","tail",pts[i],
                  ((1.48-i*.11)*k,.55*k,(1.48-i*.11)*k),"mark")
    rounded("soft_tail_tip","tail",pts[-1],(.85*k,.85*k,1.25*k),"teal")
    # A wisp of shadow fire curls off the tail tip.
    tip=pts[-1]
    for j,(dy,size,mat) in enumerate(((.9,1.0,"mark"),(1.7,.7,"teal"),(2.3,.4,"moon_glow"))):
        s.box("shadow_wisp","tail",(tip[0]+.15*j*k,tip[1]+dy*k,tip[2]-.2*j*k),(size*k,size*1.3*k,size*k),mat,(0,45,15*j))
    if s.age==2:
        for i in range(3):
            s.box("shoulder_moon_ridge","body",(0,by+3.18,-3.8+i*1.6),(.65,.1,.9),"teal",(0,45,0))
    return s.finish()


def _gargoyle(stage):
    s=Sculpt(stage,_palette(stone="#5D5269",light="#807081",dark="#3B3448",wine="#8E354D",membrane="#AF5265",eye="#F1839C",rune="#D5788E"))
    k,h=s.scale,s.head_scale
    by=6.4*k
    s.bones["body"]=("root",[0,by,0])
    s.mass("carved_gargoyle_torso","body",(0,by+1.6*k,0),(7.5*k,9*k,5.8*k),"stone")
    s.mass("stone_belly","body",(0,by+.8*k,-2.4*k),(4.6*k,6.3*k,2*k),"light")
    for side in (-1,1):
        leg=s.bone("leg_l" if side<0 else "leg_r","body",(side*2.5*k,4.5*k,.3*k))
        s.mass("crouched_haunch",leg,(side*2.8*k,3.4*k,1.1*k),(3.9*k,4.6*k,4.9*k),"stone")
        s.beam("bent_stone_shin",leg,(side*2.8*k,3.1*k,1.3*k),
               (side*2.8*k,1.15*k,-.9*k),1.8*k,"dark")
        s.mass("stone_paw",leg,(side*2.8*k,.75*k,-1*k),(3.6*k,1.5*k,4.2*k),"dark")
        for toe in range(3):
            s.box("stone_claw",leg,(side*2.8*k+(toe-1)*.9*k,.55*k,-3.1*k),(.6*k,.7*k,1.2*k),"light")
        arm=s.bone("arm_l" if side<0 else "arm_r","body",(side*3.5*k,by+3.6*k,0))
        s.beam("hunched_arm",arm,(side*3.5*k,by+3.6*k,0),(side*4.5*k,by-1*k,-2*k),1.8*k,"stone")
        s.mass("stone_hand",arm,(side*4.5*k,by-1*k,-2*k),(2.7*k,2*k,2.7*k),"light")
    # Seat the (larger) young head on top of the body rather than letting it float.
    hy,hz=by+min(8*h,6.1*k+2.9*h),-1*k
    s.bone("head","body",(0,hy-2.6*h,hz+h))
    s.mass("impish_stone_head","head",(0,hy,hz),(8.5*h,6.6*h,6.4*h),"stone")
    s.mass("gargoyle_muzzle","head",(0,hy-1.8*h,hz-2.4*h),(5.8*h,2.3*h,2.6*h),"light")
    s.eyes("head",2.25*h,hy+.25*h,hz-3.25*h,h,gentle=False)
    for side in (-1,1):
        s.box("large_pointed_ear","head",(side*4.6*h,hy+1.3*h,hz+.2*h),(4*h,2.7*h,1.3*h),"light",(0,0,side*25))
        s.box("ear_wine_inset","head",(side*4.7*h,hy+1.4*h,hz-.5*h),(2.8*h,1.3*h,.15),"wine",(0,0,side*25))
        horn=(2.4+1.1*s.age)*k
        horn_points=[(side*2.5*h,hy+2.7*h,hz+h),
                     (side*3.5*h,hy+2.7*h+horn*.65,hz+1.6*h),
                     (side*3.3*h,hy+2.7*h+horn,hz+2.6*h),
                     (side*2.7*h,hy+2.5*h+horn,hz+3.2*h)]
        for j in range(3):
            s.beam("curved_stone_horn","head",horn_points[j],horn_points[j+1],
                   (1.15-.31*j)*k,"dark")
        s.box("tiny_vampire_fang","head",(side*1.45*h,hy-2.9*h,hz-3.5*h),(.55*h,1.1*h,.6*h),"shine")
    s.gem("bloodstone_brow","head",0,hy+1.8*h,hz-3*h,1*h,"wine","dark")
    s.gem("living_bloodstone_heart","body",0,by+2.2*k,-3.45*k,1.5*k,"rune","dark")
    for side,name in ((-1,"wing_l"),(1,"wing_r")):
        root=(side*2.9*k,by+4*k,2*k)
        s.bone(name,"body",root)
        span=(6.4,8.3,10.5)[s.age]
        elbow=(side*(4*k+span*.38),by+7*k,3*k)
        end=(side*(3*k+span),by+6*k,4*k)
        s.beam("bat_wing_bone",name,root,elbow,1*k,"dark")
        s.beam("bat_wing_rim",name,elbow,end,.65*k,"light")
        for j in range(5):
            t=(j+.5)/5
            scallop=.65*abs(math.sin(t*math.pi*3))
            s.box("wine_wing_membrane",name,(side*(3*k+span*t),by+(4.1+1.5*t+scallop*.5)*k,4*k),
                  (span/5+.2,(4.6-2.5*t-scallop)*k,.38*k),"membrane",(0,0,side*12))
        for j in range(3):
            s.beam("bat_wing_finger",name,elbow,(side*(4*k+span*(.3+j*.35)),by+(1.2+j)*k,4*k),.38*k,"dark")
    s.bone("tail","body",(0,by-1*k,2.8*k))
    s.beam("devil_tail","tail",(0,by-k,2.8*k),(3*k,by+1.2*k,7*k),.8*k,"dark")
    s.box("tail_arrow","tail",(3*k,by+1.6*k,7*k),(2.4*k,2.4*k,.7*k),"wine",(0,0,45))
    if s.age==2:
        for side in (-1,1):
            for i in range(3):
                s.box("ancient_carving","body",(side*3.5,by+3-i*1.6,-1.6),(.25,.5,2.1),"rune",(side*15,0,0))
    return s.finish()


def _golem(stage):
    s=Sculpt(stage,_palette(stone="#657578",slate="#45575E",edge="#97A5A0",moss="#658255",leaf="#91AF69",rune="#62E5CF",eye="#84F6DC"))
    k,h=s.scale,s.head_scale
    by=8.5*k
    s.bones["body"]=("root",[0,by,0])
    s.box("ancient_dwarf_chest","body",(0,by+2*k,0),(10*k,9*k,6*k),"slate")
    s.box("beveled_chest_face","body",(0,by+2*k,-3.2*k),(8.7*k,7.5*k,.8*k),"stone")
    s.box("waist_stone","body",(0,by-3*k,0),(7.6*k,2.7*k,5.2*k),"edge")
    s.box("belt_rune_band","body",(0,by-2.4*k,-2.8*k),(7.8*k,.55*k,.3),"rune")
    for side in (-1,1):
        leg=s.bone("leg_l" if side<0 else "leg_r","body",(side*2.7*k,5.3*k,0))
        s.box("block_thigh",leg,(side*2.7*k,3.9*k,0),(4.2*k,4.2*k,4.5*k),"stone")
        s.box("dark_knee_joint",leg,(side*2.7*k,2.1*k,-.1*k),(3.2*k,1.3*k,3.7*k),"slate")
        s.box("heavy_stone_boot",leg,(side*2.7*k,.85*k,-.8*k),(4.7*k,1.7*k,6.1*k),"edge")
        s.box("boot_moss",leg,(side*2.7*k,1.8*k,-1.6*k),(3.7*k,.3*k,3.7*k),"moss")
        s.box("knee_rune_stem",leg,(side*2.7*k,3.8*k,-2.3*k),(.34*k,2.1*k,.15),"rune")
        s.box("knee_rune_cross",leg,(side*2.7*k,4.1*k,-2.4*k),(1.65*k,.3*k,.15),"rune",(0,0,side*26))
        for zz in (-1,1):
            s.box("boot_chiseled_edge",leg,(side*2.7*k,1.05*k,zz*2.3*k-.7*k),(4.3*k,.35*k,.4*k),"slate")
        arm=s.bone("arm_l" if side<0 else "arm_r","body",(side*6.4*k,by+4.3*k,0))
        s.mass("shoulder_boulder",arm,(side*6.4*k,by+3.7*k,0),(4.6*k,4.9*k,6*k),"edge")
        s.box("upper_arm",arm,(side*6.9*k,by+.2*k,.2*k),(3.6*k,4*k,4*k),"stone")
        s.box("rune_arm_joint",arm,(side*7*k,by-1.4*k,.2*k),(2.9*k,.9*k,3.4*k),"rune")
        s.mass("giant_stone_fist",arm,(side*7.1*k,by-3*k,-.2*k),(4.3*k,3.3*k,4.9*k),"slate")
        for finger in range(3):
            s.box("carved_finger",arm,(side*7.1*k+(finger-1)*1.1*k,by-3.5*k,-2.75*k),(.85*k,2*k,.5*k),"edge")
        s.box("shoulder_rune_stroke",arm,(side*6.4*k,by+3.7*k,-3.08*k),(.38*k,2.5*k,.15),"rune")
        s.box("shoulder_rune_branch",arm,(side*6.7*k,by+4*k,-3.18*k),(1.45*k,.35*k,.15),"rune",(0,0,side*45))
        for j in range(1+s.age):
            s.box("weathered_arm_seam",arm,(side*7*k,by+(.7-j*.9)*k,-2.05*k),(2.6*k,.17*k,.13),"slate",(0,0,side*(8+j*11)))
        s.box("shoulder_moss",arm,(side*6.4*k,by+6.1*k,-.2*k),(4.3*k,.5*k,4.8*k),"moss")
        for j in range(2+s.age):
            s.box("shoulder_leaf",arm,(side*(5.5+j*.7)*k,by+(6.4+j*.25)*k,(-.8+j*.7)*k),(1.4*k,.25*k,1.9*k),"leaf",(0,j*25,side*15))
    # Seat the (larger) young head on top of the body rather than letting it float.
    hy=by+min(9*h,6.5*k+2.9*h)
    s.bone("head","body",(0,hy-3*h,0))
    s.mass("beveled_golem_head","head",(0,hy,0),(7.7*h,6.4*h,6*h),"stone")
    s.box("brow_slab","head",(0,hy+1.7*h,-3.2*h),(8.3*h,1.35*h,1.1*h),"edge")
    s.eyes("head",2*h,hy+.25*h,-3.18*h,.85*h)
    s.box("broad_dwarf_nose","head",(0,hy-.5*h,-3.8*h),(2.1*h,2.4*h,1.6*h),"edge")
    # The beard grows with age; a young golem has a pebble face with a sprout on top.
    for j in range((0,3,5)[s.age]):
        jj=j-(1 if s.age==1 else 2)
        s.box("carved_stone_beard","head",(jj*1.15*h,hy-(2.7+(.5 if abs(jj)==1 else 1 if jj==0 else 0))*h,-2.8*h),(1.2*h,3*h,1.3*h),"slate")
    if s.age==0:
        s.beam("sprout_stem","head",(0,hy+3.5*h,0),(.3*h,hy+5.6*h,0),.45*h,"leaf")
        for side in (-1,1):
            s.box("sprout_leaf","head",(side*1.1*h,hy+5.6*h,0),(2*h,.4*h,1.3*h),"leaf",(0,0,side*-25))
    s.box("moss_hair","head",(0,hy+3.3*h,0),(7.1*h,.8*h,5.7*h),"moss")
    for side in (-1,1):
        s.box("temple_chisel_border","head",(side*3.7*h,hy,-3*h),(.5*h,4.6*h,.5*h),"edge")
        s.box("weathered_forehead_seam","head",(side*1.6*h,hy+2.7*h,-3.05*h),(2*h,.18*h,.12),"slate",(0,0,side*20))
    # Runes are built from strokes, rather than a flat glowing rectangle.
    for x in (-2,2):
        s.box("chest_rune_stem","body",(x*k,by+2*k,-3.7*k),(.42*k,4*k,.16),"rune")
        for yy in (-.8,.8):
            s.box("chest_rune_branch","body",((x+.6)*k,by+(2+yy)*k,-3.75*k),(1.7*k,.4*k,.16),"rune",(0,0,40 if yy>0 else -40))
    s.gem("rune_core","body",0,by+2*k,-3.8*k,1.4*k,"rune","edge")
    if s.age>0:
        for j in range(2+s.age):
            s.box("crown_stone","head",((j-(1+s.age)/2)*1.7*h,hy+(4.1+.45*(j%2))*h,.6*h),(1.3*h,(1.5+.4*(j%2))*h,1.4*h),"slate",(0,0,(j-1)*8))
    return s.finish()


def _ent(stage):
    s=Sculpt(stage,_palette(wood="#785639",bark="#4F3C30",cut="#BA9660",leaf="#547B45",light_leaf="#8BB65D",deep_leaf="#355B3B",amber="#EDB85D",eye="#FFE3A0",moss="#82A65B"))
    k,h=s.scale,s.head_scale
    by=7.3*k
    s.bones["body"]=("root",[0,by,0])
    s.mass("living_trunk","body",(0,by+2.2*k,0),(7.1*k,11*k,6.2*k),"wood")
    for side in (-1,1):
        leg=s.bone("leg_l" if side<0 else "leg_r","body",(side*2.1*k,4.7*k,0))
        s.beam("root_leg",leg,(side*2.1*k,4.7*k,0),(side*2.7*k,1*k,-.2*k),2.6*k,"bark")
        for j in range(3):
            s.beam("root_toe",leg,(side*2.6*k,1.2*k,0),(side*(2.3+(j-1)*1.4)*k,.45*k,(-2.7+j*.9)*k),1.15*k,"wood")
        arm=s.bone("arm_l" if side<0 else "arm_r","body",(side*3.2*k,by+5*k,0))
        elbow=(side*5.5*k,by+2.6*k,.3*k)
        hand=(side*6.3*k,by+1*k,-1*k)
        s.beam("branch_upper_arm",arm,(side*3.2*k,by+5*k,0),elbow,1.9*k,"wood")
        s.beam("branch_forearm",arm,elbow,hand,1.5*k,"bark")
        for j in range(3):
            s.beam("twig_finger",arm,hand,(side*(6.1+(j-1)*.75)*k,by-.8*k,(-1.8+j*.7)*k),.5*k,"wood")
        s.mass("shoulder_leaf_cluster",arm,(side*4*k,by+5.3*k,.5*k),(4*k,2.9*k,3.5*k),"leaf")
        s.box("wrist_leaf",arm,(side*6*k,by+1.5*k,-1.7*k),(2.7*k,.35*k,1.8*k),"light_leaf",(0,side*30,side*20))
    # Seat the (larger) young head on top of the body rather than letting it float.
    hy=by+min(10*h,7.7*k+3.5*h)
    s.bone("head","body",(0,hy-3.4*h,0))
    s.mass("kind_woodland_face","head",(0,hy,0),(8.3*h,7.7*h,6.4*h),"wood")
    s.mass("warm_face_inset","head",(0,hy-.2*h,-2.8*h),(6.7*h,5.5*h,1.5*h),"cut")
    s.eyes("head",1.95*h,hy+.1*h,-3.6*h,h,gentle=False)
    s.box("wood_nose","head",(0,hy-.7*h,-4*h),(1.6*h,1.7*h,1*h),"wood")
    s.box("kind_smile","head",(0,hy-2*h,-3.65*h),(2.5*h,.35*h,.12),"bark")
    for side in (-1,1):
        for i in range(3):
            s.box("bark_face_grain","head",(side*(3.1-i*.15)*h,hy+(1.6-i*1.45)*h,-2.8*h),(.28*h,1*h,.6),"bark",(0,0,side*10))
        branch_start=(side*2.3*h,hy+2.8*h,.5*h)
        branch_end=(side*(4.4+s.age*.7)*h,hy+(7+s.age)*h,1*h)
        s.beam("antler_branch","head",branch_start,branch_end,1.35*k,"bark")
        s.beam("antler_twig","head",(side*3.7*h,hy+5.1*h,.8*h),(side*6.1*h,hy+5.8*h,1.3*h),.65*k,"wood")
    canopy=(9,12,15)[s.age]*k
    for j in range(5+s.age*2):
        a=j*2.39996
        xx=math.cos(a)*canopy*.35
        zz=math.sin(a)*canopy*.25+.8*k
        yy=hy+(4.35+(1.7 if j%3==0 else .25 if j%3==1 else -.55))*h
        crown=.88 if j%3==0 else 1.08 if j%3==1 else .96
        s.mass("leaf_crown_cluster","head",(xx,yy,zz),
               (canopy*.47*crown,(3.4+.5*(j%2))*k,canopy*.44*crown),
               ("leaf","light_leaf","deep_leaf")[j%3])
    for i in range(5+3*s.age):
        z=math.cos(i*2.4)*2.8*k
        x=math.sin(i*2.4)*3.1*k
        y=by+(-1+i%5*1.5)*k
        s.box("trunk_bark_ridge","body",(x,y,z),(.55*k,2.7*k,.6*k),"bark",(0,i*30,0))
    s.gem("amber_sap_heart","body",0,by+2*k,-3.3*k,1.1*k,"amber","bark")
    if s.age==2:
        for side in (-1,1):
            s.beam("hanging_vine","head",(side*5,hy+4.5,0),(side*5.5,hy-1.5,.5),.35,"moss")
            for j in range(3):
                s.box("vine_leaf","head",(side*(5.3+j*.1),hy+3-j*1.5,-.1),(1.7,.4,1.3),"light_leaf",(0,side*20,side*25))
    return s.finish()


def _tortoise(stage):
    s=Sculpt(stage,_palette(skin="#5C947E",pale="#B2D4AD",shell="#246E5C",emerald="#3AA987",light="#75D8AE",deep="#174C4A",eye="#F2D680",crystal="#A5F2D1"))
    k,h=s.scale,s.head_scale
    by=_quadruped(s,11.8,10,4.6,2.9,"skin","pale")
    # Build the curved carapace in plan. Each broad scute rises from one
    # continuous base, so front and side views read as a dome rather than
    # five full-width shelves balanced on the turtle's back.
    for row in range(5):
        for col in range(5):
            xx=(col-2)*2.5*k;zz=(row-2)*2.5*k+.5*k
            top=(6.35-.8*(abs(col-2)**1.4+abs(row-2)**1.4))*k
            bottom=.35*k
            s.box("domed_carapace_scute","body",(xx,by+(top+bottom)/2,zz),
                  (2.61*k,top-bottom,2.61*k),"shell")
    s.box("gold_shell_rim","body",(0,by+.4*k,.5*k),(13.9*k,.8*k,13.8*k),"gold")
    s.box("dark_shell_base","body",(0,by-.3*k,.5*k),(12.8*k,.8*k,12.7*k),"deep")
    # Nine raised diamond scutes form a true dimensional carapace mosaic.
    for row in range(3):
        for col in range(3):
            xx=(col-1)*3.65*k
            zz=(row-1)*3.65*k+.5*k
            yy=by+(6.4-1.6*(abs(col-1)+abs(row-1)))*k
            s.box("gold_scute_border","body",(xx,yy,zz),(2.7*k,.22*k,2.7*k),"gold",(0,45,0))
            s.box("faceted_emerald_scute","body",(xx,yy+.25*k,zz),(2.35*k,.38*k,2.35*k),"emerald" if (row+col)%2 else "light",(0,45,0))
            s.box("scute_facet","body",(xx-.3*k,yy+.48*k,zz-.35*k),(.9*k,.1*k,.9*k),"crystal",(0,45,0))
    hy,hz=by+.6*h,-8*k
    s.bone("head","body",(0,hy,hz+2*h))
    s.mass("gentle_turtle_head","head",(0,hy,hz),(6.2*h,4.7*h,5.9*h),"skin")
    s.mass("turtle_beak","head",(0,hy-1.05*h,hz-2.2*h),(4.9*h,2*h,2.5*h),"pale")
    s.eyes("head",1.75*h,hy+.6*h,hz-2.9*h,.8*h)
    s.box("turtle_smile","head",(0,hy-1.2*h,hz-3.5*h),(2.7*h,.25*h,.12),"deep")
    s.gem("turtle_forehead_gem","head",0,hy+1.6*h,hz-2.5*h,.65*h,"emerald")
    s.bone("tail","body",(0,by-1*k,5.7*k))
    s.beam("tortoise_tail","tail",(0,by-1*k,5.7*k),(0,by-.5*k,8.9*k),1.1*k,"skin")
    for side in (-1,1):
        for i in range(4):
            s.box("rim_gold_inlay","body",(side*6.7*k,by+1.1*k,(-4.5+i*3.2)*k),(.35*k,.9*k,1.7*k),"gold_light")
    crystals=1+2*s.age
    for i in range(crystals):
        xx=math.sin(i*2.4)*(2.5 if i else 0)*k
        zz=math.cos(i*2.4)*(2.2 if i else 0)*k+.8*k
        height=(2.3+s.age*.9+(1 if i==0 else 0))*k
        s.box("growing_shell_crystal","body",(xx,by+7*k+height*.38,zz),
              (1.25*k,height*.78,1.2*k),"crystal",(i*5,0,(i-2)*7))
        s.box("tapered_crystal_crown","body",(xx,by+7*k+height*.87,zz),
              (.75*k,height*.24,.73*k),"crystal",(i*5,0,(i-2)*7))
        s.box("crystal_foot_mount","body",(xx,by+7*k,zz),(2*k,.5*k,1.9*k),"gold",(0,45,0))
    return s.finish()


def _manta(stage):
    s=Sculpt(stage,_palette(navy="#24488C",back="#3466B5",cyan="#5FE8F5",belly="#D2F1F7",spots="#A6FAFF",violet="#9C8AF0",eye="#B3F8F2"))
    k,h=s.scale,s.head_scale
    by=5.5*k+2
    s.bones["body"]=("root",[0,by,0])
    s.mass("streamlined_ray_body","body",(0,by,0),(8.4*k,3.4*k,10*k),"navy")
    s.mass("pale_underbelly","body",(0,by-1.35*k,-.25*k),(7.2*k,.8*k,9.2*k),"belly")
    s.mass("dorsal_mantle","body",(0,by+1.25*k,-.2*k),(6.2*k,1.6*k,8*k),"back")
    s.bone("head","body",(0,by,-3*k))
    s.mass("manta_forehead","head",(0,by+.2*k,-4.15*k),(7.2*h,2.6*h,3.7*h),"back")
    face_z=-4.15*k-1.85*h
    s.box("gentle_ray_mouth","head",(0,by-.5*k,face_z-.08),(2.4*h,.22*h,.12),"navy")
    for side in (-1,1):
        x=side*2.55*h
        ey=by+1.05*h
        s.mass("raised_ray_eye","head",(x,ey,face_z+.55*h),(1.85*h,1.55*h,1.55*h),"navy")
        s.box("ray_eye_border","head",(x,ey,face_z-.26*h),(1.43*h,1.22*h,.18),"pupil")
        s.box("ray_eye","head",(x,ey+.03*h,face_z-.39*h),(1.05*h,.97*h,.15),"eye")
        s.box("ray_eye_pupil","head",(x+side*.08*h,ey-.05*h,face_z-.5*h),(.37*h,.74*h,.10),"pupil")
        s.box("ray_eye_glint","head",(x-.22*h,ey+.27*h,face_z-.58*h),(.3*h,.3*h,.08),"shine")
        lobes=[(side*2.5*k,by-.1*k,-5.4*k),
               (side*3.1*k,by-.2*k,-6.9*k),
               (side*2.85*k,by+.15*k,-8*k),
               (side*2.28*k,by+.55*k,-8.3*k)]
        for j in range(3):
            s.beam("curled_cephalic_lobe","head",lobes[j],lobes[j+1],
                   (1.12-j*.2)*k,"cyan" if j<2 else "belly",(1.0-j*.18)*k)
    for side,name in ((-1,"wing_l"),(1,"wing_r")):
        s.bone(name,"body",(side*3*k,by,0))
        span=(7,11.5,16)[s.age]
        hinge_t=.6
        hinge_front=(-5.2+4.7*hinge_t**1.6)*k
        hinge_rear=(6.0-6.0*hinge_t**.8)*k
        tip_bone=s.bone(name+"_tip",name,
                        (side*(3*k+span*hinge_t),
                         by+math.sin(hinge_t*math.pi)*.35*k+hinge_t**4*1.5*k,
                         (hinge_front+hinge_rear)/2))
        # A swept diamond in plan, with a soft rise at the tips. The former
        # steeply rotated strips formed a deep boat even in its resting pose.
        for j in range(10):
            t=(j+.5)/10
            width=span/10+.12*k
            front=(-5.2+4.7*t**1.6)*k
            rear=(6.0-6.0*t**.8)*k
            depth=rear-front
            x=side*(3*k+span*t)
            yy=by+math.sin(t*math.pi)*.35*k+t**4*1.5*k
            z=(front+rear)/2
            rotation=(0,side*2,side*(2+8*t**3))
            origin=(x,yy,z)
            thick=(1.15-.75*t)*k
            fin_bone=tip_bone if j>=6 else name
            s.box("sweeping_ray_fin",fin_bone,origin,(width,thick,depth),"back" if j<7 else "navy",rotation,origin)
            # A pale underside, so the fin reads against the sky from below and from the side.
            s.box("pale_fin_underside",fin_bone,(x,yy-thick*.55,z+depth*.04),(width*.96,thick*.3,depth*.86),"belly",rotation,origin)
            s.box("luminous_fin_leading_edge",fin_bone,(x,yy+thick*.52,front+.19*k),
                  (width,.12*k,.35*k),"cyan",rotation,origin)
            if 1<j<8:
                s.box("fin_constellation",fin_bone,(x,yy+thick*.55,z+depth*.18),
                      (.34*k,.10,.35*k),"spots",rotation,origin)
                if j%2==0:
                    s.box("fin_constellation_spark",fin_bone,(x,yy+thick*.55,z-depth*.2),
                          (.19*k,.10,.21*k),"spots",rotation,origin)
        for j in range(3):
            t=.42+j*.2
            a=(side*3*k,by+.4*k,(j-1)*1.8*k)
            b=(side*(3*k+span*t),by+.4*k+t**4*1.5*k,(-1.2+j*.45)*k)
            if t>hinge_t:
                blend=hinge_t/t
                mid=tuple(a[axis]+(b[axis]-a[axis])*blend for axis in range(3))
                s.beam("fin_iridescent_vein",name,a,mid,.14*k,"violet")
                s.beam("fin_iridescent_vein_tip",tip_bone,mid,b,.14*k,"violet")
            else:
                s.beam("fin_iridescent_vein",name,a,b,.14*k,"violet")
    s.bone("tail","body",(0,by,5*k))
    points=[(0,by,4.5*k),(.15*k,by-.2*k,7.4*k),(.65*k,by-.3*k,10.4*k),
            (1.55*k,by-.05*k,13.3*k),(2.6*k,by+.5*k,15.9*k),
            (3.3*k,by+1.3*k,18.1*k),(3.2*k,by+2.1*k,19.6*k)]
    s.bone("tail_tip","tail",points[3])
    for i in range(len(points)-1):
        tail_bone="tail_tip" if i>=3 else "tail"
        s.beam("elegant_whip_tail",tail_bone,points[i],points[i+1],(1.05-.14*i)*k,"navy")
        if i in (1,3):
            s.box("tail_light_pearl",tail_bone,points[i+1],(.38*k,.34*k,.46*k),"spots")
    for i in range(3):
        s.box("dorsal_diamond","body",(0,by+1.34*k,(-2.2+i*2.1)*k),(.75*k,.12*k,.95*k),"cyan",(0,45,0))
    return s.finish()


def _mimic(stage):
    s=Sculpt(stage,_palette(wood="#69453F",panel="#936848",dark="#382C3D",metal="#CDA65D",gold="#E4BA61",mouth="#3B2336",tongue="#CB7191",tooth="#F2E2BB",potion="#7CD5CA",purple="#A580D0",eye="#8AEBDD"))
    k,h=s.scale,s.head_scale
    by=3.2*k
    width=(8,10.5,13)[s.age]
    depth=(6.3,8.4,10)[s.age]
    bh=(5.8,7.3,9)[s.age]
    s.bones["body"]=("root",[0,by,0])
    s.box("ornate_chest_base","body",(0,by+bh/2,0),(width,bh,depth),"wood")
    s.box("front_wood_panel","body",(0,by+bh/2,-depth/2-.12),(width*.82,bh*.72,.35),"panel")
    for side in (-1,1):
        for zz in (-1,1):
            s.box("corner_brass_angle","body",(side*(width/2-.5*k),by+bh/2,zz*(depth/2-.4*k)),(.9*k,bh+.2*k,.9*k),"gold")
        leg=s.bone("leg_l" if side<0 else "leg_r","body",(side*width*.3,by,0))
        s.box("little_gold_leg",leg,(side*width*.3,by/2,0),(1.6*k,by,1.6*k),"metal")
        s.mass("gilded_claw_foot",leg,(side*width*.3,.7*k,-.8*k),(3.4*k,1.4*k,3.8*k),"gold")
        for j in range(2):
            s.box("foot_toe",leg,(side*width*.3+(j-.5)*1.1*k,.55*k,-2.5*k),(.75*k,.8*k,1.1*k),"gold_light")
        s.box("brass_side_handle","body",(side*(width/2+.32*k),by+bh*.52,0),(.45*k,2.3*k,3.1*k),"metal")
        s.box("handle_hollow","body",(side*(width/2+.6*k),by+bh*.52,0),(.2,1.1*k,1.6*k),"dark")
    mouth_y=by+bh+.25*k
    s.box("dark_open_mouth","body",(0,mouth_y,-.1*k),(width*.9,.9*k,depth*.85),"mouth")
    lid_pivot=(0,mouth_y,depth/2)
    s.bone("lid","body",lid_pivot)
    s.bone("head","lid",(0,mouth_y+2*k,0))
    # Raised barrel lid and hinge; the exporter can animate the entire lid bone.
    s.box("barrel_lid_base","lid",(0,mouth_y+1.1*k,.2*k),
          (width+1*k,2*k,depth+.5*k),"wood")
    s.box("barrel_lid_crown","lid",(0,mouth_y+3.3*k,.2*k),
          (width+.6*k,2.3*k,depth*.46),"wood")
    for side in (-1,1):
        s.box("barrel_lid_curved_quarter","lid",(0,mouth_y+2.6*k,.2*k+side*depth*.33),
              (width+.7*k,2.25*k,depth*.3),"wood",(side*-24,0,0))
    s.box("lid_brass_lip","lid",(0,mouth_y+.6*k,-depth/2),(width+1.2*k,1*k,.8*k),"gold")
    for side in (-1,1):
        for zz,yy,rot in ((0,4.55,0),(-depth*.33,3.65,-24),(depth*.33,3.65,24)):
            s.box("brass_lid_strap","lid",(side*width*.3,mouth_y+yy*k,zz),
                  (.7*k,.24*k,depth*(.49 if rot==0 else .32)),"metal",(rot,0,0))
        if s.age<2:
            # Big googly eyes peek over the lip of a young mimic.
            ex,ey,ez=side*width*.23,mouth_y+2.9*k,-depth/2-.7*k
            s.disc("googly_white","head",(ex,ey,ez),2.6*h,2.6*h,.6,"tooth")
            s.disc("googly_pupil","head",(ex+side*.25*h,ey-.3*h,ez-.35),1.3*h,1.3*h,.2,"pupil")
            s.box("googly_glint","head",(ex-.2*h,ey+.15*h,ez-.5),(.45*h,.45*h,.1),"shine")
        else:
            s.gem("mimic_gem_eye","head",side*width*.23,mouth_y+2.6*k,-depth/2-.6*k,1.45*h,"eye","gold")
            s.box("sly_brow","head",(side*width*.23,mouth_y+4*k,-depth/2-.9*k),(2.2,.45,.4),"dark",(0,0,side*-14))
        for z in (-depth*.25,depth*.25):
            s.box("lid_strap_rivet","lid",(side*width*.3,mouth_y+5.15*k,z),(.65*k,.3*k,.65*k),"gold_light")
    for row,bone,yy in ((0,"body",mouth_y+.5*k),(1,"lid",mouth_y+1.1*k)):
        for i in range(7+s.age*2):
            xx=(i-(6+s.age*2)/2)*width/(8+s.age*2)
            s.box("friendly_mimic_tooth",bone,(xx,yy,-depth/2-.1*k),(.7*k,(1.1 if (i+row)%2 else .8)*k,.6*k),"tooth",(0,0,(-8 if row else 8)*(1 if i%2 else -1)))
    s.box("lolling_tongue","body",(width*.12,mouth_y-.4*k,-depth/2-1.3*k),(2.1*k,.65*k,3.5*k),"tongue",(-15,0,0))
    s.box("tongue_groove","body",(width*.12,mouth_y-.05*k,-depth/2-1.9*k),(.15,.1,1.7*k),"mouth")
    s.gem("chest_lock","body",0,by+bh*.52,-depth/2-.4*k,1.15*k,"purple")
    for x in (-1,1):
        for yy in (.2,.8):
            s.box("front_ornament","body",(x*width*.32,by+bh*yy,-depth/2-.35*k),(1.2*k,.45*k,.2),"gold",(0,0,x*35))
    # Bottles perched in a small alchemy rack: visible individual glass, liquid, corks.
    bottles=1+s.age
    for j in range(bottles):
        x=(j-(bottles-1)/2)*2.8*k
        y=mouth_y+5.6*k
        z=depth*.25
        s.mass("alchemy_bottle","lid",(x,y+1*k,z),(1.7*k,2.1*k,1.7*k),"potion" if j%2==0 else "purple")
        s.box("bottle_neck","lid",(x,y+2.35*k,z),(.8*k,1*k,.8*k),"shine")
        s.box("bottle_cork","lid",(x,y+2.95*k,z),(1*k,.5*k,1*k),"panel")
        s.box("bottle_glint","lid",(x-.45*k,y+1.15*k,z-.83*k),(.25*k,1*k,.12),"shine")
    s.bone("tail","body",(0,by+2*k,depth/2))
    for j in range(3+s.age):
        s.box("trailing_gold_coin","tail",((j%2-.5)*1.5*k,by+(1.8-.2*j)*k,depth/2+(j+1)*1.2*k),(1.2*k,.35*k,1.2*k),"gold",(0,j*25,0))
    return s.finish()


def _fairy(stage):
    s=Sculpt(stage,_palette(teal="#43978F",dress="#74C9BA",deep="#356E7C",skin="#F0D2B5",hair="#E9D99C",wing="#9EDDDC",wing_edge="#DAF7EA",sand="#F4C776",glass="#BDEAE1",eye="#397B83",blush="#E9A8B4"))
    k,h=s.scale,s.head_scale
    by=6*k+2
    s.bones["body"]=("root",[0,by,0])
    s.mass("fairy_bodice","body",(0,by+4*k,0),(4.1*k,5.7*k,3.1*k),"teal")
    for i in range(3):
        s.box("layered_bell_skirt","body",(0,by+(1.5-i*.85)*k,0),((4.5+i*1.3)*k,1.5*k,(3.5+i*.9)*k),"dress" if i%2 else "teal")
    for side in (-1,1):
        leg=s.bone("leg_l" if side<0 else "leg_r","body",(side*1.1*k,by,0))
        s.beam("tiny_fairy_leg",leg,(side*1.1*k,by,0),(side*1.2*k,by-3.7*k,0),.85*k,"skin")
        s.box("curled_gold_shoe",leg,(side*1.2*k,by-4*k,-.7*k),(1.3*k,1.2*k,2.5*k),"gold")
        arm=s.bone("arm_l" if side<0 else "arm_r","body",(side*2.1*k,by+5.8*k,0))
        s.beam("fairy_upper_arm",arm,(side*2.1*k,by+5.8*k,0),(side*3.4*k,by+3.6*k,-.1*k),.8*k,"skin")
        s.beam("fairy_forearm",arm,(side*3.4*k,by+3.6*k,-.1*k),(side*2.6*k,by+2.1*k,-2.4*k),.75*k,"skin")
        s.mass("tiny_hand",arm,(side*2.6*k,by+2.1*k,-2.4*k),(1.2*k,1.2*k,1.1*k),"skin")
    # Seat the (larger) young head on top of the body rather than letting it float.
    hy=by+min(9.3*h,6.85*k+2.8*h)
    s.bone("head","body",(0,hy-2.4*h,0))
    # Full cheeks soften into a short chin instead of a flat square jaw.
    s.mass("soft_fairy_face","head",(0,hy+.25*h,0),(5.65*h,4.6*h,5.2*h),"skin")
    s.mass("tapered_fairy_jaw","head",(0,hy-1.8*h,-.1*h),(4.55*h,1.9*h,4.7*h),"skin")
    s.mass("rounded_fairy_chin","head",(0,hy-2.65*h,-.15*h),(3.25*h,.75*h,4.15*h),"skin")

    # An off-center crown rises in broad vertical facets, then a single
    # swept fringe flows across it. This reads as hair rather than a cap.
    for x,top,depth in ((-2.4,3.2,4.2),(-1.2,4.2,5.4),(0,4.55,5.7),
                        (1.2,4.0,5.2),(2.4,2.8,3.7)):
        bottom=.75
        s.box("golden_hair_dome","head",((x-.4)*h,hy+(top+bottom)*.5*h,.55*h),
              (1.35*h,(top-bottom)*h,depth*h),"hair")
    fringe=[(-2.4*h,hy+2.65*h,-2.45*h),(-.65*h,hy+2.05*h,-2.85*h),
            (1.05*h,hy+1.3*h,-2.9*h),(2.25*h,hy+.8*h,-2.5*h)]
    for i in range(3):
        s.beam("swept_fringe","head",fringe[i],fringe[i+1],
               (1.35-i*.25)*h,"hair",(.95-i*.12)*h)
    for side in (-1,1):
        s.box("pointed_fairy_ear","head",(side*3.2*h,hy-.1*h,.1*h),(2*h,1.4*h,1*h),"skin",(0,0,side*25))
        lock=[(side*2.65*h,hy+.9*h,.95*h),(side*3.05*h,hy-.9*h,1.4*h),
              (side*2.65*h,hy-2.55*h,1.3*h),
              (side*(2.05 if side<0 else 2.25)*h,hy-2.9*h,.55*h)]
        for i in range(3):
            s.beam("side_lock","head",lock[i],lock[i+1],
                   (1.5-i*.26)*h,"hair",(1.7-i*.25)*h)
    s.eyes("head",1.5*h,hy-.1*h,-2.8*h,.75*h,gentle=True)
    s.box("fairy_smile","head",(0,hy-1.97*h,-2.53*h),(.54*h,.13*h,.10),"deep")
    for side in (-1,1):
        s.box("fairy_smile","head",(side*.36*h,hy-1.86*h,-2.53*h),
              (.34*h,.13*h,.10),"deep",(0,0,side*24))
    s.gem("clock_tiara_gem","head",-.65*h,hy+3.45*h,-2.4*h,.78*h,"dress")
    for side,name in ((-1,"wing_l"),(1,"wing_r")):
        root=(side*1.1*k,by+4.5*k,1.6*k)
        s.bone(name,"body",root)
        span=(4.5,7,9.5)[s.age]
        for lobe in (0,1):
            cx=side*(2*k+span*.48)
            cy=by+(7.5 if lobe==0 else 2)*k
            cz=2.6*k
            rot=(0,side*10,side*(-32 if lobe==0 else 35))
            w=span*(.66 if lobe==0 else .53)
            hh=(7.2 if lobe==0 else 5.2)*k
            pivot=(cx,cy,cz)
            s.box("butterfly_glass_wing",name,pivot,(w,hh*.62,.25*k),"wing",rot,pivot)
            s.box("wing_inner_facet",name,(cx,cy,cz-.17*k),(w*.7,hh*.58,.12),"glass",rot,pivot)
            for dy in (-1,1):
                s.box("rounded_wing_lobe",name,(cx,cy+dy*hh*.405,cz),
                      (w*.66,hh*.19+.025,.25*k),"wing",rot,pivot)
                s.box("pearl_wing_tip",name,(cx,cy+dy*hh*.495,cz-.17*k),
                      (w*.6,.15*k,.14),"wing_edge",rot,pivot)
            for dx in (-1,1):
                s.box("gold_wing_tracery",name,(cx+dx*w*.45,cy,cz-.24*k),
                      (.17*k,hh*.61,.17),"gold",rot,pivot)
            s.beam("wing_clockwork_vein",name,root,(side*(2*k+span*.76),cy+hh*.28,cz-.4*k),.22*k,"gold")
    # A real hourglass silhouette hangs between her hands.
    gy=by+1.3*k
    gz=-3.2*k
    for yy in (-1.9,1.9):
        s.box("hourglass_gold_cap","body",(0,gy+yy*k,gz),(3.2*k,.5*k,2.2*k),"gold")
    for side in (-1,1):
        s.box("hourglass_pillar","body",(side*1.25*k,gy,gz),(.35*k,3.8*k,.35*k),"gold")
    s.mass("hourglass_upper_bulb","body",(0,gy+.9*k,gz),(2.1*k,1.7*k,1.5*k),"glass")
    s.mass("hourglass_lower_bulb","body",(0,gy-.9*k,gz),(2.1*k,1.7*k,1.5*k),"glass")
    s.box("falling_time_sand","body",(0,gy,gz-.8*k),(.2*k,2.6*k,.12),"sand")
    s.box("hourglass_sand_heap","body",(0,gy-1.3*k,gz-.75*k),(1.4*k,.55*k,.2),"sand")
    if s.age>0:
        s.bone("tail","body",(0,by+2*k,1.5*k))
        for side in (-1,1):
            s.box("long_silk_sash","tail",(side*1.8*k,by-1.4*k,2*k),(1.3*k,7*k,.25*k),"dress",(-16,0,side*13))
            s.box("sash_gold_hem","tail",(side*2.5*k,by-4.3*k,2.8*k),(1.5*k,.45*k,.3),"gold")
    if s.age==2:
        for i in range(8):
            a=i*math.pi/4
            s.box("clock_halo_tick","head",(math.sin(a)*4.5,hy+math.cos(a)*4.5,1.3),(.35,1.1,.35),"gold",(0,0,-i*45))
    return s.finish()


_BUILDERS = {
    "arcane_nine_tails": _fox,
    "frost_wyvern": _wyvern,
    "astral_owl": _owl,
    "crimson_cerberus": _cerberus,
    "shadow_panther": _panther,
    "blood_gargoyle": _gargoyle,
    "runic_golem": _golem,
    "world_tree_entling": _ent,
    "crystal_tortoise": _tortoise,
    "abyssal_manta": _manta,
    "alchemist_mimic": _mimic,
    "chrono_fairy": _fairy,
}


def build(species, stage):
    """Return the sculpture contract consumed by the shared pet pack exporter."""
    try:
        builder = _BUILDERS[species]
    except KeyError as exc:
        raise ValueError(f"Unknown rare pet species: {species}") from exc
    return builder(stage)

