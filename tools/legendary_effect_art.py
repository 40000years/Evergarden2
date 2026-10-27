"""Regenerate original legendary spell SVG/PNG art, using the celestial line-art workflow.

Only Pillow is required. Geometry remains editable SVG rather than an opaque
generated image. All game art is transparent, centred, and shared by both clients.
"""
import html
import hashlib
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

from mythic_line_art import Drawing

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'advance-magic/art/effects/legendary'
GOLD, IVORY = '#f6cf79', '#fff6da'
EMERALD, JADE = '#58ffb1', '#12b76a'
SILVER, BLUE, LILAC = '#d8f3ff', '#72bfff', '#b59cff'
CRIMSON, RUBY, OBSIDIAN = '#ff315b', '#a41b52', '#180d27'


def rgb(color):
    return tuple(int(color[i:i + 2], 16) for i in (1, 3, 5))


def shipping_image(image):
    # A four-level maximum channel error is invisible at world-plane scale but
    # removes noisy subpixel values from blurred glow. Keep native RGBA/1024px,
    # instead of an indexed palette that could lose fine translucent accents.
    values=[min(255,round(value/8)*8) for value in range(256)]
    image=image.point(values*4)
    transparent=image.getchannel('A').point(lambda value:255 if value==0 else 0)
    image.paste((0,0,0,0),mask=transparent)
    return image


def bezier(a, b, c, d, steps=40):
    return [tuple((1-t)**3*a[k] + 3*(1-t)**2*t*b[k]
                  + 3*(1-t)*t*t*c[k] + t**3*d[k] for k in (0, 1))
            for t in (i/steps for i in range(steps+1))]


def at(x, y, radius, angle):
    return x + math.cos(angle)*radius, y + math.sin(angle)*radius


class Engraving(Drawing):
    """Supersampled ivory filaments with soft coloured bloom and editable paths."""
    def ellipse(self, x, y, rx, ry, width=2, color=GOLD):
        self.shapes.append(('ellipse', (x, y, rx, ry), width, color))

    def polygon(self, points, fill, color=GOLD, width=3, alpha=110):
        self.shapes.append(('fill', (points, alpha), width, fill))
        self.path(points + [points[0]], width, color)

    def trace(self, points, width=2, color=GOLD, alpha=100):
        """Recessed celestial engraving, kept quieter than the hero contour."""
        self.shapes.append(('trace', (points, alpha), width, color))

    def trace_arc(self, x, y, radius, start, stop, width=2, color=GOLD, alpha=100):
        count=max(12, int(abs(stop-start)*radius/5))
        self.trace([at(x, y, radius, start+(stop-start)*i/count)
                    for i in range(count+1)], width, color, alpha)

    def curve(self, a, b, c, d, width=2, color=GOLD):
        self.path(bezier(a, b, c, d), width, color)

    def arc(self, x, y, radius, start, stop, width=2, color=GOLD):
        count=max(12, int(abs(stop-start)*radius/5))
        self.path([at(x, y, radius, start+(stop-start)*i/count)
                   for i in range(count+1)], width, color)

    def spark(self, x, y, radius=10, color=IVORY):
        self.path([(x-radius*.28, y), (x, y-radius), (x+radius*.28, y),
                   (x, y+radius), (x-radius*.28, y)], 1.6, color)
        self.path([(x-radius*.68, y), (x+radius*.68, y)], 1, color)

    def jewel(self, x, y, height=30, width=14, color=EMERALD):
        points=[(x, y-height), (x+width, y), (x, y+height), (x-width, y)]
        self.polygon(points, color, IVORY, 1.5, 35)
        self.path([(x, y-height), (x, y+height)], 1, color)
        self.path([(x-width, y), (x+width, y)], 1, color)

    def render(self):
        scale=2
        size=(1024*scale, 1024*scale)
        strokes=Image.new('RGBA', size)
        fills=Image.new('RGBA', size)
        stroke_draw, fill_draw = ImageDraw.Draw(strokes), ImageDraw.Draw(fills)
        bloom_layers=[Image.new('RGBA',size) for _ in range(3)]
        bloom_draws=[ImageDraw.Draw(layer) for layer in bloom_layers]
        svg_strokes, svg_fills = [], []
        for kind, data, width, color in self.shapes:
            if kind == 'fill':
                points, alpha=data
                fill_draw.polygon([(x*scale, y*scale) for x, y in points], fill=(*rgb(color), alpha))
                pts=' '.join(f'{x:.2f},{y:.2f}' for x, y in points)
                svg_fills.append(f'<polygon points="{pts}" fill="{color}" fill-opacity="{alpha/255:.4f}"/>')
                continue
            stroke_alpha=255
            if kind == 'trace':
                data,stroke_alpha=data
            if kind in ('circle', 'ellipse'):
                x, y, rx, ry=(512, 512, data, data) if kind == 'circle' else data
                stroke_draw.ellipse(tuple(v*scale for v in (x-rx, y-ry, x+rx, y+ry)),
                                    outline=(*rgb(color), 255), width=max(1, round(width*scale)))
                for glow_draw,extra in zip(bloom_draws,(18,8,3)):
                    glow_draw.ellipse(tuple(v*scale for v in (x-rx, y-ry, x+rx, y+ry)),
                                      outline=(*rgb(color),255),width=round((width+extra)*scale))
                svg_strokes.append(f'<ellipse cx="{x:.2f}" cy="{y:.2f}" rx="{rx:.2f}" ry="{ry:.2f}" stroke="{color}" stroke-width="{width}"/>')
            else:
                pts=[(x*scale, y*scale) for x, y in data]
                stroke_draw.line(pts, fill=(*rgb(color), stroke_alpha), width=max(1, round(width*scale)), joint='curve')
                for glow_draw,extra in zip(bloom_draws,(18,8,3)):
                    glow_draw.line(pts,fill=(*rgb(color),stroke_alpha),width=round((width+extra)*scale),joint='curve')
                # Round joins avoid sharp antialias gaps on crystalline contours.
                if len(pts) < 30:
                    r=max(1, width*scale/2)
                    for x,y in pts:
                        stroke_draw.ellipse((x-r,y-r,x+r,y+r),fill=(*rgb(color),stroke_alpha))
                pts=' '.join(f'{x:.2f},{y:.2f}' for x,y in data)
                svg_strokes.append(f'<polyline points="{pts}" stroke="{color}" stroke-width="{width}" opacity="{stroke_alpha/255:.4f}"/>')
        image=fills
        for glow_layer,blur,opacity in zip(bloom_layers,(9,4,1.4),(.20,.34,.42)):
            glow=glow_layer.filter(ImageFilter.GaussianBlur(blur*scale))
            glow.putalpha(glow.getchannel('A').point(lambda a: round(a*opacity)))
            image=Image.alpha_composite(image,glow)
        image=Image.alpha_composite(image,strokes).resize((1024,1024),Image.Resampling.LANCZOS)
        # LANCZOS can carry colour into zero-alpha texels; these do not render.
        definitions=''.join(f'<filter id="glow{i}" x="-30%" y="-30%" width="160%" height="160%"><feMorphology operator="dilate" radius="{spread}"/><feGaussianBlur stdDeviation="{blur}"/></filter>'
                            for i,(blur,spread) in enumerate(((9,9),(4,4),(1.4,1.5))))
        content='\n'.join(svg_strokes)
        svg=['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">',
             '<title>Advance Magic original legendary spell engraving</title>',
             f'<defs>{definitions}</defs>', '<g>'+ '\n'.join(svg_fills)+'</g>']
        for i,opacity in enumerate((.20,.34,.42)):
            svg.append(f'<g fill="none" stroke-linecap="round" stroke-linejoin="round" filter="url(#glow{i})" opacity="{opacity}">{content}</g>')
        svg.append(f'<g fill="none" stroke-linecap="round" stroke-linejoin="round">{content}</g></svg>')
        return image,'\n'.join(svg)+'\n'


