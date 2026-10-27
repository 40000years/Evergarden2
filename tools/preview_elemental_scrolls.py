"""Build a local scroll-art gallery and inventory-scale review images."""
from __future__ import annotations

import base64
from functools import lru_cache
import html
import io
from pathlib import Path
import textwrap
import zipfile

from PIL import Image, ImageDraw, ImageFont

from elemental_spell_assets import COLORS, RARITY, _texture, catalog

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'previews/elemental-scrolls'
ELEMENTS = ('fire', 'water', 'ice', 'wind', 'earth', 'lightning', 'light', 'dark')
FEATURED = ('phoenix_rebirth', 'leviathans_wrath', 'absolute_zero', 'tempest_dominion',
            'titans_judgment', 'wrath_of_raijin', 'heavens_descent', 'abyssal_collapse')


@lru_cache
def font(size, bold=False):
    names = ('C:/Windows/Fonts/segoeuib.ttf' if bold else 'C:/Windows/Fonts/segoeui.ttf',
             '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf')
    for name in names:
        if Path(name).is_file():
            return ImageFont.truetype(name, size)
    return ImageFont.load_default()


def place(canvas, image, centre, size):
    tile = image.resize((size, size), Image.Resampling.LANCZOS)
    canvas.alpha_composite(tile, (round(centre[0] - size / 2), round(centre[1] - size / 2)))


def overview(rows, icons):
    canvas = Image.new('RGBA', (1600, 1090), '#0b101b')
    d = ImageDraw.Draw(canvas)
    d.text((40, 27), 'EVERGARDEN  /  ARCANE COLLECTION', font=font(15), fill='#9ba8bf')
    d.text((38, 55), 'ELEMENTAL SCROLLS', font=font(43, True), fill='#f4e8c9')
    d.text((40, 114), '58 individual spell emblems  /  8 elements  /  4 rarity silhouettes', font=font(18), fill='#a5b1c7')
    for i, (element, featured) in enumerate(zip(ELEMENTS, FEATURED)):
        x, y = 40 + i % 4 * 390, 163 + i // 4 * 449
        colour = COLORS[element][2]
        d.rounded_rectangle((x, y, x + 373, y + 431), radius=13, fill='#121b2a', outline='#2b3548', width=2)
        d.line((x + 16, y + 1, x + 357, y + 1), fill=colour, width=3)
        d.text((x + 18, y + 16), element.upper(), font=font(22, True), fill=colour)
        place(canvas, icons[featured], (x + 186, y + 157), 205)
        spell = next(row for row in rows if row['id'] == featured)
        d.text((x + 186, y + 270), spell['title'], anchor='mm', font=font(17, True), fill='#f4e8c9')
        family = [row for row in rows if row['element'] == element]
        for k, rarity in enumerate(('BASIC', 'RARE', 'EPIC')):
            spell = next(row for row in family if row['rarity'] == rarity)
            place(canvas, icons[spell['id']], (x + 70 + k * 116, y + 348), 93)
            d.text((x + 70 + k * 116, y + 408), rarity.title(), anchor='mm', font=font(12), fill=RARITY[rarity])
    d.text((40, 1062), 'Actual transparent item textures. Menu mockups are rendered from the existing slot layout.', font=font(14), fill='#8d9bb0')
    canvas.convert('RGB').save(OUT / 'overview.png')


def catalogue(rows, icons):
    canvas = Image.new('RGBA', (1860, 1840), '#0b101b')
    d = ImageDraw.Draw(canvas)
    d.text((35, 25), 'ALL 58 ELEMENTAL SCROLLS', font=font(34, True), fill='#f4e8c9')
    d.text((36, 77), '128 x 128 RGBA artwork  /  existing spell IDs  /  Java + Bedrock', font=font(18), fill='#a5b1c7')
    for row_index, element in enumerate(ELEMENTS):
        y = 127 + row_index * 210
        d.text((35, y), element.upper(), font=font(17, True), fill=COLORS[element][2])
        d.line((180, y + 13, 1825, y + 13), fill='#293246')
        for i, spell in enumerate(spell for spell in rows if spell['element'] == element):
            x = 35 + i * 180
            place(canvas, icons[spell['id']], (x + 80, y + 90), 128)
            lines = textwrap.wrap(spell['title'], width=22)
            for j, line in enumerate(lines):
                d.text((x + 80, y + 162 + j * 16), line, anchor='mm', font=font(13), fill='#e4e8ef')
            d.text((x + 80, y + 196), spell['rarity'].title(), anchor='mm', font=font(11), fill=RARITY[spell['rarity']])
    canvas.convert('RGB').save(OUT / 'all-scrolls.png')


