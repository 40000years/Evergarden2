package com.example.voidscape.world;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;

/**
 * <b>Glaceryn, Sovereign of Winter &mdash; The Sovereign Eyrie (รังมังกรเหมันต์นิรันดร์)</b>
 * <p>
 * Architectural Typology: <b>Five-Spire Boreal Crystal Palace &amp; Sweeping Glacial Wing-Terraces</b>.
 * <ul>
 *   <li>Central 109-block open-sky frozen mirror-lake dragon nest basin ({@code r <= 54}) with
 *       a 12-point Boreal Snowflake rune inlaid in {@code GILDED_BLACKSTONE}, {@code GOLD_BLOCK},
 *       and {@code CHISELED_QUARTZ_BLOCK} over glowing sub-ice {@code PEARLESCENT_FROGLIGHT}.</li>
 *   <li>East &amp; West Sweeping Bezier Glacial Wing-Terraces extending outward like unfurled
 *       crystalline dragon wings, plus 10 asymmetric Blue-Ice &amp; Gold Crown Spires.</li>
 *   <li>North Sanctum built as a soaring 5-Spire Boreal Crystal Palace (central Sovereign Spire
 *       rising 148 blocks above the floor, flanked by 4 stepped crystal towers &amp; flying
 *       buttresses) housing Glaceryn's Winter Throne and 8 treasure chests.</li>
 * </ul>
 */
final class IceDragonNestDesign {
    private static final Material BLUE_ICE = Material.BLUE_ICE;
    private static final Material PACKED_ICE = Material.PACKED_ICE;
    private static final Material CALCITE = Material.CALCITE;
    private static final Material QUARTZ = Material.SMOOTH_QUARTZ;
    private static final Material QUARTZ_BRICK = Material.QUARTZ_BRICKS;
    private static final Material CHISELED_QUARTZ = Material.CHISELED_QUARTZ_BLOCK;
    private static final Material GILDED = Material.GILDED_BLACKSTONE;
    private static final Material DEEPSLATE = Material.DEEPSLATE_TILES;
    private static final Material PRISMARINE = Material.DARK_PRISMARINE;
    private static final Material GOLD = Material.GOLD_BLOCK;
    private static final Material DIAMOND = Material.DIAMOND_BLOCK;
    private static final Material ICE_GLASS = Material.LIGHT_BLUE_STAINED_GLASS;
    private static final Material CYAN_GLASS = Material.CYAN_STAINED_GLASS;
    private static final Material LANTERN = Material.SEA_LANTERN;
    private static final Material PEARL = Material.PEARLESCENT_FROGLIGHT;
    private static final Material SOUL = Material.SOUL_LANTERN;
    private static final Material CHAIN = Material.IRON_CHAIN;

    private IceDragonNestDesign() {}

    static void build(DragonNest s) {
        glacialMassifIsland(s);
        frozenMirrorLakeFloor(s);
        crystallineNestRimAndHoards(s);
        tenGlacialCrownSpires(s);
        sweepingGlacialWingTerraces(s);
        northFiveSpireCrystalPalace(s);
        southBorealBridge(s);
        // Ensure the 109-block central dragon basin and arrival spine remain completely unobstructed.
        s.clearOpenCaldera(s.kind.arenaRadius - 1, s.kind.maxY);
        s.box(-5, s.floor + 1, s.kind.chestZ + 4, 5, s.floor + 9, s.kind.arrivalZ + 2, Material.AIR);
    }

