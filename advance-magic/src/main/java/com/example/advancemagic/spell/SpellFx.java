package com.example.advancemagic.spell;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Client-animated spell effects shared by every native scene, drawn natively on both clients.
 * <ul>
 *   <li><b>Shots</b>: one sprite that plays its whole life from a single send. Bedrock gets one
 *       {@code fxs_<sprite>_<mode>} particle whose Molang eases size, spin and alpha; Java gets one item
 *       display that interpolates between a few keyframes, then removes itself.</li>
 *   <li><b>Kits</b>: element particle recipes. Bedrock gets one {@code kit_<element>_<kind>} emitter per
 *       layer that spawns all of its particles client-side (a whole burst, rain or swirl per packet);
 *       Java viewers get matching vanilla particles, every tick for the continuous kinds.</li>
 * </ul>
 * Bedrock viewers draw kit particles from a token bucket so one crowded fight cannot flood a phone, and
 * Java-only particle types never reach Geyser.
 */
final class SpellFx implements AutoCloseable {
    enum Mode { BB, FLAT, PLANE, VERT }
    enum Kind { BURST, RING, RISE, SWIRL, TRAIL, RAIN, LINE }
    /** The renderer's shared vanilla-particle budget, so kits and scene detail draw from one quota. */
    interface Budget {
        /** Particles this world may still draw this tick. */
        int world(World world);
        void spend(World world, int particles);
        /** Particles granted to one Java viewer for one packet (spending both), or 0. */
        int viewer(Player viewer, int particles);
    }

    private static final Map<String, Color> TINTS = Map.of(
            "fire", Color.fromRGB(255, 138, 61), "water", Color.fromRGB(79, 195, 255), "ice", Color.fromRGB(190, 240, 255),
            "wind", Color.fromRGB(128, 255, 208), "earth", Color.fromRGB(214, 176, 98), "light", Color.fromRGB(255, 226, 122),
            "dark", Color.fromRGB(176, 124, 255), "lightning", Color.fromRGB(160, 215, 255), "heal", Color.fromRGB(120, 255, 170));
    private static final Map<Kind, String[]> LAYERS = new EnumMap<>(Map.of(
            Kind.BURST, new String[]{"burst_a", "burst_b", "burst_c"}, Kind.RING, new String[]{"ring"},
            Kind.RISE, new String[]{"rise_a", "rise_b"}, Kind.SWIRL, new String[]{"swirl"}, Kind.TRAIL, new String[]{"trail"},
            Kind.RAIN, new String[]{"rain"}, Kind.LINE, new String[]{"line"}));
    private static final Color WHITE = Color.WHITE, EMERALD = Color.fromRGB(0x58FFB1), GOLD = Color.fromRGB(0xFFD34D),
            VIOLET = Color.fromRGB(0xA36BFF), INK = Color.fromRGB(0x3A1F5C), SAND = Color.fromRGB(0xD9C08A), STONE = Color.fromRGB(0x9C8A73),
            CYAN = Color.fromRGB(0x72EDFF), FROST = Color.fromRGB(0xBFE9FF), ORANGE = Color.fromRGB(0xFF8546), LEAF = Color.fromRGB(0x5FD068);

    private final MagicContext context;
    private final Budget budget;
    private final MythicVisuals bridge;
    private final List<Pulse> pulses = new ArrayList<>();
    private final List<Emitter> emitters = new ArrayList<>();
    private final Map<UUID, Object> sessions = new HashMap<>();
    private final Map<UUID, Double> tokens = new HashMap<>();
    private final Map<UUID, Integer> bedrockPackets = new HashMap<>();
    private final Map<UUID, Integer> worldPulses = new HashMap<>();
    private final Map<Material, BlockData> blocks = new HashMap<>();
    private final Map<Material, ItemStack> items = new HashMap<>();
    private long tick, sessionTick = -1000, budgetTick = -1000;

    SpellFx(MagicContext context, Budget budget) {
        this.context = context;
        this.budget = budget;
        bridge = new MythicVisuals(context);
    }

    private int setting(String key, int fallback, int min, int max) {
        return Math.clamp(context.plugin.getConfig().getInt("elemental-visuals." + key, fallback), min, max);
    }

    private double range() { return setting("native-view-distance", 48, 16, 96); }

    static Color tint(String tint) { return TINTS.getOrDefault(tint, WHITE); }

    // --- viewers -------------------------------------------------------------------------------------

    /** Bedrock budgets follow the renderer's own ticks: kit particles refill a bucket, packets reset each tick. */
    private void resetBudget() {
        if (tick == budgetTick) return;
        long ticks = Math.clamp(tick - budgetTick, 1, 1000);
        double refill = setting("bedrock-kit-particles-per-tick", 70, 10, 1000) * (double) ticks;
        double cap = setting("bedrock-kit-particle-burst", 1400, 100, 20000);
        tokens.replaceAll((id, value) -> Math.min(cap, value + refill));
        budgetTick = tick;
        bedrockPackets.clear();
    }

