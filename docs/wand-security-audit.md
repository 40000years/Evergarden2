# Survival wand duplication audit — 3 October 2026

The old release contains a reproducible server-side Survival duplication bug affecting
Advance Magic wands through Evergarden's scroll application. The patch is in
**Evergarden**, so replacing only `advance-magic.jar` does not close this hole.

## Confirmed findings

### Critical: scroll application recreates a moved wand

`EnchantApplyListener` previously applied an enchant immediately, then scheduled a
second write of the captured equipment and scroll remainder on the next tick.
It did this for scroll-on-equipment, equipment-on-scroll (touch controls), and
main/offhand application. The delayed write did not check whether the player had
moved, dropped, or returned the equipment by closing the inventory.

On disposable Paper 26.2, a Survival actor without operator permissions starts with
one wand and one Scroll of Eternity. Actual `ServerboundContainerClickPacket`
handling reproduces **one wand becoming two** after:

- Applying the scroll and moving the wand to another inventory slot.
- Applying equipment-on-scroll and placing or dropping the cursor result.
- Applying equipment-on-scroll in a chest and closing the inventory.
- Applying the scroll in a world chest and transferring the wand before closing.

The hand path also duplicates when the selected hotbar slot changes before the
scheduled write. Synthetic event tests exposed writes into cancelled transactions,
craft result previews, and showcase decorations. Those are reported separately
from the actual packet reproductions.

The fix commits click/hand application once. Scheduled refreshes send the **current**
inventory to the client and never write captured equipment back. Click application
requires normal left/right transfer actions and a real editable storage slot;
cancelled events, result previews, and custom menu windows are excluded. Equipment
must be a single item. Item metadata is prepared on clones.

For a single-slot compatibility drag event, Paper restores the old cursor after
a cancelled drag. The fix therefore starts a new transaction next tick, only if
the player is alive and online, the original view remains open, and both cursor
and target are exactly unchanged. It then reads and consumes the live items once.
Moving items or changing views aborts it. Native quick-craft drag over incompatible
scroll/wand stacks does not reach this compatibility operation; native gestures
and the compatibility event contract are tested separately.

This follows Paper's documented option to cancel an inventory event and apply
the intended changes once. See [InventoryClickEvent](https://jd.papermc.io/paper/1.21.11/org/bukkit/event/inventory/InventoryClickEvent.html).

### High: scroll crafting can delete the wand without delivering output

Evergarden's dynamic scroll/repair recipes previously called
`consumeMatrixIngredients()` before letting the native result transfer run.
Changing the matrix cleared the result. Real Paper result-click packets consumed
the wand and one scroll while delivering no equipment, including ordinary pickup
and actions with an occupied cursor.

The corrected dynamic crafting transaction validates current inputs and cursor
capacity, cancels the native transfer, consumes one unit of each ingredient, and
delivers the planned output exactly once. Unsupported actions and failed delivery
leave the inputs intact. Registered recipes continue to use native accounting.

## Scope and evidence

| Path | Audit result |
| --- | --- |
| Evergarden scroll click, touch reverse click, hands | Confirmed duplication; patched |
| Evergarden dynamic scroll/repair crafting | Confirmed item loss; patched |
| Advance Magic native crafting | Packet accounting checked for pure/mixed premium ingredients, both core positions, normal/shift/hotbar/offhand/drop/clone/double-click actions, repeated requests, full inventory, missing native recipe, and denied permissions |
| Magic craft menu | Exact payment, failed payment/full inventory, and repeated click handling checked |
| Wand upgrade cores | Existing-item modification and core consumption checked |
| Restoration wand and altars | No captured-item replay found; altars revalidate items and commit current storage once |
| Flying staff and spell casting | No deferred inventory recreation or display-to-item refund found |
| Treasure, Vault claims, showcases | Persistent loot markers and menu cancellation/admin checks reviewed |
| Bedrock creative bridge | Server grant requires Creative; it cannot directly grant a Survival player a wand |
| Soulbound and AfterDeath | Soulbound removes retained items from drops; follow-up native death tests found retention defects described in [the Soulbound audit](soulbound-security-audit.md). AfterDeath does not recreate wand inventory |

These tests use actual Paper player objects and inventory packet handlers on
fresh local servers. They do **not** use a real phone/PC Bedrock client or identify
what the observed player actually did. The confirmed root cause is in server code
shared by Java and Bedrock. Production player data and logs were not accessed.
The patch does not delete existing items; the current item format has no unique
per-wand serial that would reliably distinguish older copies from legitimate wands.

## Reproduce and verify

The test plugins are disposable probes that edit their own test world and stop
the server. They are never included in release JARs.

```powershell
python tests/wand-security/run.py --name fresh-security-check --template <paper-template> --magic dist/advance-magic.jar --garden dist/evergarden.jar
python tests/wand-inventory/run.py --paper-template <paper-template> --magic-jar dist/advance-magic.jar --garden-jar dist/evergarden.jar
python tools/release_jar_checks.py dist/evergarden.jar
```

The first runner refuses an existing test target. Logs and result files remain
under `.audit-plugins` for inspection. Baseline JARs were preserved before building
the patch. The complete plugin sources are compiled with JDK 25 into isolated
directories to avoid reusing stale compiled classes.

Final verification:

- The identical wand-security probe has 18 failing cases against the preserved
  release, and **149 passing assertions with zero failures** against the patch.
- The inventory packet suite passes **1,510 checks** across 906 `CraftItemEvent`
  and 449 fallback result events. Native recipe accounting uses equality, including
  dropped outputs captured at spawn. Dynamic scroll and repair tests include a
  registered underlying recipe, no underlying recipe, blocked cursors, unsupported
  actions, cancelled events, stale previews, and repeated result requests.
- `release_jar_checks.py` and `git diff --check` pass. The Advance Magic candidate's
  complete archive contents match the existing release, so it needs no replacement.
- Released Evergarden SHA-256:
  `0a2e986f368924ee21c04aaff0fcdbdc0269340f2e9a1232c885a84de05b401a`.

Recorded results are in `.audit-plugins/wand-security-baseline-final-20261003`,
`.audit-plugins/wand-security-fixed-20261003`, and
`.audit-plugins/wand-inventory-fixed-20261003`. The wand-security runs record input
JAR and probe hashes alongside their results. The distributed Evergarden JAR is
the exact candidate that passed these checks.

## Install

Use `dist/evergarden.jar`, version **3.0.0-e2.34-wand-security**. Stop the server,
replace the existing Evergarden JAR, and start the server again. Advance Magic
keeps its existing item identifiers and resource packs. No server files or worlds
were replaced during this audit.
