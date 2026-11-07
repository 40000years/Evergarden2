package com.example.forge;

import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

import java.util.Locale;

/** Affix grade, separate from an item's own identity (a Legendary weapon stays Legendary). */
public enum Rarity {
    COMMON("Worn", 0xD8D8D8, Material.IRON_NUGGET, "เศษเหล็กหลอม"),
    UNCOMMON("Tempered", 0x5EE87A, Material.PRISMARINE_CRYSTALS, "ผลึกใบหยก"),
    RARE("Arcane", 0x4FB4FF, Material.ECHO_SHARD, "ชิ้นคริสตัลคราม"),
    EPIC("Exalted", 0xB47CFF, Material.AMETHYST_SHARD, "เกล็ดอาคมม่วง"),
    MYTHIC("Divine", 0xFF9A3C, Material.RESIN_BRICK, "อำพันหลอมเทพ");

    /** Player-facing grade name. */
    public final String label;
    public final TextColor color;
    public final Material material;
    public final String materialName;

    Rarity(String label, int color, Material material, String materialName) {
        this.label = label;
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
