# ไม้เท้าบิน: ใช้งาน ติดตั้ง และผลทดสอบ

โค้ดอยู่ใน Advance Magic รุ่น `1.2.1-flying-staff` แพ็กทั้งสองฝั่งอยู่ใน JAR และ `advance-magic/dist/` ไฟล์แยกใน `advance-magic/art/flying-staff/dist/` เป็นงาน preview ไม่ใช่แพ็กที่เซิร์ฟเวอร์แจก

## Build บนเครื่องใหม่

ต้องมี Python 3, Java 25+ และ Maven ตามที่ระบุใน README จากโฟลเดอร์ราก ใช้คำสั่งนี้เพื่อสร้างแพ็ก ตรวจแพ็ก และสร้างปลั๊กอิน:

```sh
python3 advance-magic/tools/build_packs.py
python3 advance-magic/tests/check_packs.py
mvn -pl advance-magic -am package -DskipTests
cp advance-magic/target/advance-magic.jar dist/advance-magic.jar
```

การสร้างแพ็กอ่านต้นฉบับจาก `advance-magic/art/flying-staff/` และ `advance-magic/art/wands/` แล้วเขียนไฟล์แจกลง `advance-magic/dist/` ส่วน JAR ที่ build จะฝังแพ็กและ Geyser mapping เหล่านั้นไว้แล้ว

## ใช้งาน

ผู้เล่นทั่วไปคราฟต์ไม้เท้าได้ด้วยสูตร `GAG / BRB / GAG`: G = Gold Ingot, A = Amethyst Shard, B = Blaze Rod, R = Heart of the Sea แอดมินให้ของทดสอบได้ด้วย `/magic givestaff [ชื่อผู้เล่น]` หรือเมนู `/magic items` สิทธิ์ใช้งาน `advance-magic.flying-staff` เปิดให้ผู้เล่นทั่วไป

ถือไม้เท้าคลิกขวาเพื่อเรียกข้างหน้า คลิกขวาที่ตัวไม้เท้าเพื่อขึ้นขี่ ย่องเมื่อต้องการลง ถ้าอยู่สูงไม้เท้าจะลงจอดก่อน ลงแล้วตีไม้เท้าเพื่อเก็บ ไอเทมเดิมไม่ถูกย้ายหรือดรอปซ้ำ เรียกได้หนึ่งตัวต่อคน ต้องยังมีไอเทมอยู่ในตัวจึงขี่ต่อได้

ขณะขี่ เดินหน้า/ถอย/ซ้าย/ขวาเพื่อเคลื่อนที่ กระโดดเพื่อสูงขึ้น มองลงเกิน 35° แล้วเดินหน้าเพื่อลงระดับ ปล่อยปุ่มเพื่อลอยค้าง ค่าเริ่มต้น `horizontal-speed: 0.27` และ `vertical-speed: 0.16` บล็อกต่อ tick เพิ่มจากค่าเดิม 0.18 และ 0.12 ค่าเดิมใน config ของเซิร์ฟเวอร์จะถูกอัปเดตหนึ่งครั้ง ส่วนค่าที่แอดมินปรับเองจะคงเดิม

กด Sprint เพื่อเร่ง `turbo-multiplier: 1.8` เท่า (Java ใช้ Ctrl; Bedrock มือถือใช้ปุ่มวิ่ง) หรือคลิกขวาไม้เท้าในมือขณะขี่เพื่อเปิด/ปิด Turbo ค้าง ถ้าปุ่มใช้ไอเทมไม่ขึ้นบนมือถือ ใช้ `/magic turbo` แทนได้ ความเร็วสูงสุดที่โค้ดรับคือ 0.65 บล็อกต่อ tick ในแนวราบ และ 0.35 ในแนวดิ่ง ทั้งการเร่งและความเร็วปกติใช้มานาตามค่า `mana-per-second` เดียวกัน

