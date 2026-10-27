อัปเดต 22 ต.ค. 2026: โมเดลรูปปั้นของ Seraphon/Arkhaia ในเอกสารนี้ถูกแทนด้วยโมเดล ModelEngine ใหม่ ดู [บอสหลัก Evergarden](evergarden-main-bosses.md) ส่วนค่าพลังและสกิลด้านล่างยังเป็นข้อเสนอสำหรับเฟสถัดไป

# Seraphon & Arkhaia — Boss model and encounter design

อัปเดตเฟส 1: Evergarden `3.0.0-e2.49-boss-architecture-redesign` ออกแบบใหม่เป็นปราสาท Arkhaia บนหน้าผา, วิหารปีก Seraphon Valkyrie สำหรับโมเดลถืออาวุธ และนครวิหารวงกลม Seraphon สำหรับโมเดลไม่ถืออาวุธ พร้อมลานและกล่องสมบัติ ดู [โครงสร้าง การเกิด และคำสั่งวาร์ป](boss-structures-phase-one.md) การอัญเชิญและสกิลของบอสใหม่อยู่ในเฟสถัดไป

สถานะ: โมเดล Java/Bedrock **white-eyes v2** รวมใน Evergarden **3.0.0-e2.47-faction-model-previews** วันที่ 13 ตุลาคม 2026 แอดมินเสกพรีวิวจากเมนูทดสอบได้ ค่าพลังและสกิลในเอกสารนี้ยังเป็นข้อเสนอสำหรับ playtest ไม่มี combat handler ของบอสสองตัวนี้

**Seraph เป็นตัวละครฝั่งดีที่มีโมเดลอยู่แล้ว ส่วน Seraphon และ Arkhaia เป็นบอสทั้งสองตัวตามภาพอ้างอิง** บทบาท Church of Raphion / Cult of Thanasius และเผ่า Sheea / Inferion มาจากคำขอผู้ใช้ รายละเอียดเรื่องราวและกลไกเพิ่มเติมด้านล่างเป็นข้อเสนอการออกแบบ

## 1. มาตรฐานที่ศึกษาจาก Seraph

อ่าน [ตัวสร้างโมเดล Seraph](../evergarden/tools/seraph_preview_assets.py), [ตัวเรนเดอร์พรีวิว](../evergarden/tools/preview_seraph.py), [ตัวแสดงโมเดลในเกม](../evergarden/src/main/java/com/example/voidscape/boss/SeraphPreview.java) และ [การตรวจ asset](../evergarden/tests/check_judge_assets.py) แล้วใช้เป็นฐานงานใหม่:

| ส่วน | Seraph ที่มีอยู่ | บอสต้นแบบใหม่ |
| --- | --- | --- |
| Geometry | 193 cuboids ต่อท่า; 122 ชิ้นมี rotation | เกราะหลายชั้น ขอบโลหะ อัญมณีและปีกเป็น geometry; ต่ำกว่าเพดาน 256 cuboids ต่อท่า |
| Texture | RGBA atlas 1024×1024; tile 64×64 | RGBA atlas 1024×1024 แยกคนละตัว; tile 128×128 สำหรับหน้า ลายเกราะ ลายขนและเยื่อปีก |
| Pose | idle / cast เปลี่ยนท่าแขน | idle / cast แยก item model; ยกฝ่ามือและเปลี่ยนตำแหน่งกรงเล็บตามแขน |
| Java | ItemDisplay, FIXED, scale 1.6, ยก model center 2 บล็อก | ใช้พิกัดและ scale เดียวกัน; `voidscape:boss_<name>_<pose>` |
| Bedrock | Helmet attachable บน ArmorStand ล่องหน | canonical mesh เดียวกัน; สะท้อนแกน X และแปลง UV/pivot แบบ Seraph |
| Culling | Java width/height 7; Bedrock bounds 7×7 | ตรวจมุม cuboid หลัง rotation ให้อยู่ใน bounds จริง |
| Preview | อ่าน geometry/texture จากแพ็กที่สร้างเสร็จและใช้ depth buffer | ใช้วิธีเดียวกัน พร้อมด้านหน้า ด้านข้าง ด้านหลัง และภาพ idle/cast |

