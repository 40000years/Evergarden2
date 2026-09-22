package com.example.advancemagic.item;

import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.spell.Spell;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import java.util.*;

/** Server-owned chest controls work without Bedrock creative-item reverse translation. */
public final class MagicItemMenu implements Listener {
    private final AdvanceMagicPlugin plugin;
    public MagicItemMenu(AdvanceMagicPlugin plugin){this.plugin=plugin;}
    private static final class Menu implements InventoryHolder {
        final boolean admin;
        final Inventory inventory;
        Menu(boolean admin) {
            this.admin=admin;
            inventory=Bukkit.createInventory(this,36,admin?"Magic: Wands / Cores":"Magic: Craft a Wand");
        }
        public Inventory getInventory(){return inventory;}
    }
    public void open(Player player,boolean admin) {
        if(admin&&!player.hasPermission("advance-magic.admin")) {
            player.sendMessage(ChatColor.RED+"No permission.");return;
        }
        Menu menu=new Menu(admin);
        for(Spell spell:Spell.values()) {
            ItemStack wand=plugin.wands().create(spell);
            var meta=wand.getItemMeta();var lore=new ArrayList<>(meta.getLore());
            lore.add(ChatColor.YELLOW+(admin?"แตะเพื่อรับคทา":"แตะเพื่อคราฟ: Core ธาตุนี้ 1 + Netherite/Nether Star 8"));
            meta.setLore(lore);wand.setItemMeta(meta);
            menu.inventory.setItem(spell.ordinal(),wand);
            if(admin)menu.inventory.setItem(18+spell.ordinal(),plugin.wands().createCore(spell));
        }
        player.openInventory(menu.inventory);
    }
    /** Plan against clones first: failed crafts never consume ingredients or drop the result. */
    public String craft(Player player,Spell spell) {
        if(!plugin.wands().canCraft(player))return "คุณไม่มีสิทธิ์คราฟคทา";
        ItemStack[] contents=Arrays.stream(player.getInventory().getStorageContents())
            .map(item->item==null?null:item.clone()).toArray(ItemStack[]::new);
        int core=-1;
        for(int i=0;i<contents.length;i++)if(plugin.wands().coreSpell(contents[i])==spell){core=i;break;}
        if(core<0)return "ต้องมี Core ของ "+spell.title+" ในกระเป๋า";
        int needed=8;
        for(int i=0;i<contents.length&&needed>0;i++) {
            ItemStack item=contents[i];
            if(item==null||(item.getType()!=Material.NETHERITE_INGOT&&item.getType()!=Material.NETHER_STAR))continue;
            int take=Math.min(needed,item.getAmount());needed-=take;
            if(take==item.getAmount())contents[i]=null;else item.setAmount(item.getAmount()-take);
        }
        if(needed>0)return "ต้องมี Netherite Ingot หรือ Nether Star รวม 8 ชิ้น (ผสมได้)";
        if(contents[core].getAmount()==1)contents[core]=null;
        else contents[core].setAmount(contents[core].getAmount()-1);
        int output=-1;
        for(int i=0;i<contents.length;i++)if(contents[i]==null||contents[i].getType().isAir()){output=i;break;}
        if(output<0)return "กระเป๋าเต็ม กรุณาเว้นที่ให้คทา 1 ช่อง";
        contents[output]=plugin.wands().create(spell);
        player.getInventory().setStorageContents(contents);
        return null;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void click(InventoryClickEvent event) {
        if(!(event.getView().getTopInventory().getHolder() instanceof Menu menu))return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player player)||!event.isLeftClick()||event.isShiftClick())return;
        int slot=event.getRawSlot();
        if(slot<0||slot>=menu.inventory.getSize())return;
        boolean core=slot>=18;
        int index=core?slot-18:slot;
        if(index>=Spell.values().length||(!menu.admin&&core))return;
        Spell spell=Spell.values()[index];
        if(menu.admin) {
            if(!player.hasPermission("advance-magic.admin"))return;
            ItemStack item=core?plugin.wands().createCore(spell):plugin.wands().create(spell);
            var left=player.getInventory().addItem(item);
            if(!left.isEmpty())player.sendMessage(ChatColor.RED+"กระเป๋าเต็ม กรุณาเว้นที่ 1 ช่อง");
        }else {
            String failure=craft(player,spell);
            player.sendMessage((failure==null?ChatColor.GREEN:ChatColor.RED)+(failure==null?"คราฟ "+spell.title+" Wand สำเร็จ":failure));
        }
        Bukkit.getScheduler().runTask(plugin,player::updateInventory);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if(event.getView().getTopInventory().getHolder() instanceof Menu)event.setCancelled(true);
    }
}
