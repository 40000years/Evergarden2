# ย้ายเครื่องทำงาน (Evergarden2)

งานแบ่งเป็นสองส่วน:

1. **GitHub** (`40000years/Evergarden2`, branch `workstation-2026-10-27`) มีโค้ด เครื่องมือ เทสต์
   art ต้นฉบับ resource pack (`*/dist/`) เอกสาร และปลั๊กอินรุ่นล่าสุดใน `dist/`:
   - `evergarden.jar` = 3.0.0-e2.156-malefeasance-v5
   - `advance-magic.jar` = 1.34.1-bedrock-pack-repair
2. **Build kit** (`output/buildkit-20261027.zip`, ประมาณ 564 MiB) ห้ามขึ้น GitHub เพราะ repo เป็น
   public และในนั้นมีไฟล์ที่มีลิขสิทธิ์หรือแจกต่อไม่ได้:
   - Paper server พร้อม libraries และ mojang jar
   - ModelEngine 4.1.1 (ปลั๊กอินเสียเงิน)
   - Geyser และ Floodgate
   - jar ที่ใช้ compile ตามไฟล์ javac args
   ให้ก๊อปไฟล์นี้ไปเครื่องใหม่เอง เช่นผ่าน USB หรือ Google Drive ส่วนตัว ถ้าทำใหม่ใช้
   `python tools/make_buildkit.py`

## ติดตั้งบนเครื่องใหม่

1. ติดตั้งเครื่องมือ (รุ่นที่ใช้อยู่ตอนนี้):
   - Windows 10/11 (เทสต์ใช้ PowerShell junction)
   - JDK 25 (25.0.4)
   - Python 3.14
   - Git
2. Clone ไว้ที่ path เดิม `C:\Users\User\Afterdeath\evergarden2` เพราะไฟล์ javac args และ
   classpath ใน build kit อ้าง path แบบเต็ม:
   ```
   git clone https://github.com/40000years/Evergarden2.git C:\Users\User\Afterdeath\evergarden2
   cd C:\Users\User\Afterdeath\evergarden2
   git checkout workstation-2026-10-27
   python -m pip install -r requirements.txt
   ```
3. แตก build kit ทับที่ root ของ repo (ไฟล์จะลงไปใน `output/...` ตาม path เดิม):
   ```
   powershell -Command "Expand-Archive output-copy\buildkit-20261027.zip -DestinationPath ."
   ```
4. ถ้า path หรือชื่อ user บนเครื่องใหม่ไม่ใช่ `User` ให้แทน `C:/Users/User/Afterdeath/evergarden2`
   ด้วย path ใหม่ในไฟล์เหล่านี้:
   - `output/banshee-hunt-build-v1-20261015/evergarden-javac.args`
   - `output/boss-rewards-build-v3-20261021/advance-magic-javac.args`
   - `output/cocytus-balance-20261025/build/classpath.txt`
5. ตรวจว่าใช้ได้:
   ```
   python tests/model-engine/check_evergarden_bosses.py
   python tests/arkhaia/run.py --template output/arkhaia-aggro-feedback-me-v4-20261014 --garden-jar dist/evergarden.jar --server output/check-new-machine --classpath-file output/cocytus-balance-20261025/build/classpath.txt --direct-launch --malefeasance
   ```

## สิ่งที่ไม่ได้ขึ้นทั้งสองที่

- `output/` เกือบทั้งหมด (หลาย GB): เซิร์ฟเวอร์เทสต์เก่า log และ build ชั่วคราว ซึ่งสร้างใหม่ได้
- jar แบบมีเลขเวอร์ชันเก่าใน `dist/` และ zip pack เก่าใน `dist/*.zip` (รวมประมาณ 2 GB)
  ยังอยู่บนเครื่องนี้ ถ้าต้องใช้รุ่นเก่าให้ก๊อปเอง
- รูปพรีวิวที่ไม่มีเอกสารลิงก์ถึง (`previews/` ประมาณ 480 MB) ส่วนรูปที่ README และ `docs/`
  ลิงก์อยู่ได้ขึ้นแล้ว
- `output/pack-mirror-repo` ถ้าไม่มี `tools/publish_pack_mirror.py` จะ clone branch `packs` ใหม่
  ตอนเผยแพร่ pack

หลังออก release ทุกครั้ง ยังต้องรัน `python tools/publish_pack_mirror.py --release`
(ดู `docs/pack-mirror.md`)
