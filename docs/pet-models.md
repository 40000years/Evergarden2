# โมเดลสัตว์เลี้ยง Evergarden · e2.104

ชุดนี้สร้างตามรายการที่แนบครบ **17 ชนิด × 3 วัย = 51 โมเดล**: ร่างเด็ก (`baby`), ร่างกลาง (`medium`) และร่างโตเต็มวัย (`adult`) แยกเป็นไฟล์ Blockbench `.bbmodel` ที่แก้ไขต่อได้ แต่ละไฟล์มีท่า `idle`, `walk`, `fly`, `sit`, `greet` ใช้กับ ModelEngine R4 สำหรับ Java Edition

งานรอบนี้เป็นโมเดลและคำสั่งแสดงตัวอย่างสำหรับแอดมิน ยังไม่มี Passive, Active, การขี่, ระบบเลี้ยง หรือวิวัฒนาการ การสั่ง `fly` เป็นการแสดงแอนิเมชันของโมเดลเท่านั้น

## ติดตั้งและเปิดดู

1. สำรองและแทนที่ `plugins/evergarden.jar` ด้วย [dist/evergarden.jar](../dist/evergarden.jar) เวอร์ชัน `3.0.0-e2.104-model-restore` แล้วรีสตาร์ตเซิร์ฟเวอร์ที่มี ModelEngine R4
2. ปลั๊กอินเก็บไฟล์ `everpet_<id>_<stage>.bbmodel` ครบ 51 ไฟล์ใน `plugins/Evergarden/modelengine/pets/library/` ค่าเริ่มต้น **ยังไม่นำสัตว์เลี้ยงเข้า ModelEngine** เพื่อเหลือพื้นที่ให้โมเดลบอสและ `internal_fire` เลือกชนิดที่ต้องการใน `pets.model-preview.install-species` ตามตัวอย่างด้านล่าง แล้วรีสตาร์ตและรอ ModelEngine สร้าง resource pack ให้เสร็จ
3. เปิด `world-boss.visuals.pack.enabled: true` และ **เว้น `world-boss.visuals.pack.url` ว่าง** เพื่อให้ HTTP host ภายในของ boss pack ส่งทรัพยากร ModelEngine ผ่านระบบ resource pack เดิมของเซิร์ฟเวอร์ ตั้งค่า public host/URL และเปิดพอร์ตให้ผู้เล่นเข้าถึงได้ แล้วให้ผู้เล่น Java ยอมรับ pack นี้ ปัจจุบัน preview ต้องใช้ HTTP host ภายในนี้ การตั้ง `world-boss.visuals.pack.url` เป็น CDN ภายนอกจะทำให้ backend ไม่พร้อมและไม่เสกตัวอย่าง
4. เข้าเกมด้วย OP หรือสิทธิ์ `evergarden.admin` / `voidscape.admin` แล้วใช้คำสั่งด้านล่าง `/evergarden pets list` จะแสดงสถานะนำเข้า จำนวนโมเดลที่พร้อม และ ID ทุกตัว

ค่าเริ่มต้นและตัวอย่างการเลือกหนึ่งชนิด:

```yaml
pets:
  model-preview:
    enabled: true
    install-species: [abyssal_manta] # ค่าเริ่มต้นเป็น []
    install-all-models: false
```

หนึ่งชนิดนำเข้าทั้งสามวัย ดังนั้นตัวอย่างนี้จะเพิ่มโมเดล Abyssal Manta 3 ไฟล์ใน `plugins/ModelEngine/blueprints/` และเปิดใช้ `/evergarden pets growth abyssal_manta` ได้ การเลือกชนิดอื่นให้ใช้ ID จากตารางด้านล่าง เปิด `install-all-models: true` เฉพาะเมื่อ ModelEngine รองรับทั้ง 51 โมเดลพร้อมบอสและโมเดลภายในได้ หลังเปลี่ยนรายการนำเข้าให้รีสตาร์ตเซิร์ฟเวอร์

