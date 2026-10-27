package com.example.advancemagic.spell;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** One captured world-space layout shared by authored art and real hit volumes. */
public final class LegendaryCastGeometry {
    public static final double VERDANT_LENGTH = 36;
    public static final double VERDANT_DIAMETER = 4;
    public static final double VERDANT_FINAL_DIAMETER = 5.2;
    public static final int LUNAR_GRID = 4;
    public static final int LUNAR_COUNT = LUNAR_GRID * LUNAR_GRID;
    public static final double LUNAR_COLUMN_RADIUS = .55;
    public static final double LUNAR_TILT_DEGREES = 50;
    public static final int CRIMSON_START = 96, CRIMSON_FLIGHT = 8, CRIMSON_COUNT = 8;
    public static int crimsonArrival(int index) { return CRIMSON_START + (index + 1) * CRIMSON_FLIGHT; }
    public static boolean crimsonImpact(int age) {
        return age >= crimsonArrival(0) && age <= crimsonArrival(CRIMSON_COUNT - 1)
                && (age - CRIMSON_START) % CRIMSON_FLIGHT == 0;
    }
    private LegendaryCastGeometry() {}

    public sealed interface Scene permits Verdant, Lunar {
        Location base();
        Vector forward();
        Vector right();
        double scale();
        void refresh(MagicContext context);
    }

    public static Vector capturedForward(Player player) {
        Location facing = player.getLocation();
        facing.setPitch(0);
        Vector forward = facing.getDirection().setY(0);
        if (forward.lengthSquared() < .001) forward = new Vector(0, 0, 1);
        return forward.normalize();
    }

    public static final class Verdant implements Scene {
        private final Location base, source, sigil;
        private final Vector forward, right;
        private final double scale;
        private Beam beam, finalBeam;
        public Verdant(MagicContext context, Location base, Vector forward, double scale) {
            this.base = base.clone();
            this.forward = forward.clone().normalize();
            this.right = new Vector(forward.getZ(), 0, -forward.getX());
            this.scale = scale;
            // Keep the caster's camera outside the light volume, including the
            // large finale. The discharge starts clearly beyond the wand tip.
            source = base.clone().add(forward.clone().multiply(4.5)).add(0, 2.8 * scale, 0);
            sigil = base.clone().subtract(forward.clone().multiply(7.5)).add(0, 12 * scale, 0);
            refresh(context);
        }
        @Override public Location base() { return base.clone(); }
        @Override public Vector forward() { return forward.clone(); }
        @Override public Vector right() { return right.clone(); }
        @Override public double scale() { return scale; }
        public Location source() { return source.clone(); }
        public Location sigil() { return sigil.clone(); }
        public Beam beam() { return beam; }
        public Beam finalBeam() { return finalBeam; }
        @Override public void refresh(MagicContext context) {
            beam = clipped(context, source, forward, VERDANT_LENGTH, VERDANT_DIAMETER * scale / 2);
            finalBeam = beam == null ? null : new Beam(beam.from(), beam.to(), VERDANT_FINAL_DIAMETER * scale / 2);
        }
    }

