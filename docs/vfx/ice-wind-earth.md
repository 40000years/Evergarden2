# VFX design: Ice, Wind และ Earth

สเปกภาพ 22 เวทนี้ยึดจังหวะและพื้นที่ของ `ElementalSpellService.java` โดยตรง ที่ 20 TPS หนึ่ง tick เท่ากับ 50 ms ภาพที่เกิดหลัง hit ทันทีเป็นภาพสลายต่อเนื่อง ไม่ใช่การเลื่อนเวลาทำดาเมจ แผ่นภาพโปร่งใสใช้ ItemDisplay ใน Java และ custom world particle ใน Bedrock; หากไม่มีแพ็ก ใช้อนุภาควาดเส้นรูปทรงเดียวกันแบบลดรายละเอียด

## Element style guide

### Ice

- **Visual theme:** ผลึกเจียระไน ผิวแตกเป็นเหลี่ยม รอยเยือกแข็งบางและเกล็ดหิมะที่มีแกนชัด
- **Main colors:** ฟ้าน้ำแข็ง `#A8E8FF`, น้ำเงินผลึก `#4B88C7`
- **Secondary colors:** ขาวเย็น `#EDF9FF`, ครามลึก `#25416F`; ขอบขาวใช้เฉพาะด้านรับแสง
- **Particle style:** SNOWFLAKE กระจุกเล็ก, DUST ตามแนวผลึก, เศษ BLOCK แบบ PACKED_ICE เฉพาะ impact ที่จำเป็น ไม่สร้างฝนเกล็ดทั่วจอ
- **Spell circle / sigil style:** กิ่งหิมะหกแฉก ลายเส้นผลึกที่แตกไม่เท่ากัน ช่องตัดรูปสามเหลี่ยม; เขตพื้นที่ยังใช้ขอบตามรัศมีจริง แต่ไม่เติมแผ่นน้ำแข็งทึบทั้งพื้น
- **Motion style:** พุ่งคม → หยุดฉับ → แตกเป็นแผ่นเล็ก; ความเย็นแพร่เป็นเส้นแตก ไม่ไหลเป็นคลื่นแบบ Water
- **Impact style:** ดาวผลึก 3–6 กิ่งและเศษสามเหลี่ยมที่ตกสั้น ๆ ขอบขาวสว่าง 1–2 tick แล้วเหลือฟ้า
- **Sound mood:** แก้วคม น้ำแข็งร้าว และลมเย็นเบา เสียงแตกสั้นสำคัญกว่าควันหรือเสียงระเบิด
- **Overall fantasy identity:** เวทเยือกแข็งที่แม่นยำ เปราะคม และตรึงเป้าด้วยผลึกที่อ่านช่องว่างได้
- **ทางเลือกเบา:** DUST ฟ้าสองโทนเป็นรูปสามเหลี่ยม + SNOWFLAKE 3–6 จุด; รักษาตำแหน่งหนามและกรอบสถานะจริง

### Wind

- **Visual theme:** ริบบิ้นอากาศทรงเสี้ยว คมลมบาง และเส้นสั้นที่แสดงทิศทางชัด พื้นที่ส่วนใหญ่เป็นช่องโปร่ง
- **Main colors:** ขาวอมมิ้นต์ `#DFFFF3`, เขียวหยกอ่อน `#82DCC5`
- **Secondary colors:** เทาอากาศ `#A9BDC4`, ขาว `#F5FFFF`; ไม่ใช้ขาวทึบทั้งแผ่น
- **Particle style:** CLOUD ขนาดต่ำเฉพาะจุดเริ่ม/ปะทะ, DUST เป็นเส้นลม และ SWEEP_ATTACK จำนวนน้อยเฉพาะจุดยืนยัน hit
- **Spell circle / sigil style:** เสี้ยวใบมีดขาดช่วง ลูกศรโค้ง และร่องลมสามเส้น วงกลมใช้เป็นขอบอิทธิพลเฉพาะพายุ
- **Motion style:** เข้าเร็ว ออกเร็ว โค้งเป็นเกลียวเมื่อเป็นการดึง; ลมเคลื่อนขึ้นต่างจากน้ำที่ไหลตามพื้น
- **Impact style:** รอยเฉือนสั้นเฉียงกับเศษลมที่คลายตัว ไม่แตกเป็นก้อนหรือไฟกระจาย
- **Sound mood:** ลมปาด พัดผ่าน และเสียงกวาดแคบ จังหวะเบาและสูงกว่า Earth; พายุใช้เสียงต่ำเฉพาะตอนขึ้น/ลง
- **Overall fantasy identity:** ผู้ควบคุมอากาศที่เร็วและคล่อง มองเห็นแรงผ่านเส้นทางของลมแทนหมอกหนา
- **ทางเลือกเบา:** แถบเสี้ยว 1 เส้นหรือ DUST โค้ง 8–12 จุด; ลดละอองก่อนลดหัวคมหรือทิศการไหล

### Earth

- **Visual theme:** หินทรงเหลี่ยม แผ่นแตก น้ำหนักตกแนวดิ่ง และร่องรูนโบราณเป็นช่วง ๆ มีมอสเป็นรายละเอียดรอง
- **Main colors:** หินอุ่น `#776A54`, เขียวเพอริดอต `#A7C65A`
- **Secondary colors:** ทราย `#C4AC77`, หินเข้ม `#3D403D`, ขอบแร่ `#DAE7A1`
- **Particle style:** BLOCK จาก STONE/COARSE_DIRT/MOSS_BLOCK แบบเลือกจุด, DUST สีน้ำตาลและแร่; ไม่พ่นเศษ block ต่อเนื่องทั้งสนาม
- **Spell circle / sigil style:** รอยแตกตัดกัน แผ่นศิลารูปหลายเหลี่ยม และร่องเรขาคณิตเก่า รูปหลักสื่อมวล ไม่เป็นแผ่นวงเวททอง
- **Motion style:** ชูขึ้นสั้น → กระแทกหนัก → เศษตกตามแรงโน้มถ่วง; ใช้การหยุดนิ่งช่วงสั้นให้รู้สึกมีน้ำหนัก
- **Impact style:** รอยแตกแนวพื้น ฝุ่นต่ำและหิน 3–5 เศษในช่วง 4–8 tick; ไม่กลบเท้าหรือขอบโจมตีบอสด้วยควัน
- **Sound mood:** หินเสียด พื้นสะเทือนทุ้ม แร่กังวานเบา จบเร็ว; ไม่เปิดเสียงพังหินทุกเศษ
- **Overall fantasy identity:** ผู้พิทักษ์ศิลาดึกดำบรรพ์ หนัก มั่นคง และเชื่อมกับพื้นดิน
- **ทางเลือกเบา:** วาดรอยแตกด้วย DUST และเศษ BLOCK 4–8 จุด; สิ่งกีดขวางแสดงกรอบตำแหน่งจริงเสมอ

## Redesigned spell effects

### Frost Bolt

