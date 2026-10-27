# Sanctuary habitat — e2.80

The six revision 5 buildings retain their architecture, ground placement, eight caches each, arena space and rare-material budgets.

## Tree crowns

Generation already clears the authored columns. A deferred, chunk-local pass also removes nonpersistent natural leaves in unauthored cells above the pad, including crowns deposited later by neighbouring chunk decorators. In recently generated sanctuary chunks it also removes intruding natural logs and repairs authored cells overwritten by tree decoration. Player placements during that generation window are recorded and skipped. Existing old chunks do not have their unauthored logs removed. It runs on chunk load and after neighbouring population. Authored trees, persistent player-placed leaves and the forest outside the exact pad silhouette are preserved. The scan uses chunk snapshots and handles one queued chunk per tick without loading other chunks.

## Ambient monsters

Only hostile `Enemy` entities with natural, chunk-generation, patrol or reinforcement spawn reasons are blocked. Bounds use the authored column silhouette, from each column's lowest authored voxel to eight blocks above the tallest architectural voxel. Caves below the foundation, areas outside the pad, animals, summons, player spawners, bosses and structure guards are unaffected. No Overworld gamerules or LevelledMobs world settings are changed. On the first habitat migration, ordinary unnamed natural hostiles in that same volume are cleared; named mobs, persistent pets/collected mobs and guards survive.

## Treasure

End City rolls retain their original equipment and 15% chance of one eligible enchant at vanilla cap + 1. An armor-trim template roll instead awards one real Advance Magic mana potion: medium 60%, good 35%, supreme 5%. This replaces the template slot and is not a guaranteed extra reward. If Advance Magic is absent, that slot becomes four amethyst shards instead of a nonfunctional potion.

Already generated caches migrate only if their entire seeded inventory still matches the old untouched End City roll plus the existing enchant award. Empty, looted or edited inventories are never rerolled, and player-added templates are preserved. A per-cache marker makes the change one-time.

## Armored guard cages

Three deliberately placed cages per site: wither skeleton, adult piglin and skeleton. Coordinates in `overworld-sanctuaries/guards.yml` use clear room positions in immutable revision 5 geometry and stay outside the arena. Each cage spawns one guard per cycle, waits 500–900 ticks (25–45 seconds), activates within 14 blocks and has a native limit of three nearby same-type mobs. A second local limit rejects new cage guards when six guards already occupy the surrounding 24 × 12 × 24 half-extents. Native collision and difficulty checks still apply; light rules allow the decorated, lit rooms.

Templates already contain armor before spawn listeners run. Wither skeletons and skeletons have iron/chain armor; piglins have gold/chain/iron armor and cannot zombify in the Overworld. Guard equipment has zero normal drop chance and item pickup is disabled. Usual species drops remain vanilla unless another plugin changes them.

Guards use native `SPAWNER` events and carry no Evergarden/no-level exclusions. LevelledMobs therefore applies the server's normal rules for spawner mobs, including its configured level range and attributes. This release does not force levels or rewrite server rules. Servers explicitly excluding `SPAWNER` mobs must enable that spawn reason in their own [LevelledMobs rules](https://arcaneplugins.gitbook.io/levelledmobs-the-ultimate-mob-levelling-solution/the-default-files/rules.yml/conditions).

Only the marked cage block is protected against mining (including creative/Silk Touch), explosions, pistons, entity block changes and right-click retuning. Surrounding architecture remains mineable. Cages install once per chunk on load after architectural upgrades and only into unchanged air with a solid floor; player blocks are preserved. Restarting does not recreate a cage removed administratively.

Config switches under `overworld-sanctuaries.habitat`: `clear-stray-leaves`, `suppress-natural-hostiles`, `guard-spawners` (all default true). Restart after replacing the JAR; existing eligible sites receive the habitat pass as their chunks load.
