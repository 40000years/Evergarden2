"""Void Ring Sanctuary (End): วิหารวงแหวนสุญญากาศ (`void_ring`).

An engineered satellite End-island complex anchored to a natural outer End island edge on +Z:
  - Inverted astrolabe & stalactite under-cone (y=-1..-35) with obsidian/end-stone ribs, arched
    flying sub-ribs, and glowing void-core energy rings.
  - Central Boss Court (radius 35, clear height 72) paved with an eclipse/astrolabe mandala at y=0.
  - Recessed void-trench (R=36..43) with suspended glowing conduit rings below, crossed by 4 broad
    cardinal bridges (South, North, East, West).
  - Outer Broken Ring Sanctuary (R=44..60) with vaulted ring cloisters, shattered intercardinal roof
    breaches bridged by intact purpur/end-stone steps, South Gatehouse Narthex, East/West Astrolabe
    Pavilions, North Void Reliquary / Astral Sanctum, and 4 Diagonal Monolith Vaults (10 chests total).
  - 12 towering monoliths / floating-look pylons (y=48..68) piercing giant tilted overhead broken
    halo rings (strictly outside R > 35.5).
"""
from __future__ import annotations

import math
import numpy as np

from .grid import Grid, hash2, hash3, fbm

# ---- materials (valid 26.3 states) ----
ES = 'end_stone'
ESB = 'end_stone_bricks'
PUR = 'purpur_block'
PURP = 'purpur_pillar[axis=y]'
OBS = 'obsidian'
CRY = 'crying_obsidian'
AME = 'amethyst_block'
DT = 'deepslate_tiles'
DB = 'deepslate_bricks'
CD = 'chiseled_deepslate'
PBB = 'polished_blackstone_bricks'
CPBB = 'cracked_polished_blackstone_bricks'
CHB = 'chiseled_polished_blackstone'
PBS = 'polished_blackstone'
SEA = 'sea_lantern'
FROG = 'pearlescent_froglight[axis=y]'
EROD_U = 'end_rod[facing=up]'
EROD_D = 'end_rod[facing=down]'
SLANTH = 'soul_lantern[hanging=true,waterlogged=false]'
CHAIN = 'iron_chain[axis=y,waterlogged=false]'
PGLASS = 'purple_stained_glass'
MGLASS = 'magenta_stained_glass'


def stair(mat: str, facing: str, half: str = 'bottom') -> str:
    return f'{mat}_stairs[facing={facing},half={half},shape=straight,waterlogged=false]'


def slab(mat: str, kind: str = 'bottom') -> str:
    return f'{mat}_slab[type={kind},waterlogged=false]'


def wall(mat: str) -> str:
    return f'{mat}_wall[east=none,north=none,south=none,up=true,waterlogged=false,west=none]'


X0, X1, Y0, Y1, Z0, Z1 = -82, 82, -36, 82, -82, 92
COURT_R = 35

META = {
    'arena': {'x': 0, 'y': 0, 'z': 0, 'radius': 35, 'height': 72},
    'landing': {'x': 0, 'y': 1, 'z': 78},
    'mouth': {'x': 0, 'y': 1, 'z': 84, 'dir': 'south'},
    'gate': {'x': 0, 'y': 1, 'z': 44, 'width': 9, 'height': 14, 'dir': 'south'},
}


