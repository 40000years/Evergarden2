"""Preview the actual Judge model cuboids and bundled textures. Requires Pillow + numpy."""
from pathlib import Path
import io
import json
import math
import sys
import zipfile
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT=Path(__file__).resolve().parents[1]
def render(close=False,phase=1):
    shape=json.loads((ROOT/'evergarden/src/main/resources/judge-shape.yml').read_text())
    with zipfile.ZipFile(ROOT/'evergarden/dist/judge-java.zip') as pack:
        atlas=np.asarray(Image.open(io.BytesIO(pack.read('assets/voidscape/textures/item/judge_atlas.png'))).convert('RGB'))
    boxes=[]
    for name,mesh in shape.items():
        if close and not name.startswith('judge_mask'):continue
        if phase<3 and name in ('judge_shard','judge_heart'):continue
        if phase==3 and name.startswith(('judge_left','judge_right')):continue
        offsets=[]
        if name=='judge_shard':
            offsets=[np.array([math.cos(i*math.pi/6)*18,27+math.sin(i*math.pi/6)*13,4]) for i in range(12)]
        else:
            offsets=[np.array([-21 if name.startswith('judge_left') else 21 if name.startswith('judge_right') else 0,
                              1 if name.startswith(('judge_left','judge_right')) else 0 if name=='judge_heart' else 17,
                              -2 if name.startswith(('judge_left','judge_right')) else 0])+np.array(mesh.get('offset',[0,0,0]))]
        for offset in offsets:
            for c in mesh['cubes']:boxes.append((np.array(c['from'])+offset,np.array(c['size']),c['tile'],name.endswith('_light') or name=='judge_heart'))
    w,h=(1600,1400) if close else (2000,1600)
    az,el=math.radians(168),math.radians(8)
    cam=np.array([math.sin(az)*math.cos(el),math.sin(el),math.cos(az)*math.cos(el)])
    right=np.array([-math.cos(az),0,math.sin(az)]);up=np.array([-math.sin(el)*math.sin(az),math.cos(el),-math.sin(el)*math.cos(az)])
    proj=np.array([right,-up,cam]);sun=np.array([-.4,.75,-.6]);sun/=np.linalg.norm(sun)
    allpoints=np.array([p+size*np.array([a,b,c]) for p,size,_,_ in boxes for a in [0,1] for b in [0,1] for c in [0,1]])@proj.T
    lo=allpoints.min(axis=0);hi=allpoints.max(axis=0)
    scale=min(w*.89/(hi[0]-lo[0]),h*.75/(hi[1]-lo[1]));origin=np.array([w/2-scale*(lo[0]+hi[0])/2,h*.54-scale*(lo[1]+hi[1])/2])
    yy=np.arange(h)[:,None];xx=np.arange(w)[None,:]
    t=np.broadcast_to(yy/h,(h,w))
    canvas=np.array([12,18,31])*(1-t[...,None])+np.array([33,45,62])*t[...,None]
    depth=np.full((h,w),-1e10);glow=np.zeros((h,w))
    for p,size,tile,emits in boxes:
        for axis,ta,tb in [(0,1,2),(1,0,2),(2,0,1)]:
            sign=1 if cam[axis]>0 else -1
            a=p.copy();a[axis]+=size[axis] if sign>0 else 0
            e1=np.zeros(3);e2=np.zeros(3);e1[ta]=size[ta];e2[tb]=size[tb]
            q=(proj@a)[:2]*scale+origin;s1=(proj@e1)[:2]*scale;s2=(proj@e2)[:2]*scale
            points=np.array([q,q+s1,q+s2,q+s1+s2])
            x0=max(0,math.floor(points[:,0].min()));x1=min(w,math.ceil(points[:,0].max()))
            y0=max(0,math.floor(points[:,1].min()));y1=min(h,math.ceil(points[:,1].max()))
            det=s1[0]*s2[1]-s1[1]*s2[0]
            if x0>=x1 or y0>=y1 or abs(det)<1e-7:continue
            x=np.arange(x0,x1)[None,:]+.5-q[0];y=np.arange(y0,y1)[:,None]+.5-q[1]
            u=(x*s2[1]-y*s2[0])/det;v=(y*s1[0]-x*s1[1])/det
            z=cam@a+u*(cam@e1)+v*(cam@e2)
            mask=(u>=0)&(u<=1)&(v>=0)&(v<=1)&(z>depth[y0:y1,x0:x1])
            tx=np.clip((9+u*238).astype(int),9,246)+tile%4*256
            ty=np.clip((9+(1-v)*238).astype(int),9,246)+tile//4*256
            normal=np.zeros(3);normal[axis]=sign
            light=1.1 if emits else .62+.40*max(0,normal@sun)
            colors=atlas[ty,tx].astype(float)*light
            canvas[y0:y1,x0:x1][mask]=colors[mask];depth[y0:y1,x0:x1][mask]=z[mask]
            glow[y0:y1,x0:x1][mask]=1 if emits else 0
    def plane(name,center,right,up,width,height):
        with zipfile.ZipFile(ROOT/'evergarden/dist/judge-java.zip') as pack:
            texture=np.asarray(Image.open(io.BytesIO(pack.read(f'assets/voidscape/textures/effect/{name}.png'))).convert('RGBA'))
        a=np.array(center)-np.array(right)*width/2-np.array(up)*height/2
        e1=np.array(right)*width;e2=np.array(up)*height
        q=(proj@a)[:2]*scale+origin;s1=(proj@e1)[:2]*scale;s2=(proj@e2)[:2]*scale
        points=np.array([q,q+s1,q+s2,q+s1+s2]);det=s1[0]*s2[1]-s1[1]*s2[0]
        if abs(det)<1e-7:return
        x0=max(0,math.floor(points[:,0].min()));x1=min(w,math.ceil(points[:,0].max()))
        y0=max(0,math.floor(points[:,1].min()));y1=min(h,math.ceil(points[:,1].max()))
        if x0>=x1 or y0>=y1:return
        x=np.arange(x0,x1)[None,:]+.5-q[0];y=np.arange(y0,y1)[:,None]+.5-q[1]
        u=(x*s2[1]-y*s2[0])/det;v=(y*s1[0]-x*s1[1])/det
        z=cam@a+u*(cam@e1)+v*(cam@e2)
        pixels=texture[np.clip(((1-v)*1023).astype(int),0,1023),np.clip((u*1023).astype(int),0,1023)]
        mask=(u>=0)&(u<=1)&(v>=0)&(v<=1)&(z>depth[y0:y1,x0:x1]);alpha=pixels[...,3:4]/255*mask[...,None]
        canvas[y0:y1,x0:x1]=canvas[y0:y1,x0:x1]*(1-alpha)+pixels[...,:3]*alpha
    if not close:
        if phase==3:plane('judge_sanctuary',[0,.14,0],[1,0,0],[0,0,1],6.4*512/480,6.4*512/480)
        else:
            for side in (-1,1):plane('judge_seal',[side*21,.1,-2],[1,0,0],[0,0,1],22*512/480,22*512/480)
    img=Image.fromarray(np.clip(canvas,0,255).astype('uint8'))
    emiss=Image.fromarray((glow*255).astype('uint8')).filter(ImageFilter.GaussianBlur(12))
    halo=Image.new('RGB',(w,h),(255,52,80));halo.putalpha(emiss.point(lambda x:int(x*.20)))
    img=Image.alpha_composite(img.convert('RGBA'),halo).convert('RGB')
    draw=ImageDraw.Draw(img)
    fontpath=Path('C:/Windows/Fonts/seguisb.ttf');smallpath=Path('C:/Windows/Fonts/segoeui.ttf')
    title=ImageFont.truetype(str(fontpath),44);small=ImageFont.truetype(str(smallpath),23)
    draw.text((w*.07,h*.055),'THE BODILESS JUDGE',font=title,fill=(235,214,171))
    draw.text((w*.07,h*.10),'Final phase  /  temple sovereigns  /  exposed heart' if phase==3 else 'Colossal cathedral wings  /  suspended mantle  /  ivory sovereign',font=small,fill=(163,183,199))
    draw.text((w*.07,h*.935),'Actual authored model + 1024px atlas. Simulated lighting; not an in-game screenshot.',font=small,fill=(143,165,189))
    out=ROOT/'previews'/('crimson-judge-final.png' if phase==3 else 'ivory-judge-detail.png' if close else 'ivory-judge.png');out.parent.mkdir(exist_ok=True);img.save(out)
    print(out)

if __name__=='__main__':render();render(True);render(False,3)
