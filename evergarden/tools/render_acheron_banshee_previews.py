"""Render high-fidelity preview images for Remastered Crimson Acheron and Sea-Foam Banshee Wail."""
import json
from pathlib import Path
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'evergarden/tools'))

from boss_render import Rig, render

# ---------------------------------------------------------------------------
# CRIMSON ACHERON PREVIEWS
# ---------------------------------------------------------------------------
def render_acheron_previews():
    print("Rendering Acheron Crimson Sovereign preview suite...")
    acheron_dir = ROOT / 'previews/acheron-remastered'
    acheron_dir.mkdir(parents=True, exist_ok=True)

    model_path = ROOT / 'evergarden/src/main/resources/modelengine/rivers/arkhaia_acheron.bbmodel'
    echo_path  = ROOT / 'evergarden/src/main/resources/modelengine/rivers/arkhaia_acheron_echo.bbmodel'
    bak_path   = ROOT / 'evergarden/src/main/resources/modelengine/rivers/arkhaia_acheron.bbmodel.bak'

    with open(model_path, 'r', encoding='utf-8') as f:
        rig = Rig(json.load(f))

    # 1. Multi-Angle Sheet
    views = [
        ('Front View (Dual Ruby Sorrow-Quill Blades)', dict(clip='idle', time=0.0, yaw=0.0, elevation=4.0)),
        ('Three-Quarter Right (Duelist Blade-Breaker & Horns)', dict(clip='idle', time=0.4, yaw=25.0, elevation=8.0)),
        ('Three-Quarter Left (Crimson Mask & Sanguine Core)', dict(clip='idle', time=0.8, yaw=-25.0, elevation=8.0)),
        ('Back View (Articulated Spine & Crimson Sashes)', dict(clip='idle', time=1.2, yaw=180.0, elevation=6.0)),
    ]
    tiles = []
    tile_size = 640
    for title, params in views:
        img, _ = render(rig, size=tile_size, **params)
        draw = ImageDraw.Draw(img)
        draw.rectangle([0, 0, tile_size, 38], fill=(22, 6, 12, 220))
        draw.text((15, 10), title, fill=(255, 160, 180))
        tiles.append(img)

    sheet = Image.new('RGBA', (tile_size * 2, tile_size * 2), (18, 6, 10, 255))
    sheet.paste(tiles[0], (0, 0))
    sheet.paste(tiles[1], (tile_size, 0))
    sheet.paste(tiles[2], (0, tile_size))
    sheet.paste(tiles[3], (tile_size, tile_size))
    sheet.save(acheron_dir / 'acheron-multiangle.png')
    print("Saved acheron-multiangle.png")

    # 2. Combat Actions Sheet
    actions = [
        ('Idle (Crimson Stance)', 'idle', 0.6, 20.0),
        ('Walk (Phantom Stride)', 'walk', 0.5, 25.0),
        ('Attack Active (Twin Sorrow Rend)', 'attack_active', 0.2, -15.0),
        ('Cast Active (Sanguine Wave)', 'cast_active', 0.4, 20.0),
        ('Ritual Active (Soul Split Spawn)', 'ritual_active', 0.25, 0.0),
        ('Stun (Flow Interrupted)', 'stun', 0.7, -25.0),
    ]
    tile_w, tile_h = 500, 560
    act_tiles = []
    for title, clip, t, yaw in actions:
        img, _ = render(rig, clip=clip, time=t, yaw=yaw, elevation=6.0, size=tile_w)
        draw = ImageDraw.Draw(img)
        draw.rectangle([0, 0, tile_w, 36], fill=(26, 8, 14, 220))
        draw.text((15, 10), f"{title} [{clip}]", fill=(255, 175, 195))
        act_tiles.append(img)

    act_sheet = Image.new('RGBA', (tile_w * 3, tile_h * 2), (18, 6, 10, 255))
    for i, t in enumerate(act_tiles):
        act_sheet.paste(t, ((i % 3) * tile_w, (i // 3) * tile_h))
    act_sheet.save(acheron_dir / 'acheron-actions-sheet.png')
    print("Saved acheron-actions-sheet.png")

    # 3. Echo Clone Comparison (Primary Sovereign vs 0.6x Echo Clone)
    with open(echo_path, 'r', encoding='utf-8') as f:
        rig_echo = Rig(json.load(f))
    sz = 640
    img_pri, _ = render(rig, clip='ritual_active', time=0.25, yaw=15.0, elevation=4.0, size=sz)
    img_echo, _ = render(rig_echo, clip='attack_active', time=0.2, yaw=-20.0, elevation=4.0, size=sz)
    draw_pri = ImageDraw.Draw(img_pri)
    draw_pri.rectangle([0, 0, sz, 42], fill=(28, 6, 14, 230))
    draw_pri.text((20, 10), "ACHERON SOVEREIGN (Scale 1.0, Crimson #f54268)", fill=(255, 180, 200))
    draw_echo = ImageDraw.Draw(img_echo)
    draw_echo.rectangle([0, 0, sz, 42], fill=(22, 5, 10, 230))
    draw_echo.text((20, 10), "ACHERON ECHO CLONE (Scale 0.6x, Ritual Split Clone)", fill=(255, 210, 225))
    echo_comp = Image.new('RGBA', (sz * 2, sz), (18, 6, 10, 255))
    echo_comp.paste(img_pri, (0, 0))
    echo_comp.paste(img_echo, (sz, 0))
    echo_comp.save(acheron_dir / 'acheron-echo-comparison.png')
    print("Saved acheron-echo-comparison.png")

    # 4. Evolution Comparison (Old vs Remastered)
    if bak_path.exists():
        with open(bak_path, 'r', encoding='utf-8') as f:
            rig_old = Rig(json.load(f))
        img_old, _ = render(rig_old, clip='idle', time=0.0, yaw=20.0, elevation=6.0, size=sz)
        img_new, _ = render(rig, clip='idle', time=0.0, yaw=20.0, elevation=6.0, size=sz)
        draw_o = ImageDraw.Draw(img_old)
        draw_o.rectangle([0, 0, sz, 42], fill=(16, 16, 16, 230))
        draw_o.text((20, 10), "ORIGINAL ACHERON (Flat palette, 382 cubes)", fill=(200, 200, 200))
        draw_n = ImageDraw.Draw(img_new)
        draw_n.rectangle([0, 0, sz, 42], fill=(30, 8, 16, 230))
        draw_n.text((20, 10), "REMASTERED CRIMSON ACHERON (Authentic Red #f54268, Zero Gaps)", fill=(255, 185, 205))
        comp = Image.new('RGBA', (sz * 2, sz), (18, 6, 10, 255))
        comp.paste(img_old, (0, 0))
        comp.paste(img_new, (sz, 0))
        comp.save(acheron_dir / 'acheron-evolution-comparison.png')
        print("Saved acheron-evolution-comparison.png")

# ---------------------------------------------------------------------------
# SEAFOAM BANSHEE WAIL PREVIEWS
# ---------------------------------------------------------------------------
def render_banshee_previews():
    print("Rendering Banshee Wail Sea-Foam Queen preview suite...")
    banshee_dir = ROOT / 'previews/banshee-remastered'
    banshee_dir.mkdir(parents=True, exist_ok=True)

    model_path = ROOT / 'evergarden/src/main/resources/modelengine/rivers/arkhaia_banshee_wail.bbmodel'
    bak_path   = ROOT / 'evergarden/src/main/resources/modelengine/rivers/arkhaia_banshee_wail.bbmodel.bak'

    with open(model_path, 'r', encoding='utf-8') as f:
        rig = Rig(json.load(f))

    # 1. Multi-Angle Sheet
    views = [
        ('Front View (Screaming Wail Maw & 18-Segment Halo)', dict(clip='hover', time=0.0, yaw=0.0, elevation=4.0)),
        ('Three-Quarter Right (9 Spirit Prongs & Ghost Ballgown)', dict(clip='hover', time=0.4, yaw=25.0, elevation=8.0)),
        ('Three-Quarter Left (14-Panel Pleated Dress & Claws)', dict(clip='hover', time=0.8, yaw=-25.0, elevation=8.0)),
        ('Back View (Orbiting Soul Wisps & 6 Mourning Veils)', dict(clip='hover', time=1.2, yaw=180.0, elevation=6.0)),
    ]
    tiles = []
    tile_size = 640
    for title, params in views:
        img, _ = render(rig, size=tile_size, **params)
        draw = ImageDraw.Draw(img)
        draw.rectangle([0, 0, tile_size, 38], fill=(4, 18, 16, 220))
        draw.text((15, 10), title, fill=(124, 244, 206))
        tiles.append(img)

    sheet = Image.new('RGBA', (tile_size * 2, tile_size * 2), (4, 14, 12, 255))
    sheet.paste(tiles[0], (0, 0))
    sheet.paste(tiles[1], (tile_size, 0))
    sheet.paste(tiles[2], (0, tile_size))
    sheet.paste(tiles[3], (tile_size, tile_size))
    sheet.save(banshee_dir / 'banshee-multiangle.png')
    print("Saved banshee-multiangle.png")

    # 2. Combat Actions Sheet
    actions = [
        ('Hover (Ghost Ballgown Billow)', 'hover', 0.6, 20.0),
        ('Curse Active (Sonic Shockwave Shriek)', 'curse_active', 0.25, 0.0),
        ('Attack Active (Soul Claws Rend)', 'attack_active', 0.2, -20.0),
        ('Cast Active (Mourning Wail Pulse)', 'cast_active', 0.25, 15.0),
        ('Reveal (Ghost Manifestation)', 'reveal', 0.5, -15.0),
        ('Stun (Echo Resonance)', 'stun', 0.7, -25.0),
    ]
    tile_w, tile_h = 500, 560
    act_tiles = []
    for title, clip, t, yaw in actions:
        img, _ = render(rig, clip=clip, time=t, yaw=yaw, elevation=6.0, size=tile_w)
        draw = ImageDraw.Draw(img)
        draw.rectangle([0, 0, tile_w, 36], fill=(6, 22, 20, 220))
        draw.text((15, 10), f"{title} [{clip}]", fill=(145, 250, 215))
        act_tiles.append(img)

    act_sheet = Image.new('RGBA', (tile_w * 3, tile_h * 2), (4, 14, 12, 255))
    for i, t in enumerate(act_tiles):
        act_sheet.paste(t, ((i % 3) * tile_w, (i // 3) * tile_h))
    act_sheet.save(banshee_dir / 'banshee-actions-sheet.png')
    print("Saved banshee-actions-sheet.png")

    # 3. Evolution Comparison (Old vs Remastered)
    if bak_path.exists():
        with open(bak_path, 'r', encoding='utf-8') as f:
            rig_old = Rig(json.load(f))
        sz = 640
        img_old, _ = render(rig_old, clip='hover', time=0.0, yaw=20.0, elevation=6.0, size=sz)
        img_new, _ = render(rig, clip='hover', time=0.0, yaw=20.0, elevation=6.0, size=sz)
        draw_o = ImageDraw.Draw(img_old)
        draw_o.rectangle([0, 0, sz, 42], fill=(16, 16, 16, 230))
        draw_o.text((20, 10), "ORIGINAL BANSHEE (319 cubes)", fill=(200, 200, 200))
        draw_n = ImageDraw.Draw(img_new)
        draw_n.rectangle([0, 0, sz, 42], fill=(6, 28, 24, 230))
        draw_n.text((20, 10), "REMASTERED BANSHEE (Authentic Sea-Foam #7cf4ce & Pleated Ballgown)", fill=(124, 244, 206))
        comp = Image.new('RGBA', (sz * 2, sz), (4, 14, 12, 255))
        comp.paste(img_old, (0, 0))
        comp.paste(img_new, (sz, 0))
        comp.save(banshee_dir / 'banshee-evolution-comparison.png')
        print("Saved banshee-evolution-comparison.png")

def main():
    render_acheron_previews()
    render_banshee_previews()
    print("All preview suites rendered successfully!")

if __name__ == '__main__':
    main()
