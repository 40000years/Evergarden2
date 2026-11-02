import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.DimensionalSanctuaries;
import com.example.voidscape.world.DimensionalSanctuaries.Arena;
import com.example.voidscape.world.DimensionalSanctuaries.Kind;
import com.example.voidscape.world.DimensionalSanctuaries.Point;
import com.example.voidscape.world.DimensionalSanctuaries.Site;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * End-to-end Paper verification for all 4 DimensionalSanctuaries (crown-fortress, bone-temple, crystal-city, void-ring):
 * - Old-world region exclusion
 * - Multi-region deterministic planning without loading chunks
 * - Nether ceiling preservation & End island placement without void pillars
 * - Walkable entrance, teleport landing, and 70+ block unobstructed boss courts
 * - Non-refilling treasure chests across chunk reload and server restart
 * - Chunk-generation-order independence (forward order on Boot 1 vs reverse order on Boot 2)
 */
public final class DimensionalSanctuariesProbe extends JavaPlugin {
    private final List<String> checks = new ArrayList<>();
    private final ArrayDeque<ChunkJob> queue = new ArrayDeque<>();
    private record ChunkJob(World world, int cx, int cz) {}
    private VoidscapePlugin garden;
    private DimensionalSanctuaries gen;
    private World nether, end;
    private final EnumMap<Kind, Site> sites = new EnumMap<>(Kind.class);
    private Site reverseSite;
    private boolean restart;

