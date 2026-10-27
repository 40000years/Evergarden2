# Styx: blood warrior

Release `3.0.0-e2.100-styx-bloodwarrior`, resource-pack version `3.37.0`.
Styx starts at **600 HP** with **900 maximum HP**, synchronized between its native
receiver, encounter health and boss bar. Sanctuary, Arkhaia and combat previews
use the same rules.

## Incoming damage and healing

Direct player melee and sweeping attacks are rejected regardless of the held
item, including fists, swords, axes, tools and a held trident. Rejected melee
does not consume the accepted-hit cooldown or feed Berserker. Thrown projectiles
and actual Advance Magic damage remain valid counters. Advance Magic's damage
context is checked before the ordinary melee-body filter.

The first self-buff grants **ten seconds of blood drain** after a 24-tick ritual.
During that time, only accepted Styx attacks heal it: 50% of the actual heart HP
the victim loses. Damage from other sources does not heal Styx. Healing stops at
900 HP and expires after 200 ticks. Normal cooldown to this buff is 360 ticks
from activation; its first opportunity is 40 ticks into the encounter.

The second self-buff grants **five seconds of Berserker** after its own ritual.
During those 100 ticks, accepted incoming damage is converted directly into HP
gain, up to 900, rather than subtracted first. Thus a 100-damage accepted hit at
600 HP raises the boss to 700. Repeated or otherwise-lethal accepted hits also
convert while the buff is active. After expiration, damage works normally.
The first opportunity is at 240 ticks; repeat cooldown is 440 ticks from activation.
Active attacks finish before the AI chooses a due buff.

When accepted damage first drops Styx below **450 HP**, half of its 900 maximum,
it permanently gains **30% attack damage** for that encounter. Exactly 450 does
not trigger it. Healing above the threshold neither removes nor stacks enrage.
A fresh encounter restores the initial state.

## Outgoing damage, armor and shields

The existing warned opposite sweeps, forward strike and collision-stopped dash
remain. Their base damages are 32/32/40 and 38 respectively. Enrage multiplies
these by 1.3. Attacks use a Styx-specific native impact context that removes
armor, Protection, Resistance, shield and absorption reductions from the hit:
the intended amount reaches actual heart HP. Absorption is not consumed instead.
Ordinary lethal attacks still allow a native Totem rescue.

An accepted hit charges all four armor slots at **eight times** the old impact
wear: `8 * max(1, floor(normal_attack_damage / 4))`. A base 32-damage hit therefore
wears each piece by 64 durability. Native durability/enchantment and item-damage
event behavior remain in effect for armor. Cancellation cannot charge wear.

A shield actively raised in either hand breaks immediately on an accepted hit,
including shields with Unbreaking. A merely carried shield is preserved. Shield
breaks stop item use and emit the normal break event and sound. Lethal wear and
shield removal occur before native death drops are copied.

## Fatal ground chop

The ground chop retains its committed 18-block line, 44-tick warning, terrain
visibility checks and recovery. A player caught in the line takes a guaranteed
lethal native impact that bypasses armor, Protection, Resistance, absorption and
Totem rescue. Moving out of the marked line or using solid cover avoids contact.
Creative/invulnerable spectators remain excluded by the encounter rules.

If blood drain is active, this execution heals only up to half of one ordinary
32-damage attack: **16 HP**, or **20.8 HP** after enrage. It cannot drain the
victim's entire remaining health. Explicitly cancelled damage grants no healing,
wear or shield break.

## Presentation and cleanup

Six articulated blood/berserk ritual clips are appended to the existing 22
clips. Both hands retain the axe grip. Java and Bedrock share all **28 indexes**.
Blood has a red particle helix, Berserker a cyan helix and a counter-rotating seal;
the boss bar names active buffs and enrage. Reset, departure of all eligible
players, defeat and shutdown close timed buffs and owned displays.

`tools/update_styx_buffs.py` authors the clips and patches Styx's Bedrock rig.
The standard pack builder uses content version `3.37.0`. Geometry previews are
under `previews/styx-bloodwarrior`; these are not connected-client screenshots.

## Validation and delivery

`tests/arkhaia/run.py --styx` checks native heart damage against Protection XX,
Resistance V and absorption; both raised shield hands; cancellation; armor wear;
ordinary Totem rescue; melee immunity and magic/projectile counters; both timed
buffs; cap, threshold and enrage behavior; and the fatal chop against 1,000 HP,
500 absorption and a Totem, including its capped drain.

The official Model Engine fixture imports all 28 clips and plays every new buff
state. Intermediate body-clearance and two-hand grip checks cover the new clips.
Independent Bedrock decoding checks geometry and animation pose parity. Separate
armor-helper, Cocytus and Malefeasance fixtures exercise shared-code regressions.

Binary, reports, source snapshot and the previous JAR backup are under
`output/styx-release-20261021`. Replace `dist/evergarden.jar`, restart and accept
the refreshed pack. No live server was modified. Connected-player testing is
still needed to judge final balance and visual feel.
