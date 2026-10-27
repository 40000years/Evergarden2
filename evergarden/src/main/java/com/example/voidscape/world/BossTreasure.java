package com.example.voidscape.world;

import com.example.voidscape.VoidscapePlugin;
import com.example.advancemagic.AdvanceMagicPlugin;
import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Eight decorated single chests; independent durable markers prevent any refill/reroll. */
public final class BossTreasure {
    private final VoidscapePlugin plugin;
    private final WhaleTreasure originalRewards;
    private final BossInteriorService interiors;
    private final BossLootTable loot;
    public BossTreasure(VoidscapePlugin plugin,WhaleTreasure originalRewards){
        this.plugin=plugin;this.originalRewards=originalRewards;interiors=new BossInteriorService(plugin);loot=new BossLootTable(plugin);
        plugin.getServer().getPluginManager().registerEvents(interiors,plugin);
    }
    public BossLootTable loot(){return loot;}
    public void populate(Chunk chunk,BossStructureLayout.Site site){
        interiors.populate(chunk,site);
        var points=site.blueprint().treasures();
        for(int index=0;index<points.size();index++){
            var spot=points.get(index);var point=spot.point();int x=site.x()+point.x(),z=site.z()+point.z();
            if(Math.floorDiv(x,16)!=chunk.getX()||Math.floorDiv(z,16)!=chunk.getZ())continue;
            var marker=plugin.key(site.kind().id+"_vault_"+index+"_v3");var data=chunk.getPersistentDataContainer();
            if(data.has(marker,PersistentDataType.BYTE))continue;
            var block=chunk.getBlock(Math.floorMod(x,16),point.y(),Math.floorMod(z,16));
            boolean oldRewards=index<2&&data.has(plugin.key(site.kind().id+"_treasure_v1"),PersistentDataType.BYTE);
            // An already-looted old chest stays empty; an old removed chest stays removed.
            data.set(marker,PersistentDataType.BYTE,(byte)1);
            if(oldRewards)continue;
            if(!(block.getState() instanceof Chest chest)||!chunk.getBlock(Math.floorMod(x,16),point.y()+1,Math.floorMod(z,16)).getType().isAir()
                ||!chunk.getBlock(Math.floorMod(x,16),point.y()-1,Math.floorMod(z,16)).getType().isSolid())continue;
            if(!chest.getBlockInventory().isEmpty())continue;
            var random=new Random(DungeonLayout.mix(chunk.getWorld().getSeed()^site.kind().salt
                ^((long)site.x()*341873128712L)^((long)site.z()*132897987541L)^0x424f53534c4f4f54L^index));
            var items=loot.roll(random,site.kind());
            if(index==0)items.add(originalRewards.createWandUpgrade(random));
            var slots=new ArrayList<Integer>();for(int slot=0;slot<27;slot++)slots.add(slot);Collections.shuffle(slots,random);
            for(int i=0;i<items.size();i++)chest.getBlockInventory().setItem(slots.get(i),items.get(i));
            ElementalScrollTreasure.add(chest,"boss");
            if(index==0){originalRewards.addRestorationTreasure(chest,site.x(),site.z(),site.kind().ordinal()+10);addFlyingStaff(chunk,chest,site);}
        }
    }
    private void addFlyingStaff(Chunk chunk,Chest chest,BossStructureLayout.Site site){
        var marker=plugin.key(site.kind().id+"_flying_staff_v1");var data=chunk.getPersistentDataContainer();
        if(data.has(marker,PersistentDataType.BYTE))return;
        var addon=plugin.getServer().getPluginManager().getPlugin("advance-magic");
        if(!(addon instanceof AdvanceMagicPlugin magic)||!magic.isEnabled())return;
        data.set(marker,PersistentDataType.BYTE,(byte)1);
        double chance=plugin.getConfig().getDouble("structures.treasure.flying-staff-chances."+site.kind().id,.075);
        if(!Double.isFinite(chance))chance=.075;
        var random=new Random(DungeonLayout.mix(chunk.getWorld().getSeed()^((long)site.x()*341873128712L)
            ^((long)site.z()*132897987541L)^0x464c59494e475354L^site.kind().id.hashCode()));
        int slot=chest.getBlockInventory().firstEmpty();
        if(slot>=0&&random.nextDouble()<Math.clamp(chance,0,1))chest.getBlockInventory().setItem(slot,magic.flyingStaff().create());
    }
}
