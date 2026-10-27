# Vault weapon drops

Current rates and early-tier rewards are documented in [Vault reward balance](vault-balance.md). The e2.139 balance supersedes the Tier 1–2 preservation and old staff behavior described below.

Evergarden `3.0.0-e2.132-vault-weapons` adds one independent modeled-weapon bonus roll after the normal Vault rewards and supplies.

| Temple tier | Any weapon | Rare | Legendary |
| --- | ---: | ---: | ---: |
| 3 | 5% | 4% | 1% |
| 4 | 10% | 8% | 2% |
| 5 | 15% | 12% | 3% |

The Rare/Legendary split is 80%/20% **when the weapon bonus succeeds**. Each Tier 3–5 opening can add at most one weapon, including Tier 5 with its two major rewards. For these tiers, the old Rare rolls in major rewards and the equipment category are replaced by this single bonus, so the advertised chances are the total modeled-weapon chances per opening. Tier 1–2 retain their existing Rare loot and receive no new bonus roll.

This applies to Dark, Astral and Time sanctum Vaults. Rare weapons use the existing thematic selection: 72% select from the sanctum's enabled Rare pool, otherwise select from all 18 enabled Rare weapons. Thornwhisper and Runelock remain excluded. Legendary selects equally among Excalibur, Longinus, Tyrfing, Mjolnir and Vajra. The existing item factories supply models, attributes, item identity and abilities.

Advance Magic supplies Legendary weapons. If the plugin is disabled or its factory is unavailable, a winning Legendary ticket gives an enabled Rare weapon instead, preserving the overall bonus chance. Existing key tiers, per-player claim limits, saving and inventory-overflow delivery use the existing Vault flow.

Build the candidate with `python tools/build_vault_weapon_release.py`. Test it with `evergarden/tests/run_vault_weapon_checks.py` on an isolated cached Paper server before using `--promote`. The scoped build preserves all unrelated baseline classes and resource packs.

Validated on isolated Paper: 51,505 checks with Advance Magic active and 51,249 checks with it disabled after startup. Checks cover all 50,000 tier/ticket combinations, the 18 enabled Rare and five Legendary factories, exact rate boundaries, one bonus maximum, unchanged major rewards/supplies, and preservation of Tier 1–2 loot. The legacy 10,000-ticket loot checks also pass. All 586 unrelated JAR entries, including five pack files, remain byte-identical to the delivered e2.131 baseline.

Disabling Advance Magic after startup exercises the fallback. Removing its JAR entirely still encounters an existing `SeraphShop` API class reference during Evergarden startup; this release does not change that separate integration.
