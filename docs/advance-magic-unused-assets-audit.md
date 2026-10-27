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