มานาลด 2 ต่อวินาทีโดยค่าเริ่มต้น ระหว่างขี่หยุดฟื้นมานา เตือนเมื่อเหลือไม่เกิน 10 และลงจอดอัตโนมัติเมื่อหมด ถ้าลงจอดไม่ได้ใน 10 วินาทีหรือเข้าใกล้ขอบล่างโลก ระบบพากลับตำแหน่งที่เรียกไม้เท้า ตัวแสดงถูกลบเมื่อออกเกม ตาย ย้ายโลก ปิดปลั๊กอิน หรือไม่มีไอเทมในตัว

## Resource pack

`advance-magic/tools/build_packs.py` รวมโมเดลและแอนิเมชันเข้ากับแพ็กคทาเดิม โดยรักษา namespace/ไอเทมเดิมไว้ เพิ่ม Geyser mapping ในไฟล์เดียวกัน Bedrock pack version `1.5.0` ขยับจาก `1.4.0` เพื่อบังคับ client รับของใหม่ ตัวแสดงผลบน Armor Stand เปลี่ยนฐานจาก `iron_helmet` เป็น `carved_pumpkin` เพื่อให้ Java แสดง `item_model` แทนโมเดลเกราะ

Java เปิด bundled HTTP pack host บน TCP 8187 เป็นค่าเริ่มต้น เพราะ URL ของแพ็กเก่าที่เคยตรึงไว้ยังไม่มีไม้เท้า ระบบย้าย config ที่ชี้ URL ทางการรุ่นเก่ามาใช้ bundled host อัตโนมัติ และคำนวณ SHA-1 จาก ZIP ที่อยู่ใน JAR จริง URL ที่แอดมินกำหนดเองยังคงเดิม: ต้องอัปโหลด ZIP รุ่นใหม่นี้และตั้ง SHA-1 ให้ตรงเอง มิฉะนั้น Java จะเห็นภาพเก่า

ผู้เล่น Java ต้องเข้าถึงพอร์ต 8187 ของเซิร์ฟเวอร์ หากใช้ proxy/SRV ให้ตั้ง `resource-pack.host.public-host` หรือ `resource-pack.host.public-url` ให้ชี้มาที่ host นี้ ตรวจผลด้วย `/magic pack` และ `/magic pack resend` ถ้า host ไม่เริ่ม ระบบจะแจ้งใน log และไม่เสนอ ZIP รุ่นเก่าผิด hash

Geyser-Spigot บนเครื่องเดียวกันจะรับ Bedrock pack และ mapping จาก JAR ในช่วง `onLoad` ก่อน Geyser เริ่ม หาก Geyser แยกเครื่อง ให้คัดลอก `advance-magic/dist/advance-magic-bedrock.mcpack` ไป `packs/` และ `advance-magic/dist/geyser-mappings.json` ไป `custom_mappings/advance-magic.json` แล้วรีสตาร์ต Geyser/ให้ผู้เล่นรับแพ็กใหม่

## `allow-flight=false` และ anti-cheat

ไม้เท้าใช้ Armor Stand ที่เซิร์ฟเวอร์ขยับทีละระยะโดยรักษา passenger ไว้ ผู้เล่นเป็นผู้โดยสาร ระบบไม่สั่ง `setAllowFlight(true)` หรือ `setFlying(true)` จึงใช้กับ `server.properties` ที่ตั้ง `allow-flight=false` ได้ตามที่ทดสอบบน Paper การเคลื่อนที่ถูกจำกัดความเร็วและตรวจทางโล่งก่อนขยับ ความลื่นของภาพยังต้องตรวจจาก client จริง