def menu(icons, before):
    """Same 54-slot layout as SpellMenu; other UI textures come from the pack."""
    import skilltree_gui_assets as sg
    grid = ['st_panel'] * 54
    for slot, name in {0: 'st_help', 4: 'shield_off', 5: 'st_tab_core_on', 6: 'upgrade',
                       7: 'codex', 8: 'st_close', 9: 'staff', 14: 'socket', 15: 'st_lock',
                       20: 'extract', 21: 'extract', 22: 'extract', 30: 'move_left',
                       31: 'move_left', 36: 'label_scrolls'}.items():
        grid[slot] = name
    for slot, name in {11: 'fireball', 12: 'frost_bolt', 13: 'wind_cutter', 37: 'meteor_rain',
                       38: 'radiant_bolt', 39: 'thunder_spear', 40: 'stone_wall', 41: 'shadow_bolt'}.items():
        grid[slot] = 'scroll_' + name
    ui = {}
    with zipfile.ZipFile(ROOT / 'advance-magic/dist/advance-magic-java.zip') as archive:
        for name in set(grid):
            if name.startswith('scroll_'):
                continue
            if name == 'staff':
                path = 'assets/advance_magic/textures/item/elemental_staffs/fire_icon.png'
            elif name.startswith('st_'):
                path = 'assets/advance_magic/textures/item/skilltree/' + name[3:] + '.png'
            else:
                path = 'assets/advance_magic/textures/item/menu/' + name + '.png'
            ui[name] = Image.open(io.BytesIO(archive.read(path))).convert('RGBA')
            if name == 'staff':
                ui[name] = ui[name].crop((0, 0, ui[name].width, ui[name].width))
    def render(source):
        images = {**ui, **{'scroll_' + name: icon for name, icon in source.items()}}
        return sg.render_menu(images, grid, 'Staff · คลิกคัมภีร์ด้านล่างเพื่อใส่เวท')
    old, new = render(before), render(icons)
    old.save(OUT / 'spell-menu-before.png')
    new.save(OUT / 'spell-menu-after.png')
    menu_preview = ROOT / 'previews/menu-gui/spell-menu.png'
    menu_preview.parent.mkdir(parents=True, exist_ok=True)
    new.save(menu_preview)
    page = Image.new('RGBA', (old.width * 2 + 72, old.height + 120), '#0b101b')
    d = ImageDraw.Draw(page)
    d.text((24, 17), 'BEFORE', font=font(24, True), fill='#8d9bb0')
    d.text((old.width + 48, 17), 'NEW SCROLL ART', font=font(24, True), fill='#f4e8c9')
    page.alpha_composite(old, (24, 62))
    page.alpha_composite(new, (old.width + 48, 62))
    d.text((24, page.height - 30), 'Inventory-scale mockup using the existing /spell slot positions; not a live game screenshot.', font=font(14), fill='#a5b1c7')
    page.convert('RGB').save(OUT / 'before-after.png')


def data_url(image):
    buffer = io.BytesIO()
    image.save(buffer, format='PNG')
    return 'data:image/png;base64,' + base64.b64encode(buffer.getvalue()).decode('ascii')


