package com.example.forge;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Container;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class ForgeListener implements Listener {
    /** Vanilla stations where a gem or material would be eaten as its base item (emerald trades, beacon payment...). */
    private static final Set<InventoryType> VANILLA_STATIONS = EnumSet.of(InventoryType.FURNACE, InventoryType.BLAST_FURNACE,
        InventoryType.SMOKER, InventoryType.BREWING, InventoryType.BEACON, InventoryType.ENCHANTING, InventoryType.MERCHANT,
        InventoryType.STONECUTTER, InventoryType.LOOM, InventoryType.CARTOGRAPHY, InventoryType.GRINDSTONE, InventoryType.CRAFTER);

    private final ForgePlugin plugin;

    public ForgeListener(ForgePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean forgeItem(ItemStack item) {
        return plugin.gems().isForgeItem(item);
    }

    // ---- arrow stats -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;
        double power = plugin.affixes().total(event.getBow(), Stat.ARROW_DAMAGE);
        double speed = plugin.affixes().total(event.getBow(), Stat.ARROW_SPEED);
        if (power != 0) arrow.setDamage(arrow.getDamage() * (1 + power));
        if (speed != 0) arrow.setVelocity(arrow.getVelocity().multiply(1 + speed));
    }

    // ---- structure chests ----------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLoot(LootGenerateEvent event) {
        ForgeSettings settings = plugin.settings();
        if (!settings.lootEnabled || event.isPlugin()) return;
        if (settings.lootBlockedWorlds.contains(event.getWorld().getName())) return;
        String table = event.getLootTable().getKey().getKey();
        if (!table.startsWith("chests/")) return;
        // Evergarden's own chests get the Evergarden roll on first open instead (EvergardenChestLoot).
        // Its sanctuaries fill chests from the End City table outside the End, which no vanilla structure does.
        if (event.getInventoryHolder() instanceof Container container) {
            EvergardenChestLoot evergarden = plugin.evergardenChests();
            boolean sanctuary = settings.evergardenEndCityOutsideEnd && table.equals("chests/end_city_treasure")
                && event.getWorld().getEnvironment() != World.Environment.THE_END;
            if (sanctuary) evergarden.markEvergardenChest(container.getBlock());
            if (sanctuary || evergarden.evergardenChunk(container.getBlock().getChunk())) return;
        }
        Map<Rarity, Integer> weights = settings.weights();
        for (String rich : settings.richTables) {
            if (table.contains(rich)) {
                weights = settings.richWeights;
                break;
            }
        }
        AffixService affixes = plugin.affixes();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<ItemStack> loot = event.getLoot();
        int converted = 0;
        for (ItemStack item : loot) {
            if (affixes.category(item) == null || affixes.salvageLocked(item) || affixes.read(item) != null) continue;
            if (random.nextDouble() < settings.convertChance && affixes.affix(item, affixes.rollRarity(random, weights), random)) converted++;
        }
        StringBuilder extra = new StringBuilder();
        if (random.nextDouble() < settings.bonusGearChance) {
            ItemStack gear = plugin.gear().affixed(affixes.rollRarity(random, weights), random, event.getWorld().getEnvironment());
            loot.add(gear);
            extra.append(" bonus=").append(gear.getType().getKey().getKey());
        }
        if (random.nextDouble() < settings.materialChance) {
            Rarity rarity = affixes.rollRarity(random, weights);
            loot.add(affixes.material(rarity, random.nextInt(settings.materialMin, settings.materialMax + 1)));
            extra.append(" material=").append(rarity);
        }
        if (random.nextDouble() < settings.lootGemChance) {
            GemService.Gem gem = plugin.gems().roll(affixes.rollRarity(random, weights), random);
            loot.add(plugin.gems().item(gem, 1));
            extra.append(" gem=").append(gem.serialize());
        }
        if (converted > 0 || !extra.isEmpty()) plugin.getLogger().fine("[Forge-Loot] table=" + table + " converted=" + converted + extra);
    }

    // ---- gems, dust and materials are not vanilla ingredients ------------------------------------

    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (forgeItem(item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onSmith(PrepareSmithingEvent event) {
        for (ItemStack item : event.getInventory().getContents()) {
            if (forgeItem(item)) {
                event.setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent event) {
        for (ItemStack item : event.getInventory().getContents()) {
            if (forgeItem(item)) {
                event.setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onStationClick(InventoryClickEvent event) {
        if (!VANILLA_STATIONS.contains(event.getView().getTopInventory().getType())) return;
        boolean intoTop = event.getRawSlot() < event.getView().getTopInventory().getSize();
        if ((intoTop && (forgeItem(event.getCursor()) || hotbarForgeItem(event)))
            || (!intoTop && event.isShiftClick() && forgeItem(event.getCurrentItem()))) {
            event.setCancelled(true);
        }
    }

    private boolean hotbarForgeItem(InventoryClickEvent event) {
        return event.getHotbarButton() >= 0 && forgeItem(event.getWhoClicked().getInventory().getItem(event.getHotbarButton()))
            || event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND
                && forgeItem(event.getWhoClicked().getInventory().getItemInOffHand());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onStationDrag(InventoryDragEvent event) {
        if (!VANILLA_STATIONS.contains(event.getView().getTopInventory().getType()) || !forgeItem(event.getOldCursor())) return;
        int top = event.getView().getTopInventory().getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < top) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /** No throwing a gem pearl, lighting fire with a gem fire charge, or feeding a gem scute to a wolf. */
    @EventHandler(priority = EventPriority.LOW)
    public void onUse(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL || !forgeItem(event.getItem()) || plugin.charms().isCharm(event.getItem())) return;
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
            && event.getClickedBlock().getType() == plugin.settings().stationBlock && event.getPlayer().isSneaking()) return;
        event.setUseItemInHand(Event.Result.DENY);
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) event.setUseInteractedBlock(Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onUseEntity(PlayerInteractEntityEvent event) {
        ItemStack hand = event.getPlayer().getInventory().getItem(event.getHand());
        if (forgeItem(hand)) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (forgeItem(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAutomatedIngredient(InventoryMoveItemEvent event) {
        if (forgeItem(event.getItem()) && VANILLA_STATIONS.contains(event.getDestination().getType())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent event) {
        if (event.getBlock().getState() instanceof org.bukkit.block.Crafter crafter)
            for (ItemStack item : crafter.getInventory().getContents()) if (forgeItem(item)) {event.setCancelled(true);return;}
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        var inventory = event.getInventory();
        for (int slot=0;slot<inventory.getSize();slot++) {
            ItemStack item=inventory.getItem(slot);
            if (plugin.gems().refresh(item) | ForgeResources.model(plugin,item)) inventory.setItem(slot,item);
        }
        if (event.getPlayer() instanceof Player player) migrate(player.getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        ItemStack item=event.getItem().getItemStack();
        if(plugin.gems().refresh(item) | plugin.affixes().ensure(item) | ForgeResources.model(plugin,item))event.getItem().setItemStack(item);
    }

    private void migrate(PlayerInventory inventory) {
        for(int slot=0;slot<inventory.getSize();slot++)ensure(inventory,slot);
    }

    // ---- forge station ---------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStation(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getPlayer().isSneaking() || event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != plugin.settings().stationBlock) return;
        if (!event.getPlayer().hasPermission("forge.use")) return;
        event.setCancelled(true);
        plugin.menu().open(event.getPlayer(), ForgeMenu.Page.REFORGE);
    }

    // ---- keep affixes applied after other plugins rewrite an item --------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        int slot = event.getNewSlot();
        // Other plugins refresh the held weapon in this same event; check after them.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.getInventory().getHeldItemSlot() == slot) ensure(player.getInventory(), slot);
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) migrate(player.getInventory());
        }, 40);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) ensureEquipment(player);
        });
    }

    private void ensureEquipment(Player player) {
        PlayerInventory inventory = player.getInventory();
        ensure(inventory, inventory.getHeldItemSlot());
        for (int slot = 36; slot <= 40; slot++) ensure(inventory, slot);
    }

    private void ensure(PlayerInventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        if (item == null || item.getType().isAir()) return;
        if (plugin.affixes().ensure(item) | plugin.gems().refresh(item) | ForgeResources.model(plugin,item)) inventory.setItem(slot, item);
    }
}
