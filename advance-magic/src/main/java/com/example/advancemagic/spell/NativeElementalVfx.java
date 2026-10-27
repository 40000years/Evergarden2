package com.example.advancemagic.spell;

import com.example.advancemagic.elemental.ElementalSpell;
import com.example.advancemagic.item.ElementalStaffService.Element;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;

/**
 * Native, cosmetic spell choreography. Particles and display blocks never deal damage,
 * move targets, alter blocks, load chunks, or take a combat effect slot.
 * Projectile entities, where appropriate, are owned by NativeSpellProjectiles.
 */
public final class NativeElementalVfx implements AutoCloseable {
    private static final double TAU = Math.PI * 2;
    private static final Vector X = new Vector(1, 0, 0);
    private static final Vector Y = new Vector(0, 1, 0);
    private static final Vector Z = new Vector(0, 0, 1);
    private final MagicContext context;
    private final Set<Scene> scenes = new LinkedHashSet<>();
    private final Map<String, Scene> markers = new HashMap<>();
    private final Map<UUID, Integer> particles = new HashMap<>();
    private final Map<UUID, Integer> viewerParticles = new HashMap<>();
    private final Map<UUID, Integer> viewerPackets = new HashMap<>();
    private final Map<UUID, Integer> displays = new HashMap<>();
    private final Map<UUID, Long> sounds = new HashMap<>();
    private final Map<Material, BlockData> blocks = new HashMap<>();
    private int budgetTick = Integer.MIN_VALUE;
    private long tick;
    private boolean warned;

    public NativeElementalVfx(MagicContext context) { this.context = context; }

    private int setting(String key, int fallback, int min, int max) {
        return Math.clamp(context.plugin.getConfig().getInt("elemental-visuals." + key, fallback), min, max);
    }

    private boolean low() {
        return "low".equalsIgnoreCase(context.plugin.getConfig().getString("elemental-visuals.quality", "balanced"));
    }

    private void resetBudget() {
        int now = Bukkit.getCurrentTick();
        if (now == budgetTick) return;
        budgetTick = now;
        particles.clear();
        viewerParticles.clear();
        viewerPackets.clear();
    }

    public Scene begin(Player owner, ElementalSpell spell) {
        Scene scene = new Scene(owner, spell);
        if (context.plugin.getConfig().getBoolean("elemental-visuals.enabled", true)
                && scenes.size() < setting("max-scenes", 96, 8, 256)) scenes.add(scene);
        else scene.closed = true;
        return scene;
    }

