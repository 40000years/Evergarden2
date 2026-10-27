"""Occupied, layered interiors and reference-specific exterior corrections.

Artwork is permanent block mosaic, not disposable entities. Rare blocks are
measured accents with individual limits, never bulk structural material.
"""
import math
from collections import Counter
from sanctuary_design_v3 import CHESTS, affordable

LIMITS={'gold_block':4,'iron_block':8,'waxed_cut_copper':12,'anvil':2,
    'blast_furnace':4,'cauldron':2,'brewing_stand':1,'enchanting_table':1,
    'quartz_block':24,'quartz_pillar':24,'smooth_quartz':24,'quartz_bricks':24,
    'amethyst_block':12,'sea_lantern':8,'end_rod':6,'dark_prismarine':24,'blue_ice':8}

class Interior:
    def __init__(self,b):
        self.b=b;b.routes=[];b.occupied=[];self.sinks=0
        self.wood=b.wood;self.panel='stripped_spruce_log' if b.name=='cocytus' else 'stripped_dark_oak_log'
        self.plaster={'acheron':'gray_terracotta','phlegethon':'gray_concrete','cocytus':'white_terracotta',
            'banshee':'brown_terracotta','malefeasance':'white_concrete','styx':'yellow_terracotta'}[b.name]

    def plant(self,x,f,z,large=False):
        b=self.b
        if large:
            b.put(x,f+1,z,'terracotta');b.put(x,f+2,z,'azalea_leaves[persistent=true]')
            b.put(x,f+3,z,'flowering_azalea_leaves[persistent=true]')
        else:b.put(x,f+1,z,'potted_fern' if b.name in ('banshee','acheron') else 'potted_blue_orchid' if b.name=='cocytus' else 'potted_allium')

    def artwork(self,x,f,z,w=5,motif='landscape'):
        b=self.b
        # Recessed timber frame and a readable coloured scene, facing south.
        b.box(x-w//2-1,f+3,z,x+w//2+1,f+8,z,self.wood)
        for dx in range(-w//2+1,w//2+1):
            for dy in range(4):
                color='light_blue_terracotta' if dy>=2 else 'green_terracotta'
                if motif=='moon':color='white_terracotta' if (dx+1)**2+(dy-2)**2<=2 else 'blue_terracotta'
                elif motif=='sun':color='yellow_terracotta' if dx*dx+(dy-2)**2<=2 else 'orange_terracotta'
                elif motif=='portrait':color='black_terracotta' if abs(dx)>1 else 'red_terracotta' if dy<2 else 'brown_terracotta'
                elif dy<2 and abs(dx)<=dy:color='brown_terracotta'
                b.put(x+dx,f+4+dy,z+1,color)

    def chair(self,x,f,z,facing='north'):
        b=self.b;b.put(x,f+1,z,'spruce_stairs[facing='+facing+']')
        for dx in (-1,1):b.put(x+dx,f+1,z,'spruce_trapdoor[open=true,facing='+('west' if dx<0 else 'east')+']')

    def table(self,x,f,z,rx=2,rz=1):
        b=self.b
        for dx in (-rx,rx):
            for dz in (-rz,rz):b.put(x+dx,f+1,z+dz,'spruce_fence')
        b.box(x-rx,f+2,z-rz,x+rx,f+2,z+rz,'dark_oak_slab[type=top]')
        b.put(x-rx,f+3,z,'white_candle[candles=2,lit=true]')
        b.put(x+rx,f+3,z,'flower_pot')

    def hearth(self,x,f,z):
        b=self.b
        b.box(x-3,f+1,z,x+3,f+7,z+1,'stone_bricks')
        b.box(x-1,f+1,z+1,x+1,f+3,z+1,'air')
        b.box(x-1,f+1,z,x+1,f+1,z,'campfire[lit=true]')
        b.box(x-3,f+5,z-1,x+3,f+5,z+2,'stone_brick_slab[type=top]')
        for dx in (-2,2):b.put(x+dx,f+6,z+2,'flower_pot')
        b.box(x-1,f+6,z,x+1,f+8,z,'bricks')

    def cabinet(self,x,f,z,w=3):
        b=self.b;b.box(x-w,f+1,z,x+w,f+4,z,self.wood)
        for dx in range(-w+1,w,2):
            b.box(x+dx,f+2,z+1,x+dx,f+3,z+1,'spruce_trapdoor[open=true,facing=north]')
        b.box(x-w,f+5,z,x+w,f+5,z,'spruce_slab[type=bottom]')
        self.plant(x-w,f+5,z)

    def rug(self,x1,z1,x2,z2,f):
        b=self.b
        b.box(x1,f+1,z1,x2,f+1,z2,b.carpet)
        for x in range(x1,x2+1,2):
            for z in (z1,z2):b.put(x,f+1,z,'gray_carpet' if b.name=='acheron' else 'white_carpet')

    def cell(self,x1,z1,x2,z2,f,role,upper=False):
        """A room with deliberate wall furnishings and a clear approach to its door."""
        b=self.b;cx=(x1+x2)//2;cz=(z1+z2)//2
        if x2-x1<7 or z2-z1<7:return
        b.occupied.append({'role':role,'bounds':[x1,f,z1,x2,f+8,z2],'level':'upper' if upper else 'ground'})
        self.rug(x1+2,z1+3,x2-2,z2-3,f)
        motif='portrait' if b.name=='acheron' else 'moon' if b.name in ('cocytus','malefeasance','banshee') else 'sun'
        if role in ('kitchen','pantry'):
            b.box(x1+1,f+1,z1+1,x2-1,f+1,z1+2,'bricks')
            for x in (x1+2,x2-2):b.put(x,f+2,z1+1,'furnace[facing=south]')
            b.put(cx,f+2,z1+1,'water_cauldron[level=3]' if self.sinks<2 else 'light_blue_stained_glass')
            self.sinks+=1
            b.box(x1+1,f+5,z1+1,x2-1,f+5,z1+2,'spruce_slab[type=top]')
            for x in (x1+2,cx,x2-2):b.put(x,f+6,z1+1,'flower_pot')
            self.table(cx,f,cz+1,2,1)
            b.put(x1+1,f+1,cz,'composter');b.put(x1+1,f+1,cz+2,'hay_block')
        elif role in ('bedchamber','infirmary','guestroom','bunks'):
            for x in (x1+2,x2-2):
                color='blue' if b.name=='cocytus' else 'cyan' if b.name=='banshee' else 'red'
                b.put(x,f+1,z1+3,color+'_bed[part=head,facing=north]')
                b.put(x,f+1,z1+4,color+'_bed[part=foot,facing=north]')
                b.box(x-1,f+2,z1+2,x+1,f+2,z1+2,self.wood)
                b.put(x-1,f+3,z1+2,b.light)
            self.cabinet(cx,f,z1+1,2)
            self.artwork(cx,f,z2-1,3,motif)
            self.plant(x2-1,f,z2-1,True)
        elif role in ('library','archive','laboratory','cartography','study'):
            for x in (x1+1,x2-1):
                b.box(x,f+1,z1+1,x,f+4,z2-3,'bookshelf')
                b.box(x,f+5,z1+1,x,f+5,z2-3,'spruce_slab[type=bottom]')
            self.table(cx,f,cz,2,1);self.chair(cx,f,cz+3)
            b.put(cx,f+3,cz,'lectern[facing=south,has_book=false]')
            self.artwork(cx,f,z1+1,3,motif)
            self.plant(x1+2,f,z2-2,True)
        elif role in ('workshop','control','toolroom'):
            self.cabinet(cx,f,z1+1,max(2,(x2-x1)//2-2))
            b.box(x1+1,f+1,cz-2,x1+2,f+1,cz+2,'smooth_stone')
            b.put(x1+1,f+2,cz-1,'stonecutter');b.put(x1+1,f+2,cz+1,'grindstone[face=floor,facing=south]')
            self.table(cx,f,cz+2,2,1)
            self.artwork(cx,f,z2-1,3,'sun')
        elif role in ('dining','council'):
            self.table(cx,f,cz,2,min(3,(z2-z1)//2-3))
            for z in (cz-2,cz+2):
                self.chair(cx-4,f,z,'east');self.chair(cx+4,f,z,'west')
            self.cabinet(cx,f,z1+1,max(2,(x2-x1)//2-2))
            self.artwork(cx,f,z1,3,motif)
            self.plant(x2-1,f,z2-1,True)
        elif role in ('chapel','relics','music'):
            b.box(cx-2,f+1,z1+2,cx+2,f+1,z1+3,b.trim)
            b.box(cx-1,f+2,z1+2,cx+1,f+2,z1+3,b.accent)
            b.put(cx,f+3,z1+2,'white_candle[candles=3,lit=true]')
            self.artwork(cx,f,z1,3,motif)
            for z in (cz,cz+3):b.box(x1+2,f+1,z,x2-2,f+1,z,'spruce_stairs[facing=north]')
            self.plant(x1+1,f,z2-1,True)
        else:
            self.hearth(cx,f,z1+1)
            for x in (x1+2,x2-2):
                b.box(x,f+1,cz-1,x,f+1,cz+2,'spruce_stairs[facing='+('east' if x<cx else 'west')+']')
            self.table(cx,f,cz+1,1,1)
            self.artwork(cx,f,z2-1,3,motif)
            self.plant(x1+1,f,z2-1,True);self.plant(x2-1,f,z2-1,True)
        # Ceiling border and a suspended light give each room a finished upper edge.
        for x in (x1,x2):b.box(x,f+8,z1,x,f+8,z2,self.panel)
        for z in (z1,z2):b.box(x1,f+8,z,x2,f+8,z,self.panel)
        b.put(cx,f+7,cz,b.light+'[hanging=true]');b.put(cx,f+8,cz,'iron_chain[axis=y]')

    def door_x(self,x,f,z):
        b=self.b;b.box(x-1,f+1,z-1,x+1,f+5,z+1,'air')
        for dz in (-2,2):b.box(x,f+1,z+dz,x,f+6,z+dz,self.panel)
        b.box(x,f+6,z-2,x,f+6,z+2,self.panel)

    def suite(self,room,index):
        b=self.b;x1,f,z1,x2,top,z2=room['bounds'];cx=(x1+x2)//2;h=top-f
        if x2-x1<27 or z2-z1<27:
            # Small roadside buildings become complete workshops/lodges.
            b.box(x1+3,f+1,z1+3,x2-3,min(top-1,f+8),z2-3,'air')
            role='workshop' if b.name=='phlegethon' else 'sitting'
            self.cell(x1+3,z1+3,x2-3,z2-3,f,role)
            b.box(cx-2,f+1,z2-7,cx+2,f+4,z2+1,'air')
            b.routes.append((cx,f+1,z2-4));return
        # Replace loose furnishings with an integrated two-sided plan.
        b.box(x1+3,f+1,z1+3,x2-3,top-2,z2-3,'air')
        b.box(x1+3,f,z1+3,x2-3,f,z2-3,self.wood)
        b.box(cx-2,f+1,z1+4,cx+2,f+1,z2-3,b.carpet)
        start=z1+12 if h>=19 else z1+4;end=z2-4
        number=3 if end-start>=39 else 2
        edges=[start+round((end-start)*i/number) for i in range(number+1)]
        levels=[f,f+9] if h>=19 else [f]
        roles={
            'acheron':['council','study','sitting','bedchamber','archive','relics','guestroom','cartography','music','library','infirmary','dining'],
            'phlegethon':['toolroom','workshop','control','pantry','dining','archive','guestroom','study'],
            'cocytus':['chapel','music','infirmary','guestroom','library','dining','study','relics'],
            'banshee':['kitchen','dining','sitting','bedchamber','music','archive','guestroom','study'],
            'malefeasance':['laboratory','cartography','library','study','archive','relics','guestroom','council'],
            'styx':['council','toolroom','bunks','cartography','dining','archive','guestroom','study']}[b.name]
        k=index*3
        for level in levels:
            upper=level>f
            for sign in (-1,1):
                inner=cx-8 if sign<0 else cx+8
                left,right=(x1+3,inner) if sign<0 else (inner,x2-3)
                if upper:
                    b.box(left,level,start-1,right,level,end+1,self.wood)
                    rail=inner+1 if sign<0 else inner-1
                    b.box(rail,level+1,start,rail,level+1,end,'spruce_fence')
                    # A continuous gallery lane sits just outside the chambers.
                    b.box(min(inner,cx+sign*5),level,z1+3,max(inner,cx+sign*5),level,z2-3,self.wood)
                for j in range(number):
                    a,c=edges[j],edges[j+1];mid=(a+c)//2
                    b.box(inner,level+1,a,inner,level+8,c,self.plaster)
                    for zz in (a,c):b.box(left,level+1,zz,right,level+8,zz,self.plaster)
                    divisions=max(1,(right-left)//23)
                    cuts=[left+round((right-left)*q/divisions) for q in range(divisions+1)]
                    for q in range(divisions):
                        self.cell(cuts[q]+1,a+1,cuts[q+1]-1,c-1,level,roles[k%len(roles)],upper)
                        k+=1
                        if q:
                            b.box(cuts[q],level+1,a,cuts[q],level+8,c,self.plaster)
                            self.door_x(cuts[q],level,mid)
                            b.box(cuts[q]-3,level+1,mid-1,cuts[q]+3,level+3,mid+1,'air')
                    self.door_x(inner,level,mid)
                    b.box(min(inner,inner+sign*4),level+1,mid-1,max(inner,inner+sign*4),level+3,mid+1,'air')
                    b.routes.append((inner+sign*3,level+1,mid))
        # Connect upper galleries across the rear and retain a generous stairwell.
        if len(levels)==2:
            gy=f+9;b.box(x1+3,gy,z1+3,x2-3,gy,start-1,self.wood)
            b.box(x1+3,gy+1,start-1,cx-5,gy+1,start-1,'spruce_fence')
            b.box(cx+5,gy+1,start-1,x2-3,gy+1,start-1,'spruce_fence')
            for a,c in ((cx-8,cx-5),(cx+5,cx+8)):
                b.box(a,gy+1,start-1,c,gy+3,start-1,'air')
            b.box(x2-9,f+1,z1+6,x2-3,gy+4,z1+31,'air')
            for dy in range(10):
                zz=z1+28-dy*2
                b.box(x2-7,f+dy,zz-1,x2-4,f+dy,zz,self.wood)
                b.box(x2-7,f+dy+1,zz-1,x2-4,f+dy+3,zz,'air')
            b.box(x2-7,gy,z1+3,x2-4,gy,z1+9,self.wood)
            b.box(x1+3,gy+1,z1+3,x2-3,gy+4,z1+7,'air')
            b.routes=[p for p in b.routes if not(p[1]==gy+1 and x2-9<=p[0]<=x2-3 and z1+6<=p[2]<=z1+31)]
            b.routes.append((cx,gy+1,z1+5))
        # Central hall has structural rhythm, rugs, framed end-wall artwork,
        # high vault ribs and chandeliers; side rooms provide human scale.
        for z in range(z1+15,z2-4,13):
            for x in (cx-6,cx+6):
                b.box(x,f+1,z,x,min(top-3,f+19),z,b.trim)
                self.plant(x,f,z+2)
            yy=min(top-4,f+19)
            for dx in range(-6,7):b.put(cx+dx,yy+3-abs(dx)//2,z,b.trim)
            b.box(cx,yy-4,z,cx,yy+3,z,'iron_chain[axis=y]')
            b.box(cx-2,yy-4,z,cx+2,yy-4,z,'spruce_fence')
            for dx in (-2,2):b.put(cx+dx,yy-5,z,b.light+'[hanging=true]')
        self.artwork(cx,f+9 if h>=22 else f,z1+2,7,'portrait' if b.name=='acheron' else 'moon')
        b.routes.append((cx,f+1,z2-5))

    def rotunda(self):
        b=self.b
        # Curved perimeter library, four themed reading rooms, radial aisles.
        for a in range(0,360,12):
            if a%90<=18 or a%90>=72:continue
            t=math.radians(a);x,z=round(math.sin(t)*27),-59+round(math.cos(t)*27)
            b.box(x,6,z,x,10,z,'bookshelf');b.put(x,11,z,'birch_slab[type=bottom]')
        for sx in (-1,1):
            for sz in (-1,1):
                x,z=sx*17,-59+sz*17
                self.cell(x-5,z-5,x+5,z+5,5,'cartography' if sx==sz else 'laboratory')
                # Freestanding display partitions create alcoves without sealing radial paths.
                b.box(x-5,6,z-5,x+5,13,z-5,self.plaster)
                self.artwork(x,5,z-5,5,'moon')
                self.plant(x+5,5,z+5,True)
        # An inner ring balcony with a visible stair along the western curve.
        b.disk(0,17,-59,29,self.wood,23)
        for a in range(0,360,5):
            t=math.radians(a);b.put(round(math.sin(t)*23),18,-59+round(math.cos(t)*23),'birch_fence')
        for dy in range(13):
            zz=-39-dy*2
            b.box(-28,5+dy,zz-1,-25,5+dy,zz,self.wood)
            b.box(-28,6+dy,zz-1,-25,8+dy,zz,'air')
        b.box(-28,17,-65,-24,17,-62,self.wood)
        for a in (40,130,220,310):
            t=math.radians(a);self.plant(round(math.sin(t)*26),17,-59+round(math.cos(t)*26),True)
        b.routes.append((-26,18,-64))

    def foundry(self):
        b=self.b
        b.box(-76,6,-84,76,28,-32,'air')
        b.box(-76,5,-84,76,5,-32,'smooth_stone')
        b.box(-3,6,-76,3,6,-30,'orange_carpet')
        # Enclosed support rooms occupy one side only. Most of the hall is a
        # double-height industrial workshop, not another residential suite plan.
        for f in (5,16):
            if f==16:b.box(-76,f,-84,-40,f,-32,b.wood)
            for j,(a,c) in enumerate(((-82,-67),(-65,-50),(-48,-33))):
                b.box(-43,f+1,a,-43,f+8,c,self.plaster)
                for z in (a,c):b.box(-76,f+1,z,-43,f+8,z,self.plaster)
                role=('toolroom','workshop','dining')[j] if f==5 else ('control','archive','study')[j]
                self.cell(-74,a+1,-47,c-1,f,role,f>5)
                mid=(a+c)//2;self.door_x(-43,f,mid)
                b.box(-49,f+1,mid-1,-40,f+3,mid+1,'air')
                b.routes.append((-48,f+1,mid))
        b.box(-40,17,-84,-40,17,-32,'dark_oak_fence')
        for dy in range(12):
            zz=-35-dy*2
            b.box(-46,5+dy,zz-1,-43,5+dy,zz,b.wood)
            b.box(-46,6+dy,zz-1,-43,8+dy,zz,'air')
        b.routes.append((-45,17,-75))
        for cx in (-26,0,26):
            b.box(cx-6,5,-85,cx+6,13,-78,b.trim)
            b.box(cx-3,6,-79,cx+3,11,-78,'orange_stained_glass')
            b.box(cx-2,6,-82,cx+2,10,-80,'lava')
            b.box(cx-4,13,-84,cx+4,14,-78,'bricks')
        for cx,cz in ((12,-64),(12,-42),(58,-56),(58,-40)):
            b.box(cx-5,5,cz-4,cx+5,6,cz+4,b.trim)
            for dx in (-4,4):b.box(cx+dx,7,cz-3,cx+dx,15,cz+3,'polished_basalt')
            b.box(cx-5,16,cz-4,cx+5,18,cz+4,'polished_blackstone_bricks')
            b.box(cx-2,7,cz-2,cx+2,9,cz+2,'orange_stained_glass')
            b.box(cx-3,7,cz+4,cx+3,8,cz+7,'smooth_stone')
            b.put(cx,9,cz+6,'stonecutter')
            b.occupied.append({'role':'casting press','bounds':[cx-6,5,cz-5,cx+6,20,cz+8],'level':'ground'})
        for z in (-61,-41):
            b.box(5,23,z,72,25,z+2,b.trim)
            for x in (5,72):b.box(x,5,z,x,22,z+2,'polished_basalt')
            b.box(32,14,z+1,32,22,z+1,'iron_chain[axis=y]')
            b.box(30,13,z,34,13,z+2,'dark_oak_fence')
        for x in (-31,31):
            for z in (-66,-42):
                self.cabinet(x,5,z-3,3)
        b.routes.append((0,6,-34))

    def exterior(self):
        b=self.b;n=b.name
        if n=='acheron':
            # Dark parquet with red inlay; crimson wood is a restrained accent.
            for p,m in list(b.b.items()):
                x,y,z=p
                if m=='crimson_planks' and y in (5,14) and -24<=x<=26 and -88<=z<=-28:
                    b.b[p]='dark_oak_planks' if (x+z)%9 else 'polished_blackstone_bricks'
            # Gothic skyline: buttress pinnacles, red tracery, recessed facade galleries.
            for x in (-25,27):
                for z in (-82,-62,-42):
                    b.box(x,23,z,x,42,z,b.trim)
                    for dy in range(6):b.disk(x,43+dy,z,max(0,3-dy//2),b.roof)
            for x in (-65,-51,-37):
                b.box(x,31,-38,x,38,-38,b.trim);b.put(x,39,-38,'red_stained_glass')
            b.box(-6,7,-85,6,7,-80,b.trim)
            b.box(-2,8,-84,2,10,-82,'red_wool');b.box(-2,11,-85,2,17,-85,b.accent)
            for x in (-2,2):b.put(x,12,-84,'gold_block')
        elif n=='phlegethon':
            # Reference is an open foundry fortress: terraces, roof gantries and vent towers.
            b.box(-82,30,-91,82,46,-25,'air')
            for x1,x2,h in ((-78,-44,29),(-39,39,35),(44,78,26)):
                b.box(x1, h,-85,x2,h+1,-29,b.trim)
                for x in range(x1,x2+1,6):
                    for z in (-85,-29):b.box(x,h+2,z,x+2,h+4,z,b.wall)
            for x in (-65,65):
                b.tower(x,-68,6,43 if x<0 else 53,5,False)
                b.box(x-2,49 if x<0 else 59,-70,x+2,49 if x<0 else 59,-66,'soul_campfire[lit=true]')
            for x in (-31,-10,10,31):
                b.box(x,36,-83,x,43,-33,'polished_blackstone_wall')
                b.box(x,44,-84,x,45,-30,b.trim)
            for x in (-8,8):b.put(x,7,-78,'blast_furnace[facing=south]')
            b.put(-12,7,-78,'anvil');b.put(12,7,-78,'anvil')
            for x in (-6,-3,0,3,6):b.put(x,27,-23,'waxed_cut_copper')
        elif n=='cocytus':
            b.box(-7,6,-85,7,7,-80,b.trim)
            for x in range(-7,8,2):b.box(x,8,-85,x,20-abs(x),-85,b.trim)
            for z in (-68,-55,-42):
                b.box(-5,6,z,-3,6,z,'spruce_stairs[facing=north]')
                b.box(3,6,z,5,6,z,'spruce_stairs[facing=north]')
            # Abbey cross-gable and classical white bell tower, not a second dark keep.
            b.box(-90,20,-41,-68,110,-19,'air')
            for yy in range(1,63):
                r=8 if yy<10 else 6
                b.box(-79-r,yy,-30-r,-79+r,yy,-30+r,b.trim if yy%12<2 else 'white_concrete')
                if yy>2:b.box(-79-r+2,yy,-30-r+2,-79+r-2,yy,-30+r-2,'air')
            for y in (44,53):
                for dx in (-6,6):b.box(-79+dx,y,-33,-79+dx,y+6,-27,'air')
                for dz in (-6,6):b.box(-82,y,-30+dz,-76,y+6,-30+dz,'air')
            for dy in range(9):b.disk(-79,63+dy,-30,max(0,9-dy),b.roof)
            b.box(-81,52,-32,-77,52,-28,'air');b.put(-79,53,-30,'gold_block')
            for x in (-12,12):
                for z in (-24,-21):
                    b.box(x,5,z,x,25,z,'white_concrete')
                    b.box(x,5,z,x,7,z,'quartz_pillar[axis=y]')
                    b.box(x,23,z,x,25,z,'quartz_pillar[axis=y]')
            for x in (-8,-4,0,4,8):b.put(x,8,-83,'blue_ice')
            for x in (-9,9):b.put(x,23,-83,'sea_lantern')
        elif n=='banshee':
            # Front-facing timber gable and a lighted lantern turret match the haunted manor.
            b.box(-30,28,-25,30,56,-17,'air')
            for dx in range(-30,31):
                hh=28+(30-abs(dx))*2//3
                b.box(dx,28,-24,dx,hh,-24,b.wall)
                b.box(dx,hh,-26,dx,hh+1,-17,b.roof)
            for dx in (-20,-10,0,10,20):b.box(dx,26,-23,dx,47-abs(dx)//2,-23,self.panel)
            for a in range(360):
                t=math.radians(a);b.put(round(math.cos(t)*6),39+round(math.sin(t)*6),-22,b.glass)
            for x in (70,78):
                for z in (-82,-74):b.box(x,57,z,x,66,z,'dark_oak_fence')
            b.box(69,67,-83,79,68,-73,b.roof)
            b.put(74,64,-78,'sea_lantern')
        elif n=='malefeasance':
            # Turquoise dome with sparse gold ribs and luminous purple instrument points.
            for dy in (4,10,16):
                rr=round(33*math.sqrt(1-(dy/24)**2));b.put(0,32+dy,-59+rr,'waxed_cut_copper')
            for x,z in ((-5,-59),(5,-59),(0,-64),(0,-54)):
                b.put(x,7,z,'amethyst_block');b.put(x,8,z,'end_rod')
            for x in (-2,2):b.put(x,55,-59,'gold_block')
            for x in (-15,-9,9,15):
                b.put(x,8,-26,'quartz_pillar[axis=y]');b.put(x,24,-26,'quartz_block')
        else:
            # Low stepped gate wings and square pylons make a military silhouette.
            for cx in (-32,32):
                for y in range(7,67):
                    for dx in range(-9,10):
                        for dz in range(-9,10):
                            if max(abs(dx),abs(dz))>=8:b.put(cx+dx,y,-42+dz,b.trim if y%12<2 else b.wall)
                for dx in (-6,6):b.box(cx+dx,12,-32,cx+dx,53,-32,'yellow_terracotta')
                b.put(cx,54,-32,'gold_block')
            for x in (-7,7):b.put(x,63,-40,'iron_block')
            b.box(-20,31,-88,20,33,-48,b.trim)
            for x in (-17,-8,8,17):b.box(x,34,-49,x+1,37,-49,b.trim)

    def finish(self):
        b=self.b
        # Reinstate the immutable treasure alcoves after partitions are finished.
        b.chests=[]
        for x,y,z in CHESTS[b.name]:b.chest(x,y,z,'Cabinet cache within furnished room / side pavilion')
        counts=Counter(m.split('[')[0] for m in b.b.values())
        # Water cauldrons count as the same mineable iron vessel.
        counts['cauldron']+=counts.pop('water_cauldron',0)
        rare={m:c for m,c in counts.items() if c and not affordable(m)}
        for m,c in rare.items():assert m in LIMITS and c<=LIMITS[m],(b.name,m,c,LIMITS.get(m,0))
        assert sum(rare.values())<=128,(b.name,'aggregate rare budget',rare)
        b.design.update({'occupied_rooms':b.occupied,'routes':b.routes,'materials':dict(counts.most_common()),
            'rare_materials':rare,'rare_limits':LIMITS,'rare_total':sum(rare.values()),
            'artwork':'Framed permanent block mosaics, landscapes, portraits, moon/sun scenes',
            'interior_focus':'Layered furnished suites, central nave, functional rooms, upper galleries and wall artwork'})
        b.design.pop('expensive_resource_blocks',None)

def complete(b,decorator=Interior):
    a=decorator(b)
    for i,room in enumerate(b.rooms):
        if b.name=='phlegethon' and i==0:a.foundry()
        else:a.suite(room,i)
    if b.name=='malefeasance':a.rotunda()
    a.exterior();a.finish();return b
