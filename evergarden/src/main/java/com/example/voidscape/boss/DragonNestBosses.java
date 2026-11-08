package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.compat.LevelledMobsCompat;
import com.example.voidscape.world.DragonNest;
import com.example.voidscape.world.DragonNestLayout;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Resident elemental Dragon Bosses guarding the three colossal Dragon Nests in Evergarden:
 * <ul>
 *   <li><b>Vharok, Crimson Ember</b> ({@code forge_dragon_fire})</li>
 *   <li><b>Vaelthyr, Stormborne Apex</b> ({@code forge_dragon_storm})</li>
 *   <li><b>Glaceryn, Sovereign of Winter</b> ({@code forge_dragon_ice})</li>
 * </ul>
 * <p><b>Hunt.</b> A dragon rests in its nest basin and wakes when a player comes within {@code aggro-radius} of the
 * nest or strikes it. Unlike the other bosses it does not stay home: it flies after every player within
 * {@code chase-radius} (300) blocks of itself, anywhere up to {@code max-roam} (1000) blocks from its nest, and never
 * past that ceiling. To escape, a player must out-fly it to more than 300 blocks away, or get beyond 1000 blocks
 * from the nest (warping counts). With nobody left to hunt it slowly flies home, untouchable, lands, and is restored.
 * <p><b>Health.</b> Rolled between {@code health-min} and {@code health-max} (13,000-15,000) each time it spawns.
 * <p><b>Fight.</b> Bite, claw rakes, tail whip and wing gusts up close; its element breath (fire, lightning, frost)
 * swept across the ground or strafed from the air; homing breath volleys while chasing; and one element spell
 * (meteor rain, judgment lightning, ice spike eruptions) on its own cooldown. At 60% and 25% it unleashes a nova and
 * grows faster; below 25% it is enraged. Every effect is drawn for both Java and Bedrock players ({@link DragonFx}).
 */
public final class DragonNestBosses implements Listener, AutoCloseable {
    public static final double SCALE = 5.5;
    /** Anchor-to-jaw distance at the bite's contact frame (bite clip, 0.70 s), in blocks. */
    private static final double BITE_REACH = 18.4;
    private static final int BITE_SNAP = 14, CLAW_RIGHT = 12, CLAW_LEFT = 18, TAIL_CONTACT = 11,
        BREATH_FROM = 19, BREATH_TO = 50, CAST_RELEASE = 35, NOVA_RELEASE = 30, SWOOP_LOCK = 12, SWOOP_DIVE = 14;

    private enum State { IDLE, WAKE, HUNT, MOVE, RETURNING, LANDING }

    /**
     * Invisible damage receivers riding the dragon's own bones. The anchor slime sits under the hind legs, about 20
     * blocks behind the head, so arrows and spells aimed at the body, neck, head, tail or wings found nothing to hit.
     * Each relay follows its bone every tick (the same pose both clients render) and forwards every hit to the
     * dragon; the per-player hit cooldown keeps one spell touching several relays to a single hit.
     */
    private static final String[] RELAY_BONES = {"body", "neck2", "head", "tail2", "tail5", "wingelbow_r", "wingelbow_l"};
    private static final int[] RELAY_SIZES = {11, 8, 7, 8, 6, 7, 7};

    private enum Move {
        BITE("bite", 28), CLAW("claw", 32), TAIL_WHIP("tail_whip", 30), WING_GUST("wing_flap", 36),
        BREATH("breath", 60), SWOOP("bite", 44), STRAFE("breath", 60), BARRAGE("roar", 56), MAGIC("cast", 52), NOVA("roar", 60);
        final String clip;
        final int length;
        Move(String clip, int length) { this.clip = clip; this.length = length; }
    }

    // --- world spells --------------------------------------------------------------------------------

    private static final class Bolt {
        Location at;
        Vector velocity;
        final UUID target;
        final long expires;
        Bolt(Location at, Vector velocity, UUID target, long expires) { this.at = at; this.velocity = velocity; this.target = target; this.expires = expires; }
    }

    /** A burning, shocking or freezing patch of ground (a circle, or a capsule when {@code to} is set). */
    private static final class Hazard {
        final Location from, to;
        final double radius, damage;
        final long until;
        Hazard(Location from, Location to, double radius, double damage, long until) { this.from = from; this.to = to; this.radius = radius; this.damage = damage; this.until = until; }
    }

    private static final class Meteor {
        final Location target;
        final long fallAt, impactAt;
        final double radius, damage, height;
        final boolean big;
        final Vector drift;
        final List<FallingBlock> parts = new ArrayList<>();
        Meteor(Location target, long fallAt, long impactAt, double radius, double damage, double height, boolean big, Vector drift) {
            this.target = target; this.fallAt = fallAt; this.impactAt = impactAt; this.radius = radius; this.damage = damage;
            this.height = height; this.big = big; this.drift = drift;
        }
    }

    /** Storm judgment: follows a player, locks, then calls down the lightning. */
    private static final class Sigil {
        final UUID follow;
        Location at;
        final long lockAt, strikeAt;
        final double radius, damage;
        Sigil(UUID follow, Location at, long lockAt, long strikeAt, double radius, double damage) {
            this.follow = follow; this.at = at; this.lockAt = lockAt; this.strikeAt = strikeAt; this.radius = radius; this.damage = damage;
        }
    }

    private static final class Spike {
        final Location at;
        final long eruptAt, removeAt;
        final double damage;
        final int height;
        final List<FallingBlock> blocks = new ArrayList<>();
        Spike(Location at, long eruptAt, long removeAt, double damage, int height) { this.at = at; this.eruptAt = eruptAt; this.removeAt = removeAt; this.damage = damage; this.height = height; }
    }

    /** A ground shock ring expanding from the dragon; jump over its front to avoid it. */
    private static final class Wave {
        final Location center;
        final double from, to, damage;
        final long start;
        final int ticks;
        final Set<UUID> struck = new HashSet<>();
        Wave(Location center, double from, double to, long start, int ticks, double damage) { this.center = center; this.from = from; this.to = to; this.start = start; this.ticks = ticks; this.damage = damage; }
    }

    private static final class Action {
        final Move move;
        final long start;
        final UUID target;
        final Set<UUID> struck = new HashSet<>();
        Vector dir = new Vector(0, 0, 1);
        Location aim, from;
        double lunge, reach, speed, aimY;
        boolean landed;
        Action(Move move, long start, UUID target) { this.move = move; this.start = start; this.target = target; }
    }

    // --- rendering -----------------------------------------------------------------------------------

    private static final class Visual implements AutoCloseable {
        private final VoidscapePlugin plugin;
        private final Slime anchor;
        private final DragonNestLayout.Kind kind;
        private final ArkhaiaModelSupport.Rig rig;
        private final ArmorStand carrier;
        private final ArkhaiaLook look = new ArkhaiaLook();
        private String clip = "";
        private double time;

        Visual(VoidscapePlugin plugin, Slime anchor, DragonNestLayout.Kind kind) {
            this.plugin = plugin;
            this.anchor = anchor;
            this.kind = kind;
            ArkhaiaModelSupport.Rig attached = null;
            try {
                if (plugin.arkhaiaModels() != null && plugin.arkhaiaModels().ready(kind.modelId))
                    attached = plugin.arkhaiaModels().attachBoss(anchor, kind.modelId, SCALE);
            } catch (RuntimeException error) {
                plugin.getLogger().warning("Dragon rig unavailable for " + kind.modelId + ": " + error.getMessage());
            }
            this.rig = attached;
            var bridge = plugin.arkhaiaBedrock();
            this.carrier = (bridge != null && bridge.ready())
                ? anchor.getWorld().spawn(anchor.getLocation(), ArmorStand.class, e -> {
                    e.setPersistent(false);
                    e.setGravity(false);
                    e.setInvulnerable(true);
                    e.setSilent(true);
                    e.setVisibleByDefault(false);
                    e.setVisible(true);
                    e.setBasePlate(false);
                    e.setCollidable(false);
                    bridge.register(e.getUniqueId(), kind.modelId);
                    e.getPersistentDataContainer().set(plugin.key("world_boss_entity"), PersistentDataType.BYTE, (byte) 1);
                    for (var slot : EquipmentSlot.values()) e.addEquipmentLock(slot, ArmorStand.LockType.ADDING_OR_CHANGING);
                })
                : null;
        }

        /** explicit &ge; 0 pins the clip time (attack clips, so contact frames match the server timeline). */
        void frame(Location at, Player target, String next, double rate, double explicit) {
            if (!clip.equals(next)) {
                clip = next;
                time = 0;
            } else {
                time += rate / 20.0;
            }
            if (explicit >= 0) time = explicit;
            var handle = ((org.bukkit.craftbukkit.entity.CraftEntity) anchor).getHandle();
            handle.setPos(at.getX(), at.getY(), at.getZ());
            anchor.setRotation(at.getYaw(), 0);
            var gaze = look.update(
                at.getYaw(),
                target == null ? 0 : target.getX() - at.getX(),
                target == null ? 0 : target.getEyeLocation().getY() - (at.getY() + 9.5),
                target == null ? 0 : target.getZ() - at.getZ(),
                target != null,
                false
            );
            Set<UUID> accepted = new HashSet<>();
            List<Player> viewers = new ArrayList<>();
            for (Player p : at.getWorld().getPlayers()) {
                boolean near = Math.hypot(p.getX() - at.getX(), p.getZ() - at.getZ()) < 224;
                boolean bedrock = JudgePackService.bedrock(p);
                if (near) viewers.add(p);
                if (near && !bedrock && plugin.judgePack().applied(p)) accepted.add(p.getUniqueId());
                if (carrier != null) {
                    if (near && bedrock) {
                        if (!p.canSee(carrier)) p.showEntity(plugin, carrier);
                    } else if (p.canSee(carrier)) {
                        p.hideEntity(plugin, carrier);
                    }
                }
            }
            if (rig != null) {
                rig.viewers(accepted);
                rig.look(gaze.yaw(), gaze.pitch());
                rig.clip(clip, rate, at);
            }
            double length = BossHitbox.clipLength(kind.modelId, clip);
            if (length <= 0) length = 2.0;
            boolean loop = clip.equals("idle") || clip.equals("walk") || clip.equals("fly");
            double sampled = loop ? (time % length) : Math.min(time, length);
            if (carrier != null) {
                var body = ((org.bukkit.craftbukkit.entity.CraftEntity) carrier).getHandle();
                body.setPos(at.getX(), at.getY(), at.getZ());
                carrier.setRotation(at.getYaw(), 0);
                plugin.arkhaiaBedrock().riverFrame(carrier.getUniqueId(), clip, (float) sampled, (float) Math.clamp(rate, 0, 2), gaze, viewers);
            }
        }

        String clip() { return clip.isEmpty() ? "idle" : clip; }
        double time() { return time; }
        boolean owns(Entity e) { return e == carrier; }
        boolean valid() { return anchor.isValid() && (carrier == null || carrier.isValid()); }

        @Override
        public void close() {
            if (rig != null) rig.close();
            if (carrier != null) {
                plugin.arkhaiaBedrock().unregister(carrier.getUniqueId());
                carrier.remove();
            }
        }
    }

    private static final class Run {
        /** Phlegethon's weapon adaptation, active in phase three (enraged). */
        final BossAdaptation adaptation = new BossAdaptation();
        final String id;
        final DragonNestLayout.Site site;
        final DragonNestLayout.Kind kind;
        final Location center;
        final boolean test;
        final BossBar bar;
        final DragonFx fx;
        final Set<Chunk> tickets = new HashSet<>();
        final Set<UUID> contributors = new HashSet<>(), roster = new HashSet<>(), warned = new HashSet<>(), hunted = new HashSet<>();
        final Map<UUID, Long> hitCooldowns = new HashMap<>(), hazardHits = new HashMap<>();
        final Map<UUID, Double> dealt = new HashMap<>();
        final List<Bolt> bolts = new ArrayList<>();
        final List<Hazard> hazards = new ArrayList<>();
        final List<Meteor> meteors = new ArrayList<>();
        final List<Sigil> sigils = new ArrayList<>();
        final List<Spike> spikes = new ArrayList<>();
        final List<Wave> waves = new ArrayList<>();
        final Random random = new Random();
        Slime anchor;
        final List<Slime> relays = new ArrayList<>();
        Visual visual;
        Location position;
        State state = State.IDLE;
        Move lastMove;
        Action action;
        UUID target;
        String transition;
        long stateUntil, moveAt, magicAt, readyAt, retarget, transitionUntil, returnStart, lastSeen;
        double health, maxHealth, orbit = 1;
        int phase = 1, groundTicks, airTicks;
        boolean fighting, dead, grounded = true, novaQueued;

        Run(VoidscapePlugin plugin, DragonNestLayout.Site site, World world, double floorY, boolean test, double initialHealth) {
            this.site = site;
            this.kind = site.kind();
            this.test = test;
            this.center = new Location(world, site.x() + 0.5, floorY, site.z() + 0.5);
            this.position = center.clone();
            this.id = (test ? "test_" : "") + world.getUID() + "_" + kind.shortId + "_" + site.x() + "_" + site.z();
            this.maxHealth = initialHealth;
            this.health = initialHealth;
            BarColor color = switch (kind) {
                case FIRE -> BarColor.RED;
                case STORM -> BarColor.YELLOW;
                case ICE -> BarColor.BLUE;
            };
            this.bar = Bukkit.createBossBar(BossNames.legacy(bossTitle(kind)), color, BarStyle.SEGMENTED_10);
            this.fx = new DragonFx(plugin, kind);
        }
    }

    /** A read-only snapshot of one dragon, for the admin status command and Paper probes. */
    public record View(String id, String kind, String state, String move, String clip, double health, double maxHealth, int phase,
                       Location position, Location center, boolean grounded, boolean test, int spells) {}

    private final VoidscapePlugin plugin;
    private final Map<String, Run> runs = new LinkedHashMap<>();
    private final Map<UUID, Integer> interactions = new HashMap<>();
    /** Player arrows flying near a dragon, with their position on the previous tick. */
    private final Map<AbstractArrow, Location> arrows = new HashMap<>();
    /** Tridents and piercing arrows that already struck once (they keep flying). */
    private final Set<AbstractArrow> spent = new HashSet<>();
    private final File file;
    private final YamlConfiguration ledger;
    private final org.bukkit.scheduler.BukkitTask driver;
    private long tick;
    private boolean closed, applying;

