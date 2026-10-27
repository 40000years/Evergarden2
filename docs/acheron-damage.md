# Acheron damage - e2.87

Increases Acheron's accepted spinning attack damage by exactly three times:

| Attacker | Previous raw damage | New raw damage |
| --- | ---: | ---: |
| Main | 12 | 36 |
| Echo | 20 | 60 |

Uses the existing native damage and Protection enchantment handling. All wrist
quills and the ground vortex still share one victim hit per spin. Echo Slow II,
armor wear, binding, split thresholds, movement and animation are unchanged.

Validation runs real Paper damage against full diamond Protection IV, compares
against the previous raw damage through the same production hit helper, verifies
exactly triple final health loss, checks armor wear and prevents repeated hits.
Reports: `output/acheron-damage-paper-v2-20261015/arkhaia-result.txt` and
`output/acheron-damage-contact-20261015/arkhaia-result.txt`.

Release: `dist/evergarden.jar`, version `3.0.0-e2.87-acheron-damage`.
Restart the server after replacing the plugin JAR. Resource packs are unchanged.
