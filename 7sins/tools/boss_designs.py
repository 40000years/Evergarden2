"""Authored dark-fantasy silhouettes for the existing nine-bone runtime rig.

Each cube is an actual Minecraft model element, not concept art. Horns and
helmets attach to the head; only supernatural halos use the rotating crown bone.
16 model units = one block before the encounter's fivefold scale.
"""
from copy import deepcopy
import math

PALETTES = {
    'wrath': dict(armor=(35,39,47),edge=(85,88,94),bone=(175,162,141),coal=(15,17,23),ember=(204,46,25),gold=(133,89,52),hot=(255,170,63),cloth=(66,21,25)),
    'pride': dict(armor=(44,43,58),edge=(118,96,61),bone=(215,203,172),coal=(20,19,31),ember=(212,165,62),gold=(190,153,77),hot=(255,232,154),cloth=(58,31,62)),
    'greed': dict(armor=(40,55,51),edge=(93,114,94),bone=(177,164,115),coal=(18,28,28),ember=(186,122,27),gold=(173,125,45),hot=(255,203,80),cloth=(40,62,48)),
    'lust': dict(armor=(49,28,43),edge=(114,66,83),bone=(190,168,169),coal=(23,15,28),ember=(186,37,78),gold=(153,105,111),hot=(255,119,163),cloth=(84,23,50)),
    'envy': dict(armor=(28,45,50),edge=(67,112,107),bone=(148,192,181),coal=(13,24,33),ember=(36,164,130),gold=(86,143,127),hot=(137,245,202),cloth=(27,56,60)),
    'gluttony': dict(armor=(71,69,42),edge=(107,106,63),bone=(189,179,137),coal=(27,29,20),ember=(112,155,37),gold=(139,120,57),hot=(214,238,104),cloth=(83,56,43)),
    'sloth': dict(armor=(65,64,73),edge=(112,109,119),bone=(166,159,170),coal=(26,25,35),ember=(110,75,158),gold=(113,93,124),hot=(189,153,241),cloth=(45,38,58)),
}

TITLES = {
    'wrath': 'THE ASHEN EXECUTIONER', 'pride': 'THE GILDED REGENT',
    'greed': 'THE COINBOUND DEVOURER', 'lust': 'THE CRIMSON ENCHANTRESS',
    'envy': 'THE MIRROR WRAITH', 'gluttony': 'THE ABYSSAL MAW',
    'sloth': 'THE GRAVEBOUND COLOSSUS',
}


