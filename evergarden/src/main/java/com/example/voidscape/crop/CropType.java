package com.example.voidscape.crop;

import org.bukkit.Material;
import java.util.*;

public enum CropType {
    // ==========================================
    // Tier 1: Common Farm (6 crops, 180s)
    // ==========================================
    MANA_DEW_BERRY("mana_dew_berry", CropTier.TIER_1, "บลูเบอร์รี", "Blueberry",
        Material.BEETROOT_SEEDS, Material.SWEET_BERRIES,
        "ฟื้นฟูทันที +50 Mana",
        List.of(
            "§7ผลเบอร์รี่เปล่งประกายละอองน้ำค้างเวทมนตร์",
            "§b✦ ฟื้นฟูทันที +50 Mana §7ให้กับผู้ดื่ม/กิน",
            "§a✦ เหมาะสำหรับนักเวทที่ต้องการมานาฉุกเฉิน",
            "§8ระยะเวลาเติบโต: 3 นาที · ปลูกบน Farmland"
        )),

    CHAMELEON_LEAF("chameleon_leaf", CropTier.TIER_1, "ผักกาดหอม", "Lettuce",
        Material.WHEAT_SEEDS, Material.DRIED_KELP,
        "พรางกาย มอนสเตอร์ไม่โจมตี 25 วินาที",
        List.of(
            "§7ใบไม้เปลี่ยนสีตามสภาพแวดล้อมเพื่อพรางตา",
            "§a✦ ล้างสถานะ Aggro มอนสเตอร์รอบตัว 24 บล็อกทันที",
            "§e✦ มอนสเตอร์จะไม่โจมตีก่อนเป็นเวลา 25 วินาที",
            "§8ระยะเวลาเติบโต: 3 นาที · ปลูกบน Farmland"
        )),

    FAIRY_MUSHROOM("fairy_mushroom", CropTier.TIER_1, "เห็ดทรัฟเฟิล", "Truffle",
        Material.BEETROOT_SEEDS, Material.COOKIE,
        "กระโดดสองจังหวะ (Double Jump) 3 นาที",
        List.of(
            "§7เห็ดสปอร์เรืองแสงที่ภูตจิ๋วชื่นชอบ",
            "§d✦ ปลดล็อก 'Double Jump' กระโดดสองจังหวะกลางอากาศ",
            "§f✦ กดกระโดดซ้ำกลางอากาศเพื่อพุ่งทะยาน (นาน 3 นาที)",
            "§8ระยะเวลาเติบโต: 3 นาที · ปลูกบน Farmland"
        )),

    MAGNETIC_SQUASH("magnetic_squash", CropTier.TIER_1, "ฟักทองบัตเตอร์นัต", "Butternut Squash",
        Material.PUMPKIN_SEEDS, Material.PUMPKIN_PIE,
        "แม่เหล็กดูดไอเทมและ EXP 12 บล็อก 2 นาที",
        List.of(
            "§7ฟักทองที่แผ่สนามแม่เหล็กดึงดูดสสารรอบข้าง",
            "§9✦ ดูดไอเทมและหลอด EXP ทั้งหมดในระยะ 12 บล็อก",
            "§e✦ เข้ากระเป๋าอัตโนมัติต่อเนื่อง 2 นาที",
            "§8ระยะเวลาเติบโต: 3 นาที · ปลูกบน Farmland"
        )),

    MOUNTAIN_WALKER_BAMBOO("mountain_walker_bamboo", CropTier.TIER_1, "ต้นหอมญี่ปุ่น", "Leek",
        Material.WHEAT_SEEDS, Material.CARROT,
        "ก้าวขึ้นเนิน 1 บล็อกอัตโนมัติ (Step Assist) 5 นาที",
        List.of(
            "§7ต้นหอมญี่ปุ่นสดกรอบ มอบสัมผัสแห่งการเหยียบย่างมั่นคง",
            "§a✦ เดินก้าวขึ้นบล็อกสูง 1 บล็อกได้โดยไม่ต้องกระโดด",
            "§f✦ เคลื่อนที่บนภูมิประเทศขรุขระอย่างลื่นไหล นาน 5 นาที",
            "§8ระยะเวลาเติบโต: 3 นาที · ปลูกบน Farmland"
        )),

