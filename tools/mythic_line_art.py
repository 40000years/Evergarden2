"""Editable celestial line art for Solar and Chronos, matching Judgment's glow."""
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'advance-magic/art/effects'
GOLD, IVORY, ORANGE = '#ffd640', '#fff7b9', '#ff923d'
CYAN, VIOLET = '#72edff', '#b18aff'


class Drawing:
    def __init__(self):
        self.shapes = []

    def circle(self, radius, width=3, color=GOLD):
        self.shapes.append(('circle', radius, width, color))

    def path(self, points, width=3, color=GOLD):
        self.shapes.append(('path', points, width, color))

    @staticmethod
    def polar(angle, radius):
        return (512 + math.cos(angle)*radius, 512 + math.sin(angle)*radius)

    def star(self, count, step, radius, turn=0, width=3, color=GOLD):
        points = [self.polar(turn+math.tau*(i*step % count)/count, radius) for i in range(count)]
        self.path(points+[points[0]], width, color)

    def render(self):
        image = Image.new('RGBA', (1024, 1024))
        svg = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">']
        for extra, alpha in ((15,14), (8,32), (3,75), (0,255)):
            layer = Image.new('RGBA', image.size)
            draw = ImageDraw.Draw(layer)
            svg.append(f'<g fill="none" stroke-linecap="round" stroke-linejoin="round" opacity="{alpha/255:.4f}">')
            for kind, data, width, color in self.shapes:
                rgb = tuple(int(color[i:i+2],16) for i in (1,3,5))
                if kind == 'circle':
                    r = data
                    draw.ellipse((512-r,512-r,512+r,512+r), outline=(*rgb,alpha), width=width+extra)
                    svg.append(f'<circle cx="512" cy="512" r="{r}" stroke="{color}" stroke-width="{width+extra}"/>')
                else:
                    draw.line(data, fill=(*rgb,alpha), width=width+extra, joint='curve')
                    points = ' '.join(f'{x:.2f},{y:.2f}' for x,y in data)
                    svg.append(f'<polyline points="{points}" stroke="{color}" stroke-width="{width+extra}"/>')
            image = Image.alpha_composite(image, layer)
            svg.append('</g>')
        svg.append('</svg>')
        return image, '\n'.join(svg)+'\n'


def solar_corona():
    d = Drawing()
    for r,w,c in ((398,5,IVORY),(388,2,GOLD),(343,3,ORANGE),(330,2,IVORY),
                  (247,4,GOLD),(236,2,IVORY),(148,3,ORANGE),(132,2,GOLD)):
        d.circle(r,w,c)
    # Long pointed flares and curved filaments form a sun, rather than another clock.
    for i in range(24):
        a = math.tau*i/24
        peak = 478 if i%2==0 else 446
        d.path([d.polar(a-.035,402),d.polar(a,peak),d.polar(a+.035,402)],3,GOLD)
        d.path([d.polar(a-.085,351),d.polar(a-.045,371),d.polar(a+.02,381),
                d.polar(a+.07,368)],2,ORANGE)
        d.path([d.polar(a,253),d.polar(a-.025,277),d.polar(a,309),d.polar(a+.025,277),
                d.polar(a,253)],2,IVORY)
    d.star(12,5,326,math.pi/12,3,IVORY)
    d.star(8,3,233,0,2,GOLD)
    for i in range(8):
        a = math.tau*i/8
        d.path([d.polar(a-.09,156),d.polar(a,209),d.polar(a+.09,156)],3,GOLD)
    d.circle(70,3,IVORY)
    d.star(4,1,110,math.pi/4,3,GOLD)
    return d


def orbit(chronos=False):
    d = Drawing()
    d.circle(474,4,CYAN if chronos else GOLD)
    d.circle(465,2,VIOLET if chronos else IVORY)
    if chronos:
        d.circle(435,2,VIOLET)
        for i in range(24):
            a=math.tau*i/24
            d.path([d.polar(a-.01,440),d.polar(a,456),d.polar(a+.01,440)],2,CYAN)
    return d


