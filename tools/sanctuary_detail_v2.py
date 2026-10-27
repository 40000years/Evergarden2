"""Second-pass architecture: occupied rooms, distinct workshops and layered exteriors."""
import math

def table(b,x,y,z,length=7,material=None):
    wood=material or b.wood
    for zz in (z-length//2,z+length//2):
        for xx in (x-2,x+2):b.put(xx,y+1,zz,'dark_oak_fence')
    b.box(x-2,y+2,z-length//2,x+2,y+2,z+length//2,wood.replace('_planks','_slab')+'[type=top]' if wood.endswith('_planks') else wood)
    for zz in range(z-length//2,z+length//2+1,3):
        b.put(x,y+3,zz,b.light)
        b.put(x-4,y+1,zz,'dark_oak_stairs[facing=east]')
        b.put(x+4,y+1,zz,'dark_oak_stairs[facing=west]')

def shelves(b,x,y,z,length=7):
    for zz in range(z,z+length):
        b.box(x,y+1,zz,x,y+4,zz,'bookshelf' if zz%3 else b.wood)
    b.box(x,y+5,z,x,y+5,z+length-1,b.trim)

def kitchen(b,x,y,z):
    b.box(x-4,y+1,z,x+4,y+2,z,b.wood)
    for xx in (-3,0,3):
        b.put(x+xx,y+2,z,'smoker[facing=south]' if xx<0 else 'cauldron' if xx==0 else 'crafting_table')
        b.put(x+xx,y+3,z-1,'barrel[facing=south]')
    b.put(x-2,y+3,z+1,'cake');b.put(x+2,y+3,z+1,'flower_pot')
    b.box(x-4,y+5,z-1,x+4,y+5,z-1,'spruce_trapdoor[half=top]')
    b.put(x+4,y+1,z+2,'composter')

def bed(b,x,y,z,color):
    b.put(x,y+1,z,color+'_bed[part=foot,facing=north]')
    b.put(x,y+1,z-1,color+'_bed[part=head,facing=north]')
    b.put(x+1,y+1,z-1,'barrel[facing=up]');b.put(x+1,y+2,z-1,b.light)
    b.box(x-1,y+3,z-2,x+1,y+3,z-2,'spruce_trapdoor[open=true,facing=north]')

def chandelier(b,x,y,z,r=4):
    b.box(x,y+2,z,x,y+8,z,'iron_chain[axis=y]')
    b.disk(x,y,z,r,b.trim,r-1)
    for a in range(0,360,45):
        xx=x+round(math.sin(math.radians(a))*r);zz=z+round(math.cos(math.radians(a))*r)
        b.put(xx,y+1,zz,'gold_block' if b.name in ('cocytus','malefeasance','styx') else 'iron_bars')
        b.put(xx,y-1,zz,b.light+'[hanging=true]')
    b.put(x,y-2,z,b.glass)

def statue(b,x,y,z):
    b.box(x-3,y,z-3,x+3,y+1,z+3,b.trim)
    b.box(x-1,y+2,z-1,x+1,y+6,z+1,b.wall)
    b.box(x-3,y+6,z,x+3,y+7,z,b.trim)
    b.box(x-1,y+8,z-1,x+1,y+10,z+1,b.trim)
    b.put(x,y+9,z+2,b.glass)
    b.box(x+4,y+2,z,x+4,y+11,z,'iron_block')
    b.box(x+2,y+6,z,x+5,y+6,z,'gold_block' if b.name=='styx' else b.roof)

def room_finish(b,room,index):
    x1,y,z1,x2,top,z2=room['bounds'];role=room['role'];cx=(x1+x2)//2
    # Inlaid floor borders and coffered ceilings establish a human-scale interior.
    for x in range(x1+2,x2-1):
        for z in (z1+2,z2-2):b.put(x,y,z,b.accent)
    for z in range(z1+3,z2-2):
        for x in (x1+2,x2-2):b.put(x,y,z,b.accent)
    for z in range(z1+5,z2-3,8):
        b.box(x1+2,top-2,z,x2-2,top-2,z,b.trim)
        for x in range(x1+4,x2-2,7):
            b.box(x,top-3,z-2,x,top-3,z+2,b.trim)
    for x in (x1+2,x2-2):
        for z in range(z1+5,z2-2,8):
            b.box(x,y+1,z,x,top-3,z,b.trim)
            b.put(x,y+6,z+1,b.light)
    # All treasure approach pockets are restored after this furnishing pass.
    if index==0:return
    wide=x2-x1>=24;mid=(z1+z2)//2
    if wide:
        # Small rooms on both sides of a continuous central passage.
        for a,c in ((x1+2,cx-3),(cx+3,x2-8)):
            if a<=c:
                b.box(a,y+1,mid,c,y+6,mid,b.wood)
                b.box(a,y+5,mid,c,y+7,mid,b.glass)
        for x in (cx-3,cx+3):b.box(x,y+1,mid,x,y+8,mid,b.trim)
    left=x1+5;right=max(cx+3,x2-11)
    if role=='barracks':
        for z in range(z1+7,z2-7,7):bed(b,left,y,z,'red' if b.name=='acheron' else 'yellow')
        b.box(right,y+1,z1+6,right+3,y+1,z1+10,'smithing_table')
        b.box(right,y+2,z1+6,right,y+5,z1+10,'iron_bars')
        table(b,cx,y,z2-10,5)
    elif role=='forge':
        for z in range(z1+6,z2-5,9):
            b.put(left,y+1,z,'anvil');b.put(left+2,y+1,z,'grindstone[face=floor,facing=south]')
            b.put(right,y+1,z,'blast_furnace[facing=west]');b.put(right,y+2,z,'stonecutter[facing=west]')
            b.box(right+1,y+1,z,right+2,y+3,z+2,'raw_copper_block')
        b.box(cx-2,y+1,z1+8,cx+2,y+2,z1+12,b.trim)
        b.put(cx,y+3,z1+10,'anvil')
    elif role in ('library','observatory','chapel'):
        shelves(b,left,y,z1+6,10);shelves(b,left+3,y,z1+6,10)
        table(b,cx,y,z2-10,7)
        for z in (z1+9,z1+19):
            b.put(right,y+1,z,'lectern[facing=west]')
            b.put(right+1,y+2,z,'flower_pot')
        kitchen(b,cx,y,z1+3)
    else:
        kitchen(b,cx,y,z1+3)
        for z in (z1+12,z1+20):bed(b,left+1,y,z,'cyan')
        table(b,cx,y,z2-9,5)
        b.box(right,y+1,z1+7,right+2,y+4,z1+10,'barrel[facing=south]')
    if top-y>=19:
        # Furnish the upper galleries too, preserving the right-hand stair strip.
        uy=y+11
        for z in range(z1+12,z2-8,10):
            b.put(x1+4,uy+1,z,'lectern[facing=east]')
            b.put(x1+5,uy+1,z+2,'spruce_stairs[facing=north]')
            b.put(x1+3,uy+2,z+3,b.light)

def hall(b):
    y=4 if b.name=='banshee' else 5
    if b.name in ('acheron','cocytus'):
        # A ceremonial nave, readable pew groups and branching vault ribs.
        for z in (-65,-56,-47,-38):
            for sign in (-1,1):
                a,c=sorted((sign*6,sign*15))
                b.box(a,y+1,z,c,y+1,z,'dark_oak_stairs[facing=north]' if b.name=='acheron' else 'quartz_stairs[facing=north]')
                for x in (a,c):b.put(x,y+2,z,b.trim)
        for z in (-76,-60,-44):
            for x in range(-21,22):
                yy=y+19+round(7*(1-(abs(x)/21)**1.5))
                b.put(x,yy,z,b.trim)
            chandelier(b,0,y+17,z+3,4)
        for x in (-20,20):
            for z in (-70,-52,-34):
                b.box(x-1,y+1,z-1,x+1,y+2,z+1,b.trim)
                b.put(x,y+3,z,'decorated_pot')
        # Upper clerestory walk, balustrades and reading niches.
        for sign in (-1,1):
            x=sign*18
            for z in (-72,-56,-40):
                b.put(x,17,z,'lectern[facing='+('east' if sign<0 else 'west')+']')
    elif b.name=='phlegethon':
        # Central press, assembly benches, ducting and suspended ore hoppers.
        b.box(-5,y+1,-64,5,y+2,-51,b.trim)
        for x in (-5,5):b.box(x,y+3,-63,x,y+13,-52,'polished_basalt[axis=y]')
        b.box(-5,y+13,-64,5,y+15,-51,b.trim)
        b.box(-3,y+10,-62,3,y+11,-53,'iron_block')
        b.box(-2,y+3,-60,2,y+4,-55,'waxed_cut_copper')
        for x in (-17,17):
            for z in (-69,-53,-38):
                b.box(x-3,y+1,z-2,x+3,y+1,z+2,b.trim)
                b.put(x,y+2,z,'anvil');b.put(x+2,y+2,z,'grindstone[face=floor,facing=south]')
                b.box(x,y+11,z,x,y+18,z,'iron_chain[axis=y]')
                b.box(x-2,y+9,z-2,x+2,y+10,z+2,'iron_bars')
        for z in (-69,-45):b.box(-30,y+17,z,30,y+18,z,'waxed_cut_copper')
    elif b.name=='banshee':
        # Dining hall, lounge, fireplaces and occupied upper landings.
        for sign in (-1,1):
            x=sign*19
            for z in (-70,-41):
                table(b,x,y,z,5)
                b.put(x,y+3,z,'flower_pot')
            for z in (-63,-52):shelves(b,sign*26,y,z,7)
        b.box(-27,y+1,-42,-24,y+7,-35,'mud_bricks')
        b.box(-26,y+1,-41,-24,y+3,-36,'blackstone')
        b.put(-25,y+1,-39,'soul_campfire[lit=true]')
        b.box(-24,y+1,-41,-24,y+3,-37,'iron_bars')
        for z in (-66,-43):chandelier(b,0,y+15,z,4)
        for z in (-70,-53,-37):bed(b,-25,y+11,z,'cyan')
    elif b.name=='malefeasance':
        # Four dedicated research stations and a suspended celestial instrument.
        for x in (-16,16):
            for z in (-71,-43):
                b.box(x-3,6,z-3,x+3,7,z+3,b.wood)
                b.put(x,8,z,'brewing_stand' if z==-43 else 'cartography_table')
                for dx in (-2,2):b.put(x+dx,8,z,'amethyst_cluster' if dx<0 else 'flower_pot')
        for a in range(0,360,45):
            x=round(math.sin(math.radians(a))*24);z=-57+round(math.cos(math.radians(a))*24)
            b.put(x,6,z,'lectern[facing=south]')
        chandelier(b,0,27,-57,9)
        b.disk(0,24,-57,5,'purple_stained_glass',4)
        b.box(0,25,-57,0,31,-57,'end_rod[facing=up]')
    else:
        table(b,0,y,-67,13)
        b.box(-2,y+3,-72,2,y+3,-62,'green_carpet')
        b.put(0,y+4,-67,'lantern')
        for sign in (-1,1):
            shelves(b,sign*10,y,-77,7)
            b.put(sign*8,y+1,-54,'smithing_table')
            b.put(sign*8,y+2,-54,'grindstone[face=floor,facing=south]')

def exterior(b):
    datum=4 if b.name=='banshee' else 0
    for sign in (-1,1):
        x=sign*57
        # Covered cloisters frame, rather than occupy, the boss court.
        b.box(x-5,datum,-9,x+5,datum,47,b.wood if b.name=='banshee' else b.trim)
        b.box(x-6,datum+10,-11,x+6,datum+11,49,b.roof)
        for z in range(-7,48,9):
            for dx in (-5,5):
                b.box(x+dx,datum+1,z,x+dx,datum+8,z,b.trim)
                b.box(x+dx-1,datum+8,z-1,x+dx+1,datum+9,z+1,b.trim)
            b.box(x-4,datum+9,z,x+4,datum+9,z,b.trim)
            b.put(x,datum+8,z,b.light+'[hanging=true]')
        for z in (4,36):statue(b,sign*42,datum,z)
        # Continuous garden borders and cargo courts, not isolated garden squares.
        if b.name not in ('phlegethon','banshee'):
            b.box(sign*80-2,1,-16,sign*80+2,1,47,'snow_block' if b.name=='cocytus' else 'azalea_leaves[persistent=true]')
            for z in (-10,17,44):b.lamp(sign*80,2,z,2)
        if b.name=='acheron':
            b.tower(sign*16,-86,4,58,5)
            b.banner(sign*42-2,16,4,'red')
        elif b.name=='phlegethon':
            for z in (-10,38):
                b.box(sign*80-4,1,z-4,sign*80+4,3,z+4,'raw_iron_block')
                b.box(sign*80-3,4,z-3,sign*80+3,5,z+3,'iron_bars')
        elif b.name=='banshee':
            for z in (-12,12,41):b.box(sign*62,datum+10,z,sign*62,datum+16,z,'vine[south=true]')
        elif b.name=='malefeasance':
            for z in (-5,46):b.crystal(sign*80,2,z,18 if sign<0 else 24)
    # Material variation on large exposed walls, with fixed deterministic accents.
    variants={'acheron':'cracked_deepslate_bricks','phlegethon':'cracked_polished_blackstone_bricks','cocytus':'calcite','banshee':'spruce_planks','malefeasance':'smooth_quartz','styx':'cracked_deepslate_bricks'}
    for (x,y,z),m in list(b.b.items()):
        if m==b.wall and y>8 and (x*31+y*17+z*73)%37==0:b.put(x,y,z,variants[b.name])

def apply_details(b):
    for i,room in enumerate(b.rooms):room_finish(b,room,i)
    hall(b);exterior(b)
    # Preserve all eight original treasure positions and accessible lid pockets.
    caches=list(b.chests);b.chests=[]
    for c in caches:b.chest(c['x'],c['y'],c['z'],c['hint'])
    # Ground-level wing connections in v1 cut through the first stair treads.
    # Reconnect the complete flight after passages and furniture have been cut.
    for room in b.rooms:
        x1,y,z1,x2,top,z2=room['bounds']
        if top-y>=19 and x2-x1>=25 and z2-z1>=30:
            b.box(x1+3,y+11,z2-8,x2-3,y+11,z2-3,b.wood)
            for x in (x1+8,x2-8):b.box(x,y+12,z2-8,x,y+14,z2-3,'air')
            for x in (x1+8,x2-8):b.box(x,y+12,z1+4,x,y+14,z1+7,'air')
            for dy in range(12):
                z=z2-5-dy*2
                b.box(x2-6,y+dy,z-1,x2-3,y+dy,z,b.wood)
                b.box(x2-6,y+dy+1,z-1,x2-3,y+dy+3,z,'air')
