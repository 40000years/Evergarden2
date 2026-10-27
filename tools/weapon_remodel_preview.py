"""Before/after sheets for the weapon remodel: designs and the Bedrock third-person hold.

`--before` points at copies of the previous generators (legendary_weapon_assets.py
and adventurer_weapon_assets.py). Asset renders and transform arithmetic only,
not game screenshots.
"""
from __future__ import annotations

import argparse
import importlib.util
from pathlib import Path
import sys

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
sys.path.insert(0, str(ROOT / 'evergarden/tools'))
import adventurer_weapon_assets as rare  # noqa: E402
import legendary_weapon_assets as legendary  # noqa: E402
from legendary_weapon_preview import font, render_item  # noqa: E402
import weapon_hold_preview as hold  # noqa: E402

OUT = ROOT / 'previews/weapon-remodel'


def load(path: Path, name: str):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def render(module, name, kind, size=420):
    tex = module.atlas()
    w = module.make_weapon(name)
    if kind == 'rare':
        w = module._preview_sculpt(w, tex)
    return render_item(w, tex, None, size=size, yaw=26, tilt=-18, elevation=12)


def designs(old_legendary, old_rare):
    names = [('legendary', n) for n in legendary.IDS] + [('rare', n) for n in rare.IDS if n not in rare.TEMPORARILY_DISABLED]
    cols, cw, ch = 6, 360, 300
    rows = (len(names) + cols - 1) // cols
    sheet = Image.new('RGBA', (40 + cols * cw, 130 + rows * ch), (10, 15, 26, 255))
    d = ImageDraw.Draw(sheet)
    d.text((30, 20), 'WEAPON REMODEL  ·  BEFORE / AFTER', font=font(32, True), fill='#f3dfb2')
    d.text((30, 66), f'{len(names)} weapons rendered from the old and new generators (same camera). Asset renders, not game screenshots.',
           font=font(16), fill='#93a4bc')
    for i, (kind, name) in enumerate(names):
        x, y = 20 + (i % cols) * cw, 110 + (i // cols) * ch
        d.rounded_rectangle((x + 4, y + 4, x + cw - 8, y + ch - 8), radius=10, fill=(15, 22, 36, 255),
                            outline='#c9a35c' if kind == 'legendary' else '#4f6f93', width=2)
        d.text((x + 16, y + 12), name.upper(), font=font(18, True), fill='#fff1d6')
        d.text((x + cw - 110, y + 16), kind.upper(), font=font(12), fill='#c9a35c' if kind == 'legendary' else '#7fb2e6')
        old = render(old_legendary if kind == 'legendary' else old_rare, name, kind).resize((170, 170))
        new = render(legendary if kind == 'legendary' else rare, name, kind).resize((170, 170))
        sheet.alpha_composite(old, (x + 8, y + 52))
        sheet.alpha_composite(new, (x + 176, y + 52))
        d.text((x + 60, y + 254), 'BEFORE', font=font(13), fill='#ff9a8a')
        d.text((x + 236, y + 254), 'AFTER', font=font(13), fill='#8fe3a8')
    return sheet


def holds():
    def rgb(palette):
        return {k: tuple(bytes.fromhex(v[1:])) for k, v in palette.items()}
    entries = [(n, legendary.make_weapon(n).cubes, rgb(legendary.PALETTE), legendary.EMISSIVE, legendary.GRIPS[n], legendary.java_display(n))
               for n in ('excalibur', 'longinus', 'mjolnir')]
    entries += [(n, rare.make_weapon(n).cubes, rgb(rare.PALETTE), rare.EMISSIVE, rare.BY_ID[n][7], rare.java_display(n))
                for n in ('moonrift', 'bramblepike', 'glimmermaul')]
    return entries


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--before', type=Path, required=True, help='Folder with the previous generator copies')
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    old_legendary = load(args.before / 'legendary_weapon_assets.py', 'legendary_before')
    old_rare = load(args.before / 'adventurer_weapon_assets.py', 'adventurer_before')
    designs(old_legendary, old_rare).convert('RGB').save(OUT / 'designs-before-after.png')
    hold.comparison(holds(), OUT / 'bedrock-hold-before-after.png')
    print('Saved', OUT / 'designs-before-after.png', OUT / 'bedrock-hold-before-after.png')


if __name__ == '__main__':
    main()
