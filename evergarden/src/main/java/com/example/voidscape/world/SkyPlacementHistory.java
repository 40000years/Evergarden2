package com.example.voidscape.world;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Cells touched before a rarity change keep their original placement rules. */
public final class SkyPlacementHistory {
    private static final Pattern REGION = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.(?:mca|mcr|linear)");

    private SkyPlacementHistory() {}

    public static long key(int x, int z) {
        return ((long)x << 32) | (z & 0xffffffffL);
    }

    public static Set<Long> captureWorld(Path worldContainer, Path overworldFolder,
                                         String worldName, int spacingChunks) throws IOException {
        Set<Long> cells = capture(worldContainer.resolve(worldName).resolve("region"), spacingChunks);
        // Paper 26.2 can report the overworld folder itself as a nested dimension.
        // Walk back to the world root, which contains the custom dimensions.
        Path folder=overworldFolder.toAbsolutePath().normalize();
        for(int depth=0;depth<6&&folder!=null;depth++,folder=folder.getParent()) {
            cells.addAll(capture(folder.resolve("dimensions").resolve("minecraft")
                    .resolve(worldName).resolve("region"), spacingChunks));
        }
        return cells;
    }

    public static Set<Long> capture(Path regionFolder, int spacingChunks) throws IOException {
        Set<Long> cells = new HashSet<>();
        if (!Files.isDirectory(regionFolder)) return cells;
        try (var regions = Files.list(regionFolder)) {
            for (Path region : regions.filter(Files::isRegularFile).toList()) {
                var match = REGION.matcher(region.getFileName().toString());
                if (!match.matches()) continue;
                long rx = Long.parseLong(match.group(1)), rz = Long.parseLong(match.group(2));
                int minX = Math.toIntExact(Math.floorDiv(rx * 32, spacingChunks));
                int maxX = Math.toIntExact(Math.floorDiv(rx * 32 + 31, spacingChunks));
                int minZ = Math.toIntExact(Math.floorDiv(rz * 32, spacingChunks));
                int maxZ = Math.toIntExact(Math.floorDiv(rz * 32 + 31, spacingChunks));
                for (int x = minX; x <= maxX; x++)
                    for (int z = minZ; z <= maxZ; z++) cells.add(key(x, z));
            }
        }
        return cells;
    }
}
