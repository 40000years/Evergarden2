"""Spell-staff and pet menu textures, shared by Java and Bedrock.

Same contract as skilltree_gui_assets: every menu element is a paper item with
item_model advance_magic:menu_<name>, and Geyser maps the same ids. Both menus also
reuse the Skill Tree set (panel, help, close, lock).

Pet portraits come from the turnaround renders in dist/evergarden-pet-preview.zip
(one per species and stage), keyed off their flat backdrop; a dimmed copy marks
pets that cannot be picked. Bedrock pet forms use the same files as button images
(textures/menu/<name>).

    python tools/menu_gui_assets.py      # preview sheets in previews/menu-gui/
"""
from __future__ import annotations

import io
import json
import math
from pathlib import Path
import zipfile

import numpy as np
from PIL import Image

import skilltree_gui_assets as sg
from skilltree_gui_assets import S, Canvas, Glyph, base, plaque, glyph, draw_text, cov, rrect, circle, rgrad, mix, star, sigil

ROOT = Path(__file__).resolve().parents[1]
NS = sg.NS
PREFIX = 'menu_'
BASE = sg.BASE
PET_RENDERS = ROOT / 'dist/evergarden-pet-preview.zip'
SPECIES = ('aetherius', 'ignis_draconis', 'zephyros', 'solaris', 'fenrir', 'arcane_nine_tails', 'frost_wyvern',
           'astral_owl', 'crimson_cerberus', 'shadow_panther', 'blood_gargoyle', 'runic_golem', 'world_tree_entling',
           'crystal_tortoise', 'abyssal_manta', 'alchemist_mimic', 'chrono_fairy')
STAGES = {'baby': (94, 232, 122), 'medium': (79, 180, 255), 'adult': (255, 209, 102)}
WHITE = (255, 255, 255)
GREY_PLAQUE = ((76, 70, 92), (52, 47, 64), (34, 30, 44))


# ---------------------------------------------------------------------------
# Extra glyphs (unit box)


