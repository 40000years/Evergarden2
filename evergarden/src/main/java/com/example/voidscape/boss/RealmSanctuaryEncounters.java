package com.example.voidscape.boss;

import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.DimensionalSanctuaries;
import com.example.voidscape.world.DimensionalSanctuaries.Arena;
import com.example.voidscape.world.DimensionalSanctuaries.Site;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Manages the four Dimensional Sanctuary boss encounters in the Nether and The End:
 * <ul>
 *   <li>{@code crown-fortress} &rarr; <b>Cinder Colossus</b></li>
 *   <li>{@code bone-temple} &rarr; <b>Soul-Eater Hound</b></li>
 *   <li>{@code crystal-city} &rarr; <b>Crystal Moth Queen</b></li>
 *   <li>{@code void-ring} &rarr; <b>Unlit Sun</b></li>
 * </ul>
 * Includes waiting boss sculptures, attack-initiated activation, airborne combat support
 * (Wind Charge &amp; Mace), 15-second empty-arena reset, and The End arena boundary + Void knock-off rescue.
 */
public final class RealmSanctuaryEncounters implements Listener, AutoCloseable {

    private static final class Run {
        final World world;
        final Site site;
        final Arena arena;
        final Location center;
        RealmBossController controller;
        boolean fighting;
        boolean dead;
        boolean wipePending;
        boolean warnedEmpty;
        long emptySince = -1;
        long readyAt;
        final Set<Chunk> tickets = new HashSet<>();
        final Map<UUID, Long> lastRescueTick = new HashMap<>();
        final Set<UUID> engagedCreative = new HashSet<>();
        /** Fallen fighters sit out this fight, so they can retrieve their drops safely. */
        final Set<UUID> fallen = new HashSet<>();
        final ArrayDeque<int[]> sweep = new ArrayDeque<>();
        int swept;

        Run(World world, Site site, Arena arena, Location center) {
            this.world = world;
            this.site = site;
            this.arena = arena;
            this.center = center;
        }
    }

    private final VoidscapePlugin plugin;
    private final Map<String, Run> runs = new LinkedHashMap<>();
    private final File file;
    private final YamlConfiguration ledger;
    private final org.bukkit.scheduler.BukkitTask task;
    private final Map<UUID, Integer> interactions = new HashMap<>();
    private record TrackedShot(AbstractArrow arrow, Location previous) {}
    private final Map<UUID, TrackedShot> shots = new HashMap<>();
    /** Arrows loosed from outside the ring; the sanctuary ward dissolves them where they cross it. */
    private record WardedShot(AbstractArrow arrow, Run run) {}
    private final Map<UUID, WardedShot> warded = new HashMap<>();
    private final Map<UUID, Long> wardNotices = new HashMap<>();
    private record SunFire(UUID world, long expires) {}
    private final Map<UUID, SunFire> sunFire = new HashMap<>();
    /** Wither is claimed only if its effect event identifies this boss as the source. */
    private final Map<UUID, SunFire> sunWither = new HashMap<>();
    /** Attacks count only from inside the drawn ring, with a little slack for the border particles. */
    private static final double RING_SLACK = 0.5;
    private final NamespacedKey ringShotKey;
    private long tick;
    private boolean closed;

