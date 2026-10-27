package com.example.voidscape.world;

import org.bukkit.Material;

/**
 * The armed Valkyrie's shrine is a pair of unfurled wings, not a walled cathedral.
 * Its feathered islands, suspended bridges and enormous carved flight feathers
 * remain open to the sky, with a complete, unobstructed battle disc underneath.
 */
final class ValkyrieTempleDesign {
    private static final Material IVORY = Material.SMOOTH_QUARTZ;
    private static final Material EDGE = Material.CHISELED_QUARTZ_BLOCK;
    private static final Material GOLD = Material.GOLD_BLOCK;
    private static final Material BLUE = Material.LIGHT_BLUE_STAINED_GLASS;
    private static final Material LIGHT = Material.SEA_LANTERN;

    private ValkyrieTempleDesign() {}

    static void build(BossStructure b) {
        island(b);
        battleRose(b);
        processionalBridge(b);
        for (int side : new int[] {-1, 1}) {
            featherTerraces(b, side);
            bridges(b, side);
            gardens(b, side);
            greatWing(b, side);
            flyingRibs(b, side);
            bladeMemorial(b, side);
        }
        treasury(b);
        triumphalBlade(b);
        arrivalGate(b);
        // This is a real combat floor: decorations may cross it only high overhead.
        for (int x = -b.kind.arenaRadius; x <= b.kind.arenaRadius; x++) {
            for (int z = -b.kind.arenaRadius; z <= b.kind.arenaRadius; z++) {
                if (x * x + z * z <= b.kind.arenaRadius * b.kind.arenaRadius) {
                    b.box(x, b.floor + 1, z, x, b.floor + 28, z, Material.AIR);
                }
            }
        }
    }

