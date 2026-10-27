"""Shared export for wearable armor sets that both editions draw on the wearer.

A set supplies two armor layers painted on the vanilla 64x32 layout at 4x
(256x128), a helm built from cuboids in head pixels (Bedrock model space: head box
x/z -4..4, y 24..32, face -Z, wearer's right -X) and a tile palette for the helm.

Java: chestplate, leggings and boots carry `equippable` with the equipment asset
`advance_magic:<set>` (humanoid + humanoid_leggings layers). The helm is
equippable without an asset, so the client draws its item model on the head.
Bedrock (Geyser 2.12): custom item definitions declare the slot and protection;
attachables draw the same textures on boxes identical to Java's armor model
(bones named like the player's, box UV on 64x32) and the helm's cuboids.
"""
from __future__ import annotations

import base64
from dataclasses import dataclass, field
import io
import json
from pathlib import Path
import random
from typing import Callable

import numpy as np
from PIL import Image

from weapon_forge import Forge, tile_pixels, FACES

NS = 'advance_magic'
K = 4

# Java's CustomHeadLayer draws a head item at 0.625 block per model block; with
# this display transform one model unit is half a head pixel and model (8, 8, 8)
# sits at head pixel (0, 31, 0): mx = 8 - 2x, my = 8 + 2(y - 31), mz = 8 + 2z.
HELM_DISPLAY = {'rotation': [0, 0, 0], 'translation': [0, 4.8, 0], 'scale': [.8, .8, .8]}
HELD = ('gui', 'ground', 'fixed', 'firstperson_righthand', 'firstperson_lefthand', 'thirdperson_righthand', 'thirdperson_lefthand')
# Hidden while held: armor bones follow the wearer's bones by name.
WORN = "c.item_slot != 'main_hand' && c.item_slot != 'off_hand'"

# Boxes of Java's HumanoidArmorModel, in Bedrock model space with box UV on the
# 64x32 layout (the 256x128 textures are sampled at the same normalized UVs).
ARMOR_BONES = {
    'chestplate': (('body', [0, 24, 0], [-4, 12, -2], [8, 12, 4], [16, 16], 1.0, False),
                   ('rightArm', [-5, 22, 0], [-8, 12, -2], [4, 12, 4], [40, 16], 1.0, False),
                   ('leftArm', [5, 22, 0], [4, 12, -2], [4, 12, 4], [40, 16], 1.0, True)),
    'leggings': (('body', [0, 24, 0], [-4, 12, -2], [8, 12, 4], [16, 16], .5, False),
                 ('rightLeg', [-1.9, 12, 0], [-3.9, 0, -2], [4, 12, 4], [0, 16], .5, False),
                 ('leftLeg', [1.9, 12, 0], [-.1, 0, -2], [4, 12, 4], [0, 16], .5, True)),
    'boots': (('rightLeg', [-1.9, 12, 0], [-3.9, 0, -2], [4, 12, 4], [0, 16], 1.0, False),
              ('leftLeg', [1.9, 12, 0], [-.1, 0, -2], [4, 12, 4], [0, 16], 1.0, True)),
}


@dataclass
class ArmorSet:
    key: str                                   # 'seraph' -> advance_magic:seraph_helm, asset advance_magic:seraph
    pieces: tuple                              # (id, title, base item, Geyser slot, protection)
    layer_one: Callable[[], Image.Image]
    layer_two: Callable[[], Image.Image]
    helm: Callable[[], Forge]
    helm_palette: dict
    helm_glow: set
    art: Path
    credit: str
    atlas_seed: int
    label_color: tuple = (255, 228, 160)
    background: tuple = (16, 18, 28)
    skin: tuple = (58, 52, 64, 255)
    _cache: dict = field(default_factory=dict, repr=False)

    @property
    def asset(self):
        return f'{NS}:{self.key}'

    @property
    def mats(self):
        return tuple(self.helm_palette)


# ---------------------------------------------------------------------------
# Helm


