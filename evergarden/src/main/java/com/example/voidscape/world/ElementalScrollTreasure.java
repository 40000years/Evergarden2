package com.example.voidscape.world;

import com.example.advancemagic.AdvanceMagicPlugin;
import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.persistence.PersistentDataType;
import java.util.Random;

/** Called only while authoring a new treasure inventory; chunk marker survives chest removal. */
public final class ElementalScrollTreasure {
    private ElementalScrollTreasure(){}
    public static void add(Chest chest,String profile){
        if(Bukkit.getPluginManager().getPlugin("Evergarden") instanceof com.example.voidscape.VoidscapePlugin garden)
            com.example.voidscape.trade.SeraphFeathers.treasure(garden,chest,profile);
        var addon=Bukkit.getPluginManager().getPlugin("advance-magic");
        if(!(addon instanceof AdvanceMagicPlugin magic)||!magic.isEnabled()||magic.acquisition()==null)return;
        com.example.advancemagic.elemental.SkillTreeService.markStructureChest(chest);
        var key=new NamespacedKey("evergarden","elemental_scroll_"+chest.getX()+"_"+chest.getY()+"_"+chest.getZ());
        var data=chest.getChunk().getPersistentDataContainer();if(data.has(key))return;
        data.set(key,PersistentDataType.BYTE,(byte)1);
        long seed=chest.getWorld().getSeed()^((long)chest.getX()*341873128712L)^((long)chest.getZ()*132897987541L)^((long)chest.getY()*7193)^0x5343524f4c4cL;
        var scroll=magic.acquisition().rollScroll(profile,new Random(seed));int slot=chest.getBlockInventory().firstEmpty();
        if(scroll!=null&&slot>=0)chest.getBlockInventory().setItem(slot,scroll);
    }
}