- **Spell Name:** Frost Bolt
- **Element:** Ice
- **Combat Role:** กระสุนเดี่ยว ระยะ 24 บล็อก ความเร็ว 1.6 บล็อก/tick พร้อม slow ตามสถานะจริง
- **Visual Goal:** เข็มผลึกที่พุ่งแม่น เห็นหัวลูกและแนวเย็นชัดโดยไม่กลายเป็นลูกไฟฟ้าสีฟ้า
- **Cast Phase:** ผลึกสามเหลี่ยมขนาดเล็กเปิดที่ปลายคทาพร้อม tick ที่ยิง เส้นประกอบสองข้างหุบเข้าหาแกน 2 tick โดยไม่ชะลอกระสุน
- **Travel / Active Phase:** ใช้แผ่นหัวผลึกไขว้สองระนาบ ขนาด 0.3–0.5 บล็อก ติดตามตำแหน่งกระสุนจริง; หางแตกเป็นเส้นฟ้า 2 ช่วงยาวไม่เกิน 1.5 บล็อก ดับจากปลายเก่าก่อน
- **Impact Phase:** แผ่นดาวแตก 3 กิ่งติดผิวปะทะ 3 tick พร้อมเศษสามเหลี่ยมเล็ก; accepted hit จึงเติมคราบเย็นที่เท้า เป้าหมายที่ป้องกัน damage ได้เห็นเพียงเศษกระทบเล็ก
- **Lingering Effect (if any):** เครื่องหมาย slow เป็นผลึกเล็กข้างข้อเท้า ไม่หุ้มตัว; จบตามสถานะจริงและย้ายโลก/ตายต้องล้าง
- **Suggested Particles:** DUST ฟ้า/ขาวตามหาง, SNOWFLAKE 3–5 จุดที่ impact, BLOCK ของ PACKED_ICE สูงสุด 4 จุดเมื่อจำเป็น
- **Suggested Sounds:** `minecraft:block.amethyst_block.hit` เบา pitch สูงตอนยิง, `minecraft:block.glass.break` เบาเสียงเดียวตอน impact
- **Camera / readability notes:** หัวต้องชัดจากด้านข้างและด้านหลังของเพื่อนร่วมทีม; ไม่วาดลำแสงหนาเชื่อมคทาถึงเป้าตลอดเส้น
- **Performance-safe version:** ไม่มี display; หัว DUST 2 จุดกับหาง 4 จุดทุก 2 tick, SNOWFLAKE 4 จุดที่ hit เพียงครั้งเดียว
- **Why this looks better than a basic effect:** รูปเข็ม การแตกเหลี่ยม และคราบเย็นทำให้อ่านว่าเป็นน้ำแข็งได้ตั้งแต่ยังไม่โดน ไม่ต้องพึ่งสีอย่างเดียว

### Ice Spike

- **Spell Name:** Ice Spike
- **Element:** Ice
- **Combat Role:** หนามจากพื้น โจมตีทันทีรัศมี 2.5 บล็อกที่จุดเล็ง
- **Visual Goal:** เห็นผลึกแทงจากจุดพื้นอย่างคมและแรง โดยชัดว่าโจมตีเกิดทันที
- **Cast Phase:** เส้นร้าวสามกิ่งติดพื้นแวบพร้อมรับ cast ไม่มีวงชาร์จหรือการรอก่อน hit
- **Travel / Active Phase:** หนามหลักสูงประมาณ 3.5 บล็อกปรากฏพร้อม hit แล้วคลี่เศษรองสองชิ้นข้างแกนใน 2 tick; Java ใช้แผ่นผลึกไขว้ Bedrock ใช้ภาพหนามสองระนาบที่วางตำแหน่งเดียวกัน
- **Impact Phase:** ปลายหนามแสงขาว 1 tick พร้อมเศษผลึกออกจากจุดที่เป้าโดนจริง; ขอบพื้นรัศมีจริงมีมุมผลึกสั้น ๆ ไม่อ้างว่าขอบหนามคือพื้นที่ hit ทั้งหมด
- **Lingering Effect (if any):** หนามยุบและแตกภายใน 6–8 tick ไม่มีบล็อกน้ำแข็งถาวรหรือ collision ใหม่
- **Suggested Particles:** DUST ตามเส้นร้าว, SNOWFLAKE 6 จุด, BLOCK PACKED_ICE ไม่เกิน 6 จุดตอนแตก
- **Suggested Sounds:** `minecraft:block.glass.place` ต่ำตอนขึ้น ตาม `minecraft:block.amethyst_block.break` เบาตอนแตก
- **Camera / readability notes:** หนามแกนแคบและมีช่องโปร่งทั้งฐาน เพื่อให้ยังเห็นตัวบอสกับพื้นด้านหลัง; อย่าสื่อว่ามี knock-up เพิ่ม
- **Performance-safe version:** DUST สามเส้นขึ้นตรงรวม 15 จุดพร้อมรอยร้าวพื้น 9 จุด; ไม่มีชิ้นผลึกค้างหลัง impact
- **Why this looks better than a basic effect:** การตั้งแกนจากพื้นและชั้นผลึกแยกความลึกให้ความรู้สึกแทงจริง แม้ใช้งานภาพเพียงช่วงสั้น

### Frozen Armor

- **Spell Name:** Frozen Armor
- **Element:** Ice
- **Combat Role:** ลดความเสียหาย 20% นาน 140 tick/7 วินาที
- **Visual Goal:** เกราะผลึกที่ดูปกป้องแต่ไม่หุ้มผู้เล่นเป็นกล่องทึบ
- **Cast Phase:** เกล็ดผลึกสามชิ้นประกอบที่ไหล่และข้างเอวพร้อม buff เริ่มใน tick แรก ใช้แสงขาวเฉพาะรอยต่อ
- **Travel / Active Phase:** วาด facet บางสองข้างลำตัวใต้ระดับศีรษะ ติดตามทิศผู้เล่นเป็นรอบ 4 tick; ในมุมบุคคลที่หนึ่งแสดงเฉพาะขอบข้างแขน ไม่สร้างแผ่นหน้ากล้อง
- **Impact Phase:** เมื่อ buff ลด damage จริงให้ facet ด้านรับแรงสว่าง 2 tick และมีรอยแยกเล็กที่คืนสภาพภายใน 4 tick; ไม่ใช้ภาพแตกทั้งเกราะทุกครั้ง
- **Lingering Effect (if any):** คงตามอายุ reduction จริง หายเมื่อถูกแทน หมดเวลา หรือผู้เล่นตาย; สลายเป็นเกล็ดเล็ก 3 ชิ้นตอนจบ
- **Suggested Particles:** DUST ฟ้าขาว 2–4 จุดตามรอยต่อ, SNOWFLAKE ไม่เกิน 3 จุดต่อ feedback
- **Suggested Sounds:** `minecraft:block.amethyst_block.chime` เบาตอนประกอบ, `minecraft:block.glass.hit` สั้นเมื่อป้องกัน hit โดยจำกัดหนึ่งครั้งต่อ 5 tick
- **Camera / readability notes:** รูป facet อยู่ข้างไหล่ไม่คลุมหมวกหรือมือหลัก; เน้นว่าเป็น reduction ไม่ใช่โล่รับ damage แบบมี HP
- **Performance-safe version:** ใช้ผลึก DUST สามจุดหมุนช้ามากทุก 10 tick พร้อม hit spark เฉพาะด้านที่โดน ไม่มีเกราะ display ติดตัว
- **Why this looks better than a basic effect:** เกราะมีตำแหน่งและการตอบสนองต่อแรงกระแทก ผู้เล่นรับรู้ว่า buff ยังอยู่โดยไม่ต้องมีละอองรอบตัวตลอดเวลา

### Glacial Path

- **Spell Name:** Glacial Path
- **Element:** Ice
- **Combat Role:** แนวโจมตีทันทีระยะ 8 บล็อก ความกว้างตรวจชนเดิม ไม่ใช่พื้นน้ำแข็งที่วางใหม่
- **Visual Goal:** รอยเยือกแข็งคมที่ชี้แนวการโจมตีได้ตั้งแต่เฟรมแรก
- **Cast Phase:** เส้นแยกตรงปลายคทาเปิดไปตามแนวเล็งทันที 1 tick พร้อม hit เดิม
- **Travel / Active Phase:** แสดงก้างผลึกตามเส้นโจมตีจริง 3 ช่วงพร้อมกัน แล้วให้แสงไหลจากใกล้ไปไกลภายใน 4 tick เพื่อเป็น followthrough; เส้นคงทิศ 3D ตาม aim ไม่ลากแนวลงพื้นจนดูเป็น hit คนละตำแหน่ง
- **Impact Phase:** ผลึกสั้นข้างเป้าทุกตัวที่โดนจริง ช่วงปลายแนวแตกเป็นเกล็ดน้อยกว่าตำแหน่ง hit เพื่อแยกการโดนกับการสิ้นสุดภาพ
- **Lingering Effect (if any):** รอยเส้นจางภายใน 8 tick; slow/Frost indicator คงตามสถานะจริง ไม่สร้างทางเดินลื่นหรือพื้นที่ทำดาเมจต่อ
- **Suggested Particles:** DUST ฟ้าสองโทนตามแนว, SNOWFLAKE 2 จุดต่อ accepted hit; เศษผลึกใช้ texture เล็กแทน block particles หนา
- **Suggested Sounds:** `minecraft:block.glass.hit` สูงตอนปล่อย, `minecraft:block.amethyst_block.hit` ต่ำที่ปลายแนวแบบครั้งเดียว
- **Camera / readability notes:** หากยิงขึ้นหรือเล็งตัวบิน ภาพต้องอยู่บนเส้นเล็งจริง ไม่บังคับให้เป็น decal บนพื้น; หยุดที่การบังจากโลก/กำแพงเวทเดิม
- **Performance-safe version:** DUST คู่ซ้ายขวา 12–16 จุดยิงครั้งเดียว แสงไหลลดเหลือเปลี่ยนขนาดจุด ไม่แอนิเมตแผ่นยาว
- **Why this looks better than a basic effect:** ลายก้างผลึกทำให้แนวเส้นมีเอกลักษณ์และความเร็ว โดยไม่หลอกว่ามีกระสุนรอวิ่งไปทำดาเมจภายหลัง

