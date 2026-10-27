# ระบบเวทธาตุและ Skill Tree

อัปเดต **1.19.0**: เพิ่มคทาไม้คราฟต์ได้ 1→2 ช่อง สูงสุด Rare,
ประทับคัมภีร์ข้ามธาตุได้พร้อมตัวคูณดาเมจ 1.5x/0.5x และถอนเวทคืนเป็นคัมภีร์
ด้วย `/spell extract` ดู [กติกาปัจจุบันและสูตรคราฟต์](amethystwood-and-affinity.md)
รายละเอียดด้านล่างเป็นกติกาของรุ่น 1.17.0 ก่อนการเปลี่ยนแปลงนี้

Advance Magic **1.17.0-loot-only-staffs** + Evergarden
**3.0.0-e2.97-elemental-staff-loot** · 19 ตุลาคม 2026

ระบบนี้ต่อจากโมเดลคทาแปดธาตุเดิม เพิ่มเวท 58 ชนิด คัมภีร์ ช่องเวทประจำคทา
ปฏิกิริยาธาตุ และ Skill Tree ประจำผู้เล่น ตัวเลขเป็นค่าเริ่มต้นสำหรับ playtest
ยังไม่ใช่ผลบาลานซ์จากผู้เล่นจริงหรือการทดสอบผู้เล่นจำนวนมาก

## ติดตั้ง

หยุดเซิร์ฟเวอร์ สำรองข้อมูล แล้วแทน **ทั้ง** `advance-magic.jar` และ `evergarden.jar`
ด้วยไฟล์จาก release ชุดเดียวกัน จากนั้นเริ่มเซิร์ฟเวอร์ใหม่ ใช้ Java 25 และ Paper 26.2
แพ็กโมเดล Java/Bedrock ใช้ไฟล์ที่มีอยู่แล้วในโปรเจกต์ ไม่มีโมเดลคทาชุดใหม่
Geyser/Floodgate ใช้การติดตั้งเดิม

กล่องเก่าที่เคยเติมของแล้วจะไม่ถูกเติมใหม่ คัมภีร์เริ่มพบในกล่องโครงสร้างที่ระบบ
สร้างสมบัติใหม่หลังอัปเดต รวมกล่อง sanctuary ที่ยังไม่ได้สร้าง loot ครั้งแรก
ไม่ต้องรีเซ็ตโลก และไม่มีการสแกนเติมของลงกล่องผู้เล่น

## เริ่มเล่น

1. หาคทาจากรางวัล Vault หรือกล่องโครงสร้าง Evergarden ตามตารางด้านล่าง คทาธาตุคราฟไม่ได้
2. สำรวจกล่องโครงสร้าง Evergarden เพื่อหาคัมภีร์
3. ถือคทาในมือหลัก ใช้ `/spell imprint` คลิกคัมภีร์ในกระเป๋าด้านล่าง
   เลือกช่อง ดูตัวอย่าง แล้วกดยืนยัน คัมภีร์ถูกใช้หนึ่งชิ้นเมื่อสำเร็จ
4. คลิกขวาหรือ Use Item เพื่อร่าย ย่อ+Use เพื่อสลับเวท
   ใช้ `/spell select 1` ถึง `/spell select 5` หรือ GUI เป็นทางสำรอง
5. เปิด `/skilltree` เพื่อพัฒนาตัวละคร

| แหล่งคทา | โอกาส |
| --- | --- |
| Vault | 10% ต่อรางวัลหลักหนึ่งครั้ง |
| กล่อง Sky Whale / Observatory / Hanging Garden | 3% ต่อกล่องที่สร้างสมบัติใหม่ |
| กล่องโครงสร้าง Evergarden ในโลกปกติ (`NORMAL`) | 1% ต่อกล่องที่สร้างสมบัติใหม่ |

