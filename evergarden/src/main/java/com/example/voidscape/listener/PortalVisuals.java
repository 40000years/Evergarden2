package com.example.voidscape.listener;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.block.data.Orientable;
import org.bukkit.persistence.PersistentDataType;
import java.io.File;
import java.util.*;

/** Persistent quartz gates rendered by vanilla purple portal blocks. */
public final class PortalVisuals {
    private final VoidscapePlugin plugin;
    private final Map<String, Axis> cells = new HashMap<>();
    private final File file;

    public PortalVisuals(VoidscapePlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "portals.yml");
        var config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            try {
                Axis axis = Axis.valueOf(config.getString(key));
                String[] parts = key.split(",");
                World w = resolveWorld(parts[0]);
                if (w != null) {
                    cells.put(w.getName() + "," + parts[1] + "," + parts[2] + "," + parts[3], axis);
                } else {
                    cells.put(key, axis);
                }
            }
            catch (IllegalArgumentException ignored) { plugin.getLogger().warning("Invalid portal cell: " + key); }
        }
    }
    public World resolveWorld(String token) {
        if (token == null) return null;
        World w = Bukkit.getWorld(token);
        if (w != null) return w;
        try {
            return Bukkit.getWorld(UUID.fromString(token));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
    private String key(Block b) { return b.getWorld().getName()+","+b.getX()+","+b.getY()+","+b.getZ(); }
    private String legacyKey(Block b) { return b.getWorld().getUID()+","+b.getX()+","+b.getY()+","+b.getZ(); }
    public boolean contains(Block b) {
        if (b.getType() != Material.NETHER_PORTAL && b.getType() != Material.STRUCTURE_VOID) return false;
        return cells.containsKey(key(b)) || cells.containsKey(legacyKey(b));
    }
    public void add(Block b, Axis axis) {
        render(b, axis);
        cells.put(key(b), axis);
    }
    public void remove(Block b) {
        String k = key(b);
        String lk = legacyKey(b);
        cells.remove(k);
        cells.remove(lk);
    }
    /** Retire blue displays, including persisted entities loaded after startup. */
    public void cleanupOrphanDisplays(Iterable<? extends Entity> entities) {
        for(Entity entity:entities)
            if(entity.getPersistentDataContainer().has(plugin.key("portal_visual"),PersistentDataType.STRING))
                entity.remove();
    }
    public void save() {
        var config = new YamlConfiguration();
        cells.forEach((k, axis) -> config.set(k, axis.name()));
        try { config.save(file); }
        catch (java.io.IOException e) { plugin.getLogger().log(java.util.logging.Level.SEVERE, "Cannot save portal cells", e); }
    }
    public void close() {
        save();
    }
    public void tick() {
        boolean changed = false;
        Set<String> validatedCells = new HashSet<>();
        for (var entry : new HashMap<>(cells).entrySet()) {
            String origKey = entry.getKey(); String[] parts = origKey.split(",");
            World w = resolveWorld(parts[0]);
            if (w == null) continue;
            String cellKey = origKey;
            if (!parts[0].equals(w.getName())) {
                cells.remove(origKey);
                cellKey = w.getName() + "," + parts[1] + "," + parts[2] + "," + parts[3];
                cells.put(cellKey, entry.getValue());
                changed = true;
            }
            final String finalKey = cellKey;
            int x = Integer.parseInt(parts[1]), y = Integer.parseInt(parts[2]), z = Integer.parseInt(parts[3]);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            Block block = w.getBlockAt(x, y, z);
            if (!validatedCells.contains(finalKey)) {
                List<Block> frame=TravelListener.findQuartzPortalCells(block,entry.getValue());
                if(frame==null) {
                    remove(block);
                    if(block.getType()==Material.STRUCTURE_VOID||block.getType()==Material.NETHER_PORTAL)
                        block.setType(Material.AIR,false);
                    changed=true;
                    continue;
                }
                for(Block cell:frame)validatedCells.add(key(cell));
            }
            render(block, entry.getValue());
        }
        if (changed) save();
    }

    private void render(Block block, Axis axis) {
        var data = Material.NETHER_PORTAL.createBlockData();
        ((Orientable)data).setAxis(axis);
        if (!block.getBlockData().equals(data)) block.setBlockData(data, false);
    }
}
