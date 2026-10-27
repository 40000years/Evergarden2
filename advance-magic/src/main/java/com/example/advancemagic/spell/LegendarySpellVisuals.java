package com.example.advancemagic.spell;

import com.example.advancemagic.effect.EffectEngine;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.util.Vector;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Authored full-bright planes and rays, layered in world space instead of a
 * cloud of particles. Both clients receive the same artwork and animation.
 * Clients without a pack see bounded recognizable geometric silhouettes.
 */
final class LegendarySpellVisuals implements AutoCloseable {
    enum Kind { VERDANT, LUNAR, CRIMSON }
    private static final double TAU = Math.PI * 2;
    private static final int MAX_DISPLAYS = 72;
    private static final Color EMERALD = Color.fromRGB(0x58FFB1), JADE = Color.fromRGB(0x12B76A),
            SILVER = Color.fromRGB(0xD8F3FF), CYAN = Color.fromRGB(0x72EDFF),
            CRIMSON = Color.fromRGB(0xFF315B), RUBY = Color.fromRGB(0xA41B52),
            GOLD = Color.fromRGB(0xFFD34D), VIOLET = Color.fromRGB(0xA36BFF);
    private final MagicContext c;
    private final EffectEngine.Effect effect;
    private final MythicVisuals visuals;
    private final Location base;
    private final Kind kind;
    private final double radius, scale;
    private final LegendaryCastGeometry.Scene geometry;
    private final Map<String, MythicLineVisuals> art = new LinkedHashMap<>();
    private int particleBudget;

    LegendarySpellVisuals(MagicContext c, EffectEngine.Effect effect, Location base,
                         Kind kind, double radius, double scale) {
        this(c, effect, base, kind, radius, scale, null);
    }

    LegendarySpellVisuals(MagicContext c, EffectEngine.Effect effect, LegendaryCastGeometry.Scene geometry,
                         Kind kind, double radius) {
        this(c, effect, geometry.base(), kind, radius, geometry.scale(), geometry);
    }

    private LegendarySpellVisuals(MagicContext c, EffectEngine.Effect effect, Location base,
                                 Kind kind, double radius, double scale, LegendaryCastGeometry.Scene geometry) {
        this.c = c;
        this.effect = effect;
        this.visuals = new MythicVisuals(c);
        this.base = base.clone();
        this.kind = kind;
        this.radius = radius;
        this.scale = scale;
        this.geometry = geometry;
    }

    void frame(int age) {
        // Four-tick plane interpolation matches the native Bedrock emitter's
        // short lifetime. Damage runs independently at its exact pulse ticks.
        if (age % 4 != 0) return;
        visuals.frame(base);
        particleBudget = Math.clamp(c.plugin.getConfig().getInt("legendary.particle-budget-per-frame", 220), 32, 400);
        if (age >= (kind == Kind.CRIMSON ? 180 : 152)) {
            hide("");
            return;
        }
        switch (kind) {
            case VERDANT -> verdant(age);
            case LUNAR -> lunar(age);
            case CRIMSON -> crimson(age);
        }
        if (kind == Kind.CRIMSON) {
            if (age < LegendaryCastGeometry.CRIMSON_START) {
                if (visuals.needsSealFallback()) fallback(age);
                accents(age);
            } else {
                accents(age);
                if (visuals.needsSealFallback()) fallback(age);
            }
        } else {
            // Motion must survive when even one viewer has not applied the
            // pack. The fallback uses what remains instead of starving accents.
            boolean fallback = visuals.needsSealFallback();
            int reserve = fallback ? Math.min(128, particleBudget / 2) : 0;
            particleBudget -= reserve;
            directionalAccents(age);
            particleBudget += reserve;
            if (fallback) directionalFallback(age);
        }
    }

