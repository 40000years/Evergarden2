# Banshee soul veil - e2.91

Banshee Wail has 500 native and encounter HP. Its encounter centers on breaking
three Soul Anchors rather than overpowering armor with repeated sword/bow hits.

## Veil and true form

In combat, three full-bright amethyst Soul Anchors spawn around Banshee on solid,
unobstructed arena ground. Each has 30 HP, an aligned native Slime receiver, a
visible crystal, health text, soul particles and a tether back to the boss.
Native receivers are invisible, stationary, marked as world-boss entities before
spawn filtering and excluded from LevelledMobs. They cannot deal touch damage.
Melee checks actual reach, aim and cover. Travelling arrows can strike the receiver.

The veil blocks damage while any anchor survives. Breaking all three exposes the
true form for 240 ticks (12 seconds); swords and arrows work normally then. The
boss uses a reveal animation for the first second. At expiry it reforms three
anchors and its shield with a reformation animation, retaining remaining HP.
The boss bar names the anchor count or remaining exposure time. If three safe
anchor positions cannot be found, Banshee exposes instead of becoming unwinnable.
Invalidated anchor entities are cleaned up and cannot leave an unbreakable shield.

## Soul attacks

The existing travelling sonic attack remains a visible, dodgeable, wall-blocked
projectile. Both it and the curse impact use fixed five-point soul damage (2.5
hearts). The native generic_kill source bypasses armor, enchantments, shields and
Resistance and prevents Totem resurrection. A scoped high-priority event restores
the fixed amount after virtual armor reductions. Explicit event cancellation,
creative/spectator modes and admin invulnerability are respected.

Existing absorption consumes part of the same five-point total; it does not cause
additional damage. Armor pays one point of native durability wear per accepted
impact, including lethal hits before death drops copy equipment. Other bosses
continue using their existing damage types and normal Totem behavior.

## Healing seal

The dedicated curse gesture has a 32-tick windup and a seven-block marked circle.
Its contact deals five soul damage and seals healing for 200 ticks (10 seconds),
with Hunger II. Curse cooldown is 260 ticks (13 seconds) from commitment. The
travelling basic soul attack does not also apply the healing seal.

The seal cancels native health-regain events from instant potions, regeneration,
food, golden apples and custom healing. New Absorption potion effects are blocked,
including golden and enchanted golden apples. A tick cap also rejects direct
plugin increases to health or absorption. Existing absorption is retained until
consumed. Healing and new absorption work again at expiry. The owned Hunger effect
is removed on expiry/departure/owner death/encounter cleanup; an older weaker
Hunger effect is restored for its remaining duration when appropriate.

## Models and verification

The approved sculpture and original sonic animation remain. Five complete clips
are added: curse windup/active/recovery, reveal and reform. Cape and veil motion
trail behind the forward soul release. Banshee has its own 15-entry Bedrock clip
table; other river clip tables are retained. Resource-pack version is 3.33.0.

The real Paper fixture tests native sword/arrow anchor hits, exposure/reformation,
fixed damage with armor/Protection/Resistance, virtual reductions, real healing
potion and both apple consumptions, all health-regain reasons, duration, cleanup,
both-hand Totem bypass and armor wear in death drops. Official ModelEngine 4.1.1,
Geyser and real LevelledMobs validate the new poses, clip indexes and anchor spawn.
Decoded Bedrock poses are compared against the shipped source. Rendered geometry
previews (not gameplay screenshots) are in `previews/banshee-souls`.

Release: `dist/evergarden.jar`, `3.0.0-e2.91-banshee-souls`. Restart after replacing
the JAR and accept resource pack 3.33.0 for the added animations. Validation reports
and hashes are recorded in `output/banshee-souls-release-20261015/release-validation.json`.
