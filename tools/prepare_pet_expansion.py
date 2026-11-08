"""Prepare the curated gameplay roster and append stable species identifiers once."""
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'evergarden/tools'))
from pet_expansion_catalog import ENTRIES

# Effects use their actual Bukkit names. Descriptions below are generated from these
# same values so the guide cannot promise a different buff from the runtime rule.
TYPES = {'SPEED':'ความเร็ว', 'HASTE':'ขุดเร็ว', 'LUCK':'โชค', 'JUMP_BOOST':'กระโดดสูง',
         'RESISTANCE':'ต้านทาน', 'NIGHT_VISION':'มองในที่มืด', 'SLOW_FALLING':'ร่อนลงช้า',
         'FIRE_RESISTANCE':'ต้านไฟ', 'WATER_BREATHING':'หายใจใต้น้ำ',
         'DOLPHINS_GRACE':'ว่ายน้ำเร็ว', 'CONDUIT_POWER':'พลังคอนดูอิต', 'STRENGTH':'พลังโจมตี',
         'REGENERATION':'ฟื้นฟู', 'ABSORPTION':'หัวใจโล่'}
CONDITIONS = {'ALWAYS':'ตลอดเวลา','SPRINTING':'ขณะวิ่ง','SNEAKING':'ขณะย่อตัว',
              'IN_WATER':'ขณะอยู่ในน้ำ','NEAR_WATER':'ใกล้น้ำ','LOW_HEALTH':'เมื่อเลือดต่ำกว่า 40%',
              'FULL_HEALTH':'เมื่อเลือดอย่างน้อย 90%','HUNGRY':'เมื่อยังหิว','FULL_FOOD':'เมื่อความหิวเต็ม',
              'ON_GRASS':'บนพื้นหญ้า','ON_STONE':'บนพื้นหิน','ON_SAND':'บนพื้นทราย',
              'ON_SNOW':'บนหิมะหรือน้ำแข็ง','ON_MOSS':'บนมอส','ON_LOGS':'บนขอนไม้',
              'ON_PATH':'บนทางเดิน','NEAR_FLOWERS':'ใกล้ดอกไม้','FOREST':'ในป่าโบราณ',
              'DARK':'ในที่มืด','SKY':'ในที่โล่ง','BURNING':'ขณะติดไฟ','FALLING':'เมื่อตกเกิน 3 บล็อก',
              'STILL':'ขณะยืนพัก'}
ACTION = {'HEAL':'ฟื้นเลือด {value} HP','FOOD':'เติมความหิว {value} แต้ม',
          'SATURATION':'เพิ่มความอิ่ม {value} แต้ม','CLEAR_FIRE':'ดับไฟ',
          'CLEANSE_POISON':'ล้างพิษ','CLEANSE_WITHER':'ล้าง Wither','CLEANSE_BLINDNESS':'ล้างตาบอด',
          'CLEANSE_NAUSEA':'ล้างคลื่นไส้','CLEANSE_HUNGER':'ล้าง Hunger',
          'THAW':'ลดการแข็งตัว {value} tick','ABSORPTION':'รับหัวใจโล่ I 20 วินาที'}
BUFFS = {}


def b(effect, condition='ALWAYS', amplifier=0):
    return dict(type=effect, condition=condition, amplifier=amplifier,
                ticks=600 if effect=='NIGHT_VISION' else 40 if effect=='SLOW_FALLING' else 60)


def p(action, every, condition='ALWAYS', value=1):
    return dict(action=action,every=every,condition=condition,value=value)


