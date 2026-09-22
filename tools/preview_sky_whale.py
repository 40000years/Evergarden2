#!/usr/bin/env python3
"""Render the actual SkyWhale blueprint with a deterministic orthographic voxel renderer.

No geometry is invented by this renderer. Vanilla textures are read from a locally
installed Minecraft client JAR; no Minecraft texture files are copied into this repo.
Lighting, shadows, sky and fog are presentation effects, not an in-game screenshot.

After building the module and exporting its blueprint:
  java -cp 'evergarden/target/classes:<dependencies>' tools/ExportSkyWhale.java > /tmp/sky-whale.csv
  uv run --with pillow --with numpy tools/preview_sky_whale.py /tmp/sky-whale.csv

Use --minecraft-jar PATH when Minecraft is installed outside the standard locations.
"""
from __future__ import annotations
import argparse
import csv
import hashlib
import io
import json
import math
from collections import Counter
from pathlib import Path
import time
import zipfile

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

PALETTE = {
    'BONE_BLOCK': (217, 211, 184), 'CALCITE': (220, 221, 214),
    'SMOOTH_QUARTZ': (225, 222, 210), 'QUARTZ_BLOCK': (225, 222, 210),
    'MOSS_BLOCK': (92, 123, 51), 'MOSSY_COBBLESTONE': (100, 117, 82),
    'DEEPSLATE': (64, 66, 77), 'TUFF': (106, 111, 103),
    'STONE': (127, 126, 123), 'ANDESITE': (136, 134, 128),
    'CHERRY_LEAVES': (223, 156, 182), 'CHERRY_LOG': (70, 46, 49),
    'FLOWERING_AZALEA_LEAVES': (104, 140, 61), 'AZALEA_LEAVES': (101, 139, 50),
    'SEA_LANTERN': (192, 223, 206), 'GLOWSTONE': (199, 142, 69),
    'WATER': (91, 165, 213), 'CHAIN': (58, 60, 68), 'LANTERN': (234, 162, 75),
    'DARK_OAK_PLANKS': (60, 41, 23), 'SPRUCE_PLANKS': (113, 83, 49),
    'OAK_PLANKS': (166, 132, 77), 'DIRT': (129, 94, 65),
    'GRASS_BLOCK': (96, 138, 57), 'ROOTED_DIRT': (144, 103, 77),
    'AMETHYST_BLOCK': (152, 112, 182), 'PRISMARINE': (86, 151, 143),
    'DARK_PRISMARINE': (57, 103, 90), 'VERDANT_FROGLIGHT': (207, 224, 177),
    'SHROOMLIGHT': (237, 161, 89), 'COPPER_BLOCK': (179, 102, 75),
}
TINTED_LEAVES = {'OAK_LEAVES', 'SPRUCE_LEAVES', 'BIRCH_LEAVES', 'VINE'}
EMISSION = {'SEA_LANTERN', 'LANTERN', 'SOUL_LANTERN', 'GLOWSTONE', 'SHROOMLIGHT',
            'VERDANT_FROGLIGHT', 'OCHRE_FROGLIGHT', 'PEARLESCENT_FROGLIGHT', 'END_ROD'}
NOT_SOLID = {'WATER', 'CHAIN', 'IRON_CHAIN', 'LANTERN', 'SOUL_LANTERN', 'VINE', 'CAVE_VINES',
             'CAVE_VINES_PLANT', 'GLOW_LICHEN', 'END_ROD', 'SHORT_GRASS', 'FERN',
             'TALL_GRASS', 'PINK_PETALS', 'SPORE_BLOSSOM', 'MOSS_CARPET', 'HANGING_ROOTS'}


def find_client() -> Path | None:
    bases = [Path.home() / 'Library/Application Support/minecraft/versions',
             Path.home() / '.minecraft/versions']
    for base in bases:
        for jar in sorted(base.glob('*/*.jar'), reverse=True):
            try:
                with zipfile.ZipFile(jar) as z:
                    if 'assets/minecraft/textures/block/bone_block_side.png' in z.namelist():
                        return jar
            except zipfile.BadZipFile:
                pass
    return None


