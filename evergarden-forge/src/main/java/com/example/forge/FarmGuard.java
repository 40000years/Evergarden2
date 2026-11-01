package com.example.forge;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps mob farms from printing forge loot without hurting exploration. Vanilla drops and XP are never
 * touched; only the forge's own chances (ordinary-mob gems, elite spawns, elite rewards) change, silently:
 * <ul>
 *   <li>area heat: hostile deaths in the surrounding chunks add heat that fades with a half-life. The
 *       first few kills in a spot keep the full rate; past that the rate slides down smoothly, so ten
 *       endermen in a row already halve it and a kill chamber gets next to nothing, while walking on to
 *       fresh ground restores it;</li>
 *   <li>environmental damage (fall, lava, magma, suffocation, cramming, drowning, cactus, powder snow)
 *       before death: the classic "drop it, then one-hit it" farm gives nothing;</li>
 *   <li>an optional hourly cap per player on ordinary-mob gems (off by default).</li>
 * </ul>
 * Heat lives in memory only; a restart starts every area cold.
 */
public final class FarmGuard implements Listener {
    private record Area(UUID world, int x, int z) {}

    private static final class Heat {
        double value;
        long time;
    }

    private final ForgePlugin plugin;
    private final NamespacedKey taintKey;
    private final Map<Area, Heat> heat = new HashMap<>();
    private final Map<UUID, Deque<Long>> ordinaryGems = new HashMap<>();
    private BukkitTask prune;

    public FarmGuard(ForgePlugin plugin) {
        this.plugin = plugin;
        taintKey = new NamespacedKey(plugin, "environment_damage");
    }

    public void start() {
        prune = Bukkit.getScheduler().runTaskTimer(plugin, this::prune, 6000L, 6000L);
    }

    public void stop() {
        if (prune != null) prune.cancel();
        heat.clear();
        ordinaryGems.clear();
    }

    // ---- area heat -----------------------------------------------------------------------------

    private double decayed(Heat entry, long now) {
        double halfLife = plugin.settings().farmHalfLifeMillis;
        return entry.value * Math.pow(0.5, (now - entry.time) / halfLife);
    }

    /** Adds one hostile death to the chunk at this location. */
    public void recordDeath(Location location, long now) {
        Heat entry = heat.computeIfAbsent(new Area(location.getWorld().getUID(), location.getBlockX() >> 4, location.getBlockZ() >> 4), area -> new Heat());
        entry.value = decayed(entry, now) + 1;
        entry.time = now;
    }

    /** Heat of the chunks around a location (radius from farm-guard.area-radius-chunks). */
    public double heat(Location location, long now) {
        int radius = plugin.settings().farmAreaRadius;
        int cx = location.getBlockX() >> 4, cz = location.getBlockZ() >> 4;
        UUID world = location.getWorld().getUID();
        double total = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Heat entry = heat.get(new Area(world, cx + dx, cz + dz));
                if (entry != null) total += decayed(entry, now);
            }
        }
        return total;
    }

    /**
     * Multiplier on forge chances at a location: 1 up to farm-guard.free-kills heat, then
     * exp(-(heat - free) / falloff). With the defaults (5, 10): heat 10 = 61%, 15 = 37%, 20 = 22%, 40 = 3%.
     */
    public double multiplier(Location location) {
        return multiplier(heat(location, System.currentTimeMillis()));
    }

    public double multiplier(double heat) {
        ForgeSettings settings = plugin.settings();
        if (!settings.farmGuardEnabled || heat <= settings.farmFreeKills) return 1;
        return Math.exp(-(heat - settings.farmFreeKills) / settings.farmFalloff);
    }

    private void prune() {
        long now = System.currentTimeMillis();
        heat.values().removeIf(entry -> decayed(entry, now) < 0.05);
        long hourAgo = now - 3_600_000L;
        ordinaryGems.values().forEach(times -> {
            while (!times.isEmpty() && times.peekFirst() < hourAgo) times.pollFirst();
        });
        ordinaryGems.values().removeIf(Deque::isEmpty);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        // Recorded after the reward handlers (HIGH) have read the heat, whoever or whatever killed it.
        if (event.getEntity() instanceof Enemy) recordDeath(event.getEntity().getLocation(), System.currentTimeMillis());
    }

    // ---- environmental damage ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Enemy) || !(event.getEntity() instanceof LivingEntity living)) return;
        if (!plugin.settings().farmTaintCauses.contains(event.getCause())) return;
        living.getPersistentDataContainer().set(taintKey, PersistentDataType.BYTE, (byte) 1);
    }

    public boolean tainted(LivingEntity entity) {
        return plugin.settings().farmGuardEnabled && entity.getPersistentDataContainer().has(taintKey, PersistentDataType.BYTE);
    }

    /** Rolls whether this kill gives forge rewards: never after environmental damage, else the area multiplier. */
    public boolean rewardable(LivingEntity entity, java.util.Random random) {
        return !tainted(entity) && random.nextDouble() < multiplier(entity.getLocation());
    }

    // ---- hourly cap on ordinary-mob gems -------------------------------------------------------

    /** Whether the player may receive one more ordinary-mob gem this hour; records it when true. */
    public boolean takeOrdinaryGem(Player player) {
        int cap = plugin.settings().farmOrdinaryGemsPerHour;
        // A negative cap means no limit.
        if (!plugin.settings().farmGuardEnabled || cap < 0) return true;
        long now = System.currentTimeMillis();
        Deque<Long> times = ordinaryGems.computeIfAbsent(player.getUniqueId(), id -> new ArrayDeque<>());
        while (!times.isEmpty() && times.peekFirst() < now - 3_600_000L) times.pollFirst();
        if (times.size() >= cap) return false;
        times.addLast(now);
        return true;
    }
}
