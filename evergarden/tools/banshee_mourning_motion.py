"""Banshee's clips: she floats and never walks. Hands are keyed by palm point, finger and palm
directions (IK moves the arms); the jaw drops for the wail; hair, veil, cape, sleeves, gown
and lantern trail with gravity, wind and a travelling wave. The 'aura' clip loops separately.

Geometry-space Euler degrees: negative X leans the torso forward, bows the head, opens the
jaw and streams hanging cloth backward.
"""
import math

import numpy as np

from boss_forge import Clip, Poser, Track, axis_angle, euler, frame2, rot, unit
from banshee_mourning import J, SIDES, VEILS

PALM = {side: J[f'wrist_{side}'] + np.array([0, -1.0, -.05]) for side, _ in SIDES}
CLIPS = ('idle', 'walk', 'hover', 'cast_windup', 'cast_active', 'cast_recover', 'attack_windup', 'attack_active',
         'attack_recover', 'stun', 'curse_windup', 'curse_active', 'curse_recover', 'reveal', 'reform')
SPLINES = ('palm_r', 'palm_l')


def hand_rot(side, fingers, palm):
    s = 1 if side == 'r' else -1
    return frame2(fingers, palm) @ frame2((0, -1, 0), (-s, 0, 0)).T


def mirror(right):
    """Right-side palm/finger/facing/pole intents copied to the left with X negated."""
    out = {}
    for key, value in right.items():
        out[key] = value
        if key.endswith('_r'):
            out[key[:-2] + '_l'] = [-value[0], value[1], value[2]]
    return out


STANCE = mirror(dict(
    pelvis=[-4, 0, 0], pelvis_pos=[0, 1.2, 0], chest=[-3, 0, 0], head=[-9, 0, 0], jaw=[-2], turn=[0],
    palm_r=[6.4, 21.4, -5.6], fingers_r=[.16, -.78, -.6], facing_r=[.1, -.42, .9], pole_r=[6, -3, 7],
    cloth=[0, 4],
))


def key(t, base=STANCE, **changes):
    d = dict(base)
    d.update(mirror(changes))
    return (t, d)


