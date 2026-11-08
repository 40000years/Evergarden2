package com.example.voidscape.world;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;

/**
 * <b>Vharok, Crimson Ember &mdash; The Crimson Caldera (รังมังกรเพลิงโลกันตร์)</b>
 * <p>
 * Architectural Typology: <b>Volcanic Ribcage Caldera &amp; Colossal Obsidian Dragon-Skull Citadel</b>.
 * <ul>
 *   <li>Central 109-block open-sky volcanic nest basin ({@code r <= 54}) inlaid with an 8-point
 *       molten magma &amp; {@code GILDED_BLACKSTONE} dragon sigil.</li>
 *   <li>Eight titanic inward-curving obsidian-and-gold dragon ribs and East/West Lava-Aqueduct
 *       arcades with cascading magma waterfalls.</li>
 *   <li>North Sanctum sculpted as a monumental horned Dragon-Skull Citadel &amp; Volcanic Hoard
 *       Cavern with 60-block sweeping obsidian-gold horns, blazing magma eye-sockets, fanged
 *       jaw portal, and 8 treasure chests nestled among golden hoard mountains.</li>
 * </ul>
 */
final class FireDragonNestDesign {
    private static final Material OBSIDIAN = Material.OBSIDIAN;
    private static final Material CRYING = Material.CRYING_OBSIDIAN;
    private static final Material GILDED = Material.GILDED_BLACKSTONE;
    private static final Material BLACKSTONE = Material.BLACKSTONE;
    private static final Material BRICK = Material.POLISHED_BLACKSTONE_BRICKS;
    private static final Material POLISHED = Material.POLISHED_BLACKSTONE;
    private static final Material CHISELED = Material.CHISELED_POLISHED_BLACKSTONE;
    private static final Material DEEPSLATE = Material.DEEPSLATE_TILES;
    private static final Material BASALT = Material.SMOOTH_BASALT;
    private static final Material CRIMSON = Material.CRIMSON_HYPHAE;
    private static final Material RED_BRICK = Material.RED_NETHER_BRICKS;
    private static final Material MAGMA = Material.MAGMA_BLOCK;
    private static final Material GOLD = Material.GOLD_BLOCK;
    private static final Material RAW_GOLD = Material.RAW_GOLD_BLOCK;
    private static final Material SHROOM = Material.SHROOMLIGHT;
    private static final Material RED_GLASS = Material.RED_STAINED_GLASS;
    private static final Material ORANGE_GLASS = Material.ORANGE_STAINED_GLASS;
    private static final Material CHAIN = Material.IRON_CHAIN;
    private static final Material SOUL = Material.SOUL_LANTERN;

    private FireDragonNestDesign() {}

    static void build(DragonNest s) {
        calderaIsland(s);
        moltenSigilFloor(s);
        wovenNestRimAndHoards(s);
        colossalDragonRibCrown(s);
        lavaAqueductArcades(s);
        northDragonSkullCitadel(s);
        southFangProcessional(s);
        // Guarantee the 109-block central dragon basin and arrival spine stay completely unobstructed.
        s.clearOpenCaldera(s.kind.arenaRadius - 1, s.kind.maxY);
        s.box(-5, s.floor + 1, s.kind.chestZ + 4, 5, s.floor + 9, s.kind.arrivalZ + 2, Material.AIR);
    }