def rune(d, angle, radius, index, color, size=15):
    """Original geometric script: stem, branches and open diamond, never text."""
    radial=(math.cos(angle),math.sin(angle)); tang=(-radial[1],radial[0])
    def p(x,y):
        return (512+radial[0]*(radius+y)+tang[0]*x,
                512+radial[1]*(radius+y)+tang[1]*x)
    d.path([p(0,-size),p(0,size)],1.8,color)
    d.path([p(-size*.6,-size*.62),p(0,-size*.15),p(size*.6,-size*.62)],1.8,color)
    if index%3==0:
        d.path([p(-size*.5,size*.4),p(0,size*.9),p(size*.5,size*.4)],1.5,IVORY)
    elif index%3==1:
        d.path([p(-size*.55,size*.42),p(size*.55,size*.42)],1.5,IVORY)
        d.path([p(-size*.5,0),p(-size*.5,size*.9)],1.5,color)
    else:
        d.path([p(-size*.52,size*.25),p(0,-size*.25),p(size*.52,size*.25),p(0,size*.8),p(-size*.52,size*.25)],1.5,IVORY)


def petal(d, angle, base, length, spread, color):
    left=d.polar(angle-spread,base)
    tip=d.polar(angle,base+length)
    right=d.polar(angle+spread,base)
    d.curve(left,d.polar(angle-spread*1.6,base+length*.52),
            d.polar(angle-spread*.35,base+length*.8),tip,2,color)
    d.curve(tip,d.polar(angle+spread*.35,base+length*.8),
            d.polar(angle+spread*1.6,base+length*.52),right,2,color)
    d.path([left,d.polar(angle,base+length*.12),right],1.5,GOLD)
    d.path([d.polar(angle,base),tip],1.2,IVORY)


def seal(theme):
    if theme=='verdant':return dragon_portal(floor=True)
    if theme=='lunar':return moon_portal(floor=True)
    d=Engraving()
    color,secondary={"verdant":(EMERALD,JADE),"lunar":(BLUE,LILAC),"crimson":(CRIMSON,RUBY)}[theme]
    for r,w,c in ((472,2.5,GOLD),(459,1.4,IVORY),(416,3,color),(404,1.2,GOLD),
                  (351,2.5,GOLD),(338,1.2,color),(262,2,IVORY),(251,1.2,secondary),
                  (164,2.5,color),(148,1.2,GOLD)):
        d.circle(r,w,c)
    # Small broken gate segments make a moving world plane read at every scale.
    for i in range(12):
        a=math.tau*i/12
        d.arc(512,512,488,a-.09,a+.09,2,color)
        d.path([d.polar(a-.038,463),d.polar(a,495),d.polar(a+.038,463)],2,IVORY)
        d.jewel(*d.polar(a,376),height=15,width=8,color=color)
    for i in range(36):
        a=math.tau*(i+.5)/36
        rune(d,a,437,i,color,size=12)
        # Fine jewels between glyphs remain separate from the larger glyph ring.
        d.ellipse(*d.polar(a,416),2.5,2.5,1,IVORY)
    d.star(12,5,335,math.pi/12,1.5,color)
    d.star(6,2,247,math.pi/6,1.5,GOLD)
    for i in range(12):
        a=math.tau*i/12
        if theme=='verdant':
            petal(d,a,268,60,.12,color)
            petal(d,a+.24,180,58,.12,secondary)
        elif theme=='lunar':
            d.arc(*d.polar(a,299),25,a-.25,a+math.pi*1.48,1.8,IVORY)
            d.spark(*d.polar(a,225),9,color)
            d.path([d.polar(a-.06,277),d.polar(a,324),d.polar(a+.06,277)],1.5,secondary)
        else:
            d.path([d.polar(a-.09,279),d.polar(a,327),d.polar(a+.09,279),
                    d.polar(a,292),d.polar(a-.09,279)],1.8,color)
            d.path([d.polar(a,174),d.polar(a-.075,224),d.polar(a,204),
                    d.polar(a+.075,224),d.polar(a,174)],1.6,secondary)
        d.spark(*d.polar(a+math.pi/12,357),5,IVORY)
    # Separate central heraldry makes the three seals unmistakably different.
    if theme=='verdant':
        for i in range(6):petal(d,math.tau*i/6,45,85,.27,EMERALD)
        d.star(6,2,75,math.pi/6,2,GOLD)
        d.jewel(512,512,44,25,EMERALD)
    elif theme=='lunar':
        d.arc(512,512,98,.60,math.tau-.60,3,IVORY)
        d.curve((593,457),(494,433),(469,559),(593,567),3,BLUE)
        d.spark(558,512,21,IVORY)
        for x,y,s in ((515,413,6),(472,456,8),(489,591,6)):d.spark(x,y,s,LILAC)
    else:
        d.polygon([(512,405),(579,484),(547,560),(512,617),(477,560),(445,484)],
                  RUBY,CRIMSON,2,26)
        d.path([(512,405),(483,489),(512,617),(541,489),(512,405)],1.8,IVORY)
        d.path([(445,484),(483,489),(541,489),(579,484)],1.8,GOLD)
        d.star(8,3,132,math.pi/8,1.5,RUBY)
    return d


def sovereign_portal(d,color,moon=False):
    """Six luminous bands and independent ornament around a clear hero field."""
    accent=BLUE if moon else JADE
    bright=SILVER if moon else IVORY
    for radius,width,tint in ((478,4.5,color),(469,1.6,bright),(459,2.4,GOLD),
                              (446,1.8,accent),(398,4,color),(387,1.5,GOLD)):
        d.circle(radius,width,tint)
    for i in range(48):
        a=math.tau*i/48-math.pi/2
        # Fine calibrated ticks read as one filigree band at distance rather
        # than a collection of large isolated symbols.
        d.path([d.polar(a,460),d.polar(a,475 if i%4==0 else 468)],
               2.8 if i%4==0 else 1.4,bright if i%4==0 else color)
    for i in range(24):
        a=math.tau*(i+.5)/24-math.pi/2
        rune(d,a,421,i,color,size=13)
        d.ellipse(*d.polar(a,398),2.3,2.3,1.1,bright)
    for i in range(12):
        a=math.tau*i/12-math.pi/2
        # Separate fluted arcs, diamond anchors and curling laurels frame the
        # hero; none of these strokes is drawn across its face or the moon void.
        d.arc(512,512,491,a-.075,a+.075,2.1,color)
        d.path([d.polar(a-.027,476),d.polar(a,500),d.polar(a+.027,476)],2,bright)
        d.jewel(*d.polar(a,421),height=18,width=9,color=color)
        d.arc(512,512,374,a+.038,a+.17,2.2,accent)
        d.arc(512,512,367,a+.061,a+.145,1.3,GOLD)
        d.path([d.polar(a+.101,372),d.polar(a+.119,354),d.polar(a+.137,372)],1.6,bright)
    for i in range(4):
        a=math.tau*i/4-math.pi/2
        d.polygon([d.polar(a,494),d.polar(a+.032,472),
                   d.polar(a,452),d.polar(a-.032,472)],color,bright,2.4,205)
        d.spark(*d.polar(a,345),12,bright)


def tube(points,width):
    left,right=[],[]
    for i,(x,y) in enumerate(points):
        a=points[max(0,i-1)];b=points[min(len(points)-1,i+1)]
        dx,dy=b[0]-a[0],b[1]-a[1];length=math.hypot(dx,dy) or 1
        w=width*(1-.65*i/(len(points)-1))
        left.append((x-dy/length*w,y+dx/length*w))
        right.append((x+dy/length*w,y-dx/length*w))
    return left+list(reversed(right))


