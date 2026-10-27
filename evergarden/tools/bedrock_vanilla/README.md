Mojang's Bedrock bow / crossbow files, copied unchanged from Mojang/bedrock-samples 1.26.50.4
(commit 46ba6ea, 2026-09-16), `//` comments stripped. `adventurer_weapon_assets.py` builds the
Thornwhisper / Runelock attachables from them, and `tests/weapon-remodel/check_weapon_hold.py`
compares the shipped pack against them.

- `bow.geo.json`, `crossbow.geo.json`, `bow.animation.json`, `crossbow.animation.json`: held geometry and animations
- `attachable_*.json`, `*.render_controllers.json`: the vanilla attachables, for reference and checks
- `player.animation_controllers.json`: only `controller.animation.player.root` and `.crossbow`, which the
  pack re-ships with the bow/crossbow item-name tests widened (`ranged_player_controllers`)
- `textures/`: the vanilla 16 px sprites our sprites are registered against

When Mojang changes the player controllers, refresh these files and rebuild; the exporter stops if the
conditions it widens are no longer there exactly as expected.
