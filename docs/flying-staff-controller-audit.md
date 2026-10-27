# ผลตรวจคทาบินกับจอย Bedrock — 22 ตุลาคม 2026

รายงานนี้เป็นผลก่อนแก้ รุ่น `1.20.1-controller-flight` แก้ปัญหาและผ่านรอบตรวจใหม่แล้ว
ดู [การแก้ไข วิธีใช้ และ JAR](flying-staff-controller-fix.md)

ทดสอบ JAR ปัจจุบัน `dist/advance-magic.jar` รุ่น `1.20.0-boss-exploration-points`
บนเซิร์ฟเวอร์ทิ้งได้ใน workspace: Paper `26.3-152-1a7b626`, Geyser
`2.12.0-SNAPSHOT` commit `bc81f9e` และ Floodgate `2.2.5-SNAPSHOT`.
รอบนี้เพิ่มชุดทดสอบและรายงาน ไม่มีการแก้โค้ด production หรือไฟล์ JAR แจกจริง

ผลฉบับเต็ม: [flying-staff-controls-result.json](../output/flying-staff-controls-complete-20261022/flying-staff-controls-result.json)
และ [artifact-manifest.json](../output/flying-staff-controls-complete-20261022/artifact-manifest.json)
ซึ่งเก็บ SHA-256 ของ JAR ที่ทดสอบ

## จำนวนและวิธีทดสอบ

รายงานมี 208 checks ระดับกลุ่ม/สถานการณ์ ผ่าน 196 และไม่ผ่าน 12 โดย 8 กลุ่มในนั้น
รวมการเรียกตัวคำนวณอินพุตจริง 7,920 กรณี ไม่ใช่ 7,920 client sessions
รายการไม่ผ่าน 12 รายการมาจากข้อผิดพลาดสองประเภท: ทิศซ้าย/ขวา และระยะตรวจเพดาน
ในเมทริกซ์อินพุตมี 1,150 กรณีไม่ผ่านจากบั๊กซ้าย/ขวาเดียวกัน ส่วน 6,770 กรณีผ่าน
ไม่พบ `Could not pass event` หรือข้อผิดพลาดตอนเปิด/ปิดปลั๊กอินในรอบสมบูรณ์

| กลุ่ม | สิ่งที่ตรวจ | ผล |
| --- | --- | --- |
| อินพุตทุกทิศ | yaw 19 ค่า รวมมุมเฉียง ค่าลบ และเกิน 360°, ชุดทิศทาง 16 แบบ, หักล้างปุ่มตรงข้าม, ความเร็วแนวทแยง | ซ้าย/ขวากลับด้านทั้งเมทริกซ์และ packet บน Paper ทุกทิศหลัก |
| ความสูง | Jump, ขอบ pitch 35°, Jump พร้อมก้ม, เดินถอยหลังก้ม, ปล่อยปุ่ม, Sneak | ตรงกับพฤติกรรมปัจจุบัน; ลงได้เมื่อ forward และ pitch > 35 เท่านั้น |
| Turbo | manual/input Sprint/player Sprint ทั้งหมด, ค้าง/ปล่อย, ค่า config และขอบความเร็ว | การเร่งและขอบความเร็วถูกต้อง; ผลทิศข้างยังผิดจากบั๊กเดียวกัน |
| Geyser | เรียก InputCache จริงกับ GAMEPAD, keyboard/touch, ขนาดแกน 0.0001–1, หลายปุ่ม, ค้าง/ปล่อย 40 ticks, 20 รอบกดซ้ำ | แปลงสัญญาณสอดคล้องกับที่คาด; น้ำหนักอนาล็อกหายไป |
| พาหนะใน Geyser | ArmorStand passenger, Jump/Sprint, translator สำหรับ dismount, กดลงซ้ำและไม่ได้ขี่ | flag ส่งถึงเส้นทางอินพุตตามที่คาด; dismount สร้าง Shift หนึ่งครั้ง |
| เรียก/เรียกกลับ | มือรอง, สิทธิ์, spectator, ของซ้อน, ความทนทาน 0/1, สิ่งกีดขวาง, world border/build height, pre-cancelled air-use | ผ่าน; เรียกซ้ำหักความทนทานและเริ่ม SUMMON ใหม่ทุกครั้ง |
| ขึ้นขี่/เจ้าของ | ช่วง summon, mana 0, ไม่มีของ, ผู้เล่นอื่น, damage/manipulate, มือเปล่าและไอเทมอื่น | เงื่อนไขหลักผ่าน; ถือคทาธาตุ/คทาซ่อมแล้วขึ้นไม่ได้ |
| เคลื่อนที่/ชน | packet จริงซ้ายขวา 4 yaw, เร่ง/หยุด, ขึ้น/ลง, Turbo ชนกำแพง, เลื่อนไปตามกำแพง, พื้นขั้นหนึ่งบล็อก, กำแพงสูง, border/unloaded chunk | ผ่านกรณีการชนและหยุด; ทิศข้างกลับด้าน |
| ศีรษะผู้ขี่ | รอ passenger tick จริง วัด bounding box และวางเพดาน Y+3 | ไม่ผ่าน: หัวชนเพดานแต่ clear() ยอมรับ |
| มานา | hover ปกติ 2 ต่อ 20 ticks, Sprint Turbo 3, เร่งสั้น 3 ticks, หมดมานา | ผ่าน |
| ลงจากคทา | Bedrock ใกล้พื้น/กลางอากาศ/เหนือ void, Java กลางอากาศ/เหนือ void, มานาหมดเหนือ void | ผ่าน รวม task ส่งกลับจุดเรียกหลัง Bedrock ลงเหนือ void |
| เก็บกวาด | ลืมคทาเรียกกลับ, ตีเก็บ, เสียของในตัว, teleport ไกล, quit, world-change event, ปิด service | ผ่านสำหรับกรณีที่รัน |

