package com.example.voidscape.world;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import java.util.*;

/**
 * Immutable boxes, clipped into each generated chunk. Buildings never use display entities.
 * Phase 1 Redesign: 3 distinct elemental sanctum combat arenas (Dark, Astral, Time)
 * with flat, obstruction-free circular combat pits (r <= 19), anti-cheese perimeter balustrades,
 * and dynamic Tier I–V crest beacons on the gate pediments.
 */
public final class Blueprint {
    public record Box(int x1, int y1, int z1, int x2, int y2, int z2, Material material) {}
    private final List<Box> boxes = new ArrayList<>();

    private void box(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        int ax1 = Math.max(-28, Math.min(x1, x2)), ax2 = Math.min(28, Math.max(x1, x2));
        int ay1 = Math.max(94, Math.min(y1, y2)),  ay2 = Math.min(140, Math.max(y1, y2));
        int az1 = Math.max(-28, Math.min(z1, z2)), az2 = Math.min(28, Math.max(z1, z2));
        if (ax1 <= ax2 && ay1 <= ay2 && az1 <= az2) {
            boxes.add(new Box(ax1, ay1, az1, ax2, ay2, az2, m));
        }
    }

    public List<Box> boxes() { return List.copyOf(boxes); }

    public static Blueprint sanctumDark()   { return buildDarkSanctum(); }
    public static Blueprint sanctumAstral() { return buildAstralSanctum(); }
    public static Blueprint sanctumTime()   { return buildTimeSanctum(); }

    /**
     * Common foundation, circular arena balustrade, cardinal gate thresholds,
     * and functional Altar (0,97,8) + Vault (0,97,-16) placement.
     */
    private static void buildArenaBase(Blueprint b, Material understone, Material outerFloor,
                                       Material innerFloor, Material rimBlock, Material baluster,
                                       Material glow, Material glass) {
        // 1. Sub-foundation (Y=94..95) supporting circular temple & 4 cardinal approaches
        for (int x = -24; x <= 24; x++) {
            for (int z = -24; z <= 24; z++) {
                double r = Math.hypot(x, z);
                if (r <= 23.5 || (Math.abs(x) <= 5 && Math.abs(z) <= 24) || (Math.abs(z) <= 5 && Math.abs(x) <= 24)) {
                    b.box(x, 94, z, x, 95, z, understone);
                    b.box(x, 96, z, x, 96, z, outerFloor);
                }
            }
        }

        // 2. Smooth flat circular inner combat arena (r <= 19, Y=96)
        for (int x = -19; x <= 19; x++) {
            for (int z = -19; z <= 19; z++) {
                double r = Math.hypot(x, z);
                if (r <= 18.5) {
                    b.box(x, 96, z, x, 96, z, innerFloor);
                } else if (r <= 19.6) {
                    b.box(x, 96, z, x, 96, z, rimBlock);
                }
            }
        }

        // 3. Perimeter anti-escape balustrade ring (r = 20.2 .. 21.8, Y=97..98)
        // Leaves 4 cardinal gateways (|x| <= 3 or |z| <= 3) completely open for causeways.
        for (int x = -22; x <= 22; x++) {
            for (int z = -22; z <= 22; z++) {
                if (Math.abs(x) <= 3 || Math.abs(z) <= 3) continue;
                double r = Math.hypot(x, z);
                if (r >= 20.2 && r <= 21.6) {
                    b.box(x, 97, z, x, 97, z, rimBlock);
                    if (Math.floorMod(x + z, 2) == 0) {
                        b.box(x, 98, z, x, 98, z, baluster);
                    }
                }
            }
        }

        // 4. Four Cardinal Gate Arches (North/South at z = +-21, East/West at x = +-21)
        for (int sign : new int[]{-1, 1}) {
            int gz = sign * 21;
            // North & South main portals
            b.box(-5, 97, gz, -4, 106, gz, rimBlock);
            b.box(4, 97, gz, 5, 106, gz, rimBlock);
            b.box(-5, 107, gz, 5, 108, gz, outerFloor);
            b.box(-4, 109, gz, 4, 109, gz, rimBlock);
            b.box(0, 108, gz, 0, 108, gz, glow);

            // East & West side portals
            int gx = sign * 21;
            b.box(gx, 97, -5, gx, 105, -4, rimBlock);
            b.box(gx, 97, 4, gx, 105, 5, rimBlock);
            b.box(gx, 106, -5, gx, 107, 5, outerFloor);
            b.box(gx, 108, -3, gx, 108, 3, rimBlock);
            b.box(gx, 107, 0, gx, 107, 0, glow);
        }
    }

