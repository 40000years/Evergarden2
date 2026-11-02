"""Soul Bone Temple / วิหารกระดูกวิญญาณ (Nether): a sunken Soul Sand Valley ossuary necropolis
spanned by colossal leviathan rib-cage arches inside a sealed underground cavern.

Local frame: site centre (0, 0), floor y=0, entrance on +Z (south).
Boss court: radius 34, clear height 40 (1 <= y <= 40), crowned above y=40 by a giant
rib-cage and dorsal spine vault.
"""
from __future__ import annotations

import math
import numpy as np

from .grid import Grid, hash2, hash3, fbm, vnoise

# ---- materials ----
BS = 'blackstone'
PBS = 'polished_blackstone'
PBB = 'polished_blackstone_bricks'
CPBB = 'cracked_polished_blackstone_bricks'
CHB = 'chiseled_polished_blackstone'
GBS = 'gilded_blackstone'

DST = 'deepslate_tiles'
CDST = 'cracked_deepslate_tiles'
DSB = 'deepslate_bricks'
CHDS = 'chiseled_deepslate'

BAS = 'basalt[axis=y]'
PBAS = 'polished_basalt[axis=y]'
SBAS = 'smooth_basalt'

BONE_Y = 'bone_block[axis=y]'
BONE_X = 'bone_block[axis=x]'
BONE_Z = 'bone_block[axis=z]'
SQZ = 'smooth_quartz'
QZB = 'quartz_bricks'
QZP = 'quartz_pillar[axis=y]'

SSOIL = 'soul_soil'
SSAND = 'soul_sand'
CRY = 'crying_obsidian'
OBS = 'obsidian'

SEA = 'sea_lantern'
SHROOM = 'shroomlight'
CGLASS = 'cyan_stained_glass'
SLANT = 'soul_lantern[hanging=false,waterlogged=false]'
SLANTH = 'soul_lantern[hanging=true,waterlogged=false]'
SCAMP = 'soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]'
SFIRE = 'soul_fire'
CHAIN = 'iron_chain[axis=y,waterlogged=false]'
BARS = 'iron_bars[north=false,east=false,south=false,west=false,waterlogged=false]'


def stair(mat: str, facing: str, half: str = 'bottom') -> str:
    return f'{mat}_stairs[facing={facing},half={half},shape=straight,waterlogged=false]'


def slab(mat: str, kind: str = 'bottom') -> str:
    return f'{mat}_slab[type={kind},waterlogged=false]'


X0, X1, Y0, Y1, Z0, Z1 = -68, 68, -12, 66, -68, 104
COURT_R = 34
WALL_IN = 35
WALL_OUT = 46
WALL_TOP = 24
CAV_R = 64

META = {
    'arena': {'x': 0, 'y': 0, 'z': 0, 'radius': COURT_R, 'height': 40},
    'landing': {'x': 0, 'y': 1, 'z': 52},
    'mouth': {'x': 0, 'y': 1, 'z': 98, 'dir': 'south'},
    'gate': {'x': 0, 'y': 1, 'z': 36, 'width': 9, 'height': 12, 'dir': 'south'},
}


