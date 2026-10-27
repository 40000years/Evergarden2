"""Project captured Paper displays and emitted dust points; this is a software preview, not game footage."""
import argparse
import io
import json
import math
from pathlib import Path
import zipfile
from PIL import Image, ImageDraw
from preview_legendary_runtime import add, sub, mul, dot, cross, norm, transform, warp, font

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'advance-magic/art/effects'
MYTHIC = ('shulker_levitation', 'solar_apocalypse', 'chronos_final_hour', 'heavens_judgment',
          'verdant_sovereign', 'lunar_requiem', 'crimson_oblivion')
LABELS = {
    'lightning_strike': ('LIGHTNING', 'Instant zigzag spark', 0),
    'frost_nova': ('FROST', 'Three spreading ice shards', 0),
    'shadow_step': ('SHADOW', 'Directional crescent slash', 4),
    'natures_bloom': ('NATURE', 'Arcing thorn seed', 4),
    'earth_wall': ('EARTH', 'Forward ground eruptions', 8),
    'dragons_breath': ('DRAGON', 'Three short cone pulses', 4),
    'void_pull': ('VOID', 'Slow imploding orb', 4),
    'sonic_boom': ('SONIC', 'Traveling piercing rings', 4),
    'blaze_barrage': ('BLAZE', 'Three staggered fire shots', 4),
    'wither_ray': ('WITHER', 'Corkscrew soul shot', 4),
    'shulker_levitation': ('SHULKER', 'Rotating dimensional cage', 4),
    'meteor_strike': ('METEOR', 'Lobbed ember impact', 4),
    'iron_armor': ('IRON', 'Nearby expanding nova', 4),
    'vex_legion': ('VEX', 'Twin converging spirits', 4),
    'guardian_beam': ('GUARDIAN', 'Three locked beam pulses', 8),
    'solar_apocalypse': ('SOLAR', 'Layered sun, charged discharge', 8),
    'chronos_final_hour': ('CHRONOS', 'Clock gate, primary + echo', 12),
    'heavens_judgment': ('JUDGMENT', 'Overhead seals, descending column', 12),
    'verdant_sovereign': ('VERDANT', 'Dragon gate, three emerald waves', 8),
    'lunar_requiem': ('LUNAR', 'Moon gate, three silver needles', 12),
    'crimson_oblivion': ('CRIMSON', 'Original three rotating shards', 4),
}


def texture(name):
    for directory in (ART / 'legendary', ART):
        path = directory / (name + '.png')
        if path.is_file():
            with Image.open(path) as source:
                return source.convert('RGBA')
    if name == 'judgment_beam':
        with zipfile.ZipFile(ROOT / 'advance-magic/dist/advance-magic-bedrock.mcpack') as pack:
            with Image.open(io.BytesIO(pack.read('textures/particle/judgment_beam.png'))) as source:
                # The emitter's real UV range excludes this transparent border.
                return source.convert('RGBA').crop((48, 0, 80, 128))
    raise FileNotFoundError(name)