### Crystal Prison

- **Spell Name:** Crystal Prison
- **Element:** Ice
- **Combat Role:** โจมตีเป้าหมายตรงและกักศัตรูที่ freeze ได้ 50 tick/2.5 วินาที
- **Visual Goal:** กรงผลึกเปิดช่องที่เห็นผู้ติดสถานะชัด และไม่แสดงว่ากักบอสสำเร็จเมื่อบอสต้านได้
- **Cast Phase:** กิ่งผลึกเล็กชี้เป้าจากปลายคทาพร้อม cast สำเร็จ ไม่มีเส้นกระสุนช้าเพิ่มเติม
- **Travel / Active Phase:** ถ้า frozen ถูกใช้จริง ให้ facet สูงตามตัวเป้าสามแนวขึ้นจากฐานโดยเว้นช่องมากกว่าครึ่งหนึ่งของ silhouette; กรณี immune หรือบอส แสดงเพียงรอยผลึกสั้นที่เท้าและ Frost marker ที่ระบบรับจริง
- **Impact Phase:** หนีบแสงสองมุมเข้าหาตัว 2 tick พร้อม hit ที่ยอมรับได้ ไม่หุ้มด้วยก้อนน้ำแข็งทึบ
- **Lingering Effect (if any):** กรงติดตาม anchor ของ frozen จริงและแตกเมื่อสถานะสิ้นสุด/ถูกล้างทันที อายุภาพไม่เท่ากับอายุ Frost stack โดยอัตโนมัติ
- **Suggested Particles:** DUST ที่ขอบ facet, SNOWFLAKE 6 จุดตอนปิดและแตก; ไม่ใช้ block จริง
- **Suggested Sounds:** `minecraft:block.glass.place` ตอนกรงปิด, `minecraft:block.glass.break` เบาตอนหลุด มี cooldown เสียงต่อเป้า
- **Camera / readability notes:** สำหรับผู้ถูกกัก มุมกล้องยังหมุนได้ตามเกมเดิม จึงไม่วางภาพทึบชิดตา; ห้ามใช้กรงเต็มเมื่อ freeze immunity ยังทำงาน
- **Performance-safe version:** รูปค้ำสามเหลี่ยม DUST 12 จุดทุก 10 tick เฉพาะเป้าที่ frozen จริง; แยกผลึกที่เท้าเมื่อ immune
- **Why this looks better than a basic effect:** รูปทรงบอกการกักและสถานะต้านได้ตรงกับเกม แทนเมฆที่มองไม่ออกว่าทำอะไร

### Blizzard

- **Spell Name:** Blizzard
- **Element:** Ice
- **Combat Role:** สนามรัศมี 6 บล็อก อายุ 120 tick/6 วินาที 6 pulse
- **Visual Goal:** พายุเย็นที่อ่านทิศหมุนและเขตอันตรายได้ โดยมองเห็นพื้นบอสด้านใน
- **Cast Phase:** ลายร้าวเกล็ดหิมะบางบนพื้นเปิดพร้อมสนามและ pulse แรก ไม่มีช่วงสะสมเพิ่มเติม
- **Travel / Active Phase:** วางม่านผลึกบางสามส่วนตามขอบสนาม เปิดช่องระหว่างกันกว้าง; เกล็ดหิมะเคลื่อนเฉียงลงและวนขอบ ไม่เต็มปริมาตร ระหว่าง pulse ลดความหนาแน่นลงอย่างชัด
- **Impact Phase:** ทุก pulse จริงที่อายุ 0/20/40/60/80/100 ส่งลมเย็นข้ามพื้นที่แบบแผ่นโปร่งต่ำ พร้อมรอยผลึก 2 กิ่งบน accepted targets
- **Lingering Effect (if any):** พายุหมดที่ 120 tick; คราบ Frost/slow ต้องคงตามอายุสถานะแยกต่างหาก ไม่ปล่อยสนามภาพต่อเป็นวินาทีหลัง damage จบ
- **Suggested Particles:** SNOWFLAKE กระจุก 8–12 จุดต่อ pulse, DUST ตามรอยร้าว/ขอบ, CLOUD 2–3 จุดที่ระดับเหนือหัวหากจำเป็น
- **Suggested Sounds:** `minecraft:weather.rain` เบา pitch สูงแบบ cue สั้นเฉพาะเริ่ม/จบ เสริม `minecraft:block.powder_snow.hit` เบาต่อ pulse ที่จำกัดเสียง
- **Camera / readability notes:** ความโปร่งในกลางสนามสูงสุด; ขอบต้องอยู่รัศมี 6 จริง ไม่ตามรูปม่านหิมะที่อาจแกว่งเข้าด้านใน; ไม่มี whiteout
- **Performance-safe version:** ขอบ 24 จุดทุก 10 tick + เกล็ด 8 จุดเฉพาะ pulse และภาพผลึกบนเป้า ไม่มีม่านสามชั้น
- **Why this looks better than a basic effect:** ระดับความหนาแน่นที่เต้นตาม pulse และทิศหิมะทำให้เห็นแรงพายุโดยไม่อัดเกล็ดเป็นหมอกขาว

### Absolute Zero

- **Spell Name:** Absolute Zero
- **Element:** Ice
- **Combat Role:** Ultimate ระเบิดทันทีที่พื้นเล็ง รัศมี 6 บล็อก พร้อม freeze ที่ผ่านเงื่อนไขเดิม
- **Visual Goal:** ความเย็นสุดขั้วที่หยุดนิ่งชั่วขณะแล้วแตกเป็นผลึก ดูทรงพลังโดยไม่เพิ่มเวลาร่าย
- **Cast Phase:** หัวคทาเกิดเครื่องหมายเกล็ดคมใน tick เดียวกับ cast และ hit; ไม่ทำชาร์จลวง 1 วินาทีก่อนระเบิด
- **Travel / Active Phase:** ในเฟรม hit ให้รอยร้าวผลึกเต็ม footprint ทันที ขอบมีเข็ม 6 มุม จากนั้นแสงเดินบนรอยร้าวและเสี้ยวคริสตัลแยกขึ้นใน 2–4 tick เป็นภาพสลายหลังผลเกมเกิดแล้ว
- **Impact Phase:** ดาวผลึกกว้างแต่โปร่งสว่าง 1 tick แล้วแยกเป็น facet 6 ชิ้นที่เคลื่อนออกต่ำและหยุดในพื้นที่เดิม; เสียงเงียบช่องสั้นตามด้วยแก้วแตกทำให้รู้สึกหนัก
- **Lingering Effect (if any):** ผลึกฉากจางภายใน 10–12 tick; กรงเล็กเฉพาะเป้าที่ยืนยัน frozen จริงไม่เกินอายุสถานะ 40 tick/2 วินาที บอส/immune ได้เพียง Frost marker
- **Suggested Particles:** DUST ฟ้า/ขาวบนกิ่งหลัก, SNOWFLAKE 12–16 จุดที่แตก ไม่ใช้ FLASH เต็มจอหรือพ่นเกล็ดทั่วระยะมอง
- **Suggested Sounds:** `minecraft:block.beacon.deactivate` เบาสั้นตรง tick 0 ตาม `minecraft:block.glass.break` ต่ำและ `minecraft:block.amethyst_block.resonate` หางเบา
- **Camera / readability notes:** แกนกลางโปร่งและผลึกฉากสูงไม่เกินประมาณ 3–4 บล็อก; ไม่สั่นกล้อง/แช่หน้าจอ และไม่แสดงพื้นปลอดภัยหรือการหยุดบอสที่ไม่มีจริง
- **Performance-safe version:** รอยร้าว 6 กิ่ง DUST รวม 36 จุดครั้งเดียว + SNOWFLAKE 12 จุดและ status marker ต่อเป้า ไม่มี facet ฉาก
- **Why this looks better than a basic effect:** แรงมาจากเสี้ยววินาทีที่โครงผลึกคมปรากฏพร้อมกันแล้วแตก ทิศกับการหยุดทำให้อ่านว่าเป็นศูนย์สัมบูรณ์