def add(species, name, boosts=(), pulses=(), *, guard=1.0, hit='SLOWNESS', hit_seconds=1.5,
        hit_chance=.35, hit_name='สะกิดขี้เล่น', hit_amp=0, first=1.0, lifesteal=0,
        aura=None, armor=1.0):
    descriptions=[f'{TYPES[x["type"]]} {("I","II","III")[x["amplifier"]]} {CONDITIONS[x["condition"]]}' for x in boosts]
    descriptions += [f'{ACTION[x["action"]].format(value=x["value"])} ทุก {x["every"]} วินาที {CONDITIONS[x["condition"]]}' for x in pulses]
    if guard<1:descriptions.append(f'รับดาเมจจากการโจมตีลด {round((1-guard)*100)}% ในโหมดช่วยสู้ เมื่อสัตว์อยู่ในระยะ 8 บล็อก')
    if aura:descriptions.append(f'เผยตำแหน่งมอนสเตอร์ที่ต่อสู้ได้ในรัศมี {aura["radius"]} บล็อก {CONDITIONS[aura["condition"]]}')
    hit_text=f'{round(hit_chance*100)}% ทำให้ศัตรู '+{'SLOWNESS':'ช้าลง','WEAKNESS':'อ่อนแรง','GLOWING':'เรืองแสง','POISON':'ติดพิษ','BLINDNESS':'ตาพร่า','FIRE':'ติดไฟ','FREEZE':'หนาวจัด'}[hit]+f' {hit_seconds:g} วินาที · คูลดาวน์ 4 วินาที'
    if first>1:hit_text+=f' · โจมตีเป้าหมายใหม่ ×{first:g}'
    if lifesteal:hit_text+=f' · ดูดเลือด {round(lifesteal*100)}% ของดาเมจจริง'
    BUFFS[species]=dict(passiveName=name,passive=' · '.join(descriptions),traitName=hit_name,trait=hit_text,
                        boosts=list(boosts),pulses=list(pulses),guard=guard,armor=armor,
                        hit=dict(type=hit,seconds=hit_seconds,chance=hit_chance,amplifier=hit_amp,
                                 cooldown=80,first=first,lifesteal=lifesteal),aura=aura)


# Seventeen rare profiles: compound exploration powers with bounded combat traits.
add('prism_kirin','ม่านแสงปริซึม',[b('SPEED','NEAR_FLOWERS'),b('LUCK','NEAR_FLOWERS')],[p('HEAL',30)],hit='GLOWING',hit_seconds=4,hit_chance=.6,hit_name='เขารุ้งชี้เป้า',first=1.35)
add('clockwork_red_panda','หัวใจไขลาน',[b('HASTE'),b('SPEED','SPRINTING')],[p('SATURATION',45,value=.5)],hit='SLOWNESS',hit_seconds=2,hit_chance=.55,hit_name='เฟืองล็อกจังหวะ',hit_amp=1)
add('nebula_whale','อ้อมกอดเนบิวลา',[b('SLOW_FALLING','FALLING'),b('NIGHT_VISION','DARK')],[p('ABSORPTION',60)],hit='WEAKNESS',hit_seconds=4,hit_chance=.5,hit_name='คลื่นสุญญากาศ')
add('ember_salamander','ประกายเตาอุ่น',[b('FIRE_RESISTANCE','BURNING'),b('HASTE','ON_STONE')],[p('CLEAR_FIRE',8,'BURNING')],hit='FIRE',hit_seconds=3,hit_chance=.65,hit_name='หางสะเก็ดไฟ')
add('moonstone_sphinx','ปริศนาผู้พิทักษ์',[b('NIGHT_VISION','DARK'),b('RESISTANCE','SNEAKING'),b('LUCK','DARK')],hit='BLINDNESS',hit_seconds=2,hit_chance=.4,hit_name='ปริศนาจันทรา',first=1.4)
add('thunder_quetzal','กระแสลมยอดไม้',[b('JUMP_BOOST','SPRINTING'),b('SLOW_FALLING','FALLING'),b('SPEED','SKY')],hit='SLOWNESS',hit_seconds=1,hit_chance=.7,hit_name='ขนปีกอัสนี',hit_amp=2)
add('sakura_baku','ฝันหวานใต้ซากุระ',[],[p('CLEANSE_NAUSEA',10),p('CLEANSE_HUNGER',15),p('HEAL',30,'NEAR_FLOWERS',1.5)],hit='WEAKNESS',hit_seconds=3,hit_chance=.65,hit_name='งวงกินฝัน')
add('obsidian_basilisk','หนังแก้วภูผา',[b('NIGHT_VISION','DARK')],guard=.92,armor=.8,hit='SLOWNESS',hit_seconds=2,hit_chance=.5,hit_name='จ้องหินดำ',hit_amp=2)
add('glacier_narwhal','พรเกลียวไพลิน',[b('DOLPHINS_GRACE','IN_WATER'),b('WATER_BREATHING','NEAR_WATER'),b('SPEED','ON_SNOW')],hit='FREEZE',hit_seconds=5,hit_chance=.65,hit_name='เขาเกลียวเยือกแข็ง')
add('lotus_mantis','จังหวะใบบัว',[b('HASTE','SNEAKING'),b('JUMP_BOOST','ON_GRASS')],[p('HEAL',20,'LOW_HEALTH',.5)],hit='WEAKNESS',hit_seconds=2,hit_chance=.55,hit_name='เคียวกลีบบัว',first=1.5)
add('rune_armadillo','วงแหวนอักขระ',[b('RESISTANCE','ON_STONE')],[p('ABSORPTION',60,'SNEAKING')],armor=.75,hit='SLOWNESS',hit_seconds=3,hit_chance=.5,hit_name='กลิ้งผนึกศิลา')
add('astral_seahorse','ลำนำกระแสดาว',[b('WATER_BREATHING'),b('CONDUIT_POWER','IN_WATER'),b('LUCK','SNEAKING')],hit='GLOWING',hit_seconds=5,hit_chance=.65,hit_name='ฟองดาราล้อมเป้า')
add('sunforge_lion','หัวใจสุริยัน',[b('STRENGTH','LOW_HEALTH'),b('FIRE_RESISTANCE','SKY')],hit='FIRE',hit_seconds=2.5,hit_chance=.6,hit_name='ตะปบสุริยัน',first=1.4)
add('paper_crane','พรปีกกระดาษ',[b('SLOW_FALLING','FALLING'),b('JUMP_BOOST'),b('LUCK','SKY')],hit='WEAKNESS',hit_seconds=3.5,hit_chance=.55,hit_name='ริบบิ้นพันธนา')
add('opal_chimera','สามจิตผู้พิทักษ์',[b('SPEED','SPRINTING'),b('STRENGTH','LOW_HEALTH'),b('NIGHT_VISION','DARK')],hit='POISON',hit_seconds=3,hit_chance=.45,hit_name='สามหัวประสาน',first=1.3)
add('twilight_moth_dragon','เกล็ดฝุ่นสนธยา',[b('NIGHT_VISION'),b('SLOW_FALLING','FALLING')],[p('CLEANSE_WITHER',30,'DARK')],hit='WEAKNESS',hit_seconds=4.5,hit_chance=.5,hit_name='ละอองปีกสนธยา',lifesteal=.08)
add('verdant_capricorn','พรผาและธาร',[b('JUMP_BOOST','ON_STONE',1),b('WATER_BREATHING','IN_WATER')],[p('HEAL',40,'NEAR_WATER',2)],hit='SLOWNESS',hit_seconds=3.5,hit_chance=.5,hit_name='เขาคู่ธารมรกต')

