package com.example.voidscape.world;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.crop.CropTier;
import com.example.voidscape.crop.CropType;
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
    public WhaleTreasure(VoidscapePlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onLoad(ChunkLoadEvent event) { populate(event.getChunk()); }

    public void populate(Chunk chunk) {
        if (!chunk.getWorld().equals(plugin.world())) return;
        populateLandmarks(chunk);
        if (!plugin.getConfig().getBoolean("structures.sky-whale.enabled", true)) return;
        var site = plugin.skyWhales().cell(Math.floorDiv(chunk.getX(), plugin.skyWhales().spacingChunks()),
                Math.floorDiv(chunk.getZ(), plugin.skyWhales().spacingChunks()));
        if (site == null || chunk.getX() != Math.floorDiv(site.x() - 52, 16)
                || chunk.getZ() != Math.floorDiv(site.z() + 7, 16)) return;
        var marker = plugin.key("whale_treasure_v1");
        var upgradeMarker = plugin.key("whale_wand_upgrades_v2");
        boolean alreadyPopulated = chunk.getPersistentDataContainer().has(marker, PersistentDataType.BYTE);
        if (alreadyPopulated && chunk.getPersistentDataContainer().has(upgradeMarker, PersistentDataType.BYTE)) return;
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
            if(!restorationAdded){addRestoration(chest,site.x(),site.z(),0);restorationAdded=true;}
        }
    }

    private int chestCount(Random random){
        return random.nextInt(100)<probability("second-chest-chance",.30)*100?2:1;
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
            if(chunk.getPersistentDataContainer().has(marker,PersistentDataType.BYTE))continue;
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
                if(!restorationAdded){addRestoration(chest,site.x(),site.z(),kind.ordinal()+1);restorationAdded=true;}
            }
        }
    }

    private void addRestoration(Chest chest,int x,int z,int kind){
        if(plugin.restorationLayout()==null||!plugin.restorationLayout().fresh(x,z))return;
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
        // All three landmarks use this same table, with exactly one wand upgrade.
        // It is deliberately generated here, never in the Vault reward table.
        items.add(createWandUpgrade(random));
        int stacks=Math.clamp(plugin.getConfig().getInt("structures.treasure.crop-stacks",2),0,20);
        for (int i = 0; i < stacks; i++) {
            CropTier tier = random.nextInt(100) < probability("tier-three-chance",.75)*100 ? CropTier.TIER_3 : CropTier.TIER_4;
            CropType[] crops = Arrays.stream(CropType.values()).filter(c -> c.tier == tier).toArray(CropType[]::new);
            CropType crop = crops[random.nextInt(crops.length)];
            items.add(random.nextInt(100) < probability("seed-chance",.35)*100 ? plugin.crops().factory().createSeed(crop, 1)
                    : plugin.crops().factory().createFood(crop, 1 + random.nextInt(2)));
        }
        String[] names={"key-shard","astral-dust","nether-star","limit-break","unique-scroll","none"};
        int[] weights={300,150,30,15,5,500};int total=0;
        for(int i=0;i<weights.length;i++){
            weights[i]=Math.clamp(plugin.getConfig().getInt("structures.treasure.bonus-weights."+names[i],weights[i]),0,1000000);
            total+=weights[i];
        }
        if(total==0)return items;
        int bonus=random.nextInt(total),choice=0;
        while(choice<weights.length-1&&bonus>=weights[choice])bonus-=weights[choice++];
        if (choice==0) items.add(plugin.relics().createKeyShard(1));
        else if (choice==1) items.add(plugin.relics().createAstralDust(1));
        else if (choice==2) items.add(new ItemStack(Material.NETHER_STAR, 1));
        else if (choice==3) items.add(plugin.relics().createScrollLimitBreak(
                LimitBreakType.values()[random.nextInt(LimitBreakType.values().length)]));
        else if (choice==4) items.add(plugin.relics().createScrollUnique(
                UniqueEnchant.values()[random.nextInt(UniqueEnchant.values().length)]));
        return items;
    }

    private ItemStack createWandUpgrade(Random random) {
        String[] ids = {"wand_repair", "wand_damage", "wand_cooldown"};
        String id = ids[random.nextInt(ids.length)];
        Material material = switch (id) {
            case "wand_repair" -> Material.PRISMARINE_SHARD;
            case "wand_damage" -> Material.BLAZE_POWDER;
            default -> Material.AMETHYST_SHARD;
        };
        String title = switch (id) {
            case "wand_repair" -> "Wand Repair Core";
            case "wand_damage" -> "Wand Damage Core";
            default -> "Wand Cooldown Core";
        };
        String effect = switch (id) {
            case "wand_repair" -> "Max durability +5";
            case "wand_damage" -> "Damage +3%";
            default -> "Cooldown -3%";
        };
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.setDisplayName(org.bukkit.ChatColor.GOLD + "✦ " + title);
        meta.setLore(List.of(org.bukkit.ChatColor.GRAY + "Place on a magic wand in your inventory",
                org.bukkit.ChatColor.YELLOW + effect, org.bukkit.ChatColor.DARK_GRAY + "Sky landmark treasure · max level 10"));
        var model = meta.getCustomModelDataComponent();
        model.setStrings(List.of("advance_magic:" + id));
        meta.setCustomModelDataComponent(model);
        meta.setItemModel(new org.bukkit.NamespacedKey("advance_magic", id));
        meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey("advance_magic", "wand_upgrade"), PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }
}
