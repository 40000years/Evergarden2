> Current model release: **1.20.2-weapon-remodel** (Bedrock pack **2.19.0**). All five weapons are re-sculpted and
> Bedrock now holds them with the same display transforms as Java; the trident-frame and screenshot-fitted
> first-person notes below are historical. See [weapon remodel](weapon-remodel.md).

> Gameplay release: **1.10.0-legendary-combat**. See [weapon abilities, enchanting and configuration](legendary-combat.md). Earlier release notes below are historical.

> Current hotfix: **1.9.13-longinus-bedrock-reach**. Packs remain **2.12.12**.
> Longinus distant swings now resolve inside the arm event before Paper 26.3
> resets attack strength for Geyser's Punch packet. The former next-tick handler
> saw an empty cooldown and never attacked. An installed-Geyser translator and
> real Paper Punch handler regression fails on 1.9.12 and passes on 1.9.13:
> Longinus hits at six blocks and up to seven blocks to the hitbox, ordinary
> swords/tridents/maces do not gain reach, cooldowns and walls still apply.
> Runtime checks: 81; Geyser/ModelEngine integration checks: 57. No actual client playtest.

> Current release: **1.9.12-longinus-melee** (Bedrock pack **2.12.12**).
> Longinus cannot be thrown. Main-hand melee reach is 7 blocks to the target hitbox.
> Distant swings use server ray tracing and native attacks, including cooldowns,
> walls and damage-event protection. Melee damage/speed retain the trident defaults.
> Tagged old Longinus tridents migrate when inventories load or the item is held.
> Existing approved model/idle/first-person/third-person transforms are unchanged.
> The projectile renderer is disabled; throw notes below describe older releases.
> Restart the server and reconnect clients to load the updated pack and mappings.
> Automated Paper/Geyser checks are not a real Bedrock-client playtest.

# อาวุธตำนาน — โมเดลและเอฟเฟกต์รุ่นแรก

เพิ่ม Excalibur, Longinus, Tyrfing, Mjolnir และ Vajra ลงใน Advance Magic
รุ่น `1.9.12-longinus-melee` เป็นไอเท็มตัวอย่างสำหรับตรวจรูปลักษณ์ ยังไม่มีสกิลพิเศษ
หรือสูตรคราฟต์ใหม่ ความเสียหายใช้ค่าปกติของไอเท็มฐาน

[ภาพรวมโมเดล](../previews/legendary-weapons/legendary-weapons.png) ·
[ภาพเคลื่อนไหว](../previews/legendary-weapons/legendary-weapons.gif) ·
[หน้าเปิดดูตัวอย่าง](../previews/legendary-weapons/index.html)

ภาพเหล่านี้เรนเดอร์จากกล่องและเท็กซ์เจอร์เดียวกับไฟล์แพ็กจริง เป็นพรีวิว asset
พร้อมแสงประกอบ ไม่ใช่ภาพจับหน้าจอ Minecraft และยังไม่ยืนยันว่าตรงภาพอ้างอิง 100%
ต้องตรวจตำแหน่งมือ แสง และขนาดจริงบนไคลเอนต์ Java/Bedrock อีกครั้ง

| อาวุธ | รายละเอียดโมเดล | เอฟเฟกต์ประจำตัว | ไอเท็มฐาน |
| --- | --- | --- | --- |
| Excalibur | คมเงินซ้อนชั้น ร่องทอง การ์ดทอง อัญมณีสีน้ำเงิน | แสงตามคมและประกายกากบาท | Netherite sword |
| Longinus | หอกใบเงิน แกนแดง กรงทอง ผ้าแดงสองชาย | แสงทองและประกายศักดิ์สิทธิ์ | Netherite sword (trident melee attributes) |
| Tyrfing | คมออบซิเดียนหยัก อักขระแดง ทับทิมกลางการ์ด | รอยแดงมีชีวิตและเศษอาคม | Netherite sword |
| Mjolnir | หัวเหล็กมีความหนา แถบทอง รอยแตกสายฟ้า ด้ามหนัง | สายฟ้าสีฟ้าเปลี่ยนรูป 8 เฟรม | Mace |
| Vajra | กรงวัชระทองแบบเปิด ผลึกฟ้า มีมิติด้านหน้าและหลัง | อัสนีสีไซแอนรอบกรง | Blaze rod |

