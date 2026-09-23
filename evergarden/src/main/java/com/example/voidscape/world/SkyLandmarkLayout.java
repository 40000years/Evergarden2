package com.example.voidscape.world;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Independent sparse placement for each landmark kind, mirroring SkyWhaleLayout exactly.
 * Each kind rolls its own chance per grid cell with its own seed salt –
 * they have zero dependency on each other and zero dependency on whale presence.
 */
public final class SkyLandmarkLayout {
    public enum Kind {
        OBSERVATORY("observatory", 64, 129, -30, 14, 101, 58),
        HANGING_GARDEN("hanging-garden", 48, 162, -5, 0, 77, 22);
        public final String id;
        public final int radius, chestY, chestZ, arrivalX, arrivalY, arrivalZ;
        Kind(String id, int radius, int chestY, int chestZ, int x, int y, int z) {
            this.id = id; this.radius = radius; this.chestY = chestY; this.chestZ = chestZ;
            arrivalX = x; arrivalY = y; arrivalZ = z;
        }
        public LandmarkBlueprint blueprint() {
            return this == OBSERVATORY ? SkyObservatory.blueprint() : HangingGarden.blueprint();
        }
    }

    public record Site(Kind kind, int x, int z) {
        public boolean contains(int bx, int bz, int margin) {
            return Math.abs(bx - x) <= kind.radius + margin && Math.abs(bz - z) <= kind.radius + margin;
        }
    }

    // Distinct seed salts – each kind is seeded independently, same principle as SkyWhaleLayout
    private static final long SALT_OBSERVATORY = 0x4f42534552564154L;
    private static final long SALT_GARDEN      = 0x484e47475244454eL;

    private final long seed;
    private final DungeonLayout temples;
    private final SkyWhaleLayout whales;
    private final int spacing;
    private final double chance;
    private final double unexploredChance;
    private final Set<Long> legacyCells;
    private final Map<Long, List<Site>> cache = new ConcurrentHashMap<>();

    /** Default: same grid spacing and chance as the whale layout. */
    public SkyLandmarkLayout(long seed, DungeonLayout temples, SkyWhaleLayout whales) {
        this(seed, temples, whales, whales.spacingChunks(), whales.chance(), whales.unexploredChance(), whales.legacyCells());
    }

    public SkyLandmarkLayout(long seed, DungeonLayout temples, SkyWhaleLayout whales,
                             int spacingChunks, double chance) {
        this(seed, temples, whales, spacingChunks, chance, chance, Set.of());
    }

    public SkyLandmarkLayout(long seed, DungeonLayout temples, SkyWhaleLayout whales,
                             int spacingChunks, double chance, double unexploredChance, Set<Long> legacyCells) {
        this.seed    = seed;
        this.temples = temples;
        this.whales  = whales;
        this.spacing = Math.max(32, Math.min(64, spacingChunks));
        this.chance  = Double.isFinite(chance) ? Math.clamp(chance, 0, 1) : 0.12;
        this.unexploredChance = Double.isFinite(unexploredChance) ? Math.clamp(unexploredChance, 0, 1) : 0;
        this.legacyCells = Set.copyOf(legacyCells);
    }

    public int spacingChunks() { return spacing; }
    public double chance() { return chance; }

    private static long key(int x, int z) { return ((long) x << 32) | (z & 0xffffffffL); }

    public List<Site> cell(int gx, int gz) {
        return cache.computeIfAbsent(key(gx, gz), ignored -> computeCell(gx, gz));
    }

    private List<Site> computeCell(int gx, int gz) {
        List<Site> result = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            Site site = candidate(kind, gx, gz);
            if (site == null) continue;
            // If two kinds happen to land too close inside the same cell, skip the later one.
            boolean overlaps = false;
            for (Site placed : result) {
                int minSep = site.kind().radius + placed.kind().radius + 16;
                if (Math.abs(site.x() - placed.x()) < minSep
                        && Math.abs(site.z() - placed.z()) < minSep) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) result.add(site);
        }
        return List.copyOf(result);
    }

    /**
     * Identical algorithm to SkyWhaleLayout.candidate() but per-Kind.
     * Each kind rolls its own random check and picks its own offset within the cell.
     */
    private Site candidate(Kind kind, int gx, int gz) {
        long salt  = kind == Kind.OBSERVATORY ? SALT_OBSERVATORY : SALT_GARDEN;
        long mixed = DungeonLayout.mix(seed ^ salt
                ^ ((long) gx * 341873128712L) ^ ((long) gz * 132897987541L));
        Random random = new Random(mixed);

        // Independent rarity roll – same mechanic as whale
        double cellChance = legacyCells.contains(key(gx, gz)) ? chance : unexploredChance;
        if (random.nextDouble() >= cellChance) return null;

        // 8-chunk safe margin on each side (mirrors SkyWhaleLayout exactly)
        int x = (gx * spacing + 8 + random.nextInt(spacing - 16)) * 16;
        int z = (gz * spacing + 8 + random.nextInt(spacing - 16)) * 16;

        if (Math.hypot(x, z) < 480) return null;

        // Avoid whale in this cell (same ellipse exclusion as original)
        SkyWhaleLayout.Site whale = whales.cell(gx, gz);
        if (whale != null) {
            double wx = Math.max(0, Math.abs(x - whale.x()) - 72) / 115.0;
            double wz = Math.max(0, Math.abs(z - whale.z()) - 72) / 65.0;
            if (wx * wx + wz * wz <= 1) return null;
        }

        // Avoid temples
        for (DungeonLayout.Site temple : temples.nearby(x, z))
            if (Math.abs(x - temple.x()) < 104 && Math.abs(z - temple.z()) < 104) return null;

        return new Site(kind, x, z);
    }

    public Site at(int x, int z, int margin) {
        int size = spacing * 16;
        for (Site site : cell(Math.floorDiv(x, size), Math.floorDiv(z, size)))
            if (site.contains(x, z, margin)) return site;
        return null;
    }

    public Site nearest(Kind kind, int x, int z, int radius) {
        int size = spacing * 16;
        int gx = Math.floorDiv(x, size), gz = Math.floorDiv(z, size);
        Site result = null;
        double best = Double.POSITIVE_INFINITY;
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++)
                for (Site site : cell(gx + dx, gz + dz))
                    if (site.kind == kind) {
                        double d = Math.hypot(site.x - x, site.z - z);
                        if (d < best) { best = d; result = site; }
                    }
        return result;
    }
}
