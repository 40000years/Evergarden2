package com.example.forge;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Elite special moves while an elite is hostile to a player: hook (yank to the elite), shockwave (throw back)
 * and vortex (drag in). Each elite rolls its moves once from what its type can use and keeps them on the mob.
 * Every move is telegraphed (particles, sound, action bar) before it lands, and every move that reaches a
 * mounted or gliding player throws them off first, so a boat, pet, flying staff or elytra is no escape.
 * Moves only reposition players; the damage still comes from the elite itself.
 */
public final class EliteAbilities implements Listener {
    private static final int DECIDE_EVERY = 5;
    private static final double TARGET_RANGE = 24;
    /** A hook never lifts a player more than this, nor drops them from higher than this. */
    private static final double HOOK_MAX_RISE = 4, HOOK_MAX_DROP = 12;
    /** A hook stops the player this far short of the elite. */
    private static final double HOOK_STOP = 2.0;
    private static final double VORTEX_MIN = 3.5, VORTEX_CORE = 1.8;
    private static final double SHOCK_LIFT = 0.42;
    /** Ticks between dismounting a player and moving them (the dismount teleport would cancel the motion). */
    private static final int AFTER_DISMOUNT = 2;

    private final ForgePlugin plugin;
    private final NamespacedKey movesKey;
    private final Map<UUID, Brain> brains = new HashMap<>();
    private final List<Cast> casts = new ArrayList<>();
    private final List<Impulse> impulses = new ArrayList<>();
    private final Map<UUID, Long> grace = new HashMap<>();
    private final Map<UUID, Long> remountLock = new HashMap<>();
    private final Map<UUID, Long> lockNotice = new HashMap<>();
    private BukkitTask ticker;
    private long clock;

    private static final class Brain {
        UUID target;
        long aggroSince, nextMove;
        final EnumMap<EliteMove, Long> ready = new EnumMap<>(EliteMove.class);
    }

    private static final class Cast {
        final EliteMove move;
        final UUID elite, target;
        final long fireAt, endAt;
        final Color color;
        boolean fired;

        Cast(EliteMove move, UUID elite, UUID target, long fireAt, long endAt, Color color) {
            this.move = move;
            this.elite = elite;
            this.target = target;
            this.fireAt = fireAt;
            this.endAt = endAt;
            this.color = color;
        }
    }

    private record Impulse(UUID player, Vector velocity, long at) {}

    public EliteAbilities(ForgePlugin plugin) {
        this.plugin = plugin;
        movesKey = new NamespacedKey(plugin, "elite_moves");
    }

