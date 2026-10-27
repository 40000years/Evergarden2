"""Render the shipped pet blueprints with their real texture, joints and clips."""
import argparse
import base64
import io
import json
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFont
import numpy as np

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'evergarden/tools'))
from pet_model_engine import SPECIES, MYTHICAL, RARE, MENAGERIE, HOVERING, STAGES, TITLES
import preview_arkhaia_rig as renderer

OUT=ROOT/'previews/pet-models'
SOURCE=ROOT/'evergarden/src/main/resources/modelengine/pets'
FONT='C:/Windows/Fonts/segoeui.ttf'


def save_preview(im,path,**options):
    temporary=path.with_name(path.stem+'.tmp'+path.suffix)
    im.save(temporary,**options)
    temporary.replace(path)


def load(species,stage):
    model=json.loads((SOURCE/f'everpet_{species}_{stage}.bbmodel').read_text(encoding='utf8'))
    renderer.MODEL=model
    renderer.ELEMENTS={e['uuid']:e for e in model['elements']}
    renderer.CLIPS={c['name']:c for c in model['animations']}
    renderer.TEXTURE=np.array(Image.open(io.BytesIO(base64.b64decode(model['textures'][0]['source'].split(',')[1]))).convert('RGBA'))
    return model


def span_for(species,clip='idle'):
    load(species,'adult');points=[]
    for cube,matrix in renderer.transformed(clip,0):
        a=np.array(cube['from']);b=np.array(cube['to']);origin=np.array(cube['origin'])
        r=renderer.rotation(cube['rotation'])
        for x in (a[0],b[0]):
            for y in (a[1],b[1]):
                for z in (a[2],b[2]):
                    point=r@(np.array([x,y,z])-origin)+origin
                    points.append((matrix@np.append(point,1))[:3])
    bounds=np.array(points);delta=bounds.max(axis=0)-bounds.min(axis=0)
    # Consistent camera scale across all growth forms makes the real size visible.
    return max(19,delta[0]*1.08,bounds[:,1].max()*1.30,delta[2]*1.08)


