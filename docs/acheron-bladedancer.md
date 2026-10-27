# รีดีไซน์ Acheron: The Weeping Bladedancer

แพ็ก Evergarden Bedrock **3.51.0** ใน `dist/evergarden.jar` (รุ่น `3.0.0-e2.121-boss-hitbox-fix` ที่อีกงานสร้างไว้)
เปลี่ยนเฉพาะ asset ของ Acheron ไม่ได้แตะ class ใด ๆ

[ก่อน/หลัง](../previews/acheron-bladedancer/before-after.png) ·
[รอบตัว](../previews/acheron-bladedancer/turnaround.png) ·
[ท่าต่อสู้](../previews/acheron-bladedancer/actions.png) ·
[การแยกร่าง](../previews/acheron-bladedancer/split.png)

ภาพทั้งหมดเรนเดอร์จาก blueprint และคีย์แอนิเมชันที่ส่งออกจริง ไม่ใช่ภาพจับหน้าจอเกม

## ดีไซน์

แทน "crimson-v3" (ตัวบล็อกหนา ขาเป็นแท่ง เขาตั้งเหมือนเสาอากาศ) ด้วยนักดาบคู่ร่างเพรียว ยังคงโทนแดงเลือดและเอกลักษณ์เดิม:
หน้ากากร้องไห้ น้ำตาเลือดเรือง ซี่โครง ดาบโค้งคู่ (Sorrow-Quills) และแกนวิญญาณทับทิมกลางอก

- หัวเล็กลง (0.86) ให้สัดส่วนสูงโปร่ง หน้ากากกระเบื้องขาวสลักหน้าเศร้าแบบโนห์ ใต้ฮู้ดแดงปากงุ้มที่สอบเข้าด้านบน
- เขาทับทิมโผล่จากขมับ กวาดขึ้นไปด้านหลัง มีเส้นเรืองตามแนวเขา และหางฮู้ดปลิวไปด้านหลัง
- ลำตัวทรง V (ไหล่กว้าง เอวคอด) เกราะอกซ้อนชั้น ซี่โครงแดงมีแสงเรืองใต้แต่ละซี่ ครีบหลัง
- ผ้าพันคอสองชาย ชายเสื้อคลุมผ่าสองแฉกปลายขาดเป็นแสง ผ้าปิดต้นขาสองผืน ทั้งหมดเป็นผ้าที่ปลิวตามแรงโน้มถ่วง ลม และการหมุน
- ดาบโค้งยาว ใบทับทิมตัดเป็นรูปทรงจริง (alpha-cut) ร่องกลางเรืองแสง การ์ดพระจันทร์ทอง

237 cube, 38 bone (ชื่อ bone และลำดับคลิปเหมือนเดิมทุกตัว ตามสัญญาของ RiverVisual, BossHitbox, Geyser และ probe)

## การเคลื่อนไหว

ทั้ง 13 คลิปทำใหม่ด้วย IK: เท้าวางติดพื้นจริง (ตรวจทุกมุมของรองเท้าเหนือพื้น) มือจับดาบตามตำแหน่งด้าม ทิศใบ และทิศคม
คลิปสั้นและยาวเท่าเดิม จังหวะต่อสู้ (contact) ไม่เปลี่ยน

