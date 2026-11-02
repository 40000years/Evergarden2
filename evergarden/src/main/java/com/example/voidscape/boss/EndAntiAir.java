package com.example.voidscape.boss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** A brief, dodgeable chorus projectile aimed at players hovering above the End arenas. */
final class EndAntiAir {
    private static final int AIR_TICKS = 10;
    private static final int WARNING_TICKS = 20;
    private static final int COOLDOWN_TICKS = 120;
    private static final double AIR_HEIGHT = 3.0;
    private static final double SPEED = 0.95;
    private static final int MAX_FLIGHT_TICKS = 55;

    private static final class Shot {
        final Location position;
        final Vector direction;
        final Set<UUID> hit;
        int ticks;

        Shot(Location position, Vector direction, Set<UUID> hit) {
            this.position = position.clone();
            this.direction = direction.clone();
            this.hit = hit;
        }
    }

    private final boolean moth;
    private final BiConsumer<Player, Double> damage;
    private final Map<UUID, Integer> airborne = new HashMap<>();
    private final List<Shot> shots = new ArrayList<>();
    private int cooldown;
    private int warning;
    private Location launch;
    private Location aim;

    EndAntiAir(RealmBosses.Kind kind, BiConsumer<Player, Double> damage) {
        if (kind != RealmBosses.Kind.CRYSTAL_MOTH && kind != RealmBosses.Kind.UNLIT_SUN) {
            throw new IllegalArgumentException("End anti-air requires an End boss");
        }
        this.moth = kind == RealmBosses.Kind.CRYSTAL_MOTH;
        this.damage = damage;
    }

    /** Returns true during the warning so a main attack cannot begin over the tell. */
    boolean tick(List<Player> players, Location bossPosition, Location arenaCenter, boolean available) {
        tickShots(players, arenaCenter);
        if (cooldown > 0) cooldown--;
        Set<UUID> present = new HashSet<>();
        for (Player p : players) {
            UUID id = p.getUniqueId();
            present.add(id);
            if (eligible(p, arenaCenter)) airborne.merge(id, 1, (a, b) -> Math.min(AIR_TICKS, a + b));
            else airborne.remove(id);
        }
        airborne.keySet().retainAll(present);

        if (!available) {
            cancelWarning();
            return false;
        }
        if (warning > 0) {
            warning--;
            if (warning % 3 == 0) drawWarning();
            if (warning == 0) {
                fire();
                cancelWarning();
                cooldown = COOLDOWN_TICKS;
                return false;
            }
            return true;
        }
        if (cooldown > 0) return false;

        Player target = null;
        double highest = AIR_HEIGHT;
        for (Player p : players) {
            double height = p.getY() - arenaCenter.getY();
            if (height > highest && airborne.getOrDefault(p.getUniqueId(), 0) >= AIR_TICKS) {
                highest = height;
                target = p;
            }
        }
        if (target == null) return false;
        launch = bossPosition.clone().add(0, moth ? 3.0 : 3.4, 0);
        aim = target.getLocation().add(0, 0.9, 0);
        if (launch.getWorld() != aim.getWorld() || launch.distanceSquared(aim) < 0.25) {
            cancelWarning();
            return false;
        }
        warning = WARNING_TICKS;
        target.sendActionBar(Component.text(moth
            ? "§d⚠ ราชินีผลึกเล็งลูกคอรัส! เคลื่อนที่หลบแนวยิง"
            : "§e⚠ สุริยะดับเล็งลูกคอรัสคู่! เคลื่อนที่หลบแนวยิง"));
        target.playSound(target.getLocation(), Sound.ENTITY_SHULKER_SHOOT, 1.0f, moth ? 1.3f : 0.8f);
        drawWarning();
        return true;
    }

    private boolean eligible(Player p, Location center) {
        return playable(p) && !p.isOnGround()
            && p.getWorld() == center.getWorld() && p.getY() - center.getY() > AIR_HEIGHT
            && !p.hasPotionEffect(PotionEffectType.LEVITATION);
    }