โมเดลฉบับส่งมอบ: Seraphon idle/cast 222/225 cuboids สูง 3.96 และกว้างรวมปีก 4.49 บล็อก; Arkhaia idle/cast 131/134 cuboids สูง 3.87 และกว้างรวมปีก 4.73 บล็อก ที่ display scale 1.6 ขนปีกของ Seraphon ใช้ construction ของ Seraph ส่วน Arkhaia ใช้เยื่อปีก 512×512 ต่อข้างใน atlas เดิม พร้อมกระดูกปีกสามมิติและขอบเว้าโค้งระหว่างนิ้วปีก

**การแก้ v2:** แยกผิวใบหน้าเฉพาะด้าน `north` เพียงหนึ่ง surface ต่อบอส ผิวหัว ข้างหัว ท้ายหัว มือ และเกราะใช้ texture ที่ไม่มีตาหรือปาก ลดหัวจากกว้าง 6.2 เป็น 4.5 model units ปรับ torso ให้สูงและเอวสอบ ดวงตาทั้งสองตัวเป็นสีขาวไม่มีรูม่านตา มี eye inserts แยกเพื่อรักษาความสว่าง ภาพตรวจใบหน้าแสดง 8 มุมรอบแต่ละโมเดล และ validation ตรวจ UV ใบหน้าจากแพ็กจริงทั้งสอง pose/client

วิธีรักษารายละเอียดคือใช้ชิ้น geometry กับส่วนที่เปลี่ยน silhouette ได้แก่ crown, pauldron, ปลายขน, horn, wing spar และ weapon ส่วนลายสลัก เส้นขนและลายปักอยู่บน texture ไม่เพิ่ม cube แยกทุกเส้น รูป JPG เป็นเป้าหมายด้านรูปทรงและวัสดุ โมเดลที่ส่งมอบใช้รูปแบบ Minecraft เช่นเดียวกับ Seraph ที่ศึกษา

## 2. Visual & 3D Model Spec

### Seraphon — The Gilded Verdict / Church of Raphion

**Art direction:** นักรบ Sheea ผู้บังคับบัญชาคำพิพากษาของโบสถ์ รักษารูปหญิงนักรบผมทอง เกราะทอง ปีกขาวทองและประกายฟ้าจากภาพอ้างอิง บุคลิกยืนสง่า เคร่งขรึม อำนาจของแสงถูกใช้เป็นเครื่องมือบังคับให้ทุกคนยอมรับคำพิพากษา

| ส่วน | สเปก |
| --- | --- |
| Armor | torso สูง เอวสอบ; breastplate แบ่งแผ่นโค้งเฉียง; ขอบทองนูน ลายสลักเพียงจุดหลัก; pauldrons 2 ชั้น; tassets แยกและ greaves เรียว |
| Head | ใบหน้าเคร่งขรึม ผิวซีด ดวงตาขาวไร้รูม่านตา; ผมทองแยกปอยข้างแก้มและท้ายหัว; diadem แนบศีรษะ; halo แปดเหลี่ยมด้านหลัง; ข้างและท้ายหัวไม่มีใบหน้า |
| Wings | ขนหลักยาวและ coverts ซ้อนคนละระยะความลึก; ขนแบ่ง 3 ช่วงเรียวปลาย ใช้ UV crop ต่อเนื่อง; มีขนบังข้อต่อที่หัวไหล่ โทนขาวทองกับชั้นฟ้าอ่อน |
| Cloth | tabard งาช้างผ่ากลาง ลายปักทองและแผงด้านข้าง เปิดให้เห็นข้อเข่าและเท้า |
| Weapon | หอกพิธี Raphion's Edict ด้ามทองต่อเนื่อง หัวหอกสองปีกและ core ฟ้า; silhouette แยกจากลำตัว |
| Aura ที่ออกแบบ | ผงทองเบาบางและวงพิธีใต้เท้า; ระหว่าง cast วง halo เพิ่มความสว่าง และ core ที่ฝ่ามือปรากฏ; ระหว่างเปิดจุดอ่อน core หน้าอกเป็นแสงขาวฟ้า |
| Cinematic target | key light สีอุ่นด้านหน้าเฉียง; rim สีฟ้าจากด้านหลัง; จำกัด bloom เพื่อให้เห็นลายสลักและใบหน้า |

Palette: Gilded Armor `#C49137`, Gold Edge `#FAD780`, Antique Bronze `#5F4323`, Ivory Cloth `#ECE7D4`, Feather `#FFF2CD`, Celestial Cyan `#58CEEA` สีฟ้าใช้กับอัญมณีและส่วนปลายปีกเพื่อแยกจากทอง

