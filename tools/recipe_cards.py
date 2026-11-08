"""Render the staff crafting and mana potion brewing guides as PNG cards for players.

Facts mirror Advance Magic: ElementalAcquisitionService (wooden staff recipe),
StaffSpellService.Tier/upgradeCost (/staff upgrade) and ManaPotionService (brewing,
quality odds, restore amounts, nausea and max-mana rules). Update both together.

Textures come from the released dist/advance-magic.jar pack and the local 26.3 client
jar. Pillow on this machine has no Thai shaping (raqm), so the cards are laid out as
HTML and captured with headless Edge.

    python tools/recipe_cards.py            -> docs/recipes/*.png
"""
import base64
import io
import subprocess
import zipfile
from pathlib import Path

from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parents[1]
CLIENT = Path(r'C:/Users/User/curseforge/minecraft/Install/versions/26.3/26.3.jar')
MAGIC = ROOT / 'dist/advance-magic.jar'
EDGE = Path(r'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe')
OUT = ROOT / 'docs/recipes'
WORK = ROOT / 'output/recipe-cards'
SCALE = 4  # vanilla GUI pixels -> screen pixels
OUTSIDE = (1, 2, 3)  # page surround, cropped away after capture

client = zipfile.ZipFile(CLIENT)
pack = zipfile.ZipFile(io.BytesIO(zipfile.ZipFile(MAGIC).read('resource-packs/advance-magic-java.zip')))


def texture(archive, path):
    image = Image.open(io.BytesIO(archive.read(path))).convert('RGBA')
    if image.height > image.width and image.height % image.width == 0:
        image = image.crop((0, 0, image.width, image.width))  # first frame of an animated strip
    return image


def vanilla(name):
    return texture(client, f'assets/minecraft/textures/{name}.png')


def magic(name):
    return texture(pack, f'assets/advance_magic/textures/item/{name}.png')


