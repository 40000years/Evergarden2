# Soulbound audit — 3 October 2026

This audit covers Evergarden's **Soulbound / วิญญาณสถิต** scroll, which retains
equipment after death. It does not establish that the user's name “ยึดติด” refers
to this item rather than vanilla Curse of Binding. No custom Curse of Binding
scroll was found in this repository.

## Confirmed duplication in the original release

On the preserved `3.0.0-e2.33-vault-security` release, a non-operator Survival
actor applies a Soulbound scroll to one wand, moves the result with real inventory
packets, and dies before the next scheduled inventory write. After native Paper
respawn, the actor has **two Soulbound wands** instead of one.

This is the same captured-item replay in `EnchantApplyListener` identified by
the earlier [wand audit](wand-security-audit.md). Soulbound can be used as its
catalyst; the scroll does not need a separate death-loop exploit. The copy also
retains `evergarden:ue_soulbound`, so Soulbound tags are not proof of illegitimate
origin.

The identical test on the current built `3.0.0-e2.35-bedrock-judge` artifact retains
exactly **one** wand. That artifact contains the earlier `e2.34-wand-security`
inventory transaction fixes. No new production code was changed by this audit.
This does not verify which artifact is installed on the live server.

## Death retention findings

Native deaths and respawns did not increase item count in the current build across
25 scenarios, including both values of `keepInventory`, main/offhand equipment,
armor, identical equipment pairs, full storage inventory, cancelled deaths,
another listener correctly retaining an item, and eight extra consecutive deaths.

The current handler removes retained items from the drop list before adding them
to Paper's keep list. This matches the [Paper PlayerDeathEvent API contract](https://jd.papermc.io/paper/26.2/org/bukkit/event/entity/PlayerDeathEvent.html).

However, the “never drops or disappears” description fails in eight test scenarios:

| Item location / condition | `keepInventory` | Observed result |
| --- | --- | --- |
| Soulbound + Curse of Vanishing in inventory | false | Equipment disappears |
| Soulbound on cursor in player inventory | false / true | Equipment drops |
| Soulbound on cursor while a world chest is open | false / true | Equipment drops |
| Soulbound in a crafting-table input | false / true | Equipment drops |
| Soulbound in the upgrade menu's equipment input | false | Equipment disappears |

These are **retention and item-loss bugs**, not newly confirmed duplication paths.
The current handler only examines `PlayerDeathEvent.getDrops()`. Vanishing items
are excluded from that list. Cursor, crafting, and upgrade-menu items are handled
by container closure separately, so the handler misses them.

The eight retention failures remain open. Fixing them requires handling the owned
items outside player inventory through death/container closure, including cancelled
deaths and full inventories, without introducing another delayed refund replay.

## Evidence and limits

`tests/soulbound-security/` creates a fresh localhost-only Paper 26.2 server, real
Survival actor objects, native inventory packets, actual deaths, and native respawns.
Probe tags distinguish the fixture items for exact counts; these tags are not a
production tracing feature. Production data and Bedrock hardware were not accessed.

- Current build: **75 assertions pass; 8 retention assertions fail**.
- Original release: **73 assertions pass; 10 assertions fail**. Two additional
  failed assertions detect duplication and incorrect retention in the same
  Soulbound application case.
- Results, console logs, and input JAR SHA-256 hashes are preserved under
  `.audit-plugins/soulbound-security-baseline-v2-20261003/` and
  `.audit-plugins/soulbound-security-original-release-20261003/`.

```powershell
python tests/soulbound-security/run.py --name fresh-audit --garden <evergarden.jar> --observe
```

`--observe` preserves and reports known failures without treating them as a runner
failure. Without it, any failed assertion returns a nonzero exit code. The probe
edits only its disposable test world and shuts down that test server when finished.
