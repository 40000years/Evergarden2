"""Author six editable, isolated Model Engine/Blockbench studies and render their real rigs.

Preview artifacts only. Never writes plugin resources, packs, JARs or server folders.
Uses the same cube/animation coordinate convention as the verified Arkhaia R4.1.1 rig.
"""
import base64
import copy
import hashlib
import io
import json
import math
from pathlib import Path
import random
import uuid
import zipfile

from PIL import Image, ImageDraw, ImageFont, ImageFilter
import preview_arkhaia_rig as renderer

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'previews/arkhaia-minions-v1'
np=renderer.np
NAMES=['acheron','phlegethon','cocytus','banshee_wail','malefeasance','styx']
TITLES=['Acheron','Phlegethon','Cocytus','Banshee Wail','Malefeasance','Styx']
ROLES=['BLOOD FERRYMAN','GHOSTFLAME FURNACE','FROZEN LAMENT','THE SILENT SCREAM','SHADOW PUPPETEER','OATHBOUND EXECUTIONER']
ACCENTS=[(245,74,112),(255,151,64),(91,220,255),(131,239,214),(196,114,255),(229,194,113)]
PALETTES=[
    [(57,39,57),(155,116,130),(22,21,33),(60,25,47),(221,209,189),(249,78,119),(114,26,58),(108,94,116)],
    [(72,57,58),(185,128,77),(27,26,37),(55,38,43),(188,168,139),(132,245,248),(255,148,65),(114,92,87)],
    [(57,91,123),(150,214,235),(24,34,57),(42,59,91),(218,241,244),(101,231,255),(89,145,193),(132,184,213)],
    [(61,92,100),(144,203,192),(22,32,43),(43,57,71),(227,235,220),(149,255,220),(62,144,147),(119,150,161)],
    [(53,42,77),(148,117,186),(21,21,33),(42,32,62),(195,182,216),(206,127,255),(100,57,149),(90,71,116)],
    [(74,80,89),(215,182,112),(27,31,44),(42,48,66),(225,222,199),(148,241,209),(93,141,145),(135,143,153)],
]

def uid(name):return str(uuid.uuid5(uuid.NAMESPACE_URL,'evergarden/minion-study/v1/'+name))

def bones():
    b={'root':(None,[0,0,0]),'pelvis':('root',[0,13,0]),'chest':('pelvis',[0,22,0]),
       'hi_gaze':('chest',[0,30,0]),'head':('hi_gaze',[0,30,0]),'head_forward':('head',[0,31,-7]),
       'cloak':('chest',[0,27,3]),'cloak_tip':('cloak',[0,15,3]),
       'aura':('root',[0,23,0]),'focus':('chest',[9,17,-2])}
    for side,x in [('r',-1),('l',1)]:
        b.update({f'arm_{side}':('chest',[x*7.8,27,0]),f'forearm_{side}':(f'arm_{side}',[x*8.3,20,0]),
                  f'hand_{side}':(f'forearm_{side}',[x*8.3,14,-.5]),
                  f'leg_{side}':('pelvis',[x*2.55,13,0]),f'shin_{side}':(f'leg_{side}',[x*2.55,7,0]),
                  f'foot_{side}':(f'shin_{side}',[x*2.55,1,0]),
                  f'mantle_{side}':('chest',[x*5,27,2.5])})
    b['weapon']=('hand_r',[-8.3,14,-.5])
    return b