    /** The Geyser session of a viewer (cached for a second), or null for a Java player. */
    private Object session(Player player) {
        if (tick - sessionTick >= 20) { sessions.clear(); sessionTick = tick; }
        Object cached = sessions.get(player.getUniqueId());
        if (cached == null) {
            Object session = bridge.session(player);
            cached = session == null ? Boolean.FALSE : session;
            sessions.put(player.getUniqueId(), cached);
        }
        return cached == Boolean.FALSE ? null : cached;
    }

    boolean bedrock(Player player) { return session(player) != null; }

    private boolean near(Player viewer, Location at) {
        double r = range();
        return viewer.isOnline() && viewer.getWorld() == at.getWorld() && viewer.getLocation().distanceSquared(at) <= r * r;
    }

    private boolean bedrockPacket(Player viewer) {
        resetBudget();
        int used = bedrockPackets.getOrDefault(viewer.getUniqueId(), 0);
        if (used >= setting("max-bedrock-fx-packets-per-viewer-tick", 40, 4, 400)) return false;
        bedrockPackets.put(viewer.getUniqueId(), used + 1);
        return true;
    }

    /** Fraction of a kit this Bedrock viewer can still afford (0 skips it), spending the tokens. */
    private double afford(Player viewer, double cost) {
        resetBudget();
        double cap = setting("bedrock-kit-particle-burst", 1400, 100, 20000);
        double have = tokens.computeIfAbsent(viewer.getUniqueId(), id -> cap);
        double scale = cost <= have ? 1 : have / cost;
        if (scale < .3) return 0;
        tokens.put(viewer.getUniqueId(), have - cost * scale);
        return scale;
    }

    private static String vars(String[] names, double[] values) {
        StringJoiner json = new StringJoiner(",", "[", "]");
        for (int i = 0; i < names.length; i++)
            json.add("{\"name\":\"variable." + names[i] + "\",\"value\":{\"type\":\"float\",\"value\":"
                    + String.format(Locale.ROOT, "%.4f", values[i]) + "}}");
        return json.toString();
    }

    // --- shots ---------------------------------------------------------------------------------------

    /** A one-shot animated sprite: build with the setters, then {@link #play}. */
    final class Shot {
        private final String sprite, tint;
        private final Location at;
        private Mode mode = Mode.BB;
        private Vector normal = new Vector(0, 1, 0);
        private double from = 1, to = 1, grow = 1, rotation, spin, aspect = 1, alpha = 1;
        private int life = 10, in = 1, out = 4;
        private boolean collapse;
        private Object owner;

        private Shot(String sprite, String tint, Location at) { this.sprite = sprite; this.tint = tint; this.at = at.clone(); }
        Shot size(double start, double end) { from = start; to = end; return this; }
        Shot size(double size) { return size(size, size); }
        /** Fraction of the life over which the size eases from start to end. */
        Shot grow(double fraction) { grow = Math.clamp(fraction, .01, 1); return this; }
        Shot life(int ticks) { life = Math.clamp(ticks, 2, 600); return this; }
        Shot spin(double degreesPerSecond) { spin = degreesPerSecond; return this; }
        Shot rotation(double degrees) { rotation = degrees; return this; }
        Shot fade(int inTicks, int outTicks) { in = Math.max(1, inTicks); out = Math.max(1, outTicks); return this; }
        /** Shrinks to nothing while fading out (both clients). */
        Shot collapse() { collapse = true; return this; }
        Shot aspect(double heightOverWidth) { aspect = heightOverWidth; return this; }
        Shot alpha(double value) { alpha = Math.clamp(value, 0, 1); return this; }
        Shot flat() { mode = Mode.FLAT; return this; }
        Shot vertical() { mode = Mode.VERT; return this; }
        /** The Java display is removed early when this owner is stopped. */
        Shot owner(Object value) { owner = value; return this; }
        Shot facing(Vector direction) {
            mode = Mode.PLANE;
            normal = direction.lengthSquared() < 1e-6 ? new Vector(0, 0, 1) : direction.clone().normalize();
            return this;
        }
        void play(Collection<Player> viewers) { SpellFx.this.play(this, viewers); }

        private String texture() {
            return switch (sprite) {
                case "circle", "glow", "burst" -> "fx_" + sprite + "_" + tint;
                case "bolt_a", "bolt_b", "bolt_c", "beam" -> "fx_" + sprite;
                default -> "vfx_" + sprite;
            };
        }
        private String model() {
            return switch (sprite) {
                case "circle", "glow", "burst" -> "fx_" + sprite + "_" + tint;
                case "bolt_a", "bolt_b", "bolt_c" -> "fx_" + sprite;
                default -> (sprite.equals("beam") ? "fx_beam" : "vfx_" + sprite) + "_" + tint;
            };
        }
        private boolean tinted() { return !(sprite.equals("circle") || sprite.equals("glow") || sprite.equals("burst") || sprite.startsWith("bolt_")); }
        private double size(int age) {
            double t = Math.clamp(age / Math.max(.01, grow * life), 0, 1);
            double size = from + (to - from) * t;
            return collapse ? size * Math.clamp((life - age) / (double) out, 0, 1) : size;
        }
    }

