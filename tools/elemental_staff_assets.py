"""Eight authored elemental staffs: one thick cuboid source, three native exports.

The supplied concept is a silhouette/material reference, never a pasted texture.
Held Java items and Bedrock attachables animate locally; no server emitter entity
is needed. Editable ModelEngine 4.1.1 blueprints include embedded textures,
separate focus/orbit bones, a looping idle clip, and its required hitbox helper.
"""
from __future__ import annotations

import base64
import copy
import hashlib
import io
import json
import math
from pathlib import Path
import random
import shutil
import uuid

from PIL import Image, ImageDraw
from legendary_weapon_assets import Weapon, rotate_xyz

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'advance-magic/art/elemental-staffs'
IDS = ('fire', 'wind', 'earth', 'ice', 'water', 'lightning', 'light', 'dark')
TITLES = ('Fire Staff', 'Wind Staff', 'Earth Staff', 'Ice Staff', 'Water Staff',
          'Lightning Staff', 'Light Staff', 'Dark Staff')
COLORS = ((255, 102, 32), (121, 255, 212), (165, 230, 55), (135, 222, 255),
          (44, 182, 255), (181, 172, 255), (255, 224, 132), (187, 74, 255))
DESCRIPTIONS = {
    'fire': 'Crimson sunstone in an open golden brazier; ember crown and two silk tails.',
    'wind': 'Jade focus framed by twin ivory feather crescents and a winding air helix.',
    'earth': 'Peridot heart held by split basalt antlers, moss, roots and floating stones.',
    'ice': 'Sixfold silver snowflake crown, sapphire icicles and suspended crystal chains.',
    'water': 'Azure pearl embraced by two sculpted breaking waves and a water spiral.',
    'lightning': 'Amethyst storm core between two asymmetric conductor prongs and coil rings.',
    'light': 'Sun diamond inside an open gilded astrolabe, halo and suspended light blades.',
    'dark': 'Void amethyst inside a blackened crescent, violet chains and hooked thorns.',
}

# 16px material tiles in a compact 128px atlas, shared across all export formats.
PALETTE = {
    'gold_dark': '#775023', 'gold': '#cca34d', 'gold_light': '#ffe4a1',
    'silver_dark': '#58708b', 'silver': '#a4bdd1', 'silver_light': '#e0f3fa',
    'ivory_dark': '#8d9d94', 'ivory': '#d4e1cd', 'ivory_light': '#f3ffec',
    'wood_dark': '#342b21', 'wood': '#66503b', 'wood_light': '#a08b60',
    'leather': '#493b37', 'leather_light': '#8a6c53',
    'obsidian': '#1b1728', 'obsidian_light': '#393445', 'obsidian_edge': '#716279',
    'stone_dark': '#494c43', 'stone': '#747a64', 'stone_light': '#a8a58a',
    'moss': '#456b30', 'leaf': '#8aa73e', 'white': '#f1ffff',
    'fire_dark': '#772b28', 'fire': '#c64224', 'fire_light': '#ff7f25', 'fire_core': '#ffe29a',
    'wind_dark': '#28756b', 'wind': '#4dcab2', 'wind_light': '#9effdd', 'wind_core': '#deffed',
    'earth_dark': '#3e6a2d', 'earth': '#87b32e', 'earth_light': '#c2f14c', 'earth_core': '#f0ffc1',
    'ice_dark': '#2c548e', 'ice': '#438ee4', 'ice_light': '#86d8ff', 'ice_core': '#ddfaff',
    'water_dark': '#174d88', 'water': '#137fc7', 'water_light': '#29c5fa', 'water_core': '#b1f2ff',
    'lightning_dark': '#3f3c72', 'lightning': '#8175d4', 'lightning_light': '#bbb5ff', 'lightning_core': '#f3efff',
    'light_dark': '#9b7737', 'light': '#f2c867', 'light_light': '#ffe9a3', 'light_core': '#fff9de',
    'dark_dark': '#311b50', 'dark': '#7530a5', 'dark_light': '#bb55f8', 'dark_core': '#edb4ff',
}
MATS = tuple(PALETTE)
EMISSIVE = {'white'} | {f'{name}_{suffix}' for name in IDS for suffix in ('light', 'core')}
FACES = ('north', 'south', 'east', 'west', 'up', 'down')
GRIPS = dict(zip(IDS, (10.6, 11.2, 10.3, 11.0, 10.8, 10.4, 11.1, 10.7)))
FIRST_SCALES = dict(zip(IDS, (.62, .60, .62, .60, .61, .63, .60, .62)))
# Fit each complete solid staff to y=50..450 in the existing 1160x500
# screenshot-calibrated affine view. This is an approximate framing aid, not a
# client screenshot. It preserves the separate native world contact at .9.
BEDROCK_SCALES = dict(zip(IDS, (.333394, .331237, .336824, .335852,
                               .337295, .340453, .333498, .331469)))
BEDROCK_LIFTS = dict(zip(IDS, (8.127750, 8.305010, 8.060868, 8.286573,
                              8.234059, 8.129962, 8.295537, 8.141721)))
BEDROCK_PIVOT = [0, 24, 0]
BEDROCK_GRIP = [0, 12.5, 0]


def write_json(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + '\n', encoding='utf8')


