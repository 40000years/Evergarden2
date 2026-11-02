# บอสประจำมิติ · Realm Bosses (โมเดล + ตัวโชว์)

โมเดลบอสใหม่สี่ตัวสำหรับ Sanctuary ใน Nether และ The End ([dimensional-sanctuaries.md](dimensional-sanctuaries.md)) ไฟต์จริงอยู่ใน `boss/RealmBossController` (ผูกกับ structure โดย `RealmSanctuaryEncounters`) ดูหัวข้อ [การต่อสู้](#การต่อสู้--e2165-realm-rework) ด้านล่าง

![lineup](../previews/realm-bosses/lineup.png)

| บอส | Sanctuary | จุดเด่น | ท่าไม้ตาย |
|---|---|---|---|
| **Cinder Colossus** (`cinder_colossus`) | Nether · ป้อมมงกุฎลาวา | ป้อมปราการมีชีวิต ก้อนหินดำแยกกันมีลาวาเรืองในรอยต่อ มงกุฎ 12 แฉก (ตามยอดมงกุฎ 12 แฉกของป้อม) หน้ากากกากบาทไฟ ไหล่เป็นปราการมีเชิงเทิน กุญแจมือโซ่ขาด ธงรบไหม้ | คุกเข่า แผ่นหลังเปิดเผยผลึกหัวใจสีกุหลาบ (จุดอ่อน) |
| **Soul-Eater Hound** (`soul_hound`) | Nether · วิหารกระดูกวิญญาณ | หมาป่ากระดูกยักษ์ กะโหลกยาวเขี้ยวคู่ ตาและคอไฟวิญญาณ ซี่โครงลุกเป็นไฟ ครีบกระดูกตามสันหลัง ดินวิญญาณติดตัว ปลอกคอโซ่ขาด อุ้งเท้าลุกไฟ | นั่งเห่าหอน ซี่โครงกางออก |
| **Crystal Moth Queen** (`crystal_moth`) | The End · นครคริสตัลร้าง | ปีกกระจกผลึกสี่ปีกลายตะกั่วทอง ตาลายเพชร ขอบปีกเป็นผลึกจริง ผลึกหัวใจชมพู ตาประกอบม่วง หนวดขนนก ขาตั๊กแตนตำข้าว เหล็กไนคริสตัล | ห่อปีกเป็นดักแด้แล้วระเบิดกางออก |
| **Unlit Sun** (`unlit_sun`) | The End · วิหารวงแหวนสุญญากาศ | ดวงอาทิตย์มืดจาก voxel ออบซิเดียน รอยแตกทอง เปลือกแยก 8 กลีบรอบแกนหลอมเหลว คราสมีหนามดำและแสงทอง วงแหวนเศษหินเอียง เสาหินลอยลาย hexagon (เหมือนเสาในลาน) หยดสุญญากาศใต้ดวง | เปลือกระเบิดแตกเป็นสุริยุปราคา เผยแกนและดวงตาม่านตาตั้ง |

ภาพเพิ่มเติม (เรนเดอร์ด้วยแสง entity ของ Minecraft ตามที่ผู้เล่น Java เห็นผ่าน ModelEngine): `previews/realm-bosses/<model>-hero.png`, `-views.png`, `-detail.png`, `-clips.png`

## เทคนิคที่ใช้ (แบบเดียวกับ Arkhaia / Seraphon แต่โมเดลใหม่ทั้งหมด)

Arkhaia และ Seraphon สวยเพราะไม่ได้ปั้นเป็นบล็อกตามกริด แต่ปั้นด้วยโค้ด (`evergarden/tools/boss_forge.py`):

1. **ลูกบาศก์หมุนได้ทุกองศา** แทนบล็อกตั้งตรง เขา หนาม กรงเล็บ ทำจาก `taper`/`spike` ตามเส้นโค้ง Bezier
2. **แผ่นตัด alpha** (sheet) สำหรับของบาง: ปีก เปลวไฟ ผ้า ขนนก ขอบหยัก/ขาดรุ่ย
3. **วาดพื้นผิวทีละ texel** ทุกหน้าความละเอียดเท่ากันบน atlas 1024 ใช้ ramp สีจำกัด 6–8 สีจากมืดไปสว่าง + dithering แบบ pixel art มี bevel ขอบสว่าง/เงา ลายสลักร่อง และลายเรืองแสงในร่อง
4. **ชิ้นซ้อนเป็นชั้น** (เกราะซ้อนแบบหลังคาโกธิก ขอบโลหะ อัญมณี หนาม) ทำให้ซิลูเอตซับซ้อน ชิ้นซ้าย-ขวาใช้ texel ร่วมกัน
5. **ออร่าแยก lane** (กงล้อ วงเวท เศษหิน) หมุนวนตลอดไม่ถูกท่าต่อสู้รีเซ็ต + กระดูก marker สำหรับปล่อย particle
6. **ท่าทางด้วย IK** และการแกว่งตาม (ผ้า โซ่ หาง หนวด)

สิ่งที่เพิ่มใน `evergarden/tools/realm_forge.py` สำหรับบอสชุดนี้:

- ผิวใหม่: หินสกัดมีรอยแตกลาวา/วิญญาณ (`rock`), อิฐมีปูนเป็นลาวา (`brick`), กระดูก (`bone`), ผลึกเหลี่ยม (`crystal`), กระจกสีบนปีก (`mosaic` ตะกั่วทอง + ตาลายเพชร), ลาวาไหล, ไฟ pixel, ข้อโซ่, ธงไหม้ขอบ
- `chunked`: ปริมาตรที่ประกอบจากก้อนหินแยกกันวางสลับแบบก่ออิฐ บนแกนลาวา ทำให้แสงเรืองออกตามรอยต่อเหมือนภาพคอนเซ็ปต์ ทุกก้อนใช้การหมุนเดียวกันจึงนับเป็น display เดียวใน ModelEngine
- ระบบกันทะลุพื้น (Colossus): วางฝ่าเท้าราบตามมุมโลก ยกตัวเมื่อเท้า/แข้งจมพื้น ยกเป้า IK เมื่อหมัดจะทะลุพื้น; Hound วางอุ้งเท้าด้วย IK บนพื้น

| โมเดล | ลูกบาศก์ | กระดูก | display (ME) | scale |
|---|---|---|---|---|
| cinder_colossus | 513 | 38 | 207 | 3.6 (สูง ~13 บล็อก) |
| soul_hound | 506 | 39 | 276 | 2.8 (ยาว ~16 บล็อก) |
| crystal_moth | 407 | 40 | 285 | 3.5 (ปีกกว้าง ~13 บล็อก) |
| unlit_sun | 477 | 25 | 262 | 3.6 (ดวง ~6 บล็อก ลอยสูง ~8) |

ท่าทั้ง 14 ท่าของทุกตัว (ลำดับเดียวกับ `RealmBosses.CLIPS` และ Bedrock controller): `idle walk attack_windup/active/recover skill_windup/active/recover ultimate_windup/active/recover stun transition spawn` + `aura`

## การต่อสู้ · e2.165 realm-rework

รีแมคคานิคทั้งสี่ตัวให้ยากขึ้นมากและดาเมจแรงขึ้น **โดยไม่แตะ HP และไม่แตะการป้องกันของบอส** (HP เดี่ยว 3,800 / 3,200 / 3,400 / 3,600, ตัวคูณจุดอ่อน, เกจปีกแตก, เกณฑ์ตัดท่าเตาหลอม เหมือนเดิมทั้งหมด) ใช้แค่ 14 ท่าที่มีอยู่ ไม่ต้องอัปเดต pack

### ดาเมจ

ดาเมจบอสเป็น `INDIRECT_MAGIC` ซึ่งข้ามเกราะแต่ Protection ยังลดได้ ชุด Protection เต็ม (cap 80%) + Limit Break Protection (ลดเพิ่มอีกสูงสุด 50%) ทำให้เหลือดาเมจแค่ 4–10% บอสเลยดูง่าย รอบนี้แต่ละครั้งที่โดนจึงมีสองส่วน:

| ประเภท | เวทมนตร์ (ลดด้วย Protection) | **Rend** (sonic ไม่สน Protection) | เทียบเดิม (Prot IV ×4) |
|---|---|---|---|
| ปกติ | base × 2.8 (เดิม 2.35) | base × 0.5 | 10 base: 4.2 → **7.5 หัวใจ** |
| หนัก (พลาดท่าไม้ตาย) | base × 3.1 (เดิม 3.0) | base × 0.6 | 13 base: 7.0 → **11.2 หัวใจ** |
| ซ้ำ (ลำแสงปริซึม) | base × 2.0 (เดิม 1.65) | base × 0.3 | 11 base: 3.3 → 5.6 หัวใจ |
| พื้นร้อนต่อวินาที | base × 1.5 (เดิม 1.0) | base × 0.15 | 3.5 base: 0.6 → 1.2 หัวใจ |

- Rend ยังโดนลดด้วย Resistance, absorption และ Totem ตามปกติ
- **ตราแห่งมิติ (Realm Brand):** ทุกครั้งที่โดนตรงๆ (ไม่นับพื้นร้อน) ได้ 1 ชั้น ชั้นละ +10% ดาเมจจากบอสตัวนั้น สูงสุด 5 ชั้น (+50%) หายเมื่อไม่โดนอีก 7 วินาที — โดนติดกันคือตาย
- ผลติดตัวต่อบอส: Colossus = ไฟลุก (+Slowness จากหมัด), Hound = Wither (โดนตอนเป็นเหยื่อ = Wither II + Slowness), Moth = Slowness II, Sun = ไฟลุก (+Darkness ถ้าพลาดสุริยุปราคา)
- โจมตีแต่ละจังหวะโดนผู้เล่นคนละครั้ง (ไม่ซ้อน i-frame)
- ปรับได้: `dimensional-sanctuaries.bosses.damage-percent` (ทั้งหมด), `.<boss>.damage-percent`, `dimensional-sanctuaries.bosses.rend-percent` (100 = ค่าข้างบน, 0 = ปิด rend)

### จังหวะและเฟส

- รอบท่า 6 จังหวะ: โจมตี → สกิล 1 → **ท่ากดดันใหม่** → สกิล 2 → โจมตี → ท่าไม้ตาย
- พักระหว่างท่า เฟส 1 ~0.9–1.4 วินาที, เฟส 2 (≤65% HP) ×0.65, **เฟส 3 (≤30% HP) ×0.5** — เฟส 3 รอให้ชะงักที่ทำได้จบก่อนค่อยเริ่ม
- บอสเดินไล่เร็วขึ้น ~15–20%
- เฟส 3 มีแรงกดดันทั้งลานตลอดเวลา (ทำงานคู่กับทุกท่า): Colossus หินเพลิงร่วงใส่ทุกคนทุก 3 วิ (เล็งทางที่วิ่ง), Hound วงไฟวิญญาณผุดใต้เท้าทุกคนทุก 3.5 วิ (กะพริบขาวก่อน 0.8 วิ), Moth เศษคริสตัลร่วงตามตัวทุก 2.5 วิ, Sun **โคโรนา** ลำแสงสองแขนกวาดรอบลาน (เส้นซีดนำหน้า 12°) ต้องกระโดดข้าม

### ท่าของแต่ละตัว (ท่าทางที่ใช้)

`/sanctuary boss skill 0–4` สั่งท่าตามลำดับในตาราง (0 โจมตี, 1–2 สกิล, 3 ไม้ตาย, 4 ท่าใหม่)

**Cinder Colossus** (Nether · ป้อมมงกุฎ)
0. Crown Breaker (`attack_*`): ทุบ 2 ครั้ง (เฟส 2: 3) เล็งใหม่ทุกครั้ง บอสเดินลุยตามถ้าถอยไกล · บอก 0.9 วิ วง 4.4 แล้ว 0.7 วิ วง 3.8 · เฟส 2 ทิ้งพื้นร้อน
1. Magma Rift (`skill_*`): รอยแยก 3 เส้น (เฟส 2: 5 เส้น ±50° เส้นกลางแตกกิ่ง, เฟส 3 แตกกิ่ง 3 เส้น) บอก 1.1 วิ · เฟส 2 รอยแยกลุกไฟค้าง 3.5 วิ — หลบไปด้านหลังบอส
2. Crown Rubble (`skill_*`): หินตกใส่ทุกคนทุก 0.45 วิ 4/5/6 ระลอก เฟส 2 ระลอกเว้นระลอกเล็งไปทางที่วิ่ง + ถ่านร้อนตรงที่ตก
3. Furnace Eruption (`ultimate_*`): คลื่นลาวา 3 (เฟส 2: 4) ทุก 1.1 วิ ช่องปลอดภัยแคบลง (34° → 28°) หมุนทุกคลื่น (เฟส 3 หมุนสุ่มทิศ) · ตีแกนหลังให้ถึงเกณฑ์ = ตัดท่า + ชะงัก · **ถ้าตัดไม่ทัน** ลาวาทะลักใต้เท้าทุกคน
4. **Furnace Quake** (ใหม่, `skill_*`): บอก 1.1 วิ วง 7 บล็อกรอบตัว → ทุบ (กระโดดหลบได้) → คลื่นกระแทกให้กระโดดข้าม (เฟส 2 สองลูก) — ลงโทษคนยืนแปะตัว

**Soul-Eater Hound** (Nether · วิหารกระดูก)
0. Soul Fang (`attack_*`): พุ่ง 2/3/4 ครั้ง เล็งใหม่ทุกครั้ง (บอก 0.7 แล้ว 0.55 วิ) **ชนขอบลานไม่ชะงักแล้ว** · เฟส 2 ทิ้งรอยเท้าไฟวิญญาณ
1. Grave Burrow (`skill_*`): มุดดิน ตามตัว แล้วล็อกวง 3.8 บล็อก 0.8 วิ ก่อนโผล่ · ผุด 1/2/3 ครั้ง (สลับคนใกล้/ไกลสุด) · เฟส 2 ต่อด้วยงับพุ่ง
2. Living Prey (`attack_*`): หมายหัวหนึ่งคน กระโจน 3 ครั้ง (เฟส 2: 4) หลบได้ครบ = ชะงัก 1.8 วิ ×1.75 · โดน = Wither II
3. Soul Howl (`ultimate_*`): ลูกไฟวิญญาณ 3 (เฟส 2: 4) ลอยเข้าหาใน 3.5 วิ ทำลายครบ = ชะงัก ×2 · ระหว่างหอนมีคลื่นวิญญาณวง 6 บล็อกทุก 1.25 วิ · ลูกที่เหลือกลายเป็นไฟวิญญาณ + Darkness ทั้งลาน
4. **Bone Rend** (ใหม่, `attack_*`): ตะปบรูปพัด 120° ระยะ 6.5 บล็อก 2 ครั้ง (เฟส 2: 3) หันตามคนใกล้สุดทุกครั้ง · หลบด้านข้าง/ด้านหลัง กระโดดไม่ช่วย

**Crystal Moth Queen** (End · นครคริสตัล)
0. Prism Dive (`attack_*`): โฉบ 2 (เฟส 2: 3) เล็งใหม่ · เฟส 2 ทิ้งเศษผลึกตามทาง · ครั้งสุดท้ายร่อนต่ำ 1.7 วิ (เติมเกจปีกแตก)
1. Scale Tempest (`skill_*`): พัด 7 เส้น 2/3/4 ระลอก ระลอกคู่เลื่อนครึ่งช่อง (ช่องว่างย้าย) เฟส 2 หันเล็งใหม่ทุกระลอก
2. Prism Lattice (`skill_*`): เสา 3 (เฟส 2: 4) ลำแสงเชื่อมทุกคู่ 2 วิ · เฟส 2 เสาโคจรรอบลาน (เฟส 3 เร็วขึ้น) · ทำลายเสาให้เหลือ 1 = จบท่า
3. Prismatic Chrysalis (`ultimate_*`): ระเบิด 2 (เฟส 2: 3) เตือน 1.5 / 1.3 / 1.1 วิ ช่องปลอดภัยกระโดดไปคู่ใหม่แบบสุ่ม — ยืนใกล้กลางจะวิ่งทัน · จบด้วยร่อนต่ำ 2.1 วิ
4. **Shard Nova** (ใหม่, `skill_*`): ระเบิดผลึกวง 5.5 รอบตัว แล้วเศษคริสตัลร่วงตามทุกคน 3/4 ระลอก (เฟส 2 เล็งทางวิ่ง)

**Unlit Sun** (End · วงแหวนสุญญากาศ)
0. Solar Gaze (`attack_*`): ตามเล็ง 0.3 วิ ล็อก 0.6 วิ ยิงลำแสง 30 บล็อก 1/2/3 ครั้ง · ทุกนัดเปิดตา (โบนัสดาเมจ) 2.4 วิ
1. Corona Rings (`skill_*`): วงแหวนให้กระโดด/เดินผ่านช่อง 2/3/4 วง ทุก 0.7 วิ · เฟส 2 เหลือ 2 ช่อง แคบและเร็วขึ้น
2. Monolith Judgment (`skill_*`): 3/4/5 จังหวะ แกนหมุนทุกจังหวะ สลับระหว่าง "สองข้าง" (กลางปลอดภัย) กับ "กลาง + ขอบ" (ข้างปลอดภัย)
3. Total Eclipse (`ultimate_*`): สลับขั้ว 2 (เฟส 2: 3) เตือน 1.7 / 1.4 / 1.2 วิ — ยืนใกล้เส้นกลาง · จบด้วยคลื่น sonic แกนกลาง 16 (ไม่สนเกราะ) วง 7 บล็อก
4. **Dying Stars** (ใหม่, `skill_*`): ดาวตกใส่ทุกคน 4 (เฟส 2: 5) ระลอก ระลอกคู่เล็งทางวิ่ง + ดาวหลง 2 ลูก

ทุกท่าโผล่จาก telegraph ที่วิ่งหนีทันด้วยการ sprint (วง/เส้นตามเวลาบอก) หรือกระโดดข้าม (คลื่น/วงแหวน/โคโรนา); ระบบ anti-air ของ Nether/End เหมือนเดิมแต่แรงขึ้นตามสูตรใหม่

## ล่าจุดซุ่ม · e2.181 realm-perch

บอสทั้งสี่ตัวเดินได้แค่บนพื้นลาน (ไกลสุด รัศมี − 2.2) ท่าโจมตีพื้นโดนแค่คนที่สูงจากพื้นไม่เกินราว 2.2–3.8 บล็อก ส่วน anti-air ยิงเฉพาะคนที่ลอยอยู่ ก่อน e2.181 จึงมีจุดยิงฟรี: ช่องในกำแพง Crown Fortress (+10/+24), ซอกรอบวงของ Bone Temple (+5 ถึง +35), หิ้งขอบ Crystal City (+11 ถึง +21), พื้นนอกเส้นวง (R ถึง R+1.5) และบล็อกที่ค้างในลาน

**Perch Hunt** (`boss/PerchHunt`):
- **ลอยตัวไม่นับเลย** — Wind Charge / Mace / กระโดด ใช้ได้ทุกความสูงเหมือนเดิม
- นับเฉพาะเวลาที่ **ยืนบนบล็อก** สูงจากพื้นลาน ≥ 2.5 บล็อก หรือยืนนอกเส้นวงที่วาดไว้ · ยืนได้ฟรี 3 วินาที (ขึ้นเตือนที่วินาทีแรก)
- ครบ 3 วิ บอสเรียกท่าล่า เตือน 1.5 วิ (วงหมุน + เสาสีบอกตัวที่โดนล่า + เส้นจากบอส) — 1 วิแรกวงตามตัวไปตามหิ้ง ครึ่งวิสุดท้ายล็อก
- ท่าลงทะลุกำแพง (ไม่ต้องเห็นบอส) ดาเมจฐาน 12 → 16 → 20 ถ้ายังยืนค้าง ผ่านสูตรดาเมจปกติ (rend + Brand) และกระเด็นกลับเข้าลาน:
  - Cinder Colossus — อุกกาบาตทลายป้อม ระเบิดไฟ ดีดเข้ากลางลาน
  - Soul-Eater Hound — โซ่วิญญาณ **กระชากเข้าหาตัวหมาป่า**
  - Crystal Moth Queen — หอกปริซึม ดีดเข้ากลางลาน
  - Unlit Sun — แรงโน้มถ่วงสุริยะดับ ดึงเข้ากลางลาน
- บล็อกแปลกปลอมรอบจุดซุ่ม (ที่แบบ .ods ระบุว่าเป็นอากาศ) ถูกระเบิดทิ้ง ส่วนสิ่งก่อสร้างของวิหารไม่เสียหาย
- หลบ: ลงจากที่สูง (หรือขยับหนีในครึ่งวิสุดท้าย) · กลับลงพื้นลานแล้วความร้อนหายใน 1 วิ และระดับดาเมจรีเซ็ต
- **Skyfall**: ยืนบนที่สูงแล้วดิ่งลงมาฟาดบอสด้วย Mace = หลุดจากการล่าทันที + ล้างตราแห่งมิติ (Realm Brand) ของตัวเอง
- config (ไม่ต้องใส่ก็ได้): `dimensional-sanctuaries.bosses.perch-hunt.enabled` (true), `.grace-seconds` (3.0)

**ม่านวิหาร · e2.182 realm-ward:** ยิง/ตีจากนอกเส้นวงที่วาดไว้ (เกิน R+0.5) ไม่เข้าบอสแล้ว (เดิมได้ถึง R+1.5 ซึ่งบอสตีไม่ถึง) ส่วนเขตที่นับว่า "อยู่ในลาน" (กันบอสรีเซ็ต) ยังเป็น R+1.5 เท่าเดิม
- ลูกธนู/ตรีศูล/projectile ทุกชนิดถูกประทับตอนปล่อยว่ายิงจากในหรือนอกวง (`realm_ring_shot`) — ยิงจากในวงแล้วถอยออกไปยืนขอบก็ยังเข้า ยิงจากนอกวงแล้วเดินเข้ามาก็ไม่เข้า
- ลูกธนูที่ยิงจากนอกวงจะสลายตรงเส้นวง (ประกายสีวิหาร + เสียงกระทบ) ตรีศูลไม่ถูกลบ (กันของหาย) แค่ไม่ทำดาเมจ · ตีด้วยมือ/เวท ดูจากตำแหน่งผู้เล่นตอนโดน · ทำลายลูกไฟวิญญาณ/เสาคริสตัลจากนอกวงก็ไม่ได้
- ผู้เล่นได้ข้อความ "ม่านวิหารสลายการโจมตีจากนอกวง — เข้ามาสู้ในวงแหวน" (ไม่เกินทุก 2 วิ)
- Perch Hunt: ความร้อนลดเฉพาะเมื่อลงพื้นลึกเข้าไปในวง ≥ 4 บล็อก (บอสเดินถึง R−2.2) — ยืนเลียบขอบหรือเข้าๆ ออกๆ ความร้อนไม่หาย สะสมจนโดนล่า และวงล่าตามไปตามขอบวงจนกว่าจะเข้าลึก

**ตีแล้ววิ่งหนี · e2.183 realm-hitrun:** คนที่ตีบอสเข้าภายใน 15 วิที่แล้ว ยังอยู่ในระบบล่าแม้ถอยออกไปนอกเขตลาน (เช่นหลบในทางเดิน/ประตู ไกลไม่เกิน R+48) — ยืนตรงนั้นสะสมความร้อน โดนล่าทะลุกำแพงและดีดกลับเข้าลาน · คนที่ยืนรอข้างนอกโดยไม่ได้ตีไม่โดน

**ลานสะอาด:** ทุกครั้งที่บอสเกิดและตอนเริ่มสู้ `RealmSanctuaryEncounters` ทยอยกวาดลาน 4 chunk ต่อ tick ทั้งปริมาตรที่นับเป็นลาน (รัศมี R+2, จากใต้เท้า 4 บล็อกถึง H+28) ทุกช่องที่แบบระบุเป็นอากาศแต่มีบล็อกค้าง (ฟอสซิล, ลำต้นเห็ดยักษ์, ลาวา ฯลฯ) จะถูกล้าง แล้วเขียน log `Cleared N stray blocks from the … boss arena` · ระบบซ่อม chunk ใหม่รู้จัก bone block, crimson/warped stem, shroomlight, obsidian, crying obsidian แล้ว (เดิมไม่รู้จัก จึงค้างในลาน Bone Temple ซึ่งชอบเกิดใน Soul Sand Valley)

## รางวัล · e2.166 realm-rewards

ชนะบอสแล้วได้อะไร:
- **Core ของบอส 1 ชิ้น** ตกกลางลาน (ใช้ร่วมกัน): `Core of Cinder Colossus` / `Soul-Eater Hound` / `Crystal Moth Queen` / `Unlit Sun` มีเทคเจอร์ทั้ง Java และ Bedrock (`evergarden/art/realm-cores/`, วาดด้วย `evergarden/tools/realm_core_art.py`) — Soul Orb เดิมที่ไม่มีเทคเจอร์จะเปลี่ยนเป็น core ให้เองเมื่อผู้เล่นเข้าเกมหรือเปิดกล่อง
- เอา core ไปแลกที่ **Seraph → ร้านตำนาน**: core ละ **ขนนกเซราฟทองคำ 5 อัน** ไม่จำกัดจำนวน (`seraph-trader.prices.realm-core-golden`)
- ผู้ที่ช่วยตีและยังออนไลน์อยู่ในโลกนั้นได้ขนนกธรรมดา 5 ตามค่า config เริ่มต้น (ติดเพดานรายวัน) + แต้ม skill 1 และ **ทอยของรางวัลแยกกันทุกคน**; ผู้ที่ออกจากเกมหรือย้ายโลกก่อนบอสตายไม่ได้รับรางวัลส่วนตัว:
  - บอส Nether (Cinder Colossus, Soul-Eater Hound): **อาวุธหายาก** (Rare Weapon ชุดนักผจญภัย 20 แบบ สุ่ม 1 ชิ้น) 50% · **Netherite Ingot 4 ชิ้น** 25%
  - บอส End (Crystal Moth Queen, Unlit Sun): **สมุดเวทเกินขีดจำกัด** 50% — เป็นสมุดเอนชานต์จริง (ไม่ใช่ Limit Break) เลเวล = สูงสุดของสมบัติบอส + 2 เช่น Protection 10, Sharpness 10, Efficiency 10, Power 10, Looting 7, Fortune 7, Unbreaking 8, Feather Falling 9 · 1–3 เอนชานต์ของอุปกรณ์ชนิดเดียวกันในเล่ม (ไม่ชนกัน) · ตีกับทั่งได้เลย (ติด entitlement ของระบบเอนชานต์บอส) และคุมค่าตีบนของใหม่ไม่เกิน 30 เลเวล จึงไม่ติด "Too Expensive"
- ปรับได้ใน config: `dimensional-sanctuaries.rewards.rare-weapon-chance` (0.5), `.netherite-chance` (0.25), `.netherite-amount` (4), `.enchant-book-chance` (0.5), `.enchant-book-bonus-levels` (2)
- ของที่ไม่พอช่องจะตกที่เท้าผู้เล่น

**ทั่ง · e2.167 anvil-compat:** ทั่งในโหมด Creative ของเกมข้ามการเช็กชนิดไอเท็ม (vanilla) ทำให้สมุดรองเท้า (Protection X + Feather Falling IX) ตีเข้าดาบได้ ตอนนี้ `BossEnchantAnvil` ตัดเอนชานต์บอสที่ไอเท็มรับไม่ได้ออกทุกโหมด เหมือน Survival · ผลจริงวัดบน Paper (ดาเมจ 20): เกราะ Protection X ×4 เหลือ 1.77, Protection VIII 2.45, ไม่ใส่ 20 · Protection บนดาบที่ถือไม่ลดอะไร · ตกจากที่สูง Feather Falling IX เหลือ 3.01, VII เหลือ 3.84 (เกิน 80% ของเกม ทุกแต้มที่เกินลดเพิ่ม 4% ของที่เหลือ)

**ตรวจเอนชานต์ · e2.169 enchant-audit** (`RealmBossesNativeProbe.verifyNoAnvilClimb` + `verifyEnchantAudit`, Paper จริง; ชุดบอสรวม PASS 2,326 checks ซึ่งรวมการตรวจเรื่องอื่นด้วย):
- กรณีที่ทดสอบบนทั่งตีไต่ระดับไม่ได้: VIII+VIII = VIII, X+X = X, Sharpness VIII+VIII, Looting VII+VII, Efficiency X+X ไม่ขึ้น · นอกจากสมุดบอส ยังถ่ายระดับที่ได้รับจริงจากอุปกรณ์ที่มีตรารางวัลไปยังอุปกรณ์ชนิดเดียวกันได้ และ Vault อาจให้สมุดหรืออุปกรณ์มีตราในช่วงระดับสมบัติบอสเดิม · สมุดปลอมไม่มีตรายก Protection เกิน IV ไม่ได้ · ปิด `mechanics.anvil-transfer` แล้วสมุดบอสใส่ได้แค่ระดับ vanilla
- ผลเอนชานต์ที่วัด (Wind Burst ยังไม่ได้วัด): Sharpness X +5.5 (VIII +4.5) · Power X 7.5 (VIII 6.5) เมื่อใช้ base arrow damage 2 ไม่ใช่ความเสียหายสุดท้ายทุกนัด · Impaling X +25 (VIII +20) · Density IX 4.5/บล็อก (VII 3.5) · Breach VII ไม่สนเกราะเลย (V 75%) · Punch V 5 (III 3) · Fire Aspect V ไฟ 20 วิ (III 12) · Unbreaking VIII สึก 1/9 (VI 1/7) สำหรับดาบ; เกราะมีโอกาสสึกต่างกัน · Piercing VIII 8 ตัว · Loyalty VI 6 (IV 4) · Riptide VI 5.25 (IV 3.75) · Sweeping VII 87.5% (V 83.3%) · Efficiency X ความเร็วขุด 101 (VIII 65) · Respiration VII 7 · Depth Strider / Swift Sneak ตันที่ III / V ในเกม แต่ Evergarden เพิ่มความเร็วต่อ (VII ×1.4 / ×1.35) · Soul Speed VII 0.2035 (V 0.1825) · Frost Walker V แช่แข็ง 145 บล็อก (III 69) · Protection X / Feather Falling IX ดูด้านบน
- **Fortune VII ทุบแร่เพชรจริง 300 ครั้ง ได้ 4.0 เม็ด/ก้อน (V 3.03, ไม่มี 1.0)** · **Looting VII ฆ่าซอมบี้จริง 250 ตัว ได้เนื้อเน่า 4.44/ตัว (V 3.43, ไม่มี 1.04)**
- แก้: core ที่เปลี่ยนชื่อในทั่งค้าง repair cost ทำให้ Seraph ไม่รับ — ตอนนี้กลับเป็น core มาตรฐานเอง
- แพ็กล่าสุดขึ้น GitHub release `resources-e2.169-am1.38.1` (`tools/publish_github_release.py` ดึงจากไฟล์ปลั๊กอินที่ปล่อยแล้ว ตรวจ SHA-1 หลังดาวน์โหลด)

**แผนที่:** Seraph → ร้านแผนที่ ขายแผนที่ไป Sanctuary Nether / End ทั้ง 4 แห่ง ราคาเท่าแผนที่ Sanctuary โลกปกติ (30 ขนนกเซราฟ) ดูรายละเอียดที่ [seraph-sanctuary-maps.md](seraph-sanctuary-maps.md)

**การตรวจสอบ e2.166:** `tools/build_realm_rewards_release.py` (เปลี่ยนเฉพาะ 5 คลาส + item pack Java/Bedrock 3.65.0 + Geyser mapping + pack-hashes + judge-design.json + plugin.yml; entry อื่น 653 รายการตรงทุกไบต์; asset gate 1,514 checks) · Paper 26.3 + ModelEngine: `--realm-bosses` **PASS 2,277** (core/เทคเจอร์/แปลง Soul Orb เดิม, แลกขนนกทองคำผ่าน native trade, โอกาสดรอปทุกแบบ, สมุด 300 เล่ม: เลเวล = สูงสุด+2 ไม่ชนกัน ค่าตี ≤ 30 มี entitlement, ทั่งจริงใส่ Protection 10 บนเกราะเพชรได้ด้วย 10 เลเวล) · `--boss-readiness` PASS 7 · `run_sanctuary_map_checks.py` PASS 584 + 236 หลังรีสตาร์ต (ครบ 13 ปลายทาง) · Judge/pack mirror เผยแพร่แล้ว (evergarden pack `1d88fc8e`)

## เสกดูในเกม

- `/evergarden test` → **⚔ ทดสอบ World Boss** → **บอสประจำมิติ (Nether / End)** (หรือ บอสหลัก Evergarden → ปุ่มบอสประจำมิติ)
- คลิกซ้าย = เสกหน้าคุณ 16–20 บล็อก (ต้องมีพื้นราบ) · แถวล่างเลือกท่า: วนโชว์ / ยืน / เดิน / โจมตี / สกิล / ท่าไม้ตาย / ชะงัก / เปลี่ยนเฟส · ปุ่มกรรไกรลบ · อยู่ได้ 10 นาที ไม่โจมตี ไม่มีรางวัล
- คำสั่ง: `/evergarden boss model cinder_colossus|soul_hound|crystal_moth|unlit_sun` · `/evergarden boss model realm` เปิดเมนู · `/evergarden boss model pose <ท่า>`
- Java เห็นผ่าน ModelEngine, Bedrock เห็นเป็น native Geyser entity (Bedrock pack 3.63.0)

### ModelEngine รุ่นจำกัดจำนวนโมเดล (สำคัญ) · e2.160

ModelEngine R4.1.1 ที่ใช้ในเซิร์ฟเวอร์ทดสอบและ Squid (SHA-256 `020ef936…`) นำเข้าได้ **12 โมเดลรวมกัน** และ config ของ ModelEngine ไม่มีค่าให้เพิ่มจำนวน บอสเดิม (Arkhaia, Seraphon, ลูกน้องแม่น้ำ 8) + `internal_fire` ใช้ไปแล้ว 11 ถ้าใส่บอสใหม่แยกกันสี่ไฟล์ ModelEngine จะทิ้งโมเดลแบบสุ่ม รวมถึง `internal_fire` ทำให้บอสทุกตัวแสดงผลไม่ได้

ตั้งแต่ **e2.160** บอสทั้งสี่ตัว**รวมอยู่ใน blueprint เดียว** `realm_bosses.bbmodel` ใช้ช่องเดียว (รวมทั้งหมดยังเป็น 12):

- กระดูกแต่ละตัวมีรหัสนำหน้า (`cc_` `sh_` `cm_` `us_`) ท่ามีชื่อโมเดลนำหน้า (`cinder_colossus_idle` …) และ texture แยกตัวละ 1 ไฟล์
- ตอนเสก ปลั๊กอินตัดกระดูกของอีกสามตัวทิ้งจากโมเดลชิ้นนั้น จึงเหลือแค่ตัวที่เสก (ภาระเท่าโมเดลเดียว)
- **เสกได้ครบทั้งสี่ตัวทันที ไม่ต้องรีสตาร์ต** ไม่ต้องคลิกขวาเลือกอีกแล้ว (`realm-install.txt` ของ e2.159 ไม่ถูกใช้แล้ว)
- เซิร์ฟเวอร์ที่เคยลง e2.159 ไว้ ปลั๊กอินจะถอด `cinder_colossus.bbmodel` เดิมออกจาก `plugins/ModelEngine/blueprints/` ให้เอง (ไฟล์ที่แอดมินแก้เองจะไม่ถูกลบ)
- ถ้าต้องการคืนช่องนั้น ตั้ง `realm-bosses.model-preview.enabled: false` แล้วรีสตาร์ต
- Bedrock ไม่เปลี่ยน ยังเป็น native entity แยกตัว
- หลังอัปเดต Judge pack (Java) เปลี่ยน ผู้เล่นจะได้รับ pack ใหม่อัตโนมัติ ถ้ายังเห็นของเก่าใช้ `/evergarden boss pack resend`

## ไฟล์

- ตัวสร้างโมเดล: `evergarden/tools/realm_forge.py`, `cinder_colossus.py`, `soul_hound.py`, `crystal_moth.py`, `unlit_sun.py`; สร้างทั้งหมด + ภาพตัวอย่าง: `python evergarden/tools/realm_bosses.py --preview --clips`
- Blueprint: `evergarden/src/main/resources/modelengine/realm/*.bbmodel` + manifest; atlas: `evergarden/art/realm-bosses/`
- Java: `boss/RealmBosses` (ใหม่), `BossShowcase`, `BossAura` (ออร่า 4 แบบ: ลาวา/ไฟวิญญาณ/ประกายผลึก/คราส), `BossHitbox`, `ModelEngineArkhaia` (ติดตั้งแบบเลือกได้), `ArkhaiaBedrockSupport`, `WorldBossManager`, `gui/AdminTestGui`, `command/VoidCommand` ไม่แตะไฟล์ของ dimensional sanctuaries
- Blueprint รวม: `python evergarden/tools/realm_bosses.py --merge-only` → `modelengine/realm/realm_bosses.bbmodel`
- Release: e2.159 `tools/build_realm_bosses_release.py` · e2.160 `tools/build_realm_bosses_merged_release.py [--validate] [--promote]`

## การตรวจสอบ

- `tests/model-engine/check_realm_bosses.py`: **PASS 35,307 checks** (bone/clip contract, ModelEngine lanes, UV, alpha sheet, ระยะ cube จาก pivot ≤ 30, display ≤ 330, Bedrock parity, ไม่ทะลุพื้นทุกท่ายกเว้น spawn/การมุดดิน)
- Paper 26.3 + ModelEngine R4.1.1 จริง (`tests/model-engine/RealmBossesNativeProbe.java`, `tools/try_model_engine.py --realm-bosses [--realm-install <id>]`): **PASS 4 × 32 checks** — ค่าเริ่มต้น และเลือกแต่ละตัว (พร้อมถอดตัวเดิม): internal_fire และบอสเดิมยังโหลดครบ, เสก/ท่า/ออร่า/Bedrock carrier/ลบ ทำงาน, ตัวที่ไม่ได้เลือกปฏิเสธการเสก
- `--boss-readiness` บน candidate: PASS 7 · `check_evergarden_bosses.py`: PASS 34,269
- Candidate `output/realm-bosses-20261029/build/evergarden.jar` = `3.0.0-e2.159-realm-bosses` (SHA-256 `ee5f00c8…`) สร้างบน e2.158-temple-rewards; entry อื่นตรงกับ e2.158 ทุกไบต์

### e2.160 (blueprint รวม)

- `check_realm_bosses.py`: **PASS 37,549 checks** รวมการตรวจ blueprint รวม (texture ตรงกับตัวเดิม กระดูก/cube/ท่า/keyframe ครบ ท่าของแต่ละตัว key เฉพาะกระดูกตัวเอง ชื่อกระดูกไม่ชนกัน)
- Paper 26.3 + ModelEngine R4.1.1 จริง: **PASS 74 checks** — เริ่มจากเซิร์ฟเวอร์ e2.159 ที่มี `cinder_colossus` ค้างอยู่: ถูกถอดออก, ModelEngine มี blueprint ของ Evergarden 11 ไฟล์ + `internal_fire` = 12, บอสเดิมพร้อมครบ, เสกทั้งสี่ตัวในรอบเดียว แต่ละตัวเหลือเฉพาะกระดูกตัวเอง (38/39/40/25) พร้อม head tracker, ท่า spawn/aura/เมนูท่า/Bedrock carrier/ลบ ทำงาน
- `--boss-readiness`: PASS 7
- ปล่อยแล้ว: `dist/evergarden.jar` = `3.0.0-e2.160-realm-bosses-merged` · Judge pack สำรองบน mirror `b8d1664c…`

### e2.165 (realm-rework)

- `python tools/build_realm_rework_release.py [--validate] [--promote] [--rebase]` สร้างบน e2.163-realm-challenge เปลี่ยนเฉพาะ `RealmBossController` + `plugin.yml` (entry อื่น 667 รายการตรงทุกไบต์ รวม anti-air และ Bedrock pack)
- Paper 26.3 + ModelEngine R4.1.1: `--realm-bosses` **PASS 242** (ตัวเลขดาเมจผ่าน Protection IV ×4, Brand +10%/สูงสุด 5 ชั้น, พื้นร้อนไม่เพิ่ม Brand, rend-percent 0 ปิด rend, damage-percent 50 ลดทั้งสองส่วน, ทุกท่าทั้ง 20 ท่า × เฟส 1/เฟส 3 จบเอง บอสโผล่จากดิน แกนปิด, 30% HP เปิดเฟส 3) · `--nether-antiair` PASS 515 · `--realm-melee` PASS 17 · `--realm-projectile` PASS 10 · `--boss-readiness` PASS 7

### e2.181 (realm-perch)

- `python tools/build_realm_perch_release.py [--validate] [--promote] [--rebase]` สร้างบน e2.180-forge-test-menu เปลี่ยนเฉพาะ `RealmBossController`, `RealmSanctuaryEncounters`, `PerchHunt` (ใหม่), `DimensionalSanctuaries` + `plugin.yml` (entry อื่น 689 รายการตรงทุกไบต์ แพ็กไม่เปลี่ยน)
- `tests/model-engine/RealmPerchProbe.java` (รันใน `--realm-bosses`): ลอยตัว/พื้นลาน/ในเส้นวง/creative ไม่โดนล่า, 3 วิแรกฟรี, วงตามตัวตามหิ้งแล้วล็อกครึ่งวิสุดท้าย, ดีดกลับเข้าลาน, 12 → 16, ลงพื้นแล้วหลบได้และรีเซ็ต, นอกเส้นวงโดนล่า, Skyfall · สร้าง Bone Temple จริงแล้วกวาดลาน (ลบฟอสซิล/ลำต้นเห็ด/ลาวาที่วางไว้ 5 ก้อน สิ่งก่อสร้างไม่เปลี่ยนแม้แต่บล็อกเดียว), repair คืนผนังที่โดนฟอสซิลทับ, Soul-Eater จริงล่าคนในซอกผนังทะลุกำแพงแล้วกระชากเข้าหาตัว, ซอกของวิหารไม่เสียหาย, ระเบิดบล็อกแปลกปลอมใต้เท้า
- Paper 26.3 + ModelEngine R4.1.1: `--realm-bosses` **PASS 2,355** · `--nether-antiair` PASS 515 · `--realm-melee` PASS 18 (รวม Mace smash จริงหลังยืนบนที่สูง = Skyfall ล้าง Brand) · `--realm-projectile` PASS 10 · `--boss-readiness` PASS 7

### e2.182 (realm-ward)

- `python tools/build_realm_ward_release.py [--validate] [--promote] [--rebase]` สร้างบน e2.181 เปลี่ยนเฉพาะ `RealmSanctuaryEncounters`, `PerchHunt` + `plugin.yml` (entry อื่น 707 รายการตรงทุกไบต์)
- Paper 26.3 + ModelEngine R4.1.1: `--realm-bosses` **PASS 2,359** (+ ยืนเลียบขอบเก็บความร้อน, เข้าๆ ออกๆ สะสมจนโดนล่า, วงล่าตามขอบ, เข้าลึก 4 บล็อกล้างความร้อน) · `--realm-projectile` **PASS 17** (ประทับลูกธนูในวง/นอกวง, ม่านสลายลูกธนูนอกวงที่เส้น, ดาเมจจากนอกวงถูกปฏิเสธทั้งลูกธนูและตี, ยิงจากในวงแล้วถอยไปขอบยังเข้า) · `--nether-antiair` PASS 515 · `--realm-melee` PASS 18 · `--boss-readiness` PASS 7

### e2.183 (realm-hitrun)

- `python tools/build_realm_hitrun_release.py [--validate] [--promote] [--rebase]` สร้างบน e2.182 เปลี่ยนเฉพาะ `RealmBossController` + `plugin.yml` (entry อื่น 706 รายการตรงทุกไบต์)
- `RealmPerchProbe.corridor` (ผู้เล่นทดสอบแยก): คนนอกลานที่ไม่ได้ตีไม่โดน, ตีจากในวงแล้วหนีไปทางเดินโดนล่าและดีดกลับ, ผ่าน 15 วิหลังตีครั้งสุดท้ายแล้วไม่โดน
- Paper 26.3 + ModelEngine R4.1.1: `--realm-bosses` **PASS 2,364** · `--nether-antiair` PASS 515 · `--realm-melee` PASS 18 · `--realm-projectile` PASS 17 · `--boss-readiness` PASS 7
