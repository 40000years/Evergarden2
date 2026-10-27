package com.example.voidscape.world;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import java.util.*;

/** Vanilla End City gear; a rare award raises ONE existing eligible enchant to cap+1. */
public final class SanctuaryLoot {
    private SanctuaryLoot(){}
    public static boolean armorTrim(ItemStack item){return item!=null&&item.getType().name().endsWith("_ARMOR_TRIM_SMITHING_TEMPLATE");}
    public static void replaceTrims(org.bukkit.inventory.Inventory inventory,long seed){
        var addon=org.bukkit.Bukkit.getPluginManager().getPlugin("advance-magic");
        if(!(addon instanceof com.example.advancemagic.AdvanceMagicPlugin magic)||!magic.isEnabled()){
            // No inert fake potions when the mana plugin is absent.
            for(int i=0;i<inventory.getSize();i++)if(armorTrim(inventory.getItem(i)))inventory.setItem(i,new ItemStack(org.bukkit.Material.AMETHYST_SHARD,4));
            return;
        }
        var rng=new Random(seed^0x4d414e415452494dL);
        for(int i=0;i<inventory.getSize();i++)if(armorTrim(inventory.getItem(i))){
            int roll=rng.nextInt(100);
            var quality=roll<60?com.example.advancemagic.mana.ManaPotionService.Quality.MEDIUM:
                roll<95?com.example.advancemagic.mana.ManaPotionService.Quality.GOOD:com.example.advancemagic.mana.ManaPotionService.Quality.SUPREME;
            inventory.setItem(i,magic.manaPotions().create(quality));
        }
    }
    public static boolean equipment(ItemStack item){
        if(item==null)return false;String id=item.getType().name();
        return Set.of("BOW","CROSSBOW","TRIDENT","MACE","SHIELD","ELYTRA").contains(id)
            ||Arrays.stream(new String[]{"_SWORD","_SPEAR","_AXE","_PICKAXE","_SHOVEL","_HOE","_HELMET","_CHESTPLATE","_LEGGINGS","_BOOTS"}).anyMatch(id::endsWith);
    }
    public static ItemStack upgrade(ItemStack original,Random random,double chance,VoidscapePlugin plugin){
        if(!equipment(original)||random.nextDouble()>=chance)return original;
        var eligible=original.getEnchantments().keySet().stream()
            .filter(e->e.getMaxLevel()>1&&e.canEnchantItem(original))
            .sorted(Comparator.comparing(e->e.getKey().toString())).toList();
        if(eligible.isEmpty())return original;
        Enchantment chosen=eligible.get(random.nextInt(eligible.size()));
        ItemStack result=original.clone();var meta=result.getItemMeta();
        meta.addEnchant(chosen,chosen.getMaxLevel()+1,true);
        plugin.bossEnchants().markGenerated(meta);result.setItemMeta(meta);return result;
    }
}
