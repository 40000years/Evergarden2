"""Review actual furnished blocks in architectural cutaways; exterior remains intact in-game."""
import argparse
import csv
import json
from pathlib import Path
from PIL import Image, ImageDraw
from preview_landmarks import LandmarkTextures
from preview_sky_whale import Renderer, PALETTE, font

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('exports',type=Path)
p.add_argument('--output-dir',type=Path,default=Path('previews/boss-interiors'))
p.add_argument('--minecraft-jar',type=Path)
p.add_argument('--width',type=int,default=1600)
args=p.parse_args();args.output_dir.mkdir(parents=True,exist_ok=True)
PALETTE.update({'DEEPSLATE_TILES':(59,61,69),'POLISHED_DEEPSLATE':(66,68,74),
    'DEEPSLATE_BRICKS':(67,69,75),'POLISHED_BLACKSTONE_BRICKS':(48,43,51),
    'POLISHED_BLACKSTONE':(55,49,59),'POLISHED_BLACKSTONE_WALL':(55,49,59),
    'CHISELED_POLISHED_BLACKSTONE':(57,50,62),'RED_NETHER_BRICKS':(73,19,30),
    'RED_CARPET':(171,42,45),'RED_CANDLE':(161,37,43),'WHITE_CANDLE':(231,222,203),
    'RED_STAINED_GLASS':(154,38,49),'CRIMSON_PLANKS':(120,47,69),
    'IRON_CHAIN':(57,58,62),'IRON_BLOCK':(215,218,220),'QUARTZ_PILLAR':(229,223,210),
    'GOLD_BLOCK':(239,189,48),'CHISELED_QUARTZ_BLOCK':(220,215,204),
    'QUARTZ_BRICKS':(224,220,211),'SMOOTH_QUARTZ_SLAB':(225,222,210),
    'SMOOTH_QUARTZ_STAIRS':(225,222,210),'LIGHT_BLUE_STAINED_GLASS':(124,186,214),
    'CYAN_GLAZED_TERRACOTTA':(39,129,150),'BLUE_GLAZED_TERRACOTTA':(63,80,165),
    'RED_GLAZED_TERRACOTTA':(143,38,48),'SOUL_LANTERN':(93,212,218),
    'CHEST':(153,103,47),'DECORATED_POT':(143,88,58),'END_ROD':(231,227,199)})
views=[('arkhaia-hall','ARKHAIA / THE CRIMSON THRONE HALL',28,27),
    ('arkhaia-gallery','ARKHAIA / THE ARMORED CLOISTER',220,31),
    ('valkyrie-reliquary','VALKYRIE / THE HALL OF BLADES',28,30),
    ('seraphon-hall','SERAPHON / THE CELESTIAL TREASURY',28,29)]
manifest=[]
for name,title,azimuth,elevation in views:
    with (args.exports/(name+'.csv')).open() as source:
        blocks={(int(x),int(y),int(z)):material for x,y,z,material in csv.reader(source)}
    result=Renderer(blocks,LandmarkTextures(args.minecraft_jar)).render(args.output_dir/(name+'.png'),title,
        'Actual furnished blocks | Architectural cutaway | Exterior retained in-game | Equipment displays appear in-game',
        width=args.width,height=round(args.width*.86),azimuth=azimuth,elevation=elevation,shadows=False)
    manifest.append({'id':name,**result});del blocks
thumb=760;h=round(thumb*.86);canvas=Image.new('RGB',(thumb*3,h+110),(235,230,222));draw=ImageDraw.Draw(canvas)
draw.text((30,22),'EVERGARDEN / BOSS INTERIORS',font=font(29,True),fill=(43,61,85))
draw.text((30,65),'Interior cutaways from actual blocks. Exterior walls remain intact in-game.',font=font(15),fill=(64,81,103))
for i,name in enumerate(['arkhaia-hall','valkyrie-reliquary','seraphon-hall']):
    with Image.open(args.output_dir/(name+'.png')) as im:canvas.paste(im.resize((thumb,h),Image.Resampling.LANCZOS),(i*thumb,110))
canvas.save(args.output_dir/'overview.png')
(args.output_dir/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf8')
