"""Faceted hero artwork and compositing layers for rare elemental spell scenes.

These are actual transparent world-plane textures, not inventory badges.  Work is
drawn at twice the export resolution; the silhouette, opaque facets and open air
between feathers/branches remain legible at Minecraft viewing distances.
"""
from pathlib import Path
import io
import math

from PIL import Image, ImageDraw, ImageFilter, ImageFont

from elemental_vfx_assets import PALETTES

SIZE = 512
EXPORT_SIZE = 256
PREFIX = 'elemental_rare_'
HEROES = {
    'meteor_rain': 'fire', 'phoenix_rebirth': 'fire', 'tsunami': 'water',
    'leviathans_wrath': 'water', 'absolute_zero': 'ice', 'tempest_dominion': 'wind',
    'titans_judgment': 'earth', 'heavens_descent': 'light',
    'abyssal_collapse': 'dark', 'wrath_of_raijin': 'lightning',
}
ACCENTS = {
    'fire': ('plume', 'ground'), 'water': ('crest', 'ground'),
    'ice': ('shards', 'ground'), 'wind': ('ribbon', 'ground'),
    'earth': ('fragments', 'ground'), 'light': ('rays', 'ground'),
    'dark': ('fragments', 'ground'), 'lightning': ('forks', 'ground'),
}


class Art:
    def __init__(self, element):
        self.element = element
        self.colors = dict(zip(('dark', 'base', 'hot'),
                               (tuple(bytes.fromhex(c[1:])) for c in PALETTES[element])))
        self.colors['ink'] = tuple(round(c * .45) for c in self.colors['dark'])
        self.colors['white'] = (251, 255, 255)
        self.im = Image.new('RGBA', (SIZE, SIZE))
        self.d = ImageDraw.Draw(self.im)

    def col(self, color='base', alpha=255):
        return (*self.colors.get(color, color), alpha)

    @staticmethod
    def pts(points):
        return [(round(x * 2), round(y * 2)) for x, y in points]

    def line(self, points, width=2, color='hot', alpha=255):
        self.d.line(self.pts(points), fill=self.col(color, alpha),
                    width=max(1, round(width * 2)), joint='curve')

    def poly(self, points, color='base', alpha=240, edge=True):
        self.d.polygon(self.pts(points), fill=self.col(color, alpha))
        if edge:
            self.line(points + [points[0]], 1.5, 'dark', 255)

    def ellipse(self, box, color='hot', alpha=240, width=0):
        box = tuple(round(v * 2) for v in box)
        self.d.ellipse(box, fill=self.col(color, alpha) if not width else None,
                       outline=self.col(color, alpha) if width else None,
                       width=max(1, round(width * 2)))

    def curve(self, p0, p1, p2, p3, width=3, color='hot', alpha=245):
        points = bezier(p0, p1, p2, p3)
        self.line(points, width, color, alpha)
        return points

    def ribbon(self, p0, p1, p2, p3, width=13, color='base', alpha=225):
        points = bezier(p0, p1, p2, p3)
        left, right = [], []
        for i, (x, y) in enumerate(points):
            before = points[max(0, i - 1)]
            after = points[min(len(points) - 1, i + 1)]
            dx, dy = after[0] - before[0], after[1] - before[1]
            length = math.hypot(dx, dy) or 1
            w = width * (math.sin(math.pi * i / (len(points) - 1)) ** .6) / 2
            left.append((x - dy / length * w, y + dx / length * w))
            right.append((x + dy / length * w, y - dx / length * w))
        self.poly(left + right[::-1], color, alpha, False)
        self.line(left, 1.5, 'hot', min(alpha + 25, 255))

    def shard(self, x, y, w, h, angle=0, color='base'):
        points = [(-.05, -1), (.65, -.30), (.45, .68), (-.1, 1), (-.65, .22), (-.5, -.52)]
        def tf(points):
            return [(x + u*w*math.cos(angle) - v*h*math.sin(angle),
                     y + u*w*math.sin(angle) + v*h*math.cos(angle)) for u, v in points]
        self.poly(tf(points), color)
        self.poly(tf([(-.05,-1),(-.5,-.52),(-.65,.22),(-.1,1),(-.12,-.15)]), 'dark', 245, False)
        self.poly(tf([(-.05,-1),(.65,-.3),(-.12,-.15)]), 'hot', 245, False)
        self.line(tf([(-.05,-1),(-.12,-.15),(-.1,1)]), 1.4, 'hot')

    def sparkle(self, x, y, r=5):
        self.poly([(x,y-r),(x+1.7,y-1.7),(x+r,y),(x+1.7,y+1.7),
                   (x,y+r),(x-1.7,y+1.7),(x-r,y),(x-1.7,y-1.7)], 'hot', 255, False)

    def image(self):
        # Small, tinted bloom lifts luminous edges without a square backdrop.
        alpha = self.im.getchannel('A').filter(ImageFilter.GaussianBlur(4))
        alpha = alpha.point(lambda v: round(v * .23))
        bloom = Image.new('RGBA', self.im.size, self.col('base', 0))
        bloom.putalpha(alpha)
        return Image.alpha_composite(bloom, self.im).resize(
            (EXPORT_SIZE, EXPORT_SIZE), Image.Resampling.LANCZOS)