เมื่อสุ่มได้คทาจะได้รับคทาว่างระดับ Apprentice หนึ่งอัน มี 3 ช่องเวท สุ่มหนึ่งในแปดธาตุด้วยโอกาสเท่ากัน
โอกาสข้างต้นเป็นโอกาสได้คทารวมทุกธาตุ ไม่ใช่โอกาสแยกธาตุ คทาต้องประทับคัมภีร์ก่อนร่าย
กล่องที่สร้างสมบัติแล้วจะไม่สุ่มคทาซ้ำเมื่อเปิดใหม่ และกล่องของผู้เล่นจะไม่ถูกเติมของ
สูตรคทาธาตุทั้งแปดแบบถูกถอนออกจากเซิร์ฟเวอร์และสมุดสูตร รวมถึงปิดการคราฟอัตโนมัติด้วย Crafter
คทาเดิมในกระเป๋าและคทาที่ประทับเวทไว้แล้วยังใช้งานได้ตามเดิม

ใช้คทามือหลักในการร่ายและสลับเวท มือรองไม่ร่ายซ้ำ และคทาไม่ทำดาเมจตีปกติ
คทาที่ไม่มีเวทต้องประทับคัมภีร์ก่อน ไม่แจกเวทฟรีจาก Codex หรือ Skill Tree

## ช่องเวทและข้อมูลไอเทม

| ระดับ | ช่อง | รับคัมภีร์สูงสุด | อัปเกรดไปขั้นต่อไป |
| --- | --- | --- | --- |
| Apprentice | 3 | Rare | 8 Diamond + 16 Amethyst Shard |
| Arcane | 4 | Epic | 1 Netherite Ingot + 1 Nether Star |
| Mythic | 5 | Legendary | สูงสุด |

ใช้ `/staff upgrade` หรือปุ่มใน GUI เวทเดิม ชื่อ เอนชานต์ และ PDC ที่ไม่เกี่ยวข้องอยู่ครบ
การเขียนทับมีหน้าต่างยืนยันและทำให้เวทเก่าสูญหายจากคทานั้น ไม่คืนคัมภีร์
ไม่ประทับซ้ำ ไม่ข้ามธาตุ และไม่ข้ามระดับคทา

`/spell` เปิดหน้าจัดการ คลิกซ้ายเลือกเวท คลิกขวาเวทแล้วคลิกอีกช่องเพื่อสลับลำดับ
หรือใช้ `/spell reorder 1 2` การเลือกไม่เสียมานาหรือรีเซ็ตคูลดาวน์
`/spell codex` เป็นประวัติค้นพบประจำผู้เล่น เวทที่ค้นพบแล้วยังต้องอยู่บนคทาจึงร่ายได้
`/staff protect` ป้องกันการกดทิ้งโดยไม่ตั้งใจ แต่ไม่ได้ป้องกันการตาย ไฟ ลาวา หรือการสูญหายอื่น

หน้าประทับใช้สำเนาแสดงผล ของจริงยังอยู่ในกระเป๋า ยืนยันแล้วตรวจ snapshot ของ
คทาและคัมภีร์ซ้ำก่อนเขียนช่องของจริงใน server tick เดียว ไม่มีการเขียน ItemStack
เดิมกลับใน task ภายหลัง ควรสำรองโลกตามปกติเพราะระบบ inventory ไม่ใช่ฐานข้อมูลธุรกรรม
ที่รับประกันความทนทานต่อไฟดับระหว่างการ save

คทาเก่าที่รู้จักจะเปลี่ยนรูปลักษณ์ตาม migration เดิม แล้วเริ่มด้วยช่องว่างระดับ Apprentice
ข้อมูลเวทเก่า mastery อัปเกรด ความทนทาน และข้อมูลเจ้าของเก็บไว้ ไม่แปลงเป็นคัมภีร์ฟรี
อาวุธตำนานห้าชนิด ไม้เท้าบิน และ API เวทเดิมยังแยกจากระบบนี้

## แหล่งคัมภีร์

