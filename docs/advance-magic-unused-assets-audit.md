# Advance Magic unused asset cleanup

The current Lunar spell renders sixteen `lunar_seal` portals, `lunar_orbit`
halos, `lunar_constellation` floor art and `lunar_ray` columns. The older
`lunar_crescent` has no runtime reference in the current Java sources.

Removed from the shipped Java pack:

- `assets/advance_magic/textures/effect/lunar_crescent.png`
- `assets/advance_magic/models/effect/lunar_crescent.json`
- `assets/advance_magic/items/lunar_crescent.json`

Removed from the shipped Bedrock pack:

- `textures/particle/lunar_crescent.png`
- `particles/lunar_crescent.particle.json`
- `particles/lunar_crescent_flat.particle.json`

The editable PNG/SVG and concept previews remain in the artwork directory.
They are not bundled or sent to players. The pack builder excludes the old
effect, so rebuilding does not restore it. Bedrock content version is 2.11.1.
Also removed the obsolete `wand(color, variant)` procedural builder helper;
the active builder copies the authored 128px wand PNGs instead.

All other effect textures have live references, including the dynamically
selected `judgment_seal_0` and `judgment_seal_1`. Solar, Chronos, Judgment,
Verdant, Lunar and Crimson rendering remains intact. The public disabled
`ManaService.drinkDragonBreath` compatibility method is retained; removing a
public plugin API could affect an external integration, and it contributes
no texture-loading work.

Verification compared every ZIP entry before and after rebuilding. The only
removed files were the six listed above; no new entry was added. Every retained
entry is byte-identical except the Bedrock manifest version. Wand/core mappings
and all 21 spell routes are unchanged. Legendary asset checks and release
compilation pass.

The Java ZIP is smaller by 755,173 bytes and the Bedrock ZIP by 755,800 bytes.
Removing the 1024×1024 image eliminates 4 MiB of raw RGBA pixels per client;
actual atlas allocation and loading time depend on the client. This cleanup
does not change the existing delivery of three separate Java packs.

## 1.35.0: retired wand-spell engine (2026-10-27)

The 21 wand spells (Lightning Strike … Crimson Oblivion) stopped casting long
before this release: `CastListener.castInput` returned `false`, and legacy wands
migrate into elemental staffs. Their engine and art still shipped. Advance Magic
1.35.0 (`tools/build_unused_cleanup_release.py`, Bedrock pack 2.36.0) removes
them.

**Code (server only):** 21 classes (41 class files) are gone:
- the spell engine: `SpellRegistry`, `Area/Projectile/Mobility/Channel/Mythic/Legendary/BasicSpells`;
- its visuals: `BasicSpellVisuals`, `LegendarySpellVisuals`, `Solar/ChronosLineVisuals`,
  `Crystal/JudgmentBeamVisuals`, `JudgmentSealVisuals`, `LegendaryCastGeometry`, `LegendaryEarthHand`;
- `BasicSpellProfile`, `effect.Geometry`, and `LonginusProjectileService`/`LonginusBedrockBridge`.
  Longinus has been melee-only, and that bridge was never started.

`MagicContext` loses the wand-only helpers and cast multipliers. `ManaService` no
longer persists wand cooldowns or keeps `drinkDragonBreath`. The default
`config.yml` drops `basic-casting`, `mythic-max-active-per-world`, `legendary`,
`meteor`, `damage` and `follow-up`. Existing server configs keep these keys
harmlessly.

These stay on purpose:
- The `api` package.
- The `Spell` enum, which defines legacy wand identity and drives staff migration.
- `CastListener.cast(...)` as a compatibility entry point that shows the staff
  loadout and returns `false`.
- `StatusService`: its static teleport helper is live, and the Ancient Judge
  probe uses it.
- `TemporaryTerrainService`, which recovers terrain journals after a crash.

**Packs:** `tools/retired_magic_assets.py` lists the retired items. Each model,
texture or Bedrock file they used is dropped only when nothing kept refers to it.

| | Java | Bedrock |
|---|---|---|
| Removed entries | 53 | 53 |
| Size | 7,356,015 → 6,407,788 bytes | 6,318,295 → 5,366,916 bytes |

Of the removed textures, 10 are 1024×1024: 41.2 MiB of decoded RGBA per client.

| Removed | Kept, because live code still uses it |
|---|---|
| `chronos_dial/echo/hour/minute/ray`, `crimson_heart/ray/shard`, `lunar_ray`, `shulker_prism`, `solar_orbit`, `verdant_beam_core/dragon/ray/seal`, `judgment_beam`, the `judgment_seal_0/1` item+model+particle, `longinus_projectile` | the `judgment_seal_0/1` textures (Heaven's Descent uses them through `judgment_gold_*`); `lunar_seal/orbit/constellation`, `crimson_seal`, `chronos_ripple`, `solar_corona`, `verdant_halo`, `solar_ray`; the six Mythic wand icons, shown on a legacy wand until it migrates |

`advance-magic/tools/build_packs.py` prunes the same set, so a full rebuild does
not restore it.

**Verification:**
- Every other JAR and pack entry is byte-identical. The packs differ only in the
  Bedrock manifest version.
- `GeyserPackCleanup`'s source carries an unreleased `RETIRED_CORE_IDS` edit that
  no JAR ever shipped, so the shipped class is kept.
- A class-file linkage check confirmed that no reference which resolved in 1.34.1
  became unresolved. It covered all classes of Advance Magic and all 464 classes
  of the shipped `dist/evergarden.jar`.
- The retired suites were removed: `BalanceChecks`, `IntegrationChecks`,
  `BasicCastingChecks`, `LegendarySpellChecks`, `ReleaseLinkageChecks`, and
  Evergarden's `MythicSpellChecks` and `LegendaryBedrockChecks`.
- `ManaPotionChecks` now runs on its own host: `ManaPotionProbe` via
  `run_mana_potion_checks.py --magic <jar>`.
