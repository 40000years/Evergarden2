"""Six independent plans, organized interiors, and a deliberately inexpensive palette.

Chest coordinates are an immutable save-game contract. Furnishings occupy wall
bays; the main aisles and the combat cylinder are explicit reserved space.
"""
import math
from collections import Counter

CHESTS = {
    'acheron': [(-18,6,-77),(-18,6,-43),(18,6,-77),(18,6,-43),(-47,6,-65),(59,6,-65),(-72,1,62),(72,1,62)],
    'cocytus': [(-18,6,-77),(-18,6,-43),(18,6,-77),(18,6,-43),(-47,6,-65),(59,6,-65),(-72,1,62),(72,1,62)],
    'phlegethon': [(-27,6,-66),(-27,6,-42),(27,6,-66),(27,6,-42),(-57,6,-68),(71,6,-68),(-65,1,74),(65,1,74)],
    'banshee': [(-23,5,-72),(-23,5,-41),(23,5,-72),(23,5,-41),(-64,5,-52),(57,5,-52),(-69,5,66),(69,5,66)],
    'malefeasance': [(-18,6,-68),(-18,6,-45),(18,6,-68),(18,6,-45),(-55,6,-67),(69,6,-67),(-74,1,66),(74,1,66)],
    'styx': [(-8,6,-72),(8,6,-72),(-65,6,-72),(-65,6,-47),(65,6,-72),(65,6,-47),(-72,1,69),(72,1,69)],
}

FORBIDDEN = {'gold_block','iron_block','diamond_block','emerald_block','netherite_block',
    'lapis_block','redstone_block','coal_block','copper_block','raw_gold_block',
    'raw_iron_block','raw_copper_block','ancient_debris','beacon','anvil',
    'chipped_anvil','damaged_anvil','hopper','blast_furnace','enchanting_table',
    'brewing_stand','cauldron','amethyst_block','budding_amethyst','sea_lantern',
    'end_rod','quartz_block','quartz_bricks','smooth_quartz','quartz_pillar',
    'dark_prismarine','blue_ice'}

def affordable(m):
    base=m.split('[')[0]
    return not (base in FORBIDDEN or base.endswith('_ore') or 'copper' in base)