def icon_glyph(name, box=(.18, .18, .82, .82)):
    g = Glyph(box)
    if name == 'eject':
        g.poly([(.5, .14), (.86, .56), (.14, .56)])
        g.d.rounded_rectangle([g.p(.14, .68), g.p(.86, .84)], radius=g.w(.04), fill=255)
    elif name == 'left':
        g.poly([(.12, .5), (.5, .14), (.5, .36), (.88, .36), (.88, .64), (.5, .64), (.5, .86)])
    elif name == 'upgrade':
        g.line([(.18, .5), (.5, .22), (.82, .5)], .14)
        g.line([(.18, .8), (.5, .52), (.82, .8)], .14)
    elif name == 'book':
        g.d.rounded_rectangle([g.p(.16, .12), g.p(.84, .88)], radius=g.w(.06), fill=255)
        g.d.rounded_rectangle([g.p(.24, .2), g.p(.76, .8)], radius=g.w(.03), fill=0)
        g.line([(.3, .12), (.3, .88)], .06)
        star(g, 4, .17, .05, cx=.56, cy=.48)
    elif name == 'shield':
        g.poly([(.5, .1), (.86, .22), (.82, .56), (.5, .9), (.18, .56), (.14, .22)])
    elif name == 'swap':
        g.poly([(.1, .32), (.34, .12), (.34, .25), (.86, .25), (.86, .4), (.34, .4), (.34, .52)])
        g.poly([(.9, .68), (.66, .48), (.66, .6), (.14, .6), (.14, .75), (.66, .75), (.66, .88)])
    elif name == 'paw':
        g.ellipse(.5, .64, .21)
        for x, y in ((.22, .38), (.4, .2), (.6, .2), (.78, .38)):
            g.ellipse(x, y, .1)
    elif name == 'home':
        g.poly([(.5, .1), (.9, .46), (.1, .46)])
        g.d.rectangle([g.p(.2, .44), g.p(.8, .88)], fill=255)
        g.d.rounded_rectangle([g.p(.42, .6), g.p(.58, .88)], radius=g.w(.03), fill=0)
    elif name == 'tag':
        g.poly([(.1, .5), (.34, .2), (.9, .2), (.9, .8), (.34, .8)])
        g.ellipse(.36, .5, .07, 0)
    elif name == 'leaf':
        g.poly([(.14, .86), (.2, .44), (.44, .2), (.86, .14), (.8, .56), (.56, .8)])
        g.line([(.18, .82), (.66, .34)], .05, 0)
    elif name == 'heart':
        g.ellipse(.33, .38, .2)
        g.ellipse(.67, .38, .2)
        g.poly([(.14, .46), (.86, .46), (.5, .88)])
        g.poly([(.2, .38), (.8, .38), (.8, .5), (.2, .5)])
    elif name == 'egg':
        g.ellipse(.5, .58, .3)
        g.poly([(.24, .55), (.76, .55), (.66, .2), (.5, .1), (.34, .2)])
        g.ellipse(.5, .32, .17)
    elif name == 'nest':
        g.ellipse(.5, .42, .2)
        g.poly([(.32, .42), (.68, .42), (.6, .2), (.5, .14), (.4, .2)])
        g.arc(.5, .4, .4, 15, 165, .14, caps=True)
    elif name == 'clock':
        g.ellipse(.5, .5, .4)
        g.ellipse(.5, .5, .32, 0)
        g.line([(.5, .5), (.5, .26)], .08)
        g.line([(.5, .5), (.68, .6)], .08)
    elif name == 'check':
        g.line([(.18, .52), (.42, .76), (.84, .26)], .16)
    elif name == 'cross':
        g.line([(.22, .22), (.78, .78)], .16)
        g.line([(.78, .22), (.22, .78)], .16)
    elif name == 'steps':
        for x, y, r in ((.32, .66, .14), (.66, .34, .14)):
            g.d.ellipse([g.p(x - r * .75, y - r), g.p(x + r * .75, y + r)], fill=255)
        for x, y in ((.32, .88), (.66, .56)):
            g.ellipse(x, y, .07)
    elif name == 'pin':
        g.ellipse(.5, .34, .24)
        g.poly([(.3, .44), (.7, .44), (.5, .92)])
        g.ellipse(.5, .34, .09, 0)
    elif name == 'feather':
        g.poly([(.84, .1), (.6, .2), (.36, .42), (.24, .66), (.3, .72), (.54, .62), (.76, .4)])
        g.line([(.14, .9), (.7, .26)], .05)
    elif name == 'sword':
        g.poly([(.86, .1), (.9, .14), (.44, .66), (.36, .58)])
        g.line([(.22, .5), (.52, .8)], .1)
        g.line([(.34, .66), (.14, .86)], .1)
    elif name == 'eye':
        g.d.ellipse([g.p(.08, .26), g.p(.92, .74)], fill=255)
        g.d.ellipse([g.p(.16, .32), g.p(.84, .68)], fill=0)
        g.ellipse(.5, .5, .15)
    elif name == 'eye_off':
        g.d.ellipse([g.p(.08, .26), g.p(.92, .74)], fill=255)
        g.d.ellipse([g.p(.16, .32), g.p(.84, .68)], fill=0)
        g.ellipse(.5, .5, .15)
        g.line([(.14, .86), (.86, .14)], .16, 0)
        g.line([(.18, .82), (.82, .18)], .08)
    elif name == 'sprout':
        g.line([(.5, .9), (.5, .42)], .1)
        g.poly([(.5, .5), (.2, .4), (.1, .16), (.38, .22)])
        g.poly([(.5, .42), (.8, .3), (.92, .1), (.62, .14)])
    elif name == 'cross_med':
        g.d.rounded_rectangle([g.p(.38, .14), g.p(.62, .86)], radius=g.w(.04), fill=255)
        g.d.rounded_rectangle([g.p(.14, .38), g.p(.86, .62)], radius=g.w(.04), fill=255)
    elif name == 'stats':
        for x, h in ((.2, .36), (.44, .62), (.68, .48)):
            g.d.rounded_rectangle([g.p(x, .88 - h), g.p(x + .18, .88)], radius=g.w(.03), fill=255)
    elif name == 'scroll':
        g.d.rounded_rectangle([g.p(.22, .16), g.p(.78, .84)], radius=g.w(.05), fill=255)
        g.d.rounded_rectangle([g.p(.12, .1), g.p(.88, .24)], radius=g.w(.07), fill=255)
        g.d.rounded_rectangle([g.p(.12, .76), g.p(.88, .9)], radius=g.w(.07), fill=255)
        for y in (.38, .5, .62):
            g.line([(.34, y), (.66, y)], .05, 0)
    elif name == 'plus':
        g.d.rounded_rectangle([g.p(.42, .16), g.p(.58, .84)], radius=g.w(.04), fill=255)
        g.d.rounded_rectangle([g.p(.16, .42), g.p(.84, .58)], radius=g.w(.04), fill=255)
    else:
        return sigil(name, box)
    return g.mask()


