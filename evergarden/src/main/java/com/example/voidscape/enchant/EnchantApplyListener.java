package com.example.voidscape.enchant;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.item.RelicService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EnchantApplyListener implements Listener {
    private final VoidscapePlugin plugin;
    private final RelicService relics;

    public EnchantApplyListener(VoidscapePlugin plugin, RelicService relics) {
        this.plugin = plugin;
        this.relics = relics;
    }

    public boolean isScroll(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        return relics.isScrollEternity(item)
            || relics.getLimitBreakType(item) != null
            || relics.getUniqueEnchant(item) != null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack cursor = event.getCursor();
        ItemStack target = event.getCurrentItem();

        if (cursor == null || cursor.getType().isAir() || target == null || target.getType().isAir()) {
            return;
        }

        // Case A: Cursor is scroll, target is equipment
        if (isScroll(cursor)) {
            event.setCancelled(true);
            applyAnyScroll(player, cursor, target, null, event, -1);
            return;
        }

        // Case B: Cursor is equipment, target is scroll (common on mobile touch selection)
        if (isScroll(target) && cursor.getType().getMaxDurability() > 0) {
            event.setCancelled(true);
            handleEquipmentOnScroll(player, target, cursor, event);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack oldCursor = event.getOldCursor();
        if (!isScroll(oldCursor)) return;

        // Prevent dragging scrolls across multiple slots to prevent splitting/ghost items
        event.setCancelled(true);

        if (event.getRawSlots().size() == 1) {
            int rawSlot = event.getRawSlots().iterator().next();
            ItemStack target = event.getView().getItem(rawSlot);
            if (target != null && !target.getType().isAir() && target.getType().getMaxDurability() > 0) {
                applyAnyScroll(player, oldCursor, target, null, null, rawSlot);
            }
        }
    }

    private void handleEquipmentOnScroll(Player player, ItemStack scrollInSlot, ItemStack equipOnCursor, InventoryClickEvent event) {
        ItemStack result = relics.evaluateScrollCraft(scrollInSlot, equipOnCursor);
        if (result == null) {
            fail(player, "ไม่สามารถใช้คัมภีร์นี้กับอุปกรณ์ดังกล่าวได้");
            return;
        }

        // Consume 1 scroll from slot
        ItemStack remainingScroll = null;
        if (scrollInSlot.getAmount() > 1) {
            remainingScroll = scrollInSlot.clone();
            remainingScroll.setAmount(scrollInSlot.getAmount() - 1);
        }

        if (event.getClickedInventory() != null && event.getSlot() >= 0) {
            event.getClickedInventory().setItem(event.getSlot(), remainingScroll);
        }
        player.setItemOnCursor(result);
        success(player, "✦ ปลุกเสกมนตราสำเร็จ!");

        final ItemStack finalRemaining = remainingScroll;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                if (event.getClickedInventory() != null && event.getSlot() >= 0) {
                    event.getClickedInventory().setItem(event.getSlot(), finalRemaining);
                }
                player.setItemOnCursor(result);
                player.updateInventory();
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        // Prevent double-firing: Paper fires PlayerInteractEvent for both HAND and OFF_HAND in the same tick
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;

        Player player = event.getPlayer();
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();

        if (main.getType().isAir() || off.getType().isAir()) return;

        // Case 1: Scroll in main hand, target item in off hand
        if (isScroll(main)) {
            event.setCancelled(true);
            applyAnyScroll(player, main, off, EquipmentSlot.HAND, null, -1);
            return;
        }

        // Case 2: Scroll in off hand, target item in main hand
        if (isScroll(off)) {
            event.setCancelled(true);
            applyAnyScroll(player, off, main, EquipmentSlot.OFF_HAND, null, -1);
            return;
        }
    }

    private void applyAnyScroll(Player player, ItemStack source, ItemStack target, EquipmentSlot hand, InventoryClickEvent clickEvent, int rawSlot) {
        boolean isCursor = (clickEvent != null || rawSlot >= 0);

        if (relics.isScrollEternity(source)) {
            applyScrollEternity(player, source, target, isCursor, hand, clickEvent, rawSlot);
            return;
        }

        LimitBreakType lbType = relics.getLimitBreakType(source);
        if (lbType != null) {
            applyLimitBreak(player, source, target, lbType, isCursor, hand, clickEvent, rawSlot);
            return;
        }

        UniqueEnchant unique = relics.getUniqueEnchant(source);
        if (unique != null) {
            applyUniqueEnchant(player, source, target, unique, isCursor, hand, clickEvent, rawSlot);
            return;
        }
    }

    private void applyScrollEternity(Player player, ItemStack source, ItemStack target, boolean isCursor, EquipmentSlot hand, InventoryClickEvent clickEvent, int rawSlot) {
        if (target.getType().getMaxDurability() <= 0) {
            fail(player, "ไอเทมนี้ไม่มีความทนทาน ไม่จำเป็นต้องใช้คัมภีร์ศิลานิรันดร์");
            return;
        }
        ItemMeta meta = target.getItemMeta();
        if (meta == null) return;

        if (relics.isEternityItem(target)) {
            fail(player, "ไอเทมนี้สถิตนิรันดร์อยู่แล้ว (ไม่มีวันพัง)");
            return;
        }

        relics.applyEternityMeta(meta);
        target.setItemMeta(meta);

        if (rawSlot >= 0) {
            player.getOpenInventory().setItem(rawSlot, target);
        } else if (isCursor && clickEvent != null) {
            clickEvent.setCurrentItem(target);
        }

        consumeSource(player, source, target, isCursor, hand, clickEvent, rawSlot);
        success(player, "✦ ปลุกเสกศิลานิรันดร์สำเร็จ! อุปกรณ์นี้จะไม่มีวันพังถาวร");
    }

    private void applyLimitBreak(Player player, ItemStack source, ItemStack target, LimitBreakType type, boolean isCursor, EquipmentSlot hand, InventoryClickEvent clickEvent, int rawSlot) {
        if (!type.category().matches(target.getType())) {
            fail(player, "คัมภีร์นี้ใช้ได้กับ " + type.targetDescription() + " เท่านั้น");
            return;
        }
        ItemMeta meta = target.getItemMeta();
        if (meta == null) return;

        int current = relics.getLimitBreakUpgradeLevel(target, type);
        if (current <= 0) {
            fail(player, "อุปกรณ์ต้องมีเอนแชนต์ " + type.enchantment().getKey().getKey() + " อยู่ก่อนแล้ว");
            return;
        }
        if (current >= type.maxLevel()) {
            fail(player, "เอนแชนต์นี้ถึงระดับสูงสุดแล้ว (" + type.maxLevel() + ")");
            return;
        }

        int next = current + 1;
        relics.applyLimitBreakMeta(target.getType(), meta, type, next);
        target.setItemMeta(meta);

        if (rawSlot >= 0) {
            player.getOpenInventory().setItem(rawSlot, target);
        } else if (isCursor && clickEvent != null) {
            clickEvent.setCurrentItem(target);
        }

        consumeSource(player, source, target, isCursor, hand, clickEvent, rawSlot);
        success(player, "✦ ทลายขีดจำกัดสำเร็จ! " + type.title() + " ระดับ " + toRoman(next));
    }

    private void applyUniqueEnchant(Player player, ItemStack source, ItemStack target, UniqueEnchant enchant, boolean isCursor, EquipmentSlot hand, InventoryClickEvent clickEvent, int rawSlot) {
        if (!enchant.category().matches(target.getType())) {
            fail(player, "คัมภีร์นี้ใช้ได้กับ " + enchant.category().name() + " เท่านั้น");
            return;
        }
        ItemMeta meta = target.getItemMeta();
        if (meta == null) return;

        NamespacedKey key = new NamespacedKey("evergarden", "ue_" + enchant.id().toLowerCase(Locale.ROOT));
        if (meta.getPersistentDataContainer().has(key)) {
            fail(player, "อุปกรณ์นี้มีมนตรา " + enchant.thaiTitle() + " อยู่แล้ว");
            return;
        }

        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        if (enchant == UniqueEnchant.ADVANCE_TOOL) {
            applyAdvanceToolComponent(meta);
        }
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.text("✦ " + enchant.title() + " · " + enchant.thaiTitle(), NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("   §7" + enchant.description()).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        target.setItemMeta(meta);

        if (rawSlot >= 0) {
            player.getOpenInventory().setItem(rawSlot, target);
        } else if (isCursor && clickEvent != null) {
            clickEvent.setCurrentItem(target);
        }

        consumeSource(player, source, target, isCursor, hand, clickEvent, rawSlot);
        success(player, "✦ สลักมนตราสำเร็จ! ได้รับ " + enchant.title());
    }

    private void consumeSource(Player player, ItemStack source, ItemStack target, boolean isCursor, EquipmentSlot hand, InventoryClickEvent clickEvent, int rawSlot) {
        if (isCursor) {
            ItemStack remaining = null;
            if (source.getAmount() > 1) {
                remaining = source.clone();
                remaining.setAmount(source.getAmount() - 1);
            }
            player.setItemOnCursor(remaining);
            if (clickEvent != null) {
                try { clickEvent.setCursor(remaining); } catch (Throwable ignored) {}
                try { clickEvent.getView().setCursor(remaining); } catch (Throwable ignored) {}
            }
            final ItemStack finalRemaining = remaining;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.setItemOnCursor(finalRemaining);
                    if (rawSlot >= 0) {
                        player.getOpenInventory().setItem(rawSlot, target);
                    } else if (clickEvent != null && clickEvent.getSlot() >= 0 && clickEvent.getClickedInventory() != null) {
                        clickEvent.getClickedInventory().setItem(clickEvent.getSlot(), target);
                    }

                    // Anti-dupe Bedrock safeguard:
                    // If Bedrock's client prediction swapped the old item to cursor,
                    // ensure cursor does not contain an un-enchanted clone of target
                    ItemStack onCursor = player.getItemOnCursor();
                    if (onCursor != null && onCursor.getType() == target.getType() && !isScroll(onCursor)) {
                        player.setItemOnCursor(finalRemaining);
                    }

                    player.updateInventory();
                }
            });
        } else {
            ItemStack remaining = null;
            if (source.getAmount() > 1) {
                remaining = source.clone();
                remaining.setAmount(source.getAmount() - 1);
            }
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(remaining);
                player.getInventory().setItemInMainHand(target);
            } else {
                player.getInventory().setItemInMainHand(remaining);
                player.getInventory().setItemInOffHand(target);
            }
            final ItemStack finalRemaining = remaining;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    if (hand == EquipmentSlot.OFF_HAND) {
                        player.getInventory().setItemInOffHand(finalRemaining);
                        player.getInventory().setItemInMainHand(target);
                    } else {
                        player.getInventory().setItemInMainHand(finalRemaining);
                        player.getInventory().setItemInOffHand(target);
                    }
                    player.updateInventory();
                }
            });
        }
        player.updateInventory();
    }

    private void fail(Player player, String message) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
        player.sendActionBar(Component.text("⚠ " + message, NamedTextColor.RED));
    }

    private void success(Player player, String message) {
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.25f);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.35f);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1.2, 0), 25, 0.35, 0.35, 0.35, 0.1);
        player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 1.2, 0), 12, 0.25, 0.25, 0.25, 0.05);
        player.sendActionBar(Component.text(message, NamedTextColor.GREEN));
    }

    private static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX"; case 10 -> "X";
            default -> String.valueOf(n);
        };
    }

    public static boolean hasUnique(ItemStack item, UniqueEnchant enchant) {
        if (item == null || !item.hasItemMeta()) return false;
        NamespacedKey key = new NamespacedKey("evergarden", "ue_" + enchant.id().toLowerCase(Locale.ROOT));
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    public static void applyAdvanceToolComponent(ItemMeta meta) {
        if (meta == null) return;
        try {
            org.bukkit.inventory.meta.components.ToolComponent tool = meta.getTool();
            tool.setDefaultMiningSpeed(25.0f);
            tool.setDamagePerBlock(1);
            tool.addRule(Tag.MINEABLE_PICKAXE, 25.0f, true);
            tool.addRule(Tag.MINEABLE_AXE, 25.0f, true);
            tool.addRule(Tag.MINEABLE_SHOVEL, 25.0f, true);
            tool.addRule(Tag.MINEABLE_HOE, 25.0f, true);
            tool.addRule(List.of(
                Material.DIRT, Material.COARSE_DIRT, Material.ROOTED_DIRT, Material.GRASS_BLOCK,
                Material.PODZOL, Material.MYCELIUM, Material.SAND, Material.RED_SAND,
                Material.GRAVEL, Material.CLAY, Material.SOUL_SAND, Material.SOUL_SOIL,
                Material.MUD, Material.MUDDY_MANGROVE_ROOTS, Material.SNOW_BLOCK, Material.SNOW,
                Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES, Material.JUNGLE_LEAVES,
                Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES, Material.MANGROVE_LEAVES, Material.CHERRY_LEAVES
            ), 25.0f, true);
            meta.setTool(tool);
        } catch (Throwable ignored) {
        }
    }

    public static void applyEfficiencyToolComponent(Material mat, ItemMeta meta, int level) {
        if (meta == null || level < 6) return;
        try {
            org.bukkit.inventory.meta.components.ToolComponent tool = meta.getTool();
            // Formula: 9.0 (base netherite) + level^2 + 1
            // Level 9: 9 + 82 = 91.0 (>= 90.0 required to instamine Deepslate 3.0 hardness)
            // Level 10: 9 + 101 = 110.0 (Ultra instamine)
            float speed = (float) (9.0 + (level * level + 1));
            tool.setDefaultMiningSpeed(1.0f);
            tool.setDamagePerBlock(1);
            if (mat != null) {
                String name = mat.name();
                if (name.endsWith("_PICKAXE")) {
                    tool.addRule(Tag.MINEABLE_PICKAXE, speed, true);
                } else if (name.endsWith("_AXE")) {
                    tool.addRule(Tag.MINEABLE_AXE, speed, true);
                } else if (name.endsWith("_SHOVEL")) {
                    tool.addRule(Tag.MINEABLE_SHOVEL, speed, true);
                } else if (name.endsWith("_HOE")) {
                    tool.addRule(Tag.MINEABLE_HOE, speed, true);
                } else {
                    tool.addRule(Tag.MINEABLE_PICKAXE, speed, true);
                }
            } else {
                tool.addRule(Tag.MINEABLE_PICKAXE, speed, true);
            }
            meta.setTool(tool);
        } catch (Throwable ignored) {
        }
    }

    public static void applyEfficiencyToolComponent(ItemMeta meta, int level) {
        applyEfficiencyToolComponent(null, meta, level);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (relics.isEternityItem(event.getItem())) {
            event.setCancelled(true);
        }
    }
}
