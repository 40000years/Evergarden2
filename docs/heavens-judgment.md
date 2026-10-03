# Heaven's Judgment

Legendary / MYTHIC wand inspired by four horizontal golden magic seals floating
above the target. The seals remain at their original heights (14, 20, 27 and 35
blocks); they rotate in alternating directions and scale together near the world
ceiling. Their diameters are 22, 32, 44 and 56 blocks. The continuous gold-and-ivory
sigils contain eight concentric rings, twenty-four branched glyphs, an interlaced
star and compass diamonds. After a three-second charge, an eight-block-wide yellow-white beam fires
downward for four seconds. A final pulse releases a golden ground ripple.

| Property | Default |
| --- | --- |
| Spell ID | `heavens_judgment` |
| Mana / cooldown | 100 / 45 seconds |
| Beam damage | 85 every 0.5 seconds, eight pulses |
| Closing damage | 80 |
| Full exposure | 760 damage before resistance and upgrades |
| Damage area | Radius 4, from the ground below the aim up to the highest seal |
| Durability | 30 casts; existing repair and upgrade systems apply |

The Java beam uses an enlarged `minecraft:entity/beacon_beam` texture, two gold
and ivory layers, and one full-brightness ItemDisplay. Bedrock receives a tall
golden beacon-style particle ribbon with the same position, width and height.
The beam display is hidden from Bedrock viewers while the particle adapter is
available. Golden glass supplies the Java fallback when the pack is declined.
The visual uses Paper's [display entity API](https://docs.papermc.io/paper/dev/display-entities/).

The cast traces downward from the aimed block, mob feet, or empty-air aim to the
first solid collision surface. Both clients use this exact ground anchor for the
beam, seals and damage column, including slabs and floors beneath roofs. A column
with no ground is refused. Java displays explicitly clear yaw and pitch so aiming
upward or at a flying mob cannot tilt the beam or raise its lower endpoint.

The four horizontal seal displays are full-brightness and visible from both sides.
They open smoothly during charging, rotate in alternating directions and retract
after firing. Bedrock uses the identical 1024px artwork on the emitter's XZ plane,
as documented in Microsoft's [billboard reference](https://learn.microsoft.com/en-us/minecraft/creator/reference/content/particlesreference/particlecomponents/minecraftparticle_appearance_billboard?view=minecraft-bedrock-stable).
Java clients see the authored displays after the pack loads; clients without the
pack receive the particle fallback. Every display belongs to the original cast.

The wand tooltip uses the same mana/cooldown/durability/upgrade lines as other
wands. The extra lines about four seals and an eight-block beam have been removed;
existing wands are migrated automatically when inventories are loaded.

All damage uses the existing enemy/team/PvP rules and cancellable MagicAffectEvent.
The beam and remaining cast stop on death, disconnect, world change, owner cleanup,
or plugin disable. The spell changes no terrain blocks or world weather/time.

## Items and crafting

The wand is an ivory spear-shaped crystal crowned with three gold halos and a
navy grip. The separate Core of Judgment is a compact crystal seal with a gold
frame, navy inlays and three horizontal rings.

Use **one Core of Judgment** and **eight Netherite Ingots or Nether Stars**;
mixing ingots and stars works. Put the core in the center or bottom center:

```text
N N N     N N N
N C N  or N N N
N N N     N C N
```

Both recipes are registered and discovered by the existing crafting system.
The `/magic craft` menu and `/evergarden` magic showcase include the new items.
Evergarden's Mythic Vault category stays at 0.5% and now chooses equally among
four cores, including Judgment (0.125% per Vault reward roll).

Admin commands:

```text
/magic give <player> heavens_judgment
/magic givecore <player> heavens_judgment
```

Install both `dist/advance-magic.jar` and `dist/evergarden.jar`, restart the server,
and reconnect clients to refresh the packs. Advance Magic Bedrock pack is 2.5.0;
Evergarden Bedrock pack is 3.12.0. Customized damage can be set through
`damage.heavens-judgment-pulse` and `damage.heavens-judgment-final`.

## Verification

```text
python tools/judgment_seals.py
python advance-magic/tools/build_packs.py
python evergarden/tools/build_packs.py
mvn -pl advance-magic,evergarden -am package -DskipTests
python advance-magic/tests/check_packs.py
python evergarden/tests/check_packs.py
python evergarden/tests/run_bedrock_items.py --source-server <disposable-paper-template>
```

The isolated Paper/Geyser checks exercise the complete damage timeline, charge
delay, actual beam display, eight-block width, elevated targets, targets outside
the radius, protected targets, cancellation during charge and during firing,
mana/durability/cooldown accounting, both GUIs, the new recipe, all four Mythic
cores and Bedrock beam packet dimensions. These are server-side checks; visual
appearance still needs an in-game client review.

Grounding regressions cover upward and downward air aim, airborne mobs, both
stair treads, slabs, wall faces, ceiling undersides, covered floors, void columns
and reduced height near the build ceiling. Java model bounds and Bedrock UVs are
checked for full vertical coverage; server checks compare both beam endpoints
with the ground and highest seal.
