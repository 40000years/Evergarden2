package com.example.voidscape.world;

import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;
import java.util.Random;
import java.util.Set;

/**
 * Where vanilla villages and pillager outposts can start, computed from the seed exactly as the vanilla random-spread
 * placement does (spacing, separation, salt, linear spread; outposts also roll the legacy 1-in-5 reducer). The start's
 * biome is sampled at several points and any match counts, so the answer errs towards "a settlement may be here".
 * Never loads or generates chunks.
 */
public final class VanillaSettlements {
    private VanillaSettlements() {}

    private record Placement(int spacing, int separation, int salt, int reach, boolean outpost, Set<Biome> biomes) {}

    /** Reach: how far the settlement's buildings extend from its start chunk centre. */
    private static final Placement VILLAGE = new Placement(34, 8, 10387312, 96, false,
            Set.of(Biome.PLAINS, Biome.MEADOW, Biome.DESERT, Biome.SAVANNA, Biome.SNOWY_PLAINS, Biome.TAIGA));
    private static final Placement OUTPOST = new Placement(32, 8, 165745296, 48, true,
            Set.of(Biome.PLAINS, Biome.MEADOW, Biome.DESERT, Biome.SAVANNA, Biome.SNOWY_PLAINS, Biome.TAIGA,
                    Biome.GROVE, Biome.SNOWY_SLOPES, Biome.FROZEN_PEAKS, Biome.JAGGED_PEAKS, Biome.STONY_PEAKS, Biome.CHERRY_GROVE));

    /** True when a village or outpost could stand within {@code radius} blocks of the box centred on x, z. */
    static boolean near(WorldInfo info, BiomeProvider biomes, int x, int z, int radius) {
        return near(info, biomes, VILLAGE, x, z, radius) || near(info, biomes, OUTPOST, x, z, radius);
    }

    private static boolean near(WorldInfo info, BiomeProvider biomes, Placement p, int x, int z, int radius) {
        int reach = radius + p.reach;
        int minChunk = Math.floorDiv(x - reach, 16), maxChunk = Math.floorDiv(x + reach, 16);
        int minChunkZ = Math.floorDiv(z - reach, 16), maxChunkZ = Math.floorDiv(z + reach, 16);
        for (int rx = Math.floorDiv(minChunk, p.spacing); rx <= Math.floorDiv(maxChunk, p.spacing); rx++)
            for (int rz = Math.floorDiv(minChunkZ, p.spacing); rz <= Math.floorDiv(maxChunkZ, p.spacing); rz++) {
                long[] start = start(info.getSeed(), p, rx, rz);
                int cx = (int) start[0], cz = (int) start[1];
                if (cx < minChunk || cx > maxChunk || cz < minChunkZ || cz > maxChunkZ) continue;
                if (p.outpost && !outpostRoll(info.getSeed(), cx, cz)) continue;
                if (settles(info, biomes, p, cx, cz)) return true;
            }
        return false;
    }

    /**
     * Vanilla checks the biome at the start chunk's corner at surface height; sample the corner and middle at five
     * heights so hills and biome edges still count.
     */
    private static boolean settles(WorldInfo info, BiomeProvider biomes, Placement p, int cx, int cz) {
        for (int y : new int[]{64, 80, 112, 144, 176})
            for (int o : new int[]{0, 8})
                if (p.biomes.contains(biomes.getBiome(info, cx * 16 + o, y, cz * 16 + o))) return true;
        return false;
    }

    /** RandomSpreadStructurePlacement.getPotentialStructureChunk with a linear spread. */
    private static long[] start(long seed, Placement p, int rx, int rz) {
        Random random = new Random((long) rx * 341873128712L + (long) rz * 132897987541L + seed + p.salt);
        int range = p.spacing - p.separation;
        return new long[]{(long) rx * p.spacing + random.nextInt(range), (long) rz * p.spacing + random.nextInt(range)};
    }

    /** Pillager outposts: StructurePlacement.FrequencyReductionMethod.LEGACY_TYPE_1 at frequency 0.2. */
    private static boolean outpostRoll(long seed, int cx, int cz) {
        Random random = new Random((long) ((cx >> 4) ^ (cz >> 4) << 4) ^ seed);
        random.nextInt();
        return random.nextInt(5) == 0;
    }

    /** Potential start chunks of villages (outposts false) or outposts (true) in a block box, for probes. */
    public static java.util.List<long[]> starts(WorldInfo info, BiomeProvider biomes, boolean outposts, int minX, int minZ, int maxX, int maxZ) {
        Placement p = outposts ? OUTPOST : VILLAGE;
        var result = new java.util.ArrayList<long[]>();
        for (int rx = Math.floorDiv(Math.floorDiv(minX, 16), p.spacing); rx <= Math.floorDiv(Math.floorDiv(maxX, 16), p.spacing); rx++)
            for (int rz = Math.floorDiv(Math.floorDiv(minZ, 16), p.spacing); rz <= Math.floorDiv(Math.floorDiv(maxZ, 16), p.spacing); rz++) {
                long[] s = start(info.getSeed(), p, rx, rz);
                if (p.outpost && !outpostRoll(info.getSeed(), (int) s[0], (int) s[1])) continue;
                if (settles(info, biomes, p, (int) s[0], (int) s[1])) result.add(s);
            }
        return result;
    }
}
