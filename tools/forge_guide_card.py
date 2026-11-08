"""Render the Forge (reforge) introduction for players as one PNG infographic.

Describes the EvergardenForge release the server runs, 0.5.8: legendary weapons, boss armor
sets and staffs cannot be forged there (0.6.0 made them forgeable). Facts mirror that release:
Rarity (grade names, colours, salvage materials), its bundled config.yml (affix ranges and
level windows, sockets per grade, reforge costs and choice multipliers, gem purity multipliers,
cutting cost, protected items), EliteService (name tag), AffixService (item name and lore
lines), GemType (bonus per gear group), GemService (purity names) and the ForgeMenu help line.
Every sample roll is checked against its grade's level window. Update both together.

Same pipeline as recipe_cards.py: textures from the 26.3 client jar, the released Advance
Magic pack and evergarden-forge/art, laid out as HTML and captured with headless Edge
(Pillow on this machine has no Thai shaping).

    python tools/forge_guide_card.py        -> docs/recipes/forge-guide.png
"""
import random
import subprocess
import zipfile
from decimal import ROUND_HALF_UP, Decimal

import yaml
from PIL import Image, ImageChops

from recipe_cards import EDGE, OUTSIDE, ROOT, magic, uri, vanilla

ART = ROOT / 'evergarden-forge/art'  # byte-identical to the textures packed in 0.5.8
FORGE_JAR = ROOT / 'dist/evergarden-forge-0.5.8.jar'
CONFIG = yaml.safe_load(zipfile.ZipFile(FORGE_JAR).read('config.yml').decode('utf8'))
# The "cannot be forged" strip: legendary weapons, boss armor sets, staffs (and relics) must stay protected.
assert {'advance-magic:legendary_weapon', 'advance-magic:armor_set', 'advance-magic:wooden_staff',
        'advance-magic:elemental_staff*', 'evergarden:relic'} <= set(CONFIG['protected-keys']), 'not the 0.5.8 rules'
assert 'salvage-locked-keys' not in CONFIG
OUT = ROOT / 'docs/recipes'
WORK = ROOT / 'output/forge-guide'

RARITY = {  # Rarity.java: Thai name, colour, salvage material (its art is material_<id>)
    'COMMON': ('ธรรมดา', '#d8d8d8', 'เศษเหล็กหลอม'),
    'UNCOMMON': ('ดี', '#5ee87a', 'ผลึกใบหยก'),
    'RARE': ('หายาก', '#4fb4ff', 'ชิ้นคริสตัลคราม'),
    'EPIC': ('มหากาพย์', '#b47cff', 'เกล็ดอาคมม่วง'),
    'MYTHIC': ('ปรัมปรา', '#ff9a3c', 'อำพันหลอมเทพ'),
}
# GemType.java order and colours; earth and dark are lifted a little so the names read on the dark page.
GEMS = [('fire', 'เพลิง', '#ff6a3d'), ('water', 'วารี', '#3fa9f5'), ('wind', 'วายุ', '#cfefea'),
        ('earth', 'ปฐพี', '#c08a4e'), ('lightning', 'อสนี', '#ffe14d'), ('light', 'รัศมี', '#fff4d6'),
        ('dark', 'ราตรี', '#9a6fe0'), ('life', 'ชีวา', '#4cd964')]
TIER_NAMES = ['แตกร้าว', 'หยาบ', 'เจียระไน', 'ไร้ตำหนิ', 'สมบูรณ์แบบ']  # GemService.TIER_NAMES
ROMAN = ['I', 'II', 'III', 'IV', 'V']
AFFIX = {  # AffixType + Stat: stat label, shown as percent, name adjective, name noun
    'sharp': ('ดาเมจโจมตี', False, 'คมกริบ', 'ความคม'),
    'swift': ('ความเร็วโจมตี', True, 'ว่องไว', 'ความว่องไว'),
    'heavy': ('แรงกระแทก', False, 'หนักหน่วง', 'แรงทลาย'),
    'sweep': ('ดาเมจกวาด', True, 'กวาดล้าง', 'พายุคมดาบ'),
    'vigor': ('พลังชีวิตสูงสุด', False, 'ทรหด', 'ชีวิต'),
    'fleet': ('ความเร็วเคลื่อนที่', True, 'ปราดเปรียว', 'สายลม'),
    'lucky': ('โชค', False, 'นำโชค', 'โชคลาภ'),
}
FIRE_BONUS = [('ดาเมจโจมตี', False, 0.5), ('ดาเมจลูกธนู', True, 0.04), ('ระยะเวลาติดไฟ', True, -0.08)]  # weapon, bow, armor
# ForgeMenu help in 0.5.8: "อาวุธตำนาน ชุดบอส คทา และ Relic ใช้ที่นี่ไม่ได้"
BLOCKED = [('excalibur', 'อาวุธตำนาน'), ('seraph', 'ชุดบอส'), ('staff', 'คทา'), ('relic', 'Relic')]


def stat(value, percent, label):
    """Stat.format: Java %.1f (half up on the shortest decimal), trailing .0 dropped."""
    shown = value * 100 if percent else value
    number = str(Decimal(repr(abs(shown))).quantize(Decimal('0.1'), ROUND_HALF_UP)).removesuffix('.0')
    return f'{"-" if value < 0 else "+"}{number}{"%" if percent else ""} {label}'


