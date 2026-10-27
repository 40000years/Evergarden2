package com.example.voidscape.world;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;

/** A cliff-rooted, deliberately asymmetric gothic fortress around an open ritual court. */
final class ArkhaiaCastleDesign {
    private static final Material WALL = Material.DEEPSLATE_BRICKS;
    private static final Material FRAME = Material.POLISHED_BLACKSTONE_BRICKS;
    private static final Material ROOF = Material.POLISHED_BLACKSTONE;
    private static final Material RED = Material.RED_NETHER_BRICKS;
    private static final Material GLASS = Material.RED_STAINED_GLASS;

    private ArkhaiaCastleDesign() {}

    static void build(BossStructure b) {
        cliff(b);
        ritualCourt(b);
        cathedral(b);
        cloister(b, -1);
        cloister(b, 1);
        ramparts(b);
        gate(b);

        // Unequal towers and different roof lengths create a recognizable skyline.
        turret(b, -44, -73, 9, 79, 38);
        turret(b, 46, -80, 7, 62, 32);
        turret(b, -86, -42, 6, 49, 27);
        turret(b, 84, -29, 7, 57, 31);
        turret(b, -82, 45, 8, 40, 29);
        turret(b, 83, 55, 7, 48, 26);
        turret(b, -27, 80, 7, 43, 29);
        turret(b, 27, 80, 7, 55, 32);

        for (int side : new int[]{-1, 1}) {
            garden(b, side * 35, 68);
            garden(b, side * 37, -84);
            reliquary(b, side * 20, 58);
        }
        wards(b);
        // Keep the entire combat volume unobstructed, including all twenty-eight air layers.
        for (int x = -42; x <= 42; x++) {
            int rz = (int) Math.floor(Math.sqrt(42 * 42 - x * x));
            b.box(x, b.floor + 1, -rz, x, b.floor + 28, rz, Material.AIR);
        }
    }

    private static void cliff(BossStructure b) {
        int f = b.floor;
        for (int x = -101; x <= 101; x++) for (int z = -101; z <= 101; z++) {
            double oval = Math.min(ellipse(x, z, 0, 0, 77, 88),
                    Math.min(ellipse(x, z, 0, -48, 94, 49),
                    Math.min(ellipse(x, z, x < 0 ? -69 : 69, -1, 28, 60),
                            ellipse(x, z, 0, 74, 54, 25))));
            double rough = .029 * Math.sin(x * .61 + z * .29)
                    + .022 * Math.cos(x * .27 - z * .49);
            if (oval > 1 + rough) continue;
            double edge = Math.min(1, Math.sqrt(Math.max(0, oval)));
            int bottom = Math.max(40, f - 18 - (int) ((1 - edge) * 39)
                    - (int) (7 + 6 * Math.sin(x * .13 + z * .09)));
            boolean paved = Math.hypot(x, z) < 49 ||
                    Math.abs(x) <= 80 && z >= -94 && z <= 54 ||
                    Math.abs(x) <= 45 && z >= 45 && z <= 97;
            int top = paved ? f : f - 2 - (int) (3 * edge)
                    - Math.floorMod(x * 7 + z * 11, 3);
            b.box(x, bottom, z, x, top - 7, z,
                    Math.floorMod(x + z, 17) < 3 ? Material.BASALT : Material.DEEPSLATE);
            b.box(x, top - 6, z, x, top - 3, z, Material.TUFF);
            b.box(x, top - 2, z, x, top, z, paved ? WALL : Material.BLACKSTONE);
            if (edge > .84 && Math.floorMod(x * 5 + z * 3, 19) == 0)
                b.box(x, bottom + 2, z, x, Math.min(top - 9, bottom + 8), z, Material.CRYING_OBSIDIAN);
        }
        // Long geological teeth and hanging amethyst shards break up the underside.
        int[][] teeth = {{-79, -58}, {78, -65}, {-89, 19}, {85, 39}, {-44, 85}, {50, 82}};
        for (int i = 0; i < teeth.length; i++) {
            int x = teeth[i][0], z = teeth[i][1], bottom = 43 + i * 3;
            b.line(x, 87, z, x + (x < 0 ? -4 : 4), bottom, z + 4, 2.4, Material.BASALT);
            b.line(x + 3, 83, z - 3, x + 5, bottom + 8, z - 5, 1.25, Material.AMETHYST_BLOCK);
            b.block(x + 5, bottom + 7, z - 5, Material.PURPLE_STAINED_GLASS);
        }
    }

