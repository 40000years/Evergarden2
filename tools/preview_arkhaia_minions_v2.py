"""Second Six Rivers sculpt study. All output stays in previews/arkhaia-minions-v2.

Distinct anatomies, bind-pose arm chains, actual finger/grip geometry, individual
weapons, embroidered atlases, compact separate ritual textures and motion studies.
No production resource, blueprint, pack, server or JAR is written.
"""
import argparse
import base64
import copy
import io
import json
import math
from pathlib import Path
import random
import uuid
import zipfile

from PIL import Image, ImageDraw, ImageFont, ImageFilter
import preview_arkhaia_rig as rig

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'previews/arkhaia-minions-v2'
np=rig.np
NAMES=['acheron','phlegethon','cocytus','banshee_wail','malefeasance','styx']
TITLES=['Acheron','Phlegethon','Cocytus','Banshee Wail','Malefeasance','Styx']
ROLES=['THE CRIMSON CORSAIR','THE SOUL FURNACE','THE RIME HUNTRESS','THE DROWNED BRIDE','THE VEIL SCRIBE','THE LAST EXECUTIONER']
WEAPONS=['Twin hooked daggers','Forged fists / no held weapon','Recurve ice bow / back quiver','Open hands / voice and veil','Levitating grimoire / shadow familiar','Two-handed crescent axe']
COLORS=[(245,66,104),(255,164,74),(100,224,255),(124,244,206),(191,119,255),(238,198,109)]
SCHEMES=[
    [(58,39,54),(189,132,130),(20,23,33),(66,28,43),(224,211,185),(255,94,119),(155,39,68),(171,170,186)],
    [(58,60,68),(202,147,89),(20,27,35),(45,43,47),(161,157,141),(132,255,242),(255,155,65),(123,134,147)],
    [(62,105,139),(165,227,244),(21,37,60),(43,67,97),(222,244,247),(146,242,255),(73,148,197),(189,221,238)],
    [(72,113,119),(152,208,191),(21,31,44),(50,78,86),(234,231,211),(154,255,221),(59,144,139),(156,178,182)],
    [(57,43,82),(160,132,187),(19,24,38),(47,34,68),(205,190,222),(217,148,255),(104,62,150),(135,118,166)],
    [(73,83,102),(215,180,113),(24,32,47),(47,52,68),(231,223,197),(161,244,208),(99,159,151),(178,190,204)],
]
FACES=['north','south','east','west','up','down']

def uid(s):return str(uuid.uuid5(uuid.NAMESPACE_URL,'evergarden/six-rivers/v2/'+s))
def mix(a,b,t):return tuple(round(x*(1-t)+y*t) for x,y in zip(a,b))

