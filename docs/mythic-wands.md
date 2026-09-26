# Solar Apocalypse and Chronos: Final Hour

Release: Advance Magic `1.3.5-player-guide`, Evergarden `3.0.0-e2.18-player-guide`.
Install both JARs from the root `dist` directory and restart the server. This revision
bundles refreshed packs (Advance Magic 2.3.0 / Evergarden 3.10.0) with two newly
designed elemental cores. Reconnect clients to download the updated artwork.
The player guide now covers all 17 spells, wand durability/upgrades, both restoration
methods, ruined altar sites, flying staff controls and current item sources.
See [the guide and patch infographic](player-guide-update.md).
Evergarden migrates official pack URLs to the immutable published Java pack;
administrator-owned private CDN URLs continue to require their own pack update.

Evergarden e2.14 also fixes Ancient Astral Root consumption: its `DRAGON_BREATH`
effect now supplies the required `Float` power (`1.0f`), matching the other dragon
particle calls. Mana gains and daily-use limits are unchanged. This fix was built
with `-DskipTests`; no live consumption test was run for this revision.

## Abilities

| Wand | Mana | Base cooldown | Timeline | Base damage on a target hit by every stage |
| --- | ---: | ---: | --- | ---: |
| Solar Apocalypse | 100 | 45 s | Five stacked suns with doubling diameters and wide gaps, golden glyph, five beams, simultaneous descent, shockwave and lava sea | 5 × 32 + 180 = 340, plus lava contact damage |
| Chronos: Final Hour | 95 | 40 s | Five upright clocks beneath a floating horizontal dial, outer End Crystal beams converging downward on the central clock, inner lasers, reversed echoes and poison sea | 5 × 24 + 5 × 16.8 + 100 = 304, plus poison/contact damage |

Solar's nominal sun radii are 4.4, 8.8, 17.6, 35.2 and 70.4 blocks: both radius
and diameter double at every layer, preserving a **1:2:4:8:16** size ratio. Their
mature centres are 18, 39.2, 73.6, 140.48 and 274.24 blocks above the aim point.
Surface-to-surface gaps are 8, 8, 14.08 and 28.16 blocks. All begin descending at
tick 90 and converge on the same impact at tick 110; final damage is applied once.
The complete stack scales uniformly to fit below the world's height limit, including
its outer rays; casting is refused below 10% of normal headroom. For example, with
an aim point at Y=100 and a world ceiling of 320, the scale is about 0.633: diameters
are approximately 5.57, 11.14, 22.27, 44.54 and 89.08 blocks. The doubling ratio is
unchanged; apparent screen size also depends on the viewer's distance and angle.
Geometry is redrawn every six ticks with 48–112 samples per great circle.

Chronos keeps the five upright 8-block-radius clocks and places its outer dial
**horizontally in the XZ plane**, with radius 22 (44 blocks across). The main clock
centre is 24 blocks above the aim point; the outer dial floats another 12 blocks
above it, at a height of 36 blocks. Its outline, hour markers, hands and crystal
positions all use the horizontal plane. Twelve native End Crystal emitters occupy
its hour positions and beam down into the central clock from tick 40 until shatter
at tick 160. Inner clocks retain their coloured particle lasers into the ground
target. Only the outer dial uses the End Crystal beam style.

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

Outer Chronos beams use client projections built with Paper's
[`createEntity`](https://jd.papermc.io/paper/26.2/org/bukkit/RegionAccessor.html#createEntity(org.bukkit.Location,java.lang.Class))
and native spawn/metadata/remove packets. The crystals are never added to a server
world, so they cannot heal dragons, explode, ignite terrain or be blocked by a
monster-spawn rule. Geyser translates their beam targets to Bedrock's
[`BLOCK_TARGET_POS`](https://github.com/GeyserMC/Geyser/blob/master/core/src/main/java/org/geysermc/geyser/entity/type/EnderCrystalEntity.java).
Viewers entering/leaving the 64-block cast range are added/removed; shatter, owner
cleanup and plugin shutdown remove projections. If the native packet API is
unavailable, one warning is logged and the outer rays use coloured particles.

The optional adapter resolves classes from the installed Geyser plugin without
adding Geyser as a required dependency. If its API changes, it logs one warning
and falls back to vanilla dust. Client particle settings and each edition's renderer
can affect appearance; visual parity still needs an actual two-client play check.
The particle format follows [Minecraft's billboard component](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/particlesreference/particlecomponents/minecraftparticle_appearance_billboard?view=minecraft-bedrock-stable).

## Pixel art provenance

Wands were created with the built-in imagegen tool using `lightning_strike.png` as
the original first-set style reference. Prepared as 128 × 128 transparent RGBA
sprites with a limited palette and crisp nearest-neighbour pixels.

The **1.3.4 / e2.17** core redesign uses the built-in `image_gen.imagegen` tool with
the original Lightning, Meteor and Levitation **core** sprites as style references.
Both new sprites use the same round glass relic and antique gold compass-frame
family as the established cores, each with four small cardinal studs:

- **Core of the Sun:** amber/orange glass containing a bright faceted solar-energy
  crystal and an internal spiral of fire.
- **Core of Time:** violet glass containing a cyan hourglass with gold end caps and
  falling pale cyan sand.

They are independently generated objects with their own silhouettes and internal
symbols. Production sprites are converted to 128 × 128 with nearest-neighbour
sampling and their generated RGBA transparency is retained. Java and Bedrock
receive these same PNGs through both bundled packs; existing core items use their
existing identifiers and automatically display the new designs after pack refresh.
The standalone core generator preserves the two authored Mythic PNGs.

Exact prompt set and references:
[`advance-magic/art/mythic-core-prompts.json`](../advance-magic/art/mythic-core-prompts.json).
Size-conversion command (after generating both source PNGs):

```powershell
./advance-magic/tools/prepare_mythic_core_art.ps1 -SunImage <sun.png> -TimeImage <time.png>
```

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
For **1.3.1**, the existing fixtures' durations and particle allowance were updated.
The existing particle allowance is now 65,000 packets per cast to cover the larger
geometry plus the particle fallback for outer beams. The **1.3.2 / e2.15** JARs
were built with Maven `-DskipTests`; the packet API was
inspected against the local Paper 26.2 JAR and the installed Geyser beam translator.
The **1.3.3 / e2.16** refinement also uses Maven `-DskipTests` and changes the
Solar size/spacing and outer Chronos orientation; it adds no resource-pack assets.
The **1.3.4 / e2.17** core redesign rebuilds all four packs and both JARs with
Maven `-DskipTests`. Pack archive inspection shows only the two core PNG entries
changed in each Java ZIP, plus the manifest versions in each Bedrock pack.
Tests were not rerun in these revisions. Native terrain cleanup, five-sun motion,
the doubled sphere sizes and horizontal crystal beam projections still need a
two-client play check.
