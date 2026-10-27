# ธนู Thornwhisper และหน้าไม้ Runelock (e2.136)

Release `3.0.0-e2.136-ranged-bedrock-hold` · แพ็ก Bedrock **3.57.0** · ต่อยอดจาก e2.135-map-trades (ซึ่งต่อจาก e2.134-ranged-weapons)

e2.134 ปั้นโมเดลใหม่ทั้งสองชิ้นและเปิดใช้งานอีกครั้ง (เสกได้จาก `/evergarden test` → เมนูอาวุธ, `/evergarden give <ผู้เล่น> thornwhisper|runelock`, ดรอปจาก Vault) ส่วน e2.136 แก้ฝั่ง Bedrock ให้ถือ ง้าง และบรรจุเหมือนธนู/หน้าไม้ vanilla ของ Bedrock ฝั่ง Java ไม่เปลี่ยน (แพ็ก Java เหมือนเดิมทุกไบต์)

ภาพ (เรนเดอร์จากไฟล์จริง ไม่ใช่ภาพในเกม):
- [Java กับ Bedrock ตอนถือ + ไอคอน](../previews/adventurer-weapons/ranged-hold.png)
- [สไปรต์ Bedrock ทุกสถานะเทียบของ Mojang](../previews/adventurer-weapons/ranged-bedrock.png)
- [ทุกสถานะฝั่ง Java](../previews/adventurer-weapons/ranged-states.png) · [เทียบธนู/หน้าไม้ vanilla ฝั่ง Java](../previews/adventurer-weapons/ranged-vanilla-vs-new.png)

## ดีไซน์

| อาวุธ | รายละเอียด | สถานะที่ทำแยก |
|---|---|---|
| **Thornwhisper** ธนูกระซิบพงหนาม | คันโค้งกลับแบบ recurve จากไม้มีชีวิต เถาวัลย์พัน หนามด้านหน้า ใบไม้ประดับ ปลายคันเป็นฝักเรืองแสง ด้ามพันหนังขอบทอง อัญมณีธรรมชาติ สายเรืองแสง ลูกศรหัวใบไม้เรืองแสง | พัก · ง้าง 1/2/3 |
| **Runelock** หน้าไม้อักขระมนตรา | พานเหล็กดำ ร่องลำกล้องเรืองฟ้า อักขระข้างพาน แถบทอง คันยิงเงินสลักรูน คริสตัลปลายคัน ไกและด้ามจับด้านล่าง คริสตัลโฟกัสกับห่วงเหยียบด้านหน้า | พัก · ดึงสาย 1/2/3 · บรรจุลูกดอกรูน · บรรจุพลุ |

## ปัญหาบน Bedrock ใน e2.134 และสาเหตุ

| อาการ | สาเหตุ |
|---|---|
| ธนูเล็กกว่าธนูปกติ ดูลอย | e2.134 ให้ Bedrock ถือตามท่าถือของ Java (ย่อ 0.9 มุมสาม, 0.68 มุมแรก) แต่ธนู vanilla ของ Bedrock ใช้ geometry และท่าถือของตัวเองซึ่งใหญ่กว่าและอยู่คนละที่ |
| ไม่จับสองมือ ไม่ซูม หน้าไม้ไม่มีท่าง้าง | ท่าจับสองมือ ท่าดึงสายหน้าไม้ และท่าเล็งตอนบรรจุ อยู่ใน animation controller ของตัวผู้เล่น ซึ่ง Mojang เขียนให้เล่นเฉพาะไอเท็มชื่อ `bow` / `crossbow` เท่านั้น และ Geyser ไม่ส่ง `use_animation` ให้ไอเท็ม custom |
| ไอคอนธนูในฮอตบาร์ไม่เปลี่ยนตอนง้าง | ไอคอนง้างของธนู vanilla ถูกเขียนตายตัวในตัวเกมสำหรับ `minecraft:bow` ไอเท็ม custom มีได้แค่ไอคอนเดียว (ลองรายการหลายเฟรมแล้วไอคอนหาย) |