    private static void island(BossStructure b) {
        int f = b.floor;
        // A shallow lenticular island, rimmed by three visibly separate stone layers.
        for (int x = -52; x <= 52; x++) {
            for (int z = -52; z <= 52; z++) {
                double r = Math.hypot(x, z);
                if (r > 52) continue;
                int depth = 5 + (int) Math.round(20 * Math.pow(1 - r / 52, .75));
                b.box(x, f - depth, z, x, f - 5, z, Material.CALCITE);
                b.box(x, f - 4, z, x, f - 3, z, Material.QUARTZ_BRICKS);
                b.block(x, f - 2, z, r > 49 ? GOLD : IVORY);
                b.block(x, f - 1, z, IVORY);
                b.block(x, f, z, r > 49 ? EDGE : Material.CALCITE);
            }
        }
        b.ring(0, f - 6, 0, 48.5, 1.5, Material.DARK_PRISMARINE);
        b.ring(0, f - 5, 0, 49, 1.5, BLUE);
        // Underside spokes make the shrine look built and suspended, rather than solid land.
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI / 6;
            b.line(0, f - 25, 0, 45 * Math.cos(angle), f - 7,
                    45 * Math.sin(angle), 1.2, GOLD);
        }
        hangingCrystal(b, 0, f - 26, 0, 23, 5);
        for (int i = 0; i < 12; i++) {
            double angle = i * Math.PI / 6;
            int x = round(48 * Math.cos(angle)), z = round(48 * Math.sin(angle));
            b.block(x, f, z, LIGHT);
            if (Math.abs(z) > 45 && Math.abs(x) < 9) continue;
            b.box(x - 1, f + 1, z - 1, x + 1, f + 2, z + 1, EDGE);
            b.block(x, f + 3, z, LIGHT);
        }
    }

    private static void battleRose(BossStructure b) {
        int f = b.floor, radius = b.kind.arenaRadius;
        // A twelve-point compass of blades; its fine inlay is entirely flush with the floor.
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double r = Math.hypot(x, z);
                if (r > radius) continue;
                double angle = Math.atan2(z, x);
                double nearest = Math.abs(Math.sin(angle * 6));
                double curve = Math.abs(Math.sin(angle * 6 + r * .026));
                Material m = Material.CALCITE;
                if (r < 5) m = Material.BLUE_GLAZED_TERRACOTTA;
                else if (r < 13 && nearest < .20) m = GOLD;
                else if (r > 15 && r < 39 && curve < .045) m = Material.QUARTZ_BRICKS;
                if (Math.abs(r - 13) < .55 || Math.abs(r - 40) < .55) m = GOLD;
                if (r > radius - 1.3) m = Material.DARK_PRISMARINE;
                if (r > radius - .55) m = GOLD;
                b.block(x, f, z, m);
            }
        }
        b.ring(0, f, 0, 47, .65, GOLD);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            int x = round(42 * Math.cos(a)), z = round(42 * Math.sin(a));
            b.block(x, f, z, LIGHT);
        }
    }

    private static void processionalBridge(BossStructure b) {
        int f = b.floor;
        platform(b, 0, 80, 16, 14, f, 8);
        // The arrival is at z86, and the generous central bridge remains at one level.
        b.box(-6, f - 3, 44, 6, f - 1, 87, IVORY);
        b.box(-6, f, 44, 6, f, 87, Material.QUARTZ_BRICKS);
        for (int x : new int[] {-6, 6}) {
            b.box(x, f, 47, x, f, 84, GOLD);
            b.box(x, f - 3, 48, x, f - 3, 84, BLUE);
        }
        for (int z = 48; z <= 84; z += 6) {
            b.box(-4, f, z, 4, f, z, EDGE);
            for (int side : new int[] {-1, 1}) {
                b.block(side * 6, f + 1, z, EDGE);
                b.block(side * 6, f + 2, z, LIGHT);
            }
        }
        b.ellipseRing(0, f, 80, 15, 13, .8, GOLD);
        for (int side : new int[] {-1, 1}) {
            b.line(side * 8, f - 5, 48, side * 9, f - 12, 65, 1.2, GOLD);
            b.line(side * 9, f - 12, 65, side * 8, f - 5, 84, 1.2, GOLD);
        }
        hangingCrystal(b, 0, f - 10, 82, 11, 3);
    }

    private static void featherTerraces(BossStructure b, int side) {
        int f = b.floor;
        // A curved leading edge and separated trailing feathers form the wing plan.
        for (int x = 47; x <= 91; x++) {
            double t = (x - 47) / 44.0;
            double center = -22 - 14 * t;
            double width = 24 * Math.pow(1 - t, .55) + 3;
            for (int z = -66; z <= 10; z++) {
                double d = Math.abs(z - center);
                if (d > width) continue;
                int depth = 5 + round(8 * (1 - d / width) * (1 - t));
                b.box(side * x, f - depth, z, side * x, f - 3, z, Material.CALCITE);
                b.box(side * x, f - 2, z, side * x, f - 1, z, IVORY);
                b.block(side * x, f, z, d > width - 1.5 ? EDGE : Material.QUARTZ_BRICKS);
                if (d > width - .65) b.block(side * x, f - 2, z, GOLD);
            }
        }
        for (int feather = 0; feather < 5; feather++) {
            double startX = 48 + feather * 2, startZ = -7 + feather * 10;
            double endX = 91 - feather * 5, endZ = 10 + feather * 11;
            double controlX = 75 + feather, controlZ = startZ + 4;
            int level = f - 1 - feather % 2;
            for (int step = 0; step <= 48; step++) {
                double t = step / 48.0;
                double x = bezier(startX, controlX, endX, t);
                double z = bezier(startZ, controlZ, endZ, t);
                int width = Math.max(1, round(5.2 * Math.pow(1 - t, .55)));
                int px = side * round(x), pz = round(z);
                b.box(px - width, level - 4, pz - width, px + width, level - 2, pz + width, Material.CALCITE);
                b.box(px - width, level - 1, pz - width, px + width, level, pz + width, IVORY);
                if (step % 3 == 0) b.block(px, level, pz, GOLD);
                if (step > 40) b.block(px, level - 2, pz, BLUE);
            }
            hangingCrystal(b, side * round(endX), level - 6, round(endZ), 9 + feather * 2, 2);
        }
        // An inlaid route joins the separate terrace feathers without covering their gaps.
        for (int z = -45; z <= 36; z++) {
            int x = side * round(57 + 3 * Math.sin(z / 20.0));
            b.box(x - 3, f - 3, z, x + 3, f - 1, z, IVORY);
            b.box(x - 3, f, z, x + 3, f, z, Material.QUARTZ_BRICKS);
            b.block(x, f, z, GOLD);
        }
    }

    private static void bridges(BossStructure b, int side) {
        int f = b.floor;
        // Two swept branches create actual empty space between the arrival and wing isles.
        horizontalBridge(b, side, 9, 76, 44, 78, 58, 31, 4);
        horizontalBridge(b, side, 21, -49, 40, -66, 63, -43, 4);
        for (int z : new int[] {-44, 29}) {
            int x = side * 60;
            b.ellipseRing(x, f, z, 9, 7, .8, GOLD);
            b.box(x - 1, f - 10, z - 1, x + 1, f - 5, z + 1, EDGE);
        }
    }

    private static void horizontalBridge(BossStructure b, int side, double ax, double az,
                                          double cx, double cz, double bx, double bz, int width) {
        int f = b.floor;
        for (int step = 0; step <= 100; step++) {
            double t = step / 100.0;
            double px = bezier(ax, cx, bx, t), pz = bezier(az, cz, bz, t);
            double dx = 2 * (1 - t) * (cx - ax) + 2 * t * (bx - cx);
            double dz = 2 * (1 - t) * (cz - az) + 2 * t * (bz - cz);
            double length = Math.hypot(dx, dz);
            for (int offset = -width; offset <= width; offset++) {
                int x = side * round(px - dz / length * offset), z = round(pz + dx / length * offset);
                b.box(x, f - 3, z, x, f - 1, z, IVORY);
                b.block(x, f, z, Math.abs(offset) == width ? GOLD : Material.QUARTZ_BRICKS);
                if (Math.abs(offset) == width) {
                    b.block(x, f - 4, z, BLUE);
                    if (step % 11 == 0) {
                        b.block(x, f + 1, z, EDGE);
                        b.block(x, f + 2, z, LIGHT);
                    }
                }
            }
        }
    }

    private static void gardens(BossStructure b, int side) {
        int f = b.floor;
        for (int i = 0; i < 3; i++) {
            int x = side * (66 + i * 5), z = -39 + i * 15;
            int y = f + 1 + i;
            for (int level = f - 3; level < y; level++) {
                b.ellipse(x, level, z, 8 - i, 8, level == y - 1 ? GOLD : Material.CALCITE);
            }
            b.ellipse(x, y, z, 8 - i, 8, EDGE);
            b.ellipse(x, y + 1, z, 6 - i, 6, Material.MOSS_BLOCK);
            b.ellipseRing(x, y + 1, z, 7 - i, 7, .6, GOLD);
            for (int dx : new int[] {-3, 3}) {
                b.box(x + dx - 1, y + 2, z - 2, x + dx + 1, y + 3, z + 2, Material.FLOWERING_AZALEA_LEAVES);
            }
            b.block(x, y + 2, z - 3, Material.BLUE_ORCHID);
            b.block(x, y + 2, z + 3, Material.WHITE_TULIP);
            b.block(x, y + 2, z, LIGHT);
        }
        // Bounded reflecting pools are glass mosaics, so generation never spills water.
        for (int z : new int[] {-49, -14, 23}) {
            int x = side * 56;
            b.ellipse(x, f, z, 5, 7, EDGE);
            b.ellipse(x, f, z, 3, 5, BLUE);
            b.ellipse(x, f - 1, z, 3, 5, Material.PRISMARINE_BRICKS);
            b.block(x, f - 1, z, LIGHT);
            b.ellipseRing(x, f, z, 4, 6, .5, GOLD);
        }
        for (int z : new int[] {-38, 6, 32}) {
            int x = side * 83;
            for (int level = f - 3; level <= f; level++) {
                b.ellipse(x, level, z, 3, 3, level == f ? EDGE : IVORY);
            }
            b.box(x - 1, f + 1, z - 1, x + 1, f + 5, z + 1, EDGE);
            b.block(x, f + 6, z, GOLD);
            b.block(x, f + 7, z, LIGHT);
        }
    }

    private static void greatWing(BossStructure b, int side) {
        int f = b.floor;
        // A rounded, flowing shoulder joins every quill into one raised angel wing.
        // Its surface folds in depth, rather than becoming a flat triangular sign.
        wingShoulder(b, side);
        wingWeb(b, side);
        // The load-bearing wing bone rises from its own island and sweeps upward.
        curve(b, side, 53, f + 2, -36, 91, f + 44, -40,
                68, f + 100, -38, 3.5, EDGE);
        curve(b, side, 51, f + 5, -34, 83, f + 48, -38,
                66, f + 97, -36, 1.1, GOLD);
        // Individually tapered, flat flight feathers with alternating recessed blue seams.
        for (int feather = 0; feather < 7; feather++) {
            double rootX = 66 - feather * 3.4;
            double rootY = f + 84 - feather * 6.4;
            double rootZ = -37 + feather * 5.6;
            double tipX = 84 + Math.sin(feather * .55) * 8 - feather * 2;
            double tipY = f + 134 - feather * 13.8;
            double tipZ = -47 + feather * 15.2;
            double controlX = 92 - feather * 1.7;
            double controlY = rootY + 36 - feather * 2;
            double controlZ = rootZ + 5;
            carvedFeather(b, side, rootX, rootY, rootZ, controlX, controlY, controlZ,
                    tipX, tipY, tipZ, feather);
        }
        hangingCrystal(b, side * 53, f - 14, -36, 17, 3);
    }

    private static void wingShoulder(BossStructure b, int side) {
        int f = b.floor;
        for (int strip = 0; strip <= 120; strip++) {
            double feather = strip / 20.0, q = feather / 6;
            double rootX = 66 - feather * 3.4;
            double rootY = f + 84 - feather * 6.4;
            double rootZ = -37 + feather * 5.6;
            for (int step = 0; step <= 100; step++) {
                double t = step / 100.0;
                double x = bezier(56 - q * 3, 74 - q * 17, rootX, t);
                double y = bezier(f + 5, f + 54 - q * 24, rootY, t);
                double z = bezier(-36 + q * 3, -40 + q * 25, rootZ, t);
                double groove = feather - Math.floor(feather);
                Material surface = groove < .08 && t > .25 ? Material.QUARTZ_BRICKS : IVORY;
                int px = side * round(x), py = round(y), pz = round(z);
                b.box(px - 1, py, pz - 4, px + 1, py, pz - 1, Material.CALCITE);
                b.box(px - 1, py, pz, px + 1, py, pz, surface);
                if (groove > .10 && groove < .20 && t > .40) {
                    b.block(px, py, pz + 1, EDGE);
                }
            }
        }
        // These flowing quills articulate the shoulder and continue into the flight feathers.
        for (int feather = 0; feather <= 6; feather++) {
            double q = feather / 6.0;
            double previousX = 56 - q * 3, previousY = f + 5, previousZ = -36 + q * 3;
            for (int step = 1; step <= 80; step++) {
                double t = step / 80.0;
                double x = bezier(56 - q * 3, 74 - q * 17, 66 - feather * 3.4, t);
                double y = bezier(f + 5, f + 54 - q * 24, f + 84 - feather * 6.4, t);
                double z = bezier(-36 + q * 3, -40 + q * 25, -37 + feather * 5.6, t);
                if (t > .28) {
                    b.line(side * previousX, previousY, previousZ + 1,
                            side * x, y, z + 1, .55, feather % 2 == 0 ? GOLD : BLUE);
                }
                previousX = x;
                previousY = y;
                previousZ = z;
            }
        }
    }

    private static void wingWeb(BossStructure b, int side) {
        int f = b.floor;
        // Join the proximal feathers into a broad fan, but leave their long tips separated.
        // The curved edge and overlapping carved layers retain a feathered silhouette.
        for (int strip = 0; strip <= 120; strip++) {
            double feather = strip / 20.0;
            double end = .44 + .21 * Math.sin(feather * Math.PI / 6);
            double rootX = 66 - feather * 3.4;
            double rootY = f + 84 - feather * 6.4;
            double rootZ = -37 + feather * 5.6;
            double tipX = 84 + Math.sin(feather * .55) * 8 - feather * 2;
            double tipY = f + 134 - feather * 13.8;
            double tipZ = -47 + feather * 15.2;
            double controlX = 92 - feather * 1.7;
            double controlY = rootY + 36 - feather * 2;
            double controlZ = rootZ + 5;
            for (int step = 0; step <= 90; step++) {
                double t = end * step / 90.0;
                int px = side * round(bezier(rootX, controlX, tipX, t));
                int py = round(bezier(rootY, controlY, tipY, t));
                int pz = round(bezier(rootZ, controlZ, tipZ, t));
                double groove = feather - Math.floor(feather);
                Material surface = groove < .13 ? Material.QUARTZ_BRICKS : IVORY;
                b.box(px - 1, py, pz - 4, px + 1, py, pz - 1, Material.CALCITE);
                b.box(px - 1, py, pz, px + 1, py, pz, surface);
                if (step >= 87) {
                    b.box(px - 1, py, pz + 1, px + 1, py, pz + 1, EDGE);
                    if (strip % 20 < 2) b.block(px, py, pz + 2, GOLD);
                }
            }
        }
    }

    private static void carvedFeather(BossStructure b, int side, double ax, double ay, double az,
                                       double cx, double cy, double cz,
                                       double bx, double by, double bz, int feather) {
        for (int step = 0; step <= 72; step++) {
            double t = step / 72.0;
            double x = bezier(ax, cx, bx, t), y = bezier(ay, cy, by, t), z = bezier(az, cz, bz, t);
            double dx = 2 * (1 - t) * (cx - ax) + 2 * t * (bx - cx);
            double dy = 2 * (1 - t) * (cy - ay) + 2 * t * (by - cy);
            double length = Math.max(.01, Math.hypot(dx, dy));
            int width = Math.max(0, round((4.5 + feather % 2) * Math.sin(Math.PI * (.11 + .89 * t))));
            int thickness = t < .7 ? 1 : 0;
            for (int offset = -width; offset <= width; offset++) {
                int px = side * round(x - dy / length * offset), py = round(y + dx / length * offset);
                int pz = round(z);
                Material material = Math.abs(offset) == width && width > 1 ? EDGE : IVORY;
                b.box(px, py, pz - thickness, px, py, pz + thickness, material);
                if (offset == -width && width > 1) b.block(px, py, pz - thickness - 1, GOLD);
                if (offset == 0) b.block(px, py, pz + thickness, feather % 2 == 0 ? Material.QUARTZ_BRICKS : BLUE);
                // Short raised barbs articulate the feather instead of a featureless tube.
                if (step % 9 == 0 && t > .12 && t < .86 && Math.abs(offset) > 1) {
                    b.block(px, py, pz + thickness + 1, EDGE);
                }
            }
        }
    }

    private static void flyingRibs(BossStructure b, int side) {
        int f = b.floor;
        for (int rib = 0; rib < 3; rib++) {
            int x = 55 + rib * 9, z = -48 + rib * 17;
            b.ellipse(side * x, f + 1, z, 4, 5, EDGE);
            b.ellipse(side * x, f + 2, z, 3, 4, GOLD);
            curve(b, side, x, f + 3, z, 80 - rib * 4, f + 71, z - 14,
                    43 + rib * 4, f + 84 - rib * 9, z - 9, 1.9, IVORY);
            curve(b, side, x - 2, f + 4, z, 78 - rib * 4, f + 70, z - 14,
                    42 + rib * 4, f + 83 - rib * 9, z - 9, .6, GOLD);
        }
    }

    private static void bladeMemorial(BossStructure b, int side) {
        int f = b.floor, x = side * 64, z = 28;
        for (int level = f - 3; level <= f; level++) {
            b.ellipse(x, level, z, 7, 8, level == f ? IVORY : Material.CALCITE);
        }
        b.ellipse(x, f + 1, z, 7, 8, EDGE);
        b.ellipse(x, f + 2, z, 6, 7, GOLD);
        b.ellipse(x, f + 3, z, 5, 6, IVORY);
        // Outward leaning spears, distinct from the much larger rear sword.
        b.line(x, f + 4, z, side * 78, f + 50, z + 9, 1.1, EDGE);
        b.line(x + side, f + 5, z, side * 79, f + 50, z + 9, .6, GOLD);
        for (int dy = 0; dy <= 15; dy++) {
            int px = side * (78 + dy / 4), pz = z + 9 + dy / 8;
            int width = dy < 5 ? 2 : dy < 11 ? 1 : 0;
            b.box(px - width, f + 50 + dy, pz - 1, px + width, f + 50 + dy, pz + 1,
                    dy % 4 == 0 ? GOLD : Material.IRON_BLOCK);
        }
        b.line(side * 73, f + 47, z + 7, side * 83, f + 45, z + 7, 1, GOLD);
        b.block(side * 82, f + 66, z + 10, LIGHT);
    }

    private static void treasury(BossStructure b) {
        int f = b.floor, cz = b.kind.chestZ;
        platform(b, 0, cz - 3, 24, 22, f, 11);
        b.ellipseRing(0, f, cz - 3, 23, 21, .8, GOLD);
        // A round, glass-lit reliquary has an actual enclosed treasury under its scalloped roof.
        for (int y = f + 1; y <= f + 27; y++) {
            b.ellipseRing(0, y, cz - 2, 20, 17, 2.2, IVORY);
        }
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            int x = round(19 * Math.cos(a)), z = cz - 2 + round(16 * Math.sin(a));
            b.box(x - 1, f + 1, z - 1, x + 1, f + 28, z + 1, EDGE);
            b.block(x, f + 29, z, GOLD);
            if (Math.abs(x) > 5) {
                b.box(x, f + 9, z, x, f + 23, z, BLUE);
                b.block(x, f + 8, z, LIGHT);
            }
        }
        // A broad portal and side entry open into the level spine at the rear of the arena.
        b.box(-5, f + 1, cz + 12, 5, f + 13, cz + 17, Material.AIR);
        for (int dx = -6; dx <= 6; dx++) {
            int y = f + 14 + round(5 * Math.sqrt(Math.max(0, 1 - dx * dx / 36.0)));
            b.box(dx, y, cz + 14, dx, y + 1, cz + 16, GOLD);
        }
        for (int step = 0; step <= 18; step++) {
            double scale = Math.sqrt(Math.max(0, 1 - Math.pow(step / 19.0, 2)));
            double rx = 22 * scale, rz = 19 * scale;
            b.ellipseRing(0, f + 28 + step, cz - 2, Math.max(2, rx), Math.max(2, rz), 2,
                    step % 5 == 0 ? GOLD : Material.QUARTZ_BRICKS);
        }
        b.ellipse(0, f + 46, cz - 2, 7, 6, BLUE);
        b.ellipseRing(0, f + 47, cz - 2, 7, 6, 1, GOLD);
        b.ellipse(0, f, cz - 2, 17, 14, Material.QUARTZ_BRICKS);
        b.ellipseRing(0, f, cz - 2, 13, 10, .6, GOLD);
        // The library/chests are furnished by BossStructure after this shell is built.
        for (int side : new int[] {-1, 1}) {
            b.box(side * 14 - 1, f + 2, cz - 2, side * 14 + 1, f + 3, cz + 2, EDGE);
            b.block(side * 14, f + 4, cz, LIGHT);
        }
        hangingCrystal(b, 0, f - 14, cz - 3, 18, 4);
    }

    private static void triumphalBlade(BossStructure b) {
        int f = b.floor, z = b.kind.chestZ - 10;
        // An immense suspended sword crowns the reliquary; wings frame its pointed blade.
        b.box(-2, f + 47, z - 2, 2, f + 59, z + 2, Material.DARK_PRISMARINE);
        for (int y = f + 48; y <= f + 58; y += 3) b.box(-3, y, z - 2, 3, y, z + 2, GOLD);
        b.ellipse(0, f + 45, z, 5, 5, GOLD);
        b.block(0, f + 45, z, LIGHT);
        for (int x = -18; x <= 18; x++) {
            int y = f + 61 - round(5 * Math.pow(Math.abs(x) / 18.0, 1.4));
            b.box(x, y, z - 2, x, y + 2, z + 2, GOLD);
        }
        for (int h = 0; h <= 61; h++) {
            int width = h < 42 ? 4 : Math.max(0, (61 - h) / 5);
            b.box(-width, f + 64 + h, z - 1, width, f + 64 + h, z + 1, Material.IRON_BLOCK);
            b.box(0, f + 64 + h, z - 2, 0, f + 64 + h, z - 2, BLUE);
            if (width > 0) {
                b.block(-width, f + 64 + h, z - 2, IVORY);
                b.block(width, f + 64 + h, z - 2, IVORY);
            }
            if (h % 10 == 0) b.block(0, f + 64 + h, z - 2, LIGHT);
        }
        b.block(0, f + 126, z, LIGHT);
    }

    private static void arrivalGate(BossStructure b) {
        int f = b.floor;
        // A slim, swept coronet over the arrival, without walls or corner towers.
        for (int side : new int[] {-1, 1}) {
            b.ellipse(side * 15, f + 1, 81, 3, 4, EDGE);
            curve(b, side, 15, f + 2, 81, 26, f + 32, 79,
                    1, f + 46, 82, 1.9, IVORY);
            curve(b, side, 16, f + 3, 80, 26, f + 32, 78,
                    1, f + 47, 81, .6, GOLD);
            for (int feather = 0; feather < 3; feather++) {
                curve(b, side, 15 + feather, f + 10 + feather * 5, 81,
                        25 + feather * 3, f + 12 + feather * 6, 79,
                        25 + feather * 2, f + 27 + feather * 7, 78,
                        .9, EDGE);
            }
        }
        b.box(-1, f + 46, 81, 1, f + 48, 83, GOLD);
        b.block(0, f + 49, 82, LIGHT);
    }

    private static void platform(BossStructure b, int cx, int cz, int rx, int rz, int y, int depth) {
        for (int h = 0; h <= depth; h++) {
            double scale = 1 - .38 * h / depth;
            b.ellipse(cx, y - h, cz, rx * scale, rz * scale,
                    h == 0 ? IVORY : h == 2 ? GOLD : Material.CALCITE);
        }
        b.ellipseRing(cx, y, cz, rx - .5, rz - .5, .8, EDGE);
    }

    private static void hangingCrystal(BossStructure b, int x, int topY, int z, int length, int width) {
        for (int drop = 0; drop <= length; drop++) {
            int r = Math.max(0, round(width * (1 - drop / (double) length)));
            b.box(x - r, topY - drop, z - r, x + r, topY - drop, z + r,
                    drop % 6 == 0 ? LIGHT : BLUE);
        }
    }

    private static void curve(BossStructure b, int side, double ax, double ay, double az,
                              double cx, double cy, double cz, double bx, double by, double bz,
                              double width, Material material) {
        double px = ax, py = ay, pz = az;
        for (int step = 1; step <= 64; step++) {
            double t = step / 64.0;
            double x = bezier(ax, cx, bx, t), y = bezier(ay, cy, by, t), z = bezier(az, cz, bz, t);
            b.line(side * px, py, pz, side * x, y, z, width, material);
            px = x;
            py = y;
            pz = z;
        }
    }

    private static double bezier(double start, double control, double end, double t) {
        return (1 - t) * (1 - t) * start + 2 * (1 - t) * t * control + t * t * end;
    }

    private static int round(double value) {
        return (int) Math.round(value);
    }
}