    private static void glacialMassifIsland(DragonNest s) {
        int f = s.floor;
        for (int x = -112; x <= 112; x++) {
            for (int z = -112; z <= 112; z++) {
                double d = Math.hypot(x, z);
                double angle = Math.atan2(z, x);
                double lobe = 92.0
                    + 8.5 * Math.cos(angle * 6.0)
                    + 5.0 * Math.sin(angle * 12.0 + 0.4)
                    + (z < -48 ? 16.0 : 0.0)
                    + (z > 52 && Math.abs(x) < 26 ? 12.0 : 0.0);
                if (d > lobe) continue;
                double edge = d / lobe;
                int depth = (int) Math.round(54.0 * Math.pow(1.0 - edge, 0.66)
                    + 7.0 * Math.sin(x * 0.16) * Math.cos(z * 0.16));
                int y1 = Math.max(DragonNest.MIN_Y + 3, f - 8 - depth);
                s.box(x, y1, z, x, f - 15, z, BLUE_ICE);
                s.box(x, Math.max(y1, f - 14), z, x, f - 6, z, PACKED_ICE);
                s.box(x, f - 5, z, x, f - 2, z, DEEPSLATE);
                s.block(x, f - 1, z, CALCITE);
                s.block(x, f, z, (edge > 0.80 && Math.floorMod(x * 3 - z * 5, 7) == 0) ? GILDED : QUARTZ_BRICK);
            }
        }
        for (int i = 0; i < 18; i++) {
            double a = i * Math.PI / 9.0;
            int sx = (int) Math.round(Math.cos(a) * 66);
            int sz = (int) Math.round(Math.sin(a) * 66);
            int bottom = DragonNest.MIN_Y + 1 + (i % 3) * 3;
            for (int y = bottom; y <= f - 16; y++) {
                int r = Math.max(0, (y - bottom) / 6);
                s.disk(sx, y, sz, r, y % 6 == 0 ? GILDED : (y % 2 == 0 ? BLUE_ICE : PACKED_ICE));
            }
            s.block(sx, bottom - 1, sz, LANTERN);
        }
    }

    private static void frozenMirrorLakeFloor(DragonNest s) {
        int f = s.floor, r = s.kind.arenaRadius;
        s.disk(0, f - 3, 0, r - 2, BLUE_ICE);
        s.disk(0, f - 2, 0, r - 3, PACKED_ICE);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6.0;
            int lx = (int) Math.round(Math.cos(a) * 29);
            int lz = (int) Math.round(Math.sin(a) * 29);
            s.disk(lx, f - 2, lz, 5, i % 2 == 0 ? LANTERN : PEARL);
        }
        s.disk(0, f - 2, 0, 11, PEARL);

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double d = Math.hypot(x, z);
                if (d > r + 0.5) continue;
                double a = Math.atan2(z, x);
                double snowflake6 = Math.abs(Math.sin(a * 3.0));
                double snowflake12 = Math.abs(Math.sin(a * 6.0));
                boolean outerRing = Math.abs(d - (r - 2.5)) < 1.8;
                boolean midRing = Math.abs(d - 34.0) < 1.6;
                boolean innerRing = Math.abs(d - 16.0) < 1.5;