    /** One still, readable dragon sigil and one massive horizontal discharge. */
    private void verdant(int age) {
        var dragon = (LegendaryCastGeometry.Verdant) geometry;
        double growth = smooth(age / 24.0), fade = fade(age);
        seal("floor", "verdant_seal", base.clone().add(0, .08, 0), 18 * scale * growth * fade, age * .008);
        seal("floor-outer", "verdant_halo", base.clone().add(0, .16, 0), 26 * scale * growth * fade, -age * .023);
        plane("dragon", "verdant_dragon", dragon.sigil(), dragon.right(), new Vector(0, 1, 0),
                22 * scale * growth * fade, 22 * scale * growth * fade, 0);
        plane("dragon-halo", "verdant_halo", dragon.sigil().subtract(dragon.forward().multiply(.06)),
                dragon.right(), new Vector(0, 1, 0), 26 * scale * growth * fade, 26 * scale * growth * fade, -age * .017);
        if (age >= 24 && age <= 140) {
            var beam = age >= 112 && age < 124 ? dragon.finalBeam() : dragon.beam();
            if (beam != null) {
                double endFade = age < 136 ? 1 : 1 - smooth((age - 136) / 8.0);
                ray("forward-beam", "verdant_ray", beam.from(), beam.to(), beam.width() * endFade);
                // A far light cap stays visible along the firing axis. Leave
                // the near mouth open so it cannot become a screen-sized disc.
                plane("beam-tip", "verdant_beam_core", beam.to(), dragon.right(), new Vector(0, 1, 0),
                        beam.width() * endFade, beam.width() * endFade, 0);
                for (int wave = 0; wave < 3; wave++) {
                    if (beam.length() < 12) { hide("breath-wave-" + wave); continue; }
                    double progress = (age * .028 + wave / 3.0) % 1;
                    Location at = beam.from().add(dragon.forward().multiply(8 + progress * (beam.length() - 8)));
                    double diameter = beam.width() * (1.08 + .15 * Math.sin(progress * Math.PI)) * endFade;
                    plane("breath-wave-" + wave, "verdant_halo", at, dragon.right(), new Vector(0, 1, 0),
                            diameter, diameter, age * .03 + wave);
                }
            } else {
                hide("forward-beam");
                hide("beam-tip");
                hide("breath-wave-");
            }
        } else {
            hide("forward-beam");
            hide("beam-tip");
            hide("breath-wave-");
        }
        var impact = dragon.beam();
        if (impact != null && age >= 112) {
            double progress = smooth((age - 112) / 36.0);
            Location end = impact.to();end.setY(base.getY() + .2);
            for (int layer = 0; layer < 2; layer++)
                seal("dragon-impact-" + layer, "verdant_halo", end.clone().add(0, layer * .14, 0),
                        (5 + progress * 21 - layer * 2) * scale * fade, age * .032 * (layer == 0 ? 1 : -1));
        }
    }

    /** Large separate moon circles discharge needle-thin silver beams. */
    private void lunar(int age) {
        var moons = (LegendaryCastGeometry.Lunar) geometry;
        double growth = smooth(age / 24.0), fade = fade(age);
        var centers = moons.rings();
        var columns = moons.columns();
        Location altar = base.clone().add(moons.right().multiply(moons.spacing() / 2))
                .add(moons.forward().multiply(moons.spacing() / 2)).add(0, .1, 0);
        double altarWidth = moons.spacing() * 3 + moons.ringDiameter();
        seal("moon-altar", "lunar_constellation", altar, altarWidth * growth * fade, age * .008);
        seal("moon-altar-inner", "lunar_constellation", altar.clone().add(0, .13, 0),
                altarWidth * .62 * growth * fade, -age * .018);
        for (int i = 0; i < LegendaryCastGeometry.LUNAR_COUNT; i++) {
            double summon = smooth((age - i % 4 * 3) / 20.0);
            double diameter = moons.ringDiameter() * summon * fade;
            plane("moon-" + i, "lunar_seal", centers.get(i), moons.planeRight(i), moons.planeUp(i),
                    diameter, diameter, 0);
            double halo = diameter * (1.14 + .025 * Math.sin(age * .12 + i));
            // The revolving engraving must not share the hero's depth: their
            // fine strokes cross as it turns and would otherwise flicker.
            Vector normal = moons.planeRight(i).crossProduct(moons.planeUp(i)).normalize();
            Location haloCenter = centers.get(i).clone().subtract(normal.multiply(.06));
            plane("moon-halo-" + i, "lunar_orbit", haloCenter, moons.planeRight(i), moons.planeUp(i),
                    halo, halo, age * .033 * (i % 2 == 0 ? 1 : -1) + i * .4);
            var beam = columns.get(i);
            if (beam == null) {
                hide("column-" + i);
                hide("impact-" + i);
                continue;
            }
            int shotAge = LegendaryCastGeometry.Lunar.shotAge(age, i);
            if (shotAge >= 0) {
                double discharge = shotAge < 4 ? 1 : 1 - smooth((shotAge - 4) / 4.0);
                ray("column-" + i, "lunar_ray", beam.from(), beam.to(), beam.width() * discharge);
                // The shock ripple is decoration; the hit volume remains the
                // narrow shared column rather than this large moving engraving.
                seal("impact-" + i, "lunar_constellation", beam.to().clone().add(0, .07, 0),
                        (3 + shotAge * .85) * scale * fade, -age * .032 + i);
            } else { hide("column-" + i); hide("impact-" + i); }
        }
    }

