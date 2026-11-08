# ช่องทางหาไอเท็ม Evergarden / Advance Magic

ตรวจเมื่อ **2026-10-30** สำหรับ **Evergarden e2.174-vault-specials** ผ่านการทดสอบบน Paper 26.2 รวม **577,932 checks** ครอบคลุมลูตทั้ง 50,000 ช่องสุ่ม คุณภาพแต่ละ Tier และตัวนับผลใน GUI ที่เทียบกับของเข้ากระเป๋าจริง ผลและ hash เก็บใน `output/vault-specials-20261030/test-receipt.json` การทดสอบฟอร์ม Bedrock จำลองเฉพาะการส่งฟอร์ม ไม่ได้เชื่อมต่อไคลเอนต์ Bedrock เอกสารนี้ตรวจช่องทางเล่น Survival จากโค้ดและ config เริ่มต้น ไม่ถือว่าคำสั่งแจกของแอดมินเป็นช่องทางหาไอเท็ม

## คทาบินที่ตกหล่น

รุ่นสองคนมีตัวไอเท็มและโมเดลแล้ว แต่ก่อน e2.172 มีเพียงเมนูแจกของแอดมิน e2.172 เพิ่มทางหาใน Vault แล้ว ส่วน e2.174 เพิ่มโอกาสของพิเศษใน Tier IV–V ดังนี้:

| ไอเท็ม | Tier I | Tier II | Tier III | Tier IV | Tier V |
| --- | ---: | ---: | ---: | ---: | ---: |
| คทาบินธรรมดา 1 คน | 0% | 0% | 0.50% | 2% | 2% |
| คทาบินระดับ 2 อัครปักษา 2 คน | 0% | 0% | 0% | **3%** | **5%** |

เรตต่อการเปิดหนึ่งครั้ง ใช้เหมือนกันใน Dark / Astral / Time Vault; รุ่นสองคนเฉลี่ยประมาณ 1 ใน 33 ครั้งที่ Tier IV และ 1 ใน 20 ครั้งที่ Tier V โดยไม่รับประกันจำนวนครั้ง ต้องมี Advance Magic ที่เปิดทำงานอยู่ มิฉะนั้นหมวดนี้ให้สมุดเอนชานต์ตาม Tier แทน รางวัลหายากเป็นรางวัลหลักหนึ่งชิ้น; ของชิ้นที่สองสุ่มเพิ่มได้เฉพาะเมื่อรางวัลหลักเป็นอุปกรณ์เพชรหรือสมุดเอนชานต์

เรต Tier V เดิม 1% มีโอกาสเปิดสำเร็จ 100 ครั้งแล้วยังไม่พบรุ่นสองคนถึง **36.60%** ส่วนเรตใหม่ 5% ลดโอกาสพลาดครบ 100 ครั้งเหลือประมาณ **0.592%** คำว่าเฉลี่ยจึงไม่ใช่จำนวนครั้งที่รับประกันว่าจะได้

Scroll of Eternity คือรางวัล **Unbreakable** ที่ลดเรตครั้งนี้: Tier II **0.05%**, III **0.10%**, IV **0.20%**, V **0.30%** เพื่อให้เป็นของตามหาและไม่เปิด Tier ต่ำแล้วได้ง่ายกว่า Tier สูง อาวุธ Rare / Legendary ใน Tier IV เพิ่มเป็น **17% / 4%** และ Tier V **22% / 6%**

แหล่งจริง: [VaultLootTable](../evergarden/src/main/java/com/example/voidscape/item/VaultLootTable.java), [RelicService](../evergarden/src/main/java/com/example/voidscape/item/RelicService.java), [FlyingStaffService](../advance-magic/src/main/java/com/example/advancemagic/item/FlyingStaffService.java)

## คุณภาพตาม Tier และ Limit Break

