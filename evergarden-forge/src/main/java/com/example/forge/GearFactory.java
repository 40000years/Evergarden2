package com.example.forge;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** Random base gear of a rarity for elite drops and bonus chest loot: weapons, bows, shields, tools, armor. */
public final class GearFactory {
    /** Kinds without a material tier map straight to one item. */
    private static final Map<String, Material> FIXED = Map.of(
        "mace", Material.MACE,
        "trident", Material.TRIDENT,
        "bow", Material.BOW,
        "crossbow", Material.CROSSBOW,
        "shield", Material.SHIELD,
        "fishing_rod", Material.FISHING_ROD);
    private static final List<String> TIERED = List.of("sword", "axe", "spear", "pickaxe", "shovel", "hoe",
        "helmet", "chestplate", "leggings", "boots");
    private static final List<String> ARMOR = List.of("helmet", "chestplate", "leggings", "boots");

    private final ForgePlugin plugin;

    public GearFactory(ForgePlugin plugin) {
        this.plugin = plugin;
    }

    public static boolean knows(String kind) {
        return FIXED.containsKey(kind) || TIERED.contains(kind);
    }

    public ItemStack base(Rarity rarity, Random random, World.Environment environment) {
        Map<String, Integer> kinds = plugin.settings().gearKinds;
        int total = kinds.values().stream().mapToInt(Integer::intValue).sum();
        String kind = kinds.keySet().iterator().next();
        if (total > 0) {
            int ticket = random.nextInt(total);
            for (Map.Entry<String, Integer> entry : kinds.entrySet()) {
                ticket -= entry.getValue();
                if (ticket < 0) {
                    kind = entry.getKey();
                    break;
                }
            }
        }
        Material fixed = FIXED.get(kind);
        if (fixed != null) return new ItemStack(fixed);
        List<String> materials = plugin.settings().gearMaterials(environment, rarity);
        for (int attempt = 0; attempt < 16; attempt++) {
            Material material = resolve(materials.get(random.nextInt(materials.size())), kind);
            if (material != null) return new ItemStack(material);
        }
        // No family in the list makes this kind (e.g. only WOODEN for a helmet): use the list's first family for a sword.
        Material sword = resolve(materials.getFirst(), "sword");
        return new ItemStack(ARMOR.contains(kind) ? Material.LEATHER_CHESTPLATE : sword == null ? Material.WOODEN_SWORD : sword);
    }

    /** "CHAINMAIL" + "sword" has no item, "STONE" + "helmet" neither: null means pick again. */
    private static Material resolve(String family, String kind) {
        Material material = Material.matchMaterial(family.toUpperCase(Locale.ROOT) + "_" + kind.toUpperCase(Locale.ROOT));
        return material != null && LootCategory.of(material) != null ? material : null;
    }

    /** Base gear of a world's material table with affixes of the rarity applied. */
    public ItemStack affixed(Rarity rarity, Random random, World.Environment environment) {
        ItemStack item = base(rarity, random, environment);
        plugin.affixes().affix(item, rarity, random);
        return item;
    }
}
