# Banshee Wail: the Mourning Veil model

Replaces the Banshee Wail river rig (`modelengine/rivers/arkhaia_banshee_wail.bbmodel`) with a
redesigned floating widow. Gameplay, HP, skills and the 15-clip table are unchanged.

## Look

- A tall pierced silver comb behind the crown carries a dark lace mantilla. The mantilla falls
  beside the face and down the back in six lengths (`veil_0..veil_5`).
- The face is pale and gaunt, with glowing sea-foam eyes and tear tracks. The jaw is hinged and
  opens onto a glowing throat for the wail.
- Long silver hair falls from under the veil.
- The overgown is dark and open at the front. Through the opening a luminous underdress shows,
  and both fray into light at the hem.
- She also has bell sleeves, a lace shawl, a long cape and long hands with glowing claw tips.
- Amethyst details echo the Soul Anchors: a locket, a cameo, the girdle gem and a soul lantern
  at the hip.
- On the aura lane, a crescent halo with tear-drop chimes cradles the head and three soul wisps
  orbit her.

Numbers: 347 cuboids, 42 bones, a 1024 atlas and a 1.4 MB blueprint (the previous one was
5 MB). The tallest pose stays below the identity seal, which sits 12.3 blocks up at scale 4.

## Contract kept

- **Bones read by the game and tests:** `hi_gaze`, `head`, `head_forward` (the wail origin for
  `RiverVisual.castOrigin`), `jaw`, `veil_0..5` and `cape`/`cape_tip`. Each side keeps
  `shoulder`, `arm`, `forearm`, `hand` and `weapon`.
- **Clips:** idle, walk, hover, cast_*, attack_*, stun, curse_*, reveal and reform. Their order
  and lengths match `ArkhaiaBedrockSupport.riverClips` and `RiverVisual.frame`.
- **Aura clip:** `aura` loops on ModelEngine's aura lane and in the Bedrock `animate` list. It
  is not part of the indexed clip table.

## Regenerate

```
python evergarden/tools/banshee_mourning.py          # blueprint, manifest, art/banshee-model atlas
python tools/update_river_bedrock.py arkhaia_banshee_wail arkhaia_malefeasance_phase2 --version <next>
python tests/model-engine/check_banshee_wail.py      # contract, clearance, choreography, Bedrock pack
python tools/preview_banshee_wail.py                 # previews/banshee-mourning/
```

`update_river_bedrock.py` re-exports river entities into `evergarden/dist/evergarden-bedrock.mcpack`
at the scale `RiverVisual` renders them. It then bumps the Bedrock content version and updates
`pack-hashes.json` and `judge-design.json`.

Any release pipeline that rebuilds the pack from an older snapshot must re-run this script.
Otherwise Bedrock players keep the previous Banshee, and the Malefeasance phase-2 entity
disappears.
