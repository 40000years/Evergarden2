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
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;

import static com.example.advancemagic.spell.SpellFx.Kind.BURST;
import static com.example.advancemagic.spell.SpellFx.Kind.LINE;
import static com.example.advancemagic.spell.SpellFx.Kind.RAIN;
import static com.example.advancemagic.spell.SpellFx.Kind.RING;
import static com.example.advancemagic.spell.SpellFx.Kind.RISE;
import static com.example.advancemagic.spell.SpellFx.Kind.SWIRL;
import static com.example.advancemagic.spell.SpellFx.Kind.TRAIL;

/**
 * Native, cosmetic spell choreography. Nothing here deals damage, moves targets, alters blocks,
 * loads chunks or takes a combat effect slot. Projectile entities, where appropriate, are owned
 * by NativeSpellProjectiles.
 *
 * <p>Both clients see the same thing, and Bedrock is never asked for more than it can draw:
 * <ul>
 *   <li><b>Shots and kits</b> ({@link SpellFx}) carry each cast's spectacle: element magic circles, bursts,
 *       glows, shockwaves, beams and bolts that animate themselves from one send, and element particle
 *       kits that Bedrock spawns client-side from one emitter packet per layer.</li>
 *   <li><b>Sprites</b> are tracked planes a scene redraws (projectile heads, blades, shields).</li>
 *   <li><b>Props</b> give volume: a Java BlockDisplay and, for Geyser viewers, the full-pose cube rig with the
 *       same pose. At most 12 per cast (18 for an Ultimate); a resting prop sends Bedrock almost nothing.</li>
 *   <li><b>Vanilla particles</b> are fine detail for Java viewers. Only types Geyser draws as a single
 *       particle are ever shared with Bedrock, inside a small per-viewer budget.</li>
 * </ul>
 */
public final class NativeElementalVfx implements AutoCloseable {
    private static final double TAU = Math.PI * 2;
    private static final Vector X = new Vector(1, 0, 0);
    private static final Vector Y = new Vector(0, 1, 0);
    private static final Vector Z = new Vector(0, 0, 1);
    private static final Color GOLD = Color.fromRGB(0xFFD34D), ORANGE = Color.fromRGB(0xFF8546), CYAN = Color.fromRGB(0x72EDFF),
            VIOLET = Color.fromRGB(0xA36BFF), EMERALD = Color.fromRGB(0x58FFB1), JADE = Color.fromRGB(0x12B76A),
            SILVER = Color.fromRGB(0xD8F3FF), CRIMSON = Color.fromRGB(0xFF315B), RUBY = Color.fromRGB(0xA41B52), WHITE = Color.WHITE;
    private static final Color EMBER = Color.fromRGB(0xFFB347), DEEP_WATER = Color.fromRGB(0x2F7DE1), STONE_DUST = Color.fromRGB(0x9C8A73),
            SAND_DUST = Color.fromRGB(0xD9C08A), FROST = Color.fromRGB(0xBFE9FF), INK = Color.fromRGB(0x3A1F5C), SMOKE = Color.fromRGB(0x6F6A66);
    /** Particle types Geyser draws as one Bedrock particle (see Geyser's mappings/particles.json). */
    private static final Set<Particle> SAFE = Set.of(Particle.FLAME, Particle.SMALL_FLAME, Particle.SOUL_FIRE_FLAME, Particle.LAVA,
            Particle.SNOWFLAKE, Particle.END_ROD, Particle.ELECTRIC_SPARK, Particle.SPLASH, Particle.BUBBLE_POP, Particle.DRIPPING_WATER,
            Particle.SOUL, Particle.SCULK_SOUL, Particle.REVERSE_PORTAL, Particle.SQUID_INK, Particle.HAPPY_VILLAGER, Particle.HEART,
            Particle.DUST, Particle.FALLING_DUST, Particle.ENCHANT, Particle.GLOW, Particle.TOTEM_OF_UNDYING,
            Particle.CURRENT_DOWN, Particle.SCRAPE);
    private final MagicContext context;
    private final SpellFx fx;
    private final Set<Scene> scenes = new LinkedHashSet<>();
    private final Map<String, Scene> markers = new HashMap<>();
    private final Map<UUID, Integer> particles = new HashMap<>();
    private final Map<UUID, Integer> viewerParticles = new HashMap<>();
    private final Map<UUID, Integer> viewerPackets = new HashMap<>();
    private final Map<UUID, Integer> displays = new HashMap<>();
    private final Map<UUID, Integer> sprites = new HashMap<>();
    private final Map<UUID, Long> sounds = new HashMap<>();
    private final Map<Material, BlockData> blocks = new HashMap<>();
    private final Set<Material> unsupported = new HashSet<>();
    private int budgetTick = Integer.MIN_VALUE;
    private long tick;
    private boolean warned;

    public NativeElementalVfx(MagicContext context) {
        this.context = context;
        fx = new SpellFx(context, new SpellFx.Budget() {
            @Override public int world(World world) {
                resetBudget();
                return setting("max-native-particles-per-world-tick", 3200, 64, 12000) - particles.getOrDefault(world.getUID(), 0);
            }
            @Override public void spend(World world, int count) { particles.merge(world.getUID(), count, Integer::sum); }
            @Override public int viewer(Player viewer, int count) {
                resetBudget();
                UUID id = viewer.getUniqueId();
                int used = viewerParticles.getOrDefault(id, 0), packets = viewerPackets.getOrDefault(id, 0);
                int allowed = Math.min(count, setting("max-native-particles-per-viewer-tick", 900, 24, 4000) - used);
                if (allowed <= 0 || packets >= setting("max-native-packets-per-viewer-tick", 240, 16, 1024)) return 0;
                viewerParticles.put(id, used + allowed);
                viewerPackets.put(id, packets + 1);
                return allowed;
            }
        });
    }

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

    /** The cast flourish alone, for spells whose own choreography is authored elsewhere; close it with that scene. */
    public Scene flourish(Player owner, ElementalSpell spell) {
        Scene scene = begin(owner, spell);
        if (scene.enabled()) scene.startup();
        return scene;
    }

    /**
     * Bedrock's share of an authored Legendary: a few long kit emitters over the spell's area instead of hundreds
     * of translated vanilla particles (Java viewers keep the authored particles).
     */
    public Scene overlay(Player owner, ElementalSpell spell, Location floor, int duration) {
        Scene scene = begin(owner, spell);
        if (scene.enabled()) scene.overlay(floor, duration);
        return scene;
    }

