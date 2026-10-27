"""Authored 20 Rare Adventurer Weapons: 3D cuboids, Java/Bedrock packs, Geyser v2 & ModelEngine 4.1.1.

Covers Adventurer Weapons I-IV (20 weapons across Sword, Spear, Mace, Bow, Crossbow).
Includes 3-stage bow pulling and 3-stage crossbow pulling + charged arrow/rocket models
on both Java Edition and Bedrock Edition (via Geyser attachables).

Melee weapons are sculpted with tools/weapon_forge.py (bevelled reliefs and
voxel volumes) and held on Bedrock through its java2bedrock rig, so both
editions use the same display transform.
"""
from __future__ import annotations

import base64
import copy
import io
import json
import math
from pathlib import Path
import random
import re
import sys
import uuid
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from weapon_forge import (Forge, band, bedrock_cube, bedrock_point, circle, cone_mask, edges, inside, mirrored,  # noqa: E402
                          profile, rig_animation, rig_bones, segment_distance, tile_pixels)

EVERGARDEN = ROOT / 'evergarden'
ART = EVERGARDEN / 'art/adventurer-weapons'
PREVIEWS = ROOT / 'previews/adventurer-weapons'

# (id, title, subtitle, thai_title, base_material, category, sanctum, grip_y, rgb_color)
CATALOG = (
    # Sheet I
    ('emberfang', 'Emberfang', 'The Blade of the Ashen Hunt', 'ดาบเขี้ยวถ่านเพลิง', 'netherite_sword', 'sword', 'dark', 4.75, (255, 96, 40)),
    ('stormpiercer', 'Stormpiercer', 'The Lancer of Open Skies', 'หอกทะลวงพายุฟ้า', 'netherite_spear', 'spear', 'astral', 9.0, (92, 198, 255)),
    ('ironbloom', 'Ironbloom', 'The Axe of the Wild Forge', 'ขวานศึกบุปผาเหล็กไหล', 'mace', 'mace', 'time', 7.0, (112, 232, 104)),
    ('frostveil', 'Frostveil', 'The Dagger of Silent Winter', 'กริชม่านเหมันต์', 'netherite_sword', 'sword', 'astral', 4.4, (116, 234, 255)),
    ('sunflare', 'Sunflare', 'The Morningstar of Dawn', 'ค้อนดารารุ่งอรุณ', 'mace', 'mace', 'time', 4.9, (255, 196, 68)),
    # Sheet II
    ('moonrift', 'Moonrift', 'The Saber of Dusk Trails', 'ดาบโค้งผ่าจันทรา', 'netherite_sword', 'sword', 'dark', 4.7, (194, 108, 255)),
    ('bramblepike', 'Bramblepike', 'The Halberd of the Green March', 'ทวนหนามพงไพร', 'netherite_spear', 'spear', 'time', 8.7, (118, 236, 96)),
    ('tidehook', 'Tidehook', 'The Trident of the Deep Run', 'ตรีศูลคลื่นสมุทร', 'netherite_spear', 'spear', 'astral', 9.0, (68, 212, 255)),
    ('ashcleaver', 'Ashcleaver', 'The Greatsword of Black Trails', 'ดาบใหญ่ผ่าเถ้าถ่าน', 'netherite_sword', 'sword', 'dark', 5.6, (255, 108, 52)),
    ('glimmermaul', 'Glimmermaul', 'The Hammer of Bright Steel', 'ค้อนศึกเหล็กประกายแสง', 'mace', 'mace', 'time', 5.1, (255, 224, 92)),
    # Sheet III
    ('thornwhisper', 'Thornwhisper', 'The Bow of Hidden Groves', 'ธนูเสียงกระซิบพงหนาม', 'bow', 'bow', 'time', 10.0, (116, 240, 108)),
    ('runelock', 'Runelock', 'The Crossbow of Arcane Hunts', 'หน้าไม้อักขระมนตรา', 'crossbow', 'crossbow', 'astral', 4.5, (88, 188, 255)),
    ('mistcaller', 'Mistcaller', 'The Staff of Veiled Paths', 'ทวนสายหมอกเร้นลับ', 'netherite_spear', 'spear', 'astral', 9.1, (116, 244, 236)),
    ('silvershiv', 'Silvershiv', 'The Knife of Quiet Steps', 'มีดสั้นเงาจันทร์สีเงิน', 'netherite_sword', 'sword', 'dark', 4.0, (206, 136, 255)),
    ('stoneheart', 'Stoneheart', 'The Mace of the Iron Hold', 'กระบองศิลาหัวใจเพลิง', 'mace', 'mace', 'dark', 5.0, (255, 116, 64)),
    # Sheet IV
    ('nightbloom', 'Nightbloom', 'The Scythe of Shadow Fields', 'เคียวบุปผาราตรี', 'netherite_spear', 'spear', 'dark', 9.2, (244, 88, 208)),
    ('ironwave', 'Ironwave', 'The Glaive of Tidal Steel', 'ง้าวคลื่นเหล็กสมุทร', 'netherite_spear', 'spear', 'astral', 8.8, (76, 196, 255)),
    ('starfang', 'Starfang', 'The Rapier of Bright Dusk', 'ดาบเรเปียร์เขี้ยวดารา', 'netherite_sword', 'sword', 'astral', 4.9, (255, 214, 102)),
    ('rootspire', 'Rootspire', 'The Club of the Old Warden', 'กระบองรากไม้ผู้พิทักษ์', 'mace', 'mace', 'time', 4.9, (124, 236, 100)),
    ('voidreel', 'Voidreel', 'The Chakram of Midnight Wind', 'กงจักรสายลมรัตติกาล', 'netherite_sword', 'sword', 'dark', 14.0, (186, 96, 255)),
)

IDS = tuple(row[0] for row in CATALOG)
BY_ID = {row[0]: row for row in CATALOG}

# 16px tiles in a 128x128 atlas (up to 64 tiles in an 8x8 grid).
PALETTE = {
    'gold_dark': '#7c4d21', 'gold': '#d89e38', 'gold_light': '#ffe59c',
    'bronze_dark': '#5e3922', 'bronze': '#a86938', 'bronze_light': '#dfa166',
    'silver_dark': '#667a99', 'silver': '#b8c8e3', 'silver_light': '#eef7ff',
    'steel_dark': '#2e384b', 'steel': '#58667d', 'steel_light': '#96a5bf',
    'obsidian': '#231d2c', 'obsidian_light': '#4f3f58', 'obsidian_edge': '#876b8c',
    'stone_dark': '#3b3936', 'stone': '#69655f', 'stone_light': '#9c968d',
    'wood_dark': '#3b2618', 'wood': '#6e492d', 'wood_light': '#a6764b',
    'leather': '#573928', 'leather_light': '#8f6240', 'black': '#151824',
    'vine_dark': '#225225', 'vine': '#3f8c3a', 'leaf_light': '#7cd957',
    'ember_dark': '#6e1616', 'ember': '#c92a1d', 'ember_light': '#ff6b21', 'ember_core': '#ffda75',
    'sky_dark': '#144480', 'sky': '#2482de', 'sky_light': '#5ecaff', 'sky_core': '#dbf8ff',
    'frost_dark': '#1b5b75', 'frost': '#35b4d4', 'frost_light': '#7ef2ff', 'frost_core': '#ebffff',
    'nature_dark': '#1d592b', 'nature': '#33a64a', 'nature_light': '#6ef062', 'nature_core': '#dfffd4',
    'void_dark': '#391b61', 'void': '#7c3aed', 'void_light': '#c268ff', 'void_core': '#f4d9ff',
    'magenta': '#b32285', 'magenta_light': '#ff59c4',
    'sun_dark': '#995017', 'sun': '#ed9121', 'sun_light': '#ffd844', 'sun_core': '#fffbe0',
    'cloth_red': '#9e192f', 'cloth_red_light': '#e03848',
    'cloth_blue': '#1b569e', 'cloth_blue_light': '#4298f5',
    'cloth_purple': '#5a248a', 'cloth_purple_light': '#9c4de0',
    'string_light': '#d9f2e6', 'white': '#f7ffff', 'leather_dark': '#35231a',
}
MATS = tuple(PALETTE)
assert len(MATS) <= 64, f'Palette fits 8x8 16px atlas: {len(MATS)}'

EMISSIVE = {
    'ember_light', 'ember_core',
    'sky_light', 'sky_core',
    'frost_light', 'frost_core',
    'nature_light', 'nature_core',
    'void_light', 'void_core',
    'magenta_light',
    'sun_light', 'sun_core',
    'string_light', 'white',
}

FACES = ('north', 'south', 'east', 'west', 'up', 'down')


