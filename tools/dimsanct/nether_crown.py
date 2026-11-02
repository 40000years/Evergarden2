"""Crown Fortress (Nether): a blackstone/basalt crown ring standing in its own lava cavern.

Local frame: site centre (0, 0), floor y=0, entrance on +Z (south). The whole building is
authored inside a sealed cavern shell so it cannot leak lava, never touches the Nether roof
and does not depend on natural cave shapes. Boss court: radius 35 (71 blocks across), clear to y=42.
"""
from __future__ import annotations

import math
import numpy as np

from .grid import Grid, hash2, hash3, fbm, vnoise

# ---- materials ----
BS, PBS, PBB, CPBB, CHB = (
    'blackstone',
    'polished_blackstone',
    'polished_blackstone_bricks',
    'cracked_polished_blackstone_bricks',
    'chiseled_polished_blackstone',
)
GBS = 'gilded_blackstone'
BAS, PBAS, SBAS = 'basalt[axis=y]', 'polished_basalt[axis=y]', 'smooth_basalt'
RNB, NB, CNB, CHNB = (
    'red_nether_bricks',
    'nether_bricks',
    'cracked_nether_bricks',
    'chiseled_nether_bricks',
)
NR, MAGMA, SHROOM, CRY, OBS, GOLD = (
    'netherrack',
    'magma_block',
    'shroomlight',
    'crying_obsidian',
    'obsidian',
    'gold_block',
)
LAVA = 'lava[level=0]'
LANT = 'lantern[hanging=false,waterlogged=false]'
LANTH = 'lantern[hanging=true,waterlogged=false]'
SLANT = 'soul_lantern[hanging=false,waterlogged=false]'
SLANTH = 'soul_lantern[hanging=true,waterlogged=false]'
CHAIN = 'iron_chain[axis=y,waterlogged=false]'


def stair(mat, facing, half='bottom'):
    return f'{mat}_stairs[facing={facing},half={half},shape=straight,waterlogged=false]'


def slab(mat, kind='bottom'):
    return f'{mat}_slab[type={kind},waterlogged=false]'


X0, X1, Y0, Y1, Z0, Z1 = -70, 70, -14, 68, -70, 108
COURT_R = 35
WALL_OUT = 47
WALL_TOP = 24
MOAT_IN, MOAT_OUT = 53, 61
CAV_R = 66
LAVA_TOP = -2
ARENA_H = 42


