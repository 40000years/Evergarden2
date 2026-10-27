# Boss armor durability — e2.83

`dist/evergarden.jar` version `3.0.0-e2.83-boss-armor-wear` fixes missing armor
durability loss on the Six Rivers' direct hits, Arkhaia spell impacts, and the
Judge's spell attacks. Replace the JAR and restart. Resource-pack assets are unchanged.

These attacks intentionally use `INDIRECT_MAGIC` or `SONIC_BOOM` to retain their
existing HP-damage rules. Minecraft tags these damage types as armor-bypassing,
which also skips native armor durability loss. Arkhaia's sword already uses
`MOB_ATTACK` and continues to receive native wear exactly once.

`BossDamage` applies wear only after HP or absorption is consumed, or a real Totem
resurrection occurs. It uses the native equipment durability path with
`max(1, floor(raw damage / 4))` wear per eligible armor slot. This preserves
Unbreaking, custom durability, damage-on-hurt equipment components, unbreakable
items, `PlayerItemDamageEvent` cancellation, Eternity, break events and equipment
updates. A scoped lethal-hit hook charges wear before resurrection or death-drop
creation, so Totems and dropped armor retain the correct durability without
charging the same impact twice.

Cancelled/rejected/fully resisted hits and equal hits rejected by damage immunity
do not charge wear. Creative equipment is exempt. Bleeding, Ghostflame damage over
time and Banshee reflection are not fresh armor impacts, so they do not repeatedly
charge the armor after the initial hit. HP damage and the native armor/enchantment
mitigation rules of each attack remain unchanged.

Validation uses a real Paper player and the actual Evergarden listeners, including
native durability randomness and item break behavior. The focused probe first
reproduces the original vanilla magic behavior, then tests the fix and exercises
the six production river attack/release implementations. The existing Arkhaia
combat regression also runs against the release.

```text
python tests/arkhaia/run.py --template <cached-paper-runtime> --garden-jar <release.jar> --server <fresh-test-directory> --classpath-file <probe-classpath.txt> --armor-wear
```

The final release passes 38 focused durability checks, including lethal death
drops and low-health Totem rescue, plus 71 existing Paper encounter checks.
See `output/boss-armor-wear-paper-v4-20261015/arkhaia-result.txt` and
`output/boss-armor-wear-regression-v4-20261015/arkhaia-result.txt` for results.
