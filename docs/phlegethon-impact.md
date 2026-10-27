# Phlegethon impact upgrade - e2.89

Makes the three existing furnace attacks more threatening. Health stays 1,000
and the accepted adaptation mechanic retains its weapon-family halving/reset rules.
No rig, resource-pack or Acheron damage change is required.

## Ground slam

The warning has three concentric seals and bright native particle boundaries at
radii 3, 6 and 10 blocks (20-block outer diameter). Colors graduate from red-orange
in the center through orange to amber outside. At authored fist impact, explosion
and deepslate debris launch a visible expanding ground wave. It reaches radius ten
over 12 ticks; each victim can be hit once per slam. Damage follows the player's
distance from the impact center: 60 inside three blocks, 44 inside six, 28 outside
six. Accepted hits apply Nausea I for 120 ticks (six seconds).

The wave tests an annulus rather than leaving a damaging filled circle behind it.
Solid cover blocks damage; running behind a passed wave is safe. Warning and wave
displays, victim sets and anchors clear on cancellation, owner death or no targets.
The center deals 21.6 actual damage through full diamond Protection IV and wears
all four armor pieces once through the shared native damage helper.

## Kick

Keeps the authored extended-shin/foot contact at active time 0.2 seconds. Damage
rises from 20 to 40; accepted contact sets horizontal knockback strength to 2.0
and vertical velocity to 0.55. Rear targets and targets separated by solid cover
remain safe. Recovery does not repeat the hit.

## Boulder

Tracks the committed target through windup and the four active ticks before
release. Walking velocity is measured from positions, smoothed and limited;
teleports cannot become extreme leading velocity. The release leads movement by
the estimated flight time, capped at eight blocks. Horizontal travel increases
from 0.85 to approximately 1.5 blocks per tick (7–20 tick nominal flight).
Once released the trajectory remains ballistic, with no homing.

Direct rock contact deals 48 (previously 22). Collision with player, wall or ground
creates one visible debris explosion with a 3.5-block splash radius, dealing 32
to nearby players with clear line of sight. Direct victims are excluded from the
splash, preventing double damage. Full-volume swept collision still stops the
1.8-block display at solid cover. Small sidesteps can avoid the direct rock while
remaining inside splash; larger or late direction changes can escape both.

## Timing and verification

Windups: slam 34 ticks, kick 22, throw 28. Skill cooldown is 80 ticks from commitment
(previously 100). Animation rates follow the new windups; contact keys are retained.

Real Paper checks exercise graded wave arrival, nausea, Protection IV mitigation
and durability, passed-wave safety, cover, knockback magnitude, faster predictive
throws, near-miss splash and cleanup. Contact, armor and Acheron regression suites
run against the delivered binary. Reports and SHA-256 are recorded in
`output/phlegethon-impact-final-20261015/release-validation.json`.

Deliverable: `dist/evergarden.jar`, version `3.0.0-e2.89-phlegethon-impact`.
Replace the JAR and restart. Existing resource pack 3.32.0 is retained.
