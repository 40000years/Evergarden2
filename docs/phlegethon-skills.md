# Phlegethon furnace skills - e2.88

Phlegethon has 1,000 virtual and native HP, including the health bar, spawn
announcement and harmless admin previews. Its three attacks are:

| Skill | Windup | Contact | Raw damage |
| --- | --- | --- | ---: |
| Two-fist ground slam | 44 ticks | Existing authored ground-contact pose | 24 |
| Raised-knee forward kick | 26 ticks | Active animation 0.2 seconds | 20 |
| Two-hand boulder throw | 36 ticks | Releases at active animation 0.2 seconds; damages only on collision | 22 |

Near targets alternate slam, kick and throw; kicks require a target within five
blocks. Targets beyond seven blocks select a throw, up to 28 blocks away. Skill
cooldown is 100 ticks from commitment. Vanilla touch/melee remains suppressed.
Protection enchantments, native immunity frames and armor wear use the shared
production damage helper. These attacks are weaker than Acheron's 36/60 spin.

The kick uses the authored knee-to-foot segment at the contact key, with a small
contact margin and wall visibility check. The 1.8-block deepslate boulder appears
between both hands during windup and follows their authored poses. It flies along
a fixed ballistic trajectory towards the committed player location. A swept
volume checks walls and players, accepts the first contact only, then destroys
the projectile. Deepslate fragments and soul-fire trails remain visible without
custom textures. Held and flying displays clean up with their owner/encounter.

The overhead personal seal is the passive adaptation effect, not a fourth
attack. Each accepted repeated weapon-family hit halves the previous damage:
10, 5, 2.5, 1.25, and so on. A different family resets the sequence immediately;
the first hit with it deals full damage. Returning to the previous family also
starts at full damage. Adaptation is shared across attackers on this boss.
Swords, axes, pickaxes, shovels and hoes group by family regardless of material;
bow, crossbow, trident, fist and other projectile types are distinct. Arrows use
their stored launch weapon, so swapping held items before impact cannot trick
adaptation. Misses, rejected rapid hits and outside-arena attacks cannot change it.
The seal accelerates and shows amber particles after adapting; action-bar feedback
names the adapted family and chain count. A fresh boss starts unadapted.

Only Phlegethon receives six added clips. Its Bedrock clip table has 16 entries;
other models retain their existing tables. The source sculpture, Acheron and
other river attacks are retained. Resource-pack version is 3.32.0.

Validation includes real Paper damage/adaptation/projectile contact, official
ModelEngine 4.1.1 native poses and Geyser clip indexes, decoded Bedrock pose parity,
825,696 limb-clearance samples, and 1,860 held-boulder/protocol checks. Rendered
geometry previews (not in-game captures) are in `previews/phlegethon-skills`.

Release JAR: `dist/evergarden.jar`, `3.0.0-e2.88-phlegethon`. Restart after replacing
the JAR and accept the updated resource pack for the new animations.
