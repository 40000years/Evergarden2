# Six sanctuary encounters — e2.93

Adds attack-initiated resident bosses to the six existing Overworld structures:
`acheron`, `phlegethon`, `cocytus`, `banshee`, `malefeasance`, `styx`.
Locate or travel using the existing `/sanctuary locate|tp <name>` commands.

## New combat

All damage below is raw damage through the existing native damage/armor-wear
helper. Acheron, Phlegethon and Banshee retain their accepted encounter mechanics.
The other three retain 800 HP and the approved sculptures.

* **Cocytus:** three separately aimed, committed frost arrows (18 each); three
  advancing ice lanes with gaps (32, once per cast); and a 64-tick blizzard
  preparation that supplies three ice pillars. The forward storm deals 48 once;
  the pillars and solid terrain block it. Pillars shatter after release, opening
  a three-second counterattack window with 1.5x incoming damage. Three cold hits
  cause Slow II for two seconds, clear the stack and grant five-second cold
  immunity. Temporary ice uses displays and never changes terrain.
* **Malefeasance:** two tethered 60-HP shadows each reduce incoming damage by 20%
  (40% maximum). They approach within a bounded radius, mark their short-range
  attacks before dealing 12 damage, and briefly expose their caster when killed.
  Three successive ground curses each lock an old player position and deal 30
  after 24 ticks. A travelling, wall-blocked ranged bolt deals 22. During a
  five-second ritual the shadows return to the caster; each surviving sacrifice
  supplies one separately telegraphed travelling wave (36). Destroying every
  shadow before sacrifice interrupts the ritual and opens five seconds at 1.5x
  damage. Shadows, tethers and warnings belong to their caster.
* **Styx:** preserves the existing two-handed axe. Its combo sweeps in opposite
  directions, then thrusts forward (32/32/40). Each strike has a 20-tick warning
  and locks its facing before contact. A distant target prompts a 28-tick warned
  dash (38); collision with solid terrain stops it and opens three seconds of
  recovery. Its 44-tick execution attacks a committed 18-block line for 60,
  briefly halves armor and applies Weakness. Heavy recovery opens three seconds
  at 1.5x damage. Three accepted hits during recovery break its stance for another
  three seconds; a single opening cannot repeatedly retrigger stagger.

Below 40% HP these bosses shorten pauses between moves while keeping the warning
windows. All three use the same logic in the sanctuary, Arkhaia encounter and
admin combat tests. Styx adds twelve complete animation clips; its Java and
Bedrock tables both contain 22 entries. Resource-pack version is **3.34.0**.

## Waiting bosses and boundaries

Each loaded sanctuary has one boss at its authored arena center (local X 0,
Z 27, above its combat floor; the Banshee deck is four blocks higher). It remains
passive until a valid Survival/Adventure player strikes its evaluated body from
inside the arena. Banshee creates her Soul Anchors when awakened. Passive admin
previews keep their previous behavior.

The visible border has radius 34. Since e2.120, active combat includes the air
above that cylinder, retaining airborne targets and committed attacks during
Wind Charge jumps. Feet below 0.25 blocks under standing height remain excluded.
Idle activation keeps the original upper limit of 2.2 blocks (12 for Cocytus).
Incoming and outgoing active damage use the same combat boundary.
Player projectiles must originate inside that particular arena as well as have
their shooter inside at impact. Native contact, environmental damage, dispenser
arrows and attacks by unrelated mobs cannot damage or activate a boss.

If all players leave, the current return grace lasts fifteen seconds. Returning
before its end resumes the encounter; otherwise the boss resets to full health
and waiting status. Airborne players above the arena do not start this clock. A boss
displaced below its floor or beyond its leash is returned to its arena. Since
e2.101, defeat leaves the arena empty for 24,000 game ticks before returning a
fresh passive sculpture. The persistent cooldown is stored in
`sanctuary-bosses.yml`. See [current rewards and respawn rules](boss-orbs-and-skill-points.md).

Idle sculptures do not force-load chunks. Active encounters hold and release
their arena chunk tickets. Loading a site, retrying discovery, or restarting does
not duplicate its boss. Missing native receivers/models are recovered, and reset,
unload and shutdown close owned children, spells, projectiles and modifiers.

## Habitat and terrain protection

The sanctuary's authored footprint, foundations and headroom now participate in
the shared structure protection used by mining and Advance Magic. It prevents
block breaking/placing, buckets and fluid flow, explosions, piston intrusion,
terrain-changing entities, fire, portals, and placed vehicles/armor stands or
hanging entities. Bosses cannot be mounted or fished into traps and do not collide
with player-pushed vehicles. Existing Creative administrator bypass rules remain.

Ordinary natural mobs and unowned hostile spawn reasons are rejected inside the
protected footprint. The authored cage templates retain their guard marker and
continue through native spawner and LevelledMobs handling. Bosses and their marked
children are explicitly exempt. Outside the footprint, spawning is unchanged.

Habitat migration version 2 removes old unowned ordinary mobs and repairs existing
pits/pillars inside the reserved combat cylinder to shipped geometry (four floor
layers and thirteen blocks of headroom). Buildings, treasure and player edits
outside that combat cylinder are not rewritten. The world layout, chest loot and
cage coordinates are retained. Shutdown avoids mutating the populator list while
Paper decoration workers may still be iterating it.

Configuration: `overworld-sanctuaries.bosses.enabled` defaults to true. The existing
habitat suppression setting remains supported.

The subsequent [Cocytus rework](cocytus-encounter.md) replaces its original
attacks with a 700-HP tempest/prison/rain encounter and extends only its vertical
combat boundary to twelve blocks, retaining elevated and imprisoned targets.
The subsequent [Malefeasance rework](malefeasance-dimensions.md) replaces its
shadow mechanics with a 600-HP, magic-immune dimensional caster, sky gates,
blood-debt healing and one revival at 90 HP.
The subsequent [Styx rework](styx-bloodwarrior.md) starts at 600 HP with a 900 HP
cap, rejects melee, deals true-heart damage, breaks raised shields, adds blood
drain and Berserker, and makes the warned ground chop lethal on contact.
The subsequent [airborne combat fix](airborne-boss-combat.md) keeps elevated
players in all six encounters and adds committed, visibly bounded aerial responses.

## Delivery and verification

Build with `python tools/build_sanctuary_encounters.py`. The resulting JAR includes
the source models and updated resource packs. Release output and verification
reports are recorded under `output/sanctuary-encounters-release-20261016`.

Verification uses isolated Paper worlds, real generated sites, an actual
ModelEngine R4.1.1/Geyser/LevelledMobs stack, decoded Bedrock poses, and articulated
limb/grip checks. These checks establish mechanics and protocol behavior; a
connected-player playtest is still needed to judge final balance and appearance.

Replace `dist/evergarden.jar`, restart the server and accept the refreshed resource
pack. No live server deployment is performed by the build.
