"""Repack Java native pet textures without their empty space; every face keeps its exact pixels.

The Pet Atelier paints each expansion pet into a 1024x1024 texture, but only about a sixth of it holds faces.
Minecraft stitches every item texture into one atlas, and a texture costs its full size there whatever is
transparent: the 171 large pet textures alone took about 179 million atlas pixels, which pushed the Java
items atlas past 16384x16384 and made GPUs with that limit (most AMD, Intel and laptop GPUs) fail to load
the pack (FAILED_RELOAD), while 32768-capable NVIDIA cards loaded it.

`repack` moves each face's pixel rectangle, unchanged, into the smallest canvas from CANVASES that holds them
all and rewrites the UVs of every model of that form to match. Nothing is resampled: each face samples
exactly the same pixels in the same orientation, which `repack` checks face by face before returning.
Canvas sides are powers of two, so UVs (pixels * 16 / side) stay exact binary fractions. Textures no larger
than LIMIT (the 37 original species) are left byte-for-byte as they are.
"""
import io
import json
import zipfile

import numpy as np
from PIL import Image

TEXTURES = 'assets/evergarden/textures/item/pet_native/'
MODELS = 'assets/evergarden/models/pet_native/'
CANVASES = ((512, 512), (1024, 512), (1024, 1024))
LIMIT = 512
DATE = (1980, 1, 1, 0, 0, 0)   # the native exporter's fixed entry date


def _skyline(sizes, width, height, pad):
    """Deterministic bottom-left skyline packing. sizes: [(w, h)]; returns [(x, y)] or None."""
    order = sorted(range(len(sizes)), key=lambda i: (-sizes[i][1], -sizes[i][0], i))
    sky = [(0, 0, width)]                      # (x, y, span) segments, left to right
    spots = [None] * len(sizes)
    for i in order:
        w, h = sizes[i][0] + pad, sizes[i][1] + pad
        best = None
        for start in range(len(sky)):
            x = sky[start][0]
            if x + w > width:
                break
            y, span, k = 0, 0, start
            while span < w and k < len(sky):
                y = max(y, sky[k][1])
                span += sky[k][2]
                k += 1
            if span < w or y + h > height:
                continue
            if best is None or (y, x) < (best[1], best[0]):
                best = (x, y)
        if best is None:
            return None
        x, y = best
        spots[i] = best
        rest = []
        for sx, sy, sw in sky:
            if sx + sw <= x or sx >= x + w:
                rest.append((sx, sy, sw))
                continue
            if sx < x:
                rest.append((sx, sy, x - sx))
            if sx + sw > x + w:
                rest.append((x + w, sy, sx + sw - x - w))
        rest.append((x, y + h, w))
        rest.sort()
        sky = []
        for segment in rest:
            if sky and sky[-1][1] == segment[1] and sky[-1][0] + sky[-1][2] == segment[0]:
                sky[-1] = (sky[-1][0], segment[1], sky[-1][2] + segment[2])
            else:
                sky.append(segment)
    return spots


def _pixels(uv, width, height):
    """UV [u1, v1, u2, v2] in model units (0-16) -> integer pixel corners; raises if off the pixel grid."""
    out = []
    for index, value in enumerate(uv):
        scaled = value * (width if index % 2 == 0 else height) / 16
        if abs(scaled - round(scaled)) > 1e-6:
            raise ValueError(f'UV {uv} is not on the {width}x{height} pixel grid')
        out.append(int(round(scaled)))
    return out


def _crop(image, corners):
    u1, v1, u2, v2 = corners
    return image[min(v1, v2):max(v1, v2), min(u1, u2):max(u1, u2)]


def _encode_png(array):
    buffer = io.BytesIO()
    Image.fromarray(array, 'RGBA').save(buffer, 'PNG', optimize=True)
    return buffer.getvalue()


def _encode_json(value):
    """The native exporter's own format (compact, trailing newline), so untouched models round-trip byte for byte."""
    return (json.dumps(value, ensure_ascii=False, separators=(',', ':')) + '\n').encode('utf8')


