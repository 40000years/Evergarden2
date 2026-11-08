package com.example.voidscape.boss;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Hunts players who camp on a ledge, a wall alcove, a leftover block or the floor outside the arena ring, where
 * the floor-bound boss and its ground attacks cannot reach. Height is never punished on its own: Wind Charge and
 * Mace flight build no heat, and only standing time on a perch does. The heat drains only once the player commits
 * to the fight a few blocks inside the ring, so hugging the edge or hopping in and out of it keeps the heat. After
 * the grace period the boss calls a strike on the perch: it chases the target along the ledge or the ring edge for
 * most of its warning, lands through cover, knocks everyone on the spot back into the arena, blows away blocks
 * that do not belong to the sanctuary and grows stronger until the player commits. Diving off the perch with a
 * Mace smash on the boss (Skyfall) ends the hunt.
 */
final class PerchHunt {
    /** Floor attacks reach about 2.2–3.8 blocks above the arena floor. */
    static final double PERCH_HEIGHT = 2.5;
    static final double EDGE_SLACK = 0.5;
    /** How far inside the ring the floor counts as committed to the fight (the boss walks to R − 2.2). */
    static final double COMMIT_DEPTH = 4.0;
    static final int HINT_TICKS = 20;
    static final int WARNING_TICKS = 30;
    /** The mark tracks the target along the ledge for this many warning ticks, then locks. */
    static final int FOLLOW_TICKS = 20;
    static final int COOLDOWN_TICKS = 50;
    static final double RADIUS = 2.6;
    static final double[] DAMAGE = {12.0, 16.0, 20.0};

    interface Hooks {
        /** Deals the boss's damage; true when it landed. */
        boolean strike(Player player, double damage);
        /** Removes non-sanctuary blocks around a perch; returns how many. */
        int clear(Location at, double radius);
    }

    private static final class Perch {
        int heat;
        int cooldown;
        int warning;
        int escalation;
        boolean hinted;
        Location mark;
    }

    private final RealmBosses.Kind kind;
    private final Location center;
    private final double ring;
    private final int graceTicks;
    private final Hooks hooks;
    private final Map<UUID, Perch> perches = new HashMap<>();
    private int ticks;

    PerchHunt(RealmBosses.Kind kind, Location center, double ring, int graceTicks, Hooks hooks) {
        this.kind = kind;
        this.center = center.clone();
        this.ring = ring;
        this.graceTicks = Math.max(HINT_TICKS + 1, graceTicks);
        this.hooks = hooks;
    }

    /** Standing (not flying) on something higher than the floor attacks reach, or outside the drawn ring. */
    boolean perched(Player p) {
        if (!p.isOnGround()) return false;
        double flat = Math.hypot(p.getX() - center.getX(), p.getZ() - center.getZ());
        return p.getY() - center.getY() >= PERCH_HEIGHT || flat > ring + EDGE_SLACK;
    }

    /** On the arena floor well inside the ring, where the boss and its ground attacks reach. */
    boolean committed(Player p) {
        if (!p.isOnGround()) return false;
        double flat = Math.hypot(p.getX() - center.getX(), p.getZ() - center.getZ());
        return p.getY() - center.getY() < PERCH_HEIGHT && flat <= ring - COMMIT_DEPTH;
    }

    void tick(List<Player> players, Location boss) {
        ticks++;
        Set<UUID> present = new HashSet<>();
        for (Player p : players) {
            if (!playable(p) || p.getWorld() != center.getWorld()) continue;
            Perch s = perches.computeIfAbsent(p.getUniqueId(), id -> new Perch());
            present.add(p.getUniqueId());
            if (s.cooldown > 0) s.cooldown--;
            boolean high = perched(p);
            boolean committed = !high && committed(p);
            if (high) {
                s.heat = Math.min(graceTicks, s.heat + 1);
                if (!s.hinted && s.heat >= HINT_TICKS && s.mark == null) {
                    s.hinted = true;
                    hint(p);
                }
            } else if (committed) {
                // Into the fight: the heat drains and the escalation forgets. The edge band and the air keep both.
                s.heat = Math.max(0, s.heat - 3);
                if (s.heat == 0) {
                    s.escalation = 0;
                    s.hinted = false;
                }
            }
            if (s.mark != null) {
                s.warning--;
                // The mark chases along ledges and the ring edge until the target commits or takes off
                if (p.isOnGround() && !committed && s.warning >= WARNING_TICKS - FOLLOW_TICKS) s.mark = p.getLocation();
                telegraph(s, boss);
                if (s.warning <= 0) fire(s, players, boss);
            } else if (high && s.heat >= graceTicks && s.cooldown == 0) {
                begin(p, s);
            }
        }
        perches.keySet().retainAll(present);
    }

