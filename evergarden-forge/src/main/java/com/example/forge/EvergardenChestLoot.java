package com.example.forge;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * Gems and affix gear in Evergarden's own chests (landmarks, exploration sites, whales, boss vaults,
 * sanctuaries). Evergarden fills those chests itself at chunk load, so no loot event fires; instead it
 * marks the chunk. The first time a player opens a non-empty chest in a marked chunk, the forge rolls
 * its extra loot once. Chests that players placed are never decorated.
 */
public final class EvergardenChestLoot implements Listener {
    private static final Set<Material> CONTAINERS = EnumSet.of(Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL);

    private final ForgePlugin plugin;

    public EvergardenChestLoot(ForgePlugin plugin) {
        this.plugin = plugin;
    }

    private NamespacedKey positionKey(String prefix, Block block) {
        return new NamespacedKey(plugin, prefix + "_" + block.getX() + "_" + block.getY() + "_" + block.getZ());
    }

    /** Marks a chest Evergarden filled from a loot table (sanctuaries), so its first open is decorated. */
    public void markEvergardenChest(Block block) {
        block.getChunk().getPersistentDataContainer().set(positionKey("evg_chest", block), PersistentDataType.BYTE, (byte) 1);
    }

    /** True when Evergarden recorded treasure in this chunk. */
    public boolean evergardenChunk(Chunk chunk) {
        List<Pattern> markers = plugin.settings().evergardenChunkMarkers;
        if (markers.isEmpty()) return false;
        for (NamespacedKey key : chunk.getPersistentDataContainer().getKeys()) {
            String text = key.toString().toLowerCase(Locale.ROOT);
            for (Pattern marker : markers) if (marker.matcher(text).matches()) return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!CONTAINERS.contains(block.getType())) return;
        block.getChunk().getPersistentDataContainer().set(positionKey("placed", block), PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!plugin.settings().evergardenChestsEnabled) return;
        InventoryHolder holder = event.getInventory().getHolder();
        List<Container> containers = new ArrayList<>();
        if (holder instanceof DoubleChest chest) {
            if (chest.getLeftSide() instanceof Container left) containers.add(left);
            if (chest.getRightSide() instanceof Container right) containers.add(right);
        } else if (holder instanceof Container container) {
            containers.add(container);
        }
        for (Container container : containers) {
            List<ItemStack> added = decorate(container.getBlock(), ThreadLocalRandom.current());
            if (!added.isEmpty() && event.getPlayer() instanceof org.bukkit.entity.Player player) {
                if (plugin.charmEffects().lostHours(player)) extra(container, ThreadLocalRandom.current());
                plugin.charmEffects().luckyStar(player, player.getLocation());
            }
        }
    }