def dragon_portal(floor=False):
    """A plated emerald drake within an ornate sovereign spell instrument."""
    d=Engraving();sovereign_portal(d,EMERALD)
    # A curved heraldic plinth fills the otherwise empty lower inner margin.
    # Its geometry stays outside the silhouette, so the dragon reads first.
    for radius,width,color in ((328,1.8,GOLD),(318,2.6,EMERALD),(309,1.2,IVORY)):
        d.arc(512,512,radius,.36,math.pi-.36,width,color)
    for i in range(9):
        a=.48+(math.pi-.96)*i/8
        d.path([d.polar(a-.018,318),d.polar(a,337),d.polar(a+.018,318)],1.7,GOLD)
    # The wings stop outside the neck: none of their bones crosses the face/body.
    wing=[(447,483),(392,381),(315,313),(231,265),(203,326),(153,360),
          (205,366),(181,423),(251,405),(249,469),(314,435),
          (341,510),(391,473),(438,525)]
    for sign in (-1,1):
        mirror=lambda p:(512+sign*(512-p[0]),p[1])
        points=[mirror(p) for p in wing]
        d.polygon(points,JADE,EMERALD,10,205 if not floor else 175)
        for end in ((231,265),(153,360),(181,423),(249,469)):
            d.curve(mirror((435,484)),mirror((380,415)),mirror((301,328)),mirror(end),5.2,EMERALD)
        d.curve(mirror((329,339)),mirror((302,353)),mirror((296,381)),mirror((294,415)),3.6,IVORY)
        d.path([mirror((271,350)),mirror((281,373)),mirror((263,390))],3.4,GOLD)
        d.path([mirror((252,296)),mirror((303,329)),mirror((318,349))],1.8,IVORY)
        d.path([mirror((284,356)),mirror((294,365)),mirror((279,373))],1.7,GOLD)
        d.path([mirror((322,389)),mirror((339,401)),mirror((325,412))],1.7,IVORY)
        for offset in range(4):
            x=313+offset*23;y=367+offset*30
            d.curve(mirror((x,y)),mirror((x+17,y+5)),
                    mirror((x+16,y+19)),mirror((x+29,y+23)),1.6,GOLD)
        d.curve(mirror((214,333)),mirror((251,310)),mirror((295,335)),mirror((341,379)),1.8,GOLD)
        d.curve(mirror((242,423)),mirror((272,395)),mirror((311,405)),mirror((367,440)),1.8,IVORY)
    spine=(bezier((512,463),(579,518),(650,532),(580,606),35)+
           bezier((580,606),(507,670),(336,618),(373,701),35)+
           bezier((373,701),(410,787),(584,777),(663,704),35))
    d.polygon(tube(spine,42),JADE,EMERALD,10,230)
    # A limited plated belly reinforces the body curve rather than filling it
    # with a lattice; the silhouette carries recognition at far viewing ranges.
    d.path(spine[6:],5.2,IVORY)
    for i in range(17,len(spine)-8,11):
        x,y=spine[i];a=spine[i-1];b=spine[i+1]
        dx,dy=b[0]-a[0],b[1]-a[1];length=math.hypot(dx,dy) or 1
        nx,ny=-dy/length,dx/length
        w=26*(1-.4*i/len(spine))
        d.path([(x+nx*w,y+ny*w),(x-dx/length*9,y-dy/length*9),
                (x-nx*w,y-ny*w)],3.2,GOLD)
    for i in (32,52,72,92):
        x,y=spine[i];a=spine[i-1];b=spine[i+1]
        dx,dy=b[0]-a[0],b[1]-a[1];length=math.hypot(dx,dy) or 1
        tx,ty=dx/length,dy/length;nx,ny=-ty,tx
        x+=nx*10;y+=ny*10
        d.path([(x-tx*7,y-ty*7),(x+nx*5,y+ny*5),
                (x+tx*7,y+ty*7),(x-nx*5,y-ny*5),(x-tx*7,y-ty*7)],1.5,IVORY)
    # Paired small scale plates run within the material rather than extending
    # the silhouette. Ivory rims remain visible over the saturated jade body.
    for i in range(12,len(spine)-11,7):
        x,y=spine[i];a=spine[i-1];b=spine[i+1]
        dx,dy=b[0]-a[0],b[1]-a[1];length=math.hypot(dx,dy) or 1
        tx,ty=dx/length,dy/length;nx,ny=-ty,tx
        width=13*(1-.4*i/len(spine))
        for side in (-1,1):
            cx=x+side*nx*width;cy=y+side*ny*width
            d.path([(cx-tx*5,cy-ty*5),(cx+side*nx*4,cy+side*ny*4),
                    (cx+tx*5,cy+ty*5)],1.6,GOLD)
    d.polygon([(648,720),(674,681),(691,704),(664,746)],JADE,EMERALD,7,225)
    # Two hooked claws reach forward, separate from the serpentine belly.
    claws=[(456,453),(419,497),(385,534),(372,546),(365,528),(349,551),
           (348,526),(327,547),(337,510),(399,469),(426,430)]
    for sign in (-1,1):
        mirror=lambda p:(512+sign*(512-p[0]),p[1])
        d.polygon([mirror(p) for p in claws],JADE,EMERALD,8,225)
        d.path([mirror((371,529)),mirror((383,509)),mirror((416,481))],3.4,IVORY)
    # A front-facing head with wide horns, angular snout, bright eyes and an open
    # dark jaw. This contour remains unmistakable before any filigree is visible.
    head=[(432,326),(443,288),(418,220),(467,260),(485,305),(512,293),
          (539,305),(557,260),(606,220),(581,288),(592,326),(578,355),
          (600,377),(581,411),(560,429),(550,463),(525,483),(512,468),
          (499,483),(474,463),(464,429),(443,411),(424,377),(446,355)]
    d.polygon(head,JADE,EMERALD,11,245)
    # Large cheek and forehead facets establish a substantial crystal creature.
    d.polygon([(443,323),(483,307),(493,369),(471,405),(436,378)],EMERALD,EMERALD,2.5,150)
    d.polygon([(581,323),(541,307),(531,369),(553,405),(588,378)],EMERALD,EMERALD,2.5,150)
    d.path([(512,299),(501,354),(512,384),(523,354),(512,299)],5.2,IVORY)
    d.path([(436,333),(471,337),(486,351)],6,GOLD)
    d.path([(588,333),(553,337),(538,351)],6,GOLD)
    for eye in ([(447,350),(481,357),(466,373)],[(577,350),(543,357),(558,373)]):
        d.polygon(eye,IVORY,IVORY,3,255)
    d.polygon([(472,403),(512,419),(552,403),(546,447),(512,463),(478,447)],'#052a25',EMERALD,6,255)
    for sign in (-1,1):
        for i in range(2):
            x=512+sign*(27-i*14);y=418+i*6
            d.polygon([(x-sign*5,y-5),(x+sign*6,y-1),(x,y+13)],IVORY,IVORY,1.8,255)
    d.path([(480,393),(490,389)],5.5,'#052a25')
    d.path([(544,393),(534,389)],5.5,'#052a25')
    d.path([(429,235),(455,286)],4.5,IVORY)
    d.path([(595,235),(569,286)],4.5,IVORY)
    for sign in (-1,1):
        d.path([(512+sign*52,377),(512+sign*61,386),(512+sign*50,394)],1.8,GOLD)
        d.path([(512+sign*20,318),(512+sign*29,327),(512+sign*21,337)],1.7,GOLD)
        d.path([(512+sign*73,289),(512+sign*65,307),(512+sign*78,319)],1.7,IVORY)
        d.path([(512+sign*71,401),(512+sign*58,409),(512+sign*57,421)],2,GOLD)
        for row in range(3):
            x=512+sign*(26+row*9);y=342+row*14
            d.path([(x-sign*4,y-4),(x,y),(x+sign*4,y-4)],1.5,IVORY)
    if floor:
        # The coordinated floor seal uses the same large readable drake and
        # quiet script band, with four restrained radial channel markers.
        for i in range(4):
            a=math.pi/4+math.tau*i/4
            d.arc(512,512,382,a-.09,a+.09,5,JADE)
    return d


