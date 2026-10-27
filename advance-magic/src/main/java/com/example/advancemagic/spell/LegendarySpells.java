package com.example.advancemagic.spell;

import com.example.advancemagic.effect.EffectEngine;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Three automatic, staged legendary casts. One scope owns the entire entrance,
 * attacks, final strike and fading authored art. No blocks or global world state
 * are changed, and all opponent interaction follows MagicContext's policies.
 */
public final class LegendarySpells implements Listener {
    static final int FINAL_TICK = 112;
    static final int DURATION = 156;
    private final MagicContext c;
    private final Map<UUID, Set<EffectEngine.Effect>> activeByWorld = new HashMap<>();

    public LegendarySpells(MagicContext c) {
        this.c = c;
        Bukkit.getPluginManager().registerEvents(this, c.plugin);
    }

    public boolean verdant(Player player) {
        return directional(player, LegendarySpellVisuals.Kind.VERDANT);
    }

    public boolean lunar(Player player) {
        return directional(player, LegendarySpellVisuals.Kind.LUNAR);
    }

    public boolean crimson(Player player) {
        return cast(player, Spell.CRIMSON_OBLIVION, LegendarySpellVisuals.Kind.CRIMSON);
    }

    /** Frozen caster/aim geometry drives both the readable artwork and attacks. */
    private boolean directional(Player owner, LegendarySpellVisuals.Kind kind) {
        boolean verdant = kind == LegendarySpellVisuals.Kind.VERDANT;
        Spell spell = verdant ? Spell.VERDANT_SOVEREIGN : Spell.LUNAR_REQUIEM;
        Location base = verdant ? owner.getLocation() : center(owner);
        if (base == null || !c.loaded(base) || !room(base.getWorld())) return false;
        double radius = finiteConfig("legendary.radius", 14, 8, 18);
        double scale = Math.min(finiteConfig("legendary.visual-scale", 1, .5, 1.5),
                (base.getWorld().getMaxHeight() - 2 - base.getY()) / 36.0);
        if (scale < .25) return false;
        Vector facing = LegendaryCastGeometry.capturedForward(owner);
        LegendaryCastGeometry.Scene geometry = verdant
                ? new LegendaryCastGeometry.Verdant(c, base, facing, scale)
                : new LegendaryCastGeometry.Lunar(c, base, facing, scale, radius, owner.getEyeLocation());
        if (geometry instanceof LegendaryCastGeometry.Verdant dragon
                && (dragon.beam() == null || dragon.beam().length() < 1 || !c.loaded(dragon.sigil()))) return false;
        if (geometry instanceof LegendaryCastGeometry.Lunar moons && !moons.complete()) return false;
        double power = c.getCastDamageMultiplier(owner.getUniqueId());
        double opening = c.configuredDamage(verdant ? "damage.verdant-thorns" : "damage.lunar-slash", verdant ? 65 : 70);
        double secondary = c.configuredDamage(verdant ? "damage.verdant-dragon-breath" : "damage.lunar-final", verdant ? 55 : 110);
        double finale = c.configuredDamage(verdant ? "damage.verdant-final" : "damage.lunar-moonfall", verdant ? 240 : 260);
        LegendarySpellVisuals[] art = {null};
        boolean[] cancelled = {false};
        UUID worldId = base.getWorld().getUID();
        var scope = c.plugin.effects().start(owner, DURATION, (effect, age) -> {
            if (Bukkit.getWorld(worldId) != base.getWorld() || !c.loaded(base)) return false;
            if (age % 4 == 0) geometry.refresh(c);
            if (age == 0) {
                art[0] = new LegendarySpellVisuals(c, effect, geometry, kind, radius);
                effect.onClose(art[0]::close);
                entrance(base, kind);
            }
            art[0].frame(age);
            List<LegendaryCastGeometry.Beam> firing = new ArrayList<>(LegendaryCastGeometry.LUNAR_COUNT);
            double amount = 0;
            if (geometry instanceof LegendaryCastGeometry.Verdant dragon) {
                boolean first = age >= 24 && age <= 60 && (age - 24) % 12 == 0;
                boolean sustained = age >= 64 && age <= 96 && (age - 64) % 8 == 0;
                if (first || sustained || age == FINAL_TICK) {
                    var beam = age == FINAL_TICK ? dragon.finalBeam() : dragon.beam();
                    if (beam != null) firing.add(beam);
                    amount = age == FINAL_TICK ? finale : sustained ? secondary : opening;
                }
            } else if (geometry instanceof LegendaryCastGeometry.Lunar moons) {
                boolean all = age == FINAL_TICK || age == 124 || age == 136;
                for (int column = 0; column < LegendaryCastGeometry.LUNAR_COUNT; column++) {
                    if (all || LegendaryCastGeometry.Lunar.openingPulse(age, column)) {
                        var beam = moons.columns().get(column);
                        if (beam != null) firing.add(beam);
                    }
                }
                amount = age == FINAL_TICK ? finale : all ? secondary : opening;
            }
            if (!firing.isEmpty()) {
                // One union query and UUID set make simultaneous columns deal
                // damage only once per target, even for a body crossing rings.
                for (LivingEntity enemy : beamTargets(owner, base, firing, spell)) {
                    if (cancelled[0]) return false;
                    if (!beamEligible(owner, enemy, firing)) continue;
                    c.damage(owner, enemy, amount * power, DamageType.MAGIC);
                    if (cancelled[0]) return false;
                    if (!beamEligible(owner, enemy, firing)) continue;
                    if (verdant) {
                        if (age == FINAL_TICK) c.velocity(enemy, facing.clone().multiply(1.1).setY(.12));
                        else c.potion(enemy, PotionEffectType.SLOWNESS, 20, 1);
                    }
                }
                pulseSound(base, kind, age);
            }
            return true;
        });
        scope.onClose(() -> cancelled[0] = true);
        activeByWorld.computeIfAbsent(worldId, ignored -> new LinkedHashSet<>()).add(scope);
        scope.onClose(() -> {
            Set<EffectEngine.Effect> effects = activeByWorld.get(worldId);
            if (effects != null && effects.remove(scope) && effects.isEmpty()) activeByWorld.remove(worldId);
        });
        return true;
    }

