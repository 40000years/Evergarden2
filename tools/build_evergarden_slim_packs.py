"""Slim the Evergarden resource packs without changing anything players see (Bedrock 3.55.0).

* Palette-compresses PNGs with libimagequant, keeping a file only if its alpha-weighted colour RMSE
  is at most 4/255 and mean alpha error at most 2/255 (otherwise lossless, or untouched).
* Identical inputs give identical outputs, so textures shared by Java and Bedrock stay identical.
* Any PNG whose bytes equal authored source art (evergarden/art, art, advance-magic/art) or a
  texture embedded in a shipped ModelEngine blueprint stays byte-for-byte untouched, because the
  boss, pet and weapon checks pin those copies to their sources.
* Drops the mythical five's menu portraits from pet-portraits.zip; they have no models until the
  legendary tier opens.

Needs the imagequant module (pip install imagequant).

    python tools/build_evergarden_slim_packs.py [--dry-run]
"""
import argparse
import base64
import hashlib
import io
import json
import os
import re
import shutil
import zipfile
from pathlib import Path

import imagequant
import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / 'evergarden/dist'
RESOURCES = ROOT / 'evergarden/src/main/resources'
OUT = ROOT / 'output/evergarden-slim-20261025'
BASE_SHA1 = {
    'evergarden-java.zip': 'c82f05b7460481d0323626f04d9a64b9a07c2f99',
    'evergarden-bedrock.mcpack': '5c0b4e1048555625857d4e236f549c0448345b5b',
    'judge-java.zip': 'dac26fde701cbe540e093525af14ef24cfa87118',
}
PORTRAITS_SHA1 = None  # checked against the backup copy taken before this build
VERSION = [3, 55, 0]
MYTHICAL = ('aetherius', 'ignis_draconis', 'zephyros', 'solaris', 'fenrir')
MAX_RMSE, MAX_ALPHA = 4.0, 2.0
ART_ROOTS = (ROOT / 'evergarden/art', ROOT / 'art', ROOT / 'advance-magic/art')


def pinned_sources():
    """SHA-1s of every authored source PNG and every texture embedded in a shipped blueprint."""
    pinned = set()
    for root in ART_ROOTS:
        if root.exists():
            for path in root.rglob('*.png'):
                pinned.add(hashlib.sha1(path.read_bytes()).hexdigest())
    for path in (RESOURCES / 'modelengine').rglob('*.bbmodel'):
        for texture in json.loads(path.read_text(encoding='utf8')).get('textures', []):
            source = texture.get('source', '')
            if source.startswith('data:image/png;base64,'):
                pinned.add(hashlib.sha1(base64.b64decode(source.split(',', 1)[1])).hexdigest())
    return pinned


_cache = {}


def compress(data, pinned, stats):
    key = hashlib.sha1(data).hexdigest()
    if key in pinned:
        stats['pinned'] += 1
        return data
    if key not in _cache:
        rgba = Image.open(io.BytesIO(data)).convert('RGBA')
        best, method = data, 'kept'
        lossless = io.BytesIO()
        rgba.save(lossless, 'PNG', optimize=True)
        if lossless.tell() < len(best):
            best, method = lossless.getvalue(), 'lossless'
        try:
            quantized = imagequant.quantize_pil_image(rgba, dithering_level=1.0, max_colors=256, min_quality=0, max_quality=95)
            a = np.asarray(rgba).astype(np.float64)
            c = np.asarray(quantized.convert('RGBA')).astype(np.float64)
            weight = a[..., 3:] / 255
            rmse = float(np.sqrt((((a[..., :3] - c[..., :3]) * weight) ** 2).mean()))
            alpha = float(np.abs(a[..., 3] - c[..., 3]).mean())
            buffer = io.BytesIO()
            quantized.save(buffer, 'PNG', optimize=True)
            if rmse <= MAX_RMSE and alpha <= MAX_ALPHA and buffer.tell() < len(best):
                best, method = buffer.getvalue(), 'palette'
        except RuntimeError:
            pass
        _cache[key] = (best, method)
    best, method = _cache[key]
    stats[method] += 1
    return best


def source(name):
    """The pre-slim originals: the backup once this build has replaced them."""
    backup = OUT / 'before' / ('resources' if name == 'pet-portraits.zip' else 'evergarden-dist') / name
    return backup if backup.exists() else (RESOURCES if name == 'pet-portraits.zip' else DIST) / name


def read(path):
    with zipfile.ZipFile(path) as z:
        return [(i, z.read(i.filename)) for i in z.infolist() if not i.is_dir()]


def write(path, entries, fixed_date=None):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as z:
        for info, data in entries:
            out = zipfile.ZipInfo(info.filename, fixed_date or info.date_time)
            out.compress_type = zipfile.ZIP_DEFLATED
            out.external_attr = info.external_attr
            z.writestr(out, data, compresslevel=9)
    with zipfile.ZipFile(path) as z:
        if z.testzip() is not None or [n for n in z.namelist()] != [i.filename for i, _ in entries]:
            raise RuntimeError('Invalid staged pack ' + path.name)
        for info, data in entries:
            if z.read(info.filename) != data:
                raise RuntimeError(f'{path.name}: {info.filename} differs after writing')


