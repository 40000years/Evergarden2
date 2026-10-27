"""Acheron's clips: a low, light-footed blade-dancer. Feet are planted by IK; each Sorrow-Quill
is keyed by grip point, blade direction and edge direction and IK moves the arm; the scarf,
coat tails and tassets trail with gravity, wind and the spin, kept clear of the legs and floor.

Clip names, order and lengths are the river contract (ArkhaiaBedrockSupport.RIVER_CLIPS and
RiverVisual). cast_active is the spin: three full turns in 1.2 s, 45 degrees per tick, with
both weapon bones held out for the quill fans. ritual_* serves the split and the snare.

Geometry-space Euler degrees: negative X leans the torso forward and bows the head. Grip,
foot, pole and wind intents are authored in the body frame and turn with the root; the head
track is body-relative, so the torso can coil and twist while the mask stays on target.
"""
import itertools
import math

import numpy as np

from acheron_bladedancer import J, SCARF, SIDES
from boss_forge import Clip, Poser, Track, axis_angle, euler, frame2, rot, unit

CLIPS = ('idle', 'walk', 'hover', 'cast_windup', 'cast_active', 'cast_recover', 'attack_windup', 'attack_active',
         'attack_recover', 'stun', 'ritual_windup', 'ritual_active', 'ritual_recover')
SPLINES = ('grip_r', 'grip_l')
# The quill leaves the fist along the hand's -Y with its edge forward (-Z).
BLADE_BIND = frame2((0, -1, 0), (0, 0, -1))
TIP = np.array([0, -14.2, -2.35])
FLOOR = 0.0
SPIN_STEP = 45.0


# The guard: hips twisted and dropped into a wide crouch while the chest stays square and
# upright, so the gaze bone aims the mask exactly (the river idle contract checks t=0).
GUARD_HIPS = [-5, -16, 0]
STANCE = dict(
    turn=[0], lift=[0], pelvis=GUARD_HIPS, pelvis_pos=[0, -2.0, .3], chest=list(euler(rot(GUARD_HIPS).T)), head=[0, 0, 0], jaw=[0],
    foot_r=[-3.1, 1.7, -3.8], yaw_r=[12], pitch_r=[0], knee_r=[-1.5, 9, -14],
    foot_l=[3.8, 1.7, 3.2], yaw_l=[-40], pitch_l=[-18], knee_l=[1.5, 9, -14],
    grip_r=[-6.0, 19.8, -8.4], blade_r=[-.32, -.18, -.93], edge_r=[-.1, -1, .2], pole_r=[-7, -3, 6],
    grip_l=[7.0, 17.2, 1.8], blade_l=[.4, -.48, .78], edge_l=[.15, -1, -.3], pole_l=[7, -3, 5],
    wind=[0, .1, .4], spin=[0], flutter=[0],
)


def pose_of(base=STANCE, **changes):
    d = dict(base)
    d.update(changes)
    return d


def key(t, base=STANCE, **changes):
    return (t, pose_of(base, **changes))


def blade_rot(blade, edge):
    """World rotation of the hand that points the quill along `blade` with its edge toward `edge`."""
    return frame2(blade, edge) @ BLADE_BIND.T


class Layered:
    """A Track plus additive per-channel offsets `offsets(name, t)` (vector or None)."""

    def __init__(self, track, offsets):
        self.track, self.offsets = track, offsets

    def __call__(self, name, t, default=None):
        value = self.track(name, t, default)
        add = self.offsets(name, t)
        if add is None or value is None:
            return value
        return np.asarray(value, float) + np.asarray(add, float)