def potion(rgb):
    """Vanilla potion item: tinted overlay (layer0) under the glass bottle (layer1)."""
    overlay, bottle = vanilla('item/potion_overlay'), vanilla('item/potion')
    tinted = Image.new('RGBA', overlay.size)
    tinted.putdata([(p[0] * rgb[0] // 255, p[1] * rgb[1] // 255, p[2] * rgb[2] // 255, p[3]) for p in overlay.get_flattened_data()])
    tinted.alpha_composite(bottle)
    return tinted


def sized(image, px):
    return image.resize((px, px), Image.NEAREST if image.width <= 16 else Image.LANCZOS)


def uri(image):
    buffer = io.BytesIO()
    image.save(buffer, 'PNG')
    return 'data:image/png;base64,' + base64.b64encode(buffer.getvalue()).decode()


ITEMS = {
    'amethyst': vanilla('item/amethyst_shard'), 'blaze_rod': vanilla('item/blaze_rod'),
    'blaze_powder': vanilla('item/blaze_powder'), 'nether_wart': vanilla('item/nether_wart'),
    'diamond': vanilla('item/diamond'), 'netherite': vanilla('item/netherite_ingot'),
    'nether_star': vanilla('item/nether_star'), 'water': potion((0x38, 0x5D, 0xC6)),
    'wood_staff': magic('wooden_staff/wood_icon'),
}
ELEMENTS = [('fire', 'Fire', '#ff6a18'), ('wind', 'Wind', '#9fffe2'), ('earth', 'Earth', '#b9df42'),
            ('ice', 'Ice', '#a6ecff'), ('water', 'Water', '#25aaff'), ('lightning', 'Lightning', '#d7b8ff'),
            ('light', 'Light', '#ffe5a1'), ('dark', 'Dark', '#b94cff')]
for element, _, _ in ELEMENTS:
    ITEMS['staff_' + element] = magic(f'elemental_staffs/{element}_icon')
QUALITIES = [('low', 'ต่ำ', '#8978ac', 60, 20), ('medium', 'ปานกลาง', '#7963da', 30, 35),
             ('good', 'ดี', '#5f9cef', 8, 50), ('supreme', 'สูงสุด', '#dd9eff', 2, 100)]
for quality, *_ in QUALITIES:
    ITEMS['mana_' + quality] = magic('mana_potion_' + quality)
RARE, EPIC, LEGENDARY = '#4fb4ff', '#b47cff', '#ff9a3c'


def icon(name, px):
    return f'<img src="{uri(sized(ITEMS[name], px))}" width="{px}" height="{px}" alt="">'


def gui(sheet, height, items, overlays=()):
    """Top of a vanilla container sheet, closed with its own bottom border, items in slots."""
    source = vanilla(f'gui/container/{sheet}')
    panel = Image.new('RGBA', (176, height + 4))
    panel.paste(source.crop((0, 0, 176, height)), (0, 0))
    panel.paste(source.crop((0, 162, 176, 166)), (0, height))
    for sprite, x, y, crop in overlays:
        image = vanilla(f'gui/sprites/container/{sheet}/{sprite}')
        panel.alpha_composite(image.crop(crop), (x, y + crop[1]))
    panel = panel.resize((176 * SCALE, (height + 4) * SCALE), Image.NEAREST)
    for name, x, y in items:
        panel.alpha_composite(sized(ITEMS[name], 16 * SCALE), (x * SCALE, y * SCALE))
    return f'<img src="{uri(panel)}" width="{panel.width}" height="{panel.height}" alt="">'


def chip(name, count=None, label=None):
    n = f'<i class="n">{count}</i>' if count and count > 1 else ''
    caption = f'<span class="cap">{label}</span>' if label else ''
    return f'<div class="chipbox"><div class="chip">{icon(name, 64)}{n}</div>{caption}</div>'


CSS = """
*{box-sizing:border-box;margin:0;padding:0}
html,body{background:rgb(1,2,3)}
.page{width:1600px;padding:60px 64px 60px;color:#ece8f6;font-family:"Leelawadee UI",Tahoma,sans-serif;
  background:radial-gradient(1100px 560px at 88% -8%,rgba(165,110,255,.24),transparent 62%),
  radial-gradient(900px 520px at -8% 108%,rgba(70,140,255,.14),transparent 62%),#120f1d}
.kicker{font:700 19px/1 "Segoe UI",sans-serif;letter-spacing:.34em;color:#c9a6ff}
h1{font-size:66px;font-weight:700;line-height:1.3;margin:12px 0 2px}
.lead{font-size:25px;line-height:1.5;color:#aaa3bf}
.card{margin-top:34px;padding:34px 40px 36px;border-radius:22px;border:1px solid rgba(190,150,255,.2);
  background:linear-gradient(180deg,rgba(255,255,255,.06),rgba(255,255,255,.025))}
h2{display:flex;align-items:center;gap:16px;font-size:34px;line-height:1.35;font-weight:700}
.num{display:inline-grid;place-items:center;width:46px;height:46px;border-radius:50%;flex:none;
  background:#7b4dff;color:#fff;font:700 24px "Segoe UI",sans-serif}
h3{font-size:27px;line-height:1.4;margin:26px 0 14px;font-weight:700}
h3 span{font-size:21px;font-weight:400;color:#aaa3bf;margin-left:10px}
.gui{position:relative;flex:none}
.gui img{display:block;border-radius:6px;box-shadow:0 18px 40px rgba(0,0,0,.45)}
.gui .title{position:absolute;top:14px;font-size:27px;line-height:1.3;color:#404040}
.chipbox{display:flex;flex-direction:column;align-items:center;gap:14px}
.chip{position:relative;width:80px;height:80px;display:grid;place-items:center;background:#8b8b8b;
  box-shadow:inset 4px 4px 0 #373737,inset -4px -4px 0 #fff,0 0 0 4px #c6c6c6,0 8px 22px 4px rgba(0,0,0,.35)}
.chip .n{position:absolute;right:5px;bottom:-1px;font:700 27px "Segoe UI",sans-serif;font-style:normal;
  color:#fff;text-shadow:3px 3px 0 #3f3f3f}
.cap{font-size:20px;line-height:1.35;color:#cfc8e2;text-align:center}
.plus,.eq{font:700 40px "Segoe UI",sans-serif;color:#8f84ad;margin:0 4px 30px}
.arrow{position:relative;height:6px;border-radius:3px;background:linear-gradient(90deg,rgba(201,166,255,.25),#c9a6ff)}
.arrow::after{content:"";position:absolute;right:-4px;top:50%;transform:translateY(-50%);
  border-left:20px solid #c9a6ff;border-top:13px solid transparent;border-bottom:13px solid transparent}
code{font:700 25px "Cascadia Mono",Consolas,monospace;color:#ffe08a;background:rgba(0,0,0,.35);
  padding:3px 12px;border-radius:8px;border:1px solid rgba(255,224,138,.25)}
.how{font-size:24px;line-height:1.6;color:#d9d3ea;margin-top:10px}
.notes{list-style:none;margin-top:26px;display:grid;gap:12px}
.notes li{position:relative;padding-left:30px;font-size:22px;line-height:1.55;color:#cfc8e2}
.notes li::before{content:"✦";position:absolute;left:0;top:0;color:#c9a6ff}
i.rar{font-style:normal}.rar.r{color:#4fb4ff}.rar.e{color:#b47cff}.rar.l{color:#ff9a3c}
"""

STAFF_CSS = """
.craft{display:flex;gap:44px;align-items:center;margin-top:24px}
.recipe{display:grid;gap:18px;flex:1}
.ing{display:flex;align-items:center;gap:20px}
.ing b{display:block;font-size:27px;line-height:1.35}
.ing span{display:block;font-size:21px;line-height:1.45;color:#aaa3bf}
.yield{display:flex;align-items:center;gap:20px;margin-top:6px;padding:16px 20px;border-radius:16px;
  background:rgba(79,180,255,.08);border:1px solid rgba(79,180,255,.3)}
.yield b{display:block;font:700 28px "Segoe UI",sans-serif;color:#ffd27a}
.yield span{display:block;font-size:21px;line-height:1.5;color:#cfc8e2}
.steps{display:flex;align-items:center;gap:22px}
.tier{width:224px;flex:none;text-align:center}
.art{width:136px;height:136px;margin:0 auto 10px;border-radius:24px;display:grid;place-items:center;
  background:radial-gradient(circle,color-mix(in srgb,var(--c) 34%,transparent),transparent 72%),#1c1730;
  border:2px solid color-mix(in srgb,var(--c) 60%,transparent);
  box-shadow:0 0 28px color-mix(in srgb,var(--c) var(--glow),transparent)}
.tier b{display:block;font:700 19px "Segoe UI",sans-serif;letter-spacing:.02em}
.tier span{display:block;font-size:21px;line-height:1.45;color:#ece8f6}
.tier em{display:block;font-style:normal;font-size:19px;line-height:1.45;color:var(--c)}
.cost{width:250px;flex:none;display:grid;gap:12px;margin-top:-34px}
.cost .chips{display:flex;justify-content:center;gap:26px}
.cap-end{font-size:21px;line-height:1.5;color:#8f84ad;max-width:240px}
.elements{display:flex;gap:12px;margin-top:20px;padding:16px 18px;border-radius:16px;background:rgba(0,0,0,.22)}
.el{flex:1;text-align:center}
.el img{display:block;margin:0 auto 4px}
.el b{font:700 18px "Segoe UI",sans-serif}
"""

POTION_CSS = """
.flow{display:flex;align-items:center;gap:18px;margin-top:20px}
.flow .arrow{width:90px;margin:0 10px 34px}
.step{font-size:26px;line-height:1.4;font-weight:700;margin-top:28px}
.step span{font-weight:400;color:#aaa3bf;font-size:21px;margin-left:8px}
.brew{display:flex;align-items:center;gap:36px;margin-top:18px}
.brew .arrow{width:110px}
.outcome{display:grid;grid-template-columns:repeat(2,96px);gap:12px}
.outcome div{width:96px;height:96px;display:grid;place-items:center;background:#8b8b8b;
  box-shadow:inset 4px 4px 0 #373737,inset -4px -4px 0 #fff,0 0 0 4px #c6c6c6}
.outtext b{display:block;font-size:28px;line-height:1.4}
.outtext span{display:block;font-size:21px;line-height:1.5;color:#aaa3bf}
.split{display:grid;grid-template-columns:1.18fr 1fr;gap:28px}
.split .card{margin-top:28px}
.q{display:grid;grid-template-columns:80px 1fr 92px;align-items:center;gap:6px 18px;padding:14px 0;
  border-bottom:1px solid rgba(255,255,255,.07)}
.q:last-child{border-bottom:0}
.q .ic{grid-row:span 2;width:80px;height:80px;border-radius:16px;display:grid;place-items:center;
  background:radial-gradient(circle,color-mix(in srgb,var(--c) 40%,transparent),transparent 70%),#1c1730}
.q b{font-size:26px;line-height:1.35}
.q .mp{grid-row:span 2;font:700 26px "Segoe UI",sans-serif;color:#9fd6ff;text-align:right}
.q .mp small{display:block;font:400 18px "Leelawadee UI",sans-serif;color:#aaa3bf}
.bar{display:flex;align-items:center;gap:12px}
.bar i{display:block;height:12px;border-radius:6px;background:var(--c);min-width:12px}
.bar em{font:700 21px "Segoe UI",sans-serif;font-style:normal;color:var(--c)}
"""


def tier(name, label, slots, rarity, color, glow):
    rarity_class = {'Rare': 'r', 'Epic': 'e', 'Legendary': 'l'}[rarity]
    return (f'<div class="tier" style="--c:{color};--glow:{glow}%"><div class="art">{icon(name, 104)}</div>'
            f'<b>{label}</b><span>ใส่เวทได้ {slots} ช่อง</span><em>รับเวทถึง <i class="rar {rarity_class}">{rarity}</i></em></div>')


def cost(*chips):
    return f'<div class="cost"><div class="chips">{"".join(chips)}</div><div class="arrow"></div></div>'


def page(css, body):
    return (f'<!doctype html><html lang="th"><head><meta charset="utf-8"><style>{CSS}{css}</style></head>'
            f'<body><div class="page">{body}</div></body></html>')


def staff_page():
    crafting = gui('crafting_table', 80, [('amethyst', 48, 17), ('blaze_rod', 48, 35), ('blaze_rod', 48, 53),
                                               ('wood_staff', 124, 35)])
    elements = ''.join(f'<div class="el">{icon("staff_" + e, 64)}<b style="color:{c}">{t}</b></div>'
                       for e, t, c in ELEMENTS)
    body = f"""
<div class="kicker">ADVANCE MAGIC · EVERGARDEN</div>
<h1>คราฟและอัปเกรดคทาเวท</h1>
<p class="lead">เริ่มจากคทาไม้ที่คราฟเองได้ แล้วค่อยอัปเกรดเพิ่มช่องเวท</p>
<section class="card">
  <h2><span class="num">1</span>คราฟคทาไม้ที่โต๊ะคราฟต์</h2>
  <div class="craft">
    <div class="gui">{crafting}<div class="title" style="left:116px">โต๊ะคราฟต์</div></div>
    <div class="recipe">
      <div class="ing">{chip('amethyst')}<div><b>เศษแอเมทิสต์ ×1</b><span>ช่องบน แถวกลาง</span></div></div>
      <div class="ing">{chip('blaze_rod', 2)}<div><b>แท่งเบลซ ×2</b><span>ช่องกลางและช่องล่าง แถวกลาง</span></div></div>
      <div class="yield">{icon('wood_staff', 88)}<div><b>Amethystwood Staff</b>
        <span>WOODEN · ใส่เวทได้ 1 ช่อง · รับเวทถึง <i class="rar r">Rare</i></span></div></div>
    </div>
  </div>
  <ul class="notes"><li>ใช้วัตถุดิบธรรมดาที่ยังไม่ได้เปลี่ยนชื่อ</li></ul>
</section>
<section class="card">
  <h2><span class="num">2</span>อัปเกรดคทา</h2>
  <p class="how">ถือคทาไว้ในมือหลัก เก็บวัตถุดิบไว้ในกระเป๋า แล้วพิมพ์ <code>/staff upgrade</code> · เวทที่ใส่ไว้ยังอยู่ครบ</p>
  <h3>สายคทาไม้<span>เริ่มจากคทาที่คราฟเอง</span></h3>
  <div class="steps">
    {tier('wood_staff', 'WOODEN', 1, 'Rare', RARE, 20)}
    {cost(chip('amethyst', 4), chip('blaze_rod', 2))}
    {tier('wood_staff', 'REINFORCED_WOODEN', 2, 'Rare', RARE, 40)}
    <div class="cap-end">ระดับสูงสุดของสายไม้</div>
  </div>
  <h3>สายคทาธาตุ<span>คราฟไม่ได้ · ได้จากหีบสมบัติเท่านั้น</span></h3>
  <div class="steps">
    {tier('staff_fire', 'APPRENTICE', 3, 'Rare', RARE, 25)}
    {cost(chip('diamond', 8), chip('amethyst', 16))}
    {tier('staff_fire', 'ARCANE', 4, 'Epic', EPIC, 45)}
    {cost(chip('netherite'), chip('nether_star'))}
    {tier('staff_fire', 'MYTHIC', 5, 'Legendary', LEGENDARY, 70)}
  </div>
  <div class="elements">{elements}</div>
  <ul class="notes">
    <li>คทาธาตุระดับ APPRENTICE สุ่มได้จาก Vault และหีบในโครงสร้างของ Evergarden รวมถึงหีบในวิหารที่โลกปกติ (สุ่ม 1&nbsp;ใน&nbsp;8&nbsp;ธาตุ)</li>
    <li>คทาไม้อัปเกรดเป็นคทาธาตุไม่ได้ เพราะเป็นคนละสาย</li>
  </ul>
</section>"""
    return page(STAFF_CSS, body)


def potion_page():
    brewing = gui('brewing_stand', 80,
                  [('amethyst', 79, 17), ('water', 56, 51), ('water', 79, 58), ('water', 102, 51), ('blaze_powder', 17, 17)],
                  [('fuel_length', 60, 44, (0, 0, 18, 4)), ('brew_progress', 97, 16, (0, 0, 9, 18)),
                   ('bubbles', 63, 14, (0, 10, 12, 29))])
    outcome = ''.join(f'<div>{icon("mana_" + q, 80)}</div>' for q, *_ in QUALITIES)
    qualities = ''.join(
        f'<div class="q" style="--c:{c}"><div class="ic">{icon("mana_" + q, 72)}</div><b>ยามานา · {label}</b>'
        f'<div class="mp">+{mp}<small>มานา</small></div><div class="bar"><i style="width:{max(pct, 2) * 4}px"></i>'
        f'<em>{pct}%</em></div></div>' for q, label, c, pct, mp in QUALITIES)
    body = f"""
<div class="kicker">ADVANCE MAGIC · EVERGARDEN</div>
<h1>สูตรปรุงยามานา</h1>
<p class="lead">ปรุงที่แท่นปรุงยาตามปกติ ใช้ผงเบลซเป็นเชื้อเพลิง</p>
<section class="card">
  <h2><span class="num">1</span>ขั้นตอนการปรุง</h2>
  <div class="step">ขั้นที่ 1 · ทำน้ำยาเจื่อน<span>สูตรปกติของเกม</span></div>
  <div class="flow">
    {chip('water', None, 'ขวดน้ำ')}<span class="plus">+</span>{chip('nether_wart', None, 'หูดเนเธอร์')}
    <div class="arrow"></div>{chip('water', None, 'น้ำยาเจื่อน')}
  </div>
  <div class="step">ขั้นที่ 2 · ใส่เศษแอเมทิสต์<span>วางน้ำยาเจื่อนได้ครั้งละ 3 ขวด</span></div>
  <div class="brew">
    <div class="gui">{brewing}<div class="title" style="left:0;right:0;text-align:center">แท่นปรุงยา</div></div>
    <div class="arrow"></div>
    <div class="outcome">{outcome}</div>
    <div class="outtext"><b>ยามานา</b><span>สุ่มคุณภาพแยกทีละขวด</span></div>
  </div>
</section>
<div class="split">
  <section class="card">
    <h2><span class="num">2</span>คุณภาพที่สุ่มได้</h2>
    <div style="margin-top:12px">{qualities}</div>
  </section>
  <section class="card">
    <h2><span class="num">3</span>ข้อควรรู้</h2>
    <ul class="notes">
      <li>ดื่ม “ต่ำ” 2&nbsp;ขวด หรือ “ปานกลาง” 5&nbsp;ขวด ภายใน 1&nbsp;นาที<br>จะติดอาการวิงเวียน 30&nbsp;วินาที</li>
      <li>“สูงสุด” เพิ่มมานาสูงสุดถาวร +10<br>ขวดอื่นมีโอกาสเพิ่ม +2&nbsp;ถึง&nbsp;+5</li>
      <li>มานาสูงสุดที่เพิ่ม จะลดเลือดสูงสุดลงเท่ากัน<br>จนกว่าจะนอนหลับ · มานาสูงสุดไม่เกิน 200</li>
      <li>ยามานาทำเป็นน้ำยาแบบปาหรือแบบระเหยต่อไม่ได้</li>
    </ul>
  </section>
</div>"""
    return page(POTION_CSS, body)


def capture(name, html):
    WORK.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    source, shot = WORK / f'{name}.html', WORK / f'{name}-raw.png'
    source.write_text(html, encoding='utf8')
    shot.unlink(missing_ok=True)
    subprocess.run([str(EDGE), '--headless=new', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1',
                    f'--user-data-dir={WORK / "edge-profile"}', '--window-size=1600,3000',
                    f'--screenshot={shot}', source.as_uri()], check=True, capture_output=True, timeout=120)
    image = Image.open(shot).convert('RGB')
    box = ImageChops.difference(image, Image.new('RGB', image.size, OUTSIDE)).getbbox()
    target = OUT / f'{name}.png'
    image.crop(box).save(target, optimize=True)
    print(target, image.crop(box).size)


if __name__ == '__main__':
    capture('staff-crafting', staff_page())
    capture('mana-potion-brewing', potion_page())