## ปัญหาที่พบแน่นอน

1. **ซ้าย–ขวาสลับกัน** — `FlyingStaffService.input()` สร้าง side เป็น
   `(forward.Z, 0, -forward.X)` ซึ่งเป็นเวกเตอร์ซ้าย แต่บวกเมื่อ `isRight()` เป็นจริง
   สูตรขวาคือ `(-forward.Z, 0, forward.X)`.
   ที่ yaw 0 กดขวาควรไป -X แต่ไป +X; ยืนยันด้วยอินพุต packet บนเซิร์ฟเวอร์จริง
   เช่นกันทุกทิศหลัก ไม่ใช่เฉพาะ Bedrock

2. **ตรวจชนไม่ครอบคลุมศีรษะผู้ขี่** — หลัง Paper passenger tick วัดหัวผู้ขี่ได้
   `3.174999976` บล็อกเหนือจุดอ้างอิง ArmorStand แต่ `clear()` ตรวจสูงสุดเพียง +2.2
   เมื่อวางบล็อกเพดานที่ +3 bounding box ผู้ขี่ซ้อนกับบล็อก และ `clear()` ยังคืน true
   การทดสอบนี้ยืนยันช่องว่างของการตรวจชน ไม่ได้ทดสอบความเสียหายจากการขาดอากาศ

3. **ถือไอเทมบางชนิดแล้วขึ้นไม่ได้** — เมื่อคทาบินอยู่ในกระเป๋า มือเปล่าขึ้นได้
   แต่ถือคทาธาตุหรือคทาซ่อมจะขึ้นไม่ได้ ตัว listener ของไอเทมนั้นยกเลิก
   `PlayerInteractEntityEvent` ก่อน `FlyingStaffService.mount()` ซึ่งตั้ง `ignoreCancelled=true`.

## พฤติกรรมที่ทำให้ใช้จอยยาก

- **ควบคุมความเร็วด้วยน้ำหนักจอยไม่ได้:** InputCache ของ Geyser ที่ติดตั้งแปลง
  GAMEPAD analog vector ด้วยเครื่องหมายแกนเป็น boolean ทิศทาง ทดสอบขนาด 0.0001,
  0.01, 0.25, 0.5 และ 1 แล้วได้ทิศเดียวกัน แกนด้านข้าง 0.001 ขณะเดินหน้า 1
  กลายเป็นเดินเฉียงเต็มสัดส่วนเช่นกัน ผลนี้ใช้กับค่าที่ส่งมาถึง Geyser;
  ไม่ได้วัด deadzone ของจอยหรือ client จริง
