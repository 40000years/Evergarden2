# รีโมเดลอาวุธตำนาน 5 ชิ้น + อาวุธหายาก 18 ชิ้น และแก้ท่าถือ Bedrock

Advance Magic **1.20.2-weapon-remodel** (แพ็ก Bedrock **2.19.0**) ·
แพ็ก Evergarden Bedrock **3.50.0** ใน `dist/evergarden.jar` (รุ่น `3.0.0-e2.120-airborne-boss-combat`)

[ก่อน/หลัง ดีไซน์ทั้ง 23 ชิ้น](../previews/weapon-remodel/designs-before-after.png) ·
[ท่าถือ Bedrock ก่อน/หลัง](../previews/weapon-remodel/bedrock-hold-before-after.png) ·
[อาวุธตำนาน](../previews/legendary-weapons/legendary-weapons.png) ·
[อาวุธหายาก](../previews/adventurer-weapons/adventurer-weapons.png)

ภาพทั้งหมดเรนเดอร์จากกล่องและค่าที่ส่งออกจริง ไม่ใช่ภาพจับหน้าจอเกม

## ทำไมท่าถือ Bedrock ถึงแปลก

ทุกอาวุธบน Bedrock ใช้กรอบถือของ **ตรีศูล (trident)** ของ Mojang:
pivot `[0,24,0]`, ท่า third person `[97,-1.5,-49]` และ first person ที่จูนจากภาพหน้าจอ (scale/lift แยกรายชิ้น)

- ท่าตรีศูลหมุนวัตถุรอบแกนตัวเองราว **49°** ดาบ ขวาน และค้อนจึงถือเอียง ใบดาบไม่ตั้งคม การ์ดเอียงทแยง
- ด้ามอยู่ห่างจากตำแหน่งกำมือของ Java ราว 2.6 px (ไปทางหลังมือ)
- first person เป็นค่าที่ไล่จูนจากภาพ ไม่ได้มาจากท่าของ Java จึงไม่ตรงกับฝั่ง Java

## สิ่งที่แก้

Bedrock ใช้ rig แบบ java2bedrock (เครื่องมือแปลงโมเดลที่เซิร์ฟเวอร์ Geyser ใช้กันทั่วไป):
กระดูก `root` ผูกกับช่องมือ แล้วตามด้วย `display_x/y/z` ที่ใส่ค่า display transform **ชุดเดียวกับ Java**

- **Third person ตรงกับ Java ทุกจุด**: ตรวจทุกมุมของทุกกล่องในอาวุธทั้ง 23 ชิ้น ผลต่างสูงสุดไม่ถึง 0.001 px
  ใบดาบตั้งคมขึ้น-ลง ด้ามอยู่ในกำมือ มือซ้ายเป็นภาพสะท้อนของมือขวา
- **First person** ใช้กรอบมือ `[90,60,-40]`, `[4,10,4]`, ×1.5 ของ java2bedrock และขนาดเดียวกับ Java
  (0.68 / หอก 0.62) ทุกชิ้นจับที่จุดเดียวกับไอเท็ม handheld ของเกม
  ปรับค่า Z ของ java2bedrock ให้คงผลเดิมกับไอเท็มปกติ แต่ไม่ดันอาวุธยาวไปผิดทาง
- อาวุธคมเดียว (Moonrift, Silvershiv, Ironwave, Bramblepike และ knuckle bow ของ Starfang) กลับด้านให้คมหันลง/ออกจากผู้ถือ
- ท่าถือฝั่ง Java เดิมไม่เปลี่ยนวิธีคำนวณ (จับที่จุด contact ของ handheld) เปลี่ยนเฉพาะตำแหน่งด้ามตามโมเดลใหม่

## โมเดลใหม่

ปั้นใหม่ทั้งหมดด้วย `tools/weapon_forge.py`: ขอบเบเวลหลายชั้น (คมบาง ตัวใบหนา) คำนวณจากระยะห่างจากขอบจริง
ไม่มีกล่องซ้อนระนาบเดียวกัน (กัน z-fighting), หัวค้อน/ลูกตุ้ม/กรงวัชระเป็นวอกเซล 3 มิติ
เท็กซ์เจอร์วัสดุเป็นสีเรียบเงาอ่อน และเอฟเฟกต์ตอนถือของอาวุธหายากแยกตามธาตุ (ถ่านไฟ เกล็ดน้ำแข็ง ประกายฟ้า ใบไม้ ฟองน้ำ ประกายทอง ละอองว่าง กลีบดอก)

