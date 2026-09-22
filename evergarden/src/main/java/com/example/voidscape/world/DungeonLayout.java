package com.example.voidscape.world;

import java.util.*;

/** Pure, seed-stable placement for the 3 Voidscape elemental shrines. */
public final class DungeonLayout {
    public enum Kind {
        SANCTUM_DARK("วิหารความมืด"),
        SANCTUM_ASTRAL("วิหารดวงดาว"),
        SANCTUM_TIME("วิหารกาลเวลา");

        public final String displayName;
        Kind(String name) { this.displayName = name; }
    }

    public record Site(Kind kind, int x, int z, long variant) {
        public String id() { return kind.name().toLowerCase(Locale.ROOT) + "_" + x + "_" + z; }
        public int radius() { return 28; }
        public boolean contains(double px, double pz, int margin) {
            return Math.abs(px - x) <= radius() + margin && Math.abs(pz - z) <= radius() + margin;
        }
    }

    // 3 Guaranteed starter sanctums surrounding the spawn island (~250-255 blocks out)
    public static final List<Site> STARTER_SITES = List.of(
        new Site(Kind.SANCTUM_DARK, 0, -250, 1001L),
        new Site(Kind.SANCTUM_ASTRAL, 220, 130, 1002L),
        new Site(Kind.SANCTUM_TIME, -220, 130, 1003L)
    );

    private final long seed;
    private final int spacing;
    private final double chance;

    public DungeonLayout(long seed, int spacingChunks, double chance) {
        this.seed = seed;
        this.spacing = Math.max(12, Math.min(64, spacingChunks));
        this.chance = clamp(chance);
    }

    private static double clamp(double d) { return Double.isFinite(d) ? Math.max(0, Math.min(1, d)) : 0; }
    public static long mix(long n) {
        n = (n ^ (n >>> 30)) * 0xbf58476d1ce4e5b9L;
        n = (n ^ (n >>> 27)) * 0x94d049bb133111ebL;
        return n ^ (n >>> 31);
    }

    private Site candidate(int gx, int gz) {
        int blockSize = spacing * 16;
        int cellCenterX = gx * blockSize + blockSize / 2;
        int cellCenterZ = gz * blockSize + blockSize / 2;
        // Keep spawn island zone clear (starter shrines handle spawn region)
        if (Math.hypot(cellCenterX, cellCenterZ) < 260) return null;

        long cellSeed = mix(seed ^ ((long) gx * 341873128712L) ^ ((long) gz * 132897987541L));
        Random r = new Random(cellSeed);
        if (r.nextDouble() >= chance) return null;
        int margin = Math.max(3, spacing / 5);
        int x = (gx * spacing + margin + r.nextInt(Math.max(1, spacing - 2 * margin))) * 16 + 8;
        int z = (gz * spacing + margin + r.nextInt(Math.max(1, spacing - 2 * margin))) * 16 + 8;
        for (Site starter : STARTER_SITES) {
            if (Math.hypot(x - starter.x(), z - starter.z()) < 140) return null;
        }
        Kind kind = Kind.values()[Math.floorMod((int) (cellSeed >>> 16), Kind.values().length)];
        return new Site(kind, x, z, cellSeed);
    }

    public List<Site> nearby(int blockX, int blockZ) {
        List<Site> sites = new ArrayList<>(12);
        // Include any starter sites in range
        for (Site starter : STARTER_SITES) {
            if (Math.abs(starter.x - blockX) <= 120 && Math.abs(starter.z - blockZ) <= 120) {
                sites.add(starter);
            }
        }
        int blockSize = spacing * 16;
        int gx = Math.floorDiv(blockX, blockSize), gz = Math.floorDiv(blockZ, blockSize);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Site s = candidate(gx + dx, gz + dz);
                if (s != null) sites.add(s);
            }
        }
        return sites;
    }

    public Site at(int x, int z, int margin) {
        for (Site starter : STARTER_SITES) {
            if (starter.contains(x, z, margin)) return starter;
        }
        for (Site s : nearby(x, z)) {
            if (s.contains(x, z, margin)) return s;
        }
        return null;
    }

    public Site locate(int x, int z, Kind targetKind, int cells) {
        Site nearest = null;
        double best = Double.MAX_VALUE;
        // Check starter sites first
        for (Site s : STARTER_SITES) {
            if (targetKind != null && s.kind() != targetKind) continue;
            double d = Math.hypot((double) s.x - x, (double) s.z - z);
            if (d < best) {
                best = d;
                nearest = s;
            }
        }
        // Check grid sites across the search cells
        int blockSize = spacing * 16;
        int gx = Math.floorDiv(x, blockSize), gz = Math.floorDiv(z, blockSize);
        for (int dx = -cells; dx <= cells; dx++) {
            for (int dz = -cells; dz <= cells; dz++) {
                Site s = candidate(gx + dx, gz + dz);
                if (s == null) continue;
                if (targetKind != null && s.kind() != targetKind) continue;
                double d = Math.hypot((double) s.x - x, (double) s.z - z);
                if (d < best) {
                    best = d;
                    nearest = s;
                }
            }
        }
        return nearest;
    }
}