def build() -> Grid:
    g = Grid(X0, X1, Y0, Y1, Z0, Z1)
    X, Z, R, TH = g.X, g.Z, g.R, g.TH
    Yv = np.arange(Y0, Y1 + 1)[None, :, None]
    X3 = X[:, None, :] * np.ones_like(Yv)
    Z3 = Z[:, None, :] * np.ones_like(Yv)
    Y3 = Yv * np.ones_like(X[:, None, :])
    air3 = np.zeros(g.a.shape, bool)

    # =========================================================================
    # 1. SEALED SOUL SAND VALLEY CAVERN SHELL
    # =========================================================================
    n_rad = (fbm(X, Z, 11.0, 3) - 0.5) * 4.0
    Rn = R + n_rad
    # Broad, high-shouldered cavern vault so both exterior & court views see the full rib dome
    u = np.clip(Rn / CAV_R, 0.0, 1.0)
    Hc = 24.0 + 37.0 * ((1.0 - u ** 2) ** 0.42) + (fbm(X, Z, 14.0, 5) - 0.5) * 3.5
    Hc = np.clip(Hc, 18.0, 61.0)
    cav_main = (Rn <= CAV_R)[:, None, :] & (Yv >= 1) & (Yv <= Hc[:, None, :])
    air3 |= cav_main

    # East & West crypt cavern lobes
    for sx in (-1, 1):
        dx = X - sx * 42.0
        dz = Z
        r_lobe = np.hypot(dx / 24.0, dz / 24.0)
        h_lobe = 12.0 + 24.0 * np.clip(1.0 - r_lobe ** 2, 0.0, 1.0)
        air3 |= (r_lobe <= 1.0)[:, None, :] & (Yv >= 1) & (Yv <= h_lobe[:, None, :])

    # North sanctum cavern lobe
    r_north = np.hypot(X / 24.0, (Z + 42.0) / 24.0)
    h_north = 12.0 + 24.0 * np.clip(1.0 - r_north ** 2, 0.0, 1.0)
    air3 |= (r_north <= 1.0)[:, None, :] & (Yv >= 1) & (Yv <= h_north[:, None, :])

    # South narthex & processional spine causeway cavern (Z = 30..101)
    w_south = np.where(Z < 68, 28.0, 19.0)
    h_south_max = np.where(Z < 68, 36.0, 25.0)
    u_south = np.clip(np.abs(X) / w_south, 0.0, 1.5)
    h_south = 9.0 + (h_south_max - 9.0) * np.sqrt(np.clip(1.0 - u_south ** 2, 0.0, 1.0))
    cav_south = (np.abs(X) <= w_south) & (Z >= 30) & (Z <= 101)
    air3 |= cav_south[:, None, :] & (Yv >= 1) & (Yv <= h_south[:, None, :])

    # 3-block thick sealed cavern shell
    shell = g.dilate(air3, 3) & ~air3
    sh = hash3(X3, Y3, Z3, 11)
    strata = vnoise(X3 + Y3 * 2, Z3 + Y3 * 2, 7.0, 19)
    for lo, hi, mat in (
        (0.00, 0.36, SSOIL),
        (0.36, 0.60, BAS),
        (0.60, 0.78, BS),
        (0.78, 0.91, SBAS),
        (0.91, 0.97, SSAND),
        (0.97, 1.01, BONE_Y),
    ):
        g.where3(shell & (sh >= lo) & (sh < hi), mat)
    g.where3(shell & (strata > 0.78), BAS)
    g.where3(shell & (strata < 0.16), SSOIL)
    g.where3(shell & (sh > 0.984), CRY)
    g.where3(air3, 'air')

    # Solid sub-floor foundation under the entire sanctuary
    floor_fp = (
        (Rn <= CAV_R + 2)
        | ((np.abs(X) <= 30) & (Z >= 28) & (Z <= 102))
        | ((np.abs(X) <= 65) & (np.abs(Z) <= 26))
        | ((np.abs(X) <= 26) & (Z >= -65) & (Z <= -28))
    )
    depth = -(6 + 3 * np.clip(1.0 - (R / 68.0) ** 2, 0.0, 1.0) + (fbm(X, Z, 8.0, 7) - 0.5) * 2.0).astype(int)
    depth = np.clip(depth, -9, -5)
    mass = floor_fp[:, None, :] & (Yv <= 0) & (Yv >= depth[:, None, :])
    sh_fl = hash3(X3, Y3, Z3, 23)
    g.where3(mass & (Yv < 0), BS)
    g.where3(mass & (Yv < 0) & (sh_fl > 0.45), SSOIL)
    g.where3(mass & (Yv < -2) & (sh_fl > 0.72), BAS)

    # Default cavern floor at y=0 outside the temple walls: Soul Sand Valley terrain with bone paths
    fl_noise = hash2(X // 2, Z // 2, 13)
    for lo, hi, mat in (
        (0.00, 0.38, SSOIL),
        (0.38, 0.62, BS),
        (0.62, 0.80, SBAS),
        (0.80, 0.93, PBB),
        (0.93, 1.01, SSAND),
    ):
        g.extrude(floor_fp & (fl_noise >= lo) & (fl_noise < hi), 0, 0, mat)

    # Glowing Soul-Fire & Cyan Chasm Trench around the exterior perimeter (R = 49.0..55.5)
    outer_trench = (R >= 49.0) & (R <= 55.5) & (Z < 36)
    g.extrude(outer_trench, -2, -2, SSOIL)
    g.extrude((R >= 51.0) & (R <= 53.5) & (Z < 36), -2, -2, SEA)
    g.extrude((R >= 50.5) & (R <= 54.0) & (Z < 36), -1, -1, CGLASS)
    g.extrude(outer_trench, 0, 0, 'air')
    g.extrude((np.abs(R - 48.5) < 0.8) & (Z < 36), -2, 0, CHB)
    g.extrude((np.abs(R - 56.0) < 0.8) & (Z < 36), -2, 0, CHB)
    # Fossilized rib-bridges & soul beacons around the outer chasm
    for k in range(24):
        deg = k * 15.0
        rad = math.radians(deg)
        tx, tz = round(math.cos(rad) * 52.2), round(math.sin(rad) * 52.2)
        if tz < 34:
            g.box(tx - 1, -2, tz - 1, tx + 1, -2, tz + 1, CRY)
            g.set(tx, -1, tz, SEA)
            if k % 2 == 0:
                g.set(tx, 0, tz, BONE_Y)
                g.set(tx, 1, tz, SLANT)

    # Cavern ceiling stalactites & glowing fossil ribs (strictly outside R<=34 or above y=48)
    for sx in range(-58, 59, 6):
        for sz in range(-58, 92, 6):
            r_pt = math.hypot(sx, sz)
            if r_pt < 38 and sz < 36:
                continue
            if r_pt > 58 and abs(sx) > 20:
                continue
            if hash2(sx, sz, 43) < 0.52:
                continue
            for cy in range(60, 22, -1):
                if g.get(sx, cy, sz) == 'minecraft:air' and g.get(sx, cy + 1, sz) != 'minecraft:air':
                    length = 3 + int(hash2(sx, sz, 47) * 4)
                    for dy in range(length):
                        g.set(sx, cy - dy, sz, BAS if dy < length - 1 else CRY)
                    if hash2(sx, sz, 53) > 0.40:
                        g.set(sx, cy - length, sz, SLANTH)
                    break

    # =========================================================================
    # 2. CIRCULAR NECROPOLIS PERIMETER WALL & FLYING BONE BUTTRESSES (R = 35..46)
    # =========================================================================
    wall_ring = (R >= WALL_IN) & (R <= WALL_OUT)
    g.extrude(wall_ring, 0, WALL_TOP, PBB)
    w_hash = hash3(X3, Y3, Z3, 31)
    wall3 = wall_ring[:, None, :] & (Yv >= 1) & (Yv <= WALL_TOP)
    g.where3(wall3 & (w_hash > 0.82), CPBB)
    g.where3(wall3 & (w_hash > 0.64) & (w_hash <= 0.82), DST)
    g.where3(wall3 & (w_hash < 0.10), PBS)

    # High Clerestory Wall (R = 35.0..38.0, y = 25..35) with Bone-Spire Crown at y = 36..40
    # Leave an open cutaway terrace on the SE exterior viewing sector (deg 28..62) above y=24
    # so the exterior camera at (38, 36, 40) looks directly across the outer buttresses INTO the Boss Court!
    se_cutaway = g.wedge(45.0, 18.0) & (X > 10) & (Z > 10)
    clerestory_ring = (R >= WALL_IN) & (R <= 38.0) & (~se_cutaway)
    g.extrude(clerestory_ring, WALL_TOP + 1, 34, PBB)
    g.extrude((R >= WALL_IN) & (R <= 38.0), WALL_TOP, WALL_TOP, BONE_Y)
    g.extrude(clerestory_ring, 35, 35, BONE_Y)

    # Horizontal bone & chiseled blackstone friezes on the inner and outer wall faces
    inner_face = (R >= WALL_IN) & (R <= WALL_IN + 1.2)
    outer_face = (R >= WALL_OUT - 1.2) & (R <= WALL_OUT)
    for y_band in (7, 8, 19, 20):
        g.extrude(inner_face | outer_face, y_band, y_band, CHB)
    for y_bone in (8, 20, WALL_TOP):
        g.extrude(inner_face | outer_face, y_bone, y_bone, BONE_Y)

    # Outer Wall Roof Terrace (R = 38.2..46.0, y = 24): Bone-and-Deepslate patterned promenade with spires
    roof_terrace = (R >= 38.2) & (R <= WALL_OUT)
    g.extrude(roof_terrace, WALL_TOP, WALL_TOP, DST)
    g.extrude((R >= 41.0) & (R <= 43.0), WALL_TOP, WALL_TOP, BONE_Y)
    for k in range(32):
        deg = k * 11.25
        rad = math.radians(deg)
        # Outer rim bone pinnacles at R = 45.5
        ox, oz = round(math.cos(rad) * (WALL_OUT - 0.5)), round(math.sin(rad) * (WALL_OUT - 0.5))
        g.box(ox - 1, WALL_TOP + 1, oz - 1, ox + 1, WALL_TOP + 3, oz + 1, BONE_Y)
        g.set(ox, WALL_TOP + 4, oz, SQZ)
        g.set(ox, WALL_TOP + 5, oz, SEA if k % 2 == 0 else SLANT)
        # Inner high crown bone fangs at R = 36.2 (y = 36..39)
        if not (27.0 <= deg <= 63.0):
            ix, iz = round(math.cos(rad) * 36.2), round(math.sin(rad) * 36.2)
            g.box(ix, 36, iz, ix, 38, iz, BONE_Y)
            g.set(ix, 39, iz, SEA if k % 2 == 0 else SQZ)

    # 16 Exterior Flying Bone Buttresses around the perimeter (R = 45..54)
    for k in range(16):
        deg = k * 22.5
        if abs(((deg - 90 + 180) % 360) - 180) < 25:
            continue
        if abs(((deg - 0 + 180) % 360) - 180) < 16:
            continue
        if abs(((deg - 180 + 180) % 360) - 180) < 16:
            continue
        if abs(((deg - 270 + 180) % 360) - 180) < 16:
            continue
        rad = math.radians(deg)
        ux, uz = math.cos(rad), math.sin(rad)
        px, pz = -uz, ux
        # Outer pier rising out of the glowing soul trench at R = 53
        ox, oz = round(ux * 53.0), round(uz * 53.0)
        g.box(ox - 1, -2, oz - 1, ox + 1, 18, oz + 1, PBB)
        g.box(ox, 1, oz, ox, 20, oz, BONE_Y)
        g.set(ox, 21, oz, SQZ)
        g.set(ox, 22, oz, SEA)
        # Double-tiered curved bone flying arches from R = 53 to R = 45
        for s in range(18):
            t = s / 17.0
            rr = 53.0 - t * 8.0
            yy1 = round(14.0 + 10.0 * math.sin(t * math.pi * 0.5))
            yy2 = round(6.0 + 8.0 * math.sin(t * math.pi * 0.5))
            bx, bz = round(ux * rr), round(uz * rr)
            for w in (-1, 0, 1):
                wx, wz = round(bx + px * w * 0.7), round(bz + pz * w * 0.7)
                g.box(wx, yy1 - 1, wz, wx, yy1 + 1, wz, BONE_Y if w == 0 else PBS)
            g.set(bx, yy1 + 2, bz, SQZ if s % 3 != 0 else SEA)
            g.box(bx, yy2, bz, bx, yy2 + 1, bz, BONE_Y)

    # =========================================================================
    # 3. CARDINAL HALLS: EAST & WEST OSSUARY CRYPTS & NORTH SANCTUM
    # =========================================================================
    # 3a. East & West Ossuary Crypts (x = ±36..55, z = -15..15)
    for sx in (-1, 1):
        xa, xb = min(sx * 37, sx * 55), max(sx * 37, sx * 55)
        g.box(xa, 0, -16, xb, 21, 16, PBB)
        ia, ib = min(sx * 37, sx * 52), max(sx * 37, sx * 52)
        g.box(ia, 0, -13, ib, 0, 13, DST)
        g.box(ia, 1, -13, ib, 16, 13, 'air')

        # Sunken Cyan Soul-Glass Ossuary Trench in the Crypt Floor (x = ±40..45, z = -8..8)
        ga, gb = min(sx * 40, sx * 45), max(sx * 40, sx * 45)
        g.box(ga, -2, -8, gb, -2, 8, SSOIL)
        for z_b in range(-6, 7, 3):
            g.box(ga + 1, -2, z_b, gb - 1, -2, z_b, BONE_X)
            g.set(sx * 42, -2, z_b, SEA)
            g.set(sx * 43, -2, z_b, SEA)
        g.box(ga, -1, -8, gb, -1, 8, 'air')
        g.box(ga, 0, -8, gb, 0, 8, CGLASS)
        # Walkable Bone Spine Bridge across the center of the Crypt (z = -2..2)
        g.box(ia, 0, -2, ib, 0, 2, QZB)
        g.box(ia, 0, -1, ib, 0, 1, BONE_X)
        for x_l in range(ia + 1, ib, 3):
            g.set(x_l, 0, 0, SEA)

        # Curved Bone Rib Vaults spanning the Crypt (x = ±39, ±44, ±49)
        for x_r in (sx * 39, sx * 44, sx * 49):
            for z_c in range(-12, 13):
                u_z = abs(z_c) / 12.0
                y_v = round(16.0 - 5.0 * (u_z ** 2))
                g.box(x_r, y_v, z_c, x_r, min(16, y_v + 1), z_c, BONE_Z if abs(z_c) < 8 else BONE_Y)
                if abs(z_c) in (0, 6):
                    g.set(x_r, y_v - 1, z_c, SEA)
            for z_side in (-13, 13):
                g.box(x_r, 1, z_side, x_r, 12, z_side, BONE_Y)
                g.set(x_r, 8, z_side - (1 if z_side > 0 else -1), SEA)
            g.box(x_r, 13, 0, x_r, 14, 0, CHAIN)
            g.set(x_r, 12, 0, SLANTH)

        # Tiered Bone Catacomb Niches along the North, South & Back crypt walls
        for z_wall in (-14, 14):
            for x_n in (sx * 41, sx * 46, sx * 50):
                for y_n in (2, 6, 10):
                    g.box(x_n - 1, y_n, z_wall, x_n + 1, y_n + 2, z_wall, SSOIL)
                    g.set(x_n - 1, y_n, z_wall, BONE_Y)
                    g.set(x_n + 1, y_n, z_wall, BONE_Y)
                    g.set(x_n, y_n, z_wall, BONE_Z)
                    g.set(x_n, y_n + 1, z_wall, SEA if y_n == 6 else SLANT)
                    g.set(x_n, y_n + 2, z_wall, CRY)
        for z_back in (-9, -6, 6, 9):
            for y_n in (2, 6, 10):
                g.box(sx * 53, y_n, z_back - 1, sx * 53, y_n + 2, z_back + 1, CRY)
                g.set(sx * 52, y_n, z_back, BONE_Y)
                g.set(sx * 52, y_n + 1, z_back, SEA if y_n == 6 else SLANT)

        # Central Reliquary Shrine at the back of the Crypt (x = sx * 47..51, z = -3..3)
        g.box(min(sx * 46, sx * 51), 0, -4, max(sx * 46, sx * 51), 0, 4, QZB)
        for dz in (-3, 3):
            g.box(sx * 47, 1, dz, sx * 47, 10, dz, BONE_Y)
            g.box(sx * 51, 1, dz, sx * 51, 10, dz, BONE_Y)
            # Soul-fire braziers flanking the shrine entrance
            g.set(sx * 45, 1, dz, CHB)
            g.set(sx * 45, 2, dz, BONE_Y)
            g.set(sx * 45, 3, dz, SEA)
            g.set(sx * 45, 4, dz, SLANT)
        g.box(min(sx * 47, sx * 51), 11, -3, max(sx * 47, sx * 51), 12, 3, BONE_Y)
        g.box(min(sx * 48, sx * 50), 10, -2, max(sx * 48, sx * 50), 10, 2, SEA)
        g.box(sx * 50, 1, -2, sx * 50, 7, 2, CHB)
        g.box(sx * 50, 2, -1, sx * 50, 6, 1, CRY)
        g.set(sx * 50, 4, 0, SEA)

    # 3b. North High Reliquary Sanctum (x = -16..16, z = -37..-56)
    g.box(-16, 0, -56, 16, 22, -37, PBB)
    g.box(-13, 0, -53, 13, 0, -37, DST)
    g.box(-13, 1, -53, 13, 17, -37, 'air')
    g.box(-2, 0, -52, 2, 0, -35, BONE_Z)

    for z_r in (-39, -43, -47, -51):
        g.box(-13, 17, z_r, 13, 17, z_r, BONE_X)
        for sx in (-12, 12):
            g.box(sx, 1, z_r, sx, 16, z_r, BONE_Y)
            g.set(sx - (1 if sx > 0 else -1), 9, z_r, SLANT)
        g.set(0, 17, z_r, SEA)
        g.set(0, 16, z_r, SLANTH)

    # Raised 1-step dais at z = -45..-51 (y = 1 solid floor, players stand at y = 2)
    g.box(-6, 1, -51, 6, 1, -45, QZB)
    g.box(-4, 1, -49, 4, 1, -46, CHB)
    for sx in (-5, 5):
        g.box(sx, 2, -51, sx, 12, -46, BONE_Y)
        g.set(sx, 13, -48, SEA)
    g.box(-4, 2, -52, 4, 13, -51, BONE_Y)
    g.box(-2, 5, -51, -1, 7, -51, SEA)
    g.box(1, 5, -51, 2, 7, -51, SEA)
    g.box(-1, 2, -51, 1, 4, -51, CRY)

    # =========================================================================
    # 4. GRAND NARTHEX & PROCESSIONAL SPINE CAUSEWAY (+Z, Z = 36..101)
    # =========================================================================
    narthex_outer = g.rect(-22, 36, 22, 66) & (R >= 36.2)
    g.extrude(narthex_outer, 0, 25, PBB)
    narthex_int = g.rect(-19, 39, 19, 64)
    g.extrude(narthex_int, 0, 0, DST)
    g.extrude(narthex_int, 1, 21, 'air')

    # Sunken Cyan Soul-Glass Crypt Panels in the Narthex Side Aisles (x = ±10..11, z = 41..51)
    for sx in (-1, 1):
        xa, xb = min(sx * 10, sx * 11), max(sx * 10, sx * 11)
        g.box(xa, -2, 41, xb, -2, 51, SEA)
        g.box(xa, -1, 41, xb, -1, 51, BONE_Z)
        g.box(xa, 0, 41, xb, 0, 51, CGLASS)

    # Axial Bone Spine Carpet through Narthex and Causeway (Z = 35..101)
    g.extrude(g.rect(-4, 35, 4, 101), 0, 0, PBB)
    g.extrude(g.rect(-2, 35, 2, 101), 0, 0, QZB)
    g.extrude(g.rect(-1, 35, 1, 101), 0, 0, BONE_Z)
    for z_vertebra in range(36, 101, 3):
        g.box(-3, 0, z_vertebra, 3, 0, z_vertebra, BONE_X)
        g.set(0, 0, z_vertebra, SEA)
        g.set(-3, 0, z_vertebra, CHB)
        g.set(3, 0, z_vertebra, CHB)

    # Colossal Vertebrae Colonnades in Narthex (x = ±7, z = 42, 48, 54, 60)
    for z_col in (42, 48, 54, 60):
        for sx in (-7, 7):
            g.box(sx - 1, 1, z_col - 1, sx + 1, 2, z_col + 1, CHB)
            g.box(sx, 3, z_col, sx, 19, z_col, PBAS)
            for y_ring in (4, 8, 12, 16, 19):
                g.box(sx - 1, y_ring, z_col - 1, sx + 1, y_ring, z_col + 1, BONE_Y)
            g.set(sx - (1 if sx > 0 else -1), 6, z_col, SEA)
            g.set(sx - (1 if sx > 0 else -1), 14, z_col, SEA)
        for x_arch in range(-7, 8):
            u_a = abs(x_arch) / 7.0
            y_arch = round(21.0 - 3.0 * (u_a ** 2))
            g.box(x_arch, y_arch, z_col, x_arch, y_arch + 1, z_col, BONE_X)
            if abs(x_arch) in (0, 4):
                g.set(x_arch, y_arch - 1, z_col, SEA)

    # Hanging Soul Chandeliers in Narthex Nave
    for z_ch in (45, 51, 57):
        g.box(0, 18, z_ch, 0, 21, z_ch, CHAIN)
        g.box(-1, 17, z_ch - 1, 1, 17, z_ch + 1, BONE_Y)
        g.set(0, 17, z_ch, SEA)
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            g.set(dx, 16, z_ch + dz, SLANTH)

    # Walkable Grand Staircases in Narthex Side Aisles (x = ±13..15, z = 54..61)
    for sx in (-1, 1):
        xa, xb = min(sx * 13, sx * 15), max(sx * 13, sx * 15)
        g.box(xa, 8, 36, xb, 8, 53, PBB)
        g.box(xa, 9, 36, xb, 16, 53, 'air')
        bx = sx * 13
        g.box(bx, 9, 39, bx, 9, 53, BONE_Z)
        for z_b in range(40, 53, 3):
            g.set(bx, 10, z_b, SLANT)
        for step_i in range(8):
            z_step = 61 - step_i
            y_step = 1 + step_i
            g.box(xa, 0, z_step, xb, y_step, z_step, PBB)
            g.box(xa, y_step, z_step, xb, y_step, z_step, QZB)
            g.box(xa, y_step + 1, z_step, xb, y_step + 7, z_step, 'air')

    # Tiered Catacomb Niches & East/West Narthex Ground-Level Reliquary Shrines (x = ±17..19)
    for sx in (-1, 1):
        # Upper wall catacomb niches at x = sx * 19, y = 11..15, z = 42, 46, 50
        for z_n in (42, 46, 50):
            g.box(sx * 19, 11, z_n - 1, sx * 19, 14, z_n + 1, SSOIL)
            g.set(sx * 19, 11, z_n, BONE_Y)
            g.set(sx * 19, 12, z_n, SEA)
            g.set(sx * 19, 13, z_n, CRY)
        # Ground-level reliquary shrine at z = 49..53
        g.box(min(sx * 16, sx * 19), 0, 49, max(sx * 16, sx * 19), 0, 53, QZB)
        g.box(sx * 19, 1, 49, sx * 19, 7, 53, BONE_Y)
        g.box(sx * 19, 2, 50, sx * 19, 5, 52, CRY)
        g.set(sx * 19, 4, 51, SEA)
        g.set(sx * 18, 6, 49, SLANT)
        g.set(sx * 18, 6, 53, SLANT)

    # Processional Spine Causeway & Maw Portal (Z = 65..101)
    g.box(-7, 0, 64, 7, 0, 67, DST)
    g.box(-2, 0, 64, 2, 0, 67, BONE_Z)
    g.box(-7, 1, 64, 7, 15, 67, 'air')

    g.box(-16, 0, 66, 16, 20, 100, PBB)
    g.box(-13, 0, 66, 13, 0, 99, DST)
    g.box(-13, 1, 66, 13, 18, 99, 'air')
    g.box(-5, 0, 66, 5, 0, 100, PBB)
    g.box(-2, 0, 66, 2, 0, 100, QZB)
    g.box(-1, 0, 66, 1, 0, 100, BONE_Z)
    for z_v in range(66, 100, 3):
        g.box(-4, 0, z_v, 4, 0, z_v, BONE_X)
        g.set(0, 0, z_v, SEA)

    # Sunken Cyan Soul-Glass & Soul-Fire Trenches flanking the Causeway (|X| = 7..10, Z = 69..94)
    for sx in (-1, 1):
        xa, xb = min(sx * 7, sx * 10), max(sx * 7, sx * 10)
        for z_seg0, z_seg1 in ((69, 79), (85, 94)):
            g.box(xa, -2, z_seg0, xb, -2, z_seg1, SEA)
            g.box(xa, -1, z_seg0, xb, -1, z_seg1, BONE_Z)
            g.box(xa, 0, z_seg0, xb, 0, z_seg1, CGLASS)
            for z_f in (z_seg0 + 2, z_seg1 - 2):
                # Raised Soul-Fire Brazier Pedestals at |X| = 5 visible in the approach camera!
                bx = sx * 5
                g.set(bx, 1, z_f, CHB)
                g.set(bx, 2, z_f, BONE_Y)
                g.set(bx, 3, z_f, SEA)
                g.set(bx, 4, z_f, SLANT)

        # Walkable bridge at Z = 80..84 leading to the East/West Causeway Catacomb Alcoves
        g.box(min(sx * 6, sx * 13), 0, 80, max(sx * 6, sx * 13), 0, 84, QZB)
        g.box(min(sx * 6, sx * 13), 1, 80, max(sx * 6, sx * 13), 7, 84, 'air')
        g.box(sx * 13, 1, 80, sx * 13, 7, 84, BONE_Y)
        g.box(sx * 13, 2, 81, sx * 13, 5, 83, CRY)
        g.set(sx * 13, 4, 82, SEA)

    # Colossal Processional Rib Arches spanning the Causeway every 5 blocks (Z = 68, 73, 78, 83, 88, 93)
    for z_rib in (68, 73, 78, 83, 88, 93):
        for x_r in range(-12, 13):
            u_r = abs(x_r) / 12.0
            y_r = round(17.0 - 13.0 * (u_r ** 2))
            y_top = round(18.0 - 10.0 * (u_r ** 2))
            if abs(x_r) >= 7:
                g.box(x_r, 1, z_rib, x_r, max(1, y_r), z_rib, BONE_Y)
            g.box(x_r, y_r, z_rib, x_r, y_top, z_rib, BONE_X if abs(x_r) < 7 else BONE_Y)
            g.set(x_r, y_top + 1, z_rib, SQZ)
        g.set(0, 18, z_rib, SEA)
        g.set(0, 16, z_rib, CHAIN)
        g.set(0, 15, z_rib, SLANTH)
        for sx in (-7, 7):
            g.box(sx, 1, z_rib, sx, 4, z_rib, PBAS)
            g.set(sx, 5, z_rib, BONE_Y)
            g.set(sx, 6, z_rib, SEA)

    # Giant Leviathan Tusk & Skull Portal at the +Z Entrance Mouth (Z = 95..104)
    for sx in (-1, 1):
        for h in range(1, 17):
            tx = sx * max(2, round(8.0 - h * 0.35))
            tz = 97 + (1 if h < 8 else 0)
            g.box(tx - 1, h, tz - 1, tx + 1, h, tz, BONE_Y)
            g.set(tx, h, tz + 1, SQZ)
    g.box(-5, 13, 96, 5, 18, 99, BONE_Y)
    g.box(-3, 15, 96, -2, 16, 99, SEA)
    g.box(2, 15, 96, 3, 16, 99, SEA)
    # Open the entrance portal mouth at the +Z end (Z = 95..104) with a solid floor at y = 0
    g.box(-5, -4, 95, 5, 0, 104, PBB)
    g.box(-2, 0, 95, 2, 0, 104, BONE_Z)
    g.box(-4, 1, 95, 4, 10, 104, 'air')

    # =========================================================================
    # 5. LOWER CATACOMB RING, UPPER OSSUARY TRIFORIUM & COURT FACADE
    #    (Carved AFTER Cardinal Halls & Narthex so arches are never overwritten!)
    # =========================================================================
    # 5a. Lower Catacomb Ring Corridor (R = 37.2..43.2, floor y=0, air y=1..6, ceiling y=7)
    crypt_mask = ((np.abs(X) >= 37) & (np.abs(Z) <= 13)) | ((np.abs(X) <= 13) & (Z <= -37))
    lower_corr = (R >= 37.2) & (R <= 43.2) & (~crypt_mask)
    g.extrude(lower_corr, 0, 0, DST)
    g.extrude(lower_corr, 1, 6, 'air')
    g.extrude((R >= 39.4) & (R <= 41.0) & (~crypt_mask), 0, 0, BONE_Y)
    g.extrude(lower_corr, 7, 8, PBB)

    # Connect Narthex Side Aisles to Lower Catacomb Ring (x = ±11..16, z = 35..41)
    for sx in (-1, 1):
        xa, xb = min(sx * 11, sx * 16), max(sx * 11, sx * 16)
        g.box(xa, 0, 35, xb, 0, 41, DST)
        g.box(xa, 1, 35, xb, 6, 41, 'air')

    # Upper Ossuary Triforium Gallery (R = 36.5..44.0, floor y=8, air y=9..18)
    upper_gal = (R >= 36.5) & (R <= 44.0) & (~crypt_mask)
    g.extrude(upper_gal, 8, 8, PBB)
    g.extrude((R >= 39.2) & (R <= 41.2) & (~crypt_mask), 8, 8, QZB)
    g.extrude(upper_gal, 9, 18, 'air')
    g.extrude(upper_gal, 19, 20, PBB)

    # Connect Narthex Upper Mezzanines (x = ±13..15, z = 35..40) to Upper Ossuary Gallery
    for sx in (-1, 1):
        xa, xb = min(sx * 13, sx * 15), max(sx * 13, sx * 15)
        g.box(xa, 8, 35, xb, 8, 40, QZB)
        g.box(xa, 9, 35, xb, 16, 40, 'air')

    # Lighting & burial niches inside Lower Corridor and Upper Gallery
    for k in range(24):
        deg = k * 15.0
        rad = math.radians(deg)
        mx, mz = round(math.cos(rad) * 40.2), round(math.sin(rad) * 40.2)
        if not ((abs(mx) >= 37 and abs(mz) <= 13) or (abs(mx) <= 13 and mz <= -37)):
            g.set(mx, 0, mz, SEA if k % 2 == 0 else CHB)
            g.set(mx, 7, mz, SEA)
            g.set(mx, 8, mz, SEA if k % 2 == 0 else QZB)
            g.set(mx, 19, mz, SEA)
            if k % 2 == 1:
                g.set(mx, 6, mz, SLANTH)
                g.set(mx, 18, mz, SLANTH)

        # Glowing Catacomb Niches on outer wall of Upper Gallery (R = 44.0..45.2, y = 10..16)
        deg_mid = deg + 7.5
        rm = math.radians(deg_mid)
        ux_, uz_ = round(math.cos(rm) * 44.2), round(math.sin(rm) * 44.2)
        if not ((abs(ux_) >= 37 and abs(uz_) <= 14) or (abs(ux_) <= 14 and uz_ <= -37)):
            g.box(ux_ - 1, 10, uz_ - 1, ux_ + 1, 15, uz_ + 1, BONE_Y)
            g.box(ux_, 11, uz_, ux_, 14, uz_, CRY)
            g.set(ux_, 13, uz_, SEA)

    # 5b. 16 Upper Triforium Pointed Bone Arches opening into the Boss Court (y = 10..17)
    for k in range(16):
        deg = k * 22.5 + 11.25
        w_open = g.wedge(deg, 5.2) & (R >= 34.2) & (R <= 37.5)
        g.extrude(w_open, 10, 15, 'air')
        g.extrude(g.wedge(deg, 3.6) & (R >= 34.2) & (R <= 37.5), 16, 16, 'air')
        g.extrude(g.wedge(deg, 2.0) & (R >= 34.2) & (R <= 37.5), 17, 17, 'air')
        # Bone arch frame & balustrade at R = 35.0..36.0
        bal = g.wedge(deg, 5.2) & (R >= 35.0) & (R <= 36.2)
        g.extrude(bal, 9, 9, BONE_Y)
        rad = math.radians(deg)
        # Central quartz mullion pillar & sea lantern inside each Triforium arch
        mx, mz = round(math.cos(rad) * 35.5), round(math.sin(rad) * 35.5)
        g.box(mx, 10, mz, mx, 16, mz, QZP)
        g.set(mx, 17, mz, SEA)
        for s_ang in (-5.5, 5.5):
            r_a = math.radians(deg + s_ang)
            px, pz = round(math.cos(r_a) * 35.5), round(math.sin(r_a) * 35.5)
            g.box(px, 9, pz, px, 17, pz, BONE_Y)

    # 5c. 16 High Clerestory Bone-Tracery Rose Windows (y = 22..32, R = 34.8..37.2)
    for k in range(16):
        deg = k * 22.5 + 11.25
        if 27.0 <= deg <= 63.0:
            continue  # Skip SE exterior cutaway sector
        rad = math.radians(deg)
        ux, uz = math.cos(rad), math.sin(rad)
        px, pz = -uz, ux
        # Carve recessed arch in the high clerestory wall (R = 34.5..36.8, y = 22..32)
        w_cler = g.wedge(deg, 4.5) & (R >= 34.2) & (R <= 36.5)
        g.extrude(w_cler, 22, 30, 'air')
        g.extrude(g.wedge(deg, 3.0) & (R >= 34.2) & (R <= 36.5), 31, 31, 'air')
        g.extrude(g.wedge(deg, 1.5) & (R >= 34.2) & (R <= 36.5), 32, 32, 'air')
        # Back the clerestory recess at R = 36.8 with Crying Obsidian & glowing Sea Lantern Rose
        for yy in range(22, 32):
            for dw in range(-2, 3):
                bx, bz = round(ux * 36.8 + px * dw), round(uz * 36.8 + pz * dw)
                is_rose = (abs(dw) + abs(yy - 27)) <= 2
                g.set(bx, yy, bz, SEA if is_rose else CRY)
                if is_rose:
                    gx, gz = round(ux * 35.8 + px * dw), round(uz * 35.8 + pz * dw)
                    if gx * gx + gz * gz > COURT_R * COURT_R:
                        g.set(gx, yy, gz, BONE_Y if (dw == 0 or yy == 27) else CGLASS)

    # 5d. 8 Walkable Ground-Level Portals & 8 Blazing Soul-Fire Ossuary Niches (y = 1..7)
    for k in range(16):
        deg = k * 22.5
        rad = math.radians(deg)
        if k % 2 == 0:
            if deg == 90:
                continue  # South Boss Gate handled next
            portal = g.wedge(deg, 5.2) & (R >= 33.5) & (R <= 39.0)
            g.extrude(portal, 0, 0, QZB)
            g.extrude(portal, 1, 6, 'air')
            g.extrude(g.wedge(deg, 3.5) & (R >= 33.5) & (R <= 39.0), 7, 7, 'air')
            for s_ang in (-5.6, 5.6):
                r_a = math.radians(deg + s_ang)
                px, pz = round(math.cos(r_a) * 35.2), round(math.sin(r_a) * 35.2)
                g.box(px, 1, pz, px, 7, pz, BONE_Y)
                g.set(px, 8, pz, SEA)
        else:
            # Ground-level blazing Soul-Fire Ossuary Niche facing the Boss Court
            niche = g.wedge(deg, 3.2) & (R >= 34.5) & (R <= 36.6)
            g.extrude(niche, 2, 6, 'air')
            nx_, nz_ = round(math.cos(rad) * 35.8), round(math.sin(rad) * 35.8)
            g.box(nx_ - 1, 1, nz_ - 1, nx_ + 1, 1, nz_ + 1, CHB)
            g.set(nx_, 2, nz_, SEA)
            g.set(nx_, 3, nz_, SLANT)
            g.set(nx_, 5, nz_, SLANTH)
            bx_, bz_ = round(math.cos(rad) * 36.8), round(math.sin(rad) * 36.8)
            g.box(bx_, 2, bz_, bx_, 6, bz_, CRY)
            g.set(bx_, 4, bz_, SEA)

    # 5e. Monumental South Boss Gate (Z = 33..39, width = 9, height = 12)
    gate_mask = (np.abs(X) <= 4) & (Z >= 33) & (Z <= 39)
    g.extrude(gate_mask, 0, 0, QZB)
    g.extrude((np.abs(X) <= 1) & (Z >= 33) & (Z <= 39), 0, 0, BONE_Z)
    g.extrude(gate_mask, 1, 12, 'air')
    for dy, hw in ((13, 3), (14, 2), (15, 1)):
        g.extrude((np.abs(X) <= hw) & (Z >= 33) & (Z <= 39), dy, dy, 'air')
    for sx in (-5, 5):
        g.box(sx, 1, 35, sx, 14, 38, BONE_Y)
        g.box(sx, 4, 35, sx, 11, 37, SQZ)
        g.set(sx, 8, 35, SEA)
    for dy, hw in ((13, 4), (14, 3), (15, 2), (16, 1)):
        for sx in (-hw, hw):
            g.box(sx, dy, 35, sx, dy, 38, BONE_Y)
    g.box(-2, 16, 35, 2, 19, 38, BONE_Y)
    g.set(-1, 17, 35, SEA)
    g.set(1, 17, 35, SEA)
    g.set(-1, 17, 38, SEA)
    g.set(1, 17, 38, SEA)

    # =========================================================================
    # 6. BOSS COURT FLOOR MOSAIC (R <= 34, y = 0)
    # =========================================================================
    court = R <= COURT_R
    g.extrude(court, 0, 0, PBB)
    cf = hash2(X, Z, 61)
    g.extrude(court & (cf > 0.72), 0, 0, DST)
    g.extrude(court & (cf < 0.14), 0, 0, CPBB)

    for r0, r1, mat in (
        (32.5, 34.1, QZB),
        (31.2, 32.5, BONE_Y),
        (28.5, 31.2, SSOIL),
        (27.3, 28.5, CHB),
        (21.2, 22.6, BONE_Y),
        (19.0, 21.2, SSOIL),
        (17.8, 19.0, CHB),
        (11.0, 12.4, BONE_Y),
        (9.2, 11.0, CRY),
        (7.8, 9.2, QZB),
        (3.6, 5.2, BONE_Y),
        (2.0, 3.6, SEA),
        (0.0, 2.0, CHB),
    ):
        g.extrude(court & (R >= r0) & (R < r1), 0, 0, mat)

    spoke_primary = (np.abs(((TH + 22.5) % 45.0) - 22.5) < 2.2) & (R >= 5.0) & (R <= 33.0)
    spoke_secondary = (np.abs(((TH + 11.25) % 22.5) - 11.25) < 1.1) & (R >= 8.5) & (R <= 31.5)
    g.extrude(court & spoke_secondary, 0, 0, QZB)
    g.extrude(court & spoke_primary, 0, 0, BONE_Y)

    for k in range(8):
        rad = math.radians(k * 45.0)
        ux, uz = math.cos(rad), math.sin(rad)
        px, pz = -uz, ux
        for rr in (7, 11, 15, 19, 23, 27, 31):
            cx, cz = round(ux * rr), round(uz * rr)
            for w in (-2, -1, 0, 1, 2):
                wx, wz = round(cx + px * w), round(cz + pz * w)
                if wx * wx + wz * wz <= COURT_R * COURT_R:
                    g.set(wx, 0, wz, SQZ if abs(w) == 2 else BONE_Y)
            if rr in (11, 19, 27):
                g.set(cx, 0, cz, SEA)

    for k in range(16):
        rad = math.radians(k * 22.5 + 11.25)
        for rr, lmat in ((6.5, SEA), (15.0, SEA), (25.0, SEA), (30.0, SHROOM if k % 2 == 0 else SEA)):
            lx, lz = round(math.cos(rad) * rr), round(math.sin(rad) * rr)
            if lx * lx + lz * lz <= COURT_R * COURT_R:
                g.set(lx, 0, lz, lmat)
    g.set(0, 0, 0, SEA)

    # =========================================================================
    # 7. COLOSSAL LEVIATHAN RIB-CAGE CANOPY & DORSAL SPINE
    #    (Perimeter R >= 35 for y <= 40; curving overhead at y = 41..58!)
    # =========================================================================
    for k in range(16):
        deg = k * 22.5
        rad = math.radians(deg)
        ux, uz = math.cos(rad), math.sin(rad)
        px, pz = -uz, ux

        y_start = 19 if deg == 90 else (9 if k % 2 == 0 else 1)
        y_end = 20 if k == 2 else 40
        for yy in range(y_start, y_end + 1):
            t_low = (yy - 1) / 39.0
            r_mid = 35.8 if yy <= 19 else (37.5 - 2.0 * t_low)
            drs = (0.0, 1.0) if yy <= 19 else (-0.6, 0.0, 1.0, 2.0)
            for dr in drs:
                for dw in (-1, 0, 1):
                    rx = round(ux * (r_mid + dr) + px * dw)
                    rz = round(uz * (r_mid + dr) + pz * dw)
                    if rx * rx + rz * rz <= COURT_R * COURT_R:
                        continue
                    if dw == 0 and dr <= 0.5:
                        mat = SQZ if yy % 4 == 0 else BONE_Y
                    elif abs(dw) == 1 and dr <= 0.0:
                        mat = BONE_Y
                    else:
                        mat = CHB if yy % 6 == 0 else PBB
                    g.set(rx, yy, rz, mat)
            if yy in (5, 11, 17, 23, 29, 35, 39):
                lx, lz = round(ux * 35.1), round(uz * 35.1)
                if lx * lx + lz * lz > COURT_R * COURT_R:
                    g.set(lx, yy, lz, SEA)

        # Upper Colossal Rib Arch above the clear cylinder (y = 41..55, sweeping from R = 35.5 to R = 8.5)
        steps = 44
        for s in range(steps + 1):
            t = s / float(steps)
            rr = 35.5 * (1.0 - t) + 8.5 * t
            yy_f = 41.0 + 13.5 * math.sin(t * math.pi * 0.58)
            yc = round(yy_f)
            for dy in (-1, 0, 1, 2):
                wy = max(41, yc + dy)
                for dw in (-1, 0, 1):
                    if abs(dw) == 1 and dy in (-1, 2):
                        continue
                    rx = round(ux * rr + px * dw)
                    rz = round(uz * rr + pz * dw)
                    if dy == 2:
                        mat = CHB if s % 4 == 0 else QZB
                    elif dy == -1:
                        mat = SEA if (s % 6 == 3 and dw == 0) else SQZ
                    else:
                        mat = BONE_Y if abs(ux) < 0.7 and abs(uz) < 0.7 else (BONE_X if abs(ux) >= abs(uz) else BONE_Z)
                    g.set(rx, wy, rz, mat)
            if s in (10, 22, 34):
                hx, hz = round(ux * rr), round(uz * rr)
                if yc - 2 >= 42:
                    g.set(hx, yc - 2, hz, CHAIN)
                    g.set(hx, yc - 3, hz, SLANTH)

    # Transverse Leviathan Chest-Ribs (East-West ribs at z = -22, -11, 0, 11, 22) above y = 41
    for z_rib in (-22, -11, 0, 11, 22):
        max_x = math.sqrt(max(1.0, 36.0 ** 2 - z_rib ** 2))
        for sx_step in range(-40, 41):
            t = sx_step / 40.0
            xx = t * max_x
            yc = round(55.0 - 13.0 * (t ** 2))
            for dz in (-1, 0, 1):
                for dy in (0, 1, 2):
                    wy = max(41, yc + dy)
                    if abs(dz) == 1 and dy == 2:
                        continue
                    rx, rz = round(xx), z_rib + dz
                    if dy == 2:
                        mat = SQZ if abs(sx_step) % 3 == 0 else BONE_Y
                    elif dy == 0 and dz == 0 and abs(sx_step) % 5 == 0:
                        mat = SEA
                    else:
                        mat = BONE_X
                    g.set(rx, wy, rz, mat)

    # Concentric Sternum & Oculus Bone Rings at y = 46..48 (R = 23..25) and y = 53..55 (R = 8..10)
    g.extrude((R >= 22.8) & (R <= 25.2), 46, 47, BONE_Y)
    g.extrude((R >= 23.5) & (R <= 24.5), 45, 45, SQZ)
    g.extrude((R >= 7.8) & (R <= 10.2), 53, 55, BONE_Y)
    g.extrude((R >= 8.5) & (R <= 9.5), 52, 52, SEA)

    # Colossal Central Dorsal Spine running North-South along x = 0, z = -34..34 at y = 53..58
    for zz in range(-34, 35):
        u_z = zz / 34.0
        y_spine = round(55.0 - 3.0 * (u_z ** 2))
        is_vert = (zz % 4 == 0)
        hw = 2 if is_vert else 1
        g.box(-hw, y_spine - 1, zz, hw, y_spine + 1, zz, BONE_Z if not is_vert else BONE_X)
        if is_vert:
            g.box(0, y_spine + 2, zz - 1, 0, y_spine + 3, zz + 1, BONE_Y)
            g.set(0, y_spine + 3, zz, SQZ)
            g.set(0, y_spine - 2, zz, SEA)
            g.set(-2, y_spine, zz, CRY)
            g.set(2, y_spine, zz, CRY)

    # =========================================================================
    # 8. TREASURE CHESTS (9 Chests: 5 Major tier=0 + 4 Minor tier=1)
    # =========================================================================
    # 1. North High Reliquary Sanctum Altar (tier=0)
    g.add_chest(0, 2, -47, 'south', 0)
    # 2 & 3. East & West Ossuary Crypt Main Shrines (tier=0)
    g.set(47, 0, 0, CHB)
    g.add_chest(47, 1, 0, 'west', 0)
    g.set(-47, 0, 0, CHB)
    g.add_chest(-47, 1, 0, 'east', 0)
    # 4 & 5. East & West Upper Ossuary Gallery Reliquaries (tier=0)
    g.box(13, 8, 43, 15, 8, 45, QZB)
    g.set(15, 8, 44, CHB)
    g.add_chest(15, 9, 44, 'west', 0)
    g.box(-15, 8, 43, -13, 8, 45, QZB)
    g.set(-15, 8, 44, CHB)
    g.add_chest(-15, 9, 44, 'east', 0)
    # 6 & 7. East & West Narthex Reliquary Shrines (tier=1)
    g.set(17, 0, 51, CHB)
    g.add_chest(17, 1, 51, 'west', 1)
    g.set(-17, 0, 51, CHB)
    g.add_chest(-17, 1, 51, 'east', 1)
    # 8 & 9. East & West Processional Causeway Catacomb Alcoves (tier=1)
    g.set(11, 0, 82, CHB)
    g.add_chest(11, 1, 82, 'west', 1)
    g.set(-11, 0, 82, CHB)
    g.add_chest(-11, 1, 82, 'east', 1)

    # =========================================================================
    # 9. STRICT ARENA CLEARANCE & WALKABILITY GUARANTEE
    # =========================================================================
    g.extrude(R <= COURT_R, 1, 40, 'air')
    g.extrude(R <= COURT_R, 0, 0, PBB, only='empty')

    return g


VIEWS = {
    'exterior': dict(
        cam=(38, 36, 40),
        target=(-6, 18, -6),
        fov=80,
        size=(1600, 900),
        ambient=0.28,
        light_boost=0.08,
        gamma=0.85,
        exposure=1.08,
        fog=(0.0022, (10, 22, 28)),
        tint_mul=(0.92, 1.02, 1.08),
    ),
    'approach': dict(
        cam=(0, 5, 92),
        target=(0, 8, 34),
        fov=76,
        size=(1600, 900),
        ambient=0.28,
        light_boost=0.08,
        gamma=0.85,
        exposure=1.08,
        fog=(0.0022, (10, 22, 28)),
        tint_mul=(0.92, 1.02, 1.08),
    ),
    'ossuary': dict(
        cam=(38, 6, -9),
        target=(49, 4, 2),
        fov=78,
        size=(1600, 900),
        ambient=0.28,
        light_boost=0.08,
        gamma=0.85,
        exposure=1.08,
        fog=(0.0022, (10, 22, 28)),
        tint_mul=(0.92, 1.02, 1.08),
    ),
    'court': dict(
        cam=(0, 5, 29),
        target=(0, 22, -18),
        fov=84,
        size=(1600, 900),
        ambient=0.28,
        light_boost=0.08,
        gamma=0.85,
        exposure=1.08,
        fog=(0.0022, (10, 22, 28)),
        tint_mul=(0.92, 1.02, 1.08),
    ),
}


if __name__ == '__main__':
    build()