class Sculpt:
    def __init__(self,kind):self.kind=kind;self.parts=[]
    def box(self,bone,at,size,mat=0,rot=0,glow=False,label=None):
        self.parts.append(dict(bone=bone,at=at,size=size,mat=mat,rot=rot,glow=glow,label=label or bone))
    def shard(self,bone,x,y,z,height=5,width=1.6,mat=1,tilt=0):
        self.box(bone,[x-width/2,y,z-.55],[width,height*.65,1.1],mat,tilt)
        self.box(bone,[x-width*.31,y+height*.58,z-.4],[width*.62,height*.38,.8],mat,tilt)
        self.box(bone,[x-width*.12,y+height*.87,z-.24],[width*.24,height*.22,.48],5,tilt,True)
    def frame(self,heavy=False,robe=False):
        w=10.8 if heavy else 6.8 if self.kind in [3,4] else 8.6
        self.box('pelvis',[-4.2,12,-2.2],[8.4,3,4.4],2)
        self.box('chest',[-w/2,18,-2.7],[w,9,5.4],0)
        self.box('chest',[-1.5,26.5,-1.5],[3,3,3],2)
        for row,width in enumerate([7.2,6.6,6]):
            self.box('chest',[-width/2,15+row*1.15,-2.85],[width,.85,.65],0)
        for side,x in [('r',-1),('l',1)]:
            self.box('arm_'+side,[x*7.8-1.25,20,-1.5],[2.5,6.7,3],0)
            self.box('forearm_'+side,[x*8.3-1.35,14.8,-1.65],[2.7,4.8,3.3],0)
            self.box('hand_'+side,[x*8.3-1.12,12.5,-1.45],[2.24,2.4,2.9],4 if robe else 2)
            self.box('arm_'+side,[x*7.7-2,26,-2.3],[4,2.6,4.6],1,x*12)
            self.box('forearm_'+side,[x*8.3-1.6,15,-1.9],[3.2,.55,3.8],1)
            self.box('leg_'+side,[x*2.55-1.5,7.2,-1.6],[3,5.5,3.2],2 if robe else 0)
            self.box('shin_'+side,[x*2.55-1.45,1.7,-1.7],[2.9,5,3.4],0)
            self.box('foot_'+side,[x*2.55-1.7,0,-3],[3.4,2,5.1],0)
            self.box('shin_'+side,[x*2.55-1.05,2.7,-1.95],[2.1,3.3,.4],1)
            self.box('leg_'+side,[x*3-1.8,10,-2.55],[3.6,4,.75],0,x*9)
        self.box('pelvis',[-4.5,14.3,-2.9],[9,.8,5.8],1)
        self.box('pelvis',[-1,14,-3.3],[2,1.6,.8],6,45)
    def skull(self,hood=True,visor=False):
        self.box('head',[-2.9,29,-2.7],[5.8,6.2,5.4],4)
        if hood:
            self.box('head',[-4,34,-3.2],[8,2,6.8],3)
            for side in [-1,1]:self.box('head',[side*3.15-.85,28.5,-3.1],[1.7,6.5,6.7],3,side*7)
            self.box('head',[-3.5,28.5,2],[7,6.5,1.6],3)
        for x in [-1.5,1.5]:
            self.box('head',[x-.95,32,-2.95],[1.9,.8,.4],2)
            self.box('head',[x-.65,32.15,-3.18],[1.3,.25,.18],5,0,True)
        self.box('head',[-.35,30.2,-3.1],[.7,1.25,.4],2)
        for x in [-1.6,-.8,0,.8,1.6]:self.box('head',[x-.2,29.2,-2.95],[.4,.75,.4],2)
        if visor:
            self.box('head',[-3.3,31.9,-3.35],[6.6,1.1,.45],1)
            self.box('head',[-2.5,30.8,-3.5],[5,.22,.25],5,0,True)
    def cape(self,torn=False):
        self.box('cloak',[-5,15,3.3],[10,12,.7],3)
        for i in range(5):
            self.box('cloak_tip',[-5+i*2,3+(i%2)*2 if torn else 4,3.4],[1.8,12-(i%2)*2,.65],3,(i-2)*3)
            self.box('cloak_tip',[-4.85+i*2,5,4.1],[.18,9,.15],1)
    def staff(self,mat=1):
        self.box('weapon',[-8.65,1,-1],[.7,31,.7],mat)
        for y in [4,14,24]:self.box('weapon',[-9.05,y,-1.4],[1.5,.8,1.5],1)
    def make(self):
        k=self.kind;self.frame(heavy=k in [1,5],robe=k in [0,3,4])
        if k==0:
            self.skull();self.cape(True);self.staff(2)
            # A crescent, built as stepped forged segments rather than a flat billboard.
            self.box('weapon',[-8.65,30.5,-1],[.7,4.2,.7],1)
            for i in range(8):
                a=math.pi/2+i*.18;cx=-8.3+math.cos(a)*8;cy=26+math.sin(a)*8
                tilt=math.degrees(a)+90;thick=1.3-i*.09
                self.box('weapon',[cx-1.1,cy-thick/2,-1.25],[2.2,thick,.9],7,tilt)
                self.box('weapon',[cx-.95,cy-thick/2-.12,-1.78],[1.9,.19,.14],5,tilt,True)
            for side,x in [('r',-1),('l',1)]:
                for i in range(3):self.shard('arm_'+side,x*(7.3+i*.6),28+i*.4,.3,3.3,1.1,1,x*25)
            for i in range(4):
                self.box('chest',[-3.5+i*.2,24-i*1.3,-3.2],[3,.6,.6],4,-12)
                self.box('chest',[.5-i*.2,24-i*1.3,-3.2],[3,.6,.6],4,12)
            self.shard('chest',0,19,-3.7,3,1.8,6)
            for y in [20,18.5,17,15.5]:self.box('chest',[-5.5,y,1],[.8,1,.7],1,45)
        elif k==1:
            self.skull(False,True)
            self.box('chest',[-4.8,18,-3.4],[9.6,8,1.6],2)
            self.box('chest',[-3.8,19,-3.8],[7.6,6,.4],5,0,True)
            for x in [-3.4,-1.7,0,1.7,3.4]:self.box('chest',[x-.3,18.5,-4.15],[.6,7,.55],1)
            for y in [19,24.6]:self.box('chest',[-4.5,y,-4.1],[9,.7,.6],1)
            for side,x in [('r',-1),('l',1)]:
                self.box('mantle_'+side,[x*7.4-1.8,28,1],[3.6,5,3.6],0)
                self.box('mantle_'+side,[x*7.4-1.3,32,1.5],[2.6,1,2.6],2)
                for i in range(3):self.shard('mantle_'+side,x*7.4+(i-1)*.8,33,2.5,4+i%2,1.1,5,(i-1)*13)
                self.box('forearm_'+side,[x*8.3-1.5,17,-1.95],[3,.7,.3],6,0,True)
                self.box('foot_'+side,[x*2.55-1.7,.6,-3.2],[3.4,.5,.3],6,0,True)
            self.staff(0);self.box('weapon',[-12.3,24,-2.5],[8,5,4],0)
            self.box('weapon',[-12.7,24.6,-3.1],[1.2,3.8,5.2],1)
            self.box('weapon',[-5,24.6,-3.1],[1.2,3.8,5.2],1)
            self.box('weapon',[-11,25,-2.8],[5.8,2.6,.3],6,0,True)
        elif k==2:
            self.skull(False,True);self.cape()
            for i in range(5):self.shard('head',(i-2)*1.55,34.5,0,5.5-abs(i-2),1.2,1,(i-2)*10)
            for side,x in [('r',-1),('l',1)]:
                for i in range(3):self.shard('arm_'+side,x*(7.2+i*.9),27+i*.5,.5,5-i*.4,1.3,1,x*(12+i*12))
            for y,w in [(25,7.8),(23.5,6.3),(22,4.7)]:self.box('chest',[-w/2,y,-3.1],[w,.45,.5],1)
            self.shard('chest',0,19.5,-3.7,4,2.4,5)
            self.staff()
            for i in [-1,0,1]:self.shard('weapon',-8.3+i*1.8,30,-1,7 if i==0 else 4.5,1.2,1,i*13)
        elif k==3:
            self.skull();self.cape(True)
            # Open mask, tear tracks and veil instead of another armored face.
            self.box('head',[-1.1,29.2,-3.18],[2.2,2.4,.35],2)
            for x in [-1.65,1.65]:self.box('head',[x-.17,30.5,-3.24],[.34,1.8,.22],5,0,True)
            self.box('head',[-3.2,35.4,-2.7],[6.4,.6,5.4],1)
            for i in range(5):
                x=(i-2)*1.75
                self.box('pelvis',[x-.85,3+(i%2)*2,-2.7],[1.7,12-(i%2)*2,.65],3,(i-2)*4)
            for side,x in [('r',-1),('l',1)]:
                for f in range(3):self.box('hand_'+side,[x*8.3-1+f*.65,10.7,-1.5],[.4,2.4,.55],4,0)
                self.box('mantle_'+side,[x*5-1.2,18,2.8],[2.4,12,.5],3,x*13)
            for i in range(6):
                a=i*math.pi/3
                self.box('aura',[math.cos(a)*6.3-.45,31+math.sin(a)*6.3,3.3],[.9,1.7,.7],5,math.degrees(a),True)
        elif k==4:
            self.skull();self.cape(True)
            self.box('head',[-2.65,29,-3.3],[5.3,5.4,.3],2)
            for x,y in [(-1.35,32),(1.35,32),(0,33.5)]:self.box('head',[x-.5,y,-3.55],[1,.45,.25],5,0,True)
            for side,x in [('r',-1),('l',1)]:
                for j in range(3):
                    self.box('mantle_'+side,[x*(5+j*1.7)-.45,25+j*2.1,3.8],[.9,5.8,.8],0,x*(35+j*12))
                    self.shard('mantle_'+side,x*(9+j*.8),29+j*1.6,3.8,2.5,1,5,x*24)
            self.box('chest',[-3,21,-3.3],[6,.45,.4],1,-18)
            self.box('chest',[-3,21,-3.3],[6,.45,.4],1,18)
            self.shard('chest',0,20,-3.8,3,2.8,6)
            self.staff(2)
            for i in range(5):
                a=2*math.pi*i/5;self.box('focus',[9+math.cos(a)*2.5-.45,18+math.sin(a)*2.5,-3],[.9,1.4,.9],1,math.degrees(a))
            self.box('focus',[7.8,16.9,-3.4],[2.4,2.4,2.4],5,45,True)
            self.shard('weapon',-8.3,30,-.5,5,2,6)
        else:
            self.skull(False,True);self.cape()
            self.box('head',[-3.7,34.5,-3.2],[7.4,1.5,6.4],0)
            for i in range(5):self.shard('head',(i-2)*1.6,35.5,.3,3.6,1.1,1,(i-2)*7)
            for side,x in [('r',-1),('l',1)]:
                self.box('arm_'+side,[x*7.8-2.5,27,-2.7],[5,2.2,5.4],0,x*12)
                self.box('arm_'+side,[x*7.8-2.4,27,-2.95],[4.8,.5,.5],1,x*12)
                for j in range(2):self.box('chest',[x*(2.1+j*.6)-1.3,23-j*1.8,-3.4],[2.6,2.1,.65],4,x*18)
            self.shard('chest',0,20,-4,4.4,2,5)
            # Downward ceremonial greatsword; grip stays outside the torso sweep.
            self.box('weapon',[-8.65,12,-1],[.7,4.5,.7],2)
            self.box('weapon',[-12,12,-1.6],[7.4,.9,2],1)
            for row,width in enumerate([.8,1.8,2.6,3.1]):
                self.box('weapon',[-8.3-width/2,.9+row*2.8,-1.2],[width,3,.9],7)
                self.box('weapon',[-8.44,1.2+row*2.8,-1.4],[.28,2.4,.2],5,0,True)
            for y in [9,7.5,6]:self.box('weapon',[-9,y,-1.45],[1.4,.22,.2],1)
        # Final silhouette work: full masks, layered shoulders and distinct robes.
        if k in [1,2,5]:
            self.box('head',[-3.05,29,-3.4],[6.1,6.3,.6],0)
            self.box('head',[-2.7,32,-3.75],[5.4,.65,.25],2)
            self.box('head',[-2.25,32.17,-3.95],[4.5,.24,.2],5,0,True)
            for x in [-1.5,1.5]:self.box('head',[x-.5,32.4,-4.15],[1,.4,.2],5,0,True)
            for side in [-1,1]:
                self.box('head',[side*2.1-.65,29.2,-3.7],[1.3,2.8,.65],1,side*16)
            self.box('head',[-.38,29.2,-3.85],[.76,2.35,.4],1)
        if k==1:
            for side,x in [('r',-1),('l',1)]:
                self.box('forearm_'+side,[x*8.3-1.85,15,-2],[3.7,4.5,4],0)
                self.box('forearm_'+side,[x*8.3-1.5,17,-2.25],[3,.6,.4],6,0,True)
                self.box('chest',[x*4.4-1.1,20,-3.5],[2.2,6,1.3],0,x*12)
            self.box('head',[-3.6,35,-3.1],[7.2,1.25,6.2],0)
        if k==2:
            for side,x in [('r',-1),('l',1)]:
                self.box('chest',[x*2.1-1.35,23.6,-3.5],[2.7,3,.55],1,x*17)
                self.box('shin_'+side,[x*2.55-.23,2.5,-2.25],[.46,4.5,.3],5,0,True)
        if k in [3,4]:
            # These are robed apparitions rather than recolored armored skeletons.
            for i in range(5):
                x=(i-2)*1.9;bottom=.8+(i%2)*1.6
                self.box('pelvis',[x-.92,bottom,-3.15],[1.84,13-bottom,.65],3,(i-2)*3)
                self.box('pelvis',[x-.72,bottom+.4,-3.48],[.17,10-bottom,.2],1,(i-2)*3)
            for side,x in [('r',-1),('l',1)]:
                self.box('mantle_'+side,[x*5.7-1.1,4,2.9],[2.2,23,.7],3,x*9)
                self.box('forearm_'+side,[x*8.3-1.55,12.7,-1.9],[3.1,6.7,.5],3,x*6)
        if k==3:
            for side,x in [('r',-1),('l',1)]:
                self.box('head',[x*3-.55,25.5,-3.5],[1.1,9,.75],3,x*7)
                self.box('head',[x*3.7-.65,29,1],[1.3,8,1.1],3,x*14)
            self.box('head',[-1.05,28.9,-3.4],[2.1,2.75,.4],2)
            self.box('head',[-.65,28.4,-3.45],[1.3,.4,.4],4)
            # Translucency is left for the resource exporter; sculpted veil is real geometry.
            self.box('chest',[-2.5,24.8,-3],[5,.6,.7],1)
        if k==4:
            for x,y in [(-1.35,32),(1.35,32),(0,33.7)]:self.box('head',[x-.38,y,-3.72],[.76,.6,.25],5,45,True)
            for i in range(3):self.shard('head',(i-1)*2.1,36,.4,3.2-abs(i-1),.9,1,(i-1)*15)
        if k==5:
            self.box('chest',[-2.8,22.3,-3.6],[5.6,.65,.7],1)
            self.box('head',[-3.3,33.7,-3.45],[6.6,.5,.4],1)
            for side,x in [('r',-1),('l',1)]:
                for row in range(3):self.box('arm_'+side,[x*7.8-2.1,24.8-row*.9,-2.8],[4.2,.7,.8],0,x*12)
            for p in self.parts:
                if p['bone']=='weapon' and p['at'][1]<12:p['size'][0]*=1.25
        if k!=3:
            # Hold the polearm/sword forward and outside the arm silhouette.
            for p in self.parts:
                if p['bone']=='weapon':p['at'][0]-=2.4;p['at'][2]-=2
            self.box('hand_r',[-10.6,12.9,-3.1],[3,1.6,3.8],4 if k in [0,4] else 2)
        return self.parts

