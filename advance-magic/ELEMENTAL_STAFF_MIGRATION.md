# Elemental staff model phase

The live offensive item lineup is eight models. There is no new spell selection,
elemental advantage combat, crafting recipe, or skill loadout in this phase.

- `Fire → Wind → Earth → Ice → Water → Fire`
- `Lightning → Light → Dark → Lightning`
- Java item model and Geyser string selector: `advance_magic:staff_<element>`.
- `/magic staffs` opens the preview; administrators can receive the selected item.
- `/magic staff give <element|all> [player]` issues the new models.
- `/magic items` is the administrator version of the same menu. Restoration
  utilities, flying staff, and the five legendary weapons remain available.

## Existing items

Only carrot-on-a-stick items with a valid, consistent legacy wand PDC identity
are migrated. Login, held-slot selection, inventory opening, dropped items, and
loaded item frames reach the migration path. No inventory is deleted.

| New element | Legacy wand identities |
| --- | --- |
| Fire | `dragons_breath`, `blaze_barrage`, `meteor_strike` |
| Wind | `sonic_boom`, `shulker_levitation` |
| Earth | `natures_bloom`, `earth_wall`, `iron_armor`, `verdant_sovereign` |
| Ice | `frost_nova`, `chronos_final_hour` |
| Water | `guardian_beam` |
| Lightning | `lightning_strike` |
| Light | `solar_apocalypse`, `heavens_judgment` |
| Dark | `shadow_step`, `void_pull`, `wither_ray`, `vex_legion`, `lunar_requiem`, `crimson_oblivion` |

The migration changes model, selector, generated display name, lore, and maximum
stack size. It retains the item amount, original wand identity, all other PDC
including foreign ownership/Soulbound data, enchantments, mastery, durability,
and upgrade levels. Player-assigned names and unrecognized custom lore remain.
Original display name and lore
are also archived as `advance_magic:retired_wand_name` and
`advance_magic:retired_wand_lore` for recovery. Repeating migration is a no-op.

Player input and item-backed cast calls cannot invoke the legacy spell. The old
Spell enum, registry, events, and itemless spell API remain for other server
features. `wands().spell(item)` continues to read preserved legacy identities;
new items from `staffs().create(element)` have no assigned legacy spell.

## Retired issuance

Old spell-specific give commands, craft menus, all historical shaped recipes,
and Bedrock creative resolution no longer issue offensive legacy wands or cores.
Evergarden's old showcase opens the new eight-staff menu. Its old grant-all/core
buttons and give aliases are removed; `staff_<element>` is the new give ID.

Vault draws use 9,250 active tickets by excluding the former 700 ordinary core
and 50 mythic core tickets, retaining the relative odds of all other existing
rewards. New world-boss victories award the existing keys and XP without adding
a legacy core. Already queued core entitlements remain claimable as their exact
original core. Existing cores retain models/textures and their restoration-fuel
compatibility; they are not consumed or converted during this change.

The optional held effect emits two colored dust motes per holder every four
ticks. Disable it with `elemental-staffs.ambient-effects: false`.

## Runtime regression probe

`tests/ElementalStaffChecks.java` is a disposable Paper 26.2 plugin probe. Its main
class is `ElementalStaffChecks`; depend on `advance-magic`. It writes
`elemental-staff-result.txt` and shuts down its isolated server. It tests real
commands, inventories, item migration, input events, and native crafting. When
Evergarden is loaded, it also checks its showcase/give routes and active loot
table. It is not installed on a production server.
