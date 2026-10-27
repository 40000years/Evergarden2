"""Build 111 original editable, textured, articulated ModelEngine pet blueprints.

Geometry is authored in the three pet sculpt modules. This exporter uses the same
R4 axis conventions as the six river rigs, and never changes their resources.
"""
import argparse
import base64
import hashlib
import io
import json
import math
import uuid
from pathlib import Path

from pet_paint import paint

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'evergarden/src/main/resources/modelengine/pets'
ART = ROOT / 'evergarden/art/pets'
STAGES = ('baby', 'medium', 'adult')
MYTHICAL = ('aetherius', 'ignis_draconis', 'zephyros', 'solaris', 'fenrir')
RARE = ('arcane_nine_tails', 'frost_wyvern', 'astral_owl', 'crimson_cerberus',
        'shadow_panther', 'blood_gargoyle', 'runic_golem', 'world_tree_entling',
        'crystal_tortoise', 'abyssal_manta', 'alchemist_mimic', 'chrono_fairy')
MENAGERIE = ('mochi_slime', 'cloud_lamb', 'moon_rabbit', 'bloom_hedgehog', 'coral_axolotl',
             'clover_bumble', 'aurora_penguin', 'storm_raptor', 'magma_rhino', 'eclipse_scorpion',
             'raijin_tiger', 'titan_beetle', 'anubis_jackal', 'bone_drake', 'jade_lung',
             'crystal_stag', 'luna_moth', 'celestial_koi', 'starlight_peacock', 'aurora_jelly')
SPECIES = MYTHICAL + RARE + MENAGERIE
TITLES = ('Aetherius', 'Ignis Draconis', 'Zephyros', 'Solaris', 'Fenrir',
          'Arcane Nine-Tails', 'Frost Wyvern', 'Astral Owl', 'Crimson Cerberus',
          'Shadow Panther', 'Blood Gargoyle', 'Runic Golem', 'World-Tree Entling',
          'Crystal Tortoise', 'Abyssal Manta', 'Alchemist Mimic', 'Chrono Fairy',
          'Mochi Slime', 'Cloud Lamb', 'Moon Rabbit', 'Bloom Hedgehog', 'Coral Axolotl',
          'Clover Bumble', 'Aurora Penguin', 'Storm Raptor', 'Magma Rhino', 'Eclipse Scorpion',
          'Raijin Tiger', 'Titan Beetle', 'Anubis Jackal', 'Bone Drake', 'Jade Lung',
          'Crystal Stag', 'Luna Moth', 'Celestial Koi', 'Starlight Peacock', 'Aurora Jelly')
# Always airborne: walking is a hovering drift rather than steps.
HOVERING = ('astral_owl', 'solaris', 'abyssal_manta', 'chrono_fairy', 'clover_bumble', 'luna_moth',
            'celestial_koi', 'aurora_jelly', 'jade_lung')
# Many-legged bodies that crouch flat to sit, rather than settling onto haunches.
CRAWLERS = ('eclipse_scorpion', 'titan_beetle')
FACES = ('north', 'south', 'east', 'west', 'up', 'down')


def uid(model, label):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'evergarden/pets/' + model + '/' + label))