    private void crimson(int age) {
        double entrance = smooth(age / 24.0), fade = fade(age);
        double beat = age < 40 ? 0 : Math.exp(-Math.pow(((age - 40) % 16) / 4.0, 2));
        seal("floor", "crimson_seal", base.clone().add(0, .06, 0), radius * 2 * entrance * fade, age * .01);
        seal("floor-crown", "crimson_seal", base.clone().add(0, .18, 0), radius * 1.3 * entrance * fade, -age * .023);
        if (age < LegendaryCastGeometry.CRIMSON_START) {
            Location heart = base.clone().add(0, 18 * scale, 0);
            double size = 12 * scale * entrance * (1 + beat * .16);
            crossed("heart", "crimson_heart", heart, size, size, age * .018, 0, 3);
            seal("heart-crown", "crimson_seal", heart.clone().add(0, 4 * scale, 0),
                    size * 1.45, -age * .035);
            plane("heart-orbit", "crimson_seal", heart,
                    new Vector(1, 0, 0), new Vector(0, .72, .694).normalize(), size * 1.35, size * 1.35, age * .021);
            for (int i = 0; i < 8; i++) {
                Location shard = shard(age, i);
                double angle = TAU * i / 8 + age * .012;
                double rise = smooth((age - 20 - i) / 16.0);
                double shardSize = rise;
                crossed("shard-" + i, "crimson_shard", shard, 5 * scale * shardSize,
                        8 * scale * shardSize, angle + Math.PI / 2, 0, 2);
                if (age >= 40) {
                    ray("cage-" + i, "crimson_ray", shard, heart, (.32 + beat * .3) * scale);
                    ray("link-" + i, "crimson_ray", shard, shard(age, (i + 1) % 8), .25 * scale);
                }
            }
        } else {
            hide("heart");
            hide("cage-");
            hide("link-");
            Location center = base.clone().add(0, 2 * scale, 0);
            for (int i = 0; i < 8; i++) {
                int arrival = LegendaryCastGeometry.crimsonArrival(i);
                if (age >= arrival) { hide("shard-" + i + "-"); continue; }
                Location stone = shard(age, i);
                double progress = smooth((age - (arrival - LegendaryCastGeometry.CRIMSON_FLIGHT))
                        / (double) LegendaryCastGeometry.CRIMSON_FLIGHT);
                double angle = TAU * i / 8 + 96 * .012;
                double size = 1 - progress * .55;
                crossed("shard-" + i, "crimson_shard", stone, 5 * scale * size,
                        8 * scale * size, angle + Math.PI / 2, progress * Math.PI / 2, 2);
                if (progress > 0) ray("cage-" + i, "crimson_ray", stone, center, .2 * scale);
            }
            // Each arrival gets its own compact impact; no collective implosion
            // or final vertical spear covers the separate projectiles.
            int since = (age - LegendaryCastGeometry.crimsonArrival(0)) % LegendaryCastGeometry.CRIMSON_FLIGHT;
            if (age >= LegendaryCastGeometry.crimsonArrival(0) && age < 168) {
                double pulse = smooth(since / 8.0);
                seal("impact-ring", "crimson_seal", base.clone().add(0, .22, 0),
                        (3 + pulse * 4) * scale, -age * .035);
                crossed("impact-core", "crimson_heart", center, (2.8 - pulse * 1.8) * scale,
                        (2.8 - pulse * 1.8) * scale, age * .035, 0, 2);
            } else { hide("impact-ring"); hide("impact-core"); }
            for (int wave = 0; wave < 3; wave++) {
                if (age < LegendaryCastGeometry.crimsonArrival(0)) { hide("shard-shock-" + wave); continue; }
                int latest = age - (age - LegendaryCastGeometry.crimsonArrival(0)) % LegendaryCastGeometry.CRIMSON_FLIGHT;
                latest = Math.min(latest, LegendaryCastGeometry.crimsonArrival(7));
                int hit = latest - wave * LegendaryCastGeometry.CRIMSON_FLIGHT;
                double elapsed = (age - hit) / 24.0;
                if (hit < LegendaryCastGeometry.crimsonArrival(0) || elapsed >= 1) { hide("shard-shock-" + wave); continue; }
                double diameter = (5 + smooth(elapsed) * 14) * scale;
                seal("shard-shock-" + wave, "crimson_seal", base.clone().add(0, .3 + wave * .12, 0),
                        diameter * fade, age * .022 * (wave % 2 == 0 ? 1 : -1));
            }
        }
    }

