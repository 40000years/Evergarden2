"""Render production rigs, including contact poses and the complete spinning cast."""
import base64
import io
import json
from pathlib import Path
from PIL import Image, ImageDraw
import preview_arkhaia_minions_v2 as rig

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'previews/river-choreography-v2'


def main():
    OUT.mkdir(exist_ok=True)
    board = Image.new('RGB', (1440, 6*390), (23, 21, 34))
    draw = ImageDraw.Draw(board)
    for i, name in enumerate(rig.NAMES):
        model = json.loads((ROOT / f'evergarden/src/main/resources/modelengine/rivers/arkhaia_{name}.bbmodel').read_text())
        texture = Image.open(io.BytesIO(base64.b64decode(model['textures'][0]['source'].split(',')[1]))).convert('RGBA')
        rig.load(model, texture)
        for j, (clip, t) in enumerate([('attack_windup', .8), ('attack_active', .2), ('cast_windup', .8), ('cast_active', .2)]):
            board.paste(rig.rig.render(clip, t, yaw=25, elevation=10, size=360,span=70), (j*360, i*390))
            draw.text((j*360+8, i*390+362), f'{name}: {clip}', fill='white')
        review=Image.new('RGB',(1600,860),(23,21,34));labels=ImageDraw.Draw(review)
        for row,(clip,t) in enumerate([('attack_windup',.8),('attack_active',.2)]):
            for column,yaw in enumerate([0,90,180,270]):
                review.paste(rig.rig.render(clip,t,yaw=yaw,elevation=5,size=400,span=70),(column*400,row*430))
                labels.text((column*400+6,row*430+402),f'{name} {clip} yaw={yaw}',fill='white')
        review.save(OUT/f'{name}-review.png')
        frames = []
        prepare={'acheron':24,'phlegethon':44,'styx':50}.get(name,32)
        moved=0;charge=[]
        for clip,ticks,length in [('cast_windup',prepare,1),('cast_active',24 if name=='acheron' else 10,1.2 if name=='acheron' else .5),('cast_recover',14,.7)]:
            for n in range(ticks):
                frame=rig.rig.render(clip,n/ticks*length,yaw=70,elevation=5,size=450,span=70)
                frames.append(frame)
                if name=='acheron':
                    if clip=='cast_active' and n>0:moved+=.38*min(1,(n+1)/4,(24-n)/4)
                    scene=Image.new('RGB',(900,450),(23,21,34));grid=ImageDraw.Draw(scene)
                    for x in range(0,900,40):grid.line((x,374,x-60,450),fill=(50,48,65))
                    grid.line((0,374,900,374),fill=(90,85,110))
                    mask=Image.fromarray((rig.np.any(rig.np.asarray(frame)!=[23,21,34],axis=2)*255).astype('uint8'))
                    scene.paste(frame,(round(380-moved*4*(450-85)/70),0),mask)
                    grid.text((12,12),'ACHERON / moving charge simulation / real receiver displacement',fill='white')
                    charge.append(scene)
        frames[0].save(OUT / f'{name}-cast.gif', save_all=True, append_images=frames[1:], duration=50, loop=0)
        if charge:charge[0].save(OUT/'acheron-charge.gif',save_all=True,append_images=charge[1:],duration=50,loop=0)
    board.save(OUT / 'combat-poses.png')
    print(OUT)


if __name__ == '__main__':
    main()
