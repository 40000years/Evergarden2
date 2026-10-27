"""Extend the approved two-hand axe rig with complete directional combat clips."""
import copy,json
from pathlib import Path
import numpy as np
from update_river_choreography import physical_pose,clip,ease
ROOT=Path(__file__).resolve().parents[1]
PATH=ROOT/'evergarden/src/main/resources/modelengine/rivers/arkhaia_styx.bbmodel'

def author():
    model=json.loads(PATH.read_text(encoding='utf8'));new=[]
    for skill in ('slash','reverse','thrust','dash'):
        sign=-1 if skill=='reverse' else 1
        for stage,length in [('windup',1),('active',.6 if skill=='dash' else .4),('recover',.7)]:
            poses=[];positions=[];previous={}
            for n in range(round(length*80)+1):
                t=round(n/80,6)
                if stage=='windup':lift=ease(t/.8);strike=0
                elif stage=='active':lift=1;strike=ease(t/.2)
                else:lift=1-ease((t-.25)/.45);strike=1-ease(t/.25)
                p,pos=physical_pose(model,'styx',lift,strike)
                # Rotate the entire articulated two-hand grip together. The axe,
                # wrists and elbows preserve the shipped collision-free solution.
                if skill in ('slash','reverse'):
                    turn=(-70*lift if strike==0 else -70+140*strike)*sign
                    if stage=='recover':turn=70*sign*(1-ease(t/.7))
                    p['root']=[0,turn,0]
                elif skill in ('thrust','dash'):
                    # The authored axe drives forwards as its head descends.
                    p['root']=[0,0,0]
                for bone,v in p.items():
                    if bone in previous:
                        candidates=[v,[v[0]+180,180-v[1],v[2]+180]]
                        candidates=[[a+360*round((b-a)/360) for a,b in zip(c,previous[bone])] for c in candidates]
                        p[bone]=min(candidates,key=lambda c:sum((a-b)**2 for a,b in zip(c,previous[bone])))
                    previous[bone]=p[bone]
                poses.append((t,p));positions.append((t,pos))
            new.append(clip(model,skill+'_'+stage,length,poses,positions))
    names={a['name'] for a in new};model['animations']=[a for a in model['animations'] if a['name'] not in names]+new
    PATH.write_text(json.dumps(model,separators=(',',':')),encoding='utf8')
    print('Styx:',len(model['animations']),'clips; two-hand axe choreography retained')
if __name__=='__main__':author()
