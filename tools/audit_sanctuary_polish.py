"""Find detached voxel assemblies and unsupported visible fixtures in shipped assets."""
from collections import Counter,deque
import argparse,json
from pathlib import Path
from preview_overworld_sanctuaries import read_asset
from build_overworld_sanctuaries import IDS,ASSETS

def audit(blocks):
    remaining=set(blocks);groups=[]
    while remaining:
        start=remaining.pop();q=[start];size=0;low=list(start);high=list(start);materials=Counter()
        while q:
            x,y,z=q.pop();size+=1;materials[blocks[x,y,z]]+=1
            for i,v in enumerate((x,y,z)):low[i]=min(low[i],v);high[i]=max(high[i],v)
            for p in ((x-1,y,z),(x+1,y,z),(x,y-1,z),(x,y+1,z),(x,y,z-1),(x,y,z+1)):
                if p in remaining:remaining.remove(p);q.append(p)
        if low[1]>0:groups.append({'blocks':size,'bounds':low+high,'materials':dict(materials)})
    fixtures=[]
    for (x,y,z),m in blocks.items():
        if m.startswith('POTTED_') or m in ('FLOWER_POT','GOLD_BLOCK','SEA_LANTERN'):
            if not any(p in blocks for p in ((x-1,y,z),(x+1,y,z),(x,y-1,z),(x,y+1,z),(x,y,z-1),(x,y,z+1))):
                fixtures.append({'position':[x,y,z],'material':m})
    return {'detached_assemblies':sorted(groups,key=lambda g:-g['blocks']),'isolated_fixtures':fixtures}

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--assets',type=Path,default=ASSETS);p.add_argument('--output',type=Path,required=True);p.add_argument('--require-connected',action='store_true');a=p.parse_args()
    report={}
    for n in IDS:
        r=audit(read_asset(a.assets/(n+'.ows')));report[n]=r
        print(n,'detached:',len(r['detached_assemblies']),'largest:',r['detached_assemblies'][:5],flush=True)
    a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(report,indent=2))
    if a.require_connected:assert all(not r['detached_assemblies'] and not r['isolated_fixtures'] for r in report.values()),'Detached structure or fixture remains'