def tile(mat):
    i = MATS.index(mat)
    return [i % 8 * 2, i // 8 * 2, i % 8 * 2 + 2, i // 8 * 2 + 2]


def atlas(frame=0):
    im = Image.new('RGBA', (128, 128)); d = ImageDraw.Draw(im)
    rng = random.Random(271828)
    for i, (name, code) in enumerate(PALETTE.items()):
        rgb = tuple(bytes.fromhex(code[1:])); ox, oy = i % 8 * 16, i // 8 * 16
        for y in range(16):
            for x in range(16):
                shade = (1.12 if x < 3 else 1.02 if x < 11 else .84) + rng.choice((-.025, 0, 0, .025))
                if y < 2: shade += .05
                if name in EMISSIVE: shade *= .93 + .07 * math.sin(frame * math.tau / 8 + i * .22)
                d.point((ox+x, oy+y), fill=tuple(min(255, round(c*shade)) for c in rgb)+(255,))
        if name not in EMISSIVE:
            d.line((ox+2, oy+3, ox+4, oy+3), fill=tuple(min(255, round(c*1.24)) for c in rgb)+(255,))
    return im


class Staff(Weapon):
    def __init__(self, name):
        super().__init__(name)

    def beam(self, points, width, z0, z1, mat, bone='crown'):
        """Extruded polygon segments, fewer solid cuboids than overlapping stamps."""
        for a, b in zip(points, points[1:]):
            dx, dy = b[0]-a[0], b[1]-a[1]
            length = math.hypot(dx, dy)
            nx, ny = -dy/length*width/2, dx/length*width/2
            self.poly([(a[0]+nx, a[1]+ny), (b[0]+nx, b[1]+ny),
                       (b[0]-nx, b[1]-ny), (a[0]-nx, a[1]-ny)], z0, z1, mat, bone)

    def arc(self, x, y, rx, ry, start, end, width, z0, z1, mat, bone='crown', segments=20):
        points = [(x+math.cos(math.radians(start+(end-start)*i/segments))*rx,
                   y+math.sin(math.radians(start+(end-start)*i/segments))*ry)
                  for i in range(segments+1)]
        self.beam(points, width, z0, z1, mat, bone)

    def crystal(self, x, y, r, element=None, z=6.6, height=1, bone='focus', frame=False):
        element = element or self.name
        p = [(x,y+r*height), (x+r*.78,y+r*.18), (x,y-r*height), (x-r*.78,y+r*.18)]
        self.poly(p, z, 16-z, element+'_dark', bone)
        self.poly([(x+(a-x)*.83,y+(b-y)*.92) for a,b in p],z-.18,16.18-z,element,bone)
        self.poly([(x,y+r*.85*height),(x+r*.56,y+r*.12),(x,y-r*.84*height)],z-.31,16.31-z,element+'_light',bone)
        self.poly([(x,y+r*.85*height),(x,y-r*.84*height),(x-r*.55,y+r*.12)],z-.30,16.30-z,element+'_core',bone)
        self.box(x-.12,y-.32*r,z-.42,x+.13,y+.48*r,16.42-z,'white',bone)
        if frame:
            for sx in (-1, 1):
                self.beam([(x+sx*r*.5,y+r*.55*height),(x+sx*r*.85,y+r*.18),(x+sx*r*.5,y-r*.55*height)],.22,z-.5,16.5-z,'gold_light','crown')

    def collar(self, y, width=1.65, metal='gold', height=.5):
        self.box(8-width/2,y,8-width/2,8+width/2,y+height,8+width/2,metal,'staff')
        self.box(8-width/2+.13,y+height-.13,8-width/2-.08,8+width/2-.13,y+height+.06,8+width/2+.08,
                 'gold_light' if metal=='gold' else 'silver_light','staff')

    def leaf(self, x,y,side=1,mat='leaf',bone='filigree',z=7.05):
        self.poly([(x,y),(x+side*.5,y+.8),(x+side*1.3,y+1),(x+side*1.05,y+.25)],z,16-z,mat,bone)


def shaft(w, wood='wood', metal='gold', accent=None):
    accent=accent or w.name
    # Deliberately unobstructed, chunky leather hand contact in the middle.
    w.box(7.59,1.5,7.55,8.41,22,8.45,wood,'staff')
    w.box(7.61,2,7.39,7.83,21.6,8.61,wood+'_light' if wood in ('wood','obsidian') else 'silver_light','staff')
    g=GRIPS[w.name]
    w.box(7.44,g-1.8,7.4,8.56,g+1.8,8.6,'leather','staff')
    for y in [g-1.6+i*.6 for i in range(6)]:
        w.box(7.35,y,7.31,8.65,y+.17,8.69,'leather_light','staff')
    for y in (2.1,5.2,g-2.05,g+1.85,16.7,20.2):w.collar(y,metal=metal)
    # Small lower focus and faceted metal ferrule echo the crown's language.
    w.crystal(8,2.05,.95,accent,z=6.85,height=1.15,bone='staff')
    w.poly([(7.5,1.8),(8,.3),(8.5,1.8)],7.15,8.85,metal,'staff')
    w.collar(1.5,1.45,metal,.38)
    for y in (6.7,14.5,18):
        w.box(7.84,y,7.3,8.16,y+.8,8.7,accent+'_light','staff')


def make_staff(name):
    if name not in IDS:raise ValueError(name)
    w=Staff(name)
    shaft(w,wood=('obsidian' if name=='dark' else 'silver' if name in ('ice','lightning') else 'ivory' if name in ('wind','light') else 'wood'),
          metal='silver' if name in ('ice','lightning','dark') else 'gold')
    if name=='fire':
        # Open brazier with long outward prongs, split red-gold sunstone.
        for s in (-1,1):
            p=lambda v:[(8+s*x,y) for x,y in v]
            w.poly(p([(0,20),(1.1,21.5),(4.9,23),(5.8,26),(5.3,28),(4.45,27),(4.6,25.1),(3.8,23.7),(.7,22.7)]),6.5,9.5,'gold_dark')
            w.beam(p([(.7,21.2),(4.4,23.5),(5.15,25.6),(4.9,27.5)]),.65,6.1,9.9,'gold')
            w.beam(p([(1,21.75),(4.2,23.8),(4.8,25.5)]),.25,5.95,10.05,'gold_light')
            w.poly(p([(.5,21),(1.7,21.9),(2.35,24),(1.6,23.3),(.7,22.8)]),6.15,9.85,'fire_dark')
            # Articulated ember charms, hung away from the main shaft.
            w.beam(p([(4.2,22.8),(4.7,21),(4.7,19.9)]),.18,7.1,8.9,'gold','filigree')
            w.crystal(8+s*4.7,19.2,.53,z=7.15,bone='orbit')
        w.crystal(8,25.35,2.25,z=6.15,height=1.4,frame=True)
        flame=[(7.4,27.2),(6.35,28.5),(6.9,30),(7.25,29),(8.4,30),(8.2,31.8),(9.5,30.6),(9.2,29),(10.1,28.3),(9.2,26.8)]
        w.poly(flame,7.1,8.9,'fire','flame')
        w.poly([(7.5,27.6),(7.15,28.8),(8.3,29.7),(8.9,30.5),(8.5,28.7),(9.1,28),(8.4,27.2)],6.95,9.05,'fire_light','flame')
        w.poly([(7.8,27.4),(7.7,28.5),(8.4,29),(8.25,27.8),(8.6,27.2)],6.8,9.2,'fire_core','flame')
        w.crystal(8,20.5,.95,z=6.35,bone='crown')
        # Two silk streamers with gold stitched hems, not transparent planes.
        for s in (-1,1):
            p=[(8+s*x,y) for x,y in ((.55,19.6),(1.5,18.5),(1.35,16.6),(2.8,14.1),(2.4,12.2),(1.5,13.8),(.5,16.3),(.75,18.2))]
            z=6.95 if s<0 else 8.65
            w.poly(p,z,z+.42,'fire_dark','ribbon')
            w.beam([(8+s*.9,19),(8+s*1,16.8),(8+s*2.1,14.2),(8+s*2.1,13)],.22,z-.06,z+.49,'gold','ribbon')
    elif name=='wind':
        # Twin offset ivory crescents form a wide, airy feathered S silhouette.
        for s,cy,start,end in ((1,25.5,-95,112),(-1,26,80,285)):
            cx=8+s*.2
            w.arc(cx,cy,4.5,4.4,start,end,.83,6.85,9.15,'ivory_dark')
            w.arc(cx,cy,4.3,4.25,start,end,.62,6.6,9.4,'ivory')
            w.arc(cx,cy,4.1,4.1,start,end,.2,6.43,9.57,'ivory_light')
        # Swept feather tips protrude from the frame in opposite directions.
        for s,y in ((1,29.8),(-1,21.7)):
            w.poly([(8+s*.5,y),(8+s*1.4,y+2),(8+s*1.7,y+.8),(8+s*4,y-.2),(8+s*2.6,y-.6)],6.6,9.4,'ivory')
            w.beam([(8+s*.8,y+.5),(8+s*1.6,y+.7),(8+s*3.2,y-.1)],.2,6.4,9.6,'gold_light')
        w.crystal(8,25.6,2.1,z=6.15,height=1.15,frame=True)
        for y in (19.3,16.2,6):w.crystal(8,y,.78,z=6.7,bone='filigree')
        for x,y in ((3.5,29.7),(12.4,20.8),(10.5,8.7)):
            w.leaf(x,y,1,'wind_light','orbit',6.9)
        # A real narrow spiral ribbon curls around the lower pole in depth.
        for j in range(22):
            t=j/21; a=t*math.tau*1.3
            x=8+math.sin(a)*2.1; y=4+t*14.1; z=8+math.cos(a)*1.5
            w.box(x-.2,y,z-.2,x+.2,y+.55,z+.2,'wind','spiral')
    elif name=='earth':
        # Unequal basalt antlers: rugged outside faces, a clear peridot heart.
        for s in (-1,1):
            p=lambda v:[(8+s*x,y) for x,y in v]
            w.poly(p([(.2,20.2),(2.2,21.4),(4.5,24),(4.7,26.5),(3.8,27.2),(3.7,29.5),(2.5,31.1),(1.8,30.1),(2.4,27.9),(2.6,25.8),(1.8,23.5)]),6.3,9.7,'stone_dark')
            w.poly(p([(1.1,21.7),(2.3,22.1),(4,24.5),(4.1,26.1),(3.2,26.5),(3.2,29.2),(2.55,30.1),(2.6,27.6),(2.9,25.5),(2.2,23.6)]),6.05,9.95,'stone')
            for x,y in ((2.7,29),(3.7,25.3),(2,22.9)):
                w.box(8+s*x-.25,y,5.9,8+s*x+.35,y+.6,10.1,'stone_light')
            w.beam(p([(.8,20.7),(2,23),(3.3,24.5),(3.15,27.7)]),.31,5.8,10.2,'gold')
            w.leaf(8+s*2.8,25.5,s,'moss')
        w.crystal(8,25.6,2.15,z=5.8,height=1.3,frame=True)
        w.crystal(8,20.9,.92,z=6.2,bone='crown')
        # Winding wooden roots and moss leave the leather contact unobstructed.
        for s in (-1,1):
            for a,b in ((2.7,8),(13.3,19.5)):
                w.beam([(8+s*.7,a),(8+s*1.4,a+1.5),(8+s*.7,a+3),(8+s*.8,b)],.38,7,9,'wood_dark','filigree')
            for y in (3,5.8,15.7,18.8):w.leaf(8+s*.5,y,s)
        for x,y,z,r in ((3,28,7,.6),(13,24,6.9,.7),(4.1,20,7.3,.45),(12.5,29,7.2,.45)):
            w.box(x-r,y-r,z-r,x+r,y+r,z+r,'stone','orbit')
            w.box(x-r,y+r*.4,z-r-.1,x+.1,y+r,z+r+.1,'moss','orbit')
        for x,y in ((6.7,18),(9.6,7.4),(5.3,22)):w.crystal(x,y,.42,z=6.7,bone='orbit')
    elif name=='ice':
        # Symmetric snowflake: six solid silver spokes with sapphire lances.
        cy=25.6
        for angle,length in ((90,5.1),(30,4.9),(-30,4.9),(-90,4),(-150,4.9),(150,4.9)):
            a=math.radians(angle); dx,dy=math.cos(a),math.sin(a)
            tip=(8+dx*length,cy+dy*length)
            w.beam([(8,cy),tip],.66,6.9,9.1,'silver_dark')
            w.beam([(8,cy),tip],.35,6.6,9.4,'silver_light')
            for sign in (-1,1):
                x,y=8+dx*length*.6,cy+dy*length*.6
                b=a+sign*math.pi/3
                w.beam([(x,y),(x+math.cos(b)*1.25,y+math.sin(b)*1.25)],.33,6.6,9.4,'silver')
            w.crystal(tip[0],tip[1],.75,z=6.65,height=1.5,bone='crown')
        w.crystal(8,25.6,2.2,z=5.7,height=1.3,frame=True)
        for s in (-1,1):
            x=8+s*3.6
            w.beam([(x,22.8),(x,19.5)],.17,7.1,8.9,'silver','filigree')
            for y in (20.2,21.2,22.2):
                w.box(x-.2,y,6.95,x+.2,y+.35,9.05,'silver_light','filigree')
            w.crystal(x,18.7,.65,z=6.6,height=1.7,bone='orbit')
        for y in (4,16.1,19.3):w.crystal(8,y,.65,z=6.65,height=1.4,bone='filigree')
        for x,y in ((6.8,3.1),(9.5,2.8)):
            w.crystal(x,y,.72,z=7,height=1.7,bone='staff')
    elif name=='water':
        # Two opposing breaking waves sculpt a dynamic S around a round focus.
        for s,cy in ((1,27.2),(-1,21.3)):
            cx=8-s*.5
            # Outer wave, inset band and foam ridge: all solid volumes.
            start,end=(-40,215) if s>0 else (140,395)
            w.arc(cx,cy,4.7,3.6,start,end,1.05,6.4,9.6,'water_dark')
            w.arc(cx,cy,4.55,3.45,start,end,.76,6.1,9.9,'water')
            w.arc(cx,cy,4.6,3.6,start,end,.28,5.91,10.09,'water_light')
            for i in (2,5,8,11,14,17):
                a=math.radians(start+(end-start)*i/20)
                x,y=cx+math.cos(a)*4.8,cy+math.sin(a)*3.7
                w.box(x-.22,y-.2,5.76,x+.22,y+.45,10.24,'water_core')
        w.arc(8,25.3,2.6,2.6,0,360,.4,6.3,9.7,'gold')
        # Pearl is an octagonal solid, faceted with lighter inset volumes.
        w.poly([(6.6,23.9),(5.9,25.2),(6.6,26.7),(8,27.4),(9.4,26.7),(10.1,25.2),(9.4,23.9),(8,23.2)],6.05,9.95,'water_dark','focus')
        w.crystal(8,25.35,1.8,z=5.72,height=1.05)
        w.beam([(8,20.5),(7.5,19.3),(8.5,17.7),(9.1,16),(7,14.6)],.5,7,9,'water','spiral')
        w.beam([(7,8.2),(6.4,6.9),(8.4,5.5),(8.8,3.8)],.5,7,9,'water','spiral')
        for x,y in ((3.5,18.8),(12.7,22.6),(11.5,15.4),(5,6)):
            w.crystal(x,y,.45,z=7.2,height=1.1,bone='orbit')
    elif name=='lightning':
        # Original storm tuning fork: unequal prongs, conductor teeth and coils.
        for s,top in ((-1,30.7),(1,29.4)):
            p=lambda v:[(8+s*x,y) for x,y in v]
            w.poly(p([(.5,20.2),(2.3,22),(3.6,24),(3.6,top-1.5),(4.5,top),(3,top-.5),(2.3,top-1.8),(2.4,24.3),(1.3,22.4),(.4,21.6)]),6.5,9.5,'silver_dark')
            w.beam(p([(.8,21),(3,24),(3,top-1.7),(4,top-.6)]),.61,6.2,9.8,'silver')
            w.beam(p([(1.3,21.9),(2.65,24.3),(2.65,top-1.8)]),.22,6.0,10.0,'lightning_light')
            for y in (24.8,26.2,27.6):
                w.box(8+s*3-.6,y,6,8+s*3+.6,y+.35,10,'gold')
            w.crystal(8+s*3,top-.1,.55,z=6.65,height=1.4,bone='crown')
        w.crystal(8,25.7,1.85,z=5.95,height=1.15,frame=True)
        # Jagged physical lightning spine cutting between the conductors.
        bolt=[(8.6,30.9),(6.8,27.3),(8.3,27.3),(7.3,23.4),(9.5,27.6),(8.3,27.6),(9.3,30.9)]
        w.poly(bolt,7.35,8.65,'lightning_core','focus')
        for y in (3.5,16.2,18.1,20):
            w.arc(8,y,1.7,.55,0,360,.3,6.5,9.5,'gold','filigree',14)
            w.box(7.6,y-.2,6.15,8.4,y+.45,9.85,'lightning','filigree')
        for x,y in ((4.2,20),(12,23.5),(5.5,16)):
            w.crystal(x,y,.45,z=7,height=1.4,bone='orbit')
    elif name=='light':
        # Gilded astrolabe with an open ring and detached wing-blades.
        w.arc(8,25.7,4.25,4.25,0,360,.65,6.5,9.5,'gold_dark')
        w.arc(8,25.7,4.1,4.1,0,360,.38,6.2,9.8,'gold')
        w.arc(8,25.7,3.77,3.77,25,335,.18,6.0,10.0,'gold_light')
        w.crystal(8,25.7,2.15,z=5.85,height=1.3,frame=True)
        for s in (-1,1):
            w.poly([(8+s*3.6,24.8),(8+s*5,23),(8+s*5,16.8),(8+s*4.15,18.2),(8+s*4.15,22.8),(8+s*2.6,24)],6.45,9.55,'gold_dark')
            w.poly([(8+s*4.2,22.6),(8+s*4.7,22.9),(8+s*4.7,18),(8+s*4.3,18.6)],6.15,9.85,'light_core')
            w.beam([(8+s*2.1,21.1),(8+s*2.6,19),(8+s*2.5,17.6)],.32,6.9,9.1,'gold','filigree')
            w.crystal(8+s*2.5,16.9,.5,z=6.75,height=1.5,bone='orbit')
        # Horizontal perspective halo built from real cuboids in all three axes.
        for j in range(24):
            a=math.tau*j/24;x=8+math.cos(a)*2.3;z=8+math.sin(a)*1.65
            w.box(x-.25,30,z-.18,x+.25,30.22,z+.18,'gold_light','halo')
        w.crystal(8,31.2,.62,z=6.9,height=1.15,bone='halo')
        for s in (-1,1):
            w.crystal(8+s*5.2,25.7,.72,z=6.9,height=.55,bone='crown')
        w.crystal(8,20.6,1.08,z=6.25,bone='crown')
        for y in (6.5,15.6):w.crystal(8,y,.7,z=6.6,bone='filigree')
    elif name=='dark':
        # Strong asymmetric black moon; the opening surrounds a floating void gem.
        w.arc(8.9,25.6,5,5.3,70,300,1.15,6.4,9.6,'obsidian_edge')
        w.arc(9,25.6,4.8,5.1,70,300,.9,6.1,9.9,'obsidian_light')
        w.arc(9.1,25.6,4.65,4.95,70,300,.58,5.9,10.1,'obsidian')
        w.arc(9.1,25.6,4.3,4.6,85,290,.18,5.72,10.28,'dark_light')
        w.crystal(9.2,25.7,1.95,z=5.8,height=1.3,frame=False)
        # Hooked horn points and a spiked crown foot give front/back depth.
        w.poly([(9.7,30.2),(10.8,31.7),(10.7,29.5),(9.8,28.8)],6.3,9.7,'obsidian')
        w.poly([(9.3,21.4),(10.8,20.2),(10.1,19.1),(8.4,21)],6.3,9.7,'obsidian')
        for s in (-1,1):
            w.beam([(8+s*.6,20),(8+s*2,18.9),(8+s*2.2,16.9),(8+s*1.6,15.3)],.35,6.9,9.1,'obsidian_edge','filigree')
            for y in (17.1,18.1,19.1):
                w.box(8+s*2-.2,y,6.6,8+s*2+.2,y+.45,9.4,'silver_dark','filigree')
            w.crystal(8+s*1.6,14.8,.6,z=6.65,height=1.3,bone='orbit')
            for y in (4,6.6,15.9,18.7):
                w.poly([(8+s*.4,y),(8+s*1.5,y+.9),(8+s*1.3,y+.1),(8+s*.6,y-.4)],7,9,'obsidian','filigree')
        w.crystal(8,20.4,.9,z=6.25,bone='crown')
        w.beam([(7.7,4.3),(8.4,5.5),(7.5,6.3),(8.4,7.4)],.21,7.1,8.9,'dark_light','staff')
    return w


def fx_frame(name, frame):
    """Native cutout texture: authored elemental motion, 8 coherent 128px frames."""
    color=COLORS[IDS.index(name)];core=tuple(bytes.fromhex(PALETTE[name+'_core'][1:]))
    im=Image.new('RGBA',(128,128));d=ImageDraw.Draw(im)
    rng=random.Random(4321+IDS.index(name)*103+frame)
    phase=frame*math.tau/8
    def xy(p):return (round((p[0]+8)*4),round((32-p[1])*4))
    def line(points,width=1):
        p=[xy(v) for v in points]
        d.line(p,fill=color+(255,),width=width+2)
        d.line(p,fill=core+(255,),width=width)
    def star(x,y,r=.6):
        cx,cy=xy((x,y));r=max(1,round(r*4))
        d.line((cx-r,cy,cx+r,cy),fill=color+(255,),width=1)
        d.line((cx,cy-r,cx,cy+r),fill=core+(255,),width=1)
        d.rectangle((cx,cy,cx+1,cy+1),fill=(255,255,255,255))
    def diamond(x,y,r=.45):
        cx,cy=xy((x,y));r=max(1,round(r*4))
        d.polygon([(cx,cy-r),(cx+r,cy),(cx,cy+r),(cx-r,cy)],fill=color+(255,))
        d.point((cx,cy),fill=core+(255,))
    if name=='fire':
        for k in range(15):
            y=3+(k*2.1+frame*.7)%28
            x=(3.1 if k%2 else 12.9)+math.sin(k+phase)*.65
            diamond(x,y,.18+.15*(k%3))
        for s in (-1,1):
            line([(8+s*(1.1+.25*math.sin(phase)),28),(8+s*1.8,29),(8+s*1.5,30+frame%3*.3),(8+s*.9,31.4)],1)
        star(8+math.sin(phase)*.5,26,.5)
    elif name=='wind':
        for start,turns in ((2.5,1.45),(17.5,1.0)):
            points=[]
            for j in range(33):
                t=j/32;a=t*math.tau*turns+phase
                points.append((8+math.sin(a)*3.25,start+t*11))
            # Occluded portions are omitted, like wind travelling round the pole.
            for j in range(0,30,10):line(points[j:j+7],1)
        for k in range(7):
            y=3+(k*4+frame*.5)%28;x=8+math.sin(k*2+phase)*5.2
            diamond(x,y,.28)
    elif name=='earth':
        for k in range(10):
            y=2.5+(k*2.8+frame*.16)%27.5;x=8+math.sin(k*3+phase*.5)*(3.5+(k%2))
            diamond(x,y,.22)
            if k%3==0:line([(x-.2,y-.1),(x+.1,y+.5)],1)
        line([(5,23.5),(5.6,24.5),(5.1,25.7),(5.8,27)],1)
        line([(10.7,22.9),(10.4,24),(11,25.1)],1)
    elif name=='ice':
        for k in range(8):
            x=2.3+(k*3.47)%11.5;y=3+(k*4-frame*.32)%27
            cx,cy=xy((x,y));r=2 if k%2 else 3
            for angle in (0,60,120):
                a=math.radians(angle);dx=math.cos(a)*r;dy=math.sin(a)*r
                d.line((round(cx-dx),round(cy-dy),round(cx+dx),round(cy+dy)),fill=core+(255,),width=1)
        for x,y in ((3,27),(13,26),(7,30.4),(10.4,19.3)):star(x,y+math.sin(phase+x)*.2,.4)
    elif name=='water':
        for k in range(11):
            x=8+math.sin(k*1.7+phase*.7)*5.3;y=2+(k*2.7+frame*.4)%28
            cx,cy=xy((x,y));r=1+k%2
            d.ellipse((cx-r,cy-r,cx+r,cy+r),outline=color+(255,),width=1)
            d.point((cx-1,cy-1),fill=core+(255,))
        for offset in (0,math.pi):
            points=[(8+math.sin(j/16*math.tau+phase+offset)*2.8,3+j*.85) for j in range(17)]
            line(points[2:8],1);line(points[10:16],1)
    elif name=='lightning':
        paths=([[(4.9,28.2),(6.1,27.5),(5.9,26.5),(8,25.8),(9.5,26.8),(10.9,28)],
                [(4.8,23.7),(2.8,24.4),(3.4,25.9),(1.7,26.4)],
                [(11.1,25.1),(13.5,23.8),(12.7,22.4),(14.1,21.6)],
                [(7.5,18.4),(5.4,17.4),(6,16.1),(4.4,15.5)]])
        for points in paths:
            line([(x+rng.choice((-.25,0,.25)),y+rng.choice((-.25,0,.25))) for x,y in points],1)
        for x,y in ((4.8,28),(11,28),(5,16)):star(x,y,.4)
    elif name=='light':
        for k in range(13):
            # Keep star tips inside the 0..32 native plane, including their arms.
            a=k*math.tau/13+phase*.2;r=4.9 if k%2 else 5.2
            x=8+math.sin(a)*r;y=25.3+math.cos(a)*r
            star(x,y,.25 if k%3 else .65)
        for k in range(6):
            x=5+(k*2.3)%6;y=3+(k*3.5+frame*.35)%17
            diamond(x,y,.28)
        for s in (-1,1):line([(8+s*4.7,22.7),(8+s*4.7,19.1)],1)
    elif name=='dark':
        for k in range(15):
            a=k*2.399+phase*.5;r=3.5+k%3*.8
            x=8+math.sin(a)*r;y=23+math.cos(a)*6.4
            cx,cy=xy((x,y));d.rectangle((cx,cy,cx+1+k%2,cy+2),fill=color+(255,))
        for s in (-1,1):
            points=[(8+s*(2.1+math.sin(j*.8+phase)*.7),13.3+j*.45) for j in range(12)]
            line(points[:5],1);line(points[7:],1)
        for x,y in ((9.1,25.7),(6.6,14.5),(10.5,14.5)):star(x,y,.5)
    return im


def java_display(name):
    display={'ground':{'translation':[0,3,0],'scale':[.4]*3},
             'fixed':{'rotation':[0,180,0],'translation':[0,-4,0],'scale':[.65]*3},
             'head':{'translation':[0,10,0],'scale':[.6]*3}}
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
            scale=FIRST_SCALES[name] if first else .9
            offset=rotate_xyz([0,(GRIPS[name]-8)*scale,0],effective)
            translation=[target[i]-offset[i] for i in range(3)]
            if left:translation[0]*=-1
            display[mode]={'rotation':r,'translation':[round(v,6) for v in translation],'scale':[scale]*3}
    return display


def java_model(w):
    elements=[]
    for c in w.cubes:
        e={k:c[k] for k in ('from','to')}
        e.update(name=c['name'],faces={f:{'uv':tile(c['mat']),'texture':'#body'} for f in FACES})
        if c['mat'] in EMISSIVE:e.update(shade=False,light_emission=15)
        elements.append(e)
    for axis,pos in (('z',3.25),('z',12.75),('x',7.95)):
        if axis=='z':a,b,faces=[-8,0,pos],[24,32,pos],('north','south')
        else:a,b,faces=[pos,0,-8],[pos,32,24],('east','west')
        elements.append({'from':a,'to':b,'shade':False,'light_emission':15,
                         'faces':{f:{'uv':[0,0,16,16],'texture':'#fx'} for f in faces}})
    return {'credit':'Evergarden / elemental_staff_assets.py','ambientocclusion':False,'gui_light':'front',
            'textures':{'body':'advance_magic:item/elemental_staffs/materials',
                        'fx':f'advance_magic:item/elemental_staffs/{w.name}_fx',
                        'particle':'advance_magic:item/elemental_staffs/materials'},
            'display':java_display(w.name),'elements':elements}


def bedrock_pose(first,name):
    pose=copy.deepcopy({'rotation':[152,-9,25],'position':[-7,-1.5,-5]} if first else
                       {'rotation':[97,-1.5,-49],'position':[1.5,-2.5,-10.5]})
    pose['position'][0]=f"context.item_slot == 'off_hand' ? {-pose['position'][0]} : {pose['position'][0]}"
    for axis in (1,2):pose['rotation'][axis]=f"context.item_slot == 'off_hand' ? {-pose['rotation'][axis]} : {pose['rotation'][axis]}"
    pose['scale']=[1,1,1]
    grip={'scale':[BEDROCK_SCALES[name] if first else .9]*3}
    if first:grip['position']=[0,BEDROCK_LIFTS[name],0]
    return {'loop':True,'bones':{'root':pose,'grip':grip}}


def bedrock_geometry(w,fx=False):
    if fx:return bedrock_glints(w)
    def point(p):return [round(8-p[0],4),round(p[1]-GRIPS[w.name]+12.5,4),round(p[2]-8,4)]
    bones=[{'name':'root','pivot':BEDROCK_PIVOT,'binding':'q.item_slot_to_bone_name(context.item_slot)'},
           {'name':'grip','parent':'root','pivot':BEDROCK_GRIP,'locators':{'hand_contact':BEDROCK_GRIP}}]
    groups={}
    for c in w.cubes:
        bone=c['bone']+('_glow' if c['mat'] in EMISSIVE else '')
        groups.setdefault(bone,[]).append(c)
    for bone,items in groups.items():
        cubes=[]
        for c in items:
            uv=[v*8 for v in tile(c['mat'])]
            cubes.append({'origin':point([c['to'][0],c['from'][1],c['from'][2]]),
                          'size':[round(c['to'][i]-c['from'][i],4) for i in range(3)],
                          'uv':{f:{'uv':uv[:2],'uv_size':[uv[2]-uv[0],uv[3]-uv[1]]} for f in FACES}})
        pivot_y=25.6 if bone.startswith(('focus','orbit')) else 30 if bone.startswith('halo') else 18.5
        bones.append({'name':bone,'parent':'grip','pivot':point([8,pivot_y,8]),'cubes':cubes})
    return {'format_version':'1.16.0','minecraft:geometry':[{
        'description':{'identifier':f'geometry.advance_magic.staff_{w.name}',
                       'texture_width':128,'texture_height':128,'visible_bounds_width':4,'visible_bounds_height':4,
                       'visible_bounds_offset':[0,1,0]},'bones':bones}]}


def bedrock_glints(w):
    """Three tiny opaque crown-local solids, never screen-sized sprite cards.

    The third mote is visible only in third person. These share the exact body
    root and hand-contact frame, and use the already shipped opaque palette.
    """
    def point(p):return [round(8-p[0],4),round(p[1]-GRIPS[w.name]+12.5,4),round(p[2]-8,4)]
    bones=[{'name':'root','pivot':BEDROCK_PIVOT,'binding':'q.item_slot_to_bone_name(context.item_slot)'},
           {'name':'grip','parent':'root','pivot':BEDROCK_GRIP,'locators':{'hand_contact':BEDROCK_GRIP}}]
    specs=((5.1,23.5,7.4,.22,.34,.24,'light'),
           (10.9,26.9,8.7,.24,.38,.22,'core'),
           (8.7,29.1,8.2,.20,.28,.20,'light'))
    for i,(x,y,z,sx,sy,sz,mat) in enumerate(specs,1):
        uv=[v*8 for v in tile(w.name+'_'+mat)]
        bones.append({'name':f'glint_{i}','parent':'grip','pivot':point([x,y,z]),'cubes':[{
            'origin':point([x+sx/2,y-sy/2,z-sz/2]),'size':[sx,sy,sz],
            'uv':{f:{'uv':uv[:2],'uv_size':[uv[2]-uv[0],uv[3]-uv[1]]} for f in FACES}}]})
    return {'format_version':'1.16.0','minecraft:geometry':[{
        'description':{'identifier':f'geometry.advance_magic.staff_{w.name}_fx',
                       'texture_width':128,'texture_height':128,'visible_bounds_width':4,'visible_bounds_height':4,
                       'visible_bounds_offset':[0,1,0]},'bones':bones}]}


def bedrock_glint_animation():
    return {f'glint_{i}':{'position':[0,f'math.sin(q.life_time * 90 + {(i-1)*120}) * 0.10',0]}
            for i in range(1,4)}


def bedrock_glint_controller():
    # Official Render Controller v1.8.0 supports ignore_lighting. Mojang's
    # copper_golem_eyes uses it for local light-independent detail. Opaque atlas
    # cubes need neither alpha blending nor alpha-as-emission interpretation.
    return {'geometry':'Geometry.fx','materials':[{'*':'Material.default'}],
            'rebuild_animation_matrices':True,
            'textures':['Texture.default'],'ignore_lighting':True,
            'part_visibility':[{'glint_1':'1.0'},{'glint_2':'1.0'},
                               {'glint_3':'context.is_first_person == 0.0'}]}


def vfx_metadata():
    return {'java':{'frames':8,'frame_ticks':2,'type':'animated cutout sheets'},
            'bedrock':{'type':'opaque crown-local solid glints','first_person_motes':2,
                       'third_person_motes':3,'max_mote_dimension':.38,'max_bob':.10,
                       'source_bounds':{'from':[4,21,5],'to':[12,30,11]},
                       'material':'entity_alphatest','ignore_lighting':True},
            'server_entities':0}


def retire_bedrock_effect_frames():
    """Remove only the exact generated legacy PNG names in this asset subtree."""
    bp=ART/'bedrock/textures/elemental_staffs'
    for name in IDS:
        for frame in range(8):(bp/f'{name}_fx_{frame}.png').unlink(missing_ok=True)


def bbmodel(w, texture):
    uid=lambda s:str(uuid.uuid5(uuid.NAMESPACE_URL,f'evergarden:elemental-staff/{w.name}/{s}'))
    groups=[];gy=GRIPS[w.name]
    for bone in sorted({c['bone'] for c in w.cubes}):
        py=25.6 if bone in ('focus','orbit') else 30 if bone=='halo' else 18.5 if bone in ('ribbon','flame','spiral') else gy
        groups.append({'name':bone,'uuid':uid(bone),'origin':[0,round(py-gy,4),0],
                       'rotation':[0,0,0],'export':True,'isOpen':True,'visibility':True,
                       'children':[uid(c['name']) for c in w.cubes if c['bone']==bone]})
    elements=[]
    for c in w.cubes:
        elements.append({'name':c['name'],'type':'cube','uuid':uid(c['name']),
                         'from':[round(c['from'][0]-8,4),round(c['from'][1]-gy,4),round(c['from'][2]-8,4)],
                         'to':[round(c['to'][0]-8,4),round(c['to'][1]-gy,4),round(c['to'][2]-8,4)],
                         'origin':[0,0,0],'rotation':[0,0,0],'shade':c['mat'] not in EMISSIVE,
                         'box_uv':False,'autouv':0,'rescale':False,'locked':False,'visibility':True,'export':True,
                         'faces':{f:{'uv':[v*8 for v in tile(c['mat'])],'texture':0} for f in FACES}})
    elements.append({'name':'hitbox','uuid':uid('hitbox_cube'),'type':'cube','from':[-.5,0,-.5],
                     'to':[.5,2,.5],'origin':[0,0,0],'faces':{}})
    animators={}
    for g in groups:
        name=g['name'];frames=[]
        if name=='focus':channel,axis,amount='position','y',.27
        elif name=='orbit':channel,axis,amount='rotation','y',12
        elif name=='halo':channel,axis,amount='rotation','y',15
        elif name=='flame':channel,axis,amount='scale','y',.10
        elif name in ('ribbon','spiral'):channel,axis,amount='rotation','z',3
        else:continue
        for t,v in ((0,0),(.75,amount),(1.5,0),(2.25,-amount),(3,0)):
            base=1 if channel=='scale' else 0
            data={a:str(base+v if a==axis else base) for a in ('x','y','z')}
            frames.append({'channel':channel,'data_points':[data],'uuid':uid(name+str(t)),
                           'time':t,'interpolation':'catmullrom'})
        animators[g['uuid']]={'name':name,'type':'bone','keyframes':frames}
    return {'meta':{'format_version':'4.10','model_format':'free','box_uv':False},
            'name':f'elemental_staff_{w.name}','resolution':{'width':128,'height':128},'elements':elements,
            'outliner':groups+[{'name':'hitbox','uuid':uid('hitbox'),'origin':[0,1,0],
                                'children':[uid('hitbox_cube')],'export':True}],
            'textures':[{'name':'elemental_staff_materials.png','namespace':'','folder':'','path':'',
                         'id':'0','uuid':uid('texture'),'width':128,'height':128,'uv_width':128,'uv_height':128,
                         'source':'data:image/png;base64,'+base64.b64encode(texture).decode()}],
            'animations':[{'uuid':uid('idle'),'name':'idle','loop':'loop','length':3,'override':False,
                           'anim_time_update':'','blend_weight':'','start_delay':'','loop_delay':'','animators':animators}]}


def preview_staff(name):
    """Compatible with legendary_weapon_preview's existing textured renderer."""
    w=make_staff(name);tex=atlas()
    for c in w.cubes:
        c['preview_uv']=[v*8 for v in tile(c['mat'])]
        c['preview_texture']=tex
        c['mat']='white' if c['mat'] in EMISSIVE else 'gold'
    return w


def _idle(w):
    groups={c['bone']+('_glow' if c['mat'] in EMISSIVE else '') for c in w.cubes}
    bones=bedrock_glint_animation()
    for bone in groups:
        source=bone.removesuffix('_glow')
        if source=='focus':bones[bone]={'position':[0,'math.sin(q.life_time * 120) * 0.27',0]}
        elif source=='orbit':bones[bone]={'rotation':[0,'math.sin(q.life_time * 120) * 12',0]}
        elif source=='halo':bones[bone]={'rotation':[0,'q.life_time * 22',0]}
        elif source=='ribbon':bones[bone]={'rotation':[0,0,'math.sin(q.life_time * 120) * 3']}
        elif source=='flame':bones[bone]={'scale':[1,'1 + math.sin(q.life_time * 360) * 0.10',1]}
        elif source=='spiral':bones[bone]={'rotation':[0,0,'math.sin(q.life_time * 120) * 3']}
    return {'loop':True,'animation_length':3,'bones':bones}


def generate():
    from legendary_weapon_preview import render_icon_frames
    java=ART/'java';bedrock=ART/'bedrock';ART.mkdir(parents=True,exist_ok=True)
    tex=atlas();stream=io.BytesIO();tex.save(stream,format='PNG');texture=stream.getvalue()
    jp=java/'assets/advance_magic/textures/item/elemental_staffs';jp.mkdir(parents=True,exist_ok=True)
    bp=bedrock/'textures/elemental_staffs';bp.mkdir(parents=True,exist_ok=True)
    # Body emission subtly breathes on Java, while Bedrock uses emissive materials.
    strip=Image.new('RGBA',(128,1024))
    for f in range(8):strip.paste(atlas(f),(0,f*128))
    strip.save(jp/'materials.png');tex.save(ART/'materials.png');(bp/'materials.png').write_bytes(texture)
    write_json(jp/'materials.png.mcmeta',{'animation':{'frametime':3,'interpolate':False,'width':128,'height':128}})
    animations={};controllers={};item_atlas={};definitions={'minecraft:carrot_on_a_stick':[]};records=[]
    for name,title,color in zip(IDS,TITLES,COLORS):
        w=make_staff(name)
        write_json(ART/f'{name}.geometry.json',{'id':name,'grip':[8,GRIPS[name],8],'cubes':w.cubes})
        source=bbmodel(w,texture)
        write_json(ART/f'elemental_staff_{name}.bbmodel',source)
        write_json(ART/f'blockbench/elemental_staff_{name}.bbmodel',source)
        write_json(java/f'assets/advance_magic/models/item/elemental_staffs/{name}.json',java_model(w))
        frames=[fx_frame(name,f) for f in range(8)]
        fxstrip=Image.new('RGBA',(128,1024))
        for f,im in enumerate(frames):fxstrip.paste(im,(0,f*128))
        fxstrip.save(jp/f'{name}_fx.png')
        write_json(jp/f'{name}_fx.png.mcmeta',{'animation':{'frametime':2,'interpolate':False,'width':128,'height':128}})
        icons=render_icon_frames(preview_staff(name),tex,frames)
        iconstrip=Image.new('RGBA',(128,1024))
        for f,im in enumerate(icons):iconstrip.paste(im,(0,f*128))
        iconstrip.save(jp/f'{name}_icon.png')
        write_json(jp/f'{name}_icon.png.mcmeta',{'animation':{'frametime':2,'interpolate':False,'width':128,'height':128}})
        icons[0].save(bp/f'{name}_icon.png');icons[0].save(ART/f'{name}_icon.png')
        write_json(java/f'assets/advance_magic/models/item/elemental_staffs/{name}_icon.json',{
            'parent':'minecraft:item/generated','textures':{'layer0':f'advance_magic:item/elemental_staffs/{name}_icon'}})
        write_json(java/f'assets/advance_magic/items/staff_{name}.json',{'model':{
            'type':'minecraft:select','property':'minecraft:display_context',
            'cases':[{'when':'gui','model':{'type':'minecraft:model','model':f'advance_magic:item/elemental_staffs/{name}_icon'}}],
            'fallback':{'type':'minecraft:model','model':f'advance_magic:item/elemental_staffs/{name}'}}})
        write_json(bedrock/f'models/entity/elemental_staffs/{name}.geo.json',bedrock_geometry(w))
        write_json(bedrock/f'models/entity/elemental_staffs/{name}_fx.geo.json',bedrock_geometry(w,True))
        prefix=f'animation.advance_magic.staff_{name}'
        animations[prefix+'.first']=bedrock_pose(True,name)
        animations[prefix+'.third']=bedrock_pose(False,name)
        animations[prefix+'.idle']=_idle(w)
        attach={'identifier':f'advance_magic:staff_{name}',
                'materials':{'default':'entity_alphatest','glow':'entity_emissive_alpha'},
                'textures':{'default':'textures/elemental_staffs/materials'},
                'geometry':{'default':f'geometry.advance_magic.staff_{name}','fx':f'geometry.advance_magic.staff_{name}_fx'},
                'animations':{'first':prefix+'.first','third':prefix+'.third','idle':prefix+'.idle'},
                'scripts':{'animate':[{'first':'context.is_first_person == 1.0'},
                                      {'third':'context.is_first_person == 0.0'},'idle']},
                'render_controllers':['controller.render.advance_magic.elemental_staff_body',
                                      f'controller.render.advance_magic.staff_{name}_fx']}
        write_json(bedrock/f'attachables/elemental_staffs/{name}.json',{'format_version':'1.10.0','minecraft:attachable':{'description':attach}})
        controllers[f'controller.render.advance_magic.staff_{name}_fx']=bedrock_glint_controller()
        icon=f'advance_magic.staff_{name}';item_atlas[icon]={'textures':f'textures/elemental_staffs/{name}_icon'}
        definitions['minecraft:carrot_on_a_stick'].append({'type':'definition','model':f'advance_magic:staff_{name}',
            'bedrock_identifier':f'advance_magic:staff_{name}','display_name':title,
            'bedrock_options':{'icon':icon,'allow_offhand':True,'display_handheld':True,
                               'creative_category':'equipment'}})
        records.append({'id':name,'title':title,'model_key':f'advance_magic:staff_{name}',
                        'blueprint_id':f'elemental_staff_{name}','modelengine':f'elemental_staff_{name}.bbmodel',
                        'grip':[8,GRIPS[name],8],'cuboids':len(w.cubes),'color':color,'description':DESCRIPTIONS[name]})
    controllers['controller.render.advance_magic.elemental_staff_body']={
        'geometry':'Geometry.default','materials':[{'*':'Material.default'},
            *[{bone+'_glow':'Material.glow'} for bone in ('staff','crown','focus','orbit','filigree','ribbon','flame','halo','spiral')]],
        'textures':['Texture.default']}
    write_json(bedrock/'animations/elemental_staffs.animation.json',{'format_version':'1.8.0','animations':animations})
    write_json(bedrock/'render_controllers/elemental_staffs.render.json',{'format_version':'1.8.0','render_controllers':controllers})
    metadata={'format':1,'generator':'tools/elemental_staff_assets.py','modelengine_version':'4.1.1',
              'base_item':'minecraft:carrot_on_a_stick','ordinary_cycle':['fire','wind','earth','ice','water'],
              'special_cycle':['lightning','light','dark'],'items':records,'item_atlas':item_atlas,'definitions':definitions,
              'vfx':vfx_metadata(),
              'grip_frame':{'bedrock_root':BEDROCK_PIVOT,'bedrock_contact':BEDROCK_GRIP,
                            'method':'native trident binding; child uniformly scales about real hand contact; independent first-person camera framing'},
              'camera_fit':{'method':'existing Longinus screenshot-calibrated affine projection; approximate, not a real client render',
                            'frame':[1160,500],'solid_y_bounds':[50,450],
                            'projection':[[13,-3],[-11.68,-18.42],[2.5,1.5]]}}
    write_json(ART/'metadata.json',metadata)
    retire_bedrock_effect_frames()
    return metadata


def refresh_bedrock_effects():
    """Repair Bedrock only, keeping approved Java/body/blueprint bytes intact."""
    if not (ART/'metadata.json').is_file():raise FileNotFoundError('Generate initial assets first')
    protected=list((ART/'java').rglob('*'))+list(ART.glob('*.bbmodel'))+list((ART/'blockbench').glob('*.bbmodel'))
    protected += [ART/f'bedrock/models/entity/elemental_staffs/{name}.geo.json' for name in IDS]
    protected += [ART/f'bedrock/textures/elemental_staffs/{name}_icon.png' for name in IDS]
    protected += [ART/'bedrock/textures/elemental_staffs/materials.png']
    hashes={p:hashlib.sha256(p.read_bytes()).hexdigest() for p in protected if p.is_file()}
    bp=ART/'bedrock'
    animation_path=bp/'animations/elemental_staffs.animation.json'
    controller_path=bp/'render_controllers/elemental_staffs.render.json'
    animations=json.loads(animation_path.read_text(encoding='utf8'))
    controllers=json.loads(controller_path.read_text(encoding='utf8'))
    for name in IDS:
        w=make_staff(name)
        write_json(bp/f'models/entity/elemental_staffs/{name}_fx.geo.json',bedrock_glints(w))
        attach_path=bp/f'attachables/elemental_staffs/{name}.json'
        attach=json.loads(attach_path.read_text(encoding='utf8'))
        description=attach['minecraft:attachable']['description']
        description['textures']={'default':description['textures']['default']}
        write_json(attach_path,attach)
        idle=animations['animations'][f'animation.advance_magic.staff_{name}.idle']['bones']
        idle.pop('aura',None)
        idle.update(bedrock_glint_animation())
        controllers['render_controllers'][f'controller.render.advance_magic.staff_{name}_fx']=bedrock_glint_controller()
    write_json(animation_path,animations);write_json(controller_path,controllers)
    metadata=json.loads((ART/'metadata.json').read_text(encoding='utf8'))
    metadata['vfx']=vfx_metadata();write_json(ART/'metadata.json',metadata)
    retire_bedrock_effect_frames()
    assert all(p.is_file() and hashlib.sha256(p.read_bytes()).hexdigest()==digest for p,digest in hashes.items()),'Protected asset changed'
    print('Bedrock effects refreshed; preserved',len(hashes),'Java/body/blueprint/icon/material files byte-for-byte')
    return metadata


def register(java,bedrock,item_atlas,definitions):
    """Merge generated native assets into pack builder paths and Geyser catalogs."""
    if not (ART/'metadata.json').is_file():generate()
    metadata=json.loads((ART/'metadata.json').read_text(encoding='utf8'))
    for source,destination in ((ART/'java',java),(ART/'bedrock',bedrock)):
        for path in source.rglob('*'):
            if path.is_file():
                target=destination/path.relative_to(source);target.parent.mkdir(parents=True,exist_ok=True)
                shutil.copyfile(path,target)
    item_atlas.update(metadata['item_atlas'])
    for base,items in metadata['definitions'].items():
        entries=definitions.setdefault(base,[])
        for entry in items:
            entries[:]=[old for old in entries if old.get('model')!=entry['model']]
            entries.append(entry)


if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bedrock-only',action='store_true',help='Refresh Bedrock effects without writing any Java/body/blueprint assets')
    args=parser.parse_args()
    metadata=refresh_bedrock_effects() if args.bedrock_only else generate()
    print('Generated',len(metadata['items']),'elemental staff models;',sum(v['cuboids'] for v in metadata['items']),'solid cuboids')
    print(ART)
