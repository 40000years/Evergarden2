package com.example.forge;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static com.example.forge.AffixService.line;

/**
 * What worn charms do. Gestures: double-tap sneak (Shadow Step), hold sneak on the ground (Time Stop), hold
 * sneak in the air (Sky Glider), jump again in the air (Starwalker). Everything drawn is particles, so Java and
 * Bedrock see the same thing. Damage the charms deal themselves is flagged and never triggers charms again.
 */
public final class CharmEffects implements Listener {
    private static final long DOUBLE_TAP = 350, HOLD = 1500, KILL_CHAIN = 4000, VEIL_DELAY = 5000;
    private static final long LEGION_TIME = 15_000, LEGION_COOLDOWN = 40_000, OVERCLOCK_COOLDOWN = 20_000;
    private static final int LEGION_SOULS = 6, OVERCLOCK_KILLS = 5;
    /** Cosmetics are sampled separately; movement, targeting and hit checks keep their original cadence. */
    private static final int PASSIVE_VISUAL_TICKS = 6;

    private final ForgePlugin plugin;
    private final CharmService charms;
    private final NamespacedKey hourglassKey, timeStopKey, batKey, timelessKey, timelessItemsKey, timelessWorldKey, hexKey, overclockKey, legacyBlood;
    private final Map<UUID, State> states = new HashMap<>();
    /** Mobs whose AI a charm switched off, and when it comes back. */
    private final Map<UUID, Long> frozen = new HashMap<>();
    /** Stasis bubbles: mob → {owner, stored damage, end}. */
    private final Map<UUID, Object[]> stasis = new HashMap<>();
    private final Map<UUID, Long> contactHit = new HashMap<>();
    private BukkitTask ticker;
    private boolean internal, veinBreaking;
    private long tick;

    private Object magicPlugin;
    private Method manaService, castCaster, castSpell, spellRemaining;

    private static final class State {
        final ArrayDeque<Object[]> history = new ArrayDeque<>(); // {Location, health, time} for Hourglass
        final ArrayDeque<Object[]> safe = new ArrayDeque<>();    // {Location, time} on solid ground for Chrono Anchor
        long lastSneak, sneakStart, shadowReady, crimsonReady, legionUntil, legionReady, batUntil, lastCombat;
        long familiarAt, cascadeReady, blackHoleReady, meteorReady, anchorReady, stasisReady, overclockUntil, lastKill, eyeAt, cooldownNote;
        boolean holdFired, managedFlight, batFlight, veiled;
        int jumpsLeft, souls, chain;
        final long[] orbitReady = new long[3];
        final List<Spirit> spirits = new ArrayList<>();
        final List<Location> treasure = new ArrayList<>();
    }

    private static final class Spirit {
        Location at;
        UUID target;
        long nextHit;
    }

    public CharmEffects(ForgePlugin plugin, CharmService charms) {
        this.plugin = plugin;
        this.charms = charms;
        hourglassKey = new NamespacedKey(plugin, "charm_hourglass_ready");
        timeStopKey = new NamespacedKey(plugin, "charm_time_stop_ready");
        batKey = new NamespacedKey(plugin, "charm_bat_form_ready");
        timelessKey = new NamespacedKey(plugin, "charm_timeless_ready");
        timelessItemsKey = new NamespacedKey(plugin, "charm_timeless_items");
        timelessWorldKey = new NamespacedKey(plugin, "charm_timeless_world");
        hexKey = new NamespacedKey(plugin, "charm_hex");
        overclockKey = new NamespacedKey(CharmService.NAMESPACE, "charm_overclock");
        legacyBlood = new NamespacedKey(CharmService.NAMESPACE, "charm_blood_pact");
    }

