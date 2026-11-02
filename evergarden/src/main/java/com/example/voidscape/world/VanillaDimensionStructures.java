package com.example.voidscape.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Where the vanilla Nether complexes (fortress and bastion), End cities and End gateway exits can be,
 * computed from the seed exactly as vanilla random-spread placement does. Never loads or generates a chunk.
 */
final class VanillaDimensionStructures {
    private VanillaDimensionStructures() {}

    private record Spread(int spacing, int separation, int salt) {}
    /** nether_complexes: Nether Fortress and Bastion Remnant share this one set. */
    private static final Spread NETHER_COMPLEX = new Spread(27, 4, 30084232);
    private static final Spread END_CITY = new Spread(20, 11, 10387313);
    /** Gateway exits are searched about 1,024 blocks out in each of 20 directions. */
    private static final int GATEWAY_RING = 1024, GATEWAYS = 20;

    /** Potential start chunks (chunkX, chunkZ) within a block box, vanilla linear spread. */
    private static List<int[]> starts(long seed, Spread p, int minX, int minZ, int maxX, int maxZ) {
        List<int[]> result = new ArrayList<>();
        int range = p.spacing - p.separation;
        for (int rx = Math.floorDiv(Math.floorDiv(minX, 16), p.spacing); rx <= Math.floorDiv(Math.floorDiv(maxX, 16), p.spacing); rx++)
            for (int rz = Math.floorDiv(Math.floorDiv(minZ, 16), p.spacing); rz <= Math.floorDiv(Math.floorDiv(maxZ, 16), p.spacing); rz++) {
                Random random = new Random((long) rx * 341873128712L + (long) rz * 132897987541L + seed + p.salt);
                result.add(new int[]{rx * p.spacing + random.nextInt(range), rz * p.spacing + random.nextInt(range)});
            }
        return result;
    }

    /** A fortress or bastion could start close enough that its pieces might touch a footprint of this reach. */
    static boolean netherComplexNear(long seed, int x, int z, int reach) {
        int r = reach + 120; // generous vanilla complex size (fortress pieces stretch ~112 blocks)
        for (int[] s : starts(seed, NETHER_COMPLEX, x - r, z - r, x + r, z + r)) {
            double dx = s[0] * 16 + 8 - x, dz = s[1] * 16 + 8 - z;
            if (dx * dx + dz * dz < (double) r * r) return true;
        }
        return false;
    }

    /** True when the point lies within {@code radius} of one of the 20 End gateway exit regions. */
    static boolean endGatewayNear(int x, int z, int radius) {
        for (int i = 0; i < GATEWAYS; i++) {
            double angle = 2.0 * (-Math.PI + Math.PI / GATEWAYS * i);
            double dx = Math.cos(angle) * GATEWAY_RING - x, dz = Math.sin(angle) * GATEWAY_RING - z;
            if (dx * dx + dz * dz < (double) radius * radius) return true;
        }
        return false;
    }

    interface Heights { int top(int x, int z); }

    /**
     * An End city could start within {@code reach}. Cities need land at least about y=60, so each potential
     * start is checked against the real base terrain; the answer errs towards "a city may be here".
     */
    static boolean endCityNear(long seed, int x, int z, int reach, Heights heights) {
        int r = reach + 96;
        for (int[] s : starts(seed, END_CITY, x - r, z - r, x + r, z + r)) {
            int cx = s[0] * 16 + 8, cz = s[1] * 16 + 8;
            double dx = cx - x, dz = cz - z;
            if (dx * dx + dz * dz >= (double) r * r) continue;
            int best = Integer.MIN_VALUE;
            for (int[] o : new int[][]{{0, 0}, {5, 5}, {-5, 5}, {5, -5}, {-5, -5}}) best = Math.max(best, heights.top(cx - 1 + o[0], cz - 1 + o[1]));
            if (best >= 55) return true;
        }
        return false;
    }
}