- **ความเร็วเป้าหมายสูง:** ค่าเริ่มต้น 0.486 บล็อก/tick หรือ 9.72 บล็อก/วินาที
  ที่ 20 TPS; Turbo 4 เท่าเป็น 38.88 บล็อก/วินาที มีการค่อย ๆ เร่งและหยุด
  แต่ไม่มีน้ำหนักอนาล็อกให้ควบคุมความเร็วละเอียด
- **ลดระดับต้องใช้สองแกนพร้อมกัน:** ต้องก้มกล้องเกิน 35° และเดินหน้า
  ไม่สามารถลดระดับตรง ๆ โดยมองทางเดิมได้; ปุ่มลงพาหนะทำให้ออกจากคทา
- **เรียกซ้ำเสียของและเริ่มรอใหม่:** 4 use events ในช่วง summon เสียความทนทาน
  เพิ่ม 4 หน่วย และ age กลับเป็น 0 ต้องรออีก 12 ticks หลังการเรียกครั้งสุดท้าย
  กรณี 3 events ใน tick เดียวก็เสียเพิ่ม 3 หน่วย
- **Turbo สลับทุก accepted use event:** ขณะขี่ ทดสอบ use สองครั้งใน tick เดียว
  ได้ `false -> true -> false`. ยังไม่ได้ยืนยันว่าการกด trigger ค้างบนอุปกรณ์จริง
  ส่ง use events ซ้ำรูปแบบใด
- ปิด `flying-staff.enabled` หลังเรียกแล้ว ยังขึ้นยานเดิมได้ เป็นข้อสังเกตด้าน
  การตรวจ config ซ้ำ ไม่ได้ยืนยันว่าเป็นสาเหตุของคำร้องจากผู้เล่นครั้งนี้

## ขอบเขตที่ยังต้องลองกับผู้เล่นจริง

นี่คือเซิร์ฟเวอร์จริงกับผู้เล่น/packet จำลอง ไม่ใช่การเปิด Bedrock client ต่อด้วยจอย
การตรวจ Geyser ใช้ session ที่ไม่ได้ login และ stub เฉพาะ transport, metadata
ของการย่อง และ delayed remount; ตัว InputCache และ dismount translator เป็นโค้ดจริง
ของ Geyser. กรณี Bedrock ใน service เลือก branch บน session ของ Paper อย่างชัดเจน
จึงไม่ได้ทดสอบเส้นทาง RakNet/การ decode หรือทั้งสองเซิร์ฟเวอร์ต่อกันแบบ E2E

ต้องตรวจเพิ่มด้วย client จริง: ปุ่ม trigger ค้างส่ง use กี่ครั้ง, ความรู้สึกเลี้ยว/กล้อง,
การเล็ง hitbox เพื่อขึ้นขี่, มุมมองบุคคลที่หนึ่ง/สาม, ความหน่วง/packet loss, การโหลดฉาก,
แพ็กภาพบนเครื่องผู้เล่น, key mapping แต่ละจอย และความปลอดภัยของการตกจากที่สูง
หลัง cleanup ให้ Slow Falling 100 ticks. ไม่ได้ทดสอบปลั๊กอิน anti-cheat จริง;
ปลั๊กอิน probe ใช้ชื่อ GrimAC เพื่อทดสอบการให้และถอน permission attachment เท่านั้น

## รันทดสอบซ้ำ

```powershell
python advance-magic/tests/run_flying_staff_controls.py --source-server output/wild-walk-probe-20261022 --server output/flying-staff-controls-new
```

ใช้โฟลเดอร์ใหม่ใน `output/` และ cached Paper/Geyser fixture ที่มีอยู่.
บน Windows sandbox นี้ Java ต้องได้สิทธิ์ resolve โฟลเดอร์และเปิด localhost
นอก restricted token จึงจะเปิดเซิร์ฟเวอร์ทดสอบได้. Runner คืน exit code 1
เมื่อพบ assertion ไม่ผ่าน ซึ่งเป็นผลที่คาดสำหรับ JAR ปัจจุบันที่มีบั๊กข้างต้น

ต้นฉบับการตรวจ:
[runtime](../advance-magic/tests/FlyingStaffRuntimeChecks.java),
[input matrix](../advance-magic/tests/FlyingStaffControlChecks.java),
[Geyser](../advance-magic/tests/GeyserGamepadChecks.java),
[lifecycle เพิ่มเติม](../advance-magic/tests/FlyingStaffAdditionalChecks.java),
[runner](../advance-magic/tests/run_flying_staff_controls.py).
