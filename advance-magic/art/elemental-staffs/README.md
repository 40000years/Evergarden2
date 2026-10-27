# Elemental staffs

Eight new thick 3D staffs replace the old spell-specific wand catalogue:

- Fire → Wind → Earth → Ice → Water → Fire
- Lightning → Light → Dark → Lightning

The concept sheet guides silhouette, colour and material. Models are authored
from solid cuboids with substantial depth, faceted inset crystals, open crowns,
separate front/back ornament, and unobstructed leather grips. There is no pasted
concept art, invisible backing plate, or pedestal attached to a held item.

`tools/elemental_staff_assets.py` regenerates every asset from the same cuboid
catalogue. Run `python tools/elemental_staff_assets.py` from the repository root.
Use `python tools/elemental_staff_assets.py --bedrock-only` to refresh Bedrock
effects while verifying that Java, body geometry, icons and blueprints retain
their original bytes.

## Contents

- `java/`: native item models, context-specific animated 3D render icons,
  animated pixel materials and eight-frame transparent idle effects.
- `bedrock/`: native attachables, body geometry, first/third-person poses,
  animated focus/orbit bones, inlays and small solid crown glints.
- `blockbench/elemental_staff_*.bbmodel`: editable ModelEngine 4.1.1 sources with
  embedded texture, named bones, a three-second `idle` animation, and the required
  helper hitbox. Top-level copies support the server integration checks.
- `*.geometry.json`: source cuboids and physical hand-contact coordinates.
- `metadata.json`: public keys, Geyser v2 definitions, item atlas additions,
  blueprint IDs, descriptions and cycle membership.

Public item keys are `advance_magic:staff_fire`, `staff_wind`, `staff_earth`,
`staff_ice`, `staff_water`, `staff_lightning`, `staff_light`, and `staff_dark`.
Their base material is `minecraft:carrot_on_a_stick`. ModelEngine IDs are
`elemental_staff_fire`, `elemental_staff_wind`, and so on.

## Grip and effects

Each model has its own physical grip on a continuous solid shaft. Java display
transforms map that contact to the vanilla handheld contact point. Bedrock uses
the verified native trident item frame: root pivot `[0,24,0]`, grip child pivot
`[0,12.5,0]`, and slot binding. Both body and effects share that hierarchy. Only
the grip child is scaled; camera framing is separate from third-person contact.
Off-hand poses mirror the whole pose consistently. These are calibrated exports;
final screen framing should still be viewed on the actual game clients.

Both clients animate held VFX locally. Java uses eight-frame cutout textures and
breathing emissive material tiles. Bedrock uses two tiny solid crown glints in
first person and three in third person, with a gentle vertical bob. Their own
alpha-test render controller ignores lighting and rebuilds the secondary
geometry's animation matrices. There are no full-staff Bedrock effect sheets or
separate frame textures. Focus, orbit, halo, ribbon, flame and spiral body motion
remain intact. No
per-held-staff server entities or particle emitter entities are created. GUI
icons are rendered from the same textured solid geometry, fit uniformly with a
transparent gutter; Java icons animate, while Bedrock GUI icons use one frame.

## Individual designs

| Element | Crown and ornaments |
| --- | --- |
| Fire | Open golden brazier, crimson sunstone, flame crown, gold-stitched silk tails and ember charms |
| Wind | Twin ivory feather crescents, mint focus, jade studs and a real winding ribbon |
| Earth | Split basalt antlers, peridot heart, irregular stones, moss, roots and leaves |
| Ice | Sixfold silver snowflake, sapphire lances, icicle chains and crystal ferrule |
| Water | Opposed sculpted breaking waves, azure pearl, foam ridges and spiralling water |
| Lightning | Asymmetric fork conductors, storm core, jagged lightning spine and coil rings |
| Light | Golden astrolabe, sun diamond, horizontal halo and suspended light blades |
| Dark | Blackened crescent, void amethyst, hooked horns, violet chains and thorns |