# Forty common profiles: useful themed niches instead of identical unconditional buffs.
add('strawberry_hamster','กระเป๋าผลไม้',[],[p('FOOD',45,'HUNGRY')],hit_name='แก้มเบอร์รีหนึบ')
add('cocoa_capybara','พักริมธาร',[b('RESISTANCE','STILL')],[p('HEAL',35,'NEAR_WATER')],hit='WEAKNESS',hit_name='สะกิดใจเย็น')
add('honey_otter','เสบียงนักว่าย',[b('WATER_BREATHING','IN_WATER')],[p('SATURATION',30,'IN_WATER',.8)],hit_name='อุ้งมือน้ำผึ้ง')
add('peach_shiba','เพื่อนวิ่งสุขภาพดี',[b('SPEED','FULL_HEALTH')],[p('HEAL',45,value=.5)],hit='GLOWING',hit_seconds=2,hit_name='เห่าชี้เป้า')
add('blueberry_bat','โซนาร์เบอร์รี',[b('NIGHT_VISION','DARK')],[p('CLEANSE_BLINDNESS',20,'DARK')],hit='GLOWING',hit_seconds=3,hit_name='เสียงสะท้อนบลูเบอร์รี')
add('mint_ferret','ย่องกลิ่นมินต์',[b('SPEED','SNEAKING')],[p('CLEANSE_POISON',60)],hit='WEAKNESS',hit_seconds=2,hit_name='ละอองมินต์')
add('maple_raccoon','กระเป๋าใบเมเปิล',[b('LUCK','FOREST')],[p('FOOD',60,'FOREST')],hit='BLINDNESS',hit_seconds=1,hit_name='ใบเมเปิลบังตา')
add('lavender_cat','ครางกล่อมพัก',[b('NIGHT_VISION','DARK')],[p('HEAL',40,'STILL')],hit='WEAKNESS',hit_seconds=1.5,hit_name='อุ้งเท้าลาเวนเดอร์')
add('acorn_squirrel','เส้นทางบนยอดไม้',[b('JUMP_BOOST','FOREST'),b('SPEED','ON_LOGS')],hit_name='โอ๊กกลิ้งสะดุด')
add('buttercream_corgi','ขาสั้นแรงเต็ม',[b('SPEED','ON_PATH')],[p('SATURATION',45,'FULL_FOOD')],hit='GLOWING',hit_seconds=2.5,hit_name='เห่าเรียกเพื่อน')
add('pebble_pangolin','เกล็ดเพื่อนเหมือง',[b('HASTE','ON_STONE')],guard=.95,armor=.9,hit_name='เกล็ดกรวดสะกิด')
add('moss_toad','หมอนมอสชุ่มน้ำ',[b('WATER_BREATHING','NEAR_WATER')],[p('HEAL',40,'ON_MOSS')],hit='POISON',hit_seconds=1.5,hit_chance=.25,hit_name='ละอองมอส')
add('sunflower_duck','ย่างก้าวริมน้ำ',[b('SPEED','NEAR_WATER'),b('LUCK','ON_GRASS')],hit_name='จิกเมล็ดทานตะวัน')
add('teacup_chick','น้ำชาอุ่นใจ',[b('SLOW_FALLING','FALLING')],[p('HEAL',50,'FULL_FOOD')],hit='WEAKNESS',hit_seconds=2.5,hit_name='ไออุ่นน้ำชา')
add('pumpkin_fox','ย่องหาเมล็ดทอง',[b('LUCK','SNEAKING'),b('NIGHT_VISION','FOREST')],hit='BLINDNESS',hit_seconds=1.5,hit_name='ฟักทองบังตา')
add('rain_frog','กระโดดรับหยดน้ำ',[b('JUMP_BOOST','IN_WATER')],[p('CLEAR_FIRE',20,'NEAR_WATER')],hit_name='หยดน้ำสะดุด')
add('sleepy_sloth','พักอย่างมีแรง',[b('HASTE','SNEAKING')],[p('HEAL',45,'STILL',1.5)],hit='WEAKNESS',hit_seconds=3,hit_chance=.3,hit_name='หาวชวนง่วง')
add('cherry_red_panda','วิ่งชมซากุระ',[b('SPEED','FOREST'),b('LUCK','NEAR_FLOWERS')],hit='GLOWING',hit_seconds=3.5,hit_name='กลีบซากุระชี้เป้า')
add('cotton_chinchilla','ปุยนุ่มรับแรงตก',[b('SLOW_FALLING','FALLING'),b('JUMP_BOOST','SNEAKING')],hit_name='ปุยฝ้ายหนึบ')
add('bubble_seal','ฟองอุ่นขั้วโลก',[b('DOLPHINS_GRACE','IN_WATER')],[p('THAW',10,'ON_SNOW',100)],hit='FREEZE',hit_seconds=2,hit_name='ฟองน้ำเย็น')
add('seashell_crab','ก้ามนักสำรวจน้ำ',[b('HASTE','IN_WATER')],guard=.97,armor=.9,hit_name='ก้ามเปลือกหอย')
add('kelp_turtle','เสบียงใต้ทะเล',[b('WATER_BREATHING','NEAR_WATER')],[p('ABSORPTION',60,'IN_WATER')],hit='WEAKNESS',hit_seconds=2,hit_chance=.3,hit_name='พันสาหร่าย')
add('tangerine_gecko','ไต่หินส้มจี๊ด',[b('HASTE','ON_STONE'),b('JUMP_BOOST','ON_STONE')],hit='GLOWING',hit_seconds=2,hit_chance=.4,hit_name='เกล็ดส้มสะท้อน')
add('dandelion_meerkat','ยามกลางสวน',[b('LUCK','SKY')],[p('CLEANSE_BLINDNESS',30)],hit='GLOWING',hit_seconds=4,hit_name='สายตายามดอกหญ้า')
add('snowdrop_ermine','เส้นทางสโนว์ดรอป',[b('SPEED','ON_SNOW'),b('NIGHT_VISION','ON_SNOW')],hit='FREEZE',hit_seconds=2.5,hit_name='หางน้ำค้างแข็ง')
add('cinnamon_alpaca','ขนอุ่นยามเดินทาง',[b('RESISTANCE','ON_SNOW')],[p('FOOD',80,'ON_GRASS')],hit_name='ขนอัลปากาหนึบ')
add('lotus_snail','พักในใบบัว',[],[p('HEAL',20,'SNEAKING',.5),p('CLEANSE_POISON',45,'NEAR_WATER')],hit_name='เมือกใบบัว',hit_seconds=2)
add('pebble_pika','ผู้ช่วยข้ามผา',[b('JUMP_BOOST','ON_STONE'),b('SLOW_FALLING','FALLING')],hit='WEAKNESS',hit_seconds=1,hit_name='เสียงเตือนพิกา')
add('coral_shrimp','เสบียงปะการัง',[b('CONDUIT_POWER','IN_WATER')],[p('FOOD',80,'IN_WATER')],hit_name='หนวดปะการังสะดุด',hit_seconds=1)
add('dew_dragonfly','ปีกน้ำค้างช่างธาร',[b('SLOW_FALLING','FALLING'),b('HASTE','NEAR_WATER')],hit='GLOWING',hit_seconds=2.5,hit_chance=.4,hit_name='หยดน้ำชี้เป้า')
add('starling_sparrow','ลมใต้ปีกเล็ก',[b('SPEED','SKY'),b('SLOW_FALLING','FALLING')],hit='WEAKNESS',hit_seconds=1.5,hit_chance=.4,hit_name='จิกเบี่ยงจังหวะ')
add('apricot_tapir','เสบียงป่าแอปริคอต',[],[p('FOOD',60,'FOREST'),p('CLEANSE_HUNGER',20,'FOREST')],hit_name='งวงผลไม้ขวางทาง')
add('sage_koala','พักพิงต้นเสจ',[b('RESISTANCE','ON_LOGS')],[p('HEAL',30,'FOREST',.5)],hit='WEAKNESS',hit_seconds=2,hit_chance=.4,hit_name='อุ้งมือใบเสจ')
add('chestnut_boar','ฮึดสู้เกาลัด',[b('STRENGTH','LOW_HEALTH')],[p('SATURATION',60,'ON_GRASS')],hit_name='จมูกเกาลัดดัน',first=1.2)
add('papaya_toucan','ตานักหาอาหาร',[b('LUCK','FOREST'),b('NIGHT_VISION','FOREST')],hit='GLOWING',hit_seconds=3,hit_chance=.4,hit_name='จะงอยมะละกอชี้เป้า')
add('lantern_firefly','ตะเกียงพกพา',[b('NIGHT_VISION','DARK')],hit='GLOWING',hit_seconds=4,hit_chance=.4,hit_name='ประกายตะเกียง',aura=dict(condition='DARK',radius=5))
add('pearl_oyster','ไข่มุกคุ้มครอง',[b('LUCK','IN_WATER')],[p('ABSORPTION',90,'NEAR_WATER')],hit='WEAKNESS',hit_seconds=2.5,hit_chance=.3,hit_name='ม่านมุก')
add('clover_fawn','พรสนามโคลเวอร์',[b('SPEED','ON_GRASS')],[p('HEAL',45,'NEAR_FLOWERS')],hit='GLOWING',hit_seconds=2,hit_chance=.3,hit_name='แตะใบโคลเวอร์')
add('dusk_jerboa','ก้าวน้อยกลางทราย',[b('JUMP_BOOST','ON_SAND'),b('NIGHT_VISION','DARK')],hit_name='ทรายใต้เท้าจิ๋ว',hit_seconds=1,hit_chance=.4)
add('ribbon_lemur','ไต่เถาวัลย์ริบบิ้น',[b('JUMP_BOOST','FOREST'),b('HASTE','ON_LOGS')],hit_name='หางริบบิ้นพันขา',hit_seconds=2.5)


