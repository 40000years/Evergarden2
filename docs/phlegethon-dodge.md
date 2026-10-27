# Phlegethon boulder dodge window - e2.90

Reduces excessive throw accuracy while retaining the impact upgrade's damage,
projectile speed, splash, slam, kick and adaptation.

Aim now freezes ten ticks (0.5 seconds) before the boulder leaves the hands:
the last six windup ticks and all four active ticks retain the committed mark.
Predictive lead drops to 35% of estimated travel time, capped at 2.5 blocks
instead of eight. The released projectile remains ballistic without homing.

The real Paper fixture checks the frozen mark throughout the final windup and
active frames, reduced lead, unchanged travel speed and a player walking sideways
at 0.25 blocks per tick escaping both rock and splash without teleporting away.
Existing Phlegethon damage, wave, nausea, cover, armor, adaptation and cleanup
checks run in the same suite. Movement is stepped by the isolated fixture.

Report: `output/phlegethon-dodge-paper-final-20261015/arkhaia-result.txt`.
Binary and SHA-256: `output/phlegethon-dodge-final-20261015/release-validation.json`.
Delivery: `dist/evergarden.jar`, `3.0.0-e2.90-phlegethon-dodge`. Restart after replacing
the JAR; the resource pack is unchanged.
