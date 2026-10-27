"""Offline preview of the Meteor Rain / Tempest Dominion spectacle from a real Paper capture.

Reads the capture written by advance-magic/tests/LegendarySpectacleChecks (every
ItemDisplay pose and every particle packet the server sent a viewer, per tick),
draws the display models with the shipped Java pack textures and block props as
shaded cubes, and approximates vanilla particles from the actual packets. It is a
software preview for choreography review, not Minecraft footage.

    python tools/preview_legendary_spectacle.py <capture.json> <out-prefix> --element <fire|wind|water|ice|earth|dark|lightning|light>
"""
import argparse
import io
import json
import math
import random
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'advance-magic/dist/advance-magic-java.zip'
W, H = 720, 480
BLOCKS = {'magma_block': (226, 92, 30), 'blackstone': (52, 44, 52), 'netherrack': (128, 42, 40),
          'light_blue_stained_glass': (110, 190, 255), 'prismarine': (90, 170, 150), 'dark_prismarine': (40, 95, 85),
          'sea_lantern': (225, 245, 235), 'prismarine_bricks': (95, 165, 150), 'packed_ice': (150, 190, 240), 'blue_ice': (110, 160, 245), 'ice': (175, 215, 255),
          'stone': (125, 125, 125), 'cobblestone': (108, 108, 108), 'deepslate': (72, 72, 80), 'deepslate_bricks': (64, 64, 72),
          'chiseled_deepslate': (58, 58, 66), 'granite': (150, 105, 90), 'andesite': (135, 135, 135), 'tuff': (102, 106, 96),
          'gold_block': (245, 205, 60), 'crying_obsidian': (70, 25, 110), 'obsidian': (28, 18, 44), 'barrel': (140, 100, 60),
          'dirt': (134, 96, 67), 'coarse_dirt': (119, 85, 59), 'gravel': (131, 127, 126), 'grass_block': (95, 159, 53),
          'oak_log': (109, 85, 50), 'oak_leaves': (60, 120, 40), 'moss_block': (89, 109, 45), 'sand': (219, 207, 163),
          'oak_planks': (162, 130, 78), 'amethyst_block': (133, 97, 191)}


class Pack:
    def __init__(self):
        self.zip = zipfile.ZipFile(PACK)
        self.cache = {}

    def model(self, key):
        if key in self.cache:
            return self.cache[key]
        try:
            item = json.loads(self.zip.read(f'assets/advance_magic/items/{key}.json'))
            path = item['model']['model'].split(':')[1]
            model = json.loads(self.zip.read(f'assets/advance_magic/models/{path}.json'))
            textures = {}
            for name, ref in model['textures'].items():
                png = self.zip.read(f"assets/advance_magic/textures/{ref.split(':')[1]}.png")
                image = Image.open(io.BytesIO(png)).convert('RGBA')
                image.thumbnail((256, 256))
                textures['#' + name] = np.asarray(image).astype(np.float32) / 255
            value = (model['elements'], textures)
        except KeyError:
            value = None
        self.cache[key] = value
        return value


def quat_matrix(q):
    x, y, z, w = q
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


class Camera:
    def __init__(self, eye, target, fov=68):
        self.eye = np.array(eye, float)
        forward = np.array(target, float) - self.eye
        self.f = forward / np.linalg.norm(forward)
        self.r = np.cross(self.f, [0, 1, 0]); self.r /= np.linalg.norm(self.r)
        self.u = np.cross(self.r, self.f)
        self.scale = (W / 2) / math.tan(math.radians(fov) / 2)

    def project(self, p):
        d = np.asarray(p, float) - self.eye
        z = d @ self.f
        if z < .3:
            return None
        return np.array([W / 2 + (d @ self.r) / z * self.scale, H / 2 - (d @ self.u) / z * self.scale, z])


FACES = {  # corners as (x, y, z) unit-cube choices, and texture order top-left, top-right, bottom-right, bottom-left
    'north': [(1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)], 'south': [(0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)],
    'east': [(1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)], 'west': [(0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)],
    'up': [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)], 'down': [(0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)]}