    public static final class Lunar implements Scene {
        private final Location base;
        private final Vector forward, right;
        private final double scale, spacing, ringDiameter;
        private final List<Location> rings = new ArrayList<>(LUNAR_COUNT);
        private final List<Beam> columns = new ArrayList<>(LUNAR_COUNT);
        private final List<Vector> planeRights = new ArrayList<>(LUNAR_COUNT), planeUps = new ArrayList<>(LUNAR_COUNT);
        public Lunar(MagicContext context, Location base, Vector forward, double scale, double radius, Location viewer) {
            this.base = base.clone();
            this.forward = forward.clone().normalize();
            this.right = new Vector(forward.getZ(), 0, -forward.getX());
            this.scale = scale;
            ringDiameter = 10 * scale;
            spacing = Math.max(radius * .9, ringDiameter / .8);
            // Capture one face orientation per portal toward the casting eye.
            // A shared tilt made distant cells almost edge-on at small scales.
            // This orientation remains fixed even when the caster later moves.
            for (int row = 0; row < LUNAR_GRID; row++) for (int column = 0; column < LUNAR_GRID; column++) {
                // Keep one inner portal exactly on the aimed target; an even
                // grid centred between cells would leave the crosshair in a gap.
                double half = 1;
                Location ring = base.clone().add(right.clone().multiply((column - half) * spacing))
                        .add(forward.clone().multiply((row - half) * spacing)).add(0, 24 * scale, 0);
                rings.add(ring);
                Vector normal = viewer.toVector().subtract(ring.toVector()).normalize();
                Vector faceRight = new Vector(0, 1, 0).crossProduct(normal);
                if (faceRight.lengthSquared() < .001) faceRight = right.clone();
                faceRight.normalize();
                planeRights.add(faceRight);
                planeUps.add(normal.clone().crossProduct(faceRight).normalize());
                columns.add(null);
            }
            refresh(context);
        }
        @Override public Location base() { return base.clone(); }
        @Override public Vector forward() { return forward.clone(); }
        @Override public Vector right() { return right.clone(); }
        @Override public double scale() { return scale; }
        public Vector planeRight(int index) { return planeRights.get(index).clone(); }
        public Vector planeUp(int index) { return planeUps.get(index).clone(); }
        public double spacing() { return spacing; }
        public double ringDiameter() { return ringDiameter; }
        public List<Location> rings() { return rings.stream().map(Location::clone).toList(); }
        public List<Beam> columns() { return java.util.Collections.unmodifiableList(columns); }
        public boolean complete() { return columns.stream().allMatch(beam -> beam != null && beam.length() > .5); }
        @Override public void refresh(MagicContext context) {
            for (int i = 0; i < rings.size(); i++) {
                Location ring = rings.get(i);
                double distance = ring.getY() - ring.getWorld().getMinHeight();
                columns.set(i, clipped(context, ring, new Vector(0, -1, 0), distance, LUNAR_COLUMN_RADIUS * scale, true));
            }
        }
        public static int phase(int column) { return (column % LUNAR_GRID) * 4; }
        public static boolean openingPulse(int age, int column) {
            int time = age - phase(column);
            return time >= 24 && time <= 84 && (time - 24) % 20 == 0;
        }
        /** One eight-tick light discharge accompanies each real scheduled hit. */
        public static int shotAge(int age, int column) {
            int phase = phase(column);
            for (int start : new int[]{24 + phase, 44 + phase, 64 + phase, 84 + phase, 112, 124, 136}) {
                if (age >= start && age < start + 8) return age - start;
            }
            return -1;
        }
    }

    /** Finite capsule against the actual living body's AABB, clipped at each end. */
    public static final class Beam {
        private final Location from, to;
        private final Vector direction;
        private final double radius, length;
        public Beam(Location from, Location to, double radius) {
            this.from = from.clone();
            this.to = to.clone();
            this.radius = radius;
            Vector delta = to.toVector().subtract(from.toVector());
            length = delta.length();
            direction = length < .001 ? new Vector() : delta.multiply(1 / length);
        }
        public Location from() { return from.clone(); }
        public Location to() { return to.clone(); }
        public double radius() { return radius; }
        public double width() { return radius * 2; }
        public double length() { return length; }
        public BoundingBox bounds() {
            return new BoundingBox(Math.min(from.getX(), to.getX()) - radius, Math.min(from.getY(), to.getY()) - radius,
                    Math.min(from.getZ(), to.getZ()) - radius, Math.max(from.getX(), to.getX()) + radius,
                    Math.max(from.getY(), to.getY()) + radius, Math.max(from.getZ(), to.getZ()) + radius);
        }
        public boolean contains(BoundingBox body) {
            if (length < .05) return false;
            // A hemisphere behind the muzzle or through the collision endpoint
            // must never create rear/through-wall hits from a large beam radius.
            double centerProjection = new Vector(body.getCenterX() - from.getX(), body.getCenterY() - from.getY(),
                    body.getCenterZ() - from.getZ()).dot(direction);
            double halfProjection = Math.abs(direction.getX()) * body.getWidthX() / 2
                    + Math.abs(direction.getY()) * body.getHeight() / 2
                    + Math.abs(direction.getZ()) * body.getWidthZ() / 2;
            if (centerProjection + halfProjection < 0 || centerProjection - halfProjection > length + .0001) return false;
            return segmentBoxDistanceSquared(from.toVector(), to.toVector(), body) <= radius * radius + .0001;
        }
    }

