# Flying Staff — resource pack / animation prototype

งานภาพต้นฉบับสำหรับ [แผนไม้เท้าบิน](../../../docs/flying-staff-plan.md) รุ่น `0.1.0` มีโมเดลไม้เท้าหนึ่งแบบและสถานะภาพหลายท่า ระบบเรียก ขี่ บิน และมานาถูกเพิ่มใน Advance Magic แล้ว แต่ยังไม่ได้ทดสอบภาพจาก client จริง

ไฟล์ `dist/flying-staff-*-preview.*` ในโฟลเดอร์นี้ใช้ดูต้นแบบเท่านั้น แพ็กที่ปลั๊กอินแจกจริงอยู่ใน `advance-magic/dist/` และสร้างโดย `advance-magic/tools/build_packs.py` ซึ่งรวมภาพนี้กับคทาเดิมไว้ในแพ็กเดียว

## ไฟล์

| ไฟล์/โฟลเดอร์ | หน้าที่ |
| --- | --- |
| `java/` | Java 26.2 resource pack source, format 88.0 |
| `bedrock/` | Bedrock geometry, attachables, animations และ particles |
| `geyser-mappings.json` | Mapping v2 ของไอเทมต้นแบบ ไม่แทน mapping เดิม |
| `dist/flying-staff-java-preview.zip` | Java pack สำหรับทดลอง |
| `dist/flying-staff-bedrock-preview.mcpack` | Bedrock pack สำหรับทดลอง |
| `dist/pack-hashes.json` | SHA-1, SHA-256 และขนาดของทั้งสองแพ็ก |

Source JSON และ PNG ในโฟลเดอร์ `java/`/`bedrock/` เป็นไฟล์ต้นฉบับแก้ไขได้ Geometry มี 79 cubes; Java ใช้ texture 64×64 จำนวน 16 เฟรมในแถบ 64×1024; Bedrock ใช้ palette 64×64, icon 128×128 และ particle sprite 16×16

ไม่มี assets ใน namespace `minecraft` และไม่มี entity definition ของเรือหรือผู้เล่น จึงไม่ได้แทนรูปลักษณ์ vanilla ปกติ UUID แพ็กต้นแบบแยกจากแพ็ก Advance Magic ที่เผยแพร่อยู่

## รหัสโมเดล

| Java `item_model` / Bedrock identifier | ไอเทมฐาน | หน้าที่ |
| --- | --- | --- |
| `advance_magic:flying_staff` | `blaze_rod` | ถือมือ/ไอคอน |
| `advance_magic:flying_staff_summon` | `iron_helmet` | ภาพขณะเรียก |
| `advance_magic:flying_staff_idle` | `iron_helmet` | ภาพลอยรอ |
| `advance_magic:flying_staff_flight` | `iron_helmet` | ภาพขณะขี่ |
| `advance_magic:flying_staff_dismiss` | `iron_helmet` | ภาพขณะเก็บ |

ไอเทมฐานสำหรับแสดงภาพสวมบน Armor Stand ของระบบ ไม่ให้ผู้เล่นสวมหมวกจริง ไอเทม gameplay ตรวจ tag `advance-magic:flying_staff` ของปลั๊กอิน

## Animation contract สำหรับผู้ทำระบบภายหลัง

Java ใช้ `minecraft:custom_model_data.floats[0]` เป็นหมายเลขเฟรมของ model สถานะนั้น เฟรมเปลี่ยนเฉพาะ transform ใน display context `head` และปลั๊กอินอัปเดตแล้ว:

| สถานะ | หมายเลขเฟรม | ระยะต่อเฟรม | หลังเฟรมสุดท้าย |
| --- | --- | --- | --- |
| summon | 0–6 | 2 ticks | เปลี่ยนเป็น idle ที่ tick 12 |
| idle | 0–7 | 8 ticks | วนกลับ 0 ที่ tick 64 |
| flight | 0–7 | 4 ticks | วนกลับ 0 ที่ tick 32 |
| dismiss | 0–4 | 2 ticks | ลบภาพที่ tick 8 |

ค่าที่ไม่มีหรือค่าติดลบใช้เฟรม 0 ค่าสูงสุดเกินช่วงค้างที่เฟรมสุดท้าย Java วนเฟรมผ่าน scheduler ของปลั๊กอิน แสง texture เล่นวนอัตโนมัติทุก 48 ticks และไม่ได้สร้างแสงส่องโลกจริง การเปลี่ยนเฟรมเป็นแบบขั้น ต้องทดสอบความลื่นบน client จริง

Bedrock ใช้ `animations/flying_staff.animation.json` มี 6 clips: ท่าถือ first/third person และ 4 สถานะข้างต้น attachable แต่ละตัวเรียก clip ของตัวเอง `summon` และ `dismiss` ค้างเฟรมสุดท้าย; ปลั๊กอินเปลี่ยนสถานะ/ลบภาพตามเวลา

