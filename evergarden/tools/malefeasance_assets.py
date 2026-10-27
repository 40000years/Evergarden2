"""Code-authored dimensional seals and luminous beam textures for both clients."""
import io
import json
import math
from PIL import Image, ImageDraw, ImageFilter

COLORS={
    'violet': (174, 72, 255),
    'crimson': (255, 36, 83),
    'cyan': (34, 180, 216),
    'gold': (250, 210, 70),
}


def png(image):
    out=io.BytesIO();image.save(out,format='PNG');return out.getvalue()


def seal(color):
    ink=Image.new('RGBA',(1024,1024));d=ImageDraw.Draw(ink)
    point=lambda a,r:(512+math.cos(a)*r,512+math.sin(a)*r)
    for radius,width in [(480,4),(470,2),(409,3),(397,2),(317,3),(307,2),(151,3)]:
        d.ellipse((512-radius,512-radius,512+radius,512+radius),outline=(*color,230),width=width)
    for i in range(16):
        a=i*22.5;d.arc((39,39,985,985),a+3,a+17,fill=(245,220,255,245),width=4)
    for i in range(48):
        a=i*math.tau/48
        d.line([point(a,420),point(a,453)],fill=(*color,245),width=3)
        # Deliberately geometric glyphs: each uses a radial stem and two changing branches.
        sign=1 if i%2 else -1
        d.line([point(a,442),point(a+sign*.018,450),point(a+sign*.031,438)],fill=(255,235,255,245),width=3)
        if i%3==0:d.line([point(a,430),point(a-sign*.025,436)],fill=(*color,255),width=3)
    for offset in (0,math.pi/3):
        vertices=[point(offset+i*math.tau/3,302) for i in range(3)]
        d.line(vertices+[vertices[0]],fill=(*color,240),width=4)
    for i in range(12):
        a=i*math.tau/12
        diamond=[point(a,386),point(a+.025,364),point(a,342),point(a-.025,364)]
        d.line(diamond+[diamond[0]],fill=(*color,255),width=3)
        d.ellipse((point(a,365)[0]-3,point(a,365)[1]-3,point(a,365)[0]+3,point(a,365)[1]+3),fill=(255,235,255,255))
    # A split dimensional eye, with a luminous diamond pupil.
    d.arc((339,421,685,603),190,350,fill=(*color,255),width=5)
    d.arc((339,421,685,603),10,170,fill=(*color,255),width=5)
    d.polygon([(512,440),(550,512),(512,584),(474,512)],outline=(255,235,255,255),width=4)
    glow=Image.new('RGBA',ink.size)
    for blur in (15,6):glow=Image.alpha_composite(glow,ink.filter(ImageFilter.GaussianBlur(blur)))
    return png(Image.alpha_composite(glow,ink))


def beam(color):
    image=Image.new('RGBA',(256,1024));pixels=image.load()
    for x in range(256):
        distance=abs((x-127.5)/127.5)
        alpha=round(34*(1-distance)**.25+140*math.exp(-distance*distance/0.026))
        hot=math.exp(-distance*distance/.002)
        c=tuple(round(v*(1-hot)+255*hot) for v in color)
        for y in range(1024):pixels[x,y]=(*c,alpha)
    d=ImageDraw.Draw(image)
    for phase in range(6):
        line=[(128+math.sin(y*.017+phase*math.tau/6)*(35+phase*9),y) for y in range(0,1024,5)]
        d.line(line,fill=(*color,170),width=3)
    d.line((128,0,128,1024),fill=(255,235,255,255),width=3)
    return png(image)


def assets(encode,java_source,bedrock_source):
    java={};bedrock={}
    for palette,color in COLORS.items():
        for shape,texture in [('seal',seal(color)),('ray',beam(color))]:
            name=f'malef_{palette}_{shape}'
            template='judge_orbit' if shape=='seal' else 'judge_ray'
            java[f'assets/voidscape/textures/effect/{name}.png']=texture
            bedrock[f'textures/particle/{name}.png']=texture
            for folder in ('models/effect','items'):
                key=f'assets/voidscape/{folder}/{template}.json'
                java[f'assets/voidscape/{folder}/{name}.json']=encode(json.loads(java_source[key].decode().replace(template,name)))
            for suffix in ('','_flat'):
                key=f'particles/{template}{suffix}.particle.json'
                data=json.loads(bedrock_source[key].decode().replace(template,name))
                uv=data['particle_effect']['components']['minecraft:particle_appearance_billboard']['uv']
                width,height=(1024,1024) if shape=='seal' else (256,1024)
                uv.update(texture_width=width,texture_height=height,uv_size=[width,height])
                bedrock[f'particles/{name}{suffix}.particle.json']=encode(data)
    return java,bedrock
