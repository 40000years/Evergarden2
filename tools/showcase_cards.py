"""Render every Evergarden boss and pet from the shipped models into player-facing posters.

    python tools/showcase_cards.py              render (cached) and compose docs/showcase/*.png
    python tools/showcase_cards.py --rerender   ignore cached renders

Geometry, atlases and clips come from evergarden/src/main/resources/modelengine (boss_render's
rasteriser and lighting); the Bodiless Judge comes from judge-shape.yml and the shipped judge pack,
as in preview_ivory_judge.py. Renders are cut out on transparent backgrounds and cached under
output/showcase-cards/renders. Posters are HTML captured with headless Edge, because Pillow on this
machine has no Thai shaping (see tools/recipe_cards.py).
"""
import argparse
import io
import json
import math
import os
import subprocess
import sys
import zipfile
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

import numpy as np
from PIL import Image, ImageChops, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'evergarden/tools'))
import boss_render as br  # noqa: E402

MODELS = ROOT / 'evergarden/src/main/resources/modelengine'
WORK = ROOT / 'output/showcase-cards'
RENDERS = WORK / 'renders'
OUT = ROOT / 'docs/showcase'
EDGE = Path(r'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')
OUTSIDE = (1, 2, 3)

# name: (blueprint, clip, time, yaw, elevation, size)
BOSS_SHOTS = {
    'arkhaia': ('arkhaia', 'idle', .6, 20, -8, 1100),
    'seraphon': ('seraphon', 'idle', .6, 16, -8, 1100),
    'acheron': ('rivers/arkhaia_acheron', 'idle', .5, 26, 6, 900),
    'phlegethon': ('rivers/arkhaia_phlegethon', 'idle', .5, 26, 6, 900),
    'cocytus': ('rivers/arkhaia_cocytus', 'idle', .5, 26, 6, 900),
    'banshee': ('rivers/arkhaia_banshee_wail', 'idle', .5, 26, 6, 900),
    'malefeasance': ('rivers/arkhaia_malefeasance', 'idle', .5, 22, 4, 900),
    'malefeasance_phase2': ('rivers/arkhaia_malefeasance_phase2', 'idle', .5, 22, 4, 700),
    'styx': ('rivers/arkhaia_styx', 'idle', .5, 26, 6, 900),
    'cinder_colossus': ('realm/cinder_colossus', 'idle', .8, 28, 8, 1000),
    'soul_hound': ('realm/soul_hound', 'idle', .8, 34, 8, 1000),
    'crystal_moth': ('realm/crystal_moth', 'idle', .8, 24, 8, 1000),
    'unlit_sun': ('realm/unlit_sun', 'idle', .8, 24, 6, 1000),
}
# Flat swimmers read as a sliver from the default low camera.
PET_CAMERA = {'abyssal_manta': (30, 38)}


def render_rgba(rig, clip, time, yaw, elevation, size, supersample=2):
    """boss_render.render on a transparent background, framed on the visible faces."""
    matrices = rig.matrices(clip, time, None, time)
    big = size * supersample
    az, el = math.radians(yaw), math.radians(elevation)
    eye = np.array([-math.sin(az) * math.cos(el), math.sin(el), -math.cos(az) * math.cos(el)])
    right = np.cross(-eye, [0, 1, 0])
    right /= np.linalg.norm(right)
    up = np.cross(right, -eye)
    cuboids = [c for c in rig.cuboids(matrices) if any(f.get('texture') is not None for f in c[0]['faces'].values())]
    pts = np.concatenate([w for _, w, _ in cuboids])
    sx, sy = pts @ right, pts @ up
    span = max(sx.max() - sx.min(), sy.max() - sy.min()) * 1.06
    center = np.array([(sx.max() + sx.min()) / 2, (sy.max() + sy.min()) / 2])
    scale = big / span
    canvas = np.zeros((big, big, 3), float)
    glow = np.zeros((big, big, 3), float)
    depth = np.full((big, big), -1e9)
    light = np.array([-.45, .75, -.5]) / np.linalg.norm([-.45, .75, -.5])
    fill = np.array([.6, .2, .7]) / np.linalg.norm([.6, .2, .7])
    tex = rig.texture
    th, tw = tex.shape[:2]
    rw, rh = rig.model['resolution']['width'], rig.model['resolution']['height']
    for e, _, bone in cuboids:
        lo, hi = np.array(e['from'], float), np.array(e['to'], float)
        r_el = br.rot(e.get('rotation', [0, 0, 0]))
        m = matrices[bone]
        pivot = np.array(e.get('origin', [0, 0, 0]), float)
        for face, data in e['faces'].items():
            if data.get('texture') is None:
                continue
            n = m[:3, :3] @ (r_el @ np.array(br.NORMALS[face], float))
            if n @ eye <= 1e-6:
                continue
            quad = np.array([[hi[i] if c else lo[i] for i, c in enumerate(v)] for v in br.FACE_QUADS[face]])
            quad = ((quad - pivot) @ r_el.T + pivot) @ m[:3, :3].T + m[:3, 3]
            px = (quad @ right - center[0]) * scale + big / 2
            py = big / 2 - (quad @ up - center[1]) * scale
            pz = quad @ eye
            u0, v0, u1, v1 = data['uv']
            us = np.array([u0, u0, u1, u1]) * tw / rw
            vs = np.array([v0, v1, v1, v0]) * th / rh
            shade = e.get('shade', True)
            bright = (.5 + .42 * max(0.0, float(n @ light)) + .16 * max(0.0, float(n @ fill)) + .08 * max(0.0, n[1])) if shade else 1.0
            for tri in ((0, 1, 2), (0, 2, 3)):
                br._raster(canvas, glow, depth, px[list(tri)], py[list(tri)], pz[list(tri)],
                           us[list(tri)], vs[list(tri)], tex, bright, not shade)
    return finish(canvas, glow, depth > -1e9, size, blur=big / 90)


