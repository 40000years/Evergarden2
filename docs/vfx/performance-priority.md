## 4. Performance optimization notes

### ทำให้ประทับใจด้วยรูปทรงและจังหวะ

ภาพหนึ่งเส้นที่มีหัว หาง และจุดจบตรงการโจมตีให้ข้อมูลมากกว่าอนุภาคสุ่มร้อยจุด ใช้พื้นที่ว่าง สีต่างระดับ และการหยุด/เร่งของรูปหลักสร้างความแรง อัลติไม่ต้องเพิ่มระยะเวลาร่ายหรือปกคลุมทั้งจอเพื่อให้ดูยิ่งใหญ่

ภาพที่ทุกคนเห็นต้องเก็บข้อมูลร่วม: แนวโจมตี พื้นที่อันตราย และสถานะที่สำคัญ แล้วลดเฉพาะชั้นเสริมของเพื่อนร่วมทีม ผู้เล่นเครื่องเบายังหลบสกิลได้จากcueเดียวกัน กรณีเป้าอยู่ไกลลดรายละเอียดโดยไม่ย้ายตำแหน่งimpactให้ใกล้กล้อง

### งบแสดงผลตั้งต้นก่อนทำ profiling

ตัวเลขต่อไปนี้เป็นค่าทดลองสำหรับระบบใหม่ ไม่ใช่ผลbenchmarkหรือคำรับประกันความจุเซิร์ฟเวอร์ ต้องวัดทั้งserver tick,packet trafficและframe timeของBedrockมือถือ

| งบต่อผู้ชม | Low | Balanced | High |
| --- | ---: | ---: | ---: |
| ระยะรายละเอียดเต็มโดยประมาณ | 16 บล็อก | 24 บล็อก | 32 บล็อก |
| ระยะขอบ/แนวสำคัญ | 40 บล็อก | 48 บล็อก | 48 บล็อก |
| แผ่นภาพที่เห็นพร้อมกัน | 16 | 40 | 64 |
| อนุภาคที่ขอวาดต่อวินาทีโดยประมาณ | 250 | 600 | 1000 |
| custom Bedrock emitter packets ต่อวินาที | 40 | 80 | 120 |
| transient sound voices ที่เริ่มภายใน1วินาที | 6 | 10 | 14 |

นับแผ่นแบบweightedด้วยขนาดบนหน้าจอ: แผ่นโปร่งขนาด10บล็อกที่ใกล้ตาแพงกว่าแผ่น1บล็อกมาก การมี40แผ่นไม่ใช่สิทธิ์ให้เอาแผ่นยักษ์40ชั้นซ้อนกัน ระดับworldให้เริ่มเพดานประมาณ128แผ่นใหม่พร้อมกัน โดยนับwall/nativeentityที่มีอยู่แยกตามชนิดและให้เจ้าหน้าที่ปรับจากผลวัดจริง

เมื่อถึงbudget ลดเศษ/ชั้นglow/เมฆก่อน จากนั้นลดความถี่ribbonและstatusที่ไม่สำคัญ เก็บหัวกระสุน ขอบอันตราย และhitสำคัญ เปลี่ยนheroเป็นโครงเส้นได้ทันที อย่าปิดVFXรวมเมื่อcastเต็มจนไม่มีคำเตือน; ถ้างบcueสำคัญเกินเพดานให้รวมเส้นหรือหยุดแผ่นตกแต่งเพื่อสงวนพื้นที่

### Scheduler, entities และ packets