    private Location shard(int age, int index) {
        int orbitAge = Math.min(age, LegendaryCastGeometry.CRIMSON_START);
        double angle = TAU * index / 8 + orbitAge * .012;
        double rise = smooth((age - 20 - index) / 16.0);
        double distance = radius * .82;
        double height = (7.5 + Math.sin(orbitAge * .06 + index) * .8) * rise;
        double progress = smooth((age - LegendaryCastGeometry.CRIMSON_START - index * LegendaryCastGeometry.CRIMSON_FLIGHT)
                / (double) LegendaryCastGeometry.CRIMSON_FLIGHT);
        return base.clone().add(Math.cos(angle) * distance * (1 - progress),
                (height * (1 - progress) + 2 * progress + Math.sin(progress * Math.PI) * 2) * scale,
                Math.sin(angle) * distance * (1 - progress));
    }

    private void ripples(int age, String model) {
        double expansion = (age - 112) / 40.0;
        for (int layer = 0; layer < 3; layer++) {
            double diameter = radius * (.7 + expansion * 2.2 - layer * .12) * fade(age);
            seal("ripple-" + layer, model, base.clone().add(0, (.4 + layer * 1.15) * scale, 0),
                    diameter, age * .03 * (layer % 2 == 0 ? 1 : -1));
        }
    }

    private void crossed(String key, String model, Location at, double width, double height,
                         double yaw, double roll, int count) {
        for (int layer = 0; layer < count; layer++) {
            double angle = yaw + Math.PI * layer / count;
            Vector right = new Vector(Math.cos(angle), 0, Math.sin(angle));
            plane(key + "-" + layer, model, at, right, new Vector(0, 1, 0), width, height, roll);
        }
    }

    private void seal(String key, String model, Location at, double diameter, double turn) {
        plane(key, model, at, new Vector(1, 0, 0), new Vector(0, 0, 1), diameter, diameter, turn);
    }

    private void plane(String key, String model, Location at, Vector right, Vector up,
                       double width, double height, double angle) {
        if (width < .05 || height < .05 || !c.loaded(at)) {
            hide(key);
            return;
        }
        MythicLineVisuals plane = piece(key, model, at);
        if (plane != null) plane.plane(visuals, at, right, up, width, height, angle);
    }

    private void ray(String key, String model, Location from, Location to, double width) {
        if (width < .03 || !loadedRay(from, to)) {
            hide(key);
            return;
        }
        Location midpoint = from.clone().add(to.toVector().subtract(from.toVector()).multiply(.5));
        MythicLineVisuals ray = piece(key, model, midpoint);
        if (ray != null) ray.ray(visuals, from, to, width);
    }

