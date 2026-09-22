package com.example.voidscape.enchant;

import java.util.Locale;

public enum UniqueEnchant {
    // Bows & Ranged
    COLOSSUS_SLAYER("Colossus Slayer", "ล่าไททัน", "ยิงแรงขึ้นตาม % Max HP ของบอสและมอนสเตอร์", ItemCategory.BOW),
    RICOCHET("Ricochet", "กระสุนชิ่งสายฟ้า", "ลูกธนูชิ่งหามอนสเตอร์รอบข้าง 3 ตัวพร้อมปล่อยสายฟ้า", ItemCategory.BOW),
    KINETIC_GRAPPLE("Kinetic Grapple", "ฮุกช็อตเวหา", "ยิงปักบล็อกแล้วดึงตัวผู้เล่นพุ่งไปหาจุดปักทันที", ItemCategory.BOW),
    ABSOLUTE_ZERO("Absolute Zero", "เยือกแข็งสัมบูรณ์", "สตัฟฟ์แช่แข็งศัตรูในระยะหยุดนิ่ง 3.5 วินาที", ItemCategory.BOW),
    SINGULARITY("Singularity", "หลุมดำกลืนมิติ", "ลูกธนูปักแล้วสร้างหลุมดำดูดรวบมอนสเตอร์ 3 วินาที", ItemCategory.BOW),
    METEOR_ARROW("Meteor Arrow", "ศรดาวตกวินาศ", "เมื่อลูกธนูปักพื้น 1 วิ อุกกาบาตยักษ์จะตกลงมาเผาพื้นที่ 5x5", ItemCategory.BOW),

    // Mining & Tools
    SEISMIC_SLAM("Seismic Slam", "ขุดทลาย 3x3", "ขุด 1 ครั้งระเบิดเปิดโพรง 3×3×1 ทันที", ItemCategory.PICKAXE),
    VEIN_SMELTER("Vein Smelter", "หลอมสายแร่คู่", "ขุดทั้งสายแร่ + เผาเป็นแท่งโลหะ + โบนัสแร่ทันที", ItemCategory.PICKAXE),
    ADVANCE_TOOL("Advance Tool", "เครื่องมือสารพัดช่าง", "ขุดบล็อกทุกประเภท (ดิน, ทราย, กรวด, ไม้, หิน) ด้วยความเร็วสูงระดับ 5 เท่ากันทั้งหมด", ItemCategory.TOOL),
    DEMETER_SCYTHE("Demeter's Scythe", "เคียวเทพกสิกรรม", "เก็บเกี่ยวและปลูกคืนพืช 9×9 บล็อกอัตโนมัติ", ItemCategory.HOE),
    TITAN_BREACH("Titan Breach", "ขวานเจาะเกราะ (Breach)", "ฟันโจมตีทะลุทะลวงเกราะศัตรู (Breach) ลดเกราะและทำ True Damage", ItemCategory.AXE),
    TELEPATHY("Telepathy", "จิตสื่อสาร", "แร่และของที่ขุดได้ทุกชิ้นวาร์ปเข้าตัวผู้เล่น 100%", ItemCategory.TOOL),

    // Melee Combat
    GUILLOTINE("Guillotine", "กิโยตินปลิดชีพ", "ฟันสังหารมอนสเตอร์เลือดต่ำกว่า 15% ทันที (Execute)", ItemCategory.MELEE),
    ECHO_STRIKE("Echo Strike", "เงาดาบซ้ำสอง", "โอกาส 35% เงาฟันซ้ำดาเมจเดิม 100% ภายใน 0.2 วิ", ItemCategory.SWORD),
    BLADE_VORTEX("Blade Vortex", "คลื่นดาบสุญญากาศ", "ฟันโจมตีปล่อยคลื่นดาบตัดอากาศทะลวงศัตรูด้านหน้า 7 บล็อก", ItemCategory.SWORD),
    SOUL_HARVEST("Soul Harvest", "เกี่ยววิญญาณ", "สะสมวิญญาณรอบตัวเพิ่มเดินไว +10% และดูดเลือด 5%", ItemCategory.MELEE),
    THUNDERLORD("Thunderlord", "สายฟ้าทัณฑ์สวรรค์", "ฟันเป้าหมายเดิมครบ 3 ครั้ง ผ่าสายฟ้า True Damage", ItemCategory.MELEE),
    VAMPIRIC("Vampiric", "สูบโลหิต", "แปลง 15% ของดาเมจที่ทำได้กลับมาฟื้นฟูเลือดผู้เล่น", ItemCategory.MELEE),

    // Armor & Utility
    PHOENIX_REBIRTH("Phoenix Rebirth", "ฟีนิกซ์คืนชีพ", "เมื่อตาย คืนชีพ 50% HP + คลื่นไฟ (คูลดาวน์ 10 นาที)", ItemCategory.CHESTPLATE),
    SHADOW_STEP("Shadow Step", "ก้าวพริบตา", "กดย่อ 2 ครั้ง พริบตาวาร์ปไปข้างหน้า 6 บล็อก (คูลดาวน์ 4 วิ)", ItemCategory.BOOTS),
    TITAN_STANCE("Titan Stance", "ร่างศิลาไร้พ่าย", "ต้านทาน Knockback 100% และลดดาเมจแรงระเบิด 40%", ItemCategory.LEGGINGS),
    SOULBOUND("Soulbound", "วิญญาณสถิต", "ไอเทมชิ้นนี้จะไม่ตกและไม่สูญหายเมื่อผู้เล่นเสียชีวิต", ItemCategory.ANY);

    private final String title;
    private final String thaiTitle;
    private final String description;
    private final ItemCategory category;

    UniqueEnchant(String title, String thaiTitle, String description, ItemCategory category) {
        this.title = title;
        this.thaiTitle = thaiTitle;
        this.description = description;
        this.category = category;
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String title() { return title; }
    public String thaiTitle() { return thaiTitle; }
    public String description() { return description; }
    public ItemCategory category() { return category; }

    public static UniqueEnchant parse(String raw) {
        if (raw == null) return null;
        String clean = raw.trim().toUpperCase(Locale.ROOT);
        if (clean.equals("TIMBER_TITAN")) return TITAN_BREACH;
        if (clean.equals("BEDROCK_RESONANCE")) return ADVANCE_TOOL;
        try {
            return valueOf(clean);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
