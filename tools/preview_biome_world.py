#!/usr/bin/env python3
"""Draw topographic maps from exact BiomeTerrainChecks CSV samples, using only Pillow."""
import argparse
import csv
import json
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

COLORS = {'gardens': (219, 142, 177), 'glacier': (188, 232, 247), 'ancient-forest': (56, 131, 90), 'lunar-city': (155, 143, 205)}
NAMES = {'gardens': 'Evergarden Gardens', 'glacier': 'The Glacial Massifs', 'ancient-forest': 'The Ancient Forest', 'lunar-city': 'The Lost Lunar City'}


def font(size):
    for path in ('C:/Windows/Fonts/segoeui.ttf', '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'):
        if Path(path).is_file():
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def load(file):
    with file.open(encoding='utf8') as stream:
        rows = list(csv.DictReader(stream))
    xs = sorted({int(r['x']) for r in rows}); zs = sorted({int(r['z']) for r in rows})
    lookup = {(int(r['x']), int(r['z'])): r for r in rows}
    return xs, zs, [[lookup[x, z] for x in xs] for z in zs]


def render(file, destination, title, width):
    xs, zs, grid = load(file)
    side = width - 100; image = Image.new('RGB', (width, width + 176), '#101722'); draw = ImageDraw.Draw(image)
    draw.text((50, 28), title, fill='#eef3fa', font=font(33))
    extent_x = xs[-1] - xs[0]; extent_z = zs[-1] - zs[0]
    caption = f'{extent_x / 1000:g} x {extent_z / 1000:g} km | 1 pixel sample = {xs[1] - xs[0]} blocks | seed 20261013'
    draw.text((50, 75), caption, fill='#abbacd', font=font(18))
    terrain = Image.new('RGB', (len(xs), len(zs))); pixels = terrain.load()
    for j, row in enumerate(grid):
        for i, r in enumerate(row):
            if r['land'] != 'true':
                pixels[i, j] = (25, 41, 64)
                continue
            color = COLORS[r['kind']]; height = int(r['top'])
            left = int(row[max(0, i-1)]['top']); right = int(row[min(len(xs)-1, i+1)]['top'])
            above = int(grid[max(0, j-1)][i]['top']); below = int(grid[min(len(zs)-1, j+1)][i]['top'])
            slope = (left-right+above-below) / 70
            shade = max(.58, min(1.22, .91 + slope + (height-80)/850))
            if math.floor(height/12) != math.floor(left/12):
                shade *= .82
            pixels[i, j] = tuple(max(0, min(255, round(c * shade))) for c in color)
    terrain = terrain.resize((side, side), Image.Resampling.BICUBIC)
    image.paste(terrain, (50, 114)); draw.rectangle((49, 113, width-49, side+115), outline='#43556b', width=2)
    for fraction in (0, .25, .5, .75, 1):
        x = round(50 + side*fraction); z = round(114 + side*fraction)
        draw.line((x, 114, x, 114+side), fill='#607084', width=1)
        draw.line((50, z, side+50, z), fill='#607084', width=1)
        label = str(round(xs[0]+extent_x*fraction))
        draw.text((x-15, side+121), label, fill='#93a4ba', font=font(14))
    if xs[0] <= 0 <= xs[-1] and zs[0] <= 0 <= zs[-1]:
        px = round(50 + (0-xs[0])/extent_x*side); pz = round(114+(0-zs[0])/extent_z*side)
        draw.ellipse((px-5,pz-5,px+5,pz+5), fill='#fff6bd', outline='#101722', width=2)
        draw.text((px+10,pz-10), 'Starter gardens', fill='#fff6bd', font=font(16))
    y = side+153; cursor=50
    for kind, color in COLORS.items():
        draw.rectangle((cursor,y,cursor+13,y+13), fill=color)
        label = {'gardens':'Gardens','glacier':'Glacier','ancient-forest':'Ancient forest','lunar-city':'Lunar city'}[kind]
        draw.text((cursor+21,y-4),label,fill='#d6dfec',font=font(17)); cursor += width*.217
    draw.text((50,y+31),'Exact generator coordinate samples; simulated lighting. Not a game screenshot.',fill='#93a4ba',font=font(16))
    image.save(destination)
    return {'image': destination.name,'source':str(file),'x':[xs[0],xs[-1]],'z':[zs[0],zs[-1]],'sample_step':xs[1]-xs[0]}


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('samples',type=Path)
    p.add_argument('--output-dir',type=Path,default=Path('previews/biome-world'));p.add_argument('--width',type=int,default=1400)
    args=p.parse_args();args.output_dir.mkdir(parents=True,exist_ok=True)
    metadata=[]
    for name,title in [('overview','EVERGARDEN | FOUR GREAT SKYLANDS'),*[(k,NAMES[k].upper()) for k in COLORS]]:
        source=args.samples/(name+'.csv')
        if not source.is_file():continue
        destination=args.output_dir/(name+'-terrain-map.png')
        metadata.append(render(source,destination,title,args.width));print(destination,flush=True)
    (args.output_dir/'manifest.json').write_text(json.dumps({'seed':20261013,'biome_cell_scale':3072,'description':'Exact BiomeTerrain coordinate samples with simulated lighting; terrain map, not game screenshot.','maps':metadata},indent=2),encoding='utf8')


if __name__=='__main__':main()