def build() -> Grid:
    g = Grid(X0, X1, Y0, Y1, Z0, Z1)
    X, Z, R, TH = g.X, g.Z, g.R, g.TH
    Yv = np.arange(Y0, Y1 + 1)[None, :, None]
    air3 = np.zeros(g.a.shape, bool)

    # =============== 1. grand underground cavern & shell ===============
    Rn = R + (fbm(X, Z, 10, 1) - 0.5) * 3.5
    # High vaulted cavern so the 12 crown spires stand clear inside the subterranean dome
    Hc = 32 + 31 * np.sqrt(np.clip(1.0 - (Rn / CAV_R) ** 2, 0.0, 1.0)) + (fbm(X, Z, 14, 2) - 0.5) * 4.0
    cav3 = (Rn <= CAV_R)[:, None, :] & (Yv >= 1) & (Yv <= Hc[:, None, :])
    air3 |= cav3

    # Entrance tunnel (+Z, Z=56..98) and outer portal vestibule (Z=98..104)
    tun_x = np.abs(X) <= 7
    tun = (tun_x & (Z >= 56) & (Z <= 104))[:, None, :] & (Yv >= 1) & (Yv <= 12)
    arch = (tun_x & (Z >= 56) & (Z <= 104))[:, None, :] & (Yv >= 9) & (Yv <= 12)
    rr = np.hypot(X[:, None, :] * np.ones_like(Yv), (Yv - 8) * np.ones_like(X[:, None, :]))
    tun &= ~(arch & (rr > 7.5))
    air3 |= tun

    # 3-block thick sealed cavern shell
    shell = g.dilate(air3, 3) & ~air3
    # Open the +Z portal mouth at Z >= 103 so players can walk in from the Nether
    mouth_open = (np.abs(X) <= 5)[:, None, :] & (Z >= 102)[:, None, :] & (Yv >= 1) & (Yv <= 9)
    shell &= ~mouth_open

    sh = hash3(
        g.X[:, None, :] * np.ones_like(Yv),
        Yv * np.ones_like(g.X[:, None, :]),
        g.Z[:, None, :] * np.ones_like(Yv),
        7,
    )
    veins = vnoise(
        g.X[:, None, :] * np.ones_like(Yv) + Yv * 3,
        g.Z[:, None, :] * np.ones_like(Yv) + Yv * 2,
        5.5,
        11,
    )
    for lo, hi, mat in (
        (0.00, 0.45, BS),
        (0.45, 0.66, BAS),
        (0.66, 0.78, SBAS),
        (0.78, 0.90, PBS),
        (0.90, 0.97, RNB),
        (0.97, 1.01, GBS),
    ):
        g.where3(shell & (sh >= lo) & (sh < hi), mat)
    g.where3(shell & (veins > 0.79), MAGMA)
    # Glowing ceiling ribs / shroomlight clusters embedded in the upper cavern shell
    g.where3(shell & (Yv >= 46) & (veins > 0.74) & (sh > 0.65), SHROOM)
    g.where3(air3 | mouth_open, 'air')

    # Solid foundation mass under the cavern floor
    depth = -(7 + 5 * np.clip(1.0 - (R / 70.0) ** 2, 0.0, 1.0) + (fbm(X, Z, 8, 3) - 0.5) * 2.0).astype(int)
    floor2 = (R <= 68) | ((np.abs(X) <= 10) & (Z >= 56) & (Z <= 105))
    depth = np.where((np.abs(X) <= 10) & (Z >= 56), -7, depth)
    mass = floor2[:, None, :] & (Yv <= 0) & (Yv >= depth[:, None, :])
    sh2 = hash3(
        g.X[:, None, :] * np.ones_like(Yv),
        Yv * np.ones_like(g.X[:, None, :]),
        g.Z[:, None, :] * np.ones_like(Yv),
        9,
    )
    g.where3(mass & (Yv < 0), BS)
    g.where3(mass & (Yv < 0) & (sh2 > 0.55), BAS)
    g.where3(mass & (Yv < -2) & (sh2 > 0.82), SBAS)

    # Default floor surface at y=0
    fl = hash2(X // 2, Z // 2, 5)
    surf = np.full(X.shape, PBB, dtype=object)
    for lo, hi, mat in (
        (0.00, 0.34, PBB),
        (0.34, 0.52, BS),
        (0.52, 0.68, CPBB),
        (0.68, 0.82, PBS),
        (0.82, 0.92, SBAS),
        (0.92, 1.01, GBS),
    ):
        surf[(fl >= lo) & (fl < hi)] = mat
    for mat in set(surf[floor2].tolist()):
        g.extrude(floor2 & (surf == mat), 0, 0, mat)

    # Outer terrace between wall and moat (R=48..52) and outer cavern rim (R=62..65)
    g.extrude((R >= 47.5) & (R <= 52.5), 0, 0, PBB)
    g.extrude((np.abs(R - 50.0) < 0.7), 0, 0, RNB)
    for k in range(24):
        deg = 7.5 + 15.0 * k
        if abs(((deg - 90.0 + 180.0) % 360.0) - 180.0) < 16.0:
            continue
        a = math.radians(deg)
        lx, lz = round(math.cos(a) * 50.0), round(math.sin(a) * 50.0)
        g.set(lx, 0, lz, SHROOM)

    # =============== 2. sealed lava moat (R=53..61) ===============
    moat = (R >= MOAT_IN) & (R <= MOAT_OUT)
    # Seal basin bottom and sides before placing lava
    g.extrude((R >= MOAT_IN - 2) & (R <= MOAT_OUT + 2), -7, -1, PBB)
    g.extrude(moat, -5, LAVA_TOP, LAVA)
    g.extrude(moat, LAVA_TOP + 1, 0, 'air')
    # Ornate rim curbs at R=52 and R=62
    g.extrude((np.abs(R - (MOAT_IN - 0.8)) < 0.8), 0, 0, CHB)
    g.extrude((np.abs(R - (MOAT_OUT + 0.8)) < 0.8), 0, 0, CHB)
    # Glowing magma islands / pillars rising from the moat (sealed around lava)
    for k in range(12):
        deg = 15.0 + 30.0 * k
        if abs(((deg - 90.0 + 180.0) % 360.0) - 180.0) < 20.0:
            continue
        a = math.radians(deg)
        mx, mz = round(math.cos(a) * 57.0), round(math.sin(a) * 57.0)
        g.box(mx - 1, -6, mz - 1, mx + 1, 2, mz + 1, PBS)
        g.box(mx, 3, mz, mx, 7, mz, PBAS)
        g.set(mx, 8, mz, SHROOM)
        g.set(mx, 9, mz, slab(PBB, 'bottom'))

    # =============== 3. main crown ring wall & 12 crown spires ===============
    wall = (R >= COURT_R) & (R <= WALL_OUT)
    g.extrude(wall, 1, WALL_TOP, PBB)
    nz = hash3(
        g.X[:, None, :] * np.ones_like(Yv),
        Yv * np.ones_like(g.X[:, None, :]),
        g.Z[:, None, :] * np.ones_like(Yv),
        21,
    )
    wall3 = wall[:, None, :] & (Yv >= 1) & (Yv <= WALL_TOP)
    g.where3(wall3 & (nz > 0.86), CPBB)
    g.where3(wall3 & (nz > 0.72) & (nz <= 0.86), PBS)

    # Horizontal crimson & gilded bands on both inner and outer faces of the ring wall
    faces = ((R >= WALL_OUT - 1.5) & (R <= WALL_OUT)) | ((R >= COURT_R) & (R <= COURT_R + 1.5))
    for y in (5, 6, 15, 16, 22):
        g.extrude(faces, y, y, RNB)
    for y in (9, 20, WALL_TOP):
        g.extrude(faces, y, y, CHB)
    g.extrude(faces, 21, 21, GBS)

    # 24 vertical basalt ribs on the outer & inner ring faces
    for k in range(24):
        deg = 15.0 * k
        if abs(((deg - 90.0 + 180.0) % 360.0) - 180.0) < 18.0:
            continue
        a = math.radians(deg)
        # Outer buttress
        ox, oz = round(math.cos(a) * (WALL_OUT + 1.0)), round(math.sin(a) * (WALL_OUT + 1.0))
        g.box(ox - 1, 1, oz - 1, ox + 1, 18, oz + 1, PBS)
        g.box(ox, 2, oz, ox, 22, oz, PBAS)
        g.set(ox, 23, oz, SHROOM)
        # Inner court pilaster (outside R=35 clear cylinder: at R=36)
        ix, iz = round(math.cos(a) * (COURT_R + 0.8)), round(math.sin(a) * (COURT_R + 0.8))
        if math.hypot(ix, iz) > COURT_R + 0.2:
            g.box(ix, 1, iz, ix, 23, iz, PBAS)
            g.set(ix, 10, iz, SHROOM)
            g.set(ix, 21, iz, SHROOM)

    # THE CROWN: 12 monumental jagged spires rising from the ring wall (y=25..45)
    # plus 12 secondary tines at the mid-angles, forming a true imperial crown silhouette.
    for k in range(24):
        deg = 15.0 * k
        a = math.radians(deg)
        major = (k % 2 == 0)
        peak = 44 if major else 34
        base_w = 3.6 if major else 2.4
        cx, cz = math.cos(a) * 41.0, math.sin(a) * 41.0
        for y in range(WALL_TOP + 1, peak + 1):
            t = (y - WALL_TOP) / float(peak - WALL_TOP)
            rad = max(0.8, base_w * (1.0 - 0.72 * t))
            # Slight inward & upward crown flair, always strictly outside COURT_R
            shift = 41.0 - 2.0 * math.sin(t * math.pi)
            sx, sz = math.cos(a) * shift, math.sin(a) * shift
            m = (np.hypot(X - sx, Z - sz) <= rad) & (R > COURT_R + 0.5)
            mat = PBB if y % 4 != 0 else (RNB if y % 8 == 0 else CHB)
            g.extrude(m, y, y, mat)
        # Crown jewel in the face of each major spire
        if major:
            jx, jz = round(math.cos(a) * 38.2), round(math.sin(a) * 38.2)
            if math.hypot(jx, jz) > COURT_R + 1.0:
                g.box(jx - 1, 28, jz - 1, jx + 1, 34, jz + 1, GBS)
                g.box(jx, 29, jz, jx, 33, jz, SHROOM)
            tx, tz = round(math.cos(a) * 40.5), round(math.sin(a) * 40.5)
            g.box(tx, peak + 1, tz, tx, peak + 2, tz, SHROOM)

    # Crenelated crown parapet connecting the spires at y=25..28
    crown_band = (R >= 36.5) & (R <= 45.5)
    g.extrude(crown_band, 25, 26, PBS)
    g.extrude((np.abs(R - 37.0) < 0.7) | (np.abs(R - 45.0) < 0.7), 27, 27, RNB)

    # =============== 4. interior ring corridor, upper gallery & treasure halls ===============
    # Ground ring corridor: R=39..43, y=1..7
    corr = (R >= 39.0) & (R <= 43.5)
    g.extrude(corr, 0, 0, PBB)
    g.extrude((np.abs(R - 41.2) < 0.8), 0, 0, RNB)
    g.extrude(corr, 1, 7, 'air')
    for k in range(24):
        a = math.radians(7.5 + 15.0 * k)
        lx, lz = round(math.cos(a) * 41.2), round(math.sin(a) * 41.2)
        g.set(lx, 0, lz, SHROOM)
        g.set(lx, 8, lz, SHROOM)

    # Ceiling / upper gallery floor at y=8..9
    g.extrude((R >= 36.5) & (R <= 45.5), 8, 9, PBB)
    # Upper gallery: R=38..44, y=10..18
    gal = (R >= 38.0) & (R <= 44.0)
    g.extrude(gal, 9, 9, PBB)
    g.extrude((np.abs(R - 41.0) < 0.8), 9, 9, RNB)
    g.extrude(gal, 10, 18, 'air')

    # Grand arched gallery openings looking onto the boss court every 15 deg
    for k in range(24):
        deg = 7.5 + 15.0 * k
        if abs(((deg - 90.0 + 180.0) % 360.0) - 180.0) < 20.0:
            continue
        w = g.wedge(deg, 3.2) & (R >= COURT_R) & (R <= 39.0)
        g.extrude(w, 11, 16, 'air')
        g.extrude(g.wedge(deg, 2.0) & (R >= COURT_R) & (R <= 39.0), 17, 17, 'air')
        # Safe parapet at y=10 so players on the gallery can look into the court
        g.extrude(w & (R >= 36.0) & (R <= 37.2), 10, 10, CHB)
        a = math.radians(deg)
        lx, lz = round(math.cos(a) * 41.0), round(math.sin(a) * 41.0)
        g.set(lx, 9, lz, SHROOM)
        g.set(lx, 18, lz, LANTH)

    # Ground-level arched windows/embrasures from the ring corridor into the court
    for k in range(24):
        deg = 7.5 + 15.0 * k
        if abs(((deg - 90.0 + 180.0) % 360.0) - 180.0) < 20.0:
            continue
        if k % 2 == 1:
            w = g.wedge(deg, 2.2) & (R >= COURT_R) & (R <= 39.5)
            g.extrude(w, 2, 5, 'air')
            g.extrude(w, 1, 1, CHB)

    # Cardinal & diagonal rooms off the ground ring corridor
    # East Hall (0 deg), West Hall (180 deg), North Royal Reliquary (270 deg),
    # plus 4 Diagonal Vaults (45, 135, 225, 315 deg).
    # First carve 4 cardinal portals connecting the ring corridor directly to the boss court:
    for deg in (0.0, 180.0, 270.0):
        portal = g.wedge(deg, 4.5) & (R >= COURT_R - 0.5) & (R <= 40.0)
        g.extrude(portal, 0, 0, RNB)
        g.extrude(portal, 1, 6, 'air')
        g.extrude(g.wedge(deg, 3.0) & (R >= COURT_R - 0.5) & (R <= 40.0), 7, 7, 'air')

    # Carve & furnish the 7 exploration chambers in the outer ring (R=38..45.5)
    chamber_specs = [
        (0.0, 10.0, 'war_east'),
        (180.0, 10.0, 'war_west'),
        (270.0, 14.0, 'reliquary_north'),
        (45.0, 7.5, 'vault_se'),
        (135.0, 7.5, 'vault_sw'),
        (225.0, 7.5, 'vault_nw'),
        (315.0, 7.5, 'vault_ne'),
    ]
    for deg, half, name in chamber_specs:
        room = g.wedge(deg, half) & (R >= 38.0) & (R <= 45.2)
        g.extrude(room, 0, 0, RNB)
        g.extrude(room, 1, 7, 'air')
        a = math.radians(deg)
        rx, rz = round(math.cos(a) * 43.5), round(math.sin(a) * 43.5)
        # Ensure floor and clear standing area around the chest
        g.box(rx - 2, 0, rz - 2, rx + 2, 0, rz + 2, GBS)
        g.box(rx - 2, 1, rz - 2, rx + 2, 6, rz + 2, 'air')
        g.set(rx, 7, rz, SHROOM)
        # Facing toward the centre of the structure
        if abs(rx) >= abs(rz):
            facing = 'west' if rx > 0 else 'east'
        else:
            facing = 'south' if rz < 0 else 'north'
        tier = 0 if ('war' in name or 'reliquary' in name) else 1
        g.add_chest(rx, 1, rz, facing, tier)

    # Extra two royal chests flanking the North Reliquary altar (deg=270, Z around -43)
    for sx in (-6, 6):
        g.box(sx - 1, 0, -44, sx + 1, 0, -41, GBS)
        g.box(sx - 1, 1, -44, sx + 1, 6, -41, 'air')
        g.set(sx, 7, -43, SHROOM)
        g.add_chest(sx, 1, -43, 'south', 0)

    # =============== 5. boss court (R <= 35, 71 blocks across, clear to y=42) ===============
    court = R <= COURT_R
    g.extrude(court, 0, 0, PBB)
    cf = hash2(X, Z, 31)
    g.extrude(court & (cf > 0.90), 0, 0, CPBB)
    g.extrude(court & (cf < 0.08), 0, 0, PBS)

    # Imperial Crown Floor Medallion: 12 spokes, concentric rings, 12 crown-star points, gold-and-obsidian heart
    spoke = (np.abs(((TH + 15.0) % 30.0) - 15.0) < 1.3) & (R > 5.0) & (R < 33.5)
    g.extrude(spoke, 0, 0, RNB)
    for r0, r1, m in (
        (33.2, 35.1, RNB),
        (31.8, 33.2, CHB),
        (24.0, 25.6, RNB),
        (22.8, 24.0, GBS),
        (14.0, 15.5, RNB),
        (7.0, 8.4, GBS),
        (5.6, 7.0, CHB),
        (2.5, 4.0, CRY),
    ):
        g.extrude((R >= r0) & (R < r1), 0, 0, m)
    g.extrude(R < 2.5, 0, 0, GOLD)
    pts = (
        (np.abs((TH % 30.0) - 15.0) < 4.5)
        & (R >= 15.5)
        & (R <= 22.5)
        & (np.abs((TH % 30.0) - 15.0) < (22.5 - R) * 0.75)
    )
    g.extrude(pts, 0, 0, RNB)

    # Flush floor shroomlights in 4 concentric rings so the whole 71-block court gleams warmly
    g.set(0, 0, 0, SHROOM)
    for k in range(12):
        a = math.radians(15.0 + 30.0 * k)
        for rr_ in (9.5, 18.5, 28.0, 33.8):
            cx, cz = round(math.cos(a) * rr_), round(math.sin(a) * rr_)
            if math.hypot(cx, cz) <= COURT_R:
                g.set(cx, 0, cz, SHROOM)
    for k in range(12):
        a = math.radians(30.0 * k)
        for rr_ in (14.5, 24.8):
            cx, cz = round(math.cos(a) * rr_), round(math.sin(a) * rr_)
            if math.hypot(cx, cz) <= COURT_R:
                g.set(cx, 0, cz, SHROOM)

    # Cavern ceiling chandelier & ribs above the clear arena cylinder (y >= 44)
    chand_r = R <= 18.0
    g.extrude((np.abs(R - 14.0) < 1.2), 45, 47, PBS)
    g.extrude((np.abs(R - 14.0) < 0.8), 44, 44, SHROOM)
    g.extrude((np.abs(R - 7.0) < 1.0), 46, 48, GBS)
    g.extrude((np.abs(R - 7.0) < 0.7), 45, 45, SHROOM)
    g.extrude(R <= 3.0, 44, 49, PBS)
    g.extrude(R <= 2.0, 43, 43, SHROOM)

    # =============== 6. South Gatehouse Hall, Bridge & Entrance Causeway (+Z) ===============
    # Gatehouse exterior mass: X in -15..15, Z in 36..52, y=0..24
    hx0, hx1, hz0, hz1 = -15, 15, 36, 52
    hall_o = g.rect(hx0, hz0, hx1, hz1) & (R > COURT_R)
    g.extrude(hall_o, 0, 0, PBB)
    g.extrude(hall_o, 1, 21, PBB)
    g.extrude(hall_o, 22, 24, PBS)

    # Twin gatehouse towers flanking the south portal (X = ±12, Z = 46..52, rising to y=36)
    for sx in (-12, 12):
        g.box(sx - 3, 1, 46, sx + 3, 32, 52, PBB)
        g.box(sx - 2, 2, 47, sx + 2, 34, 51, PBAS)
        g.box(sx - 3, 28, 46, sx + 3, 29, 52, RNB)
        g.box(sx - 2, 35, 47, sx + 2, 36, 51, GBS)
        g.set(sx, 37, 49, SHROOM)
        g.set(sx, 20, 52, SHROOM)

    # Gatehouse interior narthex: X in -11..11, Z in 38..49, y=1..16
    hall_i = g.rect(-11, 38, 11, 49) & (R > COURT_R)
    g.extrude(hall_i, 0, 0, PBB)
    g.extrude(hall_i, 1, 16, 'air')
    for z in range(39, 49, 3):
        g.box(-11, 16, z, 11, 16, z, RNB)
        g.set(-6, 0, z, SHROOM)
        g.set(6, 0, z, SHROOM)
        g.set(0, 16, z, SHROOM)

    # Wide connecting passages from the Gatehouse narthex into the East & West ring corridors
    for side in (-1, 1):
        px0, px1 = min(side * 9, side * 16), max(side * 9, side * 16)
        g.box(px0, 0, 38, px1, 0, 43, PBB)
        g.box(px0, 1, 38, px1, 7, 43, 'air')

    # Walkable grand staircases inside the Gatehouse rising from y=1 (Z=47) to the upper gallery y=10 (Z=38)
    for side in (-1, 1):
        sx0, sx1 = min(side * 9, side * 11), max(side * 9, side * 11)
        # Clear headroom above stairs all the way into the upper gallery
        g.box(sx0, 1, 36, sx1, 16, 48, 'air')
        for step_i in range(9):
            z = 47 - step_i
            y_step = 1 + step_i
            g.box(sx0, 0, z, sx1, y_step - 1, z, PBB)
            g.box(sx0, y_step, z, sx1, y_step, z, stair('polished_blackstone_brick', 'north'))
        # Landing at Z=36..38 at y=9 (so feet are at y=10, flush with the upper gallery floor!)
        g.box(sx0, 8, 36, sx1, 9, 38, PBB)
        g.box(sx0, 10, 36, sx1, 15, 38, 'air')

    # Two upper gallery treasure chests on the South-East & South-West gallery overlooks
    for side in (-1, 1):
        cx, cz = side * 14, 39
        g.box(cx - 2, 9, cz - 2, cx + 2, 9, cz + 2, GBS)
        g.box(cx - 2, 10, cz - 2, cx + 2, 16, cz + 2, 'air')
        g.set(cx, 17, cz, SHROOM)
        g.add_chest(cx, 10, cz, 'west' if side > 0 else 'east', 0)

    # Grand Crown Gate opening from the Gatehouse into the Boss Court (Z=34..39, X=-4..4, height 12)
    gate = (np.abs(X) <= 4) & (Z >= 34) & (Z <= 39)
    g.extrude(gate, 0, 0, RNB)
    g.extrude(gate, 1, 12, 'air')
    for sx in (-5, 5):
        g.box(sx, 1, 36, sx, 13, 39, CHB)
    g.box(-5, 13, 36, 5, 14, 39, GBS)
    g.box(-3, 15, 36, 3, 15, 39, SHROOM)

    # Bridge across the Lava Moat & Processional Causeway to the +Z Entrance Mouth (Z=49..103)
    # Seal bridge deck & piers from y=-7..0 so the moat lava (Z=53..61) stays 100% sealed on all sides
    bridge_foundation = (np.abs(X) <= 7) & (Z >= 49) & (Z <= 64)
    g.extrude(bridge_foundation, -7, 0, PBB)
    # Walkable roadway from Z=35 all the way to Z=103
    road = (np.abs(X) <= 5) & (Z >= 35) & (Z <= 103)
    g.extrude(road, 0, 0, PBB)
    g.extrude((np.abs(X) <= 2) & (Z >= 35) & (Z <= 103), 0, 0, RNB)
    g.extrude((np.abs(X) == 0) & (Z >= 35) & (Z <= 103) & (Z % 4 == 0), 0, 0, SHROOM)
    # Clear 9-block-wide, 9-block-high passage from the mouth (Z=103) through the bridge & gatehouse into the court
    g.extrude((np.abs(X) <= 4) & (Z >= 35) & (Z <= 103), 1, 9, 'air')

    # Bridge parapets, arches & lantern pylons across Z=51..64
    for sx in (-6, 6):
        g.box(sx, 1, 51, sx, 2, 64, PBS)
        for z in range(52, 65, 3):
            g.box(sx, 1, z, sx, 5, z, PBAS)
            g.set(sx, 6, z, CHB)
            g.set(sx, 7, z, SHROOM)

    # Tunnel colonnade & ribs along Z=65..102
    for z in range(66, 103, 4):
        for sx in (-6, 6):
            g.box(sx, 1, z, sx, 8, z, PBAS)
            g.set(sx, 9, z, CHB)
            g.set(sx, 5, z, SHROOM)
        g.box(-6, 10, z, 6, 10, z, RNB)
        g.set(0, 10, z, SHROOM)

    # Outer Entrance Portal frame at Z=102..103
    for sx in (-6, -5, 5, 6):
        g.box(sx, 1, 101, sx, 9, 103, CHB)
    g.box(-6, 10, 101, 6, 11, 103, GBS)
    g.box(-3, 10, 102, 3, 10, 102, SHROOM)

    # Final safety sweep: guarantee the boss court clear cylinder (R <= COURT_R, 1 <= y <= ARENA_H)
    g.extrude(court, 1, ARENA_H, 'air')

    return g


META = {
    'arena': {'x': 0, 'y': 0, 'z': 0, 'radius': COURT_R, 'height': ARENA_H},
    'landing': {'x': 0, 'y': 1, 'z': 43},
    'mouth': {'x': 0, 'y': 1, 'z': 102, 'dir': 'south'},
    'gate': {'x': 0, 'y': 1, 'z': 36, 'width': 9, 'height': 12, 'dir': 'south'},
}

VIEWS = {
    'exterior': dict(
        cam=(35, 32, 48),
        target=(-8, 12, -8),
        fov=80,
        size=(1600, 900),
        ambient=0.26,
        gamma=0.78,
        light_boost=0.08,
        exposure=1.15,
        fog=(0.0018, (28, 11, 9)),
        title='NETHER  /  CROWN FORTRESS — CAVERN OVERVIEW',
        subtitle='Subterranean basalt-and-blackstone crown ring inside its lava moat cavern | no boss',
    ),
    'bridge': dict(
        cam=(0, 6, 63),
        target=(0, 12, 34),
        fov=78,
        size=(1600, 900),
        ambient=0.26,
        gamma=0.78,
        light_boost=0.08,
        exposure=1.15,
        fog=(0.0018, (28, 11, 9)),
        title='NETHER  /  CROWN FORTRESS — LAVA MOAT BRIDGE',
        subtitle='Processional bridge crossing the sealed lava moat toward the South Gatehouse',
    ),
    'hall': dict(
        cam=(-8, 5, 46),
        target=(8, 7, 38),
        fov=80,
        size=(1600, 900),
        ambient=0.28,
        gamma=0.78,
        light_boost=0.08,
        exposure=1.15,
        fog=(0.0020, (28, 11, 9)),
        title='NETHER  /  CROWN FORTRESS — GATEHOUSE & GALLERY STAIRS',
        subtitle='Vaulted entrance hall connecting the ring corridors, upper gallery and boss gate',
    ),
    'court': dict(
        cam=(0, 6, 31),
        target=(0, 16, -24),
        fov=82,
        size=(1600, 900),
        ambient=0.28,
        gamma=0.78,
        light_boost=0.08,
        exposure=1.15,
        fog=(0.0016, (28, 11, 9)),
        title='NETHER  /  CROWN FORTRESS — BOSS COURT (71-BLOCK CLEAR ARENA)',
        subtitle='Imperial crown medallion floor, gallery arcades and 12 crown spires rising under the cavern vault',
    ),
}


if __name__ == '__main__':
    build()
