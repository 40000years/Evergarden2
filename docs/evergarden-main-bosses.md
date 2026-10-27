# บอสหลัก Evergarden — เฟส 1: โมเดล ท่าทาง และออร่า

อัปเดต 22 ตุลาคม 2026 · โมเดล `arkhaia-dreadrose-sovereign-v1` และ `seraphon-gilded-verdict-v1` · Bedrock pack ≥ 3.45.0

เฟสนี้ทำโมเดลใหม่ทั้งหมดของบอสหลัก **Arkhaia** และ **Seraphon** ด้วย ModelEngine R4 (Java) และ Geyser custom entity (Bedrock) จากไฟล์ `.bbmodel` ชุดเดียวกัน พร้อมแอนิเมชันและออร่า ความสามารถ/สกิลใหม่ยังไม่อยู่ในเฟสนี้ ไฟต์ Arkhaia เดิมยังใช้ AI ดาเมจ และเวลาโจมตีเดิมทุกอย่าง แต่เปลี่ยนเป็นโมเดลใหม่

Seraphon ตัวนี้เป็นบอสคนละตัวกับ Seraph พ่อค้าที่ Spawn Evergarden (โมเดล Seraph ไม่ถูกแก้ไข)

ภาพรีวิว (เรนเดอร์ออฟไลน์จากไฟล์โมเดลจริง ไม่ใช่ภาพในเกม): [แกลเลอรี](../previews/evergarden-bosses/index.html) · [ภาพคู่](../previews/evergarden-bosses/evergarden-bosses-lineup.png) · [Arkhaia เคลื่อนไหว](../previews/evergarden-bosses/arkhaia-motion.gif) · [Seraphon เคลื่อนไหว](../previews/evergarden-bosses/seraphon-motion.gif)

## Arkhaia — The Dreadrose Sovereign

จอมราชันชุดเกราะออบซิเดียนแห่งปราสาท Dreadrose · 638 ชิ้น · 47 กระดูก · 25 คลิป · สเกล 3.2 (สูงราว 7.4 บล็อก เขาถึง ~8.9 บล็อก ปีกกาง ~13 บล็อก)

- หมวกเกราะปิดหน้า ช่องตาสีขาวเรืองแสง มงกุฎเหล็กหนาม และเขาคู่โค้งรูปตัว S มีห่วงเหล็กรัด
- เกราะอกทรง V มีหัวใจ Ghostflame กลางอกและรอยแตกเรืองแสง บ่าเกราะซ้อนชั้นแบบหลังคาโกธิกพร้อมหนามและช่องระบายไฟ
- ดาบใหญ่ Thanasius' Ruin ใบหยัก ร่องอักษรรูนเรืองแสง การ์ดรูปเขาและอัญมณีกุหลาบแดง
- ปีกค้างคาว 4 กระดูกต่อข้าง (ไหล่ ศอก ข้อมือ และพัดนิ้ว) เยื่อปีกตัดขอบเว้าตามนิ้วจริง ขอบสีเลือด
- ผ้าคลุมขาด 3 ช่วงซับในแดง ธงกุหลาบด้านหลัง ผ้าหน้ากุหลาบ
- ออร่าบนโมเดล: วงหนาม "Dread Halo" ลายหน้าต่างกุหลาบหมุนอยู่หลังศีรษะ เปลวไฟวิญญาณบนบ่า และวงไฟใต้เท้า

ท่าทาง: idle · walk · backstep · hover และ windup/active/recover ของ light, heavy, rift, nova, portals, eclipse · stun · transition ความยาวทุกคลิปตรงกับ `arkhaia-combat.properties` เดิม ดาบใช้ IK: ท่าเงื้อเก็บดาบหลังมงกุฎ จุดกระทบอยู่หน้าบอส ~6.6 บล็อก สูง ~1.3 บล็อก ศีรษะทรงตัวตามโลกเพื่อให้ระบบมองผู้เล่นแม่นยำ

## Seraphon — The Gilded Verdict

ผู้พิพากษาแห่ง Church of Raphion สำหรับมหาวิหาร Seraphon (Supreme Sanctuary) · 788 ชิ้น · 68 กระดูก · 18 คลิป + aura · สเกล 3.2 ลอยตัวเหนือพื้น