class Study:
    def __init__(self,index):
        self.i=index;self.parts=[];self.bones={};self.grips=[]
        self.addbone('root',None,[0,0,0]);self.addbone('pelvis','root',[0,15,0]);self.addbone('chest','pelvis',[0,24,0])
        h=31.5 if index==1 else 34
        self.addbone('neck','chest',[0,29,0]);self.addbone('hi_gaze','neck',[0,h,0]);self.addbone('head','hi_gaze',[0,h,0])
        self.addbone('head_forward','head',[0,h,-7]);self.addbone('collar','chest',[0,28,1])
        self.addbone('cape','chest',[0,28,3]);self.addbone('cape_tip','cape',[0,15,4]);self.addbone('focus','chest',[-10,18,-5])
        self.addbone('familiar','chest',[13,25,0]);self.addbone('halo','chest',[0,35,3])
        self.addbone('jaw','head',[0,h-.5,-2.5])
        for side,sgn in [('r',-1),('l',1)]:
            hip=[sgn*2.8,15,0];knee=[sgn*2.8,8,0];ankle=[sgn*2.8,1.5,0]
            self.addbone('leg_'+side,'pelvis',hip);self.addbone('shin_'+side,'leg_'+side,knee);self.addbone('foot_'+side,'shin_'+side,ankle)
            x=(9.6 if index==1 else 7.7)*sgn
            shoulder=[x,28,0];elbow=[x+sgn*.6,21,-1];hand=[x+sgn*.5,15,-2.8]
            if index==1:elbow=[sgn*11,23,-1];hand=[sgn*11,17,-4]
            if index==2:elbow=[sgn*8.6,22.5,-2];hand=[sgn*8.6,18,-5]
            if index==3:elbow=[sgn*10,24,-.7];hand=[sgn*12,21,-4]
            if index==4:elbow=[sgn*9,22,-1.4];hand=[sgn*10,18,-5]
            if index==5:
                elbow=[-9.5,22,-3] if side=='r' else [8.8,24,-3]
                hand=[-6.5,14.5,-6] if side=='r' else [2.3,24.9,-6]
            self.addbone('arm_'+side,'chest',shoulder);self.addbone('forearm_'+side,'arm_'+side,elbow);self.addbone('hand_'+side,'forearm_'+side,hand)
            self.addbone('weapon_'+side,'hand_'+side,hand)
            self.addbone('shoulder_'+side,'chest',[x,28,0]);self.addbone('sash_'+side,'pelvis',[sgn*3.8,15,0])
        for i in range(6):self.addbone('veil_'+str(i),'collar',[(i-2.5)*1.45,29,3.3])

    def addbone(self,n,p,o):self.bones[n]=(p,list(o))
    def at(self,n):return np.array(self.bones[n][1],float)
    def box(self,bone,at,size,mat=0,rot=None,glow=False,label=None,emblem=False):
        assert bone in self.bones
        self.parts.append(dict(bone=bone,at=list(map(float,at)),size=list(map(float,size)),mat=mat,
            rot=list(rot or [0,0,0]),glow=glow,label=label or bone,emblem=emblem))
    def beam(self,bone,a,b,width,depth=None,mat=0,label=None):
        a,b=np.array(a,float),np.array(b,float);v=b-a;length=float(np.linalg.norm(v));v/=length
        angles=[math.degrees(math.asin(float(v[2]))),0,math.degrees(math.atan2(-v[0],v[1]))]
        center=(a+b)/2;size=np.array([width,length,depth or width]);self.box(bone,center-size/2,size,mat,angles,label=label)
    def ring(self,bone,c,r,width=.4,mat=1,segments=12,plane='xy',phase=0):
        c=np.array(c,float)
        for i in range(segments):
            a=phase+math.tau*i/segments;b=phase+math.tau*(i+1)/segments
            def point(t):return c+np.array([math.cos(t)*r,math.sin(t)*r,0] if plane=='xy' else [math.cos(t)*r,0,math.sin(t)*r])
            self.beam(bone,point(a),point(b),width,width,mat)
    def crystal(self,bone,c,h=4,w=1.5,mat=1,tilt=0):
        x,y,z=c
        for i,(width,height) in enumerate([(w,h*.5),(w*.68,h*.32),(w*.28,h*.24)]):
            self.box(bone,[x-width/2,y+h*[0,.44,.73][i],z-width/3],[width,height,width*.67],mat,[0,0,tilt])
        self.box(bone,[x-.12,y+h*.15,z-w*.36],[.24,h*.55,.17],5,glow=True)
    def chain(self,bone,start,end,count=6,mat=1):
        for i in range(count):
            t=i/(count-1);p=np.array(start)*(1-t)+np.array(end)*t;p[1]-=math.sin(t*math.pi)*1.3
            self.ring(bone,p,.34,.16,mat,6,'xy' if i%2 else 'xz')
    def plate(self,bone,center,w,h,mat=0,tilt=0):
        x,y,z=center
        self.box(bone,[x-w/2,y-h/2,z-.3],[w,h,.6],mat,[0,0,tilt])
        self.box(bone,[x-w*.43,y-h*.5,z-.7],[w*.86,.28,.32],1,[0,0,tilt])
        for dx in [-w*.32,w*.32]:self.box(bone,[x+dx-.12,y+h*.3,z-.75],[.24,.24,.25],1)
    def fingers(self,side,mode='closed',thick=1):
        hand='hand_'+side;g=self.at(hand);x,y,z=g
        if mode=='closed':
            start=len(self.parts)
            self.box(hand,g+[-.95,-1.2,.65],[1.9,2.4,.6],8,label=hand+'_palm')
            # Handle passes through the curled fingers' central opening, not through a solid fist.
            for n in range(4):
                yy=y-.94+n*.52
                self.box(hand,[x-.99,yy,z-1.03],[1.98,.36,.38],4 if self.i==0 else 1,label='finger_'+side)
                for xx in [-.99,.65]:self.box(hand,[x+xx,yy,z-.7],[.34,.36,1.45],4 if self.i==0 else 1,label='finger_'+side)
            self.box(hand,[x-1.28,y+.25,z-.6],[.4,1.15,1.15],8,[0,0,-22],label='thumb_'+side)
            if self.i==5:
                # Curled fingers wrap the diagonal shaft, leaving a real hollow grip channel.
                axis=self.at('hand_l')-self.at('hand_r');theta=math.degrees(math.atan2(-axis[0],axis[1]));r=rig.rotation([0,0,theta])
                for part in self.parts[start:]:
                    size=np.array(part['size']);center=np.array(part['at'])+size/2;part['at']=list(g+r@(center-g)-size/2);part['rot'][2]+=theta
        elif mode=='fist':
            self.box(hand,g+[-2,-1.4,-1.6],[4,3,3.2],0,label=hand+'_palm')
            for n in range(4):
                self.box(hand,[x-1.75+n*.9,y-.4,z-2.1],[.72,1.7,.8],1,label='knuckle_'+side)
                self.box(hand,[x-1.67+n*.9,y-.1,z-2.24],[.48,.6,.2],6,glow=True)
        else:
            self.box(hand,g+[-1,-.5,-.7],[2,1.1,1.5],4 if self.i==3 else 8,label=hand+'_palm')
            for n in range(4):
                xx=x+(n-1.5)*.57;length=2.3+(1-abs(n-1.5)/2)*.9
                self.beam(hand,[xx,y-.1,z-.3],[xx+(n-1.5)*.27,y+.25,z-length],.32,.4,4 if self.i==3 else 1,label='finger_'+side)
            self.beam(hand,[x-1,y,z],[x-1.9,y+.2,z-1.7],.42,.45,4 if self.i==3 else 1,label='thumb_'+side)
    def anatomy(self):
        i=self.i;heavy=i in [1,5];robe=i in [3,4]
        widths=[7.6,12.8,7.3,6.3,7,11.1];w=widths[i]
        # Five shaped tiers, independently beveled pectoral plates, visible articulated neck.
        for y,h,width in [(14,2.2,w*.7),(16.2,2,w*.68),(18.2,3,w*.8),(21.2,3.8,w),(25,3.7,w*.91)]:
            self.box('chest',[-width/2,y,-(3.1 if heavy else 2.25)],[width,h,6.2 if heavy else 4.5],2 if robe else 0)
        self.box('neck',[-1.5,28.1,-1.5],[3,4.2,3],2)
        self.box('pelvis',[-4,12.8,-2.2],[8,2.8,4.4],8)
        for side,sgn in [('r',-1),('l',1)]:
            shoulder,elbow,hand=self.at('arm_'+side),self.at('forearm_'+side),self.at('hand_'+side)
            armwidth=3.5 if i==1 else 2.7 if i==5 else 1.8 if robe else 2.2
            self.beam('arm_'+side,shoulder,elbow,armwidth,armwidth,3 if robe else 0)
            self.beam('forearm_'+side,elbow,hand+[0,.5,0],armwidth*.9,armwidth,3 if robe else 0)
            for joint,bone,r in [(elbow,'forearm_'+side,armwidth*.62),(shoulder,'arm_'+side,armwidth*.65)]:
                self.ring(bone,joint,r,.35,1,10,'xy')
            if not robe:
                for row in range(3):
                    t=.25+row*.22;c=elbow*(1-t)+hand*t
                    self.plate('forearm_'+side,c+[0,0,-armwidth*.6],armwidth*1.3,1.2,0,sgn*5)
            hip,knee,ankle=self.at('leg_'+side),self.at('shin_'+side),self.at('foot_'+side)
            self.beam('leg_'+side,hip,knee,3 if heavy else 2.5,3 if heavy else 2.5,8 if robe else 0)
            self.beam('shin_'+side,knee,ankle,3.5 if i==1 else 2.9,3.7 if heavy else 3,0)
            self.plate('shin_'+side,knee+[0,0,-2],3.3,2.1,1)
            for row in range(3):self.plate('shin_'+side,[sgn*2.8,3.1+row*1.4,-1.9],2.65,1.2,0)
            self.box('foot_'+side,[sgn*2.8-1.7,0,-3.9],[3.4,2,5.7],0)
            self.box('foot_'+side,[sgn*2.8-1.55,.45,-4.25],[3.1,.9,1.3],1)
            self.fingers(side,'fist' if i==1 else 'open' if i in [3,4] else 'closed')
        for n in range(7):
            x=-3.6+n*1.2;self.box('pelvis',[x-.45,14.5,-2.65],[.9,.65,.65],1)
        self.box('pelvis',[-.8,14.15,-3],[1.6,1.45,.75],1)
    def hood(self,mat=3):
        h=self.bones['head'][1][1]
        for j in range(4):
            y=h+1+j*.65;w=7.8-j*.85
            self.box('head',[-w/2,y,-2.9],[w,.9,6.1-j*.6],mat)
        for s in [-1,1]:
            self.box('head',[s*3.15-.6,h-3.5,-2.8],[1.2,6,5.7],mat,[0,0,s*8])
            self.beam('head',[s*2.55,h+2.5,-3.1],[s*3.3,h-3.5,-3.2],.25,.3,1)
        self.box('head',[-3,h-3.1,2.4],[6,5.6,.9],mat)
    def face(self,mask=False):
        h=self.bones['head'][1][1]
        self.box('head',[-2.65,h-2.4,-2.6],[5.3,5.4,4.9],4 if mask else 2)
        for side in [-1,1]:
            self.plate('head',[side*1.75,h-1.6,-2.85],1.5,2.4,4 if mask else 0,side*15)
            self.box('head',[side*1.25-.67,h+.3,-3.05],[1.34,.8,.4],2)
            self.box('head',[side*1.25-.45,h+.48,-3.3],[.9,.25,.24],5,glow=True)
            self.beam('head',[side*.65,h+1.5,-3.1],[side*2.4,h+1.1,-3.1],.38,.4,1)
    def robes(self,mat=3,split=True):
        for n in range(7):
            x=(n-3)*1.36;bottom=(n%3)*.7+1.2
            bone='sash_r' if x<0 else 'sash_l'
            self.box(bone,[x-.8,bottom,-2.6],[1.6,13.6-bottom,.8],mat,[0,0,(n-3)*2.8])
            self.beam(bone,[x-.52,bottom+.5,-3.08],[x-.52,12.7,-3.08],.12,.14,9)
        for n in range(6):
            x=(n-2.5)*1.6;bottom=2+n%2
            self.box('veil_'+str(n),[x-.9,bottom,3.3],[1.8,27-bottom,.65],mat,[0,0,(n-2.5)*2])
            self.beam('veil_'+str(n),[x-.7,bottom+.5,3.95],[x-.7,27.5,3.95],.14,.14,1)
        self.box('chest',[-2.5,16,-2.55],[5,10,.45],13,emblem=True,label='embroidered_tabard')
    def shoulderfeathers(self,side,mat=0,count=5):
        s=-1 if side=='r' else 1;c=self.at('arm_'+side)
        for n in range(count):
            self.plate('shoulder_'+side,c+[s*n*.52,.8-n*.45,-.7],2.6,4.2-n*.22,mat,s*(30+n*9))
    def adddagger(self,side):
        g=self.at('hand_'+side);s=-1 if side=='r' else 1;bone='weapon_'+side
        self.beam(bone,g+[0,-1.7,0],g+[0,1.7,0],.6,.6,8,label='grip_'+side)
        self.grips.append((side,bone,list(g)))
        self.beam(bone,g+[-1.6,-1.65,0],g+[1.6,-1.65,0],.38,.6,1)
        for n in range(7):
            t=n/6;center=g+np.array([s*t*t*2.5,-2.5-t*6,0]);width=1.8*(1-t*.7)
            self.box(bone,center+[-width/2,-.65,-.3],[width,1.5,.6],7,[0,0,s*t*32],label='blade_'+side)
            self.box(bone,center+[-.12,-.57,-.42],[.24,1.25,.16],5,[0,0,s*t*32],True)
        self.ring(bone,g+[0,2,0],.7,.25,1,8)
    def sculpt(self):
        self.anatomy();i=self.i
        if i==0:
            self.face(True);self.hood();self.adddagger('r');self.adddagger('l')
            h=self.bones['head'][1][1]
            for n in range(5):self.box('jaw',[-1.6+n*.8,h-2.2,-2.9],[.4,.65,.5],4)
            self.box('head',[-2.75,h-2.7,-3.4],[5.5,1.4,.7],13,[0,0,-6],label='red_scarf')
            for n in range(4):self.plate('chest',[-1.65,24-n*1.45,-2.8],3.1,.7,4,-15);self.plate('chest',[1.65,24-n*1.45,-2.8],3.1,.7,4,15)
            self.beam('chest',[-4.3,28,-2.5],[3.4,16.4,-3],.9,.6,8)
            for n in range(6):self.box('chest',[-3.85+n*.95,26.8-n*1.4,-3.15],[.42,.7,.35],1,[0,0,-32])
            self.shoulderfeathers('r',0,4);self.plate('shoulder_l',[7.7,28,-1.1],3.7,2.1,1,16)
            self.chain('pelvis',[-4,14.4,-2.7],[-5,8.5,-2.7],7)
            for n in range(3):self.crystal('sash_l',[4.5+n*.35,7+n*1.3,-1],1.4,.5,6)
            for n in range(3):self.box('cape_tip',[2+n*.85,9-n*1.6,3.4],[1.15,14,.55],13,[0,0,10+n*6])
        elif i==1:
            h=self.bones['head'][1][1]
            for n,w in enumerate([6.4,7.2,6.8,5.3]):self.box('head',[-w/2,h-2.5+n*1.6,-2.8],[w,1.8,5.6],0)
            self.box('head',[-2.7,h+.5,-3],[5.4,.8,.5],2)
            for x in [-1.65,0,1.65]:self.box('head',[x-.45,h+.7,-3.25],[.9,.25,.25],5,glow=True)
            self.ring('chest',[0,23,-3.8],4.45,.8,1,14)
            self.ring('chest',[0,23,-3.95],3.55,.35,2,14)
            self.box('chest',[-2.8,20.2,-4.05],[5.6,5.6,.5],5,glow=True,label='soul_fire_core')
            for n in range(5):
                a=-2.8+n*1.4;self.beam('chest',[a,19.2,-4.6],[a*.85,26.9,-4.6],.4,.5,0)
            for side,s in [('r',-1),('l',1)]:
                c=self.at('arm_'+side);self.ring('shoulder_'+side,c+[0,.6,0],2.6,.7,1,10,'xz')
                self.box('shoulder_'+side,c+[-2.7,-.1,-2.7],[5.4,2.5,5.4],0)
                self.ring('shoulder_'+side,c+[0,2.5,0],1.6,.6,1,10,'xz')
                for n in range(3):self.crystal('shoulder_'+side,c+[s*(n-1)*.7,3,0],4.8+n%2,1.15,5,(n-1)*17)
                elbow=self.at('forearm_'+side)
                self.ring('forearm_'+side,elbow+[0,-1.6,-.5],2.1,.65,1,12,'xz')
                for n in range(3):self.box('forearm_'+side,elbow+[-1.8+n*1.3,-3,-2.1],[.65,2.6,.5],6,glow=True)
            for n in range(3):
                self.box('chest',[-4+n*4,24.5,3.2],[2.1,8+n%2*3,2.1],0)
                self.ring('chest',[-2.95+n*4,32.5+n%2*3,4.25],1.1,.35,1,8,'xz')
            self.chain('chest',[-6,28,0],[-5,17,-2.5],8)
        elif i==2:
            self.face(True)
            for side,s in [('r',-1),('l',1)]:
                self.box('head',[s*2.3-.6,31.5,-3.25],[1.2,4.3,.6],1,[0,0,s*16])
                self.shoulderfeathers(side,1,4)
                for n in range(4):self.crystal('shoulder_'+side,[s*(7.6+n*.7),28+n*.6,1],5.6-n*.6,1.2,1,s*(n*12+5))
                self.plate('chest',[s*2.2,25.6,-2.65],3,3.5,1,s*20)
                for n in range(3):self.crystal('cape_tip',[s*(2+n*1.5),4+n,3.6],7-n,1.2,1,s*7)
            for n in range(5):self.crystal('head',[(n-2)*1.25,36,0],6.2-abs(n-2)*1.2,1,1,(n-2)*12)
            self.box('chest',[-2,17,-2.65],[4,6,.45],13,emblem=True)
            self.box('cape',[-4,14,3.3],[8,15,.65],3)
            g=self.at('hand_l');self.grips.append(('l','weapon_l',list(g)))
            # Recurve limbs curve away from a straight string; only this minion carries a bow.
            for sign in [-1,1]:
                pts=[g+np.array([0,sign*1.25,0]),g+np.array([2.5,sign*5.7,0]),g+np.array([2.2,sign*10,0]),g+np.array([.6,sign*13,0])]
                for a,b in zip(pts,pts[1:]):self.beam('weapon_l',a,b,.85,.9,1,label='bow_limb')
                for n,a in enumerate(pts[1:]):self.crystal('weapon_l',a+[0,0,-.3],1.8,.65,5,sign*15)
            self.beam('weapon_l',g+[.6,-13,0],g+[.6,13,0],.12,.12,5,label='bow_string')
            self.beam('weapon_l',g+[0,-1.3,0],g+[0,1.3,0],.6,.7,8,label='grip_l')
            # Right hand is open for spell control, not pretending to hold a second pole.
            self.parts=[p for p in self.parts if not(p['label'].startswith(('finger_r','thumb_r','hand_r_palm')))]
            self.fingers('r','open')
            self.box('chest',[-4.6,16.5,3.2],[3,10,2.7],8,[0,0,-15],label='quiver')
            for n in range(4):
                x=-4.1+n*.7;self.beam('chest',[x,22,4.3],[x,33+n%2,4.3],.2,.2,1)
                self.box('chest',[x-.45,29+n%2,4],[.9,2.3,.35],14,[0,0,-18])
        elif i==3:
            self.face(True);self.hood();self.robes()
            h=self.bones['head'][1][1]
            self.box('head',[-1.1,h-2.2,-3.4],[2.2,2.7,.45],2,label='scream_mouth')
            for side,s in [('r',-1),('l',1)]:
                self.box('head',[s*1.35-.12,h-1.7,-3.35],[.24,2,.3],5,glow=True)
                for n in range(4):
                    self.box('head',[s*(2.7+n*.5)-.35,26+n*.8,1.7+n*.35],[.7,10-n*.8,.7],2,[0,0,s*(8+n*3)])
                self.box('shoulder_'+side,[s*7.5-1.5,25,-1.4],[3,4,.7],3,[0,0,s*12])
                for n in range(3):self.beam('forearm_'+side,self.at('forearm_'+side)+[(n-1)*.6,0,-1],self.at('hand_'+side)+[(n-1)*.7,-4,-.2],.4,.6,3)
            self.ring('halo',[0,35.5,3.9],6.6,.18,1,18)
            for n in range(9):
                a=math.pi*n/8;self.crystal('halo',[math.cos(a)*6.6,35.5+math.sin(a)*6.6,3.9],1.2,.45,5,0)
            # Silver tears, thin chest chains and a split veil, rather than plate armor.
            self.chain('chest',[-3.2,28.2,-2.65],[3.2,28.2,-2.65],12,1)
            self.box('chest',[-.55,24.4,-3.2],[1.1,2,.45],5,glow=True)
            for n in range(6):self.box('veil_'+str(n),[(n-2.5)*1.5-.9,2+n%3,4.2],[1.8,25-n%3,.4],14)
        elif i==4:
            self.face();self.hood();self.robes(3)
            h=self.bones['head'][1][1]
            for x,y in [(-1.15,h+.5),(1.15,h+.5),(0,h+2)]:self.box('head',[x-.35,y,-3.4],[.7,.48,.3],5,[0,0,45],True)
            self.shoulderfeathers('r',0,6);self.shoulderfeathers('l',1,3)
            for n in range(4):self.beam('collar',[-3.7+n*2.5,27.5,1],[-4.6+n*3.1,32.2,1.6],.7,.9,0)
            for n in range(3):self.ring('halo',[0,37+n*.4,2],3.4+n*.65,.18,1,10,'xz')
            # Actual open book geometry levitates two units above an upturned hand.
            g=self.at('hand_r');c=g+[0,3,-.2];self.addbone('book','hand_r',c)
            for side,s in [('r',-1),('l',1)]:
                self.box('book',c+[s*2-.0-1.8,-.3,-2.6],[3.6,.5,5.2],8,[0,0,-s*12],label='book_cover')
                for n in range(3):self.box('book',c+[s*1.85-1.65,n*.13,-2.4],[3.3,.12,4.8],11,[0,0,-s*12],label='book_page')
                for n in range(5):self.box('book',c+[s*1.8-1.05,.65,-1.7+n*.78],[2.1,.09,.11],6,[0,0,-s*12],True,label='page_ink')
            self.box('book',c+[-.27,-.5,-2.8],[.54,1.2,5.6],1,label='book_spine')
            for n in range(3):self.box('book',c+[-3.7+n*3.7,2+n*.5,-3.2],[1.5,.12,2.2],11,[0,0,(n-1)*14],label='floating_page')
            f=self.at('familiar');self.box('familiar',f+[-1.8,-1.8,-1.4],[3.6,3.6,2.8],2,[0,0,12])
            for x in [-.8,.8]:self.box('familiar',f+[x-.3,.3,-1.7],[.6,.25,.2],5,glow=True)
            for n in range(4):self.box('familiar',f+[-1.6+n*.8,-5.3+n%2,-.4],[.5,4,.5],3,[0,0,(n-1.5)*13])
            self.ring('familiar',f+[0,0,1],3.2,.17,1,12)
            self.beam('familiar',f+[-2,-1,-.5],self.at('hand_l')+[1,1,-1],.1,.1,5,label='puppet_thread')
        else:
            self.face(True)
            h=self.bones['head'][1][1]
            self.box('head',[-3.2,h+.2,-3.5],[6.4,1.4,.65],8,label='blindfold')
            self.box('head',[-3.1,h+.7,-3.9],[6.2,.22,.3],1)
            for n in range(5):self.crystal('head',[(n-2)*1.4,37,0],3.5-abs(n-2)*.5,.75,1,(n-2)*8)
            for side,s in [('r',-1),('l',1)]:
                self.shoulderfeathers(side,0,5)
                self.plate('chest',[s*2.9,25.4,-3.4],4.7,4.5,4,s*17)
                self.chain('shoulder_'+side,[s*6.2,29,-2],[s*8.7,23,-2],6)
                self.box('sash_'+side,[s*3.2-1.7,4,-2.8],[3.4,10,.65],3,emblem=True)
            self.box('chest',[-1.4,18.5,-3.6],[2.8,4.3,.7],13,emblem=True)
            self.box('cape',[-5,15,3.5],[10,14,.8],3)
            for n in range(5):self.box('cape_tip',[-5+n*2.1,3+n%2,3.6],[2,12-n%2,.8],3)
            g=self.at('hand_r');g2=self.at('hand_l');v=(g2-g)/np.linalg.norm(g2-g)
            a=g-v*7;b=g2+v*13
            self.beam('weapon_r',a,b,.8,.85,8,label='axe_handle')
            self.grips.extend([('r','weapon_r',list(g)),('l','weapon_r',list(g2))])
            for t in [0,.2,.4,.6,.8,1]:self.ring('weapon_r',a*(1-t)+b*t,.55,.17,1,8,'xz')
            center=b-v*2.2
            # One broad crescent head with tapered edge, socket, engraving and counterweight.
            for n in range(8):
                angle=-1.1+n*2.2/7;x=center[0]+math.cos(angle)*5.1;y=center[1]+math.sin(angle)*6.1
                self.box('weapon_r',[x-1.7,y-1.25,center[2]-.8],[3.4,2.5,1.6],7,[0,0,-math.degrees(angle)*.32],label='axe_blade')
                self.box('weapon_r',[x+1.25,y-1.1,center[2]-1.02],[.38,2.2,.32],1,[0,0,-math.degrees(angle)*.32])
                if n in [1,3,5]:self.box('weapon_r',[x-.45,y-.35,center[2]-1.05],[.9,.7,.25],5,[0,0,45],True)
            self.box('weapon_r',center+[-2,-2.4,-1.1],[3.7,4.8,2.2],0)
            self.box('weapon_r',center+[-4.1,-1,-.75],[3,2,1.5],1)
        self.finishing()
        return self

    def finishing(self):
        i=self.i
        # Large readable details alongside the fine rivets and chains.
        if i==0:
            for side,s in [('r',-1),('l',1)]:
                for n in range(3):self.plate('arm_'+side,[s*8.15,25.2-n*.75,-1.7],2.8,.8,0,s*7)
                self.box('sash_'+side,[s*3.5-1.2,8.7,-2.7],[2.4,4.5,.65],13,[0,0,s*8])
                for n in range(3):self.box('sash_'+side,[s*3.5-.7,9.3+n*1.05,-3.1],[1.4,.13,.22],1,[0,0,s*8])
                self.beam('chest',[s*1.1,29.5,-1.8],[s*3.7,26.4,-2.6],1.15,.5,13)
            self.box('chest',[-.6,27,-3],[1.2,1.5,.45],1)
            self.box('chest',[-.23,27.3,-3.3],[.46,.9,.2],5,glow=True)
        elif i==1:
            for side,s in [('r',-1),('l',1)]:
                for n in range(3):self.plate('chest',[s*(4.3-n*.3),27.1-n*.8,-3.45],2.1,.65,1,s*8)
                for n in range(3):self.plate('chest',[s*2.5,18.2-n*.8,-3.3],3.8,.55,0)
                self.beam('chest',[s*6.1,25.7,1],[s*7.3,18,0],.9,.9,1)
                self.beam('chest',[s*7.3,18,0],[s*5.1,16.5,-1.8],.9,.9,1)
                for n in range(3):self.ring('chest',[s*7.3,18.5+n,0],.7,.2,0,8,'xz')
                for n in range(3):self.plate('leg_'+side,[s*2.8,12.8-n*1.3,-1.9],3.1,1,0)
                self.box('foot_'+side,[s*2.8-2,0,-3.5],[4,1.7,5.5],0)
                for n in range(3):self.box('foot_'+side,[s*2.8-1.5+n*1.1,.6,-4.4],[.65,.65,.4],6,glow=True)
        elif i==2:
            for side,s in [('r',-1),('l',1)]:
                self.beam('head',[s*2.7,36,1],[s*4.1,38,1.6],.8,.7,1)
                self.beam('head',[s*4.1,38,1.6],[s*5.1,40.6,1.9],.55,.6,1)
                self.beam('head',[s*4.1,38,1.6],[s*3.4,39.8,2],.45,.5,1)
                self.chain('shoulder_'+side,[s*6,28,-1.7],[s*7.5,24,-1.7],5,1)
                for n in range(3):self.crystal('sash_'+side,[s*(3.7+n*.38),8+n*.6,-2.2],2.4,.55,1,s*10)
            self.ring('chest',[0,25.2,-3],.9,.18,1,6)
            self.crystal('chest',[0,22.2,-3.1],2.3,.65,5)
        elif i==3:
            for side,s in [('r',-1),('l',1)]:
                for n in range(3):self.plate('chest',[s*(1.1+n*.47),25.8-n*1.05,-2.75],1.6,2.3,4,s*(19+n*6))
                self.beam('head',[s*1.9,32.1,-3.25],[s*2.2,30.5,-3.3],.15,.18,5)
                for n in range(4):self.box('veil_'+str(n),[s*(4.8+n*.38),4.4+n%2,3.3],[.42,17-n*.7,.45],14,[0,0,s*(n+3)])
            self.ring('chest',[0,24.4,-3.3],.8,.15,1,10)
            self.parts=[p for p in self.parts if not p['bone'].startswith(('leg_','shin_','foot_'))]
        elif i==4:
            for side,s in [('r',-1),('l',1)]:
                self.beam('chest',[s*1.2,29.1,-1.8],[s*4.1,26.1,-2.7],1.7,.5,3)
                self.beam('chest',[s*1.5,29,-2.25],[s*3.9,26.3,-3.05],.22,.2,1)
                for n in range(4):self.box('forearm_'+side,self.at('forearm_'+side)+[-1.15,-2-n*.6,-1.4],[2.3,.18,.3],1)
            self.ring('chest',[0,27,-3],.85,.16,1,5)
            self.box('chest',[-.25,25.3,-3.1],[.5,1.5,.3],5,glow=True)
            for n in range(3):
                self.box('sash_r',[-4.5-n*.55,10.1,-.4],[.45,3.7,.65],11,[0,0,-6+n*7])
                self.ring('sash_r',[-4.27-n*.55,12.9,-.6],.32,.15,1,8)
        else:
            for side,s in [('r',-1),('l',1)]:
                for n in range(3):self.plate('arm_'+side,[s*7.7,26.7-n*.75,-2.1],3.7,.8,0,s*5)
                for n in range(4):self.box('shoulder_'+side,[s*(7+n*.6)-.12,28-n*.55,-2],[.24,.24,.28],1)
                self.ring('chest',[s*4.4,27,-3.3],.52,.16,1,8)
                self.beam('shin_'+side,[s*2.8-1.1,2.3,-2.3],[s*2.8-1.1,7,-2.3],.18,.2,1)
                self.beam('shin_'+side,[s*2.8+1.1,2.3,-2.3],[s*2.8+1.1,7,-2.3],.18,.2,1)
            for n in range(3):self.ring('neck',[0,29+n*.75,0],1.6,.2,1,10,'xz')
            self.box('head',[-1.3,31.2,-3.4],[2.6,2.5,.6],4)
            self.box('head',[-.75,30,-3.6],[1.5,1.4,.65],4)

