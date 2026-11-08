# Pet egg delivery recovery (e2.171)

Newly collected and admin-granted eggs use a persistent initial-delivery journal.

1. Save the egg identity, unborn pet, recipient and pending delivery before creating the inventory item.
2. For a nest collection, save the parents' cooldowns, daily egg quota and cleared nest with an egg receipt. Replaying an interrupted collection does not spend the quota twice or reopen the old nest.
3. Put the egg in the player's inventory, call `saveData()`, and verify a fresh receipt in the actual player NBT file. Paper can silently skip or fail a save, so returning from `saveData()` alone is insufficient.
4. Clear the pending journal only after that verification. Pending eggs cannot be hatched or transferred until their save is confirmed.

On login, respawn, opening `/pets` or the nest's egg list, or the 30-second incubation tick, unfinished deliveries retry. A full inventory keeps the delivery queued; the egg is never dropped on the ground as a fallback. Dead players wait until after respawn so the egg cannot be committed into an inventory that vanilla discards. Recovery reuses a valid pending inventory item rather than adding another, returns cursor eggs to saved inventory slots, and removes stale replacement copies that would block delivery. If it must replace a missing item, it rotates the delivery token so a stale copy cannot incubate or hatch. An egg identity still hatches at most once.

Existing eggs remain compatible. Recovery is restricted to unfinished initial grants: it does not reclaim committed eggs that were traded, stored in a chest, dropped or otherwise moved. Eggs already lost before this journal existed cannot be automatically distinguished from eggs legitimately given away. Corrupt files, deliberate deletion/restoration of server data, storage failure and losses after later vanilla inventory transfers are outside this initial-delivery guarantee. Keep the server's normal backups.

Build: `python tools/build_pet_egg_recovery_release.py`. The build preserves unrelated JAR entries, records source/artifact hashes and leaves a candidate under `output/pet-egg-recovery-release` for testing before promotion to `dist`.

Validation on 2026-10-29: the final JAR passed 285 checks in the isolated Paper pet/Seraph suite (`python tools/run_pet_egg_recovery_checks.py`). This includes 55 new delivery/recovery checks, disk-backed replay before and after the collection debit, a disabled player-data saving flag, full inventories, stale tokens, cursor items, pending transfer/death guards, legacy eggs and dead-player deferral. Artifact and probe hashes are in `output/pet-egg-recovery-release/test-receipt.json`; promotion to `dist/evergarden.jar` is recorded in `promotion-receipt.json`. Tests simulate interruption boundaries by reopening saved files; they do not power-cycle the machine.