โมเดลใช้กล่องที่มีความหนาจริงรวม 1,438 กล่อง ไม่ใช่การวางรูป concept บนแผ่นแบน
มีชั้นเอฟเฟกต์หน้า หลัง และด้านข้างซ้อนอยู่กับตัวอาวุธ วัตถุที่ถือจึงไม่มี entity
ติดตามมือหรือ scheduler เปลี่ยนไอเท็มทุกเฟรม

## จูนขนาด Bedrock แยกจาก Java ใน 1.9.6

ใช้ภาพ 085058 เทียบความยาวที่เห็นกับ Java เป็นเป้าหมาย แต่คงการวางโมเดลของ Bedrock
รอบนี้ขยาย Excalibur, Longinus และ Tyrfing ในมุมมองผู้ถือ Bedrock อีก 30% จาก 1.9.5
โดยขยายเท่ากันทุกแกนรอบจุดจับเดิม เพื่อเพิ่มความยาวที่เห็นและความหนาตามสัดส่วน
root rotation, root position, การยกด้าม และจุดจับเดิมไม่เปลี่ยน
Java pack, Mjolnir, Vajra, ไอคอน และท่า F5 คงเดิม
การปรับนี้เป็นขั้นถัดไปของการจูนจากภาพผู้เล่น ยังไม่ใช่การยืนยันว่าภาพตรง Java บน client จริง

## ขนาดและระดับมุมมองแรก 1.9.5

เพิ่มขนาดเฉพาะ Excalibur, Longinus และ Tyrfing ในมุมมองผู้ถือ Bedrock อีก 20% จาก 1.9.4
โดยขยายเท่ากันทุกแกน ความยาวจึงเพิ่ม 20% ด้วย และลดจุดจับตามแนวด้ามลง 0.5 หน่วยเล็กน้อย
Mjolnir และ Vajra คงท่า ขนาด และตำแหน่งของ 1.9.4 ทุกค่า ตามคำแก้ไขของผู้เล่น
ท่าและขนาดเมื่อ F5/third person คงเดิมตามที่ผู้เล่นยืนยันว่าสวยแล้ว
Java และไอคอนไม่เปลี่ยน รุ่นแพ็ก Bedrock เป็น 2.12.5

## ขนาดมุมมองแรก 1.9.4

ผู้เล่นยืนยันว่าตำแหน่ง 1.9.3 ใช้ได้แล้ว แต่ทั้งห้าชิ้นยังดูเล็กเมื่อเทียบกับดาบธรรมดา
ในภาพ 083230 และ 083239 จึงเพิ่มขนาดมุมมองแรก Bedrock เป็น 150% ของ 1.9.3
ใช้จุดจับเดิมเป็นจุดขยาย ไม่เปลี่ยน root, rotation, ตำแหน่งยกด้าม หรือจัดกรอบใหม่
โมเดลและเอฟเฟกต์ใช้กระดูก `grip` เดียวกัน จึงขยายพร้อมกันและรักษาสัดส่วนทุกแกน
Java, ไอคอน และท่าถือบุคคลที่สามยังใช้ข้อมูลเดิม
ตัวตรวจเปรียบเทียบกับค่าฐาน 1.9.3 ยืนยันจุดจับไม่เปลี่ยนและทุกจุดขยายเป็น 1.5 เท่ารอบจุดจับ

## ให้เห็นด้ามในมุมมองแรก 1.9.3

ภาพ `080743` ยืนยันว่าการเปลี่ยน root offset ของ 1.9.2 ทำให้โมเดลยังจมอยู่ด้านล่าง
และเหลือเฉพาะหัวหอกในจอ การลดขนาดโดยไม่ย้ายจุดจับเข้าสู่ภาพจึงไม่แก้ปัญหานี้

