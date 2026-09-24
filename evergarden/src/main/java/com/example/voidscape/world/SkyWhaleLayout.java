package com.example.voidscape.world;

import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Sparse, seed-stable placement. Every landmark fits inside its own grid cell. */
public final class SkyWhaleLayout {
    public record Site(int x, int z) {}

    private final long seed;
    private final DungeonLayout temples;
    private final int spacing;
    private final double chance;
    private final double unexploredChance;
    private final Set<Long> legacyCells;
    private final Set<Long> repairLegacyCells;
    private final double repairLegacyChance;
    private final ConcurrentHashMap<Long, Optional<Site>> cache = new ConcurrentHashMap<>();

    public SkyWhaleLayout(long seed, DungeonLayout temples, int spacingChunks, double chance) {
        this(seed, temples, spacingChunks, chance, chance, Set.of());
    }

    public SkyWhaleLayout(long seed, DungeonLayout temples, int spacingChunks, double chance,
                          double unexploredChance, Set<Long> legacyCells) {
        this(seed,temples,spacingChunks,chance,unexploredChance,legacyCells,Set.of(),unexploredChance);
    }
    public SkyWhaleLayout(long seed, DungeonLayout temples, int spacingChunks, double chance,
                          double unexploredChance, Set<Long> legacyCells,Set<Long> repairLegacyCells,double repairLegacyChance) {
        this.seed = seed;
        this.temples = temples;
        this.spacing = Math.max(32, Math.min(64, spacingChunks));
        this.chance = Double.isFinite(chance) ? Math.clamp(chance, 0, 1) : 0;
        this.unexploredChance = Double.isFinite(unexploredChance) ? Math.clamp(unexploredChance, 0, 1) : 0;
        this.legacyCells = Set.copyOf(legacyCells);
        this.repairLegacyCells=Set.copyOf(repairLegacyCells);
        this.repairLegacyChance=Double.isFinite(repairLegacyChance)?Math.clamp(repairLegacyChance,0,1):0;
    }

    public int spacingChunks() { return spacing; }
    public double chance() { return chance; }
    public double unexploredChance() { return unexploredChance; }
    public Set<Long> legacyCells() { return legacyCells; }
    public double chanceForCell(int x,int z){return legacyCells.contains(key(x,z))?chance:
            repairLegacyCells.contains(key(x,z))?repairLegacyChance:unexploredChance;}

    private static long key(int x, int z) { return ((long)x << 32) | (z & 0xffffffffL); }

    public Site cell(int gridX, int gridZ) {
        return cache.computeIfAbsent(key(gridX, gridZ), ignored -> Optional.ofNullable(candidate(gridX, gridZ)))
                .orElse(null);
    }

    private Site candidate(int gridX, int gridZ) {
        long mixed = DungeonLayout.mix(seed ^ 0x779b8f38e312a0d5L
                ^ ((long)gridX * 341873128712L) ^ ((long)gridZ * 132897987541L));
        Random random = new Random(mixed);
        double cellChance = chanceForCell(gridX,gridZ);
        if (random.nextDouble() >= cellChance) return null;
        // Eight chunks of margin contain the whole whale and its terrain reservation.
        int offset = 8 + random.nextInt(spacing - 16);
        int x = (gridX * spacing + offset) * 16;
        offset = 8 + random.nextInt(spacing - 16);
        int z = (gridZ * spacing + offset) * 16;
        if (Math.hypot(x, z) < 480) return null;
        for (DungeonLayout.Site temple : temples.nearby(x, z)) {
            if (Math.abs(x - temple.x()) < 160 && Math.abs(z - temple.z()) < 110) return null;
        }
        return new Site(x, z);
    }

    public Site at(int blockX, int blockZ, int margin) {
        int cellSize = spacing * 16;
        Site site = cell(Math.floorDiv(blockX, cellSize), Math.floorDiv(blockZ, cellSize));
        return site != null && SkyWhale.containsColumn(blockX - site.x(), blockZ - site.z(), margin)
                ? site : null;
    }

    public Site nearest(int blockX, int blockZ, int radiusCells) {
        int cellSize = spacing * 16;
        int gridX = Math.floorDiv(blockX, cellSize), gridZ = Math.floorDiv(blockZ, cellSize);
        Site closest = null;
        double best = Double.POSITIVE_INFINITY;
        for (int dx = -radiusCells; dx <= radiusCells; dx++)
            for (int dz = -radiusCells; dz <= radiusCells; dz++) {
                Site site = cell(gridX + dx, gridZ + dz);
                if (site == null) continue;
                double distance = Math.hypot(site.x() - blockX, site.z() - blockZ);
                if (distance < best) { best = distance; closest = site; }
            }
        return closest;
    }
}