def panel(scene, first_person=False):
    width, height = 720, 480
    caster = (.5, 100, .5)
    camera = (.5, 101.62, .5) if first_person else (7.5, 104.5, -6)
    look = (0, 0, 1) if first_person else norm(sub((.5, 101.4, 3.0), camera))
    right = norm(cross(look, (0, 1, 0)))
    up = cross(right, look)
    focal = width / (2 * math.tan(math.radians(90 if first_person else 58) / 2))

    def project(position):
        delta = sub(position, camera)
        depth = dot(delta, look)
        if depth <= .12:
            return None
        return (width / 2 + focal * dot(delta, right) / depth,
                height / 2 - focal * dot(delta, up) / depth, depth)

    image = Image.new('RGBA', (width, height), '#101724')
    draw = ImageDraw.Draw(image)
    for step in range(-8, 15):
        for a, b in (((step, 99.95, -8), (step, 99.95, 14)), ((-8, 99.95, step), (14, 99.95, step))):
            pa, pb = project(a), project(b)
            if pa and pb:
                draw.line((pa[:2], pb[:2]), fill='#243044', width=1)
    plane = ((-.5, .5, 0), (.5, .5, 0), (.5, -.5, 0), (-.5, -.5, 0))
    horizontal = ((-.5, 0, -.5), (.5, 0, -.5), (.5, 0, .5), (-.5, 0, .5))
    rays = (
        ((-.5, .5, -.5), (.5, .5, -.5), (.5, -.5, -.5), (-.5, -.5, -.5)),
        ((.5, .5, .5), (-.5, .5, .5), (-.5, -.5, .5), (.5, -.5, .5)),
        ((.5, .5, -.5), (.5, .5, .5), (.5, -.5, .5), (.5, -.5, -.5)),
        ((-.5, .5, .5), (-.5, .5, -.5), (-.5, -.5, -.5), (-.5, -.5, .5)))
    quads = []
    for pose in scene['displays']:
        if min(pose['scale']) <= .001:
            continue
        name = pose['model'].split(':')[-1]
        display = {**pose, 'left_rotation': pose['rotation'], 'right_rotation': (0, 0, 0, 1), 'translation': (0, 0, 0)}
        faces = rays if name.endswith(('_ray', '_echo')) or name == 'judgment_beam' else (horizontal,) if name.startswith('judgment_seal') else (plane,)
        for face in faces:
            projected = [project(transform(vertex, display)) for vertex in face]
            if all(projected):
                quads.append((sum(point[2] for point in projected) / 4, name, [point[:2] for point in projected]))
    for _, name, quad in sorted(quads, key=lambda item: -item[0]):
        layer = warp(texture(name), quad, image.size)
        if layer is not None:
            image = Image.alpha_composite(image, layer)
    particle_layer = Image.new('RGBA', image.size)
    dots = ImageDraw.Draw(particle_layer)
    for sample in scene.get('particles', []):
        at = project(sample['position'])
        if not at:
            continue
        radius = max(.9, min(5, focal * sample.get('size', 1) * .035 / at[2]))
        color = tuple(round(max(0, min(1, component)) * 255) for component in sample['color'])
        x, y = at[:2]
        dots.ellipse((x-radius*2.3, y-radius*2.3, x+radius*2.3, y+radius*2.3), fill=(*color, 65))
        dots.ellipse((x-radius, y-radius, x+radius, y+radius), fill=(*color, 245))
    image = Image.alpha_composite(image, particle_layer)
    draw = ImageDraw.Draw(image)
    if first_person:
        draw.line((width/2-6, height/2, width/2+6, height/2), fill='#eef5ff', width=1)
        draw.line((width/2, height/2-6, width/2, height/2+6), fill='#eef5ff', width=1)
    else:
        feet, head = project(caster), project(add(caster, (0, 1.8, 0)))
        if feet and head:
            draw.line((feet[:2], head[:2]), fill='#ffe6b2', width=4)
            draw.ellipse((head[0]-4, head[1]-4, head[0]+4, head[1]+4), fill='#ffe6b2')
            draw.text((feet[0]+8, feet[1]-12), 'CASTER / 1.8 m', fill='#ffe6b2', font=font(12))
    title, subtitle, _ = LABELS[scene['spell']]
    draw.rectangle((0, 0, width, 68), fill='#0b101a')
    draw.text((18, 12), title + ' / BASIC', fill='#edf5ff', font=font(23))
    draw.text((18, 41), subtitle, fill='#aac1d8', font=font(16))
    draw.rectangle((0, height-32, width, height), fill='#0b101a')
    view = 'caster / horizontal FOV 90' if first_person else 'oblique'
    draw.text((18, height-25), f"Paper tick {scene['age']} / {view} / emitted dust + shipped art", fill='#aac1d8', font=font(13))
    return image.convert('RGB')


def gallery(scenes, names, first_person=False, age=None):
    columns = 3 if len(names) == 3 or len(names) > 7 else 4
    rows = math.ceil(len(names) / columns)
    image = Image.new('RGB', (columns*720, rows*480+40), '#0b101a')
    for i, name in enumerate(names):
        candidates = [scene for scene in scenes if scene['spell'] == name]
        desired = LABELS[name][2] if age is None else age
        selected = min(candidates, key=lambda scene: abs(scene['age']-desired))
        image.paste(panel(selected, first_person), ((i % columns)*720, (i // columns)*480))
    ImageDraw.Draw(image).text((18, rows*480+10),
        'SOFTWARE PREVIEW / captured Paper data / native dust shown as projected points / not Minecraft footage',
        fill='#aac1d8', font=font(16))
    return image


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('scenes', type=Path)
    parser.add_argument('--output', type=Path, default=ROOT / 'previews/basic-legendary-casting.png')
    parser.add_argument('--scope', choices=('legendary', 'mythic', 'all'), default='legendary')
    parser.add_argument('--camera', choices=('oblique', 'caster'), default='oblique')
    parser.add_argument('--animate', action='store_true')
    args = parser.parse_args()
    scenes = json.loads(args.scenes.read_text(encoding='utf8'))
    names = MYTHIC[-3:] if args.scope == 'legendary' else MYTHIC if args.scope == 'mythic' else tuple(LABELS)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    gallery(scenes, names, args.camera == 'caster').save(args.output)
    if args.animate:
        frames = [gallery(scenes, names, args.camera == 'caster', age) for age in (0, 4, 8, 12, 16, 24)]
        frames[0].save(args.output.with_suffix('.gif'), save_all=True, append_images=frames[1:],
                       duration=[200, 200, 200, 200, 400, 600], loop=0)
    print('Preview:', args.output)


if __name__ == '__main__':
    main()
