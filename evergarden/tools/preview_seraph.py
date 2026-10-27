"""Texture-accurate pack review with depth testing and alpha cut-out. Requires Pillow and NumPy.

Faces follow Minecraft's FaceInfo corner order, so mirrored UVs, X/Y/Z element rotations
and painted transparent planes (wings, fringe, halo) look as they do in game.
"""
import io
import json
import math
from pathlib import Path
import zipfile
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
PACK = ROOT / 'evergarden/dist/judge-java.zip'
OUT = ROOT / 'previews/seraph-model-preview.png'
# Corners receiving (u0,v0), (u0,v1), (u1,v1), (u1,v0), then the outward normal.
FACE = {
    'north': ((1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0), (0, 0, -1)),
    'south': ((0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 0, 1)),
    'west': ((0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1), (-1, 0, 0)),
    'east': ((1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0), (1, 0, 0)),
    'up': ((0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0), (0, 1, 0)),
    'down': ((0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1), (0, -1, 0)),
}


def rotation(axis, angle):
    c, s = math.cos(math.radians(angle)), math.sin(math.radians(angle))
    return {'x': np.array([[1, 0, 0], [0, c, -s], [0, s, c]]),
            'y': np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]]),
            'z': np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])}[axis]


def render(pose, output, yaw=20, elevation=-12, size=1100):
    with zipfile.ZipFile(PACK) as archive:
        model = json.loads(archive.read(f'assets/voidscape/models/item/judge_seraph_{pose}.json'))
        texture = np.array(Image.open(io.BytesIO(archive.read('assets/voidscape/textures/item/judge_seraph.png'))).convert('RGBA'))
    az, el = math.radians(yaw), math.radians(elevation)
    # The camera stands in front (-Z) at yaw 0, with a right-handed screen.
    towards = np.array([math.sin(az) * math.cos(el), math.sin(el), -math.cos(az) * math.cos(el)])
    right = np.cross(-towards, [0, 1, 0])
    right /= np.linalg.norm(right)
    up = np.cross(right, -towards)
    lights = [np.array(v) / np.linalg.norm(v) for v in ((.2, 1, -.7), (-.2, 1, .7))]
    quads = []
    for element in model['elements']:
        lo, hi = np.array(element['from'], float), np.array(element['to'], float)
        turn, pivot = np.eye(3), np.zeros(3)
        if 'rotation' in element:
            spec = element['rotation']
            turn, pivot = rotation(spec['axis'], spec['angle']), np.array(spec['origin'], float)
        for face, data in element['faces'].items():
            *corners, normal = FACE[face]
            points = np.array([[(lo, hi)[c][i] for i, c in enumerate(corner)] for corner in corners])
            quads.append(((points - pivot) @ turn.T + pivot, turn @ np.array(normal, float), data['uv'],
                          element.get('shade', True)))
    project = lambda p: np.stack([p @ right, -(p @ up), p @ towards], axis=-1)
    projected = project(np.concatenate([q[0] for q in quads]))
    low, high = projected[:, :2].min(axis=0), projected[:, :2].max(axis=0)
    scale = min((size - 125) / (high[0] - low[0]), (size - 135) / (high[1] - low[1]))
    origin = np.array([size / 2, size / 2 - 15]) - ((low + high) / 2) * scale
    canvas = np.zeros((size, size, 3), dtype=np.float32)
    yy, xx = np.mgrid[0:size, 0:size]
    vignette = np.clip(1 - np.sqrt(((xx - size / 2) / size) ** 2 + ((yy - size / 2) / size) ** 2), 0, 1)
    canvas[:, :, :] = np.array([25, 29, 36]) + vignette[:, :, None] * np.array([10, 11, 14])
    depth = np.full((size, size), -1e8, dtype=np.float32)
    height, width = texture.shape[:2]
    for points, normal, uv, lit in quads:
        if normal @ towards <= 1e-6:
            continue
        q = project(points)
        a = q[0, :2] * scale + origin
        down = (q[1, :2] - q[0, :2]) * scale
        across = (q[3, :2] - q[0, :2]) * scale
        screen = q[:, :2] * scale + origin
        left, top = np.maximum(0, np.floor(screen.min(axis=0)).astype(int))
        right_edge, bottom = np.minimum(size, np.ceil(screen.max(axis=0)).astype(int) + 1)
        determinant = across[0] * down[1] - across[1] * down[0]
        if right_edge <= left or bottom <= top or abs(determinant) < 1e-6:
            continue
        px = np.arange(left, right_edge)[None, :] + .5 - a[0]
        py = np.arange(top, bottom)[:, None] + .5 - a[1]
        s = (px * down[1] - py * down[0]) / determinant
        t = (py * across[0] - px * across[1]) / determinant
        z = q[0, 2] + s * (q[3, 2] - q[0, 2]) + t * (q[1, 2] - q[0, 2])
        target_depth = depth[top:bottom, left:right_edge]
        mask = (s >= 0) & (s <= 1) & (t >= 0) & (t <= 1) & (z > target_depth)
        tx = np.clip(((uv[0] + s * (uv[2] - uv[0])) * width / 16).astype(int), 0, width - 1)
        ty = np.clip(((uv[1] + t * (uv[3] - uv[1])) * height / 16).astype(int), 0, height - 1)
        texels = texture[ty, tx]
        mask &= texels[:, :, 3] > 25
        # Minecraft's two-light entity diffuse, unless the element opts out of shading.
        shade = min(1., .4 + .6 * sum(max(0., float(normal @ l)) for l in lights)) if lit else 1.
        target = canvas[top:bottom, left:right_edge]
        target[mask] = texels[:, :, :3][mask] * shade
        target_depth[mask] = z[mask]
    output.parent.mkdir(exist_ok=True)
    Image.fromarray(np.clip(canvas, 0, 255).astype(np.uint8)).save(output)
    print(output)


if __name__ == '__main__':
    render('idle', OUT)
    render('cast', OUT.with_name('seraph-model-casting.png'))