    Shot shot(String sprite, String tint, Location at) { return new Shot(sprite, tint, at); }

    private void play(Shot shot, Collection<Player> viewers) {
        if (shot.at.getWorld() == null || !context.loaded(shot.at)) return;
        List<Player> java = new ArrayList<>();
        String identifier = "advance_magic:fxs_" + shot.texture() + "_" + switch (shot.mode) { case BB -> "bb"; case FLAT -> "flat"; case PLANE -> "plane"; case VERT -> "vert"; };
        Color color = shot.tinted() ? tint(shot.tint) : WHITE;
        String json = null;
        for (Player viewer : viewers) {
            if (!near(viewer, shot.at)) continue;
            Object session = session(viewer);
            if (session == null) {
                if (context.plugin.packs().hasApplied(viewer)) java.add(viewer);
                continue;
            }
            if (!bedrockPacket(viewer)) continue;
            if (json == null) json = vars(
                    new String[]{"fx_life", "fx_s0", "fx_s1", "fx_grow", "fx_aspect", "fx_rot", "fx_spin", "fx_r", "fx_g", "fx_b", "fx_a",
                            "fx_in", "fx_out", "fx_collapse", "fx_ox", "fx_oy", "fx_oz", "fx_nx", "fx_ny", "fx_nz"},
                    new double[]{shot.life / 20.0, shot.from, shot.to, shot.grow, shot.aspect, shot.rotation, shot.spin,
                            color.getRed() / 255.0, color.getGreen() / 255.0, color.getBlue() / 255.0, shot.alpha,
                            shot.in / 20.0, shot.out / 20.0, shot.collapse ? 1 : 0, 0, 0, 0,
                            shot.normal.getX(), shot.normal.getY(), shot.normal.getZ()});
            bridge.particle(session, identifier, shot.at, json);
        }
        // Low quality draws no display entities on Java (particles only).
        if (java.isEmpty() || "low".equalsIgnoreCase(context.plugin.getConfig().getString("elemental-visuals.quality", "balanced"))) return;
        UUID world = shot.at.getWorld().getUID();
        if (worldPulses.getOrDefault(world, 0) >= setting("max-fx-displays-per-world", 160, 0, 512)) return;
        pulses.add(new Pulse(shot, java));
        worldPulses.merge(world, 1, Integer::sum);
    }

    /** The Java twin of a shot: one item display keyframed toward each segment end, removed at the end of its life. */
    private final class Pulse {
        private final Shot shot;
        private final ItemDisplay display;
        private final int[] keys;
        private final long born = tick;
        private int next = 1;

        private Pulse(Shot shot, List<Player> viewers) {
            this.shot = shot;
            TreeSet<Integer> marks = new TreeSet<>(List.of(0, 1, shot.life));
            marks.add(Math.clamp((int) Math.round(shot.grow * shot.life), 1, shot.life));
            if (shot.collapse) marks.add(Math.clamp(shot.life - shot.out, 1, shot.life));
            // Each spin step stays well under half a turn so the slerp keeps its direction.
            if (shot.spin != 0) {
                int step = Math.max(2, Math.min(8, (int) (120 / Math.max(1, Math.abs(shot.spin) / 20))));
                for (int t = 1; t < shot.life; t += step) marks.add(t);
            }
            keys = marks.stream().mapToInt(Integer::intValue).toArray();
            ItemStack item = new ItemStack(Material.PAPER);
            var meta = item.getItemMeta();
            meta.setItemModel(new NamespacedKey("advance_magic", shot.model()));
            item.setItemMeta(meta);
            Location pose = shot.at.clone();
            pose.setYaw(0);
            pose.setPitch(0);
            float extent = (float) (Math.max(shot.from, shot.to) * Math.max(1, shot.aspect) + 1);
            display = pose.getWorld().spawn(pose, ItemDisplay.class, e -> {
                e.setVisibleByDefault(false);
                e.setItemStack(item);
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                e.setBillboard(switch (shot.mode) { case BB -> Display.Billboard.CENTER; case VERT -> Display.Billboard.VERTICAL; default -> Display.Billboard.FIXED; });
                e.setBrightness(new Display.Brightness(15, 15));
                e.setGravity(false);
                e.setInvulnerable(true);
                e.setSilent(true);
                e.setPersistent(false);
                e.setShadowRadius(0);
                e.setShadowStrength(0);
                e.setViewRange((float) (range() / 64.0));
                e.setDisplayWidth(extent);
                e.setDisplayHeight(extent);
                e.setInterpolationDuration(0);
                e.setTeleportDuration(0);
                e.setTransformation(state(0));
            });
            for (Player viewer : viewers) viewer.showEntity(context.plugin, display);
        }