    LUMBERJACK_ACORN("lumberjack_acorn", CropTier.TIER_1, "เกาลัด", "Chestnut",
        Material.WHEAT_SEEDS, Material.COOKIE,
        "โค่นต้นไม้ทั้งต้นในพริบตา (Tree Feller) 5 ชาร์จ",
        List.of(
            "§7ผลลูกโอ๊กยักษ์จากป่าดึกดำบรรพ์ พลังแห่งคนตัดไม้",
            "§6✦ ขุดท่อนไม้ 1 บล็อก จะทำลายท่อนไม้ทั้งต้นทันที!",
            "§e✦ สะสมการใช้งานได้ 5 ต้นไม้ (5 ชาร์จ)",
            "§8ระยะเวลาเติบโต: 3 นาที · ปลูกบน Farmland"
        )),

    // ==========================================
    // Tier 2: Combat & Slaying (6 crops, 300s)
    // ==========================================
    BLOOD_THORN_TOMATO("blood_thorn_tomato", CropTier.TIER_2, "มะเขือเทศ", "Tomato",
        Material.PUMPKIN_SEEDS, Material.APPLE,
        "ดูดเลือด 20% จากการโจมตี 60 วินาที",
        List.of(
            "§7มะเขือเทศสีแดงสดที่มีหนามแหลมคมดูดซับพลังชีวิต",
            "§c✦ ดูดเลือด 20% จากดาเมจการโจมตีระยะประชิดและธนู",
            "§4✦ ฟื้นฟูเลือดผู้เล่นต่อเนื่องตามดาเมจ นาน 60 วินาที",
            "§8ระยะเวลาเติบโต: 5 นาที · ปลูกบน Farmland"
        )),

    FROSTBITE_RADISH("frostbite_radish", CropTier.TIER_2, "หัวไชเท้า", "Radish",
        Material.BEETROOT_SEEDS, Material.CARROT,
        "แช่แข็งศัตรูที่ถูกโจมตีเป็นน้ำแข็ง 60 วินาที",
        List.of(
            "§7หัวไชเท้าที่เติบโตใต้ชั้นเพอร์มาฟรอสต์อันหนาวเหน็บ",
            "§b✦ ทุกการโจมตีทำให้เป้าหมายติดเยือกแข็ง (Freeze) 1.5s",
            "§3✦ ศัตรูติด Slowness และขยับไม่ได้ นาน 60 วินาที",
            "§8ระยะเวลาเติบโต: 5 นาที · ปลูกบน Farmland"
        )),

    THUNDER_KERNEL_CORN("thunder_kernel_corn", CropTier.TIER_2, "ข้าวโพด", "Corn",
        Material.WHEAT_SEEDS, Material.BREAD,
        "Speed II & ชิ่งสายฟ้าใส่ศัตรู 3 ตัว (60 วินาที)",
        List.of(
            "§7เมล็ดข้าวโพดที่สะสมประจุไฟฟ้าสถิตจากพายุฟ้าผ่า",
            "§e✦ ได้รับบัฟ Speed II เคลื่อนที่เร็วขึ้น นาน 60 วินาที",
            "§e✦ ทุกการโจมตีจะชิ่งสายฟ้าใส่ศัตรูใกล้เคียง 3 ตัว (6 ดาเมจ)",
            "§6✦ กวาดล้างฝูงมอนสเตอร์ได้อย่างรวดเร็ว",
            "§8ระยะเวลาเติบโต: 5 นาที · ปลูกบน Farmland"
        )),

