package com.example.voidscape.enchant;

import org.bukkit.enchantments.Enchantment;

public enum LimitBreakType {
    SHARPNESS(Enchantment.SHARPNESS, "Sharpness +1", "คมสถิตมิติ (+1 Sharpness)", "อาวุธระยะประชิด (Sword / Axe)", ItemCategory.MELEE, 10),
    PROTECTION(Enchantment.PROTECTION, "Protection +1", "ปราการสถิตมิติ (+1 Protection)", "ชุดเกราะ (Armor)", ItemCategory.ARMOR, 10),
    POWER(Enchantment.POWER, "Power +1", "พลังสถิตมิติ (+1 Power)", "ธนู (Bow)", ItemCategory.BOW, 10),
    EFFICIENCY(Enchantment.EFFICIENCY, "Efficiency +1", "ประสิทธิภาพสถิตมิติ (+1 Efficiency)", "อุปกรณ์ขุดเจาะ (Tools)", ItemCategory.TOOL, 10),
    FORTUNE(Enchantment.FORTUNE, "Fortune +1", "โชคลาภสถิตมิติ (+1 Fortune)", "ที่ขุด (Pickaxe)", ItemCategory.PICKAXE, 10),
    LOOTING(Enchantment.LOOTING, "Looting +1", "ล่าสมบัติสถิตมิติ (+1 Looting)", "ดาบ (Sword)", ItemCategory.SWORD, 10),
    UNBREAKING(Enchantment.UNBREAKING, "Unbreaking +1", "คงกระพันสถิตมิติ (+1 Unbreaking)", "อุปกรณ์และเกราะทุกชนิด (All Gear)", ItemCategory.ANY, 10),
    FEATHER_FALLING(Enchantment.FEATHER_FALLING, "Feather Falling +1", "ขนนกสถิตมิติ (+1 Feather Falling)", "รองเท้า (Boots)", ItemCategory.BOOTS, 10),
    SWEEPING_EDGE(Enchantment.SWEEPING_EDGE, "Sweeping Edge +1", "เพลงดาบกวาดล้าง (+1 Sweeping Edge)", "ดาบ (Sword)", ItemCategory.SWORD, 8),
    SMITE(Enchantment.SMITE, "Smite +1", "พิฆาตอสูรสถิตมิติ (+1 Smite)", "อาวุธระยะประชิด (Sword / Axe)", ItemCategory.MELEE, 10),
    THORNS(Enchantment.THORNS, "Thorns +1", "หนามสถิตมิติ (+1 Thorns)", "ชุดเกราะ (Armor)", ItemCategory.ARMOR, 10),
    DEPTH_STRIDER(Enchantment.DEPTH_STRIDER, "Depth Strider +1", "ย่างก้าววารีสถิตมิติ (+1 Depth Strider)", "รองเท้า (Boots)", ItemCategory.BOOTS, 5),
    DENSITY(Enchantment.DENSITY, "Density +1", "ทุบหนักสถิตมิติ (+1 Density)", "ค้อนสงคราม (Mace)", ItemCategory.MACE, 10),
    BREACH(Enchantment.BREACH, "Breach +1", "ทุบเจาะเกราะสถิตมิติ (+1 Breach)", "ค้อนสงคราม (Mace)", ItemCategory.MACE, 8);

    private final Enchantment enchantment;
    private final String title;
    private final String thaiTitle;
    private final String targetDescription;
    private final ItemCategory category;
    private final int maxLevel;

    LimitBreakType(Enchantment enchantment, String title, String thaiTitle, String targetDescription, ItemCategory category, int maxLevel) {
        this.enchantment = enchantment;
        this.title = title;
        this.thaiTitle = thaiTitle;
        this.targetDescription = targetDescription;
        this.category = category;
        this.maxLevel = maxLevel;
    }

    public Enchantment enchantment() { return enchantment; }
    public String title() { return title; }
    public String thaiTitle() { return thaiTitle; }
    public String targetDescription() { return targetDescription; }
    public ItemCategory category() { return category; }
    public int maxLevel() { return maxLevel; }
}