        private Transformation state(int age) {
            Quaternionf rotation = switch (shot.mode) {
                case FLAT -> new Quaternionf().rotationX((float) (-Math.PI / 2));
                case PLANE -> new Quaternionf().rotationTo(0, 0, 1, (float) shot.normal.getX(), (float) shot.normal.getY(), (float) shot.normal.getZ());
                default -> new Quaternionf();
            };
            rotation.rotateZ((float) Math.toRadians(shot.rotation + shot.spin * age / 20.0));
            float size = (float) Math.max(.001, shot.size(age));
            return new Transformation(new Vector3f(), rotation, new Vector3f(size, (float) (size * shot.aspect), 1), new Quaternionf());
        }

        /** False once removed. */
        private boolean advance() {
            int age = (int) (tick - born);
            if (!display.isValid() || age >= shot.life) { remove(); return false; }
            if (next < keys.length - 1 && age >= keys[next]) {
                int target = keys[next + 1];
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(Math.max(1, target - age));
                display.setTransformation(state(target));
                next++;
            }
            return true;
        }

        private void remove() {
            if (display.isValid()) display.remove();
            worldPulses.computeIfPresent(shot.at.getWorld().getUID(), (id, count) -> count <= 1 ? null : count - 1);
        }
    }

    // --- kits ----------------------------------------------------------------------------------------

    /** A kit call: build with the setters, then {@link #play}. */
    final class Kit {
        private final String tint;
        private final Kind kind;
        private final Location at;
        private double radius = 1, count = 1, seconds = 1, height = 2, size = 1, direction = 1, width = 1;
        private Vector line = new Vector();
        private Object owner;
        private boolean bedrockOnly;

        private Kit(String tint, Kind kind, Location at) { this.tint = tint; this.kind = kind; this.at = at.clone(); }
        Kit radius(double value) { radius = Math.max(.05, value); return this; }
        Kit count(double value) { count = Math.max(0, value); return this; }
        Kit seconds(double value) { seconds = Math.clamp(value, .05, 30); return this; }
        Kit height(double value) { height = value; return this; }
        Kit size(double value) { size = value; return this; }
        /** Swirl: positive rises in a widening funnel, negative drains inward. */
        Kit drain() { direction = -1; return this; }
        Kit to(Location end) { line = end.toVector().subtract(at.toVector()); return this; }
        Kit width(double value) { width = value; return this; }
        /** Continuous Java particles stop when this owner is stopped. */
        Kit owner(Object value) { owner = value; return this; }
        /** Bedrock's share of a scene whose Java side is drawn elsewhere (authored Legendaries). */
        Kit bedrockOnly() { bedrockOnly = true; return this; }
        void play(Collection<Player> viewers) { SpellFx.this.play(this, viewers); }

        private boolean continuous() { return kind == Kind.RISE || kind == Kind.SWIRL || kind == Kind.RAIN; }
        /** Approximate Bedrock particles spawned, for the token bucket. */
        private double cost() {
            return switch (kind) {
                case BURST -> 60 * count;
                case RING -> 30 * count;
                case TRAIL -> 4 * count;
                case LINE -> 16 * count;
                case RISE -> 56 * count * seconds;
                case SWIRL -> 60 * count * seconds;
                case RAIN -> 50 * count * seconds;
            };
        }
    }

    Kit kit(String tint, Kind kind, Location at) { return new Kit(tint, kind, at); }

    private void play(Kit kit, Collection<Player> viewers) {
        if (kit.at.getWorld() == null || !context.loaded(kit.at) || kit.count <= 0) return;
        List<Player> java = new ArrayList<>();
        for (Player viewer : viewers) {
            if (!near(viewer, kit.at)) continue;
            Object session = session(viewer);
            if (session == null) { if (!kit.bedrockOnly) java.add(viewer); continue; }
            double scale = afford(viewer, kit.cost());
            if (scale <= 0) continue;
            String json = vars(new String[]{"k_r", "k_n", "k_t", "k_h", "k_s", "k_dir", "k_dx", "k_dy", "k_dz", "k_w"},
                    new double[]{kit.radius, kit.count * scale, kit.seconds, kit.height, kit.size, kit.direction,
                            kit.line.getX(), kit.line.getY(), kit.line.getZ(), kit.width});
            for (String layer : LAYERS.get(kit.kind)) {
                if (!bedrockPacket(viewer)) break;
                bridge.particle(session, "advance_magic:kit_" + kit.tint + "_" + layer, kit.at, json);
            }
        }
        if (java.isEmpty()) return;
        if (kit.continuous()) emitters.add(new Emitter(kit, java));
        else javaInstant(kit, java);
    }

