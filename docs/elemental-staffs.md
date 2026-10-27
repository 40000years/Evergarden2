# คทาธาตุ — โมเดลรุ่นแรก

รุ่นล่าสุด: **1.12.1-bedrock-staff-vfx** แก้เอฟเฟกต์ Bedrock บังจอด้วยประกาย
3D ขนาดเล็กรอบหัวคทา โดยคงแพ็ก Java เดิมทุกไบต์ ดู [รายละเอียด hotfix](bedrock-staff-vfx.md)

วันที่ 17 ตุลาคม 2026 · Advance Magic `1.12.0-elemental-staff-models`

คทาร่ายเวทชุดเดิมถูกแทนที่ด้วยคทาแปดธาตุ ในรอบนี้ทำโมเดล ตำแหน่งถือ
ไอคอน และเอฟเฟกต์ก่อน ระบบจัดชุดเวทหลายสกิลยังไม่เปิดใช้งาน คทาใหม่จึงไม่ร่าย
เวทเดิม ไม่เสียมานา และไม่สร้างดาเมจจากการแตะหรือคลิก

วงจรธาตุปกติ: **Fire → Wind → Earth → Ice → Water → Fire**

วงจรธาตุพิเศษ: **Lightning → Light → Dark → Lightning**

วงจรถูกบันทึกใน catalog แล้ว ยังไม่มีตัวคูณแพ้ชนะหรือระบบเปลี่ยนธาตุอัตโนมัติ

| ธาตุ | แนวทางรูปร่าง | เอฟเฟกต์ประจำตัว |
| --- | --- | --- |
| Fire | กรงทองและผลึกแดง พร้อมส่วนโค้งคล้ายเขาไฟ | เปลวไฟและสะเก็ดถ่าน |
| Wind | โลหะงาช้างและเกลียวลมล้อมผลึกสีมิ้นต์ | กระแสลมและประกายสีเขียวอ่อน |
| Earth | หิน มอส และเถาวัลย์ล้อมผลึกเขียว | เศษหินและละอองธรรมชาติ |
| Ice | มงกุฎผลึกและกิ่งน้ำแข็งทรงเกล็ดหิมะ | ประกายน้ำแข็ง |
| Water | เกลียวคลื่นสีฟ้าล้อมแกนกลาง | สายน้ำและหยดน้ำ |
| Lightning | โครงตัวนำแยกแฉกและผลึกไฟฟ้า | สายฟ้ากระโดดระหว่างส่วนหัว |
| Light | วงรัศมีและโครงทองทรงสุริยะ | รัศมีและประกายศักดิ์สิทธิ์ |
| Dark | จันทร์เสี้ยวออบซิเดียนและผลึกม่วง | หมอกและสะเก็ดพลังเงา |

[ภาพรวม](../previews/elemental-staffs/elemental-staffs.png) ·
[ภาพเคลื่อนไหว](../previews/elemental-staffs/elemental-staffs.gif) ·
[แกลเลอรี](../previews/elemental-staffs/index.html)

ภาพเป็นการเรนเดอร์จากกล่องและเท็กซ์เจอร์ของ asset พร้อมแสงประกอบ
ต้องตรวจแสงจริง ท่ามือ และกรอบมุมมองบนไคลเอนต์ Java/Bedrock อีกครั้ง

## ทดลองในเกม

- `/magic staffs` เปิดเมนูคทาแปดธาตุ ผู้เล่นทั่วไปดูได้ แอดมินรับของได้
- `/magic staff give all` รับครบแปดธาตุ (แอดมิน)
- `/magic staff give fire` รับธาตุที่ระบุ (แอดมิน)
- `/magic pack resend` ขอแพ็กใหม่หลังอัปเดต

ชื่อธาตุที่ใช้ได้: `fire`, `wind`, `earth`, `ice`, `water`, `lightning`, `light`, `dark`

## การส่งออกโมเดล

ใช้แนวทางเดียวกับ Excalibur / Mjolnir: รูปทรงสามมิติชุดเดียวส่งออกเป็น
Java item model, Bedrock attachable และ Blockbench `.bbmodel` ที่ฝัง texture แล้ว
ไฟล์ ModelEngine ใช้ ID `elemental_staff_<element>` และคลิป `idle`
ตรวจการนำเข้ากับ ModelEngine **R4.1.1** จริง

