package com.example.voidscape.world;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;

/** The supreme sanctuary: a celestial acropolis, rather than a rectangular temple. */
final class SeraphonSanctuaryDesign {
    private static final Material WHITE = Material.SMOOTH_QUARTZ;
    private static final Material CARVED = Material.CHISELED_QUARTZ_BLOCK;
    private static final Material BRICK = Material.QUARTZ_BRICKS;
    private static final Material GOLD = Material.GOLD_BLOCK;
    private static final Material BLUE = Material.LIGHT_BLUE_STAINED_GLASS;
    private static final Material LIGHT = Material.SEA_LANTERN;
    private static final double TAU = Math.PI * 2;

    private SeraphonSanctuaryDesign() { }

    static void build(BossStructure b) {
        terraces(b);
        mandala(b);
        processionalSteps(b);
        lowerArcades(b);
        celestialColonnade(b);
        rearApse(b);
        armillaryCrown(b);
        // A final architectural cut guarantees an unobstructed arena volume.
        for (int x = -58; x <= 58; x++) {
            int dz = (int) Math.floor(Math.sqrt(58 * 58 - x * x));
            b.box(x, b.floor + 1, -dz, x, b.floor + 28, dz, Material.AIR);
        }
    }

    private static void terraces(BossStructure b) {
        int f = b.floor;
        // The underside is a deep, curved inverted dome with radial stone ribs.
        // Distinct stepped ledges break its outline well before the upper courts.
        for (int y = 49; y <= f - 18; y++) {
            double t = (y - 49.0) / (f - 67.0);
            int radius = (int) Math.round(18 + 100 * Math.sqrt(t));
            b.disk(0, y, 0, radius, y < 72 ? Material.POLISHED_DIORITE : Material.CALCITE);
        }
        for (int i = 0; i < 12; i++) {
            double a = i * TAU / 12;
            double previousR = 18, previousY = 49;
            for (int y = 52; y <= f - 18; y += 3) {
                double t = (y - 49.0) / (f - 67.0);
                double r = 18 + 100 * Math.sqrt(t);
                b.line(Math.sin(a) * previousR, previousY, Math.cos(a) * previousR,
                        Math.sin(a) * r, y, Math.cos(a) * r, 1.4, BRICK);
                previousR = r;
                previousY = y;
            }
        }
        for (int y = f - 17; y <= f - 13; y++) b.disk(0, y, 0, 118, BRICK);
        b.ring(0, f - 12, 0, 116, 2, GOLD);
        for (int y = f - 12; y <= f - 9; y++) b.disk(0, y, 0, 114, WHITE);
        b.ring(0, f - 8, 0, 113, 2, CARVED);
        for (int y = f - 8; y <= f - 5; y++) b.disk(0, y, 0, 111, Material.CALCITE);
        b.disk(0, f - 4, 0, 110, BRICK);
        b.disk(0, f - 3, 0, 108, WHITE);
        b.disk(0, f - 2, 0, 106, WHITE);
        b.disk(0, f - 1, 0, 104, WHITE);
        b.disk(0, f, 0, 102, WHITE);
        for (int level = 0; level < 4; level++)
            b.ring(0, f - level, 0, 102 + level * 2, 1, CARVED);
        // Twelve luminous suspended pendants articulate the acropolis rim.
        for (int i = 0; i < 12; i++) {
            double a = (i + .5) * TAU / 12;
            int x = round(Math.sin(a) * 104), z = round(Math.cos(a) * 104);
            b.box(x, f - 29, z, x, f - 14, z, Material.QUARTZ_PILLAR, BlockFace.UP);
            crystal(b, x, f - 30, z, 3, 8);
        }
    }

