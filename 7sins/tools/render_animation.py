"""Render exported Java skeletal poses with the actual pack cubes (Pillow)."""
import argparse
import json
import math
from pathlib import Path
from PIL import Image, ImageDraw
import build_wrath_pack as model

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('poses', type=Path)
args = parser.parse_args()
frames = json.loads(args.poses.read_text())
model.geometry()


def rotate(point, q):
    x, y, z = point
    a, b, c, w = q
    tx, ty, tz = 2*(b*z-c*y), 2*(c*x-a*z), 2*(a*y-b*x)
    return x+w*tx+b*tz-c*ty, y+w*ty+c*tx-a*tz, z+w*tz+a*ty-b*tx


def render(frame, size=(420, 450)):
    image = Image.new('RGB', size, '#12151f')
    draw = ImageDraw.Draw(image)
    draw.ellipse((90, 362, 350, 408), fill='#090b11')
    faces = []
    for name, cubes in model.PARTS.items():
        pose = frame['bones'][name]
        def vertex(p, cube):
            if 'rotation' in cube:
                r = cube['rotation']; origin = r['origin']; angle = math.radians(r['angle'])/2
                axis = {'x': (1, 0, 0), 'y': (0, 1, 0), 'z': (0, 0, 1)}[r['axis']]
                q = tuple(v*math.sin(angle) for v in axis)+(math.cos(angle),)
                p = [v+o for v, o in zip(rotate([v-o for v, o in zip(p, origin)], q), origin)]
            p = rotate([(v-8)/16 for v in p], pose['rotation'])
            x, y, z = [v+o for v, o in zip(p, pose['position'])]
            angle = math.radians(-28)
            return x*math.cos(angle)-z*math.sin(angle), y, x*math.sin(angle)+z*math.cos(angle)
        for cube in cubes:
            a, b = cube['from'], cube['to']
            points = [vertex(p, cube) for p in [(a[0],a[1],a[2]),(b[0],a[1],a[2]),(b[0],b[1],a[2]),(a[0],b[1],a[2]),
                       (a[0],a[1],b[2]),(b[0],a[1],b[2]),(b[0],b[1],b[2]),(a[0],b[1],b[2])]]
            for ids, shade in [((0,1,2,3),1.05),((4,7,6,5),.6),((0,3,7,4),.7),((1,5,6,2),.85),((3,2,6,7),1.25),((0,4,5,1),.45)]:
                vertices = [points[i] for i in ids]
                faces.append((sum(z-y*.3 for x,y,z in vertices)/4, vertices,
                              tuple(min(255,int(c*shade)) for c in model.PALETTE[cube['material']])))
    for _, vertices, color in sorted(faces, key=lambda face: face[0], reverse=True):
        coords = [(225+x*77, 381-y*77+z*23) for x,y,z in vertices]
        draw.polygon(coords, fill=color)
        draw.line(coords+[coords[0]], fill='#17121b', width=1)
    draw.text((16, 16), f"{frame['clip'].upper()} / {frame['tick']/20:.1f}s", fill='#f7ac83')
    draw.text((16, 426), 'Runtime poses + pack geometry / simulated render', fill='#9396a6')
    return image


clips = {name: [f for f in frames if f['clip']==name] for name in ('walk','slam','stomp','sweep')}
animation = []
for index in range(len(clips['walk'])):
    sheet = Image.new('RGB', (840,900))
    for cell, name in enumerate(clips):
        sheet.paste(render(clips[name][index]), ((cell%2)*420, (cell//2)*450))
    animation.append(sheet)
animation[0].save(model.ROOT/'art/wrath-animation.gif', save_all=True, append_images=animation[1:], duration=100, loop=0)
contact = Image.new('RGB', (1260,900))
for cell, tick in enumerate((0,22,44,48,54,72)):
    frame = next(f for f in clips['slam'] if f['tick']==tick)
    contact.paste(render(frame), ((cell%3)*420, (cell//3)*450))
contact.save(model.ROOT/'art/wrath-slam-poses.png')
print(model.ROOT/'art/wrath-animation.gif')
print(model.ROOT/'art/wrath-slam-poses.png')
