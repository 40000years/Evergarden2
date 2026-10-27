"""Add Acheron's ritual and a 60%-size, halo-free echo from the production rig."""
import copy
import json
from pathlib import Path
from update_river_choreography import clip

ROOT=Path(__file__).resolve().parents[1]
FOLDER=ROOT/'evergarden/src/main/resources/modelengine/rivers'

def main():
    path=FOLDER/'arkhaia_acheron.bbmodel'
    model=json.loads(path.read_text(encoding='utf8'))
    ready=dict(arm_r=[65,0,-38],arm_l=[65,0,38],forearm_r=[65,0,0],forearm_l=[65,0,0],chest=[-8,0,0],head=[-12,0,0])
    release=dict(arm_r=[35,0,-74],arm_l=[35,0,74],forearm_r=[10,0,0],forearm_l=[10,0,0],chest=[8,0,0],head=[6,0,0])
    rituals=[clip(model,'ritual_windup',1,[(0,{}),(.65,ready),(1,ready)]),
             clip(model,'ritual_active',.5,[(0,ready),(.05,release),(.5,release)]),
             clip(model,'ritual_recover',.7,[(0,release),(.7,{})])]
    model['animations']=[a for a in model['animations'] if not a['name'].startswith('ritual_')]+rituals
    path.write_text(json.dumps(model,separators=(',',':'))+'\n',encoding='utf8')
    echo=copy.deepcopy(model);echo['name']='arkhaia_acheron_echo'
    removed_elements=set();removed_bones=set()
    def discard(group):
        removed_bones.add(group['uuid'])
        for child in group['children']:
            if isinstance(child,dict):discard(child)
            else:removed_elements.add(child)
    def walk(group):
        group['origin']=[v*.6 for v in group['origin']]
        children=[]
        for child in group['children']:
            if isinstance(child,dict):
                if child['name']=='halo':discard(child);continue
                walk(child)
            children.append(child)
        group['children']=children
    for group in echo['outliner']:walk(group)
    echo['elements']=[e for e in echo['elements'] if e['uuid'] not in removed_elements]
    for cube in echo['elements']:
        for field in ('from','to','origin'):
            if field in cube:cube[field]=[v*.6 for v in cube[field]]
    for animation in echo['animations']:
        for bone in removed_bones:animation['animators'].pop(bone,None)
        for animator in animation['animators'].values():
            for frame in animator['keyframes']:
                if frame['channel']=='position':
                    for point in frame['data_points']:
                        for axis in 'xyz':point[axis]=str(float(point[axis])*.6)
    # Identical clip indexes let both client backends use one protocol. The echo
    # AI never selects ritual clips and can never recursively summon children.
    echo['meta']['evergarden_echo_scale']=.6
    (FOLDER/'arkhaia_acheron_echo.bbmodel').write_text(json.dumps(echo,separators=(',',':'))+'\n',encoding='utf8')
    print('Authored Acheron ritual + halo-free echo')

if __name__=='__main__':main()