def build() -> Grid:
    g = Grid(X0, X1, Y0, Y1, Z0, Z1)
    X, Z, R, TH = g.X, g.Z, g.R, g.TH
    TH_RAD = np.radians(TH)
    Yv = np.arange(Y0, Y1 + 1)[None, :, None]
    X3 = X[:, None, :] * np.ones_like(Yv)
    Z3 = Z[:, None, :] * np.ones_like(Yv)
    Y3 = np.ones_like(X[:, None, :]) * Yv

    # =============== 0. Carve open void/sky envelope ===============
    sanct_env = (R <= 75.0) | ((np.abs(X) <= 24) & (Z >= 48) & (Z <= 89))
    g.extrude(sanct_env, 1, Y1, 'air')
    void_under = (R <= 74.0) | ((np.abs(X) <= 22) & (Z >= 48) & (Z <= 74))
    g.extrude(void_under, Y0, 0, 'air')

    # =============== 1. Engineered Satellite Island & Under-Cone (y=-1..-35) ===============
    n_under = fbm(X, Z, 7.5, seed=11)
    n_fine = hash3(X3, Y3, Z3, seed=19)

    # 1a. Central Court Under-Cone (y = -1 down to -35)
    t_cone = np.clip((Yv + 35.0) / 34.0, 0.0, 1.0)
    flute12 = np.cos(12.0 * TH_RAD)[:, None, :]
    flute4 = np.cos(4.0 * TH_RAD)[:, None, :]
    r_cone = (36.2 * (t_cone ** 0.68)
              + 2.2 * flute12 * t_cone * (1.0 - 0.25 * t_cone)
              + 1.4 * flute4 * (t_cone ** 0.5)
              + (n_under[:, None, :] - 0.5) * 2.2 * t_cone)
    cone_mask = (Y3 <= -1) & (Y3 >= -35) & (R[:, None, :] <= r_cone)
    cone_shell = cone_mask & (R[:, None, :] >= r_cone - 3.2)

    g.where3(cone_mask, ES)
    g.where3(cone_shell & (n_fine < 0.45), ESB)
    g.where3(cone_shell & (n_fine >= 0.45) & (n_fine < 0.75), DT)
    g.where3(cone_shell & (n_fine >= 0.75), OBS)

    # 12 radial obsidian & amethyst rib-fins on the central under-cone
    rib12 = (np.abs(((TH + 15.0) % 30.0) - 15.0) <= 2.4)[:, None, :]
    rib_fin = (Y3 <= -2) & (Y3 >= -33) & rib12 & (R[:, None, :] <= r_cone + 2.2) & (R[:, None, :] >= r_cone - 1.2)
    g.where3(rib_fin, OBS)
    rib_edge = (Y3 <= -3) & (Y3 >= -31) & (np.abs(((TH + 15.0) % 30.0) - 15.0) <= 0.9)[:, None, :] & (R[:, None, :] <= r_cone + 2.5) & (R[:, None, :] >= r_cone + 0.6)
    g.where3(rib_edge, CRY)
    g.where3(rib_edge & ((Y3 % 4) == 0), SEA)
    g.where3(rib_edge & ((Y3 % 4) == -2), AME)

    # Concentric carved astrolabe rings on the central under-cone
    for yc, r_in, r_out, mat_frame, mat_glow in (
        (-4, 32.5, 36.4, DT, SEA),
        (-10, 26.0, 30.5, OBS, AME),
        (-17, 19.0, 23.8, DT, SEA),
        (-24, 11.5, 16.5, OBS, CRY),
        (-30, 5.0, 9.8, OBS, SEA),
    ):
        band = (R >= r_in) & (R <= r_out)
        g.extrude(band, yc - 1, yc + 1, mat_frame)
        glow_band = (R >= r_out - 1.2) & (R <= r_out)
        g.extrude(glow_band, yc, yc, mat_glow)
        for k in range(24):
            a = math.radians(k * 15.0)
            gx, gz = round(math.cos(a) * (r_out - 0.3)), round(math.sin(a) * (r_out - 0.3))
            g.set(gx, yc, gz, SEA if k % 2 == 0 else AME)

    # 8 sweeping flying-buttress under-ribs connecting the outer ring hull (R=50, y=-12) to the lower central cone (R=15, y=-26)
    for k in range(8):
        a = math.radians(k * 45.0)
        for s in np.linspace(0.0, 1.0, 28):
            rr = 50.0 * (1.0 - s) + 14.0 * s
            yy = round(-11.0 * (1.0 - s) - 27.0 * s - 3.5 * math.sin(s * math.pi))
            rx, rz = round(math.cos(a) * rr), round(math.sin(a) * rr)
            g.box(rx - 1, yy - 1, rz - 1, rx + 1, yy + 1, rz + 1, OBS if int(s * 10) % 2 == 0 else DT)
            g.set(rx, yy - 1, rz, SEA if int(s * 12) % 3 == 0 else AME)

    # Inverted Void-Core Singularity at the bottom tip (0, -36..-31, 0)
    g.extrude(R <= 5.0, -33, -31, OBS)
    g.extrude(R <= 3.5, -34, -32, AME)
    g.extrude(R <= 2.2, -35, -33, SEA)
    for dx, dz in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
        g.set(dx, -36, dz, EROD_D)

    # 1b. Outer Ring Under-Hull (R = 43.2..60.0, y = -1..-16 with arched cutouts revealing the central cone)
    r_mid = 51.0
    half_w = 7.6
    rib_wave = 0.5 + 0.5 * np.cos(12.0 * TH_RAD)
    hull_depth = 7.0 + 9.0 * (rib_wave ** 1.5) + (n_under - 0.5) * 2.2
    t_hull = np.clip((Yv + hull_depth[:, None, :]) / np.maximum(hull_depth[:, None, :], 1.0), 0.0, 1.0)
    w_hull = half_w * (t_hull ** 0.62) + 0.8 * np.cos(24.0 * TH_RAD)[:, None, :] * t_hull
    hull_mask = (Y3 <= -1) & (Y3 >= -18) & (np.abs(R[:, None, :] - r_mid) <= w_hull)
    g.where3(hull_mask, ES)
    hull_skin = hull_mask & (np.abs(R[:, None, :] - r_mid) >= w_hull - 2.2)
    g.where3(hull_skin & (n_fine < 0.50), ESB)
    g.where3(hull_skin & (n_fine >= 0.50) & (n_fine < 0.80), DT)
    g.where3(hull_skin & (n_fine >= 0.80), OBS)

    # Glowing under-ring conduit bands on the outer hull
    for yc, rw in ((-4, 7.2), (-8, 5.6), (-12, 3.8)):
        c_band = (np.abs(np.abs(R - r_mid) - rw) <= 0.9) & (hull_depth >= abs(yc) - 1)
        g.extrude(c_band, yc - 1, yc + 1, OBS)
        g.extrude(c_band, yc, yc, CRY)
        g.extrude(c_band & ((np.round(TH) % 10) < 2), yc, yc, SEA)

    # 1c. Chasm Energy Conduit Rings & Sub-Bridge Arches (R = 35.8..43.8)
    for yc, r0, r1 in ((-7, 38.4, 41.0), (-15, 38.8, 40.6)):
        ring_m = (R >= r0) & (R <= r1)
        g.extrude(ring_m, yc - 1, yc + 1, OBS)
        core_m = (R >= r0 + 0.6) & (R <= r1 - 0.6)
        g.extrude(core_m, yc - 1, yc + 1, AME)
        g.extrude(ring_m & (np.abs(R - 0.5 * (r0 + r1)) <= 0.7), yc + 1, yc + 1, SEA)
        g.extrude(ring_m & (np.abs(R - 0.5 * (r0 + r1)) <= 0.7), yc - 1, yc - 1, SEA)
        strut = (np.abs(((TH + 15.0) % 30.0) - 15.0) <= 1.8) & (R >= 33.0) & (R <= 45.5)
        g.extrude(strut, yc - 1, yc + 1, DT)
        g.extrude(strut & (np.abs(((TH + 15.0) % 30.0) - 15.0) <= 0.7), yc, yc, SEA)

    # Trench cliff lining at R=35.2..36.2 (court edge) and R=43.2..44.5 (outer ring inner edge)
    court_cliff = (R >= 35.0) & (R <= 36.2)
    g.extrude(court_cliff, -6, 0, DT)
    g.extrude((R >= 35.6) & (R <= 36.2), -3, -3, SEA)
    g.extrude((R >= 35.6) & (R <= 36.2), -5, -5, CRY)

    ring_cliff = (R >= 43.2) & (R <= 44.5)
    g.extrude(ring_cliff, -6, 0, DT)
    g.extrude((R >= 43.2) & (R <= 43.8), -3, -3, SEA)
    g.extrude((R >= 43.2) & (R <= 43.8), -5, -5, AME)

    # =============== 2. Boss Court Astrolabe Mandala Floor (y=0, R<=36.0) ===============
    court = R <= 36.0
    g.extrude(court, -2, -1, OBS)
    g.extrude(court, 0, 0, DT)
    cf = hash2(X, Z, seed=31)
    g.extrude(court & (cf < 0.28), 0, 0, PBB)
    g.extrude(court & (cf >= 0.28) & (cf < 0.45), 0, 0, OBS)
    g.extrude(court & (cf >= 0.92), 0, 0, CRY)

    # Eclipse crescent inlay
    R_off = np.hypot(X - 3.5, Z + 2.5)
    crescent = (R >= 16.2) & (R <= 24.4) & (R_off >= 10.5) & (R_off <= 21.8)
    g.extrude(crescent, 0, 0, ESB)
    crescent_inner = (R >= 17.2) & (R <= 23.4) & (R_off >= 12.0) & (R_off <= 20.4)
    g.extrude(crescent_inner, 0, 0, PUR)

    # 12 primary armillary spokes + cardinal axes
    spoke12 = (np.abs(((TH + 15.0) % 30.0) - 15.0) < 1.15) & (R >= 5.0) & (R <= 34.5)
    g.extrude(spoke12, 0, 0, ESB)
    spoke12_core = (np.abs(((TH + 15.0) % 30.0) - 15.0) < 0.55) & (R >= 6.0) & (R <= 33.5)
    g.extrude(spoke12_core, 0, 0, PUR)
    spoke_card = (np.abs((TH % 90.0) - 0.0) < 1.8) | (np.abs((TH % 90.0) - 90.0) < 1.8)
    g.extrude(spoke_card & (R >= 4.5) & (R <= 34.8), 0, 0, ESB)
    g.extrude(((np.abs(X) <= 1) | (np.abs(Z) <= 1)) & (R >= 4.5) & (R <= 34.8), 0, 0, AME)

    # 8-pointed inner eclipse star (R = 5..15)
    d8 = np.abs(((TH + 22.5) % 45.0) - 22.5)
    star8 = (R >= 5.2) & (R <= 14.5) & (d8 <= (14.5 - R) * 1.15)
    g.extrude(star8, 0, 0, ESB)
    star8_in = (R >= 6.0) & (R <= 13.0) & (d8 <= (13.0 - R) * 0.85)
    g.extrude(star8_in, 0, 0, AME)

    # 16-pointed outer astrolabe star (R = 26..32.5)
    d16 = np.abs(((TH + 11.25) % 22.5) - 11.25)
    star16 = (R >= 26.2) & (R <= 32.4) & (d16 <= (32.4 - R) * 1.1)
    g.extrude(star16, 0, 0, ESB)
    star16_in = (R >= 26.8) & (R <= 31.2) & (d16 <= (31.2 - R) * 0.75)
    g.extrude(star16_in, 0, 0, PUR)

    # Concentric astrolabe rings on y=0
    for r0, r1, mat in (
        (0.0, 2.2, SEA),
        (2.2, 3.6, CRY),
        (3.6, 5.0, AME),
        (5.0, 6.2, CHB),
        (14.2, 15.4, ESB),
        (15.4, 16.4, PURP),
        (24.2, 25.3, CHB),
        (25.3, 26.4, AME),
        (32.4, 33.6, PUR),
        (33.6, 34.8, ESB),
        (34.8, 36.2, CHB),
    ):
        g.extrude((R >= r0) & (R < r1), 0, 0, mat)

    # Flush star-point lights across the court floor (y=0)
    for k in range(8):
        a = math.radians(k * 45.0)
        for rr in (9.5, 13.2):
            g.set(round(math.cos(a) * rr), 0, round(math.sin(a) * rr), SEA)
    for k in range(24):
        a = math.radians(k * 15.0)
        for rr in (15.8, 25.8):
            g.set(round(math.cos(a) * rr), 0, round(math.sin(a) * rr), SEA if k % 2 == 0 else FROG)
    for k in range(36):
        a = math.radians(k * 10.0)
        g.set(round(math.cos(a) * 33.8), 0, round(math.sin(a) * 33.8), SEA)

    # Court rim bollards strictly outside R > 35.0 (at R = 35.8, skipping 4 bridge mouths)
    for k in range(24):
        deg = k * 15.0
        if any(abs(((deg - c + 180.0) % 360.0) - 180.0) < 12.0 for c in (0.0, 90.0, 180.0, 270.0)):
            continue
        a = math.radians(deg)
        bx, bz = round(math.cos(a) * 35.8), round(math.sin(a) * 35.8)
        if math.hypot(bx, bz) <= 35.1:
            continue
        g.set(bx, 1, bz, CHB)
        g.set(bx, 2, bz, SEA if k % 2 == 0 else AME)
        g.set(bx, 3, bz, EROD_U)

    # =============== 3. Outer Broken Ring Sanctuary (R = 43.5..60.0) ===============
    ring_base = (R >= 43.5) & (R <= 59.5)
    g.extrude(ring_base, -2, -1, DT)
    g.extrude(ring_base, 0, 0, ESB)

    # Ornate ring cloister floor pattern (R = 45.5..54.5, y = 0)
    g.extrude((R >= 46.2) & (R <= 53.8), 0, 0, DT)
    g.extrude((R >= 48.0) & (R <= 52.0), 0, 0, ESB)
    g.extrude((R >= 49.0) & (R <= 51.0), 0, 0, PUR)
    g.extrude((np.abs(R - 47.2) <= 0.55) | (np.abs(R - 52.8) <= 0.55), 0, 0, AME)
    g.extrude((np.abs(R - 50.0) <= 0.45), 0, 0, CRY)
    for k in range(72):
        a = math.radians(k * 5.0)
        g.set(round(math.cos(a) * 50.0), 0, round(math.sin(a) * 50.0), SEA if k % 2 == 0 else AME)

    # 3b. Vaulted Ring Cloister Shell (y = 1..20) with 4 Shattered Intercardinal Sectors
    shatter_centres = (24.0, 156.0, 204.0, 336.0)
    d_shatter = np.min(np.stack([np.abs(((TH - c + 180.0) % 360.0) - 180.0) for c in shatter_centres], axis=0), axis=0)

    inner_wall = (R >= 44.0) & (R <= 46.2)
    outer_wall = (R >= 53.8) & (R <= 56.2)
    ring_shell = (R >= 44.0) & (R <= 56.2)

    shatter_h = np.clip(2.0 + (d_shatter - 2.5) * 3.5 + (fbm(X, Z, 4.0, 41) - 0.5) * 3.0, 1.0, 19.0)
    wall_h = np.where(d_shatter < 7.5, shatter_h, 19.0)

    wall3_in = inner_wall[:, None, :] & (Y3 >= 1) & (Y3 <= wall_h[:, None, :])
    wall3_out = outer_wall[:, None, :] & (Y3 >= 1) & (Y3 <= wall_h[:, None, :])
    g.where3(wall3_in | wall3_out, ESB)
    g.where3((wall3_in | wall3_out) & (n_fine > 0.68), DT)
    g.where3((wall3_in | wall3_out) & (n_fine < 0.16), PBB)

    for yb, mat in ((5, PUR), (6, CHB), (11, AME), (12, SEA), (16, DT), (18, CHB)):
        g.where3((wall3_in | wall3_out) & (Y3 == yb), mat)

    # Pointed vault roof over intact cloister sections (y = 13..19 across R = 44.5..55.5)
    dist_mid = np.abs(R - 50.0)[:, None, :]
    vault_y_in = 16.5 - 0.65 * dist_mid
    vault_y_out = 19.5 - 0.45 * dist_mid
    vault3 = (ring_shell[:, None, :] & (d_shatter[:, None, :] >= 6.2)
              & (Y3 >= vault_y_in) & (Y3 <= vault_y_out))
    g.where3(vault3, DT)
    g.where3(vault3 & (np.abs(Y3 - vault_y_in) <= 0.9), ESB)
    rib_vault = (np.abs(((TH + 5.0) % 10.0) - 5.0) <= 0.9)[:, None, :]
    g.where3(vault3 & rib_vault & (np.abs(Y3 - vault_y_in) <= 1.1), PUR)
    spine3 = (np.abs(R - 50.0) <= 0.8)[:, None, :] & (d_shatter[:, None, :] >= 6.5) & (Y3 == 16)
    g.where3(spine3, SEA)
    spine_frame3 = (np.abs(R - 50.0) <= 1.8)[:, None, :] & (np.abs(R - 50.0) > 0.8)[:, None, :] & (d_shatter[:, None, :] >= 6.5) & (Y3 == 16)
    g.where3(spine_frame3, AME)

    # Clear cloister interior (R = 46.2..53.8, y = 1..15)
    cloister_air = ((R >= 46.2) & (R <= 53.8))[:, None, :] & (Y3 >= 1) & (Y3 < vault_y_in)
    g.where3(cloister_air, 'air')

    # Carve tall pointed arches through inner & outer walls every 10 degrees
    for k in range(36):
        deg = k * 10.0 + 5.0
        w_arch = g.wedge(deg, 2.9) & ((R >= 43.5) & (R <= 56.8))
        g.extrude(w_arch, 2, 9, 'air')
        g.extrude(g.wedge(deg, 2.1) & (R >= 43.5) & (R <= 56.8), 10, 11, 'air')
        g.extrude(g.wedge(deg, 1.2) & (R >= 43.5) & (R <= 56.8), 12, 13, 'air')
        sill_in = g.wedge(deg, 2.9) & (R >= 44.3) & (R <= 45.7)
        sill_out = g.wedge(deg, 2.9) & (R >= 54.3) & (R <= 55.7)
        g.extrude(sill_in | sill_out, 1, 1, CHB)

    # Interior colonnade piers & vault ribs every 10 degrees along R=46.2 and R=53.8
    for k in range(36):
        deg = k * 10.0
        if any(abs(((deg - c + 180.0) % 360.0) - 180.0) < 6.0 for c in shatter_centres):
            continue
        a = math.radians(deg)
        for rr in (46.2, 53.8):
            px, pz = round(math.cos(a) * rr), round(math.sin(a) * rr)
            g.box(px, 1, pz, px, 1, pz, CHB)
            g.box(px, 2, pz, px, 10, pz, PURP)
            g.box(px, 11, pz, px, 11, pz, SEA)
            g.box(px, 12, pz, px, 13, pz, AME)
        lx, lz = round(math.cos(a) * 50.0), round(math.sin(a) * 50.0)
        g.box(lx, 14, lz, lx, 15, lz, CHAIN)
        g.set(lx, 13, lz, SLANTH)

    # Shattered Breach Details: jagged obsidian/amethyst fracture edges & intact purpur step-bridges
    for c_deg in shatter_centres:
        rift_in = g.wedge(c_deg, 3.8) & (R >= 44.0) & (R < 46.8)
        rift_out = g.wedge(c_deg, 3.8) & (R > 53.2) & (R <= 57.5)
        g.extrude(rift_in | rift_out, -2, 0, CRY)
        g.extrude((rift_in | rift_out) & g.wedge(c_deg, 2.2), -2, -2, SEA)
        g.extrude((rift_in | rift_out) & g.wedge(c_deg, 2.2), -1, 4, 'air')
        bridge_deck = g.wedge(c_deg, 5.5) & (R >= 46.8) & (R <= 53.2)
        g.extrude(bridge_deck, -2, -1, OBS)
        g.extrude(bridge_deck, 0, 0, PUR)
        g.extrude(bridge_deck & (np.abs(R - 50.0) <= 1.2), 0, 0, ESB)
        g.extrude(bridge_deck & (np.abs(R - 50.0) <= 0.4), 0, 0, SEA)
        parapet_b = g.wedge(c_deg, 4.5) & ((np.abs(R - 47.0) <= 0.5) | (np.abs(R - 53.0) <= 0.5))
        g.extrude(parapet_b, 1, 1, CHB)
        lip = (np.abs(np.abs(((TH - c_deg + 180.0) % 360.0) - 180.0) - 5.8) <= 1.0) & (
            ((R >= 44.0) & (R <= 46.2)) | ((R >= 53.8) & (R <= 56.2))
        )
        g.extrude(lip, 2, 7, OBS)
        g.extrude(lip, 8, 10, AME)
        g.extrude(lip, 11, 11, SEA)

    # Crown crenellations & astral spires along intact sections of the ring wall (y = 19..24)
    for k in range(36):
        deg = k * 10.0
        if any(abs(((deg - c + 180.0) % 360.0) - 180.0) < 7.5 for c in shatter_centres):
            continue
        a = math.radians(deg)
        for rr in (44.8, 55.2):
            px, pz = round(math.cos(a) * rr), round(math.sin(a) * rr)
            g.box(px - 1, 19, pz - 1, px + 1, 20, pz + 1, DT)
            g.box(px, 21, pz, px, 22, pz, PURP)
            g.set(px, 23, pz, SEA if k % 2 == 0 else AME)
            g.set(px, 24, pz, EROD_U)

    # =============== 4. Four Cardinal Bridges Across the Void-Trench (R = 35..46) ===============
    # 4a. South Main Gate Bridge (x = -5..5, z = 35..46)
    g.box(-5, -2, 35, 5, -1, 46, OBS)
    g.box(-5, 0, 35, 5, 0, 46, ESB)
    g.box(-3, 0, 35, 3, 0, 46, PUR)
    g.box(-1, 0, 35, 1, 0, 46, AME)
    for z in range(36, 46, 2):
        g.set(0, 0, z, SEA)
    g.box(-4, 1, 35, 4, 14, 46, 'air')
    for sx in (-5, 5):
        g.box(sx, 1, 36, sx, 1, 44, CHB)
        g.box(sx, 2, 36, sx, 2, 44, wall('deepslate_tile'))
        for zp in (37, 42):
            g.box(sx, 1, zp, sx, 4, zp, PURP)
            g.set(sx, 5, zp, SEA)
            g.set(sx, 6, zp, EROD_U)
    for z in range(36, 45):
        arch_d = int(9 - 0.35 * ((z - 40.0) ** 2))
        g.box(-5, -arch_d, z, 5, -1, z, DT)
        g.box(-2, -arch_d, z, 2, -arch_d + 1, z, CRY if z != 40 else SEA)

    # 4b. North Bridge (x = -4..4, z = -46..-35)
    g.box(-4, -2, -46, 4, -1, -35, OBS)
    g.box(-4, 0, -46, 4, 0, -35, ESB)
    g.box(-2, 0, -46, 2, 0, -35, PUR)
    g.box(-1, 0, -46, 1, 0, -35, AME)
    for z in range(-45, -35, 2):
        g.set(0, 0, z, SEA)
    g.box(-3, 1, -46, 3, 14, -35, 'air')
    for sx in (-4, 4):
        g.box(sx, 1, -44, sx, 1, -36, CHB)
        g.box(sx, 2, -44, sx, 2, -36, wall('deepslate_tile'))
        for zp in (-42, -37):
            g.box(sx, 1, zp, sx, 4, zp, PURP)
            g.set(sx, 5, zp, SEA)
            g.set(sx, 6, zp, EROD_U)
    for z in range(-44, -35):
        arch_d = int(8 - 0.35 * ((z + 40.0) ** 2))
        g.box(-4, -arch_d, z, 4, -1, z, DT)
        g.box(-1, -arch_d, z, 1, -arch_d + 1, z, SEA if z == -40 else AME)

    # 4c. East Bridge (x = 35..46, z = -4..4) & West Bridge (x = -46..-35, z = -4..4)
    for sgn in (-1, 1):
        xa, xb = (35, 46) if sgn == 1 else (-46, -35)
        g.box(xa, -2, -4, xb, -1, 4, OBS)
        g.box(xa, 0, -4, xb, 0, 4, ESB)
        g.box(xa, 0, -2, xb, 0, 2, PUR)
        g.box(xa, 0, -1, xb, 0, 1, AME)
        for x in range(min(xa, xb) + 1, max(xa, xb), 2):
            g.set(x, 0, 0, SEA)
        g.box(xa, 1, -3, xb, 12, 3, 'air')
        x_in, x_out = (36, 44) if sgn == 1 else (-44, -36)
        for sz in (-4, 4):
            g.box(x_in, 1, sz, x_out, 1, sz, CHB)
            g.box(x_in, 2, sz, x_out, 2, sz, wall('deepslate_tile'))
            for xp in (sgn * 37, sgn * 42):
                g.box(xp, 1, sz, xp, 4, sz, PURP)
                g.set(xp, 5, sz, SEA)
                g.set(xp, 6, sz, EROD_U)
        for x in range(x_in, x_out + 1):
            arch_d = int(8 - 0.35 * ((abs(x) - 40.0) ** 2))
            g.box(x, -arch_d, -4, x, -1, 4, DT)
            g.box(x, -arch_d, -1, x, -arch_d + 1, 1, SEA if abs(x) == 40 else AME)

    # =============== 5. Major Sanctuary Halls & Monolith Vaults ===============
    # 5a. South Gatehouse Narthex (x = -14..14, z = 43..57, rising to y = 28)
    narthex_ext = g.rect(-14, 43, 14, 57)
    g.extrude(narthex_ext, -3, -1, OBS)
    g.extrude(narthex_ext, 0, 0, ESB)
    for y in range(1, 27):
        w = max(4, 14 - max(0, (y - 16)))
        g.box(-w, y, 43, w, y, 57, ESB if y % 4 != 0 else DT)
    g.box(-14, 6, 43, 14, 7, 57, PUR)
    g.box(-14, 13, 43, 14, 14, 57, CHB)
    g.box(-12, 20, 43, 12, 20, 57, AME)
    for dy in range(-3, 4):
        for dx in range(-3, 4):
            if dx * dx + dy * dy <= 10:
                g.box(dx, 21 + dy, 43, dx, 21 + dy, 57, PGLASS if (dx * dx + dy * dy) > 2 else SEA)

    g.box(-10, 1, 46, 10, 15, 54, 'air')
    g.box(-7, 16, 46, 7, 17, 54, 'air')
    g.box(-9, 0, 46, 9, 0, 54, DT)
    g.box(-4, 0, 43, 4, 0, 57, PUR)
    g.box(-2, 0, 43, 2, 0, 57, ESB)
    g.box(0, 0, 43, 0, 0, 57, SEA)

    g.box(-4, 1, 42, 4, 12, 58, 'air')
    for dy, hw in ((13, 3), (14, 2), (15, 1)):
        g.box(-hw, dy, 42, hw, dy, 58, 'air')
    for zf in (43, 57):
        for sx in (-5, 5):
            g.box(sx, 1, zf, sx, 13, zf, CHB)
            g.box(sx - (1 if sx < 0 else -1), 1, zf, sx - (1 if sx < 0 else -1), 14, zf, PURP)
        for dy, hw in ((13, 4), (14, 3), (15, 2), (16, 1)):
            for sx in (-hw, hw):
                g.set(sx, dy, zf, AME if dy % 2 == 0 else CHB)
        g.set(0, 16, zf, SEA)

    for sgn in (-1, 1):
        g.box(sgn * 9, 1, 47, sgn * 15, 11, 53, 'air')
        g.box(sgn * 9, 12, 48, sgn * 15, 13, 52, 'air')
        g.box(sgn * 9, 0, 47, sgn * 15, 0, 53, PUR)
        g.box(sgn * 9, 0, 49, sgn * 15, 0, 51, ESB)

    for sx in (-7, 7):
        for zp in (47, 53):
            g.box(sx, 1, zp, sx, 1, zp, CHB)
            g.box(sx, 2, zp, sx, 13, zp, PURP)
            g.box(sx, 14, zp, sx, 15, zp, AME)
            g.set(sx, 8, zp, SEA)
    for sx in (-4, 4):
        g.box(sx, 15, 50, sx, 16, 50, CHAIN)
        g.set(sx, 14, 50, SLANTH)

    # 5b. North Void Reliquary / Astral Sanctum (x = -16..16, z = -68..-44)
    # Semicircular northern apse (Z <= -50) + flat monumental cathedral facade at Z = -44
    sanct_r = np.hypot(X, Z + 54.0)
    sanct_foot = ((Z <= -50) & (sanct_r <= 14.8)) | g.rect(-13, -54, 13, -44)
    g.extrude(sanct_foot, -14, -1, OBS)
    g.extrude(sanct_foot, 0, 0, DT)
    for y in range(1, 22):
        if y in (3, 9, 15, 20):
            mat = ESB
        elif y in (4, 10, 16):
            mat = PUR
        elif y in (6, 12, 18):
            mat = AME
        elif y in (7, 13, 19):
            mat = CHB
        else:
            mat = DT if y % 2 == 0 else PBB
        g.extrude(sanct_foot, y, y, mat)

    # Pilasters on the North Sanctum court-facing facade (z = -44)
    for sx in (-12, -7, 7, 12):
        g.box(sx, 1, -44, sx, 22, -44, OBS)
        g.box(sx, 2, -45, sx, 20, -45, PURP)
        g.set(sx, 10, -44, SEA)
        g.set(sx, 17, -44, SEA)
        g.set(sx, 23, -44, SEA)
        g.set(sx, 24, -44, EROD_U)

    # Open-ribbed Astral Dome crowning the North Sanctum (y = 21..44, centered at (0, -54))
    for y in range(21, 44):
        t = (y - 21) / 22.0
        r_dome = 14.2 * math.sqrt(max(0.0, 1.0 - t * t))
        ring_d = (sanct_r >= max(0.0, r_dome - 2.0)) & (sanct_r <= r_dome + 0.5)
        if y < 25:
            g.extrude(ring_d, y, y, DT if y % 2 == 0 else PUR)
        else:
            th_s = (np.degrees(np.arctan2(Z + 54.0, X)) + 360.0) % 360.0
            rib_s = (np.abs(((th_s + 15.0) % 30.0) - 15.0) <= 3.8) | (y in (27, 32, 37, 41))
            g.extrude(ring_d & rib_s, y, y, OBS if y % 2 == 0 else AME)
            g.extrude(ring_d & (np.abs(((th_s + 15.0) % 30.0) - 15.0) <= 1.3), y, y, SEA)
    # Spire finial on North Sanctum dome (0, 42..53, -54)
    g.box(-1, 42, -55, 1, 45, -53, OBS)
    g.box(0, 44, -54, 0, 49, -54, SEA)
    g.box(0, 50, -54, 0, 53, -54, EROD_U)

    # Carve North Sanctum interior (y = 1..25)
    sanct_in = ((Z <= -50) & (sanct_r <= 11.8)) | g.rect(-10, -54, 10, -46)
    g.extrude(sanct_in, 1, 20, 'air')
    g.extrude(sanct_r <= 9.8, 21, 26, 'air')

    # Carve 5 tall stained-glass & sea-lantern gothic lancet windows into the curved North Apse wall!
    th_apse = (np.degrees(np.arctan2(Z + 54.0, X)) + 360.0) % 360.0
    for w_deg in (210.0, 240.0, 270.0, 300.0, 330.0):
        d_w = np.abs(((th_apse - w_deg + 180.0) % 360.0) - 180.0)
        win_cut = (sanct_r >= 11.2) & (sanct_r <= 15.2) & (d_w <= 6.0)
        win_glass = (sanct_r >= 12.6) & (sanct_r <= 13.6) & (d_w <= 5.5)
        win_mullion = (sanct_r >= 12.2) & (sanct_r <= 14.0) & (d_w <= 1.2)
        g.extrude(win_cut, 4, 17, 'air')
        g.extrude(win_glass, 4, 17, PGLASS)
        g.extrude(win_mullion, 4, 17, OBS)
        for wy in (6, 10, 14, 17):
            g.extrude(win_mullion, wy, wy, SEA)
        g.extrude(win_glass & (d_w >= 4.2), 4, 17, AME)

    # Soaring Grand Pointed Portal Arch on North Sanctum court facade (z = -48..-42, x = -5..5, y = 1..18)
    g.box(-5, 1, -48, 5, 14, -42, 'air')
    for dy, hw in ((15, 4), (16, 3), (17, 2), (18, 1)):
        g.box(-hw, dy, -48, hw, dy, -42, 'air')
    for sx in (-6, 6):
        g.box(sx, 1, -45, sx, 15, -44, CHB)
        g.box(sx + (-1 if sx < 0 else 1), 1, -44, sx + (-1 if sx < 0 else 1), 16, -44, AME)
    for dy, hw in ((15, 5), (16, 4), (17, 3), (18, 2), (19, 1)):
        for sx in (-hw, hw):
            g.box(sx, dy, -45, sx, dy, -44, SEA if dy % 2 == 0 else AME)
    g.box(-1, 19, -45, 1, 20, -44, SEA)

    # Flanking lancet windows on North Sanctum facade (x = ±10, z = -46..-43, y = 4..15)
    for sx in (-10, 10):
        g.box(sx - 1, 4, -46, sx + 1, 14, -43, 'air')
        g.box(sx, 4, -44, sx, 14, -44, PGLASS)
        g.set(sx, 9, -44, SEA)
        g.set(sx, 14, -44, SEA)

    # Wide side archways from North Sanctum to Ring Cloisters (x = ±9..15, z = -53..-47)
    for sgn in (-1, 1):
        g.box(sgn * 9, 1, -53, sgn * 15, 11, -47, 'air')
        g.box(sgn * 9, 12, -52, sgn * 15, 13, -48, 'air')
        g.box(sgn * 9, 0, -53, sgn * 15, 0, -47, PUR)
        g.box(sgn * 9, 0, -51, sgn * 15, 0, -49, ESB)

    # North Sanctum floor mandala & 1-block-high Astral Altar Platform (y=1 at x=-5..5, z=-61..-55)
    g.extrude(sanct_r <= 11.5, 0, 0, DT)
    g.extrude((sanct_r >= 7.5) & (sanct_r <= 9.2), 0, 0, AME)
    g.extrude((sanct_r >= 5.0) & (sanct_r < 7.5), 0, 0, ESB)
    g.extrude(sanct_r <= 4.8, 0, 0, PUR)
    g.box(-3, 0, -55, 3, 0, -43, PUR)
    g.box(-1, 0, -55, 1, 0, -43, SEA)
    g.box(-5, 1, -61, 5, 1, -55, CHB)
    g.box(-4, 1, -60, 4, 1, -56, PUR)
    g.box(-2, 1, -60, 2, 1, -56, AME)

    # Towering Astral Armillary Shrine & Void Core inside North Sanctum (0, 2..20, -58)
    g.box(-1, 2, -60, 1, 3, -58, OBS)
    g.box(0, 3, -59, 0, 20, -59, SEA)
    for a_deg in range(0, 360, 12):
        ar = math.radians(a_deg)
        rx = round(math.cos(ar) * 4.2)
        ry = round(11.5 + math.sin(ar) * 4.2)
        rz = round(-58.0 + math.sin(ar) * 3.2)
        g.set(rx, ry, -58, AME if (a_deg // 12) % 2 == 0 else SEA)
        g.set(0, ry, rz, CRY if (a_deg // 12) % 2 == 0 else SEA)
    g.box(-1, 10, -59, 1, 13, -57, SEA)
    for dy in (4, 7, 10, 15):
        g.set(-1, dy, -59, EROD_U)
        g.set(1, dy, -59, EROD_U)

    # Sanctum apse interior ribs (between the stained-glass lancets)
    for a_deg in (195, 225, 255, 285, 315, 345):
        ar = math.radians(a_deg)
        px, pz = round(math.cos(ar) * 11.2), round(-54.0 + math.sin(ar) * 11.2)
        g.box(px, 1, pz, px, 19, pz, PURP)
        for ry in (5, 10, 15):
            g.set(px, ry, pz, SEA)

    # 5c. East & West Stepped Astrolabe Pavilions (curved with the ring at x = ±44..±58, z = -9..9)
    for sgn in (-1, 1):
        pav_r = np.hypot(X - sgn * 50.5, Z)
        pav = pav_r <= 8.5
        g.extrude(pav, -8, -1, OBS)
        g.extrude(pav, 0, 0, ESB)
        for y in range(1, 28):
            rr = 8.2 if y <= 17 else max(1.2, 8.2 - (y - 17) * 0.72)
            m = pav_r <= rr
            mat = ESB if y % 4 in (1, 2) else (PUR if y % 4 == 3 else DT)
            if y in (6, 12, 18, 22):
                mat = AME if y != 12 else SEA
            g.extrude(m, y, y, mat)
        g.set(sgn * 50, 28, 0, SEA)
        g.box(sgn * 50, 29, 0, sgn * 50, 31, 0, EROD_U)

        g.extrude(pav_r <= 6.0, 1, 15, 'air')
        g.extrude(pav_r <= 4.2, 16, 18, 'air')
        g.set(sgn * 50, 18, 0, SEA)
        g.set(sgn * 50, 17, 0, EROD_D)

        g.box(sgn * 43, 1, -3, sgn * 56, 11, 3, 'air')
        g.box(sgn * 43, 12, -2, sgn * 56, 13, 2, 'air')
        g.box(sgn * 47, 1, -10, sgn * 53, 11, 10, 'air')
        g.box(sgn * 48, 12, -10, sgn * 52, 13, 10, 'air')
        fx = sgn * 44
        for sz in (-4, 4):
            g.box(fx, 1, sz, fx, 12, sz, CHB)
            g.box(fx, 3, sz + (-1 if sz < 0 else 1), fx, 11, sz + (-1 if sz < 0 else 1), PURP)
            g.set(fx, 7, sz, SEA)
            g.set(fx, 12, sz, SEA)
        g.box(fx, 13, -3, fx, 14, 3, AME)
        g.set(fx, 14, 0, SEA)

        g.extrude(pav_r <= 5.8, 0, 0, PUR)
        g.extrude((pav_r >= 3.2) & (pav_r <= 4.4), 0, 0, AME)
        g.extrude(pav_r <= 1.8, 0, 0, SEA)

    # =============== 6. Twelve Towering Monoliths / Pylons (y = -28..68) ===============
    def build_monolith(cx: float, cz: float, top_y: int, base_r: float = 5.2, has_vault: bool = False, vault_deg: float = 0.0):
        d_m = np.hypot(X - cx, Z - cz)
        for y in range(-28, 0):
            t = (y + 28) / 28.0
            rr = base_r * (t ** 0.65)
            m = d_m <= rr
            g.extrude(m, y, y, OBS if y % 3 != 0 else DT)
            if y % 4 == 0 and rr >= 1.5:
                g.extrude((d_m >= rr - 0.9) & (d_m <= rr), y, y, CRY if y % 8 != 0 else SEA)

        shaft_top = top_y - 10
        for y in range(0, shaft_top + 1):
            t = y / float(max(1, shaft_top))
            rr = base_r * (1.0 - 0.38 * t)
            dx = np.abs(X - cx)
            dz = np.abs(Z - cz)
            oct_d = np.maximum(np.maximum(dx, dz), (dx + dz) * 0.74)
            m = oct_d <= rr
            if y % 7 in (0, 1):
                mat = DT
            elif y % 7 == 3:
                mat = ESB
            else:
                mat = OBS
            g.extrude(m, y, y, mat)
            corners = m & (dx >= rr - 1.2) & (dz >= rr - 1.2)
            g.extrude(corners, y, y, ESB if y % 2 == 0 else PURP)
            conduit = (oct_d <= rr + 0.3) & (oct_d >= rr - 0.8) & ((dx <= 0.8) | (dz <= 0.8))
            g.extrude(conduit, y, y, SEA if y % 4 == 0 else (AME if y % 2 == 0 else CRY))

        ix, iz = round(cx), round(cz)
        fork_r = max(2, round(base_r * 0.55))
        norm = math.hypot(cx, cz) or 1.0
        tx, tz = -cz / norm, cx / norm
        for sgn in (-1, 1):
            px = round(cx + sgn * tx * fork_r)
            pz = round(cz + sgn * tz * fork_r)
            for y in range(shaft_top + 1, top_y + 1):
                w = 1 if y < top_y - 3 else 0
                g.box(px - w, y, pz - w, px + w, y, pz + w, OBS if y % 2 == 0 else ESB)
                g.set(px, y, pz, AME if y % 3 == 0 else OBS)
            g.set(px, top_y + 1, pz, SEA)
            g.set(px, top_y + 2, pz, EROD_U)

        star_y = top_y - 4
        g.box(ix - 1, star_y - 1, iz - 1, ix + 1, star_y + 1, iz + 1, AME)
        g.box(ix - 1, star_y, iz, ix + 1, star_y, iz, SEA)
        g.box(ix, star_y, iz - 1, ix, star_y, iz + 1, SEA)
        g.box(ix, star_y - 1, iz, ix, star_y + 1, iz, SEA)
        for dy in (2, 3, 4):
            g.set(ix, star_y + dy, iz, EROD_U)
            g.set(ix, star_y - dy, iz, EROD_D)

        if has_vault:
            vault_in = d_m <= 3.8
            g.extrude(vault_in, 0, 0, PUR)
            g.extrude(d_m <= 2.2, 0, 0, AME)
            g.set(ix, 0, iz, SEA)
            g.extrude(vault_in, 1, 10, 'air')
            g.extrude(d_m <= 2.6, 11, 12, 'air')
            g.set(ix, 12, iz, SEA)
            g.set(ix, 11, iz, EROD_D)
            a_rad = math.radians(vault_deg)
            for step_r in np.linspace(48.0, 55.5, 16):
                ax, az = round(math.cos(a_rad) * step_r), round(math.sin(a_rad) * step_r)
                g.box(ax - 2, 0, az - 2, ax + 2, 0, az + 2, PUR)
                g.box(ax - 1, 0, az - 1, ax + 1, 0, az + 1, ESB)
                g.box(ax - 2, 1, az - 2, ax + 2, 9, az + 2, 'air')
                g.box(ax - 1, 10, az - 1, ax + 1, 11, az + 1, 'air')

    # Build the 4 Diagonal Monolith Vault Towers (45°, 135°, 225°, 315° at R = 55.0)
    for deg in (45.0, 135.0, 225.0, 315.0):
        a = math.radians(deg)
        build_monolith(math.cos(a) * 55.0, math.sin(a) * 55.0, top_y=60, base_r=6.0, has_vault=True, vault_deg=deg)

    # Build 2 North Astral Spire Monoliths flanking the North Sanctum (x = ±17, z = -58)
    for sx in (-17, 17):
        build_monolith(sx, -58.0, top_y=68, base_r=5.2, has_vault=False)
        x_in = -11 if sx < 0 else 11
        g.box(min(sx, x_in), 4, -59, max(sx, x_in), 8, -57, DT)
        g.box(min(sx, x_in), 6, -58, max(sx, x_in), 6, -58, SEA)
        g.box(min(sx, x_in), 14, -59, max(sx, x_in), 17, -57, OBS)
        g.box(min(sx, x_in), 15, -58, max(sx, x_in), 15, -58, AME)

    # Build 2 South Gatehouse Monoliths flanking the South Narthex (x = ±14, z = 54)
    for sx in (-14, 14):
        build_monolith(sx, 54.0, top_y=52, base_r=4.8, has_vault=False)

    # Build 4 Outer Floating-Look Sentinel Monoliths (0° East, 180° West, 208° WNW, 332° ENE at R = 63.0)
    for deg, ty in ((0.0, 56), (180.0, 56), (208.0, 50), (332.0, 50)):
        a = math.radians(deg)
        mx, mz = math.cos(a) * 63.0, math.sin(a) * 63.0
        build_monolith(mx, mz, top_y=ty, base_r=4.5, has_vault=False)
        for s_r in np.linspace(54.5, 62.5, 18):
            bx, bz = round(math.cos(a) * s_r), round(math.sin(a) * s_r)
            by_low = round(-4.0 + 3.0 * math.sin((s_r - 54.5) / 8.0 * math.pi))
            by_high = round(14.0 + 4.0 * math.sin((s_r - 54.5) / 8.0 * math.pi))
            g.box(bx - 1, by_low - 1, bz - 1, bx + 1, by_low + 1, bz + 1, OBS)
            g.set(bx, by_low, bz, SEA)
            g.box(bx - 1, by_high - 1, bz - 1, bx + 1, by_high + 1, bz + 1, DT)
            g.set(bx, by_high, bz, AME)

    # Re-clear Narthex, North Sanctum & East/West Pavilion interiors where monolith bases overlap
    g.box(-10, 1, 46, 10, 14, 54, 'air')
    g.box(-4, 1, 42, 4, 12, 58, 'air')
    g.box(-5, 1, -48, 5, 14, -36, 'air')
    for sgn in (-1, 1):
        g.box(sgn * 9, 1, 47, sgn * 12, 9, 52, 'air')
        pav_r = np.hypot(X - sgn * 50.5, Z)
        g.extrude(pav_r <= 5.8, 1, 14, 'air')
        g.box(sgn * 43, 1, -3, sgn * 56, 11, 3, 'air')
        g.box(sgn * 47, 1, -10, sgn * 53, 11, 10, 'air')

    # =============== 7. Giant Tilted Overhead Broken Halo Rings (R = 38..62, strictly R > 35.5) ===============
    # 7a. Primary Tilted Broken Halo Ring (R_halo = 55.0, y = 30..58)
    y_halo1 = 44.0 - 0.22 * Z3 + 0.06 * X3
    dr1 = np.abs(R[:, None, :] - 55.0)
    dy1 = np.abs(Y3 - y_halo1)
    torus1 = (R[:, None, :] >= 51.0) & ((dr1 ** 2 + dy1 ** 2) <= (2.6 ** 2))
    d_s90 = np.abs(((TH - 90.0 + 180.0) % 360.0) - 180.0) / 13.5
    d_n270 = np.abs(((TH - 270.0 + 180.0) % 360.0) - 180.0) / 14.5
    d_e22 = np.abs(((TH - 22.0 + 180.0) % 360.0) - 180.0) / 4.5
    d_w158 = np.abs(((TH - 158.0 + 180.0) % 360.0) - 180.0) / 4.5
    gap1 = np.minimum(np.minimum(d_s90, d_n270), np.minimum(d_e22, d_w158))
    halo1_mask = torus1 & (gap1[:, None, :] >= 1.0)
    halo1_core = (R[:, None, :] >= 51.5) & ((dr1 ** 2 + dy1 ** 2) <= (1.35 ** 2)) & (gap1[:, None, :] >= 0.85)
    halo1_tips = torus1 & (gap1[:, None, :] >= 0.82) & (gap1[:, None, :] < 1.0)

    g.where3(halo1_mask, OBS)
    g.where3(halo1_mask & (dr1 >= 1.7), ESB)
    g.where3(halo1_mask & (dy1 >= 1.7), DT)
    g.where3(halo1_tips, AME)
    g.where3(halo1_core, SEA)
    g.where3(halo1_mask & ((np.round(TH)[:, None, :] % 8) < 2) & (dr1 >= 1.5), PUR)

    # 7b. Secondary Counter-Tilted Outer Halo Ring (R_halo2 = 58.5, y = 30..56)
    y_halo2 = 43.0 + 0.14 * Z3 - 0.16 * X3
    dr2 = np.abs(R[:, None, :] - 58.5)
    dy2 = np.abs(Y3 - y_halo2)
    torus2 = (R[:, None, :] >= 55.0) & ((dr2 ** 2 + dy2 ** 2) <= (2.2 ** 2))
    d2_s = np.abs(((TH - 90.0 + 180.0) % 360.0) - 180.0) / 40.0
    d2_n = np.abs(((TH - 270.0 + 180.0) % 360.0) - 180.0) / 14.5
    gap2 = np.minimum(d2_s, d2_n)
    halo2_mask = torus2 & (gap2[:, None, :] >= 1.0)
    halo2_core = (R[:, None, :] >= 55.5) & ((dr2 ** 2 + dy2 ** 2) <= (1.1 ** 2)) & (gap2[:, None, :] >= 0.88)
    halo2_tips = torus2 & (gap2[:, None, :] >= 0.85) & (gap2[:, None, :] < 1.0)

    g.where3(halo2_mask, DT)
    g.where3(halo2_mask & (dr2 >= 1.4), ESB)
    g.where3(halo2_tips, AME)
    g.where3(halo2_core, SEA)

    # 7c. Inner Astral Crown Ribs & Broken Halo (R = 38.5..45.2, y = 18..31 — strictly R > 35.5)
    inner_halo = (np.abs(R - 38.8) <= 1.2)[:, None, :] & (np.abs(Y3 - 29.0) <= 1.2) & (d_shatter[:, None, :] >= 9.0)
    g.where3(inner_halo, OBS)
    g.where3((np.abs(R - 38.8) <= 0.6)[:, None, :] & (Y3 == 29) & (d_shatter[:, None, :] >= 8.0), SEA)
    g.where3((np.abs(R - 38.8) <= 0.6)[:, None, :] & (Y3 == 30) & (d_shatter[:, None, :] >= 8.0), AME)

    for k in range(18):
        deg = k * 20.0 + 10.0
        if any(abs(((deg - c + 180.0) % 360.0) - 180.0) < 9.5 for c in shatter_centres):
            continue
        a = math.radians(deg)
        for i, rr in enumerate(np.linspace(45.0, 38.8, 10)):
            rx, rz = round(math.cos(a) * rr), round(math.sin(a) * rr)
            ry = round(19.0 + 10.0 * math.sin((i / 9.0) * (math.pi * 0.5)))
            g.box(rx, ry - 1, rz, rx, ry + 1, rz, ESB if i % 2 == 0 else OBS)
            if i in (3, 6):
                g.set(rx, ry, rz, SEA)
        px, pz = round(math.cos(a) * 38.8), round(math.sin(a) * 38.8)
        g.box(px, 24, pz, px, 27, pz, CHAIN)
        g.set(px, 23, pz, SEA if k % 2 == 0 else AME)
        g.set(px, 22, pz, EROD_D)

    # =============== 8. Entrance Bridge (+Z) & Natural-Island Landing Terrace (z = 56..88) ===============
    land_r = np.hypot(X, (Z - 81.0) * 1.15)
    for y in range(-18, 1):
        t = (y + 18) / 18.0
        rr = 17.5 * (t ** 0.55) + (n_under - 0.5) * 2.0
        m = (Z >= 72) & (Z <= 89) & (land_r <= rr)
        g.extrude(m, y, y, ES if y < -3 else (ESB if (y % 2 == 0) else DT))
    for sx in (-8, -4, 0, 4, 8):
        g.box(sx, -12, 73, sx, -2, 74, OBS)
        g.box(sx, -10, 73, sx, -4, 73, CRY if sx != 0 else SEA)

    plaza = (Z >= 73) & (Z <= 87) & (land_r <= 15.0)
    g.extrude(plaza, 0, 0, DT)
    g.extrude(plaza & (land_r >= 13.5), 0, 0, CHB)
    g.extrude(plaza & (np.abs(land_r - 9.5) <= 0.8), 0, 0, AME)
    g.extrude(plaza & (np.abs(land_r - 5.5) <= 0.8), 0, 0, ESB)
    g.box(-4, 0, 73, 4, 0, 87, PUR)
    g.box(-2, 0, 73, 2, 0, 87, ESB)
    for z in range(74, 87, 2):
        g.set(0, 0, z, SEA)
    cliff_parapet = (Z >= 73) & (Z <= 76) & (land_r >= 13.5) & (land_r <= 15.0) & (np.abs(X) >= 6)
    g.extrude(cliff_parapet, 1, 1, CHB)
    g.extrude(cliff_parapet, 2, 2, wall('deepslate_tile'))

    # Twin Landing Gate-Pylons at (x = ±10, z = 76, rising to y = 36)
    for sx in (-10, 10):
        build_monolith(sx, 76.0, top_y=36, base_r=3.6, has_vault=False)
    for x in range(-9, 10):
        if abs(x) in (1, 2):
            continue
        ay = round(24.0 - 0.08 * (x * x))
        g.box(x, ay - 1, 75, x, ay + 1, 77, OBS if abs(x) > 4 else AME)
        g.set(x, ay, 76, SEA)
    for sx in (-3, 3):
        g.box(sx, 22, 75, sx, 25, 77, SEA)
        g.set(sx, 26, 76, EROD_U)

    # 8b. Short, Grand Ceremonial Bridge across the Void Gap (z = 56..73, x = -6..6)
    for z in range(56, 74):
        dz = (z - 64.5) / 9.0
        arch_top = -1
        arch_bot = round(-3.5 - 8.5 * (dz * dz))
        g.box(-6, arch_bot, z, 6, arch_top, z, OBS if abs(z - 65) > 2 else DT)
        g.box(-5, arch_bot + 1, z, 5, arch_top, z, ESB)
        if 62 <= z <= 67:
            g.box(-6, arch_bot, z, 6, arch_bot + 1, z, AME)
            g.box(-3, arch_bot - 1, z, 3, arch_bot, z, SEA)

    g.box(-5, 0, 56, 5, 0, 74, ESB)
    g.box(-4, 0, 56, 4, 0, 74, PUR)
    g.box(-2, 0, 56, 2, 0, 74, DT)
    g.box(-1, 0, 56, 1, 0, 74, AME)
    for z in range(56, 75, 2):
        g.set(0, 0, z, SEA)
        g.set(-3, 0, z, SEA if z % 4 == 0 else CHB)
        g.set(3, 0, z, SEA if z % 4 == 0 else CHB)

    g.box(-4, 1, 54, 4, 14, 88, 'air')

    for sx in (-5, 5):
        g.box(sx, 1, 57, sx, 1, 73, CHB)
        g.box(sx, 2, 57, sx, 2, 73, wall('end_stone_brick'))
        for zp in (59, 65, 71):
            g.box(sx, 1, zp, sx, 2, zp, CHB)
            g.box(sx, 3, zp, sx, 7, zp, PURP)
            g.set(sx, 8, zp, AME)
            g.set(sx, 9, zp, SEA)
            g.set(sx, 10, zp, EROD_U)

    # =============== 9. Place 10 Treasure Chests (6 Major Tier=0 + 4 Minor Tier=1) ===============
    # 9a. 2 Major Tier=0 Chests on the North Void Reliquary Altar Platform (y=2 on y=1 dais)
    g.box(-4, 2, -59, 4, 5, -55, 'air')
    g.box(-1, 2, -60, 1, 3, -58, OBS)
    g.box(0, 3, -59, 0, 10, -59, SEA)
    g.add_chest(-3, 2, -57, 'south', tier=0)
    g.add_chest(3, 2, -57, 'south', tier=0)

    # 9b. 4 Major Tier=0 Chests inside the 4 Diagonal Monolith Vaults (SE, SW, NW, NE)
    for deg, facing in ((45.0, 'north'), (135.0, 'north'), (225.0, 'south'), (315.0, 'south')):
        a = math.radians(deg)
        cx, cz = round(math.cos(a) * 55.0), round(math.sin(a) * 55.0)
        g.box(cx - 2, 0, cz - 2, cx + 2, 0, cz + 2, PUR)
        g.box(cx - 1, 0, cz - 1, cx + 1, 0, cz + 1, AME)
        g.set(cx, 0, cz, SEA)
        g.box(cx - 2, 1, cz - 2, cx + 2, 6, cz + 2, 'air')
        g.add_chest(cx, 1, cz, facing, tier=0)

    # 9c. 4 Minor Tier=1 Chests in the East & West Ring Cloister Pavilions
    for sgn in (-1, 1):
        for sz, face_z in ((-5, 'south'), (5, 'north')):
            cx = sgn * 52
            g.box(cx - 1, 0, sz - 1, cx + 1, 0, sz + 1, AME)
            g.set(cx, 0, sz, SEA)
            g.box(cx - 1, 1, sz - 1, cx + 1, 5, sz + 1, 'air')
            bx = sgn * 54
            g.box(bx, 1, sz - 1, bx, 4, sz + 1, CHB)
            g.box(bx, 2, sz, bx, 3, sz, SEA)
            g.add_chest(cx, 1, sz, face_z, tier=1)

    # =============== 10. Final Boss Court Clearance Guarantee (R <= 35, 1 <= y <= 72) ===============
    court_clear = (R <= COURT_R)[:, None, :] & (Y3 >= 1) & (Y3 <= 72)
    g.where3(court_clear, 'air')

    return g


VIEWS = {
    'exterior': dict(
        cam=(76, 38, 91),
        target=(-4, 6, -4),
        fov=78,
        size=(1600, 900),
        ambient=0.40,
        light_boost=0.10,
        fog=(0.0012, (16, 10, 28)),
        sky=(16, 10, 28),
    ),
    'bridge': dict(
        cam=(0, 5, 83),
        target=(0, 11, 10),
        fov=76,
        size=(1600, 900),
        ambient=0.40,
        light_boost=0.10,
        fog=(0.0012, (16, 10, 28)),
        sky=(16, 10, 28),
    ),
    'cloister': dict(
        cam=(11, 5, -48),
        target=(-4, 8, -58),
        fov=80,
        size=(1600, 900),
        ambient=0.40,
        light_boost=0.10,
        fog=(0.0012, (16, 10, 28)),
        sky=(16, 10, 28),
    ),
    'court': dict(
        cam=(0, 4, 24),
        target=(0, 14, -45),
        fov=82,
        size=(1600, 900),
        ambient=0.40,
        light_boost=0.10,
        fog=(0.0012, (16, 10, 28)),
        sky=(16, 10, 28),
    ),
}


if __name__ == '__main__':
    build()
