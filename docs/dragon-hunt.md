# Dragon Nest hunt (e2.189-dragon-hunt, e2.190-dragon-pack-speed)

The three Dragon Nest dragons are the only Evergarden bosses that leave home. This page covers how they chase,
fight and give up, how they look on both clients, and how the release is checked.

| Dragon | Element | Rig |
|---|---|---|
| VHAROK · มังกรเพลิงโลกันตร์ | fire | `forge_dragon_fire` |
| VAELTHYR · มังกรอัสนีเวหา | lightning | `forge_dragon_storm` |
| GLACERYN · มังกรเหมันต์นิรันดร์ | frost | `forge_dragon_ice` |

Code: `evergarden/.../boss/DragonNestBosses.java` (hunt, moves, spells) and `boss/DragonFx.java` (effects for both clients).

## Health
Every spawn rolls between `health-min` and `health-max`, in steps of 10. The defaults are **13,000** and **15,000**.
Each element's section can override the pair. Party scaling is off by default (`party-health-per-player: 0`), so the
dragon's health always stays inside the rolled range.

## The hunt
- **Waking.** A resting dragon wakes when a player comes within `aggro-radius` (56) of the nest, or when someone hits it.
- **Who it hunts.** Every player within `hunt.chase-radius` (**300**) blocks of the dragon itself, as long as that player is
  within `hunt.max-roam` (**1000**) blocks of the nest. The dragon never flies past 1000 blocks from its nest.
- **Pursuit.** It sprints at `chase-speed` (1.6 blocks/tick) after anyone more than 48 blocks away, climbing over terrain as
  it goes. An elytra player can only out-fly it by boosting continuously. Within about 16 blocks it hovers at bite range
  and circles. Inside the nest basin it lands and fights on foot; anywhere else it stays airborne.
- **Escaping.** A player escapes by getting more than 300 blocks from the dragon, or more than 1000 blocks from the nest.
  Teleporting counts.
- **Giving up.** When nobody is left to hunt, the dragon flies home slowly (`return-speed` 0.6) and cannot be damaged on the way.
  It lands and is restored with a fresh health roll. If nobody is near enough to watch the flight, it is restored at once.
- **Notices.** A hunted player who leaves the nest is told the escape rules once. After that, an action bar shows their distance
  from the dragon (out of 300) and from the nest (out of 1000).

## Phases
- **Phase 1:** above 60% health.
- **Phase 2:** at 60%. It opens with a Nova, attacks faster, and casts its spell more often.
- **Phase 3, enraged:** at 25%. It opens with another Nova, deals 15% more damage, has the shortest gaps between moves,
  shows a heavier element aura, and its spells grow stronger.

## Moves
Each attack clip was authored to its contact frames (`evergarden/tools/dragon_attack_clips.py`). Hits are tested against
the dragon's real bone positions (`BossHitbox.bonePoints`), so Java, Bedrock and the server agree on where the jaws,
claws and breath are.

| Move | When | Tell | Effect |
|---|---|---|---|
| Bite (`bite`) | ground or hover, target 9-26 ahead | growl, a shrinking ring where the jaws will land | Lunges, then the jaws snap 18.4 blocks ahead at 0.70 s. Physical 16, Wither II 3 s (bleed), element status. |
| Claw rake (`claw`) | target ≤ 19 ahead | two sets of three rake lines on the ground | Right rake at 0.62 s, left at 0.92 s; each physical 12, Weakness II, swept aside; the lines stay lit as burning/shocking/freezing scars. |
| Tail whip (`tail_whip`) | target behind, or a crowd | fan behind the dragon | Physical 12 at 0.55 s with heavy knockback. |
| Wing gust (`wing_flap`) | target ≤ 26 ahead | fan in front | Two gusts (magic 6), strong knockback, element status. |
| Breath (`breath`) | 8-40 ahead | inhale, fan over the ground being swept | Sweeps left to right for 1.55 s; magic 3.4 every 2 ticks; leaves burning/static/frozen ground. Storm breath also calls lightning. |
| Strafe (air) | 10-60 | a lane marked on the ground | Climbs, flies over the lane breathing downward along it. |
| Swoop (air) | 14-80 | ring under the target | Rears up, dives with a snap at the bottom; physical 15 and a launch; ground shockwave when it dives low. |
| Barrage | ≥ 18 (air), > 26 (ground) | charging glow at the mouth | Four volleys of homing element bolts (five per volley when enraged), magic 8 each; in the air it keeps chasing while it fires. |
| Spell (`cast`) | own cooldown 20 / 15 / 11 s, target < 90 | title, rune circle and pillars | See below. |
| Nova (`roar`) | phase 2 and 3 | ground ring at 34 | Shock ring out to 36 (magic 10; **jump over it**) plus element bursts. |

