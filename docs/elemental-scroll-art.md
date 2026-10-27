# ภาพคัมภีร์เวทชุดใหม่

เปลี่ยนภาพไอเท็มคัมภีร์ทั้ง 58 เวทเป็นม้วนกระดาษเวทมนตร์ ขอบโลหะสลัก
สายหนัง อัญมณีประจำธาตุ และภาพสัญลักษณ์เฉพาะแต่ละสกิล ขนาด 128×128 RGBA
พื้นหลังโปร่งใส ใช้ภาพเดียวกันบน Java และ Bedrock

- **Basic:** ปลอกโลหะเรียบและอัญมณีขนาดเล็ก
- **Rare:** ขอบสลัก สายหนัง และอัญมณีเพิ่ม
- **Epic:** แผ่นรองสีเข้ม ปลอกโลหะบานและอัญมณีหลายจุด
- **Legendary:** ยอดมงกุฎ ปีกโลหะและตราห้อย

แต่ละเวทมีรูปสื่อความหมาย เช่น Fireball เป็นลูกไฟ, Phoenix Rebirth เป็นนกฟีนิกซ์,
Golem Guardian เป็นโกเลมหิน และ Leviathan's Wrath เป็นมังกรน้ำ

## ดูภาพ

- [ภาพรวมแปดธาตุ](../previews/elemental-scrolls/overview.png)
- [ภาพครบ 58 เวท](../previews/elemental-scrolls/all-scrolls.png)
- [เมนูเทียบก่อน–หลัง](../previews/elemental-scrolls/before-after.png)
- [แกลเลอรีที่กรองธาตุ/ระดับและปรับขนาดได้](../previews/elemental-scrolls/index.html)

ภาพเมนูเป็นภาพจำลองจากตำแหน่งช่องจริงใน `SpellMenu` ไม่ใช่ภาพบันทึกจากเกม

## ต้นฉบับและแพ็ก

ภาพต้นฉบับสร้างด้วย built-in ImageGen เก็บทั้งแปดแอตลาส พรอมป์ต์ทั้งหมด
ขอบเขตตัดภาพ และ SHA-256 ของเท็กซ์เจอร์ไว้ใน
`advance-magic/art/elemental-scrolls/`

```powershell
python tools/prepare_elemental_scroll_art.py
python tools/elemental_spell_assets.py
python tools/build_elemental_scroll_resources.py
```

ตัวอัปเดตเฉพาะคัมภีร์เขียน PNG ทั้ง 58 ใบลงแพ็กที่มีอยู่และเพิ่ม Bedrock cache
version เป็น **2.25.0** โดยคง entry อื่นใน Java 2,410 รายการและ Bedrock 1,182 รายการ
ไว้เหมือนเดิมทุกไบต์ รหัสโมเดล Java, Bedrock atlas และ Geyser mappings ใช้เส้นทางเดิม
รายงานตรวจไฟล์อยู่ที่ `output/elemental-scroll-redesign/validation.json`

ไฟล์แพ็กอยู่ใน `advance-magic/dist/` และจะถูกฝังใน JAR ผ่านขั้นตอนสร้างปลั๊กอินเดิม
ชุดนี้ยังไม่ได้ติดตั้งหรือทดลองในเกม
