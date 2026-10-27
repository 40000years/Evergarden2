"""A visual/structural correction pass; historic v4 defaults stay reproducible."""
import math
from collections import Counter
from sanctuary_rooms_v4 import Interior,complete as furnish,LIMITS
from sanctuary_design_v3 import affordable

class Polish(Interior):
    def __init__(self,b):
        super().__init__(b);self.current_room=None
        if b.name=='acheron':self.wood='dark_oak_planks'

    def table(self,x,f,z,rx=2,rz=1):
        b=self.b
        # A tabletop one block above the walking surface, with visible corner legs.
        b.box(x-rx,f+1,z-rz,x+rx,f+1,z+rz,'dark_oak_slab[type=top]')
        for dx in (-rx,rx):
            for dz in (-rz,rz):b.put(x+dx,f+1,z+dz,'dark_oak_stairs[half=top,facing='+('east' if dx<0 else 'west')+']')
        b.put(x-rx,f+2,z,'white_candle[candles=2,lit=true]');b.put(x+rx,f+2,z,'flower_pot')

    def cabinet(self,x,f,z,w=3):
        b=self.b;b.box(x-w,f+1,z,x+w,f+4,z,self.wood)
        for dx in range(-w+1,w,2):b.box(x+dx,f+2,z+1,x+dx,f+3,z+1,'spruce_trapdoor[open=true,facing=north]')
        b.box(x-w,f+4,z,x+w,f+4,z,'dark_oak_slab[type=top]')
        self.plant(x-w,f+4,z)

    def artwork(self,x,f,z,w=5,motif='landscape'):
        b=self.b;direction=1
        if self.current_room is not None:
            x1,_,z1,x2,_,z2=self.current_room
            direction=-1 if z>(z1+z2)//2 else 1
        # Inward-facing images: the previous front-wall panels faced out of rooms.
        half=w//2;b.box(x-half-1,f+3,z,x+half+1,f+8,z,self.wood)
        portrait=['.RRR.','.KSK.','.SSS.','KKRKK'];moon=['..WW.','.WW..','.WW..','..WW.'];sun=['..Y..','.YYY.','YYYYY','.YYY.']
        colors={'.':'black_concrete','R':'red_terracotta','K':'gray_concrete','S':'light_gray_terracotta','W':'white_concrete','Y':'yellow_terracotta'}
        grid=portrait if motif=='portrait' else moon if motif=='moon' else sun
        for dx in range(-half,half+1):
            for dy in range(4):
                sample=round((dx+half)*4/max(1,2*half));m=colors[grid[3-dy][sample]]
                if motif=='landscape':m='light_blue_terracotta' if dy>=2 else 'green_terracotta'
                b.put(x+dx,f+4+dy,z+direction,m)

    def cell(self,x1,z1,x2,z2,f,role,upper=False):
        old=self.current_room;self.current_room=(x1,f,z1,x2,f+8,z2)
        super().cell(x1,z1,x2,z2,f,role,upper);self.current_room=old
        if x2-x1<7 or z2-z1<7:return
        b=self.b;cx,cz=(x1+x2)//2,(z1+z2)//2
        # Ceiling cross-beams actually suspend the room lights and support the hearth.
        b.box(x1,f+8,cz,x2,f+8,cz,self.panel)
        b.box(cx,f+8,z1,cx,f+8,z2,self.panel)
        if role in ('library','archive','laboratory','cartography','study'):
            b.put(cx,f+3,cz,'air');b.put(cx,f+2,cz,'lectern[facing=south,has_book=false]')
        if role in ('kitchen','pantry'):
            # Wall brackets carry the overhead rack rather than leaving a flying shelf.
            for x in (x1+1,x2-1):b.box(x,f+2,z1+1,x,f+4,z1+1,self.panel)

    def portal(self,cx,z,f,w,h):
        b=self.b
        for sign in (-1,1):b.box(cx+sign*(w+2)-1,f,z-1,cx+sign*(w+2)+1,f+h-4,z+2,b.trim)
        heights={dx:h-round(5*(abs(dx)/(w+2))**1.7) for dx in range(-w-2,w+3)}
        for dx,hh in heights.items():
            low=min(heights.get(dx-1,hh),heights.get(dx+1,hh),hh)
            b.box(cx+dx,f+low,z-1,cx+dx,f+hh+2,z+2,b.trim)
            if abs(dx)<=w:b.box(cx+dx,f+1,z-3,cx+dx,f+hh-1,z+3,'air')

    def rose(self,cy,z,r):
        b=self.b
        for dx in range(-r-1,r+2):
            for dy in range(-r-1,r+2):
                d=dx*dx+dy*dy
                if d>(r+1)**2:continue
                frame=d>=(r-1)**2 or dx==0 or dy==0 or abs(dx)==abs(dy)
                b.put(dx,cy+dy,z,b.trim if frame else b.glass)
                if d>=(r-1)**2:b.put(dx,cy+dy,z+1,b.trim)

    def axe(self):
        b=self.b
        b.box(-21,39,-48,21,84,-36,'air')
        # The plinth is part of the gate bridge, and the forged socket clamps the haft.
        b.box(-5,39,-48,5,42,-38,b.trim);b.box(-3,43,-46,3,45,-40,'polished_andesite')
        b.box(-1,46,-44,1,81,-42,'stripped_dark_oak_log[axis=y]')
        for y in (49,54,76,80):b.box(-2,y,-45,2,y,-41,'polished_blackstone')
        for sign in (-1,1):
            for dy in range(-12,13):
                arc=math.sqrt(max(0,12*12-dy*dy))
                inner=max(3,math.ceil(7-arc))
                if abs(dy)<=9:inner=max(inner,math.ceil(2+math.sqrt(9*9-dy*dy)))
                if abs(dy)<=2:inner=3
                outer=math.floor(7+arc)
                for d in range(inner,outer+1):
                    x=sign*d;y=64+dy
                    m='polished_diorite' if d>=outer-1 else 'polished_andesite'
                    b.box(x,y,-45,x,y,-43,m)
                    if d in (inner,inner+1):b.put(x,y,-46,'stone_bricks')
        b.box(-3,61,-46,3,67,-40,'chiseled_polished_blackstone')
        for x in (-2,2):b.put(x,64,-47,'iron_block')
        b.put(0,82,-43,'yellow_terracotta')
        b.design['axe']={'bounds':[-21,39,-48,21,83,-36],'blade_center':[0,64,-44],
            'profile':'connected crescent blades, bevel, forged socket, bound haft and stone plinth'}

    def fix_acheron(self):
        b=self.b
        for cx,cz,r,y in ((-77,-81,10,84),(-87,79,7,27),(87,79,7,27)):
            b.box(cx-r,y,cz-r,cx+r,y,cz+r,b.trim)
        b.box(-10,35,-26,10,55,-24,'air');self.rose(45,-27,9)
        b.box(-10,6,-26,10,33,-20,'air');self.portal(0,-26,5,5,17)
        for sign in (-1,1):
            b.box(sign*11,5,-27,sign*11,35,-25,b.trim)
            b.box(sign*33-2,51,-32,sign*33-2,52,-23,b.trim)
        # Proper recessed stone mullions and sills instead of flat red panels.
        for x in (-24,26):
            for z in range(-83,-31,10):
                b.box(x,10,z,x,28,z,b.trim)
                b.box(x-1,9,z-2,x+1,9,z+2,b.trim)

    def fix_phlegethon(self):
        b=self.b
        # Remove the four wall sheets on the roof. Two braced trusses carry a gantry.
        b.box(-39,35,-85,41,46,-29,'air')
        b.box(-39,31,-85,39,32,-29,b.trim)
        for x in (-36,36):
            for z in (-81,-33):b.box(x,5,z,x,39,z,'polished_basalt')
        for z in (-81,-33):
            b.box(-36,39,z,36,40,z,b.trim)
            for x in range(-34,35,10):
                b.line((x,34,z),(x+5,39,z),'polished_basalt')
                b.line((x+5,39,z),(x+10,34,z),'polished_basalt')
        for x in (-36,36):b.box(x,40,-81,x,41,-33,b.trim)
        for x in (-12,-8,8,12):b.put(x,6,-78,'smooth_stone')
        for x,h in ((-65,48),(65,58)):
            b.box(x-7,h,-75,x+7,h,-61,b.trim)
            b.box(x-2,h+1,-70,x+2,h+1,-66,'soul_campfire[lit=true]')
        # Furnace insignia and frontage now have masonry behind them.
        b.box(-9,20,-25,9,33,-24,b.wall)
        for x in (-36,36):b.box(x-1,0,-26,x+1,32,-24,b.trim)

    def fix_cocytus(self):
        b=self.b
        b.box(-9,35,-27,9,53,-25,'air');self.rose(44,-28,8)
        # Four portico columns were standing five blocks above the ground.
        b.box(-15,0,-30,15,5,-17,b.trim)
        b.box(-14,26,-26,14,27,-20,'white_concrete')
        b.stairs(0,-6,6,5,0)
        b.box(-6,5,-19,6,5,-16,b.trim)
        for y in range(54,63):b.put(-79,y,-30,'iron_chain[axis=y]')
        for x in (-9,9):b.box(x,23,-88,x,23,-84,b.trim)
        for x in (-8,8):b.box(x,6,-83,x,7,-83,b.trim)
        # A usable belfry, with linked landings and supported spiral treads.
        for y in (14,28,42,51,60):b.disk(-79,y,-30,4,'spruce_planks')
        path=[];q=3
        for x in range(-q,q):path.append((x,-q))
        for z in range(-q,q):path.append((q,z))
        for x in range(q,-q,-1):path.append((x,q))
        for z in range(q,-q,-1):path.append((-q,z))
        for i in range(118):
            dx,dz=path[i%len(path)];y=2+i//2
            coords=[(dx,dz)]
            if abs(dx)==q:coords.extend(((dx-(1 if dx>0 else -1),dz),(dx+(1 if dx>0 else -1),dz)))
            else:coords.extend(((dx,dz-(1 if dz>0 else -1)),(dx,dz+(1 if dz>0 else -1))))
            for xx,zz in coords:
                b.put(-79+xx,y,-30+zz,'spruce_planks')
                b.box(-79+xx,y+1,-30+zz,-79+xx,y+3,-30+zz,'air')
        b.box(-81,3,-25,-77,9,-20,'air');b.stairs(-79,-17,2,2,0)
        b.routes.append((-79,61,-30))
        # Continuous snow strips follow roof contours, replacing the checker pattern.
        for p,m in list(b.b.items()):
            if m=='snow':b.put(*p,'air')
        for (x,y,z),m in list(b.b.items()):
            if m=='blue_concrete' and y>12 and (x,y+1,z) not in b.b and (x%9 in (0,1) or z%17 in (0,1)):
                b.put(x,y+1,z,'snow[layers=1]')

    def fix_banshee(self):
        b=self.b
        # Replace the overlapping old steeple/cage with one continuous lantern house.
        b.box(64,61,-89,86,91,-67,'air')
        b.box(68,61,-85,82,61,-71,b.wood)
        for x in (69,81):
            b.box(x,62,-84,x,70,-72,'cyan_stained_glass')
        for z in (-84,-72):b.box(69,62,z,81,70,z,'cyan_stained_glass')
        for x in (69,81):
            for z in (-84,-72):b.box(x,62,z,x,71,z,'dark_oak_log[axis=y]')
        for dy in range(8):b.disk(75,71+dy,-78,8-dy,b.roof)
        b.box(75,67,-78,75,70,-78,'iron_chain[axis=y]');b.put(75,66,-78,'sea_lantern')
        b.put(75,79,-78,'dark_oak_log[axis=y]');b.put(75,80,-78,'soul_lantern')
        for x in (-30,30):
            for z in range(-78,-30,12):
                b.box(x-1,4,z,x+1,5,z,b.trim)
        for (x,y,z),m in list(b.b.items()):
            if m=='dark_oak_fence' and y==5 and (x,4,z) not in b.b:
                b.put(x,4,z,b.wood)

    def fix_malefeasance(self):
        b=self.b
        for (x,y,z) in list(b.b):
            if y>=32 and x*x+(z+59)**2<=35**2:b.put(x,y,z,'air')
        height={}
        for x in range(-33,34):
            for dz in range(-33,34):
                if x*x+dz*dz<=33*33:height[x,dz]=round(24*math.sqrt(max(0,1-(x*x+dz*dz)/(33*33))))
        for (x,dz),h in height.items():
            lo=min(height.get((x+dx,dz+dd),0) for dx,dd in ((1,0),(-1,0),(0,1),(0,-1)))
            for y in range(32+max(0,lo-1),33+h):b.put(x,y,-59+dz,'cyan_concrete')
            if x==0 or dz==0 or abs(x)==abs(dz):b.put(x,32+h,-59+dz,'yellow_terracotta')
        b.disk(0,32,-59,34,b.trim,32)
        b.box(-1,57,-60,1,59,-58,b.trim)
        for x in (-1,1):b.put(x,59,-59,'gold_block')
        b.put(0,60,-59,b.trim);b.put(0,61,-59,b.light)
        b.disk(0,7,-59,2,b.trim)
        # Radial shelves are carried by carved column bands at the rear.
        for x in (-12,-6,6,12):
            zz=-59-round(math.sqrt(28*28-x*x));b.box(x,5,zz,x,10,zz,self.wood)
        for (x,y,z),m in list(b.b.items()):
            if m=='birch_fence' and y==18 and x*x+(z+59)**2<24**2:b.put(x,17,z,b.wood)

    def fix_styx(self):
        b=self.b
        b.box(-14,0,-48,14,5,-37,b.trim);b.stairs(0,-26,6,5,0)
        b.box(-6,5,-39,6,5,-36,b.trim)
        b.box(-16,29,-40,16,38,-39,b.wall)
        for x in (-14,14):b.box(x,5,-47,x,38,-39,b.trim)
        for cx in (-32,32):
            b.box(cx-13,66,-55,cx+13,74,-29,'air')
            for y in (66,67):
                for dx in range(-10,11):
                    for dz in range(-10,11):
                        if max(abs(dx),abs(dz))>=8:b.put(cx+dx,y,-42+dz,b.trim)
            for t in range(-9,10,4):
                for sign in (-1,1):
                    b.box(cx+t,68,-42+sign*9,cx+t+1,71,-42+sign*9,b.wall)
                    b.box(cx+sign*9,68,-42+t,cx+sign*9,71,-42+t+1,b.wall)
            b.box(cx-2,1,-34,cx+2,7,-30,'air')
            for xx in (-5,5):
                for y in (12,26,40,52):b.box(cx+xx,y,-33,cx+xx,y,-31,b.trim)
            b.box(cx-2,52,-33,cx-2,52,-29,b.trim)
        self.axe()

    def polish(self):
        getattr(self,'fix_'+self.b.name)()
        self.link_steps();self.suspend_lights();self.connect_small_details();self.connect_block_states()
        self.b.design['polish']='Attached structure, proportionate furniture, supported ornaments and revised silhouettes'

    def link_steps(self):
        b=self.b
        protected={(x+dx,y+dy,z+dz) for x,y,z in b.routes for dx in (-1,0,1) for dz in (-1,0,1) for dy in (0,1,2)}
        def thicken(x,y,z):
            m=b.b.get((x,y,z),'')
            if m.endswith('_planks') and (x,y-1,z) not in b.b and (x,y-1,z) not in protected:b.put(x,y-1,z,m)
        for room in b.rooms:
            x1,f,z1,x2,top,z2=room['bounds']
            if top-f<19 or x2-x1<27 or z2-z1<27:continue
            if b.name=='phlegethon' and x1==-79:continue
            cx=(x1+x2)//2;yy=min(top-4,f+19)
            for z in range(z1+15,z2-4,13):
                for dx in range(-6,7):
                    h=yy+3-abs(dx)//2
                    if (cx+dx,h,z) in b.b and (cx+dx,h-1,z) not in b.b:b.put(cx+dx,h-1,z,b.trim)
            for dy in range(10):
                zz=z1+28-dy*2
                for x in range(x2-7,x2-3):
                    for z in (zz-1,zz):thicken(x,f+dy,z)
        for cx,cz,r,h,f in b.towers:
            q=r-3;path=[]
            for x in range(-q,q):path.append((x,-q))
            for z in range(-q,q):path.append((q,z))
            for x in range(q,-q,-1):path.append((x,q))
            for z in range(q,-q,-1):path.append((-q,z))
            for i in range((h-4)*2):
                dx,dz=path[i%len(path)];thicken(cx+dx,f+i//2,cz+dz)
                if abs(dx)==q:thicken(cx+dx-(1 if dx>0 else -1),f+i//2,cz+dz)
                else:thicken(cx+dx,f+i//2,cz+dz-(1 if dz>0 else -1))
        if b.name=='phlegethon':
            for dy in range(12):
                for x in range(-46,-42):
                    for z in (-36-dy*2,-35-dy*2):thicken(x,5+dy,z)
        if b.name=='malefeasance':
            for dy in range(13):
                for x in range(-28,-24):
                    for z in (-40-dy*2,-39-dy*2):thicken(x,5+dy,z)
        if b.name=='cocytus':
            # The rebuilt belfry uses a tighter radius than the historical tower.
            path=[];q=3
            for x in range(-q,q):path.append((x,-q))
            for z in range(-q,q):path.append((q,z))
            for x in range(q,-q,-1):path.append((x,q))
            for z in range(q,-q,-1):path.append((-q,z))
            for i in range(118):
                dx,dz=path[i%len(path)];y=2+i//2
                thicken(-79+dx,y,-30+dz)
                if abs(dx)==q:
                    thicken(-79+dx-(1 if dx>0 else -1),y,-30+dz)
                    thicken(-79+dx+(1 if dx>0 else -1),y,-30+dz)
                else:
                    thicken(-79+dx,y,-30+dz-(1 if dz>0 else -1))
                    thicken(-79+dx,y,-30+dz+(1 if dz>0 else -1))
        if b.name=='styx':
            for z in (0,25,50):
                for dx in range(-10,11):
                    y=13-abs(dx)
                    for dz in range(-7,8):
                        if b.b.get((-66+dx,y,z+dz))=='white_wool' and (-66+dx,y-1,z+dz) not in b.b:
                            b.put(-66+dx,y-1,z+dz,'white_wool')

    def suspend_lights(self):
        b=self.b;added=0
        for (x,y,z),m in list(b.b.items()):
            if not m.startswith('iron_chain') or (x,y+1,z) in b.b:continue
            target=next((yy for yy in range(y+2,min(150,y+25)) if (x,yy,z) in b.b),None)
            if target is not None:
                for yy in range(y+1,target):b.put(x,yy,z,'iron_chain[axis=y]');added+=1
        b.design['extended_ceiling_hangers']=added

    def connect_small_details(self):
        """Short masonry ties, dormer backing and buttress feet; no long poles.

        Major assemblies are authored explicitly above. This only reconnects
        tiny inherited trims (<=40 voxels) across a small air gap to the building.
        The connection positions are retained in the review metadata.
        """
        b=self.b;remaining=set(b.b);groups=[]
        while remaining:
            start=remaining.pop();q=[start];group={start}
            while q:
                x,y,z=q.pop()
                for p in ((x-1,y,z),(x+1,y,z),(x,y-1,z),(x,y+1,z),(x,y,z-1),(x,y,z+1)):
                    if p in remaining:remaining.remove(p);group.add(p);q.append(p)
            groups.append(group)
        anchor=max(groups,key=len);repairs=[];removed=[]
        protected={(x+dx,y+dy,z+dz) for x,y,z in b.routes for dx in (-1,0,1) for dz in (-1,0,1) for dy in (0,1,2)}
        for group in sorted(groups,key=len,reverse=True):
            if group is anchor or len(group)>40:continue
            structural=[p for p in group if any(s in b.b[p] for s in ('bricks','terracotta','planks','slab','_log','wool','diorite','basalt','deepslate','blackstone'))]
            if not structural:continue
            best=None;low=min(p[1] for p in group);limit=6 if low<=7 else 3
            for x,y,z in structural:
                for direction in ((0,-1,0),(-1,0,0),(1,0,0),(0,0,-1),(0,0,1)):
                    ray=[]
                    for dist in range(1,limit+1):
                        p=(x+direction[0]*dist,y+direction[1]*dist,z+direction[2]*dist)
                        if p in anchor:
                            if ray and not any(p in protected for p in ray) and (best is None or len(ray)<len(best[0])):best=(ray,b.b[x,y,z])
                            break
                        if p in b.b:break
                        ray.append(p)
            if best is None:
                # Detached slivers left by old doorway/stair cuts have no useful
                # structural role; remove them rather than inventing long props.
                if all(affordable(b.b[p]) for p in group):
                    removed.append({'voxels':len(group),'bounds':[min(p[i] for p in group) for i in range(3)]+[max(p[i] for p in group) for i in range(3)]})
                    for p in group:b.put(*p,'air')
                continue
            ray,m=best
            for p in ray:b.put(*p,m)
            anchor.update(group);anchor.update(ray)
            repairs.append({'assembly_size':len(group),'ties':[list(p) for p in ray],'material':m})
        b.design['small_detail_connections']=repairs
        b.design['removed_detached_remnants']=removed

    def connect_block_states(self):
        """Populators use physics=false; fence/wall joins must be authored explicitly.

        Otherwise the game shows isolated pickets even where a preview infers rails.
        """
        b=self.b;count=0
        nonsolid=('glass','leaves','trapdoor','door','carpet','stairs','slab','bed','chain','lantern','pot','candle','snow','water','lava','fence','wall','bars','rail','vine','campfire','flower','fern','allium','stonecutter','grindstone','lectern')
        for (x,y,z),m in list(b.b.items()):
            material=m.split('[')[0]
            if not material.endswith(('_fence','_wall')):continue
            faces={}
            for face,dx,dz in (('north',0,-1),('south',0,1),('east',1,0),('west',-1,0)):
                n=b.b.get((x+dx,y,z+dz),'air').split('[')[0]
                faces[face]=n.endswith(('_fence','_wall')) or (n!='air' and not any(s in n for s in nonsolid))
            if material.endswith('_fence'):
                state=','.join(face+'='+str(on).lower() for face,on in faces.items())+',waterlogged=false'
            else:
                straight=(faces['north'] and faces['south'] and not faces['east'] and not faces['west']) or (faces['east'] and faces['west'] and not faces['north'] and not faces['south'])
                state=','.join(face+'='+('low' if on else 'none') for face,on in faces.items())+',up='+str(not straight or (x,y+1,z) in b.b).lower()+',waterlogged=false'
            b.b[x,y,z]=material+'['+state+']';count+=1
        b.design['explicit_fence_wall_states']=count

def complete(b):
    b=furnish(b,decorator=Polish);a=Polish.__new__(Polish)
    # Reuse the existing room/route metadata, rather than resetting it in __init__.
    a.b=b;a.wood='dark_oak_planks' if b.name=='acheron' else b.wood
    a.panel='stripped_spruce_log' if b.name=='cocytus' else 'stripped_dark_oak_log'
    a.current_room=None;a.polish()
    counts=Counter(m.split('[')[0] for m in b.b.values());counts['cauldron']+=counts.pop('water_cauldron',0)
    rare={m:c for m,c in counts.items() if c and not affordable(m)}
    for m,c in rare.items():assert m in LIMITS and c<=LIMITS[m],(b.name,m,c)
    assert sum(rare.values())<=128
    b.design.update({'materials':dict(counts.most_common()),'rare_materials':rare,'rare_total':sum(rare.values()),'routes':b.routes})
    return b
