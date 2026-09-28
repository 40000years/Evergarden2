"""Render the shipped model JSON using poses exported by the real Java rig.

Pillow only. These are geometry previews with simulated lighting, not screenshots.
"""
import argparse
import json
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import boss_designs as designs

ROOT=Path(__file__).resolve().parents[1]
PACK=ROOT/'art/java/assets/sevensins/models'


def rotate(p,q):
    x,y,z=p; a,b,c,w=q
    tx,ty,tz=2*(b*z-c*y),2*(c*x-a*z),2*(a*y-b*x)
    return (x+w*tx+b*tz-c*ty,y+w*ty+c*tx-a*tz,z+w*tz+a*ty-b*tx)


def unit(v):
    length=math.sqrt(sum(c*c for c in v))
    return tuple(c/length for c in v)


def faces(sin,pose,yaw=-23):
    result=[]
    theta=math.radians(yaw)
    light=unit((-.5,.85,-.65))
    for bone,transform in pose['bones'].items():
        mesh=json.loads((PACK/sin/f'{bone}.json').read_text())
        def vertex(p,cube):
            if 'rotation' in cube:
                r=cube['rotation']; angle=math.radians(r['angle'])/2
                axis={'x':(1,0,0),'y':(0,1,0),'z':(0,0,1)}[r['axis']]
                q=tuple(v*math.sin(angle) for v in axis)+(math.cos(angle),)
                p=tuple(v+o for v,o in zip(rotate([v-o for v,o in zip(p,r['origin'])],q),r['origin']))
            p=rotate([(v-8)/16 for v in p],transform['rotation'])
            x,y,z=[v+o for v,o in zip(p,transform['position'])]
            return (x*math.cos(theta)-z*math.sin(theta),y,x*math.sin(theta)+z*math.cos(theta))
        for cube in mesh['elements']:
            a,b=cube['from'],cube['to']
            pts=[vertex(p,cube) for p in ((a[0],a[1],a[2]),(b[0],a[1],a[2]),(b[0],b[1],a[2]),(a[0],b[1],a[2]),
                                       (a[0],a[1],b[2]),(b[0],a[1],b[2]),(b[0],b[1],b[2]),(a[0],b[1],b[2]))]
            for name,ids in (('north',(0,3,2,1)),('south',(4,5,6,7)),('west',(0,4,7,3)),
                             ('east',(1,2,6,5)),('up',(3,7,6,2)),('down',(0,1,5,4))):
                vertices=[pts[i] for i in ids]
                u=[vertices[1][i]-vertices[0][i] for i in range(3)]
                v=[vertices[2][i]-vertices[0][i] for i in range(3)]
                normal=unit((u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]))
                if normal[1]*.28-normal[2]<=0: continue
                material=cube['faces'][name]['texture'][1:]
                shade=.57+.54*max(0,sum(a*b for a,b in zip(normal,light)))
                if bone=='core': shade=max(shade,.96)
                color=tuple(min(255,round(c*shade)) for c in designs.PALETTES[sin][material])
                depth=sum(z-y*.28 for x,y,z in vertices)/4
                result.append((depth,[(x,-y+z*.28) for x,y,z in vertices],color))
    return sorted(result,key=lambda f:f[0],reverse=True)


def font(size,bold=False):
    try: return ImageFont.truetype('C:/Windows/Fonts/'+('arialbd.ttf' if bold else 'arial.ttf'),size)
    except OSError: return ImageFont.load_default(size=size)


def render(sin,pose,size=(800,850),yaw=23):
    factor=2; w,h=size
    image=Image.new('RGB',(w*factor,h*factor),(13,16,21)); draw=ImageDraw.Draw(image)
    accent=designs.PALETTES[sin]['ember']
    for radius in range(int(w*.65),0,-3):
        k=(1-radius/(w*.65))*.14
        color=tuple(round(c*(1-k)+d*k) for c,d in zip((13,16,21),accent))
        draw.ellipse(((w/2-radius)*factor,(h*.52-radius)*factor,(w/2+radius)*factor,(h*.52+radius)*factor),fill=color)
    polys=faces(sin,pose,yaw)
    xs=[x for _,verts,_ in polys for x,y in verts]; ys=[y for _,verts,_ in polys for x,y in verts]
    scale=min((w-90)/(max(xs)-min(xs)),(h-230)/(max(ys)-min(ys)))
    cx=w/2-(max(xs)+min(xs))*scale/2
    cy=h-96
    draw.ellipse(((w*.22)*factor,(h-112)*factor,(w*.78)*factor,(h-63)*factor),fill='#090c11')
    for _,vertices,color in polys:
        points=[((cx+x*scale)*factor,(cy+y*scale)*factor) for x,y in vertices]
        draw.polygon(points,fill=color)
        # Subtle contact edges rather than black outlines around every block.
        edge=tuple(round(c*.73) for c in color)
        draw.line(points+[points[0]],fill=edge,width=1)
    draw.text((38*factor,27*factor),sin.upper(),font=font(37*factor,True),fill=designs.PALETTES[sin]['bone'])
    draw.text((40*factor,74*factor),designs.TITLES[sin],font=font(14*factor),fill='#a5a9b1')
    draw.line((40*factor,105*factor,(w-40)*factor,105*factor),fill=accent,width=2*factor)
    draw.text((40*factor,(h-43)*factor),'ACTUAL PACK GEOMETRY / SIMULATED LIGHTING',font=font(11*factor),fill='#777f8e')
    return image.resize(size,Image.Resampling.LANCZOS)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--poses',type=Path,default=ROOT/'art/model-rest-pose.json')
    args=parser.parse_args()
    data=json.loads(args.poses.read_text())
    pose=next(f for f in data if f['clip']=='slam' and f['tick']==0) if isinstance(data,list) else data
    (ROOT/'art/model-rest-pose.json').write_text(json.dumps(pose,indent=2)+'\n')
    roster=Image.new('RGB',(2400,1400),'#090c11')
    for i,sin in enumerate(designs.PALETTES):
        sin_pose=pose[sin] if sin in pose else pose
        portrait=render(sin,sin_pose)
        portrait.save(ROOT/f'art/{sin}-design.png')
        roster.paste(render(sin,sin_pose,(600,700)),((i%4)*600,(i//4)*700))
    draw=ImageDraw.Draw(roster)
    draw.text((1850,945),'7 SINS',font=font(64,True),fill='#d8c6ad')
    draw.text((1853,1030),'DARK FANTASY REDESIGN',font=font(20),fill='#ac9171')
    draw.text((1853,1090),'Original Minecraft cube models',font=font(18),fill='#8e96a4')
    draw.text((1853,1120),'Nine animated bones per boss',font=font(18),fill='#8e96a4')
    draw.text((1853,1170),'Geometry preview, not a game screenshot.',font=font(16),fill='#676f7d')
    output=ROOT/'art/seven-sins-designs.png'; roster.save(output); print(output)
