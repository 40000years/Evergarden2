package com.example.forge;

import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/**
 * Entry points for other plugins (Evergarden's Vault rewards next). Callers should softdepend on
 * EvergardenForge and check {@link #available()} first.
 */
public final class ForgeApi {
    private static ForgePlugin plugin;

    private ForgeApi() {}

    static void bind(ForgePlugin instance) {
        plugin = instance;
    }

    public static boolean available() {
        return plugin != null && plugin.isEnabled();
    }

    /** Rolls affixes of the given rarity (COMMON..MYTHIC) onto the item in place. False when it cannot carry affixes. */
    public static boolean affix(ItemStack item, String rarity, Random random) {
        Rarity parsed = Rarity.parse(rarity);
        return available() && parsed != null && plugin.affixes().affix(item, parsed, random);
    }

    /** Rolls a rarity with the configured loot weights. */
    public static String rollRarity(Random random) {
        return available() ? plugin.affixes().rollRarity(random, plugin.settings().weights()).name() : Rarity.COMMON.name();
    }

    /** The item's affix rarity, or null when it has none. */
    public static String rarity(ItemStack item) {
        if (!available()) return null;
        AffixData data = plugin.affixes().read(item);
        return data == null ? null : data.rarity().name();
    }

    public static ItemStack material(String rarity, int amount) {
        Rarity parsed = Rarity.parse(rarity);
        return available() && parsed != null ? plugin.affixes().material(parsed, amount) : null;
    }

    /** Gem dust, used for gem cutting. */
    public static ItemStack dust(int amount) {
        return available() ? plugin.gems().dust(amount) : null;
    }

    /** Random affix gear (weapon, bow, shield, tool or armor) of a rarity, from the Overworld material table. */
    public static ItemStack gear(String rarity, Random random) {
        return gear(rarity, random, World.Environment.NORMAL);
    }

    /** Random affix gear using a world environment's material table (Overworld gear stops at iron). */
    public static ItemStack gear(String rarity, Random random, World.Environment environment) {
        Rarity parsed = Rarity.parse(rarity);
        return available() && parsed != null ? plugin.gear().affixed(parsed, random, environment) : null;
    }

    /** A gem of a type (fire, water, wind, earth, lightning, light, dark, life) and purity 1..5. */
    public static ItemStack gem(String type, int tier, int amount) {
        GemType parsed = GemType.parse(type);
        return available() && parsed != null ? plugin.gems().item(new GemService.Gem(parsed, tier), amount) : null;
    }

    /** Makes a living mob an elite of a rarity (UNCOMMON..MYTHIC). */
    public static boolean makeElite(LivingEntity entity, String rarity) {
        Rarity parsed = Rarity.parse(rarity);
        return available() && parsed != null && plugin.elites().makeElite(entity, parsed);
    }
}