| กล่อง | โอกาสคัมภีร์หนึ่งชิ้น | น้ำหนัก Basic / Rare / Epic / Legendary |
| --- | --- | --- |
| โครงสร้างสำรวจ | 25% | 65 / 28 / 6 / 1 |
| Whale / Observatory / Hanging Garden | 40% | 45 / 35 / 17 / 3 |
| โครงสร้างบอส | 65% | 25 / 35 / 30 / 10 |
| Overworld Sanctuary | 65% | 25 / 35 / 30 / 10 |

เมื่อผ่านโอกาสคัมภีร์ จะสุ่ม rarity ตามน้ำหนัก แล้วสุ่มเวทใน rarity นั้น
ทุกธาตุและทั้ง 58 เวทเข้าถึงได้จากตารางนี้ ไม่ดรอปคัมภีร์จากมอนสเตอร์
ใช้ RNG แยกจากของเดิมในกล่องและไม่แทนที่ของเดิม ถ้ากล่องเต็มจะไม่ทิ้งของออกมา
การพยายามสุ่มถูกบันทึกใน chunk จึงไม่ reroll เมื่อเปิดซ้ำ ทุบ/วางกล่อง หรือ restart
ตั้งค่าที่ `elemental-system.scroll-loot` ของ Advance Magic

## Skill Tree ที่ออกแบบเพิ่ม

เริ่มต้นมี 3 แต้ม ปราบบอสได้ 1 แต้ม และเปิดกล่องโครงสร้างครั้งแรกได้ 1 แต้ม
กล่องหนึ่งใบให้รางวัลร่วมกันครั้งเดียว กล่องคู่คิดเป็นกล่องเดียว
มอนทั่วไปและการค้นพบคัมภีร์ไม่เพิ่ม XP หรือแต้มอีกแล้ว
แต้มใหม่เพิ่มตรงให้ผู้เล่น ใช้ปลดสกิลได้ทันที และไม่เปลี่ยน XP เอนชานต์ของ Minecraft
Arcane XP เดิม แต้มเดิม และสกิลที่เคยอัปไว้ยังคงอยู่
ดูรายละเอียดที่ [รางวัลบอสและแต้มสำรวจ](boss-orbs-and-skill-points.md)

| สาย | ขั้นสูงสุด | ผลต่อขั้น | เงื่อนไข |
| --- | --- | --- | --- |
| Power | 5 | พลังเวท +4% | — |
| Mana | 10 | มานาสูงสุด +4 | — |
| Regen | 10 | ฟื้นมานา +10% | Mana 1 |
| Efficiency | 10 | ลดค่ามานา 3% | Mana 2 |
| Healing | 5 | พลังฮีล +5% | — |
| ความชำนาญแต่ละธาตุ | ธาตุละ 5 | ดาเมจธาตุนั้น +4% | Power 2 |

เก็บแต้มเพิ่มได้จากบอสและกล่องโครงสร้าง รีเซ็ตด้วย `/skilltree reset confirm`
ใช้ Amethyst Shard ธรรมดา 16 ชิ้นและเลเวล Minecraft 5 ระดับ คืนแต้มทั้งหมด
โดยไม่ลบ Arcane XP, มานาถาวรจากยา หรือเวทในคทา

## มานา ดาเมจ และการป้องกัน

พื้นฐาน Max Mana 100, Spell Power 12 HP, Healing Power 6 HP
ดาเมจดิบ = SP × ตัวคูณเวท × (1 + โบนัสธาตุ) ก่อนการป้องกันเวทและ damage event ของ Paper
ใช้ `DamageType.MAGIC` ตาม pipeline เดิมของปลั๊กอิน จึงข้ามค่าป้องกันจากเกราะดิบตาม
vanilla damage tags แต่ Protection, Resistance, โล่เวท และสกิลลดดาเมจยังมีผล
ฮีลใช้ Healing Power แยกจาก SP ผ่าน heal event และเพดานต่อการร่าย
ไม่เพิ่มระบบคริติคอลเวทใหม่ ค่า damage event ของปลั๊กอินอื่นยังปรับหรือยกเลิกได้

