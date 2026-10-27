package com.example.voidscape.world;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.enchant.LimitBreakType;
import com.example.voidscape.enchant.UniqueEnchant;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** One-time, shared exploration rewards. Chunk markers survive chest removal. */
public final class WhaleTreasure implements Listener {
    private final VoidscapePlugin plugin;
    private final BossTreasure bossTreasure;
    public WhaleTreasure(VoidscapePlugin plugin) { this.plugin = plugin;bossTreasure=new BossTreasure(plugin,this); }

    public static void upgradeFlyingStaffRates(org.bukkit.configuration.file.FileConfiguration config){
        String version="structures.treasure.flying-staff-rate-version";
        if(config.contains(version,true)&&config.getInt(version)>=2)return;
        String[] kinds={"whale","hanging-garden","observatory"};
        double[] oldRates={.075,.05,.05},newRates={.10,.075,.075};
        for(int i=0;i<kinds.length;i++){
            String path="structures.treasure.flying-staff-chances."+kinds[i];
            if(!config.contains(path,true)||Double.compare(config.getDouble(path),oldRates[i])==0)config.set(path,newRates[i]);
        }
        config.set(version,2);
    }

    @EventHandler
    public void onLoad(ChunkLoadEvent event) { populate(event.getChunk()); }

    public void populate(Chunk chunk) {
        if (!chunk.getWorld().equals(plugin.world())) return;
        populateLandmarks(chunk);
        populateBossStructures(chunk);
        if (!plugin.getConfig().getBoolean("structures.sky-whale.enabled", true)) return;
        var site = plugin.skyWhales().cell(Math.floorDiv(chunk.getX(), plugin.skyWhales().spacingChunks()),
                Math.floorDiv(chunk.getZ(), plugin.skyWhales().spacingChunks()));
        if (site == null || chunk.getX() != Math.floorDiv(site.x() - 52, 16)
                || chunk.getZ() != Math.floorDiv(site.z() + 7, 16)) return;
        var marker = plugin.key("whale_treasure_v1");
        var upgradeMarker = plugin.key("whale_wand_upgrades_v2");
        boolean alreadyPopulated = chunk.getPersistentDataContainer().has(marker, PersistentDataType.BYTE);
        if (alreadyPopulated && chunk.getPersistentDataContainer().has(upgradeMarker, PersistentDataType.BYTE)) {
            addFlyingStaff(chunk,site.x(),site.z(),"whale",115,7,new int[]{-52,-50},.10);
            return;
        }
        // Both positions and all validation blocks are in this chunk: no neighbor loads.
        Random random = new Random(DungeonLayout.mix(chunk.getWorld().getSeed()
                ^ (long)site.x() * 341873128712L ^ (long)site.z() * 132897987541L ^ 0x5748414c454cL));
        int count = chestCount(random);
        int[] positions = random.nextBoolean() ? new int[]{-52, -50} : new int[]{-50, -52};
        if(alreadyPopulated) {
            // Existing whales were filled before wand upgrades existed. Add one
            // upgrade to each surviving library chest without rerolling old loot.
            chunk.getPersistentDataContainer().set(upgradeMarker, PersistentDataType.BYTE, (byte)1);
            for(int i=0;i<count;i++) {
                int x=Math.floorMod(site.x()+positions[i],16),z=Math.floorMod(site.z()+7,16);
                var block=chunk.getBlock(x,115,z);
                if(!(block.getState() instanceof Chest chest)
                        || chunk.getBlock(x,114,z).getType()!=Material.SPRUCE_PLANKS
                        || chunk.getBlock(x,115,z+1).getType()!=Material.BOOKSHELF)continue;
                boolean hasUpgrade=Arrays.stream(chest.getBlockInventory().getContents()).filter(Objects::nonNull)
                        .anyMatch(item->item.hasItemMeta()&&item.getItemMeta().getPersistentDataContainer()
                                .has(new org.bukkit.NamespacedKey("advance_magic","wand_upgrade"),PersistentDataType.STRING));
                int slot=chest.getBlockInventory().firstEmpty();
                if(!hasUpgrade&&slot>=0)chest.getBlockInventory().setItem(slot,createWandUpgrade(random));
            }
            addFlyingStaff(chunk,site.x(),site.z(),"whale",115,7,new int[]{-52,-50},.10);
            return;
        }
        // Record the attempt even if a player built here, so removing their blocks
        // later cannot reroll or regenerate rewards.
        chunk.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte)1);
        chunk.getPersistentDataContainer().set(upgradeMarker, PersistentDataType.BYTE, (byte)1);
        boolean restorationAdded=false;
        for (int i = 0; i < count; i++) {
            int x = Math.floorMod(site.x() + positions[i], 16), z = Math.floorMod(site.z() + 7, 16);
            var block = chunk.getBlock(x, 115, z);
            if (!block.getType().isAir() || !chunk.getBlock(x, 116, z).getType().isAir()
                    || chunk.getBlock(x, 114, z).getType() != Material.SPRUCE_PLANKS
                    || chunk.getBlock(x, 115, z + 1).getType() != Material.BOOKSHELF) continue;
            List<ItemStack> loot = roll(random);
            block.setType(Material.CHEST, false);
            var data = (org.bukkit.block.data.type.Chest)block.getBlockData();
            data.setFacing(BlockFace.NORTH);
            data.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
            block.setBlockData(data, false);
            Chest chest = (Chest)block.getState();
            List<Integer> slots = new ArrayList<>();
            for (int slot = 0; slot < 27; slot++) slots.add(slot);
            Collections.shuffle(slots, random);
            for (int j = 0; j < loot.size(); j++) chest.getBlockInventory().setItem(slots.get(j), loot.get(j));
            ElementalScrollTreasure.add(chest,"landmark");
            if(!restorationAdded){addRestoration(chest,site.x(),site.z(),0);restorationAdded=true;}
            ElementalStaffTreasure.add(plugin,chest,"sky-chest");
        }
        addFlyingStaff(chunk,site.x(),site.z(),"whale",115,7,new int[]{-52,-50},.10);
    }

    private int chestCount(Random random){
        return random.nextInt(100)<probability("second-chest-chance",.30)*100?2:1;
    }
    private void populateBossStructures(Chunk chunk){
        var layout=plugin.bossStructures();if(layout==null)return;
        for(var site:layout.cell(Math.floorDiv(chunk.getX(),layout.spacingChunks()),Math.floorDiv(chunk.getZ(),layout.spacingChunks()))){
            var kind=site.kind();int y=kind.floorY+1;
            if(!site.legacyDesign()){
                if(layout.enabled(kind)&&plugin.getConfig().getBoolean("structures.boss-structures."+kind.id+".enabled",true))bossTreasure.populate(chunk,site);
                continue;
            }
            if(!layout.enabled(kind)||!plugin.getConfig().getBoolean("structures.boss-structures."+kind.id+".enabled",true)
                    ||chunk.getX()!=Math.floorDiv(site.x()+4,16)||chunk.getZ()!=Math.floorDiv(site.z()+kind.chestZ,16))continue;
            var marker=plugin.key(kind.id+"_treasure_v1");
            if(!chunk.getPersistentDataContainer().has(marker,PersistentDataType.BYTE)){
                // Record the attempt before placing anything: removed/looted chests never respawn.
                chunk.getPersistentDataContainer().set(marker,PersistentDataType.BYTE,(byte)1);
                var random=new Random(DungeonLayout.mix(chunk.getWorld().getSeed()^kind.salt
                    ^((long)site.x()*341873128712L)^((long)site.z()*132897987541L)^0x5452454153555245L));
                int count=chestCount(random);int[] offsets=random.nextBoolean()?new int[]{4,6}:new int[]{6,4};
                boolean restorationAdded=false;
                for(int i=0;i<count;i++){
                    int x=Math.floorMod(site.x()+offsets[i],16),z=Math.floorMod(site.z()+kind.chestZ,16);
                    var block=chunk.getBlock(x,y,z);
                    if(!block.getType().isAir()||!chunk.getBlock(x,y+1,z).getType().isAir()
                            ||chunk.getBlock(x,y-1,z).getType()!=Material.SPRUCE_PLANKS
                            ||chunk.getBlock(x,y,z+1).getType()!=Material.BOOKSHELF)continue;
                    var loot=roll(random);block.setType(Material.CHEST,false);
                    var data=(org.bukkit.block.data.type.Chest)block.getBlockData();
                    data.setFacing(BlockFace.NORTH);data.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
                    block.setBlockData(data,false);var chest=(Chest)block.getState();
                    var slots=new ArrayList<Integer>();for(int slot=0;slot<27;slot++)slots.add(slot);
                    Collections.shuffle(slots,random);
                    for(int j=0;j<loot.size();j++)chest.getBlockInventory().setItem(slots.get(j),loot.get(j));
                    ElementalScrollTreasure.add(chest,"boss");
                    if(!restorationAdded){addRestorationTreasure(chest,site.x(),site.z(),kind.ordinal()+10);restorationAdded=true;}
                }
            }
            addFlyingStaff(chunk,site.x(),site.z(),kind.id,y,kind.chestZ,new int[]{4,6},.075);
        }
    }
    private double probability(String name,double fallback){
        double value=plugin.getConfig().getDouble("structures.treasure."+name,fallback);
        return Double.isFinite(value)?Math.clamp(value,0,1):fallback;
    }
    private void populateLandmarks(Chunk chunk){
        var layout=plugin.skyLandmarks();
        for(var site:layout.cell(Math.floorDiv(chunk.getX(),layout.spacingChunks()),Math.floorDiv(chunk.getZ(),layout.spacingChunks()))){
            var kind=site.kind();int y=kind.chestY;
            if(!plugin.getConfig().getBoolean("structures."+kind.id+".enabled",true)
                    ||chunk.getX()!=Math.floorDiv(site.x()+4,16)
                    ||chunk.getZ()!=Math.floorDiv(site.z()+kind.chestZ,16))continue;
            var marker=plugin.key(kind.id+"_treasure_v1");
            if(chunk.getPersistentDataContainer().has(marker,PersistentDataType.BYTE)){
                addFlyingStaff(chunk,site.x(),site.z(),kind.id,y,kind.chestZ,new int[]{4,6},.075);
                continue;
            }
            chunk.getPersistentDataContainer().set(marker,PersistentDataType.BYTE,(byte)1);
            Random random=new Random(DungeonLayout.mix(chunk.getWorld().getSeed()^((long)site.x()*341873128712L)
                    ^((long)site.z()*132897987541L)^0x5452454153555245L^kind.ordinal()));
            int count=chestCount(random);int[] positions=random.nextBoolean()?new int[]{4,6}:new int[]{6,4};
            boolean restorationAdded=false;
            for(int i=0;i<count;i++){
                int x=Math.floorMod(site.x()+positions[i],16),z=Math.floorMod(site.z()+kind.chestZ,16);
                var block=chunk.getBlock(x,y,z);
                if(!block.getType().isAir()||!chunk.getBlock(x,y+1,z).getType().isAir()
                        ||chunk.getBlock(x,y-1,z).getType()!=Material.SPRUCE_PLANKS
                        ||chunk.getBlock(x,y,z+1).getType()!=Material.BOOKSHELF)continue;
                List<ItemStack> loot=roll(random);
                block.setType(Material.CHEST,false);
                var state=(org.bukkit.block.data.type.Chest)block.getBlockData();
                state.setFacing(BlockFace.NORTH);state.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
                block.setBlockData(state,false);
                Chest chest=(Chest)block.getState();
                List<Integer> slots=new ArrayList<>();for(int slot=0;slot<27;slot++)slots.add(slot);
                Collections.shuffle(slots,random);
                for(int j=0;j<loot.size();j++)chest.getBlockInventory().setItem(slots.get(j),loot.get(j));
                ElementalScrollTreasure.add(chest,"landmark");
                if(!restorationAdded){addRestoration(chest,site.x(),site.z(),kind.ordinal()+1);restorationAdded=true;}
                ElementalStaffTreasure.add(plugin,chest,"sky-chest");
            }
            addFlyingStaff(chunk,site.x(),site.z(),kind.id,y,kind.chestZ,new int[]{4,6},.075);
        }
    }

    private void addFlyingStaff(Chunk chunk,int siteX,int siteZ,String kind,int y,int chestZ,int[] offsets,double fallback){
        var marker=plugin.key(kind+"_flying_staff_v1");
        var persistent=chunk.getPersistentDataContainer();
        if(persistent.has(marker,PersistentDataType.BYTE))return;
        var addon=plugin.getServer().getPluginManager().getPlugin("advance-magic");
        if(!(addon instanceof com.example.advancemagic.AdvanceMagicPlugin magic)||!magic.isEnabled())return;
        // Record one attempt per structure, even if its chests were removed or full.
        // This also gives surviving old treasure chests a single upgrade roll.
        persistent.set(marker,PersistentDataType.BYTE,(byte)1);
        List<Chest> chests=new ArrayList<>();
        for(int offset:offsets){
            int x=Math.floorMod(siteX+offset,16),z=Math.floorMod(siteZ+chestZ,16);
            if(!(chunk.getBlock(x,y,z).getState() instanceof Chest chest)
                    ||chunk.getBlock(x,y-1,z).getType()!=Material.SPRUCE_PLANKS
                    ||chunk.getBlock(x,y,z+1).getType()!=Material.BOOKSHELF)continue;
            // Do not add another staff to a chest that already contains one.
            if(Arrays.stream(chest.getBlockInventory().getContents()).anyMatch(magic.flyingStaff()::isStaff))return;
            if(chest.getBlockInventory().firstEmpty()>=0)chests.add(chest);
        }
        if(chests.isEmpty())return;
        // A separate deterministic stream preserves the existing rewards and chest count.
        Random random=new Random(DungeonLayout.mix(chunk.getWorld().getSeed()^((long)siteX*341873128712L)
                ^((long)siteZ*132897987541L)^0x464c59494e475354L^kind.hashCode()));
        if(random.nextDouble()>=probability("flying-staff-chances."+kind,fallback))return;
        var inventory=chests.get(random.nextInt(chests.size())).getBlockInventory();
        inventory.setItem(inventory.firstEmpty(),magic.flyingStaff().create());
    }

    private void addRestoration(Chest chest,int x,int z,int kind){
        if(plugin.restorationLayout()==null||!plugin.restorationLayout().fresh(x,z))return;
        addRestorationTreasure(chest,x,z,kind);
    }
    void addRestorationTreasure(Chest chest,int x,int z,int kind){
        var addon=plugin.getServer().getPluginManager().getPlugin("advance-magic");
        if(!(addon instanceof com.example.advancemagic.AdvanceMagicPlugin magic)||!magic.isEnabled())return;
        // Separate RNG preserves every original loot roll, chest count and slot order.
        Random random=new Random(DungeonLayout.mix(plugin.world().getSeed()^((long)x*341873128712L)
                ^((long)z*132897987541L)^0x524553544f52454cL^kind));
        var inventory=chest.getBlockInventory();int slot=inventory.firstEmpty();
        if(slot>=0)inventory.setItem(slot,magic.restoration().createCore());
        double chance=plugin.getConfig().getDouble("structures.restoration.repair-wand-chance",.10);
        if(!Double.isFinite(chance))chance=.10;
        if(random.nextDouble()<Math.clamp(chance,0,1)&&(slot=inventory.firstEmpty())>=0)
            inventory.setItem(slot,magic.restoration().createRepairWand());
    }

    private List<ItemStack> roll(Random random) {
        List<ItemStack> items = new ArrayList<>();
        // Sky landmarks and all three boss structures share exactly one wand upgrade per chest.
        // It is deliberately generated here, never in the Vault reward table.
        items.add(createWandUpgrade(random));
        String[] names={"echo-shard","nether-star","limit-break","unique-scroll","none"};
        int[] weights={300,30,15,5,650};int total=0;
        for(int i=0;i<weights.length;i++){
            weights[i]=Math.clamp(plugin.getConfig().getInt("structures.treasure.bonus-weights."+names[i],weights[i]),0,1000000);
            total+=weights[i];
        }
        if(total==0)return items;
        int bonus=random.nextInt(total),choice=0;
        while(choice<weights.length-1&&bonus>=weights[choice])bonus-=weights[choice++];
        if (choice==0) items.add(new ItemStack(Material.ECHO_SHARD,1));
        else if (choice==1) items.add(new ItemStack(Material.NETHER_STAR, 1));
        else if (choice==2) items.add(plugin.relics().createScrollLimitBreak(
                LimitBreakType.values()[random.nextInt(LimitBreakType.values().length)]));
        else if (choice==3) items.add(plugin.relics().createScrollUnique(
                UniqueEnchant.values()[random.nextInt(UniqueEnchant.values().length)]));
        return items;
    }

    ItemStack createWandUpgrade(Random random) {
        var gems=new com.example.advancemagic.elemental.StaffGemService.Gem[]{
            com.example.advancemagic.elemental.StaffGemService.Gem.MAX,
            com.example.advancemagic.elemental.StaffGemService.Gem.DAMAGE,
            com.example.advancemagic.elemental.StaffGemService.Gem.COOLDOWN};
        return com.example.advancemagic.elemental.StaffGemService.create(gems[random.nextInt(gems.length)]);
    }
}