def moon_portal(floor=False):
    """Engraved silver crescent with a miniature astral chart in its dark void."""
    d=Engraving();sovereign_portal(d,SILVER,moon=True)
    # The small astrolabe lives entirely in the crescent's open side. It gives
    # the portal a celestial mechanism while preserving the huge moon contour.
    d.trace_arc(638,512,111,0,math.tau,1.8,BLUE,140)
    d.trace_arc(638,512,103,-.25,math.pi+.25,1.3,GOLD,170)
    d.trace_arc(638,512,87,math.pi+.18,math.tau-.18,1.3,SILVER,140)
    for i in range(12):
        a=math.tau*i/12
        d.path([at(638,512,109,a),at(638,512,119 if i%3==0 else 114,a)],1.6,SILVER)
    for x,y,s in ((666,329,9),(733,397,6),(758,577,8),(675,690,9)):
        d.spark(x,y,s,SILVER)
    d.trace([(666,329),(733,397),(758,577),(675,690)],1.2,BLUE,130)
    outer=bezier((638,223),(254,191),(224,727),(638,802),70)
    inner=bezier((638,802),(466,660),(421,397),(638,223),70)
    d.polygon(outer+inner,SILVER,SILVER,13,248)
    # Parallel engraved rims leave the bright, opaque crescent substantial.
    d.curve((611,251),(302,235),(283,715),(608,773),5,BLUE)
    d.curve((604,746),(482,636),(459,416),(610,269),3.4,GOLD)
    d.curve((584,280),(354,283),(349,680),(589,746),1.9,LILAC)
    # Original crater/phase engravings sit on the broad moon material, with
    # smaller diamond filaments giving the same hand-authored clockwork finish.
    for x,y,r in ((363,397,23),(342,502,35),(382,612,23),(421,698,12)):
        d.ellipse(x,y,r,r,2.4,BLUE)
        d.trace_arc(x+3,y+3,r*.61,0,math.tau,1.3,GOLD,185)
        d.arc(x,y,r+4,math.pi*.92,math.pi*1.55,1.5,IVORY)
    for x,y in ((415,354),(392,466),(385,558),(440,655)):
        d.path([(x,y-8),(x+5,y),(x,y+8),(x-5,y),(x,y-8)],1.6,GOLD)
    # A royal eight-point guiding star, clearly separate from the crescent.
    star=[]
    for i in range(16):
        a=-math.pi/2+math.tau*i/16
        r=(51 if i%4==0 else 35) if i%2==0 else 12
        star.append(at(638,512,r,a))
    d.polygon(star,SILVER,SILVER,2.5,195)
    d.jewel(638,512,height=27,width=11,color=BLUE)
    d.ellipse(638,512,61,61,1.5,GOLD)
    if floor:
        for i in range(4):
            a=math.tau*i/4+math.pi/4
            d.arc(512,512,375,a-.08,a+.08,3,BLUE)
    return d


def moon_orbit_quiet():
    """A coordinated halo: richer calibrated bands, with an empty beam mouth."""
    d=Engraving()
    for radius,width,color in ((472,3.2,SILVER),(459,1.4,GOLD),
                               (441,2.5,BLUE),(396,2.8,SILVER),(384,1.5,LILAC)):
        d.circle(radius,width,color)
    for i in range(24):
        a=math.tau*i/24
        d.path([d.polar(a,460),d.polar(a,470 if i%3==0 else 466)],1.8,SILVER)
        rune(d,a,419,i,SILVER,11)
    for i in range(8):
        a=math.tau*i/8
        d.arc(512,512,372,a+.10,a+.53,2.4,GOLD)
        d.path([d.polar(a,397),d.polar(a-.031,425),d.polar(a,446),d.polar(a+.031,425),d.polar(a,397)],2.3,SILVER)
    return d


def verdant_halo():
    """Concentric royal green/gold gate with a completely transparent centre."""
    d=Engraving();sovereign_portal(d,EMERALD)
    d.circle(342,3.2,GOLD);d.circle(330,1.6,IVORY)
    for i in range(12):
        a=math.tau*i/12
        petal(d,a,343,36,.054,EMERALD)
        d.path([d.polar(a-.023,343),d.polar(a,357),d.polar(a+.023,343)],1.7,GOLD)
    # Raised short scallops add dimension at the outer band. Shape geometry
    # stays outside radius330; soft bloom feathers into its inner margin.
    return d


def lunar_constellation():
    """Silver/gold star-map floor altar with a quiet central launch aperture."""
    d=Engraving();sovereign_portal(d,SILVER,moon=True)
    for radius,width,color in ((341,2.5,GOLD),(328,1.5,SILVER),
                               (237,2.3,BLUE),(226,1.3,GOLD),(128,1.6,SILVER)):
        d.circle(radius,width,color)
    # This schematic star band occupies only the annulus. Unlike a complete
    # overlapping star polygon, no diameter line crosses the quiet central disk.
    for i in range(12):
        a=math.tau*i/12-math.pi/2
        d.path([d.polar(a-.10,244),d.polar(a,320),d.polar(a+.10,244)],2.2,SILVER)
        d.path([d.polar(a-.064,246),d.polar(a,300),d.polar(a+.064,246)],1.3,GOLD)
        d.spark(*d.polar(a+math.pi/12,282),8,BLUE)
    # Twelve authored constellations, linked with faint fine lines inside the
    # next annulus, give moving full-bright displays a detailed celestial map.
    for i in range(12):
        a=math.tau*i/12
        points=[d.polar(a-.065,170),d.polar(a+.015,197),d.polar(a+.082,169)]
        d.trace(points,1.5,BLUE,175)
        for j,p in enumerate(points):d.spark(*p,5 if j!=1 else 8,SILVER)
    for i in range(8):
        a=math.tau*i/8
        d.arc(512,512,113,a+.12,a+.56,1.8,GOLD)
    return d


def crown():
    d=Engraving()
    # Royal emerald coronet, explicitly physical crystals instead of a starburst.
    outline=[(191,655),(150,358),(282,481),(297,281),(412,451),(512,151),
             (612,451),(727,281),(742,481),(874,358),(833,655),
             (771,732),(647,776),(512,796),(377,776),(253,732)]
    d.polygon(outline,JADE,GOLD,4.2,48)
    for x,y in ((150,358),(297,281),(512,151),(727,281),(874,358)):
        d.jewel(x,y,29 if x!=512 else 45,15,EMERALD)
    # Five independent facets convey crystal height and a sharp crown profile.
    for tip,base in (((150,358),(247,665)),((297,281),(386,698)),
                     ((512,151),(512,730)),((727,281),(638,698)),((874,358),(777,665))):
        tx,ty=tip;bx,by=base
        d.path([tip,(bx-29,by-35),base,(bx+29,by-35),tip],2.4,EMERALD)
        d.path([tip,base],1.8,IVORY)
    d.curve((192,655),(340,701),(684,701),(832,655),4,GOLD)
    d.curve((209,695),(360,760),(664,760),(815,695),3.2,IVORY)
    d.curve((252,733),(379,780),(645,780),(772,733),2,EMERALD)
    for i in range(9):
        x=268+i*61; y=720-48*abs(x-512)/244
        d.jewel(x,y,16,10,EMERALD if i%2==0 else GOLD)
    # Engraved curling vine inlays beneath the prongs, mirrored by hand.
    for sign in (-1,1):
        tr=lambda p:(512+sign*p[0],p[1])
        for offset in (0,123):
            d.curve(tr((37+offset,629)),tr((85+offset,562)),tr((149+offset,643)),tr((84+offset,650)),2,GOLD)
            d.curve(tr((84+offset,650)),tr((53+offset,613)),tr((97+offset,599)),tr((105+offset,629)),1.7,EMERALD)
    d.jewel(512,617,70,38,EMERALD)
    d.ellipse(512,617,47,81,2,GOLD)
    for x,y,s in ((512,91,16),(127,306,10),(897,306,10),(350,209,8),(674,209,8)):
        d.spark(x,y,s,IVORY)
    return d


