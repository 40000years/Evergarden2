# Legendary spectacle — 1.29.0 (Heaven's Descent beam cannon, slim packs)

Heaven's Descent is now a steerable golden beam cannon built from the Ancient Judge's gold seals.
Each of the seven elemental spectacles gains one more 3D layer. The plugin shrinks from 60.4 MB to
14.7 MB.

## Install

Replace the old Advance Magic JAR with `dist/advance-magic-1.29.0-light-lance.jar` (also delivered
as `dist/advance-magic.jar`, sha256 `61f86714…bd26`) and restart the server and Geyser. Players
download the new, smaller packs once (Bedrock pack **2.31.0**). The previous JAR is kept as
`dist/advance-magic-1.28.0-legendary-spectacle.jar`.

## Heaven's Descent: the beam cannon

Scroll ID and name are unchanged (`heavens_descent`); it no longer needs to be aimed at the ground.

1. **Charge, 1.2 s.** The gold judgment seal and its halo open three blocks in front of the caster,
   facing the aim, with a gold seal at the caster's feet. Four gold-block capacitors spiral in
   while enchanting glyphs and light converge on the seal. The seal follows the camera freely.
2. **Fire, 6 s.** A two-layer gold beam fires up to 48 blocks and stops at the first solid block.
   It follows the camera at up to 120° per second, so it sweeps rather than snaps. Gold rings run
   down the barrel, and a gold dust helix and a counter-turning sparkle spiral wind around the
   beam. At the impact point there are a seal, explosions, chips of the struck block and embers.
3. **Close.** A large blast at the last impact point, an expanding gold ring, and the capacitors
   fly apart.

Combat (`ElementalSpellService.heavensDescent`):

- Every five ticks the beam hits enemies within 1.4 blocks of its line, with line of sight. These
  hits share 80% of the spell's multiplier and push non-bosses slightly along the beam.
- The closing blast deals 20% in a 4-block radius, with stagger.
- Allied players in the beam are healed once a second, within the per-cast healing cap.
- Freezing the caster interrupts the beam.
- Mana, cooldown and the boss damage cap are unchanged.

| Asset | Source |
| --- | --- |
| `judgment_gold_seal`, `judgment_gold_halo` | XY and flat planes of the existing `judgment_seal_0` and `judgment_seal_1` textures (JSON only) |
| `judgment_gold_ray` | The existing 1 KB `solar_ray` texture, with a 0.15 s Bedrock lifetime so the beam does not flicker |
| Capacitors | Vanilla gold blocks (Java display plus Bedrock carrier) |

There are no muzzle flashes: a FLASH particle 3 blocks from the caster's eyes would blind them
while they aim. Flashes appear only at the far impact point.

`config.yml`:

- `elemental-combat.legendary-light`:
  - `beam-seconds: 6` (3..10)
  - `beam-range: 48` (16..64)
  - `turn-degrees-per-second: 120` (30..360)
- `elemental-visuals.legendary-light.enabled/scale` keep their meaning; `false` draws a plain beam.
- The light-rain keys are ignored: `sky-height`, `duration-seconds`, `afterglow-ticks` and `pull-strength`.

## More spectacle for the seven elements

| Spell | New 3D layer (Java and Bedrock) |
| --- | --- |
| Meteor Rain | A belt of six burning rocks orbits the sky gate. Eight burning chunks fly out of the giant crater and crash down |
| Tempest Dominion | Eight blocks of debris (dirt, grass, logs, leaves, cobblestone and more) spiral up the funnel and are flung out by the burst |
| Leviathan's Wrath | A second, guardian serpent with sea-lantern eyes coils round the caster's seal for the whole stage |
| Absolute Zero | A crown of six blue-ice crystals hangs 9.6 blocks up. They fall at the shatter and burst on the ground |
| Titan's Judgment | A ring of earth blocks heaves up along the shockwave as it rolls outward |
| Abyssal Collapse | Four leaning void tentacles (obsidian, crying obsidian, amethyst tips) curl toward the core and retract into the collapse |
| Wrath of Raijin | A thunder dragon of sea lanterns and glass, with a gold head, coils through the storm and dives with the great bolt |

Spectacle scenes now hold up to 32 display pieces (low quality still 10).

## Pack cleanup (`tools/build_light_lance_packs.py`)

