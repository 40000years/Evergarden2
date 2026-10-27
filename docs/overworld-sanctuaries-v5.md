# Six Rivers / shape, support and detail corrections

15 October 2026 · `3.0.0-e2.79-sanctuary-structural-polish`

This pass follows the reported Styx axe and checks all six buildings for detached
assemblies, disconnected ornaments, implausible furniture height and unsupported
roof/interior details. The layout, 110 furnished spaces, boss-court clearances,
eight chest coordinates per site and accepted End City loot profile are retained.

## Corrections

| Area | Finding | Change |
| --- | --- | --- |
| Styx axe | Two slab-like blade plates did not connect to the haft | Crescent profiles with concave cutouts, bevelled cutting edges, a forged socket, binding bands and a plinth connected to the bridge |
| Styx gate | Floating arch feet, detached facade strips and oversized crown remnants | Grounded entrance terrace, joined brackets/banner mounts, smaller coherent battlement crowns and tower entrances |
| Acheron | Rose-window disc sat in front of the gable; portal fragments and cornices were detached | Recessed rose/tracery, continuous arch, tied facade details and supported watchtower caps |
| Phlegethon | Central roof was a detached assembly; wall sheets read as hanging fins | Ground-reaching columns, a roof slab with braced gantries, supported vent platforms and machinery plinths |
| Cocytus | Floating portico columns, rose panel, bell and organ accents | Entrance platform, entablature, set-in rose window, bell hanger and organ pedestals; usable spiral/landings inside the belfry |
| Banshee | Lantern cage overlapped the old steeple; porch railing posts missed their deck edge | One coherent glazed lantern house, tied timber framing and supported boardwalk rails |
| Malefeasance | Dome bands and finial were disconnected; instrument stem missed its plinth | Continuous turquoise dome shell with clay-coloured ribs, grounded armillary stem and a supported gallery railing |
| Interiors | Tables were too high for chairs; some pots/racks/hangers floated | Lower tables with corner legs, corrected cabinet tops, ceiling cross-beams, wall brackets and ceiling-reaching hangers |
| Artwork | Front-wall mosaics faced out of rooms | Inward-facing framed portraits and moon/sun scenes with clearer colours |
| Fence/wall joins | Physics-disabled population left default connection flags | Explicit north/south/east/west joins are now authored; real Paper tests verify intended rail and wall connections |

Small inherited trim fragments are either linked to the building with short
material-matched ties or removed when no short support exists. Tie coordinates
are recorded in each design JSON. Walking-route reservations prevent these ties
from filling room entrances; stairs retain their underpasses where needed.

## Review artifacts

The [gallery](../previews/overworld-sanctuaries-v5-final/index.html) contains exterior,
rear/side, ground-floor and upper-floor views, before views, and an enlarged
[axe inspection](../previews/overworld-sanctuaries-v5-final/styx-axe.png).
All are renders of the shipped geometry and authored block states, using local
Minecraft textures with simulated lighting. They are not game screenshots.

The [structural audit](../previews/overworld-sanctuaries-v5-final/structural-validation.json)
checks face adjacency and isolated visible fixtures in the actual compressed
assets. All six have zero detached above-ground assemblies and isolated fixtures.
This is a voxel-connectivity test, not a claim of real-world engineering accuracy.
The [geometry validation](../previews/overworld-sanctuaries-v5-final/geometry-validation.json)
checks room access, upper routes, all chests, belfry access, arena space and axe
profile/neck continuity. The
[material audit](../previews/overworld-sanctuaries-v5-final/material-audit.json)
retains the existing individual limits and aggregate 128-block rare-material cap.

## Existing worlds

Replace `dist/evergarden.jar` while the server is stopped, then restart. Geometry
version 5 can selectively update native and previously stamped v1/v2/v3/v4 chunks
as they load. The original assets for all four versions remain packaged so source
matching is exact. Player edits and mined blocks remain untouched; treasure is
never moved, recreated or rerolled. Player-filled decorative containers survive.
Edited existing sites can consequently retain some old objects.

Commands and biome placement are unchanged: `/sanctuary tp <id> [24-256]`, default
128 cells. Boss entities/combat remain outside this architecture pass.

The release manifest records the exact final JAR, real-Paper generation/restart and
upgrade reports, connectivity/material audits and image hashes. Fresh generation
also verifies the actual fence and wall connection states, alongside every authored
material, all 48 chest inventories, search rules and clear arenas.
