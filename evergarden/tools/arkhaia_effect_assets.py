"""Selected Judge bands + legendary lunar constellations, recomposed in violet."""
import io
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageOps

ROOT = Path(__file__).resolve().parents[2]

def _tint(image):
    alpha = image.getchannel('A')
    color = ImageOps.colorize(ImageOps.grayscale(image), '#632c9a', '#ecd1ff').convert('RGBA')
    color.putalpha(alpha)
    return color

def assets(json_bytes, java_source, bedrock_source):
    outer = Image.open(io.BytesIO(java_source['assets/voidscape/textures/effect/judge_orbit.png'])).convert('RGBA')
    lunar = Image.open(ROOT / 'advance-magic/art/effects/legendary/lunar_constellation.png').convert('RGBA').resize((1024,1024))
    mask = Image.new('L',(1024,1024));draw = ImageDraw.Draw(mask)
    draw.ellipse((128,128,896,896),fill=255);draw.ellipse((340,340,684,684),fill=0)
    fragment = Image.new('RGBA',lunar.size);fragment.paste(lunar,(0,0),mask)
    rose = Image.alpha_composite(outer,fragment)
    # Six open petals replace the Judge's dense central star and rune script.
    draw = ImageDraw.Draw(rose)
    import math
    for i in range(6):
        a=i*math.tau/6
        points=[]
        for j in range(33):
            t=j/32;r=110+180*math.sin(math.pi*t);angle=a+(t-.5)*.7
            points.append((512+math.cos(angle)*r,512+math.sin(angle)*r))
        draw.line(points,fill=(210,150,255,225),width=3)
    import math
    gate=Image.alpha_composite(outer,fragment);g=ImageDraw.Draw(gate)
    for radius in (172,208,358,404):g.ellipse((512-radius,512-radius,512+radius,512+radius),outline=(235,180,255,245),width=3)
    crown=Image.new('RGBA',(1024,1024));c=ImageDraw.Draw(crown)
    for radius in (315,330,445,470):c.ellipse((512-radius,512-radius,512+radius,512+radius),outline=(220,155,255,255),width=3)
    weak=Image.new('RGBA',(1024,1024));w=ImageDraw.Draw(weak)
    w.ellipse((80,80,944,944),outline=(235,200,255,255),width=16)
    for i in range(12):
        angle=i*math.tau/12
        pts=[(512+math.cos(angle+t)*r,512+math.sin(angle+t)*r) for t,r in ((0,470),(.09,395),(0,345),(-.09,395))]
        c.line(pts+[pts[0]],fill=(245,200,255,255),width=4)
        g.line([(512+math.cos(angle)*220,512+math.sin(angle)*220),(512+math.cos(angle)*345,512+math.sin(angle)*345)],fill=(195,120,255,200),width=3)
    for i in range(4):
        a=i*math.pi/2;pts=[(512+math.cos(a+t)*r,512+math.sin(a+t)*r) for t,r in ((0,360),(.25,170),(-.25,170))]
        w.polygon(pts,fill=(235,190,255,240))
    w.ellipse((420,420,604,604),outline=(255,240,255,255),width=20)
    fracture=Image.new('RGBA',(1024,1024));f=ImageDraw.Draw(fracture)
    f.line([(8,0),(8,1023)],fill=(210,150,255,240),width=12);f.line([(1015,0),(1015,1023)],fill=(210,150,255,240),width=12)
    points=[(512+math.sin(i*2.7)*130,i*64) for i in range(17)]
    f.line(points,fill=(230,185,255,180),width=70);f.line(points,fill=(255,240,255,255),width=12)
    for i,(x,y) in enumerate(points[1:-1]):f.line([(x,y),(x+(-1 if i%2 else 1)*220,y-80)],fill=(205,135,255,220),width=7)
    sources={'arkhaia_rose':('judge_orbit',rose),'arkhaia_orbit':('judge_orbit',outer),
             'arkhaia_gate':('judge_orbit',gate),'arkhaia_crown':('judge_orbit',crown),
             'arkhaia_weakpoint':('judge_orbit',weak),'arkhaia_fracture':('judge_ray',fracture),
             'arkhaia_ray':('judge_ray',Image.open(io.BytesIO(java_source['assets/voidscape/textures/effect/judge_ray.png'])).convert('RGBA'))}
    java,bedrock={},{}
    art=ROOT/'evergarden/art/arkhaia';art.mkdir(parents=True,exist_ok=True)
    for name,(template,image) in sources.items():
        image=_tint(image);buffer=io.BytesIO();image.save(buffer,format='PNG');png=buffer.getvalue()
        java[f'assets/voidscape/textures/effect/{name}.png']=png
        bedrock[f'textures/particle/{name}.png']=png
        (art/f'{name}.png').write_bytes(png)
        for folder in ('models/effect','items'):
            data=json.loads(java_source[f'assets/voidscape/{folder}/{template}.json'].decode().replace(template,name))
            java[f'assets/voidscape/{folder}/{name}.json']=json_bytes(data)
        for suffix in ('','_flat'):
            data=json.loads(bedrock_source[f'particles/{template}{suffix}.particle.json'].decode().replace(template,name))
            bedrock[f'particles/{name}{suffix}.particle.json']=json_bytes(data)
    return java,bedrock