    private static void calderaIsland(DragonNest s) {
        int f = s.floor;
        for (int x = -112; x <= 112; x++) {
            for (int z = -112; z <= 112; z++) {
                double d = Math.hypot(x, z);
                double angle = Math.atan2(z, x);
                double lobe = 96.0
                    + 9.0 * Math.cos(angle * 4.0)
                    + 6.0 * Math.sin(angle * 8.0 + 0.6)
                    + (z < -48 ? 14.0 : 0.0)
                    + (z > 52 && Math.abs(x) < 26 ? 11.0 : 0.0);
                if (d > lobe) continue;
                double edge = d / lobe;
                int depth = (int) Math.round(54.0 * Math.pow(1.0 - edge, 0.68)
                    + 7.0 * Math.sin(x * 0.17) * Math.cos(z * 0.17));
                int y1 = Math.max(DragonNest.MIN_Y + 4, f - 8 - depth);
                s.box(x, y1, z, x, f - 14, z, BASALT);
                s.box(x, Math.max(y1, f - 13), z, x, f - 5, z, BLACKSTONE);
                s.box(x, f - 4, z, x, f - 1, z, DEEPSLATE);
                if (edge > 0.80 && (Math.floorMod(x * 3 + z * 5, 6) == 0)) {
                    s.block(x, f, z, GILDED);
                } else {
                    s.block(x, f, z, BRICK);
                }
                if (edge > 0.35 && edge < 0.88 && Math.floorMod(x * 5 - z * 7, 27) == 0) {
                    s.box(x, y1, z, x, Math.min(f - 6, y1 + 10), z, MAGMA);
                    s.block(x, y1 - 1, z, SHROOM);
                }
            }
        }
        // Hanging volcanic stalactites & lava drips beneath the island
        for (int i = 0; i < 18; i++) {
            double a = i * Math.PI / 9.0;
            int sx = (int) Math.round(Math.cos(a) * 66);
            int sz = (int) Math.round(Math.sin(a) * 66);
            int bottom = DragonNest.MIN_Y + 2 + (i % 3) * 4;
            for (int y = bottom; y <= f - 16; y++) {
                int r = Math.max(0, (y - bottom) / 6);
                s.disk(sx, y, sz, r, y % 5 == 0 ? GILDED : OBSIDIAN);
            }
            s.block(sx, bottom - 1, sz, SHROOM);
        }
    }

    private static void moltenSigilFloor(DragonNest s) {
        int f = s.floor, r = s.kind.arenaRadius;
        s.disk(0, f - 3, 0, r - 2, OBSIDIAN);
        s.disk(0, f - 2, 0, r - 3, MAGMA);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            int gx = (int) Math.round(Math.cos(a) * 28);
            int gz = (int) Math.round(Math.sin(a) * 28);
            s.disk(gx, f - 2, gz, 6, SHROOM);
        }
        s.disk(0, f - 2, 0, 11, SHROOM);

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double d = Math.hypot(x, z);
                if (d > r + 0.5) continue;
                double angle = Math.atan2(z, x);
                double star8 = Math.abs(Math.sin(angle * 4.0));
                double petal = 26.0 + 7.5 * Math.cos(angle * 8.0);
                boolean outerRing = Math.abs(d - (r - 2.5)) < 1.8;
                boolean midRing = Math.abs(d - 35.0) < 1.5;
                boolean innerRing = Math.abs(d - 15.0) < 1.5;
                boolean petalRing = Math.abs(d - petal) < 1.2;