    private List<LivingEntity> beamTargets(Player owner, Location base, List<LegendaryCastGeometry.Beam> beams, Spell spell) {
        var bounds = beams.getFirst().bounds();
        for (int i = 1; i < beams.size(); i++) bounds.union(beams.get(i).bounds());
        World world = base.getWorld();
        int minX = ((int) Math.floor(bounds.getMinX())) >> 4, maxX = ((int) Math.floor(bounds.getMaxX())) >> 4;
        int minZ = ((int) Math.floor(bounds.getMinZ())) >> 4, maxZ = ((int) Math.floor(bounds.getMaxZ())) >> 4;
        Map<UUID, LivingEntity> candidates = new HashMap<>();
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            if (!world.isChunkLoaded(x, z)) continue;
            for (Entity entity : world.getChunkAt(x, z).getEntities()) {
                if (entity instanceof LivingEntity living && beamEligible(owner, living, beams)) {
                    candidates.put(living.getUniqueId(), living);
                }
            }
        }
        int limit = Math.clamp(c.plugin.getConfig().getInt("max-targets-per-effect", 32), 1, 128);
        return candidates.values().stream().sorted(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(base)))
                .limit(limit).filter(enemy -> c.affect(owner, enemy, spell) && beamEligible(owner, enemy, beams)).toList();
    }

    private boolean beamEligible(Player owner, LivingEntity enemy, List<LegendaryCastGeometry.Beam> beams) {
        World world = beams.getFirst().from().getWorld();
        return owner.isOnline() && !owner.isDead() && owner.getWorld() == world && enemy.getWorld() == world
                && c.enemy(owner, enemy) && c.loaded(enemy.getLocation())
                && beams.stream().anyMatch(beam -> beam.contains(enemy.getBoundingBox()));
    }

    private boolean cast(Player owner, Spell spell, LegendarySpellVisuals.Kind kind) {
        Location base = center(owner);
        if (base == null || !room(base.getWorld())) return false;
        double radius = finiteConfig("legendary.radius", 14, 8, 18);
        double requestedScale = finiteConfig("legendary.visual-scale", 1, .5, 1.5);
        // Scale vertical features together under a low ceiling. Horizontal seals
        // still advertise the actual damage radius, even in very tall worlds.
        double scale = Math.min(requestedScale,
                (base.getWorld().getMaxHeight() - 2 - base.getY()) / 36.0);
        if (scale < .25) return false;

        // The listener clears this context as soon as cast() returns. Capture
        // it once for every later pulse, including the two automatic echoes.
        double multiplier = c.getCastDamageMultiplier(owner.getUniqueId());
        double opening, secondary, finalDamage;
        switch (kind) {
            case VERDANT -> {
                opening = c.configuredDamage("damage.verdant-thorns", 65);
                secondary = c.configuredDamage("damage.verdant-dragon-breath", 55);
                finalDamage = c.configuredDamage("damage.verdant-final", 240);
            }
            case LUNAR -> {
                opening = c.configuredDamage("damage.lunar-slash", 70);
                secondary = c.configuredDamage("damage.lunar-final", 110);
                finalDamage = c.configuredDamage("damage.lunar-moonfall", 260);
            }
            case CRIMSON -> {
                opening = c.configuredDamage("damage.crimson-heartbeat", 75);
                secondary = c.configuredDamage("damage.crimson-aftershock", 100);
                finalDamage = c.configuredDamage("damage.crimson-collapse", 300);
            }
            default -> throw new IllegalStateException("Unknown legendary cast");
        }
        LegendarySpellVisuals[] art = {null};
        boolean[] cancelled = {false};
        UUID worldId = base.getWorld().getUID();
        var scope = c.plugin.effects().start(owner, kind == LegendarySpellVisuals.Kind.CRIMSON ? 184 : DURATION, (effect, age) -> {
            if (Bukkit.getWorld(worldId) != base.getWorld() || !c.loaded(base)) return false;
            if (age == 0) {
                art[0] = new LegendarySpellVisuals(c, effect, base, kind, radius, scale);
                effect.onClose(art[0]::close);
                entrance(base, kind);
            }
            art[0].frame(age);

            if (age >= 24 && age < (kind == LegendarySpellVisuals.Kind.CRIMSON ? 96 : FINAL_TICK) && age % 8 == 0) {
                for (LivingEntity enemy : targets(owner, base, radius, 26 * scale, spell)) {
                    if (cancelled[0]) return false;
                    if (!eligible(owner, enemy, base, radius, 26 * scale)) continue;
                    control(enemy, base, kind, age, scale);
                }
            }

            boolean openingPulse = switch (kind) {
                case VERDANT -> age >= 24 && age <= 60 && (age - 24) % 12 == 0;
                case LUNAR -> age >= 56 && age <= 92 && (age - 56) % 12 == 0;
                case CRIMSON -> age >= 40 && age <= 88 && (age - 40) % 16 == 0;
            };
            boolean breathPulse = kind == LegendarySpellVisuals.Kind.VERDANT
                    && age >= 64 && age <= 96 && (age - 64) % 8 == 0;
            boolean echoPulse = kind == LegendarySpellVisuals.Kind.LUNAR
                    && (age == 124 || age == 136);
            boolean shardImpact = kind == LegendarySpellVisuals.Kind.CRIMSON && LegendaryCastGeometry.crimsonImpact(age);
            boolean oldFinale = kind != LegendarySpellVisuals.Kind.CRIMSON && age == FINAL_TICK;
            if (openingPulse || breathPulse || echoPulse || oldFinale || shardImpact) {
                double amount = shardImpact ? (finalDamage + 2 * secondary) / LegendaryCastGeometry.CRIMSON_COUNT
                        : oldFinale ? finalDamage
                        : breathPulse || echoPulse ? secondary : opening;
                // One fresh protection-event check covers this pulse's damage
                // and impulse. No cached target can outlive a team/PvP change.
                for (LivingEntity enemy : targets(owner, base, radius, 26 * scale, spell)) {
                    if (cancelled[0]) return false;
                    if (!eligible(owner, enemy, base, radius, 26 * scale)) continue;
                    c.damage(owner, enemy, amount * multiplier, DamageType.MAGIC);
                    // Damage events may change teams/worlds, move the target,
                    // or close the caster's effect while this body is running.
                    if (cancelled[0]) return false;
                    if (!eligible(owner, enemy, base, radius, 26 * scale)) continue;
                    if (shardImpact) {
                        Vector push = enemy.getLocation().toVector().subtract(base.toVector()).setY(0);
                        if (push.lengthSquared() > .001) push.normalize().multiply(.2);
                        c.velocity(enemy, push.setY(.12));
                    } else if (oldFinale) impact(enemy, base, kind);
                    else if (kind == LegendarySpellVisuals.Kind.VERDANT && openingPulse) {
                        c.potion(enemy, PotionEffectType.SLOWNESS, 22, 2);
                    }
                }
                pulseSound(base, kind, age);
            }
            return true;
        });
        scope.onClose(() -> cancelled[0] = true);
        activeByWorld.computeIfAbsent(worldId, ignored -> new LinkedHashSet<>()).add(scope);
        scope.onClose(() -> {
            Set<EffectEngine.Effect> effects = activeByWorld.get(worldId);
            if (effects != null && effects.remove(scope) && effects.isEmpty()) activeByWorld.remove(worldId);
        });
        return true;
    }

    private double finiteConfig(String path, double fallback, double low, double high) {
        double value = c.plugin.getConfig().getDouble(path, fallback);
        return Math.clamp(Double.isFinite(value) ? value : fallback, low, high);
    }

    private boolean room(World world) {
        int limit = Math.clamp(c.plugin.getConfig().getInt("legendary.max-active-per-world", 2), 1, 8);
        Set<EffectEngine.Effect> effects = activeByWorld.get(world.getUID());
        return c.plugin.effects().hasCapacity() && (effects == null || effects.size() < limit);
    }

    /** Follow the existing mythic aim rules, then find a loaded collision surface. */
    private Location center(Player player) {
        var hit = c.target(player, 30);
        Location aim;
        if (hit == null) {
            Location eye = player.getEyeLocation();
            Vector direction = eye.getDirection();
            aim = eye.clone();
            for (double distance = .5; distance <= 16; distance += .5) {
                Location candidate = eye.clone().add(direction.clone().multiply(distance));
                if (!c.loaded(candidate)) break;
                aim = candidate;
            }
        } else if (hit.getHitEntity() != null) {
            aim = hit.getHitEntity().getLocation().add(0, .04, 0);
        } else {
            aim = hit.getHitPosition().toLocation(player.getWorld());
            if (hit.getHitBlockFace() != null) aim.add(hit.getHitBlockFace().getDirection().multiply(.02));
        }
        if (!c.loaded(aim)) return null;
        double distance = aim.getY() - aim.getWorld().getMinHeight();
        if (distance <= 0) return null;
        // This vertical trace stays in the already loaded aim chunk. Retrace its
        // collision shape from above to handle stairs, slabs and fences exactly.
        var ground = aim.getWorld().rayTraceBlocks(aim, new Vector(0, -1, 0),
                distance, FluidCollisionMode.NEVER, true);
        if (ground == null || ground.getHitBlock() == null) return null;
        Location above = new Location(aim.getWorld(), aim.getX(),
                Math.min(aim.getWorld().getMaxHeight() - .01, ground.getHitBlock().getY() + 2), aim.getZ());
        var surface = ground.getHitBlock().rayTrace(above, new Vector(0, -1, 0), 3, FluidCollisionMode.NEVER);
        if (surface == null) return null;
        Location base = surface.getHitPosition().toLocation(aim.getWorld()).add(0, .04, 0);
        return c.loaded(base) ? base : null;
    }

    /**
     * Enumerate only loaded chunks instead of asking Bukkit to search a large
     * sphere. Limit vertical bounds, deterministic distance order and targets.
     */
    private List<LivingEntity> targets(Player owner, Location at, double radius, double height, Spell spell) {
        if (!c.loaded(at)) return List.of();
        World world = at.getWorld();
        int minX = ((int) Math.floor(at.getX() - radius)) >> 4;
        int maxX = ((int) Math.floor(at.getX() + radius)) >> 4;
        int minZ = ((int) Math.floor(at.getZ() - radius)) >> 4;
        int maxZ = ((int) Math.floor(at.getZ() + radius)) >> 4;
        List<LivingEntity> candidates = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            if (!world.isChunkLoaded(x, z)) continue;
            for (Entity entity : world.getChunkAt(x, z).getEntities()) {
                if (!(entity instanceof LivingEntity living) || !eligible(owner, living, at, radius, height)) continue;
                candidates.add(living);
            }
        }
        int limit = Math.clamp(c.plugin.getConfig().getInt("max-targets-per-effect", 32), 1, 128);
        return candidates.stream().sorted(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(at)))
                .limit(limit).filter(e -> c.affect(owner, e, spell)
                        && eligible(owner, e, at, radius, height)).toList();
    }

    /** Revalidate after external events, without firing a second protection event. */
    private boolean eligible(Player owner, LivingEntity enemy, Location at, double radius, double height) {
        World world = at.getWorld();
        if (!owner.isOnline() || owner.isDead() || owner.getWorld() != world
                || enemy.getWorld() != world || !c.enemy(owner, enemy)) return false;
        Location position = enemy.getLocation();
        if (!c.loaded(position)) return false;
        double dx = position.getX() - at.getX(), dz = position.getZ() - at.getZ();
        double bottom = Math.max(world.getMinHeight(), at.getY() - 4);
        double top = Math.min(world.getMaxHeight() - .01, at.getY() + Math.max(12, height));
        return position.getY() >= bottom && position.getY() <= top && dx * dx + dz * dz <= radius * radius;
    }

    private void control(LivingEntity enemy, Location base, LegendarySpellVisuals.Kind kind, int age, double scale) {
        Vector toward = base.toVector().subtract(enemy.getLocation().toVector()).setY(0);
        double length = toward.length();
        Vector velocity = length > .8 ? toward.multiply(Math.min(.23, length * .09) / length) : new Vector();
        if (kind == LegendarySpellVisuals.Kind.LUNAR && age >= 32 && age < 100) {
            double altitude = enemy.getLocation().getY() - base.getY();
            velocity.setY(altitude < 7 * scale ? Math.min(.4, Math.max(.04, (7 * scale - altitude) * .12)) : .015);
            c.potion(enemy, PotionEffectType.SLOW_FALLING, 26, 0);
        } else {
            // Preserve jumping/falling until the authored attack commits.
            velocity.setY(enemy.getVelocity().getY());
            c.potion(enemy, PotionEffectType.SLOWNESS, 14, kind == LegendarySpellVisuals.Kind.VERDANT ? 2 : 1);
        }
        c.velocity(enemy, velocity);
    }

    private void impact(LivingEntity enemy, Location base, LegendarySpellVisuals.Kind kind) {
        Vector outward = enemy.getLocation().toVector().subtract(base.toVector()).setY(0);
        if (outward.lengthSquared() < .001) outward = new Vector(1, 0, 0);
        outward.normalize();
        if (kind == LegendarySpellVisuals.Kind.LUNAR) {
            c.potion(enemy, PotionEffectType.SLOW_FALLING, 30, 0);
            c.velocity(enemy, outward.multiply(.55).setY(-.65));
        } else c.velocity(enemy, outward.multiply(kind == LegendarySpellVisuals.Kind.CRIMSON ? 1.15 : .95).setY(.45));
    }

    private void entrance(Location base, LegendarySpellVisuals.Kind kind) {
        base.getWorld().playSound(base, Sound.BLOCK_BEACON_ACTIVATE, 1.8f,
                kind == LegendarySpellVisuals.Kind.CRIMSON ? .55f : .85f);
        base.getWorld().playSound(base, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.5f, .65f);
    }

    private void pulseSound(Location base, LegendarySpellVisuals.Kind kind, int age) {
        World world = base.getWorld();
        if (kind == LegendarySpellVisuals.Kind.CRIMSON && age >= LegendaryCastGeometry.CRIMSON_START) {
            world.playSound(base, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.8f, .65f);
            world.playSound(base, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.5f, .8f);
            return;
        }
        if (age == FINAL_TICK) {
            world.playSound(base, Sound.ENTITY_GENERIC_EXPLODE, 2.5f, .65f);
            world.playSound(base, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2f, 1.2f);
            world.playSound(base, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 2f, .65f);
        } else if (kind == LegendarySpellVisuals.Kind.VERDANT) {
            world.playSound(base, age < 64 ? Sound.BLOCK_AMETHYST_CLUSTER_BREAK : Sound.ENTITY_ENDER_DRAGON_GROWL,
                    age < 64 ? 1.3f : 1.7f, age < 64 ? .85f : 1.3f);
        } else if (kind == LegendarySpellVisuals.Kind.LUNAR) {
            world.playSound(base, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.7f, age < 112 ? 1.5f : .6f);
            world.playSound(base, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.3f, .6f);
        } else {
            world.playSound(base, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.9f, .5f);
            world.playSound(base, Sound.ENTITY_WARDEN_HEARTBEAT, 1.8f, .65f);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void worldUnload(WorldUnloadEvent event) {
        Set<EffectEngine.Effect> effects = activeByWorld.get(event.getWorld().getUID());
        if (effects != null) for (EffectEngine.Effect effect : List.copyOf(effects)) effect.close();
    }
}
