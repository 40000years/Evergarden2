# Forge 0.5.1 item artwork

All eight gem elements, five purity levels per element, five salvage materials, and gem dust now use the selected imagegen artwork: 46 unique transparent textures.

- `source/`: full-resolution transparent imagegen originals. The accepted fire purity V preview is reused from `../redesign-v1/`; all other 45 assets were generated separately for this revision.
- `prompts.json`: complete prompts and references for each selected asset.
- `generations.json`: imagegen output origins and copied project paths.
- `manifest.json`: original and packaged texture SHA-256 hashes.
- `../catalog-v2.png`: all 46 production textures, ordered by element and purity.
- `inventory-review.png`: 16px and 32px samples side by side, each enlarged 2× with nearest-neighbor scaling for inspection.

`python tools/build_forge_resources.py` loads these originals, downsizes to 64×64 with nearest-neighbor sampling and preserves alpha. It does not redraw, recolor, add symbols, or fabricate lower-quality variants. Every purity level has its own selected source image.

The Java and Bedrock packs contain identical PNG bytes. Existing item model IDs and both Bedrock UUIDs remain stable. Bedrock version is 0.5.1; Java uses the new computed SHA-1. Older release JARs and public GitHub packs are preserved.
