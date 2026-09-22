package com.example.voidscape.crop;

import com.example.voidscape.VoidscapePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Immortal, immovable, unlimited-trade Botanist Villager NPC at Evergarden Spawn Island.
 */
public final class BotanistNpc implements Listener {
    private final VoidscapePlugin plugin;
    private UUID npcId = null;
    private Location homeLoc = null;

    public BotanistNpc(VoidscapePlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        if (plugin.world() == null) return;
        this.homeLoc = new Location(plugin.world(), 2.5, 97.0, 2.5, 135.0f, 0.0f);
        Chunk chunk = homeLoc.getChunk();
        if (!chunk.isLoaded()) {
            chunk.load(true);
        }
        chunk.addPluginChunkTicket(plugin);
        ensureSpawned();
    }

    public void ensureSpawned() {
        if (plugin.world() == null) return;
        if (homeLoc == null) homeLoc = new Location(plugin.world(), 2.5, 97.0, 2.5, 135.0f, 0.0f);

        Chunk chunk = homeLoc.getChunk();
        if (!chunk.isLoaded()) {
            chunk.load(true);
        }
        chunk.addPluginChunkTicket(plugin);

        // Check if existing entity is valid
        if (npcId != null) {
            Entity e = Bukkit.getEntity(npcId);
            if (e != null && e.isValid() && !e.isDead() && isBotanist(e)) {
                setupTrades((Villager) e);
                return;
            }
        }

        // Clean up duplicate botanists and pick one
        List<Villager> found = new ArrayList<>();
        for (Entity e : homeLoc.getWorld().getEntitiesByClass(Villager.class)) {
            if (isBotanist(e) && e.isValid() && !e.isDead()) {
                found.add((Villager) e);
            }
        }

        if (!found.isEmpty()) {
            Villager primary = found.get(0);
            this.npcId = primary.getUniqueId();
            applyAttributes(primary);
            setupTrades(primary);
            // Purge all extra duplicates that were spawned in loop
            for (int i = 1; i < found.size(); i++) {
                found.get(i).remove();
            }
            return;
        }

        // Spawn exactly ONE new Botanist
        Villager villager = homeLoc.getWorld().spawn(homeLoc, Villager.class, v -> {
            v.getPersistentDataContainer().set(plugin.key("botanist_npc"), PersistentDataType.BYTE, (byte) 1);
            applyAttributes(v);
        });

        this.npcId = villager.getUniqueId();
        setupTrades(villager);
        plugin.getLogger().info("[BotanistNpc] Spawned Evergarden Botanist NPC at " + homeLoc.getBlockX() + ", " + homeLoc.getBlockY() + ", " + homeLoc.getBlockZ());
    }

    private void applyAttributes(Villager v) {
        v.customName(Component.text("🌿 ผู้ดูแลพฤกษามิติ (Evergarden Botanist)", NamedTextColor.GREEN, TextDecoration.BOLD));
        v.setCustomNameVisible(true);
        v.setInvulnerable(true);
        v.setAI(false);
        v.setCollidable(false);
        v.setGravity(false);
        v.setSilent(true);
        v.addScoreboardTag("evergarden_mob");
        v.addScoreboardTag("no-level");
        v.addScoreboardTag("no_level");
        v.setPersistent(true);
        v.setRemoveWhenFarAway(false);
        v.setProfession(Villager.Profession.FARMER);
        v.setVillagerType(Villager.Type.JUNGLE);
        v.setVillagerLevel(5);
        v.teleport(homeLoc);
    }

