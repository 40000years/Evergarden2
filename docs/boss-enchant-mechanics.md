# Boss enchant transfer and runtime effects

13 October 2026 · Evergarden `3.0.0-e2.51-boss-enchant-transfer-and-effects` · tested on the installed Paper 26.2 build

The screenshot's Protection VIII book became Protection IV because the native anvil applied the enchant's normal maximum when copying it. Writing VIII into a loot item had already worked; transferring it and handling separate runtime saturation needed additional logic.

## Anvil rules

Boss loot now records a separate entitlement for **each awarded over-cap enchant at its actual level**. The anvil recomputes with a temporary level bypass for that operation, then limits every enchant to its vanilla maximum or its already-awarded boss entitlement. It restores the native view restrictions before returning. Global enchantment registry levels, villager trades and enchanting tables are not changed.

| Inputs | Result |
| --- | --- |
| Plain diamond chestplate + boss Protection VIII book | Protection VIII; 8 XP with no prior work |
| Boss VIII + boss VIII | VIII, never IX |
| Boss VI + boss VIII | VIII, preserving the existing higher award |
| Unmarked Unbreaking IV book + another unmarked IV | Native capped result; cannot create V |
| Boss Sharpness VIII sword + ordinary Unbreaking IV book | Sharpness VIII stays; Unbreaking is capped at III |
| Boss Fortune pickaxe + Silk Touch book | Incompatible operation remains blocked |
| A stripped boss item + ordinary Sharpness V book | V; no dormant right to create VI |

The result keeps native repair/name behavior, compatibility, prior work, survival XP payment and input consumption. The ordinary Too Expensive rule still applies. Tests cover the boundary where a preserved VIII costs 39 levels and the next operation costs 40. A result click consumes both inputs once and charges the player once; another click cannot replay it. Persisted high levels and their individual entitlements survive a real restart.

Already-awarded e2.50 loot is recognized by its existing boss origin, material, actual enchant and configured range, so existing high books and gear work without refilling any chest. If an operation combines loot with a legacy Limit Break enchant, the stronger existing virtual level is retained, or the weaker virtual metadata is removed before adopting the native level. Native and virtual bonuses are never applied twice for that enchant.

## Actual effects

All **31 distinct enchantments** in the loot configuration were enumerated and tested at every configured level. Most native effects already read the real high level and need no additional multiplier. The following values describe the installed Java 26.2 mechanics; contextual effects still require their normal conditions.

| Enchant | Loot levels | Verified effect |
| --- | --- | --- |
| Sharpness | VI–VIII | Native added damage 3.5 / 4 / 4.5 |
| Power | VI–VIII | Native arrow damage addition 3.5 / 4 / 4.5 |
| Impaling | VI–VIII | Native +15 / +17.5 / +20 against susceptible entities |
| Breach | IV–V | Native armor effectiveness factor 0.40 / 0.25 |
| Density | V–VII | Native added smash damage per fallen block 2.5 / 3 / 3.5 |
| Sweeping Edge | III–V | Native sweeping ratio 0.75 / 0.80 / 0.8333 |
| Fire Aspect | II–III | Native fire duration 160 / 240 ticks |
| Flame | I | Native projectile fire duration 2,000 ticks |
| Punch | II–III | Native knockback addition 2 / 3 |
| Quick Charge | III–IV | Native crossbow charge duration 10 / 5 ticks |
| Piercing | IV–VI | Native projectile piercing value 4 / 5 / 6 |
| Multishot | I | Native projectile count 3 |
| Loyalty | III–IV | Native return acceleration value 3 / 4 |
| Riptide | III–IV | Native spin strength 3 / 3.75; requires the normal wet condition |
| Channeling | I | Native lightning summons under thunder and open sky |
| Wind Burst | I–III | Native explosion knockback multiplier 1.2 / 1.75 / 2.2; requires a qualifying smash |
| Efficiency | VI–VIII | Native mining efficiency addition 37 / 50 / 65, on top of material tool speed |
| Fortune | IV–V | Real diamond ore drops reach 5 / 6; expected mean 2.667 / 3.143 |
| Silk Touch | I | Native silk drop behavior and zero block XP |
| Looting | III–V | Native equipment drop chance addition 3 / 4 / 5 percentage points; other mob drops follow their own loot table |
| Unbreaking | III–VI across the gear profiles | Tool damage probability `1/(L+1)`; armor probability `0.6 + 0.4/(L+1)` |
| Mending | I | Native 2 durability repaired per XP point |
| Infinity | I | Native ordinary-arrow consumption 0 |
| Respiration | III–V | Native oxygen bonus attribute 3 / 4 / 5 |
| Aqua Affinity | I | Native submerged mining multiplier addition 4, giving the normal 5× factor |
| Frost Walker | II–III | Native ice disk radius 4 / 5; its radius cap of 16 is not reached |
| Soul Speed | III–V | Native movement speed addition 0.0615 / 0.072 / 0.0825 on qualifying soul blocks |
| Protection | VI–VIII | Native EPF per piece 6 / 7 / 8, with the boss-only extension below |
| Feather Falling | V–VII | Native fall EPF 15 / 18 / 21, with the same boss-only extension |
| Depth Strider | III–V | Native water efficiency reaches its cap at III; IV/V receive the configured +10%/+20% movement extension in water |
| Swift Sneak | III–V | Native sneak factor 0.75 / 0.90 / 1.05; V gets the 5% overflow beyond the attribute's 1.0 ceiling |

