package com.example.voidscape.gui;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.enchant.EnchantApplyListener;
import com.example.voidscape.enchant.LimitBreakType;
import com.example.voidscape.enchant.UniqueEnchant;
import com.example.voidscape.enchant.UniqueCompatibility;
import com.example.voidscape.enchant.LegacyEnchantResolution;
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

    public static boolean sendLegacyResolution(VoidscapePlugin plugin,Player player) {
        return sendLegacyResolution(plugin,player,player.getInventory().getHeldItemSlot());
    }

    public static boolean sendLegacyResolution(VoidscapePlugin plugin,Player player,int targetSlot) {
        ItemStack held=player.getInventory().getItem(targetSlot);
        List<String> choices=UniqueCompatibility.choices(held,plugin.relics());
        if(choices.isEmpty())return false;
        ItemStack expected=held.clone();
        SimpleForm.Builder builder=SimpleForm.builder();
        builder.title("§6เลือกมนตราที่จะเก็บไว้");
        builder.content("§fอุปกรณ์ในมือมีมนตราที่ใช้ร่วมกันไม่ได้\n§7เลือกพลังที่จะเก็บ ส่วนที่ขัดกันจะคืนเป็นคัมภีร์ ต้องมีช่องว่างในกระเป๋า");
        for(String choice:choices) {
            String label=choice.equals("relic")?"พลังประจำยุทธภัณฑ์":choice.equals("eternity")?"Scroll of Eternity":UniqueEnchant.parse(choice).title();
            builder.button("§e"+label+"\n§7เก็บพลังนี้");
        }
        builder.validResultHandler(response->{
            int index=response.clickedButtonId();
            if(index<0||index>=choices.size())return;
            Bukkit.getScheduler().runTask(plugin,()->{
                if(!player.isOnline())return;
                if(LegacyEnchantResolution.resolveSlot(plugin,player,targetSlot,expected,choices.get(index)))
                    sendUpgradeOptions(plugin,player,targetSlot);
            });
        });
        return FloodgateApi.getInstance().sendForm(player.getUniqueId(),builder.build());
    }

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
        if (UniqueCompatibility.needsResolution(target,plugin.relics()))
            return sendLegacyResolution(plugin,player,targetSlot);

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
        content.append("\n§7เลือกคัมภีร์ในกระเป๋าของคุณด้านล่างเพื่อใช้งาน:");

        List<Consumer<Player>> buttonActions = new ArrayList<>();
        int availableOptions = 0;

        // 1. Scroll of Eternity
        int eternityCount = countScrollEternity(plugin, player);
        if (eternityCount > 0 && UniqueCompatibility.canApplyEternity(target,plugin.relics())) {
            availableOptions++;
            builder.button("📜 ปลุกเสกศิลานิรันดร์ (มี " + eternityCount + " ชิ้น)\n§aทำให้อุปกรณ์ไม่มีวันพังถาวร 100% [Unbreakable]");
            buttonActions.add(p -> applyEternity(plugin, p, targetSlot));
        }

        // 2. Limit Break Scrolls
        for (LimitBreakType type : LimitBreakType.values()) {
            int count = countUsableLimitBreak(plugin, player, type, target);
            int scrollSlot = findLimitBreakSlot(plugin, player, type, target);
            if (count > 0 && scrollSlot >= 0) {
                int currentLevel = plugin.relics().getLimitBreakUpgradeLevel(target, type);
                int cap = plugin.relics().getLimitBreakCap(player.getInventory().getItem(scrollSlot));
                availableOptions++;
                builder.button("📜 ทลายขีดจำกัด: " + type.thaiTitle() + " (ใช้ได้ " + count + " ชิ้น)\n§bระดับ " + toRoman(currentLevel + 1) + " · ใช้คัมภีร์เพดาน " + toRoman(cap) + " ก่อน");
                ItemStack expected = target.clone();
                buttonActions.add(p -> applyLimitBreak(plugin, p, targetSlot, type, expected));
            }
        }

        // 3. Unique Enchant Scrolls
        for (UniqueEnchant ue : UniqueEnchant.values()) {
            int count = countUnique(plugin, player, ue);
            if (count > 0 && UniqueCompatibility.canApply(target,ue,plugin.relics())) {
                if (!EnchantApplyListener.hasUnique(target, ue)) {
                    availableOptions++;
                    builder.button("📜 มนตราโบราณ: " + ue.title() + " (มี " + count + " ชิ้น)\n§d" + ue.thaiTitle() + " - " + ue.description());
                    buttonActions.add(p -> applyUnique(plugin, p, targetSlot, ue));
                }
            }
        }

        if (availableOptions == 0) {
            content.append("\n\n§c⚠ ไม่พบคัมภีร์ในกระเป๋าที่สามารถใช้กับอุปกรณ์ชิ้นนี้ได้!");
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
        if (!UniqueCompatibility.canApplyEternity(target,plugin.relics())) {
            player.sendMessage("§cSoulbound ใช้ร่วมกับ Scroll of Eternity ไม่ได้ หรืออุปกรณ์นี้สถิตนิรันดร์แล้ว");
            return;
        }

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

    private static void applyLimitBreak(VoidscapePlugin plugin, Player player, int targetSlot,
                                       LimitBreakType type, ItemStack expected) {
        if (!player.isOnline()) return;
        if (!expected.equals(player.getInventory().getItem(targetSlot))) {
            player.sendMessage("§cอุปกรณ์เปลี่ยนไปแล้ว กรุณาเลือกคัมภีร์ใหม่");
            sendUpgradeOptions(plugin, player, targetSlot);
            return;
        }
        applyLimitBreak(plugin, player, targetSlot, type);
    }

    private static void applyLimitBreak(VoidscapePlugin plugin, Player player, int targetSlot, LimitBreakType type) {
        if (!player.isOnline()) return;
        ItemStack target = player.getInventory().getItem(targetSlot);
        if (!isEquipment(target) || target.getAmount() != 1) return;
        int scrollSlot = findLimitBreakSlot(plugin, player, type, target);
        if (scrollSlot < 0) {
            player.sendMessage("§cไม่พบคัมภีร์ " + type.title() + " ที่อัปอุปกรณ์นี้ต่อได้ · ตรวจชนิดและเพดานคัมภีร์");
            return;
        }
        ItemStack source = player.getInventory().getItem(scrollSlot).clone();
        ItemStack upgraded = plugin.relics().evaluateScrollCraft(source, target);
        if (upgraded == null) return;

        // Commit the exact eligible stack only after the shared grade/compatibility check succeeds.
        // Lower usable grades are selected first; capped scrolls are never spent.
        if (!target.equals(player.getInventory().getItem(targetSlot))
                || !source.equals(player.getInventory().getItem(scrollSlot))) return;
        int next = plugin.relics().getLimitBreakUpgradeLevel(upgraded, type);
        source.setAmount(source.getAmount() - 1);
        player.getInventory().setItem(scrollSlot, source.getAmount() == 0 ? null : source);
        player.getInventory().setItem(targetSlot, upgraded);
        playSuccessEffects(player, "✦ ทลายขีดจำกัดสำเร็จ! " + type.title() + " ระดับ " + toRoman(next));
        sendUpgradeOptions(plugin, player, targetSlot);
    }

    private static void applyUnique(VoidscapePlugin plugin, Player player, int targetSlot, UniqueEnchant ue) {
        ItemStack target = player.getInventory().getItem(targetSlot);
        if (!isEquipment(target)) return;
        if (!UniqueCompatibility.canApply(target,ue,plugin.relics())) {
            player.sendMessage("§cอุปกรณ์นี้มีมนตราที่ใช้ร่วมกับ " + ue.title() + " ไม่ได้");
            return;
        }

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

    private static boolean usableLimitBreak(VoidscapePlugin plugin, ItemStack scroll,
                                           LimitBreakType type, ItemStack target) {
        if (!isEquipment(target) || target.getAmount() != 1 || scroll == null || scroll.getAmount() <= 0
                || plugin.relics().getLimitBreakType(scroll) != type || !type.category().matches(target.getType())) return false;
        int current = plugin.relics().getLimitBreakUpgradeLevel(target, type);
        return current > 0 && current < plugin.relics().getLimitBreakCap(scroll);
    }

    private static int countUsableLimitBreak(VoidscapePlugin plugin, Player player,
                                            LimitBreakType type, ItemStack target) {
        int count = 0;
        for (ItemStack it : player.getInventory().getContents()) {
            if (usableLimitBreak(plugin, it, type, target)) count += it.getAmount();
        }
        return count;
    }

    private static int findLimitBreakSlot(VoidscapePlugin plugin, Player player,
                                         LimitBreakType type, ItemStack target) {
        int selected = -1, lowestCap = Integer.MAX_VALUE;
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack scroll = contents[slot];
            if (!usableLimitBreak(plugin, scroll, type, target)) continue;
            int cap = plugin.relics().getLimitBreakCap(scroll);
            if (cap < lowestCap) { selected = slot; lowestCap = cap; }
        }
        return selected;
    }

    private static int countUnique(VoidscapePlugin plugin, Player player, UniqueEnchant ue) {
        int count = 0;
        for (ItemStack it : player.getInventory().getContents()) {
            if (it != null && plugin.relics().getUniqueEnchant(it) == ue) count += it.getAmount();
        }
        return count;
    }

}
