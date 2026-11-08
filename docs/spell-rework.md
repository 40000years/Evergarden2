# Advance Magic 1.36.0 — ปรับเวทธาตุ + เอฟเฟคเหมือนกันทั้ง Java และ Bedrock

ไฟล์ปลั๊กอิน: `dist/advance-magic-1.36.0-spell-rework.jar` · Bedrock pack 2.37.0 · Java pack ไม่เปลี่ยน

## เอฟเฟคเหมือนกันทั้งสองแพลตฟอร์ม

- **บล็อก 3 มิติ:** Geyser ไม่แสดง BlockDisplay ของ Java เวทที่มีหนาม กำแพง เกราะไหล่ กรง ทางน้ำแข็ง หรือคลื่น จึงไม่มีรูปทรงบน Bedrock มาก่อน (Stone Wall มองไม่เห็นเลยแต่ยังกั้นทาง) ตอนนี้ทุกชิ้นส่งไปให้ Bedrock ด้วย ผ่านโมเดลลูกบาศก์ตัวเดียวกับที่ฉาก Legendary ใช้ โมเดลนี้รองรับกว้าง/สูง/ลึกแยกกันแล้ว และได้วัสดุใหม่ 11 แบบ วัสดุ ตำแหน่ง การหมุน และขนาดตรงกับ Java ทุก tick
- **แสงเรือง:** ใช้ dust บน Java และอนุภาค `mythic_<สี>` ที่ปรับขนาดได้บน Bedrock สีจึงตรงกัน
- **อนุภาคที่ Bedrock แสดงต่างไป:**
  - CLOUD, GUST และ LARGE_SMOKE เป็นกลุ่มควันใหญ่บน Bedrock จึงแสดงเป็น dust สีแทน
  - FLASH, SWEEP_ATTACK และ FIREWORK ต้องใช้แพ็กเสริมของ Geyser จึงแสดงเป็นประกาย end rod แทน
  - เวทลม (เดิมใช้ CLOUD ทั้งชุด) จึงใช้เส้น dust ขาว/เขียวมรกต
- **กรงแช่แข็งตามธาตุ:** น้ำเป็นฟอง น้ำแข็งเป็น blue ice มืดเป็น crying obsidian สายฟ้าเป็นกระจกเหลือง แสงเป็นทอง

## การร่าย

- **ถูกขัดจังหวะ:** ถ้าเดินเกิน 2 บล็อกหรือถูกแช่แข็งระหว่างร่ายเวทที่ต้องชาร์จ จะได้มานาคืนครบ และคูลดาวน์ของเวทนั้น GCD และล็อก Ultimate ล้างทันที กดร่ายใหม่ได้เลย
- **ช่วยเล็ง:** เวทเป้าเดี่ยว (Prison, Life Drain, Cursed Mark, Chain) ถ้าเล็งพลาดจะเลือกศัตรูที่อยู่ใกล้เป้าเล็งที่สุดภายใน 8°
- **แจ้งเหตุที่ร่ายไม่ได้:** ไม่มีที่ว่างให้พุ่ง วางกำแพงไม่ได้ ไม่มีที่ให้เรียกโกเลมหรือร่างเงา

## สายบัฟ/ฮีลทีม

| เวท | ใหม่ |
| --- | --- |
| Aqua Shield | โล่น้ำให้ทั้งทีมรัศมี 6 นาน 8 วิ (32 MP) |
| Frozen Armor | ทีมรัศมี 6 ลดดาเมจ 20% 8 วิ ผู้ที่ตีเข้ามาติด Frost |
| Iron Skin | ทีมรัศมี 6 ลดดาเมจ 25% และกันแรงผลัก 8 วิ |
| Wind Barrier | ทีมรัศมี 8 กันกระสุน 50% และ Speed 8 วิ พร้อมผลักศัตรูใกล้ตัว |
| Purification | ทีมรัศมี 8 ล้างสถานะเสียทั้งหมด ฮีล ×1.5 และลดดาเมจ 20% 3 วิ (คูลดาวน์ 18 วิ) |
| Healing Spring | รัศมี 3.5→5 ฮีล ×1.2→×2.5 ล้าง Burn/Slow ทุกจังหวะ |
| Rain Blessing | รัศมี 5→7 ฮีล ×2→×3.5 |
| Holy Sanctuary | รัศมี 5→6 ฮีล ×2→×2.5 ลดดาเมจ 10%→15% |
| Static Field | เพื่อนในสนามได้ Speed และพลังโจมตี +10% |
| Flame Dash | ทิ้งทางเพลิง 3 วิ เพื่อนที่เหยียบได้พลังโจมตี +15% และ Speed 6 วิ |
| Life Drain | ดูดเลือด 20%→40% แบ่งครึ่งฮีลเพื่อนรัศมี 6 |

