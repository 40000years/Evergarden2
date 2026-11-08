package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.DragonNestLayout;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;

/**
 * Scales a slain Dragon Nest dragon leaves its hunters: Vharok's fire, Glaceryn's frost, Vaelthyr's lightning.
 * The Seraph trades them, with Golden Seraph Feathers, for the matching dragon armor set (Infernal Drake,
 * Frostwyrm, Tempest Drake). Only the PDC marker counts, so a renamed echo shard never pays.
 */
public final class DragonScales implements Listener {
    private final VoidscapePlugin plugin;
    public DragonScales(VoidscapePlugin plugin){this.plugin=plugin;}

    public static String id(DragonNestLayout.Kind kind){return kind==DragonNestLayout.Kind.ICE?"ice":kind.shortId;}
    public static NamespacedKey model(DragonNestLayout.Kind kind){return new NamespacedKey("voidscape","dragon_scale_"+id(kind));}
    public static String title(DragonNestLayout.Kind kind){
        return switch(kind){case FIRE->"เกล็ดมังกรเพลิง Vharok";case STORM->"เกล็ดมังกรอัสนี Vaelthyr";case ICE->"เกล็ดมังกรเหมันต์ Glaceryn";};
    }
    public static String set(DragonNestLayout.Kind kind){
        return switch(kind){case FIRE->"ชุดอสูรมังกรเพลิง";case STORM->"ชุดมังกรพายุอัสนี";case ICE->"ชุดมังกรน้ำแข็งนิรันดร์";};
    }
    static int color(DragonNestLayout.Kind kind){
        return switch(kind){case FIRE->0xff6a2a;case STORM->0xc7a0ff;case ICE->0x9be6ff;};
    }
    private static String essence(DragonNestLayout.Kind kind){
        return switch(kind){
            case FIRE->"เกล็ดที่ยังคุลาวาอยู่ใต้ผิว ร้อนจนแสบมือ";
            case STORM->"เกล็ดที่สายฟ้ายังวิ่งพล่านอยู่ในเส้นแร่";
            case ICE->"เกล็ดน้ำแข็งที่ไม่มีวันละลาย แม้กลางเปลวไฟ";
        };
    }
    private NamespacedKey key(){return plugin.key("dragon_scale");}

    public static ItemStack create(VoidscapePlugin plugin,DragonNestLayout.Kind kind,int amount){
        ItemStack item=new ItemStack(Material.ECHO_SHARD,Math.clamp(amount,1,64));
        var meta=item.getItemMeta();
        meta.displayName(Component.text(title(kind),TextColor.color(color(kind))).decoration(TextDecoration.ITALIC,false));
        meta.lore(List.of(
            Component.text(essence(kind),NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false),
            Component.text("ได้จากการพิชิตมังกรประจำรัง",NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC,false),
            Component.text("แลก"+set(kind)+"กับ Seraph (ร่วมกับขนนกเซราฟทองคำ)",NamedTextColor.GOLD).decoration(TextDecoration.ITALIC,false)));
        meta.setItemModel(model(kind));
        meta.setEnchantmentGlintOverride(true);
        meta.setMaxStackSize(64);
        if(meta instanceof org.bukkit.inventory.meta.Repairable repairable)repairable.setRepairCost(0);
        meta.getPersistentDataContainer().set(plugin.key("dragon_scale"),PersistentDataType.STRING,id(kind));
        item.setItemMeta(meta);
        return item;
    }
    public static DragonNestLayout.Kind identify(VoidscapePlugin plugin,ItemStack item){
        if(item==null||item.getType()!=Material.ECHO_SHARD||!item.hasItemMeta())return null;
        String id=item.getItemMeta().getPersistentDataContainer().get(plugin.key("dragon_scale"),PersistentDataType.STRING);
        if(id==null)return null;
        for(var kind:DragonNestLayout.Kind.values())if(id(kind).equals(id.toLowerCase(Locale.ROOT)))return kind;
        return null;
    }
    // An echo shard crafts a recovery compass; a dragon scale must never be spent that way.
    @EventHandler public void craft(PrepareItemCraftEvent event){
        for(ItemStack ingredient:event.getInventory().getMatrix())if(identify(plugin,ingredient)!=null){event.getInventory().setResult(null);return;}
    }
    @EventHandler(ignoreCancelled=true) public void autocraft(CrafterCraftEvent event){
        if(event.getBlock().getState() instanceof org.bukkit.block.Crafter crafter)
            for(ItemStack ingredient:crafter.getInventory().getContents())if(identify(plugin,ingredient)!=null){event.setCancelled(true);return;}
    }
}
