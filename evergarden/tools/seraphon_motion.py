"""Seraphon's clips: a floating judge. Hands are keyed by palm point, finger and palm
directions (IK moves the arms); six wings beat in staggered pairs; silk, hair and
sleeves hang under gravity with a trailing wave. The 'aura' clip loops separately.
"""
import math

import numpy as np

from boss_forge import Clip, Poser, Track, axis_angle, euler, frame2, rot, unit
from seraphon_verdict import J, SIDES, WINGS

PALM = {side: J[f'wrist_{side}'] + np.array([0, -1.0, -.05]) for side, _ in SIDES}
CLIPS = ('idle', 'walk', 'hover', 'cast_windup', 'cast_active', 'cast_recover', 'smite_windup', 'smite_active',
         'smite_recover', 'verdict_windup', 'verdict_active', 'verdict_recover', 'wings_windup', 'wings_active',
         'wings_recover', 'stun', 'transition', 'spawn')
SPLINES = ('palm_r', 'palm_l')


def hand_rot(side, fingers, palm):
    s = 1 if side == 'r' else -1
    return frame2(fingers, palm) @ frame2((0, -1, 0), (-s, 0, 0)).T


STANCE = dict(
    pelvis=[4, 0, 0], pelvis_pos=[0, .6, 0], chest=[-3, 0, 0], head=[-7, 0, 0],
    palm_r=[5.4, 22.8, -6.2], fingers_r=[.08, .82, -.3], facing_r=[-.25, .1, -.96], pole_r=[6, -6, 6],
    palm_l=[-6.0, 18.6, -5.4], fingers_l=[-.15, .1, -.98], facing_l=[0, 1, 0], pole_l=[-6, -6, 6],
    legs=[10, -14, 38, -6, -26, 44],
    upper=[2, 8, 2, 0], middle=[8, 6, 4, 0], lower=[-4, 10, 4, 0], flap=[0, 0, 1.0],
    cloth=[0, 0], hover=[0],
)


def key(t, base=STANCE, **changes):
    d = dict(base)
    d.update(changes)
    return (t, d)