### Arkhaia — The Ghostflame Sovereign / Cult of Thanasius

**Art direction:** ผู้นำ Inferion ในเกราะออบซิเดียน ดวงตาขาวไร้รูม่านตาและรอยแตกพลังสีม่วง มีเขา ปีกเยื่อขนาดใหญ่และดาบยักษ์ ตัวเกราะต้องอ่านเป็นสีดำที่รับแสง ไม่กลายเป็นก้อนดำเมื่ออยู่ในฉากมืด

| ส่วน | สเปก |
| --- | --- |
| Armor | plate สีดำม่วง มี bevel สีโลหะอ่อน; chest, abdomen และ greaves ซ้อนชั้น; shoulder spike; ร่อง Ghostflame แยกเป็น insert geometry สีม่วง |
| Head | closed infernal helm หน้าคม; visor ดวงตาขาว; brow/cheek plates และ nose guard สามมิติ; เขาโค้งต่อเนื่อง 3 ช่วง; ข้างและท้ายหมวกใช้ลายโลหะเรียบ ไม่มีลายคล้ายหน้า |
| Wings | เยื่อปีกกว้างรูปค้างคาว ขอบเว้าโค้งจากนิ้วปีกจริง; มี arch และ radial finger bones สามมิติ; UV เยื่อแต่ละข้างใช้พื้นที่ 512×512; ขอบบางด้านบน/ข้างของ cuboid ใช้ UV โปร่งใสเพื่อไม่ให้เกิดภาพเยื่อซ้ำ |
| Cloth | mantle สีดำม่วงแยกเป็นแผงด้านหลังและแถบด้านหน้า เว้นช่องขาและดาบ |
| Weapon | ดาบ Thanasius' Ruin; ใบมีด 3 ช่วงเรียวลง มีช่องพลังม่วงกลางใบ guard กว้างและอัญมณีที่โคน |
| Aura ที่ออกแบบ | Ghostflame ลอยรอบข้อเท้าและ shoulder vents; ควันโปร่งบางและเศษหินเล็ก; เปิดจุดอ่อนแล้ว seam หน้าอกเปลี่ยนเป็นขาวม่วง |
| Cinematic target | key light ม่วงเฉียงหลัง; fill เทาเย็นที่ใบหน้าและ torso; แสงลาวาสีส้มใช้กับฉากเพื่อแยกจากพลังบอส |

Palette: Obsidian Armor `#2B223A`, Metal Edge `#80669B`, Deep Shadow `#14111B`, Mantle `#251A2F`, Ghostflame `#B146F7`, Core Highlight `#CF6FFF` ให้สีพลังครองเฉพาะ visor, core และรอยแตก

### Animation, VFX และงานต่อจาก prototype

ต้นแบบมี **2 static poses** และไม่มี skeletal animation ขนปีก/เยื่อปีกในรอบนี้ วงแสงจริง ปริมาณ particle, wing beat, weapon swing, hit reaction และ death dissolve เป็นข้อกำหนดสำหรับขั้นต่อไป เช่นเดียวกับ runtime ของ Seraph ให้ server เป็นเจ้าของ anchor แล้วใช้ item model สลับ pose ตาม cast state

Java ใช้ `shade:false` กับชิ้นพลังบางส่วน แต่ยังไม่ใช่ระบบ PBR/emissive ที่ให้ bloom เอง Bedrock ใช้ material `armor` แบบเดียวกับ Seraph การทำ emission แยกส่วนและ VFX ให้เท่ากันทั้งสอง client ต้องทำเพิ่มเติม ภาพพรีวิวที่แนบแสดง geometry และ texture จริงภายใต้แสง studio

เกราะ แกนกลาง ปีกและอาวุธแยก `group` ใน canonical mesh เพื่อให้ปรับรายละเอียดและแตกชิ้น animate ภายหลังได้ Wing silhouette เป็นส่วนตกแต่ง; แนะนำ hitbox body กว้าง 1.0 บล็อก สูง 3.2 บล็อก ไม่ใช้กรอบ 7×7 ของ renderer เป็น hitbox ผู้เล่น

## 3. Core Stats — ค่าตั้งต้นสำหรับ playtest