| อาวุธ | ดีไซน์ใหม่ |
| --- | --- |
| Excalibur | ใบเงินขัดเงา ร่องเลือดมืดฝังเส้นทอง อักขระฟ้า การ์ดปีกทอง แซฟไฟร์ |
| Longinus | หัวหอกใบไม้ แกนเลือดแดง มงกุฎทองฝังทับทิม ผ้าแดงสองชายหางแฉก |
| Tyrfing | ขอบซ้ายตรง ขอบขวาฟันเลื่อยเป็นระเบียบ อักขระเลือด การ์ดเขาปีศาจ ตาแดง |
| Mjolnir | หัวค้อนลบมุม แถบทอง ลายสายฟ้าในกรอบรูนสองหน้า ด้ามสั้นพันหนัง |
| Vajra | กรงทอง 4 ซี่แบบ 3 มิติ ผลึกฟ้าแปดหน้า |
| Emberfang | ดาบเปลวไฟ (flamberge) เหล็กไหม้ แกนถ่านเรือง การ์ดปีกเปลว |
| Stormpiercer | หอกปีกพับ ใบหอกแกนฟ้า ธงผ้าสีฟ้า |
| Ironbloom | ขวานสองคมทรงพระจันทร์ ใบไม้ทองฝัง ยอดใบไม้เรืองแสง เถาวัลย์ |
| Frostveil | กริชจริง (สั้นลง) ใบผลึกน้ำแข็ง แกนแสง หนามน้ำแข็งที่การ์ด |
| Sunflare | ลูกตุ้มดาวทรงกลม 3 มิติ หนาม 13 ทิศ ปลายเรือง อัญมณีดวงอาทิตย์ |
| Moonrift | ดาบโค้งจริง คมด้านนอกเรืองม่วง สันหนา knuckle bow ทอง |
| Bramblepike | ง้าวขวานพระจันทร์มีหนาม ขอเกี่ยวด้านหลัง เถาหนามพันด้าม |
| Tidehook | ตรีศูลง่ามโค้งมีเงี่ยง ฐานคลื่นทอง ไข่มุก |
| Ashcleaver | ดาบใหญ่สองมือ ricasso เหล็กดำ รอยร้าวถ่านเรืองแตกกิ่ง |
| Glimmermaul | ค้อนศึกหน้าบาน ขอบทอง ตราดวงอาทิตย์ หนามยอด |
| Mistcaller | คทาเขาจันทร์โอบลูกแก้วน้ำแข็ง พู่ลูกปัดน้ำแข็ง |
| Silvershiv | มีดสั้นจริง (สั้นลง) คมเรืองม่วง พู่ผ้าม่วงที่ห่วงด้าม |
| Stoneheart | กระบองครีบเหล็ก 8 ครีบ หัวใจถ่านเรืองในแกนหิน |
| Nightbloom | เคียวใบยาวคมเรืองชมพู ดอกไม้ราตรีที่โคน |
| Ironwave | ง้าวใบโค้ง สันเป็นคลื่น คมเรืองฟ้า |
| Starfang | เรเปียร์ knuckle bow ทอง วงแหวนการ์ด ปุ่มด้ามรูปดาว |
| Rootspire | กระบองไม้เก่าทรงแปดเหลี่ยม รากพันสองเส้น มอสบนยอด อัญมณีธรรมชาติ |
| Voidreel | จักรวงแหวน ใบมีดโค้ง 8 ใบ ดุมอัญมณีว่าง ด้ามจับด้านใน |

Thornwhisper และ Runelock (ธนู/หน้าไม้) ปั้นใหม่และเปิดใช้งานแล้วใน e2.134 ดู [ranged-weapons.md](ranged-weapons.md)

## ติดตั้ง

1. ปิดเซิร์ฟเวอร์ แทนที่ `plugins/advance-magic.jar` ด้วย `dist/advance-magic.jar` และ `plugins/evergarden.jar` ด้วย `dist/evergarden.jar`
2. เปิดเซิร์ฟเวอร์ใหม่ ผู้เล่นออกแล้วเข้าใหม่เพื่อรับแพ็ก (Java ได้ SHA-1 ใหม่, Bedrock เป็นเวอร์ชัน 2.19.0 / 3.50.0)
3. ถ้าใช้ Geyser แยกเครื่องหรือ CDN ของตนเอง ให้คัดลอกแพ็กใหม่และ SHA-1 จาก `pack-hashes.json` ด้วย

`dist/evergarden.jar` เป็นรุ่น e2.120 ที่อีกงานเพิ่งสร้าง (airborne boss combat) รอบนี้แทนเฉพาะแพ็กใน JAR และเลขเวอร์ชันใน `judge-design.json`
ไม่ได้แตะ class ใด ๆ; Advance Magic เปลี่ยนเฉพาะแพ็กและ `plugin.yml`

## สร้างใหม่และตรวจ

```powershell
python tools/build_weapon_remodel.py --output output/<โฟลเดอร์ใหม่> --patch-jars --magic-plugin-version <เวอร์ชัน>
python tests/weapon-remodel/check_weapon_hold.py
python evergarden/tests/check_adventurer_weapons.py
python advance-magic/tests/check_legendary_weapons.py
python advance-magic/tests/check_legendary_hand_fit.py
python advance-magic/tests/check_legendary_camera_frame.py
python tools/weapon_remodel_preview.py --before output/weapon-remodel-20261023/before/tools
```

สคริปต์ build สร้างอาวุธสองตระกูลใหม่ แทนเฉพาะไฟล์ของอาวุธในแพ็ก dist ปัจจุบัน
ยืนยันว่าไฟล์อื่นในแพ็กเหมือนเดิมทุกไบต์ และ Geyser mappings ไม่เปลี่ยน แล้วขยับเวอร์ชัน Bedrock ทีละขั้น

ผลตรวจ: hold 876, อาวุธหายาก 727, อาวุธตำนาน 236 + 80 + 20, อาวุธระยะไกลที่ปิดไว้ 22,
Banshee/แม่น้ำ 30,515, Judge 17,947 ผ่านทั้งหมด

ข้อจำกัด: ยังไม่ได้เปิดเกมจริงทั้ง Java และ Bedrock
third person ของ Bedrock ตรวจด้วยคณิตศาสตร์เทียบ Java ทุกจุด ส่วน first person ใช้กรอบของ java2bedrock ซึ่งได้จากการใช้งานจริง ไม่ใช่การคำนวณกล้อง
ตัวตรวจเก่าสองตัวล้มก่อนรอบนี้อยู่แล้ว: `evergarden/tests/check_packs.py` (นับ mapping 41 แต่มี 59) และ
`advance-magic/tests/check_packs.py` / `check_legendary_assets.py` (จำนวนไม้เท้าและเท็กซ์เจอร์เวทธาตุ) ไม่เกี่ยวกับอาวุธ

ต้นฉบับก่อนรีโมเดลสำรองไว้ที่ `output/weapon-remodel-20261023/before/`