    public void start() {
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void stop() {
        if (ticker != null) ticker.cancel();
        brains.clear();
        casts.clear();
        impulses.clear();
        grace.clear();
        remountLock.clear();
        lockNotice.clear();
    }

    // ---- moves of an elite -----------------------------------------------------------------------

    /** The elite's moves, rolled once from its type's pool and its grade's count, then kept on the mob. */
    public List<EliteMove> moves(LivingEntity elite) {
        Rarity rarity = plugin.elites().rarity(elite);
        ForgeSettings.EliteMoveGrade grade = rarity == null ? null : plugin.settings().eliteMoveGrades.get(rarity);
        List<EliteMove> pool = EliteMove.pool(elite.getType());
        int count = grade == null ? 0 : Math.min(grade.moves(), pool.size());
        List<EliteMove> stored = new ArrayList<>();
        String raw = elite.getPersistentDataContainer().get(movesKey, PersistentDataType.STRING);
        if (raw != null) for (String part : raw.split(",")) {
            EliteMove move = EliteMove.parse(part);
            if (move != null && pool.contains(move) && !stored.contains(move)) stored.add(move);
        }
        if (raw != null && stored.size() == count) return stored;
        List<EliteMove> rolled = new ArrayList<>(pool);
        Collections.shuffle(rolled, ThreadLocalRandom.current());
        rolled = new ArrayList<>(rolled.subList(0, count));
        Collections.sort(rolled);
        elite.getPersistentDataContainer().set(movesKey, PersistentDataType.STRING,
            rolled.stream().map(Enum::name).collect(Collectors.joining(",")));
        return rolled;
    }

    /** Starts a move now, ignoring cooldowns and the elite's own roll (admin test and probes). */
    public boolean force(Mob elite, Player target, EliteMove move) {
        Rarity rarity = plugin.elites().rarity(elite);
        if (rarity == null || casting(elite.getUniqueId()) || !eligible(target, elite, TARGET_RANGE)) return false;
        ForgeSettings.EliteMoveGrade grade = plugin.settings().eliteMoveGrades.getOrDefault(rarity, new ForgeSettings.EliteMoveGrade(1, 1));
        begin(elite, target, move, rarity, grade, brains.computeIfAbsent(elite.getUniqueId(), key -> new Brain()), plugin.settings());
        return true;
    }

    public boolean casting(UUID elite) {
        for (Cast cast : casts) if (cast.elite.equals(elite)) return true;
        return false;
    }

    // ---- ticking -------------------------------------------------------------------------------

    private void tick() {
        clock++;
        ForgeSettings settings = plugin.settings();
        if (!impulses.isEmpty()) applyImpulses();
        if (!casts.isEmpty()) runCasts(settings);
        if (clock % DECIDE_EVERY == 0 && settings.eliteAbilitiesEnabled) decide(settings);
        if (clock % 200 == 0) prune();
    }

    private void decide(ForgeSettings settings) {
        for (UUID id : plugin.elites().trackedIds()) {
            if (casting(id) || !(Bukkit.getEntity(id) instanceof Mob mob) || !mob.isValid() || mob.isDead() || !mob.hasAI()) continue;
            if (settings.eliteAbilityExempt.contains(mob.getType())) continue;
            Rarity rarity = plugin.elites().rarity(mob);
            ForgeSettings.EliteMoveGrade grade = rarity == null ? null : settings.eliteMoveGrades.get(rarity);
            if (grade == null || grade.moves() <= 0) continue;
            Brain brain = brains.computeIfAbsent(id, key -> new Brain());
            Player target = mob.getTarget() instanceof Player player && eligible(player, mob, TARGET_RANGE) ? player : null;
            if (target == null) {
                brain.target = null;
                continue;
            }
            if (!target.getUniqueId().equals(brain.target)) {
                brain.target = target.getUniqueId();
                brain.aggroSince = clock;
            }
            if (clock - brain.aggroSince < settings.eliteFirstDelay || clock < brain.nextMove) continue;
            EliteMove move = choose(mob, target, moves(mob), brain, settings);
            if (move != null) begin(mob, target, move, rarity, grade, brain, settings);
        }
    }

    private EliteMove choose(Mob mob, Player target, List<EliteMove> moves, Brain brain, ForgeSettings settings) {
        double distance = mob.getLocation().distance(target.getLocation());
        boolean mounted = mounted(target);
        boolean sight = mob.hasLineOfSight(target);
        boolean anchored = anchored(mob);
        List<EliteMove> usable = new ArrayList<>();
        for (EliteMove move : moves) {
            ForgeSettings.EliteMoveConfig config = settings.eliteMoves.get(move);
            if (config == null || !config.enabled() || clock < brain.ready.getOrDefault(move, 0L)) continue;
            boolean ready = switch (move) {
                case HOOK -> sight && anchored && !graced(target) && hookable(mob, target, config, distance, mounted);
                case SHOCKWAVE -> !shockTargets(mob, config.radius()).isEmpty();
                case VORTEX -> sight && anchored && distance >= VORTEX_MIN && distance <= config.radius();
            };
            if (ready) usable.add(move);
        }
        if (usable.isEmpty()) return null;
        // A player hiding on a boat, pet or staff gets pulled off first.
        if (mounted && usable.contains(EliteMove.HOOK)) return EliteMove.HOOK;
        return usable.get(ThreadLocalRandom.current().nextInt(usable.size()));
    }

    private static boolean hookable(Mob mob, Player target, ForgeSettings.EliteMoveConfig config, double distance, boolean mounted) {
        double rise = mob.getLocation().getY() - target.getLocation().getY();
        if (rise > HOOK_MAX_RISE || rise < -HOOK_MAX_DROP) return false;
        return distance <= config.maxRange() && (distance >= config.minRange() || mounted && distance >= 2.5);
    }

    private void begin(Mob mob, Player target, EliteMove move, Rarity rarity, ForgeSettings.EliteMoveGrade grade, Brain brain,
                       ForgeSettings settings) {
        ForgeSettings.EliteMoveConfig config = settings.eliteMoves.get(move);
        long fireAt = clock + config.windup();
        long endAt = move == EliteMove.VORTEX ? fireAt + config.duration() : fireAt;
        casts.add(new Cast(move, mob.getUniqueId(), target.getUniqueId(), fireAt, endAt, Color.fromRGB(rarity.color.value())));
        brain.nextMove = endAt + settings.eliteGlobalCooldown;
        brain.ready.put(move, fireAt + Math.round(config.cooldown() * grade.cooldown()));
        World world = mob.getWorld();
        Location at = mob.getLocation();
        switch (move) {
            case HOOK -> {
                world.playSound(at, Sound.ENTITY_VEX_CHARGE, 1.2f, 0.6f);
                target.sendActionBar(Component.text("⚠ Elite ล็อกเป้าคุณ — หลบให้พ้นสายตาก่อนโดนกระชาก!", NamedTextColor.RED));
            }
            case SHOCKWAVE -> {
                world.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.2f, 0.7f);
                for (Player player : world.getPlayers()) if (eligible(player, mob, config.radius() + 3))
                    player.sendActionBar(Component.text("⚠ แรงผลักกำลังมา — ถอยออกห่าง!", NamedTextColor.RED));
            }
            case VORTEX -> {
                world.playSound(at, Sound.BLOCK_BEACON_DEACTIVATE, 1.4f, 0.5f);
                for (Player player : world.getPlayers()) if (eligible(player, mob, config.radius() + 2))
                    player.sendActionBar(Component.text("⚠ หลุมดูด — วิ่งออกจากวงก่อนถูกดูด!", NamedTextColor.RED));
            }
        }
        plugin.getLogger().fine("[Forge-Elite] " + mob.getType().getKey().getKey() + " " + rarity + " starts " + move.key + " on " + target.getName());
    }

