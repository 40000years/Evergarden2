"""Preview authored resource geometry, with presentation lighting only."""
from pathlib import Path
from math import floor, ceil
from restoration_assets import geometry, PALETTES
from preview_sky_whale import Renderer, Textures, PALETTE

out=Path('docs/restoration-resource-previews');out.mkdir(parents=True,exist_ok=True)
for kind,theme,title in [('wand','whale','WAND OF RESTORATION'),('core','whale','CORE OF RESTORATION'),
                        ('altar_whale','whale','CRYSTAL HEART'),('altar_garden','garden','LIVING SANCTUARY'),
                        ('altar_observatory','observatory','ASTRAL RESTORATION')]:
    for i,color in enumerate(PALETTES[theme]):PALETTE[f'RESTORATION_{i}']=tuple(bytes.fromhex(color))
    voxels={}
    for start,end,color in geometry(kind):
        for x in range(floor(start[0]*2),ceil(end[0]*2)):
            for y in range(floor(start[1]*2),ceil(end[1]*2)):
                for z in range(floor(start[2]*2),ceil(end[2]*2)):
                    voxels[x,y,z]=f'RESTORATION_{color}'
    Renderer(voxels,Textures(None)).render(out/f'{kind}.png',title,
        'Resource model preview; voxelized at half-pixel resolution. Not a game screenshot.',
        width=1050,height=1050,azimuth=160 if kind=='wand' else 35,elevation=17 if kind=='wand' else 22,shadows=False)
