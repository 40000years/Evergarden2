# Acheron binding feedback and spinning quills - e2.86

The binding spell now has bright red particle boundaries, white sparks, an inner
ring, impact flash and a half-second impact circle. These native particles are
sent regardless of custom resource-pack availability. Caught players see a BOUND
countdown, sparks circling their body and a release message. Radius remains six
blocks, warning 36 ticks, root 60 ticks and anti-repeat immunity 300 ticks. The
first binding becomes eligible 40 ticks after spawn, rather than 140.

The ground mark is fixed at cast start. At contact it tests the circle's actual
ground-to-player line instead of the giant hidden mob's elevated eye ray. If a
column interrupts the caster's view after the mark appears, a player standing on
the already visible mark can still be bound. A wall between mark and victim
still blocks the ground effect. Dodging out remains safe. The impact picture is
held for ten ticks without repeatedly applying the binding.

Short plugin teleports/corrections, knockback and mounting no longer silently
undo or bypass root. Nearby gameplay teleports are cancelled while bound.
Explicit command teleports, cross-world travel and relocations beyond twelve
blocks are allowed and release the binding on successful teleport. Root first
dismounts a passenger and prevents remounting until expiry. Death, disconnect,
main death and encounter cleanup restore the owned movement/jump modifiers.
Other plugin modifiers are preserved.

Spin now creates two eleven-line fans from the weapon/wrist bones in the shipped
animation. The bone evaluator is shared with body targeting and follows the same
45-degree-per-tick rotation, including the small echo sculpture. Fans mix short
and long rays; some rake the floor while others remain at hand height. Every ray
is clipped at solid blocks before rendering and collision. Beam planes refresh
each active tick with one-tick interpolation, and native travelling sparks keep
the effect readable on both clients. The visual count is bounded to 22 planes
per spirit, and all rays are removed at spin end or encounter cleanup.

The rendered segments also own their contact geometry. Ground vortex and every
ray share the existing per-cast victim set: a player cannot receive a separate
hit from each quill. Main spin raw damage increases from 5 to 12; echo spin from
14 to 20. Echo Slow II remains three seconds. Native hit immunity, enchantments,
Totems, cancelled damage and the existing armor durability path remain active.
There is no damage attached to a victim after leaving the attack.

No new client asset is required: the existing judge_ray texture and native
particles are present in Bedrock pack 3.31.0. The updated JAR retains that pack.
The native effects still require an actual client playtest for final aesthetic
and latency assessment. previews/acheron-quills/spin-quills.png is a code-rendered
preview of the authored arm poses and ray segments, not an in-game screenshot.

AcheronEffectsProbe extends the threshold/echo suite. It checks real native
particle packets, warning display visibility, changed eye LOS, impact persistence,
root correction/impulse/mount protection, exact ray count/hand origin/rotation,
short/long lengths, damage beyond the vortex, shared single-hit accounting,
wall clipping, cleanup and the existing threshold/root rules.

Final validation: 112 focused Paper checks, 85 contact regressions, 62 general
encounter checks, 38 armor durability checks and 339 real ModelEngine/Geyser
checks. Native particle packets and warning visibility are verified with a
headless ServerPlayer; no human client rendering/latency claim is made. Reports,
artifact hashes and backup path are recorded in
`output/acheron-quills-final-20261015/release-validation.json`.