### Wind Cutter

- **Spell Name:** Wind Cutter
- **Element:** Wind
- **Combat Role:** แนวเฉือนทันทีระยะ 18 บล็อก ถูกได้สูงสุด 3 เป้าหมาย
- **Visual Goal:** คมลมเสี้ยวที่รวดเร็วและเป็นเส้นเดียว จำรูปได้จากทิศของคม
- **Cast Phase:** แถบแสงแคบเฉียงเปิดที่ปลายคทาพร้อม hit ทันที
- **Travel / Active Phase:** วาด ribbon ตัดตามแนวเล็งจริงตั้งแต่ต้นถึงปลาย tick เดียว เสี้ยวใบมีดเล็กวิ่งตามแนวใน 2 tick เพื่อเสริมภาพตามหลังเท่านั้น ไม่รอให้เดินถึงจึงแสดง hit
- **Impact Phase:** เป้าโดนจริงได้รอยเฉือนเฉียง 2 tick และเส้นอากาศแยกสองข้าง; ปลายที่ชนบล็อกคลายเป็นลมเบาไม่ให้ feedback เท่า accepted hit
- **Lingering Effect (if any):** หางลมหายภายใน 4–6 tick; Air status ใช้สัญลักษณ์ที่ระดับเอวตามอายุจริง
- **Suggested Particles:** DUST มิ้นต์/ขาวตามสัน, CLOUD 2 จุดตอนปล่อย, SWEEP_ATTACK หนึ่งจุดเฉพาะ hit ที่ต้องเน้น
- **Suggested Sounds:** `minecraft:entity.player.attack.sweep` เบา pitch สูง; ไม่ใส่เสียงพายุหนักให้เวทพื้นฐาน
- **Camera / readability notes:** รักษาแนวโจมตีเต็มที่เกิดทันทีและไม่วาดคมกว้างกว่าความกว้างตรวจโดนจริงอย่างมาก; ใช้ขอบคู่บางสำหรับอ่านจากหลายมุม
- **Performance-safe version:** DUST สองแนวรวม 16–20 จุดครั้งเดียวและ CLOUD 2 จุดต่อ hit ไม่มี display/travel followthrough
- **Why this looks better than a basic effect:** เสี้ยวคมกับเส้นตามทิศอ่านเป็นลมเฉือนทันที ต่างจากเส้นฝุ่นที่ไม่บอกแรงหรือขอบ

### Gale Step

- **Spell Name:** Gale Step
- **Element:** Wind
- **Combat Role:** พุ่ง/ย้ายตำแหน่งทันทีสูงสุด 7 บล็อกตาม safe path เดิม ไม่มีดาเมจ
- **Visual Goal:** ความคล่องที่ทำให้ทั้งผู้ร่ายและเพื่อนรู้ว่าผู้เล่นย้ายจากไหนไปไหน
- **Cast Phase:** ริบบิ้นเล็กสองข้างข้อเท้าหุบเข้าใน tick ที่ย้ายสำเร็จ ถ้าไม่มีจุด safe ไม่เปิดเอฟเฟกต์สำเร็จ
- **Travel / Active Phase:** เส้นลมคู่ตาม corridor จากตำแหน่งก่อนย้ายถึงจุดปลายจริง หางบางจางจากต้นไปปลายใน 4 tick ไม่เพิ่มตัวละครร่างปลอมหลายตัว
- **Impact Phase:** เสี้ยวลมต่ำที่ข้อเท้าหันตามทิศพุ่งคลี่ออกด้านข้าง ไม่ใช้รอย slash บนศัตรูเพราะเวทนี้ไม่ตี
- **Lingering Effect (if any):** เส้นทางหายภายใน 6 tick และไม่ดูเหมือนสนามเพิ่มความเร็วหรือทิ้งกับดัก
- **Suggested Particles:** DUST มิ้นต์ 8–12 จุดตามทาง, CLOUD 2 จุดที่ต้นและ 2 จุดที่ปลาย
- **Suggested Sounds:** `minecraft:entity.breeze.slide` เบา หรือ `minecraft:entity.player.attack.sweep` สั้นหากแพ็กเสียงฝั่งใดไม่พร้อม
- **Camera / readability notes:** ไม่เลื่อน FOV ไม่ดึงกล้อง และไม่แสดง afterimage ระดับใบหน้าหลังย้าย; เส้นหยุดตามระยะที่ถูกบังจริง
- **Performance-safe version:** เส้นทาง DUST ครั้งเดียว 10 จุด + CLOUD ต้น/ปลาย ไม่มีแผ่นริบบิ้น
- **Why this looks better than a basic effect:** ภาพต้นทาง–ทางผ่าน–จุดลงทำให้การย้ายทันทีดูเป็นแรงลมที่มีทิศทางและอ่านได้ในการต่อสู้

### Updraft

- **Spell Name:** Updraft
- **Element:** Wind
- **Combat Role:** โจมตีรอบตัวรัศมี 5 บล็อกทันที ยกศัตรูทั่วไปที่ผ่านเงื่อนไข; บอสไม่ถูกยก
- **Visual Goal:** แรงอากาศจากพื้นพุ่งขึ้น อ่านแนวแกนตั้งและปล่อยช่องให้เห็นเป้าลอย
- **Cast Phase:** พัดเสี้ยวลมสี่ส่วนที่ระดับพื้นปรากฏพร้อม hit ไม่เปิดวงชาร์จก่อนแรงยก
- **Travel / Active Phase:** ริบบิ้นโปร่งขึ้น 2–3 แนวตามทรงกรวยเปิด โดยภาพเต็มปรากฏทันทีแล้วเลื่อน texture ขึ้นอีก 4 tick; รูปด้านบนไม่ขยายออกเกินรัศมีจริง
- **Impact Phase:** เฉพาะเป้าที่ได้รับแรงยกจริงมีไอพุ่งใต้เท้าสั้นตามการเคลื่อนครั้งนั้น; บอส accepted hit เห็นรอยกระแทกลมที่เท้าไม่ใส่เส้นแสดงการยกปลอม
- **Lingering Effect (if any):** ลมฉากหายภายใน 8 tick ไม่สร้างเสาลมต่อเนื่อง; status markers คงตามระบบแยก
- **Suggested Particles:** DUST มิ้นต์ตามแนวขึ้น, CLOUD รวม 6–8 จุดที่พื้น, SWEEP_ATTACK เล็กเฉพาะแรงปล่อย
- **Suggested Sounds:** `minecraft:entity.breeze.wind_burst` เบาหนึ่งครั้ง เสริมลมกวาดสูงสั้น ไม่ใช้เสียงพายุ loop
- **Camera / readability notes:** ให้ช่องว่างตรงกลางเสมอ เห็นแนวยกจากด้านข้าง; ไม่มีการยกภาพผู้เล่น/กล้องแยกจาก physics จริง
- **Performance-safe version:** DUST ขึ้น 4 แนวรวม 20 จุดครั้งเดียว + CLOUD ต่ำ 4 จุด ไม่มีกรวย display
- **Why this looks better than a basic effect:** การจัดทิศขึ้นเป็นภาษาภาพเฉพาะของแรงยกและบอกความต่างระหว่างเป้าที่ยกได้กับบอส

### Wind Barrier

