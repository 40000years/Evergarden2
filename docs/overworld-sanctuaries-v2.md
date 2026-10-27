# Six Rivers — furnished sanctuaries and End City treasure

15 October 2026 · `3.0.0-e2.76-sanctuary-interiors-and-end-city-loot`

This revision fills the six Overworld buildings with occupied spaces and adds
covered cloisters, guardian statues, roof dormers, material variation and layered
facades. The eight treasure locations and the clear boss courts keep their
original coordinates. Boss entities and combat remain a later phase.

Browse the [new exterior/interior gallery](../previews/overworld-sanctuaries-v2/index.html),
[exterior overview](../previews/overworld-sanctuaries-v2/overview.png), and
[main hall interiors](../previews/overworld-sanctuaries-v2/interior-overview.png).
The gallery also links the previous designs. Images render the actual block
assets; interior images remove selected walls and roofs for inspection.

| Building | New interior details |
| --- | --- |
| Acheron | Ceremonial pews, ribbed vaults, large hanging chandeliers, upper reading galleries, urn pedestals, furnished archives and throne altar |
| Phlegethon | Central metal press, assembly benches, grinding stations, ore cages, overhead ducts, service rooms and smelter stores |
| Cocytus | White nave, pew groups, vault ribs, hanging lights, archives, reading galleries, organ and ice altar |
| Banshee | Banquet hall, lounge tables, enclosed fireplace, upper sleeping landings, kitchen, storage and guest rooms |
| Malefeasance | Four research stations, alchemy and mapping equipment, radial lecterns, telescope, suspended celestial ring and archives |
| Styx | War table with map, quartermaster stations, separate sleeping bays, armory/work areas and upper galleries |

Walkability checks cover all **48 original treasure locations**. The central
60-block arena diameter retains a solid floor and 28 blocks of clear headroom.

## End City loot and rare enchant levels

New caches use Minecraft's actual **End City Treasure** table, including its
native weights, equipment, enchant combinations and resource quantities.
Equipment is not guaranteed in each chest.

After generating that vanilla loot, each eligible enchanted armor/tool/weapon
has a **15% chance** to raise **one existing enchantment** to its vanilla maximum
plus one. Examples are Sharpness VI, Protection V, Efficiency VI, Fortune IV and
Unbreaking IV. Other enchants on that item keep their original levels. Single-level
enchants such as Mending and curses stay at their normal level. Resources and
enchanted books are not candidates for this bonus.

The configurable probability is
`overworld-sanctuaries.treasure.over-cap-chance` in `plugins/Evergarden/config.yml`.
Restart after editing it. It is a probability per eligible equipment item, not a
guarantee per chest or per building. The awarded enchant receives the existing
anvil entitlement, so normal migration and permitted anvil transfers preserve it.
The bonus is processed once per cache and never rerolled on reload or reopening.

## Existing buildings and inventories

On first upgrade from e2.75, the saved world's explored layout cells are recorded
under `legacy-design-cells`. Missing chunks in those cells finish with the old
base blueprint and then receive the same selective decoration upgrade. New cells
use the complete new blueprint directly; placement coordinates and rarity stay
unchanged.

Existing chunks receive a bounded stream of decoration changes as they load.
Each replacement checks the old authored block first. Player edits and existing
containers are preserved. A chunk marker prevents repeated decoration.

An old chest is converted to End City loot only when it still has its untouched
original loot table, or its complete inventory exactly matches the old seeded
inventory. Empty, partly looted, renamed/edited and player-filled caches keep their
contents. Removed chests stay removed. A converted chest receives its new loot
and bonus once; later restarts preserve the exact resulting inventory.

## Admin teleport

```text
/sanctuary tp acheron
/sanctuary tp phlegethon
/sanctuary tp cocytus
/sanctuary tp banshee
/sanctuary tp malefeasance
/sanctuary tp styx

/sanctuary tp acheron 256
/sanctuary tp phlegethon 256
/sanctuary locate acheron 256
```

Run these in an ordinary Overworld with OP or `evergarden.admin`. Search now
continues beyond 24 cells, reports progress at 24/48/96/192 cells as needed, and
uses an efficient perimeter scan. The default maximum is **128 cells**, adjustable
through `overworld-sanctuaries.search-max-cells`. The optional command argument
accepts 24–256. Search samples the same saved placement rules as generation and
does not load or generate chunks. Teleport loads the actual found site.

A new search replaces that player's previous search. Logging out, changing worlds
or disabling the plugin cancels it. The search remains bounded by the requested
radius and by biome/explored-cell restrictions; an accepted site is not fabricated
when none exists in that area.

The regression seed reproduces the old limitation: Phlegethon is at cell X=-30,
outside the old 24-cell search. The expanded search finds all six kinds, including
Acheron and Phlegethon, without changing their saved coordinates.

## Validation and delivery

The isolated Paper checks cover every new blueprint block, all treasure routes,
both generation orders, arena headroom, saved inventories, and searches for all
six kinds. **1,500 real End City rolls** test ordinary items and rare awards,
the cap+1 bound, one raised enchant per item, item compatibility and anvil
entitlements. A three-boot upgrade starts with the delivered e2.75 JAR and checks
new furnishings, a player edit, three altered/looted caches, 45 untouched cache
conversions and exact inventory preservation after restarting v2.

The final release passed **1,954,301 authored block comparisons**, two generation
boots and three upgrade boots on Paper 26.3 build 152. Walkability also checks
the upper galleries, not just the chest approaches. Runtime reports:
`output/overworld-sanctuaries-v2-verified-20261015/` and
`output/overworld-sanctuaries-v2-upgrade-final-20261015/`.
Authoring and previews: `tools/build_overworld_sanctuaries.py`,
`tools/sanctuary_detail_v2.py`, `tools/preview_overworld_sanctuaries.py`.
The shipped v1 geometry is retained under
`evergarden/src/main/resources/overworld-sanctuaries/v1/` for upgrades.

Install the release [dist/evergarden.jar](../dist/evergarden.jar) and restart.
Keep the existing world ledger. Freshly generated sites use the new design, and
existing compatible sites receive decorations when their chunks load.
