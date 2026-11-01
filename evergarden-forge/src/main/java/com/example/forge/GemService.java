package com.example.forge;

import com.example.forge.LootCategory.Group;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.example.forge.AffixService.line;

/** Gem and gem dust items, purity tiers, gem cutting costs. Socketing itself lives in AffixService. */
public final class GemService {
    public static final int MAX_TIER = 5;
    public static final int MAX_SOCKETS = 3;
    public static final String[] TIER_NAMES = {"แตกร้าว", "หยาบ", "เจียระไน", "ไร้ตำหนิ", "สมบูรณ์แบบ"};
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V"};

    /** A gem in a socket or in hand. */
    public record Gem(GemType type, int tier) {
        public Gem {
            tier = Math.clamp(tier, 1, MAX_TIER);
        }

        public String serialize() {
            return type.id() + ":" + tier;
        }

        public static Gem parse(String text) {
            if (text == null || text.isBlank()) return null;
            String[] pair = text.split(":", 2);
            GemType type = GemType.parse(pair[0]);
            if (type == null) return null;
            try {
                return new Gem(type, pair.length == 2 ? Integer.parseInt(pair[1].trim()) : 1);
            } catch (NumberFormatException e) {
                return new Gem(type, 1);
            }
        }
    }

    public record CutCost(int gems, int dust, Rarity rarity, int materials) {}

    private final ForgePlugin plugin;
    private final NamespacedKey gemKey, dustKey;

    public GemService(ForgePlugin plugin) {
        this.plugin = plugin;
        gemKey = new NamespacedKey(plugin, "gem");
        dustKey = new NamespacedKey(plugin, "gem_dust");
    }

    public static String roman(int tier) {
        return ROMAN[Math.clamp(tier, 1, MAX_TIER) - 1];
    }

    public static String tierName(int tier) {
        return TIER_NAMES[Math.clamp(tier, 1, MAX_TIER) - 1];
    }

    public double value(Gem gem, Group group) {
        GemType.Effect effect = gem.type().effects.get(group);
        return effect.base() * plugin.settings().gemTierMultiplier(gem.tier());
    }

    public String describe(Gem gem, Group group) {
        return gem.type().effects.get(group).stat().format(value(gem, group));
    }

    // ---- items --------------------------------------------------------------------------------

    public ItemStack item(Gem gem, int amount) {
        ItemStack item = new ItemStack(gem.type().material, Math.max(1, amount));
        item.editMeta(meta -> {
            meta.customName(line(gem.type().thai + " " + roman(gem.tier()), gem.type().color));
            List<Component> lore = new ArrayList<>();
            lore.add(line("ความบริสุทธิ์ " + roman(gem.tier()) + " · " + tierName(gem.tier()), NamedTextColor.GRAY));
            lore.add(line("ใส่ในช่อง Gem ของอุปกรณ์ที่มีเกรด:", NamedTextColor.WHITE));
            for (Group group : Group.values()) {
                lore.add(line(" • " + group.thai + ": ", NamedTextColor.GRAY).append(line(describe(gem, group), gem.type().color)));
            }
            if (gem.tier() < MAX_TIER) lore.add(line("เจียระไน 2 เม็ดเท่ากัน → ขั้น " + roman(gem.tier() + 1), NamedTextColor.DARK_GRAY));
            lore.add(line("/forge → แท็บ Gem เพื่อใส่ · ถอดได้ฟรี", NamedTextColor.DARK_GRAY));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(gemKey, PersistentDataType.STRING, gem.serialize());
        });
        ForgeResources.model(plugin,item);
        return item;
    }

    public Gem read(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return Gem.parse(item.getItemMeta().getPersistentDataContainer().get(gemKey, PersistentDataType.STRING));
    }

    public ItemStack dust(int amount) {
        ItemStack item = new ItemStack(Material.GUNPOWDER, Math.max(1, amount));
        item.editMeta(meta -> {
            meta.customName(line("ผงอัญมณี", net.kyori.adventure.text.format.TextColor.color(0xE0B0FF)));
            meta.lore(List.of(
                line("ได้จากการย่อย Gem", NamedTextColor.GRAY),
                line("ใช้เจียระไน Gem ขึ้นขั้น", NamedTextColor.GRAY)));
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(dustKey, PersistentDataType.BYTE, (byte) 1);
        });
        ForgeResources.model(plugin,item);
        return item;
    }

    public boolean isDust(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(dustKey, PersistentDataType.BYTE);
    }

    /** Gems, dust and salvage materials: none of them may feed vanilla recipes or blocks. */
    public boolean isForgeItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(gemKey, PersistentDataType.STRING) || pdc.has(dustKey, PersistentDataType.BYTE)
            || plugin.affixes().materialRarity(item) != null;
    }

    // ---- loot and cutting ----------------------------------------------------------------------

    public Gem roll(Rarity rarity, Random random) {
        int[] range = plugin.settings().gemTierRange(rarity);
        GemType type = GemType.values()[random.nextInt(GemType.values().length)];
        return new Gem(type, range[0] + random.nextInt(range[1] - range[0] + 1));
    }

    /** Cost to cut a gem of this tier into the next; null at the top tier. */
    public CutCost cutCost(int tier) {
        if (tier >= MAX_TIER) return null;
        ForgeSettings settings = plugin.settings();
        int index = tier - 1;
        return new CutCost(2, settings.cutDust[index], Rarity.values()[Math.min(tier, Rarity.values().length - 1)], settings.cutMaterials[index]);
    }

    public int salvageDust(int tier) {
        return plugin.settings().gemSalvageDust[Math.clamp(tier, 1, MAX_TIER) - 1];
    }
}