ฮีลต่อการร่ายหนึ่งครั้งเพิ่มเพดานจาก 35% เป็น 60% ของเลือดสูงสุด เดิมผู้เล่น 20 HP ได้ไม่เกิน 7 HP ฮีลทีมจึงแทบไม่มีผล เซิร์ฟเวอร์ที่ยังใช้ค่าเดิม (35) จะถูกปรับให้อัตโนมัติ ส่วนค่าที่ตั้งเองไว้จะไม่ถูกแตะ

## ปรับกลไกเวทที่ใช้งานจริงไม่ได้

| เวท | ใหม่ |
| --- | --- |
| Glacial Path | ทางน้ำแข็งจริงยาวสูงสุด 10 บล็อก นาน 5 วิ ศัตรูบนทาง Slow 35% และติด Frost ทุกวินาที เพื่อนบนทางได้ Speed II ดาเมจรวม ×0.6→×1.0 |
| Golem Guardian | เดินด้วย pathfinder จริงแทนการเทเลพอร์ตทีละ 0.65 บล็อก ตีทุกวินาทีเมื่อศัตรูอยู่ในระยะ และดึงมอนสเตอร์ที่ไล่ตีทีมมาหาตัวเอง (โกเลมเป็นอมตะ) |
| Bubble / Crystal Prison | ขัง 3 วิ (ผู้เล่น 1.5 วิ) ดาเมจครึ่งหนึ่งตอนขัง อีกครึ่งตอนแตก (Bubble ×0.5→×1.0, Crystal ×1→×1.5) Crystal แตกเป็นสะเก็ดใส่ศัตรูรัศมี 3 |
| Tidal Push | ×1.2→×1.5 ระยะ 7→8 ผลักแรงขึ้น ดับไฟและเร่งเพื่อนในแนวคลื่น |
| Gale Step | ลมผลักศัตรูรอบจุดเดิม (×0.4) ติด Air 2 ชั้น และได้ Speed II 3 วิ |
| Updraft | ×0.8→×1.2 ติด Air 2 ชั้น |
| Quicksand | ×0.8→×1.2 Slow 35%→50% ดึงศัตรูลงพื้น |
| Flame Burst | ×1→×1.3 ติด Burn 2 ชั้น |
| Cursed Mark | สาปศัตรูรัศมี 3 รอบเป้า ศัตรูรับดาเมจเพิ่ม 8%→15% นาน 8 วิ และติด Curse 3 ชั้น |
| Shadow Doppelganger | คัดลอกเวท 35%→50% |
| Tsunami (U) | ×4.5→×9 (Ultimate อื่นอยู่ที่ ×16 พร้อมช่วงชาร์จ) |

คำอธิบายเวท Ultimate 7 ตัวที่เคยเป็น `????` ซ่อมแล้ว

## แก้บั๊กจาก 1.35.0

โกเลมของตัวเองถูกนับเป็นศัตรู เวทเป้าเดี่ยวจึงล็อกโกเลมตัวเองได้ ตอนนี้กลับมาเป็นพันธมิตรของเจ้าของและทีมแล้ว

## ติดตั้ง

ปิดเซิร์ฟเวอร์ แทน `plugins/advance-magic.jar` แล้วเปิดใหม่ ให้ Geyser รีสตาร์ตพร้อมเซิร์ฟเวอร์ เพราะคุณสมบัติใหม่ของโมเดลลูกบาศก์ลงทะเบียนตอน Geyser เริ่มทำงาน จากนั้นให้ผู้เล่น Bedrock เชื่อมต่อใหม่เพื่อรับแพ็ก 2.37.0

---

# Advance Magic 1.37.0: เอฟเฟคชุดใหม่และแก้ Bedrock ค้าง

