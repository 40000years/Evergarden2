"""Render preview images for Styx Remastered: The Dread Executioner."""
import base64
import io
import json
import math
from pathlib import Path
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'evergarden/tools'))

from boss_render import Rig, render

MODEL_PATH = ROOT / 'evergarden/src/main/resources/modelengine/rivers/arkhaia_styx.bbmodel'
BAK_PATH = ROOT / 'evergarden/src/main/resources/modelengine/rivers/arkhaia_styx.bbmodel.bak'
PREVIEWS_DIR = ROOT / 'previews/styx-remastered'
PREVIEWS_DIR.mkdir(parents=True, exist_ok=True)

def load_rig(path):
    with open(path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    return Rig(data)

def render_multi_angle_sheet():
    rig = load_rig(MODEL_PATH)
    views = [
        ('Front View (Executioner Stance & Colossal Great-Axe)', dict(clip='idle', time=0.0, yaw=0.0, elevation=4.0)),
        ('Three-Quarter Right (Demon Horns & Bearded Blade)', dict(clip='idle', time=0.4, yaw=25.0, elevation=8.0)),
        ('Three-Quarter Left (Rear Pick & Severed Chains)', dict(clip='idle', time=0.8, yaw=-25.0, elevation=8.0)),
        ('Back View (Spine Vertebrae & Tattered Mantle)', dict(clip='idle', time=1.2, yaw=180.0, elevation=6.0)),
    ]

    tiles = []
    tile_size = 640
    for title, params in views:
        img, _ = render(rig, size=tile_size, **params)
        draw = ImageDraw.Draw(img)
        # title banner
        draw.rectangle([0, 0, tile_size, 38], fill=(16, 8, 12, 220))
        draw.text((15, 10), title, fill=(255, 180, 190))
        tiles.append(img)

    sheet = Image.new('RGBA', (tile_size * 2, tile_size * 2), (18, 10, 14, 255))
    sheet.paste(tiles[0], (0, 0))
    sheet.paste(tiles[1], (tile_size, 0))
    sheet.paste(tiles[2], (0, tile_size))
    sheet.paste(tiles[3], (tile_size, tile_size))

    out_path = PREVIEWS_DIR / 'styx-executioner-multiangle.png'
    sheet.save(out_path)
    print(f"Saved: {out_path}")

def render_great_axe_closeup():
    rig = load_rig(MODEL_PATH)
    views = [
        ('Great-Axe Front Silhouette', dict(clip='idle', time=0.0, yaw=0.0, elevation=2.0)),
        ('Axe Crescent Blade & Blood Fullers', dict(clip='idle', time=0.4, yaw=30.0, elevation=12.0)),
        ('Rear Skull-Cracker Pick & Socket', dict(clip='idle', time=0.8, yaw=-35.0, elevation=10.0)),
    ]
    tile_w, tile_h = 600, 700
    tiles = []
    for title, params in views:
        img, _ = render(rig, size=tile_w, **params)
        draw = ImageDraw.Draw(img)
        draw.rectangle([0, 0, tile_w, 40], fill=(22, 6, 10, 230))
        draw.text((15, 12), title, fill=(255, 120, 140))
        tiles.append(img)

    sheet = Image.new('RGBA', (tile_w * 3, tile_h), (18, 10, 14, 255))
    for i, t in enumerate(tiles):
        sheet.paste(t, (i * tile_w, 0))

    out_path = PREVIEWS_DIR / 'styx-great-axe-closeup.png'
    sheet.save(out_path)
    print(f"Saved: {out_path}")

def render_attacks_sheet():
    rig = load_rig(MODEL_PATH)
    actions = [
        ('Idle (Dread Guard)', 'idle', 0.6, 20.0),
        ('Walk (Warlord Stride)', 'walk', 0.5, 25.0),
        ('Slash Active (Cleave)', 'slash_active', 0.2, 20.0),
        ('Thrust Active (Impale)', 'thrust_active', 0.2, -15.0),
        ('Blood Active (Sanguine Strike)', 'blood_active', 0.25, 15.0),
        ('Berserk Active (Dread Frenzy)', 'berserk_active', 0.25, -25.0),
    ]

    tile_w, tile_h = 500, 560
    tiles = []
    for title, clip, t, yaw in actions:
        if clip not in rig.clips:
            print(f"Clip {clip} not found, using idle")
            clip, t = 'idle', 0.0
        img, _ = render(rig, clip=clip, time=t, yaw=yaw, elevation=6.0, size=tile_w)
        draw = ImageDraw.Draw(img)
        draw.rectangle([0, 0, tile_w, 36], fill=(20, 8, 12, 220))
        draw.text((15, 10), f"{title} [{clip}]", fill=(255, 140, 160))
        tiles.append(img)

    sheet = Image.new('RGBA', (tile_w * 3, tile_h * 2), (18, 10, 14, 255))
    for i, t in enumerate(tiles):
        x = (i % 3) * tile_w
        y = (i // 3) * tile_h
        sheet.paste(t, (x, y))

    out_path = PREVIEWS_DIR / 'styx-attacks-sheet.png'
    sheet.save(out_path)
    print(f"Saved: {out_path}")

def render_comparison():
    if not BAK_PATH.exists():
        print(f"Backup {BAK_PATH} not found, skipping comparison")
        return

    rig_old = load_rig(BAK_PATH)
    rig_new = load_rig(MODEL_PATH)

    sz = 640
    img_old, _ = render(rig_old, clip='idle', time=0.0, yaw=20.0, elevation=6.0, size=sz)
    img_new, _ = render(rig_new, clip='idle', time=0.0, yaw=20.0, elevation=6.0, size=sz)

    draw_old = ImageDraw.Draw(img_old)
    draw_old.rectangle([0, 0, sz, 44], fill=(16, 16, 16, 230))
    draw_old.text((20, 12), "ORIGINAL STYX (472 cubes, flat palette)", fill=(200, 200, 200))

    draw_new = ImageDraw.Draw(img_new)
    draw_new.rectangle([0, 0, sz, 44], fill=(24, 6, 10, 230))
    draw_new.text((20, 12), "REMASTERED STYX (492 cubes, AAA Dread Executioner Great-Axe)", fill=(255, 160, 180))

    comp = Image.new('RGBA', (sz * 2, sz), (18, 10, 14, 255))
    comp.paste(img_old, (0, 0))
    comp.paste(img_new, (sz, 0))

    out_path = PREVIEWS_DIR / 'styx-evolution-comparison.png'
    comp.save(out_path)
    print(f"Saved: {out_path}")

def main():
    print("Rendering Styx Remastered preview suite...")
    render_multi_angle_sheet()
    render_great_axe_closeup()
    render_attacks_sheet()
    render_comparison()
    print("All Styx previews rendered successfully!")

if __name__ == '__main__':
    main()