ใช้ virtual HP เช่นเดียวกับ Judge ของโปรเจกต์ ค่า Judge ปัจจุบันคือ core 10,000 และ multiplier ต่อผู้เล่นเพิ่ม 0.65 จาก [config](../evergarden/src/main/resources/config.yml) งานใหม่นี้เสนอ HP สูงขึ้น แต่ไม่มีการแก้ config หรือ HP ของ Judge

| Stat | Seraphon | Arkhaia |
| --- | --- | --- |
| Base virtual HP เมื่อเล่นคนเดียว | 16,000 | 18,000 |
| HP สำหรับ 4 คน | 47,200 | 53,100 |
| HP สำหรับ 8 คน | 88,800 | 99,900 |
| HP สำหรับ 16 คน | 172,000 | 193,500 |
| จำนวนผู้เล่น | 1–16; เป้าหมาย raid 4–8 | 1–16; เป้าหมาย raid 4–8 |
| Weakness | Darkness +25% damage | Light +25% damage |
| Resistance | Light 50%; Physical 15%; Fire 10% | Darkness 50%; Physical 15%; Fire 30% |
| ธาตุอื่นและความเสียหายไม่ระบุธาตุ | 0% resistance | 0% resistance |
| Exposed core | +35% damage, 10 วินาที | +35% damage, 10 วินาที |
| CC resistance | stun/root/pull ทั่วไป 100%; mechanic stun ทำงาน | stun/root/pull ทั่วไป 100%; mechanic stun ทำงาน |
| ระยะ arena ที่เสนอ | วงกลมรัศมี 18 บล็อก | วงกลมรัศมี 18 บล็อก |
| Phase | P1: 100–70%, P2: ≤70–35%, P3: ≤35% | P1: 100–70%, P2: ≤70–35%, P3: ≤35% |

`HP = base × [1 + 0.65 × (N − 1)]`, จำกัด N ที่ 1–16 ล็อก N จากผู้เข้าร่วมตอนเริ่ม หลังเริ่ม HP ไม่กระโดดขึ้นลงเมื่อมีคนออก/เข้าใหม่

สูตร incoming damage ที่เสนอคือ `raw damage × (1 − resistance) × (1 + weakness) × (1 + core bonus)` ทุก hit มี elemental tag หลักเพียงหนึ่งค่า ไม่รวม vulnerability หลายธาตุจากชื่อสกิล ตัวอย่าง Darkness 100 ใส่ Seraphon ที่ core เปิด: `100 × 1 × 1.25 × 1.35 = 168.75` ส่วน Physical 100 ที่ core ปิด: `100 × 0.85 = 85` ไม่เพิ่ม hard cap ต่อ hit

Light/Darkness เป็น combat tag ที่ต้องเพิ่มและ map กับ spell ในขั้น implement; โค้ดต้นแบบโมเดลยังไม่ได้ตรวจธาตุหรือสร้าง resistance ความเสียหาย outgoing ในตารางใช้ % ของ max HP เป้าหมาย **ก่อน** resistance/absorption ของผู้เล่น ให้ยังใช้ Totem/Phoenix และระบบป้องกันที่เกี่ยวข้องตามปกติ

## 4. Skill Matrix

### Seraphon