def atlas(index):
    im=Image.new('RGBA',(1024,1024),(0,0,0,0));rng=random.Random(4100+index)
    for material,color in enumerate(PALETTES[index]):
        tile=Image.new('RGBA',(128,128));pixels=tile.load()
        for y in range(128):
            for x in range(128):
                edge=min(x,y,127-x,127-y);shade=.87+.16*y/127+(0.13 if edge<3 else 0)+rng.uniform(-.012,.012)
                pixels[x,y]=tuple(min(255,max(0,int(c*shade))) for c in color)+(255,)
        d=ImageDraw.Draw(tile);light=tuple(min(255,int(v*1.22)+6) for v in color)+(255,);dark=tuple(int(v*.62) for v in color)+(255,)
        if material in [0,1,7]:
            d.rectangle((6,6,121,121),outline=dark,width=2);d.line([(10,116),(10,12),(116,12)],fill=light,width=2)
            for x,y in [(16,18),(112,18),(16,110),(112,110)]:d.rectangle((x-2,y-2,x+2,y+2),fill=light)
            for y in [36,64,92]:d.line([(43,y+6),(50,y),(57,y+6),(64,y-3),(72,y+6),(79,y),(86,y+6)],fill=dark,width=2)
        if material==3:
            for x in range(10,128,17):d.line([(x,0),(x-3,50),(x+2,127)],fill=dark,width=2)
            d.line([(0,119),(128,119)],fill=light,width=2)
        if material==4:
            for x in [23,57,99]:d.line([(x,10),(x-5,40),(x+3,47),(x-1,64)],fill=dark,width=1)
        if material==5:
            d.rectangle((9,9,118,118),outline=light,width=3)
            for y in range(20,120,22):d.line([(20,y),(108,y)],fill=light,width=2)
        im.paste(tile,(material*128,0))
    return im

