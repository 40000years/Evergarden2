package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import java.util.List;
import java.util.Locale;

/** One shared trophy per main-boss victory, exchanged for one chosen armor piece. */
public final class BossArmorCores implements Listener {
    public enum Core {
        SERAPH("Core of Seraph",Material.NETHER_STAR,0xffdf8a),
        REVENANT("Core of Revenant",Material.ECHO_SHARD,0xff6666);
        public final String title;
        final Material material;
        final int color;
        Core(String title,Material material,int color){this.title=title;this.material=material;this.color=color;}
        public String id(){return name().toLowerCase(Locale.ROOT);}
        public NamespacedKey model(){return new NamespacedKey("voidscape","core_of_"+id());}
    }
    private final VoidscapePlugin plugin;
    public BossArmorCores(VoidscapePlugin plugin){this.plugin=plugin;}

    public static ItemStack create(VoidscapePlugin plugin,Core core){
        ItemStack item=new ItemStack(core.material);var meta=item.getItemMeta();
        meta.displayName(Component.text(core.title,TextColor.color(core.color)).decoration(TextDecoration.ITALIC,false));
        meta.lore(List.of(Component.text("Exchange at Seraph for one "+(core==Core.SERAPH?"Seraph":"Revenant")+" armor piece",TextColor.color(0xaaaaaa))
            .decoration(TextDecoration.ITALIC,false)));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(plugin.key("boss_armor_core"),PersistentDataType.STRING,core.id());
        item.setItemMeta(meta);migrate(plugin,item);return item;
    }
    public static Core identify(VoidscapePlugin plugin,ItemStack item){
        if(item==null||!item.hasItemMeta())return null;
        String id=item.getItemMeta().getPersistentDataContainer().get(plugin.key("boss_armor_core"),PersistentDataType.STRING);
        for(Core core:Core.values())if(core.id().equals(id)&&item.getType()==core.material)return core;
        return null;
    }
    /** Add the texture to previously awarded cores without changing their identity or quantity. */
    public static boolean migrate(VoidscapePlugin plugin,ItemStack item){
        Core core=identify(plugin,item);if(core==null)return false;
        var meta=item.getItemMeta();var data=meta.getCustomModelDataComponent();
        List<String> selector=List.of(core.model().toString());
        if(core.model().equals(meta.getItemModel())&&selector.equals(data.getStrings()))return false;
        meta.setItemModel(core.model());data.setStrings(selector);meta.setCustomModelDataComponent(data);
        item.setItemMeta(meta);return true;
    }
    private static void migrateInventory(VoidscapePlugin plugin,Inventory inventory){
        for(int slot=0;slot<inventory.getSize();slot++){ItemStack item=inventory.getItem(slot);if(migrate(plugin,item))inventory.setItem(slot,item);}
    }
    public static void migratePlayer(VoidscapePlugin plugin,Player player){
        migrateInventory(plugin,player.getInventory());
        ItemStack cursor=player.getItemOnCursor();if(migrate(plugin,cursor))player.setItemOnCursor(cursor);
    }
    @EventHandler public void join(PlayerJoinEvent event){migratePlayer(plugin,event.getPlayer());migrateInventory(plugin,event.getPlayer().getEnderChest());}
    @EventHandler(ignoreCancelled=true) public void open(InventoryOpenEvent event){
        migrateInventory(plugin,event.getInventory());if(event.getPlayer() instanceof Player player)migratePlayer(plugin,player);
    }
    /** Virtual bosses bypass native mob drops, so Looting cannot multiply this core. */
    public static Item drop(VoidscapePlugin plugin,Core core,Location center){
        Item item=center.getWorld().dropItem(center.clone().add(0,.6,0),create(plugin,core));
        item.setGlowing(true);item.setInvulnerable(true);item.setVelocity(new Vector(0,.18,0));
        center.getWorld().spawnParticle(Particle.END_ROD,center.clone().add(0,1,0),28,.5,.7,.5,.05);
        center.getWorld().playSound(center,Sound.UI_TOAST_CHALLENGE_COMPLETE,1,1.15f);
        return item;
    }
    @EventHandler public void craft(PrepareItemCraftEvent event){
        for(ItemStack ingredient:event.getInventory().getMatrix())if(identify(plugin,ingredient)!=null){event.getInventory().setResult(null);return;}
    }
    @EventHandler(ignoreCancelled=true) public void autocraft(CrafterCraftEvent event){
        if(event.getBlock().getState() instanceof org.bukkit.block.Crafter crafter)
            for(ItemStack ingredient:crafter.getInventory().getContents())if(identify(plugin,ingredient)!=null){event.setCancelled(true);return;}
    }
}
