"""Render review images for the Evergarden main-boss rigs from the shipped blueprints.

Offline renders of the exact .bbmodel geometry, atlas, keyframes and aura loop (true view,
not mirrored). Not an in-game capture: no client lighting, fog or particles.
Writes previews/evergarden-bosses/ only.
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'evergarden/tools'))
from boss_render import Rig, render  # noqa: E402

OUT = ROOT / 'previews/evergarden-bosses'
BOSSES = {
    'arkhaia': dict(title='ARKHAIA', subtitle='The Dreadrose Sovereign', accent=(208, 160, 255), span=70, cy=24,
                    poses=[('idle', 0.6), ('walk', 0.2), ('hover', 0.3), ('light_windup', 0.7), ('light_active', 0.2),
                           ('rift_active', 0.4), ('nova_active', 1.0), ('eclipse_windup', 3.5), ('eclipse_active', 0.5),
                           ('portals_active', 1.2), ('stun', 0.9), ('transition', 1.9)],
                    motion=[('idle', 2.4), ('walk', 1.6), ('light_windup', 0.7), ('light_active', 0.4), ('light_recover', 1.8),
                            ('hover', 1.4), ('transition', 3.5)]),
    'seraphon': dict(title='SERAPHON', subtitle='The Gilded Verdict', accent=(255, 224, 150), span=76, cy=25,
                     poses=[('idle', 0.0), ('walk', 0.4), ('hover', 0.3), ('cast_windup', 1.0), ('cast_active', 0.3),
                            ('smite_active', 0.3), ('wings_windup', 0.9), ('wings_active', 0.25), ('verdict_windup', 2.0),
                            ('verdict_active', 0.6), ('stun', 1.0), ('transition', 2.0)],
                     motion=[('spawn', 3.0), ('idle', 3.0), ('cast_windup', 1.0), ('cast_active', 0.6), ('cast_recover', 0.8),
                             ('hover', 2.4), ('verdict_windup', 2.0), ('verdict_active', 1.2)]),
}


def font(size, bold=False):
    try:
        return ImageFont.truetype('C:/Windows/Fonts/' + ('segoeuib.ttf' if bold else 'segoeui.ttf'), size)
    except OSError:
        return ImageFont.load_default()


def shot(rig, spec, clip, t, yaw=22, elevation=6, size=640, ground=True):
    image, _ = render(rig, clip, t, yaw=yaw, elevation=elevation, size=size, span=spec['span'],
                      center=np.array([0, spec['cy']]), ground=ground)
    return image


def caption(image, text, color, size=None):
    ImageDraw.Draw(image).text((image.width * .03, image.height * .02), text, font=font(size or max(14, image.width // 30), True), fill=color)
    return image


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    heroes = []
    for name, spec in BOSSES.items():
        model = json.loads((ROOT / f'evergarden/src/main/resources/modelengine/{name}.bbmodel').read_text(encoding='utf8'))
        manifest = json.loads((ROOT / f'evergarden/src/main/resources/modelengine/{name}-manifest.json').read_text(encoding='utf8'))
        rig = Rig(model)
        accent = spec['accent']
        hero = shot(rig, spec, 'idle', .6, yaw=18, elevation=-12, size=1100, ground=False)
        caption(hero, f"{spec['title']}  ·  {spec['subtitle']}", accent, 40)
        ImageDraw.Draw(hero).text((34, 92), f"{manifest['cubes']} cuboids · {manifest['bones']} bones · {len(manifest['animations'])} clips · "
                                          f"1024 painted atlas · offline render of the shipped rig", font=font(22), fill=tuple(int(c * .75) for c in accent))
        hero.save(OUT / f'{name}-hero.png')
        heroes.append(hero)
        turn = Image.new('RGB', (640 * 4, 640))
        for i, (yaw, label) in enumerate(((0, 'FRONT'), (35, 'THREE-QUARTER'), (90, 'SIDE'), (180, 'BACK'))):
            turn.paste(caption(shot(rig, spec, 'idle', 0, yaw=yaw), label, accent), (i * 640, 0))
        turn.save(OUT / f'{name}-turnaround.png')
        cols, size = 4, 520
        poses = Image.new('RGB', (cols * size, size * ((len(spec['poses']) + cols - 1) // cols)))
        for i, (clip, t) in enumerate(spec['poses']):
            poses.paste(caption(shot(rig, spec, clip, t, size=size), f'{clip} {t:.2f}s', accent), ((i % cols) * size, (i // cols) * size))
        poses.save(OUT / f'{name}-poses.png')
        frames = []
        for clip, length in spec['motion']:
            for t in np.arange(0, length, 1 / 12.5):
                frame, _ = render(rig, clip, float(t), yaw=24, elevation=4, size=460, span=spec['span'],
                                  center=np.array([0, spec['cy']]), supersample=1)
                frames.append(caption(frame, clip, accent, 18))
        frames[0].save(OUT / f'{name}-motion.gif', save_all=True, append_images=frames[1:], duration=80, loop=0, optimize=True)
        print(name, len(frames), 'motion frames', flush=True)
    lineup = Image.new('RGB', (2200, 1100))
    for i, hero in enumerate(heroes):
        lineup.paste(hero, (i * 1100, 0))
    lineup.save(OUT / 'evergarden-bosses-lineup.png')
    cards = ''.join(f'<article><h2>{s["title"]} <small>{s["subtitle"]}</small></h2><img src="{n}-motion.gif" alt="{n} motion">'
                    f'<a href="{n}-hero.png"><img src="{n}-hero.png" alt="{n} hero"></a><a href="{n}-turnaround.png"><img src="{n}-turnaround.png" alt="{n} turnaround"></a>'
                    f'<a href="{n}-poses.png"><img src="{n}-poses.png" alt="{n} poses"></a></article>' for n, s in BOSSES.items())
    (OUT / 'index.html').write_text('<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width">'
                                    '<title>Evergarden Main Bosses</title><style>body{background:#100f19;color:#efe6ff;font:16px Segoe UI,sans-serif;'
                                    'max-width:1500px;margin:24px auto;padding:0 16px}main{display:grid;grid-template-columns:repeat(auto-fit,minmax(340px,1fr));gap:20px}'
                                    'article{background:#18152a;border:1px solid #352d4d;border-radius:14px;padding:16px}img{width:100%;border-radius:10px;margin:6px 0}'
                                    'small{color:#a99cc4;font-weight:400}p{color:#b7aacd}</style><h1>Evergarden Main Bosses · Phase 1</h1>'
                                    '<p>Offline renders of the shipped ModelEngine rigs (geometry, painted atlas, keyframes, aura loop). '
                                    'In game the particle aura, lighting and sky add to these.</p><img src="evergarden-bosses-lineup.png" alt="lineup"><main>'
                                    + cards + '</main></html>', encoding='utf8')
    print('Previews:', OUT)


if __name__ == '__main__':
    main()