Physical hits are armor-respecting mob attacks. Magic skips armor. As with the realm bosses, part of every hit lands as a
separate sonic **rend** that ignores armor and Protection (`rend-percent`), so late-game enchantments cannot trivialise
the dragons. `damage-percent` scales everything.

### Element spells
- **Fire, ฝนดาวตกโลกันตร์ (meteor rain).** Three meteors per hunter (four when enraged): one on them, two nearby. Four more fall around
  the dragon. Each warns with a fire circle, falls as a 6-block falling-block meteor with a fire trail, explodes for magic 14
  over a 4.5-block radius, and leaves a lava pool. When enraged it adds a giant meteor (radius 9, magic 22).
- **Lightning, อัสนีพิพากษา (judgment lightning).** A sigil follows each hunter, locks, then calls down a double lightning strike
  (magic 13). The bolt chains to the nearest other hunter within 12 blocks, up to three jumps (magic 7 each), so hunters
  should spread out. Six more sigils strike around the dragon. When enraged it casts a second wave.
- **Frost, ศูนย์องศาสัมบูรณ์ (absolute zero).** Lines of ice spikes run toward every hunter (magic 11, launch and freeze). A ring of
  spikes rises around the dragon and a freezing ground wave (magic 6) expands outward; jump over it. When enraged it adds
  frost comets.

## Two clients
- **Java.** The ModelEngine rig comes from the merged `realm_bosses` blueprint (`forge_dragon_<kind>_<clip>`, still one
  ModelEngine slot).
- **Bedrock.** A native entity per dragon in Evergarden Bedrock pack **3.70.0**. The dragons were invisible on Bedrock before
  this release. Each has twelve clips indexed by `RealmBosses.DRAGON_CLIPS` and a 2048 px, 256-colour atlas (about 0.4 MB,
  down from 10 MB). The pack went from 16.4 MB to 19.2 MB. Version 3.69.0 is skipped because the abandoned 50 MB build used it.
- **Effects (`DragonFx`).** Every effect is drawn once for both clients. Java gets tinted dust plus the full vanilla mix.
  Bedrock gets only particles Geyser renders natively, with a stand-in of the same element wherever Java uses tinted dust
  (Geyser drops the tint), sweep, or large smoke:
  - fire: flame;
  - lightning: end rod or spark;
  - frost: blue flame or snowflake.

  Block particles are never sent, because Geyser replays them as block-break floods. Meteors and ice spikes are
  falling-block props, which both clients render. They never fall, drop or place a block, and any landing is cancelled.
  Lightning uses `strikeLightningEffect`, which is harmless and visible to both.

## Admin
- `/evergarden boss dragon fire|storm|ice` spawns a test dragon that treats a spot 30 blocks ahead as its nest. Test dragons
  have no respawn cooldown and give no rewards.
- `/evergarden boss dragon clear` removes the test dragons.
- `/evergarden boss dragon` (status) lists every loaded dragon: state, move, health, phase and distance from the nest.