    private static void mandala(BossStructure b) {
        // Compress each row into material runs: the mandala is deliberately tiled,
        // with twelve intersecting petals rather than a few plain circular stripes.
        for (int x = -102; x <= 102; x++) {
            int extent = (int) Math.floor(Math.sqrt(102 * 102 - x * x));
            int start = -extent;
            Material current = floorMaterial(x, start);
            for (int z = start + 1; z <= extent + 1; z++) {
                Material next = z > extent ? null : floorMaterial(x, z);
                if (next != current) {
                    b.box(x, b.floor, start, x, b.floor, z - 1, current);
                    start = z;
                    current = next;
                }
            }
        }
        for (int i = 0; i < 12; i++) {
            double a = i * TAU / 12;
            b.line(Math.sin(a) * 60, b.floor, Math.cos(a) * 60,
                    Math.sin(a) * 99, b.floor, Math.cos(a) * 99, .65, GOLD);
        }
        b.ring(0, b.floor, 0, 59.5, 1.1, GOLD);
        b.ring(0, b.floor, 0, 63, 1, CARVED);
        b.ring(0, b.floor, 0, 98.5, 1.2, GOLD);
    }

    private static Material floorMaterial(int x, int z) {
        double r = Math.hypot(x, z), a = Math.atan2(z, x);
        if (r < 4) return LIGHT;
        if (r < 8) return Math.abs(x) == Math.abs(z) ? GOLD : Material.BLUE_GLAZED_TERRACOTTA;
        if (Math.abs(r - 12) < .65 || Math.abs(r - 54) < .65) return GOLD;
        double petal = 28 + 11 * Math.cos(a * 12);
        if (r > 14 && r < 43 && Math.abs(r - petal) < .7) return GOLD;
        if (r > 14 && r < 43 && Math.abs(r - petal) < 2.1) return Material.LIGHT_BLUE_TERRACOTTA;
        if (r > 43 && r < 50 && Math.abs(Math.sin(a * 12)) < .17) return LIGHT;
        if (r < 58) return Material.CALCITE;
        if (r < 62) return Material.LIGHT_BLUE_TERRACOTTA;
        if (r > 95) return BRICK;
        int sector = (int) Math.floor((a + Math.PI) * 12 / TAU);
        if (r > 67 && r < 90 && sector % 2 == 0) return Material.POLISHED_DIORITE;
        return WHITE;
    }

    private static void processionalSteps(BossStructure b) {
        int f = b.floor;
        // A broad, scalloped forecourt is visibly connected to the circular court.
        for (int y = f - 6; y <= f; y++) {
            b.ellipse(0, y, 101, 30 + (f - y), 18 - (f - y) / 2, WHITE);
        }
        b.ellipse(0, f, 101, 30, 18, BRICK);
        b.ellipseRing(0, f, 101, 30, 18, 1.1, GOLD);
        b.box(-9, f, 72, 9, f, 118, CARVED);
        b.box(-6, f, 72, 6, f, 118, WHITE);
        for (int z = 74; z <= 112; z += 8) {
            b.box(-5, f, z, 5, f, z + 1, Material.LIGHT_BLUE_TERRACOTTA);
            for (int side : new int[]{-1, 1}) {
                int x = side * 14;
                b.box(x - 2, f + 1, z - 2, x + 2, f + 2, z + 2, CARVED);
                b.block(x, f + 3, z, GOLD);
                b.block(x, f + 4, z, LIGHT);
            }
        }
        // Low fan-shaped parapets finish the arrival without covering the view.
        for (int side : new int[]{-1, 1}) {
            for (int z = 98; z <= 114; z++) {
                int x = side * (27 - (z - 98) / 3);
                b.box(x, f + 1, z, x, f + 3, z, WHITE);
                b.block(x, f + 4, z, GOLD);
            }
            spire(b, side * 26, 99, f, 45, 4);
        }
    }