กติกาคุณภาพและ Limit Break คงตาม e2.173 อุปกรณ์เพชรและสมุดยังมีเอนชานต์ครั้งละหนึ่งชนิด แต่ระดับที่สุ่มได้เพิ่มตาม Tier โดยใช้ชนิด น้ำหนัก และเพดานจาก `boss-treasure.yml` เดิม เช่น Sharpness จาก V–VI ที่ Tier I ไปเป็น VIII แน่นอนที่ Tier V; Protection จาก IV–V ไปเป็น VIII การรับประกันนี้ใช้กับระดับของเอนชานต์ที่สุ่มได้ ไม่ได้บังคับให้สมุดทุกเล่มเป็น Sharpness หรือ Protection; สมุด Mending ยังเป็น I และไม่ถูกเพิ่มเข้ากลุ่มเอนชานต์บนอุปกรณ์

หมวดอุปกรณ์เพชร/สมุดรวมกันเป็นรางวัลหลัก **55.8% ใน Tier IV** และ **44.7% ใน Tier V** หากนับของชิ้นที่สองด้วย สัดส่วนที่คาดว่าจะเห็นในของทั้งหมดอยู่ที่ประมาณ **60.24% / 50.26%** ตามลำดับ

Limit Break ที่เกิดใหม่ใน Vault มีเกรดตาม Tier และเพิ่มได้ครั้งละหนึ่งระดับจนถึงเพดานของเกรดนั้น ใช้หลายใบเกรดต่ำก็ข้ามเพดานไม่ได้ ทั้ง Java และ Bedrock ใช้กติกาเดียวกัน Limit Break จากหีบลอยฟ้าที่เกิดใหม่ใช้เกรด III ส่วนใบเก่าที่ไม่มีเกรดยังใช้เพดานเดิม และไม่ลดระดับไอเท็มที่เคยได้รับ

ตัวอย่างเพดาน Limit Break: Sharpness เกรด I/II เพิ่มได้ถึง VI, III ถึง VII, IV/V ถึง VIII; Protection เป็น V/V/VI/VIII/VIII ตามลำดับ บางเอนชานต์มีช่วงระดับสั้นจึงใช้เพดานร่วมกันหลายเกรด เช่น Breach ถึง V ทุกเกรด ดู[ตารางลูต คุณภาพ และเพดานฉบับเต็ม](vault-balance.md) และ[เรตอาวุธ](vault-weapon-drops.md)

## ไอเท็มที่เปิดใช้งานและหาได้

