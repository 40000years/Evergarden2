# Acheron echoes and binding circles - e2.85

> Model update (Bedrock 3.51.0): Acheron and its echo now use the Weeping Bladedancer rig; the echo is
> generated from it at 60% with no halo. See [acheron-bladedancer.md](acheron-bladedancer.md).

Main Acheron retains 800 HP and its moving spin. It now has a separate ritual
animation shared by the threshold split and binding-circle spell. Java uses
ModelEngine 4.1.1; Bedrock uses native geometry with the same clip indexes.
The resource pack version is 3.31.0 so Bedrock refreshes the new echo entity.

## Threshold split

Crossing 75%, 50% or 25% (600/400/200 HP) consumes that threshold once. If any
owned echo is still alive, that threshold is skipped permanently. No summons
are queued for when the previous pair dies. A hit crossing several thresholds
can queue only one pair, and consumes all crossed thresholds. Healing cannot
re-arm consumed thresholds.

The main casts a 28-tick ritual, then creates exactly two echoes at the contact
key. Both spawn locations must have a solid floor and clear body space, remain
inside the arena and be separated. If two valid spots cannot be found, the cast
ends without spawning a partial or overlapping pair.

Each echo has 200 HP, 60% of the main sculpture and receiver scale (2.4 native,
compared with 4.0 main), and no overhead seal or halo bone. The dedicated echo
blueprint preserves UVs while scaling all geometry, pivots and animation
translations. It uses the same source animation protocol, but AI can select
only moving spin. Echoes cannot summon or cast binding circles.

Echo spin has a visible 2.2-block ground vortex, hits each player once per cast
for 14 raw damage, and applies Slow II for 60 ticks on accepted damage. It uses
native invulnerability, Totem and armor-wear rules. Main spin remains 5 damage.
Native contact/walking attacks remain cancelled. Killing an echo does not trigger
main-spirit wave replacement/exposure. Main death, phase change, demo removal and
plugin shutdown remove every owned echo and renderer.

## Binding circle

An independent spell is available seven seconds after spawn and has a 300-tick
(15-second) cooldown from cast start. It marks eligible players' positions at
commit, within 26 blocks and line of sight. The circles stay fixed on the ground;
they do not follow a player who dodges. Each is six blocks in radius, with inner
red particle rings, and has a 36-tick (1.8-second) warning before contact.

A player still intersecting a circle at its release is rooted for 60 ticks
(3 seconds). This control spell adds no hidden damage. Each victim receives
300 ticks of root immunity from impact. Overlapping circles cannot stack or
refresh the root. If every nearby player is immune, the spell is deferred without
starting another cast. Echo Slow and binding root are distinct effects.

Rooting applies owned movement/jump attribute modifiers and constrains position
while allowing camera rotation. Expiry, legitimate teleport, death, logout,
leaving the encounter, main death and cleanup remove the owned modifiers. Other
plugin modifiers are preserved. Root duration and spell cooldown use the
encounter tick clock; creating a GUI demo now initializes that clock before spawn
so an old global tick cannot cause an immediate overdue spell.

## Validation

Real isolated Paper tests cover exact thresholds, surviving-child suppression,
large hits, recursive/cap protection, damage/Slow/armor wear, harmless native
contact, fixed-circle dodging, root duration/anti-repeat, movement/camera and
cleanup. Native ModelEngine/Geyser tests additionally inspect the echo model,
missing halo, small receiver, separate Bedrock actor and ritual playback.
Decoded asset tests validate the geometry ratio, clip indexes and Java/Bedrock
pose parity. The preview in previews/acheron-split/ritual-and-echo.png renders the
actual shipped sculptures. Automated fixtures do not replace a client playtest
for network latency and encounter balance.

During full-stack checks, an existing random baby Husk could produce a small
Phlegethon receiver under its full-size sculpture. Encounter zombies are now
explicitly adult, keeping the intended body size deterministic.

Final reports: `output/acheron-split-paper-final-20261015/arkhaia-result.txt`
(64 checks) and `output/acheron-split-native-final-20261015/model-engine-result.txt`
(336 checks). Existing contact/encounter/armor suites pass 85/62/38 checks.
The echo asset decoder passes 4,133 checks; the six-river decoder passes 16,936.
The production choreography suite passes 52,194 checks, with zero sampled
forearm/hand/body collisions. Build/backup/hashes are recorded in
`output/acheron-split-final-v2-20261015/release-validation.json`.
