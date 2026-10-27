"""First-person framing of the legendary weapons on Bedrock.

Bedrock now applies each Java first-person display transform through the
java2bedrock rig, so every weapon's handle reaches the same in-hand point as a
vanilla handheld item, at the Java view size. Replaces the 1.9.2-1.9.6
screenshot-fitted grip scale/lift, which no longer exists.
Arithmetic only; perspective, FOV and hand bob are not tested.
"""
import json
from pathlib import Path
import sys
import zipfile
import numpy as np

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools'))
sys.path.insert(0,str(ROOT/'tests/weapon-remodel'))
from legendary_weapon_assets import GRIPS
from hold_math import exported_values,rx,ry,rz
checks=0
def check(ok,label):
    global checks
    assert ok,label
    checks+=1

def item_frame(clip,point,off_hand=False):
    """The rig below the bound root (display z, y, x), in Bedrock model axes."""
    p=np.array(point,dtype=float);pivot=np.array([0,8,0])
    for bone in ('display_z','display_y','display_x'):
        rotation,position,scale=exported_values(clip,bone,off_hand)
        scale=np.array(scale if isinstance(scale,list) else [scale]*3)
        p=pivot+np.array(position)+rz(-rotation[2])@ry(rotation[1])@rx(-rotation[0])@(scale*(p-pivot))
    return p

with zipfile.ZipFile(ROOT/'advance-magic/dist/advance-magic-bedrock.mcpack') as pack:
    animations=json.loads(pack.read('animations/legendary_weapons.animation.json'))['animations']
    contacts={}
    for name,grip in GRIPS.items():
        clip=animations[f'animation.advance_magic.legendary_{name}.first']['bones']
        rotation,position,scale=exported_values(clip,'root',False)
        check(rotation==[90,60,-40] and position==[4,10,4] and scale==1.5,name+' java2bedrock first-person hand frame')
        size=exported_values(clip,'display_x',False)[2]
        check(np.allclose(size,[.62 if name=='longinus' else .68]*3),name+' Java first-person view size')
        contacts[name]=item_frame(clip,[0,grip,0])
        tip=item_frame(clip,[0,31,0])
        check(np.linalg.norm(tip-contacts[name])>12,name+' full length remains in view frame (not shrunk)')
    reference=contacts['excalibur']
    for name,contact in contacts.items():
        check(np.allclose(contact,reference,atol=1e-4),name+' handle reaches the same first-person hand point '+str(contact.round(3).tolist()))
print('PASS',checks,'first-person rig checks; no real client rendering')
