package com.example.voidscape.world;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Three colossal elemental Dragon Nest builds for Evergarden:
 * <ul>
 *   <li>{@link DragonNestLayout.Kind#FIRE} &mdash; Crimson Caldera (Vharok, Crimson Ember)</li>
 *   <li>{@link DragonNestLayout.Kind#STORM} &mdash; Tempest Fulcrum (Vaelthyr, Stormborne Apex)</li>
 *   <li>{@link DragonNestLayout.Kind#ICE} &mdash; Sovereign Eyrie (Glaceryn, Sovereign of Winter)</li>
 * </ul>
 * Each dragon model at scale 5.5 spans ~53&times;28.6&times;56.2 blocks, so the central nest
 * basin keeps a full 109-block diameter ({@code arenaRadius = 54}) unobstructed to the open sky.
 */
public final class DragonNest {
    public static final int MIN_Y = 36;

    public record Box(int x1, int y1, int z1, int x2, int y2, int z2, Material material, BlockFace facing) {}
    public record Point(int x, int y, int z) {}
    public record Treasure(Point point, BlockFace facing) {}
    private record Style(Material material, BlockFace facing) {}

    final DragonNestLayout.Kind kind;
    final int floor;
    private final List<Box> boxes = new ArrayList<>();
    private final Map<Long, List<Box>> chunks = new HashMap<>();
    private final Map<Style, BlockData> states = new ConcurrentHashMap<>();
    private final List<Treasure> treasures = new ArrayList<>();

    private static final class Holder {
        static final Map<DragonNestLayout.Kind, DragonNest> BUILDS;
        static {
            var builds = new EnumMap<DragonNestLayout.Kind, DragonNest>(DragonNestLayout.Kind.class);
            for (var kind : DragonNestLayout.Kind.values()) {
                builds.put(kind, new DragonNest(kind));
            }
            BUILDS = Map.copyOf(builds);
        }
    }

    public static DragonNest blueprint(DragonNestLayout.Kind kind) {
        return Holder.BUILDS.get(kind);
    }

    public static void prewarm() {
        for (var kind : DragonNestLayout.Kind.values()) {
            Holder.BUILDS.get(kind);
        }
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    private DragonNest(DragonNestLayout.Kind kind) {
        this.kind = kind;
        this.floor = kind.floorY;
        switch (kind) {
            case FIRE -> FireDragonNestDesign.build(this);
            case STORM -> StormDragonNestDesign.build(this);
            case ICE -> IceDragonNestDesign.build(this);
        }
        Map<Long, List<Box>> rawChunks = new HashMap<>();
        for (var b : boxes) {
            for (int x = Math.floorDiv(b.x1, 16); x <= Math.floorDiv(b.x2, 16); x++) {
                for (int z = Math.floorDiv(b.z1, 16); z <= Math.floorDiv(b.z2, 16); z++) {
                    rawChunks.computeIfAbsent(key(x, z), ignored -> new ArrayList<>()).add(b);
                }
            }
        }
        boxes.clear();
        List<Style> styleList = new ArrayList<>();
        Map<Style, Integer> styleIds = new HashMap<>();
        int height = kind.maxY - MIN_Y + 1;
        int[] grid = new int[16 * 16 * height];
        for (var entry : rawChunks.entrySet()) {
            long chunkKey = entry.getKey();
            int cx = (int) (chunkKey >> 32);
            int cz = (int) chunkKey;
            int baseX = cx * 16, baseZ = cz * 16;
            Arrays.fill(grid, 0);
            for (var b : entry.getValue()) {
                int id = b.material.isAir()
                    ? 0
                    : styleIds.computeIfAbsent(new Style(b.material, b.facing), s -> {
                        styleList.add(s);
                        return styleList.size();
                    });
                int lx1 = Math.max(0, b.x1 - baseX), lx2 = Math.min(15, b.x2 - baseX);
                int lz1 = Math.max(0, b.z1 - baseZ), lz2 = Math.min(15, b.z2 - baseZ);
                int ly1 = Math.max(0, b.y1 - MIN_Y), ly2 = Math.min(height - 1, b.y2 - MIN_Y);
                for (int lx = lx1; lx <= lx2; lx++) {
                    for (int lz = lz1; lz <= lz2; lz++) {
                        int colOffset = (lx * 16 + lz) * height;
                        Arrays.fill(grid, colOffset + ly1, colOffset + ly2 + 1, id);
                    }
                }
            }
            List<Box> compacted = new ArrayList<>();
            for (int lx = 0; lx < 16; lx++) {
                for (int lz = 0; lz < 16; lz++) {
                    int colOffset = (lx * 16 + lz) * height;
                    int ly = 0;
                    while (ly < height) {
                        int id = grid[colOffset + ly];
                        if (id == 0) {
                            ly++;
                            continue;
                        }
                        int endY = ly;
                        while (endY + 1 < height && grid[colOffset + endY + 1] == id) {
                            endY++;
                        }
                        // Greedily merge identical vertical runs across Z and X within this chunk
                        int endZ = lz;
                        while (endZ + 1 < 16 && matchesRun(grid, height, lx, endZ + 1, ly, endY, id)) {
                            endZ++;
                        }
                        int endX = lx;
                        outerX:
                        while (endX + 1 < 16) {
                            for (int z = lz; z <= endZ; z++) {
                                if (!matchesRun(grid, height, endX + 1, z, ly, endY, id)) break outerX;
                            }
                            endX++;
                        }
                        for (int x = lx; x <= endX; x++) {
                            for (int z = lz; z <= endZ; z++) {
                                int off = (x * 16 + z) * height;
                                Arrays.fill(grid, off + ly, off + endY + 1, 0);
                            }
                        }
                        Style st = styleList.get(id - 1);
                        compacted.add(new Box(
                            baseX + lx, MIN_Y + ly, baseZ + lz,
                            baseX + endX, MIN_Y + endY, baseZ + endZ,
                            st.material, st.facing
                        ));
                        ly = endY + 1;
                    }
                }
            }
            if (!compacted.isEmpty()) {
                List<Box> immutable = List.copyOf(compacted);
                chunks.put(chunkKey, immutable);
                boxes.addAll(immutable);
            }
        }
    }

    private static boolean matchesRun(int[] grid, int height, int lx, int lz, int ly1, int ly2, int id) {
        int colOffset = (lx * 16 + lz) * height;
        for (int ly = ly1; ly <= ly2; ly++) {
            if (grid[colOffset + ly] != id) return false;
        }
        return true;
    }

    void box(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        box(x1, y1, z1, x2, y2, z2, m, null);
    }

    void box(int x1, int y1, int z1, int x2, int y2, int z2, Material m, BlockFace face) {
        if (x1 > x2 || y1 > y2 || z1 > z2) return;
        int r = kind.radius;
        int cx1 = Math.max(-r, x1), cx2 = Math.min(r, x2);
        int cy1 = Math.max(MIN_Y, y1), cy2 = Math.min(kind.maxY, y2);
        int cz1 = Math.max(-r, z1), cz2 = Math.min(r, z2);
        if (cx1 > cx2 || cy1 > cy2 || cz1 > cz2) return;
        boxes.add(new Box(cx1, cy1, cz1, cx2, cy2, cz2, m, face));
    }

    void block(int x, int y, int z, Material m) {
        box(x, y, z, x, y, z, m, null);
    }

    void block(int x, int y, int z, Material m, BlockFace face) {
        box(x, y, z, x, y, z, m, face);
    }

    void treasure(int x, int y, int z, BlockFace facing) {
        treasures.add(new Treasure(new Point(x, y, z), facing));
        box(x, y, z, x, y, z, Material.CHEST, facing);
    }

    void disk(int cx, int y, int cz, int radius, Material material) {
        for (int x = -radius; x <= radius; x++) {
            int z = (int) Math.floor(Math.sqrt(radius * radius - x * x));
            box(cx + x, y, cz - z, cx + x, y, cz + z, material);
        }
    }

    void cylinder(int cx, int y1, int y2, int cz, int radius, Material material) {
        for (int x = -radius; x <= radius; x++) {
            int z = (int) Math.floor(Math.sqrt(radius * radius - x * x));
            box(cx + x, y1, cz - z, cx + x, y2, cz + z, material);
        }
    }

    void ring(int cx, int y, int cz, double radius, double width, Material material) {
        int extent = (int) Math.ceil(radius + width);
        for (int x = -extent; x <= extent; x++) {
            int start = Integer.MIN_VALUE;
            for (int z = -extent; z <= extent + 1; z++) {
                boolean inside = z <= extent && Math.abs(Math.hypot(x, z) - radius) <= width;
                if (inside && start == Integer.MIN_VALUE) start = z;
                if (!inside && start != Integer.MIN_VALUE) {
                    box(cx + x, y, cz + start, cx + x, y, cz + z - 1, material);
                    start = Integer.MIN_VALUE;
                }
            }
        }
    }

    void ellipse(int cx, int y, int cz, double rx, double rz, Material material) {
        if (rx <= 0 || rz <= 0) {
            block(cx, y, cz, material);
            return;
        }
        for (int x = -(int) Math.floor(rx); x <= Math.floor(rx); x++) {
            int z = (int) Math.floor(rz * Math.sqrt(Math.max(0, 1 - x * x / (rx * rx))));
            box(cx + x, y, cz - z, cx + x, y, cz + z, material);
        }
    }

    void ellipseRing(int cx, int y, int cz, double rx, double rz, double width, Material material) {
        if (rx <= 0 || rz <= 0) {
            block(cx, y, cz, material);
            return;
        }
        int extent = (int) Math.ceil(width), limitZ = (int) Math.floor(rz + extent);
        for (int x = -(int) Math.ceil(rx) - extent; x <= rx + extent; x++) {
            int start = Integer.MIN_VALUE;
            for (int z = -(int) Math.ceil(rz) - extent; z <= limitZ + 1; z++) {
                boolean inside = z <= limitZ && Math.abs(Math.sqrt(x * x / (rx * rx) + z * z / (rz * rz)) - 1) * Math.min(rx, rz) <= width;
                if (inside && start == Integer.MIN_VALUE) start = z;
                if (!inside && start != Integer.MIN_VALUE) {
                    box(cx + x, y, cz + start, cx + x, y, cz + z - 1, material);
                    start = Integer.MIN_VALUE;
                }
            }
        }
    }

    void line(double ax, double ay, double az, double bx, double by, double bz, double radius, Material material) {
        int steps = Math.max(1, (int) Math.ceil(Math.sqrt(Math.pow(ax - bx, 2) + Math.pow(ay - by, 2) + Math.pow(az - bz, 2)) * 2));
        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps;
            double cx = ax + (bx - ax) * t, cy = ay + (by - ay) * t, cz = az + (bz - az) * t;
            int r = (int) Math.ceil(radius);
            for (int x = (int) Math.round(cx) - r; x <= (int) Math.round(cx) + r; x++) {
                for (int z = (int) Math.round(cz) - r; z <= (int) Math.round(cz) + r; z++) {
                    double distance = radius * radius - Math.pow(x - cx, 2) - Math.pow(z - cz, 2);
                    if (distance < 0) continue;
                    double dy = Math.sqrt(distance);
                    int y1 = (int) Math.ceil(cy - dy), y2 = (int) Math.floor(cy + dy);
                    if (y1 <= y2) box(x, y1, z, x, y2, z, material);
                }
            }
            if (radius < 0.8) block((int) Math.round(cx), (int) Math.round(cy), (int) Math.round(cz), material);
        }
    }

    void catenary(double ax, double ay, double az, double bx, double by, double bz, double sag, Material material) {
        int steps = Math.max(8, (int) Math.ceil(Math.hypot(ax - bx, az - bz) * 1.6));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double x = ax + (bx - ax) * t;
            double z = az + (bz - az) * t;
            double y = ay + (by - ay) * t - Math.sin(t * Math.PI) * sag;
            block((int) Math.round(x), (int) Math.round(y), (int) Math.round(z), material);
        }
    }

    /** Builds a sculpted treasure hoard mound on the outer nest rim. */
    void hoardMound(int cx, int y, int cz, int radius, Material base, Material gold, Material gem, Material glow) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > radius + 0.35) continue;
                int h = Math.max(1, (int) Math.round((1.0 - d / (radius + 0.5)) * (radius * 0.65)));
                for (int dy = 0; dy < h; dy++) {
                    Material m = dy == 0 ? base : ((dx * 7 + dz * 13 + dy * 5) % 4 == 0 ? gem : gold);
                    block(cx + dx, y + dy, cz + dz, m);
                }
                if (d < 1.2) block(cx + dx, y + h, cz + dz, glow);
            }
        }
    }

    /** Clears the 109-block central dragon basin above the nest floor so the colossal dragon never clips. */
    void clearOpenCaldera(int clearRadius, int topY) {
        for (int x = -clearRadius; x <= clearRadius; x++) {
            int zSpan = (int) Math.floor(Math.sqrt(clearRadius * clearRadius - x * x));
            box(x, floor + 1, -zSpan, x, topY, zSpan, Material.AIR);
        }
    }

    public DragonNestLayout.Kind kind() {
        return kind;
    }

    public List<Box> boxes() {
        return Collections.unmodifiableList(boxes);
    }

    public List<Treasure> treasures() {
        return List.copyOf(treasures);
    }

    public Material at(int x, int y, int z) {
        var list = chunks.getOrDefault(key(Math.floorDiv(x, 16), Math.floorDiv(z, 16)), List.of());
        for (int i = list.size() - 1; i >= 0; i--) {
            var b = list.get(i);
            if (x >= b.x1 && x <= b.x2 && y >= b.y1 && y <= b.y2 && z >= b.z1 && z <= b.z2) {
                return b.material;
            }
        }
        return Material.AIR;
    }

    BlockData state(Material material, BlockFace facing) {
        return states.computeIfAbsent(new Style(material, facing), style -> {
            var data = style.material.createBlockData();
            if (data instanceof org.bukkit.block.data.type.Leaves leaves) leaves.setPersistent(true);
            if (data instanceof org.bukkit.block.data.Bisected plant && style.facing == BlockFace.UP)
                plant.setHalf(org.bukkit.block.data.Bisected.Half.TOP);
            if (data instanceof org.bukkit.block.data.type.Slab slab && style.facing == BlockFace.UP)
                slab.setType(org.bukkit.block.data.type.Slab.Type.TOP);
            if (data instanceof org.bukkit.block.data.type.Lantern lantern && style.facing == BlockFace.UP)
                lantern.setHanging(true);
            if (data instanceof org.bukkit.block.data.type.Candle candle) {
                candle.setLit(true);
                candle.setCandles(3);
            }
            if (style.facing != null && data instanceof Directional direction) direction.setFacing(style.facing);
            if (style.facing != null && data instanceof org.bukkit.block.data.Rotatable rotated && style.facing != BlockFace.UP)
                rotated.setRotation(style.facing);
            return data;
        });
    }

    public void render(ChunkData target, int cx, int cz, int originX, int originZ) {
        if (Math.floorMod(originX, 16) != 0 || Math.floorMod(originZ, 16) != 0) {
            throw new IllegalArgumentException("Chunk-aligned origin required");
        }
        int ox = cx * 16 - originX, oz = cz * 16 - originZ;
        for (var b : chunks.getOrDefault(key(Math.floorDiv(ox, 16), Math.floorDiv(oz, 16)), List.of())) {
            int x1 = Math.max(0, b.x1 - ox), x2 = Math.min(15, b.x2 - ox);
            int z1 = Math.max(0, b.z1 - oz), z2 = Math.min(15, b.z2 - oz);
            int y1 = Math.max(target.getMinHeight(), b.y1), y2 = Math.min(target.getMaxHeight() - 1, b.y2);
            if (x1 > x2 || z1 > z2 || y1 > y2) continue;
            if (b.facing == null && !b.material.name().endsWith("_LEAVES")) {
                target.setRegion(x1, y1, z1, x2 + 1, y2 + 1, z2 + 1, b.material);
            } else {
                var state = state(b.material, b.facing);
                target.setRegion(x1, y1, z1, x2 + 1, y2 + 1, z2 + 1, state);
            }
        }
    }
}