    /** Java particles for a continuous kit, one slice per tick. */
    private final class Emitter {
        private final Kit kit;
        private final long born = tick;
        private List<Player> viewers;
        private Emitter(Kit kit, List<Player> viewers) { this.kit = kit; this.viewers = viewers; }
        private boolean advance() {
            int age = (int) (tick - born);
            if (age >= kit.seconds * 20) return false;
            if (age % 10 == 9) {
                List<Player> fresh = new ArrayList<>();
                for (Player player : kit.at.getWorld().getPlayers()) if (near(player, kit.at) && session(player) == null) fresh.add(player);
                viewers = fresh;
            }
            if (!viewers.isEmpty()) javaTick(kit, viewers, age);
            return true;
        }
    }

    /** Ends an owner's Java displays and continuous Java particles (Bedrock particles finish their own short lives). */
    void stop(Object owner) {
        emitters.removeIf(emitter -> emitter.kit.owner == owner);
        pulses.removeIf(pulse -> {
            if (pulse.shot.owner != owner) return false;
            pulse.remove();
            return true;
        });
    }

    void tick() {
        tick++;
        pulses.removeIf(pulse -> !pulse.advance());
        emitters.removeIf(emitter -> !emitter.advance());
    }

    @Override public void close() {
        for (Pulse pulse : pulses) pulse.remove();
        pulses.clear();
        emitters.clear();
        worldPulses.clear();
        sessions.clear();
        tokens.clear();
    }

    // --- Java particles -------------------------------------------------------------------------------

    private static int n(double value) { return Math.max(1, (int) Math.round(value)); }

    private BlockData block(Material material) { return blocks.computeIfAbsent(material, Material::createBlockData); }
    private ItemStack item(Material material) { return items.computeIfAbsent(material, ItemStack::new); }

    /** One vanilla particle packet to each Java viewer, inside the renderer's shared particle budget. */
    private void jp(Collection<Player> viewers, Particle type, Location at, int count, double dx, double dy, double dz, double speed, Object data) {
        if (type.getDataType() == Void.class) data = null;
        else if (data == null || !type.getDataType().isInstance(data)) return;
        int want = Math.min(Math.max(1, count), budget.world(at.getWorld()));
        if (want <= 0) return;
        int sent = 0;
        for (Player viewer : viewers) {
            if (!viewer.isOnline() || viewer.getWorld() != at.getWorld()) continue;
            int allowed = budget.viewer(viewer, want);
            if (allowed <= 0) continue;
            viewer.spawnParticle(type, at, count == 0 ? 0 : allowed, dx, dy, dz, speed, data);
            sent = Math.max(sent, allowed);
        }
        // A world emission counts once, however many viewers receive it.
        if (sent > 0) budget.spend(at.getWorld(), sent);
    }

    private void jp(Collection<Player> viewers, Particle type, Location at, int count, double spread, double speed) {
        jp(viewers, type, at, count, spread, spread, spread, speed, null);
    }

    /** A single particle launched with this velocity (blocks per tick). */
    private void jv(Collection<Player> viewers, Particle type, Location at, Vector velocity, Object data) {
        double speed = velocity.length();
        if (speed < 1e-6) { jp(viewers, type, at, 1, 0, 0, 0, 0, data); return; }
        jp(viewers, type, at, 0, velocity.getX() / speed, velocity.getY() / speed, velocity.getZ() / speed, speed, data);
    }

    private void dust(Collection<Player> viewers, Location at, Color color, float size, int count, double spread) {
        jp(viewers, Particle.DUST, at, count, spread, spread, spread, 0, new Particle.DustOptions(color, size));
    }

    private void flash(Collection<Player> viewers, Location at, Color color) {
        jp(viewers, Particle.FLASH, at, 1, 0, 0, 0, 0, color);
    }

    /** The element's signature particle for points along a path (trails, lines, swirls). */
    private void mote(Collection<Player> viewers, String tint, Location at, Vector velocity) {
        switch (tint) {
            case "fire" -> jv(viewers, Particle.FLAME, at, velocity, null);
            case "water" -> { jv(viewers, Particle.FISHING, at, velocity, null); jp(viewers, Particle.SPLASH, at, 1, .05, 0); }
            case "ice" -> jv(viewers, Particle.SNOWFLAKE, at, velocity, null);
            case "wind" -> { jv(viewers, Particle.CLOUD, at, velocity.clone().multiply(.6), null); dust(viewers, at, ThreadLocalRandom.current().nextBoolean() ? WHITE : EMERALD, 1.1f, 1, .05); }
            case "earth" -> { jv(viewers, Particle.BLOCK, at, velocity, block(Material.COARSE_DIRT)); dust(viewers, at, SAND, 1.4f, 1, .08); }
            case "light" -> jv(viewers, Particle.END_ROD, at, velocity.clone().multiply(.5), null);
            case "dark" -> { jv(viewers, Particle.REVERSE_PORTAL, at, velocity, null); dust(viewers, at, ThreadLocalRandom.current().nextBoolean() ? VIOLET : INK, 1.2f, 1, .06); }
            case "lightning" -> jv(viewers, Particle.ELECTRIC_SPARK, at, velocity.clone().multiply(1.5), null);
            default -> { jv(viewers, Particle.TOTEM_OF_UNDYING, at, velocity.clone().multiply(.6), null); dust(viewers, at, EMERALD, 1f, 1, .05); }
        }
    }