def clips(index,b):
    rest={name:[0,0,0] for name in b};rest.update(arm_r=[0,0,-5],arm_l=[0,0,5],cloak=[7,0,0],cloak_tip=[4,0,0])
    def make(name,length,poses,positions=None,loop=False):
        animators={}
        for bone in b:
            keys=[]
            for time,pose in poses:
                v=pose.get(bone,rest[bone]);v=[-v[0],-v[1],v[2]] # Same R4 importer convention as Arkhaia.
                keys.append(dict(channel='rotation',data_points=[dict(zip('xyz',map(str,v)))],uuid=uid(f'{index}/{name}/{bone}/{time}/r'),time=time,interpolation='linear'))
            if positions:
                for time,by_bone in positions:
                    v=by_bone.get(bone,[0,0,0]);keys.append(dict(channel='position',data_points=[dict(zip('xyz',map(str,v)))],uuid=uid(f'{index}/{name}/{bone}/{time}/p'),time=time,interpolation='linear'))
            animators[uid(f'{index}/bone/{bone}')]=dict(name=bone,type='bone',keyframes=keys)
        return dict(uuid=uid(f'{index}/{name}'),name=name,loop='loop' if loop else 'once',override=False,length=length,snapping=20,anim_time_update='',blend_weight='',start_delay='',loop_delay='',animators=animators)
    breathe=copy.deepcopy(rest);breathe.update(chest=[-2,0,0],forearm_l=[7,0,0],cloak_tip=[9,0,0])
    out=[make('idle',2,[(0,rest),(1,breathe),(2,rest)],loop=True)]
    poses=[];positions=[]
    for step in range(17):
        t=step/16;phase=t*math.tau;pose=copy.deepcopy(rest)
        for side,offset in [('r',0),('l',math.pi)]:
            v=math.sin(phase+offset);hip=v*23;knee=-max(0,-v)*32
            pose['leg_'+side]=[hip,0,0];pose['shin_'+side]=[knee,0,0];pose['foot_'+side]=[-hip-knee,0,0]
            pose['arm_'+side]=[-v*(9 if side=='r' else 18),0,-5 if side=='r' else 5];pose['forearm_'+side]=[7+max(0,v)*10,0,0]
        pose.update(chest=[0,math.sin(phase)*3,0],cloak=[8+math.sin(phase)*3,0,0],cloak_tip=[10+math.sin(phase-.4)*5,0,0])
        poses.append((round(t,4),pose));positions.append((round(t,4),{'pelvis':[0,.22*math.sin(phase)**2,0]}))
    out.append(make('walk',1,poses,positions,True))
    raised=copy.deepcopy(rest);raised.update(arm_l=[78,8,10],forearm_l=[14,0,0],hand_l=[0,0,0],chest=[-4,0,0],mantle_l=[0,-8,8],mantle_r=[0,8,-8])
    release=copy.deepcopy(raised);release.update(arm_l=[92,8,10],forearm_l=[-4,0,0],chest=[3,0,0])
    if index==3:
        raised.update(arm_r=[60,-12,-25],forearm_r=[24,0,0],head=[-8,0,0]);release.update(arm_r=[80,-12,-28],forearm_r=[7,0,0],head=[-15,0,0])
    ready=copy.deepcopy(rest);ready.update(arm_r=[140,-5,-8],forearm_r=[75,0,0],chest=[-5,-4,0])
    contact=copy.deepcopy(rest);contact.update(arm_r=[52,-8,-6],forearm_r=[-8,0,0],chest=[7,5,0])
    for action,a,z in [('cast',raised,release),('attack',ready,contact)]:
        out.extend([make(action+'_windup',.8,[(0,rest),(.65,a),(.8,a)]),make(action+'_active',.4,[(0,a),(.2,z),(.4,z)]),make(action+'_recover',.7,[(0,z),(.5,rest),(.7,rest)])])
    stun=copy.deepcopy(rest);stun.update(chest=[15,0,-4],head=[18,0,0])
    out.append(make('stun',1.5,[(0,rest),(.15,stun),(1.3,stun),(1.5,rest)]))
    dead=copy.deepcopy(stun);dead.update(root=[75,0,8],chest=[20,0,0])
    out.append(make('death',1,[(0,rest),(.7,dead),(1,dead)]))
    return out

