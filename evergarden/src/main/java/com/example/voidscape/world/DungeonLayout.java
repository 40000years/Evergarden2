package com.example.voidscape.world;

import java.util.*;

/**
 * Pure, seed-stable placement for the 3 Voidscape elemental shrines with 8-Tier scaling. Tiers 1-5 come from
 * the regular grid (one shrine per cell). Tier 6-8 shrines sit on the grid corners between them, at least 190
 * blocks from every regular shrine, and stand only once the {@link Expansion} (main thread, persisted) confirmed
 * nobody had generated that ground yet: no existing shrine ever changes.
 */
public final class DungeonLayout {
    public enum Kind {
        SANCTUM_DARK("วิหารความมืด"),
        SANCTUM_ASTRAL("วิหารดวงดาว"),
        SANCTUM_TIME("วิหารกาลเวลา");

        public final String displayName;
        Kind(String name) { this.displayName = name; }
    }

    public record Site(Kind kind, int x, int z, long variant, int tier) {
        public Site(Kind kind, int x, int z, long variant) {
            this(kind, x, z, variant, 1);
        }
        public Site {
            tier = Math.clamp(tier, 1, MAX_TIER);
        }
        public String id() { return kind.name().toLowerCase(Locale.ROOT) + "_" + x + "_" + z; }
        public int radius() { return 28; }
        public boolean contains(double px, double pz, int margin) {
            return Math.abs(px - x) <= radius() + margin && Math.abs(pz - z) <= radius() + margin;
        }
        public String romanTier() {
            return switch (tier) {
                case 1 -> "I";
                case 2 -> "II";
                case 3 -> "III";
                case 4 -> "IV";
                case 5 -> "V";
                case 6 -> "VI";
                case 7 -> "VII";
                case 8 -> "VIII";
                default -> Integer.toString(tier);
            };
        }
        public String tierColorCode() {
            return switch (tier) {
                case 1 -> "§a";
                case 2 -> "§b";
                case 3 -> "§e";
                case 4 -> "§6";
                case 5 -> "§c";
                case 6 -> "§9";
                case 7 -> "§5";
                default -> "§4";
            };
        }
        public String difficultyDescription() {
            return switch (tier) {
                case 1 -> "ระดับเริ่มต้น";
                case 2 -> "ระดับปานกลาง";
                case 3 -> "ระดับท้าทาย";
                case 4 -> "ระดับอันตรายสูง";
                case 5 -> "ระดับมหาวิบัติ";
                case 6 -> "ระดับวิหารผนึกคราม";
                case 7 -> "ระดับวิหารผนึกม่วง · ต้านอาวุธประชิด 70%";
                default -> "ระดับวิหารผนึกโลหิต · ต้านธนูและเวท 50%";
            };
        }
    }

    public static final int MAX_TIER = 8;

    /** Decides (main thread, persisted) whether a Tier 6-8 shrine may stand on a grid corner. */
    public interface Expansion { Site accepted(long cell); }
    private volatile Expansion expansion;
    public void expand(Expansion expansion) { this.expansion = expansion; }
    public static long cellKey(int gx, int gz) { return ((long) gx << 32) | (gz & 0xffffffffL); }
    public int cellSize() { return spacing * 16; }

    // 3 Guaranteed starter sanctums surrounding the spawn island (~250-255 blocks out) — Always Tier 1
    public static final List<Site> STARTER_SITES = List.of(
        new Site(Kind.SANCTUM_DARK, 0, -250, 1001L, 1),
        new Site(Kind.SANCTUM_ASTRAL, 220, 130, 1002L, 1),
        new Site(Kind.SANCTUM_TIME, -220, 130, 1003L, 1)
    );

    private final long seed;
    private final int spacing;
    private final double chance;
    /** Large structures (bosses, whales, landmarks, temples) that a sanctum must not overlap. */
    public interface Reservation { boolean blocks(int x, int z); }
    private volatile Reservation reservation;
    private final Map<Long, Optional<Site>> cache = new java.util.concurrent.ConcurrentHashMap<>();

    public DungeonLayout(long seed, int spacingChunks, double chance) {
        this.seed = seed;
        this.spacing = Math.max(12, Math.min(64, spacingChunks));
        this.chance = clamp(chance);
    }

    private static double clamp(double d) { return Double.isFinite(d) ? Math.max(0, Math.min(1, d)) : 0; }
    /** Installed once by the large-structure layout before any terrain is generated. */
    public void reserve(Reservation reservation) { this.reservation = reservation; cache.clear(); }
    public static long mix(long n) {
        n = (n ^ (n >>> 30)) * 0xbf58476d1ce4e5b9L;
        n = (n ^ (n >>> 27)) * 0x94d049bb133111ebL;
        return n ^ (n >>> 31);
    }

