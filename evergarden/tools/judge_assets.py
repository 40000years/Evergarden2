"""Original sculpted Ancient Judge. Shared editable mesh for Java, Bedrock and fallback.

Shared sculpted geometry and original crimson seals inspired by the Solar/Chronos pipeline.
The Java addon is independent of the pinned published relic pack.
"""
import hashlib
import json
import math
from pathlib import Path
import struct
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[1]
TILES = ['ivory', 'stone', 'gold', 'bronze', 'abyss', 'cyan', 'violet', 'rune']
PALETTE = [(223,215,186),(157,155,146),(214,169,76),(102,77,46),
           (31,24,36),(255,134,114),(238,62,94),(208,213,197)]
FALLBACK = ['SMOOTH_QUARTZ','POLISHED_TUFF','RAW_GOLD_BLOCK','WAXED_COPPER_BLOCK',
            'CRYING_OBSIDIAN','OCHRE_FROGLIGHT','REDSTONE_BLOCK','CHISELED_QUARTZ_BLOCK']
BEDROCK_VERSION = [3,19,0]
DESIGN_REVISION = 'cathedral-wings-v2'

def meshes():
    result = {}
    def new(name,scale,center):
        result[name]={'scale':scale,'center':center,'cubes':[]}
        return result[name]['cubes']
    def box(out,x,y,z,w,h,d,material,detail=False):
        out.append({'from':[round(x-w/2,4),round(y,4),round(z-d/2,4)],
                    'size':[w,h,d],'tile':TILES.index(material),'detail':detail})
    mask=new('judge_mask',16,[0,11,0]); light=new('judge_mask_light',16,[0,11,0])
    # A solid, tapered ivory death mask, with recessed sockets and a projecting nose.
    widths=[2.8,4.0,5.0,6.0,6.8,7.4,8.0,8.3,8.6,8.9,9.0,8.9,8.7,8.2,7.8,7.2,6.4,5.4]
    for row,w in enumerate(widths):
        y=1+row
        depth=2.1+1.6*math.sin(row/17*math.pi)
        box(mask,0,y,.9,2*w,1.02,depth,'stone')
        # Surface contours give cheeks and forehead thickness, not a flat outline.
        if 10<=row<=12:
            gap=1.0; outside=6.6
            box(mask,0,y,-1.4,2*gap,1.04,1.6,'ivory')
            for s in [-1,1]:box(mask,s*(w+outside)/2,y,-1.1,w-outside,1.04,1.5,'ivory')
        else:
            box(mask,0,y,-1.05,2*w-.25,1.03,1.25+depth*.15,'ivory')
    for s in [-1,1]:
        # Angular sunken eyes, strong upper brows, layered carved cheek plates.
        box(mask,s*3.85,11,-1.45,5.4,2.65,.6,'abyss')
        box(light,s*3.85,11.8,-1.8,3.7,.4,.16,'cyan')
        box(light,s*3.4,11.2,-1.81,.35,1.1,.18,'cyan')
        for i in range(5):
            box(mask,s*(1.7+i),14-i*.19,-2.05,1.15,.85,1.45,'gold')
        for i in range(4):
            box(mask,s*(5.2+i*.7),7+i*.75,-1.9,1.15,2.0,.7,'rune')
            box(mask,s*(5.1+i*.7),6.9+i*.75,-2.29,.1,1.8,.08,'bronze',True)
        # Two nested, open pointed wing plates; deliberately not magic circles.
        for i in range(6):
            x=s*(10.1+i*.6);y=8+i*2.05
            box(mask,x,y,1.4,1.0,4.6-i*.28,1.2,'bronze')
            box(mask,x+s*.18,y+.2,.66,.34,4.1-i*.28,.15,'gold',True)
            box(light,x,y+.8,.55,.13,2.5,.1,'violet')
        box(mask,s*9.35,16.5,-.3,2.0,4.8,2.1,'gold')
        for i in range(3):
            box(mask,s*(10.2+i*1.5),18.5+i*.8,.9,1.7,2.7-i*.35,1.2,'ivory')
    # Carved nose and narrow mouth, no grinning block-monster face.
    box(mask,0,7.6,-2.2,1.5,5.1,2.3,'rune')
    box(mask,0,7.1,-2.9,2.25,.75,1.65,'ivory')
    box(mask,0,4.1,-1.95,6.6,.35,.28,'abyss')
    box(mask,0,3.1,-2.04,4.5,.45,.23,'gold')
    # The original broken crown: seven stepped, pointed spires.
    for i in range(-3,4):
        x=i*2.5;height=8.0-abs(i)*1.25
        for j in range(4):
            box(mask,x,18+j*height/4,.4,max(.32,1.7-j*.42),height/4+.06,1.25-j*.19,'gold' if j%2 else 'bronze')
        box(light,x,19,-.35,.14,height-1.2,.1,'cyan')
    # Chiseled forehead diamond and offset hairline fractures.
    for j in range(5):
        w=2.4-abs(j-2)*.8
        box(mask,0,15.0+j*.5,-2.05,w,.52,.18,'gold')
    box(light,0,15.65,-2.18,.65,1.55,.12,'violet')
    for side in [-1,1]:
        for j in range(9):
            x=side*(7.2-j*.22+(j%3)*.19)
            box(mask,x,5.2+j*.8,-2.09,.20,.88,.12,'abyss',True)
            box(light,x,5.22+j*.8,-2.17,.075,.8,.05,'cyan')
    # Layered cathedral crown and flowing cheek ornaments sharpen the silhouette.
    for side in (-1,1):
        for j in range(5):
            x=side*(6.8+j*1.2);y=20+j*.65
            height=6.2-j*.6
            for step in range(4):
                box(mask,x,y+step*height/4,1.8,1.1-step*.25,height/4+.02,1-step*.18,'gold' if step==3 else 'bronze')
            box(mask,x,y+.2,1.2,.32,5.5-j*.6,.16,'gold',True)
            box(light,x,y+.4,1.08,.12,3.8-j*.4,.1,'violet')
        for j in range(6):
            box(mask,side*(7.6-j*.6),3.1+j*.65,-2.0,.65,2.2,.8,'gold')
            box(light,side*(7.6-j*.6),3.25+j*.65,-2.44,.12,1.5,.1,'violet')
    for step in range(3):box(mask,0,25.5+step*1.27,.35,1.1-step*.38,1.29,.9-step*.23,'gold')
    box(light,0,25.8,-.15,.22,2.8,.1,'violet')
    # A substantially new silhouette: a colossal mask, cathedral wings and a suspended mantle.
    # Each separate component stays inside Java's element limits and Bedrock's culling bounds.
    for name in ('judge_mask','judge_mask_light'):
        result[name]['scale']=24;result[name]['center']=[0,12,0]
        for cube in result[name]['cubes']:
            cube['from']=[round(cube['from'][i]*factor,4) for i,factor in enumerate((1.16,1.08,1.4))]
            cube['size']=[round(cube['size'][i]*factor,4) for i,factor in enumerate((1.16,1.08,1.4))]
    for side,label in ((-1,'left'),(1,'right')):
        wing=new('judge_wing_'+label,24,[0,17,0]);glow=new('judge_wing_'+label+'_light',24,[0,17,0])
        for name in ('judge_wing_'+label,'judge_wing_'+label+'_light'):result[name]['offset']=[side*12,3,6]
        # Flying buttresses fan outward, with ivory blade feathers and pointed gilded tips.
        for i in range(9):
            x=side*(2+i*2.1);y=4+i*2.25;height=15-i*.75
            box(wing,x,y,.8,2.65,height,2.0,'abyss')
            box(wing,x,y,-.35,2.15,height-.5,.65,'ivory')
            box(wing,x,y,-.76,.28,height-1,.18,'gold')
            for tip in range(3):box(wing,x,y+height+tip*.55,.3,1.3-tip*.4,.58,1.0-tip*.25,'gold')
            box(glow,x+side*.55,y+.8,-.78,.17,height-2,.12,'violet')
            # A suspended chain of red crystals extends below each armored vane.
            for j in range(3):box(glow,x,y-1.0-j*.7,-.7,.3,.45,.3,'cyan')
    mantle=new('judge_mantle',32,[0,14,0]);mantle_light=new('judge_mantle_light',32,[0,14,0])
    result['judge_mantle']['offset']=result['judge_mantle_light']['offset']=[0,-7,5]
    for side in (-1,1):
        for i in range(8):
            x=side*(5+i*2.4);bottom=2+abs(i-3)*.8;height=15-abs(i-3)
            box(mantle,x,bottom,1+i*.12,2.8,height,1.5,'abyss')
            box(mantle,x,bottom,-.15,2.4,1.3,.8,'gold')
            box(mantle,x,bottom+1.5,-.1,.32,height-2,.32,'bronze')
            box(mantle_light,x+side*.9,bottom+.8,-.42,.12,height-1,.1,'violet')
        # Massive layered shoulder plates connect the mantle to the wings.
        for j in range(4):
            box(mantle,side*(10+j*3.3),18+j*.75,0,6-j*.7,2.2,4,'ivory')
            box(mantle,side*(10+j*3.3),18+j*.75,-2.1,5-j*.6,.45,.25,'gold')
    for j in range(6):
        width=8-abs(j-2.5)*1.5
        box(mantle,0,17+j*.65,-1,width,.7,1.8,'gold')
        box(mantle_light,0,17.15+j*.65,-2,max(.4,width-1.6),.35,.1,'violet')
    # Hands have individual phalanges, nails, knuckles, plated palms and a cuff.
    for name,sign in [('judge_left',-1),('judge_right',1)]:
        hand=new(name,8,[0,5.2,0]);glow=new(name+'_light',8,[0,5.2,0])
        box(hand,0,0,0,7.8,1.25,4.3,'bronze')
        box(hand,0,.35,-2.18,8,.35,.17,'gold')
        box(hand,0,1.2,0,7.6,5.25,3.5,'stone')
        box(hand,0,1.6,-1.78,6.8,4.8,.72,'ivory')
        for s in [-1,1]:
            box(hand,s*3.35,1.8,-2.05,.45,4.3,.35,'gold')
            box(hand,s*2.65,2.3,-2.18,.2,3.2,.1,'bronze',True)
        for i in range(4):
            x=(i-1.5)*1.78;height=[4.7,6.0,5.5,4.0][i if sign<0 else 3-i]
            for j in range(3):
                box(hand,x,6.1+j*(height/3+.11),-.17-j*.22,1.47,height/3,2.7-j*.44,'ivory')
                box(hand,x,6.15+j*(height/3+.11),-1.61-j*.015,1.42,.22,.22,'gold')
            box(hand,x,6.1+height-.8,-1.24,1.02,.72,.24,'bronze')
        # Separated outward thumb.
        box(hand,sign*4.2,2.1,-.3,1.55,2.5,2.3,'ivory')
        box(hand,sign*4.55,4.75,-.7,1.4,1.85,1.85,'ivory')
        box(hand,sign*4.55,5.4,-1.7,1.0,.65,.16,'gold')
        # A lozenge-shaped inset palm stone and split original glyph.
        for j in range(7):
            w=3.5-abs(j-3)*.85
            box(hand,0,2.1+j*.4,-2.28,w,.42,.24,'gold')
            box(glow,0,2.23+j*.34,-2.44,max(.15,w-.65),.36,.18,'violet')
        for s in [-1,1]:
            for j in range(4):box(glow,s*(1.1+j*.35),4.7+j*.27,-2.28,.15,.35,.11,'cyan')
    heart=new('judge_heart',4,[0,2.08,0])
    for j in range(9):
        w=3.75-abs(j-4)*.8
        box(heart,0,.05+j*.45,0,max(.3,w),.46,max(.3,w),'violet' if j%3 else 'cyan')
    shard=new('judge_shard',2,[0,1,0])
    box(shard,0,.1,0,1.6,1.2,.8,'ivory');box(shard,0,1.3,0,.9,.8,.8,'gold')
    for mesh in result.values():
        low=[min(c['from'][i] for c in mesh['cubes']) for i in range(3)]
        high=[max(c['from'][i]+c['size'][i] for c in mesh['cubes']) for i in range(3)]
        # Java's entity box starts at root Y and is centered on root X/Z. Bedrock's
        # geometry bounds allow a center offset, and its head origin differs by cy-1.5.
        mesh['bounds']={
            'java-width':round(max(36,2*max(abs(low[i]) for i in (0,2))+2,2*max(abs(high[i]) for i in (0,2))+2),4),
            'java-height':round(max(36,high[1]+2),4),
            'bedrock-width':round(max(36,high[0]-low[0]+2,high[2]-low[2]+2),4),
            'bedrock-height':round(max(36,high[1]-low[1]+2),4),
            'bedrock-offset':[round((low[i]+high[i])/2-(mesh['center'][1]-1.5 if i==1 else 0),4) for i in range(3)]}
    return result