## สิ่งที่แก้ใน e2.136

1. **ถือด้วยไฟล์ของ Mojang เอง:** attachable ของ Thornwhisper/Runelock ใช้ geometry `texture_meshes` และแอนิเมชัน `wield` / `wield_first_person_pull` ที่คัดลอกจาก Mojang/bedrock-samples 1.26.50.4 มาแบบไม่แก้สักตัวเลข (เก็บไว้ที่ `evergarden/tools/bedrock_vanilla/`) ขนาด ตำแหน่งในมือ และการดึงธนูเข้ากลางจอในมุมแรกจึงเป็นแบบเดียวกับธนู/หน้าไม้ปกติ ตามเอกสาร Microsoft ระบุว่า texture mesh กินพื้นที่ 16 หน่วยเท่าเดิมไม่ว่าเทกซ์เจอร์จะละเอียดเท่าไร
2. **สไปรต์ 64×64 เรนเดอร์จากโมเดล 3D:** มองจากด้านที่ลูกศร/ลูกดอกวางอยู่ (แบบสไปรต์ของ Mojang) มีแสงเงาตามความลึกและขอบเข้ม วางทับตำแหน่งสไปรต์ของ Mojang พอดี (ตรวจแล้วขอบห่างไม่เกิน 1.25 พิกเซลของสไปรต์ 16 px) สลับตามจำนวน tick ที่ง้าง เกณฑ์เท่า Java (ธนู 13/18 tick, หน้าไม้ 14.5/25 tick) ธนูสั่นตอนง้างเต็มที่ 20 tick ซึ่งเป็นแรงเต็มของ Java
3. **ท่าผู้เล่น:** แพ็กมี `animation_controllers/adventurer_ranged_player.controller.json` ซึ่งเป็น controller ผู้เล่นของ Mojang (root และ crossbow) โดยแก้แค่เงื่อนไขชื่อไอเท็มให้นับ Thornwhisper เป็นธนู และ Runelock (รวมตอนบรรจุ) เป็นหน้าไม้ วิธีเดียวกับตัวอย่าง custom bow ของ Microsoft ได้ท่าจับสองมือตอนง้างธนู ท่าดึงสายหน้าไม้ทั้งมุมสามและมุมแรก และท่าเล็งตอนหน้าไม้บรรจุแล้ว
4. **`use_animation`:** หลัง Geyser สร้างตารางไอเท็ม ปลั๊กอินเติม `use_animation` bow/crossbow ให้ทั้ง 4 นิยาม (`RangedBedrockAnimation`) แบบเดียวกับที่ Geyser เขียนให้ไอเท็มกินได้ แต่ไม่เพิ่ม component อาหาร ถ้าไม่มี Geyser หรือ Geyser เปลี่ยนโครงสร้าง ก็จะไม่แตะอะไร (log เตือน)

## ข้อจำกัดที่ยังเหลือ

- **ไอคอนธนูในฮอตบาร์ Bedrock ไม่เปลี่ยนตอนง้าง** ทำไม่ได้กับไอเท็ม custom ของ Bedrock (หน้าไม้เปลี่ยนไอคอนตอนบรรจุได้ เพราะเป็นนิยามแยกผ่าน `charge_type`)
- **การซูม (FOV) ตอนง้าง:** ใส่ `use_animation: bow` แล้ว ถ้าตัวเกมผูกการซูมกับ use animation ก็จะซูม แต่ถ้าผูกกับไอเท็ม `minecraft:bow` โดยตรงจะไม่ซูม ยังยืนยันไม่ได้จนกว่าจะลองบนไคลเอนต์จริง
- **ท่าดึงสายหน้าไม้ค้างที่ท่าเริ่ม:** ท่าของ Mojang ไล่ตามสัดส่วนเวลาใช้งาน แต่ Geyser ตั้งเวลาใช้งานไอเท็ม custom ไว้ 1000 วินาที ท่าจึงหยุดอยู่ที่ท่าเริ่มดึง (สองมือจับหน้าไม้) ส่วนตัวหน้าไม้ยังเปลี่ยนสถานะสายตามปกติ
- **Bedrock ถือเป็นสไปรต์หนา 1 พิกเซลแบบธนู vanilla** ไม่ใช่โมเดล 3D แบบ Java
- controller ผู้เล่นเป็นสำเนาของ Mojang ถ้า Mojang แก้ controller ผู้เล่นในเวอร์ชันหน้า ให้อัปเดตไฟล์ใน `evergarden/tools/bedrock_vanilla/` แล้ว build ใหม่ (ตัว export จะหยุดถ้าเงื่อนไขที่ต้องแก้หายไปหรือเปลี่ยน)

