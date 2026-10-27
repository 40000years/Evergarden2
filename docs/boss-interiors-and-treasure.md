# Boss interiors and treasure

13 October 2026 · Evergarden `3.0.0-e2.50-boss-interiors-and-treasure`

The current e2.51 release additionally preserves boss levels during anvil transfer and extends the runtime mechanics that saturate. See [all 31 enchant mechanics and exploit checks](boss-enchant-mechanics.md).

The approved e2.49 exterior designs, structure locations, arenas and Arkhaia traps stay in place. Furnishings occupy the enclosed rooms and Arkhaia's two cloister floors. Boss spawning and combat remain part of the later phases.

## Interior design

| Structure | Furnishings | Treasure locations |
| --- | --- | --- |
| Arkhaia | Crimson floor inlay, blackstone pilasters and vault ribs, three soul-lit chandeliers, throne, pews, ceremonial urns and iron-clad guards; tables and lamps on both cloister floors | Two original altar positions, four nave pedestals, two upper cloister coffers |
| Valkyrie | Blue and gold radial mosaic, gilded columns, blade altar, quartz benches, suspended candelabrum, ornamental planters and diamond-clad spear guards | Two original altar positions and six decorated reliquary pedestals |
| Seraphon | Celestial floor mosaic, tall gilded pillars, winged throne, luminous ceiling rings, chandelier, ceremonial urns and diamond-clad sword guards | Two original altar positions and six decorated treasury pedestals |

Every new structure has **eight single chests**. The old bookshelf nook becomes an ornamental altar. Chest lids have free headroom, and the central processional route and combat volume remain clear. Display equipment is decorative and protected from ordinary players; creative administrators retain their maintenance bypass.

Review the [three main halls](../previews/boss-interiors/overview.png) and [Arkhaia's two-floor cloister](../previews/boss-interiors/arkhaia-gallery.png). These are renders of the actual generated blocks, with selected walls and roofs removed only in the preview to expose the interior. The in-game exterior remains intact. Entity-based armor displays are validated on Paper and do not appear in the block renderer.

## Loot

The baseline was inspected directly from the Java 26.2 client archive's `data/minecraft/loot_table/chests/end_city_treasure.json`; a reference copy is stored in [end-city-treasure-26.2.json](loot-reference/end-city-treasure-26.2.json). The boss table deliberately broadens the gear selection and sets enchant levels explicitly. Microsoft's [official End City table](https://learn.microsoft.com/en-us/minecraft/creator/reference/source/vanillabehaviorpack_snippets/loot_tables/chests/end_city_treasure?view=minecraft-bedrock-stable) is a separate Bedrock reference.

Each new chest draws **1–2 equipment items**, **2–4 resource entries**, and an additional rare entry with **15% probability**. Entries use relative weights; the table does not guarantee a particular weapon in every chest.

| Equipment family | Main enchant ranges |
| --- | --- |
| Diamond sword, spear and axe | Sharpness VI–VIII where supported; sword may also have Looting III–V, Fire Aspect II–III and Sweeping Edge III–V |
| Diamond pickaxe, shovel and hoe | Efficiency VI–VIII; Fortune IV–V; separate Silk Touch pickaxe profile |
| All four diamond armor pieces | Protection VI–VIII, with appropriate helmet, leggings and boot enchants; separate Frost Walker boots |
| Iron sword and chestplate | Over-cap Sharpness or Protection with lower selection weights |
| Bows | Power VI–VIII, optional Punch II–III and Flame; Infinity and Mending use separate profiles |
| Crossbows | Quick Charge III–IV; separate Piercing and Multishot profiles |
| Tridents | Impaling VI–VIII; separate Loyalty/Channeling and Riptide profiles |
| Maces | Separate Density V–VII and Breach IV–V profiles, optional Wind Burst |
| Shield | Unbreaking IV–VI, optional Mending |
| Enchanted books | Sharpness VI–VIII, Protection VI–VIII, Fortune IV–V, Efficiency VI–VIII, Power VI–VIII or Mending |

Most gear also has Unbreaking III–V and an 18% Mending chance. The table contains **29 equipment profiles**, **12 resource entries** and **9 rare entries**. Resources include diamonds, iron, gold, emeralds, pearls, chorus fruit, experience bottles, arrows, golden apples, amethyst and seeds. Rare loot includes a Spire trim template, horse equipment, shulker shells, netherite scrap, name tags, end crystals and enchanted golden apples.

Vanilla level ceilings are bypassed when writing item enchantments. Item compatibility and conflicting enchant pairs remain checked. Gear keeps its native material and item components, and existing relic migration preserves its enchant levels.

The main chest additionally receives one Wand Upgrade Core. When Advance Magic is active it receives the existing Restoration Core reward, optional Repair Wand and the configured one-time Flying Staff draw. These bonuses are drawn once per structure, rather than in all eight chests.

## Configuration and updating

Replace the server's Evergarden JAR with [dist/evergarden.jar](../dist/evergarden.jar) and restart. On the first boot, the bundled [boss-treasure.yml](../evergarden/src/main/resources/boss-treasure.yml) is copied to `plugins/Evergarden/boss-treasure.yml`. Edit weights, counts, enchant ranges or probabilities there and restart to load the changes. Loot changes apply to chests that have not received their rewards yet.

Already-generated e2.49 structures receive matching furnishings as their chunks load. Only generated blocks matching the old interior are replaced. Existing containers, independently edited blocks, exterior geometry and arena blocks are preserved. The two original chest positions keep their inventory or their absence; six additional decorated chests receive the new loot. Empty or deleted original chests stay empty or deleted. Per-chunk and per-chest markers prevent refills, rerolls and repeated decoration. Equipment displays retain persistent identities across reloads.

Legacy e2.48 structures marked as architecture version 1 retain their old geometry and treasure behavior. Newly generated version 2 structures use the furnished design.

Admin inspection commands remain `/evergarden tp arkhaia`, `/evergarden tp valkyrie` and `/evergarden tp seraphon`.

## Validation

The complete JAR was compiled against the cached Paper 26.2 libraries and tested in isolated real Paper worlds:

- Two boots with newly generated structures: all **35,443,090** blueprint block/air positions, arena headroom, routes, eight chests per structure, protected displays, traps, structure protection and durable placement/reward markers.
- **1,500 loot rolls**: configured high enchant levels, additional gear families, compatibility and existing relic migration.
- Three boots from the approved e2.49 JAR to e2.50 and then e2.50 again: original coordinates, exterior/arena fingerprints, preserved original loot and deleted chest, six added chests per structure, administrator edits, persistent displays and exact loot/enchant fingerprints after restart.

Results: `output/boss-interiors-validation-v4-20261013/` and `output/boss-interiors-upgrade-validation-v2-20261013/`. Preview geometry and renderer metadata: `previews/boss-interiors/manifest.json`.