| Name | Type / Phase | Effect / Debuff | Telegraph / Counterplay |
| --- | --- | --- | --- |
| **Edict Thrust** | Front cone / All | 24% max HP เป็น Light; Radiant Brand 1 stack | cone 60° ระยะ 7 บล็อก; cast 1.4s; หลบด้านข้าง; recovery 2.2s; cooldown 6s |
| **Raphion's Spearfall** | Marked ground / All | หอกลง 3 จุด รอบจุดรัศมี 2.5; 30% max HP; Brand 1 stack | วงแสงและเส้นขอบหยุดตามเป้าหมายเมื่อเหลือ 1.5s; cast รวม 2.5s; เดินออกวง; cooldown 14s |
| **Wing of Censure** | Sweeping lanes / P2–P3 | แนวโจมตีจากปีก 2 แถบ กว้าง 3 ยาว 18; 28% max HP และผลัก 2 บล็อก | cast 2.2s; อ่านแนวขนปีกและช่องกลางกว้าง 4 บล็อก; ไม่มี ground cast ซ้อน; cooldown 18s |
| **Golden Covenant** | Shield + objective / P2–P3 | shield เท่ากับ 10% HP สูงสุด; โผล่ตรา 3 อัน; shield หมดเองเมื่อครบ 12s | ทำลายตรา virtual HP 250 ต่ออันเพื่อยกเลิก shield และเปิด core 10s; ไม่มีการฮีล HP บอส; cooldown 40s |
| **Weight of Devotion** | Debuff / P2–P3 | ทำเครื่องหมายผู้เล่นจำนวน `ceil(N/4)`; หลัง 5s ระเบิดรัศมี 3; ผู้ถือโดน 15%, คนอื่นในวงโดน 30% max HP | วงส่วนตัว+icon รูปตราชัดเจน; แยกกันได้ ไม่ต้องใช้บทบาท healer; cooldown 24s |
| **Prismatic Procession** | Rotating beam / P3 | ลำแสงกว้าง 2 บล็อกหมุน 180° ใน 6s; 18% max HP ต่อ hit; cooldown hit 1s/คน | cast 2.5s; แสดงทิศลูกศร; ความเร็ว 30°/s; วิ่งตามด้านหลัง beam; หยุดสกิล ground อื่น; cooldown 22s |
| **Absolute Verdict** | Ritual ultimate / All | ช่องรัศมี 3 จำนวน 3 ช่องเป็นที่หลบ; เมื่อ cast จบ คนอยู่นอกช่องโดน 80% max HP และ Brand 2 stack | cast 8s; ตราพิธีย้ายออกจากพื้นที่อันตราย; ทำลาย judgment seal HP 750 เพื่อ interrupt, stun 4s และเปิด core 10s; cooldown 55s |
| **Shattered Halo** | Phase transition / ที่ 70%, 35% | ล้าง Brand และ shield; halo เปลี่ยน pattern; หลัง 3s คลื่นวงแหวน hit 25% max HP | แสดงวงปลอดภัยด้านในรัศมี 4 ก่อนระเบิดวง 4–12 บล็อก; cancel pending casts; recovery 3s |

**Radiant Brand:** รับ Light damage เพิ่ม 8% ต่อ stack สูงสุด 3 stack (24%); อายุ 10s รีเฟรชเมื่อโดนสกิล ล้างได้ในช่องหลบ Absolute Verdict เมื่อจบพิธีสำเร็จ ความเสียหายจาก Brand ใช้ factor `1 + 0.08 × stacks` ไม่คูณ 1.08 ซ้ำทีละ stack

### Arkhaia

| Name | Type / Phase | Effect / Debuff | Telegraph / Counterplay |
| --- | --- | --- | --- |
| **Thanasius Cleave** | Front cone / All | 26% max HP เป็น Physical; Soulburn 1 stack | cone 90° ระยะ 7; cast 1.6s; ดาบและ cone มี cue ตรงกัน; recovery 2.4s; cooldown 6s |
| **Ghostflame Wake** | Traveling lanes / All | แนวเปลวไฟ 3 แถบ กว้าง 2.5 ยาว 18; 28% max HP และ Soulburn 1 stack | cast 2.5s; เว้นช่องหลบกว้าง 3 ระหว่างแนว; lane ดับหลัง 5s; cooldown 14s |
| **Riftwing Descent** | Landing AoE / P2–P3 | รัศมี 5; 35% max HP และผลัก 2 บล็อก | cast 3s; lock จุดตกตั้งแต่เริ่ม; landing เงาและวงขอบตรงกัน; recovery 3s; cooldown 20s |
| **Soul Tether** | Personal tether / P2–P3 | mark ผู้เล่น `ceil(N/4)` คน ผูกกับ obelisk ใกล้ตัว 5s; ไม่ตัดก่อนจบโดน 35% max HP | เดินห่าง obelisk เกิน 7 บล็อก หรือทำลาย virtual HP 250; arena ไม่มีหลุมบังคับ; cooldown 24s |
| **Obsidian Dominion** | Shield + objective / P2–P3 | obelisk 3 ต้นให้ shield เท่ากับ 10% HP สูงสุด; shield หมดหลัง 12s | ทำลายครบ 3 ต้น HP 250/ต้นเพื่อยกเลิก shield และเปิด core 10s; cooldown 40s |
| **Eclipse Corridor** | Sequential lanes / P3 | ฟัน 2 แนวสลับกัน; 30% max HP ต่อแนว; 1.5s ระหว่างแต่ละแนว | cast 2.5s; ลูกศร/เลขลำดับ 1–2; แนวที่สองไม่ทับช่องหนีที่บังคับจากแนวแรก; cooldown 22s |
| **Thanasius Ascendant** | Ritual ultimate / All | ถ้าร่ายสำเร็จ ทุกคนโดน 45% max HP และวง ghostfire ขยาย 6→14 บล็อก; วงที่ขยาย hit อีก 25% | cast 10s; ทำลาย obelisk 3 ต้น HP 250/ต้นเพื่อ interrupt, stun 4s และเปิด core 10s; ทำไม่ทันยังหลบวงที่สองได้; cooldown 55s |
| **Ruptured Sovereign** | Phase transition / ที่ 70%, 35% | ล้าง Soulburn และ shield; ปีกเผยรอยแตกเพิ่ม; คลื่นรัศมีวง 4–12 hit 25% max HP หลัง 3s | cancel pending casts; มีช่องปลอดภัยด้านในรัศมี 4; recovery 3s |