1.9.3 เพิ่ม translation บนกระดูก `grip` ตามแกนด้ามของอาวุธโดยตรง และจัดคู่ scale/translation
แยกทั้งห้าชิ้น เพื่อให้เห็นรูปร่างตั้งแต่หัวถึงโคน ใช้ภาพล่าสุดเทียบกรอบฉายโดยประมาณ:
รุ่นก่อนคาดว่าโคน Longinus อยู่ต่ำกว่าขอบภาพ 371px ลงไปมากกว่า 300px
กรอบใหม่ประมาณความสูงทั้งอาวุธที่ 280px และย้ายจุดจับเข้ามาในภาพ

ตัวตรวจ `check_legendary_camera_frame.py` อ่านแอนิเมชันจากแพ็กที่ส่งออกจริง
และตรวจกรอบของทุกมุมกล่องรวมถึงจุดจับ ไม่ตรวจเฉพาะหัวอาวุธ
กรอบฉายที่เทียบจาก screenshot เป็น affine approximation ไม่ใช่ Bedrock renderer
และไม่ครอบคลุม perspective/hand bob จึงยังไม่ใช่การยืนยันภาพบน client จริง
Java, ไอคอน, geometry และท่าถือ third person ใช้ข้อมูลเดิมของ 1.9.2

## จัดมุมมองแรกใน 1.9.2

ภาพจากเกมของรุ่น 1.9.1 แสดงว่าหัวอาวุธอยู่ใกล้กล้องมากและด้ามหลุดขอบจอ
จึงดูเหมือนมีเพียงส่วนบน รุ่น 1.9.2 แยกการจัดกรอบ first person จากขนาดในโลก:

- Bedrock ลดเฉพาะโมเดลในมุมมองแรกเป็น 0.48–0.56 ตามรูปร่างอาวุธ และเลื่อนท่าถือขึ้น/ถอยจากกล้อง
- Java ลด first person เป็น 0.62 สำหรับ Longinus และ 0.68 สำหรับชิ้นอื่น พร้อมถอยตำแหน่งในกรอบกล้อง
- จุดจับ ขนาด 0.90 และท่าถือ third person ยังคงเดิม ไอคอน hotbar ใช้กรอบใหญ่ของ 1.9.1 ต่อ

การตรวจพิกัดยืนยันว่า profile มุมมองแรกแยกจากท่าถือในโลกและขยายอย่างรักษาสัดส่วน
ยังต้องยืนยันระยะที่เห็นจริงบนไคลเอนต์ การทดสอบเซิร์ฟเวอร์ไม่ยืนยันการจัดกรอบกล้องได้

## แก้ตำแหน่งมือและขนาดใน 1.9.1

ภาพจากผู้เล่นพบว่า Bedrock แสดงโมเดลลอยเหนือมือและหลุดจากมุมมอง first person
รุ่นก่อนใช้ root pivot ที่ศูนย์ร่วมกับท่าหมุนที่ไม่ได้ยึดตาม item frame มาตรฐาน
และใช้ตำแหน่งด้ามเดียวกันทั้งห้าชิ้น การทดสอบลงทะเบียนไอเท็มเดิมตรวจไม่พบปัญหาภาพนี้

รุ่นนี้ใช้ item frame และ first/third-person pose จาก trident ของ Mojang
มี pivot `[0,24,0]` ตามไฟล์ native แยกกระดูก `grip` สำหรับขยายรอบตำแหน่งจับจริง
แปลงด้ามแต่ละชิ้นให้ตรงตำแหน่งกึ่งกลางด้าม trident ในกรอบนั้น และให้ body/inlay/เอฟเฟกต์
ใช้ลำดับ parent เดียวกัน ไม่ขยาย root ที่มีระยะ offset จากมือ
ท่ามือซ้ายสะท้อนจากท่ามือขวาโดยไม่ยืดเฉพาะแกน

Java คำนวณ translation ของแต่ละโมเดลจากตำแหน่งจับของ `item/handheld` ใน client 26.3
แทนการตั้ง offset เดียวกัน ขยาย first person จาก 0.62 เป็น 0.80 และ third person จาก 0.65 เป็น 0.90
โดยชดเชยตำแหน่งด้ามเมื่อขยาย ทำให้จุดอ้างอิงจับยังอยู่ที่เดิม

