"""Render the exact pack geometry as a PNG (Pillow; no Minecraft screenshot)."""
import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import build_wrath_pack as model

model.geometry()
size = 1200, 1000
scale = 2
image = Image.new('RGB', (size[0]*scale, size[1]*scale), '#0b0d15')
draw = ImageDraw.Draw(image)
for r in range(430, 0, -2):
    a = 1-r/430
    color = tuple(int(start+a*(end-start)) for start,end in zip((11,13,21),(64,27,32)))
    draw.ellipse(((600-r)*scale,(480-r)*scale,(600+r)*scale,(480+r)*scale), fill=color)
draw.ellipse((325*scale,825*scale,915*scale,905*scale),fill='#07080e')
for depth, vertices, color, material in model.model_faces():
    coords=[((600+x*180)*scale,(845-y*180+z*50)*scale) for x,y,z in vertices]
    draw.polygon(coords,fill=color)
    draw.line(coords+[coords[0]],fill='#12101a',width=scale)
try:
    font=ImageFont.truetype('C:/Windows/Fonts/arialbd.ttf',64*scale)
    small=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',19*scale)
except OSError:
    font=small=ImageFont.load_default()
draw.text((58*scale,38*scale),'WRATH',fill='#f07756',font=font)
draw.text((62*scale,117*scale),'THE ASHEN EXECUTIONER',fill='#c5afb4',font=small)
draw.text((62*scale,923*scale),'7SINS  /  ORIGINAL IN-GAME CUBE GEOMETRY',fill='#b9a3a7',font=small)
draw.text((62*scale,953*scale),'Simulated lighting. Model preview, not a Minecraft screenshot.',fill='#817580',font=small)
path=model.ROOT/'art/wrath-preview.png'
image.resize(size,Image.Resampling.LANCZOS).save(path)
print(path)