    REAPERS_GARLIC("reapers_garlic", CropTier.TIER_2, "กระเทียม", "Garlic",
        Material.BEETROOT_SEEDS, Material.GOLDEN_CARROT,
        "ปลิดชีพมอนสเตอร์เลือดต่ำกว่า 20% ทันที 60 วินาที",
        List.of(
            "§7กลิ่นอายแห่งความตายสถิตอยู่ในกลีบกระเทียมสีดำขลับ",
            "§4✦ โจมตีศัตรูทั่วไปที่มี HP ต่ำกว่า 20% จะปลิดชีพทันที!",
            "§c✦ คมเคียวแห่งยมทูตพิพากษา นาน 60 วินาที (ไม่ส่งผลต่อบอส)",
            "§8ระยะเวลาเติบโต: 5 นาที · ปลูกบน Farmland"
        )),

    TITAN_PUMPKIN("titan_pumpkin", CropTier.TIER_2, "มะเขือม่วง", "Eggplant",
        Material.PUMPKIN_SEEDS, Material.PUMPKIN_PIE,
        "ทำลายเกราะศัตรู 25% เป็นเวลา 60 วินาที",
        List.of(
            "§7มะเขือม่วงเนื้อแน่น ช่วยเสริมพลังทำลายเกราะ",
            "§6✦ การโจมตีจะทำลายเกราะศัตรู 25% ทำให้อ่อนแอลงอย่างมาก",
            "§e✦ เพิ่มดาเมจที่ศัตรูได้รับ นาน 60 วินาที",
            "§8ระยะเวลาเติบโต: 5 นาที · ปลูกบน Farmland"
        )),

    KINETIC_PEA_POD("kinetic_pea_pod", CropTier.TIER_2, "ถั่วลันเตา", "Green Peas",
        Material.WHEAT_SEEDS, Material.SWEET_BERRIES,
        "ไร้ดาเมจตกจากที่สูง & ระเบิดแรงกระแทกรอบตัว 3 นาที",
        List.of(
            "§7ฝักถั่วที่ดูดซับแรงจลน์และแรงโน้มถ่วงได้สมบูรณ์",
            "§a✦ ยกเลิกดาเมจตกจากที่สูง (Fall Damage) 100%",
            "§2✦ แปลงแรงตกเป็นคลื่นกระแทกสะท้อนดาเมจใส่ศัตรูรอบตัว 5 บล็อก",
            "§8ระยะเวลาเติบโต: 5 นาที · ปลูกบน Farmland"
        )),

    // ==========================================
    // Tier 3: Dimension & Survival (6 crops, 450s)
    // ==========================================
    TWILIGHT_GRAPE("twilight_grape", CropTier.TIER_3, "องุ่น", "Grape",
        Material.MELON_SEEDS, Material.SWEET_BERRIES,
        "เพิ่มความเร็ว & ระยะยิงคทาเวทมนตร์ +100% 60 วินาที",
        List.of(
            "§7พวงองุ่นสีม่วงยามพลบค่ำ เสริมทัศนวิสัยและการโฟกัสจิต",
            "§d✦ เพิ่มความเร็วลูกเวทมนตร์และระยะยิงของคทาเวท +100%",
            "§5✦ ยิงเวทได้ไกลขึ้นสองเท่าและแม่นยำสูง นาน 60 วินาที",
            "§8ระยะเวลาเติบโต: 7.5 นาที · ปลูกบน Farmland"
        )),

    VOID_FEATHER_BLOSSOM("void_feather_blossom", CropTier.TIER_3, "กะหล่ำปลีม่วง", "Red Cabbage",
        Material.TORCHFLOWER_SEEDS, Material.DRIED_KELP,
        "ดีดตัวหนีความตายเมื่อตก Void 5 นาที",
        List.of(
            "§7ดอกไม้รูปปีกขนนกที่หยั่งรากในห้วงความว่างเปล่า",
            "§d✦ หากผู้เล่นพลัดตก Void (Y < -50) จะดีดตัวลอยขึ้น 40 บล็อก",
            "§5✦ พร้อมวาร์ปกลับขึ้นสู่พื้นดินที่ปลอดภัยอัตโนมัติ (นาน 5 นาที)",
            "§8ระยะเวลาเติบโต: 7.5 นาที · ปลูกบน Farmland"
        )),

