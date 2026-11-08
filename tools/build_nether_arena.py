#!/usr/bin/env python3
"""Author the Nether hall. Coordinates are relative to its floor, entrance at +Z.

The OWS1 file is the actual worldgen geometry; the preview is a cutaway of those
same blocks, with the ceiling and near wall removed solely for visibility.
"""
from collections import Counter
import gzip
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[1]
ASSET = ROOT / 'evergarden/src/main/resources/dimensional-sanctuaries/nether-arena.ows'
PREVIEW = ROOT / 'previews/dimensional-sanctuaries/nether-arena-cutaway.png'

def build():
    blocks = {}
    def box(x1, y1, z1, x2, y2, z2, material):
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1):
                for y in range(y1, y2 + 1):
                    blocks[x, y, z] = material

    # Foundation is deliberately shallow; runtime handles bounded excavation.
    box(-52, -4, -52, 52, -1, 52, 'blackstone')
    box(-52, 0, -52, 52, 0, 52, 'polished_blackstone_bricks')
    # Distressed but deterministic stone paving, with crimson geometric tracery.
    for x in range(-49, 50):
        for z in range(-49, 50):
            a, b = abs(x), abs(z)
            m = 'polished_blackstone_bricks'
            if (x * 17 + z * 29) % 23 == 0: m = 'cracked_polished_blackstone_bricks'
            # Interlocking stepped square outlines and clipped corners, all flush
            # with the floor: no obstacles inside the future boss court.
            outer = max(a, b) in (36, 37, 47) and a + b <= 72
            inner = max(a, b) in (15, 16) and a + b <= 28
            diagonal = a + b in (46, 47) and 11 <= min(a, b) <= 23
            corner = ((a in (25, 26) and 26 <= b <= 33)
                      or (b in (25, 26) and 26 <= a <= 33)
                      or (a in (32, 33) and 26 <= b <= 33)
                      or (b in (32, 33) and 26 <= a <= 33))
            arm = ((a in (6, 7) and 16 <= b <= 35)
                   or (b in (6, 7) and 16 <= a <= 35))
            if outer or inner or diagonal or corner or arm:
                m = 'red_nether_bricks'
            if (a + b in (43, 44) and 10 <= min(a, b) <= 22) or max(a, b) == 39:
                m = 'polished_blackstone'
            blocks[x, 0, z] = m
    # Light is embedded in the floor, and sealed inside the building; no lava flow.
    for x in (-29, 29):
        for z in (-29, 29):
            for dx, dz in ((-2,-2), (-2,2), (2,-2), (2,2)):
                box(x+dx-1, 0, z+dz-1, x+dx+1, 0, z+dz+1, 'shroomlight')
    # Hall shell: three block thick, secure entrance, solid roof.
    box(-52, 1, -52, -50, 40, 52, 'polished_blackstone_bricks')
    box(50, 1, -52, 52, 40, 52, 'polished_blackstone_bricks')
    box(-49, 1, -52, 49, 40, -50, 'polished_blackstone_bricks')
    box(-49, 1, 50, -9, 40, 52, 'polished_blackstone_bricks')
    box(9, 1, 50, 49, 40, 52, 'polished_blackstone_bricks')
    box(-8, 17, 50, 8, 40, 52, 'polished_blackstone_bricks')
    box(-52, 41, -52, 52, 43, 52, 'blackstone')
    # Side buttresses and tall basalt pillars define the cavernous interior.
    for x in (-44, 44):
        for z in (-39, -13, 13, 39):
            box(x-3, 1, z-3, x+3, 3, z+3, 'polished_blackstone')
            box(x-2, 4, z-2, x+2, 35, z+2, 'polished_basalt[axis=y]')
            box(x-3, 36, z-3, x+3, 39, z+3, 'polished_blackstone')
            box(x-4, 40, z-4, x+4, 40, z+4, 'chiseled_polished_blackstone')
            for y in (4, 34):
                box(x-3, y, z-3, x+3, y, z+3, 'polished_blackstone_bricks')
    # Overhead beams are flush with roof, preserving a high, open battle volume.
    for z in (-39, -13, 13, 39):
        box(-44, 39, z-1, 44, 40, z+1, 'polished_blackstone')
    # Layered lower walls and ledges, followed by an upper arcade. All of this
    # is confined to the perimeter outside the 71 x 71 clear central court.
    for side in (-1, 1):
        x0, x1 = sorted((side*47, side*49))
        box(x0, 1, -48, x1, 5, 48, 'blackstone')
        box(x0, 6, -48, x1, 7, 48, 'polished_blackstone_bricks')
        box(x0, 8, -48, x1, 8, 48, 'chiseled_polished_blackstone')
        box(x0, 26, -48, x1, 27, 48, 'polished_blackstone')
        box(x0, 32, -48, x1, 33, 48, 'polished_blackstone_bricks')
        for z in (-26, 0, 26):
            # The back of each niche sits one block behind its stone arch.
            back, frame = side*49, side*48
            box(back, 11, z-6, back, 23, z+6, 'blackstone')
            box(back, 12, z-5, back, 20, z+5, 'dark_prismarine')
            box(back, 21, z-4, back, 22, z+4, 'dark_prismarine')
            box(back, 23, z-2, back, 23, z+2, 'dark_prismarine')
            for dz in (-6, 6):
                box(frame, 9, z+dz, frame, 20, z+dz, 'polished_blackstone')
            for dy, reach in ((21,5), (22,4), (23,3), (24,2)):
                for dz in (-reach, reach):
                    blocks[frame, dy, z+dz] = 'chiseled_polished_blackstone'
            box(frame, 25, z-1, frame, 25, z+1, 'chiseled_polished_blackstone')
            for dz in (-3, 3):
                blocks[back, 15, z+dz] = 'soul_lantern[hanging=false]'
                blocks[back, 30, z+dz] = 'soul_lantern[hanging=false]'
            # Repeating narrow uprights create a shadowed upper gallery.
            for dz in (-5, 0, 5):
                box(frame, 28, z+dz, frame, 31, z+dz, 'chiseled_polished_blackstone')
        for z in (-47, 47):
            box(side*49, 12, z, side*49, 23, z, 'shroomlight')
    # Rear altar wall carries the same layered masonry and a warm central well.
    box(-48, 1, -49, 48, 5, -47, 'blackstone')
    box(-48, 6, -49, 48, 7, -47, 'polished_blackstone_bricks')
    box(-48, 8, -49, 48, 8, -47, 'chiseled_polished_blackstone')
    box(-48, 26, -49, 48, 27, -47, 'polished_blackstone')
    box(-48, 32, -49, 48, 33, -47, 'polished_blackstone_bricks')
    for x in (-38, -25, -12, 12, 25, 38):
        box(x-4, 10, -49, x+4, 23, -49, 'dark_prismarine')
        for dx in (-5, 5):
            box(x+dx, 9, -48, x+dx, 20, -48, 'polished_blackstone')
        for dy, reach in ((21,4), (22,3), (23,2)):
            for dx in (-reach, reach):
                blocks[x+dx, dy, -48] = 'chiseled_polished_blackstone'
        box(x-1, 24, -48, x+1, 24, -48, 'chiseled_polished_blackstone')
        for dx in (-2, 2):
            blocks[x+dx, 15, -49] = 'soul_lantern[hanging=false]'
        for dx in (-4, 0, 4):
            box(x+dx, 28, -48, x+dx, 31, -48, 'chiseled_polished_blackstone')
    box(-6, 10, -49, 6, 25, -49, 'polished_blackstone')
    for x in (-4, 0, 4):
        box(x, 12, -48, x, 23, -48, 'shroomlight')
    box(-7, 25, -48, 7, 25, -48, 'red_nether_bricks')
    # Entrance frame and short approach occupy the perimeter, keeping the court clear.
    for x in (-10, 10):
        box(x-1, 1, 48, x+1, 20, 52, 'polished_blackstone')
    box(-11, 18, 48, 11, 21, 52, 'polished_blackstone')
    for x in (-10, 10):
        box(x-1, 12, 47, x+1, 15, 47, 'shroomlight')
    return blocks

