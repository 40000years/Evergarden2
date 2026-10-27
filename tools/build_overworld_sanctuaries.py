"""Author the six Overworld sanctuaries. These voxels are the shipped geometry.

No image generation: runtime assets, reference views and cutaways share this data.
Coordinates are relative to the arena floor; south (+Z) is the entrance.
"""
from __future__ import annotations
import argparse, gzip, json, math, struct
from collections import Counter, deque
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'evergarden/src/main/resources/overworld-sanctuaries'
PREVIEWS = ROOT / 'previews/overworld-sanctuaries-v5-final'
IDS = ('acheron','phlegethon','cocytus','banshee','malefeasance','styx')

class Build:
    def __init__(self, name):
        self.name=name; self.b={}; self.chests=[]; self.rooms=[]; self.towers=[]
        styles={
            'acheron':('deepslate_bricks','polished_blackstone_bricks','deepslate_tiles','red_stained_glass','crimson_planks','red_carpet','soul_lantern'),
            'phlegethon':('polished_blackstone_bricks','polished_blackstone','waxed_cut_copper','orange_stained_glass','dark_oak_planks','orange_carpet','lantern'),
            'cocytus':('quartz_bricks','polished_diorite','blue_concrete','light_blue_stained_glass','spruce_planks','blue_carpet','soul_lantern'),
            'banshee':('dark_oak_planks','mud_bricks','dark_prismarine','cyan_stained_glass','spruce_planks','cyan_carpet','soul_lantern'),
            'malefeasance':('quartz_bricks','smooth_quartz','waxed_oxidized_cut_copper','purple_stained_glass','birch_planks','purple_carpet','soul_lantern'),
            'styx':('deepslate_bricks','chiseled_polished_blackstone','polished_blackstone_bricks','yellow_stained_glass','acacia_planks','yellow_carpet','lantern')}
        self.wall,self.trim,self.roof,self.glass,self.wood,self.carpet,self.light=styles[name]
        self.accent={'acheron':'red_glazed_terracotta','phlegethon':'orange_glazed_terracotta','cocytus':'light_blue_glazed_terracotta','banshee':'cyan_glazed_terracotta','malefeasance':'purple_glazed_terracotta','styx':'gold_block'}[name]
    def put(self,x,y,z,m):
        if m.startswith('chain['):m=m.replace('chain[','iron_chain[',1)
        p=(int(x),int(y),int(z))
        if m=='air': self.b.pop(p,None)
        else:self.b[p]=m
    def box(self,x1,y1,z1,x2,y2,z2,m):
        for x in range(x1,x2+1):
            for z in range(z1,z2+1):
                for y in range(y1,y2+1):self.put(x,y,z,m)
    def disk(self,x,y,z,r,m,inner=0):
        for dx in range(-math.ceil(r),math.ceil(r)+1):
            for dz in range(-math.ceil(r),math.ceil(r)+1):
                if inner**2<=dx*dx+dz*dz<=r*r:self.put(x+dx,y,z+dz,m)
    def line(self,a,b,m,r=0):
        n=max(abs(b[i]-a[i]) for i in range(3))*2+1
        for j in range(n+1):
            p=[round(a[i]+(b[i]-a[i])*j/n) for i in range(3)]
            self.box(p[0]-r,p[1]-r,p[2]-r,p[0]+r,p[1]+r,p[2]+r,m)
    def lamp(self,x,y,z,tall=3):
        self.put(x,y,z,self.trim)
        self.box(x,y+1,z,x,y+tall,z,'dark_oak_fence' if self.name=='banshee' else 'polished_blackstone_wall')
        self.put(x,y+tall+1,z,self.light)
    def banner(self,x,y,z,color):
        self.box(x,y-8,z,x,y,z,'polished_blackstone_wall')
        self.box(x+1,y-8,z,x+3,y-1,z,color+'_wool')
        for dy in range(3):self.put(x+2,y-8-dy,z,'gold_block' if color=='yellow' else 'red_nether_bricks')
    def garden(self,x,z,snow=False):
        if self.name=='phlegethon':
            self.box(x-5,0,z-4,x+5,0,z+4,'polished_blackstone')
            for dx in (-3,0,3):self.box(x+dx,1,z-2,x+dx,2,z,'raw_iron_block' if dx else 'raw_copper_block')
            self.put(x,1,z+2,'anvil');self.lamp(x+5,1,z)
            return
        self.box(x-6,0,z-5,x+6,0,z+5,self.trim)
        self.box(x-5,1,z-4,x+5,1,z+4,'snow_block' if snow else 'grass_block')
        for dx in (-5,5):self.box(x+dx,2,z-4,x+dx,2,z+4,'spruce_leaves[persistent=true]' if snow else 'azalea_leaves[persistent=true]')
        if not snow:
            for dx in (-3,0,3):
                for dz in (-2,2):
                    if self.name=='acheron':
                        self.put(x+dx,2,z+dz,'rose_bush[half=lower]');self.put(x+dx,3,z+dz,'rose_bush[half=upper]')
                    else:self.put(x+dx,2,z+dz,'allium')
        self.lamp(x,2,z)
    def tree(self,x,z,h=17):
        snow=self.name=='cocytus';birch=self.name=='malefeasance';swamp=self.name=='banshee'
        wood='spruce_log' if snow else 'birch_log' if birch else 'dark_oak_log'
        leaves='spruce_leaves[persistent=true]' if snow else 'birch_leaves[persistent=true]' if birch else 'dark_oak_leaves[persistent=true]'
        y=0 if not swamp else -1
        self.box(x,y,z,x+1,y+h,z+1,wood+'[axis=y]')
        if snow:
            for dy in range(14):self.disk(x,y+h-10+dy,z,max(1,6-dy//3),leaves)
        else:
            for dx,dz in ((-3,-2),(3,2),(-2,3),(2,-3)):
                self.line((x,y+h-7,z),(x+dx,y+h-3,z+dz),wood+'[axis=y]')
                for dy in range(-3,4):self.disk(x+dx,y+h-2+dy,z+dz,round(4.5*math.sqrt(max(0,1-(dy/4)**2))),leaves)
            self.disk(x,y+h+2,z,3,leaves)
        if swamp:
            for dx,dz in ((-4,0),(4,0),(0,-4),(0,4)):
                self.box(x+dx,y+h-10,z+dz,x+dx,y+h-3,z+dz,'vine[south=true]')
    def perimeter(self):
        if self.name in ('acheron','cocytus','styx'):
            # Defensive enclosure and a deliberately open south gate.
            for x in (-93,93):
                self.box(x-1,0,-90,x+1,8,85,self.wall)
                self.box(x-2,8,-90,x+2,9,85,self.trim)
                for z in range(-90,86,5):self.box(x-2,10,z,x+2,12,z+2,self.trim)
            for x1,x2 in ((-93,-11),(11,93)):
                self.box(x1,0,83,x2,7,85,self.wall)
                self.box(x1,8,82,x2,9,86,self.trim)
                for x in range(x1,x2,5):self.box(x,10,82,x+2,12,86,self.trim)
            for x in (-87,87):self.tower(x,79,6,25,0,self.name!='styx')
            for x in (-14,14):
                self.box(x-2,0,81,x+2,17,87,self.trim)
                self.lamp(x,18,84,1)
            self.box(-16,16,82,16,19,86,self.trim)
            self.box(-9,1,81,9,14,87,'air')
        if self.name in ('acheron','cocytus','banshee','malefeasance'):
            for x in (-100,100):
                for z in (-88,-49,-5,35,91):self.tree(x,z,17+(z%3)*3)
            if self.name=='malefeasance':
                for x in (-85,-43,43,85):self.tree(x,94,16)
        if self.name=='phlegethon':
            for sign in (-1,1):
                for z in (-87,-57,-12,40,88):
                    for dy in range(8):self.disk(sign*96,dy,z,max(1,8-dy),'orange_terracotta' if dy%3 else 'red_terracotta')
        # Path edges, benches, and low planting provide detail at walking scale.
        if self.name!='banshee':
            for sign in (-1,1):
                for z in (12,42):
                    x=sign*46
                    self.box(x-3,1,z,x+3,1,z,'spruce_slab[type=bottom]')
                    self.box(x-3,1,z-1,x+3,2,z-1,'spruce_trapdoor[open=true,facing=north]')
                    self.put(x-4,1,z,self.light)
    def facade_detail(self,x1,z,x2,floor,height):
        for x in range(x1+3,x2-1,7):
            for dx in (-2,2):
                self.box(x+dx,floor+2,z-1,x+dx,floor+height-3,z-1,self.trim)
            for dy in range(5,height-2):
                half=1 if dy<height-6 else max(0,1-(dy-(height-6))//2)
                self.box(x-half,floor+dy,z,x+half,floor+dy,z,self.glass)
            self.box(x-2,floor+4,z-2,x+2,floor+4,z-1,self.trim)
            self.box(x-2,floor+height-2,z-2,x+2,floor+height-2,z-1,self.trim)
            self.put(x,floor+height-1,z-2,self.light)
    def base(self,swamp=False):
        for x in range(-104,105):
            for z in range(-98,103):
                if (x/106)**8+((z-1)/103)**8>1:continue
                edge=max(abs(x)/105,abs(z-1)/102)
                if swamp:
                    m='water' if edge<.95 else 'mud'
                    self.put(x,-3,z,'mud');self.put(x,-2,z,m);self.put(x,-1,z,m)
                else:
                    self.box(x,-4,z,x,-1,z,'stone' if self.name not in ('phlegethon','styx') else 'terracotta')
                    self.put(x,0,z,'snow_block' if self.name=='cocytus' else 'red_sandstone' if self.name=='phlegethon' else 'grass_block')
        if not swamp:
            self.box(-8,0,55,8,0,101,self.trim)
            for z in range(60,101,10):
                self.lamp(-10,1,z);self.lamp(10,1,z)
            for x in (-48,48,-77,77):
                for z in (7,38,71):self.garden(x,z,self.name=='cocytus')
        self.arena(0 if not swamp else 4)
    def arena(self,y):
        self.arena_y=y
        self.disk(0,y-1,27,39,self.wall)
        self.disk(0,y,27,38,self.trim)
        self.disk(0,y,27,34,self.wall)
        for r in (12,25,32,37):self.disk(0,y,27,r,self.accent,r-0.8)
        for a in range(0,360,45):
            dx,dz=math.sin(math.radians(a)),math.cos(math.radians(a))
            self.line((round(dx*6),y,27+round(dz*6)),(round(dx*29),y,27+round(dz*29)),self.accent)
        self.disk(0,y,27,4,self.glass)
        self.put(0,y,27,'sea_lantern')
        for a in range(0,360,45):
            x,z=round(math.sin(math.radians(a))*19),27+round(math.cos(math.radians(a))*19)
            self.disk(x,y,z,2,self.accent)
        for a in range(0,360,30):
            if a in (0,180):continue
            x,z=round(math.sin(math.radians(a))*39),27+round(math.cos(math.radians(a))*39)
            self.lamp(x,y+1,z,4)
        # Entrances remain open on both ends of the processional axis.
        for z in (-13,67):
            for x in (-13,13):self.box(x,y,z,x,y+2,z,self.trim)
    def stairs(self,x,z,width,top,base=0,direction=-1):
        for step in range(top-base+1):
            zz=z+direction*step*2
            for dz in (0,direction):self.box(x-width,base,zz+dz,x+width,base+step,zz+dz,self.trim)
            self.box(x-width,base+step+1,zz,x+width,base+step+3,zz,'air')
            self.box(x-width,base+step+1,zz+direction,x+width,base+step+3,zz+direction,'air')
    def gable(self,x1,z1,x2,z2,y,material=None):
        material=material or self.roof
        mid=(x1+x2)//2
        for x in range(x1-2,x2+3):
            h=y+max(0,(x2-x1)//2-abs(x-mid))
            self.box(x,h,z1-2,x,h+1,z2+2,material)
            if x1<=x<=x2:
                for z in (z1,z2):self.box(x,y,z,x,h-1,z,self.wall)
        self.box(mid,y+(x2-x1)//2+2,z1-3,mid,y+(x2-x1)//2+2,z2+3,self.trim)
        # Small raised dormers, with actual hollow window pockets.
        for z in range(z1+7,z2-3,13):
            for sign in (-1,1):
                x=mid+sign*((x2-x1)//3);h=y+(x2-x1)//2-abs(x-mid)
                self.box(x-2,h-1,z-2,x+2,h+4,z+2,self.trim)
                self.box(x-1,h+1,z-1,x+1,h+3,z+1,'air')
                self.box(x+sign*2,h+1,z-1,x+sign*2,h+3,z+1,self.glass)
                for dz in range(-3,4):
                    self.box(x-3,h+7-abs(dz),z+dz,x+3,h+7-abs(dz),z+dz,material)
                self.put(x+sign*3,h+2,z,self.trim)
    def door(self,x,z,y,width=2,height=7):
        self.box(x-width,y+1,z-2,x+width,y+height,z+2,'air')
        for dx in range(-width-1,width+2):
            h=height+1-abs(dx)//2
            self.put(x+dx,y+h,z,self.trim)
    def room(self,x1,z1,x2,z2,floor=5,height=19,role='library',roof=True):
        self.rooms.append({'bounds':[x1,floor,z1,x2,floor+height,z2],'role':role})
        self.box(x1,floor-2,z1,x2,floor,z2,self.trim)
        self.box(x1,floor+1,z1,x2,floor+height,z2,self.wall)
        self.box(x1+2,floor+1,z1+2,x2-2,floor+height-1,z2-2,'air')
        self.box(x1+2,floor,z1+2,x2-2,floor,z2-2,self.wood)
        for y in (floor+1,floor+height-2,floor+height):
            self.box(x1-1,y,z1-1,x2+1,y,z1-1,self.trim)
            self.box(x1-1,y,z2+1,x2+1,y,z2+1,self.trim)
            self.box(x1-1,y,z1-1,x1-1,y,z2+1,self.trim)
            self.box(x2+1,y,z1-1,x2+1,y,z2+1,self.trim)
        for z in range(z1+5,z2-2,8):
            for x in (x1,x2):
                self.box(x,floor+5,z-1,x,floor+height-5,z+1,self.glass)
                self.box(x-1,floor+1,z+3,x+1,floor+height+1,z+3,self.trim)
                self.put(x,floor+height-4,z,self.trim)
        for x in range(x1+5,x2-2,8):
            for z in (z1,z2):self.box(x-1,floor+5,z,x+1,floor+height-5,z,self.glass)
        for x in (x1-2,x2+2):
            for z in range(z1+4,z2,9):
                self.box(x,floor+2,z,x,floor+6,z,self.trim)
                self.put(x,floor+7,z,self.light)
        self.door((x1+x2)//2,z2,floor)
        if roof:self.gable(x1,z1,x2,z2,floor+height+1)
        self.furnish(x1+3,z1+3,x2-3,z2-3,floor,role)
        self.facade_detail(x1,z2+2,x2,floor,height)
        # Deep entrance canopy with carved supports.
        cx=(x1+x2)//2
        for dx in (-4,4):self.box(cx+dx,floor+1,z2+3,cx+dx,floor+9,z2+3,self.trim)
        self.box(cx-5,floor+9,z2+1,cx+5,floor+10,z2+4,self.roof)
        self.door(cx,z2,floor)
        if height>=19 and x2-x1>=25 and z2-z1>=30:
            # Readable upper gallery with a real staircase from the ground floor.
            for sign in (-1,1):
                edge=x1+3 if sign<0 else x2-7
                self.box(edge,floor+11,z1+3,edge+4,floor+11,z2-3,self.wood)
                self.box(edge+(5 if sign<0 else -1),floor+12,z1+3,edge+(5 if sign<0 else -1),floor+12,z2-4,'dark_oak_fence')
            self.box(x1+3,floor+11,z1+3,x2-3,floor+11,z1+7,self.wood)
            # At the right edge, ascent proceeds north on two-block treads.
            for dy in range(12):
                zz=z2-5-dy*2
                self.box(x2-6,floor+dy,zz-1,x2-3,floor+dy,zz,self.wood)
                self.box(x2-6,floor+dy+1,zz-1,x2-3,floor+dy+3,zz,'air')
            for x in (x1+4,x2-4):
                for zz in range(z1+10,z2-8,9):self.put(x,floor+12,zz,'bookshelf')
    def furnish(self,x1,z1,x2,z2,y,role):
        cx=(x1+x2)//2
        self.box(cx-1,y+1,z1+2,cx+1,y+1,z2,self.carpet)
        for z in range(z1+2,z2-1,8):
            for x in (x1+2,x2-2):
                self.box(x-1,y+1,z-1,x+1,y+1,z+1,self.wood)
                self.box(x-1,y+2,z-1,x+1,y+2,z+1,'dark_oak_slab[type=top]')
                self.put(x,y+3,z,self.light)
                self.put(x,y+1,z+2,'spruce_stairs[facing=north]')
        for z in range(z1,z2-2,5):
            for x in (x1,x2):
                if role in ('library','observatory','chapel'):
                    self.box(x,y+1,z,x,y+3,z+2,'bookshelf')
                elif role=='forge':
                    self.put(x,y+1,z,'blast_furnace');self.put(x,y+1,z+1,'anvil')
                elif role=='barracks':
                    self.put(x,y+1,z,'red_bed[part=foot,facing=north]');self.put(x,y+1,z-1,'red_bed[part=head,facing=north]')
                else:
                    self.put(x,y+1,z,'barrel[facing=up]');self.put(x,y+2,z,'flower_pot')
        # Suspended chandeliers and ceiling beams, above the walkable room.
        for z in range(z1+4,z2-2,12):
            self.box(cx,y+8,z,cx,y+12,z,'chain[axis=y]')
            self.box(cx-3,y+8,z,cx+3,y+8,z,'dark_oak_fence')
            for dx in (-3,0,3):self.put(cx+dx,y+7,z,self.light+'[hanging=true]')
    def tower(self,cx,cz,r=8,height=62,floor=5,spire=True):
        self.towers.append((cx,cz,r,height,floor))
        for y in range(floor-2,floor+height+1):
            ring=range(-r,r+1)
            for dx in ring:
                for dz in ring:
                    d=max(abs(dx),abs(dz))+.43*min(abs(dx),abs(dz))
                    if d>r+1:continue
                    if y<=floor or d>=r-1:self.put(cx+dx,y,cz+dz,self.trim if y%12 in (0,1) else self.wall)
        # Slit windows, four faces, paired at each storey.
        for y in range(floor+9,floor+height-5,12):
            for sign in (-1,1):
                self.box(cx-1,y,cz+sign*r,cx+1,y+5,cz+sign*r,self.glass)
                self.box(cx+sign*r,y,cz-1,cx+sign*r,y+5,cz+1,self.glass)
        self.door(cx,cz+r,floor,1,5)
        # Continuous stair around the inner wall, one block rise per two treads.
        q=r-3;path=[]
        for x in range(-q,q):path.append((x,-q))
        for z in range(-q,q):path.append((q,z))
        for x in range(q,-q,-1):path.append((x,q))
        for z in range(q,-q,-1):path.append((-q,z))
        for i in range((height-4)*2):
            dx,dz=path[i%len(path)];yy=floor+i//2
            treads=[(cx+dx,cz+dz)]
            if abs(dx)==q:treads.append((cx+dx-(1 if dx>0 else -1),cz+dz))
            else:treads.append((cx+dx,cz+dz-(1 if dz>0 else -1)))
            for tx,tz in treads:
                self.put(tx,yy,tz,self.wood)
                self.box(tx,yy+1,tz,tx,yy+3,tz,'air')
        self.disk(cx,floor+height-3,cz,r-2,self.wood)
        # Stair exit cut through the observation deck.
        for i in range((height-9)*2,(height-4)*2):
            dx,dz=path[i%len(path)];self.box(cx+dx,floor+height-3,cz+dz,cx+dx,floor+height-1,cz+dz,'air')
        if spire:
            for dy in range(r*2+5):
                rr=max(0,r+2-dy//2)
                self.disk(cx,floor+height+dy,cz,rr,self.roof,max(0,rr-1.5))
            self.box(cx,floor+height+r*2+3,cz,cx,floor+height+r*2+7,cz,self.trim)
        else:
            for x in range(cx-r,cx+r+1,3):
                for z in (cz-r,cz+r):self.box(x,floor+height,z,x+1,floor+height+3,z,self.trim)
            for z in range(cz-r,cz+r+1,3):
                for x in (cx-r,cx+r):self.box(x,floor+height,z,x,floor+height+3,z+1,self.trim)
    def chest(self,x,y,z,label):
        self.put(x,y-1,z,self.wood)
        self.box(x,y,z,x,y+2,z,'air')
        self.put(x,y,z,'chest[facing=south,type=single,waterlogged=false]')
        # Three blocks facing the lid are always an accessible search pocket.
        self.box(x-1,y,z+1,x+1,y+2,z+3,'air')
        self.box(x-1,y-1,z+1,x+1,y-1,z+3,self.wood)
        self.chests.append({'x':x,'y':y,'z':z,'hint':label})
    def avenue(self):
        self.box(-7,0,-18,7,0,3,self.trim)
        self.stairs(0,-10,7,5)
        self.box(-7,5,-28,7,5,-21,self.trim)
    def chapel(self):
        self.base();self.avenue()
        self.room(-24,-87,24,-25,5,29,'chapel')
        self.box(-7,6,-83,7,6,-72,self.trim)
        self.box(-4,7,-81,4,8,-77,self.accent)
        if self.name=='acheron':
            self.box(-2,9,-80,2,9,-77,'crimson_planks')
            self.box(-3,9,-81,3,17,-81,self.trim)
            self.box(-1,10,-80,1,15,-80,'red_wool')
            for x in (-4,4):self.lamp(x,9,-79,2)
        else:
            self.box(-3,9,-80,3,10,-77,'blue_ice')
            self.put(0,11,-79,'sea_lantern')
            for x in range(-7,8,2):self.box(x,7,-84,x,18-abs(x),-84,'quartz_pillar[axis=y]')
        for sign in (-1,1):
            self.room(sign*53-12,-77,sign*53+12,-33,5,17,'library')
            self.box(min(sign*53,0)-3,5,-33,max(sign*53,0)+3,5,-27,self.trim)
            self.box(min(sign*53,0)-3,6,-33,max(sign*53,0)+3,10,-27,'air')
            self.stairs(sign*53,-21,5,5)
            self.tower(sign*33,-32,8,64 if sign>0 else 51)
            self.tower(sign*70,-77,6,39)
        # Tall west-front doorway, buttresses, clerestory and rose tracery.
        for sign in (-1,1):
            for z in range(-82,-29,10):
                self.box(sign*27,5,z,sign*27,26,z+2,self.trim)
                self.line((sign*27,26,z+1),(sign*34,8,z+1),self.trim,1)
        # Rose window lies in the vertical facade.
        for x in range(-9,10):
            for dy in range(-9,10):
                d=x*x+dy*dy
                if d<=81:self.put(x,43+dy,-23,self.trim if d>=60 or x==0 or dy==0 or abs(x)==abs(dy) else self.glass)
        for sign in (-1,1):
            for x in (11,22):
                self.box(sign*x-1,6,-23,sign*x+1,31,-22,self.trim)
                self.box(sign*x-2,6,-21,sign*x+2,10,-19,self.trim)
                self.box(sign*x,32,-22,sign*x,36,-22,self.roof)
            self.banner(sign*33-2,52,-21,'red' if self.name=='acheron' else 'blue')
        if self.name=='acheron':
            for sign in (-1,1):
                self.room(sign*76-8,55,sign*76+8,73,0,10,'barracks')
                self.box(min(0,sign*76),0,61,max(0,sign*76),0,66,self.trim)
        else:
            for sign in (-1,1):
                self.room(sign*72-8,54,sign*72+8,72,0,11,'chapel')
                self.box(min(0,sign*72),0,61,max(0,sign*72),0,66,self.trim)
            # Snow cap strips stay clear of paths and arena.
            for p,m in list(self.b.items()):
                x,y,z=p
                if y>10 and m==self.roof and (x,y+1,z) not in self.b and (x*31+z*17)%7<3:self.put(x,y+1,z,'snow')
        for x in (-18,18):
            for z in (-77,-43):self.chest(x,6,z,'Behind a reading desk / side altar')
        for x in (-53,53):self.chest(x+6,6,-65,'Wing archive recess')
        for x in (-72,72):self.chest(x,1,62,'Roadside chapel / guardroom cache')
    def forge(self):
        self.base();self.avenue()
        self.room(-34,-87,34,-28,5,25,'forge',False)
        self.box(-38,31,-91,38,33,-25,self.trim)
        for x in range(-32,33,8):
            self.box(x,34,-87,x+1,35,-29,'iron_bars')
        for sign in (-1,1):
            self.room(sign*64-13,-82,sign*64+13,-28,5,18,'forge',False)
            self.box(sign*64-15,24,-84,sign*64+15,25,-26,self.trim)
            self.stairs(sign*64,-16,6,5)
            self.box(min(0,sign*64)-3,5,-34,max(0,sign*64)+3,5,-28,self.trim)
            self.box(min(0,sign*64)-3,6,-34,max(0,sign*64)+3,11,-28,'air')
            for z,h in ((-72,63),(-40,47)):
                self.box(sign*85-4,0,z-4,sign*85+4,h,z+4,self.wall)
                for yy in range(8,h,10):self.box(sign*85-5,yy,z-5,sign*85+5,yy+1,z+5,self.trim)
                self.box(sign*85-2,h+1,z-2,sign*85+2,h+1,z+2,'soul_campfire[lit=true]')
        # Three monumental furnace mouths, lined with glass over contained lava.
        for x in (-22,0,22):
            self.box(x-7,5,-84,x+7,22,-77,self.trim)
            self.box(x-4,6,-76,x+4,16,-76,'orange_stained_glass')
            self.box(x-3,6,-78,x+3,14,-78,'lava')
            self.box(x-4,5,-79,x+4,5,-76,'magma_block')
            self.box(x-4,6,-75,x+4,7,-75,'iron_bars')
        # Deep open furnace portal and a layered service balcony on the facade.
        self.box(-7,6,-30,7,22,-24,'air')
        for x in (-9,9):self.box(x-1,5,-26,x+1,24,-23,self.trim)
        self.box(-10,23,-27,10,25,-23,'waxed_cut_copper')
        for x in (-75,-47,-20,20,47,75):self.box(x,5,-23,x+1,18,-22,self.trim)
        self.box(-78,18,-26,78,18,-21,self.trim)
        self.box(-78,19,-21,78,20,-21,'iron_bars')
        # Keep the central portal tall, while the balcony splits around it.
        self.box(-7,18,-27,7,22,-20,'air')
        for cx in (-23,23):
            for dx in range(-6,7):
                for dy in range(-6,7):
                    d=dx*dx+dy*dy
                    if 20<=d<=36:self.put(cx+dx,24+dy,-24,'waxed_cut_copper')
            self.box(cx-1,22,-24,cx+1,26,-24,self.trim)
        # Gantry cranes and hanging ore cages outside the combat circle.
        for sign in (-1,1):
            x=sign*65
            self.box(x-2,0,20,x+2,25,24,'stripped_dark_oak_log[axis=y]')
            self.box(min(x,x-sign*21),25,21,max(x,x-sign*21),27,23,self.trim)
            self.box(x-sign*20,9,22,x-sign*20,24,22,'chain[axis=y]')
            self.box(x-sign*20-3,6,19,x-sign*20+3,8,25,'iron_bars')
            self.room(x-9,65,x+9,82,0,10,'forge',False)
            self.box(min(0,x),0,69,max(0,x),0,75,self.trim)
        for x in (-27,27):
            for z in (-66,-42):self.chest(x,6,z,'Furnace service bay')
        for x in (-64,64):self.chest(x+7,6,-68,'Smelter supply alcove')
        for x in (-65,65):self.chest(x,1,74,'Crane operator workshop')
    def manor(self):
        self.base(True)
        self.box(-6,3,63,6,4,102,self.wood)
        for x in (-7,7):
            self.box(x,5,67,x,5,101,'dark_oak_fence')
        self.box(-42,3,-26,42,4,-11,self.wood)
        self.box(-35,3,-11,35,4,0,self.wood)
        self.room(-30,-85,30,-25,4,22,'manor')
        self.box(-3,5,-67,3,5,-46,'dark_oak_fence')
        self.box(-3,6,-67,3,6,-46,'dark_oak_slab[type=top]')
        for z in range(-65,-47,4):
            for x in (-5,5):self.put(x,5,z,'dark_oak_stairs[facing='+('east' if x<0 else 'west')+']')
            self.put(0,7,z,'soul_lantern')
        self.room(-77,-74,-36,-27,4,17,'library')
        self.room(36,-67,75,-27,4,27,'manor')
        self.tower(74,-70,8,69,4)
        self.box(-79,26,-76,-34,27,-25,self.wood)
        self.box(-77,28,-74,-36,29,-27,'dark_oak_fence')
        self.box(-72,28,-69,-41,29,-32,'air')
        # Timber framing, deep porch and external piles below every wing.
        for x in (-78,-60,-38,-29,-15,0,15,29,38,58,74):
            for z in (-84,-70,-49,-27):
                self.box(x,-3,z,x,6,z,'stripped_dark_oak_log[axis=y]')
        for x in range(-30,31,10):
            self.box(x,5,-23,x,22,-23,'stripped_dark_oak_log[axis=y]')
        self.box(-34,16,-23,34,17,-17,self.roof)
        for x in (-33,-22,22,33):self.box(x,4,-17,x,16,-17,'dark_oak_fence')
        self.line((-27,28,-23),(0,54,-23),'stripped_dark_oak_log[axis=y]')
        self.line((27,28,-23),(0,54,-23),'stripped_dark_oak_log[axis=y]')
        self.box(0,27,-23,0,52,-23,'stripped_dark_oak_log[axis=y]')
        for dx in range(-5,6):
            for dy in range(-5,6):
                if dx*dx+dy*dy<=25:self.put(dx,39+dy,-22,self.glass if dx and dy else self.trim)
        # Walkways to both wings, graveyard and boat-house, all above the water.
        for sign in (-1,1):
            self.box(min(0,sign*57)-4,3,-29,max(0,sign*57)+4,4,-17,self.wood)
            self.box(sign*57-3,5,-29,sign*57+3,10,-25,'air')
            self.box(sign*57-4,3,-17,sign*57+4,4,74,self.wood)
            self.box(min(0,sign*57),3,70,max(0,sign*57),4,76,self.wood)
            self.room(sign*69-9,55,sign*69+9,77,4,12,'manor')
            self.box(min(sign*57,sign*69),3,67,max(sign*57,sign*69),4,73,self.wood)
            self.box(min(sign*57,sign*69),5,69,max(sign*57,sign*69),10,71,'air')
            for z in range(-5,92,13):self.lamp(sign*57,5,z)
            for z in (4,26,48):
                self.box(sign*80-6,-1,z-6,sign*80+6,0,z+6,'mud_bricks')
                self.box(sign*80,1,z,sign*80,3,z,'mossy_stone_bricks')
                self.box(sign*80-1,3,z,sign*80+1,3,z,'mossy_stone_bricks')
        for x in (-23,23):
            for z in (-72,-41):self.chest(x,5,z,'Curtained manor study')
        for x in (-64,57):self.chest(x,5,-52,'Side wing cabinet')
        for x in (-69,69):self.chest(x,5,66,'Boat-house / gravekeeper lodge')
    def observatory(self):
        self.base();self.avenue()
        # Domed rotunda with a genuinely hollow interior and radial glazing.
        cx,cz=0,-57;r=29
        self.disk(cx,4,cz,r+4,self.trim);self.disk(cx,5,cz,r,self.wood)
        for y in range(6,33):
            self.disk(cx,y,cz,r,self.wall,r-2)
        for a in range(0,360,30):
            dx,dz=round(math.sin(math.radians(a))*r),round(math.cos(math.radians(a))*r)
            self.box(dx-1,6,cz+dz-1,dx+1,35,cz+dz+1,self.trim)
            wx,wz=round(math.sin(math.radians(a+15))*r),round(math.cos(math.radians(a+15))*r)
            self.box(wx-2,12,cz+wz-2,wx+2,26,cz+wz+2,self.glass)
        for dy in range(25):
            rr=round(r*math.sqrt(max(0,1-(dy/25)**2)))
            self.disk(0,34+dy,cz,rr,self.roof,max(0,rr-1.5))
            for a in range(0,360,30):
                self.put(round(math.sin(math.radians(a))*rr),34+dy,cz+round(math.cos(math.radians(a))*rr),'waxed_cut_copper')
        self.box(-2,58,cz-2,2,63,cz+2,'gold_block')
        self.door(0,-28,5,4,11)
        self.furnish(-20,-75,20,-38,5,'observatory')
        self.disk(0,27,cz,34,self.trim,28)
        self.disk(0,28,cz,34,'waxed_cut_copper',33)
        self.disk(0,5,cz,14,'blue_glazed_terracotta')
        for rr in (7,12):self.disk(0,5,cz,rr,'gold_block',rr-.7)
        for dx,dz in ((-4,3),(4,3),(0,-4)):self.line((dx,6,cz+dz),(0,11,cz),'iron_bars')
        self.line((0,11,cz+4),(0,17,cz-7),'waxed_cut_copper',1)
        self.box(-1,16,cz-9,1,18,cz-8,'tinted_glass')
        self.crystal(0,63,cz,15)
        for sign in (-1,1):
            self.room(sign*62-13,-78,sign*62+13,-32,5,19,'library')
            self.stairs(sign*62,-20,6,5)
            self.box(min(0,sign*62)-3,5,-33,max(0,sign*62)+3,5,-27,self.trim)
            self.box(min(0,sign*62)-3,6,-33,max(0,sign*62)+3,10,-27,'air')
            self.room(sign*74-8,57,sign*74+8,75,0,10,'observatory')
            self.box(min(0,sign*74),0,63,max(0,sign*74),0,69,self.trim)
            for z in (2,55):self.crystal(sign*51,1,z,15)
        for x in (-18,18):
            for z in (-68,-45):self.chest(x,6,z,'Rotunda research alcove')
        for x in (-62,62):self.chest(x+7,6,-67,'Restricted archive')
        for x in (-74,74):self.chest(x,1,66,'Garden instrument pavilion')
    def crystal(self,x,y,z,h):
        self.disk(x,y,z,5,self.trim)
        for dy in range(h):
            rr=max(0,3-(abs(dy-h//2)//3))
            self.disk(x,y+dy+1,z,rr,'amethyst_block' if dy%4 else 'purple_stained_glass')
        self.put(x,y+h//2,z,'sea_lantern')
    def gate(self):
        self.base();self.avenue()
        for sign in (-1,1):
            self.tower(sign*24,-38,10,67,5,False)
            self.tower(sign*80,-76,8,47,5,False)
            self.room(sign*58-17,-83,sign*58+17,-36,5,20,'barracks',False)
            self.box(sign*58-19,26,-85,sign*58+19,27,-34,self.trim)
            self.stairs(sign*58,-24,6,5)
            self.banner(sign*24-2,56,-27,'yellow')
            self.room(sign*72-9,60,sign*72+9,79,0,11,'barracks')
            self.box(min(0,sign*72),0,67,max(0,sign*72),0,73,self.trim)
            self.box(min(0,sign*58)-3,5,-39,max(0,sign*58)+3,5,-33,self.trim)
            self.box(min(0,sign*58)-3,6,-39,max(0,sign*58)+3,11,-33,'air')
        self.room(-14,-83,14,-48,5,21,'barracks',False)
        self.box(-6,5,-55,6,5,-26,self.trim)
        self.box(-4,6,-49,4,13,-26,'air')
        self.box(-24,30,-43,24,39,-34,self.wall)
        self.box(-17,31,-44,17,32,-33,'gold_block')
        self.box(-13,6,-43,13,22,-34,'air')
        # Monumental double axe, grounded on the gate roof.
        self.box(-1,39,-39,1,68,-37,'stripped_dark_oak_log[axis=y]')
        for sign in (-1,1):
            for dx in range(3,15):
                hh=9-abs(dx-10)//2
                self.box(min(sign*dx,sign*(dx+1)),56-hh//2,-40,max(sign*dx,sign*(dx+1)),56+hh//2,-36,'iron_block')
        for x in (-8,8):self.chest(x,6,-72,'Gate command room')
        for x in (-65,65):
            for z in (-72,-47):self.chest(x,6,z,'Garrison quartermaster recess')
        for x in (-72,72):self.chest(x,1,69,'Caravan guard lodge')
        for x in (-48,48):
            for z in (12,68):
                for dx in range(-6,7):
                    yy=9-abs(dx)
                    self.box(x+dx,yy,z-5,x+dx,yy,z+5,'white_wool')
                for dz in (-5,5):self.box(x,0,z+dz,x,8,z+dz,'acacia_fence')
                self.put(x+4,1,z+3,'barrel[facing=up]')
                self.put(x-4,1,z+3,self.light)

def build(name):
    b=Build(name)
    from sanctuary_design_v3 import author
    from sanctuary_polish_v5 import complete
    return complete(author(b))

def validate(b):
    assert len(b.chests)==8
    assert sum(m.startswith('chest[') for m in b.b.values())==8
    # Protect a full central combat cylinder, 60 blocks wide and 28 high.
    for x in range(-30,31):
        for z in range(-3,58):
            if x*x+(z-27)**2>30**2:continue
            assert (x,b.arena_y,z) in b.b,(b.name,'arena floor',x,z)
            for y in range(b.arena_y+1,b.arena_y+29):
                assert (x,y,z) not in b.b,(b.name,'arena obstruction',x,y,z)
    for chest in b.chests:
        x,y,z=[chest[k] for k in ('x','y','z')]
        assert (x,y+1,z) not in b.b
        assert (x,y,z+1) not in b.b
    return {'blocks':len(b.b),'chests':b.chests,'rooms':b.rooms,'design':b.design,'arena':{'x':0,'y':b.arena_y,'z':27,'clear_radius':30,'clear_height':28}}

def write_asset(b,folder):
    palette=sorted(set(b.b.values()));index={m:i for i,m in enumerate(palette)}
    folder.mkdir(parents=True,exist_ok=True)
    with gzip.open(folder/(b.name+'.ows'),'wb',compresslevel=9) as f:
        f.write(b'OWS1');f.write(struct.pack('>H',len(palette)))
        for m in palette:
            s=('minecraft:'+m).encode();f.write(struct.pack('>H',len(s)));f.write(s)
        f.write(struct.pack('>I',len(b.b)))
        for (x,y,z),m in sorted(b.b.items()):f.write(struct.pack('>hhhH',x,y,z,index[m]))
        f.write(struct.pack('>H',len(b.chests)))
        for c in b.chests:f.write(struct.pack('>hhh',c['x'],c['y'],c['z']))

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--only',choices=IDS);args=p.parse_args()
    PREVIEWS.mkdir(parents=True,exist_ok=True)
    report={}
    for name in ([args.only] if args.only else IDS):
        b=build(name);report[name]=validate(b);write_asset(b,ASSETS)
        (PREVIEWS/(name+'-design.json')).write_text(json.dumps(report[name],indent=2),encoding='utf8')
        print(name,len(b.b),'blocks, 8 hidden chests, combat cylinder checked',flush=True)
    if not args.only:(PREVIEWS/'manifest.json').write_text(json.dumps(report,indent=2),encoding='utf8')

if __name__=='__main__':main()
