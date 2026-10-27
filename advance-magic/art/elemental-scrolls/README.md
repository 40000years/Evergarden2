# Elemental parchment scrolls

Authored resource artwork for the existing 58 `advance_magic:scroll_<spell_id>`
items. Each spell has its own central illustration, with element-coloured gems
and bindings. Basic scrolls use restrained metal caps; Rare adds engraved caps
and leather straps; Epic adds a dark backing and flared hardware; Legendary adds
a crown, wing-shaped ornaments and a hanging seal.

- `source/`: eight original transparent image atlases from the built-in ImageGen tool.
- `generation-prompts.json`: the exact prompt set and tool provenance.
- `icons/`: 58 final 128×128 RGBA PNG textures with transparent margins.
- `manifest.json`: spell metadata, original crop bounds and final PNG SHA-256 hashes.

The source atlas order matches the order of each element's entries in
`ElementalSpell.java`. Preparation isolates connected artwork inside each cell,
keeps its alpha edge, centres it and downsamples it with a 4px minimum margin.
No network service is needed for subsequent pack builds.

From the repository root:

```powershell
python tools/prepare_elemental_scroll_art.py
python tools/elemental_spell_assets.py
python tools/build_elemental_scroll_resources.py
```

The scoped resource updater replaces only scroll PNGs and the Bedrock cache
version, verifying every other pack entry and the Geyser mappings are unchanged.
The normal full pack builder also consumes these final icons and retains the original
Java item models, Geyser identifiers and Bedrock atlas keys. Bedrock resource
version 2.25.0 refreshes its cached textures. Gameplay metadata is unchanged.

The gallery at `previews/elemental-scrolls/index.html` is self-contained and
supports element/rarity filters, searching, old/new comparison and 16–128px
display sizes. `overview.png`, `all-scrolls.png` and `before-after.png` are
exportable review images. The menu images are rendered mockups using the existing
54-slot layout, not live game screenshots.
