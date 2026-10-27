Superseded model: Arkhaia now uses the Dreadrose Sovereign rig, see [Evergarden main bosses](evergarden-main-bosses.md). The notes below describe the earlier rig and remain for history.

Current combat rules: [Arkhaia e2.70 / Six Rivers](arkhaia-worldboss.md). Earlier revision notes below are historical.

# Arkhaia: Model Engine

เวอร์ชันปัจจุบัน `e2.69-arkhaia-worldboss` ใช้ 24 คลิปและแพ็ก Bedrock 3.25.0 พร้อมสองเฟสและกติกาตีด้านหลัง ดู [กลไกและเวทคุมลาน](arkhaia-worldboss.md) รายละเอียด e2.68 ด้านล่างอธิบายการแก้แกนและโครงสร้าง renderer ที่ยังใช้อยู่

รุ่น `3.0.0-e2.68-arkhaia-bedrock-axis-fix` ใช้โมเดลมีข้อต่อสำหรับ Java ผ่าน Model Engine Free R4.1.1 และส่งต้นฉบับเดียวกันเป็น Bedrock client entity ผ่าน Geyser Entity API ใช้ AI ของ Evergarden โดยไม่ต้องติดตั้ง MythicMobs

โมเดลต้นฉบับแก้ไขได้ใน Blockbench: [arkhaia.bbmodel](../evergarden/src/main/resources/modelengine/arkhaia.bbmodel) มีชิ้นส่วนที่แสดงผล 151 กล่อง ข้อต่อ/จุดอ้างอิง 27 จุด (รวม pivot การมองและ marker ทิศหัวที่ไม่แสดงภาพ) และ hitbox ช่วยนำเข้าอีกหนึ่งกล่อง Model Engine ไม่ได้ใช้ hitbox นี้แทนตัวรับดาเมจของ Evergarden

แก้การหันตัวใน e2.63: ต้องล็อกการหมุนที่ `ActiveModel` หลัง `addModel` ด้วย การล็อกเฉพาะ `ModeledEntity` ทำให้ `setLockedYBodyRot` ของโมเดลไม่มีผลและตัวค้างทิศเดิม มีการทดสอบให้ผู้เล่นย้ายรอบบอสทั้งสี่ทิศ ตรวจมุม AI มุมโมเดล และตำแหน่ง marker ทิศหน้าที่ Model Engine คำนวณจริง บอสติดตามเป้าระหว่างเตรียมท่าจนเหลือ 3 tick สุดท้าย จากนั้นล็อกทิศให้หลบได้

ท่าดาบยกผ่านเหนือศีรษะไปค้างหลังไหล่ แล้วเหวี่ยงจากหลังผ่านเหนือหัวมาฟันลงตามแนวกึ่งกลางด้านหน้า มีคีย์เฟรมกลางให้การหมุนแต่ละช่วงน้อยกว่า 180 องศาเพื่อรักษาทิศเหวี่ยง มือซ้ายยื่นไปด้านหน้าทั้งช่วงชาร์จและปล่อย RIFT/NOVA แปลงแกน X/Y ของคีย์เฟรมให้ตรงกับ Blockbench importer ของ ME และแยกจากแกนหมุน geometry มีการตรวจกล่องดาบกับเกราะอก/หัวด้วย oriented-box intersection ตลอดท่าฟันเบา/หนัก และตรวจตำแหน่งปลายดาบ/มือร่ายจริงใน ME ระดับปลายดาบช่วงลงท่าอยู่ประมาณ 1.6–1.8 บล็อกเหนือพื้น พร้อมระยะไปด้านหน้าประมาณ 3.7–3.9 บล็อกโดยไม่รวมการพุ่งตัว

