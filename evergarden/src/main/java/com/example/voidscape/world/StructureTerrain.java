package com.example.voidscape.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Fits new biome terrain beneath existing, fixed-height blueprints. Sampling only
 * uses deterministic layout candidates and immutable geometry; it never loads a
 * world chunk or changes a structure's placement, arrival or treasure position.
 */
public final class StructureTerrain {
    private static final int MAX_RADIUS = 120, MAX_APRON = 160, CELL_CACHE_LIMIT = 192;
    private static final short EMPTY = Short.MIN_VALUE;
    // The keys are the ten shared whale/landmark/temple/boss blueprint identities,
    // rather than sites or world coordinates. Build only when a site is sampled.
    private static final Map<Object, Underside> UNDERSIDES = new IdentityHashMap<>();

    /** Lowest non-air blueprint block in each horizontal column. */
    public static final class Underside {
        private final int radiusX, radiusZ, width;
        private final short[] lowest;

        private Underside(int radiusX, int radiusZ) {
            this.radiusX = radiusX;
            this.radiusZ = radiusZ;
            width = radiusZ * 2 + 1;
            lowest = new short[(radiusX * 2 + 1) * width];
            Arrays.fill(lowest, EMPTY);
        }

        private void include(int x, int y, int z) {
            int index = (x + radiusX) * width + z + radiusZ;
            if (lowest[index] == EMPTY || y < lowest[index]) lowest[index] = (short) y;
        }

        /** Integer.MAX_VALUE means this is an open blueprint column. */
        public int lowest(int x, int z) {
            if (Math.abs((long) x) > radiusX || Math.abs((long) z) > radiusZ) return Integer.MAX_VALUE;
            int value = lowest[(x + radiusX) * width + z + radiusZ];
            return value == EMPTY ? Integer.MAX_VALUE : value;
        }
    }

    public record Anchor(int x, int z, int radiusX, int radiusZ, boolean floating,
                         int floorY, String id, int apron, int clearance, Underside underside) {
        private boolean reservation(int dx, int dz, int margin) {
            // The whale's elliptical reservation can have outlying branch blocks;
            // a bounding box also protects those from neighboring tree canopies.
            if (id.equals("sky-whale"))
                return Math.abs((long) dx) <= radiusX + margin && Math.abs((long) dz) <= radiusZ + margin;
            if (id.equals("ancient-temple"))
                return Math.abs((long) dx) <= radiusX + margin
                        && Math.abs((long) dz) <= radiusZ + margin
                        && Math.abs((long) dx) + Math.abs((long) dz) <= 170 + 2L * margin;
            return Math.abs((long) dx) <= radiusX + margin && Math.abs((long) dz) <= radiusZ + margin;
        }

        /** Rounded signed distance keeps the terrain apron free of square cuts. */
        private double distance(int dx, int dz) {
            if (id.equals("sky-whale")) {
                double radial = Math.sqrt(square(dx / (double) radiusX) + square(dz / (double) radiusZ));
                return radial == 0 ? -Math.min(radiusX, radiusZ) : Math.hypot(dx, dz) * (1 - 1 / radial);
            }
            if (id.equals("ancient-temple"))
                return Math.max(Math.max(Math.abs((long) dx) - radiusX, Math.abs((long) dz) - radiusZ),
                        (Math.abs((long) dx) + Math.abs((long) dz) - 170) / Math.sqrt(2));
            int corner = Math.min(28, Math.min(radiusX, radiusZ) / 3);
            double qx = Math.abs((long) dx) - (radiusX - corner);
            double qz = Math.abs((long) dz) - (radiusZ - corner);
            return Math.hypot(Math.max(qx, 0), Math.max(qz, 0)) + Math.min(Math.max(qx, qz), 0) - corner;
        }
    }

    public record Shape(int top, int depth, boolean influenced, boolean footprint) {}

    private final long seed;
    private final SkyWhaleLayout whales;
    private final SkyLandmarkLayout landmarks;
    private final WorldBossTempleLayout temples;
    private final BossStructureLayout bosses;
    private final boolean whaleEnabled, observatoryEnabled, gardenEnabled, templeEnabled;
    private final CellCache whaleCells = new CellCache(), landmarkCells = new CellCache();
    private final CellCache templeCells = new CellCache(), bossCells = new CellCache();

    public StructureTerrain(long seed, SkyWhaleLayout whales, boolean whaleEnabled,
                            SkyLandmarkLayout landmarks, boolean observatoryEnabled, boolean gardenEnabled,
                            WorldBossTempleLayout temples, boolean templeEnabled, BossStructureLayout bosses) {
        this.seed = seed;
        this.whales = whales;
        this.whaleEnabled = whaleEnabled;
        this.landmarks = landmarks;
        this.observatoryEnabled = observatoryEnabled;
        this.gardenEnabled = gardenEnabled;
        this.temples = temples;
        this.templeEnabled = templeEnabled;
        this.bosses = bosses;
    }

