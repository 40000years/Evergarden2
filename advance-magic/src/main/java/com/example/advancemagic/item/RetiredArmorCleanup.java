package com.example.advancemagic.item;

import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Remove retired cosmetic references without discarding players' equipment. */
public final class RetiredArmorCleanup implements Listener {
    private static final NamespacedKey TAG = new NamespacedKey("advance-magic", "legendary_armor");
    public static void clean(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        var meta = item.getItemMeta();
        var model = meta.getItemModel();
        if (!meta.getPersistentDataContainer().has(TAG)
                && (model == null || !model.getNamespace().equals("advance_magic")
                || !model.getKey().startsWith("armor_"))) return;
        meta.setItemModel(null);
        var equipment = meta.getEquippable();
        equipment.setModel(NamespacedKey.minecraft("netherite"));
        meta.setEquippable(equipment);
        meta.getPersistentDataContainer().remove(TAG);
        item.setItemMeta(meta);
    }
    public static void clean(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            var item = inventory.getItem(slot);
            clean(item);
            inventory.setItem(slot, item);
        }
    }
    @EventHandler public void join(PlayerJoinEvent event) {
        clean(event.getPlayer().getInventory());
        clean(event.getPlayer().getEnderChest());
    }
    @EventHandler public void open(InventoryOpenEvent event) { clean(event.getInventory()); }
    @EventHandler public void pickup(EntityPickupItemEvent event) {
        var item = event.getItem().getItemStack();
        clean(item);
        event.getItem().setItemStack(item);
    }
}