class Builder:
    def __init__(self, forge):
        self.poser = Poser(forge.bones)
        self.errors = []
        at = forge.at
        self.rest = {
            SCARF[0][0]: unit(at(SCARF[0][1]) - at(SCARF[0][0])), SCARF[0][1]: unit(at(SCARF[0][2]) - at(SCARF[0][1])),
            SCARF[1][0]: unit(at(SCARF[1][1]) - at(SCARF[1][0])), SCARF[1][1]: unit(at(SCARF[1][2]) - at(SCARF[1][1])),
            SCARF[0][2]: unit((-.25, -4.6, .85)), SCARF[1][2]: unit((.25, -4.6, .85)),
            'cape': unit(at('cape_tip') - at('cape')), 'cape_tip': unit((0, -8.0, 1.2)),
            'sash_r': unit((0, -8.8, -.35)), 'sash_l': unit((0, -8.8, -.35)),
        }
        # Every corner of every sabaton solid relative to its ankle, for the floor clamp.
        self.sole = {}
        for side, s in SIDES:
            corners = [part['center'] + part['r'] @ (part['size'] / 2 * np.array(c)) - J[f'ankle_{side}']
                       for part in forge.parts if part['bone'] == f'foot_{side}' for c in itertools.product((-1, 1), repeat=3)]
            self.sole[side] = np.array(corners)

    # Kinematic helpers ---------------------------------------------------
    def ground(self, side, target, r):
        """Lift the ankle so no corner of the sabaton, toe or heel spur sinks below the floor."""
        target = np.array(target, float)
        low = float((self.sole[side] @ r.T)[:, 1].min())
        target[1] = max(target[1], FLOOR - low)
        return target

    @staticmethod
    def above_floor(grip, blade, edge):
        """Tip the quill up just enough that its point grazes, never buries in, the floor."""
        for _ in range(3):
            h = blade_rot(blade, edge)
            tip = grip + h @ TIP
            if tip[1] >= FLOOR + .35:
                return blade
            flat = np.array([blade[0], 0, blade[2]])
            flat = unit(flat) if np.linalg.norm(flat) > 1e-6 else np.array([0, 0, -1.])
            y = float(np.clip(blade[1] + (FLOOR + .45 - tip[1]) / 14.4, -1, 1))
            blade = flat * math.sqrt(1 - y * y) + np.array([0, y, 0])
        return blade

    def aim(self, pose, bone, target, stiffness=1.0):
        """Rotate a hanging bone so its rest direction swings toward `target` (model space).
        Any sway already in the pose is kept as an offset around the hanging direction."""
        mats = self.poser.fk(pose)
        parent = mats[self.poser.bones[bone][0]][:3, :3]
        current = parent @ self.rest[bone]
        target = unit(target)
        axis = np.cross(current, target)
        if np.linalg.norm(axis) < 1e-6:
            return
        angle = math.degrees(math.atan2(np.linalg.norm(axis), current @ target)) * stiffness
        local = parent.T @ axis_angle(axis, angle) @ parent
        pose[bone] = list(np.array(euler(local @ rot(pose.get(bone, [0, 0, 0]))), float))

    def clear(self, pose, bone, target, frame_bone, obstacles, side, length, half_width, margin=.7):
        """Swing `target` about `frame_bone`'s X axis until the cloth hanging from `bone` passes
        `side` (+1 behind, -1 in front) of every obstacle point within its length and width."""
        mats = self.poser.fk(pose)
        frame = mats[frame_bone][:3, :3]
        origin = self.poser.world(mats, bone)
        d = frame.T @ unit(target)
        need = math.atan2(side * d[2], -d[1])
        for owner, local in obstacles:
            v = frame.T @ (self.poser.world(mats, owner, local) - origin)
            if -v[1] <= .2 or abs(v[0]) > half_width or np.linalg.norm(v) > length:
                continue
            reach = math.hypot(v[1], v[2])
            angle = math.atan2(side * v[2], -v[1]) + math.asin(min(1.0, margin / max(reach, margin)))
            need = max(need, angle)
        r = math.hypot(d[1], d[2])
        d = np.array([d[0], -r * math.cos(need), side * r * math.sin(need)])
        return frame @ d

    def lift_hem(self, pose, bones, hem, floor=FLOOR + .3):
        """Swing hanging cloth back until its hem clears the floor."""
        top, tip = bones
        for _ in range(5):
            mats = self.poser.fk(pose)
            low = min(self.poser.world(mats, tip, p)[1] for p in hem)
            if low >= floor:
                return
            need = math.degrees(math.asin(min(1.0, (floor + .1 - low) / 8.0)))
            pose[top][0] -= need * .5
            pose[tip][0] -= need * .8

    # Pose assembly -------------------------------------------------------
    def pose(self, tr, t, extra=None, feet=None, wave=1.0):
        turn = float(tr('turn', t)[0])
        lift = float(tr('lift', t)[0])
        pelvis, chest = list(tr('pelvis', t)), list(tr('chest', t))
        pose = {'root': {'rot': [0, turn, 0], 'pos': [0, lift, 0]},
                'pelvis': {'rot': pelvis, 'pos': list(tr('pelvis_pos', t))},
                'chest': chest, 'head': list(tr('head', t)), 'jaw': [float(tr('jaw', t)[0]), 0, 0]}
        if extra:
            extra(pose, t)
        # The head track is body-relative: undo the pelvis and chest so the mask holds its aim.
        torso = rot(pose['pelvis']['rot']) @ rot(pose['chest'])
        pose['head'] = list(euler(torso.T @ rot(pose['head'])))
        frame = rot([0, turn, 0])
        up = np.array([0, lift, 0])
        for side, s in SIDES:
            if feet:
                foot, pitch = feet(side, t)
            else:
                foot, pitch = tr('foot_' + side, t), float(tr('pitch_' + side, t)[0])
            r = frame @ rot((pitch, float(tr('yaw_' + side, t)[0]), 0))
            target = self.ground(side, frame @ np.asarray(foot, float) + up, r)
            pole = target + frame @ tr('knee_' + side, t)
            self.errors.append(self.poser.two_bone(pose, (f'leg_{side}', f'shin_{side}', f'foot_{side}'), target, pole,
                                                   (-1, 0, 0), end_rot=r))
        mats = self.poser.fk(pose)
        for side, s in SIDES:
            shoulder = self.poser.world(mats, f'arm_{side}')
            grip = frame @ tr('grip_' + side, t) + up
            edge = frame @ tr('edge_' + side, t)
            blade = self.above_floor(grip, frame @ unit(tr('blade_' + side, t)), edge)
            h = blade_rot(blade, edge)
            wrist = grip - h @ (J[f'grip_{side}'] - J[f'wrist_{side}'])
            self.errors.append(self.poser.two_bone(pose, (f'arm_{side}', f'forearm_{side}', f'hand_{side}'), wrist,
                                                   shoulder + frame @ tr('pole_' + side, t), (1, 0, 0), end_rot=h))
            arm = np.array(pose[f'arm_{side}'], float)
            pose[f'shoulder_{side}'] = [float(np.clip(arm[0] * .3, -20, 35)), 0, float(np.clip(arm[2] * .3, -22, 22))]
        self.cloth(pose, tr, t, frame, wave)
        return pose

    def cloth(self, pose, tr, t, frame, wave):
        wind = frame @ np.asarray(tr('wind', t), float)
        spin = float(tr('spin', t)[0])
        flutter = float(tr('flutter', t)[0])
        gravity = np.array([0, -1.0, 0])
        ph = t * math.tau * wave
        mats = self.poser.fk(pose)

        def drag(bone):
            # A spinning body throws its cloth outward and trails it against the turn.
            if not spin:
                return np.zeros(3)
            p = frame.T @ self.poser.world(mats, bone)
            r = np.array([p[0], 0, p[2]])
            return frame @ (-np.array([r[2], 0, -r[0]]) * .55 + r * .35) * spin

        for chain, s in zip(SCARF, (-1, 1)):
            for k, bone in enumerate(chain):
                amp = 2.5 + 3.0 * flutter + 1.5 * k
                pose[bone] = [math.sin(ph - k * .8 + s) * amp, 0, s * math.sin(ph * .5 + 1.3 + k * .9) * (1.5 + flutter)]
                target = gravity + wind * (1 + .3 * k) + drag(bone)
                # Never let the scarf fall into the back or the dorsal fins.
                chest = self.poser.fk(pose)['chest'][:3, :3]
                d = chest.T @ unit(target)
                if d[2] < .3:
                    d = unit(d + np.array([0, 0, .3 - d[2]]))
                self.aim(pose, bone, chest @ d, .85)
        pose['cape'] = [math.sin(ph + .3) * (2.0 + 2 * flutter), 0, math.sin(ph + 1.9) * 1.5]
        target = gravity + wind + drag('cape')
        target = self.clear(pose, 'cape', target, 'pelvis',
                            [(f'leg_{side}', p) for side, s in SIDES for p in ((s * 2.3, 12.6, 1.3), (s * 2.4, 9.8, 1.1))],
                            +1, 8.5, 3.6)
        self.aim(pose, 'cape', target, .8)
        pose['cape_tip'] = [math.sin(ph - .6) * (3.5 + 3 * flutter), 0, math.sin(ph + 1.3) * 2.5]
        target = gravity + wind * 1.3 + drag('cape_tip')
        target = self.clear(pose, 'cape_tip', target, 'pelvis',
                            [(f'shin_{side}', p) for side, s in SIDES for p in ((s * 2.5, 7.0, 1.0), (s * 2.6, 4.2, 1.3))]
                            + [(f'foot_{side}', (s * 2.6, 1.5, 2.9)) for side, s in SIDES], +1, 9.5, 3.8)
        self.aim(pose, 'cape_tip', target, .75)
        self.lift_hem(pose, ('cape', 'cape_tip'), [(.4, 2.0, 4.6), (-.4, 2.0, 4.6), (3.2, 3.4, 4.4), (-3.2, 3.4, 4.4)])
        for side, s in SIDES:
            bone = f'sash_{side}'
            pose[bone] = [math.sin(ph + s) * (1.5 + flutter), 0, s * (1.0 + math.sin(ph + .8 + s) * 1.0)]
            target = gravity + wind * .4 + drag(bone)
            target = self.clear(pose, bone, target, 'pelvis',
                                [(f'leg_{side}', p) for p in ((s * 2.3, 12.4, -1.25), (s * 2.4, 9.8, -1.3))]
                                + [(f'shin_{side}', (s * 2.45, 9.0, -1.55))], -1, 9.0, 1.6)
            self.aim(pose, bone, target, .9)
            self.lift_hem(pose, (bone, bone), [(s * 2.3, 8.8, -2.15), (s * 1.25, 10.2, -2.1), (s * 3.35, 10.2, -2.1)])


