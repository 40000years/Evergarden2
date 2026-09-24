"""Shared, native voxel models for Java items and Bedrock attachables.

Geometry and palette are authored here, so both clients use the same silhouette.
"""
import json
import math
import struct
import zlib

PALETTES = {
    'whale': ['182d46', '385774', 'ddbd70', 'fff0be', '46bfae', 'abfff1', '9473cb', 'f5ffff'],
    'garden': ['19372d', '42664d', 'd4ad62', 'ffe6a1', '3bbf92', 'afffd4', 'c582b6', 'f3fff4'],
    'observatory': ['231e43', '4b426f', 'c68c52', 'ffdab1', '597bc7', 'b6ddff', 'b98dfa', 'fbf2ff'],
}

def write(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2)+'\n', encoding='utf8')

def png(path, pixels):
    h, w = len(pixels), len(pixels[0])
    def part(kind, data):
        return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    raw=b''.join(b'\0'+bytes(c for p in row for c in p) for row in pixels)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b'\x89PNG\r\n\x1a\n'+part(b'IHDR',struct.pack('>IIBBBBB',w,h,8,6,0,0,0))+part(b'IDAT',zlib.compress(raw,9))+part(b'IEND',b''))

def palette(theme):
    colors=[tuple(bytes.fromhex(h)) for h in PALETTES[theme]]
    return [[tuple(max(0,min(255,c+(9 if (x+y)%7==0 else -6 if (x*3+y)%11==0 else 0))) for c in colors[x//8])+ (255,)
             for x in range(64)] for y in range(64)]

def geometry(kind):
    boxes=[]
    def box(x,y,z,w,h,d,color): boxes.append(([x,y,z],[x+w,y+h,z+d],color))
    def ring(y,r,color,vertical=False):
        for n in range(24):
            a=n*math.tau/24
            x=8+math.cos(a)*r
            if vertical: box(x-.55,y+math.sin(a)*r-.55,7.35,1.1,1.1,1.3,color)
            else: box(x-.65,y,8+math.sin(a)*r-.65,1.3,.8,1.3,color)
    if kind=='wand':
        box(7.2,-6,7.2,1.6,23,1.6,0)
        box(7.6,-4,6.95,.8,20,.35,4)
        for y in [-5,-1,4,9,14]: box(6.8,y,6.8,2.4,.9,2.4,2)
        box(6.3,15,6.3,3.4,2,3.4,3)
        ring(22,5,2,True)
        for side in [-1,1]:
            for n in range(4): box(8+side*(3.5+n*.65)-.6,17+n*2,7.2,1.2,2.4,1.6,3 if n==3 else 2)
        for y,w in [(18,1),(19,2),(20,3),(21,3),(22,2),(23,1)]: box(8-w/2,y,8-w/2,w,1,w,5 if y>=22 else 4)
        for n in range(5): box(5+n*1.35,16.8,6.5,.8,.8,.6,6)
        box(7.5,28,7.5,1,2,1,7)
    elif kind=='core':
        ring(8,5,2,True)
        for y,w in [(3,1),(4,2),(5,4),(6,5),(7,6),(8,5),(9,4),(10,2),(11,1)]: box(8-w/2,y,8-w/2,w,1,w,5 if y>8 else 4)
        for x in [2,13]: box(x,7,7.5,1,2,1,3)
    else:
        box(-3,0,-3,22,1.5,22,0);box(-2,1.5,-2,20,1,20,2)
        box(0,2.5,0,16,3,16,1);box(-1,5.5,-1,18,1,18,3)
        box(1,6.5,1,14,1,14,0)
        ring(7.5,6.8,2);ring(20,8,2)
        for side in [-1,1]:
            for axis in [0,1]:
                x,z=(8+side*9,8) if axis==0 else (8,8+side*9)
                box(x-1,6,z-1,2,6,2,2)
                box(x-.7,12,z-.7,1.4,2,1.4,5)
        for y,w in [(11,1),(12,2),(14,4),(16,6),(18,5),(20,4),(22,2),(24,1)]:
            box(8-w/2,y,8-w/2,w,2,w,5 if y>=20 else 4)
        for n in range(12):
            a=n*math.tau/12
            box(8+math.cos(a)*6.5-.45,8,8+math.sin(a)*6.5-.45,.9,.3,.9,7)
        if kind=='altar_whale':
            for side in [-1,1]:
                for n in range(6): box(8+side*(10-n)-.7,8+n*2.5,7.3,1.4,3,1.4,3)
        elif kind=='altar_garden':
            for n in range(20):
                a=n*math.tau/10;box(8+math.cos(a)*8-.7,7+n*.7,8+math.sin(a)*8-.7,1.4,1.6,1.4,4 if n%3 else 2)
        else:
            ring(18,10,3,True)
            for x in [1,14]: box(x,25,7.5,1,2,1,6)
    return boxes

def icon(kind, theme):
    """Orthographic pixel projection of the model, matching existing pack icon style."""
    pixels=[[(0,0,0,0) for _ in range(64)] for _ in range(64)]
    colors=[tuple(bytes.fromhex(h))+(255,) for h in PALETTES[theme]]
    for start,end,color in sorted(geometry(kind),key=lambda b:b[0][2],reverse=True):
        for x in range(max(0,int((start[0]+12)*1.5)),min(64,int((end[0]+12)*1.5)+1)):
            for y in range(max(0,int(50-end[1]*1.5)),min(64,int(50-start[1]*1.5)+1)):
                pixels[y][x]=colors[color]
    return pixels

def asset(java,bedrock,namespace,name,kind,theme):
    tex=f'{namespace}:item/{name}_palette'
    png(java/f'assets/{namespace}/textures/item/{name}_palette.png',palette(theme))
    png(bedrock/f'textures/items/{name}_palette.png',palette(theme))
    png(java/f'assets/{namespace}/textures/item/{name}.png',icon(kind,theme))
    png(bedrock/f'textures/items/{name}.png',icon(kind,theme))
    elements=[];cubes=[]
    for start,end,color in geometry(kind):
        u=color*2+.25
        elements.append({'from':start,'to':end,'faces':{f:{'uv':[u,.25,u+1.5,1.75],'texture':'#palette'} for f in ['north','south','east','west','up','down']}})
        origin=[start[0]-8,start[1]+24,start[2]-8] if kind.startswith('altar') else [start[0]-8,start[1],start[2]-8]
        cubes.append({'origin':origin,'size':[end[i]-start[i] for i in range(3)],
                      'uv':{f:{'uv':[color*8+1,1],'uv_size':[6,6]} for f in ['north','south','east','west','up','down']}})
    display={
        'gui':{'rotation':[15,-35,-25 if kind=='wand' else 0],'scale':[.4,.4,.4] if kind=='wand' else [.5,.5,.5]},
        'ground':{'translation':[0,3,0],'scale':[.45,.45,.45]},
        'head':{'translation':[0,6.4,0],'scale':[1.6,1.6,1.6]},
        'firstperson_righthand':{'rotation':[0,-90,20],'translation':[1,2,0],'scale':[.65,.65,.65]},
        'thirdperson_righthand':{'rotation':[0,-90,0],'translation':[0,2,0],'scale':[.85,.85,.85]},
    }
    display['firstperson_lefthand']=display['firstperson_righthand'];display['thirdperson_lefthand']=display['thirdperson_righthand']
    write(java/f'assets/{namespace}/models/item/{name}.json',{'textures':{'palette':tex,'particle':tex},'elements':elements,'display':display})
    write(java/f'assets/{namespace}/items/{name}.json',{'model':{'type':'minecraft:model','model':f'{namespace}:item/{name}'}})
    worn=kind.startswith('altar')
    bone={'name':'head' if worn else 'restoration','pivot':[0,24,0] if worn else [0,0,0],'cubes':cubes}
    if not worn: bone['binding']="q.item_slot_to_bone_name(context.item_slot)"
    write(bedrock/f'models/entity/{name}.geo.json',{'format_version':'1.16.0','minecraft:geometry':[{
        'description':{'identifier':f'geometry.{namespace}.{name}','texture_width':64,'texture_height':64,
            'visible_bounds_width':5,'visible_bounds_height':5,'visible_bounds_offset':[0,2,0]},'bones':[bone]}]})
    desc={'identifier':f'{namespace}:{name}','materials':{'default':'entity_alphatest'},
        'textures':{'default':f'textures/items/{name}_palette'},'geometry':{'default':f'geometry.{namespace}.{name}'},
        'render_controllers':['controller.render.restoration']}
    if not worn:
        animation=f'animation.{namespace}.{name}.hold'
        write(bedrock/f'animations/{name}.json',{'format_version':'1.8.0','animations':{animation:{'loop':True,'bones':{
            'restoration':{'rotation':[0,0,'context.is_first_person ? -20 : 0'],'position':[0,1,0],'scale':.65}}}}})
        desc['animations']={'hold':animation};desc['scripts']={'animate':['hold']}
    write(bedrock/f'attachables/{name}.json',{'format_version':'1.10.0','minecraft:attachable':{'description':desc}})
    write(bedrock/'render_controllers/restoration.json',{'format_version':'1.8.0','render_controllers':{
        'controller.render.restoration':{'geometry':'Geometry.default','materials':[{'*':'Material.default'}],'textures':['Texture.default']}}})

def register_magic(java,bedrock,atlas,definitions):
    for name,kind,material,title in [('restoration_wand','wand','blaze_rod','Wand of Restoration'),('restoration_core','core','prismarine_crystals','Core of Restoration')]:
        asset(java,bedrock,'advance_magic',name,kind,'whale')
        atlas[f'advance_magic.{name}']={'textures':f'textures/items/{name}'}
        definitions.setdefault(f'minecraft:{material}',[]).append({'type':'definition','model':f'advance_magic:{name}',
            'bedrock_identifier':f'advance_magic:{name}','display_name':title,
            'bedrock_options':{'icon':f'advance_magic.{name}','allow_offhand':True,'display_handheld':kind=='wand','creative_category':'equipment'}})

def register_altars(java,bedrock,textures,mappings,selectors,write_json):
    for theme in PALETTES:
        name=f'restoration_altar_{theme}';asset(java,bedrock,'voidscape',name,f'altar_{theme}',theme)
        textures[f'voidscape.{name}']={'textures':f'textures/items/{name}'}
        selectors.setdefault('iron_helmet',[]).append({'when':f'voidscape:{name}','model':{'type':'minecraft:model','model':f'voidscape:item/{name}'}})
        mappings['items'].setdefault('minecraft:iron_helmet',[]).append({'type':'definition','model':'minecraft:iron_helmet',
            'predicate':{'type':'match','property':'custom_model_data','index':0,'value':f'voidscape:{name}'},
            'bedrock_identifier':f'voidscape:{name}','display_name':f'Restoration Altar / {theme.title()}',
            'bedrock_options':{'icon':f'voidscape.{name}','allow_offhand':False,'display_handheld':False}})