def animations(model, bones, species):
    names=list(bones)
    def make(name, length, samples, loop):
        animators={}
        for bone in names:
            frames=[]
            for time, pose in samples:
                channels=pose.get(bone, {'rotation':[0,0,0]})
                if isinstance(channels,list): channels={'rotation':channels}
                # Explicit positions reset the previous pose during animation changes.
                channels={'rotation':[0,0,0], 'position':[0,0,0], **channels}
                for channel, vector in channels.items():
                    vector=[-vector[0],-vector[1],vector[2]] if channel=='rotation' else [-vector[0],vector[1],vector[2]]
                    frames.append(dict(channel=channel, data_points=[dict(zip('xyz',map(str,vector)))],
                        uuid=uid(model,f'{name}/{bone}/{channel}/{time}'),time=round(time,4),color=-1,interpolation='catmullrom' if loop=='loop' else 'linear'))
            animators[uid(model,'bone/'+bone)]=dict(name=bone,type='bone',keyframes=frames)
        return dict(uuid=uid(model,'animation/'+name),name=name,loop=loop,override=True,length=length,snapping=20,
                    anim_time_update='',blend_weight='',start_delay='',loop_delay='',animators=animators)
    clips=[]
    for action,length in (('idle',3.2),('walk',1.0),('fly',1.4),('sit',2.0),('greet',2.4)):
        samples=[]
        for i in range(9):
            t=i/8; s=math.sin(t*math.tau); c=math.cos(t*math.tau); pose={}
            for bone in names:
                lower=bone.lower()
                if lower=='body':
                    pose[bone]={'rotation':[s*.7,0,0], 'position':[0,(1-c)*.10,0]}
                elif lower.startswith('head'):
                    pose[bone]=[s*1.5,s*2.5,0]
                elif 'tail' in lower:
                    pose[bone]=[s*2, s*(7 if lower in ('tail','tail_base') else 4),s*2]
                elif 'wing' in lower:
                    side=-1 if lower.endswith(('_l','left')) or '_l_' in lower else 1
                    pose[bone]=[s*1.2,0,side*s*2]
            if action=='walk':
                for bone in names:
                    lower=bone.lower()
                    if lower.startswith(('leg_','front_leg','back_leg','hind_leg','foot_')):
                        limb=lower.removesuffix('_lower')
                        diagonal=1 if limb.endswith(('fl','br','_l')) else -1
                        bend=-max(0,-s*diagonal)*32 if lower.endswith('_lower') else s*diagonal*23
                        pose[bone]=[bend,0,0]
                    if lower.startswith('arm_'): pose[bone]=[-s*18*(1 if lower.endswith('_l') else -1),0,0]
                if 'body' in bones:pose['body']={'rotation':[0,s*2,0],'position':[0,abs(s)*.25,0]}
                if species in HOVERING:
                    pose['body']={'position':[0,1+(1-c)*.7,0]}
            elif action=='fly':
                for bone in names:
                    lower=bone.lower();side=-1 if '_l' in lower or lower.endswith('left') else 1
                    if 'wing' in lower:pose[bone]=[s*5,0,side*(s*29+8)]
                    elif lower.startswith('leg_'):pose[bone]=[20,0,0]
                if 'body' in bones:pose['body']={'rotation':[-4,0,0],'position':[0,2+(1-c)*.8,0]}
            elif action=='sit':
                ease=min(1,t*4)
                quadruped=all(b in bones for b in ('leg_fl','leg_fr','leg_bl','leg_br')) and species not in CRAWLERS
                if quadruped:
                    # A settled haunch pose with folded rear hocks, rather than
                    # scaling the whole animal or dragging its feet through the floor.
                    hip=min(bones[b][1][1] for b in ('leg_bl','leg_br'))
                    pose['body']={'rotation':[-4*ease,0,0],'position':[0,-hip*.15*ease,0]}
                for bone in names:
                    lower=bone.lower()
                    if lower.startswith('head'):pose[bone]=[-8*ease,s*2,4*ease]
                    if 'tail' in lower:pose[bone]=[0,18*ease,s*3]
                    if 'wing' in lower:pose[bone]=[0,0,(-1 if '_l' in lower else 1)*-8*ease]
                    if quadruped and lower in ('leg_bl','leg_br'):pose[bone]=[-52*ease,0,0]
                    if quadruped and lower in ('leg_bl_lower','leg_br_lower'):pose[bone]=[68*ease,0,0]
                    if quadruped and lower in ('leg_fl','leg_fr'):pose[bone]=[18*ease,0,0]
                if 'lid' in bones:pose['lid']=[-10*ease,0,0]
            elif action=='greet':
                wave=math.sin(t*math.pi)**2
                for bone in names:
                    lower=bone.lower()
                    if lower.startswith('head'):pose[bone]=[-wave*12,s*8,wave*7]
                    if 'tail' in lower:pose[bone]=[0,s*22,0]
                    if 'wing' in lower:pose[bone]=[0,0,(-1 if '_l' in lower else 1)*wave*24]
                    if lower=='arm_r':pose[bone]=[wave*95,0,s*12]
                if 'lid' in bones:pose['lid']=[-wave*32,0,0]
                if 'body' in bones:pose['body']={'position':[0,wave*.9,0]}
            if species=='abyssal_manta':
                # A ray sends a soft wave out along each fin; it does not beat
                # two rigid wings like a bird. Tips and tail trail the body.
                amplitude={'idle':2.5,'walk':7,'fly':13,'sit':1,'greet':7}[action]
                held=action in ('sit','greet')
                envelope=math.sin(t*math.pi)**2 if held else 1
                for bone in names:
                    if 'wing' in bone:
                        side=-1 if '_l' in bone else 1
                        distal=bone.endswith('_tip')
                        wave=math.sin(t*math.tau-(.8 if distal else 0))
                        pose[bone]=[wave*.6,0,side*wave*amplitude*(.8 if distal else 1)*envelope]
                    elif 'tail' in bone:
                        wave=math.sin(t*math.tau-(1.0 if bone.endswith('_tip') else .4))
                        pose[bone]=[0,wave*(7 if bone.endswith('_tip') else 3)*envelope,0]
                lift=.8 if action=='fly' else -.5*min(1,t*4) if action=='sit' else 0
                pose['body']={'rotation':[-1 if action=='fly' else 0,0,s*.35*envelope],
                              'position':[0,lift+(1-c)*.18*envelope,0]}
            elif species=='frost_wyvern':
                # The wrist folds behind the shoulder at rest. In flight its
                # outer fingers follow the shoulder with a small phase delay.
                flying=action=='fly'
                greeting=math.sin(t*math.pi)**2 if action=='greet' else 0
                for bone in names:
                    if 'wing' not in bone:continue
                    side=-1 if bone.endswith('_l') else 1
                    tip=bone.startswith('wingtip_')
                    wave=math.sin(t*math.tau-(.65 if tip else 0))
                    if flying:
                        pose[bone]=[wave*(2 if tip else 1),-side*(10 if tip else 4),
                                    side*(wave*(15 if tip else 23)+4)]
                    else:
                        fold=(58 if tip else 32)-greeting*(25 if tip else 12)
                        pose[bone]=[wave*.4,-side*fold,side*((15 if tip else -25)+wave*.8)]
                if action=='idle':
                    # A little forward poise and breathing; geometry supplies
                    # the crouched haunches without swinging the feet through ground.
                    pose['body']={'rotation':[-2+s*.45,0,s*.18],'position':[0,(1-c)*.08,0]}
                    pose['head']=[2+s*1.2,s*2,0]
            elif species=='mochi_slime':
                # A jelly hops and wobbles: the blob lags each bounce of its base.
                hop={'idle':.25,'walk':2.2,'fly':1.4,'sit':0,'greet':1.6}[action]
                bounce=abs(s) if action=='walk' else (1-c)*.5
                pose['body']={'position':[0,bounce*hop,0]}
                pose['head']={'rotation':[s*2.5,s*4 if action=='idle' else s*6,s*3],'position':[0,-abs(c)*.25*(action!='sit'),0]}
            elif species=='aurora_jelly':
                # The bell pulses and the arms trail behind each pulse.
                lift=(1-c)*(.5 if action in ('idle','sit') else 1.0)
                pose['body']={'position':[0,lift+(1 if action in ('walk','fly') else 0),0],'rotation':[s*2,0,0]}
                pose['head']={'rotation':[s*1.5,s*3,0]}
                for bone in names:
                    if bone.startswith('tail'):
                        phase=int(bone.rsplit('_',1)[-1]) if bone[-1].isdigit() else 1
                        wave=math.sin(t*math.tau-phase*.9)
                        pose[bone]=[wave*(6 if action=='fly' else 4),wave*3,math.cos(t*math.tau-phase*.9)*4]
            elif species=='jade_lung':
                # The serpent body travels in a wave that runs from neck to tail.
                amplitude={'idle':5,'walk':9,'fly':12,'sit':3,'greet':7}[action]
                pose['body']={'position':[0,(1-c)*.6+(1 if action in ('walk','fly') else 0),0],'rotation':[s*2,s*3,0]}
                pose['tail']=[math.sin(t*math.tau-.9)*amplitude*.6,math.sin(t*math.tau-.6)*amplitude,0]
                pose['tail_tip']=[math.sin(t*math.tau-1.8)*amplitude*.8,math.sin(t*math.tau-1.4)*amplitude*1.3,0]
            elif species=='celestial_koi':
                sway={'idle':6,'walk':12,'fly':16,'sit':4,'greet':9}[action]
                pose['tail']=[0,math.sin(t*math.tau)*sway,0]
                pose['tail_tip']=[0,math.sin(t*math.tau-.9)*sway*1.4,0]
                pose['body']={'position':[0,(1-c)*.6+(1 if action in ('walk','fly') else 0),0],'rotation':[0,-s*2,0]}
            samples.append((round(t*length,4),pose))
        clips.append(make(action,length,samples,'hold' if action in ('sit','greet') else 'loop'))
    return clips