ไฟล์ปลั๊กอิน: `dist/advance-magic-1.37.0-spell-vfx.jar` · Bedrock pack 2.38.0

## ทำไม Bedrock ถึงค้างตอนใช้เวทดิน

1.36 ใช้อนุภาค `DUST_PILLAR` (ฝุ่นของ mace) เป็นเศษหินตอนกระแทก บน Java มันเป็นอนุภาคเดียว แต่ Geyser แปลงเป็น `smash_ground_particle` ของ Bedrock ซึ่งพ่นเศษบล็อกที่มีการชนออกมาเป็นสิบๆ ชิ้นต่อครั้ง เวทที่ใช้มันได้แก่ Stone Wall (ยิงพร้อมกัน 90 ครั้ง), Seismic Slam, Earth Spike, Golem (ทุกวินาที) และ Iron Skin นอกจากนี้ CRIT, POOF, FALLING_WATER, EXPLOSION และ SMOKE ก็กลายเป็นตัวพ่นอนุภาคบน Bedrock เหมือนกัน

1.37 แก้ดังนี้:
- ใช้เฉพาะอนุภาคที่ Geyser แปลงเป็นอนุภาคเดี่ยว ชนิดอื่นจะถูกวาดเป็น dust หรือประกายแทนอัตโนมัติ
- ผู้เล่น Bedrock มีงบอนุภาคแยก คือ 140 ต่อ tick (ตั้งได้ที่ `max-native-particles-per-bedrock-viewer-tick`) เพราะทุกอนุภาคเป็น quad ที่มี texture ของตัวเอง
- บล็อก 3 มิติที่อยู่นิ่งไม่ส่งข้อมูลซ้ำ และจำกัดไว้ไม่เกิน 12 ชิ้นต่อเวท (Ultimate 18)
- ชั้น dust ที่ค้างนานวาดทุก 4 tick แทน 2

## โมเดลหายบน Bedrock

- **ตัวเลือกวัสดุ:** render controller ของโมเดลลูกบาศก์ Bedrock เลือกวัสดุด้วย Molang ternary ซ้อน 47 ชั้น (1.36 เพิ่มจาก 36) เปลี่ยนเป็น OR สั้นๆ ชั้นเดียว และให้เฉพาะ texture ที่เคลื่อนไหว 3 แบบเท่านั้นที่มี expression
- **ขนาดสำรอง:** ถ้าแกนขนาดใหม่ยังไม่ถูกลงทะเบียน (Geyser ไม่ได้รีสตาร์ต) ลูกบาศก์จะแสดงเป็นขนาดเท่ากันทุกด้าน แทนที่จะหายไป

## เอฟเฟคชุดใหม่

เพิ่มภาพ VFX ที่ย้อมสีได้ (`tools/vfx_sprite_assets.py`) แต่ละภาพมี 9 สี (8 ธาตุ + ฮีล):
- วงเวทลายวาด 2 แบบ ทำจาก sigil ของ Legendary โดยแปลงเป็นภาพขาวดำแล้วย้อมสี
- วงคลื่นกระแทก
- แสงแฟลร์
- รอยฟันโค้ง
- ประกายดาว

Java วาดด้วย item display ส่วน Bedrock ใช้อนุภาคเดียวต่อภาพ ภาพที่อยู่นิ่งส่งซ้ำทุก 4 tick และมีอายุ 0.26 วินาที ที่ 20 TPS จึงมีช่วงซ้อนสั้นๆ ไม่เกิน 2 ภาพเพื่อให้แสดงต่อเนื่อง ภาพที่เคลื่อนที่ส่งถี่กว่าและนับรวมในงบอนุภาคกับแพ็กเก็ตของผู้เล่น

เวทที่ได้ภาพใหม่:
- **เวทพื้นที่ทุกตัว:** มีวงเวทหมุนอยู่ใต้ ขนาดเท่ารัศมีจริง
- **ระเบิดทุกครั้ง:** มีวงคลื่นแผ่ออกถึงรัศมีจริง และแสงบานกลางวง
- **กระสุน:** มีแฟลร์ที่หัว
- **ใบมีดน้ำและลม:** เป็นรอยฟันที่พุ่งตามแนว
- **โล่น้ำและม่านลม:** เป็นวงแหวนหันหาผู้มองเสมอ จึงดูเป็นฟองจากทุกด้าน
- **บัฟทีม:** วงเวทของธาตุกางออกถึงระยะของบัฟ
- **Telegraph:** วงเวทแสดงรัศมีจริงตั้งแต่ tick แรก และมีวงหดเข้าบอกจังหวะที่จะโดน

