# Vault security audit — e2.33

Release: `3.0.0-e2.33-vault-security`, packaged in `dist/evergarden.jar`.

## Confirmed vulnerabilities and fixes

These behaviors were reproduced using the preceding e2.32 release on a disposable
real Paper server. They establish code defects, not evidence that any particular
production player exploited them.

| Finding | Required condition | e2.33 behavior |
| --- | --- | --- |
| Admin “reset temple cooldowns” deleted the entire `sites` section, including every player's Vault claims. Ordinary players could then roll the same Vault again. | An administrator used that button. | Only `sites.<site>.next-open` is removed. Claims and other metadata survive the reset and restart. |
| Sneaking bypassed an existing claim for operators, delegated admins, and even Creative players without admin permission. | Those privileges or Creative mode were available. | Production Vaults enforce one claim per site/UUID for all players. The existing explicitly authorized admin test menus remain available. |
| Failed ledger writes were ignored, allowing rewards without durable claim records. A restart could make those players eligible again. | The filesystem could not save `dungeons.yml`. | The entitlement must commit before key consumption or any reward delivery. A failed commit rolls back its in-memory claim and receipt, keeps the key, and disables further Vault rewards until the storage problem is corrected and the plugin restarts. |

Cooldown reset also refuses unhealthy storage, rolls back its cooldown removals
if the write fails, and reports failure through the admin GUI.

## Heaven odds and identity checks

The loot table is correct: 50 of the 10,000 tickets select the **combined** Mythic
core category, which chooses equally among Levitation, Solar, Chronos, and Heaven.
Heaven therefore has a **0.125% probability per accepted opening**, or 1 in 800.
The ordinary 7% core category contains only the other fourteen cores. No
roll-until-success or pity loop was found in the production Vault path.

The rate is a probability on each opening, not a requirement to open 800 Vaults
first. Across independent openings, the probability of at least one Heaven core
is `1 - (1 - 0.00125)^n`: approximately 11.76% for 100 openings and 46.49% for
500 openings across all players. A quick drop alone does not prove exploitation.

The separately requested world-boss reward still chooses one core uniformly from
all eighteen types for each qualifying contributor (Heaven: 1/18, about 5.56%).
That loot policy and the Vault rates are unchanged by this patch. The reported
production item was identified by the owner as coming from a Vault.

Checks on the real server confirm:

- Renaming a vanilla Trial Key cannot make it an Evergarden Key.
- Copying Heaven core names/lore does not authenticate a normal Heart of the Sea.
- Renaming a legitimate ordinary core leaves its original spell identity intact.
- Conflicting core tags are rejected by Advance Magic.
- The same UUID cannot reopen the same Vault after a name change, cooldown reset,
  or full server restart. A different genuine UUID and a different temple retain
  their separate legitimate entitlements.
- Offhand duplicate events and 100 repeated left/right clicks do not reroll.

The source audit also found live admin permission checks at the test menus and
give commands, and tag/ingredient validation in manual and automatic wand
crafting. No name-based upgrade path was found. This audit does not inspect
other plugins, production permissions, authentication, player inventories or
historical production logs. A premium account alone does not establish which
UUID the server/proxy actually uses; verify its authentication settings before
attributing an identity change. Different UUIDs have different Vault rights.

## Audit records and production update

New claims keep the compatible boolean `sites.<site>.opened.<UUID>` and add a
receipt at `sites.<site>.vault-claims.<UUID>` with `player`, `at` (epoch milliseconds),
`reward` and `amount`. Heaven appears as `reward: core:heavens_judgment`.
Successful delivery also logs:

```text
[Vault-Claim] player=<name> uuid=<UUID> site=<site ID> reward=core:heavens_judgment amount=1
```

Receipts record the selected primary loot when its entitlement is committed;
the two bonus dust and optional crop seed remain as before. Committing before
delivery prevents duplicate grants but does not make disk state and player
inventory one atomic transaction: a crash between commit and delivery can leave
a committed claim without delivery. The receipt retains its selected loot for
investigation.

Stop the production server normally, back up `plugins/Evergarden/dungeons.yml`,
replace its Evergarden JAR with this release, then restart. Preserve the existing
ledger. The patch honors old boolean-only claims without granting fresh rights.
It cannot reconstruct history that the old reset button already deleted; that
requires earlier backups or logs, merging recovered claims with newer records.
The new receipts do not retrospectively prove the source of older items.

## Reproduction and regression checks

The isolated probe starts the actual release plugins with real server players,
inventories, Bukkit interaction events and atomic YAML writes. It deliberately
blocks the temporary ledger path with an empty directory to reproduce disk
failure. It does not connect to production.

```text
python tests/vault-security/run.py --paper-template <Paper template> --garden-jar dist/evergarden.jar --magic-jar dist/advance-magic.jar
```

The e2.33 run passed **245 checks on the first boot and 7 after restart**. It
enumerates all 10,000 loot tickets and all 200 Mythic ticket/subselection pairs,
tests ordinary core exclusions, claim preservation, key authentication, hidden
admin/Creative bypasses, name changes, and failed writes/reset rollback.
Passing `--baseline` with the preceding e2.32 JAR instead reproduces the three
vulnerabilities (7 checks) on another disposable server.