    private static void stampAltarAndVault(Blueprint b, Material trim, Material stone, Material glow) {
        // Altar at (0, 97, 8) — Flush floor rune pad at Y=96 so mobs never get stuck on steps
        b.box(-3, 96, 5, 3, 96, 11, trim);
        b.box(-2, 96, 6, 2, 96, 10, stone);
        b.box(-1, 96, 7, 1, 96, 9, Material.AMETHYST_BLOCK);
        b.box(-2, 96, 8, -2, 96, 8, glow);
        b.box(2, 96, 8, 2, 96, 8, glow);
        b.box(0, 96, 6, 0, 96, 6, glow);
        b.box(0, 96, 10, 0, 96, 10, glow);
        b.box(0, 97, 8, 0, 97, 8, Material.LODESTONE);

        // Vault at (0, 97, -16) — Flush floor dais at Y=96; shrine backdrop pushed back to z=-19
        b.box(-2, 96, -18, 2, 96, -14, trim);
        b.box(-1, 96, -17, 1, 96, -15, Material.AMETHYST_BLOCK);
        b.box(-2, 96, -16, -2, 96, -16, glow);
        b.box(2, 96, -16, 2, 96, -16, glow);
        b.box(0, 97, -16, 0, 97, -16, Material.VAULT);

        // Recessed Vault Halo Arch at z=-19 (outside main combat loop so mobs cannot get wedged)
        b.box(-2, 97, -19, -2, 101, -19, trim);
        b.box(2, 97, -19, 2, 101, -19, trim);
        b.box(-2, 102, -19, 2, 102, -19, stone);
        b.box(0, 101, -19, 0, 101, -19, glow);
    }