def heart():
    d=Engraving()
    shell=[(512,78),(720,273),(762,488),(635,731),(512,941),(389,731),(262,488),(304,273)]
    d.polygon(shell,OBSIDIAN,CRIMSON,4.2,120)
    # Asymmetric facet fills make an obsidian/ruby crystal, not a wireframe icon.
    facets=[([(512,78),(512,336),(304,273)],RUBY,75),
            ([(512,78),(720,273),(512,336)],CRIMSON,35),
            ([(304,273),(512,336),(400,527),(262,488)],RUBY,55),
            ([(720,273),(762,488),(624,527),(512,336)],CRIMSON,70),
            ([(400,527),(512,336),(624,527),(512,718)],CRIMSON,95),
            ([(262,488),(400,527),(512,941),(389,731)],RUBY,50),
            ([(762,488),(635,731),(512,941),(624,527)],RUBY,75),
            ([(400,527),(512,718),(512,941)],CRIMSON,30)]
    for points,color,alpha in facets:d.polygon(points,color,CRIMSON,2,alpha)
    d.path([(512,78),(512,336),(400,527),(512,718),(624,527),(512,336)],2.6,IVORY)
    d.path([(304,273),(400,307),(512,336),(624,307),(720,273)],2.5,GOLD)
    d.path([(262,488),(400,527),(624,527),(762,488)],2.2,RUBY)
    # Small original angular inscriptions and fine fractures across the facets.
    for sign in (-1,1):
        for row in range(4):
            x=512+sign*(118+row*11); y=307+row*54
            d.path([(x-sign*16,y-14),(x,y-4),(x+sign*16,y-14),
                    (x+sign*12,y+9),(x,y+22),(x-sign*12,y+9)],1.5,GOLD)
        d.path([(512+sign*143,590),(512+sign*91,624),(512+sign*112,670),
                (512+sign*62,742)],1.4,CRIMSON)
        d.path([(512+sign*65,218),(512+sign*27,271),(512+sign*40,298)],1.3,RUBY)
    for x,y,s in ((401,398,9),(621,407,8),(512,803,10)):d.spark(x,y,s,CRIMSON)
    # Angular gold cage, floating shoulder shards and a pulsing axial heart.
    d.path([(281,310),(229,331),(210,499),(344,763),(512,980),(680,763),
            (814,499),(795,331),(743,310)],2.6,GOLD)
    for sign in (-1,1):
        for off in (0,1,2):
            x=512+sign*(259+off*38); y=403+off*117
            points=[(x,y-75),(x+sign*23,y-6),(x+sign*8,y+67),(x-sign*18,y+4)]
            d.polygon(points,OBSIDIAN,RUBY if off%2 else CRIMSON,2,100)
            d.path([points[0],points[2]],1.5,IVORY)
    d.jewel(512,500,92,45,CRIMSON)
    d.path([(465,500),(490,500),(502,476),(515,532),(528,492),(540,500),(559,500)],2.8,IVORY)
    # Small orbiting fracture sparks carry the same original angular motif.
    for x,y,s in ((189,230,10),(837,225,9),(158,746,11),(863,793,13),(512,30,12)):
        d.spark(x,y,s,CRIMSON)
    return d


def shard():
    d=Engraving()
    silhouette=[(512,57),(599,310),(633,280),(666,486),(717,464),
                (664,661),(616,809),(548,949),(510,873),(449,909),
                (387,683),(330,615),(370,459),(344,409),(425,421),(444,239),(467,289)]
    d.polygon(silhouette,OBSIDIAN,CRIMSON,4,130)
    d.polygon([(512,57),(526,418),(617,592),(548,949),(492,638),(427,498)],
              RUBY,CRIMSON,2,115)
    d.polygon([(512,57),(599,310),(526,418)],CRIMSON,GOLD,1.6,80)
    d.path([(512,57),(526,418),(492,638),(548,949)],2.8,IVORY)
    d.path([(444,239),(427,498),(387,683),(449,909)],2,GOLD)
    d.path([(633,280),(600,501),(616,809),(548,949)],2,GOLD)
    d.path([(344,409),(427,498),(492,638),(664,661)],2.1,CRIMSON)
    d.path([(633,280),(526,418),(600,501),(717,464)],2.1,CRIMSON)
    d.path([(330,615),(427,498),(492,638),(449,909)],1.6,RUBY)
    # Floating fragments keep the physical jagged blade readable in a large field.
    for points in ([(342,272),(372,333),(359,387),(331,326)],
                   [(708,655),(735,718),(706,774),(692,704)],
                   [(409,838),(419,915),(383,968),(381,897)]):
        d.polygon(points,OBSIDIAN,RUBY,2,100)
        d.path([points[0],points[2]],1.3,CRIMSON)
    for x,y,s in ((604,195,7),(386,730,8),(647,828,9)):d.spark(x,y,s,CRIMSON)
    return d


def ribbon(color, secondary):
    image=Image.new('RGBA',(256,256));pixels=image.load()
    hue=rgb(color);white=rgb(IVORY if color==EMERALD else SILVER if color==BLUE else '#fff1f3')
    for y in range(256):
        for x in range(256):
            distance=abs(x-127.5)/127.5
            core=math.exp(-(distance/.075)**2)
            halo=math.exp(-(distance/.39)**2)
            woven=.86+.14*math.cos(y*math.tau/64+distance*12)
            alpha=round(255*min(1,core+halo*.70)*woven)
            if distance>.93:alpha=0
            tint=tuple(round(v+(w-v)*core) for v,w in zip(hue,white))
            pixels[x,y]=(*tint,alpha)
    svg=f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256">
<title>Continuous {html.escape(color)} legendary axial ribbon</title>
<defs><linearGradient id="beam"><stop offset="0" stop-color="{color}" stop-opacity="0"/><stop offset=".35" stop-color="{color}" stop-opacity=".36"/><stop offset=".48" stop-color="{color}"/><stop offset=".5" stop-color="{IVORY}"/><stop offset=".52" stop-color="{color}"/><stop offset=".65" stop-color="{color}" stop-opacity=".36"/><stop offset="1" stop-color="{color}" stop-opacity="0"/></linearGradient></defs>
<rect width="256" height="256" fill="url(#beam)"/></svg>
'''
    return image,svg


def beam_core():
    """Distant radiant impact light: filaments, not an opaque circular button."""
    drawing=Engraving()
    for i in range(24):
        a=math.tau*i/24
        end=419 if i%3==0 else 308 if i%3==1 else 244
        drawing.trace([drawing.polar(a,76),drawing.polar(a,end)],
                      5 if i%3==0 else 2.5,EMERALD,195)
        if i%3==0:
            drawing.path([drawing.polar(a,48),drawing.polar(a,end*.77)],3.8,IVORY)
    for i in range(8):
        a=math.tau*i/8
        drawing.polygon([drawing.polar(a,286),drawing.polar(a+.05,106),
                         drawing.polar(a,54),drawing.polar(a-.05,106)],
                        EMERALD,IVORY,1.4,105)
    drawing.polygon([at(512,512,58,math.tau*i/64) for i in range(64)],
                    '#f1fff4',IVORY,4,235)
    drawing.circle(88,2.5,EMERALD)
    for i in range(8):
        a=math.tau*(i+.5)/8
        drawing.arc(512,512,122,a-.12,a+.12,1.8,GOLD)
    return drawing


def verdant_ribbon():
    """White-hot narrow axis inside transparent emerald energy shoulders.

    Crossed Minecraft beam faces must not become four giant white sheets in
    first person. The visible volume comes from a fine core and twin braided
    filaments, while runtime particles and travelling gates provide magnitude.
    """
    image=Image.new('RGBA',(256,256));pixels=image.load()
    hue=rgb(EMERALD);white=rgb('#f1fff4')
    for y in range(256):
        filaments=[127.5+sign*(72+9*math.sin(math.tau*y/256+phase))
                   for sign in (-1,1) for phase in (0,math.pi)]
        for x in range(256):
            distance=abs(x-127.5)/127.5
            core=math.exp(-(distance/.095)**2)
            halo=math.exp(-(distance/.42)**2)
            filament=max(math.exp(-((x-centre)/1.8)**2) for centre in filaments)
            highlight=max(core,filament*.72)
            alpha=round(255*min(.94,core*.91+halo*.16+filament*.72))
            if distance>.96:alpha=0
            pixels[x,y]=(*tuple(round(v+(w-v)*highlight) for v,w in zip(hue,white)),alpha)
    lines=[]
    for sign in (-1,1):
        for phase in (0,math.pi):
            points=' '.join(f'{127.5+sign*(72+9*math.sin(math.tau*y/256+phase)):.2f},{y}'
                            for y in range(0,257,4))
            lines.append(f'<polyline points="{points}" fill="none" stroke="{IVORY}" stroke-width="2.4" opacity=".72"/>')
    svg=f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256">
<title>Emerald breath, narrow white axis and transparent braided energy shoulders</title>
<defs><linearGradient id="beam"><stop offset="0" stop-color="{EMERALD}" stop-opacity="0"/><stop offset=".20" stop-color="{EMERALD}" stop-opacity=".03"/><stop offset=".40" stop-color="{EMERALD}" stop-opacity=".13"/><stop offset=".475" stop-color="{EMERALD}" stop-opacity=".48"/><stop offset=".5" stop-color="#f1fff4" stop-opacity=".94"/><stop offset=".525" stop-color="{EMERALD}" stop-opacity=".48"/><stop offset=".60" stop-color="{EMERALD}" stop-opacity=".13"/><stop offset=".80" stop-color="{EMERALD}" stop-opacity=".03"/><stop offset="1" stop-color="{EMERALD}" stop-opacity="0"/></linearGradient></defs>
<rect width="256" height="256" fill="url(#beam)"/>{''.join(lines)}</svg>
'''
    return image,svg


