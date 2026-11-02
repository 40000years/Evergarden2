package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.util.List;

/**
 * The textured core each realm boss leaves at its arena. The Seraph buys one for five Golden Seraph
 * Feathers. Earlier untextured "Soul Orb" drops carry the same marker and are upgraded in place.
 */
public final class RealmCores implements Listener {
    private final VoidscapePlugin plugin;
    public RealmCores(VoidscapePlugin plugin){this.plugin=plugin;}

    public static NamespacedKey model(RealmBosses.Kind kind){return new NamespacedKey("voidscape","realm_core_"+kind.model);}
    public static String title(RealmBosses.Kind kind){return "Core of "+kind.title;}
    static int color(RealmBosses.Kind kind){
        return switch(kind){case CINDER_COLOSSUS->0xff7a2f;case SOUL_HOUND->0x52e5ff;case CRYSTAL_MOTH->0xf28cff;case UNLIT_SUN->0xffd257;};
    }
    private static String essence(RealmBosses.Kind kind){
        return switch(kind){
            case CINDER_COLOSSUS->"แก่นเตาหลอมที่ยังคุไฟอยู่ใต้หินมงกุฎ";
            case SOUL_HOUND->"เปลวไฟวิญญาณที่ซี่โครงหมาป่ากักไว้";
            case CRYSTAL_MOTH->"ผลึกหัวใจของราชินีในกรอบปีกกระจกสี";
            case UNLIT_SUN->"ดวงตาของดวงอาทิตย์ดับในเปลือกออบซิเดียน";
        };
    }

    public static ItemStack create(VoidscapePlugin plugin,RealmBosses.Kind kind){
        ItemStack item=new ItemStack(Material.HEART_OF_THE_SEA);decorate(plugin,item,kind);return item;
    }
    private static void decorate(VoidscapePlugin plugin,ItemStack item,RealmBosses.Kind kind){
        var meta=item.getItemMeta();
        meta.displayName(Component.text(title(kind),TextColor.color(color(kind))).decoration(TextDecoration.ITALIC,false));
        meta.lore(List.of(
            Component.text(essence(kind),NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false),
            Component.text("รางวัลจากการพิชิต "+kind.title+" ("+kind.home+")",NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC,false),
            Component.text("แลกเป็นขนนกเซราฟทองคำ 5 อันกับ Seraph ที่จุดเกิด Evergarden",NamedTextColor.GOLD).decoration(TextDecoration.ITALIC,false)));
        meta.setItemModel(model(kind));
        var data=meta.getCustomModelDataComponent();data.setStrings(List.of(model(kind).toString()));meta.setCustomModelDataComponent(data);
        meta.setEnchantmentGlintOverride(true);
        // An anvil rename leaves a repair cost; drop it so a renamed core still matches the Seraph's trade.
        if(meta instanceof org.bukkit.inventory.meta.Repairable repairable)repairable.setRepairCost(0);
        meta.getPersistentDataContainer().set(plugin.key("realm_boss_orb"),PersistentDataType.STRING,kind.model);
        item.setItemMeta(meta);
    }
    /** Only the PDC marker counts, so a renamed Heart of the Sea never pays. */
    public static RealmBosses.Kind identify(VoidscapePlugin plugin,ItemStack item){
        if(item==null||item.getType()!=Material.HEART_OF_THE_SEA||!item.hasItemMeta())return null;
        String id=item.getItemMeta().getPersistentDataContainer().get(plugin.key("realm_boss_orb"),PersistentDataType.STRING);
        return id==null?null:RealmBosses.Kind.of(id);
    }
    /**
     * Rewrites an older Soul Orb drop (or a core from an earlier text revision) to the current core, keeping its
     * identity and stack size. The Seraph's trade matches item components exactly, so every core must be current.
     */
    public static boolean migrate(VoidscapePlugin plugin,ItemStack item){
        RealmBosses.Kind kind=identify(plugin,item);if(kind==null||item.isSimilar(create(plugin,kind)))return false;
        var meta=item.getItemMeta();
        meta.getPersistentDataContainer().remove(plugin.key("boss_orb"));meta.lore(null);item.setItemMeta(meta);
        decorate(plugin,item,kind);return true;
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
    // A Heart of the Sea crafts a conduit; a boss core must never be spent that way.
    @EventHandler public void craft(PrepareItemCraftEvent event){
        for(ItemStack ingredient:event.getInventory().getMatrix())if(identify(plugin,ingredient)!=null){event.getInventory().setResult(null);return;}
    }
    @EventHandler(ignoreCancelled=true) public void autocraft(CrafterCraftEvent event){
        if(event.getBlock().getState() instanceof org.bukkit.block.Crafter crafter)
            for(ItemStack ingredient:crafter.getInventory().getContents())if(identify(plugin,ingredient)!=null){event.setCancelled(true);return;}
    }
}
