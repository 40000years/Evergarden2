package com.example.forge;

import com.example.forge.LootCategory.Group;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Eight elemental gems. One gem gives a different bonus depending on the gear it sits in, so the
 * same stone is a choice between builds. Values are for purity I; higher purities multiply them
 * (gems.tier-multipliers). The forge addon supplies the Java models and matching Bedrock mappings.
 */
public enum GemType {
    FIRE("อัญมณีเพลิง", 0xFF6A3D, Material.FIRE_CHARGE,
        Stat.ATTACK_DAMAGE, 0.5, Stat.ARROW_DAMAGE, 0.04, Stat.BURN_TIME, -0.08, Stat.BURN_TIME, -0.12, Stat.BREAK_SPEED, 0.04),
    WATER("อัญมณีวารี", 0x3FA9F5, Material.HEART_OF_THE_SEA,
        Stat.SWEEP, 0.06, Stat.ARROW_DAMAGE, 0.03, Stat.WATER_MOVEMENT, 0.06, Stat.KNOCKBACK_RESIST, 0.04, Stat.SUBMERGED_MINING, 0.1),
    WIND("อัญมณีวายุ", 0xCFEFEA, Material.GHAST_TEAR,
        Stat.MOVE_SPEED, 0.015, Stat.MOVE_SPEED, 0.015, Stat.SAFE_FALL, 0.5, Stat.MOVE_SPEED, 0.015, Stat.MOVE_SPEED, 0.015),
    EARTH("อัญมณีปฐพี", 0xA0703C, Material.ARMADILLO_SCUTE,
        Stat.KNOCKBACK, 0.15, Stat.MAX_HEALTH, 0.4, Stat.ARMOR, 0.4, Stat.TOUGHNESS, 0.5, Stat.MINING_EFFICIENCY, 1.0),
    LIGHTNING("อัญมณีอสนี", 0xFFE14D, Material.GLOWSTONE_DUST,
        Stat.ATTACK_SPEED, 0.03, Stat.ARROW_SPEED, 0.04, Stat.MOVE_SPEED, 0.01, Stat.MOVE_SPEED, 0.01, Stat.BREAK_SPEED, 0.06),
    LIGHT("อัญมณีรัศมี", 0xFFF4D6, Material.QUARTZ,
        Stat.LUCK, 0.4, Stat.LUCK, 0.4, Stat.KNOCKBACK_RESIST, 0.03, Stat.ARMOR, 0.5, Stat.LUCK, 0.5),
    DARK("อัญมณีราตรี", 0x7A4FC0, Material.ENDER_PEARL,
        Stat.ATTACK_DAMAGE_PERCENT, 0.025, Stat.ARROW_DAMAGE, 0.05, Stat.TOUGHNESS, 0.3, Stat.EXPLOSION_RESIST, 0.08, Stat.MINING_EFFICIENCY, 0.8),
    LIFE("อัญมณีชีวา", 0x4CD964, Material.EMERALD,
        Stat.MAX_HEALTH, 0.5, Stat.MAX_HEALTH, 0.5, Stat.MAX_HEALTH, 0.6, Stat.MAX_HEALTH, 0.8, Stat.MAX_HEALTH, 0.5);

    public record Effect(Stat stat, double base) {}

    public final String thai;
    public final TextColor color;
    public final Material material;
    public final Map<Group, Effect> effects = new EnumMap<>(Group.class);

    GemType(String thai, int color, Material material,
            Stat weapon, double weaponValue, Stat ranged, double rangedValue, Stat armor, double armorValue,
            Stat shield, double shieldValue, Stat tool, double toolValue) {
        this.thai = thai;
        this.color = TextColor.color(color);
        this.material = material;
        effects.put(Group.WEAPON, new Effect(weapon, weaponValue));
        effects.put(Group.RANGED, new Effect(ranged, rangedValue));
        effects.put(Group.ARMOR, new Effect(armor, armorValue));
        effects.put(Group.SHIELD, new Effect(shield, shieldValue));
        effects.put(Group.TOOL, new Effect(tool, toolValue));
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static GemType parse(String id) {
        if (id == null) return null;
        try {
            return valueOf(id.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
