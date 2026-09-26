# Solar Apocalypse and Chronos: Final Hour

Release: Advance Magic `1.3.1-mythic-fields`, Evergarden `3.0.0-e2.13-mythic-fields`.
Install both JARs from the root `dist` directory and restart the server. This revision
uses the existing packs (Advance Magic 2.2.0 / Evergarden 3.9.0); their hashes and
item models are unchanged.

## Abilities

| Wand | Mana | Base cooldown | Timeline | Base damage on a target hit by every stage |
| --- | ---: | ---: | --- | ---: |
| Solar Apocalypse | 100 | 45 s | Giant sun, golden glyph, five beams, falling sun, shockwave and temporary sea of lava | 5 × 32 + 180 = 340, plus lava contact damage |
| Chronos: Final Hour | 95 | 40 s | Five giant clocks, short root, simultaneous lasers, reversed echoes, poison sea and five-clock shatter | 5 × 24 + 5 × 16.8 + 100 = 304, plus poison/contact damage |

Solar impacts at 5.5 seconds; Chronos shatters at 8 seconds. Their temporary fields
last 15 seconds from creation (Solar at impact; Chronos at 4.4 seconds). Aim at a mob
or block within 30 blocks. With no hit, the spell centers 16 blocks ahead. Solar's
final burst has radius 16; Chronos attacks within radius 12. Damage uses the normal
magic protection event, enemy/team/PVP filters and wand damage upgrades.
High-health mobs (150+ maximum health), Wither, Warden and Ender Dragon receive
Slowness II instead of a hard root. Players use the existing root compatibility policy.
Native resistance to potion effects still applies. Closing a cast cancels remaining
attacks and restores its terrain. Neither spell changes time, weather or the camera,
and casts do not load or generate chunks.

## Temporary seas

Solar replaces exposed ground in a radius of 16 with native lava; Chronos replaces
it with native water. Both clients therefore see the same terrain geometry and
use the matching liquid physics. The field expands over one second, holds until
halfway through its lifetime, then restores the ground from the outside inward.
Poison-water contact applies **Poison V**, refreshed for five seconds, plus 12 magic
damage per second. Solar contact deals 12 magic damage per second and ignites enemies.
All contact attacks use the usual enemy/team/PVP filters and `MagicAffectEvent`.
Native lava damage/combustion in the field is suppressed in favour of these owned attacks.
Five clock lasers fire together; damage is charged once per volley, rather than once
per rendered beam. Base mana, cooldown and durability costs are unchanged.

Only exposed full ground blocks or liquid surfaces within three blocks above to
eight below the aim height are eligible. Containers, block entities, trees, portal
frames, bedrock, barriers and floors supporting plants are skipped. Fluids cannot
flow, be collected in buckets, form stone or ignite nearby builds. Managed cells
are protected from breaking, placement, explosions and piston movement while active.
Overlapping casts retain a shared original snapshot; each restores its own layer.
External edits that replace the managed liquid are preserved.
Swimmers intersecting a returning solid floor are lifted to clear space above it.

Original block data is written to `plugins/advance-magic/mythic-terrain-recovery.yml`
before painting, using a staged atomic replacement. Cleanup covers completion,
logout, death, world changes, chunk unload and plugin shutdown. Recovery entries
are retained until a subsequent chunk/world save; after an interrupted session,
remaining liquid cells are restored as their chunks load. The journal must remain
beside the plugin data when restarting or recovering the world.

They support the existing 30-use durability, repair, upgrades and mastery system.
Craft with the matching core and eight Netherite Ingots / Nether Stars.
The Vault's **total Mythic-core category remains 0.5%**; it now chooses equally
between Levitation, Sun and Time (about 0.1667% per core). Ordinary cores remain
in the 7% category and exclude all three Mythic cores.

## Admin access

`/evergarden test` → wand collection, or `/magic items`. Both menus contain all
17 wands and cores. Flying staff remains at slot 50; restoration controls and
bulk buttons occupy separate slots. Direct commands:

```text
/magic give <player> solar_apocalypse
/magic give <player> chronos_final_hour
```

Damage defaults can be overridden in Advance Magic's config:
`damage.solar-beam`, `damage.solar-apocalypse`, `damage.chronos-blade`,
`damage.chronos-shatter`, `damage.solar-lava`, `damage.chronos-poison`.
`mythic-terrain.radius` defaults to 16 (range 6–18); `duration-seconds` defaults to
15 (range 3–30); `poison-amplifier` defaults to 4 (Poison V).
`mythic-max-active-per-world` defaults to 4 (range 1–16).
The limit releases on normal completion, interruption, logout and plugin shutdown.

## Java and Bedrock rendering

Both clients share identical geometry and attack timing. Java uses typed native
`DustOptions`; Geyser viewers receive five corresponding particle emitters carrying
the same coordinates, colour and size. Their definitions and white dot texture are
embedded in **both** offered Bedrock packs. This avoids Geyser's current generic
dust conversion, which discards the Java size. Common flame, end-rod and explosion
particles retain the installed Geyser's vanilla mappings.

The optional adapter resolves classes from the installed Geyser plugin without
adding Geyser as a required dependency. If its API changes, it logs one warning
and falls back to vanilla dust. Client particle settings and each edition's renderer
can affect appearance; visual parity still needs an actual two-client play check.
The particle format follows [Minecraft's billboard component](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/particlesreference/particlecomponents/minecraftparticle_appearance_billboard?view=minecraft-bedrock-stable).

## Pixel art provenance

Created with the built-in imagegen tool using `lightning_strike.png` as the original
first-set style reference. Prepared as 128 × 128 transparent RGBA sprites with a
limited palette and crisp nearest-neighbour pixels. New cores use the corresponding
wand heads. Existing wand sprites and their models are preserved.

Final assets:

- `advance-magic/art/wands/solar_apocalypse.png`
- `advance-magic/art/wands/chronos_final_hour.png`
- `advance-magic/art/cores/core_solar_apocalypse.png`
- `advance-magic/art/cores/core_chronos_final_hour.png`

Prompt set: flat Minecraft pixel-art wand sprite, thin dark outlined diagonal shaft
from lower left to upper right, hard square pixel clusters, transparent background,
128-pixel effective grid, gold detailing, no text, scene, smooth shading or 3D voxels.
Solar variant: black/crimson shaft, radiant white/yellow miniature sun head with an
orange/crimson corona and jagged golden rays. Chronos variant: midnight-purple
shaft, antique-gold clock head with cyan hands and violet/cyan magical highlights.

## Verification

`evergarden/tests/run_bedrock_items.py` creates an isolated Paper server using the
local Geyser and Aeternum Seasons JARs. It verifies all item factories against three
Geyser protocol registries and runs `MythicSpellChecks`: complete spell timelines,
mana, cooldown, durability/repair, exact damage stages, delayed upgrade multiplier,
ally/protection filtering, early cleanup, concurrency limit, every GUI wand/core,
and actual Geyser particle packet objects plus the bundled emitter files.
Pack checks validate sprite transparency, routing, hashes and embedded resources.
This does not launch or restart the live Minecraft server.

The **initial 1.3.0 release** was verified on Paper 26.2 build 121 and Geyser 2.11.3 build 1246:
663 item translations across 218 custom identifiers / three protocol registries,
and 145 Mythic checks. Both original timelines completed without spell/particle errors.
For **1.3.1**, the existing fixtures' durations and particle allowance were updated,
and both JARs were built with Maven `-DskipTests`. Tests were not rerun in this
revision; native terrain cleanup and the expanded visuals still need a two-client
play check.
