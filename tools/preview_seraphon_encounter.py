"""Offline review composition using the shipped rig, real clip keys and encounter spell textures."""
from pathlib import Path
import json,sys,math
import numpy as np
from PIL import Image,ImageDraw,ImageFont,ImageFilter
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'evergarden/tools'))
from boss_render import Rig,render
OUT=ROOT/'previews/seraphon-encounter';OUT.mkdir(parents=True,exist_ok=True)
ART=ROOT/'evergarden/art/seraphon-effects'
rig=Rig(json.loads((ROOT/'evergarden/src/main/resources/modelengine/seraphon.bbmodel').read_text(encoding='utf8')))
textures={n:Image.open(ART/(n+'.png')).convert('RGBA') for n in ['seraphon_seal','seraphon_orbit','seraphon_sanctuary','seraphon_feather']}
def font(size,bold=False):return ImageFont.truetype('C:/Windows/Fonts/'+('segoeuib.ttf' if bold else 'segoeui.ttf'),size)
def scene(clip,time,turn,size=1200):
    im=Image.new('RGBA',(1200,1100),(8,12,27,255));fx=Image.new('RGBA',im.size)
    def seal(name,xy,wh,angle=0):
        tile=textures[name].rotate(angle,resample=Image.Resampling.BICUBIC).resize(wh,Image.Resampling.LANCZOS)
        fx.alpha_composite(tile,(int(xy[0]-wh[0]/2),int(xy[1]-wh[1]/2)))
    seal('seraphon_seal',(600,200),(860,240),turn)
    seal('seraphon_orbit',(600,195),(1040,310),-turn*1.4)
    seal('seraphon_orbit',(600,205),(930,390),turn*.8)
    for i in range(6):
        a=i*math.pi/3;seal('seraphon_seal',(600+math.cos(a)*365,470+math.sin(a)*150),(180,180),turn*1.6)
    d=ImageDraw.Draw(fx)
    for x,y in [(190,300),(345,250),(855,250),(1010,300)]:
        d.line([(x,y),(x,820)],fill=(255,222,105,170),width=5);d.line([(x+13,y),(x+13,820)],fill=(255,244,195,55),width=15)
        seal('seraphon_seal',(x,820),(140,56),turn)
    seal('seraphon_seal',(600,865),(1060,280),-turn*.45)
    for x,y in [(375,888),(600,952),(825,888)]:seal('seraphon_sanctuary',(x,y),(140,58),-turn)
    glow=fx.filter(ImageFilter.GaussianBlur(12));im=Image.alpha_composite(im,glow);im=Image.alpha_composite(im,fx)
    model,_=render(rig,clip,time,yaw=12,elevation=-5,size=690,span=76,center=np.array([0,25]),background=(0,0,0),supersample=1,ground=False)
    arr=np.asarray(model);alpha=np.max(arr,axis=2)>1;layer=Image.fromarray(np.dstack((arr,alpha.astype('uint8')*255)))
    im.alpha_composite(layer,(255,225))
    d=ImageDraw.Draw(im);d.text((44,30),'SERAPHON',font=font(47,True),fill=(255,228,155));d.text((46,89),"HEAVEN'S VERDICT   /   10,000 HP",font=font(22),fill=(139,224,246))
    d.text((46,1032),'OFFLINE EFFECT REVIEW · shipped model, animation and rune textures',font=font(20),fill=(160,174,191))
    d.text((46,1062),'Not an in-game capture · three phases · six-wing guardian of Supreme Sanctuary',font=font(17),fill=(121,140,163))
    if size!=1200:im=im.resize((size,int(1100*size/1200)),Image.Resampling.LANCZOS)
    return im.convert('RGB')
hero=scene('verdict_windup',1.8,12);hero.save(OUT/'heavens-verdict.png')
frames=[]
for i in range(24):
    if i<12:clip,t='verdict_windup',i/6
    elif i<19:clip,t='verdict_active',(i-12)/6
    else:clip,t='verdict_recover',(i-19)/4
    frames.append(scene(clip,t,i*3,600))
frames[0].save(OUT/'heavens-verdict.gif',save_all=True,append_images=frames[1:],duration=125,loop=0,optimize=True)
(OUT/'index.html').write_text('''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>Seraphon / Heaven’s Verdict</title><style>body{background:#080c1b;color:#fff0cc;font:17px Segoe UI,sans-serif;max-width:1200px;margin:36px auto;padding:0 20px}p{color:#9fb6c8}img{width:100%;border-radius:18px}main{display:grid;grid-template-columns:1fr 1fr;gap:20px}@media(max-width:700px){main{grid-template-columns:1fr}}</style><h1>Seraphon · Heaven’s Verdict · 10,000 HP</h1><p>Offline review composition of the shipped six-wing model, authored combat animation and adapted archived golden rune textures. These are not in-game screenshots.</p><main><img src="heavens-verdict.gif" alt="Authored verdict animation with spell-circle artwork"><img src="heavens-verdict.png" alt="Golden and cyan effect review"></main></html>''',encoding='utf8')
print(OUT)