def bezier(a, b, c, d, n=48):
    return [((1-t)**3*a[0]+3*(1-t)**2*t*b[0]+3*(1-t)*t*t*c[0]+t**3*d[0],
             (1-t)**3*a[1]+3*(1-t)**2*t*b[1]+3*(1-t)*t*t*c[1]+t**3*d[1])
            for t in (i/n for i in range(n+1))]


def feather(a, root, tip, width, shade='base'):
    x,y=root; tx,ty=tip
    dx,dy=tx-x,ty-y; length=math.hypot(dx,dy) or 1
    nx,ny=-dy/length,dx/length
    p=[root,(x+dx*.35+nx*width,y+dy*.35+ny*width),tip,
       (x+dx*.65-nx*width*.48,y+dy*.65-ny*width*.48)]
    a.poly(p,shade)
    a.line([root,(x+dx*.6,y+dy*.6),tip],1.4,'hot')


def hero(name, element):
    a=Art(element)
    if name == 'meteor_rain':
        # Molten basalt core, downward composition and three split fire tails.
        for j,(x,w) in enumerate(((74,21),(124,27),(173,18))):
            a.ribbon((x,12),(x-35,60),(x+32,97),(131+j*7,160),w,'dark',240)
            a.ribbon((x+8,9),(x-17,63),(x+31,100),(135+j*5,154),w*.65,'base',250)
            a.curve((x+9,13),(x-5,66),(x+20,107),(139+j*3,156),2,'hot')
        rock=[(99,117),(137,107),(170,131),(190,170),(174,211),
              (140,234),(106,224),(76,196),(71,154)]
        a.poly(rock,'dark',255)
        for pts,c in [([(99,117),(137,107),(127,153),(85,165)],'base'),
                      ([(137,107),(170,131),(161,172),(127,153)],'dark'),
                      ([(85,165),(127,153),(124,203),(106,224),(76,196)],'dark'),
                      ([(161,172),(190,170),(174,211),(140,234),(124,203)],'base')]:
            a.poly(pts,c,250)
        for pts in ([(110,123),(127,153),(113,174),(124,203),(140,225)],
                    [(127,153),(161,172),(180,183)],[(113,174),(81,181)],
                    [(161,172),(159,140)]):
            a.line(pts,6,'base');a.line(pts,2.2,'hot')
        for x,y,w,h,t in ((47,126,10,16,-.45),(207,121,9,20,.5),(53,210,7,10,-1)):
            a.shard(x,y,w,h,t);a.sparkle(x+10,y-16,3)
    elif name in ('phoenix_rebirth','heavens_descent'):
        phoenix=name=='phoenix_rebirth'
        # Each feather has a filled face and a separate lit quill; open gaps
        # let the world show through the wings rather than forming a billboard.
        for side in (-1,1):
            for layer in range(2):
                for k in range(7):
                    root=(128+side*(9+k*2),130+k*5+layer*7)
                    tip=(128+side*(36+k*12),30+k*18+layer*18)
                    feather(a,root,tip,10-layer*2,'dark' if layer else 'base')
            a.ribbon((128+side*13,140),(128+side*33,94),
                     (128+side*72,76),(128+side*108,37),12)
        if phoenix:
            a.poly([(122,100),(116,81),(128,54),(137,75),(153,82),(137,89),
                    (141,127),(132,162),(137,181),(119,170),(116,135)],'base')
            a.poly([(128,68),(124,108),(130,143),(134,120)],'hot',255,False)
            for j in range(5):
                a.ribbon((128+(j-2)*4,157),(100+j*12,183),
                         (148+(j-2)*12,212),(104+j*12,244),10-j%2*3,
                         'hot' if j==2 else 'base')
            a.sparkle(135,81,2)
        else:
            a.poly([(128,23),(138,88),(134,159),(128,233),(122,159),(118,88)],'hot')
            a.poly([(128,24),(128,222),(123,157),(122,89)],'white',245,False)
            a.poly([(88,99),(112,94),(128,110),(144,94),(168,99),
                    (144,112),(128,118),(112,112)],'base')
            a.shard(128,88,10,16)
            for side in (-1,1):
                a.curve((128+side*36,196),(128+side*79,180),
                        (128+side*74,161),(128+side*81,137),2)
        for x,y in ((40,47),(215,45),(48,190),(211,187),(128,17)):
            a.sparkle(x,y,4)
    elif name == 'tsunami':
        # A breaking hollow wave.  The large negative curl is intentional.
        a.poly([(15,224),(20,179),(30,132),(49,84),(77,48),(118,29),
                (165,31),(199,48),(218,72),(215,94),(196,107),(174,103),
                (159,88),(145,84),(125,94),(108,118),(111,149),(137,177),
                (176,191),(221,190),(242,220)],'dark',250)
        a.poly([(20,213),(34,151),(59,101),(90,64),(122,48),(161,47),
                (192,61),(203,79),(188,83),(169,70),(143,69),(119,83),
                (94,119),(96,153),(124,186),(177,207),(225,204),(238,220)],'base',235)
        a.ribbon((23,210),(70,171),(48,65),(138,43),12,'hot',245)
        a.ribbon((40,219),(104,162),(54,62),(158,55),7,'hot',230)
        a.ribbon((72,224),(132,207),(92,170),(101,139),10,'base',230)
        for j in range(13):
            t=j/12;x=84+t*125;y=45+math.sin(t*math.pi*1.4)*16+t*24
            a.poly([(x-7,y+8),(x+1,y-7),(x+10,y+3),(x+3,y+14)],'hot',250,False)
        for x,y in ((186,33),(223,44),(231,91),(164,16),(208,120),(67,30)):
            a.shard(x,y,3,7,.4,'hot')
    elif name == 'leviathans_wrath':
        a.ribbon((37,220),(4,177),(80,148),(82,107),35,'dark',255)
        a.ribbon((37,220),(105,208),(44,152),(91,125),26,'base',245)
        a.poly([(69,137),(51,103),(37,52),(74,74),(82,32),(108,69),
                (138,65),(165,83),(190,95),(230,100),(239,120),(223,143),
                (189,147),(175,173),(143,188),(106,181),(122,151)],'base',250)
        a.poly([(82,110),(108,83),(143,81),(167,99),(190,108),
                (229,113),(219,133),(181,130),(154,151),(122,151)],'dark',250)
        a.poly([(102,91),(126,71),(149,75),(162,89),(137,93)],'hot',245,False)
        for root,tip,w in (((71,86),(38,28),12),((95,72),(106,17),12),
                           ((127,71),(146,30),9),((153,80),(180,53),9)):
            feather(a,root,tip,w)
        a.line([(144,103),(164,101),(175,107)],5,'hot')
        a.line([(181,130),(204,132),(227,124)],2.5,'hot')
        for x,y in ((187,138),(213,135)):
            a.poly([(x-4,y),(x+2,y+18),(x+8,y-1)],'hot',255,False)
        for j in range(5):
            a.line([(82+j*9,129+j*8),(100+j*9,138+j*7),(116+j*8,134+j*7)],2)
        a.curve((170,118),(172,158),(228,155),(230,187),3,'hot')
        a.curve((177,119),(190,161),(215,175),(196,201),2,'hot')
    elif name == 'absolute_zero':
        for i in range(6):
            t=i*math.tau/6
            x,y=128+math.sin(t)*62,128+math.cos(t)*62
            a.shard(x,y,16,56,-t,'base')
            p=lambda r,off=0:(128+math.sin(t+off)*r,128+math.cos(t+off)*r)
            a.line([p(35),p(113)],2,'hot')
            a.line([p(70,-.2),p(87),p(70,.2)],3,'hot')
            a.shard(*p(106,.28),5,10,-t)
        a.shard(128,128,36,67)
        a.poly([(128,86),(141,122),(128,158),(115,122)],'white',250,False)
        for i in range(6):
            t=i*math.tau/6+math.pi/6
            a.sparkle(128+math.sin(t)*98,128+math.cos(t)*98,5)
    elif name == 'tempest_dominion':
        for j in range(5):
            y=210-j*34;w=20+j*19
            a.ribbon((128-w,y+10),(33-j*3,y-46),(229+j*3,y-56),
                     (128+w,y-10),14-j,'dark' if j%2 else 'base',230)
            a.curve((128-w,y+7),(45,y-39),(221,y-49),
                    (128+w,y-10),2,'hot')
        a.ribbon((122,234),(79,176),(167,106),(135,21),18,'hot',225)
        for j,(x,y,t) in enumerate(((38,73,-.9),(210,102,.8),(62,173,-.6),
                                   (197,193,.9),(139,39,.3))):
            a.shard(x,y,6,22,t,'hot' if j%2==0 else 'base')
        for x,y in ((23,117),(222,58),(104,226)):
            a.curve((x,y+13),(x-3,y),(x+17,y-10),(x+16,y-24),2)
    elif name == 'titans_judgment':
        # Heavy carved stone fist, lit fractures rather than a flat rune badge.
        a.poly([(53,166),(38,131),(48,98),(67,75),(79,47),(104,39),
                (119,49),(137,32),(161,38),(174,57),(197,65),(215,94),
                (220,134),(198,170),(180,184),(177,226),(87,226),(82,190)],'dark',255)
        faces=[[(51,110),(67,75),(82,69),(87,123),(71,155),(53,145)],
               [(84,51),(104,45),(115,58),(120,116),(92,123)],
               [(124,48),(138,38),(157,44),(166,64),(161,117),(128,114)],
               [(175,65),(194,71),(207,98),(209,127),(172,130)],
               [(71,155),(89,126),(160,120),(186,142),(176,170),(119,178)],
               [(98,181),(131,184),(171,173),(168,219),(97,219)]]
        for i,p in enumerate(faces):a.poly(p,'base' if i%2==0 else 'dark',255)
        for pts in ([(91,67),(99,97),(91,123)],[(144,57),(137,85),(146,112)],
                    [(179,87),(188,105),(180,125)],[(82,151),(115,151),(125,166),(160,151)],
                    [(110,191),(127,205),(155,191)]):
            a.line(pts,4,'base');a.line(pts,1.6,'hot')
        for x,y,w,h,t in ((39,188,10,14,.8),(212,176,12,18,-.5),(72,229,6,10,.6)):
            a.shard(x,y,w,h,t)
    elif name == 'abyssal_collapse':
        # Open, forked tear; bright seams frame black-violet faceted interior.
        left=[(130,18),(102,60),(109,88),(75,115),(99,140),(78,174),(113,205),(124,240)]
        right=[(136,22),(155,62),(145,89),(182,118),(159,141),(180,177),(145,205),(130,239)]
        a.poly(left+right[::-1],'ink',250)
        a.line(left,10,'dark');a.line(right,9,'dark')
        a.line(left,3,'base');a.line(right,3,'base')
        a.line([(127,42),(117,77),(127,102),(112,130),(132,157),(120,188),(129,218)],2,'hot')
        for j,(x,y,t) in enumerate(((65,70,-.6),(190,74,.5),(49,137,-1.1),
                                   (211,147,.9),(76,208,-.9),(181,216,.8))):
            a.shard(x,y,8+j%2*3,18+j%3*3,t)
        for side in (-1,1):
            a.ribbon((128+side*10,133),(128+side*70,110),
                     (128+side*92,144),(128+side*108,129),7,'base',190)
    elif name == 'wrath_of_raijin':
        # Layered thunder crown, a forked incandescent spear below its eye.
        cloud=[(33,116),(21,93),(35,76),(58,75),(62,49),(91,39),(115,53),
               (136,34),(165,43),(172,64),(202,61),(229,80),(229,102),
               (209,123),(168,132),(126,123),(84,134),(53,124)]
        a.poly(cloud,'dark',250)
        for p in ([(34,92),(63,84),(80,99),(103,79),(127,85),(114,104),(73,118)],
                  [(115,57),(137,44),(160,51),(157,79),(134,88)],
                  [(167,79),(203,73),(218,86),(209,107),(181,116),(158,103)]):
            a.poly(p,'base',245)
        a.line([(50,93),(76,89),(89,100),(108,91)],2,'hot')
        a.line([(165,91),(183,84),(210,90)],2,'hot')
        bolt=[(133,91),(106,151),(124,148),(101,193),(122,188),
              (112,241),(167,167),(145,171),(167,125),(144,130),(155,91)]
        a.poly(bolt,'base',255)
        a.poly([(136,105),(118,143),(135,140),(116,181),(137,176),
                (121,222),(155,177),(137,182),(153,139),(136,144),(146,107)],'hot',255,False)
        a.line([(107,145),(75,154),(83,181),(58,199)],3,'hot')
        a.line([(162,137),(194,147),(183,171),(210,191)],3,'hot')
        a.sparkle(133,89,10)
    return a.image()


