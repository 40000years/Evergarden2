"""Render the authored 3D staff geometry and native VFX into a review gallery.

These are deterministic asset renders, not screenshots from a game client.
"""
from pathlib import Path
import math
import json
from PIL import Image, ImageDraw, ImageFont
import elemental_staff_assets as assets
import legendary_weapon_preview as renderer
import legendary_weapon_assets as legacy

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'previews/elemental-staffs'


def font(size,bold=False):
    return ImageFont.truetype('C:/Windows/Fonts/'+('segoeuib.ttf' if bold else 'segoeui.ttf'),size)


def render(name,frame=0,size=600,yaw=22,tilt=-9,elevation=8,effects=True):
    # Use the shared textured z-buffer renderer with this catalog's atlas tiles.
    previous_tile,previous_emissive=legacy.tile,legacy.EMISSIVE
    legacy.tile,legacy.EMISSIVE=assets.tile,assets.EMISSIVE
    try:
        model=assets.make_staff(name)
        return renderer.render_item(model,assets.atlas(),assets.fx_frame(name,frame) if effects else None,
            size=size,yaw=yaw,tilt=tilt,elevation=elevation)
    finally:
        legacy.tile,legacy.EMISSIVE=previous_tile,previous_emissive


def make_previews():
    OUT.mkdir(parents=True,exist_ok=True)
    width,height=2240,1500
    sheet=Image.new('RGBA',(width,height),(9,12,21,255));d=ImageDraw.Draw(sheet)
    d.text((60,32),'E V E R G A R D E N   /   E L E M E N T A L   A R M O U R Y',font=font(18),fill='#9caabc')
    d.text((55,65),'EIGHT ELEMENTAL STAFFS',font=font(56,True),fill='#f4e5c5')
    d.text((60,138),'FIRE > WIND > EARTH > ICE > WATER > FIRE     /     LIGHTNING > LIGHT > DARK > LIGHTNING',font=font(18),fill='#a9b8cf')
    subtitles=('EMBER CROWN','SPIRAL OF GALES','VERDANT MONOLITH','FROZEN CONSTELLATION',
               'TIDAL CRESCENT','STORM CONDUCTOR','SOLAR SANCTUM','ECLIPSE THORN')
    for i,name in enumerate(assets.IDS):
        x=40+(i%4)*550;y=190+(i//4)*620;color=assets.COLORS[i]
        d.rounded_rectangle((x,y,x+530,y+600),radius=12,fill='#0e1523',outline=tuple(color)+(170,),width=2)
        d.rectangle((x+2,y+2,x+528,y+5),fill=tuple(color))
        d.text((x+22,y+18),f'{i+1:02d} / '+assets.TITLES[i].upper(),font=font(30,True),fill=tuple(color))
        d.text((x+24,y+60),subtitles[i],font=font(14),fill='#9caec5')
        item=render(name,frame=2,size=500,yaw=24,tilt=-7)
        renderer.glow_composite(sheet,item,(x+15,y+78),tuple(color))
        solid=render(name,size=140,yaw=0,tilt=0,elevation=0,effects=False)
        sheet.alpha_composite(solid,(x+372,y+430))
        d=ImageDraw.Draw(sheet)
        d.text((x+25,y+566),'NATIVE 3D  /  ANIMATED VFX  /  JAVA + BEDROCK',font=font(12),fill='#a5b1c3')
        render(name,frame=2,size=900,yaw=26,tilt=-8).save(OUT/f'{name}.png')
    d.text((60,1450),'Rendered from the exported cuboids and textures. Presentation bloom is illustrative; verify camera framing in game.',font=font(17),fill='#8f9db2')
    sheet.convert('RGB').save(OUT/'elemental-staffs.png')
    frames=[]
    for frame in range(16):
        image=Image.new('RGBA',(1600,900),(9,12,21,255));draw=ImageDraw.Draw(image)
        draw.text((35,20),'ELEMENTAL STAFFS / GEOMETRY + VFX',font=font(24,True),fill='#e9d9b7')
        for i,name in enumerate(assets.IDS):
            x=(i%4)*400;y=65+(i//4)*405
            model=render(name,frame%8,size=385,yaw=22+8*math.sin(frame*math.tau/16),tilt=-7)
            renderer.glow_composite(image,model,(x+7,y-3),tuple(assets.COLORS[i]))
            draw=ImageDraw.Draw(image);draw.text((x+25,y+356),assets.TITLES[i].upper(),font=font(23,True),fill=tuple(assets.COLORS[i]))
        frames.append(image.convert('RGB'))
    frames[0].save(OUT/'elemental-staffs.gif',save_all=True,append_images=frames[1:],duration=120,loop=0,optimize=False)
    cards=''.join(f'<article><h2>{name.title()}</h2><img src="{name}.png" alt="{name} staff 3D asset render"></article>' for name in assets.IDS)
    (OUT/'index.html').write_text('''<!doctype html><html lang="th"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Evergarden | Elemental Staffs</title><style>body{margin:0;background:#090c15;color:#d1def0;font:16px system-ui}main{max-width:1500px;margin:auto;padding:24px}img{max-width:100%;height:auto}a{color:#7bdcff}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:16px}article{background:#101827;border:1px solid #33435e;border-radius:12px;padding:18px}p{line-height:1.7}</style><main><h1>คทาธาตุใหม่ทั้ง 8</h1><p>Fire → Wind → Earth → Ice → Water → Fire<br>Lightning → Light → Dark → Lightning</p><img src="elemental-staffs.png" alt="Eight elemental staffs"><img src="elemental-staffs.gif" alt="Animated elemental VFX"><p>ภาพเรนเดอร์จากโมเดลและเท็กซ์เจอร์ที่ส่งออกจริง พร้อมแสงประกอบ เป็นพรีวิว asset ต้องตรวจตำแหน่งถือบนไคลเอนต์อีกครั้ง รอบนี้ทำโมเดลก่อน ระบบหลายสกิลยังไม่ถูกผูกลงไป</p><p>ลองในเกมด้วย <code>/magic staffs</code> หรือ <code>/magic staff give all</code></p><div class="grid">'''+cards+'</div></main>',encoding='utf8')
    print('Gallery:',OUT/'index.html',flush=True)


if __name__=='__main__':make_previews()