                Material top = ICE_GLASS;
                Material under = BLUE_ICE;
                if (d < 8.0) {
                    top = d < 3.2 ? GOLD : (d < 5.8 ? GILDED : CHISELED_QUARTZ);
                    under = PEARL;
                } else if (outerRing || midRing || innerRing) {
                    top = Math.floorMod(x + z, 5) == 0 ? GOLD : (midRing ? GILDED : CHISELED_QUARTZ);
                    under = GILDED;
                } else if (snowflake6 < 0.13 && d < r - 3) {
                    top = Math.floorMod((int) Math.round(d), 4) == 0 ? GOLD : GILDED;
                    under = DEEPSLATE;
                } else if (snowflake12 < 0.16 && d > 16 && d < r - 4) {
                    top = CALCITE;
                    under = LANTERN;
                } else if ((Math.floorMod(x * 5 + z * 7, 9) == 0)) {
                    top = CYAN_GLASS;
                    under = PACKED_ICE;
                }
                s.block(x, f - 1, z, under);
                s.block(x, f, z, top);
            }
        }
    }

    private static void crystallineNestRimAndHoards(DragonNest s) {
        int f = s.floor;
        for (int x = -76; x <= 76; x++) {
            for (int z = -76; z <= 76; z++) {
                if (Math.abs(x) < 14 && (z < -48 || z > 48)) continue;
                double d = Math.hypot(x, z);
                if (d < 54.5 || d > 73.5) continue;
                double a = Math.atan2(z, x);
                double wave = Math.sin(a * 10.0) * 2.1 + Math.cos(a * 6.0) * 2.0;
                double profile = 1.0 - Math.abs(d - 63.0) / 9.5;
                int h = Math.max(1, (int) Math.round(profile * 9.5 + wave));
                for (int dy = 1; dy <= h; dy++) {
                    Material m;
                    if (dy == h && Math.floorMod(x + z, 5) == 0) m = GILDED;
                    else if ((x + z + dy) % 4 == 0) m = BLUE_ICE;
                    else if ((x * 3 - z) % 5 == 0) m = CALCITE;
                    else m = PACKED_ICE;
                    s.block(x, f + dy, z, m);
                }
            }
        }

        int[][] hoards = {
            {-49, -27, 6}, {49, -27, 6}, {-51, 13, 5}, {51, 13, 5},
            {-38, 42, 6}, {38, 42, 6}, {-32, -46, 5}, {32, -46, 5}
        };
        for (int[] h : hoards) {
            s.hoardMound(h[0], f + 1, h[1], h[2], GILDED, GOLD, DIAMOND, PEARL);
        }
    }

    private static void tenGlacialCrownSpires(DragonNest s) {
        int f = s.floor;
        double[] angles = {
            Math.toRadians(22), Math.toRadians(52), Math.toRadians(82),
            Math.toRadians(98), Math.toRadians(128), Math.toRadians(158),
            Math.toRadians(202), Math.toRadians(238), Math.toRadians(302), Math.toRadians(338)
        };
        for (int i = 0; i < angles.length; i++) {
            double a = angles[i];
            double cos = Math.cos(a), sin = Math.sin(a);
            int baseR = 84;
            int bx = (int) Math.round(cos * baseR), bz = (int) Math.round(sin * baseR);
            s.cylinder(bx, f + 1, f + 14, bz, 6, DEEPSLATE);
            s.ring(bx, f + 14, bz, 6.4, 1.4, GILDED);
            s.disk(bx, f + 15, bz, 5, BLUE_ICE);

            int tipY = f + 82 + (i % 3) * 6;
            for (int step = 0; step <= 44; step++) {
                double t = step / 44.0;
                double r = baseR + (1.0 - t) * 9.0 * Math.sin(t * Math.PI) - t * 25.0;
                double cx = cos * r;
                double cz = sin * r;
                double cy = f + 13 + Math.pow(t, 0.80) * (tipY - (f + 13));
                double thick = Math.max(1.1, 4.2 * (1.0 - 0.70 * t));
                Material core = step % 6 == 0 ? GILDED : (step % 2 == 0 ? BLUE_ICE : PACKED_ICE);
                s.line(cx, cy, cz, cx + cos * 0.4, cy + 1.0, cz + sin * 0.4, thick, core);
                int ox = (int) Math.round(cx + cos * (thick + 0.8));
                int oz = (int) Math.round(cz + sin * (thick + 0.8));
                int oy = (int) Math.round(cy + 1);
                s.block(ox, oy, oz, step % 5 == 0 ? GOLD : QUARTZ_BRICK);
                if (step > 12 && step % 9 == 0) {
                    int ix = (int) Math.round(cx - cos * (thick + 0.5));
                    int iz = (int) Math.round(cz - sin * (thick + 0.5));
                    int iy = (int) Math.round(cy);
                    s.block(ix, iy, iz, PEARL);
                    s.box(ix, iy - 4, iz, ix, iy - 1, iz, CHAIN);
                    s.block(ix, iy - 5, iz, SOUL, BlockFace.UP);
                }
            }
        }
    }

    private static void sweepingGlacialWingTerraces(DragonNest s) {
        int f = s.floor;
        // East & West Unfurled Glacial Dragon-Wing Terraces & Rising Ice Feathers
        for (int side : new int[]{-1, 1}) {
            for (int feather = 0; feather < 5; feather++) {
                double ax = 68 + feather * 3, az = -26 + feather * 12;
                double cx = 92 + feather * 2, cz = -12 + feather * 14;
                double bx = 112 - feather * 3, bz = 14 + feather * 14;
                int tipRise = 36 - feather * 5;
                for (int step = 0; step <= 44; step++) {
                    double t = step / 44.0;
                    double x = side * bezier(ax, cx, bx, t);
                    double z = bezier(az, cz, bz, t);
                    double y = f + 2 + Math.pow(t, 1.35) * tipRise;
                    double rad = Math.max(1.2, 4.2 * (1.0 - 0.65 * t));
                    Material mat = (step % 6 == 0) ? GOLD : (step % 3 == 0 ? GILDED : (feather % 2 == 0 ? BLUE_ICE : QUARTZ_BRICK));
                    s.line(x, y, z, x + side * 0.4, y + 0.8, z + 0.4, rad, mat);
                    if (step == 44) {
                        s.block((int) Math.round(x), (int) Math.round(y) + 2, (int) Math.round(z), LANTERN);
                    }
                }
            }
        }
    }

    private static double bezier(double a, double c, double b, double t) {
        double u = 1.0 - t;
        return u * u * a + 2.0 * u * t * c + t * t * b;
    }

    private static void northFiveSpireCrystalPalace(DragonNest s) {
        int f = s.floor;
        // Multi-tiered Crystalline Palace Plinth
        s.ellipse(0, f + 1, -83, 44, 26, DEEPSLATE);
        s.ellipse(0, f + 4, -83, 42, 25, QUARTZ_BRICK);
        s.ellipseRing(0, f + 5, -83, 42, 25, 1.6, GILDED);
        for (int step = 0; step < 5; step++) {
            int z = -54 - step * 2;
            s.box(-14, f + 1 + step, z, 14, f + 1 + step, z + 1, step % 2 == 0 ? GILDED : QUARTZ_BRICK);
        }

        int fy = f + 5;
        // Sculpted Octagonal-Elliptical Crystal Throne Hall
        for (int y = fy + 1; y <= fy + 42; y++) {
            s.ellipse(0, y, -83, 30, 20, y % 9 == 0 ? GILDED : QUARTZ_BRICK);
            s.ellipse(0, y, -83, 27, 17, Material.AIR);
        }
        s.ellipse(0, fy, -83, 28, 18, BLUE_ICE);
        s.box(-5, fy, -99, 5, fy, -60, GILDED);
        s.box(-2, fy, -97, 2, fy, -62, GOLD);

        // Grand Crystalline Pointed Portal Arch opening south into the nest
        for (int x = -10; x <= 10; x++) {
            int archH = fy + 24 - Math.abs(x) * 2;
            s.box(x, fy + 1, -67, x, archH, -60, Material.AIR);
            s.box(x, archH + 1, -65, x, archH + 3, -61, GILDED);
            s.block(x, archH + 4, -61, GOLD);
        }

        // 12-Radius Boreal Snowflake Rose Window on the south facade
        int roseY = fy + 33, roseZ = -63;
        for (int x = -11; x <= 11; x++) {
            for (int y = -11; y <= 11; y++) {
                double r = Math.hypot(x, y);
                if (r > 11.5) continue;
                Material m;
                if (r > 9.8 || Math.abs(r - 5.5) < 0.9) m = GILDED;
                else if (x == 0 || y == 0 || Math.abs(x) == Math.abs(y)) m = GOLD;
                else m = ((x + y) & 1) == 0 ? ICE_GLASS : CYAN_GLASS;
                s.block(x, roseY + y, roseZ, m);
            }
        }
        s.disk(0, roseY, roseZ, 2, PEARL);

        // SOARING CENTRAL BOREAL CRYSTAL SPIRE (rising from the center of the Palace to fy + 142!)
        for (int h = 0; h <= 98; h++) {
            double t = h / 98.0;
            int rad = Math.max(0, (int) Math.round(16.0 * Math.pow(1.0 - t, 0.72)));
            int y = fy + 42 + h;
            Material spireMat = (h % 8 == 0) ? GOLD : (h % 5 == 0 ? GILDED : (h % 2 == 0 ? BLUE_ICE : QUARTZ_BRICK));
            s.cylinder(0, y, y, -84, rad, spireMat);
            // 4 Crystalline Fin Blades projecting from the cardinal faces of the central spire
            if (h < 76) {
                int finSpan = rad + 4 - h / 18;
                s.box(-finSpan, y, -84, finSpan, y, -84, h % 4 == 0 ? GOLD : ICE_GLASS);
                s.box(0, y, -84 - finSpan, 0, y, -84 + finSpan, h % 4 == 0 ? GOLD : ICE_GLASS);
            }
        }
        s.box(0, fy + 141, -84, 0, fy + 146, -84, LANTERN);

        // 4 Flanking Stepped Crystal Needle Towers (forming a 5-Spire Boreal Skyline!)
        int[][] spireCoords = {
            {-26, -72, 78, 8}, {26, -72, 78, 8},
            {-38, -88, 96, 9}, {38, -88, 96, 9}
        };
        for (int[] sc : spireCoords) {
            int tx = sc[0], tz = sc[1], th = sc[2], tr = sc[3];
            for (int h = 0; h <= th; h++) {
                double t = h / (double) th;
                int r = Math.max(0, (int) Math.round(tr * Math.pow(1.0 - t, 0.68)));
                Material m = (h % 9 == 0) ? GOLD : (h % 4 == 0 ? GILDED : (h % 2 == 0 ? BLUE_ICE : QUARTZ_BRICK));
                s.cylinder(tx, fy + 1 + h, fy + 1 + h, tz, r, m);
            }
            s.box(tx, fy + th + 2, tz, tx, fy + th + 6, tz, LANTERN);
            // Flying crystalline buttress arch linking each flanking spire to the central Sovereign Spire
            s.line(tx, fy + 38, tz, 0, fy + 64, -84, 1.8, BLUE_ICE);
            s.line(tx, fy + 40, tz, 0, fy + 66, -84, 0.9, GOLD);
        }

        // Sovereign Winter Throne Pedestal + 8 Treasure Chests inside the Palace Hall
        s.cylinder(0, fy + 1, fy + 4, -88, 7, GILDED);
        s.cylinder(0, fy + 5, fy + 6, -88, 5, GOLD);
        s.block(0, fy + 7, -88, DIAMOND);
        s.block(0, fy + 8, -88, PEARL);

        int[] chestZs = {-72, -78, -84, -90};
        for (int czBay : chestZs) {
            s.hoardMound(-20, fy + 1, czBay, 4, GILDED, GOLD, DIAMOND, PEARL);
            s.hoardMound(20, fy + 1, czBay, 4, GILDED, GOLD, DIAMOND, PEARL);
            s.treasure(-15, fy + 1, czBay, BlockFace.EAST);
            s.treasure(15, fy + 1, czBay, BlockFace.WEST);
        }
    }

    private static void southBorealBridge(DragonNest s) {
        int f = s.floor;
        s.box(-12, f - 4, 54, 12, f, 110, QUARTZ_BRICK);
        s.box(-13, f + 1, 56, -11, f + 3, 108, GILDED);
        s.box(11, f + 1, 56, 13, f + 3, 108, GILDED);
        s.box(-4, f, 54, 4, f, 110, GILDED);
        s.box(-1, f, 54, 1, f, 110, GOLD);

        for (int z : new int[]{68, 86, 102}) {
            for (int side : new int[]{-1, 1}) {
                int px = side * 14;
                s.box(px - 2, f + 1, z - 2, px + 2, f + 16, z + 2, QUARTZ_BRICK);
                s.box(px - 3, f + 14, z - 3, px + 3, f + 16, z + 3, GILDED);
                s.line(px, f + 16, z, side * 3, f + 34, z, 2.0, BLUE_ICE);
                s.line(px, f + 17, z, side * 3, f + 35, z, 0.9, GILDED);
                s.block(side * 3, f + 35, z, LANTERN);
            }
        }
    }
}
