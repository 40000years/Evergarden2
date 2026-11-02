"""Generate a Bedrock-only readability candidate for the End realm bosses.

The input and output are mcpack files. Only the two End boss texture entries and
pack version are changed. The source blueprints and Java textures are untouched.
This is an offline color adjustment; in-game appearance requires client testing.
"""
import argparse
import io
import json
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image

GAMMA = {'crystal_moth': .8, 'unlit_sun': .6}


def brighten_png(data, gamma):
    """Lift dark values while retaining hue, alpha, UV layout and highlights."""
    with Image.open(io.BytesIO(data)) as image:
        pixels = np.array(image.convert('RGBA'))
    rgb = pixels[:, :, :3].astype(np.float32)
    value = rgb.max(axis=2, keepdims=True)
    adjusted = 255.0 * np.power(value / 255.0, gamma)
    lifted = np.rint(np.clip(rgb * adjusted / np.maximum(value, 1.0), 0, 255)).astype(np.uint8)
    # Leave fully transparent pixels alone, including their hidden RGB values.
    visible = pixels[:, :, 3] > 0
    pixels[visible, :3] = lifted[visible]
    result = io.BytesIO()
    Image.fromarray(pixels, 'RGBA').save(result, format='PNG', optimize=True)
    return result.getvalue()


def update_files(files):
    """Return a new archive mapping; callers must distribute a versioned pack."""
    output = dict(files)
    for name, gamma in GAMMA.items():
        entry = f'textures/entity/{name}_native.png'
        if entry not in output:
            raise ValueError(f'Missing required Bedrock texture: {entry}')
        output[entry] = brighten_png(output[entry], gamma)
    manifest = json.loads(output['manifest.json'])
    current = manifest['header']['version']
    version = [current[0], current[1] + 1, 0]
    manifest['header']['version'] = version
    for module in manifest['modules']:
        module['version'] = version
    output['manifest.json'] = (json.dumps(manifest, ensure_ascii=False, separators=(',', ':')) + '\n').encode('utf8')
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('candidate', type=Path)
    args = parser.parse_args()
    if args.source.resolve() == args.candidate.resolve():
        parser.error('candidate must be different from source')
    with zipfile.ZipFile(args.source) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError('duplicate entries in input pack')
        files = {n: archive.read(n) for n in names if not n.endswith('/')}
    output = update_files(files)
    args.candidate.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.candidate, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(output.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 29, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
    print(f'Wrote {args.candidate}; compare in a Bedrock client before release.')


if __name__ == '__main__':
    main()
