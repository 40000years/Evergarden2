# Malefeasance: dimensional sorcery

Release `3.0.0-e2.99-malefeasance-dimensions`; resource-pack version `3.36.0`.
Malefeasance has 600 native and encounter HP. This replaces the old shadows,
sacrifices, trail curses and bolt rotation in sanctuary, Arkhaia and combat tests.

## Immunity and movement

Damage issued inside Advance Magic's actual magic-damage context is rejected
before hit cooldowns, feedback confirmation or encounter HP are changed. The
Advance Magic affect event is also cancelled for the boss and its visual carrier,
blocking spells that request permission to apply control/status effects. Ordinary
melee and projectiles retain their existing damage and evaluated-body reach checks.

The caster opens a six-tick dimensional warning and blinks instantly to a
committed landing. A new warning becomes available 42 ticks after landing, giving
a nominal 48-tick / 2.4-second movement cycle. Destinations require solid footing,
11 blocks of body clearance, space from every player, a seven-block minimum hop
and a position inside radius 29. An obstructed landing is rechecked at arrival;
an unsafe blink is cancelled. The boss uses a portal draw, departure/reappearance
burst and a recovery animation instead of walking between attacks.

## Sky gates

Every ten ticks during a burst, player positions and three spreading arena
positions seed large dimensional seals twelve blocks above the floor. A sky
seal has radius 3.8; the matching ground warning and beam have radius 2.8.
Eight batches alternate between violet and crimson, followed by a 24-tick pause
before the next color's burst. A hard limit of 48 live gates bounds displays.
Existing gates keep their own color when the next burst changes color.

Each position is fixed at creation. The warning lasts 24 ticks and the beam
visibly descends over another six ticks, leaving 1.5 seconds before ground contact.
The eight-tick damaging window can hit each player once per gate. Terrain blocks
the beam and the damage ray; its visible column closes with its contact window.
The seals then shrink shut. Ordinary movement can leave the 2.8-block warning.

* Violet: 20 raw damage, then Wither II for 120 ticks / six seconds.
* Crimson: 24 raw damage, then Weakness I and an owned blood-debt mark for six
  seconds. The first red hit does not heal the boss. A later accepted beam hit
  on a still-marked, weakened player heals 60% of actual HP lost, at most 18 HP
  per hit and never above 600 HP. Absorption-only hits and rejected damage cannot
  provide healing. Removing Weakness stops the debt. Native Wither ticks, attacks
  by other entities and unrelated Weakness do not independently heal this boss.

Potion applications and debt belong to their caster. Reset, departure and defeat
clean up owned effects and restore an earlier still-valid effect when appropriate;
a stronger effect from another source is preserved.

## One rebirth

The first accepted lethal hit consumes a flag stored on the boss's encounter
spirit. It retains its receiver and encounter, returns at 90 HP (15% of 600), and
performs a protected 50-tick rebirth with a rising body, violet floor ritual,
crimson crown and spiraling motes. Old attacks and debts are closed. This first
lethal hit does not issue the encounter's final-death callback or rewards.

After the ritual the boss resumes fighting, and its second lethal hit defeats
it normally. Native receiver recovery preserves the one-use flag. A newly reset
encounter starts with a fresh charge. Blood-debt healing after rebirth can restore
HP toward the normal 600 maximum; 90 is the initial revival health.

## Model and effects

The approved hooded sculpture, floating grimoire, familiar, textures and geometry
are retained. Seven articulated clips append to its ten original clips: three
sky-casting clips, three portal clips and a two-second rebirth clip. Java and
Bedrock both use the same 17 indexes. The grimoire rises with one hand while the
other opens the sky, and the mantle moves during portal travel.

The custom violet/crimson seals have concentric engraved rings, geometric runes,
interlocked dimensional triangles and an eye. Their counter-rotating ground
warnings feed full-bright crossed beam planes with a bright core and braided
energy ribbons. Native colored particles remain visible without the pack.
Java item models and Bedrock particles share the generated textures.

`tools/update_malefeasance_skills.py` authors clips, patches owned pack assets and
renders geometry/texture previews into `previews/malefeasance-dimensional`.
`evergarden/tools/malefeasance_assets.py` also participates in normal pack builds.
These previews are not connected-client screenshots.

## Validation and delivery

`tests/arkhaia/run.py --malefeasance` exercises real Paper events, immunity,
actual-loss healing, safe blinks, beam warnings/cover, cleanup and both lethal
hits. Separate Cocytus and Styx regression fixtures cover the shared controller.
The official Model Engine fixture imports all 17 clips and plays every new state.
Independent Bedrock decoding checks the delivered geometry and animation poses;
intermediate body-clearance sampling checks arms against the torso and head.

Reports, binary, source snapshot and the previous JAR backup are under
`output/malefeasance-release-20261020`. Replace `dist/evergarden.jar`, restart and
accept the updated pack. No live server is modified. A connected-player playtest
is still needed to judge the final visual density and combat balance.
