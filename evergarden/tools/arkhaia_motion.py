"""Arkhaia's clips: the sword, the casting claw and planted feet are keyed; IK moves the limbs.

Clip names and lengths are the combat contract (arkhaia-combat.properties). Windup ends
on the active clip's first pose, active ends on recover's first pose, and recover ends
on idle, so ModelEngine's short blends never pop. The 'aura' clip is a separate loop.
"""
import math

import numpy as np

from arkhaia_sovereign import FIST_Y, GRIP_X, GRIP_Z, J, SIDES, TIMING, TIP, timing
from boss_forge import Clip, Poser, Track, ease, euler, frame2, lerp, rot, slerp, unit

FIST_R = np.array([GRIP_X, FIST_Y, GRIP_Z])
TIP_R = np.array(TIP)
CLAW_L = np.array([J['wrist_l'][0] + .95, 12.3, -.35])
HAND_BIND = frame2((0, -1, 0), (1, 0, 0))
SPLINES = ('grip', 'blade', 'left', 'foot_r', 'foot_l')


def hand_rot(axis, normal):
    """World rotation sending the hand's bind -Y to `axis` and bind +X toward `normal`."""
    return frame2(axis, normal) @ HAND_BIND.T


STANCE = dict(
    pelvis=[0, -7, 0], pelvis_pos=[0, -.9, 0], chest=[0, 7, 0], head=[0, 0, 0],
    foot_r=[5.2, 2.45, -2.9], foot_l=[-4.4, 2.45, 3.0], foot_yaw_r=[-16], foot_yaw_l=[20],
    grip=[8.8, 17.6, -6.6], blade=[.16, -.72, -.67], flat=[.95, .05, .3], pole_r=[10, -2, 9],
    left=[-8.6, 19.6, -5.4], left_axis=[.15, -.65, -.74], left_palm=[.85, -.1, -.4], pole_l=[-10, -2, 8],
    two_hand=[0], wing=[18, 9, 12, 8, 6], flap=[0, 0], cape=[-7, 4, 3], tabard=[-3, 4], legs_free=[0],
)


def key(t, base=STANCE, **changes):
    d = dict(base)
    d.update(changes)
    return (t, d)


