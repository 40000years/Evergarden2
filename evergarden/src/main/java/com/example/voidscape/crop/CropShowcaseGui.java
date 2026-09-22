package com.example.voidscape.crop;

import com.example.voidscape.VoidscapePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class CropShowcaseGui implements InventoryHolder, Listener {
    private final VoidscapePlugin plugin;
    private final CropService cropService;
    private Inventory inventory;

    public CropShowcaseGui(VoidscapePlugin plugin, CropService cropService) {
        this.plugin = plugin;
        this.cropService = cropService;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private static Component parseLegacy(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text).decoration(TextDecoration.ITALIC, false);
    }

    private boolean isAdmin(Player p) {
        return p.isOp() || p.hasPermission("evergarden.admin") || p.hasPermission("voidscape.admin");
    }

    public void open(Player player) {
        inventory = Bukkit.createInventory(this, 54, parseLegacy("§2§lEvergarden §8✦ §f30 Custom Magic Crops"));
        Inventory inv = inventory;
        CropType[] all = CropType.values();

        // Row 0: Tier 1 (0-5)
        for (int i = 0; i < 6 && i < all.length; i++) {
            inv.setItem(i, createDisplayItem(all[i]));
        }
        inv.setItem(6, createSeparator(Material.LIME_STAINED_GLASS_PANE, "§aTier I · เกษตรกรรมพื้นฐาน"));
        inv.setItem(7, createAction(Material.WHEAT_SEEDS, "§a§l🌱 รับเมล็ดพันธุ์ Tier 1 (x16)", List.of("§7รับเมล็ดพันธุ์ Tier 1 ครบทั้ง 6 ชนิด")));
        inv.setItem(8, createAction(Material.APPLE, "§2§l🍎 รับผลผลิต Tier 1 (x16)", List.of("§7รับผลผลิตอาหาร Tier 1 ครบทั้ง 6 ชนิด")));

        // Row 1: Tier 2 (9-14)
        for (int i = 0; i < 6 && (i + 6) < all.length; i++) {
            inv.setItem(9 + i, createDisplayItem(all[6 + i]));
        }
        inv.setItem(15, createSeparator(Material.CYAN_STAINED_GLASS_PANE, "§3Tier II · การต่อสู้ & ล่ามอน"));
        inv.setItem(16, createAction(Material.BEETROOT_SEEDS, "§3§l🌱 รับเมล็ดพันธุ์ Tier 2 (x16)", List.of("§7รับเมล็ดพันธุ์ Tier 2 ครบทั้ง 6 ชนิด")));
        inv.setItem(17, createAction(Material.GOLDEN_CARROT, "§b§l🍎 รับผลผลิต Tier 2 (x16)", List.of("§7รับผลผลิตอาหาร Tier 2 ครบทั้ง 6 ชนิด")));

        // Row 2: Tier 3 (18-23)
        for (int i = 0; i < 6 && (i + 12) < all.length; i++) {
            inv.setItem(18 + i, createDisplayItem(all[12 + i]));
        }
        inv.setItem(24, createSeparator(Material.PURPLE_STAINED_GLASS_PANE, "§5Tier III · มิติเอาชีวิตรอด"));
        inv.setItem(25, createAction(Material.TORCHFLOWER_SEEDS, "§5§l🌱 รับเมล็ดพันธุ์ Tier 3 (x16)", List.of("§7รับเมล็ดพันธุ์ Tier 3 ครบทั้ง 6 ชนิด")));
        inv.setItem(26, createAction(Material.DRIED_KELP, "§d§l🍎 รับผลผลิต Tier 3 (x16)", List.of("§7รับผลผลิตอาหาร Tier 3 ครบทั้ง 6 ชนิด")));

        // Row 3: Tier 4 (27-32)
        for (int i = 0; i < 6 && (i + 18) < all.length; i++) {
            inv.setItem(27 + i, createDisplayItem(all[18 + i]));
        }
        inv.setItem(33, createSeparator(Material.ORANGE_STAINED_GLASS_PANE, "§6Tier IV · เหมืองแร่ & กาลเวลา"));
        inv.setItem(34, createAction(Material.PITCHER_POD, "§6§l🌱 รับเมล็ดพันธุ์ Tier 4 (x16)", List.of("§7รับเมล็ดพันธุ์ Tier 4 ครบทั้ง 6 ชนิด")));
        inv.setItem(35, createAction(Material.GOLDEN_APPLE, "§e§l🍎 รับผลผลิต Tier 4 (x16)", List.of("§7รับผลผลิตอาหาร Tier 4 ครบทั้ง 6 ชนิด")));

        // Row 4: Tier 5 (36-41)
        for (int i = 0; i < 6 && (i + 24) < all.length; i++) {
            inv.setItem(36 + i, createDisplayItem(all[24 + i]));
        }
        inv.setItem(42, createSeparator(Material.MAGENTA_STAINED_GLASS_PANE, "§dTier V · เวทมนตร์บรรพกาล [MYTHIC]"));
        inv.setItem(43, createAction(Material.NETHER_STAR, "§d§l🌱 รับเมล็ดพันธุ์ Tier 5 [MYTHIC] (x16)", List.of("§7รับเมล็ดพันธุ์ Tier 5 ครบทั้ง 6 ชนิด")));
        inv.setItem(44, createAction(Material.ENCHANTED_GOLDEN_APPLE, "§5§l🍎 รับผลผลิต Tier 5 [MYTHIC] (x16)", List.of("§7รับผลผลิตอาหาร Tier 5 ครบทั้ง 6 ชนิด")));

        // Row 5: Actions (45-53)
        inv.setItem(45, createAction(Material.CHEST, "§a§l📦 รับเมล็ดครบ 30 ชนิด (x16)", List.of("§7คลิกเพื่อรับเมล็ดพันธุ์ทั้งหมด 30 สายพันธุ์ (Admin)")));
        inv.setItem(46, createAction(Material.ENDER_CHEST, "§e§l🍱 รับอาหารครบ 30 ชนิด (x16)", List.of("§7คลิกเพื่อรับผลผลิตอาหารบัฟทั้งหมด 30 ชนิด (Admin)")));
        inv.setItem(47, createSeparator(Material.BLACK_STAINED_GLASS_PANE, "§8✦"));
        inv.setItem(48, createAction(Material.BOOK, "§b§l📖 คู่มือการเกษตร Evergarden", List.of(
            "§7• ปลูกบนแปลงดินพรวน (Farmland)",
            "§7• ปิดการใช้งาน Bone Meal (พืชเติบโตตามเวลาจริง)",
            "§7• ใช้จอบเก็บเกี่ยวเพื่อรับโบนัส Fortune (โชคลาภ)",
            "§7• Tier 1: 3 นาที · โตไว ดูแลง่าย",
            "§7• Tier 2: 5 นาที · บัฟการต่อสู้ & ดูดเลือด",
            "§7• Tier 3: 7.5 นาที · คุ้มครองการตาย & มิติ",
            "§7• Tier 4: 10 นาที · แร่, ตัดไม้, -40% Cooldown",
            "§7• Tier 5: 15 นาที · เพิ่ม Max Mana ถาวร (300 Max)!",
            "§7• คลิกซ้าย: รับเมล็ดพันธุ์ x16",
            "§7• คลิกขวา หรือ Shift+คลิก: รับผลผลิตอาหาร x16"
        )));
        inv.setItem(49, createAction(Material.BLAZE_ROD, "§d§l✨ คลังคทาเวทมนตร์ (Wands)", List.of("§7เปิดคลังคทาและแกนคทา 15 ชนิด", "§e🖱️ คลิกเพื่อสลับหน้าต่าง")));
        inv.setItem(50, createAction(Material.NETHER_STAR, "§6§l🏆 คลังเรลิก & ไอเท็มพิเศษ (Relics)", List.of("§7เปิดคลังวัตถุโบราณและไอเท็มพระเจ้า", "§e🖱️ คลิกเพื่อสลับหน้าต่าง")));
        inv.setItem(51, createAction(Material.COMPASS, "§c§l🛠️ แผงควบคุมแอดมิน (Test Menu)", List.of("§7เปิดเมนูทดสอบระบบ Evergarden", "§e🖱️ คลิกเพื่อเปิด")));
        inv.setItem(52, createSeparator(Material.BLACK_STAINED_GLASS_PANE, "§8✦"));
        inv.setItem(53, createAction(Material.BARRIER, "§c§l❌ ปิดเมนู (Close)", List.of("§7คลิกเพื่อปิดหน้าต่างนี้")));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.8f, 1.2f);
    }

    private ItemStack createDisplayItem(CropType crop) {
        ItemStack item = cropService.factory().createFood(crop, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>(meta.lore() != null ? meta.lore() : List.of());
            lore.add(Component.text(""));
            lore.add(parseLegacy("§e🖱️ คลิกซ้าย: §fรับเมล็ดพันธุ์ (Seed) x16"));
            lore.add(parseLegacy("§6🖱️ คลิกขวา / Shift+คลิก: §fรับผลผลิตอาหาร (Food) x16"));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createAction(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(parseLegacy(name));
            List<Component> list = new ArrayList<>();
            for (String l : lore) list.add(parseLegacy(l));
            meta.lore(list);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSeparator(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(parseLegacy(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof CropShowcaseGui)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;

        int slot = e.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        if (slot == 53) {
            p.closeInventory();
            return;
        }

        if (slot == 49) {
            if (plugin.wandGui() != null) plugin.wandGui().open(p);
            return;
        }
        if (slot == 50) {
            if (plugin.relicGui() != null) plugin.relicGui().open(p);
            return;
        }
        if (slot == 51) {
            if (isAdmin(p)) {
                if (plugin.testGui() != null) plugin.testGui().open(p);
            } else {
                p.sendMessage(ChatColor.RED + "✦ เมนูนี้สำหรับแอดมินเท่านั้น!");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            }
            return;
        }

        if (!isAdmin(p)) {
            p.sendMessage(ChatColor.RED + "✦ เมนูนี้เปิดสำหรับดูข้อมูลพืช (ต้องมีสิทธิ์แอดมินจึงจะสามารถเบิกไอเท็มได้)");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        CropType[] all = CropType.values();
        int amount = 16;

        // Individual Crop Slots
        int cropIndex = -1;
        if (slot >= 0 && slot <= 5) cropIndex = slot;
        else if (slot >= 9 && slot <= 14) cropIndex = 6 + (slot - 9);
        else if (slot >= 18 && slot <= 23) cropIndex = 12 + (slot - 18);
        else if (slot >= 27 && slot <= 32) cropIndex = 18 + (slot - 27);
        else if (slot >= 36 && slot <= 41) cropIndex = 24 + (slot - 36);

        if (cropIndex >= 0 && cropIndex < all.length) {
            CropType crop = all[cropIndex];
            ItemStack toGive;
            if (e.isRightClick() || e.isShiftClick()) {
                toGive = cropService.factory().createFood(crop, amount);
                p.sendMessage(ChatColor.GREEN + "✦ ได้รับ " + crop.thaiName + " (Food) x" + amount);
            } else {
                toGive = cropService.factory().createSeed(crop, amount);
                p.sendMessage(ChatColor.GREEN + "✦ ได้รับ เมล็ด" + crop.thaiName + " (Seed) x" + amount);
            }
            giveItem(p, toGive);
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
            return;
        }

        // Action Buttons
        switch (slot) {
            case 7 -> giveTierSeeds(p, 0, 6, 16);
            case 8 -> giveTierFoods(p, 0, 6, 16);
            case 16 -> giveTierSeeds(p, 6, 12, 16);
            case 17 -> giveTierFoods(p, 6, 12, 16);
            case 25 -> giveTierSeeds(p, 12, 18, 16);
            case 26 -> giveTierFoods(p, 12, 18, 16);
            case 34 -> giveTierSeeds(p, 18, 24, 16);
            case 35 -> giveTierFoods(p, 18, 24, 16);
            case 43 -> giveTierSeeds(p, 24, 30, 16);
            case 44 -> giveTierFoods(p, 24, 30, 16);
            case 45 -> giveTierSeeds(p, 0, 30, 16);
            case 46 -> giveTierFoods(p, 0, 30, 16);
        }
    }

    private void giveTierSeeds(Player p, int start, int end, int count) {
        CropType[] all = CropType.values();
        for (int i = start; i < end && i < all.length; i++) {
            giveItem(p, cropService.factory().createSeed(all[i], count));
        }
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.9f, 1.4f);
        p.sendMessage(ChatColor.GREEN + "✦ ได้รับเมล็ดพันธุ์ " + (end - start) + " ชนิดเรียบร้อยแล้ว!");
    }

    private void giveTierFoods(Player p, int start, int end, int count) {
        CropType[] all = CropType.values();
        for (int i = start; i < end && i < all.length; i++) {
            giveItem(p, cropService.factory().createFood(all[i], count));
        }
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.9f, 1.4f);
        p.sendMessage(ChatColor.GREEN + "✦ ได้รับผลผลิตอาหาร " + (end - start) + " ชนิดเรียบร้อยแล้ว!");
    }

    private void giveItem(Player p, ItemStack item) {
        var leftovers = p.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            for (ItemStack drop : leftovers.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), drop);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof CropShowcaseGui) {
            e.setCancelled(true);
        }
    }
}
