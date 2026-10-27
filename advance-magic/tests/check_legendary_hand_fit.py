"""Hand contact, Java grip placement and hotbar icon checks for the legendary weapons.

Uses a native-item coordinate fixture and exported files. The Bedrock hold is
checked corner by corner against Java in tests/weapon-remodel/check_weapon_hold.py.
These are transform checks, not a Minecraft client test.
"""
import io
import json
import math
from pathlib import Path
import sys
import zipfile
import numpy as np
from PIL import Image

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
from legendary_weapon_assets import make_weapon,atlas,GRIPS
from legendary_weapon_preview import render_item,render_icon_frames

REF=json.loads(Path(__file__).with_name('fixtures').joinpath('legendary-hand-reference.json').read_text())
checks=0
def check(ok,label):
    global checks
    assert ok,label
    checks+=1
def rotation(angles):
    x,y,z=np.deg2rad(angles)
    rx=np.array([[1,0,0],[0,np.cos(x),-np.sin(x)],[0,np.sin(x),np.cos(x)]])
    ry=np.array([[np.cos(y),0,np.sin(y)],[0,1,0],[-np.sin(y),0,np.cos(y)]])
    rz=np.array([[np.cos(z),-np.sin(z),0],[np.sin(z),np.cos(z),0],[0,0,1]])
    return rx@ry@rz

dist=ROOT/'advance-magic/dist'
with zipfile.ZipFile(dist/'advance-magic-java.zip') as java,zipfile.ZipFile(dist/'advance-magic-bedrock.mcpack') as bedrock:
    animations=json.loads(bedrock.read('animations/legendary_weapons.animation.json'))['animations']
    for name,grip_y in GRIPS.items():
        physical=make_weapon(name)
        check(any(all(c['from'][i]<=v<=c['to'][i] for i,v in enumerate([8,grip_y,8])) for c in physical.cubes),name+' contact lies inside solid handle')
        geo=json.loads(bedrock.read(f'models/entity/legendary_weapons/{name}.geo.json'))['minecraft:geometry'][0]
        bones={b['name']:b for b in geo['bones']}
        check(bones['display_z']['locators']['hand_contact']==[0,grip_y,0],name+' Bedrock hand contact marks the same handle point')
        check(bones['root']['pivot']==[0,8,0] and 'binding' in bones['root'],name+' bound java2bedrock root')
        model=json.loads(java.read(f'assets/advance_magic/models/item/legendary_weapons/{name}.json'))
        for first in (True,False):
            label='first' if first else 'third';prefix='firstperson_' if first else 'thirdperson_'
            clip=animations[f'animation.advance_magic.legendary_{name}.{label}']['bones']
            check(set(clip)=={'root','display_x','display_y','display_z'},name+label+' animates only the rig bones')
            for left in (False,True):
                pose=model['display'][prefix+('lefthand' if left else 'righthand')]
                scale=pose['scale'];r=list(pose['rotation']);t=list(pose['translation'])
                if left:r[1]*=-1;r[2]*=-1;t[0]*=-1
                actual=rotation(r)@((np.array([8,grip_y,8])-8)*scale)+t
                native=REF['java'][label]
                expected=rotation(native['rotation'])@((np.array(REF['java']['sprite_grip'])-8)*native['scale'])+native['translation']
                if first:expected+=np.array([0,.8,-1.5])
                if left:expected[0]*=-1
                check(np.allclose(actual,expected,atol=1e-5),name+label+str(left)+' Java grip sits at the vanilla handheld contact')
                check(len(set(scale))==1 and scale[0]==((.62 if name=='longinus' else .68) if first else .9),name+label+str(left)+' Java world and view sizes are independent')
        empty=Image.new('RGBA',(128,128));body=render_icon_frames(physical,atlas(),[empty])[0]
        l,t,r,b=body.getbbox();check(108<=max(r-l,b-t)<=118,name+' solid weapon fills hotbar icon')
        check(min(l,t,128-r,128-b)>=4,name+' enlarged icon does not clip solid weapon')
        old=render_item(physical,atlas(),None,size=128,yaw=24,tilt=-30)
        ol,ot,orr,ob=old.getbbox()
        check(max(r-l,b-t)>max(orr-ol,ob-ot)*1.15,name+' solid icon at least 15 percent larger')
print('PASS',checks,'hand contact / Java grip / icon scale checks (no client rendering test)')