    LODESTONE_GOURD("lodestone_gourd", CropTier.TIER_3, "น้ำเต้า", "Bottle Gourd",
        Material.MELON_SEEDS, Material.APPLE,
        "ร่าย 3 วินาทีเพื่อวาร์ปกลับจุดเกิด/เตียงนอน",
        List.of(
            "§7น้ำเต้าที่บรรจุพลังงานการระบุพิกัดแห่งมิติ",
            "§6✦ กินแล้วร่าย 3 วินาที (ห้ามรับดาเมจหรือขยับตัว)",
            "§e✦ จะวาร์ปนำทางกลับสู่จุดเกิดหรือเตียงนอนทันที",
            "§8ระยะเวลาเติบโต: 7.5 นาที · ปลูกบน Farmland"
        )),

    ABYSSAL_KELP("abyssal_kelp", CropTier.TIER_3, "ขึ้นฉ่าย", "Celery",
        Material.WHEAT_SEEDS, Material.DRIED_KELP,
        "เกราะฟองสบู่น้ำลึก (Absorption II) & ระเบิดคลื่นน้ำสะท้อนกลับ 2 นาที",
        List.of(
            "§7ขึ้นฉ่ายที่ดูดซับฟองอากาศและแรงดันจากก้นสมุทรลึก",
            "§b✦ มอบเกราะฟองสบู่ดูดซับความเสียหาย (Absorption II) นาน 2 นาที",
            "§3✦ เมื่อถูกโจมตี จะระเบิดคลื่นน้ำผลักศัตรูรอบตัว 5 บล็อก & ดับไฟทันที",
            "§8ระยะเวลาเติบโต: 7.5 นาที · ปลูกบน Farmland"
        )),

    GLIDER_SPORE("glider_spore", CropTier.TIER_3, "บรอกโคลี", "Broccoli",
        Material.BEETROOT_SEEDS, Material.COOKIE,
        "ย่อตัวกลางอากาศเพื่อกางร่มชูชีพร่อนลง 3 นาที",
        List.of(
            "§7สปอร์เส้นใยละเอียดที่พยุงวัตถุให้ลอยตามลม",
            "§f✦ กดย่อตัว (Sneak) ขณะลอยอยู่กลางอากาศเพื่อร่อนลงช้าๆ",
            "§7✦ ป้องกันดาเมจตกจากที่สูง และพุ่งไปข้างหน้าอย่างปลอดภัย (3 นาที)",
            "§8ระยะเวลาเติบโต: 7.5 นาที · ปลูกบน Farmland"
        )),

    STAR_ANISE("star_anise", CropTier.TIER_3, "โป๊ยกั๊ก", "Star Anise",
        Material.PITCHER_POD, Material.GOLDEN_CARROT,
        "ล้างดีบัฟทั้งหมด & ภูมิคุ้มกันสถานะผิดปกติ 2 นาที",
        List.of(
            "§7สมุนไพรรูปดาวหกแฉก กลิ่นหอมบริสุทธิ์ปัดเป่ามนต์ดำ",
            "§e✦ ลบล้างสถานะด้านลบทั้งหมด (พิษ, Wither, สลบ, ตาบอด ฯลฯ)",
            "§f✦ คุ้มกันไม่ให้ติดดีบัฟใหม่ใดๆ ทั้งสิ้น นาน 2 นาที",
            "§8ระยะเวลาเติบโต: 7.5 นาที · ปลูกบน Farmland"
        )),