def bright_ribbon(color):
    """Broad solid cylinder core with coloured shoulders and four filaments."""
    image=Image.new('RGBA',(256,256));pixels=image.load()
    hue=rgb(color);white=rgb('#f1fff4' if color==EMERALD else '#f0ffff')
    for y in range(256):
        filaments=[127.5+sign*(72+9*math.sin(math.tau*y/256+phase))
                   for sign in (-1,1) for phase in (0,math.pi)]
        for x in range(256):
            distance=abs(x-127.5)/127.5
            core=max(0,1-(distance/.41)**6)
            filament=max(math.exp(-((x-centre)/2.4)**2) for centre in filaments)
            highlight=max(core,filament*.84)
            alpha=255 if distance<=.44 else round(205*math.exp(-((distance-.44)/.29)**2))
            if distance>.96:alpha=0
            pixels[x,y]=(*tuple(round(v+(w-v)*highlight) for v,w in zip(hue,white)),alpha)
    lines=[]
    for sign in (-1,1):
        for phase in (0,math.pi):
            points=' '.join(f'{127.5+sign*(72+9*math.sin(math.tau*y/256+phase)):.2f},{y}'
                            for y in range(0,257,4))
            lines.append(f'<polyline points="{points}" fill="none" stroke="{SILVER if color!=EMERALD else IVORY}" stroke-width="2.8" opacity=".8"/>')
    white_color='#f1fff4' if color==EMERALD else '#f0ffff'
    svg=f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 256 256">
<title>Broad solid legendary beam cylinder with continuous axial filaments</title>
<defs><linearGradient id="beam"><stop offset="0" stop-color="{color}" stop-opacity="0"/><stop offset=".20" stop-color="{color}" stop-opacity=".55"/><stop offset=".28" stop-color="{color}"/><stop offset=".38" stop-color="{white_color}"/><stop offset=".62" stop-color="{white_color}"/><stop offset=".72" stop-color="{color}"/><stop offset=".80" stop-color="{color}" stop-opacity=".55"/><stop offset="1" stop-color="{color}" stop-opacity="0"/></linearGradient></defs>
<rect width="256" height="256" fill="url(#beam)"/>{''.join(lines)}</svg>
'''
    return image,svg


def preview_readability(images):
    names=('verdant_dragon','lunar_seal','lunar_crescent')
    board=Image.new('RGBA',(1260,560),(5,11,16,255));draw=ImageDraw.Draw(board)
    font=ImageFont.load_default(size=18)
    for col,name in enumerate(names):
        x=col*420
        thumbnail=images[name].resize((32,32),Image.Resampling.LANCZOS)
        board.alpha_composite(thumbnail.resize((256,256),Image.Resampling.NEAREST),(x+80,55))
        board.alpha_composite(thumbnail,(x+194,352))
        draw.text((x+42,18),name.upper(),font=font,fill=SILVER)
        draw.text((x+49,410),'32px glyph enlarged 8x / nearest',font=font,fill=IVORY)
        draw.text((x+49,443),'Original 32px shown underneath',font=font,fill=SILVER)
    draw.text((36,506),'Small-scale readability check on a dark background; art preview, not game footage.',font=font,fill=SILVER)
    board.save(ART/'readability-32px.png',optimize=True)


def preview_layout(images):
    scene=Image.new('RGBA',(1900,850),(6,13,19,255));draw=ImageDraw.Draw(scene)
    font=ImageFont.load_default(size=22);small=ImageFont.load_default(size=17)
    draw.text((30,18),'LAYOUT CONCEPT / NOT MINECRAFT FOOTAGE',font=font,fill=SILVER)
    draw.line([(940,66),(940,805)],fill='#233a46',width=2)
    draw.text((35,70),'VERDANT / SEAL BEHIND CASTER + FORWARD BEAM',font=small,fill=EMERALD)
    draw.line([(40,684),(900,684)],fill='#1a4738',width=2)
    scene.alpha_composite(images['verdant_dragon'].resize((420,420),Image.Resampling.LANCZOS),(24,181))
    beam=Image.open(ART/'verdant_ray.png').resize((112,565),Image.Resampling.LANCZOS).rotate(90,expand=True)
    scene.alpha_composite(beam,(354,390))
    draw.ellipse((388,412,426,450),fill='#bdd7d2')
    draw.polygon([(388,454),(425,454),(434,556),(416,653),(406,563),(398,653),(379,654),(383,553)],fill='#688b83')
    draw.line([(418,472),(449,497),(487,482)],fill='#bdd7d2',width=14)
    draw.line([(479,491),(512,433)],fill=GOLD,width=7)
    draw.text((35,710),'The drake is one complete front-readable portal.',font=small,fill=SILVER)
    draw.text((35,741),'4m emerald beam / 5.2m finale; no crown in caster view.',font=small,fill=SILVER)
    draw.text((364,667),'CASTER',font=small,fill=IVORY)
    draw.text((765,481),'FORWARD',font=small,fill=IVORY)
    draw.text((980,70),'LUNAR / SIXTEEN LARGE SKY PORTALS + THIN RAYS',font=small,fill=SILVER)
    moon=images['lunar_seal'].resize((184,108),Image.Resampling.LANCZOS)
    ray=Image.open(ART/'lunar_ray.png').resize((10,66),Image.Resampling.LANCZOS)
    for row in range(4):
        for col in range(4):
            x=985+col*218;y=135+row*133
            scene.alpha_composite(ray,(x+87,y+60))
            scene.alpha_composite(moon,(x,y))
            draw.polygon([(x+86,y+124),(x+96,y+124),(x+91,y+130)],fill=SILVER)
    draw.text((980,710),'4 x 4 sky grid; 10m portals / 1.1m beam widths.',font=small,fill=SILVER)
    draw.text((980,741),'Each portal faces the captured casting eye and fires a thin downward beam.',font=small,fill=SILVER)
    scene.save(ART/'scene-layout.png',optimize=True)


def preview(images):
    width,height=1800,1340
    gallery=Image.new('RGBA',(width,height),(9,10,19,255))
    draw=ImageDraw.Draw(gallery)
    font=ImageFont.load_default(size=22);small=ImageFont.load_default(size=15)
    titles={'verdant':'VERDANT / DRAGON SEAL + FORWARD BEAM',
            'lunar':'LUNAR / SIXTEEN LARGE MOON PORTALS',
            'crimson':'CRIMSON OBLIVION / DOOMSDAY HEART'}
    heroes={'verdant':'verdant_dragon','lunar':'lunar_crescent','crimson':'crimson_heart'}
    accents={'verdant':'verdant_beam_core','lunar':'lunar_orbit','crimson':'crimson_shard'}
    for col,theme in enumerate(('verdant','lunar','crimson')):
        x=col*600
        draw.rounded_rectangle((x+14,14,x+586,height-15),radius=20,
                               fill=(15,20,29,255),outline=rgb({'verdant':JADE,'lunar':BLUE,'crimson':RUBY}[theme]),width=1)
        draw.text((x+34,33),titles[theme],font=small,fill=IVORY)
        gallery.alpha_composite(images[heroes[theme]].resize((540,540),Image.Resampling.LANCZOS),(x+30,66))
        label='01  ORIGINAL CRIMSON HERO / PRESERVED' if theme=='crimson' else '01  ENGRAVED CELESTIAL PORTAL / CLEAR HERO'
        draw.text((x+34,616),label,font=small,fill=SILVER)
        gallery.alpha_composite(images[theme+'_seal'].resize((470,470),Image.Resampling.LANCZOS),(x+65,664))
        draw.text((x+34,1132),'02  COORDINATED SEAL / WORLD PLANE',font=small,fill=SILVER)
        gallery.alpha_composite(images[accents[theme]].resize((165,165),Image.Resampling.LANCZOS),(x+26,1161))
        draw.text((x+203,1190),accents[theme].replace('_',' ').upper(),font=small,fill=IVORY)
        draw.text((x+203,1214),'Transparent PNG + editable SVG',font=small,fill=SILVER)
        draw.text((x+203,1238),'Same authored art: Java / Bedrock',font=small,fill=SILVER)
    gallery.save(ART/'gallery.png',optimize=True)
    companions=Image.new('RGBA',(1700,890),(7,13,23,255));companion_draw=ImageDraw.Draw(companions)
    for col,name,title in ((0,'verdant_halo','VERDANT / SOVEREIGN GATE + TRAVELLING SHOCK RING'),
                           (1,'lunar_constellation','LUNAR / CELESTIAL FLOOR ALTAR')):
        x=col*850
        companions.alpha_composite(images[name].resize((790,790),Image.Resampling.LANCZOS),(x+30,46))
        companion_draw.text((x+28,15),title,font=small,fill=IVORY)
        companion_draw.text((x+28,850),'Authored transparent plane / artwork preview, not game footage',font=small,fill=SILVER)
    companions.save(ART/'companion-gallery.png',optimize=True)
    preview_readability(images);preview_layout(images)
    # A web animation is deliberately an artwork storyboard, not a gameplay claim.
    cards=[]
    for theme in ('verdant','lunar','crimson'):
        if theme=='verdant':
            cards.append(f'''<article class="verdant"><h2>{titles[theme]}</h2><div class="stage">