**Soulburn:** ลด healing received 15% ต่อ stack สูงสุด 3 (45%); อายุ 8s; ghostfire tick 4% max HP ต่อวินาที เป็นเวลา 4s ต่อ application และไม่เพิ่มชุด DoT ตามจำนวน stack การเข้าพื้นที่ ritual ที่ชำระแล้ว 1s ล้าง debuff ทั้งหมด กลไก healing reduction ต้องทำ hook กับระบบฮีลจริงก่อนเปิดใช้งาน

### Encounter rules ที่ต้องรักษาเมื่อ implement

- บอสสองตัวเป็น encounter แยกกัน รอบนี้ไม่มีข้อกำหนดให้สู้พร้อมกัน
- คิวโจมตีอันตรายทำงานทีละสกิล; ultimate สำคัญที่สุด ตามด้วย transition แล้วค่อยสกิลทั่วไป cooldown เริ่มหลัง recovery ไม่ใช่ระหว่าง telegraph
- เมื่อเข้า phase ใหม่ ยกเลิก active attack และเคลียร์พื้นอันตรายก่อน transition ถ้า burst ข้ามหลาย threshold ให้เปลี่ยนไป phase ปลายทางครั้งเดียว ไม่เรียก transition ซ้อน
- ultimate ปรากฏครั้งแรกที่ 30s หลังเริ่ม; หลังจากนั้นรอ cooldown 55s และให้ recovery 3s ก่อนเริ่มท่าอื่น การเลื่อน ultimate ต้องไม่ตัด telegraph ที่เริ่มไปแล้ว
- ตรา/obelisk 3 จุดวางห่างศูนย์กลาง 6 บล็อก เพื่อให้ผู้เล่นเดี่ยวเดินถึงในเวลา cast ไม่เพิ่มจำนวน objective ตามผู้เล่น; ตรวจ solo clear ใน playtest
- core เป็น vulnerability ของ body hitbox เดิม ไม่บังคับยิงอัญมณีขนาดเล็ก ถ้า trigger ซ้ำระหว่างเปิด ให้เริ่มระยะ 10s ใหม่ ไม่บวกเวลาไปเรื่อย ๆ
- shield รับ damage ตามสูตรเดียวกับ HP; damage ที่เกิน shield ส่งเข้า HP โดยไม่สูญหาย core-open bonus เริ่มเมื่อ shield หายแล้ว
- ground indicator แสดง shape/icon และเสียงร่วมกับสี gold/cyan หรือ violet/white ให้ Java และ Bedrock อ่านวงเวลาเดียวกัน
- ท่า wide cone/beam ต้องหันตามทิศที่ lock ตอน telegraph ไม่หมุนตามผู้เล่นในวินาทีสุดท้าย
- ยังต้องทดสอบกับชุดอุปกรณ์จริง: clear time เป้าหมาย 5–8 นาทีสำหรับ 4–8 คน, ช่องหนีในช่วง lag และ 1 คน, accessibility ของ mark/beam, การฮีล/ช่วยชีวิต, logout/expiry/cleanup ทั้งสอง client

## 5. ไฟล์ส่งมอบและวิธีสร้างซ้ำ