    // ==========================================
    // Tier 4: Mining & Utility (6 crops, 600s)
    // ==========================================
    FORTUNE_BEET("fortune_beet", CropTier.TIER_4, "เทอร์นิป", "Turnip",
        Material.BEETROOT_SEEDS, Material.CARROT,
        "+35% โอกาสขุดแร่แล้วดรอปเบิ้ล 2 เท่า 3 นาที",
        List.of(
            "§7หัวบีทรูทสีทับทิมที่เติบโตข้างสายแร่อัญมณี",
            "§d✦ +35% โอกาสดรอปแร่สองเท่า (เพชร, เนเธอร์ไรต์, ทอง, เหล็ก)",
            "§5✦ ทำงานร่วมกับเอนแชนต์ Fortune ได้อย่างสมบูรณ์ นาน 3 นาที",
            "§8ระยะเวลาเติบโต: 10 นาที · ปลูกบน Farmland"
        )),

    DEMETERS_MELON("demeters_melon", CropTier.TIER_4, "มะละกอ", "Papaya",
        Material.MELON_SEEDS, Material.MELON_SLICE,
        "ออร่าเร่งโตพืชผักรอบตัว 6 ชาร์จ",
        List.of(
            "§7มะละกอสุกหอมที่สะสมพลังงานแห่งความอุดมสมบูรณ์",
            "§a✦ เดินเฉียดพืชผักชนิดใดก็ได้เพื่อเร่งการเติบโตทันที 1/3 รอบ",
            "§e✦ สะสมพลังเร่งโตได้ 6 ครั้ง (6 ชาร์จ)",
            "§8ระยะเวลาเติบโต: 10 นาที · ปลูกบน Farmland"
        )),

    PRISM_SHARD_CARROT("prism_shard_carrot", CropTier.TIER_4, "มันสำปะหลัง", "Cassava",
        Material.PITCHER_POD, Material.GOLDEN_CARROT,
        "+25% โอกาสพบของแรร์ใน Evergarden Vault 5 นาที",
        List.of(
            "§7หัวมันสำปะหลังเนื้อแน่นจากแปลงเพาะปลูกพิเศษ",
            "§b✦ +25% เพิ่มโอกาสสุ่มพบ Evergarden Key Shards และ Scrolls",
            "§3✦ เมื่อเปิด Evergarden Vault ในวิหารโบราณ นาน 5 นาที",
            "§8ระยะเวลาเติบโต: 10 นาที · ปลูกบน Farmland"
        )),

    GOLDLEAF_HERB("goldleaf_herb", CropTier.TIER_4, "ผักโขม", "Spinach",
        Material.TORCHFLOWER_SEEDS, Material.GOLDEN_APPLE,
        "Resistance I & แปลง 50% ดาเมจซ่อมเกราะ 2 นาที",
        List.of(
            "§7ใบไม้ทองคำบริสุทธิ์ที่มีละอองยางไม้ซ่อมแซมสิ่งของ",
            "§6✦ ได้รับ Resistance I ลดดาเมจที่ได้รับลง 20%",
            "§e✦ แปลง 50% ของดาเมจที่ได้รับ เป็นการฟื้นฟูความทนทานชุดเกราะ นาน 2 นาที",
            "§8ระยะเวลาเติบโต: 10 นาที · ปลูกบน Farmland"
        )),

    SOUL_WARD_BULB("soul_ward_bulb", CropTier.TIER_4, "หอมหัวใหญ่", "Onion",
        Material.BEETROOT_SEEDS, Material.GOLDEN_CARROT,
        "ป้องกันการตาย 1 ครั้ง & ผลักศัตรูออกรอบทิศ",
        List.of(
            "§7พืชแห่งวิญญาณที่จะกางม่านพลังพิทักษ์ชีพยามคับขัน",
            "§b✦ หากได้รับดาเมจถึงแก่ชีวิต จะรอดชีวิตทันที (เหลือ 4 HP)",
            "§3✦ ได้รับ Regeneration III และคลื่นกระแทกผลักศัตรู 8 บล็อก",
            "§8ระยะเวลาเติบโต: 10 นาที · ปลูกบน Farmland"
        )),