class Textures:
    def __init__(self, jar: Path | None):
        self.jar = zipfile.ZipFile(jar) if jar else None
        self.cache = {}
        self.fallback = set()

    def get(self, material: str, axis: int):
        key = (material, axis)
        if key in self.cache:
            return self.cache[key]
        name = material.lower()
        if name == 'bone_block': name += '_top' if axis == 1 else '_side'
        elif name in ('quartz_block', 'smooth_quartz'): name = 'quartz_block_top' if axis == 1 else 'quartz_block_side'
        elif name.endswith('_log') or name.endswith('_wood'):
            name = name.replace('_wood', '_log') + ('_top' if axis == 1 else '')
        elif name == 'grass_block': name = 'grass_block_top' if axis == 1 else 'grass_block_side'
        elif name == 'water': name = 'water_still'
        elif name == 'flowering_azalea': name = 'flowering_azalea_top' if axis == 1 else 'flowering_azalea_side'
        elif name == 'azalea': name = 'azalea_top' if axis == 1 else 'azalea_side'
        elif name in ('chain','iron_chain'): name = 'chain'
        elif name == 'moss_carpet': name = 'moss_block'
        elif name.endswith('_froglight'): name += '_top' if axis == 1 else '_side'
        elif name == 'bookshelf' and axis == 1: name = 'oak_planks'
        elif name.endswith('_planks'): pass
        elif name.endswith('_fence'): name = name[:-6] + '_planks'
        elif name.endswith('_slab'): name = name[:-5]
        elif name.endswith('_stairs'): name = name[:-7]
        if name == 'smooth_quartz': name = 'quartz_block_top' if axis == 1 else 'quartz_block_side'
        if name in ('spruce','oak','dark_oak','cherry'): name += '_planks'
        if name in ('stone_brick', 'mossy_stone_brick', 'deepslate_brick', 'polished_blackstone_brick'):
            name += 's'
        try:
            raw = self.jar.read(f'assets/minecraft/textures/block/{name}.png')
            img = Image.open(io.BytesIO(raw)).convert('RGBA')
            img = img.crop((0, 0, img.width, img.width))
            if material in ('LANTERN','SOUL_LANTERN'):
                img = img.crop((0,9,6,15) if axis == 1 else (0,2,6,9))
            if material in ('CHAIN','IRON_CHAIN'):
                img = img.crop((6,0,10,16))
            img = img.resize((16, 16), Image.Resampling.NEAREST)
            tex = np.asarray(img).astype(np.float32)
            if material in TINTED_LEAVES or (material == 'GRASS_BLOCK' and axis == 1):
                tex[:, :, :3] *= np.array([0.49, 0.72, 0.29])
            if material == 'WATER':
                tex[:, :, :3] *= np.array([0.34, 0.63, 0.92])
                tex[:, :, :3] = tex[:, :, :3] * 0.64 + np.array([89, 160, 201]) * 0.36
                tex[:, :, 3] = 255  # Surface colour, not a translucent volume simulation.
            # Leaf holes expose the leaf interior in the game; softly shade these areas.
            if 'LEAVES' in material:
                holes = tex[:, :, 3] < 128
                mean = tex[:, :, :3][~holes].mean(axis=0)
                tex[holes, :3] = mean * 0.63
                tex[:, :, 3] = 255
        except (KeyError, AttributeError):
            self.fallback.add(material)
            base = PALETTE.get(material, (141, 136, 123))
            seed = int.from_bytes(hashlib.sha256(material.encode()).digest()[:4], 'big')
            rng = np.random.default_rng(seed)
            noise = rng.uniform(0.93, 1.05, (16, 16, 1))
            tex = np.zeros((16, 16, 4), np.float32)
            tex[:, :, :3] = np.array(base) * noise
            tex[:, :, 3] = 255
        self.cache[key] = tex
        return tex


# Each normal has a pair of texture tangent axes; faces are rectangular cuboids.
FACE_AXES = ((0, 1, 2), (1, 0, 2), (2, 0, 1))


