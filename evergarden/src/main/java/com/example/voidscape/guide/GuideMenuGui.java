package com.example.voidscape.guide;

import com.example.voidscape.VoidscapePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class GuideMenuGui implements InventoryHolder, Listener {
    private final VoidscapePlugin plugin;
    private Inventory inventory;

    public GuideMenuGui(VoidscapePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open(Player player) {
        if (player == null || !player.isOnline()) return;

        inventory = Bukkit.createInventory(this, 27, Component.text("เลือกคู่มือมิติ Evergarden", NamedTextColor.DARK_BLUE));

        // Background border
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 27; i++) {
            if (i < 9 || i >= 18 || i % 9 == 0 || i % 9 == 8) {
                inventory.setItem(i, border);
            }
        }

        // Header info item
        inventory.setItem(4, createItem(Material.BOOK, "§6§l✦ ชุดคู่มือแนะนำการเล่น Evergarden ✦", List.of(
            "§7ระบบถูกแบ่งออกเป็น 2 เล่มเพื่อความสะดวกในการอ่าน",
            "§eคลิกซ้าย: §fเปิดอ่านสมุดเสมือนทันที",
            "§6Shift+คลิก: §fรับสมุดเล่มจริงเข้าช่องเก็บของ"
        )));

        // Slot 13: Book 2 - Relics & Sanctum
        inventory.setItem(13, createBookIcon(
            Material.NETHERITE_SWORD,
            "§6§lเล่มที่ 2: " + GuideBookType.RELICS.bookTitle,
            List.of(
                "§7" + GuideBookType.RELICS.englishTitle,
                GuideBookType.RELICS.description,
                "§8- 3 มหาวิหาร (ความมืด, ดวงดาว, กาลเวลา)",
                "§8- คำสาป True Death, นมแก้คำสาป, ใยแมงมุม 20%",
                "\u00a78- \u0e40\u0e04\u0e23\u0e37\u0e48\u0e2d\u0e07\u0e21\u0e37\u0e2d\u0e42\u0e1a\u0e23\u0e32\u0e13 2 ชิ้น (3x3 และหลอมแร่)",
                "§8- Scroll of Eternity [MYTHIC] และ Limit Break",
                "§8- ตารางสุ่ม Evergarden Vault ปรับปรุงใหม่",
                "§8- Whale, Garden, Observatory และสมบัติลอยฟ้า",
                "",
                "§e▶ คลิกซ้ายเพื่อเปิดอ่าน §8| §6Shift+คลิกเพื่อรับสมุด"
            )
        ));

        // Slot 15: Book 3 - Magic
        inventory.setItem(15, createBookIcon(
            Material.BLAZE_ROD,
            "§b§lเล่มที่ 3: " + GuideBookType.MAGIC.bookTitle,
            List.of(
                "§7" + GuideBookType.MAGIC.englishTitle,
                GuideBookType.MAGIC.description,
                "§8- โมเดลคทา 8 ธาตุ /magic staffs",
                "§8- ข้อมูลคทาและ Core เดิมยังเก็บไว้",
                "§8- ระบบมานาและแท่นฟื้นฟู",
                "§8- Wand of Restoration และไม้เท้าบิน",
                "",
                "§e▶ คลิกซ้ายเพื่อเปิดอ่าน §8| §6Shift+คลิกเพื่อรับสมุด"
            )
        ));

        // Slot 22: Close
        inventory.setItem(22, createItem(Material.BARRIER, "§c❌ ปิดเมนู", List.of("§7คลิกเพื่อปิดหน้าต่างนี้")));

        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuideMenuGui)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;

        int slot = e.getRawSlot();
        if (slot < 0 || slot >= e.getInventory().getSize()) return;

        GuideBookType selectedType = switch (slot) {
            case 13 -> GuideBookType.RELICS;
            case 15 -> GuideBookType.MAGIC;
            default -> null;
        };

        if (slot == 22) {
            player.closeInventory();
            return;
        }

        if (selectedType != null) {
            if (e.isShiftClick()) {
                ItemStack book = plugin.relics().createGuideBook(selectedType);
                var left = player.getInventory().addItem(book);
                if (!left.isEmpty()) {
                    left.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                }
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
                plugin.message(player, "§a✦ ได้รับ '" + selectedType.bookTitle + "' เรียบร้อยแล้ว!");
            } else {
                player.closeInventory();
                BedrockGuideService.openGuide(plugin, player, selectedType, 0);
            }
        }
    }

    private static ItemStack createBookIcon(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            if (lore != null) {
                List<Component> loreList = new ArrayList<>();
                for (String s : lore) loreList.add(LegacyComponentSerializer.legacySection().deserialize(s));
                meta.lore(loreList);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            if (lore != null) {
                List<Component> loreList = new ArrayList<>();
                for (String s : lore) loreList.add(LegacyComponentSerializer.legacySection().deserialize(s));
                meta.lore(loreList);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