def continuous(fn):
    """Sample a pose function keeping Euler angles continuous between successive samples: each
    rotation takes whichever equivalent triple lies nearest the previous one, so the linear
    keys never swing a joint the long way round across a wrap or a gimbal flip."""
    state = {'t': None, 'prev': {}}

    def nearest(v, previous):
        best = None
        for c in (np.array(v, float), np.array([v[0] + 180, 180 - v[1], v[2] + 180], float)):
            c = c + 360 * np.round((previous - c) / 360)
            if best is None or np.abs(c - previous).sum() < np.abs(best - previous).sum():
                best = c
        return best

    def sample(t):
        if state['t'] is None or t <= state['t']:
            state['prev'] = {}
        state['t'] = t
        pose = fn(t)
        for bone, value in pose.items():
            angles = value['rot'] if isinstance(value, dict) else value
            if bone in state['prev']:
                angles = [float(v) for v in nearest(angles, state['prev'][bone])]
                if isinstance(value, dict):
                    value['rot'] = angles
                else:
                    pose[bone] = angles
            state['prev'][bone] = np.array(angles, float)
        return pose
    return sample


# ---------------------------------------------------------------------------
# Rhythms layered over the keyed intents


def breathing(length, bob=.35, sway=1.0):
    # Rotations pass through zero at t=0 so the loop starts square to the gaze.
    def apply(pose, t):
        u = t / length * math.tau
        pose['pelvis']['pos'][1] += math.sin(u) * bob
        pose['pelvis']['rot'][2] += math.sin(u) * 1.2 * sway
        pose['chest'][0] += math.sin(u) * 1.6
        pose['chest'][1] += math.sin(u * .5) * 2.0 * sway
        pose['head'][2] += math.sin(u + .4) * 2.0
    return apply


