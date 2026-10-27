# Legendary elemental VFX textures — v1

**Archived draft collection:** In `1.27.2-reused-runes`, only the Fire seal, Fire
meteor and Wind seal remain in runtime packs. The other five element sets and
unused Wind vortex stay here as source artwork but are not downloaded by players.
Those spells reuse existing Lunar/Verdant/Crimson rune resources instead. The
user's Fire/Wind choreography is preserved. See `docs/legendary-elements.md`.

Approved artwork for seven flagship spells, created with built-in ImageGen:

| Element | Existing spell | New textures |
| --- | --- | --- |
| Fire | Meteor Rain | volcanic seal, flaming meteor |
| Water | Leviathan's Wrath | tide seal, water dragon |
| Ice | Absolute Zero | crystalline seal, crystal crown |
| Wind | Tempest Dominion | airflow seal, open spiral vortex |
| Earth | Titan's Judgment | golden architectural seal, hollow impact shockwave |
| Dark | Abyssal Collapse | eclipse-chain seal, fractured obsidian core |
| Lightning | Wrath of Raijin | thunder seal, floating drum array |

## Earth model reuse

Use the existing Ancient Judge hand from `boss-temple`, as requested. The actual
components are `voidscape:judge_left` + `voidscape:judge_left_light` (or the matching
right pair), with the existing Java models, atlas and Bedrock carrier/attachable
path. `earth/boss-hand-reuse.json` records the source geometry, scale, anchors and
SLAM / BAIT_SLAM movement reference. No new hand image or model was drawn.

## Files and provenance

Each element directory contains two selected native RGBA PNGs, original generated
images in `source/`, and a `manifest.json` with full prompt provenance or its prompt
record link. Earth also records the complete prompt history in
`earth/generation-prompts.json`. Selected production pixels are unchanged from the
generated originals. Review thumbnails and the contact sheet are resized only for
presentation; they are not production textures.

`catalog.json` gives exact dimensions, SHA256, transparency and suggested plane/UV
parameters. Images have true alpha. Some native outputs are tightly trimmed, so
future atlas preparation should add transparent padding while keeping the image
aspect ratio. Use actual dimensions for Bedrock UVs rather than assuming square
textures: the water assets have non-square native dimensions.

## Review and delivery

- Gallery: `previews/legendary-elements-v1/index.html`
- Contact sheet: `previews/legendary-elements-v1/texture-board.jpg`
- Actual model reference: `previews/legendary-elements-v1/boss-hand-reuse.png`
- Bundle: `dist/legendary-element-textures-v1.zip`
- Gallery/bundle builder: `python tools/prepare_legendary_element_art.py`

The initial delivery contained texture artwork only. The complete draft was
integrated in `1.27.0`, then selectively cleaned in `1.27.2` as described above.
See `docs/legendary-elements.md` for current installation and build scope. The model reference
uses real geometry and atlas under simulated lighting; this gallery remains an
art review and is not an in-game screenshot.
