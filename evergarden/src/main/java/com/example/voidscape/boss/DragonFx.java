package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.DragonNestLayout;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Dragon effects drawn on both clients from one call.
 * <p>
 * Java viewers get tinted dust plus the full vanilla mix. Bedrock viewers (Geyser) only get particles that Geyser
 * maps to a real Bedrock effect: tinted dust arrives untinted, sweep/flash turn into nothing, cloud and large smoke
 * become heavy emitters, and block particles replay as a block-break burst, so each Java-only particle has a
 * Bedrock stand-in of the same element (flame, blue flame, end rod, spark, snowflake). Solid props are falling
 * blocks, which both clients render; they never land, drop or place a block.
 */
final class DragonFx {
    private static final double RANGE = 128;
    private final VoidscapePlugin plugin;
    private final DragonNestLayout.Kind kind;
    private final NamespacedKey propKey;
    private record Viewer(Player player, double x, double y, double z) {}
    private List<Viewer> java = List.of(), bedrock = List.of();
    private World world;
    private int refreshed = Integer.MIN_VALUE;
    private final Particle.DustOptions mark, hot, deep;
    final Color color;

    DragonFx(VoidscapePlugin plugin, DragonNestLayout.Kind kind) {
        this.plugin = plugin;
        this.kind = kind;
        this.propKey = plugin.key("dragon_prop");
        this.color = switch (kind) {
            case FIRE -> Color.fromRGB(255, 92, 24);
            case STORM -> Color.fromRGB(255, 226, 92);
            case ICE -> Color.fromRGB(132, 220, 255);
        };
        Color bright = switch (kind) {
            case FIRE -> Color.fromRGB(255, 196, 80);
            case STORM -> Color.fromRGB(190, 236, 255);
            case ICE -> Color.fromRGB(236, 252, 255);
        };
        Color dark = switch (kind) {
            case FIRE -> Color.fromRGB(150, 18, 8);
            case STORM -> Color.fromRGB(112, 72, 255);
            case ICE -> Color.fromRGB(40, 110, 220);
        };
        this.mark = new Particle.DustOptions(color, 1.9f);
        this.hot = new Particle.DustOptions(bright, 2.4f);
        this.deep = new Particle.DustOptions(dark, 2.8f);
    }

    /** Splits the world's players into Java and Bedrock viewers once per server tick. */
    private void refresh(World w) {
        int now = Bukkit.getCurrentTick();
        if (now == refreshed && w == world) return;
        refreshed = now;
        world = w;
        List<Viewer> j = new ArrayList<>(), b = new ArrayList<>();
        for (Player p : w.getPlayers()) {
            Location at = p.getLocation();
            (JudgePackService.bedrock(p) ? b : j).add(new Viewer(p, at.getX(), at.getY(), at.getZ()));
        }
        java = j;
        bedrock = b;
    }

    // --- particle routing -----------------------------------------------------------------------------

    private <T> void send(boolean toJava, Particle particle, Location at, int count, double dx, double dy, double dz, double speed, T data) {
        if (count <= 0 || at.getWorld() == null) return;
        refresh(at.getWorld());
        for (Viewer v : toJava ? java : bedrock) {
            double ex = v.x - at.getX(), ey = v.y - at.getY(), ez = v.z - at.getZ();
            if (ex * ex + ey * ey + ez * ez <= RANGE * RANGE) v.player.spawnParticle(particle, at, count, dx, dy, dz, speed, data, true);
        }
    }

    /** A particle Geyser renders natively, sent to everyone. */
    void both(Particle particle, Location at, int count, double spread, double speed) {
        both(particle, at, count, spread, spread, spread, speed);
    }

    void both(Particle particle, Location at, int count, double dx, double dy, double dz, double speed) {
        send(true, particle, at, count, dx, dy, dz, speed, null);
        send(false, particle, at, count, dx, dy, dz, speed, null);
    }

    void javaOnly(Particle particle, Location at, int count, double spread, double speed) {
        send(true, particle, at, count, spread, spread, spread, speed, null);
    }