class Builder:
    def __init__(self, forge):
        self.poser = Poser(forge.bones)
        self.errors = []

    def hang(self, pose, bone, rest_dir, stiffness=.75, wind=(0, 0, 0)):
        """Rotate a cloth bone so it falls toward gravity (plus wind) instead of following its parent."""
        mats = self.poser.fk(pose)
        parent = mats[self.poser.bones[bone][0]][:3, :3]
        current = parent @ unit(rest_dir)
        target = unit(np.array([0, -1, 0]) + np.asarray(wind, float))
        axis = np.cross(current, target)
        if np.linalg.norm(axis) < 1e-6:
            return
        angle = math.degrees(math.atan2(np.linalg.norm(axis), current @ target)) * stiffness
        world = axis_angle(axis, angle)
        local = parent.T @ world @ parent
        old = np.array(pose.get(bone, [0, 0, 0]) if not isinstance(pose.get(bone), dict) else pose[bone]['rot'], float)
        pose[bone] = list(np.array(euler(local @ rot(old)), float))

    def pose(self, tr, t, extra=None, wave=1.0, wind=(0, 0, .25)):
        pose = {'pelvis': {'rot': list(tr('pelvis', t)), 'pos': list(tr('pelvis_pos', t))},
                'chest': list(tr('chest', t)), 'head': list(tr('head', t))}
        if extra:
            extra(pose, t)
        lr, sr, fr, ll, sl, fl = tr('legs', t)
        pose.update(leg_r=[lr, 0, 3], shin_r=[sr, 0, 0], foot_r=[fr, 0, 0], leg_l=[ll, 0, -3], shin_l=[sl, 0, 0], foot_l=[fl, 0, 0])
        mats = self.poser.fk(pose)
        for side, s in SIDES:
            shoulder = self.poser.world(mats, f'arm_{side}')
            h = hand_rot(side, unit(tr('fingers_' + side, t)), tr('facing_' + side, t))
            wrist = tr('palm_' + side, t) - h @ (PALM[side] - J[f'wrist_{side}'])
            self.errors.append(self.poser.two_bone(pose, (f'arm_{side}', f'forearm_{side}', f'hand_{side}'), wrist,
                                                   shoulder + tr('pole_' + side, t), (1, 0, 0), end_rot=h))
            arm = np.array(pose[f'arm_{side}'], float)
            pose[f'shoulder_{side}'] = [float(np.clip(arm[0] * .35, -25, 50)), 0, float(arm[2] * .4)]
        amp, phase, lag = tr('flap', t)
        for i, pair in enumerate(WINGS):
            lift, sweep, fold, curl = tr(pair, t)
            weight = (.55, 1.0, .45)[i]
            ph = phase - i * .55 * lag
            for side, s in SIDES:
                beat = math.sin(ph) * amp * weight
                pose[f'wing_{pair}_{side}'] = [0, -s * sweep, s * (lift + beat)]
                pose[f'wing_{pair}_mid_{side}'] = [0, -s * fold * .6, s * (-fold * .2 + amp * weight * .4 * math.sin(ph - .6))]
                pose[f'wing_{pair}_tip_{side}'] = [0, -s * fold * .4, s * (-curl + amp * weight * .3 * math.sin(ph - 1.2))]
        sway, gust = tr('cloth', t)
        ph = t * math.tau * wave
        pose['skirt_front'] = [sway * .3 + math.sin(ph) * 2.0, 0, 0]
        pose['skirt_front_tip'] = [math.sin(ph - .8) * 3.0, 0, 0]
        pose['skirt_back'] = [-sway - gust + math.sin(ph + .5) * 2.0, 0, 0]
        pose['skirt_back_tip'] = [-gust * .6 + math.sin(ph - .4) * 3.5, 0, 0]
        for side, s in SIDES:
            pose[f'skirt_{side}'] = [-sway * .4, 0, s * (3 + math.sin(ph + s) * 2.0)]
            pose[f'skirt_tip_{side}'] = [-gust * .3, 0, s * (2 + math.sin(ph - .7 + s) * 2.5)]
            pose[f'sash_{side}'] = [-10 - gust * 1.2 + math.sin(ph + s * .6) * 4.0, 0, s * 4]
            pose[f'sash_tip_{side}'] = [-8 - gust + math.sin(ph - .9 + s * .6) * 7.0, 0, s * 3]
        pose['hair'] = [-4 - gust * .5 + math.sin(ph) * 1.5, 0, math.sin(ph * .5) * 1.5]
        pose['hair_tip'] = [-6 - gust * .6 + math.sin(ph - .9) * 3.0, 0, math.sin(ph * .5 - .6) * 2.5]
        # Sleeves fall from the elbows under gravity, trailing with the wind.
        for side, s in SIDES:
            pose[f'sleeve_{side}'] = [0, 0, 0]
            self.hang(pose, f'sleeve_{side}', (s * .05, -1, .32), .8, wind=np.asarray(wind) * (1 + gust * .04))
            pose[f'sleeve_{side}'][0] += math.sin(ph + s) * 3
        return pose


def bob(length, amount=.6):
    def apply(pose, t):
        pose['pelvis']['pos'][1] += math.sin(t / length * math.tau) * amount
        pose['chest'][0] += math.sin(t / length * math.tau - .6) * 1.2
        pose['head'][0] -= math.sin(t / length * math.tau - .6) * .8
    return apply