def repack(entries):
    """entries: {name: bytes} of native-pets.zip. Returns (replacements, report) for the repacked forms."""
    models = {}
    for name in entries:
        if name.startswith(MODELS) and name.endswith('.json'):
            models.setdefault(name[len(MODELS):].split('/')[0], []).append(name)
    replacements, forms = {}, {}
    for form in sorted(models):
        texture = f'{TEXTURES}{form}_0.png'
        if texture not in entries:
            continue
        source = np.asarray(Image.open(io.BytesIO(entries[texture])).convert('RGBA'))
        height, width = source.shape[:2]
        if max(width, height) <= LIMIT:
            continue
        reference = f'evergarden:item/pet_native/{form}_0'
        parsed = {name: json.loads(entries[name]) for name in sorted(models[form])}
        rects = set()
        for name, model in parsed.items():
            if model.get('textures', {}).get('0') != reference or set(model['textures']) - {'0', 'particle'}:
                raise ValueError(f'{name}: unexpected texture bindings {model.get("textures")}')
            for element in model.get('elements', []):
                for face in element.get('faces', {}).values():
                    if face.get('texture') != '#0':
                        raise ValueError(f'{name}: face uses {face.get("texture")}')
                    u1, v1, u2, v2 = _pixels(face['uv'], width, height)
                    rects.add((min(u1, u2), min(v1, v2), max(u1, u2), max(v1, v2)))
        rects = sorted(rects)
        sizes = [(max(1, r[2] - r[0]), max(1, r[3] - r[1])) for r in rects]
        chosen = None
        for canvas in CANVASES:
            for pad in (1, 0):
                spots = _skyline(sizes, *canvas, pad)
                if spots is not None:
                    chosen = (canvas, pad, spots)
                    break
            if chosen:
                break
        if chosen is None or chosen[0][0] * chosen[0][1] >= width * height:
            continue                               # no smaller canvas holds it: keep the original
        (new_width, new_height), pad, spots = chosen
        target = np.zeros((new_height, new_width, 4), np.uint8)
        moved = {}
        for rect, (x, y) in zip(rects, spots):
            w, h = rect[2] - rect[0], rect[3] - rect[1]
            target[y:y + h, x:x + w] = source[rect[1]:rect[3], rect[0]:rect[2]]
            moved[rect] = (x, y)
        faces = 0
        for name, model in parsed.items():
            for element in model.get('elements', []):
                for face in element.get('faces', {}).values():
                    corners = _pixels(face['uv'], width, height)
                    u1, v1, u2, v2 = corners
                    x, y = moved[(min(u1, u2), min(v1, v2), max(u1, u2), max(v1, v2))]
                    w, h = abs(u2 - u1), abs(v2 - v1)
                    # Keep each corner's side, so mirrored faces stay mirrored.
                    nu1, nu2 = (x, x + w) if u1 <= u2 else (x + w, x)
                    nv1, nv2 = (y, y + h) if v1 <= v2 else (y + h, y)
                    face['uv'] = [nu1 * 16 / new_width, nv1 * 16 / new_height, nu2 * 16 / new_width, nv2 * 16 / new_height]
                    after = _pixels(face['uv'], new_width, new_height)
                    if not np.array_equal(_crop(source, corners), _crop(target, after)) \
                            or (u1 <= u2) != (after[0] <= after[2]) or (v1 <= v2) != (after[1] <= after[3]):
                        raise ValueError(f'{name}: a face does not keep its pixels after repacking')
                    faces += 1
            replacements[name] = _encode_json(model)
        replacements[texture] = _encode_png(target)
        forms[form] = dict(before=[width, height], after=[new_width, new_height], pad=pad,
                           rectangles=len(rects), faces=faces)
    report = dict(forms=len(forms),
                  atlas_pixels_before=sum(f['before'][0] * f['before'][1] for f in forms.values()),
                  atlas_pixels_after=sum(f['after'][0] * f['after'][1] for f in forms.values()),
                  canvases={str(c): sum(1 for f in forms.values() if tuple(f['after']) == c) for c in CANVASES},
                  detail=forms)
    return replacements, report


def repack_zip(data):
    """Repack a whole native-pets.zip. Returns (new zip bytes, report); other entries keep their bytes."""
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        names = [info.filename for info in archive.infolist() if not info.is_dir()]
        entries = {name: archive.read(name) for name in names}
    replacements, report = repack(entries)
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, 'w', zipfile.ZIP_DEFLATED) as archive:
        for name in sorted(entries):
            archive.writestr(zipfile.ZipInfo(name, DATE), replacements.get(name, entries[name]),
                             compress_type=zipfile.ZIP_DEFLATED)
    report['replaced_entries'] = len(replacements)
    return buffer.getvalue(), report