- **Spell Name:** Wind Barrier
- **Element:** Wind
- **Combat Role:** ลดความเสียหายกระสุน 35% นาน 100 tick/5 วินาที
- **Visual Goal:** กระแสอากาศหมุนป้องกันกระสุน เห็นว่าเป็นม่านลดแรง ไม่ใช่กำแพงหยุด projectile
- **Cast Phase:** เสี้ยวลม 3 ชิ้นคลี่รอบลำตัวใน tick ที่ buff เริ่มและเคลื่อนขึ้นข้างไหล่ 3 tick
- **Travel / Active Phase:** วงรีเปิดสามเสี้ยวหมุนสลับด้านข้างตัวที่ระดับเอวถึงหัวไหล่ ไม่วาดพื้นเป็น arena; ไม่มีผิวทึบรอบหัว
- **Impact Phase:** เมื่อถูก projectile hit ที่ reduction ทำงาน ให้เส้นลมบริเวณโดนแยกเป็นสองทาง 3 tick และประกายมิ้นต์เล็ก; ไม่ลบ/สะท้อนกระสุนเพื่อเอฟเฟกต์
- **Lingering Effect (if any):** คงตาม projectile_reduction จริงและหยุดเมื่อหมด; ปลายลมคลายขึ้น 4 tick โดยความเข้มลดชัด
- **Suggested Particles:** DUST เล็กบนปลายเสี้ยว, CLOUD ไม่เกิน 2 จุดต่อ hit feedback
- **Suggested Sounds:** `minecraft:entity.player.attack.sweep` เบาตอนเริ่ม, `minecraft:entity.breeze.slide` สั้นเมื่อกัน hit โดยคุม cooldown เสียง
- **Camera / readability notes:** มือและหน้าของผู้ร่ายยังเห็นชัดทั้ง Java/Bedrock; ห้ามทำเสียงสะท้อนกลับหรือภาพป้องกันสมบูรณ์เมื่อยังได้รับ damage
- **Performance-safe version:** DUST 6 จุดโคจรทุก 10 tick และเส้นแยกเฉพาะ projectile impact ไม่มี display ติดตัว
- **Why this looks better than a basic effect:** เสี้ยวลมที่แยกแรงตอนโดนกระสุนอธิบายหน้าที่ป้องกันได้ตรง โดยไม่ทำให้เข้าใจว่าเป็นโล่กันทุกชนิด

### Cyclone Trap

- **Spell Name:** Cyclone Trap
- **Element:** Wind
- **Combat Role:** สนามรัศมี 4 บล็อก 80 tick/4 วินาที 4 pulse พร้อมแรงดึงและ spread ตามระบบ
- **Visual Goal:** พายุทรงกรวยที่ดูดเข้าสู่ศูนย์ชัด ต่างจาก Scorching Vortex ที่พุ่งไฟขึ้น
- **Cast Phase:** เสี้ยวลูกศรตามขอบสนามปรากฏพร้อม pulse แรกและชี้เข้าแกน ไม่มีช่วงชาร์จใหม่
- **Travel / Active Phase:** ริบบิ้นอากาศรูปเกลียว 3 เส้นล้อมแกนสูงประมาณ 3 บล็อก โดยฐานกว้างและช่องว่างใหญ่ เส้นไหลจากขอบเข้าด้านในแล้วขึ้นเล็กน้อย แสดงพื้นที่รัศมีจริงด้วยขอบมิ้นต์ขาดช่วง
- **Impact Phase:** ทุก pulse ที่อายุ 0/20/40/60 มีคมเสี้ยวสั้นในสนามและ accepted-hit marker; เป้าที่ได้รับ spread จริงมีเศษสีสถานะเดิมเคลื่อนจากต้นทาง ไม่สร้างวง hit เพิ่มจากภาพเอง
- **Lingering Effect (if any):** เมื่อครบ80 tick ลดเกลียวลงและคลายเป็นเส้นลม 6 tick ไม่มีสนามดึงต่อหลัง effect ปิด
- **Suggested Particles:** DUST มิ้นต์ตามเกลียว, CLOUD ขนาดเล็ก 4 จุดบนขอบต่อ pulse, อนุภาคธาตุที่ spread เฉพาะเมื่อ reaction/การกระจายเกิดจริง
- **Suggested Sounds:** `minecraft:entity.breeze.charge` ต่ำเบาเริ่มหนึ่งครั้ง, `minecraft:entity.player.attack.sweep` เบาตาม pulse ไม่เปิด loop ทุก tick
- **Camera / readability notes:** ข้างในพายุต้องเห็น mob และพื้น; แรงดึงไม่แสดงกับบอสต้านแรงแม้เส้นสนามจะไหลเข้า; ไม่ทำ cyclone สูงจนปิด boss bar
- **Performance-safe version:** เกลียว DUST 2 เส้นรวม 18 จุดทุก 4 tick กับขอบทุก 10 tick; ตัด CLOUD ก่อนขอบสนาม
- **Why this looks better than a basic effect:** เกลียวที่มีทิศไหลและแกนโปร่งบอกทั้งแรงดึงและศูนย์สนาม ทำให้ดูเป็นพายุที่ควบคุมได้

### Thousand Wind Blades

- **Spell Name:** Thousand Wind Blades
- **Element:** Wind
- **Combat Role:** โจมตีแนวเล็ง 12 ครั้ง ทุก 3 tick ตั้งแต่อายุ 0 ถึง33; ระยะ22บล็อก แต่ละแนวทำงานทันทีและเล็งใหม่ตามผู้ร่าย
- **Visual Goal:** ฝนคมลมที่มีจังหวะและจำนวนอ่านได้ สร้างความหนาแน่นด้วยลำดับ ไม่วาดใบมีดหนึ่งพันเอนทิตี
- **Cast Phase:** เสี้ยวใบมีดเล็ก 3 ตำแหน่งด้านข้างปลายคทาพร้อมแนวแรก ไม่มีเวลาสะสมก่อนอายุ0
- **Travel / Active Phase:** แต่ละแนวเปิดริบบิ้นตรงครบระยะทันทีและมีคมเสี้ยวสั้นที่ต้นเส้นเป็น followthrough 2 tick; สลับเอียงซ้าย–ขวา–กลางให้จำ12จังหวะได้ แต่แนว hit ใช้ aim จริงของ pulse นั้น ไม่ตามตำแหน่งจาก cast แรก
- **Impact Phase:** hit spark มิ้นต์เล็กบน accepted targets ต่อ pulse; รวบ feedback บนเป้าเดียวภายใน 3 tick และไม่มีเสียง slash ทุกเป้าจนเกิดเสียงรัวหนัก
- **Lingering Effect (if any):** คมแต่ละเส้นมีอายุ2–3tick จึงมีไม่เกิน2ชุดซ้อนกัน; จบครบ12แนวแล้วเส้นคทาคลาย4tick ไม่มีการโจมตีที่13
- **Suggested Particles:** DUST 6–10 จุดตามแนวแต่ละครั้ง, SWEEP_ATTACK ไม่เกิน1จุดต่อ pulse, CLOUD ตอนเปิด/จบเท่านั้น
- **Suggested Sounds:** `minecraft:entity.player.attack.sweep` เบาเป็นกลุ่ม 3 เสียงตามลำดับแรก/กลาง/ท้าย หรือ cue สั้น6ครั้งที่เว้นpulse เพื่อลดเสียงซ้อน
- **Camera / readability notes:** วางคมประกอบข้างคทาไม่กระจายเต็มจอ; แสดงทั้ง12tick eventในรูปแม้ Low modeตัดเศษ; หยุดแนวแต่ละครั้งตรงสิ่งกีดขวาง
- **Performance-safe version:** ไม่มี display ใช้ DUST line8จุดต่อpulseและ hit2จุดต่อเป้า ไม่ลดจำนวนpulseหรือเปลี่ยนการเล็งเพื่อประหยัดภาพ
- **Why this looks better than a basic effect:** การสลับคมและจังหวะรัวที่มีช่องเงียบทำให้รู้สึกเป็นชุดใบมีดเร็ว แทนเส้นฝุ่นเดียวค้างเกือบ2วินาที

### Tempest Dominion

