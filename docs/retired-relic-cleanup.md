# Retired relic cleanup and crystal mana bottles

Advance Magic `1.8.2-crystal-mana-potions` and Evergarden `3.0.0-e2.46-recipe-migration-mana-menu`.

Key Shards, Vault Repair Stones and Void Walker Elixirs have no active item factories, enum entries, crafting recipes, abilities, command aliases, admin-menu entries, guardian drops or Vault rewards. The four retired recipe IDs are removed on startup and recipe unlocks are cleaned for players. The surviving Evergarden Key comes from completed sanctum encounters. The 58.5% Vault reagent category now gives 1–2 vanilla Echo Shards; every other category keeps its prior probability. Landmark bonuses also replace Key Shards with Echo Shards.

`RemovedRelics` keeps only tombstone identifiers required to find historical items. It recognizes both `voidscape` and `evergarden` PDC, legacy relic tags, direct item models and custom-model selectors. It clears legacy stacks from player inventories, ender chests, cursors, equipment, dropped items, item frames, containers, Shulker Box items and Bundles. Normal vanilla materials and other plugins' tags are preserved. Nested storage is cleaned recursively. Tagged guidebooks receive current pages when migrated.

Online players and loaded chunks are cleaned when the plugin enables. Offline item inventories are cleaned on their next login; unopened storage is cleaned when its chunk or inventory is loaded. The plugin does not force-load all world chunks. This repository release builds the cleanup behavior into the JARs; it does not deploy to the user's server.

Retired recipe-book entries are cleaned earlier, during plugin load, before Minecraft reads player recipes. `RetiredRecipeData` scans existing world `playerdata` directories and removes only the eight namespaced retired keys from `recipeBook.recipes` and `recipeBook.toBeDisplayed`, in both `.dat` and `.dat_old`. All unrelated decoded NBT bytes are preserved. Changed files are backed up under `plugins/Evergarden/retired-recipe-backups/` before an atomic replacement, and the migration is idempotent. A pre-login listener also handles restored older files for offline players. This fixes the native `Tried to load unrecognized recipe ... removed now` errors that occurred before the old join-time cleanup could run.

The `/evergarden test` menu has a Mana Potions button. Its submenu offers all four qualities: left click issues one bottle, right click four, and Shift click eight separate drinkable bottles. It uses the existing admin permission checks and handles full inventories through the menu's standard give-or-drop behavior.

Removed item PNGs, model/item routes, vanilla model selectors, Bedrock atlas entries and Geyser definitions are absent from the regenerated downloadable packs. JSON inside the archives is compacted. The remaining restoration altar, Restoration Core and Wand of Restoration belong to the separate wand-repair system and are retained.

| Downloadable pack | Before | After | Change |
| --- | ---: | ---: | ---: |
| Advance Magic Java | 7,411,656 B | 7,423,249 B | +11,593 B for new artwork |
| Advance Magic Bedrock | 7,389,942 B | 7,398,287 B | +8,345 B for new artwork |
| Evergarden Java | 344,408 B | 275,840 B | −68,568 B / 19.9% |
| Evergarden Bedrock | 1,359,537 B | 1,291,819 B | −67,718 B / 5.0% |

The four downloads together shrink by 116,348 bytes while adding the redesigned bottle artwork. Advance Magic Bedrock is `2.11.4`; Evergarden Bedrock is `3.24.2`. Restart the server/Geyser and reconnect to refresh packs and custom-item registrations.

Mana nausea lasts 30 seconds by default instead of 10. Additional qualifying doses refresh it. Duration is configurable under `mana-potions.nausea-seconds`; quality probabilities remain 60/30/8/2.

The bottle artwork was generated with the built-in `image_gen` tool using the imagegen skill. All four source images are in `advance-magic/art/potions/source/`, production textures are `advance-magic/art/potions/mana_potion_{low,medium,good,supreme}.png`, and exact per-quality prompts are in [imagegen-prompts.json](../advance-magic/art/potions/imagegen-prompts.json). Normalization preserves transparent alpha and uses one texture per quality on both clients. See [the bottle preview](../previews/crystal-mana-potions.png).

Validation: 372 native Paper mana/casting checks, 15,950 legacy cleanup and production Vault checks, and 465 real Geyser translations over 92 custom identifiers across five Bedrock protocol tables. The startup probe verifies primary/fallback recipe-file migration, exact preservation of other NBT, backups, malformed input and idempotence. A native Paper player exercises all four menu qualities and left/right/Shift quantities, separate bottle stacks and nonadmin rejection. Both resource-pack validators and the legendary asset validator pass against the packaged release JARs. These are server and translation probes, not screenshots from connected Java/Bedrock clients.
