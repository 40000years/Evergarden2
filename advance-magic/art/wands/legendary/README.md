# คทาระดับตำนาน 05–07 — Advance Magic

เท็กซ์เจอร์ต้นฉบับชุดนี้ถอดแบบคทาสามอันที่เหลือจากภาพแนวคิด แล้วจัดเป็นภาพไอเทม 2D แบบ `minecraft:item/handheld` ตามคทาเดิมของ Advance Magic ภาพเกมจริงเป็น PNG 128×128, RGBA, พื้นหลังโปร่งใส และขอบคม แพ็กนี้เก็บสำหรับตรวจงานศิลป์ก่อนเพิ่มระบบเวทมนตร์

| แบบ | ชื่อไทย | ชื่ออังกฤษ | ธีม | `item_model` |
| --- | --- | --- | --- | --- |
| 05 | ราชันมังกรพฤกษา | Verdant Sovereign | มงกุฎมังกร มรกตเจียระไน และหนามทองโบราณ | `advance_magic:verdant_sovereign` |
| 06 | บทสวดจันทรา | Lunar Requiem | จันทร์เสี้ยวเงิน คริสตัลจันทราสีฟ้า และเครื่องประดับดวงดาว | `advance_magic:lunar_requiem` |
| 07 | หายนะโลหิตทมิฬ | Crimson Oblivion | มงกุฎออบซิเดียนแหลม แกนพลังสีแดง และทองทมิฬ | `advance_magic:crimson_oblivion` |

ไฟล์ภาพสำหรับเกมอยู่ที่ `../verdant_sovereign.png`, `../lunar_requiem.png` และ `../crimson_oblivion.png` โมเดลใช้ไอเทมฐาน `carrot_on_a_stick` เหมือนคทาเดิม และเลือกภาพด้วย `item_model` แพ็กมีคำแปลชื่อไทย/อังกฤษ รหัสทั้งหมดอยู่ใน `catalog.json`

ดู [ภาพรวมเท็กซ์เจอร์](texture-preview.png), [ภาพแบบต้นฉบับ](reference.png) และ [ข้อมูลการสร้างภาพ](generation-prompts.json) ได้ที่นี่ ภาพต้นฉบับความละเอียดเต็มเก็บไว้ใน `generated/` สคริปต์ [prepare_legendary_wand_art.py](../../../tools/prepare_legendary_wand_art.py) ใช้ส่งออกภาพเกม 128×128 อีกครั้งจากภาพเหล่านั้น โดยจัดขนาด canvas และ alpha ให้ตรงรูปแบบคทาเดิม

## ไฟล์แพ็ก

| ไฟล์/โฟลเดอร์ | หน้าที่ |
| --- | --- |
| `java/` | Source ของ Java resource pack; รูปแบบ 75.0–88.0 ตามแพ็กเดิม |
| `bedrock/` | Source ของ Bedrock resource pack; min engine 1.21.80 |
| `geyser-mappings.json` | Geyser mapping v2 เฉพาะคทาสามอันใหม่ |
| `catalog.json` | ชื่อ ธีม รหัสไอเทม แหล่งภาพ และ hash ของเท็กซ์เจอร์ |
| `dist/advance-magic-legendary-java-preview.zip` | Java pack สำหรับทดลองภาพ |
| `dist/advance-magic-legendary-bedrock-preview.mcpack` | Bedrock pack สำหรับทดลองผ่าน Geyser |
| `dist/geyser-mappings.json` | Mapping ที่ใช้คู่กับ Bedrock pack |
| `dist/pack-hashes.json` | SHA-1, SHA-256 และขนาดของไฟล์แพ็ก |

ไม่มีการลงทะเบียนคทาใน `Spell.java` หรือเพิ่มสกิล แพ็ก release ที่ `advance-magic/dist/` และ URL/hash ที่ปลั๊กอินแจกยังใช้ชุดเดิม UUID ของ Bedrock preview แยกจากแพ็กจริง ส่วนชื่อไอเทมที่สร้างจากเซิร์ฟเวอร์อาจใช้ชื่อที่เซิร์ฟเวอร์กำหนดแทนคำแปลใน resource pack