    /** Element core: flame / spark / snowflake (Bedrock-safe on every element). */
    Particle core() {
        return switch (kind) { case FIRE -> Particle.FLAME; case STORM -> Particle.ELECTRIC_SPARK; case ICE -> Particle.SNOWFLAKE; };
    }

    /** Element accent: lava / end rod / blue flame. */
    Particle accent() {
        return switch (kind) { case FIRE -> Particle.LAVA; case STORM -> Particle.END_ROD; case ICE -> Particle.SOUL_FIRE_FLAME; };
    }

    /** The Bedrock stand-in for tinted dust: same hue family, a native Bedrock particle. */
    private Particle markStandIn() {
        return switch (kind) { case FIRE -> Particle.FLAME; case STORM -> Particle.END_ROD; case ICE -> Particle.SOUL_FIRE_FLAME; };
    }

    /** One telegraph point: crisp tinted dust on Java, a still element particle on Bedrock. */
    void mark(Location at, boolean strong) {
        send(true, Particle.DUST, at, 1, 0, 0, 0, 0, strong ? hot : mark);
        send(false, markStandIn(), at, 1, 0, 0, 0, 0, null);
    }

    void dust(Location at, int count, double spread, boolean dark) {
        send(true, Particle.DUST, at, count, spread, spread, spread, 0, dark ? deep : hot);
        send(false, core(), at, Math.max(1, count / 2), spread, spread, spread, 0.01, null);
    }

    void core(Location at, int count, double spread, double speed) {
        both(core(), at, count, spread, speed);
    }

    void accent(Location at, int count, double spread, double speed) {
        both(accent(), at, count, spread, speed);
    }

    /** Element smoke: Java's large smoke is a heavy emitter on Bedrock, which gets plain smoke instead. */
    void smoke(Location at, int count, double spread) {
        Particle javaSmoke = kind == DragonNestLayout.Kind.ICE ? Particle.WHITE_SMOKE : Particle.LARGE_SMOKE;
        send(true, javaSmoke, at, count, spread, spread, spread, 0.02, null);
        send(false, kind == DragonNestLayout.Kind.ICE ? Particle.WHITE_SMOKE : Particle.SMOKE, at, Math.max(1, count / 2), spread, spread, spread, 0.02, null);
    }

    /** A burst of the element: core, accent, dust and a little smoke; scale 1 is about a 2-block puff. */
    void burst(Location at, double scale) {
        int n = (int) Math.round(14 * scale);
        core(at, n, 0.55 * scale, 0.08 * scale);
        accent(at, Math.max(2, n / 3), 0.5 * scale, 0.05);
        dust(at, Math.max(2, n / 2), 0.6 * scale, false);
        if (kind == DragonNestLayout.Kind.ICE) both(Particle.ITEM_SNOWBALL, at, Math.max(2, n / 2), 0.6 * scale, 0.12);
        if (kind == DragonNestLayout.Kind.STORM) both(Particle.END_ROD, at, Math.max(2, n / 3), 0.4 * scale, 0.18);
        if (scale >= 1.5) smoke(at, Math.max(2, n / 4), 0.5 * scale);
    }

    /** A heavy impact: a large explosion puff, crits and the element burst. */
    void impact(Location at, double scale, boolean huge) {
        both(huge ? Particle.EXPLOSION_EMITTER : Particle.EXPLOSION, at, huge ? 1 : Math.max(1, (int) Math.round(scale)), 0.6 * scale, 0);
        both(Particle.CRIT, at, (int) Math.round(18 * scale), 0.9 * scale, 0.5);
        burst(at, scale * 1.4);
    }

    // --- shapes ---------------------------------------------------------------------------------------

