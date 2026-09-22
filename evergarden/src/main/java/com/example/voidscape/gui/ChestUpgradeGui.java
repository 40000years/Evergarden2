package com.example.voidscape.gui;

import com.example.voidscape.VoidscapePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class ChestUpgradeGui implements Listener {
    private final VoidscapePlugin plugin;

    public static final int SLOT_EQUIP = 11;
    public static final int SLOT_BUTTON = 13;
    public static final int SLOT_SCROLL = 15;

    public ChestUpgradeGui(VoidscapePlugin plugin) {
        this.plugin = plugin;
    }

    public static final class UpgradeHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    public static void open(VoidscapePlugin plugin, Player player) {
        UpgradeHolder holder = new UpgradeHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text("✦ แท่นปลุกเสกมนตรา Evergarden", NamedTextColor.GOLD));
        holder.setInventory(inv);

        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, Component.text(" ", NamedTextColor.GRAY), null);
        for (int i = 0; i < 27; i++) {
            if (i != SLOT_EQUIP && i != SLOT_BUTTON && i != SLOT_SCROLL) {
                inv.setItem(i, border);
            }
        }

        // Info item in slot 4
        inv.setItem(4, createItem(Material.NETHER_STAR,
            Component.text("✦ คำแนะนำการใช้งาน ✦", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
            List.of(
                Component.text("1. วางอาวุธ ชุดเกราะ หรือเครื่องมือในช่องซ้าย", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("2. วางคัมภีร์หรือศิลาในช่องขวา", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("3. กดปุ่มตรงกลางเพื่อทำการปลุกเสกมนตรา!", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false)
            )
        ));

        updateButton(plugin, inv);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.4f);
    }

    private static void updateButton(VoidscapePlugin plugin, Inventory inv) {
        ItemStack equip = inv.getItem(SLOT_EQUIP);
        ItemStack scroll = inv.getItem(SLOT_SCROLL);

        if (equip == null || equip.getType().isAir() || scroll == null || scroll.getType().isAir()) {
            inv.setItem(SLOT_BUTTON, createItem(Material.GRAY_DYE,
                Component.text("✦ กรุณาวางอุปกรณ์และคัมภีร์ ✦", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                List.of(Component.text("วางอุปกรณ์ในช่องซ้าย และคัมภีร์ในช่องขวา", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
            ));
            return;
        }

        // Evaluate upgrade
        ItemStack preview = evaluateUpgrade(plugin, scroll, equip);
        if (preview != null) {
            inv.setItem(SLOT_BUTTON, createItem(Material.LIME_DYE,
                Component.text("✦ คลิกเพื่อปลุกเสกมนตรา! ✦", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                List.of(
                    Component.text("อัตราสำเร็จ: 100%", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                    Component.text("คลิกเพื่ออัปเกรดอุปกรณ์ทันที", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
                )
            ));
        } else {
            inv.setItem(SLOT_BUTTON, createItem(Material.BARRIER,
                Component.text("✦ คัมภีร์ไม่สามารถใช้กับอุปกรณ์นี้ได้ ✦", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                List.of(Component.text("โปรดตรวจสอบความเข้ากันได้ของอุปกรณ์", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
            ));
        }
    }

    private static ItemStack evaluateUpgrade(VoidscapePlugin plugin, ItemStack scroll, ItemStack equip) {
        if (scroll == null || equip == null || equip.getType().getMaxDurability() <= 0) return null;

        // Repair stone special case
        if (plugin.relics().isRepairStone(scroll)) {
            if (equip.hasItemMeta() && equip.getItemMeta() instanceof Damageable dmg && dmg.getDamage() > 0) {
                ItemStack res = equip.clone();
                Damageable resDmg = (Damageable) res.getItemMeta();
                resDmg.setDamage(Math.max(0, resDmg.getDamage() - 500));
                res.setItemMeta(resDmg);
                return res;
            }
            return null;
        }

        return plugin.relics().evaluateScrollCraft(scroll, equip);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof UpgradeHolder)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;

        int rawSlot = e.getRawSlot();

        // Click inside top inventory
        if (rawSlot < 27) {
            if (rawSlot != SLOT_EQUIP && rawSlot != SLOT_SCROLL) {
                e.setCancelled(true);

                // Handle action button
                if (rawSlot == SLOT_BUTTON) {
                    handleUpgrade(p, e.getInventory());
                }
                return;
            }
        }

        // Delay 1 tick to refresh action button status
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() instanceof UpgradeHolder) {
                updateButton(plugin, p.getOpenInventory().getTopInventory());
            }
        });
    }

    private void handleUpgrade(Player p, Inventory inv) {
        ItemStack equip = inv.getItem(SLOT_EQUIP);
        ItemStack scroll = inv.getItem(SLOT_SCROLL);

        ItemStack result = evaluateUpgrade(plugin, scroll, equip);
        if (result == null) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        // Consume 1 scroll
        if (scroll.getAmount() <= 1) {
            inv.setItem(SLOT_SCROLL, null);
        } else {
            scroll.setAmount(scroll.getAmount() - 1);
            inv.setItem(SLOT_SCROLL, scroll);
        }

        // Update equipment in slot
        inv.setItem(SLOT_EQUIP, result);

        // Sound & particles
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.25f);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.35f);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1.2, 0), 25, 0.35, 0.35, 0.35, 0.1);
        p.getWorld().spawnParticle(Particle.FIREWORK, p.getLocation().add(0, 1.2, 0), 12, 0.25, 0.25, 0.25, 0.05);
        p.sendActionBar(Component.text("✦ ปลุกเสกมนตราสำเร็จ!", NamedTextColor.GREEN));

        updateButton(plugin, inv);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof UpgradeHolder)) return;
        if (!(e.getPlayer() instanceof Player p)) return;

        Inventory inv = e.getInventory();
        ItemStack equip = inv.getItem(SLOT_EQUIP);
        ItemStack scroll = inv.getItem(SLOT_SCROLL);

        if (equip != null && !equip.getType().isAir()) {
            var leftover = p.getInventory().addItem(equip);
            leftover.values().forEach(drop -> p.getWorld().dropItemNaturally(p.getLocation(), drop));
        }
        if (scroll != null && !scroll.getType().isAir()) {
            var leftover = p.getInventory().addItem(scroll);
            leftover.values().forEach(drop -> p.getWorld().dropItemNaturally(p.getLocation(), drop));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof UpgradeHolder)) return;
        for (int slot : e.getRawSlots()) {
            if (slot < 27 && slot != SLOT_EQUIP && slot != SLOT_SCROLL) {
                e.setCancelled(true);
                return;
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> updateButton(plugin, e.getInventory()));
    }

    private static ItemStack createItem(Material mat, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            if (lore != null) meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
