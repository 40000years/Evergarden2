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
        if (!chunk.getWorld().equals(plugin.world())
                || !plugin.getConfig().getBoolean("structures.sky-whale.enabled", true)) return;
        var site = plugin.skyWhales().cell(Math.floorDiv(chunk.getX(), plugin.skyWhales().spacingChunks()),
                Math.floorDiv(chunk.getZ(), plugin.skyWhales().spacingChunks()));
        if (site == null || chunk.getX() != Math.floorDiv(site.x() - 52, 16)
                || chunk.getZ() != Math.floorDiv(site.z() + 7, 16)) return;
        var marker = plugin.key("whale_treasure_v1");
        if (chunk.getPersistentDataContainer().has(marker, PersistentDataType.BYTE)) return;
        // Both positions and all validation blocks are in this chunk: no neighbor loads.
        Random random = new Random(DungeonLayout.mix(chunk.getWorld().getSeed()
                ^ (long)site.x() * 341873128712L ^ (long)site.z() * 132897987541L ^ 0x5748414c454cL));
        int count = random.nextInt(100) < 30 ? 2 : 1;
        int[] positions = random.nextBoolean() ? new int[]{-52, -50} : new int[]{-50, -52};
        // Record the attempt even if a player built here, so removing their blocks
        // later cannot reroll or regenerate rewards.
        chunk.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte)1);
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
        }
    }

    private List<ItemStack> roll(Random random) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            CropTier tier = random.nextInt(100) < 75 ? CropTier.TIER_3 : CropTier.TIER_4;
            CropType[] crops = Arrays.stream(CropType.values()).filter(c -> c.tier == tier).toArray(CropType[]::new);
            CropType crop = crops[random.nextInt(crops.length)];
            items.add(random.nextInt(100) < 35 ? plugin.crops().factory().createSeed(crop, 1)
                    : plugin.crops().factory().createFood(crop, 1 + random.nextInt(2)));
        }
        int bonus = random.nextInt(1000);
        if (bonus < 300) items.add(plugin.relics().createKeyShard(1));
        else if (bonus < 450) items.add(plugin.relics().createAstralDust(1));
        else if (bonus < 480) items.add(new ItemStack(Material.NETHER_STAR, 1));
        else if (bonus < 495) items.add(plugin.relics().createScrollLimitBreak(
                LimitBreakType.values()[random.nextInt(LimitBreakType.values().length)]));
        else if (bonus < 500) items.add(plugin.relics().createScrollUnique(
                UniqueEnchant.values()[random.nextInt(UniqueEnchant.values().length)]));
        return items;
    }
}
