"""Crystal City (The End): an abandoned End-stone & Purpur citadel with asymmetric crystal spires,
a half-collapsed grand library, reliquary conservatory and an open-sky ritual boss court.

Local frame: site centre (0, 0), floor y=0, entrance on +Z (south). Built on a stepped
island plateau with a sculpted, depth-limited inverted-cone underside (down to y=-28) so it
merges into natural outer End islands without ever dropping long pillars into the void.
Boss court: radius 36 (73 blocks across), completely clear to the sky (y=1..72).
"""
from __future__ import annotations

import math
import numpy as np

from .grid import Grid, hash2, hash3, fbm

# ---- materials ----
ES = 'end_stone'
ESB = 'end_stone_bricks'
PUR = 'purpur_block'
PPIL = 'purpur_pillar[axis=y]'
PPIL_X = 'purpur_pillar[axis=x]'
PPIL_Z = 'purpur_pillar[axis=z]'
OBS = 'obsidian'
CRY = 'crying_obsidian'
AME = 'amethyst_block'
BAME = 'budding_amethyst'
SEA = 'sea_lantern'
PBB = 'polished_blackstone_bricks'
CHB = 'chiseled_polished_blackstone'
DT = 'deepslate_tiles'
MGLASS = 'magenta_stained_glass'
PGLASS = 'purple_stained_glass'
ENDROD_UP = 'end_rod[facing=up]'
ENDROD_DN = 'end_rod[facing=down]'
SLANTH = 'soul_lantern[hanging=true,waterlogged=false]'
CHAIN = 'iron_chain[axis=y,waterlogged=false]'


def stair(mat, facing, half='bottom'):
    return f'{mat}_stairs[facing={facing},half={half},shape=straight,waterlogged=false]'


def slab(mat, kind='bottom'):
    return f'{mat}_slab[type={kind},waterlogged=false]'


X0, X1, Y0, Y1, Z0, Z1 = -78, 78, -30, 76, -78, 86
COURT_R = 36
ARENA_H = 72