def button(name, colors, ink=WHITE, box=(.2, .2, .8, .8), gloss=.16):
    c = base()
    top, bottom, edge = colors
    plaque(c, top, bottom, edge, gloss=gloss)
    return glyph(c, icon_glyph(name, box), ink, mix(edge, (0, 0, 0), .5), .55)


GREEN = ((104, 240, 152), (33, 163, 90), (14, 84, 46))
ORANGE = ((255, 190, 92), (196, 111, 18), (110, 58, 6))
RED = ((255, 98, 98), (169, 29, 42), (96, 10, 20))
DARK_RED = ((120, 46, 58), (78, 24, 36), (44, 10, 18))
BLUE = ((96, 170, 255), (42, 99, 201), (20, 48, 110))
PURPLE = ((176, 132, 255), (98, 58, 196), (48, 24, 104))
PINK = ((255, 140, 196), (204, 58, 128), (110, 20, 64))
TEAL = ((112, 230, 220), (28, 150, 150), (10, 78, 80))
GOLD_PLAQUE = ((255, 220, 120), (200, 140, 30), (110, 70, 8))
SLATE = ((70, 62, 92), (44, 38, 60), (100, 88, 130))


def socket(plus=True):
    """An empty slot: dashed rim, faint plus."""
    c = base()
    shape = rrect(4, 4, S - 4, S - 4, 6)
    c.fill(cov(shape), (16, 13, 23))
    rim = cov(np.abs(shape) - .5)
    dash = (np.mod(np.arctan2(sg.Y - 16, sg.X - 16) * 8 / math.pi, 2) < 1.15).astype(float)
    c.fill(rim * dash, (110, 98, 140))
    if plus:
        c.fill(np.maximum(cov(rrect(14.5, 10, 17.5, 22, 1)), cov(rrect(10, 14.5, 22, 17.5, 1))), (110, 98, 140))
    return c


def ring(k, color, center, label=None):
    """Progress ring in tenths with a glyph in the middle (bond, growth, health)."""
    c = base()
    dist = np.hypot(sg.X - 16, sg.Y - 16)
    shape = np.maximum(dist - 13.4, 9.8 - dist)
    c.fill(cov(shape), (36, 30, 50))
    if k:
        ang = np.mod(np.degrees(np.arctan2(sg.X - 16, -(sg.Y - 16))), 360)
        part = cov(shape) * np.clip(k * 36 - ang + .5, 0, 1)
        c.glow(part, color, 2, .4)
        c.fill(part, rgrad(mix(color, WHITE, .35), color, 16, 16, 13.4))
    return glyph(c, icon_glyph(center, (.33, .33, .67, .67)), mix(color, WHITE, .3) if k else (120, 110, 140), (6, 4, 10), .7)


def requirement(kind, ok):
    """A breeding condition: its own glyph on green or red with a tick or cross badge; grey while undecided."""
    c = base()
    if ok is None:
        plaque(c, *SLATE, gloss=.06)
        return glyph(c, icon_glyph(kind, (.2, .2, .8, .8)), (150, 140, 175), (6, 4, 10), .6)
    plaque(c, *(GREEN if ok else RED), gloss=.12)
    glyph(c, icon_glyph(kind, (.16, .14, .7, .68)), WHITE, (0, 0, 0), .35)
    badge = circle(24, 24, 6.2)
    c.fill(cov(badge + .8), (20, 14, 28))
    c.fill(cov(badge), (255, 255, 255))
    return glyph(c, icon_glyph('check' if ok else 'cross', (.62, .62, .88, .88)), (40, 170, 90) if ok else (200, 40, 50), None)


def label(kind):
    """Row label: a glyph and an arrow pointing into the row."""
    c = base()
    plaque(c, *SLATE, gloss=.06)
    glyph(c, icon_glyph(kind, (.12, .2, .58, .8)), (210, 200, 230), (6, 4, 10), .6)
    return glyph(c, icon_glyph('left', (.86, .36, .58, .64)), (255, 209, 102), (6, 4, 10), .6)


def check(ok):
    c = base()
    plaque(c, *(GREEN if ok else RED), gloss=.12)
    return glyph(c, icon_glyph('check' if ok else 'cross', (.22, .22, .78, .78)), WHITE, (0, 0, 0), .35)


