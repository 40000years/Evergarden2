"""Render the exported, actual world boss temple blocks; no invented geometry."""
import argparse
import csv
from pathlib import Path
from preview_landmarks import LandmarkTextures
from preview_sky_whale import Renderer,PALETTE

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('export',type=Path)
parser.add_argument('--output',type=Path,default=Path('previews/world-boss-temple.png'))
parser.add_argument('--minecraft-jar',type=Path)
args=parser.parse_args()
PALETTE.update({'STONE_BRICKS':(129,132,129),'CRACKED_STONE_BRICKS':(115,118,113),
    'MOSSY_STONE_BRICKS':(107,124,92),'CHISELED_STONE_BRICKS':(116,122,119),
    'SMOOTH_STONE':(163,163,156),'POLISHED_ANDESITE':(137,140,136),
    'WAXED_OXIDIZED_COPPER':(70,137,120),'CHISELED_QUARTZ_BLOCK':(220,218,202),
    'TUFF_BRICKS':(105,111,104),'POLISHED_TUFF':(117,119,107)})
with args.export.open() as source:
    blocks={(int(x),int(y),int(z)):material for x,y,z,material in csv.reader(source)}
args.output.parent.mkdir(parents=True,exist_ok=True)
Renderer(blocks,LandmarkTextures(args.minecraft_jar)).render(args.output,'THE ANCIENT WORLD BOSS TEMPLE',
    '241 x 241 blocks | Open arena: 161 blocks | Actual block geometry; simulated lighting.',
    width=2000,height=1600,azimuth=35,elevation=31,shadows=False)
