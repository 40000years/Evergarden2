"""Authored legendary weapon geometry, pixel materials and hand-drawn VFX.

One cuboid catalog feeds Java items, Bedrock attachables and editable Blockbench
sources. No image from the concept sheet is pasted onto the weapon models.
Shapes come from weapon_forge: bevelled reliefs for blades and guards, voxel
volumes for hammer heads, cages and crystals.
"""
from __future__ import annotations
import base64
import io
import json
import math
from pathlib import Path
import random
import uuid
import numpy as np
from PIL import Image, ImageDraw
from weapon_forge import (Forge, band, bedrock_cube, bedrock_point, circle as ring, edges, inside, mirrored,
                          profile, rig_animation, rig_bones, tile_pixels, FACES)

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'advance-magic/art/legendary-weapons'
IDS = ('excalibur', 'longinus', 'tyrfing', 'mjolnir', 'vajra')
TITLES = ('Excalibur', 'Longinus', 'Tyrfing', 'Mjolnir', 'Vajra')
COLORS = ((130, 201, 255), (255, 184, 78), (255, 39, 83), (55, 201, 255), (44, 244, 242))
# 16px tiles in a 128px atlas: flat metals with a gentle sheen.
PALETTE = {
    'gold': '#dba039', 'gold_light': '#ffe6a0', 'gold_dark': '#825025',
    'silver': '#bbc9e4', 'silver_light': '#edf8ff', 'silver_dark': '#6c809f',
    'steel': '#59677e', 'steel_light': '#9aa8c2', 'steel_dark': '#303b50',
    'obsidian': '#292331', 'obsidian_light': '#695166', 'obsidian_edge': '#99748d',
    'leather': '#62402d', 'leather_light': '#9a6941', 'black': '#171c2a',
    'blue': '#0866ba', 'blue_light': '#4ac7ff', 'cyan': '#00b9ce',
    'cyan_light': '#76ffff', 'red': '#b60f33', 'red_light': '#ff3b59',
    'cloth': '#a61a31', 'cloth_light': '#e23a49', 'white': '#f4fdff',
    'gold_deep': '#5c3517', 'blue_dark': '#06386b', 'red_dark': '#5e0a1c',
    'leather_dark': '#3c2619', 'obsidian_dark': '#17131d', 'cyan_dark': '#05616f',
    'silver_mid': '#93a3c2', 'cloth_dark': '#6c0f20', 'steel_mid': '#45526a',
}
MATS = tuple(PALETTE)
EMISSIVE = {'blue_light', 'cyan', 'cyan_light', 'red_light', 'white'}


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf8')