def gallery(rows, icons, before):
    cards = []
    for spell in rows:
        title = html.escape(spell['title'])
        cards.append(f'<article data-element="{spell["element"]}" data-rarity="{spell["rarity"]}" data-name="{title.lower()}" style="--accent:{COLORS[spell["element"]][2]}">'
            f'<div class="art"><figure class="old"><img alt="ภาพเดิม {title}" src="{data_url(before[spell["id"]])}"><figcaption>เดิม</figcaption></figure>'
            f'<figure><img alt="{title}" src="{data_url(icons[spell["id"]])}"><figcaption class="newlabel">ใหม่</figcaption></figure></div>'
            f'<h2>{title}</h2><p>{spell["element"].title()} · <span style="color:{RARITY[spell["rarity"]]}">{spell["rarity"].title()}</span></p>'
            f'<code>scroll_{spell["id"]}</code></article>')
    options = ''.join(f'<option value="{e}">{e.title()}</option>' for e in ELEMENTS)
    rarities = ''.join(f'<option>{r}</option>' for r in RARITY)
    document = '''<!doctype html><html lang="th"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Evergarden · Elemental Scrolls</title><style>
.compare main{grid-template-columns:repeat(auto-fill,minmax(min(100%,300px),1fr))}
:root{color-scheme:dark;--size:96px}*{box-sizing:border-box}body{margin:0;background:#0b101b;color:#e8eaf0;font:15px 'Segoe UI',sans-serif}header,main{max-width:1500px;margin:auto;padding:32px}header{padding-bottom:16px}.eyebrow{letter-spacing:.2em;font-size:12px;color:#9ca8bd}h1{font-size:clamp(28px,4vw,48px);margin:12px 0;color:#f4e8c9}.intro{max-width:850px;line-height:1.7;color:#aeb9cc}.controls{display:flex;flex-wrap:wrap;align-items:center;gap:12px;padding:20px 0;border-block:1px solid #30394a;margin-top:24px}select,input[type=search],button{font:inherit;background:#172132;border:1px solid #3b485e;color:#eef2fa;border-radius:7px;padding:9px 12px}input[type=search]{min-width:210px}label{display:flex;gap:7px;align-items:center}input[type=range]{width:110px}#count{color:#aeb9cc;margin-top:15px}main{display:grid;grid-template-columns:repeat(auto-fill,minmax(210px,1fr));gap:14px;padding-top:10px}article{border:1px solid #2c374b;border-top:2px solid var(--accent);border-radius:12px;background:#121b2a;padding:20px 12px;text-align:center}article[hidden]{display:none}.art{display:flex;justify-content:center;gap:10px;min-height:calc(var(--size) + 20px)}figure{margin:0}figure img{width:var(--size);height:var(--size);object-fit:contain;image-rendering:pixelated}figcaption{font-size:11px;color:#8e9aaf}.old,.newlabel{display:none}.compare .old,.compare .newlabel{display:block}h2{font-size:15px;margin:15px 0 7px}article p{font-size:12px;color:#b5c2d8;margin:6px}code{font-size:10px;color:#7f8ca3;overflow-wrap:anywhere}.stone article{background:#525252}.paper article{background:#c7c1b5}.paper h2,.paper article p{color:#172132}.paper code{color:#46546a}a{color:#b4dbff}footer{max-width:1500px;margin:auto;padding:0 32px 30px;color:#8e9aaf;line-height:1.7}</style>
<header><div class="eyebrow">EVERGARDEN / ARCANE COLLECTION</div><h1>คัมภีร์เวท · ชุดทรัพยากรใหม่</h1>
<p class="intro">ม้วนกระดาษโบราณ ขอบโลหะสลัก อัญมณีประจำธาตุ และภาพสัญลักษณ์เฉพาะแต่ละเวททั้ง 58 แบบ ระดับ Basic → Rare → Epic → Legendary มีรายละเอียดและรูปทรงขอบที่ต่างกัน</p>
<div class="controls"><input id="search" type="search" placeholder="ค้นหาชื่อเวท" aria-label="ค้นหาชื่อเวท">
<select id="element" aria-label="ธาตุ"><option value="">ทุกธาตุ</option>ELEMENT_OPTIONS</select>
<select id="rarity" aria-label="ระดับ"><option value="">ทุกระดับ</option>RARITY_OPTIONS</select>
<label><input id="compare" type="checkbox">เทียบภาพเดิม</label>
<label>ขนาด <input id="size" type="range" min="16" max="128" step="16" value="96"><output id="sizevalue">96px</output></label>
<select id="background" aria-label="พื้นหลัง"><option value="">พื้นเข้ม</option><option value="stone">พื้นเทา</option><option value="paper">พื้นสว่าง</option></select></div><div id="count">58 คัมภีร์</div></header>
<main>CARDS</main><footer>เท็กซ์เจอร์จริง 128×128 RGBA · รหัสไอเท็มเดิม · Java / Bedrock<br>ภาพตัวอย่างเมนูเป็นภาพจำลองตามตำแหน่งช่องเดิม ไม่ใช่ภาพบันทึกจากเกม</footer>
<script>const $=id=>document.getElementById(id);function update(){let n=0;document.querySelectorAll('article').forEach(card=>{card.hidden=!!(($('element').value&&card.dataset.element!==$('element').value)||($('rarity').value&&card.dataset.rarity!==$('rarity').value)||!card.dataset.name.includes($('search').value.toLowerCase()));if(!card.hidden)n++});$('count').textContent=n+' / 58 คัมภีร์';document.body.className=[$('compare').checked?'compare':'',$('background').value].join(' ');document.documentElement.style.setProperty('--size',$('size').value+'px');$('sizevalue').textContent=$('size').value+'px'}document.querySelectorAll('input,select').forEach(control=>control.addEventListener('input',update));update();</script></html>'''
    document = document.replace('ELEMENT_OPTIONS', options).replace('RARITY_OPTIONS', rarities).replace('CARDS', ''.join(cards))
    (OUT / 'index.html').write_text(document, encoding='utf8')
    (ROOT / 'advance-magic/dist/elemental-scroll-preview.html').write_text(document, encoding='utf8')


def build():
    OUT.mkdir(parents=True, exist_ok=True)
    rows = catalog(ROOT / 'advance-magic')
    icons = {spell['id']: _texture(spell) for spell in rows}
    before = {}
    for spell in rows:
        with Image.open(OUT / 'before' / (spell['id'] + '.png')) as opened:
            before[spell['id']] = opened.convert('RGBA')
    overview(rows, icons)
    catalogue(rows, icons)
    menu(icons, before)
    gallery(rows, icons, before)
    print(f'Scroll art gallery, overview, all 58 textures and before/after menu: {OUT}')


if __name__ == '__main__':
    build()