    private static void lowerArcades(BossStructure b) {
        int f = b.floor;
        // Two curved lower galleries cascade down the sides of the high court.
        for (int side : new int[]{-1, 1}) {
            double start = side < 0 ? Math.PI * 1.19 : Math.PI * .19;
            double end = start + Math.PI * .62;
            for (int j = 0; j <= 6; j++) {
                double a = start + (end - start) * j / 6;
                int x = round(Math.sin(a) * 108), z = round(Math.cos(a) * 108);
                pier(b, x, z, f - 4, f + 25, 2);
                if (j < 6) arc(b, 108, a, start + (end - start) * (j + 1) / 6,
                        f + 22, 9, 1.4, WHITE);
            }
            arc(b, 108, start, end, f + 34, 0, 2.2, BRICK);
            arc(b, 108, start, end, f + 37, 0, 1.1, GOLD);
            // An open upper walk, with small gold balusters at every bay.
            for (double a = start; a <= end; a += .035) {
                int x = round(Math.sin(a) * 108), z = round(Math.cos(a) * 108);
                b.box(x - 1, f + 35, z - 1, x + 1, f + 35, z + 1, WHITE);
                if (Math.floor((a - start) / .035) % 4 == 0)
                    b.box(x, f + 36, z, x, f + 38, z, CARVED);
            }
        }
    }

    private static void celestialColonnade(BossStructure b) {
        int f = b.floor;
        // A twelve-bay circular nave carries a double cornice and open arches.
        for (int i = 0; i < 12; i++) {
            double a = (i + .5) * TAU / 12, next = (i + 1.5) * TAU / 12;
            int x = round(Math.sin(a) * 85), z = round(Math.cos(a) * 85);
            pier(b, x, z, f, f + 65, 3);
            arc(b, 85, a, next, f + 63, 15, 2.1, WHITE);
            arc(b, 85, a, next, f + 67, 15, .9, GOLD);
            // Each pier fans inward into an elevated flying buttress.
            double px = x, py = f + 68, pz = z;
            for (int t = 1; t <= 18; t++) {
                double k = t / 18.0, r = 85 - 30 * k;
                double y = f + 68 + 45 * Math.sin(k * Math.PI / 2);
                double nx = Math.sin(a) * r, nz = Math.cos(a) * r;
                b.line(px, py, pz, nx, y, nz, 1.6, BRICK);
                b.line(px, py + 3, pz, nx, y + 3, nz, .7, GOLD);
                px = nx; py = y; pz = nz;
            }
            // Taller outer needle spires form a radial crown at the skyline.
            int sx = round(Math.sin(a) * 98), sz = round(Math.cos(a) * 98);
            int height = z < -20 ? 112 : z > 20 ? 86 : 98;
            spire(b, sx, sz, f, height, 4);
        }
        for (int y = f + 79; y <= f + 82; y++) b.ring(0, y, 0, 85, 3.4, BRICK);
        b.ring(0, f + 83, 0, 85, 3.6, GOLD);
        b.ring(0, f + 85, 0, 85, 2.6, WHITE);
        // Recessed blue panels under the cornice read as decorative coffers.
        for (int i = 0; i < 72; i++) {
            double a = i * TAU / 72;
            int x = round(Math.sin(a) * 85), z = round(Math.cos(a) * 85);
            b.block(x, f + 78, z, i % 3 == 0 ? LIGHT : BLUE);
            if (i % 3 == 0) b.block(x, f + 86, z, CARVED);
        }
    }