def atlas():
    im = Image.new('RGBA', (128, 128))
    rng = random.Random(74081)
    for i, (name, code) in enumerate(PALETTE.items()):
        rows = tile_pixels(tuple(bytes.fromhex(code[1:])), rng, name in EMISSIVE)
        for y, row in enumerate(rows):
            for x, pixel in enumerate(row):
                im.putpixel((i % 8 * 16 + x, i // 8 * 16 + y), pixel)
    return im


def tile(mat):
    i = MATS.index(mat)
    return [i % 8 * 2, i // 8 * 2, i % 8 * 2 + 2, i // 8 * 2 + 2]


class Weapon:
    """Original strip sculptor; elemental_staff_assets.Staff still builds on it."""
    def __init__(self, name):
        self.name, self.cubes = name, []

    def box(self, x0, y0, z0, x1, y1, z1, mat, bone='weapon'):
        assert x1 > x0 and y1 > y0 and z1 > z0
        self.cubes.append(dict(name=f'{mat}_{len(self.cubes):03}',
            **{'from': [round(x0, 4), round(y0, 4), round(z0, 4)],
               'to': [round(x1, 4), round(y1, 4), round(z1, 4)]}, mat=mat, bone=bone))

    def poly(self, points, z0, z1, mat, bone='weapon', step=.25):
        # Horizontal voxel strips preserve a clean pixel silhouette on both clients.
        last = None
        for y in range(math.floor(min(p[1] for p in points)/step), math.ceil(max(p[1] for p in points)/step)):
            cy = (y+.5)*step
            cuts = []
            for a, b in zip(points, points[1:]+points[:1]):
                if (a[1] <= cy < b[1]) or (b[1] <= cy < a[1]):
                    cuts.append(a[0]+(cy-a[1])*(b[0]-a[0])/(b[1]-a[1]))
            cuts.sort()
            for l, r in zip(cuts[::2], cuts[1::2]):
                l, r = round(l/step)*step, round(r/step)*step
                if r <= l: continue
                key = (l, r)
                if last and last[0] == key and abs(self.cubes[-1]['to'][1]-y*step)<.001:
                    self.cubes[-1]['to'][1] = (y+1)*step
                else:
                    self.box(l, y*step, z0, r, (y+1)*step, z1, mat, bone)
                last = (key, y)

    def line(self, points, width, z0, z1, mat, bone='weapon', step=.25):
        for a, b in zip(points, points[1:]):
            length = math.dist(a, b)
            count = max(1, math.ceil(length/step))
            for j in range(count):
                x = a[0]+(b[0]-a[0])*(j+.5)/count
                y = a[1]+(b[1]-a[1])*(j+.5)/count
                self.box(x-width/2, y-width/2, z0, x+width/2, y+width/2, z1, mat, bone)

    def gem(self, x, y, r, color, light, z=5.8):
        p = [(x,y+r), (x+r,y+.35*r), (x+.7*r,y-r), (x-.7*r,y-r), (x-r,y+.35*r)]
        self.poly(p, z, 16-z, 'gold_dark')
        self.poly([(x+(a-x)*.8,y+(b-y)*.8) for a,b in p], z-.12, 16.12-z, 'gold_light')
        self.poly([(x+(a-x)*.58,y+(b-y)*.65) for a,b in p], z-.3, 16.3-z, color)
        self.box(x-.3*r,y-.3*r,z-.46,x+.1*r,y+.45*r,16.46-z,light)
        self.box(x-.3*r,y+.2*r,z-.55,x-.05*r,y+.55*r,16.55-z,'white')

    def grip(self, bottom, top, width=.95):
        self.box(8-width/2,bottom,7.45,8+width/2,top,8.55,'leather')
        y=bottom+.35
        while y<top:
            self.box(7.4,y,7.3,8.6,y+.2,8.7,'leather_light');y+=.68
        for y in (bottom,top-.35):self.box(7.2,y,7.1,8.8,y+.4,8.9,'gold')


class Relic(Forge):
    """Legendary sculpts from weapon_forge's bevelled reliefs and volumes."""


def glyph(w, x, y, kind, z0, z1, mat='red_light', size=1.0):
    s = size
    strokes = {
        0: [[(0, -.7), (0, .7)], [(0, .45), (.42, .1), (0, -.15)]],
        1: [[(0, -.7), (0, .7)], [(-.42, .62), (0, .2), (.42, .62)]],
        2: [[(-.38, -.7), (.38, .7)], [(.38, -.7), (-.38, .7)]],
        3: [[(0, -.7), (0, .7)], [(0, -.1), (.4, -.55)], [(0, .25), (-.4, .62)]],
    }[kind % 4]
    for line in strokes:
        w.stroke([(x + a * s, y + b * s) for a, b in line], .26, z0, z1, mat)


def excalibur(w):
    w.relief(ring(8, 1.25, 1.35), [(0, .82, 'gold_dark'), (.28, 1.0, 'gold')])
    w.jewel(8, 1.25, .78, 'gold_light', 'blue', 'blue_light', depth=1.3, shape='round')
    w.rod(2.45, 2.9, 1.5, 'gold')
    w.wrap(2.9, 6.6, 1.05, 'leather', ['leather_light', 'gold_dark'])
    w.rod(6.6, 7.05, 1.5, 'gold')
    guard = [(8, 10.1), (9.25, 9.62), (10.6, 9.02), (12.5, 8.92), (13.9, 9.36), (14.95, 10.18), (15.15, 9.36),
             (14.6, 8.36), (13.0, 7.76), (10.9, 7.8), (9.5, 7.36), (8.7, 6.76), (8, 6.46)]
    w.relief(mirrored(guard), [(0, .92, 'gold_dark'), (.3, 1.12, 'gold'), (.62, 1.22, 'gold_light')])
    w.jewel(8, 8.6, 1.08, 'gold_light', 'blue', 'blue_light', depth=1.52)
    for x in (2.3, 13.7):
        w.jewel(x, 8.96, .44, 'gold_light', 'blue', 'blue_light', depth=1.3, shape='round')

    def half(y):
        if y < 10.6:
            return 1.15
        if y < 11.6:
            return 1.15 + (y - 10.6) * .7
        if y < 26.4:
            return 1.85 - (y - 11.6) * .02
        return 1.554 * max(0.0, 1 - (y - 26.4) / 4.8)
    blade = inside(profile(half, 9.6, 31.2))
    groove = lambda X, Y: blade(X, Y) & (np.abs(X - 8) < .45) & (Y > 10.7) & (Y < 26.9)
    # Polished bevels run along the edges and into a shadowed fuller with a gold inlay.
    w.relief(lambda X, Y: blade(X, Y) & ~groove(X, Y), [(0, .27, 'silver_light'), (.36, .58, 'silver')])
    w.relief(groove, [(0, .36, 'silver_dark')])
    w.plate(lambda X, Y: groove(X, Y) & (np.abs(X - 8) < .2), 7.58, 8.42, 'gold')
    for k, y in enumerate(np.arange(12.4, 25.6, 2.2)):
        if k % 2:
            w.box(7.62, y + .22, 7.54, 8.38, y + .48, 8.46, 'blue_light')
        w.box(7.82, y, 7.54, 8.18, y + .7, 8.46, 'blue_light')


def longinus(w):
    w.jewel(8, 1.0, .82, 'gold', 'red', 'red_light', depth=1.05, shape='diamond')
    w.rod(1.75, 2.3, 1.28, 'gold')
    w.rod(2.3, 19.2, .84, 'leather')
    w.wrap(5.6, 9.6, .86, 'leather_dark', ['leather_light'])
    for y in (2.3, 5.15, 9.65, 13.5, 16.6):
        w.rod(y, y + .45, 1.2, 'gold')
    w.rod(17.8, 19.3, 1.08, 'gold_dark')
    crown = [(8, 23.25), (9.0, 22.85), (9.7, 21.9), (10.6, 21.4), (11.5, 21.72), (12.25, 22.5), (12.15, 21.45),
             (11.4, 20.45), (10.2, 19.85), (9.2, 19.25), (8.8, 18.6), (8, 18.4)]
    w.relief(mirrored(crown), [(0, .78, 'gold_dark'), (.28, .95, 'gold'), (.6, 1.05, 'gold_light')])
    w.jewel(8, 20.85, .95, 'gold_light', 'red', 'red_light', depth=1.34)

    def half(y):
        if y < 25.4:
            return .85 + max(0.0, y - 23.0) * .38
        return 1.76 * max(0.0, 1 - (y - 25.4) / 6.2) ** .82
    w.relief(profile(half, 22.7, 31.6), [(0, .28, 'silver_light'), (.38, .5, 'silver'), (.82, .64, 'red'), (1.18, .7, 'red_light')])
    for side, z in ((-1, 7.2), (1, 8.8)):
        line = [(8 + side * x, y) for x, y in ((1.05, 19.9), (1.9, 18.3), (2.75, 16.6), (3.15, 14.8), (3.05, 13.0), (3.5, 11.4))]
        widths = [.36, .5, .58, .6, .58, .55]
        w.relief(band(line, widths, notch=.75), [(0, (.18, .18), 'cloth_dark'), (.18, .24, 'cloth'), (.45, .28, 'cloth_light')], 'ribbon', z)


def tyrfing(w):
    w.relief([(8, -.55), (9.05, .95), (9.0, 2.4), (7.0, 2.4), (6.95, .95)], [(0, .7, 'obsidian_edge'), (.3, .86, 'obsidian')])
    w.jewel(8, 1.42, .62, 'obsidian_edge', 'red', 'red_light', depth=1.12, shape='diamond')
    w.wrap(2.35, 7.15, 1.12, 'obsidian_dark', ['red_dark', 'obsidian_light'])
    w.rod(7.1, 7.55, 1.55, 'obsidian_edge')
    guard = [(8, 10.65), (9.3, 10.08), (10.9, 9.52), (12.4, 9.92), (13.6, 11.12), (14.55, 12.95), (14.25, 11.2),
             (13.4, 9.85), (12.1, 8.76), (10.5, 8.2), (9.3, 7.56), (8, 7.15)]
    w.relief(mirrored(guard), [(0, .95, 'obsidian_edge'), (.3, 1.12, 'obsidian'), (.62, 1.22, 'obsidian_light')])
    w.jewel(8, 8.95, 1.02, 'obsidian_edge', 'red', 'red_light', depth=1.5, shape='oval')
    tip = (8.3, 31.4)

    def left(y):
        if y < 26.0:
            return 8 - (1.85 - (y - 10.0) * .012)
        return tip[0] - (tip[0] - 6.34) * max(0.0, 1 - (y - 26.0) / (tip[1] - 26.0)) ** .8

    def right(y):
        # Straight left edge, a regular saw of fangs on the right that points to the tip.
        base = 8 + 1.95 - (y - 10.0) * .01
        if y < 26.0:
            if 12.0 <= y < 24.6:
                base += .82 * (((y - 12.0) % 2.1) / 2.1)
            return base
        edge = 8 + 1.95 - 16.0 * .01
        return tip[0] + (edge - tip[0]) * max(0.0, 1 - (y - 26.0) / (tip[1] - 26.0)) ** .8
    w.relief(edges(left, right, 10.0, tip[1]), [(0, .28, 'obsidian_edge'), (.36, .55, 'obsidian'), (.9, .6, 'red_dark')])
    for k, y in enumerate((12.6, 15.1, 17.6, 20.1, 22.6, 25.1)):
        glyph(w, 8.05 + .1 * math.sin(k), y, k, 7.36, 8.64, 'red_light', .85)


def mjolnir(w):
    w.relief(ring(8, 1.3, 1.28), [(0, .95, 'steel_dark'), (.3, 1.1, 'steel')])
    w.jewel(8, 1.3, .66, 'gold', 'blue', 'blue_light', depth=1.36, shape='round')
    w.wrap(2.45, 13.85, 1.45, 'leather', ['leather_light', 'leather_dark'], pitch=.66)
    w.rod(13.8, 14.75, 2.05, 'gold')

    def head(X, Y, Z):
        dx, dy, dz = np.abs(X - 8), np.abs(Y - 18.25), np.abs(Z - 8)
        body = (dx <= 7) & (dy <= 3.5) & (dz <= 3.3) & (dy + dz <= 5.55) & (dx + dy <= 9.7) & (dx + dz <= 9.5)
        gold = (np.abs(dx - 4.65) <= .42) & (dy <= 3.72) & (dz <= 3.52) & (dy + dz <= 5.9)
        edge = body & ((dy + dz > 5.05) | (dx + dy > 9.25) | (dx + dz > 9.05))
        return [('gold', gold), ('silver_light', body & (dx > 6.6)), ('steel_light', edge), ('steel', body)]
    w.volume(head, (.5, 14.5, 4.3), (15.5, 22.0, 11.7))
    for z0, z1 in ((4.62, 4.75), (11.25, 11.38)):
        diamond = [(8, 21.1), (10.55, 18.25), (8, 15.4), (5.45, 18.25), (8, 21.1)]
        w.stroke(diamond, .3, z0, z1, 'gold_dark')
        w.stroke([(6.75, 20.2), (8.75, 18.7), (7.45, 18.05), (9.3, 16.35)], .3, z0 - .02, z1 + .02, 'blue_light')


def vajra(w):
    w.jewel(8, .95, .8, 'gold', 'cyan', 'cyan_light', depth=1.0, shape='diamond')
    w.rod(1.6, 2.2, 1.3, 'gold')
    w.rod(2.2, 19.4, .78, 'silver_dark')
    for y0, y1 in ((4.9, 7.0), (12.2, 15.0)):
        w.rod(y0, y1, .9, 'cyan')
    for y in (2.2, 4.45, 7.0, 9.4, 11.75, 15.05, 17.4):
        w.rod(y, y + .45, 1.22, 'gold')
    w.jewel(8, 10.55, .62, 'gold_light', 'cyan_dark', 'cyan_light', depth=.92, shape='round')
    w.rod(18.9, 20.2, 1.55, 'gold_dark')
    w.relief(mirrored([(8, 21.0), (9.2, 20.85), (10.4, 20.2), (9.9, 19.85), (8.9, 19.75), (8, 19.6)]),
             [(0, .9, 'gold_dark'), (.25, 1.05, 'gold')])
    path = [(.9, 20.2), (2.4, 21.2), (3.9, 22.7), (4.45, 24.45), (3.95, 26.25), (2.55, 27.8), (1.05, 28.85), (.3, 29.3)]
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        w.tube([(8 + dx * r, y, 8 + dz * r) for r, y in path], .36, 'gold')
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        w.tube([(8 + dx * 4.45, 24.45, 8 + dz * 4.45), (8 + dx * 5.05, 24.45, 8 + dz * 5.05)], .3, 'gold_light')

    def crystal(X, Y, Z):
        dx, dy, dz = np.abs(X - 8), Y - 24.45, np.abs(Z - 8)
        body = (dx + dz) / 1.95 + np.abs(dy) / 2.95 <= 1
        bright = body & (dy > .2) & ((dx + dz) / 1.95 + dy / 2.95 > .62)
        return [('cyan_light', bright), ('cyan', body)]
    w.volume(crystal, (5.8, 21.3, 5.8), (10.2, 27.6, 10.2))
    w.tube([(8, 28.9, 8), (8, 30.6, 8)], .42, 'gold')
    w.jewel(8, 30.95, .55, 'gold_light', 'cyan', 'cyan_light', depth=.7, shape='diamond')


def make_weapon(name):
    w = Relic(name)
    {'excalibur': excalibur, 'longinus': longinus, 'tyrfing': tyrfing, 'mjolnir': mjolnir, 'vajra': vajra}[name](w)
    return w


def fx_frame(name, frame):
    """128px cutout VFX; every line is authored in weapon coordinates."""
    im=Image.new('RGBA',(128,128));d=ImageDraw.Draw(im)
    idx=IDS.index(name);color=COLORS[idx];rng=random.Random(701+idx*71+frame//2)
    def xy(p):return (round((p[0]+8)*4),round((32-p[1])*4))
    def line(p,width=1):
        p=[xy(v) for v in p]
        d.line(p,fill=color+(255,),width=width+2)
        d.line(p,fill=(224,255,255,255) if idx in (3,4) else (255,238,213,255),width=width)
    def star(x,y,r=1):
        cx,cy=xy((x,y));d.line((cx-r*2,cy,cx+r*2,cy),fill=color+(255,),width=1)
        d.line((cx,cy-r*3,cx,cy+r*3),fill=(243,252,255,255),width=1)
        d.rectangle((cx-1,cy-1,cx+1,cy+1),fill=(255,255,255,255))
    if name in ('mjolnir','vajra'):
        paths=([[(1,20),(-1,21),(-1.2,24),(-3,24),(-2.4,27)],
                [(14,21),(16,23),(17.7,23),(17.3,26),(20,27)],
                [(2,15),(.4,13),(-1,13.5),(-1.4,10)],
                [(13,16),(16,14.7),(15.5,12),(18,11.5)]] if name=='mjolnir' else
               [[(3,27),(1.2,28),(.6,30),(-1,29.5)],[(13,27),(15,29),(16,28.6),(17.2,30)],
                [(3,21),(.6,19.7),(1.2,17.6),(-.8,16)],[(13,22),(15.4,20),(14.9,18),(17.2,17.6)]])
        for k,p in enumerate(paths):
            line([(x+rng.choice((-.4,0,.4)),y+rng.choice((-.35,0,.35))) for x,y in p],1)
            if (frame+k)%3:star(p[1][0]+rng.uniform(-1,1),p[1][1]+rng.uniform(-1,1),.5)
        if name=='mjolnir':
            line([(4.2,22.4),(5.3,22.9),(6,22.2),(7.2,23.4),(8.7,23.0),(10.2,23.8),(11.6,23.1)],1)
    elif name=='excalibur':
        for k,(x,y) in enumerate(((3.3,25),(12.2,23),(.5,16),(13.5,13),(4,5),(10.8,29))):
            star(x+math.sin(frame*.65+k)*.3,y+math.sin(frame*.5+k)*.6,.6+(frame+k)%3*.15)
        line([(5.25,12.4),(5.45,14),(5.65,26.3),(6.4,28.6)],1)
        line([(10.75,12.4),(10.55,14),(10.35,26.3),(9.6,28.6)],1)
    elif name=='longinus':
        for k,(x,y) in enumerate(((3.4,24),(12.2,22),(10.6,8),(5.1,4),(4,28))):
            star(x,y+math.sin(frame*.7+k)*.5,.6)
        for x in (5.3,10.7):line([(x,23.6),(x+(.35 if x<8 else -.35),25.8),(8,31.0)],1)
    else:
        for k in range(14):
            x=rng.choice((3,4,12.6,13.6))+rng.uniform(-1,1);y=9+(k*1.7+frame*.7)%22
            px,py=xy((x,y));d.rectangle((px,py,px+1,py+2),fill=color+(255,))
        line([(4.4,11),(3.3,14),(4.5,15.5),(3.7,18)],1)
        line([(12.1,19),(13.5,21.6),(12.4,23.2),(13.3,25.6)],1)
    return im


def sigil(name):
    """A small bespoke equip seal using the existing world-plane bridge."""
    idx=IDS.index(name);im=Image.new('RGBA',(256,256));d=ImageDraw.Draw(im);color=COLORS[idx]+(255,)
    for r in (108,101,75):d.ellipse((128-r,128-r,128+r,128+r),outline=color,width=2)
    def point(angle,r):return (128+math.cos(angle)*r,128+math.sin(angle)*r)
    sides=(6,4,5,8,4)[idx]
    vertices=[point(math.tau*j/sides-math.pi/2,74) for j in range(sides)]
    d.line(vertices+[vertices[0]],fill=color,width=2)
    for j in range(12):
        a=math.tau*j/12
        d.line([point(a,84),point(a,94)],fill=color,width=2)
        d.line([point(a-.022,91),point(a+.035,87)],fill=color,width=2)
        if j%2==0:d.line([point(a-.045,96),point(a+.045,96)],fill=color,width=1)
    if name in ('mjolnir','vajra'):
        d.line([(142,83),(115,126),(137,126),(113,169)],fill=color,width=4)
    elif name=='excalibur':
        d.line([(128,84),(128,171)],fill=color,width=4);d.line([(108,137),(148,137)],fill=color,width=3)
    elif name=='longinus':
        d.polygon([(128,82),(140,117),(128,135),(116,117)],outline=color,width=2)
        d.line([(128,125),(128,174)],fill=color,width=3)
    else:
        d.line([(128,84),(119,108),(136,123),(121,140),(133,160)],fill=color,width=3)
    return im


# Physical handle centres along +Y; Java and Bedrock both hold the weapon here.
GRIPS = {'excalibur':4.75,'longinus':7.6,'tyrfing':4.75,'mjolnir':6.4,'vajra':9.0}
# The Bedrock attachable applies these same Java display transforms through
# weapon_forge's java2bedrock rig, so both editions hold the weapon alike.
DISPLAY = {
    'ground': {'translation':[0,3,0],'scale':[.4,.4,.4]},
    'fixed': {'rotation':[0,180,0],'translation':[0,-4,0],'scale':[.65,.65,.65]},
    'head': {'translation':[0,10,0],'scale':[.6,.6,.6]},
}
RIBBON_PIVOT = [8, 19.9, 8]
# Former Bedrock frame, kept only for the thrown-Longinus entity geometry.
PROJECTILE_TIP = .8


def rotate_xyz(point, angles):
    """Minecraft ItemTransform's Rx Ry Rz convention (angles in degrees)."""
    x,y,z=point;rx,ry,rz=map(math.radians,angles)
    x,y=x*math.cos(rz)-y*math.sin(rz),x*math.sin(rz)+y*math.cos(rz)
    x,z=x*math.cos(ry)+z*math.sin(ry),-x*math.sin(ry)+z*math.cos(ry)
    y,z=y*math.cos(rx)-z*math.sin(rx),y*math.sin(rx)+z*math.cos(rx)
    return [x,y,z]


def java_display(name):
    display=json.loads(json.dumps(DISPLAY))
    # Vanilla handheld model from the installed official 26.3 client. The grip
    # sits in the lower-left of its diagonal 16px sword sprite. Transfer that
    # contact point to each authored straight handle instead of guessing offsets.
    for first in (False,True):
        for left in (False,True):
            mode=('firstperson_' if first else 'thirdperson_')+('lefthand' if left else 'righthand')
            native_r=[0,-90,25 if first else 55]
            native_t=[1.13,3.2,1.13] if first else [0,4,.5]
            native_s=.68 if first else .85
            target=rotate_xyz([(3.5-8)*native_s,(3.5-8)*native_s,0],native_r)
            target=[target[i]+native_t[i] for i in range(3)]
            if first:target=[target[0],target[1]+.8,target[2]-1.5]
            if left:target[0]*=-1
            r=[0,90 if left else -90,-10 if first and left else 10 if first else 0]
            effective=[r[0],-r[1] if left else r[1],-r[2] if left else r[2]]
            scale=(.62 if name=='longinus' else .68) if first else .90
            offset=rotate_xyz([0,(GRIPS[name]-8)*scale,0],effective)
            translation=[target[i]-offset[i] for i in range(3)]
            if left:translation[0]*=-1
            display[mode]={'rotation':r,'translation':[round(v,6) for v in translation],'scale':[scale]*3}
    return display


def bedrock_pose(first,name):
    """Bedrock hand pose: the Java display transform through the java2bedrock rig."""
    return rig_animation(java_display(name),first)


def java_model(w):
    elements=[]
    for c in w.cubes:
        e={k:c[k] for k in ('from','to')}
        e.update(name=c['name'],faces={f:{'uv':tile(c['mat']),'texture':'#metal'} for f in FACES})
        if c['mat'] in EMISSIVE:e.update(shade=False,light_emission=15)
        elements.append(e)
    # Two separated front/back cutout layers, plus perpendicular planes: VFX
    # remains visible at side angles and never depends on server entity tracking.
    for axis,pos in (('z',3.25),('z',12.75),('x',7.95)):
        if axis=='z':a,b,faces=[-8,0,pos],[24,32,pos],('north','south')
        else:a,b,faces=[pos,0,-8],[pos,32,24],('east','west')
        elements.append({'from':a,'to':b,'shade':False,'light_emission':15,
            'faces':{f:{'uv':[0,0,16,16],'texture':'#fx'} for f in faces}})
    return {'credit':'Evergarden / legendary_weapon_assets.py','ambientocclusion':False,'gui_light':'front',
        'textures':{'metal':'advance_magic:item/legendary_weapons/materials',
                    'fx':f'advance_magic:item/legendary_weapons/{w.name}_fx',
                    'particle':'advance_magic:item/legendary_weapons/materials'},
        'display':java_display(w.name),'elements':elements}


def uv_px(mat):
    u0,v0,_,_=tile(mat)
    return [u0*8,v0*8,16]


def body_groups(w):
    groups={}
    for c in w.cubes:
        groups.setdefault('inlay' if c['mat'] in EMISSIVE else c['bone'],[]).append(c)
    return groups


def bedrock_geometry(w, fx=False):
    # A java2bedrock rig: bound root, then display X/Y/Z bones; body and FX
    # share every parent transform, so the aura cannot drift from the blade.
    bones=rig_bones(GRIPS[w.name])
    for bone,items in ({} if fx else body_groups(w)).items():
        pivot=RIBBON_PIVOT if bone=='ribbon' else [8,16,8]
        bones.append({'name':bone,'parent':'display_z','pivot':bedrock_point(pivot),
            'cubes':[bedrock_cube(c,uv_px(c['mat'])) for c in items]})
    if fx:
        cubes=[{'origin':[-16,0,z],'size':[32,32,0],
                'uv':{f:{'uv':[0,0],'uv_size':[128,128]} for f in ('north','south')}} for z in (-4.75,4.75)]
        cubes.append({'origin':[.05,0,-16],'size':[0,32,32],
            'uv':{f:{'uv':[0,0],'uv_size':[128,128]} for f in ('east','west')}})
        bones.append({'name':'aura','parent':'display_z','pivot':bedrock_point([8,16,8]),'cubes':cubes})
    return {'format_version':'1.16.0','minecraft:geometry':[{
        'description':{'identifier':f'geometry.advance_magic.{w.name}'+('_fx' if fx else ''),
            'texture_width':128,'texture_height':128,'visible_bounds_width':4,'visible_bounds_height':4.5,
            'visible_bounds_offset':[0,.75,0]},'bones':bones}]}


def bbmodel(w, texture):
    uid=lambda s:str(uuid.uuid5(uuid.NAMESPACE_URL,f'evergarden:legendary/{w.name}/{s}'))
    groups=[]
    for bone in sorted({c['bone'] for c in w.cubes}):
        groups.append({'name':bone,'uuid':uid(bone),'origin':[8,20 if bone=='ribbon' else 4,8],
            'rotation':[0,0,0],'export':True,'isOpen':True,'visibility':True,
            'children':[uid(c['name']) for c in w.cubes if c['bone']==bone]})
    elements=[]
    for c in w.cubes:
        elements.append({'name':c['name'],'type':'cube','uuid':uid(c['name']),
            'from':[c['from'][0]-8,c['from'][1]-4,c['from'][2]-8],
            'to':[c['to'][0]-8,c['to'][1]-4,c['to'][2]-8],
            'origin':[0,0,0],'rotation':[0,0,0],'shade':c['mat'] not in EMISSIVE,
            'box_uv':False,'autouv':0,'rescale':False,'locked':False,'visibility':True,'export':True,
            'faces':{f:{'uv':[v*8 for v in tile(c['mat'])],'texture':0} for f in FACES}})
    for g in groups:g['origin']=[g['origin'][0]-8,g['origin'][1]-4,g['origin'][2]-8]
    # ModelEngine's parser requires an explicit helper box. It is excluded from
    # both held-item exports and is never used as a gameplay collision volume.
    elements.append({'name':'hitbox','uuid':uid('hitbox_cube'),'type':'cube',
        'from':[-1,0,-1],'to':[1,4,1],'origin':[0,0,0],'faces':{}})
    animators={}
    for g in groups:
        amount=3 if g['name']=='ribbon' else .6
        animators[g['uuid']]={'name':g['name'],'type':'bone','keyframes':[
            {'channel':'rotation','data_points':[{'x':'0','y':'0','z':str(v)}],
             'uuid':uid(g['name']+str(t)),'time':t,'interpolation':'catmullrom'}
            for t,v in ((0,0),(.5,amount),(1,0),(1.5,-amount),(2,0))]}
    return {'meta':{'format_version':'4.10','model_format':'free','box_uv':False},
        'name':w.name,'resolution':{'width':128,'height':128},'elements':elements,
        'outliner':groups+[{'name':'hitbox','uuid':uid('hitbox'),'origin':[0,3,0],'children':[uid('hitbox_cube')],'export':True}],
        'textures':[{'name':'legendary_materials.png','namespace':'','folder':'','path':'',
            'id':'0','uuid':uid('texture'),'width':128,'height':128,
            'uv_width':128,'uv_height':128,'source':'data:image/png;base64,'+base64.b64encode(texture).decode()}],
        'animations':[{'uuid':uid('idle'),'name':'idle','loop':'loop','length':2,
            'override':False,'anim_time_update':'','blend_weight':'','start_delay':'','loop_delay':'','animators':animators}]}


def register(java, bedrock, item_atlas, definitions):
    """Merge into Advance Magic's existing atlases and Geyser mappings."""
    from legendary_weapon_preview import render_icon_frames
    ART.mkdir(parents=True,exist_ok=True)
    tex=atlas();stream=io.BytesIO();tex.save(stream,format='PNG');texture=stream.getvalue()
    p=java/'assets/advance_magic/textures/item/legendary_weapons';p.mkdir(parents=True,exist_ok=True)
    (p/'materials.png').write_bytes(texture)
    bp=bedrock/'textures/legendary_weapons';bp.mkdir(parents=True,exist_ok=True)
    (bp/'materials.png').write_bytes(texture)
    animations={};controllers={}
    for idx,name in enumerate(IDS):
        w=make_weapon(name)
        write_json(ART/f'{name}.geometry.json',{'id':name,'cubes':w.cubes})
        write_json(ART/f'{name}.bbmodel',bbmodel(w,texture))
        seal='legendary_'+name+'_sigil'
        for destination in (java/f'assets/advance_magic/textures/effect/{seal}.png',bedrock/f'textures/particle/{seal}.png'):
            destination.parent.mkdir(parents=True,exist_ok=True);sigil(name).save(destination)
        write_json(java/f'assets/advance_magic/models/effect/{seal}.json',{
            'ambientocclusion':False,'textures':{'line':f'advance_magic:effect/{seal}'},
            'elements':[{'from':[0,0,7.99],'to':[16,16,8.01],'shade':False,'light_emission':15,
                'faces':{f:{'texture':'#line','uv':[0,0,16,16]} for f in ('north','south')}}]})
        write_json(java/f'assets/advance_magic/items/{seal}.json',{
            'model':{'type':'minecraft:model','model':f'advance_magic:effect/{seal}'}})
        for suffix in ('','_flat'):
            particle=json.loads((bedrock/f'particles/solar_corona{suffix}.particle.json').read_text())
            particle['particle_effect']['description'].update(identifier=f'advance_magic:{seal}{suffix}')
            particle['particle_effect']['description']['basic_render_parameters']['texture']=f'textures/particle/{seal}'
            particle['particle_effect']['components']['minecraft:particle_appearance_billboard']['uv']={
                'texture_width':256,'texture_height':256,'uv':[0,0],'uv_size':[256,256]}
            write_json(bedrock/f'particles/{seal}{suffix}.particle.json',particle)
        write_json(java/f'assets/advance_magic/models/item/legendary_weapons/{name}.json',java_model(w))
        frames=[fx_frame(name,f) for f in range(8)]
        strip=Image.new('RGBA',(128,1024))
        for f,im in enumerate(frames):strip.paste(im,(0,f*128));im.save(bp/f'{name}_fx_{f}.png')
        strip.save(p/f'{name}_fx.png')
        write_json(p/f'{name}_fx.png.mcmeta',{'animation':{'frametime':2,'interpolate':False,'width':128,'height':128}})
        # The icon is rendered from the same cuboids, with its own eight VFX frames.
        icons=render_icon_frames(w,tex,frames)
        iconstrip=Image.new('RGBA',(128,1024))
        for f,im in enumerate(icons):iconstrip.paste(im,(0,f*128))
        iconstrip.save(p/f'{name}_icon.png')
        write_json(p/f'{name}_icon.png.mcmeta',{'animation':{'frametime':2,'interpolate':False,'width':128,'height':128}})
        icons[0].save(bp/f'{name}_icon.png');icons[0].save(ART/f'{name}_icon.png')
        write_json(java/f'assets/advance_magic/models/item/legendary_weapons/{name}_icon.json',{
            'parent':'minecraft:item/generated','textures':{'layer0':f'advance_magic:item/legendary_weapons/{name}_icon'}})
        write_json(java/f'assets/advance_magic/items/legendary_{name}.json',{'model':{
            'type':'minecraft:select','property':'minecraft:display_context',
            'cases':[{'when':'gui','model':{'type':'minecraft:model','model':f'advance_magic:item/legendary_weapons/{name}_icon'}}],
            'fallback':{'type':'minecraft:model','model':f'advance_magic:item/legendary_weapons/{name}'}}})
        write_json(bedrock/f'models/entity/legendary_weapons/{name}.geo.json',bedrock_geometry(w))
        write_json(bedrock/f'models/entity/legendary_weapons/{name}_fx.geo.json',bedrock_geometry(w,True))
        prefix=f'animation.advance_magic.legendary_{name}'
        animations[prefix+'.first']=bedrock_pose(True,name)
        animations[prefix+'.third']=bedrock_pose(False,name)
        animations[prefix+'.idle']={'loop':True,'animation_length':2,'bones':{
            'aura':{'scale':['1 + math.sin(q.life_time * 240) * 0.012',1,'1 + math.sin(q.life_time * 240) * 0.012']}}}
        if name=='longinus':animations[prefix+'.idle']['bones']['ribbon']={'rotation':[0,0,'math.sin(q.life_time * 180) * 3']}
        attach={'identifier':f'advance_magic:legendary_{name}',
            'materials':{'default':'entity_alphatest','glow':'entity_emissive_alpha'},
            'textures':{'default':'textures/legendary_weapons/materials',**{f'fx{f}':f'textures/legendary_weapons/{name}_fx_{f}' for f in range(8)}},
            'geometry':{'default':f'geometry.advance_magic.{name}','fx':f'geometry.advance_magic.{name}_fx'},
            'animations':{'first':prefix+'.first','third':prefix+'.third','idle':prefix+'.idle'},
            'scripts':{'animate':[{'first':'context.is_first_person == 1.0'},{'third':'context.is_first_person == 0.0'},'idle']},
            'render_controllers':['controller.render.advance_magic.legendary_body',f'controller.render.advance_magic.legendary_{name}_fx']}
        write_json(bedrock/f'attachables/legendary_weapons/{name}.json',{'format_version':'1.10.0','minecraft:attachable':{'description':attach}})
        controllers[f'controller.render.advance_magic.legendary_{name}_fx']={
            'arrays':{'textures':{'Array.arcs':[f'Texture.fx{f}' for f in range(8)]}},
            'geometry':'Geometry.fx','materials':[{'*':'Material.glow'}],
            'textures':['Array.arcs[math.mod(math.floor(q.life_time * 10), 8)]']}
        icon=f'advance_magic.legendary_{name}';item_atlas[icon]={'textures':f'textures/legendary_weapons/{name}_icon'}
        base=('netherite_sword','netherite_sword','netherite_sword','mace','netherite_sword')[idx]
        components={}
        definitions.setdefault('minecraft:'+base,[]).append({'type':'definition','model':f'advance_magic:legendary_{name}',
            'bedrock_identifier':f'advance_magic:legendary_{name}','display_name':TITLES[idx],
            'bedrock_options':{'icon':icon,'allow_offhand':True,'display_handheld':True},**components})
    # Front-view spear made from the same authored cuboids, for world-space rain.
    rain='legendary_longinus_rain'
    image=Image.new('RGBA',(256,256));draw=ImageDraw.Draw(image)
    for cube in sorted(make_weapon('longinus').cubes,key=lambda c:c['from'][2],reverse=True):
        x0,y0,z0=cube['from'];x1,y1,z1=cube['to']
        draw.rectangle((round(128+(x0-8)*7),round(248-y1*7),round(128+(x1-8)*7),round(248-y0*7)),fill=PALETTE[cube['mat']])
    for destination in (java/f'assets/advance_magic/textures/effect/{rain}.png',bedrock/f'textures/particle/{rain}.png'):image.save(destination)
    write_json(java/f'assets/advance_magic/models/effect/{rain}.json',{
        'ambientocclusion':False,'textures':{'line':f'advance_magic:effect/{rain}'},
        'elements':[{'from':[0,0,7.99],'to':[16,16,8.01],'shade':False,'light_emission':15,
        'faces':{face:{'texture':'#line','uv':[0,0,16,16]} for face in ('north','south')}}]})
    write_json(java/f'assets/advance_magic/items/{rain}.json',{'model':{'type':'minecraft:model','model':f'advance_magic:effect/{rain}'}})
    for suffix in ('','_flat'):
        particle=json.loads((bedrock/f'particles/solar_corona{suffix}.particle.json').read_text())
        particle['particle_effect']['description'].update(identifier=f'advance_magic:{rain}{suffix}')
        particle['particle_effect']['description']['basic_render_parameters']['texture']=f'textures/particle/{rain}'
        particle['particle_effect']['components']['minecraft:particle_appearance_billboard']['uv']={'texture_width':256,'texture_height':256,'uv':[0,0],'uv_size':[256,256]}
        write_json(bedrock/f'particles/{rain}{suffix}.particle.json',particle)
    projectile_assets(java,bedrock)
    controllers['controller.render.advance_magic.legendary_body']={
        'geometry':'Geometry.default','materials':[{'*':'Material.default'},{'inlay':'Material.glow'}],'textures':['Texture.default']}
    write_json(bedrock/'animations/legendary_weapons.animation.json',{'format_version':'1.8.0','animations':animations})
    write_json(bedrock/'render_controllers/legendary_weapons.render.json',{'format_version':'1.8.0','render_controllers':controllers})


def projectile_geometry(w, fx):
    """Thrown-Longinus entity: tip near the origin, pole along +Z after the grip turn."""
    # Bedrock negates bone X/Y rotations when converting its model axes.
    # -90 X puts the pole along model +Z. Bedrock entity-to-world also
    # reflects Z; root yaw must be 180 + flight yaw, not -flight yaw.
    shift=PROJECTILE_TIP-max(c['to'][1] for c in w.cubes)
    bones=[{'name':'root','pivot':[0,0,0]},{'name':'grip','parent':'root','pivot':[0,0,0],'rotation':[-90,0,0]}]
    for bone,items in ({} if fx else body_groups(w)).items():
        cubes=[]
        for c in items:
            cube=bedrock_cube(c,uv_px(c['mat']));cube['origin'][1]=round(cube['origin'][1]+shift,4);cubes.append(cube)
        bones.append({'name':bone,'parent':'grip','pivot':[0,round(20+shift,4),0],'cubes':cubes})
    if fx:
        cubes=[{'origin':[-16,round(shift,4),z],'size':[32,32,0],
                'uv':{f:{'uv':[0,0],'uv_size':[128,128]} for f in ('north','south')}} for z in (-4.75,4.75)]
        cubes.append({'origin':[0,round(shift,4),-16],'size':[0,32,32],
            'uv':{f:{'uv':[0,0],'uv_size':[128,128]} for f in ('east','west')}})
        bones.append({'name':'aura','parent':'grip','pivot':[0,round(16+shift,4),0],'cubes':cubes})
    return {'format_version':'1.16.0','minecraft:geometry':[{
        'description':{'identifier':'geometry.advance_magic.longinus_projectile'+('_fx' if fx else ''),
            'texture_width':128,'texture_height':128,'visible_bounds_width':4,'visible_bounds_height':4,
            'visible_bounds_offset':[0,0,0]},'bones':bones}]}


def projectile_assets(java,bedrock):
    import copy
    w=make_weapon('longinus')
    model=copy.deepcopy(java_model(w));model.pop('display')
    for element in model['elements']:
        for point in ('from','to'):element[point][1]-=8
    write_json(java/'assets/advance_magic/models/item/legendary_weapons/longinus_projectile.json',model)
    write_json(java/'assets/advance_magic/items/longinus_projectile.json',{'model':{
        'type':'minecraft:model','model':'advance_magic:item/legendary_weapons/longinus_projectile'}})
    for fx in (False,True):
        write_json(bedrock/('models/entity/legendary_weapons/longinus_projectile'+('_fx' if fx else '')+'.geo.json'),projectile_geometry(w,fx))
    write_json(bedrock/'entity/longinus_projectile.entity.json',{'format_version':'1.10.0','minecraft:client_entity':{'description':{
        'identifier':'advance_magic:longinus_projectile',
        'materials':{'default':'entity_alphatest','glow':'entity_emissive_alpha'},
        'textures':{'default':'textures/legendary_weapons/materials',**{f'fx{f}':f'textures/legendary_weapons/longinus_fx_{f}' for f in range(8)}},
        'geometry':{'default':'geometry.advance_magic.longinus_projectile','fx':'geometry.advance_magic.longinus_projectile_fx'},
        'animations':{'flight':'animation.advance_magic.longinus_projectile'},
        'scripts':{'animate':['flight']},
        'render_controllers':['controller.render.advance_magic.legendary_body','controller.render.advance_magic.legendary_longinus_fx']}}})
    write_json(bedrock/'animations/longinus_projectile.animation.json',{'format_version':'1.8.0','animations':{
        'animation.advance_magic.longinus_projectile':{'loop':True,'bones':{'root':{'rotation':["q.property('advance_magic:flight_pitch')","180 + q.property('advance_magic:flight_yaw')",0]}}}}})