    /** A Mace smash on the boss by a player who stood on a perch since last touching the floor. */
    boolean skyfall(Player p) {
        Perch s = perches.get(p.getUniqueId());
        if (s == null || (s.heat <= 0 && s.mark == null)) return false;
        s.heat = 0;
        s.escalation = 0;
        s.warning = 0;
        s.cooldown = 0;
        s.hinted = false;
        s.mark = null;
        return true;
    }

    void reset() {
        perches.clear();
    }

    void forget(UUID player) {
        perches.remove(player);
    }

    private static boolean playable(Player p) {
        return p.isOnline() && !p.isDead()
            && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE);
    }

    private void hint(Player p) {
        boolean outside = p.getY() - center.getY() < PERCH_HEIGHT;
        int seconds = (int) Math.ceil(graceTicks / 20.0);
        p.sendActionBar(Component.text(outside
            ? "§e⚠ คุณยืนนอกวงแหวน — ตีจากนอกวงไม่เข้า เข้ามาในลานภายใน " + seconds + " วินาที ก่อนบอสล่า"
            : "§e⚠ บอสเห็นคุณบนที่สูง — ยืนได้ " + seconds + " วินาที แล้วดิ่งลงมาฟาดด้วย Mace ได้เลย!"));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, 0.6f);
    }

    private void begin(Player p, Perch s) {
        s.mark = p.getLocation();
        s.warning = WARNING_TICKS;
        String escape = p.getY() - center.getY() < PERCH_HEIGHT ? "เข้าลึกไปในลาน!" : "ลงจากที่สูง หรือดิ่งลงมาฟาด!";
        p.sendActionBar(Component.text(switch (kind) {
            case CINDER_COLOSSUS -> "§6⚠ อุกกาบาตทลายป้อมพุ่งมาที่คุณ — ";
            case SOUL_HOUND -> "§b⚠ โซ่วิญญาณจะกระชากคุณ — ";
            case CRYSTAL_MOTH -> "§d⚠ หอกปริซึมเล็งคุณ — ";
            case UNLIT_SUN -> "§e⚠ แรงโน้มถ่วงสุริยะดับดึงคุณ — ";
        } + escape));
        World w = s.mark.getWorld();
        switch (kind) {
            case CINDER_COLOSSUS -> w.playSound(s.mark, Sound.ENTITY_BLAZE_SHOOT, 1.6f, 0.5f);
            case SOUL_HOUND -> w.playSound(s.mark, Sound.ENTITY_WOLF_GROWL, 1.6f, 0.55f);
            case CRYSTAL_MOTH -> w.playSound(s.mark, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.6f, 0.6f);
            case UNLIT_SUN -> w.playSound(s.mark, Sound.BLOCK_BEACON_POWER_SELECT, 1.6f, 0.5f);
        }
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.8f);
    }

    private Location origin(Location boss) {
        return boss.clone().add(0, switch (kind) {
            case CINDER_COLOSSUS -> 7.0;
            case SOUL_HOUND -> 3.2;
            case CRYSTAL_MOTH -> 5.0;
            case UNLIT_SUN -> 5.4;
        }, 0);
    }

    private Color tone() {
        return switch (kind) {
            case CINDER_COLOSSUS -> Color.fromRGB(255, 95, 20);
            case SOUL_HOUND -> Color.fromRGB(65, 230, 255);
            case CRYSTAL_MOTH -> Color.fromRGB(240, 110, 255);
            case UNLIT_SUN -> Color.fromRGB(255, 200, 60);
        };
    }

    private Particle accent() {
        return switch (kind) {
            case CINDER_COLOSSUS, UNLIT_SUN -> Particle.FLAME;
            case SOUL_HOUND -> Particle.SOUL_FIRE_FLAME;
            case CRYSTAL_MOTH -> Particle.END_ROD;
        };
    }

    private void telegraph(Perch s, Location boss) {
        World w = s.mark.getWorld();
        Location from = origin(boss);
        Vector path = s.mark.toVector().add(new Vector(0, 0.9, 0)).subtract(from.toVector());
        double length = path.length();
        if (s.warning <= 10 && length > 0.5) {
            // The strike itself, crossing the arena during the last half second
            Location head = from.clone().add(path.clone().multiply(1.0 - s.warning / 10.0));
            w.spawnParticle(accent(), head, 6, 0.18, 0.18, 0.18, 0.01, null, true);
            if (kind == RealmBosses.Kind.CINDER_COLOSSUS) w.spawnParticle(Particle.LAVA, head, 1, 0.1, 0.1, 0.1, 0, null, true);
        }
        if (s.warning % 2 != 0) return;
        var dust = new Particle.DustOptions(tone(), 1.5f);
        var core = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 1.1f);
        boolean locked = s.warning < WARNING_TICKS - FOLLOW_TICKS;
        int points = 20;
        double spin = ticks * 0.09;
        for (int i = 0; i < points; i++) {
            double a = spin + i * (2 * Math.PI / points);
            w.spawnParticle(Particle.DUST, s.mark.clone().add(Math.cos(a) * RADIUS, 0.15, Math.sin(a) * RADIUS), 1, 0, 0, 0, 0, dust, true);
        }
        // A pillar on the perch, so the whole arena reads who is being hunted; it turns white once locked
        for (double h = 0.6; h <= 6.0; h += 0.9) {
            w.spawnParticle(Particle.DUST, s.mark.clone().add(0, h, 0), 1, 0, 0, 0, 0, locked ? core : dust, true);
        }
        if (s.warning % 4 == 0 && length > 0.5) {
            Vector step = path.clone().normalize();
            for (double d = 1.0; d < length; d += 1.6) {
                w.spawnParticle(Particle.DUST, from.clone().add(step.clone().multiply(d)), 1, 0, 0, 0, 0, dust, true);
            }
        }
    }

    private void fire(Perch s, List<Player> players, Location boss) {
        Location at = s.mark;
        s.mark = null;
        s.warning = 0;
        s.cooldown = COOLDOWN_TICKS;
        double damage = DAMAGE[Math.min(s.escalation, DAMAGE.length - 1)];
        s.escalation = Math.min(DAMAGE.length - 1, s.escalation + 1);
        impact(at);
        int cleared = hooks.clear(at, RADIUS);
        for (Player p : players) {
            if (!playable(p) || p.getWorld() != at.getWorld()) continue;
            double dy = p.getY() - at.getY();
            if (dy < -1.2 || dy > 3.2 || Math.hypot(p.getX() - at.getX(), p.getZ() - at.getZ()) > RADIUS) continue;
            hooks.strike(p, damage);
            if (p.isOnline() && !p.isDead()) launch(p, boss);
            if (cleared > 0) p.sendActionBar(Component.text("§6✦ บอสทลายบล็อกแปลกปลอมที่ใช้ซุ่มทิ้ง " + cleared + " บล็อก!"));
        }
    }

    private void impact(Location at) {
        World w = at.getWorld();
        Location mid = at.clone().add(0, 0.8, 0);
        var dust = new Particle.DustOptions(tone(), 1.8f);
        for (int i = 0; i < 24; i++) {
            double a = i * (2 * Math.PI / 24);
            w.spawnParticle(Particle.DUST, at.clone().add(Math.cos(a) * RADIUS, 0.3, Math.sin(a) * RADIUS), 1, 0, 0, 0, 0, dust, true);
        }
        switch (kind) {
            case CINDER_COLOSSUS -> {
                w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);
                w.spawnParticle(Particle.EXPLOSION, mid, 3, 0.8, 0.4, 0.8, 0, null, true);
                w.spawnParticle(Particle.LAVA, mid, 14, 1.0, 0.4, 1.0, 0, null, true);
                w.spawnParticle(Particle.FLAME, mid, 30, 1.0, 0.6, 1.0, 0.05, null, true);
            }
            case SOUL_HOUND -> {
                w.playSound(at, Sound.ENTITY_PHANTOM_BITE, 1.6f, 0.5f);
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, mid, 36, 1.0, 0.6, 1.0, 0.05, null, true);
                w.spawnParticle(Particle.SOUL, mid, 10, 0.8, 0.5, 0.8, 0.02, null, true);
            }
            case CRYSTAL_MOTH -> {
                w.playSound(at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.6f, 0.6f);
                w.spawnParticle(Particle.END_ROD, mid, 36, 1.0, 0.6, 1.0, 0.08, null, true);
            }
            case UNLIT_SUN -> {
                w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.3f, 1.25f);
                w.spawnParticle(Particle.FLAME, mid, 30, 1.0, 0.6, 1.0, 0.05, null, true);
                w.spawnParticle(Particle.REVERSE_PORTAL, mid, 30, 1.0, 0.8, 1.0, 0.08, null, true);
            }
        }
    }

    /** Throws the camper back into the fight: away from the wall, or (Soul-Eater) straight at the hound. */
    private void launch(Player p, Location boss) {
        Location toward = kind == RealmBosses.Kind.SOUL_HOUND ? boss : center;
        Vector v = toward.toVector().subtract(p.getLocation().toVector()).setY(0);
        if (v.lengthSquared() < 1e-4) v = new Vector(0, 0, 1);
        double horizontal = switch (kind) {
            case CINDER_COLOSSUS -> 1.25;
            case SOUL_HOUND -> 1.55;
            case CRYSTAL_MOTH -> 1.1;
            case UNLIT_SUN -> 1.0;
        };
        double vertical = switch (kind) {
            case CINDER_COLOSSUS -> 0.55;
            case SOUL_HOUND -> 0.35;
            case CRYSTAL_MOTH -> 0.6;
            case UNLIT_SUN -> 0.3;
        };
        p.setVelocity(v.normalize().multiply(horizontal).setY(vertical));
    }
}