ไอคอนทั้งสอง Edition ครอปรอบเนื้ออาวุธแทนพื้นที่ประกายที่อยู่ไกล
ขยายแบบรักษาสัดส่วน มีขอบโปร่งใส และใช้กรอบเดียวกันทุกเฟรมเพื่อไม่ให้ขนาดกระตุก
เพิ่มตัวตรวจตำแหน่งมือ/สัดส่วน/ไอคอนอีก 145 ข้อ โดยอ่านไฟล์ที่ส่งออกจริง
พร้อม fixture ท่าถือ native การตรวจนี้ยังไม่ใช่การเปิดเกมตรวจท่าถือบน client

## ทดลองในเกม

นำ [dist/advance-magic.jar](../dist/advance-magic.jar) ไปแทน JAR เดิมขณะปิดเซิร์ฟเวอร์
แล้วเริ่มใหม่ ให้ผู้เล่นโหลดแพ็กใหม่และเชื่อมต่อใหม่

```text
/magic weapons
/magic weapon give all
/magic weapon give mjolnir
/magic weapon give excalibur PlayerName
```

เมนูและคำสั่งรับอาวุธใช้สิทธิ์ `advance-magic.admin` และใช้ได้ทั้ง Java/Bedrock
เมนูคลิกเพื่อรับไอเท็มจากเซิร์ฟเวอร์ มีการตรวจสิทธิ์ซ้ำและกันการย้ายรูปไอเท็มออกจากเมนู
ใช้ `@s` เป็นผู้รับได้ และรองรับชื่อ Bedrock ที่มีจุดนำหน้า

แพ็กทั้งสองฝังใน JAR แล้ว:

- Java: [advance-magic-java.zip](../advance-magic/dist/advance-magic-java.zip)
- Bedrock: [advance-magic-bedrock.mcpack](../advance-magic/dist/advance-magic-bedrock.mcpack), รุ่นแพ็ก `2.12.6`
- Geyser: [geyser-mappings.json](../advance-magic/dist/geyser-mappings.json), รูปแบบ v2

ระบบเดิมติดตั้งแพ็กและ mapping ให้ Geyser-Spigot ที่อยู่ในเซิร์ฟเวอร์เดียวกันระหว่าง `onLoad`
ถ้าใช้ Geyser แยกเครื่อง ต้องคัดลอกแพ็กและ mapping ไปอินสแตนซ์นั้นแล้วเริ่ม Geyser ใหม่
ถ้าตั้ง `resource-pack.url` เป็น CDN ของตนเอง ให้อัปโหลด ZIP ใหม่พร้อม SHA-1 ใหม่ด้วย
ระบบคง URL ของผู้ดูแลไว้ ดูค่าแฮชใน [pack-hashes.json](../advance-magic/dist/pack-hashes.json)

## สิ่งที่แสดงบนแต่ละไคลเอนต์

Java ใช้ `item_model` เลือกโมเดล 3D เมื่อถือ และเลือกไอคอนเรนเดอร์เฉพาะเมื่ออยู่ใน GUI/hotbar
เท็กซ์เจอร์อาวุธและไอคอนมีเอฟเฟกต์เคลื่อนไหว 8 เฟรม ที่ 10 fps
คม อัญมณี อักขระ และสายฟ้าบางส่วนกำหนดค่า emissive ในโมเดล

Bedrock ใช้ Geyser mapping แยกแต่ละอาวุธและ attachable geometry ที่ส่งออกจากกล่องชุดเดียวกัน
มีท่าถือ first person/third person และผูกเข้าช่อง main hand/off hand
render controller สลับเอฟเฟกต์ 8 เฟรมด้วยเวลาไคลเอนต์ พร้อมแอนิเมชันออราและชายผ้าของ Longinus
ไอคอน Bedrock รุ่นนี้มีสายฟ้า/ประกายอยู่ในภาพ แต่เป็นภาพนิ่ง
อัญมณีและลวดลายเรืองแสงใช้ material แยกจากตัวโลหะ