<img class="dragon-portal" src="verdant_dragon.png" alt="Complete emerald dragon seal behind caster">
<img class="sovereign-halo" src="verdant_halo.png" alt="Separate rotating royal green gate">
<div class="caster"><i></i></div><div class="forward-beam"></div></div>
<p>Engraved dragon seal behind the caster, framed by an independently moving royal gate. The forward beam has a narrow hot axis and transparent green shoulders; its far end radiates light. No crown occupies the caster's view.</p></article>''')
            continue
        if theme=='lunar':
            portals=''.join(f'<div class="moon-node" style="--n:{i}"><div class="down-beam"></div><img src="lunar_seal.png" alt="Independent silver moon portal {i+1}"></div>' for i in range(16))
            cards.append(f'''<article class="lunar"><h2>{titles[theme]}</h2><div class="stage moon-stage"><img class="constellation-altar" src="lunar_constellation.png" alt="Separate celestial floor altar"><div class="sky-grid">{portals}</div></div>
<p>Sixteen separate engraved moon portals in a 4×4 sky grid, each firing a narrow 1.1m downward beam above a detailed constellation altar. Exact phases and scale come from the runtime.</p></article>''')
            continue
        cards.append(f'''<article class="{theme}"><h2>{titles[theme]}</h2><div class="stage">
<img class="seal" src="{theme}_seal.png" alt="{theme} rune seal">
<img class="accent" src="{accents[theme]}.png" alt="{theme} crown or orbit">
<img class="hero" src="{heroes[theme]}.png" alt="{theme} hero engraving"></div>
<p>Preserved ruby invocation and crystal opening. The runtime finishes with eight separate shards entering the middle one by one, producing one impact each.</p></article>''')
    page='''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Legendary spell artwork storyboard</title><style>
*{box-sizing:border-box}body{margin:0;background:#070b12;color:#e4efff;font:16px system-ui;padding:30px;max-width:1900px;margin:auto}
h1{font-size:30px;letter-spacing:.05em}p{color:#a9b8ce;font-size:13px}main{display:grid;grid-template-columns:repeat(3,1fr);gap:18px}article{border:1px solid #284442;border-radius:20px;background:#0c1420;padding:20px;overflow:hidden}h2{font-size:14px;font-weight:600;letter-spacing:.06em} .stage{position:relative;aspect-ratio:1 / 1.22;perspective:1000px;background:radial-gradient(ellipse at 50% 75%,#1c263b00,#0c1420)}img{position:absolute;object-fit:contain;pointer-events:none}.seal{width:100%;height:80%;left:0;bottom:-10%;transform:rotateX(65deg);animation:seal 10s infinite}.hero{width:96%;height:88%;left:2%;top:0;animation:hero 10s infinite}.accent{width:80%;height:65%;left:10%;top:5%;opacity:.65;animation:accent 10s infinite}.verdant .accent{width:35%;height:30%;top:-4%;left:32%}.crimson .accent{width:35%;height:75%;top:8%;left:63%}.lunar{border-color:#314b7a}.crimson{border-color:#73233d}@keyframes seal{0%{opacity:0;transform:rotateX(65deg) rotateZ(0deg) scale(.3)}30%,65%{opacity:1;transform:rotateX(65deg) rotateZ(60deg) scale(1)}85%{opacity:1;transform:rotateX(65deg) rotateZ(120deg) scale(1.12)}100%{opacity:0;transform:rotateX(65deg) rotateZ(170deg) scale(1.3)}}@keyframes hero{0%,15%{opacity:0;transform:translateY(20%) scale(.4)}35%,60%{opacity:1;transform:translateY(-2%) scale(1)}78%{opacity:1;transform:translateY(13%) scale(.7)}85%,100%{opacity:0;transform:translateY(25%) scale(.5)}}@keyframes accent{0%,10%{opacity:0;transform:rotate(0deg) scale(.4)}30%,65%{opacity:.7;transform:rotate(8deg) scale(1)}90%,100%{opacity:0;transform:rotate(28deg) scale(1.2)}}@media(max-width:1100px){main{grid-template-columns:1fr}.stage{max-height:680px}}@media(prefers-reduced-motion:reduce){img{animation:none!important}.hero{opacity:1}}
.dragon-portal{left:0;top:18%;width:74%;height:62%;animation:invoke 9s infinite}.caster{position:absolute;width:7%;height:22%;left:45%;top:51%;background:#799b91;border-radius:40% 40% 10% 10%;z-index:4}.caster i{position:absolute;left:0;top:-22%;width:100%;aspect-ratio:1;background:#c4dad1;border-radius:50%}.forward-beam{position:absolute;left:49%;top:45%;width:64%;height:11%;background:linear-gradient(0deg,transparent,#1fcf71,#ecfff1,#1fcf71,transparent);box-shadow:0 0 26px #3fff8b70;transform-origin:left;animation:forward 9s infinite}.sky-grid{position:absolute;left:0;top:8%;width:100%;height:75%;display:grid;grid-template-columns:repeat(4,1fr);gap:7% 8%;perspective:900px}.moon-node{position:relative;aspect-ratio:1;min-width:0}.moon-node img{position:absolute;z-index:2;left:0;top:0;width:100%;height:100%;transform:rotateX(50deg);animation:invoke 9s infinite;animation-delay:calc(var(--n)*.025s)}.down-beam{position:absolute;left:44%;top:55%;width:12%;height:80%;background:linear-gradient(90deg,transparent,#96d9ff,#f2ffff,#96d9ff,transparent);box-shadow:0 0 15px #70bfff75;transform-origin:top;animation:downward 9s infinite}@keyframes invoke{0%,5%{opacity:0}24%,82%{opacity:1}100%{opacity:0}}@keyframes forward{0%,30%{opacity:0;transform:scaleX(0)}42%,76%{opacity:1;transform:scaleX(1)}90%,100%{opacity:0;transform:scaleX(1)}}@keyframes downward{0%,32%{opacity:0;transform:scaleY(0)}46%,78%{opacity:1;transform:scaleY(1)}95%,100%{opacity:0;transform:scaleY(1)}}@media(prefers-reduced-motion:reduce){.forward-beam,.down-beam,.dragon-portal,.moon-node img{animation:none!important;opacity:1}}
.sovereign-halo{left:-5%;top:12%;width:84%;height:72%;animation:royal 14s linear infinite;opacity:.7}.constellation-altar{left:-10%;bottom:-11%;width:120%;height:61%;transform:rotateX(65deg);animation:altar 22s linear infinite;opacity:.7}@keyframes royal{to{transform:rotate(-360deg)}}@keyframes altar{to{transform:rotateX(65deg) rotateZ(360deg)}}@media(prefers-reduced-motion:reduce){.sovereign-halo,.constellation-altar{animation:none!important}}
</style><h1>Legendary spell artwork</h1><p>Authored asset / cinematic storyboard preview. This animation is a concept preview, not footage captured in Minecraft. Exact gameplay timing comes from the spell runtime.</p><main>'''+''.join(cards)+'</main></html>'
    (ART/'storyboard.html').write_text(page,encoding='utf8')