| Pack | 2.30.0 | 2.31.0 |
| --- | --- | --- |
| Java | 30.65 MB | 7.39 MB |
| Bedrock | 29.54 MB | 6.25 MB |

Removed: the prismatic light-rain seal, halo, beams and motes (8.8 MB per pack), the painted Fire
meteor (unused since 1.28.0's 3D meteors) and advance-magic's duplicate Ancient Judge hand. The
Earth hand is retired, and Evergarden ships its own Judge assets and Geyser mappings, which are
unchanged. Four Geyser mappings are removed.

The remaining PNGs are palette-compressed with libimagequant. A texture is kept only if its
alpha-weighted colour RMSE is at most 4/255 and its mean alpha error at most 2/255; otherwise it is
recompressed losslessly. These textures are lossless only:

- staff and weapon atlases and icons, armour and entity textures, which checks require to be
  pixel-identical across clients;
- the five legendary weapon icons, which `check_legendary_weapons.py` pins to their source art and
  which stay byte-identical.

Source art in `advance-magic/art` is not modified. The builder needs `pip install imagequant` and
reads the 2.30.0 originals from `output/light-lance-20261025/before/`.

## Verification

- `LegendarySpectacleChecks` (`run_elemental_system_checks.py --probe spectacle`): **PASS 175** on
  the published JAR. It covers all eight Legendaries and the new beam test (charge, firing,
  steering at a bounded rate, piercing at 30 blocks, closing blast), Bedrock carrier parity,
  budgets, low quality and cleanup.
- `ElementalSystemChecks` (`--probe elemental`) now reaches 2,294 passes; on 1.28.0 it stopped
  at 918. Every Heaven's Descent combat check passes: real cast, mana, cooldown, damage 230.4
  within its 298.8 budget, and heals allies while damaging enemies. The suite still stops at
  "staff manager has standard 27-slot inventory", an unrelated menu check. Stale expectations from
  the 1.27/1.28 Legendary redesign were updated:
  - the Legendary scenes' icons;
  - the native Meteor Rain fireballs;
  - Abyssal Collapse's 110-tick timing;
  - low-quality and native-style displays.
- Asset checks that passed before still pass: staff 4,277, wooden staff 2,759, Bedrock staff
  VFX 361 and legendary weapons 236.
- `check_packs.py`, `check_legendary_assets.py` and `check_elemental_vfx_assets.py` already failed
  on the 2.30.0 packs, at an earlier point.
- `HeavensDescentVisualChecks` and `HeavensDescentBedrockChecks` (`--probe heavens` and
  `--probe heavens-bedrock`) test the retired light rain.
- No live Java or Bedrock client test.

Build: `python tools/build_light_lance_packs.py`, then `python tools/build_light_lance_release.py`.
Records are in `output/light-lance-20261025/` (`pack-build.json`, `release/release-build.json`);
the previous packs and JAR are in `before/` and `release/previous/`.

## 1.28.0: Legendary spectacle

All seven elemental Legendaries became full 3D/particle spectacles that look the same on Java and
Bedrock. No textures were added: the five reworked elements reuse rune circles that already ship in
both packs. Combat, damage timing, mana and cooldowns were unchanged. 1.28.0 was delivered as
`dist/advance-magic-1.28.0-legendary-spectacle.jar` (sha256 `6bab6c25…5e6a`, Bedrock pack 2.30.0).

### Bedrock parity

Geyser does not translate display entities, so 1.27.1 rocks and meteors were Java only. Every 3D
prop is now a uniform vanilla-block cube drawn twice: an ItemDisplay for Java viewers and an
invisible marker armour stand wearing the same block as a helmet, shown only to Geyser players,
with the same centre and edge length (`SCALE` attribute). Leaning or curved shapes are built from
staggered cube stacks, so the silhouette matches. Only the per-cube tilt is Java-only: Bedrock
helmets turn around the vertical axis. Particles are vanilla and translated by Geyser; Raijin's
bolts use `strikeLightningEffect`, which both editions show.

| Element | Spell | Reused seals (floor / halo) | 3D props and particles |
| --- | --- | --- | --- |
| Fire | Meteor Rain | own Fire seal | 1.27.1 meteors, now visible on Bedrock |
| Wind | Tempest Dominion | own Wind seal | 1.27.1 vortex (no props) |
| Water | Leviathan's Wrath | `chronos_ripple` / `lunar_orbit` | A prismarine leviathan breaches above each wave's crest, with humped body, snapping jaw, sea-lantern eyes and glass fins. Spray, rain from a ceiling halo, and a ripple per wave |
| Ice | Absolute Zero | `lunar_seal` / `lunar_orbit` | Frost buds during the charge. Six ice spires and a core rise, freeze on hit 2, and shatter on hit 3. Blizzard and snowflake vortex |
| Earth | Titan's Judgment | `solar_corona` / `verdant_halo` | A deepslate obelisk with a gold cap forms 16 blocks up, then slams down. Boulders, stone spikes, dust and an earth crack plane |
| Dark | Abyssal Collapse | `crimson_seal` | An obsidian core with heartbeats. Spiralling debris, accretion disc, whirlpool and shadow chains, ending in an implosion and sonic boom |
| Lightning | Wrath of Raijin | `lunar_constellation` / `lunar_orbit` | Six thunder drums under a storm halo. Bolts and a lightning strike every 20 ticks, with three at the finale |

The Ancient Judge hand is retired from Titan's Judgment; its boss assets are untouched.

`config.yml` → `elemental-visuals`:

- `legendary-particles-per-viewer-tick` (450; low quality 160).
- `legendary-lightning-strikes: true`: Raijin's cosmetic bolts (no damage or fire). Set it to `false` to drop the bolts and their thunder.
- Scenes hold up to 24 display pieces (10 on `quality: low`); each piece's Bedrock carrier is cleaned up with it.

Real Paper probe `advance-magic/tests/LegendarySpectacleChecks.java` (run with
`run_elemental_system_checks.py --probe spectacle`): **PASS 141** on the published JAR. It casts all
seven scrolls and checks:

- every Java block pose has a matching Bedrock carrier;
- each spell's choreography;
- damage alignment;
- particle and display budgets;
- low quality;
- cleanup.

Peak per-viewer particles stay within 420 per tick. Previews: `previews/legendary-spectacle/*.gif`
(software approximation, not game footage). Carrier size and offset (0.625 block per scale,
centre 1.69 scales above the feet) are calibrated from vanilla armour-stand geometry. They have not
been checked on a live Bedrock client.

Build: `python tools/build_legendary_spectacle_release.py` (`--no-copy` leaves `dist` alone). Record:
`output/legendary-spectacle-20261025/release/release-build.json`. Sources before this release:
`output/legendary-spectacle-20261025/before/`.

## 1.27.2: Legendary rune reuse

Fire and Wind keep the user-authored 3D/particle choreography. The five other
draft presentations are shelved: their ten new PNGs and bespoke hero sequences
are removed from runtime packs/code. Their scrolls use lightweight existing
weapon runes plus native particles. Heaven's Descent keeps its active light rain.

The original artwork remains in `advance-magic/art/effects/legendary-elements-v1`
as an archive. The unused wind-vortex picture and legacy Light mist are also
excluded from the downloadable packs. No production images were redrawn or
downscaled in this cleanup.

1.27.2 was delivered as `dist/advance-magic-1.27.2-reused-runes.jar`. Bedrock pack version is **2.30.0**. Embedded packs include
the four Ancient Judge hand components and their Geyser mappings, so the earth
spell does not need a second hand texture or a spawned boss encounter.

## 1.27.1: Meteor Rain and Tempest Dominion spectacle

`dist/advance-magic-1.27.1-fire-wind-spectacle.jar` (also `dist/advance-magic.jar`) replaces the
flat hero sprites of these two spells. Packs are unchanged (Bedrock 2.29.0); combat is unchanged.

- **Meteor Rain:** a burning sky gate 24 blocks up and a giant 3D magma meteor forming during the
  charge. Six tumbling 3D meteors with fire and smoke trails strike on the exact tick and spot of
  each hit, leaving burning craters. The giant meteor plunges at tick 96 and detonates with the
  final hit at tick 120: explosion, a fire column, lava and a fire wall rolling outward.
- **Tempest Dominion:** a tornado of six stacked magic circles that rise in turn, counter-rotate and
  sway, wrapped in spiralling cloud strands, wind streaks and a storm cap with sparks. Every damage
  pulse sends wind blades and a shockwave; lifts send an updraft; the funnel tightens then bursts.
- In 1.27.1 the 3D rocks were Java only and Bedrock saw painted meteor planes; 1.28.0 replaces the
  planes with Bedrock carriers of the rocks themselves.
- Budget: `elemental-visuals.legendary-particles-per-viewer-tick` (450; low quality 160) and up to
  24 display pieces (low 10).

Real Paper probe `advance-magic/tests/LegendarySpectacleChecks.java` (run with
`run_elemental_system_checks.py --probe spectacle`): **PASS 47**. Offline previews from that
capture: `previews/legendary-spectacle/*.gif` (software approximation, not game footage).
No live Java or Bedrock client test yet.

## Spells

| Element | Scroll ID | Presentation and combat |
| --- | --- | --- |
| Fire | `meteor_rain` | 50-tick charge, six small meteors and one large final impact, burn and knockback; finale keeps a brief visual tail |
| Water | `leviathans_wrath` | Five waves 24 ticks apart, each ridden by the 3D leviathan; previous combat retained |
| Ice | `absolute_zero` | Slow, freeze at 20 ticks and shatter at 60 ticks under six ice spires; previous combat retained |
| Wind | `tempest_dominion` | Six-second layered vortex, six damage pulses, orbital impulses and brief lifts |
| Earth | `titans_judgment` | The obelisk slam with boulders and spikes; previous combat retained |
| Dark | `abyssal_collapse` | Heartbeats at 0 and 40 ticks, collapse and sonic boom at 80 ticks; previous combat retained |
| Lightning | `wrath_of_raijin` | A strike every 20 ticks and three at the 100-tick finale; previous combat retained |

Each of these seven spells has total base multiplier **16**, divided across its
hits. Native damage events, PvP/team rules, protection events, line of sight,
target caps, boss damage budgets and boss displacement resistance still apply.
Mana and cooldown values remain as defined for the existing scrolls. Phoenix
Rebirth and Tsunami retain their previous implementation.

For example:

```text
/spell give staff fire mythic
/spell give scroll meteor_rain
```

Use the matching element (`fire`, `water`, `ice`, `wind`, `earth`, `dark` or
`lightning`) and a scroll ID from the table. Imprint through `/spell`, select its
slot and cast. Legendary scrolls require a Mythic staff. Sounds follow
`elemental-visuals.sounds`.

## Resources and lifecycle

Three selected Fire/Wind PNGs remain active: Fire seal, Fire meteor and Wind
seal. Other spells reference existing Lunar/Verdant/Crimson models and particles
directly, without copying their PNGs under new names. The 58 existing scroll icons
and active Light artwork are preserved.

The Bedrock pack shrank from 60,437,390 to 29,540,535 bytes (about 51%). The Java
pack shrank from 61,547,976 to 30,654,693 bytes. All unrelated retained pack entries
keep their bytes. Gameplay classes remain byte-identical to the Fire/Wind JAR.

In 1.27.2 (retired in 1.28.0), Earth used the existing `voidscape:judge_left` and `voidscape:judge_left_light`
models and atlas, with Java ItemDisplay and Bedrock ArmorStand carriers. Both use
the same reduced scale and centre alignment. Four active hands per world is the
hard limit, with cleanup on cancellation, scene completion and owner cleanup.
Other authored scene limits retain 12 Balanced / seven Low display layers and
the existing shared packet budget. Optional detail may be omitted under load.

The seven combat stages have finite ownership and cleanup for interruption,
recast, death, logout, world change and unloaded centres.

## Build records

```text
python tools/clean_legendary_resources.py
python tools/build_legendary_cleanup_release.py
```

The release compiles the visual sources against the delivered
`1.27.1-fire-wind-spectacle` JAR and preserves its unrelated classes. This isolates the
release from other work in the shared workspace. The full pack builder also
registers the collection for future builds.

- Pack record: `output/legendary-cleanup-20261024/resource-cleanup.json`
- Release record: `output/legendary-cleanup-20261024/release/release-build.json`
- Fire/Wind source preservation: `output/legendary-cleanup-20261024/fire-wind-preservation.json`
- Historical texture gallery: `previews/legendary-elements-v1/index.html` (includes shelved artwork)

This release has Java 25 compilation, source review and archive packaging checks.
No gameplay test server or live Java/Bedrock visual/audio client test was run for
the seven new spell scenes.