ฟื้นมานานอกต่อสู้ 7/วินาที ขณะต่อสู้ 3/วินาที หยุดฟื้น 2 วินาทีหลังร่าย
ความเสียหายต่อเนื่องไม่เริ่ม regen delay ใหม่ การต่อสู้คงสถานะ 10 วินาทีจากการโจมตีล่าสุด
GCD 0.65 วินาที, Ultimate GCD 1.2 วินาที, Ultimate category ร่วม 15 วินาที
คูลดาวน์แยกตามผู้เล่นและ Spell ID บันทึกข้ามการออกเกมและสลับคทา
ลดค่ามานาสูงสุด 30% โบนัส regen สูงสุด 100% มานารวมสูงสุด 300
โบนัส Max Mana จาก tree เป็นชั่วคราว แยกจากค่าสะสมถาวรของยา

พันธมิตรใช้ main-scoreboard team ของ Minecraft รวมตัวผู้ร่ายและสัตว์เลี้ยงตามระบบเดิม
ถ้าใช้ปลั๊กอินปาร์ตี้ภายนอก ต้องให้ปลั๊กอินนั้นเชื่อมสมาชิกกับทีม หรือเชื่อมการยกเลิก event

เวทหลายระลอกแบ่งตัวคูณรวมตามจำนวนระลอก ไม่ให้แต่ละระลอกคูณเต็มใหม่
จำกัดเป้าหมาย 12 ตัวต่อการร่าย ดาเมจต่อบอสจากการร่ายเดียวรวม DoT/Reaction
มีเพดาน 5% Max HP ของบอส ค่าเหล่านี้ปรับได้ที่ `elemental-combat`
บอสที่มีแท็ก `control_resistant`, `world_boss_entity`, `sanctuary_boss` และชนิด native
จะไม่โดน hard CC แต่เปลี่ยนเป็น Stagger/Frost Vulnerability

Reaction แต่ละชนิดมี cooldown ต่อเป้าหมาย 2 วินาที และ damage จาก reaction
ไม่สร้าง reaction ใหม่ มี Wildfire, Electro-Charged, Flash Freeze, Molten Core,
Eclipse, Steam, Thunderstorm และ Permafrost

เวทร่ายหน่วงยกเลิกเมื่อเคลื่อนเกินสองบล็อก ถูกแช่แข็ง ตาย ออกเกม หรือเปลี่ยนโลก
เวทที่เริ่มทำงานแล้วไม่คืนมานาหรือ cooldown เมื่อถูกขัดจังหวะ/ยิงพลาด
Phoenix Rebirth ทำงานอัตโนมัติเมื่อเวทอยู่ในคทามือหลัก ต้องมีมานาและ cooldown พร้อม
ไม่ป้องกัน Void หรือคำสั่ง kill ของแอดมิน

## ส่วนที่ปรับจากไอเดียและข้อจำกัด

- Stone Wall ไม่เปลี่ยนบล็อกโลก ใช้ display + ระบบตรวจผู้เล่น/กระสุนแยก
  Bedrock มี particle boundary สำรอง เนื่องจาก Geyser ไม่แสดง Block Display แบบ Java
  ไม่รับประกันการกัน pathfinding มอนสเตอร์หรือกระสุนเฉพาะปลั๊กอินอื่นทุกชนิด
- ดาเมจและการชนของเวทคำนวณบนเซิร์ฟเวอร์ ภาพมังกรน้ำ แขนยักษ์และเสาเวทใช้
  geometry/particles ที่มีอยู่ ไม่ใช่โมเดล custom ใหม่ตามชื่อในแนวคิด
- Shadow Doppelganger เป็นร่างตัวแทนแบบ armor stand เลียนแบบการโจมตีที่อนุญาต
  ภายในงบดาเมจรวม ไม่คัดลอก Ultimate, Heal หรือ Summon
- ไม่มีการเพิ่มหน้าต่าง UI แบบ client mod ใช้ inventory GUI และคำสั่งร่วมกันทั้งสอง Edition
- ระยะผลักใช้ native velocity และเพดาน compatibility ระยะจริงขึ้นกับพื้น กำแพง และ
  anticheat จึงไม่รับประกันว่าทุกเป้าหมายจะเคลื่อนเท่าตัวเลขระยะในแนวคิด