    private void runCasts(ForgeSettings settings) {
        Iterator<Cast> iterator = casts.iterator();
        while (iterator.hasNext()) {
            Cast cast = iterator.next();
            if (!(Bukkit.getEntity(cast.elite) instanceof Mob mob) || !mob.isValid() || mob.isDead()) {
                iterator.remove();
                continue;
            }
            ForgeSettings.EliteMoveConfig config = settings.eliteMoves.get(cast.move);
            Player target = Bukkit.getPlayer(cast.target);
            if (clock < cast.fireAt) {
                if ((cast.fireAt - clock) % 2 == 0) telegraph(cast, mob, target, config);
                continue;
            }
            if (!cast.fired) {
                cast.fired = true;
                fire(cast, mob, target, config, settings);
            }
            if (cast.move == EliteMove.VORTEX && clock <= cast.endAt) vortexPulse(cast, mob, config, settings);
            if (clock >= cast.endAt) iterator.remove();
        }
    }

    // ---- telegraphs ----------------------------------------------------------------------------

    private void telegraph(Cast cast, Mob mob, Player target, ForgeSettings.EliteMoveConfig config) {
        World world = mob.getWorld();
        Location center = mob.getLocation();
        Particle.DustOptions dust = new Particle.DustOptions(cast.color, 1.3f);
        double progress = config.windup() <= 0 ? 1 : 1 - (double) (cast.fireAt - clock) / config.windup();
        switch (cast.move) {
            case HOOK -> {
                if (target == null || !target.getWorld().equals(world)) return;
                Location from = mob.getEyeLocation();
                Vector line = target.getLocation().add(0, 1, 0).toVector().subtract(from.toVector());
                for (int i = 1; i <= 8; i++) {
                    Location point = from.clone().add(line.clone().multiply(i / 9.0));
                    world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dust);
                }
                world.spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 3, 0.25, 0.4, 0.25, 0.02);
            }
            case SHOCKWAVE -> ring(world, center.clone().add(0, 0.15, 0), config.radius() * (0.35 + 0.65 * progress), 16, Particle.DUST, dust);
            case VORTEX -> ring(world, center.clone().add(0, 0.2, 0), config.radius() * (1 - 0.6 * progress), 18, Particle.REVERSE_PORTAL, null);
        }
    }

    private static <T> void ring(World world, Location center, double radius, int points, Particle particle, T data) {
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            Location point = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            world.spawnParticle(particle, point, 1, 0, 0, 0, 0, data);
        }
    }

    // ---- moves landing -------------------------------------------------------------------------

    private void fire(Cast cast, Mob mob, Player target, ForgeSettings.EliteMoveConfig config, ForgeSettings settings) {
        switch (cast.move) {
            case HOOK -> fireHook(mob, target, config, settings);
            case SHOCKWAVE -> fireShockwave(mob, config, settings);
            case VORTEX -> {
                if (!anchored(mob)) return;
                mob.getWorld().playSound(mob.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.6f, 1.8f);
            }
        }
    }

    private void fireHook(Mob mob, Player target, ForgeSettings.EliteMoveConfig config, ForgeSettings settings) {
        if (target == null || !eligible(target, mob, config.maxRange() + 4) || !mob.hasLineOfSight(target) || !anchored(mob)) {
            if (target != null && target.isOnline()) target.sendActionBar(Component.text("หลบการกระชากได้!", NamedTextColor.GREEN));
            return;
        }
        World world = mob.getWorld();
        Location from = mob.getEyeLocation();
        Vector line = target.getLocation().add(0, 1, 0).toVector().subtract(from.toVector());
        for (int i = 1; i <= 10; i++) world.spawnParticle(Particle.CRIT, from.clone().add(line.clone().multiply(i / 11.0)), 1, 0, 0, 0, 0);
        world.playSound(mob.getLocation(), Sound.ENTITY_BREEZE_JUMP, 1.2f, 0.7f);
        world.playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f);
        grace.put(target.getUniqueId(), clock + settings.elitePlayerGrace);
        int delay = dismount(target, settings) ? AFTER_DISMOUNT : 0;
        push(target, hookVelocity(target.getLocation(), mob.getLocation(), config.maxSpeed()), delay);
    }

    private void fireShockwave(Mob mob, ForgeSettings.EliteMoveConfig config, ForgeSettings settings) {
        World world = mob.getWorld();
        Location center = mob.getLocation();
        world.spawnParticle(Particle.EXPLOSION, center.clone().add(0, 0.6, 0), 1, 0, 0, 0, 0);
        ring(world, center.clone().add(0, 0.3, 0), config.radius(), 20, Particle.CLOUD, null);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.5f);
        for (Player player : shockTargets(mob, config.radius())) {
            Vector away = player.getLocation().toVector().subtract(center.toVector()).setY(0);
            double distance = away.length();
            if (distance < 0.2) away = center.getDirection().setY(0);
            if (away.lengthSquared() < 1e-6) away = new Vector(1, 0, 0);
            away.normalize();
            double speed = config.strength() * (0.65 + 0.35 * (1 - Math.min(1, distance / config.radius())));
            grace.put(player.getUniqueId(), clock + settings.elitePlayerGrace);
            int delay = dismount(player, settings) ? AFTER_DISMOUNT : 0;
            Vector velocity = safePush(player.getLocation(), away, speed);
            // No safe ground behind the player (void, lava): they keep their footing and are only jolted.
            push(player, velocity != null ? velocity : new Vector(0, 0.25, 0), delay);
        }
    }

    private void vortexPulse(Cast cast, Mob mob, ForgeSettings.EliteMoveConfig config, ForgeSettings settings) {
        World world = mob.getWorld();
        Location center = mob.getLocation();
        long age = clock - cast.fireAt;
        double spin = age * 0.45;
        Particle.DustOptions dust = new Particle.DustOptions(cast.color, 1.2f);
        for (int arm = 0; arm < 3; arm++) {
            double angle = spin + arm * Math.PI * 2 / 3;
            double radius = config.radius() * (1 - (age % 10) / 10.0);
            world.spawnParticle(Particle.REVERSE_PORTAL, center.clone().add(Math.cos(angle) * radius, 0.4, Math.sin(angle) * radius), 2, 0.1, 0.1, 0.1, 0);
            world.spawnParticle(Particle.DUST, center.clone().add(Math.cos(angle + 0.6) * radius * 0.6, 0.8, Math.sin(angle + 0.6) * radius * 0.6), 1, 0, 0, 0, 0, dust);
        }
        if (age % 2 != 0 || !anchored(mob)) return;
        for (Player player : world.getPlayers()) {
            if (!eligible(player, mob, config.radius() + 0.5) || !mob.hasLineOfSight(player)) continue;
            if (dismount(player, settings)) continue;
            Vector toward = center.toVector().subtract(player.getLocation().toVector()).setY(0);
            double distance = toward.length();
            if (distance < VORTEX_CORE) continue;
            double speed = config.strength() * (0.6 + 0.4 * (1 - Math.min(1, distance / config.radius())));
            // Keep the resting fall speed, so the drag never floats a player or cancels a fall.
            player.setVelocity(toward.normalize().multiply(speed).setY(-0.0784));
        }
    }

    // ---- motion helpers --------------------------------------------------------------------------

    /**
     * Launch speed that lands a player about {@link #HOOK_STOP} blocks short of the elite, from vanilla air
     * physics (vertical: v = (v - 0.08) * 0.98 per tick; horizontal: x 0.91 per tick).
     */
    static Vector hookVelocity(Location from, Location to, double maxSpeed) {
        Vector flat = to.toVector().subtract(from.toVector());
        double rise = flat.getY();
        flat.setY(0);
        double distance = flat.length();
        if (distance < 1e-3) return new Vector(0, 0.42, 0);
        double travel = Math.max(0, distance - HOOK_STOP);
        double lift = Math.clamp(0.42 + 0.02 * travel + Math.max(0, rise) * 0.12, 0.42, 0.9);
        int ticks = flightTicks(lift, rise);
        double reach = (1 - Math.pow(0.91, ticks)) / 0.09;
        double speed = Math.min(maxSpeed, travel / reach);
        return flat.normalize().multiply(speed).setY(lift);
    }

    /** Ticks in the air for a launch speed until the player is back down at the given height difference. */
    static int flightTicks(double lift, double rise) {
        double y = 0, velocity = lift;
        int ticks = 0;
        while (ticks < 80) {
            y += velocity;
            velocity = (velocity - 0.08) * 0.98;
            ticks++;
            if (velocity < 0 && y <= rise) break;
        }
        return ticks;
    }

    /** The strongest push (full, 60% or 30%) whose landing spot has ground; null when none does. */
    static Vector safePush(Location from, Vector direction, double speed) {
        double reach = (1 - Math.pow(0.91, flightTicks(SHOCK_LIFT, 0))) / 0.09;
        for (double scale : new double[]{1, 0.6, 0.3}) {
            double horizontal = speed * scale;
            Location landing = from.clone().add(direction.clone().multiply(horizontal * reach));
            if (safeGround(landing)) return direction.clone().multiply(horizontal).setY(SHOCK_LIFT);
        }
        return null;
    }

    /** Solid ground or water within 8 blocks under a spot, without lava, fire or the void first. */
    static boolean safeGround(Location at) {
        World world = at.getWorld();
        int x = at.getBlockX(), z = at.getBlockZ();
        if (world == null || !world.isChunkLoaded(x >> 4, z >> 4)) return false;
        int top = at.getBlockY() + 2;
        for (int y = top; y >= Math.max(world.getMinHeight(), top - 10); y--) {
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();
            if (type == Material.LAVA || type == Material.FIRE || type == Material.SOUL_FIRE) return false;
            if (type == Material.WATER || type.isSolid()) return true;
        }
        return false;
    }

    private void push(Player player, Vector velocity, int delay) {
        if (delay <= 0) {
            player.setVelocity(velocity);
            return;
        }
        impulses.add(new Impulse(player.getUniqueId(), velocity, clock + delay));
    }

    private void applyImpulses() {
        Iterator<Impulse> iterator = impulses.iterator();
        while (iterator.hasNext()) {
            Impulse impulse = iterator.next();
            if (clock < impulse.at()) continue;
            iterator.remove();
            Player player = Bukkit.getPlayer(impulse.player());
            if (player != null && player.isValid() && !player.isDead() && !player.isInsideVehicle()) player.setVelocity(impulse.velocity());
        }
    }

    /** Throws a player off a vehicle (boat, pet, staff) or out of an elytra glide; true when they were. */
    private boolean dismount(Player player, ForgeSettings settings) {
        if (!mounted(player)) return false;
        if (player.isInsideVehicle()) player.leaveVehicle();
        if (player.isGliding()) player.setGliding(false);
        if (settings.eliteRemountLock > 0) remountLock.put(player.getUniqueId(), clock + settings.eliteRemountLock);
        player.sendActionBar(Component.text("⚠ ถูกกระชากลงจากพาหนะ!", NamedTextColor.RED));
        return true;
    }

    private static boolean mounted(Player player) {
        return player.isInsideVehicle() || player.isGliding();
    }

    private static boolean anchored(Mob mob) {
        return mob.isOnGround() || mob.isInWater();
    }

    private boolean graced(Player player) {
        return grace.getOrDefault(player.getUniqueId(), 0L) > clock;
    }

    private List<Player> shockTargets(Mob mob, double radius) {
        List<Player> targets = new ArrayList<>();
        for (Player player : mob.getWorld().getPlayers()) {
            if (eligible(player, mob, radius + 0.5) && !graced(player) && mob.hasLineOfSight(player)) targets.add(player);
        }
        return targets;
    }

    private static boolean eligible(Player player, LivingEntity elite, double range) {
        if (player == null || !player.isValid() || player.isDead()) return false;
        GameMode mode = player.getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) return false;
        return player.getWorld().equals(elite.getWorld()) && player.getLocation().distanceSquared(elite.getLocation()) <= range * range;
    }

    // ---- remount lock and cleanup ----------------------------------------------------------------

    /** A player an elite has just thrown off cannot climb straight back into the boat or onto the pet. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Long until = remountLock.get(player.getUniqueId());
        if (until == null || until <= clock) return;
        event.setCancelled(true);
        if (lockNotice.getOrDefault(player.getUniqueId(), 0L) <= clock) {
            lockNotice.put(player.getUniqueId(), clock + 20);
            player.sendActionBar(Component.text("⚠ ยังขึ้นพาหนะไม่ได้ชั่วครู่", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        forget(event.getEntity().getUniqueId());
    }

    private void forget(UUID player) {
        grace.remove(player);
        remountLock.remove(player);
        lockNotice.remove(player);
        impulses.removeIf(impulse -> impulse.player().equals(player));
    }

    private void prune() {
        Set<UUID> live = Set.copyOf(plugin.elites().trackedIds());
        brains.keySet().removeIf(id -> !live.contains(id));
        grace.values().removeIf(until -> until <= clock);
        remountLock.values().removeIf(until -> until <= clock);
        lockNotice.values().removeIf(until -> until <= clock);
    }
}