    private static Beam clipped(MagicContext context, Location from, Vector direction, double desired, double radius) {
        return clipped(context, from, direction, desired, radius, false);
    }

    private static Beam clipped(MagicContext context, Location from, Vector direction, double desired, double radius,
                                boolean requireGround) {
        if (!context.loaded(from) || !Double.isFinite(desired) || desired <= 0) return null;
        double loaded = 0;
        if (Math.abs(direction.getX()) < 1e-9 && Math.abs(direction.getZ()) < 1e-9) {
            // A vertical column never leaves its already loaded X/Z chunk. A
            // world-height clamp replaces hundreds of repeated loaded checks.
            loaded = Math.min(desired, direction.getY() < 0 ? from.getY() - from.getWorld().getMinHeight()
                    : from.getWorld().getMaxHeight() - .01 - from.getY());
        } else {
            for (double distance = .5; distance <= desired; distance += .5) {
                if (!context.loaded(from.clone().add(direction.clone().multiply(distance)))) break;
                loaded = distance;
            }
        }
        if (loaded < .25) return null;
        var collision = from.getWorld().rayTraceBlocks(from, direction, loaded, FluidCollisionMode.NEVER, true);
        if (requireGround && collision == null) return null;
        Location to = collision == null ? from.clone().add(direction.clone().multiply(loaded))
                : collision.getHitPosition().toLocation(from.getWorld()).subtract(direction.clone().multiply(.015));
        return from.distanceSquared(to) < .01 ? null : new Beam(from, to, radius);
    }

    /** Exact convex distance: split at the segment's crossings of the six box faces. */
    private static double segmentBoxDistanceSquared(Vector from, Vector to, BoundingBox box) {
        double[] origin = {from.getX(), from.getY(), from.getZ()};
        double[] delta = {to.getX() - from.getX(), to.getY() - from.getY(), to.getZ() - from.getZ()};
        double[] low = {box.getMinX(), box.getMinY(), box.getMinZ()}, high = {box.getMaxX(), box.getMaxY(), box.getMaxZ()};
        List<Double> splits = new ArrayList<>(8);
        splits.add(0.0); splits.add(1.0);
        for (int axis = 0; axis < 3; axis++) if (Math.abs(delta[axis]) > 1e-9) {
            for (double boundary : new double[]{low[axis], high[axis]}) {
                double time = (boundary - origin[axis]) / delta[axis];
                if (time > 0 && time < 1) splits.add(time);
            }
        }
        splits.sort(Comparator.naturalOrder());
        double best = Double.POSITIVE_INFINITY;
        for (int interval = 0; interval < splits.size() - 1; interval++) {
            double start = splits.get(interval), end = splits.get(interval + 1), mid = (start + end) * .5;
            double quadratic = 0, linear = 0;
            for (int axis = 0; axis < 3; axis++) {
                double position = origin[axis] + delta[axis] * mid;
                if (position >= low[axis] && position <= high[axis]) continue;
                double offset = origin[axis] - (position < low[axis] ? low[axis] : high[axis]);
                quadratic += delta[axis] * delta[axis];
                linear += delta[axis] * offset;
            }
            double time = quadratic < 1e-12 ? mid : Math.clamp(-linear / quadratic, start, end);
            for (double t : new double[]{start, time, end}) {
                double squared = 0;
                for (int axis = 0; axis < 3; axis++) {
                    double position = origin[axis] + delta[axis] * t;
                    double outside = position < low[axis] ? low[axis] - position : Math.max(0, position - high[axis]);
                    squared += outside * outside;
                }
                best = Math.min(best, squared);
            }
        }
        return best;
    }
}
