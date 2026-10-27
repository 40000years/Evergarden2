# ผู้เล่นโดนเตะเพราะแพ็ก DISCARDED (e2.146)

Release `3.0.0-e2.146-judge-pack-order` · ต่อจาก e2.145-tandem-flight · แก้คลาสเดียว (JudgePackService) ไม่มีการเปลี่ยนแพ็ก

## อาการ

```
[advance-magic] Advance Magic pack for Bowran: DISCARDED ...
[Evergarden] Evergarden pack for Bowran: DISCARDED ...
Bowran lost connection: ต้องโหลดแพ็กทรัพยากรของเซิร์ฟเวอร์ให้สำเร็จ...
```

## สาเหตุ

1. **ตัวเกมทิ้งแพ็กที่เหลือทั้งชุด:** จากโค้ดของ Minecraft 26.3 (`ServerPackManager.onDownload`) ถ้ามีแพ็กใดแพ็กหนึ่งดาวน์โหลดไม่สำเร็จ ตัวเกมจะให้แพ็กนั้นเป็น FAILED_DOWNLOAD และทิ้งแพ็กอื่นที่ยังไม่ได้เปิดใช้ทั้งหมดเป็น **DISCARDED**
2. **เดิมส่งแพ็ก 3 ตัวพร้อมกัน:** Magic, Evergarden และ Judge แพ็ก Judge ไม่มี log ตอนล้ม log จึงเห็นแค่ 2 แพ็กที่โดนทิ้งตาม
3. **แพ็ก Judge ได้ hash ใหม่ทุกครั้งที่รีสตาร์ต:**
   - ModelEngine 4.1.1 นำเข้าโมเดลบอสหลาย thread ลำดับจึงสุ่ม
   - ผลคือเลข custom_model_data และชื่อเทกซ์เจอร์เปลี่ยนทุกรอบ (สองรอบที่ทดสอบต่างกัน 353 จาก 2,025 ไฟล์)
   - ผู้เล่นจึงต้องโหลดแพ็ก Judge (~12 MB) ใหม่ทุกครั้ง ส่วน Magic กับ Evergarden ใช้จากแคช
4. **บนเครื่องทดสอบดาวน์โหลดได้ครบ:** ทั้ง 3 แพ็กผ่านพอร์ตเกมได้ และ SHA-1 ตรงทุกแพ็ก จึงสันนิษฐานว่าบนโฮสต์จริง การดาวน์โหลด HTTP ผ่านที่อยู่ที่ผู้เล่นใช้เข้าเซิร์ฟล้มทันที
   - IP ผู้เล่น 127.51.33.157 บอกว่ามี proxy หรือ tunnel อยู่หน้าเซิร์ฟ

## แก้อะไร

- **ส่งแพ็ก Judge ตามหลังแพ็กหลัก:** ส่งหลังจากแพ็ก Evergarden โหลดสำเร็จแล้วเท่านั้น
  - ถ้า Judge ล้ม จะไม่ลากแพ็ก Magic กับ Evergarden ล้มตามอีก
  - ตอน ModelEngine สร้างโมเดลเสร็จแล้วส่งแพ็กใหม่ให้ทุกคน คนที่ยังโหลดแพ็กหลักไม่เสร็จก็จะรอเหมือนกัน
- **log ทุกครั้งที่ Judge ล้ม:** เช่น `Judge pack for <ชื่อ>: FAILED_DOWNLOAD from http://...`
- **ถ้า Judge ยังตั้งเป็นแพ็กบังคับ (ค่าเริ่มต้น):** ผู้เล่นยังโดนเตะเมื่อ Judge ล้ม แต่ log จะบอกชัดว่าแพ็กไหนล้มและล้มที่ URL อะไร

## ถ้ายังโดนเตะ

1. ดู log หาบรรทัด `Judge pack for ...` แล้วลองเปิด URL ในบรรทัดนั้นด้วยเบราว์เซอร์จากเครื่องของผู้เล่น
   - ถ้าเปิดไม่ได้ แปลว่าโฮสต์หรือ tunnel ไม่ส่ง HTTP ผ่านพอร์ตเกม ต้องแก้ฝั่งโฮสต์ หรือใช้ URL ภายนอก (`resource-pack.host.public-url`)
2. ทางแก้ชั่วคราวให้เข้าเล่นได้ทันที: ตั้ง `world-boss.visuals.pack.required: false` ใน `plugins/Evergarden/config.yml`
   - ผู้เล่นจะเข้าเล่นได้ด้วยแพ็กหลัก แต่ถ้า Judge โหลดไม่ได้จะเห็นบอส สัตว์เลี้ยง และโมเดล ModelEngine เป็นแบบสำรอง
3. log ฝั่งผู้เล่น (`.minecraft/logs/latest.log`) จะบอกสาเหตุที่ดาวน์โหลดไม่ได้

## ตรวจแล้ว (Paper จริง)

- `tests/arkhaia/run.py --pack-order` (PackOrderProbe): ผ่าน 12 ข้อ ด้วยผู้เล่นจำลองบน Paper จริง
  - แพ็กหลักมาก่อน Judge รอแม้มีการส่งซ้ำ แล้วถูกส่งหลังแพ็กหลักโหลดเสร็จ
  - bytes ตรงกับ hash ทุกแพ็ก
  - Judge ที่ตั้งเป็นแพ็กบังคับ: ถ้าล้มยังเตะ
  - Judge ที่ตั้งเป็นไม่บังคับ: ถ้าล้มผู้เล่นอยู่ต่อได้
- `--pack-download` (PackDownloadProbe): ดาวน์โหลดทั้ง 3 แพ็กผ่านพอร์ตเกม ทั้งพร้อมกันและทีละแพ็ก พร้อม ModelEngine จริง
  - HTTP 200 ทุกแพ็ก SHA-1 ตรง มี pack.mcmeta ครบ
- `tests/resource-packs/ResourcePackPaperChecks.java` ปรับให้คาดหวังลำดับใหม่แล้ว แต่ยังไม่ได้รัน (ต้องใช้ build directory ของ run_checks.py)
- ยังไม่ได้ทดสอบกับตัวเกมจริงผ่านโฮสต์ของคุณ

ติดตั้ง: แทน `plugins/evergarden.jar` ด้วย `dist/evergarden.jar` (sha256 `bb4fcc06…`) แล้วรีสตาร์ต · สร้างใหม่: `python tools/build_judge_pack_order_release.py [--promote]`
