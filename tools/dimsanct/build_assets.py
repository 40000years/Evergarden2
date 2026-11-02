#!/usr/bin/env python3
"""Author, check and render every dimensional sanctuary asset.

  python tools/dimsanct/build_assets.py [crown_fortress|crystal_city ...]

Writes evergarden/src/main/resources/dimensional-sanctuaries/v1/<id>.ods and the preview
renders under previews/dimensional-sanctuaries/. Nothing is copied to dist/ or a server.
"""
from __future__ import annotations

import importlib
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))

from dimsanct import checks, render as R  # noqa: E402

MODULES = {
    'crown_fortress': 'nether_crown',
    'bone_temple': 'nether_bone',
    'crystal_city': 'end_city',
    'void_ring': 'end_ring',
}
ASSETS = ROOT / 'evergarden/src/main/resources/dimensional-sanctuaries/v1'
PREVIEWS = ROOT / 'previews/dimensional-sanctuaries'


def main(argv):
    wanted = [a for a in argv[1:] if not a.startswith('--')] or list(MODULES)
    textures = R.Textures()
    for kind in wanted:
        mod = importlib.import_module('dimsanct.' + MODULES[kind])
        t = time.time()
        g = mod.build()
        meta = dict(mod.META)
        meta['id'] = kind
        meta['version'] = 1
        stats = checks.check(g, meta, kind)
        palette, arr = g.write(ASSETS / f'{kind}.ods', meta)
        print(f'{kind}: {g.a.shape} palette={len(palette)} {stats} built in {time.time() - t:.1f}s, '
              f'{(ASSETS / (kind + ".ods")).stat().st_size:,} bytes')
        if '--no-render' in argv:
            continue
        for view, cfg in mod.VIEWS.items():
            t = time.time()
            R.render(g.a, g.palette, (g.x0, g.y0, g.z0), PREVIEWS / f'{kind}-{view}.png', textures=textures, **cfg)
            print('  rendered', view, f'{time.time() - t:.1f}s')


if __name__ == '__main__':
    main([a for a in sys.argv if not a.startswith('--')] + [a for a in sys.argv if a.startswith('--')])