    /**
     * Rolls the extra loot into an Evergarden chest once. Returns the items added (empty when the
     * block is not an Evergarden chest, was placed by a player, is empty, or was decorated before).
     */
    public List<ItemStack> decorate(Block block, Random random) {
        if (!CONTAINERS.contains(block.getType()) || !(block.getState() instanceof Container container)) return List.of();
        Chunk chunk = block.getChunk();
        PersistentDataContainer data = chunk.getPersistentDataContainer();
        NamespacedKey done = positionKey("evg_loot", block);
        if (data.has(done, PersistentDataType.BYTE) || data.has(positionKey("placed", block), PersistentDataType.BYTE)) return List.of();
        if (!evergardenChunk(chunk) && !data.has(positionKey("evg_chest", block), PersistentDataType.BYTE)) return List.of();
        // A chest's own half, not the combined double-chest view.
        Inventory inventory = container instanceof Chest chest ? chest.getBlockInventory() : container.getInventory();
        // Evergarden only fills chests at its treasure spots; an empty one is a player's or already looted.
        if (inventory.isEmpty()) return List.of();
        data.set(done, PersistentDataType.BYTE, (byte) 1);
        List<ItemStack> items = roll(random, block.getWorld().getEnvironment());
        List<Integer> free = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) if (inventory.getItem(slot) == null) free.add(slot);
        Collections.shuffle(free, random);
        List<ItemStack> added = new ArrayList<>();
        for (int i = 0; i < items.size() && i < free.size(); i++) {
            inventory.setItem(free.get(i), items.get(i));
            added.add(items.get(i));
        }
        if (!added.isEmpty()) {
            plugin.getLogger().fine("[Forge-EvergardenChest] " + block.getWorld().getName() + " " + block.getX() + "," + block.getY() + "," + block.getZ()
                + " added=" + added.stream().map(item -> item.getType().getKey().getKey()).toList());
        }
        return added;
    }

    /**
     * Eye of the Abyss: treasure nobody opened yet near a point (loaded chunks only), nearest first, at most 12:
     * Evergarden treasure chests the forge has not decorated, and vanilla containers that still hold their loot table.
     */
    public List<org.bukkit.Location> unopenedNear(org.bukkit.Location center, double radius) {
        World world = center.getWorld();
        int reach = (int) Math.ceil(radius / 16), cx = center.getBlockX() >> 4, cz = center.getBlockZ() >> 4;
        List<org.bukkit.Location> found = new ArrayList<>();
        for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++) {
            if (!world.isChunkLoaded(cx + dx, cz + dz)) continue;
            Chunk chunk = world.getChunkAt(cx + dx, cz + dz);
            PersistentDataContainer data = chunk.getPersistentDataContainer();
            boolean garden = evergardenChunk(chunk);
            for (org.bukkit.block.BlockState state : chunk.getTileEntities(false)) {
                if (!CONTAINERS.contains(state.getType()) || !(state instanceof Container container)) continue;
                Block block = state.getBlock();
                if (block.getLocation().add(0.5, 0.5, 0.5).distanceSquared(center) > radius * radius) continue;
                if (state instanceof org.bukkit.loot.Lootable lootable && lootable.hasLootTable()) {
                    found.add(block.getLocation());
                    continue;
                }
                if (data.has(positionKey("placed", block), PersistentDataType.BYTE) || data.has(positionKey("evg_loot", block), PersistentDataType.BYTE)) continue;
                if (!garden && !data.has(positionKey("evg_chest", block), PersistentDataType.BYTE)) continue;
                Inventory inventory = container instanceof Chest chest ? chest.getBlockInventory() : container.getInventory();
                if (!inventory.isEmpty()) found.add(block.getLocation());
            }
        }
        found.sort(java.util.Comparator.comparingDouble(location -> location.distanceSquared(center)));
        return found.size() > 12 ? new ArrayList<>(found.subList(0, 12)) : found;
    }

    /** Lost Hours: one more loot pass into free slots of a chest that was just decorated. */
    private void extra(Container container, Random random) {
        Inventory inventory = container instanceof Chest chest ? chest.getBlockInventory() : container.getInventory();
        List<ItemStack> items = roll(random, container.getWorld().getEnvironment());
        if (items.isEmpty()) items = List.of(plugin.gems().item(plugin.gems().roll(plugin.affixes().rollRarity(random, plugin.settings().evergardenWeights), random), 1));
        for (ItemStack item : items) {
            int slot = inventory.firstEmpty();
            if (slot < 0) break;
            inventory.setItem(slot, item);
        }
    }

    /** Gems (60%, then 25% for each extra, up to 3) and affix gear (40%, then 20% extra, up to 2) by default. */
    public List<ItemStack> roll(Random random, World.Environment environment) {
        ForgeSettings settings = plugin.settings();
        List<ItemStack> items = new ArrayList<>();
        int gems = count(random, settings.evergardenGemChance, settings.evergardenGemExtra, settings.evergardenGemMax);
        for (int i = 0; i < gems; i++) {
            Rarity rarity = plugin.affixes().rollRarity(random, settings.evergardenWeights);
            items.add(plugin.gems().item(plugin.gems().roll(rarity, random), 1));
        }
        int gear = count(random, settings.evergardenGearChance, settings.evergardenGearExtra, settings.evergardenGearMax);
        for (int i = 0; i < gear; i++) {
            Rarity rarity = plugin.affixes().rollRarity(random, settings.evergardenWeights);
            items.add(plugin.gear().affixed(rarity, random, environment));
        }
        return items;
    }

    private static int count(Random random, double first, double extra, int max) {
        if (max <= 0 || random.nextDouble() >= first) return 0;
        int count = 1;
        while (count < max && random.nextDouble() < extra) count++;
        return count;
    }
}
