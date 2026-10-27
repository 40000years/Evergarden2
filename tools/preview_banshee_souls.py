"""Render authored Banshee soul/curse/veil poses for geometry review."""
import base64,io,json
from pathlib import Path
from PIL import Image,ImageDraw
import preview_arkhaia_minions_v2 as rig

ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'previews/banshee-souls';OUT.mkdir(exist_ok=True)
model=json.loads((ROOT/'evergarden/src/main/resources/modelengine/rivers/arkhaia_banshee_wail.bbmodel').read_text())
rig.load(model,Image.open(io.BytesIO(base64.b64decode(model['textures'][0]['source'].split(',')[1]))).convert('RGBA'))
board=Image.new('RGB',(1200,860),(23,21,34));draw=ImageDraw.Draw(board)
for i,(clip,t) in enumerate([('attack_active',.2),('curse_windup',1),('curse_active',.2),('reveal',.8),('reform',.8),('hover',0)]):
    board.paste(rig.rig.render(clip,t,yaw=60,elevation=8,size=400,span=65),(i%3*400,i//3*430));draw.text((i%3*400+12,i//3*430+405),clip,fill='white')
board.save(OUT/'poses.png')
frames=[]
for clip,ticks,length in [('curse_windup',32,1),('curse_active',10,.5),('curse_recover',14,.7),('reveal',20,1),('reform',20,1)]:
    for n in range(ticks):frames.append(rig.rig.render(clip,n/ticks*length,yaw=60,elevation=8,size=360,span=65))
frames[0].save(OUT/'souls.gif',save_all=True,append_images=frames[1:],duration=50,loop=0)
print(OUT)