def atlas():
    rows=[]
    for y in range(1024):
        row=bytearray([0])
        for x in range(1024):
            tile=(y//256)*4+x//256
            base=PALETTE[tile%8];u=x%256;v=y%256
            # Stone grain, warm gilded edges and angular carved filigree at 256px per face.
            noise=((x*17+y*47+(x*y)%109)%23)-11
            vein=math.sin(u*.031+math.sin(v*.027)*2)*math.sin(v*.052+u*.014)
            shade=noise*.35+vein*3
            if tile%8 in (2,3):shade+=12*math.cos(u/256*math.pi)-8*v/256
            edge=min(u,v,255-u,255-v)
            if edge<5:shade-=18
            if 6<edge<9:shade+=16
            # Original interlocking stepped chevrons engraved into ivory/gold.
            if tile%8 in (0,2,7):
                line=(u+abs((v%80)-40)*2)%96
                if line<2:shade-=36
                if 2<=line<4:shade+=16
            if tile%8 in (5,6):shade+=12*math.cos((u-v)*.05)
            row.extend((*[int(max(0,min(255,c+shade))) for c in base],255))
        rows.append(bytes(row))
    def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',1024,1024,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(b''.join(rows),9))+chunk(b'IEND',b'')

def json_bytes(data):return (json.dumps(data,indent=2)+'\n').encode()

def aether_texture():
    # An original four-point glint with a soft cyan-white falloff.
    rows=[]
    for y in range(64):
        row=bytearray([0])
        for x in range(64):
            u=abs((x-31.5)/31.5);v=abs((y-31.5)/31.5)
            light=max(0,1-u-v)**3
            ray=max(0,1-min(u,v)*16)*max(0,1-max(u,v))**2
            alpha=int(255*min(1,light+ray*.45))
            row.extend((255,255,255,alpha))
        rows.append(bytes(row))
    def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',64,64,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(b''.join(rows),9))+chunk(b'IEND',b'')

def assets():
    java={'pack.mcmeta':json_bytes({'pack':{'min_format':[75,0],'max_format':[97,1],'description':'Evergarden · The Ivory Judge / original sculpture'}})}
    bedrock={};shape=meshes();texture=atlas()
    java['assets/voidscape/textures/item/judge_atlas.png']=texture
    bedrock['textures/items/judge_atlas.png']=texture
    for name,mesh in shape.items():
        elems=[];cubes=[];scale=mesh['scale'];center=mesh['center'];bounds=mesh['bounds']
        for c in mesh['cubes']:
            at=c['from'];size=c['size'];t=c['tile'];tx=t%4;ty=t//4
            uv=[tx*4+.14,ty*4+.14,tx*4+3.86,ty*4+3.86]
            start=[round(8+(at[i]-center[i])*16/scale,5) for i in range(3)]
            end=[round(start[i]+size[i]*16/scale,5) for i in range(3)]
            elems.append({'from':start,'to':end,'faces':{f:{'texture':'#atlas','uv':uv} for f in ['north','south','west','east','up','down']}})
            cubes.append({'origin':[at[0]*16,(at[1]-center[1])*16+24,at[2]*16],
                'size':[n*16 for n in size],
                'uv':{f:{'uv':[tx*256+9,ty*256+9],'uv_size':[238,238]} for f in ['north','south','west','east','up','down']}})
        java[f'assets/voidscape/models/item/{name}.json']=json_bytes({'textures':{'atlas':'voidscape:item/judge_atlas'},'elements':elems,'gui_light':'front','display':{'fixed':{'scale':[1,1,1]}}})
        java[f'assets/voidscape/items/{name}.json']=json_bytes({'model':{'type':'minecraft:model','model':'voidscape:item/'+name}})
        bedrock[f'models/entity/{name}.geo.json']=json_bytes({'format_version':'1.16.0','minecraft:geometry':[{
            'description':{'identifier':'geometry.voidscape.'+name,'texture_width':1024,'texture_height':1024,
                'visible_bounds_width':bounds['bedrock-width'],'visible_bounds_height':bounds['bedrock-height'],'visible_bounds_offset':bounds['bedrock-offset']},
            'bones':[{'name':'head','pivot':[0,24,0],'cubes':cubes}]}]})
        bedrock[f'attachables/{name}.json']=json_bytes({'format_version':'1.10.0','minecraft:attachable':{'description':{
            'identifier':'voidscape:'+name,'materials':{'default':'entity_alphatest'},'textures':{'default':'textures/items/judge_atlas'},
            'geometry':{'default':'geometry.voidscape.'+name},'render_controllers':['controller.render.evergarden_judge_glow' if name.endswith('_light') or name=='judge_heart' else 'controller.render.evergarden_judge']}}})
    bedrock['render_controllers/judge.json']=json_bytes({'format_version':'1.8.0','render_controllers':{
        'controller.render.evergarden_judge':{'geometry':'Geometry.default','materials':[{'*':'Material.default'}],'textures':['Texture.default']},
        'controller.render.evergarden_judge_glow':{'geometry':'Geometry.default','materials':[{'*':'Material.default'}],'textures':['Texture.default'],'ignore_lighting':True}}})
    from judge_sigils import assets as sigil_assets
    sigil_java,sigil_bedrock=sigil_assets(json_bytes)
    java.update(sigil_java);bedrock.update(sigil_bedrock)
    bedrock['textures/particle/judge_glint.png']=aether_texture()
    for tint,color in [('cyan',[.46,.87,.95,1]),('violet',[.75,.56,1,1]),('amber',[1,.68,.35,1]),('red',[1,.18,.28,1])]:
        bedrock[f'particles/judge_{tint}.particle.json']=json_bytes({'format_version':'1.10.0','particle_effect':{
            'description':{'identifier':'voidscape:judge_'+tint,'basic_render_parameters':{'material':'particles_blend','texture':'textures/particle/judge_glint'}},
            'components':{'minecraft:emitter_lifetime_once':{'active_time':.01},'minecraft:emitter_rate_instant':{'num_particles':1},
                'minecraft:emitter_shape_point':{'offset':[0,0,0],'direction':[0,0,0]},'minecraft:particle_lifetime_expression':{'max_lifetime':.65},
                'minecraft:particle_initial_speed':0,'minecraft:particle_motion_dynamic':{},
                'minecraft:particle_appearance_billboard':{'size':['variable.judge_size * 0.25','variable.judge_size * 0.25'],
                    'facing_camera_mode':'rotate_xyz','uv':{'texture_width':64,'texture_height':64,'uv':[0,0],'uv_size':[64,64]}},
                'minecraft:particle_appearance_tinting':{'color':color}}}})
    return java,bedrock,shape

def archive(files,path):
    with zipfile.ZipFile(path,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as out:
        for name,data in sorted(files.items()):
            info=zipfile.ZipInfo(name,(2026,10,2,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
            out.writestr(info,data)

def build():
    java,bedrock,shape=assets();dist=ROOT/'dist';archive(java,dist/'judge-java.zip')
    # Patch only owned content. Retain every existing Java relic asset and its CDN SHA-1.
    with zipfile.ZipFile(dist/'evergarden-bedrock.mcpack') as source:
        files={n:source.read(n) for n in source.namelist() if not n.endswith('/')}
    files.update(bedrock)
    manifest=json.loads(files['manifest.json']);manifest['header']['version']=BEDROCK_VERSION
    for module in manifest['modules']:module['version']=BEDROCK_VERSION
    files['manifest.json']=json_bytes(manifest)
    atlas_data=json.loads(files['textures/item_texture.json'])
    mappings=json.loads((dist/'geyser-mappings.json').read_text())
    entries=mappings['items'].setdefault('minecraft:iron_helmet',[])
    entries[:]=[e for e in entries if not e.get('bedrock_identifier','').startswith('voidscape:judge_')]
    for name in shape:
        atlas_data['texture_data']['voidscape.'+name]={'textures':'textures/items/judge_atlas'}
        entries.append({'type':'definition','model':'voidscape:'+name,'bedrock_identifier':'voidscape:'+name,
            'display_name':'Ancient Judge Sculpture','bedrock_options':{'icon':'voidscape.'+name,'allow_offhand':False,'display_handheld':False}})
    files['textures/item_texture.json']=json_bytes(atlas_data)
    archive(files,dist/'evergarden-bedrock.mcpack');(dist/'geyser-mappings.json').write_bytes(json_bytes(mappings))
    hashes=json.loads((dist/'pack-hashes.json').read_text())
    for name in ['evergarden-bedrock.mcpack','judge-java.zip']:hashes[name]=hashlib.sha1((dist/name).read_bytes()).hexdigest()
    (dist/'pack-hashes.json').write_bytes(json_bytes(hashes))
    # JSON is valid YAML: Bukkit can read the same canonical dimensions for the vanilla fallback.
    (ROOT/'src/main/resources/judge-shape.yml').write_bytes(json_bytes(shape))
    (ROOT/'src/main/resources/judge-design.json').write_bytes(json_bytes({
        'revision':DESIGN_REVISION,'name':'Cathedral wings + suspended mantle',
        'models':len(shape),'components':len(shape)+11,'bedrock-version':'.'.join(map(str,BEDROCK_VERSION))}))
    print('Judge assets:',len(shape),'models;',sum(len(m['cubes']) for m in shape.values()),'cuboids;',hashes['judge-java.zip'])

if __name__=='__main__':build()