def accent(element, shape):
    a=Art(element)
    if shape == 'ground':
        ground(a,element)
    elif shape in ('fragments','shards'):
        for j,(x,y,w,h,t) in enumerate(((51,52,13,24,-.6),(181,43,11,20,.5),
                       (211,126,13,23,1.1),(158,203,17,31,.3),
                       (57,189,12,23,-.8),(113,116,10,20,.2))):
            a.shard(x,y,w,h,t)
            if element=='fire':a.line([(x,y-h),(x+5,y),(x-3,y+h*.65)],1.6,'hot')
    elif shape in ('ribbon','ribbons','wisps','tendrils','arc'):
        for j in range(3):
            if element=='dark':
                a.ribbon((31+j*19,227),(246-j*10,163),(10+j*38,93),(204-j*21,27),13-j*2)
            elif element=='lightning':
                a.line([(28,192-j*39),(81,166-j*33),(72,139-j*25),
                        (147,118-j*21),(137,90-j*16),(224,50+j*23)],6-j,'base')
                a.line([(28,192-j*39),(81,166-j*33),(72,139-j*25),
                        (147,118-j*21),(137,90-j*16),(224,50+j*23)],2,'hot')
            else:
                a.ribbon((20,208-j*37),(114,221-j*54),(107,42+j*38),(238,34+j*39),17-j*3,
                         'base' if j!=1 else 'hot',230)
    elif shape in ('star','frost','scorch','fissure'):
        n=6 if element=='ice' else 8
        for i in range(n):
            t=i*math.tau/n
            def p(r,b=0):return (128+math.cos(t+b)*r,128+math.sin(t+b)*r)
            if shape in ('scorch','fissure'):
                pts=[p(18),p(44,.14),p(62,-.05),p(82,.12),p(112,.04)]
                a.line(pts,7,'dark');a.line(pts,3,'base');a.line(pts,1.2,'hot')
                a.line([pts[2],p(74,.4),p(94,.43)],1.6,'hot')
            else:
                a.poly([p(20,-.08),p(64,-.07),p(113),p(65,.065),p(20,.08)],'base',230)
                a.line([p(24),p(102)],2,'hot')
                if element=='ice':a.line([p(64,-.17),p(79),p(64,.17)],2,'hot')
        a.sparkle(128,128,19 if shape=='star' else 8)
    elif shape == 'plume':
        for j in range(4):
            x=61+j*41
            a.poly([(x-16,229),(x-30,180),(x-18,127),(x-26,67),
                    (x+4,21+j*13),(x+2,98),(x+22,75),(x+16,142),
                    (x+28,185),(x+12,231)],'dark' if j%2 else 'base')
            a.ribbon((x,228),(x+24,155),(x-20,99),(x+4,29+j*13),9,'hot',250)
    elif shape == 'crest':
        for j in range(3):
            y=91+j*37
            a.ribbon((20,y+30),(92,y-55),(139,y-47),(237,y+25),24-j*3)
            a.curve((20,y+23),(94,y-65),(143,y-47),(237,y+18),3,'hot')
        for x,y in ((52,41),(107,33),(173,38),(216,71)):
            a.shard(x,y,3,8,.4,'hot')
    elif shape == 'spray':
        for x,y,h,t in ((40,59,14,-.5),(89,26,11,-.2),(155,44,17,.2),
                         (215,78,14,.8),(196,170,12,.4),(70,193,14,-.5),
                         (130,136,12,.1),(45,125,8,-.7),(158,222,8,.2)):
            a.shard(x,y,4,h,t,'hot')
    elif shape == 'blades':
        for j in range(4):
            x=42+j*47
            a.poly([(x-16,215),(x+1,119),(x+37,26),(x+20,128)],'base',230)
            a.line([(x-11,204),(x+6,119),(x+33,34)],2,'hot')
    elif shape == 'crown':
        for j in range(7):
            x=29+j*33;y=181+abs(j-3)*4
            a.shard(x,y,18,36+(3-abs(j-3))*19,(j-3)*-.10)
    elif shape == 'rays':
        for j in range(7):
            x=22+j*35
            a.poly([(x-9,236),(x-5,71+j%3*18),(x,16),(x+5,71+j%3*18),(x+9,236)],'base',115,False)
            a.line([(x,219),(x,36+j%3*23)],1.5,'hot',245)
            a.sparkle(x,72+j%3*23,5)
    elif shape == 'feathers':
        for x,y,t in ((47,42,-.4),(143,30,.3),(213,88,.8),(146,169,.4),(53,194,-.2)):
            dx,dy=math.sin(t)*45,math.cos(t)*45
            feather(a,(x-dx,y+dy),(x+dx,y-dy),10)
    elif shape == 'rift':
        for side in (-1,1):
            pts=[(128+side*3,20),(128+side*25,63),(128+side*12,96),
                 (128+side*43,123),(128+side*19,159),(128+side*30,188),(128,237)]
            a.line(pts,13,'dark');a.line(pts,4,'base');a.line(pts,1,'hot')
    elif shape == 'forks':
        for pts in ([(128,18),(101,81),(127,75),(95,141),(123,132),(100,238)],
                    [(116,92),(63,120),(79,153),(35,198)],
                    [(109,151),(176,120),(163,85),(216,47)],
                    [(122,200),(186,189),(201,219)]):
            a.line(pts,8,'base');a.line(pts,2.7,'hot')
    return a.image()