    public void tick() {
        tick++;
        fx.tick();
        for (Scene scene : List.copyOf(scenes)) scene.advance();
        sounds.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    public void closeOwner(UUID owner) {
        for (Scene scene : List.copyOf(scenes)) if (scene.owner.getUniqueId().equals(owner)) scene.close();
    }

    @Override public void close() {
        for (Scene scene : List.copyOf(scenes)) scene.close();
        fx.close();
        markers.clear();
        displays.clear();
        sprites.clear();
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

    private static double random(double from, double to) { return ThreadLocalRandom.current().nextDouble(from, to); }

    /** Rotation taking a prop's long (Y) axis onto {@code direction}. */
    private static Quaternionf along(Vector direction) {
        Vector d = unit(direction);
        return new Quaternionf().rotationTo(0, 1, 0, (float) d.getX(), (float) d.getY(), (float) d.getZ());
    }

    private static Quaternionf spin(double yaw, double pitch, double roll) {
        return new Quaternionf().rotateY((float) yaw).rotateX((float) pitch).rotateZ((float) roll);
    }

    private static Color primary(Element element) {
        return switch (element) {
            case FIRE -> ORANGE; case WATER -> CYAN; case ICE -> SILVER; case WIND -> EMERALD;
            case EARTH -> JADE; case LIGHT -> GOLD; case DARK -> VIOLET; case LIGHTNING -> SILVER;
        };
    }

    private static Color secondary(Element element) {
        return switch (element) {
            case FIRE -> EMBER; case WATER -> DEEP_WATER; case ICE -> FROST; case WIND -> WHITE;
            case EARTH -> STONE_DUST; case LIGHT -> WHITE; case DARK -> INK; case LIGHTNING -> CYAN;
        };
    }

    /** The sprite tint ({@code vfx_<sprite>_<tint>}) of an element. */
    private static String tint(Element element) {
        return switch (element) {
            case FIRE -> "fire"; case WATER -> "water"; case ICE -> "ice"; case WIND -> "wind";
            case EARTH -> "earth"; case LIGHT -> "light"; case DARK -> "dark"; case LIGHTNING -> "lightning";
        };
    }

    /** The element's own vanilla particle; wind has none that Bedrock draws alike, so it is pale dust. */
    private static Particle particle(Element element) {
        return switch (element) {
            case FIRE -> Particle.FLAME;
            case WATER -> Particle.SPLASH;
            case ICE -> Particle.SNOWFLAKE;
            case WIND -> Particle.DUST;
            case EARTH -> Particle.FALLING_DUST;
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
            case WIND -> Sound.ENTITY_BREEZE_SHOOT;
            case EARTH -> Sound.BLOCK_DRIPSTONE_BLOCK_BREAK;
            case LIGHT -> Sound.BLOCK_AMETHYST_BLOCK_CHIME;
            case DARK -> Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE;
            case LIGHTNING -> Sound.ITEM_TRIDENT_THROW;
        };
    }

    /** The low thump layered under an element's blasts. */
    private static Sound boom(Element element) {
        return switch (element) {
            case FIRE -> Sound.ENTITY_GENERIC_EXPLODE;
            case WATER -> Sound.ENTITY_GENERIC_SPLASH;
            case ICE -> Sound.BLOCK_GLASS_BREAK;
            case WIND -> Sound.ENTITY_BREEZE_WIND_BURST;
            case EARTH -> Sound.ENTITY_GENERIC_EXPLODE;
            case LIGHT -> Sound.BLOCK_BEACON_POWER_SELECT;
            case DARK -> Sound.ENTITY_WARDEN_SONIC_BOOM;
            case LIGHTNING -> Sound.ENTITY_LIGHTNING_BOLT_IMPACT;
        };
    }

    /** The block a freeze cage is built from, themed by the caster's element. */
    private static Material cageMaterial(Element element) {
        return switch (element) {
            case FIRE -> Material.MAGMA_BLOCK; case WATER -> Material.LIGHT_BLUE_STAINED_GLASS; case ICE -> Material.BLUE_ICE;
            case WIND -> Material.WHITE_STAINED_GLASS; case EARTH -> Material.TUFF; case LIGHT -> Material.GOLD_BLOCK;
            case DARK -> Material.CRYING_OBSIDIAN; case LIGHTNING -> Material.YELLOW_STAINED_GLASS;
        };
    }

    private static boolean glowing(Material material) {
        return Set.of(Material.GLOWSTONE, Material.SEA_LANTERN, Material.SHROOMLIGHT, Material.MAGMA_BLOCK, Material.CRYING_OBSIDIAN,
                Material.GOLD_BLOCK, Material.AMETHYST_BLOCK).contains(material) || material.name().endsWith("_STAINED_GLASS")
                || material.name().endsWith("ICE");
    }

    /** The kit tint a status marker draws with. */
    private static String statusTint(String key) {
        return switch (key) {
            case "burn" -> "fire"; case "wet" -> "water"; case "frost" -> "ice"; case "air" -> "wind";
            case "radiance" -> "light"; case "shock" -> "lightning"; case "break" -> "earth"; default -> "dark";
        };
    }

    private interface Draw { void paint(Frame frame); }
    private record Clip(long start, int life, Draw draw) {}
    private record Piece(BlockDisplay entity, UUID world, Material material, MythicVisuals.BlockProp prop) {}
    private record Sprite(MythicLineVisuals display, String model, UUID world) {}

    public final class Scene implements AutoCloseable {
        private final Player owner;
        private final ElementalSpell spell;
        private final World world;
        private final Vector facing;
        private final Vector side;
        private final Map<String, Clip> clips = new LinkedHashMap<>();
        private final Map<String, Piece> pieces = new HashMap<>();
        private final Map<String, Sprite> planes = new HashMap<>();
        private final Map<String, Long> planeSent = new HashMap<>();
        private final Set<String> opened = new HashSet<>();
        private final MythicVisuals views;
        private Frame spriteFrame;
        private List<Player> viewers = List.of();
        private long lastView = -100;
        private long lastTouch = tick;
        private long lastProjectile = -100;
        private long wallBorn = -1;
        private boolean wallRang;
        private boolean closed;

        private Scene(Player owner, ElementalSpell spell) {
            this.owner = owner;
            this.spell = spell;
            this.world = owner.getWorld();
            this.facing = unit(owner.getEyeLocation().getDirection());
            this.side = right(facing);
            this.views = new MythicVisuals(context, player -> spriteFrame != null && spriteFrame.permitSprite(player),
                    setting("native-view-distance", 48, 16, 96));
        }

        public boolean enabled() { return !closed; }
        private boolean grand() { return spell.rarity().ordinal() >= ElementalSpell.Rarity.EPIC.ordinal(); }
        /** 0 basic, 1 rare, 2 epic, 3 legendary. */
        private int tier() { return spell.rarity().ordinal(); }
        private boolean alive() { return !closed && owner.isOnline() && !owner.isDead() && owner.getWorld() == world; }
        private Color main() { return primary(spell.element()); }
        private Color accent() { return secondary(spell.element()); }
        private String tint() { return NativeElementalVfx.tint(spell.element()); }
        /** Healing spells draw their circles and kits in the healing green. */
        private String kindTint() {
            return switch (spell) { case HEALING_SPRING, RAIN_BLESSING, PURIFICATION -> "heal"; default -> tint(); };
        }
        /** Kit particle counts follow the quality setting. */
        private double q() { return low() ? .5 : 1; }

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
            views.frame(at);
        }

        /** Everyone who may see something at {@code at} (range is checked per send). */
        private List<Player> crowd(Location at) {
            viewers(at);
            return viewers;
        }

        /** True once per key for this scene: one-shot openings of clips that are re-emitted. */
        private boolean opening(String key) { return opened.add(key); }

        private SpellFx.Shot shot(String sprite, String tint, Location at) { return fx.shot(sprite, tint, at).owner(this); }

        private SpellFx.Kit kit(String tint, SpellFx.Kind kind, Location at) { return fx.kit(tint, kind, at).owner(this); }

        private void emit(String key, Location at, int life, Draw draw) {
            safe(() -> {
                if (at.getWorld() != world || !context.loaded(at)) return;
                viewers(at);
                lastTouch = tick;
                if (!clips.containsKey(key) && clips.size() >= 40) clear(clips.keySet().iterator().next());
                clips.put(key, new Clip(tick, Math.clamp(life, 1, 400), draw));
                Frame frame = new Frame(this, key, 0);
                draw.paint(frame);
                frame.finish();
            });
        }

        private void advance() {
            safe(() -> {
                if (clips.isEmpty() && tick - lastTouch > 12) { end(false); return; }
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
            for (String key : List.copyOf(planes.keySet()))
                if (key.startsWith(prefix + "/") || key.startsWith(prefix + ":")) release(key);
            markers.entrySet().removeIf(entry -> entry.getValue() == this
                    && (entry.getKey().equals(prefix) || entry.getKey().startsWith(prefix + ":")));
        }

        private void release(String key) {
            Piece piece = pieces.remove(key);
            if (piece != null) {
                piece.entity().remove();
                piece.prop().clear();
                displays.computeIfPresent(piece.world(), (id, count) -> count <= 1 ? null : count - 1);
            }
            Sprite sprite = planes.remove(key);
            if (sprite != null) {
                sprite.display().close();
                planeSent.remove(key);
                sprites.computeIfPresent(sprite.world(), (id, count) -> count <= 1 ? null : count - 1);
            }
        }

        @Override public void close() { end(true); }

        /** A cancelled scene also stops its kit sprites and particles; one that simply finished lets them play out. */
        private void end(boolean cancel) {
            if (cancel) fx.stop(this);
            if (closed) return;
            closed = true;
            for (String key : List.copyOf(pieces.keySet())) release(key);
            for (String key : List.copyOf(planes.keySet())) release(key);
            clips.clear();
            viewers = List.of();
            views.clear();
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
            world.playSound(at, sound, spell.ultimate() ? .65f : .45f, pitch);
        }

        /** A sound layered under another in the same beat (no throttle). */
        private void layer(Location at, Sound sound, float volume, float pitch) {
            if (!context.plugin.getConfig().getBoolean("elemental-visuals.sounds", true) || !context.loaded(at)) return;
            world.playSound(at, sound, volume, pitch);
        }

        /**
         * The cast: an element circle opens under the caster and a second one in front of the staff, the
         * release flares through it and a shockwave rolls out. Rarer spells add a counter-rotating sigil,
         * a rising column and, for Legendaries, a circle in the sky joined by a pillar of light.
         */
        public void startup() {
            Location feet = owner.getLocation().add(0, .06, 0);
            feet.setYaw(0);
            feet.setPitch(0);
            Location staff = owner.getEyeLocation().add(facing.clone().multiply(1.05)).add(0, -.28, 0);
            int tier = tier();
            String t = tint();
            double ground = new double[]{2.2, 2.9, 3.8, 5.2}[tier], front = new double[]{.9, 1.25, 1.65, 2.2}[tier];
            int life = new int[]{16, 22, 30, 40}[tier];
            safe(() -> {
                List<Player> crowd = crowd(staff);
                shot("circle", t, feet).flat().size(ground * .3, ground).grow(.18).life(life).spin(tier % 2 == 0 ? 55 : -55)
                        .fade(2, 8).collapse().play(crowd);
                shot("ring", t, feet.clone().add(0, .02, 0)).flat().size(ground * .3, ground * 1.6).life(10).fade(1, 5).play(crowd);
                shot("circle", t, staff).facing(facing).size(.2, front).grow(.25).life(8 + tier * 4).spin(-140).fade(1, 5).collapse().play(crowd);
                shot("glow", t, staff).size(.4, front * 1.15).grow(.3).life(8).fade(1, 4).collapse().play(crowd);
                kit(t, BURST, staff).radius(.5 + tier * .15).count((.35 + tier * .2) * q()).size(.7).play(crowd);
                if (tier >= 1) kit(t, RING, feet).radius(ground * .9).count(.6 * q()).play(crowd);
                if (tier >= 2) {
                    shot("sigil_b", t, feet.clone().add(0, .03, 0)).flat().size(ground * .2, ground * .62).grow(.25).life(life)
                            .spin(-90).fade(2, 8).collapse().play(crowd);
                    kit(t, RISE, feet).radius(1.1).height(2.6).seconds(.7).count(.8 * q()).play(crowd);
                }
                if (tier >= 3) {
                    shot("circle", t, feet.clone().add(0, 5.5, 0)).flat().size(.5, ground * .8).grow(.3).life(life + 6).spin(35)
                            .fade(4, 10).collapse().play(crowd);
                    shot("beam", t, feet.clone().add(0, 2.8, 0)).vertical().size(1.1).aspect(5).life(14).fade(2, 6).play(crowd);
                }
            });
            emit("startup", staff, 8, f -> {
                if (f.age % 2 != 0) return;
                double r = .55 * (1 - f.age / 10.0);
                for (int i = 0; i < 8; i++) {
                    double a = i * TAU / 8 + f.age * .4;
                    f.dust(staff.clone().add(side.clone().multiply(Math.cos(a) * r)).add(0, Math.sin(a) * r, 0), i % 2 == 0 ? main() : accent(), .9f, 1, 0);
                }
                if (f.age == 0) f.shared(() -> f.element(staff, 4, .14));
            });
            sound(staff, activation(spell.element()), tier >= 2 ? .85f : 1.2f);
            if (tier >= 1) layer(staff, Sound.ENTITY_ILLUSIONER_CAST_SPELL, .45f, 1.35f - tier * .12f);
            if (tier >= 2) layer(feet, Sound.BLOCK_BEACON_POWER_SELECT, .4f, 1.6f);
            if (tier >= 3) layer(feet, Sound.ENTITY_EVOKER_PREPARE_SUMMON, .55f, .9f);
        }

        public void projectile(Location from, Location to) {
            if (tick - lastProjectile < 1) return;
            lastProjectile = tick;
            Location start = from.clone(), end = to.clone();
            Vector direction = unit(end.toVector().subtract(start.toVector()));
            boolean fireball = spell == ElementalSpell.FIREBALL;
            safe(() -> kit(tint(), TRAIL, end).count((fireball ? 1.7 : 1.1) * q()).size(fireball ? 1.4 : 1).play(crowd(end)));
            emit("projectile", end, 3, f -> {
                double turn = tick * .45, pulse = 1 + .14 * Math.sin(tick * .9);
                f.billboard("head", "glow", tint(), end, (fireball ? 1.7 : 1.2) * pulse);
                if (tick % 2 == 0) f.ribbon(start, end, main(), accent(), turn);
                switch (spell) {
                    case FIREBALL -> { f.p(Particle.FLAME, end, 4, .14, .01); f.p(Particle.SMALL_FLAME, start, 2, .08, 0); if (tick % 4 == 0) f.p(Particle.LAVA, end, 1, .05, 0); }
                    case AQUA_BULLET -> {
                        f.cube("core", end, Material.LIGHT_BLUE_STAINED_GLASS, .34, .34, .34, spin(turn, turn * .7, 0), 1);
                        f.p(Particle.SPLASH, end, 3, .12, 0); f.p(Particle.BUBBLE_POP, start, 1, .05, 0);
                    }
                    case FROST_BOLT -> {
                        f.cube("core", end, Material.PACKED_ICE, .15, .6, .15, along(direction), 1);
                        f.p(Particle.SNOWFLAKE, start, 3, .07, 0);
                    }
                    case STONE_BULLET -> {
                        f.cube("core", end, Material.COBBLESTONE, .44, .44, .44, spin(turn, turn * 1.3, turn * .4), 1);
                        f.p(Particle.FALLING_DUST, start, 2, .12, .02);
                    }
                    case RADIANT_BOLT -> {
                        f.cube("core", end, Material.GLOWSTONE, .24, .24, .24, spin(turn, Math.PI / 4, Math.PI / 4), 1);
                        f.p(Particle.END_ROD, start, 2, .05, .005);
                    }
                    case SHADOW_BOLT -> {
                        f.cube("core", end, Material.CRYING_OBSIDIAN, .28, .28, .28, spin(-turn, turn * .8, 0), 1);
                        f.p(Particle.SOUL, start, 1, .05, 0); f.p(Particle.REVERSE_PORTAL, end, 3, .1, .01);
                    }
                    case SPARK_SHOT -> { f.bolt(start, end, .15, 5); f.p(Particle.ELECTRIC_SPARK, end, 3, .1, .02); }
                    default -> f.element(end, 3, .08);
                }
            });
        }

        public void beam(Location from, Location to) {
            Location a = from.clone(), b = to.clone();
            Vector span = b.toVector().subtract(a.toVector()), dir = unit(span), r = right(dir);
            Vector up = unit(dir.clone().crossProduct(r));
            double length = span.length();
            String key = "beam:" + a.getBlockX() + ":" + a.getBlockY() + ":" + a.getBlockZ()
                    + ":" + b.getBlockX() + ":" + b.getBlockY() + ":" + b.getBlockZ();
            String t = spell == ElementalSpell.LIFE_DRAIN ? "dark" : tint();
            if (opening(key)) safe(() -> {
                List<Player> crowd = crowd(a);
                double width = switch (spell) { case WATER_BLADE, WIND_CUTTER, THOUSAND_WIND_BLADES, TIDAL_PUSH -> 2.4; case INFERNO_SPEAR, THUNDER_SPEAR -> 1.2; default -> .8; };
                // One Bedrock emitter scatters motes along the whole path; Java viewers get the same motes.
                kit(t, LINE, a).to(b).count(Math.clamp(length / 2.5, .5, 6) * q()).width(width).play(crowd);
                switch (spell) {
                    case LIGHTNING_CHAIN, THUNDER_SPEAR, THUNDER_DASH -> {
                        kit("lightning", BURST, b).radius(.9).count(.7 * q()).play(crowd);
                        shot("burst", "lightning", b).size(.5, spell == ElementalSpell.THUNDER_SPEAR ? 3 : 2.3).grow(.3).life(8)
                                .rotation(random(0, 360)).fade(1, 4).collapse().play(crowd);
                        shot("glow", "lightning", b).size(2.6, .3).life(6).fade(1, 4).play(crowd);
                        shot("bolt_" + "abc".charAt(ThreadLocalRandom.current().nextInt(3)), "lightning", b.clone().add(0, 2.4, 0))
                                .vertical().size(5).life(5).fade(1, 3).play(crowd);
                    }
                    case HELLFIRE_CHAIN -> {
                        kit("fire", BURST, b).radius(1).count(.7 * q()).play(crowd);
                        shot("burst", "fire", b).size(.5, 2.4).grow(.35).life(9).rotation(random(0, 360)).fade(1, 4).collapse().play(crowd);
                    }
                    case LIFE_DRAIN -> {
                        shot("glow", "dark", a).size(2, .4).life(10).fade(1, 5).play(crowd);
                        kit("heal", BURST, b.clone().add(0, -.4, 0)).radius(.6).count(.5 * q()).play(crowd);
                    }
                    case FLAME_DASH, GALE_STEP, GLACIAL_PATH -> {
                        Location foot = a.clone().add(0, -.75, 0);
                        shot("ring", t, foot).flat().size(.6, 4.2).life(10).fade(1, 5).play(crowd);
                        shot("glow", t, a).size(2.4, .3).life(8).fade(1, 4).play(crowd);
                        shot("glow", t, b).size(.3, 2.2).grow(.4).life(8).fade(1, 4).collapse().play(crowd);
                        kit(t, BURST, b).radius(.9).count(.6 * q()).play(crowd);
                        kit(t, RING, foot).radius(2.4).count(.6 * q()).play(crowd);
                    }
                    default -> { }
                }
            });
            emit(key, a, 8, f -> {
                switch (spell) {
                    case LIGHTNING_CHAIN, THUNDER_SPEAR, THUNDER_DASH -> {
                        if (f.age < 4) f.bolt(a, b, spell == ElementalSpell.THUNDER_SPEAR ? .34 : .2, 14);
                        if (f.age < 3 && spell == ElementalSpell.THUNDER_SPEAR)
                            f.cube("lance", a.clone().add(span.clone().multiply(.5)), Material.YELLOW_STAINED_GLASS, .16 * (1 - f.age / 4.0), length, .16 * (1 - f.age / 4.0), along(dir), 2);
                        if (f.age < 6) f.billboard("hit", "glow", "lightning", b, 2.4 * (1 - f.age / 7.0));
                        if (f.age == 0) f.p(Particle.ELECTRIC_SPARK, b, 10, .35, .06);
                    }
                    case WATER_BLADE, WIND_CUTTER, THOUSAND_WIND_BLADES -> {
                        boolean water = spell.element() == Element.WATER;
                        double t2 = Math.min(1, .2 + f.age * .16);
                        Location center = a.clone().add(span.clone().multiply(t2));
                        // The crescent stands across the flight path and sweeps forward with it; a glow rides its edge.
                        f.upright("blade", "slash", tint(), center, r, water ? 2.8 : 2.4, 1.4, Math.PI);
                        f.billboard("edge", "glow", tint(), center.clone().add(0, .55, 0), 1.1);
                        if (f.age % 2 == 0) for (int i = 0; i < 9; i++) {
                            double angle = -1 + i * 2.0 / 8;
                            Location point = center.clone().add(r.clone().multiply(Math.sin(angle) * 1.2)).add(dir.clone().multiply(Math.cos(angle) * .5 - .8));
                            f.dust(point, i % 3 == 0 ? accent() : main(), 1f, 1, .02);
                        }
                        if (water && f.age % 2 == 0) f.p(Particle.SPLASH, center, 4, .3, 0);
                        if (!water && f.age % 2 == 0) f.p(Particle.CLOUD, center, 2, .2, .02);
                    }
                    case HELLFIRE_CHAIN -> {
                        if (f.age < 4) {
                            int points = Math.min(24, Math.max(8, (int) (length * 3)));
                            for (int i = 0; i < points; i++) {
                                double t2 = i / (double) (points - 1), angle = i * 1.9 + f.age;
                                f.p(Particle.FLAME, a.clone().add(span.clone().multiply(t2)).add(r.clone().multiply(Math.cos(angle) * .22)).add(up.clone().multiply(Math.sin(angle) * .22)), 1, .01, 0);
                            }
                        }
                        if (f.age < 6) f.billboard("hit", "glow", "fire", b, 2 * (1 - f.age / 7.0));
                    }
                    case INFERNO_SPEAR -> {
                        double t2 = Math.min(1, (f.age + 1) / 3.0);
                        Location head = a.clone().add(span.clone().multiply(t2));
                        f.cube("spear", head.clone().subtract(dir.clone().multiply(.8)), Material.MAGMA_BLOCK, .17, 1.6, .17, along(dir), 2);
                        f.cube("tip", head, Material.SHROOMLIGHT, .26, .26, .26, along(dir).rotateY((float) (f.age * .9)), 2);
                        f.billboard("tip", "glow", "fire", head, 1.9);
                        if (f.age < 4) f.line(a, head, Particle.FLAME, 16, .05);
                        if (f.age == 3) f.arrive(b, "fire", 1.4);
                        if (f.age >= 3) f.p(Particle.LAVA, b, 1, .2, 0);
                    }
                    case LIFE_DRAIN -> {
                        if (f.age % 2 == 0) f.line(a, b, Particle.SOUL, 10, .04);
                        for (int i = 0; i < 3; i++) {
                            double t2 = ((f.age + i * 2.3) % 7) / 6.0;
                            f.dust(a.clone().add(span.clone().multiply(t2)), RUBY, 1.4f, 2, .06);
                        }
                        f.billboard("source", "glow", "dark", a, 1.4 * (1 - f.age / 9.0));
                    }
                    case FLAME_DASH -> { if (f.age < 4) { f.line(a, b, Particle.FLAME, 16, .1); f.dustLine(a, b, CRIMSON, 1.1f, 10); } }
                    case GALE_STEP -> { if (f.age < 4) { f.dustLine(a, b, WHITE, 1.2f, 14); f.dustLine(a.clone().add(0, .4, 0), b.clone().add(0, .4, 0), EMERALD, .9f, 10); f.line(a, b, Particle.CLOUD, 6, .1); } }
                    case GLACIAL_PATH -> { if (f.age < 4) { f.line(a, b, Particle.SNOWFLAKE, 12, .15); f.dustLine(a, b, FROST, 1.1f, 12); } }
                    case METEOR_RAIN -> f.line(a, b, Particle.FLAME, 12, .15);
                    default -> { if (f.age < 4) { f.dustLine(a, b, main(), 1.1f, 14); f.line(a, b, particle(spell.element()), 6, .04); } }
                }
            });
        }

        public void area(Location at, double radius) {
            Location base = at.clone().add(0, .08, 0);
            base.setYaw(0); base.setPitch(0);
            String key = "area:" + at.getBlockX() + ":" + at.getBlockY() + ":" + at.getBlockZ();
            int life = grand() ? 20 : 14;
            String t = tint();
            if (opening(key)) safe(() -> {
                List<Player> crowd = crowd(base);
                // Every blast: a shockwave ring to the real radius, a scorch/crack decal, a bloom and an element burst.
                shot("ring", t, base.clone().add(0, .05, 0)).flat().size(radius * .4, radius * 2.3).life(11).fade(1, 6).play(crowd);
                shot("burst", t, base.clone().add(0, .03, 0)).flat().size(radius * .6, radius * 2.1).grow(.15).life(grand() ? 40 : 28)
                        .rotation(random(0, 360)).fade(1, 14).play(crowd);
                shot("glow", t, base.clone().add(0, .9, 0)).size(radius * 1.5, radius * .2).life(7).fade(1, 4).play(crowd);
                shot("burst", t, base.clone().add(0, 1, 0)).size(.5, radius * 1.7).grow(.35).life(10).spin(random(-120, 120))
                        .rotation(random(0, 360)).fade(1, 4).collapse().play(crowd);
                kit(t, BURST, base.clone().add(0, .4, 0)).radius(radius).count((grand() ? 1.3 : 1) * q()).play(crowd);
                kit(t, RING, base).radius(radius).count(q()).play(crowd);
                layer(base, boom(spell.element()), grand() ? .8f : .55f, .9f);
            });
            emit(key, base, life, f -> {
                double t2 = Math.min(1, (f.age + 2) / (double) life), fade = 1 - t2;
                switch (spell) {
                    case FIREBALL, PHOENIX_REBIRTH -> {
                        f.ejecta(base, Material.MAGMA_BLOCK, 5, radius * .85, 1.5, .24, t2, .6);
                        if (f.age == 0) f.p(Particle.FLAME, base, 16, radius * .3, .05);
                        if (f.age % 4 == 0) f.p(Particle.LAVA, base, 2, .3, 0);
                    }
                    case FLAME_BURST -> {
                        if (f.age % 2 == 0) f.ring(base.clone().add(0, .3, 0), radius * ease(t2) * .9, Particle.FLAME, 20, t2);
                        f.ejecta(base, Material.MAGMA_BLOCK, 6, radius, 1.8, .26, t2, 0);
                        // Flame pillars flare at eight points of the nova.
                        if (f.age < 8) for (int i = 0; i < 8; i++) {
                            double a = i * TAU / 8;
                            f.p(Particle.FLAME, base.clone().add(Math.cos(a) * radius * .7, .2 + f.age * .25, Math.sin(a) * radius * .7), 2, .08, .01);
                        }
                    }
                    case MAGMA_ERUPTION -> {
                        if (f.age % 2 == 0) f.fountain(base, radius * .55, 4.6 * Math.sin(t2 * Math.PI), Particle.FLAME, 20);
                        f.ejecta(base, Material.MAGMA_BLOCK, 7, radius * .9, 3.6, .34, t2, .3);
                        f.billboard("plume", "glow", "fire", base.clone().add(0, 1.2 + 2.4 * t2, 0), 3 * fade + .5);
                        if (f.age % 3 == 0) f.p(Particle.LAVA, base.clone().add(0, .3, 0), 3, radius * .35, .02);
                        if (f.age == 0) f.arrive(base.clone().add(0, 2.2, 0), "fire", radius * .9);
                    }
                    case ICE_SPIKE, EARTH_SPIKE -> {
                        boolean ice = spell.element() == Element.ICE;
                        f.spikes(base, radius * .7, ice, 6, Math.sin(Math.min(1, t2 * 1.4) * Math.PI * .5) * (t2 > .75 ? (1 - t2) * 4 : 1));
                        if (f.age == 0) f.debris(base, radius * .6, ice ? Material.PACKED_ICE : Material.TUFF, ice);
                    }
                    case SEISMIC_SLAM, TITANS_JUDGMENT -> {
                        for (int i = 0; i < 6; i++) {
                            double a = i * TAU / 6 + .3, rise = Math.sin(Math.min(1, t2 * 1.6) * Math.PI);
                            Location foot = base.clone().add(Math.cos(a) * radius * .75 * ease(t2), -.2, Math.sin(a) * radius * .75 * ease(t2));
                            f.block("slab" + i, foot, i % 2 == 0 ? Material.DEEPSLATE : Material.COBBLESTONE, .8, .9 * rise + .05, .55, -a, i % 2 == 0 ? .35 : -.3);
                        }
                        if (f.age == 0) f.debris(base, radius * .7, Material.STONE, false);
                    }
                    case UPDRAFT -> {
                        if (f.age == 0) f.kitHere(base, "wind", SWIRL, radius * .65, 4.5, .8, 1.2);
                        if (f.age % 2 == 0) {
                            f.helixDust(base, radius * .6, 4.5 * ease(t2), f.age * .6, WHITE, 16, 3);
                            f.helixDust(base, radius * .45, 3.5 * ease(t2), -f.age * .5, EMERALD, 10, 2);
                        }
                    }
                    case GALE_STEP -> { if (f.age % 2 == 0) f.helixDust(base, 1.3 * fade, 2.2, f.age * .8, WHITE, 12, 2); }
                    case BUBBLE_PRISON -> {
                        f.billboard("bubble", "ring", "water", base.clone().add(0, 1, 0), (1.6 + radius * 1.4 * ease(t2)) * (f.age < 10 ? 1 : fade * 2));
                        if (f.age == 0) { f.p(Particle.BUBBLE_POP, base.clone().add(0, 1, 0), 16, .7, .05); f.p(Particle.SPLASH, base.clone().add(0, 1, 0), 16, .7, .05); }
                    }
                    case CRYSTAL_PRISON -> {
                        f.ejecta(base.clone().add(0, .6, 0), Material.PACKED_ICE, 7, radius, 1.4, .24, t2, 0);
                        if (f.age == 0) f.p(Particle.SNOWFLAKE, base.clone().add(0, 1, 0), 24, radius * .4, .06);
                    }
                    case CURSED_MARK -> {
                        f.flat("seal", "circle", "dark", base.clone().add(0, .06, 0), radius * 2.2 * (.7 + .3 * ease(t2 * 2)), f.age * .06, .02);
                        if (f.age % 3 == 0) f.p(Particle.SOUL, base.clone().add(0, .4, 0), 3, radius * .4, .02);
                        if (f.age == 0) f.p(Particle.REVERSE_PORTAL, base.clone().add(0, 1, 0), 20, .8, .05);
                    }
                    case GOLEM_GUARDIAN -> { if (f.age == 0) f.debris(base, .9, Material.IRON_BLOCK, false); }
                    case DIVINE_JUDGMENT -> { if (f.age == 0) f.p(Particle.END_ROD, base.clone().add(0, .4, 0), 20, radius * .3, .08); }
                    case ABSOLUTE_ZERO -> {
                        if (f.age % 2 == 0) f.sphere(base.clone().add(0, .8, 0), radius * ease(t2), Particle.SNOWFLAKE, 24, f.age);
                        f.spikes(base, radius * .65 * ease(t2), true, 6, Math.sin(t2 * Math.PI));
                    }
                    case ABYSSAL_COLLAPSE -> { if (f.age % 2 == 0) f.sphere(base.clone().add(0, 1.1, 0), radius * fade, Particle.REVERSE_PORTAL, 24, f.age); }
                    default -> { if (f.age % 2 == 0) f.fountain(base, radius * .5 * t2, fade * 1.9, particle(spell.element()), 10); }
                }
            });
            if (grand()) sound(base, activation(spell.element()), .7f);
        }

        /** Tidal Push: a breaking wave of glass crests rolling over the cone behind a foam line. */
        public void cone(Location origin, Vector direction, double radius, double cosine) {
            Location base = origin.clone().add(0, .1, 0);
            base.setYaw(0); base.setPitch(0);
            Vector dir = unit(direction), r = right(dir);
            double half = Math.acos(Math.clamp(cosine, -1, 1));
            if (opening("cone")) safe(() -> {
                List<Player> crowd = crowd(base);
                kit(tint(), RING, base).radius(radius * .9).count(1.2 * q()).play(crowd);
                kit(tint(), BURST, base.clone().add(dir.clone().multiply(radius * .8)).add(0, .6, 0)).radius(1.6).count(q()).play(crowd);
                shot("ring", tint(), base).flat().size(1, radius * 2).life(12).fade(1, 6).play(crowd);
            });
            emit("cone", base, 10, f -> {
                double t = (f.age + 1) / 10.0, reach = radius * ease(t * 1.2);
                for (int i = 0; i < 5; i++) {
                    double angle = -half + half * 2 * i / 4, crest = 1.7 * Math.sin(Math.min(1, t * 1.3) * Math.PI) + .2;
                    Vector heading = dir.clone().multiply(Math.cos(angle)).add(r.clone().multiply(Math.sin(angle)));
                    Location foot = base.clone().add(heading.clone().multiply(reach));
                    if (spell.element() == Element.WATER) {
                        f.lean("crest" + i, foot, Material.LIGHT_BLUE_STAINED_GLASS, 1.4, crest, .35, Math.atan2(heading.getX(), heading.getZ()), .35);
                        if (f.age % 2 == 0) f.p(Particle.SPLASH, foot.clone().add(0, crest, 0), 5, .25, .02);
                    } else f.dust(foot.clone().add(0, .4, 0), main(), 1.1f, 3, .15);
                }
                if (f.age % 2 == 0) for (int i = 0; i < 13; i++) {
                    double angle = -half + half * 2 * i / 12;
                    f.dust(base.clone().add(dir.clone().multiply(Math.cos(angle) * reach)).add(r.clone().multiply(Math.sin(angle) * reach)), i % 2 == 0 ? WHITE : CYAN, 1.2f, 1, .05);
                }
                f.upright("front", "slash", tint(), base.clone().add(dir.clone().multiply(reach * .9)).add(0, .9, 0), r, reach * 1.6, 1.7, Math.PI);
            });
        }

        public void boundary(Location at, double radius) {
            if (tick % 4 != 0) return;
            Location base = at.clone().add(0, .12, 0);
            emit("boundary:" + at.getBlockX() + ":" + at.getBlockZ(), base, 5,
                    f -> { if (f.age == 0) f.dustRing(base, radius, main(), .9f, low() ? 8 : 16, tick * .02); });
        }

        public void impact(LivingEntity target) {
            Location at = target.getLocation().add(0, Math.min(1.3, target.getHeight() * .55), 0);
            String key = "hit:" + target.getUniqueId();
            Clip old = clips.get(key);
            if (old != null && tick - old.start() < 3) return;
            String t = tint();
            safe(() -> {
                List<Player> crowd = crowd(at);
                shot("burst", t, at).size(.4, grand() ? 3 : 2.2).grow(.35).life(9).spin(random(-160, 160)).rotation(random(0, 360))
                        .fade(1, 4).collapse().play(crowd);
                shot("glow", t, at).size(1.9, .2).life(6).fade(1, 4).play(crowd);
                kit(t, BURST, at).radius(.9).count(.65 * q()).size(.9).play(crowd);
            });
            emit(key, at, 6, f -> {
                if (f.age != 0) return;
                f.element(at, 6, .22);
                switch (spell.element()) {
                    case FIRE -> f.p(Particle.LAVA, at, 2, .1, 0);
                    case WATER -> f.p(Particle.BUBBLE_POP, at, 4, .2, .02);
                    case LIGHTNING -> f.p(Particle.ELECTRIC_SPARK, at, 6, .25, .05);
                    case LIGHT -> f.p(Particle.GLOW, at, 3, .2, .02);
                    case DARK -> f.p(Particle.SQUID_INK, at, 3, .2, .02);
                    default -> { }
                }
            });
        }

        public void heal(Player target) {
            String key = "heal:" + target.getUniqueId();
            Location feet = target.getLocation().add(0, .07, 0);
            Clip old = clips.get(key);
            if (old == null || tick - old.start() > 10) safe(() -> {
                List<Player> crowd = crowd(feet);
                shot("circle", "heal", feet).flat().size(.3, 1.9).grow(.25).life(16).spin(100).fade(1, 6).collapse().play(crowd);
                kit("heal", BURST, feet.clone().add(0, 1, 0)).radius(.7).count(.6 * q()).play(crowd);
                kit("heal", RISE, feet).radius(.5).height(2.1).seconds(.5).count(.7 * q()).play(crowd);
            });
            emit(key, target.getLocation(), 12, f -> {
                if (!target.isOnline() || target.getWorld() != world || target.isDead()) return;
                Location at = target.getLocation();
                if (f.age % 2 == 0) f.helixDust(at, .55, 2.1, f.age * .45, EMERALD, 8, 1);
                if (f.age % 4 == 0) f.p(Particle.HAPPY_VILLAGER, at.clone().add(0, .4 + f.age * .12, 0), 2, .3, 0);
                if (f.age == 0) f.p(Particle.HEART, at.clone().add(0, 2.1, 0), 2, .2, 0);
            });
        }

        /** The burst of a team buff: its element circle opens to the buff's reach while a column of light rises on the caster. */
        public void aura(Location center, double radius) {
            Location base = center.clone().add(0, .1, 0);
            base.setYaw(0); base.setPitch(0);
            String circle = spell == ElementalSpell.PURIFICATION ? "light" : spell == ElementalSpell.AQUA_SHIELD ? "water" : tint();
            safe(() -> {
                List<Player> crowd = crowd(base);
                shot("circle", circle, base).flat().size(.4, radius * 2.1).grow(.2).life(30).spin(60).fade(3, 10).collapse().play(crowd);
                shot("ring", circle, base.clone().add(0, .03, 0)).flat().size(.5, radius * 2.4).life(14).fade(1, 6).play(crowd);
                shot("beam", circle, base.clone().add(0, 2.6, 0)).vertical().size(1.4).aspect(3.6).life(14).fade(2, 6).play(crowd);
                kit(circle, RING, base).radius(radius).count(q()).play(crowd);
                kit(circle, RISE, base).radius(1.2).height(3).seconds(1).count(q()).play(crowd);
            });
            emit("aura", base, 22, f -> {
                double t = (f.age + 1) / 22.0, open = ease(t * 1.6);
                if (f.age % 2 == 0) f.helixDust(base, 1.2, 2.8 * ease(t * 1.5), f.age * .5, main(), 12, 2);
                switch (spell) {
                    case AQUA_SHIELD -> { if (f.age % 3 == 0) f.p(Particle.SPLASH, base.clone().add(0, 1.2, 0), 5, .6, .02); }
                    case FROZEN_ARMOR -> { if (f.age % 3 == 0) f.p(Particle.SNOWFLAKE, base.clone().add(0, 1, 0), 4, open * radius * .3, .01); f.ejecta(base.clone().add(0, .3, 0), Material.PACKED_ICE, 5, radius * .6, 1, .2, t, 0); }
                    case WIND_BARRIER -> { if (f.age % 2 == 0) f.helixDust(base, radius * .55 * open, 1.8, -f.age * .7, WHITE, 14, 3); }
                    case IRON_SKIN -> { if (f.age == 0) f.debris(base, 1.4, Material.IRON_BLOCK, false); if (f.age % 3 == 0) f.p(Particle.SCRAPE, base.clone().add(0, 1, 0), 4, .6, .02); }
                    case PURIFICATION -> { if (f.age % 2 == 0) for (int i = 0; i < 6; i++) { double a = i * TAU / 6 + t; f.p(Particle.END_ROD, base.clone().add(Math.cos(a) * radius * .7 * open, 2.4 * (1 - t), Math.sin(a) * radius * .7 * open), 1, .02, 0); } }
                    default -> { }
                }
            });
            sound(base, spell.element() == Element.WATER ? Sound.BLOCK_BEACON_ACTIVATE : activation(spell.element()), 1.3f);
        }

        /** A lasting floor lane: magma tiles under a fire stream for Flame Dash, frozen tiles for Glacial Path. */
        public void trail(Location from, Location to, int age, int duration) {
            if (age % 2 != 0) return;
            Location a = from.clone().add(0, -.42, 0), b = to.clone().add(0, -.42, 0);
            Vector span = b.toVector().subtract(a.toVector());
            double length = span.length(), yaw = Math.atan2(span.getX(), span.getZ());
            boolean fire = spell.element() == Element.FIRE;
            if (opening("trail")) safe(() -> {
                Location mid = a.clone().add(span.clone().multiply(.5)).add(0, .1, 0);
                kit(fire ? "fire" : "ice", RISE, mid).radius(Math.max(1, length * .45)).height(fire ? 1.1 : .8)
                        .seconds(Math.max(1, (duration - age) / 20.0)).count(.6 * q()).play(crowd(mid));
            });
            emit("trail", a, 4, f -> {
                int time = age + f.age;
                double fade = time > duration - 16 ? Math.max(.05, (duration - time) / 16.0) : 1;
                int tiles = Math.max(2, Math.min(5, (int) Math.round(length / 2)));
                for (int i = 0; i < tiles; i++) {
                    double t = (i + .5) / tiles, grow = Math.min(1, (time + 1) / (2.0 + i * 2));
                    Location foot = a.clone().add(span.clone().multiply(t));
                    double w = (fire ? .8 : 1.3) * grow * fade, d = length / tiles * .97 * grow;
                    f.block("tile" + i, foot, fire ? Material.MAGMA_BLOCK : Material.PACKED_ICE, w, .07, d, yaw, 0);
                    if (time % 4 == 0) {
                        if (fire) f.p(Particle.FLAME, foot.clone().add(0, .3, 0), 2, .25, .01);
                        else f.p(Particle.SNOWFLAKE, foot.clone().add(0, .3, 0), 2, .35, 0);
                    }
                }
                Location runner = a.clone().add(span.clone().multiply((time % 20) / 20.0)).add(0, .5, 0);
                f.billboard("runner", "glow", fire ? "fire" : "ice", runner, (fire ? 1.5 : 1.2) * fade);
            });
        }

        public void field(Location center, double radius, int age, int duration) {
            if (age % 2 != 0) return;
            Location base = center.clone().add(0, .12, 0);
            base.setYaw(0); base.setPitch(0);
            String circleTint = kindTint();
            if (opening("field")) safe(() -> {
                List<Player> crowd = crowd(base);
                int life = Math.max(10, duration - age);
                double seconds = life / 20.0, grow = Math.min(1, 10.0 / life);
                double turn = switch (spell) { case WHIRLPOOL, CYCLONE_TRAP, TEMPEST_DOMINION, SCORCHING_VORTEX -> 110; case QUICKSAND -> -60; default -> 28; };
                // One spinning circle and one counter-rotating sigil for the field's whole life.
                shot("circle", circleTint, base.clone().add(0, .03, 0)).flat().size(radius * .4, radius * 2.15).grow(grow).life(life + 2)
                        .spin(turn).fade(4, 10).collapse().play(crowd);
                shot("sigil_b", circleTint, base.clone().add(0, .05, 0)).flat().size(radius * .3, radius * 1.25).grow(grow).life(life)
                        .spin(-turn * 1.6).fade(4, 10).alpha(.75).collapse().play(crowd);
                shot("ring", circleTint, base.clone().add(0, .04, 0)).flat().size(radius * .3, radius * 2.4).life(12).fade(1, 6).play(crowd);
                switch (spell) {
                    case BURNING_GROUND -> {
                        kit("fire", RISE, base).radius(radius * .9).height(1.4).seconds(seconds).count(.9 * q()).play(crowd);
                        kit("fire", RING, base).radius(radius).count(.8 * q()).play(crowd);
                    }
                    case HEALING_SPRING -> {
                        kit("water", RISE, base).radius(.45).height(2.4).seconds(seconds).count(.7 * q()).play(crowd);
                        kit("heal", RISE, base).radius(radius * .8).height(2.2).seconds(seconds).count(.6 * q()).play(crowd);
                    }
                    case RAIN_BLESSING -> {
                        kit("water", RAIN, base).radius(radius).height(4.4).seconds(seconds).count(.8 * q()).play(crowd);
                        kit("heal", RISE, base).radius(radius * .7).height(2).seconds(seconds).count(.5 * q()).play(crowd);
                    }
                    case WHIRLPOOL -> kit("water", SWIRL, base).radius(radius).height(.3).seconds(seconds).count(q()).drain().play(crowd);
                    case SCORCHING_VORTEX -> {
                        kit("fire", SWIRL, base).radius(radius * .75).height(4.6).seconds(seconds).count(1.1 * q()).play(crowd);
                        kit("fire", RISE, base).radius(radius * .5).height(1.2).seconds(seconds).count(.5 * q()).play(crowd);
                    }
                    case CYCLONE_TRAP, TEMPEST_DOMINION -> kit("wind", SWIRL, base).radius(radius * .8).height(spell.ultimate() ? 7 : 4.4)
                            .seconds(seconds).count((spell.ultimate() ? 1.4 : 1.1) * q()).play(crowd);
                    case BLIZZARD -> {
                        kit("ice", RAIN, base).radius(radius).height(5).seconds(seconds).count(q()).play(crowd);
                        kit("ice", SWIRL, base).radius(radius * .85).height(3).seconds(seconds).count(.7 * q()).play(crowd);
                    }
                    case QUICKSAND -> kit("earth", SWIRL, base).radius(radius).height(.3).seconds(seconds).count(.9 * q()).drain().play(crowd);
                    case HOLY_SANCTUARY -> {
                        kit("light", RISE, base).radius(radius * .9).height(3).seconds(seconds).count(.8 * q()).play(crowd);
                        kit("light", RAIN, base).radius(radius * .8).height(4).seconds(seconds).count(.4 * q()).play(crowd);
                        shot("beam", "light", base.clone().add(0, 3, 0)).vertical().size(2).aspect(3).life(Math.min(life, 30)).fade(4, 10).alpha(.8).play(crowd);
                    }
                    case STATIC_FIELD -> kit("lightning", RISE, base).radius(radius).height(1.6).seconds(seconds).count(q()).play(crowd);
                    default -> kit(tint(), RISE, base).radius(radius * .7).height(2).seconds(seconds).count(.6 * q()).play(crowd);
                }
            });
            emit("field", base, 4, f -> {
                int time = age + f.age;
                double fade = time > duration - 10 ? Math.max(.1, (duration - time) / 10.0) : 1;
                boolean dusty = time % 4 == 0;
                switch (spell) {
                    case BURNING_GROUND -> {
                        for (int i = 0; i < 4; i++) {
                            double angle = i * TAU / 4 + .4, r = radius * .5;
                            double pulse = (.6 + .12 * Math.sin(time * .2 + i)) * fade;
                            f.block("patch" + i, base.clone().add(Math.cos(angle) * r, -.5, Math.sin(angle) * r), Material.MAGMA_BLOCK, pulse, .06, pulse, angle, 0);
                        }
                        if (dusty) for (int i = 0; i < 8; i++) {
                            double angle = i * 2.399 + time * .025, r = radius * Math.sqrt((i + .5) / 8);
                            f.p(Particle.FLAME, base.clone().add(Math.cos(angle) * r, .15, Math.sin(angle) * r), 1, .1, .01);
                        }
                    }
                    case HEALING_SPRING -> {
                        f.cube("core", base.clone().add(0, .6 + .1 * Math.sin(time * .15), 0), Material.SEA_LANTERN, .38 * fade, .38 * fade, .38 * fade, spin(time * .08, Math.PI / 4, Math.PI / 4), 2);
                        f.billboard("glow", "glow", "heal", base.clone().add(0, .6, 0), 2 * fade);
                        if (dusty) f.fountain(base, radius * .3, 1.6, Particle.SPLASH, 10);
                    }
                    case RAIN_BLESSING -> {
                        Location cloud = base.clone().add(0, 4.2, 0);
                        f.flat("cloud", "circle", "water", cloud, radius * 1.4 * ease((time + 2) / 10.0) * fade, -time * .03, -.03);
                        if (dusty) f.rain(base, radius, 4.1, time, Particle.DRIPPING_WATER, 14);
                    }
                    case WHIRLPOOL -> {
                        f.cube("eye", base.clone().add(0, .3, 0), Material.LIGHT_BLUE_STAINED_GLASS, .9 * fade, .12, .9 * fade, spin(-time * .2, 0, 0), 2);
                        if (dusty) { f.ring(base.clone().add(0, .15, 0), radius * .55, Particle.SPLASH, 10, -time * .12); f.p(Particle.CURRENT_DOWN, base, 3, .2, .01); }
                    }
                    case SCORCHING_VORTEX -> {
                        for (int i = 0; i < 3; i++) {
                            double a = time * .3 + i * TAU / 3, h = ((time * .04 + i * .33) % 1) * 3.8;
                            f.cube("ember" + i, base.clone().add(Math.cos(a) * radius * .5 * (1 - h / 5), h, Math.sin(a) * radius * .5 * (1 - h / 5)), Material.MAGMA_BLOCK, .24, .24, .24, spin(a, a, 0), 2);
                        }
                        f.billboard("core", "glow", "fire", base.clone().add(0, 2, 0), 2.6 * fade);
                    }
                    case CYCLONE_TRAP, TEMPEST_DOMINION -> {
                        double height = spell.ultimate() ? 7 : 4.4;
                        f.upright("funnel", "slash", "wind", base.clone().add(0, height * .5, 0), right(new Vector(Math.cos(time * .25), 0, Math.sin(time * .25))), radius * 1.6, height * .7, 0);
                        if (dusty) f.helixDust(base, radius * .5, height * .8, time * .25 + 1.4, EMERALD, 12, 2);
                    }
                    case BLIZZARD -> {
                        for (int i = 0; i < 3; i++) {
                            double a = i * 2.1 + time * .05, h = 4 - ((time * .18 + i * 1.3) % 4);
                            f.cube("hail" + i, base.clone().add(Math.cos(a) * radius * .55, h, Math.sin(a) * radius * .55), Material.PACKED_ICE, .24, .24, .24, spin(time * .3, i, 0), 2);
                        }
                    }
                    case QUICKSAND -> {
                        f.block("sand", base.clone().add(0, -.5, 0), Material.SAND, radius * 1.6 * fade, .05, radius * 1.6 * fade, -time * .05, 0);
                        if (dusty) f.dustRing(base, radius, SAND_DUST, 1.2f, 14, -time * .05);
                    }
                    case HOLY_SANCTUARY -> {
                        for (int i = 0; i < 3; i++) {
                            double a = time * .06 + i * TAU / 3;
                            f.cube("orb" + i, base.clone().add(Math.cos(a) * radius * .55, 1.4 + .3 * Math.sin(time * .1 + i), Math.sin(a) * radius * .55), Material.GLOWSTONE, .24, .24, .24, spin(a, Math.PI / 4, Math.PI / 4), 2);
                        }
                        if (dusty) for (int arc = 0; arc < 4; arc++) {
                            double a = arc * Math.PI / 4 + time * .01;
                            for (int i = 1; i < 6; i++) {
                                double phi = i / 6.0 * Math.PI;
                                f.dust(base.clone().add(Math.cos(a) * Math.cos(phi) * radius, Math.sin(phi) * radius * .6, Math.sin(a) * Math.cos(phi) * radius), arc % 2 == 0 ? GOLD : WHITE, .9f, 1, 0);
                            }
                        }
                    }
                    case STATIC_FIELD -> {
                        if (dusty) for (int i = 0; i < 3; i++) {
                            double a = time * .06 + i * TAU / 3;
                            f.bolt(base.clone().add(0, .3, 0), base.clone().add(Math.cos(a) * radius, .1 + .5 * (i % 2), Math.sin(a) * radius), .22, 8);
                        }
                        // A strike from above every second at a random point in the field.
                        if (time % 20 == 0) {
                            double a = random(0, TAU), d = radius * Math.sqrt(random(0, 1));
                            f.strike(base.clone().add(Math.cos(a) * d, 0, Math.sin(a) * d), 4.5);
                        }
                        f.cube("core", base.clone().add(0, .8, 0), Material.YELLOW_STAINED_GLASS, .4, .4, .4, spin(time * .3, time * .2, 0), 2);
                        f.billboard("core", "glow", "lightning", base.clone().add(0, .8, 0), 2.2 * fade);
                    }
                    default -> { if (dusty) f.helixDust(base, radius * .7, 2, time * .08, main(), 14, 2); }
                }
            });
        }

        public void telegraph(Location center, double radius, int age, int duration) {
            if (age % 2 != 0) return;
            Location base = center.clone().add(0, .1, 0);
            base.setYaw(0); base.setPitch(0);
            double progress = Math.clamp(age / (double) Math.max(1, duration), 0, 1);
            if (opening("telegraph")) safe(() -> {
                List<Player> crowd = crowd(base);
                int life = Math.max(6, duration - age);
                // The circle draws the real hit radius; a closing ring times the strike; energy gathers inside.
                shot("circle", tint(), base).flat().size(radius * .3, radius * 2.15).grow(.3).life(life + 8).spin(70).fade(3, 8).collapse().play(crowd);
                shot("ring", tint(), base.clone().add(0, .04, 0)).flat().size(radius * 2.1, radius * .25).life(life).fade(2, 2).play(crowd);
                kit(tint(), RISE, base).radius(radius * .85).height(2.6).seconds(life / 20.0).count(.7 * q()).play(crowd);
            });
            emit("telegraph", base, 5, f -> {
                switch (spell) {
                    case TITANS_JUDGMENT -> {
                        Location boulder = base.clone().add(0, 9 - 5 * ease((progress - .6) / .4), 0);
                        f.block("boulder", boulder, Material.STONE, 3.4, 3, 3.4, age * .012, -.15);
                    }
                    case MAGMA_ERUPTION -> {
                        for (int i = 0; i < 5; i++) {
                            double a = i * TAU / 5 + .5;
                            f.block("crack" + i, base.clone().add(Math.cos(a) * radius * .35, -.5, Math.sin(a) * radius * .35), Material.MAGMA_BLOCK, radius * .6 * progress, .06, .2, -a, 0);
                        }
                        if (age % 4 == 0) f.p(Particle.LAVA, base, 2, radius * .45, .02);
                    }
                    case DIVINE_JUDGMENT, HEAVENS_DESCENT -> {
                        Location high = base.clone().add(0, 8 - progress * 3, 0);
                        f.flat("halo", "circle", "light", high, spell.ultimate() ? 5 : 3.4, age * .08, .08);
                        f.cube("seal", high, Material.GLOWSTONE, .45, .45, .45, spin(age * .2, Math.PI / 4, Math.PI / 4), 2);
                        if (age % 4 == 0) f.line(high, high.clone().add(0, -2, 0), Particle.END_ROD, 6, .08);
                    }
                    case METEOR_RAIN -> { if (age % 4 == 0) f.p(Particle.FLAME, base, 6, radius * .45, .01); }
                    case WRATH_OF_RAIJIN -> { if (age % 4 == 0) f.p(Particle.ELECTRIC_SPARK, base, 6, radius * .3, .01); }
                    default -> { if (age % 4 == 0) f.helix(base, radius * .5, 1.4, age * .1, particle(spell.element()), 10, 1); }
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
            base.setYaw(0); base.setPitch(0);
            if (opening("pillar")) safe(() -> {
                List<Player> crowd = crowd(base);
                Location floor = base.clone().add(0, .05, 0);
                switch (spell) {
                    case ICE_SPIKE, EARTH_SPIKE -> {
                        kit(tint(), BURST, base.clone().add(0, .3, 0)).radius(1.4).count(q()).play(crowd);
                        shot("ring", tint(), floor).flat().size(.4, 3.6).life(10).fade(1, 5).play(crowd);
                        shot("burst", tint(), floor).flat().size(1, 3.4).grow(.2).life(30).rotation(random(0, 360)).fade(1, 12).play(crowd);
                    }
                    case SEISMIC_SLAM -> {
                        kit("earth", BURST, base.clone().add(0, .3, 0)).radius(2.6).count(1.3 * q()).play(crowd);
                        kit("earth", RING, base).radius(5).count(1.2 * q()).play(crowd);
                        shot("burst", "earth", floor).flat().size(2, 8.5).grow(.12).life(44).rotation(random(0, 360)).fade(1, 16).play(crowd);
                        shot("ring", "earth", floor.clone().add(0, .02, 0)).flat().size(1, 11).life(12).fade(1, 6).play(crowd);
                    }
                    default -> {
                        if (spell.element() == Element.LIGHT) {
                            shot("beam", "light", base.clone().add(0, height / 2, 0)).vertical().size(1.9).aspect(height / 1.9).life(16).fade(1, 8).play(crowd);
                            shot("beam", "light", base.clone().add(0, height / 2, 0)).vertical().size(.7).aspect(height / .7).life(12).fade(1, 6).play(crowd);
                            shot("circle", "light", floor).flat().size(.5, 5.2).grow(.2).life(24).spin(80).fade(1, 8).collapse().play(crowd);
                            shot("circle", "light", base.clone().add(0, height, 0)).flat().size(3.4, 1).life(16).spin(-120).fade(1, 6).play(crowd);
                            kit("light", BURST, base.clone().add(0, .4, 0)).radius(1.6).count(1.2 * q()).play(crowd);
                            kit("light", RISE, base).radius(1.2).height(height).seconds(.8).count(q()).play(crowd);
                            kit("light", RING, base).radius(3).count(q()).play(crowd);
                        } else kit(tint(), RISE, base).radius(1.4).height(height).seconds(.8).count(q()).play(crowd);
                    }
                }
            });
            emit("pillar", base, grand() ? 18 : 12, f -> {
                double t = f.age / (grand() ? 18.0 : 12.0);
                switch (spell) {
                    case ICE_SPIKE, EARTH_SPIKE -> {
                        boolean ice = spell.element() == Element.ICE;
                        double rise = Math.sin(Math.min(1, t * 1.6) * Math.PI * .5) * (t > .7 ? Math.max(.05, (1 - t) / .3) : 1);
                        f.block("core", base.clone().add(0, -.3, 0), ice ? Material.BLUE_ICE : Material.TUFF, .75 * rise, (height + .4) * rise, .75 * rise, .4, .05);
                        f.block("core2", base.clone().add(.28, -.3, .15), ice ? Material.PACKED_ICE : Material.DEEPSLATE, .48 * rise, height * .75 * rise, .48 * rise, 1.1, -.18);
                        f.block("core3", base.clone().add(-.25, -.3, -.2), ice ? Material.ICE : Material.COBBLESTONE, .4 * rise, height * .55 * rise, .4 * rise, 2.2, .22);
                    }
                    case SEISMIC_SLAM -> {
                        double rise = Math.sin(Math.min(1, t * 2) * Math.PI * .5) * (1 - t);
                        f.block("core", base.clone().add(0, -.4, 0), Material.DEEPSLATE, 1.2, height * rise + .1, 1.2, .3, 0);
                    }
                    case TITANS_JUDGMENT -> f.spikes(base, 3 * ease((f.age + 2) / 12.0), false, 6, Math.sin(t * Math.PI));
                    case ABYSSAL_COLLAPSE -> { if (f.age % 2 == 0) f.helix(base, 2 * (1 - ease(t)), height * .7, -f.age * .35, Particle.REVERSE_PORTAL, 20, 3); }
                    case WRATH_OF_RAIJIN -> { if (f.age < 6) f.bolt(base.clone().add(0, height, 0), base, .65, 18); }
                    default -> {
                        if (spell.element() == Element.LIGHT) {
                            double beam = .9 * (1 - t * .7);
                            f.cube("shaft", base.clone().add(0, height * .5, 0), Material.YELLOW_STAINED_GLASS, beam, height, beam, spin(f.age * .15, 0, 0), 2);
                            if (f.age % 2 == 0) f.line(base, base.clone().add(0, height, 0), Particle.END_ROD, 14, .12);
                        } else if (f.age % 2 == 0) f.fountain(base, 1.4, height * (1 - t), particle(spell.element()), 16);
                    }
                }
            });
            sound(base, spell.element() == Element.LIGHTNING ? Sound.ITEM_TRIDENT_THUNDER : activation(spell.element()), .7f);
            if (spell.element() == Element.LIGHT || spell == ElementalSpell.SEISMIC_SLAM) layer(base, boom(spell.element()), .7f, .8f);
        }

        private void overlay(Location floor, int duration) {
            double seconds = duration / 20.0;
            safe(() -> {
                List<Player> crowd = crowd(floor);
                switch (spell) {
                    case METEOR_RAIN -> {
                        kit("fire", RAIN, floor).radius(6).height(11).seconds(seconds).count(1.2 * q()).bedrockOnly().play(crowd);
                        kit("fire", RISE, floor).radius(5).height(2).seconds(seconds).count(q()).bedrockOnly().play(crowd);
                    }
                    case LEVIATHANS_WRATH -> {
                        kit("water", SWIRL, floor).radius(6).height(5).seconds(seconds).count(1.3 * q()).bedrockOnly().play(crowd);
                        kit("water", RAIN, floor).radius(6).height(6).seconds(seconds).count(q()).bedrockOnly().play(crowd);
                    }
                    case ABSOLUTE_ZERO -> {
                        kit("ice", SWIRL, floor).radius(6).height(3.5).seconds(seconds).count(1.2 * q()).bedrockOnly().play(crowd);
                        kit("ice", RAIN, floor).radius(6).height(6).seconds(seconds).count(q()).bedrockOnly().play(crowd);
                    }
                    case TEMPEST_DOMINION -> {
                        kit("wind", SWIRL, floor).radius(6).height(8).seconds(seconds).count(1.6 * q()).bedrockOnly().play(crowd);
                        kit("wind", RISE, floor).radius(6).height(3).seconds(seconds).count(q()).bedrockOnly().play(crowd);
                    }
                    case TITANS_JUDGMENT -> {
                        kit("earth", RISE, floor).radius(5).height(3).seconds(seconds).count(1.2 * q()).bedrockOnly().play(crowd);
                        kit("earth", SWIRL, floor).radius(5).height(2.5).seconds(seconds).count(q()).bedrockOnly().play(crowd);
                    }
                    case ABYSSAL_COLLAPSE -> {
                        kit("dark", SWIRL, floor).radius(6).height(.3).seconds(seconds).count(1.4 * q()).drain().bedrockOnly().play(crowd);
                        kit("dark", RISE, floor).radius(5).height(4).seconds(seconds).count(q()).bedrockOnly().play(crowd);
                    }
                    case WRATH_OF_RAIJIN -> {
                        kit("lightning", RISE, floor).radius(6).height(2.5).seconds(seconds).count(1.2 * q()).bedrockOnly().play(crowd);
                        kit("lightning", RAIN, floor).radius(6).height(10).seconds(seconds).count(q()).bedrockOnly().play(crowd);
                    }
                    default -> { }
                }
            });
        }

        /** Tsunami: a curling wall of glass crests and foam travelling with the real hit front. */
        public void wave(Location front, Vector direction, double radius, int wave) {
            Clip old = clips.get("wave:" + wave);
            if (old != null && tick - old.start() < 2) return;
            Location at = front.clone();
            at.setYaw(0); at.setPitch(0);
            Vector dir = unit(direction), r = right(dir);
            double yaw = Math.atan2(dir.getX(), dir.getZ());
            if (tick % 4 == 0) safe(() -> {
                List<Player> crowd = crowd(at);
                Location left = at.clone().add(r.clone().multiply(-radius)).add(0, 1.6, 0);
                kit("water", LINE, left).to(at.clone().add(r.clone().multiply(radius)).add(0, 1.6, 0)).count(radius * .5 * q()).width(1.5).play(crowd);
                kit("water", BURST, at.clone().add(0, 1, 0)).radius(radius * .6).count(.4 * q()).play(crowd);
            });
            emit("wave:" + wave, at, 3, f -> {
                int columns = 5;
                for (int i = 0; i < columns; i++) {
                    double x = i / (double) (columns - 1) * 2 - 1, h = 2.6 * Math.sqrt(Math.max(.05, 1 - x * x));
                    Location foot = at.clone().add(r.clone().multiply(x * radius)).add(0, -.7, 0);
                    f.lean("crest" + i, foot, Material.LIGHT_BLUE_STAINED_GLASS, radius * 2 / columns * 1.08, h, .6, yaw, .4);
                    if (tick % 2 == 0) f.p(Particle.SPLASH, foot.clone().add(0, h + .1, 0), 3, .3, .02);
                }
                f.upright("foam", "slash", "water", at.clone().add(dir.clone().multiply(-.3)).add(0, 1.4, 0), r, radius * 2.4, 2.2, Math.PI);
                if (tick % 2 == 0) for (int i = 0; i < 12; i++) {
                    double x = i / 11.0 * 2 - 1;
                    f.dust(at.clone().add(r.clone().multiply(x * radius)).add(dir.clone().multiply(-.6)).add(0, -.5, 0), i % 2 == 0 ? WHITE : CYAN, 1.3f, 1, .05);
                }
            });
        }

        public void storm(Location at, int age) {
            if (age % 4 != 0 && age != 100) return;
            Location base = at.clone();
            if (age % 20 < 4 || age == 100) safe(() -> {
                List<Player> crowd = crowd(base);
                shot("bolt_" + "abc".charAt(ThreadLocalRandom.current().nextInt(3)), "lightning", base.clone().add(0, 5, 0))
                        .vertical().size(age == 100 ? 12 : 10).life(6).fade(1, 3).play(crowd);
                kit("lightning", BURST, base.clone().add(0, .5, 0)).radius(age == 100 ? 2.4 : 1.4).count(q()).play(crowd);
                shot("ring", "lightning", base.clone().add(0, .06, 0)).flat().size(.5, age == 100 ? 8 : 5).life(9).fade(1, 4).play(crowd);
            });
            emit("storm", base, 5, f -> {
                if (age % 20 < 4 || age == 100) {
                    f.bolt(base.clone().add(.4 * Math.sin(age), 10, 0), base, age == 100 ? .8 : .45, 18);
                    f.billboard("strike", "glow", "lightning", base.clone().add(0, 1, 0), 3.4);
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
                if (key.equals("curse_mark")) { f.flat("mark", "ring", "dark", target.getLocation().add(0, .1, 0), 1.6, 0, 0); return; }
                if (f.age % 8 != 0) return;
                Location at = target.getLocation().add(0, Math.min(1.1, target.getHeight() * .55), 0);
                // Bedrock draws the marker from one small kit emitter; Java keeps its vanilla motes.
                f.kitHere(at, statusTint(key), TRAIL, .3, 0, 0, Math.min(1.5, .5 + count * .25));
                switch (key) {
                    case "burn" -> f.p(Particle.SMALL_FLAME, at, Math.min(4, count + 1), .22, .005);
                    case "wet" -> f.p(Particle.DRIPPING_WATER, at.add(0, .7, 0), Math.min(3, count + 1), .22, 0);
                    case "frost" -> f.p(Particle.SNOWFLAKE, at, Math.min(4, count + 1), .3, 0);
                    case "air" -> f.dust(at, WHITE, .9f, Math.min(4, count + 1), .25);
                    case "radiance" -> f.p(Particle.END_ROD, at.add(0, .65, 0), Math.min(3, count + 1), .2, 0);
                    case "shock" -> f.p(Particle.ELECTRIC_SPARK, at.add(0, .5, 0), Math.min(4, count + 2), .24, .015);
                    case "break" -> f.p(Particle.FALLING_DUST, at, Math.min(4, count + 1), .25, .01);
                    default -> f.p(Particle.SQUID_INK, at, 1, .2, .005);
                }
            });
        }

        private boolean valid(LivingEntity target, BooleanSupplier active) {
            return target.isValid() && !target.isDead() && target.getWorld() == world && active.getAsBoolean();
        }

        /** A freeze cage themed by the caster's element: a bubble for water, ice bars, obsidian, charged glass... */
        public void cage(LivingEntity target, int duration, BooleanSupplier active) {
            String id = "cage:" + target.getUniqueId();
            claim(id);
            if (opening(id)) safe(() -> {
                Location at = target.getLocation();
                List<Player> crowd = crowd(at);
                kit(tint(), BURST, at.clone().add(0, target.getHeight() * .5, 0)).radius(1).count(.8 * q()).play(crowd);
                shot("ring", tint(), at.clone().add(0, .06, 0)).flat().size(.5, 3.4).life(10).fade(1, 5).play(crowd);
            });
            emit(id, target.getLocation(), duration, f -> {
                if (!valid(target, active)) { clear(id); return; }
                Location base = target.getLocation();
                base.setYaw(0); base.setPitch(0);
                double height = Math.min(3.2, target.getHeight() + .45), radius = Math.max(.65, Math.min(1.3, target.getWidth() * .75));
                if (spell.element() == Element.WATER) {
                    f.billboard("bubble", "ring", "water", base.clone().add(0, height * .5, 0), Math.max(radius * 2.4, height * 1.1));
                    if (f.age % 4 == 0) f.sphere(base.clone().add(0, height * .5, 0), radius + .2, Particle.BUBBLE_POP, 10, f.age * .07);
                } else {
                    Material material = cageMaterial(spell.element());
                    for (int i = 0; i < 5; i++) {
                        double a = i * TAU / 5;
                        f.block("bar" + i, base.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius), material, .2, height, .2, -a, (i % 2 == 0 ? .07 : -.07));
                    }
                    f.block("cap", base.clone().add(0, height, 0), material, radius * 1.6, .12, radius * 1.6, f.age * .02, 0);
                    f.flat("floor", "circle", tint(), base.clone().add(0, .06, 0), radius * 2.6, f.age * .03, .03);
                }
            });
        }

        public void buff(LivingEntity target, String key, int duration, BooleanSupplier active) {
            String id = "buff:" + target.getUniqueId() + ":" + key;
            claim(id);
            if (opening(id)) safe(() -> {
                Location feet = target.getLocation().add(0, .07, 0);
                List<Player> crowd = crowd(feet);
                String t = spell == ElementalSpell.PURIFICATION || spell == ElementalSpell.HOLY_SANCTUARY ? "light" : tint();
                shot("circle", t, feet).flat().size(.3, 1.8).grow(.25).life(18).spin(90).fade(1, 6).collapse().play(crowd);
                shot("glow", t, feet.clone().add(0, 1.1, 0)).size(1.8, .3).life(8).fade(1, 4).play(crowd);
                kit(t, RISE, feet).radius(.6).height(2.2).seconds(.6).count(.7 * q()).play(crowd);
            });
            emit(id, target.getLocation(), duration, f -> {
                if (!valid(target, active)) { clear(id); return; }
                Location at = target.getLocation();
                at.setYaw(0); at.setPitch(0);
                switch (spell) {
                    case AQUA_SHIELD -> {
                        // The shield outline faces whoever looks at it, so it reads as a bubble from every side.
                        f.billboard("shell", "ring", "water", at.clone().add(0, 1, 0), 2.4);
                        for (int i = 0; i < 2; i++) {
                            double a = f.age * .12 + i * Math.PI;
                            f.cube("drop" + i, at.clone().add(Math.cos(a) * .85, 1 + .35 * Math.sin(f.age * .1 + i), Math.sin(a) * .85), Material.LIGHT_BLUE_STAINED_GLASS, .2, .2, .2, spin(a, a, 0), 4);
                        }
                    }
                    case WIND_BARRIER -> { f.billboard("shell", "ring", "wind", at.clone().add(0, 1, 0), 2.2); if (f.age % 8 == 0) f.helixDust(at, .85, 1.9, f.age * .22, WHITE, 10, 2); }
                    case FROZEN_ARMOR, IRON_SKIN -> {
                        boolean ice = spell == ElementalSpell.FROZEN_ARMOR;
                        Material material = ice ? Material.PACKED_ICE : Material.IRON_BLOCK;
                        Vector r = right(target.getLocation().getDirection());
                        double yaw = Math.toRadians(-target.getLocation().getYaw());
                        for (int i : new int[]{-1, 1})
                            f.block("pauldron" + i, at.clone().add(r.clone().multiply(i * .43)).add(0, 1.15, 0), material, .26, .42, .32, yaw, i * .12);
                        f.flat("floor", "ring", ice ? "ice" : "earth", at.clone().add(0, .06, 0), 1.6, 0, 0);
                        if (f.age % 8 == 0) f.p(ice ? Particle.SNOWFLAKE : Particle.SCRAPE, at.clone().add(0, .6, 0), 3, .3, .005);
                    }
                    case FLAME_DASH, STATIC_FIELD -> {
                        boolean fire = spell == ElementalSpell.FLAME_DASH;
                        f.flat("floor", "ring", fire ? "fire" : "lightning", at.clone().add(0, .06, 0), 1.3, 0, 0);
                        if (f.age % 8 == 0) f.p(fire ? Particle.SMALL_FLAME : Particle.ELECTRIC_SPARK, at.clone().add(0, .1, 0), 3, .3, 0);
                    }
                    case HOLY_SANCTUARY, PURIFICATION -> {
                        f.flat("floor", "ring", "light", at.clone().add(0, .06, 0), 1.4, 0, 0);
                        if (f.age % 8 == 0) f.p(Particle.END_ROD, at.clone().add(0, 1.8, 0), 2, .2, 0);
                    }
                    default -> { if (f.age % 8 == 0) f.helixDust(at, .6, 1.6, f.age * .1, main(), 8, 1); }
                }
            });
        }

        public void summon(LivingEntity entity, int age) {
            if (age % 6 != 0) return;
            String key = "summon:" + entity.getUniqueId();
            boolean golem = spell == ElementalSpell.GOLEM_GUARDIAN;
            if (opening(key)) safe(() -> {
                Location feet = entity.getLocation().add(0, .06, 0);
                feet.setYaw(0); feet.setPitch(0);
                List<Player> crowd = crowd(feet);
                if (golem) {
                    shot("circle", "earth", feet).flat().size(.5, 4.4).grow(.2).life(32).spin(45).fade(2, 10).collapse().play(crowd);
                    kit("earth", BURST, feet.clone().add(0, .4, 0)).radius(1.8).count(1.2 * q()).play(crowd);
                    kit("earth", RING, feet).radius(3.2).count(q()).play(crowd);
                    shot("burst", "earth", feet).flat().size(1.5, 5).grow(.2).life(36).rotation(random(0, 360)).fade(1, 14).play(crowd);
                    layer(feet, Sound.ENTITY_IRON_GOLEM_REPAIR, .8f, .7f);
                } else {
                    shot("circle", "dark", feet).flat().size(.5, 3.4).grow(.2).life(30).spin(-70).fade(2, 10).collapse().play(crowd);
                    kit("dark", SWIRL, feet).radius(1.1).height(2.6).seconds(1).count(q()).play(crowd);
                    kit("dark", BURST, feet.clone().add(0, 1, 0)).radius(1).count(.8 * q()).play(crowd);
                }
            });
            emit(key, entity.getLocation(), 8, f -> {
                if (!entity.isValid() || entity.isDead() || entity.getWorld() != world) { clear(key); return; }
                Location at = entity.getLocation();
                at.setYaw(0); at.setPitch(0);
                f.flat("seal", "circle", golem ? "earth" : "dark", at.clone().add(0, .06, 0), golem ? 2.6 : 1.9, age * .03, .03);
                if (f.age == 0) {
                    if (golem) f.p(Particle.FALLING_DUST, at.clone().add(0, .2, 0), 4, .5, .015);
                    else { f.p(Particle.SOUL, at.clone().add(0, 1, 0), 3, .35, .01); f.dust(at.clone().add(0, 1.4, 0), VIOLET, 1.2f, 3, .35); }
                }
            });
        }

        public void wall(BoundingBox box) {
            Location center = box.getCenter().toLocation(world);
            if (wallBorn < 0) wallBorn = tick;
            boolean alongX = box.getWidthX() > box.getWidthZ();
            Vector across = alongX ? X : Z;
            double width = Math.max(box.getWidthX(), box.getWidthZ());
            Location groundLine = center.clone().add(0, -box.getHeight() / 2, 0);
            if (opening("wall")) safe(() -> {
                List<Player> crowd = crowd(center);
                for (int i : new int[]{-1, 0, 1})
                    kit("earth", BURST, groundLine.clone().add(across.clone().multiply(i * width * .35)).add(0, .3, 0)).radius(1.1).count(.7 * q()).play(crowd);
                kit("earth", RING, groundLine).radius(width * .7).count(.8 * q()).play(crowd);
                layer(groundLine, Sound.ENTITY_GENERIC_EXPLODE, .35f, .7f);
            });
            emit("wall", center, 6, f -> {
                Vector normal = alongX ? Z : X;
                double depth = Math.min(box.getWidthX(), box.getWidthZ());
                double height = box.getHeight(), angle = alongX ? 0 : Math.PI / 2;
                long time = tick - wallBorn;
                Location ground = center.clone().add(0, -height / 2, 0);
                // A crenellated rampart heaves out of the ground column by column, a beat apart.
                double plinth = ease(Math.clamp(time / 5.0, 0, 1));
                f.block("plinth", ground.clone().add(0, -(1 - plinth) * .5, 0), Material.DEEPSLATE_BRICKS, width * 1.04, .5, depth * 1.2, angle, 0);
                for (int i = 0; i < 5; i++) {
                    double rise = ease(Math.clamp((time - 1 - Math.abs(i - 2)) / 6.0, 0, 1));
                    double tall = height + (i % 2 == 0 ? .4 : 0);
                    Location foot = ground.clone().add(across.clone().multiply((i - 2) * width / 5)).add(0, -(1 - rise) * tall, 0);
                    f.block("stone" + i, foot, i % 2 == 0 ? Material.STONE_BRICKS : Material.COBBLESTONE, width / 5 * .97, tall, depth * .98, angle, 0);
                    if (rise > 0 && rise < 1 && time % 2 == 0) {
                        Location base = ground.clone().add(across.clone().multiply((i - 2) * width / 5)).add(0, .3, 0);
                        f.p(Particle.FALLING_DUST, base, 3, .35, 0);
                        f.dust(base, STONE_DUST, 1.6f, 3, .3);
                    }
                }
                Location face = center.clone().add(0, .1, 0);
                if (time < 10) {
                    // The ground sigil and a shockwave mark where the wall broke through.
                    f.flat("sigil", "circle", "earth", ground.clone().add(0, .06, 0), width + 1.8, time * .05, 0);
                    f.flat("shock", "ring", "earth", ground.clone().add(0, .08, 0), 1.5 + time * .9, 0, 0);
                }
                if (time >= 7) {
                    // A slow-turning earth rune glows on both faces while the wall stands.
                    double pulse = 2.1 + .12 * Math.sin(time * .15);
                    for (int side : new int[]{-1, 1})
                        f.upright("rune" + side, "circle", "earth", face.clone().add(normal.clone().multiply(side * (depth * .5 + .04))), across, pulse, pulse, time * .03 * side);
                    if (time % 6 == 0) for (int side : new int[]{-1, 1}) {
                        double u = Math.sin(time * 1.7 + side) * width * .4;
                        f.dust(ground.clone().add(across.clone().multiply(u)).add(normal.clone().multiply(side * (depth * .5 + .1))).add(0, .4, 0), SAND_DUST, 1.2f, 2, .1);
                    }
                }
                if (time == 0 && !wallRang) {
                    wallRang = true;
                    sound(ground, Sound.BLOCK_DEEPSLATE_BRICKS_PLACE, .6f);
                }
            });
        }

        public void phoenix() {
            Location base = owner.getLocation().add(0, 1.1, 0);
            Vector facing = unit(owner.getLocation().getDirection().setY(0)), side = right(facing);
            safe(() -> {
                List<Player> crowd = crowd(base);
                Location feet = owner.getLocation().add(0, .06, 0);
                kit("fire", SWIRL, feet).radius(1.7).height(4.4).seconds(1.5).count(1.2 * q()).play(crowd);
                kit("fire", BURST, base.clone().add(0, 1, 0)).radius(2).count(1.4 * q()).play(crowd);
                shot("burst", "fire", base.clone().add(0, 1.2, 0)).size(.5, 4.2).grow(.3).life(16).spin(90).fade(1, 6).collapse().play(crowd);
                shot("circle", "fire", feet).flat().size(.6, 5).grow(.2).life(30).spin(70).fade(2, 10).collapse().play(crowd);
            });
            emit("phoenix", base, 30, f -> {
                double t = f.age / 30.0;
                Location chest = base.clone().add(0, .5 + t * 3.5, 0);
                f.billboard("heart", "glow", "fire", chest, 3.4 * (1 - t * .5));
                if (f.age % 2 == 0) for (int wing : new int[]{-1, 1}) for (int i = 0; i < 12; i++) {
                    double u = i / 11.0, span = u * 3.4 * (.8 + .2 * ease(t * 4));
                    double flap = Math.sin(f.age * .32) * u * .75;
                    Location point = chest.clone().add(side.clone().multiply(wing * span))
                            .subtract(facing.clone().multiply(u * u * .8))
                            .add(0, Math.sin(u * Math.PI) * .7 + flap - u * .25, 0);
                    f.p(Particle.FLAME, point, 2, .05, .01);
                    if (i % 3 == 0) f.dust(point, i % 2 == 0 ? ORANGE : CRIMSON, 1.3f, 1, .05);
                }
                if (f.age == 0) f.p(Particle.TOTEM_OF_UNDYING, base, 24, .6, .12);
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
        private Location spriteAt;
        private String spriteId;
        private boolean spriteCounted;
        /** True while vanilla particles may also go to Bedrock viewers (types Geyser draws as one particle). */
        private boolean shared;

        private Frame(Scene scene, String key, int age) { this.scene = scene; this.key = key; this.age = age; }

        private void finish() {
            for (String id : List.copyOf(scene.pieces.keySet()))
                if (id.startsWith(key + "/") && !used.contains(id)) scene.release(id);
            for (String id : List.copyOf(scene.planes.keySet()))
                if (id.startsWith(key + "/") && !used.contains(id)) scene.release(id);
        }

        /** Also sends this frame's vanilla particles to Bedrock viewers (only SAFE types, inside their budget). */
        private void shared(Runnable draw) {
            shared = true;
            try { draw.run(); } finally { shared = false; }
        }

        private void element(Location at, int count, double spread) {
            if (scene.spell.element() == Element.WIND) dust(at, count % 2 == 0 ? WHITE : EMERALD, 1f, count, spread);
            else p(particle(scene.spell.element()), at, count, spread, .008);
        }

        /** A kit from inside a frame (same scene ownership and audience). */
        private void kitHere(Location at, String tint, SpellFx.Kind kind, double radius, double height, double seconds, double count) {
            scene.kit(tint, kind, at).radius(radius).height(height).seconds(seconds).count(count * scene.q()).play(scene.crowd(at));
        }

        /** The arrival of a spear or plume: burst sprite, flash and element burst. */
        private void arrive(Location at, String tint, double radius) {
            List<Player> crowd = scene.crowd(at);
            scene.shot("burst", tint, at).size(.5, radius * 2).grow(.35).life(10).rotation(random(0, 360)).fade(1, 4).collapse().play(crowd);
            scene.shot("glow", tint, at).size(radius * 1.6, .2).life(7).fade(1, 4).play(crowd);
            scene.kit(tint, BURST, at).radius(radius).count(scene.q()).play(crowd);
        }

        /** A lightning strike from above: a bolt sprite, a flash ring and sparks. */
        private void strike(Location ground, double height) {
            List<Player> crowd = scene.crowd(ground);
            scene.shot("bolt_" + "abc".charAt(ThreadLocalRandom.current().nextInt(3)), "lightning", ground.clone().add(0, height / 2, 0))
                    .vertical().size(height).life(5).fade(1, 3).play(crowd);
            scene.shot("ring", "lightning", ground.clone().add(0, .06, 0)).flat().size(.4, 2.6).life(8).fade(1, 4).play(crowd);
            scene.kit("lightning", BURST, ground.clone().add(0, .3, 0)).radius(.8).count(.5 * scene.q()).play(crowd);
            scene.layer(ground, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, .25f, 1.4f);
        }

        /** Vanilla particles. Anything Geyser turns into a Bedrock emitter is redrawn as dust or sparks on both clients. */
        private void p(Particle type, Location at, int count, double spread, double extra) {
            if (SAFE.contains(type)) { spawn(type, at, count, spread, extra, null); return; }
            switch (type.name()) {
                // Geyser plays BLOCK as a whole block-break burst per particle (a dirt flood on Bedrock).
                case "BLOCK", "BLOCK_CRUMBLE", "DUST_PILLAR", "DUST_PLUME" -> spawn(Particle.FALLING_DUST, at, Math.min(count, 4), spread, 0, null);
                case "FALLING_WATER" -> spawn(Particle.DRIPPING_WATER, at, count, spread, 0, null);
                case "ITEM_SNOWBALL" -> spawn(Particle.SNOWFLAKE, at, count, spread, 0, null);
                case "CLOUD" -> { if (shared) dust(at, WHITE, 1.4f, count, Math.max(spread, .1)); else spawn(Particle.CLOUD, at, count, spread, extra, null); }
                case "CRIT", "SWEEP_ATTACK", "FLASH", "FIREWORK", "ENCHANTED_HIT", "EXPLOSION", "EXPLOSION_EMITTER" -> spawn(Particle.END_ROD, at, Math.min(count, 4), spread, 0, null);
                case "SMOKE", "LARGE_SMOKE", "CAMPFIRE_COSY_SMOKE", "CAMPFIRE_SIGNAL_SMOKE", "WHITE_SMOKE" -> dust(at, SMOKE, 1.1f, count, Math.max(spread, .1));
                default -> dust(at, WHITE, 1.1f, count, Math.max(spread, .1));
            }
        }

        private void dust(Location at, Color color, float size, int count, double spread) {
            spawn(Particle.DUST, at, count, spread, 0, new Particle.DustOptions(color, size));
        }

        private int frameCap() { return low() ? 60 : scene.spell.ultimate() ? 240 : scene.grand() ? 160 : 110; }

        private void spawn(Particle type, Location at, int count, double spread, double extra, Object data) {
            if (count <= 0 || scene.closed || at.getWorld() != scene.world || !context.loaded(at)) return;
            int frameCap = frameCap();
            if (samples >= frameCap) return;
            if (low()) count = Math.max(1, (count + 1) / 2);
            count = Math.min(count, frameCap - samples);
            scene.viewers(at);
            resetBudget();
            UUID world = at.getWorld().getUID();
            int worldUsed = particles.getOrDefault(world, 0);
            count = Math.min(count, setting("max-native-particles-per-world-tick", 3200, 64, 12000) - worldUsed);
            if (count <= 0) return;
            if (data == null) {
                if (type.getDataType() == BlockData.class) {
                    Material material = scene.spell == ElementalSpell.QUICKSAND ? Material.SAND
                            : scene.spell.element() == Element.ICE ? Material.PACKED_ICE
                            : scene.spell.element() == Element.FIRE ? Material.MAGMA_BLOCK : Material.STONE;
                    data = blocks.computeIfAbsent(material, Material::createBlockData);
                } else if (type.getDataType() == Particle.DustOptions.class) data = new Particle.DustOptions(primary(scene.spell.element()), 1);
                else if (type.getDataType() == Float.class) data = 1f;
                else if (type.getDataType() == Color.class) data = Color.WHITE;
                else if (type.getDataType() != Void.class) return;
            }
            double range = setting("native-view-distance", 48, 16, 96);
            int javaCap = setting("max-native-particles-per-viewer-tick", 900, 24, 4000);
            // A Bedrock client draws every particle as its own textured quad: give it a lighter budget.
            int bedrockCap = setting("max-native-particles-per-bedrock-viewer-tick", 80, 0, 2000);
            int packetCap = setting("max-native-packets-per-viewer-tick", 240, 16, 1024);
            boolean safe = SAFE.contains(type);
            int sent = 0;
            for (Player viewer : scene.viewers) {
                if (!viewer.isOnline() || viewer.getWorld() != scene.world
                        || viewer.getLocation().distanceSquared(at) > range * range) continue;
                UUID id = viewer.getUniqueId();
                boolean bedrock = scene.views.isBedrock(viewer);
                // Bedrock viewers get the kit emitters instead; only shared single-particle types reach them.
                if (bedrock && (!shared || !safe)) continue;
                int usedParticles = viewerParticles.getOrDefault(id, 0), usedPackets = viewerPackets.getOrDefault(id, 0);
                int allowance = Math.min(count, (bedrock ? bedrockCap : javaCap) - usedParticles);
                if (allowance <= 0 || usedPackets >= (bedrock ? packetCap / 4 : packetCap)) continue;
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
            points = Math.min(points, low() ? 8 : 24);
            for (int i = 0; i < points; i++) p(type, from.clone().add(span.clone().multiply(i / (double) Math.max(1, points - 1))), 1, spread, .002);
        }

        private void dustLine(Location from, Location to, Color color, float size, int points) {
            if (from.getWorld() != to.getWorld()) return;
            Vector span = to.toVector().subtract(from.toVector());
            points = Math.min(points, low() ? 6 : 18);
            for (int i = 0; i < points; i++) dust(from.clone().add(span.clone().multiply(i / (double) Math.max(1, points - 1))), color, size, 1, .01);
        }

        /** Two dust strands twisting around a projectile's last step. */
        private void ribbon(Location from, Location to, Color a, Color b, double turn) {
            Vector span = to.toVector().subtract(from.toVector()), dir = unit(span), r = right(dir), up = unit(dir.clone().crossProduct(r));
            for (int i = 0; i < 4; i++) {
                double t = i / 4.0, angle = turn + t * Math.PI;
                Location point = from.clone().add(span.clone().multiply(t));
                dust(point.clone().add(r.clone().multiply(Math.cos(angle) * .22)).add(up.clone().multiply(Math.sin(angle) * .22)), a, .8f, 1, 0);
                dust(point.clone().add(r.clone().multiply(-Math.cos(angle) * .22)).add(up.clone().multiply(-Math.sin(angle) * .22)), b, .8f, 1, 0);
            }
        }

        private void ring(Location center, double radius, Particle type, int points, double turn) {
            if (radius < .01) return;
            if (low()) points = Math.max(6, points / 2);
            for (int i = 0; i < points; i++) {
                double a = turn + i * TAU / points;
                p(type, center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius), 1, .02, .005);
            }
        }

        private void dustRing(Location center, double radius, Color color, float size, int points, double turn) {
            if (radius < .01) return;
            if (low()) points = Math.max(6, points / 2);
            for (int i = 0; i < points; i++) {
                double a = turn + i * TAU / points;
                dust(center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius), color, size, 1, .01);
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

        private void helixDust(Location base, double radius, double height, double turn, Color color, int points, int strands) {
            if (low()) points = Math.max(6, points / 2);
            for (int i = 0; i < points; i++) {
                double t = i / (double) Math.max(1, points - 1), angle = t * TAU * strands + turn;
                double r = radius * (.3 + t * .7);
                dust(base.clone().add(Math.cos(angle) * r, t * height, Math.sin(angle) * r), color, 1f, 1, .02);
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
                if (!low() && i % 2 == 0) dust(previous.clone().add(next.toVector().subtract(previous.toVector()).multiply(.5)), i % 4 == 0 ? CYAN : WHITE, .9f, 1, 0);
                previous = next;
            }
        }

        /** A burst of chips around an impact: falling dust plus coloured dust, never BLOCK (a block-break burst per particle on Bedrock). */
        private void debris(Location base, double radius, Material material, boolean ice) {
            Object data = blocks.computeIfAbsent(material, Material::createBlockData);
            for (int i = 0; i < 8; i++) {
                double a = i * TAU / 8;
                spawn(Particle.FALLING_DUST, base.clone().add(Math.cos(a) * radius, .5, Math.sin(a) * radius), 1, .15, 0, data);
            }
            if (ice) spawn(Particle.SNOWFLAKE, base.clone().add(0, .5, 0), 8, radius * .4, .02, null);
        }

        private void spikes(Location base, double radius, boolean ice, int count, double growth) {
            double rise = Math.max(.06, growth);
            for (int i = 0; i < count; i++) {
                double a = i * TAU / count + .35;
                Location foot = base.clone().add(Math.cos(a) * radius, -.15, Math.sin(a) * radius);
                double h = (1.1 + (i % 3) * .45) * rise;
                Material material = ice ? (i % 2 == 0 ? Material.BLUE_ICE : Material.PACKED_ICE) : (i % 2 == 0 ? Material.DEEPSLATE : Material.TUFF);
                block("spike" + i, foot, material, .35 * rise, h, .35 * rise, -a, (i % 2 == 0 ? .16 : -.16));
            }
        }

        /**
         * Small cubes thrown from {@code base} along parabolas: {@code reach} outward, {@code height} at the apex,
         * shrinking as they land. {@code t} is the clip's 0..1 progress.
         */
        private void ejecta(Location base, Material material, int count, double reach, double height, double size, double t, double phase) {
            if (t >= 1) return;
            for (int i = 0; i < count; i++) {
                double a = i * TAU / count + phase + .3, out = reach * (.55 + .45 * ((i * 7) % 5) / 4.0) * t;
                double lift = height * (.7 + .3 * (i % 3) / 2.0) * 4 * t * (1 - t);
                Location at = base.clone().add(Math.cos(a) * out, lift + .1, Math.sin(a) * out);
                double s = size * (1 - t * .6);
                cube("ejecta" + i, at, material, s, s, s, spin(a + t * 6, t * 5 + i, 0), 2);
            }
        }

        // --- sprites -------------------------------------------------------------------------------------

        /** Item model of a tracked sprite: the kit art (circle, glow, burst, beam) or a 1.37 tintable vfx sprite. */
        private static String model(String sprite, String tint) {
            return switch (sprite) {
                case "circle", "glow", "burst" -> "fx_" + sprite + "_" + tint;
                case "beam" -> "fx_beam_" + tint;
                default -> "vfx_" + sprite + "_" + tint;
            };
        }

        private MythicLineVisuals sprite(String name, String sprite, String tint, Location at) {
            String id = key + "/" + name, model = model(sprite, tint);
            used.add(id);
            if (low() || scene.closed || at.getWorld() != scene.world || !context.loaded(at)) { scene.release(id); return null; }
            scene.viewers(at);
            Sprite existing = scene.planes.get(id);
            if (existing != null && !existing.model().equals(model)) { scene.release(id); existing = null; }
            if (existing != null) return existing.display();
            UUID world = scene.world.getUID();
            if (scene.planes.size() >= (scene.spell.ultimate() ? 10 : 6)
                    || sprites.getOrDefault(world, 0) >= setting("max-native-sprites-per-world", 96, 0, 256)) return null;
            MythicLineVisuals display = new MythicLineVisuals(at, model);
            scene.planes.put(id, new Sprite(display, model, world));
            sprites.merge(world, 1, Integer::sum);
            return display;
        }

        /** Whether this sprite's Bedrock particle should be re-sent now; resting sprites go every 4 ticks. */
        private boolean bedrockDue(String name, int interval) {
            String id = key + "/" + name;
            long last = scene.planeSent.getOrDefault(id, -100L);
            return tick - last >= interval;
        }

        /** Admit a sprite recipient against the same tick budgets as vanilla particles. */
        private boolean permitSprite(Player viewer) {
            double range = setting("native-view-distance", 48, 16, 96);
            if (spriteAt == null || scene.closed || !viewer.isOnline() || viewer.getWorld() != scene.world
                    || viewer.getLocation().distanceSquared(spriteAt) > range * range) return false;
            resetBudget();
            UUID world = scene.world.getUID(), id = viewer.getUniqueId();
            int worldUsed = particles.getOrDefault(world, 0);
            if (!spriteCounted && worldUsed >= setting("max-native-particles-per-world-tick", 3200, 64, 12000)) return false;
            int usedPackets = viewerPackets.getOrDefault(id, 0);
            if (usedPackets >= setting("max-native-packets-per-viewer-tick", 240, 16, 1024) / 4) return false;
            viewerPackets.put(id, usedPackets + 1);
            // A world emission/frame sample is counted once, even when several viewers receive it.
            if (!spriteCounted) {
                spriteCounted = true;
                particles.put(world, worldUsed + 1);
                scene.planeSent.put(spriteId, tick);
            }
            return true;
        }

        /** Java always updates; only permitted Bedrock recipients spend a sprite sample or redraw interval. */
        private void drawSprite(String name, Location at, int interval, MythicLineVisuals display, Runnable draw) {
            display.bedrock(bedrockDue(name, interval));
            spriteAt = at;
            spriteId = key + "/" + name;
            spriteCounted = false;
            scene.spriteFrame = this;
            try { draw.run(); }
            finally { scene.spriteFrame = null; spriteAt = null; }
        }

        /** A horizontal sprite on the ground (magic circle, shockwave), spinning {@code spin} radians per tick. */
        private void flat(String name, String sprite, String tint, Location center, double diameter, double angle, double spin) {
            if (diameter < .05) { used.add(key + "/" + name); return; }
            MythicLineVisuals display = sprite(name, sprite, tint, center);
            if (display == null) return;
            drawSprite(name, center, 4, display, () -> {
                if (spin != 0) display.planeSpin(scene.views, center, X, Z, diameter, angle, spin);
                else display.plane(scene.views, center, X, Z, diameter, diameter, angle, 2);
            });
        }

        /** An upright sprite in the plane of {@code right} and world up (slashes, wave foam). */
        private void upright(String name, String sprite, String tint, Location center, Vector right, double width, double height, double angle) {
            if (width < .05 || height < .05) { used.add(key + "/" + name); return; }
            MythicLineVisuals display = sprite(name, sprite, tint, center);
            if (display == null) return;
            drawSprite(name, center, 2, display,
                    () -> display.plane(scene.views, center, unit(right), Y, width, height, angle, 2));
        }

        /** A camera-facing sprite (flare, glow, shield outline). */
        private void billboard(String name, String sprite, String tint, Location at, double size) {
            if (size < .05) { used.add(key + "/" + name); return; }
            MythicLineVisuals display = sprite(name, sprite, tint, at);
            if (display == null) return;
            int interval = key.equals("projectile") ? 1 : 2;
            drawSprite(name, at, interval, display, () -> display.billboard(scene.views, at, size, interval));
        }

        // --- props ---------------------------------------------------------------------------------------

        /** A prop standing on {@code base}, turned by {@code yaw} then tilted by {@code tilt}. */
        private void block(String name, Location base, Material material, double width, double height, double depth, double yaw, double tilt) {
            Quaternionf rotation = new Quaternionf().rotateY((float) yaw).rotateZ((float) tilt);
            Vector3f lift = rotation.transform(new Vector3f(0, (float) height / 2, 0));
            cube(name, base.clone().add(lift.x, lift.y, lift.z), material, width, height, depth, rotation, 2);
        }

        /** A prop standing on {@code base}, turned by {@code yaw} then leaning forward by {@code pitch}, like a breaking crest. */
        private void lean(String name, Location base, Material material, double width, double height, double depth, double yaw, double pitch) {
            Quaternionf rotation = new Quaternionf().rotateY((float) yaw).rotateX((float) pitch);
            Vector3f lift = rotation.transform(new Vector3f(0, (float) height / 2, 0));
            cube(name, base.clone().add(lift.x, lift.y, lift.z), material, width, height, depth, rotation, 2);
        }

        /**
         * One 3D prop centred at {@code center}: Java gets a BlockDisplay, Geyser viewers the Bedrock cube rig with
         * the same rotation and per-axis size. {@code lerp} is the number of ticks both clients interpolate over.
         */
        private void cube(String name, Location center, Material material, double width, double height, double depth, Quaternionf rotation, int lerp) {
            String id = key + "/" + name;
            used.add(id);
            if (scene.closed || center.getWorld() != scene.world || !context.loaded(center) || width < .03 || height < .03 || depth < .03) return;
            if (low()) { scene.release(id); return; }
            scene.viewers(center);
            double range = setting("native-view-distance", 48, 16, 96);
            boolean visible = scene.viewers.stream().anyMatch(viewer -> viewer.isOnline() && viewer.getWorld() == scene.world
                    && viewer.getLocation().distanceSquared(center) <= range * range);
            if (!visible) { scene.release(id); return; }
            Location place = center.clone();
            place.setYaw(0); place.setPitch(0);
            Piece piece = scene.pieces.get(id);
            if (piece != null && (!piece.entity().isValid() || piece.material() != material)) { scene.release(id); piece = null; }
            if (piece == null) {
                int cap = setting("max-native-displays-per-world", 160, 0, 320);
                if (displays.getOrDefault(scene.world.getUID(), 0) >= cap || scene.pieces.size() >= (scene.spell.ultimate() ? 18 : 12)) return;
                BlockDisplay display = scene.world.spawn(place, BlockDisplay.class, entity -> {
                    entity.setBlock(blocks.computeIfAbsent(material, Material::createBlockData));
                    entity.setPersistent(false);
                    entity.setInvulnerable(true);
                    entity.setGravity(false);
                    entity.setSilent(true);
                    entity.setViewRange((float) range / 64f);
                    entity.setShadowRadius(0);
                    entity.setShadowStrength(0);
                    entity.setInterpolationDuration(lerp);
                    entity.setTeleportDuration(Math.min(3, lerp));
                    entity.setDisplayWidth(8);
                    entity.setDisplayHeight(12);
                    // The Bedrock rig ignores world light; full brightness keeps both clients' props equally lit.
                    entity.setBrightness(new Display.Brightness(glowing(material) ? 15 : 13, 15));
                });
                piece = new Piece(display, scene.world.getUID(), material, new MythicVisuals.BlockProp(material));
                scene.pieces.put(id, piece);
                displays.merge(scene.world.getUID(), 1, Integer::sum);
            }
            BlockDisplay display = piece.entity();
            if (display.getLocation().distanceSquared(place) > 1e-8) display.teleport(place);
            display.setInterpolationDelay(0);
            display.setTransformationMatrix(new Matrix4f().rotate(rotation)
                    .scale((float) width, (float) height, (float) depth).translate(-.5f, -.5f, -.5f));
            if (scene.views.hasBedrockViewers()) {
                if (scene.views.blocksAvailable() && context.plugin.blockModels().material(piece.prop().material) >= 0)
                    scene.views.block(piece.prop(), center, 1, rotation, lerp, (float) width, (float) height, (float) depth);
                else if (unsupported.add(material))
                    context.plugin.getLogger().warning("Bedrock prop for " + material + " unavailable; Bedrock viewers see only its particles");
            }
        }
    }
}
