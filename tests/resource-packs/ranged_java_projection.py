"""26.3 first-person matrix reconstruction for ranged asset regression/previews.

Constants/order come from the local client FirstPersonHandsAndItemsRenderer
and ItemTransform bytecode. This deliberately includes the native use pose
before the JSON display transform; testing the authored model axis alone misses
the extra bow/crossbow rotations.
"""
from __future__ import annotations

import math
import numpy as np


def translation(x, y, z):
    matrix = np.eye(4)
    matrix[:3, 3] = [x, y, z]
    return matrix


def rotation(axis, degrees):
    c, s = math.cos(math.radians(degrees)), math.sin(math.radians(degrees))
    matrix = np.eye(4)
    i, j = ((1, 2), (2, 0), (0, 1))[axis]
    matrix[i, i] = matrix[j, j] = c
    matrix[i, j], matrix[j, i] = -s, s
    return matrix


def scaling(x, y, z):
    return np.diag([x, y, z, 1])


def first_person_matrix(name, state, display, *, ticks=0, quick_charge=0, left=False, main_hand=True):
    sign = -1 if left else 1
    matrix = translation(sign * 0.56, -0.52, -0.72)
    crossbow = name == 'runelock'
    if state.startswith('pulling_'):
        matrix = matrix @ translation(sign * (-0.4785682 if crossbow else -0.2785682),
                                      -0.094387 if crossbow else 0.18344387, 0.05731531 if crossbow else 0.15731531)
        matrix = matrix @ rotation(0, -11.935 if crossbow else -13.935)
        matrix = matrix @ rotation(1, sign * (65.3 if crossbow else 35.3)) @ rotation(2, sign * -9.785)
        fraction = min(1, ticks / ((25 - 5 * quick_charge) if crossbow else 20))
        power = fraction if crossbow else (fraction * fraction + 2 * fraction) / 3
        # The subpixel sine shake is omitted; it cannot hide a draw silhouette.
        matrix = matrix @ translation(0, 0, power * 0.04) @ scaling(1, 1, 1 + power * 0.2) @ rotation(1, sign * -45)
    elif crossbow and state in ('arrow', 'firework') and main_hand:
        matrix = matrix @ translation(sign * -0.641864, 0, 0) @ rotation(1, sign * 10)
    angles, offset, scale = display['rotation'], display['translation'], display['scale']
    matrix = matrix @ translation(offset[0] * sign / 16, offset[1] / 16, offset[2] / 16)
    matrix = matrix @ rotation(0, angles[0]) @ rotation(1, angles[1] * sign) @ rotation(2, angles[2] * sign)
    return matrix @ scaling(*scale) @ translation(-0.5, -0.5, -0.5) @ scaling(1 / 16, 1 / 16, 1 / 16)


def transform(matrix, points):
    points = np.asarray(points, dtype=float)
    return np.concatenate((points, np.ones((*points.shape[:-1], 1))), axis=-1) @ matrix.T[..., :3]


def project(points, width=1920, height=1080, fov=70):
    points = np.asarray(points, dtype=float)
    focal = height / (2 * math.tan(math.radians(fov / 2)))
    return points[..., :2] / -points[..., 2:3] * [focal, -focal] + [width / 2, height / 2]


def string_midpoint(mesh, name):
    wire = np.asarray([(np.array(element['from']) + element['to']) / 2
                       for element in mesh['elements'] if element.get('name', '').startswith('string_light_')])
    axis, value = (1, 16) if name == 'thornwhisper' else (0, 8)
    return wire[np.abs(wire[:, axis] - value) < 0.4].mean(axis=0)


def forward(matrix, name):
    axis = [-1, 0, 0] if name == 'thornwhisper' else [0, 1, 0]
    direction = matrix[:3, :3] @ axis
    return direction / np.linalg.norm(direction)
