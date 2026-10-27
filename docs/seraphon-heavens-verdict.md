> **e2.131:** Seraphon's combat was redesigned (sun mirrors, Ophanim choir, Heaven's Gate, 7000–10000 scaled HP, Verdict no longer interruptible). See [evergarden-endgame-bosses.md](evergarden-endgame-bosses.md).

# Seraphon — Heaven's Verdict

Evergarden `3.0.0-e2.122-seraphon-heavens-verdict` adds the six-wing, white-and-gold
Seraphon as the resident guardian of Supreme Sanctuary. Default health is **10,000**.
The approved model, textures, skeleton and animation assets remain unchanged.
The Spawn trader and Valkyrie's structure are unaffected.

## Finding and starting the encounter

Use the existing `/evergarden tp seraphon` command as an administrator, or reach
a naturally generated Supreme Sanctuary. Once its central arena is loaded and
the Seraphon ModelEngine blueprint is ready, one passive guardian appears.
Strike its visible body from inside the arena to begin. The first hit counts;
the guardian then descends through its authored spawn animation and announces
the judgment. The arena extends 54 blocks horizontally from the center.
Airborne players remain in an active fight.

The encounter uses the existing ModelEngine backend for Java and the native
Geyser Seraphon rig for Bedrock. Both clients, body collision, emitter positions
and the particle aura share the same animation clock. Lower robes and limbs
remain valid melee targets. During ordinary movement the guardian stays low;
the major judgment lifts it above the arena and then returns it for recovery.

## Combat

| Skill | Behavior |
| --- | --- |
| Heavenly lances | Six casting gates announce volleys of travelling light projectiles from the orb. The attack commits its aim and stops at the first player or solid block. |
| Judgment feathers | The wing animation releases feather fans from all six authored wing emitters. Feathers that strike nearby ground leave a warned, delayed burst. |
| Solar pillars | Ground and sky seals precede three column patterns around committed target positions. Each column can hit a player once, and roofs block its beam and damage. |
| Sanctuary orbit | Two low beams rotate through the circular arena after a harmless warning. Players can move with the gaps, jump above the beam or use solid cover. |
| Heaven's Verdict | Nested golden seals and tilted armillary rings open above the sanctuary. Three cyan petals mark safe areas among 27 individually warned light columns. Phase three adds a second warned impact. |

At 65% and 30% health, the guardian completes its committed cast and plays the
3.5-second wing transition before switching attack patterns. Phase two adds the
orbit mechanic; phase three uses more frequent judgments and stronger volleys.
The encounter never changes real structure blocks or terrain.

Dealing 4.5% of maximum health during a judgment windup interrupts it into the
authored 1.8-second stun. At the default health this requires 450 damage. Stun
opens a 1.5× damage window; normal cast recovery takes 1.35× damage. Native
armor, shields, invulnerability, cancellation and Totems remain in effect.

If every eligible player leaves for 300 ticks, the guardian returns to waiting
at full health and removes all pending attacks. Victory removes the guardian
for 24,000 world game ticks. Its cooldown survives a restart. Eligible nearby
contributors receive five Skill Tree points when Advance Magic is present,
25 existing Seraph Feathers when the trader service is present, and 2,000 XP.

## Artwork and delivery

The five new spell assets adapt the archived vectors in `tools/judgment_seals.py`:
golden seal, outer orbit, cyan sanctuary seal, light ray and feather. Existing
Java and Bedrock models and resource-pack entries are preserved. Only these
effects, pack digests and the Bedrock version are added or updated. Display
entities are reused, projectiles are capped at 128 per guardian, and warning
columns are capped at 96. High airborne combatants retain the effect audience.

Install `dist/evergarden.jar`, restart the server, and reconnect to download the
updated resource packs. The Bedrock pack version is **3.51.0**. Settings are
`seraphon.enabled` and `seraphon.health` in Evergarden's config, defaulting to
`true` and `10000.0`. This work does not restart a live server.

Build with `python tools/build_seraphon_release.py --output <new-directory>`.
The previous JAR is retained at
`output/seraphon-release-20261023/evergarden-before.jar`. The manifest records
source hashes, added assets, preserved model hashes and the delivered JAR hash.

The delivered artifact passed **272 Paper checks**: 51 for the Seraphon encounter
with official ModelEngine/Geyser, 80 existing hitbox checks, and 141 sanctuary
regressions. It also passed **1,884 asset and safe-petal checks** and the shared
**67,143 combat-logic cases**. All 73 embedded ModelEngine assets match the
previous release byte for byte. Reports and the tested artifact hash are in
`output/seraphon-release-20261023/validation.json`.

An [offline animated effect review](../previews/seraphon-encounter/index.html)
combines the shipped mesh, animation keyframes and actual spell textures. It is
not an in-game screenshot. Server tests verify collision, native animation
selection, phases and cleanup; a connected Java/Bedrock client playtest is still
needed to assess network feel and frame rate.