    /**
     * 1. SANCTUM OF DARKNESS (วิหารความมืด)
     * Theme: Abyssal Gothic Cathedral — Ribbed obsidian buttresses, crying-obsidian chains,
     * 8-pointed eclipse floor sigil with under-lit purple glass, and a dark eclipse halo overhead.
     */
    private static Blueprint buildDarkSanctum() {
        Blueprint b = new Blueprint();
        Material under = Material.DEEPSLATE_TILES;
        Material outer = Material.POLISHED_BLACKSTONE_BRICKS;
        Material inner = Material.POLISHED_DEEPSLATE;
        Material trim  = Material.CHISELED_DEEPSLATE;
        Material glass = Material.PURPLE_STAINED_GLASS;
        Material glow  = Material.SEA_LANTERN;

        buildArenaBase(b, under, outer, inner, trim, Material.POLISHED_BLACKSTONE_BRICK_WALL, glow, glass);

        // Abyssal 8-Pointed Eclipse Floor Mosaic (Y=95 underlight + Y=96 flush glass/obsidian runes)
        for (int x = -17; x <= 17; x++) {
            for (int z = -17; z <= 17; z++) {
                double r = Math.hypot(x, z);
                boolean outerRing = Math.abs(r - 14.0) < 0.65;
                boolean innerRing = Math.abs(r - 6.5) < 0.65;
                boolean cardinalRay = (Math.abs(x) <= 1 && r >= 6.0 && r <= 14.5) || (Math.abs(z) <= 1 && r >= 6.0 && r <= 14.5);
                boolean diagRay = Math.abs(Math.abs(x) - Math.abs(z)) <= 1 && r >= 6.0 && r <= 14.5;

                if (outerRing || innerRing) {
                    b.box(x, 96, z, x, 96, z, Material.OBSIDIAN);
                    if ((Math.abs(x) + Math.abs(z)) % 4 == 0) {
                        b.box(x, 95, z, x, 95, z, glow);
                        b.box(x, 96, z, x, 96, z, glass);
                    }
                } else if (cardinalRay || diagRay) {
                    if (x == 0 || z == 0 || Math.abs(x) == Math.abs(z)) {
                        b.box(x, 95, z, x, 95, z, glow);
                        b.box(x, 96, z, x, 96, z, glass);
                    } else {
                        b.box(x, 96, z, x, 96, z, trim);
                    }
                }
            }
        }

        // 8 Towering Gothic Rib Buttresses curving inward above the perimeter (Y=97..122)
        int[][] buttresses = {
            {-19, -9}, {19, -9}, {-19, 9}, {19, 9},
            {-9, -19}, {9, -19}, {-9, 19}, {9, 19}
        };
        for (int[] p : buttresses) {
            int px = p[0], pz = p[1];
            b.box(px - 1, 97, pz - 1, px + 1, 99, pz + 1, trim);
            b.box(px, 100, pz, px, 114, pz, outer);
            b.box(px, 103, pz, px, 111, pz, Material.CRYING_OBSIDIAN);
            b.box(px - 1, 115, pz - 1, px + 1, 116, pz + 1, trim);
            b.box(px, 117, pz, px, 121, pz, Material.OBSIDIAN);
            b.box(px, 122, pz, px, 122, pz, glow);

            // Inward curving gothic rib arch (Y=115..123)
            int ix = (int) Math.round(px * 0.72);
            int iz = (int) Math.round(pz * 0.72);
            b.box(Math.min(px, ix), 116, Math.min(pz, iz), Math.max(px, ix), 118, Math.max(pz, iz), outer);
            b.box(ix, 119, iz, ix, 123, iz, Material.CRYING_OBSIDIAN);
        }

        // 4 Diagonal Dark Spires with suspended Soul Lantern / Amethyst chains
        for (int dx : new int[]{-15, 15}) {
            for (int dz : new int[]{-15, 15}) {
                b.box(dx - 1, 97, dz - 1, dx + 1, 100, dz + 1, outer);
                b.box(dx, 101, dz, dx, 118, dz, Material.OBSIDIAN);
                b.box(dx, 119, dz, dx, 124, dz, Material.CRYING_OBSIDIAN);
                b.box(dx, 125, dz, dx, 125, dz, glow);
            }
        }

        // Overhead Abyssal Eclipse Halo (Y=125..134) — Hollow center so (0,110,0) is open sky
        for (int x = -15; x <= 15; x++) {
            for (int z = -15; z <= 15; z++) {
                double r = Math.hypot(x, z);
                if (Math.abs(r - 12.5) < 0.75) {
                    int y = 126 + (int) Math.round(Math.sin(Math.atan2(z, x) * 4) * 1.5);
                    b.box(x, y, z, x, y + 1, z, Material.OBSIDIAN);
                    if (Math.floorMod(x + z, 5) == 0) {
                        b.box(x, y + 2, z, x, y + 2, z, Material.CRYING_OBSIDIAN);
                        b.box(x, y - 1, z, x, y - 1, z, glow);
                    }
                }
            }
        }
        // High suspended Dark Eclipse Core (Y=128..135, well above Y=110 check)
        b.box(-1, 129, -1, 1, 133, 1, Material.OBSIDIAN);
        b.box(0, 127, 0, 0, 135, 0, Material.CRYING_OBSIDIAN);
        b.box(0, 131, 0, 0, 131, 0, glow);

        stampAltarAndVault(b, trim, outer, glow);
        return b;
    }

