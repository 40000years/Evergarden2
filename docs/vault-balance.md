# Vault reward balance

Release: **Evergarden `3.0.0-e2.174-vault-specials`**, validated on **2026-10-30**. The final candidate passed **577,932 checks** on isolated Paper 26.2 with Advance Magic 1.38.2-spell-audit.

This release increases special reward rates at Tiers IV–V and reduces Scroll of Eternity, the Unbreakable reward, at Tiers II–V. Equipment/book quality and Limit Break grades retain the e2.173 rules. Every opening selects one primary item. Tiers II–V may give one additional equipment item or book, for a maximum of two items, each with amount one. Mana potions are absent from the Vault pool. Newly generated Eternity scrolls no longer display the obsolete fixed `MYTHIC 0.5%` claim; tier buttons show the current chance from the production table.

## Primary rewards

These are total chances per opening, shared by Dark, Astral and Time Vaults. Each column totals 100%.

| Reward | Tier I | Tier II | Tier III | Tier IV | Tier V |
| --- | ---: | ---: | ---: | ---: | ---: |
| Diamond equipment / bow | 65% | 61.25% | 56.6% | 33.8% | 26.7% |
| Configured enchant book | 30% | 31% | 30% | 22% | 18% |
| Limit Break scroll | 3% | 3% | 3.5% | 5% | 5% |
| Unique Enchant scroll | 1.8% | 2% | 3% | 4% | 4% |
| Rift / Smelter Pickaxe | 0.2% | 1.2% | 2% | 5% | 6% |
| Rare modeled weapon | 0% | 1% | 3% | 17% | 22% |
| Legendary modeled weapon | 0% | 0% | 0.3% | 4% | 6% |
| Elemental staff | 0% | 0.5% | 1% | 4% | 5% |
| Flying Staff | 0% | 0% | 0.5% | 2% | 2% |
| Flying Staff tier 2 (two seats) | 0% | 0% | 0% | 3% | 5% |
| Scroll of Eternity (Unbreakable) | 0% | 0.05% | 0.1% | 0.2% | 0.3% |

Weapons, relic tools and staffs occupy the primary slot. There are no independent weapon/staff bonus rolls, mythic rerolls or guaranteed supplies. A second item cannot be another rare reward. Legacy crafting core rewards remain retired.

The elemental reward is one Apprentice staff with three empty spell slots, chosen uniformly among all eight elements. `staff-loot.enabled` controls this reward. The legacy `staff-loot.vault-chance` setting does not set the tiered Vault rates above. Sky and Overworld chest staff rates are unchanged.

Both flying staff tiers require enabled Advance Magic; without it their selected reward falls back to one configured enchant book at the Vault's tier. Sky structure Flying Staff rolls still award tier 1 only. Previously claimed Vaults remain claimed.

At the previous Tier V two-seat staff rate of 1%, missing it across 100 successful openings had a 36.60% probability. At the new 5% rate, that probability is about 0.592%; the average is five staffs per 100 openings, without a guarantee. Eternity is now a chase reward averaging one per 500 Tier IV or 333 Tier V openings. Its lower-tier rates stay below the higher-tier rates.

## Second item

The second-item roll occurs only when the primary reward is ordinary diamond equipment/a bow or a configured enchant book.

| Vault tier | Chance after an eligible primary | Chance across all openings |
| --- | ---: | ---: |
| I | 0% | 0% |
| II | 10% | 9.225% |
| III | 15% | 12.99% |
| IV | 20% | 11.16% |
| V | 25% | 11.175% |

The second item is 65% equipment and 35% book, at the same tier as the primary draw. A primary scroll, relic tool, modeled weapon or staff gives one item total.

Equipment/books occupy 55.8% of Tier IV primary draws and 44.7% of Tier V draws. Including bonus items, their expected shares among all delivered items are about **60.24% / 50.26%**, respectively. The expected total per 100 openings is 111.16 items at Tier IV and 111.175 at Tier V.

## Equipment and book quality

Both pools read the existing `equipment.entries` profiles in `plugins/Evergarden/boss-treasure.yml`. Profile materials, enchantment types and weights remain authoritative. Restart after editing that file. No profile is added, no server config is rewritten, and world boss or boss structure equipment drops retain their own generation rules.

Equipment is restricted to nine materials: diamond helmet, chestplate, leggings, boots, sword, pickaxe, axe, shovel, and bow. Each item receives exactly **one** compatible native enchant. An equipment profile must originally support an enchantment above its vanilla ceiling to enter this pool. This eligibility filter excludes Mending I and similar ordinary-only enchants from equipment. The tiered level subsequently awarded may be at the vanilla ceiling, especially at Tier I; equipment is no longer guaranteed to be overcap at every tier.

Books use the configured `ENCHANTED_BOOK` profiles and receive exactly one real stored enchant. Ordinary profiles whose configured maximum is at or below the vanilla maximum retain their complete configured range at every tier. Fixed Mending I stays I.

For a profile above the vanilla maximum, let `base = min(configured minimum, vanilla maximum)` and `top = configured maximum`. The quarter and midpoint round upward:

| Vault tier | Awarded level range |
| --- | --- |
| I | base through first quarter |
| II | first quarter, fixed |
| III | first quarter through midpoint |
| IV | midpoint through top |
| V | top, fixed |