    CHRONO_PEPPER("chrono_pepper", CropTier.TIER_4, "พริกหวาน", "Bell Pepper",
        Material.PUMPKIN_SEEDS, Material.APPLE,
        "-40% คูลดาวน์คทาเวทมนตร์ทั้งหมด 60 วินาที",
        List.of(
            "§7พริกไทยสีเพลิงที่เร่งการไหลเวียนของห้วงเวลา",
            "§c✦ ลดคูลดาวน์สกิลคทาเวทมนตร์ (Advance Magic) ลง 40%",
            "§e✦ ร่ายเวทต่อเนื่องได้รวดเร็วดั่งสายฟ้าฟาด นาน 60 วินาที",
            "§8ระยะเวลาเติบโต: 10 นาที · ปลูกบน Farmland"
        )),

    // ==========================================
    // Tier 5: Mythic Arcana (6 crops, 900s)
    // ==========================================
    ANCIENT_ASTRAL_ROOT("ancient_astral_root", CropTier.TIER_5, "มันหวาน", "Sweet Potato",
        Material.TORCHFLOWER_SEEDS, Material.GOLDEN_APPLE,
        "เพิ่ม Max Mana ถาวร! (แทน Dragon's Breath เดิม, 1 ครั้ง/วัน)",
        List.of(
            "§4§k||§r §c[ระดับตำนานสูงสุด · MYTHIC] §4§k||§r",
            "§b✦ กินเพื่อเพิ่มขีดจำกัด Max Mana ถาวร! (สูงสุด 300 Mana)",
            "§7  • Max Mana < 150: §a+5.0 Max Mana",
            "§7  • Max Mana 150-200: §e+2.0 Max Mana",
            "§7  • Max Mana 200-300: §6+0.5 Max Mana",
            "§d✦ ทดแทนระบบ Dragon's Breath เดิม (กินได้วันละ 1 ครั้งในเกม)",
            "§8ระยะเวลาเติบโต: 15 นาที · ปลูกบน Farmland"
        )),

    YGGDRASIL_SPROUT("yggdrasil_sprout", CropTier.TIER_5, "หน่อไม้ฝรั่ง", "Asparagus",
        Material.TORCHFLOWER_SEEDS, Material.GOLDEN_APPLE,
        "เพิ่ม Mana Regen ถาวร +0.2/s (สูงสุด 15.0/s, 1 ครั้ง/วัน)",
        List.of(
            "§4§k||§r §c[ระดับตำนานสูงสุด · MYTHIC] §4§k||§r",
            "§a✦ หน่อพันธุ์จากต้นไม้อิกดราซิลผู้ค้ำจุนจักรวาล",
            "§e✦ กินเพื่อเพิ่มอัตราการฟื้นฟู Mana ถาวร +0.2 Mana/วินาที",
            "§6✦ อัปเกรดได้สูงสุดถึง 15.0 Mana/s (กินได้วันละ 1 ครั้งในเกม)",
            "§8ระยะเวลาเติบโต: 15 นาที · ปลูกบน Farmland"
        )),

    VOID_OVERCHARGE_FIG("void_overcharge_fig", CropTier.TIER_5, "มะเดื่อ", "Fig",
        Material.PITCHER_POD, Material.GOLDEN_CARROT,
        "มอบ +100 Overcharge Mana ทะลุหลอด 45 วินาที",
        List.of(
            "§4§k||§r §c[ระดับตำนานสูงสุด · MYTHIC] §4§k||§r",
            "§5✦ มะเดื่อสีอเวจีอัดแน่นด้วยพลังงานเวทมนตร์ทะลักล้น",
            "§d✦ ได้รับ +100 Mana เกินขีดจำกัดสูงสุด (Overcharge)",
            "§b✦ ปลดปล่อยมหาเวทใหญ่ติดต่อกันได้ทันที นาน 45 วินาที",
            "§8ระยะเวลาเติบโต: 15 นาที · ปลูกบน Farmland"
        )),