def finish(canvas, glow, mask, size, blur):
    """Premultiplied RGBA: solid where geometry is, soft additive bloom around emissive texels."""
    bloom = np.asarray(Image.fromarray(np.clip(glow, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(blur)), float) * .55
    rgb = canvas * mask[..., None] + bloom
    alpha = np.maximum(mask.astype(float), np.clip(bloom.max(axis=2) / 255 * 1.4, 0, 1))
    premult = Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8))
    a = Image.fromarray((alpha * 255).astype(np.uint8))
    width = size if canvas.shape[1] == canvas.shape[0] else round(size * canvas.shape[1] / canvas.shape[0])
    target = (width, size)
    premult, a = premult.resize(target, Image.Resampling.LANCZOS), a.resize(target, Image.Resampling.LANCZOS)
    p = np.asarray(premult, float)
    av = np.asarray(a, float)[..., None] / 255
    straight = np.where(av > 0, p / np.maximum(av, 1e-6), 0)
    out = Image.fromarray(np.dstack([np.clip(straight, 0, 255), av[..., 0] * 255]).astype(np.uint8), 'RGBA')
    return out.crop(out.getbbox())


def render_judge(width=2400, height=1700):
    """preview_ivory_judge.render (phase 1, full figure) with alpha from its depth buffer."""
    shape = json.loads((ROOT / 'evergarden/src/main/resources/judge-shape.yml').read_text())
    with zipfile.ZipFile(ROOT / 'evergarden/dist/judge-java.zip') as pack:
        atlas = np.asarray(Image.open(io.BytesIO(pack.read('assets/voidscape/textures/item/judge_atlas.png'))).convert('RGB'))
        seal = np.asarray(Image.open(io.BytesIO(pack.read('assets/voidscape/textures/effect/judge_seal.png'))).convert('RGBA'))
    boxes = []
    for name, mesh in shape.items():
        if name in ('judge_shard', 'judge_heart'):
            continue
        side = name.startswith(('judge_left', 'judge_right'))
        offset = np.array([-21 if name.startswith('judge_left') else 21 if name.startswith('judge_right') else 0,
                           1 if side else 17, -2 if side else 0]) + np.array(mesh.get('offset', [0, 0, 0]))
        for c in mesh['cubes']:
            boxes.append((np.array(c['from']) + offset, np.array(c['size']), c['tile'], name.endswith('_light')))
    w, h = width, height
    az, el = math.radians(168), math.radians(8)
    cam = np.array([math.sin(az) * math.cos(el), math.sin(el), math.cos(az) * math.cos(el)])
    right = np.array([-math.cos(az), 0, math.sin(az)])
    up = np.array([-math.sin(el) * math.sin(az), math.cos(el), -math.sin(el) * math.cos(az)])
    proj = np.array([right, -up, cam])
    sun = np.array([-.4, .75, -.6]) / np.linalg.norm([-.4, .75, -.6])
    pts = np.array([p + s * np.array([a, b, c]) for p, s, _, _ in boxes for a in (0, 1) for b in (0, 1) for c in (0, 1)]) @ proj.T
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    scale = min(w * .92 / (hi[0] - lo[0]), h * .80 / (hi[1] - lo[1]))
    origin = np.array([w / 2 - scale * (lo[0] + hi[0]) / 2, h * .46 - scale * (lo[1] + hi[1]) / 2])
    canvas = np.zeros((h, w, 3), np.float32)
    alpha = np.zeros((h, w), np.float32)
    depth = np.full((h, w), -1e10, np.float32)
    glow = np.zeros((h, w, 3), np.float32)
    for p, size, tile, emits in boxes:
        for axis, ta, tb in ((0, 1, 2), (1, 0, 2), (2, 0, 1)):
            sign = 1 if cam[axis] > 0 else -1
            a = p.copy()
            a[axis] += size[axis] if sign > 0 else 0
            e1, e2 = np.zeros(3), np.zeros(3)
            e1[ta], e2[tb] = size[ta], size[tb]
            q = (proj @ a)[:2] * scale + origin
            s1, s2 = (proj @ e1)[:2] * scale, (proj @ e2)[:2] * scale
            corners = np.array([q, q + s1, q + s2, q + s1 + s2])
            x0, x1 = max(0, math.floor(corners[:, 0].min())), min(w, math.ceil(corners[:, 0].max()))
            y0, y1 = max(0, math.floor(corners[:, 1].min())), min(h, math.ceil(corners[:, 1].max()))
            det = s1[0] * s2[1] - s1[1] * s2[0]
            if x0 >= x1 or y0 >= y1 or abs(det) < 1e-7:
                continue
            x = np.arange(x0, x1)[None, :] + .5 - q[0]
            y = np.arange(y0, y1)[:, None] + .5 - q[1]
            u, v = (x * s2[1] - y * s2[0]) / det, (y * s1[0] - x * s1[1]) / det
            z = cam @ a + u * (cam @ e1) + v * (cam @ e2)
            mask = (u >= 0) & (u <= 1) & (v >= 0) & (v <= 1) & (z > depth[y0:y1, x0:x1])
            tx = np.clip((9 + u * 238).astype(int), 9, 246) + tile % 4 * 256
            ty = np.clip((9 + (1 - v) * 238).astype(int), 9, 246) + tile // 4 * 256
            normal = np.zeros(3)
            normal[axis] = sign
            colors = atlas[ty, tx].astype(np.float32) * (1.1 if emits else .62 + .40 * max(0, normal @ sun))
            canvas[y0:y1, x0:x1][mask] = colors[mask]
            alpha[y0:y1, x0:x1][mask] = 1
            depth[y0:y1, x0:x1][mask] = z[mask]
            glow[y0:y1, x0:x1][mask] = (np.array([255, 52, 80]) * .9) if emits else 0
    # The two judgment seals under the hands, as in the preview.
    for side in (-1, 1):
        size = 22 * 512 / 480
        a = np.array([side * 21, .1, -2]) - np.array([size / 2, 0, size / 2])
        e1, e2 = np.array([size, 0, 0]), np.array([0, 0, size])
        q = (proj @ a)[:2] * scale + origin
        s1, s2 = (proj @ e1)[:2] * scale, (proj @ e2)[:2] * scale
        corners = np.array([q, q + s1, q + s2, q + s1 + s2])
        det = s1[0] * s2[1] - s1[1] * s2[0]
        x0, x1 = max(0, math.floor(corners[:, 0].min())), min(w, math.ceil(corners[:, 0].max()))
        y0, y1 = max(0, math.floor(corners[:, 1].min())), min(h, math.ceil(corners[:, 1].max()))
        if abs(det) < 1e-7 or x0 >= x1 or y0 >= y1:
            continue
        x = np.arange(x0, x1)[None, :] + .5 - q[0]
        y = np.arange(y0, y1)[:, None] + .5 - q[1]
        u, v = (x * s2[1] - y * s2[0]) / det, (y * s1[0] - x * s1[1]) / det
        z = cam @ a + u * (cam @ e1) + v * (cam @ e2)
        px = seal[np.clip(((1 - v) * 1023).astype(int), 0, 1023), np.clip((u * 1023).astype(int), 0, 1023)]
        pa = (px[..., 3] / 255 * ((u >= 0) & (u <= 1) & (v >= 0) & (v <= 1) & (z > depth[y0:y1, x0:x1]))).astype(np.float32)
        region = canvas[y0:y1, x0:x1]
        canvas[y0:y1, x0:x1] = region * (1 - pa[..., None]) + px[..., :3] * pa[..., None]
        alpha[y0:y1, x0:x1] = alpha[y0:y1, x0:x1] * (1 - pa) + pa
    bloom = np.asarray(Image.fromarray(np.clip(glow, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(14)), np.float32) * .45
    rgb = canvas * alpha[..., None] + bloom
    a = np.maximum(alpha, np.clip(bloom.max(axis=2) / 255 * 1.4, 0, 1))
    straight = np.where(a[..., None] > 0, rgb / np.maximum(a[..., None], 1e-6), 0)
    out = Image.fromarray(np.dstack([np.clip(straight, 0, 255), a * 255]).astype(np.uint8), 'RGBA')
    return out.crop(out.getbbox())


def load_rig(blueprint):
    return br.Rig(json.loads((MODELS / f'{blueprint}.bbmodel').read_text(encoding='utf8')))


def job(task):
    """One cached render; runs in a worker process."""
    name, kind, args, rerender = task
    target = RENDERS / f'{name}.png'
    if target.exists() and not rerender:
        return name, 'cached'
    if kind == 'judge':
        image = render_judge()
    else:
        blueprint, clip, time, yaw, elevation, size = args
        image = render_rgba(load_rig(blueprint), clip, time, yaw, elevation, size)
    temporary = target.with_suffix('.tmp.png')
    image.save(temporary)
    temporary.replace(target)
    return name, f'{image.width}x{image.height}'


def tasks(rerender, only=None):
    from pet_model_engine import RARE, MENAGERIE
    work = [('judge', 'judge', None, rerender)]
    work += [(name, 'model', shot, rerender) for name, shot in BOSS_SHOTS.items()]
    for species in RARE + MENAGERIE:
        yaw, elevation = PET_CAMERA.get(species, (32, 14))
        work.append((f'pet_{species}_adult', 'model', (f'pets/everpet_{species}_adult', 'idle', 0.0, yaw, elevation, 560), rerender))
        work.append((f'pet_{species}_baby', 'model', (f'pets/everpet_{species}_baby', 'idle', 0.0, yaw, elevation, 260), rerender))
    return [t for t in work if only is None or t[0] in only]


def render_all(rerender, only=None):
    RENDERS.mkdir(parents=True, exist_ok=True)
    work = tasks(rerender, only)
    with ProcessPoolExecutor(max_workers=max(1, (os.cpu_count() or 2) - 1)) as pool:
        for name, result in pool.map(job, work):
            print(f'{name:34} {result}', flush=True)


# ---- poster content ---------------------------------------------------------------------------
# Facts from docs/ancient-judge.md, evergarden-main-bosses.md, evergarden-endgame-bosses.md,
# arkhaia-summoning.md, the river docs, realm-bosses.md and the released config.
ENDGAME = [
    dict(render='judge', c='#f2c879', c2='#ff4d6d', loc='บอสโลก · วิหารโบราณใน Evergarden', en='THE BODILESS JUDGE',
         epi='Ivory Sovereign of the Ancient Temple', th='ผู้พิพากษาไร้ร่าง',
         desc='หน้ากากงาช้างมหึมาใต้มงกุฎหอคอยทองเจ็ดยอด กางปีกมหาวิหาร ผ้าคลุมทมิฬแขวนลอย '
              'และมือสองข้างที่ร่ายตราพิพากษาลงบนลาน',
         chips=['วิหารขนาด 241×241 บล็อก', 'กลับมาทุก 4 ชั่วโมง', 'ลงดาเมจที่มือและแกนกลาง']),
    dict(render='arkhaia', c='#b07bff', c2='#ff3355', loc='บอส Endgame · ปราสาท Arkhaia', en='ARKHAIA',
         epi='The Dreadrose Sovereign', th='ราชันย์กุหลาบทมิฬ',
         desc='จอมราชันชุดเกราะออบซิเดียนกับดาบใหญ่ Thanasius’ Ruin ดวลดาบประชิดตัว 3 เฟส '
              'และอัญเชิญวิญญาณหกสายน้ำมาร่วมรบ',
         chips=['อัญเชิญด้วยบล็อกเนเธอไรต์', '3 เฟส', 'ปัดดาบ · Perfect Guard']),
    dict(render='seraphon', c='#ffd27a', c2='#7fd8ff', loc='บอส Endgame · มหาวิหาร Supreme Sanctuary', en='SERAPHON',
         epi='The Gilded Verdict', th='มหาเทวีบัญชาสวรรค์',
         desc='ผู้พิพากษาแห่ง Church of Raphion ลอยฟ้าด้วยหกปีกทองคำ รัศมีสุริยะและวงโคจรเวทหมุนรอบกาย '
              'ต้องยืนตำแหน่งให้ดีเพื่อหลบคำพิพากษาจากฟากฟ้า',
         chips=['ลอยฟ้า 6 ปีก', '3 เฟส', 'ท่าไม้ตาย Verdict']),
]
RIVERS = [
    dict(render='acheron', c='#ff5a7a', en='ACHERON', epi='The Weeping Bladedancer', th='นักดาบร่ายรำผู้ร่ำไห้',
         loc='วิหารใน ป่ามืด', trait='คมดาบทำให้เลือดไหล'),
    dict(render='phlegethon', c='#ff8f3d', en='PHLEGETHON', epi='The Burning Furnace', th='เตาหลอมแห่งสายน้ำเพลิง',
         loc='วิหารใน แบดแลนด์', trait='ขว้างหินเพลิง ทางพุ่งกลายเป็นไฟวิญญาณ'),
    dict(render='cocytus', c='#6fd6ff', en='COCYTUS', epi='The Frost Tempest', th='พายุเยือกแข็ง',
         loc='วิหารใน ที่ราบหิมะ', trait='คุกน้ำแข็ง ฝนศร และดาบที่ทำให้ช้าลง'),
    dict(render='banshee', c='#5ff2c6', en='BANSHEE', epi='The Mourning Veil', th='หญิงหม้ายในม่านไว้ทุกข์',
         loc='วิหารใน ที่ลุ่มน้ำขัง', trait='ทำลาย Soul Anchor ก่อนจึงตีร่างจริงได้'),
    dict(render='malefeasance', inset='malefeasance_phase2', inset_label='ร่างที่ 2 · Star Imp', c='#c77dff',
         en='MALEFEASANCE', epi='The Sin Witch', th='แม่มดบาปแห่งวงเวท', loc='วิหารใน ป่าเบิร์ช',
         trait='วงเวทใต้เท้า และแปลงร่างเป็นภูตดาราในเฟสสอง'),
    dict(render='styx', c='#ff3048', en='STYX', epi='The Blood Warrior', th='นักรบโลหิต',
         loc='วิหารใน สะวันนา', trait='ท่าหนักรุนแรงและทำให้อ่อนแรง'),
]
REALMS = [
    dict(render='cinder_colossus', c='#ff8a2a', realm='NETHER', loc='ป้อมมงกุฎลาวา', en='CINDER COLOSSUS',
         th='ยักษ์ป้อมปราการลาวา', desc='ป้อมปราการมีชีวิต หินดำรอยต่อลาวาเรือง มงกุฎ 12 แฉก หน้ากากกากบาทไฟ',
         ult='คุกเข่าเผยผลึกหัวใจกลางหลัง — จุดอ่อน'),
    dict(render='soul_hound', c='#4fdcff', realm='NETHER', loc='วิหารกระดูกวิญญาณ', en='SOUL-EATER HOUND',
         th='หมาป่ากลืนวิญญาณ', desc='หมาป่ากระดูกยักษ์ ตาและซี่โครงลุกด้วยไฟวิญญาณ ปลอกคอโซ่ขาด',
         ult='นั่งเห่าหอน ซี่โครงกางออก'),
    dict(render='crystal_moth', c='#d98cff', realm='THE END', loc='นครคริสตัลร้าง', en='CRYSTAL MOTH QUEEN',
         th='ราชินีผีเสื้อผลึก', desc='ปีกกระจกผลึกสี่ปีกลายตะกั่วทอง หัวใจผลึกชมพู เหล็กไนคริสตัล',
         ult='ห่อปีกเป็นดักแด้แล้วระเบิดกางออก'),
    dict(render='unlit_sun', c='#ffd34d', realm='THE END', loc='วิหารวงแหวนสุญญากาศ', en='UNLIT SUN',
         th='สุริยะอับแสง', desc='ดวงอาทิตย์มืดเปลือกออบซิเดียน รอยแตกทอง วงแหวนเศษหินและเสาหินลอย',
         ult='เปลือกแตกเป็นสุริยุปราคา เผยดวงตาม่านตาตั้ง'),
]
HABITATS = {'gardens': ('สวน Evergarden', '#ff9bcf'), 'glacier': ('เทือกเขาธารน้ำแข็ง', '#93dcff'),
            'forest': ('ป่าโบราณ', '#93e08c'), 'city': ('นครจันทราที่สาบสูญ', '#c9aaff')}


def pet_catalog():
    """(species, english, thai, habitats) in catalog order, read from the plugin source."""
    import re
    pet = ROOT / 'evergarden/src/main/java/com/example/voidscape/pet'
    names = re.findall(r'^\s+([A-Z_]+)\("([^"]+)",\s*"([^"]+)"\)', (pet / 'PetCatalog.java').read_text(encoding='utf8'), re.M)
    homes = {s: re.findall(r'\b(gardens|glacier|forest|city)\b', places) for s, places in
             re.findall(r'HABITAT\.put\(PetCatalog\.Species\.([A-Z_]+),EnumSet\.of\(([^)]*)\)\)', (pet / 'WildPetSpawns.java').read_text(encoding='utf8'))}
    return [(s.lower(), en, th, homes[s]) for s, en, th in names if s in homes]


def tint(image):
    """The pet's own accent: mean of its most saturated, bright opaque pixels."""
    rgba = np.asarray(image.convert('RGBA'), float)
    px = rgba[rgba[..., 3] > 200][:, :3]
    hi, lo = px.max(axis=1), px.min(axis=1)
    score = (hi - lo) * hi
    top = px[score >= np.quantile(score, .85)].mean(axis=0)
    top = top / max(top.max(), 1) * 255
    return '#%02x%02x%02x' % tuple(int(v) for v in top)


def uri(name):
    import base64
    return 'data:image/png;base64,' + base64.b64encode((RENDERS / f'{name}.png').read_bytes()).decode()


def stars():
    import base64
    rng = np.random.default_rng(7)
    tile = np.zeros((600, 600, 4), np.uint8)
    for _ in range(170):
        x, y = rng.integers(0, 600, 2)
        b = int(rng.uniform(90, 255))
        tile[y, x] = (255, 245, 255, b)
        if rng.random() < .12:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                tile[(y + dy) % 600, (x + dx) % 600] = (255, 240, 255, b // 3)
    buffer = io.BytesIO()
    Image.fromarray(tile, 'RGBA').save(buffer, 'PNG')
    return 'data:image/png;base64,' + base64.b64encode(buffer.getvalue()).decode()


CSS = """
*{box-sizing:border-box;margin:0;padding:0}
html,body{background:rgb(1,2,3)}
.page{position:relative;width:1600px;padding:76px 64px 52px;overflow:hidden;color:#f1ecfa;
  font-family:"Leelawadee UI",Tahoma,sans-serif;background:var(--bg)}
.page::before{content:"";position:absolute;inset:0;background:url(STARS);opacity:.5}
.page::after{content:"";position:absolute;inset:0;background:radial-gradient(ellipse 75% 60% at 50% 42%,transparent 55%,rgba(0,0,0,.6))}
.page>*{position:relative;z-index:1}
header{text-align:center;margin-bottom:46px}
.kicker{font:700 19px "Palatino Linotype",serif;letter-spacing:.55em;color:var(--accent);padding-left:.55em}
h1{font-size:80px;font-weight:700;line-height:1.32;margin-top:8px;color:#fff;
  text-shadow:0 0 34px color-mix(in srgb,var(--accent) 55%,transparent),0 4px 0 rgba(0,0,0,.35)}
.sub{font:italic 25px "Palatino Linotype",serif;letter-spacing:.16em;color:#d8cfee}
.lead{font-size:22px;line-height:1.65;color:#bdb4d4;margin:16px auto 0;max-width:1080px}
.orn{display:flex;align-items:center;justify-content:center;gap:16px;margin-top:20px}
.orn i{width:200px;height:1px;background:linear-gradient(90deg,transparent,var(--accent))}
.orn i:last-child{background:linear-gradient(270deg,transparent,var(--accent))}
.orn b{width:11px;height:11px;transform:rotate(45deg);border:1.5px solid var(--accent);box-shadow:0 0 12px var(--accent)}
.card{position:relative;border-radius:22px;overflow:hidden;isolation:isolate;
  background:linear-gradient(180deg,color-mix(in srgb,var(--c) 13%,#141020) 0%,#0e0b16 72%);
  border:1px solid color-mix(in srgb,var(--c) 42%,transparent);
  box-shadow:0 24px 60px rgba(0,0,0,.5),inset 0 1px 0 rgba(255,255,255,.07)}
.card::before,.card::after{content:"";position:absolute;width:13px;height:13px;transform:rotate(45deg);z-index:3;
  border:1.5px solid color-mix(in srgb,var(--c) 80%,white);background:#0e0b16;top:14px}
.card::before{left:14px}.card::after{right:14px}
.stage{position:relative;display:flex;align-items:flex-end;justify-content:center;padding-bottom:26px;overflow:hidden}
.stage .halo{position:absolute;left:50%;top:46%;width:130%;aspect-ratio:1;transform:translate(-50%,-50%);z-index:-1;
  background:radial-gradient(circle,color-mix(in srgb,var(--c) 46%,transparent) 0%,color-mix(in srgb,var(--c2,var(--c)) 14%,transparent) 34%,transparent 62%)}
.stage .rays{position:absolute;left:50%;top:44%;width:170%;aspect-ratio:1;transform:translate(-50%,-50%);z-index:-1;opacity:.13;
  background:repeating-conic-gradient(from 0deg,color-mix(in srgb,var(--c) 70%,white) 0 2deg,transparent 2deg 15deg);
  -webkit-mask:radial-gradient(circle,#000 0%,transparent 58%);mask:radial-gradient(circle,#000 0%,transparent 58%)}
.stage .floor{position:absolute;left:50%;bottom:10px;width:66%;height:44px;transform:translateX(-50%);z-index:-1;border-radius:50%;
  background:radial-gradient(ellipse,color-mix(in srgb,var(--c) 55%,transparent),transparent 70%)}
.stage img.model{position:relative;object-fit:contain;filter:drop-shadow(0 16px 26px rgba(0,0,0,.65))}
.meta{padding:6px 30px 30px}
.loc{display:inline-flex;align-items:center;gap:9px;font-size:17px;line-height:1.5;padding:4px 15px;border-radius:999px;
  color:color-mix(in srgb,var(--c) 80%,white);background:color-mix(in srgb,var(--c) 14%,transparent);
  border:1px solid color-mix(in srgb,var(--c) 45%,transparent)}
.loc::before{content:"";width:7px;height:7px;transform:rotate(45deg);background:var(--c)}
.en{font:700 42px/1.15 "Palatino Linotype",serif;letter-spacing:.07em;margin-top:14px;color:#fff;
  text-shadow:0 0 22px color-mix(in srgb,var(--c) 50%,transparent)}
.epi{font:italic 22px "Palatino Linotype",serif;color:color-mix(in srgb,var(--c) 75%,white);margin-top:4px}
.th{font-size:29px;font-weight:700;line-height:1.45;margin-top:6px}
.desc{font-size:19.5px;line-height:1.62;color:#cbc3dd;margin-top:8px}
.chips{display:flex;flex-wrap:wrap;gap:8px;margin-top:14px}
.chips span{font-size:16px;line-height:1.5;padding:4px 13px;border-radius:9px;color:#e9e3f5;
  background:rgba(255,255,255,.06);border:1px solid rgba(255,255,255,.12)}
footer{margin-top:34px;text-align:center;font-size:16px;color:#857d9c;letter-spacing:.04em}
footer b{font:700 15px "Palatino Linotype",serif;letter-spacing:.4em;color:color-mix(in srgb,var(--accent) 70%,white);margin-right:10px}
"""


def page(css, accent, background, header, body):
    footer = '<footer><b>EVERGARDEN</b>ภาพเรนเดอร์จากโมเดลจริงในเกม · แสงจำลอง ไม่ใช่ภาพหน้าจอ</footer>'
    return (f'<!doctype html><html lang="th"><head><meta charset="utf-8"><style>{CSS.replace("STARS", stars())}{css}</style></head>'
            f'<body><div class="page" style="--accent:{accent};--bg:{background}">{header}{body}{footer}</div></body></html>')


def head(kicker, title, sub, lead):
    return (f'<header><div class="kicker">{kicker}</div><h1>{title}</h1><div class="sub">{sub}</div>'
            f'<p class="lead">{lead}</p><div class="orn"><i></i><b></b><i></i></div></header>')


def stage(name, height, max_w, max_h, extra=''):
    return (f'<div class="stage" style="height:{height}px"><div class="rays"></div><div class="halo"></div><div class="floor"></div>'
            f'<img class="model" src="{uri(name)}" style="max-width:{max_w}px;max-height:{max_h}px">{extra}</div>')


def chips(items):
    return '<div class="chips">' + ''.join(f'<span>{i}</span>' for i in items) + '</div>'


def endgame_poster():
    judge, *duo = ENDGAME
    css = """
.judge{display:grid;grid-template-columns:500px 1fr;align-items:center;margin-bottom:30px}
.judge .meta{padding:40px 10px 40px 44px}
.judge .stage{overflow:visible}
.duo{display:grid;grid-template-columns:1fr 1fr;gap:30px}
"""
    cards = (f'<article class="card judge" style="--c:{judge["c"]};--c2:{judge["c2"]}">'
             f'<div class="meta"><div class="loc">{judge["loc"]}</div><div class="en">{judge["en"]}</div><div class="epi">{judge["epi"]}</div>'
             f'<div class="th">{judge["th"]}</div><p class="desc">{judge["desc"]}</p>{chips(judge["chips"])}</div>'
             f'{stage("judge", 600, 900, 560)}</article><div class="duo">')
    for b in duo:
        cards += (f'<article class="card" style="--c:{b["c"]};--c2:{b["c2"]}">{stage(b["render"], 700, 680, 650)}'
                  f'<div class="meta"><div class="loc">{b["loc"]}</div><div class="en">{b["en"]}</div><div class="epi">{b["epi"]}</div>'
                  f'<div class="th">{b["th"]}</div><p class="desc">{b["desc"]}</p>{chips(b["chips"])}</div></article>')
    cards += '</div>'
    header = head('EVERGARDEN · BESTIARY', 'บอสใหญ่แห่ง Evergarden', 'World &amp; Endgame Bosses',
                  'สามผู้ครองบัลลังก์สูงสุดของมิติ — บอสโลกแห่งวิหารโบราณ และสองบอส Endgame ที่รอผู้กล้าอยู่ในปราสาทและมหาวิหาร')
    bg = ('radial-gradient(900px 600px at 20% 6%,rgba(242,200,121,.18),transparent 60%),'
          'radial-gradient(900px 700px at 85% 60%,rgba(176,123,255,.16),transparent 60%),#0b0912')
    return page(css, '#f2c879', bg, header, cards)


def rivers_poster():
    css = """
.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:26px}
.grid .en{font-size:34px}.grid .th{font-size:25px}.grid .epi{font-size:19px}
.trait{display:flex;gap:10px;margin-top:12px;font-size:18px;line-height:1.55;color:#ddd5ee}
.trait::before{content:"✦";color:var(--c)}
.inset{position:absolute;right:16px;bottom:22px;display:flex;flex-direction:column;align-items:center;gap:4px}
.inset img{max-height:255px;filter:drop-shadow(0 0 14px color-mix(in srgb,#7aa8ff 60%,transparent))}
.inset span{font-size:14px;color:#c9d8ff;background:rgba(10,14,40,.7);padding:2px 10px;border-radius:999px;border:1px solid rgba(122,168,255,.4)}
"""
    body = '<div class="grid">'
    for r in RIVERS:
        inset = (f'<div class="inset"><img src="{uri(r["inset"])}"><span>{r["inset_label"]}</span></div>' if 'inset' in r else '')
        body += (f'<article class="card" style="--c:{r["c"]}">{stage(r["render"], 500, 380 if inset else 420, 460, inset)}'
                 f'<div class="meta"><div class="loc">{r["loc"]}</div><div class="en">{r["en"]}</div><div class="epi">{r["epi"]}</div>'
                 f'<div class="th">{r["th"]}</div><div class="trait">{r["trait"]}</div></div></article>')
    body += '</div>'
    header = head('EVERGARDEN · BESTIARY', 'หกสายน้ำแห่ง Arkhaia', 'The Six Rivers · Sanctuary Bosses',
                  'วิญญาณแม่น้ำทั้งหกเฝ้าวิหารศักดิ์สิทธิ์ในโลกปกติคนละแห่ง และถูก Arkhaia อัญเชิญกลับมาร่วมรบในเฟสสอง')
    bg = ('radial-gradient(900px 600px at 12% 10%,rgba(255,90,122,.15),transparent 60%),'
          'radial-gradient(900px 600px at 88% 14%,rgba(111,214,255,.14),transparent 60%),'
          'radial-gradient(1000px 700px at 50% 95%,rgba(199,125,255,.14),transparent 60%),#0a0911')
    return page(css, '#ff8fa6', bg, header, body)


def realms_poster():
    css = """
.grid{display:grid;grid-template-columns:1fr 1fr;gap:30px}
.realm{font:700 15px "Palatino Linotype",serif;letter-spacing:.35em;color:var(--c);margin-right:4px}
.ult{margin-top:14px;padding:12px 16px;border-radius:12px;font-size:18.5px;line-height:1.55;color:#efe8fb;
  background:linear-gradient(90deg,color-mix(in srgb,var(--c) 20%,transparent),transparent);border-left:3px solid var(--c)}
.ult b{display:block;font:700 13px "Palatino Linotype",serif;letter-spacing:.3em;color:color-mix(in srgb,var(--c) 80%,white)}
"""
    body = '<div class="grid">'
    for r in REALMS:
        body += (f'<article class="card" style="--c:{r["c"]}">{stage(r["render"], 560, 660, 520)}'
                 f'<div class="meta"><div class="loc"><span class="realm">{r["realm"]}</span>{r["loc"]}</div>'
                 f'<div class="en">{r["en"]}</div><div class="th">{r["th"]}</div><p class="desc">{r["desc"]}</p>'
                 f'<div class="ult"><b>ULTIMATE</b>{r["ult"]}</div></div></article>')
    body += '</div>'
    header = head('EVERGARDEN · BESTIARY', 'บอสประจำมิติ', 'Realm Bosses · Nether &amp; The End',
                  'ผู้พิทักษ์วิหารใน Nether และ The End — ล้มได้แล้วรับ Realm Core และของรางวัลประจำมิติ')
    bg = ('radial-gradient(900px 700px at 18% 22%,rgba(255,138,42,.17),transparent 60%),'
          'radial-gradient(900px 700px at 84% 70%,rgba(217,140,255,.17),transparent 60%),#0b0910')
    return page(css, '#ffb35c', bg, header, body)


def pets_poster(pets, part, total):
    css = """
.grid{display:grid;grid-template-columns:repeat(4,1fr);gap:24px}
.pet .stage{padding-bottom:20px;padding-left:58px}
.pet .meta{padding:2px 16px 22px;text-align:center}
.pet .th{font-size:24px;margin-top:0}
.pet .en{font:700 15px "Palatino Linotype",serif;letter-spacing:.2em;margin-top:2px;color:color-mix(in srgb,var(--c) 70%,white);text-shadow:none}
.hab{display:flex;flex-wrap:wrap;justify-content:center;gap:6px;margin-top:12px}
.hab span{font-size:13.5px;line-height:1.5;padding:2px 10px;border-radius:999px;color:var(--h);
  background:color-mix(in srgb,var(--h) 12%,transparent);border:1px solid color-mix(in srgb,var(--h) 45%,transparent)}
.baby{position:absolute;left:12px;bottom:14px;display:flex;flex-direction:column;align-items:center;gap:2px}
.baby img{max-height:62px;max-width:70px;filter:drop-shadow(0 4px 6px rgba(0,0,0,.6))}
.baby span{font-size:12.5px;color:#d9d2ea;background:rgba(8,6,14,.7);padding:0 8px;border-radius:999px}
"""
    body = '<div class="grid">'
    for index, (species, en, th, homes) in pets:
        color = tint(Image.open(RENDERS / f'pet_{species}_adult.png'))
        baby = f'<div class="baby"><img src="{uri(f"pet_{species}_baby")}"><span>ร่างเด็ก</span></div>'
        habitat = ''.join(f'<span style="--h:{HABITATS[h][1]}">{HABITATS[h][0]}</span>' for h in homes)
        body += (f'<article class="card pet" style="--c:{color}">'
                 f'{stage(f"pet_{species}_adult", 290, 262, 240, baby)}'
                 f'<div class="meta"><div class="th">{th}</div><div class="en">{en.upper()}</div><div class="hab">{habitat}</div></div></article>')
    body += '</div>'
    header = head('EVERGARDEN · COMPANIONS', 'สัตว์เลี้ยงแห่ง Evergarden', f'Companion Collection · {part} / {total}',
                  'พบตัวป่าตามภูมิภาคในมิติ Evergarden — ฝึกให้เชื่อง แล้วเลี้ยงจนโตครบ 3 ร่าง')
    bg = ('radial-gradient(900px 600px at 15% 8%,rgba(255,155,207,.15),transparent 60%),'
          'radial-gradient(900px 700px at 88% 40%,rgba(147,220,255,.13),transparent 60%),'
          'radial-gradient(900px 700px at 30% 92%,rgba(147,224,140,.10),transparent 60%),#0b0a12')
    return page(css, '#ffc2e0', bg, header, body)


def capture(name, html):
    WORK.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    source, shot = WORK / f'{name}.html', WORK / f'{name}-raw.png'
    source.write_text(html, encoding='utf8')
    shot.unlink(missing_ok=True)
    subprocess.run([str(EDGE), '--headless=new', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1',
                    f'--user-data-dir={WORK / "edge-profile"}', '--window-size=1600,4200',
                    '--virtual-time-budget=3000', f'--screenshot={shot}', source.as_uri()],
                   check=True, capture_output=True, timeout=180)
    image = Image.open(shot).convert('RGB')
    image = image.crop(ImageChops.difference(image, Image.new('RGB', image.size, OUTSIDE)).getbbox())
    target = OUT / f'{name}.png'
    image.save(target, optimize=True)
    print(target, image.size, flush=True)


def compose():
    capture('bosses-1-world-endgame', endgame_poster())
    capture('bosses-2-six-rivers', rivers_poster())
    capture('bosses-3-realms', realms_poster())
    pets = list(enumerate(pet_catalog(), 1))
    half = (len(pets) + 1) // 2
    capture('pets-1', pets_poster(pets[:half], 'I', 'II'))
    capture('pets-2', pets_poster(pets[half:], 'II', 'II'))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--rerender', action='store_true')
    parser.add_argument('--only', nargs='*', help='render only these cache names (no posters)')
    options = parser.parse_args()
    render_all(options.rerender, set(options.only) if options.only else None)
    if not options.only:
        compose()
