package com.example.advancemagic.item;

import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.item.ElementalStaffService.Element;
import com.example.advancemagic.spell.Spell;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;

/** Server-owned preview works on Java and Bedrock without creative reverse translation. */
public final class MagicItemMenu implements Listener {
    private final AdvanceMagicPlugin plugin;
    public MagicItemMenu(AdvanceMagicPlugin plugin){this.plugin=plugin;}
    private static final class Menu implements InventoryHolder {
        final boolean admin;final Inventory inventory;
        Menu(boolean admin){this.admin=admin;inventory=Bukkit.createInventory(this,27,admin?"Magic: Elemental Staffs (Admin)":"Magic: Elemental Staffs");}
        public Inventory getInventory(){return inventory;}
    }
    public void open(Player player,boolean admin){
        if(admin&&!player.hasPermission("advance-magic.admin")){player.sendMessage(ChatColor.RED+"No permission.");return;}
        Menu menu=new Menu(admin);
        for(Element e:Element.values())menu.inventory.setItem(e.ordinal(),plugin.staffSpells().createStaff(e,com.example.advancemagic.elemental.StaffSpellService.Tier.APPRENTICE));
        menu.inventory.setItem(8,plugin.staffSpells().createWoodenStaff());
        if(admin){
            menu.inventory.setItem(20,plugin.flyingStaff().create(2));
            menu.inventory.setItem(21,com.example.advancemagic.elemental.StaffGemService.create(com.example.advancemagic.elemental.StaffGemService.Gem.DAMAGE));
            menu.inventory.setItem(22,com.example.advancemagic.elemental.StaffGemService.create(com.example.advancemagic.elemental.StaffGemService.Gem.COOLDOWN));
            menu.inventory.setItem(23,com.example.advancemagic.elemental.StaffGemService.create(com.example.advancemagic.elemental.StaffGemService.Gem.MAX));
            menu.inventory.setItem(24,plugin.restoration().createCore());menu.inventory.setItem(25,plugin.restoration().createRepairWand());menu.inventory.setItem(26,plugin.flyingStaff().create());
        }
        player.openInventory(menu.inventory);
    }
    /** Compatibility API: retired crafting never consumes player ingredients. */
    public String craft(Player player,Spell spell){return "สูตรคราฟคทาชุดเดิมถูกยกเลิกแล้ว";}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void click(InventoryClickEvent event){
        if(!(event.getView().getTopInventory().getHolder() instanceof Menu menu))return;
        event.setCancelled(true);
        if(!menu.admin||!(event.getWhoClicked() instanceof Player player)||!player.hasPermission("advance-magic.admin")||!event.isLeftClick()||event.isShiftClick())return;
        int slot=event.getRawSlot();ItemStack output=null;
        if(slot>=0&&slot<Element.values().length)output=plugin.staffSpells().createStaff(Element.values()[slot],com.example.advancemagic.elemental.StaffSpellService.Tier.APPRENTICE);
        else if(slot==8)output=plugin.staffSpells().createWoodenStaff();
        else if(slot==20)output=plugin.flyingStaff().create(2);
        else if(slot==21)output=com.example.advancemagic.elemental.StaffGemService.create(com.example.advancemagic.elemental.StaffGemService.Gem.DAMAGE);
        else if(slot==22)output=com.example.advancemagic.elemental.StaffGemService.create(com.example.advancemagic.elemental.StaffGemService.Gem.COOLDOWN);
        else if(slot==23)output=com.example.advancemagic.elemental.StaffGemService.create(com.example.advancemagic.elemental.StaffGemService.Gem.MAX);
        else if(slot==24)output=plugin.restoration().createCore();
        else if(slot==25)output=plugin.restoration().createRepairWand();
        else if(slot==26)output=plugin.flyingStaff().create();
        if(output==null)return;
        if(!player.getInventory().addItem(output).isEmpty())player.sendMessage(ChatColor.RED+"Inventory full.");
        Bukkit.getScheduler().runTask(plugin,player::updateInventory);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof Menu)event.setCancelled(true);}
}