def ground(a,element):
    """Ground accents preserve open centres and never imply a new hit area."""
    if element in ('fire','earth'):
        for j in range(7 if element=='fire' else 6):
            t=j*math.tau/(7 if element=='fire' else 6)
            def p(r,b=0):return (128+math.cos(t+b)*r,128+math.sin(t+b)*r)
            pts=[p(22),p(46,.13),p(65,-.1),p(83,.07),p(111,-.03)]
            a.line(pts,9 if element=='earth' else 6,'dark')
            a.line(pts,3.5 if element=='earth' else 3,'base')
            a.line(pts,1.2,'hot')
            a.line([pts[2],p(76,.33),p(96,.36)],2,'base')
            a.shard(*p(87,-.21),6,10,t+math.pi/2)
        if element=='earth':
            for j in range(3):
                t=j*math.tau/3+.2
                def p(r,b=0):return (128+math.cos(t+b)*r,128+math.sin(t+b)*r)
                a.poly([p(35,-.17),p(51,-.24),p(72,-.08),p(66,.13),p(46,.09)],'dark',220)
                a.line([p(51,-.24),p(72,-.08),p(66,.13)],1.7,'hot')
    elif element == 'water':
        for j in range(4):
            t=j*math.pi/2
            def tf(p):
                x,y=p;return (128+x*math.cos(t)-y*math.sin(t),128+x*math.sin(t)+y*math.cos(t))
            a.ribbon(tf((21,-20)),tf((102,-12)),tf((111,52)),tf((61,72)),11,'base',225)
            a.curve(tf((23,-24)),tf((99,-19)),tf((119,45)),tf((63,74)),2,'hot')
            for k in range(3):
                x,y=tf((91+k*3,18+k*13));a.shard(x,y,2.2,4,-t,'hot')
    elif element == 'ice':
        for j in range(6):
            t=j*math.tau/6
            def p(r,b=0):return (128+math.cos(t+b)*r,128+math.sin(t+b)*r)
            a.poly([p(27,-.10),p(64,-.06),p(111),p(64,.06),p(27,.10)],'base',210)
            a.line([p(30),p(106)],1.6,'hot')
            a.line([p(60,-.21),p(79),p(60,.21)],2,'hot')
            a.shard(*p(91,.26),4,9,math.pi/2+t)
    elif element == 'wind':
        for j in range(3):
            t=j*math.tau/3
            def tf(p):
                x,y=p;return (128+x*math.cos(t)-y*math.sin(t),128+x*math.sin(t)+y*math.cos(t))
            a.ribbon(tf((16,-18)),tf((105,-91)),tf((135,52)),tf((45,99)),13,'base',220)
            a.curve(tf((28,-24)),tf((104,-78)),tf((124,50)),tf((51,96)),2,'hot')
            a.poly([tf((53,-65)),tf((91,-67)),tf((117,-42)),tf((87,-51))],'hot',225,False)
    elif element == 'light':
        for j in range(4):
            t=j*math.pi/2
            def p(r,b=0):return (128+math.cos(t+b)*r,128+math.sin(t+b)*r)
            a.poly([p(29,-.08),p(62,-.13),p(114),p(62,.13),p(29,.08)],'base',220)
            a.line([p(35),p(108)],1.7,'hot')
            a.poly([p(64,-.36),p(78,-.34),p(94,-.45),p(76,-.51)],'base',225)
            a.line([p(64,-.36),p(94,-.45)],1.2,'hot')
        for j in range(4):
            t=j*math.pi/2+math.pi/4
            a.sparkle(128+math.cos(t)*76,128+math.sin(t)*76,5)
        a.poly([(128,98),(158,128),(128,158),(98,128)],'dark',180)
        a.line([(128,98),(158,128),(128,158),(98,128),(128,98)],1.8,'hot')
    elif element == 'dark':
        for j in range(5):
            t=j*math.tau/5+.17
            def p(r,b=0):return (128+math.cos(t+b)*r,128+math.sin(t+b)*r)
            pts=[p(24),p(48,.1),p(63,-.16),p(88,.13),p(112,.06)]
            a.line(pts,8,'ink');a.line(pts,3,'base');a.line(pts,1,'hot')
            a.shard(*p(86,-.28),6,11,t+.3)
        a.poly([(128,105),(148,124),(131,151),(108,131)],'ink',245)
        a.line([(128,105),(148,124),(131,151)],2,'hot')
    elif element == 'lightning':
        for j in range(4):
            t=j*math.pi/2
            def p(r,b=0):return (128+math.cos(t+b)*r,128+math.sin(t+b)*r)
            pts=[p(20),p(51,.13),p(47,-.20),p(79,-.04),p(73,.15),p(115,.09)]
            a.line(pts,6,'base');a.line(pts,2,'hot')
            fork=[p(51,.13),p(70,.43),p(78,.33),p(101,.52)]
            a.line(fork,3,'base');a.line(fork,1.3,'hot')
        a.sparkle(128,128,12)