def frame(species,stage,span,size=560,clip='idle',time=0,yaw=32):
    load(species,stage)
    return renderer.render(clip,time,yaw=yaw,elevation=14,size=size,span=span)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--gifs',action='store_true')
    p.add_argument('--assemble-only',action='store_true',help='Assemble the gallery from rendered growth sheets.')
    p.add_argument('--species',nargs='*',choices=SPECIES);args=p.parse_args()
    OUT.mkdir(parents=True,exist_ok=True)
    title=ImageFont.truetype(FONT,25);small=ImageFont.truetype(FONT,18)
    species_list=[] if args.assemble_only else args.species or SPECIES
    for species in species_list:
        span=span_for(species);index=SPECIES.index(species)
        sheet=Image.new('RGB',(1680,650),(23,21,34));draw=ImageDraw.Draw(sheet)
        for column,stage in enumerate(STAGES):
            f=frame(species,stage,span);sheet.paste(f,(560*column,50))
            draw.text((column*560+24,598),stage.upper(),font=small,fill='#97a8bc')
            save_preview(f,OUT/f'{species}-{stage}.png')
        draw.text((24,15),TITLES[index],font=title,fill='#ffe5b2' if species in MYTHICAL else '#b4e4e9')
        draw.text((24,625),'Original Blockbench geometry / actual texture and skeleton / shared growth scale',font=small,fill='#8e889f')
        save_preview(sheet,OUT/f'{species}-growth.png')
        views=Image.new('RGB',(1680,600),(23,21,34))
        for col,yaw in enumerate((0,90,180)):
            views.paste(frame(species,'adult',span,yaw=yaw),(col*560,0))
            ImageDraw.Draw(views).text((col*560+20,565),('FRONT','SIDE','BACK')[col],font=small,fill='#a4adc0')
        save_preview(views,OUT/f'{species}-turnaround.png')
        if args.gifs:
            frames=[]
            clip='fly' if species in HOVERING+('frost_wyvern','bone_drake','titan_beetle') else 'greet'
            motion_span=max(span,span_for(species,clip))
            for i in range(24):
                f=frame(species,'adult',motion_span,size=420,clip=clip,time=i/24*(1.4 if clip=='fly' else 2.4))
                ImageDraw.Draw(f).text((16,12),TITLES[index]+' / '+clip,font=small,fill='#f7dfb4');frames.append(f)
            save_preview(frames[0],OUT/f'{species}.gif',save_all=True,append_images=frames[1:],duration=80,loop=0,optimize=True)
        print(species,flush=True)
    if not args.species:
        rows=(len(SPECIES)+4)//5
        lineup=Image.new('RGB',(1600,rows*370+130),(16,17,27));labels=ImageDraw.Draw(lineup)
        labels.text((28,22),'EVERGARDEN  /  PET COLLECTION',font=title,fill='#efcf99')
        labels.text((28,64),f'{len(SPECIES)} original companions / adult showcase / 3 growth forms each',font=small,fill='#9daac1')
        for i,species in enumerate(SPECIES):
            original=Image.open(OUT/f'{species}-adult.png').convert('RGB')
            bounds=ImageChops.difference(original,Image.new('RGB',original.size,(23,21,34))).getbbox()
            pet=original.crop(bounds);pet.thumbnail((280,290),Image.Resampling.NEAREST)
            x,y=i%5*320,130+i//5*370
            lineup.paste(pet,(x+(320-pet.width)//2,y+290-pet.height))
            labels.text((x+15,y+308),TITLES[i],font=small,fill='#efcf99' if species in MYTHICAL else '#abdce1')
        temporary=OUT/'pet-collection.tmp.png';lineup.save(temporary)
        temporary.replace(OUT/'pet-collection.png')
        for group,names in (('mythical',MYTHICAL),('rare-1',RARE[:6]),('rare-2',RARE[6:]),('menagerie-cute',MENAGERIE[:7]),
                            ('menagerie-fierce',MENAGERIE[7:14]),('menagerie-beautiful',MENAGERIE[14:])):
            contact=Image.new('RGB',(1260,488*len(names)),(23,21,34))
            for i,species in enumerate(names):
                row=Image.open(OUT/f'{species}-growth.png');row.thumbnail((1260,488));contact.paste(row,(0,i*488))
            # Atomic replacement also avoids Windows/Pillow opening an existing
            # contact sheet for simultaneous read/write while a preview is open.
            temporary=OUT/f'{group}-growth.tmp.png';contact.save(temporary)
            temporary.replace(OUT/f'{group}-growth.png')
        cards=[]
        for species in SPECIES:
            motion=f'<details><summary>Animation preview</summary><img style="max-width:420px" src="{species}.gif" alt="Actual skeleton animation"></details>' if (OUT/f'{species}.gif').exists() else ''
            cards.append(f'<article><h2>{TITLES[SPECIES.index(species)]}</h2><img src="{species}-growth.png" alt="Baby medium adult {species}"><details><summary>Front / side / back</summary><img src="{species}-turnaround.png"></details>{motion}<code>/evergarden pets growth {species}</code></article>')
        html='''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Evergarden / Pet Atelier</title><style>body{margin:0;background:#11111c;color:#f2ebdf;font:16px Segoe UI,sans-serif}main{max-width:1500px;margin:auto;padding:48px 28px}h1{font:52px Georgia;margin:10px 0}header{padding:20px 0 48px;border-bottom:1px solid #343241}p,summary{color:#aab2c3;line-height:1.8}small{letter-spacing:4px;color:#d9b877}article{padding:22px 0 36px;border-bottom:1px solid #343241}h2{font:28px Georgia;color:#efcf99}img{display:block;width:100%;height:auto;border-radius:10px}code{display:block;padding:16px;background:#1d1c2c;color:#abd9dd;border-radius:8px;overflow:auto}summary{cursor:pointer;padding:10px}a{color:#b1d6e8}</style><main><header><small>EVERGARDEN / ORIGINAL COLLECTION</small><h1>Pet Atelier</h1><p>'''+f'{len(SPECIES)} companions. Three authored growth forms. {len(SPECIES)*3} editable ModelEngine models.'+'''<br>Images render the actual shipped .bbmodel geometry, textures and joints; they are offline renders, not Minecraft screenshots.<br>Baby, medium and adult use the same camera scale within each species. Riding is reserved for a later phase.</p></header>'''+''.join(cards)+'</main></html>'
        (OUT/'index.html').write_text(html,encoding='utf8')


if __name__=='__main__':main()