    /** Includes neighbor cells whose structure or smooth apron can reach this point. */
    public List<Anchor> nearby(int x, int z, int extra) {
        int reach = MAX_RADIUS + MAX_APRON + Math.max(0, extra);
        List<Anchor> result = new ArrayList<>();
        if (whaleEnabled && whales != null)
            collect(result, x, z, reach, whales.spacingChunks() * 16, whaleCells, (gx, gz) -> {
                var site = whales.cell(gx, gz);
                if (site == null) return List.of();
                var map = cached(SkyWhale.blocks(), () -> whaleUnderside());
                return List.of(anchor(site.x(), site.z(), 115, 65, 100, "sky-whale", true, map));
            });
        if ((observatoryEnabled || gardenEnabled) && landmarks != null)
            collect(result, x, z, reach, landmarks.spacingChunks() * 16, landmarkCells, (gx, gz) -> {
                List<Anchor> values = new ArrayList<>();
                for (var site : landmarks.cell(gx, gz)) {
                    boolean observatory = site.kind() == SkyLandmarkLayout.Kind.OBSERVATORY;
                    if (observatory ? !observatoryEnabled : !gardenEnabled) continue;
                    var blueprint = site.kind().blueprint();
                    var map = cached(blueprint, () -> landmarkUnderside(blueprint));
                    int rx = Math.max(site.kind().radius, map.radiusX), rz = Math.max(site.kind().radius, map.radiusZ);
                    values.add(anchor(site.x(), site.z(), rx, rz, observatory ? 120 : 76,
                            site.kind().id, floating(site.x(), site.z(), site.kind().id, observatory ? .6 : .82), map));
                }
                return List.copyOf(values);
            });
        if (templeEnabled && temples != null)
            collect(result, x, z, reach, temples.spacingChunks() * 16, templeCells, (gx, gz) -> {
                var site = temples.cell(gx, gz);
                if (site == null) return List.of();
                var blueprint = WorldBossTemple.blueprint();
                var map = cached(blueprint, () -> templeUnderside(blueprint));
                return List.of(anchor(site.x(), site.z(), WorldBossTemple.RADIUS, WorldBossTemple.RADIUS,
                        WorldBossTemple.FLOOR_Y, "ancient-temple", floating(site.x(), site.z(), "ancient-temple", .6), map));
            });
        if (bosses != null)
            collect(result, x, z, reach, bosses.spacingChunks() * 16, bossCells, (gx, gz) -> {
                List<Anchor> values = new ArrayList<>();
                for (var site : bosses.cell(gx, gz)) {
                    if (!bosses.enabled(site.kind())) continue;
                    var blueprint = site.blueprint();
                    var map = cached(blueprint, () -> bossUnderside(blueprint, site.kind().radius));
                    values.add(anchor(site.x(), site.z(), site.kind().radius, site.kind().radius,
                            site.kind().floorY, site.kind().id, floating(site.x(), site.z(), site.kind().id, .6), map));
                }
                return List.copyOf(values);
            });
        return List.copyOf(result);
    }

    /**
     * Floating sites lower terrain below their actual underside; attached sites
     * join beneath the first non-air block without rising above their floor.
     * Callers use footprint to retain land even where natural island noise is void.
     */
    public Shape shape(int x, int z, int naturalTop, int naturalDepth, List<Anchor> anchors) {
        int attachedTop = naturalTop, floatingCap = Integer.MAX_VALUE, protectedCap = Integer.MAX_VALUE;
        double strongestAttachment = 0;
        boolean influenced = false, footprint = false;
        for (var anchor : anchors) {
            int dx = x - anchor.x, dz = z - anchor.z;
            if (Math.abs((long) dx) > anchor.radiusX + anchor.apron
                    || Math.abs((long) dz) > anchor.radiusZ + anchor.apron) continue;
            int lowest = anchor.underside.lowest(dx, dz);
            double distance = anchor.distance(dx, dz);
            // Exact geometry wins over rounded corners, including outlying foliage.
            if (lowest != Integer.MAX_VALUE) distance = Math.min(distance, 0);
            if (distance >= anchor.apron) continue;
            double weight = 1 - smooth(Math.max(0, distance) / anchor.apron);
            boolean inside = distance <= 0;
            influenced = true;
            footprint |= inside;
            if (anchor.floating) {
                int cap = inside && lowest != Integer.MAX_VALUE
                        ? Math.min(lowest - anchor.clearance, anchor.floorY - 45) : anchor.floorY - 45;
                floatingCap = Math.min(floatingCap, (int) Math.round(naturalTop + (cap - naturalTop) * weight));
            } else {
                int target = inside ? lowest != Integer.MAX_VALUE
                        ? Math.min(lowest - 1, anchor.floorY - 1) : anchor.floorY - 32 : anchor.floorY - 1;
                if (weight > strongestAttachment) {
                    strongestAttachment = weight;
                    attachedTop = (int) Math.round(naturalTop + (target - naturalTop) * weight);
                }
            }
            if (inside) protectedCap = Math.min(protectedCap,
                    lowest == Integer.MAX_VALUE ? anchor.floorY - (anchor.floating ? 45 : 32)
                            : lowest - (anchor.floating ? anchor.clearance : 1));
        }
        int top = Math.min(attachedTop, Math.min(floatingCap, protectedCap));
        int depth = influenced ? Math.clamp(naturalDepth + Math.max(0, top - naturalTop), 28, 170) : naturalDepth;
        return new Shape(top, depth, influenced, footprint);
    }