    private boolean loadedRay(Location from, Location to) {
        Vector delta = to.toVector().subtract(from.toVector());
        int steps = Math.max(1, (int) Math.ceil(delta.length() / 2));
        for (int step = 0; step <= steps; step++) {
            if (!c.loaded(from.clone().add(delta.clone().multiply((double) step / steps)))) return false;
        }
        return true;
    }

    private MythicLineVisuals piece(String key, String model, Location at) {
        MythicLineVisuals existing = art.get(key);
        if (existing != null) return existing;
        if (art.size() >= MAX_DISPLAYS || !c.loaded(at)) return null;
        MythicLineVisuals created = new MythicLineVisuals(effect, at, model);
        art.put(key, created);
        return created;
    }

    private void hide(String prefix) {
        art.forEach((key, value) -> { if (key.startsWith(prefix)) value.hide(); });
    }

    /** Small guided accents add motion around the authored lines, within budget. */
    private void accents(int age) {
        Color bright = kind == Kind.VERDANT ? EMERALD : kind == Kind.LUNAR ? SILVER : CRIMSON;
        Color dark = kind == Kind.VERDANT ? JADE : kind == Kind.LUNAR ? CYAN : RUBY;
        if (kind == Kind.CRIMSON && age >= LegendaryCastGeometry.CRIMSON_START) {
            if (LegendaryCastGeometry.crimsonImpact(age)) {
                sparks(base.clone().add(0, 2 * scale, 0), Particle.END_ROD, 28, 1.5 * scale);
                ring(base.clone().add(0, .25, 0), 3.5 * scale, CRIMSON, 1.65f, 24, age * .03, false);
            }
            for (int i = 0; i < 16; i++) {
                double turn = TAU * i / 16 + age * .15;
                double rise = (age % 8 + i % 4) * .6 * scale;
                double spread = (.8 + rise * .22) * scale;
                dust(base.clone().add(Math.cos(turn) * spread, rise, Math.sin(turn) * spread),
                        i % 3 == 0 ? GOLD : CRIMSON, 1.2f, false);
            }
            int flying = Math.min(7, Math.max(0, (age - LegendaryCastGeometry.CRIMSON_START) / 8));
            if (age < LegendaryCastGeometry.crimsonArrival(7)) {
                Location stone = shard(age, flying);
                Vector toward = base.toVector().add(new Vector(0, 2 * scale, 0)).subtract(stone.toVector()).normalize();
                for (int i = 0; i < 10; i++)
                    dust(stone.clone().subtract(toward.clone().multiply(i * .3)), i % 3 == 0 ? GOLD : RUBY, 1.25f, false);
            }
            return;
        }
        if (age == 100) {
            base.getWorld().playSound(base, Sound.BLOCK_BEACON_DEACTIVATE, 1.8f, .55f);
        }
        if (age == 112) sparks(base.clone().add(0, .8, 0), Particle.END_ROD, 28, 2.1);
        if (age < 112) {
            // Twelve individually positioned lights climb three helices; these
            // are decoration, not a replacement for the hero or seal textures.
            for (int i = 0; i < 12; i++) {
                double angle = TAU * i / 12 + age * .055;
                double height = ((age * .11 + i * 1.8) % 17) * scale;
                double distance = radius * (.62 + .07 * Math.sin(i + age * .04));
                dust(base.clone().add(Math.cos(angle) * distance, height, Math.sin(angle) * distance),
                        i % 3 == 0 ? bright : dark, i % 3 == 0 ? 1.5f : 1.05f, false);
            }
        } else {
            double distance = radius * smooth((age - 112) / 24.0);
            for (int i = 0; i < 20; i++) {
                double angle = TAU * i / 20 + age * .025;
                dust(base.clone().add(Math.cos(angle) * distance,
                                (.6 + Math.sin(angle * 3 + age * .2) * .25) * scale, Math.sin(angle) * distance),
                        i % 2 == 0 ? bright : dark, 1.4f, false);
            }
        }
    }