    private void javaInstant(Kit kit, List<Player> v) {
        Location at = kit.at;
        double r = kit.radius, c = kit.count;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        switch (kit.kind) {
            case BURST -> {
                switch (kit.tint) {
                    case "fire" -> {
                        jp(v, Particle.FLAME, at, n(34 * c), r * .35, r * .3, r * .35, .06 + .025 * r, null);
                        jp(v, Particle.SMALL_FLAME, at, n(14 * c), r * .3, .06);
                        jp(v, Particle.LAVA, at, n(5 * c), r * .3, 0);
                        jp(v, Particle.LARGE_SMOKE, at, n(6 * c), r * .3, .02);
                        flash(v, at, ORANGE);
                        if (r >= 2.4) jp(v, Particle.EXPLOSION, at, 1, 0, 0);
                    }
                    case "water" -> {
                        jp(v, Particle.SPLASH, at, n(50 * c), r * .35, .3, r * .35, .2, null);
                        jp(v, Particle.FALLING_WATER, at.clone().add(0, .6, 0), n(16 * c), r * .4, .4, r * .4, 0, null);
                        jp(v, Particle.BUBBLE_POP, at, n(12 * c), r * .3, .05);
                        jp(v, Particle.FISHING, at, n(14 * c), r * .3, .1, r * .3, .05, null);
                        flash(v, at, CYAN);
                    }
                    case "ice" -> {
                        jp(v, Particle.SNOWFLAKE, at, n(30 * c), r * .35, .09);
                        jp(v, Particle.ITEM, at, n(18 * c), r * .3, r * .3, r * .3, .2, item(Material.ICE));
                        jp(v, Particle.WHITE_ASH, at, n(24 * c), r * .5, .02);
                        jp(v, Particle.ITEM_SNOWBALL, at, n(10 * c), r * .3, .1);
                        flash(v, at, FROST);
                    }
                    case "wind" -> {
                        jp(v, Particle.CLOUD, at, n(18 * c), r * .3, .2, r * .3, .22, null);
                        jp(v, Particle.SMALL_GUST, at, n(6 * c), r * .45, 0);
                        if (r >= 2) jp(v, Particle.GUST, at, 1, 0, 0);
                        dust(v, at, WHITE, 1.3f, n(14 * c), r * .4);
                        dust(v, at, EMERALD, 1.1f, n(10 * c), r * .4);
                        jp(v, Particle.SWEEP_ATTACK, at, n(3 * c), r * .3, 0);
                    }
                    case "earth" -> {
                        jp(v, Particle.BLOCK, at, n(36 * c), r * .3, .2, r * .3, .25, block(Material.STONE));
                        jp(v, Particle.BLOCK, at, n(14 * c), r * .3, .2, r * .3, .25, block(Material.COARSE_DIRT));
                        jp(v, Particle.DUST_PLUME, at, n(6 * c), r * .3, .1, r * .3, .05, null);
                        jp(v, Particle.POOF, at, n(10 * c), r * .4, .06);
                        jp(v, Particle.FALLING_DUST, at.clone().add(0, 1, 0), n(12 * c), r * .4, .5, r * .4, 0, block(Material.COARSE_DIRT));
                        if (r >= 2.4) jp(v, Particle.EXPLOSION, at, 1, 0, 0);
                    }
                    case "light" -> {
                        jp(v, Particle.END_ROD, at, n(28 * c), r * .2, .18);
                        jp(v, Particle.FIREWORK, at, n(16 * c), r * .2, .15);
                        jp(v, Particle.WAX_OFF, at, n(14 * c), r * .4, 0);
                        jp(v, Particle.GLOW, at, n(8 * c), r * .3, 0);
                        dust(v, at, GOLD, 1.2f, n(18 * c), r * .4);
                        flash(v, at, GOLD);
                    }
                    case "dark" -> {
                        jp(v, Particle.PORTAL, at, n(40 * c), r * .5, .8);
                        jp(v, Particle.REVERSE_PORTAL, at, n(26 * c), r * .3, .12);
                        jp(v, Particle.SOUL, at, n(8 * c), r * .3, .04);
                        jp(v, Particle.SQUID_INK, at, n(6 * c), r * .3, .08);
                        dust(v, at, VIOLET, 1.3f, n(16 * c), r * .4);
                        flash(v, at, VIOLET);
                    }
                    case "lightning" -> {
                        jp(v, Particle.ELECTRIC_SPARK, at, n(40 * c), r * .3, .5);
                        jp(v, Particle.FIREWORK, at, n(10 * c), r * .2, .2);
                        jp(v, Particle.END_ROD, at, n(8 * c), r * .2, .1);
                        jp(v, Particle.WAX_OFF, at, n(10 * c), r * .3, 0);
                        flash(v, at, CYAN);
                    }
                    default -> {
                        jp(v, Particle.HAPPY_VILLAGER, at, n(18 * c), r * .4, 0);
                        jp(v, Particle.HEART, at.clone().add(0, .6, 0), n(3 * c), r * .3, 0);
                        jp(v, Particle.TOTEM_OF_UNDYING, at, n(16 * c), r * .3, .25);
                        jp(v, Particle.COMPOSTER, at, n(8 * c), r * .4, 0);
                        dust(v, at, EMERALD, 1.2f, n(14 * c), r * .4);
                    }
                }
            }
            case RING -> {
                int points = n(32 * c);
                Location base = at.clone().add(0, .15, 0);
                for (int i = 0; i < points; i++) {
                    double a = i * Math.PI * 2 / points + random.nextDouble() * .1;
                    Vector out = new Vector(Math.cos(a), .02, Math.sin(a));
                    switch (kit.tint) {
                        case "fire" -> jv(v, Particle.FLAME, base, out.multiply(r * .045), null);
                        case "water" -> jv(v, Particle.CLOUD, base, out.multiply(r * .04), null);
                        case "ice" -> jv(v, Particle.SNOWFLAKE, base, out.multiply(r * .045), null);
                        case "wind" -> jv(v, Particle.CLOUD, base, out.multiply(r * .05), null);
                        case "earth" -> jv(v, Particle.BLOCK, base, out.multiply(r * .06).setY(.15), block(Material.COARSE_DIRT));
                        case "light" -> jv(v, Particle.END_ROD, base, out.multiply(r * .04), null);
                        case "dark" -> jv(v, Particle.SOUL, base, out.multiply(r * .04), null);
                        case "lightning" -> jv(v, Particle.ELECTRIC_SPARK, base, out.multiply(r * .1), null);
                        default -> jv(v, Particle.TOTEM_OF_UNDYING, base, out.multiply(r * .1), null);
                    }
                }
                if (kit.tint.equals("water")) jp(v, Particle.SPLASH, base, n(30 * c), r * .5, .1, r * .5, .1, null);
                if (kit.tint.equals("earth")) jp(v, Particle.POOF, base, n(10 * c), r * .5, .1, r * .5, .05, null);
            }
            case TRAIL -> {
                for (int i = 0; i < n(2 * c); i++)
                    mote(v, kit.tint, at.clone().add(random.nextGaussian() * .08, random.nextGaussian() * .08, random.nextGaussian() * .08),
                            new Vector(random.nextGaussian() * .01, .01, random.nextGaussian() * .01));
                switch (kit.tint) {
                    case "fire" -> jp(v, Particle.SMALL_FLAME, at, 2, .08, .01);
                    case "water" -> jp(v, Particle.FALLING_WATER, at, 1, .08, 0);
                    case "ice" -> jp(v, Particle.ITEM_SNOWBALL, at, 1, .08, .02);
                    case "light" -> dust(v, at, GOLD, 1f, 2, .1);
                    case "lightning" -> jp(v, Particle.ELECTRIC_SPARK, at, 3, .1, .06);
                    default -> { }
                }
            }
            case LINE -> {
                int points = Math.min(80, n(16 * c));
                double spread = .15 * kit.width;
                for (int i = 0; i < points; i++) {
                    double t = random.nextDouble();
                    Location point = at.clone().add(kit.line.clone().multiply(t))
                            .add(random.nextGaussian() * spread, random.nextGaussian() * spread, random.nextGaussian() * spread);
                    mote(v, kit.tint, point, new Vector(0, .01, 0));
                }
            }
            default -> { }
        }
    }

