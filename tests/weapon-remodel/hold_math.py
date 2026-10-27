"""Independent hand-transform arithmetic for held-weapon checks (no game client).

Java: ItemInHandLayer moves to the arm pivot, applies Rx(-90) Ry(180) and
(+-1, 2, -10) px, then ItemTransform translates, rotates Rx Ry Rz and scales
about the model centre. Results are in Bedrock player axes (y up, ground 0).

Bedrock: bones are walked from the exported geometry and animation files. A
bound bone's model point (0, 24, 0) sits at the item bone pivot; that offset
puts both Mojang's trident pivot [0,24,0] and java2bedrock's [0,13,-3] in the
fist. Rotations use Blockbench's decoding, Rz(-z) Ry(y) Rx(-x) in Bedrock axes.
"""
import math

import numpy as np

OFF_HAND = "context.item_slot == 'off_hand'"


def rx(a):
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def ry(a):
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rz(a):
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def java_hand(view, point, left=False):
    r = view.get('rotation', [0, 0, 0])
    t = view.get('translation', [0, 0, 0])
    s = np.array(view.get('scale', [1, 1, 1]), dtype=float)
    f = -1 if left else 1
    local = rx(r[0]) @ ry(f * r[1]) @ rz(f * r[2]) @ (s * (np.asarray(point, dtype=float) - 8)) + [f * t[0], t[1], t[2]]
    q = np.array([5 if left else -5, 2, 0]) + rx(-90) @ ry(180) @ (local + [f, 2, -10])
    return np.array([q[0], 24 - q[1], q[2]])


def value(v, off_hand):
    if not isinstance(v, str):
        return float(v)
    condition, options = v.split('?')
    assert condition.strip() == OFF_HAND, v
    yes, no = options.split(':')
    return float(yes if off_hand else no)


def bedrock_hand(geometry_bones, animation_bones, bone, point, off_hand=False):
    """Walk a Bedrock-space model point from `bone` to the bound root and into the player."""
    by_name = {b['name']: b for b in geometry_bones}
    p = np.asarray(point, dtype=float)
    while True:
        b = by_name[bone]
        assert 'rotation' not in b, 'rest rotations are not used by the rig'
        pivot = np.array(b.get('pivot', [0, 0, 0]), dtype=float)
        anim = animation_bones.get(bone, {})
        angles = [value(v, off_hand) for v in anim.get('rotation', [0, 0, 0])]
        offset = np.array([value(v, off_hand) for v in anim.get('position', [0, 0, 0])])
        scale = anim.get('scale', 1)
        scale = np.array([value(v, off_hand) for v in scale]) if isinstance(scale, list) else np.full(3, float(scale))
        p = pivot + offset + rz(-angles[2]) @ ry(angles[1]) @ rx(-angles[0]) @ (scale * (p - pivot))
        if 'parent' not in b:
            assert 'binding' in b, 'the top bone must be bound to the item slot'
            break
        bone = b['parent']
    return np.array([6 if off_hand else -6, 15, 1]) + p - [0, 24, 0]


def java2bedrock(display, first):
    """Expected rig animation values for one view, derived from the Java display.

    java2bedrock's bone layout and base poses. Its first-person -tz is kept
    for vanilla handheld translations (tz = 1.13 -> -1.13) and other
    translations move rigidly: z = tz - 2.26.
    """
    view = 'firstperson_' if first else 'thirdperson_'
    out = {}
    for hand, left in (('righthand', False), ('lefthand', True)):
        d = display[view + hand]
        r, t, s = d.get('rotation', [0, 0, 0]), d.get('translation', [0, 0, 0]), d.get('scale', [1, 1, 1])
        out[left] = {
            'root': ([90, 60, -40], [4, 10, 4], 1.5) if first else ([90, 0, 0], [0, 13, -3], 1),
            'display_x': ([-r[0], 0, 0], [t[0] if left else -t[0], t[1], t[2] - 2.26 if first else t[2]], list(s)),
            'display_y': ([0, -r[1], 0], [0, 0, 0], 1),
            'display_z': ([0, 0, r[2]], [0, 0, 0], 1),
        }
    return out


def exported_values(animation_bones, bone, off_hand):
    anim = animation_bones.get(bone, {})
    scale = anim.get('scale', 1)
    return ([value(v, off_hand) for v in anim.get('rotation', [0, 0, 0])],
            [value(v, off_hand) for v in anim.get('position', [0, 0, 0])],
            [value(v, off_hand) for v in scale] if isinstance(scale, list) else float(scale))
