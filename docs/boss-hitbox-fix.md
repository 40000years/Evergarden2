# Boss hitbox fix — e2.121

Release `3.0.0-e2.121-boss-hitbox-fix` corrects melee selections that missed the
visible body. Install `dist/evergarden.jar` and restart the Minecraft server.
No resource-pack update is required.

## Corrections

- Malefeasance's body collision uses its actual scale of two, including the
  transformed second-phase body. Acheron echoes retain their already scaled
  geometry at renderer scale four.
- Banshee's gown and sleeves, lower tabards, jaws and collars count as body
  targets. Body bounds have a 0.25-block aiming tolerance. Weapons, wings,
  halos, rear capes and decorative effects remain outside these bounds.
- Body poses accumulate animation playback time when movement speed changes.
  Arkhaia's moving body uses its rendered locomotion clip. Cocytus's Bedrock
  walk loop uses the existing model's 1.2-second duration.
- Fallback melee selects the body when the player swings. The native attack
  is dispatched the following tick using that confirmed selection, so a turn
  or a limb's next pose does not discard an already valid contact.
- Direct and fallback melee share the same reach tolerance. A hit is eligible
  on the exact tick its cooldown expires. Walls, arena membership, real weapon
  damage, charge, enchantments, cancelled events and boss immunities still apply.
  A native hit and its fallback cannot count as two accepted strikes.

## Build and verification

`python tools/build_boss_hitbox_release.py` compiles only the five affected class
families against a saved copy of the current release. It preserves every other
JAR entry except the plugin version. The previous release is saved at
`output/boss-hitbox-release-20261023/evergarden-before.jar`.

The build manifest records source and artifact hashes and verifies all 78
embedded model/resource-pack entries byte for byte against that saved release.
No model geometry, texture, animation asset or external resource pack is edited.

The delivered JAR passed **221 Paper checks**: 80 focused hitbox checks and
141 sanctuary checks. The shared combat-logic suite passed **67,143 cases**.
Reports, tested artifact hash and model-preservation evidence are recorded in
`output/boss-hitbox-release-20261023/validation.json`.

The focused Paper regression is available through `tests/arkhaia/run.py --hitbox
--direct-launch`. It checks actual native damage events, body rotations, both
Malefeasance phases, Banshee's lower gown, changing animation rates, range,
occlusion, cooldown boundaries and deferred swing selection. Sanctuary lifecycle
regressions use `--sanctuary`. Test worlds and servers are isolated under `output`;
the work does not restart or modify a live server.

Automated server checks do not replace a connected Java/Bedrock playtest for
visual alignment under real network latency.