    /**
     * Rolls a deterministic Tier (1..5) weighted by distance from (0,0),
     * while preserving mixed tiers across all zones (rare Tier 5 near spawn,
     * and occasional Tier 1-4 even in deep territory).
     */
    public static int rollTier(int x, int z, long cellSeed) {
        double dist = Math.hypot(x, z);
        long tierHash = mix(cellSeed ^ 0x544945525F5631L);
        int roll = Math.floorMod((int) (tierHash >>> 16), 100); // 0..99

        if (dist < 1000.0) {
            // Near spawn (< 1000 blocks): mostly Tier 1-2, small Tier 3-4, rare 2% Tier 5
            if (roll < 52) return 1;
            if (roll < 80) return 2;
            if (roll < 92) return 3;
            if (roll < 98) return 4;
            return 5;
        } else if (dist < 2000.0) {
            // Early-Mid zone (1000 - 2000 blocks)
            if (roll < 25) return 1;
            if (roll < 60) return 2;
            if (roll < 84) return 3;
            if (roll < 96) return 4;
            return 5;
        } else if (dist < 3500.0) {
            // Mid-Far zone (2000 - 3500 blocks)
            if (roll < 12) return 1;
            if (roll < 32) return 2;
            if (roll < 66) return 3;
            if (roll < 90) return 4;
            return 5;
        } else {
            // Deep zone (3500+ blocks): high Tier 4-5, but still mixed with Tier 1-3
            if (roll < 8) return 1;
            if (roll < 20) return 2;
            if (roll < 44) return 3;
            if (roll < 74) return 4;
            return 5;
        }
    }

    private Site candidate(int gx, int gz) {
        if (cache.size() > 200_000) cache.clear(); // roughly 129 km square of cells; rolls are deterministic
        return cache.computeIfAbsent(cellKey(gx, gz), ignored -> Optional.ofNullable(roll(gx, gz))).orElse(null);
    }

    /** The accepted Tier 6-8 shrine on the corner keyed (gx, gz), or null. */
    private Site extra(int gx, int gz) {
        Expansion gate = expansion;
        return gate == null ? null : gate.accepted(cellKey(gx, gz));
    }

    /**
     * The Tier 6-8 shrine the grid corner (gx * cell, gz * cell) could hold: beyond 1,500 blocks, at least 190
     * blocks from the four regular shrines around it, not reserved. Deterministic; whether it is built is up to
     * the {@link Expansion}.
     */
    public Site extraCandidate(int gx, int gz) {
        int blockSize = spacing * 16;
        int cornerX = gx * blockSize, cornerZ = gz * blockSize;
        double distance = Math.hypot(cornerX, cornerZ);
        if (distance < 1500) return null;
        long cellSeed = mix(seed ^ 0x5449455236373800L ^ ((long) gx * 341873128712L) ^ ((long) gz * 132897987541L));
        Random r = new Random(cellSeed);
        if (r.nextDouble() >= 0.5) return null;
        int x = cornerX + r.nextInt(81) - 40, z = cornerZ + r.nextInt(81) - 40;
        for (int dx = -1; dx <= 0; dx++) for (int dz = -1; dz <= 0; dz++) {
            Site regular = candidate(gx + dx, gz + dz);
            if (regular != null && Math.hypot(regular.x() - x, regular.z() - z) < 190) return null;
        }
        for (Site starter : STARTER_SITES) if (Math.hypot(x - starter.x(), z - starter.z()) < 140) return null;
        Reservation reserved = reservation;
        if (reserved != null && reserved.blocks(x, z)) return null;
        Kind kind = Kind.values()[Math.floorMod((int) (cellSeed >>> 16), Kind.values().length)];
        int roll = r.nextInt(100);
        int tier = distance < 3000 ? (roll < 70 ? 6 : roll < 95 ? 7 : 8) : (roll < 45 ? 6 : roll < 80 ? 7 : 8);
        return new Site(kind, x, z, cellSeed, tier);
    }
    private Site roll(int gx, int gz) {
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
        Reservation reserved = reservation;
        if (reserved != null && reserved.blocks(x, z)) return null;
        Kind kind= Kind.values()[Math.floorMod((int) (cellSeed >>> 16), Kind.values().length)];
        int tier = rollTier(x, z, cellSeed);
        return new Site(kind, x, z, cellSeed, tier);
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
                Site e = extra(gx + dx, gz + dz);
                if (e != null) sites.add(e);
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

    /** All candidate centers in a box, including grids narrower than a large landmark. */
    public List<Site> within(int x,int z,int radius) {
        List<Site> result=new ArrayList<>();int size=spacing*16;
        for(var site:STARTER_SITES)
            if(Math.abs((long)site.x-x)<=radius&&Math.abs((long)site.z-z)<=radius)result.add(site);
        for(int gx=Math.floorDiv(x-radius,size);gx<=Math.floorDiv(x+radius,size);gx++)
            for(int gz=Math.floorDiv(z-radius,size);gz<=Math.floorDiv(z+radius,size);gz++){
                for(var site:new Site[]{candidate(gx,gz),extra(gx,gz)})
                    if(site!=null&&Math.abs((long)site.x-x)<=radius&&Math.abs((long)site.z-z)<=radius)result.add(site);
            }
        return result;
    }

    public Site locate(int x, int z, Kind targetKind, int cells) {
        return locate(x, z, targetKind, 0, cells);
    }

    public Site locate(int x, int z, Kind targetKind, int targetTier, int cells) {
        Site nearest = null;
        double best = Double.MAX_VALUE;
        // Check starter sites first
        for (Site s : STARTER_SITES) {
            if (targetKind != null && s.kind() != targetKind) continue;
            if (targetTier >= 1 && targetTier <= MAX_TIER && s.tier() != targetTier) continue;
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
                for (Site s : new Site[]{candidate(gx + dx, gz + dz), extra(gx + dx, gz + dz)}) {
                    if (s == null) continue;
                    if (targetKind != null && s.kind() != targetKind) continue;
                    if (targetTier >= 1 && targetTier <= MAX_TIER && s.tier() != targetTier) continue;
                    double d = Math.hypot((double) s.x - x, (double) s.z - z);
                    if (d < best) {
                        best = d;
                        nearest = s;
                    }
                }
            }
        }
        return nearest;
    }
}