    private static void rearApse(BossStructure b) {
        int f = b.floor, cz = -89, rx = 31, rz = 27;
        // A genuinely curved, enclosed treasury is entered from the front court.
        for (int y = f; y <= f + 64; y++) b.ellipse(0, y, cz, rx, rz, WHITE);
        for (int y = f + 1; y <= f + 62; y++) b.ellipse(0, y, cz, rx - 4, rz - 4, Material.AIR);
        for (int y : new int[]{f + 3, f + 30, f + 60, f + 64})
            b.ellipseRing(0, y, cz, rx + 1, rz + 1, 1.7, y == f + 60 ? GOLD : CARVED);
        b.box(-7, f + 1, -66, 7, f + 18, -59, Material.AIR);
        for (int x = -9; x <= 9; x++) {
            int y = f + 17 + round(9 * Math.sqrt(Math.max(0, 1 - x * x / 81.0)));
            b.box(x, y, -63, x, y + 2, -60, CARVED);
            b.block(x, y + 3, -61, GOLD);
        }
        // Deep radial window openings have gold tracery and two glass surfaces.
        for (int i = 1; i < 12; i++) {
            double a = i * TAU / 12;
            for (int y = f + 12; y <= f + 53; y++) {
                double width = y > f + 45 ? .10 * Math.sqrt((f + 54 - y) / 9.0) : .10;
                for (double da = -width; da <= width; da += .025) {
                    double theta = a + da;
                    b.line(Math.sin(theta) * (rx - 4), y, cz + Math.cos(theta) * (rz - 4),
                            Math.sin(theta) * rx, y, cz + Math.cos(theta) * rz,
                            .45, Math.abs(da) < .015 || y % 11 == 0 ? GOLD : BLUE);
                }
            }
            int x = round(Math.sin(a) * 33), z = cz + round(Math.cos(a) * 25);
            pier(b, x, z, f, f + 66, 2);
            for (int t = 0; t <= 12; t++) {
                int r = Math.max(0, 3 - t / 4);
                b.disk(x, f + 67 + t, z, r, t % 4 == 0 ? GOLD : CARVED);
            }
            b.block(x, f + 80, z, LIGHT);
        }
        roseWindow(b, 0, f + 44, -61, 13);
        // A turquoise dome has sixteen structural ribs and horizontal gold bands.
        for (int h = 1; h <= 51; h++) {
            double angle = h / 52.0 * Math.PI / 2;
            int drx = Math.max(2, round(rx * Math.cos(angle)));
            int drz = Math.max(2, round(rz * Math.cos(angle)));
            b.ellipseRing(0, f + 64 + h, cz, drx, drz, 1.5,
                    h % 13 == 0 ? GOLD : Material.OXIDIZED_COPPER);
        }
        for (int rib = 0; rib < 16; rib++) {
            double a = rib * TAU / 16;
            double px = Math.sin(a) * rx, py = f + 64, pz = cz + Math.cos(a) * rz;
            for (int h = 3; h <= 51; h += 3) {
                double angle = h / 52.0 * Math.PI / 2;
                double x = Math.sin(a) * rx * Math.cos(angle);
                double z = cz + Math.cos(a) * rz * Math.cos(angle);
                b.line(px, py, pz, x, f + 64 + h, z, .9, GOLD);
                px = x; py = f + 64 + h; pz = z;
            }
        }
        for (int y = f + 115; y <= f + 132; y++) {
            b.ellipseRing(0, y, cz, 5, 5, 1, y % 5 == 0 ? GOLD : BLUE);
            for (int side : new int[]{-1, 1}) {
                b.block(side * 4, y, cz, CARVED);
                b.block(0, y, cz + side * 4, CARVED);
            }
        }
        for (int h = 0; h <= 14; h++) b.disk(0, f + 133 + h, cz, Math.max(0, 7 - h / 2), h % 3 == 0 ? GOLD : BRICK);
        b.box(0, f + 148, cz, 0, f + 153, cz, GOLD);
        b.block(0, f + 154, cz, LIGHT);
        // Sheltered treasury alcoves, mosaic aisles, and a raised ceremonial dais.
        for (int side : new int[]{-1, 1}) {
            b.box(side < 0 ? -22 : 16, f, -95, side < 0 ? -16 : 22, f + 2, -84, CARVED);
            b.box(side < 0 ? -22 : 18, f + 3, -94, side < 0 ? -18 : 22, f + 4, -86, BLUE);
            b.block(side * 20, f + 5, -90, LIGHT);
        }
        b.box(-9, f, -105, 9, f + 2, -100, BRICK);
        b.box(-7, f + 3, -105, 7, f + 3, -101, GOLD);
        b.box(-3, f, -100, 2, f, -61, CARVED);
    }