## ทดลองใน Java

คัดลอก `dist/advance-magic-legendary-java-preview.zip` เข้าโฟลเดอร์ `.minecraft/resourcepacks/` ของ client แล้วเปิดแพ็กใน Options → Resource Packs ใช้ client ที่รองรับช่วง resource format ของแพ็กเดิม เช่น Java 26.2 เปิดสิทธิ์คำสั่งในโลกทดสอบ แล้วใช้:

```mcfunction
/give @s minecraft:carrot_on_a_stick[minecraft:item_model="advance_magic:verdant_sovereign"] 1
/give @s minecraft:carrot_on_a_stick[minecraft:item_model="advance_magic:lunar_requiem"] 1
/give @s minecraft:carrot_on_a_stick[minecraft:item_model="advance_magic:crimson_oblivion"] 1
```

คำสั่งข้างบนทดลองรูปลักษณ์อย่างเดียว ชื่อเริ่มต้นยังเป็นชื่อไอเทมฐาน หากต้องการทดลองชื่อแปลตามภาษาของ client ให้ใช้ตัวอย่างนี้:

```mcfunction
/give @s minecraft:carrot_on_a_stick[minecraft:item_model="advance_magic:verdant_sovereign",minecraft:item_name={translate:"item.advance_magic.verdant_sovereign",color:"gold",italic:false}] 1
/give @s minecraft:carrot_on_a_stick[minecraft:item_model="advance_magic:lunar_requiem",minecraft:item_name={translate:"item.advance_magic.lunar_requiem",color:"gold",italic:false}] 1
/give @s minecraft:carrot_on_a_stick[minecraft:item_model="advance_magic:crimson_oblivion",minecraft:item_name={translate:"item.advance_magic.crimson_oblivion",color:"gold",italic:false}] 1
```

## ทดลองใน Bedrock ผ่าน Geyser

ใน Geyser ของเซิร์ฟเวอร์ทดสอบ ให้คัดลอก MCPACK ไปไว้ใน `packs/` และคัดลอก `dist/geyser-mappings.json` ไปเป็นไฟล์ชื่อแยก `custom_mappings/advance-magic-legendary-preview.json` จากนั้น restart Geyser และเชื่อมต่อใหม่เพื่อรับแพ็ก

ไอเทมต้องสร้างจากฝั่ง Java/Paper โดยมี `item_model` ตามตาราง ถ้าใช้ console ให้เปลี่ยน `@s` ในคำสั่ง Java ด้านบนเป็นชื่อผู้เล่น Bedrock ที่เซิร์ฟเวอร์รู้จัก การนำ MCPACK เข้า Bedrock แบบ standalone เพียงอย่างเดียวไม่เพิ่มไอเทม เพราะชุดนี้เป็น resource pack ไม่มี behavior pack และยังไม่มีสกิลหรือคำสั่งรับคทาในปลั๊กอิน

## สร้างแพ็กซ้ำและตรวจไฟล์

รันจากราก repository; ต้องมี Python และ Pillow:

```powershell
python advance-magic/tools/prepare_legendary_wand_art.py
python advance-magic/tools/build_legendary_wand_pack.py
python advance-magic/tools/build_legendary_wand_pack.py --validate-only
```

Builder อ่านภาพต้นฉบับสามอัน แล้วสร้าง source และ archive เฉพาะภายในโฟลเดอร์นี้ ZIP ใช้ลำดับไฟล์และ timestamp คงที่ เมื่อภาพและเครื่องมือเดิมไม่เปลี่ยนจะได้ไฟล์เดิม ตรวจ RGBA 128×128, alpha 0/255, พื้นที่โปร่งใส, ขอบว่าง 3 px, การอ้างอิง texture/model/item/atlas, ชื่อสองภาษา, รหัส Geyser, JSON, archive CRC และ hash

ยังต้องดูรูปลักษณ์ใน client จริงเพื่อยืนยันตำแหน่งขณะถือและอ่าน log การโหลดแพ็ก การตรวจไฟล์อัตโนมัติไม่ได้ยืนยันภาพในเกม
