"""Authored elemental VFX silhouettes, exported to native Java and Bedrock planes."""
from pathlib import Path
import copy
import io
import json
import math
import re

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'advance-magic/art/elemental-vfx'
PALETTES = {
    'fire': ('#B52B20','#FF7929','#FFF0B0'), 'water': ('#17639D','#48CCEB','#DAFFFF'),
    'ice': ('#32629D','#9CDEFF','#F0FCFF'), 'wind': ('#4A9F96','#9EEBD3','#ECFFF7'),
    'earth': ('#6C634E','#ADC765','#E6F1B7'), 'light': ('#B68A39','#FFD67E','#FFF9E1'),
    'dark': ('#44305E','#A96BD6','#E4B0FB'), 'lightning': ('#5966AB','#A9C6FF','#F2FAFF'),
}
SHAPES = {
 'fireball':'flame','flame_burst':'burst','burning_ground':'cracks','flame_dash':'streak',
 'inferno_spear':'spear','magma_eruption':'eruption','hellfire_chain':'chain',
 'scorching_vortex':'vortex','meteor_rain':'meteor','phoenix_rebirth':'phoenix',
 'aqua_bullet':'drop','water_blade':'blade','healing_spring':'flower','bubble_prison':'bubble',
 'tidal_push':'wave','aqua_shield':'shield','rain_blessing':'rain','whirlpool':'vortex',
 'tsunami':'wave','leviathans_wrath':'dragon','frost_bolt':'crystal','ice_spike':'spike',
 'frozen_armor':'armor','glacial_path':'cracks','crystal_prison':'prison','blizzard':'snow',
 'absolute_zero':'star','wind_cutter':'blade','gale_step':'streak','updraft':'updraft',
 'wind_barrier':'shield','cyclone_trap':'vortex','thousand_wind_blades':'fan',
 'tempest_dominion':'tempest','stone_bullet':'stone','earth_spike':'spike',
 'stone_wall':'wall','iron_skin':'armor','quicksand':'sand','seismic_slam':'cracks',
 'golem_guardian':'guardian','titans_judgment':'fist','radiant_bolt':'spear',
 'holy_sanctuary':'shrine','purification':'feather','divine_judgment':'lightblade',
 'heavens_descent':'wings','shadow_bolt':'dart','life_drain':'ribbon','cursed_mark':'eye',
 'shadow_doppelganger':'ghost','abyssal_collapse':'rift','spark_shot':'bolt',
 'lightning_chain':'bolt','thunder_dash':'streak','static_field':'grid',
 'thunder_spear':'spear','wrath_of_raijin':'storm',
}


def catalog():
    source=(ROOT/'advance-magic/src/main/java/com/example/advancemagic/elemental/ElementalSpell.java').read_text(encoding='utf8')
    return [(name.lower(),element.lower()) for name,element in re.findall(r'^\s*(\w+)\("[^"]+",Element\.(\w+),',source,re.M)]


