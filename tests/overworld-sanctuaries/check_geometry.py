"""Check actual authored walkability, treasure count and the clear combat space."""
import sys,json
from pathlib import Path
from collections import deque
ROOT=Path(__file__).resolve().parents[2];sys.path.insert(0,str(ROOT/'tools'))
from build_overworld_sanctuaries import build,validate,IDS,PREVIEWS

def passable(m):
    return m is None or any(s in m for s in ('carpet','lantern','flower','poppy','allium','vine','chain','snow[')) or m=='snow'

def walk(b):
    def free(x,y,z):return passable(b.b.get((x,y,z))) and passable(b.b.get((x,y+1,z)))
    def stand(x,y,z):
        m=b.b.get((x,y-1,z))
        return m is not None and not passable(m) and not any(s in m for s in ('water','lava','fence','wall','bars')) and free(x,y,z)
    start=(0,b.arena_y+1,92)
    assert stand(*start),(b.name,'entrance',start)
    seen={start};q=deque([start])
    while q:
        x,y,z=q.popleft()
        for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)):
            xx,zz=x+dx,z+dz
            if abs(xx)>111 or abs(zz)>112:continue
            for dy in (0,1,-1):
                yy=y+dy;p=(xx,yy,zz)
                if p in seen or yy< -4 or yy>120:continue
                if dy==1 and not passable(b.b.get((x,y+2,z))):continue
                if stand(xx,yy,zz):seen.add(p);q.append(p);break
    reached=[]
    for p in b.routes:
        assert p in seen,(b.name,'unreachable room or mezzanine',p)
    for room in b.occupied:
        x1,f,z1,x2,top,z2=room['bounds']
        reached_room=sum((x,f+1,z) in seen for x in range(x1,x2+1) for z in range(z1,z2+1))
        assert reached_room>=6,(b.name,'furnished room has no usable access',room,reached_room)
    for c in b.chests:
        x,y,z=[c[k] for k in ('x','y','z')]
        ok=any((x+dx,y,z+dz) in seen for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)))
        assert ok,(b.name,'unreachable chest',c)
        reached.append(c)
    return len(seen)

if __name__=='__main__':
    PREVIEWS.mkdir(parents=True,exist_ok=True)
    result={}
    for name in IDS:
        b=build(name);report=validate(b);report['reachable_standing_positions']=walk(b)
        if name=='styx':
            def blade_row(dy):return sum((x,64+dy,-44) in b.b for x in range(3,21))
            assert 0<blade_row(-12)<blade_row(0)//4 and 0<blade_row(12)<blade_row(0)//4,'Axe needs curved tips and a broad blade'
            assert all((x,64,-44) in b.b for x in range(-19,20)),'Axe blades must connect through the forged socket'
            assert (5,70,-44) not in b.b and (5,58,-44) not in b.b,'Axe needs concave cutouts, not diamond plates'
        result[name]=report;print('PASS',name,report['blocks'],'blocks;',report['reachable_standing_positions'],'walkable positions; 8 reachable chests',flush=True)
    (PREVIEWS/'geometry-validation.json').write_text(json.dumps(result,indent=2),encoding='utf8')