- **ไม่ถืออาวุธ** ตามแผนเดิมที่มหาวิหาร Seraphon ใช้โมเดลไม่ถืออาวุธ ส่วนวิหาร Valkyrie (Temple of Blades) เก็บไว้สำหรับบอสถืออาวุธตัวที่สาม
- ปีก **6 ปีก** (บน/กลาง/ล่าง) ขนนกตัดรูปทรงซ้อนชั้น ปลายฟ้า/ขาวทอง/ทอง แขนปีกทองฝังอัญมณี
- เกราะทองลายสลัก แกนอัญมณีฟ้าพร้อมรังสี คอเสื้อทรงดอกลิลลี่ ฐานปีกรูปจานสุริยะด้านหลัง
- กระโปรงไหมหลายชั้น ผ้าหน้าสีฟ้าลายดวงอาทิตย์ แขนเสื้อห้อยตามแรงโน้มถ่วง ริบบิ้นด้านหลัง ผมทองยาว
- ออร่าบนโมเดล: รัศมีสุริยะ + วงไจโรสโคปเอียง วงโคจรเอียงรอบเอว (ล้อวงโคจรของมหาวิหาร) วงเวทใต้เท้า และลูกแก้ว Verdict ลอยเหนือฝ่ามือ

ท่าทาง: idle · walk (ร่อน) · hover · cast / smite / verdict (ท่าไม้ตาย) / wings แบบ windup-active-recover · stun · transition (ห่อปีกแล้วระเบิดเปิด) · spawn (ลงจากฟ้า)

## ออร่าและพาร์ติเคิล

- ออร่าบนโมเดลอยู่ในคลิป `aura` ที่วนแยกบนเลน 1 ของ ModelEngine ใต้คลิปต่อสู้เลน 10 คลิปต่อสู้ทุกคลิปเป็น override และ key กระดูกร่างกายครบทุกชิ้น แต่ไม่ key กระดูกออร่า วงจึงหมุนต่อเนื่องแม้เปลี่ยนท่า (ยืนยันบนเซิร์ฟเวอร์จริง) Bedrock เล่น `aura` ตลอดใน `animate`
- พาร์ติเคิลฝั่งเซิร์ฟเวอร์ (`BossAura`) ยึดกระดูกมาร์กเกอร์ที่มองไม่เห็นในโมเดล (`fx_*`) จึงตามท่าได้ทั้ง Java และ Bedrock: Arkhaia มีไฟวิญญาณลอยจากบ่า ประกายตามใบดาบ ถ่านแดงที่ปลายปีก แสงแกนอก และควันรอบเท้า · Seraphon มีแสงวิ่งรอบรัศมี ผงทอง ขนนกร่วงจากปลายปีก ประกายลูกแก้ว และละอองบนวงเวท ไฟต์จริงหรือท่าโจมตีจะเพิ่มความเข้ม
- ประกายสีใช้ JudgeAether เดิมเพื่อให้ Bedrock เห็นสีถูกต้อง
- Bedrock ใช้ `ignore_lighting` ให้สว่างเท่าฝั่ง Java (ModelEngine ตั้งแสงเต็ม) บอสจึงไม่มืดในฉากกลางคืน

## เสกดูโมเดล (แอดมิน)

- `/evergarden test` → World Boss Test → **บอสหลัก Evergarden** (ช่องกลางแถวบน) หรือช่อง Seraphon / Arkhaia เดิม
- คำสั่ง: `/evergarden boss model arkhaia|seraphon|both|clear` และ `/evergarden boss model pose showcase|idle|walk|hover|attack|spell|ultimate|stun|phase`
- เสกหน้าแอดมิน 12 บล็อก ต้องมีพื้นราบและที่ว่างสูง 10 บล็อก ไม่โจมตี ไม่มีรางวัล หันมองผู้เล่น อยู่ได้ 10 นาที

## สิ่งที่เปลี่ยนจากเดิม