เมื่อหยิบถือ จะมีวงเวทย์วาดเองเฉพาะอาวุธเป็นเวลา 1.2 วินาที
ใช้ EffectEngine, ItemDisplay และ native Bedrock particle bridge ของ Advance Magic เดิม
ระบบจำกัดความถี่ ลบวงเมื่อเก็บอาวุธ และใช้ cleanup เดิมเมื่อผู้เล่นตาย เปลี่ยนโลก หรือปิดปลั๊กอิน

เสียงใช้เสียง Minecraft หลายชั้นตามบุคลิกอาวุธ เช่น thunder สำหรับ Mjolnir
ส่งเฉพาะผู้ถือ มีเสียงกังวานเบาทุก 12 วินาที ปรับได้ใน `plugins/advance-magic/config.yml`:

```yaml
legendary-weapons:
  enabled: true
  equip-sigil: true
  sounds: true
  volume: 0.45
```

`enabled` ควบคุมระบบเอฟเฟกต์และเสียงฝั่งเซิร์ฟเวอร์ เท็กซ์เจอร์เคลื่อนไหวบนไอเท็มยังเป็นหน้าที่ไคลเอนต์
ตั้งแต่ 1.9.7 Longinus ที่ปาออกไปมีโมเดลเฉพาะทั้ง Java และ Bedrock พร้อมเอฟเฟกต์เดิม
ตัว trident จริงยังจัดการวิถี การชน ดาเมจ Loyalty และการเก็บคืน ไม่เปลี่ยนภาพ trident ธรรมดา
Java ใช้ ItemDisplay ที่ติดตามหอก ผู้เล่นที่ยังโหลดแพ็กไม่สำเร็จจะเห็น trident ปกติเป็น fallback
Bedrock ใช้ Geyser custom entity definition เฉพาะ UUID ของ Longinus จึงต้อง **รีสตาร์ตเซิร์ฟเวอร์**
เพื่อให้ definition ลงทะเบียนก่อน Geyser เปิดใช้งาน รุ่น Geyser ที่ไม่มี API นี้จะใช้ภาพ trident ปกติ
แพ็ก Bedrock ปัจจุบันเป็น 2.12.11 ต้องออกเข้าเกมใหม่เพื่อรับแพ็ก