def main():
    assert len(ENTRIES)==len(BUFFS)==57 and {e['id'] for e in ENTRIES}==set(BUFFS)
    roster=[dict(entry,**BUFFS[entry['id']]) for entry in ENTRIES]
    target=ROOT/'evergarden/src/main/resources/pet-expansion.json'
    target.write_text(json.dumps(dict(format=1,species=roster),ensure_ascii=False,indent=2)+'\n',encoding='utf8')
    catalog=ROOT/'evergarden/src/main/java/com/example/voidscape/pet/PetCatalog.java'
    text=catalog.read_text(encoding='utf8')
    marker='        // BEGIN EXPANSION SPECIES'
    if marker not in text:
        end='        AURORA_JELLY("Aurora Jelly", "แมงกะพรุนออโรร่า");'
        assert end in text
        rows=[f'        {e["id"].upper()}("{e["display"]}", "{e["thai"]}"'+(', Rarity.RARE)' if e['rarity']=='rare' else ')') for e in ENTRIES]
        text=text.replace(end,end[:-1]+',\n'+marker+'\n'+',\n'.join(rows)+';\n        // END EXPANSION SPECIES')
        catalog.write_text(text,encoding='utf8')
    print(f'Prepared {len(roster)} profiles, {sum(e["rarity"]=="rare" for e in roster)} rare, 40 common')


if __name__=='__main__':main()
