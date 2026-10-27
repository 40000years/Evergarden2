"""Textured depth-buffer previews of the exported cuboids, not game screenshots."""
from __future__ import annotations
import math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'previews/legendary-weapons'


def render_item(weapon,texture,fx=None,size=512,yaw=24,tilt=0,elevation=10):
    from legendary_weapon_assets import tile, EMISSIVE
    tex=np.asarray(texture.convert('RGBA'))
    a,e,t=map(math.radians,(yaw,elevation,tilt))
    camera=np.array([math.sin(a)*math.cos(e),math.sin(e),-math.cos(a)*math.cos(e)])
    proj=np.array([[math.cos(a),0,math.sin(a)],[-math.sin(e)*math.sin(a),math.cos(e),math.sin(e)*math.cos(a)],camera])
    proj[1]*=-1
    rz=np.array([[math.cos(t),-math.sin(t),0],[math.sin(t),math.cos(t),0],[0,0,1]])
    proj=rz@proj
    scale=size/39
    depth=np.full((size,size),-1e8);canvas=np.zeros((size,size,4),dtype=np.uint8)
    light=np.array([-.4,.7,-.6]);light/=np.linalg.norm(light)
    def face(verts,normal,uv,image,emissive=False):
        if np.dot(normal,camera)<=0:return
        points=(np.array(verts)-[8,16,8])@proj.T
        screen=points[:,:2]*scale+[size/2,size/2]
        left,top=np.maximum(0,np.floor(screen.min(axis=0)).astype(int))
        right,bottom=np.minimum(size,np.ceil(screen.max(axis=0)+1).astype(int))
        if right<=left or bottom<=top:return
        origin=screen[0];b=screen[1]-origin;c=screen[3]-origin
        det=b[0]*c[1]-b[1]*c[0]
        if abs(det)<1e-8:return
        px=np.arange(left,right)[None,:]+.5-origin[0];py=np.arange(top,bottom)[:,None]+.5-origin[1]
        u=(px*c[1]-py*c[0])/det;v=(py*b[0]-px*b[1])/det
        z=points[0,2]+u*(points[1,2]-points[0,2])+v*(points[3,2]-points[0,2])
        target=depth[top:bottom,left:right]
        mask=(u>=0)&(u<=1)&(v>=0)&(v<=1)&(z>target)
        h,w=image.shape[:2]
        tx=np.clip((uv[0]+u*(uv[2]-uv[0])).astype(int),0,w-1)
        ty=np.clip((uv[1]+v*(uv[3]-uv[1])).astype(int),0,h-1)
        pixels=image[ty,tx];mask&=pixels[:,:,3]>127
        shade=1 if emissive else .66+.36*max(0,float(np.dot(normal,light)))
        rgba=pixels.copy();rgba[:,:,:3]=np.clip(pixels[:,:,:3]*shade,0,255).astype('uint8')
        canvas[top:bottom,left:right][mask]=rgba[mask];target[mask]=z[mask]
    for cube in weapon.cubes:
        x0,y0,z0=cube['from'];x1,y1,z1=cube['to']
        uv=cube.get('preview_uv',[i*8 for i in tile(cube['mat'])])
        cube_texture=np.asarray(cube['preview_texture'].convert('RGBA')) if 'preview_texture' in cube else tex
        for normal,verts in (
            ((0,0,-1),[(x0,y1,z0),(x1,y1,z0),(x1,y0,z0),(x0,y0,z0)]),
            ((0,0,1),[(x1,y1,z1),(x0,y1,z1),(x0,y0,z1),(x1,y0,z1)]),
            ((1,0,0),[(x1,y1,z0),(x1,y1,z1),(x1,y0,z1),(x1,y0,z0)]),
            ((-1,0,0),[(x0,y1,z1),(x0,y1,z0),(x0,y0,z0),(x0,y0,z1)]),
            ((0,1,0),[(x0,y1,z1),(x1,y1,z1),(x1,y1,z0),(x0,y1,z0)]),
            ((0,-1,0),[(x0,y0,z0),(x1,y0,z0),(x1,y0,z1),(x0,y0,z1)])):
            if 'pose' in cube:
                rotation=np.asarray(cube['pose']['rotation']);offset=np.asarray(cube['pose']['translation'])
                verts=np.asarray(verts)@rotation.T+offset;normal=rotation@np.asarray(normal)
            face(verts,normal,uv,cube_texture,cube['mat'] in EMISSIVE)
    if fx is not None:
        fxtex=np.asarray(fx.convert('RGBA'))
        face([(-8,32,3.25),(24,32,3.25),(24,0,3.25),(-8,0,3.25)],(0,0,-1),[0,0,128,128],fxtex,True)
        face([(24,32,12.75),(-8,32,12.75),(-8,0,12.75),(24,0,12.75)],(0,0,1),[0,0,128,128],fxtex,True)
        face([(7.95,32,-8),(7.95,32,24),(7.95,0,24),(7.95,0,-8)],(1,0,0),[0,0,128,128],fxtex,True)
    return Image.fromarray(canvas)