    /** Geometric pack-free heroes receive budget before the generic floor seal. */
    private void fallback(int age) {
        crimsonFallback(age);
        Color color = kind == Kind.VERDANT ? EMERALD : kind == Kind.LUNAR ? SILVER : CRIMSON;
        double growth = smooth(age / 24.0) * fade(age);
        ring(base.clone().add(0, .13, 0), radius * growth, color, 1.4f, 24, age * .008, true);
        ring(base.clone().add(0, .17, 0), radius * .73 * growth, color, 1.1f, 20, -age * .016, true);
        for (int i = 0; i < 8; i++) {
            double a = TAU * i / 8 + age * .012;
            double b = a + TAU * 3 / 8;
            line(base.clone().add(Math.cos(a) * radius * .73 * growth, .19, Math.sin(a) * radius * .73 * growth),
                    base.clone().add(Math.cos(b) * radius * .73 * growth, .19, Math.sin(b) * radius * .73 * growth),
                    color, 1f, 4, true);
        }
    }

    private void directionalFallback(int age) {
        if (geometry instanceof LegendaryCastGeometry.Verdant dragon) {
            Location center = dragon.sigil();
            Vector right = dragon.right(), up = new Vector(0, 1, 0);
            double growth = smooth(age / 24.0) * fade(age);
            for (int i = 0; i < 28; i++) {
                double angle = TAU * i / 28;
                dust(planePoint(center, right, up, Math.cos(angle) * 11 * scale * growth,
                        Math.sin(angle) * 11 * scale * growth), EMERALD, 1.4f, true);
            }
            Location previous = null;
            for (int i = 0; i <= 8; i++) {
                double t = i / 8.0;
                Location node = planePoint(center, right, up, Math.sin(t * TAU) * 1.3 * scale * growth,
                        (t * 7 - 3.5) * scale * growth);
                if (previous != null) line(previous, node, EMERALD, 1.5f, 2, true);
                previous = node;
            }
            for (int side : new int[]{-1, 1}) {
                Location shoulder = planePoint(center, right, up, side * scale, 1.2 * scale);
                Location tip = planePoint(center, right, up, side * 5 * scale * growth, 2.5 * scale * growth);
                Location lower = planePoint(center, right, up, side * 3.5 * scale * growth, -2 * scale * growth);
                line(shoulder, tip, EMERALD, 1.4f, 3, true);
                line(tip, lower, JADE, 1.2f, 3, true);
                line(lower, shoulder, EMERALD, 1.2f, 3, true);
            }
            if (age >= 24 && age <= 140) {
                var beam = age >= 112 && age < 124 ? dragon.finalBeam() : dragon.beam();
                if (beam != null) {
                    for (int corner = 0; corner < 4; corner++) {
                        double angle = TAU * corner / 4 + Math.PI / 4;
                        Vector offset = right.clone().multiply(Math.cos(angle) * beam.radius())
                                .add(up.clone().multiply(Math.sin(angle) * beam.radius()));
                        line(beam.from().add(offset), beam.to().add(offset), EMERALD, 1.8f, 12, true);
                    }
                }
            }
            ring(base.clone().add(0, .15, 0), 6.75 * scale * growth, JADE, 1.1f, 20, 0, true);
        } else if (geometry instanceof LegendaryCastGeometry.Lunar moons) {
            double growth = smooth(age / 24.0) * fade(age);
            var centers = moons.rings();
            // Attack outlines first; divide every remaining glyph sample evenly
            // so the last sky portals cannot disappear when the budget is low.
            for (int i = 0; i < LegendaryCastGeometry.LUNAR_COUNT; i++) {
                var beam = moons.columns().get(i);
                if (beam != null && LegendaryCastGeometry.Lunar.shotAge(age, i) >= 0)
                    line(beam.from(), beam.to(), SILVER, 1.5f, 1, true);
            }
            int samples = Math.min(12, particleBudget / centers.size());
            int moonPoints = samples >= 6 ? 3 : samples >= 2 ? 2 : 0;
            int ringPoints = samples - moonPoints;
            for (int portal = 0; portal < centers.size(); portal++) {
                Location center = centers.get(portal);
                double r = moons.ringDiameter() * .5 * growth;
                for (int i = 0; i < ringPoints; i++) {
                    double angle = TAU * i / ringPoints;
                    dust(planePoint(center, moons.planeRight(portal), moons.planeUp(portal), Math.cos(angle) * r,
                            Math.sin(angle) * r), SILVER, 1.3f, true);
                }
                for (int i = 0; i < moonPoints; i++) {
                    double angle = Math.PI / 3 + Math.PI * 4 / 3 * i / Math.max(1, moonPoints - 1);
                    dust(planePoint(center, moons.planeRight(portal), moons.planeUp(portal), Math.cos(angle) * r * .65,
                            Math.sin(angle) * r * .65), SILVER, 1.25f, true);
                }
            }
        }
    }