def circling(length, radius=.45):
    """The quill points draw slow circles: a duelist's restless guard."""
    def offsets(name, t):
        u = t / length * math.tau
        if name == 'grip_r':
            return [math.cos(u) * radius, math.sin(u) * radius, 0]
        if name == 'grip_l':
            return [math.cos(u + 2.2) * radius * .8, math.sin(u + 2.2) * radius * .8, 0]
        if name == 'blade_r':
            return [math.sin(u) * .08, math.cos(u) * .08, 0]
        if name == 'blade_l':
            return [0, math.sin(u + 1.3) * .08, 0]
        return None
    return offsets


def tremor(amount, rate=9.0):
    def apply(pose, t):
        k = amount * math.sin(t * rate * math.tau) * math.sin(t * rate * 1.7)
        pose['chest'][0] += k
        pose['chest'][2] += k * .6
    return apply


# ---------------------------------------------------------------------------
# Walk: a low, stalking glide; the planted sole travels back at body speed. At rate 1 the
# body covers 0.12 blocks per tick (RiverVisual plays walk at speed / 0.12).

STRIDE = 6.0
PLANTED = .62


def stalk_feet(side, t):
    s = -1 if side == 'r' else 1
    p = (t + (0 if side == 'r' else .5)) % 1.0
    if p < PLANTED:
        u = p / PLANTED
        z = -STRIDE / 2 + STRIDE * u
        pitch = -22 * max(0.0, (u - .78) / .22) ** 1.5
        return np.array([s * 2.35, 1.7, z]), pitch
    u = (p - PLANTED) / (1 - PLANTED)
    e = u * u * (3 - 2 * u)
    z = STRIDE / 2 - STRIDE * e
    y = 1.7 + math.sin(math.pi * u) * 2.4
    pitch = -22 * (1 - u) ** 2 + 10 * math.sin(math.pi * min(1.0, u * 1.25)) * u
    return np.array([s * (2.35 + math.sin(math.pi * u) * .35), y, z]), pitch