class Sculpt:
    def __init__(self):
        self.parts = {part: [] for part in ('body','head','left_arm','right_arm','left_leg','right_leg','cleaver','core','crown')}

    def box(self, part, a, b, material='armor', angle=0, axis='z', pivot=None):
        assert all(-16 <= v <= 32 for v in (*a,*b)), (part,a,b)
        assert all(a[i] < b[i] for i in range(3)), (part,a,b)
        cube = dict(from_=list(a), to=list(b), material=material)
        cube['from'] = cube.pop('from_')
        if angle:
            cube['rotation'] = dict(origin=list(pivot or [(a[i]+b[i])/2 for i in range(3)]), axis=axis, angle=angle)
        self.parts.setdefault(part, []).append(cube)

    def stud(self, part, x, y, z, material='gold', size=1.5):
        self.box(part,(x,y,z),(x+size,y+size,z+1),material)

    def taper(self, part, x, y, z, width=5, height=12, material='bone', lean=22.5):
        # Overlapping tapered sections give a curved horn without floating tips.
        for i in range(3):
            w=width*(1-i*.25)
            self.box(part,(x-w/2,y+i*height/3,z-w/2),(x+w/2,y+(i+1)*height/3+1,z+w/2),
                     material if i<2 else 'coal',lean,pivot=(x,y,z))

    def halo(self, radius=13, count=8, y=11, material='gold', teeth=False):
        # Thin tangential segments form a continuous iron halo rather than floating blocks.
        for i in range(16):
            a=i*math.tau/16
            x,z=8+radius*math.cos(a),8+radius*math.sin(a)
            length=radius*math.tan(math.pi/16)*1.04
            angle=-(i*22.5+90)
            wide=True
            while angle < -45: angle+=90; wide=not wide
            while angle > 45: angle-=90; wide=not wide
            dx,dz=(length,.65) if wide else (.65,length)
            self.box('crown',(x-dx,y-.6,z-dz),(x+dx,y+.6,z+dz),material,angle,'y')
        for i in range(count):
            a=i*math.tau/count
            x,z=8+radius*math.cos(a),8+radius*math.sin(a)
            if teeth:
                self.box('crown',(x-.7,y,z-.7),(x+.7,y+3.5,z+.7),'ember')

    def armor_body(self, width=24, depth=16):
        l,r=8-width/2,8+width/2
        self.box('body',(l+3,-5,3),(r-3,10,depth),'coal')
        self.box('body',(l,5,1),(r,19,depth),'armor')
        self.box('body',(l+2,16,2),(r-2,22,depth-1),'edge')
        # Two sloping breastplates and a recessed sternum, with overlapping waist plates.
        for side in (-1,1):
            x=8+side*(width/4)
            self.box('body',(x-5,7,-1),(x+5,17,4),'armor',side*22.5)
            self.box('body',(x-4,8,-2),(x+4,10,0),'edge',side*22.5)
        for y,w in ((0,17),(-4,15),(-8,13)):
            self.box('body',(8-w/2,y,2),(8+w/2,y+5,6),'armor')
            self.box('body',(8-w/2,y,1),(8+w/2,y+1,3),'edge')
        for x in (l+2,r-4):
            for y in (6,13): self.stud('body',x,y,0)

    def arms(self, width=14, heavy=False):
        for part,side in (('left_arm',1),('right_arm',-1)):
            l,r=8-width/2,8+width/2
            self.box(part,(3,-6,4),(13,12,13),'coal')
            self.box(part,(l,3,2),(r,14,15),'armor')
            self.box(part,(l-3,10,1),(r+3,17,16),'edge',-side*22.5)
            self.box(part,(l-2,9,0),(r+2,15,15),'armor',-side*22.5)
            for y in (-8,-3,2):
                self.box(part,(l+1,y,1),(r-1,y+5,14),'armor')
                self.box(part,(l+1,y,0),(r-1,y+1,3),'edge')
            self.box(part,(3,-12,3),(13,-7,13),'coal')
            for x in (3,6,9): self.box(part,(x,-12,1),(x+2,-8,5),'bone')
            self.stud(part,5,11,-1)
            self.stud(part,10,11,-1)
            if heavy:
                self.box(part,(l-3,-8,1),(r+3,-1,16),'edge')
                self.box(part,(l-2,-8,-1),(r+2,-2,2),'armor')

    def legs(self, width=12, claws=False):
        for part in ('left_leg','right_leg'):
            l,r=8-width/2,8+width/2
            self.box(part,(l+1,0,4),(r-1,17,13),'coal')
            self.box(part,(l,8,1),(r,17,14),'armor')
            self.box(part,(l-1,5,-1),(r+1,11,5),'edge')
            self.box(part,(l,5,-2),(r,10,1),'armor')
            self.box(part,(l,-3,1),(r,5,14),'armor')
            self.box(part,(l-1,-6,-3),(r+1,-1,15),'coal')
            self.box(part,(l,-5,-4),(r,-2,4),'edge')
            if claws:
                for x in (l,l+4,r-2): self.box(part,(x,-6,-7),(x+2,-3,-1),'bone')
            else:
                self.box(part,(7,-2,-1),(9,6,2),'gold')

    def core(self, kind='gem'):
        if kind=='maw':
            self.box('core',(-1,-1,3),(17,17,7),'coal')
            for x in (0,4,8,12):
                self.box('core',(x,11,0),(x+2.5,17,5),'bone',-22.5)
                self.box('core',(x,-1,0),(x+2.5,5,5),'bone',22.5)
            self.box('core',(3,5,2),(13,11,4),'ember')
            self.box('core',(6,6,1),(10,10,3),'hot')
        elif kind=='hourglass':
            self.box('core',(2,0,3),(14,3,8),'gold')
            self.box('core',(2,13,3),(14,16,8),'gold')
            for x in (2,12): self.box('core',(x,3,4),(x+2,13,7),'bone')
            self.box('core',(4,10,3),(12,13,7),'ember')
            self.box('core',(7,5,2),(9,11,6),'hot')
            self.box('core',(4,3,3),(12,6,7),'ember')
        else:
            self.box('core',(2,2,4),(14,14,8),'coal',45)
            self.box('core',(4,4,2),(12,12,5),'gold',45)
            self.box('core',(5,5,0),(11,11,3),'ember',45)
            self.box('core',(7,7,-1),(9,9,1),'hot',45)

    def shaft(self, tail=-6):
        self.box('cleaver',(6,tail,6),(10,24,10),'coal')
        for y in range(tail+2,20,6): self.box('cleaver',(5.5,y,5.5),(10.5,y+1.5,10.5),'gold')
        self.box('cleaver',(5,tail-2,5),(11,tail+2,11),'edge')

    def ground_weapon(self, sin):
        self.box('ground_sword',(6,0,6),(10,8,10),'coal')
        self.box('ground_sword',(1,7,5),(15,10,11),'gold')
        self.box('ground_sword',(4,10,6),(12,25,10),'armor')
        self.box('ground_sword',(4,10,5),(6,26,7),'edge')
        self.box('ground_sword',(10,10,5),(12,26,7),'edge')
        self.box('ground_sword',(6,25,6),(10,29,10),'edge')
        self.box('ground_sword',(7,29,7),(9,32,9),'hot')
        self.box('ground_sword',(7,12,4.5),(9,25,6),'ember')
        if sin in ('gluttony','sloth'):
            for y in (12,18,24): self.box('ground_sword',(2,y,6),(5,y+3,10),'bone')


