# Java spell textures: Solar, Chronos and Heaven's Judgment

The pink/black surfaces are missing atlas sprites. A successful resource pack
download does not prove that textures referenced by a model are in an atlas.

The published packs contain all eleven custom spell PNGs under
`assets/advance_magic/textures/effect/`, but do not register that directory in
`assets/minecraft/atlases/items.json`. Java 1.21.11 and later use a separate item
atlas. The twelfth sprite, `minecraft:entity/beacon_beam`, also points at a PNG
that moved to `textures/entity/beacon/beacon_beam.png` in Java 26.1.

`tools/magic_java_atlas.py` adds the effect directory to the item atlas and aliases
the old beacon sprite ID to its current PNG. An overlay selects the old PNG path
for resource formats 75–76. Both pack generators now include this registration.
Existing models keep the same sprite IDs, including models in an older pack.

## Server release

The atlas fix is included in both main Java packs and the rebuilt
`dist/advance-magic.jar` and `dist/evergarden.jar`. Install both updated plugins
and restart the server. Official pack URLs are migrated to the fixed revision;
custom CDN URLs must be updated by their administrator. Players using this
release do not need the separate supplement.

## Optional client supplement for an older server

1. Keep the server's Advance Magic resource pack enabled.
2. Copy `advance-magic/dist/advance-magic-java-effects-fix.zip` to the client's
   resource pack folder, using Options → Resource Packs → Open Pack Folder.
3. Enable **Advance Magic | Java spell texture atlas fix** and click Done.
   Cast Solar Apocalypse, Chronos Final Hour or Heaven's Judgment again.

This small supplement contains atlas registration only. Atlas definitions merge
across packs, so it works even when server packs have higher priority. No server
JAR change is needed for this client-side correction. It has been checked with
the actual Java 26.2 and 26.3 atlas loaders, including both pack stacking orders;
visual rendering in the user's running game remains to be confirmed.

## Verification and rebuilding

Build release JARs in a fresh checkout outside the editor's workspace. An editor
compiler can put error stubs in `target/classes`; an incremental Maven build can
then reuse those files despite reporting success. Both pack validation scripts
now reject JARs containing these error stubs. `ReleaseLinkageChecks.java` also
executes the MythicSpells constructor and resolves the effect classes and
Evergarden's MagicCastEvent listener against the release JARs.

The release JARs at commit `322c670b8c4f47a496cc2078004f54c03ff93f74`
were checked on 2026-10-01 in an isolated Paper 26.2 build 121 server with Java
25. Both plugins enabled successfully. The Mythic spell probe passed 226 checks
across Solar, Chronos and Judgment, including damage, protection and cleanup;
the Bedrock probe passed 1,115 translations and 220 custom identifiers across
five protocol tables. Both pack checks passed, and the compiler stub check
rejected the original broken JAR. Downloads from GitHub matched the tested JARs
byte for byte:

| JAR | SHA-256 |
| --- | --- |
| `dist/advance-magic.jar` | `977d8e78057c59b46ff166539346f990f26d911dc7ea698ff76c3cbb989df62b` |
| `dist/evergarden.jar` | `6dc5672b32e2f871b80be54ce6b5e33bf546755a85d88f725974178fd6b1dcee` |

`build.sh` now performs a clean Maven build and rejects compiler error stubs
before copying the release JARs to `dist/`.

Build the supplement with:

```powershell
python advance-magic/tools/build_java_effects_fix.py
```

With an installed Java 26.2 client, reproduce the original missing sprites and
then verify the fix, below both existing server packs:

```powershell
python advance-magic/tests/check_java_effect_atlas.py --expect-missing path/to/old-advance-magic-java.zip
python advance-magic/tests/check_java_effect_atlas.py advance-magic/dist/advance-magic-java-effects-fix.zip advance-magic/dist/advance-magic-java.zip evergarden/dist/evergarden-java.zip
```

Use `--version 26.3` to run the same check against that installed client.
The test invokes Minecraft's own atlas codecs and source loaders, checks vanilla
control sprites, resolves all twelve effect models and decodes their PNGs. It
does not launch a game window or render a frame.

The main ZIPs and the rebuilt JARs use the fixed assets, with pinned CDN URLs
and SHA-1 values matching the published pack revision.

References: [Java 1.21.11 item atlas split](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-11),
[Java 26.1 texture moves](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-1).