## ตรวจแล้ว

- `tests/weapon-remodel/check_weapon_hold.py`: ผ่าน 1,041 รายการ geometry/แอนิเมชันของ Bedrock ตรงกับของ Mojang ทุกสถานะ สไปรต์ทับตำแหน่งสไปรต์ Mojang controller ผู้เล่นต่างจากของ Mojang แค่ 8 เงื่อนไขชื่อไอเท็ม อาวุธประชิด 18 ชิ้นยังเหมือน Java ทุกมุม
- `evergarden/tests/check_adventurer_weapons.py`: ผ่าน 837 รายการ สไปรต์ในแพ็กตรงกับการเรนเดอร์ใหม่จากโมเดล การสลับสถานะ ไอคอน นิยามหน้าไม้ตอนบรรจุ
- Paper + Geyser จริง (`tests/arkhaia/run.py --ranged-geyser`): ผ่าน 107 รายการ ทั้ง 4 นิยาม × 3 โปรโตคอลได้ `use_animation` (ธนู 4, หน้าไม้ 9) ไม่มี component อาหาร และเป็นอ็อบเจกต์เดียวกับที่ Geyser ส่งใน ItemComponentPacket แก้เสร็จในวินาทีเดียวกับที่ Geyser เริ่มรับการเชื่อมต่อ
- `tools/try_model_engine.py --adventurer-ranged`: ผ่าน 1,139 รายการ (ง้าง บรรจุ ยิง สองมือ ลูกศร พลุ Quick Charge) เท่ากับ e2.134
- jar ที่ส่งคือไฟล์ที่ทดสอบ (sha256 `a15798961b59239b18e4a378866b64145e3d2385ae98d7dd46d2d0048a3eb9bf`) ไฟล์อื่นใน jar และแพ็กเหมือน e2.135 ทุกไบต์
- **ยังไม่ได้ทดสอบบนไคลเอนต์ Bedrock จริง**

## ติดตั้ง

1. แทน `plugins/evergarden.jar` ด้วย `dist/evergarden.jar`
2. รีสตาร์ตเซิร์ฟ (Geyser ต้องโหลด mapping ใหม่ และ log ควรมีบรรทัด `Bedrock bow/crossbow draw animation added to … Geyser definitions`)
3. ผู้เล่น Bedrock ออกแล้วเข้าใหม่ เพื่อรับแพ็ก 3.57.0
4. ถ้าโฮสต์แพ็กหรือ Geyser ไว้ที่อื่น ให้คัดลอก `evergarden/dist/evergarden-bedrock.mcpack` และ `geyser-mappings.json` ไปด้วย

สร้างใหม่: `python tools/build_ranged_weapons_release.py --base <evergarden.jar> [--promote]` (แทนเฉพาะไฟล์ธนู/หน้าไม้ ไฟล์อื่นเหมือนเดิมทุกไบต์) · ภาพ: `python tools/ranged_weapon_preview.py`

jar ก่อนหน้าสำรองไว้ที่ `output/ranged-bedrock-hold-20261025/base.jar` (e2.135-map-trades) และ `output/ranged-weapons-20261025/base-e2.133.jar`
