package com.example.voidscape.world;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tier 6-8 shrines on the grid corners between regular shrines. A corner is decided once, on the main thread, ahead of the
 * players: it is accepted only when no chunk the shrine (or its valley) would touch has been generated, so a
 * shrine is never half-built into explored land and no shrine anyone has seen ever changes. Decisions are
 * saved; world generation threads only read them.
 */
public final class SanctumExpansion implements DungeonLayout.Expansion {
    /** The shrine's terrain valley reaches 29 + 96 blocks (VoidGenerator.surface). */
    private static final int FOOTPRINT = 128;
    private static final int AHEAD_CELLS = 2;

    private final VoidscapePlugin plugin;
    private final DungeonLayout layout;
    private final File file;
    private final Map<Long, DungeonLayout.Site> accepted = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> decided = new ConcurrentHashMap<>();
    private boolean dirty;

    public SanctumExpansion(VoidscapePlugin plugin, DungeonLayout layout) {
        this.plugin = plugin;
        this.layout = layout;
        this.file = new File(plugin.getDataFolder(), "sanctum-tiers.yml");
        load();
    }

    @Override
    public DungeonLayout.Site accepted(long cell) {
        return accepted.get(cell);
    }

    public int acceptedCount() {
        return accepted.size();
    }

    /** Main thread: decide every cell within two cells of each player in the Evergarden world. */
    public void decideAroundPlayers() {
        World world = plugin.world();
        if (world == null) return;
        int diskBudget = 2;
        for (Player player : world.getPlayers()) {
            if (diskBudget <= 0) break;
            diskBudget = decideAroundBudgeted(player.getLocation().getBlockX(), player.getLocation().getBlockZ(), AHEAD_CELLS, diskBudget);
        }
        flush();
    }

    /** Main thread: decide the cells within {@code cells} of a block position (also used by locate). */
    public void decideAround(int x, int z, int cells) {
        decideAroundBudgeted(x, z, cells, Integer.MAX_VALUE);
    }

    private int decideAroundBudgeted(int x, int z, int cells, int diskBudget) {
        int size = layout.cellSize();
        int gx = Math.floorDiv(x, size), gz = Math.floorDiv(z, size);
        for (int ring = 0; ring <= cells; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    if (diskBudget <= 0) return 0;
                    if (decide(gx + dx, gz + dz)) {
                        diskBudget--;
                    }
                }
            }
        }
        return diskBudget;
    }

    /** Returns {@code true} if disk-backed {@link #untouched(DungeonLayout.Site)} had to be queried. */
    private boolean decide(int gx, int gz) {
        long cell = DungeonLayout.cellKey(gx, gz);
        if (decided.containsKey(cell)) return false;
        DungeonLayout.Site site = layout.extraCandidate(gx, gz);
        if (site == null) {
            decided.put(cell, false);
            dirty = true;
            return false;
        }
        boolean accept = untouched(site);
        decided.put(cell, accept);
        if (accept) {
            accepted.put(cell, site);
            plugin.getLogger().info("[Sanctum-Tiers] new Tier " + site.tier() + " " + site.kind() + " at " + site.x() + "," + site.z());
        }
        dirty = true;
        return true;
    }

    private boolean untouched(DungeonLayout.Site site) {
        World world = plugin.world();
        int minCx = Math.floorDiv(site.x() - FOOTPRINT, 16);
        int maxCx = Math.floorDiv(site.x() + FOOTPRINT, 16);
        int minCz = Math.floorDiv(site.z() - FOOTPRINT, 16);
        int maxCz = Math.floorDiv(site.z() + FOOTPRINT, 16);
        // Fast in-memory check first across the entire footprint.
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                if (world.isChunkLoaded(cx, cz)) return false;
            }
        }
        int centerCx = site.x() >> 4, centerCz = site.z() >> 4;
        if (world.isChunkGenerated(centerCx, centerCz)) return false;
        // Any player visit generates at least a 5x5+ chunk area; sampling every 4 chunks
        // catches any previously generated land while reducing region I/O queries by >11x.
        for (int cx = minCx; cx <= maxCx; cx += 4) {
            for (int cz = minCz; cz <= maxCz; cz += 4) {
                if (world.isChunkGenerated(cx, cz)) return false;
            }
            if (world.isChunkGenerated(cx, maxCz)) return false;
        }
        for (int cz = minCz; cz <= maxCz; cz += 4) {
            if (world.isChunkGenerated(maxCx, cz)) return false;
        }
        return !world.isChunkGenerated(maxCx, maxCz);
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var cells = yaml.getConfigurationSection("cells");
        if (cells == null) return;
        for (String key : cells.getKeys(false)) {
            long cell;
            try {
                cell = Long.parseLong(key);
            } catch (NumberFormatException error) {
                continue;
            }
            boolean accept = cells.getBoolean(key + ".accepted", false);
            decided.put(cell, accept);
            if (!accept) continue;
            DungeonLayout.Site site = layout.extraCandidate((int) (cell >> 32), (int) cell);
            // The layout is deterministic: a saved shrine must still be the same one.
            if (site != null && site.x() == cells.getInt(key + ".x") && site.z() == cells.getInt(key + ".z") && site.tier() == cells.getInt(key + ".tier")) {
                accepted.put(cell, site);
            } else {
                plugin.getLogger().warning("[Sanctum-Tiers] saved shrine " + key + " no longer matches the layout; it stays out");
            }
        }
    }

    public void flush() {
        if (!dirty) return;
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", 1);
        for (var entry : decided.entrySet()) {
            String path = "cells." + entry.getKey();
            yaml.set(path + ".accepted", entry.getValue());
            DungeonLayout.Site site = accepted.get(entry.getKey());
            if (site != null) {
                yaml.set(path + ".x", site.x());
                yaml.set(path + ".z", site.z());
                yaml.set(path + ".tier", site.tier());
                yaml.set(path + ".kind", site.kind().name());
            }
        }
        try {
            var target = file.toPath();
            Files.createDirectories(target.getParent());
            var tmp = target.resolveSibling("sanctum-tiers.yml.tmp");
            Files.writeString(tmp, yaml.saveToString());
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
        } catch (IOException error) {
            plugin.getLogger().warning("[Sanctum-Tiers] cannot save decisions: " + error);
        }
    }
}