แพ็กเพิ่มขึ้น Java +359 KB และ Bedrock +441 KB เพราะภาพใหญ่ถูกบีบอัดแบบ palette แล้ว แต่ยังเล็กกว่าก่อนการ cleanup ใน 1.35

## เทสต์ใหม่

Probe Bedrock (`--probe blocks-bedrock`) ตรวจระหว่างร่ายเวทธรรมดาทั้ง 21 ตัวว่า:
- ทุกอนุภาคที่ผู้เล่น Bedrock ได้รับเป็นชนิดที่ปลอดภัย
- จำนวนอนุภาคต่อ tick อยู่ในงบ
- ทุก sprite ที่ส่งไป Bedrock มีอยู่จริงในแพ็ก

## ผลตรวจรุ่นสุดท้ายและสถานะ Mirror

JAR ที่ผ่านการตรวจและคัดลอกลง `dist/advance-magic.jar` กับ `dist/advance-magic-1.37.0-spell-vfx.jar` มี SHA-256 `5e55ed1efcf55caaf01b23f0489a50ddd245371d0477186c78810e5fd38fcc0b`

- ระบบเวทและหน้าจัดการคทา: **PASS 2389**
- ระดับเวทและคูลดาวน์: **PASS 3150** (วัดจากเวลาจองคูลดาวน์จริง ไม่หักเวลาสร้างฉาก)
- Legendary spectacle: **PASS 175**
- Bedrock: **PASS 310** รวม 9 ข้อใหม่ที่ร่าย 24 ฉากพร้อมกันและรวมทั้งอนุภาคธรรมดากับภาพ sprite ในโควตาเดียวกัน การ redraw ซ้ำใน Bukkit tick เดียวกันไม่เพิ่มทราฟฟิกเกินงบ
- ความสมบูรณ์ของแพ็กและภาพร่วม Java/Bedrock: **PASS 607** และ direction schema ของ Bedrock **198** รายการ
- ระบบส่งแพ็ก: **PASS 70**; สร้าง Judge และดาวน์โหลดแพ็กผ่านเซิร์ฟ Paper ในเครื่อง: **PASS packs**

หลักฐานอยู่ที่ `output/spell-rework2-20261027/gates.json` และ log `final-*.log` โมเดลและประสิทธิภาพบน client Minecraft จริงยังต้องตรวจในเกม

Mirror ของแพ็ก Java ใหม่เตรียมไว้แล้ว แต่ **ยังไม่ได้เผยแพร่หรือฝังรายการใหม่ลง JAR** เพราะระบบตรวจอนุมัติอัตโนมัติต้องการอนุมัติการเผยแพร่สาธารณะ ดู [สถานะ Mirror](pack-mirror.md) ก่อนนำไปใช้กับโฮสต์ที่โหลดแพ็กผ่านพอร์ตเซิร์ฟไม่ได้

เมื่อติดตั้งให้ปิดเซิร์ฟ แทน JAR แล้วเปิดใหม่พร้อม Geyser และให้ผู้เล่น Bedrock เชื่อมต่อใหม่เพื่อรับแพ็ก **2.38.0**

## 1.37.1: no block-break particles on Bedrock

Bedrock players still saw a dirt flood and froze on earth spells in 1.37.0. Geyser plays every
`BLOCK` particle (and `BLOCK_CRUMBLE`, `DUST_PILLAR`, `DUST_PLUME`) as a whole block-break burst
(`LevelEvent.PARTICLE_CRACK_BLOCK`), so a single 90-count burst in Titan's Judgment became 90 break effects.

* `NativeElementalVfx`, `ElementalVfx.Frame.fx` and the earth projectile accent never send BLOCK-family
  particles: both clients draw a few `FALLING_DUST` chips of the same block, plus dust in the block's map
  colour for the burst's body.
* Legendary scenes send emitter-type particles (cloud, explosion, gust, sweep, flash...) to Bedrock viewers
  at most two per call.
