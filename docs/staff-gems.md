# Two Gem sockets in /spell

Advance Magic `1.31.1-max-gems` and Evergarden `3.0.0-e2.143-max-gems` provide Damage, Cooldown and Max Gems in two removable staff sockets. This is a test-server reset of the old staff upgrade path; old ranks do not grant additional bonuses or compensation.

| Gem build | Spell damage | Spell cooldown | Max casts with default base 300 |
| --- | ---: | ---: | ---: |
| Empty | unchanged | unchanged | 300 |
| Damage + Damage | +30% | unchanged | 300 |
| Cooldown + Cooldown | unchanged | −30% | 300 |
| Damage + Cooldown | +15% | −15% | 300 |
| Max + Max | unchanged | unchanged | 800 |
| Max + Damage | +15% | unchanged | 550 |
| Max + Cooldown | unchanged | −15% | 550 |

Each Damage or Cooldown Gem contributes 15 percentage points. Each Max Gem adds 250 casts of capacity. Identical Gems add together and every kind uses one of the same two sockets. Damage and cooldown factors combine with the existing Skill Tree, spell level and elemental affinity. The spell-level cooldown factor and Gem factor remain separate, so two Cooldown Gems also work with levels I–VII.

## Use

1. Hold a staff and open `/spell`. Two Gem sockets appear on the left below the staff preview.
2. Click a Gem in the menu's inventory list or directly in your inventory, then click socket 1 or 2.
3. Click an occupied socket without a Gem selected to take it out. Select another Gem first to replace it and receive the old one back.

Insertion consumes exactly one physical Gem. Removal returns it without a fee. Replacement returns the old Gem in the same inventory transaction. If there is no room for a returned Gem, nothing changes. Gems remain attached through casting, spell extraction, tier upgrades and item serialization. Replacing the held staff closes its old menu. GUI preview icons are display copies and cannot be installed as real Gems.

All wooden and elemental staff tiers have two sockets. All three Gem kinds use existing Java/Bedrock Core models, with names and lore describing their use. They are available in the admin magic item menu and in the existing landmark and boss-structure upgrade loot. The three equal outcomes are Max Gem, Damage Gem and Cooldown Gem. Existing loose Repair/Damage/Cooldown Cores are accepted in the Gem UI as their matching Gem.

Max Gems increase capacity only: inserting or replacing a Gem preserves the current remaining casts and does not repair the staff. Removing Max clamps current remaining casts to the new capacity and persists that lower value. Reinstalling the Gem does not restore the clamped casts. A depleted staff remains depleted until repaired. If extraction is blocked by a full inventory, capacity and remaining casts both stay unchanged.

## Retired staff ranks and repair

Dragging Cores onto a combat staff no longer upgrades it to +10. Only the two sockets determine its equipment damage, cooldown and capacity bonuses. The old Damage, Cooldown and Max Durability rank tags are ignored and removed when the staff is migrated or socketed. Staff lore displays socket contents and their combined bonuses.

Combat staffs retain their configured base cast capacity (default 300), the existing one-use-per-successful-cast rule and Restoration repair paths. Restoration refills to the current capacity, including Max Gems. Fresh staff initialization uses that capacity instead of the legacy 30-use wand default. Max Gems can also act as the flying staff's old Repair Core: dragging one onto a flying staff grants its legacy +5 capacity per rank, up to +10. Flying staffs do not receive combat Gem sockets.

## Combined release

Install both JARs together with the server stopped, then restart. Embedded resource packs are unchanged. This release also includes the previously requested Vault book/tier balance over the latest map and Bedrock pickaxe fixes, so the main Evergarden JAR no longer drops those Vault changes when switching releases. Spell levels I–VII and scroll fusion are preserved from Advance Magic 1.30.0.

Build the Max Gem update with `python tools/build_staff_max_gem_release.py`. Promotion requires a matching Paper validation receipt for the paired candidates and checks that delivered baselines and candidate sources have not changed before replacing the main JARs. Baselines are kept under `output/staff-max-gems-20261026/before/`. The update preserves the latest worldgen release, Vault balance, spell levels, scroll fusion, map/pickaxe fixes and all embedded resource packs.

The initial two-kind release passed **1,069 Gem checks**, **3,147 spell-level checks** and **103,426 Vault checks** on isolated Paper (107,642 total). The Max Gem update passed **2,747 checks**, extending the Gem probe to all nine ordered Gem pairs on every staff element/tier, capacity/repair persistence, repeated swaps without free repairs, a depleted staff, full-inventory extraction, flying staff compatibility and real GUI insertion/removal. The scoped Max update preserves 180 unrelated Advance Magic entries and 607 unrelated Evergarden entries, including all resource packs and world generation. Client-side appearance was not checked in a connected Java/Bedrock client.
