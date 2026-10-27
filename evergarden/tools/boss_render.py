"""Offline renderer for the Evergarden boss blueprints (previews and pose checks).

Reads the exact .bbmodel geometry, atlas and keyframes. The camera is a true view:
looking at the native front (-Z), the model's +X (its anatomical right) appears on
the viewer's left, as it does in game. Faces with a null texture are not drawn and
texels with alpha below 50% are discarded, matching ModelEngine/Bedrock cutouts.
"""
import base64
import io
import math

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

from boss_forge import rot

FACE_QUADS = {
    # Vertex order follows Minecraft FaceInfo: (u0,v0), (u0,v1), (u1,v1), (u1,v0).
    'north': ((1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)),
    'south': ((0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)),
    'west': ((0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)),
    'east': ((1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)),
    'up': ((0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)),
    'down': ((0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)),
}
NORMALS = {'north': (0, 0, -1), 'south': (0, 0, 1), 'west': (-1, 0, 0), 'east': (1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}


class Rig:
    def __init__(self, model):
        self.model = model
        source = model['textures'][0]['source'].split(',', 1)[1]
        self.texture = np.asarray(Image.open(io.BytesIO(base64.b64decode(source))).convert('RGBA'))
        self.elements = {e['uuid']: e for e in model['elements']}
        self.clips = {c['name']: c for c in model['animations']}
        self.flip = -1 if model.get('meta', {}).get('evergarden_animation_position_x') == 'inverted' else 1
        self.groups = {}

        def visit(group, parent):
            self.groups[group['name']] = (group, parent)
            for child in group['children']:
                if isinstance(child, dict):
                    visit(child, group['name'])
        for group in model['outliner']:
            visit(group, None)
        self.animated = {}
        for clip in model['animations']:
            for animator in clip['animators'].values():
                self.animated.setdefault(clip['name'], {})[animator['name']] = animator

    def sample(self, clip, bone, time, channel):
        animator = self.animated.get(clip, {}).get(bone)
        if animator is None:
            return None
        frames = sorted((f for f in animator['keyframes'] if f['channel'] == channel), key=lambda f: f['time'])
        if not frames:
            return None
        length = self.clips[clip]['length']
        if self.clips[clip]['loop'] == 'loop' and length > 0:
            time = time % length
        def vec(f):
            p = f['data_points'][0]
            v = np.array([float(p['x']), float(p['y']), float(p['z'])])
            return v * np.array([-1, -1, 1]) if channel == 'rotation' else v * np.array([self.flip, 1, 1])
        if time <= frames[0]['time']:
            return vec(frames[0])
        for a, b in zip(frames, frames[1:]):
            if a['time'] <= time <= b['time']:
                u = (time - a['time']) / max(1e-9, b['time'] - a['time'])
                return vec(a) * (1 - u) + vec(b) * u
        return vec(frames[-1])

    def matrices(self, clip='idle', time=0.0, gaze=None, aura_time=None, aura='aura'):
        result = {}

        def walk(name, parent):
            group, _ = self.groups[name]
            o = np.array(group['origin'], float)
            r_angles = self.sample(clip, name, time, 'rotation')
            p = self.sample(clip, name, time, 'position')
            if r_angles is None and aura_time is not None and aura in self.clips:
                r_angles = self.sample(aura, name, aura_time, 'rotation')
                p = self.sample(aura, name, aura_time, 'position')
            r = rot(r_angles if r_angles is not None else [0, 0, 0])
            m = np.eye(4)
            m[:3, :3] = r
            m[:3, 3] = o - r @ o + (p if p is not None else 0)
            m = parent @ m
            if name == 'hi_gaze' and gaze is not None:
                pivot = (parent @ np.append(o, 1))[:3]
                look = np.eye(4)
                lr = rot([-gaze[1], -gaze[0], 0])
                look[:3, :3] = lr
                look[:3, 3] = pivot - lr @ pivot
                m = look @ m
            result[name] = m
            for child in group['children']:
                if isinstance(child, dict):
                    walk(child['name'], m)
        for group in self.model['outliner']:
            if group['name'] != 'hitbox':
                walk(group['name'], np.eye(4))
        return result

    def owners(self):
        owner = {}
        for name, (group, _) in self.groups.items():
            for child in group['children']:
                if isinstance(child, str):
                    owner[child] = name
        return owner

    def point(self, matrices, bone, offset=None):
        o = np.array(self.groups[bone][0]['origin'], float) if offset is None else np.asarray(offset, float)
        return (matrices[bone] @ np.append(o, 1))[:3]

    def cuboids(self, matrices):
        """Yield (element, 8x3 world vertices in from/to corner order, bone)."""
        owner = self.owners()
        for e in self.model['elements']:
            bone = owner.get(e['uuid'])
            if bone is None or bone == 'hitbox':
                continue
            lo, hi = np.array(e['from'], float), np.array(e['to'], float)
            r = rot(e.get('rotation', [0, 0, 0]))
            pivot = np.array(e.get('origin', [0, 0, 0]), float)
            corners = np.array([[hi[0] if x else lo[0], hi[1] if y else lo[1], hi[2] if z else lo[2]]
                                for x in (0, 1) for y in (0, 1) for z in (0, 1)])
            world = (corners - pivot) @ r.T + pivot
            m = matrices[bone]
            yield e, world @ m[:3, :3].T + m[:3, 3], bone


def render(rig, clip='idle', time=0.0, yaw=20.0, elevation=8.0, size=900, gaze=None, aura_time=None,
           span=None, center=None, background=(18, 15, 27), bloom=True, supersample=2, ground=True):
    """Orthographic true-view render. yaw=0 looks at the native front."""
    matrices = rig.matrices(clip, time, gaze, aura_time if aura_time is not None else time)
    big = size * supersample
    az, el = math.radians(yaw), math.radians(elevation)
    # Camera sits on the -Z side at yaw 0 and orbits counter-clockwise seen from above.
    eye = np.array([-math.sin(az) * math.cos(el), math.sin(el), -math.cos(az) * math.cos(el)])
    forward = -eye
    right = np.cross(forward, [0, 1, 0])
    right /= np.linalg.norm(right)
    up = np.cross(right, forward)
    cuboids = list(rig.cuboids(matrices))
    if span is None or center is None:
        pts = np.concatenate([w for _, w, _ in cuboids])
        sx, sy = pts @ right, pts @ up
        span = span or max(sx.max() - sx.min(), sy.max() - sy.min()) * 1.08
        center = center if center is not None else np.array([(sx.max() + sx.min()) / 2, (sy.max() + sy.min()) / 2])
    scale = big / span
    canvas = np.zeros((big, big, 3), float)
    canvas[:] = background
    glow = np.zeros((big, big, 3), float)
    depth = np.full((big, big), -1e9)
    light = np.array([-.45, .75, -.5])
    light /= np.linalg.norm(light)
    fill = np.array([.6, .2, .7])
    fill /= np.linalg.norm(fill)
    tex = rig.texture
    th, tw = tex.shape[:2]
    rw, rh = rig.model['resolution']['width'], rig.model['resolution']['height']
    if ground:
        g = np.zeros((big, big))
        gy = big / 2 + (center[1] - 0) * scale
        yy, xx = np.mgrid[0:big, 0:big]
        ell = ((xx - big / 2) / (big * .32)) ** 2 + ((yy - gy) / (big * .32 * max(.08, math.sin(el)))) ** 2
        g = np.clip(1 - ell, 0, 1) ** 1.5
        canvas = canvas * (1 - g[..., None] * .35)
    for e, world, bone in cuboids:
        lo = np.array(e['from'], float)
        hi = np.array(e['to'], float)
        r_el = rot(e.get('rotation', [0, 0, 0]))
        m = matrices[bone]
        for face, data in e['faces'].items():
            if data.get('texture') is None:
                continue
            n = m[:3, :3] @ (r_el @ np.array(NORMALS[face], float))
            if n @ eye <= 1e-6:
                continue
            quad = np.array([[hi[i] if c else lo[i] for i, c in enumerate(v)] for v in FACE_QUADS[face]])
            pivot = np.array(e.get('origin', [0, 0, 0]), float)
            quad = (quad - pivot) @ r_el.T + pivot
            quad = quad @ m[:3, :3].T + m[:3, 3]
            px = (quad @ right - center[0]) * scale + big / 2
            py = big / 2 - (quad @ up - center[1]) * scale
            pz = quad @ eye
            u0, v0, u1, v1 = data['uv']
            us = np.array([u0, u0, u1, u1]) * tw / rw
            vs = np.array([v0, v1, v1, v0]) * th / rh
            if e.get('shade', True):
                bright = .5 + .42 * max(0.0, float(n @ light)) + .16 * max(0.0, float(n @ fill)) + .08 * max(0.0, n[1])
            else:
                bright = 1.0
            for tri in ((0, 1, 2), (0, 2, 3)):
                _raster(canvas, glow, depth, px[list(tri)], py[list(tri)], pz[list(tri)], us[list(tri)], vs[list(tri)],
                        tex, bright, not e.get('shade', True))
    image = Image.fromarray(np.clip(canvas, 0, 255).astype(np.uint8))
    if bloom:
        g = Image.fromarray(np.clip(glow, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(big / 90))
        image = Image.fromarray(np.clip(np.asarray(image, float) + np.asarray(g, float) * .55, 0, 255).astype(np.uint8))
    if supersample > 1:
        image = image.resize((size, size), Image.Resampling.LANCZOS)
    return image, dict(span=span, center=center)


def _raster(canvas, glow, depth, xs, ys, zs, us, vs, tex, bright, emissive):
    h, w = depth.shape
    x0, x1 = int(max(0, math.floor(xs.min()))), int(min(w - 1, math.ceil(xs.max())))
    y0, y1 = int(max(0, math.floor(ys.min()))), int(min(h - 1, math.ceil(ys.max())))
    if x1 < x0 or y1 < y0:
        return
    det = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
    if abs(det) < 1e-9:
        return
    gy, gx = np.mgrid[y0:y1 + 1, x0:x1 + 1] + .5
    a = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / det
    b = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / det
    c = 1 - a - b
    inside = (a >= -1e-4) & (b >= -1e-4) & (c >= -1e-4)
    if not inside.any():
        return
    z = a * zs[0] + b * zs[1] + c * zs[2]
    u = a * us[0] + b * us[1] + c * us[2]
    v = a * vs[0] + b * vs[1] + c * vs[2]
    th, tw = tex.shape[:2]
    tx = np.clip(np.floor(u).astype(int), 0, tw - 1)
    ty = np.clip(np.floor(v).astype(int), 0, th - 1)
    texel = tex[ty, tx]
    region = depth[y0:y1 + 1, x0:x1 + 1]
    mask = inside & (texel[..., 3] >= 128) & (z > region)
    if not mask.any():
        return
    color = texel[..., :3].astype(float) * bright
    canvas[y0:y1 + 1, x0:x1 + 1][mask] = color[mask]
    if emissive:
        glow[y0:y1 + 1, x0:x1 + 1][mask] = texel[..., :3][mask]
    else:
        glow[y0:y1 + 1, x0:x1 + 1][mask] = 0
    region[mask] = z[mask]


def label(image, text, sub=None, color=(230, 214, 255)):
    from PIL import ImageFont
    draw = ImageDraw.Draw(image)
    try:
        font = ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf', max(16, image.width // 34))
        small = ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', max(12, image.width // 52))
    except OSError:
        font = small = None
    draw.text((image.width * .03, image.height * .025), text, font=font, fill=color)
    if sub:
        draw.text((image.width * .03, image.height * .025 + image.width / 26), sub, font=small, fill=tuple(int(c * .75) for c in color))
    return image
