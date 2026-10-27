# Arkhaia Six Rivers - native models

Current combat animation update: [e2.82 — moving spin, ground slam and articulated weapon poses](river-kinematics.md). The [e2.81 notes](river-choreography.md) and the e2.71 description below record earlier integrations.

Release `3.0.0-e2.71-river-models`, Bedrock resource content `3.26.0`.

The six approved V2 Blockbench sculptures are now bundled as managed resources in `modelengine/rivers`. Geometry, textures, posed fingers and weapons match the reviewed models. Only import identifiers, empty texture metadata required by R4.1.1, and the unused hitbox eye-height marker were normalized. The encounter still owns real mob hitboxes and 800 virtual minion HP; Model Engine never resizes these hitboxes.

Java uses Model Engine R4.1.1 active models. Bedrock uses six dedicated Geyser entity definitions with equivalent geometry, textures and animations. One invisible, non-collidable armor-stand carrier follows each real damage receiver through normal relative movement updates. It has zero Bedrock collision bounds and is shown only to Bedrock clients. Damage receivers stay pickable; neither vanilla melee nor arrows depend on hitting the visual carrier. Players without ME availability retain vanilla minions until models become ready.

Body yaw follows movement direction when walking, or faces the target while standing. The inherited head pivot looks toward the player's eyes independently. Casts, melee swings and stuns lock body direction. Idle, walk/hover, cast preparation/release/recovery, attack preparation/contact/recovery and stun select actual skeletal clips. Cast playback rate follows the existing 32/50-tick skill warning. Melee contact occurs twenty ticks after commitment, four ticks into the active swing, with recovery before the next decision. Styx keeps both arm chains together through a shared chest transform, preserving the two-handed axe grip. Banshee uses hover and sculpted veil motion.

Each type has an original compact personal seal (radius 1.1 blocks) and its own colored skill warnings: Acheron crimson, Phlegethon amber with cyan flame center, Cocytus ice blue, Banshee mint, Malefeasance violet, Styx muted gold. Large skill warnings retain their existing damage radius. The seal texture margin is accounted for so the visible outer rim agrees with that radius. Vanilla fallback dust uses the matching personal color.

## Admin GUI

Open `/evergarden test` and select the Six Rivers echo-shard entry (root slot 3). The same entry is also available in World Boss Test, slot 7.

- Top row: choose any harmless model preview.
- Second row: choose a real combat test, 800 HP and its actual skills.
- Preview all six / combat all six spawn a single controlled test group.
- Idle/head tracking, walk in place, cast cycle and stun buttons apply to harmless previews. Banshee keeps hovering instead of showing a foot gait.
- Clear removes only your test group, its native models/carriers, health bars, seals and owned temporary debuffs.

Tests need clear, solid, level ground in front of the admin (12 x 12 blocks for all six). Combat targets Survival/Adventure players within an 18-block test arena; outside attacks are rejected. Harmless models also work for Creative admins and show their health bars. Each new summon replaces that admin's previous group. Groups close on owner logout/world change/leaving 64 blocks, after ten minutes, or when all combat units die. They give no drops/rewards and do not touch the boss cooldown ledger. Summon, clear and pose operations require admin permissions, rechecked after GUI callbacks are scheduled.

Boss phase-I summons use these same visual controllers and retain the rotating six-type roster, HP, defensive modifiers, containment and special skills from e2.70. Phase II clears minions as before. Every death/phase clear/test cleanup unregisters native actors and removes the ME entity. A missing visual carrier is rebuilt after allowing the R4 worker to finish removing the old renderer, preserving the real minion and its HP.

## Validation

The isolated official R4.1.1 + Geyser + Floodgate + LevelledMobs runtime imports all six blueprints, verifies real GUI click routes, individual/all previews and combat, pose controls, per-model Bedrock spawn events and five-property batches, head behavior/body yaw, health bars, unauthorized access, real arrows, cleanup and missing-carrier recovery. Combat regression covers the main boss, six skills, outside protection and Totem survival. Asset checks independently decode all six Bedrock rigs and arbitrary-axis cubes, compare full poses to the Java-authoring geometry, verify head handedness and preserve each transparent seal texture/color. These are server/API and transform checks; they are not a connected-device visual or FPS benchmark.