def helm_atlas(s: ArmorSet):
    im = Image.new('RGBA', (128, 128))
    rng = random.Random(s.atlas_seed)
    for i, (name, code) in enumerate(s.helm_palette.items()):
        rows = tile_pixels(tuple(bytes.fromhex(code[1:])), rng, name in s.helm_glow)
        for y, row in enumerate(rows):
            for x, pixel in enumerate(row):
                im.putpixel((i % 8 * 16 + x, i // 8 * 16 + y), pixel)
    return im


def tile(s: ArmorSet, mat):
    i = s.mats.index(mat)
    return i % 8 * 16, i // 8 * 16


def java_helm_model(s: ArmorSet, f):
    elements = []
    for c in f.cubes:
        (x0, y0, z0), (x1, y1, z1) = c['from'], c['to']
        u, v = tile(s, c['mat'])
        e = {'from': [round(8 - 2 * x1, 4), round(8 + 2 * (y0 - 31), 4), round(8 + 2 * z0, 4)],
             'to': [round(8 - 2 * x0, 4), round(8 + 2 * (y1 - 31), 4), round(8 + 2 * z1, 4)],
             'faces': {face: {'uv': [u / 8, v / 8, u / 8 + 2, v / 8 + 2], 'texture': '#helm'} for face in FACES}}
        if c['mat'] in s.helm_glow:
            e.update(shade=False, light_emission=15)
        elements.append(e)
    for e in elements:
        assert all(-16 <= v <= 32 for v in e['from'] + e['to']), e
    return {'credit': s.credit, 'ambientocclusion': False,
            'textures': {'helm': f'{NS}:item/{s.key}/helm', 'particle': f'{NS}:item/{s.key}/helm'},
            'display': {'head': HELM_DISPLAY}, 'elements': elements}


def bedrock_helm(s: ArmorSet, f):
    body, glow = [], []
    for c in f.cubes:
        u, v = tile(s, c['mat'])
        cube = {'origin': c['from'], 'size': [round(b - a, 4) for a, b in zip(c['from'], c['to'])],
                'uv': {face: {'uv': [u, v], 'uv_size': [16, 16]} for face in FACES}}
        (glow if c['mat'] in s.helm_glow else body).append(cube)
    return {'format_version': '1.16.0', 'minecraft:geometry': [{
        'description': {'identifier': f'geometry.advance_magic.{s.key}_helm', 'texture_width': 128, 'texture_height': 128,
                        'visible_bounds_width': 3, 'visible_bounds_height': 3.5, 'visible_bounds_offset': [0, 1.5, 0]},
        'bones': [{'name': 'head', 'pivot': [0, 24, 0], 'cubes': body},
                  {'name': 'helm_glow', 'parent': 'head', 'pivot': [0, 24, 0], 'cubes': glow}]}]}


# ---------------------------------------------------------------------------
# Bedrock armor


def bedrock_armor(s: ArmorSet, piece):
    bones = []
    for name, pivot, origin, size, uv, inflate, mirror in ARMOR_BONES[piece]:
        cube = {'origin': origin, 'size': size, 'uv': uv, 'inflate': inflate}
        if mirror:
            cube['mirror'] = True
        bones.append({'name': name, 'pivot': pivot, 'cubes': [cube]})
    return {'format_version': '1.16.0', 'minecraft:geometry': [{
        'description': {'identifier': f'geometry.advance_magic.{s.key}_{piece}', 'texture_width': 64, 'texture_height': 32,
                        'visible_bounds_width': 2, 'visible_bounds_height': 3, 'visible_bounds_offset': [0, 1.5, 0]},
        'bones': bones}]}


def attachable(s: ArmorSet, piece):
    texture = f'textures/{s.key}/helm' if piece == 'helm' else f"textures/{s.key}/layer_{2 if piece == 'leggings' else 1}"
    description = {'identifier': f'{NS}:{s.key}_{piece}',
                   'materials': {'default': 'entity_alphatest', 'glow': 'entity_emissive_alpha'},
                   'textures': {'default': texture},
                   'geometry': {'default': f'geometry.advance_magic.{s.key}_{piece}'},
                   'render_controllers': [{f"controller.render.advance_magic.{s.key}_{'helm' if piece == 'helm' else 'armor'}": WORN}]}
    return {'format_version': '1.10.0', 'minecraft:attachable': {'description': description}}


def render_controllers(s: ArmorSet):
    return {'format_version': '1.8.0', 'render_controllers': {
        f'controller.render.advance_magic.{s.key}_armor': {'geometry': 'Geometry.default', 'materials': [{'*': 'Material.default'}],
                                                         'textures': ['Texture.default']},
        f'controller.render.advance_magic.{s.key}_helm': {'geometry': 'Geometry.default',
                                                        'materials': [{'*': 'Material.default'}, {'helm_glow': 'Material.glow'}],
                                                        'textures': ['Texture.default']}}}


# ---------------------------------------------------------------------------
# Offline preview model (true view: +X is the wearer's right, as boss_render expects)

ATLAS_SIZE = (512, 256)
OFFSETS = {'layer1': (0, 0), 'layer2': (256, 0), 'helm': (0, 128), 'skin': (128, 128)}


def box_faces(u, v, w, h, d, mirror, offset):
    """Blockbench's box-UV unfolding at scale K, optionally mirrored (left limbs)."""
    ox, oy = offset
    r = {'north': (u + d, v + d, u + d + w, v + d + h), 'south': (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
         'east': (u, v + d, u + d, v + d + h), 'west': (u + d + w, v + d, u + 2 * d + w, v + d + h),
         'up': (u + d + w, v + d, u + d, v), 'down': (u + d + 2 * w, v, u + d + w, v + d)}
    if mirror:
        r['east'], r['west'] = r['west'], r['east']
        r = {k: (a[2], a[1], a[0], a[3]) for k, a in r.items()}
    return {k: {'uv': [ox + a[0] * K, oy + a[1] * K, ox + a[2] * K, oy + a[3] * K], 'texture': 0} for k, a in r.items()}


def preview_atlas(s: ArmorSet):
    if 'png' not in s._cache:
        atlas = Image.new('RGBA', ATLAS_SIZE)
        atlas.alpha_composite(s.layer_one(), OFFSETS['layer1'])
        atlas.alpha_composite(s.layer_two(), OFFSETS['layer2'])
        atlas.alpha_composite(helm_atlas(s), OFFSETS['helm'])
        atlas.alpha_composite(Image.new('RGBA', (128, 128), s.skin), OFFSETS['skin'])
        stream = io.BytesIO()
        atlas.save(stream, format='PNG')
        s._cache['png'] = stream.getvalue()
    return s._cache['png']


def preview_model(s: ArmorSet, pieces=('helm', 'chestplate', 'leggings', 'boots'), mannequin=True, pose=None):
    """bbmodel-style dict for boss_render: mannequin + chosen pieces."""
    bones = {'body': [0, 24, 0], 'head': [0, 24, 0], 'right_arm': [5, 22, 0], 'left_arm': [-5, 22, 0],
             'right_leg': [1.9, 12, 0], 'left_leg': [-1.9, 12, 0]}
    children = {b: [] for b in bones}
    elements = []

    def add(bone, lo, hi, faces_uv, name, shade=True):
        uid = f'{name}-{len(elements)}'
        elements.append({'uuid': uid, 'name': name, 'from': list(lo), 'to': list(hi), 'origin': [0, 0, 0], 'rotation': [0, 0, 0],
                         'faces': faces_uv, 'shade': shade})
        children[bone].append(uid)

    # Box per limb, preview space: x mirrored from Bedrock (right limbs at +X).
    limbs = {'head': ((-4, 24, -4), (8, 8, 8), (0, 0)), 'body': ((-4, 12, -2), (8, 12, 4), (16, 16)),
             'right_arm': ((4, 12, -2), (4, 12, 4), (40, 16)), 'left_arm': ((-8, 12, -2), (4, 12, 4), (40, 16)),
             'right_leg': ((-.1, 0, -2), (4, 12, 4), (0, 16)), 'left_leg': ((-3.9, 0, -2), (4, 12, 4), (0, 16))}
    if mannequin:
        sx, sy = OFFSETS['skin']
        for bone, (lo, size, _) in limbs.items():
            hi = [a + b for a, b in zip(lo, size)]
            uv = {face: {'uv': [sx + 4, sy + 4, sx + 12, sy + 12], 'texture': 0} for face in FACES}
            add(bone, lo, hi, uv, 'skin')
    layers = {'chestplate': (('body', 1.0), ('right_arm', 1.0), ('left_arm', 1.0)),
              'leggings': (('body', .5), ('right_leg', .5), ('left_leg', .5)),
              'boots': (('right_leg', 1.0), ('left_leg', 1.0))}
    for piece in pieces:
        if piece == 'helm':
            hx, hy = OFFSETS['helm']
            for c in s.helm().cubes:
                (x0, y0, z0), (x1, y1, z1) = c['from'], c['to']
                u, v = tile(s, c['mat'])
                uv = {face: {'uv': [hx + u + .5, hy + v + .5, hx + u + 15.5, hy + v + 15.5], 'texture': 0} for face in FACES}
                add('head', (-x1, y0, z0), (-x0, y1, z1), uv, 'glow' if c['mat'] in s.helm_glow else 'helm', c['mat'] not in s.helm_glow)
            continue
        offset = OFFSETS['layer2' if piece == 'leggings' else 'layer1']
        for bone, inflate in layers[piece]:
            lo, size, (u, v) = limbs[bone]
            lo2 = [a - inflate for a in lo]
            hi2 = [a + b + inflate for a, b in zip(lo, size)]
            add(bone, lo2, hi2, box_faces(u, v, size[0], size[1], size[2], bone.startswith('left'), offset), piece)
    outliner = [{'name': b, 'uuid': 'bone-' + b, 'origin': o, 'children': children[b]} for b, o in bones.items()]
    animations = []
    if pose:
        animators = {}
        for bone, angles in pose.items():
            animators['bone-' + bone] = {'name': bone, 'keyframes': [{'channel': 'rotation', 'time': 0,
                                                                     'data_points': [{'x': -angles[0], 'y': -angles[1], 'z': angles[2]}]}]}
        animations.append({'name': 'pose', 'length': 1, 'loop': 'hold', 'animators': animators})
    return {'meta': {}, 'resolution': {'width': ATLAS_SIZE[0], 'height': ATLAS_SIZE[1]}, 'elements': elements, 'outliner': outliner,
            'animations': animations, 'textures': [{'source': 'data:image/png;base64,' + base64.b64encode(preview_atlas(s)).decode()}]}


def render_icon(s: ArmorSet, piece, size=64):
    from boss_render import Rig, render
    rig = Rig(preview_model(s, (piece,), mannequin=False))
    yaw, elevation = {'helm': (-28, 12), 'chestplate': (-22, 10), 'leggings': (-22, 10), 'boots': (-35, 14)}[piece]
    image, _ = render(rig, 'none', 0, yaw=yaw, elevation=elevation, size=size * 4, background=(0, 0, 0), bloom=False, ground=False)
    a = np.asarray(image.convert('RGB'), float)
    alpha = (a.sum(axis=2) > 6).astype(np.uint8) * 255
    rgba = Image.fromarray(np.dstack([a.astype(np.uint8), alpha]), 'RGBA')
    crop = rgba.crop(rgba.getbbox())
    side = max(crop.size)
    square = Image.new('RGBA', (side, side))
    square.alpha_composite(crop, ((side - crop.size[0]) // 2, (side - crop.size[1]) // 2))
    icon = square.resize((size - 4, size - 4), Image.Resampling.LANCZOS)
    out = Image.new('RGBA', (size, size))
    out.alpha_composite(icon, (2, 2))
    px = np.asarray(out).copy()
    px[..., 3] = np.where(px[..., 3] > 100, 255, 0)
    return Image.fromarray(px, 'RGBA')


# ---------------------------------------------------------------------------
# Pack registration


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf8')


def save(image, *paths):
    for path in paths:
        path.parent.mkdir(parents=True, exist_ok=True)
        image.save(path)


def register(s: ArmorSet, java, bedrock, item_atlas, definitions):
    """Write the set into Advance Magic's Java and Bedrock packs and Geyser mappings."""
    s.art.mkdir(parents=True, exist_ok=True)
    jt = java / f'assets/{NS}/textures'
    k = s.key
    save(s.layer_one(), jt / f'entity/equipment/humanoid/{k}.png', bedrock / f'textures/{k}/layer_1.png', s.art / 'layer_1.png')
    save(s.layer_two(), jt / f'entity/equipment/humanoid_leggings/{k}.png', bedrock / f'textures/{k}/layer_2.png', s.art / 'layer_2.png')
    save(helm_atlas(s), jt / f'item/{k}/helm.png', bedrock / f'textures/{k}/helm.png', s.art / 'helm_materials.png')
    write_json(java / f'assets/{NS}/equipment/{k}.json', {'layers': {
        'humanoid': [{'texture': s.asset}], 'humanoid_leggings': [{'texture': s.asset}]}})
    f = s.helm()
    write_json(s.art / 'helm.geometry.json', {'id': f'{k}_helm', 'cubes': f.cubes})
    write_json(java / f'assets/{NS}/models/item/{k}/helm.json', java_helm_model(s, f))
    write_json(bedrock / f'models/entity/{k}/helm.geo.json', bedrock_helm(s, f))
    for piece, title, base, slot, protection in s.pieces:
        icon = render_icon(s, piece)
        save(icon, jt / f'item/{k}/{piece}_icon.png', bedrock / f'textures/{k}/{piece}_icon.png', s.art / f'{piece}_icon.png')
        write_json(java / f'assets/{NS}/models/item/{k}/{piece}_icon.json', {
            'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{k}/{piece}_icon'}})
        icon_model = {'type': 'minecraft:model', 'model': f'{NS}:item/{k}/{piece}_icon'}
        if piece == 'helm':
            # Worn on the head the 3D helm shows; in hands, frames and menus the icon does.
            model = {'type': 'minecraft:select', 'property': 'minecraft:display_context',
                     'cases': [{'when': list(HELD), 'model': icon_model}],
                     'fallback': {'type': 'minecraft:model', 'model': f'{NS}:item/{k}/helm'}}
        else:
            model = icon_model
            write_json(bedrock / f'models/entity/{k}/{piece}.geo.json', bedrock_armor(s, piece))
        write_json(java / f'assets/{NS}/items/{k}_{piece}.json', {'model': model})
        write_json(bedrock / f'attachables/{k}/{piece}.json', attachable(s, piece))
        key = f'{NS}.{k}_{piece}'
        item_atlas[key] = {'textures': f'textures/{k}/{piece}_icon'}
        definitions.setdefault(f'minecraft:{base}', []).append({
            'type': 'definition', 'model': f'{NS}:{k}_{piece}', 'bedrock_identifier': f'{NS}:{k}_{piece}',
            'display_name': title,
            'bedrock_options': {'icon': key, 'allow_offhand': False, 'display_handheld': False, 'protection_value': protection,
                                'creative_category': 'equipment'},
            'components': {'minecraft:equippable': {'slot': slot}}})
    write_json(bedrock / f'render_controllers/{k}_armor.render.json', render_controllers(s))


def owned(key):
    """Pack paths a set writes (for patching dist packs in place)."""
    return (f'assets/{NS}/items/{key}_', f'assets/{NS}/models/item/{key}/', f'assets/{NS}/textures/item/{key}/',
            f'assets/{NS}/equipment/{key}.json', f'assets/{NS}/textures/entity/equipment/humanoid/{key}.png',
            f'assets/{NS}/textures/entity/equipment/humanoid_leggings/{key}.png',
            f'attachables/{key}/', f'models/entity/{key}/', f'textures/{key}/', f'render_controllers/{key}_armor.render.json')


# ---------------------------------------------------------------------------
# Preview sheets


def previews(s: ArmorSet, out):
    from boss_render import Rig, label, render
    out.mkdir(parents=True, exist_ok=True)
    stand = {'right_arm': [0, 0, 6], 'left_arm': [0, 0, -6]}
    walk = {'right_arm': [28, 0, 6], 'left_arm': [-28, 0, -6], 'right_leg': [-24, 0, 0], 'left_leg': [24, 0, 0], 'head': [-6, 12, 0]}
    full = Rig(preview_model(s, pose=stand))
    moving = Rig(preview_model(s, pose=walk))
    tiles = []
    for rig, yaw, el, title in ((full, 0, 6, 'Front'), (full, 35, 10, 'Three-quarter'), (full, 90, 6, 'Side'),
                                (full, 160, 10, 'Back three-quarter'), (moving, -35, 10, 'Walking')):
        image, _ = render(rig, 'pose', 0, yaw=yaw, elevation=el, size=560, span=40, center=(0, 19), background=s.background)
        tiles.append(label(image, title, None, s.label_color))
    sheet = Image.new('RGB', (560 * 5, 560), s.background)
    for i, t in enumerate(tiles):
        sheet.paste(t, (i * 560, 0))
    sheet.save(out / f'{s.key}-turnaround.png')
    helm_rig = Rig(preview_model(s, ('helm',), mannequin=True, pose=stand))
    tiles = []
    for yaw, el in ((0, 4), (-35, 10), (-90, 4), (150, 12)):
        image, _ = render(helm_rig, 'pose', 0, yaw=yaw, elevation=el, size=480, span=22, center=(0, 32), background=s.background)
        tiles.append(image)
    sheet = Image.new('RGB', (480 * 4, 480), s.background)
    for i, t in enumerate(tiles):
        sheet.paste(t, (i * 480, 0))
    sheet.save(out / f'{s.key}-helm.png')
    textures = Image.new('RGBA', (256 * 2 + 64 * 4 + 60, 300), s.background + (255,))
    textures.alpha_composite(s.layer_one(), (10, 10))
    textures.alpha_composite(s.layer_two(), (276, 10))
    for i, (piece, *_rest) in enumerate(s.pieces):
        textures.alpha_composite(render_icon(s, piece).resize((128, 128), Image.Resampling.NEAREST), (10 + i * 140, 160))
    textures.convert('RGB').save(out / f'{s.key}-textures-icons.png')
    return [out / f'{s.key}-{name}.png' for name in ('turnaround', 'helm', 'textures-icons')]