| กลุ่มไอเท็ม | ช่องทาง Survival | โค้ดอ้างอิง |
| --- | --- | --- |
| กุญแจ Vault Tier I–V | ผ่านดันเจียนตาม Tier; ผู้พิพากษาไร้ร่างแจก Tier V | [DungeonManager](../evergarden/src/main/java/com/example/voidscape/dungeon/DungeonManager.java), [WorldBossManager](../evergarden/src/main/java/com/example/voidscape/boss/WorldBossManager.java) |
| อุปกรณ์เพชร / ธนู และสมุดเอนชานต์ | Vault ทุก Tier; เลือกเอนชานต์หนึ่งชนิดตาม config เดิม แล้วปรับช่วงระดับตาม Tier | [VaultEnchantRewards](../evergarden/src/main/java/com/example/voidscape/item/VaultEnchantRewards.java), [VaultProgression](../evergarden/src/main/java/com/example/voidscape/item/VaultProgression.java) |
| Rift / Smelter Pickaxe | หมวดอุปกรณ์พิเศษใน Vault | [RelicService](../evergarden/src/main/java/com/example/voidscape/item/RelicService.java) |
| Limit Break **14 ชนิด**, Unique Enchant **21 ชนิด**, Scroll of Eternity | Vault; Limit Break ใหม่ใช้เกรดตาม Tier ส่วน Limit Break ใหม่ในหีบลอยฟ้าเป็นเกรด III; Unique มีในหีบลอยฟ้าด้วย ทุกชนิดอยู่ในกลุ่มสุ่ม | [RelicService](../evergarden/src/main/java/com/example/voidscape/item/RelicService.java), [WhaleTreasure](../evergarden/src/main/java/com/example/voidscape/world/WhaleTreasure.java) |
| อาวุธ Rare **20 แบบ** | Vault Tier II–V; บอส Sanctuary ฝั่ง Nether | [AdventurerWeaponService](../evergarden/src/main/java/com/example/voidscape/item/AdventurerWeaponService.java), [RealmRewards](../evergarden/src/main/java/com/example/voidscape/boss/RealmRewards.java) |
| อาวุธ Legendary **5 แบบ** | Vault Tier III–V หรือแลก Core of Legend ที่ Seraph | [RelicService](../evergarden/src/main/java/com/example/voidscape/item/RelicService.java), [SeraphShop](../evergarden/src/main/java/com/example/voidscape/trade/SeraphShop.java) |
| คทาไม้ / ไม้เสริม | คราฟต์คทาไม้ด้วย Amethyst Shard 1 + Blaze Rod 2; อัปเกรดด้วย `/staff upgrade` | [ElementalAcquisitionService](../advance-magic/src/main/java/com/example/advancemagic/elemental/ElementalAcquisitionService.java), [StaffSpellService](../advance-magic/src/main/java/com/example/advancemagic/elemental/StaffSpellService.java) |
| คทาธาตุ **8 ธาตุ**, Apprentice → Arcane → Mythic | Apprentice จาก Vault หีบโครงสร้างที่รองรับ หรือร้าน Seraph; ใช้วัตถุดิบอัปเกรดต่อด้วย `/staff upgrade` | [ElementalStaffTreasure](../evergarden/src/main/java/com/example/voidscape/world/ElementalStaffTreasure.java), [SeraphShop](../evergarden/src/main/java/com/example/voidscape/trade/SeraphShop.java), [StaffSpellService](../advance-magic/src/main/java/com/example/advancemagic/elemental/StaffSpellService.java) |
| คัมภีร์เวท **58 เวท** (Basic 11 / Rare 22 / Epic 15 / Legendary 10) | หีบ exploration / landmark / boss / sanctuary มีโอกาสครบทุก rarity และทุกเวท; Seraph หมุนเวทขาย; ผู้พิพากษาให้เวท Legendary | [ElementalAcquisitionService](../advance-magic/src/main/java/com/example/advancemagic/elemental/ElementalAcquisitionService.java), [ElementalScrollTreasure](../evergarden/src/main/java/com/example/voidscape/world/ElementalScrollTreasure.java), [SeraphShop](../evergarden/src/main/java/com/example/voidscape/trade/SeraphShop.java) |
| Staff Gems **MAX / DAMAGE / COOLDOWN** | หีบลอยฟ้าและโครงสร้างบอสที่รองรับ | [WhaleTreasure](../evergarden/src/main/java/com/example/voidscape/world/WhaleTreasure.java) |
| Core of Restoration / Wand of Restoration | หีบโครงสร้างที่รองรับ Restoration; คทาซ่อมมีโอกาส 10% ตามค่าเริ่มต้น | [WhaleTreasure](../evergarden/src/main/java/com/example/voidscape/world/WhaleTreasure.java) |
| ยามานา **4 คุณภาพ** | ต้ม Awkward Potion ด้วย Amethyst Shard ได้ครบทุกคุณภาพ; บางคุณภาพมีในหีบและร้าน Seraph | [ManaPotionService](../advance-magic/src/main/java/com/example/advancemagic/mana/ManaPotionService.java), [SanctuaryLoot](../evergarden/src/main/java/com/example/voidscape/world/SanctuaryLoot.java), [SeraphShop](../evergarden/src/main/java/com/example/voidscape/trade/SeraphShop.java) |
| Soul Orb **6 ชนิด**, Realm Core **4 ชนิด**, Seraph / Revenant Core | ชนะบอสที่เป็นเจ้าของรางวัลจริง; ของรางวัลกายภาพบางชนิดมีหนึ่งชิ้นให้แบ่งกันทั้งกลุ่ม | [BossRewards](../evergarden/src/main/java/com/example/voidscape/boss/BossRewards.java), [RealmSanctuaryEncounters](../evergarden/src/main/java/com/example/voidscape/boss/RealmSanctuaryEncounters.java), [SeraphonBoss](../evergarden/src/main/java/com/example/voidscape/boss/SeraphonBoss.java), [ArkhaiaBoss](../evergarden/src/main/java/com/example/voidscape/boss/ArkhaiaBoss.java) |
| Core of Legend / ขนนก Seraph / ขนนกทอง | ขนนกจากกิจกรรม บอส หีบ และพร; แลกทองได้สองทาง; Core of Legend แลกที่ Seraph (เริ่มต้นขนนกทอง 15 + Orb ชนิดเดียวกัน 3) | [SeraphFeathers](../evergarden/src/main/java/com/example/voidscape/trade/SeraphFeathers.java), [SeraphShop](../evergarden/src/main/java/com/example/voidscape/trade/SeraphShop.java) |
| เกราะ Seraph / Revenant **ชุดละ 4 ชิ้น** | แลก Core บอสของชุดนั้น 1 อันต่อเกราะที่เลือก 1 ชิ้น ที่ Seraph | [SeraphShop](../evergarden/src/main/java/com/example/voidscape/trade/SeraphShop.java) |
| สัตว์เลี้ยงที่เปิดใช้ **32 ชนิด** และไข่ | เกิดป่าทุกชนิด มีโอกาสครบทุกวัย; จับคู่พ่อแม่ชนิดเดียวกันที่โตเต็มวัยเพื่อรับไข่ | [PetCatalog](../evergarden/src/main/java/com/example/voidscape/pet/PetCatalog.java), [WildPetSpawns](../evergarden/src/main/java/com/example/voidscape/pet/WildPetSpawns.java), [PetBreeding](../evergarden/src/main/java/com/example/voidscape/pet/PetBreeding.java) |
| แผนที่ / คู่มือ 2 เล่ม | แผนที่ซื้อจาก Seraph; คู่มือได้ครั้งแรกที่เข้า Evergarden และขอใหม่จากเมนูคู่มือ | [SanctuaryMaps](../evergarden/src/main/java/com/example/voidscape/trade/SanctuaryMaps.java), [TravelListener](../evergarden/src/main/java/com/example/voidscape/listener/TravelListener.java), [GuideMenuGui](../evergarden/src/main/java/com/example/voidscape/guide/GuideMenuGui.java) |