class Builder:
    def __init__(self, forge):
        self.poser = Poser(forge.bones)
        self.floor = .4
        self.airborne = False

    def free_legs(self, pose, w, kick=0.0):
        for side, s in SIDES:
            phase = 1 if side == 'r' else -1
            angles = {f'leg_{side}': [24 + kick * phase, 0, s * 6], f'shin_{side}': [-40 - kick * .5 * phase, 0, 0],
                      f'foot_{side}': [30, 0, 0]}
            for bone, a in angles.items():
                old = np.array(pose.get(bone, [0, 0, 0]), float)
                pose[bone] = list(old * (1 - w) + np.array(a) * w)

    def legs(self, pose, tr, t, feet=None):
        for side, s in SIDES:
            target = np.array(feet[side][0] if feet else tr('foot_' + side, t), float)
            pitch = feet[side][1] if feet else 0.0
            yaw = float(tr('foot_yaw_' + side, t, [0])[0])
            pole = target + np.array([s * 1.5, 9, -14])
            self.poser.two_bone(pose, (f'leg_{side}', f'shin_{side}', f'foot_{side}'), target, pole, (-1, 0, 0),
                                end_rot=rot((pitch, yaw, 0)))

    def arms(self, pose, tr, t):
        mats = self.poser.fk(pose)
        shoulder_r = self.poser.world(mats, 'arm_r')
        shoulder_l = self.poser.world(mats, 'arm_l')
        grip, blade, flat = tr('grip', t), unit(tr('blade', t)), tr('flat', t)
        # Never bury the blade: tip the sword up just enough to graze the floor.
        floor = self.floor + .5 - (4.5 if self.airborne else 0)
        reach = float(np.linalg.norm(TIP_R - FIST_R))
        lowest = (floor - grip[1]) / reach
        if blade[1] < lowest:
            flat_dir = np.array([blade[0], 0, blade[2]])
            flat_dir = unit(flat_dir) if np.linalg.norm(flat_dir) > 1e-6 else np.array([0, 0, -1.])
            y = float(np.clip(lowest, -1, 1))
            blade = flat_dir * math.sqrt(1 - y * y) + np.array([0, y, 0])
        h = hand_rot(blade, flat)
        wrist = grip - h @ (FIST_R - J['wrist_r'])
        self.errors.append(self.poser.two_bone(pose, ('arm_r', 'forearm_r', 'hand_r'), wrist, shoulder_r + tr('pole_r', t),
                                               (1, 0, 0), end_rot=h))
        w = float(np.clip(tr('two_hand', t, [0])[0], 0, 1))
        free = hand_rot(unit(tr('left_axis', t)), tr('left_palm', t))
        held = hand_rot(blade, -np.asarray(flat, float))
        left_rot = slerp(free, held, w) if w > 0 else free
        on_grip = grip - blade * 2.7
        palm = lerp(tr('left', t), on_grip, w)
        wrist_l = palm - left_rot @ (CLAW_L - J['wrist_l'])
        self.errors.append(self.poser.two_bone(pose, ('arm_l', 'forearm_l', 'hand_l'), wrist_l, shoulder_l + tr('pole_l', t),
                                               (1, 0, 0), end_rot=left_rot))
        for side in ('r', 'l'):
            arm = np.array(pose[f'arm_{side}'], float)
            pose[f'shoulder_{side}'] = [float(np.clip(arm[0] * .38, -30, 60)), 0, float(arm[2] * .45)]

    def wings(self, pose, tr, t):
        lift, sweep, fold, curl, fan = tr('wing', t)
        amp, phase = tr('flap', t)
        for side, s in SIDES:
            beat = math.sin(phase) * amp
            pose[f'wing_{side}'] = [0, -s * sweep, s * (lift + beat)]
            pose[f'wing_mid_{side}'] = [0, -s * fold * .55, s * (-fold * .3 + amp * .45 * math.sin(phase - .6))]
            pose[f'wing_tip_{side}'] = [0, -s * fold * .45, s * (-curl + amp * .35 * math.sin(phase - 1.15))]
            pose[f'wing_fan_{side}'] = [0, 0, s * (fan + max(0.0, math.sin(phase + .4)) * amp * .25)]

    def lift_cape(self, pose):
        """Swing the lower cape back until its torn hem clears the floor."""
        hem = [np.array(v, float) for v in ((7.9, 1.0, 6.4), (-7.9, 1.0, 6.4), (0, .4, 7.2), (4, .6, 7.0), (-4, .6, 7.0))]
        for _ in range(4):
            mats = self.poser.fk(pose)
            low = min(self.poser.world(mats, 'cloak_tip', p)[1] for p in hem)
            if low >= self.floor + .25:
                return
            need = math.degrees(math.asin(min(1.0, (self.floor + .35 - low) / 9.0)))
            pose['cloak_mid'][0] -= need * .55
            pose['cloak_tip'][0] -= need * .7

    def cloth(self, pose, tr, t, wave=(1.5, 1.0), speed=0.0):
        c0, c1, c2 = tr('cape', t)
        body = float(np.array(pose['pelvis']['rot'] if isinstance(pose['pelvis'], dict) else pose['pelvis'])[0]) + float(pose['chest'][0])
        a, cycles = wave
        ph = t * math.tau * cycles
        pose['cloak'] = [c0 - .55 * body - speed * 6 + math.sin(ph) * a * .4, math.sin(ph * .5) * 1.5, 0]
        pose['cloak_mid'] = [c1 - speed * 8 + math.sin(ph - .7) * a, 0, math.sin(ph * .5 - .5) * 2]
        pose['cloak_tip'] = [c2 - speed * 10 + math.sin(ph - 1.4) * a * 1.4, 0, math.sin(ph * .5 - 1.0) * 3]
        mats = self.poser.fk(pose)
        swing = 0.0
        for side in ('r', 'l'):
            knee = self.poser.world(mats, f'shin_{side}')
            swing = max(swing, -(knee[2] + 1.6) * 6.5)
        t0, t1 = tr('tabard', t)
        pelvis_x = float(pose['pelvis']['rot'][0]) if isinstance(pose['pelvis'], dict) else float(pose['pelvis'][0])
        pose['tabard'] = [float(np.clip(max(t0 - .7 * pelvis_x, swing), -10, 70)) + math.sin(ph - .3) * a * .3, 0, 0]
        pose['tabard_tip'] = [t1 + math.sin(ph - 1.0) * a * .5, 0, 0]
        if float(np.clip(tr('legs_free', t, [0])[0], 0, 1)) < .5:
            self.lift_cape(pose)

    def pose(self, tr, t, extra=None, feet=None, wave=(1.5, 1.0), speed=0.0):
        pose = {'pelvis': {'rot': list(tr('pelvis', t)), 'pos': list(tr('pelvis_pos', t))},
                'chest': list(tr('chest', t)), 'head': list(tr('head', t))}
        if extra:
            extra(pose, t)
        # The head track is world-relative: the torso coils and twists, the head stays on target.
        torso = rot(pose['pelvis']['rot']) @ rot(pose['chest'])
        pose['head'] = list(euler(torso.T @ rot(pose['head'])))
        self.legs(pose, tr, t, feet)
        free = float(np.clip(tr('legs_free', t, [0])[0], 0, 1))
        if free > 0:
            self.free_legs(pose, free)
        self.arms(pose, tr, t)
        self.wings(pose, tr, t)
        self.cloth(pose, tr, t, wave, speed)
        return pose