- ยังต้องตรวจภาพจริง FOV, ความหนาแน่น particles, input มือถือ และ anticheat
  บน Java/Bedrock ของเซิร์ฟเวอร์ที่ใช้งาน การทดสอบ server ไม่ยืนยันภาพ client
- ไม่ปรับ HP/เฟสของบอสเดิมเพื่อให้ตรงเวลา 3–10 นาทีโดยไม่มี playtest จริง

ข้อมูลพื้นฐานของข้อจำกัด: [Paper display entities](https://docs.papermc.io/paper/dev/display-entities/)
และ [Geyser current limitations](https://geysermc.org/wiki/geyser/current-limitations/)

## แอดมินและส่วนเชื่อม

```text
/spell give staff fire mythic <player>
/spell give scroll fireball <player> 1
/spell adminupgrade mythic <player>
/magic mana
/magic pack
```

คำสั่งแจกและปรับ tier ต้องมี `advance-magic.admin` ผู้เล่นทั่วไปใช้เมนูได้ตามปกติ
ปลั๊กอินภายนอกเชื่อมสถิติผ่าน Bukkit ServicesManager ด้วย `ElementalStats.Provider`
โดย Snapshot จำกัดค่าที่รับเข้ามา และยกเลิกการร่ายด้วย `ElementalCastEvent`
ผลต่อเป้าหมายส่ง `MagicAffectEvent` โดย `getSpell()` เป็น null สำหรับเวทระบบใหม่
การทำ damage ใช้ pipeline เดียวกับระบบเวทเดิม

## ทดสอบซ้ำ

ใช้เซิร์ฟเวอร์ Paper ที่ดาวน์โหลด dependencies ไว้แล้วเป็น source template:

```powershell
python advance-magic/tests/run_elemental_system_checks.py --source-server <cached-paper-server> --server <new-test-directory>
python evergarden/tests/run_elemental_treasure_checks.py --source-server <cached-paper-server> --server <new-treasure-test-directory> --magic-jar <tested-advance-magic.jar> --compat-server <cached-geyser-template>
```

runner สร้างเซิร์ฟเวอร์ localhost แยกและ JAR จาก source ไม่คัดลอกเข้าเซิร์ฟเวอร์ผู้เล่น
ผลการตรวจสุดท้ายและ SHA-256 ของไฟล์ release อยู่ใน manifest ที่ส่งมอบ

ไฟล์รุ่นที่ส่งมอบผ่าน **1,207 assertions** บน Paper 26.3 ที่แคชไว้จริง
(โค้ดกำหนด API 26.2) และ **280 assertions** เมื่อเปิดร่วมกับ Evergarden,
Geyser 2.12 และ Floodgate 2.2.5 รวม **1,487 ข้อ** ไม่มี spell/event runtime exception
ใน log การทดสอบ ตรวจครบ 57 เวทแบบกดร่ายและ Phoenix แบบอัตโนมัติ รวมกรณี
กดยืนยันซ้ำ การใช้ snapshot เก่า ช่องเต็ม คูลดาวน์ข้ามคทา โบนัสมานาหลัง reload,
รีเซ็ต tree, discovery XP ที่ไม่ซ้ำ, ปฏิกิริยาธาตุ, เพดานบอส และกำแพงกับผู้เล่น/ลูกธนู
การตรวจนี้ไม่ใช่การทดสอบภาพผ่าน client หรือ benchmark ผู้เล่นจำนวนมาก

## ????????? 58 ????

????????????????????????????????? ??????????????????????

| Spell ID | ???? | MP | CD (s) | ????????? | Rarity | ????? |
| --- | --- | ---: | ---: | ---: | --- | --- |
| `fireball` | Fire | 12 | 4 | 1.2x | BASIC | ระเบิดรัศมี 2.5 บล็อก และสะสม Burn |
| `flame_burst` | Fire | 18 | 8 | 1x | BASIC | ระเบิดรอบตัวและผลักศัตรู |
| `burning_ground` | Fire | 25 | 14 | 2x | RARE | พื้นที่ไฟ 5 วินาที |
| `flame_dash` | Fire | 18 | 12 | .8x | RARE | พุ่ง 6 บล็อกและทิ้งทางไฟ |
| `inferno_spear` | Fire | 30 | 10 | 2.4x | RARE | หอกไฟทะลุสูงสุด 4 เป้าหมาย |
| `magma_eruption` | Fire | 35 | 18 | 3x | EPIC | ลาวาปะทุหลังเตือน 0.8 วินาที |
| `hellfire_chain` | Fire | 28 | 13 | 2.2x | RARE | ไฟชิ่งสูงสุด 5 เป้าหมาย |
| `scorching_vortex` | Fire | 42 | 22 | 3.6x | EPIC | พายุไฟดูดศัตรู 5 วินาที |
| `meteor_rain` | Fire | 65 | 45 | 6x | LEGENDARY | ร่าย 2.5 วินาที แล้วฝนอุกกาบาต 6 ลูก |
| `phoenix_rebirth` | Fire | 80 | 120 | 2.5x | LEGENDARY | ป้องกันความตายอัตโนมัติเมื่อถือคทาที่ประทับไว้ |
| `aqua_bullet` | Water | 10 | 3 | .9x | BASIC | กระสุนน้ำและ Wet 5 วินาที |
| `water_blade` | Water | 16 | 6 | 1.4x | BASIC | คมมีดน้ำโจมตีแนวตรง |
| `healing_spring` | Water | 25 | 16 | 1.2x | RARE | บ่อฮีลพันธมิตร 5 ครั้งใน 5 วินาที |
| `bubble_prison` | Water | 30 | 20 | .5x | RARE | กักศัตรูทั่วไป 2 วินาที |
| `tidal_push` | Water | 24 | 10 | 1.2x | RARE | คลื่นน้ำผลักศัตรู |
| `aqua_shield` | Water | 28 | 18 | 1.8x | RARE | โล่ป้องกัน 6 วินาที |
| `rain_blessing` | Water | 42 | 30 | 2x | EPIC | ฝนฮีลรอบตัว 8 วินาที |
| `whirlpool` | Water | 35 | 19 | 2.2x | EPIC | วังน้ำวนดูดศัตรู 4 วินาที |
| `tsunami` | Water | 58 | 40 | 4.5x | LEGENDARY | คลื่นใหญ่ผลักและทำให้ Wet |
| `leviathans_wrath` | Water | 75 | 65 | 7x | LEGENDARY | มังกรน้ำโจมตีเป็นระลอก |
| `frost_bolt` | Ice | 12 | 4 | 1x | BASIC | Slow 20% นาน 3 วินาที |
| `ice_spike` | Ice | 22 | 8 | 1.8x | RARE | หนามน้ำแข็งจากพื้น |
| `frozen_armor` | Ice | 26 | 20 | 0x | RARE | ลดความเสียหาย 20% นาน 7 วินาที |
| `glacial_path` | Ice | 20 | 12 | .6x | RARE | ทางน้ำแข็งยาว 8 บล็อก |
| `crystal_prison` | Ice | 38 | 26 | 1x | EPIC | กักศัตรูทั่วไป 2.5 วินาที |
| `blizzard` | Ice | 46 | 26 | 3.5x | EPIC | พายุหิมะรัศมี 6 บล็อก 6 วินาที |
| `absolute_zero` | Ice | 80 | 75 | 5x | LEGENDARY | ระเบิดเยือกแข็งและใช้ Frost Stack |
| `wind_cutter` | Wind | 10 | 3 | .9x | BASIC | ใบมีดลมระยะ 18 บล็อก |
| `gale_step` | Wind | 14 | 8 | 0x | BASIC | พุ่ง 7 บล็อก |
| `updraft` | Wind | 22 | 14 | .8x | RARE | ยกศัตรูทั่วไปขึ้น |
| `wind_barrier` | Wind | 25 | 18 | 0x | RARE | ลดความเสียหายกระสุน 35% นาน 5 วินาที |
| `cyclone_trap` | Wind | 35 | 20 | 2x | EPIC | ดูดศัตรูเข้าศูนย์กลาง 4 วินาที |
| `thousand_wind_blades` | Wind | 42 | 24 | 3.6x | EPIC | ใบมีดลม 12 ใบ แบ่งความเสียหายรวม |
| `tempest_dominion` | Wind | 75 | 65 | 6x | LEGENDARY | มหาพายุโจมตีหลายระลอก |
| `stone_bullet` | Earth | 12 | 4 | 1.2x | BASIC | กระสุนหินผลักเป้าหมาย |
| `earth_spike` | Earth | 22 | 9 | 1.9x | RARE | หนามหินจากพื้น |
| `stone_wall` | Earth | 26 | 22 | 0x | RARE | กำแพงจำลองกั้นกระสุน 6 วินาที |
| `iron_skin` | Earth | 28 | 24 | 0x | RARE | ลดความเสียหาย 25% นาน 7 วินาที |
| `quicksand` | Earth | 26 | 16 | .8x | RARE | Slow 35% นาน 5 วินาที |
| `seismic_slam` | Earth | 36 | 18 | 2.8x | EPIC | กระแทกพื้นและสะสม Stagger |
| `golem_guardian` | Earth | 50 | 45 | 3x | EPIC | ผู้พิทักษ์โจมตี 15 วินาที |
| `titans_judgment` | Earth | 80 | 70 | 6.5x | LEGENDARY | หินยักษ์ทุบพื้นหลังร่าย 2 วินาที |
| `radiant_bolt` | Light | 12 | 4 | 1.1x | BASIC | กระสุนแสงและ Radiance Mark |
| `holy_sanctuary` | Light | 40 | 28 | 2x | EPIC | เขตฮีลและลดความเสียหาย 10% นาน 6 วินาที |
| `purification` | Light | 25 | 22 | 0x | RARE | ล้างสถานะเสียที่อนุญาตสูงสุด 2 ชนิด |
| `divine_judgment` | Light | 46 | 25 | 3.8x | EPIC | เสาแสงโจมตีและระเบิด Radiance Mark |
| `heavens_descent` | Light | 85 | 90 | 4x | LEGENDARY | โจมตีด้วยแสงและฮีลพันธมิตร |
| `shadow_bolt` | Dark | 12 | 4 | 1x | BASIC | กระสุนเงาและ Curse Stack |
| `life_drain` | Dark | 28 | 12 | 2x | RARE | ฟื้นเลือดจากความเสียหายจริง 20% ภายใต้เพดาน |
| `cursed_mark` | Dark | 24 | 18 | 0x | RARE | เพิ่มความเสียหายที่เป้าหมายรับ 8% นาน 6 วินาที |
| `shadow_doppelganger` | Dark | 45 | 35 | 3x | EPIC | ร่างเงาโจมตี 8 วินาที ไม่คัดลอก Ultimate |
| `abyssal_collapse` | Dark | 80 | 75 | 6.5x | LEGENDARY | ระเบิดความมืดและใช้ Curse Stack |
| `spark_shot` | Lightning | 11 | 3 | 1x | BASIC | กระสุนไฟฟ้าและ Shock Stack |
| `lightning_chain` | Lightning | 25 | 9 | 2x | RARE | สายฟ้าชิ่งสูงสุด 4 เป้าหมาย |
| `thunder_dash` | Lightning | 20 | 11 | 1.2x | RARE | พุ่ง 7 บล็อกและโจมตีระหว่างทาง |
| `static_field` | Lightning | 32 | 17 | 2.5x | EPIC | สนามไฟฟ้า 5 วินาที |
| `thunder_spear` | Lightning | 40 | 18 | 3x | EPIC | หอกสายฟ้าทะลวงเป้าหมาย |
| `wrath_of_raijin` | Lightning | 80 | 70 | 7x | LEGENDARY | พายุสายฟ้าและฟ้าผ่าปิดท้าย |
