package com.example.forge;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Three reforge outcomes, as in Apotheosis: they come from a per-player seed that only changes
 * after a reforge, so closing and reopening the menu shows the same three choices. The choices
 * are not sorted by strength; later choices only cost more.
 */
public final class ReforgeService {
    public static final int CHOICES = 3;

    public record Cost(int materials, int levels) {}

    private final ForgePlugin plugin;
    private final NamespacedKey seedKey;

    public ReforgeService(ForgePlugin plugin) {
        this.plugin = plugin;
        this.seedKey = new NamespacedKey(plugin, "reforge_seed");
    }

    public long seed(Player player) {
        Long seed = player.getPersistentDataContainer().get(seedKey, PersistentDataType.LONG);
        if (seed == null) {
            seed = ThreadLocalRandom.current().nextLong();
            player.getPersistentDataContainer().set(seedKey, PersistentDataType.LONG, seed);
        }
        return seed;
    }

    public void advanceSeed(Player player) {
        player.getPersistentDataContainer().set(seedKey, PersistentDataType.LONG, ThreadLocalRandom.current().nextLong());
    }

    /** Finished items for each choice, or an empty list when the gear cannot be reforged. */
    public List<ItemStack> choices(Player player, ItemStack gear, Rarity rarity) {
        AffixService affixes = plugin.affixes();
        LootCategory category = affixes.category(gear);
        if (category == null) return List.of();
        long base = seed(player)
            ^ (gear.getType().getKey().hashCode() * 0x9E3779B97F4A7C15L)
            ^ ((rarity.ordinal() + 1L) * 0xC2B2AE3D27D4EB4FL);
        List<ItemStack> choices = new ArrayList<>(CHOICES);
        for (int i = 0; i < CHOICES; i++) {
            Random random = new Random(base + i * 0x632BE59BD9B4E019L);
            ItemStack result = gear.clone();
            affixes.apply(result, affixes.roll(category, rarity, random));
            choices.add(result);
        }
        return choices;
    }

    public Cost cost(Rarity rarity, int choice) {
        ForgeSettings settings = plugin.settings();
        ForgeSettings.Grade grade = settings.grade(rarity);
        int multiplier = settings.choiceMultipliers[Math.clamp(choice, 0, CHOICES - 1)];
        return new Cost(grade.reforgeMaterials() * multiplier, grade.reforgeLevels() * multiplier);
    }

    public boolean allowed(Rarity rarity) {
        return rarity.ordinal() <= plugin.settings().maxReforgeRarity.ordinal();
    }
}
