# Evergarden portal routing (2026-10-14)

Quartz gates render with vanilla `NETHER_PORTAL` blocks. The old player lookup
checked only the blocks containing the feet and eyes. A player's body can touch
the portal while both points remain in adjacent air. In this case the custom
listener missed the contact and could allow vanilla Nether travel.

`TravelListener` now also checks the entity bounding box at the event position.
Quartz portal contact cancels vanilla travel and requests the Evergarden
destination. The same contact lookup protects entities. The return protection
still cancels vanilla travel while requiring the player to leave the portal.

Validation used real Paper blocks and a strict player proxy to inspect requested
teleport destinations. The previous release failed the edge-contact regression;
the patched release passed both portal axes and both sides, event-position
translation, movement routing, Nether cancellation, entity contact, return
protection, ordinary Nether travel, legacy migration, physics, and frame removal.
This does not test a connected Java or Bedrock client or the live server.

Run the focused checks with:

```powershell
python evergarden/tests/run_portal_travel.py --source-server output/purple-portal-validation-20261013 --garden-jar dist/evergarden.jar --server output/portal-check-new
```

The runner creates a new isolated localhost server and copies its dependencies.
Windows sandbox restrictions on Java JAR access may require elevated execution.

Results: `output/portal-routing-baseline-v3-20261014/portal-result.txt` and
`output/portal-routing-final-20261014/portal-result.txt`.
The local release `dist/evergarden.jar` changes only `TravelListener.class`.
Its previous version is backed up in
`output/portal-routing-release-20261014/evergarden-before.jar`.
No live server was changed.