e2.66 ลดมุมยกไหล่จาก 210–230 เหลือ 140–145 องศา แล้วพับศอก 80–85 องศาเพื่อเก็บดาบด้านหลังหัว ฟันเบาเตรียม 0.45 วินาที ฟันหนัก 0.65 วินาที ร่าย Rift/Nova 1.10/1.00 วินาที พักหลังท่าสั้นลงและหันกลับตามเป้าตั้งแต่ Recovery ความเร็วเดินของคลิปสัมพันธ์กับระยะเคลื่อนจริง เวลาอ่านจาก [arkhaia-combat.properties](../evergarden/src/main/resources/modelengine/arkhaia-combat.properties) ร่วมกันระหว่าง Java AI และตัวส่งออกโมเดล

ภาพด้านข้างระบุด้านหน้า/หลัง: [ท่าเงื้อและฟัน](../previews/arkhaia-model-engine/arkhaia-sword-direction.png), [มือชาร์จและปล่อยเวท](../previews/arkhaia-model-engine/arkhaia-casting-direction.png)

ปรับแผ่นอกให้ซ้อนเป็นชั้น เพิ่มคิ้วเกราะและขอบไหล่ เปลี่ยนดาบเป็นใบกว้างมีร่องพลังม่วง แยกหัว ไหล่ ศอก มือ สะโพก เข่า เท้า ปีก ปลายปีก และชายผ้าคลุม ขนาดโมเดล 3.2 เท่าของหน่วยต้นฉบับ คงขนาดบอสใหญ่ประมาณสองเท่าจากรุ่นเริ่มต้น

แอนิเมชัน 17 ชุดประกอบด้วย idle, walk, hover, backstep, stun และ windup/active/recover ของ light, heavy, rift, nova เวลาของแต่ละชุดตรงกับ `ArkhaiaBrain` ดาบฟันเบาผ่านจุดลงน้ำหนักที่ active tick 3 และฟันหนักที่ tick 5 ใช้ state-machine animation handler แยกจาก state machine การต่อสู้ รอยดาบม่วงอ่านตำแหน่งกระดูก `sword_tip` มีเพดานจำนวนอนุภาค

เมื่อเปิดเซิร์ฟเวอร์ Evergarden ติดตั้ง blueprint ลง `plugins/ModelEngine/blueprints/arkhaia.bbmodel` ก่อนการนำเข้า จากนั้นรอ Model Engine สร้างแพ็กเสร็จแล้วรวม assets เข้า `plugins/Evergarden/resource-packs/judge-java.zip` ผ่านพอร์ตแพ็กบอสเดิม (ค่าเริ่มต้น 8189) URL เปลี่ยนตาม SHA-1 และ URL รุ่นก่อนหน้ายังโหลดได้ระหว่างเปลี่ยนแพ็ก ไม่มีการเพิ่ม shader สำหรับโมเดลผู้เล่นเข้ามา

ต้องเปิด bundled boss pack host ถ้าใช้ `world-boss.visuals.pack.url` ชี้แพ็กภายนอก ระบบจะใช้ท่าสำรองจนกว่าจะจัดการการเผยแพร่แพ็กและการตรวจความพร้อมให้รองรับบริการภายนอก ใช้ `/evergarden boss pack` ตรวจสถานะและ `/evergarden boss pack resend` ขอแพ็กอีกครั้ง

Java ที่รับแพ็กสำเร็จจะเห็น rig ของ Model Engine โดยซ่อน ItemDisplay/ArmorStand รุ่นเดิม ระบบ culling ของ ME รับเฉพาะ UUID ผู้เล่น Java ที่รับแพ็กแล้ว Bedrock ใช้ `voidscape:arkhaia` เป็น client entity ของตัวเอง โดย Geyser เปลี่ยนเฉพาะ UUID ของ carrier ที่ encounter ลงทะเบียนไว้ ไม่เปลี่ยน ArmorStand อื่นและไม่สร้างตัวรับดาเมจเพิ่ม

