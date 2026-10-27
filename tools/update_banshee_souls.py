"""Author Banshee curse, broken-veil reveal and reformation on the existing rig."""
import json
from pathlib import Path
import numpy as np
from update_river_choreography import groups,aim_arm,clip,ease,mix

ROOT=Path(__file__).resolve().parents[1]
PATH=ROOT/'evergarden/src/main/resources/modelengine/rivers/arkhaia_banshee_wail.bbmodel'

def pose(model,lift,release):
    bones=groups(model);p={'head':[-12*lift,0,0],'jaw':[12*lift+20*release,0,0],'cape':[-18*lift-14*release,0,0],'cape_tip':[-12*lift-10*release,0,0]}
    for side,sign in [('r',-1),('l',1)]:
        ready=[sign*13,29,-7];active=[sign*13,24,-6]
        target=mix(bones['hand_'+side]['origin'],ready,lift) if release==0 else mix(ready,active,release)
        p.update(aim_arm(bones,side,target,[sign*17,25,-3]))
    for i in range(6):p['veil_'+str(i)]=[-12*lift-24*release,0,(i-2.5)*3*lift]
    return p

def author():
    model=json.loads(PATH.read_text(encoding='utf8'));new=[]
    for name,length in [('curse_windup',1),('curse_active',.5),('curse_recover',.7),('reveal',1),('reform',1)]:
        poses=[];positions=[];previous={}
        for n in range(round(length*80)+1):
            t=round(n/80,6);pos={}
            if name=='curse_windup':p=pose(model,ease(t/.8),0)
            elif name=='curse_active':p=pose(model,1,ease(t/.1))
            elif name=='curse_recover':p=pose(model,1-ease((t-.3)/.4),1-ease(t/.3))
            elif name=='reveal':
                strength=ease(t/.35)
                p={'chest':[-12*strength,0,0],'head':[20*strength,0,0],'arm_r':[-12*strength,0,-15*strength],'arm_l':[-12*strength,0,15*strength],'forearm_r':[-15*strength,0,0],'forearm_l':[-15*strength,0,0],'cape':[-22*strength,0,0],'cape_tip':[-16*strength,0,0]}
                pos={'root':[0,.3*strength,0]}
            else:
                strength=ease(t/.65);p=pose(model,strength,0);pos={'root':[0,.8*np.sin(np.pi*t),0]}
            for bone,v in p.items():
                if bone in previous:
                    candidates=[v,[v[0]+180,180-v[1],v[2]+180]]
                    candidates=[[a+360*round((b-a)/360) for a,b in zip(c,previous[bone])] for c in candidates]
                    p[bone]=min(candidates,key=lambda c:sum((a-b)**2 for a,b in zip(c,previous[bone])))
                previous[bone]=p[bone]
            poses.append((t,p));positions.append((t,pos))
        new.append(clip(model,name,length,poses,positions))
    names={a['name'] for a in new};model['animations']=[a for a in model['animations'] if a['name'] not in names]+new
    PATH.write_text(json.dumps(model,separators=(',',':')),encoding='utf8');print('Banshee:',len(model['animations']),'clips')

if __name__=='__main__':author()