- ใช้EffectEngineที่มีอยู่เป็นเจ้าของscene ไม่สร้างBukkitRunnableต่อเศษหรือชิ้นแผ่น; logicกระสุนยังเดินทุกtickตามเดิมแต่rendererอาจอัปเดต2tickพร้อมribbonเชื่อมตำแหน่งก่อนหน้า
- แผ่นสนาม/การหมุนทั่วไปอัปเดต4tickและใช้Java interpolationเฉพาะการเคลื่อนไหวภาพ หัวชน/beamimpactตั้งตำแหน่งทันทีเพื่อไม่ให้เส้นยาวกวาดออกข้างหรือimpactช้า
- Bedrock emitterมีอายุสั้นเท่ารอบส่งที่ตั้งไว้ประมาณ0.10/0.20sเพื่อลดการค้างสองภาพ bridgeเดิมมีวิธีตรึงworld planeและย้ายจุดเปิดemitterใกล้ผู้ชมอยู่แล้ว คงworld offsetเดิมทุกครั้ง; ตรวจว่าการdrop frame/latencyไม่ทำให้ภาพขาดหรือซ้อนหนา
- Cacheรายชื่อผู้ชมตามโลก/ระยะทุก10–20tick แล้วตรวจworld/onlineก่อนส่งอีกครั้ง ไม่วนผู้เล่นทั้งเซิร์ฟเวอร์ต่อparticle วัดจำนวนpacketจริงเมื่อมีหลายfieldพร้อมกัน
- reuseแผ่นเดิมในsceneแทนspawn/removeทุกframe แต่ตอนsceneจบให้removeจริง ไม่เก็บdisplayที่scale0เป็นpoolในโลกจนลืมล้าง; cacheได้เฉพาะmodel/template/mathที่ไม่มีworld entity
- สุ่มshapeจากseedของcastหนึ่งครั้ง เช่นสายฟ้า/ตำแหน่งฟอง แล้วreusepath ไม่ทำrandomwalk/targetsearchทุกframe ค่าDUSTOptionsและBlockDataใช้cacheตามpalette/material
- ไม่ใช้armorstandเป็นตัวแทนparticleหลายสิบตัว ไม่ใช้FallingBlock,itemdrop,tntหรือworldlightningที่มีphysics/damageเพิ่ม; nativegolemและshadowที่มีอยู่เป็นเกมหลักอยู่แล้ว
- ไม่มีchunkloadเพื่อภาพ; ขอบชิ้นที่ข้ามเข้าchunkที่ไม่loadedให้ตัด ไม่raytraceใหม่เพื่อตรวจdamage ใช้segment/impactที่simulationส่งมา
- จำกัดhit spark/เสียงบนเป้าเดียวใน2–5tickเพื่อไม่ให้12hitที่หน้าจอเดียวกันเป็นแสงก้อน; ปริมาณdamageจริงยังทำงานทุกhit
- กรองเสียงตามผู้ได้ยินจริง ใช้เสียงต่ำให้รู้สึกหนักแทนเพิ่มvolumeจนได้ยินทั้งเมือง รักษาเสียงเตือนบอสและhitสำคัญเป็นชั้นหน้า
- เมื่อeffectถูกขัด แทนที่ เจ้าของตาย/ออก/เปลี่ยนโลก โลก unload หรือปลั๊กอินปิด ให้ล้างscene,display,การส่งpacketและเสียงloopทั้งหมด; ภาพสำรองต้องไม่ใช้taskที่หลุดจากเจ้าของ

### การผูก renderer โดยแก้ core เท่าที่จำเป็น

เพิ่มfacadeภาพที่เปิดให้elementalเรียกได้ เช่น `ElementalVisualScene` เพราะrendererเก่าหลายclassอยู่package-privateใน`spell` ใช้primitive plane/ray/nativeparticleจากชุดเดิม แต่ไม่เรียก`MythicSpells.solar/chronos/judgment`เพื่อยืมภาพทั้งcast เพราะจะพ่วงdamage/terrain/ระยะที่ต่างจาก58เวท

จุดรับข้อมูลที่จำเป็นคือ `castStarted`, `projectileStep(from,to)`, `fieldFrame(center,radius,age)`, `pulse`, `hitAccepted`, `healApplied`, `statusChanged` และ `closed(reason)` ใช้ชื่อใดก็ได้ตามแนวโค้ดเดิม หลักคือsimulationส่งผลให้renderer; rendererไม่หาเป้าหมาย หักมานา เปลี่ยนHP ย้ายคน หรือตัดสินfreezeเอง

