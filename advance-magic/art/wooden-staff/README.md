# Amethystwood Staff

`advance_magic:staff_wood` is a neutral starter staff, authored as 270 solid
cuboids with the same physical hand contact and native export helpers as the
eight elemental staffs. The warm chestnut branch, grain, leather grip and
restrained blaze brass surround three fixed violet amethyst crystals. Crystal
tips taper in depth as well as width, so the silhouette works from the side.

The staff begins with one spell slot, upgrades to two slots at Rare, and has no
elemental affinity. Gameplay owns its 50% damage penalty and Rare upgrade cap.
These art assets do not independently enforce gameplay rules.

- Java: native 3D held model, large separate GUI icon, slow material shimmer.
- Bedrock: native bound attachable, separate first/third person hand poses,
  opaque wood/leather geometry and emissive crystal facets, matching GUI icon.
- No fullstaff picture planes, particles, server effect entities or detached
  moving crystal pieces are used.
- `wooden_staff.bbmodel` and `blockbench/wooden_staff.bbmodel` are identical
  editable sources, with an embedded 128 px material atlas.
- `wood.geometry.json` preserves the exact untransformed source cuboids.
- `metadata.json` supplies the Geyser mapping, item atlas and calibrated grip.

Regenerate only this staff with `python tools/wooden_staff_assets.py`. The script
never regenerates or writes the eight original elemental staffs.

Review images live in `previews/wooden-staff/`: multiangle geometry, transparent
hero render, 128 px / 16 px icon comparison, and the existing screenshot-calibrated
approximate Bedrock held frame. They are deterministic asset renders. Native
client lighting, FOV, arm movement, and ModelEngine import still need an in-game
check; these images do not claim to be client screenshots.
