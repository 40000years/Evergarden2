"""Render the live probe's boss block snapshot on a crop of the actual temple floor."""
import argparse
import csv
from pathlib import Path
from preview_landmarks import LandmarkTextures
from preview_sky_whale import Renderer, PALETTE

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('body',type=Path)
p.add_argument('temple',type=Path)
p.add_argument('--output',type=Path,default=Path('previews/ancient-judge.png'))
args=p.parse_args()
PALETTE.update({'CHISELED_STONE_BRICKS':(116,122,119),'SMOOTH_STONE':(163,163,156),
    'WAXED_OXIDIZED_COPPER':(70,137,120),'POLISHED_TUFF':(117,119,107),
    'AMETHYST_BLOCK':(159,106,205),'CRYING_OBSIDIAN':(57,31,86),'EMERALD_BLOCK':(38,188,99)})
blocks={}
with args.temple.open() as source:
    for x,y,z,material in csv.reader(source):
        x,y,z=int(x),int(y),int(z)
        if y==100 and x*x+z*z<=36*36:blocks[x,y,z]=material
with args.body.open() as source:
    for x,y,z,material in csv.reader(source):blocks[int(x),int(y),int(z)]=material
args.output.parent.mkdir(parents=True,exist_ok=True)
Renderer(blocks,LandmarkTextures(None)).render(args.output,'THE BODILESS JUDGE',
    'Live boss block snapshot on a cropped temple floor. Simulated lighting; not an in-game screenshot.',
    width=1800,height=1300,azimuth=20,elevation=18,shadows=False)
