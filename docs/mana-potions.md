# Amethyst mana potions

Implemented in Advance Magic `1.8.3-quiet-mana-clean-wands`. The normal brewing stand handles fuel, the 400-tick brewing duration, inventory automation and bottle consumption. No new client pack is required.

1. Fill up to three bottle slots with water bottles and fuel the stand with Blaze Powder.
2. Brew Nether Wart into vanilla Awkward Potions.
3. Brew one Amethyst Shard into mana potions. Each occupied Awkward Potion slot rolls its quality independently; other potions and empty slots are preserved.

| Quality | Probability per bottle | Mana restored | Nausea |
| --- | ---: | ---: | --- |
| Low | 60% | 20 | Second and subsequent low bottles within 60 seconds |
| Medium | 30% | 35 | Fifth and subsequent medium bottles within 60 seconds |
| Good | 8% | 50 | None |
| Supreme | 2% | 100 | None |

Nausea lasts 30 seconds, refreshing when another dose reaches its threshold. Configure the duration under `mana-potions.nausea-seconds` (10–120 seconds). Dose windows are tracked separately for low and medium quality and survive logging out. Restore values are configurable under `mana-potions.restore`; mana never exceeds the player's current maximum.

Supreme quality grants +10 permanent maximum mana. Other qualities roll +2 through +5 with equal probability on a successful progression roll. The chance starts at 2%, increases by 0.5 percentage points for each previously consumed mana potion, and stops at 15%. Total potion doses are persistent across sleep, death and reconnects.

Potion progression caps at 200. At 195, a Supreme potion grants the remaining five; at 200 it only restores mana. Existing administrator-set maximums above 200 are preserved and cannot grow from potions.

Every successful increase subtracts the same number of **HP units** from maximum health until a successful native Paper deep-sleep transition (normally 100 sleeping ticks / about five seconds). Ten HP means five hearts. Leaving a bed early or cancelling deep sleep does not restore the maximum. Sleeping restores maximum health, without automatically healing the player or removing another plugin's health modifiers.

When paying the complete HP cost would leave less than two HP, permanent growth is skipped and the bottle still restores mana. This allows a player with 20 maximum HP to gain +10 once, then requires rest before another Supreme gain. Exhaustion is stored in player persistent data and reapplied after join, respawn and once per second to account for changed health equipment. Removing health buffs cannot reduce the exhaustion-adjusted maximum below one heart; the outstanding exhaustion remains until sleep.

Mana bottles use identical fixed-color bottle artwork in the Java and Bedrock packs, selected through item models and Geyser v2 mappings. Pack version 2.11.4 requires a server/Geyser restart and reconnect to update Bedrock custom item registrations. Short tooltips use explicit colors and disable italics. Existing tagged bottles migrate on login, inventory opening, item pickup and entity loading while retaining their amount and quality. Renaming a bottle cannot forge a mana potion. Brewing does not convert them into splash or lingering versions. Vanilla drinking owns the correct hand, creative behavior and glass bottle. Cancelled consumption grants no restoration or progression.

The in-game Evergarden magic guide includes the recipe and recovery rules. The final local isolated Paper probe passed **372 checks**, including 71 potion checks and the existing 301 casting checks. These cover native Nether Wart and Amethyst brewing, independent bottle rolls, partial stands, cancelled brewing/drinking, vanilla glass bottles, quality restoration, nausea thresholds, cap behavior, exact HP costs with external attribute modifiers, persistence and successful/cancelled deep sleep. Native sleeping retains the penalty at 99 ticks and restores the maximum at 100. Java/Bedrock player clients have not been manually exercised in this change.

Run the probe with a cached Paper server:

```powershell
python advance-magic/tests/run_basic_casting_checks.py --source-server output/distinct-basics-final-validation-20261013 --server output/mana-potions-validation-fresh
```

Potion progression, insufficient-HP growth and deep-sleep recovery no longer send gameplay chat notices. Wand and flying-staff tooltips retain their numeric stats while Thai control instructions and spell descriptions are removed. Existing items migrate through the normal inventory/entity paths. No in-game guide pages were changed for this UI reduction.
