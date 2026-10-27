# Evergarden structure protection

Evergarden `3.0.0-e2.41-existing-structure-protection` protects sky whales,
observatories and hanging gardens, including existing landmarks from historical
placement rules. The previous `e2.40` fix checked only the current placement
algorithm. A Survival player without OP could still mine a garden built at an
old paired-layout position that no longer appeared in that algorithm.

The protection checks the saved layout of the Evergarden world. When the current
layout misses an existing landmark, it checks known historical positions against
multiple distinctive blocks of its actual architecture. Verified origins are
saved in `plugins/Evergarden/protected-structures.yml`, bound to the world UUID.
These regions survive restarts and later changes to the identifying blocks.
An empty historical candidate or a single matching player-placed block does not
qualify for protection. Both horizontal footprint and height must match. Ordinary islands, terrain
outside these volumes and matching coordinates in other worlds remain editable.
Coordinates near the world border are checked without integer distance overflow.

| Structure | Horizontal footprint | Protected height (inclusive) |
| --- | --- | --- |
| Sky whale | Existing elliptical terrain reservation, 115 by 65 block radii | Y 48–184 |
| Observatory | Existing 64 block radius square reservation | Y 32–210 |
| Hanging garden | Existing 48 block radius square reservation | Y 32–210 |
| World boss temple | Existing 241 block wide octagonal reservation | Y 40–205 |
| Small temples | Existing 28 block radius square reservation | Y 94–140 |

Mining is cancelled before enchanted mining abilities run, with a second check
after normal event handlers. Instant mining, building, buckets, piston movement,
explosions, fire, tool modifications and temporary Advance Magic terrain also
respect the landmark volumes. Chest interaction stays available. The small
temples retain their combat-web removal behavior.

The maintenance bypass uses the same rule as the small temples: Creative mode
and operator status or `evergarden.admin` / `voidscape.admin`. Survival operators
and ordinary Creative players cannot mine the protected structures.

The existing `structures.<kind>.enabled` setting controls generation and that
kind's region protection. Spawn and small-temple behavior are unchanged.

Replace the server's Evergarden JAR with `dist/evergarden.jar` and restart.
Historical recovery supports both shipped paired-placement rules and previous
independent placements whose rarity rolls no longer pass. No world regeneration
or layout reset is needed. Previously removed blocks
are not restored by protection.

## Regression checks

`tests/structure-protection/run.py` creates a disposable Paper server and boots
it twice. It checks every blueprint block of the three sky landmarks, actual
generated samples, real `Player.breakBlock` calls by a Survival player without OP,
event ordering, horizontal and vertical bounds, other worlds,
negative and distant coordinates, admin bypass, chest access, magic terrain,
small temples, spawn and combat webs. The second boot checks that existing sites
stay protected with the same saved positions. It also builds a historical garden
pavilion outside the current layout, verifies mining its solid floor is blocked,
then removes its identifying architecture before the second boot to verify the
saved region. An unbuilt historical candidate remains mineable.

The historical-floor regression fails against the exact previously delivered
`e2.40` JAR: the solid garden floor is removed by actual Survival mining. The
updated release is compiled from all 72 Evergarden Java sources and checked for
compiler error stubs.

Paper 26.2 build 121 passed 138 checks on the first boot and 140 on the second
boot (278 total), including actual mining and saved historical-region recovery.

Players can look at a problem block and run `/evergarden protection` without OP.
The result includes the installed plugin version, world, coordinates, protection
decision, game mode and bypass status.

```text
python tests/structure-protection/run.py --paper-template <disposable-paper-template> --garden-jar <evergarden.jar> --magic-jar <advance-magic.jar>
```

If JetBrains annotations are absent from the Paper libraries and local Maven
cache, pass `--extra-classpath <annotations.jar>`. The runner uses Windows
junctions for the template's existing libraries, versions and cache.