    /**
     * 2. SANCTUM OF THE STARS (วิหารดวงดาว)
     * Theme: Celestial Astrolabe Observatory — Radiant quartz & calcite starlight amphitheater,
     * constellation floor chart, winged purpur-quartz pillars, and intersecting 3D armillary rings.
     */
    private static Blueprint buildAstralSanctum() {
        Blueprint b = new Blueprint();
        Material under = Material.CALCITE;
        Material outer = Material.SMOOTH_QUARTZ;
        Material inner = Material.CALCITE;
        Material trim  = Material.PURPUR_PILLAR;
        Material glass = Material.LIGHT_BLUE_STAINED_GLASS;
        Material glow  = Material.SEA_LANTERN;

        buildArenaBase(b, under, outer, inner, Material.CHISELED_QUARTZ_BLOCK, trim, glow, glass);

        // Celestial Star-Chart & Compass Rose Floor Mosaic (Y=95..96)
        for (int x = -18; x <= 18; x++) {
            for (int z = -18; z <= 18; z++) {
                double r = Math.hypot(x, z);
                boolean outerOrbit = Math.abs(r - 15.0) < 0.6;
                boolean midOrbit   = Math.abs(r - 10.0) < 0.55;
                boolean innerOrbit = Math.abs(r - 5.0) < 0.55;
                boolean starCross  = (x == 0 && Math.abs(z) <= 15) || (z == 0 && Math.abs(x) <= 15);
                boolean diagStar   = Math.abs(x) == Math.abs(z) && r <= 12.0;

                if (outerOrbit || midOrbit || innerOrbit) {
                    b.box(x, 96, z, x, 96, z, outer);
                    if (Math.floorMod(x * 3 + z * 5, 7) == 0) {
                        b.box(x, 95, z, x, 95, z, glow);
                        b.box(x, 96, z, x, 96, z, glass);
                    }
                } else if (starCross || diagStar) {
                    b.box(x, 95, z, x, 95, z, glow);
                    b.box(x, 96, z, x, 96, z, glass);
                }
            }
        }

        // 8 Winged Celestial Observatory Pillars around the perimeter
        int[][] pillars = {
            {-19, -8}, {19, -8}, {-19, 8}, {19, 8},
            {-8, -19}, {8, -19}, {-8, 19}, {8, 19}
        };
        for (int[] p : pillars) {
            int px = p[0], pz = p[1];
            b.box(px - 1, 97, pz - 1, px + 1, 98, pz + 1, Material.CHISELED_QUARTZ_BLOCK);
            b.box(px, 99, pz, px, 114, pz, outer);
            b.box(px, 102, pz, px, 110, pz, trim);
            b.box(px - 1, 115, pz - 1, px + 1, 115, pz + 1, Material.CHISELED_QUARTZ_BLOCK);
            b.box(px, 116, pz, px, 118, pz, glass);
            b.box(px, 119, pz, px, 119, pz, glow);
        }

        // 4 Diagonal Floating Starlight Prisms above the corners
        for (int dx : new int[]{-15, 15}) {
            for (int dz : new int[]{-15, 15}) {
                b.box(dx, 97, dz, dx, 108, dz, trim);
                b.box(dx, 109, dz, dx, 109, dz, glow);
                // Floating crystal prism at Y=114..120
                b.box(dx, 114, dz, dx, 120, dz, glass);
                b.box(dx - 1, 117, dz, dx + 1, 117, dz, glass);
                b.box(dx, 117, dz - 1, dx, 117, dz + 1, glass);
                b.box(dx, 117, dz, dx, 117, dz, glow);
            }
        }

        // Intersecting Dual Armillary Sphere Rings overhead (Y=120..136)
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                double r = Math.hypot(x, z);
                // Ring A: tilted along X
                if (Math.abs(r - 13.5) < 0.7) {
                    int yA = 126 + (int) Math.round(x * 0.32);
                    b.box(x, yA, z, x, yA, z, outer);
                    if (Math.floorMod(x + z, 6) == 0) b.box(x, yA + 1, z, x, yA + 1, z, glow);
                }
                // Ring B: tilted along Z
                if (Math.abs(r - 10.5) < 0.7) {
                    int yB = 126 - (int) Math.round(z * 0.32);
                    b.box(x, yB, z, x, yB, z, trim);
                    if (Math.floorMod(x - z, 6) == 0) b.box(x, yB + 1, z, x, yB + 1, z, glass);
                }
            }
        }
        // Central Astral Star Core at Y=127..135 (leaves Y=110 open)
        b.box(0, 127, 0, 0, 135, 0, glass);
        b.box(-2, 131, 0, 2, 131, 0, glass);
        b.box(0, 131, -2, 0, 131, 2, glass);
        b.box(0, 130, 0, 0, 132, 0, Material.AMETHYST_BLOCK);
        b.box(0, 131, 0, 0, 131, 0, glow);

        stampAltarAndVault(b, trim, outer, glow);
        return b;
    }

    /**
     * 3. SANCTUM OF TIME (วิหารกาลเวลา)
     * Theme: Chronos Clockwork Colosseum — 12-hour sundial/clock-face floor with toothed copper gear rim,
     * double-tiered colosseum arches, and a giant suspended hourglass & pendulum spire overhead.
     */
    private static Blueprint buildTimeSanctum() {
        Blueprint b = new Blueprint();
        Material under = Material.END_STONE_BRICKS;
        Material outer = Material.END_STONE_BRICKS;
        Material inner = Material.SMOOTH_QUARTZ;
        Material copper = Material.WAXED_OXIDIZED_COPPER;
        Material bronze = Material.WAXED_COPPER_BLOCK;
        Material gold   = Material.GOLD_BLOCK;
        Material glass  = Material.CYAN_STAINED_GLASS;
        Material glow   = Material.OCHRE_FROGLIGHT;

        buildArenaBase(b, under, outer, inner, copper, Material.END_STONE_BRICK_WALL, glow, glass);

        // Clockwork Gear Rim & 12-Hour Sundial Floor Mosaic (Y=95..96)
        for (int x = -18; x <= 18; x++) {
            for (int z = -18; z <= 18; z++) {
                double r = Math.hypot(x, z);
                double angle = Math.atan2(z, x);
                // Toothed outer gear ring (r = 14.5 .. 16.2)
                int tooth = Math.floorMod((int) Math.round((angle + Math.PI) / (2 * Math.PI) * 24), 2);
                if (Math.abs(r - 15.0) < 0.7 || (tooth == 0 && r >= 15.0 && r <= 16.4)) {
                    b.box(x, 96, z, x, 96, z, copper);
                } else if (Math.abs(r - 8.5) < 0.6) {
                    b.box(x, 96, z, x, 96, z, bronze);
                } else if (r <= 2.2) {
                    b.box(x, 96, z, x, 96, z, gold);
                }

                // Clock hands pointing North (to Vault) and South (to Altar) and East
                if ((x == 0 && z >= -14 && z <= 7) || (z == 0 && x >= 0 && x <= 11)) {
                    b.box(x, 95, z, x, 95, z, glow);
                    b.box(x, 96, z, x, 96, z, glass);
                }
            }
        }
        // 12 Hour Markers around radius 12.5
        for (int h = 0; h < 12; h++) {
            double rad = h * Math.PI / 6.0;
            int hx = (int) Math.round(Math.cos(rad) * 12.5);
            int hz = (int) Math.round(Math.sin(rad) * 12.5);
            b.box(hx, 96, hz, hx, 96, hz, h % 3 == 0 ? gold : glow);
        }

        // Colosseum Double-Tiered Aqueduct Arches around the perimeter (Y=97..116)
        int[][] piers = {
            {-19, -9}, {19, -9}, {-19, 9}, {19, 9},
            {-9, -19}, {9, -19}, {-9, 19}, {9, 19},
            {-15, -15}, {15, -15}, {-15, 15}, {15, 15}
        };
        for (int[] p : piers) {
            int px = p[0], pz = p[1];
            b.box(px - 1, 97, pz - 1, px + 1, 98, pz + 1, copper);
            b.box(px, 99, pz, px, 113, pz, outer);
            b.box(px, 105, pz, px, 106, pz, bronze);
            b.box(px - 1, 114, pz - 1, px + 1, 115, pz + 1, copper);
            b.box(px, 116, pz, px, 116, pz, glow);
        }

        // Giant Suspended Celestial Hourglass overhead (Y=120..136, well above Y=110)
        for (int dy = -6; dy <= 6; dy++) {
            int y = 128 + dy;
            int rad = Math.max(1, Math.abs(dy));
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    double d = Math.hypot(dx, dz);
                    if (Math.abs(dy) == 6 && d <= rad + 0.4) {
                        // Top and bottom hourglass plates
                        b.box(dx, y, dz, dx, y, dz, copper);
                    } else if (Math.abs(d - rad) < 0.75) {
                        // Glass hourglass bulbs
                        b.box(dx, y, dz, dx, y, dz, dy == 0 ? gold : glass);
                    }
                }
            }
        }
        // Glowing Time-Sand Nexus at the waist of the hourglass (0, 127..129, 0)
        b.box(0, 126, 0, 0, 130, 0, glow);
        // Outer orbital clock ring around the hourglass at Y=128
        for (int x = -14; x <= 14; x++) {
            for (int z = -14; z <= 14; z++) {
                double r = Math.hypot(x, z);
                if (Math.abs(r - 12.5) < 0.65) {
                    b.box(x, 128, z, x, 128, z, copper);
                    if (x == 0 || z == 0 || Math.abs(x) == Math.abs(z)) {
                        b.box(x, 129, z, x, 129, z, gold);
                    }
                }
            }
        }

        stampAltarAndVault(b, copper, outer, glow);
        return b;
    }

    public void render(ChunkData data, int chunkX, int chunkZ, DungeonLayout.Site site) {
        int ox = chunkX * 16 - site.x(), oz = chunkZ * 16 - site.z();
        for (Box b : boxes) {
            int x1 = Math.max(0, b.x1 - ox), x2 = Math.min(15, b.x2 - ox);
            int z1 = Math.max(0, b.z1 - oz), z2 = Math.min(15, b.z2 - oz);
            if (x1 > x2 || z1 > z2) continue;
            data.setRegion(x1, b.y1, z1, x2 + 1, b.y2 + 1, z2 + 1, b.material);
        }

        // Render Tier I - V visual crest stars above North & South Gate Arches (z = -21 and z = 21, y = 110..111)
        int tier = Math.clamp(site.tier(), 1, 5);
        Material tierGlow = switch (tier) {
            case 1 -> Material.VERDANT_FROGLIGHT;
            case 2 -> Material.SEA_LANTERN;
            case 3 -> Material.OCHRE_FROGLIGHT;
            case 4 -> Material.SHROOMLIGHT;
            default -> Material.CRYING_OBSIDIAN;
        };
        int[] starOffsets = switch (tier) {
            case 1 -> new int[]{0};
            case 2 -> new int[]{-2, 2};
            case 3 -> new int[]{-3, 0, 3};
            case 4 -> new int[]{-4, -1, 1, 4};
            default -> new int[]{-4, -2, 0, 2, 4};
        };
        for (int gz : new int[]{-21, 21}) {
            int lz = gz - oz;
            if (lz < 0 || lz > 15) continue;
            for (int sx : starOffsets) {
                int lx = sx - ox;
                if (lx >= 0 && lx <= 15) {
                    data.setBlock(lx, 110, lz, tierGlow);
                    if (tier == 5) {
                        data.setBlock(lx, 111, lz, Material.SEA_LANTERN);
                    }
                }
            }
        }
    }
}