def write_json(path: Path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf8')


def atlas() -> Image.Image:
    im = Image.new('RGBA', (128, 128))
    rng = random.Random(90210)
    for i, (name, code) in enumerate(PALETTE.items()):
        rows = tile_pixels(tuple(bytes.fromhex(code[1:])), rng, name in EMISSIVE)
        for y, row in enumerate(rows):
            for x, pixel in enumerate(row):
                im.putpixel(((i % 8) * 16 + x, (i // 8) * 16 + y), pixel)
    return im


def tile(mat: str) -> list[int]:
    i = MATS.index(mat)
    return [(i % 8) * 2, (i // 8) * 2, (i % 8) * 2 + 2, (i // 8) * 2 + 2]


def rotate_xyz(point, angles):
    x, y, z = point
    rx, ry, rz = map(math.radians, angles)
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    return [x, y, z]


class Sculpt(Forge):
    """Forge primitives for one adventurer weapon."""


# -- melee sculpts ------------------------------------------------------------
def leaf(cx, cy, length, width, angle):
    """A pointed leaf outline from its stem at (cx, cy) along `angle` (degrees)."""
    a = math.radians(angle)
    t, n = np.array([math.cos(a), math.sin(a)]), np.array([-math.sin(a), math.cos(a)])
    pts = []
    for s in np.linspace(0, 1, 9):
        pts.append(np.array([cx, cy]) + t * length * s + n * width * math.sin(math.pi * s) ** .8)
    for s in np.linspace(1, 0, 9)[1:-1]:
        pts.append(np.array([cx, cy]) + t * length * s - n * width * math.sin(math.pi * s) ** .8)
    return [tuple(p) for p in pts]


def emberfang(w):
    w.relief(mirrored([(8, 2.55), (9.2, 2.25), (9.6, 1.2), (9.05, .2), (8, -.3)]), [(0, .78, 'gold_dark'), (.28, .95, 'gold')])
    w.jewel(8, 1.15, .72, 'gold_light', 'ember', 'ember_core', depth=1.2, shape='diamond')
    w.rod(2.45, 2.85, 1.45, 'gold')
    w.wrap(2.85, 6.7, 1.0, 'obsidian', ['ember_dark', 'leather_light'])
    w.rod(6.65, 7.05, 1.45, 'gold')
    guard = [(8, 9.75), (8.9, 9.5), (9.8, 9.02), (10.9, 8.98), (12.15, 9.55), (12.95, 10.6), (13.15, 11.65),
             (13.65, 10.45), (13.55, 9.2), (12.8, 8.15), (11.5, 7.55), (9.9, 7.5), (8.8, 7.1), (8, 6.75)]
    w.relief(mirrored(guard), [(0, .9, 'gold_dark'), (.3, 1.08, 'gold'), (.62, 1.18, 'gold_light')])
    w.jewel(8, 8.35, .95, 'gold_light', 'ember', 'ember_core', depth=1.45)

    def half(y):
        # A flamberge: mirrored waves that settle into a clean point.
        base = 1.55 - (y - 9.4) * .016
        wave = .42 * math.sin((y - 9.4) * math.tau / 3.6)
        return max(0.0, (base + wave) * min(1.0, (31.2 - y) / 3.8) ** .85)
    blade = inside(profile(half, 9.3, 31.2))
    core = lambda X, Y: blade(X, Y) & (np.abs(X - 8) < .34) & (Y > 10.4) & (Y < 27.6)
    w.relief(lambda X, Y: blade(X, Y) & ~core(X, Y), [(0, .26, 'steel_light'), (.34, .5, 'steel_dark'), (.72, .55, 'obsidian')])
    w.relief(core, [(0, .46, 'ember'), (.2, .52, 'ember_light')])
    for y in (13.1, 18.0, 22.9):
        w.box(7.8, y, 7.42, 8.2, y + .5, 8.58, 'ember_core')


def stormpiercer(w):
    w.jewel(8, .95, .78, 'gold', 'sky', 'sky_core', depth=1.0, shape='diamond')
    w.rod(1.6, 2.2, 1.25, 'gold')
    w.rod(2.2, 20.4, .84, 'sky_dark')
    w.wrap(7.0, 11.0, .86, 'steel_dark', ['silver', 'sky'])
    for y in (2.2, 6.55, 11.05, 14.8, 17.6):
        w.rod(y, y + .45, 1.18, 'gold')
    w.rod(19.3, 20.8, 1.08, 'gold_dark')
    # Swept "ears" rise from the socket like folded wings, thinner than the collar.
    for side in (1, -1):
        ear = [(8 + side * x, y) for x, y in ((.7, 20.15), (2.0, 20.3), (3.4, 20.95), (4.55, 22.2), (5.0, 23.75),
                                              (4.15, 22.9), (3.05, 22.25), (1.9, 22.0), (.7, 21.85))]
        w.relief(ear, [(0, .3, 'silver_light'), (.3, .44, 'silver'), (.6, .5, 'sky')])
    w.relief(mirrored([(8, 22.0), (9.05, 21.8), (9.5, 21.0), (9.2, 20.0), (8, 19.7)]),
             [(0, .78, 'gold_dark'), (.26, .92, 'gold')])
    w.jewel(8, 20.85, .72, 'gold_light', 'sky', 'sky_core', depth=1.2)

    def half(y):
        if y < 23.6:
            return .72 + max(0.0, y - 21.8) * .55
        return 1.71 * max(0.0, 1 - (y - 23.6) / 8.0) ** .85
    w.relief(profile(half, 21.6, 31.6), [(0, .26, 'silver_light'), (.36, .48, 'silver'), (.72, .56, 'sky'), (1.0, .6, 'sky_light')])
    w.relief(band([(8.6, 19.5), (9.8, 18.2), (10.75, 16.5), (11.15, 14.6), (10.9, 12.7)], [.4, .52, .58, .58, .55], notch=.7),
             [(0, .18, 'cloth_blue'), (.24, .23, 'cloth_blue_light')], 'ribbon', 8.85)


def ironbloom(w):
    w.jewel(8, 1.0, .82, 'gold', 'nature', 'nature_core', depth=1.05, shape='diamond')
    w.rod(1.7, 2.3, 1.35, 'gold')
    w.rod(2.3, 24.6, 1.05, 'wood_dark')
    w.wrap(4.3, 9.7, 1.08, 'leather', ['leather_light', 'leather_dark'])
    for y in (2.3, 9.9, 13.6):
        w.rod(y, y + .45, 1.38, 'gold')
    turns = np.linspace(0, 3 * math.tau, 46)
    w.tube([(8 + .66 * math.cos(t), y, 8 + .66 * math.sin(t)) for t, y in zip(turns, np.linspace(14.2, 16.9, 46))], .2, 'vine')
    w.rod(16.8, 22.4, 1.62, 'bronze_dark')
    for y in (16.8, 21.95):
        w.rod(y, y + .45, 1.9, 'gold')
    # Bearded crescent bits: narrow at the eye, flaring to a curved edge.
    for side in (1, -1):
        bit = [(8 + side * x, y) for x, y in ((.7, 21.2), (1.9, 21.45), (3.3, 22.4), (4.6, 23.95), (5.55, 24.6),
                                              (5.95, 22.6), (6.05, 20.0), (5.75, 17.4), (5.2, 15.3), (4.25, 16.1),
                                              (3.0, 17.15), (1.8, 17.75), (.7, 17.8))]
        w.relief(bit, [(0, .3, 'silver_light'), (.38, .54, 'steel_light'), (1.3, .62, 'steel')])
        w.relief(leaf(8 + side * 1.35, 19.5, 3.3, .78, 90 - side * 90), [(0, .66, 'gold'), (.2, .7, 'gold_light')])
        w.stroke([(8 + side * 1.7, 19.5), (8 + side * 4.25, 19.5)], .2, 7.26, 8.74, 'nature_light')
    w.jewel(8, 19.5, .9, 'gold_light', 'nature', 'nature_core', depth=1.3)
    w.relief(mirrored([(8, 28.6), (8.55, 27.4), (9.0, 25.6), (8.95, 23.9), (8.6, 22.4), (8, 22.3)]),
             [(0, .3, 'gold_light'), (.3, .46, 'gold'), (.6, .5, 'nature_light')])


def frostveil(w):
    w.relief(mirrored([(8, 2.35), (9.0, 1.95), (9.3, 1.0), (8.6, .05), (8, -.3)]), [(0, .72, 'silver_dark'), (.26, .88, 'silver')])
    w.jewel(8, 1.05, .6, 'silver_light', 'frost', 'frost_core', depth=1.15, shape='diamond')
    w.rod(2.3, 2.65, 1.35, 'silver_light')
    w.wrap(2.65, 6.1, .95, 'steel_dark', ['silver', 'frost_dark'])
    guard = [(8, 8.6), (9.2, 8.25), (10.6, 7.95), (11.6, 8.4), (12.6, 9.5), (12.45, 8.05), (12.95, 7.15),
             (11.7, 7.25), (10.4, 6.95), (9.1, 6.5), (8, 6.15)]
    w.relief(mirrored(guard), [(0, .85, 'silver_dark'), (.28, 1.0, 'silver'), (.55, 1.08, 'silver_light')])
    w.jewel(8, 7.32, .78, 'silver_light', 'frost', 'frost_core', depth=1.35)

    def half(y):
        t = (y - 8.2) / 12.8
        return max(0.0, (1.0 + .62 * math.sin(min(t, 1) * math.pi * .85)) * (1 - t) ** .55)
    w.relief(profile(half, 8.2, 21.0), [(0, .24, 'frost_light'), (.3, .44, 'frost'), (.72, .5, 'frost_dark')])
    w.stroke([(8, 9.0), (8, 18.6)], .3, 7.46, 8.54, 'frost_core')
    for side in (1, -1):
        shard = [(8 + side * 1.2, 8.35), (8 + side * 2.15, 8.55), (8 + side * 2.4, 10.7), (8 + side * 1.75, 9.65)]
        w.relief(shard, [(0, .3, 'frost_light'), (.18, .38, 'frost_core')])


def sunflare(w):
    w.jewel(8, 1.0, .8, 'gold', 'sun', 'sun_core', depth=1.0, shape='diamond')
    w.rod(1.7, 2.2, 1.35, 'gold')
    w.wrap(2.2, 7.6, 1.05, 'leather', ['leather_light', 'gold_dark'])
    w.rod(7.55, 18.4, .95, 'silver_dark')
    for y in (7.55, 10.6, 13.6, 16.4):
        w.rod(y, y + .45, 1.3, 'gold')
    w.relief(mirrored([(8, 19.95), (9.3, 19.65), (9.75, 18.9), (9.1, 18.35), (8, 18.1)]), [(0, .95, 'gold_dark'), (.3, 1.1, 'gold')])
    centre = np.array([8, 23.35, 8])
    spikes = [np.array(d, dtype=float) / np.linalg.norm(d) for d in (
        (1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0), (1, 1, 0), (-1, 1, 0), (0, 1, 1), (0, 1, -1),
        (1, 0, 1), (1, 0, -1), (-1, 0, 1), (-1, 0, -1))]

    def head(X, Y, Z):
        d = np.sqrt((X - 8) ** 2 + (Y - 23.35) ** 2 + (Z - 8) ** 2)
        ball = d <= 3.2
        tips = np.zeros(X.shape, dtype=bool)
        steel = np.zeros(X.shape, dtype=bool)
        for s in spikes:
            steel |= cone_mask(X, Y, Z, centre + s * 2.6, s, 2.6, .78, .3)
            tips |= cone_mask(X, Y, Z, centre + s * 5.1, s, .95, .32, .05)
        return [('sun_light', tips), ('silver_light', steel & ~ball), ('gold_dark', ball & (np.abs(Y - 23.35) <= .3)),
                ('gold_light', ball & (Y > 25.7)), ('gold', ball)]
    w.volume(head, (1.4, 16.8, 1.4), (14.6, 30.4, 14.6))
    w.jewel(8, 23.35, 1.15, 'gold_light', 'sun', 'sun_core', depth=3.42, shape='round')


def moonrift(w):
    w.relief([(7.8, 2.55), (6.6, 2.2), (6.3, 1.0), (7.1, .05), (8.4, -.1), (7.6, .6), (7.25, 1.4), (7.9, 2.1)],
             [(0, .7, 'gold_dark'), (.24, .84, 'gold')])
    w.jewel(7.9, 1.25, .5, 'gold_light', 'void', 'void_core', depth=1.05, shape='round')
    w.rod(2.45, 2.85, 1.4, 'gold')
    w.wrap(2.85, 6.75, .98, 'obsidian', ['void_dark', 'gold_dark'])
    # Knuckle bow on the cutting side (-X), which faces down and forward in the hand.
    w.stroke([(4.4, 8.3), (3.65, 6.9), (3.45, 5.1), (3.9, 3.5), (5.1, 2.35), (6.8, 2.25)], .48, 7.62, 8.38, 'gold')
    guard = [(11.4, 9.25), (9.8, 9.35), (8.0, 9.4), (6.1, 9.15), (4.6, 8.75), (3.8, 7.8), (4.9, 8.05), (6.4, 8.25),
             (8.0, 8.0), (9.5, 7.8), (10.8, 7.5), (11.7, 7.95), (11.85, 8.85)]
    w.relief(guard, [(0, .88, 'gold_dark'), (.28, 1.02, 'gold')])
    w.jewel(8.0, 8.7, .74, 'gold_light', 'void', 'void_core', depth=1.32, shape='round')
    curve = lambda y: .0034 * max(0.0, y - 9.4) ** 2
    tip = (8.75 + curve(29.3), 31.3)

    def edge(y):
        # Convex cutting edge on -X with a slight swell (yelman) before the point.
        x = 6.95 + curve(y) - .3 * math.exp(-((y - 25.0) / 2.6) ** 2)
        if y > 27.6:
            x = tip[0] + (x - tip[0]) * max(0.0, (tip[1] - y) / (tip[1] - 27.6)) ** .75
        return x

    def back(y):
        return min(8.75 + curve(y), tip[0]) if y < 29.3 else tip[0] - (y - 29.3) * .12
    blade = inside(edges(edge, back, 9.3, tip[1]))
    # The thick spine follows the inside of the curve; the cutting half stays thin and bright.
    spine = lambda X, Y: blade(X, Y) & (X > 7.95 + .0034 * np.clip(Y - 9.4, 0, None) ** 2)
    w.relief(lambda X, Y: blade(X, Y) & ~spine(X, Y), [(0, .24, 'silver_light'), (.3, .42, 'silver')])
    w.relief(spine, [(0, .5, 'silver_dark'), (.3, .56, 'steel_light')])
    w.stroke([(edge(y) + .36, y) for y in np.arange(10.2, 29.0, .5)], .3, 7.6, 8.4, 'void_light')


def bramblepike(w):
    w.relief([(8, -.45), (9.0, 1.65), (7.0, 1.65)], [(0, .55, 'bronze_dark'), (.2, .66, 'bronze')])
    w.rod(1.6, 2.1, 1.25, 'bronze')
    w.rod(2.1, 21.4, .9, 'wood_dark')
    w.wrap(6.6, 10.8, .92, 'leather', ['leather_light', 'vine_dark'])
    turns = np.linspace(0, 3.5 * math.tau, 70)
    path = [(8 + .6 * math.cos(t), y, 8 + .6 * math.sin(t)) for t, y in zip(turns, np.linspace(11.4, 20.4, 70))]
    w.tube(path, .19, 'vine')
    for k in range(0, 70, 9):
        x, y, z = path[k]
        w.cone((x, y, z), (x - 8, .35, z - 8), .7, .16, 'leaf_light', tip_radius=.03)
    w.rod(20.4, 23.0, 1.28, 'bronze_dark')
    for y in (20.4, 22.55):
        w.rod(y, y + .45, 1.55, 'gold')

    def half(y):
        if y < 25.2:
            return .62 + max(0.0, y - 22.8) * .32
        return 1.39 * max(0.0, 1 - (y - 25.2) / 6.4) ** .85
    w.relief(profile(half, 22.8, 31.6), [(0, .26, 'gold_light'), (.32, .48, 'bronze_light'), (.7, .55, 'bronze')])
    # Crescent axe on +X with thorns along its back; a curved fluke on -X.
    axe = [(8.5, 25.0), (9.6, 25.2), (10.9, 25.85), (12.3, 26.95), (13.4, 27.5), (13.75, 25.9), (13.95, 23.6),
           (13.75, 21.3), (13.2, 19.3), (12.25, 19.85), (11.8, 21.0), (11.45, 22.6), (10.4, 21.7), (9.4, 21.15), (8.5, 21.0)]
    w.relief(axe, [(0, .3, 'gold_light'), (.36, .52, 'bronze'), (1.0, .6, 'bronze_dark')])
    w.relief(leaf(9.2, 23.3, 3.1, .62, 8), [(0, .56, 'gold'), (.22, .6, 'nature_light')])
    for x, y in ((10.4, 25.55), (11.8, 26.4)):
        w.relief([(x - .35, y), (x + .1, y + 1.05), (x + .4, y + .2)], [(0, .32, 'gold_light')])
    hook = [(7.5, 24.4), (6.3, 24.55), (4.9, 24.05), (3.5, 22.95), (2.85, 21.2), (3.9, 22.15), (5.3, 22.6), (6.6, 22.6), (7.5, 22.35)]
    w.relief(hook, [(0, .3, 'gold_light'), (.3, .5, 'bronze')])
    w.jewel(8, 21.75, .7, 'gold_light', 'nature', 'nature_core', depth=1.0)


def tidehook(w):
    w.jewel(8, .95, .78, 'gold', 'sky_core', 'white', depth=.98, shape='round')
    w.rod(1.7, 2.2, 1.25, 'gold')
    w.rod(2.2, 20.4, .85, 'sky_dark')
    w.wrap(6.8, 11.2, .88, 'steel_dark', ['sky', 'silver'])
    for y in (2.2, 6.35, 11.25, 14.6, 17.4):
        w.rod(y, y + .45, 1.2, 'gold')
    crest = [(8, 21.3), (9.4, 21.2), (10.9, 21.6), (11.9, 22.5), (12.05, 21.55), (11.3, 20.55), (10.2, 20.15), (11.0, 19.75),
             (10.0, 19.35), (8.9, 19.75), (8, 19.6)]
    w.relief(mirrored(crest), [(0, .74, 'gold_dark'), (.26, .88, 'gold'), (.55, .96, 'gold_light')])
    w.jewel(8, 20.45, .72, 'gold_light', 'sky_core', 'white', depth=1.2, shape='round')

    def half(y):
        if y < 26.8:
            return .55 + .12 * math.sin((y - 21.0) / 5.8 * math.pi)
        return .62 * max(0.0, 1 - (y - 26.8) / 4.8) ** .75
    w.relief(profile(half, 21.0, 31.6), [(0, .24, 'silver_light'), (.28, .42, 'sky'), (.42, .46, 'sky_light')])
    for side in (1, -1):
        w.relief([(8 + side * .35, 27.6), (8 + side * 1.3, 26.6), (8 + side * .62, 27.25)], [(0, .32, 'silver_light')])
        line = [(8 + side * 1.0, 21.4), (8 + side * 2.6, 22.0), (8 + side * 3.75, 23.5), (8 + side * 4.05, 25.8), (8 + side * 3.6, 28.4), (8 + side * 3.25, 29.9)]
        w.relief(band(line, [.42, .46, .48, .46, .4, .2]), [(0, .26, 'silver_light'), (.2, .4, 'sky')])
        w.relief([(8 + side * 3.0, 27.6), (8 + side * 2.15, 26.35), (8 + side * 2.95, 26.75)], [(0, .3, 'silver_light')])
        w.relief(band([(8 + side * 1.4, 22.05), (8 + side * 2.3, 22.65), (8 + side * 2.35, 23.6), (8 + side * 1.75, 23.95)], .2),
                 [(0, .5, 'gold')])


def ashcleaver(w):
    w.relief([(8 + 1.45 * math.cos(a), 1.15 + 1.25 * math.sin(a)) for a in np.linspace(0, math.tau, 6, endpoint=False) + math.pi / 6],
             [(0, .82, 'steel_dark'), (.3, .98, 'steel_light')])
    w.jewel(8, 1.15, .62, 'steel_light', 'ember', 'ember_core', depth=1.22, shape='round')
    w.rod(2.35, 2.8, 1.6, 'steel_light')
    w.wrap(2.8, 8.4, 1.15, 'obsidian', ['leather_light', 'ember_dark'])
    w.rod(8.35, 8.8, 1.6, 'steel_light')
    guard = [(8, 11.4), (9.1, 11.05), (10.5, 10.6), (12.4, 10.45), (13.7, 10.7), (14.45, 10.25), (14.05, 9.25),
             (12.6, 9.0), (10.6, 9.05), (9.4, 8.65), (8, 8.3)]
    w.relief(mirrored(guard), [(0, 1.0, 'obsidian_light'), (.3, 1.16, 'steel_dark'), (.62, 1.24, 'steel')])
    w.jewel(8, 9.9, 1.0, 'steel_light', 'ember', 'ember_core', depth=1.58)

    def left(y):
        if y < 12.6:
            return 6.45
        if y < 13.4:
            return 6.45 - (y - 12.6) * 1.1
        if y < 27.0:
            return 5.57 + (y - 13.4) * .012
        return 5.73 + (y - 27.0) / 4.6 * (8.75 - 5.73)

    def right(y):
        if y < 12.6:
            return 9.55
        if y < 13.4:
            return 9.55 + (y - 12.6) * 1.1
        if y < 29.0:
            return 10.43 - (y - 13.4) * .012
        return 10.24 - (y - 29.0) / 2.6 * (10.24 - 8.75)
    w.relief(edges(left, right, 11.2, 31.6), [(0, .3, 'steel_light'), (.4, .6, 'steel_dark'), (.85, .68, 'obsidian')])
    for crack in ([(8.0, 12.2), (7.5, 14.6), (8.4, 16.8), (7.7, 19.4), (8.3, 22.0), (7.9, 24.6), (8.15, 27.2)],
                  [(7.7, 19.4), (6.6, 20.6)], [(8.4, 16.8), (9.5, 17.9)], [(8.3, 22.0), (9.3, 23.4)]):
        w.stroke(crack, .32, 7.28, 8.72, 'ember')
        w.stroke(crack, .16, 7.24, 8.76, 'ember_core')


def glimmermaul(w):
    w.jewel(8, 1.0, .82, 'gold', 'sun', 'sun_core', depth=1.0, shape='diamond')
    w.rod(1.7, 2.2, 1.4, 'gold')
    w.wrap(2.2, 8.0, 1.12, 'leather', ['leather_light', 'gold_dark'])
    w.rod(7.95, 19.2, 1.05, 'steel_dark')
    for y in (7.95, 11.4, 14.8, 18.0):
        w.rod(y, y + .45, 1.4, 'gold')

    def head(X, Y, Z):
        dx, dy, dz = np.abs(X - 8), np.abs(Y - 22.7), np.abs(Z - 8)
        flare = np.clip((dx - 4.2) / 2.0, 0, 1) * .55
        body = (dx <= 6.45) & (dy <= 2.9 + flare) & (dz <= 2.7 + flare) & (dy + dz <= 4.55 + flare)
        rim = body & (dx > 4.5) & (dx < 5.1)
        face = body & (dx > 6.0)
        edge = body & (dy + dz > 4.05 + flare)
        spike = cone_mask(X, Y, Z, (8, 25.2, 8), (0, 1, 0), 4.6, 1.15, .1)
        return [('gold', rim), ('silver_light', face), ('sun_light', spike & (Y > 28.6)), ('gold_light', spike & ~body),
                ('silver', edge), ('steel_light', body)]
    w.volume(head, (1.0, 19.0, 3.8), (15.0, 30.2, 12.2))
    for z in (8 - 2.95, 8 + 2.95):
        for a in range(8):
            ang = a * math.tau / 8
            w.stroke([(8 + math.cos(ang) * .9, 22.7 + math.sin(ang) * .9), (8 + math.cos(ang) * 2.0, 22.7 + math.sin(ang) * 2.0)],
                     .22, z - .1, z + .1, 'sun_light')
    w.jewel(8, 22.7, .85, 'gold_light', 'sun', 'sun_core', depth=3.05, shape='round')


def mistcaller(w):
    w.jewel(8, .95, .76, 'silver', 'frost', 'frost_core', depth=.98, shape='diamond')
    w.rod(1.6, 2.1, 1.2, 'silver_light')
    w.rod(2.1, 22.2, .82, 'silver_dark')
    w.wrap(7.0, 11.2, .86, 'steel_dark', ['silver', 'frost_dark'])
    for y in (2.1, 6.55, 11.25, 15.0, 18.4):
        w.rod(y, y + .45, 1.16, 'silver_light')
    w.rod(20.9, 22.4, 1.12, 'silver')
    horn = [(8, 23.0), (9.3, 22.9), (10.9, 23.6), (11.85, 25.2), (11.75, 27.3), (10.7, 29.0), (9.55, 29.6), (10.4, 28.2),
            (10.75, 26.6), (10.3, 25.0), (9.2, 24.25), (8, 24.4)]
    w.relief(mirrored(horn), [(0, .5, 'silver_dark'), (.24, .62, 'silver'), (.48, .7, 'silver_light')])
    w.relief(mirrored([(8, 22.9), (9.5, 22.7), (10.0, 22.2), (9.2, 21.85), (8, 21.8)]), [(0, .82, 'silver_dark'), (.25, .95, 'silver')])

    def orb(X, Y, Z):
        d = np.sqrt((X - 8) ** 2 + (Y - 26.4) ** 2 + (Z - 8) ** 2)
        return [('frost_core', (d <= 1.85) & (Y - 26.4 > .9) & (X < 8.6)), ('frost_light', (d <= 1.85) & (d > 1.35)), ('frost', d <= 1.85)]
    w.volume(orb, (5.8, 24.2, 5.8), (10.2, 28.6, 10.2), bone='focus')
    # A short charm of ice beads hangs from the collar and sways.
    w.stroke([(9.15, 21.9), (9.55, 20.6), (9.65, 19.2), (9.6, 17.7)], .16, 7.92, 8.08, 'silver_light', 'ribbon')
    for y, r in ((20.55, .32), (19.15, .36), (17.45, .44)):
        w.jewel(9.6, y, r, 'silver', 'frost', 'frost_core', z=8.0, depth=.5, shape='diamond', bone='ribbon')


def silvershiv(w):
    w.stroke(circle(8, .65, .72, 20) + [(8.72, .65)], .3, 7.7, 8.3, 'silver_light')
    w.relief(band([(8.4, .2), (9.3, -.6), (9.75, -1.5)], [.24, .28, .3], notch=.35), [(0, .14, 'cloth_purple'), (.14, .18, 'cloth_purple_light')], 'ribbon')
    w.rod(1.25, 2.0, 1.1, 'silver')
    w.wrap(2.0, 6.05, .9, 'cloth_purple', ['silver_dark', 'cloth_purple_light'])
    w.relief([(8 + 1.65 * math.cos(a), 6.5 + .5 * math.sin(a)) for a in np.linspace(0, math.tau, 24, endpoint=False)],
             [(0, .7, 'silver_dark'), (.18, .82, 'silver')])
    w.jewel(8, 6.5, .34, 'silver_light', 'void', 'void_core', depth=.98, shape='round')

    def left(y):
        if y < 15.2:
            return 7.22 + (y - 6.9) * .01
        return 7.3 + (y - 15.2) * .02

    def right(y):
        if y < 13.0:
            return 8.95 + (y - 6.9) * .05
        return 9.26 + (7.38 - 9.26) * ((y - 13.0) / 5.5) ** 1.6
    blade = edges(left, right, 6.9, 18.5)
    w.relief(blade, [(0, .22, 'silver_light'), (.28, .4, 'silver'), (.6, .46, 'silver_dark')])
    w.stroke([(right(y) - .3, y) for y in np.arange(7.6, 17.3, .5)], .3, 7.66, 8.34, 'void_light')


def stoneheart(w):
    w.jewel(8, 1.0, .8, 'bronze', 'ember', 'ember_core', depth=1.0, shape='diamond')
    w.rod(1.7, 2.2, 1.4, 'bronze')
    w.wrap(2.2, 7.8, 1.12, 'leather', ['leather_light', 'bronze_dark'])
    w.rod(7.75, 18.6, 1.1, 'wood_dark')
    for y in (7.75, 11.2, 14.6, 17.6):
        w.rod(y, y + .45, 1.42, 'bronze')

    def head(X, Y, Z):
        # Eight iron flanges around a stone core whose ember heart glows in the gaps.
        dx, dy, dz = X - 8, Y - 22.8, Z - 8
        r = np.hypot(dx, dz)
        core = (r <= 1.75) & (np.abs(dy) <= 4.0)
        reach = np.where(dy > 0, 1 - dy / 4.4, 1 + dy / 3.4)
        straight = (np.minimum(np.abs(dx), np.abs(dz)) <= .36) & (np.maximum(np.abs(dx), np.abs(dz)) <= 1.4 + 2.4 * np.clip(reach, 0, 1))
        diagonal = (np.abs(np.abs(dx) - np.abs(dz)) <= .5) & (r <= 1.4 + 1.6 * np.clip(reach, 0, 1))
        flange = (straight | diagonal) & (dy >= -3.4) & (dy <= 4.4)
        edge = flange & (r > 1.1 + 2.0 * np.clip(reach, 0, 1))
        heart = core & (r > 1.45) & (np.abs(dy - .2) <= 1.6)
        cap = cone_mask(X, Y, Z, (8, 26.4, 8), (0, 1, 0), 2.6, 1.25, .18)
        collar = (r <= 1.95) & (np.abs(dy + 3.7) <= .42)
        return [('steel_light', edge), ('steel', flange), ('ember_light', heart), ('bronze', collar),
                ('stone_light', cap & (Y > 27.6)), ('stone', cap), ('stone_dark', core)]
    w.volume(head, (3.2, 18.6, 3.2), (12.8, 29.2, 12.8))
    w.jewel(8, 28.35, .4, 'stone_light', 'ember', 'ember_core', depth=.8, shape='round')


def nightbloom(w):
    w.relief([(8, -.4), (8.85, 1.7), (7.15, 1.7)], [(0, .5, 'obsidian_edge'), (.2, .6, 'obsidian_light')])
    w.rod(1.6, 2.1, 1.22, 'gold')
    w.rod(2.1, 26.4, .86, 'obsidian')
    w.wrap(7.0, 11.4, .88, 'black', ['magenta', 'gold_dark'])
    for y in (2.1, 6.55, 11.45, 15.6, 19.8, 23.6):
        w.rod(y, y + .45, 1.18, 'gold')
    blade = [(8.7, 25.35), (8.7, 27.9), (6.9, 28.55), (4.9, 28.75), (3.0, 28.25), (1.45, 27.1), (.5, 25.55), (.15, 23.8),
             (1.1, 25.0), (2.6, 26.0), (4.6, 26.55), (6.6, 26.45)]
    w.relief(blade, [(0, .24, 'magenta_light'), (.3, .44, 'obsidian_edge'), (.62, .5, 'obsidian_light')])
    w.rod(25.1, 28.3, 1.15, 'gold_dark')
    for k in range(5):
        ang = math.pi / 2 + k * math.tau / 5
        for z in (7.0, 9.0):
            w.relief(leaf(8.2, 26.7, 1.55, .5, math.degrees(ang)), [(0, .2, 'magenta'), (.16, .24, 'magenta_light')], z=z)
    w.jewel(8.2, 26.7, .44, 'gold_light', 'void', 'void_core', depth=1.32, shape='round')
    w.relief(band([(8.7, 25.6), (9.7, 24.4), (10.4, 22.8), (10.6, 21.0), (10.2, 19.4)], [.32, .42, .46, .44, .4], notch=.55),
             [(0, .16, 'magenta'), (.2, .2, 'magenta_light')], 'ribbon', 8.75)


def ironwave(w):
    w.jewel(8, .95, .78, 'gold', 'sky', 'sky_core', depth=1.0, shape='diamond')
    w.rod(1.6, 2.2, 1.25, 'gold')
    w.rod(2.2, 20.6, .85, 'steel_dark')
    w.wrap(6.6, 11.0, .88, 'steel', ['sky_dark', 'silver'])
    for y in (2.2, 6.15, 11.05, 14.6, 17.6):
        w.rod(y, y + .45, 1.2, 'gold')
    w.rod(19.6, 21.2, 1.12, 'gold_dark')

    def left(y):
        base = 7.25 + .12 * math.sin((y - 20.8) / 10.8 * math.pi)
        if 21.6 <= y < 28.0:
            base -= .55 * (1 - ((y - 21.6) % 2.1) / 2.1)
        if y > 28.6:
            base += (y - 28.6) / 3.0 * (8.95 - base)
        return base

    def right(y):
        x = 8.9 + 1.05 * math.sin(min(1.0, (y - 20.8) / 9.4) * math.pi * .78)
        if y > 28.0:
            x = 8.95 + (x - 8.95) * max(0.0, (31.6 - y) / 3.6) ** .8
        return x
    w.relief(edges(left, right, 20.8, 31.6), [(0, .24, 'silver_light'), (.3, .44, 'silver'), (.7, .52, 'silver_dark')])
    w.stroke([(right(y) - .34, y) for y in np.arange(21.6, 29.6, .5)], .3, 7.62, 8.38, 'sky_light')
    w.relief(mirrored([(8, 21.4), (9.2, 21.2), (10.1, 20.6), (9.6, 19.95), (8.8, 19.7), (8, 19.6)]),
             [(0, .82, 'gold_dark'), (.26, .96, 'gold')])
    w.jewel(8, 20.5, .62, 'gold_light', 'sky', 'sky_core', depth=1.2, shape='round')


def starfang(w):
    star = [(8 + (1.5 if k % 2 == 0 else .55) * math.cos(math.pi / 2 + k * math.pi / 4),
             1.3 + (1.5 if k % 2 == 0 else .55) * math.sin(math.pi / 2 + k * math.pi / 4)) for k in range(8)]
    w.relief(star, [(0, .7, 'gold_dark'), (.24, .84, 'gold')])
    w.jewel(8, 1.3, .48, 'gold_light', 'sky', 'sky_core', depth=1.02, shape='round')
    w.rod(2.55, 2.95, 1.25, 'gold')
    w.wrap(2.95, 7.1, .88, 'sky_dark', ['gold', 'sky'])
    w.rod(7.05, 7.5, 1.25, 'gold')
    w.stroke([(10.85, 8.05), (11.6, 6.6), (11.75, 4.9), (11.25, 3.5), (10.1, 2.8), (8.9, 2.85)], .42, 7.68, 8.32, 'gold_light')
    w.relief([(4.1, 8.3), (11.9, 8.3), (11.9, 7.75), (4.1, 7.75)], [(0, .42, 'gold')])
    for x in (3.85, 12.15):
        w.jewel(x, 8.02, .42, 'gold_light', 'sky', 'sky_core', depth=.62, shape='round')
    w.tube([(8 + 1.55 * math.cos(a), 8.85, 8 + 1.55 * math.sin(a)) for a in np.linspace(0, math.tau, 25)], .2, 'gold_light')

    def half(y):
        if y < 10.0:
            return .7
        if y < 27.0:
            return .7 - (y - 10.0) * .009
        return .547 * max(0.0, 1 - (y - 27.0) / 4.6) ** .9
    w.relief(profile(half, 8.3, 31.6), [(0, .2, 'silver_light'), (.25, .34, 'silver'), (.45, .4, 'silver_light')])
    w.box(7.86, 8.6, 7.6, 8.14, 11.6, 8.4, 'sun_light')


def rootspire(w):
    w.relief(mirrored([(8, 1.9), (9.0, 1.7), (9.45, .9), (8.8, .1), (8, -.2)]), [(0, .62, 'wood_dark'), (.24, .74, 'wood')])
    w.rod(1.6, 15.6, 1.15, 'wood_dark')
    w.wrap(2.2, 7.6, 1.18, 'leather', ['leather_light', 'vine_dark'])
    for y, side in ((9.2, 1), (11.6, -1), (13.8, 1)):
        w.cone((8, y, 8), (side, .55, .3 * side), .9, .34, 'wood', tip_radius=.12)

    def head(X, Y, Z):
        # A tapered club of old wood: octagonal swell, bark grooves, two binding roots and a mossy crown.
        dx, dy, dz = X - 8, Y, Z - 8
        radius = np.interp(dy, [15.0, 18.0, 23.0, 27.5, 30.2], [.7, 2.0, 2.9, 2.6, 1.0])
        wood = (np.maximum(np.abs(dx), np.abs(dz)) <= radius) & (np.abs(dx) + np.abs(dz) <= radius * 1.42) & (dy >= 15.0) & (dy <= 30.2)
        roots = np.zeros(X.shape, dtype=bool)
        for start in (0.3, math.pi + .3):
            path = []
            for t in np.linspace(0, 1, 14):
                y = 16.5 + t * 11.5
                r = np.interp(y, [15.0, 18.0, 23.0, 27.5, 30.2], [.7, 2.0, 2.9, 2.6, 1.0]) + .15
                a = start + t * 2.6
                path.append((8 + r * math.cos(a), y, 8 + r * math.sin(a)))
            roots |= segment_distance(X, Y, Z, np.array(path)) <= .48
        moss = wood & (dy > 27.4)
        bark = wood & (np.abs(dx) + np.abs(dz) > radius * 1.2)
        return [('wood_dark', roots & ~wood), ('nature', moss & (dy > 28.6)), ('vine', moss), ('wood_light', bark), ('wood', wood)]
    w.volume(head, (4.4, 14.8, 4.4), (11.6, 30.4, 11.6), cell=.5)
    w.jewel(8, 23.2, .78, 'vine_dark', 'nature', 'nature_core', depth=3.25, shape='diamond')


def voidreel(w):
    cx, cy = 8.0, 17.1
    ring = lambda X, Y: (np.hypot(X - cx, Y - cy) <= 5.55) & (np.hypot(X - cx, Y - cy) >= 4.35)
    w.relief(ring, [(0, .4, 'obsidian_edge'), (.24, .52, 'obsidian_light'), (.5, .58, 'obsidian')])
    for k in range(8):
        a0 = k * math.tau / 8 + .2
        blade = [(cx + r * math.cos(a0 + s), cy + r * math.sin(a0 + s)) for s, r in
                 ((0, 5.35), (.12, 6.25), (.3, 7.05), (.5, 7.35), (.52, 6.75), (.42, 6.2), (.38, 5.35))]
        w.relief(blade, [(0, .22, 'silver_light'), (.22, .34, 'silver'), (.42, .4, 'void_light')])
    hub = circle(cx, cy, 1.25, 20)
    w.relief(hub, [(0, .62, 'obsidian_edge'), (.3, .74, 'obsidian_light')])
    w.jewel(cx, cy, .8, 'obsidian_edge', 'void', 'void_core', depth=1.06, shape='round')
    for a in (0, math.pi / 2, math.pi):
        x0, y0 = cx + 1.1 * math.cos(a), cy + 1.1 * math.sin(a)
        x1, y1 = cx + 4.5 * math.cos(a), cy + 4.5 * math.sin(a)
        w.stroke([(x0, y0), (x1, y1)], .5, 7.62, 8.38, 'obsidian_edge')
    w.wrap(12.0, 15.95, .82, 'leather', ['leather_light', 'void_dark'])


MELEE = {
    'emberfang': emberfang, 'stormpiercer': stormpiercer, 'ironbloom': ironbloom, 'frostveil': frostveil,
    'sunflare': sunflare, 'moonrift': moonrift, 'bramblepike': bramblepike, 'tidehook': tidehook,
    'ashcleaver': ashcleaver, 'glimmermaul': glimmermaul, 'mistcaller': mistcaller, 'silvershiv': silvershiv,
    'stoneheart': stoneheart, 'nightbloom': nightbloom, 'ironwave': ironwave, 'starfang': starfang,
    'rootspire': rootspire, 'voidreel': voidreel,
}
# Held forward, model +X faces up (third person) and toward the camera (first
# person). These single-edged sculpts are authored with the edge on +X and are
# mirrored so the edge, axe bit or knuckle bow faces down and away.
EDGE_DOWN = {'silvershiv', 'ironwave', 'bramblepike', 'starfang'}
# Bedrock pivots for the swaying idle bones, in model pixels (before mirroring).
ANCHORS = {
    'stormpiercer': {'ribbon': (8.6, 19.5, 8.85)},
    'mistcaller': {'ribbon': (9.15, 21.9, 8), 'focus': (8, 26.4, 8)},
    'silvershiv': {'ribbon': (8.4, .2, 8)},
    'nightbloom': {'ribbon': (8.7, 25.6, 8.75)},
}


def anchor(name: str, bone: str):
    x, y, z = ANCHORS.get(name, {}).get(bone, (8, 16, 8))
    return (16 - x if name in EDGE_DOWN else x, y, z)


def make_weapon(name: str, state: str = 'standby') -> Sculpt:
    w = Sculpt(name)
    if name in MELEE:
        MELEE[name](w)
        if name in EDGE_DOWN:
            for c in w.cubes:
                c['from'][0], c['to'][0] = round(16 - c['to'][0], 4) + 0.0, round(16 - c['from'][0], 4) + 0.0
    else:
        ranged_weapon(w, name, state)
    return w


# -- ranged sculpts -----------------------------------------------------------
# Authored in the vanilla bow/crossbow sprite frame, so Mojang's own display
# transforms (RANGED_DISPLAY) hold them on Java exactly like a vanilla bow or
# crossbow, and a front view of the sculpt registers with the vanilla sprite that
# Bedrock's own bow/crossbow attachable holds (see "Bedrock held ranged weapons").
# The weapon lies in the XY plane centred on z=8 and fires toward (-1, +1);
# aimed in third person, the crossbow's +Z side faces the ground.
RANGED_STATES = {'bow': ('standby', 'pulling_0', 'pulling_1', 'pulling_2'),
                 'crossbow': ('standby', 'pulling_0', 'pulling_1', 'pulling_2', 'arrow', 'firework')}
DRAW = {'standby': 0.0, 'pulling_0': .35, 'pulling_1': .7, 'pulling_2': 1.0, 'arrow': 1.0, 'firework': 1.0}
# Mojang's item/bow.json and item/crossbow.json (Java 26.3), unchanged.
RANGED_DISPLAY = {
    'bow': {
        'thirdperson_righthand': {'rotation': [-80, 260, -40], 'translation': [-1, -2, 2.5], 'scale': [0.9, 0.9, 0.9]},
        'thirdperson_lefthand': {'rotation': [-80, -280, 40], 'translation': [-1, -2, 2.5], 'scale': [0.9, 0.9, 0.9]},
        'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
        'firstperson_lefthand': {'rotation': [0, 90, -25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
    },
    'crossbow': {
        'thirdperson_righthand': {'rotation': [-90, 0, -60], 'translation': [2, 0.1, -3], 'scale': [0.9, 0.9, 0.9]},
        'thirdperson_lefthand': {'rotation': [-90, 0, 30], 'translation': [2, 0.1, -3], 'scale': [0.9, 0.9, 0.9]},
        'firstperson_righthand': {'rotation': [-90, 0, -55], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
        'firstperson_lefthand': {'rotation': [-90, 0, 35], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
    },
}
# Draw-state thresholds in ticks of use, matching the Java item definitions:
# bow use_duration x 0.05 >= 0.65 / 0.9; crossbow pull (25-tick charge) >= 0.58 / 1.0.
DRAW_TICKS = {'bow': (13, 18), 'crossbow': (14.5, 25)}


class RangedFrame:
    """(s along the limbs, t toward the target, z) <-> item-model pixels."""

    def __init__(self, origin, along, forward):
        self.o = np.asarray(origin, dtype=float)
        self.u = np.asarray(along, dtype=float) / np.linalg.norm(along)
        self.f = np.asarray(forward, dtype=float) / np.linalg.norm(forward)
        assert abs(self.u @ self.f) < 1e-9

    def __call__(self, s, t, z=8.0):
        p = self.o + s * self.u + t * self.f
        return np.array([p[0], p[1], z])

    def local(self, X, Y):
        dx, dy = X - self.o[0], Y - self.o[1]
        return dx * self.u[0] + dy * self.u[1], dx * self.f[0] + dy * self.f[1]


def tapered(X, Y, Z, points, radii):
    """Clearance to a 3D polyline whose radius is interpolated per vertex (<= 0 inside)."""
    P = np.stack([X, Y, Z], -1)
    best = np.full(np.shape(X), np.inf)
    for a, b, ra, rb in zip(points, points[1:], radii, radii[1:]):
        a, b = np.asarray(a, dtype=float), np.asarray(b, dtype=float)
        d = b - a
        t = np.clip(((P - a) @ d) / max(d @ d, 1e-9), 0, 1)
        best = np.minimum(best, np.linalg.norm(P - a - t[..., None] * d, axis=-1) - (ra + (rb - ra) * t))
    return best


def flat_shape(X, Y, Z, outline, z0, z1):
    """A thin plate in the XY plane: the polygon between two depths."""
    return inside(outline)(X, Y) & (Z >= z0) & (Z <= z1)


def thornwhisper(w: Sculpt, state: str) -> None:
    """Living recurve bow of thorned wood, leaf charms, a seed gem and a glowing string."""
    draw = DRAW[state]
    # Vanilla bow sprite: limb tips at (2.5, .5) and (14.5, 14.5); the grip bulges 4.3 px ahead of that chord.
    F = RangedFrame((8.5, 7.5), (12, 14), (-14, 12))
    half, bulge = 9.2 * (1 - .05 * draw), 4.3

    def limb_t(s):
        a = min(1.0, abs(s) / half)
        curl = max(0.0, (a - .72) / .28) ** 2
        return bulge * (1 - a ** 1.7) - (1.1 + .9 * draw) * curl

    samples = np.linspace(-half, half, 41)
    limb = [F(s, limb_t(s)) for s in samples]
    radii = [.36 + .38 * (1 - abs(s) / half) ** .8 for s in samples]
    tips = [F(-half, limb_t(half)), F(half, limb_t(half))]
    nock = draw * 5.5
    string = [tips[0], F(0, limb_t(half) - nock, 8.0), tips[1]] if draw else [tips[0], tips[1]]
    arrow_tail, arrow_head = -nock + limb_t(half), -nock + limb_t(half) + 13.2
    vine = [F(s, limb_t(s) + .42 * math.cos(s * 2.1), 8 + .42 * math.sin(s * 2.1)) for s in np.linspace(-half * .9, half * .9, 70)]
    thorns = [(s, 1.05 - .1 * abs(s)) for s in (-7.4, -5.9, -4.3, -2.7, 2.7, 4.3, 5.9, 7.4)]
    leaves = []
    for s, side in ((-6.6, -1), (-3.6, -1), (3.6, 1), (6.6, 1)):
        base = F(s, limb_t(s) - .25)
        direction = -F.f * .55 + F.u * side * .85
        leaves.append(leaf(base[0], base[1], 2.3, .75, math.degrees(math.atan2(direction[1], direction[0]))))

    def material(X, Y, Z):
        S, T = F.local(X, Y)
        grip = (np.abs(S) <= 1.55) & (tapered(X, Y, Z, [F(-1.6, bulge), F(1.6, bulge)], [.92, .92]) <= 0)
        gem = (np.hypot(S, T - bulge - .1) <= .62) & (np.abs(Z - 8) <= 1.05)
        parts = [('nature_core', gem & (np.hypot(S - .15, T - bulge + .05) <= .26)),
                 ('nature_light', gem),
                 ('gold', grip & (np.abs(S) >= 1.3)),
                 ('gold_light', grip & (T > bulge + .55) & (np.abs(S) <= .45) & (Z < 8)),
                 ('leather_light', grip & (np.mod(S + 2, .6) < .3)),
                 ('leather', grip)]
        if draw:
            shaft = tapered(X, Y, Z, [F(0, arrow_tail, 7.35), F(0, arrow_head - 1.3, 7.35)], [.16, .16]) <= 0
            head = flat_shape(X, Y, Z, leaf(*F(0, arrow_head - 2.2)[:2], 2.4, .95, math.degrees(math.atan2(F.f[1], F.f[0]))), 6.95, 7.75)
            fletch = flat_shape(X, Y, Z, [tuple(F(-.85, arrow_tail + .2)[:2]), tuple(F(0, arrow_tail + 2.1)[:2]),
                                          tuple(F(.85, arrow_tail + .2)[:2]), tuple(F(0, arrow_tail + .8)[:2])], 7.2, 7.5)
            parts += [('nature_core', head), ('leaf_light', fletch), ('wood_light', shaft)]
        parts.append(('string_light', tapered(X, Y, Z, string, [.17] * len(string)) <= 0))
        for tip in tips:
            parts.append(('nature_light', np.linalg.norm(np.stack([X, Y, Z], -1) - tip, axis=-1) <= .58))
        for s, length in thorns:
            base = F(s, limb_t(s) + .25)
            direction = F.f + F.u * np.sign(s) * .45
            parts.append(('vine_dark', cone_mask(X, Y, Z, base, [direction[0], direction[1], 0], length, .3)))
        parts.append(('vine', tapered(X, Y, Z, vine, [.17] * len(vine)) <= 0))
        for outline in leaves:
            parts.append(('leaf_light', flat_shape(X, Y, Z, outline, 7.85, 8.15)))
        body = tapered(X, Y, Z, limb, radii) <= 0
        centre = np.array([limb_t(s) for s in np.clip(S, -half, half).ravel()]).reshape(S.shape)
        parts += [('wood_dark', body & (T > centre)), ('wood', body)]
        return parts

    w.volume(material, (-2.5, -3.5, 6.0), (18.5, 19.5, 10.0))


def runelock(w: Sculpt, state: str) -> None:
    """Rune-forged crossbow: blackened stock with a glowing groove, a silver prod and crystal fittings."""
    draw = DRAW[state]
    # Vanilla crossbow sprite: butt at (15, 1), muzzle toward (3.5, 12.5); prod tips (12.5, 15.5) / (.5, 3.5).
    F = RangedFrame((8, 8), (1, 1), (-1, 1))
    span = 8.5 - .5 * draw

    def prod_t(s):
        return 3.6 - (1.5 + .7 * draw) * min(1.0, abs(s) / span) ** 1.8

    samples = np.linspace(-span, span, 33)
    prod = [F(s, prod_t(s)) for s in samples]
    radii = [.3 + .2 * (1 - abs(s) / span) for s in samples]
    tips = [F(-span, prod_t(span)), F(span, prod_t(span))]
    nut = prod_t(span) - draw * (prod_t(span) + 3.2)
    string = [tips[0], F(0, nut, 6.95), tips[1]] if draw else [tips[0], tips[1]]

    def material(X, Y, Z):
        S, T = F.local(X, Y)
        a = np.abs(S)
        stock = (T >= -9.4) & (T <= 5.0) & (a <= .85) & (Z >= 7.1) & (Z <= 9.0)
        butt = (T >= -9.6) & (T <= -7.3) & (a <= 1.2) & (Z >= 6.9) & (Z <= 9.6)
        mount = (T >= 2.9) & (T <= 4.4) & (a <= 1.25) & (Z >= 6.95) & (Z <= 9.15)
        lean = Z - 9.0
        grip = (T >= -5.9 - lean * .45) & (T <= -4.5 - lean * .45) & (a <= .6) & (Z >= 9.0) & (Z <= 11.3)
        trigger = (T >= -3.9) & (T <= -3.45) & (a <= .2) & (Z >= 9.0) & (Z <= 10.1)
        guard = (T >= -4.4) & (T <= -2.9) & (a <= .24) & (Z >= 10.1) & (Z <= 10.35)
        bands = (np.min([np.abs(T - c) for c in (-7.0, -4.0, .6, 4.6)], axis=0) <= .18) & (a <= 1.0) & (Z >= 6.95) & (Z <= 9.15)
        groove = (T >= -3.0) & (T <= 4.6) & (a <= .15) & (Z >= 6.85) & (Z < 7.1)
        glyphs = (a >= .85) & (a <= .97) & (Z >= 7.55) & (Z <= 8.45) & (np.mod(T + 9, 1.7) < .55) & (T >= -6.8) & (T <= 2.6)
        focus = (np.abs(S) + np.abs(T - 5.55) <= .62) & (Z >= 6.75) & (Z <= 9.25)
        stirrup = tapered(X, Y, Z, [F(-.85, 4.4), F(-.75, 6.7), F(.75, 6.7), F(.85, 4.4)], [.2] * 4) <= 0
        parts = [('sky_core', focus), ('sky_light', groove | glyphs), ('gold', bands | trigger)]
        if state == 'arrow':
            parts += [('sky_core', tapered(X, Y, Z, [F(0, 6.9, 6.55), F(0, 8.4, 6.55)], [.42, .05]) <= 0),
                      ('cloth_blue_light', (T >= -2.9) & (T <= -1.6) & (a <= .55) & (np.abs(Z - 6.55) <= .06)),
                      ('silver_light', tapered(X, Y, Z, [F(0, -3.1, 6.55), F(0, 6.9, 6.55)], [.17, .17]) <= 0)]
        elif state == 'firework':
            body = tapered(X, Y, Z, [F(0, -2.8, 6.4), F(0, 5.0, 6.4)], [.42, .42]) <= 0
            parts += [('ember_light', tapered(X, Y, Z, [F(0, 5.0, 6.4), F(0, 6.7, 6.4)], [.42, .06]) <= 0),
                      ('white', body & (np.min([np.abs(T - c) for c in (-1.0, 2.0)], axis=0) <= .3)),
                      ('cloth_red', body)]
        parts += [('string_light', tapered(X, Y, Z, string, [.16] * len(string)) <= 0),
                  ('gold_light', (T >= nut - .55) & (T <= nut) & (a <= .45) & (Z >= 6.75) & (Z < 7.1) if draw else np.zeros(S.shape, bool))]
        for tip in tips:
            outward = tip[:2] - F.o
            outward = np.array([outward[0], outward[1], 0]) / np.linalg.norm(outward)
            parts.append(('sky_light', cone_mask(X, Y, Z, tip, outward, 1.15, .42)))
        prod_body = tapered(X, Y, Z, prod, radii) <= 0
        runes = prod_body & (np.min([np.abs(np.abs(S) - c) for c in (2.6, 5.4)], axis=0) <= .22)
        centre = np.array([prod_t(s) for s in np.clip(S, -span, span).ravel()]).reshape(S.shape)
        parts += [('sky_light', runes), ('silver_light', prod_body & (T > centre)), ('silver', prod_body),
                  ('steel_dark', mount), ('steel_light', stirrup), ('leather', grip), ('steel', guard),
                  ('steel_light', stock & (Z < 7.4) & (a <= .55)), ('obsidian_light', butt), ('obsidian', stock)]
        return parts

    w.volume(material, (-4.5, -4.5, 6.0), (20.5, 20.5, 11.5))


RANGED = {'thornwhisper': thornwhisper, 'runelock': runelock}


def ranged_weapon(w: Sculpt, name: str, state: str) -> None:
    RANGED[name](w, state)


# -- held VFX -----------------------------------------------------------------
THEMES = {
    'emberfang': 'ember', 'ashcleaver': 'ember', 'stoneheart': 'ember',
    'stormpiercer': 'storm', 'ironwave': 'tide', 'tidehook': 'tide',
    'ironbloom': 'leaf', 'bramblepike': 'leaf', 'rootspire': 'leaf',
    'frostveil': 'frost', 'mistcaller': 'frost',
    'sunflare': 'sun', 'glimmermaul': 'sun', 'starfang': 'sun',
    'moonrift': 'void', 'silvershiv': 'void', 'voidreel': 'void',
    'nightbloom': 'petal', 'thornwhisper': 'leaf', 'runelock': 'storm',
}
# Where the effect gathers (model y range): blades, heads or the chakram ring.
FOCUS = {'sword': (11.0, 30.0), 'spear': (19.5, 31.0), 'mace': (17.5, 30.0), 'bow': (4.0, 28.0), 'crossbow': (12.0, 30.0)}
FOCUS_OVERRIDE = {'frostveil': (8.5, 20.5), 'silvershiv': (7.0, 18.0), 'voidreel': (10.0, 24.5), 'nightbloom': (22.0, 30.5)}


def fx_frame(name: str, frame: int) -> Image.Image:
    """128x128 eight-frame cutout VFX themed to each weapon's element."""
    im = Image.new('RGBA', (128, 128))
    d = ImageDraw.Draw(im)
    row = BY_ID[name]
    color = row[8]
    theme = THEMES[name]
    y0, y1 = FOCUS_OVERRIDE.get(name, FOCUS[row[5]])
    rng = random.Random(1901 + IDS.index(name) * 97)
    t = frame / 8

    def xy(x, y):
        return round((x + 8) * 4), round((32 - y) * 4)

    def px(x, y, rgba, size=1):
        cx, cy = xy(x, y)
        d.rectangle((cx, cy, cx + size - 1, cy + size - 1), fill=rgba)

    def glint(x, y, r, rgba):
        cx, cy = xy(x, y)
        d.line((cx - r, cy, cx + r, cy), fill=rgba, width=1)
        d.line((cx, cy - r, cx, cy + r), fill=rgba, width=1)
        d.point((cx, cy), fill=(255, 255, 255, 255))

    seeds = [(rng.uniform(0, 1), rng.choice((-1, 1)) * rng.uniform(2.4, 5.2), rng.uniform(0, 1)) for _ in range(9)]
    span = y1 - y0
    for k, (phase, side, wobble) in enumerate(seeds):
        p = (phase + t) % 1
        if theme == 'ember':
            x, y = 8 + side * (1 - .35 * p) + math.sin((p + wobble) * math.tau) * .5, y0 + span * p
            heat = ((255, 218, 117, 255), (255, 107, 33, 255), (201, 42, 29, 255))[min(2, int(p * 3))]
            px(x, y, heat, 2 if p < .5 else 1)
        elif theme == 'frost':
            x, y = 8 + side * .85 + math.sin((p + wobble) * math.tau) * .6, y1 - span * p
            if k % 3 == 0:
                glint(x, y, 2, (126, 242, 255, 255))
            else:
                px(x, y, (235, 255, 255, 255), 1)
        elif theme == 'storm':
            if (frame + k) % 4 == 0 and k < 4:
                x, y = 8 + side * .8, y0 + span * wobble
                pts = [xy(x, y)]
                for _ in range(3):
                    x += side * rng.uniform(.4, .9) * .5
                    y += rng.uniform(-1.1, 1.1)
                    pts.append(xy(x, y))
                d.line(pts, fill=(94, 202, 255, 255), width=2)
                d.line(pts, fill=(219, 248, 255, 255), width=1)
            else:
                px(8 + side * .9, y0 + span * ((wobble + t * 1.5) % 1), (94, 202, 255, 255))
        elif theme == 'tide':
            x, y = 8 + side * .8 + math.sin((p + wobble) * math.tau) * .35, y0 + span * p
            cx, cy = xy(x, y)
            r = 1 if k % 2 else 2
            d.rectangle((cx - r, cy - r, cx + r, cy + r), outline=(126, 220, 255, 255))
        elif theme == 'leaf':
            x, y = 8 + side * .8 + math.sin((p + wobble) * math.tau) * .9, y1 - span * p
            cx, cy = xy(x, y)
            shade = (110, 240, 98, 255) if k % 2 else (63, 160, 58, 255)
            d.line((cx, cy, cx + 2, cy - 1 if (frame + k) % 2 else cy + 1), fill=shade, width=1)
            d.point((cx + 1, cy), fill=(223, 255, 212, 255))
        elif theme == 'sun':
            if (frame + k) % 3 != 1:
                x, y = 8 + side * .9, y0 + span * wobble
                glint(x, y, 1 + (frame + k) % 2, (255, 216, 68, 255))
        elif theme == 'void':
            a = (wobble + t) * math.tau + k
            x, y = 8 + math.cos(a) * abs(side) * .75, (y0 + y1) / 2 + math.sin(a) * span * .42
            px(x, y, (194, 104, 255, 255) if k % 2 else (244, 217, 255, 255), 2 if k % 3 == 0 else 1)
        else:
            x, y = 8 + side * .8 + math.sin((p + wobble) * math.tau) * .8, y1 - span * p
            cx, cy = xy(x, y)
            d.rectangle((cx, cy, cx + 1, cy), fill=(255, 89, 196, 255) if k % 2 else (179, 34, 133, 255))
    return im


def java_display(name: str, state: str = 'standby'):
    row = BY_ID[name]
    category = row[5]
    grip_y = row[7]
    display = {
        'ground': {'translation': [0, 3, 0], 'scale': [0.4, 0.4, 0.4]},
        'fixed': {'rotation': [0, 180, 0], 'translation': [0, -4, 0], 'scale': [0.65, 0.65, 0.65]},
        'head': {'translation': [0, 10, 0], 'scale': [0.6, 0.6, 0.6]},
    }
    if category in RANGED_DISPLAY:
        # Mojang's own transforms: the model shares the vanilla sprite frame.
        return {'ground': {'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
                'fixed': {'rotation': [0, 180, 0]},
                'head': {'rotation': [0, 180, 0], 'translation': [0, 13, 7]},
                **copy.deepcopy(RANGED_DISPLAY[category])}

    display.update(handheld_display(grip_y, category == 'spear'))
    return display


def handheld_display(grip_y: float, spear: bool = False):
    """Hand transforms for a vertical shaft gripped at (8, grip_y, 8), held where a vanilla tool sits."""
    display = {}
    for first in (False, True):
        for left in (False, True):
            mode = ('firstperson_' if first else 'thirdperson_') + ('lefthand' if left else 'righthand')
            native_r = [0, -90, 25 if first else 55]
            native_t = [1.13, 3.2, 1.13] if first else [0, 4, 0.5]
            native_s = 0.68 if first else 0.85
            target = rotate_xyz([(3.5 - 8) * native_s, (3.5 - 8) * native_s, 0], native_r)
            target = [target[i] + native_t[i] for i in range(3)]
            if first:
                target = [target[0], target[1] + 0.8, target[2] - 1.5]
            if left:
                target[0] *= -1
            r = [0, 90 if left else -90, -10 if first and left else 10 if first else 0]
            effective = [r[0], -r[1] if left else r[1], -r[2] if left else r[2]]
            scale = (0.62 if spear else 0.68) if first else 0.90
            offset = rotate_xyz([0, (grip_y - 8) * scale, 0], effective)
            translation = [target[i] - offset[i] for i in range(3)]
            if left:
                translation[0] *= -1
            display[mode] = {'rotation': r, 'translation': [round(v, 6) for v in translation], 'scale': [scale] * 3}
    return display


def java_model(w: Sculpt, state: str = 'standby'):
    elements = []
    for c in w.cubes:
        e = {k: c[k] for k in ('from', 'to')}
        e.update(name=c['name'], faces={f: {'uv': tile(c['mat']), 'texture': '#metal'} for f in FACES})
        if c['mat'] in EMISSIVE:
            e.update(shade=False, light_emission=15)
        elements.append(e)
    for axis, pos in (() if BY_ID[w.name][5] in RANGED_STATES else (('z', 3.25), ('z', 12.75), ('x', 7.95))):
        if axis == 'z':
            a, b, faces = [-8, 0, pos], [24, 32, pos], ('north', 'south')
        else:
            a, b, faces = [pos, 0, -8], [pos, 32, 24], ('east', 'west')
        elements.append({
            'from': a, 'to': b, 'shade': False, 'light_emission': 15,
            'faces': {f: {'uv': [0, 0, 16, 16], 'texture': '#fx'} for f in faces}
        })
    return {
        'credit': 'Evergarden / adventurer_weapon_assets.py',
        'ambientocclusion': False,
        'gui_light': 'front',
        'textures': {
            'metal': 'voidscape:item/adventurer_weapons/materials',
            **({} if BY_ID[w.name][5] in RANGED_STATES else {'fx': f'voidscape:item/adventurer_weapons/{w.name}_fx'}),
            'particle': 'voidscape:item/adventurer_weapons/materials',
        },
        'display': java_display(w.name, state),
        'elements': elements,
    }


def java_draw_states(name: str):
    return RANGED_STATES.get(BY_ID[name][5], ('standby',))


def java_state_dispatch(name: str, icon: bool = False):
    """Use vanilla draw/charge properties for both held meshes and GUI icons.

    crossbow/pull supplies the enchantment-adjusted fraction, including Quick
    Charge. A fixed use_duration divisor would desynchronise faster crossbows.
    """
    def model(state='standby'):
        suffix = '' if state == 'standby' else '_' + state
        if icon:
            suffix += '_icon'
        return {'type': 'minecraft:model', 'model': f'voidscape:item/adventurer_weapons/{name}{suffix}'}

    category = BY_ID[name][5]
    if category not in ('bow', 'crossbow'):
        return model()
    pulling = {
        'type': 'minecraft:range_dispatch',
        'property': 'minecraft:crossbow/pull' if category == 'crossbow' else 'minecraft:use_duration',
        'fallback': model('pulling_0'),
        'entries': [
            {'threshold': 0.58 if category == 'crossbow' else 0.65, 'model': model('pulling_1')},
            {'threshold': 1.0 if category == 'crossbow' else 0.90, 'model': model('pulling_2')},
        ],
    }
    if category == 'bow':
        pulling['scale'] = 0.05
    using = {
        'type': 'minecraft:condition', 'property': 'minecraft:using_item',
        'on_false': model(), 'on_true': pulling,
    }
    if category == 'crossbow':
        return {
            'type': 'minecraft:select', 'property': 'minecraft:charge_type',
            'cases': [
                {'when': 'arrow', 'model': model('arrow')},
                {'when': 'rocket', 'model': model('firework')},
            ],
            'fallback': using,
        }
    return using


def java_item_definition(name: str):
    return {'model': {
        'type': 'minecraft:select', 'property': 'minecraft:display_context',
        'cases': [{'when': 'gui', 'model': java_state_dispatch(name, icon=True)}],
        'fallback': java_state_dispatch(name),
    }}


def bedrock_pose(first: bool, name: str):
    """The Java display transform through weapon_forge's java2bedrock rig (melee weapons)."""
    return rig_animation(java_display(name), first)


def uv_px(mat: str):
    u0, v0, _, _ = tile(mat)
    return [u0 * 8, v0 * 8, 16]


def body_groups(w: Sculpt):
    groups: dict[str, list[dict]] = {}
    for c in w.cubes:
        bname = c['bone']
        if c['mat'] in EMISSIVE and bname == 'weapon':
            bname = 'inlay'
        groups.setdefault(bname, []).append(c)
    return groups


def bedrock_geometry(w: Sculpt, fx: bool = False):
    # java2bedrock rig: bound root, then display X/Y/Z bones; body and FX
    # layers share every parent transform.
    bones = rig_bones(BY_ID[w.name][7])
    for bone, items in ({} if fx else body_groups(w)).items():
        bones.append({'name': bone, 'parent': 'display_z', 'pivot': bedrock_point(anchor(w.name, bone)),
                      'cubes': [bedrock_cube(c, uv_px(c['mat'])) for c in items]})
    if fx:
        cubes = [{'origin': [-16, 0, z], 'size': [32, 32, 0],
                  'uv': {f: {'uv': [0, 0], 'uv_size': [128, 128]} for f in ('north', 'south')}} for z in (-4.75, 4.75)]
        cubes.append({'origin': [.05, 0, -16], 'size': [0, 32, 32],
                      'uv': {f: {'uv': [0, 0], 'uv_size': [128, 128]} for f in ('east', 'west')}})
        bones.append({'name': 'aura', 'parent': 'display_z', 'pivot': bedrock_point((8, 16, 8)), 'cubes': cubes})
    return {
        'format_version': '1.16.0',
        'minecraft:geometry': [{
            'description': {
                'identifier': f'geometry.voidscape.adventurer_{w.name}' + ('_fx' if fx else ''),
                'texture_width': 128,
                'texture_height': 128,
                'visible_bounds_width': 4,
                'visible_bounds_height': 4.5,
                'visible_bounds_offset': [0, 0.75, 0],
            },
            'bones': bones,
        }],
    }


def bbmodel(w: Sculpt, texture: bytes):
    uid = lambda s: str(uuid.uuid5(uuid.NAMESPACE_URL, f'evergarden:adventurer/{w.name}/{s}'))
    grip_y = BY_ID[w.name][7]
    groups = []
    for bone in sorted({c['bone'] for c in w.cubes}):
        groups.append({
            'name': bone, 'uuid': uid(bone),
            'origin': [0, round((20 if bone in ('ribbon', 'focus', 'orbit') else grip_y) - grip_y, 4), 0],
            'rotation': [0, 0, 0], 'export': True, 'isOpen': True, 'visibility': True,
            'children': [uid(c['name']) for c in w.cubes if c['bone'] == bone],
        })
    elements = []
    for c in w.cubes:
        elements.append({
            'name': c['name'], 'type': 'cube', 'uuid': uid(c['name']),
            'from': [round(c['from'][0] - 8, 4), round(c['from'][1] - grip_y, 4), round(c['from'][2] - 8, 4)],
            'to': [round(c['to'][0] - 8, 4), round(c['to'][1] - grip_y, 4), round(c['to'][2] - 8, 4)],
            'origin': [0, 0, 0], 'rotation': [0, 0, 0], 'shade': c['mat'] not in EMISSIVE,
            'box_uv': False, 'autouv': 0, 'rescale': False, 'locked': False, 'visibility': True, 'export': True,
            'faces': {f: {'uv': [v * 8 for v in tile(c['mat'])], 'texture': 0} for f in FACES},
        })
    elements.append({
        'name': 'hitbox', 'uuid': uid('hitbox_cube'), 'type': 'cube',
        'from': [-1, 0, -1], 'to': [1, 4, 1], 'origin': [0, 0, 0], 'faces': {},
    })
    animators = {}
    for g in groups:
        amount = 3.0 if g['name'] in ('ribbon', 'orbit') else 0.6
        animators[g['uuid']] = {
            'name': g['name'], 'type': 'bone',
            'keyframes': [
                {
                    'channel': 'rotation',
                    'data_points': [{'x': '0', 'y': '0', 'z': str(v)}],
                    'uuid': uid(g['name'] + str(t)),
                    'time': t,
                    'interpolation': 'catmullrom',
                }
                for t, v in ((0, 0), (0.5, amount), (1, 0), (1.5, -amount), (2, 0))
            ],
        }
    return {
        'meta': {'format_version': '4.10', 'model_format': 'free', 'box_uv': False},
        'name': f'adventurer_{w.name}',
        'resolution': {'width': 128, 'height': 128},
        'elements': elements,
        'outliner': groups + [{'name': 'hitbox', 'uuid': uid('hitbox'), 'origin': [0, 3, 0], 'children': [uid('hitbox_cube')], 'export': True}],
        'textures': [{
            'name': 'adventurer_materials.png', 'namespace': '', 'folder': '', 'path': '',
            'id': '0', 'uuid': uid('texture'), 'width': 128, 'height': 128,
            'uv_width': 128, 'uv_height': 128,
            'source': 'data:image/png;base64,' + base64.b64encode(texture).decode(),
        }],
        'animations': [{
            'uuid': uid('idle'), 'name': 'idle', 'loop': 'loop', 'length': 2,
            'override': False, 'anim_time_update': '', 'blend_weight': '',
            'start_delay': '', 'loop_delay': '', 'animators': animators,
        }],
    }


def _preview_sculpt(w: Sculpt, tex: Image.Image) -> Sculpt:
    cp = copy.deepcopy(w)
    for c in cp.cubes:
        c['preview_uv'] = [v * 8 for v in tile(c['mat'])]
        c['preview_texture'] = tex
        c['mat'] = 'white' if c['mat'] in EMISSIVE else 'gold'
    return cp


def register_runelock_loaded_states(bedrock: Path, item_atlas: dict, mappings: dict, controllers: dict, attach: dict, title: str):
    """Map the native charged-projectile component to deterministic Bedrock UI icons.

    This uses Geyser's supported charge_type predicate. It needs no server-side
    item replacement and does not depend on an atlas frame selecting UI states.
    """
    for charge_type, state in (('arrow', 'arrow'), ('rocket', 'firework')):
        identifier = f'voidscape:adventurer_runelock_loaded_{charge_type}'
        icon_key = f'voidscape.adventurer_runelock_loaded_{charge_type}'
        item_atlas[icon_key] = {'textures': f'textures/adventurer_weapons/runelock_{state}_icon'}
        alias = copy.deepcopy(attach)
        alias['identifier'] = identifier
        # The loaded item becomes the standby definition again when fired, so
        # this alias can show its actual charged pose without a frame query.
        controller = f'controller.render.voidscape.adventurer_runelock_loaded_{charge_type}'
        alias['render_controllers'] = [controller]
        controllers[controller] = held_controller([VANILLA_STATE['crossbow'][state][1]])
        write_json(bedrock / f'attachables/adventurer_weapons/runelock_loaded_{charge_type}.json', {
            'format_version': '1.10.0',
            'minecraft:attachable': {'description': alias},
        })
        mappings['items'].setdefault('minecraft:crossbow', []).append({
            'type': 'definition',
            'model': 'voidscape:adventurer_runelock',
            'predicate': {'type': 'match', 'property': 'charge_type', 'value': charge_type},
            'bedrock_identifier': identifier,
            'display_name': title,
            'bedrock_options': {
                'icon': icon_key,
                'allow_offhand': True,
                'display_handheld': True,
                'creative_category': 'equipment',
            },
        })


def ranged_icon(w: Sculpt, tex: Image.Image) -> Image.Image:
    """A static 128 px icon in the vanilla sprite orientation (no diagonal tilt: the model already fires up-left)."""
    from legendary_weapon_preview import render_item
    body = render_item(_preview_sculpt(w, tex), tex, None, size=256, yaw=14, tilt=0, elevation=10)
    l, t, r, b = body.getbbox()
    side = math.ceil(max(r - l, b - t) * 1.08)
    cx, cy = (l + r) / 2, (t + b) / 2
    crop = (round(cx - side / 2), round(cy - side / 2), round(cx - side / 2) + side, round(cy - side / 2) + side)
    return body.crop(crop).resize((128, 128), Image.Resampling.NEAREST)


def bedrock_draw_frame(category: str) -> list[str]:
    """Attachable pre_animation: the Java draw state from the held item's elapsed use ticks.

    Bedrock counts main_hand_item_use_duration down from main_hand_item_max_duration
    while the item is in use (Mojang's own bow and trident attachables read it so);
    it is 0 when idle. Loaded crossbows are separate charge_type definitions.
    charge_amount is Mojang's own expression (it drives the full-draw shake of the
    vanilla first-person pull), reaching 1 at Java's full power / full charge.
    """
    first, second = DRAW_TICKS[category]
    return ['v.draw_ticks = q.main_hand_item_use_duration > 0.0 ? q.main_hand_item_max_duration - q.main_hand_item_use_duration : -1.0;',
            f'v.draw_frame = v.draw_ticks < 0.0 ? 0 : (v.draw_ticks < {first} ? 1 : (v.draw_ticks < {second} ? 2 : 3));',
            'variable.charge_amount = math.clamp((query.main_hand_item_max_duration - (query.main_hand_item_use_duration'
            f' - query.frame_alpha + 1.0)) / {FULL_DRAW[category]:.1f}, 0.0, 1.0);']


# -- Bedrock held ranged weapons ----------------------------------------------
# Bedrock holds a bow or crossbow through Mojang's own attachable: a texture
# mesh (the sprite extruded one pixel deep) placed by geometry.bow_* /
# crossbow_* and moved by animation.bow.* / crossbow.*. Those files are copied
# unchanged from Mojang/bedrock-samples 1.26.50.4 (bedrock_vanilla/) and given a
# sprite rendered from the 3D sculpt, so Thornwhisper and Runelock sit in the
# hand at the vanilla size and place, including the first-person pull. A
# texture mesh always spans 16 units, whatever the texture resolution. The
# two-handed draw and crossbow charge poses are the player's own animations,
# keyed to item names in Mojang's player controllers (ranged_player_controllers).
VANILLA = EVERGARDEN / 'tools/bedrock_vanilla'
SPRITE = 64
# Our draw state -> (Mojang geometry suffix, attachable texture/geometry key).
VANILLA_STATE = {
    'bow': {'standby': ('standby', 'default'), 'pulling_0': ('pulling_0', 'bow_pulling_0'),
            'pulling_1': ('pulling_1', 'bow_pulling_1'), 'pulling_2': ('pulling_2', 'bow_pulling_2')},
    'crossbow': {'standby': ('standby', 'default'), 'pulling_0': ('pulling_0', 'crossbow_pulling_0'),
                 'pulling_1': ('pulling_1', 'crossbow_pulling_1'), 'pulling_2': ('pulling_2', 'crossbow_pulling_2'),
                 'arrow': ('arrow', 'crossbow_arrow'), 'firework': ('rocket', 'crossbow_rocket')},
}
# Where the hand closes on each sculpt (sprite pixels, y up): the bow grip and the crossbow's pistol grip.
SPRITE_GRIP = {'thornwhisper': (5.24, 10.3), 'runelock': (11.68, 4.32)}
FULL_DRAW = {'bow': 20, 'crossbow': 25}
RANGED_PLAYER_CONTROLLERS = 'animation_controllers/adventurer_ranged_player.controller.json'
THORNWHISPER_IDS = ('voidscape:adventurer_thornwhisper',)
RUNELOCK_LOADED_IDS = ('voidscape:adventurer_runelock_loaded_arrow', 'voidscape:adventurer_runelock_loaded_rocket')
RUNELOCK_IDS = ('voidscape:adventurer_runelock',) + RUNELOCK_LOADED_IDS


def vanilla(name: str):
    return json.loads((VANILLA / name).read_text(encoding='utf8'))


def held_geometry_id(name: str, state: str) -> str:
    return f'geometry.voidscape.adventurer_{name}.{state}'


def held_geometry(name: str, category: str):
    """Mojang's bow/crossbow texture-mesh geometries, one per draw state, under our identifiers."""
    geo = vanilla(f'{category}.geo.json')
    by_suffix = {suffix: state for state, (suffix, _) in VANILLA_STATE[category].items()}
    for entry in geo['minecraft:geometry']:
        suffix = entry['description']['identifier'].removeprefix(f'geometry.{category}_')
        entry['description']['identifier'] = held_geometry_id(name, by_suffix[suffix])
    return geo


def held_animations(name: str, category: str):
    """Mojang's wield / first-person pull animations under our identifiers."""
    prefix = f'animation.voidscape.adventurer_{name}'
    return {prefix + key.removeprefix(f'animation.{category}'): value
            for key, value in vanilla(f'{category}.animation.json')['animations'].items()}


def sprite_fit(name: str, weapons) -> tuple:
    """(grip, scale, shift) fitting every draw state into the 16 px sprite: full size where it fits,
    otherwise scaled about the grip, which moves by at most half a pixel."""
    lo = np.min([c['from'][:2] for w in weapons for c in w.cubes], axis=0)
    hi = np.max([c['to'][:2] for w in weapons for c in w.cubes], axis=0)
    grip = np.array(SPRITE_GRIP[name], dtype=float)
    for scale in np.arange(1.0, .8, -.005):
        low = np.maximum(-(grip + scale * (lo - grip)), -.5)
        high = np.minimum(16 - (grip + scale * (hi - grip)), .5)
        if np.all(low <= high):
            return grip, round(float(scale), 3), np.clip(0.0, low, high)
    raise ValueError(f'{name} does not fit the sprite')


def ranged_sprite(w: Sculpt, tex: Image.Image, fit) -> Image.Image:
    """The sculpt's arrow side (-Z, where the nocked arrow and loaded bolt lie, as in Mojang's sprites)
    laid out in the vanilla sprite frame, shaded from its depth and outlined one texel, as vanilla
    sprites are, so the slimmer sculpt keeps a vanilla bow's weight."""
    grip, scale, shift = fit
    n, step = SPRITE, 16 / SPRITE
    centres = (np.arange(n) + .5) * step
    X = (centres[None, :] - shift[0] - grip[0]) / scale + grip[0]
    Y = ((16 - centres)[:, None] - shift[1] - grip[1]) / scale + grip[1]
    depth = np.full((n, n), -np.inf)
    mats = np.full((n, n), '', dtype=object)
    # A cube shows in every texel whose footprint it overlaps, so one-cell strings stay unbroken when scaled.
    h = step / (2 * scale) - 1e-6
    for c in w.cubes:
        hit = (X + h > c['from'][0]) & (X - h < c['to'][0]) & (Y + h > c['from'][1]) & (Y - h < c['to'][1]) & (-c['from'][2] > depth)
        depth[hit] = -c['from'][2]
        mats[hit] = c['mat']
    filled = np.isfinite(depth)

    def neighbour(dy, dx):
        shifted = np.full((n + 2, n + 2), -np.inf)
        shifted[1:-1, 1:-1] = depth
        return shifted[1 + dy:n + 1 + dy, 1 + dx:n + 1 + dx]

    near = {d: neighbour(*d) for d in ((0, -1), (0, 1), (-1, 0), (1, 0))}
    base = np.where(filled, depth, 0.0)
    level = lambda d: np.where(np.isfinite(near[d]), near[d], base)
    gx = (level((0, 1)) - level((0, -1))) * scale / (2 * step)
    gy = (level((-1, 0)) - level((1, 0))) * scale / (2 * step)
    light = np.array([-1.0, 1.0, 1.4]) / np.linalg.norm([-1.0, 1.0, 1.4])
    lambert = np.clip((-gx * light[0] - gy * light[1] + light[2]) / np.sqrt(gx ** 2 + gy ** 2 + 1), 0, 1)
    shade = np.clip(.45 + .55 * lambert / light[2], .55, 1.2)
    rim = np.any([~np.isfinite(v) for v in near.values()], axis=0)
    occluded = np.any([v > depth + .5 for v in near.values()], axis=0)
    shade = shade * np.where(rim, .82, 1.0) * np.where(occluded, .88, 1.0)
    pixels = np.asarray(tex.convert('RGBA'))
    out = np.zeros((n, n, 4), dtype=np.uint8)
    for j, i in zip(*np.nonzero(filled)):
        u0, v0 = tile(mats[j, i])[:2]
        colour = pixels[v0 * 8 + j % 16, u0 * 8 + i % 16, :3].astype(float)
        factor = max(shade[j, i], 1.0) if mats[j, i] in EMISSIVE else shade[j, i]
        out[j, i, :3] = np.clip(colour * factor, 0, 255).astype(np.uint8)
        out[j, i, 3] = 255
    padded = np.zeros((n + 2, n + 2, 4), dtype=float)
    padded[1:-1, 1:-1] = out
    around = [padded[1 + dy:n + 1 + dy, 1 + dx:n + 1 + dx] for dy, dx in ((0, -1), (0, 1), (-1, 0), (1, 0))]
    count = sum((a[..., 3] > 0).astype(float) for a in around)
    edge = ~filled & (count > 0)
    out[edge, :3] = (sum(a[..., :3] for a in around)[edge] / count[edge, None] * .42).astype(np.uint8)
    out[edge, 3] = 255
    return Image.fromarray(out, 'RGBA')


def held_attachable(name: str, category: str, states) -> dict:
    keys = [VANILLA_STATE[category][state][1] for state in states]
    prefix = f'animation.voidscape.adventurer_{name}'
    textures = {key: f'textures/adventurer_weapons/{name}_held_{state}' for key, state in zip(keys, states)}
    textures['enchanted'] = 'textures/misc/enchanted_item_glint'
    return {
        'identifier': f'voidscape:adventurer_{name}',
        'materials': {'default': 'entity_alphatest', 'enchanted': 'entity_alphatest_glint'},
        'textures': textures,
        'geometry': {key: held_geometry_id(name, state) for key, state in zip(keys, states)},
        'animations': {'wield': prefix + '.wield', 'wield_first_person_pull': prefix + '.wield_first_person_pull'},
        'scripts': {'pre_animation': bedrock_draw_frame(category),
                    'animate': ['wield', {'wield_first_person_pull': 'query.main_hand_item_use_duration > 0.0f && c.is_first_person'}]},
        'render_controllers': [f'controller.render.voidscape.adventurer_{name}_held'],
    }


def held_controller(keys) -> dict:
    """Mojang's bow/crossbow render controller, indexed by our draw frame (or fixed to one loaded key)."""
    frames = len(keys) > 1
    controller = {
        'geometry': 'Array.held_geometry[v.draw_frame]' if frames else f'Geometry.{keys[0]}',
        'materials': [{'*': 'variable.is_enchanted ? Material.enchanted : Material.default'}],
        'textures': ['Array.held_texture[v.draw_frame]' if frames else f'Texture.{keys[0]}', 'Texture.enchanted'],
    }
    if frames:
        controller['arrays'] = {'textures': {'Array.held_texture': [f'Texture.{key}' for key in keys]},
                                'geometries': {'Array.held_geometry': [f'Geometry.{key}' for key in keys]}}
    return controller


def ranged_player_controllers() -> dict:
    """Mojang's player root and crossbow controllers with Thornwhisper counted as a bow and Runelock
    (standby or loaded) as a crossbow, so Bedrock plays the same two-handed draw, charge and aim poses."""
    names = lambda ids: "query.is_item_name_any('slot.weapon.mainhand', " + ', '.join(f"'{i}'" for i in ids) + ')'
    swaps = {
        "query.get_equipped_item_name == 'bow'": f"(query.get_equipped_item_name == 'bow' || {names(THORNWHISPER_IDS)})",
        "query.get_equipped_item_name == 'crossbow'": f"(query.get_equipped_item_name == 'crossbow' || {names(RUNELOCK_IDS)})",
        "query.get_equipped_item_name != 'crossbow'": f"!(query.get_equipped_item_name == 'crossbow' || {names(RUNELOCK_IDS)})",
        'query.item_is_charged': f'(query.item_is_charged || {names(RUNELOCK_LOADED_IDS)})',
        "query.is_item_name_any('slot.weapon.mainhand', 'minecraft:bow')": names(('minecraft:bow',) + THORNWHISPER_IDS),
    }
    expected = {"query.get_equipped_item_name == 'bow'": 1, "query.get_equipped_item_name == 'crossbow'": 2,
                "query.get_equipped_item_name != 'crossbow'": 2, 'query.item_is_charged': 4,
                "query.is_item_name_any('slot.weapon.mainhand', 'minecraft:bow')": 1}
    pattern = re.compile('|'.join(re.escape(key) for key in sorted(swaps, key=len, reverse=True)))
    counts = dict.fromkeys(swaps, 0)

    def swap(match):
        counts[match.group(0)] += 1
        return swaps[match.group(0)]

    def walk(value):
        if isinstance(value, str):
            return pattern.sub(swap, value)
        if isinstance(value, list):
            return [walk(v) for v in value]
        if isinstance(value, dict):
            return {k: walk(v) for k, v in value.items()}
        return value

    result = walk(vanilla('player.animation_controllers.json'))
    if counts != expected:
        raise ValueError(f"Mojang's player controllers changed: {counts}")
    return result


def register(java: Path, bedrock: Path, item_atlas: dict, mappings: dict):
    """Export all twenty weapons' Java/Bedrock assets."""
    from legendary_weapon_preview import render_icon_frames

    ART.mkdir(parents=True, exist_ok=True)
    tex = atlas()
    stream = io.BytesIO()
    tex.save(stream, format='PNG')
    texture = stream.getvalue()

    jp = java / 'assets/voidscape/textures/item/adventurer_weapons'
    jp.mkdir(parents=True, exist_ok=True)
    (jp / 'materials.png').write_bytes(texture)

    bp = bedrock / 'textures/adventurer_weapons'
    bp.mkdir(parents=True, exist_ok=True)
    (bp / 'materials.png').write_bytes(texture)

    animations = {}
    controllers = {}

    for row in CATALOG:
        name, title, subtitle, thai_title, base_mat, category, sanctum, grip_y, rgb = row
        ranged = category in RANGED_STATES
        states = java_draw_states(name)
        w_standby = make_weapon(name, 'standby')
        w_source = make_weapon(name, states[3]) if ranged else w_standby
        write_json(ART / f'{name}.geometry.json', {'id': name, 'cubes': w_standby.cubes})
        write_json(ART / f'adventurer_{name}.bbmodel', bbmodel(w_source, texture))

        # Java 3D models, one per draw/charge state.
        write_json(java / f'assets/voidscape/models/item/adventurer_weapons/{name}.json', java_model(w_standby))
        for state in states[1:]:
            write_json(java / f'assets/voidscape/models/item/adventurer_weapons/{name}_{state}.json', java_model(make_weapon(name, state), state))

        if ranged:
            # Static icons per state: Java's GUI branch selects them through the same
            # use/charge properties as the held model; Bedrock shows the standby icon.
            for state in states:
                suffix = '' if state == 'standby' else '_' + state
                icon = ranged_icon(make_weapon(name, state), tex)
                icon.save(jp / f'{name}{suffix}_icon.png')
                icon.save(bp / f'{name}{suffix}_icon.png')
                if state == 'standby':
                    icon.save(ART / f'{name}_icon.png')
                write_json(java / f'assets/voidscape/models/item/adventurer_weapons/{name}{suffix}_icon.json', {
                    'parent': 'minecraft:item/generated',
                    'textures': {'layer0': f'voidscape:item/adventurer_weapons/{name}{suffix}_icon'},
                })
        else:
            # 8-frame held VFX & animated icons
            frames = [fx_frame(name, f) for f in range(8)]
            strip = Image.new('RGBA', (128, 1024))
            for f, im in enumerate(frames):
                strip.paste(im, (0, f * 128))
                im.save(bp / f'{name}_fx_{f}.png')
            strip.save(jp / f'{name}_fx.png')
            write_json(jp / f'{name}_fx.png.mcmeta', {'animation': {'frametime': 2, 'interpolate': False, 'width': 128, 'height': 128}})

            icons = render_icon_frames(_preview_sculpt(w_standby, tex), tex, frames)
            iconstrip = Image.new('RGBA', (128, 1024))
            for f, im in enumerate(icons):
                iconstrip.paste(im, (0, f * 128))
            iconstrip.save(jp / f'{name}_icon.png')
            write_json(jp / f'{name}_icon.png.mcmeta', {'animation': {'frametime': 2, 'interpolate': False, 'width': 128, 'height': 128}})
            icons[0].save(bp / f'{name}_icon.png')
            icons[0].save(ART / f'{name}_icon.png')
            write_json(java / f'assets/voidscape/models/item/adventurer_weapons/{name}_icon.json', {
                'parent': 'minecraft:item/generated',
                'textures': {'layer0': f'voidscape:item/adventurer_weapons/{name}_icon'},
            })

        write_json(java / f'assets/voidscape/items/adventurer_{name}.json', java_item_definition(name))

        prefix = f'animation.voidscape.adventurer_{name}'
        if ranged:
            # Bedrock: Mojang's bow/crossbow attachable (texture mesh, wield and
            # first-person pull) showing a sprite of the sculpt per draw state,
            # chosen from the elapsed use ticks.
            weapons = [make_weapon(name, state) for state in states]
            fit = sprite_fit(name, weapons)
            for state, w in zip(states, weapons):
                ranged_sprite(w, tex, fit).save(bp / f'{name}_held_{state}.png')
            write_json(bedrock / f'models/entity/adventurer_weapons/{name}.geo.json', held_geometry(name, category))
            animations.update(held_animations(name, category))
            attach = held_attachable(name, category, states)
            controllers[attach['render_controllers'][0]] = held_controller([VANILLA_STATE[category][s][1] for s in states[:4]])
        else:
            # Bedrock: the java2bedrock rig, so both editions use one display transform.
            write_json(bedrock / f'models/entity/adventurer_weapons/{name}.geo.json', bedrock_geometry(w_standby, False))
            write_json(bedrock / f'models/entity/adventurer_weapons/{name}_fx.geo.json', bedrock_geometry(w_standby, True))
            animations[prefix + '.first'] = bedrock_pose(True, name)
            animations[prefix + '.third'] = bedrock_pose(False, name)
            attach = {
                'identifier': f'voidscape:adventurer_{name}',
                'materials': {'default': 'entity_alphatest', 'glow': 'entity_emissive_alpha'},
                'textures': {'default': 'textures/adventurer_weapons/materials'},
                'geometry': {'default': f'geometry.voidscape.adventurer_{name}'},
                'animations': {'first': prefix + '.first', 'third': prefix + '.third'},
                'scripts': {'animate': [{'first': 'context.is_first_person == 1.0'}, {'third': 'context.is_first_person == 0.0'}]},
                'render_controllers': ['controller.render.voidscape.adventurer_body'],
            }
            attach['textures'].update({f'fx{f}': f'textures/adventurer_weapons/{name}_fx_{f}' for f in range(8)})
            attach['geometry']['fx'] = f'geometry.voidscape.adventurer_{name}_fx'
            attach['animations']['idle'] = prefix + '.idle'
            attach['scripts']['animate'].append('idle')
            attach['render_controllers'].append(f'controller.render.voidscape.adventurer_{name}_fx')
            animations[prefix + '.idle'] = {'loop': True, 'animation_length': 2, 'bones': {
                'aura': {'scale': ['1 + math.sin(q.life_time * 240) * 0.012', 1, '1 + math.sin(q.life_time * 240) * 0.012']},
                'ribbon': {'rotation': [0, 0, 'math.sin(q.life_time * 180) * 3']},
                'focus': {'position': [0, 'math.sin(q.life_time * 150) * 0.22', 0]},
                'orbit': {'rotation': [0, 'math.sin(q.life_time * 140) * 8', 0]},
            }}
            controllers[f'controller.render.voidscape.adventurer_{name}_fx'] = {
                'arrays': {'textures': {'Array.arcs': [f'Texture.fx{f}' for f in range(8)]}},
                'geometry': 'Geometry.fx',
                'materials': [{'*': 'Material.glow'}],
                'textures': ['Array.arcs[math.mod(math.floor(q.life_time * 10), 8)]'],
            }
        write_json(bedrock / f'attachables/adventurer_weapons/{name}.json', {
            'format_version': '1.10.0',
            'minecraft:attachable': {'description': attach},
        })
        icon_key = f'voidscape.adventurer_{name}'
        # One static UI texture per Bedrock definition; loaded crossbows are their own definitions.
        item_atlas[icon_key] = {'textures': f'textures/adventurer_weapons/{name}_icon'}
        mappings['items'].setdefault('minecraft:' + base_mat, []).append({
            'type': 'definition',
            'model': f'voidscape:adventurer_{name}',
            'bedrock_identifier': f'voidscape:adventurer_{name}',
            'display_name': title,
            'bedrock_options': {
                'icon': icon_key,
                'allow_offhand': True,
                'display_handheld': True,
                'creative_category': 'equipment',
            },
        })
        if name == 'runelock':
            register_runelock_loaded_states(bedrock, item_atlas, mappings, controllers, attach, title)

    controllers['controller.render.voidscape.adventurer_body'] = {
        'geometry': 'Geometry.default',
        'materials': [{'*': 'Material.default'}, {'inlay': 'Material.glow'}, {'focus': 'Material.glow'}],
        'textures': ['Texture.default'],
    }
    write_json(bedrock / 'animations/adventurer_weapons.animation.json', {'format_version': '1.8.0', 'animations': animations})
    write_json(bedrock / 'render_controllers/adventurer_weapons.render.json', {'format_version': '1.8.0', 'render_controllers': controllers})
    write_json(bedrock / RANGED_PLAYER_CONTROLLERS, ranged_player_controllers())


def make_preview_sheet():
    from legendary_weapon_preview import render_item, render_icon_frames, glow_composite, font

    PREVIEWS.mkdir(parents=True, exist_ok=True)
    tex = atlas()
    shown = list(CATALOG)
    cols = 6
    rows = math.ceil(len(shown) / cols)
    card_w, card_h = 340, 440
    width = 60 + cols * (card_w + 18)
    height = 170 + rows * (card_h + 18)
    bg = Image.new('RGBA', (width, height), (9, 13, 24, 255))
    d = ImageDraw.Draw(bg)
    d.text((40, 26), 'E V E R G A R D E N   /   S A N C T U M   A R M O U R Y', font=font(18), fill='#93a4bc')
    d.text((38, 54), f'{len(shown)} RARE ADVENTURER WEAPONS (JAVA + BEDROCK + MODELENGINE)', font=font(42, True), fill='#f6dfaf')
    d.text((40, 114), 'SWORD  ·  SPEAR  ·  MACE  ·  BOW  ·  CROSSBOW', font=font(18), fill='#7ad3ff')

    for idx, row in enumerate(shown):
        name, title, subtitle, thai_title, base_mat, category, sanctum, grip_y, color = row
        c = idx % cols
        r = idx // cols
        x = 36 + c * (card_w + 18)
        y = 154 + r * (card_h + 18)
        d.rounded_rectangle((x, y, x + card_w, y + card_h), radius=10, fill=(15, 22, 36, 255), outline=(*color, 150), width=2)
        d.rectangle((x + 2, y + 2, x + card_w - 2, y + 6), fill=color)
        d.text((x + 16, y + 14), f'{IDS.index(name) + 1:02d} · {category.upper()} · {sanctum.upper()}', font=font(12, True), fill=color)
        d.text((x + 16, y + 34), title.upper(), font=font(24, True), fill='#fff5dc')
        d.text((x + 16, y + 64), subtitle, font=font(12), fill='#9fb0c7')

        sculpt = _preview_sculpt(make_weapon(name), tex)
        im = render_item(sculpt, tex, fx_frame(name, 1), size=360, yaw=25, tilt=-18, elevation=12)
        im = im.resize((300, 300), Image.Resampling.NEAREST)
        glow_composite(bg, im, (x + 20, y + 88), color)
        d = ImageDraw.Draw(bg)
        icon = render_icon_frames(_preview_sculpt(make_weapon(name), tex), tex, [fx_frame(name, 0)])[0].resize((56, 56), Image.Resampling.NEAREST)
        d.rectangle((x + card_w - 72, y + card_h - 72, x + card_w - 12, y + card_h - 12), fill=(24, 31, 46, 255), outline='#8e9aaa', width=2)
        bg.alpha_composite(icon, (x + card_w - 70, y + card_h - 70))
        d = ImageDraw.Draw(bg)
        d.text((x + 16, y + card_h - 34), f'Base: {base_mat}', font=font(12), fill='#b8c6d9')

    out_path = PREVIEWS / 'adventurer-weapons.png'
    bg.convert('RGB').save(out_path)
    print('Saved preview:', out_path)


if __name__ == '__main__':
    make_preview_sheet()