def wrath(s):
    s.armor_body(25,18); s.arms(15,True); s.legs(13,True); s.core(); s.shaft(-14)
    for y,w in ((5,11),(10,12),(15,13)):
        for side in (-1,1):
            x=8+side*w/2
            s.box('body',(x-4,y,-3),(x+4,y+2,1),'bone',side*22.5)
    s.box('head',(0,0,2),(16,16,15),'coal')
    s.box('head',(-1,10,0),(17,17,13),'armor')
    for side in (-1,1):
        x=8+side*6
        s.box('head',(x-4,2,-1),(x+4,12,4),'armor',side*22.5)
        s.box('head',(x-3,8,-2),(x+2,10,0),'ember')
        s.taper('head',8+side*10,12,8,7,13,'bone',-side*22.5)
        s.box('body',(8+side*10-3,-8,5),(8+side*10+3,2,16),'armor',side*22.5)
    s.box('head',(6,1,-3),(10,13,1),'edge')
    for x in (1,5,9,13): s.box('head',(x,-2,-1),(x+2,3,3),'bone')
    for part,side in (('left_arm',1),('right_arm',-1)):
        for x in (3,10): s.taper(part,x,15,8,5,13,'bone',-side*22.5)
        s.box(part,(5,-3,-2),(11,1,0),'ember')
    # Broad striking faces with recessed hot runes; the shaft stays at the hand pivot.
    s.box('cleaver',(-7,22,-1),(23,31,17),'armor')
    for x in (-10,22):
        s.box('cleaver',(x,20,-3),(x+4,32,19),'edge')
        s.box('cleaver',(x,23,-4),(x+4,29,-2),'ember')
    for x in (1,7,13): s.box('cleaver',(x,24,-2),(x+2,29,0),'gold')
    s.halo(13,8,12,'gold',True)


def pride(s):
    s.armor_body(24,16); s.arms(13); s.legs(12); s.core(); s.shaft()
    # Cathedral mantle: broad at the shoulders and tapered into separated skirt tails.
    s.box('body',(-5,7,15),(21,24,19),'cloth')
    for x in (-5,5,15):
        s.box('body',(x,-15,16),(x+7,12,20),'cloth')
        s.box('body',(x,-15,15),(x+1,-2,17),'gold')
    for side in (-1,1):
        x=8+side*8
        s.box('body',(x-4,9,-2),(x+4,18,1),'gold',side*22.5)
        s.box('body',(x-2,-7,0),(x+2,5,4),'bone')
    s.box('head',(1,-1,3),(15,17,14),'coal')
    s.box('head',(3,1,0),(13,17,6),'armor')
    s.box('head',(4,2,-1),(12,8,3),'bone')
    for x in (0,13): s.box('head',(x,3,-1),(x+3,19,7),'gold')
    s.box('head',(3,8,-1),(13,10,1),'coal')
    for x in (4,10): s.box('head',(x,8,-2),(x+2,9.5,0),'hot')
    s.box('head',(7,-2,-2),(9,21,2),'gold')
    for x,h in ((-2,20),(3,23),(7,26),(11,23),(16,20)):
        s.box('head',(x,15,4),(x+2,h,10),'gold')
        s.box('head',(x,15,3),(x+2,18,5),'ember')
    for part,side in (('left_arm',1),('right_arm',-1)):
        for i in range(3):
            x=8+side*(4+i*3)
            s.box(part,(x-3,12+i*2,4),(x+3,23+i*2,12),'gold',-side*22.5)
        s.box(part,(4,-7,-1),(12,5,1),'bone')
        s.box(part,(7,-6,-2),(9,6,0),'gold')
    # A broad ceremonial sword, not a stick with a cube on it.
    s.box('cleaver',(-3,13,4),(19,17,12),'gold')
    s.box('cleaver',(2,17,5),(14,29,11),'bone')
    s.box('cleaver',(1,17,5),(3,27,11),'gold',-22.5)
    s.box('cleaver',(13,17,5),(15,27,11),'gold',22.5)
    s.box('cleaver',(5,27,6),(11,32,10),'gold')
    s.box('cleaver',(7,18,4),(9,30,6),'hot')
    s.halo(17,12,16,'gold',True)


