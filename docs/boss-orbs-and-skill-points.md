# Sanctuary trophies and exploration skill points

Release: Evergarden `3.0.0-e2.101-boss-rewards`, Advance Magic `1.20.0-boss-exploration-points`, Bedrock pack `3.38.0`.

## Six sanctuary bosses

Each final victory drops **one shared physical Soul Orb** at the arena center. Each contributing player who is still present in Survival or Adventure receives **one Advance Magic Skill Tree point** directly. Inventory space does not affect the point reward. Damage to Acheron echoes and destruction of Banshee Soul Anchors count as contributions to their parent encounter.

| Boss | Orb palette | Core motif |
| --- | --- | --- |
| Acheron | Crimson red | Three-bladed vortex |
| Phlegethon | Molten orange | Furnace flame |
| Cocytus | Glacial cyan | Frost star |
| Banshee Wail | Spectral mint | Soul wisp |
| Malefeasance | Amethyst purple | Dimensional eye |
| Styx | Radiant gold | Crescent axe |

The orbs are collectible trophies. Points are awarded at victory; picking up, moving or trading a trophy does not award additional points. Malefeasance's first death grants nothing: its revived 90 HP form must also be defeated. Acheron echoes, previews, ordinary mobs, Banshee anchors, and Arkhaia's repeatable river summons do not independently drop trophies or award points.

## Respawn

After the final defeat, the sanctuary stays empty for **24,000 game ticks**, approximately 20 minutes at 20 TPS. A fresh passive boss returns after that time. Waiting does not keep arena chunks loaded. Cooldowns use the world's persistent monotonic game clock and the `game-cooldowns` entries in `plugins/Evergarden/sanctuary-bosses.yml`; server downtime and `/time set` do not advance this clock. Restarting the plugin or unloading and reloading the area cannot skip the delay.

Existing 60-second wall-clock entries are migrated once, estimating time since the old defeat and preserving the remaining portion of the new day. Abandoning an unfinished encounter still resets the fight after the existing five-second grace period and grants no rewards.

## Skill Tree sources

- Sanctuary bosses: one direct point per present contributor.
- Existing world-boss victory rewards: one queued point per contributor, delivered through the existing claim flow. Arkhaia's actual final defeat also awards one point to present contributors.
- Native Minecraft bosses (Ender Dragon, Wither and similar): **no points**. They can be respawned by players, so awarding points for them made the tree farmable.
- Structure treasure: one point to the first Survival or Adventure player to successfully open a supported chest. This is a shared claim, not one claim per player.

Vanilla structure loot tables under `chests/` and Evergarden exploration, sky landmark, boss-structure and sanctuary treasure are supported. Existing custom chests with durable scroll/staff provenance also qualify once after upgrading; pre-upgrade open history is unavailable, so their first successful open after this upgrade consumes that claim. Normal player-made containers grant nothing. A double chest grants one point for the combined inventory. Chunk coordinate claims persist after chest removal; opening again, replacing the chest, or copying its block NBT cannot reset a claimed coordinate. Cancelled opens do not consume the reward. Older custom treasure without trustworthy generation markers is not blindly backfilled.

Ordinary mob kills and scroll discovery no longer grant Arcane XP or points. The codex still records discoveries. Existing Arcane XP, unlocked ranks, unused legacy points, the initial three points, and paid respec behavior are preserved. New rewards are recorded separately in player PDC `advance-magic:tree_earned_points` and can be spent immediately.

## Assets and integration

The six originals were generated with the built-in `image_gen` tool, with transparent backgrounds. Original images and exact prompts are in `evergarden/art/boss-orbs/originals/` and `generation-prompts.json`. Game export uses 256 × 256 RGBA textures; the originals remain intact. `preview.html` shows the complete collection.

Java items use `voidscape:boss_orb_<boss>` item models with custom-model-data fallback routes. Geyser uses direct item-model mappings and the same artwork in the Bedrock item atlas. `tools/update_boss_orbs.py` updates the existing archives without regenerating unrelated boss models. `evergarden/tools/boss_orb_assets.py` also registers them during a complete pack build.

Install both updated JARs together and allow clients to download the updated pack. Local validation reports are supplied with the release; no running production server was modified and no connected-client playtest was performed.