def chronos_dial():
    d = Drawing()
    for r,w,c in ((480,5,GOLD),(469,2,IVORY),(433,3,CYAN),(424,2,VIOLET),
                  (350,3,GOLD),(339,2,IVORY),(285,3,VIOLET),(273,2,CYAN),
                  (206,3,GOLD),(190,2,CYAN),(65,3,IVORY),(50,2,VIOLET)):
        d.circle(r,w,c)
    for i in range(60):
        a = math.tau*i/60-math.pi/2
        d.path([d.polar(a,439 if i%5==0 else 450),d.polar(a,463)],
               4 if i%5==0 else 2, CYAN if i%15==0 else GOLD)
    # Roman numerals use authored strokes; they remain editable in the SVG.
    numerals = ('XII','I','II','III','IV','V','VI','VII','VIII','IX','X','XI')
    letters = {'I':[[(0,-15),(0,15)],[(-6,-15),(6,-15)],[(-6,15),(6,15)]],
               'V':[[(-8,-15),(0,15),(8,-15)]],
               'X':[[(-8,-15),(8,15)],[(8,-15),(-8,15)]]}
    for i, word in enumerate(numerals):
        a = math.tau*i/12-math.pi/2
        radial=(math.cos(a),math.sin(a)); tangent=(-radial[1],radial[0])
        for j,letter in enumerate(word):
            x=(j-(len(word)-1)/2)*20
            for stroke in letters[letter]:
                d.path([(512+radial[0]*(388-y)+tangent[0]*(x+sx),
                         512+radial[1]*(388-y)+tangent[1]*(x+sx)) for sx,y in stroke],3,IVORY)
        # Curving filigree beneath each hour number.
        d.path([d.polar(a-.12,309),d.polar(a-.06,319),d.polar(a,300),
                d.polar(a+.06,319),d.polar(a+.12,309)],2,CYAN)
    # Interlocking escapement gears and an hourglass in the central mechanism.
    gear=[]
    for i in range(36):
        for fraction,r in ((0,213),(.2,229),(.65,229),(.85,213)):
            gear.append(d.polar(math.tau*(i+fraction)/36,r))
    d.path(gear+[gear[0]],3,VIOLET)
    d.star(8,3,266,math.pi/8,2,CYAN)
    for i in range(4):
        a=math.tau*i/4+math.pi/4
        d.path([d.polar(a-.07,76),d.polar(a,158),d.polar(a+.07,76)],2,GOLD)
    d.path([(462,92+320),(562,92+320),(542,492),(482,532),(462,612),
            (562,612),(542,532),(482,492),(462,412)],3,IVORY)
    return d


def hand(long=True):
    d=Drawing()
    color=CYAN if long else VIOLET
    end=400 if long else 254
    d.path([(454,512),(512,501),(512+end-44,507),(512+end,512),
            (512+end-44,517),(512,523),(454,512)],3,color)
    d.path([(506,512),(512+end-38,512)],2,IVORY)
    d.path([(512+end-65,499),(512+end-25,512),(512+end-65,525)],3,color)
    d.circle(19,3,IVORY)
    d.path([(434,512),(454,501),(474,512),(454,523),(434,512)],2,color)
    return d


def ray(color):
    # A continuous white-hot axial ribbon, shared verbatim between Java and Bedrock.
    image=Image.new('RGBA',(256,256));pixels=image.load()
    rgb=tuple(int(color[i:i+2],16) for i in (1,3,5))
    for x in range(256):
        distance=abs(x-127.5)/127.5
        alpha=int(255*max(0,1-distance)**1.65)
        core=max(0,1-distance*5)
        tint=tuple(int(v+(255-v)*core) for v in rgb)
        for y in range(256):pixels[x,y]=(*tint,alpha)
    return image


def main():
    ART.mkdir(parents=True,exist_ok=True)
    drawings={'solar_corona':solar_corona(),'solar_orbit':orbit(),
              'chronos_dial':chronos_dial(),'chronos_minute':hand(),
              'chronos_hour':hand(False),'chronos_ripple':orbit(True)}
    rendered={}
    for name,drawing in drawings.items():
        image,svg=drawing.render();rendered[name]=image
        image.save(ART/f'{name}.png');(ART/f'{name}.svg').write_text(svg,encoding='utf8')
    for name,color in (('solar_ray',GOLD),('chronos_ray',CYAN),('chronos_echo',VIOLET)):
        ray(color).save(ART/f'{name}.png')
    preview=Image.new('RGBA',(1600,840),(12,9,24,255))
    for x,name,title in ((20,'solar_corona','SOLAR / CELESTIAL CORONA'),
                         (820,'chronos_dial','CHRONOS / FINAL HOUR')):
        img=rendered[name]
        if name=='chronos_dial':
            img=Image.alpha_composite(img,rendered['chronos_minute'].rotate(62))
            img=Image.alpha_composite(img,rendered['chronos_hour'].rotate(-35))
        preview.alpha_composite(img.resize((760,760),Image.Resampling.LANCZOS),(x,45))
        ImageDraw.Draw(preview).text((x+160,16),title,fill='#fff7b9',font=ImageFont.load_default(size=20))
    path=ROOT/'previews/solar-chronos-line-art.png'
    preview.save(path);print(path)


if __name__=='__main__':main()