`idle`/`flight` วนเอง มีวงแหวนหมุนและคริสตัลเต้นเล็กน้อย จังหวะเริ่มของผู้ชมที่เพิ่งโหลด entity อาจต่างกัน Java ยังไม่มีการหมุนวงแหวนแยกชิ้นในต้นแบบนี้

พาร์ติเคิล Bedrock ผูกไว้ในคลิปแล้ว: `advance_magic:flying_staff_trail` ปล่อยประมาณ 6 เม็ด/วินาทีขณะ flight และ `advance_magic:flying_staff_spark` ปล่อย 9 เม็ดตอนเรียก/เก็บ Particle มีอายุ 0.45 วินาที ไม่สั่งสร้าง entity เพิ่ม Java ได้ particle vanilla จากปลั๊กอิน ไม่ทับ texture ของเอฟเฟกต์เดิม

## จุดยึดโมเดล

โมเดล Java หันหัวไปทาง `-Z` ศูนย์กลางด้ามอยู่ `[8,8,8]` จุดนั่งที่เสนออยู่แถว `[8,9.7,10]` ด้ามยาวประมาณ 2.94 บล็อกในสเกลแสดงภาพ 1.0 ทั้งจุดนั่งและสเกลยังต้องเทียบกับผู้เล่นจริง

Bedrock geometry `flying_staff_display` ยึด bone `head`; `staff_root` อยู่ที่ `[0,24,0]` ส่วน `flying_staff_held` ใช้ item-slot binding และ root `[0,0,0]` โดยแปลงพิกัดมาจากรูปทรงเดียวกับ Java

ตัวแสดงต้องรักษา head pose ให้ตรงตามที่ออกแบบ ภายหลังต้องปรับ offset ระหว่าง entity, โมเดล และที่นั่งทั้งสอง client แอนิเมชันภาพที่ขยับได้ไม่ใช่ตำแหน่งชนบล็อกหรือที่นั่งจริง

## ทดลองภาพภายหลัง

โหลด Java ZIP เป็น resource pack ในโลกทดสอบ Java 26.2 แล้วใช้คำสั่งต่อไปนี้ได้ คำสั่งยังไม่ได้รันหรือยืนยันผลใน client:

```mcfunction
/give @s minecraft:blaze_rod[minecraft:item_model="advance_magic:flying_staff"]
/summon minecraft:armor_stand ~ ~ ~ {Tags:["eg_flying_staff_art"],Invisible:1b,NoGravity:1b,Invulnerable:1b,Marker:1b}
/item replace entity @e[type=minecraft:armor_stand,tag=eg_flying_staff_art,sort=nearest,limit=1] armor.head with minecraft:iron_helmet[minecraft:item_model="advance_magic:flying_staff_idle",minecraft:custom_model_data={floats:[0.0f]}]
```

การดูเฟรม Java ให้เปลี่ยน model และเลขใน `floats` ตามตารางข้างบน โดยคำสั่งอย่างเดียวไม่ขับเฟรมต่อเนื่อง ลบเฉพาะตัวแสดงทดลองที่ติด tag นี้เมื่อเสร็จ:

```mcfunction
/kill @e[type=minecraft:armor_stand,tag=eg_flying_staff_art]
```

Bedrock ผ่าน Geyser: ในเซิร์ฟเวอร์ทดสอบ ให้ใส่ MCPACK ใน `packs/` และ mapping นี้เป็นไฟล์ชื่อแยกใน `custom_mappings/` จากนั้น restart และรับแพ็กใหม่ การตั้ง `item_model` ให้ทำผ่าน Java/Paper console ด้วยรูปแบบคำสั่งของ Java การนำ MCPACK เข้า Bedrock standalone อย่างเดียวไม่เพิ่มไอเทม เพราะไม่มี behavior pack

เมื่อแก้ source ให้สร้าง ZIP จาก **เนื้อหาภายใน** `java/` หรือ `bedrock/` เพื่อให้ `pack.mcmeta`/`manifest.json` อยู่ที่ราก archive แล้วคำนวณ hash ใหม่ ห้ามนำ hash ต้นแบบไปใส่กับ URL แพ็ก production คนละไฟล์

## ผลตรวจ

ตรวจ JSON, พิกัดโมเดล, texture/geometry/animation references, หมายเลขเฟรม, Geyser mappings, เนื้อหา ZIP และ hash พร้อมดูภาพเรนเดอร์จาก geometry แล้ว ยังต้องตรวจ log การโหลดแพ็ก ตำแหน่งท่าถือ/สวม และเอฟเฟกต์ใน Java/Bedrock จริง

อ้างอิง: [Java 26.2 resource format](https://feedback.minecraft.net/hc/en-us/articles/46690753273997-Minecraft-Java-Edition-26-2), [Java model dispatch](https://www.minecraft.net/en-us/article/minecraft-snapshot-24w45a), [Bedrock attachables](https://learn.microsoft.com/en-us/minecraft/creator/documents/attachables?view=minecraft-bedrock-stable), [Bedrock particle components](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/particlesreference/examples/particlecomponents/particle_document?view=minecraft-bedrock-stable), [Geyser custom items](https://geysermc.org/wiki/geyser/custom-items/)
