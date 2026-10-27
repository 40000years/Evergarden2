# Evergarden boss armor exchange

Evergarden `3.0.0-e2.125-boss-core-textures` with Advance Magic
`1.23.0-armor-combat` or newer. Install the JAR pair and restart.

| Defeated main boss | Physical drop | Exchange at Seraph |
| --- | --- | --- |
| Seraphon, Supreme Sanctuary in Evergarden | 1 Core of Seraph | Any one Seraph armor piece |
| Arkhaia, Dreadrose castle in Evergarden | 1 Core of Revenant | Any one black Revenant armor piece |

Each victory drops **one shared physical core**, not one per participant. The core
appears at the center of the arena, glows, and resists damage. Arkhaia only drops
its core after the final phase. Summoning, phase changes, encounter resets,
aborted encounters and model previews award no cores. Looting does not multiply
these virtual-boss rewards. Existing skill points, feathers and experience remain.

Visit the existing Seraph NPC at the Evergarden entrance/spawn and choose
**ร้านตำนาน**. Armor is included in this existing shop, without a separate armor
category. Java and Bedrock use the same villager-style trading screen. There are
eight offers: helmet, chestplate, leggings and boots for each of the two sets.

Each offer costs exactly **1 matching core** and produces one chosen piece, with
no extra feathers, materials, discounts or daily/weekly limits. Four matching
cores can buy a complete four-piece set. Players can select the same slot again
for a replacement. A Core of Legend or the other set's core cannot pay, and a
renamed vanilla item cannot impersonate a core. Cores are excluded from manual
and automated crafting.

Both cores have dedicated transparent 256px hand-painted textures: white/gold
feather wings and a luminous crystal for Seraph, black thorned armor and a red
soul crystal for Revenant. Java item models and Geyser/Bedrock item definitions
select these textures; ordinary Nether Stars and Echo Shards retain their vanilla
appearance. Bedrock pack content is `3.52.0`. Previously awarded cores gain the
new model when carried into the shop, on join or when opening storage; quantities
and identity remain intact. See [art preview](../evergarden/art/boss-armor-cores/preview.html)
and [built-in imagegen prompts](../evergarden/art/boss-armor-cores/prompts.json).

The exchange uses Advance Magic's existing armor factory. Models, base `Combat`
lore, durability, attributes, set powers, enchanting and upgrades therefore match
the existing Seraph / Revenant armor. See [armor-combat.md](armor-combat.md).

The boss at boss-temple is deferred. No core reward or armor exchange is added
for it. The six Overworld sanctuary Soul Orbs and the
existing Core of Legend recipes are unchanged.

## Validation

`tests/arkhaia/run.py --armor-exchange` runs an isolated Paper fixture with the
production boss controllers and native merchant containers. It checks all eight
offers inside the legend shop, legacy-core migration, custom texture selectors,
counterfeit and cross-set payment rejection, actual purchases and core
consumption, repeated and shift-click purchases, full inventories, crafting,
native item serialization, both final boss drops, phase/abort exclusions and
duplicate-defeat protection. No live server is modified by this fixture.
