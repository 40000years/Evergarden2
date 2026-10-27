# Seraph sanctuary maps

Evergarden `3.0.0-e2.135-map-trades` opens **ร้านแผนที่** in the same native Villager trading window as the angel NPC's other goods, on both Java and Bedrock. The shop lists the actual feather ingredient, quantity and named map result for each boss. Players select a recipe, supply its ingredient and take the result normally.

| Destination | Price per map | Choices |
| --- | --- | --- |
| Overworld Sanctuary | 30 normal Seraph Feathers | Acheron, Phlegethon, Cocytus, Banshee, Malefeasance, Styx |
| Evergarden main boss | 10 Golden Seraph Feathers | Bodiless Judge, Arkhaia, Seraphon |

Each exchange gives one native filled map. Hold it in the world named in its lore: the red X marks the fixed destination, and Minecraft's normal player indicator shows the approach. Terrain starts unexplored and fills as players explore. The target name and world are also on the item because Bedrock does not preserve Java map-cursor captions. No new resource pack is required.

The buyer chooses the boss type. Evergarden offers only the three main bosses: Bodiless Judge uses the ancient world-boss temple layout; Arkhaia and Seraphon use their current boss structure layouts. Valkyrie and the Dark/Astral/Time sanctums are excluded. Disabled bosses/structures cannot be purchased. Overworld searches use the current attached Overworld when applicable, otherwise the first attached Overworld, starting at that world's spawn when buying from Evergarden. Evergarden searches start from the buyer's position in Evergarden. Searches follow the same biome, layout history and placement rules as generation and do not generate chunks. Targets previously reached through that buyer's maps are skipped; reaching within 144 horizontal blocks records the visit. Another player's visit does not exclude the boss location.

Maps never change their destination when reopened or traded. A matching map already in the buyer's storage inventory prevents an accidental duplicate purchase. A lost map can be purchased again. Plain map cloning retains the destination; scaling and locking are refused because vanilla would assign a new map ID and lose the destination marker.

Prices require the exact tagged currency: golden feathers are not automatically substituted for normal feathers, or vice versa. Opening the shop requires no currency and spends nothing. All nine offers show their ingredient and result; unavailable locations and already-owned maps are out of stock. Available results are actual fixed-destination filled maps prepared before the merchant opens. Native Minecraft trading handles payment, pickup, Shift-click capacity and returning unused input when closing. Each offer allows one purchase; reopening reuses the same map ID and refuses an already-carried duplicate. Live target/configuration/border checks and registry failures can cancel a trade without spending its ingredient.

Preparing or viewing a map offer does not count as purchasing or visiting its destination. Preview map IDs are reused, and only purchased records participate in visit history. Changing inventory screens or leaving while lookup runs cancels it without reopening a shop later. Cancelling a purchase after validation rolls back preview ownership, including when another attempt succeeds in the same tick.

Map IDs, target world/coordinates, owner, purchased and reached state are atomically saved to `plugins/Evergarden/seraph-sanctuary-maps.yml`. Existing e2.133 records remain purchased maps when the new field is absent. Saved renderers are restored at startup and when maps/worlds initialize. The file is separate from Vault claims and existing shop limits; malformed/unwritable map storage pauses map exchanges.

Defaults (also used for existing configs without these keys):

```yaml
seraph-trader:
  maps:
    overworld-feathers: 30
    evergarden-golden-feathers: 10
    evergarden-search-radius: 12000
```

Overworld lookup uses the existing `overworld-sanctuaries.search-max-cells` setting (24–256). Price bounds are 1–64 of the specified currency. Map offers are independent of the shop's rotating promotions and have no daily/weekly stock limit.

Build with `python tools/build_map_merchant_release.py`, verify the candidate using `evergarden/tests/run_sanctuary_map_checks.py` and the Vault regression probe, then promote with `--promote`. The build preserves the delivered e2.134 weapon changes and all unrelated classes, models and resource packs. Nothing is installed in a running server by these commands.

Validation passed 434 checks using actual native container clicks for all nine offers, exact currency, Thai UI text, pickup/Shift-click, full inventory, late cancellation/retry, preview reuse and purchased visit history. A second Java server process passed 164 checks for nine purchased plus nine unbought preview map IDs and automatically restored red X overlays. The Vault probe passed 51,596 checks for the current 20 enabled Rare weapons and five Legendary weapons. The scoped archive check preserves all 589 unrelated baseline JAR entries, including all five resource pack files. Bedrock uses Geyser's native merchant-offer, filled-map and red-X translation; no physical Bedrock-client session was run.
