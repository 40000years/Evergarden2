"""Evergarden relic pickaxes in 3D: Rift Excavator and Smelter's Pickaxe.

Sculpted with weapon_forge on the adventurer weapons' material atlas and held like the
melee adventurer weapons: a vertical shaft gripped at (8, GRIP_Y, 8), the head across the
top along X (forward/back in the hand), the shared handheld display on Java and the
java2bedrock rig on Bedrock (bound to the item slot, verified in game for the melee set).
The GUI shows a rendered 3D icon on both editions.

    build_packs.py calls register(java, bedrock, textures) after the flat relic items.
"""
from __future__ import annotations

import math
from pathlib import Path
import sys

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
sys.path.insert(0, str(ROOT / 'evergarden/tools'))
from weapon_forge import bedrock_cube, cone_mask, mirrored, rig_animation, rig_bones  # noqa: E402
import adventurer_weapon_assets as aw  # noqa: E402

GRIP_Y = 5.0
SPAN = 9.4  # socket centre to pick point, along X
PICKAXES = {'rift_pickaxe': 'Rift Excavator', 'smelter_pickaxe': "Smelter's Pickaxe"}


def arm(side, s):
    """The pick arm's centre line: rises off the socket, then curves down to the point."""
    return np.array([8 + side * SPAN * s, 24.4 + 1.0 * s - 4.0 * s * s, 8.0])


def arm_mask(X, Y, Z, side, root, tip, flat=1.35):
    """A pick arm tapering from `root` to `tip` radius; flattened in Z so it reads as a blade edge."""
    samples = np.linspace(0, 1, 26)
    points = [arm(side, s) for s in samples]
    radii = [root + (tip - root) * s ** .85 for s in samples]
    return aw.tapered(X, Y, 8 + (Z - 8) * flat, points, radii) <= 0


def arm_progress(X, side):
    return np.clip((X - 8) * side / SPAN, 0, 1)


def rift_pickaxe(w):
    # Pommel: a void gem in a dark steel claw.
    w.relief(mirrored([(8, 2.4), (9.05, 2.1), (9.45, 1.1), (8.85, .1), (8, -.25)]), [(0, .74, 'steel_dark'), (.26, .9, 'obsidian_edge')])
    w.jewel(8, 1.1, .66, 'obsidian_edge', 'void', 'void_core', depth=1.2, shape='diamond')
    # Handle: obsidian core, banded leather grip, silver collars.
    w.rod(2.3, 21.0, 1.12, 'obsidian')
    w.wrap(2.7, 8.6, 1.04, 'leather_dark', ['void_dark', 'leather_light'])
    for y in (2.3, 8.8, 13.4, 17.6):
        w.rod(y, y + .45, 1.36, 'silver')
    # A thin glowing seam climbs the shaft to the head, as if the rift leaks down it.
    w.box(7.86, 9.5, 7.42, 8.14, 20.6, 7.5, 'void_light')
    w.box(7.86, 9.5, 8.5, 8.14, 20.6, 8.58, 'void_light')

    def head(X, Y, Z):
        dx, dy, dz = X - 8, Y - 24.2, Z - 8
        parts = []
        socket = (np.abs(dx) <= 1.55) & (dy >= -3.8) & (dy <= 2.6) & (np.abs(dz) <= 1.25)
        socket_rim = socket & ((np.abs(dy + 3.6) <= .25) | (np.abs(dy - 2.4) <= .25))
        for side in (1, -1):
            body = arm_mask(X, Y, Z, side, 1.95, .2)
            t = arm_progress(X, side)
            centre = np.array([arm(side, s)[1] for s in t.ravel()]).reshape(t.shape)
            # The rift: a jagged glowing crack along the arm's flank, both faces.
            crack = body & (np.abs(Y - centre - .32 * np.sin(t * 19)) <= .2) & (np.abs(dz) >= .55) & (t > .08) & (t < .86)
            tip = body & (t >= .84)
            edge = body & (Y < centre - .55)
            parts += [('void_core', crack & (np.abs(dz) >= .75) & (np.mod(t * 9, 1) < .35)), ('void_light', crack | tip),
                      ('obsidian', edge), ('void', body & (Y > centre + .8)), ('void_dark', body)]
        # Rift shards break upward out of the head.
        for x, h, lean in ((5.2, 3.0, -.35), (8.0, 4.2, 0.0), (10.8, 3.0, .35)):
            shard = cone_mask(X, Y, Z, (x, 26.1, 8), (lean, 1, 0), h, .6, .05)
            parts += [('void_core', shard & (Y > 26.1 + h * .7)), ('void_light', shard)]
        parts += [('void_light', socket_rim), ('obsidian_light', socket)]
        return parts

    w.volume(head, (-3.2, 18.8, 5.2), (19.2, 30.4, 10.8))
    # The floating rift crystal set into both faces of the socket.
    w.jewel(8, 23.4, .95, 'obsidian_edge', 'void_light', 'void_core', depth=2.9, shape='diamond')


