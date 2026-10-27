# Elemental staff asset cleanup

Audit date: 2026-10-17. Active offensive equipment is now eight staffs:
`fire`, `wind`, `earth`, `ice`, `water`, `lightning`, `light`, `dark`.
Their public item models and Geyser identifiers are
`advance_magic:staff_<element>`. The two element cycles are
Fire → Wind → Earth → Ice → Water → Fire and
Lightning → Light → Dark → Lightning.

## Removed

The pack builder no longer registers a separate equipment item for each of the
21 spell IDs. It registers only the eight new elemental staffs on
`minecraft:carrot_on_a_stick`. A fresh temporary staging directory prevents old
models, texture-atlas entries and mappings from surviving a rebuild.

Removed 19 ordinary wand cutouts from `advance-magic/art/wands/`: the 15 current
ordinary spell sprites, plus the already retired `invisibility_shroud`,
`poison_spores`, `soul_drain` and `time_dilation` sprites. Removed their 15 raw
image originals in `art/generated/`, the obsolete ordinary generation prompt
catalog, the unused `prepare_wand_art.py` and `generate_reworked_wands.py`
generators, and the old ordinary wand preview HTML/PNG. The new preview is
`advance-magic/dist/elemental-staff-preview.html`.

These are 39 exact files totaling 24,051,173 bytes. No recursive directory
deletion was used. The complete removed-file list, sizes and SHA-256 digests,
plus retained-source digests, are in
[elemental-staff-cleanup.json](elemental-staff-cleanup.json).

For ordinary spell IDs, generated Java item definitions, flat item models and
item PNGs are absent from the rebuilt pack. Bedrock ordinary item sprites and
atlas entries are also absent. The six preserved IDs below retain direct Java
model compatibility resources but are absent from the Geyser equipment catalog.

## Retained dependencies

| Resource | Reason retained |
| --- | --- |
| Solar Apocalypse, Chronos: Final Hour, Heaven's Judgment, Verdant Sovereign, Lunar Requiem, Crimson Oblivion wand textures | Preserve all mythic/legendary artwork and direct Java model references, including source concepts and cutouts. These are unregistered compatibility assets. |
| All 21 spell cores | Pending boss-reward entitlements, saved-item migration, restoration fuel and compatibility APIs still use these identifiers. New core loot and old wand recipes are retired. Both packs retain the existing core art and routes. |
| All shared spell effects, including Solar, Chronos, Judgment, Verdant, Lunar and Crimson | Runtime spell rendering still uses these effect models and particles. No effect was removed by this cleanup. The previously retired Lunar crescent remains excluded from shipped packs. |
| Excalibur, Longinus, Tyrfing, Mjolnir and Vajra | Separate legendary weapon system; original models, icons, attachables and VFX remain. |
| Restoration wand/core | Separate restoration utility system registered by `restoration_assets.py`. |
| Flying staff and its animation states | Separate flight utility system; authored native item/attachable resources and mappings remain. |
| Potions and upgrade cores | Existing mana/upgrade systems continue using these items. |

## Dependency audit and validation

Searched production sources, build scripts and tests under `advance-magic`,
`evergarden`, `tools`, `tests`, `.github`, the README and documentation for old
texture paths, spell item model IDs, the ordinary generator names and raw-image
paths. The only ordinary PNG consumers were the retired generators, the updated
pack builder and the updated asset checks. Neither removed generator had an
external import or caller. The legendary exporter mentioned the ordinary
workflow in prose; that reference has been updated.

Spell/core identifiers are retained as API and saved-item data. Their continued
presence in gameplay sources does not imply a dependency on a retired wand PNG.
Historical release archives and previews outside the active source/catalog paths
are not merged into the generated packs.

The existing Evergarden Java and Bedrock packs and Geyser mappings were also
inspected: zero old wand registrations, zero direct wand Java models, and zero
ordinary wand Bedrock sprites. Its builder only copies shared `core_*` assets
and already uses fresh temporary staging. Regression checks ensure it cannot
duplicate the new staff mappings or reintroduce the old wand textures.

`check_packs.py` verifies eight unique Geyser routes, native 3D staff models,
resolved model/attachable textures, removal of ordinary texture/model routes,
the 21 existing core routes, preserved mythic/legendary artwork, valid PNGs,
archive integrity and hashes. `check_legendary_assets.py` continues checking all
live shared effects and cross-pack core resources. The cleanup manifest also
checks that retained source bytes have not changed and removed files have not
been reintroduced. Bedrock's content version advances from 2.14.2 to 2.15.0.

`check_elemental_staff_assets.py` independently compares the exported Java,
Bedrock and editable Blockbench cuboids, resolves texture/animation/controller
dependencies, checks all eight VFX frames and hotbar silhouettes, and evaluates
physical Java grip contact in both hands against the existing native-item
coordinate fixture. These checks inspect exports. A separate isolated server
probe passed 148 actual Geyser/ModelEngine R4.1.1 checks, including import,
model generation and idle playback for all eight staffs. Final connected-client
appearance still requires an in-game check.

Editable models, metadata, README and a copy of the repository generator are
distributed separately in `advance-magic/dist/elemental-staff-sources.zip` with
fixed archive timestamps. The generator copy uses the original repository's
shared legendary-weapon export and preview helpers; run it in that repository.
No `.bbmodel` source is included in either client resource pack.