def greed(s):
    s.armor_body(30,23); s.arms(17,True); s.legs(14); s.shaft()
    # A deep treasury-vault torso with a locking wheel and chained gold ingots.
    s.box('body',(-8,-3,-2),(24,15,9),'armor')
    for x in (-8,21): s.box('body',(x,-5,-4),(x+3,18,1),'gold')
    for y in (-5,15): s.box('body',(-8,y,-4),(24,y+3,1),'gold')
    for x in (-5,2,9,16):
        for y in (0,7): s.stud('body',x,y,-5,size=2)
    s.box('core',(1,1,1),(15,15,7),'coal')
    for a in range(0,360,45):
        x,z=8+7*math.cos(math.radians(a)),8+7*math.sin(math.radians(a))
        s.box('core',(x-1,z-1,0),(x+1,z+1,3),'gold')
    s.box('core',(6,2,-1),(10,14,2),'gold')
    s.box('core',(2,6,-1),(14,10,2),'gold')
    s.box('core',(6,6,-2),(10,10,0),'hot')
    s.box('head',(-1,-2,1),(17,15,16),'armor')
    s.box('head',(-2,9,-1),(18,17,12),'edge')
    s.box('head',(1,5,-2),(15,8,1),'coal')
    for x in (2,11): s.box('head',(x,6,-3),(x+3,7.5,-1),'hot')
    s.box('head',(0,-3,-2),(16,3,5),'gold')
    for x in (1,4,7,10,13): s.box('head',(x,0,-3),(x+2,5,0),'bone')
    for side in (-1,1):
        s.taper('head',8+side*10,9,9,5,13,'gold',-side*22.5)
    for part in ('left_arm','right_arm'):
        for i in range(3):
            s.box(part,(-2+i*5,12,0),(2+i*5,20,6),'gold',22.5)
        for y in (-7,-1,5):
            for x in (0,14): s.box(part,(x,y,1),(x+2,y+5,4),'gold')
        s.box(part,(2,-6,-3),(14,-1,0),'gold')
    s.box('cleaver',(-2,19,0),(18,31,16),'gold')
    s.box('cleaver',(-4,21,2),(20,29,14),'gold')
    s.box('cleaver',(-2,20,-4),(18,29,0),'armor')
    for x in (0,7,14): s.box('cleaver',(x,22,-5),(x+2,27,-3),'ember')
    for x in (-7,19): s.box('cleaver',(x,21,3),(x+4,28,13),'edge')
    s.halo(14,8,11,'gold')