def validate(entries, bedrock):
    names = {i.filename for i, _ in entries}
    for info, data in entries:
        if info.filename.endswith('.json'):
            json.loads(data)
        if info.filename.endswith('.png'):
            Image.open(io.BytesIO(data)).verify()
    if bedrock:
        for info, data in entries:
            if info.filename.startswith('particles/'):
                texture = json.loads(data)['particle_effect']['description']['basic_render_parameters']['texture']
                if texture + '.png' not in names:
                    raise RuntimeError(f'{info.filename} needs missing {texture}.png')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dry-run', action='store_true', help='Stage under output/ without touching evergarden/')
    args = parser.parse_args()
    pinned = pinned_sources()
    report = {'bedrock_version': VERSION, 'thresholds': {'rmse': MAX_RMSE, 'alpha': MAX_ALPHA}, 'pinned_sources': len(pinned), 'files': {}}
    staged = {}
    for name, digest in BASE_SHA1.items():
        path = source(name)
        if hashlib.sha1(path.read_bytes()).hexdigest() != digest:
            raise RuntimeError(f'{name} is not the e2.128 pack this build expects')
        stats = {'palette': 0, 'lossless': 0, 'kept': 0, 'pinned': 0}
        entries = []
        for info, data in read(path):
            if info.filename.endswith('.png'):
                data = compress(data, pinned, stats)
            elif name.endswith('.mcpack') and info.filename == 'manifest.json':
                manifest = json.loads(data)
                manifest['header']['version'] = VERSION
                for module in manifest['modules']:
                    module['version'] = VERSION
                data = (json.dumps(manifest, ensure_ascii=False, separators=(',', ':')) + '\n').encode()
            entries.append((info, data))
        validate(entries, name.endswith('.mcpack'))
        staged[name] = OUT / 'packs' / name
        write(staged[name], entries)
        report['files'][name] = {'before': path.stat().st_size, 'after': staged[name].stat().st_size, 'png': stats}
    # Pet portraits: the obtainable species only, same deterministic layout as pet_portraits.py.
    path = source('pet-portraits.zip')
    stats = {'palette': 0, 'lossless': 0, 'kept': 0, 'pinned': 0}
    mythical = re.compile(r'pet_(' + '|'.join(MYTHICAL) + r')_')
    removed = []
    entries = []
    for info, data in read(path):
        if mythical.search(info.filename):
            removed.append(info.filename)
            continue
        entries.append((info, compress(data, pinned, stats) if info.filename.endswith('.png') else data))
    validate(entries, False)
    staged['pet-portraits.zip'] = OUT / 'packs/pet-portraits.zip'
    write(staged['pet-portraits.zip'], entries, (1980, 1, 1, 0, 0, 0))
    report['files']['pet-portraits.zip'] = {'before': path.stat().st_size, 'after': staged['pet-portraits.zip'].stat().st_size,
                                            'png': stats, 'removed_mythical_entries': len(removed)}
    design_path = OUT / 'before/resources/judge-design.json' if (OUT / 'before/resources/judge-design.json').exists() else RESOURCES / 'judge-design.json'
    design_raw = design_path.read_text(encoding='utf8')
    design = json.loads(design_raw)
    if design['bedrock-version'] != '3.54.0':
        raise RuntimeError('judge-design.json is not at Bedrock 3.54.0')
    design_bytes = design_raw.replace('"bedrock-version": "3.54.0"', '"bedrock-version": "%d.%d.%d"' % tuple(VERSION)).encode('utf8') \
        if '"bedrock-version": "3.54.0"' in design_raw else None
    if design_bytes is None:
        design['bedrock-version'] = '%d.%d.%d' % tuple(VERSION)
        design_bytes = (json.dumps(design, indent=2) + '\n').encode('utf8')
    (OUT / 'packs/judge-design.json').write_bytes(design_bytes)
    hashes = {name: hashlib.sha1(staged[name].read_bytes()).hexdigest() for name in BASE_SHA1}
    # Same CRLF layout as the e2.128 file.
    hashes_bytes = (json.dumps(hashes, indent=2) + '\n').replace('\n', '\r\n').encode()
    (OUT / 'packs/pack-hashes.json').write_bytes(hashes_bytes)
    report['sha1'] = hashes
    if not args.dry_run:
        for name in BASE_SHA1:
            shutil.copy2(staged[name], DIST / (name + '.staged'))
            os.replace(DIST / (name + '.staged'), DIST / name)
        shutil.copy2(staged['pet-portraits.zip'], RESOURCES / 'pet-portraits.zip.staged')
        os.replace(RESOURCES / 'pet-portraits.zip.staged', RESOURCES / 'pet-portraits.zip')
        (DIST / 'pack-hashes.json').write_bytes(hashes_bytes)
        (RESOURCES / 'judge-design.json').write_bytes(design_bytes)
    report['copied'] = not args.dry_run
    (OUT / 'pack-build.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
