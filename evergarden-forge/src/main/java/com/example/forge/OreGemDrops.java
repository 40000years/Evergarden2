package com.example.forge;

import com.example.forge.GemService.Gem;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Random;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mining ores (and, very rarely, stone) can drop a purity I-II gem. One roll per block: Fortune and
 * Silk Touch do not change it. Ores a player placed never roll, so silk-touched ores cannot be farmed.
 */
public final class OreGemDrops implements Listener {
    private final ForgePlugin plugin;

    public OreGemDrops(ForgePlugin plugin) {
        this.plugin = plugin;
    }

    private NamespacedKey placedKey(Block block) {
        return new NamespacedKey(plugin, "ore_placed_" + block.getX() + "_" + block.getY() + "_" + block.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        // Only ores are tracked: stone's 0.005% is not worth a chunk entry per placed block.
        if (block.getType() == Material.STONE || block.getType() == Material.DEEPSLATE) return;
        if (plugin.settings().oreGemChances.containsKey(block.getType())) {
            block.getChunk().getPersistentDataContainer().set(placedKey(block), PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExtend(BlockPistonExtendEvent event) {
        movePlaced(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRetract(BlockPistonRetractEvent event) {
        // For a non-empty retract Paper supplies the actual pull direction, not the piston facing.
        movePlaced(event.getBlocks(), event.getDirection());
    }

    private void movePlaced(List<Block> blocks, BlockFace movement) {
        // Snapshot the whole chain before clearing sources: one block's destination can be
        // another source, and a move may cross the chunk boundary where the marker is stored.
        List<Block> placed = blocks.stream().filter(block -> block.getChunk().getPersistentDataContainer()
            .has(placedKey(block), PersistentDataType.BYTE)).toList();
        for (Block block : placed) block.getChunk().getPersistentDataContainer().remove(placedKey(block));
        for (Block block : placed) {
            Block target = block.getRelative(movement);
            target.getChunk().getPersistentDataContainer().set(placedKey(target), PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        ForgeSettings settings = plugin.settings();
        Block block = event.getBlock();
        Double chance = settings.oreGemChances.get(block.getType());
        if (chance == null) return;
        NamespacedKey placed = placedKey(block);
        if (block.getChunk().getPersistentDataContainer().has(placed, PersistentDataType.BYTE)) {
            block.getChunk().getPersistentDataContainer().remove(placed);
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR || !event.isDropItems()) return;
        plugin.charmEffects().veinMine(player, block, other -> !other.getChunk().getPersistentDataContainer().has(placedKey(other), PersistentDataType.BYTE));
        if (!settings.oreGemsEnabled || settings.oreGemBlockedWorlds.contains(block.getWorld().getName())) return;
        // The wrong tool (diamond ore by hand) drops nothing in vanilla, and no gem either.
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (block.getDrops(tool, player).isEmpty()) return;
        Gem gem = roll(block.getType(), ThreadLocalRandom.current());
        if (gem == null) return;
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), plugin.gems().item(gem, 1));
        player.sendActionBar(Component.text("✦ พบ " + gem.type().label + " " + GemService.roman(gem.tier()), gem.type().color));
        plugin.getLogger().fine("[Forge-OreGem] player=" + player.getName() + " block=" + block.getType().getKey().getKey() + " gem=" + gem.serialize());
    }

    /** One roll for a mined block: the block's chance, then a random type and purity by ore-gems.tier-weights. */
    public Gem roll(Material material, Random random) {
        ForgeSettings settings = plugin.settings();
        Double chance = settings.oreGemChances.get(material);
        if (chance == null || random.nextDouble() >= chance) return null;
        int[] weights = settings.oreGemTierWeights;
        int total = 0;
        for (int weight : weights) total += weight;
        int tier = 1;
        if (total > 0) {
            int ticket = random.nextInt(total);
            for (int i = 0; i < weights.length; i++) {
                ticket -= weights[i];
                if (ticket < 0) {
                    tier = i + 1;
                    break;
                }
            }
        }
        GemType type = GemType.values()[random.nextInt(GemType.values().length)];
        return new Gem(type, tier);
    }
}