| คลิป | ท่า |
| --- | --- |
| idle | ย่อเข่ากว้าง สะโพกบิด อกตรง ดาบขวาชี้หน้า ดาบซ้ายลากหลัง ปลายดาบวนช้า ๆ |
| walk | ย่อต่ำ เอนไปหน้า ดาบสองเล่มลากไปข้างหลังเหมือนปีก (ก้าวสัมพันธ์กับความเร็ว 0.12 บล็อก/tick) |
| hover | เด้งบนปลายเท้า ดาบขวายกขึ้น |
| cast (หมุน) | ม้วนตัวสวนทิศ → หมุน 3 รอบใน 1.2 วินาที (45°/tick) ลอยจากพื้นเล็กน้อย แขนกางสุด |
| attack | ยกดาบสูงหลังศีรษะ → พุ่งฟันเฉียง (โดนที่ 0.2 วินาที) → ดาบซ้ายกวาดตาม → ควงดาบกลับท่า |
| stun | สะดุ้งถอย แล้วคุกเข่าข้างเดียว ปักดาบสองเล่มลงพื้นพยุงตัว |
| ritual (แยกร่าง/ผนึก) | ไขว้ดาบเป็นรูป X หน้าหน้ากาก สั่น → ฉีกดาบออกสองข้าง เงยหน้าร้อง |

- ดาบไม่ทะลุลำตัว สะโพก หัว หรือหน้ากากในทุกคลิป แขนและกำปั้นไม่ตัดผ่านลำตัว (ตรวจ 99,800 จุด)
- มุม Euler ต่อเนื่องระหว่างคีย์ (ไม่หมุนอ้อมทางไกล 300° ระหว่างเฟรม) และคีย์การหมุนตัวถูกแบ่งทุก tick เพราะ ModelEngine เบลนด์การหมุนแบบทางสั้นสุด
- ตอน idle t=0 อกตั้งตรงพอดี เพื่อให้ gaze bone หันหน้ากากไปที่ผู้เล่นได้ตรง (เงื่อนไขเดิมของบอสแม่น้ำ)
- ท่าหมุน: ด้ามดาบห่างแกนกลาง ~4.3 บล็อก สูง ~6.9 บล็อก (เดิม 4.9 / 6.4) เส้น quill จึงสั้นลงราว 0.6 บล็อก

## การแยกร่าง (echo)

สิ่งที่พบ: blueprint `arkhaia_acheron_echo` ของ crimson-v3 ยังมี bone `halo` ทั้งที่สัญญากำหนดว่าร่างแยกต้องไม่มี
(`tests/model-engine/check_acheron_split_assets.py` ล้มอยู่) ตอนนี้แก้แล้ว:

- ร่างแยกสร้างจากโมเดลเดียวกันโดยอัตโนมัติ: ขนาด 60% (geometry, pivot, การเคลื่อนที่) ไม่มี halo, UV เหมือนกันทุกหน้า
- เท็กซ์เจอร์ร่างแยกเป็นเงาสีแดงอมม่วง (ส่วนเรืองแสงยังสว่างเท่าเดิม) จึงแยกออกจากตัวจริงได้แต่ยังมองเห็นในฉากมืด
- Bedrock entity `arkhaia_acheron_echo_native` ส่งออกใหม่แล้ว

ฝั่งโค้ด (`ArkhaiaMinions.split()`): ร่างแยก 2 ตัวเกิดห่าง 3-5 บล็อกด้านข้าง พร้อมเสียง `ENTITY_ILLUSIONER_MIRROR_MOVE` อย่างเดียว
ยังไม่มีเอฟเฟกต์ตอนร่างแยกปรากฏ ถ้าจะเพิ่ม (เช่นละอองแดงพุ่งจากตัวจริงไปยังจุดเกิด) ต้องแก้ Java ไฟล์นั้น ซึ่งอีกงานกำลังแก้อยู่ รอบนี้จึงยังไม่ได้แตะ

## ข้อควรรู้เรื่องการตีโดน

BossHitbox ตรวจการตีระยะประชิดจากกล่องของแต่ละ bone ในโมเดล ร่างใหม่ผอมกว่า
พื้นที่ด้านหน้าที่ผู้เล่นตีถึงได้ (ต่ำกว่า ~4.5 บล็อก) ลดจากราว 35 เหลือราว 20 ตารางบล็อก
(ดีไซน์ดั้งเดิมก่อน crimson-v3 อยู่ราว 34) ถ้าในเกมรู้สึกว่าตีพลาดบ่อย ปรับได้ที่ `AIM_PADDING` ใน `BossHitbox.java`