def clips(forge):
    b = Builder(forge)
    out = []

    def make(name, length, track, loop=False, extra=None, rate=0.0, amp=None, wave=None, wind=(0, 0, .25)):
        def fn(t):
            tr = track
            if rate:
                tr = Track([(k_t, dict(d, flap=[d['flap'][0] if amp is None else amp, t * rate * math.tau, d['flap'][2]]))
                            for k_t, d in track.keys], track.splines)
            return b.pose(tr, t, extra, wave=(wave if wave is not None else (1 / length if loop else .8)), wind=wind)
        out.append(Clip(name, length, fn, loop=loop))

    # Idle: a slow, regal float; wings breathe top to bottom like a ripple of light.
    idle = Track([key(0, flap=[5, 0, 1.0]), key(3.0, flap=[5, 0, 1.0])], SPLINES)
    make('idle', 3.0, idle, loop=True, extra=bob(3.0), rate=1 / 3.0)

    glide = dict(STANCE, pelvis=[14, 0, 0], pelvis_pos=[0, 1.6, 0], chest=[-6, 0, 0], head=[-2, 0, 0], legs=[-14, -18, 50, -24, -30, 54],
                 palm_r=[6.6, 19.4, -2.4], fingers_r=[.2, -.5, -.85], facing_r=[-.9, 0, .2],
                 upper=[4, 20, 6, 4], middle=[10, 22, 8, 4], lower=[0, 24, 8, 4], cloth=[10, 14])
    make('walk', 1.6, Track([key(0, glide, flap=[9, 0, 1.0]), key(1.6, glide, flap=[9, 0, 1.0])], SPLINES), loop=True,
         extra=bob(1.6, .35), rate=1 / 1.6, wind=(0, .2, 1.0))

    soar = dict(STANCE, pelvis=[8, 0, 0], legs=[-8, -24, 52, -16, -34, 56], upper=[6, 6, 2, 0], middle=[12, 4, 2, 0],
                lower=[0, 8, 2, 0], cloth=[6, 10])
    make('hover', 1.2, Track([key(0, soar, flap=[26, 0, 1.0]), key(1.2, soar, flap=[26, 0, 1.0])], SPLINES), loop=True,
         extra=lambda pose, t: pose['pelvis']['pos'].__setitem__(1, pose['pelvis']['pos'][1] + 1.2 + math.sin(t / 1.2 * math.tau + 1.2) * .9),
         rate=1 / 1.2, wind=(0, .6, .4))

    # Cast: raise the right hand to the sky, then bring judgment down on the target.
    raise_hand = dict(STANCE, chest=[4, -10, 2], head=[12, 6, 0], palm_r=[5.2, 36.6, -1.0], fingers_r=[0, 1, -.1],
                      facing_r=[0, .1, -1], pole_r=[8, -2, 4], palm_l=[-5.0, 21.6, -5.6],
                      upper=[10, 2, 0, 0], middle=[18, 0, 0, -4], lower=[4, 4, 0, 0], cloth=[2, 6])
    point = dict(STANCE, chest=[-8, 8, -2], head=[-4, -4, 0], palm_r=[3.6, 23.8, -12.4], fingers_r=[-.1, -.12, -.99],
                 facing_r=[0, -.1, -1], pole_r=[8, -4, 6], upper=[4, -6, 0, 0], middle=[10, -10, 2, 0], lower=[-2, -4, 2, 0],
                 cloth=[-4, 10])
    make('cast_windup', 1.0, Track([key(0), (.8, dict(raise_hand, ease='out')), (1.0, raise_hand)], SPLINES), rate=1 / 2.0, amp=6)
    make('cast_active', .6, Track([(0, dict(raise_hand, ease='in')), (.15, point), (.6, point)], SPLINES), rate=1 / 2.0, amp=4)
    make('cast_recover', .8, Track([(0, point), (.8, STANCE)], SPLINES), rate=1 / 2.0, amp=5)

    # Smite: gather a lance of light at the hip, then thrust both palms forward.
    draw = dict(STANCE, chest=[2, -24, 0], head=[-2, 18, 0], palm_r=[6.8, 20.4, 2.0], fingers_r=[0, -.2, -.98], facing_r=[-1, 0, 0],
                palm_l=[2.6, 20.8, -.6], fingers_l=[0, -.2, -.98], facing_l=[1, 0, 0], pole_l=[-4, -6, 6],
                middle=[16, 18, 6, 2], upper=[6, 16, 4, 0], lower=[0, 18, 4, 0], cloth=[4, 4])
    thrust = dict(STANCE, chest=[-10, 10, 0], head=[-4, -6, 0], palm_r=[2.2, 25.2, -13.2], fingers_r=[0, .4, -.9], facing_r=[0, .2, -1],
                  palm_l=[-1.6, 25.0, -13.0], fingers_l=[0, .4, -.9], facing_l=[0, .2, -1], pole_l=[-6, -4, 6],
                  middle=[14, 26, 2, 0], upper=[8, 22, 2, 0], lower=[2, 24, 2, 0], cloth=[-6, 16])
    make('smite_windup', .8, Track([key(0), (.7, dict(draw, ease='out')), (.8, draw)], SPLINES))
    make('smite_active', .5, Track([(0, dict(draw, ease='snap')), (.12, thrust), (.5, thrust)], SPLINES))
    make('smite_recover', .9, Track([(0, thrust), (.9, STANCE)], SPLINES))

    # Verdict (ultimate): ascend, arms and all six wings raised, then judgment falls.
    exalt = dict(STANCE, pelvis=[-4, 0, 0], pelvis_pos=[0, 3.4, 0], chest=[10, 0, 0], head=[22, 0, 0],
                 palm_r=[11.6, 33.4, -3.6], fingers_r=[.5, .85, -.1], facing_r=[.1, .3, -.95], pole_r=[6, -6, 4],
                 palm_l=[-11.6, 33.4, -3.6], fingers_l=[-.5, .85, -.1], facing_l=[-.1, .3, -.95], pole_l=[-6, -6, 4],
                 upper=[18, 0, -4, -6], middle=[30, -2, -4, -8], lower=[16, 2, -2, -4], legs=[0, -8, 50, -4, -12, 52], cloth=[-2, 4])
    judge = dict(STANCE, pelvis=[10, 0, 0], pelvis_pos=[0, 1.2, 0], chest=[-16, 0, 0], head=[-14, 0, 0],
                 palm_r=[7.0, 18.8, -9.6], fingers_r=[.3, -.7, -.65], facing_r=[0, -.6, -.8], pole_r=[8, -2, 6],
                 palm_l=[-7.0, 18.8, -9.6], fingers_l=[-.3, -.7, -.65], facing_l=[0, -.6, -.8], pole_l=[-8, -2, 6],
                 upper=[-6, -16, 8, 6], middle=[-12, -24, 10, 8], lower=[-14, -10, 6, 4], cloth=[-6, 18])
    make('verdict_windup', 2.0, Track([key(0), (1.6, dict(exalt, ease='out')), (2.0, exalt)], SPLINES), rate=1 / 1.4, amp=8)
    make('verdict_active', 1.2, Track([(0, dict(exalt, ease='in')), (.2, judge), (1.2, judge)], SPLINES), rate=1 / 2.0, amp=3)
    make('verdict_recover', 1.4, Track([(0, judge), (1.4, STANCE)], SPLINES), rate=1 / 2.0, amp=5)

    # Wing gale: the great middle pair cocks back, then sweeps forward.
    cock = dict(STANCE, chest=[6, 0, 0], head=[4, 0, 0], middle=[20, 46, 10, 6], upper=[10, 34, 6, 2], lower=[2, 36, 6, 2],
                palm_r=[7.4, 24.6, -3.0], palm_l=[-7.4, 24.6, -3.0], fingers_l=[-.4, .5, -.75], facing_l=[0, .3, -.95], cloth=[4, 4])
    gale = dict(STANCE, chest=[-10, 0, 0], head=[-6, 0, 0], middle=[2, -42, 4, -6], upper=[0, -30, 2, -4], lower=[-6, -30, 2, -4],
                palm_r=[6.0, 21.8, -8.0], cloth=[-6, 20])
    make('wings_windup', .9, Track([key(0), (.8, dict(cock, ease='out')), (.9, cock)], SPLINES))
    make('wings_active', .5, Track([(0, dict(cock, ease='in')), (.18, gale), (.5, gale)], SPLINES), wind=(0, .2, -1.0))
    make('wings_recover', .8, Track([(0, gale), (.8, STANCE)], SPLINES))

    droop = dict(STANCE, pelvis=[4, 0, 4], pelvis_pos=[0, -.5, 0], chest=[-22, 6, -6], head=[-28, -6, 4],
                 palm_r=[6.6, 15.6, -2.6], fingers_r=[0, -1, -.1], facing_r=[-1, 0, 0],
                 palm_l=[-5.4, 15.2, -2.0], fingers_l=[0, -1, -.1], facing_l=[1, 0, 0],
                 upper=[-16, 18, 14, 10], middle=[-26, 16, 18, 12], lower=[-20, 14, 12, 8], legs=[2, -30, 30, -8, -36, 30], cloth=[6, 0])
    make('stun', 1.8, Track([key(0), (.35, dict(droop, ease='out')), (1.55, droop), (1.8, STANCE)], SPLINES))

    cocoon = dict(STANCE, chest=[-14, 0, 0], head=[-18, 0, 0], palm_r=[1.4, 26.0, -4.4], fingers_r=[-.5, .7, -.4], facing_r=[-.8, 0, -.5],
                  palm_l=[-1.4, 26.0, -4.4], fingers_l=[.5, .7, -.4], facing_l=[.8, 0, -.5],
                  upper=[-6, -52, 18, 10], middle=[-16, -64, 20, 12], lower=[-12, -56, 16, 8], legs=[22, -40, 44, 20, -44, 46])
    radiant = dict(exalt, pelvis_pos=[0, 2.2, 0], upper=[22, -4, -6, -8], middle=[34, -6, -6, -10], lower=[20, -2, -4, -6])
    make('transition', 3.5, Track([key(0), (.9, dict(cocoon, ease='in')), (1.25, dict(radiant, ease='out')), (2.8, radiant),
                                   (3.5, STANCE)], SPLINES), rate=1 / 3.5, amp=4)

    descend = dict(STANCE, pelvis_pos=[0, 14.0, 0], pelvis=[-2, 0, 0], legs=[-4, -10, 56, -8, -16, 58],
                   palm_r=[9.6, 25.2, -2.6], fingers_r=[.6, -.6, -.5], palm_l=[-9.6, 25.2, -2.6], fingers_l=[-.6, -.6, -.5],
                   upper=[16, 4, 0, -4], middle=[24, 2, 0, -6], lower=[10, 4, 0, -2], cloth=[-8, 24])
    make('spawn', 3.0, Track([key(0, descend, flap=[28, 0, 1.0]), (2.2, dict(STANCE, flap=[10, 0, 1.0], ease='out')),
                              key(3.0, flap=[5, 0, 1.0])], SPLINES), rate=1 / .9, wind=(0, 1.0, .2))
    return out, max(b.errors) if b.errors else 0.0


def aura_clip():
    def fn(t):
        u = t / 12.0
        return {'halo': [0, 0, 360 * u], 'halo_ring': [0, 720 * u, 0], 'orbit': [0, -720 * u, 0], 'sigil': [0, 360 * u, 0],
                'orb': {'rot': [720 * u, 1080 * u, 0], 'pos': [0, math.sin(u * math.tau * 6) * .35, 0]}}
    return Clip('aura', 12.0, fn, loop=True, step=1 / 20, bones=('halo', 'halo_ring', 'orbit', 'sigil', 'orb'))
