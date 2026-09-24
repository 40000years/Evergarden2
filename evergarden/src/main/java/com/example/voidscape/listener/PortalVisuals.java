package com.example.voidscape.listener;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import java.io.File;
import java.util.*;

/** Persistent custom portal cells; no vanilla portal texture overrides. */
public final class PortalVisuals {
    private final VoidscapePlugin plugin;
    private final Map<String, Axis> cells = new HashMap<>();
    private final Map<String, UUID> displays = new HashMap<>();
    private final File file;
    private final NamespacedKey rendererRevision;
    private final Random random = new Random();
    private final Particle.DustOptions cyanDust = new Particle.DustOptions(Color.fromRGB(25, 215, 255), 0.9f);
    private final Particle.DustOptions deepBlueDust = new Particle.DustOptions(Color.fromRGB(10, 100, 240), 0.8f);

    public PortalVisuals(VoidscapePlugin plugin) {
        this.plugin = plugin;
        rendererRevision = plugin.key("portal_renderer_revision");
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
        Bukkit.getScheduler().runTaskTimer(plugin, this::spawnPortalParticles, 10L, 3L);
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
        if (b.getType() != Material.STRUCTURE_VOID) return false;
        return cells.containsKey(key(b)) || cells.containsKey(legacyKey(b));
    }
    public void add(Block b, Axis axis) {
        b.setType(Material.STRUCTURE_VOID, false);
        cells.put(key(b), axis);
    }
    public void remove(Block b) {
        String k = key(b);
        String lk = legacyKey(b);
        cells.remove(k);
        cells.remove(lk);
        UUID uuid = displays.remove(k);
        if (uuid == null) uuid = displays.remove(lk);
        Entity e = uuid == null ? null : Bukkit.getEntity(uuid);
        if (e != null) e.remove();
        // After a restart the display UUID map is empty, but the persistent stand remains.
        Location center=b.getLocation().add(0.5,-0.5,0.5);
        for(Entity nearby:b.getWorld().getNearbyEntities(center,0.65,2.0,0.65))
            if(nearby instanceof ArmorStand stand) {
                String cell=stand.getPersistentDataContainer().get(plugin.key("portal_visual"),PersistentDataType.STRING);
                if(k.equals(cell)||lk.equals(cell))stand.remove();
            }
    }
    public void cleanupOrphanDisplays(Iterable<? extends Entity> entities) {
        for(Entity entity:entities)if(entity instanceof ArmorStand stand) {
            String cell=stand.getPersistentDataContainer().get(plugin.key("portal_visual"),PersistentDataType.STRING);
            if(cell==null)continue;
            String[] parts=cell.split(",");
            if(parts.length!=4){stand.remove();continue;}
            World world=resolveWorld(parts[0]);
            if(world==null||world!=stand.getWorld()){stand.remove();continue;}
            try {
                Block block=world.getBlockAt(Integer.parseInt(parts[1]),Integer.parseInt(parts[2]),Integer.parseInt(parts[3]));
                if(!cells.containsKey(key(block))&&!cells.containsKey(legacyKey(block)))stand.remove();
            } catch(NumberFormatException ignored) {stand.remove();}
        }
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
            if (block.getType() != Material.STRUCTURE_VOID) {
                if (block.getType() == Material.AIR || block.getType() == Material.CAVE_AIR || block.getType() == Material.VOID_AIR) {
                    block.setType(Material.STRUCTURE_VOID, false);
                } else {
                    remove(block);
                    changed = true;
                    continue;
                }
            }
            UUID uuid = displays.get(finalKey); Entity old = uuid == null ? null : Bukkit.getEntity(uuid);
            if (old instanceof ArmorStand stand && old.isValid()) { configure(stand, finalKey); continue; }
            Location loc = new Location(w, x + 0.5, y - 1.5, z + 0.5, entry.getValue() == Axis.X ? 0 : 90, 0);
            ArmorStand found = null;
            for (Entity nearby : w.getNearbyEntities(loc, 0.5, 0.5, 0.5)) {
                if (nearby instanceof ArmorStand stand && (finalKey.equals(stand.getPersistentDataContainer().get(plugin.key("portal_visual"), PersistentDataType.STRING))
                        || legacyKey(block).equals(stand.getPersistentDataContainer().get(plugin.key("portal_visual"), PersistentDataType.STRING)))) {
                    found = stand;
                    break;
                }
            }
            if (found == null) found = w.spawn(loc, ArmorStand.class, stand -> {
                stand.setInvisible(true); stand.setGravity(false); stand.setMarker(false);
                stand.setCollidable(false); stand.setInvulnerable(true); stand.setSilent(true);
                stand.setBasePlate(false); stand.setPersistent(true);
                stand.getPersistentDataContainer().set(plugin.key("portal_visual"), PersistentDataType.STRING, finalKey);
                configure(stand, finalKey);
            });
            configure(found, finalKey);
            displays.put(finalKey, found.getUniqueId());
        }
        if (changed) save();
    }

    private void configure(ArmorStand stand,String key) {
        // Keep the base item model aligned with Geyser's mapping. The worn armor
        // asset must be absent so Java uses the custom item model on the head.
        if(Integer.valueOf(6).equals(stand.getPersistentDataContainer().get(rendererRevision,PersistentDataType.INTEGER)))return;
        stand.setInvisible(true);stand.setGravity(false);stand.setMarker(false);
        stand.setCollidable(false);stand.setInvulnerable(true);stand.setSilent(true);
        stand.setBasePlate(false);stand.setPersistent(true);
        stand.getPersistentDataContainer().set(plugin.key("portal_visual"),PersistentDataType.STRING,key);
        ItemStack item=new ItemStack(Material.IRON_HELMET);
        var meta=item.getItemMeta();
        meta.setItemModel(null);
        var equipment=meta.getEquippable();
        equipment.setSlot(EquipmentSlot.HEAD);equipment.setModel(null);
        meta.setEquippable(equipment);
        var cmd=meta.getCustomModelDataComponent();
        cmd.setStrings(List.of("voidscape:azure_portal"));
        meta.setCustomModelDataComponent(cmd);item.setItemMeta(meta);
        stand.getEquipment().setHelmet(item,true);
        for(var lock:ArmorStand.LockType.values())stand.addEquipmentLock(EquipmentSlot.HEAD,lock);
        stand.getPersistentDataContainer().set(rendererRevision,PersistentDataType.INTEGER,6);
    }

    public void spawnPortalParticles() {
        if (cells.isEmpty()) return;
        for (var entry : new ArrayList<>(cells.entrySet())) {
            String key = entry.getKey();
            String[] parts = key.split(",");
            World w = resolveWorld(parts[0]);
            if (w == null) continue;
            int x = Integer.parseInt(parts[1]), y = Integer.parseInt(parts[2]), z = Integer.parseInt(parts[3]);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;

            Location center = new Location(w, x + 0.5, y + 0.5, z + 0.5);
            if (w.getNearbyPlayers(center, 32.0).isEmpty()) continue;

            Axis axis = entry.getValue();
            double px, py, pz;
            if (axis == Axis.X) {
                px = x + random.nextDouble();
                py = y + random.nextDouble();
                pz = z + 0.5 + (random.nextBoolean() ? 0.08 : -0.08);
            } else {
                px = x + 0.5 + (random.nextBoolean() ? 0.08 : -0.08);
                py = y + random.nextDouble();
                pz = z + random.nextDouble();
            }

            // Upward gentle soul flame stream
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, px, py, pz, 1, 0.0, 0.015, 0.0, 0.005);

            // Ambient azure/cyan dust mist
            if (random.nextInt(3) == 0) {
                Particle.DustOptions dust = random.nextBoolean() ? cyanDust : deepBlueDust;
                w.spawnParticle(Particle.DUST, px, py, pz, 1, 0.0, 0.0, 0.0, dust);
            }

            // Ghostly cyan soul wisp drifting out of the portal
            if (random.nextInt(5) == 0) {
                w.spawnParticle(Particle.SCULK_SOUL, px, py, pz, 1, 0.0, 0.02, 0.0, 0.01);
            }

            // Subtle mystical portal hum
            if (random.nextInt(70) == 0) {
                w.playSound(center, Sound.BLOCK_PORTAL_AMBIENT, SoundCategory.BLOCKS, 0.2f, 1.35f);
            }
        }
    }
}
