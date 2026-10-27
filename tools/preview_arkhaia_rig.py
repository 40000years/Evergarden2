"""Render the actual editable rig with hierarchical joints, without a game client."""
import base64
import io
import json
import math
from pathlib import Path
import sys
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1]
try:import numpy as np
except ModuleNotFoundError:
    sys.path.insert(0,str(ROOT/'output/faction-bosses/render-packages'));import numpy as np
MODEL=json.loads((ROOT/'evergarden/src/main/resources/modelengine/arkhaia.bbmodel').read_text())
TEXTURE=np.asarray(Image.open(io.BytesIO(base64.b64decode(MODEL['textures'][0]['source'].split(',')[1]))).convert('RGBA'))
ELEMENTS={e['uuid']:e for e in MODEL['elements']}
CLIPS={c['name']:c for c in MODEL['animations']}
OUT=ROOT/'previews/arkhaia-model-engine';OUT.mkdir(parents=True,exist_ok=True)

def combat_timing(action):
    source=dict(line.split('=',1) for line in (ROOT/'evergarden/src/main/resources/modelengine/arkhaia-combat.properties').read_text().splitlines()
                if line and not line.startswith('#'))
    return [int(v)/20 for v in source[action].split(',')]

def rotation(v):
    x,y,z=map(math.radians,v);cx,sx,cy,sy,cz,sz=math.cos(x),math.sin(x),math.cos(y),math.sin(y),math.cos(z),math.sin(z)
    return np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])

def sample(anim,time,channel,position_x_sign=1):
    frames=sorted([f for f in anim.get('keyframes',[]) if f['channel']==channel],key=lambda f:f['time'])
    if not frames:return np.zeros(3)
    def vector(f):
        value=np.array([float(f['data_points'][0].get(a,0)) for a in 'xyz'])
        # Match the handedness used by ME's Blockbench animation importer.
        return value*np.array([-1,-1,1]) if channel=='rotation' else value*np.array([position_x_sign,1,1]) if channel=='position' else value
    if time<=frames[0]['time']:return vector(frames[0])
    for a,b in zip(frames,frames[1:]):
        if a['time']<=time<=b['time']:
            t=(time-a['time'])/(b['time']-a['time']);return vector(a)*(1-t)+vector(b)*t
    return vector(frames[-1])

def transformed(clip,time,gaze=None):
    result=[];animators=CLIPS[clip]['animators']
    def walk(group,parent):
        if group['name']=='hitbox':return
        o=np.array(group['origin']);a=animators.get(group['uuid'],{})
        r=rotation(sample(a,time,'rotation'));matrix=np.eye(4);matrix[:3,:3]=r;matrix[:3,3]=o-r@o+sample(a,time,'position',-1 if MODEL.get('meta',{}).get('evergarden_animation_position_x')=='inverted' else 1);matrix=parent@matrix
        if group['name']=='hi_gaze' and gaze is not None:
            pivot=(parent@np.append(o,1))[:3];look=rotation([-gaze[1],-gaze[0],0]);offset=np.eye(4)
            offset[:3,:3]=look;offset[:3,3]=pivot-look@pivot;matrix=offset@matrix
        for child in group['children']:
            if isinstance(child,dict):walk(child,matrix)
            else:result.append((ELEMENTS[child],matrix))
    for g in MODEL['outliner']:walk(g,np.eye(4))
    return result