def tremor(amount, rate=11.0):
    def apply(pose, t):
        k = amount * math.sin(t * rate * math.tau) * math.sin(t * rate * 1.7)
        pose['chest'][0] += k
        pose['head'][1] += k * 1.5
    return apply


def breath(length, amount=1.0):
    def apply(pose, t):
        b = math.sin(t / length * math.tau)
        pose['chest'][0] += -1.3 * b * amount
        pose['head'][0] += 1.0 * b * amount
        pose['pelvis']['pos'][1] += .12 * b * amount
    return apply


def clips(forge):
    b = Builder(forge)
    b.errors = []
    out = []

    def make(name, length, track, loop=False, extra=None, step=1 / 20, **kw):
        def fn(t):
            return b.pose(track, t, extra, **kw)
        out.append(Clip(name, length, fn, loop=loop, step=step))

    idle = Track([key(0), key(2.4)])
    make('idle', 2.4, idle, loop=True, extra=breath(2.4), wave=(1.5, 1 / 2.4))

    # Heavy, deliberate walk: planted soles travel back at body speed; the sword rides low.
    walk_base = dict(STANCE, pelvis=[2, 0, 0], chest=[-7, 0, 0], head=[8, 0, 0], grip=[8.4, 16.4, -4.4],
                     blade=[.1, -.82, -.56], left=[-8.4, 19.0, -2.4], wing=[11, 32, 24, 14, 10], cape=[-10, 2, 2])

    def gait(direction, lean):
        def feet_at(t):
            phase = t / .8
            result = {}
            for side, s in SIDES:
                p = (phase + (0 if side == 'r' else .5)) % 1
                stride = 6.6
                if p < .55:
                    u = p / .55
                    z = (-stride / 2 + stride * u) * direction
                    y, pitch = 0.0, 0.0
                else:
                    u = (p - .55) / .45
                    z = (stride / 2 - stride * ease(u)) * direction
                    y = 2.1 * math.sin(math.pi * u)
                    pitch = -18 * math.sin(math.pi * u) * direction
                result[side] = ((s * 3.7, 2.45 + y, .3 + z), pitch)
            return result

        def extra(pose, t):
            ph = t / .8 * math.tau
            pose['pelvis']['pos'] = [math.sin(ph) * .35, -1.0 + .32 * math.cos(2 * ph), 0]
            pose['pelvis']['rot'] = [lean, math.sin(ph) * 5 * direction, math.sin(ph) * 2.2]
            pose['chest'][1] -= math.sin(ph) * 7 * direction
            pose['chest'][2] -= math.sin(ph) * 1.6
            pose['head'][1] += math.sin(ph) * 3 * direction
        return feet_at, extra

    for name, direction, lean, base in (('walk', 1, 2, walk_base),
                                         ('backstep', -1, -6, dict(walk_base, chest=[6, 0, 0], head=[2, 0, 0],
                                                                    grip=[6.6, 21.0, -8.0], blade=[-.38, .3, -.87],
                                                                    flat=[.7, .65, .1], wing=[22, 8, 14, 8, 4], cape=[-4, 3, 2]))):
        feet_at, extra = gait(direction, lean)
        track = Track([(0, base), (.8, base)])

        def fn(t, track=track, feet_at=feet_at, extra=extra, direction=direction):
            pose = b.pose(track, t, extra, feet=feet_at(t), wave=(2.2, 1 / .8), speed=.6 * max(direction, 0))
            ph = t / .8 * math.tau
            pose['arm_l'][0] += math.sin(ph + math.pi) * 6 * direction
            for side, s in SIDES:
                pose[f'wing_{side}'][2] += s * math.sin(2 * ph) * 2.5
            return pose
        out.append(Clip(name, .8, fn, loop=True))

    # Hover: powerful downstrokes (fast) and slow recovery strokes; legs trail.
    hover_base = dict(STANCE, pelvis=[6, 0, 0], chest=[-4, 0, 0], head=[4, 0, 0], legs_free=[1], wing=[18, 14, 8, 6, 4],
                      grip=[8.0, 16.0, -1.0], blade=[.18, -.9, .4], flat=[.98, 0, .1], left=[-8.4, 20.6, -4.0],
                      cape=[-14, -4, -3])

    def hover_extra(pose, t):
        u = (t / 1.4) % 1
        down = u < .42
        stroke = ease(u / .42) if down else 1 - ease((u - .42) / .58)
        pose['pelvis']['pos'] = [0, .4 + 1.1 * (stroke if down else stroke * .7), 0]

    def hover_fn(t, track=Track([(0, hover_base), (1.4, hover_base)])):
        b.airborne = True
        u = (t / 1.4) % 1
        phase = math.pi / 2 + (u / .42 * math.pi if u < .42 else math.pi + (u - .42) / .58 * math.pi)
        tr = Track([(0, dict(hover_base, flap=[34, phase]))])
        pose = b.pose(tr, t, hover_extra, wave=(3.0, 1 / 1.4))
        b.free_legs(pose, 1, kick=math.sin(u * math.tau) * 6)
        b.airborne = False
        return pose
    out.append(Clip('hover', 1.4, hover_fn, loop=True))

    # --- Sword: light (one hand) and heavy (two-hand overhead slam). ---------------
    for action, heavy in (('light', False), ('heavy', True)):
        wind, active, impact, recovery, _ = timing(action)
        lift = 5.0 if heavy else 0.0
        ready = dict(STANCE, pelvis=[-2, 16, 0], pelvis_pos=[0, -1.2, 0], chest=[6 + (3 if heavy else 0), -22, 3], head=[2, 15, 0],
                     foot_l=[-4.8, 2.45, 3.6],
                     grip=[3.0 if heavy else 6.2, 36.5 + lift, 3.8 if heavy else 2.6],
                     blade=[-.1 if heavy else -.32, .05 if heavy else -.25, .99 if heavy else .91],
                     flat=[.95, 0, 0] if heavy else [.75, .55, .25], pole_r=[6, 6, -4],
                     left=[-5.6, 23.5, -9.0], left_axis=[.2, .2, -.95], left_palm=[.6, -.3, -.6], two_hand=[1 if heavy else 0],
                     pole_l=[-6, 4, -3], wing=[34, 12, 12, 6, 2], cape=[-12, 2, 4], tabard=[-6, 6])
        guard = dict(ready, grip=[7.6, 27.0, -4.0], blade=[-.1, .7, -.7] if not heavy else [0, .82, -.57],
                     chest=[1, -8, 1], pelvis=[0, 6, 0], pelvis_pos=[0, -1.0, 0], wing=[24, 20, 16, 9, 6])
        over = dict(ready, grip=[3.6 if not heavy else 1.8, 37.5 + lift, -3.4], blade=[-.2, .35, -.92] if not heavy else [0, .55, -.84],
                    chest=[-2, -4, 0], pelvis=[-4, 4, 0], pelvis_pos=[0, -1.6, 0])
        hit = dict(ready, pelvis=[-10, -14, 0], pelvis_pos=[0, -2.5, -.6], chest=[-20, 20, -3], head=[-6, -12, 0],
                   foot_l=[-3.4, 2.45, -4.6], foot_yaw_l=[8],
                   grip=[1.6 if not heavy else .8, 15.2, -13.0 if not heavy else -13.6],
                   blade=[-.12, -.42, -.9] if not heavy else [-.03, -.4, -.92], flat=[.95, -.25, 0],
                   pole_r=[7, 4, 7], pole_l=[-8, 2, 6], wing=[4, 2, 18, 4, 0], cape=[-20, -2, 6], tabard=[10, 2])
        # Follow-through: the blade keeps travelling a little and settles level at waist height.
        follow = dict(hit, grip=[-.6 if not heavy else .2, 13.0, -12.4], blade=[-.16, -.36, -.92] if not heavy else [-.04, -.36, -.93],
                      chest=[-24, 24, -4], pelvis=[-12, -16, 0], pelvis_pos=[0, -2.9, -.6], wing=[-2, 8, 20, 6, 4], cape=[-6, -4, 2])
        windup = Track([key(0), (wind * .45, dict(guard, ease='smooth')), (wind * .85, dict(ready, ease='out')), (wind, ready)], SPLINES)
        swing = Track([(0, dict(ready, ease='in')), (impact * .45, dict(over, ease='linear')), (impact, dict(hit, ease='out')),
                       (active, follow)], SPLINES)
        settle = dict(follow, grip=[4.0, 13.2, -8.4], blade=[.05, -.8, -.6], chest=[-14, 12, -2], pelvis=[-6, -6, 0],
                      pelvis_pos=[0, -1.8, -.3])
        recover = Track([(0, follow), (recovery * .35, dict(settle, ease='smooth')), (recovery, STANCE)], SPLINES)
        make(action + '_windup', wind, windup, extra=breath(2.4, .3))
        make(action + '_active', active, swing, step=1 / 40)
        make(action + '_recover', recovery, recover)

    # --- Spells ---------------------------------------------------------------
    low_sword = dict(grip=[10.2, 15.6, 1.8], blade=[.25, -.62, .74], flat=[.95, .2, -.2])
    palm_forward = dict(left_axis=[.05, .62, -.78], left_palm=[0, -.3, -.95])

    wind, active, impact, recovery, _ = timing('rift')
    gather = dict(STANCE, **low_sword, pelvis=[2, 10, 0], chest=[7, -8, 0], head=[10, 6, 0], left=[-4.0, 25.0, -4.6],
                  left_axis=[.6, .6, -.5], left_palm=[.9, 0, .4], wing=[32, 6, 10, 4, 0], cape=[-6, 3, 3])
    aim = dict(gather, **palm_forward, chest=[-6, 14, 0], head=[2, -10, 0], pelvis=[-2, -6, 0], left=[-5.2, 25.6, -14.0],
               wing=[30, -4, 6, 4, -2], cape=[-12, 2, 4])
    thrust = dict(aim, left=[-4.6, 26.0, -16.6], chest=[-10, 18, 0], pelvis_pos=[0, -1.3, -.9], foot_l=[-4.2, 2.45, -1.6],
                  wing=[38, -10, 2, 2, -4], cape=[-18, 0, 6])
    make('rift_windup', wind, Track([key(0), (wind * .45, gather), (wind * .85, dict(aim, ease='out')), (wind, aim)], SPLINES))
    make('rift_active', active, Track([(0, dict(aim, ease='snap')), (impact, thrust), (active, thrust)], SPLINES), extra=tremor(.8))
    make('rift_recover', recovery, Track([(0, thrust), (recovery * .55, dict(STANCE, ease='smooth')), (recovery, STANCE)], SPLINES))

    wind, active, impact, recovery, _ = timing('nova')
    coil = dict(STANCE, pelvis=[10, 0, 0], pelvis_pos=[0, -2.8, 0], chest=[-24, 0, 0], head=[-18, 0, 0],
                grip=[-1.0, 21.0, -7.6], blade=[-.9, -.32, -.28], flat=[0, .3, -.95], pole_r=[8, -4, 6],
                left=[3.0, 25.0, -8.0], left_axis=[.7, -.5, -.5], left_palm=[0, 0, 1], pole_l=[-8, -4, 6],
                wing=[-6, -30, 46, 30, 18], cape=[-2, 6, 6], tabard=[6, 6])
    burst = dict(STANCE, pelvis=[-6, 0, 0], pelvis_pos=[0, -.4, 0], chest=[14, 0, 0], head=[24, 0, 0],
                 grip=[15.5, 25.0, -5.0], blade=[.72, .55, -.42], flat=[0, .6, .8], pole_r=[4, -8, 8],
                 left=[-13.0, 25.5, -11.0], left_axis=[-.45, .55, -.7], left_palm=[-.3, -.2, -.93], pole_l=[-4, -8, 8],
                 wing=[46, 0, 0, -6, -6], cape=[-22, -4, -2], tabard=[-8, -2])
    make('nova_windup', wind, Track([key(0), (wind * .7, dict(coil, ease='out')), (wind, coil)], SPLINES), extra=tremor(.5, 7))
    make('nova_active', active, Track([(0, dict(coil, ease='snap')), (impact, burst), (active, burst)], SPLINES), extra=tremor(1.2))
    make('nova_recover', recovery, Track([(0, burst), (recovery * .6, dict(STANCE, ease='smooth')), (recovery, STANCE)], SPLINES))

    # Portals and Eclipse are cast airborne (the encounter lifts the body).
    wind, active, impact, recovery, _ = timing('portals')
    air = dict(STANCE, legs_free=[1], pelvis=[6, 0, 0], chest=[-2, 0, 0], head=[4, 0, 0], **low_sword, cape=[-14, -4, -3])
    raise_left = dict(air, left=[-12.6, 29.0, -6.0], left_axis=[-.45, .7, -.55], left_palm=[-.6, 0, -.8], chest=[2, -14, 4],
                      head=[6, 16, 0])
    sweep_end = dict(raise_left, left=[4.4, 26.4, -13.4], left_axis=[.55, .45, -.7], left_palm=[.3, -.1, -.95],
                     chest=[-6, 18, -3], head=[0, -14, 0])

    def flap_extra(rate, amp):
        def apply(pose, t):
            pose['pelvis']['pos'][1] += .55 + math.sin(t * rate * math.tau - 1) * .45
        return apply

    def flapping(track, rate, amp, airborne=True):
        def fn(t):
            tr = Track([(k_t, dict(d, flap=[amp, t * rate * math.tau])) for k_t, d in track.keys], SPLINES)
            b.airborne = airborne
            try:
                return b.pose(tr, t, flap_extra(rate, amp), wave=(3.0, rate))
            finally:
                b.airborne = False
        return fn
    out.append(Clip('portals_windup', wind, flapping(Track([key(0, air), (wind * .7, dict(raise_left, ease='out')),
                                                            (wind, raise_left)], SPLINES), 1 / .9, 26)))
    out.append(Clip('portals_active', active, flapping(Track([(0, dict(raise_left, ease='snap')), (impact + .5, sweep_end),
                                                              (active, sweep_end)], SPLINES), 1 / .9, 22)))
    out.append(Clip('portals_recover', recovery, flapping(Track([(0, sweep_end), (recovery * .7, dict(STANCE, ease='smooth')),
                                                                 (recovery, STANCE)], SPLINES), 1 / 1.1, 10, airborne=False)))

    wind, active, impact, recovery, _ = timing('eclipse')
    ascend = dict(air, wing=[44, 4, 0, -4, -4], chest=[8, 0, 0], head=[18, 0, 0], grip=[3.4, 41.5, -2.6],
                  blade=[-.05, .99, .1], flat=[1, 0, 0], two_hand=[1], pole_r=[8, 2, -4], pole_l=[-8, 2, -4],
                  left=[-4.0, 38.0, -3.0], cape=[-18, -6, -4])
    slam = dict(air, pelvis=[-14, 0, 0], chest=[-24, 0, 0], head=[-10, 0, 0], grip=[.6, 15.8, -13.6], blade=[-.02, -.36, -.93],
                flat=[1, 0, 0], two_hand=[1], pole_r=[8, 4, 6], pole_l=[-8, 4, 6], wing=[-4, -18, 16, 8, 4], cape=[-6, 2, 6], tabard=[14, 4])
    out.append(Clip('eclipse_windup', wind, flapping(Track([key(0, air), (1.2, dict(air, wing=[40, 6, 4, 0, 0], two_hand=[0])),
                                                            (wind * .8, dict(ascend, ease='out')), (wind, ascend)], SPLINES), 1 / 1.4, 14)))
    out.append(Clip('eclipse_active', active, flapping(Track([(0, dict(ascend, ease='in')), (impact, slam), (active, slam)], SPLINES),
                                                       1 / 2.0, 6)))
    out.append(Clip('eclipse_recover', recovery, flapping(Track([(0, slam), (recovery * .6, dict(STANCE, ease='smooth')),
                                                                 (recovery, STANCE)], SPLINES), 1 / 2.0, 4, airborne=False)))

    stun_len = int(TIMING['stun']) / 20
    slump = dict(STANCE, pelvis=[8, 4, -6], pelvis_pos=[0, -3.2, .6], chest=[-24, -6, 6], head=[-26, 8, 0],
                 grip=[8.6, 13.8, -5.2], blade=[.2, -.68, -.7], left=[-2.4, 24.6, -4.6], left_axis=[.5, -.3, -.8],
                 left_palm=[.2, 0, 1], wing=[-26, 42, 34, 22, 16], cape=[-2, 6, 6], tabard=[8, 4])
    jolt = dict(STANCE, chest=[12, 0, -4], head=[16, 0, 0], wing=[30, 10, 6, 4, 0])
    make('stun', stun_len, Track([key(0), (.12, jolt), (.4, dict(slump, ease='smooth')), (stun_len - .2, slump), (stun_len, STANCE)],
                                 SPLINES), extra=tremor(.6, 5))

    hunch = dict(STANCE, pelvis=[8, 0, 0], pelvis_pos=[0, -2.4, 0], chest=[-26, 0, 0], head=[-20, 0, 0],
                 grip=[3.0, 20.0, -8.0], blade=[-.4, -.8, -.45], left=[-2.4, 23.0, -6.8], left_axis=[.5, -.6, -.6],
                 left_palm=[0, 0, 1], wing=[-8, -36, 48, 30, 20], cape=[0, 6, 6])
    roar = dict(burst, chest=[16, 0, 0], head=[30, 0, 0], wing=[50, 2, -2, -8, -8])
    make('transition', 3.5, Track([key(0), (.7, dict(hunch, ease='in')), (1.0, dict(roar, ease='out')), (2.8, roar), (3.5, STANCE)],
                                  SPLINES), extra=tremor(1.0, 9))
    worst = max(b.errors) if b.errors else 0.0
    return out, worst


def aura_clip():
    def fn(t):
        u = t / 12.0
        pose = {'dread_halo': [0, 0, -360 * u], 'ember_ring': [0, 360 * u, 0]}
        for side, s in SIDES:
            pose[f'flame_{side}'] = {'rot': [math.sin(u * math.tau * 9) * 6, s * 360 * u * 6, math.sin(u * math.tau * 7 + s) * 6],
                                     'pos': [0, abs(math.sin(u * math.tau * 18)) * .25, 0]}
        return pose
    return Clip('aura', 12.0, fn, loop=True, step=1 / 20, bones=('dread_halo', 'ember_ring', 'flame_r', 'flame_l'))
