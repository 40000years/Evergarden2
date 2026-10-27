"""Authored two-seat flying staff: matching Java, Bedrock and editable cube sources.

Run this before build_tandem_flight_release.py. Existing tier-one assets are untouched.
The Java head model stretches Z by 1.5; seat centers are 21.6 world model units apart.
"""
from pathlib import Path
import copy, json, math, random, uuid
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[1]
ART=ROOT/'advance-magic/art/flying-staff'
NAME='flying_staff_tier2'
COLORS=['#d9d5b7','#f5efd7','#b78940','#edca71','#264450','#3e7180','#64d5df','#c1ffff',
        '#332a3e','#584052','#92624f','#ce9a70','#102c39','#287890','#36afc6','#fff6b3']
PARTS=[]

def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')

def cube(name,a,b,color):
    PARTS.append((name,list(a),list(b),color))

def design():
    cube('ivory keel',(6.9,6.9,-9),(9.1,9.1,30),0)
    cube('top inlay',(7.7,9.12,-8),(8.3,9.35,30),3)
    cube('bottom blue channel',(7.5,6.5,-8),(8.5,6.88,29),4)
    for z in [-7,-3,1,12,17,28]:
        cube('gold collar',(6.55,6.55,z),(9.45,9.45,z+.75),2)
        cube('collar glow',(6.42,7.5,z+.15),(6.56,8.5,z+.55),6)
        cube('collar glow',(9.44,7.5,z+.15),(9.58,8.5,z+.55),6)
    for z in [8,22.4]:
        cube('saddle gold frame',(4.7,8.65,z-2.7),(11.3,9.35,z+2.7),2)
        cube('saddle leather',(5.1,9.35,z-2.3),(10.9,9.95,z+2.3),8)
        cube('saddle piping',(5.1,9.96,z-2.3),(10.9,10.2,z-1.85),11)
        cube('saddle piping',(5.1,9.96,z+1.85),(10.9,10.2,z+2.3),11)
        cube('saddle inset',(5.75,9.97,z-1.6),(10.25,10.06,z+1.6),9)
        for x in [5.15,10.4]:
            cube('saddle rivet',(x,10.03,z-1.7),(x+.45,10.3,z-1.25),3)
            cube('saddle rivet',(x,10.03,z+1.25),(x+.45,10.3,z+1.7),3)
        for x in [3.8,11.5]:
            cube('footrest support',(x,3.8,z-.4),(x+.7,8.9,z+.4),2)
        cube('footrest left',(2.5,3.6,z-1.4),(4.5,4.15,z+1.4),4)
        cube('footrest right',(11.5,3.6,z-1.4),(13.5,4.15,z+1.4),4)
    cube('pilot handle stem',(7.5,9.1,3.3),(8.5,13.2,4.2),2)
    cube('pilot handlebar',(3.8,12.6,3.1),(12.2,13.4,4.3),3)
    for x in [3.4,10.8]:cube('wrapped grip',(x,12.4,2.9),(x+1.8,13.6,4.5),4)
    cube('passenger handhold stem',(7.5,9.3,18.4),(8.5,12.3,19.2),2)
    cube('passenger handhold',(4.8,11.8,18.2),(11.2,12.55,19.4),3)
    # Tier-two's pronged luminous prow and paired swept feather wings.
    for z,width,height,col in [(-15,1,1,7),(-14,1.8,1.8,6),(-12.5,2.4,2.6,6),(-10.5,1.7,2,7)]:
        cube('azure flight crystal',(8-width,8-height,z),(8+width,8+height,z+1.6),col)
    for sign in [-1,1]:
        for i in range(5):
            x=8+sign*(2.5+i*1.15)
            cube('gilded swept feather',(x-.5,7.2+i*.22,-9+i*1.6),(x+.5,8.8+i*.22,-3+i*2.1),2 if i%2==0 else 0)
            cube('feather cyan tip',(x-.35,8.82+i*.22,-9+i*1.6),(x+.35,9.07+i*.22,-7.5+i*1.6),6)
        for i in range(3):
            x=8+sign*(2+i*1.3)
            cube('tail feathers',(x-.45,7.25,26+i),(x+.45,8.75,30.8+i*.3),4 if i%2==0 else 3)
    cube('tail crystal',(7.15,7.15,29.5),(8.85,8.85,31.8),6)
    for z in [-1,14.5,26]:
        cube('rune socket',(7.15,9.18,z),(8.85,9.52,z+1.4),4)
        cube('rune light',(7.55,9.53,z+.25),(8.45,9.7,z+1.1),7)