def homography(src, dst):
    a = []
    for (x, y), (u, v) in zip(src, dst):
        a.append([x, y, 1, 0, 0, 0, -u * x, -u * y, -u]); a.append([0, 0, 0, x, y, 1, -v * x, -v * y, -v])
    _, _, vt = np.linalg.svd(np.array(a))
    return vt[-1].reshape(3, 3)


def draw_quad(canvas, screen, texture, uv):
    """Composite a textured quad (screen corners TL, TR, BR, BL) over the float RGB canvas."""
    xs, ys = screen[:, 0], screen[:, 1]
    x0, x1 = int(max(0, xs.min())), int(min(W, xs.max() + 1)); y0, y1 = int(max(0, ys.min())), int(min(H, ys.max() + 1))
    if x1 <= x0 or y1 <= y0 or (x1 - x0) * (y1 - y0) > W * H * 4:
        return
    th, tw = texture.shape[:2]
    u0, v0, u1, v1 = [c / 16 for c in uv]
    corners = [(u0 * tw, v0 * th), (u1 * tw, v0 * th), (u1 * tw, v1 * th), (u0 * tw, v1 * th)]
    try:
        hmat = homography([tuple(p) for p in screen[:, :2]], corners)
    except np.linalg.LinAlgError:
        return
    gx, gy = np.meshgrid(np.arange(x0, x1) + .5, np.arange(y0, y1) + .5)
    den = hmat[2, 0] * gx + hmat[2, 1] * gy + hmat[2, 2]
    with np.errstate(divide='ignore', invalid='ignore'):
        tu = (hmat[0, 0] * gx + hmat[0, 1] * gy + hmat[0, 2]) / den
        tv = (hmat[1, 0] * gx + hmat[1, 1] * gy + hmat[1, 2]) / den
    lo_u, hi_u = min(c[0] for c in corners), max(c[0] for c in corners)
    lo_v, hi_v = min(c[1] for c in corners), max(c[1] for c in corners)
    inside = (tu >= lo_u) & (tu < hi_u) & (tv >= lo_v) & (tv < hi_v)
    if not inside.any():
        return
    ti = np.clip(tu, 0, tw - 1).astype(int); tj = np.clip(tv, 0, th - 1).astype(int)
    texel = texture[tj, ti]
    alpha = texel[..., 3] * inside
    region = canvas[y0:y1, x0:x1]
    region[:] = region * (1 - alpha[..., None]) + texel[..., :3] * alpha[..., None]


def flat_quad(canvas, screen, color):
    image = Image.new('L', (W, H), 0)
    ImageDraw.Draw(image).polygon([tuple(p[:2]) for p in screen], fill=255)
    mask = np.asarray(image).astype(np.float32)[..., None] / 255
    canvas[:] = canvas * (1 - mask) + np.array(color, np.float32) / 255 * mask


