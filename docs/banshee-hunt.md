# Banshee ranged pressure - e2.92

Extends e2.91's soul veil encounter. Health remains 500, with three 30-HP
crystals and a 12-second vulnerable phase. Soul damage remains five HP per
accepted impact, with the existing armor bypass and Totem rules.

The sonic attack now reaches targets up to 26 blocks away. It releases three
travelling rays at -10, 0 and +10 degrees, sharing a victim set so overlapping
rays cannot multiply damage. Rays travel at 1.05 blocks/tick for at most 34 ticks
and respect cover. Attack cooldown is 55 ticks. This pressures straight retreats
while preserving sideways dodges and physical projectile travel time.

The curse targets the player's ground position from up to 28 blocks away. Its
5.5-block circle follows for the first eight ticks, then locks for the remaining
24 ticks before impact. A native cyan particle ring supplements the custom
sigil. The existing 10-second healing seal and Hunger apply on contact. Curse
cooldown remains 260 ticks. Revealing the boss cancels pending curse damage and
removes its warning circle.

When a target is farther than 12 blocks, Banshee can commit to a flank blink.
The destination is fixed on commitment and marked for 12 ticks. Displacement is
capped at 20 blocks; landing requires solid ground and clear room for the model.
Obstructions are checked again on arrival. Blink cooldown is 110 ticks, with the
first opportunity after 70 ticks. Arrival itself causes no damage and leaves at
least 10 ticks before an attack and 12 ticks before a curse. The existing reform
animation and soul trail accompany the move. Pending blinks and markers are
cleared on phase transitions, target loss and encounter cleanup.

ModelEngine and Geyser carriers update to the landing position. Existing model
animations and resource pack 3.33.0 are reused without asset changes.

Validation uses isolated Paper servers and an actual ModelEngine 4.1.1 runtime;
reports are recorded in the release-validation.json beside the release build.
These checks cover native entities, projectile contacts, locked curse dodging,
phase interruption, safe blink cancellation and carrier/bone synchronization.
They do not substitute for a connected player's assessment of encounter feel.