def build() -> Grid:
    g = Grid(X0, X1, Y0, Y1, Z0, Z1)
    X, Z, R, TH = g.X, g.Z, g.R, g.TH
    Yv = np.arange(Y0, Y1 + 1)[None, :, None]

    # Clear the upper air box over the citadel footprint so natural End hills on the island are carved away cleanly
    sky_clear = (R <= 75) | ((np.abs(X) <= 18) & (Z >= 50) & (Z <= 84))
    g.extrude(sky_clear, 1, Y1, 'air')

    # =============== 1. sculpted island underside & stepped terrace plinth ===============
    # Tapering inverted-cone underside (depth-limited to y=-28; no single-column pillars to the void)
    rn = R + (fbm(X, Z, 9.0, 41) - 0.5) * 6.0
    cone_depth = -(4.0 + 23.0 * np.clip(1.0 - (rn / 68.0) ** 1.65, 0.0, 1.0) + (fbm(X, Z, 6.0, 43) - 0.5) * 4.0)
    # Six stalactite crystal ribs radiating under the island
    rib_angle = np.abs(((TH + 30.0) % 60.0) - 30.0)
    rib_bonus = np.clip(1.0 - rib_angle / 8.0, 0.0, 1.0) * np.clip(1.0 - np.abs(R - 34.0) / 24.0, 0.0, 1.0) * 5.0
    bot_y = np.clip((cone_depth - rib_bonus).astype(int), -28, -2)

    under_mask = ((R <= 68) | ((np.abs(X) <= 16) & (Z >= 52) & (Z <= 83)))[:, None, :] & (Yv <= -1) & (Yv >= bot_y[:, None, :])
    sh_u = hash3(
        X[:, None, :] * np.ones_like(Yv),
        Yv * np.ones_like(X[:, None, :]),
        Z[:, None, :] * np.ones_like(Yv),
        51,
    )
    g.where3(under_mask, ES)
    g.where3(under_mask & (Yv >= -5) & (sh_u > 0.35), ESB)
    g.where3(under_mask & (Yv <= -12) & (sh_u > 0.72), OBS)
    g.where3(under_mask & (Yv <= -16) & (sh_u > 0.88), CRY)
    g.where3(under_mask & (Yv <= -18) & (sh_u < 0.08), AME)

    #Glowing crystal heart at the apex of the underside (x=0, z=0, y=-28..-23)
    g.extrude(R <= 5.5, -26, -21, OBS)
    g.extrude(R <= 3.5, -28, -22, AME)
    g.extrude(R <= 1.8, -28, -24, SEA)

    # Stepped terrace plinth around the outer perimeter (R=54..72) blending into the natural End island
    for step_i, (r_in, r_out, y_top) in enumerate((
        (68.0, 73.0, -4),
        (64.0, 68.5, -3),
        (60.0, 64.5, -2),
        (56.0, 60.5, -1),
        (0.0, 56.5, 0),
    )):
        ring = (R >= r_in) & (R <= r_out)
        g.extrude(ring, y_top - 3, y_top - 1, ES)
        g.extrude(ring, y_top, y_top, ESB)
        rim = (np.abs(R - r_out) < 0.8) & (R >= 55.5)
        g.extrude(rim, y_top, y_top, PUR if step_i % 2 == 0 else CHB)

    # Main plateau paving at y=0 (R <= 56.5)
    plat = R <= 56.5
    fl = hash2(X // 2, Z // 2, 61)
    g.extrude(plat & (fl < 0.58), 0, 0, ESB)
    g.extrude(plat & (fl >= 0.58) & (fl < 0.78), 0, 0, ES)
    g.extrude(plat & (fl >= 0.78) & (fl < 0.92), 0, 0, PUR)
    g.extrude(plat & (fl >= 0.92), 0, 0, PBB)

    # =============== 2. Citadel Ring Colonnade & Upper Astral Walkway (R=37..49) ===============
    # Outer ring wall & vaulted arcade surrounding the open-sky boss court
    ring_wall = (R >= 37.0) & (R <= 49.0)
    g.extrude(ring_wall, 0, 0, ESB)
    g.extrude(ring_wall, 1, 20, ESB)
    # Purpur & obsidian architectural bands
    band_faces = ((R >= 37.0) & (R <= 38.4)) | ((R >= 47.6) & (R <= 49.0))
    for y in (5, 10, 16, 20):
        g.extrude(band_faces, y, y, PUR)
    for y in (1, 11):
        g.extrude(band_faces, y, y, CHB)
    g.extrude((np.abs(R - 37.5) < 0.6) | (np.abs(R - 48.5) < 0.6), 9, 9, CRY)

    # Ground Ring Cloister (R=39.5..46.5, y=1..8)
    cloister = (R >= 39.5) & (R <= 46.5)
    g.extrude(cloister, 0, 0, ESB)
    g.extrude((np.abs(R - 43.0) < 1.0), 0, 0, PUR)
    g.extrude(cloister, 1, 8, 'air')
    g.extrude((R >= 38.0) & (R <= 48.0), 9, 10, ESB)
    g.extrude((np.abs(R - 43.0) < 1.2), 9, 9, PUR)

    # Upper Astral Observatory Walk (R=39.0..47.0, y=11..18) — roof partially open/ruined in places
    upper_walk = (R >= 39.0) & (R <= 47.0)
    g.extrude(upper_walk, 10, 10, ESB)
    g.extrude((np.abs(R - 43.0) < 1.0), 10, 10, PUR)
    g.extrude(upper_walk, 11, 19, 'air')

    # 24 Arched Openings from Both Ground Cloister and Upper Walk onto the Boss Court
    for k in range(24):
        deg = 7.5 + 15.0 * k
        # Skip cardinal gates (handled explicitly)
        if any(abs(((deg - c + 180.0) % 360.0) - 180.0) < 15.0 for c in (0.0, 90.0, 180.0, 270.0)):
            continue
        w_ground = g.wedge(deg, 2.8) & (R >= 36.5) & (R <= 40.0)
        g.extrude(w_ground, 1, 6, 'air')
        g.extrude(g.wedge(deg, 1.8) & (R >= 36.5) & (R <= 40.0), 7, 7, 'air')
        w_upper = g.wedge(deg, 3.2) & (R >= 36.5) & (R <= 39.8)
        g.extrude(w_upper, 12, 17, 'air')
        g.extrude(w_upper & (R >= 37.0) & (R <= 38.2), 11, 11, PUR)
        a = math.radians(deg)
        lx, lz = round(math.cos(a) * 43.0), round(math.sin(a) * 43.0)
        g.set(lx, 0, lz, SEA)
        g.set(lx, 9, lz, SEA)
        g.set(lx, 10, lz, SEA)
        # Purpur pillar piers between arches on the court rim (R=37.5, strictly outside R=36)
        pa = math.radians(15.0 * k)
        px, pz = round(math.cos(pa) * 37.8), round(math.sin(pa) * 37.8)
        if math.hypot(px, pz) > COURT_R + 0.5:
            g.box(px, 1, pz, px, 19, pz, PPIL)
            g.set(px, 8, pz, SEA)
            g.set(px, 18, pz, SEA)

    # Ruined erosion on the top of the ring wall (y=18..22): deterministic jagged notches & amethyst growth
    ruin_n = fbm(X, Z, 7.5, 73)
    erode = ((R >= 37.0) & (R <= 49.0))[:, None, :] & (Yv >= (16 + (ruin_n * 6.0).astype(int))[:, None, :]) & (Yv <= 22)
    g.where3(erode, 'air')
    # Creeping amethyst crystal clusters where the upper wall fractured
    crystal_scar = ((R >= 37.5) & (R <= 48.5) & (ruin_n < 0.36))
    g.extrude(crystal_scar, 16, 17, AME)
    g.extrude(crystal_scar & (hash2(X, Z, 77) > 0.65), 18, 18, SEA)

    # =============== 3. Seven Asymmetric Crystal Spires (หอคริสตัลไม่สมมาตร) ===============
    # Each tower sits at R=44..61 (strictly outside COURT_R=36) with its own height, radius, crystal crown & tilt.
    tower_specs = [
        # (deg, radius_pos, base_r, stone_top, spire_top, tilt_dx, tilt_dz, glass_mat)
        (28.0, 50.0, 6.5, 24, 52, -0.06, -0.04, MGLASS),    # SE Observatory Spire
        (68.0, 48.0, 5.5, 20, 44, 0.0, 0.05, PGLASS),       # SSE Gate Sentinel
        (112.0, 48.0, 5.5, 16, 35, -0.05, 0.04, MGLASS),    # SSW Fractured Ruin Spire (shorter, shattered)
        (154.0, 51.0, 6.2, 22, 49, 0.07, -0.03, PGLASS),    # SW Library Beacon
        (208.0, 50.0, 6.8, 25, 56, 0.05, 0.06, MGLASS),     # NW Archive Tower
        (270.0, 52.0, 8.0, 28, 66, 0.0, -0.05, MGLASS),     # North Grand Sovereign Crystal Spire (tallest!)
        (332.0, 50.0, 6.2, 22, 47, -0.05, 0.04, PGLASS),    # NE Astrolabe Spire
    ]
    for deg, r_pos, br, stone_h, top_h, tdx, tdz, gmat in tower_specs:
        a = math.radians(deg)
        cx0, cz0 = math.cos(a) * r_pos, math.sin(a) * r_pos
        # Stone & purpur tower shaft
        for y in range(1, stone_h + 1):
            t = y / float(stone_h)
            rad = br * (1.0 - 0.18 * t)
            m = (np.hypot(X - cx0, Z - cz0) <= rad) & (R > COURT_R + 1.0)
            mat = ESB if y % 5 != 0 else (PUR if y % 10 != 0 else CHB)
            g.extrude(m, y, y, mat)
        # Hollow lower room inside each tower at y=1..8 connected to the ring cloister
        inner = (np.hypot(X - cx0, Z - cz0) <= br - 2.0) & (R > COURT_R + 2.5)
        g.extrude(inner, 0, 0, PUR)
        g.extrude(inner, 1, 7, 'air')
        # Passage from ring cloister (R=43) into the tower room
        bridge_w = g.wedge(deg, 5.0) & (R >= 42.0) & (R <= r_pos)
        g.extrude(bridge_w, 0, 0, PUR)
        g.extrude(bridge_w, 1, 6, 'air')
        g.set(round(cx0), 0, round(cz0), SEA)
        g.set(round(cx0), 7, round(cz0), SEA)

        # Asymmetric faceted crystal spire bursting out of the top of the tower (stone_h-2 .. top_h)
        for y in range(stone_h - 2, top_h + 1):
            t = (y - (stone_h - 2)) / float(max(1, top_h - (stone_h - 2)))
            cx = cx0 + tdx * (y - stone_h) * 3.2
            cz = cz0 + tdz * (y - stone_h) * 3.2
            rad = max(0.7, (br - 0.8) * ((1.0 - t) ** 0.75))
            dist = np.hypot(X - cx, Z - cz)
            # Star/crystal facet shaping
            ang = np.arctan2(Z - cz, X - cx)
            facet = rad * (0.85 + 0.22 * np.cos(5.0 * ang + y * 0.18))
            m = (dist <= facet) & (R > COURT_R + 1.0)
            core = (dist <= max(0.6, rad * 0.42)) & (R > COURT_R + 1.0)
            if y % 6 == 0:
                g.extrude(m, y, y, AME)
                g.extrude(core, y, y, SEA)
            elif y % 3 == 0:
                g.extrude(m, y, y, BAME)
                g.extrude(core, y, y, CRY)
            else:
                g.extrude(m, y, y, AME if t < 0.45 else gmat)
                g.extrude(core, y, y, SEA if y % 4 == 0 else AME)

    # =============== 4. West Ruined Grand Library & East Crystal Conservatory ===============
    # West Wing — Ruined Grand Library (X = -62..-38, Z = -20..20)
    lib_outer = g.rect(-62, -20, -38, 20) & (R > COURT_R + 1.0)
    g.extrude(lib_outer, 0, 0, ESB)
    g.extrude(lib_outer, 1, 16, ESB)
    lib_inner = g.rect(-59, -17, -40, 17) & (R > COURT_R + 2.0)
    g.extrude(lib_inner, 0, 0, PUR)
    g.extrude(lib_inner, 1, 14, 'air')
    # Connect Library to Ring Cloister and Boss Court via West Portal
    west_portal = g.rect(-59, -4, -35, 4)
    g.extrude(west_portal, 0, 0, PUR)
    g.extrude(west_portal, 1, 7, 'air')

    # Ruined collapsed roof over the north half of the Library (so starlight/sky pours in!)
    collapse = g.rect(-61, -18, -39, 4) & (R > COURT_R + 1.5)
    for y in range(9, 18):
        rm = collapse & (fbm(X + y, Z - y, 5.0, 81) > 0.38)
        g.extrude(rm, y, y, 'air')
    # Library stone book-stacks / alcoves along the walls & glowing crystal study tables (keep central aisle X=-51..-45 clear!)
    for z in (-14, -8, 8, 14):
        # West wall lectern alcoves
        g.box(-58, 1, z - 1, -56, 6, z + 1, CHB)
        g.box(-57, 2, z, -57, 4, z, AME)
        g.set(-56, 5, z, SEA)
        # Standing purpur columns (southern intact half) vs shattered column stumps (northern ruined half)
        top_c = 13 if z > 0 else 5
        g.box(-53, 1, z, -53, top_c, z, PPIL)
        g.set(-53, top_c + 1, z, SEA if z > 0 else AME)
        g.set(-49, 0, z, SEA)

    # 3 Treasure Chests in the Ruined Library (all adjacent to the clear central aisle X=-51..-45)
    g.box(-55, 0, -16, -45, 0, 16, PUR)
    g.box(-52, 1, -16, -45, 6, 16, 'air')
    g.add_chest(-52, 1, -14, 'east', 0)
    g.add_chest(-52, 1, 0, 'east', 1)
    g.add_chest(-52, 1, 14, 'east', 1)

    # East Wing — Crystal Reliquary & Astrolabe Conservatory (X = 38..62, Z = -20..20)
    con_outer = g.rect(38, -20, 62, 20) & (R > COURT_R + 1.0)
    g.extrude(con_outer, 0, 0, ESB)
    g.extrude(con_outer, 1, 17, ESB)
    g.extrude(g.rect(39, -19, 61, 19) & (R > COURT_R + 1.5), 17, 18, PUR)
    con_inner = g.rect(40, -17, 59, 17) & (R > COURT_R + 2.0)
    g.extrude(con_inner, 0, 0, DT)
    g.extrude(con_inner, 1, 15, 'air')
    # Connect Conservatory to Ring Cloister and Boss Court via East Portal
    east_portal = g.rect(35, -4, 59, 4)
    g.extrude(east_portal, 0, 0, PUR)
    g.extrude(east_portal, 1, 7, 'air')
    # Suspended crystal astrolabe rings in the upper half of the Conservatory (y=8..14, leaving y=1..7 walkable)
    astro_c = np.hypot(X - 50.0, Z)
    g.extrude((np.abs(astro_c - 6.5) < 0.8), 10, 11, AME)
    g.extrude((np.abs(astro_c - 3.5) < 0.8), 11, 12, SEA)
    g.box(49, 8, -1, 51, 14, 1, MGLASS)
    g.set(50, 9, 0, SEA)
    for z in (-12, -6, 0, 6, 12):
        g.set(49, 0, z, SEA)
        g.box(55, 1, z, 55, 14, z, PPIL)
        g.set(54, 6, z, SEA)
    # 3 Treasure Chests in the East Conservatory
    g.box(45, 1, -15, 53, 6, 15, 'air')
    g.add_chest(52, 1, -13, 'west', 0)
    g.add_chest(52, 1, 0, 'west', 0)
    g.add_chest(52, 1, 13, 'west', 1)

    # North Sovereign Sanctum (Z = -60..-37, X = -16..16) under the tallest crystal spire
    sanct_outer = g.rect(-16, -60, 16, -37) & (R > COURT_R + 0.5)
    g.extrude(sanct_outer, 0, 0, ESB)
    g.extrude(sanct_outer, 1, 18, ESB)
    sanct_inner = g.rect(-12, -56, 12, -39) & (R > COURT_R + 1.0)
    g.extrude(sanct_inner, 0, 0, PUR)
    g.extrude(sanct_inner, 1, 14, 'air')
    north_portal = g.rect(-4, -55, 4, -35)
    g.extrude(north_portal, 0, 0, PUR)
    g.extrude(north_portal, 1, 9, 'air')
    for sx in (-8, 0, 8):
        for sz in (-52, -46, -42):
            g.set(sx, 0, sz, SEA)
            g.set(sx, 14, sz, SEA)
    # Crystal throne dais & 2 Royal Treasure Chests in the North Sanctum
    g.box(-4, 1, -55, 4, 1, -52, CHB)
    g.box(-2, 2, -55, 2, 7, -54, AME)
    g.box(-1, 3, -54, 1, 6, -54, SEA)
    g.add_chest(-6, 1, -51, 'south', 0)
    g.add_chest(6, 1, -51, 'south', 0)

    # =============== 5. South Gatehouse, Upper Staircases & Processional Avenue (+Z) ===============
    # Gatehouse mass: X in -18..18, Z in 37..54
    gh_outer = g.rect(-18, 37, 18, 54) & (R > COURT_R)
    g.extrude(gh_outer, 0, 0, ESB)
    g.extrude(gh_outer, 1, 20, ESB)
    g.extrude(gh_outer, 20, 21, PUR)
    gh_inner = g.rect(-13, 39, 13, 51) & (R > COURT_R + 0.5)
    g.extrude(gh_inner, 0, 0, ESB)
    g.extrude(gh_inner, 1, 15, 'air')

    # Connect Gatehouse to East & West Ring Cloisters
    for side in (-1, 1):
        px0, px1 = min(side * 10, side * 18), max(side * 10, side * 18)
        g.box(px0, 0, 39, px1, 0, 46, PUR)
        g.box(px0, 1, 39, px1, 8, 46, 'air')

    # Walkable staircases in the Gatehouse rising from y=1 (Z=50) to Upper Observatory Walk y=11 (Z=40)
    for side in (-1, 1):
        sx0, sx1 = min(side * 10, side * 12), max(side * 10, side * 12)
        g.box(sx0, 1, 38, sx1, 17, 51, 'air')
        for step_i in range(10):
            z = 50 - step_i
            y_step = 1 + step_i
            g.box(sx0, 0, z, sx1, y_step - 1, z, ESB)
            g.box(sx0, y_step, z, sx1, y_step, z, stair('purpur', 'north'))
        g.box(sx0, 9, 38, sx1, 10, 40, PUR)
        g.box(sx0, 11, 38, sx1, 17, 40, 'air')
        # Upper Observatory Chests on the SE & SW overlooks
        cx, cz = side * 15, 41
        g.box(cx - 2, 10, cz - 2, cx + 2, 10, cz + 2, PUR)
        g.box(cx - 2, 11, cz - 2, cx + 2, 17, cz + 2, 'air')
        g.set(cx, 10, cz - 1, SEA)
        g.add_chest(cx, 11, cz, 'west' if side > 0 else 'east', 0 if side > 0 else 1)

    # Grand Astral Gate from the Gatehouse into the Open-Sky Boss Court (X=-4..4, Z=35..40, height 13)
    gate = (np.abs(X) <= 4) & (Z >= 35) & (Z <= 40)
    g.extrude(gate, 0, 0, PUR)
    g.extrude(gate, 1, 13, 'air')
    for sx in (-5, 5):
        g.box(sx, 1, 37, sx, 14, 40, PPIL)
    g.box(-5, 14, 37, 5, 15, 40, PUR)
    g.box(-3, 15, 37, 3, 16, 39, AME)
    g.box(-1, 15, 37, 1, 15, 39, SEA)

    # Processional Causeway from the +Z Mouth (Z=82) to the Gatehouse (Z=52)
    causeway = (np.abs(X) <= 10) & (Z >= 51) & (Z <= 83)
    g.extrude(causeway, -5, -1, ES)
    g.extrude(causeway, 0, 0, ESB)
    g.extrude((np.abs(X) <= 3) & (Z >= 36) & (Z <= 83), 0, 0, PUR)
    g.extrude((np.abs(X) == 0) & (Z >= 36) & (Z <= 82) & (Z % 4 == 0), 0, 0, SEA)
    # Clear walkable avenue from Z=82 straight through the Gatehouse into the Boss Court
    g.extrude((np.abs(X) <= 5) & (Z >= 36) & (Z <= 83), 1, 12, 'air')

    # Processional Crystal Colonnade along Z=54..82
    for z in range(56, 83, 5):
        for sx in (-8, 8):
            g.box(sx - 1, 0, z - 1, sx + 1, 1, z + 1, CHB)
            g.box(sx, 2, z, sx, 10, z, PPIL)
            g.box(sx - 1, 11, z - 1, sx + 1, 12, z + 1, AME)
            g.set(sx, 13, z, SEA)
            g.set(sx, 14, z, ENDROD_UP)
        # Broken/intact transverse purpur arches high over the avenue (y=12..14)
        if z in (61, 71, 81):
            g.box(-8, 12, z, 8, 13, z, PUR)
            g.set(0, 12, z, SEA)

    # =============== 6. Open-Sky Ritual Boss Court (R <= 36, 73 blocks across, clear y=1..72) ===============
    court = R <= COURT_R
    g.extrude(court, 0, 0, ESB)
    cf = hash2(X, Z, 91)
    g.extrude(court & (cf > 0.88), 0, 0, ES)
    g.extrude(court & (cf < 0.10), 0, 0, PBB)

    # Astral Star-Compass Floor Mosaic
    # 8-pointed star rays & concentric ritual circles
    ray8 = (np.abs(((TH + 22.5) % 45.0) - 22.5) < 1.35) & (R > 4.0) & (R < 34.5)
    g.extrude(ray8, 0, 0, PUR)
    for r0, r1, m in (
        (34.2, 36.1, PUR),
        (32.8, 34.2, CHB),
        (25.0, 26.6, PUR),
        (23.6, 25.0, AME),
        (15.5, 17.0, PUR),
        (14.2, 15.5, CRY),
        (7.2, 8.6, AME),
        (5.8, 7.2, CHB),
        (2.2, 3.8, CRY),
    ):
        g.extrude((R >= r0) & (R < r1), 0, 0, m)
    g.extrude(R < 2.2, 0, 0, SEA)

    # 8 diamond star-petals inlaid between R=16 and R=24
    star_petal = (
        (np.abs((TH % 45.0) - 22.5) < 5.5)
        & (R >= 16.5)
        & (R <= 23.5)
        & (np.abs((TH % 45.0) - 22.5) < (23.5 - R) * 0.8)
    )
    g.extrude(star_petal, 0, 0, AME)

    # Flush sea_lantern star points across the entire 73-block court
    for k in range(16):
        a = math.radians(22.5 * k)
        for rr_ in (6.5, 11.5, 16.2, 24.3, 30.0, 34.8):
            cx, cz = round(math.cos(a) * rr_), round(math.sin(a) * rr_)
            if math.hypot(cx, cz) <= COURT_R:
                g.set(cx, 0, cz, SEA)

    # Guarantee 100% unobstructed open-sky cylinder above the boss court (R <= 36, y=1..72)
    g.extrude(court, 1, ARENA_H, 'air')

    return g


META = {
    'arena': {'x': 0, 'y': 0, 'z': 0, 'radius': COURT_R, 'height': ARENA_H},
    'landing': {'x': 0, 'y': 1, 'z': 45},
    'mouth': {'x': 0, 'y': 1, 'z': 82, 'dir': 'south'},
    'gate': {'x': 0, 'y': 1, 'z': 37, 'width': 9, 'height': 13, 'dir': 'south'},
}

VIEWS = {
    'exterior': dict(
        cam=(68, 46, 76),
        target=(-6, 10, -6),
        fov=74,
        size=(1600, 900),
        ambient=0.42,
        gamma=0.80,
        light_boost=0.10,
        exposure=1.18,
        fog=(0.0011, (18, 11, 28)),
        sky=(18, 11, 28),
        title='THE END  /  ABANDONED CRYSTAL CITY — AERIAL OVERVIEW',
        subtitle='End-stone & purpur citadel, asymmetric amethyst spires, stepped island base and open-sky ritual court',
    ),
    'approach': dict(
        cam=(0, 6, 78),
        target=(0, 14, 30),
        fov=76,
        size=(1600, 900),
        ambient=0.40,
        gamma=0.80,
        light_boost=0.10,
        exposure=1.18,
        fog=(0.0012, (18, 11, 28)),
        sky=(18, 11, 28),
        title='THE END  /  CRYSTAL CITY — PROCESSIONAL AVENUE & GATEHOUSE',
        subtitle='Walkable entrance avenue flanked by crystal-crowned purpur colonnades leading to the Astral Gate',
    ),
    'library': dict(
        cam=(-47, 6, 15),
        target=(-50, 7, -15),
        fov=80,
        size=(1600, 900),
        ambient=0.42,
        gamma=0.80,
        light_boost=0.10,
        exposure=1.18,
        fog=(0.0014, (18, 11, 28)),
        sky=(18, 11, 28),
        title='THE END  /  CRYSTAL CITY — RUINED GRAND LIBRARY',
        subtitle='Half-collapsed purpur vault open to the End sky, shattered columns, study alcoves and treasure caches',
    ),
    'court': dict(
        cam=(0, 7, 33),
        target=(0, 18, -28),
        fov=82,
        size=(1600, 900),
        ambient=0.42,
        gamma=0.80,
        light_boost=0.10,
        exposure=1.18,
        fog=(0.0011, (18, 11, 28)),
        sky=(18, 11, 28),
        title='THE END  /  CRYSTAL CITY — OPEN-SKY RITUAL BOSS COURT (73-BLOCK ARENA)',
        subtitle='Astral star-compass mosaic, two-tier arcaded amphitheatre rim and asymmetric crystal spires',
    ),
}


if __name__ == '__main__':
    build()
