package com.example.forge;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

import java.util.Locale;

/** Affix grade, separate from an item's own identity (a Legendary weapon stays Legendary). */
public enum Rarity {
    COMMON("ธรรมดา", 0xD8D8D8, Material.IRON_NUGGET, "เศษเหล็กหลอม"),
    UNCOMMON("ดี", 0x5EE87A, Material.PRISMARINE_CRYSTALS, "ผลึกใบหยก"),
    RARE("หายาก", 0x4FB4FF, Material.ECHO_SHARD, "ชิ้นคริสตัลคราม"),
    EPIC("มหากาพย์", 0xB47CFF, Material.AMETHYST_SHARD, "เกล็ดอาคมม่วง"),
    MYTHIC("ปรัมปรา", 0xFF9A3C, Material.RESIN_BRICK, "อำพันหลอมเทพ");

    public final String thai;
    public final TextColor color;
    public final Material material;
    public final String materialName;

    Rarity(String thai, int color, Material material, String materialName) {
        this.thai = thai;
        this.color = TextColor.color(color);
        this.material = material;
        this.materialName = materialName;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Rarity parse(String value) {
        if (value == null) return null;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
