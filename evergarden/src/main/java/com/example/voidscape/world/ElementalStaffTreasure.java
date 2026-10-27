package com.example.voidscape.world;

import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.elemental.StaffSpellService;
import com.example.advancemagic.item.ElementalStaffService.Element;
import com.example.voidscape.VoidscapePlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.util.Objects;
import java.util.Random;
import java.util.random.RandomGenerator;

/** Fresh structure loot only. Its independent stream never alters existing chest rewards. */
public final class ElementalStaffTreasure {
    private ElementalStaffTreasure(){}

    /** A successful roll is one blank, uniquely identified Apprentice staff, uniform across eight elements. */
    public static ItemStack roll(VoidscapePlugin plugin,String source,RandomGenerator random){
        double fallback=defaultChance(source);
        Objects.requireNonNull(random,"random");
        if(!plugin.getConfig().getBoolean("staff-loot.enabled",true))return null;
        var addon=plugin.getServer().getPluginManager().getPlugin("advance-magic");
        if(!(addon instanceof AdvanceMagicPlugin magic)||!magic.isEnabled()
                ||magic.staffSpells()==null||magic.staffs()==null)return null;
        double chance=plugin.getConfig().getDouble("staff-loot."+source+"-chance",fallback);
        if(!Double.isFinite(chance))chance=fallback;
        if(random.nextDouble()>=Math.clamp(chance,0,1))return null;
        Element[] elements=Element.values();
        return magic.staffSpells().createStaff(elements[random.nextInt(elements.length)],StaffSpellService.Tier.APPRENTICE);
    }

    /** Called once while authoring a supported chest, never as an upgrade/backfill of older treasure. */
    public static void add(VoidscapePlugin plugin,Chest chest,String source){
        defaultChance(source);
        World world=chest.getWorld();
        if(source.equals("sky-chest")){
            if(!world.equals(plugin.world()))return;
        }else if(source.equals("overworld-chest")){
            if(world.equals(plugin.world())||world.getEnvironment()!=World.Environment.NORMAL)return;
        }else throw new IllegalArgumentException("Vault rolls do not populate chests");
        var marker=new NamespacedKey("evergarden","elemental_staff_"+chest.getX()+"_"+chest.getY()+"_"+chest.getZ());
        var data=chest.getChunk().getPersistentDataContainer();
        if(data.has(marker,PersistentDataType.BYTE))return;
        // Record failed/full/disabled attempts too; reloads and replacing a chest cannot reroll it.
        data.set(marker,PersistentDataType.BYTE,(byte)1);
        int slot=chest.getBlockInventory().firstEmpty();
        if(slot<0)return;
        long seed=DungeonLayout.mix(world.getSeed()^((long)chest.getX()*341873128712L)
                ^((long)chest.getZ()*132897987541L)^((long)chest.getY()*7193L)^0x53544146464cL^source.hashCode());
        ItemStack staff=roll(plugin,source,new Random(seed));
        if(staff!=null)chest.getBlockInventory().setItem(slot,staff);
    }

    private static double defaultChance(String source){
        return switch(source){
            case "vault" -> .10;
            case "sky-chest" -> .03;
            case "overworld-chest" -> .01;
            default -> throw new IllegalArgumentException("Unknown staff loot source: "+source);
        };
    }
}
