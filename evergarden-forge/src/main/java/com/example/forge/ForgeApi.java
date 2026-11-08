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

    /** A charm by id (for example "blood_pact") at a grade (COMMON..MYTHIC); null when unknown. */
    public static ItemStack charm(String id, String rarity) {
        Charm charm = Charm.parse(id);
        Rarity grade = Rarity.parse(rarity);
        return available() && charm != null && grade != null ? plugin.charms().item(charm, grade) : null;
    }

    /** Every charm id, sanctum by sanctum (dark, astral, time; nine each). */
    public static java.util.List<String> charmIds() {
        return java.util.Arrays.stream(Charm.values()).map(Charm::id).toList();
    }

    /** The sanctum (dark, astral, time) a charm id belongs to, or null. */
    public static String charmTemple(String id) {
        Charm charm = Charm.parse(id);
        return charm == null ? null : charm.temple.id();
    }

    /** A random charm of a sanctum (dark, astral, time; null or "any" = all 27) at a grade. Sanctum rewards use this. */
    public static ItemStack rollCharm(String temple, String rarity, Random random) {
        Rarity grade = Rarity.parse(rarity);
        Charm.Temple from = temple == null || temple.equalsIgnoreCase("any") ? null : java.util.Arrays.stream(Charm.Temple.values())
            .filter(t -> t.id().equalsIgnoreCase(temple)).findFirst().orElse(null);
        if (!available() || grade == null || (from == null && temple != null && !temple.equalsIgnoreCase("any"))) return null;
        return plugin.charms().roll(from, grade, random);
    }

    /**
     * Makes a living mob an elite of a rarity (UNCOMMON..MYTHIC). When the mob belongs to the calling plugin (it
     * carries a key from elites.skip-namespaces, as Evergarden's sanctum mobs do), that plugin keeps the mob's
     * health, damage and name; Forge adds the grade, armor, aura, moves and rewards. An ordinary mob gets the
     * full elite multipliers.
     */
    public static boolean makeElite(LivingEntity entity, String rarity) {
        Rarity parsed = Rarity.parse(rarity);
        return available() && parsed != null && plugin.elites().makeElite(entity, parsed, plugin.elites().ownedByAnotherPlugin(entity));
    }
}
