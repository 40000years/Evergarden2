package com.example.voidscape.dungeon;

import com.example.voidscape.world.DungeonLayout;
import org.bukkit.*;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import java.util.List;

/** Equipment models are local to sanctuary mobs; no global entity texture replacement. */
public final class GuardianAppearance {
    private GuardianAppearance() {}
    public static String theme(DungeonLayout.Kind kind) {
        return switch(kind){case SANCTUM_DARK->"thorn";case SANCTUM_ASTRAL->"astral";case SANCTUM_TIME->"chrono";};
    }
    public static ItemStack mask(DungeonLayout.Kind kind,boolean boss) {
        var item=new ItemStack(Material.CARVED_PUMPKIN);var meta=item.getItemMeta();
        String id=theme(kind)+(boss?"_crown":"_mask");
        meta.setDisplayName("Evergarden · "+id);
        var data=meta.getCustomModelDataComponent();data.setStrings(List.of("voidscape:"+id));meta.setCustomModelDataComponent(data);
        meta.setItemModel(new NamespacedKey("voidscape", id));
        item.setItemMeta(meta);return item;
    }
    private static ItemStack armor(Material material,Color color) {
        var item=new ItemStack(material);var meta=(LeatherArmorMeta)item.getItemMeta();meta.setColor(color);item.setItemMeta(meta);return item;
    }
    public static void apply(Mob mob,DungeonLayout.Kind kind,boolean boss) {
        var equipment=mob.getEquipment();if(equipment==null)return;
        Color color=switch(kind){case SANCTUM_DARK->Color.fromRGB(35,78,60);case SANCTUM_ASTRAL->Color.fromRGB(118,109,164);case SANCTUM_TIME->Color.fromRGB(48,112,111);};
        equipment.setHelmet(mask(kind,boss));equipment.setHelmetDropChance(0);
        // Keep existing boss chest armor and attributes; color the remaining silhouette.
        if(!boss){equipment.setChestplate(armor(Material.LEATHER_CHESTPLATE,color));equipment.setChestplateDropChance(0);}
        equipment.setLeggings(armor(Material.LEATHER_LEGGINGS,color));equipment.setLeggingsDropChance(0);
        equipment.setBoots(armor(Material.LEATHER_BOOTS,color));equipment.setBootsDropChance(0);
        mob.setCanPickupItems(false);
    }
}