* Gate: `LegendaryBlockBedrockChecks` asserts the Bedrock observer receives zero block-break particles across
  all eight Legendary spectacles (0 of 45 955) and no longer whitelists `block` for native scenes.

Build: `python tools/build_spell_vfx_fix_release.py [--promote]` (classes only; packs byte-identical).
Gates: elemental 2389, levels 3150, spectacle 175, blocks-bedrock 311.

## 1.37.2: Stone Wall redesign and resting-prop keepalive

* Stone Wall: a crenellated rampart (stone brick / cobblestone columns over a deep-slate plinth) heaves out of
  the ground from the middle outwards, with a ground sigil, a shockwave ring and stone dust as it breaks
  through, and a slow-turning earth rune on both faces while it stands.
* A resting Bedrock cube prop re-sends its pose about once a second (1.37.0 sent it once): a client that
  missed the spawn's pose no longer keeps a static wall at size 0.

Build: `python tools/build_stone_wall_release.py [--promote]`. Gates: elemental 2389, levels 3150,
spectacle 175, blocks-bedrock 311.

## 1.38.0: the spell VFX kit

Players still found nearly every cast flat on both clients, several spells looked alike (the same two sigils and
flare in every tint), and Bedrock still lagged. 1.38.0 replaces the drawing layer with a kit that is grander on
both clients and far lighter on Bedrock.

**Art** (`tools/vfx_kit_textures.py`, full colour, procedurally drawn):
- a magic circle for each tint, with geometry of its own: flame petals, breaking wave crests, a hexagonal
  snowflake, a triskelion spiral, a tectonic octagram, a sunburst star, a pentagram with thorns, a zigzag
  hexagram, and a vine around a flower of life;
- a glow orb and an impact burst per tint: flame petals, splash crown, shard star, swirl, ground cracks,
  ray star, void, forked arcs, bloom;
- three lightning bolts and a tintable beam;
- a 16 px particle atlas: flame and smoke flipbooks, sparks, droplets, shards, snow, leaves, rocks, wisps, arcs.

**Drawing** (`SpellFx`):
- **Shots** are one-shot animated sprites. Bedrock gets a single `fxs_*` particle that eases size, spin and
  fade itself. Java gets one display that interpolates through a few keyframes and removes itself.
- **Kits** are element emitters (`kit_<tint>_<kind>`): burst, ground ring, rising column, funnel or drain
  swirl, projectile trail, rain, and a line along a path. One Bedrock packet spawns the whole effect on the
  client; Java viewers get matching vanilla particles.

**Choreography** (`NativeElementalVfx`):
- **Every cast:** an element circle opens under the caster and a second circle in front of the staff, the
  release flares through it, and a shockwave rolls out. Rare spells add a ground ring. Epic spells add a
  counter-rotating sigil and a rising column. Legendaries add a circle in the sky and a pillar of light.
- **Hits and blasts:** a burst sprite, a flash, a ground decal (scorch, cracks, frost), a shockwave and the
  element burst.
- **Fields, telegraphs, buffs, heals and summons:** one long-lived circle per cast plus continuous kits (fire
  tornado, whirlpool drain, blizzard, holy rain, quicksand drain, static strikes from the sky).
- **Lightning:** bolts strike from above.

**Lag** (`--probe lag`, `SpellLagProbe`, every castable spell with a Java watcher and a Geyser observer):
- Bedrock never receives translated vanilla detail from native scenes; it gets kits and shots instead.
- The authored Legendaries cap Bedrock at 60 translated particles per tick (450 before). They add a
  Bedrock-only kit overlay for the spell's whole duration.
- Legendary Bedrock peaks fell from 152–423 to 64–87 packets per tick, and their totals fell 10–55%.
  Ordinary spells peak at 16–55 Bedrock packets per tick, and each packet can now be a whole emitter.
- Average server cost stays under 0.7 ms per tick for every spell.

Reports: `output/vfx-overhaul-20261029/{baseline,after}-lag/spell-lag-report.json`.

Build: `python tools/build_spell_fx_release.py [--promote]` (Bedrock pack 2.39.0).

Gates: elemental 2439, levels 3150, spectacle 175, blocks-bedrock 311, asset validation 6,756.
