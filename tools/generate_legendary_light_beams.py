"""Deterministic transparent technical VFX sprites for Heavenly Light v3.

These are code-native light profiles, not repainted circle illustrations.  The
three accepted illustrated sigil/halo/mist PNGs are deliberately never opened
for writing. Run from any directory with Pillow installed.
"""
from __future__ import annotations

import hashlib
import json
import math
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'advance-magic/art/effects/legendary-light'
BEACON = 'elemental_legendary_light_beacon'
SHOT = 'elemental_legendary_light_shot'
MOTE = 'elemental_legendary_light_mote'


def smoothstep(edge0, edge1, value):
    t = max(0.0, min(1.0, (value - edge0) / (edge1 - edge0)))
    return t * t * (3.0 - 2.0 * t)


def ray(shot=False):
    width, height = 256, 1024
    # x positions stay straight throughout the full beam. The coloured lanes
    # have clear transparent air between them, never a painted smoke curtain.
    lanes = ([(-.16, (80, 229, 255)), (.16, (255, 111, 205))] if shot else [
        (-.32, (142, 112, 255)), (-.205, (74, 219, 255)),
        (-.105, (105, 255, 197)), (.105, (255, 220, 93)),
        (.205, (255, 112, 194)), (.32, (167, 143, 255)),
    ])
    image = Image.new('RGBA', (width, height))
    pixels = image.load()
    for y in range(height):
        v = y / (height - 1)
        end = smoothstep(.018, .085 if shot else .065, v)
        end *= 1 - smoothstep(.915 if shot else .935, .982, v)
        # Subtle light pulses, while the lane geometry remains fully straight.
        pulse = .94 + .06 * math.sin(v * math.tau * 3)
        for x in range(width):
            u = (x + .5) / width - .5
            samples = []
            core_sigma = .014 if shot else .016
            core = 252 * math.exp(-.5 * (u / core_sigma) ** 2)
            samples.append((core, (255, 255, 240)))
            samples.append((46 * math.exp(-.5 * (u / (.044 if shot else .052)) ** 2), (255, 250, 222)))
            for position, color in lanes:
                sigma = .010 if shot else .008
                body = 228 * math.exp(-.5 * ((u - position) / sigma) ** 2)
                glow = 43 * math.exp(-.5 * ((u - position) / .024) ** 2)
                samples.append((body + glow, color))
            amount = sum(alpha for alpha, _ in samples)
            alpha = min(255, round(amount * end * pulse))
            if alpha < 2:
                continue
            color = tuple(round(sum(a * rgb[c] for a, rgb in samples) / amount) for c in range(3))
            pixels[x, y] = (*color, alpha)
    return image


def mote():
    scale = 4
    mask = Image.new('L', (64 * scale, 64 * scale))
    draw = ImageDraw.Draw(mask)
    left, right = [], []
    for i in range(65):
        t = i / 64
        x = 20 + 22 * t + 5 * math.sin(math.pi * t)
        y = 49 - 36 * t
        half = 3.7 * math.sin(math.pi * t) ** .8
        left.append(((x - half) * scale, (y - half * .45) * scale))
        right.append(((x + half) * scale, (y + half * .45) * scale))
    draw.polygon(left + right[::-1], fill=245)
    # A tiny separate fleck reads as magical confetti rather than opaque smoke.
    draw.ellipse((17 * scale, 22 * scale, 19 * scale, 24 * scale), fill=220)
    glow = mask.filter(ImageFilter.GaussianBlur(1.8 * scale)).point(lambda a: round(a * .26))
    mask = ImageChops.lighter(mask, glow).resize((64, 64), Image.Resampling.LANCZOS)
    image = Image.new('RGBA', (64, 64), (255, 255, 255, 0))
    image.putalpha(mask)
    return image


def main():
    ART.mkdir(parents=True, exist_ok=True)
    textures = []
    for name, image in ((BEACON, ray()), (SHOT, ray(shot=True)), (MOTE, mote())):
        path = ART / (name + '.png')
        image.save(path, optimize=True)
        alpha = image.getchannel('A')
        occupied = sum(count for value, count in enumerate(alpha.histogram()) if value >= 64)
        textures.append({'id': name, 'size': list(image.size), 'mode': image.mode,
                         'alpha_bbox': list(alpha.getbbox()),
                         'coverage_alpha_ge_64': round(occupied / (image.width * image.height), 5),
                         'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
    manifest = {'version': 1, 'generator': 'tools/generate_legendary_light_beams.py',
                'method': 'Deterministic Pillow technical profiles and curved white petal mask; no generated scene or painted smoke.',
                'preserved_artwork': ['elemental_legendary_light_sigil.png',
                                      'elemental_legendary_light_halo.png', 'elemental_legendary_light_mist.png'],
                'textures': textures}
    (ART / 'technical-vfx-manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf8')
    print(json.dumps(manifest, indent=2))


if __name__ == '__main__':
    main()