- [ภาพรวมโมเดล idle/cast](../previews/faction-bosses/seraphon-arkhaia-models.png)
- [ภาพฉบับแก้ v2](../previews/faction-bosses/seraphon-arkhaia-v2.png), [ตรวจใบหน้า 8 มุมรอบตัว](../previews/faction-bosses/head-review-v2.png)
- [Seraphon idle](../previews/faction-bosses/seraphon-idle.png), [cast](../previews/faction-bosses/seraphon-cast.png), [front](../previews/faction-bosses/seraphon-front.png), [side](../previews/faction-bosses/seraphon-side.png), [back](../previews/faction-bosses/seraphon-back.png)
- [Arkhaia idle](../previews/faction-bosses/arkhaia-idle.png), [cast](../previews/faction-bosses/arkhaia-cast.png), [front](../previews/faction-bosses/arkhaia-front.png), [side](../previews/faction-bosses/arkhaia-side.png), [back](../previews/faction-bosses/arkhaia-back.png)
- [Canonical meshes พร้อมชื่อ group](../output/faction-bosses/canonical-meshes.json)
- [Java model pack](../output/faction-bosses/faction-boss-java.zip), [Bedrock model pack](../output/faction-bosses/faction-boss-bedrock.mcpack), [Geyser mapping additions](../output/faction-bosses/geyser-mappings.json)
- [จำนวน geometry และ hash](../output/faction-bosses/model-manifest.json), [validation report](../output/faction-bosses/validation-report.json)
- [ตัวสร้าง asset](../evergarden/tools/faction_boss_assets.py), [ตัวสร้าง preview](../evergarden/tools/preview_faction_bosses.py), [ตัวตรวจ asset](../evergarden/tests/check_faction_boss_assets.py)

```powershell
python evergarden/tools/faction_boss_assets.py
python evergarden/tests/check_faction_boss_assets.py
python evergarden/tools/preview_faction_bosses.py
```

ต้องมี Pillow; preview ต้องมี NumPy เพิ่มด้วย ขั้นสร้าง asset และตรวจ pack ใช้ Pillow กับ standard library ไม่มีการดาวน์โหลด dependency อัตโนมัติ ไฟล์ทั้งหมดออกไปยัง `output/faction-bosses/` และ `previews/faction-bosses/`

## 6. พรีวิวในเกมและ build ที่รวมแล้ว

ใช้ [evergarden.jar](../dist/evergarden.jar) แทนไฟล์เดิมใน `plugins/` แล้วรีสตาร์ตเซิร์ฟเวอร์ เวอร์ชันใหม่คือ `3.0.0-e2.47-faction-model-previews` แพ็ก Java addon, Bedrock, Geyser mappings และ hashes ใหม่อยู่ใน JAR แล้ว หากใช้ Geyser แยกเครื่อง ให้ใช้ [Bedrock pack](../evergarden/dist/evergarden-bedrock.mcpack) และ [mappings ฉบับรวม](../evergarden/dist/geyser-mappings.json)

แอดมินใช้ `/evergarden test` → **World Boss Test**:

- **เสกโมเดล Seraphon** (ดาบทอง ช่อง 0): พรีวิวด้านหน้า 6 บล็อก ฝั่งซ้าย 3 บล็อก
- **เสกโมเดล Arkhaia** (ดาบเนเธอไรต์ ช่อง 2): พรีวิวด้านหน้า 6 บล็อก ฝั่งขวา 3 บล็อก
- **ลบโมเดล Seraphon / Arkhaia** (กรรไกร ช่อง 6): ลบเฉพาะพรีวิวสองตัวที่แอดมินคนนั้นเสกไว้

ทั้งสองอยู่พร้อมกันได้ คนละหนึ่งตัวต่อแอดมิน กดเสกซ้ำแทนที่เฉพาะตัวเดิม สลับ idle/cast ทุก 5 วินาที และเก็บกวาดเมื่อครบ 10 นาที ออกจากเกม หรือปิดปลั๊กอิน Java ต้องรับแพ็ก World Boss ให้เสร็จก่อนเสก หากยังไม่เห็นโมเดลใช้ `/evergarden boss pack resend` แล้วกดเสกอีกครั้ง

ตัวพรีวิวไม่มี AI ต่อสู้ ไม่เริ่มไฟต์ ไม่แจก loot และไม่เปลี่ยน game mode โมเดล Seraph ฝั่งดีและปุ่มพรีวิวเดิมยังใช้งานได้ การตรวจ lifecycle/menu ใช้ [Paper probe](../tests/faction-preview/FactionPreviewProbe.java) ในเซิร์ฟเวอร์ทดสอบแยก และ item mappings ตรวจด้วย Geyser บน protocol tables ห้าชุด
