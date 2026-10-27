"""Compare actual old/new hotbar PNGs; this is an asset comparison, not a screenshot."""
import io
from pathlib import Path
import zipfile
from PIL import Image,ImageDraw,ImageFont

ROOT=Path(__file__).resolve().parents[1]
NAMES=('excalibur','longinus','tyrfing','mjolnir','vajra')
font=lambda n:ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf',n)
sheet=Image.new('RGB',(1500,620),'#0b1220');d=ImageDraw.Draw(sheet)
d.text((30,20),'HOTBAR ICONS / SAME SLOT SIZE / 1.9.0 → 1.9.1',font=font(28),fill='#eaddb7')
with zipfile.ZipFile(ROOT/'dist/legendary-weapons-1.9.0.zip') as old:
    for i,name in enumerate(NAMES):
        x=i*300
        d.text((x+30,85),name.upper(),font=font(22),fill='#8cdaff')
        before=Image.open(io.BytesIO(old.read(f'advance-magic/art/legendary-weapons/{name}_icon.png'))).convert('RGBA')
        after=Image.open(ROOT/f'advance-magic/art/legendary-weapons/{name}_icon.png').convert('RGBA')
        for im,y,label in ((before,145,'BEFORE'),(after,350,'AFTER')):
            d.text((x+25,y-30),label,font=font(17),fill='#98aac2')
            d.rectangle((x+25,y,x+155,y+130),fill='#272f3e',outline='#9ba4b4',width=2)
            sheet.paste(im,(x+27,y+2),im)
            # Actual 16px HUD size enlarged with nearest pixels, same for both.
            small=im.resize((16,16),Image.Resampling.LANCZOS).resize((64,64),Image.Resampling.NEAREST)
            d.rectangle((x+193,y+30,x+263,y+100),fill='#272f3e',outline='#9ba4b4',width=2)
            sheet.paste(small,(x+197,y+34),small)
d.text((30,555),'128px assets at left; 16px hotbar samples (4×) at right. Uniform scale, shared frame crop.',font=font(19),fill='#a9bad0')
d.text((30,587),'Asset preview only. Native first/third-person grip still needs confirmation on the game clients.',font=font(16),fill='#778da8')
out=ROOT/'previews/legendary-weapons/grip-fix-icons.png';sheet.save(out);print(out)