def texture():
    tex=Image.new('RGBA',(64,64));d=ImageDraw.Draw(tex);rng=random.Random(215)
    for i,col in enumerate(COLORS):
        x=(i%8)*8;y=(i//8)*8;d.rectangle((x,y,x+7,y+7),fill=col)
        rgb=tuple(int(col[j:j+2],16) for j in [1,3,5])
        for _ in range(12):
            delta=rng.choice([-9,9]);c=tuple(max(0,min(255,v+delta)) for v in rgb)
            d.point((x+rng.randrange(8),y+rng.randrange(8)),fill=c)
    return tex

def faces(color):
    x=(color%8)*2;y=(color//8)*2
    return {face:{'uv':[x+.25,y+.25,x+1.75,y+1.75],'texture':'#palette'} for face in ['north','south','east','west','up','down']}

def preview(path,icon=False):
    w,h=(256,256) if icon else (1600,820)
    im=Image.new('RGBA',(w,h),(0,0,0,0) if icon else '#101c29');d=ImageDraw.Draw(im)
    def render(cx,cy,zoom,yaw):
        ca,sa=math.cos(yaw),math.sin(yaw)
        def projection(v):
            x,y,z=v;x-=8;y-=8;z=(z-8)*1.5
            u=ca*x+sa*z;depth=-sa*x+ca*z
            return (cx+u*zoom,cy+(depth*.38-y)*zoom),depth+y*.1
        polys=[]
        for _,a,b,c in PARTS:
            verts=[[x,y,z] for x in [a[0],b[0]] for y in [a[1],b[1]] for z in [a[2],b[2]]]
            for inds,shade in [([0,1,3,2],.65),([4,6,7,5],.83),([2,3,7,6],1.08),([1,5,7,3],.9),([0,2,6,4],.75)]:
                points=[projection(verts[i]) for i in inds];rgb=COLORS[c];color=tuple(min(255,int(int(rgb[k:k+2],16)*shade)) for k in [1,3,5])
                polys.append((sum(p[1] for p in points)/4,[p[0] for p in points],color))
        for _,pts,col in sorted(polys,key=lambda p:p[0]):d.polygon(pts,fill=col)
    if icon:render(128,132,3.05,-.83)
    else:
        try:f=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',32);small=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',21)
        except OSError:f=small=ImageFont.load_default()
        d.text((65,44),'AETHERWING  /  FLYING STAFF II',font=f,fill='#f1d58d')
        d.text((65,95),'Ivory keel · Azure crystal · Twin leather saddles · Gilded feather prow',font=small,fill='#9ab6c6')
        render(795,325,15,-.97);render(810,630,13,-1.48)
        d.text((65,747),'2 seats     /     50% flight speed     /     150% mana     /     Java + Bedrock',font=small,fill='#82e3e6')
    path.parent.mkdir(parents=True,exist_ok=True);im.save(path)

def main():
    design();tex=texture()
    java=ART/'java/assets/advance_magic';bed=ART/'bedrock'
    for p in [java/f'textures/item/{NAME}_palette.png',bed/f'textures/items/{NAME}_palette.png']:
        p.parent.mkdir(parents=True,exist_ok=True);tex.save(p)
    preview(ART/f'{NAME}-preview.png')
    icon=ART/f'{NAME}-icon.png';preview(icon,True)
    for p in [java/f'textures/item/{NAME}.png',bed/f'textures/items/{NAME}.png']:p.write_bytes(icon.read_bytes())
    old=json.loads((java/'models/item/flying_staff.json').read_text())
    model={'credit':'Evergarden Aetherwing: two seats at z=8 and z=22.4; head Z scale 1.5',
           'textures':{'palette':f'advance_magic:item/{NAME}_palette','particle':f'advance_magic:item/{NAME}_palette'},
           'elements':[{'name':n,'from':a,'to':b,'faces':faces(c)} for n,a,b,c in PARTS], 'display':copy.deepcopy(old['display'])}
    for context,transform in model['display'].items():
        scale=transform.get('scale',[1,1,1]);transform['scale']=[scale[0],scale[1],scale[2]*1.5]
    write(java/f'models/item/{NAME}.json',model)
    write(java/f'models/item/{NAME}_icon.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'advance_magic:item/{NAME}'}})
    held=json.loads((java/'items/flying_staff.json').read_text());write(java/f'items/{NAME}.json',json.loads(json.dumps(held).replace('flying_staff',NAME)))
    for state in ['summon','idle','flight','dismiss']:
        data=json.loads((java/f'items/flying_staff_{state}.json').read_text())
        write(java/f'items/{NAME}_{state}.json',json.loads(json.dumps(data).replace('flying_staff',NAME)))
        for p in (java/'models/item/flying_staff').glob(state+'_*.json'):
            frame=json.loads(p.read_text());frame['parent']=f'advance_magic:item/{NAME}'
            scale=frame['display']['head'].get('scale',[1,1,1]);frame['display']['head']['scale']=[scale[0],scale[1],scale[2]*1.5]
            write(java/f'models/item/{NAME}/{p.name}',frame)
    for held_mode in [False,True]:
        suffix='held' if held_mode else 'display';cubes=[]
        for n,a,b,c in PARTS:
            if held_mode:
                origin=[(a[0]-8)*.65,24-(b[2]-8)*.975,(a[1]-8)*.65]
                size=[(b[0]-a[0])*.65,(b[2]-a[2])*.975,(b[1]-a[1])*.65]
            else:origin=[a[0]-8,a[1]+16,(a[2]-8)*1.5];size=[b[0]-a[0],b[1]-a[1],(b[2]-a[2])*1.5]
            x=(c%8)*8+1;y=(c//8)*8+1
            cubes.append({'origin':origin,'size':size,'uv':{f:{'uv':[x,y],'uv_size':[6,6]} for f in ['north','south','east','west','up','down']}})
        bone={'name':'staff_root','pivot':[0,24,0],'cubes':cubes,'locators':{'tail':[0,24,35.7],'crystal_tip':[0,24,-34.5],'grip':[0,24,0]}}
        if held_mode:bone['binding']='q.item_slot_to_bone_name(context.item_slot)'
        else:bone['parent']='head'
        geo={'format_version':'1.16.0','minecraft:geometry':[{'description':{'identifier':f'geometry.advance_magic.{NAME}_{suffix}','texture_width':64,'texture_height':64,'visible_bounds_width':7,'visible_bounds_height':5,'visible_bounds_offset':[0,1.5,0]},'bones':([{'name':'head','pivot':[0,24,0]}] if not held_mode else [])+[bone]}]}
        write(bed/f'models/entity/{NAME}_{suffix}.geo.json',geo)
    for suffix in ['', '_summon','_idle','_flight','_dismiss']:
        attach=json.loads((bed/f'attachables/flying_staff{suffix}.json').read_text());desc=attach['minecraft:attachable']['description']
        desc['identifier']=f'advance_magic:{NAME}{suffix}';desc['textures']['default']=f'textures/items/{NAME}_palette'
        desc['geometry']['default']=f'geometry.advance_magic.{NAME}_'+('display' if suffix else 'held')
        write(bed/f'attachables/{NAME}{suffix}.json',attach)
    atlas=json.loads((bed/'textures/item_texture.json').read_text());atlas['texture_data'][f'advance_magic.{NAME}']={'textures':f'textures/items/{NAME}'};write(bed/'textures/item_texture.json',atlas)
    mappings=json.loads((ART/'geyser-mappings.json').read_text())
    for material in ['minecraft:blaze_rod','minecraft:carved_pumpkin']:
        originals=[r for r in mappings['items'][material] if r['model'].startswith('advance_magic:flying_staff') and 'tier2' not in r['model']]
        mappings['items'][material]=[r for r in mappings['items'][material] if 'flying_staff_tier2' not in r['model']]
        for row in originals:
            row=json.loads(json.dumps(row).replace('flying_staff',NAME));row['display_name']=row['display_name'].replace('Flying Staff','Flying Staff II');mappings['items'][material].append(row)
    write(ART/'geyser-mappings.json',mappings)
    # Blockbench's Java cube model with an embedded texture, for later hand editing.
    import base64
    elements=[dict(e,uuid=str(uuid.uuid5(uuid.NAMESPACE_URL,NAME+str(i))),type='cube',origin=[8,8,8]) for i,e in enumerate(model['elements'])]
    for e in elements:
        for face in e['faces'].values():
            face['texture']=0
            face['uv']=[v*4 for v in face['uv']]
    write(ART/f'{NAME}.bbmodel',{'meta':{'format_version':'4.10','model_format':'java_block'},'name':NAME,'resolution':{'width':64,'height':64},'elements':elements,'outliner':[e['uuid'] for e in elements],'textures':[{'name':NAME+'_palette.png','id':'0','width':64,'height':64,'source':'data:image/png;base64,'+base64.b64encode((java/f'textures/item/{NAME}_palette.png').read_bytes()).decode()}],'display':model['display']})
    print(f'Generated {len(PARTS)} cubes, all animation states, Java + Bedrock, Blockbench and preview')

if __name__=='__main__':main()