- **Spell Name:** Tempest Dominion
- **Element:** Wind
- **Combat Role:** Ultimate สนามติดตามผู้ร่าย รัศมี7บล็อก120tick/6วินาที6pulse พร้อมแรงดึงเดิม
- **Visual Goal:** ผู้ร่ายยืนเป็นศูนย์กลางมหาพายุที่ขอบเคลื่อนตามได้ชัด โดยสนามไม่กลายเป็นกำแพงหมอก
- **Cast Phase:** คมลมใหญ่สามเสี้ยวตั้งด้านนอกผู้ร่ายพร้อมสนามและpulseแรก เปิดช่องตรงกลางคงสายตาไว้
- **Travel / Active Phase:** เสี้ยวอากาศโค้ง3ชั้นที่ระดับ0.8/2.2/4บล็อกหมุนต่างความเร็วเป็นโครงพายุเปิด ขอบสนามตามตำแหน่งผู้ร่ายจริง; จังหวะย้ายขอบให้คงรัศมี7 ไม่ใช้วงแสงล่าช้าจนแสดงพื้นที่ผิด
- **Impact Phase:** ทุก20tickมีหน้าคมลมสั้นผ่านสนามครบ footprint กับ hit feedback ต่อเป้าจริง; pulseท้ายที่100สว่างขึ้นเล็กน้อยโดยไม่มีfinaldamageเพิ่ม
- **Lingering Effect (if any):** โครงพายุหมดที่120tick เส้นตกแต่งจางได้6tickโดยลดความสว่างชัดและไม่ทิ้งขอบอันตรายค้าง
- **Suggested Particles:** DUST มิ้นต์/ขาวตามเสี้ยว, CLOUD เล็ก8จุดต่อpulseบริเวณขอบ ไม่วางเต็มทรงกระบอก
- **Suggested Sounds:** `minecraft:entity.breeze.wind_burst` เบาตอนเริ่มและpulseท้าย; เสียงลมต่ำคุมระยะเป็นcueต่อpulseใหญ่ ไม่ใช้เสียงเดียวซ้อนทุกframe
- **Camera / readability notes:** ส่วนที่สูงทำเป็นเส้นข้าง ไม่ปิดพื้นเตือนภายใน7บล็อก; ไม่มีcamera/FOV effect; ต้องตามการย้าย/หยุดของเจ้าของจริง
- **Performance-safe version:** ขอบDUST28จุดทุก10tick + เกลียว18จุดทุก4tickและpulseเส้นสั้น เก็บศูนย์เคลื่อนที่และขอบสนามเสมอ
- **Why this looks better than a basic effect:** ระดับความสูงกับความเร็วหมุนแยกชั้นทำให้รู้สึกเป็นอาณาเขตลมใหญ่ โดยใช้เพียงเส้นโครงที่ยังอ่านสนามได้

### Stone Bullet

- **Spell Name:** Stone Bullet
- **Element:** Earth
- **Combat Role:** กระสุนเดี่ยวระยะ24บล็อก ความเร็ว1.6บล็อก/tick พร้อมแรงผลักศัตรูทั่วไปที่ผ่านเงื่อนไข
- **Visual Goal:** ก้อนหินอัดแน่นที่หนักและมีแกนแร่ ต่างจากลูกไฟหรือฝุ่นลอย
- **Cast Phase:** เศษเหลี่ยมสามชิ้นหุบรวมเป็นก้อนที่ปลายคทาพร้อมกระสุนออกในtickแรก ไม่เพิ่มเวลาชาร์จ
- **Travel / Active Phase:** หัวหินทรงเหลี่ยม0.4บล็อก ใช้textureไขว้สองระนาบให้Java/Bedrockอ่านมวลใกล้เคียง; รอยแร่สว่างด้านเดียวและหางเศษสั้น1บล็อก ไม่หมุนเร็วเหมือนใบมีดลม
- **Impact Phase:** ก้อนแตก3–5เศษพร้อมaccepted hitและฝุ่นต่ำไม่เกิน0.6บล็อก; เมื่อชนผนังแตกตามnormalผนังโดยไม่มีhit sparkของศัตรู
- **Lingering Effect (if any):** เศษตกหายภายใน6tick ไม่มีไอเท็มตกพื้น; Stagger markerถ้ามีจริงแยกจากฝุ่นและไม่แสดง knockback ปลอมบนบอส
- **Suggested Particles:** BLOCK STONE4–6จุดที่impact, DUSTหิน/แร่2–3จุดตามหัว, ไม่มีBLOCK trailหนาทั้งทาง
- **Suggested Sounds:** `minecraft:block.stone.hit` ต่ำตอนออก, `minecraft:block.deepslate.break` เบาตอนimpactครั้งเดียว
- **Camera / readability notes:** หัวทึบเล็กได้แต่หางโปร่ง ระยะภาพตรงกับกระสุนจริง; ไม่ใช้ FallingBlock ที่มีphysics/ไอเท็มหรือโหลดchunkเพิ่ม
- **Performance-safe version:** DUSTก้อน3จุดทุก2tick + BLOCK6จุดตอนชน ไม่มีเอนทิตีหัวกระสุน
- **Why this looks better than a basic effect:** เศษที่รวมตัวแล้วแยกตอนชนทำให้หินมีน้ำหนักและวัสดุ ไม่ใช่จุดสีน้ำตาลที่พุ่งไปเฉย ๆ

### Earth Spike

- **Spell Name:** Earth Spike
- **Element:** Earth
- **Combat Role:** หนามจากพื้นสูงภาพประมาณ3.5บล็อก โจมตีทันทีรัศมี2.5พร้อมStaggerเดิม
- **Visual Goal:** ลิ่มศิลาโบราณที่แตกพื้นขึ้นอย่างหนักและมีรูปร่างต่างจากIce Spike
- **Cast Phase:** รอยแตกสามเส้นสีน้ำตาล/แร่เปิดที่พื้นพร้อมhitทันที ไม่รอก้อนหินโตจึงทำดาเมจ
- **Travel / Active Phase:** ลิ่มหินแผ่นกว้างหัวทู่ปรากฏพร้อมแรงกระแทก มีลิ่มรองเตี้ยข้างละชิ้น; รูนเพอริดอตเล็กสว่างด้านหน้าหนึ่งเส้น Bedrockใช้silhouetteแผ่นไขว้ ไม่สัญญาว่าเป็นโมเดลทึบที่เดินชนได้
- **Impact Phase:** แผ่นร้าวพื้นและหินกระเด็นออกต่ำ4ชิ้น ยอมให้มวลภาพหยุด1–2tickก่อนยุบเพื่อให้รู้สึกหนัก
- **Lingering Effect (if any):** ลิ่มคืนพื้นแบบภาพภายใน8tick ไม่เปลี่ยนblockหรือเพิ่มobstacle; สัญลักษณ์Staggerอยู่ตามค่าจริง
- **Suggested Particles:** BLOCK STONE/COARSE_DIRTรวม8จุด, DUSTแร่ตามร่อง2–3จุด ไม่ปล่อยควันสูงทับเป้า
- **Suggested Sounds:** `minecraft:block.stone.place` ต่ำและ `minecraft:block.deepslate.break` เบาโดยเลือกเป็นเสียงกระแทกหลักเดียว
- **Camera / readability notes:** ลิ่มกว้างที่ฐานแต่กลางภาพโปร่งพอเห็นเป้า ขอบรัศมี2.5มีหมุดร้าวชัด; ไม่แสดงการยกศัตรูเพราะไม่มีknock-upในสกิลนี้
- **Performance-safe version:** ลิ่มDUST3ขอบรวม15จุดพร้อมBLOCK6จุดเฉพาะimpact ไม่มีแผ่นหินค้าง
- **Why this looks better than a basic effect:** รูปหัวทู่ ร่องแร่ และจังหวะหยุดทำให้ความหนักของEarthต่างจากคมบางของIceทั้งที่ใช้จุดโจมตีคล้ายกัน

### Stone Wall