def texture(element, shape):
    im=Image.new('RGBA',(256,256)); d=ImageDraw.Draw(im)
    dark,base,hot=[tuple(bytes.fromhex(c[1:])) for c in PALETTES[element]]
    def line(points,width=3,color=None,alpha=225):
        color=color or base
        d.line(points,fill=(*color,alpha),width=max(2,round(width*1.7)),joint='curve')
    def poly(points,fill=True):
        if fill:d.polygon(points,fill=(*dark,145))
        line(points+[points[0]],3,base)
        if len(points)>2:line(points[:2],2,hot)
    def arc(cx,cy,rx,ry,start,end,width=3,color=None):
        line([(cx+math.cos(t)*rx,cy+math.sin(t)*ry) for t in [start+(end-start)*i/32 for i in range(33)]],width,color)
    def shard(x,y,w=14,h=30):
        poly([(x,y-h),(x+w,y-h*.18),(x+w*.48,y+h*.8),(x-w*.5,y+h),(x-w,y+h*.1)])
        line([(x,y-h),(x-w*.25,y+h*.25),(x-w*.5,y+h)],2,hot)
    if shape=='trail':
        for j in range(3):
            pts=[(128+math.sin(i/32*math.pi*3+j*.8)*(5+j*6),16+i*224/32) for i in range(33)]
            line(pts,6-j*2,hot if j==0 else base,230-j*35)
        line([(113,38),(120,77)],2,hot);line([(143,173),(136,221)],2,hot)
    elif shape in ('flame','meteor'):
        poly([(125,22),(153,66),(149,98),(178,74),(181,134),(162,178),(130,220),(91,198),(71,164),(78,114),(104,78),(103,124),(126,93)])
        poly([(126,98),(150,139),(146,173),(128,199),(110,169),(113,139)],True)
        line([(128,126),(137,158),(128,187)],4,hot)
        if shape=='meteor':
            for x,y in ((56,42),(85,24),(183,37)):line([(x,y),(x+12,y+40)],3,hot)
    elif shape=='drop':
        poly([(128,25),(171,90),(188,137),(177,185),(150,216),(116,223),(81,198),(66,157),(80,111)])
        arc(126,157,39,47,-.5,1.8,5,hot); line([(105,115),(88,153),(93,177)],3,hot)
    elif shape in ('crystal','spike','prison'):
        for x,y,w,h in ((128,129,35,98),(72,170,20,57),(184,161,19,64)):
            shard(x,y,w,h)
        if shape=='prison':
            line([(52,207),(78,226),(179,226),(203,202)],3,hot)
            line([(54,203),(54,95),(78,55)],3);line([(201,201),(201,90),(180,51)],3)
    elif shape in ('blade','fan'):
        for j in range(3 if shape=='fan' else 1):
            off=j*23
            poly([(30+off,191-off),(71+off,101-off),(156+off,43),(225,37+off),(164,82+off),(98+off,133),(56+off,204-off)],False)
            line([(47+off,182-off),(111+off,103),(207,45+off)],3,hot)
        line([(38,220),(87,189),(115,159)],2)
    elif shape in ('spear','lightblade','dart'):
        poly([(128,16),(154,89),(137,81),(139,211),(128,239),(116,210),(118,81),(100,91)])
        line([(128,32),(128,218)],5,hot)
        if shape=='lightblade':
            poly([(72,112),(111,105),(128,120),(146,105),(184,112),(147,130),(128,125),(107,130)])
        if shape=='dart':
            line([(96,182),(74,209),(86,165)],3);line([(159,181),(184,212),(171,166)],3)
    elif shape in ('wave','ribbon'):
        for j in range(3):
            pts=[(24+i*208/32,131+math.sin(i/32*math.pi*2+j*.55)*28+j*14) for i in range(33)]
            line(pts,7-j*2,hot if j==0 else base,220-j*35)
        for x in (45,87,133,178,211):line([(x,113),(x+5,103),(x+13,104),(x+20,119)],3,hot)
    elif shape=='streak':
        for off in (-42,0,42):
            line([(24,194+off*.3),(74,164+off*.5),(153,105+off*.7),(231,52+off)],5 if off==0 else 2,hot if off==0 else base)
    elif shape in ('vortex','tempest','sand'):
        for j in range(3):
            pts=[]
            for i in range(49):
                a=i/48*math.pi*2.4+j*math.pi*2/3;r=18+i/48*86
                pts.append((128+math.cos(a)*r,128+math.sin(a)*r*(.65 if shape=='sand' else 1)))
            line(pts,5 if j==0 else 3,hot if j==0 else base,210)
        if shape=='tempest':
            for a in (0,2.1,4.2):shard(128+math.cos(a)*88,128+math.sin(a)*88,9,20)
    elif shape in ('burst','star','snow'):
        n=6 if shape!='burst' else 8
        for i in range(n):
            a=i*math.tau/n
            p=lambda r,b=0:(128+math.cos(a+b)*r,128+math.sin(a+b)*r)
            line([p(19),p(99)],4,hot)
            line([p(57,-.24),p(70),p(57,.24)],3)
            if shape=='snow':line([p(76,-.16),p(88),p(76,.16)],2)
        if shape=='burst':poly([(128,87),(144,112),(169,128),(145,142),(128,169),(110,145),(87,128),(111,111)])
    elif shape in ('cracks','eruption'):
        for i in range(6):
            a=i*math.tau/6; pts=[]
            for j,r in enumerate((15,39,51,79,108)):
                b=a+(.16 if j%2 else -.09);pts.append((128+math.cos(b)*r,128+math.sin(b)*r))
            line(pts,5 if i%2 else 3,hot if i%2 else base)
            line([pts[2],(pts[2][0]+math.cos(a+.7)*28,pts[2][1]+math.sin(a+.7)*28)],2)
        if shape=='eruption':poly([(117,157),(104,109),(121,126),(129,78),(152,145),(146,177)],True)
    elif shape=='chain':
        for i in range(5):
            x=48+i*40;y=128+math.sin(i*1.2)*18
            poly([(x-21,y),(x,y-15),(x+21,y),(x,y+15)],False)
        line([(23,151),(48,145),(65,151)],2,hot)
    elif shape in ('flower','shrine'):
        n=3 if shape=='flower' else 4
        for i in range(n):
            a=i*math.tau/n-math.pi/2
            pts=[]
            for x,y in ((-9,18),(-33,58),(-16,101),(0,113),(19,91),(28,56),(9,18)):
                pts.append((128+math.cos(a)*y-math.sin(a)*x,128+math.sin(a)*y+math.cos(a)*x))
            poly(pts,False)
        poly([(128,105),(149,128),(128,151),(107,128)],False)
    elif shape in ('bubble','shield'):
        for i in range(3):
            arc(128,128,91,99,-math.pi/2+i*math.tau/3+.16,-math.pi/2+(i+1)*math.tau/3-.16,5,hot if i==0 else base)
            arc(128,128,71,78,-math.pi/2+i*math.tau/3+.24,-math.pi/2+(i+1)*math.tau/3-.24,2)
        if shape=='shield':poly([(128,80),(161,98),(153,145),(128,173),(103,145),(95,98)],False)
    elif shape=='rain':
        for x,y in ((45,49),(89,73),(139,41),(192,65),(66,137),(120,160),(187,143)):
            line([(x,y),(x-9,y+35)],4,hot)
        arc(128,202,81,20,.1,3,3)
    elif shape in ('phoenix','wings','feather'):
        for side in (-1,1):
            if shape=='feather' and side==1:continue
            p=lambda ps:[(128+side*x,y) for x,y in ps]
            poly(p([(1,153),(25,113),(87,41),(69,106),(102,77),(77,139),(102,119),(64,176),(30,187)]))
            for k in range(3):line(p([(22+k*15,151-k*16),(57+k*14,105-k*20)]),3,hot)
        line([(128,106),(128,208)],3,hot)
        if shape=='phoenix':poly([(113,106),(128,80),(146,105),(135,127),(127,119)])
    elif shape=='dragon':
        poly([(84,166),(54,131),(35,65),(81,92),(99,54),(116,98),(156,85),(183,114),(225,124),(211,155),(177,160),(158,193),(119,210),(132,173)])
        line([(164,119),(179,117),(186,122)],4,hot)
        poly([(179,157),(183,177),(194,156)],True)
        line([(92,124),(125,135),(161,136),(211,141)],4,hot)
    elif shape in ('stone','armor','guardian','fist','wall'):
        if shape=='wall':
            for row in range(3):
                for col in range(5):
                    x=21+col*43;y=69+row*43
                    poly([(x,y),(x+38,y),(x+38,y+37),(x,y+37)],True)
        elif shape=='armor':
            for side in (-1,1):
                poly([(128+side*15,63),(128+side*80,91),(128+side*70,155),(128+side*35,196),(128+side*9,158)])
            line([(128,72),(128,180)],3,hot)
        elif shape=='guardian':
            poly([(105,47),(154,47),(159,84),(174,106),(206,116),(197,166),(164,158),(157,214),(130,214),(128,168),(112,213),(84,213),(89,146),(55,160),(49,114),(88,94),(102,83)])
            line([(113,66),(123,66)],4,hot);line([(137,66),(147,66)],4,hot)
            line([(103,112),(127,129),(151,111)],3,hot)
        elif shape=='fist':
            poly([(66,181),(43,139),(60,88),(84,63),(111,62),(126,47),(156,53),(175,74),(197,84),(215,114),(201,161),(176,187),(167,224),(95,224)])
            for x in (85,117,151,183):line([(x,83),(x-7,118),(x,145)],3,hot)
        else:
            poly([(87,49),(161,36),(207,96),(195,173),(140,220),(64,191),(39,116)])
            line([(87,49),(117,111),(64,191)],4,hot);line([(117,111),(207,96)],3)
    elif shape in ('updraft','grid','bolt','storm','rift','eye','ghost'):
        if shape=='updraft':
            for x in (65,128,191):line([(x-22,219),(x+12,159),(x-11,92),(x,32)],4,hot)
            line([(105,59),(128,32),(147,59)],3)
        elif shape=='grid':
            for x,y in ((57,55),(194,55),(57,197),(194,197)):
                line([(x,128),(x+12,y+20),(128,y),(151,128)],3)
            shard(128,128,12,25)
        elif shape=='storm':
            line([(34,88),(66,69),(99,79),(127,53),(161,72),(202,61),(225,84)],6)
            for off in (-44,0,44):line([(128+off,97),(114+off,134),(141+off,132),(116+off,196)],5,hot)
        elif shape=='rift':
            poly([(136,20),(111,74),(140,105),(112,147),(146,178),(125,236),(160,174),(134,144),(158,100),(132,75)],True)
            for side in (-1,1):line([(128+side*27,114),(128+side*64,89),(128+side*99,100)],3)
        elif shape=='eye':
            poly([(29,129),(77,95),(128,78),(180,95),(225,129),(179,161),(128,177),(77,161)],False)
            shard(128,128,15,38)
            for x in (66,128,190):line([(x,190),(x-5,211)],3,hot)
        elif shape=='ghost':
            poly([(111,49),(148,49),(158,80),(149,98),(182,118),(191,186),(162,159),(164,223),(133,197),(119,225),(93,188),(61,202),(78,126),(105,98),(96,79)],True)
            line([(109,74),(119,78),(133,72),(144,77)],3,hot)
        else:
            line([(144,21),(111,82),(146,79),(105,144),(140,135),(112,235)],7,hot)
            line([(129,84),(185,65),(168,109)],3);line([(112,144),(64,171),(76,125)],3)
    else:raise ValueError(shape)
    # A small halo follows the existing strokes, never a filled opaque background.
    glow=im.filter(ImageFilter.GaussianBlur(3)); glow.putalpha(glow.getchannel('A').point(lambda a:a//4))
    return Image.alpha_composite(glow,im)


def jwrite(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf8')


def generate():
    from rare_spell_vfx_assets import generate_assets
    rows=catalog()
    if set(SHAPES)!={name for name,element in rows}:raise ValueError('VFX shape catalog must cover the 58 spells exactly')
    assets=[]
    for name,element in rows:assets.append(('elemental_'+name,element,SHAPES[name]))
    heads={'fire':'flame','water':'drop','ice':'crystal','wind':'blade','earth':'stone','light':'spear','dark':'dart','lightning':'bolt'}
    for element in PALETTES:
        for kind,shape in [('head',heads[element]),('trail','trail'),('burst','star' if element=='ice' else 'burst'),('mark','eye' if element=='dark' else 'crystal'),('facet','armor')]:
            assets.append((f'elemental_{element}_{kind}',element,shape))
    ART.mkdir(parents=True,exist_ok=True)
    for name,element,shape in assets:texture(element,shape).save(ART/(name+'.png'),optimize=True)
    assets.extend(generate_assets(ART))
    jwrite(ART/'manifest.json',{'format':2,'spells':dict((name,{'element':element,'shape':SHAPES[name],'model':'advance_magic:elemental_'+name}) for name,element in rows),
                              'assets':[{'id':name,'element':element,'shape':shape} for name,element,shape in assets]})
    width=8*150; height=math.ceil(len(rows)/8)*150
    gallery=Image.new('RGB',(width,height),'#131824'); draw=ImageDraw.Draw(gallery)
    for i,(name,element) in enumerate(rows):
        x=i%8*150;y=i//8*150
        gallery.paste(texture(element,SHAPES[name]).resize((112,112),Image.Resampling.LANCZOS),(x+19,y+1),texture(element,SHAPES[name]).resize((112,112),Image.Resampling.LANCZOS))
        draw.text((x+5,y+119),name[:22],fill='#e7edf5')
    buffer=io.BytesIO();gallery.save(buffer,format='PNG');path=ART/'shape-gallery-1.15.0.png'
    if not path.is_file() or path.read_bytes()!=buffer.getvalue():path.write_bytes(buffer.getvalue())
    return assets


def register(java,bedrock):
    for name,element,shape in generate():
        data=(ART/(name+'.png')).read_bytes()
        for path in (java/f'assets/advance_magic/textures/effect/{name}.png',bedrock/f'textures/particle/{name}.png'):
            path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
        ray=name.endswith('_trail')
        faces=('north','south','east','west') if ray else ('north','south')
        jwrite(java/f'assets/advance_magic/models/effect/{name}.json',{'ambientocclusion':False,'textures':{'art':f'advance_magic:effect/{name}'},
            'elements':[{'from':[0,0,0] if ray else [0,0,7.99],'to':[16,16,16] if ray else [16,16,8.01], 'shade':False,
                         'faces':{side:{'texture':'#art','uv':[0,0,16,16]} for side in faces}}]})
        jwrite(java/f'assets/advance_magic/items/{name}.json',{'model':{'type':'minecraft:model','model':f'advance_magic:effect/{name}'}})
        particle={'format_version':'1.10.0','particle_effect':{'description':{'identifier':f'advance_magic:{name}',
            'basic_render_parameters':{'material':'particles_blend','texture':f'textures/particle/{name}'}},'components':{
            'minecraft:emitter_lifetime_once':{'active_time':.01},'minecraft:emitter_rate_instant':{'num_particles':1},
            'minecraft:emitter_shape_point':{'offset':['variable.line_offset_x','variable.line_offset_y','variable.line_offset_z'],'direction':[0,0,0]},
            'minecraft:particle_lifetime_expression':{'max_lifetime':.1 if ray or name.endswith('_head') or name in ('elemental_tsunami','elemental_leviathans_wrath','elemental_meteor_rain','elemental_tidal_push','elemental_rare_tsunami','elemental_rare_leviathans_wrath','elemental_rare_meteor_rain') else .2},'minecraft:particle_initial_speed':0,
            'minecraft:particle_initial_spin':{'rotation':0 if ray else 'variable.line_rotation','rotation_rate':0},
            'minecraft:particle_motion_dynamic':{},'minecraft:particle_appearance_billboard':{
                'size':['variable.line_width * 0.5','variable.line_height * 0.5'],'facing_camera_mode':'direction_y' if ray else 'direction_z',
                'direction':{'mode':'custom','custom_direction':['variable.line_normal_x','variable.line_normal_y','variable.line_normal_z']},
                'uv':{'texture_width':256,'texture_height':256,'uv':[0,0],'uv_size':[256,256]}}}}}
        jwrite(bedrock/f'particles/{name}.particle.json',particle)
        if not ray:
            flat=copy.deepcopy(particle);flat['particle_effect']['description']['identifier']+='_flat'
            billboard=flat['particle_effect']['components']['minecraft:particle_appearance_billboard'];billboard['facing_camera_mode']='emitter_transform_xz';del billboard['direction']
            jwrite(bedrock/f'particles/{name}_flat.particle.json',flat)


if __name__=='__main__':print('Generated',len(generate()),'elemental VFX assets in',ART)
