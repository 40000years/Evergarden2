package com.example.voidscape.world;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;

/**
 * <b>Vaelthyr, Stormborne Apex &mdash; The Tempest Fulcrum (รังมังกรอัสนีเวหา)</b>
 * <p>
 * Architectural Typology: <b>Domed Celestial Storm Observatory &amp; Tilted Armillary Dynamo</b>.
 * <ul>
 *   <li>Central 109-block open-sky nest basin ({@code r <= 54}) with a 12-point Lichtenberg
 *       lightning astrolabe floor inlaid with {@code GOLD_BLOCK}, {@code GILDED_BLACKSTONE},
 *       and sub-floor {@code OCHRE_FROGLIGHT} plasma conduits.</li>
 *   <li>Six towering Tesla Conductor Pylons ringed by induction toruses, East/West Curved
 *       Storm Colonnades, and tilted 3D outer armillary rings.</li>
 *   <li>North Sanctum built as a monumental Ribbed Copper-and-Gold Domed Rotunda crowned by a
 *       148-block Lightning Needle, housing 8 storm treasure chests around a Fulcrum Dynamo Core.</li>
 * </ul>
 */
final class StormDragonNestDesign {
    private static final Material GILDED = Material.GILDED_BLACKSTONE;
    private static final Material DEEPSLATE = Material.DEEPSLATE_BRICKS;
    private static final Material POLISHED = Material.POLISHED_DEEPSLATE;
    private static final Material TILES = Material.DEEPSLATE_TILES;
    private static final Material PRISMARINE = Material.DARK_PRISMARINE;
    private static final Material OXIDIZED = Material.WAXED_OXIDIZED_COPPER;
    private static final Material OX_CUT = Material.WAXED_OXIDIZED_CUT_COPPER;
    private static final Material COPPER = Material.WAXED_COPPER_BLOCK;
    private static final Material GOLD = Material.GOLD_BLOCK;
    private static final Material AMETHYST = Material.AMETHYST_BLOCK;
    private static final Material OCHRE = Material.OCHRE_FROGLIGHT;
    private static final Material LANTERN = Material.SEA_LANTERN;
    private static final Material YELLOW_GLASS = Material.YELLOW_STAINED_GLASS;
    private static final Material PURPLE_GLASS = Material.PURPLE_STAINED_GLASS;
    private static final Material ROD = Material.LIGHTNING_ROD;
    private static final Material CHAIN = Material.IRON_CHAIN;

    private StormDragonNestDesign() {}

    static void build(DragonNest s) {
        fulguriteIsland(s);
        astrolabeLightningFloor(s);
        conductorNestRimAndHoards(s);
        sixTempestPylonsAndArmillaryRings(s);
        curvedStormColonnades(s);
        northDomedStormRotunda(s);
        southConductorCauseway(s);
        // Keep the 109-block central dragon basin and arrival spine completely clear.
        s.clearOpenCaldera(s.kind.arenaRadius - 1, s.kind.maxY);
        s.box(-5, s.floor + 1, s.kind.chestZ + 4, 5, s.floor + 9, s.kind.arrivalZ + 2, Material.AIR);
    }

