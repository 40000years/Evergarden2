# ตรวจแพ็ก Bedrock: น้ำยาเดินเวหา

รุ่นแก้ไข: Evergarden `3.0.0-e2.11-bedrock-elixir`, Bedrock pack `3.8.2` ใช้ร่วมกับ Advance Magic `1.2.15` เดิม

## สาเหตุและการแก้

น้ำยาเดินเวหาที่ `RelicService` สร้างมี `item_model=voidscape:void_elixir` แต่ mapping รุ่นก่อนใช้ `model=minecraft:honey_bottle` พร้อม predicate ของ custom model data Geyser v2 เลือกกลุ่มจาก item model ก่อนประเมิน predicate จึงไม่พบกลุ่มของน้ำยาและใช้ขวดธรรมดา ภาพ PNG มีอยู่ในแพ็ก ไม่ได้ขาดไฟล์ภาพ

หน้ากากและมงกุฎของ Guardian ทั้ง 6 แบบมีปัญหาเดียวกัน: `GuardianAppearance.mask` ตั้งโมเดลตรง `voidscape:<ชื่อ>` แต่ mapping ชี้ไปที่ `minecraft:carved_pumpkin` แก้ทั้ง 7 รายการเป็น direct model โดยคงรหัส Bedrock, carrier item และไอคอนเดิม ไม่เปลี่ยน mapping ของขวดน้ำผึ้งธรรมดาหรือ Aeternum

เมื่อสร้างแพ็กจากต้นฉบับล่าสุด พบความต่างที่ยังไม่ได้รวมในแพ็กแจก: ไอคอนไม้เท้าบินจาก Advance Magic และโมเดลแท่นฟื้นฟูทั้ง 3 แบบ รวมไฟล์เหล่านี้แล้ว คง UUID ของแพ็กและเพิ่มเวอร์ชัน Bedrock เป็น `3.8.2` เพื่อให้ client โหลดแพ็กใหม่

Java ZIP เปลี่ยนเฉพาะนิยามและ geometry ของแท่นฟื้นฟู 6 ไฟล์ อัปเดต URL GitHub ทางการและ SHA-1 ให้ตรง ZIP โดยระบบคง URL ที่แอดมินกำหนดเองไว้

## ผลตรวจ

- แพ็กก่อนแก้บน Paper + Geyser ที่ติดตั้งจริง: การเลือกน้ำยา `voidscape:void_elixir` คืนค่า vanilla ทำซ้ำอาการได้
- หลังแก้: ไอเทม Evergarden และ Advance Magic ครบ 214 รหัสเลือกได้ถูกต้องผ่านตัวเลือกจริงของ Geyser ในตาราง Bedrock 3 รุ่น ตรวจไอเทมจาก factory ของ Relic, คทา, Core, เมล็ด, อาหาร, พืชทุกระยะ, หน้ากาก/มงกุฎ, ไม้เท้า, Core อัปเกรดและไอเทมซ่อม รวมถึง display ของพอร์ทัลและแท่น
- น้ำยาเก่าที่ล้าง item model ไว้ถูก migrate กลับโดยจำนวนไอเทมไม่เปลี่ยน ขวดน้ำผึ้งธรรมดาไม่ถูกเลือกเป็นน้ำยา
- ตรวจทุก atlas texture และไฟล์อ้างอิง geometry, texture, render controller และ animation ในแพ็ก Evergarden / Advance Magic ไม่พบไฟล์ตกหล่น
- ตรวจ Java fallback, PNG, JSON, wearable, รหัส mapping ไม่ซ้ำระหว่างปลั๊กอิน, SHA-1 และไฟล์ที่ฝังใน JAR ผ่าน
- ตรวจแพ็ก Aeternum: Java SHA-1, crop model 12 แบบ, Bedrock item 11 แบบ, crop block state 16 แบบ และการซ้อนแพ็กผ่าน ไม่แก้ไฟล์หรือระบบฤดูกาล

เซิร์ฟเวอร์ทดสอบแยกอยู่ใน `.audit-plugins/bedrock-items-*` ไม่รีสตาร์ตหรือเขียนทับไฟล์บนเซิร์ฟเวอร์ที่ใช้งานจริง Context ของตัวเลือก Geyser ไม่มี client เชื่อมต่อ ใช้สำหรับ predicate ของ custom model data เท่านั้น จึงยืนยันเส้นทางเลือกไอเทมและไฟล์แพ็ก แต่ยังไม่ได้ยืนยันภาพบนจอ Bedrock PC / มือถือ

## ติดตั้ง

เปลี่ยน `evergarden.jar` แล้วรีสตาร์ตเซิร์ฟเวอร์ Geyser-Spigot เครื่องเดียวกันจะได้รับแพ็กและ mapping จาก JAR ก่อนเริ่ม ผู้เล่น Bedrock ออกเข้าใหม่และรับแพ็ก `3.8.2` ได้ ใช้ `/evergarden pack` ดูเวอร์ชันและตำแหน่งไฟล์

หาก Geyser แยกเครื่อง ให้ใช้ `evergarden/dist/evergarden-bedrock.mcpack` กับ `evergarden/dist/geyser-mappings.json` รุ่นเดียวกัน คัดลอกเป็น `packs/voidscape-bedrock.mcpack` และ `custom_mappings/voidscape.json` ของ Geyser แล้วรีสตาร์ต Geyser ชื่อ `voidscape` ในปลายทางเป็นชื่อที่ตัวติดตั้งใช้อยู่ ส่วนไฟล์ legacy `evergarden/dist/voidscape-*` ไม่ใช่แพ็กที่รุ่นปัจจุบันฝังและแจก

## รันทดสอบซ้ำ

```powershell
python evergarden/tests/check_packs.py
python evergarden/tests/run_bedrock_items.py --source-server C:/path/to/Paper-server
```

ตัวรันทดสอบอ่าน Paper, libraries, Geyser และ Aeternum จาก source server แต่เขียนและเปิดเซิร์ฟเวอร์เฉพาะในพื้นที่ทดสอบแยก ใช้พอร์ต TCP/UDP ว่าง
