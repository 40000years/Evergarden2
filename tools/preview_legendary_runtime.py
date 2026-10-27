"""Render exported Paper ItemDisplay poses; this is a software preview, not game footage.

Uses the shipped effect PNGs and actual display transformations captured by
LegendarySpellChecks. Pillow is the only dependency; it does not invent geometry.
"""
import argparse
import json
import math
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'advance-magic/art/effects/legendary'


def add(a, b):
    return tuple(x+y for x, y in zip(a, b))


def sub(a, b):
    return tuple(x-y for x, y in zip(a, b))


def mul(a, n):
    return tuple(x*n for x in a)


def dot(a, b):
    return sum(x*y for x, y in zip(a, b))


def cross(a, b):
    return (a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])


def norm(a):
    return mul(a, 1/math.sqrt(dot(a, a)))


def rotate(p, q):
    t = mul(cross(q[:3], p), 2)
    return add(p, add(mul(t, q[3]), cross(q[:3], t)))


def transform(p, display):
    p = rotate(p, display['right_rotation'])
    p = tuple(x*s for x, s in zip(p, display['scale']))
    p = rotate(p, display['left_rotation'])
    return add(display['position'], add(display['translation'], p))


def solve(rows):
    # Small pivoted elimination avoids another rendering dependency.
    n = len(rows)
    for column in range(n):
        pivot = max(range(column, n), key=lambda row: abs(rows[row][column]))
        rows[column], rows[pivot] = rows[pivot], rows[column]
        divisor = rows[column][column]
        if abs(divisor) < 1e-10:
            return None
        rows[column] = [v/divisor for v in rows[column]]
        for row in range(n):
            if row != column:
                factor = rows[row][column]
                rows[row] = [x-factor*y for x, y in zip(rows[row], rows[column])]
    return tuple(row[-1] for row in rows)


def warp(texture, quad, size):
    rows = []
    w, h = texture.size
    for (x, y), (u, v) in zip(quad, ((0, 0), (w, 0), (w, h), (0, h))):
        rows.append([x, y, 1, 0, 0, 0, -u*x, -u*y, u])
        rows.append([0, 0, 0, x, y, 1, -v*x, -v*y, v])
    coefficients = solve(rows)
    if coefficients is None:
        return None
    layer = texture.transform(size, Image.Transform.PERSPECTIVE, coefficients,
                              Image.Resampling.BICUBIC)
    mask = Image.new('L', size)
    ImageDraw.Draw(mask).polygon(quad, fill=255)
    layer.putalpha(ImageChops.multiply(layer.getchannel('A'), mask))
    return layer