def texture(study):
    i=study.i;colors=SCHEMES[i]+[(77,56,54),(211,178,116),(101,158,164),(236,221,185),(30,28,43),mix(COLORS[i],(45,34,54),.6),(178,204,208),(51,53,67)]
    im=Image.new('RGBA',(1024,1024));rng=random.Random(731+i)
    for n,color in enumerate(colors):
        tile=Image.new('RGBA',(128,128));pix=tile.load()
        for y in range(128):
            for x in range(128):
                s=.93+.07*math.cos((x-38)/83)+.03*math.sin(y/20)+rng.uniform(-.008,.008)
                pix[x,y]=tuple(min(255,round(c*s)) for c in color)+(255,)
        d=ImageDraw.Draw(tile);dim=mix(color,(15,20,31),.3);light=mix(color,(245,237,216),.24)
        if n in [0,1,7]:
            for x in range(12,128,25):d.line([(x,0),(x-4,128)],fill=dim,width=1)
            d.line([(0,38),(128,38)],fill=light,width=1)
        if n in [3,8,13]:
            for x in range(0,128,6):d.line([(x,0),(x,128)],fill=dim,width=1)
        if n==11:
            for y in range(12,128,12):d.line([(15,y),(111,y)],fill=(144,108,135),width=2)
        if n==13:
            d.line([(15,0),(15,128)],fill=colors[9],width=2);d.line([(113,0),(113,128)],fill=colors[9],width=2)
            for y in range(18,128,30):d.polygon([(64,y-11),(77,y),(64,y+11),(51,y)],outline=colors[9],width=2)
        if i==3 and n==14:
            tile=Image.new('RGBA',(128,128));d=ImageDraw.Draw(tile)
            for x in range(8,128,24):
                pts=[(x+math.sin(y/15)*5,y) for y in range(128)];d.line(pts,fill=(*color,255),width=3)
            for y in range(8,128,24):
                d.line([(0,y),(128,y)],fill=(*color,255),width=2)
                for x in range(8,128,24):d.ellipse((x-4,y-4,x+4,y+4),outline=(*color,255),width=2)
        im.paste(tile,((n%8)*128,(n//8)*128))
    return im

def animations(study):
    b=study.bones;i=study.i;rest={k:[0,0,0] for k in b}
    def clip(name,length,poses,positions=None,loop=False):
        animators={}
        for bone in b:
            keys=[]
            for time,pose in poses:
                r=pose.get(bone,[0,0,0]);r=[-r[0],-r[1],r[2]]
                keys.append(dict(channel='rotation',time=time,data_points=[dict(zip('xyz',map(str,r)))],interpolation='linear',uuid=uid(f'{i}/{name}/{bone}/{time}/rot')))
            for time,pose in positions or []:
                keys.append(dict(channel='position',time=time,data_points=[dict(zip('xyz',map(str,pose.get(bone,[0,0,0]))))],interpolation='linear',uuid=uid(f'{i}/{name}/{bone}/{time}/pos')))
            animators[uid(f'{i}/bone/{bone}')]=dict(name=bone,type='bone',keyframes=keys)
        return dict(name=name,uuid=uid(f'{i}/clip/{name}'),length=length,loop='loop' if loop else 'once',override=False,snapping=24,animators=animators)
    # Weapon R and L are bound to the exact palm coordinate. Styx keeps both arm
    # chains fixed together while chest/cape/legs move; this preserves both grips.
    breathe=copy.deepcopy(rest);breathe['chest']=[-.7,0,0]
    for n in range(6):breathe['veil_'+str(n)]=[2+n%3,0,(n-2.5)*.7]
    idlepos=[(0,{}),(1.2,{'familiar':[0,.5,0],'book':[0,.2,0]}),(2.4,{})]
    out=[clip('idle',2.4,[(0,rest),(1.2,breathe),(2.4,rest)],idlepos,True)]
    poses=[];positions=[]
    for n in range(25):
        t=n/24;f=math.tau*t;p=copy.deepcopy(rest)
        for side,offset in [('r',0),('l',math.pi)]:
            v=math.sin(f+offset);hip=20*v;knee=-max(0,-v)*30
            p['leg_'+side]=[hip,0,0];p['shin_'+side]=[knee,0,0];p['foot_'+side]=[-hip-knee,0,0]
            if i not in [2,3,4,5]:p['arm_'+side]=[-v*(7 if i==1 else 12),0,0]
        p['cape']=[5+math.sin(f)*2,0,0];p['cape_tip']=[6+math.sin(f-.3)*3,0,0]
        for j in range(6):p['veil_'+str(j)]=[4+math.sin(f-j*.3)*2,0,math.sin(f+j*.4)*1.5]
        poses.append((round(t,6),p));positions.append((round(t,6),{'pelvis':[0,.16*math.sin(f)**2,0]}))
    out.append(clip('walk',1,poses,positions,True))
    hover=[];hoverpos=[]
    for n in range(17):
        t=n/16;p=copy.deepcopy(rest)
        for j in range(6):p['veil_'+str(j)]=[6+math.sin(t*math.tau-j*.6)*5,0,math.cos(t*math.tau+j*.4)*3]
        p['jaw']=[3+math.sin(t*math.tau)*3,0,0]
        hover.append((round(t*2,6),p));hoverpos.append((round(t*2,6),{'pelvis':[0,3.2+math.sin(t*math.tau)*.8,0],'familiar':[0,math.sin(t*math.tau)*.7,0]}))
    out.append(clip('hover',2,hover,hoverpos,True))
    # Individual casting poses. Armed dual-grip model moves as one upper-body unit.
    cast=copy.deepcopy(rest)
    if i==0:cast.update(arm_l=[35,0,0],forearm_l=[15,0,0],chest=[-3,0,0])
    elif i==1:cast.update(arm_r=[42,0,0],arm_l=[42,0,0],forearm_r=[12,0,0],forearm_l=[12,0,0],chest=[-3,0,0])
    elif i==2:cast.update(arm_r=[35,0,-5],forearm_r=[12,0,0],chest=[-2,0,0])
    elif i==3:cast.update(arm_r=[25,0,-10],arm_l=[25,0,10],forearm_r=[15,0,0],forearm_l=[15,0,0],jaw=[15,0,0],chest=[-4,0,0])
    elif i==4:cast.update(arm_l=[35,0,0],forearm_l=[20,0,0],book=[0,0,8],familiar=[0,12,0],chest=[-2,0,0])
    else:cast.update(chest=[-4,0,0])
    release=copy.deepcopy(cast);release['chest']=[2,0,0] if i!=5 else [4,0,0]
    out.extend([clip('cast_windup',1,[(0,rest),(.8,cast),(1,cast)]),clip('cast_active',.5,[(0,cast),(.15,release),(.5,release)]),clip('cast_recover',.7,[(0,release),(.6,rest),(.7,rest)])])
    # Preview attack clip is role-specific; integration/contact timing is deferred.
    ready=copy.deepcopy(rest);contact=copy.deepcopy(rest)
    if i==0:ready.update(arm_r=[85,0,0],forearm_r=[35,0,0]);contact.update(arm_r=[10,0,0],forearm_r=[5,0,0])
    elif i==1:ready.update(arm_r=[70,0,0]);contact.update(arm_r=[85,0,0],forearm_r=[-15,0,0])
    elif i==5:ready.update(chest=[-12,0,0]);contact.update(chest=[13,0,0])
    else:ready=copy.deepcopy(cast);contact=copy.deepcopy(release)
    out.extend([clip('attack_windup',.8,[(0,rest),(.8,ready)]),clip('attack_active',.4,[(0,ready),(.2,contact),(.4,contact)]),clip('attack_recover',.7,[(0,contact),(.7,rest)])])
    stun=copy.deepcopy(rest);stun['chest']=[12,0,-3];stun['head']=[12,0,0]
    out.append(clip('stun',1.5,[(0,rest),(.2,stun),(1.3,stun),(1.5,rest)]))
    return out

def build(study):
    data=[];children={k:[] for k in study.bones}
    for n,p in enumerate(study.parts):
        at=p['at'];size=p['size'];m=p['mat'];u=m%8*128;v=m//8*128
        faces={}
        for f in FACES:
            w,h=(size[0],size[1]) if f in ['north','south'] else (size[2],size[1]) if f in ['east','west'] else (size[0],size[2])
            tw,th=(112,112) if p['emblem'] and f=='north' else (min(112,max(4,w*6)),min(112,max(4,h*6)))
            faces[f]=dict(uv=[u+64-tw/2,v+64-th/2,u+64+tw/2,v+64+th/2],texture=0)
        e=dict(name=p['label']+'_'+str(n),type='cube',uuid=uid(f'{study.i}/cube/{n}'),**{'from':at},to=[a+s for a,s in zip(at,size)],
               origin=[a+s/2 for a,s in zip(at,size)],rotation=p['rot'],faces=faces,box_uv=False,shade=not p['glow'],visibility=True,export=True,autouv=0)
        data.append(e);children[p['bone']].append(e['uuid'])
    def group(name):return dict(name=name,uuid=uid(f'{study.i}/bone/{name}'),origin=study.bones[name][1],rotation=[0,0,0],export=True,children=children[name]+[group(c) for c,(p,o) in study.bones.items() if p==name])
    tex=texture(study);buf=io.BytesIO();tex.save(buf,format='PNG');hid=uid(f'{study.i}/hit/cube')
    data.append(dict(name='hitbox',type='cube',uuid=hid,**{'from':[-5,0,-3]},to=[5,36,3],origin=[0,0,0],faces={}))
    model=dict(meta=dict(format_version='4.10',model_format='free',box_uv=False),name='arkhaia_'+NAMES[study.i]+'_v2',resolution=dict(width=1024,height=1024),elements=data,
        outliner=[group('root'),dict(name='hitbox',uuid=uid(f'{study.i}/hit'),origin=[0,0,0],children=[hid])],
        textures=[dict(name=NAMES[study.i]+'.png',id='0',uuid=uid(f'{study.i}/texture'),width=1024,height=1024,uv_width=1024,uv_height=1024,source='data:image/png;base64,'+base64.b64encode(buf.getvalue()).decode())],animations=animations(study))
    return model,tex

def load(model,tex):
    rig.MODEL=model;rig.TEXTURE=np.asarray(tex);rig.ELEMENTS={e['uuid']:e for e in model['elements']};rig.CLIPS={a['name']:a for a in model['animations']}

def matrices(model,clip,t,gaze=None):
    animations=next(a for a in model['animations'] if a['name']==clip)['animators'];result={}
    def walk(g,parent):
        o=np.array(g['origin']);a=animations.get(g['uuid'],{});r=rig.rotation(rig.sample(a,t,'rotation'));m=np.eye(4);m[:3,:3]=r;m[:3,3]=o-r@o+rig.sample(a,t,'position',-1 if model.get('meta',{}).get('evergarden_animation_position_x')=='inverted' else 1);m=parent@m
        if g['name']=='hi_gaze' and gaze is not None:
            pivot=(parent@np.append(o,1))[:3];r=rig.rotation([-gaze[1],-gaze[0],0]);look=np.eye(4);look[:3,:3]=r;look[:3,3]=pivot-r@pivot;m=look@m
        result[g['name']]=m
        for c in g['children']:
            if isinstance(c,dict):walk(c,m)
    for g in model['outliner']:walk(g,np.eye(4))
    return result

def sigil(index):
    size=768;im=Image.new('RGBA',(size,size));d=ImageDraw.Draw(im);c=COLORS[index];center=size/2
    def point(a,r):return (center+math.cos(a)*r,center+math.sin(a)*r)
    def circle(r,w=2,col=None):d.ellipse((center-r,center-r,center+r,center+r),outline=col or c,width=w)
    def polygon(n,r,phase=0,w=3):d.line([point(phase+math.tau*j/n,r) for j in range(n+1)],fill=c,width=w,joint='curve')
    circle(331,3);circle(317,1);circle(270,2)
    for j in range(24):
        a=j*math.tau/24;r=304 if j%3 else 290
        d.line([point(a,r),point(a,312)],fill=c,width=2)
        if j%3==0:
            p=point(a,283);d.line([(p[0]-4,p[1]-4),(p[0]+4,p[1]+4)],fill=c,width=2)
    if index==0:
        for j in range(3):
            a=j*math.tau/3;pts=[point(a+.3,215),point(a+.6,70),point(a+1.3,190)]
            d.line(pts,fill=c,width=6);d.arc((center-170,center-170,center+170,center+170),j*120+20,j*120+95,fill=c,width=3)
        circle(35,4)
    elif index==1:
        polygon(8,220,math.pi/8,4);circle(151,3)
        for j in range(8):
            a=j*math.tau/8;d.line([point(a,159),point(a+.08,210),point(a+.17,180)],fill=c,width=5)
        for j in range(3):
            x=center+(j-1)*45;d.line([(x,center+75),(x-20,center+20),(x+18,center-35),(x,center-92)],fill=(132,255,242),width=5)
    elif index==2:
        polygon(6,230,math.pi/6,3)
        for j in range(6):
            a=j*math.tau/6;d.line([point(a,20),point(a,205)],fill=c,width=5)
            for r in [100,155]:
                p=point(a,r)
                for sign in [-1,1]:d.line([p,(p[0]+math.cos(a+sign*.8)*40,p[1]+math.sin(a+sign*.8)*40)],fill=c,width=3)
        polygon(6,48)
    elif index==3:
        for r in [100,155,211]:
            d.arc((center-r,center-r,center+r,center+r),205,335,fill=c,width=4);d.arc((center-r,center-r,center+r,center+r),25,155,fill=c,width=4)
        d.polygon([(center,center-95),(center-39,center+9),(center,center+59),(center+39,center+9)],outline=c,width=4)
        circle(240,1)
    elif index==4:
        polygon(5,235,-math.pi/2,3)
        pts=[point(-math.pi/2+math.tau*(j*2%5)/5,180) for j in range(6)];d.line(pts,fill=c,width=2)
        d.arc((center-100,center-70,center+100,center+70),180,360,fill=c,width=4);d.arc((center-100,center-70,center+100,center+70),0,180,fill=c,width=4);circle(35,4)
    else:
        polygon(8,235,math.pi/8,4);polygon(4,168,math.pi/4,2)
        d.line([(center-61,center-100),(center+61,center-100),(center-61,center+100),(center+61,center+100),(center-61,center-100)],fill=c,width=5)
        for a in [0,math.pi/2,math.pi,math.pi*1.5]:d.line([point(a,180),point(a,218)],fill=c,width=5)
    return im

def floor_layer(index,size,yaw,elevation):
    tex=sigil(index);az,el=map(math.radians,[yaw,elevation]);s=(size-85)/57;origin=np.array([size/2,size*.83]);r=16.5
    right=np.array([math.cos(az),math.sin(el)*math.sin(az)])*s;up=np.array([math.sin(az),-math.sin(el)*math.cos(az)])*s
    # World X/Z affine quad; same texture will be usable by a runtime spell plane.
    basis=np.column_stack((right,up))*r*2/tex.width;offset=origin-(right+up)*r;inv=np.linalg.inv(basis);translation=-inv@offset
    coeff=(inv[0,0],inv[0,1],translation[0],inv[1,0],inv[1,1],translation[1])
    return tex.transform((size,size),Image.Transform.AFFINE,coeff,Image.Resampling.BICUBIC)

def picture(study,clip='idle',t=0,yaw=25,size=620,gaze=None):
    raw=rig.render(clip,t,yaw=yaw,elevation=10,size=size,gaze=gaze).convert('RGBA')
    bg=Image.new('RGBA',(size,size),(23,21,34,255));floor=floor_layer(study.i,size,yaw,10)
    bg.alpha_composite(floor.filter(ImageFilter.GaussianBlur(2)));bg.alpha_composite(floor)
    arr=np.asarray(raw);mask=Image.fromarray((np.any(arr[:,:,:3]!=[23,21,34],axis=2)*255).astype('uint8'));bg.paste(raw,(0,0),mask)
    return bg.convert('RGB')

def validate(study,model,tex,grip_tolerance=.0001):
    load(model,tex);checks=0;grip_errors=[];blade_checks=0
    def obb(part,m):
        lo=np.array(part['at']);size=np.array(part['size']);center=lo+size/2
        return (m@np.append(center,1))[:3],m[:3,:3]@rig.rotation(part['rot']),size/2
    def intersects(a,b):
        ca,ra,ea=a;cb,rb,eb=b
        axes=np.vstack((ra.T,rb.T,np.cross(ra.T[:,None,:],rb.T[None,:,:]).reshape(9,3)))
        axes=axes[np.linalg.norm(axes,axis=1)>1e-8]
        return not np.any(np.abs(axes@(cb-ca))>=np.abs(axes@ra)@ea+np.abs(axes@rb)@eb-1e-5)
    # Check actual shaft solids against the curled fingers/palm, not just coincident markers.
    bind=matrices(model,'idle',0)
    for side,weapon,point in study.grips:
        shaft=next(p for p in study.parts if p['label']==('axe_handle' if study.i==5 else 'grip_'+side))
        for p in study.parts:
            if p['bone']=='hand_'+side:
                assert not intersects(obb(p,bind[p['bone']]),obb(shaft,bind[weapon])),f'{NAMES[study.i]}: shaft penetrates {p["label"]}';checks+=1
    blades=[p for p in study.parts if p['label'].startswith(('blade_','axe_blade'))]
    # Broad torso/head geometry excludes intentionally layered chest ornaments.
    cores=[p for p in study.parts if p['bone']=='chest' and p['label']=='chest' and p['size'][2]>=4]
    cores += [p for p in study.parts if p['bone']=='head' and p['label']=='head' and p['size'][2]>=4]
    for a in model['animations']:
        for t in np.linspace(0,a['length'],17):
            transforms=matrices(model,a['name'],float(t))
            for blade in blades:
                for body in cores:
                    assert not intersects(obb(blade,transforms[blade['bone']]),obb(body,transforms[body['bone']])),f'{NAMES[study.i]} {a["name"]}: blade penetrates core';blade_checks+=1
            for side,weapon,point in study.grips:
                hand=np.append(study.at('hand_'+side),1);grip=np.append(point,1)
                error=float(np.linalg.norm((transforms['hand_'+side]@hand)[:3]-(transforms[weapon]@grip)[:3]));grip_errors.append(error)
                assert error<grip_tolerance,f'{NAMES[study.i]} {a["name"]} {side} grip drift {error}';checks+=1
    for t in np.linspace(0,1,33):
        matrices_=matrices(model,'walk',float(t))
        for side in ['r','l']:
            # Complete foot, including layered toe plate, remains at or above the floor.
            for p in study.parts:
                if p['bone']!='foot_'+side:continue
                lo=np.array(p['at']);hi=lo+np.array(p['size']);o=(lo+hi)/2;r=rig.rotation(p['rot'])
                verts=np.array([[x,y,z] for x in [lo[0],hi[0]] for y in [lo[1],hi[1]] for z in [lo[2],hi[2]]]);verts=(verts-o)@r.T+o
                verts=np.column_stack((verts,np.ones(8)))@matrices_['foot_'+side].T
                assert verts[:,1].min()>-.025,'foot below floor';checks+=1
    for yaw in [-50,0,50]:
        m=matrices(model,'idle',0,(yaw,20));forward=m['head'][:3,:3]@np.array([0,0,-1]);a,p=math.radians(yaw),math.radians(20)
        assert np.dot(forward,[math.sin(a)*math.cos(p),-math.sin(p),-math.cos(a)*math.cos(p)])>.9999;checks+=1
        assert np.allclose(m['chest'][:3,:3],np.eye(3));checks+=1
    for e in model['elements']:
        assert all(math.isfinite(v) for v in e['from']+e['to']);assert all(a<b for a,b in zip(e['from'],e['to']));checks+=1
    return dict(checks=checks,blade_core_collision_samples=blade_checks,max_grip_error_units=max(grip_errors,default=0),geometry_cubes=len(study.parts),bones=len(study.bones),animations=len(model['animations']))

def hand_detail(study):
    size=1300;yaw=25;el=10;scale=(size-85)/57
    pos=(study.at('hand_r')+study.at('hand_l'))/2
    crop_w,crop_h=600,390
    if study.i==0:pos+=np.array([0,-3,0]);crop_w,crop_h=610,470
    elif study.i==2:pos=study.at('hand_l');crop_w,crop_h=260,310
    elif study.i==3:pos=study.at('hand_r');crop_w,crop_h=280,290
    elif study.i==4:pos=study.at('hand_r')+np.array([0,2.4,0]);crop_w,crop_h=320,270
    elif study.i==5:crop_w,crop_h=390,410
    a,e=map(math.radians,[yaw,el]);x=size/2+(pos[0]*math.cos(a)+pos[2]*math.sin(a))*scale
    y=size*.83+(-pos[1]*math.cos(e)+pos[0]*math.sin(e)*math.sin(a)-pos[2]*math.sin(e)*math.cos(a))*scale
    raw=rig.render('idle',0,yaw=yaw,elevation=el,size=size)
    crop=raw.crop((int(x-crop_w/2),int(y-crop_h/2),int(x+crop_w/2),int(y+crop_h/2)))
    crop.thumbnail((570,410),Image.Resampling.LANCZOS)
    card=Image.new('RGB',(620,480),(23,21,34));card.paste(crop,((620-crop.width)//2,55+(410-crop.height)//2))
    font=ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf',24);ImageDraw.Draw(card).text((20,16),TITLES[study.i]+' / hand study',font=font,fill=COLORS[study.i])
    return card

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--draft',action='store_true');args=parser.parse_args()
    OUT.mkdir(parents=True,exist_ok=True)
    for name in ['models','sigils']:(OUT/name).mkdir(exist_ok=True)
    font=ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf',28);small=ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',17)
    sheet=Image.new('RGB',(1860,1580),(16,15,25));draw=ImageDraw.Draw(sheet)
    draw.text((30,24),'ARKHAIA / SIX RIVERS / SECOND SCULPT',font=font,fill=(237,226,249));draw.text((32,65),'Distinct silhouettes, fitted grips and personal ritual seals / Editable geometry / Preview only',font=small,fill=(172,158,192))
    sigils=Image.new('RGB',(1380,1020),(16,15,25));sd=ImageDraw.Draw(sigils);sd.text((25,20),'PERSONAL RITUAL SEALS / COMPACT FIELD EFFECTS',font=font,fill=(237,226,249))
    manifest=[];hands=Image.new('RGB',(1860,960),(23,21,34))
    for i,name in enumerate(NAMES):
        study=Study(i).sculpt();model,tex=build(study);result=validate(study,model,tex);result.update(name=name,weapon=WEAPONS[i],sigil_color='#'+''.join(f'{v:02x}' for v in COLORS[i]),preview_only=True)
        manifest.append(result);(OUT/'models'/f'{name}.bbmodel').write_text(json.dumps(model,separators=(',',':')),encoding='utf8');tex.save(OUT/'models'/f'{name}.png')
        rune=sigil(i);rune.save(OUT/'sigils'/f'{name}.png');smallrune=rune.resize((410,410),Image.Resampling.LANCZOS)
        x=i%3*460+25;y=i//3*470+85;sigils.paste(smallrune,(x,y),smallrune);sd.text((x,y+415),TITLES[i],font=small,fill=COLORS[i])
        load(model,tex);pose='hover' if i==3 else 'idle';card=Image.new('RGB',(620,720),(23,21,34));card.paste(picture(study,pose,.4),(0,45));d=ImageDraw.Draw(card)
        hands.paste(hand_detail(study),(i%3*620,i//3*480))
        d.text((25,17),f'0{i+1} / {TITLES[i]}',font=font,fill=COLORS[i]);d.text((26,55),ROLES[i],font=small,fill=(191,179,208));d.text((25,663),WEAPONS[i],font=small,fill=COLORS[i]);d.text((25,691),f'{len(study.parts)} cubes / fitted articulated hands',font=small,fill=(172,159,192))
        card.save(OUT/f'{name}.png');sheet.paste(card,(i%3*620,105+i//3*730))
        views=Image.new('RGB',(1860,680),(23,21,34));vd=ImageDraw.Draw(views)
        for j,(yaw,label) in enumerate([(0,'FRONT'),(90,'SIDE'),(180,'BACK')]):views.paste(picture(study,pose,.4,yaw),(j*620,20));vd.text((j*620+25,20),label,font=small,fill=COLORS[i])
        views.save(OUT/f'{name}-turnaround.png')
        if not args.draft:
            frames=[]
            for n in range(24):
                t=n/24;frame=picture(study,'hover' if i==3 else 'walk',t*(2 if i==3 else 1),size=460,gaze=(math.sin(t*math.tau)*30,10));d=ImageDraw.Draw(frame);d.text((14,12),TITLES[i]+' / '+('hover' if i==3 else 'walk'),font=small,fill=COLORS[i]);frames.append(frame)
            frames[0].save(OUT/f'{name}-motion.gif',save_all=True,append_images=frames[1:],duration=[40,40,45]*8 if i!=3 else [80,85,85]*8,loop=0,optimize=False)
        print(name,result,flush=True)
    sheet.save(OUT/'six-rivers-v2-lineup.png');hands.save(OUT/'hand-studies.png');sigils.save(OUT/'personal-sigils.png');(OUT/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf8')
    (OUT/'README.txt').write_text('''Six Rivers - second sculpt study. Preview only, nothing installed.
Six actual editable Blockbench cube rigs with embedded 1024-pixel atlases.
Uses Arkhaia's R4.1.1 free-format model / animation handedness convention.
Native front -Z; hi_gaze head pivot. Runtime AI, turning, attack timing,
Model Engine import and Bedrock export remain separate integration work.
Personal sigils are separate transparent textures, not baked to the feet.
Proposed runtime radius: 1.0-1.3 blocks, no damage implementation in this preview.
Grip positions are sampled across every animation. Styx locks both arm chains
and carries the axe through a shared chest transform to preserve both grips.
Walking soles, separate head yaw/pitch and finite geometry are checked.
Render front/side/back and moving GIFs are from these exact model files.
Full inter-part collision and live game rendering have not yet been certified.
''',encoding='utf8')
    html='''<!doctype html><html><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>Six Rivers - Second Sculpt</title><style>body{background:#100f19;color:#ede2fa;font:16px Segoe UI,sans-serif;max-width:1700px;margin:30px auto;padding:20px}p{color:#b7a7c9}img{max-width:100%;height:auto;border-radius:10px}main{display:grid;grid-template-columns:repeat(auto-fit,minmax(350px,1fr));gap:20px}article{background:#171522;padding:18px;border:1px solid #3a3049;border-radius:14px}a{color:#d6b4ff}summary{padding:12px;cursor:pointer}article img{width:100%}</style><h1>ARKHAIA / SIX RIVERS / SECOND SCULPT</h1><p>Editable model previews. Distinct equipment, fitted hand poses and small personal seals. Nothing installed to Serva.</p><img src="six-rivers-v2-lineup.png"><h2>Personal ritual colors</h2><img src="personal-sigils.png"><main>'''
    for i,name in enumerate(NAMES):html+=f'<article><h2>{TITLES[i]}</h2><p>{WEAPONS[i]}</p><img loading="lazy" src="{name}-motion.gif"><p><a href="models/{name}.bbmodel" download>Editable model</a> / <a href="sigils/{name}.png">Transparent sigil</a></p><details><summary>Front / side / back</summary><a href="{name}-turnaround.png"><img loading="lazy" src="{name}-turnaround.png"></a></details></article>'
    html+='</main><h2>Hands and equipment - geometry closeups</h2><a href="hand-studies.png"><img src="hand-studies.png"></a><p>Model Engine runtime import, Bedrock export, combat contact and connected-client validation remain pending. See README and manifest.</p></html>'
    (OUT/'index.html').write_text(html,encoding='utf8')
    if not args.draft:
        with zipfile.ZipFile(OUT/'six-rivers-v2-model-studies.zip','w',zipfile.ZIP_DEFLATED) as z:
            for p in sorted(OUT.rglob('*')):
                if p.is_file() and p.suffix!='.zip':z.write(p,p.relative_to(OUT))

if __name__=='__main__':main()
