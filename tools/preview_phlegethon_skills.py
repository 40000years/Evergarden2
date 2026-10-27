"""Render production Phlegethon clips for geometry review, outside the game."""
import base64
import io
import json
from pathlib import Path
from PIL import Image,ImageDraw
import preview_arkhaia_minions_v2 as rig

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'previews/phlegethon-skills'
model=json.loads((ROOT/'evergarden/src/main/resources/modelengine/rivers/arkhaia_phlegethon.bbmodel').read_text())
texture=Image.open(io.BytesIO(base64.b64decode(model['textures'][0]['source'].split(',')[1]))).convert('RGBA')
rig.load(model,texture);OUT.mkdir(exist_ok=True)
board=Image.new('RGB',(1200,860),(23,21,34));draw=ImageDraw.Draw(board)
for column,prefix in enumerate(['cast','kick','throw']):
    for row,(stage,t) in enumerate([('windup',1),('active',.2)]):
        board.paste(rig.rig.render(prefix+'_'+stage,t,yaw=60,elevation=8,size=400,span=65),(column*400,row*430))
        draw.text((column*400+12,row*430+405),prefix+' '+stage,fill='white')
    frames=[]
    prepare={'cast':44,'kick':26,'throw':36}[prefix]
    for stage,ticks,length in [('windup',prepare,1),('active',10,.5),('recover',14,.7)]:
        for n in range(ticks):
            frames.append(rig.rig.render(prefix+'_'+stage,n/ticks*length,yaw=60,elevation=8,size=360,span=65))
    frames[0].save(OUT/(prefix+'.gif'),save_all=True,append_images=frames[1:],duration=50,loop=0)
board.save(OUT/'poses.png');print(OUT)
