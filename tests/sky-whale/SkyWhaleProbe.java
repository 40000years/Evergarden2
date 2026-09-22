import com.example.voidscape.world.DungeonLayout;
import com.example.voidscape.world.SkyWhale;
import com.example.voidscape.world.VoidGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Opt-in integration probe. Run only through run.py, which creates a disposable server. */
public final class SkyWhaleProbe extends JavaPlugin {
    private static final long SEED = 72819345L;
    private final List<String> checks = new ArrayList<>();

    @Override public void onEnable() {
        // World creation must wait until Paper has completed its initial startup.
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                runProbe();
                Files.writeString(getServer().getWorldContainer().toPath().resolve("whale-probe-result.txt"),
                        "PASS\n" + String.join("\n", checks) + "\n");
                getLogger().info("SKY_WHALE_PROBE_PASS " + String.join(" | ", checks));
            } catch (Throwable failure) {
                getLogger().log(java.util.logging.Level.SEVERE, "SKY_WHALE_PROBE_FAIL", failure);
                try {
                    Files.writeString(getServer().getWorldContainer().toPath().resolve("whale-probe-result.txt"),
                            "FAIL\n" + failure + "\n" + String.join("\n", checks));
                } catch (java.io.IOException ignored) { }
            } finally {
                Bukkit.shutdown();
            }
        });
    }

    private void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks.add(description);
        getLogger().info("PASS " + description);
    }

    private void runProbe() {
        DungeonLayout layout = new DungeonLayout(SEED, 18, .85);
        VoidGenerator generator = new VoidGenerator(SEED, layout, true);
        VoidGenerator disabled = new VoidGenerator(SEED, layout, false);
        World world = new WorldCreator("whale_probe").seed(SEED).generator(generator).createWorld();
        if (world == null) throw new AssertionError("Probe world did not load");
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        world.setAutoSave(false);

        for (SkyWhale.Block block : SkyWhale.blocks())
            if (!SkyWhale.containsColumn(block.x(), block.z()))
                throw new AssertionError("Structure leaves its reserved footprint at " + block);
        check(!SkyWhale.blocks().isEmpty(), "Every model block fits inside the reserved footprint");

        int reserved = 0, restoredLand = 0;
        for (int x = SkyWhale.MIN_X - 8; x <= SkyWhale.MAX_X + 8; x++)
            for (int z = SkyWhale.MIN_Z - 8; z <= SkyWhale.MAX_Z + 8; z++) {
                if (!SkyWhale.containsColumn(x, z)) continue;
                int wx = x + SkyWhale.CENTER_X, wz = z + SkyWhale.CENTER_Z;
                if (generator.surface(wx, wz).land())
                    throw new AssertionError("Terrain intrudes into reserved column " + wx + "," + wz);
                if (disabled.surface(wx, wz).land()) restoredLand++;
                if (layout.at(wx, wz, 4) != null)
                    throw new AssertionError("Default-seed temple overlaps reservation at " + wx + "," + wz);
                reserved++;
            }
        check(reserved > 1000, reserved + " terrain columns reserved without a default-seed temple overlap");
        check(restoredLand > 100, "Disabling the whale restores noise terrain in " + restoredLand + " columns");

        int minCX = Math.floorDiv(SkyWhale.MIN_X + SkyWhale.CENTER_X - 8, 16);
        int maxCX = Math.floorDiv(SkyWhale.MAX_X + SkyWhale.CENTER_X + 8, 16);
        int minCZ = Math.floorDiv(SkyWhale.MIN_Z + SkyWhale.CENTER_Z - 8, 16);
        int maxCZ = Math.floorDiv(SkyWhale.MAX_Z + SkyWhale.CENTER_Z + 8, 16);
        List<int[]> chunks = new ArrayList<>();
        for (int cx = minCX; cx <= maxCX; cx++) for (int cz = minCZ; cz <= maxCZ; cz++)
            chunks.add(new int[]{cx, cz});
        Collections.shuffle(chunks, new Random(1024));

        long started = System.nanoTime(), solid = 0, air = 0, seamBlocks = 0;
        for (int[] coords : chunks) {
            Chunk chunk = world.getChunkAt(coords[0], coords[1]);
            ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
            for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
                int x = coords[0] * 16 + lx - SkyWhale.CENTER_X;
                int z = coords[1] * 16 + lz - SkyWhale.CENTER_Z;
                if (!SkyWhale.containsColumn(x, z)) continue;
                for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                    Material expected = SkyWhale.blockAt(x, y, z);
                    Material actual = snapshot.getBlockType(lx, y, lz);
                    if (expected == null || expected.isAir()) {
                        if (!actual.isAir()) throw new AssertionError("Unexpected terrain/decor at "
                                + x + "," + y + "," + z + ": " + actual);
                        air++;
                    } else {
                        if (actual != expected) throw new AssertionError("Clipped/changed structure at "
                                + x + "," + y + "," + z + ": " + actual + " expected " + expected);
                        solid++;
                        if (lx == 0 || lx == 15 || lz == 0 || lz == 15) seamBlocks++;
                    }
                }
            }
        }
        double elapsed = (System.nanoTime() - started) / 1_000_000_000.0;
        check(solid > 5000, solid + " structure blocks match actual Paper-generated chunks");
        check(air > 100000, air + " reserved air cells remain clear of noise, trees and ruins");
        check(seamBlocks > 500, seamBlocks + " blocks preserved on positive/negative chunk edges");
        checks.add(chunks.size() + " chunks generated and scanned in " + String.format(java.util.Locale.ROOT, "%.2f", elapsed)
                + " s (includes terrain, lighting and verification; not a server TPS benchmark)");

        SkyWhale.WalkPoint previous = null;
        for (SkyWhale.WalkPoint point : SkyWhale.walkway()) {
            int wx = point.x() + SkyWhale.CENTER_X, wz = point.z() + SkyWhale.CENTER_Z;
            if (!world.getBlockAt(wx, point.y(), wz).getType().isSolid())
                throw new AssertionError("Walkway has no solid floor at " + point);
            for (int above = 1; above <= 3; above++)
                if (!world.getBlockAt(wx, point.y() + above, wz).getType().isAir())
                    throw new AssertionError("Walkway headroom obstructed at " + point + " + " + above);
            if (previous != null && (Math.abs(point.x() - previous.x()) > 1
                    || Math.abs(point.z() - previous.z()) > 1 || Math.abs(point.y() - previous.y()) > 1))
                throw new AssertionError("Walkway has an unwalkable gap between " + previous + " and " + point);
            previous = point;
        }
        check(SkyWhale.walkway().size() > 130,
                SkyWhale.walkway().size() + " route steps have solid floors, 3-block headroom and no gaps");

        // Compare independently generated ChunkData to engine chunks in reverse order.
        // This catches decoration state that changes with chunk loading order.
        Collections.reverse(chunks);
        int compared = 0;
        for (int[] coords : chunks) {
            ChunkData raw = Bukkit.getServer().createChunkData(world);
            generator.generateNoise(world, new Random(9876), coords[0], coords[1], raw);
            ChunkSnapshot snapshot = world.getChunkAt(coords[0], coords[1]).getChunkSnapshot(false, false, false);
            for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
                int x = coords[0] * 16 + lx - SkyWhale.CENTER_X;
                int z = coords[1] * 16 + lz - SkyWhale.CENTER_Z;
                if (!SkyWhale.containsColumn(x, z)) continue;
                for (int y = SkyWhale.MIN_Y; y <= SkyWhale.MAX_Y; y++) {
                    Material expected = raw.getType(lx, y, lz);
                    Material actual = snapshot.getBlockType(lx, y, lz);
                    if (actual != expected && !(actual.isAir() && expected.isAir()))
                        throw new AssertionError("Order-dependent block at " + x + "," + y + "," + z);
                    compared++;
                }
            }
        }
        check(compared > 100000, compared + " voxel samples identical after reverse-order generation");

        // Existing chunks must survive loading. The design is only applied to new chunks.
        int markerY = SkyWhale.MAX_Y + 5;
        world.getBlockAt(SkyWhale.CENTER_X, markerY, SkyWhale.CENTER_Z).setType(Material.GOLD_BLOCK, false);
        world.save();
        check(Bukkit.unloadWorld(world, true), "Disposable probe world unloads after save");
        World reloaded = new WorldCreator("whale_probe").seed(SEED).generator(generator).createWorld();
        check(reloaded != null && reloaded.getBlockAt(SkyWhale.CENTER_X, markerY, SkyWhale.CENTER_Z).getType() == Material.GOLD_BLOCK,
                "Reload preserves existing chunks; a new design needs fresh chunks or an isolated new world");
    }
}