Reactionทั้ง8แบบเดิมควรได้เพียงaccentสั้นตามeventจริง เช่นไอน้ำขาว2–3จุด,ประกายฟ้าชิ่ง,เศษน้ำแข็ง หรือแร่หลอม โดยไม่เปิดheroUltimateอีกชุดบนhitเดียว Reactionที่สร้างfieldจริงต้องได้ขอบfieldของตนและนับbudgetร่วม การขยายจากstackให้รอยmarkerแตกเล็กน้อย แทนทำexplosionใหญ่ทุกstack

### Pipeline ของ asset และ resource pack

1. ตั้งชื่อคงที่เช่น `elemental_vfx/fire/fireball_head` และแยกinventoryiconออกจากeffecttexture เก็บsourceที่แก้ได้เช่นSVG/PNGแบบlayerพร้อมpaletteและpivot
2. Java: `items/<id>.json`ชี้`advance_magic:effect/<path>`ที่มีอยู่จริงใน`models/effect/<path>.json`; modeltextureชี้ไฟล์ใน`textures/effect/` ตรวจchainทั้งitem→model→textureรวมvanillaparent/atlasที่เกี่ยวข้องเพื่อกันmissingtextureสีดำม่วง
3. Bedrock: particleidentifierไม่ซ้ำ,texturepathตรง,UV/ขนาด/pivot/worldorientationถูกชนิด สร้างXZversionสำหรับพื้นและrayversionที่แกนยาวตรงfrom→toเมื่อจำเป็น textureparticleไม่ต้องจดGeyseritemmappingแบบคัมภีร์ เพราะถูกเรียกด้วยparticleidentifierผ่านbridge
4. packbuilderรวมassetลงZIPและMCPACKจริง ฝังไฟล์เดียวกันในJAR อัปเดตSHA-1และBedrockUUID/versionตามแนวระบบเดิม โดยversionต้องเพิ่มเมื่อเนื้อหาเปลี่ยน รักษาไฟล์พิเศษของแอดมิน/ปลั๊กอินอื่น
5. ตรวจpackที่ฝังในJARด้วยชื่อและhashจากไฟล์ที่ใช้พรีวิว ไม่ถือว่าการเห็นPNGนอกเกมหรือคอมไพล์Javaผ่านเท่ากับclientโหลดassetสำเร็จ

### สิ่งที่ทำให้เวท Minecraft ดูไม่สวย

- เปลี่ยนเพียงสีของทรงลูกปุยเดียวกันทั้ง8ธาตุ ทำให้ทุกวัสดุดูเหมือนฝุ่น
- ใส่วงรูนใหญ่ทุกสกิล รวมถึงdashและกระสุนพื้นฐาน จนเสียความต่างของบทบาท
- อัดFLAME/CLOUD/FLASHเต็มvolumeจนมองไม่เห็นบอส มือ หรือพื้นเตือน
- เปิดglowขาวตลอดและทำimpactทุกอันใหญ่เท่ากัน ไม่มีช่วงเงียบหรือhierarchy
- กระสุนภาพไปช้ากว่าdamage หรือhitเกิดก่อนภาพเตือนที่สัญญาว่าจะมีcasttime
- ขอบfieldไม่ตรงรัศมีจริง ตกแต่งสูงกลายเป็นhitboxลวง หรือแสดงเส้นวิ่งทะลุกำแพง
- กรงfreezeครอบบอสที่immune หรือฮีลตัวเลข/ประกายสำเร็จทั้งที่damage/healถูกยกเลิก
- ลายstrokeบางเกินจอมือถือ สีมืดดำสนิทในถ้ำ พื้นหลังไม่โปร่ง หรือแผ่นตั้งมองด้านหลังแล้วหาย
- วางหลายplaneบนระนาบเดียวกันจนz-fighting หรือใช้billboardหันกล้องกับลำแสงจนปลายลื่นออกเป้า
- ชิ้นเสียงยาวซ้อนทุกpulse ระเบิดดังทุกhit หรือเสียงธาตุไม่สัมพันธ์กับวัสดุ
- ใช้spriteใหญ่แทนการจัดcomposition ทำให้โปร่งหลายชั้นoverdrawหนักแม้entityน้อย
- ภาพยังค้างหลังcastcancel/หมดbuff ทำให้ผู้เล่นอ่านสถานะผิดและสะสมentityรั่ว