1.9.11 ตรวจคลิปผู้ใช้ `Clip_-_10162026_105532_AM.mp4` แบบแยกเฟรม
ช่วงประมาณ 0.6–0.9 วินาทีเห็นภาพหอกเริ่มชิดตัวแล้วชี้กลับเข้าตัวละคร
การตรวจแกนก่อนหน้านี้หยุดที่ mesh space และขาดการสะท้อนแกน Z จาก entity space ไป world space
จึงต้องแก้ root yaw เป็น `180 + flight_yaw` ไม่ใช่ `-flight_yaw`
โดยคง pitch และตำแหน่งปลายหอกเดิม เมื่อรวมการแปลงกรอบโลกแล้วหัวหอกจะตรงกับทิศความเร็ว
ทดสอบครบ 26 ทิศหลัก/เฉียง และอีกสองทิศเฉียงที่ไม่สมมาตร
ทดสอบกับแพ็ก 1.9.10 เดิมพบว่ากรณีบินไป world +Z หัวกลับชี้ world -Z
อ้างอิงกรอบพิกัดจาก [Coordinate Space Conversion](https://wiki.bedrock.dev/entities/coordinate-space-conversion.html)

อีกจุดคือ projectile เกิดใกล้ตาผู้ยิงก่อน physics step แรก แต่โมเดลยาวเกือบสองบล็อก
จึงไม่แสดง carrier ให้ Bedrock ใน birth frame และไม่แสดง trident สำรองแทรกในจังหวะนั้น
เมื่อหอกจริงเคลื่อนจากจุดปล่อยแล้วจึงเปิดภาพ หรือเปิดทันทีเมื่อเป็นหอกที่ชน/โหลดกลับมา
ไม่ย้ายจุดเกิดของหอกจริง ไม่แก้ความเร็ว การชน ดาเมจ Loyalty หรือภาพฝั่ง Java
ภาพที่ใช้ตรวจคลิปเป็นเฟรมจากคลิปเดิม ไม่ใช่ภาพไคลเอนต์ที่รันรุ่นแก้แล้ว
ยังต้องยืนยันผลภาพบนไคลเอนต์ Bedrock จริง

1.9.10 แก้การส่งมุมหอกที่หยุดแล้วใน Bedrock โดยไม่เปลี่ยนไฟล์ใน Java pack
พบกรณี spawn event เก็บมุมตอนเริ่มบินไว้ แต่ consumer ทำงานหลังหอกชนพื้นแล้ว
อีกกรณีคือส่งมุมตอนชนขณะที่ entity ยังไม่เข้า cache ของไคลเอนต์ และโค้ดเดิมไม่ส่งซ้ำ
เพราะมุมไม่เปลี่ยนหลังจากนั้น จึงเกิดหอกบางเล่มใช้มุมเก่าและดูนอนราบ

spawn consumer ใช้มุมล่าสุดเมื่อทำงานจริง และติดตามการส่งแยกตาม connection กับ entity instance
การหา entity ไม่เจอจะไม่นับว่าส่งสำเร็จ เมื่อ entity เข้า cache จะส่งมุมสุดท้ายใหม่
รวมถึงเมื่อ entity ถูกสร้างใหม่ตอนกลับเข้าระยะมองเห็น มีการยืนยันค่าซ้ำทุก 40 ticks
และล้างรายการเมื่อเก็บหอกหรือปิดการเชื่อมต่อ

เพิ่ม regression test ด้วย Geyser session/entity cache และ SetEntityDataPacket จริง:
สร้าง entity ล่าช้าหลังชนพื้น, ส่งมุมสุดท้ายเมื่อ cache ยังว่าง, cache พร้อมในภายหลัง
และสร้าง entity instance ใหม่โดยมุมไม่เปลี่ยน รุ่น 1.9.9 ไม่ผ่านกรณี delayed spawn consumer
ส่วนไฟล์โมเดล แกน สเกล และท่าถือทั้งหมดคงเดิม ยังไม่ได้ยืนยันภาพด้วยไคลเอนต์ Bedrock จริง

1.9.9 แก้เฉพาะท่าง้าง first-person ของ Java โดยคงแกนเดียวกับท่าถือปกติ
ตามไฟล์ `trident_in_hand.json` และ `trident_throwing.json` ในไคลเอนต์ Minecraft 26.3 ที่ติดตั้งอยู่
ท่าง้าง third-person ที่ผู้เล่นยืนยันแล้วคงเดิม และไม่เปลี่ยนภาพหอกปักพื้นฝั่ง Java

Bedrock แยกภาพออกจาก entity trident ที่มีการทำนายการเคลื่อนที่ฝั่งไคลเอนต์
ใช้ armor stand แบบ marker ไม่มีแรงโน้มถ่วง ไม่บันทึกลงโลก และไม่มีขนาดชน
แสดงเฉพาะผู้เล่น Bedrock ผ่าน custom entity definition เดิม และซ่อน trident จริงกับ ItemDisplay ของ Java
ตัว marker ต้องไม่ตั้ง Invisible เพราะจะซ่อนโมเดล custom ไปด้วย
เซิร์ฟเวอร์ย้าย marker ตามตำแหน่งหอกจริง รวมถึงตอนค้างบนพื้น และลบเมื่อหอกถูกเก็บหรือถูกลบ
การชน ดาเมจ Loyalty และการเก็บคืนยังเป็นหน้าที่ trident จริง

แก้ root pitch ของโมเดล Bedrock เป็นค่าบวกจาก flight_pitch ขณะที่ yaw ใช้ค่าลบ
เมื่อรวมกับแกน X/Y ของ Bedrock และ grip -90 องศา หอกที่บินลงจะชี้ปลายลงและด้ามอยู่นอกพื้น
ตรวจสมการจุดปลายและโคนทั้งแปดทิศ รวมถึงลงตรงและแนวเฉียง
อ้างอิงการแปลงแกนจาก [Blockbench Bedrock geometry codec](https://github.com/JannisX11/blockbench/blob/master/js/formats/bedrock/bedrock.js)
และ [animation codec](https://github.com/JannisX11/blockbench/blob/master/js/formats/bedrock/bedrock_animation.js)

การตรวจ Geyser รอบนี้สร้าง ArmorStandEntity จริงจาก entity factory แล้วจับ AddEntityPacket ที่ส่งออก
ตรวจ identifier, ตำแหน่ง, ความเร็วศูนย์, ไม่ Invisible, scale=1 และ rotation properties ทั้งสองค่า
รวมถึงตามตำแหน่งเมื่อหอกย้าย อยู่กับที่ 30 ticks โดยไม่จม และลบ marker พร้อมหอก
ยังไม่ได้ตรวจภาพหรืออินพุตด้วยไคลเอนต์ Java/Bedrock จริง

1.9.8 แยกทิศตอนบินออกจากความเร็วสะท้อนหลังชนและคงทิศเดิมเมื่อปักพื้น
จุดอ้างอิงภาพอยู่ที่ปลายหอกใกล้จุดชน จึงไม่ฝังด้ามครึ่งหนึ่งไว้ในบล็อก
Bedrock รับ yaw/pitch จากเซิร์ฟเวอร์ผ่าน custom entity properties แทนการพึ่งมุมของ vanilla renderer
เพิ่มโมเดลง้าง Java เฉพาะตอน using_item โดยกลับแกนรอบตำแหน่งมือและไม่ย้ายจุดจับ
เพิ่มท่าง้าง Bedrock ทั้ง first/third person ตามตัวอย่าง native trident ของ Mojang
พร้อมกำหนด consumable animation `spear` ใน Geyser mapping เพื่อให้เกิด use_animation=6
และมีระยะกดค้างยาวรอการปล่อย แม้ผู้เล่นมีค่าความหิวเต็ม
เซิร์ฟเวอร์ยังใช้ trident ปกติและเกณฑ์กดค้าง 10 ticks ไม่จำลองการปาซ้ำด้วยปลั๊กอิน

ทดสอบการยิงลงชนบล็อกจริงบน Paper: ปลายอยู่บนผิวบล็อก ด้ามอยู่นอกพื้น
และทิศไม่กลับหลังชน รวมถึงทดสอบการกดค้าง–ปล่อยซ้ำ 5/10/20/30 ticks
ทดสอบ Geyser NBT ที่ส่งจริงทั้งสาม protocol tables สำหรับ animation, use duration และการใช้งานขณะหิวเต็ม
และตรวจค่า yaw/pitch ที่ตั้งก่อน spawn ยังไม่ได้ตรวจภาพหรืออินพุตด้วยไคลเอนต์ Java/Bedrock จริง

แหล่งอ้างอิง: [Mojang trident animations](https://github.com/Mojang/bedrock-samples/blob/main/resource_pack/animations/trident.animation.json),
[Geyser custom-item component encoding](https://github.com/GeyserMC/Geyser/blob/master/core/src/main/java/org/geysermc/geyser/registry/populator/CustomItemRegistryPopulator.java)

Mjolnir เปลี่ยนฐานจาก NETHERITE_AXE เป็น MACE ใช้กลไก smash ของ vanilla รวมถึงดาเมจตามระยะตก
และการรีเซ็ตระยะตกเมื่อทุบสำเร็จ ค้อนเก่าที่มีแท็กและ item model ถูกต้องจะเปลี่ยนเป็น MACE
เมื่อเข้าเกม เปิดกระเป๋า/หีบ เลือกถือ หรือเก็บจากพื้น โดยรักษาชื่อ lore และ enchantments เดิม
โมเดล ท่าถือ สเกล และไอคอนที่รับแล้วคงเดิมทุกไฟล์

ทดสอบการโจมตีจริงด้วยผู้เล่นจำลองบน Paper: ยืนตี 6 ดาเมจ ระยะตก 6 บล็อกตี 36 ดาเมจ
เท่ากับ MACE ปกติในเงื่อนไขเดียวกัน พร้อมการรีเซ็ต fall distance และ fall protection
ตรวจเส้นทาง Geyser spawn event และ mapping ด้วยปลั๊กอินที่ติดตั้งจริง ยังไม่ได้ทดสอบภาพด้วยไคลเอนต์เกมจริง

## ต้นฉบับและ ModelEngine 4.1.1

ไฟล์แก้ไขได้อยู่ที่ [advance-magic/art/legendary-weapons](../advance-magic/art/legendary-weapons)
มี `.bbmodel` ทั้งห้าชิ้น ฝังเท็กซ์เจอร์ครบ พร้อมคลิป `idle` และ helper hitbox สำหรับตัวนำเข้า ModelEngine
helper นี้ไม่อยู่ในโมเดลไอเท็มและไม่ได้เพิ่ม hitbox หรือดาเมจในเกม

หากต้องการใช้เป็นโมเดล entity ใน ModelEngine ให้นำเข้าไฟล์โดยใช้ชื่อ
`legendary_excalibur.bbmodel` เป็นต้น การตรวจ integration ใช้ชื่อนี้
JAR ไม่ติดตั้ง blueprint ลง ModelEngine อัตโนมัติ โมเดลที่ถือจริงใช้แพ็ก native ของแต่ละ Edition
จึงใช้ได้แม้เซิร์ฟเวอร์ไม่ได้เปิด ModelEngine; แอนิเมชัน `idle` ใน `.bbmodel`
เป็นต้นฉบับสำหรับต่อยอดท่าขยับด้วย ModelEngine และไม่ใช่ตัวควบคุมเอฟเฟกต์บนไอเท็ม Java

ตัวสร้าง geometry/ลาย/วงเวทย์: [legendary_weapon_assets.py](../tools/legendary_weapon_assets.py)
ตัวเรนเดอร์พรีวิว: [legendary_weapon_preview.py](../tools/legendary_weapon_preview.py)

```powershell
python advance-magic/tools/build_packs.py
python tools/legendary_weapon_preview.py
python advance-magic/tests/check_legendary_weapons.py
python advance-magic/tests/check_legendary_hand_fit.py
python advance-magic/tests/check_legendary_camera_frame.py
python advance-magic/tests/run_legendary_weapon_checks.py --source-server <cached-paper-with-compile-dependencies> --server <new-test-folder>
python advance-magic/tests/run_legendary_weapon_integration.py --source-server <cached-server-with-geyser-and-modelengine> --server <new-integration-folder>
```

ตรวจแล้ว: asset checks 225 ข้อ และ runtime บน Paper 26.2 จำนวน 58 ข้อ
ครอบคลุม item factory, การบันทึกไอเท็ม, คำสั่ง, สิทธิ์, กระเป๋าเต็ม, เมนู,
แพ็กเสียงที่ส่งจริง, offhand และการเก็บกวาด entity ของวงเวทย์
ตัวตรวจแพ็กเดิมผ่าน รวมถึงเปรียบเทียบไฟล์แพ็กภายใน JAR กับไฟล์ที่แจก
integration บน Paper 26.3 พร้อม Geyser 2.12.0-SNAPSHOT และ ModelEngine R4.1.1 ผ่านอีก 41 ข้อ
รวม matcher จริงครบทั้งห้าอาวุธในสาม protocol tables การนำเข้า blueprint ทุกชิ้น
การสร้างกระดูก การเปิดคลิป `idle` และตรวจแพ็กที่ Geyser ติดตั้งเทียบกับแพ็กใน JAR
การทดสอบเหล่านี้ไม่เปิดไคลเอนต์เกมจริง และไม่ได้ติดตั้งลงเซิร์ฟเวอร์ใช้งานจริง

เอกสารอ้างอิงสำหรับตัวส่งออก:
[Geyser custom items v2](https://geysermc.org/wiki/geyser/custom-items/),
[Bedrock attachables](https://learn.microsoft.com/en-us/minecraft/creator/documents/attachables?view=minecraft-bedrock-stable),
[render controller arrays](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/animationsreference/generated/animationrendercontroller?view=minecraft-bedrock-stable),
[vanilla materials](https://learn.microsoft.com/en-us/minecraft/creator/documents/material-files?view=minecraft-bedrock-stable)
