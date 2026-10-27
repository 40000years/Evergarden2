"""Render review images for Banshee Wail (banshee-mourning-veil) from the shipped blueprint.

Offline renders of the exact .bbmodel geometry, atlas, keyframes and aura loop (true view,
not mirrored). Not an in-game capture: no client lighting, fog or particles.
Writes previews/banshee-mourning/ only. Pass --before with older blueprints to compare.
"""
import argparse
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'evergarden/tools'))
from boss_render import Rig, render  # noqa: E402

OUT = ROOT / 'previews/banshee-mourning'
MODEL = ROOT / 'evergarden/src/main/resources/modelengine/rivers/arkhaia_banshee_wail.bbmodel'
ACCENT = (124, 244, 206)
SPAN, CENTER = 60, np.array([0, 23.5])
POSES = [('hover', .5), ('idle', 1.0), ('walk', .4), ('attack_windup', .8), ('attack_active', .2), ('attack_recover', .3),
         ('curse_windup', 1.0), ('curse_active', .3), ('stun', 1.0), ('reveal', .6), ('reform', .45), ('cast_windup', 1.0)]
MOTION = [('hover', 2.0), ('attack_windup', .8), ('attack_active', .4), ('attack_recover', .7), ('hover', 1.0), ('curse_windup', 1.0),
          ('curse_active', .5), ('curse_recover', .7), ('reveal', 1.0), ('reform', 1.0), ('hover', 1.0)]


def font(size, bold=False):
    try:
        return ImageFont.truetype('C:/Windows/Fonts/' + ('segoeuib.ttf' if bold else 'segoeui.ttf'), size)
    except OSError:
        return ImageFont.load_default()


def caption(image, text, size=None, color=ACCENT):
    ImageDraw.Draw(image).text((image.width * .03, image.height * .02), text, font=font(size or max(14, image.width // 30), True), fill=color)
    return image


def shot(rig, clip, t, yaw=22, elevation=6, size=640, span=SPAN, center=CENTER, ground=True):
    image, _ = render(rig, clip, t, yaw=yaw, elevation=elevation, size=size, span=span, center=np.asarray(center, float), ground=ground)
    return image


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--before', nargs='*', type=Path, default=[], help='Older blueprints shown beside the new rig, as label=path')
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    model = json.loads(MODEL.read_text(encoding='utf8'))
    manifest = json.loads(MODEL.with_name('arkhaia_banshee_wail-manifest.json').read_text(encoding='utf8'))
    rig = Rig(model)
    hero = shot(rig, 'hover', .5, yaw=20, elevation=-10, size=1100, ground=False)
    caption(hero, 'BANSHEE WAIL  ·  The Mourning Veil', 40)
    ImageDraw.Draw(hero).text((34, 92), f"{manifest['cubes']} cuboids · {manifest['bones']} bones · {len(manifest['animations'])} clips · "
                                        'offline render of the shipped rig', font=font(22), fill=tuple(int(c * .75) for c in ACCENT))
    hero.save(OUT / 'banshee-hero.png')
    turn = Image.new('RGB', (640 * 4, 640))
    for i, (yaw, label) in enumerate(((0, 'FRONT'), (35, 'THREE-QUARTER'), (90, 'SIDE'), (180, 'BACK'))):
        turn.paste(caption(shot(rig, 'hover', .5, yaw=yaw), label), (i * 640, 0))
    turn.save(OUT / 'banshee-turnaround.png')
    # How players see her: from the ground, looking up at a ten-block spirit.
    view = Image.new('RGB', (640 * 3, 640))
    for i, (clip, t, yaw) in enumerate((('hover', .5, 10), ('attack_active', .2, 25), ('curse_windup', 1.0, -20))):
        view.paste(caption(shot(rig, clip, t, yaw=yaw, elevation=-26, center=CENTER + (0, 3)), f'player view · {clip}'), (i * 640, 0))
    view.save(OUT / 'banshee-player-view.png')
    cols, size = 4, 520
    poses = Image.new('RGB', (cols * size, size * ((len(POSES) + cols - 1) // cols)))
    for i, (clip, t) in enumerate(POSES):
        poses.paste(caption(shot(rig, clip, t, yaw=28, elevation=2, size=size, span=SPAN + 6, center=CENTER + (0, 2)), f'{clip} {t:.2f}s'),
                    ((i % cols) * size, (i // cols) * size))
    poses.save(OUT / 'banshee-poses.png')
    frames = []
    for clip, length in MOTION:
        for t in np.arange(0, length, 1 / 12.5):
            frame, _ = render(rig, clip, float(t), yaw=24, elevation=0, size=460, span=SPAN + 6, center=CENTER + (0, 2), supersample=1,
                              aura_time=len(frames) / 12.5)
            frames.append(caption(frame, clip, 18))
    frames[0].save(OUT / 'banshee-motion.gif', save_all=True, append_images=frames[1:], duration=80, loop=0, optimize=True)
    compare = [('NEW · Mourning Veil', rig)]
    for item in args.before:
        label, path = str(item).split('=', 1) if '=' in str(item) else (Path(item).stem, item)
        compare.append((label, Rig(json.loads(Path(path).read_text(encoding='utf8')))))
    if len(compare) > 1:
        sheet = Image.new('RGB', (640 * len(compare), 640))
        for i, (label, r) in enumerate(reversed(compare)):
            sheet.paste(caption(shot(r, 'hover', .5, yaw=20, elevation=2), label), (i * 640, 0))
        sheet.save(OUT / 'banshee-before-after.png')
    print('Previews:', OUT, len(frames), 'motion frames')


if __name__ == '__main__':
    main()
