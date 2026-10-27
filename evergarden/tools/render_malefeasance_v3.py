"""Render preview comparison and animations sheet for Malefeasance v3."""
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

MODEL_PATH = ROOT / 'evergarden/src/main/resources/modelengine/rivers/arkhaia_malefeasance.bbmodel'
BAK_PATH = ROOT / 'evergarden/src/main/resources/modelengine/rivers/arkhaia_malefeasance.bbmodel.bak'
PREVIEWS_DIR = ROOT / 'previews/malefeasance-remastered'
PREVIEWS_DIR.mkdir(parents=True, exist_ok=True)

def load_rig(path):
    with open(path, 'r', encoding='utf-8') as f:
        data = json.load(f)
    return Rig(data)

def render_multi_angle_sheet():
    rig = load_rig(MODEL_PATH)
    views = [
        ('Front View', dict(clip='idle', time=0.0, yaw=0.0, elevation=4.0)),
        ('Three-Quarter Right', dict(clip='idle', time=0.4, yaw=25.0, elevation=8.0)),
        ('Three-Quarter Left', dict(clip='idle', time=0.8, yaw=-25.0, elevation=8.0)),
        ('Back View (Veils & Cape)', dict(clip='idle', time=1.2, yaw=180.0, elevation=6.0)),
    ]

    tiles = []
    tile_size = 640
    for title, params in views:
        img, _ = render(rig, size=tile_size, **params)
        draw = ImageDraw.Draw(img)
        # title banner
        draw.rectangle([0, 0, tile_size, 38], fill=(12, 10, 20, 210))
        draw.text((15, 10), title, fill=(240, 220, 160))
        tiles.append(img)

    sheet = Image.new('RGBA', (tile_size * 2, tile_size * 2), (14, 12, 22, 255))
    sheet.paste(tiles[0], (0, 0))
    sheet.paste(tiles[1], (tile_size, 0))
    sheet.paste(tiles[2], (0, tile_size))
    sheet.paste(tiles[3], (tile_size, tile_size))

    out_path = PREVIEWS_DIR / 'malefeasance-v3-multiangle.png'
    sheet.save(out_path)
    print(f"Saved: {out_path}")

def render_spells_sheet():
    rig = load_rig(MODEL_PATH)
    actions = [
        ('Idle (Starlight Grace)', 'idle', 0.6, 20.0),
        ('Walk (Astral Glide)', 'walk', 0.5, 25.0),
        ('Cast (Dimensional Weave)', 'cast_active', 0.25, 20.0),
        ('Attack (Arcane Burst)', 'attack_active', 0.2, -15.0),
        ('Sky (Supernova Call)', 'sky_active', 0.25, 15.0),
        ('Portal (Void Phase)', 'portal_active', 0.25, -25.0),
    ]

    tile_w, tile_h = 500, 560
    tiles = []
    for title, clip, t, yaw in actions:
        if clip not in rig.clips:
            print(f"Clip {clip} not found, using idle")
            clip, t = 'idle', 0.0
        img, _ = render(rig, clip=clip, time=t, yaw=yaw, elevation=6.0, size=tile_w)
        draw = ImageDraw.Draw(img)
        draw.rectangle([0, 0, tile_w, 36], fill=(12, 10, 20, 210))
        draw.text((15, 10), f"{title} [{clip}]", fill=(140, 230, 255))
        tiles.append(img)

    sheet = Image.new('RGBA', (tile_w * 3, tile_h * 2), (14, 12, 22, 255))
    for i, t in enumerate(tiles):
        r = i // 3
        c = i % 3
        sheet.paste(t, (c * tile_w, r * tile_h))

    out_path = PREVIEWS_DIR / 'malefeasance-v3-spells.png'
    sheet.save(out_path)
    print(f"Saved: {out_path}")

def render_comparison():
    rig_bak = load_rig(BAK_PATH)
    rig_new = load_rig(MODEL_PATH)

    tile_size = 720
    img_old, _ = render(rig_bak, clip='idle', time=0.5, yaw=20.0, elevation=6.0, size=tile_size)
    d_old = ImageDraw.Draw(img_old)
    d_old.rectangle([0, 0, tile_size, 46], fill=(30, 10, 15, 220))
    d_old.text((20, 12), "ORIGINAL MALEFEASANCE (Old Model)", fill=(255, 170, 170))

    img_new, _ = render(rig_new, clip='idle', time=0.5, yaw=20.0, elevation=6.0, size=tile_size)
    d_new = ImageDraw.Draw(img_new)
    d_new.rectangle([0, 0, tile_size, 46], fill=(15, 30, 45, 220))
    d_new.text((20, 12), "REMASTERED V3: ASTRAL ARCH-SORCERESS (AAA Fidelity)", fill=(140, 230, 255))

    comp = Image.new('RGBA', (tile_size * 2, tile_size), (12, 10, 18, 255))
    comp.paste(img_old, (0, 0))
    comp.paste(img_new, (tile_size, 0))

    out_path = PREVIEWS_DIR / 'malefeasance-v3-comparison.png'
    comp.save(out_path)
    print(f"Saved: {out_path}")

def main():
    print("Rendering Malefeasance v3 sheets...")
    render_multi_angle_sheet()
    render_spells_sheet()
    render_comparison()
    print("Done rendering all sheets!")

if __name__ == '__main__':
    main()
