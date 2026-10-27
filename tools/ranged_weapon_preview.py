"""Thornwhisper / Runelock preview sheets: what Java and Bedrock each hold, per draw state.

ranged-hold.png: Java third person (ItemInHandLayer on the vanilla player model, arm raised 18 degrees),
the sprite Bedrock's vanilla bow/crossbow attachable holds, and the hotbar icon.
ranged-bedrock.png: every Bedrock sprite next to Mojang's own sprite for that state and the two overlaid
(white = both, gold = ours only, blue = Mojang only), on the same 16 px frame the texture mesh spans.
Arithmetic renders of the shipped data, not game screenshots.

    python tools/ranged_weapon_preview.py [--out-dir previews/adventurer-weapons]
"""
from __future__ import annotations

import argparse
from pathlib import Path
import sys

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
sys.path.insert(0, str(ROOT / 'evergarden/tools'))
import weapon_forge as wf  # noqa: E402
from weapon_hold_preview import font, render, scene  # noqa: E402
import adventurer_weapon_assets as assets  # noqa: E402

ROWS = (('thornwhisper', 'standby', 'idle'), ('thornwhisper', 'pulling_2', 'full draw'),
        ('runelock', 'standby', 'idle'), ('runelock', 'arrow', 'loaded bolt'), ('runelock', 'firework', 'loaded rocket'))
MOJANG = {'firework': 'firework'}


def colours():
    return {name: tuple(int(value[i:i + 2], 16) for i in (1, 3, 5)) for name, value in assets.PALETTE.items()}


def sprites():
    """Each weapon's Bedrock sprites, fitted once across its draw states as the exporter does."""
    tex, out = assets.atlas(), {}
    for name in assets.RANGED:
        states = assets.RANGED_STATES[assets.BY_ID[name][5]]
        weapons = [assets.make_weapon(name, s) for s in states]
        fit = assets.sprite_fit(name, weapons)
        for state, w in zip(states, weapons):
            out[name, state] = assets.ranged_sprite(w, tex, fit)
    return out


def mojang(name, state):
    kind = assets.BY_ID[name][5]
    return Image.open(assets.VANILLA / f'textures/{kind}_{state}.png').convert('RGBA')


def overlay(ours, theirs, size):
    a = np.asarray(ours.resize((64, 64), Image.Resampling.NEAREST))[..., 3] > 0
    b = np.asarray(theirs.resize((64, 64), Image.Resampling.NEAREST))[..., 3] > 0
    img = np.zeros((64, 64, 4), np.uint8)
    img[b] = (90, 120, 255, 255)
    img[a] = (255, 196, 64, 255)
    img[a & b] = (245, 245, 245, 255)
    return Image.fromarray(img, 'RGBA').resize((size, size), Image.Resampling.NEAREST)


def checker(size, cell=16):
    board = Image.new('RGBA', (size, size), (22, 30, 46, 255))
    d = ImageDraw.Draw(board)
    for y in range(0, size, cell):
        for x in range(0, size, cell):
            if (x // cell + y // cell) % 2:
                d.rectangle((x, y, x + cell - 1, y + cell - 1), fill=(28, 38, 56, 255))
    return board


def hold_sheet(held, out):
    cell, label = 330, 230
    sheet = Image.new('RGBA', (label + 3 * cell + 40, 150 + len(ROWS) * (cell + 20)), (10, 15, 26, 255))
    d = ImageDraw.Draw(sheet)
    d.text((30, 18), 'THORNWHISPER  ·  RUNELOCK  —  JAVA / BEDROCK', font=font(30, True), fill='#f3dfb2')
    d.text((30, 60), "Java: the 3D model on Mojang's display (arm raised 18°). Bedrock: the sprite Mojang's own bow/crossbow"
           ' attachable holds. Not a game screenshot.', font=font(16), fill='#93a4bc')
    for i, heading in enumerate(('JAVA  ·  3D model in hand', "BEDROCK  ·  held sprite (vanilla mesh)", 'HOTBAR ICON')):
        d.text((label + 20 + i * cell, 104), heading, font=font(16, True), fill='#a8c6ff')
    palette, tex = colours(), assets.atlas()
    for row, (name, state, caption) in enumerate(ROWS):
        y = 140 + row * (cell + 20)
        w = assets.make_weapon(name, state)
        display = assets.java_display(name, state)
        java = render(scene(w.cubes, palette, assets.EMISSIVE, lambda p: wf.java_third_person(display, p)), cell, scale=11, centre=(-5, 12, -6))
        d.rounded_rectangle((20, y, label + 3 * cell + 20, y + cell), radius=10, fill=(14, 21, 35, 255))
        d.text((36, y + 20), assets.BY_ID[name][1].upper(), font=font(22, True), fill='#fff1d6')
        d.text((36, y + 54), caption, font=font(17), fill='#9fb2cc')
        sheet.alpha_composite(java, (label + 20, y))
        board = checker(256)
        board.alpha_composite(held[name, state].resize((256, 256), Image.Resampling.NEAREST))
        sheet.alpha_composite(board, (label + 20 + cell + (cell - 256) // 2, y + (cell - 256) // 2))
        icon = assets.ranged_icon(w, tex).resize((192, 192), Image.Resampling.NEAREST)
        sheet.alpha_composite(icon, (label + 20 + 2 * cell + (cell - 192) // 2, y + (cell - 192) // 2))
    sheet.convert('RGB').save(out)


def bedrock_sheet(held, out):
    size, gap = 192, 18
    columns = ("MOJANG'S SPRITE", 'OURS (64 px)', 'OVERLAY')
    rows = [(name, state) for name in assets.RANGED for state in assets.RANGED_STATES[assets.BY_ID[name][5]]]
    width = 260 + 3 * (size + gap)
    sheet = Image.new('RGBA', (width, 150 + len(rows) * (size + gap)), (10, 15, 26, 255))
    d = ImageDraw.Draw(sheet)
    d.text((30, 18), 'BEDROCK HELD SPRITES vs MOJANG', font=font(30, True), fill='#f3dfb2')
    d.text((30, 60), 'Same 16-unit texture mesh, wield and pull as the vanilla bow/crossbow. Overlay: white both, gold ours, blue Mojang.',
           font=font(16), fill='#93a4bc')
    for i, heading in enumerate(columns):
        d.text((260 + i * (size + gap), 104), heading, font=font(16, True), fill='#a8c6ff')
    for r, (name, state) in enumerate(rows):
        y = 134 + r * (size + gap)
        d.text((30, y + 70), assets.BY_ID[name][1], font=font(20, True), fill='#fff1d6')
        d.text((30, y + 100), state.replace('_', ' '), font=font(16), fill='#9fb2cc')
        theirs = mojang(name, MOJANG.get(state, state))
        for i, image in enumerate((theirs, held[name, state])):
            board = checker(size)
            board.alpha_composite(image.resize((size, size), Image.Resampling.NEAREST))
            sheet.alpha_composite(board, (260 + i * (size + gap), y))
        sheet.alpha_composite(overlay(held[name, state], theirs, size), (260 + 2 * (size + gap), y))
    sheet.convert('RGB').save(out)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out-dir', type=Path, default=ROOT / 'previews/adventurer-weapons')
    args = parser.parse_args()
    args.out_dir.mkdir(parents=True, exist_ok=True)
    held = sprites()
    hold_sheet(held, args.out_dir / 'ranged-hold.png')
    bedrock_sheet(held, args.out_dir / 'ranged-bedrock.png')
    print(args.out_dir / 'ranged-hold.png', args.out_dir / 'ranged-bedrock.png')


if __name__ == '__main__':
    main()
