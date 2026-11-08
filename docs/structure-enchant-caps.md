# เพดานเอนชานต์ในหีบโครงสร้างบอส (e2.176)

Release: **Evergarden `3.0.0-e2.176-structure-enchant-caps`** · ต่อจาก e2.174 · แพ็กทรัพยากรไม่เปลี่ยน

หีบ 8 ใบในปราสาท Arkhaia (Dreadrose Castle), Seraphon Valkyrie (Temple of Blades) และ Seraphon (Supreme Sanctuary)
เคยให้อุปกรณ์ระดับเดียวกับ Vault Tier IV–V (VI–VIII) และติดมาหลายเอนชานต์ในชิ้นเดียว ฟรีโดยไม่ต้องใช้กุญแจ
รุ่นนี้ใส่เพดานระดับเฉพาะหีบโครงสร้าง จำนวนชิ้น ชนิดของ และโอกาส Mending เหมือนเดิมทุกอย่าง

## กติกา

- **main** = เอนชานต์ตัวแรกของแต่ละโปรไฟล์ใน `equipment.entries` (Sharpness, Protection, Efficiency, Power, Impaling, Density ฯลฯ)
- **others** = เอนชานต์อื่นทั้งหมดในชิ้นนั้น
- ระดับที่ได้ = ค่าที่สุ่มตามโปรไฟล์ แต่ไม่เกิน `ค่าสูงสุดของเกม + เพดาน`

| โครงสร้าง | main | others |
|---|---|---|
| Arkhaia — Dreadrose Castle (`arkhaia-castle`) | +1 | +0 |
| Seraphon — Supreme Sanctuary (`seraphon-sanctum`) | +1 | +0 |
| Seraphon Valkyrie — Temple of Blades (`seraphon-valkyrie`, ไม่มีบอส) | +0 | +0 |

| เอนชานต์ | เดิม | Arkhaia / Seraphon | Valkyrie |
|---|---|---|---|
| Sharpness / Efficiency / Power / Impaling | VI–VIII | VI | V |
| Protection | VI–VIII | V | IV |
| Density | V–VII | V–VI | V |
| Fortune (อีเต้อ) / Looting / Unbreaking | สูงสุด V | III | III |
| Feather Falling | V–VII | IV | IV |
| หนังสือ Sharpness / Protection / Power | VI–VIII | VI / V / VI | V / IV / V |

ระดับ VII–VIII ยังได้จาก Vault Tier III–V, ทั่งบอส และรางวัลบอสมิติเหมือนเดิม เพราะระบบเหล่านั้นอ่าน `equipment.entries` ช่วงเต็มที่ไม่ได้ถูกแก้

## ตั้งค่า

`plugins/Evergarden/boss-treasure.yml`:

```yaml
structure-level-caps:
  arkhaia-castle: {main: 1, others: 0}
  seraphon-valkyrie: {main: 0, others: 0}
  seraphon-sanctum: {main: 1, others: 0}
```

เซิร์ฟเวอร์ที่มีไฟล์อยู่แล้ว ปลั๊กอินจะเพิ่มหัวข้อนี้ให้เองครั้งแรกที่เปิด (ค่าอื่นในไฟล์ไม่เปลี่ยน) แก้แล้วรีสตาร์ต
ใส่ค่าสูง เช่น 32 เพื่อปิดเพดานของโครงสร้างนั้น

- มีผลกับหีบที่ถูกสร้างหลังอัปเดตเท่านั้น หีบจะถูกใส่ของตอน chunk โหลดครั้งแรก โครงสร้างที่เคยโหลดแล้วยังมีของแบบเดิม
- ของที่ผู้เล่นได้ไปแล้วไม่ถูกแก้
- เพดานคำนวณหลังสุ่มระดับ ลำดับการสุ่มของหีบจึงเหมือนเดิม หีบใบเดียวกันได้ของชนิด/จำนวน/ชนิดเอนชานต์เดิม ต่างแค่ระดับ

## สร้างและตรวจสอบ

```powershell
python tools/build_structure_enchant_caps_release.py              # candidate บน dist e2.174
python tools/build_structure_enchant_caps_release.py --validate   # Paper แยก 3 รอบ
python tools/build_structure_enchant_caps_release.py --promote
```

`--validate` รัน `evergarden/tests/StructureEnchantCapChecks.java` บน Paper 26.3-231 ที่แยกไว้ใต้ `output/structure-enchant-caps/paper`
สุ่มหีบ 3,000 ใบต่อโครงสร้างด้วย `BossLootTable` จริง:

1. JAR เดิม e2.174: บันทึก fingerprint (ชนิดของ จำนวน ชนิดเอนชานต์) ของทุกหีบ
2. Candidate บน `boss-treasure.yml` เดิมของเซิร์ฟเวอร์: ต้องเพิ่มหัวข้อ `structure-level-caps` เองโดยไม่ทำให้ส่วนอื่นหาย, ไม่มีเอนชานต์เกินเพดาน, เพดานถูกใช้ถึงจริง (Sharpness/Protection/Power/Looting), Vault Tier V ยังได้ VIII, และ fingerprint เท่ากับข้อ 1
3. Candidate หลังแก้ไฟล์ให้ Valkyrie เป็น main 2 / others 1: ต้องได้ระดับตามค่าที่แก้
