package com.example.voidscape.world;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.bukkit.loot.LootTables;
import org.bukkit.persistence.PersistentDataType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * Boss-arena sanctuaries for the Nether and the End. Each kind is an authored ODS1 asset (a dense block volume that carries its
 * own cavern shell or island) that is pasted at a PLANNED site: x, y, z and entrance direction are chosen once from the world
 * seed, the real base terrain and the vanilla structure layout, then written to a ledger. Generation, /sanctuary locate and
 * /sanctuary tp all read that one plan. No chunk is ever loaded by the planner and a chunk only paints its own blocks.
 *
 * No boss, spawner or encounter is attached here; {@link #siteAt} and {@link Arena} are the hooks for later.
 */
public final class DimensionalSanctuaries implements Listener {
    public enum Kind {
        CROWN_FORTRESS("crown-fortress", World.Environment.NETHER, 2048, 400),
        BONE_TEMPLE("bone-temple", World.Environment.NETHER, 2048, 400),
        CRYSTAL_CITY("crystal-city", World.Environment.THE_END, 3072, 1250),
        VOID_RING("void-ring", World.Environment.THE_END, 3072, 1250);
        public final String id; public final World.Environment environment; public final int regionBlocks, minOriginDistance;
        Kind(String id, World.Environment environment, int regionBlocks, int minOriginDistance) {
            this.id = id; this.environment = environment; this.regionBlocks = regionBlocks; this.minOriginDistance = minOriginDistance;
        }
        public static Kind parse(String id) { for (Kind k : values()) if (k.id.equalsIgnoreCase(id)) return k; return null; }
    }
    public record Site(Kind kind, int x, int y, int z, int rotation, long seed, int version) {}
    public record Arena(int x, int y, int z, int radius, int height) {}
    public record Point(int x, int y, int z) {}

    /** Rotation by quarter turns clockwise seen from above: east becomes south. */
    private static int[] rotate(int x, int z, int quarter) {
        for (int i = 0; i < (quarter & 3); i++) { int t = x; x = -z; z = t; }
        return new int[]{x, z};
    }
    private static int[] unrotate(int x, int z, int quarter) {
        for (int i = 0; i < (quarter & 3); i++) { int t = x; x = z; z = -t; }
        return new int[]{x, z};
    }
    private static long key(int x, int z) { return ((long) x << 32) | (z & 0xffffffffL); }

    /** A dense, immutable block volume. Index = ((x * nz) + z) * ny + y. Palette 0 keeps natural terrain, 1 is air. */
    static final class Asset {
        final Kind kind; final int version, minX, minY, minZ, nx, ny, nz, top, bottom, reach;
        final short[] data; final String[] palette; final BlockData[][] blocks = new BlockData[4][];
        final List<int[]> chests = new ArrayList<>(); final YamlConfiguration meta;
        final int arenaX, arenaY, arenaZ, arenaRadius, arenaHeight; final int[] landing, mouth;

        Asset(Kind kind, int version, InputStream resource) throws IOException {
            this.kind = kind; this.version = version;
            try (var in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(resource), 1 << 16))) {
                if (in.readInt() != 0x4f445331) throw new IOException("Unsupported sanctuary asset " + kind.id);
                minX = in.readShort(); minY = in.readShort(); minZ = in.readShort();
                nx = in.readShort(); ny = in.readShort(); nz = in.readShort();
                if (nx < 1 || ny < 1 || nz < 1 || (long) nx * ny * nz > 8_000_000L) throw new IOException("Invalid asset size");
                palette = new String[in.readUnsignedShort()];
                for (int i = 0; i < palette.length; i++) {
                    byte[] bytes = new byte[in.readUnsignedShort()]; in.readFully(bytes);
                    palette[i] = new String(bytes, StandardCharsets.UTF_8);
                }
                int runs = in.readInt(); data = new short[nx * ny * nz]; int at = 0;
                for (int i = 0; i < runs; i++) {
                    int value = in.readUnsignedShort(), count = in.readUnsignedShort();
                    if (value >= palette.length || at + count > data.length) throw new IOException("Corrupt asset run");
                    Arrays.fill(data, at, at + count, (short) value); at += count;
                }
                if (at != data.length) throw new IOException("Truncated asset data");
                byte[] json = new byte[in.readInt()]; in.readFully(json);
                meta = new YamlConfiguration();
                try { meta.loadFromString(new String(json, StandardCharsets.UTF_8)); }
                catch (org.bukkit.configuration.InvalidConfigurationException ex) { throw new IOException("Bad asset metadata", ex); }
            }
            var base = new BlockData[palette.length];
            for (int i = 2; i < palette.length; i++) base[i] = Bukkit.createBlockData(palette[i]);
            base[1] = Bukkit.createBlockData(Material.AIR);
            blocks[0] = base;
            StructureRotation[] turns = {StructureRotation.NONE, StructureRotation.CLOCKWISE_90, StructureRotation.CLOCKWISE_180, StructureRotation.COUNTERCLOCKWISE_90};
            for (int r = 1; r < 4; r++) {
                var rotated = new BlockData[palette.length];
                for (int i = 1; i < palette.length; i++) { rotated[i] = base[i].clone(); rotated[i].rotate(turns[r]); }
                blocks[r] = rotated;
            }
            top = meta.getInt("top"); bottom = meta.getInt("bottom");
            arenaX = meta.getInt("arena.x"); arenaY = meta.getInt("arena.y"); arenaZ = meta.getInt("arena.z");
            arenaRadius = meta.getInt("arena.radius"); arenaHeight = meta.getInt("arena.height");
            landing = new int[]{meta.getInt("landing.x"), meta.getInt("landing.y"), meta.getInt("landing.z")};
            mouth = new int[]{meta.getInt("mouth.x"), meta.getInt("mouth.y"), meta.getInt("mouth.z")};
            for (var row : meta.getList("chests", List.of())) {
                var list = (List<?>) row;
                chests.add(new int[]{((Number) list.get(0)).intValue(), ((Number) list.get(1)).intValue(), ((Number) list.get(2)).intValue(), ((Number) list.get(3)).intValue()});
            }
            reach = Math.max(Math.max(Math.abs(minX), Math.abs(minX + nx - 1)), Math.max(Math.abs(minZ), Math.abs(minZ + nz - 1))) + 1;
        }
        int at(int x, int y, int z) { return ((x - minX) * nz + (z - minZ)) * ny + (y - minY); }
        /** Block-offset bounding box {minDx, maxDx, minDz, maxDz} after rotation. */
        int[] bounds(int r) {
            int[] a = rotate(minX, minZ, r), b = rotate(minX + nx - 1, minZ + nz - 1, r);
            return new int[]{Math.min(a[0], b[0]), Math.max(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[1], b[1])};
        }
    }

    private static final int MAX_VERSION = 9;
    private final VoidscapePlugin plugin;
    private final EnumMap<Kind, Map<Integer, Asset>> assets = new EnumMap<>(Kind.class);
    private final Map<UUID, Placement> worlds = new ConcurrentHashMap<>();
    private final File ledger; private final YamlConfiguration saved;
    private final Queue<String[]> pendingWrites = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final Map<String, Integer> due = new ConcurrentHashMap<>();
    private final Set<String> edited = ConcurrentHashMap.newKeySet();
    private final org.bukkit.NamespacedKey sealKey;
    private final org.bukkit.NamespacedKey editedKey;
    private boolean closed;
    private static final EnumSet<Material> NETHER_INTRUDERS = EnumSet.of(Material.LAVA, Material.FIRE, Material.SOUL_FIRE, Material.GLOWSTONE,
            Material.NETHERRACK, Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE, Material.GRAVEL, Material.SOUL_SAND, Material.SOUL_SOIL,
            Material.MAGMA_BLOCK, Material.BASALT, Material.BLACKSTONE, Material.CRIMSON_NYLIUM, Material.WARPED_NYLIUM, Material.NETHER_SPROUTS,
            Material.CRIMSON_ROOTS, Material.WARPED_ROOTS, Material.WEEPING_VINES, Material.TWISTING_VINES, Material.WEEPING_VINES_PLANT,
            Material.TWISTING_VINES_PLANT, Material.BROWN_MUSHROOM, Material.RED_MUSHROOM, Material.CRIMSON_FUNGUS, Material.WARPED_FUNGUS,
            Material.NETHER_WART_BLOCK, Material.WARPED_WART_BLOCK, Material.ANCIENT_DEBRIS, Material.SMOOTH_BASALT, Material.NETHER_WART,
            // Soul sand valley fossils, huge fungus stems and lights, ruined portals
            Material.BONE_BLOCK, Material.CRIMSON_STEM, Material.WARPED_STEM, Material.SHROOMLIGHT, Material.OBSIDIAN, Material.CRYING_OBSIDIAN);
    private static final EnumSet<Material> END_INTRUDERS = EnumSet.of(Material.CHORUS_PLANT, Material.CHORUS_FLOWER, Material.END_STONE);

    public DimensionalSanctuaries(VoidscapePlugin plugin) throws IOException {
        this.plugin = plugin;
        sealKey = plugin.key("ods_seal");
        editedKey = plugin.key("ods_edited");
        for (Kind kind : Kind.values()) {
            var versions = new HashMap<Integer, Asset>();
            for (int v = 1; v <= MAX_VERSION; v++) {
                InputStream in = plugin.getResource("dimensional-sanctuaries/v" + v + "/" + kind.id.replace('-', '_') + ".ods");
                if (in != null) versions.put(v, new Asset(kind, v, in));
            }
            if (!versions.isEmpty()) assets.put(kind, versions);
        }
        ledger = new File(plugin.getDataFolder(), "dimensional-sanctuaries.yml");
        saved = YamlConfiguration.loadConfiguration(ledger);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (World world : Bukkit.getWorlds()) attach(world);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10, 10);
    }

    // ---------------------------------------------------------------- lifecycle
    @EventHandler public void init(WorldInitEvent event) {
        try { attach(event.getWorld()); }
        catch (IOException ex) { throw new IllegalStateException("Cannot preserve dimensional sanctuary generation history", ex); }
    }
    private static String root(World world) { return "worlds." + world.getUID(); }
    private List<Kind> kindsFor(World world) {
        var list = new ArrayList<Kind>();
        for (Kind kind : Kind.values()) if (kind.environment == world.getEnvironment() && assets.containsKey(kind)) list.add(kind);
        return list;
    }
    private void attach(World world) throws IOException {
        if (!plugin.getConfig().getBoolean("dimensional-sanctuaries.enabled", true) || worlds.containsKey(world.getUID())) return;
        if (world.getGenerator() != null || kindsFor(world).isEmpty()) return;
        var allowed = plugin.getConfig().getStringList("dimensional-sanctuaries.worlds");
        if (!allowed.isEmpty() && !allowed.contains(world.getName())) return;
        NoiseProbe probe;
        try { probe = NoiseProbe.create(world); }
        catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().severe("Dimensional sanctuaries disabled in " + world.getName() + ": cannot read base terrain (" + ex + ")");
            return;
        }
        String root = root(world);
        if (!saved.contains(root)) {
            // Terrain that already exists is never touched: any whole region file counts as visited.
            Set<Long> old = new TreeSet<>();
            boolean end = world.getEnvironment() == World.Environment.THE_END;
            String legacy = end ? "DIM1/region" : "DIM-1/region", modern = end ? "dimensions/minecraft/the_end/region" : "dimensions/minecraft/the_nether/region";
            Path folder = world.getWorldFolder().toPath();
            old.addAll(SkyPlacementHistory.capture(folder.resolve("region"), 32));
            old.addAll(SkyPlacementHistory.capture(folder.resolve(legacy), 32));
            old.addAll(SkyPlacementHistory.capture(folder.resolve(modern), 32));
            old.addAll(SkyPlacementHistory.captureWorld(Bukkit.getWorldContainer().toPath(), folder, world.getName(), 32));
            Path parent = folder.toAbsolutePath().normalize();
            for (int depth = 0; depth < 6 && parent != null; depth++, parent = parent.getParent()) {
                old.addAll(SkyPlacementHistory.capture(parent.resolve(legacy), 32));
                old.addAll(SkyPlacementHistory.capture(parent.resolve(modern), 32));
            }
            for (Chunk loaded : world.getLoadedChunks()) old.add(key(Math.floorDiv(loaded.getX(), 32), Math.floorDiv(loaded.getZ(), 32)));
            saved.set(root + ".name", world.getName()); saved.set(root + ".ledger-version", 1);
            saved.set(root + ".old-regions", new ArrayList<>(old));
            for (Kind kind : kindsFor(world)) saved.set(root + ".region-blocks." + kind.id,
                    Math.clamp(plugin.getConfig().getInt("dimensional-sanctuaries.region-blocks." + kind.id, kind.regionBlocks), 768, 16384) / 16 * 16);
            persist();
        }
        if (saved.getInt(root + ".ledger-version") != 1) throw new IOException("Unsupported dimensional sanctuary ledger version");
        Map<Kind, Integer> sizes = new EnumMap<>(Kind.class);
        for (Kind kind : kindsFor(world)) sizes.put(kind, saved.getInt(root + ".region-blocks." + kind.id, kind.regionBlocks) / 16 * 16);
        var placement = new Placement(world, probe, new HashSet<>(saved.getLongList(root + ".old-regions")), sizes);
        var section = saved.getConfigurationSection(root + ".plans");
        if (section != null) for (String id : section.getKeys(false)) placement.plans.put(id, section.getString(id, "-"));
        worlds.put(world.getUID(), placement); world.getPopulators().add(placement);
        plugin.getLogger().info("Dimensional sanctuaries attached to " + world.getName() + " (" + kindsFor(world).stream().map(k -> k.id).toList() + "); old regions: " + placement.oldRegions.size());
    }
    private void persist() throws IOException {
        Path target = ledger.toPath(), tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, saved.saveToString());
        try { Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ex) { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING); }
    }
    public void flush() {
        String[] write; boolean any = false;
        while ((write = pendingWrites.poll()) != null) { saved.set(write[0], write[1]); any = true; }
        if (any) try { persist(); } catch (IOException ex) { plugin.getLogger().warning("Cannot save dimensional sanctuary ledger: " + ex.getMessage()); }
    }
    public void close() {
        closed = true; flush(); due.clear(); edited.clear();
        for (var entry : worlds.entrySet()) {
            World world = Bukkit.getWorld(entry.getKey());
            if (world != null && !Bukkit.isStopping()) world.getPopulators().remove(entry.getValue());
        }
        worlds.clear();
    }
    public boolean enabled(World world) { return world != null && worlds.containsKey(world.getUID()); }

    // ---------------------------------------------------------------- public queries
    /** Nearest planned site of the given kind within {@code rings} regions around (x, z). */
    public Site findNearest(World world, Kind kind, int x, int z, int rings) {
        Placement p = world == null || kind == null ? null : worlds.get(world.getUID());
        return p == null || kind.environment != world.getEnvironment() || !assets.containsKey(kind) ? null : p.nearest(kind, x, z, rings);
    }
    /** The sanctuary whose footprint contains this block column, or null. Reads the ledger and plans; never loads chunks. */
    public Site siteAt(World world, int x, int z) {
        Placement p = world == null ? null : worlds.get(world.getUID());
        return p == null ? null : p.siteForChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16), false);
    }
    /** Boss-arena volume (centre of the floor layer, radius, clear height) in world coordinates. */
    public Arena arena(Site site) {
        Asset a = asset(site); int[] c = rotate(a.arenaX, a.arenaZ, site.rotation());
        return new Arena(site.x() + c[0], site.y() + a.arenaY, site.z() + c[1], a.arenaRadius, a.arenaHeight);
    }
    /** Where an administrator is placed: a walkable spot just inside the entrance. */
    public Point landing(Site site) {
        Asset a = asset(site); int[] c = rotate(a.landing[0], a.landing[2], site.rotation());
        return new Point(site.x() + c[0], site.y() + a.landing[1], site.z() + c[1]);
    }
    public Point entrance(Site site) {
        Asset a = asset(site); int[] c = rotate(a.mouth[0], a.mouth[2], site.rotation());
        return new Point(site.x() + c[0], site.y() + a.mouth[1], site.z() + c[1]);
    }
    /** Block-coordinate bounding box {minX, maxX, minY, maxY, minZ, maxZ} of the site in world space. */
    public int[] bounds(Site site) {
        Asset a = asset(site); int[] b = a.bounds(site.rotation());
        return new int[]{site.x() + b[0], site.x() + b[1], site.y() + a.bottom, site.y() + a.top, site.z() + b[2], site.z() + b[3]};
    }
    public List<Point> chestLocations(Site site) {
        Asset a = asset(site); List<Point> out = new ArrayList<>(a.chests.size());
        for (int[] c : a.chests) {
            int[] p = rotate(c[0], c[2], site.rotation());
            out.add(new Point(site.x() + p[0], site.y() + c[1], site.z() + p[1]));
        }
        return out;
    }
    private Asset asset(Site site) { return assets.get(site.kind()).get(site.version()); }

    private boolean authoredAt(World world, int wx, int wy, int wz) {
        Site site = siteAt(world, wx, wz); if (site == null) return false;
        Asset a = asset(site);
        int[] l = unrotate(wx - site.x(), wz - site.z(), site.rotation());
        int ly = wy - site.y();
        if (l[0] < a.minX || l[0] >= a.minX + a.nx || l[1] < a.minZ || l[1] >= a.minZ + a.nz || ly < a.minY || ly >= a.minY + a.ny) return false;
        return a.data[a.at(l[0], ly, l[1])] != 0;
    }

    public boolean protects(Block block) {
        return block != null && enabled(block.getWorld()) && authoredAt(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    public boolean protectedAt(Location loc) {
        return loc != null && loc.getWorld() != null && enabled(loc.getWorld())
                && authoredAt(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    // ---------------------------------------------------------------- commands
    /** /sanctuary locate|tp &lt;kind&gt; [regions] in the Nether or End. Supports cross-dimension lookup and teleport. */
    public boolean command(Player player, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("boss") && plugin.realmSanctuaryEncounters() != null) {
            return plugin.realmSanctuaryEncounters().command(player, args);
        }
        Kind kind = args.length > 1 ? Kind.parse(args[1]) : null;
        boolean teleport = args.length > 0 && args[0].equalsIgnoreCase("tp");
        if (args.length < 2 || args.length > 3 || kind == null || !(teleport || args[0].equalsIgnoreCase("locate"))) {
            player.sendMessage("/sanctuary locate|tp " + String.join("|", assets.keySet().stream().map(k -> k.id).sorted().toList()) + " [1-32 regions] หรือ /sanctuary boss [status|spawn|reset]");
            return true;
        }
        World currentWorld = player.getWorld();
        World targetWorld = currentWorld.getEnvironment() == kind.environment && worlds.containsKey(currentWorld.getUID())
                ? currentWorld
                : Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == kind.environment && worlds.containsKey(w.getUID())).findFirst().orElse(null);
        if (targetWorld == null) {
            player.sendMessage("ไม่พบโลกมิติ " + kind.environment.name().toLowerCase(Locale.ROOT) + " ที่เปิดใช้งาน " + kind.id);
            return true;
        }
        Placement placement = worlds.get(targetWorld.getUID());
        if (placement == null || !assets.containsKey(kind)) {
            player.sendMessage(kind.id + " ใช้ไม่ได้ในโลกนี้");
            return true;
        }
        int limit = 16;
        if (args.length == 3) try { limit = Integer.parseInt(args[2]); } catch (NumberFormatException ex) { player.sendMessage("ระยะค้นหาต้องเป็นจำนวน region 1–32"); return true; }
        final int rings = Math.clamp(limit, 1, 32);
        final World destinationWorld = targetWorld;
        int px = destinationWorld == currentWorld ? player.getLocation().getBlockX() : 0;
        int pz = destinationWorld == currentWorld ? player.getLocation().getBlockZ() : 0;
        player.sendMessage("กำลังค้นหา " + kind.id + " ใน " + destinationWorld.getName() + " (รัศมี " + rings + " region) …");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Site found = placement.nearest(kind, px, pz, rings);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || closed) return;
                if (found == null) { player.sendMessage("ไม่พบ " + kind.id + " ในระยะที่ค้นหา · ลองเพิ่มรัศมีเป็น 32"); return; }
                Arena arena = arena(found); Asset asset = asset(found);
                player.sendMessage(kind.id + " · " + destinationWorld.getName() + " · X=" + found.x() + " Y=" + found.y() + " Z=" + found.z()
                        + " · entrance " + new String[]{"south", "west", "north", "east"}[found.rotation()] + " · arena r=" + arena.radius()
                        + " h=" + arena.height() + " · " + asset.chests.size() + " treasure chests (asset v" + found.version() + ")");
                if (teleport) teleportTo(player, destinationWorld, found);
            });
        });
        return true;
    }

    public void constructChunk(World world, Chunk chunk, Site site) {
        Asset a = asset(site); int r = site.rotation(); BlockData[] palette = a.blocks[r];
        int minH = world.getMinHeight(), maxH = world.getMaxHeight();
        int[] chunkEdits = chunk.getPersistentDataContainer().getOrDefault(editedKey, PersistentDataType.INTEGER_ARRAY, new int[0]);
        String prefix = world.getUID() + ":";
        for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
            int wx = chunk.getX() * 16 + lx, wz = chunk.getZ() * 16 + lz;
            int[] l = unrotate(wx - site.x(), wz - site.z(), r);
            if (l[0] < a.minX || l[0] >= a.minX + a.nx || l[1] < a.minZ || l[1] >= a.minZ + a.nz) continue;
            int base = ((l[0] - a.minX) * a.nz + (l[1] - a.minZ)) * a.ny;
            for (int yi = 0; yi < a.ny; yi++) {
                int v = a.data[base + yi]; if (v == 0) continue;
                int wy = site.y() + a.minY + yi; if (wy < minH || wy >= maxH) continue;
                Block block = chunk.getBlock(lx, wy, lz);
                if (block.getType() == Material.BEDROCK) continue;
                if (block.getType() == Material.CHEST && palette[v].getMaterial() == Material.CHEST) continue;
                if (chunkEdits.length > 0 && hasPacked(chunkEdits, packLocal(lx, wy, lz))) continue;
                if (!edited.isEmpty() && edited.contains(prefix + wx + ":" + wy + ":" + wz)) continue;
                if (!block.getBlockData().equals(palette[v])) block.setBlockData(palette[v], false);
            }
        }
        for (int i = 0; i < a.chests.size(); i++) {
            int[] c = a.chests.get(i), p = rotate(c[0], c[2], r);
            int x = site.x() + p[0], y = site.y() + c[1], z = site.z() + p[1];
            if (Math.floorDiv(x, 16) != chunk.getX() || Math.floorDiv(z, 16) != chunk.getZ()) continue;
            Block block = world.getBlockAt(x, y, z);
            if (block.getState() instanceof Chest chest) {
                if (!chest.getPersistentDataContainer().has(plugin.key("sanctuary_loot_seed"), PersistentDataType.LONG)) {
                    long lootSeed = DungeonLayout.mix(site.seed() + i * 7919L + 17);
                    var table = site.kind().environment == World.Environment.THE_END ? LootTables.END_CITY_TREASURE
                            : (c[3] == 0 && i == 0) ? LootTables.BASTION_TREASURE : LootTables.BASTION_OTHER;
                    chest.setLootTable(table.getLootTable(), lootSeed);
                    chest.getPersistentDataContainer().set(plugin.key("sanctuary_loot_seed"), PersistentDataType.LONG, lootSeed);
                    chest.getPersistentDataContainer().set(plugin.key("sanctuary_loot_profile"), PersistentDataType.INTEGER, 2);
                    chest.update(true, false);
                }
            }
        }
        if (plugin.overworldSanctuaries() != null) plugin.overworldSanctuaries().prepareLoot(chunk);
    }

    private void teleportTo(Player player, World world, Site site) {
        Point to = landing(site), arena = new Point(arena(site).x(), arena(site).y(), arena(site).z());
        int cx = Math.floorDiv(to.x(), 16), cz = Math.floorDiv(to.z(), 16);
        int ax = Math.floorDiv(arena.x(), 16), az = Math.floorDiv(arena.z(), 16);
        Set<Long> chunkKeys = new LinkedHashSet<>();
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) chunkKeys.add(key(cx + dx, cz + dz));
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) chunkKeys.add(key(ax + dx, az + dz));
        List<CompletableFuture<Chunk>> loads = new ArrayList<>();
        for (long k : chunkKeys) loads.add(world.getChunkAtAsync((int) (k >> 32), (int) k, true));
        CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).whenComplete((ok, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || closed) return;
            if (error != null) { player.sendMessage("โหลด chunk ปลายทางไม่สำเร็จ"); return; }
            for (long k : chunkKeys) {
                int x = (int) (k >> 32), z = (int) k;
                if (world.isChunkLoaded(x, z)) {
                    Chunk ch = world.getChunkAt(x, z);
                    constructChunk(world, ch, site);
                    repair(world, ch, site);
                }
            }
            if (plugin.realmSanctuaryEncounters() != null) plugin.realmSanctuaryEncounters().discoverSite(world, site);
            Location targetLoc = findSafeLandingLocation(world, to, arena);
            float yaw = (float) Math.toDegrees(Math.atan2(-(arena.x() - targetLoc.getX()), arena.z() - targetLoc.getZ()));
            targetLoc.setYaw(yaw); targetLoc.setPitch(0);
            player.teleportAsync(targetLoc).thenAccept(moved -> {
                if (!moved && player.isOnline()) {
                    boolean syncMoved = player.teleport(targetLoc);
                    if (!syncMoved) player.sendMessage("วาร์ปไม่สำเร็จ");
                    else player.sendMessage("วาร์ปไปยัง " + site.kind().id + " (" + world.getName() + ") เรียบร้อยแล้ว");
                } else if (player.isOnline()) {
                    player.sendMessage("วาร์ปไปยัง " + site.kind().id + " (" + world.getName() + ") เรียบร้อยแล้ว");
                }
            });
        }));
    }

    private Location findSafeLandingLocation(World world, Point to, Point arenaFallback) {
        Location loc = checkStandable(world, to.x(), to.y(), to.z());
        if (loc != null) return loc;
        for (int dy : new int[]{1, -1, 2, -2, 3, -3}) {
            loc = checkStandable(world, to.x(), to.y() + dy, to.z());
            if (loc != null) return loc;
        }
        for (int r = 1; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                for (int dy = -2; dy <= 2; dy++) {
                    loc = checkStandable(world, to.x() + dx, to.y() + dy, to.z() + dz);
                    if (loc != null) return loc;
                }
            }
        }
        loc = checkStandable(world, arenaFallback.x(), arenaFallback.y() + 1, arenaFallback.z());
        if (loc != null) return loc;
        Block floor = world.getBlockAt(to.x(), to.y() - 1, to.z());
        if (!floor.getType().isSolid()) floor.setType(Material.STONE, false);
        Block feet = world.getBlockAt(to.x(), to.y(), to.z());
        if (!feet.isPassable()) feet.setType(Material.AIR, false);
        Block head = world.getBlockAt(to.x(), to.y() + 1, to.z());
        if (!head.isPassable()) head.setType(Material.AIR, false);
        return new Location(world, to.x() + .5, to.y(), to.z() + .5);
    }

    private Location checkStandable(World world, int x, int y, int z) {
        if (y < world.getMinHeight() + 2 || y >= world.getMaxHeight() - 2) return null;
        Block floor = world.getBlockAt(x, y - 1, z);
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        if (floor.getType().isSolid() && feet.isPassable() && head.isPassable()) {
            return new Location(world, x + .5, y, z + .5);
        }
        return null;
    }
    public List<String> kindIds() { return assets.keySet().stream().map(k -> k.id).sorted().toList(); }

    // ---------------------------------------------------------------- repair of late vanilla decoration & player edit tracking
    private String blockId(Block b) { return b.getWorld().getUID() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ(); }
    private static int packLocal(int x, int y, int z) { return ((x & 15) << 20) | ((z & 15) << 16) | (y & 0xFFFF); }
    private static boolean hasPacked(int[] arr, int target) {
        for (int v : arr) if (v == target) return true;
        return false;
    }
    private void markEdited(Block b) {
        if (!enabled(b.getWorld())) return;
        edited.add(blockId(b));
        if (authoredAt(b.getWorld(), b.getX(), b.getY(), b.getZ())) {
            var pdc = b.getChunk().getPersistentDataContainer();
            int packed = packLocal(b.getX(), b.getY(), b.getZ());
            int[] cur = pdc.getOrDefault(editedKey, PersistentDataType.INTEGER_ARRAY, new int[0]);
            if (!hasPacked(cur, packed)) {
                int[] next = Arrays.copyOf(cur, cur.length + 1);
                next[cur.length] = packed;
                pdc.set(editedKey, PersistentDataType.INTEGER_ARRAY, next);
            }
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void placed(BlockPlaceEvent e) { markEdited(e.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void broken(BlockBreakEvent e) { markEdited(e.getBlock()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void bucketEmpty(PlayerBucketEmptyEvent e) { markEdited(e.getBlockClicked().getRelative(e.getBlockFace())); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void bucketFill(PlayerBucketFillEvent e) { markEdited(e.getBlockClicked()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void pistonExtend(BlockPistonExtendEvent e) { for (Block b : e.getBlocks()) { markEdited(b); markEdited(b.getRelative(e.getDirection())); } }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void pistonRetract(BlockPistonRetractEvent e) { for (Block b : e.getBlocks()) { markEdited(b); markEdited(b.getRelative(e.getDirection())); } }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void explosion(EntityExplodeEvent e) { e.blockList().forEach(this::markEdited); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void explosion(BlockExplodeEvent e) { e.blockList().forEach(this::markEdited); }
    private static final Set<CreatureSpawnEvent.SpawnReason> WILD_SPAWNS = EnumSet.of(
            CreatureSpawnEvent.SpawnReason.NATURAL, CreatureSpawnEvent.SpawnReason.CHUNK_GEN,
            CreatureSpawnEvent.SpawnReason.PATROL, CreatureSpawnEvent.SpawnReason.REINFORCEMENTS);

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void creatureSpawn(CreatureSpawnEvent event) {
        if (!plugin.getConfig().getBoolean("dimensional-sanctuaries.habitat.suppress-natural-hostiles", true)) return;
        if (!(event.getEntity() instanceof Mob mob) || !protectedAt(event.getLocation())) return;
        if (mob instanceof Tameable pet && pet.isTamed()) return;
        if (mob.getPersistentDataContainer().has(plugin.key("world_boss_entity"))
                || mob.getPersistentDataContainer().has(plugin.key("pet_model_preview"), PersistentDataType.BYTE)
                || mob.getPersistentDataContainer().has(plugin.key("sanctuary_guard"))) return;
        if (mob instanceof Enemy || WILD_SPAWNS.contains(event.getSpawnReason())) {
            event.setCancelled(true);
        }
    }

    private void purgeWildHostiles(Chunk chunk) {
        if (!plugin.getConfig().getBoolean("dimensional-sanctuaries.habitat.suppress-natural-hostiles", true)) return;
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Mob mob && protectedAt(entity.getLocation())
                    && (mob instanceof Enemy || WILD_SPAWNS.contains(entity.getEntitySpawnReason()))
                    && !(mob instanceof Tameable pet && pet.isTamed())
                    && !mob.getPersistentDataContainer().has(plugin.key("world_boss_entity"))
                    && !mob.getPersistentDataContainer().has(plugin.key("pet_model_preview"), PersistentDataType.BYTE)
                    && !mob.getPersistentDataContainer().has(plugin.key("sanctuary_guard"))) {
                entity.remove();
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void flow(BlockFromToEvent e) {
        Block to = e.getToBlock();
        if (enabled(to.getWorld()) && authoredAt(to.getWorld(), to.getX(), to.getY(), to.getZ()) && !authoredAt(e.getBlock().getWorld(), e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ()))
            e.setCancelled(true);
    }

    /** Vanilla features of a NEIGHBOUR chunk may still write into a chunk after its populator ran (lava springs, glowstone, chorus). */
    @EventHandler(priority = EventPriority.MONITOR) public void populated(ChunkPopulateEvent event) {
        World world = event.getWorld(); Placement p = worlds.get(world.getUID()); if (p == null) return;
        int cx = event.getChunk().getX(), cz = event.getChunk().getZ();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            if (p.siteForChunk(cx + dx, cz + dz, false) != null) due.put(world.getUID() + ":" + (cx + dx) + ":" + (cz + dz), Bukkit.getCurrentTick() + 100);
    }
    @EventHandler(priority = EventPriority.MONITOR) public void loaded(ChunkLoadEvent event) {
        World world = event.getWorld(); Placement p = worlds.get(world.getUID()); if (p == null) return;
        Chunk chunk = event.getChunk();
        if (p.siteForChunk(chunk.getX(), chunk.getZ(), false) != null) {
            Bukkit.getScheduler().runTask(plugin, () -> { if (chunk.isLoaded()) purgeWildHostiles(chunk); });
            if (chunk.getPersistentDataContainer().getOrDefault(sealKey, PersistentDataType.INTEGER, 0) != 1)
                due.putIfAbsent(world.getUID() + ":" + chunk.getX() + ":" + chunk.getZ(), Bukkit.getCurrentTick() + 40);
        }
    }
    private void tick() {
        if (closed) return;
        if (Bukkit.getCurrentTick() % 40 == 0 || !pendingWrites.isEmpty() && Bukkit.getCurrentTick() % 20 == 0) flush();
        int now = Bukkit.getCurrentTick(), budget = 1;
        for (var it = due.entrySet().iterator(); it.hasNext() && budget > 0; ) {
            var entry = it.next(); if (entry.getValue() > now) continue;
            String[] part = entry.getKey().split(":"); World world = Bukkit.getWorld(UUID.fromString(part[0]));
            it.remove();
            if (world == null) continue;
            int cx = Integer.parseInt(part[1]), cz = Integer.parseInt(part[2]);
            Placement p = worlds.get(world.getUID()); if (p == null || !world.isChunkLoaded(cx, cz)) continue;
            Chunk chunk = world.getChunkAt(cx, cz);
            if (chunk.getPersistentDataContainer().getOrDefault(sealKey, PersistentDataType.INTEGER, 0) == 1) continue;
            Site site = p.siteForChunk(cx, cz, false); if (site == null) continue;
            budget--;
            repair(world, chunk, site);
            boolean complete = true;
            for (int dx = -1; dx <= 1 && complete; dx++) for (int dz = -1; dz <= 1 && complete; dz++) if (!world.isChunkGenerated(cx + dx, cz + dz)) complete = false;
            if (complete) { chunk.getPersistentDataContainer().set(sealKey, PersistentDataType.INTEGER, 1); }
            else due.put(entry.getKey(), now + 600);
        }
    }
    /** Re-writes only cells that vanilla decoration has replaced with foreign terrain material. Returns how many cells changed. */
    public int repair(World world, Chunk chunk, Site site) {
        Asset a = asset(site); int changed = 0;
        var intruders = world.getEnvironment() == World.Environment.THE_END ? END_INTRUDERS : NETHER_INTRUDERS;
        BlockData[] palette = a.blocks[site.rotation()];
        String prefix = world.getUID() + ":";
        int[] chunkEdits = chunk.getPersistentDataContainer().getOrDefault(editedKey, PersistentDataType.INTEGER_ARRAY, new int[0]);
        for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
            int wx = chunk.getX() * 16 + lx, wz = chunk.getZ() * 16 + lz;
            int[] l = unrotate(wx - site.x(), wz - site.z(), site.rotation());
            if (l[0] < a.minX || l[0] >= a.minX + a.nx || l[1] < a.minZ || l[1] >= a.minZ + a.nz) continue;
            int base = ((l[0] - a.minX) * a.nz + (l[1] - a.minZ)) * a.ny;
            for (int yi = 0; yi < a.ny; yi++) {
                int v = a.data[base + yi]; if (v == 0) continue;
                int wy = site.y() + a.minY + yi; if (wy < world.getMinHeight() || wy >= world.getMaxHeight()) continue;
                Block block = chunk.getBlock(lx, wy, lz); Material now = block.getType();
                if (now == Material.BEDROCK || !intruders.contains(now) || now == palette[v].getMaterial()) continue;
                if (chunkEdits.length > 0 && hasPacked(chunkEdits, packLocal(lx, wy, lz))) continue;
                if (!edited.isEmpty() && edited.contains(prefix + wx + ":" + wy + ":" + wz)) continue;
                block.setBlockData(palette[v], false); changed++;
            }
        }
        purgeWildHostiles(chunk);
        return changed;
    }

    /** The authored block at a world position of this site, or null where the asset keeps natural terrain or ends. */
    private BlockData authored(Site site, int wx, int wy, int wz) {
        Asset a = asset(site);
        int[] l = unrotate(wx - site.x(), wz - site.z(), site.rotation());
        int ly = wy - site.y();
        if (l[0] < a.minX || l[0] >= a.minX + a.nx || l[1] < a.minZ || l[1] >= a.minZ + a.nz || ly < a.minY || ly >= a.minY + a.ny) return null;
        int v = a.data[a.at(l[0], ly, l[1])];
        return v == 0 ? null : a.blocks[site.rotation()][v];
    }

    /**
     * Boss-arena hygiene for one chunk: every cell the asset authors as air within {@code radius} of the arena
     * centre and between {@code minY} and {@code maxY} is emptied again. Fossils, fungus stems or anything else that
     * reached a chunk before it was sealed would otherwise stay as a perch the boss cannot reach. Authored structure
     * and natural terrain cells are never touched, and player edits are not exempt. Returns the blocks removed.
     */
    public int clearArena(World world, Chunk chunk, Site site, double radius, int minY, int maxY) {
        Asset a = asset(site); BlockData[] palette = a.blocks[site.rotation()]; Arena arena = arena(site);
        int low = Math.max(Math.max(minY, world.getMinHeight()), site.y() + a.minY);
        int high = Math.min(Math.min(maxY, world.getMaxHeight() - 1), site.y() + a.minY + a.ny - 1);
        ChunkSnapshot snapshot = null; int changed = 0;
        for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
            int wx = chunk.getX() * 16 + lx, wz = chunk.getZ() * 16 + lz;
            if (Math.hypot(wx - arena.x(), wz - arena.z()) > radius) continue;
            int[] l = unrotate(wx - site.x(), wz - site.z(), site.rotation());
            if (l[0] < a.minX || l[0] >= a.minX + a.nx || l[1] < a.minZ || l[1] >= a.minZ + a.nz) continue;
            int base = ((l[0] - a.minX) * a.nz + (l[1] - a.minZ)) * a.ny;
            for (int wy = low; wy <= high; wy++) {
                int v = a.data[base + wy - site.y() - a.minY];
                if (v == 0 || !palette[v].getMaterial().isAir()) continue;
                if (snapshot == null) snapshot = chunk.getChunkSnapshot(false, false, false);
                Material now = snapshot.getBlockType(lx, wy, lz);
                if (now.isAir() || now == Material.BEDROCK) continue;
                chunk.getBlock(lx, wy, lz).setBlockData(palette[v], false); changed++;
            }
        }
        return changed;
    }

    /** {@link #clearArena}'s rule for a small cylinder around one point, such as a perch the boss blasts. */
    public int clearForeign(World world, Site site, Location at, double radius, int below, int above) {
        int changed = 0, reach = (int) Math.ceil(radius);
        for (int x = at.getBlockX() - reach; x <= at.getBlockX() + reach; x++)
            for (int z = at.getBlockZ() - reach; z <= at.getBlockZ() + reach; z++) {
                if (Math.hypot(x + .5 - at.getX(), z + .5 - at.getZ()) > radius) continue;
                for (int y = at.getBlockY() - below; y <= at.getBlockY() + above; y++) {
                    if (y < world.getMinHeight() || y >= world.getMaxHeight()) continue;
                    BlockData want = authored(site, x, y, z);
                    if (want == null || !want.getMaterial().isAir()) continue;
                    Block block = world.getBlockAt(x, y, z); Material now = block.getType();
                    if (now.isAir() || now == Material.BEDROCK) continue;
                    block.setBlockData(want, false); changed++;
                }
            }
        return changed;
    }

    // ---------------------------------------------------------------- placement
    private final class Placement extends BlockPopulator {
        final World world; final NoiseProbe probe; final long seed; final Set<Long> oldRegions; final Map<Kind, Integer> sizes;
        final Map<String, String> plans = new ConcurrentHashMap<>();
        private final Map<String, Optional<Site>> parsed = new ConcurrentHashMap<>();
        private final Object computing = new Object();
        private final List<Kind> kinds;
        Placement(World world, NoiseProbe probe, Set<Long> oldRegions, Map<Kind, Integer> sizes) {
            this.world = world; this.probe = probe; this.seed = world.getSeed(); this.oldRegions = Set.copyOf(oldRegions); this.sizes = sizes;
            this.kinds = kindsFor(world);
        }
        int[] origin(Kind kind) {
            long h = DungeonLayout.mix(seed ^ (kind.ordinal() + 1) * 0x4f44534f52474e31L); int size = sizes.get(kind);
            return new int[]{(int) Math.floorMod(h, (long) size) / 16 * 16, (int) Math.floorMod(h >>> 24, (long) size) / 16 * 16};
        }
        int region(Kind kind, int blockCoord, int axis) { return Math.floorDiv(blockCoord - origin(kind)[axis], sizes.get(kind)); }
        private String id(Kind kind, int rx, int rz) { return kind.id + "@" + rx + "@" + rz; }

        /** The planned site of a region; computes and records it when allowed (never from an event handler). */
        Site site(Kind kind, int rx, int rz, boolean compute) {
            String id = id(kind, rx, rz);
            String stored = plans.get(id);
            if (stored == null) {
                if (!compute) return null;
                synchronized (computing) {
                    stored = plans.get(id);
                    if (stored == null) {
                        Site site = plan(kind, rx, rz);
                        stored = site == null ? "-" : site.x() + "," + site.y() + "," + site.z() + "," + site.rotation() + "," + site.seed() + "," + site.version();
                        plans.put(id, stored); pendingWrites.add(new String[]{root(world) + ".plans." + id, stored});
                    }
                }
            }
            if (stored.equals("-")) return null;
            final String value = stored;
            return parsed.computeIfAbsent(id, ignored -> {
                String[] p = value.split(",");
                Kind k = kind; int version = Integer.parseInt(p[5]);
                if (!assets.get(k).containsKey(version)) { plugin.getLogger().warning("Planned " + id + " needs missing asset version " + version); return Optional.empty(); }
                return Optional.of(new Site(k, Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Long.parseLong(p[4]), version));
            }).orElse(null);
        }
        int[] footprint(Site s) {
            int[] b = asset(s).bounds(s.rotation());
            return new int[]{s.x() + b[0], s.x() + b[1], s.z() + b[2], s.z() + b[3]};
        }
        Site siteForChunk(int cx, int cz, boolean compute) {
            int x0 = cx * 16, z0 = cz * 16;
            for (Kind kind : kinds) {
                Site s = site(kind, region(kind, x0 + 8, 0), region(kind, z0 + 8, 1), compute);
                if (s == null) continue;
                int[] f = footprint(s);
                if (x0 + 15 >= f[0] && x0 <= f[1] && z0 + 15 >= f[2] && z0 <= f[3]) return s;
            }
            return null;
        }
        Site nearest(Kind kind, int x, int z, int rings) {
            int size = sizes.get(kind), rx = region(kind, x, 0), rz = region(kind, z, 1);
            Site best = null; double bestD = Double.MAX_VALUE;
            for (int r = 0; r <= rings; r++) {
                for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    Site s = site(kind, rx + dx, rz + dz, true); if (s == null) continue;
                    double d = Math.hypot(s.x() - x, s.z() - z); if (d < bestD) { best = s; bestD = d; }
                }
                if (best != null && bestD <= (double) r * size) break;
            }
            return best;
        }
        private boolean touchesOld(int[] f) {
            for (int rx = Math.floorDiv(f[0] - 48, 512); rx <= Math.floorDiv(f[1] + 48, 512); rx++)
                for (int rz = Math.floorDiv(f[2] - 48, 512); rz <= Math.floorDiv(f[3] + 48, 512); rz++)
                    if (oldRegions.contains(key(rx, rz))) return true;
            return false;
        }
        private boolean overlapsHigherPriority(Kind kind, int[] f) {
            for (Kind other : kinds) {
                if (other.ordinal() >= kind.ordinal()) continue;
                int rx = region(other, (f[0] + f[1]) / 2, 0), rz = region(other, (f[2] + f[3]) / 2, 1);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    Site s = site(other, rx + dx, rz + dz, true); if (s == null) continue;
                    int[] g = footprint(s);
                    if (f[0] - 32 <= g[1] && f[1] + 32 >= g[0] && f[2] - 32 <= g[3] && f[3] + 32 >= g[2]) return true;
                }
            }
            return false;
        }

        // ---- planning: one deterministic function of seed, base terrain, vanilla layout and old regions ----
        private record Fit(int y, int rotation, double score) {}
        private Site plan(Kind kind, int rx, int rz) {
            Asset asset = assets.get(kind).values().stream().max(Comparator.comparingInt(a -> a.version)).orElseThrow();
            int size = sizes.get(kind), margin = asset.reach + 16, span = size - 2 * margin;
            if (span < 64) return null;
            int[] origin = origin(kind); long hash = DungeonLayout.mix(seed ^ rx * 341873128712L ^ rz * 132897987541L ^ (kind.ordinal() + 1L) * 0x4f44535041434b31L);
            Random random = new Random(hash);
            Map<Long, byte[]> columns = new HashMap<>();
            Fit bestFit = null; int bestX = 0, bestZ = 0, valid = 0;
            for (int attempt = 0; attempt < 72 && valid < 4; attempt++) {
                int x = (int) ((long) rx * size + origin[0] + margin + random.nextInt(span)) / 16 * 16 + 8;
                int z = (int) ((long) rz * size + origin[1] + margin + random.nextInt(span)) / 16 * 16 + 8;
                if (Math.abs(x) > 29_999_000 || Math.abs(z) > 29_999_000) continue;
                if ((double) x * x + (double) z * z < (double) kind.minOriginDistance * kind.minOriginDistance) continue;
                int reachAll = asset.reach + 8;
                int[] f = new int[]{x - reachAll, x + reachAll, z - reachAll, z + reachAll};
                if (touchesOld(f) || overlapsHigherPriority(kind, f)) continue;
                if (kind.environment == World.Environment.NETHER) {
                    if (VanillaDimensionStructures.netherComplexNear(seed, x, z, asset.reach)) continue;
                } else {
                    if (VanillaDimensionStructures.endGatewayNear(x, z, asset.reach + 300)) continue;
                    if (VanillaDimensionStructures.endCityNear(seed, x, z, asset.reach, (hx, hz) -> probe.top(hx, hz))) continue;
                }
                Fit fit = kind.environment == World.Environment.NETHER ? fitNether(kind, asset, x, z, columns) : fitEnd(kind, asset, x, z, columns);
                if (fit == null) continue;
                valid++;
                if (bestFit == null || fit.score > bestFit.score) { bestFit = fit; bestX = x; bestZ = z; }
            }
            if (bestFit == null) return null;
            return new Site(kind, bestX, bestFit.y, bestZ, bestFit.rotation, DungeonLayout.mix(hash ^ bestX * 31L ^ bestZ), asset.version);
        }
        private byte[] column(Map<Long, byte[]> cache, int x, int z) { return cache.computeIfAbsent(key(x, z), ignored -> probe.classify(x, z)); }
        private double biomeScore(Kind kind, int x, int y, int z) {
            org.bukkit.generator.BiomeProvider vanilla = world.vanillaBiomeProvider();
            double total = 0; int n = 0;
            for (int[] o : new int[][]{{0, 0}, {40, 0}, {-40, 0}, {0, 40}, {0, -40}}) {
                Biome biome = vanilla.getBiome(world, x + o[0], y, z + o[1]); n++;
                total += preference(kind, biome);
            }
            return total / n;
        }
        private double preference(Kind kind, Biome biome) {
            return switch (kind) {
                case CROWN_FORTRESS -> biome == Biome.BASALT_DELTAS ? 1 : biome == Biome.NETHER_WASTES ? .8 : biome == Biome.SOUL_SAND_VALLEY ? .6 : biome == Biome.CRIMSON_FOREST ? .4 : .3;
                case BONE_TEMPLE -> biome == Biome.SOUL_SAND_VALLEY ? 1 : biome == Biome.NETHER_WASTES ? .5 : .3;
                case CRYSTAL_CITY -> biome == Biome.END_HIGHLANDS ? 1 : biome == Biome.END_MIDLANDS ? .8 : biome == Biome.END_BARRENS ? .6 : biome == Biome.SMALL_END_ISLANDS ? .3 : 0;
                case VOID_RING -> biome == Biome.END_BARRENS ? 1 : biome == Biome.END_MIDLANDS ? .8 : biome == Biome.SMALL_END_ISLANDS ? .7 : biome == Biome.END_HIGHLANDS ? .6 : 0;
            };
        }
        /** Direction whose approach has the best ground or open cave beyond the entrance; ties go to the lower rotation. */
        private int bestRotation(Asset asset, int x, int y, int z, Map<Long, byte[]> cache, boolean wantAir) {
            int bestRotation = 0; double bestScore = -1;
            for (int r = 0; r < 4; r++) {
                double score = 0; int n = 0;
                int[] m = rotate(asset.mouth[0], asset.mouth[2], r); double len = Math.hypot(m[0], m[1]);
                double ux = m[0] / len, uz = m[1] / len;
                for (int dist = 6; dist <= 66; dist += 12) {
                    int px = (int) Math.round(x + m[0] + ux * dist), pz = (int) Math.round(z + m[1] + uz * dist);
                    byte[] c = column(cache, px, pz);
                    for (int dy = wantAir ? 2 : -2; dy <= (wantAir ? 8 : 1); dy += 2) {
                        int yy = y + asset.mouth[1] + dy - probe.minY(); n++;
                        if (yy < 0 || yy >= c.length) continue;
                        if (wantAir ? c[yy] == NoiseProbe.AIR : c[yy] == NoiseProbe.SOLID) score++;
                    }
                }
                score /= Math.max(1, n);
                if (score > bestScore + 1e-9) { bestScore = score; bestRotation = r; }
            }
            return bestRotation;
        }
        /** Nether: wide cave volume at a Y where the whole building clears the roof, preferring natural air to dig out. */
        private Fit fitNether(Kind kind, Asset asset, int x, int z, Map<Long, byte[]> cache) {
            int low = Math.max(36, 6 - asset.bottom), high = Math.min(52, 116 - asset.top);
            if (high < low || world.getMaxHeight() < 124) return null;
            List<int[]> samples = new ArrayList<>();
            for (int dx = -60; dx <= 60; dx += 12) for (int dz = -60; dz <= 60; dz += 12) if (Math.hypot(dx, dz) <= 62) samples.add(new int[]{dx, dz});
            double bestScore = -9; int bestY = 0;
            for (int y = low; y <= high; y += 2) {
                int air = 0, total = 0, lava = 0, support = 0;
                for (int[] s : samples) {
                    byte[] c = column(cache, x + s[0], z + s[1]);
                    for (int yy = y + 1; yy <= y + 30; yy += 3) { total++; byte v = c[yy - probe.minY()]; if (v == NoiseProbe.AIR) air++; else if (v == NoiseProbe.FLUID) lava++; }
                    if (c[y - 1 - probe.minY()] == NoiseProbe.SOLID) support++;
                }
                double airFrac = air / (double) total, lavaFrac = lava / (double) total, supportFrac = support / (double) samples.size();
                double score = .65 * airFrac + .2 * supportFrac - .25 * lavaFrac;
                if (airFrac >= .06 && score > bestScore) { bestScore = score; bestY = y; }
            }
            if (bestScore == -9) return null;
            double biome = biomeScore(kind, x, bestY + 10, z);
            return new Fit(bestY, bestRotation(asset, x, bestY, z, cache, true), bestScore + .35 * biome);
        }
        /**
         * End: CRYSTAL_CITY chooses a broad natural island (coverage >= 0.50); VOID_RING chooses a satellite site near an
         * island edge (coverage >= 0.25 with solid island ground under its entrance direction) so the short entrance bridge
         * lands on natural End stone while the ring sanctuary hangs out over the void.
         */
        private Fit fitEnd(Kind kind, Asset asset, int x, int z, Map<Long, byte[]> cache) {
            List<Integer> tops = new ArrayList<>(); int total = 0;
            for (int dx = -60; dx <= 60; dx += 10) for (int dz = -60; dz <= 60; dz += 10) {
                if (Math.hypot(dx, dz) > 62) continue;
                total++; byte[] c = column(cache, x + dx, z + dz); int top = Integer.MIN_VALUE;
                for (int i = c.length - 1; i >= 0; i--) if (c[i] == NoiseProbe.SOLID) { top = i + probe.minY(); break; }
                if (top != Integer.MIN_VALUE) tops.add(top);
            }
            double coverage = tops.size() / (double) total;
            double minCoverage = kind == Kind.VOID_RING ? 0.25 : 0.50;
            if (coverage < minCoverage) return null;
            Collections.sort(tops); int median = tops.get(tops.size() / 2);
            if (median < 24 || median + asset.top >= world.getMaxHeight() - 4 || median + asset.bottom < world.getMinHeight() + 2) return null;
            int even = 0; for (int t : tops) if (Math.abs(t - median) <= 6) even++;
            double flat = even / (double) tops.size();
            int rot = bestRotation(asset, x, median, z, cache, false);
            if (kind == Kind.VOID_RING) {
                // Ensure the chosen entrance direction actually meets natural island ground just outside the mouth
                int[] m = rotate(asset.mouth[0], asset.mouth[2], rot);
                int mx = x + m[0], mz = z + m[1];
                int approachSolid = 0;
                for (int dx = -8; dx <= 8; dx += 8) for (int dz = -8; dz <= 8; dz += 8)
                    if (probe.top(mx + dx, mz + dz) >= 24) approachSolid++;
                if (approachSolid < 3) return null;
                // Prefer island edges (coverage around 0.45..0.75) over isolated void or buried island centres
                double edgeScore = 1.0 - Math.min(1.0, Math.abs(coverage - 0.55) / 0.45);
                return new Fit(median, rot, 0.55 * edgeScore + 0.30 * flat + 0.20 * biomeScore(kind, x, median, z));
            }
            double biome = biomeScore(kind, x, median, z);
            return new Fit(median, rot, .6 * coverage + .4 * flat + .2 * biome);
        }

        // ---- generation: only the chunk being populated is touched ----
        @Override public void populate(WorldInfo info, Random ignored, int cx, int cz, LimitedRegion region) {
            Site site = siteForChunk(cx, cz, true); if (site == null) return;
            Asset a = asset(site); int r = site.rotation(); BlockData[] palette = a.blocks[r];
            int minH = info.getMinHeight(), maxH = info.getMaxHeight();
            for (int x = cx * 16; x < cx * 16 + 16; x++) for (int z = cz * 16; z < cz * 16 + 16; z++) {
                int[] l = unrotate(x - site.x(), z - site.z(), r);
                if (l[0] < a.minX || l[0] >= a.minX + a.nx || l[1] < a.minZ || l[1] >= a.minZ + a.nz) continue;
                int base = ((l[0] - a.minX) * a.nz + (l[1] - a.minZ)) * a.ny;
                for (int yi = 0; yi < a.ny; yi++) {
                    int v = a.data[base + yi]; if (v == 0) continue;
                    int y = site.y() + a.minY + yi; if (y < minH || y >= maxH) continue;
                    if (region.getType(x, y, z) == Material.BEDROCK) continue;
                    region.setBlockData(x, y, z, palette[v]);
                }
            }
            for (int i = 0; i < a.chests.size(); i++) {
                int[] c = a.chests.get(i), p = rotate(c[0], c[2], r);
                int x = site.x() + p[0], y = site.y() + c[1], z = site.z() + p[1];
                if (Math.floorDiv(x, 16) != cx || Math.floorDiv(z, 16) != cz) continue;
                if (!(region.getBlockState(x, y, z) instanceof Chest chest)) throw new IllegalStateException("Missing authored treasure chest in " + site.kind().id);
                long lootSeed = DungeonLayout.mix(site.seed() + i * 7919L + 17);
                var table = site.kind().environment == World.Environment.THE_END ? LootTables.END_CITY_TREASURE
                        : (c[3] == 0 && i == 0) ? LootTables.BASTION_TREASURE : LootTables.BASTION_OTHER;
                // Vanilla lazily rolls loot once and the roll lives with the container: opened chests never refill.
                chest.setLootTable(table.getLootTable(), lootSeed);
                chest.getPersistentDataContainer().set(plugin.key("sanctuary_loot_seed"), PersistentDataType.LONG, lootSeed);
                chest.getPersistentDataContainer().set(plugin.key("sanctuary_loot_profile"), PersistentDataType.INTEGER, 2);
                region.setBlockState(x, y, z, chest);
            }
        }
    }
}