    public RealmSanctuaryEncounters(VoidscapePlugin plugin) {
        this.plugin = plugin;
        this.ringShotKey = plugin.key("realm_ring_shot");
        this.file = new File(plugin.getDataFolder(), "dimensional-sanctuary-bosses.yml");
        this.ledger = YamlConfiguration.loadConfiguration(file);
        if (!ledger.getBoolean("migrated-respawn-600", false)) {
            ledger.set("migrated-respawn-600", true);
            ledger.set("game-cooldowns", null);
            if (plugin.getConfig().getLong("dimensional-sanctuaries.bosses.respawn-ticks", 600L) == 24000L) {
                plugin.getConfig().set("dimensional-sanctuaries.bosses.respawn-ticks", 600L);
                plugin.saveConfig();
            }
            save();
        }
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getPersistentDataContainer().has(plugin.key("realm_sanctuary_boss"), PersistentDataType.BYTE)) {
                    e.remove();
                }
            }
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getPluginManager().registerEvents(new RealmCores(plugin), plugin);
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (World w : Bukkit.getWorlds()) {
                for (Chunk c : w.getLoadedChunks()) discover(c);
            }
        }, 40);
    }

    public boolean owns(Entity entity) {
        if (entity == null) return false;
        for (Run r : runs.values()) {
            if (r.controller != null && r.controller.owns(entity)) return true;
        }
        return false;
    }

    private String id(World world, Site site) {
        return world.getUID() + "_" + site.kind().id + "_" + site.x() + "_" + site.z();
    }

    private boolean enabled() {
        return !closed
            && plugin.getConfig().getBoolean("dimensional-sanctuaries.enabled", true)
            && plugin.getConfig().getBoolean("dimensional-sanctuaries.bosses.enabled", true);
    }

    private long respawnTicks() {
        return Math.max(100L, plugin.getConfig().getLong("dimensional-sanctuaries.bosses.respawn-ticks", 600L));
    }

    private long emptyResetTicks() {
        return Math.max(5L, plugin.getConfig().getLong("dimensional-sanctuaries.bosses.empty-reset-seconds", 15L)) * 20L;
    }

    private boolean playable(Player p) {
        return p != null && p.isOnline() && !p.isDead()
            && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE);
    }

    private boolean canAttack(Player p) {
        return p != null && p.isOnline() && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR;
    }

    private boolean playableInRun(Run r, Player p) {
        return p != null && !r.fallen.contains(p.getUniqueId()) && (playable(p) || (p.isOnline() && !p.isDead()
            && p.getGameMode() == GameMode.CREATIVE && r.engagedCreative.contains(p.getUniqueId())));
    }

    /**
     * Active combat volume includes high air above the floor so Wind Charge + Mace jumps
     * never count as leaving the arena.
     */
    private boolean contains(Run r, Location loc) {
        if (loc == null || loc.getWorld() != r.world || !Double.isFinite(loc.getY())) return false;
        double dy = loc.getY() - r.center.getY();
        if (dy < -3.5 || dy > r.arena.height() + 28.0) return false;
        return Math.hypot(loc.getX() - r.center.getX(), loc.getZ() - r.center.getZ()) <= r.arena.radius() + 1.5;
    }

    /**
     * Inside the drawn ring. Presence (and so the empty-arena reset) still uses {@link #contains}, but hits, shots
     * and orb breaks only count from here: the band past the line is where the boss and its attacks cannot reach.
     */
    private boolean insideRing(Run r, Location loc) {
        return contains(r, loc)
            && Math.hypot(loc.getX() - r.center.getX(), loc.getZ() - r.center.getZ()) <= r.arena.radius() + RING_SLACK;
    }

    /** A projectile counts by where it was loosed; anything else by where the attacker stands now. */
    private boolean fromRing(Run r, Player p, Entity direct) {
        if (r.wipePending || r.fallen.contains(p.getUniqueId())) return false;
        Byte launched = direct instanceof Projectile shot
            ? shot.getPersistentDataContainer().get(ringShotKey, PersistentDataType.BYTE) : null;
        if (launched != null) return launched == 1 && contains(r, p.getLocation());
        return insideRing(r, p.getLocation());
    }

    private void wardNotice(Player p) {
        long last = wardNotices.getOrDefault(p.getUniqueId(), Long.MIN_VALUE / 2);
        if (tick - last < 40) return;
        wardNotices.put(p.getUniqueId(), tick);
        p.sendActionBar(Component.text("§c✦ ม่านวิหารสลายการโจมตีจากนอกวง — เข้ามาสู้ในวงแหวน"));
        p.playSound(p.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.8f, 1.4f);
    }

    private boolean containsActivation(Run r, Location loc) {
        return contains(r, loc) && loc.getY() <= r.center.getY() + 18.0;
    }

    private List<Player> players(Run r) {
        return r.world.getPlayers().stream().filter(p -> playableInRun(r, p) && contains(r, p.getLocation())).toList();
    }

    public void discover(Chunk chunk) {
        if (!enabled() || chunk == null || !chunk.isLoaded()) return;
        var service = plugin.dimensionalSanctuaries();
        if (service == null || !service.enabled(chunk.getWorld())) return;
        Site site = service.siteAt(chunk.getWorld(), (chunk.getX() << 4) + 8, (chunk.getZ() << 4) + 8);
        if (site != null) discoverSite(chunk.getWorld(), site);
    }

    public RealmBossController discoverSite(World world, Site site) {
        if (!enabled() || world == null || site == null) return null;
        var service = plugin.dimensionalSanctuaries();
        if (service == null || !service.enabled(world)) return null;
        String key = id(world, site);
        Run existing = runs.get(key);
        if (existing != null) {
            if (existing.controller == null && !existing.dead
                    && world.isChunkLoaded(existing.center.getBlockX() >> 4, existing.center.getBlockZ() >> 4)
                    && clearSpawn(world, existing.center)) {
                spawn(existing);
            }
            return existing.controller;
        }
        Arena arena = service.arena(site);
        float yaw = new float[]{0f, 90f, 180f, -90f}[site.rotation() & 3];
        Location center = new Location(world, arena.x() + 0.5, arena.y() + 1.0, arena.z() + 0.5, yaw, 0f);
        if (!world.isChunkLoaded(center.getBlockX() >> 4, center.getBlockZ() >> 4)) return null;
        if (!clearSpawn(world, center)) return null;

        Run r = new Run(world, site, arena, center);
        r.readyAt = ledger.getLong("game-cooldowns." + key, 0L);
        runs.put(key, r);
        spawn(r);
        return r.controller;
    }

    public RealmBossController controllerAt(World world, Site site) {
        if (world == null || site == null) return null;
        Run r = runs.get(id(world, site));
        return r != null ? r.controller : null;
    }

    public int activeRunsCount() {
        return runs.size();
    }

    private boolean clearSpawn(World w, Location c) {
        int cx = c.getBlockX(), cy = c.getBlockY(), cz = c.getBlockZ();
        if (!w.isChunkLoaded(cx >> 4, cz >> 4)) return false;
        if (!w.getBlockAt(cx, cy - 1, cz).getType().isSolid()) return false;
        for (int y = 0; y < 8; y++) {
            if (!w.getBlockAt(cx, cy + y, cz).isPassable()) return false;
        }
        return true;
    }

    private void spawn(Run r) {
        if (r.controller != null) r.controller.close();
        r.dead = false;
        r.fighting = false;
        r.wipePending = false;
        r.fallen.clear();
        r.emptySince = -1;
        r.warnedEmpty = false;
        r.engagedCreative.clear();
        r.controller = new RealmBossController(
            plugin,
            r.site,
            r.arena,
            r.center,
            p -> playableInRun(r, p) && contains(r, p.getLocation()),
            defeat -> {
                r.dead = true;
                r.fighting = false;
                r.engagedCreative.clear();
                r.readyAt = r.world.getGameTime() + respawnTicks();
                ledger.set("game-cooldowns." + id(r.world, r.site), r.readyAt);
                save();
                release(r);
                grantVictoryRewards(r, defeat);
            }
        );
        if (r.world.getGameTime() < r.readyAt) {
            r.dead = true;
            r.controller.close();
            return;
        }
        r.controller.spawnWaiting(tick);
        queueSweep(r);
    }

    /**
     * Queue the arena chunks for clean-up: leftover blocks inside the combat volume (fossils, fungus stems,
     * anything a sealed chunk kept) would be perches the floor-bound boss cannot reach. A few chunks run per tick.
     */
    private void queueSweep(Run r) {
        r.sweep.clear();
        int reach = (int) Math.ceil(r.arena.radius() + 2.0);
        for (int x = (r.arena.x() - reach) >> 4; x <= (r.arena.x() + reach) >> 4; x++) {
            for (int z = (r.arena.z() - reach) >> 4; z <= (r.arena.z() + reach) >> 4; z++) {
                r.sweep.add(new int[]{x, z});
            }
        }
    }

    private void sweep(Run r, int budget) {
        var service = plugin.dimensionalSanctuaries();
        int feet = r.center.getBlockY();
        while (budget-- > 0 && !r.sweep.isEmpty()) {
            int[] c = r.sweep.poll();
            if (service == null || !r.world.isChunkLoaded(c[0], c[1])) continue;
            // The whole volume contains() counts as the arena, plus the blocks players there can stand on
            r.swept += service.clearArena(r.world, r.world.getChunkAt(c[0], c[1]), r.site,
                r.arena.radius() + 2.0, feet - 4, feet + r.arena.height() + 28);
        }
        if (r.sweep.isEmpty() && r.swept > 0) {
            plugin.getLogger().info("Cleared " + r.swept + " stray blocks from the " + r.site.kind().id
                + " boss arena at " + r.arena.x() + "," + r.arena.y() + "," + r.arena.z());
            r.swept = 0;
        }
    }

    private void save() {
        try {
            ledger.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Cannot save dimensional sanctuary boss cooldowns", ex);
        }
    }

    /** Track only fire that Unlit Sun started itself, never existing environmental fire. */
    void trackSunFire(Player player, int before, int duration) {
        UUID id = player.getUniqueId();
        long now = Bukkit.getCurrentTick();
        SunFire existing = sunFire.get(id);
        boolean alreadyOurs = existing != null && existing.expires() >= now
            && existing.world().equals(player.getWorld().getUID());
        if (before > 0 && !alreadyOurs) return;
        sunFire.put(id, new SunFire(player.getWorld().getUID(), now + Math.max(duration, player.getFireTicks()) + 2L));
    }

    /** A later ignition from any other source makes the origin ambiguous. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void invalidateSunFire(EntityCombustEvent event) {
        if (event.getEntity() instanceof Player player) sunFire.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSunWither(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getModifiedType() != PotionEffectType.WITHER) return;
        UUID id = player.getUniqueId();
        sunWither.remove(id);
        PotionEffect added = event.getNewEffect();
        if (added == null || (event.getOldEffect() != null && !event.isOverride())) return;
        Entity source = event.getSource();
        if (source == null) return;
        for (Run r : runs.values()) {
            if (r.site.kind() == DimensionalSanctuaries.Kind.VOID_RING && r.controller != null && r.controller.owns(source)) {
                long duration = added.getDuration() == PotionEffect.INFINITE_DURATION ? Integer.MAX_VALUE : added.getDuration();
                sunWither.put(id, new SunFire(player.getWorld().getUID(), Bukkit.getCurrentTick() + duration + 2L));
                return;
            }
        }
    }

    /** Rename only direct hits from the Unlit Sun proxy or fire definitely started by that boss. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossDeathMessage(PlayerDeathEvent event) {
        Player player = event.getEntity();
        DamageSource source = event.getDamageSource();
        boolean sun = false;
        Entity cause = source.getCausingEntity(), direct = source.getDirectEntity();
        for (Run r : runs.values()) {
            if (r.site.kind() != DimensionalSanctuaries.Kind.VOID_RING || r.controller == null) continue;
            if (r.controller.owns(cause) || r.controller.owns(direct)) { sun = true; break; }
        }
        if (!sun && source.getDamageType() == DamageType.ON_FIRE) {
            SunFire fire = sunFire.get(player.getUniqueId());
            sun = fire != null && fire.world().equals(player.getWorld().getUID())
                && fire.expires() >= Bukkit.getCurrentTick() && player.getFireTicks() > 0;
        }
        if (!sun && source.getDamageType() == DamageType.WITHER) {
            SunFire wither = sunWither.get(player.getUniqueId());
            sun = wither != null && wither.world().equals(player.getWorld().getUID())
                && wither.expires() >= Bukkit.getCurrentTick() && player.hasPotionEffect(PotionEffectType.WITHER);
        }
        if (!sun) return;
        Component message = Component.text(player.getName() + " ถูกสังหารโดย Unlit Sun");
        if (event.deathMessage() != null) event.deathMessage(message);
        event.deathScreenMessageOverride(message);
    }

    private boolean hasLivingFighter(Run r, Set<UUID> participants) {
        // A player may have just entered the arena after the boss's previous tick.
        return !players(r).isEmpty() || participants.stream().map(Bukkit::getPlayer)
            .anyMatch(other -> other != null && other.getWorld() == r.world && playableInRun(r, other));
    }

    /** A real death is different from leaving the arena or being rescued by a Totem. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFighterDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        sunFire.remove(player.getUniqueId());
        sunWither.remove(player.getUniqueId());
        for (Run r : new ArrayList<>(runs.values())) {
            if (r.site.kind() != DimensionalSanctuaries.Kind.VOID_RING || !r.fighting || r.dead
                    || r.wipePending || r.controller == null) continue;
            Set<UUID> participants = r.controller.participants();
            if (!participants.contains(player.getUniqueId())) continue;
            r.fallen.add(player.getUniqueId());
            r.controller.playerDefeated(player.getUniqueId());
            // A living participant elsewhere in the same world still gets the normal leave-arena
            // grace. Offline, spectators and players who changed worlds cannot keep a wipe alive.
            if (!hasLivingFighter(r, participants)) {
                r.wipePending = true;
                RealmBossController wiped = r.controller;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Do not overwrite a victory, admin reset, or encounter removal in the meantime.
                    if (!closed && runs.containsValue(r) && r.wipePending && !r.dead && r.controller == wiped) reset(r);
                });
            }
        }
    }

    @EventHandler public void onFighterQuit(PlayerQuitEvent event) {
        sunFire.remove(event.getPlayer().getUniqueId());
        sunWither.remove(event.getPlayer().getUniqueId());
    }

    private void hold(Run r) {
        int radChunks = (r.arena.radius() + 16) >> 4;
        int cx = r.center.getBlockX() >> 4, cz = r.center.getBlockZ() >> 4;
        for (int x = cx - radChunks; x <= cx + radChunks; x++) {
            for (int z = cz - radChunks; z <= cz + radChunks; z++) {
                Chunk chunk = r.world.getChunkAt(x, z);
                chunk.addPluginChunkTicket(plugin);
                r.tickets.add(chunk);
            }
        }
        // Every arena chunk is loaded now, so the fight starts on a clean arena
        queueSweep(r);
    }

    private void release(Run r) {
        for (Chunk c : r.tickets) c.removePluginChunkTicket(plugin);
        r.tickets.clear();
    }

    private void reset(Run r) {
        if (r.controller != null) r.controller.close();
        release(r);
        spawn(r);
    }

    private void tick() {
        if (closed) return;
        tick++;
        if (tick % 100 == 0) {
            sunFire.values().removeIf(fire -> fire.expires() < Bukkit.getCurrentTick());
            sunWither.values().removeIf(wither -> wither.expires() < Bukkit.getCurrentTick());
        }
        long resetGrace = emptyResetTicks();

        for (Run r : new ArrayList<>(runs.values())) {
            if (!enabled()) {
                if (r.controller != null) r.controller.close();
                release(r);
                runs.remove(id(r.world, r.site));
                continue;
            }
            // A death event may fire inside the boss's damage call. Reset next tick, after Paper
            // finishes death/drop processing, and do not run another attack in the meantime.
            if (r.wipePending) continue;
            if (r.dead) {
                if (r.controller != null) r.controller.close();
                release(r);
                r.fighting = false;
                if (r.world.getGameTime() >= r.readyAt && clearSpawn(r.world, r.center)) {
                    reset(r);
                } else if (tick % 40 == 0) {
                    for (Player p : r.world.getPlayers()) {
                        if (p.getLocation().distanceSquared(r.center) < 48 * 48) {
                            long rem = Math.max(0, (r.readyAt - r.world.getGameTime() + 19) / 20);
                            p.sendActionBar(Component.text("§7วิหารกำลังพักฟื้นพลัง: §e" + rem + "s"));
                        }
                    }
                }
                continue;
            }
            if (!r.sweep.isEmpty()) sweep(r, 4);

            // End Sanctuary Void Rescue check
            if (r.world.getEnvironment() == World.Environment.THE_END) {
                checkEndVoidRescue(r);
            }

            if (r.fighting) {
                List<Player> present = players(r);
                if (present.isEmpty()) {
                    if (r.emptySince < 0) {
                        r.emptySince = tick;
                        r.warnedEmpty = false;
                    }
                    long elapsed = tick - r.emptySince;
                    if (elapsed >= resetGrace) {
                        for (Player p : r.world.getPlayers()) {
                            if (p.getLocation().distanceSquared(r.center) < 72 * 72) {
                                plugin.message(p, "§cบอสกลับสู่สถานะรอเนื่องจากไม่มีผู้เล่นอยู่ในลานครบ " + (resetGrace / 20) + " วินาที");
                                p.sendActionBar(Component.text("§cบอสรีเซ็ตแล้ว — โจมตีในลานเพื่อเริ่มใหม่"));
                            }
                        }
                        reset(r);
                        continue;
                    }
                    long rem = Math.max(1, (resetGrace - elapsed + 19) / 20);
                    if (!r.warnedEmpty) {
                        r.warnedEmpty = true;
                        for (Player p : r.world.getPlayers()) {
                            if (p.getLocation().distanceSquared(r.center) < 72 * 72) {
                                plugin.message(p, "§c⚠ ออกนอกเขตต่อสู้! กลับเข้าลานภายใน " + rem + " วินาที มิฉะนั้นบอสจะรีเซ็ต");
                            }
                        }
                    }
                    if (elapsed % 10 == 0) {
                        for (Player p : r.world.getPlayers()) {
                            if (p.getLocation().distanceSquared(r.center) < 72 * 72) {
                                p.sendActionBar(Component.text("§c⚠ ออกนอกเขตบอส! กลับเข้าลานภายใน §e" + rem + " วินาที §cก่อนบอสรีเซ็ต"));
                            }
                        }
                    }
                    if (elapsed % 20 == 0) {
                        for (Player p : r.world.getPlayers()) {
                            if (p.getLocation().distanceSquared(r.center) < 72 * 72) {
                                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, rem <= 5 ? 1.5f : 0.9f);
                            }
                        }
                    }
                } else if (r.emptySince >= 0) {
                    boolean wasWarned = r.warnedEmpty;
                    r.emptySince = -1;
                    r.warnedEmpty = false;
                    if (wasWarned) {
                        for (Player p : present) {
                            p.sendActionBar(Component.text("§aกลับเข้าสู่ลานประลองแล้ว — การต่อสู้ดำเนินต่อ!"));
                        }
                    }
                }
            }

            if (r.controller != null) {
                if (r.controller.isFighting() && !r.fighting) {
                    r.fighting = true;
                    hold(r);
                } else if (r.fighting && !r.controller.isFighting() && !r.dead) {
                    r.controller.awaken(players(r));
                }
                r.controller.tick(tick);
            }
            if (tick % 10 == 0 && r.fighting) drawArenaBorder(r);
            if (tick % 20 == 0 && !r.fighting && r.world.getPlayers().stream().anyMatch(p -> p.getLocation().distanceSquared(r.center) < 52 * 52)) {
                drawArenaBorder(r);
            }
            if (tick % 40 == 0 && !r.fighting) {
                for (Player p : r.world.getPlayers()) {
                    if (p.getLocation().distanceSquared(r.center) < 42 * 42) {
                        p.sendActionBar(Component.text("§e✦ โจมตี " + r.controller.thaiName() + " ภายในวงแหวนลานประลองเพื่อเริ่มการต่อสู้"));
                    }
                }
            }
        }

        tickShots();
        tickWarded();

        if (tick % 40 == 0 && enabled()) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (plugin.dimensionalSanctuaries() != null && plugin.dimensionalSanctuaries().enabled(p.getWorld())) {
                    discover(p.getLocation().getChunk());
                }
            }
        }
    }

    private static Color ringColor(Run r) {
        return switch (r.site.kind()) {
            case CROWN_FORTRESS -> Color.fromRGB(255, 130, 40);
            case BONE_TEMPLE -> Color.fromRGB(70, 235, 255);
            case CRYSTAL_CITY -> Color.fromRGB(245, 125, 255);
            case VOID_RING -> Color.fromRGB(255, 215, 75);
        };
    }

    private void drawArenaBorder(Run r) {
        boolean isEnd = r.world.getEnvironment() == World.Environment.THE_END;
        var dust = new Particle.DustOptions(ringColor(r), isEnd ? 1.45f : 1.2f);
        int points = 96;
        for (int n = 0; n < points; n++) {
            double a = n * (2 * Math.PI / points);
            Location pt = r.center.clone().add(Math.cos(a) * r.arena.radius(), 0.2, Math.sin(a) * r.arena.radius());
            r.world.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, dust, true);
            if (isEnd && n % 4 == 0) {
                r.world.spawnParticle(Particle.END_ROD, pt.clone().add(0, 0.35, 0), 1, 0, 0, 0, 0);
            }
        }
    }

    /**
     * Rescues players knocked off The End sanctuary arena (`crystal-city` & `void-ring`)
     * before they fall into the Void.
     */
    private void checkEndVoidRescue(Run r) {
        for (Player p : r.world.getPlayers()) {
            if (!playable(p)) continue;
            Location loc = p.getLocation();
            double dx = loc.getX() - r.center.getX();
            double dz = loc.getZ() - r.center.getZ();
            double dist = Math.hypot(dx, dz);
            if (dist > r.arena.radius() + 34.0) continue;

            // Rescue when falling below the arena platform outside the safe floor radius, or deep below the arena
            boolean fellOffEdge = (r.fighting || dist <= r.arena.radius() + 14.0)
                && ((dist >= r.arena.radius() - 2.0 && loc.getY() < r.center.getY() - 4.5)
                    || loc.getY() < r.center.getY() - 10.0);
            if (!fellOffEdge) continue;

            long last = r.lastRescueTick.getOrDefault(p.getUniqueId(), -100L);
            if (tick - last < 25) continue;
            r.lastRescueTick.put(p.getUniqueId(), tick);

            rescuePlayerToArenaEdge(r, p, dx, dz, dist);
        }
    }

    private void rescuePlayerToArenaEdge(Run r, Player p, double dx, double dz, double dist) {
        double safeR = Math.max(4.0, r.arena.radius() - 4.0);
        double ux = dist > 1e-3 ? dx / dist : 0.0;
        double uz = dist > 1e-3 ? dz / dist : 1.0;
        Location safe = new Location(
            r.world,
            r.center.getX() + ux * safeR,
            r.center.getY() + 1.2,
            r.center.getZ() + uz * safeR,
            (float) Math.toDegrees(Math.atan2(ux, -uz)),
            0f
        );
        p.setFallDistance(0f);
        p.teleport(safe);
        p.setVelocity(new Vector(-ux * 0.25, 0.32, -uz * 0.25));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 70, 0, false, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 35, 1, false, false, true));
        r.world.playSound(safe, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1.2f, 1.15f);
        r.world.spawnParticle(Particle.REVERSE_PORTAL, safe.clone().add(0, 1.0, 0), 32, 0.5, 0.8, 0.5, 0.05);
        if (p.getHealth() > 4.0) {
            p.setHealth(Math.max(2.0, p.getHealth() - 4.0));
            p.playHurtAnimation(0);
        }
        p.sendActionBar(Component.text("§d✦ พลังแห่งวิหารช่วยดึงคุณกลับจากเหว Void เข้าสู่ขอบลานประลอง!"));
    }

    // =========================================================================
    // REWARDS & ADMIN COMMANDS
    // =========================================================================

    private void grantVictoryRewards(Run r, RealmBossController.Defeat defeat) {
        Location drop = r.center.clone().add(0, 0.8, 0);
        // One shared core per victory; the per-player spoils below are rolled for each contributor.
        ItemStack core = RealmCores.create(plugin, defeat.kind());
        Item item = r.world.dropItem(drop, core);
        item.setGlowing(true);
        item.setInvulnerable(true);
        item.setVelocity(new Vector(0, 0.2, 0));

        var trader = plugin.seraphTrader();
        int feathers = plugin.integer("seraph-trader.feathers.sanctuary-boss", 8, 0, 64);
        var addon = Bukkit.getPluginManager().getPlugin("advance-magic");

        for (UUID uuid : defeat.contributors()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline() || p.getWorld() != r.world) continue;
            plugin.message(p, "§6✦ พิชิต " + defeat.kind().title + " (" + defeat.kind().home + ") สำเร็จ!");
            if (trader != null) {
                trader.feathers().grant(p, feathers, "พิชิต " + defeat.kind().title, true);
            }
            if (addon instanceof AdvanceMagicPlugin magic && magic.isEnabled() && magic.skillTree() != null) {
                magic.skillTree().grantPoints(p, 1, defeat.kind().title);
            }
            RealmRewards.grant(plugin, p, defeat.kind(), java.util.concurrent.ThreadLocalRandom.current());
        }
    }

    public boolean command(Player player, String[] args) {
        // /sanctuary boss [status|spawn|reset|skill <0-3>]
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "status";
        var service = plugin.dimensionalSanctuaries();
        Site site = service != null ? service.siteAt(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockZ()) : null;
        if (site == null) {
            player.sendMessage("§cคุณไม่ได้อยู่ในบริเวณวิหาร Nether / End · ใช้ /sanctuary tp <kind> เพื่อไปยังวิหารก่อน");
            return true;
        }
        String key = id(player.getWorld(), site);
        switch (sub) {
            case "spawn", "reset" -> {
                ledger.set("game-cooldowns." + key, null);
                save();
                Run r = runs.get(key);
                if (r != null) {
                    r.readyAt = 0;
                    reset(r);
                } else {
                    discoverSite(player.getWorld(), site);
                }
                player.sendMessage("§a✦ รีเซ็ตและอัญเชิญบอสประจำวิหาร " + site.kind().id + " เรียบร้อยแล้ว");
            }
            case "skill" -> {
                Run r = runs.get(key);
                if (r == null || r.controller == null) {
                    player.sendMessage("§cยังไม่มีบอสในวิหารนี้");
                    return true;
                }
                if (!r.fighting) {
                    r.fighting = true;
                    r.controller.awaken(players(r));
                    hold(r);
                }
                int slot = 0;
                if (args.length > 2) {
                    try { slot = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) {}
                }
                r.controller.forceSkill(slot);
                player.sendMessage("§e✦ สั่งบอสใช้ทักษะลำดับที่ " + slot);
            }
            default -> {
                Run r = runs.get(key);
                if (r == null || r.controller == null) {
                    player.sendMessage("§eวิหาร " + site.kind().id + ": ยังไม่ได้โหลดบอส (ลอง /sanctuary boss spawn)");
                } else {
                    player.sendMessage("§b✦ " + r.controller.thaiName() + " (" + r.controller.englishName() + ") · สถานะ: "
                        + (r.dead ? "พักฟื้น" : (r.fighting ? "กำลังต่อสู้" : "รอผู้ท้าชิง"))
                        + " · HP: " + (int) Math.ceil(r.controller.health()) + "/" + (int) Math.ceil(r.controller.maxHealth()));
                }
            }
        }
        return true;
    }

    // =========================================================================
    // EVENT HANDLERS
    // =========================================================================

    private Player attacker(EntityDamageEvent event) {
        if (event.getDamageSource().getCausingEntity() instanceof Player p) return p;
        if (event instanceof EntityDamageByEntityEvent by) {
            if (by.getDamager() instanceof Player p) return p;
            if (by.getDamager() instanceof Projectile shot && shot.getShooter() instanceof Player p) return p;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVoidDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID || !(event.getEntity() instanceof Player p)) return;
        if (p.getWorld().getEnvironment() != World.Environment.THE_END) return;
        for (Run r : runs.values()) {
            if (r.world == p.getWorld()) {
                double dx = p.getX() - r.center.getX(), dz = p.getZ() - r.center.getZ();
                double dist = Math.hypot(dx, dz);
                if (dist <= r.arena.radius() + 40.0) {
                    event.setCancelled(true);
                    rescuePlayerToArenaEdge(r, p, dx, dz, dist);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRapidMelee(EntityDamageEvent event) {
        Player player = attacker(event);
        if (player == null) return;
        for (Run r : new ArrayList<>(runs.values())) {
            if (r.controller != null && r.controller.owns(event.getEntity()) && r.controller.rejectRapidMelee(event, player)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Entity victim = event.getEntity();
        for (Run r : new ArrayList<>(runs.values())) {
            if (r.controller == null || !r.controller.owns(victim)) continue;
            Player p = attacker(event);
            Entity direct = event instanceof EntityDamageByEntityEvent by ? by.getDamager() : event.getDamageSource().getDirectEntity();
            if (p == null || !canAttack(p) || !fromRing(r, p, direct)) {
                event.setCancelled(true);
                if (p != null && canAttack(p)) wardNotice(p);
                return;
            }
            if (p.getGameMode() == GameMode.CREATIVE) {
                r.engagedCreative.add(p.getUniqueId());
            }
            if (r.controller.tryHitDestructibleNode(p, victim, 64.0)) {
                event.setCancelled(true);
                return;
            }
            if (r.fighting && r.emptySince >= 0) {
                r.emptySince = -1;
                r.warnedEmpty = false;
            }
            if (!r.fighting || !r.controller.isFighting()) {
                if (!containsActivation(r, p.getLocation()) || r.world.getGameTime() < r.readyAt) {
                    event.setCancelled(true);
                    return;
                }
                r.fighting = true;
                r.controller.awaken(players(r));
                hold(r);
                drawArenaBorder(r);
                for (Player pl : players(r)) {
                    plugin.message(pl, "§6✦ " + r.controller.thaiName() + " ตื่นขึ้นแล้ว! หากทุกคนออกนอกลานเกิน "
                        + (emptyResetTicks() / 20) + " วินาที บอสจะรีเซ็ตกลับสู่สถานะรอ");
                }
            }
            r.controller.handleIncomingDamage(event, p);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        TrackedShot tracked = shots.remove(event.getEntity().getUniqueId());
        warded.remove(event.getEntity().getUniqueId());
        if (tracked != null && event.getHitEntity() == null && hitShot(tracked, event.getEntity().getLocation())) return;
        if (!(event.getEntity().getShooter() instanceof Player p) || !canAttack(p)) return;
        for (Run r : runs.values()) {
            if (r.controller == null || r.world != p.getWorld() || !fromRing(r, p, event.getEntity())) continue;
            if (p.getGameMode() == GameMode.CREATIVE) {
                r.engagedCreative.add(p.getUniqueId());
            }
            if (event.getHitEntity() != null && r.controller.tryHitDestructibleNode(p, event.getHitEntity(), 64.0)) {
                event.getEntity().remove();
                return;
            }
            // Also check if projectile hit near any destructible node
            if (r.controller.tryHitDestructibleNode(p, null, 64.0)) {
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player p) || !canAttack(p)) return;
        Location from = p.getLocation();
        for (Run r : runs.values()) {
            if (r.controller == null || r.world != p.getWorld()
                    || Math.hypot(from.getX() - r.center.getX(), from.getZ() - r.center.getZ()) > r.arena.radius() + 64.0) continue;
            // Stamp where it was loosed: stepping in and out of the ring after the release changes nothing
            boolean inside = insideRing(r, from);
            projectile.getPersistentDataContainer().set(ringShotKey, PersistentDataType.BYTE, (byte) (inside ? 1 : 0));
            if (!(projectile instanceof AbstractArrow arrow) || arrow instanceof Trident) return;
            if (!inside) {
                if (r.fighting) warded.put(arrow.getUniqueId(), new WardedShot(arrow, r));
            } else if (arrow.getPierceLevel() == 0) {
                shots.put(arrow.getUniqueId(), new TrackedShot(arrow, arrow.getLocation()));
            }
            return;
        }
    }

    /** Dissolve arrows from outside the ring where they cross it; their hits are refused either way. */
    private void tickWarded() {
        for (Iterator<WardedShot> it = warded.values().iterator(); it.hasNext();) {
            WardedShot shot = it.next();
            AbstractArrow arrow = shot.arrow();
            Run r = shot.run();
            if (!arrow.isValid() || arrow.isInBlock() || arrow.getWorld() != r.world || r.controller == null
                    || !r.fighting || arrow.getTicksLived() > 200) {
                it.remove();
                continue;
            }
            Location now = arrow.getLocation();
            Location next = now.clone().add(arrow.getVelocity());
            Location at = insideRing(r, now) ? now : insideRing(r, next) ? next : null;
            if (at == null) continue;
            it.remove();
            arrow.remove();
            var dust = new Particle.DustOptions(ringColor(r), 1.6f);
            r.world.spawnParticle(Particle.DUST, at, 14, 0.35, 0.35, 0.35, 0, dust, true);
            r.world.spawnParticle(Particle.END_ROD, at, 6, 0.2, 0.2, 0.2, 0.04);
            r.world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_HIT, 1.0f, 1.6f);
            if (arrow.getShooter() instanceof Player shooter) wardNotice(shooter);
        }
    }

    private void tickShots() {
        for (Iterator<TrackedShot> it = shots.values().iterator(); it.hasNext();) {
            TrackedShot shot = it.next();
            AbstractArrow arrow = shot.arrow();
            if (!arrow.isValid() || arrow.isDead() || arrow.isInBlock()
                    || !(arrow.getShooter() instanceof Player player) || !canAttack(player)) {
                it.remove();
                continue;
            }
            Location now = arrow.getLocation();
            if (hitShot(shot, now)) { it.remove(); continue; }
            // Predict the coming tick as well: entity collision is processed before our
            // next sample, so an arrow can otherwise pass right through the model.
            TrackedShot next = new TrackedShot(arrow, now);
            if (hitShot(next, now.clone().add(arrow.getVelocity()))) { it.remove(); continue; }
            shots.put(arrow.getUniqueId(), next);
        }
    }

    private boolean hitShot(TrackedShot shot, Location to) {
        AbstractArrow arrow = shot.arrow();
        if (!(arrow.getShooter() instanceof Player player) || !canAttack(player)
                || shot.previous().getWorld() != to.getWorld()) return false;
        Vector segment = to.toVector().subtract(shot.previous().toVector());
        double length = segment.length();
        if (length < 1.0e-5) return false;
        Vector direction = segment.clone().multiply(1.0 / length);
        Run target = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (Run r : runs.values()) {
            if (r.controller == null || r.world != to.getWorld() || !contains(r, player.getLocation())) continue;
            double distance = r.controller.projectileRayDistance(shot.previous(), direction, length);
            if (distance < nearest) { nearest = distance; target = r; }
        }
        if (target == null || !Double.isFinite(nearest)) return false;
        // A different living entity along the path must receive its normal hit first.
        var obstruction = nearest <= 1.0e-5 ? null : to.getWorld().rayTraceEntities(shot.previous(), direction, nearest, 0.1,
            e -> e != player && e instanceof LivingEntity living && living.isCollidable() && !owns(e));
        if (obstruction != null) return false;
        Mob receiver = target.controller.carrier();
        if (receiver == null || !receiver.isValid()) return false;
        double damage = Math.ceil(Math.max(0.0, arrow.getDamage()) * arrow.getVelocity().length());
        if (arrow.isCritical()) damage += java.util.concurrent.ThreadLocalRandom.current().nextInt((int) (damage / 2.0) + 2);
        DamageSource source = DamageSource.builder(DamageType.ARROW).withDirectEntity(arrow).withCausingEntity(player).build();
        double before = target.controller.health();
        receiver.damage(damage, source);
        if (target.controller.health() >= before) return false;
        arrow.remove();
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void used(PlayerInteractEvent e) {
        if (e.getAction() != org.bukkit.event.block.Action.LEFT_CLICK_AIR) {
            interactions.put(e.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void usedEntity(PlayerInteractEntityEvent e) {
        interactions.put(e.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void swing(PlayerAnimationEvent e) {
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING || !canAttack(e.getPlayer())) return;
        Player p = e.getPlayer();
        int serverTick = Bukkit.getCurrentTick();
        long swung = tick;
        double reach = BossHitbox.reach(p);

        for (Run r : runs.values()) {
            if (r.controller == null || r.world != p.getWorld() || !insideRing(r, p.getLocation())) continue;
            if (p.getGameMode() == GameMode.CREATIVE) {
                r.engagedCreative.add(p.getUniqueId());
            }
            // 1. Check destructible Soul Fire Orbs / Prism Crystals first
            if (r.controller.tryHitDestructibleNode(p, null, reach + 1.4)) return;
            // 2. Check 3D animated boss body / airborne cylinder
            double dist = r.controller.meleeRayDistance(p, reach, swung);
            if (!Double.isFinite(dist)) continue;
            Mob receiver = r.controller.carrier();
            if (receiver == null || !receiver.isValid()) continue;
            Location eye = p.getEyeLocation();
            Run selected = r;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (closed || !p.isOnline() || selected.controller == null || !insideRing(selected, p.getLocation())
                        || interactions.getOrDefault(p.getUniqueId(), Integer.MIN_VALUE) >= serverTick) return;
                BossHitbox.attack(p, receiver, eye, dist);
            });
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent e) {
        if (owns(e.getEntity())) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSlimeSplit(SlimeSplitEvent e) {
        if (owns(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent e) {
        if (owns(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        for (Run r : runs.values()) {
            if (r.controller != null && r.controller.owns(e.getEntity())) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent e) {
        for (Run r : runs.values()) {
            if (r.controller != null && r.controller.owns(e.getEntity())) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> discover(e.getChunk()), 20);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent e) {
        for (Run r : new ArrayList<>(runs.values())) {
            if (!r.fighting && r.world == e.getWorld()
                    && (r.center.getBlockX() >> 4) == e.getChunk().getX()
                    && (r.center.getBlockZ() >> 4) == e.getChunk().getZ()) {
                runs.remove(id(r.world, r.site));
                if (r.controller != null) r.controller.close();
                release(r);
            }
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        shots.clear();
        warded.clear();
        wardNotices.clear();
        sunFire.clear();
        sunWither.clear();
        task.cancel();
        for (Run r : runs.values()) {
            if (r.controller != null) r.controller.close();
            release(r);
        }
        runs.clear();
        interactions.clear();
        save();
    }
}