    private static void fulguriteIsland(DragonNest s) {
        int f = s.floor;
        for (int x = -112; x <= 112; x++) {
            for (int z = -112; z <= 112; z++) {
                double d = Math.hypot(x, z);
                double angle = Math.atan2(z, x);
                double lobe = 95.0
                    + 10.0 * Math.cos(angle * 6.0)
                    + 4.5 * Math.cos(angle * 12.0)
                    + (z < -48 ? 14.0 : 0.0)
                    + (z > 52 && Math.abs(x) < 24 ? 11.0 : 0.0);
                if (d > lobe) continue;
                double edge = d / lobe;
                int depth = (int) Math.round(50.0 * Math.pow(1.0 - edge, 0.72)
                    + 6.0 * Math.cos(x * 0.19) * Math.sin(z * 0.19));
                int y1 = Math.max(DragonNest.MIN_Y + 4, f - 8 - depth);
                s.box(x, y1, z, x, f - 13, z, TILES);
                s.box(x, Math.max(y1, f - 12), z, x, f - 5, z, DEEPSLATE);
                s.box(x, f - 4, z, x, f - 1, z, PRISMARINE);
                s.block(x, f, z, (edge > 0.80 && Math.floorMod(x * 5 + z * 3, 6) == 0) ? GILDED : POLISHED);
                if (edge > 0.40 && edge < 0.88 && Math.floorMod(x * 7 + z * 11, 31) == 0) {
                    s.box(x, y1, z, x, Math.min(f - 6, y1 + 8), z, OXIDIZED);
                    s.block(x, y1 - 1, z, OCHRE);
                }
            }
        }
        // 6 Underside Magnetic Resonator Coils hanging beneath the island
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(30 + i * 60);
            int cx = (int) Math.round(Math.cos(a) * 68);
            int cz = (int) Math.round(Math.sin(a) * 68);
            int bottom = DragonNest.MIN_Y + 2;
            for (int y = bottom; y <= f - 14; y++) {
                int r = Math.max(0, (y - bottom) / 5);
                s.disk(cx, y, cz, r, y % 4 == 0 ? OX_CUT : (y % 7 == 0 ? GILDED : DEEPSLATE));
            }
            s.ring(cx, bottom + 6, cz, 4.5, 1.1, GOLD);
            s.block(cx, bottom - 1, cz, OCHRE);
        }
    }

    private static void astrolabeLightningFloor(DragonNest s) {
        int f = s.floor, r = s.kind.arenaRadius;
        s.disk(0, f - 3, 0, r - 2, TILES);
        s.disk(0, f - 2, 0, r - 3, AMETHYST);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6.0;
            int lx = (int) Math.round(Math.cos(a) * 30);
            int lz = (int) Math.round(Math.sin(a) * 30);
            s.disk(lx, f - 2, lz, 5, i % 2 == 0 ? OCHRE : LANTERN);
        }
        s.disk(0, f - 2, 0, 12, OCHRE);

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double d = Math.hypot(x, z);
                if (d > r + 0.5) continue;
                double a = Math.atan2(z, x);
                boolean ringOuter = Math.abs(d - (r - 2.5)) < 1.8;
                boolean ringMid = Math.abs(d - 36.0) < 1.6;
                boolean ringInner = Math.abs(d - 18.0) < 1.5;
                double spoke12 = Math.abs(Math.sin(a * 6.0 + 0.18 * Math.sin(d * 0.35)));
                Material top = POLISHED;
                Material under = DEEPSLATE;

                if (d < 7.5) {
                    top = d < 3.0 ? GOLD : (d < 5.5 ? GILDED : YELLOW_GLASS);
                    under = OCHRE;
                } else if (ringOuter || ringMid || ringInner) {
                    top = Math.floorMod(x * 3 + z * 5, 4) == 0 ? GOLD : (ringMid ? OX_CUT : GILDED);
                    under = GILDED;
                } else if (spoke12 < 0.14 && d < r - 3) {
                    top = d % 5 < 2 ? GOLD : YELLOW_GLASS;
                    under = OCHRE;
                } else if (spoke12 > 0.90 && d > 18 && d < r - 4) {
                    top = PURPLE_GLASS;
                    under = LANTERN;
                } else if ((Math.floorMod(x * 5 - z * 3, 11) == 0)) {
                    top = PRISMARINE;
                } else if ((Math.floorMod(x * 9 + z * 7, 23) == 0)) {
                    top = GILDED;
                }
                s.block(x, f - 1, z, under);
                s.block(x, f, z, top);
            }
        }
    }

    private static void conductorNestRimAndHoards(DragonNest s) {
        int f = s.floor;
        for (int x = -76; x <= 76; x++) {
            for (int z = -76; z <= 76; z++) {
                if (Math.abs(x) < 14 && (z < -48 || z > 48)) continue;
                double d = Math.hypot(x, z);
                if (d < 54.5 || d > 73.5) continue;
                double a = Math.atan2(z, x);
                double wave = Math.cos(a * 12.0) * 2.2 + Math.sin(a * 6.0) * 1.6;
                double profile = 1.0 - Math.abs(d - 63.5) / 9.5;
                int h = Math.max(1, (int) Math.round(profile * 8.5 + wave));
                for (int dy = 1; dy <= h; dy++) {
                    Material m;
                    if (dy == h && Math.floorMod(x + z, 5) == 0) m = GILDED;
                    else if ((x + z + dy) % 4 == 0) m = OX_CUT;
                    else if ((x * 5 - z) % 6 == 0) m = PRISMARINE;
                    else m = DEEPSLATE;
                    s.block(x, f + dy, z, m);
                }
                if (Math.abs(d - 60.0) < 0.9 && Math.floorMod(x * 7 + z * 3, 17) == 0) {
                    s.block(x, f + h + 1, z, COPPER);
                    s.block(x, f + h + 2, z, ROD, BlockFace.UP);
                }
            }
        }

        int[][] hoards = {
            {-49, -26, 6}, {49, -26, 6}, {-51, 14, 5}, {51, 14, 5},
            {-37, 42, 6}, {37, 42, 6}, {-31, -46, 5}, {31, -46, 5}
        };
        for (int[] h : hoards) {
            s.hoardMound(h[0], f + 1, h[1], h[2], GILDED, GOLD, AMETHYST, OCHRE);
        }
    }

    private static void sixTempestPylonsAndArmillaryRings(DragonNest s) {
        int f = s.floor;
        double[] angles = {
            Math.toRadians(28), Math.toRadians(84), Math.toRadians(152),
            Math.toRadians(208), Math.toRadians(276), Math.toRadians(332)
        };
        int[] px = new int[angles.length];
        int[] pz = new int[angles.length];
        int[] pyTop = new int[angles.length];

        for (int i = 0; i < angles.length; i++) {
            double a = angles[i];
            double cos = Math.cos(a), sin = Math.sin(a);
            int bx = (int) Math.round(cos * 84), bz = (int) Math.round(sin * 84);
            int height = 82 + (i % 2) * 10;
            s.cylinder(bx, f + 1, f + 16, bz, 7, DEEPSLATE);
            s.ring(bx, f + 8, bz, 7.5, 1.4, GILDED);
            s.ring(bx, f + 16, bz, 7.8, 1.5, OX_CUT);

            int tx = (int) Math.round(cos * 68), tz = (int) Math.round(sin * 68);
            px[i] = tx;
            pz[i] = tz;
            pyTop[i] = f + height;
            s.line(bx, f + 14, bz, tx, f + height, tz, 3.4, DEEPSLATE);
            s.line(bx + cos * 2, f + 14, bz + sin * 2, tx + cos * 2, f + height, tz + sin * 2, 1.4, OXIDIZED);
            s.line(bx - cos * 2, f + 14, bz - sin * 2, tx - cos * 2, f + height, tz - sin * 2, 1.2, GILDED);

            for (int coil = 1; coil <= 4; coil++) {
                double t = coil / 5.0;
                int cx = (int) Math.round(bx + (tx - bx) * t);
                int cz = (int) Math.round(bz + (tz - bz) * t);
                int cy = f + 16 + (int) Math.round((height - 16) * t);
                double cr = 7.5 - coil * 0.8;
                s.ring(cx, cy, cz, cr, 1.3, coil % 2 == 0 ? GOLD : OX_CUT);
                s.ring(cx, cy + 1, cz, cr - 0.5, 0.9, OCHRE);
            }

            s.cylinder(tx, f + height - 2, f + height + 2, tz, 4, GOLD);
            s.cylinder(tx, f + height - 1, f + height + 1, tz, 3, OCHRE);
            for (int dy = 3; dy <= 12; dy++) {
                s.block(tx, f + height + dy, tz, dy < 8 ? COPPER : ROD);
            }
        }

        // Tilted 3D Armillary Storm Orbits around the outer perimeter (r = 72..82, keeping central r <= 54 open!)
        tiltedOuterOrbit(s, f + 58, 76.0, 14.0, Math.toRadians(25), GOLD, OX_CUT);
        tiltedOuterOrbit(s, f + 68, 70.0, -14.0, Math.toRadians(115), GILDED, OCHRE);

        for (int i = 0; i < angles.length; i++) {
            int next = (i + 1) % angles.length;
            if (Math.hypot(px[i] - px[next], pz[i] - pz[next]) > 110) continue;
            s.catenary(px[i], pyTop[i] - 6, pz[i], px[next], pyTop[next] - 6, pz[next], 7.0, CHAIN);
        }
    }

    private static void tiltedOuterOrbit(DragonNest s, double cy, double radius, double tiltY, double phase, Material primary, Material glow) {
        int steps = 180;
        double px = 0, py = 0, pz = 0;
        for (int i = 0; i <= steps; i++) {
            double a = i * (Math.PI * 2.0) / steps;
            double x = Math.cos(a) * radius;
            double z = Math.sin(a) * radius;
            double y = cy + Math.sin(a + phase) * tiltY;
            if (i > 0) {
                s.line(px, py, pz, x, y, z, 1.25, i % 6 == 0 ? glow : primary);
            }
            px = x;
            py = y;
            pz = z;
        }
    }

    private static void curvedStormColonnades(DragonNest s) {
        int f = s.floor;
        // East & West Curved Observatory Arcades with suspended Amethyst-Gold Plasma Resonators
        for (int side : new int[]{-1, 1}) {
            double start = side < 0 ? Math.toRadians(146) : Math.toRadians(-34);
            double end = side < 0 ? Math.toRadians(214) : Math.toRadians(34);
            int rad = 96;
            for (int j = 0; j <= 5; j++) {
                double a = start + (end - start) * j / 5.0;
                int x = (int) Math.round(Math.cos(a) * rad);
                int z = (int) Math.round(Math.sin(a) * rad);
                s.cylinder(x, f + 1, f + 30, z, 3, DEEPSLATE);
                s.ring(x, f + 15, z, 3.6, 1.1, OX_CUT);
                s.ring(x, f + 30, z, 3.8, 1.2, GOLD);
                s.block(x, f + 31, z, OCHRE);
                s.box(x, f + 32, z, x, f + 36, z, ROD);
                if (j < 5) {
                    double nextA = start + (end - start) * (j + 1) / 5.0;
                    int nx = (int) Math.round(Math.cos(nextA) * rad);
                    int nz = (int) Math.round(Math.sin(nextA) * rad);
                    s.line(x, f + 28, z, nx, f + 28, nz, 1.8, OX_CUT);
                    s.line(x, f + 30, z, nx, f + 30, nz, 1.0, GOLD);
                    // Suspended Amethyst & Gold resonator crystal in the middle of each arch bay
                    int mx = (x + nx) / 2, mz = (z + nz) / 2;
                    s.box(mx, f + 20, mz, mx, f + 27, mz, CHAIN);
                    s.cylinder(mx, f + 16, f + 19, mz, 2, AMETHYST);
                    s.ring(mx, f + 17, mz, 2.4, 0.9, GOLD);
                    s.block(mx, f + 15, mz, OCHRE);
                }
            }
        }
    }

    private static void northDomedStormRotunda(DragonNest s) {
        int f = s.floor, cz = -83, rx = 34, rz = 25;
        // Stepped circular/elliptical observatory terrace
        for (int dy = 1; dy <= 5; dy++) {
            s.ellipse(0, f + dy, cz, rx + 6 - dy, rz + 5 - dy, dy == 5 ? GILDED : DEEPSLATE);
        }
        for (int step = 0; step < 5; step++) {
            int z = -54 - step * 2;
            s.box(-13, f + 1 + step, z, 13, f + 1 + step, z + 1, step % 2 == 0 ? OX_CUT : GILDED);
        }

        int fy = f + 5;
        // Cylindrical/Elliptical Rotunda Drum (fy + 1 .. fy + 44)
        for (int y = fy + 1; y <= fy + 44; y++) {
            s.ellipse(0, y, cz, rx, rz, DEEPSLATE);
            s.ellipse(0, y, cz, rx - 3, rz - 3, Material.AIR);
        }
        s.ellipse(0, fy, cz, rx - 2, rz - 2, PRISMARINE);
        s.box(-5, fy, -102, 5, fy, -58, GILDED);
        s.box(-2, fy, -100, 2, fy, -60, GOLD);

        for (int y : new int[]{fy + 4, fy + 22, fy + 40, fy + 44}) {
            s.ellipseRing(0, y, cz, rx + 1, rz + 1, 1.5, y == fy + 40 ? GOLD : OX_CUT);
        }

        // Tall arched portal opening south toward the nest
        for (int x = -10; x <= 10; x++) {
            int archH = fy + 22 - (x * x) / 6;
            s.box(x, fy + 1, -64, x, archH, -56, Material.AIR);
            s.box(x, archH + 1, -61, x, archH + 3, -57, OX_CUT);
            s.block(x, archH + 4, -58, GOLD);
        }

        // 12 Radial Buttress Piers & Stained-Glass Lancet Windows around the Rotunda Drum
        for (int i = 0; i < 12; i++) {
            double a = i * (Math.PI * 2.0) / 12.0;
            // Skip the south portal opening
            if (Math.sin(a) > 0.72) continue;
            int px = (int) Math.round(Math.cos(a) * (rx + 1));
            int pz = cz + (int) Math.round(Math.sin(a) * (rz + 1));
            s.cylinder(px, fy + 1, fy + 48, pz, 2, GILDED);
            s.block(px, fy + 49, pz, OCHRE);
            s.box(px, fy + 50, pz, px, fy + 54, pz, ROD);
            // Stained-glass storm window between piers
            double wa = a + Math.PI / 12.0;
            if (Math.sin(wa) <= 0.72) {
                int wx = (int) Math.round(Math.cos(wa) * (rx - 1));
                int wz = cz + (int) Math.round(Math.sin(wa) * (rz - 1));
                s.box(wx - 1, fy + 10, wz - 1, wx + 1, fy + 36, wz + 1, i % 2 == 0 ? YELLOW_GLASS : PURPLE_GLASS);
            }
        }

        // 12-Radius Storm-Astrolabe Rose Window above the south portal
        int roseY = fy + 33, roseZ = -58;
        for (int x = -11; x <= 11; x++) {
            for (int y = -11; y <= 11; y++) {
                double r = Math.hypot(x, y);
                if (r > 11.5) continue;
                Material m;
                if (r > 9.8 || Math.abs(r - 5.5) < 0.9) m = OX_CUT;
                else if (x == 0 || y == 0 || Math.abs(x) == Math.abs(y)) m = GOLD;
                else m = ((x + y) & 1) == 0 ? YELLOW_GLASS : PURPLE_GLASS;
                s.block(x, roseY + y, roseZ, m);
            }
        }
        s.disk(0, roseY, roseZ, 2, OCHRE);

        // Colossal Ribbed Oxidized-Copper & Gold Observatory Dome (fy + 45 .. fy + 86)
        for (int h = 1; h <= 42; h++) {
            double angle = (h / 42.0) * (Math.PI / 2.0);
            double drx = Math.max(3.0, rx * Math.cos(angle));
            double drz = Math.max(3.0, rz * Math.cos(angle));
            Material domeMat = (h % 10 == 0) ? GOLD : (h % 2 == 0 ? OX_CUT : PRISMARINE);
            s.ellipseRing(0, fy + 44 + h, cz, drx, drz, 1.6, domeMat);
        }
        // 16 Golden Meridian Ribsarching up the Dome
        for (int rib = 0; rib < 16; rib++) {
            double a = rib * (Math.PI * 2.0) / 16.0;
            double px = Math.cos(a) * rx, py = fy + 44, pz = cz + Math.sin(a) * rz;
            for (int h = 3; h <= 42; h += 3) {
                double angle = (h / 42.0) * (Math.PI / 2.0);
                double nx = Math.cos(a) * rx * Math.cos(angle);
                double nz = cz + Math.sin(a) * rz * Math.cos(angle);
                double ny = fy + 44 + h;
                s.line(px, py, pz, nx, ny, nz, 0.95, GOLD);
                px = nx;
                py = ny;
                pz = nz;
            }
        }

        // Apex Cupola Lantern & Towering Lightning Needle (rising to fy + 132!)
        for (int y = fy + 86; y <= fy + 104; y++) {
            s.ellipseRing(0, y, cz, 6, 6, 1.2, y % 4 == 0 ? GOLD : OX_CUT);
            s.disk(0, y, cz, 3, y % 3 == 0 ? OCHRE : YELLOW_GLASS);
        }
        for (int h = 0; h <= 28; h++) {
            int sr = Math.max(0, 6 - h / 4);
            s.cylinder(0, fy + 105 + h, fy + 105 + h, cz, sr, h % 3 == 0 ? GOLD : COPPER);
        }
        s.box(0, fy + 134, cz, 0, fy + 142, cz, ROD);

        // Twin Flanking Tesla Side-Towers at (-38, -76) and (38, -76)
        for (int side : new int[]{-1, 1}) {
            int tx = side * 38, tz = -78;
            s.cylinder(tx, fy + 1, fy + 68, tz, 7, DEEPSLATE);
            for (int ry = fy + 12; ry <= fy + 64; ry += 13) {
                s.ring(tx, ry, tz, 7.6, 1.4, ry % 26 == 0 ? GOLD : OX_CUT);
                s.ring(tx, ry + 1, tz, 7.0, 0.9, OCHRE);
            }
            for (int h = 0; h <= 20; h++) {
                s.cylinder(tx, fy + 69 + h, fy + 69 + h, tz, Math.max(0, 7 - h / 3), h % 3 == 0 ? GOLD : OX_CUT);
            }
            s.box(tx, fy + 90, tz, tx, fy + 96, tz, ROD);
        }

        // Central Fulcrum Dynamo Altar + 8 Storm Treasure Chests inside the Rotunda
        s.cylinder(0, fy + 1, fy + 4, -86, 7, GILDED);
        s.cylinder(0, fy + 5, fy + 6, -86, 5, OX_CUT);
        s.block(0, fy + 7, -86, GOLD);
        s.block(0, fy + 8, -86, OCHRE);
        s.block(0, fy + 9, -86, ROD, BlockFace.UP);

        int[] chestZs = {-72, -78, -84, -90};
        for (int czBay : chestZs) {
            s.hoardMound(-20, fy + 1, czBay, 4, GILDED, GOLD, AMETHYST, OCHRE);
            s.hoardMound(20, fy + 1, czBay, 4, GILDED, GOLD, AMETHYST, OCHRE);
            s.treasure(-15, fy + 1, czBay, BlockFace.EAST);
            s.treasure(15, fy + 1, czBay, BlockFace.WEST);
        }
    }

    private static void southConductorCauseway(DragonNest s) {
        int f = s.floor;
        s.box(-12, f - 4, 54, 12, f, 110, DEEPSLATE);
        s.box(-13, f + 1, 56, -11, f + 3, 108, OX_CUT);
        s.box(11, f + 1, 56, 13, f + 3, 108, OX_CUT);
        s.box(-4, f, 54, 4, f, 110, GILDED);
        s.box(-1, f, 54, 1, f, 110, GOLD);

        for (int z : new int[]{68, 86, 102}) {
            for (int side : new int[]{-1, 1}) {
                int px = side * 14;
                s.box(px - 2, f + 1, z - 2, px + 2, f + 18, z + 2, DEEPSLATE);
                s.ring(px, f + 12, z, 3.2, 1.0, OX_CUT);
                s.ring(px, f + 18, z, 3.5, 1.1, GOLD);
                s.line(px, f + 18, z, side * 4, f + 34, z, 1.8, OX_CUT);
                s.line(px, f + 19, z, side * 4, f + 35, z, 0.9, GOLD);
                s.block(side * 4, f + 35, z, OCHRE);
                s.block(side * 4, f + 36, z, ROD, BlockFace.UP);
            }
        }
    }
}