def main():
    ART.mkdir(parents=True,exist_ok=True)
    preservation=ART/'crimson-preservation-before.json'
    if not preservation.exists():
        crimson_files=[p for p in sorted(ART.glob('crimson_*')) if p.suffix in ('.png','.svg')]
        preservation.write_text(json.dumps({p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in crimson_files},indent=2)+'\n',encoding='utf8')
    before=json.loads(preservation.read_text(encoding='utf8'))
    drawings={'verdant_seal':seal('verdant'),'verdant_dragon':dragon_portal(),'verdant_beam_core':beam_core(),
              'verdant_halo':verdant_halo(),
              'lunar_seal':seal('lunar'),'lunar_crescent':moon_portal(),'lunar_orbit':moon_orbit_quiet(),
              'lunar_constellation':lunar_constellation(),
              'crimson_seal':seal('crimson'),'crimson_heart':heart(),'crimson_shard':shard()}
    images={}
    for name,drawing in drawings.items():
        if name.startswith('crimson_') and all((ART/f'{name}.{ext}').exists() for ext in ('png','svg')):
            images[name]=Image.open(ART/f'{name}.png').convert('RGBA')
            print(f'{name}: preserved original PNG/SVG bytes')
            continue
        image,svg=drawing.render();image=shipping_image(image);images[name]=image
        image.save(ART/f'{name}.png',optimize=True)
        (ART/f'{name}.svg').write_text(svg,encoding='utf8')
        print(f'{name}: {len(drawing.shapes)} authored shapes')
    for name,color,secondary in (('verdant_ray',EMERALD,JADE),('lunar_ray',BLUE,LILAC),('crimson_ray',CRIMSON,RUBY)):
        if name.startswith('crimson_') and all((ART/f'{name}.{ext}').exists() for ext in ('png','svg')):
            print(f'{name}: preserved original PNG/SVG bytes')
            continue
        image,svg=ribbon(color,secondary) if color==CRIMSON else verdant_ribbon() if color==EMERALD else bright_ribbon(color)
        image=shipping_image(image)
        image.save(ART/f'{name}.png',optimize=True)
        (ART/f'{name}.svg').write_text(svg,encoding='utf8')
    after={name:hashlib.sha256((ART/name).read_bytes()).hexdigest() for name in before}
    assert after==before,'Crimson original art must remain byte-identical'
    (ART/'crimson-preservation-after.json').write_text(json.dumps(after,indent=2)+'\n',encoding='utf8')
    preview(images)
    (ART/'README.md').write_text('''# Original legendary spell engravings

Regenerate PNG, editable SVG and the gallery with `python tools/legendary_effect_art.py` from the repository root. Requires Pillow, matching `tools/mythic_line_art.py`.

The eleven hero/seal assets are transparent RGBA 1024×1024. The three continuous axial ribbons are 256×256. Every image has a matching editable SVG. The source has original geometry and custom geometric glyphs; it uses no external image, font or artwork. Shipped PNG channels are rounded to steps of eight (maximum error four), reducing glow download size without changing resolution, geometry or RGBA mode. Editable SVG retains full-precision colours.

`gallery.png` is an artwork contact sheet. `companion-gallery.png` shows the clear-centre Verdant halo and the Lunar constellation altar. `readability-32px.png` checks real 32px thumbnails with nearest-neighbour enlargement. `scene-layout.png` and `storyboard.html` show a dragon sigil behind the caster and a forward emerald beam, with an open near mouth and a distant radiant light. Sixteen separate moon portals form a 4x4 sky grid, each facing the captured casting eye and firing a thin 1.1m downward ray. Crimson retains its opening and finishes with eight individual inward impacts. All previews are artwork/concept illustrations, **not Minecraft footage**. Actual timing, world size and animation are defined by the spell runtime.

Java world planes have two visible faces; ribbons use crossed faces. Runtime-owned full-bright ItemDisplays supply brightness. Bedrock receives identical textures via short-lived world-space particles, with a dedicated XZ basis for horizontal seals. All centres are (512,512); the dragon is front-facing with horns, a large jaw, eyes, claws, wings and a curved plated body; the broad filled silver crescent opens right; the shard's tip points upward (negative image Y). Six concentric celestial bands, calibrated ticks, original rune script and separate fluted ornaments frame the dragon/moon without obscuring them. The moon includes engraved craters and an independent small astrolabe in its open side. Verdant's narrow white-hot beam core has transparent emerald shoulders and four fine braided filaments rather than broad opaque white sheets; its distant end glows as a radiant starburst. Lunar rays are thin in world space. Verdant keeps its near mouth open so the distant light cannot obscure the casting camera. Crimson keeps its opening and uses eight separate inward shard impacts at the end. The heart is a faceted ruby/obsidian crystal rather than an anatomical heart.

`verdant_halo` keeps all shape geometry outside radius330, with soft bloom feathering into that margin and a fully transparent central disk inside radius290; it supplies an outer royal gate or travelling ring without a crown. `lunar_constellation` uses annular star geometry, twelve small authored constellations and a quiet central aperture instead of another giant moon. These two companion planes let the runtime animate secondary structures independently from its fixed readable hero portals, following the layered Solar/Chronos celestial drawing style.

Palette: emerald 58FFB1 / jade 12B76A; moon silver D8F3FF / blue 72BFFF; crimson FF315B / ruby A41B52. Ivory and aged-gold fine inlays preserve the celestial style inside the new bold filled hero silhouettes.

The four Crimson PNG/SVG pairs are preserved byte-for-byte during regeneration. Compare `crimson-preservation-before.json` with `crimson-preservation-after.json`: all eight SHA-256 values must match.
''',encoding='utf8')
    print(ART/'gallery.png')


if __name__=='__main__':
    main()
