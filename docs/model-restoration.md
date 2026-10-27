# คืนโมเดลบอส · e2.104

Log ของเซิร์ฟเวอร์แสดงว่า ModelEngine R4.1.1 นำเข้าโมเดลบอสเดิม 8 ไฟล์และสัตว์เลี้ยงบางส่วน รวม 12 ไฟล์ แต่ไม่ได้นำเข้า `internal_fire` ตัว renderer ของ ModelEngine เรียกโมเดลภายในนี้เมื่อผูกโมเดลทุกตัว จึงเกิด `NullPointerException` ที่ `DisplayRendererImpl$HitboxImpl.updateFireDisplay` ทั้งในสัตว์เลี้ยงและบอสแม่น้ำ ตัวรับม็อบจึงยังปรากฏเป็น Wither Skeleton สีดำ

โมเดลบอสเดิมไม่ได้หาย: เปรียบเทียบไฟล์ทั้งแปดใน JAR รุ่น e2.101, e2.102 และ e2.103 รวมถึงไฟล์ในเซิร์ฟเวอร์จริงแล้ว ตรงกันทุกไบต์ ปัญหาเริ่มจากการติดตั้งสัตว์เลี้ยงทั้ง 51 แบบเข้าโฟลเดอร์ที่ ModelEngine สแกนพร้อมกัน โดยยังไม่กันพื้นที่สำหรับโมเดลภายใน

## วิธีติดตั้งตัวแก้

1. แทนที่ `plugins/evergarden.jar` ด้วย `dist/evergarden.jar` รุ่น `3.0.0-e2.104-model-restore` แล้วรีสตาร์ตเซิร์ฟเวอร์
2. ตัวแก้จะทำงานตั้งแต่ onLoad ก่อน ModelEngine เริ่มนำเข้า ย้ายเฉพาะโมเดลสัตว์เลี้ยงที่เป็นไฟล์ที่ปลั๊กอินจัดการออกจากโฟลเดอร์สแกน เก็บครบใน `plugins/Evergarden/modelengine/pets/library/` และตรวจสำเนาให้ตรงทุกไบต์ก่อนนำไฟล์ออกจากชุดที่นำเข้า
3. ยอมรับ resource pack ที่เซิร์ฟเวอร์ส่งใหม่ หากยังใช้ pack เก่า ให้ใช้ `/evergarden boss pack resend` แล้วล้างและเสกตัวทดสอบใหม่

ไฟล์บอสทั้งแปดไม่ถูกเปลี่ยน โมเดลสัตว์เลี้ยงยังอยู่ครบใน JAR และคลัง ไฟล์ active ที่แอดมินแก้เองและไม่ตรงกับ ownership hash จะคงอยู่ พร้อมแจ้งตำแหน่งใน console

## เลือกสัตว์เลี้ยงมาแสดง

ค่าเริ่มต้นคืนพื้นที่ให้บอสก่อน:

```yaml
pets:
  model-preview:
    enabled: true
    install-species: []
    install-all-models: false
```

หากต้องการดู Abyssal Manta ให้เลือกเพียงชนิดนี้แล้วรีสตาร์ต:

```yaml
pets:
  model-preview:
    enabled: true
    install-species: [abyssal_manta]
    install-all-models: false
```

```text
/evergarden pets growth abyssal_manta
/evergarden pets spawn abyssal_manta adult
```

หนึ่งชนิดใช้สามไฟล์สำหรับสามวัย การเปิด `install-all-models: true` ตั้งใจให้ใช้กับ ModelEngine ที่รองรับจำนวนโมเดลทั้งชุดพร้อมบอสและโมเดลภายในเท่านั้น ค่าเริ่มต้นจะไม่เปิดทั้ง 51 แบบอัตโนมัติอีก

ตัวตรวจ renderer รุ่นใหม่ต้องพบ `internal_fire` และโมเดลบอสเดิมครบ ก่อนประกาศว่าพร้อม ซ่อนตัวรับหลังผูกโมเดลสำเร็จ เก็บกวาดการผูกที่ล้มเหลว และแจ้งโมเดลที่ขาดแทนการเกิด exception ซ้ำ

## การตรวจสอบ

ใช้ ModelEngine JAR ที่มี SHA-256 ตรงกับไฟล์ในเซิร์ฟเวอร์ผู้ใช้ ทดสอบในเซิร์ฟเวอร์แยกบน loopback โดยเริ่มจากไฟล์สัตว์เลี้ยง 51 แบบที่ติดตั้งค้างจากรุ่นก่อน ไม่ย้ายไฟล์ด้วย probe เพื่อช่วยให้ผ่าน การย้ายต้องมาจากโค้ดอัปเกรดของปลั๊กอินเอง

- `ModelRestoreProbe`: ตรวจคลังครบ 51 แบบโดยเทียบข้อมูลกับ JAR บอสเดิมทั้งแปดกับ `internal_fire` นำเข้าและมี assets ใน pack ที่ส่งจริง ผูก `RiverVisual` และ renderer จริง มี bone/animation และซ่อนตัวรับสำเร็จ
- ทดสอบเลือก `abyssal_manta` เพิ่มทั้งสามวัยและเล่น animation ทั้งห้า เพื่อครอบคลุมคำสั่งที่ล้มใน log เดิม
- `PetStagingChecks`: ตรวจสำเนา binary การเก็บโมเดลรุ่นก่อน การปฏิเสธ backup ที่ข้อมูลขัดกัน และการรักษาไฟล์ที่ถูกแก้ระหว่างย้าย

ผลทดสอบ native รวม **455 checks** บน Paper และ ModelEngine จริง:

- ค่าเริ่มต้นไม่เปิดสัตว์เลี้ยง: **PASS 209 checks** — [model-engine-result.txt](../output/model-restore-native-default-v3-20261021/model-engine-result.txt)
- เลือก `abyssal_manta` ทั้งสามวัย: **PASS 246 checks** — [model-engine-result.txt](../output/model-restore-native-manta-v1-20261021/model-engine-result.txt)
- การรักษาข้อมูลบน filesystem: **PASS 13 checks** — [result.txt](../output/model-restore-staging-checks-20261021/result.txt) จาก [PetStagingChecks.java](../tests/model-engine/PetStagingChecks.java) ตรวจสำเนาตรงทุกไบต์ archive รุ่นเก่า backup ที่ขัดกัน และไฟล์ active ที่ถูกแก้ก่อนนำออก

JAR รุ่น `3.0.0-e2.104-model-restore` ที่ผ่านการทดสอบและใช้ติดตั้งคือ [dist/evergarden.jar](../dist/evergarden.jar) SHA-256: `a4dd3267fdfab074a0acb940cd1cc9ce40085f045713a0190df3329d0d618da2`

เซิร์ฟเวอร์ที่ผู้ใช้กำลังเล่นถูกตรวจแบบอ่านอย่างเดียว ไม่ได้แทนที่ JAR หรือรีสตาร์ตเซิร์ฟเวอร์นั้นจากการทดสอบนี้