def smelter_pickaxe(w):
    # Pommel: an ember jewel in a bronze cap.
    w.relief(mirrored([(8, 2.4), (9.1, 2.15), (9.5, 1.15), (8.9, .1), (8, -.25)]), [(0, .76, 'bronze_dark'), (.26, .92, 'bronze')])
    w.jewel(8, 1.1, .68, 'gold', 'ember', 'ember_core', depth=1.2, shape='round')
    # Handle: charred wood with a leather and bronze wrap.
    w.rod(2.3, 20.8, 1.04, 'wood_dark')
    w.wrap(2.7, 8.6, 1.08, 'leather', ['leather_light', 'bronze_dark'])
    for y in (2.3, 8.8, 13.2, 17.2):
        w.rod(y, y + .45, 1.4, 'bronze')
    w.rod(17.65, 18.9, 1.18, 'steel_dark')

    def head(X, Y, Z):
        dx, dy, dz = X - 8, Y - 23.9, Z - 8
        parts = []
        # The furnace: a bronze housing with a glowing grate on both faces.
        furnace = (np.abs(dx) <= 2.35) & (dy >= -4.4) & (dy <= 2.5) & (np.abs(dz) <= 1.75)
        bevel = furnace & (np.abs(dx) + np.maximum(dy - 1.4, -dy - 3.3) * .9 > 2.75)
        grate_window = (np.abs(dx) <= 1.55) & (dy >= -2.9) & (dy <= 1.3) & (np.abs(dz) >= 1.35)
        bars = grate_window & (np.mod(X - 8 + .25, .9) < .3)
        glow = grate_window & ~bars
        rivets = furnace & (np.abs(dz) >= 1.5) & (np.abs(np.abs(dx) - 1.95) <= .17) & (np.abs(np.abs(dy + 1.0) - 2.9) <= .17)
        chimney = (np.hypot(dx, dz) <= .78) & (dy > 2.5) & (dy <= 4.7)
        chimney_cap = (np.hypot(dx, dz) <= 1.05) & (dy > 4.3) & (dy <= 4.85)
        flame = cone_mask(X, Y, Z, (8, 28.85, 8), (0, 1, 0), 1.7, .55, .06)
        for side in (1, -1):
            body = arm_mask(X, Y, Z, side, 1.8, .2)
            t = arm_progress(X, side)
            centre = np.array([arm(side, s)[1] for s in t.ravel()]).reshape(t.shape)
            # Heat climbs from the steel near the furnace to molten points.
            parts += [('ember_core', body & (t >= .9)), ('ember_light', body & (t >= .76)), ('ember', body & (t >= .6) & (Y < centre + .2)),
                      ('steel_light', body & (Y > centre + .6)), ('steel', body & (Y < centre - .5)), ('steel_dark', body)]
        parts += [('ember_core', flame & (Y < 29.6)), ('ember_light', flame), ('gold', chimney_cap), ('bronze_dark', chimney),
                  ('gold_light', rivets), ('steel_dark', bars), ('ember_light', glow & (np.abs(dy + .8) <= 1.0)), ('ember', glow),
                  ('bronze_dark', bevel), ('bronze', furnace)]
        return parts

    w.volume(head, (-3.0, 18.8, 5.2), (19.0, 30.8, 10.8))


SCULPTS = {'rift_pickaxe': rift_pickaxe, 'smelter_pickaxe': smelter_pickaxe}