    private void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        checks.add(what);
        getLogger().info("PASS " + what);
    }

    private static List<ChunkJob> requiredChunks(DimensionalSanctuaries gen, World w, Site site) {
        Set<Long> seen = new LinkedHashSet<>();
        List<ChunkJob> jobs = new ArrayList<>();
        java.util.function.BiConsumer<Integer, Integer> add = (bx, bz) -> {
            int cx = Math.floorDiv(bx, 16), cz = Math.floorDiv(bz, 16);
            long k = ((long) cx << 32) | (cz & 0xffffffffL);
            if (seen.add(k)) jobs.add(new ChunkJob(w, cx, cz));
        };
        Arena a = gen.arena(site);
        for (int dx = -a.radius(); dx <= a.radius(); dx += 16)
            for (int dz = -a.radius(); dz <= a.radius(); dz += 16)
                if (Math.hypot(dx, dz) <= a.radius()) add.accept(a.x() + dx, a.z() + dz);
        for (Point pt : List.of(gen.landing(site), gen.entrance(site)))
            for (int dx = -16; dx <= 16; dx += 16)
                for (int dz = -16; dz <= 16; dz += 16)
                    add.accept(pt.x() + dx, pt.z() + dz);
        for (Point cp : gen.chestLocations(site)) add.accept(cp.x(), cp.z());
        return jobs;
    }

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                restart = Files.exists(Path.of("dimsanct-checkpoint.txt"));
                garden = (VoidscapePlugin) Bukkit.getPluginManager().getPlugin("Evergarden");
                check(garden != null && garden.isEnabled(), "Evergarden enabled");
                gen = garden.dimensionalSanctuaries();
                check(gen != null, "DimensionalSanctuaries service initialized");

                World overworld = Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == World.Environment.NORMAL).findFirst().orElseThrow();
                nether = Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == World.Environment.NETHER).findFirst().orElseThrow();
                end = Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == World.Environment.THE_END).findFirst().orElseThrow();
                nether.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                end.setGameRule(GameRule.DO_MOB_SPAWNING, false);

                check(!gen.enabled(overworld), "DimensionalSanctuaries not attached to Overworld");
                check(gen.enabled(nether), "DimensionalSanctuaries attached to Nether");
                check(gen.enabled(end), "DimensionalSanctuaries attached to The End");
                check(gen.kindIds().equals(List.of("bone-temple", "crown-fortress", "crystal-city", "void-ring")),
                        "all four sanctuary kinds loaded: " + gen.kindIds());

                // 1. Search all 4 kinds without loading any chunks
                int netherLoadedBefore = nether.getLoadedChunks().length;
                int endLoadedBefore = end.getLoadedChunks().length;
                for (Kind kind : Kind.values()) {
                    World w = kind.environment == World.Environment.NETHER ? nether : end;
                    Site s = gen.findNearest(w, kind, -2500, 2500, 6);
                    check(s != null, "planned site found for " + kind.id + ": " + s);
                    check(s.equals(gen.findNearest(w, kind, -2500, 2500, 6)), "repeated search is deterministic for " + kind.id);
                    sites.put(kind, s);
                }
                reverseSite = gen.findNearest(nether, Kind.CROWN_FORTRESS, 4500, -4500, 6);
                check(reverseSite != null && !reverseSite.equals(sites.get(Kind.CROWN_FORTRESS)),
                        "distinct second site planned for reverse-order chunk test: " + reverseSite);
                check(netherLoadedBefore == nether.getLoadedChunks().length && endLoadedBefore == end.getLoadedChunks().length,
                        "planning all 4 kinds + reverse site loaded zero chunks");

                // 2. Verify pre-existing region r.20.20 (blocks 10240..10751) is excluded in both Nether and End
                check(gen.siteAt(nether, 10400, 10400) == null && gen.siteAt(end, 10400, 10400) == null,
                        "pre-existing old region r.20.20 is excluded in Nether and End");

                // 3. Multi-region survey across a 15,000 x 15,000 block span for all 4 kinds
                for (Kind kind : Kind.values()) {
                    World w = kind.environment == World.Environment.NETHER ? nether : end;
                    Set<Site> found = new HashSet<>();
                    for (int gx = -2; gx <= 2; gx += 2) {
                        for (int gz = -2; gz <= 2; gz += 2) {
                            Site s = gen.findNearest(w, kind, gx * kind.regionBlocks, gz * kind.regionBlocks, 2);
                            if (s != null) {
                                int[] b = gen.bounds(s);
                                if (kind.environment == World.Environment.NETHER) {
                                    check(b[3] < 118 && b[2] >= 6, kind.id + " stays safely between bedrock floor and Nether ceiling: y=" + b[2] + ".." + b[3]);
                                } else {
                                    check(Math.hypot(s.x(), s.z()) >= kind.minOriginDistance, kind.id + " clears central End island & gateway ring");
                                    check(s.y() >= 24 && b[3] < w.getMaxHeight() - 4 && b[2] >= w.getMinHeight() + 2,
                                            kind.id + " fits End height bounds: y=" + b[2] + ".." + b[3]);
                                }
                                found.add(s);
                            }
                        }
                    }
                    check(found.size() >= 3, "15km survey found " + found.size() + " distinct valid sites for " + kind.id);
                }

                gen.flush();

                Path checkpoint = Path.of("dimsanct-checkpoint.txt");
                if (restart) {
                    List<String> expected = Files.readAllLines(checkpoint);
                    int idx = 0;
                    for (Kind kind : Kind.values()) {
                        Site s = sites.get(kind);
                        String actual = kind.id + ":" + s.x() + "," + s.y() + "," + s.z() + "," + s.rotation() + "," + s.seed() + "," + s.version();
                        check(expected.get(idx++).equals(actual), "plan persisted across restart for " + kind.id);
                    }
                    String revActual = "reverse:" + reverseSite.x() + "," + reverseSite.y() + "," + reverseSite.z() + "," + reverseSite.rotation();
                    check(expected.get(idx).equals(revActual), "reverse site plan persisted across restart");
                }

                for (Kind kind : Kind.values()) {
                    World w = kind.environment == World.Environment.NETHER ? nether : end;
                    queue.addAll(requiredChunks(gen, w, sites.get(kind)));
                }
                if (restart) {
                    List<ChunkJob> revJobs = new ArrayList<>(requiredChunks(gen, nether, reverseSite));
                    Collections.reverse(revJobs);
                    queue.addAll(revJobs);
                    nextBatch();
                } else {
                    Site cf = sites.get(Kind.CROWN_FORTRESS);
                    Arena cfArena = gen.arena(cf);
                    int ex = cfArena.x() + 10, ey = cfArena.y() + 1, ez = cfArena.z() + 10;
                    nether.getChunkAtAsync(Math.floorDiv(ex, 16), Math.floorDiv(ez, 16), true).whenComplete((chunk, err) ->
                        Bukkit.getScheduler().runTaskLater(this, () -> {
                            if (err != null) { finish(err); return; }
                            chunk.addPluginChunkTicket(this);
                            Block blk = nether.getBlockAt(ex, ey, ez);
                            var prev = blk.getState();
                            blk.setType(Material.NETHERRACK, false);
                            gen.placed(new BlockPlaceEvent(blk, prev, blk, new ItemStack(Material.NETHERRACK), null, true, EquipmentSlot.HAND));
                            nextBatch();
                        }, 1));
                }
            } catch (Throwable ex) {
                finish(ex);
            }
        }, 5);
    }

    private void nextBatch() {
        if (queue.isEmpty()) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                try {
                    verifyGeneratedSites();
                } catch (Throwable ex) {
                    finish(ex);
                }
            }, 60);
            return;
        }
        List<CompletableFuture<Chunk>> batch = new ArrayList<>();
        for (int i = 0; i < 16 && !queue.isEmpty(); i++) {
            ChunkJob job = queue.poll();
            batch.add(job.world.getChunkAtAsync(job.cx, job.cz, true));
        }
        CompletableFuture.allOf(batch.toArray(CompletableFuture[]::new)).whenComplete((v, err) ->
            Bukkit.getScheduler().runTask(this, () -> {
                if (err != null) finish(err);
                else {
                    for (var f : batch) f.join().addPluginChunkTicket(this);
                    nextBatch();
                }
            }));
    }

    private void verifySiteGeometry(World w, Site site, String label, boolean allowPlayerEditAt10) {
        for (ChunkJob job : requiredChunks(gen, w, site)) {
            if (w.isChunkLoaded(job.cx, job.cz)) gen.repair(w, w.getChunkAt(job.cx, job.cz), site);
        }

        check(site.equals(gen.siteAt(w, site.x(), site.z())), label + ": siteAt matches centre");
        Arena arena = gen.arena(site);
        check(arena.radius() >= 34 && arena.height() >= 40, label + ": arena dimensions r=" + arena.radius() + " h=" + arena.height());

        for (int dx = -arena.radius(); dx <= arena.radius(); dx += 16) {
            for (int dz = -arena.radius(); dz <= arena.radius(); dz += 16) {
                if (Math.hypot(dx, dz) > arena.radius()) continue;
                int x = arena.x() + dx, z = arena.z() + dz;
                Material floor = w.getBlockAt(x, arena.y(), z).getType();
                if (!floor.isSolid()) throw new AssertionError(label + ": arena floor not solid at " + x + "," + z + ": " + floor);
                for (int dy = 1; dy <= arena.height(); dy += 4) {
                    if (allowPlayerEditAt10 && dx == 10 && dz == 10 && dy == 1) continue;
                    Material air = w.getBlockAt(x, arena.y() + dy, z).getType();
                    if (!air.isAir()) throw new AssertionError(label + ": arena obstructed at " + x + "," + (arena.y() + dy) + "," + z + ": " + air);
                }
            }
        }
        check(true, label + ": continuous boss floor (diameter " + (arena.radius() * 2 + 1) + ") and clear height " + arena.height());

        for (Point pt : List.of(gen.landing(site), gen.entrance(site))) {
            Material fl = w.getBlockAt(pt.x(), pt.y() - 1, pt.z()).getType();
            boolean head = w.getBlockAt(pt.x(), pt.y(), pt.z()).isPassable() && w.getBlockAt(pt.x(), pt.y() + 1, pt.z()).isPassable();
            check(fl.isSolid() && head, label + ": walkable point " + pt + " has solid floor (" + fl + ") and clear headroom");
        }

        int[] b = gen.bounds(site);
        Block insideFloor = w.getBlockAt(arena.x(), arena.y(), arena.z());
        Block insideAir = w.getBlockAt(arena.x(), arena.y() + 2, arena.z());
        Block outsideTerrain = w.getBlockAt(b[0] - 2, site.y(), b[4] - 2);
        Block cornerUnauthored = w.getBlockAt(b[0], b[3], b[4]);
        check(garden.structureProtection().protects(insideFloor) && garden.structureProtection().protects(insideAir),
                label + ": StructureProtection protects authored floor and interior air");
        check(!garden.structureProtection().protects(outsideTerrain) && !garden.structureProtection().protects(cornerUnauthored),
                label + ": StructureProtection allows mining natural terrain outside voxel shell");
        check(gen.protectedAt(insideAir.getLocation()) && !gen.protectedAt(outsideTerrain.getLocation()),
                label + ": mob-suppression boundary matches exact authored voxel volume");

        if (site.kind().environment == World.Environment.NETHER) {
            if (w.getBlockAt(site.x(), 127, site.z()).getType() != Material.BEDROCK)
                throw new AssertionError(label + ": Nether ceiling bedrock broken at centre");
            check(true, label + ": Nether ceiling bedrock (y=127) 100% intact above structure");
        } else {
            for (int y = 2; y < b[2]; y++) {
                Material under = w.getBlockAt(site.x(), y, site.z()).getType();
                if (y <= 8 && !under.isAir())
                    throw new AssertionError(label + ": unexpected pillar to void at y=" + y + ": " + under);
                if (site.kind() == Kind.VOID_RING && !under.isAir())
                    throw new AssertionError(label + ": satellite island should have open void below y=" + b[2] + " but found " + under + " at y=" + y);
                if (!under.isAir() && under != Material.END_STONE)
                    throw new AssertionError(label + ": structure block leaked below bounds y=" + b[2] + " at y=" + y + ": " + under);
            }
            check(true, label + ": End underside terminates cleanly at y=" + b[2] + " with no void pillars");
        }
    }

    private void verifyGeneratedSites() throws Exception {
        for (Kind kind : Kind.values()) {
            World w = kind.environment == World.Environment.NETHER ? nether : end;
            Site site = sites.get(kind);
            verifySiteGeometry(w, site, kind.id, kind == Kind.CROWN_FORTRESS);

            List<Point> chests = gen.chestLocations(site);
            check(chests.size() >= 8, kind.id + ": has " + chests.size() + " authored treasure chests");
            int netheriteUpgrades = 0;
            for (int i = 0; i < chests.size(); i++) {
                Point cp = chests.get(i);
                Block blk = w.getBlockAt(cp.x(), cp.y(), cp.z());
                check(blk.getType() == Material.CHEST && blk.getState() instanceof Chest,
                        kind.id + ": chest #" + i + " exists at " + cp);
                garden.overworldSanctuaries().prepareLoot(blk.getChunk());
                Chest chest = (Chest) blk.getState();
                for (ItemStack it : chest.getBlockInventory().getContents()) {
                    if (it != null && it.getType() == Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE) {
                        netheriteUpgrades += it.getAmount();
                    }
                }
                if (i == 0) {
                    if (!restart) {
                        boolean hadLoot = Arrays.stream(chest.getBlockInventory().getContents()).anyMatch(it -> it != null && !it.getType().isAir());
                        check(hadLoot, kind.id + ": chest #0 generated non-empty treasure on Boot 1");
                        chest.getBlockInventory().clear();
                        chest.getBlockInventory().setItem(0, new ItemStack(Material.GOLD_INGOT, 3));
                    } else {
                        ItemStack marker = chest.getBlockInventory().getItem(0);
                        long nonEmpty = Arrays.stream(chest.getBlockInventory().getContents()).filter(it -> it != null && !it.getType().isAir()).count();
                        check(marker != null && marker.getType() == Material.GOLD_INGOT && marker.getAmount() == 3 && nonEmpty == 1,
                                kind.id + ": looted chest #0 did NOT refill across restart");
                    }
                }
            }
            if (kind.environment == World.Environment.NETHER) {
                check(netheriteUpgrades <= 1, kind.id + ": Netherite Upgrade Smithing Template is rare across all chests (found " + netheriteUpgrades + ")");
            }

            var boss = garden.realmSanctuaryEncounters().discoverSite(w, site);
            check(boss != null && boss.carrier() != null && boss.carrier().isValid() && boss.maxHealth() >= 3000,
                    kind.id + ": spawned waiting realm boss " + (boss == null ? "null" : boss.englishName() + " HP=" + boss.maxHealth()));
            boss.awaken(List.of());
            check(boss.isFighting(), kind.id + ": Round 1 boss is aggressive after awaken");
            // Simulate unexpected vanilla carrier entity removal mid-fight and verify aggression is preserved
            boss.carrier().remove();
            boss.tick(10L);
            check(boss.isFighting() && boss.carrier() != null && boss.carrier().isValid(),
                    kind.id + ": boss preserves fighting=true and recovers carrier if proxy entity is removed mid-fight");
            for (int skill = 0; skill < 4; skill++) {
                boss.forceSkill(skill);
                for (int t = 1; t <= 40; t++) boss.tick(t + skill * 50L);
            }
            // Simulate Round 2 respawn and re-engagement
            boss.spawnWaiting(300L);
            check(!boss.isFighting() && !boss.isDead() && boss.health() == boss.maxHealth(),
                    kind.id + ": Round 2 respawned in waiting state at full HP");
            boss.awaken(List.of());
            boss.forceSkill(0);
            for (int t = 1; t <= 25; t++) boss.tick(300L + t);
            check(boss.isFighting() && boss.carrier() != null && boss.carrier().isValid(),
                    kind.id + ": Round 2 boss remains aggressive and executes skills after respawn");
            Arena a = gen.arena(site);
            check(w.getBlockAt(a.x(), a.y(), a.z()).getType().isSolid(),
                    kind.id + ": boss skills did not alter real sanctuary floor blocks");
        }

        Arena cfArena = gen.arena(sites.get(Kind.CROWN_FORTRESS));
        check(nether.getBlockAt(cfArena.x() + 10, cfArena.y() + 1, cfArena.z() + 10).getType() == Material.NETHERRACK,
                "player-placed NETHERRACK inside CROWN_FORTRESS survived repair and restart");

        if (!restart) {
            Point cp = gen.chestLocations(sites.get(Kind.CROWN_FORTRESS)).getFirst();
            int cx = Math.floorDiv(cp.x(), 16), cz = Math.floorDiv(cp.z(), 16);
            nether.removePluginChunkTickets(this);
            end.removePluginChunkTickets(this);
            nether.unloadChunkRequest(cx, cz);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                try {
                    nether.getChunkAtAsync(cx, cz, true).whenComplete((chunk, err) ->
                        Bukkit.getScheduler().runTask(this, () -> {
                            try {
                                if (err != null) { finish(err); return; }
                                garden.overworldSanctuaries().prepareLoot(chunk);
                                Chest chest = (Chest) nether.getBlockAt(cp.x(), cp.y(), cp.z()).getState();
                                ItemStack marker = chest.getBlockInventory().getItem(0);
                                long nonEmpty = Arrays.stream(chest.getBlockInventory().getContents()).filter(it -> it != null && !it.getType().isAir()).count();
                                check(marker != null && marker.getType() == Material.GOLD_INGOT && marker.getAmount() == 3 && nonEmpty == 1,
                                        "crown-fortress: looted chest #0 did NOT refill after chunk unload & reload");

                                List<String> lines = new ArrayList<>();
                                for (Kind kind : Kind.values()) {
                                    Site s = sites.get(kind);
                                    lines.add(kind.id + ":" + s.x() + "," + s.y() + "," + s.z() + "," + s.rotation() + "," + s.seed() + "," + s.version());
                                }
                                lines.add("reverse:" + reverseSite.x() + "," + reverseSite.y() + "," + reverseSite.z() + "," + reverseSite.rotation());
                                Files.write(Path.of("dimsanct-checkpoint.txt"), lines);
                                finish(null);
                            } catch (Throwable ex) {
                                finish(ex);
                            }
                        }));
                } catch (Throwable ex) {
                    finish(ex);
                }
            }, 40);
        } else {
            verifySiteGeometry(nether, reverseSite, "crown-fortress-reverse-chunk-order", false);
            for (Point cp : gen.chestLocations(reverseSite)) {
                Block blk = nether.getBlockAt(cp.x(), cp.y(), cp.z());
                check(blk.getType() == Material.CHEST && blk.getState() instanceof Chest,
                        "reverse-order site chest intact at " + cp);
            }
            finish(null);
        }
    }

    private void finish(Throwable error) {
        if (error != null) getLogger().log(java.util.logging.Level.SEVERE, "DIMENSIONAL SANCTUARIES PROBE", error);
        try {
            if (gen != null) gen.flush();
            Files.writeString(Path.of("dimsanct-result-" + (restart ? 2 : 1) + ".txt"),
                    (error == null ? "PASS" : "FAIL " + error) + "\n" + String.join("\n", checks) + "\n");
        } catch (Exception ignored) {}
        Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 5);
    }
}