    private boolean playable(Player p) {
        return p.isOnline() && !p.isDead()
            && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE);
    }

    private void drawWarning() {
        if (launch == null || aim == null || launch.getWorld() == null) return;
        World world = launch.getWorld();
        Vector line = aim.toVector().subtract(launch.toVector());
        double length = line.length();
        if (length < 0.01) return;
        line.normalize();
        Particle.DustOptions dust = new Particle.DustOptions(moth ? Color.fromRGB(240, 120, 255) : Color.fromRGB(255, 195, 70), 1.3f);
        drawWarningLine(world, launch, line, length, dust);
        if (!moth) drawWarningLine(world, launch.clone().add(sideways(line)), line, length, dust);
        world.spawnParticle(Particle.END_ROD, aim, 3, 0.35, 0.35, 0.35, 0);
    }

    private void drawWarningLine(World world, Location start, Vector line, double length, Particle.DustOptions dust) {
        // Show only the unobstructed portion: cover remains a valid counterplay.
        RayTraceResult wall = world.rayTraceBlocks(start, line, length, FluidCollisionMode.NEVER, true);
        if (wall != null) length = Math.min(length, wall.getHitPosition().distance(start.toVector()));
        for (double d = 0; d <= length; d += 0.9) {
            world.spawnParticle(Particle.DUST, start.clone().add(line.clone().multiply(d)), 1, 0, 0, 0, 0, dust, true);
        }
    }

    private Vector sideways(Vector direction) {
        Vector side = new Vector(-direction.getZ(), 0, direction.getX());
        if (side.lengthSquared() < 0.01) side = new Vector(1, 0, 0);
        return side.normalize().multiply(0.65);
    }

    private void fire() {
        if (launch == null || aim == null || launch.getWorld() == null) return;
        Vector direction = aim.toVector().subtract(launch.toVector());
        if (direction.lengthSquared() < 0.01) return;
        direction.normalize();
        Set<UUID> volleyHit = new HashSet<>();
        shots.add(new Shot(launch, direction, volleyHit));
        if (!moth) {
            // Two parallel, separated rays; both are locked at launch and share one-hit protection.
            shots.add(new Shot(launch.clone().add(sideways(direction)), direction, volleyHit));
        }
        launch.getWorld().playSound(launch, Sound.ENTITY_SHULKER_SHOOT, 1.3f, moth ? 1.25f : 0.7f);
    }

    private void tickShots(List<Player> players, Location arenaCenter) {
        for (Iterator<Shot> it = shots.iterator(); it.hasNext();) {
            Shot shot = it.next();
            World world = shot.position.getWorld();
            if (world == null || ++shot.ticks > MAX_FLIGHT_TICKS) { it.remove(); continue; }
            RayTraceResult wall = world.rayTraceBlocks(shot.position, shot.direction, SPEED, FluidCollisionMode.NEVER, true);
            double length = wall == null ? SPEED : Math.min(SPEED, wall.getHitPosition().distance(shot.position.toVector()));
            Player victim = null;
            double closest = length + 1e-6;
            for (Player p : players) {
                if (p.getWorld() != world || !playable(p) || shot.hit.contains(p.getUniqueId())
                    || p.getY() - arenaCenter.getY() <= AIR_HEIGHT) continue;
                RayTraceResult hit = p.getBoundingBox().expand(0.35).rayTrace(shot.position.toVector(), shot.direction, length);
                if (hit == null) continue;
                double d = hit.getHitPosition().distance(shot.position.toVector());
                if (d < closest) { closest = d; victim = p; }
            }
            if (victim != null) {
                shot.hit.add(victim.getUniqueId());
                damage.accept(victim, moth ? 13.0 : 14.5);
                if (victim.isOnline() && !victim.isDead()) {
                    // The catch interrupts a developing smash rather than carrying its stored fall into levitation.
                    victim.setFallDistance(0);
                    PotionEffect previous = victim.getPotionEffect(PotionEffectType.LEVITATION);
                    if (previous == null || (previous.getAmplifier() == 0 && previous.getDuration() < (moth ? 50 : 60))) {
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, moth ? 50 : 60, 0, false, true, true));
                    }
                }
                world.spawnParticle(Particle.PORTAL, victim.getLocation().add(0, 1, 0), 20, 0.35, 0.5, 0.35, 0.1);
                it.remove();
                continue;
            }
            if (wall != null) { it.remove(); continue; }
            shot.position.add(shot.direction.clone().multiply(SPEED));
            world.spawnParticle(Particle.END_ROD, shot.position, 2, 0.07, 0.07, 0.07, 0);
            world.spawnParticle(Particle.PORTAL, shot.position, 3, 0.12, 0.12, 0.12, 0.03);
        }
    }

    private void cancelWarning() {
        warning = 0;
        launch = null;
        aim = null;
    }

    void reset() {
        cancelWarning();
        shots.clear();
        airborne.clear();
        cooldown = 0;
    }
}