Bedrock เล่นข้อต่อจาก geometry และ keyframe ต้นฉบับเดียวกันครบ 17 คลิป ส่งเลขคลิป เวลา ความเร็ว และ yaw/pitch ของหัวผ่าน entity properties แทนการสลับหมวกทุกเฟรม ไคลเอนต์ขยับเวลาต่อระหว่าง snapshot ได้ไม่เกิน 0.1 วินาทีและ blend การเปลี่ยนคลิป 0.05 วินาที ตัว carrier ใช้ entity tracker ส่งการเคลื่อนที่ปกติแทนการเรียก Bukkit teleport ทุก tick เพื่อให้ Bedrock เกลี่ยตำแหน่งได้ ระดับเท้าและขนาด 3.2 ตรงกับตัวบอส แพ็ก Bedrock รุ่น 3.24.9 ต้องรีสตาร์ตเซิร์ฟเวอร์และเชื่อมต่อใหม่เพื่อโหลด ใช้ [Geyser Entity API](https://geysermc.org/wiki/geyser/custom-entities/) ที่มีตั้งแต่ 2.11; ทดสอบกับ Geyser-Spigot 2.12.0-SNAPSHOT ที่ติดตั้งอยู่ ถ้า API ไม่มีจะใช้ attachable เดิม

ระบบ [ArkhaiaLook](../evergarden/src/main/java/com/example/voidscape/boss/ArkhaiaLook.java) มองระดับสายตาของเป้าหมายจากหัวสูง 5.88 บล็อก จำกัดคอซ้ายขวา 60 องศา เงย 25/ก้ม 60 องศา และค่อย ๆ คืนท่าเมื่อไม่มีเป้าหมาย Java ใช้ `hi_gaze` ตาม [Head bone behavior](https://wiki.mythiccraft.io/modelengine/Modeling/Bone-Behaviors) ส่วน Bedrock เพิ่มชั้นแอนิเมชันบน `gaze` โดยไม่หมุนอก แขน หรือแนวฟัน ไหล่ทั้งสองข้างเลื่อนออก 0.65 หน่วยและท่าพักกางต้นแขน 8 องศาแทนการหนีบเข้าด้านใน ดู [ภาพการมอง](../previews/arkhaia-model-engine/arkhaia-gaze.png)

e2.68 แก้แกนแอนิเมชัน Bedrock: ไฟล์ bbmodel นี้เก็บ rotation ที่ปรับ X/Y สำหรับ ME แล้ว การส่งออก Bedrock ต้องคืนเป็น geometry-space ก่อนใช้ conversion ของ Bedrock จึงได้ค่า rotation เท่ากับ raw ME channel เดิม การกลับ X/Y เพียงรอบเดียวใน e2.67 ทำให้ฟันและร่ายย้อนหลัง รวมถึงก้มเงยกลับทิศ แก้ look layer เป็น `[pitch, yaw, 0]` และเพิ่มแพ็กเป็น 3.24.9 โดยไม่เปลี่ยน blueprint หรือ animation clock ฝั่ง Java

การตรวจทิศเพิ่มการนำข้อมูล Bedrock ในแพ็กกลับมาเป็น transform ตาม [Blockbench Bedrock animation importer](https://github.com/JannisX11/blockbench/blob/master/js/formats/bedrock/bedrock_animation.js) แล้วตรวจตำแหน่ง sword_tip/hand_l/head_forward ทางกายภาพ แพ็ก e2.67 ทำให้การตรวจนี้ล้มเหลวที่ท่า light windup; แพ็กรุ่นแก้ผ่านทั้งดาบ มือร่าย และมุมหัว การเทียบค่า keyframe เพียงอย่างเดียวในชุดทดสอบเดิมไม่ตรวจพบการแปลงแกนซ้ำนี้

เลือด 7,000, การป้องกัน LevelledMobs, AI เดินวน/รักษาระยะ/คอมโบ/ถอยหนี และเงื่อนไขอัญเชิญด้วยบล็อกเนเทอไรต์ที่จุดแดงกลางลานคงเดิม บล็อกไม่ถูกใช้หมด สามารถตั้ง `arkhaia.model-engine.enabled: false` แล้วเริ่มเซิร์ฟเวอร์ใหม่เพื่อกลับไปใช้ท่าสำรองทั้งสองฝั่ง

ระบบอัปเดต blueprint ที่ Evergarden เคยติดตั้งให้เอง โดยรู้จัก hash ของรุ่นที่เผยแพร่ e2.62–e2.64 แม้ไฟล์บันทึก ownership หายไปจากการติดตั้งใหม่ และสำรองรุ่นก่อนหน้าไว้ที่ `plugins/Evergarden/modelengine/backups` ก่อนแทนที่ บันทึก ownership สองแห่งคือ `plugins/Evergarden/modelengine/arkhaia.sha256` และ `plugins/ModelEngine/arkhaia-evergarden.sha256` เพื่อให้การรีเซ็ตโฟลเดอร์ Evergarden อย่างเดียวยังตามรุ่นโมเดลได้ ถ้าไฟล์ถูกแก้ไขเองจน hash ต่างจากรุ่นที่รู้จักและ ownership ทั้งสองแห่ง ระบบจะแจ้งเตือนและใช้ไฟล์นั้นต่อ ไม่เขียนทับงานของผู้ดูแล

คำสั่ง `/evergarden boss pack` และ log ตอนเริ่มเซิร์ฟเวอร์แสดง fingerprint ของ blueprint ที่ใช้จริงพร้อมระบุว่าเป็น bundled/custom การทดสอบเปรียบเทียบไฟล์ที่ Model Engine โหลดกับโมเดลภายใน JAR ด้วย เพื่อจับกรณีเปลี่ยน JAR แล้วแต่แอนิเมชันยังเป็นรุ่นเก่า

ภาพตรวจงานเป็นการเรนเดอร์จาก geometry และ keyframe จริงนอกเกม: [ภาพท่า](../previews/arkhaia-model-engine/arkhaia-rig-poses.png), [ภาพเคลื่อนไหว](../previews/arkhaia-model-engine/arkhaia-rig-combat.gif) ยังต้องตรวจแสง ทิศ และความรู้สึกในการต่อสู้บนไคลเอนต์จริง

การทดสอบใช้ Paper 26.3 build 152 และปลั๊กอินจาก Serva ตรวจการนำเข้า 17 คลิป การรวม/ดาวน์โหลดแพ็ก อัญเชิญด้วย event จริง เลือด/ขนาด/ตัวรับดาเมจ การเปลี่ยนคลิป การเคลื่อนของกระดูกและปลายดาบ ทิศหัวใน ME และการเก็บกวาดเมื่อจบไฟต์ ฝั่ง Geyser ตรวจการลงทะเบียน entity/properties, ยิง spawn event และใช้ entity fixture รับ pre-spawn snapshot พร้อมตรวจแพ็กที่ส่งจริง ตัวทดสอบเป็นผู้เล่นจำลอง ไม่มี client ตอบรับ chunk จึงใช้ viewer fixture ผ่าน API ก่อนประมวลผลกระดูก ไม่ใช่การทดสอบ FPS หรือภาพบน Java/Bedrock client จริง

คำสั่งสร้างและตรวจซ้ำ:

```powershell
python evergarden/tools/arkhaia_model_engine.py
python tests/model-engine/check_sword_path.py
python tests/model-engine/check_bedrock_rig.py
python tests/model-engine/check_bedrock_facing.py
python tools/preview_arkhaia_rig.py
python tools/build_faction_preview_release.py --build-directory output/arkhaia-me-release
python tools/try_model_engine.py --source-server C:/Users/User/Desktop/Serva --plugin output/model-engine-trial-20261014/ModelEngine-4.1.1.jar --garden-jar output/arkhaia-me-release/evergarden.jar --arkhaia --test-directory output/arkhaia-me-validation
```
