# ปรับการสร้างโลก Evergarden (e2.142)

Release `3.0.0-e2.142-worldgen` · ต่อจาก e2.141-staff-gems (รวมของ staff-gems ไว้ครบ) · ไม่มีการเปลี่ยนแพ็ก Java/Bedrock

> **ต้องสร้างโลก evergarden ใหม่ถึงจะเห็นผล** โลกเดิมใช้เลย์เอาต์ที่บันทึกไว้แล้ว (`world-layout.yml`) จึงไม่เปลี่ยน

## เปลี่ยนอะไร

1. **สิ่งก่อสร้างใหญ่ได้ที่ก่อน:** เดิมดันเจียน Sanctum (ทุก 288 บล็อก) กันพื้นที่จนบอสแทบไม่เกิดเลย ตอนนี้ดันเจียนหลบให้บอส (Arkhaia, Valkyrie, Seraphon), วิหาร Judge, วาฬฟ้า และแลนด์มาร์ก แผนที่ Seraph จึงมีบอสให้แลกมากขึ้นด้วย
2. **ไม่มีวงโล่งรอบ spawn อีกแล้ว:** ช่วง 384–776 บล็อกจาก spawn เดิมไม่มีต้นไม้หรือพืชเลย ตอนนี้ฉากของไบโอมเริ่มตรงที่ต้นไม้สวนเก่าหยุด
3. **พื้นดินมีพืชคลุมหนาขึ้น:** หญ้าทุกที่ บวกพืชเฉพาะไบโอม
   - Gardens: ทุ่งดอกไม้เปลี่ยนสีเป็นแถบ, Pink Petals, Wildflowers, พุ่มไม้
   - Ancient Forest: เฟิร์น, Leaf Litter, มอส, เห็ด, Sweet Berry, Firefly Bush
   - Lunar City: Pale Moss, Eyeblossom, ดอกลิลลี่
4. **Glacier มีชีวิตขึ้น:**
   - มีป่าสนในหุบที่ต่ำกว่าระดับ 118 ส่วนยอดเขายังเป็นทุ่งหิมะโล่ง
   - จุดสำรวจลองหาตำแหน่งได้ 4 จุดต่อช่อง และยอมพื้นเอียงได้มากขึ้น 3 บล็อก
5. **เจอไบโอมเร็วขึ้น:**
   - ขนาดไบโอม 3072 → **2048**
   - เขตสวนบังคับรอบ spawn ลดจาก 1024 เหลือราว **700** บล็อก ขอบเป็นคลื่น ไม่ใช่วงกลม

## ผลวัดจากโลกใหม่จริง (seed เดียวกัน, ±6 km)

| | ก่อน (e2.140) | หลัง (e2.142) |
|---|---|---|
| Arkhaia / Valkyrie / Seraphon | 1 / 0 / 0 | **18 / 14 / 8** |
| วิหาร Judge | 0 | **15** |
| วาฬฟ้า | 6 | **26** |
| Hanging Garden / Observatory | 10 / 5 | **17 / 12** |
| แท่นบูชา / ศาลฟื้นฟู | 13 / 8 | **28 / 27** |
| จุดสำรวจ (เล็ก + กลาง) | 193 | **396** |
| จุดสำรวจใน Glacier | 5 | **45** |
| ดันเจียน Sanctum | 1,807 | 1,653 |
| ต้นไม้ที่ 384–776 บล็อกจาก spawn | 0 | มีทุกทิศ |
| พืชคลุมดินที่เป็นดิน/หญ้า | ~1–2% | Gardens 44% · Ancient Forest 41% · Lunar City 21% |

แผนที่: `previews/world-survey/evergarden-e2.141-world.png` (ก่อนแก้: `evergarden-fresh-world.png`) · ตัวเลขเต็ม: `previews/world-survey/survey-e2.141.txt`

## ติดตั้ง (สร้างโลกใหม่)

1. หยุดเซิร์ฟเวอร์ แล้วแทน `plugins/evergarden.jar` ด้วย `dist/evergarden.jar` (sha256 `e9735a19…`)
2. ใน `plugins/Evergarden/config.yml` ตั้ง `dimension.terrain.biome-size: 2048` ไฟล์ config เดิมยังเก็บค่า 3072 ไว้ ถ้าลบบรรทัดนี้ทิ้ง ปลั๊กอินจะใช้ 2048 เอง
3. ลบโฟลเดอร์โลก evergarden และ `plugins/Evergarden/world-layout.yml`
   - ข้อมูลที่ผูกกับโลกเก่าก็ลบด้วย เช่น `seraph-sanctuary-maps.yml`
4. เปิดเซิร์ฟเวอร์ ปลั๊กอินจะสร้างเลย์เอาต์ใหม่ทั้งหมด

## ตรวจแล้ว (Paper จริง)

- `tests/arkhaia/run.py --world-survey` บนโลกใหม่ (ตามตารางข้างบน): ผ่าน ไม่มี exception
- `--sanctuary` ล้มที่ "scripted undead Sanctuary boss cannot start chasing full Seraph wearer" แต่ e2.140 ก็ล้มจุดเดียวกัน จึงไม่ใช่ผลจากรอบนี้
- jar ที่ส่งต่างจากไฟล์ที่ทดสอบแค่เลขเวอร์ชันใน plugin.yml เพราะต้องเปลี่ยนจาก e2.141 เป็น e2.142 หลังพบว่า staff-gems ใช้เลข e2.141 ไปแล้ว ไฟล์อื่นเหมือน e2.141-staff-gems ทุกไบต์
- ยังไม่ได้ลองเดินในเกมจริง

สร้างใหม่: `python tools/build_worldgen_release.py --base <evergarden.jar> [--promote]`