def model(index):
    b=bones();elements=[];children={bone:[] for bone in b}
    for i,p in enumerate(Sculpt(index).make()):
        at=p['at'];size=p['size'];mat=p['mat'];u=mat*128
        e=dict(name=f'{p["label"]}_{i}',type='cube',uuid=uid(f'{index}/cube/{i}'),**{'from':at},to=[at[j]+size[j] for j in range(3)],
               origin=[at[j]+size[j]/2 for j in range(3)],rotation=[0,0,p['rot']],box_uv=False,
               faces={face:dict(uv=[u,0,u+128,128],texture=0) for face in ['north','south','east','west','up','down']},visibility=True,export=True,shade=not p['glow'],autouv=0)
        elements.append(e);children[p['bone']].append(e['uuid'])
    def group(name):return dict(name=name,uuid=uid(f'{index}/bone/{name}'),origin=b[name][1],rotation=[0,0,0],export=True,
                               children=children[name]+[group(child) for child in b if b[child][0]==name])
    tex=atlas(index);buffer=io.BytesIO();tex.save(buffer,format='PNG')
    hit=uid(f'{index}/hitbox/cube');elements.append(dict(name='hitbox',type='cube',uuid=hit,**{'from':[-5,0,-3]},to=[5,34,3],origin=[0,0,0],faces={}))
    data=dict(meta=dict(format_version='4.10',model_format='free',box_uv=False),name='arkhaia_'+NAMES[index],model_identifier='arkhaia_'+NAMES[index],
              resolution=dict(width=1024,height=1024),elements=elements,outliner=[group('root'),dict(name='hitbox',uuid=uid(f'{index}/hitbox'),origin=[0,0,0],children=[hit])],
              textures=[dict(name=NAMES[index]+'.png',id='0',uuid=uid(f'{index}/texture'),width=1024,height=1024,uv_width=1024,uv_height=1024,
                             source='data:image/png;base64,'+base64.b64encode(buffer.getvalue()).decode())],animations=clips(index,b))
    return data,tex