เมื่ออัปเกรดจาก e2.102 หรือ e2.103 ที่เคยนำเข้าครบ 51 ไฟล์ ปลั๊กอินจัดเก็บเฉพาะไฟล์สัตว์เลี้ยงที่ตนจัดการและไม่ได้เลือกใช้งานออกจากโฟลเดอร์สแกนตั้งแต่ก่อน ModelEngine เริ่มนำเข้า โดยตรวจสำเนาให้ตรงทุกไบต์ก่อนนำไฟล์ active ออก โมเดลบอสและไฟล์สัตว์เลี้ยงที่แอดมินแก้เองจะถูกเก็บรักษาไว้ รายละเอียดการแก้บอสแสดงเป็นโครงกระดูกดำอยู่ใน [model-restoration.md](model-restoration.md)

สามารถเปิดไฟล์ในคลังด้วย Blockbench หรือดาวน์โหลดต้นฉบับจาก [dist/evergarden-pet-models.zip](../dist/evergarden-pet-models.zip) หากนำเข้าเองให้เลือกเฉพาะไฟล์ที่ต้องใช้และเหลือพื้นที่สำหรับบอสกับโมเดลภายใน ขั้นตอนนำเข้าตรงกับ [เอกสาร Importing a Model ของ ModelEngine](https://wiki.mythiccraft.io/modelengine/Modeling/Importing-a-Model)

## คำสั่งแอดมิน

```text
/evergarden pets list
/evergarden pets spawn aetherius baby
/evergarden pets spawn ignis_draconis medium
/evergarden pets spawn fenrir adult
/evergarden pets growth aetherius
/evergarden pets gallery baby
/evergarden pets gallery medium
/evergarden pets gallery adult
/evergarden pets animate idle
/evergarden pets animate walk
/evergarden pets animate fly
/evergarden pets animate sit
/evergarden pets animate greet
/evergarden pets clear
```

`spawn <id> <วัย>` เพิ่มโมเดลด้านหน้าผู้สั่ง `growth <id>` แทนที่ตัวอย่างของผู้สั่งด้วยเด็ก → กลาง → โตเต็มวัย วางห่างกัน 8 บล็อกสำหรับเปรียบเทียบ ต้องมีพื้นโล่งประมาณ 24×12 บล็อก `gallery <วัย>` แทนที่ตัวอย่างด้วยสัตว์ครบ 17 ชนิดในตารางห่างกัน 8 บล็อก ต้องมีพื้นโล่งประมาณ 40×40 บล็อก ทั้งสองคำสั่งตรวจพื้นที่และโมเดลก่อนล้างตัวอย่างเดิม

คำสั่งเสกใช้ได้เมื่อ ModelEngine นำเข้าชนิดนั้นแล้ว ตัวอย่างคำสั่งข้างต้นจึงต้องเลือกชนิดที่เกี่ยวข้องใน config ก่อน ส่วน `gallery` ต้องมีครบทั้ง 17 ชนิด จึงต้องเปิดชุดทั้งหมดบน ModelEngine ที่รองรับจำนวนโมเดลเพียงพอ

`animate` เปลี่ยนท่าให้ตัวอย่างทุกตัวของผู้สั่ง ส่วน `clear` ล้างเฉพาะตัวอย่างของผู้สั่ง ไม่มีผลกับตัวอย่างของแอดมินคนอื่น คำสั่งมี Tab completion สำหรับ ID วัย และท่า คอนโซลใช้ `list` ได้ ส่วนการเสกต้องสั่งจากผู้เล่นในเกม

| ID | สัตว์เลี้ยง |
|---|---|
| `aetherius` | Aetherius · ยูนิคอร์นดาราจักร |
| `ignis_draconis` | Ignis Draconis · มังกรเพลิงบรรพกาล |
| `zephyros` | Zephyros · กริฟฟินวายุอัสนี |
| `solaris` | Solaris · ฟีนิกซ์สุริยะนิรันดร์ |
| `fenrir` | Fenrir · หมาป่าทมิฬกลืนมิติ |
| `arcane_nine_tails` | Arcane Nine-Tails · จิ้งจอกเก้าหางมนตรา |
| `frost_wyvern` | Frost Wyvern · ไวเวิร์นเกล็ดน้ำแข็ง |
| `astral_owl` | Astral Owl · นกฮูกปราชญ์ดารา |
| `crimson_cerberus` | Crimson Cerberus · เซอร์เบอรัสสามหัว |
| `shadow_panther` | Shadow Panther · เสือดำเขี้ยวเงา |
| `blood_gargoyle` | Blood Gargoyle · การ์กอยล์โลหิต |
| `runic_golem` | Runic Golem · โกเลมศิลาจารึก |
| `world_tree_entling` | World-Tree Entling · ภูตพฤกษาโลก |
| `crystal_tortoise` | Crystal Tortoise · เต่ามรกตปราการ |
| `abyssal_manta` | Abyssal Manta · ปลากระเบนมิติ |
| `alchemist_mimic` | Alchemist Mimic · หีบสมบัติจอมตะกละ |
| `chrono_fairy` | Chrono Fairy · แฟรี่กาลเวลา |

## อายุและการล้างตัวอย่าง

```yaml
pets:
  model-preview:
    enabled: true
    lifetime-seconds: 600
    max-per-admin: 18
    max-distance: 96
```

ตัวอย่างอยู่ได้ 10 นาทีเป็นค่าเริ่มต้น จำกัดแอดมินละ 18 ตัว และล้างเมื่อออกเกม เปลี่ยนโลก ตาย เสียสิทธิ์แอดมิน เดินห่างเกิน 96 บล็อก พื้นที่ unload ปิด ModelEngine หรือปิดปลั๊กอิน การเปลี่ยนค่าตัวอย่างเหล่านี้ใช้ `/evergarden reload` ซึ่งล้างตัวอย่างก่อนเปิด backend ใหม่ ส่วนการเปลี่ยน `install-species` หรือ `install-all-models` ให้รีสตาร์ตเพื่อให้ ModelEngine นำเข้าชุดใหม่และสร้าง pack ให้ครบ

ตัวรับโมเดลไม่มี AI ไม่มีแรงโน้มถ่วง ไม่มี collision รับหรือสร้างความเสียหายไม่ได้ ไม่เก็บไอเทม และไม่บันทึกตัวอย่างลงโลก ปลั๊กอินจะซ่อนตัวรับก็ต่อเมื่อผูกโมเดลสำเร็จ หากไม่มี ModelEngine หรือยังนำเข้าไม่เสร็จ จะแจ้งสถานะและไม่ทิ้งม็อบล่องหนไว้

ไฟล์โมเดลที่แอดมินแก้เองจะไม่ถูกเขียนทับ อัปเดตได้เฉพาะไฟล์ที่ตรงกับ SHA-256 ที่ปลั๊กอินบันทึกไว้ เมื่ออัปเดตไฟล์ active จะสำรองไฟล์เดิมใน `plugins/Evergarden/modelengine/pets/backups/` มีสำเนา marker ในโฟลเดอร์ ModelEngine เพื่อคงข้อมูลเจ้าของไฟล์แม้ติดตั้งโฟลเดอร์ Evergarden ใหม่ หากจัดเก็บ active รุ่นเก่าที่ข้อมูลต่างจากไฟล์ในคลัง จะเก็บสำเนารุ่นเก่าไว้ใน `plugins/Evergarden/modelengine/pets/library/previous/` ก่อนนำออกจากโฟลเดอร์สแกน ไฟล์ active ที่แก้เองและยังอยู่จะใช้ช่องนำเข้าของ ModelEngine ต่อไป โดยมีข้อความแจ้งตำแหน่งใน console

## ขอบเขตและการตรวจสอบ

โมเดลชุดนี้รองรับ ModelEngine บน **Java Edition** การ preview จากผู้เล่น Bedrock จะแจ้งข้อจำกัดก่อนสร้างตัวอย่าง ชุดนี้ยังไม่มี Geyser custom entity bridge สำหรับสัตว์เลี้ยง

การเปิดสัตว์ครบ 51 โมเดลพร้อมบอสเดิมและใช้แกลเลอรีครบ 17 ชนิด ต้องใช้ ModelEngine รุ่นเต็มที่ไม่มีข้อจำกัดจำนวนโมเดล รุ่น e2.104 จึงเก็บครบในคลังและนำเข้าเฉพาะชนิดที่เลือก ค่าเริ่มต้น `install-species: []` และ `install-all-models: false` ไม่เปิดทั้งชุด ถ้านำเข้าโมเดลบางตัวไม่ครบ คำสั่งจะแจ้งว่าไม่พบโมเดลและเก็บตัวอย่างเดิมไว้

การตรวจรุ่นปัจจุบัน e2.104 จำลองไฟล์สัตว์เลี้ยง 51 แบบที่ค้างจากรุ่นก่อน แล้วทดสอบการเริ่มเซิร์ฟเวอร์ตามปกติสองกรณี: ไม่เลือกสัตว์เลี้ยง **209 checks** และเลือก Abyssal Manta ทั้งสามวัย **246 checks** รวม **455 native checks** พร้อม **13 filesystem checks** สำหรับการเก็บรักษาข้อมูล รายงานและ SHA-256 ของ JAR ปัจจุบันอยู่ใน [model-restoration.md](model-restoration.md)

### ผลตรวจย้อนหลังของชุดโมเดล e2.102

ผลด้านล่างเป็นการตรวจชุดโมเดลเมื่อสร้าง e2.102 ไม่ใช่ผลการเปิดทั้งชุดพร้อมบอสในรุ่น e2.104 รุ่นทดลอง R4.1.1 ที่ใช้ตรวจนำเข้า custom model ได้จำกัด จึงทดสอบเป็น 6 ชุด ชุดละไม่เกิน 9 สัตว์เลี้ยงและ Arkhaia 1 โมเดล โดยเหลือที่สำหรับโมเดลภายในของ ModelEngine และย้ายไฟล์อื่นออกจากโฟลเดอร์ blueprints ชั่วคราวในเซิร์ฟเวอร์ทดสอบแยก แล้วใช้คำสั่ง reload ปกติ ไม่มีการแก้ปลั๊กอินหรือข้อจำกัดสิทธิ์ใช้งาน

ภาพและตัวดูโมเดล: [previews/pet-models/index.html](../previews/pet-models/index.html) การตรวจโครงสร้างไฟล์และ native probe ใช้กับ Paper + ModelEngine จริง โดย `tests/model-engine/PetModelsNativeProbe.java` ตรวจการนำเข้าครบ 51 โมเดลใน 6 ชุด ท่าทั้งห้า การเสกผ่านคำสั่งจริง สิทธิ์ การรับส่งความเสียหาย การจำกัดจำนวน เปรียบเทียบสามวัย การปฏิเสธแกลเลอรีเมื่อมีโมเดลไม่ครบ และการล้างตามวงจรชีวิต ส่วน `PetBlueprintChecks.java` ตรวจการสำรองและรักษาไฟล์ที่แอดมินแก้ การแสดงทั้ง 51 โมเดลพร้อมกันและแกลเลอรีครบ 17 ชนิดยังไม่ได้ตรวจบน ModelEngine รุ่นเต็ม

```text
python evergarden/tools/pet_model_engine.py
python tools/preview_pet_models.py --gifs
python tests/model-engine/check_pet_models.py
python tools/try_model_engine.py --pets --source-server <isolated-source-server> --plugin <ModelEngine.jar> --garden-jar <evergarden.jar> --test-directory <new-isolated-test-directory>
```

ผลตรวจโครงสร้างไฟล์ชุดสุดท้าย: **PASS 174,789 checks** ครอบคลุม 51 โมเดล และ 255 แอนิเมชัน ผลตรวจการรักษาไฟล์โมเดลที่แอดมินแก้และการสำรอง: **PASS 18 checks**

ผลทดสอบบน Paper 26.3 + ModelEngine R4.1.1 จริง: **PASS 1,923 checks** ครบ 51 โมเดลใน 6 ชุดตามข้อจำกัดรุ่นทดลอง รวมท่าทั้ง 255 ท่า การเสกในโลก Evergarden จริง และการล้าง registry ของ ModelEngine หลังปิดตัวอย่าง รายงานฉบับเต็ม: [model-engine-result.txt](../output/pet-models-native-final-batches-v7-20261021/model-engine-result.txt)

JAR e2.102 ที่ใช้กับผลตรวจย้อนหลังนี้คือ `output/pet-models-build-final-v4-20261021/evergarden.jar` ตรงกับ JAR ในเซิร์ฟเวอร์ทดสอบรอบนั้นทุกไบต์ SHA-256: `4567004812aea704165887397975ab762d45dbc54435253be81aededf46c0358` ส่วนไฟล์ติดตั้งปัจจุบันเป็น e2.104 ตามขั้นตอนต้นเอกสาร

ผลทดสอบอยู่ใน `model-engine-result.txt` ของโฟลเดอร์ทดสอบ การตรวจนี้ไม่ใช่การยืนยันภาพจากผู้เล่น Minecraft ที่เชื่อมต่อจริง และไม่ได้ปรับเซิร์ฟเวอร์ที่กำลังใช้งาน
