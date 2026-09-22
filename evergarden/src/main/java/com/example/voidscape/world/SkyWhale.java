package com.example.voidscape.world;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator.ChunkData;

/** One fixed, block-only landmark for the isolated Evergarden2 world. */
public final class SkyWhale {
    public static final int CENTER_X = 0;
    public static final int CENTER_Z = -400;
    private static final int MIN_X = -74, MAX_X = 72, MIN_Z = -18, MAX_Z = 18;

    private SkyWhale() {}

    public static Material blockAt(int x, int y, int z) {
        if (x < MIN_X || x > MAX_X || z < MIN_Z || z > MAX_Z || y < 91 || y > 128) return null;

        // Two small islands make both ends reachable and break up the long silhouette.
        for (int anchor : new int[]{-64, 62}) {
            double d = Math.hypot(x - anchor, z);
            if (d <= 11 && y == 98) return Material.MOSS_BLOCK;
            if (d <= 11 && y >= 91 && y < 98 && d + (98 - y) * 1.35 < 12)
                return y < 95 ? Material.DEEPSLATE : Material.CALCITE;
            if (d > 8 && d <= 10 && y == 99 && Math.floorMod(x + z, 7) == 0)
                return Material.FLOWERING_AZALEA;
        }

        // Full-block stair from the head island to the interior deck.
        if (x >= -65 && x <= -57 && Math.abs(z) <= 2 && y == 99 + (x + 65))
            return Material.SMOOTH_QUARTZ;
        if (x >= 57 && x <= 65 && Math.abs(z) <= 2 && y == 107 - (x - 56))
            return Material.SMOOTH_QUARTZ;
        if (x >= -56 && x <= 56 && Math.abs(z) <= 2 && y == 107)
            return Math.floorMod(x, 9) == 0 && z == 0 ? Material.SEA_LANTERN : Material.SMOOTH_QUARTZ;
        if (x >= -54 && x <= 53 && Math.abs(z) == 3 && y == 108)
            return Material.CALCITE;

        // Hollow skull, with a broad snout and eye sockets. The open center is walkable.
        double head = Math.pow((x + 46) / 14.0, 2) + Math.pow(z / 10.0, 2)
                + Math.pow((y - 113) / 8.0, 2);
        if (head >= 0.72 && head <= 1.15 && x >= -60 && x <= -32)
            return (x <= -42 && Math.abs(z) >= 7 && y >= 112 && y <= 115)
                    ? Material.SEA_LANTERN : Material.BONE_BLOCK;
        if (x >= -70 && x <= -57 && Math.abs(z) <= Math.max(2, 6 - (-57 - x) / 3)
                && (y == 108 || y == 113)) return Material.BONE_BLOCK;
        if (x >= -68 && x <= -58 && Math.abs(z) == 5 && y >= 108 && y <= 110)
            return Material.CALCITE;

        // Arched rib pairs, separated enough to see the sky between them.
        if (x >= -29 && x <= 37) {
            int nearest = Math.round((x + 29) / 8.0f) * 8 - 29;
            if (Math.abs(x - nearest) <= 1) {
                double ring = Math.pow(z / 10.0, 2) + Math.pow((y - 109) / 12.0, 2);
                if (ring >= 0.79 && ring <= 1.19 && y >= 109)
                    return Math.floorMod(nearest, 3) == 0 ? Material.CALCITE : Material.BONE_BLOCK;
            }
        }
        if (x >= -31 && x <= 40 && Math.abs(z) <= 1 && y >= 120 && y <= 121)
            return Material.BONE_BLOCK;

        // Split tail flukes, easy to identify from the approach.
        if (x >= 39 && x <= 69 && y >= 108 && y <= 110
                && Math.abs(z) >= Math.max(0, (x - 39) / 4)
                && Math.abs(z) <= Math.min(16, 3 + (x - 39) / 2))
            return Material.CALCITE;
        return null;
    }

    public static void render(ChunkData data, int chunkX, int chunkZ) {
        int startX = chunkX * 16, startZ = chunkZ * 16;
        if (startX > CENTER_X + MAX_X || startX + 15 < CENTER_X + MIN_X
                || startZ > CENTER_Z + MAX_Z || startZ + 15 < CENTER_Z + MIN_Z) return;
        for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
            int x = startX + lx - CENTER_X, z = startZ + lz - CENTER_Z;
            for (int y = Math.max(91, data.getMinHeight()); y <= Math.min(128, data.getMaxHeight() - 1); y++) {
                Material material = blockAt(x, y, z);
                if (material != null) data.setBlock(lx, y, lz, material);
            }
        }
    }
}
