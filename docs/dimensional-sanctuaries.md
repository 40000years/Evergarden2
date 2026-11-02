# Dimensional Sanctuaries — Nether & End Boss Arenas (Structure & Treasure Phase)

บริการ `DimensionalSanctuaries` แยกอิสระจาก `OverworldSanctuaries` เพื่อสร้าง **Structure ใหม่ 4 ชนิด (มิติละ 2 ชนิด)** พร้อมทางเข้า พื้นที่สำรวจ ห้องสมบัติ และลานบอสว่างขนาดใหญ่ (70+ บล็อก) โดยยังไม่ผูกกับระบบเกิดบอส

---

## 1. ภาพรวมทั้ง 4 สถานที่

| มิติ | ชนิด (`Kind`) | ชื่อแนวคิด | ขนาด `(X × Y × Z)` | ลานบอสว่าง (`Arena`) | สมบัติ | จุดเด่นทางสถาปัตยกรรมและวิธีวาง |
|---|---|---|---|---|---|---|
| **Nether** | `crown-fortress` | **ป้อมมงกุฎลาวา** (Crown Fortress) | `141 × 83 × 179` (`y: -12..66`) | รัศมี `35` (กว้าง `71` บล็อก), สูงโล่ง `42` บล็อก | `11` กล่อง (Bastion Treasure / Other) | ป้อมวงแหวน `blackstone`–`basalt` ยอดมงกุฎ 12 แฉก ล้อมด้วยคูลาวาปิดผนึก สะพานข้ามร่องลาวาเข้าสู่โถงประตูทิศใต้ ระเบียงวงแหวน 2 ชั้น และลานกลางประดับตราแผ่นทองและหินร้องไห้ อยู่ในโพรงถ้ำปิดผนึก ไม่แตะเพดาน Nether |
| **Nether** | `bone-temple` | **วิหารกระดูกวิญญาณ** (Soul Bone Temple) | `137 × 79 × 173` (`y: -9..64`) | รัศมี `34` (กว้าง `69` บล็อก), สูงโล่ง `40` บล็อก | `9` กล่อง (Bastion Treasure / Other) | โครงซี่โครงยักษ์ (`bone_block` + `quartz`) โค้งครอบวิหารหินดำ-ดินวิญญาณ ซุ้มหน้าต่างไฟวิญญาณสีฟ้า สุสานขั้นบันได ระเบียงอัฐิชั้นบน และทางเดินพิธีกรรม ให้ความสำคัญกับ `soul_sand_valley` |
| **The End** | `crystal-city` | **นครคริสตัลร้าง** (Abandoned Crystal City) | `157 × 107 × 165` (`y: -28..66`) | รัศมี `36` (กว้าง `73` บล็อก), สูงโล่ง `72` บล็อก | `10` กล่อง (End City Treasure) | ป้อม `end_stone_bricks`–`purpur` หอคริสตัลอเมทิสต์ไม่สมมาตร 7 ยอด ห้องสมุดหลังคาถล่มเปิดสู่ท้องฟ้า ห้องสมุดดาราศาสตร์ และลานพิธีเปิดโล่ง ใช้ฐานขั้นบันได 5 ชั้นเชื่อมเกาะใหญ่ พร้อมฐานหินย้อยใต้เกาะลึกไม่เกิน 28 บล็อก |
| **The End** | `void-ring` | **วิหารวงแหวนสุญญากาศ** (Void Ring Sanctuary) | `165 × 115 × 175` (`y: -34..78`) | รัศมี `35` (กว้าง `71` บล็อก), สูงโล่ง `72` บล็อก | `10` กล่อง (End City Treasure) | เกาะบริวารที่สร้างเพิ่มใกล้ขอบเกาะธรรมชาติ เชื่อมด้วยสะพานสั้นจากชานพักเกาะจริง วงแหวนวิหารชั้นนอกและเสาหินลอย 12 ต้นพร้อมวงแหวนแตกเหนือหัว ล้อมลานแอสโตรแลบกลาง |

ทุกลานมีลำดับการเดินชัดเจน: **ปากทางเข้า (`mouth`) → สะพาน/ทางเดินพิธีกรรม → โถงสำรวจและห้องสมบัติ (`landing` / galleries / vaults) → ประตูบอส (`gate`) → ลานบอสว่าง (`arena`)** ซึ่งตรวจสอบด้วย 3D Flood-Fill (`tools/dimsanct/checks.py`) ว่าเดินถึงทุกจุดและทุกกล่องสมบัติได้จริงโดยไม่ต้องทุบบล็อก

---

## 2. ภาพตัวอย่างจากการเรนเดอร์บล็อกจริง (`ODS1`)