    private static void armillaryCrown(BossStructure b) {
        int f = b.floor;
        // The central canopy is an open astronomical instrument. Its intersecting
        // tilted orbits remain high above the full 28-block combat clearance.
        b.ring(0, f + 114, 0, 55, 2, BRICK);
        b.ring(0, f + 116, 0, 55, 1.2, GOLD);
        b.ring(0, f + 118, 0, 54, 1, BLUE);
        orbit(b, f + 118, 58, 45, Math.toRadians(63), Math.toRadians(25), GOLD, 1.05);
        orbit(b, f + 118, 58, 45, Math.toRadians(-63), Math.toRadians(25), GOLD, 1.05);
        orbit(b, f + 119, 52, 64, Math.PI / 2, Math.toRadians(115), BRICK, 1.3);
        orbit(b, f + 119, 52, 64, Math.PI / 2, Math.toRadians(115), GOLD, .55);
        orbit(b, f + 118, 50, 47, Math.toRadians(37), Math.toRadians(105), BLUE, .9);
        for (int i = 0; i < 12; i++) {
            double a = i * TAU / 12;
            int x = round(Math.sin(a) * 55), z = round(Math.cos(a) * 55);
            b.box(x, f + 117, z, x, f + 126, z, GOLD);
            crystal(b, x, f + 128, z, 2, 5);
            // A chain and drop beneath each support gives the ring visible depth.
            b.box(x, f + 99, z, x, f + 112, z, Material.IRON_BARS);
            crystal(b, x, f + 96, z, 2, 5);
        }
        crystal(b, 0, f + 134, 0, 11, 26);
        b.ring(0, f + 134, 0, 13, 1, GOLD);
        b.ring(0, f + 129, 0, 11.5, 1, CARVED);
        b.ring(0, f + 139, 0, 11.5, 1, CARVED);
        b.box(0, f + 111, 0, 0, f + 157, 0, LIGHT);
        // The apex is a small four-point star, at height 303 within the reservation.
        b.box(-3, 299, 0, 3, 300, 0, GOLD);
        b.box(0, 299, -3, 0, 300, 3, GOLD);
        b.box(0, 298, 0, 0, 302, 0, CARVED);
        b.block(0, 303, 0, LIGHT);
    }

    private static void pier(BossStructure b, int x, int z, int base, int top, int radius) {
        b.box(x - radius - 2, base, z - radius - 2, x + radius + 2, base + 2, z + radius + 2, BRICK);
        b.box(x - radius - 1, base + 3, z - radius - 1, x + radius + 1, base + 5, z + radius + 1, CARVED);
        b.box(x - radius, base + 6, z - radius, x + radius, top - 5, z + radius, WHITE);
        for (int d = -radius; d <= radius; d += 2) {
            b.box(x + d, base + 8, z - radius - 1, x + d, top - 8, z - radius - 1, Material.QUARTZ_PILLAR, BlockFace.UP);
            b.box(x + d, base + 8, z + radius + 1, x + d, top - 8, z + radius + 1, Material.QUARTZ_PILLAR, BlockFace.UP);
            b.box(x - radius - 1, base + 8, z + d, x - radius - 1, top - 8, z + d, Material.QUARTZ_PILLAR, BlockFace.UP);
            b.box(x + radius + 1, base + 8, z + d, x + radius + 1, top - 8, z + d, Material.QUARTZ_PILLAR, BlockFace.UP);
        }
        b.box(x - radius - 1, top - 5, z - radius - 1, x + radius + 1, top - 3, z + radius + 1, CARVED);
        b.box(x - radius - 2, top - 2, z - radius - 2, x + radius + 2, top, z + radius + 2, WHITE);
        b.box(x - radius - 2, top + 1, z - radius - 2, x + radius + 2, top + 1, z + radius + 2, GOLD);
    }