## 5. Implementation priority plan

### P0 — จุดเชื่อมภาพและการส่งแพ็ก

แยกfacadeVFXจากhitlogicให้รับข้อมูลเดิมได้ทั้งสองแพลตฟอร์ม ตั้งbudget/qualitypreset/cleanupที่sceneเดียว ตรวจpackreferencechainและตัวส่งGeyser particleก่อนเริ่มสร้าง58asset เป้าหมายคือprototypeรูปทรงหนึ่งชิ้นที่ยืน/หมุน/beam/แนบพื้นได้ตามโลกจริงบนทั้งJavaกับBedrock

**เกณฑ์พร้อม:** โหลดแพ็กใหม่สำเร็จในทั้งสองclient,ไม่มีดำม่วง,จากหน้า/หลัง/ใกล้พื้นยังอ่านได้,ยกเลิกcastแล้วไม่มีภาพค้าง,Lowmodeยังเห็นขอบอันตรายครบ

### P1 — ตัวอย่าง8ธาตุที่ใช้บ่อย

ทำFireball,Aqua Bullet,Frost Bolt,Wind Cutter,Stone Bullet,Radiant Bolt,Shadow Bolt,Spark Shotให้ครบสามช่วงก่อน ใช้ตัดสินความต่างของpalette/material/impactและbalanceเสียง เป้าหมายคือผู้เล่นเห็นภาพเสี้ยววินาทีแล้วแยกธาตุได้โดยไม่อ่านชื่อ

**เกณฑ์พร้อม:** หัว/หางไม่ปิดcrosshair,WindCutterยังhitทันที,อนุภาคสำรองเข้าใจเหมือนแบบเต็ม,hitถูกป้องกันไม่แสดงสถานะปลอม,เสียงไม่กลบเสียงบอส

### P2 — เส้น,พื้นที่ทันที,การพุ่ง และhit feedback

ต่อยอดribbon/segmentไปWater Blade,Inferno Spear,Thunder Spear,Glacial Path,chainทั้งสอง,dashทั้งสาม,Flame Burst,Tidal Push,Updraft,Ice/Earth Spike,Seismic SlamและPurification รวมเรื่องacceptedhit/heal/statusให้ชัดก่อนเพิ่มฉากใหญ่

**เกณฑ์พร้อม:** ภาพทันทีตรงtickจริง,เส้นหยุดที่สิ่งบัง,เส้นchainตรงชุดเป้าที่โดน,ปลายdashตรงตำแหน่งsafeจริง,บอสimmuneไม่ได้ภาพยก/แช่ลวง

### P3 — สนาม,ป้องกัน,คำสาป และsummon

ทำBurning Ground,Healing Spring,Rain Blessing,Whirlpool,Scorching Vortex,Blizzard,Cyclone Trap,Quicksand,Static Field,Holy Sanctuary; ตามด้วยBubble/Crystal Prison,Aqua Shield,Frozen Armor,Wind Barrier,Iron Skin,Stone Wall,Cursed Mark,Life Drain,GolemและShadow; Thousand Wind Bladesใช้ตัวอย่างpulseถี่สำหรับทดสอบbudget

**เกณฑ์พร้อม:** ขอบสนามและbuffหายตรงอายุจริง,shieldแตกเมื่อpoolหมด,fieldที่ตามเจ้าของอยู่ตำแหน่งเดียวกับlogic,wallBedrockเห็นกรอบที่ชนได้,หลายfieldซ้อนยังเห็นพื้นบอส