## ติดตั้ง

1. ปิดเซิร์ฟเวอร์ แทนที่ `plugins/evergarden.jar` ด้วย `dist/evergarden.jar` แล้วเปิดใหม่
2. ปลั๊กอินอัปเกรด blueprint ใน `plugins/ModelEngine/blueprints/` ให้เอง (ไฟล์เดิมเป็นของปลั๊กอินตาม hash ที่บันทึกไว้; เครื่อง Serva ในเครื่องตรงกันแล้ว)
   และสำรองไฟล์เก่าไว้ที่ `plugins/Evergarden/modelengine/backups/`
3. ผู้เล่น Bedrock ได้แพ็ก 3.51.0 เมื่อเข้าเกมใหม่ ถ้าใช้ Geyser/CDN แยก ให้คัดลอกแพ็กและ SHA-1 จาก `evergarden/dist/pack-hashes.json`

## สร้างใหม่และตรวจ

```powershell
python evergarden/tools/acheron_bladedancer.py                      # blueprint ตัวจริง + ร่างแยก + manifest
python tools/update_river_bedrock.py arkhaia_acheron arkhaia_acheron_echo --version <เวอร์ชันถัดไป>
python evergarden/tools/acheron_bladedancer_preview.py --before output/acheron-bladedancer-20261023/before/arkhaia_acheron.bbmodel
python tests/model-engine/check_acheron_split_assets.py
python tests/model-engine/check_banshee_wail.py
python tests/model-engine/check_river_choreography.py
```

ถ้าไปรัน `evergarden/tools/build_packs.py` (สร้างแพ็กไอเท็มใหม่ทั้งก้อน) ภายหลัง entity ของบอสแม่น้ำจะหายจากแพ็ก
ต้องรัน `update_river_bedrock.py` ซ้ำด้วยเวอร์ชันที่สูงกว่า

ไม่ควรรัน `evergarden/tools/acheron_crimson.py` (ตัวสร้าง crimson-v3 เดิม) อีก:
มันจะเขียนโมเดลเก่าทับทั้งใน resources และใน `C:/Users/User/Desktop/Serva/plugins/ModelEngine/blueprints/` โดยตรง

ผลตรวจ: แยกร่าง/Bedrock 3,557, Acheron production 99,800, Banshee/แม่น้ำ 30,515, บอสหลัก Evergarden 34,269, Phlegethon 1,860 ผ่านทั้งหมด

ตัวตรวจที่ล้มอยู่ก่อนแล้ว ไม่เกี่ยวกับรอบนี้:
- `check_river_choreography.py` ไปล้มที่ Cocytus ("shaft penetrates finger_l") ไฟล์ Cocytus ไม่ได้ถูกแก้
- `check_river_assets.py` ล็อก geometry ของบอสแม่น้ำทุกตัวไว้กับรุ่น v2 ใน `previews/arkhaia-minions-v2/models/` (crimson-v3 ก็ไม่ผ่านอยู่แล้ว)
- `check_river_body_clearance.py` Cocytus 33 และ Banshee 1,252 จุด (Acheron ตัดออกจากตัวนี้ เพราะตัวนี้ใช้แขนของสตั๊ดดี้ v2 เก่า ไปตรวจแทนใน `check_river_choreography.py` ด้วยชิ้นส่วนจริง)
- `tests/arkhaia/check_boss_orbs.py` ยังคาดหวังแพ็ก Bedrock 3.38.0

ข้อจำกัด: ยังไม่ได้เปิดเกมจริงทั้ง Java และ Bedrock

ต้นฉบับก่อนรีดีไซน์ (blueprint crimson-v3, แพ็ก 3.50.0, `judge-design.json`, JAR e2.121) สำรองไว้ที่ `output/acheron-bladedancer-20261023/before/`