    public DragonNestBosses(VoidscapePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "dragon-bosses.yml");
        this.ledger = YamlConfiguration.loadConfiguration(file);
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                var data = e.getPersistentDataContainer();
                if (data.has(plugin.key("dragon_nest_boss")) || data.has(plugin.key("dragon_prop"))) e.remove();
            }
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getPluginManager().registerEvents(new DragonScales(plugin), plugin);
        this.driver = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Chunk c : plugin.world().getLoadedChunks()) discover(c);
        }, 80);
    }

    private static String bossTitle(DragonNestLayout.Kind kind) {
        return switch (kind) {
            case FIRE -> "VHAROK · มังกรเพลิงโลกันตร์";
            case STORM -> "VAELTHYR · มังกรอัสนีเวหา";
            case ICE -> "GLACERYN · มังกรเหมันต์นิรันดร์";
        };
    }

    private static String shortName(DragonNestLayout.Kind kind) {
        return switch (kind) { case FIRE -> "§c§lVHAROK"; case STORM -> "§e§lVAELTHYR"; case ICE -> "§b§lGLACERYN"; };
    }

    // --- configuration -------------------------------------------------------------------------------

    private boolean enabled() {
        return !closed && plugin.getConfig().getBoolean("dragon-bosses.enabled", true);
    }

    private boolean enabled(DragonNestLayout.Kind kind) {
        return enabled()
            && plugin.dragonNests() != null
            && plugin.dragonNests().enabled(kind)
            && plugin.getConfig().getBoolean("dragon-bosses." + kind.shortId + ".enabled", true);
    }

    private double number(String path, double fallback, double min, double max) {
        double value = plugin.getConfig().getDouble(path, fallback);
        return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }

    /** A fresh health roll: uniform between health-min and health-max (per-element overrides win), in steps of 10. */
    private double rollHealth(DragonNestLayout.Kind kind) {
        double min = number("dragon-bosses." + kind.shortId + ".health-min", number("dragon-bosses.health-min", 13000, 1000, 1000000), 1000, 1000000);
        double max = number("dragon-bosses." + kind.shortId + ".health-max", number("dragon-bosses.health-max", 15000, 1000, 1000000), 1000, 1000000);
        if (max < min) max = min;
        double roll = min + ThreadLocalRandom.current().nextDouble() * (max - min);
        return Math.clamp(Math.round(roll / 10.0) * 10.0, min, max);
    }

    private double partyHealth() { return number("dragon-bosses.party-health-per-player", 0, 0, 1000000); }
    private double healthCap() { return number("dragon-bosses.health-cap", 60000, 1000, 2000000); }
    private double aggroRadius() { return number("dragon-bosses.aggro-radius", 56.0, 24.0, 160.0); }
    private double chaseRadius() { return number("dragon-bosses.hunt.chase-radius", 300.0, 64.0, 600.0); }
    private double maxRoam() { return number("dragon-bosses.hunt.max-roam", 1000.0, 120.0, 3000.0); }
    private double flySpeed() { return number("dragon-bosses.hunt.fly-speed", 1.05, 0.25, 2.5); }
    private double chaseSpeed() { return number("dragon-bosses.hunt.chase-speed", 1.6, 0.4, 3.0); }
    private double walkSpeed() { return number("dragon-bosses.hunt.walk-speed", 0.42, 0.12, 1.0); }
    private double returnSpeed() { return number("dragon-bosses.hunt.return-speed", 0.6, 0.2, 2.6); }
    private double damageScale(Run r) {
        double percent = number("dragon-bosses.damage-percent", 100.0, 0.0, 1000.0) / 100.0;
        return percent * (r.phase >= 3 ? 1.15 : 1.0);
    }
    private double rendScale() { return number("dragon-bosses.rend-percent", 100.0, 0.0, 500.0) / 100.0; }

    // --- players -------------------------------------------------------------------------------------

    private boolean playable(Player p) {
        return p.isOnline() && !p.isDead() && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE);
    }

    private static double flat(Location a, Location b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    /** Within the hunt: at most chase-radius from the dragon and at most max-roam from its nest. */
    private boolean engaged(Run r, Player p) {
        if (!playable(p) || p.getWorld() != r.center.getWorld()) return false;
        double chase = chaseRadius();
        return p.getLocation().distanceSquared(r.position) <= chase * chase && flat(p.getLocation(), r.center) <= maxRoam();
    }

    private boolean withinAggro(Run r, Player p) {
        if (!playable(p) || p.getWorld() != r.center.getWorld()) return false;
        double dy = p.getY() - r.center.getY();
        return flat(p.getLocation(), r.center) <= aggroRadius() && dy >= -12.0 && dy <= 64.0;
    }

    private List<Player> engaged(Run r) {
        return r.center.getWorld().getPlayers().stream().filter(p -> engaged(r, p)).toList();
    }

    public boolean owns(Entity e) {
        return owner(e) != null;
    }

    private Run owner(Entity e) {
        if (e == null) return null;
        for (Run r : runs.values()) {
            if (r.anchor == e || (r.visual != null && r.visual.owns(e)) || (e instanceof Slime && r.relays.contains(e))) return r;
        }
        return null;
    }

    private static boolean receiver(Run r, Entity e) {
        return e == r.anchor || (e instanceof Slime && r.relays.contains(e));
    }

    private Slime relay(Run r, Location at, int size) {
        return r.center.getWorld().spawn(at, Slime.class, e -> {
            BossDamage.tag(e, bossTitle(r.kind));
            e.setSize(size);
            e.setAI(false);
            e.setGravity(false);
            e.setSilent(true);
            e.setInvisible(true);
            e.setCollidable(false);
            e.setPersistent(false);
            e.setRemoveWhenFarAway(false);
            e.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1024);
            e.setHealth(1024);
            e.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(0);
            e.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1);
            e.addScoreboardTag("evergarden_unique");
            LevelledMobsCompat.tagMob(e, plugin, true);
            e.getPersistentDataContainer().set(plugin.key("world_boss_entity"), PersistentDataType.BYTE, (byte) 1);
            e.getPersistentDataContainer().set(plugin.key("dragon_nest_boss"), PersistentDataType.BYTE, (byte) 1);
        });
    }

    /** Moves every relay onto its bone for the pose being shown (respawning any that despawned). */
    private void placeRelays(Run r) {
        if (r.visual == null) return;
        Map<String, Location> bones = BossHitbox.bonePoints(r.kind.modelId, r.visual.clip(), r.visual.time(), r.position, SCALE, Set.of(RELAY_BONES));
        for (int i = 0; i < RELAY_BONES.length; i++) {
            Location bone = bones.get(RELAY_BONES[i]);
            if (bone == null) continue;
            // A slime stands on its position: lower it by half its height so the box is centred on the bone.
            Location at = bone.clone().subtract(0, RELAY_SIZES[i] * 0.26, 0);
            Slime relay = i < r.relays.size() ? r.relays.get(i) : null;
            if (relay == null || !relay.isValid()) {
                if (!at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) continue;
                relay = relay(r, at, RELAY_SIZES[i]);
                if (i < r.relays.size()) r.relays.set(i, relay);
                else r.relays.add(relay);
                continue;
            }
            ((org.bukkit.craftbukkit.entity.CraftEntity) relay).getHandle().setPos(at.getX(), at.getY(), at.getZ());
        }
    }

    // --- lifecycle -----------------------------------------------------------------------------------

    private void discover(Chunk chunk) {
        if (!enabled() || chunk.getWorld() != plugin.world()) return;
        var layout = plugin.dragonNests();
        if (layout == null) return;
        for (var site : layout.cell(Math.floorDiv(chunk.getX(), layout.spacingChunks()), Math.floorDiv(chunk.getZ(), layout.spacingChunks()))) {
            if (!enabled(site.kind())) continue;
            Location at = new Location(plugin.world(), site.x() + 0.5, site.kind().floorY + 1.0, site.z() + 0.5);
            String id = at.getWorld().getUID() + "_" + site.kind().shortId + "_" + site.x() + "_" + site.z();
            if (runs.containsKey(id) || !clearNestCenter(at)) continue;
            Run r = new Run(plugin, site, plugin.world(), site.kind().floorY + 1.0, false, rollHealth(site.kind()));
            r.readyAt = ledger.getLong("cooldowns." + r.id, 0L);
            runs.put(r.id, r);
            if (at.getWorld().getGameTime() < r.readyAt) {
                r.dead = true;
            } else {
                spawn(r);
            }
        }
    }

    private boolean clearNestCenter(Location at) {
        World w = at.getWorld();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                int bx = at.getBlockX() + x, bz = at.getBlockZ() + z;
                if (!w.isChunkLoaded(bx >> 4, bz >> 4)) return false;
                if (!w.getBlockAt(bx, at.getBlockY() - 1, bz).getType().isSolid()) return false;
                for (int y = 0; y < 12; y++) {
                    if (!w.getBlockAt(bx, at.getBlockY() + y, bz).isPassable()) return false;
                }
            }
        }
        return true;
    }

    private void spawn(Run r) {
        if (r.visual != null) {
            r.visual.close();
            r.visual = null;
        }
        if (r.anchor != null) r.anchor.remove();
        r.anchor = r.center.getWorld().spawn(r.position, Slime.class, e -> {
            BossDamage.tag(e, bossTitle(r.kind));
            e.setSize(12);
            e.setAI(false);
            e.setGravity(false);
            e.setSilent(true);
            e.setInvisible(true);
            e.setCollidable(false);
            e.setPersistent(false);
            e.setRemoveWhenFarAway(false);
            e.setNoDamageTicks(0);
            e.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1024);
            e.setHealth(1024);
            e.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(0);
            e.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1);
            e.addScoreboardTag("evergarden_unique");
            LevelledMobsCompat.tagMob(e, plugin, true);
            e.getPersistentDataContainer().set(plugin.key("world_boss_entity"), PersistentDataType.BYTE, (byte) 1);
            e.getPersistentDataContainer().set(plugin.key("dragon_nest_boss"), PersistentDataType.BYTE, (byte) 1);
        });
        r.visual = new Visual(plugin, r.anchor, r.kind);
        r.dead = false;
        for (Slime relay : r.relays) relay.remove();
        r.relays.clear();
        placeRelays(r);
    }

    /** Tickets for the 3x3 loaded chunks around the dragon; chunks it has left are released. */
    private void holdChunks(Run r) {
        World w = r.center.getWorld();
        int cx = r.position.getBlockX() >> 4, cz = r.position.getBlockZ() >> 4;
        Set<Chunk> want = new HashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (w.isChunkLoaded(cx + dx, cz + dz)) want.add(w.getChunkAt(cx + dx, cz + dz));
            }
        }
        for (var it = r.tickets.iterator(); it.hasNext(); ) {
            Chunk c = it.next();
            if (!want.contains(c)) {
                c.removePluginChunkTicket(plugin);
                it.remove();
            }
        }
        for (Chunk c : want) if (r.tickets.add(c)) c.addPluginChunkTicket(plugin);
    }

    private void releaseChunks(Run r) {
        for (Chunk c : r.tickets) c.removePluginChunkTicket(plugin);
        r.tickets.clear();
    }

    private void announce(Run r, List<Player> to, String subtitle) {
        for (Player p : to) {
            plugin.message(p, bossTitle(r.kind) + " · " + subtitle);
            p.sendTitle(shortName(r.kind), subtitle, 8, 45, 12);
        }
    }

    private void actionBar(List<Player> to, String text) {
        for (Player p : to) p.sendActionBar(Component.text(text));
    }

    private void wake(Run r) {
        if (r.fighting || r.state == State.RETURNING || r.state == State.LANDING) return;
        r.fighting = true;
        r.state = State.WAKE;
        r.stateUntil = tick + 40;
        r.moveAt = tick + 56;
        r.magicAt = tick + 260;
        r.phase = 1;
        r.adaptation.reset();
        r.grounded = true;
        r.transition = null;
        holdChunks(r);
        announce(r, engaged(r), switch (r.kind) {
            case FIRE -> "§6ผู้บุกรุกรังเพลิงโลกันตร์ จงมอดไหม้เป็นเถ้าถ่าน!";
            case STORM -> "§eผู้ล่วงล้ำรังอัสนีเวหา จงรับสายฟ้าพิพากษา!";
            case ICE -> "§bผู้เหยียบย่ำรังเหมันต์นิรันดร์ จงกลายเป็นน้ำแข็งชั่วนิรันดร์!";
        });
        World w = r.center.getWorld();
        w.playSound(r.position, Sound.ENTITY_ENDER_DRAGON_GROWL, 5.0f, 0.6f);
        w.playSound(r.position, Sound.ENTITY_RAVAGER_ROAR, 4.0f, 0.5f);
    }

    private void beginReturn(Run r) {
        clearAction(r);
        r.fighting = false;
        r.state = State.RETURNING;
        r.returnStart = tick;
        r.transition = null;
        r.bolts.clear();
        r.roster.clear();
        r.contributors.clear();
        r.dealt.clear();
        r.center.getWorld().playSound(r.position, Sound.ENTITY_ENDER_DRAGON_FLAP, 3.0f, 0.7f);
        for (UUID id : r.hunted) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline() && p.getWorld() == r.center.getWorld()) {
                plugin.message(p, bossTitle(r.kind) + " · §aคุณหนีพ้นแล้ว — มังกรละทิ้งการไล่ล่าและค่อยๆ บินกลับรัง");
            }
        }
        r.hunted.clear();
        r.warned.clear();
    }

    private void clearAction(Run r) {
        r.action = null;
        if (r.state == State.MOVE) r.state = State.HUNT;
    }

    // --- bar -----------------------------------------------------------------------------------------

    private void bar(Run r, List<Player> engaged) {
        List<Player> present = new ArrayList<>(engaged);
        for (Player p : r.center.getWorld().getPlayers()) {
            if (!present.contains(p) && p.getLocation().distanceSquared(r.position) <= 96 * 96) present.add(p);
        }
        r.bar.getPlayers().stream().filter(p -> !present.contains(p)).toList().forEach(r.bar::removePlayer);
        if (!r.fighting && r.state != State.RETURNING && r.state != State.LANDING) {
            r.bar.removeAll();
            return;
        }
        present.forEach(r.bar::addPlayer);
        r.bar.setProgress(Math.clamp(r.health / r.maxHealth, 0.0, 1.0));
        String mode = switch (r.state) {
            case RETURNING, LANDING -> " · บินกลับรัง (ไม่รับความเสียหาย)";
            case MOVE -> r.action == null ? "" : switch (r.action.move) {
                case BITE, SWOOP -> " · เขี้ยวมังกร";
                case CLAW -> " · กรงเล็บฉีก";
                case TAIL_WHIP -> " · ฟาดหาง";
                case WING_GUST -> " · พายุปีก";
                case BREATH, STRAFE -> " · ลมหายใจมังกร";
                case BARRAGE -> " · ห่าลูกไฟธาตุ";
                case MAGIC -> " · ร่ายมนตร์";
                case NOVA -> " · ระเบิดพลังมังกร";
            };
            default -> r.grounded ? "" : " · บินไล่ล่า";
        };
        String phase = r.phase >= 3 ? " §4[คลั่ง]§r" : r.phase == 2 ? " §6[II]§r" : "";
        r.bar.setTitle(BossNames.legacy(bossTitle(r.kind)) + phase + " §8· §f" + (int) Math.ceil(r.health) + " / " + (int) r.maxHealth + "§7" + mode);
    }

    // --- tick ----------------------------------------------------------------------------------------

    private void tick() {
        if (closed) return;
        tick++;
        if (tick % 40 == 0) {
            for (Player p : plugin.world().getPlayers()) discover(p.getLocation().getChunk());
        }
        tickArrows();
        for (Run r : new ArrayList<>(runs.values())) {
            try {
                update(r);
            } catch (RuntimeException error) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Dragon boss reset at " + r.id, error);
                remove(r);
                runs.remove(r.id);
            }
        }
    }

    private void update(Run r) {
        if (!r.test && !enabled(r.kind)) {
            remove(r);
            runs.remove(r.id);
            return;
        }
        if (r.dead) {
            if (!r.test && r.center.getWorld().getGameTime() >= r.readyAt && clearNestCenter(r.center)) resetToFullAtNest(r);
            return;
        }
        World w = r.center.getWorld();
        if (!r.fighting && r.state == State.IDLE) {
            boolean near = false;
            for (Player p : w.getPlayers()) {
                if (p.getLocation().distanceSquared(r.center) <= 160 * 160) { near = true; break; }
            }
            if (!near) {
                bar(r, List.of());
                return;
            }
        }
        if (!w.isChunkLoaded(r.position.getBlockX() >> 4, r.position.getBlockZ() >> 4)) {
            // Nobody is near enough to keep its chunk loaded: finish any flight home off-screen.
            if (r.state == State.RETURNING || r.state == State.LANDING || r.fighting) snapHome(r);
            return;
        }
        if (r.anchor == null || !r.anchor.isValid() || r.visual == null || !r.visual.valid()) spawn(r);
        if (r.state == State.IDLE) {
            for (Player p : w.getPlayers()) {
                if (withinAggro(r, p)) {
                    wake(r);
                    break;
                }
            }
        }
        List<Player> engaged = r.fighting ? engaged(r) : List.of();
        bar(r, engaged);
        tickSpells(r, engaged);
        if (r.fighting) {
            holdChunks(r);
            recruit(r, engaged);
            if (engaged.isEmpty()) beginReturn(r);
            else notifyChase(r, engaged);
        }

        Player target = r.fighting ? target(r, engaged) : null;
        String clip = "idle";
        double rate = 1.0, explicit = -1;
        switch (r.state) {
            case IDLE -> r.position.setY(r.center.getY());
            case WAKE -> {
                clip = "roar";
                if (target != null) face(r, target.getLocation());
                if (tick % 6 == 0) {
                    r.fx.burst(r.position.clone().add(0, 12, 0), 2.6);
                    r.fx.ring(ground(r.position), 8 + (40 - (r.stateUntil - tick)) * 0.5, 1.2, tick * 0.05, true);
                }
                if (tick >= r.stateUntil) r.state = State.HUNT;
            }
            case HUNT -> {
                if (target == null) {
                    beginReturn(r);
                    break;
                }
                String[] out = {clip};
                hunt(r, target, engaged, out);
                clip = out[0];
                rate = clip.equals("fly") ? 1.15 : clip.equals("walk") ? 1.1 : 1.0;
            }
            case MOVE -> {
                Action a = r.action;
                if (a == null) {
                    r.state = State.HUNT;
                    break;
                }
                long age = tick - a.start;
                Player t = a.target == null ? null : Bukkit.getPlayer(a.target);
                if (t != null && !engaged(r, t)) t = null;
                clip = a.move.clip;
                explicit = age / 20.0;
                switch (a.move) {
                    case BITE -> tickBite(r, a, age, t, engaged);
                    case CLAW -> tickClaw(r, a, age, t, engaged);
                    case TAIL_WHIP -> tickTail(r, a, age, engaged);
                    case WING_GUST -> tickGust(r, a, age, t, engaged);
                    case BREATH -> tickBreath(r, a, age, t, engaged);
                    case SWOOP -> {
                        clip = age < SWOOP_LOCK || age > SWOOP_LOCK + SWOOP_DIVE + 6 ? "fly" : "bite";
                        explicit = clip.equals("bite") ? (age - SWOOP_LOCK) / 20.0 : -1;
                        rate = clip.equals("fly") ? 1.3 : 1.0;
                        tickSwoop(r, a, age, t, engaged);
                    }
                    case STRAFE -> tickStrafe(r, a, age, t, engaged);
                    case BARRAGE -> tickBarrage(r, a, age, t, engaged);
                    case MAGIC -> tickMagic(r, a, age, t, engaged);
                    case NOVA -> tickNova(r, a, age, engaged);
                }
                if (r.action == a && age >= a.move.length) finishMove(r);
            }
            case RETURNING -> {
                clip = "fly";
                rate = 0.9;
                r.health = Math.min(r.maxHealth, r.health + r.maxHealth * 0.0002);
                Location home = r.center.clone().add(0, 10.0, 0);
                Vector toHome = home.toVector().subtract(r.position.toVector());
                double dist = toHome.length();
                face(r, home);
                boolean watched = false;
                for (Player p : w.getPlayers()) {
                    if (p.getLocation().distanceSquared(r.position) <= 144 * 144) { watched = true; break; }
                }
                if (!watched && tick - r.returnStart > 60) {
                    snapHome(r);
                    return;
                }
                if (dist <= 2.0) {
                    r.position.setX(r.center.getX());
                    r.position.setZ(r.center.getZ());
                    r.state = State.LANDING;
                    r.stateUntil = tick + 30;
                } else {
                    Vector step = toHome.normalize().multiply(Math.min(dist, returnSpeed()));
                    r.position = avoid(r, r.position.clone().add(step), returnSpeed());
                }
                if (tick % 16 == 0) w.playSound(r.position, Sound.ENTITY_ENDER_DRAGON_FLAP, 2.0f, 0.8f);
            }
            case LANDING -> {
                clip = "land";
                double nextY = Math.max(r.center.getY(), r.position.getY() - 0.36);
                r.position.setY(nextY);
                if (tick >= r.stateUntil || nextY <= r.center.getY() + 0.05) {
                    r.position.setY(r.center.getY());
                    if (r.test) {
                        remove(r);
                        runs.remove(r.id);
                        return;
                    }
                    resetToFullAtNest(r);
                    return;
                }
            }
        }
        aura(r);
        if (r.visual != null) r.visual.frame(r.position, r.fighting ? target : null, clip, rate, explicit);
        placeRelays(r);
    }

    private void aura(Run r) {
        if (tick % 5 != 0) return;
        Location body = r.position.clone().add(0, r.grounded ? 9.0 : 12.0, 0);
        r.fx.core(body, r.phase >= 3 ? 12 : 6, 3.2, 0.03);
        r.fx.accent(body, r.phase >= 3 ? 4 : 2, 2.6, 0.02);
        if (r.phase >= 3 && tick % 10 == 0) {
            Vector side = facing(r).crossProduct(new Vector(0, 1, 0)).multiply(11);
            r.fx.pillar(r.position.clone().add(side), 18, 2);
            r.fx.pillar(r.position.clone().subtract(side), 18, 2);
        }
    }

    /** New players inside the hunt join the roster (and the optional party health). */
    private void recruit(Run r, List<Player> engaged) {
        for (Player p : engaged) {
            if (r.roster.size() < 16 && r.roster.add(p.getUniqueId()) && r.roster.size() > 1 && partyHealth() > 0) {
                double next = Math.min(healthCap(), r.maxHealth + partyHealth());
                r.health += next - r.maxHealth;
                r.maxHealth = next;
            }
        }
    }

    /** Players hunted outside the nest learn the escape rules once and see their distances. */
    private void notifyChase(Run r, List<Player> engaged) {
        for (Player p : engaged) {
            double fromNest = flat(p.getLocation(), r.center);
            if (fromNest <= r.kind.radius + 6) continue;
            r.hunted.add(p.getUniqueId());
            if (r.warned.add(p.getUniqueId())) {
                plugin.message(p, bossTitle(r.kind) + " · §cมังกรบินไล่ล่าคุณออกนอกรัง! §7หนีให้ห่างมันเกิน §f"
                    + (int) chaseRadius() + "§7 บล็อก หรือออกไปไกลกว่า §f" + (int) maxRoam() + "§7 บล็อกจากรัง");
            }
            if (tick % 40 == 0) {
                p.sendActionBar(Component.text("§c🐉 " + bossTitle(r.kind).split(" ")[0] + " ไล่ล่า §7· ห่างมังกร §f"
                    + (int) p.getLocation().distance(r.position) + "/" + (int) chaseRadius() + " §7· ห่างรัง §f"
                    + (int) fromNest + "/" + (int) maxRoam()));
            }
        }
    }

    /** Sticky target: keep the current prey while it is hunted, re-evaluating the nearest every 5 s. */
    private Player target(Run r, List<Player> engaged) {
        if (engaged.isEmpty()) return null;
        Player current = r.target == null ? null : Bukkit.getPlayer(r.target);
        if (current != null && engaged.contains(current) && tick < r.retarget) return current;
        Player nearest = engaged.stream().min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(r.position))).orElse(null);
        if (current != null && engaged.contains(current) && nearest != null
                && current.getLocation().distanceSquared(r.position) < 2.25 * nearest.getLocation().distanceSquared(r.position)) {
            nearest = current;
        }
        r.target = nearest == null ? null : nearest.getUniqueId();
        r.retarget = tick + 100;
        return nearest;
    }

    // --- movement ------------------------------------------------------------------------------------

    private static Vector facing(Run r) {
        double yaw = Math.toRadians(r.position.getYaw());
        return new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    private void face(Run r, Location target) {
        r.position.setYaw(ArkhaiaSteering.facingYaw(r.position.getYaw(), target.getX() - r.position.getX(), target.getZ() - r.position.getZ()));
    }

    private void faceNow(Run r, Vector dir) {
        if (dir.lengthSquared() < 1e-6) return;
        r.position.setYaw((float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ())));
    }

    /** The top of the ground below a point (searching down 64 blocks), or the point itself over the void. */
    private static Location ground(Location at) {
        World w = at.getWorld();
        int x = at.getBlockX(), z = at.getBlockZ();
        int top = Math.min(w.getMaxHeight() - 1, at.getBlockY() + 1);
        for (int y = top; y > Math.max(w.getMinHeight(), top - 64); y--) {
            if (!w.getBlockAt(x, y, z).isPassable()) return new Location(w, at.getX(), y + 1.12, at.getZ());
        }
        return new Location(w, at.getX(), at.getY() + 0.12, at.getZ());
    }

    private static boolean solid(World w, Location at) {
        return !w.getBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ()).isPassable();
    }

    /** Steps around terrain by climbing, keeps the dragon under the build limit and inside max-roam. */
    private Location avoid(Run r, Location next, double speed) {
        World w = r.center.getWorld();
        boolean blocked = solid(w, next.clone().add(0, 1, 0)) || solid(w, next.clone().add(0, 7, 0)) || solid(w, next.clone().add(0, 14, 0))
            || solid(w, next.clone().add(facing(r).multiply(10)).add(0, 4, 0));
        Location out = next;
        if (blocked) {
            out = r.position.clone().add(0, Math.max(0.5, speed), 0);
            out.setYaw(next.getYaw());
        }
        out.setY(Math.min(out.getY(), w.getMaxHeight() - 24));
        double roam = maxRoam(), dx = out.getX() - r.center.getX(), dz = out.getZ() - r.center.getZ(), d = Math.hypot(dx, dz);
        if (d > roam) {
            out.setX(r.center.getX() + dx * roam / d);
            out.setZ(r.center.getZ() + dz * roam / d);
        }
        return out;
    }

    private boolean onNestFloor(Run r, Player t) {
        return flat(t.getLocation(), r.center) < r.kind.arenaRadius - 2 && Math.abs(t.getY() - r.center.getY()) < 3.5
            && !t.isGliding() && !t.isFlying();
    }

    private void hunt(Run r, Player t, List<Player> engaged, String[] clip) {
        if (onNestFloor(r, t)) { r.groundTicks++; r.airTicks = 0; } else { r.airTicks++; r.groundTicks = 0; }
        double d = flat(t.getLocation(), r.position);
        boolean wantGround = r.groundTicks > 30 && d < 40 && flat(r.position, r.center) < r.kind.arenaRadius + 24;
        boolean wantAir = r.airTicks > 12 || d > 46;
        if (r.transition != null) {
            clip[0] = r.transition;
            face(r, t.getLocation());
            if (r.transition.equals("takeoff")) {
                r.position.add(0, 0.34, 0);
            } else {
                double remaining = Math.max(1, r.transitionUntil - tick);
                r.position.setY(Math.max(r.center.getY(), r.position.getY() - Math.max(0.2, (r.position.getY() - r.center.getY()) / remaining)));
            }
            if (tick >= r.transitionUntil) {
                r.grounded = r.transition.equals("land");
                if (r.grounded) r.position.setY(r.center.getY());
                r.transition = null;
                r.moveAt = Math.max(r.moveAt, tick + 10);
            }
            return;
        }
        if (r.grounded && wantAir) {
            r.transition = "takeoff";
            r.transitionUntil = tick + 30;
            r.center.getWorld().playSound(r.position, Sound.ENTITY_ENDER_DRAGON_FLAP, 4.0f, 0.6f);
            r.fx.ring(ground(r.position), 14, 1.0, 0, false);
            r.fx.both(Particle.GUST_EMITTER_LARGE, r.position.clone().add(0, 1, 0), 1, 0, 0);
            clip[0] = "takeoff";
            return;
        }
        if (!r.grounded && wantGround && r.position.getY() - r.center.getY() < 16 && flat(r.position, r.center) < r.kind.arenaRadius - 4) {
            r.transition = "land";
            r.transitionUntil = tick + 30;
            clip[0] = "land";
            return;
        }
        if (r.grounded) {
            clip[0] = walk(r, t) ? "walk" : "idle";
        } else {
            fly(r, t, wantGround);
            clip[0] = "fly";
            if (tick % 16 == 0) r.center.getWorld().playSound(r.position, Sound.ENTITY_ENDER_DRAGON_FLAP, 3.0f, 0.75f);
        }
        if (tick >= r.moveAt) chooseMove(r, t, engaged);
    }

    private boolean walk(Run r, Player t) {
        face(r, t.getLocation());
        r.position.setY(r.center.getY());
        Vector delta = t.getLocation().toVector().subtract(r.position.toVector()).setY(0);
        double dist = delta.length();
        if (dist <= 15.0) return false;
        Vector step = delta.normalize().multiply(Math.min(dist - 15.0, walkSpeed()));
        Location next = r.position.clone().add(step);
        if (flat(next, r.center) < r.kind.arenaRadius - 4) {
            r.position = next;
            return true;
        }
        return false;
    }

    /** Hovers at a bite-length standoff from the prey, circling it; sprints after prey that runs. */
    private void fly(Run r, Player t, boolean landing) {
        Location prey = t.getLocation();
        face(r, prey);
        Vector away = r.position.toVector().subtract(prey.toVector()).setY(0);
        if (away.lengthSquared() < 1e-4) away = facing(r).multiply(-1);
        double d = away.length();
        double standoff = 16.0;
        if (d <= standoff + 6) {
            if (tick % 200 == 0 && r.random.nextBoolean()) r.orbit = -r.orbit;
            away.rotateAroundY(0.012 * r.orbit);
        }
        Location desired = prey.clone().add(away.normalize().multiply(standoff));
        double floor = ground(desired.clone().add(0, 24, 0)).getY();
        double y = landing ? r.center.getY() + 4 : Math.max(prey.getY() - 1.0, floor + 2.0);
        if (d > 60) y = Math.max(y, prey.getY() + 5);
        y += Math.sin(tick * 0.07) * 0.6;
        desired.setY(y);
        Vector delta = desired.toVector().subtract(r.position.toVector());
        double dist = delta.length();
        double speed = d > 48 ? chaseSpeed() : flySpeed();
        if (r.phase >= 2) speed *= 1.08;
        if (dist > 0.05) {
            Vector step = delta.normalize().multiply(Math.min(dist, speed));
            Location next = r.position.clone().add(step);
            next.setYaw(r.position.getYaw());
            r.position = avoid(r, next, speed);
        }
    }

    // --- move selection ------------------------------------------------------------------------------

    private int gap(Run r) {
        return switch (r.phase) { case 1 -> 26 + r.random.nextInt(11); case 2 -> 18 + r.random.nextInt(11); default -> 12 + r.random.nextInt(9); };
    }

    private int magicCooldown(Run r) {
        return switch (r.phase) { case 1 -> 400; case 2 -> 300; default -> 220; } + r.random.nextInt(61);
    }

    private void chooseMove(Run r, Player t, List<Player> engaged) {
        if (r.novaQueued) {
            r.novaQueued = false;
            start(r, Move.NOVA, t);
            return;
        }
        Location prey = t.getLocation();
        Vector to = prey.toVector().subtract(r.position.toVector()).setY(0);
        double d = to.length(), dy = prey.getY() - r.position.getY();
        double angle = d < 1e-3 ? 0 : Math.toDegrees(facing(r).angle(to.clone().normalize()));
        boolean front = angle < 55, behind = angle > 110;
        int crowd = (int) engaged.stream().filter(p -> flat(p.getLocation(), r.position) < 24).count();
        Map<Move, Double> weights = new EnumMap<>(Move.class);
        if (d > 140) {
            // Far prey is simply chased down at full speed; volleys resume once it is in range.
            r.moveAt = tick + 10;
            return;
        }
        if (tick >= r.magicAt && d < 90) weights.put(Move.MAGIC, r.phase >= 2 ? 6.0 : 4.0);
        if (r.grounded) {
            if (front && d >= 9 && d <= 26) weights.put(Move.BITE, 3.2);
            if (front && d <= 19) weights.put(Move.CLAW, 3.0);
            if (behind && d < 28 || crowd >= 3 && d < 22) weights.put(Move.TAIL_WHIP, 3.0);
            else if (d < 22) weights.put(Move.TAIL_WHIP, 0.7);
            if (d < 26) weights.put(Move.WING_GUST, crowd >= 2 ? 2.4 : 1.1);
            if (d >= 8 && d <= 40) weights.put(Move.BREATH, 2.6);
            if (d > 26) weights.put(Move.BARRAGE, 2.0);
        } else {
            if (d >= 14 && d <= 80 && dy > -36) weights.put(Move.SWOOP, 3.0);
            if (d >= 10 && d <= 60) weights.put(Move.STRAFE, 1.8);
            if (d >= 18) weights.put(Move.BARRAGE, d > 60 ? 5.0 : 2.2);
            if (d >= 8 && d <= 34) weights.put(Move.BREATH, 2.2);
            if (front && d >= 9 && d <= 25 && Math.abs(dy) < 6) weights.put(Move.BITE, 2.6);
            if (front && d <= 17 && Math.abs(dy) < 7) weights.put(Move.CLAW, 1.6);
            if (d < 24) weights.put(Move.WING_GUST, 1.0);
        }
        if (r.lastMove != null && weights.containsKey(r.lastMove) && weights.size() > 1) weights.computeIfPresent(r.lastMove, (m, w) -> w * 0.35);
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) {
            r.moveAt = tick + 10;
            return;
        }
        double roll = r.random.nextDouble() * total;
        Move pick = null;
        for (var entry : weights.entrySet()) {
            roll -= entry.getValue();
            if (roll <= 0) { pick = entry.getKey(); break; }
        }
        if (pick == null) pick = weights.keySet().iterator().next();
        start(r, pick, t);
    }

    private void start(Run r, Move move, Player t) {
        // The move's first frame runs on the next dragon tick, which must be its age 0 (set-up, sounds, aim).
        Action a = new Action(move, tick + 1, t == null ? null : t.getUniqueId());
        r.action = a;
        r.state = State.MOVE;
        r.lastMove = move;
        if (move == Move.MAGIC) r.magicAt = tick + magicCooldown(r);
        if (t != null) {
            face(r, t.getLocation());
            a.dir = horizontal(t.getLocation().toVector().subtract(r.position.toVector()), facing(r));
            a.aimY = ground(t.getLocation().clone().add(0, 0.5, 0)).getY();
        }
    }

    private void finishMove(Run r) {
        r.action = null;
        r.state = State.HUNT;
        r.moveAt = tick + gap(r);
    }

    private static Vector horizontal(Vector v, Vector fallback) {
        Vector h = v.clone().setY(0);
        return h.lengthSquared() < 1e-6 ? fallback.clone() : h.normalize();
    }

    private Location bone(Run r, String name, String clip, double time) {
        Map<String, Location> points = BossHitbox.bonePoints(r.kind.modelId, clip, time, r.position, SCALE, Set.of(name, "head"));
        Location at = points.getOrDefault(name, points.get("head"));
        return at != null ? at : r.position.clone().add(facing(r).multiply(18)).add(0, 12, 0);
    }

    // --- damage --------------------------------------------------------------------------------------

    /**
     * One boss hit. Physical strikes (bite, claws, tail, dive) are armor-respecting mob attacks; magic (breath,
     * spells) skips armor. As with the realm bosses, a slice of each hit lands as a separate sonic rend that ignores
     * armor and Protection, so late-game enchantments cannot make a dragon harmless.
     */
    private boolean strike(Run r, Player p, double base, boolean physical) {
        if (!engaged(r, p) || !r.fighting || r.dead || r.anchor == null || !r.anchor.isValid()) return false;
        double scale = damageScale(r) * (r.phase >= 3 ? BossAdaptation.damageMultiplier(plugin, BossAdaptation.DRAGONS) : 1.0);
        double amount = Math.max(1.0, base * (physical ? 2.4 : 2.8) * scale);
        applying = true;
        try {
            p.setNoDamageTicks(0);
            boolean landed = BossDamage.hit(p, amount, source(r, physical ? DamageType.MOB_ATTACK : DamageType.INDIRECT_MAGIC), true);
            double rend = base * (physical ? 0.45 : 0.5) * rendScale() * scale;
            if (rend >= 0.5 && p.isOnline() && !p.isDead()) {
                p.setNoDamageTicks(0);
                landed |= BossDamage.hit(p, rend, source(r, DamageType.SONIC_BOOM), true);
            }
            return landed;
        } finally {
            applying = false;
        }
    }

    private DamageSource source(Run r, DamageType type) {
        return DamageSource.builder(type).withCausingEntity(r.anchor).withDirectEntity(r.anchor).withDamageLocation(r.position).build();
    }

    /** The element's status: fire burns, lightning shocks (slow + glow), frost freezes and slows. */
    private void afflict(Run r, Player p, int power) {
        switch (r.kind) {
            case FIRE -> BossDamage.ignite(p, 40 + 30 * power, bossTitle(r.kind));
            case STORM -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60 + 20 * power, 0, false, false, true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 + 10 * power, Math.min(2, power - 1), false, false, true));
            }
            case ICE -> {
                p.setFreezeTicks(Math.min(p.getMaxFreezeTicks() + 80, p.getFreezeTicks() + 40 + 40 * power));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40 + 20 * power, Math.min(3, power), false, false, true));
            }
        }
    }

    private static void knock(Player p, Location from, double horizontal, double vertical) {
        Vector v = p.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (v.lengthSquared() < 1e-4) v = new Vector(0, 0, 1);
        p.setVelocity(v.normalize().multiply(horizontal).setY(vertical));
    }

    private static double segmentDistance(Vector p, Vector a, Vector b) {
        Vector ab = b.clone().subtract(a);
        double len = ab.lengthSquared();
        double t = len < 1e-9 ? 0 : Math.clamp(p.clone().subtract(a).dot(ab) / len, 0, 1);
        return p.distance(a.clone().add(ab.multiply(t)));
    }

    private static Vector centerOf(Player p) {
        return p.getLocation().toVector().add(new Vector(0, 0.9, 0));
    }

    private void sound(Location at, Sound sound, float volume, float pitch) {
        at.getWorld().playSound(at, sound, SoundCategory.HOSTILE, volume, pitch);
    }

    private void hazard(Run r, Location from, Location to, double radius, double damage, int ticks) {
        if (r.hazards.size() >= 48) r.hazards.remove(0);
        r.hazards.add(new Hazard(from.clone(), to == null ? null : to.clone(), radius, damage, tick + ticks));
    }

    // --- moves: close combat -------------------------------------------------------------------------

    private void tickBite(Run r, Action a, long age, Player t, List<Player> engaged) {
        if (age == 0) {
            sound(r.position, Sound.ENTITY_ENDER_DRAGON_GROWL, 4.0f, 1.15f);
            sound(r.position, Sound.ENTITY_RAVAGER_ROAR, 2.5f, 0.6f);
        }
        if (age <= 8 && t != null) {
            face(r, t.getLocation());
            a.dir = horizontal(t.getLocation().toVector().subtract(r.position.toVector()), facing(r));
            double d = flat(t.getLocation(), r.position);
            a.lunge = Math.clamp(d - BITE_REACH, -2.0, 8.0);
            a.aimY = ground(t.getLocation().clone().add(0, 0.5, 0)).getY();
        }
        Location contact = r.position.clone().add(a.dir.clone().multiply(BITE_REACH + (age <= 8 ? a.lunge : 0)));
        contact.setY(r.grounded ? r.center.getY() + 0.12 : a.aimY);
        if (age <= BITE_SNAP && age % 2 == 0) {
            double shrink = Math.max(0.6, 5.0 * (1 - age / (double) BITE_SNAP));
            r.fx.ring(contact, 5.0, 0.9, tick * 0.1, true);
            r.fx.ring(contact, shrink, 0.8, -tick * 0.1, false);
        }
        if (age == 8) sound(r.position, Sound.ENTITY_WARDEN_SONIC_CHARGE, 2.0f, 0.6f);
        if (age > 8 && age <= BITE_SNAP) {
            r.position = avoid(r, r.position.clone().add(a.dir.clone().multiply(a.lunge / (BITE_SNAP - 8))), 0.6);
            Location mouth = bone(r, "fx_mouth", "bite", age / 20.0);
            r.fx.core(mouth, 4, 0.8, 0.05);
        }
        if (age == BITE_SNAP) {
            Location mouth = bone(r, "fx_mouth", "bite", BITE_SNAP / 20.0);
            r.fx.both(Particle.SONIC_BOOM, mouth, 1, 0, 0);
            r.fx.impact(mouth, 1.6, false);
            r.fx.ring(ground(mouth), 3.0, 0.7, 0, true);
            r.fx.ring(ground(mouth), 6.0, 0.9, 0.3, false);
            sound(mouth, Sound.ENTITY_EVOKER_FANGS_ATTACK, 3.0f, 0.45f);
            sound(mouth, Sound.ENTITY_RAVAGER_ATTACK, 3.0f, 0.6f);
            sound(mouth, Sound.ENTITY_PLAYER_ATTACK_CRIT, 2.0f, 0.6f);
            for (Player p : engaged) {
                if (centerOf(p).distance(mouth.toVector()) <= 6.2 && a.struck.add(p.getUniqueId())) {
                    if (strike(r, p, 16, true)) {
                        a.landed = true;
                        BossDamage.effect(p, new PotionEffect(PotionEffectType.WITHER, 60, 1, false, true, true), bossTitle(r.kind));
                        afflict(r, p, 1);
                        knock(p, r.position, 0.8, 0.55);
                        actionBar(List.of(p), "§4✖ ถูกเขี้ยวมังกรขย้ำ! §7(เลือดไหล)");
                    }
                }
            }
        }
        if (a.landed && age > BITE_SNAP && age <= BITE_SNAP + 6 && age % 2 == 0) {
            Location mouth = bone(r, "fx_mouth", "bite", age / 20.0);
            r.fx.both(Particle.CRIT, mouth, 14, 1.2, 0.4);
            r.fx.dust(mouth, 8, 1.0, true);
        }
    }

    /** The three parallel rake lines of one claw, on the ground in front of the dragon. */
    private List<Location[]> rake(Run r, Action a, boolean right) {
        Vector f = a.dir.clone(), side = new Vector(-f.getZ(), 0, f.getX());
        double y = r.grounded ? r.center.getY() + 0.12 : a.aimY;
        double s = right ? 1 : -1;
        Location base = r.position.clone();
        base.setY(y);
        Location from = base.clone().add(f.clone().multiply(5)).add(side.clone().multiply(-6 * s));
        Location to = base.clone().add(f.clone().multiply(21)).add(side.clone().multiply(4 * s));
        Vector along = to.toVector().subtract(from.toVector()).normalize();
        Vector normal = new Vector(-along.getZ(), 0, along.getX());
        List<Location[]> lines = new ArrayList<>();
        for (int i = -1; i <= 1; i++) {
            lines.add(new Location[]{from.clone().add(normal.clone().multiply(2.2 * i)), to.clone().add(normal.clone().multiply(2.2 * i))});
        }
        return lines;
    }

    private void tickClaw(Run r, Action a, long age, Player t, List<Player> engaged) {
        if (age == 0) {
            sound(r.position, Sound.ENTITY_RAVAGER_ROAR, 3.0f, 0.75f);
            sound(r.position, Sound.ENTITY_ENDER_DRAGON_GROWL, 3.0f, 1.0f);
        }
        if (age <= 6 && t != null) {
            face(r, t.getLocation());
            a.dir = horizontal(t.getLocation().toVector().subtract(r.position.toVector()), facing(r));
            a.aimY = ground(t.getLocation().clone().add(0, 0.5, 0)).getY();
        }
        if (age % 2 == 0) {
            if (age < CLAW_RIGHT) for (Location[] l : rake(r, a, true)) r.fx.line(l[0], l[1], 0.9, true);
            if (age < CLAW_LEFT) for (Location[] l : rake(r, a, false)) r.fx.line(l[0], l[1], 1.1, age >= CLAW_RIGHT);
        }
        if (age == CLAW_RIGHT || age == CLAW_LEFT) {
            boolean right = age == CLAW_RIGHT;
            List<Location[]> lines = rake(r, a, right);
            Vector swipe = lines.get(1)[1].toVector().subtract(lines.get(1)[0].toVector()).normalize();
            Location foot = bone(r, right ? "forefoot_r" : "forefoot_l", "claw", age / 20.0);
            sound(foot, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 3.0f, 0.45f);
            sound(foot, Sound.ENTITY_RAVAGER_ATTACK, 3.0f, 0.7f);
            sound(foot, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.3f);
            r.fx.impact(foot, 1.2, false);
            for (Location[] l : lines) {
                Vector d = l[1].toVector().subtract(l[0].toVector());
                double len = d.length();
                d.normalize();
                for (double s = 0; s <= len; s += 1.0) {
                    Location at = l[0].clone().add(d.clone().multiply(s)).add(0, 0.4, 0);
                    r.fx.core(at, 2, 0.25, 0.04);
                    if (((int) s) % 3 == 0) r.fx.both(Particle.CRIT, at, 3, 0.3, 0.3);
                    if (((int) s) % 4 == 0) r.fx.javaOnly(Particle.SWEEP_ATTACK, at.clone().add(0, 0.6, 0), 1, 0, 0);
                }
                r.fx.both(Particle.EXPLOSION, l[1].clone().add(0, 0.5, 0), 1, 0, 0);
                hazard(r, l[0], l[1], 1.25, 2.0, 70);
            }
            for (Player p : engaged) {
                if (a.struck.contains(p.getUniqueId()) && !right) a.struck.remove(p.getUniqueId());
                Vector c = centerOf(p);
                boolean hit = false;
                for (Location[] l : lines) {
                    if (Math.abs(p.getY() - l[0].getY()) <= 4.5 && segmentDistance(c.clone().setY(l[0].getY()), l[0].toVector(), l[1].toVector()) <= 1.9) hit = true;
                }
                if (!hit && c.distance(foot.toVector()) <= 4.5) hit = true;
                if (hit && a.struck.add(p.getUniqueId()) && strike(r, p, 12, true)) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 1, false, true, true));
                    afflict(r, p, 1);
                    p.setVelocity(swipe.clone().multiply(1.3).setY(0.38));
                    actionBar(List.of(p), "§c✖ กรงเล็บมังกรฉีกเกราะ! §7(อ่อนแรง)");
                }
            }
        }
    }

    private void tickTail(Run r, Action a, long age, List<Player> engaged) {
        Location floor = r.position.clone();
        floor.setY(r.grounded ? r.center.getY() + 0.12 : ground(r.position.clone().add(0, 1, 0)).getY());
        double yaw = Math.toRadians(r.position.getYaw());
        if (age == 0) sound(r.position, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 3.0f, 0.35f);
        if (age < TAIL_CONTACT && age % 2 == 0) r.fx.fan(floor, yaw + Math.toRadians(70), yaw + Math.toRadians(290), 6, 22);
        if (age == TAIL_CONTACT) {
            sound(r.position, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.65f);
            sound(r.position, Sound.ENTITY_BREEZE_WIND_BURST, 3.0f, 0.5f);
            for (int i = 0; i <= 10; i++) {
                double ang = yaw + Math.toRadians(70 + 22 * i);
                Location at = floor.clone().add(-Math.sin(ang) * 16, 0.6, Math.cos(ang) * 16);
                r.fx.burst(at, 1.6);
                if (i % 3 == 0) r.fx.both(Particle.EXPLOSION, at, 1, 0, 0);
            }
            Vector f = facing(r);
            for (Player p : engaged) {
                Vector to = p.getLocation().toVector().subtract(r.position.toVector()).setY(0);
                double d = to.length();
                double angle = d < 1e-3 ? 180 : Math.toDegrees(f.angle(to.normalize()));
                if (d <= 23 && angle >= 70 && Math.abs(p.getY() - floor.getY()) <= 6 && a.struck.add(p.getUniqueId()) && strike(r, p, 12, true)) {
                    knock(p, r.position, 1.8, 0.62);
                    afflict(r, p, 1);
                }
            }
        }
    }

    private void tickGust(Run r, Action a, long age, Player t, List<Player> engaged) {
        if (age == 0) sound(r.position, Sound.ENTITY_ENDER_DRAGON_FLAP, 5.0f, 0.5f);
        if (t != null && age < 8) face(r, t.getLocation());
        Location floor = r.position.clone();
        floor.setY(r.grounded ? r.center.getY() + 0.12 : ground(r.position.clone().add(0, 1, 0)).getY());
        double yaw = Math.toRadians(r.position.getYaw());
        if (age < 10 && age % 2 == 0) r.fx.fan(floor, yaw - Math.toRadians(60), yaw + Math.toRadians(60), 6, 28);
        if (age == 10 || age == 20) {
            a.struck.clear();
            Vector f = facing(r);
            sound(r.position, Sound.ENTITY_BREEZE_WIND_BURST, 4.0f, 0.55f);
            sound(r.position, Sound.ENTITY_ENDER_DRAGON_FLAP, 4.0f, 0.65f);
            for (int i = -1; i <= 1; i++) {
                Location at = r.position.clone().add(f.clone().rotateAroundY(Math.toRadians(i * 35)).multiply(14)).add(0, 3, 0);
                r.fx.both(Particle.GUST_EMITTER_LARGE, at, 1, 0, 0);
                r.fx.burst(at, 2.2);
            }
            for (Player p : engaged) {
                Vector to = p.getLocation().toVector().subtract(r.position.toVector()).setY(0);
                double d = to.length();
                double angle = d < 1e-3 ? 0 : Math.toDegrees(f.angle(to.normalize()));
                if (d <= 28 && angle <= 62 && a.struck.add(p.getUniqueId()) && strike(r, p, 6, false)) {
                    knock(p, r.position, 2.2, 0.55);
                    afflict(r, p, 1);
                }
            }
        }
    }

    // --- moves: breath -------------------------------------------------------------------------------

    /** The breath stream's end point for the current clip pose: along the neck's sweep, down to the locked aim. */
    private Location breathEnd(Run r, Action a, Location mouth, double distance) {
        Vector sweep = horizontal(mouth.toVector().subtract(r.position.toVector()), facing(r));
        Location end = mouth.clone().add(sweep.multiply(distance));
        end.setY(a.aimY + 0.3);
        Vector dir = end.toVector().subtract(mouth.toVector());
        double length = dir.length();
        if (length > 0.5) {
            RayTraceResult wall = mouth.getWorld().rayTraceBlocks(mouth, dir.normalize(), length, FluidCollisionMode.NEVER, true);
            if (wall != null) end = wall.getHitPosition().toLocation(mouth.getWorld());
        }
        return end;
    }

    private void breathSounds(Run r, Location at, long age) {
        if (age % 5 != 0) return;
        switch (r.kind) {
            case FIRE -> { sound(at, Sound.ENTITY_BLAZE_SHOOT, 2.5f, 0.6f); sound(at, Sound.BLOCK_FIRE_AMBIENT, 2.0f, 0.7f); }
            case STORM -> { sound(at, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.6f, 1.4f); sound(at, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.8f); }
            case ICE -> { sound(at, Sound.BLOCK_POWDER_SNOW_BREAK, 2.5f, 0.6f); sound(at, Sound.BLOCK_GLASS_BREAK, 1.2f, 1.6f); }
        }
    }

    /** One stream frame: particles, splash, ground hazards and damage every 2 ticks along the stream and splash. */
    private void stream(Run r, Action a, long age, Location mouth, Location end, List<Player> engaged) {
        r.fx.stream(mouth, end, 2.4);
        if (age % 2 == 0) r.fx.burst(end, 1.5);
        if (r.kind == DragonNestLayout.Kind.STORM && age % 8 == 0) end.getWorld().strikeLightningEffect(end);
        if (age % 4 == 0) hazard(r, end, null, 3.4, 2.2, 80);
        breathSounds(r, end, age);
        // Every other tick: the sweep crosses a player in about six ticks, so standing in it costs three hits.
        if (age % 2 != 0) return;
        Vector m = mouth.toVector(), e = end.toVector();
        double length = Math.max(1, m.distance(e));
        for (Player p : engaged) {
            Vector c = centerOf(p);
            Vector ab = e.clone().subtract(m);
            double along = Math.clamp(c.clone().subtract(m).dot(ab) / (length * length), 0, 1);
            double allowed = 1.6 + 2.4 * along;
            if (segmentDistance(c, m, e) <= allowed || c.distance(e) <= 5.2) {
                if (strike(r, p, 3.4, false)) afflict(r, p, 1);
            }
        }
    }

    private void tickBreath(Run r, Action a, long age, Player t, List<Player> engaged) {
        Location mouth = bone(r, "fx_mouth", "breath", age / 20.0);
        if (age == 0) {
            sound(r.position, Sound.ENTITY_BREEZE_INHALE, 4.0f, 0.45f);
            sound(r.position, Sound.ENTITY_ENDER_DRAGON_GROWL, 3.0f, 0.6f);
            double d = t == null ? 20 : flat(t.getLocation(), mouth);
            a.reach = Math.clamp(d, 10, 34);
        }
        if (age < BREATH_FROM) {
            // Inhale: the element gathers into the jaws while the fan marks the swept ground.
            for (int i = 0; i < 4; i++) {
                double ang = r.random.nextDouble() * Math.PI * 2, rad = 2.5 + r.random.nextDouble() * 3.5;
                r.fx.core(mouth.clone().add(Math.cos(ang) * rad, (r.random.nextDouble() - 0.5) * 3, Math.sin(ang) * rad), 1, 0.1, 0);
            }
            if (age % 4 == 0) {
                Location origin = r.position.clone();
                origin.setY(a.aimY);
                double yaw = Math.toRadians(r.position.getYaw());
                r.fx.fan(origin, yaw - Math.toRadians(42), yaw + Math.toRadians(42), 12, a.reach + 20);
            }
            return;
        }
        if (age <= BREATH_TO) stream(r, a, age, mouth, breathEnd(r, a, mouth, a.reach), engaged);
    }

    private void tickStrafe(Run r, Action a, long age, Player t, List<Player> engaged) {
        if (age == 0) {
            sound(r.position, Sound.ENTITY_BREEZE_INHALE, 4.0f, 0.45f);
            sound(r.position, Sound.ENTITY_ENDER_DRAGON_GROWL, 4.0f, 0.8f);
            Location prey = t == null ? r.position.clone().add(facing(r).multiply(30)) : t.getLocation();
            a.aim = prey.clone();
            a.aimY = ground(prey.clone().add(0, 0.5, 0)).getY();
            a.speed = Math.max(prey.getY() + 12, a.aimY + 12);
        }
        faceNow(r, a.dir);
        Location line0 = a.aim.clone().add(a.dir.clone().multiply(-22)), line1 = a.aim.clone().add(a.dir.clone().multiply(26));
        line0.setY(a.aimY);
        line1.setY(a.aimY);
        if (age < 18) {
            if (age % 3 == 0) {
                Vector side = new Vector(-a.dir.getZ(), 0, a.dir.getX()).multiply(4);
                r.fx.line(line0, line1, 1.0, true);
                r.fx.line(line0.clone().add(side), line1.clone().add(side), 1.6, false);
                r.fx.line(line0.clone().subtract(side), line1.clone().subtract(side), 1.6, false);
            }
            Location next = r.position.clone();
            next.setY(next.getY() + Math.clamp(a.speed - next.getY(), -0.8, 0.8));
            r.position = avoid(r, next, 0.8);
            return;
        }
        if (age <= 52) {
            Location next = r.position.clone().add(a.dir.clone().multiply(1.3));
            next.setY(next.getY() + Math.clamp(a.speed - next.getY(), -0.4, 0.4));
            next.setYaw(r.position.getYaw());
            r.position = avoid(r, next, 1.3);
            Location mouth = bone(r, "fx_mouth", "breath", Math.min(2.4, age / 20.0));
            Location below = mouth.clone().add(a.dir.clone().multiply(6));
            Location end = ground(below);
            if (end.getY() > mouth.getY()) end.setY(a.aimY);
            stream(r, a, age, mouth, end, engaged);
        }
    }

    private void tickBarrage(Run r, Action a, long age, Player t, List<Player> engaged) {
        // In the air the volleys are fired on the wing: the dragon keeps chasing while it shoots.
        if (t != null && !r.grounded) fly(r, t, false);
        Location mouth = bone(r, "fx_mouth", "roar", age / 20.0);
        if (t != null) face(r, t.getLocation());
        if (age == 0) sound(r.position, Sound.ENTITY_ENDER_DRAGON_GROWL, 4.0f, 0.75f);
        if (age < 12) r.fx.core(mouth, 6, 0.9, 0.06);
        if (t != null && age >= 12 && age <= 42 && (age - 12) % 10 == 0) {
            Location aim = t.getEyeLocation().add(t.getVelocity().clone().multiply(Math.min(18, mouth.distance(t.getEyeLocation()) / 1.5)));
            Vector dir = aim.toVector().subtract(mouth.toVector());
            if (dir.lengthSquared() < 0.01) dir = facing(r);
            dir.normalize();
            int count = r.phase >= 3 ? 5 : 3;
            for (int i = 0; i < count; i++) {
                double spread = (i - (count - 1) / 2.0) * 9.0;
                Vector v = dir.clone().rotateAroundY(Math.toRadians(spread)).multiply(1.55);
                if (r.bolts.size() < 48) r.bolts.add(new Bolt(mouth.clone(), v, t.getUniqueId(), tick + 80));
            }
            switch (r.kind) {
                case FIRE -> { sound(mouth, Sound.ENTITY_BLAZE_SHOOT, 3.0f, 0.55f); sound(mouth, Sound.ENTITY_GHAST_SHOOT, 2.0f, 0.6f); }
                case STORM -> { sound(mouth, Sound.ITEM_TRIDENT_THUNDER, 1.6f, 1.5f); sound(mouth, Sound.ENTITY_BREEZE_SHOOT, 2.0f, 0.7f); }
                case ICE -> { sound(mouth, Sound.BLOCK_GLASS_BREAK, 2.5f, 0.6f); sound(mouth, Sound.ENTITY_BREEZE_SHOOT, 2.0f, 0.5f); }
            }
            r.fx.burst(mouth, 1.6);
        }
    }

    // --- moves: dive ---------------------------------------------------------------------------------

    private void tickSwoop(Run r, Action a, long age, Player t, List<Player> engaged) {
        if (age == 0) {
            sound(r.position, Sound.ENTITY_ENDER_DRAGON_GROWL, 5.0f, 0.9f);
            sound(r.position, Sound.ENTITY_PHANTOM_SWOOP, 4.0f, 0.5f);
        }
        if (age < SWOOP_LOCK) {
            if (t != null) {
                face(r, t.getLocation());
                Location mark = ground(t.getLocation().clone().add(0, 0.5, 0));
                r.fx.ring(mark, 6.0, 0.9, tick * 0.1, true);
                r.fx.ring(mark, Math.max(1, 6.0 * (1 - age / (double) SWOOP_LOCK)), 0.8, 0, false);
            }
            // Wind-up: rear back and rise before the dive.
            Location next = r.position.clone().add(facing(r).multiply(-0.35)).add(0, 0.45, 0);
            next.setYaw(r.position.getYaw());
            r.position = avoid(r, next, 0.5);
            return;
        }
        if (age == SWOOP_LOCK) {
            Location prey = t == null ? r.position.clone().add(facing(r).multiply(24)) : t.getLocation().add(t.getVelocity().multiply(6));
            a.dir = horizontal(prey.toVector().subtract(r.position.toVector()), facing(r));
            Location anchorAim = prey.clone().subtract(a.dir.clone().multiply(BITE_REACH * 0.92));
            anchorAim.setY(Math.max(prey.getY() - 1.5, ground(prey.clone().add(0, 0.5, 0)).getY() - 0.5));
            a.from = r.position.clone();
            a.aim = anchorAim;
            Vector path = anchorAim.toVector().subtract(r.position.toVector());
            a.speed = Math.clamp(path.length() / SWOOP_DIVE, 1.2, 3.2);
            faceNow(r, a.dir);
            sound(r.position, Sound.ENTITY_ENDER_DRAGON_FLAP, 5.0f, 0.5f);
        }
        long dive = age - SWOOP_LOCK;
        if (dive > 0 && dive <= SWOOP_DIVE && !a.landed) {
            Vector path = a.aim.toVector().subtract(r.position.toVector());
            double left = path.length();
            Location next = left <= a.speed ? a.aim.clone() : r.position.clone().add(path.normalize().multiply(a.speed));
            next.setYaw(r.position.getYaw());
            if (solid(r.center.getWorld(), next.clone().add(0, 0.5, 0))) next = r.position.clone();
            r.position = next;
            r.fx.core(r.position.clone().add(0, 8, 0), 8, 3.0, 0.1);
            r.fx.smoke(r.position.clone().add(0, 6, 0), 2, 2.0);
        }
        if (dive == SWOOP_DIVE) {
            Location mouth = bone(r, "fx_mouth", "bite", SWOOP_DIVE / 20.0);
            Location floor = ground(mouth.clone().add(0, 1, 0));
            boolean low = mouth.getY() - floor.getY() < 6;
            r.fx.impact(mouth, 2.0, low);
            r.fx.both(Particle.SONIC_BOOM, mouth, 1, 0, 0);
            if (low) {
                r.fx.ring(floor, 4, 0.8, 0, true);
                r.fx.ring(floor, 8, 1.0, 0.2, true);
                r.fx.ring(floor, 12, 1.2, 0.4, false);
            }
            sound(mouth, Sound.ENTITY_GENERIC_EXPLODE, 3.5f, 0.7f);
            sound(mouth, Sound.ENTITY_EVOKER_FANGS_ATTACK, 3.0f, 0.45f);
            sound(mouth, Sound.ENTITY_RAVAGER_ATTACK, 3.0f, 0.6f);
            for (Player p : engaged) {
                Vector c = centerOf(p);
                boolean hit = c.distance(mouth.toVector()) <= 7.0 || c.distance(r.position.toVector().add(new Vector(0, 6, 0))) <= 7.5
                    || (low && Math.abs(p.getY() - floor.getY()) < 2.5 && flat(p.getLocation(), floor) <= 8);
                if (hit && a.struck.add(p.getUniqueId()) && strike(r, p, 15, true)) {
                    knock(p, mouth, 1.1, 1.0);
                    afflict(r, p, 1);
                    actionBar(List.of(p), "§4✖ มังกรโฉบขย้ำ!");
                }
            }
        }
        if (dive > SWOOP_DIVE) {
            Location next = r.position.clone().add(a.dir.clone().multiply(1.2)).add(0, 0.6, 0);
            next.setYaw(r.position.getYaw());
            r.position = avoid(r, next, 1.2);
        }
    }

    // --- moves: spells -------------------------------------------------------------------------------

    private String spellName(DragonNestLayout.Kind kind) {
        return switch (kind) {
            case FIRE -> "§6☄ ฝนดาวตกโลกันตร์ §7— หนีออกจากวงเพลิง!";
            case STORM -> "§e⚡ อัสนีพิพากษา §7— วงสายฟ้าตามตัวคุณ อย่าอยู่ใกล้เพื่อน!";
            case ICE -> "§b❄ ศูนย์องศาสัมบูรณ์ §7— หลบหนามน้ำแข็งและกระโดดข้ามคลื่นเยือกแข็ง!";
        };
    }

    private void tickMagic(Run r, Action a, long age, Player t, List<Player> engaged) {
        Location floor = r.position.clone();
        floor.setY(r.grounded ? r.center.getY() + 0.12 : ground(r.position.clone().add(0, 1, 0)).getY());
        if (t != null && age < CAST_RELEASE) face(r, t.getLocation());
        if (age == 0) {
            sound(r.position, Sound.BLOCK_END_PORTAL_SPAWN, 1.2f, 1.3f);
            sound(r.position, Sound.ENTITY_ENDER_DRAGON_GROWL, 4.0f, 0.55f);
            for (Player p : engaged) p.sendTitle("", spellName(r.kind), 4, 40, 10);
        }
        if (age < CAST_RELEASE) {
            if (age % 2 == 0) r.fx.rune(floor, 12, tick * 0.06);
            if (age % 6 == 0) {
                for (int i = 0; i < 5; i++) {
                    double ang = tick * 0.06 + i * 2 * Math.PI / 5;
                    r.fx.pillar(floor.clone().add(Math.cos(ang) * 11.4, 0, Math.sin(ang) * 11.4), 6 + age / 5.0, 1);
                }
            }
            Location mouth = bone(r, "fx_mouth", "cast", age / 20.0);
            r.fx.core(mouth, 6, 1.2, 0.05);
            if (age % 10 == 0) sound(r.position, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 2.5f, 0.6f);
            return;
        }
        if (age == CAST_RELEASE) {
            Location mouth = bone(r, "fx_mouth", "cast", age / 20.0);
            r.fx.impact(mouth, 2.0, true);
            r.fx.both(Particle.SONIC_BOOM, mouth, 1, 0, 0);
            sound(r.position, Sound.ENTITY_GENERIC_EXPLODE, 4.0f, 0.6f);
            sound(r.position, Sound.ENTITY_WITHER_SHOOT, 3.0f, 0.5f);
            List<Player> victims = engaged.stream().filter(p -> p.getLocation().distanceSquared(r.position) <= 100 * 100).toList();
            switch (r.kind) {
                case FIRE -> castMeteors(r, victims, floor);
                case STORM -> castJudgment(r, victims, floor);
                case ICE -> castAbsoluteZero(r, victims, floor);
            }
        }
    }

    private void castMeteors(Run r, List<Player> victims, Location floor) {
        int perPlayer = r.phase >= 3 ? 4 : 3;
        for (Player p : victims) {
            Location base = ground(p.getLocation().clone().add(0, 0.5, 0));
            for (int i = 0; i < perPlayer; i++) {
                Location at = base.clone();
                if (i > 0) {
                    double ang = r.random.nextDouble() * Math.PI * 2, dist = 4 + r.random.nextDouble() * 6;
                    at = ground(base.clone().add(Math.cos(ang) * dist, 6, Math.sin(ang) * dist));
                }
                meteor(r, at, 4.5, 14, false, 16 + i * 5);
            }
        }
        for (int i = 0; i < 4; i++) {
            double ang = r.random.nextDouble() * Math.PI * 2, dist = 10 + r.random.nextDouble() * 18;
            meteor(r, ground(floor.clone().add(Math.cos(ang) * dist, 8, Math.sin(ang) * dist)), 4.5, 14, false, 20 + i * 4);
        }
        if (r.phase >= 3 && !victims.isEmpty()) {
            Player p = victims.get(r.random.nextInt(victims.size()));
            meteor(r, ground(p.getLocation().clone().add(0, 0.5, 0)), 9.0, 22, true, 34);
        }
    }

    private void meteor(Run r, Location target, double radius, double damage, boolean big, int delay) {
        Vector drift = facing(r).multiply(-0.6).add(new Vector(r.random.nextDouble() - 0.5, 0, r.random.nextDouble() - 0.5).multiply(0.4));
        r.meteors.add(new Meteor(target, tick + delay, tick + delay + 20, radius, damage, big ? 44 : 32, big, drift));
    }

    private void castJudgment(Run r, List<Player> victims, Location floor) {
        for (Player p : victims) {
            r.sigils.add(new Sigil(p.getUniqueId(), ground(p.getLocation().clone().add(0, 0.5, 0)), tick + 22, tick + 36, 4.2, 13));
            if (r.phase >= 3) r.sigils.add(new Sigil(p.getUniqueId(), ground(p.getLocation().clone().add(0, 0.5, 0)), tick + 52, tick + 64, 4.2, 13));
        }
        for (int i = 0; i < 6; i++) {
            double ang = r.random.nextDouble() * Math.PI * 2, dist = 8 + r.random.nextDouble() * 18;
            r.sigils.add(new Sigil(null, ground(floor.clone().add(Math.cos(ang) * dist, 8, Math.sin(ang) * dist)), tick, tick + 26 + i * 3, 3.6, 11));
        }
    }

    private void castAbsoluteZero(Run r, List<Player> victims, Location floor) {
        Vector f = facing(r);
        Location start = floor.clone().add(f.clone().multiply(10));
        for (Player p : victims) {
            Location goal = ground(p.getLocation().clone().add(0, 0.5, 0));
            Vector dir = horizontal(goal.toVector().subtract(start.toVector()), f);
            double length = Math.min(48, flat(goal, start) + 8);
            int index = 0;
            for (double s = 0; s <= length; s += 2.6, index++) {
                Location at = ground(start.clone().add(dir.clone().multiply(s)).add(0, 10, 0));
                spike(r, at, 6 + index * 2, 11, 3);
            }
        }
        // A ring of spikes around the dragon and the freezing ground wave.
        for (int i = 0; i < 10; i++) {
            double ang = i * Math.PI * 2 / 10;
            spike(r, ground(floor.clone().add(Math.cos(ang) * 9, 8, Math.sin(ang) * 9)), 4, 11, 4);
        }
        r.waves.add(new Wave(floor, 3, 28, tick + 4, 24, 6));
        if (r.phase >= 3) {
            for (Player p : victims) meteor(r, ground(p.getLocation().clone().add(0, 0.5, 0)), 6.5, 18, true, 30);
        }
    }

    private void spike(Run r, Location at, int delay, double damage, int height) {
        if (r.spikes.size() >= 160) return;
        r.spikes.add(new Spike(at, tick + delay, tick + delay + 40, damage, height));
    }

    private void tickNova(Run r, Action a, long age, List<Player> engaged) {
        Location floor = r.position.clone();
        floor.setY(r.grounded ? r.center.getY() + 0.12 : ground(r.position.clone().add(0, 1, 0)).getY());
        if (age == 0) {
            sound(r.position, Sound.ENTITY_ENDER_DRAGON_GROWL, 6.0f, 0.45f);
            sound(r.position, Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.8f);
            String line = r.phase >= 3 ? "§4มังกรคลั่ง! §cพลังธาตุปะทุทั่วร่าง" : "§6มังกรโกรธเกรี้ยว! §eกระโดดหลบคลื่นกระแทก";
            for (Player p : engaged) p.sendTitle(shortName(r.kind), line, 4, 40, 10);
        }
        if (age < NOVA_RELEASE) {
            if (age % 2 == 0) {
                r.fx.rune(floor, 10, -tick * 0.08);
                r.fx.ring(floor, 34, 1.4, tick * 0.02, false);
            }
            if (age % 4 == 0) r.fx.pillar(r.position.clone(), 26, 2);
            return;
        }
        if (age == NOVA_RELEASE) {
            r.waves.add(new Wave(floor, 3, 36, tick, 18, 10));
            Vector f = facing(r);
            for (int i = 0; i < 3; i++) {
                r.fx.both(Particle.EXPLOSION_EMITTER, floor.clone().add(f.clone().rotateAroundY(i * 2.094).multiply(8)).add(0, 1, 0), 1, 0, 0);
            }
            r.fx.both(Particle.SONIC_BOOM, r.position.clone().add(0, 10, 0), 1, 0, 0);
            sound(r.position, Sound.ENTITY_GENERIC_EXPLODE, 5.0f, 0.5f);
            switch (r.kind) {
                case FIRE -> {
                    for (int i = 0; i < 8; i++) {
                        double ang = i * Math.PI / 4;
                        r.fx.pillar(floor.clone().add(Math.cos(ang) * 14, 0, Math.sin(ang) * 14), 16, 3);
                        hazard(r, floor.clone().add(Math.cos(ang) * 14, 0, Math.sin(ang) * 14), null, 3.5, 2.5, 100);
                    }
                }
                case STORM -> {
                    sound(r.position, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 4.0f, 0.6f);
                    for (int i = 0; i < 8; i++) {
                        double ang = i * Math.PI / 4;
                        r.center.getWorld().strikeLightningEffect(floor.clone().add(Math.cos(ang) * 18, 0, Math.sin(ang) * 18));
                    }
                }
                case ICE -> {
                    for (int i = 0; i < 12; i++) {
                        double ang = i * Math.PI / 6;
                        spike(r, ground(floor.clone().add(Math.cos(ang) * 12, 8, Math.sin(ang) * 12)), 2 + i, 11, 4);
                    }
                }
            }
        }
    }

    // --- world spells tick ---------------------------------------------------------------------------

    private void tickSpells(Run r, List<Player> engaged) {
        tickBolts(r, engaged);
        tickHazards(r, engaged);
        tickMeteors(r, engaged);
        tickSigils(r, engaged);
        tickSpikes(r, engaged);
        tickWaves(r, engaged);
    }

    private void tickBolts(Run r, List<Player> engaged) {
        World w = r.center.getWorld();
        for (var it = r.bolts.iterator(); it.hasNext(); ) {
            Bolt b = it.next();
            if (tick >= b.expires || !r.fighting) {
                it.remove();
                continue;
            }
            Player prey = Bukkit.getPlayer(b.target);
            if (prey != null && engaged.contains(prey)) {
                Vector want = prey.getEyeLocation().toVector().subtract(b.at.toVector());
                if (want.lengthSquared() > 1e-4) {
                    double speed = b.velocity.length();
                    Vector turn = b.velocity.clone().normalize().multiply(0.96).add(want.normalize().multiply(0.04));
                    b.velocity = turn.normalize().multiply(speed);
                }
            }
            Location next = b.at.clone().add(b.velocity);
            Location mid = b.at.clone().add(b.velocity.clone().multiply(0.5));
            r.fx.core(next, 4, 0.22, 0.02);
            r.fx.core(mid, 2, 0.18, 0.01);
            r.fx.accent(next, 1, 0.15, 0.01);
            if (tick % 2 == 0) r.fx.dust(mid, 2, 0.2, false);
            boolean hit = solid(w, next);
            Player struck = null;
            for (Player p : engaged) {
                if (p.getBoundingBox().expand(1.25).contains(next.toVector())) {
                    struck = p;
                    hit = true;
                    break;
                }
            }
            if (hit) {
                r.fx.impact(next, 1.2, false);
                sound(next, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 1.2f);
                for (Player p : engaged) {
                    if (p == struck || centerOf(p).distance(next.toVector()) <= 3.0) {
                        if (strike(r, p, 8, false)) afflict(r, p, 2);
                    }
                }
                it.remove();
            } else {
                b.at = next;
            }
        }
    }

    private void tickHazards(Run r, List<Player> engaged) {
        for (var it = r.hazards.iterator(); it.hasNext(); ) {
            Hazard h = it.next();
            if (tick >= h.until) {
                it.remove();
                continue;
            }
            if (tick % 4 == 0) {
                if (h.to == null) {
                    for (int i = 0; i < 3; i++) {
                        double ang = r.random.nextDouble() * Math.PI * 2, rad = r.random.nextDouble() * h.radius;
                        Location at = h.from.clone().add(Math.cos(ang) * rad, 0.2, Math.sin(ang) * rad);
                        r.fx.core(at, 1, 0.15, 0.02);
                    }
                    r.fx.accent(h.from.clone().add(0, 0.3, 0), 1, h.radius * 0.5, 0.01);
                    if (tick % 8 == 0) r.fx.ring(h.from, h.radius, 1.3, tick * 0.03, false);
                } else {
                    Vector d = h.to.toVector().subtract(h.from.toVector());
                    for (int i = 0; i < 3; i++) r.fx.core(h.from.clone().add(d.clone().multiply(r.random.nextDouble())).add(0, 0.25, 0), 1, 0.2, 0.02);
                }
            }
            for (Player p : engaged) {
                if (Math.abs(p.getY() - h.from.getY()) > 2.5) continue;
                Vector c = p.getLocation().toVector().setY(h.from.getY());
                double dist = h.to == null ? c.distance(h.from.toVector()) : segmentDistance(c, h.from.toVector(), h.to.toVector().setY(h.from.getY()));
                if (dist > h.radius) continue;
                long next = r.hazardHits.getOrDefault(p.getUniqueId(), 0L);
                if (tick < next) continue;
                r.hazardHits.put(p.getUniqueId(), tick + 10);
                if (strike(r, p, h.damage, false)) afflict(r, p, 1);
            }
        }
    }

    private void tickMeteors(Run r, List<Player> engaged) {
        for (var it = r.meteors.iterator(); it.hasNext(); ) {
            Meteor m = it.next();
            if (tick % 2 == 0) {
                r.fx.ring(m.target, m.radius, 0.9, tick * 0.08, true);
                double left = Math.clamp((m.impactAt - tick) / (double) (m.impactAt - m.fallAt + 16), 0, 1);
                r.fx.ring(m.target, Math.max(0.6, m.radius * left), 0.8, -tick * 0.08, false);
                if (m.big) r.fx.rune(m.target, m.radius * 0.7, tick * 0.05);
            }
            if (tick < m.fallAt) continue;
            double u = Math.clamp((tick - m.fallAt) / (double) (m.impactAt - m.fallAt), 0, 1);
            Location at = m.target.clone().add(m.drift.clone().multiply((1 - u) * m.height * 0.6)).add(0, m.height * (1 - u) + 0.5, 0);
            if (m.parts.isEmpty()) {
                Material coreBlock = r.kind == DragonNestLayout.Kind.ICE ? Material.BLUE_ICE : Material.MAGMA_BLOCK;
                Material shell = r.kind == DragonNestLayout.Kind.ICE ? Material.PACKED_ICE : Material.BLACKSTONE;
                m.parts.add(r.fx.prop(at, coreBlock));
                double s = m.big ? 1.0 : 0.65;
                for (Vector o : List.of(new Vector(s, 0, 0), new Vector(-s, 0, 0), new Vector(0, 0, s), new Vector(0, 0, -s), new Vector(0, s, 0))) {
                    m.parts.add(r.fx.prop(at.clone().add(o), shell));
                }
                if (m.big) {
                    for (Vector o : List.of(new Vector(0, -1, 0), new Vector(1, 1, 0), new Vector(-1, 1, 0), new Vector(0, 1, 1), new Vector(0, 1, -1))) {
                        m.parts.add(r.fx.prop(at.clone().add(o), coreBlock));
                    }
                }
                sound(at, Sound.ENTITY_BLAZE_SHOOT, 2.0f, 0.4f);
            }
            Location core = m.parts.get(0).getLocation();
            Vector shift = at.toVector().subtract(core.toVector());
            for (FallingBlock part : m.parts) DragonFx.place(part, part.getLocation().add(shift));
            r.fx.core(at, m.big ? 14 : 8, m.big ? 1.4 : 0.8, 0.05);
            r.fx.smoke(at.clone().add(0, 1.2, 0), m.big ? 4 : 2, 0.7);
            r.fx.accent(at, 2, 0.6, 0.02);
            if (tick < m.impactAt) continue;
            for (FallingBlock part : m.parts) part.remove();
            r.fx.impact(m.target.clone().add(0, 0.6, 0), m.big ? 3.0 : 1.8, true);
            r.fx.ring(m.target, m.radius * 0.6, 0.7, 0, true);
            r.fx.ring(m.target, m.radius, 0.8, 0.3, true);
            sound(m.target, Sound.ENTITY_GENERIC_EXPLODE, m.big ? 5.0f : 3.0f, m.big ? 0.5f : 0.75f);
            if (r.kind == DragonNestLayout.Kind.ICE) sound(m.target, Sound.BLOCK_GLASS_BREAK, 3.0f, 0.5f);
            for (Player p : engaged) {
                if (Math.abs(p.getY() - m.target.getY()) <= 4.5 && flat(p.getLocation(), m.target) <= m.radius && strike(r, p, m.damage, false)) {
                    knock(p, m.target, 0.9, 0.85);
                    afflict(r, p, m.big ? 3 : 2);
                }
            }
            hazard(r, m.target, null, m.radius * 0.8, 2.5, m.big ? 140 : 100);
            it.remove();
        }
    }

    private void tickSigils(Run r, List<Player> engaged) {
        World w = r.center.getWorld();
        for (var it = r.sigils.iterator(); it.hasNext(); ) {
            Sigil s = it.next();
            if (tick < s.lockAt && s.follow != null) {
                Player p = Bukkit.getPlayer(s.follow);
                if (p != null && engaged.contains(p)) s.at = ground(p.getLocation().clone().add(0, 0.5, 0));
            }
            if (tick % 2 == 0) {
                boolean locked = tick >= s.lockAt;
                r.fx.ring(s.at, s.radius, 0.8, tick * 0.15, locked);
                for (int i = 0; i < 4; i++) {
                    double ang = tick * 0.15 + i * Math.PI / 2;
                    r.fx.line(s.at, s.at.clone().add(Math.cos(ang) * s.radius, 0, Math.sin(ang) * s.radius), 1.0, false);
                }
                if (locked) {
                    double left = Math.clamp((s.strikeAt - tick) / (double) Math.max(1, s.strikeAt - s.lockAt), 0, 1);
                    r.fx.ring(s.at, Math.max(0.5, s.radius * left), 0.7, 0, true);
                    r.fx.both(Particle.ELECTRIC_SPARK, s.at.clone().add(0, 1, 0), 4, 0.8, 0.1);
                }
            }
            if (tick < s.strikeAt) continue;
            w.strikeLightningEffect(s.at);
            w.strikeLightningEffect(s.at.clone().add(0.6, 0, -0.4));
            r.fx.impact(s.at.clone().add(0, 0.5, 0), 1.6, false);
            r.fx.pillar(s.at, 22, 2);
            sound(s.at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3.0f, 0.8f);
            List<Player> chained = new ArrayList<>();
            for (Player p : engaged) {
                if (Math.abs(p.getY() - s.at.getY()) <= 5 && flat(p.getLocation(), s.at) <= s.radius && strike(r, p, s.damage, false)) {
                    afflict(r, p, 2);
                    knock(p, s.at, 0.5, 0.6);
                    chained.add(p);
                }
            }
            for (Player first : chained) chain(r, first, engaged);
            it.remove();
        }
    }

    /** Lightning jumps from a struck player to the nearest other hunted player within 12 blocks, up to three times. */
    private void chain(Run r, Player first, List<Player> engaged) {
        Set<UUID> done = new HashSet<>(Set.of(first.getUniqueId()));
        Player from = first;
        for (int jump = 0; jump < 3; jump++) {
            Player src = from;
            Player next = engaged.stream()
                .filter(p -> !done.contains(p.getUniqueId()) && p.getLocation().distanceSquared(src.getLocation()) <= 144)
                .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(src.getLocation()))).orElse(null);
            if (next == null) return;
            done.add(next.getUniqueId());
            Location a = from.getEyeLocation(), b = next.getEyeLocation();
            Vector d = b.toVector().subtract(a.toVector());
            double length = d.length();
            d.normalize();
            for (double s = 0; s <= length; s += 0.8) {
                Location at = a.clone().add(d.clone().multiply(s)).add((r.random.nextDouble() - 0.5) * 0.6, (r.random.nextDouble() - 0.5) * 0.6, (r.random.nextDouble() - 0.5) * 0.6);
                r.fx.both(Particle.END_ROD, at, 1, 0.02, 0);
                r.fx.both(Particle.ELECTRIC_SPARK, at, 2, 0.1, 0.02);
            }
            sound(b, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 2.0f, 1.2f);
            if (strike(r, next, 7, false)) afflict(r, next, 2);
            from = next;
        }
    }

    private void tickSpikes(Run r, List<Player> engaged) {
        for (var it = r.spikes.iterator(); it.hasNext(); ) {
            Spike s = it.next();
            if (tick < s.eruptAt) {
                if (s.eruptAt - tick <= 10 && tick % 2 == 0) r.fx.ring(s.at, 1.5, 0.6, tick * 0.2, true);
                continue;
            }
            long age = tick - s.eruptAt;
            if (age == 0) {
                for (int i = 0; i < s.height; i++) {
                    Material m = i == s.height - 1 ? Material.ICE : i == 0 ? Material.PACKED_ICE : Material.BLUE_ICE;
                    s.blocks.add(r.fx.prop(s.at.clone().add(0, i - s.height - 0.1, 0), m));
                }
                r.fx.burst(s.at.clone().add(0, 1, 0), 1.6);
                r.fx.both(Particle.ITEM_SNOWBALL, s.at.clone().add(0, 1.5, 0), 10, 0.6, 0.2);
                sound(s.at, Sound.BLOCK_GLASS_BREAK, 1.6f, 0.6f);
                sound(s.at, Sound.BLOCK_POWDER_SNOW_BREAK, 1.6f, 0.5f);
                for (Player p : engaged) {
                    double dy = p.getY() - s.at.getY();
                    if (dy >= -1.5 && dy <= 3.2 && flat(p.getLocation(), s.at) <= 2.3 && strike(r, p, s.damage, false)) {
                        p.setVelocity(new Vector(0, 0.75, 0));
                        afflict(r, p, 2);
                    }
                }
            }
            if (age <= 3) {
                double rise = s.height * Math.min(1, (age + 1) / 4.0);
                for (int i = 0; i < s.blocks.size(); i++) {
                    DragonFx.place(s.blocks.get(i), s.at.clone().add(0, i - s.height + rise - 0.1, 0));
                }
            } else if (tick % 10 == 0) {
                for (FallingBlock b : s.blocks) DragonFx.place(b, b.getLocation());
            }
            if (tick >= s.removeAt) {
                for (FallingBlock b : s.blocks) b.remove();
                r.fx.both(Particle.ITEM_SNOWBALL, s.at.clone().add(0, 1.5, 0), 12, 0.6, 0.15);
                r.fx.core(s.at.clone().add(0, 1.5, 0), 8, 0.6, 0.04);
                sound(s.at, Sound.BLOCK_GLASS_BREAK, 1.2f, 1.2f);
                it.remove();
            }
        }
    }

    private void tickWaves(Run r, List<Player> engaged) {
        for (var it = r.waves.iterator(); it.hasNext(); ) {
            Wave v = it.next();
            if (tick < v.start) continue;
            double u = (tick - v.start) / (double) v.ticks;
            if (u > 1) {
                it.remove();
                continue;
            }
            double radius = v.from + (v.to - v.from) * u;
            r.fx.ring(v.center, radius, 1.1, tick * 0.05, true);
            int bursts = Math.max(8, (int) (radius * 0.8));
            for (int i = 0; i < bursts; i++) {
                double ang = i * 2 * Math.PI / bursts + tick * 0.1;
                r.fx.core(v.center.clone().add(Math.cos(ang) * radius, 0.4, Math.sin(ang) * radius), 2, 0.3, 0.05);
            }
            for (Player p : engaged) {
                if (v.struck.contains(p.getUniqueId())) continue;
                double d = flat(p.getLocation(), v.center), dy = p.getY() - v.center.getY();
                if (Math.abs(d - radius) <= 1.9 && dy >= -2 && dy <= 2.1 && v.struck.add(p.getUniqueId()) && strike(r, p, v.damage, false)) {
                    knock(p, v.center, 2.2, 0.7);
                    afflict(r, p, 1);
                }
            }
        }
    }

    private void clearSpells(Run r) {
        r.bolts.clear();
        r.hazards.clear();
        r.sigils.clear();
        r.waves.clear();
        for (Meteor m : r.meteors) for (FallingBlock b : m.parts) b.remove();
        r.meteors.clear();
        for (Spike s : r.spikes) for (FallingBlock b : s.blocks) b.remove();
        r.spikes.clear();
    }

    // --- arrows --------------------------------------------------------------------------------------

    /**
     * Arrows are judged against the dragon's animated model, as the realm bosses do: its damage receivers cannot
     * collide (they would shove players), and Paper never lets a projectile hit a non-collidable entity. Each tick,
     * every player arrow near a dragon is traced from its last position, and one tick ahead, through the model.
     */
    private void tickArrows() {
        arrows.keySet().removeIf(a -> !a.isValid() || a.isInBlock());
        spent.removeIf(a -> !a.isValid());
        for (Run r : runs.values()) {
            if (r.dead || r.visual == null || r.anchor == null || !r.anchor.isValid()
                    || r.state == State.RETURNING || r.state == State.LANDING) continue;
            Location body = r.position.clone().add(0, 10, 0);
            if (!body.getWorld().isChunkLoaded(body.getBlockX() >> 4, body.getBlockZ() >> 4)) continue;
            for (AbstractArrow arrow : body.getWorld().getNearbyEntitiesByType(AbstractArrow.class, body, 48)) {
                if (!(arrow.getShooter() instanceof Player p) || !playable(p) || arrow.isInBlock() || spent.contains(arrow)) continue;
                Location now = arrow.getLocation();
                Location previous = arrows.getOrDefault(arrow, now.clone().subtract(arrow.getVelocity()));
                if (strikeArrow(r, arrow, p, previous, now) || strikeArrow(r, arrow, p, now, now.clone().add(arrow.getVelocity()))) {
                    arrows.remove(arrow);
                    continue;
                }
                arrows.put(arrow, now);
            }
        }
    }

    private boolean strikeArrow(Run r, AbstractArrow arrow, Player p, Location from, Location to) {
        if (from.getWorld() != to.getWorld()) return false;
        Vector segment = to.toVector().subtract(from.toVector());
        double length = segment.length();
        if (length < 1e-4) return false;
        double distance = BossHitbox.ray(from, segment.multiply(1 / length), r.kind.modelId, r.visual.clip(), r.visual.time(), r.position, SCALE, length);
        if (!Double.isFinite(distance)) return false;
        double damage = Math.ceil(Math.max(0.0, arrow.getDamage()) * Math.max(1.0, arrow.getVelocity().length()));
        if (arrow.isCritical()) damage += ThreadLocalRandom.current().nextInt((int) (damage / 2.0) + 2);
        double before = r.health;
        r.anchor.damage(damage, DamageSource.builder(DamageType.ARROW).withDirectEntity(arrow).withCausingEntity(p).build());
        if (r.health >= before) return false;
        if (arrow instanceof Trident || arrow.getPierceLevel() > 0) spent.add(arrow);
        else arrow.remove();
        return true;
    }

    // --- damage intake -------------------------------------------------------------------------------

    private Player attacker(EntityDamageEvent e) {
        if (e.getDamageSource().getCausingEntity() instanceof Player p) return p;
        if (e instanceof EntityDamageByEntityEvent b) {
            if (b.getDamager() instanceof Player p) return p;
            if (b.getDamager() instanceof Projectile s && s.getShooter() instanceof Player p) return p;
        }
        return null;
    }

    private boolean magicDamage() {
        var addon = Bukkit.getPluginManager().getPlugin("advance-magic");
        return addon instanceof com.example.advancemagic.AdvanceMagicPlugin m
            && m.isEnabled()
            && m.context() != null
            && m.context().isMagicDamage();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageEvent e) {
        Run r = owner(e.getEntity());
        if (r == null) return;
        double amount = e.getFinalDamage();
        e.setCancelled(true);
        Player p = attacker(e);
        if (!receiver(r, e.getEntity()) || p == null || !Double.isFinite(amount) || amount <= 0 || r.dead
                || r.state == State.RETURNING || r.state == State.LANDING) {
            return;
        }
        if (!r.fighting && !withinAggro(r, p) && p.getLocation().distanceSquared(r.position) > 96 * 96) return;
        if (r.fighting && !engaged(r, p)) return;
        DamageType type = e.getDamageSource().getDamageType();
        boolean physical = type == DamageType.PLAYER_ATTACK || type == DamageType.MOB_ATTACK || type.getKey().getKey().equals("mace_smash");
        if (e instanceof EntityDamageByEntityEvent b && b.getDamager() == p && physical && !magicDamage()
                && (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK || e.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)
                && !BossHitbox.confirmed(p, r.anchor)
                && !BossHitbox.melee(p, r.kind.modelId, r.visual.clip(), r.visual.time(), r.position, SCALE,
                    e.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) {
            return;
        }
        if (tick < r.hitCooldowns.getOrDefault(p.getUniqueId(), 0L)) return;
        if (!r.fighting) wake(r);
        r.hitCooldowns.put(p.getUniqueId(), tick + 8);
        double before = r.health;
        if (r.phase >= 3) amount = r.adaptation.phaseThree(plugin, e, p, amount, BossAdaptation.DRAGONS, bossTitle(r.kind), r.position.clone().add(0, 6, 0));
        r.health = Math.max(0.0, r.health - amount);
        r.contributors.add(p.getUniqueId());
        r.dealt.merge(p.getUniqueId(), before - r.health, Double::sum);
        ArkhaiaHitFeedback.confirm(p, r.anchor, e, before - r.health, false);
        if (e instanceof EntityDamageByEntityEvent b && b.getDamager() instanceof AbstractArrow arrow && !(arrow instanceof Trident) && arrow.getPierceLevel() == 0) {
            arrow.remove();
        }
        int phase = r.health <= r.maxHealth * 0.25 ? 3 : r.health <= r.maxHealth * 0.60 ? 2 : 1;
        if (phase > r.phase && r.health > 0) {
            r.phase = phase;
            r.novaQueued = true;
            r.moveAt = Math.min(r.moveAt, tick + 4);
        }
        bar(r, engaged(r));
        if (r.health <= 0.0) defeat(r);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void outgoing(EntityDamageByEntityEvent e) {
        if (!applying && owns(e.getDamager())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void prop(EntityChangeBlockEvent e) {
        if (e.getEntity() instanceof FallingBlock && e.getEntity().getPersistentDataContainer().has(plugin.key("dragon_prop"), PersistentDataType.BYTE)) {
            e.setCancelled(true);
            e.getEntity().remove();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void used(PlayerInteractEvent e) {
        if (e.getAction() != org.bukkit.event.block.Action.LEFT_CLICK_AIR) {
            interactions.put(e.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void usedEntity(PlayerInteractEntityEvent e) {
        interactions.put(e.getPlayer().getUniqueId(), Bukkit.getCurrentTick());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void swing(PlayerAnimationEvent e) {
        Player p = e.getPlayer();
        if (e.getAnimationType() != PlayerAnimationType.ARM_SWING || !playable(p)) return;
        int serverTick = Bukkit.getCurrentTick();
        Location eye = p.getEyeLocation();
        Run selected = null;
        double nearest = Double.POSITIVE_INFINITY;
        for (Run r : runs.values()) {
            if (!r.dead && r.state != State.RETURNING && r.state != State.LANDING && p.getWorld() == r.center.getWorld()
                    && p.getLocation().distanceSquared(r.position) <= 64 * 64
                    && tick >= r.hitCooldowns.getOrDefault(p.getUniqueId(), 0L) && r.visual != null) {
                double distance = BossHitbox.ray(p, r.kind.modelId, r.visual.clip(), r.visual.time(), r.position, SCALE, BossHitbox.reach(p));
                if (distance < nearest) {
                    nearest = distance;
                    selected = r;
                }
            }
        }
        if (selected == null) return;
        if (nearest > 0 && p.getWorld().rayTraceEntities(eye, eye.getDirection(), nearest, x -> x != p && x instanceof LivingEntity && !owns(x)) != null) {
            return;
        }
        Run r = selected;
        Slime receiver = r.anchor;
        double distance = nearest;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!closed && runs.get(r.id) == r && r.anchor == receiver
                    && interactions.getOrDefault(p.getUniqueId(), Integer.MIN_VALUE) < serverTick) {
                BossHitbox.attack(p, receiver, eye, distance);
            }
        });
    }

    // --- defeat / reset ------------------------------------------------------------------------------

    private void defeat(Run r) {
        if (r.dead) return;
        List<Player> witnesses = engaged(r);
        r.dead = true;
        r.fighting = false;
        r.state = State.IDLE;
        r.action = null;
        World w = r.center.getWorld();
        Location body = r.position.clone().add(0, 9, 0);
        r.fx.impact(body, 3.0, true);
        r.fx.pillar(r.position, 34, 4);
        r.fx.ring(ground(r.position.clone().add(0, 1, 0)), 20, 1.0, 0, true);
        w.playSound(r.position, Sound.ENTITY_ENDER_DRAGON_DEATH, 4.0f, 0.85f);
        w.playSound(r.position, Sound.ENTITY_GENERIC_EXPLODE, 4.0f, 0.5f);
        announce(r, witnesses, "§aผู้ครองรังมังกรพ่ายแพ้ — ตำนานบทใหม่ถูกจารึก");
        if (!r.test) {
            long respawnSeconds = plugin.integer("dragon-bosses.respawn-seconds", 1800, 60, 86400);
            r.readyAt = w.getGameTime() + respawnSeconds * 20L;
            ledger.set("cooldowns." + r.id, r.readyAt);
            save();
            for (UUID uuid : r.contributors) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline() || p.getWorld() != w || p.getLocation().distanceSquared(r.position) > 320 * 320) continue;
                var magic = Bukkit.getPluginManager().getPlugin("advance-magic");
                if (magic instanceof com.example.advancemagic.AdvanceMagicPlugin m && m.isEnabled()) {
                    m.skillTree().grantPoints(p, 5, bossTitle(r.kind));
                }
                if (plugin.seraphTrader() != null) {
                    plugin.seraphTrader().feathers().grant(p, 30, "พิชิต " + bossTitle(r.kind), false);
                }
                p.giveExp(2500);
            }
            grantScales(r);
        }
        clearSpells(r);
        destroy(r);
        releaseChunks(r);
        if (r.test) runs.remove(r.id);
    }

    /**
     * Dragon scales for the hunters: 1 for dealing at least 3% of its health, 2 from 12%, and one more for whoever
     * dealt the most. Scales trade at the Seraph for the dragon armor of the same element.
     */
    private void grantScales(Run r) {
        UUID top = r.dealt.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        for (var entry : r.dealt.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            double share = entry.getValue() / Math.max(1.0, r.maxHealth);
            if (p == null || !p.isOnline() || p.getWorld() != r.center.getWorld() || share < 0.03
                    || p.getLocation().distanceSquared(r.position) > 320 * 320) continue;
            int amount = 1 + (share >= 0.12 ? 1 : 0) + (entry.getKey().equals(top) ? 1 : 0);
            ItemStack scales = DragonScales.create(plugin, r.kind, amount);
            for (ItemStack left : p.getInventory().addItem(scales).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
            plugin.message(p, "§6ได้รับ " + DragonScales.title(r.kind) + " ×" + amount + " §7(ดาเมจ " + Math.round(share * 100) + "%)"
                + " · แลก" + DragonScales.set(r.kind) + "ได้ที่ Seraph");
        }
    }

    /** The dragon finished its flight home where nobody could watch: restore it in the nest (or drop the run). */
    private void snapHome(Run r) {
        World w = r.center.getWorld();
        if (r.test || !w.isChunkLoaded(r.center.getBlockX() >> 4, r.center.getBlockZ() >> 4)) {
            remove(r);
            runs.remove(r.id);
            return;
        }
        resetToFullAtNest(r);
    }

    private void resetToFullAtNest(Run r) {
        clearSpells(r);
        destroy(r);
        releaseChunks(r);
        r.maxHealth = rollHealth(r.kind);
        r.health = r.maxHealth;
        r.fighting = false;
        r.dead = false;
        r.state = State.IDLE;
        r.action = null;
        r.phase = 1;
        r.novaQueued = false;
        r.grounded = true;
        r.transition = null;
        r.target = null;
        r.hitCooldowns.clear();
        r.hazardHits.clear();
        r.contributors.clear();
        r.dealt.clear();
        r.roster.clear();
        r.hunted.clear();
        r.warned.clear();
        r.position = r.center.clone();
        spawn(r);
    }

    private void destroy(Run r) {
        r.bar.removeAll();
        if (r.visual != null) {
            r.visual.close();
            r.visual = null;
        }
        if (r.anchor != null) {
            r.anchor.remove();
            r.anchor = null;
        }
        for (Slime relay : r.relays) relay.remove();
        r.relays.clear();
    }

    private void remove(Run r) {
        clearSpells(r);
        destroy(r);
        releaseChunks(r);
    }

    private void save() {
        try {
            ledger.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Cannot save Dragon Nest boss cooldowns", e);
        }
    }

    // --- admin ---------------------------------------------------------------------------------------

    /** Admin test: a dragon that treats a spot 30 blocks ahead of the admin as its nest. No cooldown, no rewards. */
    public String spawnTest(Player admin, DragonNestLayout.Kind kind) {
        Vector ahead = horizontal(admin.getLocation().getDirection(), new Vector(0, 0, 1));
        Location spot = ground(admin.getLocation().clone().add(ahead.multiply(30)).add(0, 6, 0));
        var site = new DragonNestLayout.Site(kind, spot.getBlockX(), spot.getBlockZ());
        Run r = new Run(plugin, site, admin.getWorld(), Math.floor(spot.getY()), true, rollHealth(kind));
        Run old = runs.remove(r.id);
        if (old != null) remove(old);
        r.position.setYaw((float) Math.toDegrees(Math.atan2(ahead.getX(), -ahead.getZ())));
        runs.put(r.id, r);
        spawn(r);
        return bossTitle(kind) + " (ทดสอบ) · HP " + (int) r.maxHealth + " · ที่ " + spot.getBlockX() + ", " + spot.getBlockY() + ", " + spot.getBlockZ()
            + " · เข้าใกล้ " + (int) aggroRadius() + " บล็อกหรือโจมตีเพื่อปลุก";
    }

    public int clearTests() {
        int removed = 0;
        for (Run r : new ArrayList<>(runs.values())) {
            if (!r.test) continue;
            remove(r);
            runs.remove(r.id);
            removed++;
        }
        return removed;
    }

    public List<View> views() {
        List<View> out = new ArrayList<>();
        for (Run r : runs.values()) {
            out.add(new View(r.id, r.kind.shortId, r.state.name(), r.action == null ? "" : r.action.move.name(),
                r.visual == null ? "" : r.visual.clip(), r.health, r.maxHealth, r.phase, r.position.clone(), r.center.clone(), r.grounded, r.test,
                r.bolts.size() + r.hazards.size() + r.meteors.size() + r.sigils.size() + r.spikes.size() + r.waves.size()));
        }
        return out;
    }

    // --- events --------------------------------------------------------------------------------------

    @EventHandler
    public void load(ChunkLoadEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> discover(e.getChunk()), 2);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void unload(ChunkUnloadEvent e) {
        for (Run r : new ArrayList<>(runs.values())) {
            if (!r.fighting && r.state == State.IDLE && e.getWorld() == r.center.getWorld()
                    && e.getChunk().getX() == (r.center.getBlockX() >> 4)
                    && e.getChunk().getZ() == (r.center.getBlockZ() >> 4)) {
                remove(r);
                runs.remove(r.id);
            }
        }
    }

    @EventHandler
    public void split(SlimeSplitEvent e) {
        if (owns(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void heal(EntityRegainHealthEvent e) {
        if (owns(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void target(EntityTargetLivingEntityEvent e) {
        // The dragon never targets anything, and no mob may hunt one of its invisible hitboxes.
        if (owns(e.getEntity()) || owns(e.getTarget())) e.setCancelled(true);
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        interactions.remove(e.getPlayer().getUniqueId());
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        driver.cancel();
        for (Run r : runs.values()) remove(r);
        runs.clear();
        interactions.clear();
        arrows.clear();
        spent.clear();
        save();
    }
}
