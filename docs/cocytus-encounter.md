# Cocytus: frost tempest, ice prisons and arrow rain

Balance release: `3.0.0-e2.130-cocytus-balance`. Existing models and packs are reused.
Cocytus has 700 native and encounter HP. Sanctuary, Arkhaia and combat previews
share these mechanics through `RiverTrials` and `CocytusEncounter`.

## Arrow immunity

Ordinary, tipped and spectral arrows from bows and crossbows deal no damage,
including Power V and the exposed recovery windows. Rejected arrows do not consume
accepted-hit cooldowns. Melee, thrown tridents and actual Advance Magic damage
remain valid. An arrow hit displays `COCYTUS - ARROW IMMUNE` at most once per second.

## Frost tempest

Cocytus returns to the arena centre and creates up to sixteen temporary ice
pillars, distributed over two rings at ten and twenty-four blocks. Each pillar
is 2.6 blocks wide and 5.5 blocks tall. A 56-tick (2.8-second) warning gives
players time to reach shelter. An obstructed arena that permits no pillars
cancels the attack and opens recovery rather than producing an unavoidable cast.

Six waves fire twelve ticks apart. Each emits 72 arrows at each of three
heights, plus a committed aimed lane for every eligible player: 1,296 radial
arrows over the cast, before aimed lanes. The aimed lanes and elevated layers
threaten players standing on raised blocks. Arrows travel at 2 blocks per
tick, stop at terrain or the visible ice pillars, and deal 24 raw damage once
per player per wave. They do not home. Cover is tested against each arrow's
actual swept path. The last arrows finish travelling before the pillars shatter.
The cast completes at tick 160, after the last arrow's full flight allowance.
The boss then has a 1.8-second recovery window with 1.5x incoming damage.

## Ice prison

Two-block warning circles commit to the players' old ground positions for
22 ticks (1.1 seconds). A failed dodge raises the player onto the tip of a
2.8-block tapered ice spike. Position and jumping are held while camera aim
and attacks remain available. The invisible receiver inside the visible ice
accepts three reachable melee strikes, at least eight ticks apart; allies can
also help. Breaking it releases the player immediately. A successful ender
pearl teleport also releases them. A cancelled teleport does not release them.
Short plugin teleports cannot bypass the prison; administrator commands work.

The first execution arrow launches 50 ticks (2.5 seconds) after capture. The
remaining two launch fourteen ticks apart. Each deals 80 raw damage and flies
toward the committed player position, with at most about seven ticks of nominal
travel. Escape removes the pending prison arrows. The bow draw/release repeats
for all three shots. A surviving prisoner is ejected at capture age 100 ticks.
Release restores the original gravity state and grants five seconds of capture
immunity. Death, departure, missing prison entities and encounter cleanup also
release the player. If no player is captured, the move ends twelve ticks after
eruption. Breaking or escaping the last prison also lets the move end early.
The recovery window lasts one second.

## Arrow rain

Two rounds each commit five-block circles. The authored `rain_windup` raises
the articulated bow; the harmless upward arrow leaves at tick 28, four ticks
into the release animation. Falling arrows begin at tick 32 and arrive in
three dense pulses four ticks apart. They fall from twelve blocks above the
arena at 2 blocks per tick. A stationary player takes 45 raw damage once per
round. Leaving the committed circles avoids subsequent targeted vertical lanes.

The minimum warning before falling arrows launch is 1.6 seconds. Ground-level
contact follows about 0.25–0.3 seconds later. At 0.215 blocks per tick, clearing
the five-block radius plus a 0.3-block body allowance takes about 25 ticks.
Even after six reaction ticks (0.3 seconds), a player can clear it by tick 31,
before launch at tick 32. The warning positions never chase a dodging player.
Rounds start sixty ticks apart; the normal cast ends at tick 119, followed by
a one-second recovery. Extremely high arrows retain enough lifetime to reach the
floor, and the cast waits for those flights without starting a third round.

After recovery, the next Cocytus move starts sixteen ticks later, or eight ticks
later below 40% HP. Other bosses retain their existing recovery scheduling.

The dormant Cocytus sanctuary accepts activation up to twelve blocks above its
combat floor. Active combat retains airborne players above that activation height.
Its horizontal boundary remains 34 blocks.
All temporary geometry uses displays; the attacks do not edit terrain.

## Assets and validation

The existing sculpture and textures are preserved. Three upward archery clips
are appended to its ten original clips, with matching Java and Bedrock indexes.
`tools/update_cocytus_skills.py` authors the clips and patches only Cocytus's
native Bedrock assets, preserving the rest of the pack and refreshing its hash.
The balance release uses `tools/build_boss_hitbox_release.py` with the three
families `ArkhaiaMinions`, `CocytusEncounter` and `RiverTrials`, compiled over the
current delivered JAR. Every unrelated entry and all 117 model/pack entries are
preserved byte-for-byte.

Mechanics run in isolated Paper using `tests/arkhaia/run.py --cocytus`: 71 checks
cover arrow immunity, valid melee/magic/trident damage, cover, ordinary walking
dodges, prison escape, aerial travel, cleanup, actual rewards and Styx's existing
melee immunity, arrow vulnerability and combo timing. The separate
`--malefeasance` suite passes 54 checks for the shared damage routing.
The unchanged models retain their prior Java/Bedrock animation validation.
The older `--river-trials` and `--sanctuary` aggregate probes still stop at their
Arkhaia summoning and Seraph chase assertions respectively; both failures were
reproduced against the pre-balance JAR. See the release `validation.json`.
Balance reports and the released binary are under `output/cocytus-balance-20261025`.
These fixtures do not replace a connected-player balance and visual playtest.

Replace `dist/evergarden.jar` and restart; the existing resource pack is sufficient.
The release procedure does not modify a live server.