    private void javaTick(Kit kit, List<Player> v, int age) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double r = kit.radius, h = kit.height, c = kit.count;
        switch (kit.kind) {
            case RISE -> {
                for (int i = 0; i < n(3 * c); i++) {
                    double a = random.nextDouble() * Math.PI * 2, d = r * Math.sqrt(random.nextDouble());
                    Location foot = kit.at.clone().add(Math.cos(a) * d, .1, Math.sin(a) * d);
                    Location mid = foot.clone().add(0, random.nextDouble() * h, 0);
                    switch (kit.tint) {
                        case "fire" -> jv(v, Particle.FLAME, foot, new Vector(0, .055 * h, 0), null);
                        case "water" -> { jp(v, Particle.SPLASH, mid, 2, .1, .1); if (i == 0) jp(v, Particle.FALLING_WATER, foot.clone().add(0, h * .8, 0), 1, .2, 0); }
                        case "ice" -> jv(v, Particle.SNOWFLAKE, foot, new Vector(0, .03 * h, 0), null);
                        case "wind" -> { jv(v, Particle.CLOUD, foot, new Vector(0, .045 * h, 0), null); dust(v, mid, i % 2 == 0 ? WHITE : EMERALD, 1.1f, 1, .05); }
                        case "earth" -> { jv(v, Particle.BLOCK, foot, new Vector(random.nextGaussian() * .02, .05 * h, random.nextGaussian() * .02), block(Material.COARSE_DIRT)); dust(v, mid, STONE, 1.3f, 1, .05); }
                        case "light" -> { jv(v, Particle.END_ROD, foot, new Vector(0, .05 * h, 0), null); dust(v, mid, GOLD, 1f, 1, .05); }
                        case "dark" -> { jv(v, Particle.SOUL, foot, new Vector(0, .04 * h, 0), null); jp(v, Particle.REVERSE_PORTAL, mid, 2, .1, .02); }
                        case "lightning" -> jp(v, Particle.ELECTRIC_SPARK, mid, 2, .12, .1);
                        default -> { jv(v, Particle.TOTEM_OF_UNDYING, foot, new Vector(0, .06 * h, 0), null); dust(v, mid, EMERALD, 1f, 1, .05); if (i == 0 && age % 6 == 0) jp(v, Particle.HAPPY_VILLAGER, mid, 1, .2, 0); }
                    }
                }
                if (kit.tint.equals("fire") && age % 4 == 0) jp(v, Particle.LAVA, kit.at, 1, r * .4, 0);
            }
            case SWIRL -> {
                int points = n(5 * c);
                boolean drain = kit.direction < 0;
                for (int j = 0; j < points; j++) {
                    double p = (j + random.nextDouble() * .5) / points;
                    double turn = age * .35 * (drain ? 1.6 : 1) + p * Math.PI * 6 + j * 2.4;
                    double radius = r * (drain ? 1 - .85 * p : .3 + .7 * p), y = drain ? .15 + .1 * p : h * p;
                    Location point = kit.at.clone().add(Math.cos(turn) * radius, y, Math.sin(turn) * radius);
                    Vector tangent = new Vector(-Math.sin(turn), drain ? 0 : .02, Math.cos(turn)).multiply(.04 + .02 * r);
                    if (drain) tangent.add(new Vector(-Math.cos(turn), 0, -Math.sin(turn)).multiply(.03));
                    mote(v, kit.tint, point, tangent);
                }
            }
            case RAIN -> {
                for (int i = 0; i < n(4 * c); i++) {
                    double a = random.nextDouble() * Math.PI * 2, d = r * Math.sqrt(random.nextDouble());
                    Location top = kit.at.clone().add(Math.cos(a) * d, h, Math.sin(a) * d);
                    switch (kit.tint) {
                        case "fire" -> { jp(v, Particle.FALLING_LAVA, top, 1, .1, 0); jv(v, Particle.FLAME, top, new Vector(0, -.2, 0), null); }
                        case "water" -> { jp(v, Particle.FALLING_WATER, top, 2, .2, 0); if (i % 2 == 0) jp(v, Particle.SPLASH, kit.at.clone().add(Math.cos(a) * d, .1, Math.sin(a) * d), 2, .1, 0); }
                        case "ice" -> { jv(v, Particle.SNOWFLAKE, top, new Vector(random.nextGaussian() * .02, -.06, random.nextGaussian() * .02), null); jp(v, Particle.WHITE_ASH, top, 2, .3, 0); }
                        case "wind" -> jp(v, Particle.TINTED_LEAVES, top, 1, .3, .3, .3, 0, LEAF);
                        case "earth" -> { jp(v, Particle.FALLING_DUST, top, 1, .2, .2, .2, 0, block(Material.COARSE_DIRT)); if (i == 0) jv(v, Particle.BLOCK, top, new Vector(0, -.3, 0), block(Material.STONE)); }
                        case "light" -> { jv(v, Particle.END_ROD, top, new Vector(0, -.12, 0), null); if (i == 0) jp(v, Particle.WAX_OFF, top.clone().add(0, -h * .5, 0), 1, .5, 0); }
                        case "dark" -> { jp(v, Particle.ASH, top, 3, .4, 0); if (i == 0) jv(v, Particle.SOUL, top, new Vector(0, -.08, 0), null); }
                        case "lightning" -> jv(v, Particle.ELECTRIC_SPARK, top, new Vector(0, -.6, 0), null);
                        default -> { jv(v, Particle.TOTEM_OF_UNDYING, top, new Vector(0, -.15, 0), null); dust(v, top.clone().add(0, -h * random.nextDouble(), 0), EMERALD, 1f, 1, .1); }
                    }
                }
            }
            default -> { }
        }
    }

    /** World the pulse/emitter counts belong to, for tests and diagnostics. */
    int displays(World world) { return worldPulses.getOrDefault(world.getUID(), 0); }
}