def font(size,bold=False):
    return ImageFont.truetype('C:/Windows/Fonts/'+('segoeuib.ttf' if bold else 'segoeui.ttf'),size)


def render_icon_frames(weapon,texture,effects):
    # Fit the SOLID weapon, not distant sparks. One square crop is shared by
    # all frames; uniform scaling preserves shape and avoids animated size jitter.
    kwargs=dict(size=256,yaw=18,tilt=-38,elevation=8)
    body=render_item(weapon,texture,None,**kwargs)
    l,t,r,b=body.getbbox();side=math.ceil(max(r-l,b-t)*1.12)
    cx,cy=(l+r)/2,(t+b)/2
    crop=(round(cx-side/2),round(cy-side/2),round(cx-side/2)+side,round(cy-side/2)+side)
    icons=[]
    for effect in effects:
        im=render_item(weapon,texture,effect,**kwargs).crop(crop).resize((128,128),Image.Resampling.NEAREST)
        # Keep a transparent gutter even when a distant lightning branch passes
        # beyond the body crop. The solid handle/blade always remains inside it.
        alpha=im.getchannel('A');d=ImageDraw.Draw(alpha)
        for box in ((0,0,127,3),(0,124,127,127),(0,0,3,127),(124,0,127,127)):d.rectangle(box,fill=0)
        im.putalpha(alpha);icons.append(im)
    return icons


def glow_composite(target,im,xy,color):
    # Presentation bloom is confined to the reference preview; the pack ships cutouts.
    mask=im.getchannel('A').filter(ImageFilter.GaussianBlur(12))
    glow=Image.new('RGBA',im.size,color+(0,));glow.putalpha(mask.point(lambda a:int(a*.18)))
    target.alpha_composite(glow,xy);target.alpha_composite(im,xy)


