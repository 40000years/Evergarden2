# Vault weapon drops

Evergarden `3.0.0-e2.174-vault-specials` increases modeled weapon rates at Tiers IV–V in the single primary Vault reward table. The final candidate passed 577,932 checks on isolated Paper 26.2, including exact production factory counts. These are total chances per opening, shared by Dark, Astral and Time Vaults:

| Vault tier | Any modeled weapon | Rare | Legendary |
| --- | ---: | ---: | ---: |
| I | 0% | 0% | 0% |
| II | 1% | 1% | 0% |
| III | 3.3% | 3% | 0.3% |
| IV | 21% | 17% | 4% |
| V | 28% | 22% | 6% |

A modeled weapon is the primary item and receives no second-item roll. A Vault cannot award multiple modeled weapons or combine a modeled weapon with a staff. Only ordinary diamond equipment/a bow or a configured enchant book can receive a second item. See [Vault reward balance](vault-balance.md) for the full pool, tier quality and second-item chances.

Rare weapons retain the thematic selection from enabled weapons. Legendary weapons use the existing Advance Magic factories for Excalibur, Longinus, Tyrfing, Mjolnir and Vajra, including their models, attributes, identities and abilities. The tiered one-enchant rule applies to generated ordinary equipment/books and does not rewrite these special weapon factories.

If the Legendary factory is unavailable, its winning primary slot falls back to an enabled Rare weapon. Tier II never selects Legendary. Normal key-tier checks, per-player claim limits, save-before-delivery behavior and inventory-overflow delivery remain in the production Vault flow.

Build and validation instructions are in [Vault reward balance](vault-balance.md). The scoped release preserves delivered resource packs and mirror configuration; it does not rebuild Advance Magic or change weapon assets.