    public void setupTrades(Villager v) {
        if (plugin.crops() == null || plugin.crops().factory() == null || plugin.relics() == null) return;
        CropItemFactory factory = plugin.crops().factory();
        List<MerchantRecipe> recipes = new ArrayList<>();

        // Tier 1 seeds: 1 Astral Dust -> 1 Seed
        for (CropType crop : List.of(
            CropType.MANA_DEW_BERRY,
            CropType.CHAMELEON_LEAF,
            CropType.FAIRY_MUSHROOM,
            CropType.MAGNETIC_SQUASH,
            CropType.MOUNTAIN_WALKER_BAMBOO,
            CropType.LUMBERJACK_ACORN
        )) {
            MerchantRecipe recipe = new MerchantRecipe(factory.createSeed(crop, 1), Integer.MAX_VALUE);
            recipe.addIngredient(plugin.relics().createAstralDust(1));
            configureUnlimited(recipe);
            recipes.add(recipe);
        }

        // Sell crops for Astral Dust: 6 Harvested Produce -> 2 Astral Dust
        for (CropType crop : List.of(
            CropType.MANA_DEW_BERRY,
            CropType.CHAMELEON_LEAF,
            CropType.FAIRY_MUSHROOM,
            CropType.MAGNETIC_SQUASH,
            CropType.MOUNTAIN_WALKER_BAMBOO,
            CropType.LUMBERJACK_ACORN
        )) {
            MerchantRecipe recipe = new MerchantRecipe(plugin.relics().createAstralDust(2), Integer.MAX_VALUE);
            recipe.addIngredient(factory.createFood(crop, 6));
            configureUnlimited(recipe);
            recipes.add(recipe);
        }

        // Farming Utilities
        // 2 Astral Dust -> 1 Water Bucket
        MerchantRecipe water = new MerchantRecipe(new ItemStack(Material.WATER_BUCKET, 1), Integer.MAX_VALUE);
        water.addIngredient(plugin.relics().createAstralDust(2));
        configureUnlimited(water);
        recipes.add(water);

        v.setRecipes(recipes);
    }

    private void configureUnlimited(MerchantRecipe recipe) {
        recipe.setMaxUses(Integer.MAX_VALUE);
        recipe.setUses(0);
        recipe.setDemand(0);
        recipe.setPriceMultiplier(0.0f);
        recipe.setExperienceReward(false);
    }

    public boolean isBotanist(Entity e) {
        if (!(e instanceof Villager)) return false;
        return e.getPersistentDataContainer().has(plugin.key("botanist_npc"), PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (isBotanist(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicle(VehicleEnterEvent e) {
        if (isBotanist(e.getEntered()) || isBotanist(e.getVehicle())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent e) {
        if (isBotanist(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (isBotanist(e.getRightClicked())) {
            Villager v = (Villager) e.getRightClicked();
            // Refresh recipe uses to 0 every time so it never locks!
            List<MerchantRecipe> current = v.getRecipes();
            for (MerchantRecipe r : current) {
                r.setUses(0);
                r.setMaxUses(Integer.MAX_VALUE);
                r.setDemand(0);
            }
            v.setRecipes(current);
            Player p = e.getPlayer();
            p.playSound(v.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.9f, 1.1f);
        }
    }

    public void tick() {
        if (plugin.world() == null || homeLoc == null) return;
        // Do not attempt to spawn or query entities if the chunk is not loaded
        if (!homeLoc.getWorld().isChunkLoaded(homeLoc.getBlockX() >> 4, homeLoc.getBlockZ() >> 4)) {
            return;
        }
        if (npcId == null) {
            ensureSpawned();
            return;
        }
        Entity e = Bukkit.getEntity(npcId);
        if (e == null || !e.isValid() || e.isDead()) {
            ensureSpawned();
            return;
        }
        if (e instanceof Villager v) {
            // Keep exactly locked in place
            if (v.getLocation().distanceSquared(homeLoc) > 0.05) {
                v.teleport(homeLoc);
            }
        }
    }

    public void close() {
        if (homeLoc != null && homeLoc.getWorld() != null) {
            try {
                homeLoc.getChunk().removePluginChunkTicket(plugin);
            } catch (Exception ignored) {}
        }
    }
}