### P4 — Hero Ultimateและเวทขั้นสูงที่เหลือ

ทำMeteor Rain,Leviathan's Wrath,Titan's Judgment,Heaven's Descent,Wrath of Raijinเป็น5heroคนละชนิด แล้วต่อTsunami,Phoenix Rebirth,Absolute Zero,Tempest Dominion,Abyssal Collapse; Magma EruptionและDivine Judgmentใช้เป็นtelegraphสั้นเพื่อปรับความอ่านได้ก่อนฉากใหญ่ ทั้ง58เวทต้องมีLowmodeและfinish/cancelของตน

**เกณฑ์พร้อม:** ขนาดheroไม่เปลี่ยนhitbox,telegraph/จังหวะโจมตีคงเดิม,ไม่มีทรงใดทึบปิดสนาม,คุณภาพลดแล้วยังอ่านภาษาธาตุและการโจมตีได้

### P5 — ตรวจจริงก่อนปล่อยเซิร์ฟเวอร์

ตรวจVFXในที่สว่าง/ถ้ำ/ฝน/พื้นที่เพดานต่ำ,พื้นบันได/ครึ่งบล็อก,มุมผู้ร่าย/ผู้โดน/คนข้างสนาม,JavaและBedrockมือถือ รวมคำเตือนบอสกับเวทหลายผู้เล่น; วัดการใช้งานหลายsceneพร้อมกันกับmetricsเดิมก่อนกำหนดจำนวนผู้เล่นที่รับได้

ตรวจdeath/logout/worldchange/cancel/recast/packไม่โหลด,มานาและdamageเท่ารุ่นก่อน,hit/healที่ถูกปลั๊กอินอื่นยกเลิก,บอสimmune,wallกับprojectile และผลกระทบsoundbudget จบด้วยreleaseที่packhash/JARmanifestตรงกันและบอกข้อจำกัดclientที่ยังพบจริง

### จุดตัดสินใจที่ต้องยืนยันจากภาพในเกม

ความเหมือนของsolidshapeบนBedrock,ความสว่าง/alphaในถ้ำ,การหันworldparticleเมื่อกล้องเคลื่อน,เครื่องมือถือกับแผ่นโปร่งซ้อน และขนาดsafeheroใกล้bossต้องดูในเกม ไม่รับรองจากPNGหรือจำนวนparticleอย่างเดียว หากheroแพงให้ลดเป็นsilhouetteตามแบบPerformance-safeของเวทนั้น โดยคงข้อมูลต่อสู้และจังหวะไว้ครบ

### Source map สำหรับผู้ลงโค้ด

- Catalog: `advance-magic/src/main/java/com/example/advancemagic/elemental/ElementalSpell.java`
- พฤติกรรม58เวท: `advance-magic/src/main/java/com/example/advancemagic/elemental/ElementalSpellService.java`
- Plane/rayJava: `advance-magic/src/main/java/com/example/advancemagic/spell/MythicLineVisuals.java`
- Geyser bridge/fallback: `advance-magic/src/main/java/com/example/advancemagic/spell/MythicVisuals.java`
- ตัวอย่างการประกอบฉาก: `SolarLineVisuals.java`, `ChronosLineVisuals.java`, `JudgmentSealVisuals.java`, `LegendarySpellVisuals.java`
- Packexport: `advance-magic/tools/build_packs.py`, `tools/mythic_line_assets.py`, `tools/judgment_assets.py`

เอกสารนี้เป็นสเปกต้นทาง58รายการ รุ่น1.15.0เชื่อมprimitiveและจังหวะภาพหลักเข้ากับruntimeแล้ว ขอบเขตที่ส่งมอบและการติดตั้งอยู่ใน `docs/elemental-vfx-release.md` รายละเอียดการมิกซ์และความเหมือนของภาพระหว่างclientให้ปรับจากplaytestจริงตามแผนนี้