### Nether — ป้อมมงกุฎลาวา (`crown-fortress`)
- [มุมมองภาพรวมในโพรงถ้ำ (Exterior Overview)](../previews/dimensional-sanctuaries/crown_fortress-exterior.png)
- [สะพานข้ามคูลาวาเข้าสู่โถงประตู (Lava Moat Bridge)](../previews/dimensional-sanctuaries/crown_fortress-bridge.png)
- [โถงประตูและบันไดขึ้นระเบียงชั้นบน (Gatehouse & Gallery Stairs)](../previews/dimensional-sanctuaries/crown_fortress-hall.png)
- [ลานบอสว่าง 71 บล็อก (Boss Court)](../previews/dimensional-sanctuaries/crown_fortress-court.png)

### Nether — วิหารกระดูกวิญญาณ (`bone-temple`)
- [มุมมองภาพรวมโครงซี่โครงยักษ์ (Exterior Overview)](../previews/dimensional-sanctuaries/bone_temple-exterior.png)
- [ทางเดินพิธีกรรมเข้าสู่วิหาร (Processional Approach)](../previews/dimensional-sanctuaries/bone_temple-approach.png)
- [ห้องเก็บอัฐิและสุสานด้านข้าง (Ossuary & Catacombs)](../previews/dimensional-sanctuaries/bone_temple-ossuary.png)
- [ลานบอสว่างใต้ซี่โครงยักษ์ (Boss Court)](../previews/dimensional-sanctuaries/bone_temple-court.png)

### The End — นครคริสตัลร้าง (`crystal-city`)
- [มุมมองทางอากาศและหอคริสตัลไม่สมมาตร (Aerial Overview)](../previews/dimensional-sanctuaries/crystal_city-exterior.png)
- [ถนนพิธีกรรมและซุ้มประตู (Processional Avenue & Gatehouse)](../previews/dimensional-sanctuaries/crystal_city-approach.png)
- [ห้องสมุดแตกพังเปิดสู่ท้องฟ้า (Ruined Grand Library)](../previews/dimensional-sanctuaries/crystal_city-library.png)
- [ลานพิธีบอสเปิดโล่ง 73 บล็อก (Open-Sky Ritual Boss Court)](../previews/dimensional-sanctuaries/crystal_city-court.png)

### The End — วิหารวงแหวนสุญญากาศ (`void-ring`)
- [มุมมองเกาะบริวาร เสาหินลอย และฐานใต้เกาะ (Aerial Overview)](../previews/dimensional-sanctuaries/void_ring-exterior.png)
- [สะพานเชื่อมจากขอบเกาะธรรมชาติ (Entrance Bridge)](../previews/dimensional-sanctuaries/void_ring-bridge.png)
- [ระเบียงวงแหวนและแท่นสมบัติ (Ring Cloister & Vault)](../previews/dimensional-sanctuaries/void_ring-cloister.png)
- [ลานบอสแอสโตรแลบและวงแหวนแตกเหนือหัว (Boss Court)](../previews/dimensional-sanctuaries/void_ring-court.png)

---

## 3. ระบบวางโครงสร้างและการแก้บทเรียนจาก `/sanctuary` เดิม

1. **แยกตาราง Region ต่อชนิด และสุ่มหาหลายจุดใน Region (`DimensionalSanctuaries.Placement.plan`)**:
   - Nether ใช้ region ขนาด `2048 × 2048` บล็อก, The End ใช้ `3072 × 3072` บล็อก โดยแต่ละชนิดมี offset ของตารางตัวเองจาก seed
   - สุ่มตรวจสูงสุด `72` จุดต่อ region เพื่อหาตำแหน่งที่ผ่านเกณฑ์ภูมิประเทศจริง แล้วเลือกจุดที่ได้คะแนนภูมิประเทศ + ไบโอมดีที่สุด (ไบโอมช่วยเพิ่มคะแนนบรรยากาศโดยไม่บังคับทั้งอาณาเขตให้เป็นไบโอมเดียวจนเกิดยาก)