def pose_of(base=STANCE, **changes):
    d = dict(base)
    d.update(mirror(changes))
    return d


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

    def pose(self, tr, t, extra=None, wave=1.0, wind=(0, 0, .3)):
        pose = {'root': list(np.array([0, tr('turn', t)[0], 0])),
                'pelvis': {'rot': list(tr('pelvis', t)), 'pos': list(tr('pelvis_pos', t))},
                'chest': list(tr('chest', t)), 'head': list(tr('head', t)), 'jaw': [float(tr('jaw', t)[0]), 0, 0]}
        if extra:
            extra(pose, t)
        mats = self.poser.fk(pose)
        # Hand intents are authored in the body frame; the root turn (reform spin) carries them along.
        turn = rot(pose['root'])
        for side, s in SIDES:
            shoulder = self.poser.world(mats, f'arm_{side}')
            h = turn @ hand_rot(side, unit(tr('fingers_' + side, t)), tr('facing_' + side, t))
            wrist = turn @ tr('palm_' + side, t) - h @ (PALM[side] - J[f'wrist_{side}'])
            self.errors.append(self.poser.two_bone(pose, (f'arm_{side}', f'forearm_{side}', f'hand_{side}'), wrist,
                                                   shoulder + turn @ tr('pole_' + side, t), (1, 0, 0), end_rot=h))
            arm = np.array(pose[f'arm_{side}'], float)
            pose[f'shoulder_{side}'] = [float(np.clip(arm[0] * .3, -25, 40)), 0, float(np.clip(arm[2] * .35, -25, 25))]
        sway, gust = tr('cloth', t)
        # Everything that hangs settles under gravity plus the clip's wind; 'gust' adds a rush of air.
        wind = np.asarray(wind, float) + np.array([0, 0, gust * .045])
        ph = t * math.tau * wave
        # Gown panels sway on a travelling wave and trail behind with the wind.
        pose['skirt_front'] = [-sway * .2 + math.sin(ph) * 2.0, 0, 0]
        pose['skirt_back'] = [-sway * .6 + math.sin(ph + .5) * 2.5, 0, 0]
        for side, s in SIDES:
            pose[f'skirt_{side}'] = [-sway * .3 + math.sin(ph + s) * 1.5, 0, s * (2.5 + math.sin(ph + s) * 2.0)]
        for bone in ('skirt_front', 'skirt_back', 'skirt_r', 'skirt_l'):
            self.hang(pose, bone, (0, -1, 0), .45, wind=wind * .35)
        pose['skirt_front_tip'] = [math.sin(ph - .8) * 3.5, 0, 0]
        pose['skirt_back_tip'] = [math.sin(ph - .4) * 4.5, 0, 0]
        for side, s in SIDES:
            pose[f'skirt_{side}_tip'] = [math.sin(ph - .7 + s) * 2.5, 0, s * (2 + math.sin(ph - .7 + s) * 2.5)]
        for bone in ('skirt_front_tip', 'skirt_back_tip', 'skirt_r_tip', 'skirt_l_tip'):
            self.hang(pose, bone, (0, -1, 0), .35, wind=wind * .6)
        pose['cape'] = [math.sin(ph + .3) * 3.0, 0, math.sin(ph + 1.9) * 2.0]
        self.hang(pose, 'cape', (0, -.974, .224), .7, wind=wind)
        pose['cape_tip'] = [math.sin(ph - .6) * 5.0, 0, math.sin(ph + 1.3) * 3.0]
        self.hang(pose, 'cape_tip', (0, -.968, .249), .55, wind=wind * 1.2)
        pose['hair'] = [math.sin(ph) * 2.0, 0, math.sin(ph + 2.2) * 2.0]
        self.hang(pose, 'hair', (0, -.987, .159), .6, wind=wind * .8)
        pose['hair_tip'] = [math.sin(ph - .9) * 4.5, 0, math.sin(ph + 1.5) * 3.0]
        self.hang(pose, 'hair_tip', (0, -.99, .14), .5, wind=wind)
        for side, s in SIDES:
            pose[f'lock_{side}'] = [math.sin(ph + s) * 2.0, 0, s * (1.5 + math.sin(ph + 2.0 + s) * 1.5)]
            self.hang(pose, f'lock_{side}', (s * .1, -.99, -.1), .45, wind=wind * .5)
        for n in range(VEILS):
            spread = (n - (VEILS - 1) / 2) * 1.6
            pose[f'veil_{n}'] = [math.sin(ph - n * .55) * 4.5, 0, spread + math.sin(ph + 1.7 + n * .8) * 2.5]
            self.hang(pose, f'veil_{n}', (0, -.99, .14), .75, wind=wind * 1.15)
        for side, s in SIDES:
            pose[f'sleeve_{side}'] = [0, 0, 0]
            self.hang(pose, f'sleeve_{side}', (s * .08, -1, .3), .8, wind=wind * (1 + gust * .03))
            pose[f'sleeve_{side}'][0] += math.sin(ph + s) * 4
        pose['lantern'] = [0, 0, 0]
        self.hang(pose, 'lantern', (-.1, -1, -.08), .9, wind=wind * .3)
        pose['lantern'][0] += math.sin(ph + 1.1) * 6
        pose['lantern'][2] += math.sin(ph + 2.4) * 4
        return pose


def bob(length, amount=.8):
    def apply(pose, t):
        u = t / length * math.tau
        pose['pelvis']['pos'][1] += math.sin(u) * amount
        pose['pelvis']['rot'][2] += math.sin(u + 1.2) * 2.0
        pose['chest'][0] += math.sin(u - .7) * 1.5
        pose['chest'][2] -= math.sin(u + .8) * 1.5
        pose['head'][0] -= math.sin(u - .7) * 1.0
        pose['head'][2] += math.sin(u + .3) * 3.0
    return apply


