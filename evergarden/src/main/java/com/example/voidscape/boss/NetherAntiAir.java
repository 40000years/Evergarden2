package com.example.voidscape.boss;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
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
import org.bukkit.util.Vector;

/** A locked, dodgeable anti-air spell for each Nether sanctuary boss. */
final class NetherAntiAir {
    private static final int AIRBORNE_REQUIRED = 14;
    private static final int WARNING_TICKS = 30;
    private static final int COOLDOWN_TICKS = 180;
    private final RealmBosses.Kind kind;
    private final BiConsumer<Player, Double> damage;
    private final Map<UUID, Integer> airborneTicks = new HashMap<>();
    private Location locked;
    private UUID targetId;
    private int warningTicks;
    private int cooldown;

    NetherAntiAir(RealmBosses.Kind kind, BiConsumer<Player, Double> damage) {
        if (kind != RealmBosses.Kind.CINDER_COLOSSUS && kind != RealmBosses.Kind.SOUL_HOUND)
            throw new IllegalArgumentException("Nether anti-air requires a Nether boss");
        this.kind = kind;
        this.damage = damage;
    }

    /** Returns true while the boss should finish its warning before choosing another move. */
    boolean tick(List<Player> players, Location bossPosition, Location arenaCenter, boolean available) {
        Set<UUID> present = new HashSet<>();
        for (Player player : players) {
            UUID id = player.getUniqueId();
            present.add(id);
            if (eligible(player, arenaCenter)) airborneTicks.merge(id, 1, (a, b) -> Math.min(a + b, AIRBORNE_REQUIRED));
            else airborneTicks.remove(id);
        }
        airborneTicks.keySet().retainAll(present);
        if (cooldown > 0) cooldown--;
        if (!available) {
            cancelWarning();
            return false;
        }

        if (locked != null) {
            if (!present.contains(targetId) || bossPosition.getWorld() != locked.getWorld()) {
                cancelWarning();
                return false;
            }
            warningTicks++;
            drawWarning(bossPosition);
            if (warningTicks >= WARNING_TICKS) {
                fire(players, bossPosition);
                cancelWarning();
                return false;
            }
            return true;
        }
        if (cooldown > 0) return false;
        Player target = null;
        int longest = AIRBORNE_REQUIRED - 1;
        for (Player player : players) {
            int time = airborneTicks.getOrDefault(player.getUniqueId(), 0);
            if (time > longest) { target = player; longest = time; }
        }
        if (target == null) return false;
        locked = target.getLocation().add(0, 0.9, 0);
        targetId = target.getUniqueId();
        warningTicks = 0;
        cooldown = COOLDOWN_TICKS;
        if (kind == RealmBosses.Kind.CINDER_COLOSSUS) {
            target.sendMessage("§6⚠ เตาหลอมเล็งอุกกาบาตไฟที่ตำแหน่งของคุณ — รีบเคลื่อนที่!");
            locked.getWorld().playSound(locked, Sound.ENTITY_BLAZE_AMBIENT, 1.2f, 0.7f);
        } else {
            target.sendMessage("§b⚠ หมาป่าเล็งเขี้ยววิญญาณที่ตำแหน่งของคุณ — รีบเคลื่อนที่!");
            locked.getWorld().playSound(locked, Sound.ENTITY_WOLF_GROWL, 1.3f, 0.65f);
        }
        drawWarning(bossPosition);
        return true;
    }

    void reset() {
        airborneTicks.clear();
        cooldown = 0;
        cancelWarning();
    }

    private void cancelWarning() {
        locked = null;
        targetId = null;
        warningTicks = 0;
    }

    private static boolean eligible(Player player, Location center) {
        return player.isOnline() && !player.isDead()
            && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
            && player.getWorld() == center.getWorld()
            && !player.isOnGround() && player.getY() - center.getY() >= 4.5;
    }

    private Location origin(Location bossPosition) {
        return bossPosition.clone().add(0, kind == RealmBosses.Kind.CINDER_COLOSSUS ? 4.5 : 2.5, 0);
    }

    private void drawWarning(Location bossPosition) {
        if (warningTicks % 3 != 0) return;
        World world = locked.getWorld();
        double radius = kind == RealmBosses.Kind.CINDER_COLOSSUS ? 2.4 : 1.7;
        Color color = kind == RealmBosses.Kind.CINDER_COLOSSUS ? Color.fromRGB(255, 96, 20) : Color.fromRGB(55, 220, 240);
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.5f);
        for (int i = 0; i < 20; i++) {
            double a = i * Math.PI * 2 / 20;
            world.spawnParticle(Particle.DUST, locked.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius), 1, 0, 0, 0, 0, dust, true);
        }
        Location from = origin(bossPosition);
        Vector line = locked.toVector().subtract(from.toVector());
        double length = line.length();
        if (length > 0.01) {
            line.normalize();
            for (double d = 1; d < length; d += 2) {
                world.spawnParticle(Particle.DUST, from.clone().add(line.clone().multiply(d)), 1, 0, 0, 0, 0, dust, true);
            }
        }
    }

    private void fire(List<Player> players, Location bossPosition) {
        World world = locked.getWorld();
        Location from = origin(bossPosition);
        if (!unobstructed(from, locked)) return;
        boolean colossus = kind == RealmBosses.Kind.CINDER_COLOSSUS;
        double radius = colossus ? 2.4 : 1.7;
        world.playSound(locked, colossus ? Sound.ENTITY_GENERIC_EXPLODE : Sound.ENTITY_PHANTOM_BITE, 1.2f, colossus ? 0.85f : 0.7f);
        world.spawnParticle(colossus ? Particle.FLAME : Particle.SOUL_FIRE_FLAME, locked, 40, radius / 2, radius / 2, radius / 2, 0.04);
        if (colossus) world.spawnParticle(Particle.EXPLOSION, locked, 1, 0, 0, 0, 0);
        for (Player player : players) {
            if (!player.isOnline() || player.isDead() || player.getWorld() != world
                    || (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE)) continue;
            Location body = player.getLocation().add(0, 0.9, 0);
            if (body.distanceSquared(locked) > radius * radius || !unobstructed(locked, body)) continue;
            damage.accept(player, colossus ? 16.5 : 14.5);
            if (colossus) BossDamage.ignite(player,60,kind.title);
            else player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0));
        }
    }

    private static boolean unobstructed(Location from, Location to) {
        Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        return distance < 0.01 || from.getWorld().rayTraceBlocks(from, direction.normalize(), distance, FluidCollisionMode.NEVER, true) == null;
    }
}