def load(data,tex):
    renderer.MODEL=data;renderer.TEXTURE=np.asarray(tex);renderer.ELEMENTS={e['uuid']:e for e in data['elements']};renderer.CLIPS={c['name']:c for c in data['animations']}

def picture(index,clip='idle',time=0,yaw=22,gaze=None,size=520):
    image=renderer.render(clip,time,yaw=yaw,elevation=8,size=size,gaze=gaze)
    # Soft studio ground is behind actual textured geometry; no conceptual painted model.
    bg=Image.new('RGB',(size,size),(23,21,34));d=ImageDraw.Draw(bg);c=ACCENTS[index]
    for radius in [size*.20,size*.26]:
        d.ellipse((size/2-radius,size*.83-radius*.18,size/2+radius,size*.83+radius*.18),outline=tuple(int(v*.32) for v in c),width=1)
    mask=Image.fromarray(np.any(np.asarray(image)!=[23,21,34],axis=2).astype('uint8')*255)
    bg.paste(image,(0,0),mask);return bg

def check(data,tex):
    load(data,tex);checks=0
    # Required topology, finite coordinates and matching importer channels, not a runtime AI promise.
    def flatten(groups):
        for group in groups:
            if isinstance(group,dict):yield group;yield from flatten(group.get('children',[]))
    groups=list(flatten(data['outliner']));names={g['name'] for g in groups}
    assert {'hi_gaze','head','leg_r','shin_r','foot_r','leg_l','shin_l','foot_l','weapon','hitbox'}<=names;checks+=1
    ids=[e['uuid'] for e in data['elements']]+[g['uuid'] for g in groups];assert len(set(ids))==len(ids);checks+=1
    for e in data['elements']:
        assert all(math.isfinite(float(v)) for v in e['from']+e['to']);checks+=1
        assert all(a<b for a,b in zip(e['from'],e['to']));checks+=1
    for clip in data['animations']:
        for animator in clip['animators'].values():
            assert animator['name'] in names and all(0<=f['time']<=clip['length'] for f in animator['keyframes']);checks+=1
    for t in np.linspace(0,1,33):
        for cube,matrix in renderer.transformed('walk',float(t)):
            if cube['name'].startswith(('foot_r_','foot_l_')):
                lo,hi=cube['from'],cube['to'];verts=np.array([[x,y,z,1] for x in [lo[0],hi[0]] for y in [lo[1],hi[1]] for z in [lo[2],hi[2]]])
                assert (verts@matrix.T)[:,1].min()>-.025,'sole penetrates floor';checks+=1
    # Native -Z front becomes Bukkit +Z after the renderer's body basis conversion.
    for yaw in [-45,0,45]:
        samples=renderer.transformed('idle',0,gaze=(yaw,20));assert all(np.isfinite(m).all() for e,m in samples);checks+=1
        head=next(m for e,m in samples if e['name'].startswith('head_'))
        actual=head[:3,:3]@np.array([0,0,-1]);a,p=math.radians(yaw),math.radians(20)
        expected=np.array([math.sin(a)*math.cos(p),-math.sin(p),-math.cos(a)*math.cos(p)])
        assert np.dot(actual,expected)>.9999,'head yaw/pitch points away from preview target';checks+=1
        chest=next(m for e,m in samples if e['name'].startswith('chest_'));assert np.allclose(chest[:3,:3],np.eye(3));checks+=1
    hand=next(m for e,m in renderer.transformed('cast_windup',.8) if e['name'].startswith('hand_l_'))
    assert (hand@np.array([8.3,13.5,-.5,1]))[2]<-5,'casting palm points backwards';checks+=1
    return checks

