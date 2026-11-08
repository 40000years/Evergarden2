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
 * Feathers, and two buy a mythical companion egg.
 * <p>Era 2 (e2.193): cores won before the realm bosses were reworked (party health and phase walls, e2.192) were
 * far too easy to farm and flooded the egg trade. Every core from an earlier era, including old Soul Orbs, turns
 * into a spent core on sight: a keepsake with no trade value. Only cores dropped from now on buy anything.
 */
public final class RealmCores implements Listener {
    private final VoidscapePlugin plugin;
    public RealmCores(VoidscapePlugin plugin){this.plugin=plugin;}
    /** Cores of this era are the only ones the Seraph accepts. */
    public static final int ERA=2;

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
        meta.getPersistentDataContainer().set(plugin.key("realm_core_era"),PersistentDataType.INTEGER,ERA);
        item.setItemMeta(meta);
    }
    /** A core from an earlier era, kept as a keepsake: grey, no glint, no trade marker. */
    private static void spend(VoidscapePlugin plugin,ItemStack item,RealmBosses.Kind kind){
        var meta=item.getItemMeta();var data=meta.getPersistentDataContainer();
        data.remove(plugin.key("realm_boss_orb"));data.remove(plugin.key("boss_orb"));data.remove(plugin.key("realm_core_era"));
        data.set(plugin.key("realm_core_spent"),PersistentDataType.STRING,kind.model);
        meta.displayName(Component.text("Spent "+title(kind)+" (หมดพลัง)",NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC,false));
        meta.lore(List.of(
            Component.text("แกนรุ่นเก่าที่ได้มาก่อนบอสมิติถูกปรับความยาก",NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false),
            Component.text("พลังในแกนสลายไปแล้ว — Seraph ไม่รับแลก",NamedTextColor.RED).decoration(TextDecoration.ITALIC,false),
            Component.text("เก็บไว้เป็นที่ระลึกได้ · แกนใหม่ได้จากการพิชิตบอสมิติอีกครั้ง",NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC,false)));
        meta.setItemModel(model(kind));
        meta.setEnchantmentGlintOverride(false);
        item.setItemMeta(meta);
    }
    public static boolean spent(VoidscapePlugin plugin,ItemStack item){
        return item!=null&&item.hasItemMeta()&&item.getItemMeta().getPersistentDataContainer().has(plugin.key("realm_core_spent"),PersistentDataType.STRING);
    }
    /** Only the PDC marker of the current era counts, so a renamed Heart of the Sea or an old core never pays. */
    public static RealmBosses.Kind identify(VoidscapePlugin plugin,ItemStack item){
        RealmBosses.Kind kind=marked(plugin,item);
        if(kind==null)return null;
        Integer era=item.getItemMeta().getPersistentDataContainer().get(plugin.key("realm_core_era"),PersistentDataType.INTEGER);
        return era!=null&&era==ERA?kind:null;
    }
    /** Any realm core marker, whatever its era. */
    private static RealmBosses.Kind marked(VoidscapePlugin plugin,ItemStack item){
        if(item==null||item.getType()!=Material.HEART_OF_THE_SEA||!item.hasItemMeta())return null;
        String id=item.getItemMeta().getPersistentDataContainer().get(plugin.key("realm_boss_orb"),PersistentDataType.STRING);
        return id==null?null:RealmBosses.Kind.of(id);
    }
    /**
     * Rewrites an older Soul Orb drop (or a core from an earlier text revision) to the current core, keeping its
     * identity and stack size. The Seraph's trade matches item components exactly, so every core must be current.
     */
    public static boolean migrate(VoidscapePlugin plugin,ItemStack item){
        RealmBosses.Kind old=marked(plugin,item);
        if(old!=null&&identify(plugin,item)==null){spend(plugin,item,old);return true;}
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
    @EventHandler public void join(PlayerJoinEvent event){
        Player player=event.getPlayer();
        int before=count(plugin,player.getInventory())+count(plugin,player.getEnderChest());
        migratePlayer(plugin,player);migrateInventory(plugin,player.getEnderChest());
        if(before>0)plugin.message(player,"§7แกนบอสมิติรุ่นเก่า §f"+before+"§7 ชิ้นหมดพลังแล้ว (รีเซ็ตหลังปรับความยากบอสมิติ) · แกนใหม่ได้จากการพิชิตบอสมิติอีกครั้ง");
    }
    private static int count(VoidscapePlugin plugin,Inventory inventory){
        int total=0;for(ItemStack item:inventory.getContents())if(marked(plugin,item)!=null&&identify(plugin,item)==null)total+=item.getAmount();return total;
    }
    @EventHandler(ignoreCancelled=true) public void open(InventoryOpenEvent event){
        migrateInventory(plugin,event.getInventory());if(event.getPlayer() instanceof Player player)migratePlayer(plugin,player);
    }
    // A Heart of the Sea crafts a conduit; a boss core must never be spent that way.
    @EventHandler public void craft(PrepareItemCraftEvent event){
        for(ItemStack ingredient:event.getInventory().getMatrix())if(marked(plugin,ingredient)!=null||spent(plugin,ingredient)){event.getInventory().setResult(null);return;}
    }
    @EventHandler(ignoreCancelled=true) public void autocraft(CrafterCraftEvent event){
        if(event.getBlock().getState() instanceof org.bukkit.block.Crafter crafter)
            for(ItemStack ingredient:crafter.getInventory().getContents())if(marked(plugin,ingredient)!=null||spent(plugin,ingredient)){event.setCancelled(true);return;}
    }
}
