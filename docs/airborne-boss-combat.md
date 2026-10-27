# Airborne sanctuary combat — e2.120

Release `3.0.0-e2.120-airborne-boss-combat` keeps players using Wind Charge and
Mace in the six Overworld sanctuary encounters while they are above the arena.

## Arena and committed attacks

An active arena is the same 34-block horizontal cylinder, with the same lower
bound of 0.25 blocks below its standing height. It has no upper combat ceiling:
a higher Wind Charge cannot make a player disappear from targeting, the boss
bar, outgoing damage or the empty-arena clock. Valid airborne attacks retain
the existing weapon, model-body reach, armor, immunity and hit-cooldown rules.

An idle sculpture still requires an attack from the original activation height:
2.2 blocks, or 12 for Cocytus. Projectile origin and shooter checks still use the
particular sanctuary. A real horizontal exit or departure below the arena floor
starts the existing 300-tick return grace; returning in time resumes the fight.

Jumping preserves committed windups, active attacks and owned effects. Ground
attacks retain their existing physical height limits, so jumping can dodge a
ground strike. Ground movement destinations are projected onto the arena floor.

## Aerial responses

* **Acheron and Styx:** when free of an existing move, stun and recovery, a player
  more than three blocks above the arena can prompt a sky lance. It snapshots a
  line from the boss's upper body through the target's eye position, warns for
  28 ticks, strikes once, then recovers for eight ticks. Cooldown is 100 ticks
  from the warning's start. The visible line has diameter 1.3 blocks during
  warning and impact. It never follows a dodging player, stops at solid cover,
  and clears on reset or defeat. Acheron deals 18 raw damage; Styx deals 16 via
  its existing heart-damage path, including its enrage and blood-drain rules.
  This response does not execute players or overlap another committed move.
* **Phlegethon:** chooses its existing warned rock throw for elevated targets.
  Ranged engagement uses horizontal distance; the throw retains target height,
  its existing aim lock, ballistic travel and swept collision.
* **Banshee:** uses its existing committed sonic volleys for elevated targets
  instead of starting a ground curse beneath them. Horizontal engagement range
  and sufficient projectile lifetime allow those volleys to reach high targets.
  A curse already in progress still completes at its committed ground position.
* **Cocytus:** storm aimed lanes reach high targets within their existing flight
  window. Rain commits both horizontal lanes and a sky height 12 blocks above
  the highest marked player. Rain arrows have enough lifetime to descend, and
  the encounter waits for the last arrows without starting an extra rain round.
  Raised rain has an additional visible sky warning. Both rounds remain
  dodgeable; prison spikes still require actual contact near the floor.
* **Malefeasance:** sky gates snapshot a height above their marked targets.
  Their rendered beam and damage share the same vertical interval, including
  where solid terrain blocks the beam. Players above the seal or below a
  blocking roof cannot take damage from an invisible continuation.

## Build and installation

`python tools/build_airborne_boss_release.py` compiles only the seven affected
source files against the saved current release. It patches their class families
and plugin version into that JAR, and records source and artifact hashes in
`build-manifest.json`. Unrelated classes and embedded resource packs are checked
byte for byte against the input JAR. The previous JAR is preserved as
`output/airborne-boss-release-20261023/evergarden-before.jar`.

Install `dist/evergarden.jar` and restart the Minecraft server. This release does
not require a new resource pack. The work does not restart a running server.

The delivered JAR passed **386 Paper checks**: 135 for airborne combat, 56 for
Cocytus, 54 for Malefeasance and 141 for the existing sanctuary rules. The shared
boss-logic suite also passed 67,143 cases. Reports and the tested SHA-256 are in
`output/airborne-boss-release-20261023/validation.json`.

Focused Paper regressions are available through `tests/arkhaia/run.py --airborne`,
`--cocytus`, `--malefeasance` and `--sanctuary`. The optional
`--direct-launch` uses an already extracted Paper template, keeping its patched
version JAR before its listed libraries to avoid Windows Paperclip extraction
failures. A connected Java/Bedrock playtest remains useful for combat balance
and how the warning feels during real network latency.
