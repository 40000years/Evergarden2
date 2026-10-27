package com.example.voidscape.enchant;

import com.example.voidscape.item.RelicService;
import com.example.voidscape.item.RelicService.Relic;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** One rule set for every scroll application path and for older, stacked items. */
public final class UniqueCompatibility {
    private UniqueCompatibility() {}

    private static int group(UniqueEnchant enchant) {
        return switch (enchant) {
            case COLOSSUS_SLAYER, RICOCHET, ABSOLUTE_ZERO, SINGULARITY, METEOR_ARROW -> 1;
            case GUILLOTINE, ECHO_STRIKE, BLADE_VORTEX, THUNDERLORD, TITAN_BREACH -> 2;
            case SOUL_HARVEST, VAMPIRIC -> 3;
            case SEISMIC_SLAM, VEIN_SMELTER, ADVANCE_TOOL -> 4;
            default -> 0;
        };
    }

    public static String ruleLabel(UniqueEnchant enchant) {
        return switch(group(enchant)) {
            case 1 -> "\u0e1e\u0e25\u0e31\u0e07\u0e2b\u0e25\u0e31\u0e01\u0e18\u0e19\u0e39: \u0e40\u0e25\u0e37\u0e2d\u0e01\u0e44\u0e14\u0e49 1 \u0e41\u0e1a\u0e1a\u0e15\u0e48\u0e2d\u0e04\u0e31\u0e19";
            case 2 -> "พลังหลักประชิด: เลือกได้ 1 แบบต่ออาวุธ; ไม่รวมกับ Advance Tool";
            case 3 -> "พลังดูดเลือด: เลือกได้ 1 แบบ และใช้กับ Blade Vortex ไม่ได้";
            case 4 -> "พลังหลักขุด: เลือกได้ 1 แบบ (รวมพลังอีเต้อโบราณ); Advance Tool ไม่รวมกับพลังหลักประชิด";
            default -> enchant == UniqueEnchant.SOULBOUND ? "ใช้ร่วมกับ Scroll of Eternity ไม่ได้" : "พลังเสริม";
        };
    }

    public static boolean conflict(UniqueEnchant left, UniqueEnchant right) {
        if (left == right) return true;
        int group = group(left);
        if (group != 0 && group == group(right)) return true;
        if (left == UniqueEnchant.ADVANCE_TOOL && group(right) == 2
            || right == UniqueEnchant.ADVANCE_TOOL && group == 2) return true;
        return left == UniqueEnchant.BLADE_VORTEX && group(right) == 3
            || right == UniqueEnchant.BLADE_VORTEX && group == 3;
    }

    private static boolean nativeConflict(ItemStack item, UniqueEnchant enchant, RelicService relics) {
        Relic relic = relics.type(item);
        return group(enchant) == 4 && (relic == Relic.RIFT_PICKAXE || relic == Relic.SMELTER_PICKAXE);
    }

    public static boolean canApply(ItemStack item, UniqueEnchant enchant, RelicService relics) {
        if (item == null || enchant == null || !enchant.category().matches(item.getType())) return false;
        if (needsResolution(item, relics)) return false;
        if (EnchantApplyListener.hasUnique(item, enchant)) return false;
        if (enchant == UniqueEnchant.SOULBOUND && relics.isEternityItem(item)) return false;
        if (nativeConflict(item, enchant, relics)) return false;
        for (UniqueEnchant existing : UniqueEnchant.values()) {
            if (EnchantApplyListener.hasUnique(item, existing) && conflict(enchant, existing)) return false;
        }
        return true;
    }

    public static boolean canApplyEternity(ItemStack item, RelicService relics) {
        return item != null && item.getType().getMaxDurability() > 0
            && !needsResolution(item, relics)
            && !relics.isEternityItem(item)
            && !EnchantApplyListener.hasUnique(item, UniqueEnchant.SOULBOUND);
    }

    /** Excess legacy scrolls stay on the item until claimed, but their extra powers do not fire. */
    public static boolean active(ItemStack item, UniqueEnchant enchant, RelicService relics) {
        if (!EnchantApplyListener.hasUnique(item, enchant)) return false;
        if (enchant == UniqueEnchant.SOULBOUND && relics.isEternityItem(item)) return false;
        if (nativeConflict(item, enchant, relics)) return false;
        for (UniqueEnchant earlier : UniqueEnchant.values()) {
            if (earlier == enchant) break;
            if (EnchantApplyListener.hasUnique(item, earlier) && conflict(enchant, earlier)
                && active(item, earlier, relics)) return false;
        }
        return true;
    }

