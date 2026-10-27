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
    private static final class BossTestHolder implements InventoryHolder {
        @Override public Inventory getInventory(){return null;}
    }
    private static final class RiverTestHolder implements InventoryHolder {
        @Override public Inventory getInventory(){return null;}
    }
    private static final class MainBossHolder implements InventoryHolder {
        @Override public Inventory getInventory(){return null;}
    }
    private static final class ManaPotionHolder implements InventoryHolder {
        @Override public Inventory getInventory(){return null;}
    }
    private record SpellScrollHolder(com.example.advancemagic.item.ElementalStaffService.Element element,
                                     int page) implements InventoryHolder {
        @Override public Inventory getInventory(){return null;}
        List<com.example.advancemagic.elemental.ElementalSpell> spells(){
            return java.util.Arrays.stream(com.example.advancemagic.elemental.ElementalSpell.values())
                .filter(spell->element==null||spell.element()==element).toList();
        }
    }

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
        inv.setItem(2,createActionItem(Material.POTION,"§d§lยามานา · เลือกคุณภาพ",List.of("§7เสกยามานาทั้ง 4 คุณภาพ")));
        inv.setItem(3,createActionItem(Material.ECHO_SHARD,"§d§lลูกน้อง Arkhaia V2",List.of("§7เลือกเสกดูโมเดลหรือทดสอบต่อสู้ ทั้งหกชนิด")));
        inv.setItem(4,createActionItem(Material.ENCHANTED_BOOK,"§b§lคัมภีร์เวท 58 แบบ",List.of("§7เลือกธาตุ แล้วคลิกเสกคัมภีร์", "§7ซ้าย 1 · ขวา 4 · Shift 16 ใบ")));
        inv.setItem(5, createActionItem(Material.AMETHYST_BLOCK, "§d§l⚔ ทดสอบ World Boss · เลือกเฟส",
            List.of("§7เริ่มเฟส 1 / 2 / 3 ได้ทันที พร้อมวาร์ปเข้าลาน", "§7เรียกท่าชาร์จเฟส 3 · รีเซ็ต · หยุดไฟต์", "§eไฟต์ทดสอบไม่มีรางวัลและไม่เปลี่ยนคูลดาวน์จริง")));
        inv.setItem(6, createActionItem(Material.NETHERITE_SPEAR, "§6§l⚔ อาวุธนักผจญภัย 20 เล่ม (Sanctum Armoury)",
            List.of("§7คลังอาวุธหายากประจำวิหารทั้ง 3 ธาตุ", "§7ดาบ · หอก · ค้อน · ธนู · หน้าไม้ (Java + Bedrock 3D)", "§eคลิกเพื่อเปิดดูและเสกอาวุธทั้ง 20 ชิ้น")));
        inv.setItem(7, createActionItem(Material.BOOK, "§a§l📚 ชุดคู่มือมิติ Evergarden (3 เล่ม)",
            List.of("§eคลิกซ้าย: §aเปิดเมนูเลือกคู่มือ (Guide Menu)",
                    "§6Shift+คลิก: §7รับคู่มือครบทั้ง 3 เล่มลงกระเป๋าทันที")));
        inv.setItem(8, createActionItem(Material.NETHER_STAR, "§6§l🏆 คลังเรลิก & วัตถุโบราณ (Relics Showcase)",
            List.of("§eคลิกซ้าย: §aเปิดคลังวัตถุโบราณและอาวุธเทพ (Showcase GUI)",
                    "§6คลิกขวา: §7จำลองการเปิด Evergarden Vault สุ่มของ",
                    "\u00a7cShift+คลิก: \u00a77\u0e23\u0e31\u0e1a\u0e40\u0e04\u0e23\u0e37\u0e48\u0e2d\u0e07\u0e21\u0e37\u0e2d\u0e42\u0e1a\u0e23\u0e32\u0e13 2 \u0e0a\u0e34\u0e49\u0e19")));

        // ==========================================
        // Row 1 (9-17): Limit Break Scrolls
        // ==========================================
        inv.setItem(9, plugin.relics().createScrollLimitBreak(LimitBreakType.SHARPNESS));
        inv.setItem(10, plugin.relics().createScrollLimitBreak(LimitBreakType.PROTECTION));
        inv.setItem(11, plugin.relics().createScrollLimitBreak(LimitBreakType.POWER));
        inv.setItem(12, plugin.relics().createScrollLimitBreak(LimitBreakType.EFFICIENCY));
        inv.setItem(13, plugin.relics().createScrollLimitBreak(LimitBreakType.FORTUNE));
        inv.setItem(14, plugin.relics().createScrollLimitBreak(LimitBreakType.LOOTING));
        inv.setItem(15, createActionItem(Material.CARROT_ON_A_STICK, "§d§lคทาธาตุ & ไม้เท้าบินระดับ 1–2",
            List.of("§eคลิก: §aดูโมเดลคทาธาตุชุดใหม่",
                    "§7ไม้เท้าบินระดับ 2 ซ้อนเพิ่มได้ 1 คน")));
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
            List.of("§7ล้างคูลดาวน์วิหารทั้งมิติ", "§7เก็บประวัติเปิด Vault ไว้ เปิดซ้ำไม่ได้", "§aคลิกแล้วสามารถเริ่มสู้บอสใหม่ได้ทันที")));
        inv.setItem(49, createActionItem(Material.DRAGON_HEAD, "§4§l⚔️ เสกบอสทดสอบตรงหน้า (Spawn Boss)",
            List.of("§7เสก Sanctum Boss เลือด 3,500 HP ตรงหน้าทันที", "§cใช้ทดสอบดาเมจและระบบต่อสู้")));
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
        if(event.getInventory().getHolder() instanceof SpellScrollHolder holder){
            event.setCancelled(true);
            if(!isAdmin(player)){player.closeInventory();return;}
            int slot=event.getRawSlot();
            if(slot<0||slot>=54)return;
            if(slot<=8||slot==45||slot==49||slot==53){
                Bukkit.getScheduler().runTask(plugin,()->{
                    if(!player.isOnline()||!isAdmin(player))return;
                    if(slot==49){open(player);return;}
                    var filter=slot==0?null:slot<=8?com.example.advancemagic.item.ElementalStaffService.Element.values()[slot-1]:holder.element();
                    int page=slot==45?holder.page()-1:slot==53?holder.page()+1:0;
                    openSpellScrolls(player,filter,page);
                });return;
            }
            if(slot<9||slot>=45)return;
            var spells=holder.spells();int index=holder.page()*36+slot-9;
            if(index>=spells.size())return;
            var magic=magic();if(magic==null)return;
            var spell=spells.get(index);
            ItemStack scroll=magic.staffSpells().createScroll(spell);
            scroll.setAmount(event.isShiftClick()?16:event.isRightClick()?4:1);
            // Refuse a full inventory without dropping or deleting any items.
            Inventory preview=Bukkit.createInventory(null,36);
            preview.setContents(player.getInventory().getStorageContents());
            if(!preview.addItem(scroll.clone()).isEmpty()){
                player.sendMessage("§cกระเป๋าเต็ม กรุณาเว้นที่ก่อนรับคัมภีร์");return;
            }
            player.getInventory().addItem(scroll);
            player.sendMessage("§aได้รับ "+spell.title()+" Scroll ×"+(event.isShiftClick()?16:event.isRightClick()?4:1));
            player.playSound(player.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,.4f,1.4f);
            return;
        }
        if(event.getInventory().getHolder() instanceof ManaPotionHolder){
            event.setCancelled(true);
            if(!isAdmin(player)){player.closeInventory();return;}
            int raw=event.getRawSlot();
            if(raw==22){Bukkit.getScheduler().runTask(plugin,()->{if(player.isOnline())open(player);});return;}
            if(raw<10||raw>16||(raw-10)%2!=0)return;
            var magic=magic();if(magic==null)return;
            var quality=com.example.advancemagic.mana.ManaPotionService.Quality.values()[(raw-10)/2];
            int amount=event.isShiftClick()?8:event.isRightClick()?4:1;
            for(int count=0;count<amount;count++)giveOrDrop(player,magic.manaPotions().create(quality));
            return;
        }
        if(event.getInventory().getHolder() instanceof MainBossHolder){
            event.setCancelled(true);if(!isAdmin(player)){player.closeInventory();return;}
            int slot=event.getRawSlot();if(slot<0||slot>=36||event.getCurrentItem()==null)return;
            Bukkit.getScheduler().runTask(plugin,()->{
                if(!player.isOnline()||!isAdmin(player))return;
                var modes=com.example.voidscape.boss.WorldBossManager.bossShowcaseModes();
                switch(slot){
                    case 10->{player.closeInventory();plugin.worldBoss().spawnArkhaiaPreview(player);}
                    case 12->{player.closeInventory();plugin.worldBoss().spawnSeraphonPreview(player);}
                    case 14->{player.closeInventory();plugin.worldBoss().spawnBossShowcase(player);}
                    case 16->{plugin.worldBoss().removeFactionPreviews(player);openMainBosses(player);}
                    case 27->openBossTests(player);
                    case 35->player.closeInventory();
                    default->{if(slot>=18&&slot<18+modes.size()){plugin.worldBoss().bossShowcaseMode(player,modes.get(slot-18));openMainBosses(player);}}
                }
            });return;
        }
        if(event.getInventory().getHolder() instanceof RiverTestHolder){
            event.setCancelled(true);if(!isAdmin(player)){player.closeInventory();return;}
            int slot=event.getRawSlot();if(slot<0||slot>=54||event.getCurrentItem()==null)return;
            Bukkit.getScheduler().runTask(plugin,()->{
                if(!player.isOnline()||!isAdmin(player))return;player.closeInventory();
                if(slot>=10&&slot<=15)plugin.worldBoss().riverTest(player,slot-10,false);
                else if(slot>=19&&slot<=24)plugin.worldBoss().riverTest(player,slot-19,true);
                else switch(slot){
                    case 28->plugin.worldBoss().riverTest(player,-1,false);
                    case 30->plugin.worldBoss().riverTest(player,-1,true);
                    case 32->{plugin.worldBoss().clearRiverTest(player);openRiverTests(player);}
                    case 38,39,40,41->{plugin.worldBoss().riverPose(player,List.of("idle","walk","cast","stun").get(slot-38));openRiverTests(player);}
                    case 45->open(player);
                    default->{}
                }
            });return;
        }
        if(event.getInventory().getHolder() instanceof BossTestHolder){
            event.setCancelled(true);
            if(!isAdmin(player)){player.closeInventory();return;}
            int slot=event.getRawSlot();
            if(slot<0||slot>=27||event.getCurrentItem()==null)return;
            Bukkit.getScheduler().runTask(plugin,()->{
                if(!player.isOnline()||!isAdmin(player))return;
                player.closeInventory();
                switch(slot){
                    case 9 -> plugin.worldBoss().triggerTestBait(player);
                    case 10 -> plugin.worldBoss().startTest(player,1);
                    case 11 -> plugin.worldBoss().spawnSeraphPreview(player);
                    case 12 -> plugin.worldBoss().startTest(player,2);
                    case 13 -> plugin.worldBoss().removeSeraphPreview(player);
                    case 14 -> plugin.worldBoss().startTest(player,3);
                    case 15 -> plugin.worldBoss().triggerTestJudgment(player);
                    case 16 -> plugin.worldBoss().triggerTestRupture(player);
                    case 0 -> plugin.worldBoss().spawnSeraphonPreview(player);
                    case 1 -> openMainBosses(player);
                    case 2 -> plugin.worldBoss().spawnArkhaiaPreview(player);
                    case 6 -> plugin.worldBoss().removeFactionPreviews(player);
                    case 7 -> openRiverTests(player);
                    case 18 -> open(player);
                    case 20 -> {
                        if(!player.isDead()){player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());player.setFoodLevel(20);player.setSaturation(20);player.setFireTicks(0);}
                        openBossTests(player);
                    }
                    case 22 -> plugin.worldBoss().stopTest(player);
                    case 24 -> {
                        ItemStack sword=new ItemStack(Material.DIAMOND_SWORD);var meta=sword.getItemMeta();meta.setUnbreakable(true);
                        meta.displayName(Component.text("ดาบฝึกขัดท่า",NamedTextColor.AQUA));sword.setItemMeta(meta);
                        giveOrDrop(player,sword);openBossTests(player);
                    }
                    default -> {}
                }
            });
            return;
        }
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
            case 4 -> Bukkit.getScheduler().runTask(plugin,()->openSpellScrolls(player,null,0));
            case 2 -> Bukkit.getScheduler().runTask(plugin,()->{if(player.isOnline())openManaPotions(player);});
            case 3 -> Bukkit.getScheduler().runTask(plugin,()->openRiverTests(player));
            case 5 -> Bukkit.getScheduler().runTask(plugin,()->openBossTests(player));
            case 6 -> Bukkit.getScheduler().runTask(plugin,()->{if(player.isOnline()&&plugin.adventurerWeapons()!=null)plugin.adventurerWeapons().openGui(player);});
            case 8 -> { // Vault Test Roll / Relic Showcase
                if (event.isShiftClick()) {
                    for (RelicService.Relic r : new RelicService.Relic[]{
                        RelicService.Relic.SMELTER_PICKAXE,
                        RelicService.Relic.RIFT_PICKAXE,
                    }) {
                        giveOrDrop(player, plugin.relics().create(r, 1));
                    }
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
                    plugin.message(player, "\u0e44\u0e14\u0e49\u0e23\u0e31\u0e1a\u0e40\u0e04\u0e23\u0e37\u0e48\u0e2d\u0e07\u0e21\u0e37\u0e2d\u0e42\u0e1a\u0e23\u0e32\u0e13 2 \u0e0a\u0e34\u0e49\u0e19 (Rift Pickaxe \u0e41\u0e25\u0e30 Smelter Pickaxe)!");
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
                plugin.message(player, "ได้รับ ดาบเทพแห่งความว่างเปล่า (Sharpness VIII + Thunderlord + Vampiric + Unbreakable)!");
            }
            case 42 -> { // God Bow
                giveOrDrop(player, createGodBow());
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.2f);
                plugin.message(player, "ได้รับ ธนูเทพล่าไททัน (Power VIII + Colossus Slayer + Unbreakable)!");
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
                    plugin.message(player, "ได้รับ จอบเจาะมิติทลายแผ่นดิน (Efficiency VIII + Fortune V + Seismic Slam + Telepathy + Unbreakable)!");
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
                if(!plugin.dungeons().resetAllCooldowns()) {
                    plugin.message(player,"รีเซ็ตคูลดาวน์ไม่สำเร็จ · ระบบบันทึกไม่พร้อม กรุณาตรวจ server log");
                    return;
                }
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
            case 15 -> Bukkit.getScheduler().runTask(plugin,()->{if(player.isOnline()&&plugin.wandGui()!=null)plugin.wandGui().open(player);});
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
        if (event.getInventory().getHolder() instanceof AdminTestGui||event.getInventory().getHolder() instanceof BossTestHolder||event.getInventory().getHolder() instanceof ManaPotionHolder||event.getInventory().getHolder() instanceof RiverTestHolder||event.getInventory().getHolder() instanceof MainBossHolder||event.getInventory().getHolder() instanceof SpellScrollHolder) {
            event.setCancelled(true);
        }
    }
    private com.example.advancemagic.AdvanceMagicPlugin magic(){
        var addon=Bukkit.getPluginManager().getPlugin("advance-magic");
        return addon instanceof com.example.advancemagic.AdvanceMagicPlugin magic&&magic.isEnabled()?magic:null;
    }
    public void openSpellScrolls(Player player,com.example.advancemagic.item.ElementalStaffService.Element element,int requestedPage){
        if(!isAdmin(player))return;
        var magic=magic();
        if(magic==null||magic.staffSpells()==null){player.sendMessage("§cต้องเปิดใช้งาน Advance Magic ก่อน");return;}
        var initial=new SpellScrollHolder(element,0);var spells=initial.spells();
        int pages=Math.max(1,(spells.size()+35)/36),page=Math.max(0,Math.min(requestedPage,pages-1));
        Inventory inv=Bukkit.createInventory(new SpellScrollHolder(element,page),54,
            Component.text("คัมภีร์เวท · "+(element==null?"ทั้งหมด":element.title)+" · "+(page+1)+"/"+pages,NamedTextColor.DARK_PURPLE));
        inv.setItem(0,createActionItem(Material.BOOKSHELF,"§fทุกธาตุ",List.of("§7คัมภีร์เวทครบ 58 แบบ")));
        for(var filter:com.example.advancemagic.item.ElementalStaffService.Element.values()){
            ItemStack icon=magic.staffs().create(filter);var meta=icon.getItemMeta();
            meta.setDisplayName(filter.color+"คัมภีร์ธาตุ "+filter.title);
            meta.setLore(List.of("§7คลิกเพื่อกรองคัมภีร์ธาตุนี้"));icon.setItemMeta(meta);inv.setItem(filter.ordinal()+1,icon);
        }
        for(int i=0;i<36&&page*36+i<spells.size();i++){
            ItemStack icon=magic.staffSpells().createScroll(spells.get(page*36+i));var meta=icon.getItemMeta();
            var lore=new ArrayList<>(meta.getLore()==null?List.<String>of():meta.getLore());
            lore.add("§eซ้าย 1 · ขวา 4 · Shift 16 ใบ");meta.setLore(lore);icon.setItemMeta(meta);inv.setItem(9+i,icon);
        }
        if(page>0)inv.setItem(45,createActionItem(Material.ARROW,"§eหน้าก่อนหน้า",List.of()));
        inv.setItem(49,createActionItem(Material.BARRIER,"§eกลับ Evergarden Test",List.of()));
        if(page+1<pages)inv.setItem(53,createActionItem(Material.ARROW,"§eหน้าถัดไป",List.of()));
        player.openInventory(inv);
    }
    public void openManaPotions(Player player){
        if(!isAdmin(player))return;
        var magic=magic();if(magic==null){player.sendMessage(Component.text("ต้องเปิดใช้งาน Advance Magic ก่อน",NamedTextColor.RED));return;}
        Inventory inventory=Bukkit.createInventory(new ManaPotionHolder(),27,Component.text("ยามานา · เมนูแอดมิน",NamedTextColor.DARK_PURPLE));
        for(var quality:com.example.advancemagic.mana.ManaPotionService.Quality.values()){
            ItemStack icon=magic.manaPotions().create(quality);var meta=icon.getItemMeta();
            meta.lore(List.of(Component.text("ซ้าย 1 · ขวา 4 · Shift 8 ขวด",NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false)));
            icon.setItemMeta(meta);inventory.setItem(10+quality.ordinal()*2,icon);
        }
        inventory.setItem(22,createActionItem(Material.ARROW,"§eกลับเมนูทดสอบ",List.of()));
        player.openInventory(inventory);
    }
    private void openBossTests(Player player){
        if(!isAdmin(player))return;
        Inventory inv=Bukkit.createInventory(new BossTestHolder(),27,Component.text("✦ World Boss Test",NamedTextColor.DARK_PURPLE,TextDecoration.BOLD));
        inv.setItem(0,createActionItem(Material.GOLDEN_SWORD,"§6§lเสกโมเดล Seraphon",List.of("§7ผู้พิพากษาหกปีก · เกราะทองลายสลัก · รัศมีสุริยะ", "§7Model Engine + Bedrock · วนท่าและออร่า · อยู่ได้ 10 นาที")));
        inv.setItem(1,createActionItem(Material.NETHER_STAR,"§d§lบอสหลัก Evergarden",List.of("§7เสก Arkhaia / Seraphon / ทั้งคู่", "§7เลือกท่า: เดิน · บิน · โจมตี · ร่าย · ท่าไม้ตาย · ชะงัก · เปลี่ยนเฟส")));
        inv.setItem(2,createActionItem(Material.NETHERITE_SWORD,"§5§lเสกโมเดล Arkhaia",List.of("§7จอมราชันกุหลาบทมิฬ · ปีกค้างคาว · ดาบรูนวิญญาณ", "§7Model Engine + Bedrock · วนท่าและออร่า · อยู่ได้ 10 นาที")));
        inv.setItem(7,createActionItem(Material.ECHO_SHARD,"§d§lลูกน้อง Arkhaia V2",List.of("§7เลือกดูโมเดลหรือทดสอบต่อสู้ ทั้งหกชนิด")));
        inv.setItem(6,createActionItem(Material.SHEARS,"§cลบโมเดล Seraphon / Arkhaia",List.of("§7ลบโมเดลบอสหลักที่คุณเสกไว้")));
        inv.setItem(4,createActionItem(Material.BOOK,"§e§lเลือกเฟสเพื่อเริ่มหรือรีเซ็ตไฟต์ทดสอบ",
            List.of("§7วาร์ปเข้าวิหารใหญ่และเข้า Survival ให้อัตโนมัติ", "§7คืนโหมดเดิมเมื่อจบหรือหยุดไฟต์ทดสอบ", "§7ไม่มีรางวัล · ไม่เปลี่ยนคูลดาวน์ · ไม่แทนที่ไฟต์จริง", "§aเฟส 3: ตีแกนได้ตลอด · ทำลายผลึกขัดท่าทั้งคู่เพื่อ ×3")));
        inv.setItem(10,createActionItem(Material.IRON_SWORD,"§f§lเริ่ม / รีเซ็ต เฟส 1",List.of("§7เริ่มพร้อมมือทั้งสอง · ทดสอบโบนัสมือแตก")));
        inv.setItem(11,createActionItem(Material.FEATHER,"§f§lเสกโมเดลเทพปีกขาว",List.of("§7ตัวอย่างเฉพาะบอส ใช้ Witch เป็นแกน", "§7ชุดขาวทอง · ปีกใหญ่ · ท่าร่ายเวทย์", "§7เสกด้านหน้า 4 บล็อก อยู่ได้ 10 นาที")));
        inv.setItem(9,createActionItem(Material.GOLD_BLOCK,"§6§lเรียกท่าล่อมือทันที",List.of("§7ใช้ในไฟต์ทดสอบเฟส 2", "§7เข้าผนึกทอง · วงล็อกแล้ววิ่งออก")));
        inv.setItem(12,createActionItem(Material.BOW,"§b§lเริ่ม / รีเซ็ต เฟส 2",List.of("§7ล่อมือทุบผนึกทอง หรือทุบมือเปิดแกน", "§7กันธนู แต่คทาใช้ได้")));
        inv.setItem(13,createActionItem(Material.SHEARS,"§cลบโมเดลเทพปีกขาว",List.of("§7ลบตัวอย่างที่คุณเสกไว้ทันที")));
        inv.setItem(14,createActionItem(Material.AMETHYST_BLOCK,"§d§lเริ่ม / รีเซ็ต เฟส 3",List.of("§7ข้ามเฟส 1–2 · แกนเปิดตลอด · ใช้อาวุธประชิด", "§7ทำลายผลึกขัดท่าทั้งคู่ หรือออกนอกวงแดง")));
        inv.setItem(15,createActionItem(Material.TOTEM_OF_UNDYING,"§c§lเรียกพิพากษาทันที · ทุกเฟส",List.of("§7ท่าฆ่าทะลุ Totem และระบบช่วยชีวิต", "§7เข้าวงเขียวให้ทัน 8 วินาที")));
        inv.setItem(16,createActionItem(Material.END_CRYSTAL,"§c§lเรียกท่าระเบิดแกนทันที",List.of("§7ใช้ในไฟต์ทดสอบเฟส 3", "§7เริ่มชาร์จใหม่ 8 วินาที พร้อมผลึกขัดท่า 2 จุด")));
        inv.setItem(18,createActionItem(Material.ARROW,"§eกลับ Evergarden Test",List.of("§7กลับไปเมนูอุปกรณ์ทดสอบ")));
        inv.setItem(20,createActionItem(Material.GOLDEN_APPLE,"§aฟื้นฟูเลือดและอาหาร",List.of("§7ฮีลระหว่างทดลองแมคคานิค")));
        inv.setItem(22,createActionItem(Material.REDSTONE_BLOCK,"§cหยุดไฟต์ทดสอบ",List.of("§7ล้างบอส บริวาร ผนึกและวงเตือน", "§7เริ่มทดสอบใหม่ได้ทันที")));
        inv.setItem(24,createActionItem(Material.DIAMOND_SWORD,"§bรับดาบฝึกขัดท่า",List.of("§7ดาบเพชรไม่แตก สำหรับทดสอบการทำลายผลึกขัดท่า")));
        inv.setItem(26,createActionItem(Material.BARRIER,"§cปิดเมนู",List.of("§7ไฟต์ทดสอบยังดำเนินต่อ")));
        player.openInventory(inv);
    }

    public void openMainBosses(Player player){
        if(!isAdmin(player))return;
        Inventory inv=Bukkit.createInventory(new MainBossHolder(),36,Component.text("บอสหลัก Evergarden",NamedTextColor.DARK_PURPLE,TextDecoration.BOLD));
        inv.setItem(4,createActionItem(Material.BOOK,"§eโมเดลบอสหลัก (เฟส 1: โมเดล ท่าทาง ออร่า)",List.of(
            "§7เสกหน้าคุณ 12 บล็อก · ต้องมีพื้นราบและที่ว่างสูง 10 บล็อก","§7ไม่โจมตี ไม่มีรางวัล · อยู่ได้ 10 นาที","§7Java: Model Engine · Bedrock: โมเดล Geyser ของจริง")));
        inv.setItem(10,createActionItem(Material.NETHERITE_SWORD,"§5§lเสก Arkhaia",List.of("§7Dreadrose Sovereign · ดาบรูน · เปลวไฟวิญญาณ")));
        inv.setItem(12,createActionItem(Material.GOLDEN_SWORD,"§6§lเสก Seraphon",List.of("§7Gilded Verdict · หกปีก · รัศมีสุริยะ")));
        inv.setItem(14,createActionItem(Material.NETHER_STAR,"§d§lเสกทั้งสองตัว",List.of("§7ยืนซ้าย-ขวา ห่างกัน 16 บล็อก")));
        inv.setItem(16,createActionItem(Material.SHEARS,"§cลบโมเดลของฉัน",List.of("§7ลบทั้ง Arkhaia และ Seraphon")));
        Material[] icons={Material.CLOCK,Material.ARMOR_STAND,Material.LEATHER_BOOTS,Material.ELYTRA,Material.IRON_SWORD,Material.BLAZE_POWDER,Material.DRAGON_HEAD,Material.ANVIL,Material.END_CRYSTAL};
        String[] labels={"§aวนโชว์ทุกท่า","§fยืน · หายใจ · หันมองผู้เล่น","§fเดิน / ร่อน","§fบิน · กระพือปีก","§fท่าโจมตี","§fท่าร่ายเวท","§fท่าไม้ตาย","§fท่าชะงัก","§fเปลี่ยนเฟส"};
        var modes=com.example.voidscape.boss.WorldBossManager.bossShowcaseModes();
        for(int i=0;i<modes.size();i++)inv.setItem(18+i,createActionItem(icons[i],labels[i],List.of("§7ใช้กับโมเดลบอสหลักที่คุณเสกไว้")));
        inv.setItem(27,createActionItem(Material.ARROW,"§eกลับ World Boss Test",List.of()));
        inv.setItem(35,createActionItem(Material.BARRIER,"§cปิดเมนู",List.of("§7โมเดลยังอยู่จนลบหรือครบ 10 นาที")));
        player.openInventory(inv);
    }

    public void openRiverTests(Player player){
        if(!isAdmin(player))return;
        Inventory inv=Bukkit.createInventory(new RiverTestHolder(),54,Component.text("Arkhaia V2 / Six Rivers",NamedTextColor.DARK_PURPLE));
        String[] names={"Acheron","Phlegethon","Cocytus","Banshee Wail","Malefeasance","Styx"};
        Material[] icons={Material.IRON_SWORD,Material.FURNACE,Material.BOW,Material.GHAST_TEAR,Material.ENCHANTED_BOOK,Material.NETHERITE_AXE};
        inv.setItem(4,createActionItem(Material.BOOK,"§eเลือกวิธีทดสอบ",List.of("§7แถวบน: ดูโมเดล ไม่โจมตีผู้เล่น","§7แถวที่สอง: ต่อสู้จริง เลือดตามชนิดบอส พร้อมสกิล","§7เลือกพื้นราบด้านหน้า / เข้า Survival เพื่อสู้","§7เมื่อเสกใหม่ ตัวทดสอบเดิมของคุณจะถูกลบ")));
        for(int i=0;i<6;i++){
            inv.setItem(10+i,createActionItem(icons[i],"§aดูโมเดล / "+names[i],List.of("§7ไม่โจมตี พร้อมแอนิเมชันและวงเวทย์ประจำตัว")));
            inv.setItem(19+i,createActionItem(icons[i],"§cต่อสู้ / "+names[i],List.of("§7เลือด "+(i==1?1000:i==3?500:800)+" / ใช้สกิลจริง","§7ไม่มีของดรอปหรือรางวัลบอส")));
        }
        inv.setItem(28,createActionItem(Material.ARMOR_STAND,"§aดูโมเดลครบหกตัว",List.of("§7ไม่โจมตี / ใช้พื้นที่ราบโล่ง 12 × 12 บล็อก")));
        inv.setItem(30,createActionItem(Material.BLAZE_POWDER,"§cต่อสู้ครบหกตัว",List.of("§7ใช้สกิลจริงพร้อมกันทั้งหกชนิด","§7เข้า Survival เพื่อเริ่มต่อสู้")));
        inv.setItem(32,createActionItem(Material.SHEARS,"§cลบตัวทดสอบของฉัน",List.of("§7ลบลูกน้อง โมเดล วงเวทย์ และดีบัฟของชุดทดสอบ")));
        Material[] poses={Material.ARMOR_STAND,Material.LEATHER_BOOTS,Material.AMETHYST_SHARD,Material.ANVIL};
        String[] labels={"ยืน / หันหัวตามผู้เล่น","ท่าเดินอยู่กับที่","วนท่าร่ายเวทย์","ท่าชะงัก"};
        for(int i=0;i<4;i++)inv.setItem(38+i,createActionItem(poses[i],labels[i],List.of("§7ใช้กับตัวที่เสกดูโมเดล")));
        inv.setItem(45,createActionItem(Material.ARROW,"§eกลับ Evergarden Test",List.of()));
        inv.setItem(53,createActionItem(Material.BARRIER,"§cปิดเมนู",List.of("§7ตัวทดสอบอยู่ต่อจนลบหรือออกจากพื้นที่")));
        player.openInventory(inv);
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
        attachUnique(meta, UniqueEnchant.THUNDERLORD);
        attachUnique(meta, UniqueEnchant.VAMPIRIC);

        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
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

        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.text("✦ Colossus Slayer · ล่าไททัน", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("   §7ยิงแรงขึ้นตาม % Max HP ของบอสและมอนสเตอร์").decoration(TextDecoration.ITALIC, false));
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
        attachUnique(meta, UniqueEnchant.TELEPATHY);

        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.text("✦ Seismic Slam · ขุดทลาย 3x3", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("   §7ขุด 1 ครั้งระเบิดเปิดโพรง 3×3×1 ทันที").decoration(TextDecoration.ITALIC, false));
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