    ETHEREAL_MINT("ethereal_mint", CropTier.TIER_5, "สะระแหน่", "Mint",
        Material.WHEAT_SEEDS, Material.APPLE,
        "ร่ายเวทซ้ำเบิ้ล 2 เท่าฟรี! (Arcane Echo) 2 ชาร์จ",
        List.of(
            "§4§k||§r §c[ระดับตำนานสูงสุด · MYTHIC] §4§k||§r",
            "§3✦ ใบมินต์โปร่งแสงที่กักเก็บเสียงสะท้อนแห่งห้วงมิติ",
            "§b✦ เวทมนตร์ 2 ครั้งถัดไปจะร่ายซ้ำเบิ้ลทันทีอีก 1 ครั้ง ฟรี!",
            "§f✦ ไม่เสียมานาเพิ่มและไม่ติดคูลดาวน์ซ้ำ (2 ชาร์จ, คูลดาวน์ 3s)",
            "§8ระยะเวลาเติบโต: 15 นาที · ปลูกบน Farmland"
        )),

    BLOODBURN_CHILI("bloodburn_chili", CropTier.TIER_5, "พริกชี้ฟ้า", "Chili Pepper",
        Material.PUMPKIN_SEEDS, Material.APPLE,
        "ร่ายเวทด้วยเลือดแทนเมื่อมานาหมด (Blood Cast) 30 วินาที",
        List.of(
            "§4§k||§r §c[ระดับตำนานสูงสุด · MYTHIC] §4§k||§r",
            "§4✦ พริกเพลิงโลหิตที่จุดประกายชีพให้ลุกไหม้เป็นมานา",
            "§c✦ หากมานาหมดหรือมานาไม่พอ จะใช้พลังชีวิต (HP) ร่ายเวทแทน!",
            "§6✦ ไม่มีวันขาดแคลนพลังโจมตียามคับขัน นาน 30 วินาที",
            "§8ระยะเวลาเติบโต: 15 นาที · ปลูกบน Farmland"
        )),

    OMNI_POMEGRANATE("omni_pomegranate", CropTier.TIER_5, "ทับทิม", "Pomegranate",
        Material.TORCHFLOWER_SEEDS, Material.GOLDEN_APPLE,
        "ร่ายเวทระเบิดมหาธาตุ 18 ดาเมจ & เผาไหม้ 60 วินาที",
        List.of(
            "§4§k||§r §c[ระดับตำนานสูงสุด · MYTHIC] §4§k||§r",
            "§e✦ ทับทิมแห่งมหาธาตุทั้ง 4 (ไฟ, น้ำแข็ง, สายฟ้า, ธรรมชาติ)",
            "§d✦ ทุกครั้งที่ร่ายเวท จะระเบิดมหาธาตุสร้าง 18 ดาเมจรอบตัวผู้ร่าย",
            "§f✦ ผลักศัตรูออกไป พร้อมเผาไหม้และแช่แข็ง นาน 60 วินาที",
            "§8ระยะเวลาเติบโต: 15 นาที · ปลูกบน Farmland"
        ));

    public final String id;
    public final CropTier tier;
    public final String thaiName;
    public final String englishName;
    public final Material seedMaterial;
    public final Material foodMaterial;
    public final String summary;
    public final List<String> lore;

    CropType(String id, CropTier tier, String thaiName, String englishName,
             Material seedMaterial, Material foodMaterial, String summary, List<String> lore) {
        this.id = id;
        this.tier = tier;
        this.thaiName = thaiName;
        this.englishName = englishName;
        this.seedMaterial = seedMaterial;
        this.foodMaterial = foodMaterial;
        this.summary = summary;
        this.lore = lore;
    }

    public static CropType fromId(String id) {
        if (id == null) return null;
        String clean = id.toLowerCase(Locale.ROOT).trim().replace("seed_", "").replace("crop_", "").replace("-", "_");
        for (CropType type : values()) {
            if (type.id.equals(clean) || type.name().toLowerCase(Locale.ROOT).equals(clean)) {
                return type;
            }
        }
        return null;
    }
}