2. **ตรวจ X/Y/Z และทิศทางทางเข้าจาก Base Terrain จริงโดยไม่โหลด Chunk (`NoiseProbe.java`)**:
   - อ่าน `ChunkGenerator.getBaseColumn` จาก seed โดยตรง
   - **Nether (`fitNether`)**: เลือกระดับ `Y` (`36..52`) ที่ตัวอาคารและโพรงถ้ำอยู่ต่ำกว่าเพดาน Nether เสมอ (`y + top < 116`) และสูงกว่าชั้น bedrock ล่าง (`y + bottom >= 6`) พร้อมตรวจสัดส่วนโพรงอากาศ พื้นรองรับ และหลีกเลี่ยงจุดที่จมทะเลลาวา
   - **The End (`fitEnd`)**:
     - `crystal-city` เลือกเกาะใหญ่ที่มีพื้นที่รองรับ (`coverage >= 0.50`) และวางระดับพื้น `y = 0` ของอาคารให้ตรงกับค่ามัธยฐาน (`median`) ของผิวเกาะจริง
     - `void-ring` เลือกบริเวณขอบเกาะธรรมชาติ (`coverage >= 0.25`) และหมุนทิศทางทางเข้า (`rotation`) ให้ปลายสะพาน (`mouth`) เชื่อมเข้ากับพื้นหินของเกาะธรรมชาติเสมอ
3. **เคลียร์เฉพาะปริมาตรที่อาคารต้องใช้ (`ODS1` Dense Volume)**:
   - บล็อกรหัส `0` (`ods:keep`) คงภูมิประเทศธรรมชาติไว้ทั้งหมด
   - Nether ใช้เปลือกโพรงถ้ำหนา 3 บล็อก (`shell`) ปิดกั้นลาวาจากภายนอก และไม่ลบหิน/bedrock เหนือโพรงถ้ำ
   - The End ออกแบบรูปทรงหินย้อยใต้เกาะไว้ในตัวโมเดล (`bottom = -28` ถึง `-34`) ไม่ต่อเสายาวลงเหว
4. **หลบโครงสร้าง Vanilla ประจำมิติ (`VanillaDimensionStructures.java`)**:
   - คำนวณตำแหน่งเกิดของ **Nether Fortress / Bastion Remnant** (`spacing=27, separation=4, salt=30084232`) และ **End City** (`spacing=20, separation=11, salt=10387313` พร้อมตรวจความสูงพื้น) จาก seed โดยตรงโดยไม่โหลด chunk
   - เว้นระยะเกาะกลางมังกรและจุดวาร์ป **End Gateway** ทั้ง 20 จุด (`radius >= 1250` จากจุดกำเนิดโลก)
5. **บันทึกแผนถาวร (`plugins/Evergarden/dimensional-sanctuaries.yml`) และกันพื้นที่โลกเก่า**:
   - ตอนเปิดใช้ครั้งแรก จะบันทึก region file ที่มีอยู่แล้วทั้งหมดลง `old-regions` และห้ามวางโครงสร้างทับหรือเฉียด `old-regions`
   - แผนตำแหน่งที่เลือกแล้ว (`x, y, z, rotation, seed, version`) ถูกบันทึกลง `plans.<kind>@<rx>@<rz>` ทำให้ `/sanctuary locate`, `/sanctuary tp` และ `BlockPopulator` ใช้แผนเดียวกันเสมอแม้รีสตาร์ตหรือสร้าง chunk คนละลำดับ
   - กล่องสมบัติผูกกับระบบอัปเกรดสมบัติของ `OverworldSanctuaries.prepareLoot` และ `SanctuaryLoot` โดยบันทึกสถานะลง `PersistentDataContainer` ของกล่อง ทำให้เปิดแล้วไม่สุ่มซ้ำหรือเติมซ้ำ

---

## 4. คำสั่งและการตั้งค่า

ใน `plugins/Evergarden/config.yml`:

```yaml
dimensional-sanctuaries:
  enabled: true
  worlds: []
  region-blocks:
    crown-fortress: 2048
    bone-temple: 2048
    crystal-city: 3072
    void-ring: 3072
```

คำสั่งสำหรับแอดมิน (`evergarden.admin` หรือ OP) ในมิติ Nether หรือ The End:
- `/sanctuary locate crown-fortress|bone-temple|crystal-city|void-ring [1-32]`
- `/sanctuary tp crown-fortress|bone-temple|crystal-city|void-ring [1-32]`

---

## 5. การสร้างไฟล์ใหม่และทดสอบบน Paper 26.3

1. สร้างไฟล์โมเดล `.ods` และภาพเรนเดอร์ทั้ง 16 มุมมอง:
   ```text
   python tools/dimsanct/build_assets.py
   ```
2. คอมไพล์และประกอบ Candidate JAR (`output/dimensional-sanctuaries-20261029/evergarden.jar`):
   ```text
   python tools/build_dimensional_sanctuaries.py
   ```
3. รันชุดทดสอบ Paper 26.3 แบบแยกสภาพแวดล้อม (2 รอบบูต):
   ```text
   python tests/dimensional-sanctuaries/run.py --jar output/dimensional-sanctuaries-20261029/evergarden.jar --old-world output/nether-arena-probe-isolated-20261029/arena_probe --server output/dimsanct-probe-20261029
   ```