def stalk(pose, t):
    u = t * math.tau
    pose['pelvis']['pos'][1] += -.55 * math.cos(2 * u)
    pose['pelvis']['rot'][1] += 7 * math.sin(u)
    pose['pelvis']['rot'][2] += 3 * math.sin(u)
    pose['chest'][1] += -9 * math.sin(u)
    pose['chest'][0] += .8 * math.cos(2 * u)


def stalk_arms(name, t):
    u = t * math.tau
    if name == 'grip_r':
        return [0, .4 * math.cos(u), -1.2 * math.sin(u)]
    if name == 'grip_l':
        return [0, .4 * math.cos(u + math.pi), -1.2 * math.sin(u + math.pi)]
    return None


# ---------------------------------------------------------------------------


def clips(forge):
    b = Builder(forge)
    out = []

    def make(name, length, track, loop=False, extra=None, feet=None, wave=None):
        def fn(t):
            return b.pose(track, t, extra, feet, wave=(wave if wave is not None else (1 / length if loop else .9)))
        out.append(Clip(name, length, continuous(fn), loop=loop))

    # Idle: the duelist's crouch, right quill forward, left trailing; the points circle slowly.
    idle = Layered(Track([key(0), key(2.4)], SPLINES), circling(2.4))
    make('idle', 2.4, idle, loop=True, extra=breathing(2.4))

    # Walk: low and leaning, both quills trailing behind like a pair of swept wings.
    run = pose_of(pelvis=[-12, 0, 0], pelvis_pos=[0, -2.4, -.3], chest=[-16, 0, 0], head=[-4, 0, 0],
                  yaw_r=[4], yaw_l=[-4], pitch_l=[0], knee_r=[-1.5, 9, -14],
                  grip_r=[-6.4, 18.2, 3.0], blade_r=[-.3, -.36, .88], edge_r=[-.2, -1, -.2], pole_r=[-7, -1, 4],
                  grip_l=[6.4, 18.2, 3.0], blade_l=[.3, -.36, .88], edge_l=[.2, -1, -.2], pole_l=[7, -1, 4],
                  wind=[0, .3, 1.5], flutter=[.6])
    make('walk', 1.0, Layered(Track([key(0, run), key(1.0, run)], SPLINES), stalk_arms), loop=True, extra=stalk,
         feet=stalk_feet)

    # Hover (en garde): bouncing on the balls of the feet, the right quill raised.
    garde = pose_of(pelvis_pos=[0, -1.4, .3], pitch_r=[-8], pitch_l=[-24],
                    grip_r=[-5.2, 23.4, -8.0], blade_r=[-.2, .5, -.84], edge_r=[-.1, -.85, -.5],
                    grip_l=[5.4, 17.6, -5.6], blade_l=[.25, -.35, -.9], edge_l=[.2, -1, .3], pole_l=[7, -3, 4])

    def bounce(pose, t):
        u = t / 2.0 * math.tau
        pose['pelvis']['pos'][1] += -.45 * abs(math.cos(u * 2)) + .2
        pose['chest'][0] += 1.2 * math.sin(u * 2)
        pose['chest'][1] += 3 * math.sin(u)
    make('hover', 2.0, Layered(Track([key(0, garde), key(2.0, garde)], SPLINES), circling(1.0, .3)), loop=True, extra=bounce)

    # The spin (cast): coil hard against the turn, then whirl three times with both quills
    # flung out at arm's length, lifting off the floor; land low and rise.
    # Coiled to the left against the right-hand turn: the right quill drawn across to the left
    # shoulder for a backhand, the left drawn back; edges face the way they are about to travel.
    coil = pose_of(pelvis=[-10, -22, 0], pelvis_pos=[0, -4.2, .8], chest=[-14, -20, -4], head=[-8, -10, 0],
                   foot_r=[-4.4, 1.7, -2.8], yaw_r=[30], foot_l=[4.6, 1.7, 3.6], yaw_l=[-50], pitch_l=[-24],
                   grip_r=[7.5, 21.5, -2.5], blade_r=[.6, .25, .76], edge_r=[-.6, .3, -.4], pole_r=[-2, -6, -4],
                   grip_l=[7.6, 19.6, 5.6], blade_l=[.1, -.3, .95], edge_l=[.94, 0, -.34], pole_l=[6, -4, 4],
                   wind=[0, .1, .3])
    whirl = pose_of(pelvis=[-4, 0, 0], pelvis_pos=[0, -2.6, 0], chest=[-6, 0, 0], head=[-4, 0, 0], lift=[2.2],
                    foot_r=[-2.6, 3.6, -1.6], yaw_r=[6], pitch_r=[-20], foot_l=[2.8, 4.6, 1.8], yaw_l=[-12], pitch_l=[-30],
                    grip_r=[-17.2, 25.6, -.6], blade_r=[-.86, -.2, -.47], edge_r=[0, 0, 1], pole_r=[-3, -6, 3],
                    grip_l=[17.2, 25.6, .6], blade_l=[.86, -.2, .47], edge_l=[0, 0, -1], pole_l=[3, -6, -3],
                    spin=[1.0], wind=[0, .3, .1], flutter=[.8])
    landing = pose_of(pelvis=[-12, -6, 0], pelvis_pos=[0, -4.8, .4], chest=[-14, 10, 0], head=[-4, -4, 0],
                      foot_r=[-3.6, 1.7, -3.4], yaw_r=[16], foot_l=[4.2, 1.7, 3.4], yaw_l=[-46], pitch_l=[-30],
                      grip_r=[-9.6, 13.4, -4.0], blade_r=[-.7, -.55, -.45], edge_r=[-.2, -.5, 1],
                      grip_l=[9.4, 13.8, 3.2], blade_l=[.72, -.5, .48], edge_l=[.2, -.5, -1], spin=[.2], flutter=[.4])
    # The right quill passes in front of the chest, point forward, on its way to the shoulder.
    sweep = dict(grip_r=[-1.0, 21.5, -10.0], blade_r=[.5, .1, -.86], edge_r=[.3, -1, 0])
    make('cast_windup', 1.0, Track([key(0), (.35, sweep), (.7, dict(coil, ease='out')), (1.0, coil)], SPLINES), extra=tremor(.6))
    # No grip splines here: the arms must hold their full reach steady through all three turns.
    # The backhand crosses the front point-first before both arms reach full spread.
    unwind = dict(grip_r=[-2.0, 24.5, -10.5], blade_r=[-.35, .05, -.94], edge_r=[-1, 0, 0], ease='smooth')
    spin_keys = Track([(0, dict(coil, ease='smooth')), (.07, unwind), (.15, dict(whirl, ease='smooth')), (1.0, dict(whirl, ease='in')),
                       (1.2, dict(landing, spin=[.5]))])

    def spin_track(name, t, default=None):
        # A constant 45 degrees per tick: three full turns over the 24-tick active window.
        if name == 'turn':
            return np.array([SPIN_STEP * 20 * t])
        return spin_keys(name, t, default)
    make('cast_active', 1.2, spin_track)
    make('cast_recover', .7, Track([(0, dict(landing, spin=[.5], ease='out')), (.3, dict(landing, spin=[0])), (.7, STANCE)], SPLINES))

    # Attack: draw the right quill high behind the head, then a cross-cut that lunges
    # through the target (contact at attack_active 0.2 s) and a reverse sweep of the left.
    raise_ = pose_of(pelvis=[2, 8, 0], pelvis_pos=[0, -1.6, 1.2], chest=[-4, 22, -6], head=[-6, -18, 0],
                     foot_r=[-3.0, 1.7, -3.4], pitch_r=[-10], foot_l=[3.8, 1.7, 3.6],
                     grip_r=[-7.6, 31.4, 2.4], blade_r=[-.3, .55, .78], edge_r=[-.1, .8, -.6], pole_r=[-7, 2, 4],
                     grip_l=[4.6, 18.4, -7.8], blade_l=[.2, -.3, -.93], edge_l=[.2, -1, .2], pole_l=[7, -3, 5],
                     wind=[0, .1, .5])
    mid = pose_of(raise_, pelvis=[-6, -6, 0], pelvis_pos=[0, -2.4, -.4], chest=[-10, 0, -2], head=[-6, -4, 0],
                  grip_r=[-4.6, 30.8, -5.6], blade_r=[.22, .74, -.63], edge_r=[.35, -.55, -.75], wind=[0, .2, .8])
    cut = pose_of(raise_, pelvis=[-12, -22, 0], pelvis_pos=[0, -3.4, -1.8], chest=[-16, -14, 4], head=[-8, 6, 0],
                  foot_r=[-3.2, 1.7, -7.4], pitch_r=[6], foot_l=[3.6, 1.7, 2.6], pitch_l=[-28],
                  grip_r=[-.8, 21.0, -11.6], blade_r=[.62, -.4, -.68], edge_r=[.7, -.7, .1],
                  grip_l=[7.0, 19.6, -4.6], blade_l=[.55, .1, .83], edge_l=[-.2, 0, 1], pole_l=[7, -2, 4],
                  wind=[0, .2, 1.2], flutter=[.5])
    follow = pose_of(cut, pelvis=[-12, -24, 0], chest=[-14, -16, 6], head=[-8, 14, 0],
                     grip_r=[5.0, 14.6, -8.8], blade_r=[.62, -.55, .56], edge_r=[.3, -.4, -1],
                     grip_l=[3.2, 21.4, -9.4], blade_l=[-.55, .05, -.83], edge_l=[-.8, 0, .5], pole_l=[6, -5, 2])
    make('attack_windup', .8, Track([key(0), (.62, dict(raise_, ease='out')), (.8, raise_)], SPLINES), extra=tremor(.5))
    make('attack_active', .4, Track([(0, dict(raise_, ease='in')), (.1, dict(mid, ease='linear')), (.2, dict(cut, ease='out')),
                                     (.4, follow)], SPLINES))
    flourish = pose_of(follow, pelvis=[-8, -20, 0], pelvis_pos=[0, -2.6, -1.0], chest=[-12, -8, 2],
                       grip_r=[-2.0, 17.4, -9.2], blade_r=[-.4, .6, -.7], edge_r=[-.6, -.4, .2],
                       grip_l=[6.4, 18.0, -1.6], blade_l=[.45, -.6, .66], edge_l=[0, -.7, -.7], flutter=[.3])
    make('attack_recover', .7, Track([(0, follow), (.3, dict(flourish, ease='smooth')), (.7, STANCE)], SPLINES))

    # Stun: snapped back by the blow, then down on one knee with both quills driven into
    # the floor to hold itself up, head hung; springs back to guard at the end.
    flinch = pose_of(pelvis=[8, -10, 4], pelvis_pos=[0, -1.4, 1.4], chest=[16, 10, -6], head=[18, 14, 6], jaw=[-10],
                     grip_r=[-10.2, 25.6, -2.0], blade_r=[-.5, .4, .77], edge_r=[-.3, -.8, .4],
                     grip_l=[10.4, 24.4, 1.0], blade_l=[.6, .3, .74], edge_l=[.3, -.8, -.4], wind=[0, .2, -.4], flutter=[.6])
    kneel = pose_of(pelvis=[-14, -10, 6], pelvis_pos=[0, -6.4, 1.2], chest=[-20, 8, -6], head=[-30, -6, 10], jaw=[-6],
                    foot_r=[-2.8, 1.7, 5.4], yaw_r=[4], pitch_r=[-62], knee_r=[-1, -6, -14],
                    foot_l=[3.4, 1.7, -3.6], yaw_l=[-16], pitch_l=[0],
                    grip_r=[-6.6, 13.0, -6.2], blade_r=[-.06, -.98, -.18], edge_r=[0, 0, -1], pole_r=[-7, -2, 4],
                    grip_l=[7.6, 13.4, -4.4], blade_l=[.06, -.98, -.2], edge_l=[0, 0, -1], pole_l=[7, -2, 4],
                    wind=[0, 0, .25])
    make('stun', 1.5, Track([key(0), (.12, dict(flinch, ease='out')), (.5, dict(kneel, ease='smooth')), (1.25, kneel), (1.5, STANCE)],
                            SPLINES), extra=tremor(.7, 6))

    # Ritual (the split and the snare): the quills cross in an X before the mask, then are
    # torn apart to both sides as the echoes or seals break free, the mask thrown back.
    gather = pose_of(pelvis=[-8, 0, 0], pelvis_pos=[0, -4.4, .6], chest=[-12, 0, 0], head=[-16, 0, 0], jaw=[0],
                     foot_r=[-4.2, 1.7, -2.2], yaw_r=[22], pitch_r=[0], foot_l=[4.2, 1.7, 2.2], yaw_l=[-22], pitch_l=[0],
                     knee_l=[1.5, 9, -14],
                     grip_r=[-1.6, 27.6, -8.6], blade_r=[.62, .72, .3], edge_r=[0, 0, -1], pole_r=[-7, -4, 2],
                     grip_l=[1.6, 27.4, -8.2], blade_l=[-.62, .72, .3], edge_l=[0, 0, -1], pole_l=[7, -4, 2],
                     wind=[0, .5, .2], flutter=[.3])
    burst = pose_of(gather, pelvis=[6, 0, 0], pelvis_pos=[0, -1.2, .2], chest=[14, 0, 0], head=[16, 0, 0], jaw=[-16],
                    foot_r=[-5.0, 1.7, -1.6], yaw_r=[26], foot_l=[5.0, 1.7, 1.6], yaw_l=[-26],
                    grip_r=[-14.0, 22.0, -4.0], blade_r=[-.84, -.4, -.36], edge_r=[0, -.2, -1], pole_r=[-4, -6, 4],
                    grip_l=[14.0, 22.0, -4.0], blade_l=[.84, -.4, -.36], edge_l=[0, -.2, -1], pole_l=[4, -6, 4],
                    wind=[0, .6, 1.0], flutter=[1.0])
    # The trailing left quill comes round outside the body before it crosses the right.
    round_l = dict(grip_l=[7.8, 22.5, -7.6], blade_l=[.62, .45, -.64], edge_l=[.3, -.8, -.4])
    make('ritual_windup', 1.0, Track([key(0), (.4, round_l), (.75, dict(gather, ease='out')), (1.0, gather)], SPLINES),
         extra=tremor(1.0, 11))
    # Torn apart: both quills first open upward in front of the mask, then fling wide.
    tear = dict(grip_r=[-6.5, 27.0, -9.5], blade_r=[-.45, .85, -.27], grip_l=[6.5, 27.0, -9.5], blade_l=[.45, .85, -.27], ease='out')
    make('ritual_active', .5, Track([(0, dict(gather, ease='snap')), (.05, tear), (.14, burst), (.5, dict(burst, jaw=[-10]))],
                                    SPLINES))
    make('ritual_recover', .7, Track([(0, burst), (.7, STANCE)], SPLINES))
    assert tuple(c.name for c in out) == CLIPS
    # Reach errors accumulate while the clips are sampled; read them after export.
    return out, b.errors
