package com.example.voidscape.gui;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.enchant.LegacyEnchantResolution;
import com.example.voidscape.enchant.UniqueCompatibility;
import com.example.voidscape.enchant.UniqueEnchant;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class LegacyEnchantResolutionGui implements Listener {
    private final VoidscapePlugin plugin;
    public LegacyEnchantResolutionGui(VoidscapePlugin plugin) { this.plugin=plugin; }

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private final ItemStack expected;
        private final List<String> choices;
        private Holder(ItemStack expected,List<String> choices) { this.expected=expected;this.choices=choices; }
        @Override public Inventory getInventory() { return inventory; }
    }

    public static boolean open(VoidscapePlugin plugin,Player player) {
        ItemStack held=player.getInventory().getItemInMainHand();
        List<String> choices=UniqueCompatibility.choices(held,plugin.relics());
        if(choices.isEmpty())return false;
        Holder holder=new Holder(held.clone(),List.copyOf(choices));
        Inventory inv=Bukkit.createInventory(holder,27,Component.text("เลือกมนตราที่จะเก็บไว้",NamedTextColor.GOLD));
        holder.inventory=inv;
        ItemStack info=new ItemStack(Material.BOOK);
        ItemMeta infoMeta=info.getItemMeta();
        infoMeta.displayName(Component.text("เลือกพลังของอุปกรณ์ในมือ",NamedTextColor.YELLOW));
        infoMeta.lore(List.of(Component.text("มนตราที่ขัดกันจะถูกถอดและคืนเป็นคัมภีร์",NamedTextColor.GRAY),
            Component.text("ต้องมีช่องว่างพอรับคัมภีร์คืน",NamedTextColor.GRAY)));
        info.setItemMeta(infoMeta);inv.setItem(4,info);
        for(int i=0;i<choices.size();i++) {
            String choice=choices.get(i);
            ItemStack icon;
            if(choice.equals("eternity"))icon=plugin.relics().createScrollEternity();
            else if(choice.equals("relic"))icon=new ItemStack(held.getType());
            else icon=plugin.relics().createScrollUnique(UniqueEnchant.parse(choice));
            ItemMeta meta=icon.getItemMeta();
            if(choice.equals("relic"))meta.displayName(Component.text("เก็บพลังประจำยุทธภัณฑ์",NamedTextColor.GOLD));
            meta.lore(List.of(Component.text("คลิกเพื่อเก็บพลังนี้ แล้วรับคัมภีร์ที่ขัดกันคืน",NamedTextColor.GREEN)));
            icon.setItemMeta(meta);
            inv.setItem(10+i,icon);
        }
        player.openInventory(inv);
        return true;
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if(!(event.getView().getTopInventory().getHolder() instanceof Holder holder))return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player player))return;
        int choiceIndex=event.getRawSlot()-10;
        if(choiceIndex<0||choiceIndex>=holder.choices.size())return;
        if(!LegacyEnchantResolution.resolveHeld(plugin,player,holder.expected,holder.choices.get(choiceIndex)))return;
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin,()->{if(player.isOnline())UpgradeMenuService.open(plugin,player);});
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if(event.getView().getTopInventory().getHolder() instanceof Holder)event.setCancelled(true);
    }
}