# Particle approximations: life (ticks), gravity, drag, size (blocks), colour, blend.
LOOK = {
    'flame': (16, -.002, .96, .28, (255, 150, 40), 'add'), 'small_flame': (12, -.002, .96, .17, (255, 170, 60), 'add'),
    'soul_fire_flame': (16, 0, .96, .28, (90, 230, 255), 'add'), 'lava': (26, .05, .98, .22, (255, 190, 60), 'add'),
    'falling_lava': (32, .03, .98, .13, (255, 120, 30), 'add'), 'large_smoke': (34, -.02, .96, 1.0, (55, 52, 56), 'alpha'),
    'smoke': (20, -.015, .96, .45, (70, 68, 72), 'alpha'), 'campfire_signal_smoke': (120, -.07, .995, 1.4, (170, 168, 170), 'alpha'),
    'campfire_cosy_smoke': (80, -.04, .99, 1.1, (120, 116, 118), 'alpha'), 'white_ash': (40, .006, .98, .09, (230, 230, 230), 'alpha'),
    'dust_color_transition': (20, 0, .96, .32, (255, 120, 40), 'add'), 'dust': (20, 0, .96, .28, (120, 255, 190), 'add'),
    'explosion': (9, 0, 1, 2.6, (230, 220, 210), 'alpha'), 'flash': (3, 0, 1, 7, (255, 230, 170), 'add'),
    'block': (24, .06, .98, .16, (120, 40, 25), 'alpha'), 'cloud': (20, -.005, .9, .55, (240, 248, 245), 'alpha'),
    'white_smoke': (20, -.01, .95, .4, (235, 240, 240), 'alpha'), 'poof': (10, 0, .9, .5, (235, 235, 235), 'alpha'),
    'sweep_attack': (6, 0, 1, 1.6, (255, 255, 255), 'arc'), 'gust': (10, 0, 1, 2.2, (230, 255, 245), 'ring'),
    'gust_emitter_small': (12, 0, 1, 3, (230, 255, 245), 'ring'), 'gust_emitter_large': (14, 0, 1, 5, (230, 255, 245), 'ring'),
    'small_gust': (8, 0, 1, 1.1, (230, 255, 245), 'ring'), 'electric_spark': (8, 0, .9, .12, (170, 255, 255), 'add'),
    'cherry_leaves': (60, .015, .97, .14, (255, 170, 205), 'alpha'), 'dust_plume': (12, -.01, .95, .35, (150, 140, 130), 'alpha'),
    'explosion_emitter': (12, 0, 1, 3.2, (235, 215, 200), 'alpha'), 'end_rod': (20, 0, .96, .16, (255, 255, 230), 'add'),
    'splash': (14, .04, .96, .14, (170, 215, 255), 'alpha'), 'falling_water': (30, .04, .98, .1, (90, 150, 255), 'alpha'),
    'dripping_water': (24, .03, .98, .1, (90, 150, 255), 'alpha'), 'bubble_pop': (8, 0, .9, .12, (210, 240, 255), 'alpha'),
    'glow': (24, 0, .96, .14, (120, 255, 230), 'add'), 'snowflake': (30, .004, .97, .14, (245, 250, 255), 'alpha'),
    'item_snowball': (16, .03, .97, .14, (250, 250, 255), 'alpha'), 'dust_pillar': (16, -.03, .95, .3, (130, 100, 70), 'alpha'),
    'falling_dust': (30, .02, .98, .1, (110, 100, 95), 'alpha'), 'wax_on': (14, 0, .95, .14, (255, 200, 80), 'add'),
    'portal': (30, 0, .9, .14, (170, 80, 255), 'add'), 'reverse_portal': (24, 0, .95, .16, (190, 90, 255), 'add'),
    'squid_ink': (20, 0, .92, .35, (12, 8, 18), 'alpha'), 'soul': (24, -.02, .96, .25, (120, 210, 230), 'add'),
    'sonic_boom': (10, 0, 1, 2.8, (100, 220, 230), 'ring'),
    'enchant': (20, 0, .9, .1, (230, 220, 255), 'add'), 'firework': (20, -.01, .96, .12, (255, 250, 230), 'add'),
}
DUST = {'fire': 'dust_color_transition', 'wind': (120, 255, 190), 'water': (90, 160, 255), 'ice': (200, 235, 255),
        'earth': (246, 211, 139), 'dark': (163, 107, 255), 'lightning': (190, 230, 255), 'light': (255, 211, 77)}