## Config (`dragon-bosses`)
```yaml
aggro-radius: 56.0
hunt: {chase-radius: 300.0, max-roam: 1000.0, fly-speed: 1.05, chase-speed: 1.6, walk-speed: 0.42, return-speed: 0.6}
health-min: 13000.0
health-max: 15000.0
party-health-per-player: 0.0
damage-percent: 100.0
rend-percent: 100.0
respawn-seconds: 1800
```
The old `leash-radius`, `fly-speed`, `return-speed`, `health-per-extra-player` and per-element `health` keys are no longer
read. The movement keys moved under `hunt:` so that old values in existing server configs (such as `return-speed: 1.25`)
cannot override the new hunt.

## Gates
- `python tests/model-engine/check_dragon_hunt.py`: **451** checks. It covers the clip order, the merged blueprint matching
  each rig, contact frames matching the server timings, no ground clipping, the Bedrock entities, and the pack version.
- `python tests/arkhaia/run.py --template output/arkhaia-aggro-feedback-me-v4-20261014 --direct-launch --classpath-file
  output/cocytus-balance-20261025/build/classpath.txt --garden-jar dist/evergarden.jar --server output/<fresh> --dragon-hunt`:
  `DragonHuntProbe`, **144** checks on real Paper. It covers:
  - health rolls;
  - all ten moves for all three elements, with the damaging ones landing;
  - props appear, then vanish, and never place a block;
  - the chase at 250 blocks, the drop beyond 300, and the 1000 ceiling;
  - the untouchable, slow flight home;
  - the phase 2 Nova, and cleanup after defeat.
- `tests/model-engine/check_banshee_wail.py` still passes, including the judge-design bedrock-version.
  `check_realm_bosses.py` already failed on the 3.68.0 baseline (the brightened crystal moth atlas), so it is not a gate here.

## e2.190: the frozen Java rig and the slow pack loading
- **Frozen Java rig.** A dragon in flight sat in its bind pose and did not turn. ModelEngine's
  `Skip-Models-Behind-Viewer` (MOVEMENT_ONLY) was judging the dragon by its anchor, which sits under the hind legs,
  about 20 blocks behind the head. Whenever a hunter faced the head, ModelEngine stopped sending the rig's animation
  and rotation. `ModelEngineArkhaia` now turns off back-culling and vertical-culling for the three dragons
  (blocked-culling was already off).
- **Slow pack loading.** After the dragons joined the merged blueprint, ModelEngine's rebuild on every start grew from
  about 8 s to about 45 s. A player joining in that window waited up to 30 s for the Judge pack, got the other packs
  alone, then got a second download when ModelEngine finished. Two fixes:
  - **Smaller textures.** `tools/shrink_dragon_textures.py` gives the Java rigs the Bedrock pack's 2048 px palette
    atlases. ModelEngine's pack went from 47.5 MB to 14.8 MB, the plugin JAR from 131 MB to 74 MB, and Java clients no
    longer stitch three 4096 px textures into the block atlas.
  - **Judge cache.** `JudgePackService` keeps the last Judge pack ModelEngine built (`resource-packs/judge-modelengine.zip`),
    with a key over the bundled Judge, the ModelEngine version, its config and every blueprint. On the next start the
    pack is served at once. ModelEngine's rebuild then produces the same hash, so nothing is re-sent. If anything
    changed, the old flow applies.
- **Still slow.** About 19,500 of the roughly 22,300 generated model files come from the dragons: one per unique cube
  rotation per bone. Rounding the rotations to 22.5 degrees only cuts that by a third, so the rebuild still takes about
  40 s. The cache hides it from players after the first start.
- **Verified.** On a trial server that was restarted twice, the pack was restored and the rebuilt hash matched it.
  Only the very first build on a fresh ModelEngine folder differs.
- **Gate.** `python tools/try_model_engine.py --source-server C:/Users/User/Desktop/Serva --plugin
  output/model-engine-trial-20261014/ModelEngine-4.1.1.jar --test-directory output/<fresh> --garden-jar dist/evergarden.jar
  --dragon-hunt` runs `DragonModelEngineProbe`, **91** checks. It covers the culling switches, the fly loop while
  chasing prey in the air, and bite, claw, breath and cast in ModelEngine with matching Bedrock clips.

Server checks prove the logic and the packets, not the look. The animations and effects still need checking in game on a
Java client and a Bedrock client.