def make_pickaxe(name: str) -> aw.Sculpt:
    w = aw.Sculpt(name)
    SCULPTS[name](w)
    return w


def java_display():
    return {
        'ground': {'translation': [0, 3, 0], 'scale': [0.4, 0.4, 0.4]},
        'fixed': {'rotation': [0, 180, 0], 'translation': [0, -4, 0], 'scale': [0.65, 0.65, 0.65]},
        'head': {'translation': [0, 10, 0], 'scale': [0.6, 0.6, 0.6]},
        **aw.handheld_display(GRIP_Y),
    }


def java_model(w: aw.Sculpt):
    elements = []
    for c in w.cubes:
        e = {k: c[k] for k in ('from', 'to')}
        e.update(name=c['name'], faces={f: {'uv': aw.tile(c['mat']), 'texture': '#metal'} for f in aw.FACES})
        if c['mat'] in aw.EMISSIVE:
            e.update(shade=False, light_emission=15)
        elements.append(e)
    return {
        'credit': 'Evergarden / relic_pickaxe_assets.py',
        'ambientocclusion': False,
        'gui_light': 'front',
        'textures': {'metal': 'voidscape:item/adventurer_weapons/materials', 'particle': 'voidscape:item/adventurer_weapons/materials'},
        'display': java_display(),
        'elements': elements,
    }


def bedrock_geometry(w: aw.Sculpt):
    bones = rig_bones(GRIP_Y)
    glow = [c for c in w.cubes if c['mat'] in aw.EMISSIVE]
    body = [c for c in w.cubes if c['mat'] not in aw.EMISSIVE]
    # Never a player bone name (body, head, rightArm...): Bedrock would attach that bone to the wearer's skeleton.
    for bone, items in (('tool', body), ('inlay', glow)):
        bones.append({'name': bone, 'parent': 'display_z', 'pivot': [0, GRIP_Y, 0],
                      'cubes': [bedrock_cube(c, aw.uv_px(c['mat'])) for c in items]})
    return {'format_version': '1.16.0', 'minecraft:geometry': [{
        'description': {'identifier': f'geometry.voidscape.{w.name}', 'texture_width': 128, 'texture_height': 128,
                        'visible_bounds_width': 4, 'visible_bounds_height': 4.5, 'visible_bounds_offset': [0, 0.75, 0]},
        'bones': bones}]}


def icon(w: aw.Sculpt, tex):
    from legendary_weapon_preview import render_icon_frames
    return render_icon_frames(aw._preview_sculpt(w, tex), tex, [None])[0]