class Particles:
    def __init__(self, element):
        self.items = []
        self.element = element
        self.rng = random.Random(7)

    def spawn(self, packet):
        kind, x, y, z, count, dx, dy, dz, speed = packet
        kind = kind.split(':')[1]
        if kind not in LOOK:
            return
        if kind == 'dust' and self.element == 'fire':
            kind = 'dust_color_transition'
        g = self.rng.gauss
        if count == 0:
            self.items.append([kind, x, y, z, dx * speed, dy * speed, dz * speed, 0])
            return
        if kind == 'explosion_emitter':
            for _ in range(10 * count):
                self.items.append(['explosion', x + g(0, 2), y + g(0, 1.2), z + g(0, 2), 0, 0, 0, -self.rng.randint(0, 6)])
            return
        for _ in range(min(count, 200)):
            vx, vy, vz = (g(0, 1) * speed, g(0, 1) * speed, g(0, 1) * speed)
            if kind == 'lava':
                vy = abs(vy) + .22
            self.items.append([kind, x + g(0, 1) * dx, y + g(0, 1) * dy, z + g(0, 1) * dz, vx, vy, vz, 0])

    def step(self):
        alive = []
        for p in self.items:
            life, gravity, drag = LOOK[p[0]][:3]
            p[7] += 1
            if p[7] > life:
                continue
            if p[7] > 0:
                p[1] += p[4]; p[2] += p[5]; p[3] += p[6]
                p[4] *= drag; p[6] *= drag; p[5] = p[5] * drag - gravity
            alive.append(p)
        self.items = alive

    def draw(self, canvas, camera):
        glow = np.zeros((H, W, 3), np.float32)
        smoke = Image.new('RGBA', (W, H), (0, 0, 0, 0)); sd = ImageDraw.Draw(smoke)
        glow_img = Image.new('RGB', (W, H), (0, 0, 0)); gd = ImageDraw.Draw(glow_img)
        lines = Image.new('RGBA', (W, H), (0, 0, 0, 0)); ld = ImageDraw.Draw(lines)
        projected = []
        for p in self.items:
            if p[7] < 0:
                continue
            s = camera.project(p[1:4])
            if s is not None:
                projected.append((s[2], p, s))
        projected.sort(key=lambda e: -e[0])
        for depth, p, s in projected:
            life, _, _, size, color, blend = LOOK[p[0]]
            if p[0] == 'dust' and self.element in DUST:
                color = DUST[self.element]
            t = p[7] / life
            radius = max(.6, size * camera.scale / depth * (1 + (t if blend == 'alpha' else 0) * .8))
            x, y = s[0], s[1]
            if blend == 'add':
                fade = 1 - t * .7
                c = tuple(int(v * fade) for v in color)
                if p[0] == 'dust_color_transition':
                    c = tuple(int((a * (1 - t) + b * t) * fade) for a, b in zip((255, 180, 60), (217, 35, 15)))
                gd.ellipse([x - radius, y - radius, x + radius, y + radius], fill=c)
            elif blend == 'alpha':
                a = int(170 * (1 - t))
                sd.ellipse([x - radius, y - radius, x + radius, y + radius], fill=color + (a,))
            elif blend == 'arc':
                ld.arc([x - radius, y - radius * .5, x + radius, y + radius * .5], 200, 340, fill=color + (230,), width=max(1, int(radius * .18)))
            else:
                ld.ellipse([x - radius, y - radius * .6, x + radius, y + radius * .6], outline=color + (int(200 * (1 - t)),), width=max(1, int(radius * .12)))
        smoke = smoke.filter(ImageFilter.GaussianBlur(2))
        sa = np.asarray(smoke).astype(np.float32) / 255
        canvas[:] = canvas * (1 - sa[..., 3:]) + sa[..., :3] * sa[..., 3:]
        g = np.asarray(glow_img.filter(ImageFilter.GaussianBlur(1.2))).astype(np.float32) / 255
        bloom = np.asarray(glow_img.filter(ImageFilter.GaussianBlur(6))).astype(np.float32) / 255
        canvas[:] = np.clip(canvas + g + bloom * .8, 0, 1)
        la = np.asarray(lines).astype(np.float32) / 255
        canvas[:] = canvas * (1 - la[..., 3:]) + la[..., :3] * la[..., 3:]


VIEWS = {  # eye offset and look-at offset per element (two views each)
    'fire': [([-25, 9, -21], [0, 10.5, 0]), ([3, 3.2, -27], [0, 9, 0])],
    'wind': [([-25, 9, -21], [0, 9, 0]), ([3, 3.2, -27], [0, 9, 0])],
    'water': [([-20, 8, 0], [0, 3, 10]), ([13, 4.5, 8], [0, 3.5, 14])],
    'ice': [([-15, 8, -13], [0, 3, 0]), ([2, 3, -17], [0, 3, 0])],
    'earth': [([-17, 9, -15], [0, 6, 0]), ([2, 3, -19], [0, 6, 0])],
    'dark': [([-15, 8, -13], [0, 4, 0]), ([2, 3, -17], [0, 4, 0])],
    'lightning': [([-19, 8, -16], [0, 7, 0]), ([2, 3, -21], [0, 8, 0])],
    'light': [([-12, 6, -9], [5, 1.5, 15]), ([12, 5, -4], [3, 1.5, 14])],
}