    private State state(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), id -> new State());
    }

    private boolean has(Player player, Charm charm) {
        return charms.has(player, charm);
    }

    private double value(Player player, Charm charm) {
        Rarity grade = charms.grade(player, charm);
        return grade == null ? 0 : charm.value(grade);
    }

    private int intValue(Player player, Charm charm) {
        Rarity grade = charms.grade(player, charm);
        return grade == null ? 0 : charm.intValue(grade);
    }

    // ---- lifecycle ---------------------------------------------------------------------------

    public void start() {
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
        Bukkit.getScheduler().runTask(plugin, this::hookMagic);
    }

    public void stop() {
        if (ticker != null) ticker.cancel();
        for (UUID id : new ArrayList<>(frozen.keySet())) unfreeze(id);
        for (Player player : Bukkit.getOnlinePlayers()) release(player);
        states.clear();
    }

    /** Undo everything a charm changed on the player (flight, invisibility, attack speed). */
    private void release(Player player) {
        State state = states.get(player.getUniqueId());
        if (state != null) {
            if (state.managedFlight || state.batFlight) {
                player.setFlying(false);
                if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) player.setAllowFlight(false);
            }
            if (state.veiled) unveil(player, state);
        }
        AttributeInstance speed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (speed != null && speed.getModifier(overclockKey) != null) speed.removeModifier(overclockKey);
    }

    /** Called when the worn set changes: leftovers of a charm that came off are removed. */
    public void wornChanged(Player player) {
        AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
        if (health != null && health.getModifier(legacyBlood) != null) health.removeModifier(legacyBlood);
        State state = states.get(player.getUniqueId());
        if (state == null) return;
        if (state.managedFlight && !has(player, Charm.STARWALKER)) {
            state.managedFlight = false;
            if (!state.batFlight && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)) player.setAllowFlight(false);
        }
        if (state.veiled && !has(player, Charm.NIGHTFALL_VEIL)) unveil(player, state);
        if (!has(player, Charm.SOUL_LEGION)) state.spirits.clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        release(event.getPlayer());
        states.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> restoreTimeless(event.getPlayer()), 40);
    }

    // ---- helpers -----------------------------------------------------------------------------

    private boolean boss(LivingEntity entity) {
        if (entity instanceof Boss || entity instanceof Warden || entity instanceof ElderGuardian) return true;
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        return max != null && max.getValue() > plugin.getConfig().getDouble("charms.boss-health", 300);
    }

    private static boolean hostile(Entity entity) {
        return entity instanceof LivingEntity living && entity instanceof Enemy && living.isValid() && !living.isDead()
            && !(entity instanceof Tameable tame && tame.isTamed());
    }

    private static Player attacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    private static boolean melee(EntityDamageByEntityEvent event) {
        return event.getDamager() instanceof Player && (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
            || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK);
    }

    private static double maxHealth(LivingEntity entity) {
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        return max == null ? 20 : max.getValue();
    }

    private static boolean survival(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    private void charmDamage(LivingEntity target, double amount, Player source) {
        if (amount <= 0 || !target.isValid() || target.isDead()) return;
        internal = true;
        try {
            // A charm hit is its own hit: the target's hurt frames from the last swing must not swallow it.
            target.setNoDamageTicks(0);
            target.damage(amount, source);
        } finally {
            internal = false;
        }
    }

    private List<LivingEntity> hostilesNear(Location center, double radius) {
        List<LivingEntity> found = new ArrayList<>();
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (hostile(entity) && entity.getLocation().distanceSquared(center) <= radius * radius) found.add((LivingEntity) entity);
        }
        return found;
    }

    private static Particle.DustOptions dust(int rgb, float size) {
        return new Particle.DustOptions(Color.fromRGB(rgb), size);
    }

    private boolean passiveVisualTick() {
        return tick % PASSIVE_VISUAL_TICKS == 0 && plugin.getConfig().getBoolean("charms.visuals.passive-particles", true);
    }

    private static void ring(World world, Location center, double radius, Particle particle, int points, Object data) {
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            Location point = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            if (data == null) world.spawnParticle(particle, point, 1, 0, 0, 0, 0);
            else world.spawnParticle(particle, point, 1, 0, 0, 0, 0, data);
        }
    }

    private static void beam(World world, Location from, Location to, Particle.DustOptions color, double step) {
        Vector line = to.toVector().subtract(from.toVector());
        double length = line.length();
        if (length < 0.01) return;
        line.multiply(1 / length);
        step = Math.max(0.65, step);
        for (double d = 0; d <= length; d += step) world.spawnParticle(Particle.DUST, from.clone().add(line.clone().multiply(d)), 1, 0, 0, 0, 0, color);
    }

    private long readyAt(Player player, NamespacedKey key) {
        return player.getPersistentDataContainer().getOrDefault(key, PersistentDataType.LONG, 0L);
    }

    private boolean ready(Player player, NamespacedKey key) {
        return System.currentTimeMillis() >= readyAt(player, key);
    }

    private void cooldown(Player player, NamespacedKey key, double seconds) {
        player.getPersistentDataContainer().set(key, PersistentDataType.LONG, System.currentTimeMillis() + (long) (seconds * 1000));
    }

    /** "Charm · พักอยู่ 42 วินาที", at most every 1.5 seconds. */
    private void resting(Player player, Charm charm, long until) {
        State state = state(player);
        long now = System.currentTimeMillis();
        if (now < state.cooldownNote) return;
        state.cooldownNote = now + 1500;
        long left = Math.max(1, (until - now + 999) / 1000);
        String text = left >= 120 ? (left / 60) + " นาที" : left + " วินาที";
        player.sendActionBar(line(charm.title + " · พักอยู่ " + text, NamedTextColor.GRAY));
    }

    private void freeze(LivingEntity entity, int ticks) {
        if (!(entity instanceof Mob mob)) return;
        long until = System.currentTimeMillis() + ticks * 50L;
        if (frozen.containsKey(mob.getUniqueId())) {
            frozen.merge(mob.getUniqueId(), until, Math::max);
            return;
        }
        if (!mob.hasAI()) return; // someone else switched it off; leave it alone
        mob.setAI(false);
        frozen.put(mob.getUniqueId(), until);
    }

    private void unfreeze(UUID id) {
        frozen.remove(id);
        if (Bukkit.getEntity(id) instanceof Mob mob && mob.isValid()) mob.setAI(true);
    }

    // ---- gestures ----------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (charms.worn(player).isEmpty()) return;
        State state = state(player);
        long now = System.currentTimeMillis();
        if (!event.isSneaking()) {
            state.sneakStart = 0;
            return;
        }
        state.sneakStart = now;
        state.holdFired = false;
        if (now - state.lastSneak <= DOUBLE_TAP && has(player, Charm.SHADOW_STEP)) {
            state.lastSneak = 0;
            if (now < state.shadowReady) resting(player, Charm.SHADOW_STEP, state.shadowReady);
            else if (shadowStep(player)) state.shadowReady = now + (long) (value(player, Charm.SHADOW_STEP) * 1000);
            return;
        }
        state.lastSneak = now;
    }

    private boolean shadowStep(Player player) {
        if (player.isInsideVehicle() || player.isGliding()) return false;
        Location start = player.getLocation();
        Vector dir = start.getDirection().setY(0);
        if (dir.lengthSquared() < 1e-4) return false;
        dir.normalize();
        World world = player.getWorld();
        RayTraceResult hit = world.rayTraceBlocks(start.clone().add(0, 0.6, 0), dir, 8, FluidCollisionMode.NEVER, true);
        double reach = hit == null ? 8 : Math.max(0, hit.getHitPosition().distance(start.clone().add(0, 0.6, 0).toVector()) - 0.7);
        Location end = null;
        for (double d = reach; d >= 1; d -= 0.5) {
            Location candidate = start.clone().add(dir.clone().multiply(d));
            if (candidate.getBlock().isPassable() && candidate.clone().add(0, 1, 0).getBlock().isPassable()) {
                end = candidate;
                break;
            }
        }
        if (end == null) return false;
        Location from = start.clone().add(0, 1, 0), to = end.clone().add(0, 1, 0);
        Vector path = to.toVector().subtract(from.toVector());
        double length = path.length();
        for (LivingEntity mob : hostilesNear(start.clone().add(path.clone().multiply(0.5)), length / 2 + 2)) {
            Vector rel = mob.getLocation().add(0, mob.getHeight() / 2, 0).toVector().subtract(from.toVector());
            double t = Math.clamp(rel.dot(path) / (length * length), 0, 1);
            if (rel.subtract(path.clone().multiply(t)).length() <= 1.6) {
                charmDamage(mob, 6, player);
                world.spawnParticle(Particle.SWEEP_ATTACK, mob.getLocation().add(0, mob.getHeight() / 2, 0), 1, 0, 0, 0, 0);
            }
        }
        beam(world, from, to, dust(0x2A0F45, 0.8f), 0.65);
        world.spawnParticle(Particle.REVERSE_PORTAL, from, 8, 0.25, 0.3, 0.25, 0.02);
        player.teleport(end);
        player.setFallDistance(0);
        player.setNoDamageTicks(Math.max(player.getNoDamageTicks(), 10));
        world.spawnParticle(Particle.REVERSE_PORTAL, to, 8, 0.25, 0.3, 0.25, 0.02);
        world.playSound(end, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.5f);
        world.playSound(end, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 0.7f);
        return true;
    }

    /** Starwalker: managed flight is the jump detector; a flight toggle in the air becomes a jump. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        State state = states.get(player.getUniqueId());
        if (state == null || !state.managedFlight || state.batFlight || !event.isFlying() || !survival(player)) return;
        event.setCancelled(true);
        player.setFlying(false);
        if (state.jumpsLeft <= 0 || !has(player, Charm.STARWALKER)) {
            player.setAllowFlight(false);
            return;
        }
        state.jumpsLeft--;
        Vector look = player.getLocation().getDirection().setY(0);
        Vector velocity = (look.lengthSquared() > 1e-4 ? look.normalize().multiply(0.35) : new Vector()).setY(0.82);
        player.setVelocity(velocity);
        player.setFallDistance(0);
        Location feet = player.getLocation();
        ring(player.getWorld(), feet.clone().add(0, 0.1, 0), 0.7, Particle.DUST, 8, dust(0xBFE9FF, 0.7f));
        player.getWorld().playSound(feet, Sound.ENTITY_BREEZE_JUMP, 0.6f, 1.4f);
        if (state.jumpsLeft <= 0) player.setAllowFlight(false);
    }

    /** Crimson Wave: a blood wave on every swing at full health. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player player = event.getPlayer();
        if (!has(player, Charm.CRIMSON_WAVE) || player.getHealth() < maxHealth(player) - 0.5) return;
        String held = player.getInventory().getItemInMainHand().getType().name();
        if (!held.endsWith("_SWORD") && !held.endsWith("_AXE") && !held.equals("MACE") && !held.equals("TRIDENT") && !held.endsWith("_SPEAR")) return;
        State state = state(player);
        long now = System.currentTimeMillis();
        if (now < state.crimsonReady) return;
        // Swinging at a block (mining, chopping) is not an attack.
        if (player.getTargetEntity(4) == null && player.getTargetBlockExact(4) != null) return;
        state.crimsonReady = now + 600;
        crimsonWave(player, value(player, Charm.CRIMSON_WAVE));
    }

    private void crimsonWave(Player player, double damage) {
        Location origin = player.getEyeLocation().add(0, -0.35, 0);
        Vector dir = origin.getDirection().normalize();
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        if (side.lengthSquared() < 1e-4) side = new Vector(1, 0, 0);
        side.normalize();
        Set<UUID> struck = new HashSet<>();
        World world = player.getWorld();
        world.playSound(origin, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 0.6f);
        Vector across = side;
        for (int step = 1; step <= 7; step++) {
            int at = step;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                Location point = origin.clone().add(dir.clone().multiply(at));
                if (!point.getBlock().isPassable()) return;
                if (at % 2 == 1) for (double s = -1.1; s <= 1.1; s += 0.55) {
                    double bend = (1 - s * s) * 0.35;
                    world.spawnParticle(Particle.DUST, point.clone().add(across.clone().multiply(s)).add(dir.clone().multiply(bend)), 1, 0, 0, 0, 0,
                        dust(Math.abs(s) < 0.5 ? 0xFF3B55 : 0x9A0A24, 0.7f));
                }
                for (LivingEntity mob : hostilesNear(point, 1.6)) if (struck.add(mob.getUniqueId())) charmDamage(mob, damage, player);
            }, step);
        }
    }

    // ---- outgoing damage ---------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (internal || !(event.getEntity() instanceof LivingEntity victim)) return;
        Player player = attacker(event.getDamager());
        if (player == null || player == victim || charms.worn(player).isEmpty()) return;
        State state = state(player);
        long now = System.currentTimeMillis();
        state.lastCombat = now;
        if (state.veiled) {
            event.setDamage(event.getDamage() * (1 + value(player, Charm.NIGHTFALL_VEIL) / 100));
            unveil(player, state);
            victim.getWorld().spawnParticle(Particle.DUST, victim.getLocation().add(0, victim.getHeight() / 2, 0), 6, 0.2, 0.25, 0.2, 0, dust(0x3A0A4F, 0.8f));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f);
        }
        if (!hostile(victim)) return;
        double left = victim.getHealth() - event.getFinalDamage();
        // Only a hit the mob would have survived is an execution; an ordinary killing blow is not.
        if (has(player, Charm.REAPER_MARK) && !boss(victim) && left > 0 && left <= value(player, Charm.REAPER_MARK) / 100 * maxHealth(victim)) {
            event.setDamage(Math.max(event.getDamage(), victim.getHealth() * 4 + 40));
            reap(player, victim);
        }
        if (has(player, Charm.HEX_BRAND)) brand(victim, player, 0);
        if (has(player, Charm.BLACK_HOLE) && now >= state.blackHoleReady) {
            state.blackHoleReady = now + (long) (value(player, Charm.BLACK_HOLE) * 1000);
            blackHole(player, victim.getLocation());
        }
        if (!melee(event)) return;
        if (has(player, Charm.STASIS_TRAP) && now >= state.stasisReady && !stasis.containsKey(victim.getUniqueId())) {
            state.stasisReady = now + (long) (value(player, Charm.STASIS_TRAP) * 1000);
            trap(player, victim);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDealt(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        Player player = attacker(event.getDamager());
        if (player == null || player == victim) return;
        double dealt = Math.min(event.getFinalDamage(), victim.getHealth() + victim.getAbsorptionAmount());
        Object[] bubble = stasis.get(victim.getUniqueId());
        if (bubble != null && bubble[0].equals(player.getUniqueId())) bubble[1] = (double) bubble[1] + dealt;
        if (internal || !melee(event) || dealt <= 0 || !has(player, Charm.ECHO_STRIKE) || !hostile(victim)) return;
        double echo = dealt * value(player, Charm.ECHO_STRIKE) / 100;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !victim.isValid() || victim.isDead()) return;
            Location at = victim.getLocation().add(0, victim.getHeight() / 2, 0);
            victim.getWorld().spawnParticle(Particle.SWEEP_ATTACK, at, 1, 0, 0, 0, 0);
            victim.getWorld().spawnParticle(Particle.DUST, at, 4, 0.2, 0.25, 0.2, 0, dust(0x46D1C0, 0.7f));
            victim.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.7f);
            charmDamage(victim, echo, player);
        }, 12);
    }

    private void reap(Player player, LivingEntity victim) {
        Location at = victim.getLocation().add(0, victim.getHeight() / 2, 0);
        World world = victim.getWorld();
        world.spawnParticle(Particle.SOUL, at, 3, 0.2, 0.25, 0.2, 0.01);
        world.spawnParticle(Particle.DUST, at, 5, 0.2, 0.25, 0.2, 0, dust(0xEDE4D3, 0.7f));
        world.playSound(at, Sound.ENTITY_WITHER_SKELETON_HURT, 0.7f, 0.6f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            LivingEntity next = null;
            double best = 64;
            for (LivingEntity mob : hostilesNear(at, 8)) {
                if (mob == victim) continue;
                double d = mob.getLocation().distanceSquared(at);
                if (d < best) {
                    best = d;
                    next = mob;
                }
            }
            if (next == null) return;
            beam(world, at, next.getLocation().add(0, next.getHeight() / 2, 0), dust(0x7FF7EA, 1.0f), 0.3);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, next.getLocation().add(0, next.getHeight() / 2, 0), 3, 0.1, 0.2, 0.1, 0.01);
            charmDamage(next, 5, player);
        }, 4);
    }

    private void brand(LivingEntity victim, Player owner, int generation) {
        long until = System.currentTimeMillis() + 8000;
        victim.getPersistentDataContainer().set(hexKey, PersistentDataType.STRING, owner.getUniqueId() + ":" + generation + ":" + until);
        victim.getWorld().spawnParticle(Particle.DUST, victim.getLocation().add(0, victim.getHeight() + 0.3, 0), 2, 0.15, 0.05, 0.15, 0, dust(0x9A4DFF, 0.7f));
    }

    private void blackHole(Player player, Location center) {
        World world = center.getWorld();
        Location core = center.clone().add(0, 1, 0);
        world.playSound(core, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.5f);
        world.playSound(core, Sound.BLOCK_PORTAL_TRIGGER, 0.4f, 1.8f);
        for (int t = 0; t <= 60; t += 2) {
            int at = t;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                double spin = at * 0.35;
                if (at % 6 == 0) {
                for (int arm = 0; arm < 2; arm++) {
                    for (double r = 0.6; r < 4; r += 1.0) {
                        double angle = spin + arm * Math.PI + r * 0.9;
                        world.spawnParticle(Particle.DUST, core.clone().add(Math.cos(angle) * r, (r - 2) * 0.08, Math.sin(angle) * r), 1, 0, 0, 0, 0,
                            dust(r < 1.5 ? 0x0A0418 : 0x6A5CFF, 0.7f));
                    }
                }
                world.spawnParticle(Particle.REVERSE_PORTAL, core, 1, 0.1, 0.1, 0.1, 0.02);
                }
                if (!player.isOnline()) return;
                for (LivingEntity mob : hostilesNear(core, 6)) {
                    if (!boss(mob)) {
                        Vector pull = core.toVector().subtract(mob.getLocation().toVector());
                        if (pull.lengthSquared() > 0.6) mob.setVelocity(pull.normalize().multiply(0.45).setY(0.08));
                    }
                    if (at % 10 == 0 && mob.getLocation().distanceSquared(core) < 2.5 * 2.5) charmDamage(mob, 2.5, player);
                }
                if (at == 60) {
                    world.spawnParticle(Particle.EXPLOSION, core, 1, 0, 0, 0, 0);
                    world.playSound(core, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
                    for (LivingEntity mob : hostilesNear(core, 3.5)) charmDamage(mob, 5, player);
                }
            }, t);
        }
    }

    private void trap(Player player, LivingEntity victim) {
        long end = System.currentTimeMillis() + 3000;
        stasis.put(victim.getUniqueId(), new Object[]{player.getUniqueId(), 0.0, end});
        if (boss(victim)) victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2, false, false, true));
        else freeze(victim, 60);
        victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.9f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Object[] bubble = stasis.remove(victim.getUniqueId());
            if (bubble == null || !victim.isValid() || victim.isDead()) return;
            Location at = victim.getLocation().add(0, victim.getHeight() / 2, 0);
            victim.getWorld().spawnParticle(Particle.FIREWORK, at, 6, 0.25, 0.25, 0.25, 0.04);
            victim.getWorld().spawnParticle(Particle.DUST, at, 8, 0.4, 0.4, 0.4, 0, dust(0x46D1C0, 0.8f));
            victim.getWorld().playSound(at, Sound.BLOCK_GLASS_BREAK, 0.9f, 1.4f);
            double stored = (double) bubble[1] * 0.5;
            if (player.isOnline() && stored > 0) charmDamage(victim, stored, player);
        }, 60);
    }

    // ---- incoming damage ---------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || charms.worn(player).isEmpty()) return;
        State state = state(player);
        long now = System.currentTimeMillis();
        if (now < state.batUntil) {
            event.setCancelled(true);
            return;
        }
        if (internal) return;
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause != EntityDamageEvent.DamageCause.FALL && cause != EntityDamageEvent.DamageCause.VOID) state.lastCombat = now;
        if (state.veiled && event instanceof EntityDamageByEntityEvent) unveil(player, state);
        // Chrono Anchor: falls into the void or lava, or a long fall, step back to safe ground.
        boolean longFall = cause == EntityDamageEvent.DamageCause.FALL && player.getFallDistance() > 10 && !has(player, Charm.METEOR_FALL);
        if (has(player, Charm.CHRONO_ANCHOR) && (cause == EntityDamageEvent.DamageCause.VOID || cause == EntityDamageEvent.DamageCause.LAVA || longFall)) {
            if (now >= state.anchorReady && anchor(player, state)) {
                event.setCancelled(true);
                state.anchorReady = now + (long) (value(player, Charm.CHRONO_ANCHOR) * 1000);
                return;
            }
        }
        if (cause == EntityDamageEvent.DamageCause.FALL && has(player, Charm.METEOR_FALL)) {
            double fall = Math.max(player.getFallDistance(), event.getDamage() + 3);
            event.setCancelled(true);
            if (fall >= 4 && now >= state.meteorReady) {
                state.meteorReady = now + 4000;
                meteor(player, fall);
            }
            return;
        }
        Entity damager = event instanceof EntityDamageByEntityEvent byEntity ? byEntity.getDamager() : null;
        if (damager instanceof Projectile projectile && has(player, Charm.ORBIT) && attacker(projectile) != player) {
            for (int i = 0; i < 3; i++) {
                if (now < state.orbitReady[i]) continue;
                state.orbitReady[i] = now + 10_000;
                event.setCancelled(true);
                Location at = projectile.getLocation();
                projectile.remove();
                player.getWorld().spawnParticle(Particle.END_ROD, at, 4, 0.1, 0.1, 0.1, 0.03);
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.9f, 1.7f);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent event) {
        if (internal || !(event.getEntity() instanceof Player player) || charms.worn(player).isEmpty() || !survival(player)) return;
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.KILL || cause == EntityDamageEvent.DamageCause.SUICIDE) return;
        if (event.getFinalDamage() < player.getHealth() + player.getAbsorptionAmount()) return;
        if (holdsTotem(player) || !has(player, Charm.HOURGLASS)) return;
        if (!ready(player, hourglassKey)) return;
        if (rewind(player)) {
            event.setCancelled(true);
            cooldown(player, hourglassKey, value(player, Charm.HOURGLASS));
        }
    }

    /** Bat Form after the hit landed: below 30% the player bursts into bats. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurtAfter(EntityDamageEvent event) {
        if (internal || !(event.getEntity() instanceof Player player) || !has(player, Charm.BAT_FORM) || !survival(player)) return;
        double after = player.getHealth() - Math.max(0, event.getFinalDamage() - player.getAbsorptionAmount());
        if (after <= 0 || after >= maxHealth(player) * 0.3) return;
        if (!ready(player, batKey)) return;
        cooldown(player, batKey, value(player, Charm.BAT_FORM));
        Bukkit.getScheduler().runTask(plugin, () -> batForm(player));
    }

    private static boolean holdsTotem(Player player) {
        return player.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING
            || player.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
    }

    private boolean rewind(Player player) {
        State state = state(player);
        long target = System.currentTimeMillis() - 5000;
        Object[] chosen = null;
        for (Object[] snapshot : state.history) if ((long) snapshot[2] <= target) chosen = snapshot;
        if (chosen == null) chosen = state.history.peekFirst();
        if (chosen == null) return false;
        Location from = player.getLocation();
        Location to = (Location) chosen[0];
        player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, from.clone().add(0, 0.25, 0), 12, 0.35, 0.15, 0.35, 0.02);
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.setHealth(Math.min(maxHealth(player), Math.max(6, (double) chosen[1])));
        player.setNoDamageTicks(30);
        if (to.getWorld() == from.getWorld() && to.distanceSquared(from) > 0.25) player.teleport(to);
        Location at = player.getLocation();
        ring(at.getWorld(), at.clone().add(0, 0.1, 0), 1.2, Particle.DUST, 8, dust(0xF2C35B, 0.75f));
        at.getWorld().spawnParticle(Particle.END_ROD, at.clone().add(0, 0.25, 0), 6, 0.3, 0.15, 0.3, 0.01);
        player.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.9f, 1.4f);
        player.playSound(at, Sound.BLOCK_BELL_USE, 0.8f, 0.7f);
        player.showTitle(Title.title(line("⌛ Hourglass", Charm.Temple.TIME.color), line("ย้อนเวลากลับ 5 วินาที", NamedTextColor.GRAY)));
        state.history.clear();
        return true;
    }

    private boolean anchor(Player player, State state) {
        long target = System.currentTimeMillis() - 3000;
        Location chosen = null;
        for (Object[] snapshot : state.safe) if ((long) snapshot[1] <= target) chosen = (Location) snapshot[0];
        if (chosen == null && !state.safe.isEmpty()) chosen = (Location) state.safe.peekFirst()[0];
        if (chosen == null || chosen.getWorld() != player.getWorld()) return false;
        player.getWorld().spawnParticle(Particle.REVERSE_PORTAL, player.getLocation().add(0, 0.25, 0), 10, 0.35, 0.15, 0.35, 0.02);
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.setVelocity(new Vector());
        player.teleport(chosen);
        player.setNoDamageTicks(20);
        ring(player.getWorld(), chosen.clone().add(0, 0.1, 0), 0.9, Particle.DUST, 6, dust(0xF2C35B, 0.75f));
        player.playSound(chosen, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, 1.2f);
        player.sendActionBar(line("⚓ Chrono Anchor · ย้อนกลับจุดปลอดภัย", Charm.Temple.TIME.color));
        state.safe.clear();
        return true;
    }

    private void meteor(Player player, double fall) {
        double height = Math.min(20, fall);
        double damage = height * value(player, Charm.METEOR_FALL);
        double radius = Math.min(6, 2.5 + height / 5);
        Location at = player.getLocation();
        World world = player.getWorld();
        world.spawnParticle(Particle.EXPLOSION, at.clone().add(0, 0.3, 0), 1, 0, 0, 0, 0);
        ring(world, at.clone().add(0, 0.15, 0), radius * 0.5, Particle.DUST, 12, dust(0xFFD76A, 0.8f));
        ring(world, at.clone().add(0, 0.15, 0), radius, Particle.DUST, 20, dust(0x7FD3FF, 0.8f));
        world.spawnParticle(Particle.END_ROD, at.clone().add(0, 0.3, 0), 6, radius / 3, 0.1, radius / 3, 0.04);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.2f);
        world.playSound(at, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 1f);
        for (LivingEntity mob : hostilesNear(at, radius)) {
            charmDamage(mob, damage, player);
            if (!boss(mob)) mob.setVelocity(mob.getLocation().toVector().subtract(at.toVector()).setY(0).normalize().multiply(0.6).setY(0.45));
        }
    }

    private void batForm(Player player) {
        if (!player.isOnline() || player.isDead()) return;
        State state = state(player);
        long now = System.currentTimeMillis();
        state.batUntil = now + 3000;
        if (!player.getAllowFlight()) {
            player.setAllowFlight(true);
            state.batFlight = true;
        }
        player.setFlying(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 60, 0, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 2, false, false, true));
        for (Entity near : player.getNearbyEntities(16, 8, 16)) if (near instanceof Mob mob && mob.getTarget() == player) mob.setTarget(null);
        World world = player.getWorld();
        Location at = player.getLocation().add(0, 1, 0);
        List<Bat> bats = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            boolean silent = i % 2 == 1;
            bats.add(world.spawn(at.clone().add(ThreadLocalRandom.current().nextDouble(-1, 1), ThreadLocalRandom.current().nextDouble(0, 1),
                ThreadLocalRandom.current().nextDouble(-1, 1)), Bat.class, bat -> {
                bat.setPersistent(false);
                bat.setInvulnerable(true);
                bat.setSilent(silent);
                bat.setAwake(true);
            }));
        }
        world.spawnParticle(Particle.SMOKE, at.clone().add(0, -0.75, 0), 4, 0.35, 0.15, 0.35, 0.01);
        world.spawnParticle(Particle.DUST, at.clone().add(0, -0.75, 0), 6, 0.35, 0.15, 0.35, 0, dust(0x2A0A2F, 0.75f));
        world.playSound(at, Sound.ENTITY_BAT_TAKEOFF, 1f, 0.8f);
        player.showTitle(Title.title(line("🦇 Bat Form", Charm.Temple.DARK.color), line("บินหนี 3 วินาที · ไม่มีอะไรทำร้ายได้", NamedTextColor.GRAY)));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            bats.forEach(bat -> { if (bat.isValid()) bat.remove(); });
            if (!player.isOnline()) return;
            if (state.batFlight) {
                state.batFlight = false;
                player.setFlying(false);
                if (!state.managedFlight && survival(player)) player.setAllowFlight(false);
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 120, 0, false, false, true));
            player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 0.25, 0), 4, 0.3, 0.15, 0.3, 0.01);
        }, 60);
    }

    private void unveil(Player player, State state) {
        state.veiled = false;
        PotionEffect current = player.getPotionEffect(PotionEffectType.INVISIBILITY);
        if (current != null && current.isInfinite()) player.removePotionEffect(PotionEffectType.INVISIBILITY);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player player)) return;
        State state = states.get(player.getUniqueId());
        if (state != null && (System.currentTimeMillis() < state.batUntil || state.veiled)) event.setCancelled(true);
    }

    // ---- kills -------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onKill(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead instanceof Player) return;
        frozen.remove(dead.getUniqueId());
        stasis.remove(dead.getUniqueId());
        String hex = dead.getPersistentDataContainer().get(hexKey, PersistentDataType.STRING);
        if (hex != null) hexBurst(dead, hex);
        Player player = dead.getKiller();
        if (player == null || charms.worn(player).isEmpty() || !(dead instanceof Enemy)) return;
        State state = state(player);
        long now = System.currentTimeMillis();
        if (has(player, Charm.SOUL_LEGION) && now >= state.legionReady && state.legionUntil < now) {
            state.souls++;
            dead.getWorld().spawnParticle(Particle.SOUL, dead.getLocation().add(0, 0.8, 0), 2, 0.2, 0.3, 0.2, 0.02);
            if (state.souls >= LEGION_SOULS) {
                state.souls = 0;
                summonLegion(player, state, now);
            } else {
                player.sendActionBar(line("☾ Soul Legion · วิญญาณ " + state.souls + "/" + LEGION_SOULS, Charm.Temple.DARK.color));
            }
        }
        if (has(player, Charm.OVERCLOCK) && now >= state.overclockUntil) {
            state.chain = now - state.lastKill <= KILL_CHAIN ? state.chain + 1 : 1;
            state.lastKill = now;
            if (state.chain >= OVERCLOCK_KILLS && now >= readyAt(player, overclockReadyKey())) {
                state.chain = 0;
                overclock(player, state, now);
            } else if (state.chain > 1) {
                player.sendActionBar(line("» Overclock " + state.chain + "/" + OVERCLOCK_KILLS, Charm.Temple.TIME.color));
            }
        }
    }

    private NamespacedKey overclockReadyKey() {
        return new NamespacedKey(plugin, "charm_overclock_ready");
    }

    private void hexBurst(LivingEntity dead, String hex) {
        String[] parts = hex.split(":");
        if (parts.length != 3) return;
        long until;
        int generation;
        Player owner;
        try {
            owner = Bukkit.getPlayer(UUID.fromString(parts[0]));
            generation = Integer.parseInt(parts[1]);
            until = Long.parseLong(parts[2]);
        } catch (IllegalArgumentException error) {
            return;
        }
        if (owner == null || System.currentTimeMillis() > until || !has(owner, Charm.HEX_BRAND)) return;
        Location at = dead.getLocation().add(0, dead.getHeight() / 2, 0);
        double damage = value(owner, Charm.HEX_BRAND);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!owner.isOnline()) return;
            World world = at.getWorld();
            world.spawnParticle(Particle.DUST, at, 8, 1, 0.4, 1, 0, dust(0x9A4DFF, 0.8f));
            world.spawnParticle(Particle.WITCH, at, 4, 0.8, 0.3, 0.8, 0.03);
            ring(world, at.clone().add(0, -0.4, 0), 3, Particle.DUST, 12, dust(0x5A1A9A, 0.7f));
            world.playSound(at, Sound.ENTITY_EVOKER_CAST_SPELL, 0.8f, 1.3f);
            for (LivingEntity mob : hostilesNear(at, 3)) {
                if (mob == dead) continue;
                if (generation < 4) brand(mob, owner, generation + 1);
                charmDamage(mob, damage, owner);
            }
        }, 2);
    }

    private void summonLegion(Player player, State state, long now) {
        state.legionUntil = now + LEGION_TIME;
        state.legionReady = state.legionUntil + LEGION_COOLDOWN;
        state.spirits.clear();
        int count = Math.max(1, intValue(player, Charm.SOUL_LEGION));
        for (int i = 0; i < count; i++) {
            Spirit spirit = new Spirit();
            spirit.at = player.getLocation().add(Math.cos(i * 2.1) * 1.2, 1.6, Math.sin(i * 2.1) * 1.2);
            state.spirits.add(spirit);
        }
        player.getWorld().spawnParticle(Particle.SOUL, player.getLocation().add(0, 1.2, 0), 8, 0.6, 0.6, 0.6, 0.05);
        player.playSound(player.getLocation(), Sound.ENTITY_VEX_CHARGE, 1f, 0.7f);
        player.playSound(player.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.8f);
        player.sendActionBar(line("☾ Soul Legion · วิญญาณ " + count + " ดวงตื่นขึ้น!", Charm.Temple.DARK.color));
    }

    private void overclock(Player player, State state, long now) {
        double seconds = value(player, Charm.OVERCLOCK);
        state.overclockUntil = now + (long) (seconds * 1000);
        cooldown(player, overclockReadyKey(), seconds + OVERCLOCK_COOLDOWN / 1000.0);
        int ticks = (int) (seconds * 20);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 1, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, ticks, 1, false, false, true));
        AttributeInstance speed = player.getAttribute(Attribute.ATTACK_SPEED);
        if (speed != null && speed.getModifier(overclockKey) == null)
            speed.addModifier(new AttributeModifier(overclockKey, 0.6, AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.ANY));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 2f);
        player.showTitle(Title.title(line("» OVERCLOCK «", Charm.Temple.TIME.color), line("เร่งเวลา " + Math.round(seconds) + " วินาที", NamedTextColor.GRAY)));
    }

    // ---- Timeless ----------------------------------------------------------------------------

    private static final PersistentDataType<?, List<byte[]>> STORED = PersistentDataType.LIST.listTypeFrom(PersistentDataType.BYTE_ARRAY);

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        State state = states.get(player.getUniqueId());
        if (state != null) {
            state.history.clear();
            state.safe.clear();
            state.chain = 0;
            state.spirits.clear();
        }
        if (event.getKeepInventory() || !has(player, Charm.TIMELESS) || !ready(player, timelessKey)) return;
        List<ItemStack> kept = new ArrayList<>();
        List<ItemStack> slots = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) slots.add(player.getInventory().getItem(slot));
        slots.add(player.getInventory().getItemInOffHand());
        for (ItemStack item : slots) {
            if (item == null || item.getType().isAir()) continue;
            // Only what really drops: items another plugin already keeps are not in the drop list.
            for (Iterator<ItemStack> drops = event.getDrops().iterator(); drops.hasNext(); ) {
                ItemStack drop = drops.next();
                if (drop.equals(item)) {
                    drops.remove();
                    kept.add(drop.clone());
                    break;
                }
            }
        }
        boolean xp = player.getLevel() > 0 || player.getExp() > 0;
        if (kept.isEmpty() && !xp) return;
        if (xp) {
            event.setKeepLevel(true);
            event.setDroppedExp(0);
        }
        cooldown(player, timelessKey, value(player, Charm.TIMELESS) * 60);
        if (!kept.isEmpty()) {
            List<byte[]> previous = player.getPersistentDataContainer().getOrDefault(timelessItemsKey, STORED, List.of());
            List<byte[]> all = new ArrayList<>(previous);
            kept.forEach(item -> all.add(item.serializeAsBytes()));
            player.getPersistentDataContainer().set(timelessItemsKey, STORED, all);
            player.getPersistentDataContainer().set(timelessWorldKey, PersistentDataType.STRING, player.getWorld().getName());
        }
        plugin.getLogger().info("[Forge-Charm] timeless player=" + player.getName() + " kept=" + kept.size() + " xp=" + xp);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> restoreTimeless(event.getPlayer()), 20);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        // After per-world inventory plugins have swapped the inventory in.
        Bukkit.getScheduler().runTaskLater(plugin, () -> restoreTimeless(event.getPlayer()), 20);
    }

    private void restoreTimeless(Player player) {
        if (!player.isOnline() || player.isDead()) return;
        var data = player.getPersistentDataContainer();
        List<byte[]> stored = data.get(timelessItemsKey, STORED);
        if (stored == null || stored.isEmpty()) return;
        String world = data.get(timelessWorldKey, PersistentDataType.STRING);
        if (world != null && !world.equals(player.getWorld().getName())) {
            player.sendActionBar(line("⌛ Timeless · ของจะคืนเมื่อกลับไปโลก " + world, Charm.Temple.TIME.color));
            return;
        }
        data.remove(timelessItemsKey);
        data.remove(timelessWorldKey);
        int count = 0;
        for (byte[] bytes : stored) {
            try {
                ItemStack item = ItemStack.deserializeBytes(bytes);
                player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                count++;
            } catch (RuntimeException error) {
                plugin.getLogger().warning("Timeless item for " + player.getName() + " unreadable: " + error);
            }
        }
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.8f, 1.6f);
        player.sendMessage(line("⌛ Timeless · ได้ของคืน " + count + " ชิ้น และ XP ไม่หาย", Charm.Temple.TIME.color));
    }

    // ---- hooks used by other forge systems ---------------------------------------------------

    public double eliteSpawnMultiplier(Location location) {
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= 48 * 48 && has(player, Charm.CURSED_COIN)) return 2.0;
        }
        return 1.0;
    }

    public boolean cursedCoinBonus(Player killer) {
        return has(killer, Charm.CURSED_COIN) && ThreadLocalRandom.current().nextDouble() * 100 < value(killer, Charm.CURSED_COIN);
    }

    public boolean lostHours(Player player) {
        boolean extra = has(player, Charm.LOST_HOURS) && ThreadLocalRandom.current().nextDouble() * 100 < value(player, Charm.LOST_HOURS);
        if (extra) {
            player.playSound(player.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.7f, 1.4f);
            player.sendActionBar(line("⌛ Lost Hours · หีบนี้มีของเพิ่มอีกรอบ", Charm.Temple.TIME.color));
        }
        return extra;
    }

    /** Lucky Star: a chance that a star falls next to the player with a gem or materials. */
    public void luckyStar(Player player, Location near) {
        if (!has(player, Charm.LUCKY_STAR) || ThreadLocalRandom.current().nextDouble() * 100 >= value(player, Charm.LUCKY_STAR)) return;
        Random random = ThreadLocalRandom.current();
        Rarity rarity = plugin.affixes().rollRarity(random, plugin.settings().weights());
        ItemStack reward = random.nextDouble() < 0.6 ? plugin.gems().item(plugin.gems().roll(rarity, random), 1) : plugin.affixes().material(rarity, 2);
        Location ground = near.clone().add(random.nextDouble(-1.5, 1.5), 0.3, random.nextDouble(-1.5, 1.5));
        World world = ground.getWorld();
        world.playSound(ground, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 1.4f);
        for (int step = 0; step <= 10; step++) {
            int at = step;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location point = ground.clone().add(1.5 - at * 0.15, 10 - at, 0);
                world.spawnParticle(Particle.DUST, point, 1, 0.03, 0.03, 0.03, 0, dust(0xFFE27A, 0.8f));
                if (at < 10) return;
                world.spawnParticle(Particle.FIREWORK, ground, 6, 0.2, 0.15, 0.2, 0.05);
                world.playSound(ground, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
                world.dropItemNaturally(ground, reward);
                if (player.isOnline()) player.sendActionBar(line("☆ Lucky Star · ดาวนำโชคตกลงมา!", Charm.Temple.ASTRAL.color));
            }, step * 2L);
        }
    }

    /** Vein of Ages: the rest of a natural ore vein breaks with the first block. */
    public void veinMine(Player player, Block origin, java.util.function.Predicate<Block> natural) {
        if (veinBreaking || !has(player, Charm.VEIN_OF_AGES) || player.isSneaking()) return;
        String kind = family(origin.getType());
        if (kind == null) return;
        int limit = Math.max(1, intValue(player, Charm.VEIN_OF_AGES)) - 1;
        List<Block> vein = new ArrayList<>();
        Set<Block> seen = new HashSet<>(List.of(origin));
        ArrayDeque<Block> open = new ArrayDeque<>(List.of(origin));
        while (!open.isEmpty() && vein.size() < limit) {
            Block block = open.poll();
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                Block next = block.getRelative(dx, dy, dz);
                if (vein.size() >= limit || !seen.add(next) || next.getLocation().distanceSquared(origin.getLocation()) > 64) continue;
                if (kind.equals(family(next.getType())) && natural.test(next)) {
                    vein.add(next);
                    open.add(next);
                }
            }
        }
        if (vein.isEmpty()) return;
        ItemStack tool = player.getInventory().getItemInMainHand().clone();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            int broken = 0;
            veinBreaking = true;
            try {
                for (Block block : vein) {
                    // Stop when the pickaxe broke or the player switched items.
                    if (player.getInventory().getItemInMainHand().getType() != tool.getType()) break;
                    if (kind.equals(family(block.getType())) && player.breakBlock(block)) broken++;
                }
            } finally {
                veinBreaking = false;
            }
            if (broken > 0) {
                origin.getWorld().spawnParticle(Particle.DUST, origin.getLocation().add(0.5, 0.5, 0.5), 4, 0.5, 0.4, 0.5, 0, dust(0xF2C35B, 0.7f));
                origin.getWorld().playSound(origin.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 0.8f);
                player.sendActionBar(line("⌛ Vein of Ages · ขุดแร่ทั้งสาย +" + broken + " ก้อน", Charm.Temple.TIME.color));
            }
        });
    }

    private static String family(Material type) {
        String name = type.name();
        if (type == Material.ANCIENT_DEBRIS) return name;
        if (!name.endsWith("_ORE")) return null;
        return name.startsWith("DEEPSLATE_") ? name.substring("DEEPSLATE_".length()) : name;
    }

    // ---- ticking -----------------------------------------------------------------------------

    private void tick() {
        tick++;
        long now = System.currentTimeMillis();
        if (tick % 5 == 0 && !frozen.isEmpty()) {
            for (Map.Entry<UUID, Long> entry : new ArrayList<>(frozen.entrySet())) {
                Entity entity = Bukkit.getEntity(entry.getKey());
                if (entity == null || !entity.isValid() || now >= entry.getValue()) unfreeze(entry.getKey());
                else if (tick % 20 == 0 && !stasis.containsKey(entry.getKey()))
                    entity.getWorld().spawnParticle(Particle.DUST, entity.getLocation().add(0, entity.getHeight() + 0.2, 0),
                        1, 0.1, 0.05, 0.1, 0, dust(0xF2C35B, 0.65f));
            }
        }
        if (tick % 10 == 0 && !stasis.isEmpty()) {
            for (UUID id : stasis.keySet()) {
                if (!(Bukkit.getEntity(id) instanceof LivingEntity mob) || !mob.isValid()) continue;
                Location c = mob.getLocation().add(0, mob.getHeight() / 2, 0);
                double r = Math.max(0.8, mob.getWidth());
                for (int i = 0; i < 4; i++) {
                    double a = tick * 0.2 + i * Math.PI / 2, b = Math.sin(tick * 0.15 + i) * 0.4;
                    mob.getWorld().spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * r, b, Math.sin(a) * r), 1, 0, 0, 0, 0, dust(0x9FFFF0, 0.65f));
                }
            }
        }
        if (tick % 200 == 0) contactHit.values().removeIf(time -> now - time > 5000);
        for (Player player : Bukkit.getOnlinePlayers()) {
            Map<Charm, Rarity> worn = charms.worn(player);
            if (worn.isEmpty()) {
                if (states.containsKey(player.getUniqueId())) {
                    wornChanged(player);
                    states.remove(player.getUniqueId());
                }
                continue;
            }
            if (player.isDead()) continue;
            State state = state(player);
            if (worn.containsKey(Charm.STARWALKER)) starwalker(player, state);
            if (worn.containsKey(Charm.SKY_GLIDER)) glide(player);
            if (tick % 10 == 0 && worn.containsKey(Charm.HOURGLASS)) {
                state.history.addLast(new Object[]{player.getLocation(), player.getHealth(), now});
                while (state.history.size() > 14) state.history.removeFirst();
            }
            if (tick % 10 == 0 && worn.containsKey(Charm.CHRONO_ANCHOR) && player.isOnGround() && !player.isInLava() && !player.isInWater()
                && player.getLocation().getBlock().getRelative(BlockFace.DOWN).getType().isSolid()) {
                state.safe.addLast(new Object[]{player.getLocation(), now});
                while (state.safe.size() > 12) state.safe.removeFirst();
            }
            if (worn.containsKey(Charm.TIME_STOP)) timeStopHold(player, state, now);
            if (tick % 2 == 0 && worn.containsKey(Charm.ORBIT)) orbit(player, state, now);
            if (worn.containsKey(Charm.STAR_FAMILIAR)) familiar(player, state, now);
            if (tick % 2 == 0 && !state.spirits.isEmpty()) legion(player, state, now);
            if (tick % 20 == 0 && worn.containsKey(Charm.REAPER_MARK)) reaperMarks(player);
            if (tick % 10 == 0 && worn.containsKey(Charm.NIGHTFALL_VEIL) && !state.veiled && now - state.lastCombat >= VEIL_DELAY) {
                state.veiled = true;
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, true));
                player.sendActionBar(line("☾ Nightfall Veil · ล่องหนในเงามืด", Charm.Temple.DARK.color));
                player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 0.3, 0), 3, 0.25, 0.1, 0.25, 0.01);
            }
            if (tick % 4 == 0 && worn.containsKey(Charm.LODESTAR)) lodestar(player);
            if (worn.containsKey(Charm.EYE_OF_ABYSS)) eye(player, state, now);
            if (state.overclockUntil > 0) {
                if (now >= state.overclockUntil) {
                    state.overclockUntil = 0;
                    AttributeInstance speed = player.getAttribute(Attribute.ATTACK_SPEED);
                    if (speed != null && speed.getModifier(overclockKey) != null) speed.removeModifier(overclockKey);
                } else if (passiveVisualTick()) {
                    player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 0.15, 0), 1, 0.3, 0.05, 0.3, 0, dust(0xFFC53D, 0.6f));
                }
            }
            if (state.veiled && tick % 20 == 0 && plugin.getConfig().getBoolean("charms.visuals.passive-particles", true))
                player.spawnParticle(Particle.DUST, player.getLocation().add(0, 0.2, 0), 1, 0.2, 0.03, 0.2, 0, dust(0x3A0A4F, 0.6f));
        }
    }

    private void starwalker(Player player, State state) {
        if (!survival(player) || player.isInsideVehicle() || player.isGliding()) return;
        if (player.isOnGround()) {
            state.jumpsLeft = Math.max(1, intValue(player, Charm.STARWALKER));
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
                state.managedFlight = true;
            }
        }
    }

    private void glide(Player player) {
        if (!player.isSneaking() || player.isOnGround() || player.isFlying() || player.isGliding() || player.isInsideVehicle()
            || player.isInWater() || player.isInLava() || !survival(player)) return;
        Vector velocity = player.getVelocity();
        if (velocity.getY() > -0.08) return;
        Vector look = player.getLocation().getDirection().setY(0);
        if (look.lengthSquared() < 1e-4) return;
        double speed = value(player, Charm.SKY_GLIDER) / 20;
        Vector forward = look.normalize().multiply(speed);
        player.setVelocity(new Vector(forward.getX(), Math.max(velocity.getY(), -0.11), forward.getZ()));
        player.setFallDistance(0);
        if (passiveVisualTick()) {
            Location back = player.getLocation().add(look.clone().multiply(-0.6)).add(0, 0.6, 0);
            player.getWorld().spawnParticle(Particle.DUST, back, 1, 0.2, 0.03, 0.2, 0, dust(0xDFF4FF, 0.6f));
        }
    }

    private void timeStopHold(Player player, State state, long now) {
        if (state.sneakStart == 0 || state.holdFired || !player.isSneaking() || !player.isOnGround()) return;
        long held = now - state.sneakStart;
        if (held < 400 || tick % 4 != 0 && held < HOLD) return;
        List<LivingEntity> targets = hostilesNear(player.getLocation(), 12);
        if (targets.isEmpty()) return;
        long readyAt = readyAt(player, timeStopKey);
        if (now < readyAt) {
            if (held >= HOLD) {
                state.holdFired = true;
                resting(player, Charm.TIME_STOP, readyAt);
            }
            return;
        }
        if (held < HOLD) {
            int bars = (int) Math.min(5, held * 5 / HOLD);
            player.sendActionBar(line("⌛ Time Stop " + "▰".repeat(bars) + "▱".repeat(5 - bars), Charm.Temple.TIME.color));
            if (tick % 8 == 0) ring(player.getWorld(), player.getLocation().add(0, 0.1, 0), 0.6 + held / 1000.0,
                Particle.DUST, 6, dust(0xF2C35B, 0.65f));
            return;
        }
        state.holdFired = true;
        cooldown(player, timeStopKey, value(player, Charm.TIME_STOP));
        World world = player.getWorld();
        Location center = player.getLocation();
        for (LivingEntity mob : targets) {
            if (boss(mob)) mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 3, false, false, true));
            else freeze(mob, 80);
        }
        for (Entity entity : player.getNearbyEntities(12, 8, 12)) {
            if (entity instanceof Projectile projectile && !(projectile.getShooter() instanceof Player)) {
                world.spawnParticle(Particle.END_ROD, projectile.getLocation(), 1, 0.05, 0.05, 0.05, 0.01);
                projectile.remove();
            }
        }
        ring(world, center.clone().add(0, 0.1, 0), 3, Particle.DUST, 12, dust(0xF2C35B, 0.7f));
        ring(world, center.clone().add(0, 0.1, 0), 12, Particle.DUST, 24, dust(0xF2C35B, 0.7f));
        world.spawnParticle(Particle.END_ROD, center.clone().add(0, 0.25, 0), 6, 0.8, 0.1, 0.8, 0.02);
        world.playSound(center, Sound.BLOCK_BELL_USE, 1f, 0.5f);
        world.playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.6f);
        player.showTitle(Title.title(line("⌛ TIME STOP", Charm.Temple.TIME.color), line("หยุดเวลา " + targets.size() + " ตัว · 4 วินาที", NamedTextColor.GRAY)));
    }

    private Location orbitPoint(Player player, int index, long now) {
        double angle = (now % 2400) / 2400.0 * Math.PI * 2 + Math.PI * 2 * index / 3;
        return player.getLocation().add(Math.cos(angle) * 1.6, 1.0 + Math.sin(angle * 2) * 0.2, Math.sin(angle) * 1.6);
    }

    private void orbit(Player player, State state, long now) {
        double damage = value(player, Charm.ORBIT);
        for (int i = 0; i < 3; i++) {
            Location point = orbitPoint(player, i, now);
            boolean charged = now >= state.orbitReady[i];
            if (passiveVisualTick()) player.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0,
                dust(charged ? 0xFFE9A6 : 0x6A7A9A, charged ? 0.65f : 0.5f));
            if (tick % 4 != 0) continue;
            for (LivingEntity mob : hostilesNear(point, 1.1)) {
                if (now - contactHit.getOrDefault(mob.getUniqueId(), 0L) < 1000) continue;
                contactHit.put(mob.getUniqueId(), now);
                charmDamage(mob, damage, player);
                if (!boss(mob)) mob.setVelocity(mob.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0).normalize().multiply(0.45).setY(0.2));
                player.getWorld().spawnParticle(Particle.FIREWORK, point, 2, 0.05, 0.05, 0.05, 0.02);
            }
        }
    }

    private void familiar(Player player, State state, long now) {
        double angle = (now % 4000) / 4000.0 * Math.PI * 2;
        // Stay behind one shoulder, below eye level, instead of orbiting through the camera view.
        Location sprite = player.getLocation();
        double yaw = Math.toRadians(sprite.getYaw());
        sprite.add(Math.cos(yaw) * 1.1 + Math.sin(yaw) * 0.45, 1.05 + Math.sin(angle * 3) * 0.08,
            Math.sin(yaw) * 1.1 - Math.cos(yaw) * 0.45);
        World world = player.getWorld();
        if (passiveVisualTick()) world.spawnParticle(Particle.DUST, sprite, 1, 0, 0, 0, 0, dust(0xFFF1A8, 0.65f));
        if (now < state.familiarAt) return;
        LivingEntity target = null;
        double best = 100;
        for (LivingEntity mob : hostilesNear(player.getLocation(), 10)) {
            double d = mob.getLocation().distanceSquared(player.getLocation());
            if (d < best && player.hasLineOfSight(mob)) {
                best = d;
                target = mob;
            }
        }
        if (target == null) return;
        state.familiarAt = now + 1500;
        Location hit = target.getLocation().add(0, target.getHeight() * 0.6, 0);
        beam(world, sprite, hit, dust(0xBFE9FF, 0.6f), 0.65);
        world.spawnParticle(Particle.END_ROD, hit, 2, 0.1, 0.1, 0.1, 0.01);
        world.playSound(sprite, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 2f);
        charmDamage(target, value(player, Charm.STAR_FAMILIAR), player);
    }

    private void legion(Player player, State state, long now) {
        if (now >= state.legionUntil) {
            for (Spirit spirit : state.spirits) player.getWorld().spawnParticle(Particle.SOUL, spirit.at, 2, 0.1, 0.1, 0.1, 0.01);
            state.spirits.clear();
            return;
        }
        World world = player.getWorld();
        for (int i = 0; i < state.spirits.size(); i++) {
            Spirit spirit = state.spirits.get(i);
            if (spirit.at.getWorld() != world) spirit.at = player.getLocation().add(0, 1.6, 0);
            Entity current = spirit.target == null ? null : Bukkit.getEntity(spirit.target);
            if (!(current instanceof LivingEntity living) || !hostile(living) || living.getLocation().distanceSquared(player.getLocation()) > 16 * 16) {
                current = null;
                double best = 14 * 14;
                for (LivingEntity mob : hostilesNear(player.getLocation(), 14)) {
                    double d = mob.getLocation().distanceSquared(spirit.at);
                    if (d < best) {
                        best = d;
                        current = mob;
                    }
                }
                spirit.target = current == null ? null : current.getUniqueId();
            }
            Location goal = current == null
                ? player.getLocation().add(Math.cos(now / 500.0 + i * 2.1) * 1.3, 1.7, Math.sin(now / 500.0 + i * 2.1) * 1.3)
                : current.getLocation().add(0, ((LivingEntity) current).getHeight() * 0.6, 0);
            Vector move = goal.toVector().subtract(spirit.at.toVector());
            double length = move.length();
            if (length > 0.05) spirit.at.add(move.multiply(Math.min(1, 0.7 / length)));
            if (passiveVisualTick()) {
                Location visual = current == null ? spirit.at.clone().add(0, -0.65, 0) : spirit.at;
                world.spawnParticle(Particle.DUST, visual, 1, 0, 0, 0, 0, dust(0x7FF7EA, 0.65f));
            }
            if (current instanceof LivingEntity living && length < 1.4 && now >= spirit.nextHit) {
                spirit.nextHit = now + 800;
                world.spawnParticle(Particle.SOUL, spirit.at, 2, 0.1, 0.1, 0.1, 0.01);
                world.playSound(spirit.at, Sound.ENTITY_VEX_HURT, 0.4f, 1.6f);
                charmDamage(living, 4, player);
            }
        }
    }

    private void reaperMarks(Player player) {
        double share = value(player, Charm.REAPER_MARK) / 100;
        int visible = 0;
        for (LivingEntity mob : hostilesNear(player.getLocation(), 16)) {
            if (boss(mob) || mob.getHealth() > share * maxHealth(mob)) continue;
            if (visible++ >= 8) break;
            Location head = mob.getLocation().add(0, mob.getHeight() + 0.45, 0);
            player.spawnParticle(Particle.DUST, head, 1, 0.03, 0.03, 0.03, 0, dust(0xEDE4D3, 0.7f));
        }
    }

    private void lodestar(Player player) {
        double radius = value(player, Charm.LODESTAR);
        Location center = player.getLocation().add(0, 0.6, 0);
        int motes = 0;
        for (Entity entity : player.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof Item item) {
                if (!item.canPlayerPickup() || item.getPickupDelay() > 40) continue;
                UUID thrower = item.getThrower(), owner = item.getOwner();
                if (thrower != null && !thrower.equals(player.getUniqueId()) || owner != null && !owner.equals(player.getUniqueId())) continue;
            } else if (!(entity instanceof ExperienceOrb)) {
                continue;
            }
            Vector pull = center.toVector().subtract(entity.getLocation().toVector());
            double distance = pull.length();
            if (distance < 0.8 || distance > radius) continue;
            entity.setVelocity(pull.normalize().multiply(Math.min(0.9, 0.25 + distance * 0.06)));
            if (tick % 20 == 0 && motes++ < 4) player.getWorld().spawnParticle(Particle.DUST, entity.getLocation().add(0, 0.2, 0),
                1, 0, 0, 0, 0, dust(0xBFE9FF, 0.6f));
        }
    }

    private void eye(Player player, State state, long now) {
        if (now >= state.eyeAt) {
            state.eyeAt = now + 3000;
            state.treasure.clear();
            state.treasure.addAll(plugin.evergardenChests().unopenedNear(player.getLocation(), value(player, Charm.EYE_OF_ABYSS)));
            if (!state.treasure.isEmpty()) {
                Location nearest = state.treasure.stream().min(Comparator.comparingDouble(l -> l.distanceSquared(player.getLocation()))).orElseThrow();
                player.sendActionBar(line("◉ Eye of the Abyss · หีบสมบัติ " + state.treasure.size() + " ใบ · ใกล้สุด "
                    + Math.round(nearest.distance(player.getLocation())) + " บล็อก " + arrow(player, nearest), Charm.Temple.DARK.color));
            }
        }
        if (tick % 20 != 0) return;
        List<Location> markers = state.treasure.stream().filter(chest -> chest.getWorld() == player.getWorld())
            .sorted(Comparator.comparingDouble(chest -> chest.distanceSquared(player.getLocation()))).limit(3).toList();
        for (Location chest : markers) {
            for (double y = 0.8; y <= 4.1; y += 1.6) {
                player.spawnParticle(Particle.DUST, chest.clone().add(0.5, y, 0.5), 1, 0, 0, 0, 0,
                    dust(y < 2 ? 0xFFD76A : 0xB566FF, 0.7f));
            }
        }
    }

    private static String arrow(Player player, Location target) {
        Vector to = target.toVector().subtract(player.getLocation().toVector()).setY(0);
        Vector look = player.getLocation().getDirection().setY(0);
        if (to.lengthSquared() < 1e-4 || look.lengthSquared() < 1e-4) return "•";
        double angle = Math.toDegrees(Math.atan2(look.getX() * to.getZ() - look.getZ() * to.getX(), look.getX() * to.getX() + look.getZ() * to.getZ()));
        String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
        return arrows[(int) Math.floorMod(Math.round(angle / 45), 8)];
    }

    // ---- Advance Magic -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void hookMagic() {
        Plugin magic = Bukkit.getPluginManager().getPlugin("advance-magic");
        if (magic == null || !magic.isEnabled()) return;
        try {
            ClassLoader loader = magic.getClass().getClassLoader();
            manaService = magic.getClass().getMethod("mana");
            Class<?> service = manaService.getReturnType();
            Class<? extends Event> castEvent = (Class<? extends Event>) Class.forName("com.example.advancemagic.api.ElementalCastEvent", true, loader);
            Class<?> spell = Class.forName("com.example.advancemagic.elemental.ElementalSpell", true, loader);
            castCaster = castEvent.getMethod("getCaster");
            castSpell = castEvent.getMethod("getSpell");
            spellRemaining = service.getMethod("elementalRemaining", Player.class, spell, long.class);
            magicPlugin = magic;
            EventExecutor executor = (listener, event) -> {
                if (castEvent.isInstance(event)) onCast(event);
            };
            Bukkit.getPluginManager().registerEvent(castEvent, this, EventPriority.MONITOR, executor, plugin, true);
            plugin.getLogger().info("Charms hooked into Advance Magic casts");
        } catch (ReflectiveOperationException | LinkageError error) {
            magicPlugin = null;
            plugin.getLogger().warning("Charms: Advance Magic hook unavailable (" + error + "); Celestial Cascade stays inactive");
        }
    }

    /** A cast that really went off (its cooldown started a tick later) rains stars. */
    private void onCast(Event event) {
        try {
            Player player = (Player) castCaster.invoke(event);
            if (!has(player, Charm.CELESTIAL_CASCADE)) return;
            Object spell = castSpell.invoke(event);
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (!player.isOnline()) return;
                    long now = System.currentTimeMillis();
                    long remaining = (long) spellRemaining.invoke(manaService.invoke(magicPlugin), player, spell, now);
                    State state = state(player);
                    if (remaining <= 0 || now < state.cascadeReady) return;
                    state.cascadeReady = now + 1200;
                    cascade(player);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            });
        } catch (ReflectiveOperationException | RuntimeException error) {
            plugin.getLogger().fine("Charm cast hook: " + error);
        }
    }

    private void cascade(Player player) {
        List<LivingEntity> targets = new ArrayList<>(hostilesNear(player.getLocation(), 12));
        if (targets.isEmpty()) return;
        Collections.shuffle(targets);
        double damage = value(player, Charm.CELESTIAL_CASCADE);
        for (int i = 0; i < 3; i++) {
            LivingEntity target = targets.get(i % targets.size());
            int delay = i * 5;
            Bukkit.getScheduler().runTaskLater(plugin, () -> fallingStar(player, target.getLocation(), damage), delay);
        }
    }

    private void fallingStar(Player player, Location impact, double damage) {
        World world = impact.getWorld();
        world.playSound(impact, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.6f, 0.8f);
        for (int step = 0; step <= 6; step++) {
            int at = step;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location point = impact.clone().add(1.8 - at * 0.3, 6 - at, 0);
                world.spawnParticle(Particle.DUST, point, 1, 0.04, 0.04, 0.04, 0, dust(0xFFE27A, 0.8f));
                if (at < 6) return;
                world.spawnParticle(Particle.FIREWORK, impact.clone().add(0, 0.25, 0), 6, 0.3, 0.15, 0.3, 0.05);
                ring(world, impact.clone().add(0, 0.15, 0), 2.2, Particle.DUST, 8, dust(0xFFE27A, 0.7f));
                world.playSound(impact, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.9f, 0.9f);
                if (!player.isOnline()) return;
                for (LivingEntity mob : hostilesNear(impact, 2.2)) charmDamage(mob, damage, player);
            }, step);
        }
    }
}