    private Location planePoint(Location center, Vector right, Vector up, double x, double y) {
        return center.clone().add(right.clone().multiply(x)).add(up.clone().multiply(y));
    }

    private void directionalAccents(int age) {
        if (geometry instanceof LegendaryCastGeometry.Verdant dragon) {
            double glow = smooth(age / 24.0) * fade(age);
            ring(base.clone().add(0, .25, 0), 12.5 * scale * glow, EMERALD, 1.3f, 24, -age * .03, false);
            if (age < 24) for (int i = 0; i < 16; i++) {
                double turn = TAU * i / 16 + age * .08;
                double distance = (1 - smooth(age / 24.0)) * 8 * scale + .7;
                dust(dragon.sigil().add(dragon.right().multiply(Math.cos(turn) * distance))
                        .add(0, Math.sin(turn) * distance, 0), i % 2 == 0 ? EMERALD : GOLD, 1.4f, false);
            }
            var beam = age >= 112 && age < 124 ? dragon.finalBeam() : dragon.beam();
            if (beam != null && age >= 24 && age <= 140) {
                Vector delta = beam.to().toVector().subtract(beam.from().toVector());
                for (int stream = 0; stream < 4; stream++) for (int i = 0; i < 14; i++) {
                    double progress = (age * .06 + i / 14.0) % 1;
                    double angle = age * .18 + progress * TAU * 1.6 + TAU * stream / 4;
                    Vector rim = dragon.right().multiply(Math.cos(angle) * beam.radius())
                            .add(new Vector(0, Math.sin(angle) * beam.radius(), 0));
                    dust(beam.from().add(delta.clone().multiply(progress)).add(rim),
                            stream == 0 ? GOLD : stream % 2 == 0 ? EMERALD : JADE, 1.3f, false);
                }
            }
            if ((age == 24 || age == 64 || age == 112) && beam != null) {
                sparks(beam.to(), Particle.END_ROD, age == 112 ? 44 : 20, age == 112 ? 2.5 : 1.3);
                base.getWorld().playSound(base, Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, age == 112 ? .65f : 1.1f);
            }
            if (age >= 112 && beam != null)
                ring(beam.to(), (3 + smooth((age - 112) / 32.0) * 10) * scale,
                        GOLD, 1.4f, 24, age * .03, false);
        } else if (geometry instanceof LegendaryCastGeometry.Lunar moons) {
            var ringCenters = moons.rings();
            for (int i = 0; i < LegendaryCastGeometry.LUNAR_COUNT; i++) {
                var beam = moons.columns().get(i);
                if (beam != null && LegendaryCastGeometry.Lunar.shotAge(age, i) >= 0) {
                    Vector down = beam.to().toVector().subtract(beam.from().toVector());
                    int shot = LegendaryCastGeometry.Lunar.shotAge(age, i);
                    for (int bead = 0; bead < 4; bead++) {
                        double progress = (shot * .125 + bead / 4.0) % 1;
                        dust(beam.from().add(down.clone().multiply(progress)), bead % 2 == 0 ? SILVER : GOLD, 1.35f, false);
                    }
                    if (shot == 0) sparks(beam.to().add(0, .3, 0), Particle.END_ROD, 5, .6);
                    ring(beam.to().add(0, .09, 0), (1.3 + shot * .3) * scale, SILVER, 1.2f, 4, age * .025, false);
                } else if (age < 24) {
                    dust(ringCenters.get(i), i % 2 == 0 ? SILVER : GOLD, 1.4f, false);
                }
            }
            if (age == 112 || age == 124 || age == 136)
                base.getWorld().playSound(base, Sound.BLOCK_BEACON_POWER_SELECT, 2f, age == 112 ? .65f : 1.1f);
        }
        if (age == 20) base.getWorld().playSound(base, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.8f, .7f);
    }

