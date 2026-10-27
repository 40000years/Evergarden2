# Six Rivers / distinct architecture and organized interiors

15 October 2026 · `3.0.0-e2.77-distinct-sanctuary-architecture`

The shared central-hall/two-wings/cloister layout has been replaced with separately
authored plans. The supplied reference establishes each biome's identity; renders
show the actual shipped blocks, with local Minecraft textures and simulated
lighting. They are not game screenshots and no percentage of similarity is claimed.

| Site | Architectural identity | Interior organization |
| --- | --- | --- |
| Acheron | Asymmetric dark fortress, tall western keep, red rose window, defensive gate | Throne dais, paired audience benches, rear mezzanine, guard bays and archive |
| Phlegethon | Long sawtooth foundry, clustered chimneys, gantry yard, casting trenches | Three furnace mouths, partitioned service bays and broad cross aisles |
| Cocytus | White and blue abbey, offset tall belfry, low side roofs, eastern garden arcade | Organ altar, ordered pews, dormitory and refectory |
| Banshee | Timber manor on piles, staggered roofs, verandas, boathouses and wooden ritual deck | Dining salons, archive, study and rear library mezzanine |
| Malefeasance | Circular rotunda with ribbed turquoise dome, portico and split reflecting gardens | Armillary instrument, four reading quadrants and separate archive pavilion |
| Styx | Twin-pylon triumphal gate, monumental stone axe, tent market and obelisks | Gate command room, aligned bunks and eastern records hall |

See [exteriors](../previews/overworld-sanctuaries-v3/overview.png),
[interior cutaways](../previews/overworld-sanctuaries-v3/interior-overview.png),
and the [full gallery](../previews/overworld-sanctuaries-v3/index.html).

The same entrance position, natural biome placement, search rules and eight cache
coordinates are retained. Arenas have a clear 60-block diameter and 28-block
headroom; rectangular, circular and timber courts use different surface designs.
Room entrances, mezzanines and all 48 caches are checked by a walking-route flood
fill against the authored blocks.

## Mineable construction and economy

Construction uses stone, deepslate, blackstone, diorite, calcite, concrete,
terracotta, wood and glass. A final palette audit rejects precious storage blocks,
ores, raw metal blocks, copper variants, anvils, hoppers, blast furnaces, cauldrons,
beacons, enchanting/brewing stations, amethyst blocks, quartz construction,
prismarine, sea lanterns, end rods and blue ice. Yellow terracotta replaces gold
trim; gray stone replaces iron sculptures. Small functional furnishings such as
ordinary furnaces, stonecutters, bookshelves and lamps remain mineable. This is a
construction-material audit, not a server shop price simulation.

The exact per-site previous/new counts are recorded in
[material-audit.json](../previews/overworld-sanctuaries-v3/material-audit.json).
Every new site's forbidden-material count is zero. Loot contents were deliberately
kept at the accepted v2 settings: actual vanilla End City treasure, with a 15%
chance per eligible gear item for one existing multilevel enchantment at vanilla
maximum + 1. No loot rebalance or additional inventory roll is performed.

## Existing worlds

Install `dist/evergarden.jar` while the server is stopped, then restart. Existing
sites are patched as their chunks load, with a bounded per-tick queue. The ledger
records previous design cells before switching to geometry version 3. Original
v1/v2 assets remain packaged so partially explored sites generate compatible old
pieces before receiving the same selective upgrade.

Only blocks still matching the original authored state are changed. Mined blocks
and player edits survive. Treasure chests are never moved, recreated or refilled.
Empty original decorative barrels/furnaces can be removed with old clutter;
containers holding player belongings survive. Thus an edited existing site can
retain some old objects; newly generated sites use the complete v3 geometry.

The existing command remains `/sanctuary tp <id> [24-256]`, default search radius
128 cells, with permission `evergarden.admin`. Example:
`/sanctuary tp phlegethon 256`. No world or placement-ledger reset is needed.

## Verification

The final JAR is tested on Paper 26.3 build 152 / Java 25 with fresh generation and
restart, delivered v2 -> v3 -> restart, and delivered v1 -> v3 -> restart. Checks
cover all 1,986,843 authored voxels in real chunks, 48 persistent inventories,
unchanged v2 loot bytes, mined gold, an administrator edit, a player-filled barrel,
empty decoration removal, old valuable-block replacements, biome search and arena
clearance. Boss models and combat remain outside this architecture revision.

Reports are under `output/overworld-sanctuaries-v3-final-20261015/`,
`output/overworld-sanctuaries-v3-from-v2-final-20261015/`, and
`output/overworld-sanctuaries-v3-from-v1-final-20261015/`. The exact tested JAR and
artifact hashes are recorded in the v3 build's `release-manifest.json`.
