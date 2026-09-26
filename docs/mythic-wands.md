# Solar Apocalypse and Chronos: Final Hour

Release: Advance Magic `1.3.0-mythic-wands`, Evergarden `3.0.0-e2.12-mythic-wands`.
Install both JARs from the root `dist` directory and restart the server, then reconnect
Bedrock so Geyser offers the new packs (Advance Magic 2.2.0 / Evergarden 3.9.0).

## Abilities

| Wand | Mana | Base cooldown | Timeline | Base damage on a target hit by every stage |
| --- | ---: | ---: | --- | ---: |
| Solar Apocalypse | 100 | 45 s | Giant rising sun, golden glyph, five descending beams, falling sun and expanding shockwave | 5 × 32 + 180 = 340 |
| Chronos: Final Hour | 95 | 40 s | Giant moving clock, short root, five time blades, reversed 70% echoes and clock shatter | 5 × 24 + 5 × 16.8 + 100 = 304 |

Solar finishes after about 7 seconds; Chronos after about 7.5 seconds. Aim at a mob
or block within 30 blocks. With no hit, the spell centers 16 blocks ahead. Solar's
final burst has radius 16; Chronos attacks within radius 12. Damage uses the normal
magic protection event, enemy/team/PVP filters and wand damage upgrades.
High-health mobs (150+ maximum health), Wither, Warden and Ender Dragon receive
Slowness II instead of a hard root. Players use the existing root compatibility policy.
Native resistance to potion effects still applies. Neither spell edits terrain, time,
weather, chunks or a player's camera. Closing a cast cancels its remaining attacks.

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
`damage.chronos-shatter`. `mythic-max-active-per-world` defaults to 4 (range 1–16).
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

Verified on the installed Paper 26.2 build 121 and Geyser 2.11.3 build 1246:
663 item translations across 218 custom identifiers / three protocol registries,
and 145 Mythic checks. Both full timelines completed without spell/particle errors.