    private static double ellipse(int x, int z, int cx, int cz, int rx, int rz) {
        return Math.pow((x - cx) / (double) rx, 2) + Math.pow((z - cz) / (double) rz, 2);
    }

    private static void ritualCourt(BossStructure b) {
        int f = b.floor;
        b.disk(0, f, 0, 48, Material.POLISHED_DEEPSLATE);
        for (int x = -48; x <= 48; x++) for (int z = -48; z <= 48; z++) {
            double r = Math.hypot(x, z);
            if (r > 48) continue;
            double angle = Math.atan2(z, x);
            double petal = 25 + 5.5 * Math.cos(angle * 8);
            Material material = Math.floorMod(x + z, 6) == 0
                    ? Material.DEEPSLATE_TILES : Material.POLISHED_DEEPSLATE;
            if (Math.abs(r - 39) < .7 || Math.abs(r - 41.5) < .65 ||
                    Math.abs(r - 12) < .65 || Math.abs(r - petal) < .62) material = RED;
            if (r > 12 && r < 39 && Math.abs(Math.sin(angle * 8)) < .035) material = FRAME;
            if (r < 5) material = Material.RED_GLAZED_TERRACOTTA;
            if (r > 43 && Math.floorMod(x + z, 5) == 0) material = Material.CHISELED_POLISHED_BLACKSTONE;
            b.block(x, f, z, material);
        }
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * i / 4;
            int cx = (int) Math.round(Math.cos(angle) * 34);
            int cz = (int) Math.round(Math.sin(angle) * 34);
            b.disk(cx, f, cz, 3, Material.CHISELED_POLISHED_BLACKSTONE);
            b.block(cx, f, cz, Material.RED_GLAZED_TERRACOTTA);
        }
        // The long procession changes from charcoal paving to a crimson woven carpet.
        for (int z = 44; z <= 97; z++) {
            b.box(-12, f, z, 12, f, z, FRAME);
            b.box(-8, f, z, 8, f, z, z % 5 == 0 ? Material.CHISELED_POLISHED_BLACKSTONE : WALL);
            b.box(-2, f, z, 2, f, z, RED);
            for (int x : new int[]{-10, 10}) b.block(x, f, z, Material.CRIMSON_PLANKS);
        }
        for (int z = 49; z <= 95; z += 10) for (int x : new int[]{-12, 12}) {
            b.box(x - 1, f + 1, z - 1, x + 1, f + 2, z + 1, FRAME);
            b.block(x, f + 3, z, Material.SOUL_LANTERN);
        }
    }

    private static void cathedral(BossStructure b) {
        int f = b.floor, front = -47, rear = -94, half = 30, top = f + 61;
        b.box(-half - 2, f - 3, rear - 2, half + 2, f, front + 2, FRAME);
        b.box(-half, f + 1, rear, -half + 2, top, front, WALL);
        b.box(half - 2, f + 1, rear, half, top, front, WALL);
        b.box(-half, f + 1, rear, half, top, rear + 2, WALL);
        b.box(-half, f + 1, front - 2, half, top, front, WALL);
        b.box(-27, f, -91, 27, f, -50, Material.DEEPSLATE_TILES);

        // Tall pointed glass lancets are deeply inset between exterior piers.
        for (int side : new int[]{-1, 1}) for (int z : new int[]{-57, -72, -87}) {
            lancetX(b, side * half, z, f + 17, 4, 24);
            b.box(side < 0 ? -34 : 32, f + 2, z - 2,
                    side < 0 ? -32 : 34, f + 48, z + 2, FRAME);
            pinnacle(b, side * 33, z, f + 49, 2, 12);
            b.box(side < 0 ? -49 : 47, f + 1, z - 2,
                    side < 0 ? -47 : 49, f + 30, z + 2, FRAME);
            pinnacle(b, side * 48, z, f + 31, 2, 13);
            flyingRib(b, side, 33, 48, z, f + 48, f + 30, 9);
            flyingRib(b, side, 33, 48, z, f + 62, f + 42, 10);
        }
        // A steep long roof, dormer details and a tall front gable replace a box-shaped keep.
        for (int dx = -33; dx <= 33; dx++) {
            int y = top + (33 - Math.abs(dx));
            b.box(dx, y, rear - 2, dx, y + 1, front + 2,
                    Math.abs(dx) % 8 == 0 ? RED : ROOF);
            if (Math.abs(dx) <= 30)
                b.box(dx, top + 1, front - 1, dx, y, front, WALL);
        }
        b.box(-1, top + 35, rear - 2, 1, top + 36, front + 2, RED);
        for (int z : new int[]{-84, -65, -47}) pinnacle(b, 0, z, top + 37, 2, 13);
        for (int side : new int[]{-1, 1}) for (int z : new int[]{-81, -62}) {
            int cx = side * 16, cy = top + 17;
            b.box(cx - 4, cy - 3, z - 3, cx + 4, cy + 8, z - 3, WALL);
            rose(b, cx, cy + 2, z - 4, 3);
            for (int dx = -5; dx <= 5; dx++)
                b.box(cx + dx, cy + 8 + 5 - Math.abs(dx), z - 4,
                        cx + dx, cy + 9 + 5 - Math.abs(dx), z + 3, ROOF);
        }

        // Three nested portal archivolts, a thirteen-petal rose, and fine vertical tracery.
        pointedPortal(b, 0, front, 8, f + 1, 15, 9, 2);
        rose(b, 0, f + 41, front + 1, 14);
        roseRing(b, 0, f + 41, front + 2, 17, .8, RED);
        for (int x : new int[]{-24, -19, 19, 24}) {
            b.box(x - 1, f + 3, front + 1, x + 1, f + 52, front + 2, FRAME);
            pinnacle(b, x, front + 1, f + 53, 1, 9);
        }
        for (int x : new int[]{-24, 24}) lancetZ(b, x, front + 3, f + 15, 2, 22);
        b.box(-27, f + 57, front + 1, 27, f + 58, front + 2, FRAME);
        for (int x = -26; x <= 26; x += 4) b.block(x, f + 59, front + 2, Material.CHISELED_POLISHED_BLACKSTONE);
        // A narrow three-lancet crown and an inset triangular border enrich the high gable.
        for (int cx : new int[]{-12, 0, 12}) {
            int height = cx == 0 ? 25 : 13;
            for (int dx = -2; dx <= 2; dx++) {
                int y = f + 63 + height - Math.abs(dx) * 2;
                b.box(cx + dx, f + 63, front - 2, cx + dx, y, front, GLASS);
            }
            lancetZ(b, cx, front + 1, f + 63, 2, height);
        }
        for (int dx = -30; dx <= 30; dx++) {
            int y = top + 30 - Math.abs(dx);
            b.block(dx, y, front + 1, Math.floorMod(dx, 6) == 0 ? RED : FRAME);
        }

        // The treasury stays enclosed at ground level and opens directly to the court.
        b.box(-11, f + 1, -77, -10, f + 15, -60, FRAME);
        b.box(10, f + 1, -77, 11, f + 15, -60, FRAME);
        b.box(-11, f + 1, -77, 11, f + 15, -76, FRAME);
        b.box(-11, f + 1, -61, 11, f + 15, -60, FRAME);
        b.box(-11, f + 16, -77, 11, f + 16, -60, Material.CRIMSON_PLANKS);
        pointedPortal(b, 0, -60, 4, f + 1, 7, 4, 1);
        for (int x : new int[]{-8, 8}) {
            b.box(x, f + 1, -73, x, f + 8, -73, Material.IRON_CHAIN);
            b.block(x, f + 1, -73, Material.SOUL_LANTERN);
        }
        b.box(-8, f + 17, -73, 8, f + 17, -65, Material.RED_STAINED_GLASS);
        // Nave lamps are supported by thin hanging chains, leaving its aisle usable.
        for (int z : new int[]{-54, -85}) for (int x : new int[]{-16, 16}) {
            b.box(x, f + 23, z, x, top - 2, z, Material.IRON_CHAIN);
            b.box(x - 2, f + 22, z - 2, x + 2, f + 22, z + 2, FRAME);
            for (int dx : new int[]{-2, 2}) for (int dz : new int[]{-2, 2})
                b.block(x + dx, f + 21, z + dz, Material.SOUL_LANTERN);
        }
    }

    private static void cloister(BossStructure b, int side) {
        int f = b.floor, left = side < 0 ? -79 : 57, right = side < 0 ? -57 : 79;
        b.box(left, f - 2, -44, right, f, 43, FRAME);
        b.box(left, f + 1, -44, left + 1, f + 24, 43, WALL);
        b.box(right - 1, f + 1, -44, right, f + 24, 43, WALL);
        b.box(left, f + 1, -44, right, f + 24, -43, WALL);
        b.box(left, f + 1, 42, right, f + 24, 43, WALL);
        b.box(left + 2, f + 13, -42, right - 2, f + 13, 41, Material.CRIMSON_PLANKS);
        for (int dx = -13; dx <= 13; dx++) {
            int y = f + 24 + 13 - Math.abs(dx);
            b.box(side * 68 + dx, y, -46, side * 68 + dx, y + 1, 45,
                    Math.abs(dx) == 13 || dx % 6 == 0 ? RED : ROOF);
        }
        for (int z : new int[]{-34, -14, 6, 26}) {
            lancetX(b, side * 79, z, f + 6, 3, 11);
            // Upper gallery has an open pointed arcade and a delicate exterior balustrade.
            b.box(side < 0 ? -59 : 48, f + 12, z - 10,
                    side < 0 ? -48 : 59, f + 13, z + 10, FRAME);
            pointedArcadeX(b, side * 49, z, 8, f + 1, 8, 5);
            pointedArcadeX(b, side * 49, z, 8, f + 14, 10, 6);
            b.box(side * 49, f + 15, z - 7, side * 49, f + 15, z + 7, Material.POLISHED_BLACKSTONE_WALL);
            b.box(side * 49, f + 16, z - 7, side * 49, f + 16, z + 7, FRAME);
            for (int dz = -7; dz <= 7; dz += 3)
                b.block(side * 49, f + 14, z + dz, Material.POLISHED_BLACKSTONE_WALL);
            b.box(side < 0 ? -52 : 48, f + 1, z - 11,
                    side < 0 ? -48 : 52, f + 25, z - 9, FRAME);
            pinnacle(b, side * 50, z - 10, f + 26, 2, 10);
            flyingRib(b, side, 50, 66, z - 10, f + 24, f + 37, 7);
            flyingRib(b, side, 50, 66, z - 10, f + 34, f + 45, 4);
            // Both flying ribs land on a real roof pier, capped by its own slender finial.
            b.box(side * 66 - 1, f + 33, z - 11,
                    side * 66 + 1, f + 45, z - 9, FRAME);
            b.box(side * 66 - 2, f + 46, z - 12,
                    side * 66 + 2, f + 46, z - 8, RED);
            pinnacle(b, side * 66, z - 10, f + 47, 1, 9);
            // The open gallery joins the inhabited wing on both levels.
            b.box(side < 0 ? -59 : 56, f + 1, z - 2,
                    side < 0 ? -56 : 59, f + 8, z + 2, Material.AIR);
            b.box(side < 0 ? -59 : 56, f + 14, z - 2,
                    side < 0 ? -56 : 59, f + 23, z + 2, Material.AIR);
        }
        for (int z = -43; z <= 43; z++) {
            b.box(side < 0 ? -60 : 48, f + 32, z,
                    side < 0 ? -48 : 60, f + 33, z, ROOF);
            b.block(side * 49, f + 34, z, RED);
        }
        // Broad visible stairs at both ends make the balconies more than scenery.
        for (int zEnd : new int[]{-44, 43}) for (int step = 0; step < 13; step++) {
            int z = zEnd < 0 ? zEnd - 13 + step : zEnd + 13 - step;
            b.box(side < 0 ? -61 : 51, f - 1, z,
                    side < 0 ? -51 : 61, f + step, z, FRAME);
            b.box(side < 0 ? -61 : 61, f + step + 1, z,
                    side < 0 ? -61 : 61, f + step + 2, z, Material.POLISHED_BLACKSTONE_WALL);
        }
        // Open the end doors to the upper stairs and the lower trap/bypass corridors.
        for (int z : new int[]{-44, 43}) {
            b.box(side * 68 - 3, f + 1, z - 1, side * 68 + 3, f + 8, z + 1, Material.AIR);
            b.box(side * 68 - 3, f + 14, z - 1, side * 68 + 3, f + 21, z + 1, Material.AIR);
        }
    }

    private static void ramparts(BossStructure b) {
        int f = b.floor;
        for (int side : new int[]{-1, 1}) {
            for (int z = -66; z <= 48; z++) {
                int height = 12 + (int) Math.round(2 * Math.sin(z * .14));
                b.box(side < 0 ? -92 : 89, f - 7, z,
                        side < 0 ? -89 : 92, f + height, z, WALL);
                b.box(side < 0 ? -92 : 89, f + height + 1, z,
                        side < 0 ? -89 : 92, f + height + 1, z, FRAME);
                if (Math.floorMod(z, 8) < 3)
                    b.box(side < 0 ? -93 : 89, f + height + 2, z,
                            side < 0 ? -89 : 93, f + height + 4, z, FRAME);
                if (Math.floorMod(z, 14) == 0) {
                    b.box(side * 90, f + 5, z, side * 90, f + 9, z, Material.AIR);
                    b.block(side * 90, f + 4, z, GLASS);
                }
            }
            // The fronts sweep backward instead of forming another square enclosure.
            for (int x = 36; x <= 79; x++) {
                int z = 77 - (x - 36) / 2;
                b.box(side * x, f - 4, z, side * x, f + 12, z + 2, WALL);
                b.box(side * x, f + 13, z - 1, side * x, f + 14, z + 3, FRAME);
                if (x % 7 < 3) b.box(side * x, f + 15, z - 1, side * x, f + 17, z + 3, FRAME);
            }
            for (int z : new int[]{-61, -25, 11, 43}) {
                b.box(side < 0 ? -96 : 91, f - 4, z - 2,
                        side < 0 ? -91 : 96, f + 17, z + 2, FRAME);
                pinnacle(b, side * 94, z, f + 18, 2, 9);
            }
        }
    }

    private static void gate(BossStructure b) {
        int f = b.floor;
        pointedPortal(b, 0, 80, 18, f + 1, 28, 18, 3);
        pointedPortal(b, 0, 76, 15, f + 1, 25, 16, 1);
        for (int side : new int[]{-1, 1}) {
            b.box(side < 0 ? -21 : 17, f + 1, 76,
                    side < 0 ? -17 : 21, f + 35, 84, FRAME);
            pinnacle(b, side * 19, 80, f + 36, 2, 14);
            for (int z : new int[]{75, 85})
                b.box(side * 19, f + 9, z, side * 19, f + 22, z, RED);
        }
        // A hanging openwork seal crowns the gate without blocking the arrival.
        roseRing(b, 0, f + 43, 80, 6, 1, RED);
        b.line(-4, f + 39, 80, 4, f + 47, 80, .7, FRAME);
        b.line(4, f + 39, 80, -4, f + 47, 80, .7, FRAME);
        b.block(0, f + 43, 80, Material.SHROOMLIGHT);
    }

    private static void turret(BossStructure b, int cx, int cz, int r, int height, int roofHeight) {
        int f = b.floor, top = f + height;
        for (int dx = -r - 2; dx <= r + 2; dx++) for (int dz = -r - 2; dz <= r + 2; dz++) {
            if (!octagon(dx, dz, r + 2)) continue;
            b.box(cx + dx, f - 4, cz + dz, cx + dx, f, cz + dz, FRAME);
            if (!octagon(dx, dz, r - 1))
                b.box(cx + dx, f + 1, cz + dz, cx + dx, f + 3, cz + dz, FRAME);
            if (octagon(dx, dz, r) && !octagon(dx, dz, r - 2))
                b.box(cx + dx, f + 4, cz + dz, cx + dx, top, cz + dz, WALL);
        }
        for (int band = f + 12; band <= top; band += 14) octagonBand(b, cx, cz, r + 1, band, FRAME);
        octagonBand(b, cx, cz, r + 2, top + 1, RED);
        octagonBand(b, cx, cz, r + 2, top + 2, FRAME);
        for (int side : new int[]{-1, 1}) {
            for (int y = f + 15; y < top - 12; y += 19) {
                lancetZ(b, cx, cz + side * r, y, 1, 9);
                lancetX(b, cx + side * r, cz, y, 1, 9);
            }
            for (int dx : new int[]{-1, 1}) {
                int px = cx + dx * (r - 2), pz = cz + side * (r - 2);
                b.box(px, f + 4, pz, px, top - 3, pz, FRAME);
                pinnacle(b, px, pz, top - 2, 1, 10);
            }
        }
        for (int y = 0; y < roofHeight; y++) {
            int rr = Math.max(1, (int) Math.ceil((r + 2) * (1 - y / (double) roofHeight)));
            octagonBand(b, cx, cz, rr, top + 3 + y, y % 9 == 0 ? RED : ROOF);
        }
        b.box(cx, top + roofHeight + 3, cz, cx, top + roofHeight + 9, cz, FRAME);
        b.block(cx, top + roofHeight + 10, cz, Material.SOUL_LANTERN);
        b.box(cx - 2, f + 1, cz + r - 2, cx + 2, f + 8, cz + r + 2, Material.AIR);
    }

    private static boolean octagon(int dx, int dz, int r) {
        return Math.abs(dx) <= r && Math.abs(dz) <= r && Math.abs(dx) + Math.abs(dz) <= r * 1.5;
    }

    private static void octagonBand(BossStructure b, int cx, int cz, int r, int y, Material m) {
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++)
            if (octagon(dx, dz, r) && !octagon(dx, dz, r - 1)) b.block(cx + dx, y, cz + dz, m);
    }

    private static void pinnacle(BossStructure b, int x, int z, int y, int r, int height) {
        b.box(x - r, y, z - r, x + r, y + 1, z + r, FRAME);
        for (int h = 2; h < height; h++) {
            int rr = Math.max(0, r - h * r / Math.max(1, height - 2));
            b.box(x - rr, y + h, z - rr, x + rr, y + h, z + rr, h % 5 == 0 ? RED : ROOF);
        }
        b.block(x, y + height, z, Material.SOUL_LANTERN);
    }

    private static void flyingRib(BossStructure b, int side, int inner, int outer,
                                  int z, int innerY, int outerY, int lift) {
        int span = outer - inner;
        for (int t = 0; t <= span; t++) {
            double ratio = t / (double) span;
            int y = (int) Math.round(innerY + (outerY - innerY) * ratio + Math.sin(ratio * Math.PI) * lift);
            int x = side * (inner + t);
            b.box(x, y, z - 1, x, y + 2, z + 1, FRAME);
            b.block(x, y + 3, z, RED);
        }
    }

    private static void pointedPortal(BossStructure b, int cx, int z, int half,
                                      int baseY, int spring, int rise, int depth) {
        for (int side : new int[]{-1, 1})
            b.box(cx + side * half - 2, baseY, z - depth,
                    cx + side * half + 2, baseY + spring, z + depth, FRAME);
        // Carve the opening first, then frame its pointed upper profile with layered voussoirs.
        for (int dx = -half + 2; dx <= half - 2; dx++) {
            int y = baseY + spring + (int) Math.round(rise * (1 - Math.pow(Math.abs(dx) / (double) half, .8)));
            b.box(cx + dx, baseY, z - depth - 1, cx + dx, y - 1, z + depth + 1, Material.AIR);
        }
        for (int layer = 0; layer < 3; layer++) for (int dx = -half - layer; dx <= half + layer; dx++) {
            int y = baseY + spring + (int) Math.round(rise * (1 - Math.pow(Math.abs(dx) / (double) (half + layer), .8))) + layer * 2;
            b.box(cx + dx, y, z - depth, cx + dx, y + 1, z + depth,
                    layer == 1 ? RED : FRAME);
        }
    }

    private static void pointedArcadeX(BossStructure b, int x, int cz, int half,
                                       int baseY, int spring, int rise) {
        for (int side : new int[]{-1, 1})
            b.box(x - 1, baseY, cz + side * half - 1, x + 1, baseY + spring, cz + side * half + 1, FRAME);
        for (int dz = -half; dz <= half; dz++) {
            int y = baseY + spring + (int) Math.round(rise * (1 - Math.pow(Math.abs(dz) / (double) half, .8)));
            b.box(x - 1, y, cz + dz, x + 1, y + 1, cz + dz, FRAME);
            b.block(x, y + 2, cz + dz, RED);
        }
    }

    private static void lancetZ(BossStructure b, int cx, int z, int baseY, int half, int height) {
        for (int dx = -half - 1; dx <= half + 1; dx++) {
            int top = baseY + height - Math.abs(dx) * 2;
            boolean border = Math.abs(dx) > half;
            b.box(cx + dx, baseY, z, cx + dx, top, z, border ? FRAME : GLASS);
            b.block(cx + dx, top + 1, z, FRAME);
        }
        b.box(cx - half - 1, baseY - 1, z, cx + half + 1, baseY - 1, z, RED);
        b.box(cx, baseY, z, cx, baseY + height, z, FRAME);
        b.box(cx - half, baseY + height / 2, z, cx + half, baseY + height / 2, z, FRAME);
    }

    private static void lancetX(BossStructure b, int x, int cz, int baseY, int half, int height) {
        for (int dz = -half - 1; dz <= half + 1; dz++) {
            int top = baseY + height - Math.abs(dz) * 2;
            b.box(x, baseY, cz + dz, x, top, cz + dz, Math.abs(dz) > half ? FRAME : GLASS);
            b.block(x, top + 1, cz + dz, FRAME);
        }
        b.box(x, baseY - 1, cz - half - 1, x, baseY - 1, cz + half + 1, RED);
        b.box(x, baseY, cz, x, baseY + height, cz, FRAME);
        b.box(x, baseY + height / 2, cz - half, x, baseY + height / 2, cz + half, FRAME);
    }

    private static void rose(BossStructure b, int cx, int cy, int z, int radius) {
        for (int dx = -radius; dx <= radius; dx++) for (int dy = -radius; dy <= radius; dy++) {
            double r = Math.hypot(dx, dy);
            if (r > radius) continue;
            double angle = Math.atan2(dy, dx);
            double petals = radius * .57 + radius * .18 * Math.cos(angle * 12);
            boolean tracery = r > radius - 1.3 || Math.abs(r - petals) < .55 ||
                    r > radius * .38 && Math.abs(Math.sin(angle * 6)) < .09 || Math.abs(r - radius * .3) < .6;
            b.block(cx + dx, cy + dy, z, tracery ? FRAME :
                    Math.floorMod(dx + dy, 5) == 0 ? Material.PURPLE_STAINED_GLASS : GLASS);
        }
        b.block(cx, cy, z + 1, Material.SHROOMLIGHT);
        roseRing(b, cx, cy, z + 1, radius + 1, .7, RED);
    }

    private static void roseRing(BossStructure b, int cx, int cy, int z, int radius, double width, Material m) {
        for (int dx = -radius - 1; dx <= radius + 1; dx++) for (int dy = -radius - 1; dy <= radius + 1; dy++)
            if (Math.abs(Math.hypot(dx, dy) - radius) <= width) b.block(cx + dx, cy + dy, z, m);
    }

    private static void garden(BossStructure b, int x, int z) {
        int f = b.floor;
        b.disk(x, f + 1, z, 6, FRAME);
        b.disk(x, f + 2, z, 4, Material.CRIMSON_NYLIUM);
        b.block(x, f + 3, z, Material.ROSE_BUSH);
        b.box(x, f + 4, z, x, f + 4, z, Material.ROSE_BUSH, BlockFace.UP);
        for (int dx : new int[]{-3, 3}) for (int dz : new int[]{-2, 2}) {
            b.block(x + dx, f + 3, z + dz, Material.ROSE_BUSH);
            b.box(x + dx, f + 4, z + dz, x + dx, f + 4, z + dz, Material.ROSE_BUSH, BlockFace.UP);
        }
        for (int dx : new int[]{-6, 6}) {
            b.box(x + dx, f + 2, z, x + dx, f + 5, z, Material.POLISHED_BLACKSTONE_WALL);
            b.block(x + dx, f + 6, z, Material.SOUL_LANTERN);
        }
    }

    private static void reliquary(BossStructure b, int x, int z) {
        int f = b.floor;
        b.box(x - 3, f + 1, z - 3, x + 3, f + 2, z + 3, FRAME);
        b.box(x - 2, f + 3, z - 2, x + 2, f + 9, z + 2, Material.CHISELED_POLISHED_BLACKSTONE);
        for (int dx : new int[]{-3, 3}) for (int dz : new int[]{-3, 3})
            b.box(x + dx, f + 3, z + dz, x + dx, f + 13, z + dz, FRAME);
        b.box(x - 4, f + 14, z - 4, x + 4, f + 15, z + 4, RED);
        pinnacle(b, x, z, f + 16, 3, 11);
        for (int side : new int[]{-1, 1}) {
            b.box(x - 1, f + 5, z + side * 3, x + 1, f + 8, z + side * 3, GLASS);
            b.block(x, f + 10, z + side * 3, Material.SOUL_LANTERN);
        }
    }

    private static void wards(BossStructure b) {
        int f = b.floor;
        // Obvious paired magma cuts flank each plate; both outer corridor edges bypass them.
        for (int z : new int[]{-27, -3, 21}) {
            b.box(-74, f - 3, z - 2, -64, f - 1, z + 2, Material.AIR);
            b.box(-74, f - 4, z - 2, -64, f - 4, z + 2, Material.MAGMA_BLOCK);
            b.box(-74, f, z - 2, -72, f, z + 2, Material.AIR);
            b.box(-66, f, z - 2, -64, f, z + 2, Material.AIR);
            b.block(-71, f + 1, z, Material.COBWEB);
            b.block(-67, f + 1, z, Material.COBWEB);
            b.block(-69, f, z, Material.RED_GLAZED_TERRACOTTA);
            b.block(-69, f + 1, z, Material.POLISHED_BLACKSTONE_PRESSURE_PLATE);
            b.trap(-69, f + 1, z);
            for (int x : new int[]{-77, -60}) b.block(x, f + 1, z, Material.SOUL_LANTERN);
        }
    }
}
