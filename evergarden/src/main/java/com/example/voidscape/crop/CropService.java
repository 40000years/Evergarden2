package com.example.voidscape.crop;

import com.example.voidscape.VoidscapePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CropService implements Listener, AutoCloseable {
    // Head height: 24/16 * .5; farmland top: -1/16.
    private static final double CROP_STAND_Y = -0.8125;
    private final VoidscapePlugin plugin;
    private final CropItemFactory factory;
    private final Map<String, PlantedCrop> plantedCrops = new ConcurrentHashMap<>();
    public record DeferredCrop(String key, String typeId, int stage, long plantedAt, UUID displayUuid) {}
    private final Map<String, DeferredCrop> deferredCrops = new ConcurrentHashMap<>();
    private final Map<UUID, PlantedCrop> entityUuidToCrop = new ConcurrentHashMap<>();
    private final Map<ChunkCoord, Set<PlantedCrop>> cropsByChunk = new ConcurrentHashMap<>();
    private final PriorityQueue<GrowthEntry> growthQueue = new PriorityQueue<>();
    private final NamespacedKey cropEntityKey;
    private final File saveFile;
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final AtomicBoolean saving = new AtomicBoolean(false);
    private int tickCounter = 0;

    public record ChunkCoord(String world, int x, int z) {
        public static ChunkCoord of(Location loc) {
            return new ChunkCoord(loc.getWorld().getName(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        }
        public static ChunkCoord of(Chunk chunk) {
            return new ChunkCoord(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        }
    }

    public record GrowthEntry(String locKey, long targetTimeMs, int expectedStage) implements Comparable<GrowthEntry> {
        @Override
        public int compareTo(GrowthEntry other) {
            return Long.compare(this.targetTimeMs, other.targetTimeMs);
        }
    }

    public CropService(VoidscapePlugin plugin) {
        this.plugin = plugin;
        this.factory = new CropItemFactory(plugin);
        this.cropEntityKey = new NamespacedKey("voidscape", "crop_entity");
        this.saveFile = new File(plugin.getDataFolder(), "crops.yml");
        loadCrops();
    }

    public CropItemFactory factory() {
        return factory;
    }

    public Collection<PlantedCrop> getPlantedCrops() {
        return Collections.unmodifiableCollection(plantedCrops.values());
    }

    public PlantedCrop getCropAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return plantedCrops.get(locKey(loc));
    }

    public PlantedCrop getCropByEntityUuid(UUID uuid) {
        if (uuid == null) return null;
        return entityUuidToCrop.get(uuid);
    }

    private String locKey(Location l) {
        return l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    private Location parseKey(String key) {
        String[] parts = key.split(",");
        if (parts.length != 4) return null;
        World w = Bukkit.getWorld(parts[0]);
        if (w == null) {
            try {
                w = Bukkit.getWorld(UUID.fromString(parts[0]));
            } catch (IllegalArgumentException ignored) {}
        }
        if (w == null) return null;
        return new Location(w, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
    }

    private void addCropToChunkIndex(PlantedCrop crop) {
        Location loc = crop.getLocation();
        if (loc.getWorld() == null) return;
        cropsByChunk.computeIfAbsent(ChunkCoord.of(loc), k -> ConcurrentHashMap.newKeySet()).add(crop);
    }

    private void removeCropFromChunkIndex(PlantedCrop crop) {
        Location loc = crop.getLocation();
        if (loc.getWorld() == null) return;
        ChunkCoord coord = ChunkCoord.of(loc);
        Set<PlantedCrop> set = cropsByChunk.get(coord);
        if (set != null) {
            set.remove(crop);
            if (set.isEmpty()) {
                cropsByChunk.remove(coord);
            }
        }
    }

    private synchronized void scheduleGrowth(PlantedCrop crop) {
        if (crop.isMature()) return;
        long targetTime = crop.getNextStageTimestamp();
        if (targetTime != Long.MAX_VALUE) {
            growthQueue.offer(new GrowthEntry(locKey(crop.getLocation()), targetTime, crop.getStage()));
        }
    }

    public synchronized void onCropAccelerated(PlantedCrop crop) {
        if (crop.isMature()) return;
        int targetStage = crop.calculateTargetStage();
        if (targetStage > crop.getStage()) {
            Location loc = crop.getLocation();
            boolean chunkLoaded = loc.getWorld() != null && loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
            ArmorStand display = chunkLoaded ? getOrSpawnDisplay(crop) : null;
            advanceStage(crop, targetStage, display);
            if (!crop.isMature()) {
                scheduleGrowth(crop);
            }
        } else {
            scheduleGrowth(crop);
        }
    }

    public PlantedCrop findNearestUnripeCrop(Location center, double radius) {
        World world = center.getWorld();
        if (world == null) return null;

        int minCx = (center.getBlockX() - (int) Math.ceil(radius)) >> 4;
        int maxCx = (center.getBlockX() + (int) Math.ceil(radius)) >> 4;
        int minCz = (center.getBlockZ() - (int) Math.ceil(radius)) >> 4;
        int maxCz = (center.getBlockZ() + (int) Math.ceil(radius)) >> 4;

        double radiusSq = radius * radius;
        PlantedCrop nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                ChunkCoord coord = new ChunkCoord(world.getName(), cx, cz);
                Set<PlantedCrop> crops = cropsByChunk.get(coord);
                if (crops == null || crops.isEmpty()) continue;

                for (PlantedCrop crop : crops) {
                    if (crop.isMature()) continue;
                    Location cLoc = crop.getLocation();
                    if (!world.equals(cLoc.getWorld())) continue;
                    double distSq = cLoc.distanceSquared(center);
                    if (distSq <= radiusSq && distSq < nearestDistSq) {
                        nearest = crop;
                        nearestDistSq = distSq;
                    }
                }
            }
        }
        return nearest;
    }

    public void tick() {
        tickCounter++;
        long now = System.currentTimeMillis();

        // 1. Process time-based growth queue (O(1) when idle)
        synchronized (this) {
            while (!growthQueue.isEmpty()) {
                GrowthEntry entry = growthQueue.peek();
                if (entry.targetTimeMs() > now) {
                    break;
                }
                growthQueue.poll();

                PlantedCrop crop = plantedCrops.get(entry.locKey());
                if (crop == null || crop.getStage() != entry.expectedStage() || crop.isMature()) {
                    continue;
                }

                Location loc = crop.getLocation();
                World world = loc.getWorld();
                if (world == null) continue;

                if (world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                    Block soil = loc.clone().subtract(0, 1, 0).getBlock();
                    if (soil.getType() != Material.FARMLAND) {
                        harvest(crop, null, true);
                        continue;
                    }
                }

                int targetStage = crop.calculateTargetStage();
                if (targetStage > crop.getStage()) {
                    boolean chunkLoaded = world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
                    ArmorStand display = chunkLoaded ? getOrSpawnDisplay(crop) : null;
                    advanceStage(crop, targetStage, display);
                    if (!crop.isMature()) {
                        scheduleGrowth(crop);
                    }
                }
            }
        }

        // 2. Throttled, player-aware ambient particles for mature crops
        tickAmbientParticles();

        // 3. Periodic async autosave every 60s (120 ticks * 0.5s) if modified
        if (tickCounter % 120 == 0 && dirty.get()) {
            saveCropsAsync();
        }
    }

    private void tickAmbientParticles() {
        if (tickCounter % 2 != 0) return; // run particle check every 1s

        int spawnedThisTick = 0;
        final int MAX_PARTICLES_PER_TICK = 10;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (spawnedThisTick >= MAX_PARTICLES_PER_TICK) break;
            Location pLoc = player.getLocation();
            World world = pLoc.getWorld();
            if (world == null) continue;

            int pcx = pLoc.getBlockX() >> 4;
            int pcz = pLoc.getBlockZ() >> 4;

            for (int dx = -1; dx <= 1 && spawnedThisTick < MAX_PARTICLES_PER_TICK; dx++) {
                for (int dz = -1; dz <= 1 && spawnedThisTick < MAX_PARTICLES_PER_TICK; dz++) {
                    ChunkCoord coord = new ChunkCoord(world.getName(), pcx + dx, pcz + dz);
                    Set<PlantedCrop> crops = cropsByChunk.get(coord);
                    if (crops == null || crops.isEmpty()) continue;

                    for (PlantedCrop crop : crops) {
                        if (!crop.isMature()) continue;
                        Location cLoc = crop.getLocation();
                        if (cLoc.distanceSquared(pLoc) > 256.0) continue; // within 16 blocks

                        if (((crop.hashCode() ^ (tickCounter >> 1)) & 7) == 0) {
                            spawnAmbientParticles(crop);
                            spawnedThisTick++;
                            if (spawnedThisTick >= MAX_PARTICLES_PER_TICK) break;
                        }
                    }
                }
            }
        }
    }

    private void advanceStage(PlantedCrop crop, int newStage, ArmorStand display) {
        crop.setStage(newStage);
        if (display != null && display.isValid()) {
            display.getEquipment().setHelmet(factory.createPlantDisplay(crop.getType(), newStage), true);
        }

        Location loc = crop.getLocation().add(0.5, 0.5, 0.5);
        World world = loc.getWorld();
        if (world != null) {
            world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.3f);
            if (newStage == 2) {
                spawnMatureParticles(crop);
            } else {
                world.spawnParticle(Particle.HAPPY_VILLAGER, loc, 12, 0.3, 0.3, 0.3, 0.05);
            }
        }
        dirty.set(true);
    }

    private void spawnMatureParticles(PlantedCrop crop) {
        Location loc = crop.getLocation().add(0.5, 0.6, 0.5);
        World w = loc.getWorld();
        if (w == null) return;
        switch (crop.getType().tier) {
            case TIER_1 -> {
                w.spawnParticle(Particle.HAPPY_VILLAGER, loc, 25, 0.4, 0.4, 0.4, 0.08);
                w.spawnParticle(Particle.COMPOSTER, loc, 15, 0.3, 0.3, 0.3, 0.05);
            }
            case TIER_2 -> {
                w.spawnParticle(Particle.CRIT, loc, 25, 0.4, 0.4, 0.4, 0.15);
                w.spawnParticle(Particle.FLAME, loc, 15, 0.3, 0.3, 0.3, 0.03);
            }
            case TIER_3 -> {
                w.spawnParticle(Particle.PORTAL, loc, 35, 0.5, 0.5, 0.5, 0.2);
                w.spawnParticle(Particle.WITCH, loc, 15, 0.3, 0.3, 0.3, 0.05);
            }
            case TIER_4 -> {
                w.spawnParticle(Particle.SCRAPE, loc, 20, 0.4, 0.4, 0.4, 0.1);
                w.spawnParticle(Particle.ELECTRIC_SPARK, loc, 20, 0.4, 0.4, 0.4, 0.15);
            }
            case TIER_5 -> {
                w.spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 40, 0.5, 0.6, 0.5, 0.25);
                w.spawnParticle(Particle.END_ROD, loc, 20, 0.3, 0.5, 0.3, 0.05);
            }
        }
    }

    private void spawnAmbientParticles(PlantedCrop crop) {
        Location loc = crop.getLocation().add(0.5, 0.6, 0.5);
        World w = loc.getWorld();
        if (w == null) return;
        switch (crop.getType().tier) {
            case TIER_1 -> w.spawnParticle(Particle.HAPPY_VILLAGER, loc, 2, 0.2, 0.2, 0.2, 0.02);
            case TIER_2 -> w.spawnParticle(Particle.CRIT, loc, 3, 0.2, 0.2, 0.2, 0.05);
            case TIER_3 -> w.spawnParticle(Particle.PORTAL, loc, 4, 0.2, 0.2, 0.2, 0.08);
            case TIER_4 -> w.spawnParticle(Particle.ELECTRIC_SPARK, loc, 3, 0.2, 0.2, 0.2, 0.05);
            case TIER_5 -> {
                w.spawnParticle(Particle.END_ROD, loc, 3, 0.2, 0.3, 0.2, 0.02);
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 2, 0.2, 0.2, 0.2, 0.02);
            }
        }
    }

    public ArmorStand getOrSpawnDisplay(PlantedCrop crop) {
        Location loc = crop.getLocation();
        World world = loc.getWorld();
        if (world == null || !world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            return null;
        }

        if (crop.getStandUuid() != null) {
            Entity ent = Bukkit.getEntity(crop.getStandUuid());
            if (ent instanceof ArmorStand stand && ent.isValid() && ownsCropEntity(stand, crop)) {
                updateCropRenderer(stand, crop);
                return stand;
            }
            entityUuidToCrop.remove(crop.getStandUuid());
            crop.setStandUuid(null);
        }

        Location center = loc.clone().add(0.5, CROP_STAND_Y, 0.5);
        for (Entity nearby : world.getNearbyEntities(center, 0.6, 0.6, 0.6)) {
            if (nearby instanceof ArmorStand stand && ownsCropEntity(stand, crop)) {
                updateCropRenderer(stand, crop);
                crop.setStandUuid(stand.getUniqueId());
                entityUuidToCrop.put(stand.getUniqueId(), crop);
                stand.getEquipment().setHelmet(factory.createPlantDisplay(crop.getType(), crop.getStage()), true);
                dirty.set(true);
                return stand;
            }
            if ((nearby instanceof Interaction || nearby instanceof ItemDisplay) && ownsCropEntity(nearby, crop)) {
                entityUuidToCrop.remove(nearby.getUniqueId());
                nearby.remove();
            }
        }

        ArmorStand display = world.spawn(center, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setSmall(true);
            stand.setMarker(false);
            stand.setCollidable(false);
            for (ArmorStand.LockType lock : ArmorStand.LockType.values()) stand.addEquipmentLock(EquipmentSlot.HEAD, lock);
            stand.setGravity(false);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setSilent(true);
            stand.setInvulnerable(true);
            stand.setPersistent(false);
            stand.getEquipment().setHelmet(factory.createPlantDisplay(crop.getType(), crop.getStage()), true);
            stand.getPersistentDataContainer().set(cropEntityKey, PersistentDataType.STRING, locKey(crop.getLocation()));
        });
        crop.setStandUuid(display.getUniqueId());
        entityUuidToCrop.put(display.getUniqueId(), crop);
        dirty.set(true);
        return display;
    }

    private boolean ownsCropEntity(Entity entity, PlantedCrop crop) {
        return locKey(crop.getLocation()).equals(entity.getPersistentDataContainer().get(cropEntityKey, PersistentDataType.STRING));
    }

    private void updateCropRenderer(ArmorStand stand, PlantedCrop crop) {
        NamespacedKey revisionKey = new NamespacedKey("voidscape", "crop_renderer_revision");
        if (Integer.valueOf(7).equals(stand.getPersistentDataContainer().get(revisionKey, PersistentDataType.INTEGER))) return;
        stand.setMarker(false);
        stand.setCollidable(false);
        stand.setGravity(false);
        stand.setInvisible(true);
        stand.setSmall(true);
        stand.teleport(crop.getLocation().add(0.5, CROP_STAND_Y, 0.5));
        for (ArmorStand.LockType lock : ArmorStand.LockType.values()) stand.addEquipmentLock(EquipmentSlot.HEAD, lock);
        stand.getEquipment().setHelmet(factory.createPlantDisplay(crop.getType(), crop.getStage()), true);
        stand.getPersistentDataContainer().set(revisionKey, PersistentDataType.INTEGER, 7);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent e) {
        ChunkCoord coord = ChunkCoord.of(e.getChunk());
        Set<PlantedCrop> crops = cropsByChunk.get(coord);
        if (crops == null || crops.isEmpty()) return;

        cleanOrphanCropEntities(e.getChunk());

        for (PlantedCrop crop : crops) {
            getOrSpawnDisplay(crop);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldLoad(org.bukkit.event.world.WorldLoadEvent e) {
        World world = e.getWorld();
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, DeferredCrop> entry : deferredCrops.entrySet()) {
            String key = entry.getKey();
            DeferredCrop d = entry.getValue();
            Location loc = parseKey(key);
            if (loc != null && loc.getWorld().equals(world)) {
                toRemove.add(key);
                CropType type = CropType.fromId(d.typeId());
                if (type == null) continue;
                PlantedCrop crop = new PlantedCrop(loc, type, d.stage(), d.plantedAt(), d.displayUuid());
                plantedCrops.put(locKey(loc), crop);
                addCropToChunkIndex(crop);
                if (d.displayUuid() != null) entityUuidToCrop.put(d.displayUuid(), crop);
                if (!crop.isMature()) {
                    int targetStage = crop.calculateTargetStage();
                    if (targetStage > crop.getStage()) {
                        crop.setStage(targetStage);
                        dirty.set(true);
                    }
                    if (!crop.isMature()) {
                        scheduleGrowth(crop);
                    }
                }
                if (world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                    getOrSpawnDisplay(crop);
                }
            }
        }
        for (String key : toRemove) {
            deferredCrops.remove(key);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent e) {
        ChunkCoord coord = ChunkCoord.of(e.getChunk());
        Set<PlantedCrop> crops = cropsByChunk.get(coord);
        if (crops == null || crops.isEmpty()) return;

        for (PlantedCrop crop : crops) {
            UUID id = crop.getStandUuid();
            if (id != null) {
                entityUuidToCrop.remove(id);
                Entity ent = Bukkit.getEntity(id);
                if (ent != null) {
                    ent.remove();
                }
                crop.setStandUuid(null);
            }
        }
    }

    private void cleanOrphanCropEntities(Chunk chunk) {
        Set<String> seenCropKeys = new HashSet<>();
        for (Entity entity : chunk.getEntities()) {
            if (entity.getPersistentDataContainer().has(cropEntityKey, PersistentDataType.STRING)) {
                if (entity instanceof Interaction || entity instanceof ItemDisplay) {
                    entityUuidToCrop.remove(entity.getUniqueId());
                    entity.remove();
                    continue;
                }
                if (entity instanceof ArmorStand) {
                    String key = entity.getPersistentDataContainer().get(cropEntityKey, PersistentDataType.STRING);
                    if (key != null && !plantedCrops.containsKey(key) && !deferredCrops.containsKey(key)) {
                        Location loc = parseKey(key);
                        if (loc != null && (plantedCrops.containsKey(locKey(loc)) || deferredCrops.containsKey(locKey(loc)))) {
                            key = locKey(loc);
                        }
                    }
                    if (key == null || (!plantedCrops.containsKey(key) && !deferredCrops.containsKey(key)) || !seenCropKeys.add(key)) {
                        entityUuidToCrop.remove(entity.getUniqueId());
                        entity.remove();
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlant(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        Block clicked = e.getClickedBlock();
        if (clicked == null) return;

        // Block bone meal if clicking farmland or crop
        ItemStack hand = e.getItem();
        if (hand != null && hand.getType() == Material.BONE_MEAL) {
            Block cropBlock = clicked.getType() == Material.FARMLAND ? clicked.getRelative(BlockFace.UP) : clicked;
            if (getCropAt(cropBlock.getLocation()) != null) {
                e.setCancelled(true);
                e.getPlayer().getWorld().playSound(e.getPlayer().getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                e.getPlayer().sendActionBar(Component.text("✦ พืชเวทมนตร์ Evergarden ไม่สามารถเร่งโตด้วย Bone Meal ได้ (ต้องเติบโตตามกาลเวลา)", NamedTextColor.RED));
                return;
            }
        }

        if (clicked.getType() != Material.FARMLAND) return;
        if (e.getBlockFace() != BlockFace.UP) return;

        CropType cropType = factory.getSeedType(hand);
        if (cropType == null) return;

        Block above = clicked.getRelative(BlockFace.UP);
        if (above.getType() != Material.AIR && above.getType() != Material.CAVE_AIR && above.getType() != Material.VOID_AIR) {
            return;
        }

        if (plantedCrops.containsKey(locKey(above.getLocation()))) return;

        Player p = e.getPlayer();
        org.bukkit.event.block.BlockPlaceEvent placeEvent = new org.bukkit.event.block.BlockPlaceEvent(above, above.getState(), clicked, hand, p, true, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled() || !placeEvent.canBuild()) {
            return;
        }

        e.setCancelled(true);

        // Leave block above as AIR (NO tripwire string!)
        above.setType(Material.AIR, false);

        // Single ArmorStand with custom helmet model and hitbox for clicks
        Location displayLoc = above.getLocation().add(0.5, CROP_STAND_Y, 0.5);
        ArmorStand display = above.getWorld().spawn(displayLoc, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setSmall(true);
            stand.setMarker(false);
            stand.setCollidable(false);
            for (ArmorStand.LockType lock : ArmorStand.LockType.values()) stand.addEquipmentLock(EquipmentSlot.HEAD, lock);
            stand.setGravity(false);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setSilent(true);
            stand.setInvulnerable(true);
            stand.setPersistent(false);
            stand.getEquipment().setHelmet(factory.createPlantDisplay(cropType, 0), true);
            stand.getPersistentDataContainer().set(cropEntityKey, PersistentDataType.STRING, locKey(above.getLocation()));
        });

        PlantedCrop crop = new PlantedCrop(above.getLocation(), cropType, 0, System.currentTimeMillis(), display.getUniqueId());
        plantedCrops.put(locKey(above.getLocation()), crop);
        addCropToChunkIndex(crop);
        entityUuidToCrop.put(display.getUniqueId(), crop);
        scheduleGrowth(crop);

        if (p.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
        }

        above.getWorld().playSound(displayLoc, Sound.ITEM_CROP_PLANT, 1.0f, 1.1f);
        above.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, displayLoc, 10, 0.3, 0.3, 0.3, 0.05);

        p.sendActionBar(Component.text("🌱 ปลูก " + cropType.thaiName + " สำเร็จ! (โตเต็มที่ใน " + cropType.tier.formattedTime() + ")", NamedTextColor.GREEN));
        dirty.set(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractFarmland(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        Block clicked = e.getClickedBlock();
        if (clicked == null) return;

        Block cropBlock = clicked.getType() == Material.FARMLAND ? clicked.getRelative(BlockFace.UP) : clicked;
        PlantedCrop crop = getCropAt(cropBlock.getLocation());
        if (crop == null) return;

        e.setCancelled(true);
        Player p = e.getPlayer();
        ItemStack hand = e.getItem();

        // Bone Meal is completely disabled!
        if (hand != null && hand.getType() == Material.BONE_MEAL) {
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            p.sendActionBar(Component.text("✦ พืชเวทมนตร์ Evergarden ไม่สามารถเร่งโตด้วย Bone Meal ได้ (ต้องเติบโตตามกาลเวลา)", NamedTextColor.RED));
            return;
        }

        // Astral Dust: accelerates crop growth by 5% (Evergarden dimension only)
        if (hand != null && plugin.relics() != null && plugin.relics().isAstralDust(hand)) {
            applyAstralDust(p, crop, hand);
            return;
        }

        if (crop.isMature()) {
            if (hand != null && com.example.voidscape.enchant.EnchantApplyListener.hasUnique(hand, com.example.voidscape.enchant.UniqueEnchant.DEMETER_SCYTHE) && plugin.abilities() != null) {
                plugin.abilities().harvestCropsArea(p, cropBlock);
            } else {
                harvest(crop, p, false);
            }
        } else {
            p.sendActionBar(Component.text("⏳ " + crop.getType().thaiName + " กำลังเติบโต (" + (int)(crop.growthProgress() * 100) + "% · เหลือ " + crop.secondsRemaining() + " วินาที)", NamedTextColor.YELLOW));
            cropBlock.getWorld().playSound(cropBlock.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.6f, 1.5f);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractAtEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Entity target = e.getRightClicked();
        PlantedCrop found = entityUuidToCrop.get(target.getUniqueId());
        if (found == null) return;

        e.setCancelled(true);
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItem(e.getHand());

        // Bone Meal is completely disabled!
        if (hand != null && hand.getType() == Material.BONE_MEAL) {
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            p.sendActionBar(Component.text("✦ พืชเวทมนตร์ Evergarden ไม่สามารถเร่งโตด้วย Bone Meal ได้ (ต้องเติบโตตามกาลเวลา)", NamedTextColor.RED));
            return;
        }

        // Astral Dust: accelerates crop growth by 5% (Evergarden dimension only)
        if (hand != null && plugin.relics() != null && plugin.relics().isAstralDust(hand)) {
            applyAstralDust(p, found, hand);
            return;
        }

        if (found.isMature()) {
            if (hand != null && com.example.voidscape.enchant.EnchantApplyListener.hasUnique(hand, com.example.voidscape.enchant.UniqueEnchant.DEMETER_SCYTHE) && plugin.abilities() != null) {
                plugin.abilities().harvestCropsArea(p, found.getLocation().getBlock());
            } else {
                harvest(found, p, false);
            }
        } else {
            p.sendActionBar(Component.text("⏳ " + found.getType().thaiName + " กำลังเติบโต (" + (int)(found.growthProgress() * 100) + "% · เหลือ " + found.secondsRemaining() + " วินาที)", NamedTextColor.YELLOW));
            found.getLocation().getWorld().playSound(found.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.6f, 1.5f);
        }
    }

    public boolean applyAstralDust(Player player, PlantedCrop crop, ItemStack hand) {
        if (crop == null || player == null || hand == null) return false;
        if (plugin.relics() == null || !plugin.relics().isAstralDust(hand)) return false;

        World world = crop.getLocation().getWorld();
        if (world == null) return false;

        if (crop.isMature()) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            player.sendActionBar(Component.text("✦ " + crop.getType().thaiName + " โตเต็มที่แล้ว (พร้อมเก็บเกี่ยว)", NamedTextColor.GREEN));
            return true;
        }

        // Accelerate 5% of total growth duration
        int boostSeconds = Math.max(1, (int) Math.round(crop.getType().tier.growthSeconds * 0.05));
        crop.accelerate(boostSeconds);

        // Update stage if threshold passed
        int newStage = crop.calculateTargetStage();
        if (newStage > crop.getStage()) {
            crop.setStage(newStage);
            ArmorStand stand = getOrSpawnDisplay(crop);
            if (stand != null) {
                stand.getEquipment().setHelmet(factory.createPlantDisplay(crop.getType(), newStage), true);
            }
        }
        dirty.set(true);
        scheduleGrowth(crop);

        // 50% chance to consume the Astral Dust
        boolean consumed = false;
        if (player.getGameMode() != GameMode.CREATIVE && ThreadLocalRandom.current().nextBoolean()) {
            hand.setAmount(hand.getAmount() - 1);
            consumed = true;
        }

        Location loc = crop.getLocation().add(0.5, 0.4, 0.5);
        world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.6f);
        world.playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.4f);
        world.spawnParticle(Particle.FIREWORK, loc, 8, 0.25, 0.25, 0.25, 0.04);
        world.spawnParticle(Particle.HAPPY_VILLAGER, loc, 6, 0.25, 0.25, 0.25, 0.02);

        if (crop.isMature()) {
            String suffix = consumed ? " (ผงถูกใช้ไป)" : " (ผงยังคงพลัง \u2726)";
            player.sendActionBar(Component.text("\u2728 ผงละอองดาวเร่งโต " + crop.getType().thaiName + " +5%! (\u2605 โตเต็มที่แล้ว!)" + suffix, NamedTextColor.GOLD));
        } else {
            int pct = (int) (crop.growthProgress() * 100);
            String suffix = consumed ? " · ผงถูกใช้ไป" : " · \u2726 ผงยังคงพลัง";
            player.sendActionBar(Component.text("\u2728 ผงละอองดาวเร่งโต " + crop.getType().thaiName + " +5% (" + pct + "% · เหลือ " + crop.secondsRemaining() + " วินาที" + suffix + ")", NamedTextColor.AQUA));
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageCrop(EntityDamageByEntityEvent e) {
        Entity target = e.getEntity();
        if (!(target instanceof ArmorStand) && !(target instanceof Interaction) && !(target instanceof ItemDisplay)) return;

        PlantedCrop found = entityUuidToCrop.get(target.getUniqueId());
        if (found == null) return;

        e.setCancelled(true);
        Player p = e.getDamager() instanceof Player pl ? pl : null;
        if (p != null) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand != null && com.example.voidscape.enchant.EnchantApplyListener.hasUnique(hand, com.example.voidscape.enchant.UniqueEnchant.DEMETER_SCYTHE) && plugin.abilities() != null) {
                plugin.abilities().harvestCropsArea(p, found.getLocation().getBlock());
                return;
            }
        }
        harvest(found, p, true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFarmlandBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() == Material.FARMLAND) {
            PlantedCrop cropAbove = getCropAt(b.getRelative(BlockFace.UP).getLocation());
            if (cropAbove != null) {
                harvest(cropAbove, e.getPlayer(), true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFarmlandTrample(PlayerInteractEvent e) {
        if (e.getAction() != Action.PHYSICAL) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.FARMLAND) return;
        if (getCropAt(b.getRelative(BlockFace.UP).getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobTrample(EntityInteractEvent e) {
        Block b = e.getBlock();
        if (b.getType() == Material.FARMLAND && getCropAt(b.getRelative(BlockFace.UP).getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFarmlandFade(BlockFadeEvent e) {
        Block b = e.getBlock();
        if (b.getType() == Material.FARMLAND && getCropAt(b.getRelative(BlockFace.UP).getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMoistureChange(MoistureChangeEvent e) {
        Block b = e.getBlock();
        if (getCropAt(b.getRelative(BlockFace.UP).getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFertilize(BlockFertilizeEvent e) {
        Block b = e.getBlock();
        if (plugin.world() != null && b.getWorld().equals(plugin.world())) {
            e.setCancelled(true);
            if (e.getPlayer() != null) {
                e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                e.getPlayer().sendActionBar(Component.text("✦ พืชเวทมนตร์ Evergarden ไม่สามารถเร่งโตด้วย Bone Meal หรือปุ๋ยได้ (ต้องเติบโตตามกาลเวลา)", NamedTextColor.RED));
            }
            return;
        }
        if (getCropAt(b.getLocation()) != null || getCropAt(b.getRelative(BlockFace.UP).getLocation()) != null) {
            e.setCancelled(true);
            if (e.getPlayer() != null) {
                e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                e.getPlayer().sendActionBar(Component.text("✦ พืชเวทมนตร์ Evergarden ไม่สามารถเร่งโตด้วย Bone Meal หรือปุ๋ยได้ (ต้องเติบโตตามกาลเวลา)", NamedTextColor.RED));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent e) {
        if (e.getItem() != null && e.getItem().getType() == Material.BONE_MEAL) {
            if (plugin.world() != null && e.getBlock().getWorld().equals(plugin.world())) {
                e.setCancelled(true);
                return;
            }
            if (e.getBlock().getBlockData() instanceof org.bukkit.block.data.Directional dir) {
                Block target = e.getBlock().getRelative(dir.getFacing());
                if (getCropAt(target.getLocation()) != null || getCropAt(target.getRelative(BlockFace.UP).getLocation()) != null) {
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreakWildFlora(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getWorld() != plugin.world()) return;
        Player player = e.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) return;

        Material mat = b.getType();
        boolean isFlora = mat == Material.SHORT_GRASS || mat == Material.TALL_GRASS
            || mat == Material.FERN || mat == Material.LARGE_FERN
            || mat == Material.PINK_PETALS || Tag.FLOWERS.isTagged(mat);

        if (isFlora) {
            double roll = java.util.concurrent.ThreadLocalRandom.current().nextDouble();
            Location dropLoc = b.getLocation().add(0.5, 0.3, 0.5);

            // 12% chance for Tier 1 seed
            if (roll < 0.12 && factory != null) {
                CropType[] tier1 = Arrays.stream(CropType.values())
                    .filter(c -> c.tier == CropTier.TIER_1)
                    .toArray(CropType[]::new);
                CropType picked = tier1[java.util.concurrent.ThreadLocalRandom.current().nextInt(tier1.length)];
                ItemStack seed = factory.createSeed(picked, 1);
                b.getWorld().dropItemNaturally(dropLoc, seed);
                b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, dropLoc, 8, 0.3, 0.3, 0.3, 0.05);
                player.playSound(dropLoc, Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1.0f, 1.4f);
                player.sendActionBar(Component.text("🌿 ค้นพบเมล็ดพันธุ์ลอยฟ้า: " + picked.thaiName + "!", NamedTextColor.GREEN));
            }
            // 4% chance for Tier 2 seed (harder, 12–16% window)
            else if (roll < 0.16 && factory != null) {
                CropType[] tier2 = Arrays.stream(CropType.values())
                    .filter(c -> c.tier == CropTier.TIER_2)
                    .toArray(CropType[]::new);
                CropType picked = tier2[java.util.concurrent.ThreadLocalRandom.current().nextInt(tier2.length)];
                ItemStack seed = factory.createSeed(picked, 1);
                b.getWorld().dropItemNaturally(dropLoc, seed);
                b.getWorld().spawnParticle(Particle.CRIT, dropLoc, 12, 0.3, 0.3, 0.3, 0.08);
                b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, dropLoc, 6, 0.2, 0.2, 0.2, 0.04);
                player.playSound(dropLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.6f);
                player.playSound(dropLoc, Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 0.8f, 1.2f);
                player.sendActionBar(Component.text("✨ โชคดี! ค้นพบเมล็ดพันธุ์หายาก Tier II: " + picked.thaiName + "!", NamedTextColor.GOLD));
            }
            // 8% chance for Astral Dust (16–24% window)
            else if (roll < 0.24 && plugin.relics() != null) {
                ItemStack dust = plugin.relics().createAstralDust(1);
                b.getWorld().dropItemNaturally(dropLoc, dust);
                b.getWorld().spawnParticle(Particle.FIREWORK, dropLoc, 6, 0.2, 0.2, 0.2, 0.05);
                player.playSound(dropLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.8f);
                player.sendActionBar(Component.text("✦ ค้นพบละอองดาว (Astral Dust) ในพุ่มพฤกษา!", NamedTextColor.AQUA));
            }
        }
    }

    // Protection against liquids, pistons, and explosions
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent e) {
        Block to = e.getToBlock();
        PlantedCrop crop = getCropAt(to.getLocation());
        if (crop != null) {
            harvest(crop, null, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        handlePiston(e.getBlocks());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        handlePiston(e.getBlocks());
    }

    private void handlePiston(List<Block> blocks) {
        for (Block b : blocks) {
            PlantedCrop crop = getCropAt(b.getLocation());
            if (crop != null) {
                harvest(crop, null, true);
            }
            if (b.getType() == Material.FARMLAND) {
                PlantedCrop cropAbove = getCropAt(b.getRelative(BlockFace.UP).getLocation());
                if (cropAbove != null) {
                    harvest(cropAbove, null, true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        handleExplosion(e.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        handleExplosion(e.blockList());
    }

    private void handleExplosion(List<Block> blocks) {
        for (Block b : blocks) {
            PlantedCrop crop = getCropAt(b.getLocation());
            if (crop != null) {
                harvest(crop, null, true);
            }
            if (b.getType() == Material.FARMLAND) {
                PlantedCrop cropAbove = getCropAt(b.getRelative(BlockFace.UP).getLocation());
                if (cropAbove != null) {
                    harvest(cropAbove, null, true);
                }
            }
        }
    }

    public void harvest(PlantedCrop crop, Player player, boolean isBreak) {
        if (crop == null) return;
        String key = locKey(crop.getLocation());
        if (!plantedCrops.containsKey(key)) return;

        if (player != null) {
            org.bukkit.event.block.BlockBreakEvent breakEvent = new org.bukkit.event.block.BlockBreakEvent(crop.getLocation().getBlock(), player);
            Bukkit.getPluginManager().callEvent(breakEvent);
            if (breakEvent.isCancelled()) return;
        }

        Location loc = crop.getLocation();
        World w = loc.getWorld();
        if (w == null) return;
        Location dropLoc = loc.clone().add(0.5, 0.3, 0.5);

        // Hoe handling
        ItemStack tool = player != null ? player.getInventory().getItemInMainHand() : null;
        boolean isHoe = tool != null && (tool.getType().name().endsWith("_HOE") || Tag.ITEMS_HOES.isTagged(tool.getType()));

        if (isHoe && player != null && player.getGameMode() != GameMode.CREATIVE) {
            if (tool.getItemMeta() instanceof Damageable d) {
                d.setDamage(d.getDamage() + 1);
                tool.setItemMeta(d);
            }
            w.playSound(dropLoc, Sound.ITEM_HOE_TILL, 0.9f, 1.2f);
        }

        boolean hasTelepathy = player != null && tool != null && com.example.voidscape.enchant.EnchantApplyListener.hasUnique(tool, com.example.voidscape.enchant.UniqueEnchant.TELEPATHY);

        if (crop.isMature()) {
            int fortune = 0;
            if (tool != null) {
                if (plugin.relics() != null) {
                    fortune = plugin.relics().getLimitBreakLevel(tool, com.example.voidscape.enchant.LimitBreakType.FORTUNE);
                }
                if (fortune <= 0) {
                    fortune = tool.getEnchantmentLevel(Enchantment.FORTUNE);
                }
            }

            int extraFood = 0;
            if (fortune > 3) {
                extraFood = 1 + (int) (Math.random() * (fortune - 2));
            } else if (fortune > 0) {
                extraFood = Math.random() < (0.4 + fortune * 0.15) ? 1 : 0;
            }
            int foodCount = 1 + extraFood;

            int extraSeed = 0;
            if (fortune > 3) {
                extraSeed = (int) (Math.random() * (fortune - 2));
            } else if (fortune > 0) {
                extraSeed = Math.random() < (0.3 + fortune * 0.1) ? 1 : 0;
            }
            int seedCount = 1 + extraSeed;
            ItemStack seed = factory.createSeed(crop.getType(), seedCount);
            ItemStack produce = factory.createFood(crop.getType(), foodCount);

            giveOrDrop(player, dropLoc, seed, hasTelepathy);
            giveOrDrop(player, dropLoc, produce, hasTelepathy);

            w.playSound(dropLoc, Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1.0f, 1.2f);
            w.playSound(dropLoc, Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.4f);
            spawnMatureParticles(crop);

            if (player != null) {
                player.sendActionBar(Component.text("🌾 เก็บเกี่ยว " + crop.getType().thaiName + " ได้รับ x" + foodCount + " ชิ้น!", NamedTextColor.GREEN));
            }

            if (!isBreak) {
                // Auto-replant at Stage 0 with fresh timestamp!
                crop.setStage(0);
                crop.setPlantedAt(System.currentTimeMillis());
                ArmorStand display = getOrSpawnDisplay(crop);
                if (display != null) {
                    display.getEquipment().setHelmet(factory.createPlantDisplay(crop.getType(), 0), true);
                }
                scheduleGrowth(crop);
                dirty.set(true);
                return;
            }
        } else {
            // Unripe break
            ItemStack seed = factory.createSeed(crop.getType(), 1);
            giveOrDrop(player, dropLoc, seed, hasTelepathy);
            w.playSound(dropLoc, Sound.BLOCK_CROP_BREAK, 1.0f, 0.9f);
            if (player != null) {
                player.sendActionBar(Component.text("⏳ พืชยังไม่โตเต็มที่ (ได้รับเมล็ดคืน)", NamedTextColor.GRAY));
            }
        }

        // Cleanup entities and mapping
        plantedCrops.remove(key);
        removeCropFromChunkIndex(crop);
        removeEntities(crop);
        loc.getBlock().setType(Material.AIR, false);
        dirty.set(true);
    }

    private void giveOrDrop(Player player, Location dropLoc, ItemStack item, boolean telepathy) {
        if (item == null || item.getAmount() <= 0) return;
        World w = dropLoc.getWorld();
        if (w == null) return;
        if (telepathy && player != null) {
            var leftover = player.getInventory().addItem(item);
            if (leftover.isEmpty()) return;
            for (ItemStack rem : leftover.values()) {
                w.dropItemNaturally(dropLoc, rem);
            }
        } else {
            w.dropItemNaturally(dropLoc, item);
        }
    }

    private void removeEntities(PlantedCrop crop) {
        if (crop.getStandUuid() != null) {
            entityUuidToCrop.remove(crop.getStandUuid());
            Entity ent = Bukkit.getEntity(crop.getStandUuid());
            if (ent != null) ent.remove();
            crop.setStandUuid(null);
        }
        Location center = crop.getLocation().add(0.5, 0.45, 0.5);
        World world = crop.getLocation().getWorld();
        if (world != null && world.isChunkLoaded(crop.getLocation().getBlockX() >> 4, crop.getLocation().getBlockZ() >> 4)) {
            for (Entity nearby : world.getNearbyEntities(center, 0.9, 1.3, 0.9)) {
                if ((nearby instanceof ItemDisplay || nearby instanceof ArmorStand || nearby instanceof Interaction) &&
                    ownsCropEntity(nearby, crop)) {
                    entityUuidToCrop.remove(nearby.getUniqueId());
                    nearby.remove();
                }
            }
        }
    }

    public void markDirty() {
        dirty.set(true);
    }

    public void saveCropsAsync() {
        // Crop records are small; writing synchronously avoids a stale async
        // snapshot overwriting changes during shutdown or rapid planting.
        if (dirty.get()) saveCropsSync();
    }

    public void saveCropsSync() {
        writeCropsYaml(new HashMap<>(plantedCrops));
        dirty.set(false);
    }

    public void saveCrops() {
        saveCropsSync();
    }

    private void writeCropsYaml(Map<String, PlantedCrop> map) {
        try {
            File parent = saveFile.getParentFile();
            if (parent != null) Files.createDirectories(parent.toPath());
            YamlConfiguration cfg = new YamlConfiguration();
            for (Map.Entry<String, PlantedCrop> entry : map.entrySet()) {
                String key = entry.getKey();
                PlantedCrop c = entry.getValue();
                cfg.set(key + ".type", c.getType().id);
                cfg.set(key + ".stage", c.getStage());
                cfg.set(key + ".plantedAt", c.getPlantedAt());
                if (c.getStandUuid() != null) {
                    cfg.set(key + ".display", c.getStandUuid().toString());
                }
            }
            for (Map.Entry<String, DeferredCrop> entry : deferredCrops.entrySet()) {
                String key = entry.getKey();
                DeferredCrop c = entry.getValue();
                cfg.set(key + ".type", c.typeId());
                cfg.set(key + ".stage", c.stage());
                cfg.set(key + ".plantedAt", c.plantedAt());
                if (c.displayUuid() != null) {
                    cfg.set(key + ".display", c.displayUuid().toString());
                }
            }
            cfg.save(saveFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save crops.yml: " + e.getMessage());
        }
    }

    public void loadCrops() {
        plantedCrops.clear();
        deferredCrops.clear();
        entityUuidToCrop.clear();
        cropsByChunk.clear();
        synchronized (this) {
            growthQueue.clear();
        }
        if (!saveFile.exists()) return;
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(saveFile);
            for (String key : cfg.getKeys(false)) {
                String typeId = cfg.getString(key + ".type");
                CropType type = CropType.fromId(typeId);
                if (type == null) continue;

                int stage = cfg.getInt(key + ".stage", 0);
                long plantedAt = cfg.getLong(key + ".plantedAt", System.currentTimeMillis());
                String dUuidStr = cfg.getString(key + ".display");
                UUID displayUuid = null;
                if (dUuidStr != null) {
                    try {
                        displayUuid = UUID.fromString(dUuidStr);
                    } catch (IllegalArgumentException ignored) {}
                }

                Location loc = parseKey(key);
                if (loc == null) {
                    deferredCrops.put(key, new DeferredCrop(key, typeId, stage, plantedAt, displayUuid));
                    continue;
                }

                PlantedCrop crop = new PlantedCrop(loc, type, stage, plantedAt, displayUuid);
                plantedCrops.put(locKey(loc), crop);
                addCropToChunkIndex(crop);
                if (displayUuid != null) entityUuidToCrop.put(displayUuid, crop);

                // Catch-up stage or schedule growth
                if (!crop.isMature()) {
                    int targetStage = crop.calculateTargetStage();
                    if (targetStage > crop.getStage()) {
                        crop.setStage(targetStage);
                        dirty.set(true);
                    }
                    if (!crop.isMature()) {
                        scheduleGrowth(crop);
                    }
                }
            }
            plugin.getLogger().info("Loaded " + plantedCrops.size() + " planted crops (" + deferredCrops.size() + " deferred) from crops.yml");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load crops.yml: " + e.getMessage());
        }

        // Restore/spawn displays for crops in already loaded chunks
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                ChunkCoord coord = ChunkCoord.of(chunk);
                Set<PlantedCrop> crops = cropsByChunk.get(coord);
                if (crops != null && !crops.isEmpty()) {
                    cleanOrphanCropEntities(chunk);
                    for (PlantedCrop crop : crops) {
                        getOrSpawnDisplay(crop);
                    }
                }
            }
        }
    }

    @Override
    public void close() {
        // Always flush the complete in-memory set before entities are removed.
        saveCropsSync();
        for (PlantedCrop crop : plantedCrops.values()) removeEntities(crop);
        cropsByChunk.clear();
        synchronized (this) {
            growthQueue.clear();
        }
    }
}
