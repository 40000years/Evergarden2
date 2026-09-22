package com.example.voidscape.gui;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.enchant.EnchantApplyListener;
import com.example.voidscape.enchant.LimitBreakType;
import com.example.voidscape.enchant.UniqueEnchant;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class FloodgateUpgradeForm {
    private FloodgateUpgradeForm() {}

    /**
     * Opens the Bedrock Upgrade / Enchanting Form for a player.
     * @param targetSlot The inventory slot of the item to upgrade, or -1 to auto-detect/select.
     */
    public static boolean open(VoidscapePlugin plugin, Player player, int targetSlot) {
        if (player == null || !player.isOnline()) return false;

        // If targetSlot is -1, try main hand or show equipment picker
        if (targetSlot < 0) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (isEquipment(hand)) {
                return sendUpgradeOptions(plugin, player, player.getInventory().getHeldItemSlot());
            }
            return sendEquipmentSelector(plugin, player);
        }

        return sendUpgradeOptions(plugin, player, targetSlot);
    }

    public static boolean isEquipment(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getType().getMaxDurability() > 0;
    }

    private static String getItemName(ItemStack item) {
        if (item == null) return "Unknown";
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
        }
        String name = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] words = name.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX"; case 10 -> "X";
            default -> String.valueOf(n);
        };
    }

    /**
     * Form 1: Equipment Selector (if main hand is not an equipment, or player wants to choose another piece).
     */
    public static boolean sendEquipmentSelector(VoidscapePlugin plugin, Player player) {
        SimpleForm.Builder builder = SimpleForm.builder();
        builder.title("§6§lแท่นปลุกเสกมนตรา Evergarden");
        builder.content("§fกรุณาเลือกอาวุธหรืออุปกรณ์ที่คุณต้องการตีบวก:\n§7(ระบบจะสแกนคัมภีร์ที่เข้ากันได้ในกระเป๋าของคุณอัตโนมัติ)");

        List<Consumer<Player>> buttonActions = new ArrayList<>();
        ItemStack[] contents = player.getInventory().getContents();
        int foundCount = 0;

        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (!isEquipment(item)) continue;
            foundCount++;

            int finalSlot = slot;
            String name = getItemName(item);
            int maxDur = item.getType().getMaxDurability();
            int currentDmg = 0;
            boolean isUnbreakable = false;
            if (item.hasItemMeta()) {
                if (item.getItemMeta() instanceof Damageable dmg) currentDmg = dmg.getDamage();
                isUnbreakable = plugin.relics().isEternityItem(item);
            }

            String status = isUnbreakable ? "§6[สถิตนิรันดร์]" : "§7ความทนทาน: §f" + (maxDur - currentDmg) + "/" + maxDur;
            String icon = getEquipmentEmoji(item.getType());

            builder.button(icon + " " + name + "\n" + status);
            buttonActions.add(p -> sendUpgradeOptions(plugin, p, finalSlot));
        }

        if (foundCount == 0) {
            builder.content("§cไม่พบอาวุธ ชุดเกราะ หรือเครื่องมือในกระเป๋าของคุณ!\n§7กรุณานำอุปกรณ์ที่ต้องการตีบวกมาไว้ในตัวก่อนเปิดเมนู");
        }

        builder.button("❌ ปิดเมนู");
        buttonActions.add(p -> {});

        builder.validResultHandler(response -> {
            int buttonId = response.clickedButtonId();
            if (buttonId >= 0 && buttonId < buttonActions.size()) {
                Consumer<Player> action = buttonActions.get(buttonId);
                Bukkit.getScheduler().runTask(plugin, () -> action.accept(player));
            }
        });

        return FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder.build());
    }

    private static String getEquipmentEmoji(Material mat) {
        String name = mat.name();
        if (name.endsWith("_SWORD")) return "⚔️";
        if (name.endsWith("_AXE")) return "🪓";
        if (name.endsWith("_PICKAXE")) return "⛏️";
        if (name.endsWith("_SHOVEL")) return "🥄";
        if (name.endsWith("_HOE")) return "🌾";
        if (name.endsWith("_BOW") || name.endsWith("CROSSBOW")) return "🏹";
        if (name.endsWith("_HELMET")) return "🪖";
        if (name.endsWith("_CHESTPLATE")) return "👕";
        if (name.endsWith("_LEGGINGS")) return "👖";
        if (name.endsWith("_BOOTS")) return "👢";
        if (name.endsWith("SHIELD")) return "🛡️";
        if (name.endsWith("ELYTRA")) return "🪽";
        return "✨";
    }

    /**
     * Form 2: Upgrade Actions for the selected equipment.
     */
    public static boolean sendUpgradeOptions(VoidscapePlugin plugin, Player player, int targetSlot) {
        ItemStack target = player.getInventory().getItem(targetSlot);
        if (!isEquipment(target)) {
            return sendEquipmentSelector(plugin, player);
        }

        String targetName = getItemName(target);
        int maxDur = target.getType().getMaxDurability();
        int currentDmg = 0;
        boolean isUnbreakable = false;
        if (target.hasItemMeta()) {
            if (target.getItemMeta() instanceof Damageable dmg) currentDmg = dmg.getDamage();
            isUnbreakable = plugin.relics().isEternityItem(target);
        }

        SimpleForm.Builder builder = SimpleForm.builder();
        builder.title("§6§lปลุกเสก: §e" + targetName);

        StringBuilder content = new StringBuilder();
        content.append("§fอุปกรณ์ที่เลือก: §e").append(targetName).append("\n");
        content.append("§7ความทนทาน: §f").append(maxDur - currentDmg).append("§7/§f").append(maxDur);
        if (isUnbreakable) {
            content.append(" §6[✦ สถิตนิรันดร์: ไม่มีวันพัง]");
        }
        content.append("\n§7เลือกคัมภีร์หรือศิลาในกระเป๋าของคุณด้านล่างเพื่อใช้งาน:");

        List<Consumer<Player>> buttonActions = new ArrayList<>();
        int availableOptions = 0;

        // 1. Scroll of Eternity
        int eternityCount = countScrollEternity(plugin, player);
        if (eternityCount > 0 && !isUnbreakable) {
            availableOptions++;
            builder.button("📜 ปลุกเสกศิลานิรันดร์ (มี " + eternityCount + " ชิ้น)\n§aทำให้อุปกรณ์ไม่มีวันพังถาวร 100% [Unbreakable]");
            buttonActions.add(p -> applyEternity(plugin, p, targetSlot));
        }

        // 2. Limit Break Scrolls
        for (LimitBreakType type : LimitBreakType.values()) {
            int count = countLimitBreak(plugin, player, type);
            if (count > 0 && type.category().matches(target.getType())) {
                ItemMeta meta = target.getItemMeta();
                if (meta != null) {
                    int currentLevel = plugin.relics().getLimitBreakUpgradeLevel(target, type);
                    if (currentLevel > 0 && currentLevel < type.maxLevel()) {
                        availableOptions++;
                        builder.button("📜 ทลายขีดจำกัด: " + type.thaiTitle() + " (มี " + count + " ชิ้น)\n§bเพิ่ม " + type.title() + " เป็นระดับ " + toRoman(currentLevel + 1));
                        buttonActions.add(p -> applyLimitBreak(plugin, p, targetSlot, type));
                    }
                }
            }
        }

        // 3. Unique Enchant Scrolls
        for (UniqueEnchant ue : UniqueEnchant.values()) {
            int count = countUnique(plugin, player, ue);
            if (count > 0 && ue.category().matches(target.getType())) {
                if (!EnchantApplyListener.hasUnique(target, ue)) {
                    availableOptions++;
                    builder.button("📜 มนตราโบราณ: " + ue.title() + " (มี " + count + " ชิ้น)\n§d" + ue.thaiTitle() + " - " + ue.description());
                    buttonActions.add(p -> applyUnique(plugin, p, targetSlot, ue));
                }
            }
        }

        // 4. Vault Repair Stone
        int repairCount = countRepairStone(plugin, player);
        if (repairCount > 0 && currentDmg > 0) {
            availableOptions++;
            builder.button("💎 ศิลาฟื้นฟูมิติ (มี " + repairCount + " ชิ้น)\n§aซ่อมแซมความทนทาน 500 หน่วย (ชำรุด " + currentDmg + ")");
            buttonActions.add(p -> applyRepairStone(plugin, p, targetSlot));
        }

        if (availableOptions == 0) {
            content.append("\n\n§c⚠ ไม่พบคัมภีร์หรือศิลาในกระเป๋าที่สามารถใช้กับอุปกรณ์ชิ้นนี้ได้!");
        }

        builder.content(content.toString());

        // Navigation buttons
        builder.button("🔄 เลือกอุปกรณ์ชิ้นอื่น");
        buttonActions.add(p -> sendEquipmentSelector(plugin, p));

        builder.button("❌ ปิดเมนู");
        buttonActions.add(p -> {});

        builder.validResultHandler(response -> {
            int buttonId = response.clickedButtonId();
            if (buttonId >= 0 && buttonId < buttonActions.size()) {
                Consumer<Player> action = buttonActions.get(buttonId);
                Bukkit.getScheduler().runTask(plugin, () -> action.accept(player));
            }
        });

        return FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder.build());
    }

    // --- Upgrade Logic ---

    private static void applyEternity(VoidscapePlugin plugin, Player player, int targetSlot) {
        ItemStack target = player.getInventory().getItem(targetSlot);
        if (!isEquipment(target)) return;

        if (!consumeOne(player, it -> plugin.relics().isScrollEternity(it))) {
            player.sendMessage("§cไม่พบคัมภีร์ศิลานิรันดร์ในกระเป๋าของคุณ");
            return;
        }

        ItemMeta meta = target.getItemMeta();
        if (meta == null) return;
        plugin.relics().applyEternityMeta(meta);
        target.setItemMeta(meta);

        player.getInventory().setItem(targetSlot, target);
        playSuccessEffects(player, "✦ ปลุกเสกศิลานิรันดร์สำเร็จ! อุปกรณ์นี้ไม่มีวันพังถาวร");
        sendUpgradeOptions(plugin, player, targetSlot);
    }

    private static void applyLimitBreak(VoidscapePlugin plugin, Player player, int targetSlot, LimitBreakType type) {
        ItemStack target = player.getInventory().getItem(targetSlot);
        if (!isEquipment(target)) return;

        ItemMeta meta = target.getItemMeta();
        if (meta == null) return;
        int current = plugin.relics().getLimitBreakUpgradeLevel(target, type);
        if (current <= 0 || current >= type.maxLevel()) return;

        if (!consumeOne(player, it -> plugin.relics().getLimitBreakType(it) == type)) {
            player.sendMessage("§cไม่พบคัมภีร์ทลายขีดจำกัด " + type.title() + " ในกระเป๋า");
            return;
        }

        int next = current + 1;
        plugin.relics().applyLimitBreakMeta(target.getType(), meta, type, next);
        target.setItemMeta(meta);

        player.getInventory().setItem(targetSlot, target);
        playSuccessEffects(player, "✦ ทลายขีดจำกัดสำเร็จ! " + type.title() + " ระดับ " + toRoman(next));
        sendUpgradeOptions(plugin, player, targetSlot);
    }

    private static void applyUnique(VoidscapePlugin plugin, Player player, int targetSlot, UniqueEnchant ue) {
        ItemStack target = player.getInventory().getItem(targetSlot);
        if (!isEquipment(target)) return;

        ItemMeta meta = target.getItemMeta();
        if (meta == null) return;
        NamespacedKey key = new NamespacedKey("evergarden", "ue_" + ue.id().toLowerCase(Locale.ROOT));
        if (meta.getPersistentDataContainer().has(key)) return;

        if (!consumeOne(player, it -> plugin.relics().getUniqueEnchant(it) == ue)) {
            player.sendMessage("§cไม่พบคัมภีร์มนตรา " + ue.title() + " ในกระเป๋า");
            return;
        }

        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        if (ue == UniqueEnchant.ADVANCE_TOOL) {
            com.example.voidscape.enchant.EnchantApplyListener.applyAdvanceToolComponent(meta);
        }
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.text("✦ " + ue.title() + " · " + ue.thaiTitle(), NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("   §7" + ue.description()).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        target.setItemMeta(meta);

        player.getInventory().setItem(targetSlot, target);
        playSuccessEffects(player, "✦ สลักมนตราสำเร็จ! ได้รับ " + ue.title());
        sendUpgradeOptions(plugin, player, targetSlot);
    }

    private static void applyRepairStone(VoidscapePlugin plugin, Player player, int targetSlot) {
        ItemStack target = player.getInventory().getItem(targetSlot);
        if (!isEquipment(target)) return;

        ItemMeta meta = target.getItemMeta();
        if (!(meta instanceof Damageable dmg) || dmg.getDamage() <= 0) return;

        if (!consumeOne(player, it -> plugin.relics().isRepairStone(it))) {
            player.sendMessage("§cไม่พบศิลาฟื้นฟูมิติในกระเป๋า");
            return;
        }

        int restored = Math.min(500, dmg.getDamage());
        dmg.setDamage(Math.max(0, dmg.getDamage() - 500));
        target.setItemMeta(dmg);

        player.getInventory().setItem(targetSlot, target);
        player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1.0f, 1.2f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.05);
        player.sendActionBar(Component.text("✦ ศิลาฟื้นฟูมิติ ซ่อมแซมความทนทาน " + restored + " หน่วย!", NamedTextColor.GREEN));
        player.sendMessage("§a[Evergarden] §fศิลาฟื้นฟูมิติซ่อมแซมความทนทาน " + restored + " หน่วยเรียบร้อยแล้ว!");
        player.updateInventory();
        sendUpgradeOptions(plugin, player, targetSlot);
    }

    private static void playSuccessEffects(Player player, String msg) {
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.25f);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.35f);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1.2, 0), 25, 0.35, 0.35, 0.35, 0.1);
        player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 1.2, 0), 12, 0.25, 0.25, 0.25, 0.05);
        player.sendActionBar(Component.text(msg, NamedTextColor.GREEN));
        player.sendMessage("§a[Evergarden] §f" + msg);
        player.updateInventory();
    }

    private static boolean consumeOne(Player player, java.util.function.Predicate<ItemStack> predicate) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType().isAir()) continue;
            if (predicate.test(it)) {
                int amt = it.getAmount() - 1;
                if (amt <= 0) {
                    player.getInventory().setItem(i, null);
                } else {
                    it.setAmount(amt);
                    player.getInventory().setItem(i, it);
                }
                player.updateInventory();
                return true;
            }
        }
        return false;
    }

    private static int countScrollEternity(VoidscapePlugin plugin, Player player) {
        int count = 0;
        for (ItemStack it : player.getInventory().getContents()) {
            if (it != null && plugin.relics().isScrollEternity(it)) count += it.getAmount();
        }
        return count;
    }

    private static int countLimitBreak(VoidscapePlugin plugin, Player player, LimitBreakType type) {
        int count = 0;
        for (ItemStack it : player.getInventory().getContents()) {
            if (it != null && plugin.relics().getLimitBreakType(it) == type) count += it.getAmount();
        }
        return count;
    }

    private static int countUnique(VoidscapePlugin plugin, Player player, UniqueEnchant ue) {
        int count = 0;
        for (ItemStack it : player.getInventory().getContents()) {
            if (it != null && plugin.relics().getUniqueEnchant(it) == ue) count += it.getAmount();
        }
        return count;
    }

    private static int countRepairStone(VoidscapePlugin plugin, Player player) {
        int count = 0;
        for (ItemStack it : player.getInventory().getContents()) {
            if (it != null && plugin.relics().isRepairStone(it)) count += it.getAmount();
        }
        return count;
    }
}