def make_previews():
    from legendary_weapon_assets import IDS,TITLES,COLORS,make_weapon,atlas,fx_frame
    OUT.mkdir(parents=True,exist_ok=True)
    tex=atlas();width,height=2100,1240
    bg=Image.new('RGBA',(width,height),(8,12,23,255));d=ImageDraw.Draw(bg)
    d.text((65,34),'E V E R G A R D E N   /   A R M O U R Y',font=font(19),fill='#9caabd')
    d.text((60,64),'LEGENDARY WEAPONS',font=font(63,True),fill='#f6dfaf')
    d.text((64,149),'FIVE RELICS.  FIVE SIGNATURES.     /     JAVA + BEDROCK',font=font(20),fill='#a5b5ca')
    d.line((65,199,width-65,199),fill='#3c3e47',width=2)
    subtitles=('THE SWORD OF KINGS','THE SACRED LANCE','THE SOUL DEVOURER','THUNDER OF THE GODS','THE DIVINE SCEPTER')
    effects=('SILVER / SAPPHIRE / HOLY LIGHT','GOLD / CRIMSON SILK / EMBERS','OBSIDIAN / LIVING CRIMSON RUNES','FORGED STEEL / ARC LIGHTNING','GOLDEN CAGE / CYAN CRYSTAL')
    for i,name in enumerate(IDS):
        x=45+i*405;color=COLORS[i];w=make_weapon(name)
        d.rounded_rectangle((x,224,x+390,1110),radius=12,fill=(13,20,33,255),outline=(*color,130),width=1)
        d.rectangle((x+1,224,x+389,228),fill=color)
        d.text((x+23,248),f'0{i+1}   /   LEGENDARY',font=font(14),fill=color)
        d.text((x+22,277),TITLES[i].upper(),font=font(34,True),fill='#fff4da')
        d.text((x+23,324),subtitles[i],font=font(14),fill='#a4b1c5')
        # 3D weapon uses depth-buffered, texture-mapped exported faces.
        im=render_item(w,tex,fx_frame(name,1),size=600,yaw=27,tilt=-20,elevation=12)
        im=im.resize((480,480),Image.Resampling.NEAREST)
        glow_composite(bg,im,(x-45,358),color)
        d=ImageDraw.Draw(bg)
        d.line((x+22,830,x+367,830),fill='#2c3a4e')
        d.text((x+24,850),'PROFILE',font=font(13),fill='#7c8da8')
        front=render_item(w,tex,None,size=230,yaw=0,tilt=0,elevation=0)
        bg.alpha_composite(front,(x-18,864))
        icon=Image.open(ROOT/f'advance-magic/art/legendary-weapons/{name}_icon.png').convert('RGBA')
        d=ImageDraw.Draw(bg);d.text((x+224,850),'HOTBAR ICON',font=font(13),fill='#7c8da8')
        d.rectangle((x+234,894,x+330,990),fill='#202735',outline='#8e9aaa',width=3)
        bg.alpha_composite(icon.resize((92,92),Image.Resampling.NEAREST),(x+237,897))
        d=ImageDraw.Draw(bg);d.text((x+223,1010),'AUTHORED VFX',font=font(13),fill=color)
        d.text((x+24,1080),effects[i],font=font(11),fill='#b3c0d0')
        render_item(w,tex,fx_frame(name,1),size=768,yaw=27,tilt=-15).save(OUT/f'{name}.png')
    d.text((65,1150),'3D GEOMETRY  /  ANIMATED ARC TEXTURES  /  EMISSIVE DETAILS  /  EDITABLE SOURCES',font=font(19),fill='#d6c5a4')
    d.text((65,1190),'Actual asset geometry preview. Lighting is illustrative; client rendering must be checked in game.',font=font(16),fill='#748398')
    bg.convert('RGB').save(OUT/'legendary-weapons.png')
    frames=[]
    for f in range(16):
        sheet=Image.new('RGBA',(1500,600),(9,14,25,255));draw=ImageDraw.Draw(sheet)
        for i,name in enumerate(IDS):
            im=render_item(make_weapon(name),tex,fx_frame(name,f%8),size=330,yaw=18+8*math.sin(f*math.tau/16),tilt=-15)
            glow_composite(sheet,im,(i*300-15,70),COLORS[i])
            draw=ImageDraw.Draw(sheet);draw.text((i*300+30,435),TITLES[i].upper(),font=font(25,True),fill=COLORS[i])
            icon=render_icon_frames(make_weapon(name),tex,[fx_frame(name,f%8)])[0].resize((64,64),Image.Resampling.NEAREST)
            draw.rectangle((i*300+112,485,i*300+182,555),fill='#242a36',outline='#99a6b9',width=2)
            sheet.alpha_composite(icon,(i*300+115,488))
        draw=ImageDraw.Draw(sheet);draw.text((30,20),'LEGENDARY RELICS  /  GEOMETRY + VFX PREVIEW',font=font(22,True),fill='#e7d9b9')
        frames.append(sheet.convert('RGB'))
    frames[0].save(OUT/'legendary-weapons.gif',save_all=True,append_images=frames[1:],duration=100,loop=0,optimize=False)
    (OUT/'index.html').write_text('''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Evergarden | Legendary weapons</title><style>body{margin:0;background:#080c17;color:#c8d6e8;font:16px system-ui}main{max-width:1500px;margin:auto;padding:30px}img{width:100%;height:auto}a{color:#76d6ff}p{line-height:1.7}</style><main><img src="legendary-weapons.png" alt="Five legendary weapon asset previews"><img src="legendary-weapons.gif" alt="Animated weapon VFX and hotbar preview"><p>Rendered from the same cuboids and textures exported to the packs. This is an asset preview, not a Minecraft screenshot. Java hotbar icons animate; Bedrock hotbar icons include a static signature effect. Both held models animate their VFX.</p><p>Try in game: <code>/magic weapons</code> (admin menu), <code>/magic weapon give mjolnir</code>.</p></main></html>''',encoding='utf8')
    print('Preview:',OUT/'legendary-weapons.png')


if __name__=='__main__':make_previews()
