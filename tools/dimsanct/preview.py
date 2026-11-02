"""Build a sanctuary grid and render preview images from the exact asset array."""
from __future__ import annotations

import importlib
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))

from dimsanct import render as R  # noqa: E402


def main(argv):
    name = argv[1]
    views = argv[2:] or ['overview']
    mod = importlib.import_module(f'dimsanct.{name}')
    t = time.time()
    g = mod.build()
    print('built', g.a.shape, f'{time.time() - t:.1f}s', 'palette', len(g.palette))
    textures = R.Textures()
    out = ROOT / 'output' / 'dimsanct-preview'
    for v in views:
        cfg = mod.VIEWS[v]
        t = time.time()
        R.render(g.a, g.palette, (g.x0, g.y0, g.z0), out / f'{name}-{v}.png', textures=textures, **cfg)
        print('rendered', v, f'{time.time() - t:.1f}s')


if __name__ == '__main__':
    main(sys.argv)