- ลบโมเดลรูปปั้น `boss_arkhaia_*` / `boss_seraphon_*` และเทกเจอร์เก่าออกจากแพ็ก Java/Bedrock (74 + 74 รายการ) และ Geyser mapping 36 รายการ รวมถึงตัวสร้าง `faction_boss_assets.py` และ `arkhaia_model_engine.py` เดิม (สำรองไว้ที่ `output/evergarden-bosses-release-20261022/before/`)
- Arkhaia ไม่มีท่าสำรองแบบ ItemDisplay อีกแล้ว การอัญเชิญต้องมี ModelEngine พร้อม ส่วน Bedrock ต้องใช้ Geyser ที่มี custom entity API (2.11+)
- ความพร้อมของ ModelEngine แยกรายโมเดล: ถ้า blueprint ลูกน้องตัวใดนำเข้าไม่ได้ Arkhaia และ Seraphon ยังใช้งานได้ ลูกน้องตัวนั้นเท่านั้นที่ใช้ท่าสำรอง และ `/evergarden boss pack` แสดงชื่อไฟล์ที่เสีย
- ตัวติดตั้ง blueprint รู้จัก hash ของ Arkhaia รุ่นก่อน จึงอัปเกรดไฟล์บนเซิร์ฟเวอร์ให้เองและสำรองรุ่นเก่า Seraphon ติดตั้งด้วยระบบเดียวกัน
- ระดับสายตา Arkhaia สำหรับระบบมองผู้เล่นเปลี่ยนจาก 5.88 เป็น 6.95 บล็อกตามโมเดลที่สูงขึ้น วง halo ตกแต่งแบบเดิมเหนือหัวถูกแทนด้วยออร่าบนโมเดล วงเตือนสกิลและเครื่องหมายจุดอ่อนยังเหมือนเดิม

## สร้างใหม่และตรวจสอบ

```powershell
python evergarden/tools/arkhaia_sovereign.py      # arkhaia.bbmodel + manifest + atlas
python evergarden/tools/seraphon_verdict.py       # seraphon.bbmodel + manifest + atlas
python tools/update_evergarden_bosses.py          # ส่งออก Bedrock native entity ลงแพ็ก (ไม่ลดเวอร์ชันแพ็กเดิม)
python tools/preview_evergarden_bosses.py         # ภาพรีวิว
python tests/model-engine/check_evergarden_bosses.py
python tests/model-engine/check_bedrock_facing.py
python tools/try_model_engine.py --source-server <Paper> --plugin <ModelEngine-4.1.1.jar> --garden-jar <evergarden.jar> --arkhaia --test-directory <ใหม่>
python tools/try_model_engine.py ... --boss-readiness --test-directory <ใหม่>
```

ผลตรวจ: ตรวจไฟล์ออฟไลน์ 34,269 รายการ (สัญญากระดูก/คลิปกับตาราง Java, กติกาเลน ModelEngine, geometry/UV, ชิ้นตัดโปร่ง, Bedrock ตรงกับ Java, ไม่จมพื้น, จุดกระทบดาบ, Seraphon ลอย) · ถอดรหัส Bedrock อิสระ 34 รายการ · บนเซิร์ฟเวอร์ Paper 26.3 + ModelEngine R4.1.1 + Geyser + Floodgate + LevelledMobs แยกจากเซิร์ฟเวอร์จริง ผ่าน 339 รายการ (ไฟต์ Arkhaia จริง ทิศ การมอง ดาบ ร่ายเวท ออร่าหมุนข้ามคลิป showcase และเมนู Bedrock entity ลูกน้องหกตัว และการเก็บกวาด) และทดสอบความทนทานเมื่อ blueprint เสีย 7 รายการ ยังไม่ได้ตรวจภาพบนไคลเอนต์จริงหรือวัด FPS

หมายเหตุ: ไฟล์ `modelengine/rivers/arkhaia_malefeasance.bbmodel` ในโฟลเดอร์ทำงานตอนนี้ (งาน river รุ่นใหม่จากอีกเซสชัน) นำเข้า ModelEngine ไม่ได้ (`hitbox` มี `from` ไม่ใช่อาร์เรย์) การทดสอบข้างต้นจึงใช้ river รุ่นที่ใช้งานได้ล่าสุด

## เฟสถัดไป

- สกิลและกลไกใหม่ของ Arkhaia / Seraphon โดยใช้คลิปที่เตรียมไว้
- โมเดลบอสตัวที่สาม Valkyrie (ถืออาวุธ) สำหรับ Temple of Blades
- ตรวจภาพบนไคลเอนต์ Java/Bedrock จริงและปรับความเข้มของพาร์ติเคิล