def rolled(rarity, rolls):
    """Lore lines for affix rolls, refusing a level the grade can never roll."""
    low, high = CONFIG['rarities'][rarity]['level']
    lines = []
    for key, level in rolls:
        assert low <= level <= high, f'{key} {level} is outside the {rarity} window {low}-{high}'
        affix = CONFIG['affixes'][key]
        label, percent, *_ = AFFIX[key]
        lines.append((stat(affix['min'] + (affix['max'] - affix['min']) * level, percent, label), round(level * 100)))
    return lines


def item_name(base, rolls):
    """AffixService.writeName: first affix adjective, then 'แห่ง' + the second affix noun."""
    keys = [key for key, _ in rolls]
    return f'{base} {AFFIX[keys[0]][2]}' + (f' แห่ง{AFFIX[keys[1]][3]}' if len(keys) > 1 else '')


def gem_value(base, tier):
    return base * CONFIG['gems']['tier-multipliers'][tier - 1]


# ---- textures ------------------------------------------------------------------------------

def art(name):
    return Image.open(ART / f'{name}.png').convert('RGBA')


def square(image):
    side = max(image.size)
    canvas = Image.new('RGBA', (side, side))
    canvas.alpha_composite(image, ((side - image.width) // 2, (side - image.height) // 2))
    return canvas


def img(image, size, cls=''):
    image = square(image)
    pixel = image.width <= 16 or size % image.width == 0
    image = image.resize((size, size), Image.NEAREST if pixel else Image.LANCZOS)
    return f'<img class="{cls}" src="{uri(image)}" width="{size}" height="{size}" alt="">'


def face(skin, eyes=None):
    """Front of a mob head (with its hat layer and emissive eyes) from an entity skin."""
    texture = vanilla(skin)
    head = texture.crop((8, 8, 16, 16))
    if texture.height == 64:
        head.alpha_composite(texture.crop((40, 8, 48, 16)))
    if eyes:
        head.alpha_composite(vanilla(eyes).crop((8, 8, 16, 16)))
    return head


def chest():
    """Front of a vanilla chest: lid and base faces with the latch, from the entity texture."""
    texture = vanilla('entity/chest/normal')
    front = Image.new('RGBA', (14, 15))
    front.paste(texture.crop((14, 14, 28, 19)), (0, 0))
    front.paste(texture.crop((14, 33, 28, 43)), (0, 5))
    front.alpha_composite(texture.crop((1, 1, 3, 5)), (6, 3))
    return front


TEX = {
    'sword': vanilla('item/iron_sword'), 'bow': vanilla('item/bow'), 'chestplate': vanilla('item/iron_chestplate'),
    'shield': vanilla('entity/shield/shield_base_nopattern').crop((1, 1, 13, 23)),
    'staff': magic('elemental_staffs/fire_icon'), 'excalibur': magic('legendary_weapons/excalibur_icon'),
    'seraph': magic('seraph/chestplate_icon'), 'relic': vanilla('item/nether_star'),  # Evergarden's relic menu icon
    'ore': vanilla('block/diamond_ore'),
    'xp': vanilla('item/experience_bottle'), 'chest': chest(), 'dust': art('gem_dust'),
    'zombie': face('entity/zombie/zombie'), 'skeleton': face('entity/skeleton/skeleton'),
    'blaze': face('entity/blaze/blaze'), 'enderman': face('entity/enderman/enderman', 'entity/enderman/enderman_eyes'),
}
for rarity_id in RARITY:
    TEX['mat_' + rarity_id] = art('material_' + rarity_id.lower())


def gem(element, tier):
    return art(f'gem_{element}_{tier}')


# ---- pieces --------------------------------------------------------------------------------

def slot(image, size=64, count=None, rarity=None):
    ring = f' style="--c:{RARITY[rarity][1]}"' if rarity else ''
    number = f'<i class="n">{count}</i>' if count else ''
    return f'<div class="slot{" glow" if rarity else ""}"{ring}>{img(image, size)}{number}</div>'


def tooltip(name, rarity, lines, gems=(), empty=0, small=False):
    thai, colour, _ = RARITY[rarity]
    rows = [f'<p class="nm" style="color:{colour}">{name}</p>', f'<p style="color:{colour}">✦ เกรด {thai}</p>']
    rows += [f'<p> ▸ {text}<span class="q">  ({quality}%)</span></p>' for text, quality in lines]
    for element, tier, text in gems:
        thai_gem, gem_colour = next((t, c) for e, t, c in GEMS if e == element)
        rows.append(f'<p><span style="color:{gem_colour}"> ◆ อัญมณี{thai_gem} {ROMAN[tier - 1]}: </span>{text}</p>')
    rows += ['<p class="dim"> ◇ ช่อง Gem ว่าง</p>'] * empty
    return f'<div class="tip{" small" if small else ""}">{"".join(rows)}</div>'


def embers(count, width, height, seed, keep_clear):
    """Forge sparks in the hero corner, kept off the showcase tooltip (x0, y0, x1, y1)."""
    rng = random.Random(seed)
    dots = []
    while len(dots) < count:
        x, y = rng.randrange(width), rng.randrange(height)
        size = rng.choice((4, 6, 6, 8, 10))
        colour = rng.choice(('#ffb347', '#ff8a3c', '#ffd27a', '#ff6a3d'))
        opacity = rng.uniform(.18, .7)
        if keep_clear[0] <= x <= keep_clear[2] and keep_clear[1] <= y <= keep_clear[3]:
            continue
        dots.append(f'<i style="left:{x}px;top:{y}px;width:{size}px;height:{size}px;background:{colour};'
                    f'color:{colour};opacity:{opacity:.2f}"></i>')
    return ''.join(dots)


def mob(texture, mob_name, rarity, seed):
    thai, colour, _ = RARITY[rarity]
    bold = list(RARITY).index(rarity) >= list(RARITY).index('EPIC')  # EliteService bolds epic and up
    rng = random.Random(seed)
    sparks = ''.join(f'<i style="left:{rng.randrange(8, 180)}px;top:{rng.randrange(8, 170)}px;'
                     f'opacity:{rng.uniform(.35, .95):.2f}"></i>' for _ in range(14))
    return (f'<div class="mob" style="--c:{colour}"><div class="tag{" b" if bold else ""}">✦ {thai} · {mob_name}</div>'
            f'<div class="aura">{sparks}{img(TEX[texture], 104, "head")}</div></div>')


# ---- page ----------------------------------------------------------------------------------

CSS = """
*{box-sizing:border-box;margin:0;padding:0}
html,body{background:rgb(1,2,3)}
.page{position:relative;overflow:hidden;width:1600px;padding:64px 64px 52px;color:#efeaf7;
  font-family:"Leelawadee UI",Tahoma,sans-serif;
  background:radial-gradient(1050px 640px at 94% -6%,rgba(255,132,52,.27),transparent 62%),
    radial-gradient(760px 520px at -8% 34%,rgba(124,82,255,.13),transparent 62%),
    radial-gradient(900px 640px at 108% 74%,rgba(255,160,70,.09),transparent 60%),
    radial-gradient(800px 520px at 0% 102%,rgba(70,140,255,.10),transparent 60%),#110e19}
.embers{position:absolute;left:760px;top:0;width:840px;height:560px;pointer-events:none}
.embers i{position:absolute;border-radius:1px;box-shadow:0 0 10px currentColor}
.kicker{font:700 19px/1 "Segoe UI",sans-serif;letter-spacing:.36em;color:#ffb36b}
h1{font-size:76px;font-weight:700;line-height:1.22;margin:16px 0 10px;letter-spacing:-.005em}
h1 em{font-style:normal;background:linear-gradient(90deg,#ffe2a1,#ff9a3c 70%);-webkit-background-clip:text;color:transparent}
.lead{font-size:26px;line-height:1.62;color:#c9c2d9;max-width:770px}
.lead b{color:#ffd27a;font-weight:700}
.hero{position:relative;display:grid;grid-template-columns:1fr 600px;gap:40px;align-items:center}
.gear{display:flex;gap:20px;margin-top:34px}
.gear div{width:150px;text-align:center}
.gear .frame{width:150px;height:150px;border-radius:26px;display:grid;place-items:center;margin-bottom:12px;
  background:radial-gradient(circle,color-mix(in srgb,var(--c) 34%,transparent),transparent 70%),#1c1729;
  border:2px solid color-mix(in srgb,var(--c) 70%,transparent);box-shadow:0 0 30px color-mix(in srgb,var(--c) 38%,transparent)}
.gear b{display:block;font-size:25px;line-height:1.3}
.gear span{display:block;font-size:19px;line-height:1.4;color:var(--c)}
.showcase{position:relative;justify-self:end}
.sc-label{font-size:19px;letter-spacing:.04em;color:#a59cbb;margin:0 0 14px 4px}
.hover{display:flex;align-items:flex-start;gap:16px}
.hover .slot{margin-top:14px}
.tip{min-width:430px;padding:14px 22px 16px;border-radius:6px;border:3px solid transparent;
  background:linear-gradient(rgba(18,4,20,.97),rgba(18,4,20,.97)) padding-box,linear-gradient(180deg,#6a2cff,#2c0c86) border-box;
  box-shadow:0 22px 50px rgba(0,0,0,.55),0 0 40px rgba(255,140,60,.12)}
.tip p{font-size:21px;line-height:1.56;white-space:pre;color:#e8e4f0}
.tip p.nm{font-size:25px;font-weight:700;margin-bottom:2px}
.tip .q{color:#8a8399}.tip .dim{color:#6d6680}
.tip.small{min-width:0;width:100%;padding:12px 18px 14px}
.tip.small p{font-size:19px}.tip.small p.nm{font-size:22px}
.grades{display:flex;align-items:center;gap:14px;margin-top:44px;padding:18px 26px;border-radius:18px;
  background:rgba(0,0,0,.22);border:1px solid rgba(255,255,255,.06)}
.grades .lbl{font-size:22px;color:#a59cbb;margin-right:8px}
.pill{font-size:23px;font-weight:700;padding:6px 20px;border-radius:999px;color:var(--c);
  background:color-mix(in srgb,var(--c) 13%,transparent);border:1.5px solid color-mix(in srgb,var(--c) 55%,transparent)}
.chev{color:#5f5874;font:700 22px "Segoe UI",sans-serif}
.blocked{display:flex;align-items:center;gap:30px;margin-top:16px;padding:18px 26px;border-radius:18px;
  background:linear-gradient(90deg,rgba(255,77,77,.10),rgba(255,77,77,.03));border:1px solid rgba(255,98,98,.28)}
.blocked .lbl{font-size:23px;font-weight:700;color:#ff8a8a;line-height:1.35;flex:none}
.blocked .lbl span{display:block;font-size:19px;font-weight:400;color:#a59cbb}
.no{display:flex;align-items:center;gap:24px}
.no .ic{position:relative;width:70px;height:70px;display:grid;place-items:center;border-radius:16px;
  background:rgba(0,0,0,.32);border:1px solid rgba(255,98,98,.3)}
.no .ic::after{content:"";position:absolute;right:-9px;bottom:-9px;width:28px;height:28px;border:4px solid #ff5a5a;border-radius:50%;
  background:linear-gradient(45deg,transparent 41%,#ff5a5a 41%,#ff5a5a 59%,transparent 59%),#2a1119}
.no b{font-size:23px;color:#e8e2f2}
.card{position:relative;margin-top:34px;padding:36px 40px 40px;border-radius:24px;border:1px solid rgba(255,170,90,.15);
  background:linear-gradient(180deg,rgba(255,255,255,.058),rgba(255,255,255,.022))}
h2{display:flex;align-items:center;gap:18px;font-size:38px;line-height:1.35;font-weight:700}
.num{display:grid;place-items:center;width:54px;height:54px;border-radius:50%;flex:none;color:#2b1205;
  font:800 27px "Segoe UI",sans-serif;background:linear-gradient(135deg,#ffcb6b,#ff7a2f);box-shadow:0 0 26px rgba(255,140,60,.45)}
.say{font-size:24px;line-height:1.62;color:#d3cce3;margin:12px 0 0 72px}
.say b{color:#fff;font-weight:700}
h3{font-size:27px;line-height:1.4;font-weight:700;margin:34px 0 16px}
h3 span{font-size:21px;font-weight:400;color:#a59cbb;margin-left:12px}
.slot{position:relative;flex:none;width:80px;height:80px;display:grid;place-items:center;background:#8b8b8b;
  box-shadow:inset 4px 4px 0 #373737,inset -4px -4px 0 #fff,0 0 0 4px #c6c6c6,0 8px 22px 4px rgba(0,0,0,.35)}
.slot.glow{box-shadow:inset 4px 4px 0 #373737,inset -4px -4px 0 #fff,0 0 0 4px var(--c),0 0 26px 6px color-mix(in srgb,var(--c) 45%,transparent)}
.slot .n{position:absolute;right:5px;bottom:-1px;font:700 27px "Segoe UI",sans-serif;font-style:normal;color:#fff;text-shadow:3px 3px 0 #3f3f3f}
.cap{font-size:20px;line-height:1.4;color:#cfc8e2;text-align:center}
.arrow{position:relative;height:6px;border-radius:3px;background:linear-gradient(90deg,rgba(255,179,71,.2),#ffb347)}
.arrow::after{content:"";position:absolute;right:-4px;top:50%;transform:translateY(-50%);
  border-left:20px solid #ffb347;border-top:13px solid transparent;border-bottom:13px solid transparent}
.op{font:700 40px "Segoe UI",sans-serif;color:#8f84ad}
/* 1 */
.hunt{display:grid;grid-template-columns:1fr 300px;gap:30px;margin-top:28px;align-items:stretch}
.mobs{display:grid;grid-template-columns:repeat(4,1fr);gap:16px}
.mob{display:flex;flex-direction:column;align-items:center}
.tag{font-size:19px;line-height:1.35;padding:3px 12px;border-radius:4px;background:rgba(0,0,0,.42);color:var(--c);white-space:nowrap}
.tag.b{font-weight:700}
.aura{position:relative;width:196px;height:180px;display:grid;place-items:center;margin-top:10px;border-radius:24px;
  background:radial-gradient(circle at 50% 52%,color-mix(in srgb,var(--c) 42%,transparent),transparent 64%)}
.aura i{position:absolute;width:8px;height:8px;background:var(--c);box-shadow:0 0 8px var(--c)}
.aura .head{position:relative;z-index:1;box-shadow:0 14px 26px rgba(0,0,0,.5)}
.realms{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-top:6px}
.realms span{text-align:center;font-size:20px;color:#a59cbb;padding-top:12px;border-top:2px solid rgba(255,255,255,.12)}
.loot{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:16px;padding:20px;border-radius:20px;
  background:radial-gradient(circle at 50% 38%,rgba(255,200,110,.16),transparent 62%),rgba(0,0,0,.2);border:1px solid rgba(255,210,122,.18)}
.loot b{display:block;font-size:25px;line-height:1.35;text-align:center}
.loot span{display:block;font-size:20px;line-height:1.45;color:#a59cbb;text-align:center}
.or{display:grid;place-items:center;font-size:22px;color:#8f84ad}
/* 2 */
.fork{display:grid;grid-template-columns:200px 80px 1fr;gap:0;align-items:center;margin-top:28px}
.drop{display:flex;flex-direction:column;align-items:center;gap:16px}
.split2{position:relative;height:210px}
.split2::before{content:"";position:absolute;left:0;top:50%;width:40px;height:4px;background:#ffb347;opacity:.65}
.split2::after{content:"";position:absolute;left:38px;top:26px;bottom:26px;width:42px;border:4px solid rgba(255,179,71,.65);border-right:0;border-radius:14px 0 0 14px}
.ways{display:grid;gap:22px}
.way{display:flex;align-items:center;gap:22px;padding:16px 22px;border-radius:16px;background:rgba(0,0,0,.2);border:1px solid rgba(255,255,255,.07)}
.way .k{font-size:24px;font-weight:700;padding:4px 18px;border-radius:999px;flex:none}
.way .k.use{color:#9ff0b3;background:rgba(94,232,122,.12);border:1.5px solid rgba(94,232,122,.5)}
.way .k.salv{color:#ffd27a;background:rgba(255,179,71,.12);border:1.5px solid rgba(255,179,71,.5)}
.way p{font-size:22px;line-height:1.5;color:#d3cce3}
.way .arrow{width:70px;flex:none}
.mats{display:grid;grid-template-columns:repeat(5,1fr);gap:16px}
.mat{display:flex;flex-direction:column;align-items:center;gap:12px;padding:20px 10px 18px;border-radius:18px;
  background:radial-gradient(circle at 50% 30%,color-mix(in srgb,var(--c) 18%,transparent),transparent 70%),rgba(0,0,0,.18);
  border:1px solid color-mix(in srgb,var(--c) 30%,transparent)}
.mat b{font-size:22px;line-height:1.35}
.mat .to{font-size:20px;color:#a59cbb}
.mat .to em{font-style:normal;font-weight:700;color:var(--c)}
.recipe{display:flex;align-items:center;gap:18px;margin:-4px 0 22px;font-size:21px;color:#a59cbb}
.recipe .slot{transform:scale(.8);margin:-8px}
.recipe b{font-weight:700}
.choices{display:grid;grid-template-columns:repeat(3,1fr);gap:26px}
.choice{display:flex;flex-direction:column;gap:14px}
.choice .hd{font-size:23px;font-weight:700;color:#ffd27a}
.cost{display:flex;align-items:center;gap:10px;font-size:22px;color:#d3cce3;padding:10px 16px;border-radius:12px;background:rgba(0,0,0,.24)}
.cost b{font:700 24px "Segoe UI",sans-serif;color:#fff;margin-right:8px}
.cost .lv{color:#b9f26b}
/* 3 */
.sockets{display:grid;grid-template-columns:repeat(4,1fr);gap:20px;margin-top:28px}
.sk{display:flex;flex-direction:column;align-items:center;gap:18px;padding:24px 12px 22px;border-radius:20px;
  background:radial-gradient(circle at 50% 0%,color-mix(in srgb,var(--c) 20%,transparent),transparent 72%),rgba(0,0,0,.18);
  border:1px solid color-mix(in srgb,var(--c) 34%,transparent)}
.sk b{font-size:30px;line-height:1.2;color:var(--c)}
.holes{display:flex;gap:22px;height:56px;align-items:center}
.hole{width:40px;height:40px;transform:rotate(45deg);border-radius:6px;border:4px solid var(--c);
  background:radial-gradient(circle,color-mix(in srgb,var(--c) 30%,transparent),#14111f 70%);box-shadow:0 0 18px color-mix(in srgb,var(--c) 55%,transparent)}
.hole.maybe{border-style:dashed;background:transparent;box-shadow:none;opacity:.6}
.hole.none{border-color:#4a4459;border-style:dashed;background:transparent;box-shadow:none}
.sk span{font-size:24px;font-weight:700}
.legend{display:flex;gap:40px;justify-content:center;margin-top:22px;font-size:21px;color:#a59cbb}
.legend div{display:flex;align-items:center;gap:16px}
.legend .hole{--c:#d3cce3;width:22px;height:22px;border-width:3px}
/* 4 */
.sources{display:grid;grid-template-columns:repeat(3,1fr);gap:20px;margin-top:28px}
.src{display:flex;align-items:center;gap:24px;padding:18px 24px;border-radius:18px;background:rgba(0,0,0,.2);border:1px solid rgba(255,255,255,.07)}
.src b{display:block;font-size:25px;line-height:1.35}
.src span{display:block;font-size:20px;line-height:1.4;color:#a59cbb}
.elements{display:grid;grid-template-columns:repeat(8,1fr);gap:12px}
.el{display:flex;flex-direction:column;align-items:center;gap:8px;padding:16px 4px 14px;border-radius:18px;
  background:radial-gradient(circle at 50% 38%,color-mix(in srgb,var(--c) 30%,transparent),transparent 66%),rgba(0,0,0,.18)}
.el img{filter:drop-shadow(0 0 12px color-mix(in srgb,var(--c) 60%,transparent))}
.el b{font-size:24px;line-height:1.3;color:var(--c)}
.two{display:grid;grid-template-columns:1.05fr 1fr;gap:34px}
.ladder{display:grid;grid-template-columns:repeat(5,1fr);gap:10px;padding:22px 18px 18px;border-radius:18px;background:rgba(0,0,0,.2)}
.rung{display:flex;flex-direction:column;align-items:center;gap:6px}
.rung .well{height:128px;display:flex;align-items:flex-end;margin-top:8px}
.rung img{filter:drop-shadow(0 0 calc(var(--g) * 1px) #ff6a3d)}
.bar{width:44px;border-radius:8px 8px 3px 3px;background:linear-gradient(180deg,#ffb347,#ff5a2a);box-shadow:0 0 16px rgba(255,120,60,.35)}
.rung b{font:700 24px "Segoe UI",sans-serif;color:#ffd2b8}
.rung span{font-size:18px;color:#a59cbb;line-height:1.3}
.rung em{font:700 18px "Segoe UI",sans-serif;font-style:normal;color:#ff9a6a}
.effects{display:grid;grid-template-columns:120px 1fr;gap:0 18px;align-items:center;padding:20px 22px;border-radius:18px;background:rgba(0,0,0,.2)}
.effects .big{grid-row:span 3;display:grid;place-items:center;height:100%;
  background:radial-gradient(circle,rgba(255,106,61,.32),transparent 66%)}
.fx{display:flex;align-items:center;gap:16px;padding:8px 0}
.fx .slot{transform:scale(.75);margin:-10px}
.fx p{font-size:22px;color:#d3cce3}
.fx p b{color:#fff}
.note{font-size:19px;color:#8f84ad;margin-top:12px}
.socketin{display:flex;align-items:center;justify-content:center;gap:24px;margin-top:28px;padding:16px;border-radius:16px;
  background:linear-gradient(90deg,transparent,rgba(255,179,71,.09),transparent);font-size:24px}
.socketin .arrow{width:80px}
.socketin .hole{--c:#ffb347;width:34px;height:34px}
/* 5 */
.cut{display:flex;align-items:flex-start;justify-content:center;gap:22px;margin-top:30px}
.cut .grp{display:flex;flex-direction:column;align-items:center;gap:16px}
.cut .pair{display:flex;gap:18px;padding:16px 18px 12px;border-radius:16px;border:2px dashed rgba(255,106,61,.45)}
.cut .op{margin-top:26px}
.cut .arrow{width:110px;margin-top:44px}
.cut .result .slot{width:112px;height:112px;margin-top:-16px}
.result .cap b{display:block;font-size:24px;color:#ff9a6a}
.dustline{display:flex;align-items:center;justify-content:center;gap:22px;margin-top:34px;padding:22px;border-radius:18px;background:rgba(0,0,0,.2)}
.dustline .arrow{width:90px}
.dustline .tx b{display:block;font-size:25px;line-height:1.4}
.dustline .tx span{display:block;font-size:21px;line-height:1.5;color:#a59cbb}
.foot{display:flex;align-items:center;justify-content:center;gap:18px;margin-top:40px;color:#7d7592;
  font:700 17px/1 "Segoe UI",sans-serif;letter-spacing:.36em}
.foot img{opacity:.9}
"""


def body():
    rarity_pills = '<span class="chev">›</span>'.join(
        f'<span class="pill" style="--c:{colour}">{thai}</span>' for thai, colour, _ in RARITY.values())

    gear = ''.join(
        f'<div style="--c:{RARITY[r][1]}"><div class="frame">{img(TEX[t], s)}</div><b>{label}</b><span>{RARITY[r][0]}</span></div>'
        for t, s, label, r in [('sword', 96, 'ดาบ', 'UNCOMMON'), ('bow', 96, 'ธนู', 'RARE'),
                               ('shield', 88, 'โล่', 'EPIC'), ('chestplate', 96, 'ชุดเกราะ', 'MYTHIC')])
    blocked = ''.join(f'<div class="no"><div class="ic">{img(TEX[t], 48)}</div><b>{label}</b></div>' for t, label in BLOCKED)

    showcase_rolls = [('sharp', .84), ('fleet', .90), ('swift', .82), ('vigor', .85)]
    fire_label, fire_percent, fire_base = FIRE_BONUS[0]
    showcase = tooltip(item_name('ดาบเหล็ก', showcase_rolls), 'MYTHIC', rolled('MYTHIC', showcase_rolls),
                       gems=[('fire', 2, stat(gem_value(fire_base, 2), fire_percent, fire_label))], empty=1)
    assert 2 <= CONFIG['rarities']['MYTHIC']['sockets'][1]

    realm = {'zombie': ('ซอมบี้', 'UNCOMMON'), 'skeleton': ('โครงกระดูก', 'RARE'),
             'blaze': ('เบลซ', 'EPIC'), 'enderman': ('เอนเดอร์แมน', 'MYTHIC')}
    mobs = ''.join(mob(t, name, r, i) for i, (t, (name, r)) in enumerate(realm.items()))
    assert set(CONFIG['elites']['weights']['NORMAL']) == {'UNCOMMON', 'RARE'}
    assert set(CONFIG['elites']['weights']['NETHER']) | set(CONFIG['elites']['weights']['THE_END']) == {'EPIC', 'MYTHIC'}
    named = ', '.join(f'<b style="color:{RARITY[r][1]}">{RARITY[r][0]}</b>' for r in ['UNCOMMON', 'RARE', 'EPIC', 'MYTHIC'])

    mats = ''.join(
        f'<div class="mat" style="--c:{colour}">{slot(TEX["mat_" + r])}<b>{material}</b>'
        f'<div class="to">ได้เกรด <em>{thai}</em></div></div>' for r, (thai, colour, material) in RARITY.items())

    base = CONFIG['rarities']['RARE']['reforge']
    options = [[('sharp', .45), ('swift', .50), ('fleet', .40)],
               [('sweep', .55), ('vigor', .35), ('lucky', .40)],
               [('heavy', .60), ('sharp', .56), ('vigor', .48)]]
    rare_sockets = CONFIG['rarities']['RARE']['sockets']
    assert rare_sockets == [1, 1]
    choices = ''.join(
        f'<div class="choice"><div class="hd">ตัวเลือก {i + 1}</div>'
        f'{tooltip(item_name("ดาบเหล็ก", rolls), "RARE", rolled("RARE", rolls), empty=1, small=True)}'
        f'<div class="cost">{img(TEX["mat_RARE"], 40)}<b>×{base["materials"] * m}</b>{img(TEX["xp"], 32)}'
        f'<span class="lv">{base["levels"] * m} เลเวล</span></div></div>'
        for i, (rolls, m) in enumerate(zip(options, CONFIG['reforge']['choice-multipliers'])))

    def holes(low, high):
        cells = ['<i class="hole"></i>'] * low + ['<i class="hole maybe"></i>'] * (high - low)
        return ''.join(cells) or '<i class="hole none"></i>'
    sockets = ''.join(
        f'<div class="sk" style="--c:{RARITY[r][1]}"><b>{RARITY[r][0]}</b><div class="holes">{holes(*CONFIG["rarities"][r]["sockets"])}</div>'
        f'<span>{"–".join(map(str, sorted(set(CONFIG["rarities"][r]["sockets"]))))} ช่อง</span></div>'
        for r in ['UNCOMMON', 'RARE', 'EPIC', 'MYTHIC'])

    elements = ''.join(f'<div class="el" style="--c:{c}">{img(gem(e, 3), 64)}<b>{t}</b></div>' for e, t, c in GEMS)
    multipliers = CONFIG['gems']['tier-multipliers']
    ladder = ''.join(
        f'<div class="rung" style="--g:{4 + tier * 4}">{img(gem("fire", tier), 64)}<b>{ROMAN[tier - 1]}</b>'
        f'<span>{TIER_NAMES[tier - 1]}</span><div class="well"><div class="bar" style="height:{round(multipliers[tier - 1] * 34)}px"></div></div>'
        f'<em>×{multipliers[tier - 1]:g}</em></div>' for tier in range(1, 6))
    effects = ''.join(
        f'<div class="fx">{slot(TEX[t])}<p>{where} <b>{stat(value, percent, label)}</b></p></div>'
        for t, where, (label, percent, value) in zip(['sword', 'bow', 'chestplate'],
                                                     ['ใส่ในอาวุธ', 'ใส่ในธนู', 'ใส่ในชุดเกราะ'], FIRE_BONUS))

    dust = CONFIG['gems']['cut-dust'][0]
    cut_material = CONFIG['gems']['cut-materials'][0]

    return f"""
<div class="embers">{embers(46, 840, 560, 3, (220, 100, 800, 500))}</div>
<header class="hero">
  <div>
    <div class="kicker">EVERGARDEN · FORGE</div>
    <h1>ระบบ Forge <em>(รีฟอร์จ)</em></h1>
    <p class="lead">Forge คือการรีฟอร์จอุปกรณ์ เช่น ดาบ ธนู โล่ ชุดเกราะ หรืออุปกรณ์อื่น ๆ เพื่อให้ได้<b>ค่าพิเศษแบบสุ่ม</b>
      จนได้<b>ไอเท็มยูนีค</b>ที่เป็นของคุณเองและไม่ซ้ำใคร</p>
    <div class="gear">{gear}</div>
  </div>
  <div class="showcase">
    <div class="sc-label">ตัวอย่างไอเท็มหลังรีฟอร์จ</div>
    <div class="hover">{slot(TEX['sword'], rarity='MYTHIC')}{showcase}</div>
  </div>
</header>
<div class="grades"><span class="lbl">เกรดของอุปกรณ์</span>{rarity_pills}</div>
<div class="blocked"><div class="lbl">ใช้กับ Forge ไม่ได้<span>รีฟอร์จ ย่อย และใส่ Gem ไม่ได้</span></div>{blocked}</div>

<section class="card">
  <h2><span class="num">1</span>หาวัตถุดิบ</h2>
  <p class="say">หาวัตถุดิบได้จากมอน <b>Elite</b> (มอนที่ชื่อมีสี {named}) และจากหีบในสิ่งก่อสร้างต่าง ๆ ของ Minecraft</p>
  <div class="hunt">
    <div>
      <div class="mobs">{mobs}</div>
      <div class="realms"><span>โลกปกติ</span><span>เนเธอร์ · ดิเอนด์</span></div>
    </div>
    <div class="loot">{img(TEX['chest'], 112)}<div><b>หีบในสิ่งก่อสร้าง</b><span>ต่าง ๆ ของ Minecraft</span></div></div>
  </div>
</section>

<section class="card">
  <h2><span class="num">2</span>ใช้ต่อ หรือย่อยไปรีฟอร์จ</h2>
  <p class="say">เมื่อฆ่า Elite จะได้<b>อุปกรณ์มีเกรด</b>มา คุณเลือกใช้ต่อ หรือย่อยเพื่อเอาวัสดุช่างไปรีฟอร์จอุปกรณ์หลักของคุณได้</p>
  <div class="fork">
    <div class="drop">{slot(TEX['sword'], rarity='UNCOMMON')}<div class="cap">อุปกรณ์มีเกรด<br>จาก Elite</div></div>
    <div class="split2"></div>
    <div class="ways">
      <div class="way"><span class="k use">ใช้ต่อ</span><p>เก็บไว้ใช้เป็นอุปกรณ์ได้เลย</p></div>
      <div class="way"><span class="k salv">ย่อย</span><div class="arrow"></div>{slot(TEX['mat_UNCOMMON'])}
        <p>ได้<b style="color:#fff">วัสดุช่าง</b> ไปรีฟอร์จอุปกรณ์หลักของคุณ</p></div>
    </div>
  </div>
  <h3>วัสดุช่างจะกำหนดเกรดของผลลัพธ์</h3>
  <div class="mats">{mats}</div>
  <h3>การรีฟอร์จมี 3 ตัวเลือก ให้เลือก 1 อย่าง<span>ผลลัพธ์สุ่มทั้งหมด · ตัวที่ 2 และ 3 ใช้วัสดุมากกว่า</span></h3>
  <div class="recipe"><span>ตัวอย่าง:</span>{slot(TEX['sword'])}<span>ดาบเหล็ก +</span>{slot(TEX['mat_RARE'])}
    <span>{RARITY['RARE'][2]} → ได้ผลลัพธ์เกรด <b style="color:{RARITY['RARE'][1]}">{RARITY['RARE'][0]}</b> 3 แบบ</span></div>
  <div class="choices">{choices}</div>
</section>

<section class="card">
  <h2><span class="num">3</span>ช่อง Gem ตามเกรด</h2>
  <p class="say">อุปกรณ์ที่มีเกรดจะมีช่องใส่ Gem ตามเกรด</p>
  <div class="sockets">{sockets}</div>
  <div class="legend"><div><i class="hole"></i>ได้แน่นอน</div><div><i class="hole maybe"></i>มีโอกาสได้</div></div>
</section>

<section class="card">
  <h2><span class="num">4</span>Gem</h2>
  <p class="say">Gem หาได้จากการขุดแร่ ฆ่ามอนทั่วไป (โอกาสน้อย) และหีบต่าง ๆ</p>
  <div class="sources">
    <div class="src">{slot(TEX['ore'])}<div><b>ขุดแร่</b><span>แร่ในเกม</span></div></div>
    <div class="src">{slot(TEX['zombie'])}<div><b>ฆ่ามอนทั่วไป</b><span>โอกาสน้อย</span></div></div>
    <div class="src">{slot(TEX['chest'])}<div><b>หีบต่าง ๆ</b><span>ในสิ่งก่อสร้าง</span></div></div>
  </div>
  <h3>มี 8 ธาตุ</h3>
  <div class="elements">{elements}</div>
  <div class="two">
    <div><h3>แต่ละธาตุมี 5 ขั้น (I ถึง V)<span>ขั้นสูงให้ผลแรงกว่า</span></h3><div class="ladder">{ladder}</div></div>
    <div><h3>ผลของ Gem ขึ้นกับอุปกรณ์ที่ใส่</h3>
      <div class="effects"><div class="big">{img(gem('fire', 1), 96)}</div>{effects}</div>
      <div class="note">ตัวอย่าง: อัญมณีเพลิง ขั้น I</div></div>
  </div>
  <div class="socketin">{img(gem('life', 3), 64)}<div class="arrow"></div><i class="hole"></i><span>ใส่ลงช่องว่างได้เลย</span></div>
</section>

<section class="card">
  <h2><span class="num">5</span>อัปเกรด Gem</h2>
  <p class="say">ใช้ Gem <b>ชนิดเดียวกันและขั้นเดียวกัน 2 เม็ด</b> + ผงอัญมณี + วัสดุช่าง ได้ Gem ขั้นถัดไป 1 เม็ด</p>
  <div class="cut">
    <div class="grp"><div class="pair">{slot(gem('fire', 1))}{slot(gem('fire', 1))}</div><div class="cap">Gem ชนิดเดียวกัน<br>ขั้นเดียวกัน 2 เม็ด</div></div>
    <span class="op">+</span>
    <div class="grp" style="margin-top:18px">{slot(TEX['dust'], count=dust)}<div class="cap">ผงอัญมณี</div></div>
    <span class="op">+</span>
    <div class="grp" style="margin-top:18px">{slot(TEX['mat_UNCOMMON'], count=cut_material if cut_material > 1 else None)}<div class="cap">วัสดุช่าง</div></div>
    <div class="arrow"></div>
    <div class="grp result" style="margin-top:18px">{slot(gem('fire', 2), 96, rarity='MYTHIC')}<div class="cap"><b>อัญมณีเพลิง II</b>ขั้นถัดไป 1 เม็ด</div></div>
  </div>
  <div class="dustline">
    {slot(gem('water', 2))}{slot(gem('earth', 1))}{slot(gem('dark', 3))}
    <div class="arrow"></div>{slot(TEX['dust'])}
    <div class="tx"><b>ผงอัญมณี</b><span>ได้จากการย่อย Gem ชนิดไหนก็ได้ และใช้ได้กับทุกชนิด</span></div>
  </div>
</section>
<div class="foot">{''.join(img(gem(e, 5), 28) for e, _, _ in GEMS[:4])}EVERGARDEN · FORGE{''.join(img(gem(e, 5), 28) for e, _, _ in GEMS[4:])}</div>
"""


def capture(name, html):
    WORK.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    source, shot = WORK / f'{name}.html', WORK / f'{name}-raw.png'
    source.write_text(html, encoding='utf8')
    shot.unlink(missing_ok=True)
    subprocess.run([str(EDGE), '--headless=new', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1',
                    f'--user-data-dir={WORK / "edge-profile"}', '--window-size=1600,6000',
                    f'--screenshot={shot}', source.as_uri()], check=True, capture_output=True, timeout=120)
    image = Image.open(shot).convert('RGB')
    box = ImageChops.difference(image, Image.new('RGB', image.size, OUTSIDE)).getbbox()
    target = OUT / f'{name}.png'
    image.crop(box).save(target, optimize=True)
    print(target, image.crop(box).size)


if __name__ == '__main__':
    capture('forge-guide', f'<!doctype html><html lang="th"><head><meta charset="utf-8"><style>{CSS}</style></head>'
                           f'<body><div class="page">{body()}</div></body></html>')