def font(size):
    for name in ('C:/Windows/Fonts/segoeui.ttf', 'DejaVuSans.ttf'):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def render(frame, output, overhead=False, caster_view=False):
    width, height = 1440, 960
    position = frame['caster']
    forward = norm(tuple(frame.get('facing', (0, 0, 1))))
    right = norm(cross((0, 1, 0), forward))
    verdant = frame['spell'] == 'verdant_sovereign'
    crimson = frame['spell'] == 'crimson_oblivion'
    center = add(position, mul(forward, 12 if verdant else 8))
    center = add(center, (0, 4 if verdant else 15, 0))
    if crimson:
        center = add(add(position, mul(forward, 6)), (0, 5, 0))
    elif not verdant:
        portals = [d['position'] for d in frame['displays'] if d['model'] == 'lunar_seal']
        if portals:
            center = (sum(p[0] for p in portals)/len(portals), position[1]+12,
                      sum(p[2] for p in portals)/len(portals))
    if overhead:
        camera = add(center, (2, 135, -4))
    else:
        camera = add(add(center, mul(right, 45)), mul(forward, 54 if verdant else -62))
        camera = add(camera, (0, 23 if verdant else 9, 0))
        if crimson:
            camera = add(add(add(center, mul(right, 24)), mul(forward, -28)), (0, 10, 0))
    look = norm(sub(center, camera))
    if caster_view:
        camera = frame['camera_position']
        look = forward
    view_right = norm(cross(look, (0, 1, 0)))
    view_up = cross(view_right, look)
    focal = width/(2*math.tan(math.radians(58)/2))

    def project(point):
        delta = sub(point, camera)
        depth = dot(delta, look)
        if depth <= .5:
            return None
        return (width/2+focal*dot(delta, view_right)/depth,
                height/2-focal*dot(delta, view_up)/depth, depth)

    image = Image.new('RGBA', (width, height), '#101724')
    draw = ImageDraw.Draw(image)
    floor = position[1]-.05
    for step in range(-32, 65, 4):
        for a, b in (((position[0]+step, floor, position[2]-32),
                      (position[0]+step, floor, position[2]+64)),
                     ((position[0]-32, floor, position[2]+step),
                      (position[0]+64, floor, position[2]+step))):
            pa, pb = project(a), project(b)
            if pa and pb:
                draw.line((pa[:2], pb[:2]), fill='#263143', width=1)
    quads = []
    # Java's model space centres on (8,8,8), i.e. the middle of a unit cube.
    plane = ((-.5, .5, 0), (.5, .5, 0), (.5, -.5, 0), (-.5, -.5, 0))
    ray_faces = (
        ((-.5, .5, -.5), (.5, .5, -.5), (.5, -.5, -.5), (-.5, -.5, -.5)),
        ((.5, .5, .5), (-.5, .5, .5), (-.5, -.5, .5), (.5, -.5, .5)),
        ((.5, .5, -.5), (.5, .5, .5), (.5, -.5, .5), (.5, -.5, -.5)),
        ((-.5, .5, .5), (-.5, .5, -.5), (-.5, -.5, -.5), (-.5, -.5, .5)))
    for display in frame['displays']:
        if min(display['scale']) <= 0:
            continue
        texture_path = ART / (display['model'].split(':')[-1]+'.png')
        if not texture_path.exists():
            raise FileNotFoundError(texture_path)
        faces = ray_faces if display['model'].endswith('_ray') else (plane,)
        for face in faces:
            projected = [project(transform(vertex, display)) for vertex in face]
            if all(projected):
                quads.append((sum(p[2] for p in projected)/4,
                              texture_path, [p[:2] for p in projected]))
    for _, path, quad in sorted(quads, key=lambda item: -item[0]):
        with Image.open(path) as source:
            layer = warp(source.convert('RGBA'), quad, image.size)
        if layer is not None:
            image = Image.alpha_composite(image, layer)
    draw = ImageDraw.Draw(image)
    feet, head = project(position), project(add(position, (0, 1.8, 0)))
    if feet and head:
        draw.line((feet[:2], head[:2]), fill='#ffe6b2', width=5)
        draw.ellipse((head[0]-5, head[1]-5, head[0]+5, head[1]+5), fill='#ffe6b2')
        draw.text((feet[0]+10, feet[1]-10), 'CASTER / 1.8 m', font=font(15), fill='#ffe6b2')
    title = ('VERDANT / FORWARD BEAM, NO CROWN' if verdant else 'CRIMSON / ONE SHARD AT A TIME' if crimson else 'LUNAR / SIXTEEN LARGE PORTALS, THIN RAYS')
    draw.rectangle((0, 0, width, 85), fill='#0b101a')
    draw.text((25, 15), title, font=font(26), fill='#eef6ff')
    draw.text((25, 52), f"Actual Paper display poses at age {frame['age']} - {'caster' if caster_view else 'overhead' if overhead else 'oblique'} view",
              font=font(17), fill='#a8bed0')
    draw.rectangle((0, height-44, width, height), fill='#0b101a')
    draw.text((25, height-33), 'SOFTWARE PREVIEW / shipped textures + captured runtime transforms / not Minecraft footage',
              font=font(17), fill='#a8bed0')
    image.convert('RGB').save(output)


def main():
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument('scenes', type=Path)
    parser.add_argument('--output', type=Path, default=ART/'runtime-preview')
    args = parser.parse_args()
    frames = json.loads(args.scenes.read_text(encoding='utf8'))['frames']
    args.output.mkdir(parents=True, exist_ok=True)
    written = []
    for spell in ('verdant_sovereign', 'lunar_requiem', 'crimson_oblivion'):
        candidates = [f for f in frames if f['spell'] == spell]
        if not candidates:
            raise ValueError('Missing captured runtime frames for '+spell)
        desired_age = 108 if spell == 'crimson_oblivion' else 80
        frame = min(candidates, key=lambda f: abs(f.get('visual_scale', 1)-1)*1000+abs(f['age']-desired_age))
        for overhead in ((False, True) if spell == 'lunar_requiem' else (False,)):
            path = args.output/(spell+('-overhead' if overhead else '')+'.png')
            render(frame, path, overhead)
            written.append(path)
        if spell == 'verdant_sovereign':
            path = args.output/'verdant-caster-view.png'
            render(frame, path, caster_view=True)
            written.append(path)
        if spell == 'crimson_oblivion':
            motion=[]
            for shot in sorted((f for f in candidates if f['age'] in (100,108,116,124,132,140,148,156)), key=lambda f:f['age']):
                path = args.output/f"crimson-{shot['age']}.png"
                render(shot,path)
                with Image.open(path) as still: motion.append(still.convert('RGB'))
            if motion:
                path=args.output/'crimson-sequence.gif'
                motion[0].save(path,save_all=True,append_images=motion[1:],duration=400,loop=0)
                written.append(path)
    print('Rendered actual runtime pose previews:', *(str(path) for path in written), sep='\n')


if __name__ == '__main__':
    main()