def main():
    OUT.mkdir(parents=True,exist_ok=True);models=OUT/'models';models.mkdir(exist_ok=True)
    font=ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf',27);small=ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',16)
    sheet=Image.new('RGB',(1560,1370),(16,15,25));d=ImageDraw.Draw(sheet)
    d.text((30,22),'ARKHAIA / THE SIX RIVERS',font=font,fill=(235,224,250));d.text((32,61),'MODEL STUDIES 01   /   Actual editable geometry + rigged motion   /   Preview only',font=small,fill=(166,153,190))
    summary=[]
    for i,name in enumerate(NAMES):
        data,tex=model(i);checks=check(data,tex);(models/(name+'.bbmodel')).write_text(json.dumps(data,separators=(',',':')),encoding='utf8');tex.save(models/(name+'.png'))
        load(data,tex);card=Image.new('RGB',(520,620),(23,21,34));card.paste(picture(i),(0,45));draw=ImageDraw.Draw(card)
        draw.text((25,18),f'0{i+1} / {TITLES[i]}',font=font,fill=ACCENTS[i]);draw.text((26,57),ROLES[i],font=small,fill=(181,173,199))
        draw.line((25,565,495,565),fill=tuple(int(v*.55) for v in ACCENTS[i]),width=1)
        draw.text((26,580),f'{len(data["elements"])-1} cubes  /  {len(bones())} bones  /  10 clips',font=small,fill=(180,171,196));card.save(OUT/(name+'.png'))
        sheet.paste(card,(i%3*520,105+i//3*630))
        angles=Image.new('RGB',(1560,580),(23,21,34));ad=ImageDraw.Draw(angles)
        for j,(yaw,label) in enumerate([(0,'FRONT'),(90,'SIDE'),(180,'BACK')]):
            angles.paste(picture(i,yaw=yaw),(j*520,35));ad.text((j*520+25,20),label,font=small,fill=ACCENTS[i])
        angles.save(OUT/(name+'-turnaround.png'))
        frames=[]
        for n in range(24):
            t=n/24;frame=picture(i,'walk',t,gaze=(math.sin(t*math.tau)*32,12),size=420);draw=ImageDraw.Draw(frame)
            draw.text((15,12),TITLES[i]+' / walk + head tracking',font=small,fill=ACCENTS[i]);frames.append(frame)
        frames[0].save(OUT/(name+'-walk.gif'),save_all=True,append_images=frames[1:],duration=[80,85,85]*8,loop=0,optimize=False)
        poses=Image.new('RGB',(1560,580),(23,21,34));pd=ImageDraw.Draw(poses)
        for j,(clip,t,label) in enumerate([('idle',0,'IDLE'),('cast_windup',.8,'CAST / HAND FORWARD'),('stun',.4,'STUN')]):
            poses.paste(picture(i,clip,t),(j*520,35));pd.text((j*520+25,20),label,font=small,fill=ACCENTS[i])
        poses.save(OUT/(name+'-poses.png'))
        summary.append(dict(name=name,cubes=len(data['elements'])-1,bones=len(bones()),clips=[a['name'] for a in data['animations']],checks=checks))
        print(name,summary[-1]['cubes'],'cubes,',checks,'checks',flush=True)
    sheet.save(OUT/'six-rivers-lineup.png')
    readme='''ARKHAIA / SIX RIVERS - MODEL STUDIES 01

Preview only. No server, JAR, installed blueprint or resource pack changed.
Open models/*.bbmodel in Blockbench. Each contains its own embedded painted atlas.
These are cube-based free-format Blockbench rigs using the existing Arkhaia R4.1.1 convention.
Native facing is -Z. hi_gaze is the inherited head pivot; body yaw belongs to AI.
Runtime movement, yaw, head targeting and clip selection still need an integration adapter.
No new AI or production resource-pack/Geyser entity registration is installed here.

Walk has separate hips, knees and ankle counterrotation, opposite arm swing and trailing cloak.
GIF gaze is a preview target sweep, not proof of connected-game target tracking.
The following remain review-stage: attack contact trajectories, cape/body clipping,
actual R4.1.1 import/runtime rendering, Bedrock export and connected-client visual tests.
Cast poses and feet/floor bounds are previewed; full combat contact validation comes after design review.
Do not replace live Arkhaia's blueprint with a minion study.
'''
    (OUT/'README.txt').write_text(readme,encoding='utf8');(OUT/'manifest.json').write_text(json.dumps(summary,indent=2),encoding='utf8')
    html='''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>Arkhaia - The Six Rivers / Model Studies</title>
<style>body{background:#100f19;color:#e9dff7;font:16px Segoe UI,sans-serif;max-width:1560px;margin:32px auto;padding:20px}h1{letter-spacing:3px}p{color:#b8accb}img{max-width:100%;height:auto;border-radius:12px}main{display:grid;grid-template-columns:repeat(auto-fit,minmax(330px,1fr));gap:20px}article{background:#171522;padding:18px;border:1px solid #393047;border-radius:14px}a{color:#cbb2fa}summary{cursor:pointer;padding:12px}article img{width:100%}.row{margin:28px 0}</style>
<h1>ARKHAIA / THE SIX RIVERS</h1><p>Editable model studies, preview only. The images are rendered from the included .bbmodel geometry. Walk and head tracking below are rig previews; live integration is still pending.</p><img src="six-rivers-lineup.png"><main>'''
    for i,name in enumerate(NAMES):
        html+=f'<article><h2>{TITLES[i]}</h2><p>{ROLES[i]}</p><img loading="lazy" src="{name}-walk.gif"><p><a href="models/{name}.bbmodel" download>Editable .bbmodel</a> / <a href="models/{name}.png">Texture atlas</a></p><details><summary>Front / side / back</summary><a href="{name}-turnaround.png"><img loading="lazy" src="{name}-turnaround.png"></a></details><details><summary>Idle / cast / stun poses</summary><a href="{name}-poses.png"><img loading="lazy" src="{name}-poses.png"></a></details></article>'
    html+='</main><p>Native facing -Z / separated knees + ankles / hi_gaze head pivot / 10 animation clips. No changes installed to Serva. See README for validation limits.</p></html>'
    (OUT/'index.html').write_text(html,encoding='utf8')
    with zipfile.ZipFile(OUT/'arkhaia-six-rivers-model-studies.zip','w',zipfile.ZIP_DEFLATED) as z:
        for file in sorted(OUT.rglob('*')):
            if file.is_file() and file.suffix!='.zip':z.write(file,file.relative_to(OUT))
    print('Preview:',OUT/'six-rivers-lineup.png',flush=True)

if __name__=='__main__':main()
