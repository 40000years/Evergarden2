# Boss contact and sword targeting - e2.84

Damage is owned by attack contacts and visible spell volumes. The hidden native
receiver is not allowed to deal unscheduled slime touch or vanilla melee damage.

## Attack behavior

- Arkhaia: light/heavy contact uses the authored sword bone volume at the impact
  frame, swept over the previous tick at 80 Hz. The previous 6.5/7.3-block cone is
  removed. Windup, locomotion and recovery do not deal sword damage.
- Acheron: the overhead daggers are above normal player height. Its ground vortex
  is therefore explicitly visible, moves with the charge, has a 3-block radius,
  and damages each player at most once per cast. There is no attached bleed.
- Phlegethon: one ground impact at the end of the two-hand downswing; 3-block
  warning radius at the fists' landing point. No attached ghostflame damage.
- Styx: one 2.5-block ground impact near the authored axe edge (local x=.8,
  forward=8.4 at scale four), instead of a 9-block target-centered explosion.
  Weakness, temporary sunder and the recovery opening remain.
- Cocytus: normal shots and the frost spell both launch real owned arrows. The
  spell waits four active ticks for the bow release key; the 2.5-block frost burst
  occurs only where that arrow hits. A wall consumes it and blocks splash through
  the wall. A warning mark never damages a player on its own.
- Banshee Wail: a scream circle pulses once at the first active contact key.
  Normal attacks are visible travelling sonic waves, aimed at the committed
  location. Remote damage reflection is removed.
- Malefeasance: the shadow bolt travels along a fixed visible path and stops at
  the first player or solid block. Summoned shades have a 14-tick charging tell,
  a directional close swing, and a 40-tick cooldown; walking near one is harmless.

Ground spells use the player's actual body footprint, a limited height above
that ground, and block occlusion. Projectiles use swept collision each tick;
fast movement cannot jump over a collision sample. Normal native invulnerability,
Totems, cancelled damage and the e2.83 native armor-wear path remain in effect.

## Sword targeting

BossHitbox loads body volumes from the shipped Blockbench bones and the same
animation keyframes. It compiles those keyframes once and uses binary search for
sampling. The volumes rotate with the body and current pose. They are per-bone
oriented bounds with a .12-block aiming tolerance, not per-pixel mesh collision.
Legs, feet, torso, arms, head, neck and lower sash are valid targets. Decorative
wings, seals, held weapons and rear cape are excluded.

A native melee hit must aim at that body within the player's entity interaction
range and without a block in the way. Sweep hits check distance to the body.
Air swings can fill a missing native selection using Player.attack, so real
weapon damage, enchantments, attack charge and damage events are preserved.
The normal damage cooldown prevents a packet hit and fallback from hitting twice.
Right-clicks and block interactions do not trigger fallback attacks.

## Validation

Isolated Paper fixtures use actual players, events, inventory items and moving
native arrows. No live server world is used. The contact suite also disables
player invulnerability frames between logical ticks to expose accidental repeats.

- Contact, dodge, native touch suppression, sword targeting, projectile walls,
  shade tells, cleanup, and native air-swing fallback: 85 Paper checks.
- General encounter lifecycle, phase rules, protection, Totem and attack checks:
  62 Paper checks and 67,143 pure combat/decision checks.
- Armor durability: 38 Paper checks, including all six production impact paths,
  Unbreaking, Eternity, cancellation, Totem, item break and death drops.
- Official ModelEngine 4.1.1 with Geyser/Floodgate/LevelledMobs: 324 checks,
  including native poses, moving spin collision, arrows and cleanup.

The automated fixtures verify server behavior and native bone poses. They do not
replace a human Java/Bedrock client playtest for visual/network latency feel.