def lust(s):
    s.armor_body(18,14); s.arms(11); s.legs(10); s.core(); s.shaft()
    # Armored banshee, long segmented gown and broad thorn wings behind the shoulders.
    for side in (-1,1):
        for i in range(3):
            x=8+side*(9+i*3)
            s.box('body',(x-3,-14+i,7),(x+3,6,14),'cloth',side*22.5)
            s.box('body',(x-2,-14+i,6),(x-1,2,8),'edge',side*22.5)
        for i in range(4):
            x=8+side*(9+i*3.5)
            s.box('body',(x-2,7+i*2,13),(x+2,21+i*2,17),'armor',-side*22.5)
            s.box('body',(x-1,12+i*2,12),(x+1,23+i*2,14),'edge',-side*22.5)
    s.box('body',(3,-12,0),(13,6,5),'cloth')
    s.box('body',(7,-12,-1),(9,8,1),'gold')
    s.box('head',(2,0,3),(14,15,13),'coal')
    s.box('head',(3,2,0),(13,14,5),'bone')
    s.box('head',(1,10,1),(15,17,12),'armor')
    for side in (-1,1):
        x=8+side*7
        s.box('head',(x-2,-4,2),(x+2,11,8),'armor',side*22.5)
        s.taper('head',8+side*8,12,8,5,13,'edge',-side*22.5)
    for x in (4,10): s.box('head',(x,8,-1),(x+2,10,1),'ember')
    s.box('head',(6,0,-1),(10,5,2),'gold')
    for part,side in (('left_arm',1),('right_arm',-1)):
        s.taper(part,8+side*5,13,8,5,13,'edge',-side*22.5)
        for y in (-8,-2,4): s.box(part,(6,y,-1),(10,y+3,1),'gold',45)
    # Crescent thorns around the spearhead read from either side.
    for side in (-1,1):
        s.box('cleaver',(8+side*8-2,17,5),(8+side*8+2,29,11),'edge',-side*22.5)
    s.box('cleaver',(5,19,4),(11,31,12),'ember')
    s.box('cleaver',(7,27,3),(9,32,9),'hot')
    s.halo(12,6,15,'edge',True)


def envy(s):
    s.armor_body(20,15); s.arms(11); s.legs(11,True); s.core(); s.shaft()
    # Asymmetric crystal carapace; the left claw and right mantle break the human silhouette.
    for i in range(4):
        s.box('body',(-7+i*5,7+i*2,12),(-3+i*5,25+i*2,18),'edge',-22.5)
        s.box('body',(-6+i*5,11+i*2,11),(-4+i*5,24+i*2,13),'ember',-22.5)
    s.box('body',(13,-12,8),(22,6,15),'cloth',22.5)
    s.box('head',(1,0,3),(15,15,14),'coal')
    s.box('head',(-1,1,0),(7,16,7),'bone',-22.5)
    s.box('head',(9,1,-1),(16,14,6),'armor',22.5)
    s.box('head',(2,8,-2),(6,10,0),'hot')
    s.box('head',(10,7,-2),(14,9,0),'ember')
    s.taper('head',-1,12,9,6,13,'edge',22.5)
    s.taper('head',17,12,9,4,9,'bone',-22.5)
    s.box('left_arm',(-4,-7,0),(20,8,15),'armor')
    s.box('left_arm',(-5,1,-2),(21,11,5),'edge',-22.5)
    for x in (-3,4,11,18):
        s.box('left_arm',(x,-16,-3),(x+3,-5,3),'bone',22.5)
        s.box('left_arm',(x,10,5),(x+3,24,9),'edge',-22.5)
    for y in (1,7,13): s.box('right_arm',(1,y,1),(15,y+3,4),'bone',22.5)
    s.box('cleaver',(0,14,4),(16,18,12),'edge')
    s.box('cleaver',(2,18,5),(14,29,11),'bone',22.5)
    s.box('cleaver',(5,20,4),(11,32,7),'ember',22.5)
    s.box('cleaver',(3,-15,4),(13,-9,12),'edge')
    s.halo(15,5,13,'edge',True)


def gluttony(s):
    s.arms(18,True); s.legs(15,True); s.core('maw'); s.shaft()
    # Wide layered belly with a front cavity, rather than a flat rectangular chest.
    s.box('body',(-7,-7,2),(23,20,23),'armor')
    s.box('body',(-11,-2,3),(27,15,21),'armor')
    s.box('body',(-4,16,5),(20,23,20),'edge')
    s.box('body',(-4,-11,5),(20,-4,19),'cloth')
    s.box('body',(-3,1,-4),(19,17,3),'coal')
    for side in (-1,1):
        x=8+side*13
        s.box('body',(x-3,0,-2),(x+3,18,5),'bone',side*22.5)
        for y in (0,6,12): s.box('body',(x-4,y,-3),(x+4,y+3,1),'edge')
    for x in (-6,1,8,15):
        s.box('body',(x,-8,0),(x+6,-2,5),'bone')
        s.box('body',(x+2,14,-4),(x+4,21,1),'bone',-22.5)
    s.box('head',(-2,-4,2),(18,12,17),'armor')
    s.box('head',(-4,-6,-2),(20,1,13),'bone')
    s.box('head',(-2,1,-3),(18,6,3),'coal')
    for x in (-1,3,7,11,15): s.box('head',(x,-1,-4),(x+2,6,0),'bone')
    s.box('head',(-1,9,0),(17,15,10),'edge')
    for x in (1,11): s.box('head',(x,7,-1),(x+4,9,1),'ember')
    for side in (-1,1): s.taper('head',8+side*11,1,7,6,15,'bone',side*22.5)
    for part,side in (('left_arm',1),('right_arm',-1)):
        for x in (1,8,15): s.taper(part,x,13,9,5,11,'bone',-side*22.5)
        s.box(part,(0,-6,-3),(16,2,1),'cloth')
    # A butcher's jaw-club, with teeth around both striking faces.
    s.box('cleaver',(-3,18,1),(19,30,15),'bone')
    s.box('cleaver',(-5,20,3),(21,28,13),'bone')
    s.box('cleaver',(-2,20,-2),(18,28,1),'coal')
    for x in (-3,3,9,15): s.box('cleaver',(x,19,-4),(x+3,25,0),'bone')
    s.halo(13,7,8,'bone')


