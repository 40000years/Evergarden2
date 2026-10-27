# Java resource pack delivery

Advance Magic, Evergarden and Judge keep separate ZIPs, UUIDs, content-addressed
URLs and SHA-1 hashes. An update to one pack can still reuse the client's cached
copies of the others. Existing administrator CDN, hash and reverse proxy settings
are preserved. Bedrock delivery remains with Geyser.

All three Java packs are enabled and required by default. The first startup of this
release upgrades existing optional configs too, using
`internal.required-java-packs-version: 1`. Magic and Evergarden use
`resource-pack.required: true`; Judge uses
`world-boss.visuals.pack.required: true` in Evergarden's config. Players must allow
and successfully apply the installed packs to remain connected.

The server checks unfinished required packs once per second. Declines, download or
reload failures, discarded packs, invalid URLs/hashes and unavailable pack hosts
cause a disconnect. Loads still unfinished after two minutes also disconnect with
a retry message. The watchdog stops when every required pack finishes and restarts
if a later reload fails. Disconnects clear its session data.

`ResourcePackManager` collects Java offers during the join event and flushes them
on the next server tick. The core packs form one Adventure request, in registration
order: Magic, Evergarden. Since e2.146 Judge is offered only after Evergarden reports
SUCCESSFULLY_LOADED: a Minecraft client marks every unfinished pack DISCARDED when any
download in its batch fails, and Judge (which carries the ModelEngine rig, renumbered
on every server start) is the pack clients must download again after each restart. Requests use `replace(false)` to
preserve packs from other plugins. If an administrator deliberately configures
different required policies after migration, adjacent groups use separate requests
to retain those policies.

Ordinary offers with the same UUID, URL, hash, prompt and required policy are
suppressed for the current connection, including offers during loading and after
success or failure. Failed downloads do not trigger an automatic retry loop.
`/magic pack resend`, `/evergarden pack resend [player]` and
`/evergarden boss pack resend` explicitly retry completed attempts. Repeated
resends during loading are suppressed for 30 seconds; an explicit resend can
then retry a stalled request. Disconnecting clears the session so reconnecting
can apply cached packs again. Client status events still control Magic effects
and Judge's vanilla fallback.

The pack commands show batch count, offer count, suppressed duplicates and queued
packs for the current connection. These counters measure server delivery requests,
not the client's number of resource reloads. Batching reduces staggered offers;
the client still controls download scheduling and resource application. One visible
loading screen and a particular speed improvement are not guaranteed.

The main Java pack bytes read during `extract()` are reused during `start()`, then
released after the HTTP host takes its private copy. The hash is computed once at
startup. Pack builders already sort files and use fixed ZIP timestamps; this
refactor does not rebuild the texture ZIPs or invalidate their hashes.

The delivery package has one source under
`advance-magic/src/main/java/com/example/advancemagic/pack/delivery/`. It is compiled
into both JARs, including by Evergarden's Maven source configuration and the local
release builders. Either plugin can run alone. A single Bukkit service coordinates
the installed plugins, including isolated classloaders, and transfers pending work
to a remaining plugin when its scheduler owner shuts down. Future manual JAR
builders must include this shared package in the Evergarden JAR too.

## Verification

Build and exercise delivery with cached Paper dependencies:

```powershell
python tests/resource-packs/run_checks.py --compiler-options path/to/previous-javac.args --build-directory output/pack-checks
python tests/resource-packs/run_paper_checks.py --build-directory output/pack-checks --source-server path/to/cached-paper-server
```

The controlled-tick checks cover ordering, batching, cache identity, duplicate
suppression, required policies, retries, disconnects, transport failures, host
handoff, required-load deadlines and isolated plugin classloaders. Configuration
checks verify required-pack migration, CDN migration and hashes. The Paper probe runs with both plugins and with only
Evergarden, checking real push packets, HTTP bytes and cache responses, client
status handling, scheduler handoff and real disconnect requests on failure. The
probe cancels its synthetic player's kick events to exercise subsequent cases.
It does not launch a Minecraft client or
measure its rendering time. Both runners use fresh local artifacts and test servers.

The 2026-10-13 required-pack validation passed 49 controlled delivery checks,
36 checks on Paper 26.2 build 121 with both plugins and 30 with Evergarden alone.
Required-config migration, CDN configuration and compiler-stub checks also passed.
The validated local JARs are
`dist/advance-magic.jar` and `dist/evergarden.jar`; install both updates and restart
to coordinate all three packs. This change has not been deployed to a live server.

Reference: [Paper's Adventure resource pack API](https://docs.papermc.io/adventure/resource-pack/).
