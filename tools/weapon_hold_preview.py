"""Third-person hold previews computed from the exported hand transforms.

Places each weapon on a Bedrock player model with the vanilla holding arm
(-18 degrees) using: the former trident-frame rig, the current java2bedrock rig
and Java's ItemInHandLayer. Arithmetic renders of the transforms, not game
screenshots.
"""
from __future__ import annotations

import math
from pathlib import Path
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
sys.path.insert(0, str(ROOT / 'evergarden/tools'))
import weapon_forge as wf  # noqa: E402

SHOULDER = np.array([-5.0, 22.0, 0.0])
HOLDING = wf.bedrock_rotation([-18, 0, 0])
PLAYER = (  # Bedrock geometry.humanoid.custom: origin, size, colour, follows the right arm
    ((-4, 24, -4), (8, 8, 8), (196, 150, 118), False),
    ((-4, 12, -2), (8, 12, 4), (52, 112, 140), False),
    ((-8, 12, -2), (4, 12, 4), (52, 112, 140), True),
    ((4, 12, -2), (4, 12, 4), (52, 112, 140), False),
    ((-4, 0, -2), (4, 12, 4), (58, 58, 112), False),
    ((0, 0, -2), (4, 12, 4), (58, 58, 112), False),
)


def old_trident_third(point, grip_y):
    """The former rig: trident pivot [0,24,0], wield pose and a 0.9 grip child."""
    b = np.array([8 - point[0], point[1] - grip_y + 12.5, point[2] - 8], dtype=float)
    g = np.array([0, 12.5, 0]) + .9 * (b - [0, 12.5, 0])
    r = np.array([0, 24, 0]) + [1.5, -2.5, -10.5] + wf.bedrock_rotation([97, -1.5, -49]) @ (g - [0, 24, 0])
    return np.array([-6, 15, 1]) + r - [0, 24, 0]


def corners(lo, hi):
    (x0, y0, z0), (x1, y1, z1) = lo, hi
    return np.array([(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)], dtype=float)


QUADS = ((0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3))


def render(boxes, size=420, yaw=38, elevation=14, scale=8.5, centre=(0, 17, 0)):
    """boxes: [(8 corners in Bedrock player space, rgb, emissive)] -> RGBA image."""
    a, e = math.radians(yaw), math.radians(elevation)
    # Right-handed view (Bedrock X mirrored): camera in front of the player, to its right.
    to_camera = np.array([math.sin(a) * math.cos(e), math.sin(e), -math.cos(a) * math.cos(e)])
    view = -to_camera
    right = np.cross(view, [0, 1, 0])
    right /= np.linalg.norm(right)
    up = np.cross(right, view)
    light = np.array([.35, .8, -.5])
    light /= np.linalg.norm(light)
    canvas = np.zeros((size, size, 4), dtype=np.uint8)
    depth = np.full((size, size), -1e9)
    mirror = np.array([-1.0, 1.0, 1.0])
    for pts, rgb, emissive in boxes:
        pts = (pts - centre) * mirror
        sx = pts @ right * scale + size / 2
        sy = -(pts @ up) * scale + size * .52
        sz = pts @ to_camera
        for quad in QUADS:
            p = pts[list(quad)]
            normal = np.cross(p[1] - p[0], p[3] - p[0])
            length = np.linalg.norm(normal)
            if length < 1e-9:
                continue
            normal /= length
            centre_box = pts.mean(0)
            if np.dot(normal, p.mean(0) - centre_box) < 0:
                normal = -normal
            if np.dot(normal, to_camera) <= 0:
                continue
            shade = 1.0 if emissive else .55 + .45 * max(0.0, float(np.dot(normal, light)))
            colour = np.array([min(255, int(c * shade)) for c in rgb] + [255], dtype=np.uint8)
            xs, ys, zs = sx[list(quad)], sy[list(quad)], sz[list(quad)]
            x0, x1 = max(0, int(xs.min())), min(size - 1, int(xs.max()) + 1)
            y0, y1 = max(0, int(ys.min())), min(size - 1, int(ys.max()) + 1)
            if x1 < x0 or y1 < y0:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + .5, np.arange(y0, y1 + 1) + .5)
            inside = np.ones(gx.shape, dtype=bool)
            sign = np.sign((xs[1] - xs[0]) * (ys[2] - ys[0]) - (ys[1] - ys[0]) * (xs[2] - xs[0])) or 1
            for k in range(4):
                ax, ay, bx, by = xs[k], ys[k], xs[(k + 1) % 4], ys[(k + 1) % 4]
                inside &= sign * ((bx - ax) * (gy - ay) - (by - ay) * (gx - ax)) >= -1e-9
            z = zs.mean()
            region = depth[y0:y1 + 1, x0:x1 + 1]
            mask = inside & (z > region)
            region[mask] = z
            canvas[y0:y1 + 1, x0:x1 + 1][mask] = colour
    return Image.fromarray(canvas)


def scene(cubes, colours, emissive, mapping):
    boxes = []
    for origin, size, rgb, follows_arm in PLAYER:
        pts = corners(origin, np.add(origin, size))
        if follows_arm:
            pts = SHOULDER + (pts - SHOULDER) @ HOLDING.T
        boxes.append((pts, rgb, False))
    for cube in cubes:
        pts = np.array([mapping(p) for p in corners(cube['from'], cube['to'])])
        pts = SHOULDER + (pts - SHOULDER) @ HOLDING.T
        boxes.append((pts, colours[cube['mat']], cube['mat'] in emissive))
    return boxes


def font(size, bold=False):
    return ImageFont.truetype('C:/Windows/Fonts/' + ('segoeuib.ttf' if bold else 'segoeui.ttf'), size)


def comparison(entries, out):
    """entries: [(title, cubes, colours, emissive, grip_y, display)] -> before/after sheet."""
    cell = 360
    sheet = Image.new('RGBA', (40 + len(entries) * cell, 1010), (10, 15, 26, 255))
    d = ImageDraw.Draw(sheet)
    d.text((30, 18), 'BEDROCK THIRD-PERSON HOLD  ·  BEFORE / AFTER', font=font(30, True), fill='#f3dfb2')
    d.text((30, 60), 'Computed from the exported transforms on the vanilla player rig (arm raised 18°). Not a game screenshot.',
           font=font(16), fill='#93a4bc')
    for i, (title, cubes, colours, emissive, grip_y, display) in enumerate(entries):
        x = 30 + i * cell
        d.text((x + 10, 100), title.upper(), font=font(22, True), fill='#fff1d6')
        anim = wf.rig_animation(display, False)
        before = render(scene(cubes, colours, emissive, lambda p: old_trident_third(p, grip_y)), cell)
        after = render(scene(cubes, colours, emissive, lambda p: wf.bound_third_person(anim, wf.bedrock_point(p))), cell)
        sheet.alpha_composite(before, (x, 140))
        sheet.alpha_composite(after, (x, 140 + cell + 60))
        d.text((x + 10, 140 + cell + 8), 'BEFORE · trident frame', font=font(15), fill='#ff9a8a')
        d.text((x + 10, 140 + 2 * cell + 66), 'NOW · same hold as Java', font=font(15), fill='#8fe3a8')
    sheet.convert('RGB').save(out)
    return out