    /** Full blueprint reservations prevent decoration canopies crossing a building. */
    public boolean reserved(int x, int z, int margin, List<Anchor> anchors) {
        for (var anchor : anchors)
            if (anchor.reservation(x - anchor.x, z - anchor.z, Math.max(0, margin))) return true;
        return false;
    }

    private Anchor anchor(int x, int z, int rx, int rz, int floor, String id, boolean floating, Underside map) {
        long hash = siteHash(x, z, id);
        return new Anchor(x, z, rx, rz, floating, floor, id,
                112 + Math.floorMod((int) (hash >>> 16), 49), 22 + Math.floorMod((int) (hash >>> 32), 11), map);
    }

    private boolean floating(int x, int z, String id, double chance) {
        return (siteHash(x, z, id) & 65535L) / 65536.0 < chance;
    }

    private long siteHash(int x, int z, String id) {
        return DungeonLayout.mix(seed ^ ((long) x * 341873128712L) ^ ((long) z * 132897987541L) ^ id.hashCode());
    }

    private interface CellFactory { List<Anchor> build(int gx, int gz); }

    private static final class CellCache {
        private final Map<Long, List<Anchor>> cells = new LinkedHashMap<>(32, .75f, true);
        synchronized List<Anchor> get(int gx, int gz, Supplier<List<Anchor>> factory) {
            long key = ((long) gx << 32) | (gz & 0xffffffffL);
            List<Anchor> found = cells.get(key);
            if (found != null) return found;
            List<Anchor> value = factory.get();
            if (cells.size() >= CELL_CACHE_LIMIT) cells.remove(cells.keySet().iterator().next());
            cells.put(key, value);
            return value;
        }
    }

    private static void collect(List<Anchor> target, int x, int z, int reach, int cellSize,
                                CellCache cache, CellFactory factory) {
        for (int gx = Math.floorDiv(x - reach, cellSize); gx <= Math.floorDiv(x + reach, cellSize); gx++)
            for (int gz = Math.floorDiv(z - reach, cellSize); gz <= Math.floorDiv(z + reach, cellSize); gz++) {
                final int cellX = gx, cellZ = gz;
                for (var anchor : cache.get(gx, gz, () -> factory.build(cellX, cellZ)))
                    if (Math.abs((long) anchor.x - x) <= anchor.radiusX + anchor.apron + reach - MAX_RADIUS - MAX_APRON
                            && Math.abs((long) anchor.z - z) <= anchor.radiusZ + anchor.apron + reach - MAX_RADIUS - MAX_APRON)
                        target.add(anchor);
            }
    }

    private static Underside cached(Object blueprint, Supplier<Underside> factory) {
        synchronized (UNDERSIDES) {
            return UNDERSIDES.computeIfAbsent(blueprint, ignored -> factory.get());
        }
    }

    private static Underside whaleUnderside() {
        var map = new Underside(115, 65);
        for (var block : SkyWhale.blocks())
            if (!block.material().isAir()) map.include(block.x(), block.y(), block.z());
        return map;
    }

    private static Underside landmarkUnderside(LandmarkBlueprint blueprint) {
        int rx = 0, rz = 0;
        for (var block : blueprint.blocks()) {
            rx = Math.max(rx, Math.abs(block.x()));
            rz = Math.max(rz, Math.abs(block.z()));
        }
        var map = new Underside(rx, rz);
        for (var block : blueprint.blocks())
            if (!block.material().isAir()) map.include(block.x(), block.y(), block.z());
        return map;
    }

    private static Underside templeUnderside(WorldBossTemple blueprint) {
        var map = new Underside(WorldBossTemple.RADIUS, WorldBossTemple.RADIUS);
        for (var box : blueprint.boxes()) {
            if (box.material().isAir()) continue;
            for (int x = box.x1(); x <= box.x2(); x++)
                for (int z = box.z1(); z <= box.z2(); z++) map.include(x, box.y1(), z);
        }
        return map;
    }

    private static Underside bossUnderside(BossStructure blueprint, int radius) {
        var map = new Underside(radius, radius);
        for (var box : blueprint.boxes()) {
            // AIR is an ordered carving operation, never an underside block. A
            // conservative old solid beneath a cut remains safe to build below.
            if (box.material().isAir()) continue;
            for (int x = box.x1(); x <= box.x2(); x++)
                for (int z = box.z1(); z <= box.z2(); z++) map.include(x, box.y1(), z);
        }
        return map;
    }

    private static double square(double value) { return value * value; }
    private static double smooth(double value) {
        value = Math.clamp(value, 0, 1);
        return value * value * (3 - 2 * value);
    }
}