def sloth(s):
    s.armor_body(28,22); s.arms(18,True); s.legs(14); s.core('hourglass'); s.shaft()
    # Tombstone/stone coffin on the back: raised ledges, inset slab and chained lid.
    s.box('body',(-7,-11,18),(23,27,25),'coal')
    s.box('body',(-5,-10,23),(21,26,28),'armor')
    for x in (-7,20): s.box('body',(x,-11,24),(x+3,27,30),'edge')
    for y in (-11,24): s.box('body',(-7,y,24),(23,y+3,30),'edge')
    s.box('body',(6,-3,28),(10,21,30),'bone')
    s.box('body',(0,11,28),(16,14,30),'bone')
    s.box('head',(-3,-5,3),(19,15,18),'cloth')
    s.box('head',(-4,9,0),(20,17,15),'armor')
    for x in (-4,15): s.box('head',(x,-5,0),(x+5,13,6),'armor')
    s.box('head',(1,0,2),(15,9,5),'coal')
    for x in (3,10): s.box('head',(x,5,0),(x+3,7,3),'ember')
    s.box('head',(5,-3,1),(11,1,5),'bone')
    for part in ('left_arm','right_arm'):
        s.box(part,(-5,8,2),(21,22,20),'armor')
        s.box(part,(-6,8,0),(22,11,21),'edge')
        s.box(part,(-4,19,1),(20,23,20),'edge')
        for y in (-9,-3,3,9):
            for x in (0,14): s.box(part,(x,y,-1),(x+2,y+4,3),'gold')
        s.box(part,(5,11,-1),(11,19,2),'coal')
        s.box(part,(7,12,-2),(9,18,0),'ember')
    for side in (-1,1):
        x=8+side*10
        s.box('body',(x-4,-13,6),(x+4,2,16),'cloth')
    # Massive grave-scythe with a thick spine and stepped hooked edge.
    s.box('cleaver',(-10,21,3),(21,28,13),'edge')
    s.box('cleaver',(-9,19,2),(19,23,7),'bone')
    s.box('cleaver',(-12,12,3),(-7,25,12),'edge',-22.5)
    s.box('cleaver',(-11,10,2),(-8,20,6),'ember',-22.5)
    s.halo(15,8,12,'bone')


def geometry(sin, variants=True):
    s=Sculpt()
    globals()[sin](s)
    if variants:
        s.ground_weapon(sin)
        for part in ('body','head','cleaver','core'):
            cubes=deepcopy(s.parts[part])
            for cube in cubes:
                if cube['material']=='ember': cube['material']='hot'
                elif part=='core' and cube['material']=='gold': cube['material']='ember'
            s.parts[part+'_unbound']=cubes
    return s.parts


def texture(material, color, size=32):
    """Subdued material grain: broad value masses survive at in-game viewing distance."""
    pixels=[]
    for y in range(size):
        row=[]
        for x in range(size):
            grain=((x*13+y*23+x*y*7)%11)-5
            if material in ('ember','hot'):
                shade=9*math.sin(x*.22)+5*math.cos(y*.3)+grain*.25
            elif material=='cloth':
                shade=(3 if x%4==0 else -1)+(2 if y%4==0 else 0)+grain*.3
            elif material=='bone':
                shade=grain*.65-12*(x==(y//7+11)%size)
            else:
                shade=grain*.7+(3 if y%8==0 else 0)-5*(x==y+8)
            row.append(tuple(max(0,min(255,round(c+shade))) for c in color)+(255,))
        pixels.append(row)
    return pixels