ยังไม่พบกลุ่มไอเท็มผู้เล่นที่เปิดใช้งานแล้วแต่ไม่มีช่องทางหาอื่นเพิ่มเติมจากคทาบินสองคน การมีช่องทางสุ่มไม่ได้หมายความว่าร้านจะขายครบพร้อมกันหรือเปิดหีบจำนวนหนึ่งแล้วต้องได้แน่นอน

ค่า config ของเซิร์ฟเวอร์จริงมีผล: การปิดระบบ ตั้งโอกาส/น้ำหนักเป็น 0 หรือไม่มี Advance Magic อาจทำให้ช่องทางบางอย่างหายไป หีบที่เคยแจกหรือถูกปล้นแล้วไม่สุ่มรางวัลใหม่เพียงเพราะอัปเดตปลั๊กอิน; Vault ที่ผู้เล่นเคลมแล้วไม่ถูกรีเซ็ต คทาบินธรรมดาในหีบลอยฟ้ายังคงเป็นรุ่นหนึ่งคน

## ของที่ยังหาใหม่ไม่ได้โดยตั้งใจ

- สัตว์เลี้ยงเทพ **5 ตัวที่ยังไม่ปล่อยโมเดล**: Aetherius, Ignis Draconis, Zephyros, Solaris, Fenrir อยู่ใน enum เพื่อรักษาข้อมูลเดิม แต่ไม่อยู่ใน 32 ชนิดที่เปิดใช้ ([PetCatalog](../evergarden/src/main/java/com/example/voidscape/pet/PetCatalog.java))
- Relic ที่เลิกใช้: `key_shard`, `repair_stone`, `void_elixir`, `rift_blade`, `storm_bow`, `nova_bow`, `eternal_aegis` มีระบบล้างของเก่า ไม่ควรนำกลับเข้ารางวัล ([RemovedRelics](../evergarden/src/main/java/com/example/voidscape/item/RemovedRelics.java))
- คทาเวทเดี่ยวรุ่นเก่า **21 แบบ** และแกนสำหรับคราฟต์เดิม **21 ชนิด** เป็น legacy; สูตรคราฟต์ถูกถอด คทาเดิมถูกย้ายเข้าสู่ระบบใหม่ รางวัลคอร์ที่ค้างอยู่ยังรับได้ และแกนเก่าใช้เป็นเชื้อเพลิงฟื้นฟูได้ แต่ระบบใหม่ไม่แจกแกนคราฟต์เก่าเพิ่ม ([WandService](../advance-magic/src/main/java/com/example/advancemagic/item/WandService.java), [ElementalStaffService](../advance-magic/src/main/java/com/example/advancemagic/item/ElementalStaffService.java), [WorldBossManager](../evergarden/src/main/java/com/example/voidscape/boss/WorldBossManager.java), [RestorationService](../advance-magic/src/main/java/com/example/advancemagic/item/RestorationService.java))
- Dev God equipment, โมเดลส่วนตัวบอส, ไอคอนเมนู และอุปกรณ์ทดสอบ เป็นของแอดมินหรือชิ้นส่วนแสดงผล ไม่ใช่รางวัล Survival ([AdminTestGui](../evergarden/src/main/java/com/example/voidscape/gui/AdminTestGui.java))