def render(capture, out, element, every=2):
    fire = element == 'fire'
    data = json.loads(Path(capture).read_text())
    stage = np.array(data['stage'], float)
    pack = Pack()
    cameras = [Camera(stage + eye, stage + look) for eye, look in VIEWS[element]]
    sky = np.zeros((H, W, 3), np.float32)
    for j in range(H):
        t = j / H
        sky[j] = np.array([.07 + .1 * t, .06 + .09 * t, .12 + .1 * t]) if fire else np.array([.10 + .14 * t, .13 + .16 * t, .17 + .16 * t])
    systems = [Particles(element) for _ in cameras]
    frames = [[] for _ in cameras]
    for index, frame in enumerate(data['frames']):
        for system in systems:
            system.step()
            for packet in frame['particles']:
                system.spawn(packet)
        if index % every:
            continue
        for ci, (camera, system) in enumerate(zip(cameras, systems)):
            canvas = sky.copy()
            ground = [stage + [x, -.02, z] for x, z in ((-30, 30), (30, 30), (30, -30), (-30, -30))]
            proj = [camera.project(p) for p in ground]
            if all(p is not None for p in proj):
                flat_quad(canvas, np.array(proj), (58, 62, 60) if fire else (70, 84, 74))
            quads = []
            for display in frame['displays']:
                pos, scale, rot = np.array(display['pos']), np.array(display['scale']), quat_matrix(display['rot'])
                key = display['model']
                if key.startswith('carrier:'):
                    continue  # the Bedrock body of a prop; the Java display is drawn instead
                if key.startswith('block:'):
                    base = BLOCKS.get(key[6:], (150, 150, 150))
                    for face, shade in (('up', 1.15), ('north', .8), ('south', .8), ('east', .95), ('west', .95), ('down', .6)):
                        pts = [pos + rot @ ((np.array(c) - .5) * scale) for c in FACES[face]]
                        normal = rot @ np.array({'up': (0, 1, 0), 'down': (0, -1, 0), 'north': (0, 0, -1), 'south': (0, 0, 1), 'east': (1, 0, 0), 'west': (-1, 0, 0)}[face])
                        if normal @ (camera.eye - np.mean(pts, axis=0)) <= 0:
                            continue
                        screen = [camera.project(p) for p in pts]
                        if any(s is None for s in screen):
                            continue
                        quads.append((np.mean([s[2] for s in screen]), 'flat', np.array(screen), tuple(min(255, int(c * shade)) for c in base)))
                    continue
                model = pack.model(key)
                if model is None:
                    continue
                elements, textures = model
                for element in elements:
                    lo, hi = np.array(element['from']) / 16 - .5, np.array(element['to']) / 16 - .5
                    for face, spec in element.get('faces', {}).items():
                        texture = textures.get(spec.get('texture'))
                        if texture is None:
                            continue
                        corners = [lo + (hi - lo) * np.array(c) for c in FACES[face]]
                        pts = [pos + rot @ (c * scale) for c in corners]
                        screen = [camera.project(p) for p in pts]
                        if any(s is None for s in screen):
                            continue
                        quads.append((np.mean([s[2] for s in screen]), 'tex', np.array(screen), (texture, spec.get('uv', [0, 0, 16, 16]))))
            quads.sort(key=lambda q: -q[0])
            for _, kind, screen, payload in quads:
                if kind == 'flat':
                    flat_quad(canvas, screen, payload)
                else:
                    draw_quad(canvas, screen, *payload)
            system.draw(canvas, camera)
            image = Image.fromarray((np.clip(canvas, 0, 1) * 255).astype(np.uint8))
            ImageDraw.Draw(image).text((10, 8), f"tick {frame['tick']}", fill=(220, 220, 220))
            frames[ci].append(image)
    for ci, images in enumerate(frames):
        images[0].save(f'{out}-view{ci + 1}.gif', save_all=True, append_images=images[1:], duration=100, loop=0)
    return frames


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('capture')
    parser.add_argument('out')
    parser.add_argument('--element', choices=sorted(VIEWS), default='fire')
    args = parser.parse_args()
    frames = render(args.capture, args.out, args.element)
    print(f'{len(frames[0])} frames per view -> {args.out}-view1.gif, {args.out}-view2.gif')


if __name__ == '__main__':
    main()
