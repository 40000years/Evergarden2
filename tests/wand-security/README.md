This probe boots copied release JARs on an isolated Paper 26.2 server with a real server player in Survival, without operator permissions. It shuts down after recording its results. It generates disposable worlds and must never be installed on a player server.

Run from the repository root with JDK 25 and Python available:

```powershell
python tests/wand-security/run.py --name candidate-20261003 --magic dist/advance-magic.jar --garden dist/evergarden.jar
```

The runner copies the local Paper launcher, libraries and versions from `.audit-plugins/vault-security-fixed-20261003`. It requires the existing Maven dependency cache, including Paper and Gson. It creates a new `.audit-plugins/wand-security-<name>` directory; choose a new name for every run. Result, console log and exact input hashes are saved there. It does not replace release artifacts or copy production player data.

The core reproduction submits real `ServerboundContainerClickPacket` transactions to Paper. Scroll placement followed by same-tick inventory movement, reverse placement, cursor drop and closing a chest reproduced wand counts changing from one to two on the original Evergarden JAR. Hand changes use Bukkit inventory/interact operations, and compatibility drag cases construct `InventoryDragEvent` with Paper's cancelled-event cursor restoration explicitly modeled. Those compatibility cases do not represent a tested Bedrock/mobile client.

Actual `QUICK_CRAFT` gestures test incompatible occupied slots and cancellation. Vanilla Paper does not admit an occupied wand as a scroll drag target because the stacks differ. The probe therefore separates native packet checks from compatibility event checks. Crafting uses the actual server recipe engine for every spell and both registered core positions. Restoration ritual checks invoke the real altar service and advance its ticker reflectively to verify exact payment, output conservation and moved-target cancellation.