    private static void spire(BossStructure b, int x, int z, int base, int height, int radius) {
        for (int y = base; y <= base + 6; y++) b.disk(x, y, z, radius + 2 - (y - base) / 3, CARVED);
        for (int y = base + 7; y <= base + height - 30; y++) {
            b.disk(x, y, z, radius, y % 18 == 0 ? GOLD : WHITE);
            if (y > base + 17 && y < base + height - 38) {
                b.block(x + radius, y, z, BLUE); b.block(x - radius, y, z, BLUE);
                b.block(x, y, z + radius, BLUE); b.block(x, y, z - radius, BLUE);
            }
        }
        for (int y = base + height - 29; y <= base + height - 25; y++) b.disk(x, y, z, radius + 2, CARVED);
        b.disk(x, base + height - 24, z, radius + 2, GOLD);
        for (int h = 0; h < 20; h++) b.disk(x, base + height - 23 + h, z,
                Math.max(0, radius + 1 - h / 4), h % 6 == 0 ? GOLD : BRICK);
        b.box(x, base + height - 3, z, x, base + height + 3, z, GOLD);
        b.block(x, base + height + 4, z, LIGHT);
        for (int side : new int[]{-1, 1}) {
            b.line(x + side * (radius + 5), base + 4, z,
                    x + side * radius, base + 24, z, .9, BRICK);
            b.line(x, base + 4, z + side * (radius + 5),
                    x, base + 24, z + side * radius, .9, BRICK);
        }
    }

    private static void arc(BossStructure b, double radius, double start, double end,
                            double spring, double rise, double thickness, Material m) {
        int steps = Math.max(8, round((end - start) * radius * 1.5));
        double px = Math.sin(start) * radius, py = spring, pz = Math.cos(start) * radius;
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps, a = start + (end - start) * t;
            double x = Math.sin(a) * radius, y = spring + rise * Math.sin(t * Math.PI), z = Math.cos(a) * radius;
            b.line(px, py, pz, x, y, z, thickness, m);
            px = x; py = y; pz = z;
        }
    }

    private static void orbit(BossStructure b, double cy, double ru, double rv, double tilt,
                              double azimuth, Material m, double thickness) {
        double px = 0, py = 0, pz = 0;
        for (int i = 0; i <= 360; i++) {
            double a = i * TAU / 360;
            double u = Math.cos(a) * ru, v = Math.sin(a) * rv;
            double x = u * Math.cos(azimuth) - v * Math.cos(tilt) * Math.sin(azimuth);
            double y = cy + v * Math.sin(tilt);
            double z = u * Math.sin(azimuth) + v * Math.cos(tilt) * Math.cos(azimuth);
            if (i > 0) b.line(px, py, pz, x, y, z, thickness, m);
            px = x; py = y; pz = z;
        }
    }

    private static void crystal(BossStructure b, int x, int cy, int z, int radius, int halfHeight) {
        for (int dy = -halfHeight; dy <= halfHeight; dy++) {
            int r = round(radius * (1 - Math.abs(dy) / (double) halfHeight));
            for (int dx = -r; dx <= r; dx++) {
                int dz = r - Math.abs(dx);
                b.box(x + dx, cy + dy, z - dz, x + dx, cy + dy, z + dz,
                        Math.abs(dx) == r || dy % 8 == 0 ? BLUE : Material.WHITE_STAINED_GLASS);
            }
            b.block(x, cy + dy, z, Math.abs(dy) < halfHeight - 1 ? LIGHT : GOLD);
        }
    }

    private static void roseWindow(BossStructure b, int cx, int cy, int z, int radius) {
        for (int dx = -radius - 2; dx <= radius + 2; dx++) {
            for (int dy = -radius - 2; dy <= radius + 2; dy++) {
                double r = Math.hypot(dx, dy);
                if (r > radius + 1) continue;
                double a = Math.atan2(dy, dx);
                Material m = r > radius - 1 ? CARVED : Math.abs(Math.sin(a * 8)) < .18 || Math.abs(r - radius * .55) < .65 ? GOLD : BLUE;
                b.box(cx + dx, cy + dy, z - 1, cx + dx, cy + dy, z + 1, m);
            }
        }
        b.block(cx, cy, z + 2, LIGHT);
    }

    private static int round(double value) { return (int) Math.round(value); }
}
