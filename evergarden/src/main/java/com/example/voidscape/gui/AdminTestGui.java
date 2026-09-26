package com.example.voidscape.gui;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.enchant.ItemCategory;
import com.example.voidscape.enchant.LimitBreakType;
import com.example.voidscape.enchant.UniqueEnchant;
import com.example.voidscape.item.RelicService;
import com.example.voidscape.world.DungeonLayout;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AdminTestGui implements InventoryHolder, Listener {
    private final VoidscapePlugin plugin;
    private final Component title = Component.text("✦ Evergarden Test Kit", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD);

    public AdminTestGui(VoidscapePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }

    private boolean isAdmin(Player p) {
        return p != null && (p.isOp() || p.hasPermission("evergarden.admin") || p.hasPermission("voidscape.admin"));
    }

    public void open(Player player) {
        if (!isAdmin(player)) {
            player.sendMessage(ChatColor.RED + "✦ เมนูนี้สำหรับแอดมินเท่านั้น!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }
        Inventory inv = Bukkit.createInventory(this, 54, title);

        // ==========================================
        // Row 0 (0-8): Core Items & Vault Test
        // ==========================================
        inv.setItem(0, plugin.relics().createScrollEternity());
        inv.setItem(1, plugin.relics().createVoidKey());
        inv.setItem(2, plugin.relics().createKeyShard(4));
        inv.setItem(3, plugin.relics().createRepairStone(1));
        inv.setItem(4, plugin.relics().createVoidElixir(1));
        inv.setItem(5, plugin.relics().createAstralDust(16));
        inv.setItem(6, plugin.relics().createMagicCore("shulker_levitation"));
        inv.setItem(7, createActionItem(Material.BOOK, "§a§l📚 ชุดคู่มือมิติ Evergarden (3 เล่ม)",
            List.of("§eคลิกซ้าย: §aเปิดเมนูเลือกคู่มือ (Guide Menu)",
                    "§6Shift+คลิก: §7รับคู่มือครบทั้ง 3 เล่มลงกระเป๋าทันที")));
        inv.setItem(8, createActionItem(Material.NETHER_STAR, "§6§l🏆 คลังเรลิก & วัตถุโบราณ (Relics Showcase)",
            List.of("§eคลิกซ้าย: §aเปิดคลังวัตถุโบราณและอาวุธเทพ (Showcase GUI)",
                    "§6คลิกขวา: §7จำลองการเปิด Evergarden Vault สุ่มของ",
                    "§cShift+คลิก: §7รับยุทธภัณฑ์โบราณครบ 6 ชิ้นทันที")));

        // ==========================================
        // Row 1 (9-17): Limit Break Scrolls
        // ==========================================
        inv.setItem(9, plugin.relics().createScrollLimitBreak(LimitBreakType.SHARPNESS));
        inv.setItem(10, plugin.relics().createScrollLimitBreak(LimitBreakType.PROTECTION));
        inv.setItem(11, plugin.relics().createScrollLimitBreak(LimitBreakType.POWER));
        inv.setItem(12, plugin.relics().createScrollLimitBreak(LimitBreakType.EFFICIENCY));
        inv.setItem(13, plugin.relics().createScrollLimitBreak(LimitBreakType.FORTUNE));
        inv.setItem(14, plugin.relics().createScrollLimitBreak(LimitBreakType.LOOTING));
        inv.setItem(15, createActionItem(Material.CARROT_ON_A_STICK, "§d§l🪄 คลังคทาเวทมนตร์ & ไม้เท้าบิน",
            List.of("§eคลิก: §aเปิดคลังคทาเวทมนตร์และปุ่มรับไม้เท้าบิน",
                    "§6Shift+คลิก: §7รับคทาทั้ง 17 สายลงกระเป๋าทันที")));
        inv.setItem(16, createActionItem(Material.CHEST, "§b§l📦 รับ Limit Break x5 ทุกชนิด",
            List.of("§7คลิกเพื่อรับคัมภีร์ Limit Break ทุกสาย", "§7สายละ 5 เล่มลงในกระเป๋า")));
        inv.setItem(17, createActionItem(Material.BOOKSHELF, "§d§l📜 รับ Unique Enchants ครบ 22 ใบ",
            List.of("§7คลิกเพื่อรับคัมภีร์มนตราทั้ง 22 สกิล", "§7ลงในกระเป๋าทันที")));

        // ==========================================
        // Row 2 (18-26): Bow & Mining Unique Enchants (9 ชิ้น)
        // ==========================================
        inv.setItem(18, plugin.relics().createScrollUnique(UniqueEnchant.COLOSSUS_SLAYER));
        inv.setItem(19, plugin.relics().createScrollUnique(UniqueEnchant.RICOCHET));
        inv.setItem(20, plugin.relics().createScrollUnique(UniqueEnchant.KINETIC_GRAPPLE));
        inv.setItem(21, plugin.relics().createScrollUnique(UniqueEnchant.ABSOLUTE_ZERO));
        inv.setItem(22, plugin.relics().createScrollUnique(UniqueEnchant.SINGULARITY));
        inv.setItem(23, plugin.relics().createScrollUnique(UniqueEnchant.METEOR_ARROW));
        inv.setItem(24, plugin.relics().createScrollUnique(UniqueEnchant.SEISMIC_SLAM));
        inv.setItem(25, plugin.relics().createScrollUnique(UniqueEnchant.VEIN_SMELTER));
        inv.setItem(26, plugin.relics().createScrollUnique(UniqueEnchant.ADVANCE_TOOL));

        // ==========================================
        // Row 3 (27-35): Farming & Melee Unique Enchants (9 ชิ้น)
        // ==========================================
        inv.setItem(27, plugin.relics().createScrollUnique(UniqueEnchant.DEMETER_SCYTHE));
        inv.setItem(28, plugin.relics().createScrollUnique(UniqueEnchant.TITAN_BREACH));
        inv.setItem(29, plugin.relics().createScrollUnique(UniqueEnchant.TELEPATHY));
        inv.setItem(30, plugin.relics().createScrollUnique(UniqueEnchant.GUILLOTINE));
        inv.setItem(31, plugin.relics().createScrollUnique(UniqueEnchant.ECHO_STRIKE));
        inv.setItem(32, plugin.relics().createScrollUnique(UniqueEnchant.BLADE_VORTEX));
        inv.setItem(33, plugin.relics().createScrollUnique(UniqueEnchant.SOUL_HARVEST));
        inv.setItem(34, plugin.relics().createScrollUnique(UniqueEnchant.THUNDERLORD));
        inv.setItem(35, plugin.relics().createScrollUnique(UniqueEnchant.VAMPIRIC));

        // ==========================================
        // Row 4 (36-44): Armor Enchants & Pre-made God Weapons
        // ==========================================
        inv.setItem(36, plugin.relics().createScrollUnique(UniqueEnchant.PHOENIX_REBIRTH));
        inv.setItem(37, plugin.relics().createScrollUnique(UniqueEnchant.SHADOW_STEP));
        inv.setItem(38, plugin.relics().createScrollUnique(UniqueEnchant.TITAN_STANCE));
        inv.setItem(39, plugin.relics().createScrollUnique(UniqueEnchant.SOULBOUND));
        inv.setItem(40, createActionItem(Material.WHEAT, "§a§l🌿 คลังพืชผล Evergarden 30 ชนิด",
            List.of("§7เปิดคลังเมล็ดพันธุ์และผลผลิตอาหาร 30 ชนิด", "§7พร้อมระบบเพาะปลูกและบัฟเวทมนตร์", "§eคลิกเพื่อเปิดเมนู Crop Showcase ทันที")));
        inv.setItem(41, createGodSword());
        inv.setItem(42, createGodBow());
        inv.setItem(43, createGodPickaxe());
        inv.setItem(44, createActionItem(Material.NETHERITE_CHESTPLATE, "§6§l🛡️ รับ Full Set เกราะเทพ (God Armor)",
            List.of("§7คลิกเพื่อรับเซ็ตเกราะเนเธอร์ไรต์ Protection VIII", "§7+ Phoenix Rebirth + Titan Stance + Shadow Step", "§6✦ สถิตนิรันดร์ ไม่มีวันพัง 100%")));

        // ==========================================
        // Row 5 (45-53): Sanctum Teleports, Boss Spawns & Utility
        // ==========================================
        inv.setItem(45, createActionItem(Material.WITHER_SKELETON_SKULL, "§c§l👹 วาร์ปไปวิหารความมืด",
            List.of("§7Sanctum of Darkness (จอมมารความมืด)", "§eคลิกเพื่อวาร์ปไปยังแท่น Lodestone ทันที")));
        inv.setItem(46, createActionItem(Material.NETHER_STAR, "§b§l🌟 วาร์ปไปวิหารดวงดาว",
            List.of("§7Sanctum of Astral (อัครเทวทูตดวงดาว)", "§eคลิกเพื่อวาร์ปไปยังแท่น Lodestone ทันที")));
        inv.setItem(47, createActionItem(Material.CLOCK, "§e§l⏳ วาร์ปไปวิหารกาลเวลา",
            List.of("§7Sanctum of Time (ผู้พิทักษ์กาลเวลา)", "§eคลิกเพื่อวาร์ปไปยังแท่น Lodestone ทันที")));
        inv.setItem(48, createActionItem(Material.RECOVERY_COMPASS, "§a§l🔄 รีเซ็ตคูลดาวน์วิหารทั้งหมด",
            List.of("§7ล้างคูลดาวน์วิหารทั้งมิติ", "§aคลิกแล้วสามารถเริ่มสู้บอสใหม่ได้ทันที")));
        inv.setItem(49, createActionItem(Material.DRAGON_HEAD, "§4§l⚔️ เสกบอสทดสอบตรงหน้า (Spawn Boss)",
            List.of("§7เสก Sanctum Boss เลือด 3,500 HP ตรงหน้าทันที", "§cใช้ทดสอบดาเมจและระบบต่อสู้")));
        inv.setItem(50, createActionItem(Material.BLAZE_ROD, "§6§l🧙‍♂️ รับชุดแกนเวทมนตร์ 14 ธาตุ",
            List.of("§7รับ Ancient Magic Core ครบทุกธาตุ 14 ชิ้น", "§7สำหรับคราฟต์คทาเวทมนตร์โบราณ")));
        inv.setItem(51, createActionItem(Material.FEATHER, "§f§l🧹 ล้างมอนสเตอร์และใยแมงมุม",
            List.of("§7เคลียร์มอนสเตอร์และใยแมงมุมดันเจี้ยนทั้งหมด", "§fช่วยรีเซ็ตสนามรบให้สะอาด")));
        inv.setItem(52, createActionItem(Material.GOLDEN_APPLE, "§d§l💖 ฮีลเลือดเต็ม & ล้างดีบัฟ",
            List.of("§7ฟื้นฟูเลือดและค่าอาหารเต็มหลอด", "§dลบเอฟเฟกต์ดีบัฟทั้งหมด")));
        inv.setItem(53, createActionItem(Material.BARRIER, "§c§l❌ ปิดเมนู (Close)",
            List.of("§7คลิกเพื่อปิดหน้าต่างทดสอบนี้")));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof AdminTestGui)) return;

        event.setCancelled(true);
        if (!isAdmin(player)) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "✦ ไม่มีสิทธิ์แอดมิน!");
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= 54) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        int slot = event.getRawSlot();

        switch (slot) {
            case 8 -> { // Vault Test Roll / Relic Showcase
                if (event.isShiftClick()) {
                    for (RelicService.Relic r : new RelicService.Relic[]{
                        RelicService.Relic.SMELTER_PICKAXE,
                        RelicService.Relic.RIFT_PICKAXE,
                        RelicService.Relic.RIFT_BLADE,
                        RelicService.Relic.ETERNAL_AEGIS,
                        RelicService.Relic.STORM_BOW,
                        RelicService.Relic.NOVA_BOW
                    }) {
                        giveOrDrop(player, plugin.relics().create(r, 1));
                    }
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
                    plugin.message(player, "ได้รับชุดยุทธภัณฑ์โบราณครบทั้ง 6 ชิ้น (รวม Smelter Pickaxe) ลงกระเป๋าเรียบร้อยแล้ว!");
                } else if (event.isRightClick()) {
                    ItemStack reward = plugin.relics().rollVaultReward();
                    giveOrDrop(player, reward);
                    player.playSound(player.getLocation(), Sound.BLOCK_VAULT_OPEN_SHUTTER, 1.0f, 1.0f);
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.3f);
                    String name = reward.getItemMeta() != null && reward.getItemMeta().hasDisplayName()
                        ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(reward.getItemMeta().displayName())
                        : reward.getType().name();
                    plugin.message(player, "สุ่มเปิด Vault ได้รับ: " + name + " ×" + reward.getAmount());
                } else {
                    if (plugin.relicGui() != null) {
                        plugin.relicGui().open(player);
                    }
                }
            }
            case 7 -> {
                if (event.isShiftClick()) {
                    for (com.example.voidscape.guide.GuideBookType gType : com.example.voidscape.guide.GuideBookType.values()) {
                        giveOrDrop(player, plugin.relics().createGuideBook(gType));
                    }
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.9f, 1.2f);
                    plugin.message(player, "ได้รับชุดคู่มือแนะนำการเล่นครบทั้ง 3 เล่ม!");
                } else {
                    plugin.guideMenu().open(player);
                }
            }
            case 40 -> plugin.cropGui().open(player);
            case 16 -> { // Limit Break All x5
                for (LimitBreakType lb : LimitBreakType.values()) {
                    ItemStack scroll = plugin.relics().createScrollLimitBreak(lb);
                    scroll.setAmount(5);
                    giveOrDrop(player, scroll);
                }
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.9f, 1.2f);
                plugin.message(player, "ได้รับคัมภีร์ Limit Break ทุกชนิด อย่างละ 5 เล่ม!");
            }
            case 17 -> { // Unique Enchants All 22
                for (UniqueEnchant ue : UniqueEnchant.values()) {
                    giveOrDrop(player, plugin.relics().createScrollUnique(ue));
                }
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.9f, 1.2f);
                plugin.message(player, "ได้รับคัมภีร์ Unique Enchants ครบทั้ง 22 ชนิด!");
            }
            case 41 -> { // God Sword
                giveOrDrop(player, createGodSword());
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.2f);
                plugin.message(player, "ได้รับ ดาบเทพแห่งความว่างเปล่า (Sharpness VIII + Guillotine + Thunderlord + Vampiric + Unbreakable)!");
            }
            case 42 -> { // God Bow
                giveOrDrop(player, createGodBow());
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.2f);
                plugin.message(player, "ได้รับ ธนูเทพล่าไททัน (Power VIII + Colossus Slayer + Ricochet + Unbreakable)!");
            }
            case 43 -> { // God Pickaxe / Smelter Pickaxe
                if (event.isRightClick()) {
                    giveOrDrop(player, plugin.relics().create(RelicService.Relic.SMELTER_PICKAXE, 1));
                    player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.2f);
                    plugin.message(player, "ได้รับ อีเต้อหลอมเพลิงมิติ (Smelter Pickaxe - Auto Smelt)!");
                } else if (event.isShiftClick()) {
                    giveOrDrop(player, plugin.relics().create(RelicService.Relic.RIFT_PICKAXE, 1));
                    player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.2f);
                    plugin.message(player, "ได้รับ อีเต้อแยกพิภพ (Rift Pickaxe 3×3)!");
                } else {
                    giveOrDrop(player, createGodPickaxe());
                    player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.2f);
                    plugin.message(player, "ได้รับ จอบเจาะมิติทลายแผ่นดิน (Efficiency VIII + Fortune V + Seismic Slam + Vein Smelter + Telepathy + Unbreakable)!");
                }
            }
            case 44 -> { // God Armor Set
                giveGodArmorSet(player);
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.0f);
                plugin.message(player, "ได้รับ ชุดเกราะเทพเนเธอร์ไรต์ครบเซ็ต (Protection VIII + Phoenix Rebirth + Titan Stance + Shadow Step + Unbreakable)!");
            }
            case 45 -> warpToSanctum(player, DungeonLayout.Kind.SANCTUM_DARK);
            case 46 -> warpToSanctum(player, DungeonLayout.Kind.SANCTUM_ASTRAL);
            case 47 -> warpToSanctum(player, DungeonLayout.Kind.SANCTUM_TIME);
            case 48 -> { // Reset cooldowns
                plugin.dungeons().resetAllCooldowns();
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.4f);
                plugin.message(player, "รีเซ็ตคูลดาวน์วิหารทั้งหมดเรียบร้อยแล้ว! สามารถคลิก Lodestone สู้บอสได้ทันที");
            }
            case 49 -> { // Spawn test boss
                Location spawnLoc = player.getLocation().add(player.getLocation().getDirection().multiply(4));
                spawnLoc.setY(player.getLocation().getY());
                plugin.dungeons().spawnTestBoss(spawnLoc, DungeonLayout.Kind.SANCTUM_DARK);
                player.playSound(spawnLoc, Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.0f);
                plugin.message(player, "เสกบอสทดสอบ (Shadow Overlord) ตรงหน้าแล้ว!");
            }
            case 15 -> { // Magic Wands & Showcase
                if (event.isShiftClick()) {
                    for (RelicService.MagicCore c : RelicService.MAGIC_CORES) {
                        ItemStack wand = com.example.voidscape.command.VoidCommand.createWandViaAdvanceMagic(c.id());
                        if (wand != null) giveOrDrop(player, wand);
                    }
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.9f, 1.2f);
                    plugin.message(player, "ได้รับ Ancient Magic Wands ครบทั้ง 17 เล่ม!");
                } else {
                    if (plugin.wandGui() != null) {
                        plugin.wandGui().open(player);
                    }
                }
            }
            case 50 -> { // 17 Magic Cores
                for (RelicService.MagicCore c : RelicService.MAGIC_CORES) {
                    giveOrDrop(player, plugin.relics().createMagicCore(c));
                }
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.9f, 1.2f);
                plugin.message(player, "ได้รับ Ancient Magic Cores ครบทุกธาตุ 17 ชิ้น!");
            }
            case 51 -> { // Clear mobs
                plugin.dungeons().clearAllDungeonMobs();
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
                plugin.message(player, "ล้างมอนสเตอร์และใยแมงมุมตกค้างเรียบร้อย!");
            }
            case 52 -> { // Heal & Cleanse
                var maxHp = player.getAttribute(Attribute.MAX_HEALTH);
                if (maxHp != null) player.setHealth(maxHp.getValue());
                player.setFoodLevel(20);
                player.setSaturation(20f);
                player.setFireTicks(0);
                for (var effect : player.getActivePotionEffects()) {
                    player.removePotionEffect(effect.getType());
                }
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.8f);
                plugin.message(player, "ฟื้นฟูเลือดเต็มหลอดและล้างดีบัฟเรียบร้อย!");
            }
            case 53 -> player.closeInventory();
            default -> { // Normal item grant (clone and give)
                ItemStack toGive = clicked.clone();
                giveOrDrop(player, toGive);
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.3f);
                String itemName = toGive.getItemMeta() != null && toGive.getItemMeta().hasDisplayName()
                    ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(toGive.getItemMeta().displayName())
                    : toGive.getType().name();
                player.sendActionBar(Component.text("✦ ได้รับ " + itemName, NamedTextColor.GREEN));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof AdminTestGui) {
            event.setCancelled(true);
        }
    }

    private void warpToSanctum(Player player, DungeonLayout.Kind kind) {
        int x = player.getWorld() == plugin.world() ? player.getLocation().getBlockX() : 0;
        int z = player.getWorld() == plugin.world() ? player.getLocation().getBlockZ() : 0;
        var s = plugin.layout().locate(x, z, kind, 12);
        if (s == null) {
            plugin.message(player, "ไม่พบวิหารในระยะค้นหา");
            return;
        }
        Location dest = new Location(plugin.world(), s.x() + 0.5, 97.0, s.z() + 8.5);
        player.teleport(dest);
        player.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        plugin.message(player, "วาร์ปมายัง " + s.kind().displayName + " (X=" + s.x() + " Y=97 Z=" + (s.z() + 8) + ") เรียบร้อย!");
    }

    private ItemStack createSeparator(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }

    ItemStack createFlyingStaff() {
        org.bukkit.plugin.Plugin magicPlugin = Bukkit.getPluginManager().getPlugin("advance-magic");
        if (magicPlugin instanceof com.example.advancemagic.AdvanceMagicPlugin magic
                && magic.isEnabled() && magic.flyingStaff() != null) {
            return magic.flyingStaff().create();
        }
        return null;
    }

    ItemStack createFlyingStaffMenuItem() {
        ItemStack item = createFlyingStaff();
        if (item == null) {
            return createActionItem(Material.BARRIER, ChatColor.RED + "ยังใช้ไม้เท้าบินไม่ได้",
                    List.of(ChatColor.GRAY + "ต้องเปิดใช้งาน Advance Magic ก่อน"));
        }
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.YELLOW + "คลิก: รับไม้เท้าบิน 1 อัน");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createActionItem(Material mat, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(Component.text(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ==========================================
    // Pre-made God Test Weapons & Armor
    // ==========================================

    public static ItemStack createGodSword() {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.displayName(Component.text("✦ ดาบเทพแห่งความว่างเปล่า (Dev God Sword)", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        VoidscapePlugin plugin = VoidscapePlugin.getPlugin(VoidscapePlugin.class);
        plugin.relics().applyEternityMeta(meta);
        plugin.relics().applyLimitBreakMeta(meta, LimitBreakType.SHARPNESS, 8);
        plugin.relics().applyLimitBreakMeta(meta, LimitBreakType.LOOTING, 5);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);

        // Apply Unique Enchants PDC
        attachUnique(meta, UniqueEnchant.GUILLOTINE);
        attachUnique(meta, UniqueEnchant.THUNDERLORD);
        attachUnique(meta, UniqueEnchant.VAMPIRIC);

        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.text("✦ Guillotine · กิโยตินปลิดชีพ", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("   §7ฟันสังหารมอนสเตอร์เลือดต่ำกว่า 15% ทันที (Execute)").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("✦ Thunderlord · สายฟ้าทัณฑ์สวรรค์", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("   §7ฟันเป้าหมายเดิมครบ 3 ครั้ง ผ่าสายฟ้า True Damage").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("✦ Vampiric · สูบโลหิต", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("   §7แปลง 15% ของดาเมจที่ทำได้กลับมาฟื้นฟูเลือดผู้เล่น").decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        sword.setItemMeta(meta);
        return sword;
    }

    public static ItemStack createGodBow() {
        ItemStack bow = new ItemStack(Material.BOW);
        ItemMeta meta = bow.getItemMeta();
        meta.displayName(Component.text("✦ ธนูเทพล่าไททัน (Dev God Bow)", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        VoidscapePlugin plugin = VoidscapePlugin.getPlugin(VoidscapePlugin.class);
        plugin.relics().applyEternityMeta(meta);
        plugin.relics().applyLimitBreakMeta(meta, LimitBreakType.POWER, 8);
        meta.addEnchant(Enchantment.INFINITY, 1, true);

        attachUnique(meta, UniqueEnchant.COLOSSUS_SLAYER);
        attachUnique(meta, UniqueEnchant.RICOCHET);

        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.text("✦ Colossus Slayer · ล่าไททัน", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("   §7ยิงแรงขึ้นตาม % Max HP ของบอสและมอนสเตอร์").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("✦ Ricochet · กระสุนชิ่งสายฟ้า", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("   §7ลูกธนูชิ่งหามอนสเตอร์รอบข้าง 3 ตัวพร้อมปล่อยสายฟ้า").decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        bow.setItemMeta(meta);
        return bow;
    }

    public static ItemStack createGodPickaxe() {
        ItemStack pick = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = pick.getItemMeta();
        meta.displayName(Component.text("✦ จอบเจาะมิติทลายแผ่นดิน (Dev God Pickaxe)", NamedTextColor.GOLD, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        VoidscapePlugin plugin = VoidscapePlugin.getPlugin(VoidscapePlugin.class);
        plugin.relics().applyEternityMeta(meta);
        plugin.relics().applyLimitBreakMeta(meta, LimitBreakType.EFFICIENCY, 8);
        plugin.relics().applyLimitBreakMeta(meta, LimitBreakType.FORTUNE, 5);

        attachUnique(meta, UniqueEnchant.SEISMIC_SLAM);
        attachUnique(meta, UniqueEnchant.VEIN_SMELTER);
        attachUnique(meta, UniqueEnchant.ADVANCE_TOOL);
        attachUnique(meta, UniqueEnchant.TELEPATHY);

        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.text("✦ Seismic Slam · ขุดทลาย 3x3", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("   §7ขุด 1 ครั้งระเบิดเปิดโพรง 3×3×1 ทันที").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("✦ Vein Smelter · หลอมสายแร่คู่", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("   §7ขุดทั้งสายแร่ + เผาเป็นแท่งโลหะ + โบนัสแร่ทันที").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("✦ Advance Tool · เครื่องมือสารพัดช่าง", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("   §7ขุดบล็อกทุกประเภท (ดิน, ทราย, กรวด, ไม้, หิน) ด้วยความเร็วสูง 25.0f").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("✦ Telepathy · จิตสื่อสาร", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("   §7แร่และของที่ขุดได้ทุกชิ้นวาร์ปเข้าตัวผู้เล่น 100%").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("§e[คลิกซ้าย: รับ God Pickaxe | คลิกขวา: รับ Smelter Pickaxe | Shift+คลิก: รับ Rift Pickaxe]", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        pick.setItemMeta(meta);
        return pick;
    }

    public static ItemStack createGodHelmet() {
        ItemStack helm = new ItemStack(Material.NETHERITE_HELMET);
        ItemMeta hMeta = helm.getItemMeta();
        hMeta.displayName(Component.text("✦ หมวกเทพพิทักษ์มิติ (God Helmet)", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        VoidscapePlugin plugin = VoidscapePlugin.getPlugin(VoidscapePlugin.class);
        plugin.relics().applyEternityMeta(hMeta);
        plugin.relics().applyLimitBreakMeta(hMeta, LimitBreakType.PROTECTION, 8);
        helm.setItemMeta(hMeta);
        return helm;
    }

    public static ItemStack createGodChestplate() {
        ItemStack chest = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemMeta cMeta = chest.getItemMeta();
        cMeta.displayName(Component.text("✦ เกราะอกฟีนิกซ์นิรันดร์ (God Chestplate)", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        VoidscapePlugin plugin = VoidscapePlugin.getPlugin(VoidscapePlugin.class);
        plugin.relics().applyEternityMeta(cMeta);
        plugin.relics().applyLimitBreakMeta(cMeta, LimitBreakType.PROTECTION, 8);
        attachUnique(cMeta, UniqueEnchant.PHOENIX_REBIRTH);
        List<Component> lore = cMeta.hasLore() ? new ArrayList<>(cMeta.lore()) : new ArrayList<>();
        lore.add(Component.text("✦ Phoenix Rebirth · ฟีนิกซ์คืนชีพ", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("   §7เมื่อตาย คืนชีพ 50% HP + คลื่นไฟ (คูลดาวน์ 10 นาที)").decoration(TextDecoration.ITALIC, false));
        cMeta.lore(lore);
        chest.setItemMeta(cMeta);
        return chest;
    }

    public static ItemStack createGodLeggings() {
        ItemStack legs = new ItemStack(Material.NETHERITE_LEGGINGS);
        ItemMeta lMeta = legs.getItemMeta();
        lMeta.displayName(Component.text("✦ สนับเพลาศิลาไร้พ่าย (God Leggings)", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        VoidscapePlugin plugin = VoidscapePlugin.getPlugin(VoidscapePlugin.class);
        plugin.relics().applyEternityMeta(lMeta);
        plugin.relics().applyLimitBreakMeta(lMeta, LimitBreakType.PROTECTION, 8);
        attachUnique(lMeta, UniqueEnchant.TITAN_STANCE);
        List<Component> lore = lMeta.hasLore() ? new ArrayList<>(lMeta.lore()) : new ArrayList<>();
        lore.add(Component.text("✦ Titan Stance · ร่างศิลาไร้พ่าย", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("   §7ต้านทาน Knockback 100% และลดดาเมจแรงระเบิด 40%").decoration(TextDecoration.ITALIC, false));
        lMeta.lore(lore);
        legs.setItemMeta(lMeta);
        return legs;
    }

    public static ItemStack createGodBoots() {
        ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);
        ItemMeta bMeta = boots.getItemMeta();
        bMeta.displayName(Component.text("✦ รองเท้าก้าวพริบตามิติ (God Boots)", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        VoidscapePlugin plugin = VoidscapePlugin.getPlugin(VoidscapePlugin.class);
        plugin.relics().applyEternityMeta(bMeta);
        plugin.relics().applyLimitBreakMeta(bMeta, LimitBreakType.PROTECTION, 8);
        attachUnique(bMeta, UniqueEnchant.SHADOW_STEP);
        List<Component> lore = bMeta.hasLore() ? new ArrayList<>(bMeta.lore()) : new ArrayList<>();
        lore.add(Component.text("✦ Shadow Step · ก้าวพริบตา", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("   §7กดย่อ 2 ครั้ง พริบตาวาร์ปไปข้างหน้า 6 บล็อก (คูลดาวน์ 4 วิ)").decoration(TextDecoration.ITALIC, false));
        bMeta.lore(lore);
        boots.setItemMeta(bMeta);
        return boots;
    }

    public static void giveGodArmorSet(Player player) {
        giveOrDrop(player, createGodHelmet());
        giveOrDrop(player, createGodChestplate());
        giveOrDrop(player, createGodLeggings());
        giveOrDrop(player, createGodBoots());
    }

    public static void attachUnique(ItemMeta meta, UniqueEnchant ue) {
        NamespacedKey key = new NamespacedKey("evergarden", "ue_" + ue.id().toLowerCase(Locale.ROOT));
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        if (ue == UniqueEnchant.ADVANCE_TOOL) {
            com.example.voidscape.enchant.EnchantApplyListener.applyAdvanceToolComponent(meta);
        }
    }

    public static void giveOrDrop(Player player, ItemStack item) {
        if (item == null) return;
        var leftovers = player.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            for (ItemStack drop : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }
}