def write(blocks):
    palette = sorted(set(blocks.values()))
    indices = {state: i for i, state in enumerate(palette)}
    ASSET.parent.mkdir(parents=True, exist_ok=True)
    # mtime=0 makes the authored artifact byte-for-byte reproducible.
    with ASSET.open('wb') as raw, gzip.GzipFile(filename='', mode='wb', fileobj=raw, compresslevel=9, mtime=0) as f:
        f.write(b'OWS1')
        f.write(struct.pack('>H', len(palette)))
        for state in palette:
            encoded = ('minecraft:' + state).encode('ascii')
            f.write(struct.pack('>H', len(encoded)))
            f.write(encoded)
        f.write(struct.pack('>I', len(blocks)))
        for (x, y, z), state in sorted(blocks.items()):
            f.write(struct.pack('>hhhH', x, y, z, indices[state]))
        f.write(struct.pack('>H', 0))
    print(f'{ASSET}: {len(blocks):,} blocks; {len(palette)} states; 105 x 48 x 105')
    print(dict(Counter(blocks.values())))

def preview(blocks):
    from preview_sky_whale import Renderer, Textures, PALETTE
    PALETTE.update({'BLACKSTONE': (44, 40, 49), 'POLISHED_BLACKSTONE': (55, 49, 56),
                    'POLISHED_BLACKSTONE_BRICKS': (48, 44, 50), 'CRACKED_POLISHED_BLACKSTONE_BRICKS': (54, 47, 50),
                    'CHISELED_POLISHED_BLACKSTONE': (66, 58, 64), 'POLISHED_BASALT': (79, 76, 79),
                    'RED_NETHER_BRICKS': (104, 32, 38), 'SOUL_LANTERN': (81, 209, 214)})
    # Cut away the roof and entry wall so the camera can see the real court.
    visible = {p: state.split('[')[0].upper() for p, state in blocks.items()
               if p[1] < 39 and not (p[2] >= 47 and p[1] > 0)}
    minecraft_jar = Path.home() / 'AppData/Roaming/.minecraft/versions/26.3/26.3.jar'
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    renderer = Renderer(visible, Textures(minecraft_jar if minecraft_jar.exists() else None))
    renderer.render(
        PREVIEW, 'NETHER / FORTRESS HALL', 'Actual authored blocks | roof and near wall cut away | no boss',
        width=1440, height=1000, azimuth=25, elevation=30, shadows=False)
    print('Preview:', PREVIEW)
    perspective = PREVIEW.with_name('nether-arena-front-cutaway.png')
    renderer.render(
        perspective, 'NETHER / FORTRESS HALL', 'Actual authored blocks | roof and near wall cut away | no boss',
        width=1440, height=900, azimuth=8, elevation=12, focus=(0, 14, 0), span=120, shadows=False)
    print('Preview:', perspective)

if __name__ == '__main__':
    geometry = build()
    assert all(-52 <= x <= 52 and -4 <= y <= 43 and -52 <= z <= 52 for x, y, z in geometry)
    assert all((x, y, z) not in geometry for x in range(-35, 36) for z in range(-35, 36) for y in range(1, 39))
    write(geometry)
    preview(geometry)
