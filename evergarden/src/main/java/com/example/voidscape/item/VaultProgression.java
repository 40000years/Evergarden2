package com.example.voidscape.item;

import org.bukkit.enchantments.Enchantment;
import java.util.Objects;

/** Tier quality within an existing enchantment profile; never raises its configured ceiling. */
public final class VaultProgression {
    public record LevelRange(int min,int max) {}

    private VaultProgression() {}

    public static LevelRange range(Enchantment enchantment,int configuredMin,int configuredMax,int tier) {
        Objects.requireNonNull(enchantment,"enchantment");
        int min=Math.clamp(configuredMin,1,32),top=Math.clamp(configuredMax,min,32);
        int nativeMax=enchantment.getMaxLevel();
        // Ordinary profiles such as fixed Mending or Protection IV retain their configured levels.
        if(top<=nativeMax)return new LevelRange(min,top);
        int base=Math.min(min,nativeMax),distance=top-base;
        int quarter=base+(distance+3)/4,middle=base+(distance+1)/2;
        return switch(Math.clamp(tier,1,5)) {
            case 1 -> new LevelRange(base,quarter);
            case 2 -> new LevelRange(quarter,quarter);
            case 3 -> new LevelRange(quarter,middle);
            case 4 -> new LevelRange(middle,top);
            default -> new LevelRange(top,top);
        };
    }

    /** New Vault scrolls stop at their tier's equipment ceiling; legacy scrolls keep their own cap. */
    public static int limitBreakCap(Enchantment enchantment,int maxLevel,int tier) {
        Objects.requireNonNull(enchantment,"enchantment");
        int nativeMax=enchantment.getMaxLevel();
        int ceiling=switch(enchantment.getKey().toString()) {
            case "minecraft:sharpness","minecraft:protection","minecraft:power",
                 "minecraft:efficiency","minecraft:smite" -> 8;
            case "minecraft:fortune","minecraft:looting","minecraft:unbreaking",
                 "minecraft:thorns","minecraft:depth_strider","minecraft:sweeping_edge",
                 "minecraft:breach" -> 5;
            case "minecraft:feather_falling","minecraft:density" -> 7;
            default -> nativeMax;
        };
        int top=Math.clamp(Math.min(maxLevel,ceiling),1,32);
        return top<=nativeMax?top:range(enchantment,nativeMax,top,tier).max();
    }
}