def register(java: Path, bedrock: Path, textures: dict):
    """Replace the flat pickaxe items with the 3D sculpts on both editions (mappings stay as they are)."""
    tex = aw.atlas()
    animations, controllers, cases = {}, {}, []
    for name, title in PICKAXES.items():
        w = make_pickaxe(name)
        art = icon(w, tex)
        for target in (java / f'assets/voidscape/textures/item/{name}.png', bedrock / f'textures/items/{name}.png'):
            target.parent.mkdir(parents=True, exist_ok=True)
            art.save(target)
        aw.write_json(java / f'assets/voidscape/models/item/{name}.json', java_model(w))
        aw.write_json(java / f'assets/voidscape/models/item/{name}_icon.json',
                      {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'voidscape:item/{name}'}})
        definition = {'type': 'minecraft:select', 'property': 'minecraft:display_context',
                      'cases': [{'when': 'gui', 'model': {'type': 'minecraft:model', 'model': f'voidscape:item/{name}_icon'}}],
                      'fallback': {'type': 'minecraft:model', 'model': f'voidscape:item/{name}'}}
        aw.write_json(java / f'assets/voidscape/items/{name}.json', {'model': definition})
        cases.append({'when': f'voidscape:{name}', 'model': definition})
        textures[f'voidscape.{name}'] = {'textures': f'textures/items/{name}'}

        aw.write_json(bedrock / f'models/entity/relic_tools/{name}.geo.json', bedrock_geometry(w))
        prefix = f'animation.voidscape.{name}'
        animations[prefix + '.first'] = rig_animation(java_display(), True)
        animations[prefix + '.third'] = rig_animation(java_display(), False)
        aw.write_json(bedrock / f'attachables/relic_tools/{name}.json', {'format_version': '1.10.0', 'minecraft:attachable': {'description': {
            'identifier': f'voidscape:{name}',
            'materials': {'default': 'entity_alphatest', 'glow': 'entity_emissive_alpha', 'enchanted': 'entity_alphatest_glint'},
            'textures': {'default': 'textures/adventurer_weapons/materials', 'enchanted': 'textures/misc/enchanted_item_glint'},
            'geometry': {'default': f'geometry.voidscape.{name}'},
            'animations': {'first': prefix + '.first', 'third': prefix + '.third'},
            'scripts': {'animate': [{'first': 'context.is_first_person == 1.0'}, {'third': 'context.is_first_person == 0.0'}]},
            'render_controllers': ['controller.render.voidscape.relic_tool'],
        }}})
    controllers['controller.render.voidscape.relic_tool'] = {
        'geometry': 'Geometry.default',
        'materials': [{'*': 'variable.is_enchanted ? Material.enchanted : Material.default'}, {'inlay': 'Material.glow'}],
        'textures': ['Texture.default', 'Texture.enchanted'],
    }
    aw.write_json(bedrock / 'animations/relic_tools.animation.json', {'format_version': '1.8.0', 'animations': animations})
    aw.write_json(bedrock / 'render_controllers/relic_tools.render.json', {'format_version': '1.8.0', 'render_controllers': controllers})
    # Vanilla netherite pickaxe routing: the pickaxes now pick a GUI icon or the 3D model by context.
    aw.write_json(java / 'assets/minecraft/items/netherite_pickaxe.json', {'model': {
        'type': 'minecraft:select', 'property': 'minecraft:custom_model_data', 'index': 0, 'cases': cases,
        'fallback': {'type': 'minecraft:model', 'model': 'minecraft:item/netherite_pickaxe'}}})


def preview(out: Path):
    """Side-by-side Java hand, Bedrock rig and icon renders of both pickaxes."""
    from PIL import Image, ImageDraw
    import weapon_forge as wf
    from weapon_hold_preview import font, render, scene
    palette = {n: tuple(int(v[i:i + 2], 16) for i in (1, 3, 5)) for n, v in aw.PALETTE.items()}
    tex = aw.atlas()
    cell = 360
    sheet = Image.new('RGBA', (60 + 4 * cell, 120 + 2 * (cell + 20)), (10, 15, 26, 255))
    d = ImageDraw.Draw(sheet)
    d.text((30, 18), 'RIFT EXCAVATOR  ·  SMELTER\'S PICKAXE', font=font(30, True), fill='#f3dfb2')
    for i, h in enumerate(('3D MODEL', 'JAVA  ·  in hand', 'BEDROCK  ·  rig', 'ICON')):
        d.text((40 + i * cell, 72), h, font=font(16, True), fill='#a8c6ff')
    display = java_display()
    anim = wf.rig_animation(display, False)
    for row, name in enumerate(PICKAXES):
        y = 100 + row * (cell + 20)
        w = make_pickaxe(name)
        from legendary_weapon_preview import render_item
        model = render_item(aw._preview_sculpt(w, tex), tex, None, size=cell, yaw=24, tilt=-38, elevation=10)
        java = render(scene(w.cubes, palette, aw.EMISSIVE, lambda p: wf.java_third_person(display, p)), cell, scale=11, centre=(-5, 12, -6))
        bedrock = render(scene(w.cubes, palette, aw.EMISSIVE, lambda p: wf.bound_third_person(anim, wf.bedrock_point(p))), cell, scale=11, centre=(-5, 12, -6))
        for i, im in enumerate((model, java, bedrock)):
            sheet.alpha_composite(im, (30 + i * cell, y))
        sheet.alpha_composite(icon(w, tex).resize((256, 256), Image.Resampling.NEAREST), (30 + 3 * cell + 52, y + 52))
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.convert('RGB').save(out)
    return out


if __name__ == '__main__':
    print(preview(ROOT / 'previews/relic-pickaxes/pickaxes.png'))
