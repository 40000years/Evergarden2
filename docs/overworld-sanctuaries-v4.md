# Six Rivers / occupied suites and limited rare accents

15 October 2026 · `3.0.0-e2.78-occupied-sanctuary-suites`

The main halls now contain usable rooms and upper galleries rather than a few
objects around a large empty floor. There are 110 furnished spaces across the six
sites, including enclosed rooms, reading alcoves and casting stations. Their
entrances, stairs, treasure approaches and usable floor space are checked against
the actual authored blocks. The central boss courts retain their clear 60-block
diameter and 28-block headroom.

| Site | Furnished spaces | Layout and reference details |
| --- | ---: | --- |
| Acheron | 22 | Two-storey suites around the throne nave; council/study rooms, library, bedrooms, lounges and relic niches; Gothic pinnacles and red tracery |
| Phlegethon | 12 | Support rooms on one side; open double-height foundry, furnace mouths, four casting presses, cranes and control gallery; stepped fortress roofs replace sawtooth factory roof |
| Cocytus | 24 | Chapel nave, organ, infirmary, music/prayer rooms, library, refectory and guest suites; white square belfry and sparse quartz column accents |
| Banshee | 28 | Kitchen, dining/sitting rooms, bedrooms, archive, music room and upper gallery; timber front gable and lantern turret |
| Malefeasance | 14 | Armillary instrument, four furnished radial study alcoves, curved book stacks, upper ring balcony and separate research rooms |
| Styx | 10 | Military support rooms and command suites; square monumental pylons, stepped gatehouse, sun medallion and caravan camp |

Furnishings include built-in cabinets, bookshelves, beds, desks and chairs, sofas,
workbenches, sinks, hearths, rugs with borders, ceiling beams, hanging lamps,
planters and potted flowers. Framed portraits, landscapes and moon/sun scenes are
permanent block mosaics, not painting entities; they survive unload/reload without
an entity-spawning system. Interior renders show both ground and upper floors;
ceilings and selected walls are hidden in the images only.

See the [full gallery](../previews/overworld-sanctuaries-v4/index.html),
[ground-floor overview](../previews/overworld-sanctuaries-v4/interior-overview.png),
and [upper-floor overview](../previews/overworld-sanctuaries-v4/upper-overview.png).
These are renders of the shipped blocks with simulated lighting, not screenshots
or a quantified match to the reference image.

## Rare materials return as accents

The previous all-common construction rule has been replaced with strict material
budgets. Each site can contain at most 128 blocks from the tracked rare/material
category; individual limits are tighter (gold blocks 4, iron blocks 8, anvils 2,
blast furnaces 4, copper blocks 12, etc.). Ordinary structural surfaces remain
stone, wood, concrete and terracotta.

| Site | Actual rare accents |
| --- | --- |
| Acheron | 2 gold blocks |
| Phlegethon | 5 cut copper blocks, 2 anvils, 2 blast furnaces |
| Cocytus | 24 quartz pillar blocks, 5 blue ice, 2 sea lanterns, 1 gold block |
| Banshee | 2 cauldrons, 1 sea lantern |
| Malefeasance | 3 copper, 2 gold, 4 amethyst, 4 quartz pillars, 4 quartz blocks, 4 end rods |
| Styx | 2 gold blocks, 2 iron blocks |

The exact audit, per-material limits and room-function counts are recorded in
[material-audit.json](../previews/overworld-sanctuaries-v4/material-audit.json).
The authoring step fails if a material or aggregate budget is exceeded. This
limits construction yield; it does not attempt to model custom shop prices.

## Existing worlds and loot

Stop the server, replace `evergarden.jar`, then restart. Owned original blocks are
selectively updated when their chunks load. Packaged v1, v2 and v3 geometries plus
the original placement ledger retain the correct source for native, previously
upgraded and partially explored chunks. Chunks stamped by earlier upgrades can
advance to version 4. Player-edited or mined blocks remain untouched.

The same eight treasure coordinates and End City loot profile are retained; v2/v3
inventories are not rerolled or refilled. A looted chest stays looted. Original
decorative containers holding player items survive; empty old decoration can be
removed. Consequently edited existing sites can retain some old objects.

Admin commands remain `/sanctuary tp <id> [24-256]` and
`/sanctuary locate <id> [24-256]`, default 128 cells. No world or ledger reset is
needed. Boss models and combat are still outside this architecture revision.

## Validation

Real Paper 26.3 build 152 / Java 25 tests cover all 2,055,142 authored voxels,
natural placement/search, 48 persistent treasure inventories and arena clearance.
Three-boot upgrade scenarios start from each delivered v1, v2 and v3 release and
test native and previously stamped chunks, player inventory preservation, empty
decoration removal, mined blocks and exact inventory stability on restart. The
exact tested JAR, image hashes, room routes and material audit are recorded in
`output/overworld-sanctuaries-v4-build-20261015/release-manifest.json`.