def _write_if_changed(path, image):
    stream=io.BytesIO();image.save(stream,format='PNG',optimize=True)
    data=stream.getvalue()
    if not path.exists() or path.read_bytes()!=data:
        path.write_bytes(data)


def generate_assets(art_dir):
    """Write 26 256px assets; return (model id, element, descriptive shape)."""
    art_dir=Path(art_dir);art_dir.mkdir(parents=True,exist_ok=True)
    assets=[]
    for name,element in HEROES.items():
        model=PREFIX+name
        _write_if_changed(art_dir/(model+'.png'),hero(name,element))
        assets.append((model,element,'rare_hero_'+name))
    for element,shapes in ACCENTS.items():
        for shape in shapes:
            model=PREFIX+element+'_'+shape
            _write_if_changed(art_dir/(model+'.png'),accent(element,shape))
            assets.append((model,element,'rare_'+shape))
    # Versioned contact sheet avoids modifying a preview held open by Windows.
    cell_w,cell_h,cols=224,252,6
    sheet=Image.new('RGB',(cols*cell_w,math.ceil(len(assets)/cols)*cell_h),'#10202A')
    draw=ImageDraw.Draw(sheet)
    try:font=ImageFont.truetype('C:/Windows/Fonts/consola.ttf',12)
    except OSError:font=ImageFont.load_default()
    for i,(model,element,shape) in enumerate(assets):
        x=(i%cols)*cell_w;y=(i//cols)*cell_h
        draw.rectangle((x+3,y+3,x+cell_w-4,y+cell_h-4),fill='#142833',outline='#314451')
        with Image.open(art_dir/(model+'.png')) as image:
            image=image.resize((210,210),Image.Resampling.LANCZOS)
            sheet.paste(image,(x+7,y+4),image)
        label=model.removeprefix(PREFIX)
        draw.text((x+9,y+218),label,fill='#E8F4FC',font=font)
        draw.text((x+9,y+234),element,fill='#9CB1BD',font=font)
    _write_if_changed(art_dir/'rare-gallery-1.16.0.png',sheet)
    return assets


if __name__=='__main__':
    import json
    root=Path(__file__).resolve().parents[1]
    print(json.dumps(generate_assets(root/'advance-magic/art/elemental-vfx'),indent=2))