def render(clip='idle',time=0,yaw=17,elevation=7,size=650,gaze=None,span=57):
    az,el=math.radians(yaw),math.radians(elevation)
    camera=np.array([math.sin(az)*math.cos(el),math.sin(el),-math.cos(az)*math.cos(el)])
    proj=np.array([[math.cos(az),0,math.sin(az)],[-math.sin(el)*math.sin(az),math.cos(el),math.sin(el)*math.cos(az)],camera]);proj[1]*=-1
    light=np.array([-.35,.6,-.72]);light/=np.linalg.norm(light)
    canvas=np.zeros((size,size,3),dtype=float);canvas[:]=[23,21,34];depth=np.full((size,size),-1e8)
    scale=(size-85)/span;origin=np.array([size/2,size*.83])
    for cube,matrix in transformed(clip,time,gaze):
        x0,y0,z0=cube['from'];x1,y1,z1=cube['to'];rot=matrix[:3,:3]@rotation(cube.get('rotation',[0,0,0]));pivot=np.array(cube.get('origin',[0,0,0]))
        for face,normal,verts in (
            ('north',(0,0,-1),[(x0,y1,z0),(x1,y1,z0),(x1,y0,z0),(x0,y0,z0)]),
            ('south',(0,0,1),[(x1,y1,z1),(x0,y1,z1),(x0,y0,z1),(x1,y0,z1)]),
            ('east',(1,0,0),[(x1,y1,z0),(x1,y1,z1),(x1,y0,z1),(x1,y0,z0)]),
            ('west',(-1,0,0),[(x0,y1,z1),(x0,y1,z0),(x0,y0,z0),(x0,y0,z1)]),
            ('up',(0,1,0),[(x0,y1,z1),(x1,y1,z1),(x1,y1,z0),(x0,y1,z0)]),
            ('down',(0,-1,0),[(x0,y0,z0),(x1,y0,z0),(x1,y0,z1),(x0,y0,z1)])):
            normal=rot@normal
            if normal@camera<=0:continue
            verts=(np.array(verts)-pivot)@rotation(cube.get('rotation',[0,0,0])).T+pivot
            verts=verts@matrix[:3,:3].T+matrix[:3,3];points=verts@proj.T
            screen=points[:,:2]*scale+origin;a=screen[0];b=screen[1]-a;c=screen[3]-a
            left,top=np.maximum(0,np.floor(screen.min(axis=0)).astype(int));right,bottom=np.minimum(size,np.ceil(screen.max(axis=0)+1).astype(int))
            if right<=left or bottom<=top:continue
            det=b[0]*c[1]-b[1]*c[0]
            if abs(det)<1e-5:continue
            px=np.arange(left,right)[None,:]+.5-a[0];py=np.arange(top,bottom)[:,None]+.5-a[1]
            u=(px*c[1]-py*c[0])/det;v=(py*b[0]-px*b[1])/det
            z=points[0,2]+u*(points[1,2]-points[0,2])+v*(points[3,2]-points[0,2])
            target_depth=depth[top:bottom,left:right];mask=(u>=0)&(u<=1)&(v>=0)&(v<=1)&(z>target_depth)
            uv=cube['faces'][face]['uv'];tx=np.clip((uv[0]+u*(uv[2]-uv[0])).astype(int),0,TEXTURE.shape[1]-1);ty=np.clip((uv[1]+v*(uv[3]-uv[1])).astype(int),0,TEXTURE.shape[0]-1)
            texels=TEXTURE[ty,tx];mask&=texels[:,:,3]>0
            shade=.83+.22*max(0,float(normal@light)) if cube.get('shade',True) else 1
            canvas[top:bottom,left:right][mask]=np.clip(texels[:,:,:3]*shade,0,255)[mask];target_depth[mask]=z[mask]
    return Image.fromarray(canvas.astype('uint8'))

def main():
    font=ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',22)
    look=Image.new('RGB',(1500,560),(23,21,34))
    for i,yaw in enumerate((-45,0,45)):
        look.paste(render('idle',0,yaw=0,size=500,gaze=(yaw,32)),(i*500,0))
        ImageDraw.Draw(look).text((i*500+15,515),f'Head yaw {yaw} / pitch 32',font=font,fill=(223,195,255))
    look.save(OUT/'arkhaia-gaze.png')
    for filename,views in (
        ('arkhaia-sword-direction.png',[('heavy_windup',CLIPS['heavy_windup']['length']),('heavy_active',combat_timing('heavy')[2]*.35),('heavy_active',combat_timing('heavy')[2])]),
        ('arkhaia-casting-direction.png',[('rift_windup',CLIPS['rift_windup']['length']),('rift_active',CLIPS['rift_active']['length'])])):
        side=Image.new('RGB',(len(views)*500,560),(23,21,34));labels=ImageDraw.Draw(side)
        for i,(name,time) in enumerate(views):
            side.paste(render(name,time,yaw=90,size=500),(i*500,0))
            labels.text((i*500+15,12),'FRONT <---  |  ---> BEHIND',font=font,fill=(223,195,255))
            labels.text((i*500+15,515),name,font=font,fill=(223,195,255))
        side.save(OUT/filename)
    views=[('idle',.6),('walk',.2),('heavy_windup',CLIPS['heavy_windup']['length']),('heavy_active',combat_timing('heavy')[2]),('rift_windup',CLIPS['rift_windup']['length']),('hover',.35)]
    sheet=Image.new('RGB',(1800,1300),(23,21,34));draw=ImageDraw.Draw(sheet)
    for i,(clip,t) in enumerate(views):
        frame=render(clip,t,size=600);sheet.paste(frame,(i%3*600,i//3*650));draw.text((i%3*600+20,i//3*650+610),clip,font=font,fill=(223,195,255))
    sheet.save(OUT/'arkhaia-rig-poses.png');render().save(OUT/'arkhaia-rig-idle.png')
    timeline=[(name,CLIPS[name]['length']) for name in ('idle','walk','light_windup','light_active','light_recover','heavy_windup','heavy_active','heavy_recover','hover','rift_windup','rift_active','rift_recover')]
    frames=[]
    for clip,length in timeline:
        for t in np.arange(0,length,.05):
            frame=render(clip,float(t),size=500);d=ImageDraw.Draw(frame);d.text((14,14),clip,font=font,fill=(223,195,255));frames.append(frame)
    frames[0].save(OUT/'arkhaia-rig-combat.gif',save_all=True,append_images=frames[1:],duration=50,loop=0,optimize=True)
    print(OUT)

if __name__=='__main__':main()