## ทดสอบ Vault สำหรับแอดมิน

เปิด `/evergarden test` → ปุ่ม **ทดสอบ Vault · Tier I–V** (slot 27 แบบเริ่มนับจาก 0) → เลือก Dark / Astral / Time → เลือก Tier I–V คลิกสุ่ม 1 ครั้ง หรือ Shift-click สุ่ม 10 ครั้ง ใช้โรงงานไอเท็ม เรต คุณภาพเอนชานต์ และเกรด Limit Break เดียวกับ Vault จริง รวมถึงเรตคทาบินสองคนด้านบน

เมนูนี้แสดงเวอร์ชันปลั๊กอินที่โหลดอยู่ จำนวนการเปิดที่มอบของสำเร็จแยกตาม Tier และยอดของแต่ละหมวดที่ได้รับจริง ตรวจสิทธิ์แอดมิน ใช้ทดสอบโดยไม่กินกุญแจและไม่แก้ประวัติเคลมไซต์

Shift-click เปิดสิบครั้ง แต่ช่องตัวอย่างแสดงเฉพาะการเปิดครั้งสุดท้าย ให้ดูยอดของแต่ละหมวดสำหรับผลทั้งชุด ถ้ากระเป๋ารับของทั้งชุดไม่พอ จะยกเลิกการมอบของทั้งชุดและไม่เพิ่มตัวนับสำเร็จ จำนวนคลิกจึงอาจไม่เท่ากับจำนวนที่ได้รับจริง ควรเคลียร์พื้นที่กระเป๋าระหว่างทดสอบ ช่องแสดงผลรอบล่าสุดหยิบของซ้ำไม่ได้ ([AdminTestGui](../evergarden/src/main/java/com/example/voidscape/gui/AdminTestGui.java))
