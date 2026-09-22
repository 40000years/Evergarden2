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
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ChestGuideGui implements Listener {
    private final VoidscapePlugin plugin;

    public ChestGuideGui(VoidscapePlugin plugin) {
        this.plugin = plugin;
    }

    public static final class GuideHolder implements InventoryHolder {
        private final GuideBookType type;
        private final int pageIndex;
        private final boolean isIndex;
        private final int returnPageIndex;
        private Inventory inventory;

        public GuideHolder(GuideBookType type, int pageIndex, boolean isIndex, int returnPageIndex) {
            this.type = type != null ? type : GuideBookType.CROPS;
            this.pageIndex = pageIndex;
            this.isIndex = isIndex;
            this.returnPageIndex = returnPageIndex;
        }

        public GuideBookType getType() { return type; }
        public int getPageIndex() { return pageIndex; }
        public boolean isIndex() { return isIndex; }
        public int getReturnPageIndex() { return returnPageIndex; }

        @Override
        public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
    }

    public static void open(VoidscapePlugin plugin, Player player, GuideBookType type, int pageIndex) {
        if (type == null) type = GuideBookType.CROPS;
        List<GuidePage> pages = GuideData.pagesFor(type);
        if (pageIndex < 0) pageIndex = 0;
        if (pageIndex >= pages.size()) pageIndex = pages.size() - 1;

        GuidePage page = pages.get(pageIndex);
        int totalPages = pages.size();

        GuideHolder holder = new GuideHolder(type, pageIndex, false, pageIndex);
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text(type.bookTitle + " (" + (pageIndex + 1) + "/" + totalPages + ")", NamedTextColor.DARK_BLUE));
        holder.setInventory(inv);

        // Fill borders with dark glass pane
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 27; i++) {
            if (i < 9 || i >= 18 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, border);
            }
        }

        // Slot 0: Switch book menu
        inv.setItem(0, createItem(Material.ENCHANTED_BOOK, "§e📚 เลือกคู่มือเล่มอื่น (3 เล่ม)", List.of("§7คลิกเพื่อกลับไปหน้าเลือกคู่มือ")));

        // Center: Book reading item (Slot 13)
        List<String> lore = new ArrayList<>();
        for (String line : page.content().split("\n")) {
            String formatted = line
                    .replace("§0", "§f")
                    .replace("§8", "§7")
                    .replace("§1", "§b")
                    .replace("§2", "§a")
                    .replace("§4", "§c")
                    .replace("§5", "§d")
                    .replace("§r", "§r§f");
            if (!formatted.isEmpty() && !formatted.startsWith("§")) {
                formatted = "§f" + formatted;
            }
            lore.add(formatted);
        }
        inv.setItem(13, createItem(Material.WRITTEN_BOOK, "§e§l" + page.title(), lore));

        // Previous button (Slot 11)
        if (pageIndex > 0) {
            inv.setItem(11, createItem(Material.ARROW, "§a⬅️ หน้าก่อนหน้า (§f" + pageIndex + "§a/" + totalPages + ")", List.of("§7คลิกเพื่อย้อนกลับ")));
        }

        // Next button (Slot 15)
        if (pageIndex < totalPages - 1) {
            inv.setItem(15, createItem(Material.ARROW, "§a➡️ หน้าถัดไป (§f" + (pageIndex + 2) + "§a/" + totalPages + ")", List.of("§7คลิกเพื่อเปิดหน้าถัดไป")));
        }

        // Chapter selector (Slot 4)
        inv.setItem(4, createItem(Material.COMPASS, "§b📑 สารบัญหัวข้อ", List.of("§7คลิกเพื่อเลือกหน้าที่ต้องการอ่าน")));

        // Close button (Slot 22)
        inv.setItem(22, createItem(Material.BARRIER, "§c❌ ปิดคู่มือ", List.of("§7คลิกเพื่อปิด")));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f);
    }

    public static void open(VoidscapePlugin plugin, Player player, int pageIndex) {
        open(plugin, player, GuideBookType.CROPS, pageIndex);
    }

    public static void openIndex(VoidscapePlugin plugin, Player player, GuideBookType type, int returnPageIndex) {
        if (type == null) type = GuideBookType.CROPS;
        List<GuidePage> pages = GuideData.pagesFor(type);
        GuideHolder holder = new GuideHolder(type, returnPageIndex, true, returnPageIndex);
        Inventory inv = Bukkit.createInventory(holder, 54, Component.text("สารบัญ: " + type.bookTitle, NamedTextColor.DARK_BLUE));
        holder.setInventory(inv);

        for (int i = 0; i < pages.size() && i < 36; i++) {
            String prefix = (i == returnPageIndex) ? "§6▶ " : "§9";
            inv.setItem(i, createItem(Material.BOOK, prefix + (i + 1) + ". " + pages.get(i).title(), List.of("§7คลิกเพื่ออ่านหน้านี้")));
        }

        // Bottom navigation
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 36; i < 54; i++) {
            inv.setItem(i, border);
        }

        inv.setItem(45, createItem(Material.ARROW, "§e⬅️ กลับไปหน้าที่อ่านค้างไว้", List.of("§7หน้า " + (returnPageIndex + 1))));
        inv.setItem(47, createItem(Material.ENCHANTED_BOOK, "§e📚 เลือกคู่มือเล่มอื่น", List.of("§7คลิกเพื่อกลับไปหน้าเลือกคู่มือ 3 เล่ม")));
        inv.setItem(49, createItem(Material.BARRIER, "§c❌ ปิด", null));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f);
    }

    public static void openIndex(VoidscapePlugin plugin, Player player, int returnPageIndex) {
        openIndex(plugin, player, GuideBookType.CROPS, returnPageIndex);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuideHolder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;

        int slot = e.getRawSlot();
        if (slot < 0 || slot >= e.getInventory().getSize()) return;

        GuideBookType type = holder.getType();
        List<GuidePage> pages = GuideData.pagesFor(type);

        if (holder.isIndex()) {
            if (slot >= 0 && slot < pages.size()) {
                open(plugin, player, type, slot);
            } else if (slot == 45) {
                open(plugin, player, type, holder.getReturnPageIndex());
            } else if (slot == 47) {
                plugin.guideMenu().open(player);
            } else if (slot == 49) {
                player.closeInventory();
            }
        } else {
            if (slot == 0) {
                plugin.guideMenu().open(player);
            } else if (slot == 11 && holder.getPageIndex() > 0) {
                open(plugin, player, type, holder.getPageIndex() - 1);
            } else if (slot == 15 && holder.getPageIndex() < pages.size() - 1) {
                open(plugin, player, type, holder.getPageIndex() + 1);
            } else if (slot == 4) {
                openIndex(plugin, player, type, holder.getPageIndex());
            } else if (slot == 22) {
                player.closeInventory();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof GuideHolder) {
            e.setCancelled(true);
        }
    }

    private static ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) meta.displayName(Component.text(name));
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