def build_model(species,stage):
    if species in MYTHICAL:
        from pet_mythical_sculpt import build
    elif species in MENAGERIE:
        from pet_menagerie_sculpt import build
    else:
        from pet_rare_sculpt import build
    sculpt=build(species,stage);bones=sculpt['bones'];palette=sculpt['palette']
    model='everpet_'+species+'_'+stage
    # Every face has its own hand-shaded region at an even texel density (pet_paint).
    texture,uvs,_=paint(species,stage,sculpt['parts'],palette);buffer=io.BytesIO();texture.save(buffer,format='PNG')
    elements=[];children={name:[] for name in bones}
    for i,p in enumerate(sculpt['parts']):
        e=dict(name=p['name'],type='cube',uuid=uid(model,'cube/'+str(i)),
               **{'from':p['from']},to=[round(p['from'][j]+p['size'][j],5) for j in range(3)],
               origin=p.get('origin',[p['from'][j]+p['size'][j]/2 for j in range(3)]),
               rotation=p.get('rotation',[0,0,0]),box_uv=False,
               faces={face:dict(uv=uvs[i][face],texture=0) for face in FACES},visibility=True,export=True,
               shade=not any(s in p['material'].lower() for s in ('glow','eye','rune','glint','shine','pupil','flame','ember')),autouv=0)
        elements.append(e);children[p['bone']].append(e['uuid'])
    def group(name):
        return dict(name=name,uuid=uid(model,'bone/'+name),origin=bones[name][1],rotation=[0,0,0],
            export=True,visibility=True,color=0,children=children[name]+[group(n) for n,(parent,_) in bones.items() if parent==name])
    hit=uid(model,'hitbox/cube')
    height=max(p['from'][1]+p['size'][1] for p in sculpt['parts'])
    elements.append(dict(name='hitbox',uuid=hit,type='cube',**{'from':[-4,0,-4]},to=[4,max(8,height),4],origin=[0,0,0],faces={}))
    data=dict(meta=dict(format_version='4.10',model_format='free',box_uv=False,
                       evergarden_animation_position_x='inverted',evergarden_pet_species=species,evergarden_pet_stage=stage,
                       evergarden_pet_revision='1.1-anatomy-and-attention'),
        name=model,model_identifier=model,resolution=dict(width=512,height=512),elements=elements,
        outliner=[group('root'),dict(name='hitbox',uuid=uid(model,'hitbox'),origin=[0,height*.7,0],children=[hit],export=True)],
        textures=[dict(name=model+'.png',namespace='',folder='',path='',id='0',uuid=uid(model,'texture'),width=512,height=512,
                       uv_width=512,uv_height=512,source='data:image/png;base64,'+base64.b64encode(buffer.getvalue()).decode())],
        animations=animations(model,bones,species),display={},variable_placeholders='')
    return data,texture


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--species',choices=SPECIES,nargs='*')
    args=parser.parse_args();OUT.mkdir(parents=True,exist_ok=True);ART.mkdir(parents=True,exist_ok=True)
    manifest=[]
    # The mythical five ship no blueprints until the legendary tier opens; name one to build it.
    for species in (args.species or [s for s in SPECIES if s not in MYTHICAL]):
        for stage in STAGES:
            data,texture=build_model(species,stage)
            target=OUT/(data['name']+'.bbmodel')
            target.write_text(json.dumps(data,separators=(',',':'),ensure_ascii=False)+'\n',encoding='utf8')
            texture.save(ART/(data['name']+'.png'))
            manifest.append(dict(id=data['name'],species=species,stage=stage,title=TITLES[SPECIES.index(species)],
                rarity='mythical' if species in MYTHICAL else 'rare',cubes=len(data['elements'])-1,
                bones=len(data['animations'][0]['animators']),animations=[a['name'] for a in data['animations']],
                sha256=hashlib.sha256(target.read_bytes()).hexdigest()))
    if not args.species:
        (OUT/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    print(f'Built {len(manifest)} pet models / {sum(m["cubes"] for m in manifest)} sculpted cubes')


if __name__=='__main__':main()