- **Spell Name:** Stone Wall
- **Element:** Earth
- **Combat Role:** กำแพงจำลอง5คูณ3บล็อกนาน120tick/6วินาที มีการกั้นผู้เล่นศัตรู/กระสุนตามกลไกเดิม
- **Visual Goal:** กำแพงศิลาอ่านการปิดทางได้ตรงกรอบcollisionจริงทั้งสองแพลตฟอร์ม
- **Cast Phase:** ถ้าตรวจสร้างสำเร็จ แสดงกรอบความกว้างและความสูงเต็มทันทีพร้อมwall active แล้วให้ฝุ่นวิ่งจากฐานขึ้นเป็นfollowthrough4tick ไม่ค่อย ๆ สร้างช่องที่ภาพบอกว่ายังเดินผ่านได้
- **Travel / Active Phase:** JavaคงBlockDisplay15ก้อนเดิมและเสริมร่องแร่2–3แนว; Bedrockใช้แผ่นfront/backภาพศิลาที่ล้อมกรอบเดียวกันพร้อมสันข้างด้วยDUST ควรแสดงสองด้าน ไม่สมมติว่าGeyserแปลBlockDisplayได้เอง
- **Impact Phase:** กระสุนที่ระบบwallลบจริงแตกที่จุดชนด้วยเศษหินน้อย; การเดินชนแสดงรูนกระพริบต่ำหนึ่งครั้งโดยคุมถี่ ไม่สร้างbouncephysicsใหม่
- **Lingering Effect (if any):** กำแพงอยู่จนeffectปิดและหายพร้อมcollision; ฝุ่นสลายตามหลัง4tickได้โดยกรอบsolidดับทันที ไม่แสดงรอยแยกเหมือนกำแพงยังอยู่
- **Suggested Particles:** DUSTหิน/แร่บนขอบ, BLOCK STONEไม่เกิน6จุดต่อprojectile impact, ไม่มีฝุ่นต่อทุกก้อนทุกtick
- **Suggested Sounds:** `minecraft:block.stone.place` ตอนสร้าง, `minecraft:block.stone.hit` เบาเมื่อชนโดยจำกัดต่อwall5tick, `minecraft:block.deepslate.break` ตอนหมด
- **Camera / readability notes:** วางรูปเท่ากรอบworld-axisของwallจริง ไม่หมุนกำแพงตามกล้อง; ฝั่งBedrockใช้กรอบสำรองเสมอถ้าtextureหาย และอย่าสื่อว่าหยุดmob pathfindingทุกชนิด
- **Performance-safe version:** Javaใช้15BlockDisplayที่มีอยู่และตัดruneเพิ่ม; Bedrockใช้กรอบเส้นDUSTตามมุมจริงกับเส้นตั้ง2ช่วงทุก10tick คงทั้งหน้า/ด้านข้างและความสูง
- **Why this looks better than a basic effect:** ผิวศิลากับกรอบเดียวกันทั้งสองฝั่งทำให้ปฏิสัมพันธ์wallชัด และการแตกตรงจุดชนตอบสนองต่อการป้องกันจริง

### Iron Skin

- **Spell Name:** Iron Skin
- **Element:** Earth
- **Combat Role:** ลดความเสียหาย25%นาน140tick/7วินาที
- **Visual Goal:** ผิวป้องกันที่หนักแน่นเหมือนแผ่นโลหะ/ศิลาแร่ ไม่เป็นเกราะน้ำแข็งสีน้ำตาล
- **Cast Phase:** แถบโลหะเหลี่ยมเล็กประสานจากเอวขึ้นไหล่พร้อมbuffเริ่ม เส้นรอยต่อเพอริดอตติด1tick
- **Travel / Active Phase:** ใช้plateข้างหัวไหล่และข้างลำตัวรวม2–3ชิ้น แทบไม่โคจร มีเพียงการยกตามจังหวะเดินเพื่อให้รู้สึกมั่นคง; ไม่เปลี่ยนชุดเกราะจริงของผู้เล่น
- **Impact Phase:** เมื่อreductionทำงานจริง plateด้านรับแรงแสดงรอยกระแทกทรงสี่เหลี่ยมและประกายโลหะ2tick ไม่หุ้มทั้งจอหรือเล่นเสียงanvilดัง
- **Lingering Effect (if any):** จบตามbuffจริง แถบประสานคลายแล้วจาง4tick; ไม่ทิ้งเกราะitem/attributeเพิ่ม
- **Suggested Particles:** DUSTหินและแร่2–4จุดต่อhit, CRIT2จุดแทนประกายโลหะหากจำเป็น ไม่รัวทั่วตัว
- **Suggested Sounds:** `minecraft:block.chain.place` เบาตอนประสาน, `minecraft:block.anvil.hit` เบามากpitchต่ำเมื่อโดน โดยจำกัด5tickต่อผู้เล่น
- **Camera / readability notes:** ภาพอยู่ข้างลำตัว ไม่บังมือหลัก/หัว; เป็นreductionที่ยังเสียHPได้ จึงไม่ใช้สัญลักษณ์blockสมบูรณ์หรือสะท้อนกลับ
- **Performance-safe version:** รูนDUSTที่ไหล่สองจุดทุก10tick และspark2จุดเมื่อhitจริง ไม่มีplateเอนทิตี
- **Why this looks better than a basic effect:** การเคลื่อนไหวต่ำกับเสียงโลหะสั้นให้ความหนักแน่นตรงบุคลิกEarth และผู้เล่นรู้สึกถึงbuffผ่านการรับแรง

### Quicksand

- **Spell Name:** Quicksand
- **Element:** Earth
- **Combat Role:** สนามรัศมี4บล็อก100tick/5วินาที5pulse พร้อมslow35%ที่ต่ออายุตามhitจริง
- **Visual Goal:** พื้นทรายที่รั้งเท้าด้วยการไหลเข้าช้า ต่างจากวังน้ำวนที่หมุนลื่น
- **Cast Phase:** ร่องแตกระแนงแห้งและหมุดขอบทรายเกิดพร้อมpulseแรก พื้นโลกยังเป็นblockเดิม
- **Travel / Active Phase:** แถบเม็ดทรายโปร่ง3แนวเคลื่อนเข้ากลางอย่างช้า ๆ ในfootprintจริง ผิวไม่ต่ำลงและไม่มีหลุมที่อ้างว่าตกได้; เต้นเล็กตามpulseทุก20tick
- **Impact Phase:** accepted targetมีเส้นทรายหนืดต่ำรอบข้อเท้า2–3tick และmarker slowจริง อย่าดึงภาพตัวละครเข้ากลางเพราะกลไกนี้ไม่ใช่pull
- **Lingering Effect (if any):** สนามหมด100tick; markerเท้าตามอายุslowที่ได้รับจริงซึ่งอาจยาวกว่าภาพสนามเล็กน้อย ไม่ปล่อยรอยพื้นอันตรายค้าง
- **Suggested Particles:** DUSTทรายบนร่อง, BLOCK SAND/COARSE_DIRT4จุดต่อpulseตรงจุดหลัก ไม่พ่นเม็ดต่อผู้เล่นทุกtick
- **Suggested Sounds:** `minecraft:block.sand.hit` ต่ำเบาเฉพาะpulseแรก/ท้าย, `minecraft:block.gravel.step` สั้นเมื่อslowได้รับจริงแบบคุมถี่
- **Camera / readability notes:** ต้องเห็นmarkerบอสบนพื้นผ่านtextureได้; รูปslowไม่สูงเกินข้อเท้าและไม่ใช่กรงตรึง เพราะเป้ายังเดินได้
- **Performance-safe version:** ขอบDUST20จุดทุก10tick ร่องไหล8จุดเฉพาะpulse และslow marker2จุดต่อเป้า ไม่มีพื้นแผ่นกว้าง
- **Why this looks better than a basic effect:** ทิศเม็ดที่รั้งเข้ากลางกับรอยเท้าหนืดสื่อความหนักของทรายโดยไม่ต้องขุดพื้นจริงหรือทำฝุ่นปิดฉาก

### Seismic Slam