def cuboids(material: str, position, blocks):
    if material in ('CHAIN', 'IRON_CHAIN', 'END_ROD'):
        return [(.43, 0., .43, .57, 1., .57)]
    if material in ('LANTERN', 'SOUL_LANTERN'):
        return [(5/16, 1/16, 5/16, 11/16, .5, 11/16), (.375, .5, .375, .625, .625, .625), (.47, .625, .47, .53, 1., .53)]
    if material.endswith('_FENCE'):
        boxes = [(.375, 0., .375, .625, 1., .625)]
        for axis in (0,2):
            for sign in (-1,1):
                q = list(position); q[axis] += sign
                adjacent = blocks.get(tuple(q), '')
                if adjacent.endswith('_FENCE') or (adjacent and adjacent not in NOT_SOLID):
                    for y0,y1 in ((.375,.5625),(.75,.9375)):
                        lo=[.4375,y0,.4375]; hi=[.5625,y1,.5625]
                        if sign < 0: lo[axis] = 0.; hi[axis] = .5
                        else: lo[axis] = .5; hi[axis] = 1.
                        boxes.append(tuple(lo+hi))
        return boxes
    if material.endswith('_STAIRS'):
        return [(0.,0.,0.,1.,.5,1.),(.5,.5,0.,1.,1.,1.)]
    if material.endswith('_CARPET'):
        return [(0.,0.,0.,1.,1/16,1.)]
    if material in ('CAVE_VINES','CAVE_VINES_PLANT','HANGING_ROOTS'):
        return [(0.,0.,.49,1.,1.,.51),(.49,0.,0.,.51,1.,1.)]
    if material.endswith('_SLAB'):
        return [(0., 0., 0., 1., .5, 1.)]
    if material in ('FLOWERING_AZALEA', 'AZALEA'):
        return [(.40, 0., .40, .60, .50, .60), (0., .35, 0., 1., 1., 1.)]
    if material == 'PINK_PETALS':
        return [(.12, .01, .12, .88, .07, .88)]
    return [(0., 0., 0., 1., 1., 1.)]


def background(width: int, height: int):
    yy = np.linspace(0, 1, height)[:, None, None]
    top, bottom = np.array([128., 164., 214.]), np.array([237., 224., 215.])
    grad = top * (1 - yy) + bottom * yy
    canvas = np.broadcast_to(grad, (height, width, 3)).copy()
    # Soft, purely atmospheric clouds; no extra islands, trees, or structures.
    rng = np.random.default_rng(42)
    noise = Image.fromarray(np.uint8(rng.uniform(0, 255, (60, 120))))
    noise = noise.resize((width, height), Image.Resampling.BICUBIC).filter(ImageFilter.GaussianBlur(width / 65))
    cloud = np.asarray(noise).astype(float) / 255
    cloud = np.clip((cloud - .42) * 1.8, 0, .5)[..., None]
    cloud *= (.30 + .70 * yy)
    canvas = canvas * (1 - cloud) + np.array([255, 244, 232]) * cloud
    # Broad pale sunlight near the upper left.
    px = np.linspace(0, 1, width)[None, :]
    py = np.linspace(0, 1, height)[:, None]
    glow = np.exp(-((px - .14) ** 2 / .11 + (py - .14) ** 2 / .12))[..., None] * .24
    canvas = canvas * (1 - glow) + np.array([255, 233, 211]) * glow
    return canvas.astype(np.float32)


def font(size: int, bold=False):
    candidates = [Path('/System/Library/Fonts/Supplemental/' + ('Arial Bold.ttf' if bold else 'Arial.ttf')),
                  Path('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf')]
    for path in candidates:
        if path.exists(): return ImageFont.truetype(str(path), size)
    return ImageFont.load_default(size=size)