# ---------------------------------------------------------------------------
# Pet portraits


def pet_renders():
    """species -> stage -> RGBA crop on transparent, from the preview turnarounds."""
    out = {}
    with zipfile.ZipFile(PET_RENDERS) as z:
        for species in SPECIES:
            for stage in STAGES:
                im = np.asarray(Image.open(io.BytesIO(z.read(f'{species}-{stage}.png'))).convert('RGB')).astype(float)
                alpha = np.clip((np.abs(im - im[0, 0]).sum(-1) - 6) / 30, 0, 1)
                ys, xs = np.nonzero(alpha > .5)
                cy, cx = (ys.min() + ys.max()) // 2, (xs.min() + xs.max()) // 2
                side = max(ys.max() - ys.min(), xs.max() - xs.min()) + 10
                rgba = Image.fromarray(np.dstack([im, alpha * 255]).astype(np.uint8), 'RGBA')
                out.setdefault(species, {})[stage] = rgba.crop((cx - side // 2, cy - side // 2, cx + side // 2, cy + side // 2))
    return out


def portrait(render, stage, dim=False):
    c = base()
    edge = STAGES[stage]
    plaque(c, (40, 34, 56), (26, 22, 36), mix(edge, (40, 34, 56), .55 if dim else 0), inset=1, radius=6, gloss=.05)
    art = np.asarray(render.resize((28, 28), Image.Resampling.LANCZOS)).astype(float) / 255
    layer = np.zeros((S, S, 4))
    layer[2:30, 2:30] = art
    rgb_, a = layer[..., :3] * 255, layer[..., 3]
    if dim:
        grey = rgb_.mean(-1, keepdims=True)
        rgb_ = (grey * .55 + rgb_ * .15) * .7
        a = a * .8
    c.fill(np.roll(np.roll(a, 1, 0), 1, 1), (6, 4, 10), .5)
    c.fill(a, rgb_)
    return c


# ---------------------------------------------------------------------------


def textures():
    """name -> (image, Bedrock display name)."""
    out = {
        # staff workbench
        'socket': (socket(), 'Empty slot'),
        'extract': (button('eject', ORANGE), 'Extract'),
        'move_left': (button('left', BLUE), 'Move'),
        'swap': (button('swap', PURPLE), 'Swap'),
        'upgrade': (button('upgrade', GREEN), 'Upgrade'),
        'upgrade_off': (button('upgrade', GREY_PLAQUE, (118, 109, 138), gloss=.06), 'Upgrade'),
        'upgrade_armed': (button('upgrade', GOLD_PLAQUE), 'Upgrade'),
        'codex': (button('book', PURPLE), 'Codex'),
        'shield_on': (button('shield', TEAL), 'Protect'),
        'shield_off': (button('shield', GREY_PLAQUE, (118, 109, 138), gloss=.06), 'Protect'),
        'back': (button('left', SLATE, (210, 200, 230)), 'Back'),
        # pets
        'summon': (button('paw', GREEN), 'Summon'),
        'dismiss': (button('home', ORANGE), 'Dismiss'),
        'rename': (button('tag', BLUE), 'Rename'),
        'release': (button('leaf', DARK_RED, (255, 214, 214)), 'Release'),
        'release_armed': (button('leaf', RED), 'Release'),
        'breed': (button('heart', PINK), 'Breed'),
        'breed_off': (button('heart', GREY_PLAQUE, (118, 109, 138), gloss=.06), 'Breed'),
        'nest': (button('nest', ORANGE), 'Nest'),
        'nest_wait': (button('clock', BLUE), 'Nest'),
        'egg_ready': (button('egg', GOLD_PLAQUE), 'Egg'),
        'cancel': (button('cross', DARK_RED, (255, 214, 214)), 'Cancel'),
        'cancel_armed': (button('cross', RED), 'Cancel'),
        'check_ok': (check(True), 'Ready'),
        'check_no': (check(False), 'Not ready'),
        'skills': (button('book', PURPLE), 'Skills'),
        'stats': (button('stats', TEAL), 'Stats'),
        'eye_on': (button('eye_off', TEAL), 'Hide pets'),
        'eye_off': (button('eye', GREY_PLAQUE, (200, 190, 220), gloss=.06), 'Hide pets'),
        'pick': (socket(), 'Pick'),
        'label_scrolls': (label('scroll'), 'Scrolls'),
        'label_pets': (label('paw'), 'Pets'),
        'page': (button('left', SLATE, (210, 200, 230)), 'Page'),
    }
    for kind, glyph_name in (('adult', 'sprout'), ('species', 'paw'), ('bond', 'heart'), ('rest', 'clock'), ('daily', 'egg')):
        out[f'req_{kind}_ok'] = (requirement(glyph_name, True), 'Ready')
        out[f'req_{kind}_no'] = (requirement(glyph_name, False), 'Not ready')
        out[f'req_{kind}_wait'] = (requirement(glyph_name, None), 'Pick two pets')
    for mode, glyph_name in (('follow', 'steps'), ('stay', 'pin'), ('perch', 'feather'), ('assist', 'sword')):
        out[f'mode_{mode}'] = (button(glyph_name, SLATE, (210, 200, 230), gloss=.06), 'Mode')
        out[f'mode_{mode}_on'] = (button(glyph_name, GOLD_PLAQUE), 'Mode')
    for k in range(11):
        out[f'bond_{k}'] = (ring(k, (255, 112, 176), 'heart'), 'Bond')
        out[f'growth_{k}'] = (ring(k, (94, 232, 122), 'sprout'), 'Growth')
        out[f'health_{k}'] = (ring(k, (255, 84, 84), 'cross_med'), 'Health')
    renders = pet_renders()
    for species in SPECIES:
        for stage in STAGES:
            out[f'pet_{species}_{stage}'] = (portrait(renders[species][stage], stage), species.replace('_', ' ').title())
            out[f'pet_{species}_{stage}_dim'] = (portrait(renders[species][stage], stage, True), species.replace('_', ' ').title())
    return {name: (c.image(), title) for name, (c, title) in out.items()}


def register(java, bedrock, item_atlas, definitions):
    rows = definitions.setdefault(BASE, [])
    for name, (image, title) in textures().items():
        ident = PREFIX + name
        for path in (java / f'assets/{NS}/textures/item/menu/{name}.png', bedrock / f'textures/menu/{name}.png'):
            path.parent.mkdir(parents=True, exist_ok=True)
            image.save(path)
        sg.write_json(java / f'assets/{NS}/models/item/menu/{name}.json', {
            'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/menu/{name}'}})
        sg.write_json(java / f'assets/{NS}/items/{ident}.json', {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/menu/{name}'}})
        item_atlas[f'{NS}.{ident}'] = {'textures': f'textures/menu/{name}'}
        rows.append({'type': 'definition', 'model': f'{NS}:{ident}', 'bedrock_identifier': f'{NS}:{ident}',
                     'display_name': title,
                     'bedrock_options': {'icon': f'{NS}.{ident}', 'allow_offhand': False, 'display_handheld': False}})


def owned():
    return (f'assets/{NS}/items/{PREFIX}', f'assets/{NS}/models/item/menu/', f'assets/{NS}/textures/item/menu/', 'textures/menu/')


# ---------------------------------------------------------------------------
# Previews (mirror SpellMenu / PetMenu slot maps)


def previews(out=ROOT / 'previews/menu-gui'):
    out.mkdir(parents=True, exist_ok=True)
    tex = {k: v[0] for k, v in textures().items()}
    st = {k: v[0] for k, v in sg.textures().items()}
    scrolls = {}
    with zipfile.ZipFile(ROOT / 'advance-magic/dist/advance-magic-java.zip') as z:
        for spell in ('fireball', 'frost_bolt', 'wind_cutter', 'meteor_rain', 'radiant_bolt', 'thunder_spear', 'stone_wall', 'shadow_bolt'):
            scrolls[spell] = Image.open(io.BytesIO(z.read(f'assets/advance_magic/textures/item/scroll_{spell}.png'))).convert('RGBA')
    with zipfile.ZipFile(ROOT / 'advance-magic/dist/advance-magic-java.zip') as z:
        staff = Image.open(io.BytesIO(z.read('assets/advance_magic/textures/item/elemental_staffs/fire_icon.png'))).convert('RGBA')
    staff = staff.crop((0, 0, staff.width, staff.width))

    def lookup(name):
        if name.startswith('scroll_'):
            return scrolls[name[7:]]
        if name == 'staff':
            return staff
        if name.startswith('st_'):
            return st[name[3:]]
        if name.startswith('vanilla_'):
            return Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        return tex[name]

    images = _Lookup(lookup)
    spell = ['st_panel'] * 54
    spell[9], spell[0], spell[4], spell[5], spell[6], spell[7], spell[8] = 'staff', 'st_help', 'shield_off', 'st_tab_core_on', 'upgrade', 'codex', 'st_close'
    spell[36] = 'label_scrolls'
    for i, name in enumerate(('scroll_fireball', 'scroll_frost_bolt', 'scroll_wind_cutter', 'socket', 'st_lock')):
        spell[11 + i] = name
    for i in range(3):
        spell[20 + i] = 'extract'
    for i in (1, 2):
        spell[29 + i] = 'move_left'
    for i, name in enumerate(('scroll_meteor_rain', 'scroll_radiant_bolt', 'scroll_thunder_spear', 'scroll_stone_wall', 'scroll_shadow_bolt')):
        spell[37 + i] = name
    sg.render_menu(images, spell, 'Staff · คลิกคัมภีร์ด้านล่างเพื่อใส่เวท').save(out / 'spell-menu.png')

    stable = ['st_panel'] * 54
    stable[0], stable[2], stable[4], stable[6], stable[7], stable[8] = 'st_help', 'dismiss', 'pet_fenrir_adult', 'nest_wait', 'eye_off', 'st_close'
    pets = ['pet_fenrir_adult', 'pet_solaris_medium', 'pet_astral_owl_baby', 'pet_crystal_tortoise_adult', 'pet_fenrir_adult', 'pet_chrono_fairy_medium']
    for i, name in enumerate(pets):
        stable[10 + i] = name
    for i in range(6, 9):
        stable[10 + i if 10 + i <= 16 else 19 + i - 7] = 'socket'
    sg.render_menu(images, stable, 'คอกสัตว์เลี้ยง 6/9 · คลิกสัตว์เพื่อจัดการ').save(out / 'pet-stable.png')

    card = ['st_panel'] * 54
    card[0], card[4], card[8] = 'back', 'pet_fenrir_adult', 'st_close'
    card[10], card[11], card[12], card[14], card[15] = 'bond_7', 'growth_10', 'health_9', 'skills', 'stats'
    card[28], card[30], card[32], card[34] = 'dismiss', 'rename', 'breed', 'release'
    for i, mode in enumerate(('follow', 'stay', 'perch', 'assist')):
        card[37 + i * 2] = f'mode_{mode}_on' if mode == 'assist' else f'mode_{mode}'
    sg.render_menu(images, card, '✦ Fenrir · คลิกปุ่มเพื่อสั่ง').save(out / 'pet-card.png')

    nest = ['st_panel'] * 54
    nest[0], nest[8] = 'back', 'st_close'
    nest[11], nest[13], nest[15] = 'pet_fenrir_adult', 'breed_off', 'pick'
    for i, (kind, state) in enumerate((('adult', 'ok'), ('species', 'wait'), ('bond', 'ok'), ('rest', 'ok'), ('daily', 'ok'))):
        nest[19 + i] = f'req_{kind}_{state}'
    nest[24] = 'vanilla_food'
    nest[27] = 'label_pets'
    cands = ['pet_fenrir_adult', 'pet_solaris_medium_dim', 'pet_astral_owl_baby_dim', 'pet_crystal_tortoise_adult_dim', 'pet_chrono_fairy_medium_dim']
    for i, name in enumerate(cands):
        nest[28 + i] = name
    sg.render_menu(images, nest, '✦ รังผสมพันธุ์ · เลือกคู่จากด้านล่าง').save(out / 'pet-nest.png')
    names = [n for n in tex if not n.startswith('pet_') or n.endswith(('fenrir_adult', 'fenrir_adult_dim', 'solaris_baby', 'aetherius_medium'))]
    cols = 16
    sheet = Image.new('RGBA', (cols * 72, math.ceil(len(names) / cols) * 72), (24, 20, 32, 255))
    for i, name in enumerate(names):
        sheet.alpha_composite(tex[name].resize((64, 64), Image.Resampling.NEAREST), ((i % cols) * 72 + 4, (i // cols) * 72 + 4))
    sheet.save(out / 'textures.png')
    print(f'{len(tex)} textures -> {out}')


class _Lookup(dict):
    def __init__(self, fn):
        super().__init__()
        self.fn = fn

    def __getitem__(self, key):
        image = self.fn(key)
        return image if image.size == (S, S) else image.resize((S, S), Image.Resampling.LANCZOS)


if __name__ == '__main__':
    previews()
