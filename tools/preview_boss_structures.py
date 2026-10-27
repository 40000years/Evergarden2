"""Render the exported boss structures from their actual block geometry."""
import argparse
import csv
import json
from pathlib import Path
from PIL import Image, ImageDraw
from preview_landmarks import LandmarkTextures
from preview_sky_whale import Renderer, PALETTE, font

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('exports',type=Path)
parser.add_argument('--output-dir',type=Path,default=Path('previews/boss-structures'))
parser.add_argument('--minecraft-jar',type=Path)
parser.add_argument('--width',type=int,default=1600)
parser.add_argument('--only',choices=('arkhaia-castle','seraphon-valkyrie','seraphon-sanctum'))
parser.add_argument('--no-overview',action='store_true',help='Render one independent view without changing the combined overview.')
parser.add_argument('--assemble-only',action='store_true',help='Assemble completed independent renders without rerendering geometry.')
args=parser.parse_args();args.output_dir.mkdir(parents=True,exist_ok=True)
PALETTE.update({'DEEPSLATE_BRICKS':(64,65,76),'POLISHED_DEEPSLATE':(74,76,86),
    'POLISHED_BLACKSTONE_BRICKS':(48,43,51),'POLISHED_BLACKSTONE':(55,49,59),
    'BLACKSTONE':(45,40,49),'CRIMSON_PLANKS':(117,49,73),'CRIMSON_NYLIUM':(136,38,60),
    'RED_GLAZED_TERRACOTTA':(143,38,48),'GOLD_BLOCK':(239,189,48),
    'IRON_BLOCK':(219,223,226),'QUARTZ_BRICKS':(224,220,211),
    'CHISELED_QUARTZ_BLOCK':(220,215,204),'LIGHT_BLUE_STAINED_GLASS':(124,186,214),
    'PURPLE_STAINED_GLASS':(139,67,161),'BLUE_STAINED_GLASS':(66,83,175),
    'BLUE_GLAZED_TERRACOTTA':(80,103,171),'MAGMA_BLOCK':(161,63,27),
    'BOOKSHELF':(118,84,46),'COBWEB':(227,225,223),'SOUL_LANTERN':(93,212,218),
    'POLISHED_BLACKSTONE_PRESSURE_PLATE':(52,46,57),'ROSE_BUSH':(148,47,65),
    'RED_NETHER_BRICKS':(73,19,30),'RED_STAINED_GLASS':(152,42,54),
    'CRYING_OBSIDIAN':(52,26,75),'OBSIDIAN':(32,24,46),'BASALT':(69,69,76),
    'POLISHED_DIORITE':(204,206,201),'QUARTZ_PILLAR':(226,222,211),
    'WAXED_CUT_COPPER':(178,101,72),'WAXED_EXPOSED_COPPER':(163,110,79),
    'WAXED_OXIDIZED_COPPER':(75,144,124),'TUFF_BRICKS':(102,105,97)})
names=[('arkhaia-castle','ARKHAIA / DREADROSE CASTLE',209,85),
       ('seraphon-valkyrie','SERAPHON VALKYRIE / TEMPLE OF BLADES',193,89),
       ('seraphon-sanctum','SERAPHON / SUPREME SANCTUARY',241,117)]
manifest=[]
views={'arkhaia-castle':(28,32),'seraphon-valkyrie':(23,32),'seraphon-sanctum':(30,35)}
for name,title,width,arena in names:
    if args.assemble_only:
        manifest.append(json.loads((args.output_dir/(name+'-manifest.json')).read_text()))
        continue
    if args.only and args.only!=name:
        continue
    with (args.exports/(name+'.csv')).open() as source:
        blocks={(int(x),int(y),int(z)):material for x,y,z,material in csv.reader(source)}
    info=json.loads((args.exports/(name+'-counts.json')).read_text())
    rendered=Renderer(blocks,LandmarkTextures(args.minecraft_jar)).render(
        args.output_dir/(name+'.png'),title,
        f'Architecture redesign | {width} x {width} footprint | Arena: {arena} blocks',
        width=args.width,height=round(args.width*1.02),azimuth=views[name][0],elevation=views[name][1],shadows=False)
    entry={'id':name,'geometry_revision':2,**info,**rendered}
    manifest.append(entry)
    (args.output_dir/(name+'-manifest.json')).write_text(json.dumps(entry,indent=2),encoding='utf8')
    del blocks
if args.no_overview:raise SystemExit(0)
if args.only:
    manifest=[json.loads((args.output_dir/(name+'-manifest.json')).read_text()) for name,*_ in names]
thumb=760;canvas=Image.new('RGB',(thumb*3,round(thumb*1.02)+110),(235,230,222));draw=ImageDraw.Draw(canvas)
draw.text((30,22),'EVERGARDEN / THREE BOSS STRUCTURES',font=font(29,True),fill=(43,61,85))
draw.text((30,63),'Actual generated block geometry. Lighting preview, not an in-game screenshot.',font=font(15),fill=(64,81,103))
for i,(name,*_) in enumerate(names):
    with Image.open(args.output_dir/(name+'.png')) as im:
        canvas.paste(im.resize((thumb,round(thumb*1.02)),Image.Resampling.LANCZOS),(i*thumb,110))
canvas.save(args.output_dir/'overview.png')
(args.output_dir/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf8')
