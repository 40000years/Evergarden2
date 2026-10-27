# Seraph / Revenant combat powers

Advance Magic `1.23.0-armor-combat` + Evergarden `3.0.0-e2.123-armor-combat`.
Install both JARs together and restart. Existing armor updates when a player joins,
opens a container, or carries/wears a piece. Models and resource packs are unchanged
(Advance Magic Bedrock pack remains 2.21.0).

## Obtain armor from bosses

Evergarden `3.0.0-e2.125-boss-core-textures` supplies one shared physical
Core of Seraph per Seraphon victory and one Core of Revenant per final Arkhaia
victory. At the Seraph NPC, **ร้านตำนาน** exchanges 1 matching core for any
one chosen piece of that set. Four cores buy all four slots. No extra materials
or daily/weekly limits apply. The boss at boss-temple is deferred.
See [boss-armor-exchange.md](boss-armor-exchange.md).

## Item presentation and base stats

- Base lore is just `Combat`. The original Thai description and `1 / 4` set label
  are removed from existing pieces. Player names, enchantments, upgrade PDC and
  additional upgrade lore are preserved.
- Native netherite durability: helmet 407, chestplate 592, leggings 555, boots 481.
  Armor is damageable and breaks normally. No base Unbreakable flag.
- Armor points are 150% of netherite: helmet 4.5, chestplate 12, leggings 9, boots 4.5.
  Toughness is 4.5 per piece. A complete set has 30 armor and 18 toughness.
  Native 0.1 knockback resistance per piece is preserved.
- Minecraft's armor formula and caps still apply. Increasing armor/toughness by
  50% does not mean a flat 50% reduction to every incoming damage type. Damage
  types that bypass armor retain their native behavior.

## Revenant

- Any equipped piece provides one death guard, independently recharging after
  ten real minutes. Four equipped pieces provide up to four guards.
- Charges belong to the player's four armor slots, not a particular item. Swapping
  armor, relogging or restarting does not reset cooldowns. Only a currently worn
  Revenant slot can spend its own charge. Guards restore 1 HP, extinguish fire,
  and grant the normal 20-tick hurt cooldown; a void rescue moves to respawn/spawn.
- Existing damage cancellation, Protection and custom enchant reductions settle
  before checking fatal damage. Phoenix Rebirth can rescue first; a cancelled
  fatal hit never spends a Revenant charge. Native Totems remain usable once no
  worn Revenant charge is ready.
- **Full set:** heal 50% of actual health damage inflicted. Normal damage settles
  after native health loss; cancelled hits, absorption-only hits, invulnerable boss
  phases and excess overkill do not create healing. Virtual boss controllers report
  only their confirmed health loss. Healing still respects healing-cancellation effects.
- **Full set:** each distinct Sanctuary victory adds 2 max HP, capped at 12 for
  Acheron, Phlegethon, Cocytus, Banshee, Malefeasance and Styx. Only present,
  contributing players wearing the full set at victory receive progress through
  the existing Sanctuary reward path. Echoes, river summons, ordinary mobs,
  Seraphon, repeated victories and another player do not add duplicate progress.
- Progress is saved per player. The max HP modifier is active only with a full set;
  unequipping removes the bonus and clamps current HP to the remaining maximum.
  Reequipping restores the earned bonus, without adding a second modifier.

## Seraph

All powers require the complete set. As requested, the damage multipliers are
separate: **undead x4, other enemies x2**, never x8/x10. Melee, projectile and
player-attributed spell/elemental damage use the same rule.

Since Advance Magic `1.29.1-seraph-boss-targeting`, ordinary undead cannot select
the wearer as a target; their existing targets and pathfinding are cleared.
Bosses retain normal target selection and pursuit, including the six scripted
Sanctuary/river bosses, admin combat tests, world bosses and dungeon bosses.
The same boss exclusion applies to the native targeting listener, periodic
target clearing and Evergarden's scripted target selection. Boss identity uses
native boss types and the existing encounter PDC/scoreboard markers.
This does not grant immunity to projectiles or area hazards already present in
the world. Damage multipliers remain undead x4 and other enemies x2.

## Enchantments and upgrades

Native armor materials are retained, so vanilla enchanting, anvil repair, Mending
and Unbreaking keep their normal eligibility and behavior. Existing Protection
IV -> V through X scroll upgrades, unique enchantments and boss-enchant transfers
retain the item model, set identity, native stats and damage.

An unenchanted piece has no durability immunity. Applying the existing Scroll of
Eternity deliberately protects it from wear under the server's existing rules;
this is an upgrade effect, not the base armor behavior. Existing incompatibilities
(such as Eternity versus Soulbound) still apply.

Sources: `ArmorSetService`, `ArmorCombatService`, Sanctuary `BossRewards`,
`ArkhaiaHitFeedback`, and existing `RelicService` / `BossEnchantEffects`.

## Validation

`tests/arkhaia/run.py --armor-sets` runs real Paper item serialization, migration,
native durability/breakage, slot cooldowns, full-set gating, actual hit/lifesteal,
undead targeting and the existing upgrade/enchantment paths. Test fixture reports
and final JAR hashes are recorded under `output/armor-combat-release-20261023/`.

Final candidate passed **154 armor checks + 147 Sanctuary checks = 301 Paper
checks**, plus **67,143 pure boss-rule checks**. The Sanctuary suite includes real
virtual-HP lifesteal, actual victory/repeat-victory HP grants and scripted undead
pursuit. Native saved player NBT independently contains the six-bit soul mask and
all four cooldown timestamps. All 91 pre-existing embedded pack/model entries
across both JARs are byte-identical to the preceding armor release.