    public void tick() {
        tick++;
        for (Scene scene : List.copyOf(scenes)) scene.advance();
        sounds.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    public void closeOwner(UUID owner) {
        for (Scene scene : List.copyOf(scenes)) if (scene.owner.getUniqueId().equals(owner)) scene.close();
    }

    @Override public void close() {
        for (Scene scene : List.copyOf(scenes)) scene.close();
        markers.clear();
        displays.clear();
        particles.clear();
        viewerParticles.clear();
        viewerPackets.clear();
        sounds.clear();
    }

    private static Vector unit(Vector direction) {
        return direction.lengthSquared() < .0001 ? Z.clone() : direction.clone().normalize();
    }

    private static Vector right(Vector direction) {
        Vector side = new Vector(direction.getZ(), 0, -direction.getX());
        return side.lengthSquared() < .0001 ? X.clone() : side.normalize();
    }

    private static double ease(double t) {
        t = Math.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static Particle particle(Element element) {
        return switch (element) {
            case FIRE -> Particle.FLAME;
            case WATER -> Particle.SPLASH;
            case ICE -> Particle.SNOWFLAKE;
            case WIND -> Particle.CLOUD;
            case EARTH -> Particle.BLOCK;
            case LIGHT -> Particle.END_ROD;
            case DARK -> Particle.REVERSE_PORTAL;
            case LIGHTNING -> Particle.ELECTRIC_SPARK;
        };
    }

    private static Sound activation(Element element) {
        return switch (element) {
            case FIRE -> Sound.ENTITY_BLAZE_SHOOT;
            case WATER -> Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED;
            case ICE -> Sound.BLOCK_AMETHYST_BLOCK_HIT;
            case WIND -> Sound.ENTITY_PLAYER_ATTACK_SWEEP;
            case EARTH -> Sound.BLOCK_DRIPSTONE_BLOCK_BREAK;
            case LIGHT -> Sound.BLOCK_AMETHYST_BLOCK_CHIME;
            case DARK -> Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE;
            case LIGHTNING -> Sound.ITEM_TRIDENT_THROW;
        };
    }

    private interface Draw { void paint(Frame frame); }
    private record Clip(long start, int life, Draw draw) {}
    private record Piece(BlockDisplay entity, UUID world, Material material) {}

    public final class Scene implements AutoCloseable {
        private final Player owner;
        private final ElementalSpell spell;
        private final World world;
        private final Vector facing;
        private final Vector side;
        private final Map<String, Clip> clips = new LinkedHashMap<>();
        private final Map<String, Piece> pieces = new HashMap<>();
        private List<Player> viewers = List.of();
        private long lastView = -100;
        private long lastTouch = tick;
        private long lastProjectile = -100;
        private boolean closed;

        private Scene(Player owner, ElementalSpell spell) {
            this.owner = owner;
            this.spell = spell;
            this.world = owner.getWorld();
            this.facing = unit(owner.getEyeLocation().getDirection());
            this.side = right(facing);
        }

        public boolean enabled() { return !closed; }
        private boolean grand() { return spell.rarity().ordinal() >= ElementalSpell.Rarity.EPIC.ordinal(); }
        private boolean alive() { return !closed && owner.isOnline() && !owner.isDead() && owner.getWorld() == world; }

        private void safe(Runnable operation) {
            if (!alive()) { close(); return; }
            try { operation.run(); }
            catch (RuntimeException failure) {
                if (!warned) {
                    warned = true;
                    context.plugin.getLogger().log(Level.WARNING, "A native elemental visual scene failed; combat continues", failure);
                }
                close();
            }
        }

        private void viewers(Location at) {
            if (tick - lastView < 4) return;
            lastView = tick;
            // Use the full world player list here: a remote impact can be far from the caster.
            viewers = List.copyOf(world.getPlayers());
        }

        private void emit(String key, Location at, int life, Draw draw) {
            safe(() -> {
                if (at.getWorld() != world || !context.loaded(at)) return;
                viewers(at);
                lastTouch = tick;
                if (!clips.containsKey(key) && clips.size() >= 32) clear(clips.keySet().iterator().next());
                clips.put(key, new Clip(tick, Math.clamp(life, 1, 400), draw));
                Frame frame = new Frame(this, key, 0);
                draw.paint(frame);
                frame.finish();
            });
        }

        private void advance() {
            safe(() -> {
                if (clips.isEmpty() && tick - lastTouch > 12) { close(); return; }
                for (var entry : List.copyOf(clips.entrySet())) {
                    Clip clip = entry.getValue();
                    int age = (int) (tick - clip.start());
                    if (age >= clip.life()) { clear(entry.getKey()); continue; }
                    boolean marker = entry.getKey().startsWith("status:") || entry.getKey().startsWith("buff:");
                    int interval = marker ? (low() ? 8 : 4) : 2;
                    if (age > 0 && age % interval == 0) {
                        Frame frame = new Frame(this, entry.getKey(), age);
                        clip.draw().paint(frame);
                        frame.finish();
                    }
                }
            });
        }

        public void clear(String prefix) {
            clips.keySet().removeIf(key -> key.equals(prefix) || key.startsWith(prefix + ":"));
            for (String key : List.copyOf(pieces.keySet()))
                if (key.startsWith(prefix + "/") || key.startsWith(prefix + ":")) release(key);
            markers.entrySet().removeIf(entry -> entry.getValue() == this
                    && (entry.getKey().equals(prefix) || entry.getKey().startsWith(prefix + ":")));
        }

        private void release(String key) {
            Piece piece = pieces.remove(key);
            if (piece == null) return;
            piece.entity().remove();
            displays.computeIfPresent(piece.world(), (id, count) -> count <= 1 ? null : count - 1);
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            for (String key : List.copyOf(pieces.keySet())) release(key);
            clips.clear();
            viewers = List.of();
            markers.values().removeIf(value -> value == this);
            scenes.remove(this);
        }

        private void claim(String key) {
            if (!alive()) return;
            Scene old = markers.get(key);
            if (old != null && old != this) old.clear(key);
            markers.put(key, this);
        }

        private void sound(Location at, Sound sound, float pitch) {
            if (!context.plugin.getConfig().getBoolean("elemental-visuals.sounds", true)
                    || !context.loaded(at) || tick - sounds.getOrDefault(owner.getUniqueId(), -100L) < 3) return;
            sounds.put(owner.getUniqueId(), tick);
            world.playSound(at, sound, spell.ultimate() ? .65f : .4f, pitch);
        }

        public void startup() {
            Location at = owner.getEyeLocation().add(facing.clone().multiply(.65)).add(0, -.25, 0);
            emit("startup", at, 8, f -> {
                double t = f.age / 8.0;
                for (int i = 0; i < 6; i++) {
                    double a = i * TAU / 6 + t * 2;
                    f.element(at.clone().add(side.clone().multiply(Math.cos(a) * .3 * (1 - t)))
                            .add(0, Math.sin(a) * .3 * (1 - t), 0), 1, .01);
                }
                if (f.age == 0 && spell.element() == Element.WIND) f.p(Particle.SMALL_GUST, at, 1, 0, 0);
            });
            sound(at, activation(spell.element()), grand() ? .85f : 1.2f);
        }

        public void projectile(Location from, Location to) {
            if (tick - lastProjectile < 1) return;
            lastProjectile = tick;
            Location start = from.clone(), end = to.clone();
            Vector direction = unit(end.toVector().subtract(start.toVector()));
            emit("projectile", end, 3, f -> {
                f.line(start, end, particle(spell.element()), low() ? 3 : 6, .025);
                switch (spell) {
                    case FIREBALL -> { f.p(Particle.FLAME, end, 3, .13, .015); f.p(Particle.SMOKE, start, 1, .08, .01); }
                    case AQUA_BULLET -> { f.sphere(end, .19, Particle.SPLASH, 8, f.age); f.p(Particle.BUBBLE_POP, end, 2, .12, 0); }
                    case FROST_BOLT -> f.block("core", end, Material.PACKED_ICE, .16, .48, .16, Math.atan2(direction.getX(), direction.getZ()), .8);
                    case STONE_BULLET -> f.block("core", end.clone().add(0, -.2, 0), Material.STONE, .48, .48, .48, tick * .25, tick * .1);
                    case RADIANT_BOLT -> { f.p(Particle.END_ROD, end, 3, .09, .01); f.p(Particle.GLOW, end, 1, .04, 0); }
                    case SHADOW_BOLT -> { f.p(Particle.SQUID_INK, end, 2, .08, .01); f.p(Particle.SOUL, start, 1, .04, 0); }
                    case SPARK_SHOT -> f.bolt(start, end, .13, 6);
                    default -> { }
                }
            });
        }

        public void beam(Location from, Location to) {
            Location a = from.clone(), b = to.clone();
            Vector span = b.toVector().subtract(a.toVector()), dir = unit(span), r = right(dir);
            Vector up = unit(dir.clone().crossProduct(r));
            String key = "beam:" + a.getBlockX() + ":" + a.getBlockY() + ":" + a.getBlockZ()
                    + ":" + b.getBlockX() + ":" + b.getBlockY() + ":" + b.getBlockZ();
            emit(key, a, 5, f -> {
                switch (spell) {
                    case LIGHTNING_CHAIN, THUNDER_SPEAR, THUNDER_DASH -> {
                        f.bolt(a, b, spell == ElementalSpell.THUNDER_SPEAR ? .3 : .18, 14);
                        if (f.age == 0) f.p(Particle.FLASH, b, 1, 0, 0);
                    }
                    case WATER_BLADE, WIND_CUTTER, THOUSAND_WIND_BLADES -> {
                        Location center = a.clone().add(span.clone().multiply(.35 + f.age * .13));
                        for (int i = 0; i < 13; i++) {
                            double angle = -.9 + i * 1.8 / 12;
                            Location point = center.clone().add(r.clone().multiply(Math.sin(angle) * 1.15))
                                    .add(dir.clone().multiply(Math.cos(angle) * .7));
                            f.p(spell.element() == Element.WATER ? Particle.SPLASH : Particle.CLOUD, point, 1, .01, .005);
                        }
                        if (f.age == 0 && spell.element() == Element.WIND) f.p(Particle.SWEEP_ATTACK, center, 1, 0, 0);
                    }
                    case HELLFIRE_CHAIN -> {
                        int points = Math.min(24, Math.max(5, (int) (span.length() * 3)));
                        for (int i = 0; i < points; i++) {
                            double t = i / (double) (points - 1), angle = i * 1.9 + f.age;
                            f.p(Particle.FLAME, a.clone().add(span.clone().multiply(t))
                                    .add(r.clone().multiply(Math.cos(angle) * .15)).add(up.clone().multiply(Math.sin(angle) * .15)), 1, .01, 0);
                        }
                    }
                    case INFERNO_SPEAR -> {
                        f.line(a, b, Particle.FLAME, 20, .02);
                        f.line(b.clone().subtract(dir.clone().multiply(.7)).add(r.clone().multiply(.25)), b, Particle.FLAME, 4, 0);
                        f.line(b.clone().subtract(dir.clone().multiply(.7)).subtract(r.clone().multiply(.25)), b, Particle.FLAME, 4, 0);
                    }
                    case GLACIAL_PATH -> {
                        for (int i = 0; i < 5; i++) {
                            Location at = a.clone().add(span.clone().multiply(i / 4.0));
                            f.block("ice" + i, at, Material.PACKED_ICE, .35, .35 + .15 * Math.sin(i), .35, i, .12);
                            f.p(Particle.SNOWFLAKE, at.clone().add(0, .3, 0), 2, .1, 0);
                        }
                    }
                    case LIFE_DRAIN -> {
                        f.line(a, b, Particle.SOUL, 12, .025);
                        Location motes = b.clone().subtract(span.clone().multiply((f.age + 1) / 6.0));
                        f.p(Particle.SCULK_SOUL, motes, 3, .12, .01);
                    }
                    case METEOR_RAIN -> { f.line(a, b, Particle.FLAME, 12, .15); f.p(Particle.SMOKE, b, 2, .2, .02); }
                    default -> f.line(a, b, particle(spell.element()), 16, .04);
                }
            });
        }

        public void area(Location at, double radius) {
            Location base = at.clone().add(0, .08, 0);
            String key = "area:" + at.getBlockX() + ":" + at.getBlockY() + ":" + at.getBlockZ();
            emit(key, base, grand() ? 18 : 10, f -> {
                double t = Math.min(1, (f.age + 2) / (grand() ? 18.0 : 10.0));
                switch (spell) {
                    case ICE_SPIKE, EARTH_SPIKE -> f.spikes(base, radius * .6, spell.element() == Element.ICE, 5, t);
                    case MAGMA_ERUPTION -> {
                        f.fountain(base, radius * .65, 4.2 * Math.sin(t * Math.PI), Particle.FLAME, 20);
                        f.p(Particle.LAVA, base.clone().add(0, .3, 0), f.age == 0 ? 6 : 2, radius * .35, .02);
                        if (f.age == 0) f.p(Particle.EXPLOSION, base.clone().add(0, .8, 0), 1, 0, 0);
                    }
                    case ABSOLUTE_ZERO -> {
                        f.sphere(base.clone().add(0, .8, 0), radius * ease(t), Particle.SNOWFLAKE, 30, f.age);
                        f.spikes(base, radius * .65 * ease(t), true, 7, Math.sin(t * Math.PI));
                        if (f.age == 0) f.p(Particle.FLASH, base, 1, 0, 0);
                    }
                    case SEISMIC_SLAM, TITANS_JUDGMENT -> {
                        f.spikes(base, radius * ease(t), false, 7, Math.sin(t * Math.PI));
                        f.p(Particle.BLOCK, base.clone().add(0, .4, 0), 7, radius * .3, .08);
                        if (f.age == 0) f.p(Particle.EXPLOSION, base, 1, 0, 0);
                    }
                    case UPDRAFT -> f.helix(base, radius * .55, 3.5, f.age * .6, Particle.CLOUD, 18, 2);
                    case ABYSSAL_COLLAPSE -> {
                        f.sphere(base.clone().add(0, 1.1, 0), radius * (1 - ease(t)), Particle.REVERSE_PORTAL, 28, f.age);
                        f.p(Particle.SQUID_INK, base.clone().add(0, 1, 0), 4, .4 * (1 - t), .015);
                        if (f.age == 0) f.p(Particle.SONIC_BOOM, base.clone().add(0, 1, 0), 1, 0, 0);
                    }
                    case PURIFICATION -> { f.helix(base, .7 + t, 2, f.age * .4, Particle.END_ROD, 14, 2); f.p(Particle.INSTANT_EFFECT, base.clone().add(0, 1, 0), 4, .5, .02); }
                    default -> {
                        f.ring(base, radius * ease(t), particle(spell.element()), grand() ? 24 : 18, t * .3);
                        f.fountain(base, radius * .5 * t, (1 - t) * 1.7, particle(spell.element()), 10);
                        if (f.age == 0 && spell.element() == Element.FIRE) { f.p(Particle.EXPLOSION, base.clone().add(0, .4, 0), 1, 0, 0); f.p(Particle.SMOKE, base, 5, .4, .03); }
                    }
                }
            });
            if (grand()) sound(base, activation(spell.element()), .7f);
        }

        public void cone(Location origin, Vector direction, double radius, double cosine) {
            Location base = origin.clone().add(0, .15, 0);
            Vector dir = unit(direction), r = right(dir);
            double half = Math.acos(Math.clamp(cosine, -1, 1));
            emit("cone", base, 8, f -> {
                double reach = radius * ease((f.age + 2) / 8.0);
                for (int i = 0; i < 17; i++) {
                    double angle = -half + half * 2 * i / 16;
                    Location point = base.clone().add(dir.clone().multiply(Math.cos(angle) * reach))
                            .add(r.clone().multiply(Math.sin(angle) * reach)).add(0, Math.sin(i * .8 + f.age) * .2, 0);
                    f.element(point, 1, .05);
                    if (spell.element() == Element.WATER && i % 3 == 0) f.p(Particle.BUBBLE_POP, point.clone().add(0, .5, 0), 1, .1, 0);
                }
            });
        }

        public void boundary(Location at, double radius) {
            Location base = at.clone().add(0, .12, 0);
            emit("boundary:" + at.getBlockX() + ":" + at.getBlockZ(), base, 5,
                    f -> f.ring(base, radius, particle(spell.element()), low() ? 8 : 12, tick * .02));
        }

        public void impact(LivingEntity target) {
            Location at = target.getLocation().add(0, Math.min(1.3, target.getHeight() * .55), 0);
            String key = "hit:" + target.getUniqueId();
            Clip old = clips.get(key);
            if (old != null && tick - old.start() < 3) return;
            emit(key, at, 5, f -> {
                f.element(at, f.age == 0 ? 7 : 2, .18 + f.age * .04);
                if (f.age == 0) f.p(switch (spell.element()) {
                    case FIRE -> Particle.SMOKE; case WATER -> Particle.BUBBLE_POP; case ICE -> Particle.ITEM_SNOWBALL;
                    case WIND -> Particle.SMALL_GUST; case EARTH -> Particle.BLOCK; case LIGHT -> Particle.GLOW;
                    case DARK -> Particle.SQUID_INK; case LIGHTNING -> Particle.CRIT;
                }, at, spell.element() == Element.WIND ? 1 : 4, .2, .02);
            });
        }

        public void heal(Player target) {
            emit("heal:" + target.getUniqueId(), target.getLocation(), 10, f -> {
                if (!target.isOnline() || target.getWorld() != world || target.isDead()) return;
                Location at = target.getLocation();
                f.helix(at, .4, 1.5, f.age * .4, Particle.HAPPY_VILLAGER, 7, 1);
                if (f.age == 0) f.p(Particle.HEART, at.add(0, 1.5, 0), 1, .1, 0);
            });
        }

        public void field(Location center, double radius, int age, int duration) {
            if (age % 2 != 0) return;
            Location base = center.clone().add(0, .12, 0);
            emit("field", base, 4, f -> {
                int time = age + f.age;
                switch (spell) {
                    case BURNING_GROUND -> {
                        for (int i = 0; i < 12; i++) {
                            double angle = i * 2.399 + time * .025, r = radius * Math.sqrt((i + .5) / 12);
                            Location at = base.clone().add(Math.cos(angle) * r, .1, Math.sin(angle) * r);
                            f.p(Particle.SMALL_FLAME, at, 2, .08, .01);
                            if (i % 4 == 0) f.p(Particle.SMOKE, at, 1, .04, .01);
                        }
                    }
                    case HEALING_SPRING -> {
                        f.ring(base, radius, Particle.SPLASH, 12, time * .02);
                        f.fountain(base, radius * .5, .7, Particle.SPLASH, 10);
                        if (time % 10 == 0) f.p(Particle.HAPPY_VILLAGER, base.clone().add(0, .6, 0), 4, radius * .5, .02);
                    }
                    case RAIN_BLESSING -> f.rain(base, radius, 4.4, time, Particle.FALLING_WATER, 18);
                    case WHIRLPOOL -> {
                        for (int layer = 0; layer < 3; layer++) f.ring(base.clone().add(0, layer * .13, 0), radius * (.25 + layer * .3), Particle.SPLASH, 10, -time * .12 + layer);
                        f.p(Particle.CURRENT_DOWN, base, 3, .2, .01);
                    }
                    case SCORCHING_VORTEX -> { f.helix(base, radius * .7, 4.3, time * .22, Particle.FLAME, 24, 3); f.p(Particle.LARGE_SMOKE, base.clone().add(0, 3.7, 0), 2, .35, .025); }
                    case CYCLONE_TRAP, TEMPEST_DOMINION -> {
                        double height = spell.ultimate() ? 7 : 4;
                        f.helix(base, radius * .75, height, time * .25, Particle.CLOUD, spell.ultimate() ? 36 : 24, 3);
                        if (time % 6 == 0) f.p(Particle.GUST, base.clone().add(0, height * .5, 0), 1, radius * .3, 0);
                    }
                    case BLIZZARD -> {
                        f.rain(base, radius, 4, time, Particle.SNOWFLAKE, 22);
                        f.helix(base, radius * .8, 2.5, time * -.11, Particle.SNOWFLAKE, 16, 2);
                    }
                    case QUICKSAND -> {
                        f.ring(base, radius * (.65 + .1 * Math.sin(time * .1)), Particle.FALLING_DUST, 18, -time * .04);
                        f.p(Particle.BLOCK, base, 4, radius * .45, .005);
                    }
                    case HOLY_SANCTUARY -> {
                        f.ring(base, radius, Particle.END_ROD, 16, time * .012);
                        for (int i = 0; i < 4; i++) {
                            double a = i * Math.PI / 2;
                            f.p(Particle.END_ROD, base.clone().add(Math.cos(a) * radius * .7, .6 + (time % 30) * .05, Math.sin(a) * radius * .7), 1, .04, .01);
                        }
                        if (time % 8 == 0) f.p(Particle.HAPPY_VILLAGER, base.clone().add(0, .7, 0), 3, radius * .5, .01);
                    }
                    case STATIC_FIELD -> {
                        for (int i = 0; i < 3; i++) {
                            double a = time * .06 + i * TAU / 3;
                            Location edge = base.clone().add(Math.cos(a) * radius, .1 + .5 * (i % 2), Math.sin(a) * radius);
                            f.bolt(base.clone().add(0, .25, 0), edge, .18, 8);
                        }
                    }
                    default -> f.helix(base, radius * .7, 2, time * .08, particle(spell.element()), 16, 2);
                }
            });
        }

        public void telegraph(Location center, double radius, int age, int duration) {
            if (age % 4 != 0 && duration - age > 12) return;
            if (age % 2 != 0) return;
            Location base = center.clone().add(0, .1, 0);
            double progress = Math.clamp(age / (double) Math.max(1, duration), 0, 1);
            emit("telegraph", base, 5, f -> {
                f.ring(base, radius, particle(spell.element()), 16, age * .025);
                f.ring(base, radius * (1 - progress), particle(spell.element()), 8, -age * .05);
                switch (spell) {
                    case TITANS_JUDGMENT -> {
                        Location boulder = base.clone().add(0, 9 - 5 * ease((progress - .6) / .4), 0);
                        f.block("boulder", boulder, Material.STONE, 3.4, 3, 3.4, age * .012, -.15);
                        f.p(Particle.FALLING_DUST, boulder, 5, 1, .02);
                    }
                    case MAGMA_ERUPTION -> { f.p(Particle.LAVA, base, 2, radius * .45, .02); f.p(Particle.SMOKE, base, 4, radius * .3, .01); }
                    case METEOR_RAIN -> f.p(Particle.FLAME, base, 5, radius * .45, .01);
                    case ABYSSAL_COLLAPSE -> f.sphere(base.clone().add(0, 2, 0), 1 + progress * 1.5, Particle.REVERSE_PORTAL, 20, age * .2);
                    case DIVINE_JUDGMENT, HEAVENS_DESCENT -> {
                        Location high = base.clone().add(0, 9 - progress * 3, 0);
                        f.line(high, high.clone().add(0, 2.5, 0), Particle.END_ROD, 9, .08);
                        f.ring(high, spell.ultimate() ? 2.5 : 1.2, Particle.END_ROD, 12, age * .035);
                    }
                    case WRATH_OF_RAIJIN -> { f.p(Particle.CAMPFIRE_COSY_SMOKE, base.clone().add(0, 8, 0), 4, 2, .01); f.p(Particle.ELECTRIC_SPARK, base, 4, radius * .3, .01); }
                    default -> f.helix(base, radius * .5, 1.2, age * .1, particle(spell.element()), 10, 1);
                }
            });
        }

        public void meteors(Location center, int age) {
            // Native fireball bodies and their trajectories are rendered by NativeSpellProjectiles.
            if (age % 10 != 0 || age >= 60) return;
            Location base = center.clone().add(0, .12, 0);
            emit("meteor-warning", base, 5, f -> f.ring(base, 1.2 + f.age * .08, Particle.SMALL_FLAME, 12, age));
        }

        public void pillar(Location at, double height) {
            Location base = at.clone();
            emit("pillar", base, grand() ? 18 : 10, f -> {
                double t = f.age / 18.0;
                switch (spell) {
                    case TITANS_JUDGMENT -> {
                        f.spikes(base, 3 * ease((f.age + 2) / 12.0), false, 8, Math.sin(t * Math.PI));
                        if (f.age == 0) { f.p(Particle.EXPLOSION, base.clone().add(0, 1, 0), 2, .8, 0); f.p(Particle.BLOCK, base, 18, 1.4, .1); }
                    }
                    case ABYSSAL_COLLAPSE -> {
                        f.helix(base, 2 * (1 - ease(t)), height * .7, -f.age * .35, Particle.REVERSE_PORTAL, 28, 3);
                        f.p(Particle.SQUID_INK, base.clone().add(0, 1.7, 0), 5, .25, .01);
                    }
                    case WRATH_OF_RAIJIN -> { f.bolt(base.clone().add(0, height, 0), base, .65, 24); if (f.age == 0) f.p(Particle.FLASH, base, 1, 0, 0); }
                    default -> {
                        if (spell.element() == Element.LIGHT) {
                            f.line(base, base.clone().add(0, height, 0), Particle.END_ROD, 26, .08);
                            for (int i = 0; i < 3; i++) {
                                double angle = i * TAU / 3 + f.age * .1;
                                Location bottom = base.clone().add(Math.cos(angle) * .7, 0, Math.sin(angle) * .7);
                                f.line(bottom, bottom.clone().add(0, height * (1 - t * .25), 0), Particle.END_ROD, 9, .02);
                            }
                            if (f.age == 0) { f.p(Particle.FLASH, base.clone().add(0, 1, 0), 1, 0, 0); f.p(Particle.FIREWORK, base.clone().add(0, 1, 0), 10, .4, .06); }
                        } else f.fountain(base, 1.4, height * (1 - t), particle(spell.element()), 24);
                    }
                }
            });
            sound(base, spell.element() == Element.LIGHTNING ? Sound.ITEM_TRIDENT_THUNDER : activation(spell.element()), .7f);
        }

        public void wave(Location front, Vector direction, double radius, int wave) {
            Clip old = clips.get("wave:" + wave);
            if (old != null && tick - old.start() < 2) return;
            Location at = front.clone();
            Vector dir = unit(direction), r = right(dir);
            emit("wave:" + wave, at, 3, f -> {
                boolean serpent = spell == ElementalSpell.LEVIATHANS_WRATH;
                for (int i = 0; i < (serpent ? 26 : 22); i++) {
                    double x = i / (serpent ? 25.0 : 21.0) * 2 - 1;
                    double h = (serpent ? 2.7 : 2) * Math.sqrt(Math.max(0, 1 - x * x));
                    Location crest = at.clone().add(r.clone().multiply(x * radius)).add(0, h, 0);
                    f.p(Particle.SPLASH, crest, 2, .08, .02);
                    if (i % 2 == 0) f.line(crest.clone().add(dir.clone().multiply(-.45)), crest.clone().add(0, -.65, 0), Particle.FALLING_WATER, 3, .04);
                }
                if (serpent) {
                    // A moving three-dimensional water serpent, with a coiling spine and split jaw.
                    for (int i = 0; i < 18; i++) {
                        double t = i / 17.0;
                        Location point = at.clone().subtract(dir.clone().multiply(t * 5))
                                .add(r.clone().multiply(Math.sin(t * TAU + tick * .15 + wave) * (1 - t)))
                                .add(0, 1.7 + Math.cos(t * TAU + tick * .15) * .65, 0);
                        f.p(Particle.SPLASH, point, 2, .12, .015);
                        if (i < 3) f.p(Particle.BUBBLE_POP, point.clone().add(r.clone().multiply(i % 2 == 0 ? .4 : -.4)), 1, .04, 0);
                    }
                }
            });
        }

        public void storm(Location at, int age) {
            if (age % 4 != 0 && age != 100) return;
            Location base = at.clone();
            emit("storm", base, 5, f -> {
                Location cloud = base.clone().add(0, 8, 0);
                f.ring(cloud, 3.7, Particle.CAMPFIRE_COSY_SMOKE, 12, age * .025);
                if (age % 20 < 4 || age == 100) {
                    f.bolt(cloud.clone().add(.4 * Math.sin(age), 2, 0), base, age == 100 ? .8 : .45, 24);
                    f.bolt(base.clone().add(0, 5, 0), base.clone().add(3 * Math.sin(age), 0, 3 * Math.cos(age)), .25, 12);
                    if (f.age == 0) f.p(Particle.FLASH, base, 1, 0, 0);
                }
            });
            if (age % 20 == 0 || age == 100) sound(base, Sound.ITEM_TRIDENT_THUNDER, age == 100 ? .65f : 1.2f);
        }

        public void status(LivingEntity target, String key, int count, int duration, BooleanSupplier active) {
            if (!Set.of("burn", "wet", "frost", "air", "radiance", "curse", "curse_mark", "shock", "break").contains(key)) return;
            String id = "status:" + target.getUniqueId() + ":" + key;
            claim(id);
            emit(id, target.getLocation(), duration, f -> {
                if (!valid(target, active)) { clear(id); return; }
                Location at = target.getLocation().add(0, .35, 0);
                // Each condition uses a native material cue; no floating texture icons.
                switch (key) {
                    case "burn" -> f.p(Particle.SMALL_FLAME, at, Math.min(3, count), .22, .005);
                    case "wet" -> f.p(Particle.DRIPPING_WATER, at.add(0, .7, 0), Math.min(3, count), .22, 0);
                    case "frost" -> f.p(Particle.SNOWFLAKE, at, Math.min(3, count), .3, 0);
                    case "air" -> f.p(Particle.CLOUD, at, 1, .18, .005);
                    case "radiance" -> f.p(Particle.END_ROD, at.add(0, .65, 0), Math.min(3, count), .2, 0);
                    case "shock" -> f.p(Particle.ELECTRIC_SPARK, at.add(0, .5, 0), Math.min(4, count + 1), .24, .015);
                    case "break" -> f.p(Particle.BLOCK, at, Math.min(3, count), .25, .01);
                    default -> f.p(Particle.SCULK_SOUL, at.add(0, .55, 0), Math.min(3, count), .2, .005);
                }
            });
        }

        private boolean valid(LivingEntity target, BooleanSupplier active) {
            return target.isValid() && !target.isDead() && target.getWorld() == world && active.getAsBoolean();
        }

        public void cage(LivingEntity target, int duration, BooleanSupplier active) {
            String id = "cage:" + target.getUniqueId();
            claim(id);
            emit(id, target.getLocation(), duration, f -> {
                if (!valid(target, active)) { clear(id); return; }
                Location base = target.getLocation();
                double height = Math.min(3.2, target.getHeight() + .45), radius = Math.max(.65, Math.min(1.3, target.getWidth() * .75));
                if (spell.element() == Element.WATER) {
                    f.sphere(base.clone().add(0, height * .5, 0), radius + .2, Particle.BUBBLE_POP, 20, f.age * .07);
                    f.helix(base, radius, height, f.age * .16, Particle.SPLASH, 12, 2);
                } else {
                    for (int i = 0; i < 5; i++) {
                        double a = i * TAU / 5;
                        Location foot = base.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
                        f.block("bar" + i, foot, Material.BLUE_ICE, .2, height, .2, -a, (i % 2 == 0 ? .07 : -.07));
                        f.p(Particle.SNOWFLAKE, foot.clone().add(0, height, 0), 1, .05, 0);
                    }
                }
            });
        }

        public void buff(LivingEntity target, String key, int duration, BooleanSupplier active) {
            String id = "buff:" + target.getUniqueId() + ":" + key;
            claim(id);
            emit(id, target.getLocation(), duration, f -> {
                if (!valid(target, active)) { clear(id); return; }
                Location at = target.getLocation();
                if (spell == ElementalSpell.AQUA_SHIELD) f.sphere(at.add(0, .95, 0), .8, Particle.SPLASH, 14, f.age * .08);
                else if (spell == ElementalSpell.WIND_BARRIER) f.helix(at, .8, 1.8, f.age * .22, Particle.CLOUD, 12, 2);
                else if (spell == ElementalSpell.FROZEN_ARMOR || spell == ElementalSpell.IRON_SKIN) {
                    Material material = spell == ElementalSpell.FROZEN_ARMOR ? Material.PACKED_ICE : Material.IRON_BLOCK;
                    Vector r = right(target.getLocation().getDirection());
                    for (int i : new int[]{-1, 1}) f.block("pauldron" + i,
                            at.clone().add(r.clone().multiply(i * .43)).add(0, 1, 0), material, .24, .5, .3, target.getLocation().getYaw() * Math.PI / 180, i * .1);
                    f.p(spell == ElementalSpell.FROZEN_ARMOR ? Particle.SNOWFLAKE : Particle.SCRAPE, at.add(0, .5, 0), 2, .3, .005);
                } else f.helix(at, .6, 1.6, f.age * .1, particle(spell.element()), 8, 1);
            });
        }

        public void summon(LivingEntity entity, int age) {
            if (age % 6 != 0) return;
            String key = "summon:" + entity.getUniqueId();
            emit(key, entity.getLocation(), 8, f -> {
                if (!entity.isValid() || entity.isDead() || entity.getWorld() != world) { clear(key); return; }
                Location at = entity.getLocation();
                if (spell == ElementalSpell.GOLEM_GUARDIAN) {
                    f.p(Particle.BLOCK, at.clone().add(0, .2, 0), 4, .5, .015);
                    if (age == 0) f.fountain(at, .7, 2, Particle.BLOCK, 14);
                } else {
                    f.p(Particle.SOUL, at.clone().add(0, 1, 0), 3, .35, .01);
                    f.p(Particle.SQUID_INK, at.clone().add(0, .3, 0), 2, .3, .01);
                }
            });
        }

        public void wall(BoundingBox box) {
            Location center = box.getCenter().toLocation(world);
            emit("wall", center, 6, f -> {
                Vector across = box.getWidthX() > box.getWidthZ() ? X : Z;
                double width = Math.max(box.getWidthX(), box.getWidthZ());
                double height = box.getHeight();
                double angle = across == X ? 0 : Math.PI / 2;
                for (int i = 0; i < 5; i++) {
                    Location foot = center.clone().add(across.clone().multiply((i - 2) * width / 5)).add(0, -height / 2, 0);
                    f.block("stone" + i, foot, Material.STONE_BRICKS, width / 5 * .96, height,
                            Math.min(box.getWidthX(), box.getWidthZ()) * .98, angle, 0);
                    if (f.age == 0) f.p(Particle.BLOCK, foot, 2, .1, .01);
                }
            });
        }

        public void phoenix() {
            Location base = owner.getLocation().add(0, 1.1, 0);
            emit("phoenix", base, 30, f -> {
                double t = f.age / 30.0;
                Location chest = base.clone().add(0, .5 + t * 3.5, 0);
                // Articulated fire wings occupy several depths and flap as the bird rises.
                for (int wing : new int[]{-1, 1}) for (int i = 0; i < 16; i++) {
                    double u = i / 15.0, span = u * 3.2 * (.8 + .2 * ease(t * 4));
                    double flap = Math.sin(f.age * .32) * u * .75;
                    Location point = chest.clone().add(side.clone().multiply(wing * span))
                            .subtract(facing.clone().multiply(u * u * .8))
                            .add(0, Math.sin(u * Math.PI) * .7 + flap - u * .25, 0);
                    f.p(Particle.FLAME, point, 2, .05, .01);
                    if (i % 3 == 0) f.line(point, point.clone().subtract(facing.clone().multiply(.6 + u)).add(0, -.2, 0), Particle.SMALL_FLAME, 4, .03);
                }
                f.line(chest.clone().add(0, -.6, 0), chest.clone().add(0, .8, 0), Particle.FLAME, 9, .1);
                for (int i = -1; i <= 1; i++) f.line(chest.clone().add(0, -.5, 0),
                        chest.clone().subtract(facing.clone().multiply(1.3)).add(side.clone().multiply(i * .45)).add(0, -1.8, 0), Particle.FLAME, 7, .03);
                if (f.age == 0) { f.p(Particle.TOTEM_OF_UNDYING, base, 22, .6, .1); f.p(Particle.FLASH, base, 1, 0, 0); }
            });
            sound(base, Sound.ITEM_TOTEM_USE, .8f);
        }
    }

    private final class Frame {
        private final Scene scene;
        private final String key;
        private final int age;
        private final Set<String> used = new HashSet<>();
        private int samples;

        private Frame(Scene scene, String key, int age) { this.scene = scene; this.key = key; this.age = age; }

        private void finish() {
            for (String id : List.copyOf(scene.pieces.keySet()))
                if (id.startsWith(key + "/") && !used.contains(id)) scene.release(id);
        }

        private void element(Location at, int count, double spread) { p(particle(scene.spell.element()), at, count, spread, .008); }

        private void p(Particle type, Location at, int count, double spread, double extra) {
            if (count <= 0 || scene.closed || at.getWorld() != scene.world || !context.loaded(at)) return;
            int frameCap = low() ? 48 : scene.spell.ultimate() ? 130 : 90;
            if (samples >= frameCap) return;
            if (low()) count = Math.max(1, (count + 1) / 2);
            count = Math.min(count, frameCap - samples);
            scene.viewers(at);
            resetBudget();
            UUID world = at.getWorld().getUID();
            int worldUsed = particles.getOrDefault(world, 0);
            count = Math.min(count, setting("max-native-particles-per-world-tick", 1200, 64, 8000) - worldUsed);
            if (count <= 0) return;
            Object data = null;
            if (type.getDataType() == BlockData.class) {
                Material material = scene.spell == ElementalSpell.QUICKSAND ? Material.SAND
                        : scene.spell.element() == Element.ICE ? Material.PACKED_ICE : Material.STONE;
                data = blocks.computeIfAbsent(material, Material::createBlockData);
            } else if (type.getDataType() == Particle.DustOptions.class) data = new Particle.DustOptions(Color.WHITE, 1);
            else if (type.getDataType() == Float.class) data = 1f;
            else if (type.getDataType() == Color.class) data = Color.WHITE;
            else if (type.getDataType() != Void.class) return;
            double range = setting("native-view-distance", 48, 16, 96);
            int sent = 0;
            for (Player viewer : scene.viewers) {
                if (!viewer.isOnline() || viewer.getWorld() != scene.world
                        || viewer.getLocation().distanceSquared(at) > range * range) continue;
                UUID id = viewer.getUniqueId();
                int usedParticles = viewerParticles.getOrDefault(id, 0), usedPackets = viewerPackets.getOrDefault(id, 0);
                int allowance = Math.min(count, setting("max-native-particles-per-viewer-tick", 220, 24, 2000) - usedParticles);
                if (allowance <= 0 || usedPackets >= setting("max-native-packets-per-viewer-tick", 96, 16, 512)) continue;
                viewer.spawnParticle(type, at, allowance, spread, spread, spread, extra, data);
                viewerParticles.put(id, usedParticles + allowance);
                viewerPackets.put(id, usedPackets + 1);
                sent = Math.max(sent, allowance);
            }
            if (sent > 0) { particles.put(world, worldUsed + sent); samples += sent; }
        }

        private void line(Location from, Location to, Particle type, int points, double spread) {
            if (from.getWorld() != to.getWorld()) return;
            Vector span = to.toVector().subtract(from.toVector());
            points = Math.min(points, low() ? 14 : 30);
            for (int i = 0; i < points; i++) p(type, from.clone().add(span.clone().multiply(i / (double) Math.max(1, points - 1))), 1, spread, .002);
        }

        private void ring(Location center, double radius, Particle type, int points, double turn) {
            if (radius < .01) return;
            if (low()) points = Math.max(6, points / 2);
            for (int i = 0; i < points; i++) {
                double a = turn + i * TAU / points;
                p(type, center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius), 1, .02, .005);
            }
        }

        private void sphere(Location center, double radius, Particle type, int points, double turn) {
            if (low()) points = Math.max(6, points / 2);
            for (int i = 0; i < points; i++) {
                double y = 1 - 2 * (i + .5) / points, r = Math.sqrt(1 - y * y), a = i * 2.399963 + turn;
                p(type, center.clone().add(Math.cos(a) * r * radius, y * radius, Math.sin(a) * r * radius), 1, .01, .004);
            }
        }

        private void helix(Location base, double radius, double height, double turn, Particle type, int points, int strands) {
            if (low()) points = Math.max(6, points / 2);
            for (int i = 0; i < points; i++) {
                double t = i / (double) Math.max(1, points - 1), angle = t * TAU * strands + turn;
                double r = radius * (.3 + t * .7);
                p(type, base.clone().add(Math.cos(angle) * r, t * height, Math.sin(angle) * r), 1, .03, .005);
            }
        }

        private void fountain(Location base, double radius, double height, Particle type, int points) {
            if (low()) points = Math.max(6, points / 2);
            for (int i = 0; i < points; i++) {
                double t = (i + .5) / points, angle = i * 2.399963 + age * .07;
                p(type, base.clone().add(Math.cos(angle) * radius * t, Math.sin(t * Math.PI) * height,
                        Math.sin(angle) * radius * t), 1, .045, .015);
            }
        }

        private void rain(Location base, double radius, double height, int time, Particle type, int points) {
            if (low()) points = Math.max(6, points / 2);
            for (int i = 0; i < points; i++) {
                double a = i * 2.399963, r = radius * Math.sqrt((i + .5) / points);
                double y = height * (1 - ((time * .045 + i * .137) % 1));
                p(type, base.clone().add(Math.cos(a) * r, y, Math.sin(a) * r), 1, .03, .01);
            }
        }

        private void bolt(Location from, Location to, double jitter, int points) {
            Vector span = to.toVector().subtract(from.toVector()), r = right(span);
            Vector up = unit(unit(span).crossProduct(r));
            Location previous = from;
            for (int i = 1; i <= points; i++) {
                double t = i / (double) points, shift = i == points ? 0 : Math.sin(i * 2.71 + tick * 1.7) * jitter;
                Location next = from.clone().add(span.clone().multiply(t)).add(r.clone().multiply(shift))
                        .add(up.clone().multiply(Math.cos(i * 3.11 + tick) * jitter * Math.sin(t * Math.PI)));
                p(Particle.ELECTRIC_SPARK, next, 1, .015, 0);
                if (!low() && i % 2 == 0) p(Particle.END_ROD, previous.clone().add(next.toVector().subtract(previous.toVector()).multiply(.5)), 1, .005, 0);
                previous = next;
            }
        }

        private void spikes(Location base, double radius, boolean ice, int count, double growth) {
            double rise = Math.max(.06, growth);
            for (int i = 0; i < count; i++) {
                double a = i * TAU / count + .35;
                Location foot = base.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
                double h = (1.1 + (i % 3) * .45) * rise;
                Material material = ice ? Material.BLUE_ICE : Material.DEEPSLATE;
                block("spike" + i, foot, material, .35 * rise, h, .35 * rise, -a, (i % 2 == 0 ? .16 : -.16));
                p(ice ? Particle.SNOWFLAKE : Particle.BLOCK, foot.clone().add(0, h, 0), 2, .12, .015);
            }
        }

        private void block(String name, Location at, Material material, double width, double height, double depth, double yaw, double tilt) {
            String id = key + "/" + name;
            used.add(id);
            if (scene.closed || at.getWorld() != scene.world || !context.loaded(at) || width < .03 || height < .03) return;
            // Geyser does not translate display entities. Always provide a native 3D
            // outline as well, so ice bars, armor and incoming boulders remain readable.
            Particle outline = material == Material.PACKED_ICE || material == Material.BLUE_ICE
                    ? Particle.SNOWFLAKE : Particle.BLOCK;
            for (int corner = 0; corner < 8; corner++) {
                double x = ((corner & 1) == 0 ? -.5 : .5) * width;
                double y = ((corner & 2) == 0 ? 0 : height);
                double z = ((corner & 4) == 0 ? -.5 : .5) * depth;
                double tiltedX = x * Math.cos(tilt) - y * Math.sin(tilt);
                double tiltedY = x * Math.sin(tilt) + y * Math.cos(tilt);
                p(outline, at.clone().add(tiltedX * Math.cos(yaw) + z * Math.sin(yaw), tiltedY,
                        -tiltedX * Math.sin(yaw) + z * Math.cos(yaw)), 1, .025, .003);
            }
            if (low()) { scene.release(id); return; }
            scene.viewers(at);
            double range = setting("native-view-distance", 48, 16, 96);
            boolean visible = scene.viewers.stream().anyMatch(viewer -> viewer.isOnline() && viewer.getWorld() == scene.world
                    && viewer.getLocation().distanceSquared(at) <= range * range);
            if (!visible) { scene.release(id); return; }
            Piece piece = scene.pieces.get(id);
            if (piece != null && (!piece.entity().isValid() || piece.material() != material)) { scene.release(id); piece = null; }
            if (piece == null) {
                int cap = setting("max-native-displays-per-world", 80, 0, 240);
                if (displays.getOrDefault(scene.world.getUID(), 0) >= cap || scene.pieces.size() >= (scene.spell.ultimate() ? 12 : 8)) {
                    p(material == Material.PACKED_ICE || material == Material.BLUE_ICE ? Particle.SNOWFLAKE : Particle.BLOCK,
                            at.clone().add(0, height * .5, 0), 2, width * .5, .005);
                    return;
                }
                BlockDisplay display = scene.world.spawn(at, BlockDisplay.class, entity -> {
                    entity.setBlock(blocks.computeIfAbsent(material, Material::createBlockData));
                    entity.setPersistent(false);
                    entity.setInvulnerable(true);
                    entity.setGravity(false);
                    entity.setSilent(true);
                    entity.setViewRange((float) range / 64f);
                    entity.setShadowRadius(0);
                    entity.setShadowStrength(0);
                    entity.setInterpolationDuration(2);
                    entity.setTeleportDuration(2);
                    entity.setDisplayWidth(8);
                    entity.setDisplayHeight(12);
                    if (material == Material.PACKED_ICE || material == Material.BLUE_ICE) entity.setBrightness(new Display.Brightness(12, 12));
                });
                piece = new Piece(display, scene.world.getUID(), material);
                scene.pieces.put(id, piece);
                displays.merge(scene.world.getUID(), 1, Integer::sum);
            }
            BlockDisplay display = piece.entity();
            display.teleport(at);
            display.setInterpolationDelay(0);
            display.setTransformationMatrix(new Matrix4f().rotateY((float) yaw).rotateZ((float) tilt)
                    .scale((float) width, (float) height, (float) depth).translate(-.5f, 0, -.5f));
        }
    }
}
