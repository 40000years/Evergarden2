"""Add articulated furnace kick/throw clips; retain all approved river clips."""
import json
from pathlib import Path
import numpy as np
from update_river_choreography import groups, chain, aim_arm, clip, ease, mix

ROOT=Path(__file__).resolve().parents[1]
PATH=ROOT/'evergarden/src/main/resources/modelengine/rivers/arkhaia_phlegethon.bbmodel'


def pose(model,skill,lift,strike):
    bones=groups(model);p={};positions={}
    if skill=='kick':
        p.update(chest=[12*lift-18*strike,0,-6*lift],head=[-8*lift,0,0])
        # Raise the knee first, then extend the articulated shin towards the
        # player's body. The other foot remains planted throughout the kick.
        raised=[-2.8,10,-5];extended=[-2.8,6,-9]
        target=mix(bones['foot_r']['origin'],raised,lift) if strike==0 else mix(raised,extended,strike)
        p.update(chain(bones,'r',target,[-3,12,-12],('leg_','shin_','foot_')))
        for side,sign in [('r',-1),('l',1)]:
            p.update(aim_arm(bones,side,mix(bones['hand_'+side]['origin'],[sign*13,25,-6],lift),[sign*18,25,-3]))
    else:
        # Both palms stay outside the torso as the boulder rises above the
        # head, then swing forward together to release at active time 0.2s.
        for side,sign in [('r',-1),('l',1)]:
            rest=[sign*5,24,-9]
            target=mix(rest,[sign*4,41,-1],lift) if strike==0 else mix([sign*4,41,-1],[sign*5,27,-9],strike)
            if strike==0:target[2]=-9+8*ease((lift-.94)/.06)
            if strike>0:
                target[2]=-1-8*ease(strike/.18)
            shrug=5*lift*(1-strike);target[1]-=shrug
            shoulder=np.array(bones['arm_'+side]['origin']);delta=target-shoulder
            forward=min(0,float(delta[2])+np.sqrt(max(0,11.8**2-delta[0]**2-delta[1]**2)))
            target[2]-=forward
            p.update(aim_arm(bones,side,target,[sign*17,30,-5]))
            positions['arm_'+side]=[0,shrug,forward];positions['shoulder_'+side]=[0,shrug,forward]
        p['head']=[-10*lift+15*strike,0,0]
    return p,positions


def author():
    model=json.loads(PATH.read_text(encoding='utf8'));new=[]
    for skill in ['kick','throw']:
        for stage,length in [('windup',1),('active',.5),('recover',.7)]:
            poses=[];positions=[];previous={}
            for n in range(round(length*80)+1):
                t=round(n/80,6)
                if stage=='windup':lift=ease(t/.8);strike=0
                elif stage=='active':lift=1;strike=ease(t/.2)
                else:strike=1-ease(t/.3);lift=1-ease((t-.3)/.4)
                p,pos=pose(model,skill,lift,strike)
                if skill=='throw' and stage=='recover' and t>.5:
                    rest=ease((t-.5)/.2)
                    p={bone:list(np.array(v)*(1-rest)) for bone,v in p.items()}
                    pos={bone:list(np.array(v)*(1-rest)) for bone,v in pos.items()}
                for bone,v in p.items():
                    if bone in previous:
                        candidates=[v,[v[0]+180,180-v[1],v[2]+180]]
                        candidates=[[a+360*round((b-a)/360) for a,b in zip(c,previous[bone])] for c in candidates]
                        p[bone]=min(candidates,key=lambda c:sum((a-b)**2 for a,b in zip(c,previous[bone])))
                    previous[bone]=p[bone]
                poses.append((t,p));positions.append((t,pos))
            new.append(clip(model,skill+'_'+stage,length,poses,positions))
    model['animations']=[a for a in model['animations'] if not a['name'].startswith(('kick_','throw_'))]+new
    PATH.write_text(json.dumps(model,separators=(',',':')),encoding='utf8')
    print('Phlegethon:',len(model['animations']),'clips; added knee lift, forward kick and two-hand boulder throw')


if __name__=='__main__':author()