ขณะถือใช้ native item ของแต่ละ Edition เช่นเดียวกับอาวุธตำนานเดิม
แยกจุดจับกับขนาดมุมมองแรกและมุมมองบุคคลที่สาม เอฟเฟกต์บนไอเทมใช้
animation ของแพ็ก โดยไม่สร้าง entity ติดตามมือทุกเฟรม ส่วน `.bbmodel`
ใช้สำหรับนำเข้า ModelEngine และแก้ไขต้นฉบับต่อได้

ไฟล์ต้นฉบับ: `advance-magic/art/elemental-staffs/`

ชุดต้นฉบับ: `advance-magic/dist/elemental-staff-sources.zip`

สร้างใหม่:

```powershell
python tools/elemental_staff_assets.py
python advance-magic/tools/build_packs.py
python tools/elemental_staff_preview.py
```

## คทาเก่าและข้อมูลที่เก็บไว้

คทาเก่าที่รู้จักจะเปลี่ยนรูปเป็นธาตุใหม่เมื่อระบบพบใน inventory หรือขณะถือ
ข้อมูลระบุเวทเดิม อัปเกรด mastery และข้อมูลผู้เล่นยังเก็บไว้สำหรับการย้ายระบบรอบต่อไป
เวทภายในและ API ที่ส่วนอื่นอาจเรียกใช้ยังอยู่ แต่การใช้ไอเทมคทาเดิมไม่ร่ายเวทเหล่านั้น

| ธาตุใหม่ | คทาเดิม |
| --- | --- |
| Fire | Dragon's Breath, Blaze Barrage, Meteor Strike |
| Wind | Sonic Boom, Shulker Levitation |
| Earth | Nature's Bloom, Earth Wall, Iron Armor, Verdant Sovereign |
| Ice | Frost Nova, Chronos Final Hour |
| Water | Guardian Beam |
| Lightning | Lightning Strike |
| Light | Solar Apocalypse, Heaven's Judgment |
| Dark | Shadow Step, Void Pull, Wither Ray, Vex Legion, Lunar Requiem, Crimson Oblivion |

เก็บ texture คทา Mythic/ตำนานทั้งหกชุด รวมถึง core และเอฟเฟกต์ที่ใช้ร่วมกัน
เพื่อรองรับจุดอ้างอิงเดิม อาวุธ Excalibur / Longinus / Tyrfing / Mjolnir / Vajra,
ไม้เท้าบิน และอุปกรณ์ซ่อมยังเป็นอุปกรณ์แยกจากคทาร่ายเวทธาตุ
ดูรายการ cleanup และเหตุผลการเก็บไฟล์ใน [รายงาน cleanup](elemental-staff-cleanup.md)

## ผลตรวจ

รุ่นที่ส่งมอบผ่านการตรวจ:

- 4,445 ข้อสำหรับรูปทรง texture ไฟล์ส่งออก แอนิเมชัน และจุดจับ
- 64 ข้อสำหรับกรอบมุมมอง Bedrock โดยใช้ค่าฉายประมาณจากภาพ client เดิม
- 246 ข้อบน Paper ร่วมกับ Evergarden สำหรับข้อมูลไอเทม เมนู คำสั่ง และการถอดระบบเก่า
- 148 ข้อบน Geyser และ ModelEngine R4.1.1 จริง รวมการนำเข้า สร้างโมเดล และเล่น `idle` ครบแปดธาตุ
- 236 ข้อสำหรับ asset อาวุธตำนานเดิม รวมถึงการตรวจแพ็กและไฟล์ที่ต้องเก็บไว้

กรอบมุมมองแรกของ Java เห็นหัวคทาและจุดจับ ส่วนปลายด้ามล่างอาจพ้นจอตามลักษณะ
คทายาว กรอบของ Bedrock ถูกจัดให้เห็นหัวและด้ามทั้งหมดในค่าฉายอ้างอิง
ผลตรวจนี้ไม่ครอบคลุม FOV, hand bob, แสง หรือการเรนเดอร์จริงบน client

ผลตรวจ asset, runtime และ ModelEngine/Geyser ของไฟล์ที่ส่งมอบบันทึกไว้ใน
`dist/elemental-staffs-manifest.json` เป็นการตรวจใน workspace และเซิร์ฟเวอร์ทดสอบแยก
ยังไม่ได้ติดตั้งลงเซิร์ฟเวอร์ที่ผู้เล่นใช้งาน