                Material top = DEEPSLATE;
                Material under = BRICK;
                if (d < 8.5) {
                    top = d < 3.5 ? GOLD : (d < 6.0 ? GILDED : ORANGE_GLASS);
                    under = d < 3.5 ? GOLD : MAGMA;
                } else if (outerRing || midRing || innerRing || petalRing) {
                    top = (Math.floorMod(x + z, 4) == 0) ? GOLD : GILDED;
                    under = GILDED;
                } else if (d > 15 && d < 34 && star8 < 0.22) {
                    top = RED_GLASS;
                    under = MAGMA;
                } else if (d > 35 && d < r - 4 && star8 > 0.80) {
                    top = ORANGE_GLASS;
                    under = SHROOM;
                } else if (Math.floorMod(x * 7 + z * 11, 17) == 0) {
                    top = GILDED;
                } else if (Math.floorMod(x * 3 - z * 5, 9) == 0) {
                    top = BRICK;
                }
                s.block(x, f - 1, z, under);
                s.block(x, f, z, top);
            }
        }
    }

    private static void wovenNestRimAndHoards(DragonNest s) {
        int f = s.floor;
        for (int x = -76; x <= 76; x++) {
            for (int z = -76; z <= 76; z++) {
                if (Math.abs(x) < 14 && (z < -48 || z > 48)) continue;
                double d = Math.hypot(x, z);
                if (d < 54.5 || d > 73.5) continue;
                double a = Math.atan2(z, x);
                double wave = Math.sin(a * 12.0) * 2.0 + Math.cos(a * 6.0) * 2.4;
                double profile = 1.0 - Math.abs(d - 63.0) / 9.5;
                int h = Math.max(1, (int) Math.round(profile * 10.0 + wave));
                for (int dy = 1; dy <= h; dy++) {
                    Material m;
                    if (dy == h && (Math.floorMod(x + z, 5) == 0)) m = GILDED;
                    else if ((x + z + dy) % 5 == 0) m = CRIMSON;
                    else if ((x * 3 + z) % 7 == 0) m = CRYING;
                    else m = dy > h - 2 ? BRICK : OBSIDIAN;
                    s.block(x, f + dy, z, m);
                }
                if (Math.abs(d - 58.0) < 1.2 && Math.floorMod(x * 3 + z * 5, 11) == 0) {
                    s.block(x, f + h + 1, z, MAGMA);
                }
            }
        }

        int[][] hoards = {
            {-49, -28, 6}, {49, -28, 6}, {-52, 12, 5}, {52, 12, 5},
            {-38, 42, 6}, {38, 42, 6}, {-32, -47, 5}, {32, -47, 5}
        };
        for (int[] h : hoards) {
            s.hoardMound(h[0], f + 1, h[1], h[2], GILDED, GOLD, RAW_GOLD, SHROOM);
        }
    }

    private static void colossalDragonRibCrown(DragonNest s) {
        int f = s.floor;
        double[] angles = {
            Math.toRadians(24), Math.toRadians(54), Math.toRadians(126), Math.toRadians(156),
            Math.toRadians(204), Math.toRadians(234), Math.toRadians(306), Math.toRadians(336)
        };
        for (int i = 0; i < angles.length; i++) {
            double a = angles[i];
            double cos = Math.cos(a), sin = Math.sin(a);
            int baseR = 84;
            int bx = (int) Math.round(cos * baseR), bz = (int) Math.round(sin * baseR);
            s.cylinder(bx, f + 1, f + 15, bz, 6, BRICK);
            s.ring(bx, f + 15, bz, 6.4, 1.4, GILDED);
            s.disk(bx, f + 16, bz, 4, MAGMA);

            int tipX = (int) Math.round(cos * 56), tipZ = (int) Math.round(sin * 56);
            int tipY = f + 84 + (i % 2) * 8;
            for (int step = 0; step <= 48; step++) {
                double t = step / 48.0;
                double r = baseR + (1.0 - t) * 10.0 * Math.sin(t * Math.PI) - t * 28.0;
                double cx = cos * r;
                double cz = sin * r;
                double cy = f + 12 + Math.pow(t, 0.78) * (tipY - (f + 12));
                double thick = Math.max(1.2, 4.6 * (1.0 - 0.68 * t));
                Material core = (step % 5 == 0) ? GILDED : (step % 2 == 0 ? OBSIDIAN : BRICK);
                s.line(cx, cy, cz, cx + cos * 0.5, cy + 1.0, cz + sin * 0.5, thick, core);
                // Serrated golden dorsal bone-plates along the back of each rib
                if (step % 4 == 0) {
                    double spurLen = 4.5 * (1.0 - 0.5 * t);
                    s.line(
                        cx + cos * thick, cy, cz + sin * thick,
                        cx + cos * (thick + spurLen), cy + 3.5, cz + sin * (thick + spurLen),
                        1.1, step % 8 == 0 ? GOLD : RED_BRICK
                    );
                }
                if (step > 12 && step % 9 == 0) {
                    int ix = (int) Math.round(cx - cos * (thick + 0.5));
                    int iz = (int) Math.round(cz - sin * (thick + 0.5));
                    int iy = (int) Math.round(cy);
                    s.block(ix, iy, iz, SHROOM);
                    s.box(ix, iy - 5, iz, ix, iy - 1, iz, CHAIN);
                    s.block(ix, iy - 6, iz, SOUL, BlockFace.UP);
                }
            }
            s.block(tipX, tipY + 1, tipZ, GOLD);
            s.block(tipX, tipY + 2, tipZ, SHROOM);
        }
    }

    private static void lavaAqueductArcades(DragonNest s) {
        int f = s.floor;
        // Curved East & West Volcanic Cloister Arcades with magma waterfalls and twin bastion spires
        for (int side : new int[]{-1, 1}) {
            double start = side < 0 ? Math.toRadians(148) : Math.toRadians(-32);
            double end = side < 0 ? Math.toRadians(212) : Math.toRadians(32);
            int rad = 94;
            for (int j = 0; j <= 4; j++) {
                double a = start + (end - start) * j / 4.0;
                int px = (int) Math.round(Math.cos(a) * rad);
                int pz = (int) Math.round(Math.sin(a) * rad);
                s.cylinder(px, f + 1, f + 32, pz, 4, BRICK);
                s.ring(px, f + 18, pz, 4.5, 1.2, GILDED);
                s.ring(px, f + 32, pz, 4.8, 1.3, GILDED);
                // Pointed volcanic turret cap
                for (int h = 1; h <= 16; h++) {
                    s.cylinder(px, f + 32 + h, f + 32 + h, pz, Math.max(0, 4 - h / 4), h % 4 == 0 ? GOLD : OBSIDIAN);
                }
                s.block(px, f + 49, pz, SHROOM);
                if (j < 4) {
                    double nextA = start + (end - start) * (j + 1) / 4.0;
                    int nx = (int) Math.round(Math.cos(nextA) * rad);
                    int nz = (int) Math.round(Math.sin(nextA) * rad);
                    s.line(px, f + 28, pz, nx, f + 28, nz, 2.2, BRICK);
                    s.line(px, f + 30, pz, nx, f + 30, nz, 1.2, GILDED);
                    s.line(px, f + 31, pz, nx, f + 31, nz, 0.8, MAGMA);
                    // Glowing lava-glass waterfall curtain in the middle bay
                    if (j == 1 || j == 2) {
                        int mx = (px + nx) / 2, mz = (pz + nz) / 2;
                        s.box(mx - 1, f + 2, mz - 2, mx + 1, f + 27, mz + 2, ORANGE_GLASS);
                        s.box(mx, f + 2, mz - 1, mx, f + 27, mz + 1, MAGMA);
                    }
                }
            }
        }
    }

    private static void northDragonSkullCitadel(DragonNest s) {
        int f = s.floor;
        // Stepped volcanic plinth & hoard cavern foundation
        s.ellipse(0, f + 1, -82, 42, 26, BRICK);
        s.ellipse(0, f + 3, -82, 40, 25, DEEPSLATE);
        s.ellipseRing(0, f + 4, -82, 40, 25, 1.6, GILDED);
        for (int step = 0; step < 5; step++) {
            int z = -54 - step * 2;
            s.box(-14, f + 1 + step, z, 14, f + 1 + step, z + 1, step % 2 == 0 ? GILDED : POLISHED);
        }

        int fy = f + 4;
        // Sculpted domed Dragon-Skull Cranium & Ribbed Volcanic Cavern (instead of a rectangular box!)
        for (int h = 0; h <= 46; h++) {
            double t = h / 46.0;
            double rx = 36.0 * Math.pow(1.0 - t * 0.65, 0.55);
            double rz = 23.0 * Math.pow(1.0 - t * 0.55, 0.55);
            int cz = -83 - (int) Math.round(t * 6.0);
            Material shell = (h % 7 == 0) ? GILDED : (h % 3 == 0 ? OBSIDIAN : BRICK);
            s.ellipse(0, fy + h, cz, rx, rz, shell);
            if (h <= 38) {
                s.ellipse(0, fy + h, cz, Math.max(2, rx - 4), Math.max(2, rz - 4), Material.AIR);
            }
        }
        // Floor of the Hoard Cavern
        s.ellipse(0, fy, -83, 34, 20, DEEPSLATE);
        s.box(-5, fy, -100, 5, fy, -58, GILDED);
        s.box(-2, fy, -98, 2, fy, -60, GOLD);

        // Gaping Fanged Dragon-Jaw Portal at z = -60..-66
        for (int x = -12; x <= 12; x++) {
            int archH = fy + 22 - (x * x) / 8;
            s.box(x, fy + 1, -68, x, archH, -58, Material.AIR);
            s.box(x, archH + 1, -64, x, archH + 3, -59, GILDED);
            s.block(x, archH + 4, -60, GOLD);
            // Hanging obsidian & gold dragon fangs along the upper jaw
            if (Math.abs(x) % 4 == 2 && Math.abs(x) <= 10) {
                int fangLen = Math.abs(x) == 10 ? 6 : 4;
                s.box(x, archH - fangLen, -60, x, archH, -59, OBSIDIAN);
                s.block(x, archH - fangLen - 1, -60, GOLD);
            }
        }
        // Lower jaw fangs rising from the entrance sides
        for (int side : new int[]{-1, 1}) {
            s.line(side * 15, fy + 1, -58, side * 11, fy + 14, -55, 2.2, OBSIDIAN);
            s.line(side * 15, fy + 2, -57, side * 11, fy + 15, -55, 1.0, GOLD);
            s.block(side * 11, fy + 15, -55, SHROOM);
        }

        // Twin Blazing Molten Eye-Sockets on the Dragon Skull Facade
        for (int side : new int[]{-1, 1}) {
            int ex = side * 14, ey = fy + 31, ez = -65;
            s.ellipseRing(ex, ey, ez, 6, 4, 1.4, GILDED);
            for (int dx = -4; dx <= 4; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    if (dx * dx / 16.0 + dy * dy / 4.0 <= 1.0) {
                        s.block(ex + dx, ey + dy, ez, Math.abs(dx) <= 1 && Math.abs(dy) <= 1 ? SHROOM : ORANGE_GLASS);
                        s.block(ex + dx, ey + dy, ez - 1, MAGMA);
                    }
                }
            }
        }

        // Colossal Sweeping Twin Dragon Horns (rising from the skull temples to floor + 124!)
        for (int side : new int[]{-1, 1}) {
            for (int step = 0; step <= 56; step++) {
                double t = step / 56.0;
                double hx = side * (22.0 + 26.0 * Math.sin(t * Math.PI * 0.75));
                double hy = fy + 34.0 + 82.0 * Math.pow(t, 0.85);
                double hz = -76.0 - 24.0 * t + 14.0 * Math.sin(t * Math.PI);
                double rad = Math.max(1.0, 5.2 * (1.0 - 0.78 * t));
                Material mat = (step % 5 == 0) ? GOLD : (step % 3 == 0 ? GILDED : OBSIDIAN);
                s.line(hx, hy, hz, hx + side * 0.4, hy + 1.0, hz - 0.3, rad, mat);
            }
            // Secondary lower cheek-horns
            for (int step = 0; step <= 32; step++) {
                double t = step / 32.0;
                double hx = side * (28.0 + 18.0 * t);
                double hy = fy + 18.0 + 36.0 * t;
                double hz = -72.0 + 8.0 * t;
                double rad = Math.max(1.0, 3.6 * (1.0 - 0.72 * t));
                s.line(hx, hy, hz, hx + side * 0.3, hy + 0.8, hz, rad, step % 4 == 0 ? GILDED : OBSIDIAN);
            }
        }

        // Central Dorsal Crest & Volcanic Spire rising above the skull crown
        for (int i = 0; i < 6; i++) {
            int sz = -72 - i * 6;
            int sh = 18 + i * 5;
            s.line(0, fy + 42 - i, sz, 0, fy + 42 + sh, sz - 4, 2.2, i % 2 == 0 ? GILDED : OBSIDIAN);
            s.block(0, fy + 43 + sh, sz - 4, GOLD);
            s.block(0, fy + 44 + sh, sz - 4, SHROOM);
        }

        // Sovereign Hoard Vault inside the Skull Cavern: Central Altar + 8 Treasure Chests
        s.cylinder(0, fy + 1, fy + 4, -86, 7, GILDED);
        s.cylinder(0, fy + 5, fy + 6, -86, 5, GOLD);
        s.block(0, fy + 7, -86, CRYING);
        s.block(0, fy + 8, -86, SHROOM);

        int[] chestZs = {-72, -78, -84, -90};
        for (int czBay : chestZs) {
            s.hoardMound(-20, fy + 1, czBay, 4, GILDED, GOLD, RAW_GOLD, SHROOM);
            s.hoardMound(20, fy + 1, czBay, 4, GILDED, GOLD, RAW_GOLD, SHROOM);
            s.treasure(-15, fy + 1, czBay, BlockFace.EAST);
            s.treasure(15, fy + 1, czBay, BlockFace.WEST);
        }
    }

    private static void southFangProcessional(DragonNest s) {
        int f = s.floor;
        s.box(-12, f - 4, 54, 12, f, 110, BRICK);
        s.box(-13, f + 1, 56, -11, f + 3, 108, GILDED);
        s.box(11, f + 1, 56, 13, f + 3, 108, GILDED);
        s.box(-4, f, 54, 4, f, 110, GILDED);
        s.box(-1, f, 54, 1, f, 110, GOLD);

        for (int z : new int[]{68, 86, 102}) {
            for (int side : new int[]{-1, 1}) {
                int px = side * 14;
                s.box(px - 2, f + 1, z - 2, px + 2, f + 16, z + 2, BRICK);
                s.box(px - 3, f + 14, z - 3, px + 3, f + 16, z + 3, GILDED);
                s.block(px, f + 17, z, MAGMA);
                s.line(px, f + 16, z, side * 3, f + 34, z, 2.0, OBSIDIAN);
                s.line(px, f + 17, z, side * 3, f + 35, z, 0.9, GILDED);
                s.block(side * 3, f + 35, z, SHROOM);
            }
        }
    }
}
