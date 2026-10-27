# สัตว์เลี้ยงตกตามพื้นและคลิกเทม — e2.154

แก้สัตว์ป่าและสัตว์เลี้ยงที่ค้างกลางอากาศหลังทุบบล็อกใต้ตัว รวมถึงการคลิกเทมที่ต้องเล็งตัวรับกระต่ายขนาดเล็กใต้โมเดล

## สิ่งที่แก้

- ตัวรับปิด native gravity เพื่อควบคุมท่าทางและการเดินเอง จึงเพิ่มการตกตาม collision shape ของพื้นทุก tick เมื่อไม่มีสิ่งรองรับ ใช้กับตอนยืนนิ่ง นั่ง ทักทาย กำลังป้อนอาหาร และโหมด stay/follow/assist
- ระบุ `RETAIN_PASSENGERS` ขณะย้ายตัวรับอย่างชัดเจน และทดสอบว่าชิ้นโมเดล Java กับ carrier Bedrock ตามการตกและการเดิน โดย Paper รุ่นที่ทดสอบรองรับการ teleport พร้อมผู้โดยสารอยู่แล้ว สาเหตุที่ค้างคือการไม่มีขั้นตอนตกในระบบควบคุมการเคลื่อนที่
- ตรวจทั้งช่วงที่เคลื่อนลงเพื่อไม่ให้ทะลุพื้น และปรับปลายก้าวให้ตรงความสูงจริงของ slab/บล็อกไม่เต็มก้อน การขึ้นลงหนึ่งบล็อกยังใช้ก้าวปกติ
- สัตว์ที่กำลังบินและตัวที่เกาะหัวเจ้าของใช้การเคลื่อนที่เดิม การเรียกตัวบินข้างเจ้าของเหนือพื้นที่ไม่มีพื้นต้องระบุสถานะบินให้ชัดเจน
- คลิกขวาที่ภาพสัตว์ได้ผ่านการยิง ray ตามชิ้นโมเดลที่ฝังใน JAR พร้อมเผื่อขอบเล็กน้อย ตรวจระยะใช้งานจริง สิ่งกีดขวาง และการแสดงผลให้ผู้เล่นคนนั้น เลือกเฉพาะสัตว์ที่เล็งถึง
- ฝั่ง Bedrock ได้ขนาดจุดแตะตามวัยแทนค่า width/height ศูนย์ บอสยังใช้ตัวรับเฉพาะของตัวเองตามเดิม
- มือรองและ event ซ้ำไม่กินอาหารซ้ำ เครื่องมือ/ไข่ที่มี plugin tag ยังใช้เส้นทางเดิม ช่วงพักป้อนอาหารแสดงเวลารอใน action bar

## การดึง resource

อ่านและทำตามระบบปัจจุบันใน [pack-mirror.md](pack-mirror.md): ส่งสามแพ็กเป็นชุดเดียว ใช้สำเนาสาธารณะที่ฝังลายนิ้วมือใน JAR ก่อน แล้วใช้เซิร์ฟเวอร์เป็น fallback พร้อมกู้แพ็กที่โหลดไม่สำเร็จแยกทีละแพ็ก

รุ่นนี้เป็นการแก้โค้ด จึงต่อจาก `e2.153-boss-balance` โดยเปลี่ยนเฉพาะ class ที่เกี่ยวกับงานนี้และเลขเวอร์ชัน คงทุก entry อื่นไว้เหมือนเดิมทุกไบต์ รวม resource packs, โมเดล, `pack-mirror.properties` และระบบส่งแพ็ก ไม่สร้างแพ็กจากไฟล์เก่าในโฟลเดอร์ module dist และไม่ต้องเผยแพร่แพ็กใหม่

## บิลด์และทดสอบ

```powershell
python tools/build_pet_grounding_release.py --base dist/evergarden-3.0.0-e2.153-boss-balance.jar --magic-base dist/advance-magic-1.34.1-bedrock-pack-repair.jar
python tools/run_pet_grounding_checks.py --magic-jar output/pet-grounding-20261026-v3/before/advance-magic.jar
python tools/build_pet_grounding_release.py --base dist/evergarden-3.0.0-e2.153-boss-balance.jar --magic-base dist/advance-magic-1.34.1-bedrock-pack-repair.jar --promote --versioned-only
```

กรณีใช้ Paper ที่เตรียมไว้แล้ว ให้ระบุ candidate และตำแหน่งเซิร์ฟทดสอบ:

```powershell
python tools/run_pet_grounding_checks.py --prepared --server output/pet-grounding-20261026/paper --magic-jar output/pet-grounding-20261026-v3/before/advance-magic.jar
python tools/build_pet_grounding_release.py --base dist/evergarden-3.0.0-e2.153-boss-balance.jar --magic-base dist/advance-magic-1.34.1-bedrock-pack-repair.jar --promote --versioned-only --validation-server output/pet-grounding-20261026/paper
```

บิลด์เก็บฐานเดิมไว้ที่ `output/pet-grounding-20261026-v3/before/` และรับเฉพาะผลทดสอบที่ hash ของ JAR ทั้งสองและ probe ตรงกัน ผ่าน **1,284 เช็ก** บน Paper แยกจากเซิร์ฟเวอร์จริง ครอบคลุมพื้นถูกทุบ การตก/ลงพื้น slab การเดินขึ้นลง การบิน/เกาะหัว จุดคลิกบนภาพสัตว์ แพ็กพร้อมแสดง ระยะ กำแพง มือรอง event ซ้ำ และข้อความช่วงพักอาหาร การวัดนี้ไม่ใช่การตรวจภาพกับไคลเอนต์ Java/Bedrock ที่เชื่อมต่อจริง

ใช้ `dist/evergarden-3.0.0-e2.154-pet-grounding.jar` แทนไฟล์บนเซิร์ฟเวอร์แล้วรีสตาร์ต ไม่ต้องเปลี่ยนแพ็กหรือไฟล์ Advance Magic สำหรับการแก้สัตว์เลี้ยงครั้งนี้ บิลด์ไม่ติดตั้งให้เซิร์ฟเวอร์จริงอัตโนมัติ