ขณะขี่เท่านั้น ระบบให้ permission attachment ตาม `flying-staff.anticheat.bypass-permissions` ถ้าพบปลั๊กอินชื่อนั้น ติดตั้งค่าเริ่มต้นสำหรับ GrimAC (`grim.disabled`) และ NoCheatPlus (`nocheatplus.checks.moving.vehicle`) ถอด attachment ตอนลงจอด ออกจากเกม หรือลบไม้เท้า การให้สิทธิ์ Grim ปิดการตรวจของ Grim ทั้งหมดชั่วคราวระหว่างขี่ จึงควรใช้กับผู้เล่นที่ถือไม้เท้าอย่างถูกต้องเท่านั้น [Grim permissions](https://github.com/GrimAnticheat/Grim/wiki/Permissions), [NoCheatPlus vehicle permission](https://github.com/NoCheatPlus/NoCheatPlus/blob/master/NCPPlugin/src/main/resources/plugin.yml)

ปลั๊กอิน anti-cheat อื่นเติมรายการใน config เป็น `ชื่อปลั๊กอิน: [ชื่อ.permission]` ได้เมื่อมีเอกสารยืนยัน วิธีนี้ไม่ได้สั่ง anti-cheat ทุกยี่ห้อให้ bypass อัตโนมัติ บางตัว cache permission หรือมี API เฉพาะ ตัวอย่าง Matrix มี API `reloadPermissionCache` และ `tempBypass` แยก จึงต้องทำ adapter เฉพาะถ้าเซิร์ฟเวอร์นั้นใช้ Matrix [Matrix API](https://matrix.rip/docs/developer/api)

## ตรวจแล้วและยังต้องตรวจ

- Maven build ผ่าน; ตรวจโครงสร้าง ZIP, JSON, model, mappings, PNG, Bedrock manifest version, SHA-1 และไฟล์ที่ฝังใน JAR
- Paper 26.2 บนเซิร์ฟเวอร์ทดสอบแยกที่ตั้ง `allow-flight=false` ผ่าน 32 การตรวจ: เรียกและขึ้นขี่จาก Bukkit event จริง, ตรวจไอเทมแสดงผลบนหัว Armor Stand, ส่งปุ่มเดินหน้าและ Sprint ผ่าน packet แล้ววัดความเร็ว, ใช้ `/magic turbo` เพื่อเร่งค้าง, อัปเดตค่า config เดิมโดยรักษาค่าที่ปรับเอง, มานาลดสุทธิ, ไม่มีสำเนาไม้เท้า, พาหนะถูกเก็บ, ลงจอดเมื่อมานาหมด และ HTTP แจก ZIP ตรงกับ SHA-1
- Geyser-Spigot 2.11.3 บนเซิร์ฟเวอร์ทดสอบรันพร้อม JAR รุ่น `1.2.1-flying-staff` และแพ็ก Bedrock `1.5.0` ได้ จดทะเบียน custom items รวม 41 รายการ; ไฟล์ที่ติดตั้งใน `packs/` และ `custom_mappings/` ตรงกับไฟล์ที่ build ทุกบิต และ Paper ผ่าน 32 การตรวจในรอบเดียวกัน
- ตรวจ bytecode ของไคลเอนต์ Java 26.2: `LivingEntityRenderer` ส่งไอเทมที่ไม่เข้าเงื่อนไข armor layer ไป `ItemModelResolver` ด้วยบริบท `HEAD`; `CustomHeadLayer` วาดผลลัพธ์นั้นบนหัวโมเดล ส่วน Geyser 2.11.3 แปลงจอยสัมผัสเป็นปุ่มเดินของ Java และ `SPRINT_DOWN` เป็นปุ่ม Sprint ใน packet อินพุต การตรวจนี้ยืนยันเส้นทางโค้ด แต่ยังไม่ใช่การเห็นภาพในเกมจริง
- ทดสอบการให้และถอน permission กับปลั๊กอินจำลองชื่อ GrimAC; ยังไม่ได้ทดสอบกับ GrimAC หรือ NoCheatPlus จริง
- ยังไม่ได้ต่อ Java/Bedrock client เพื่อดูท่านั่ง การเลี้ยว การวางบล็อกบนไม้เท้า ภาพ Geyser หรือพาร์ติเคิล Bedrock จึงต้องทดสอบบนเซิร์ฟเวอร์ staging ก่อนเปิดให้ผู้เล่น

ไม่เปลี่ยน `server.properties`, ไฟล์ anti-cheat หรือเซิร์ฟเวอร์จริงของผู้ใช้ในงานนี้