The shipped six book profiles each have weight 3:

| Selected enchant | Configured range | Tier I | Tier II | Tier III | Tier IV | Tier V |
| --- | --- | --- | --- | --- | --- | --- |
| Sharpness | VI–VIII | V–VI | VI | VI–VII | VII–VIII | VIII |
| Protection | VI–VIII | IV–V | V | V–VI | VI–VIII | VIII |
| Fortune | IV–V | III–IV | IV | IV | IV–V | V |
| Efficiency | VI–VIII | V–VI | VI | VI–VII | VII–VIII | VIII |
| Power | VI–VIII | V–VI | VI | VI–VII | VII–VIII | VIII |
| Mending | I | I | I | I | I | I |

Tier V fixes the level of the **selected** enchant at its configured maximum. It still rolls the profile: a book can be Mending I instead of Sharpness VIII. Looting is absent from the shipped book profiles; an administrator can add a compatible book profile. Untiered `VaultEnchantRewards.gear(random)` and `.book(random)` remain compatibility APIs with their previous full ranges; production Vault openings use the tiered APIs.

## Limit Break grades

New Vault Limit Break scrolls record their Vault tier and maximum permitted enchant level. They still add one level per use, and cannot upgrade an item already at or above that scroll's cap. Multiple low-grade scrolls cannot bypass the cap. Java and Bedrock use the same validation and item factory. New sky treasure Limit Break scrolls use grade III.

| Enchant group | Grade I | Grade II | Grade III | Grade IV | Grade V |
| --- | ---: | ---: | ---: | ---: | ---: |
| Sharpness / Power / Efficiency / Smite | VI | VI | VII | VIII | VIII |
| Protection | V | V | VI | VIII | VIII |
| Fortune / Looting / Unbreaking / Thorns / Depth Strider / Sweeping Edge | IV | IV | IV | V | V |
| Feather Falling | V | V | VI | VII | VII |
| Density | VI | VI | VI | VII | VII |
| Breach | V | V | V | V | V |

Adjacent grades may share a cap when only a few higher levels are available; Breach has one allowed step above vanilla. A cap never exceeds the Limit Break type's own maximum. Partially present, malformed or unsupported grade/cap metadata cannot create a new higher entitlement. Previously earned **ungraded** Limit Break scrolls keep their legacy type maximum, and existing equipment is never downgraded.

## Anvil compatibility and existing saves

`mechanics.anvil-transfer: true` in `boss-treasure.yml` enables the existing high-level transfer behavior. Generated equipment and books are stamped after writing their actual enchant. The existing listener preserves that awarded level on compatible items. Combining equal high-level books does not create a higher level. Item compatibility, enchant conflicts, XP costs and input consumption still apply.

Existing earned items, enchant entitlements, keys and per-player claimed Vaults retain their persistence. This release does not refill claimed loot. Untiered admin reward previews use the real Tier I primary pool.

## Build and validation

Build with `python tools/build_vault_specials_release.py`. The candidate is `output/vault-specials-20261030/evergarden.jar`. Reuse the prepared isolated Paper server from the previous Vault run:

```powershell
python tools/run_vault_economy_checks.py --server output/vault-tandem-loot-20261030/paper --garden-jar output/vault-specials-20261030/evergarden.jar --prepared --link-cache
python tools/build_vault_specials_release.py --promote --validation-server output/vault-tandem-loot-20261030/paper
```

The scoped build preserves **671 unrelated delivered JAR entries**, including resource assets. Advance Magic is unchanged. Promotion requires the successful matching Paper receipt for this exact candidate, dependency and current probe. The reusable fixture receipt is `output/vault-tandem-loot-20261030/paper/vault-economy-validation.json`; promotion retains the receipt, report and frozen probe source under `output/vault-specials-20261030/`.

The successful probe enumerates all 50,000 primary tickets and verifies tier-quality endpoints, all 14 Limit Break types across five grades, both staff tiers/models, the two-item cap, survival anvils, missing Advance Magic fallbacks and actual test-GUI clicks. GUI coverage compares delivered-opening/category counters with actual inventory contents, including interleaved tiers, full-batch refusal, preview protection and revoked permissions. Bedrock form delivery alone was mocked; the upgrade code was real, and no connected Bedrock client was used. Earlier reports remain historical evidence.

In `/evergarden test`, select **ทดสอบ Vault · Tier I–V** (or right-click Relics Showcase), choose Dark/Astral/Time, then click a Tier. A click opens once; Shift-click opens ten times. The view shows the loaded plugin version, successfully delivered openings by tier and actual reward-category totals. This uses production odds and tier quality, consumes no keys and changes no claimed-Vault state.

Preview slots show only the last opening of a batch, even after Shift-clicking ten times. Category totals summarize all delivered rewards. A full inventory rejects the entire batch and adds nothing to successful-opening counters; attempted clicks are not delivered openings. Clear enough inventory space between batches. Last-result icons cannot be withdrawn.

After validation and promotion, install the resulting Evergarden JAR with the server stopped, then restart and verify `/version Evergarden` reports `3.0.0-e2.174-vault-specials`.
