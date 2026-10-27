> **e2.131:** Arkhaia's combat was redesigned (posture duel, river ritual, execution duel, 7000–10000 scaled HP). See [evergarden-endgame-bosses.md](evergarden-endgame-bosses.md); the combat values below are historical.

Current visual release: [e2.71 native Six Rivers models and Admin GUI](arkhaia-river-models.md). Combat values below remain the e2.70 baseline.

# Arkhaia world boss - e2.70 / Six Rivers

Release: `3.0.0-e2.70-arkhaia-six-rivers`. Supersedes e2.69 damage immunities.
HP remains 7,000; phase II starts at 3,500. A hit cannot skip the phase boundary.
Right-click the central red glazed terracotta holding a netherite block to summon.

## Damage received

Every player damage type, including arrows and non-wand magic, now works in both phases. Environmental damage is ignored. The attacker/shooter must currently be inside the arena, alive, and in Survival or Adventure. Being able to see/shoot into the room from outside grants no damage, including spell side effects on minions.

Multipliers use the incoming damage after the normal event modifiers. Rear is a 120-degree cone behind the body, based on the player's position; head rotation and projectile impact direction do not change it. Melee must be within 7 blocks, within 6 vertically, and have line of sight.

| Hit type (base 100) | Phase I | Phase II |
|---|---:|---:|
| General player attack | 50 | 150 |
| Advance Magic from behind | 300 | 150 |
| Melee from behind (sword, axe, unarmed) | 50 | 900 |

Rear bonus replaces the 50% reduction: +200% means x3 total. Phase II additionally multiplies received damage by three. An alive main spirit multiplies phase-I damage by 0.8; any spirit death multiplies damage by 1.5 for ten seconds, refreshing without stacking. These two modifiers can overlap if another spirit survives. Example: a normal phase-I hit of 100 becomes 40 with spirits, or 60 with both a survivor and the death vulnerability; a rear wand hit becomes 240 or 360. With no survivors during vulnerability they become 75 and 450.

## Phase I - river spirits

One active main spirit slot per eligible player, capped at six. Replacement slots wait ten seconds after a spirit death. The roster rotates Acheron -> Phlegethon -> Cocytus -> Banshee Wail -> Malefeasance -> Styx -> repeats, skipping any type still alive. Survivors remain; no living type is duplicated. Empty arenas do not create new spirits.

Each main spirit has 800 encounter HP, a named boss health bar, native V2 Java/Bedrock models, and Evergarden unique/LevelledMobs exclusion tags set before spawn listeners run. They cannot pick up equipment or drop equipment/XP. No extra client assets are needed for the spirits.

| Spirit | Behavior |
|---|---|
| Acheron (wither skeleton) | Close attacks and ritual apply Bleed: 1 raw damage each second for five seconds. |
| Phlegethon (husk) | Warned ghostflame circle: 18% maximum HP initial raw damage, then 4% per second for four seconds. Blue soul fire visuals; DOT stops on leaving the room. |
| Cocytus (stray) | Warned AOE: 6 raw damage, Slowness III for four seconds, immediately reduces current velocity. |
| Banshee Wail (drowned) | Scream AOE and four-second reflect stance, marked by a crown and REFLECT health-bar label. Returns 30% of a hit, capped at 25% player maximum HP per reflection, at most once per ten ticks per player. Reflected hits cannot recurse. |
| Malefeasance (skeleton) | Summons two Sentient Darkness vexes; at most four per encounter, 60 HP each, twenty-second lifetime, bounded attacks. These lesser shades do not grant the main spirit's vulnerability or shield. |
| Styx (wither skeleton) | 2.5-second warned radius-9 heavy AOE, 18 raw damage; Weakness II and 50% armor reduction for six seconds. Self-stuns three seconds after release. |

All other main casts warn for 1.6 seconds. Purple textured circles reuse the world-boss pack on both Java and Bedrock. Damage runs through ordinary events/armor/protection/Totems. Status effects apply only after actual health/absorption damage. Bleed and ghostflame refresh rather than stack. Temporary armor modifiers are transient and removed on expiry, leaving arena, disconnect, phase transition, shutdown, or fight cleanup.

Spirits/shades outside radius 34, more than 7 blocks above/below the floor, or in another world return to the center. No target outside the arena is accepted. Lost targets cancel pending casts. Missing minion entities are restored with their existing virtual HP, without awarding a death bonus.

## Phase II - execution magic

All spirits, lesser shades, DOTs and armor modifiers are removed on transition. No replacement summons occur. The boss still mostly casts, occasionally advances between spells and rarely strikes after several magic attacks. Sword and ordinary spell damage are three times phase-I values; successful sword hits apply Nausea for five seconds and Slowness II for three seconds.

Styx Eclipse uses the existing grand violet gate, sky crown and four finite marked lanes. It warns for 3.5 seconds before release, with a lethal warning message. Each lane releases once on its scheduled tick; gaps between lanes and outside their finite length are safe. In phase II raw damage is max(100, player maximum HP x20), sufficient to kill ordinary geared players. It uses normal INDIRECT_MAGIC damage, not setHealth/kill or a Totem-bypassing damage type. Totems and event cancellation still work. Extremely high defenses can still mitigate it.

The body tracks only during the first six preparation ticks; release, recovery, stun and phase-transition poses remain locked. Rear openings and native Java/Bedrock animation direction fixes are retained. The body/gaze models and resource pack are unchanged from e2.69 (Bedrock pack 3.25.0).

## Encounter lifetime and projectiles

The arena and its immediate boundary own reference-counted plugin chunk tickets until fight cleanup. A missing visual or slime hitbox restores the three body parts and renderer with the original HP, phase and combat state. Removal cause and recovery are logged. The former silent whole-fight removal on any invalid part has been removed. Empty arenas now have a five-minute grace period for death/respawn/reconnect, with presence checked before expiry and an explicit cleanup log.

The reported previous disappearance has no exception in the supplied server log; invalid-part cleanup or absence expiry were silent in e2.69, so the historical cause cannot be proven from that log alone.

Paper's LivingEntity.isPickable() checks the collidable flag. Setting the slime non-collidable made actual arrows pass through it. The receiver now remains collidable/pickable, with player collision exemptions to avoid pushing players. Visual carriers remain non-collidable. Tests include a moving arrow hitting the real receiver, including with Model Engine installed.

## Validation

See `tests/arkhaia` for pure wave/damage/brain/pattern checks and isolated production Paper combat checks, including all six spirit abilities, 800 HP/LevelledMobs tags, shielding/vulnerability timing, outside attacks, containment, recovery, phase cleanup, real arrow physics, temporary armor cleanup, Totem survival and inactivity cleanup. `tests/model-engine/ArkhaiaModelEngineProbe.java` boots official Model Engine Free with Geyser, Floodgate and real LevelledMobs, checks native clips/gaze/facing/carriers, real-arrow damage and 800-HP minions. These are server/API tests, not a connected-client visual/FPS measurement.
