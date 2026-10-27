# ชุดเกราะเซราฟ (Seraph Armor)

Advance Magic **1.23.0-armor-combat** · แพ็ก Bedrock **2.21.0**

[ใส่บนตัว](../previews/seraph-armor/seraph-turnaround.png) ·
[หมวก 3D](../previews/seraph-armor/seraph-helm.png) ·
[เท็กซ์เจอร์และไอคอน](../previews/seraph-armor/seraph-textures-icons.png)

ภาพเรนเดอร์จากกล่องและเท็กซ์เจอร์ที่ส่งออกจริง ไม่ใช่ภาพจับหน้าจอเกม

## แนวคิด

ใส่ได้เหมือนเกราะ vanilla ตัวเกมของผู้เล่นวาดเกราะเอง ไม่มี entity ลอยตาม

| ชิ้น | ฐาน | Java | Bedrock (Geyser) |
| --- | --- | --- | --- |
| Seraph Helm | netherite_helmet | `equippable` ช่องหัว **ไม่มี asset** → ตัวเกมวาดโมเดลไอเท็ม 3D บนหัว (เขาทอง หงอนมงกุฎ หน้ากากตาเรือง) | attachable ผูกกับกระดูก `head` ใช้ลูกบาศก์ชุดเดียวกัน |
| Seraph Cuirass | netherite_chestplate | `equippable` asset `advance_magic:seraph` ชั้น humanoid | attachable กล่องเกราะ body/แขน |
| Seraph Greaves | netherite_leggings | ชั้น humanoid_leggings | attachable body/ขา |
| Seraph Sabatons | netherite_boots | ชั้น humanoid | attachable ขา |

- เท็กซ์เจอร์เกราะ 256×128 (ละเอียด 4 เท่าของผัง 64×32 ของ vanilla) ใช้ไฟล์เดียวกันทั้ง Java และ Bedrock
- หมวกปั้นให้ครอบชั้นหมวก/ผมของสกินผู้เล่นได้ทั้งหมด (ใหญ่กว่าหัว 0.5 px ทุกด้าน)
- ค่าเกราะและ toughness 1.5 เท่าเนเธอไรต์ มีความทนทานและพังได้
- ครบชุด: ดาเมจอันเดท ×4 / ศัตรูอื่น ×2 แยกกัน และอันเดทไม่ไล่ผู้สวม — ดู [กติกาปัจจุบัน](armor-combat.md)
- Geyser mapping ระบุ `minecraft:equippable` และ `protection_value` ผู้เล่น Bedrock จึงลากใส่ช่องเกราะได้และเห็นแถบเกราะ

## ใช้งาน

`/magic armor give seraph [helm|chestplate|leggings|boots|all] [player]` (สิทธิ์ `advance-magic.admin`)
แบบเดิม `/magic armor give helm Bowran` ยังใช้ได้ (ให้ชุดเซราฟ) · ชุดที่สอง: [revenant-armor.md](revenant-armor.md)

## ติดตั้ง

ติดตั้ง `dist/advance-magic.jar` และ `dist/evergarden.jar` คู่กันใน `plugins/` แล้วเปิดเซิร์ฟเวอร์ใหม่
ปลั๊กอินแตกแพ็ก Bedrock และ mapping ลง Geyser ให้เอง ต้อง restart เซิร์ฟเวอร์ (Geyser อ่าน mapping ตอนเริ่มเท่านั้น)

รุ่นระบบต่อสู้ปัจจุบันสร้างจากซอร์สทั้งสองโมดูล รายละเอียดความสามารถและผลตรวจอยู่ใน
[armor-combat.md](armor-combat.md) ต้นฉบับชุดภาพรุ่นแรกสำรองที่ `output/seraph-armor-20261023/before/`

## ต้องลองในเกม (ยังไม่ได้ทดสอบจริง)

1. Java: ใส่ครบ 4 ชิ้น เดิน/วิ่ง/ย่อ ตรวจว่าเกราะขยับตามแขนขา และหมวกไม่ทำให้ผม/หมวกของสกินโผล่
2. Bedrock: ใส่ได้จากช่องเกราะ เห็นบนตัวเองและบนผู้เล่น Java; ถือไว้ในมือแล้วเกราะต้องไม่ไปโผล่บนตัว
3. ผู้เล่น Java มองผู้เล่น Bedrock ที่ใส่ชุดนี้ และกลับกัน

จุดที่ยังไม่แน่ใจที่สุดคือฝั่ง Bedrock: attachable ผูกกระดูกตามชื่อ (`body`, `rightArm`, `head` …) และซ่อนตอนถือในมือด้วย
`c.item_slot` ถ้าเกราะไม่ขึ้นหรือขึ้นตอนถือ แก้ที่ `tools/seraph_armor_assets.py` (`ARMOR_BONES`, `WORN`) แล้วสร้างใหม่

## สร้างใหม่

```powershell
python tools/seraph_armor_assets.py           # ภาพตัวอย่าง
python tools/build_armor_sets.py --version <เวอร์ชัน Bedrock ถัดไป>   # ใส่ทุกชุดลงแพ็ก dist
python tools/build_plugin_jars.py                                     # build advance-magic.jar + evergarden.jar
```

ชุดนี้ลงทะเบียนใน `advance-magic/tools/build_packs.py` แล้ว (สร้างแพ็กเต็มก็ได้ชุดนี้ด้วย)
