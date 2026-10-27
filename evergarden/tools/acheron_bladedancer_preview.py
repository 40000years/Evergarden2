"""Preview sheets for the Weeping Bladedancer Acheron: before/after, turnaround, combat
actions and the split (main body with its two echoes at true relative scale).

Renders the shipped blueprints in modelengine/rivers with boss_render (orthographic,
asset renders, not game screenshots).

    python evergarden/tools/acheron_bladedancer_preview.py --before output/acheron-bladedancer-20261023/before/arkhaia_acheron.bbmodel
"""
import argparse
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

from boss_render import Rig, label, render

ROOT = Path(__file__).resolve().parents[2]
RIVERS = ROOT / 'evergarden/src/main/resources/modelengine/rivers'
OUT = ROOT / 'previews/acheron-bladedancer'
BACKGROUND = (18, 12, 20)


def font(size, bold=False):
    try:
        return ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf' if bold else 'C:/Windows/Fonts/segoeui.ttf', size)
    except OSError:
        return ImageFont.load_default()


def load(path):
    return Rig(json.loads(Path(path).read_text(encoding='utf8')))


def tile(rig, clip, time, yaw, size=560, elevation=8, span=46, center=(0, 19.5), title=None, sub=None):
    image, _ = render(rig, clip, time, yaw=yaw, elevation=elevation, size=size, span=span, center=center, background=BACKGROUND)
    return label(image, title, sub, color=(255, 196, 210)) if title else image


def sheet(tiles, cols, heading, note):
    w, h = tiles[0].size
    rows = (len(tiles) + cols - 1) // cols
    out = Image.new('RGB', (cols * w, 96 + rows * h), BACKGROUND)
    d = ImageDraw.Draw(out)
    d.text((24, 16), heading, font=font(30, True), fill=(255, 214, 224))
    d.text((24, 58), note, font=font(16), fill=(196, 150, 166))
    for i, t in enumerate(tiles):
        out.paste(t, ((i % cols) * w, 96 + (i // cols) * h))
    return out


def before_after(before, after):
    tiles = [tile(before, 'idle', 0, 0, title='BEFORE  crimson-v3', sub='front'),
             tile(before, 'idle', 0, 35, title='BEFORE', sub='three-quarter'),
             tile(after, 'idle', 0, 0, title='AFTER  bladedancer-v1', sub='front'),
             tile(after, 'idle', 0, 35, title='AFTER', sub='three-quarter')]
    return sheet(tiles, 4, 'ACHERON  -  BEFORE / AFTER', 'Same camera and scale. Idle pose at t=0.')


def turnaround(rig):
    tiles = [tile(rig, 'idle', .6, yaw, title=name) for yaw, name in
             ((0, 'Front'), (40, 'Three-quarter'), (95, 'Side'), (145, 'Back three-quarter'), (180, 'Back'))]
    return sheet(tiles, 5, 'ACHERON  -  THE WEEPING BLADEDANCER',
                 'Porcelain sorrow mask under a crimson cowl, swept ruby horns, rib lames around a ruby core, two curved Sorrow-Quills.')


def actions(rig):
    poses = [('idle', .6, 30, 'Idle', 'square chest, low guard'),
             ('walk', .3, 100, 'Walk', 'low stalk, quills trailing'),
             ('cast_windup', 1.0, 30, 'Spin wind-up', 'coiled against the turn'),
             ('cast_active', .4, 30, 'Spin', '3 turns in 1.2 s, quills at full reach'),
             ('attack_windup', .8, 30, 'Attack wind-up', 'quill drawn high'),
             ('attack_active', .2, 70, 'Attack contact', 'lunging cross-cut (0.2 s)'),
             ('attack_active', .4, 100, 'Follow-through', 'reverse sweep of the left quill'),
             ('stun', .8, 30, 'Stun', 'down on one knee, quills in the floor'),
             ('ritual_windup', 1.0, 20, 'Ritual wind-up', 'quills crossed before the mask'),
             ('ritual_active', .14, 20, 'Ritual burst', 'torn apart: the split / the snare')]
    tiles = [tile(rig, c, t, yaw, size=480, title=title, sub=sub) for c, t, yaw, title, sub in poses]
    return sheet(tiles, 5, 'ACHERON  -  COMBAT ACTIONS', 'Frames sampled from the exported clips (the same keys Java and Bedrock play).')


def split(rig, echo):
    """The burst of ritual_active with the two echoes standing where split() places them (left and right)."""
    size, span = 620, 60
    main = tile(rig, 'ritual_active', .14, 0, size=size, span=span, center=(0, 22))
    ghosts = [tile(echo, 'idle', t, yaw, size=size, span=span, center=(0, 22)) for t, yaw in ((.4, -25), (1.6, 25))]
    out = Image.new('RGB', (size * 3, size + 150), BACKGROUND)
    d = ImageDraw.Draw(out)
    d.text((24, 16), 'ACHERON  -  THE SPLIT', font=font(30, True), fill=(255, 214, 224))
    d.text((24, 58), 'ritual_active tears the quills apart and two echoes stand 3-5 blocks to either side. '
                     'Echo: 60% scale, same rig and clips, no halo, crimson-violet afterimage texture.',
           font=font(16), fill=(196, 150, 166))
    out.paste(ghosts[0], (0, 96))
    out.paste(main, (size, 96))
    out.paste(ghosts[1], (size * 2, 96))
    for i, text in enumerate(('Echo (60%)', 'Acheron  ritual_active 0.14 s', 'Echo (60%)')):
        d.text((i * size + 24, 96 + size + 12), text, font=font(20, True), fill=(255, 196, 210))
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--before', type=Path, help='Previous arkhaia_acheron.bbmodel for the before/after sheet')
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    rig, echo = load(RIVERS / 'arkhaia_acheron.bbmodel'), load(RIVERS / 'arkhaia_acheron_echo.bbmodel')
    saved = []
    if args.before:
        before_after(load(args.before), rig).save(OUT / 'before-after.png')
        saved.append('before-after.png')
    turnaround(rig).save(OUT / 'turnaround.png')
    actions(rig).save(OUT / 'actions.png')
    split(rig, echo).save(OUT / 'split.png')
    saved += ['turnaround.png', 'actions.png', 'split.png']
    print('Saved', ', '.join(str(OUT / name) for name in saved))


if __name__ == '__main__':
    main()
