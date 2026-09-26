package com.example.voidscape.gui;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.command.VoidCommand;
import com.example.voidscape.item.RelicService;
import net.kyori.adventure.text.Component;
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

public final class WandShowcaseGui implements InventoryHolder, Listener {
    private final VoidscapePlugin plugin;
    public WandShowcaseGui(VoidscapePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }

    private static Component parseLegacy(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text).decoration(TextDecoration.ITALIC, false);
    }

    private boolean isAdmin(Player p) {
        return p.isOp() || p.hasPermission("evergarden.admin") || p.hasPermission("voidscape.admin");
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(this, 54, parseLegacy("§5§lAdvance Magic §8✦ §f15 Wands & Ancient Cores"));
        List<RelicService.MagicCore> cores = RelicService.MAGIC_CORES;

        // Row 0 (0-8): First 9 Wands
        for (int i = 0; i < 9 && i < cores.size(); i++) {
            inv.setItem(i, createWandDisplayItem(cores.get(i)));
        }

        // Row 1 (9-17): Next 6 Wands + Actions
        for (int i = 0; i < 6 && (i + 9) < cores.size(); i++) {
            inv.setItem(9 + i, createWandDisplayItem(cores.get(9 + i)));
        }
        inv.setItem(15, createSeparator(Material.PURPLE_STAINED_GLASS_PANE, "§5✦ คทาเวทมนตร์ 15 สาย"));
        inv.setItem(16, createAction(Material.NETHER_STAR, "§d§l🪄 รับคทาครบ 15 เล่ม", List.of("§7คลิกเพื่อรับ Magic Wand ครบทั้ง 15 เล่ม", "§7ลงในกระเป๋าทันที")));
        inv.setItem(17, createSeparator(Material.PURPLE_STAINED_GLASS_PANE, "§5✦"));

        // Row 2 (18-26): First 9 Cores
        for (int i = 0; i < 9 && i < cores.size(); i++) {
            inv.setItem(18 + i, createCoreDisplayItem(cores.get(i)));
        }

        // Row 3 (27-35): Next 6 Cores + Actions
        for (int i = 0; i < 6 && (i + 9) < cores.size(); i++) {
            inv.setItem(27 + i, createCoreDisplayItem(cores.get(9 + i)));
        }
        inv.setItem(33, createSeparator(Material.MAGENTA_STAINED_GLASS_PANE, "§d✦ แกนเวทมนตร์ 15 ธาตุ"));
        inv.setItem(34, createAction(Material.HEART_OF_THE_SEA, "§6§l🔮 รับแกนครบ 15 ชิ้น (x1)", List.of("§7คลิกเพื่อรับ Magic Core ครบทุกธาตุ", "§7ธาตุละ 1 ชิ้นลงในกระเป๋า")));
        inv.setItem(35, createAction(Material.ENDER_EYE, "§e§l🔮 รับแกนครบ 15 ชิ้น (x16)", List.of("§7คลิกเพื่อรับ Magic Core ครบทุกธาตุ", "§7ธาตุละ 16 ชิ้นลงในกระเป๋า")));

        // Row 4 (36-44): Info Banner & Separator
        for (int i = 36; i <= 44; i++) {
            inv.setItem(i, createSeparator(Material.BLACK_STAINED_GLASS_PANE, "§8✦"));
        }
        inv.setItem(40, createAction(Material.BOOK, "§b§l📖 คู่มือการเสกคทา & แกนเวทมนตร์", List.of(
            "§7• ด้านบน (แถว 0-1): คทาเวทมนตร์ 15 สาย",
            "§7  - §eคลิกซ้าย: §fรับคทา 1 เล่ม",
            "§7  - §6คลิกขวา: §fรับแกนเวทมนตร์ (Core) ของคทานั้น",
            "§7• ด้านล่าง (แถว 2-3): แกนเวทมนตร์ 15 ธาตุ",
            "§7  - §eคลิกซ้าย: §fรับแกน x1",
            "§7  - §6คลิกขวา: §fรับแกน x16",
            "§7  - §bShift+คลิก: §fรับคทาเวทมนตร์ของแกนนี้"
        )));

        // Row 5 (45-53): Cross-Navigation & Controls
        inv.setItem(45, createAction(Material.WHEAT, "§a§l🌿 คลังพืชผล Evergarden (30 Crops)", List.of("§7คลิกเพื่อสลับไปยังหน้าต่างคลังพืชผลและอาหาร 30 ชนิด")));
        inv.setItem(46, createAction(Material.NETHERITE_PICKAXE, "§b§l⚔️ คลังยุทธภัณฑ์ & วัตถุดิบ (Relics)", List.of("§7คลิกเพื่อสลับไปยังคลัง Relics, คัมภีร์, และวัตถุดิบ")));
        inv.setItem(47, createAction(Material.COMMAND_BLOCK, "§6§l⚙️ แผงควบคุมระบบ (Admin Test Kit)", List.of("§7คลิกเพื่อสลับไปยังแผงควบคุมระบบ /evergarden test")));
        inv.setItem(48, createAction(Material.CARROT_ON_A_STICK, "§d§l🪄 รับคทาครบ 15 เล่ม", List.of("§7รับคทาเวทมนตร์ครบทั้ง 15 เล่มทันที")));
        inv.setItem(49, createAction(Material.HEART_OF_THE_SEA, "§e§l🔮 รับแกน x16 ครบ 15 ธาตุ", List.of("§7รับแกนเวทมนตร์ครบทั้ง 15 ธาตุ กองละ 16 ชิ้น")));
        inv.setItem(50, plugin.testGui().createFlyingStaffMenuItem());
        inv.setItem(51, createSeparator(Material.BLACK_STAINED_GLASS_PANE, "§8✦"));
        inv.setItem(52, createSeparator(Material.BLACK_STAINED_GLASS_PANE, "§8✦"));
        inv.setItem(53, createAction(Material.BARRIER, "§c§l❌ ปิดเมนู (Close)", List.of("§7คลิกเพื่อปิดหน้าต่างนี้")));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.8f, 1.2f);
    }

    private ItemStack createWandDisplayItem(RelicService.MagicCore core) {
        ItemStack wand = VoidCommand.createWandViaAdvanceMagic(core.id());
        if (wand == null) {
            wand = new ItemStack(Material.CARROT_ON_A_STICK);
            ItemMeta m = wand.getItemMeta();
            if (m != null) {
                m.displayName(parseLegacy("§d✦ " + core.wandTitle() + " Wand"));
                wand.setItemMeta(m);
            }
        }
        ItemStack item = wand.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>(meta.lore() != null ? meta.lore() : List.of());
            lore.add(Component.text(""));
            lore.add(parseLegacy("§e🖱️ คลิกซ้าย: §fรับคทาเล่มนี้ 1 ด้าม"));
            lore.add(parseLegacy("§6🖱️ คลิกขวา / Shift+คลิก: §fรับแกนเวทมนตร์ (" + core.title() + ")"));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createCoreDisplayItem(RelicService.MagicCore core) {
        ItemStack rawCore = plugin.relics().createMagicCore(core);
        ItemStack item = rawCore.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>(meta.lore() != null ? meta.lore() : List.of());
            lore.add(Component.text(""));
            lore.add(parseLegacy("§e🖱️ คลิกซ้าย: §fรับแกนเวทมนตร์ x1"));
            lore.add(parseLegacy("§6🖱️ คลิกขวา: §fรับแกนเวทมนตร์ x16"));
            lore.add(parseLegacy("§b🖱️ Shift+คลิก: §fรับคทาเวทมนตร์ (" + core.wandTitle() + ") 1 ด้าม"));
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
        if (!(e.getInventory().getHolder() instanceof WandShowcaseGui)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;

        int slot = e.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        if (slot == 53) {
            p.closeInventory();
            return;
        }

        if (!isAdmin(p)) {
            p.sendMessage(ChatColor.RED + "✦ เมนูนี้เปิดสำหรับดูข้อมูลคทา (ต้องมีสิทธิ์แอดมินจึงจะสามารถเสกไอเท็มได้)");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        List<RelicService.MagicCore> cores = RelicService.MAGIC_CORES;

        // 1. Wand Slots (Row 0: 0-8, Row 1: 9-14)
        int wandIndex = -1;
        if (slot >= 0 && slot <= 8) wandIndex = slot;
        else if (slot >= 9 && slot <= 14) wandIndex = slot;

        if (wandIndex >= 0 && wandIndex < cores.size()) {
            RelicService.MagicCore core = cores.get(wandIndex);
            if (e.isRightClick() || e.isShiftClick()) {
                ItemStack coreItem = plugin.relics().createMagicCore(core);
                giveItem(p, coreItem);
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                p.sendMessage(ChatColor.GREEN + "✦ ได้รับ " + core.title() + " x1");
            } else {
                ItemStack wand = VoidCommand.createWandViaAdvanceMagic(core.id());
                if (wand != null) {
                    giveItem(p, wand);
                    p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                    p.sendMessage(ChatColor.LIGHT_PURPLE + "✦ ได้รับ คทา " + core.wandTitle() + "!");
                }
            }
            return;
        }

        // 2. Core Slots (Row 2: 18-26, Row 3: 27-32)
        int coreIndex = -1;
        if (slot >= 18 && slot <= 26) coreIndex = slot - 18;
        else if (slot >= 27 && slot <= 32) coreIndex = (slot - 27) + 9;

        if (coreIndex >= 0 && coreIndex < cores.size()) {
            RelicService.MagicCore core = cores.get(coreIndex);
            if (e.isShiftClick()) {
                ItemStack wand = VoidCommand.createWandViaAdvanceMagic(core.id());
                if (wand != null) {
                    giveItem(p, wand);
                    p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                    p.sendMessage(ChatColor.LIGHT_PURPLE + "✦ ได้รับ คทา " + core.wandTitle() + "!");
                }
            } else if (e.isRightClick()) {
                ItemStack stack = plugin.relics().createMagicCore(core);
                stack.setAmount(16);
                giveItem(p, stack);
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                p.sendMessage(ChatColor.GREEN + "✦ ได้รับ " + core.title() + " x16");
            } else {
                ItemStack single = plugin.relics().createMagicCore(core);
                giveItem(p, single);
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
                p.sendMessage(ChatColor.GREEN + "✦ ได้รับ " + core.title() + " x1");
            }
            return;
        }

        // 3. Action & Navigation Slots
        switch (slot) {
            case 16, 48 -> { // All 15 wands
                for (RelicService.MagicCore c : cores) {
                    ItemStack wand = VoidCommand.createWandViaAdvanceMagic(c.id());
                    if (wand != null) giveItem(p, wand);
                }
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
                p.sendMessage(ChatColor.LIGHT_PURPLE + "✦ ได้รับ Magic Wands ครบทั้ง 15 เล่มเรียบร้อยแล้ว!");
            }
            case 34 -> { // All 15 cores x1
                for (RelicService.MagicCore c : cores) {
                    giveItem(p, plugin.relics().createMagicCore(c));
                }
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.9f, 1.4f);
                p.sendMessage(ChatColor.GOLD + "✦ ได้รับ Magic Cores ครบทุกธาตุ 15 ชิ้นเรียบร้อยแล้ว!");
            }
            case 35, 49 -> { // All 15 cores x16
                for (RelicService.MagicCore c : cores) {
                    ItemStack st = plugin.relics().createMagicCore(c);
                    st.setAmount(16);
                    giveItem(p, st);
                }
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.4f);
                p.sendMessage(ChatColor.GOLD + "✦ ได้รับ Magic Cores ครบทุกธาตุ กองละ 16 ชิ้นเรียบร้อยแล้ว!");
            }
            case 45 -> plugin.cropGui().open(p);
            case 46 -> plugin.relicGui().open(p);
            case 47 -> plugin.testGui().open(p);
            case 50 -> {
                ItemStack staff = plugin.testGui().createFlyingStaff();
                if (staff == null) {
                    p.sendMessage(ChatColor.RED + "ต้องเปิดใช้งาน Advance Magic ก่อนจึงจะรับไม้เท้าบินได้");
                    return;
                }
                giveItem(p, staff);
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.9f, 1.2f);
                p.sendMessage(ChatColor.AQUA + "✦ ได้รับไม้เท้าบินแล้ว: คลิกขวาเรียก แล้วคลิกที่ไม้เท้าเพื่อขึ้นขี่");
            }
        }
    }

    private void giveItem(Player p, ItemStack item) {
        if (item == null) return;
        var leftovers = p.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            for (ItemStack drop : leftovers.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), drop);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof WandShowcaseGui) {
            e.setCancelled(true);
        }
    }
}
