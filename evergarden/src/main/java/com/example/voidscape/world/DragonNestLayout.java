package com.example.voidscape.world;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Seed-stable placement for the three colossal elemental Dragon Nests in Evergarden:
 * <ul>
 *   <li>{@code dragon-nest-fire} &mdash; <b>Vharok, Crimson Ember</b> (Crimson Caldera)</li>
 *   <li>{@code dragon-nest-storm} &mdash; <b>Vaelthyr, Stormborne Apex</b> (Tempest Fulcrum)</li>
 *   <li>{@code dragon-nest-ice} &mdash; <b>Glaceryn, Sovereign of Winter</b> (Sovereign Eyrie)</li>
 * </ul>
 * Uses a slightly rarer default roll (0.018 per cell vs 0.03 for ordinary boss structures)
 * and yields to all existing landmarks, whales, ancient temples and boss structures.
 */
public final class DragonNestLayout {
    public enum Kind {
        FIRE("dragon-nest-fire", "fire", "forge_dragon_fire", "df",
            "Vharok, Crimson Ember — รังมังกรเพลิงโลกันตร์",
            116, 112, 54, -78, 104, 296, 0x4452474e46495245L),
        STORM("dragon-nest-storm", "storm", "forge_dragon_storm", "ds",
            "Vaelthyr, Stormborne Apex — รังมังกรอัสนีเวหา",
            116, 118, 54, -78, 104, 304, 0x4452474e5354524dL),
        ICE("dragon-nest-ice", "ice", "forge_dragon_ice", "di",
            "Glaceryn, Sovereign of Winter — รังมังกรเหมันต์นิรันดร์",
            116, 114, 54, -78, 104, 300, 0x4452474e49434531L);

        public final String id, shortId, modelId, bonePrefix, title;
        public final int radius, floorY, arenaRadius, chestZ, arrivalZ, maxY;
        final long salt;

        Kind(String id, String shortId, String modelId, String bonePrefix, String title,
             int radius, int floorY, int arenaRadius, int chestZ, int arrivalZ, int maxY, long salt) {
            this.id = id;
            this.shortId = shortId;
            this.modelId = modelId;
            this.bonePrefix = bonePrefix;
            this.title = title;
            this.radius = radius;
            this.floorY = floorY;
            this.arenaRadius = arenaRadius;
            this.chestZ = chestZ;
            this.arrivalZ = arrivalZ;
            this.maxY = maxY;
            this.salt = salt;
        }

        public DragonNest blueprint() {
            return DragonNest.blueprint(this);
        }