class Renderer:
    def __init__(self, blocks, textures):
        self.blocks = blocks
        self.textures = textures
        self.solid = {p for p, mat in blocks.items() if mat not in NOT_SOLID}
        self.sun = np.array([-.38, .83, -.41])
        self.sun /= np.linalg.norm(self.sun)
        self.shadow_cache = {}
        self.points = np.array(list(blocks), np.float32)
        self.lights = {}
        for (x, y, z), mat in blocks.items():
            if mat in EMISSION:
                cell = (x // 8, y // 8, z // 8)
                self.lights.setdefault(cell, []).append((x + .5, y + .5, z + .5, mat))

    def shadow(self, p):
        key = tuple(round(float(v), 2) for v in p)
        if key in self.shadow_cache: return self.shadow_cache[key]
        dx, dy, dz = self.sun
        x, y, z = p
        result = 1.
        for t in np.arange(.8, 90, 1.15):
            q = (math.floor(x + dx * t), math.floor(y + dy * t), math.floor(z + dz * t))
            if q in self.solid:
                result = .36
                break
        self.shadow_cache[key] = result
        return result

    def local_light(self, p):
        x, y, z = p
        bx, by, bz = math.floor(x / 8), math.floor(y / 8), math.floor(z / 8)
        warm = cool = 0.
        for ax in range(bx - 1, bx + 2):
            for ay in range(by - 1, by + 2):
                for az in range(bz - 1, bz + 2):
                    for lx, ly, lz, mat in self.lights.get((ax, ay, az), ()):
                        dist2 = (lx - x)**2 + (ly - y)**2 + (lz - z)**2
                        if dist2 < 36:
                            value = .42 / (1 + dist2 * .25)
                            if mat in ('SEA_LANTERN', 'SOUL_LANTERN'): cool += value
                            else: warm += value
        return min(.32, warm), min(.18, cool)

    def render(self, out: Path, title: str, subtitle: str, *, width=2560, height=1600,
               azimuth=220, elevation=23, focus=None, span=None, shadows=True):
        started = time.monotonic()
        az, el = math.radians(azimuth), math.radians(elevation)
        cam = np.array([math.sin(az)*math.cos(el), math.sin(el), math.cos(az)*math.cos(el)])
        right = np.array([-math.cos(az), 0, math.sin(az)])
        up = np.array([-math.sin(el)*math.sin(az), math.cos(el), -math.sin(el)*math.cos(az)])
        proj = np.array([right, -up, cam])
        points = self.points + .5
        pp = points @ proj.T
        xmin, ymin, dmin = pp.min(axis=0)
        xmax, ymax, dmax = pp.max(axis=0)
        margin_x, margin_top, margin_bot = width*.055, height*.18, height*.08
        if focus is None:
            scale = min((width-2*margin_x)/(xmax-xmin+2), (height-margin_top-margin_bot)/(ymax-ymin+2))
            cx, cy = (xmin+xmax)/2, (ymin+ymax)/2
        else:
            f = np.array(focus) @ proj.T
            cx, cy = f[0], f[1]
            scale = width / span
        origin = np.array([width/2-scale*cx, (height+margin_top-margin_bot)/2-scale*cy])
        canvas = background(width, height)
        depth = np.full((height, width), -1e10, np.float32)
        emissive = np.zeros((height, width), np.float32)
        rendered_faces = 0
        visible = [(axis, 1 if cam[axis] > 0 else -1, ta, tb) for axis, ta, tb in FACE_AXES]
        for index, (position, material) in enumerate(self.blocks.items()):
            pos = np.array(position, np.float64)
            for box in cuboids(material,position,self.blocks):
                lo, hi = np.array(box[:3]), np.array(box[3:])
                for axis, sign, ta, tb in visible:
                    neighbor = list(position); neighbor[axis] += sign
                    full = box == (0., 0., 0., 1., 1., 1.)
                    if full and tuple(neighbor) in self.solid: continue
                    if material == 'WATER' and self.blocks.get(tuple(neighbor)) == 'WATER': continue
                    a = pos + lo
                    a[axis] = pos[axis] + (hi[axis] if sign > 0 else lo[axis])
                    edge1 = np.zeros(3); edge1[ta] = hi[ta] - lo[ta]
                    edge2 = np.zeros(3); edge2[tb] = hi[tb] - lo[tb]
                    p0 = (proj @ a)[:2] * scale + origin
                    e1 = (proj @ edge1)[:2] * scale
                    e2 = (proj @ edge2)[:2] * scale
                    corners = np.array([p0, p0+e1, p0+e2, p0+e1+e2])
                    left = max(0, int(math.floor(corners[:,0].min())))
                    top = max(int(height*.155), int(math.floor(corners[:,1].min())))
                    r = min(width, int(math.ceil(corners[:,0].max()+1)))
                    bottom = min(int(height*.925), int(math.ceil(corners[:,1].max()+1)))
                    if r <= left or bottom <= top: continue
                    determinant = e1[0]*e2[1]-e1[1]*e2[0]
                    if abs(determinant) < .0001: continue
                    xx = np.arange(left, r, dtype=np.float32)[None, :] + .5-p0[0]
                    yy = np.arange(top, bottom, dtype=np.float32)[:, None] + .5-p0[1]
                    u = (xx*e2[1]-yy*e2[0])/determinant
                    v = (yy*e1[0]-xx*e1[1])/determinant
                    mask = (u >= -.001) & (u <= 1.001) & (v >= -.001) & (v <= 1.001)
                    z = cam @ a + u*(cam @ edge1) + v*(cam @ edge2)
                    region_depth = depth[top:bottom,left:r]
                    mask &= z > region_depth
                    if not mask.any(): continue
                    normal = np.zeros(3); normal[axis] = sign
                    middle = a+edge1*.5+edge2*.5+normal*.025
                    shadow = self.shadow(middle) if shadows else 1
                    directional = max(0, float(normal @ self.sun))
                    ambient = .70 if axis == 1 else (.55 if axis == 0 else .61)
                    intensity = ambient + .38*directional*shadow
                    # Four-corner voxel ambient occlusion, using actual adjacent blocks.
                    ao = []
                    for va, vb in [(0,0),(1,0),(0,1),(1,1)]:
                        q = list(position); q[axis] += sign
                        q1 = q.copy(); q1[ta] += 1 if va else -1
                        q2 = q.copy(); q2[tb] += 1 if vb else -1
                        q3 = q1.copy(); q3[tb] += 1 if vb else -1
                        occ = sum(tuple(t) in self.solid for t in (q1,q2,q3))
                        ao.append(1.-occ*.075)
                    ao_factor = (1-u)*(1-v)*ao[0]+u*(1-v)*ao[1]+(1-u)*v*ao[2]+u*v*ao[3]
                    tex = self.textures.get(material, axis)
                    tx = np.clip((u*16).astype(np.int32),0,15)
                    ty = np.clip(((1-v)*16).astype(np.int32),0,15)
                    colors = tex[ty,tx,:3].copy()
                    alpha = tex[ty,tx,3]
                    mask &= alpha >= 30
                    warm, cool = self.local_light(middle)
                    is_glowing = material in EMISSION
                    if is_glowing:
                        intensity = max(1.15, intensity)
                    colors *= (intensity*ao_factor)[...,None]
                    colors += np.array([24.,13.,3.])*directional*shadow
                    colors += np.array([65.,31.,5.])*warm + np.array([4.,31.,36.])*cool
                    # Restrained distance haze; foreground retains full detail.
                    fog = np.clip((dmax-z)/(dmax-dmin)*.095,0,.095)
                    colors = colors*(1-fog[...,None])+np.array([193.,205.,221.])*fog[...,None]
                    canvas[top:bottom,left:r][mask] = colors[mask]
                    region_depth[mask] = z[mask]
                    emissive[top:bottom,left:r][mask] = 1. if is_glowing else 0.
                    rendered_faces += 1
            if index and index % 25000 == 0:
                print(f'{out.name}: {index:,}/{len(self.blocks):,} blocks', flush=True)
        im = Image.fromarray(np.uint8(np.clip(canvas,0,255)))
        if emissive.max():
            glowmask = Image.fromarray(np.uint8(emissive*255)).filter(ImageFilter.GaussianBlur(max(2,width/850)))
            glowalpha = np.asarray(glowmask).astype(np.float32)/255*.25
            arr = np.asarray(im).astype(np.float32)
            arr = np.clip(arr+glowalpha[...,None]*np.array([72,51,25]),0,255)
            im = Image.fromarray(arr.astype(np.uint8))
        draw = ImageDraw.Draw(im, 'RGBA')
        ink = (43,61,85,255)
        x = int(width*.055)
        draw.text((x,int(height*.042)), 'EVERGARDEN II  /  WORLD LANDMARK', fill=(57,75,99,235),font=font(int(width*.0085), True))
        draw.text((x,int(height*.065)), title, fill=ink,font=font(int(width*.0235), True))
        draw.text((x,int(height*.11)), subtitle, fill=(64,81,103,235),font=font(int(width*.009)))
        footer = f'{len(self.blocks):,} ACTUAL BLOCKS  ·  GENERATED GEOMETRY  ·  LIGHTING PREVIEW, NOT AN IN-GAME SCREENSHOT'
        draw.text((x,height-int(height*.04)),footer,fill=(63,75,92,255),font=font(int(width*.0074)))
        out.parent.mkdir(parents=True, exist_ok=True)
        im.save(out, optimize=True)
        print(f'{out}: {rendered_faces:,} faces, {time.monotonic()-started:.1f}s', flush=True)
        return {'path': out.name, 'camera_azimuth':azimuth,'camera_elevation':elevation,
                'rendered_faces':rendered_faces,'width':width,'height':height,'focus':focus,'span':span}


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('csv',type=Path)
    ap.add_argument('--output-dir',type=Path,default=Path(__file__).resolve().parents[1]/'previews')
    ap.add_argument('--minecraft-jar',type=Path,default=None)
    ap.add_argument('--view',choices=('all','hero','head','side'),default='all')
    ap.add_argument('--width',type=int,default=2560)
    ap.add_argument('--no-shadows',action='store_true')
    args = ap.parse_args()
    blocks = {}
    with args.csv.open() as f:
        for row in csv.reader(f):
            if not row or row[0].startswith('#'): continue
            x,y,z,material = row
            blocks[(int(x),int(y),int(z))] = material
    if not blocks: raise SystemExit('Blueprint CSV has no blocks')
    jar = args.minecraft_jar or find_client()
    print(f'Blueprint: {len(blocks):,} blocks. Textures: {jar or "procedural material colours"}',flush=True)
    textures = Textures(jar)
    renderer = Renderer(blocks,textures)
    outputs = []
    common = dict(width=args.width,height=round(args.width*.625),shadows=not args.no_shadows)
    if args.view in ('all','hero'):
        outputs.append(renderer.render(args.output_dir/'sky-whale-block-preview.png',
            'THE SKY LEVIATHAN', 'A forgotten sanctuary suspended between islands.',
            azimuth=217,elevation=18, **{**common, "height": round(args.width*.92)}))
    if args.view in ('all','head'):
        outputs.append(renderer.render(args.output_dir/'sky-whale-sanctuary-detail.png',
            'THE SKULL SANCTUARY', 'Walk through the jaw and into the heart of the ancient giant.',
            azimuth=215,elevation=9,focus=(-52,117,0),span=105, **common))
    if args.view in ('all','side'):
        outputs.append(renderer.render(args.output_dir/'sky-whale-side-preview.png',
            'BONE, BLOSSOM & SKY', 'The complete silhouette, as built by the world generator.',
            azimuth=195,elevation=12, **common))
    pts = np.array(list(blocks))
    manifest = {'source_csv_sha256':hashlib.sha256(args.csv.read_bytes()).hexdigest(),
                'block_count':len(blocks),'materials':dict(sorted(Counter(blocks.values()).items())),
                'bounds_min':pts.min(axis=0).tolist(),'bounds_max':pts.max(axis=0).tolist(),
                'texture_source':jar.name if jar else 'procedural colours',
                'fallback_materials':sorted(textures.fallback),'images':outputs,
                'disclosure':'Exact generated block positions; simulated lighting and sky; not an in-game screenshot.'}
    (args.output_dir/'sky-whale-preview-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')


if __name__ == '__main__': main()
