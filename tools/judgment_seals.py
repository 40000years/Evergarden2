"""Author continuous golden sigils as editable SVG and deterministic game textures."""
import math
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SIZE = 1024


def geometry(variant):
    shapes = []
    def circle(radius, width, ivory=False):
        shapes.append(('circle', radius, width, ivory))
    def path(points, width=3, ivory=False):
        shapes.append(('path', points, width, ivory))
    def polar(angle, radius):
        return (512 + math.cos(angle)*radius, 512 + math.sin(angle)*radius)
    for radius, width, ivory in ((478,5,False),(467,2,True),(402,4,False),
                                  (390,2,True),(302,4,False),(289,2,True),(178,4,False),(164,2,True)):
        circle(radius,width,ivory)
    # Twenty-four radial glyphs, with distinct branched strokes and diamond ends.
    for i in range(24):
        angle=math.tau*i/24
        radial=(math.cos(angle),math.sin(angle));tangent=(-radial[1],radial[0])
        def local(x,y):
            return (512+radial[0]*(434+y)+tangent[0]*x,512+radial[1]*(434+y)+tangent[1]*x)
        path([local(0,-19),local(0,19)],4)
        branches=((-10,-10),(10,3),(-10,12)) if (i+variant)%2 else ((10,-10),(-10,3),(10,12))
        for x,y in branches:path([local(0,y-7),local(x,y),local(0,y+7)],3)
        if i%3==0:path([local(0,23),local(5,28),local(0,33),local(-5,28),local(0,23)],2,True)
    # Two counterposed triangles frame a six-point star and an interlaced octagram.
    for turn in (0,math.pi):
        points=[polar(turn+math.tau*i/3+variant*.12,382) for i in range(3)]
        path(points+[points[0]],4,True)
    points=[polar(math.tau*i/8+math.pi/8,292) for i in range(8)]
    for start in range(2):
        order=[points[(start+3*i)%8] for i in range(8)]
        path(order+[order[0]],2)
    # Eight outward arrows and four compact compass diamonds.
    for i in range(8):
        angle=math.tau*i/8
        path([polar(angle-.08,310),polar(angle,368),polar(angle+.08,310)],3)
    for i in range(4):
        angle=math.tau*i/4
        path([polar(angle,184),polar(angle+.055,214),polar(angle,246),polar(angle-.055,214),polar(angle,184)],3,True)
    circle(122,3)
    # Intentionally leave the beam aperture open.
    return shapes


def render(variant):
    shapes=geometry(variant)
    image=Image.new('RGBA',(SIZE,SIZE))
    svg=['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024">',
         '<defs><filter id="glow" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="3"/></filter></defs>']
    # Broad transparent strokes supply a restrained halo; crisp inner lines carry the pattern.
    for extra,alpha in ((15,14),(8,32),(3,75),(0,255)):
        layer=Image.new('RGBA',image.size);draw=ImageDraw.Draw(layer)
        svg.append(f'<g fill="none" stroke-linecap="round" stroke-linejoin="round" opacity="{alpha/255:.4f}">')
        for kind,data,width,ivory in shapes:
            color=(255,247,185,alpha) if ivory and not extra else (255,214,64,alpha)
            stroke='#fff7b9' if ivory and not extra else '#ffd640'
            if kind=='circle':
                r=data;draw.ellipse((512-r,512-r,512+r,512+r),outline=color,width=width+extra)
                svg.append(f'<circle cx="512" cy="512" r="{r}" stroke="{stroke}" stroke-width="{width+extra}"/>')
            else:
                draw.line(data,fill=color,width=width+extra,joint='curve')
                points=' '.join(f'{x:.2f},{y:.2f}' for x,y in data)
                svg.append(f'<polyline points="{points}" stroke="{stroke}" stroke-width="{width+extra}"/>')
        image=Image.alpha_composite(image,layer);svg.append('</g>')
    svg.append('</svg>')
    return image,'\n'.join(svg)+'\n'


def main():
    folder=ROOT/'advance-magic/art/effects';folder.mkdir(parents=True,exist_ok=True)
    for variant in range(2):
        image,svg=render(variant)
        name=f'judgment_seal_{variant}'
        image.save(folder/f'{name}.png')
        (folder/f'{name}.svg').write_text(svg,encoding='utf8')
        if variant==0:
            preview=ROOT/'previews/heavens-judgment-seal.png';preview.parent.mkdir(parents=True,exist_ok=True)
            Image.alpha_composite(Image.new('RGBA',image.size,(12,9,24,255)),image).save(preview)
        print(folder/f'{name}.png')


if __name__=='__main__':main()
