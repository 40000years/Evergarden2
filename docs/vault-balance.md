# Vault reward balance

Evergarden `3.0.0-e2.139-vault-balance` gives early Vaults useful enchant books between ordinary supplies and rare equipment jackpots.

| Temple tier | Enchant book as major reward | Modeled weapon bonus | Elemental staff bonus |
| --- | ---: | ---: | ---: |
| 1 | 35% | None | None |
| 2 | 45% | 0.25%, Rare only | 0.25% |
| 3 | — | 5% | 10% |
| 4 | — | 10% | 15% |
| 5 | — | 15% | 20% |

These are per-opening chances. Weapon and elemental staff bonuses each roll once, independently of each other and the major rewards. They can appear together. Tier 5 still grants two major rewards and a supply; it does not double either bonus chance. Astral Vaults use the same bonus chances as Dark and Time Vaults. Successful Tier 3–5 weapon rolls retain the 80% Rare / 20% Legendary split and all current enabled weapons. An unavailable Legendary factory still falls back to a Rare weapon.

Books have one native stored enchant, selected equally among six types:

| Enchant | Tier 1 level | Tier 2 levels |
| --- | ---: | --- |
| Sharpness | VI | VI / VII |
| Protection | VI | VI / VII |
| Looting | IV | IV / V |
| Fortune | IV | IV / V |
| Efficiency | VI | VI / VII |
| Unbreaking | IV | IV / V |

Tier 2 books roll the lower level 70% and the upper level 30% of the time. Book rolls replace part of the normal major reward pool. Remaining rewards keep the existing scrolls, relic tools, potions and Eternity rolls, with Tier 2 potion improvements. The former direct Rare and equipment-category weapon paths are removed for early tiers. Flying Staff rewards and flying outcomes of mythic rolls now start at Tier 3; a Tier 2 mythic bonus awards Eternity, and an early flying-staff category ticket awards a relic tool. Untiered admin reward previews now use the real Tier 1 major pool.

The elemental bonus gives one blank Apprentice staff with three spell slots, equally likely across all eight elements. `staff-loot.enabled` still disables elemental loot. Vaults use the tiered odds above; the old `staff-loot.vault-chance` setting is retained for legacy raw-roll callers and does not control tiered Vault rewards. Sky chest and Overworld chest rates are unchanged.

## Anvil compatibility

The existing setting is `mechanics.anvil-transfer: true` in `plugins/Evergarden/boss-treasure.yml`; the shipped default already enables it. Vault books call `BossEnchantService.markGenerated` after writing their stored enchant. The existing anvil listener therefore transfers the exact high level and its entitlement to compatible equipment. Equal high-level books cannot create a higher level. Native item compatibility, enchant conflicts, XP costs and input consumption remain enforced. No global vanilla enchant limit is changed.

## Build and validation

Build with `python tools/build_vault_balance_release.py`, run the updated `evergarden/tests/run_vault_weapon_checks.py` against the candidate on isolated cached Paper with Advance Magic enabled and disabled, then use `--promote`. Promotion checks validation receipts against the candidate, dependency and test-source hashes before replacing `dist/evergarden.jar`.

Only the RelicService and VaultLootTable class families, plugin metadata and config comments are replaced. All 599 unrelated baseline JAR entries, including resource packs and the anvil implementation, remain byte-identical. The baseline JAR is retained under `output/vault-balance-20261026/before/`.

Validated on isolated Paper 26.3: **103,426 checks passed** with Advance Magic active and **102,763 checks passed** with it disabled after startup. Coverage includes all 50,000 tier/weapon ticket combinations, all 50,000 tier/book ticket combinations, all 20 enabled Rare weapons, all five Legendary weapons, eight staff elements, reward boundaries and independent bonus delivery. All 12 actual book variants passed native survival anvil transfer, XP payment, input consumption, equal-level merge limits and serialization checks. Protection/sword and Fortune/Silk Touch conflicts remain blocked.

Install the resulting Evergarden JAR with the server stopped, then restart. Existing claimed Vaults, keys and earned items retain their normal persistence; this release does not refill claimed loot.
