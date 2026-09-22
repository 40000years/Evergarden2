package com.example.voidscape.gui;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.enchant.LimitBreakType;
import com.example.voidscape.enchant.UniqueEnchant;
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

public final class RelicShowcaseGui implements InventoryHolder, Listener {
    private final VoidscapePlugin plugin;
    public RelicShowcaseGui(VoidscapePlugin plugin) {
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
        Inventory inv = Bukkit.createInventory(this, 54, parseLegacy("§9§lEvergarden §8✦ §fRelics, Scrolls & Materials"));

        // ==========================================
        // Row 0 (0-8): 6 Special Relics + Key + Shards + Eternity
        // ==========================================
        inv.setItem(0, wrapDisplay(plugin.relics().create(RelicService.Relic.SMELTER_PICKAXE, 1), "§eคลิก: รับ Smelter Pickaxe 1 เล่ม"));
        inv.setItem(1, wrapDisplay(plugin.relics().create(RelicService.Relic.RIFT_PICKAXE, 1), "§eคลิก: รับ Rift Pickaxe (3×3) 1 เล่ม"));
        inv.setItem(2, wrapDisplay(plugin.relics().create(RelicService.Relic.RIFT_BLADE, 1), "§eคลิก: รับ Rift Blade 1 เล่ม"));
        inv.setItem(3, wrapDisplay(plugin.relics().create(RelicService.Relic.ETERNAL_AEGIS, 1), "§eคลิก: รับ Eternal Aegis 1 ชิ้น"));
        inv.setItem(4, wrapDisplay(plugin.relics().create(RelicService.Relic.STORM_BOW, 1), "§eคลิก: รับ Storm Bow 1 คัน"));
        inv.setItem(5, wrapDisplay(plugin.relics().create(RelicService.Relic.NOVA_BOW, 1), "§eคลิก: รับ Nova Bow 1 คัน"));
        inv.setItem(6, wrapDisplay(plugin.relics().createVoidKey(), "§eคลิกซ้าย: รับกุญแจ x1 | §6คลิกขวา: รับกุญแจ x4"));
        inv.setItem(7, wrapDisplay(plugin.relics().createKeyShard(4), "§eคลิกซ้าย: รับเศษกุญแจ x4 | §6คลิกขวา: รับเศษกุญแจ x16"));
        inv.setItem(8, wrapDisplay(plugin.relics().createScrollEternity(), "§eคลิกซ้าย: รับคัมภีร์ x1 | §6คลิกขวา: รับคัมภีร์ x5"));

        // ==========================================
        // Row 1 (9-17): Materials, Guide & God Weapons + 1st Unique
        // ==========================================
        inv.setItem(9, wrapDisplay(plugin.relics().createAstralDust(16), "§eคลิกซ้าย: รับผงละอองดาว x16 | §6คลิกขวา: รับ x64"));
        inv.setItem(10, wrapDisplay(plugin.relics().createRepairStone(1), "§eคลิกซ้าย: รับศิลาซ่อม x1 | §6คลิกขวา: รับ x8"));
        inv.setItem(11, wrapDisplay(plugin.relics().createVoidElixir(1), "§eคลิกซ้าย: รับน้ำยา x1 | §6คลิกขวา: รับ x4"));
        inv.setItem(12, createAction(Material.BOOK, "§a§l📚 ชุดคู่มือมิติ Evergarden (3 เล่ม)", List.of(
            "§7ประกอบด้วย: เล่ม 1 พืช, เล่ม 2 ยุทธภัณฑ์, เล่ม 3 Advance Magic",
            "§eคลิกซ้าย: §aเปิดเมนูเลือกอ่านคู่มือ",
            "§6คลิกขวา / Shift: §7รับชุดคู่มือครบทั้ง 3 เล่มลงกระเป๋า"
        )));
        inv.setItem(13, wrapDisplay(AdminTestGui.createGodSword(), "§eคลิก: รับ Dev God Sword (Sharpness VIII + Vampiric)"));
        inv.setItem(14, wrapDisplay(AdminTestGui.createGodBow(), "§eคลิก: รับ Dev God Bow (Power VIII + Ricochet)"));
        inv.setItem(15, wrapDisplay(AdminTestGui.createGodPickaxe(), "§eคลิก: รับ Dev God Pickaxe (Efficiency VIII + 3×3 + Vein)"));
        inv.setItem(16, createAction(Material.NETHERITE_CHESTPLATE, "§6§l🛡️ Full Set เกราะเทพ 4 ชิ้น", List.of(
            "§7เซ็ตเกราะ Protection VIII + Phoenix + Titan + Shadow Step",
            "§eคลิกเพื่อรับเกราะครบทั้ง 4 ชิ้น (หมวก, เสื้อ, กางเกง, รองเท้า)"
        )));
        inv.setItem(17, wrapDisplay(plugin.relics().createScrollUnique(UniqueEnchant.COLOSSUS_SLAYER), "§eคลิก: รับคัมภีร์ x1 | §6คลิกขวา: รับ x5"));

        // ==========================================
        // Row 2 (18-26): 6 Limit Breaks + 3 Unique Enchants
        // ==========================================
        inv.setItem(18, wrapDisplay(plugin.relics().createScrollLimitBreak(LimitBreakType.SHARPNESS), "§eคลิกซ้าย: รับ x1 | §6คลิกขวา: รับ x5"));
        inv.setItem(19, wrapDisplay(plugin.relics().createScrollLimitBreak(LimitBreakType.PROTECTION), "§eคลิกซ้าย: รับ x1 | §6คลิกขวา: รับ x5"));
        inv.setItem(20, wrapDisplay(plugin.relics().createScrollLimitBreak(LimitBreakType.POWER), "§eคลิกซ้าย: รับ x1 | §6คลิกขวา: รับ x5"));
        inv.setItem(21, wrapDisplay(plugin.relics().createScrollLimitBreak(LimitBreakType.EFFICIENCY), "§eคลิกซ้าย: รับ x1 | §6คลิกขวา: รับ x5"));
        inv.setItem(22, wrapDisplay(plugin.relics().createScrollLimitBreak(LimitBreakType.FORTUNE), "§eคลิกซ้าย: รับ x1 | §6คลิกขวา: รับ x5"));
        inv.setItem(23, wrapDisplay(plugin.relics().createScrollLimitBreak(LimitBreakType.LOOTING), "§eคลิกซ้าย: รับ x1 | §6คลิกขวา: รับ x5"));
        inv.setItem(24, wrapDisplay(plugin.relics().createScrollUnique(UniqueEnchant.RICOCHET), "§eคลิก: รับคัมภีร์ x1 | §6คลิกขวา: รับ x5"));
        inv.setItem(25, wrapDisplay(plugin.relics().createScrollUnique(UniqueEnchant.KINETIC_GRAPPLE), "§eคลิก: รับคัมภีร์ x1 | §6คลิกขวา: รับ x5"));
        inv.setItem(26, wrapDisplay(plugin.relics().createScrollUnique(UniqueEnchant.ABSOLUTE_ZERO), "§eคลิก: รับคัมภีร์ x1 | §6คลิกขวา: รับ x5"));

        // ==========================================
        // Row 3 (27-35): 9 Unique Enchants
        // ==========================================
        inv.setItem(27, wrapDisplay(plugin.relics().createScrollUnique(UniqueEnchant.SINGULARITY), "§eคลิก: รับคัมภีร์ x1 | §6คลิกขวา: รับ x5"));
        inv.setItem(28, wrapDisplay(plugin.relics().createScrollUnique(UniqueEnchant.METEOR_ARROW), "§eคลิก: รับคัมภีร์ x1 | §6คลิกขวา: รับ x5"));
        inv.setItem(29, wrapDisplay(plugin.relics().createScrollUnique(UniqueEnchant.SEISMIC_SLAM), "§eคลิก: รับคัมภีร์ x1 | §6คลิกขวา: รับ x5"));
        inv.setItem(30, wrapDisplay(plugin.relics().createScrollUnique(UniqueEnchant.VEIN_SMELTER), "§eคลิก: รับคัมภีร์ x1 | §6คลิกขวา: รับ x5"));
        inv.setItem(31, wrapDisplay(plugin.relics().createScrollUnique(UniqueEnchant.ADVANCE_TOOL), "§eคลิก: รับคัมภีร์ x1 | §6คลิกขวา: รับ x5"));
        inv.setItem(32, wrapDisplay(plugin.relics().createScrollUnique(UniqueEnchant.DEMETER_SCYTHE), "§eคลิก: รับคัมภีร์ x1 | §6คลิกขวา: รับ x5"));
        inv.setItem(33, wrapDisplay(plugin.relics().createScrollUnique(UniqueEnchant.TITAN_BREACH), "§eคลิก: รับคัมภีร์ x1 | §6คลิกขวา: รับ x5"));
        inv.setItem(34, wrapDisplay(plugin.relics().createScrollUnique(UniqueEnchant.TELEPATHY), "§eคลิก: รับคัมภีร์ x1 | §6คลิกขวา: รับ x5"));
        inv.setItem(35, wrapDisplay(plugin.relics().createScrollUnique(UniqueEnchant.GUILLOTINE), "§eคลิก: รับคัมภีร์ x1 | §6คลิกขวา: รับ x5"));

        // ==========================================
        // Row 4 (36-44): Remaining 9 Unique Enchants
        // ==========================================
        inv.setItem(36, wrapDisplay(plugin.relics().createScrollUnique(UniqueEnchant.ECHO_STRIKE), "§eคลิก: รับคัมภีร์ x1 | §6คลิกขวา: รับ x5"));
        inv.setItem(37, wrapDisplay(plugin.relics().createScrollUnique(UniqueEnchant.BLADE_VORTEX), "§eคลิก: รับคัมภีร์ x1 | §6คลิกขวา: รับ x5"));
        inv.setItem(38, wrapDisplay(plugin.relics().createScrollUnique(UniqueEnchant.SOUL_HARVEST), "§eคลิก: รับคัมภีร์ x1 | §6คลิกขวา: รับ x5"));
        inv.setItem(39, wrapDisplay(plugin.relics().createScrollUnique(UniqueEnchant.THUNDERLORD), "§eคลิก: รับคัมภีร์ x1 | §6คลิกขวา: รับ x5"));
        inv.setItem(40, wrapDisplay(plugin.relics().createScrollUnique(UniqueEnchant.VAMPIRIC), "§eคลิก: รับคัมภีร์ x1 | §6คลิกขวา: รับ x5"));
        inv.setItem(41, wrapDisplay(plugin.relics().createScrollUnique(UniqueEnchant.PHOENIX_REBIRTH), "§eคลิก: รับคัมภีร์ x1 | §6คลิกขวา: รับ x5"));
        inv.setItem(42, wrapDisplay(plugin.relics().createScrollUnique(UniqueEnchant.SHADOW_STEP), "§eคลิก: รับคัมภีร์ x1 | §6คลิกขวา: รับ x5"));
        inv.setItem(43, wrapDisplay(plugin.relics().createScrollUnique(UniqueEnchant.TITAN_STANCE), "§eคลิก: รับคัมภีร์ x1 | §6คลิกขวา: รับ x5"));
        inv.setItem(44, wrapDisplay(plugin.relics().createScrollUnique(UniqueEnchant.SOULBOUND), "§eคลิก: รับคัมภีร์ x1 | §6คลิกขวา: รับ x5"));

        // ==========================================
        // Row 5 (45-53): Cross-Navigation & Bulk Grants
        // ==========================================
        inv.setItem(45, createAction(Material.CARROT_ON_A_STICK, "§5§l🪄 คลังคทา & แกนเวทมนตร์ (Wands)", List.of("§7คลิกเพื่อสลับไปยังคลังคทา 15 เล่มและแกน 15 ธาตุ")));
        inv.setItem(46, createAction(Material.WHEAT, "§a§l🌿 คลังพืชผล Evergarden (30 Crops)", List.of("§7คลิกเพื่อสลับไปยังคลังพืชผลและอาหาร 30 ชนิด")));
        inv.setItem(47, createAction(Material.COMMAND_BLOCK, "§6§l⚙️ แผงควบคุมระบบ (Admin Test Kit)", List.of("§7คลิกเพื่อสลับไปยังหน้าต่างควบคุมระบบหลัก")));
        inv.setItem(48, createAction(Material.NETHERITE_PICKAXE, "§e§l📦 รับยุทธภัณฑ์ 6 ชิ้นครบชุด", List.of("§7รับ Smelter, Rift, Blade, Aegis, Storm, Nova ลงกระเป๋าทันที")));
        inv.setItem(49, createAction(Material.CHEST, "§b§l📦 รับ Limit Break x5 ทุกชนิด", List.of("§7รับคัมภีร์ Limit Break ทั้ง 6 สาย สายละ 5 เล่ม")));
        inv.setItem(50, createAction(Material.BOOKSHELF, "§d§l📜 รับ Unique Enchants ครบ 22 ใบ", List.of("§7รับคัมภีร์มนตราทั้ง 22 สกิลลงกระเป๋าทันที")));
        inv.setItem(51, createAction(Material.ENDER_CHEST, "§e§l🎲 สุ่มเปิด Vault (Test Roll)", List.of("§7คลิกเพื่อจำลองการเปิด Evergarden Vault")));
        inv.setItem(52, createSeparator(Material.BLACK_STAINED_GLASS_PANE, "§8✦"));
        inv.setItem(53, createAction(Material.BARRIER, "§c§l❌ ปิดเมนู (Close)", List.of("§7คลิกเพื่อปิดหน้าต่างนี้")));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.8f, 1.2f);
    }

    private ItemStack wrapDisplay(ItemStack raw, String actionHint) {
        if (raw == null) return new ItemStack(Material.AIR);
        ItemStack item = raw.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>(meta.lore() != null ? meta.lore() : List.of());
            lore.add(Component.text(""));
            lore.add(parseLegacy("§e🖱️ " + actionHint));
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
        if (!(e.getInventory().getHolder() instanceof RelicShowcaseGui)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;

        int slot = e.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        if (slot == 53) {
            p.closeInventory();
            return;
        }

        if (!isAdmin(p)) {
            p.sendMessage(ChatColor.RED + "✦ เมนูนี้เปิดสำหรับดูข้อมูลยุทธภัณฑ์ (ต้องมีสิทธิ์แอดมินจึงจะสามารถเสกไอเท็มได้)");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        boolean right = e.isRightClick();
        boolean shift = e.isShiftClick();

        switch (slot) {
            // Row 0: Relics & Core items
            case 0 -> give(p, plugin.relics().create(RelicService.Relic.SMELTER_PICKAXE, 1), "อีเต้อหลอมเพลิงมิติ (Smelter Pickaxe)");
            case 1 -> give(p, plugin.relics().create(RelicService.Relic.RIFT_PICKAXE, 1), "อีเต้อแยกพิภพ (Rift Pickaxe 3×3)");
            case 2 -> give(p, plugin.relics().create(RelicService.Relic.RIFT_BLADE, 1), "ดาบกรีดมิติ (Rift Blade)");
            case 3 -> give(p, plugin.relics().create(RelicService.Relic.ETERNAL_AEGIS, 1), "โล่แห่งความอมตะ (Eternal Aegis)");
            case 4 -> give(p, plugin.relics().create(RelicService.Relic.STORM_BOW, 1), "ธนูพิพากษาสายฟ้า (Storm Bow)");
            case 5 -> give(p, plugin.relics().create(RelicService.Relic.NOVA_BOW, 1), "ธนูสะเก็ดดาว (Nova Bow)");
            case 6 -> give(p, plugin.relics().create(RelicService.Relic.VOID_KEY, right ? 4 : 1), "กุญแจ Evergarden Key");
            case 7 -> give(p, plugin.relics().createKeyShard(right ? 16 : 4), "เศษกุญแจมิติ Key Shard");
            case 8 -> give(p, createScrollEternityStack(right || shift ? 5 : 1), "คัมภีร์ศิลานิรันดร์ Scroll of Eternity");

            // Row 1: Materials & God weapons
            case 9 -> give(p, plugin.relics().createAstralDust(right ? 64 : 16), "ผงละอองดาว Astral Dust");
            case 10 -> give(p, plugin.relics().createRepairStone(right ? 8 : 1), "ศิลาฟื้นฟูมิติ Repair Stone");
            case 11 -> give(p, plugin.relics().createVoidElixir(right ? 4 : 1), "น้ำยาเดินเวหา Void Elixir");
            case 12 -> {
                if (right || shift) {
                    for (com.example.voidscape.guide.GuideBookType gType : com.example.voidscape.guide.GuideBookType.values()) {
                        give(p, plugin.relics().createGuideBook(gType), gType.bookTitle);
                    }
                } else {
                    plugin.guideMenu().open(p);
                }
            }
            case 13 -> give(p, AdminTestGui.createGodSword(), "Dev God Sword");
            case 14 -> give(p, AdminTestGui.createGodBow(), "Dev God Bow");
            case 15 -> give(p, AdminTestGui.createGodPickaxe(), "Dev God Pickaxe");
            case 16 -> {
                AdminTestGui.giveGodArmorSet(p);
                p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.2f);
                p.sendMessage(ChatColor.GOLD + "✦ ได้รับ Full Set เกราะเทพเนเธอร์ไรต์ครบทั้ง 4 ชิ้น!");
            }
            case 17 -> giveUnique(p, UniqueEnchant.COLOSSUS_SLAYER, right || shift ? 5 : 1);

            // Row 2: Limit Breaks + Uniques
            case 18 -> giveLB(p, LimitBreakType.SHARPNESS, right || shift ? 5 : 1);
            case 19 -> giveLB(p, LimitBreakType.PROTECTION, right || shift ? 5 : 1);
            case 20 -> giveLB(p, LimitBreakType.POWER, right || shift ? 5 : 1);
            case 21 -> giveLB(p, LimitBreakType.EFFICIENCY, right || shift ? 5 : 1);
            case 22 -> giveLB(p, LimitBreakType.FORTUNE, right || shift ? 5 : 1);
            case 23 -> giveLB(p, LimitBreakType.LOOTING, right || shift ? 5 : 1);
            case 24 -> giveUnique(p, UniqueEnchant.RICOCHET, right || shift ? 5 : 1);
            case 25 -> giveUnique(p, UniqueEnchant.KINETIC_GRAPPLE, right || shift ? 5 : 1);
            case 26 -> giveUnique(p, UniqueEnchant.ABSOLUTE_ZERO, right || shift ? 5 : 1);

            // Row 3: Uniques
            case 27 -> giveUnique(p, UniqueEnchant.SINGULARITY, right || shift ? 5 : 1);
            case 28 -> giveUnique(p, UniqueEnchant.METEOR_ARROW, right || shift ? 5 : 1);
            case 29 -> giveUnique(p, UniqueEnchant.SEISMIC_SLAM, right || shift ? 5 : 1);
            case 30 -> giveUnique(p, UniqueEnchant.VEIN_SMELTER, right || shift ? 5 : 1);
            case 31 -> giveUnique(p, UniqueEnchant.ADVANCE_TOOL, right || shift ? 5 : 1);
            case 32 -> giveUnique(p, UniqueEnchant.DEMETER_SCYTHE, right || shift ? 5 : 1);
            case 33 -> giveUnique(p, UniqueEnchant.TITAN_BREACH, right || shift ? 5 : 1);
            case 34 -> giveUnique(p, UniqueEnchant.TELEPATHY, right || shift ? 5 : 1);
            case 35 -> giveUnique(p, UniqueEnchant.GUILLOTINE, right || shift ? 5 : 1);

            // Row 4: Uniques
            case 36 -> giveUnique(p, UniqueEnchant.ECHO_STRIKE, right || shift ? 5 : 1);
            case 37 -> giveUnique(p, UniqueEnchant.BLADE_VORTEX, right || shift ? 5 : 1);
            case 38 -> giveUnique(p, UniqueEnchant.SOUL_HARVEST, right || shift ? 5 : 1);
            case 39 -> giveUnique(p, UniqueEnchant.THUNDERLORD, right || shift ? 5 : 1);
            case 40 -> giveUnique(p, UniqueEnchant.VAMPIRIC, right || shift ? 5 : 1);
            case 41 -> giveUnique(p, UniqueEnchant.PHOENIX_REBIRTH, right || shift ? 5 : 1);
            case 42 -> giveUnique(p, UniqueEnchant.SHADOW_STEP, right || shift ? 5 : 1);
            case 43 -> giveUnique(p, UniqueEnchant.TITAN_STANCE, right || shift ? 5 : 1);
            case 44 -> giveUnique(p, UniqueEnchant.SOULBOUND, right || shift ? 5 : 1);

            // Row 5: Navigation & Bulk
            case 45 -> plugin.wandGui().open(p);
            case 46 -> plugin.cropGui().open(p);
            case 47 -> plugin.testGui().open(p);
            case 48 -> {
                for (RelicService.Relic r : new RelicService.Relic[]{
                    RelicService.Relic.SMELTER_PICKAXE,
                    RelicService.Relic.RIFT_PICKAXE,
                    RelicService.Relic.RIFT_BLADE,
                    RelicService.Relic.ETERNAL_AEGIS,
                    RelicService.Relic.STORM_BOW,
                    RelicService.Relic.NOVA_BOW
                }) {
                    give(p, plugin.relics().create(r, 1), r.title);
                }
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
                p.sendMessage(ChatColor.GOLD + "✦ ได้รับชุดยุทธภัณฑ์โบราณครบทั้ง 6 ชิ้น!");
            }
            case 49 -> {
                for (LimitBreakType lb : LimitBreakType.values()) {
                    ItemStack scroll = plugin.relics().createScrollLimitBreak(lb);
                    scroll.setAmount(5);
                    give(p, scroll, lb.title());
                }
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.9f, 1.2f);
                p.sendMessage(ChatColor.AQUA + "✦ ได้รับคัมภีร์ Limit Break ทุกชนิด อย่างละ 5 เล่ม!");
            }
            case 50 -> {
                for (UniqueEnchant ue : UniqueEnchant.values()) {
                    give(p, plugin.relics().createScrollUnique(ue), ue.title());
                }
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.9f, 1.2f);
                p.sendMessage(ChatColor.LIGHT_PURPLE + "✦ ได้รับคัมภีร์ Unique Enchants ครบทั้ง 22 ชนิด!");
            }
            case 51 -> {
                ItemStack reward = plugin.relics().rollVaultReward();
                give(p, reward, reward.getType().name());
                p.playSound(p.getLocation(), Sound.BLOCK_VAULT_OPEN_SHUTTER, 1.0f, 1.0f);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.3f);
                p.sendMessage(ChatColor.YELLOW + "✦ สุ่มเปิด Vault สำเร็จ!");
            }
        }
    }

    private ItemStack createScrollEternityStack(int count) {
        ItemStack item = plugin.relics().createScrollEternity();
        item.setAmount(Math.min(count, 64));
        return item;
    }

    private void giveLB(Player p, LimitBreakType lb, int count) {
        ItemStack item = plugin.relics().createScrollLimitBreak(lb);
        item.setAmount(count);
        give(p, item, "Limit Break: " + lb.title());
    }

    private void giveUnique(Player p, UniqueEnchant ue, int count) {
        ItemStack item = plugin.relics().createScrollUnique(ue);
        item.setAmount(count);
        give(p, item, "มนตรา: " + ue.title());
    }

    private void give(Player p, ItemStack item, String name) {
        if (item == null) return;
        var leftovers = p.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            for (ItemStack drop : leftovers.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), drop);
            }
        }
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.3f);
        p.sendMessage(ChatColor.GREEN + "✦ ได้รับ " + name + " ×" + item.getAmount());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof RelicShowcaseGui) {
            e.setCancelled(true);
        }
    }
}