- **Spell Name:** Seismic Slam
- **Element:** Earth
- **Combat Role:** กระแทกทันทีรอบตัวรัศมี5บล็อกพร้อมStagger; ไม่มีแรงยกใหม่
- **Visual Goal:** การส่งแรงลงพื้นแล้วแผ่รอยแตกสั้น มีน้ำหนักในชั่วขณะเดียว
- **Cast Phase:** คทาชี้ลงเชิงภาพและตรามุมหินเปิดที่เท้าพร้อมhitเดิม ไม่ล็อกมือ/รอanimationผู้เล่น
- **Travel / Active Phase:** รอยร้าว6กิ่งปรากฏเต็มระยะพร้อมผลเกม จากนั้นเม็ดฝุ่นวิ่งตามกิ่งออกใน4tickเป็นfollowthrough; หินกึ่งเสาสูงภาพ2บล็อกแคบข้างตัวช่วยเน้นแกนไม่ทับศีรษะผู้ร่าย
- **Impact Phase:** แผ่นกระแทกต่ำรูปหลายเหลี่ยมและเศษหิน4ชิ้นออกเป็นทิศหลัก; accepted targetsได้รอยแตกแร่สั้นข้างเท้า ไม่ยกกล้องหรือใช้explosionทั้งจอ
- **Lingering Effect (if any):** รอยร้าวหาย8tick; Stagger statusตามค่าจริง ไม่มีเศษblockกีดขวางหรือกับดักที่มองว่ายังทำดาเมจ
- **Suggested Particles:** BLOCK STONE8–12จุดที่ฐาน, DUSTสีน้ำตาล/แร่ตามกิ่ง, CLOUD2จุดต่ำเฉพาะแกนถ้าจำเป็น
- **Suggested Sounds:** `minecraft:block.deepslate.break` ต่ำเป็นimpactหลักหนึ่งครั้ง เสริม `minecraft:block.note_block.basedrum` เบาแทนกล้องสั่น
- **Camera / readability notes:** รูปแผ่ของภาพเกิดทันทีเต็มรัศมี ไม่ให้ขอบคลื่นที่วิ่งช้าเข้าใจว่าอีก1วินาทีจึงโดน; ไม่มีscreen shake
- **Performance-safe version:** รอยแตกDUST6กิ่งรวม24จุดพร้อมBLOCK8จุดครั้งเดียว ไม่มีเสาหรือแผ่นยก
- **Why this looks better than a basic effect:** เศษหินที่แยกออกตามรอยแตกและเสียงต่ำในจังหวะเดียวทำให้รับรู้แรงกระแทกหนัก แม้ไม่เพิ่มจำนวนภาพมาก

### Golem Guardian

- **Spell Name:** Golem Guardian
- **Element:** Earth
- **Combat Role:** ผู้พิทักษ์Iron Golemเดิมหนึ่งตัวต่อผู้ร่าย อายุ300tick/15วินาที เคลื่อน/โจมตีตามระบบsummonเดิม
- **Visual Goal:** ทำให้golemvanillaดูเป็นผู้พิทักษ์แร่ของเจ้าของ โดยไม่ต้องเปลี่ยนเป็นโมเดลยักษ์ที่ต่างกันสองclient
- **Cast Phase:** รอยฐานศิลารูปสี่เหลี่ยมแตกเปิด ณ ตำแหน่งspawnที่ตรวจsafeแล้วพร้อมentityเกิด; ริบบิ้นแร่ช้อนขึ้นใต้แขนสองข้าง3tick ไม่ทำspawnเพิ่ม
- **Travel / Active Phase:** คงตัวIron Golemnativeที่ทั้งสองฝั่งเห็นได้ เติมร่องแร่เล็กข้างไหล่และหมุดสังกัดต่ำที่ฐาน อัปเดตตามตำแหน่งgolemจริง ไม่จำลองAIหรือanimationเดินแยกจากตัว
- **Impact Phase:** เมื่อโจมตีจริงตามtickและเป้ายอมรับdamage ให้เสี้ยวหินสั้นข้างหมัดกับรอยกระแทกบนเป้า; การswingที่ไม่โดนไม่มีhitแตกใหญ่
- **Lingering Effect (if any):** ขอบแร่ติดตามจนsummonถูกล้าง/แทน/หมดเวลา15s; entityหายแล้วแสงร่องศิลาจาง4tick ไม่มีซากitemหรือXPเพิ่ม
- **Suggested Particles:** DUSTเพอริดอตที่ร่อง2–3จุดทุก10tick, BLOCK STONE4จุดต่อaccepted strike
- **Suggested Sounds:** `minecraft:entity.iron_golem.repair` เบาตอนspawn, `minecraft:entity.iron_golem.attack` เบาเฉพาะstrikeที่ต้องเน้น ไม่เปิดเสียงnativeกับเสียงซ้อนดังพร้อมกัน
- **Camera / readability notes:** nativegolemเป็นsilhouetteหลักตามขนาดจริง; ถ้าอยากใส่herooverlayต้องไม่ทำให้ดูว่าแขนโจมตีไกลกว่าระยะเดิม และสีสังกัดต้องเห็นผ่านคนอื่น
- **Performance-safe version:** ใช้golemเดิมกับDUSTสังกัด1จุด/10tick และBLOCK4จุดตอนตี ไม่มีdisplayติดตัวเพิ่ม
- **Why this looks better than a basic effect:** รูนแร่และฐานศิลามีจังหวะspawn/โจมตี/สลายครบ ทำให้entityเดิมมีบุคลิกผู้พิทักษ์ของธาตุโดยต้นทุนต่ำ

### Titan's Judgment

- **Spell Name:** Titan's Judgment
- **Element:** Earth
- **Combat Role:** Ultimate ร่ายเดิม40tick/2วินาทีแล้วกระแทกพื้นที่รัศมี6บล็อกพร้อมStagger
- **Visual Goal:** มวลศิลายักษ์ที่อัดน้ำหนักลงจุดเป้า มีคำเตือนอ่านได้ชัดและไม่อ้างsummonTitanจริง
- **Cast Phase:** ช่วง0–40tickวางขอบหลายเหลี่ยมตามรัศมี6จริงและร่องร้าวชี้ศูนย์ ด้านบนประกอบsilhouetteหัวค้อน/กำปั้นศิลาจากfacet4–6ชิ้นสูงไม่เกิน8บล็อก ค่อยล็อกรอยต่อก่อนhit
- **Travel / Active Phase:** ให้มวลภาพตกในช่วงท้าย32–40tickจนผิวหน้าสัมผัสพื้นตรงtick40พอดี ไม่มีdamageตอนระหว่างตกและไม่มีการตามเป้าเล็งใหม่; ลดความสูงภาพเมื่อเพดานต่ำโดยคงfootprint
- **Impact Phase:** ที่tick40ค้อนแตกเป็นแผ่นด้านข้างพร้อมรอยร้าวเต็มรัศมีทันที ฝุ่นต่ำแยกตาม4ทิศและhit markerของเป้าที่ยอมรับจริง; กระแทกหลักหนึ่งครั้งไม่ทำmeteorหลายpulse
- **Lingering Effect (if any):** มวลและรอยพื้นสลาย8–12tickโดยลดความทึบ ไม่มีblockที่พังจริงหรือพื้นที่damageต่อ; ถ้าร่ายถูกขัดต้องยุบภาพก่อนimpactทันที
- **Suggested Particles:** BLOCK STONE/DEEPSLATEรวม12–16จุดที่impact, DUSTแร่ตามรอยต่อ, CLOUD4จุดต่ำแต่ไม่ปิดพื้นboss
- **Suggested Sounds:** `minecraft:block.grindstone.use` ต่ำเบาตอนสะสม, `minecraft:block.note_block.basedrum`+`minecraft:block.deepslate.break`เป็นimpactสองชั้นที่จำกัดvolume ไม่ใช้เสียงระเบิดหลายครั้ง
- **Camera / readability notes:** มวลภาพมีช่องว่างและไม่สูงจนครอบจอ; ขอบเตือนกับศูนย์ต้องเห็นจากใต้ค้อนและทุกมุม ห้ามcamera shake/teleportเพื่อสร้างความหนัก
- **Performance-safe version:** คงขอบเตือน24จุดและโครงค้อนDUST12จุดวาดทุก4tick; impactรอยแตก24จุดพร้อมBLOCK12จุด ไม่มีfacetเอนทิตีขนาดใหญ่
- **Why this looks better than a basic effect:** การประกอบมวลหนัก–ตก–แตกที่ลงพร้อมdamageเป็นเรื่องราวสามช่วงที่อ่านทัน ช่วยให้สกิลรู้สึกเป็นคำพิพากษาของTitanด้วยต้นทุนที่ควบคุมได้