Protection and Feather Falling share the native EPF ceiling of 20. The extension counts only excess contributed by authorized boss enchants. For each such point above the native ceiling, remaining damage is multiplied by `0.96` by default. Four Protection VIII pieces have EPF 32, giving an enchantment damage factor `0.20 × 0.96^12 = 0.1225419515`. This is a continuous extension that stays above zero; damage types that bypass enchantments remain exempt. Ordinary Protection IV equipment retains its native factor of 0.36 for four pieces. The extension changes the enchantment damage modifier before absorption, rather than adding a second full native enchant bonus.

Movement extensions use named transient player attribute modifiers. They apply only under the relevant water/sneaking condition and are removed when equipment or state changes, on logout and when the plugin shuts down. They do not overwrite another plugin's movement modifiers.

## Configuration

The server reads `plugins/Evergarden/boss-treasure.yml`. These keys are added automatically if missing from an existing file, while its loot settings are retained:

```yaml
mechanics:
  anvil-transfer: true
  protection-extra-point-reduction: 0.04
  depth-strider-extra-level-speed: 0.10
  swift-sneak-overflow: true
```

Restart after editing. Loot levels and relative weights still use the existing equipment entries. No option enables equal high levels to manufacture a higher level. The default configuration is [boss-treasure.yml](../evergarden/src/main/resources/boss-treasure.yml).

Install [dist/evergarden.jar](../dist/evergarden.jar) and restart. Structure decoration, placement and one-time chest ledgers remain unchanged.

## Evidence and scope

The tests use native Minecraft enchant helpers, registered effect definitions, real diamond ore drop evaluation, actual damage events and native survival anvil result clicks. They enumerate every configured enchant and level, sample 15,000 chest rolls, sample 20,000 durability attempts per level and item category, and sample 10,000 ore drops per Fortune level. Both tool and armor Unbreaking formulas are checked. A second real Paper boot verifies serialized items, preserved levels, exact entitlements and resistance to equal-level promotion.

Tests are in [BossEnchantProbe.java](../tests/boss-enchants/BossEnchantProbe.java). Quantitative results and contextual-effect definitions are an audit of native mechanics; they are not a claim of testing every possible mob, movement trajectory, environment or third-party plugin combination. Vanilla predicates such as thunder for Channeling, sensitive entities for Impaling and soul blocks for Soul Speed still apply.

The final release passed **677 checks** across two enchant-test boots and two structure-test boots, including the eight actual chests in each structure and their individual transfer entitlements. Reports are in `output/boss-enchant-validation-v10-20261013/` and `output/boss-enchant-structures-validation-v1-20261013/`. The [quantitative runtime results](../output/boss-enchant-release-v4-20261013/runtime-checks.txt) and [release manifest](../output/boss-enchant-release-v4-20261013/release-manifest.json) record the tested artifact and its hash.

The official [AnvilView API](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/inventory/view/AnvilView.html) describes the native repair-cost controls. Exact implementation behavior and the effect formulas were inspected from the installed Paper libraries and Minecraft 26.2 client data, then verified against that running server.