    void ring(Location c, double radius, double spacing, double phase, boolean strong) {
        int points = Math.max(10, (int) Math.round(2 * Math.PI * radius / spacing));
        for (int i = 0; i < points; i++) {
            double a = phase + i * 2 * Math.PI / points;
            mark(c.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius), strong);
        }
    }

    void line(Location a, Location b, double spacing, boolean strong) {
        Vector d = b.toVector().subtract(a.toVector());
        double length = d.length();
        if (length < 1e-6) { mark(a, strong); return; }
        d.multiply(1 / length);
        for (double s = 0; s <= length; s += spacing) mark(a.clone().add(d.clone().multiply(s)), strong);
    }

    /** A spinning rune: outer and inner ring with a five-point star between them. */
    void rune(Location c, double radius, double angle) {
        ring(c, radius, 0.9, angle, true);
        ring(c, radius * 0.62, 1.1, -angle * 1.6, false);
        Location[] star = new Location[5];
        for (int i = 0; i < 5; i++) {
            double a = angle + i * 2 * Math.PI / 5;
            star[i] = c.clone().add(Math.cos(a) * radius * 0.95, 0, Math.sin(a) * radius * 0.95);
        }
        for (int i = 0; i < 5; i++) line(star[i], star[(i + 2) % 5], 1.1, false);
    }

    /** A telegraph fan on the ground: rim arc and spokes between yaw bounds, inner to outer radius. */
    void fan(Location origin, double yawFrom, double yawTo, double inner, double outer) {
        double span = yawTo - yawFrom;
        int rim = Math.max(8, (int) Math.round(Math.abs(span) * outer / 1.2));
        for (int i = 0; i <= rim; i++) {
            double a = yawFrom + span * i / rim;
            mark(origin.clone().add(-Math.sin(a) * outer, 0, Math.cos(a) * outer), true);
        }
        for (double a : new double[]{yawFrom, yawTo}) {
            Vector dir = new Vector(-Math.sin(a), 0, Math.cos(a));
            line(origin.clone().add(dir.clone().multiply(inner)), origin.clone().add(dir.clone().multiply(outer)), 1.4, false);
        }
    }

    /** A rising column of the element. */
    void pillar(Location base, double height, int perBlock) {
        for (double y = 0; y < height; y += 1.0) {
            Location at = base.clone().add(0, y, 0);
            core(at, perBlock, 0.35, 0.02);
            if (((int) y) % 3 == 0) accent(at, 1, 0.3, 0.02);
        }
    }

    /** Particle stream from the mouth to an end point, widening with distance. */
    void stream(Location from, Location to, double width) {
        Vector d = to.toVector().subtract(from.toVector());
        double length = d.length();
        if (length < 1e-6) return;
        d.multiply(1 / length);
        for (double s = 0.5; s <= length; s += 1.25) {
            double spread = 0.25 + width * s / length;
            Location at = from.clone().add(d.clone().multiply(s));
            core(at, 3, spread, 0.03);
            if (s % 3.75 < 1.25) accent(at, 1, spread * 0.8, 0.02);
            if (kind == DragonNestLayout.Kind.ICE) both(Particle.ITEM_SNOWBALL, at, 1, spread, 0.05);
            if (kind == DragonNestLayout.Kind.FIRE && s % 5 < 1.25) smoke(at, 1, spread);
        }
    }

    // --- props ----------------------------------------------------------------------------------------

    /** A block both clients draw that never falls, lands, drops or places itself. */
    FallingBlock prop(Location at, Material material) {
        BlockData data = material.createBlockData();
        FallingBlock block = at.getWorld().spawnFallingBlock(at, data);
        block.setGravity(false);
        block.setDropItem(false);
        block.setCancelDrop(true);
        block.setHurtEntities(false);
        block.setInvulnerable(true);
        block.setPersistent(false);
        block.setSilent(true);
        block.getPersistentDataContainer().set(plugin.key("world_boss_entity"), PersistentDataType.BYTE, (byte) 1);
        block.getPersistentDataContainer().set(propKey, PersistentDataType.BYTE, (byte) 1);
        return block;
    }

    boolean isProp(org.bukkit.entity.Entity e) {
        return e instanceof FallingBlock && e.getPersistentDataContainer().has(propKey, PersistentDataType.BYTE);
    }

    /** Moves a prop and keeps the falling-block tick counter low so it never times out into an item. */
    static void place(FallingBlock block, Location at) {
        if (block == null || !block.isValid()) return;
        block.teleport(at);
        block.setVelocity(new Vector());
        block.setTicksLived(1);
    }
}