class Architect:
    def __init__(self,b):
        self.b=b; self.y=4 if b.name=='banshee' else 0
        b.routes=[]; b.design={}
        # Every material in the final geometry is audited, including utilities.
        b.wall={'acheron':'deepslate_bricks','phlegethon':'polished_blackstone_bricks',
            'cocytus':'white_concrete','banshee':'dark_oak_planks',
            'malefeasance':'calcite','styx':'deepslate_bricks'}[b.name]
        b.trim={'acheron':'polished_blackstone_bricks','phlegethon':'polished_basalt',
            'cocytus':'polished_diorite','banshee':'stripped_dark_oak_log',
            'malefeasance':'polished_diorite','styx':'chiseled_polished_blackstone'}[b.name]
        b.roof={'acheron':'deepslate_tiles','phlegethon':'brown_terracotta',
            'cocytus':'blue_concrete','banshee':'green_terracotta',
            'malefeasance':'cyan_terracotta','styx':'polished_blackstone_bricks'}[b.name]
        b.accent={'acheron':'red_nether_bricks','phlegethon':'orange_terracotta',
            'cocytus':'light_blue_concrete','banshee':'cyan_terracotta',
            'malefeasance':'purple_terracotta','styx':'yellow_terracotta'}[b.name]
        self.floor={'acheron':'polished_andesite','phlegethon':'smooth_stone',
            'cocytus':'polished_diorite','banshee':'spruce_planks',
            'malefeasance':'white_concrete','styx':'smooth_sandstone'}[b.name]

    def ground(self):
        b=self.b;swamp=b.name=='banshee'
        for x in range(-103,104):
            for z in range(-98,103):
                if (x/105)**8+((z-1)/103)**8>1:continue
                if swamp:
                    b.put(x,-3,z,'mud');b.box(x,-2,z,x,-1,z,'water')
                else:
                    b.box(x,-4,z,x,-1,z,'terracotta' if b.name in ('phlegethon','styx') else 'stone')
                    b.put(x,0,z,'snow_block' if b.name=='cocytus' else 'red_sandstone' if b.name=='phlegethon' else 'grass_block')
        self.path(-7,61,7,101,self.y)
        b.arena_y=self.y
        # Each arena reads as part of its own architecture, not a repeated plaza.
        if b.name in ('acheron','phlegethon','banshee','styx'):
            half=36 if b.name!='phlegethon' else 38
            self.path(-half,-9,half,63,self.y)
            for x in range(-half,half+1):
                for z in range(-9,64):
                    if b.name=='banshee':
                        m='dark_oak_planks' if x%12==0 or (z+9)%12==0 else 'spruce_planks'
                    elif b.name=='phlegethon':
                        m=b.accent if x in (-35,35) or z in (-6,60) else self.floor
                    else:
                        d=max(abs(x),abs(z-27))
                        m=b.accent if d in (12,30,35) else self.floor
                    b.put(x,self.y,z,m)
        else:
            b.disk(0,self.y-1,27,39,b.trim);b.disk(0,self.y,27,38,self.floor)
            for r in (6,29,35,38):b.disk(0,self.y,27,r,b.accent,r-.7)
        # A low, flat motif does not obstruct the fight.
        if b.name!='banshee':
            for a in range(0,360,45):
                dx,dz=math.sin(math.radians(a)),math.cos(math.radians(a))
                b.line((round(dx*7),self.y,27+round(dz*7)),(round(dx*22),self.y,27+round(dz*22)),b.accent)
        for x in (-42,42):
            for z in (0,54):b.lamp(x,self.y+1,z,3)
        for x in (-10,10):
            for z in (77,97):b.lamp(x,self.y+1,z,3)

    def path(self,x1,z1,x2,z2,y=0):
        b=self.b;b.box(x1,y-1,z1,x2,y,z2,b.wood if b.name=='banshee' else self.floor)
        if b.name=='banshee':
            for x in (x1,x2):
                for z in range(z1,z2+1,9):b.box(x,-2,z,x,y-1,z,'dark_oak_log[axis=y]')

    def entry(self,cx,z,floor=5,width=5):
        b=self.b;b.stairs(cx,z+floor*2+2,width,floor,0)
        self.path(cx-width,z-2,cx+width,z+2,floor)
        b.routes.append((cx,floor+1,z-4))

    def arch(self,cx,z,y,width=5,height=12,depth=2):
        b=self.b
        for dx in range(-width-2,width+3):
            h=height-max(0,abs(dx)-1)*2//3
            if abs(dx)>width:b.box(cx+dx,y,z,cx+dx,y+h,z+depth,b.trim)
            else:
                b.box(cx+dx,y+1,z,cx+dx,y+h,z+depth,'air')
                b.box(cx+dx,y+h+1,z,cx+dx,y+h+2,z+depth,b.trim)

    def shell(self,x1,z1,x2,z2,f=5,h=19,role='archive',roof='gable'):
        b=self.b
        b.rooms.append({'bounds':[x1,f,z1,x2,f+h,z2],'role':role,'gallery':False})
        b.box(x1,f-3,z1,x2,f,z2,b.trim)
        b.box(x1,f+1,z1,x2,f+h,z2,b.wall)
        b.box(x1+2,f+1,z1+2,x2-2,f+h-1,z2-2,'air')
        b.box(x1+2,f,z1+2,x2-2,f,z2-2,b.wood)
        cx=(x1+x2)//2
        # Broad aisle, a narrow border, and a quiet floor pattern.
        b.box(cx-2,f+1,z1+5,cx+2,f+1,z2,b.carpet)
        for x in (x1+3,x2-3):b.box(x,f,z1+3,x,f,z2-3,b.accent)
        for yy in (f,f+h-1,f+h+1):
            b.box(x1-1,yy,z1-1,x2+1,yy,z1,b.trim);b.box(x1-1,yy,z2,x2+1,yy,z2+1,b.trim)
            b.box(x1-1,yy,z1,x1,yy,z2,b.trim);b.box(x2,yy,z1,x2+1,yy,z2,b.trim)
        # Articulated structural bays: glazing inset between masonry piers.
        for z in range(z1+6,z2-3,10):
            for x in (x1,x2):
                b.box(x if x==x1 else x-1,f+5,z-2,x+1 if x==x1 else x,f+h-5,z+2,b.glass)
                inner=x+2 if x==x1 else x-2
                for dz in (-3,3):b.box(inner,f+1,z+dz,inner,f+h-3,z+dz,b.trim)
                b.box(inner,f+4,z-3,inner,f+4,z+3,b.trim)
                b.box(inner,f+h-4,z-3,inner,f+h-4,z+3,b.trim)
                for dy in range(3):
                    b.put(inner,f+h-5+dy,z-2+dy,b.trim)
                    b.put(inner,f+h-5+dy,z+2-dy,b.trim)
                b.box(x-1,f+1,z+4,x+1,f+h+3,z+4,b.trim)
                b.put(x,f+h-4,z,b.trim)
                if b.name in ('acheron','cocytus'):
                    outer=x-4 if x==x1 else x+4
                    b.box(outer,f,z+4,outer,f+7,z+4,b.trim)
                    b.line((outer,f+8,z+4),(x,f+h-1,z+4),b.trim)
        for x in range(x1+6,x2-3,10):
            for z in (z1,z2):b.box(x-2,f+5,z if z==z1 else z-1,x+2,f+h-5,z+1 if z==z1 else z,b.glass)
        for x in (x1,x2):
            for z in (z1,z2):b.box(x-1,f,z-1,x+1,f+h+4,z+1,b.trim)
        if roof=='gable':b.gable(x1,z1,x2,z2,f+h+2)
        elif roof=='hip':
            for dy in range(min((x2-x1)//2,(z2-z1)//2)+3):
                xx1,xx2=x1-2+dy,x2+2-dy;zz1,zz2=z1-2+dy,z2+2-dy
                if xx1>xx2 or zz1>zz2:break
                b.box(xx1,f+h+2+dy,zz1,xx2,f+h+2+dy,zz2,b.roof)
        elif roof=='flat':
            b.box(x1-2,f+h+2,z1-2,x2+2,f+h+3,z2+2,b.roof)
            for x in range(x1,x2+1,4):
                for z in (z1,z2):b.box(x,f+h+4,z,x+1,f+h+6,z,b.trim)
        self.arch(cx,z2-1,f, min(4,(x2-x1)//4),min(h-3,12),3)
        self.furniture(x1,z1,x2,z2,f,h,role)
        b.routes.append((cx,f+1,z2-5))

    def desk(self,x,y,z,w=3):
        b=self.b
        for dx in (-w,w):b.put(x+dx,y+1,z,'spruce_fence')
        b.box(x-w,y+2,z-1,x+w,y+2,z+1,'spruce_slab[type=top]')
        b.put(x-w,y+3,z,'flower_pot');b.put(x+w,y+3,z,b.light)
        b.put(x,y+1,z+3,'spruce_stairs[facing=north]')

    def furniture(self,x1,z1,x2,z2,f,h,role):
        b=self.b;cx=(x1+x2)//2
        # Wall bays only. Nothing projects into the central five-block aisle.
        for z in range(z1+8,z2-9,12):
            for side in (-1,1):
                x=x1+4 if side<0 else x2-4
                if role in ('archive','study','observatory'):
                    b.box(x,f+1,z-2,x,f+3,z+2,'bookshelf')
                    b.box(x,f+4,z-2,x,f+4,z+2,'spruce_slab[type=bottom]')
                    xx=x+side*-4
                    if abs(xx-cx)>=9:self.desk(xx,f,z,2)
                elif role in ('dormitory','guardroom'):
                    color='blue' if b.name=='cocytus' else 'red'
                    b.put(x,f+1,z,color+'_bed[part=head,facing=north]')
                    b.put(x,f+1,z+1,color+'_bed[part=foot,facing=north]')
                    b.put(x,f+1,z-2,b.wood);b.put(x,f+2,z-2,b.light)
                elif role=='workshop':
                    b.box(x-1,f+1,z-2,x+1,f+1,z+2,'smooth_stone')
                    b.put(x,f+2,z-1,'stonecutter');b.put(x,f+2,z+1,'furnace[facing=south]')
                elif role in ('refectory','dining'):
                    if abs(x-cx)>9:self.desk(x-side*3,f,z,3)
                else:
                    # A bench, wall niche and discreet lamp, not miscellaneous piles.
                    b.box(x,f+1,z-2,x,f+1,z+2,'spruce_slab[type=bottom]')
                    b.put(x,f+3,z,b.light)
        # Roof trusses/chandeliers stay well above players and sight lines.
        for z in range(z1+8,z2-6,14):
            b.box(x1+1,f+h-2,z,x2-1,f+h-2,z,b.wood)
            b.box(cx,f+h-7,z,cx,f+h-2,z,'iron_chain[axis=y]')
            b.box(cx-3,f+h-7,z,cx+3,f+h-7,z,'spruce_fence')
            for dx in (-3,3):b.put(cx+dx,f+h-8,z,b.light+'[hanging=true]')

    def gallery(self,x1,z1,x2,z2,f,h):
        b=self.b;gy=f+9
        # One rear mezzanine: furnished perimeter, open central double-height hall.
        b.box(x1+2,gy,z1+2,x2-2,gy,z1+9,b.wood)
        b.box(x1+2,gy+1,z1+10,x2-10,gy+1,z1+10,'spruce_fence')
        # Straight, generous three-wide staircase on the right-hand side.
        for dy in range(10):
            zz=z1+28-dy*2
            b.box(x2-7,f+dy,zz-1,x2-4,f+dy,zz,b.wood)
            b.box(x2-7,f+dy+1,zz-1,x2-4,f+dy+3,zz,'air')
        b.box(x2-7,gy,z1+3,x2-4,gy,z1+10,b.wood)
        b.box(x2-7,gy+1,z1+3,x2-4,gy+3,z1+10,'air')
        self.desk(x1+13,gy,z1+5,3)
        b.box(x1+3,gy+1,z1+2,x1+3,gy+3,z1+7,'bookshelf')
        b.routes.append((x1+5,gy+1,z1+5))
        b.rooms[-1]['gallery']=True

    def connector(self,x1,z1,x2,z2,f=5):
        b=self.b;self.path(x1,z1,x2,z2,f)
        b.box(x1,f+1,z1,x2,f+5,z2,'air')

    def hedge(self,x1,z1,x2,z2,y=0):
        b=self.b;b.box(x1,y,z1,x2,y,z2,b.trim)
        b.box(x1,y+1,z1,x2,y+2,z2,'azalea_leaves[persistent=true]')

    def acheron(self):
        b=self.b;self.ground()
        # An irregular fortified castle, dominated by a tall western keep.
        self.shell(-25,-89,27,-27,5,29,'throne')
        self.gallery(-25,-89,27,-27,5,29)
        self.shell(-79,-83,-34,-39,5,24,'guardroom',roof='flat')
        self.shell(39,-84,75,-51,5,18,'archive')
        self.connector(-43,-46,-20,-39);self.connector(24,-58,44,-51)
        self.entry(0,-27);self.entry(-56,-39);self.entry(57,-51)
        b.tower(-77,-81,9,79,5,False);b.tower(-34,-31,8,58,5)
        b.tower(31,-32,7,70,5);b.tower(77,-81,6,44,5)
        # A raised throne dais and rose window form one clear interior focal point.
        b.box(-7,6,-84,7,6,-79,b.trim);b.box(-3,7,-83,3,8,-80,b.accent)
        b.box(-2,9,-84,2,15,-84,'red_wool')
        for dx in range(-9,10):
            for dy in range(-9,10):
                d=dx*dx+dy*dy
                if d<=81:b.put(dx,45+dy,-25,b.trim if d>=63 or dx==0 or dy==0 or abs(dx)==abs(dy) else b.glass)
        for x in (-33,31):b.banner(x-1,51,-23,'red')
        for x in (-11,11):
            for z in (-62,-48,-35):
                b.box(x-4,6,z-3,x+4,6,z+3,'red_carpet')
                b.box(x-3,6,z-1,x+3,6,z-1,'dark_oak_stairs[facing=north]')
        # Stone window arches and hanging tapestries frame the nave.
        for z in (-71,-51,-31):
            for sign in (-1,1):
                x=sign*19
                b.box(x,6,z,x,25,z,b.trim)
                b.box(x+sign,15,z-3,x+sign,22,z-1,'red_wool')
        # Curtained approach: walls and towers belong to this fortress alone.
        for x in (-92,92):
            b.box(x-1,0,-90,x+1,8,82,b.wall)
            for z in range(-90,83,6):b.box(x-2,9,z,x+2,11,z+2,b.trim)
        for x1,x2 in ((-92,-12),(12,92)):b.box(x1,0,83,x2,8,85,b.wall)
        for x in (-87,87):b.tower(x,79,6,27,0,False)
        for x in (-72,72):
            self.shell(x-10,52,x+10,75,0,11,'guardroom',roof='flat')
            self.path(min(x,0),67,max(x,0),73)
        for x in (-51,51):
            for z in (6,38):self.hedge(x-3,z-7,x+3,z+7)
        for x,z in ((-78,-8),(-77,26),(77,2),(78,37),(-55,78),(56,80)):
            b.tree(x,z,18)
            for dx in (-7,6):
                for dz in (-4,4):
                    b.put(x+dx,1,z+dz,'rose_bush[half=lower]');b.put(x+dx,2,z+dz,'rose_bush[half=upper]')
        for x in (-16,16):
            b.box(x-2,0,81,x+2,21,87,b.trim)
            b.box(x-3,21,80,x+3,22,88,b.accent)
            b.lamp(x,23,84,2)
        for x1,x2 in ((-90,-20),(20,90)):
            for x in range(x1,x2,6):b.box(x,9,82,x+2,11,86,b.trim)
        b.design={'identity':'Asymmetric blood fortress / western keep, throne hall, eastern archive', 'interior_focus':'Throne dais, rear mezzanine, wall-side guard bays', 'cutaway':[-25,-89,27,-27,5,34]}

    def phlegethon(self):
        b=self.b;self.ground()
        # A single long industrial building; sawtooth roof and clustered stacks.
        self.shell(-79,-87,79,-29,5,24,'workshop',roof=None)
        for x1 in range(-80,80,20):
            for dx in range(21):
                yy=31+dx//2
                b.box(x1+dx,yy,-89,x1+dx,yy+1,-27,b.roof)
            b.box(x1+19,32,-86,x1+19,39,-30,b.glass)
            for z in range(-86,-29,9):b.box(x1+19,32,z,x1+19,40,z,b.trim)
        # Distinct service zones, separated by wide cross aisles and structural columns.
        for x in (-42,42):
            b.box(x,5,-85,x,28,-34,b.trim)
            for z in (-68,-46):b.box(x-1,6,z-4,x+1,19,z+4,'air')
        for cx in (-21,0,21):
            b.box(cx-6,5,-85,cx+6,20,-79,b.trim)
            self.arch(cx,-78,5,3,10,0)
            b.box(cx-2,6,-81,cx+2,14,-81,'orange_stained_glass')
            b.box(cx-2,6,-82,cx+2,13,-82,'lava')
            b.box(cx-3,5,-80,cx+3,5,-77,'magma_block')
        for x,z,h in ((-91,-79,69),(-91,-56,54),(-77,-93,45)):
            b.box(x-3,0,z-3,x+3,h,z+3,b.wall)
            for yy in range(8,h,9):b.box(x-4,yy,z-4,x+4,yy+1,z+4,b.trim)
            b.put(x,h+1,z,'campfire[lit=true]')
        self.entry(0,-29,5,7)
        # Open casting yard; benches/crane stay outside the arena.
        for x in (-65,65):
            self.shell(x-10,64,x+10,84,0,11,'workshop',roof='flat')
            self.path(min(0,x),70,max(0,x),78)
            self.path(x-4,-23,x+4,68)
        for x in (-62,):
            for z in (1,27):
                b.box(x-2,0,z,x+2,27,z+3,'stripped_dark_oak_log[axis=y]')
                b.box(x,27,z,x+18,29,z+3,b.trim)
                b.box(x+17,12,z+1,x+17,26,z+1,'iron_chain[axis=y]')
        for x in (53,72):
            b.box(x,1,9,x+7,2,39,'smooth_stone')
            for z in (13,23,33):b.box(x+2,3,z,x+5,4,z+3,'gray_terracotta')
        b.design={'identity':'Long sawtooth foundry / clustered western chimneys, open casting yard','interior_focus':'Three furnace mouths, separate machine bays, broad cross aisles','cutaway':[-79,-87,79,-29,5,29]}

    def cocytus(self):
        b=self.b;self.ground()
        # An L-shaped abbey, with a tall offset belfry and low cloister garden.
        self.shell(-25,-89,25,-28,5,28,'chapel')
        self.gallery(-25,-89,25,-28,5,28)
        self.shell(-79,-83,-36,-49,5,15,'dormitory',roof='hip')
        self.shell(36,-88,79,-36,5,17,'refectory',roof='hip')
        self.connector(-42,-60,-22,-53);self.connector(22,-58,42,-51)
        self.entry(0,-28);self.entry(-58,-49);self.entry(58,-36)
        b.tower(-79,-30,8,74,0);b.tower(31,-80,5,43,5)
        # Front transept and a stepped organ altar, with ordered pews.
        b.box(-7,6,-84,7,7,-79,b.trim)
        for x in range(-7,8,2):b.box(x,8,-85,x,19-abs(x),-85,'polished_diorite')
        for z in (-65,-52,-39):
            for x1,x2 in ((-15,-7),(7,15)):
                b.box(x1,6,z,x2,6,z,'spruce_stairs[facing=north]')
        # Garden arcades only on the east. The west is an exposed snow bank.
        for z in range(-14,56,10):
            for x in (56,76):b.box(x,0,z,x,9,z,b.trim)
            b.box(56,10,z,76,11,z,b.roof)
        self.path(55,-18,77,57)
        for x in (-72,72):
            self.shell(x-10,53,x+10,76,0,12,'chapel')
            self.path(min(x,0),67,max(x,0),73)
        for x,z in ((-59,11),(-77,34),(-57,48),(89,11)):
            b.tree(x,z,18)
        for p,m in list(b.b.items()):
            x,y,z=p
            if m==b.roof and y>14 and (x,y+1,z) not in b.b and (x*3+z)%5<2:b.put(x,y+1,z,'snow')
        b.design={'identity':'L-shaped snow abbey / offset bell tower, low refectory, eastern garden arcade','interior_focus':'Organ altar, ordered pews, quiet dormitory, dining hall','cutaway':[-25,-89,25,-28,5,33]}

    def banshee(self):
        b=self.b;self.ground()
        # A connected, rambling timber manor with staggered roof heights.
        self.shell(-30,-87,30,-26,4,23,'dining',roof='hip')
        self.gallery(-30,-87,30,-26,4,23)
        self.shell(-80,-76,-39,-30,4,18,'archive',roof='hip')
        self.shell(39,-78,78,-37,4,30,'study')
        self.connector(-45,-42,-25,-35,4);self.connector(25,-52,45,-45,4)
        self.path(-37,-28,37,-11,4);self.path(-9,-14,9,2,4)
        b.tower(75,-78,7,60,4)
        # Timber framing and large, deep verandas rather than stone castle walls.
        for x in (-29,-15,0,15,29):b.box(x,5,-24,x,25,-24,'stripped_dark_oak_log[axis=y]')
        b.box(-34,17,-25,34,18,-17,b.roof)
        for x in (-34,-22,22,34):b.box(x,4,-17,x,17,-17,'dark_oak_log[axis=y]')
        self.desk(0,4,-65,5);self.desk(0,4,-47,5)
        for x in (-58,58):
            self.path(x-4,-33,x+4,75,4)
            self.path(min(0,x),69,max(0,x),76,4)
            self.shell(x-20,53,x+20,80,4,12,'guardroom' if x<0 else 'workshop',roof='hip')
            for z in range(-20,55,11):
                for xx in (x-5,x+5):b.put(xx,5,z,'dark_oak_fence')
                b.lamp(x+6,5,z,2)
        # The arena is a large timber ritual deck, skirted by mooring piles.
        for x in (-39,39):
            for z in range(-9,64,12):b.box(x,-3,z,x,6,z,'dark_oak_log[axis=y]')
        for x,z in ((-86,10),(-77,33),(82,14),(88,43)):
            b.tree(x,z,21)
        self.connector(-79,69,79,75,4)
        b.design={'identity':'Stilt manor / staggered roofs, connected verandas, boathouses, ritual deck','interior_focus':'Dining salons, archive bays, rear library mezzanine','cutaway':[-30,-87,30,-26,4,27]}

    def malefeasance(self):
        b=self.b;self.ground()
        # A true round rotunda, not a gabled hall with a dome added on top.
        cx,cz,r=0,-59,32
        for y in range(2,32):
            b.disk(cx,y,cz,r,b.trim if y in (3,5,29,30,31) else b.wall,0 if y<=5 else r-2)
        b.disk(0,5,cz,r-2,b.wood)
        for a in range(0,360,30):
            xx,zz=round(math.sin(math.radians(a))*31),cz+round(math.cos(math.radians(a))*31)
            b.box(xx-1,6,zz-1,xx+1,32,zz+1,b.trim)
            if a not in (0,180):
                b.box(xx-2,13,zz,xx+2,23,zz,b.glass)
        for dy in range(24):
            rr=round(33*math.sqrt(max(0,1-(dy/24)**2)))
            b.disk(0,32+dy,cz,rr,b.roof,max(0,rr-1.5))
            if dy in (0,4,9,14,19):b.disk(0,32+dy,cz,rr,b.trim,max(0,rr-.7))
        for a in range(0,360,45):
            for dy in range(24):
                rr=round(33*math.sqrt(max(0,1-(dy/24)**2)))
                b.put(round(math.sin(math.radians(a))*rr),32+dy,cz+round(math.cos(math.radians(a))*rr),b.trim)
        b.box(-2,55,cz-2,2,60,cz+2,b.trim);b.put(0,61,cz,b.light)
        self.arch(0,-29,5,5,16,3);self.entry(0,-27)
        # Central astronomical instrument on a low, legible stepped plinth.
        b.disk(0,6,cz,7,b.trim);b.disk(0,7,cz,5,b.accent,3.8)
        for a in range(360):
            t=math.radians(a)
            b.put(round(math.cos(t)*5),14+round(math.sin(t)*5),cz,b.wood)
            b.put(0,14+round(math.sin(t)*5),cz+round(math.cos(t)*5),b.wood)
        b.box(0,8,cz,0,13,cz,b.trim)
        # Reading desks in four perimeter quadrants, broad radial aisles.
        for x in (-19,19):
            for z in (-72,-46):self.desk(x,5,z,3)
        self.shell(-79,-85,-40,-46,5,19,'archive')
        self.shell(40,-83,80,-51,5,14,'study',roof='hip')
        self.connector(-45,-61,-24,-54);self.connector(24,-65,45,-58)
        self.entry(-59,-46);self.entry(60,-51)
        for x in (-74,74):
            self.shell(x-10,54,x+10,79,0,10,'observatory',roof='hip')
            self.path(min(0,x),68,max(0,x),75)
        # Split garden terraces, pergolas and reflecting pools.
        for x in (-59,59):
            self.path(x-10,-6,x+10,51)
            for z in (4,29,48):
                self.hedge(x-9,z,x-6,z+7);self.hedge(x+6,z,x+9,z+7)
            b.box(x-3,0,11,x+3,0,32,'water')
            for z in (-3,42):
                for xx in (x-11,x+11):b.box(xx,0,z,xx,9,z,'birch_fence')
                b.box(x-12,10,z,x+12,10,z+1,'birch_slab[type=bottom]')
        b.design={'identity':'Round observatory / ribbed turquoise dome, radial salon, split water gardens','interior_focus':'Armillary instrument, four reading quadrants, separate archive pavilion','cutaway':[-32,-91,32,-27,5,31]}

    def styx(self):
        b=self.b;self.ground()
        # Broad triumphal gate, two massive pylons, and low asymmetric caravan yards.
        self.shell(-20,-88,20,-47,5,24,'guardroom',roof='flat')
        self.shell(-81,-86,-48,-29,5,15,'dormitory',roof='flat')
        self.shell(48,-86,81,-34,5,19,'archive',roof='flat')
        self.connector(-54,-59,-15,-52);self.connector(15,-59,54,-52)
        self.entry(0,-47);self.entry(-64,-29);self.entry(64,-34)
        for x in (-32,32):
            b.tower(x,-42,10,68,0,False)
            for xx in (x-5,x+5):b.box(xx,12,-31,xx,52,-31,b.accent)
            b.banner(x-2,52,-29,'yellow')
        b.box(-32,38,-46,32,42,-39,b.trim)
        self.arch(0,-44,5,9,27,5)
        # Monumental axe uses gray stone and yellow clay, never solid iron/gold.
        b.box(-1,42,-44,1,75,-42,'stripped_dark_oak_log[axis=y]')
        for sign in (-1,1):
            for dx in range(3,16):
                hh=11-abs(dx-10)//2
                b.box(min(sign*dx,sign*(dx+1)),61-hh//2,-45,max(sign*dx,sign*(dx+1)),61+hh//2,-41,'polished_andesite')
        for x in (-72,72):
            self.shell(x-10,55,x+10,80,0,11,'guardroom',roof='flat')
            self.path(min(0,x),69,max(0,x),76)
        # Long western tent market; eastern open garden has obelisks instead.
        for z in (0,25,50):
            x=-66
            for dx in range(-10,11):b.box(x+dx,13-abs(dx),z-7,x+dx,13-abs(dx),z+7,'white_wool')
            for dz in (-7,7):b.box(x,0,z+dz,x,12,z+dz,'acacia_fence')
            self.desk(x,0,z,3)
        for x,z in ((56,5),(76,22),(57,43)):
            b.box(x-2,0,z-2,x+2,2,z+2,b.trim)
            b.box(x-1,3,z-1,x+1,12,z+1,b.wall);b.put(x,13,z,b.accent)
        b.design={'identity':'Triumphal sun gate / twin pylons, western tent market, eastern obelisk court','interior_focus':'Gate command room, organized bunk bays, eastern records hall','cutaway':[-81,-86,-48,-29,5,20]}

    def finish(self):
        b=self.b
        # Immutable chest locations preserve saved inventory and the original seed order.
        for x,y,z in CHESTS[b.name]:
            # Cabinet recesses have a small frame; no random boxes in traffic lanes.
            b.box(x-2,y-1,z-1,x+2,y+3,z,b.wood)
            b.box(x-1,y,z,x+1,y+2,z,'air')
            b.chest(x,y,z,'Recessed cabinet in room wall / side pavilion')
        for p,m in list(b.b.items()):
            # Shared utility banner() has a historical gold tip.
            if m=='gold_block':b.b[p]='yellow_terracotta'
        counts=Counter(m.split('[')[0] for m in b.b.values())
        denied={m:n for m,n in counts.items() if not affordable(m)}
        assert not denied,(b.name,'valuable construction material',denied)
        b.design['materials']=dict(counts.most_common())
        b.design['expensive_resource_blocks']=denied
        b.design['routes']=b.routes

    def enrich(self):
        """Site-specific secondary scale; nothing is scattered across room aisles."""
        b=self.b;n=b.name
        if n=='acheron':
            # Rose parterres, grave court, and paved approaches between castle courts.
            for sign in (-1,1):
                self.path(sign*58-4,-32,sign*58+4,52)
                for z in (-9,17,43):
                    x=sign*77
                    b.box(x-7,0,z-6,x+7,0,z+6,b.trim)
                    b.box(x-6,1,z-5,x+6,1,z+5,'coarse_dirt')
                    for dx in (-4,0,4):
                        for dz in (-3,3):
                            b.put(x+dx,2,z+dz,'rose_bush[half=lower]');b.put(x+dx,3,z+dz,'rose_bush[half=upper]')
                for z in (3,29):
                    x=sign*47
                    b.box(x-2,0,z-4,x+2,0,z+4,b.trim)
                    b.box(x,1,z-3,x,4,z-3,'chiseled_stone_bricks')
            # Layered pointed portal, not a flat hole in a facade.
            self.arch(0,-25,5,5,17,3)
            for x in (-8,8):b.box(x-1,5,-24,x+1,27,-22,b.trim)
            for dx in range(-9,10):b.put(dx,32-abs(dx),-22,b.accent)
        elif n=='phlegethon':
            # Open service galleries and a covered casting trench along each edge.
            for sign in (-1,1):
                for z in (-2,25,50):
                    x=sign*88
                    b.box(x-5,0,z-9,x+5,0,z+9,b.trim)
                    b.box(x-2,1,z-7,x+2,1,z+7,'magma_block')
                    b.box(x-2,2,z-7,x+2,2,z+7,'orange_stained_glass')
                    for dz in (-10,10):b.box(x-6,1,z+dz,x+6,3,z+dz,b.trim)
                for yy in (14,22):b.box(-76,yy,-27,76,yy,-24,b.trim)
            for x in (-65,-42,42,65):
                b.box(x-1,5,-26,x+1,25,-24,b.trim)
                b.box(x-3,24,-28,x+3,25,-22,b.roof)
            # Emblem over the entry, a basalt gear with an orange clay hub.
            for dx in range(-7,8):
                for dy in range(-7,8):
                    d=dx*dx+dy*dy
                    if 25<=d<=49:b.put(dx,26+dy,-23,b.trim)
            b.box(-1,24,-23,1,28,-23,b.accent)
            for x,z in ((-97,-16),(96,13),(-97,48),(96,65)):
                for dy in range(8):b.disk(x,dy,z,max(1,7-dy), 'orange_terracotta' if dy%3 else 'red_terracotta')
        elif n=='cocytus':
            # Blue rose window and a proper snowy approach to the abbey.
            for dx in range(-8,9):
                for dy in range(-8,9):
                    d=dx*dx+dy*dy
                    if d<=64:b.put(dx,44+dy,-26,b.trim if d>=49 or dx==0 or dy==0 or abs(dx)==abs(dy) else b.glass)
            for x in (-10,10):b.box(x-1,5,-27,x+1,30,-24,b.trim)
            for sign in (-1,1):
                for z in (8,35):
                    x=sign*85
                    for dy in range(4):b.disk(x,dy,z,10-dy*2,'snow_block')
                self.path(sign*46-3,-21,sign*46+3,53)
            # Frozen reflecting pool, framed with low stone seats.
            b.box(-76,0,3,-56,0,37,b.trim);b.box(-74,0,5,-58,0,35,'ice')
            for z in (6,33):b.box(-72,1,z,-60,1,z,'spruce_slab[type=bottom]')
        elif n=='banshee':
            # Cross bracing and porches read as timber construction at close range.
            for x in (-30,30):
                for z in range(-80,-29,12):
                    outer=x-2 if x<0 else x+2
                    b.box(outer,4,z,outer,19,z,'dark_oak_log[axis=y]')
                    b.line((outer,7,z),(outer,16,z+8),'stripped_dark_oak_log[axis=y]')
                    b.line((outer,16,z),(outer,7,z+8),'stripped_dark_oak_log[axis=y]')
            for sign in (-1,1):
                x=sign*36
                self.path(x-3,-77,x+3,-28,4)
                for z in range(-76,-28,7):
                    b.box(x,5,z,x,13,z,'dark_oak_fence')
                    b.put(x,12,z,b.light+'[hanging=true]')
                b.box(x-4,14,-80,x+4,15,-27,b.roof)
            for r in (10,25,33):b.disk(0,4,27,r,b.accent,r-.6)
            for a in range(0,360,90):
                t=math.radians(a);b.line((round(math.sin(t)*6),4,27+round(math.cos(t)*6)),(round(math.sin(t)*22),4,27+round(math.cos(t)*22)),b.accent)
        elif n=='malefeasance':
            # A classical portico wraps the rotunda's entrance.
            for x in (-15,-9,9,15):
                b.box(x-1,5,-28,x+1,7,-24,b.trim)
                b.box(x,8,-26,x,23,-26,'white_concrete')
                b.box(x-1,24,-27,x+1,25,-25,b.trim)
            b.box(-17,26,-29,17,27,-23,b.trim)
            for dx in range(-17,18):b.box(dx,28,-28,dx,28+(17-abs(dx))//2,-24,b.roof)
            for sign in (-1,1):
                for z in (-3,40):
                    x=sign*84;b.tree(x,z,19)
                for z in (10,35):
                    x=sign*45
                    b.box(x-2,0,z-3,x+2,0,z+3,b.trim)
                    for dz in (-2,0,2):b.put(x,1,z+dz,'allium')
            # Ordered book stacks along the round rear wall, not inside radial aisles.
            for x in (-12,-6,6,12):
                zz=-59-round(math.sqrt(28*28-x*x))
                b.box(x-1,6,zz,x+1,8,zz,'bookshelf')
        else:
            # Layered gate facade, marching yellow pilasters and sun medallion.
            for sign in (-1,1):
                for x in (55,65,75):
                    b.box(sign*x,6,-32,sign*x,17,-32,b.accent)
                self.path(sign*88-3,-26,sign*88+3,54)
                for z in (4,28,48):
                    x=sign*91
                    b.box(x-3,0,z-3,x+3,1,z+3,b.trim)
                    b.box(x,2,z,x,7,z,'acacia_log[axis=y]')
                    for dy in range(2):b.disk(x,8+dy,z,6-dy*2,'acacia_leaves[persistent=true]')
            for dx in range(-8,9):
                for dy in range(-8,9):
                    d=dx*dx+dy*dy
                    if 40<=d<=64:b.put(dx,31+dy,-38,b.accent)
            for x in (-12,12):b.box(x-1,0,84,x+1,15,88,b.trim)
            b.box(-13,15,84,13,17,88,b.trim)
            b.box(-11,16,85,11,16,85,b.accent)

def author(b):
    a=Architect(b);getattr(a,b.name)();a.enrich();a.finish();return b
