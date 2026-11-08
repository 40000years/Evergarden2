"""Validate a Bedrock readability candidate against its exact input mcpack.

Usage: python tests/model-engine/check_end_bedrock_readability.py BEFORE AFTER
This checks asset preservation and color transformation, not client rendering.
"""
import io
import json
import sys
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from brighten_end_boss_bedrock import GAMMA, brighten_png  # noqa: E402


def rgba(data):
    with Image.open(io.BytesIO(data)) as image:
        return np.array(image.convert('RGBA'))


def files(path):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        assert archive.testzip() is None and len(names) == len(set(names)), 'invalid pack'
        return {n: archive.read(n) for n in names if not n.endswith('/')}


def check(before, after):
    old, new = files(before), files(after)
    assert old.keys() == new.keys(), 'pack entries differ'
    names = {f'textures/entity/{name}_native.png' for name in GAMMA}
    assert {n for n in old if old[n] != new[n]} == names | {'manifest.json'}, 'unexpected changes'
    prior, current = json.loads(old['manifest.json']), json.loads(new['manifest.json'])
    assert current['header']['uuid'] == prior['header']['uuid'], 'pack identity changed'
    version = prior['header']['version']
    assert current['header']['version'] == [version[0], version[1] + 1, 0], 'pack version not bumped'
    assert all(module['version'] == current['header']['version'] for module in current['modules'])
    for name, gamma in GAMMA.items():
        entry = f'textures/entity/{name}_native.png'
        source, result = rgba(old[entry]), rgba(new[entry])
        assert source.shape == result.shape, f'{name}: atlas size changed'
        assert np.array_equal(source[:, :, 3], result[:, :, 3]), f'{name}: alpha changed'
        assert np.array_equal(source[source[:, :, 3] == 0], result[result[:, :, 3] == 0]), f'{name}: transparent pixels changed'
        assert np.array_equal(result, rgba(brighten_png(old[entry], gamma))), f'{name}: unexpected colors'
        assert np.all(result[:, :, :3] >= source[:, :, :3]) and np.any(result[:, :, :3] > source[:, :, :3]), f'{name}: not brightened'
    print('PASS pack identity, version, exact two texture adjustments, transparency and unrelated entries')


if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    check(sys.argv[1], sys.argv[2])