    private void crimsonFallback(int age) {
        if (age < LegendaryCastGeometry.CRIMSON_START) {
            Location heart = base.clone().add(0, 18 * scale, 0);
            double size = 5 * scale * smooth(age / 24.0);
            diamond(heart, size, size * 1.1, CRIMSON, 4, true);
            diamond(heart, size * .55, size * 1.05, RUBY, 3, true);
            for (int i = 0; i < 8; i++) {
                Location shard = shard(age, i);
                diamond(shard, .75 * scale, 2.6 * scale, i % 2 == 0 ? CRIMSON : RUBY, 1, true);
                if (age >= 40) {
                    line(shard, heart, CRIMSON, 1.2f, 3, true);
                    line(shard, shard(age, (i + 1) % 8), RUBY, 1.2f, 2, true);
                }
            }
        } else {
            for (int i = 0; i < 8; i++) {
                if (age >= LegendaryCastGeometry.crimsonArrival(i)) continue;
                Location stone = shard(age, i);
                diamond(stone, .75 * scale, 2.6 * scale, i % 2 == 0 ? CRIMSON : RUBY, 1, true);
                if (age >= LegendaryCastGeometry.CRIMSON_START + i * LegendaryCastGeometry.CRIMSON_FLIGHT)
                    line(stone, base.clone().add(0, 2 * scale, 0), CRIMSON, 1.2f, 6, true);
            }
            if (LegendaryCastGeometry.crimsonImpact(age))
                ring(base.clone().add(0, .25, 0), 2 * scale, CRIMSON, 1.3f, 16, 0, true);
        }
    }

    private void diamond(Location center, double width, double height, Color color, int samples, boolean fallback) {
        Location top = center.clone().add(0, height, 0), bottom = center.clone().add(0, -height, 0);
        for (int i = 0; i < 4; i++) {
            double angle = TAU * i / 4;
            Location rim = center.clone().add(Math.cos(angle) * width, 0, Math.sin(angle) * width);
            line(top, rim, color, 1.4f, samples, fallback);
            line(bottom, rim, color, 1.4f, samples, fallback);
        }
    }

    private void line(Location from, Location to, Color color, float size, int samples, boolean fallback) {
        Vector delta = to.toVector().subtract(from.toVector());
        for (int i = 0; i <= samples && particleBudget > 0; i++) {
            dust(from.clone().add(delta.clone().multiply((double) i / samples)), color, size, fallback);
        }
    }

    private void ring(Location center, double radius, Color color, float size, int samples, double turn, boolean fallback) {
        for (int i = 0; i < samples && particleBudget > 0; i++) {
            double angle = TAU * i / samples + turn;
            dust(center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius), color, size, fallback);
        }
    }

    private void dust(Location at, Color color, float size, boolean fallback) {
        if (particleBudget <= 0 || !c.loaded(at)) return;
        particleBudget--;
        if (fallback) visuals.dustFallback(at, color, size);
        else visuals.dust(at, color, size);
    }

    private void sparks(Location at, Particle type, int requested, double spread) {
        if (particleBudget <= 0 || !c.loaded(at)) return;
        int count = Math.min(requested, particleBudget);
        particleBudget -= count;
        at.getWorld().spawnParticle(type, at, count, spread, spread, spread, 0, null, true);
    }

    private static double smooth(double progress) {
        double t = Math.clamp(progress, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private double fade(int age) { return 1 - smooth((age - (kind == Kind.CRIMSON ? 164 : 136)) / 20.0); }

    @Override public void close() {
        art.values().forEach(MythicLineVisuals::close);
        art.clear();
        visuals.clear();
    }
}