def clips(forge):
    b = Builder(forge)
    out = []

    def make(name, length, track, loop=False, extra=None, wave=None, wind=(0, 0, .3)):
        def fn(t):
            return b.pose(track, t, extra, wave=(wave if wave is not None else (1 / length if loop else .8)), wind=wind)
        out.append(Clip(name, length, fn, loop=loop))

    # Idle: drifting with lowered hands, as if mourning over the water.
    rest = pose_of(head=[-14, 0, 0], palm_r=[6.0, 17.8, -3.6], fingers_r=[.1, -.95, -.25], facing_r=[-.3, -.1, .95],
                   pole_r=[6, -2, 6], cloth=[0, 2])
    make('idle', 2.4, Track([key(0, rest), key(2.4, rest)], SPLINES), loop=True, extra=bob(2.4, .6))
    glide = pose_of(pelvis=[-12, 0, 0], chest=[-8, 0, 0], head=[2, 0, 0], palm_r=[6.6, 19.6, 1.6], fingers_r=[.1, -.7, .7],
                    facing_r=[-.6, -.4, .2], pole_r=[6, 0, 6], cloth=[8, 20])
    make('walk', 1.0, Track([key(0, glide), key(1.0, glide)], SPLINES), loop=True, extra=bob(1.0, .35), wind=(0, .25, 1.2))
    # Hover: the combat float, both hands drifting forward with limp wrists.
    make('hover', 2.0, Track([key(0), key(2.0)], SPLINES), loop=True, extra=bob(2.0), wind=(0, .1, .45))

    # Cast (demo): open the arms to the sides, then push both palms at the target.
    spread = pose_of(chest=[6, 0, 0], head=[4, 0, 0], palm_r=[11.6, 27.4, -1.2], fingers_r=[.9, .25, -.3],
                     facing_r=[.1, 1, .1], pole_r=[4, -6, 6], cloth=[-2, 6], jaw=[-6])
    push = pose_of(chest=[-14, 0, 0], head=[4, 0, 0], palm_r=[3.0, 25.4, -11.4], fingers_r=[0, .35, -.94],
                   facing_r=[0, .1, -1], pole_r=[6, -4, 6], cloth=[-6, 16], jaw=[-14])
    make('cast_windup', 1.0, Track([key(0), (.8, dict(spread, ease='out')), (1.0, spread)], SPLINES))
    make('cast_active', .5, Track([(0, dict(spread, ease='in')), (.15, push), (.5, push)], SPLINES), wind=(0, .2, 1.0))
    make('cast_recover', .7, Track([(0, push), (.7, STANCE)], SPLINES))

    # The wail: inhale with the arms swept back, then the scream thrown forward from the
    # mouth (head_forward); the hit lands at attack_active .2s.
    inhale = pose_of(pelvis=[2, 0, 0], pelvis_pos=[0, 2.2, 0], chest=[14, 0, 0], head=[16, 0, 0], jaw=[-10],
                     palm_r=[9.2, 25.8, 2.6], fingers_r=[.5, -.2, .85], facing_r=[-.4, -.8, .2], pole_r=[5, -3, 7], cloth=[-6, 0])
    scream = pose_of(pelvis=[-10, 0, 0], pelvis_pos=[0, 1.0, -.6], chest=[-22, 0, 0], head=[8, 0, 0], jaw=[-28],
                     palm_r=[11.4, 27.6, 4.6], fingers_r=[.65, .15, .75], facing_r=[0, -1, .2], pole_r=[3, -5, 6], cloth=[12, 26])
    make('attack_windup', .8, Track([key(0), (.7, dict(inhale, ease='out')), (.8, inhale)], SPLINES))
    make('attack_active', .4, Track([(0, dict(inhale, ease='snap')), (.12, scream), (.4, scream)], SPLINES), wind=(0, .25, 1.8))
    make('attack_recover', .7, Track([(0, scream), (.25, dict(scream, jaw=[-10])), (.7, STANCE)], SPLINES), wind=(0, .15, .9))

    droop = pose_of(pelvis=[-6, 0, 6], pelvis_pos=[0, .2, 0], chest=[-20, 4, -6], head=[-32, -8, 6], jaw=[-12],
                    palm_r=[6.2, 15.6, -1.6], fingers_r=[0, -1, -.1], facing_r=[-1, 0, 0], pole_r=[6, 0, 4],
                    cloth=[6, 0])
    flinch = pose_of(chest=[10, 0, 4], head=[14, 6, 0], jaw=[-16], palm_r=[8.4, 25.0, 1.0], cloth=[-4, 10])
    make('stun', 1.5, Track([key(0), (.12, dict(flinch, ease='out')), (.5, dict(droop, ease='smooth')), (1.25, droop), (1.5, STANCE)],
                            SPLINES))

    # Curse: both hands raised to gather the soul seal, then cast it down onto the mark.
    gather = pose_of(pelvis=[2, 0, 0], pelvis_pos=[0, 2.4, 0], chest=[6, 0, 0], head=[-20, 0, 0], jaw=[-4],
                     palm_r=[5.4, 40.2, -2.6], fingers_r=[.25, .95, -.15], facing_r=[-1, .1, -.1], pole_r=[7, -3, 3], cloth=[-4, 2])
    cast_down = pose_of(pelvis=[-8, 0, 0], pelvis_pos=[0, 1.4, 0], chest=[-18, 0, 0], head=[-6, 0, 0], jaw=[-18],
                        palm_r=[3.6, 23.0, -11.2], fingers_r=[0, -.55, -.84], facing_r=[0, -.84, .55], pole_r=[6, -3, 6], cloth=[10, 22])
    make('curse_windup', 1.0, Track([key(0), (.85, dict(gather, ease='out')), (1.0, gather)], SPLINES), wind=(0, .6, .5))
    make('curse_active', .5, Track([(0, dict(gather, ease='in')), (.16, cast_down), (.5, cast_down)], SPLINES), wind=(0, .2, 1.3))
    make('curse_recover', .7, Track([(0, cast_down), (.7, STANCE)], SPLINES))

    # Reveal: the broken veil throws her open, face to the sky in a silent cry.
    exposed = pose_of(pelvis=[6, 0, 0], pelvis_pos=[0, 2.8, 0], chest=[20, 0, 0], head=[24, 0, 0], jaw=[-24],
                      palm_r=[12.0, 30.4, 1.6], fingers_r=[.85, .4, .3], facing_r=[0, .2, -1], pole_r=[3, -6, 6], cloth=[-8, 10])
    make('reveal', 1.0, Track([key(0), (.22, dict(exposed, ease='snap')), (.75, exposed), (1.0, dict(exposed, chest=[12, 0, 0],
                                                                                                      head=[12, 0, 0], jaw=[-16]))],
                              SPLINES), wind=(0, .5, .5))
    # Reform: she wraps herself and turns once as the veil knits closed, then unfurls.
    wrap = pose_of(pelvis=[-6, 0, 0], pelvis_pos=[0, .4, 0], chest=[-14, 0, 0], head=[-24, 0, 0], jaw=[-2],
                   palm_r=[-2.0, 27.0, -5.0], fingers_r=[-.9, .3, .1], facing_r=[0, 0, 1], pole_r=[8, -3, 0], cloth=[14, 24])
    make('reform', 1.0, Track([(0, dict(STANCE, ease='in')), (.3, dict(wrap, turn=[120], ease='linear')), (.7, dict(wrap, turn=[300], ease='out')),
                               (1.0, dict(STANCE, turn=[360]))],
                              SPLINES), wind=(.6, .3, .4))
    assert tuple(c.name for c in out) == CLIPS
    return out, max(b.errors) if b.errors else 0.0


def aura_clip():
    def fn(t):
        u = t / 12.0
        sway = math.sin(u * math.tau * 2)
        return {'halo': [sway * 2.0, 0, sway * 6.0], 'wisps': {'rot': [0, -360 * u, 0], 'pos': [0, math.sin(u * math.tau * 3) * .8, 0]}}
    return Clip('aura', 12.0, fn, loop=True, step=1 / 20, bones=('halo', 'wisps'))