    public static boolean needsResolution(ItemStack item, RelicService relics) {
        if (item == null || !item.hasItemMeta()) return false;
        for (UniqueEnchant enchant : UniqueEnchant.values()) {
            if (EnchantApplyListener.hasUnique(item, enchant) && !active(item, enchant, relics)) return true;
        }
        return false;
    }

    public static List<String> choices(ItemStack item, RelicService relics) {
        List<String> choices = new ArrayList<>();
        if (!needsResolution(item, relics)) return choices;
        Relic relic = relics.type(item);
        if (relic == Relic.RIFT_PICKAXE || relic == Relic.SMELTER_PICKAXE) choices.add("relic");
        if (relics.isEternityItem(item) && EnchantApplyListener.hasUnique(item, UniqueEnchant.SOULBOUND))
            choices.add("eternity");
        for (UniqueEnchant enchant : UniqueEnchant.values()) {
            if (!EnchantApplyListener.hasUnique(item, enchant)) continue;
            if (nativeConflict(item, enchant, relics)) continue;
            boolean hasConflict = enchant == UniqueEnchant.SOULBOUND && relics.isEternityItem(item);
            for (UniqueEnchant other : UniqueEnchant.values()) {
                if (other != enchant && EnchantApplyListener.hasUnique(item, other)
                    && conflict(enchant, other)) hasConflict = true;
            }
            if (hasConflict) choices.add(enchant.id());
        }
        return choices;
    }

    public record Resolution(ItemStack item, List<ItemStack> refunds) {}

    /** Resolve one conflicting family at a time, after the owner chooses the power to keep. */
    public static Resolution resolve(ItemStack original, String keep, RelicService relics) {
        if (original == null || keep == null || !needsResolution(original, relics)) return null;
        if (!choices(original, relics).contains(keep)) return null;
        ItemStack item = original.clone();
        List<ItemStack> refunds = new ArrayList<>();
        if (keep.equals("eternity")) {
            removeUnique(item, UniqueEnchant.SOULBOUND, relics);
            refunds.add(relics.createScrollUnique(UniqueEnchant.SOULBOUND));
        } else if (keep.equals("relic")) {
            for (UniqueEnchant enchant : UniqueEnchant.values()) {
                if (EnchantApplyListener.hasUnique(item, enchant) && nativeConflict(item, enchant, relics)) {
                    removeUnique(item, enchant, relics);
                    refunds.add(relics.createScrollUnique(enchant));
                }
            }
        } else {
            UniqueEnchant chosen = UniqueEnchant.parse(keep);
            if (chosen == null || !EnchantApplyListener.hasUnique(item, chosen)) return null;
            if (chosen == UniqueEnchant.SOULBOUND && relics.isEternityItem(item)) {
                removeEternity(item, relics);
                refunds.add(relics.createScrollEternity());
            }
            for (UniqueEnchant enchant : UniqueEnchant.values()) {
                if (enchant != chosen && EnchantApplyListener.hasUnique(item, enchant) && conflict(chosen, enchant)) {
                    removeUnique(item, enchant, relics);
                    refunds.add(relics.createScrollUnique(enchant));
                }
            }
        }
        return refunds.isEmpty() ? null : new Resolution(item, List.copyOf(refunds));
    }

    private static void removeUnique(ItemStack item, UniqueEnchant enchant, RelicService relics) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().remove(new NamespacedKey("evergarden", "ue_" + enchant.id()));
        if (meta.hasLore()) {
            List<Component> old = meta.lore();
            List<Component> lore = new ArrayList<>();
            for (int i = 0; i < old.size(); i++) {
                String line = PlainTextComponentSerializer.plainText().serialize(old.get(i));
                if (line.contains("✦ " + enchant.title() + " · ")) {
                    if (i + 1 < old.size() && PlainTextComponentSerializer.plainText()
                        .serialize(old.get(i + 1)).contains(enchant.description())) i++;
                    continue;
                }
                lore.add(old.get(i));
            }
            meta.lore(lore);
        }
        if (enchant == UniqueEnchant.ADVANCE_TOOL) {
            meta.setTool(null);
            int efficiency = relics.getLimitBreakLevel(item, LimitBreakType.EFFICIENCY);
            if (efficiency >= 6) EnchantApplyListener.applyEfficiencyToolComponent(item.getType(), meta, efficiency);
        }
        item.setItemMeta(meta);
    }

    private static void removeEternity(ItemStack item, RelicService relics) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().remove(new NamespacedKey("voidscape", "relic_eternity"));
        if (meta.hasLore()) {
            List<Component> lore = new ArrayList<>(meta.lore());
            lore.removeIf(line -> PlainTextComponentSerializer.plainText().serialize(line)
                .contains("สถิตนิรันดร์"));
            meta.lore(lore);
        }
        item.setItemMeta(meta);
    }
}
