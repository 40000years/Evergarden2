# AfterDeath PvP inventory audit — 4 October 2026

The current `afterdeath.jar` from the user's GitHub link did **not** duplicate
fixture items in the tested death/respawn paths. The live-server cause remains
unconfirmed because the installed PvP toggle plugin and server configuration are
unknown. The user confirmed that the older AfterDeath artifact is not in use.

## Artifact and behavior

Downloaded `https://raw.githubusercontent.com/40000years/Evergarden2/main/dist/afterdeath.jar`
and compared it with the workspace's `dist/afterdeath.jar`: both SHA-256 hashes are
`f594e287432fa94cefe8c011658e65786b1ee955451f0363c8dc5c714100a037`.
Inspection of both source and compiled bytecode shows:

- AfterDeath records death coordinates and conditionally awards one clock.
- It does not enable PvP, implement a player PvP toggle, retain the inventory,
  set `keepInventory`, or replay the player's equipment after respawn.
- It skips automatic clocks for player-caused deaths, retained inventories, and
  empty death-drop lists.
- It inserts only a newly created clock with `addItem`; a full inventory rejects
  that clock without moving, overwriting, or dropping existing equipment.

Consequently, PvP does not intrinsically mean items are retained by this plugin.
That behavior depends on the world game rule or another plugin.

## Native-server tests

`tests/afterdeath-security/` launches a fresh localhost-only Paper 26.2 server,
creates non-operator Survival actors, applies native fatal damage, and performs
native server respawns. Unique fixture tags count each stack across both inventory
and ground items. Armor is unbreakable so the fatal damage cannot destroy the
helmet and confuse conservation checks.

Each run covers 38 cases: 36 combinations of environment/melee/player-owned arrow
damage, partial/full storage (36 stacks of 64), both `keepInventory` values, and
three inventory retention policies. The policies are vanilla behavior, a simulated
plugin setting keep-inventory and clearing drops, and a simulated plugin moving
drops to Paper's keep list. Every fixture also includes helmet and offhand gear.
The two additional cases exercise an intentionally broken retention handler and
a manual clock request with full storage.

| Plugin setup | Assertions | Failures |
| --- | ---: | ---: |
| Exact GitHub AfterDeath JAR | 187 | 0 |
| AfterDeath absent | 186 | 0 |
| AfterDeath + Evergarden + Advance Magic, with Soulbound helmet | 187 | 0 |

The combined run uses Evergarden SHA-256
`e34d23ef22286fa31d41ab2787a9f4b8c095cf12b8c2e9bc4ab34b9c6a119595`
and Advance Magic SHA-256
`6a1cd0d86bd2cbb874d33758c7c5e350278341dea12273cf03e172612e0be6e3`.
These are local audited builds; their installation on the user's server has not
been verified. In that run only the helmet is tagged Soulbound; storage stacks
and the offhand item remain ordinary items.

The deliberately broken handler sets `keepInventory=true` during death while
leaving the original death-drop list populated. This produces a retained inventory
plus ground copies, including when AfterDeath is absent. It demonstrates that the
probe detects actual duplication and that this particular API misuse can cause
duplication independently of AfterDeath. It does **not** prove the unknown live
PvP plugin uses that handler. Paper explicitly documents the need to suppress
drops when retaining items: [PlayerDeathEvent API](https://jd.papermc.io/paper/26.2/org/bukkit/event/entity/PlayerDeathEvent.html).

Logs, exact input hashes, and results are preserved in:

- `.audit-plugins/afterdeath-security-github-v3-20261004/`
- `.audit-plugins/afterdeath-security-no-plugin-v2-20261004/`
- `.audit-plugins/afterdeath-security-combined-20261004/`

Earlier development runs failed because the fatal fixture damage destroyed the
helmet. That was a fixture issue reproduced with AfterDeath absent, corrected by
using unbreakable gear; it is not evidence of duplication or plugin item loss.

## Reproduction and remaining information

```powershell
python tests/afterdeath-security/run.py --name fresh-current --afterdeath dist/afterdeath.jar
python tests/afterdeath-security/run.py --name fresh-control --without-afterdeath
python tests/afterdeath-security/run.py --name fresh-combined --afterdeath dist/afterdeath.jar --garden .audit-plugins/soulbound-security-baseline-v2-20261003/plugins/evergarden.jar --magic dist/advance-magic.jar --soulbound
```

The default runner template reuses cached Paper binaries/libraries from
`.audit-plugins/vault-security-fixed-20261003/` through directory junctions; worlds,
plugin copies, and results are separate per run. Supply `--template` to use another
compatible cached Paper 26.2 template. Each `--name` must be new.

To identify the live-server cause, obtain its `/plugins` output or installed plugin
JAR names and versions, the affected world's `keepInventory` value, and whether
extra items appear in inventory or on the ground. A player-controlled PvP toggle
alone is not enough to uniquely identify a plugin. The unknown plugin's delayed
inventory refunds, combat attribution, and interaction with the server's other
plugins cannot be verified by these fixtures. Bedrock client behavior, custom
menus/cursor escrow, and the live server were not exercised.

No production source, release JAR, live-server files, or existing user changes
were modified by this audit. Only the reusable probe and this report were added.