        public static Kind fromId(String raw) {
            if (raw == null) return null;
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "fire", "dragon-fire", "fire-dragon", "fire-nest", "dragon-nest-fire", "vharok" -> FIRE;
                case "storm", "lightning", "electric", "dragon-storm", "storm-dragon", "lightning-dragon",
                     "storm-nest", "dragon-nest-storm", "vaelthyr" -> STORM;
                case "ice", "frost", "dragon-ice", "ice-dragon", "ice-nest", "dragon-nest-ice", "glaceryn" -> ICE;
                default -> null;
            };
        }
    }

    public record Site(Kind kind, int x, int z) {
        public DragonNest blueprint() {
            return DragonNest.blueprint(kind);
        }

        public boolean contains(int bx, int bz, int margin) {
            return Math.abs((long) bx - x) <= kind.radius + margin
                && Math.abs((long) bz - z) <= kind.radius + margin;
        }
    }

    public static final double DEFAULT_CHANCE = 0.018;
    private static final int DUNGEON_RADIUS = 28;

    private final long seed;
    private final DungeonLayout dungeons;
    private final SkyWhaleLayout whales;
    private final SkyLandmarkLayout landmarks;
    private final WorldBossTempleLayout ancient;
    private final BossStructureLayout bossStructures;
    private final int spacing, gap, marginChunks;
    private final Map<Kind, Double> chances;
    private final Set<Kind> enabled;
    private final Set<Long> excluded;
    private final Map<Long, List<Site>> cache = new ConcurrentHashMap<>();

    public DragonNestLayout(long seed, DungeonLayout dungeons, SkyWhaleLayout whales,
                            SkyLandmarkLayout landmarks, WorldBossTempleLayout ancient,
                            BossStructureLayout bossStructures, int spacing, int gap,
                            Map<Kind, Double> chances, Set<Kind> enabled, Set<Long> excluded) {
        this.seed = seed;
        this.dungeons = dungeons;
        this.whales = whales;
        this.landmarks = landmarks;
        this.ancient = ancient;
        this.bossStructures = bossStructures;
        this.spacing = Math.clamp(spacing, 32, 64);
        this.gap = Math.clamp(gap, 48, 128);
        this.marginChunks = (int) Math.ceil((116 + this.gap / 2.0) / 16.0);
        var rates = new EnumMap<Kind, Double>(Kind.class);
        for (var kind : Kind.values()) {
            double rate = chances.getOrDefault(kind, DEFAULT_CHANCE);
            rates.put(kind, Double.isFinite(rate) ? Math.clamp(rate, 0, 1) : 0);
        }
        this.chances = Map.copyOf(rates);
        this.enabled = Set.copyOf(enabled);
        this.excluded = Set.copyOf(excluded);
    }

    public int spacingChunks() { return spacing; }
    public int minimumGap() { return gap; }
    public double chance(Kind kind) { return chances.get(kind); }
    public boolean enabled(Kind kind) { return enabled.contains(kind); }

    public List<Site> cell(int gx, int gz) {
        return cache.computeIfAbsent(SkyPlacementHistory.key(gx, gz), ignored -> compute(gx, gz));
    }

    private List<Site> compute(int gx, int gz) {
        if (excluded.contains(SkyPlacementHistory.key(gx, gz))) return List.of();
        var result = new ArrayList<Site>();
        int span = Math.max(1, spacing - 2 * marginChunks);
        for (var kind : Kind.values()) {
            var random = new Random(DungeonLayout.mix(seed ^ kind.salt ^ ((long) gx * 341873128712L) ^ ((long) gz * 132897987541L)));
            if (random.nextDouble() >= chance(kind)) continue;
            int x = (gx * spacing + marginChunks + random.nextInt(span)) * 16;
            int z = (gz * spacing + marginChunks + random.nextInt(span)) * 16;
            var site = new Site(kind, x, z);
            // Keep dragon nests further from the spawn island (896 blocks vs 768 for normal boss structures).
            if (Math.hypot(x, z) < 896 || conflictsWithExisting(site)) continue;
            if (result.stream().anyMatch(other -> close(site, other.x, other.z, other.kind.radius, other.kind.radius))) continue;
            result.add(site);
        }
        return List.copyOf(result);
    }

    private boolean close(Site site, int x, int z, int radiusX, int radiusZ) {
        return Math.abs((long) site.x - x) <= site.kind.radius + radiusX + gap
            && Math.abs((long) site.z - z) <= site.kind.radius + radiusZ + gap;
    }

    private boolean conflictsWithExisting(Site site) {
        int reach = site.kind.radius + 128 + gap;
        if (whales != null) {
            int ws = whales.spacingChunks() * 16;
            for (int gx = Math.floorDiv(site.x - reach, ws); gx <= Math.floorDiv(site.x + reach, ws); gx++)
                for (int gz = Math.floorDiv(site.z - reach, ws); gz <= Math.floorDiv(site.z + reach, ws); gz++) {
                    var whale = whales.cell(gx, gz);
                    if (whale != null && close(site, whale.x(), whale.z(), 115, 65)) return true;
                }
        }
        if (landmarks != null) {
            int ls = landmarks.spacingChunks() * 16;
            for (int gx = Math.floorDiv(site.x - reach, ls); gx <= Math.floorDiv(site.x + reach, ls); gx++)
                for (int gz = Math.floorDiv(site.z - reach, ls); gz <= Math.floorDiv(site.z + reach, ls); gz++)
                    for (var landmark : landmarks.cell(gx, gz))
                        if (close(site, landmark.x(), landmark.z(), landmark.kind().radius, landmark.kind().radius)) return true;
        }
        if (ancient != null) {
            int as = ancient.spacingChunks() * 16;
            for (int gx = Math.floorDiv(site.x - reach, as); gx <= Math.floorDiv(site.x + reach, as); gx++)
                for (int gz = Math.floorDiv(site.z - reach, as); gz <= Math.floorDiv(site.z + reach, as); gz++) {
                    var temple = ancient.cell(gx, gz);
                    if (temple != null && close(site, temple.x(), temple.z(), WorldBossTemple.RADIUS, WorldBossTemple.RADIUS)) return true;
                }
        }
        if (bossStructures != null) {
            int bs = bossStructures.spacingChunks() * 16;
            for (int gx = Math.floorDiv(site.x - reach, bs); gx <= Math.floorDiv(site.x + reach, bs); gx++)
                for (int gz = Math.floorDiv(site.z - reach, bs); gz <= Math.floorDiv(site.z + reach, bs); gz++)
                    for (var boss : bossStructures.cell(gx, gz))
                        if (close(site, boss.x(), boss.z(), boss.kind().radius, boss.kind().radius)) return true;
        }
        if (dungeons != null) {
            for (var shrine : dungeons.within(site.x, site.z, reach)) {
                if (close(site, shrine.x(), shrine.z(), DUNGEON_RADIUS, DUNGEON_RADIUS)) return true;
            }
            int ds = dungeons.cellSize();
            for (int gx = Math.floorDiv(site.x - reach, ds); gx <= Math.floorDiv(site.x + reach, ds); gx++) {
                for (int gz = Math.floorDiv(site.z - reach, ds); gz <= Math.floorDiv(site.z + reach, ds); gz++) {
                    var extra = dungeons.extraCandidate(gx, gz);
                    if (extra != null && close(site, extra.x(), extra.z(), DUNGEON_RADIUS, DUNGEON_RADIUS)) return true;
                }
            }
        }
        return false;
    }

    public Site at(int x, int z, int margin) {
        int size = spacing * 16, reach = 120 + margin;
        for (int gx = Math.floorDiv(x - reach, size); gx <= Math.floorDiv(x + reach, size); gx++)
            for (int gz = Math.floorDiv(z - reach, size); gz <= Math.floorDiv(z + reach, size); gz++)
                for (var site : cell(gx, gz))
                    if (site.contains(x, z, margin)) return site;
        return null;
    }

    public Site nearest(Kind kind, int x, int z, int radiusCells) {
        Site best = null;
        double distance = Double.POSITIVE_INFINITY;
        int gx = Math.floorDiv(x, spacing * 16), gz = Math.floorDiv(z, spacing * 16);
        for (int dx = -radiusCells; dx <= radiusCells; dx++)
            for (int dz = -radiusCells; dz <= radiusCells; dz++)
                for (var site : cell(gx + dx, gz + dz))
                    if (kind == null ? enabled(site.kind) : site.kind == kind) {
                        double d = Math.hypot((double) x - site.x, (double) z - site.z);
                        if (d < distance) {
                            distance = d;
                            best = site;
                        }
                    }
        return best;
    }
}
