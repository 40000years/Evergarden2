package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.enchant.UniqueAbilityListener;
import com.example.voidscape.world.DimensionalSanctuaries;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.data.BlockData;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Authoritative combat controller and dual-client (Java ModelEngine + Bedrock Geyser) animator
 * for the four Dimensional Sanctuary bosses:
 * <ul>
 *   <li><b>Cinder Colossus</b> ({@code crown-fortress}, Nether)</li>
 *   <li><b>Soul-Eater Hound</b> ({@code bone-temple}, Nether)</li>
 *   <li><b>Crystal Moth Queen</b> ({@code crystal-city}, The End)</li>
 *   <li><b>Unlit Sun</b> ({@code void-ring}, The End)</li>
 * </ul>
 */
public final class RealmBossController implements AutoCloseable {
    private static final Set<String> LOCATORS = Set.of(
        "fx_core", "fx_hand_r", "fx_hand_l", "fx_mouth", "fx_head", "fx_tail",
        "fx_wing_r", "fx_wing_l", "fx_orb0", "fx_orb1", "fx_orb2", "fx_orb3"
    );

    public record Defeat(RealmBosses.Kind kind, DimensionalSanctuaries.Kind sanctuaryKind, Set<UUID> contributors) {}

    private enum Action {
        NONE, SPAWN, TRANSITION, STUN,
        // Cinder Colossus
        COLOSSUS_FIST, COLOSSUS_FISSURE, COLOSSUS_RUBBLE, COLOSSUS_ERUPTION, COLOSSUS_QUAKE,
        // Soul-Eater Hound
        HOUND_FANG, HOUND_BURROW, HOUND_PREY_POUNCE, HOUND_HOWL, HOUND_REND,
        // Crystal Moth Queen
        MOTH_WING_SLASH, MOTH_SCALE_RAIN, MOTH_PRISM_CRYSTALS, MOTH_CHRYSALIS, MOTH_SHARD_NOVA,
        // Unlit Sun
        SUN_SOLAR_GAZE, SUN_RING_STORM, SUN_MONOLITHS, SUN_TOTAL_ECLIPSE, SUN_FALLING_STARS
    }

    private static final class HotZone {
        final Location center;
        final double radius;
        final double damage;
        int remainingTicks;
        int armTicks;
        HotZone(Location center, double radius, int remainingTicks) {
            this(center, radius, remainingTicks, 0, 3.5);
        }
        HotZone(Location center, double radius, int remainingTicks, int armTicks, double damage) {
            this.center = center.clone();
            this.radius = radius;
            this.remainingTicks = remainingTicks;
            this.armTicks = armTicks;
            this.damage = damage;
        }
    }

    private static final class RubbleStrike {
        final Location target;
        final double radius;
        final double damage;
        final double emberRadius;
        int timer;
        BlockDisplay boulder;
        RubbleStrike(Location target, double radius, int delayTicks, double damage, double emberRadius) {
            this.target = target.clone();
            this.radius = radius;
            this.timer = delayTicks;
            this.damage = damage;
            this.emberRadius = emberRadius;
        }
    }

    /** Realm Brand: every landed hit makes the next one from this boss hurt more until it lapses. */
    private static final class Brand {
        int stacks;
        long expires;
    }

    private static final class DestructibleNode {
        final Location loc;
        final Vector velocity;
        double orbitSpeed; // radians per tick around the arena centre
        double hp;
        Interaction hitbox;
        BlockDisplay display;
        boolean alive = true;
        DestructibleNode(Location loc, Vector velocity, double hp) {
            this.loc = loc.clone();
            this.velocity = velocity.clone();
            this.hp = hp;
        }
        void remove() {
            alive = false;
            if (hitbox != null) { hitbox.remove(); hitbox = null; }
            if (display != null) { display.remove(); display = null; }
        }
    }

    private static final class ExpandingRing {
        final Location center;
        double radius;
        final double maxRadius;
        final double speed;
        final double gapBaseAngle;
        final int gapCount;
        final double gapHalfWidthRad;
        final double damage;
        final Color color;
        final Set<UUID> hitPlayers = new HashSet<>();
        ExpandingRing(Location center, double startRadius, double maxRadius, double speed,
                      double gapBaseAngle, int gapCount, double gapHalfWidthRad, double damage, Color color) {
            this.center = center.clone();
            this.radius = startRadius;
            this.maxRadius = maxRadius;
            this.speed = speed;
            this.gapBaseAngle = gapBaseAngle;
            this.gapCount = gapCount;
            this.gapHalfWidthRad = gapHalfWidthRad;
            this.damage = damage;
            this.color = color;
        }
    }

    private final VoidscapePlugin plugin;
    private final DimensionalSanctuaries.Site site;
    private final DimensionalSanctuaries.Arena arena;
    private final RealmBosses.Kind kind;
    private final Location center;
    private final Predicate<Player> insideArena;
    private final Consumer<Defeat> onDefeat;
    private final EndAntiAir endAntiAir;
    private final NetherAntiAir netherAntiAir;
    private final PerchHunt perchHunt;

    private Mob carrier;
    private Slime anchor;
    private ArmorStand bedrockCarrier;
    private ArkhaiaModelSupport.Rig rig;
    private BossAura aura;
    private final ArkhaiaLook look = new ArkhaiaLook();
    private BossBar bossBar;
    private final Set<UUID> contributors = new LinkedHashSet<>();
    private final Set<UUID> participants = new LinkedHashSet<>();
    // Vanilla living targets have a 10-tick hurt-resistance window. Our virtual
    // HP receiver has no native invulnerability, so restore that window per attacker.
    private final Map<UUID, Integer> lastMeleeHit = new HashMap<>();
    // Boss tick of each player's last accepted hit: hit-and-run attackers stay hunted after leaving the arena
    private final Map<UUID, Long> lastAttack = new HashMap<>();
    private final Map<UUID, Integer> lastWardNotice = new HashMap<>();

    private Location at;
    private String currentClip = "idle";
    private long clipStartedTick;
    private long tick;
    private boolean fighting;
    private boolean phaseTwo;
    /** Phase wall: from a phase threshold until the boss has unleashed that phase's signature skill, it takes no damage. */
    private boolean warded;
    private Action wardSkill;
    private boolean dead;
    private boolean hiddenUnderground;
    private double health;
    private double maxHealth;

    private Action action = Action.NONE;
    private int actionTick;
    private int cooldownTicks = 30;
    private int moveCycle;
    private int stunTicks;
    private double stunDamageMultiplier = 1.75;

    // Locked telegraph vectors/positions
    private Location lockedOrigin;
    private Vector lockedDir = new Vector(0, 0, 1);
    private Location lockedTarget;
    private UUID markedPreyId;
    private boolean preyHitDuringCombo;
    private double lockedYawRad;

    // Combo sequencing: strike N of a move starts at stepStart; each strike lands once per player
    private int comboStep;
    private int stepStart;
    private int dashTell;
    private final Set<UUID> struck = new HashSet<>();

    // Final phase (30% HP), Realm Brand stacks and movement tracking for leading strikes
    private static final int MAX_BRAND = 5;
    private static final int BRAND_TICKS = 140;
    private boolean phaseThree;
    /** Phlegethon's weapon adaptation, active in phase three. */
    private final BossAdaptation adaptation = new BossAdaptation();
    private int ambientTicks;
    private double coronaAngle;
    private int nodeTotal;
    private final Map<UUID, Long> coronaHits = new HashMap<>();
    private final Map<UUID, Brand> brands = new HashMap<>();
    private final Map<UUID, Location> lastSeen = new HashMap<>();
    private final Map<UUID, Vector> motion = new HashMap<>();

    // Boss-specific state & sub-objects
    private final List<HotZone> hotZones = new ArrayList<>();
    private final List<RubbleStrike> rubbleStrikes = new ArrayList<>();
    private final List<DestructibleNode> activeNodes = new ArrayList<>();
    private final List<ExpandingRing> activeRings = new ArrayList<>();

    // Cinder Colossus Furnace Core interrupt
    private boolean coreExposed;
    private double coreInterruptDamage;
    private double coreInterruptThreshold = 220.0;
    private double safeLaneBaseAngle;

    // Crystal Moth Queen Cracked-Wing Gauge & Low Hover
    private boolean lowHoverWindow;
    private int lowHoverTicks;
    private double wingCrackGauge; // 0..100
    private int safeSectorIndex;
    private double scaleFanBaseYaw;

    // Unlit Sun Opened-Eye Window & Eclipse Polarity
    private boolean eyeOpenWindow;
    private int eyeOpenTicks;
    private double eclipseAxisAngle;
    private boolean eclipseShadowSafe; // true = shadow half safe, false = light half safe

    public RealmBossController(
            VoidscapePlugin plugin,
            DimensionalSanctuaries.Site site,
            DimensionalSanctuaries.Arena arena,
            Location center,
            Predicate<Player> insideArena,
            Consumer<Defeat> onDefeat) {
        this.plugin = plugin;
        this.site = site;
        this.arena = arena;
        this.kind = switch (site.kind()) {
            case CROWN_FORTRESS -> RealmBosses.Kind.CINDER_COLOSSUS;
            case BONE_TEMPLE -> RealmBosses.Kind.SOUL_HOUND;
            case CRYSTAL_CITY -> RealmBosses.Kind.CRYSTAL_MOTH;
            case VOID_RING -> RealmBosses.Kind.UNLIT_SUN;
        };
        this.center = center.clone();
        this.insideArena = insideArena;
        this.onDefeat = onDefeat;
        this.endAntiAir = (kind == RealmBosses.Kind.CRYSTAL_MOTH || kind == RealmBosses.Kind.UNLIT_SUN)
            ? new EndAntiAir(kind, (player, damage) -> dealBossDamage(player, damage, DamageType.INDIRECT_MAGIC)) : null;
        this.netherAntiAir = (kind == RealmBosses.Kind.CINDER_COLOSSUS || kind == RealmBosses.Kind.SOUL_HOUND)
            ? new NetherAntiAir(kind, (player, damage) -> dealBossDamage(player, damage, DamageType.INDIRECT_MAGIC)) : null;
        this.perchHunt = plugin.getConfig().getBoolean("dimensional-sanctuaries.bosses.perch-hunt.enabled", true)
            ? new PerchHunt(kind, this.center, arena.radius(),
                (int) Math.round(20 * plugin.getConfig().getDouble("dimensional-sanctuaries.bosses.perch-hunt.grace-seconds", 3.0)),
                new PerchHunt.Hooks() {
                    @Override public boolean strike(Player player, double damage) {
                        boolean landed = dealBossDamage(player, damage, DamageType.INDIRECT_MAGIC);
                        if (landed) rider(player);
                        return landed;
                    }
                    @Override public int clear(Location at, double radius) {
                        var sanctuaries = plugin.dimensionalSanctuaries();
                        return sanctuaries == null ? 0 : sanctuaries.clearForeign(at.getWorld(), site, at, radius, 1, 2);
                    }
                }) : null;
        this.at = center.clone();
        this.maxHealth = configuredSoloHealth();
        this.health = this.maxHealth;
    }

    public RealmBosses.Kind kind() { return kind; }
    public String thaiName() {
        return switch (kind) {
            case CINDER_COLOSSUS -> "ป้อมปราการมีชีวิต";
            case SOUL_HOUND -> "นักล่ากระดูกวิญญาณ";
            case CRYSTAL_MOTH -> "ราชินีผลึกแห่งนครร้าง";
            case UNLIT_SUN -> "ดวงอาทิตย์ดับเหนือวงแหวน";
        };
    }
    public String englishName() { return kind.title; }
    public double modelScale() {
        return switch (kind) {
            case CINDER_COLOSSUS, UNLIT_SUN -> 3.6;
            case SOUL_HOUND -> 2.8;
            case CRYSTAL_MOTH -> 3.5;
        };
    }
    public double eyeHeight() {
        return switch (kind) {
            case CINDER_COLOSSUS -> 10.6;
            case SOUL_HOUND -> 4.9;
            case CRYSTAL_MOTH -> 8.0;
            case UNLIT_SUN -> 8.1;
        };
    }
    public Location location() { return at.clone(); }
    public boolean isDead() { return dead; }
    public boolean isFighting() { return fighting; }
    public boolean isPhaseTwo() { return phaseTwo; }
    public boolean isPhaseThree() { return phaseThree; }
    public double health() { return health; }
    public double maxHealth() { return maxHealth; }
    public double wingCrackGauge() { return wingCrackGauge; }
    public boolean isEyeOpen() { return eyeOpenWindow; }
    public boolean isLowHover() { return lowHoverWindow; }
    public boolean isCoreExposed() { return coreExposed; }

    private String configKey() {
        return kind.model.replace('_', '-');
    }

    private double configuredSoloHealth() {
        double def = switch (kind) {
            case CINDER_COLOSSUS -> 3800.0;
            case SOUL_HOUND -> 3200.0;
            case CRYSTAL_MOTH -> 3400.0;
            case UNLIT_SUN -> 3600.0;
        };
        return Math.max(200.0, plugin.getConfig().getDouble("dimensional-sanctuaries.bosses." + configKey() + ".health", def));
    }

    /**
     * Each extra challenger adds party-health-percent of the solo health (default 70%), up to party-health-max times
     * the solo health (default 3.8x, i.e. five players). A full late-game party could otherwise erase the old
     * +25%-per-player pool before the boss finished one skill rotation. Solo health is unchanged.
     */
    private double configuredExtraPlayerHealth() {
        double percent = plugin.getConfig().getDouble("dimensional-sanctuaries.bosses.party-health-percent", 70.0);
        return configuredSoloHealth() * Math.clamp(Double.isFinite(percent) ? percent : 70.0, 0.0, 500.0) / 100.0;
    }

    private double configuredHealthCap() {
        double times = plugin.getConfig().getDouble("dimensional-sanctuaries.bosses.party-health-max", 3.8);
        return configuredSoloHealth() * Math.clamp(Double.isFinite(times) ? times : 3.8, 1.0, 20.0);
    }

    private double damageScale() {
        double global = plugin.getConfig().getDouble("dimensional-sanctuaries.bosses.damage-percent", 100.0);
        double specific = plugin.getConfig().getDouble("dimensional-sanctuaries.bosses." + configKey() + ".damage-percent", 100.0);
        return Math.clamp((global / 100.0) * (specific / 100.0), 0.1, 5.0);
    }

    /** Vertical offset for aerial models so their elevated Blockbench geometry sits within melee reach. */
    private double modelYOffset() {
        if (hiddenUnderground) return -8.0;
        return switch (kind) {
            case CRYSTAL_MOTH -> (lowHoverWindow || action == Action.STUN) ? -3.35 : -2.45;
            case UNLIT_SUN -> (eyeOpenWindow || action == Action.STUN) ? -4.15 : -3.10;
            default -> 0.0;
        };
    }

    public Location renderLocation() {
        return at.clone().add(0, modelYOffset(), 0);
    }

    public void spawnWaiting(long nowTick) {
        hideBossBar();
        closeEntities();
        this.tick = nowTick;
        this.fighting = false;
        this.phaseTwo = false;
        this.phaseThree = false;
        this.adaptation.reset();
        this.warded = false;
        this.wardSkill = null;
        this.dead = false;
        this.hiddenUnderground = false;
        this.at = center.clone();
        this.maxHealth = configuredSoloHealth();
        this.health = this.maxHealth;
        this.action = Action.NONE;
        this.actionTick = 0;
        this.cooldownTicks = 24;
        this.stunTicks = 0;
        this.wingCrackGauge = 0;
        this.lowHoverWindow = false;
        this.lowHoverTicks = 0;
        this.eyeOpenWindow = false;
        this.eyeOpenTicks = 0;
        this.coreExposed = false;
        this.coreInterruptDamage = 0;
        this.comboStep = 0;
        this.stepStart = 0;
        this.ambientTicks = 0;
        this.coronaAngle = 0;
        this.struck.clear();
        this.coronaHits.clear();
        this.brands.clear();
        this.lastSeen.clear();
        this.motion.clear();
        this.contributors.clear();
        this.participants.clear();
        this.lastMeleeHit.clear();
        this.lastAttack.clear();

        ensureEntities();
        playClip("idle", true);
        syncTransforms();
    }

    /**
     * Ensures the underlying proxy entities exist without resetting combat state, HP, or aggression.
     */
    private void ensureEntities() {
        World world = center.getWorld();
        Location render = renderLocation();
        if (anchor == null || !anchor.isValid()) {
            if (rig != null) {
                rig.close();
                rig = null;
            }
            if (anchor != null) {
                anchor.remove();
            }
            anchor = world.spawn(render, Slime.class, stand -> {
                stand.setSize(1);
                stand.setAI(false);
                stand.setInvisible(true);
                stand.setGravity(false);
                stand.setCollidable(false);
                stand.setSilent(true);
                stand.setPersistent(false);
                stand.setInvulnerable(true);
                stand.setRemoveWhenFarAway(false);
                var healthAttr = stand.getAttribute(Attribute.MAX_HEALTH);
                if (healthAttr != null) healthAttr.setBaseValue(1024.0);
                stand.setHealth(1024.0);
                stand.setMaximumNoDamageTicks(0);
                stand.setNoDamageTicks(0);
                stand.getPersistentDataContainer().set(plugin.key("world_boss_entity"), PersistentDataType.BYTE, (byte) 1);
                stand.getPersistentDataContainer().set(plugin.key("realm_sanctuary_boss"), PersistentDataType.BYTE, (byte) 1);
            });
            if (plugin.arkhaiaModels() != null) {
                rig = plugin.arkhaiaModels().attachBoss(anchor, kind.model, modelScale());
            }
        }
        if (carrier == null || !carrier.isValid()) {
            if (carrier != null) {
                carrier.remove();
            }
            carrier = world.spawn(at, WitherSkeleton.class, mob -> {
                BossDamage.tag(mob,kind.title);
                mob.setPersistent(false);
                mob.setRemoveWhenFarAway(false);
                mob.setSilent(true);
                mob.setAI(false);
                mob.setCollidable(false);
                mob.setGravity(false);
                mob.setInvisible(true);
                mob.setCanPickupItems(false);
                if (mob.getEquipment() != null) mob.getEquipment().clear();
                var scaleAttr = mob.getAttribute(Attribute.SCALE);
                if (scaleAttr != null) {
                    scaleAttr.setBaseValue(switch (kind) {
                        case CINDER_COLOSSUS -> 2.1;
                        case SOUL_HOUND -> 1.75;
                        case CRYSTAL_MOTH -> 2.0;
                        case UNLIT_SUN -> 2.05;
                    });
                }
                var healthAttr = mob.getAttribute(Attribute.MAX_HEALTH);
                if (healthAttr != null) healthAttr.setBaseValue(1024.0);
                mob.setHealth(1024.0);
                mob.setMaximumNoDamageTicks(0);
                mob.setNoDamageTicks(0);
                mob.getPersistentDataContainer().set(plugin.key("world_boss_entity"), PersistentDataType.BYTE, (byte) 1);
                mob.getPersistentDataContainer().set(plugin.key("realm_sanctuary_boss"), PersistentDataType.BYTE, (byte) 1);
            });
        }
        if (aura == null) {
            aura = new BossAura(plugin, auraStyle());
        }
        var bridge = plugin.arkhaiaBedrock();
        if (bridge != null && bridge.ready() && (bedrockCarrier == null || !bedrockCarrier.isValid())) {
            if (bedrockCarrier != null) {
                bridge.unregister(bedrockCarrier.getUniqueId());
                bedrockCarrier.remove();
            }
            bedrockCarrier = world.spawn(render, ArmorStand.class, e -> {
                e.setPersistent(false);
                e.setGravity(false);
                e.setInvulnerable(true);
                e.setSilent(true);
                e.setVisibleByDefault(false);
                e.setVisible(true);
                e.setBasePlate(false);
                e.setCollidable(false);
                bridge.register(e.getUniqueId(), kind.model);
                e.getPersistentDataContainer().set(plugin.key("world_boss_entity"), PersistentDataType.BYTE, (byte) 1);
                e.getPersistentDataContainer().set(plugin.key("realm_sanctuary_boss"), PersistentDataType.BYTE, (byte) 1);
            });
        }
    }

    public void awaken(List<Player> initialPlayers) {
        if (fighting || dead) return;
        fighting = true;
        contributors.clear();
        participants.clear();
        for (Player p : initialPlayers) participants.add(p.getUniqueId());
        recalculatePartyScaling();
        bossBar = BossBar.bossBar(barTitle(), 1.0f, barColor(), BossBar.Overlay.NOTCHED_10);
        updateBossBarViewers(initialPlayers);
        startAction(Action.SPAWN);
        center.getWorld().playSound(at, Sound.ENTITY_WITHER_SPAWN, 1.2f, 0.75f);
    }

    private void recalculatePartyScaling() {
        int extra = Math.max(0, participants.size() - 1);
        double targetMax = Math.min(configuredHealthCap(), configuredSoloHealth() + extra * configuredExtraPlayerHealth());
        if (targetMax > maxHealth) {
            double ratio = maxHealth <= 0 ? 1.0 : health / maxHealth;
            maxHealth = targetMax;
            health = Math.min(maxHealth, Math.max(1.0, ratio * maxHealth));
            if (carrier != null && carrier.isValid()) {
                carrier.setHealth(1024.0);
            }
        }
    }

    private BossBar.Color barColor() {
        return switch (kind) {
            case CINDER_COLOSSUS -> BossBar.Color.RED;
            case SOUL_HOUND -> BossBar.Color.BLUE;
            case CRYSTAL_MOTH -> BossBar.Color.PINK;
            case UNLIT_SUN -> BossBar.Color.YELLOW;
        };
    }

    private Component barTitle() {
        String sub = switch (kind) {
            case CINDER_COLOSSUS -> coreExposed ? " §c[แกนหลังเปิด — โจมตีด้านหลังเพื่อตัดท่า!]"
                : (phaseThree ? " §4[เฟส 3: มงกุฎหลอมเหลว]" : phaseTwo ? " §6[เฟส 2: เตาหลอมคลั่ง]" : "");
            case SOUL_HOUND -> !activeNodes.isEmpty() ? " §b[ทำลายลูกไฟวิญญาณ " + activeNodes.size() + "/" + nodeTotal + "!]"
                : (phaseThree ? " §4[เฟส 3: ความหิวแห่งวิญญาณ]" : phaseTwo ? " §b[เฟส 2: อสูรวิญญาณคลั่ง]" : "");
            case CRYSTAL_MOTH -> (phaseThree ? " §4[เฟส 3]" : "") + (lowHoverWindow
                ? " §d[บินต่ำ — เกจปีกแตกร้าว " + (int) Math.round(wingCrackGauge) + "%]"
                : " §7[ปีกแตกร้าว " + (int) Math.round(wingCrackGauge) + "%]");
            case UNLIT_SUN -> eyeOpenWindow ? " §e[ดวงตาเปิด — โบนัสดาเมจ!]"
                : (phaseThree ? " §4[เฟส 3: โคโรนากวาดลาน]" : phaseTwo ? " §e[เฟส 2: สุริยุปราคาเต็มดวง]" : "");
        };
        if (action == Action.STUN) sub = " §a[มึนงง — โอกาสโจมตีรุนแรง!]";
        if (warded) sub = " §e[🛡 เกราะเฟส — รอให้สกิลจบ]";
        return BossNames.styled(englishName())
            .append(Component.text(" (" + (int) Math.ceil(health) + "/" + (int) Math.ceil(maxHealth) + ")" + sub, NamedTextColor.WHITE));
    }

    private void updateBossBarViewers(List<Player> present) {
        if (bossBar == null) return;
        bossBar.name(barTitle());
        bossBar.progress((float) Math.clamp(health / Math.max(1.0, maxHealth), 0.0, 1.0));
        for (Player p : present) {
            if (participants.add(p.getUniqueId())) recalculatePartyScaling();
            p.showBossBar(bossBar);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!present.contains(p)) p.hideBossBar(bossBar);
        }
    }

    public boolean owns(Entity entity) {
        if (entity == null) return false;
        if (entity.equals(carrier) || entity.equals(anchor) || entity.equals(bedrockCarrier)) return true;
        for (DestructibleNode node : activeNodes) {
            if (entity.equals(node.hitbox) || entity.equals(node.display)) return true;
        }
        return false;
    }

    public Mob carrier() { return carrier; }

    /** Players admitted to this fight, including those who subsequently left its ring. */
    Set<UUID> participants() { return Set.copyOf(participants); }

    /** A fallen player is no longer a target, even if they respawn before the fight ends. */
    void playerDefeated(UUID player) {
        lastAttack.remove(player);
        lastMeleeHit.remove(player);
        brands.remove(player);
        if (perchHunt != null) perchHunt.forget(player);
    }

    private void playClip(String clip, boolean force) {
        if (!force && clip.equals(currentClip)) return;
        currentClip = clip;
        clipStartedTick = tick;
    }

    private double clipSeconds(long atTick) {
        return Math.max(0, atTick - clipStartedTick) / 20.0;
    }

    private BossAura.Style auraStyle() {
        return switch (kind) {
            case CINDER_COLOSSUS -> BossAura.Style.COLOSSUS;
            case SOUL_HOUND -> BossAura.Style.HOUND;
            case CRYSTAL_MOTH -> BossAura.Style.MOTH;
            case UNLIT_SUN -> BossAura.Style.SUN;
        };
    }

    private void syncTransforms() {
        Location render = renderLocation();
        if (anchor != null && anchor.isValid()) {
            anchor.teleport(render);
            anchor.setRotation(at.getYaw(), 0);
        }
        if (carrier != null && carrier.isValid()) {
            Location carrierLoc;
            if (hiddenUnderground) {
                carrierLoc = at.clone().add(0, -10.0, 0);
            } else if (kind == RealmBosses.Kind.CRYSTAL_MOTH || kind == RealmBosses.Kind.UNLIT_SUN) {
                double hoverOffset = (lowHoverWindow || eyeOpenWindow || action == Action.STUN) ? 0.0 : 0.45;
                carrierLoc = at.clone().add(0, hoverOffset, 0);
            } else {
                carrierLoc = at;
            }
            carrier.teleport(carrierLoc);
            carrier.setRotation(at.getYaw(), 0);
        }
        Player target = nearestTarget(presentPlayers());
        var gaze = look.update(
            at.getYaw(),
            target == null ? 0 : target.getX() - render.getX(),
            target == null ? 0 : target.getEyeLocation().getY() - render.getY() - eyeHeight(),
            target == null ? 0 : target.getZ() - render.getZ(),
            target != null,
            currentClip.equals("stun")
        );
        Set<UUID> accepted = new HashSet<>();
        List<Player> viewers = new ArrayList<>();
        for (Player p : render.getWorld().getPlayers()) {
            boolean near = Math.hypot(p.getX() - render.getX(), p.getZ() - render.getZ()) < 110;
            boolean bedrock = JudgePackService.bedrock(p);
            if (near) viewers.add(p);
            if (near && !bedrock && (plugin.judgePack() == null || plugin.judgePack().applied(p))) accepted.add(p.getUniqueId());
            if (bedrockCarrier != null && bedrockCarrier.isValid()) {
                if (near && bedrock && !hiddenUnderground) {
                    if (!p.canSee(bedrockCarrier)) p.showEntity(plugin, bedrockCarrier);
                } else if (p.canSee(bedrockCarrier)) {
                    p.hideEntity(plugin, bedrockCarrier);
                }
            }
        }
        if (rig != null) {
            rig.viewers(hiddenUnderground ? Set.of() : accepted);
            rig.look(gaze.yaw(), gaze.pitch());
            rig.clip(currentClip, 1.0, render);
        }
        double secs = clipSeconds(tick);
        double clipLen = Math.max(0.4, BossHitbox.clipLength(kind.model, currentClip));
        double sampled = Set.of("idle", "walk").contains(currentClip) ? secs % clipLen : Math.min(secs, clipLen);
        if (bedrockCarrier != null && bedrockCarrier.isValid()) {
            bedrockCarrier.teleport(render);
            bedrockCarrier.setRotation(at.getYaw(), 0);
            if (plugin.arkhaiaBedrock() != null) {
                plugin.arkhaiaBedrock().riverFrame(bedrockCarrier.getUniqueId(), currentClip, (float) sampled, 1.0f, gaze, viewers);
            }
        }
        if (!hiddenUnderground && aura != null) {
            boolean intense = currentClip.contains("_active") || currentClip.equals("transition") || phaseTwo;
            aura.frame(render, currentClip, sampled, tick, modelScale(), intense);
        }
    }

    public List<Player> presentPlayers() {
        return center.getWorld().getPlayers().stream().filter(insideArena).toList();
    }

    public void tick(long nowTick) {
        if (dead) return;
        this.tick = nowTick;
        if (carrier == null || !carrier.isValid() || anchor == null || !anchor.isValid()) {
            ensureEntities();
        }
        if (carrier != null && carrier.isValid()) {
            if (carrier.getHealth() < 1024.0) carrier.setHealth(1024.0);
            carrier.setNoDamageTicks(0);
            carrier.setFireTicks(0);
        }
        if (anchor != null && anchor.isValid()) {
            if (anchor.getHealth() < 1024.0) anchor.setHealth(1024.0);
            anchor.setNoDamageTicks(0);
            anchor.setFireTicks(0);
        }
        List<Player> players = presentPlayers();
        if (!fighting) {
            playClip("idle", false);
            syncTransforms();
            return;
        }

        updateBossBarViewers(players);
        trackMotion(players);
        tickHotZones(players);
        tickRubbleStrikes(players);
        tickDestructibleNodes(players);
        tickExpandingRings(players);
        tickFinalPhase(players);

        if (lowHoverTicks > 0) {
            lowHoverTicks--;
            lowHoverWindow = lowHoverTicks > 0;
        } else {
            lowHoverWindow = false;
        }
        if (eyeOpenTicks > 0) {
            eyeOpenTicks--;
            eyeOpenWindow = eyeOpenTicks > 0;
        } else {
            eyeOpenWindow = false;
        }

        if (!phaseTwo && health <= maxHealth * 0.65 && action != Action.TRANSITION && action != Action.SPAWN) {
            phaseTwo = true;
            warded = true;
            wardSkill = moves().get(3);
            startAction(Action.TRANSITION);
        } else if (phaseTwo && !phaseThree && health <= maxHealth * 0.30
                && action != Action.TRANSITION && action != Action.SPAWN && action != Action.STUN) {
            // The final phase waits for an earned stun to play out
            phaseThree = true;
            warded = true;
            wardSkill = moves().get(4);
            startAction(Action.TRANSITION);
        }

        // Aerial counters get their own warning window; don't start a regular
        // attack until the counter fires or is interrupted by a stun/transition.
        boolean available = action == Action.NONE;
        boolean aerialWarning = endAntiAir != null
            ? endAntiAir.tick(players, at, center, available)
            : netherAntiAir.tick(players, at, center, available);
        // Ledge campers are hunted alongside every move, so a perch never waits for the boss to be free
        if (perchHunt != null && action != Action.SPAWN) perchHunt.tick(huntTargets(players), at);

        if (action != Action.NONE) {
            tickAction(players);
        } else if (!aerialWarning) {
            if (cooldownTicks > 0) cooldownTicks--;
            Player target = nearestTarget(players);
            if (target != null) {
                moveTowardOrCircle(target);
                if (cooldownTicks <= 0) chooseNextAction();
            } else {
                playClip("idle", false);
            }
        }
        syncTransforms();
    }

    /** Present players, plus anyone who struck the boss in the last 15 seconds and then left the arena to hide. */
    private List<Player> huntTargets(List<Player> present) {
        if (lastAttack.isEmpty()) return present;
        List<Player> targets = new ArrayList<>(present);
        for (Player p : center.getWorld().getPlayers()) {
            Long struck = lastAttack.get(p.getUniqueId());
            if (struck == null || tick - struck > 300 || targets.contains(p)) continue;
            double dy = p.getY() - center.getY();
            if (dy < -8.0 || dy > arena.height() + 28.0) continue;
            if (Math.hypot(p.getX() - center.getX(), p.getZ() - center.getZ()) > arena.radius() + 48.0) continue;
            targets.add(p);
        }
        return targets;
    }

    private Player nearestTarget(List<Player> players) {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : players) {
            double d = p.getLocation().distanceSquared(at);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    private void faceLocation(Location target) {
        double dx = target.getX() - at.getX();
        double dz = target.getZ() - at.getZ();
        if (dx * dx + dz * dz > 1e-4) {
            at.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        }
    }

    private void moveTowardOrCircle(Player target) {
        faceLocation(target.getLocation());
        double dist = Math.hypot(target.getX() - at.getX(), target.getZ() - at.getZ());
        double desiredMin = switch (kind) {
            case CINDER_COLOSSUS -> 3.8;
            case SOUL_HOUND -> 3.0;
            case CRYSTAL_MOTH -> 4.2;
            case UNLIT_SUN -> 6.0;
        };
        double speed = switch (kind) {
            case CINDER_COLOSSUS -> 0.19;
            case SOUL_HOUND -> 0.33;
            case CRYSTAL_MOTH -> 0.26;
            case UNLIT_SUN -> 0.17;
        };
        if (dist > desiredMin) {
            Vector dir = new Vector(target.getX() - at.getX(), 0, target.getZ() - at.getZ()).normalize().multiply(speed);
            stepInsideArena(dir);
            playClip("walk", false);
        } else {
            playClip("idle", false);
        }
    }

    private boolean stepInsideArena(Vector delta) {
        double nx = at.getX() + delta.getX();
        double nz = at.getZ() + delta.getZ();
        double maxR = Math.max(4.0, arena.radius() - 2.2);
        double d = Math.hypot(nx - center.getX(), nz - center.getZ());
        boolean hitWall = false;
        if (d > maxR) {
            double scale = maxR / d;
            nx = center.getX() + (nx - center.getX()) * scale;
            nz = center.getZ() + (nz - center.getZ()) * scale;
            hitWall = true;
        }
        at.setX(nx);
        at.setY(center.getY());
        at.setZ(nz);
        return hitWall;
    }

    // =========================================================================
    // MOVE SELECTION
    // =========================================================================

    /** Each cycle: attack, skill, pressure move, skill, attack, ultimate (indexes into {@link #moves()}). */
    private static final int[] ROTATION = {0, 1, 4, 2, 0, 3};

    /** Canonical order for {@code /sanctuary boss skill N}: attack, skill, skill, ultimate, pressure move. */
    private List<Action> moves() {
        return switch (kind) {
            case CINDER_COLOSSUS -> List.of(Action.COLOSSUS_FIST, Action.COLOSSUS_FISSURE, Action.COLOSSUS_RUBBLE, Action.COLOSSUS_ERUPTION, Action.COLOSSUS_QUAKE);
            case SOUL_HOUND -> List.of(Action.HOUND_FANG, Action.HOUND_BURROW, Action.HOUND_PREY_POUNCE, Action.HOUND_HOWL, Action.HOUND_REND);
            case CRYSTAL_MOTH -> List.of(Action.MOTH_WING_SLASH, Action.MOTH_SCALE_RAIN, Action.MOTH_PRISM_CRYSTALS, Action.MOTH_CHRYSALIS, Action.MOTH_SHARD_NOVA);
            case UNLIT_SUN -> List.of(Action.SUN_SOLAR_GAZE, Action.SUN_RING_STORM, Action.SUN_MONOLITHS, Action.SUN_TOTAL_ECLIPSE, Action.SUN_FALLING_STARS);
        };
    }

    private void chooseNextAction() {
        startAction(moves().get(ROTATION[Math.floorMod(moveCycle++, ROTATION.length)]));
    }

    public void forceSkill(int slot) {
        List<Action> list = moves();
        startAction(list.get(Math.floorMod(slot, list.size())));
    }

    private void startAction(Action next) {
        this.action = next;
        this.actionTick = 0;
        this.stepStart = 0;
        this.comboStep = 0;
        this.coreExposed = false;
        this.hiddenUnderground = false;
        struck.clear();
        List<Player> players = presentPlayers();
        Player target = nearestTarget(players);
        if (target != null) faceLocation(target.getLocation());

        switch (next) {
            case SPAWN -> playClip("spawn", true);
            case TRANSITION -> {
                clearActiveNodes();
                playClip("transition", true);
                if (phaseThree) {
                    broadcast(players, "§4✦ " + thaiName() + " เข้าสู่เฟสสุดท้าย — " + finalPhaseWarning());
                    center.getWorld().playSound(at, Sound.ENTITY_WITHER_SPAWN, 1.2f, 0.6f);
                } else {
                    broadcast(players, "§6✦ " + thaiName() + " เข้าสู่เฟสที่ 2 — พลังแห่งวิหารปะทุรุนแรงขึ้น!");
                    center.getWorld().playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.3f, 0.7f);
                }
            }
            case STUN -> {
                playClip("stun", true);
                center.getWorld().playSound(at, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.65f);
                center.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, at.clone().add(0, 2.2, 0), 35, 0.9, 0.9, 0.9, 0.15);
            }
            case COLOSSUS_FIST -> beginColossusSlam(target);
            case COLOSSUS_FISSURE -> {
                lockedYawRad = Math.toRadians(at.getYaw());
                lockedOrigin = at.clone();
                playClip("skill_windup", true);
                center.getWorld().playSound(at, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.4f, 0.55f);
            }
            case COLOSSUS_RUBBLE -> {
                playClip("skill_windup", true);
                center.getWorld().playSound(at, Sound.BLOCK_DEEPSLATE_BRICKS_BREAK, 1.4f, 0.6f);
            }
            case COLOSSUS_QUAKE -> {
                playClip("skill_windup", true);
                center.getWorld().playSound(at, Sound.ENTITY_RAVAGER_ROAR, 1.4f, 0.55f);
                broadcast(players, "§6⚠ Cinder Colossus ยกกำปั้นทั้งสองข้าง — §eกระโดดตอนมันทุบพื้น §6แล้วกระโดดข้ามคลื่นกระแทก!");
            }
            case COLOSSUS_ERUPTION -> {
                playClip("ultimate_windup", true);
                coreExposed = true;
                coreInterruptDamage = 0;
                coreInterruptThreshold = 210.0 + Math.max(0, players.size() - 1) * 90.0;
                safeLaneBaseAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI / 2);
                broadcast(players, "§c⚠ เตาหลอมปะทุ! อ้อมไปโจมตี §eแกนสีแดงด้านหลัง §cเพื่อตัดท่า หรือยืนในช่องทางปลอดภัยสีฟ้า — ถ้าตัดไม่ทัน ลาวาจะทะลักใต้เท้าทุกคน!");
                center.getWorld().playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.4f, 0.6f);
            }
            case HOUND_FANG -> beginHoundLunge(target, 14);
            case HOUND_BURROW -> {
                lockedOrigin = at.clone();
                lockedTarget = target != null ? groundAt(target) : at.clone();
                playClip("skill_windup", true);
                center.getWorld().playSound(at, Sound.BLOCK_SOUL_SAND_BREAK, 1.4f, 0.65f);
            }
            case HOUND_PREY_POUNCE -> {
                Player prey = target != null ? target : (players.isEmpty() ? null : players.getFirst());
                markedPreyId = prey != null ? prey.getUniqueId() : null;
                preyHitDuringCombo = false;
                if (prey != null) {
                    prey.sendActionBar(Component.text("§b⚠ คุณถูกหมายหัวด้วย 'รอยเท้าคนเป็น'! หลบการกระโจน " + (phaseTwo ? 4 : 3) + " ครั้งติดกันเพื่อทำให้หมาป่าหมดแรง!"));
                }
                beginHoundLunge(prey, 13);
            }
            case HOUND_HOWL -> {
                at = center.clone();
                playClip("ultimate_windup", true);
                broadcast(players, "§b⚠ เสียงหอนกลืนวิญญาณ! ทำลาย §fลูกไฟวิญญาณทั้ง " + (phaseTwo ? 4 : 3) + " ลูก §bก่อนมันลอยเข้าหาบอส — และอย่ายืนชิดหมาป่าตอนมันหอน!");
                center.getWorld().playSound(at, Sound.ENTITY_WOLF_WHINE, 1.5f, 0.55f);
            }
            case HOUND_REND -> beginHoundRend(target, 16);
            case MOTH_WING_SLASH -> beginMothDive(target, 14);
            case MOTH_SCALE_RAIN -> {
                scaleFanBaseYaw = at.getYaw();
                playClip("skill_windup", true);
                center.getWorld().playSound(at, Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 1.4f, 1.15f);
            }
            case MOTH_PRISM_CRYSTALS -> {
                playClip("skill_windup", true);
                spawnPrismCrystals(phaseTwo ? 4 : 3, phaseThree ? Math.toRadians(1.0) : phaseTwo ? Math.toRadians(0.55) : 0.0);
                broadcast(players, "§d✦ คริสตัลสะท้อนแสงปรากฏขึ้น! ตีทำลายเสาคริสตัลเพื่อตัดลำแสง หรือเดินหลบช่องว่าง!");
                center.getWorld().playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.35f);
            }
            case MOTH_CHRYSALIS -> {
                at = center.clone();
                safeSectorIndex = ThreadLocalRandom.current().nextInt(6);
                playClip("ultimate_windup", true);
                broadcast(players, "§d⚠ ดักแด้ปริซึม! วิ่งเข้าสู่ §aช่องสีเขียวมรกต §dก่อนลำแสงระเบิด — ช่องปลอดภัยย้ายทุกระลอก ยืนใกล้ศูนย์กลางจะวิ่งทันกว่า!");
                center.getWorld().playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, 1.4f, 0.85f);
            }
            case MOTH_SHARD_NOVA -> {
                playClip("skill_windup", true);
                center.getWorld().playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.5f, 0.7f);
            }
            case SUN_SOLAR_GAZE -> beginSolarGaze(target);
            case SUN_RING_STORM -> {
                playClip("skill_windup", true);
                center.getWorld().playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_AMBIENT, 1.4f, 1.1f);
            }
            case SUN_MONOLITHS -> {
                eclipseAxisAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI);
                playClip("skill_windup", true);
                center.getWorld().playSound(at, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.3f, 0.7f);
            }
            case SUN_TOTAL_ECLIPSE -> {
                at = center.clone();
                eclipseAxisAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI);
                eclipseShadowSafe = true;
                eyeOpenWindow = true;
                eyeOpenTicks = 120;
                playClip("ultimate_windup", true);
                broadcast(players, "§e⚠ สุริยุปราคาดับแสง! ระลอกแรก: วิ่งเข้าสู่ §bฝั่งเงามืด (สีเขียว) §eแล้วเตรียมสลับขั้ว — ยืนใกล้เส้นกลางไว้!");
                center.getWorld().playSound(at, Sound.BLOCK_END_PORTAL_SPAWN, 1.0f, 0.75f);
            }
            case SUN_FALLING_STARS -> {
                playClip("skill_windup", true);
                center.getWorld().playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, 1.3f, 0.6f);
            }
            default -> {}
        }
    }

    private String finalPhaseWarning() {
        return switch (kind) {
            case CINDER_COLOSSUS -> "§cมงกุฎหลอมเหลว! §6หินเพลิงจะร่วงใส่ทุกคนเป็นระยะ ห้ามยืนนิ่ง!";
            case SOUL_HOUND -> "§cความหิวแห่งวิญญาณ! §bวงไฟวิญญาณจะผุดใต้เท้าทุกคนเป็นระยะ ห้ามยืนนิ่ง!";
            case CRYSTAL_MOTH -> "§cพายุผลึกแตก! §dเศษคริสตัลจะร่วงตามตัวทุกคนเป็นระยะ!";
            case UNLIT_SUN -> "§cโคโรนากวาดลาน! §eลำแสงหมุนรอบวงแหวน — กระโดดข้ามตอนมันกวาดผ่าน!";
        };
    }

    private void finishAction(int nextCooldown) {
        if (warded && action == wardSkill) {
            warded = false;
            wardSkill = null;
            broadcast(presentPlayers(), "§a✦ เกราะเฟสของ " + thaiName() + " สลายแล้ว — โจมตีได้เต็มที่!");
        }
        this.action = Action.NONE;
        this.actionTick = 0;
        this.comboStep = 0;
        this.coreExposed = false;
        this.hiddenUnderground = false;
        struck.clear();
        this.cooldownTicks = phaseThree ? Math.max(8, (int) Math.round(nextCooldown * 0.5))
            : phaseTwo ? Math.max(10, (int) Math.round(nextCooldown * 0.65)) : nextCooldown;
        playClip("idle", false);
    }

    public void triggerStun(int durationTicks, double damageMultiplier, String message) {
        warded = false;
        wardSkill = null;
        clearActiveNodes();
        this.coreExposed = false;
        this.hiddenUnderground = false;
        this.stunTicks = durationTicks;
        this.stunDamageMultiplier = damageMultiplier;
        this.action = Action.STUN;
        this.actionTick = 0;
        playClip("stun", true);
        center.getWorld().playSound(at, Sound.ITEM_TOTEM_USE, 0.9f, 1.25f);
        if (message != null && !message.isEmpty()) {
            broadcast(presentPlayers(), message);
        }
    }

    private void tickAction(List<Player> players) {
        actionTick++;
        switch (action) {
            case SPAWN -> {
                if (actionTick >= 32) finishAction(18);
            }
            case TRANSITION -> {
                center.getWorld().spawnParticle(Particle.END_ROD, at.clone().add(0, 2.5, 0), 10, 1.2, 1.2, 1.2, 0.08);
                if (actionTick >= 36) {
                    // The phase opens with its signature skill; the wall falls when that skill ends.
                    if (warded && wardSkill != null) startAction(wardSkill);
                    else finishAction(16);
                }
            }
            case STUN -> {
                if (actionTick >= stunTicks) finishAction(20);
            }
            case COLOSSUS_FIST -> tickColossusFist(players);
            case COLOSSUS_FISSURE -> tickColossusFissure(players);
            case COLOSSUS_RUBBLE -> tickColossusRubble(players);
            case COLOSSUS_ERUPTION -> tickColossusEruption(players);
            case COLOSSUS_QUAKE -> tickColossusQuake(players);
            case HOUND_FANG -> tickHoundFang(players);
            case HOUND_BURROW -> tickHoundBurrow(players);
            case HOUND_PREY_POUNCE -> tickHoundPreyPounce(players);
            case HOUND_HOWL -> tickHoundHowl(players);
            case HOUND_REND -> tickHoundRend(players);
            case MOTH_WING_SLASH -> tickMothWingSlash(players);
            case MOTH_SCALE_RAIN -> tickMothScaleRain(players);
            case MOTH_PRISM_CRYSTALS -> tickMothPrismCrystals(players);
            case MOTH_CHRYSALIS -> tickMothChrysalis(players);
            case MOTH_SHARD_NOVA -> tickMothShardNova(players);
            case SUN_SOLAR_GAZE -> tickSunSolarGaze(players);
            case SUN_RING_STORM -> tickSunRingStorm(players);
            case SUN_MONOLITHS -> tickSunMonoliths(players);
            case SUN_TOTAL_ECLIPSE -> tickSunTotalEclipse(players);
            case SUN_FALLING_STARS -> tickSunFallingStars(players);
            default -> finishAction(20);
        }
    }

    // =========================================================================
    // 1. CINDER COLOSSUS SKILLS
    // =========================================================================

    private void beginColossusSlam(Player target) {
        struck.clear();
        stepStart = actionTick;
        if (target != null) faceLocation(target.getLocation());
        lockedDir = directionFromYaw(at.getYaw());
        double reach = 4.2;
        if (target != null) {
            double d = Math.hypot(target.getX() - at.getX(), target.getZ() - at.getZ());
            // Wade in after a retreating target so backing off is not a free dodge
            if (d > 7.0) stepInsideArena(lockedDir.clone().multiply(Math.min(1.8, d - 7.0)));
            reach = Math.clamp(Math.hypot(target.getX() - at.getX(), target.getZ() - at.getZ()), 3.2, 7.0);
        }
        lockedTarget = at.clone().add(lockedDir.clone().multiply(reach));
        clampToArena(lockedTarget);
        playClip("attack_windup", true);
        center.getWorld().playSound(at, Sound.BLOCK_BASALT_BREAK, 1.4f, comboStep == 0 ? 0.6f : 0.8f);
    }

    private void tickColossusFist(List<Player> players) {
        // Crown Breaker: 2 slams (3 from phase 2), each re-aimed at the nearest player.
        // 18t tell on a 4.4 circle, then 14t tells on 3.8 circles; phase 2 slams leave hot ground.
        int slams = phaseTwo ? 3 : 2;
        int rel = actionTick - stepStart;
        if (comboStep >= slams) {
            if (rel >= 22) finishAction(20);
            return;
        }
        int tell = comboStep == 0 ? 18 : 14;
        double radius = comboStep == 0 ? 4.4 : 3.8;
        if (rel <= tell) {
            drawCircleTelegraph(lockedTarget, radius, Color.fromRGB(255, 115, 25), Particle.LAVA);
            if (rel % 5 == 0) center.getWorld().playSound(lockedTarget, Sound.BLOCK_FURNACE_FIRE_CRACKLE, 1.2f, 0.7f);
        } else if (rel == tell + 1) {
            playClip("attack_active", true);
            World w = center.getWorld();
            w.playSound(lockedTarget, Sound.ENTITY_GENERIC_EXPLODE, 1.3f, 0.75f);
            w.spawnParticle(Particle.EXPLOSION_EMITTER, lockedTarget.clone().add(0, 0.4, 0), 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.LAVA, lockedTarget.clone().add(0, 0.5, 0), 36, 2.2, 0.4, 2.2, 0.1);
            for (Player p : players) {
                if (p.getLocation().distanceSquared(lockedTarget) <= radius * radius && Math.abs(p.getY() - center.getY()) <= 3.5 && firstContact(p)) {
                    if (dealBossDamage(p, 10.5, DamageType.INDIRECT_MAGIC)) {
                        rider(p);
                        afflict(p, PotionEffectType.SLOWNESS, 30, 0);
                    }
                    knockback(p, lockedTarget, 0.85, 0.35);
                }
            }
            if (phaseTwo) hotZones.add(new HotZone(lockedTarget, radius - 0.4, 110));
        } else if (rel >= tell + 7) {
            comboStep++;
            if (comboStep < slams) {
                beginColossusSlam(nearestTarget(players));
            } else {
                stepStart = actionTick;
                playClip("attack_recover", true);
            }
        }
    }

    private double[] fissureAngles() {
        return phaseTwo
            ? new double[]{-Math.toRadians(50), -Math.toRadians(25), 0.0, Math.toRadians(25), Math.toRadians(50)}
            : new double[]{-Math.toRadians(28), 0.0, Math.toRadians(28)};
    }

    private boolean fissureForks(double offset) {
        return phaseTwo && Math.abs(offset) < (phaseThree ? 0.5 : 0.05);
    }

    private void tickColossusFissure(List<Player> players) {
        // Magma Rift: 22t tell (+ overhead markers for airborne players), rifts race out 2.6 blocks/tick.
        // Phase 2: five rifts, the centre one forks; final phase forks the inner three. Phase 2 rifts keep burning.
        double[] angles = fissureAngles();
        if (actionTick <= 22) {
            for (double offset : angles) {
                Vector dir = vectorFromYawRad(lockedYawRad + offset);
                drawLineTelegraphWithSky(lockedOrigin, dir, 26.0, 2.3, Color.fromRGB(255, 130, 30), true);
                if (fissureForks(offset)) {
                    Location fork = lockedOrigin.clone().add(dir.clone().multiply(12.0));
                    drawLineTelegraphWithSky(fork, vectorFromYawRad(lockedYawRad + offset + Math.toRadians(22)), 14.0, 2.0, Color.fromRGB(255, 90, 20), true);
                    drawLineTelegraphWithSky(fork, vectorFromYawRad(lockedYawRad + offset - Math.toRadians(22)), 14.0, 2.0, Color.fromRGB(255, 90, 20), true);
                }
            }
        } else if (actionTick == 23) {
            playClip("skill_active", true);
            center.getWorld().playSound(lockedOrigin, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 0.6f);
        }
        if (actionTick >= 23 && actionTick <= 33) {
            double reach = (actionTick - 22) * 2.6;
            for (double offset : angles) {
                Vector dir = vectorFromYawRad(lockedYawRad + offset);
                eruptFissureSegment(players, lockedOrigin, dir, reach - 2.6, reach, 2.3, 12.0);
                if (fissureForks(offset) && reach > 12.0) {
                    Location fork = lockedOrigin.clone().add(dir.clone().multiply(12.0));
                    eruptFissureSegment(players, fork, vectorFromYawRad(lockedYawRad + offset + Math.toRadians(22)), reach - 14.6, reach - 12.0, 2.0, 11.0);
                    eruptFissureSegment(players, fork, vectorFromYawRad(lockedYawRad + offset - Math.toRadians(22)), reach - 14.6, reach - 12.0, 2.0, 11.0);
                }
            }
        } else if (actionTick == 34) {
            playClip("skill_recover", true);
            if (phaseTwo) {
                for (double offset : angles) {
                    Vector dir = vectorFromYawRad(lockedYawRad + offset);
                    for (double d = 4.0; d <= 24.0; d += 4.0) {
                        Location lane = lockedOrigin.clone().add(dir.clone().multiply(d));
                        clampToArena(lane);
                        hotZones.add(new HotZone(lane, 1.9, 70));
                    }
                }
            }
        } else if (actionTick >= 52) {
            finishAction(22);
        }
    }

    private void eruptFissureSegment(List<Player> players, Location origin, Vector dir, double d0, double d1, double width, double dmg) {
        if (d1 < 0) return;
        double dist = Math.max(1.0, (d0 + d1) * 0.5);
        Location pt = origin.clone().add(dir.clone().multiply(dist));
        World w = pt.getWorld();
        w.spawnParticle(Particle.LAVA, pt.clone().add(0, 0.3, 0), 6, 0.6, 0.3, 0.6, 0.05);
        w.spawnParticle(Particle.FLAME, pt.clone().add(0, 1.5, 0), 8, 0.5, 1.2, 0.5, 0.03);
        for (Player p : players) {
            if (Math.abs(p.getY() - origin.getY()) <= 4.5 && p.getLocation().distanceSquared(pt) <= (width + 0.6) * (width + 0.6) && firstContact(p)) {
                if (dealBossDamage(p, dmg, DamageType.INDIRECT_MAGIC)) rider(p);
                p.setVelocity(p.getVelocity().setY(0.42));
            }
        }
    }

    private void tickColossusRubble(List<Player> players) {
        // Crown Rubble: a volley every 9t (4, 5 from phase 2, 6 final); each boulder lands 18t later.
        // Phase 2 aims every other volley where moving players are heading and leaves embers on impact.
        int volleys = phaseThree ? 6 : phaseTwo ? 5 : 4;
        if (actionTick == 8) playClip("skill_active", true);
        int k = actionTick - 8;
        if (k >= 0 && k % 9 == 0 && k / 9 < volleys) {
            int volley = k / 9;
            center.getWorld().playSound(at, Sound.ENTITY_BLAZE_SHOOT, 1.3f, 0.65f);
            for (Player p : players) {
                spawnFallingStrike(strikeTarget(p, phaseTwo && volley % 2 == 1, 18), 3.4, 18, 10.5, Material.MAGMA_BLOCK, phaseTwo ? 2.6 : 0.0);
            }
            // Extra scatter so a solo player still faces a full volley
            for (int i = 0; i < (phaseTwo ? 3 : 2); i++) {
                spawnFallingStrike(randomArenaPoint(6.0), 3.4, 18, 10.5, Material.MAGMA_BLOCK, 0.0);
            }
        }
        int end = 8 + (volleys - 1) * 9 + 20;
        if (actionTick == end) playClip("skill_recover", true);
        if (actionTick >= end + 18) finishAction(22);
    }

    private void tickColossusQuake(List<Player> players) {
        // Furnace Quake: 22t tell around the body, a 7-block slam that only misses airborne players,
        // then a shock ring to jump over (a second, faster ring from phase 2).
        if (actionTick <= 22) {
            drawCircleTelegraph(at, 7.0, Color.fromRGB(255, 70, 20), Particle.LAVA);
            if (actionTick % 4 == 0) drawCircleTelegraph(at, 3.5, Color.fromRGB(255, 170, 60), null);
            if (actionTick == 12) center.getWorld().playSound(at, Sound.BLOCK_ANVIL_PLACE, 1.2f, 0.5f);
        } else if (actionTick == 23) {
            playClip("skill_active", true);
            World w = center.getWorld();
            w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.6f);
            w.spawnParticle(Particle.EXPLOSION_EMITTER, at.clone().add(0, 0.5, 0), 2, 1.5, 0.2, 1.5, 0);
            w.spawnParticle(Particle.LAVA, at.clone().add(0, 0.5, 0), 40, 3.5, 0.3, 3.5, 0.1);
            for (Player p : players) {
                if (p.getLocation().distanceSquared(at) <= 7.0 * 7.0 && p.getY() - at.getY() <= 1.0) {
                    if (dealBossDamage(p, 12.0, DamageType.INDIRECT_MAGIC)) rider(p);
                    knockback(p, at, 1.25, 0.5);
                }
            }
            activeRings.add(new ExpandingRing(at, 7.0, arena.radius(), 0.9, 0.0, 0, 0.0, 9.5, Color.fromRGB(255, 110, 30)));
        } else if (phaseTwo && actionTick == 35) {
            center.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.3f, 0.8f);
            activeRings.add(new ExpandingRing(at, 3.0, arena.radius(), 1.15, 0.0, 0, 0.0, 9.5, Color.fromRGB(255, 60, 20)));
        } else if (actionTick == 38) {
            playClip("skill_recover", true);
        } else if (actionTick >= 56) {
            finishAction(20);
        }
    }

    private void tickColossusEruption(List<Player> players) {
        // Furnace Eruption: 30t kneel, then a lava wave every 22t (3 waves, 4 from phase 2) with narrower lanes later.
        // The back core stays exposed until the last wave; if nobody breaks it, lava spills under every challenger.
        int waves = phaseTwo ? 4 : 3;
        double laneWidth = Math.toRadians(phaseTwo ? 28 : 34);
        int lastWave = 44 + (waves - 1) * 22;
        Location backCore = at.clone().subtract(directionFromYaw(at.getYaw()).multiply(1.9)).add(0, 2.8, 0);
        if (actionTick <= lastWave) {
            coreExposed = true;
            // Highlight red core behind the kneeling Colossus
            center.getWorld().spawnParticle(Particle.DUST, backCore, 10, 0.45, 0.45, 0.45, 0,
                new Particle.DustOptions(Color.fromRGB(255, 35, 35), 2.0f), true);
            center.getWorld().spawnParticle(Particle.END_ROD, backCore, 3, 0.3, 0.3, 0.3, 0.02);
            drawSafeWalkways(safeLaneBaseAngle, 4, laneWidth);
        }
        int cue = actionTick - 30;
        if (cue >= 0 && cue % 22 == 0 && cue / 22 < waves) {
            playClip("ultimate_active", true);
        }
        int k = actionTick - 44;
        if (k >= 0 && k % 22 == 0 && k / 22 < waves) {
            fireFurnaceLavaWave(players, safeLaneBaseAngle, 4, laneWidth);
            if (phaseThree) safeLaneBaseAngle += Math.toRadians(ThreadLocalRandom.current().nextBoolean() ? 30 : -30);
            else if (phaseTwo) safeLaneBaseAngle += Math.toRadians(22);
        }
        if (actionTick == lastWave + 2) {
            coreExposed = false;
            playClip("ultimate_recover", true);
            for (Player p : players) {
                hotZones.add(new HotZone(groundAt(p), 3.2, 100, 14, 4.0));
            }
            broadcast(players, "§c✦ แกนเตาหลอมไม่ถูกทำลาย — ลาวาทะลักขึ้นใต้เท้าทุกคน! ถอยออกจากวงสีขาว!");
        }
        if (actionTick >= lastWave + 26) {
            finishAction(28);
        }
    }

    private void drawSafeWalkways(double baseAngle, int lanes, double laneWidthRad) {
        World w = center.getWorld();
        var safeDust = new Particle.DustOptions(Color.fromRGB(60, 245, 215), 1.5f);
        var dangerDust = new Particle.DustOptions(Color.fromRGB(255, 85, 20), 1.4f);
        for (int i = 0; i < lanes; i++) {
            double centerAng = baseAngle + i * (2 * Math.PI / lanes);
            for (double r = 4.0; r <= arena.radius() - 2; r += 2.5) {
                for (double s : new double[]{-laneWidthRad * 0.5, 0, laneWidthRad * 0.5}) {
                    double a = centerAng + s;
                    w.spawnParticle(Particle.DUST, center.clone().add(Math.cos(a) * r, 0.2, Math.sin(a) * r), 1, 0, 0, 0, 0, safeDust, true);
                }
                double dangerA = centerAng + (Math.PI / lanes);
                w.spawnParticle(Particle.DUST, center.clone().add(Math.cos(dangerA) * r, 0.2, Math.sin(dangerA) * r), 1, 0, 0, 0, 0, dangerDust, true);
            }
        }
    }

    private void fireFurnaceLavaWave(List<Player> players, double baseAngle, int lanes, double laneWidthRad) {
        World w = center.getWorld();
        w.playSound(center, Sound.ITEM_FIRECHARGE_USE, 1.5f, 0.65f);
        for (int n = 0; n < 72; n++) {
            double a = n * Math.PI / 36.0;
            if (isAngleInSafeLane(a, baseAngle, lanes, laneWidthRad)) continue;
            for (double r = 4.5; r <= arena.radius() - 2; r += 4.5) {
                w.spawnParticle(Particle.LAVA, center.clone().add(Math.cos(a) * r, 0.35, Math.sin(a) * r), 2, 0.4, 0.2, 0.4, 0.02);
            }
        }
        for (Player p : players) {
            double dx = p.getX() - center.getX(), dz = p.getZ() - center.getZ();
            double dist = Math.hypot(dx, dz);
            if (dist < 3.5 || Math.abs(p.getY() - center.getY()) > 3.2) continue;
            double ang = Math.atan2(dz, dx);
            if (!isAngleInSafeLane(ang, baseAngle, lanes, laneWidthRad)) {
                if (dealHeavyDamage(p, 12.0, DamageType.INDIRECT_MAGIC)) ignite(p, 80);
            }
        }
    }

    private boolean isAngleInSafeLane(double angle, double baseAngle, int lanes, double laneWidthRad) {
        for (int i = 0; i < lanes; i++) {
            double centerAng = baseAngle + i * (2 * Math.PI / lanes);
            double diff = Math.abs( wrapRadians(angle - centerAng) );
            if (diff <= laneWidthRad * 0.55) return true;
        }
        return false;
    }

    // =========================================================================
    // 2. SOUL-EATER HOUND SKILLS
    // =========================================================================

    private void beginHoundLunge(Player target, int tell) {
        struck.clear();
        stepStart = actionTick;
        dashTell = tell;
        if (target != null) faceLocation(target.getLocation());
        lockedOrigin = at.clone();
        lockedDir = directionFromYaw(at.getYaw());
        playClip("attack_windup", true);
        center.getWorld().playSound(at, Sound.ENTITY_WOLF_GROWL, 1.4f, comboStep == 0 ? 0.55f : 0.8f);
    }

    private void tickHoundFang(List<Player> players) {
        // Soul Fang: 2 lunges (3 from phase 2, 4 final), each re-aimed at the nearest player.
        // 14t first tell, 11t after; 7 steps of 2.85 blocks. Crashing into the arena edge no longer stuns.
        int lunges = phaseThree ? 4 : phaseTwo ? 3 : 2;
        int rel = actionTick - stepStart;
        if (comboStep >= lunges) {
            if (rel >= 14) finishAction(18);
            return;
        }
        if (rel <= dashTell) {
            drawLineTelegraphWithSky(lockedOrigin, lockedDir, 20.0, 2.2, Color.fromRGB(70, 235, 255), false);
        } else if (rel == dashTell + 1) {
            playClip("attack_active", true);
            center.getWorld().playSound(at, Sound.ENTITY_PHANTOM_BITE, 1.4f, 0.75f);
        }
        if (rel > dashTell && rel <= dashTell + 7) {
            performLockedDashStep(players, lockedDir, 2.85, 2.3, 10.0, phaseTwo);
        } else if (rel > dashTell + 10) {
            comboStep++;
            if (comboStep < lunges) {
                beginHoundLunge(nearestTarget(players), 11);
            } else {
                stepStart = actionTick;
                playClip("attack_recover", true);
            }
        }
    }

    private boolean performLockedDashStep(List<Player> players, Vector dir, double stepLen, double hitRadius, double dmg, boolean leaveSoulPrints) {
        Location before = at.clone();
        boolean hitWall = stepInsideArena(dir.clone().multiply(stepLen));
        World w = center.getWorld();
        w.spawnParticle(kind == RealmBosses.Kind.SOUL_HOUND ? Particle.SOUL_FIRE_FLAME : Particle.END_ROD,
            at.clone().add(0, 1.0, 0), 10, 0.6, 0.4, 0.6, 0.03);
        if (leaveSoulPrints) {
            hotZones.add(new HotZone(before, 2.2, 80));
        }
        boolean preyCombo = action == Action.HOUND_PREY_POUNCE;
        for (Player p : players) {
            if (Math.abs(p.getY() - at.getY()) <= 3.0 && p.getLocation().distanceSquared(at) <= hitRadius * hitRadius && firstContact(p)) {
                boolean prey = p.getUniqueId().equals(markedPreyId);
                if (dealBossDamage(p, dmg, DamageType.INDIRECT_MAGIC)) {
                    rider(p);
                    if (prey && preyCombo) {
                        afflict(p, PotionEffectType.WITHER, 60, 1);
                        afflict(p, PotionEffectType.SLOWNESS, 30, 0);
                    }
                }
                knockback(p, at, 0.7, 0.3);
                if (prey) preyHitDuringCombo = true;
            }
        }
        return hitWall;
    }

    private void tickHoundBurrow(List<Player> players) {
        // Grave Burrow: 16t dig, then per eruption a tracking trail (20t, 12t for follow-ups) and a 16t locked ring.
        // 1 eruption, 2 from phase 2 (alternating nearest / farthest player), 3 final; phase 2 chains a fast bite.
        int eruptions = phaseThree ? 3 : phaseTwo ? 2 : 1;
        if (actionTick <= 16) {
            center.getWorld().spawnParticle(Particle.SOUL, at.clone().add(0, 0.5, 0), 12, 0.8, 0.3, 0.8, 0.04);
            if (actionTick == 16) {
                hiddenUnderground = true;
                lockedOrigin = at.clone();
                stepStart = actionTick;
            }
            return;
        }
        int rel = actionTick - stepStart;
        if (comboStep < eruptions) {
            int track = comboStep == 0 ? 20 : 12;
            int erupt = track + 17;
            if (rel <= track) {
                Player t = comboStep % 2 == 0 ? nearestTarget(players) : farthestTarget(players, lockedOrigin);
                if (t != null) {
                    lockedTarget = groundAt(t);
                    clampToArena(lockedTarget);
                }
                double frac = Math.clamp(rel / (double) track, 0.0, 1.0);
                Location trail = lockedOrigin.clone().add(lockedTarget.toVector().subtract(lockedOrigin.toVector()).multiply(frac));
                center.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, trail.clone().add(0, 0.2, 0), 14, 0.5, 0.15, 0.5, 0.02);
                drawCircleTelegraph(lockedTarget, 3.8, Color.fromRGB(70, 235, 255), Particle.SOUL_FIRE_FLAME);
            } else if (rel < erupt) {
                drawCircleTelegraph(lockedTarget, 3.8, Color.fromRGB(150, 255, 255), Particle.SOUL_FIRE_FLAME);
            } else if (rel == erupt) {
                hiddenUnderground = false;
                at = lockedTarget.clone();
                Player t = nearestTarget(players);
                if (t != null) faceLocation(t.getLocation());
                playClip("skill_active", true);
                center.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.15f);
                center.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, at.clone().add(0, 1.2, 0), 48, 1.8, 1.2, 1.8, 0.08);
                struck.clear();
                for (Player p : players) {
                    if (p.getLocation().distanceSquared(at) <= 3.8 * 3.8 && Math.abs(p.getY() - at.getY()) <= 3.8 && firstContact(p)) {
                        if (dealBossDamage(p, 12.0, DamageType.INDIRECT_MAGIC)) rider(p);
                        p.setVelocity(p.getVelocity().setY(0.6));
                    }
                }
            } else if (rel >= erupt + 9) {
                comboStep++;
                stepStart = actionTick;
                if (comboStep < eruptions) {
                    // Straight back under for the next eruption
                    hiddenUnderground = true;
                    lockedOrigin = at.clone();
                    playClip("skill_windup", true);
                    center.getWorld().playSound(at, Sound.BLOCK_SOUL_SAND_BREAK, 1.3f, 0.75f);
                } else if (phaseTwo) {
                    Player t = nearestTarget(players);
                    if (t != null) faceLocation(t.getLocation());
                    lockedOrigin = at.clone();
                    lockedDir = directionFromYaw(at.getYaw());
                    struck.clear();
                    playClip("attack_windup", true);
                } else {
                    playClip("skill_recover", true);
                }
            }
            return;
        }
        if (phaseTwo) {
            // Follow-up fast bite along a locked line
            if (rel <= 11) {
                drawLineTelegraphWithSky(lockedOrigin, lockedDir, 12.0, 2.0, Color.fromRGB(70, 235, 255), false);
            } else if (rel == 12) {
                playClip("attack_active", true);
                center.getWorld().playSound(at, Sound.ENTITY_PHANTOM_BITE, 1.4f, 0.8f);
            }
            if (rel >= 12 && rel <= 15) {
                performLockedDashStep(players, lockedDir, 3.0, 2.4, 10.0, true);
            } else if (rel == 20) {
                playClip("attack_recover", true);
            } else if (rel >= 34) {
                finishAction(20);
            }
        } else if (rel >= 16) {
            finishAction(20);
        }
    }

    private void tickHoundPreyPounce(List<Player> players) {
        // Living Prey: 3 pounces (4 from phase 2) at the marked player, 13t first tell then 11t.
        // Dodge every one and the Hound is exhausted (stun); get caught and the bite withers.
        Player prey = markedPreyId != null ? Bukkit.getPlayer(markedPreyId) : null;
        if (prey == null || !insideArena.test(prey)) prey = nearestTarget(players);
        int pounces = phaseTwo ? 4 : 3;
        int rel = actionTick - stepStart;
        if (comboStep >= pounces) {
            if (rel >= 14) finishAction(20);
            return;
        }
        if (rel <= dashTell) {
            drawLineTelegraphWithSky(lockedOrigin, lockedDir, 18.0, 2.1, Color.fromRGB(80, 255, 220), false);
        } else if (rel == dashTell + 1) {
            playClip("attack_active", true);
            center.getWorld().playSound(at, comboStep == 0 ? Sound.ENTITY_WOLF_AMBIENT : Sound.ENTITY_PHANTOM_BITE, 1.3f, 0.7f);
        }
        if (rel > dashTell && rel <= dashTell + 6) {
            performLockedDashStep(players, lockedDir, 2.9, 2.2, 10.5, phaseTwo);
        } else if (rel > dashTell + 9) {
            comboStep++;
            if (comboStep < pounces) {
                beginHoundLunge(prey, 11);
            } else {
                if (!preyHitDuringCombo) {
                    triggerStun(36, 1.75, "§a✦ หลบ 'รอยเท้าคนเป็น' ได้ครบ " + pounces + " จังหวะ! Soul-Eater Hound หอบเหนื่อยเปิดช่องให้โจมตี!");
                    return;
                }
                stepStart = actionTick;
                playClip("attack_recover", true);
            }
        }
    }

    private void tickHoundHowl(List<Player> players) {
        // Soul Howl: 20t windup, 3 orbs (4 from phase 2) drift inward over 70t; a soul pulse every 25t
        // throws back anyone hugging the Hound. Orbs left alive burst into soul fire and blind the arena.
        int orbs = phaseTwo ? 4 : 3;
        if (actionTick == 20) {
            playClip("ultimate_active", true);
            spawnSoulFireOrbs(orbs, 70);
        }
        if (actionTick > 20 && actionTick <= 90) {
            if ((actionTick - 20) % 20 == 0) playClip("ultimate_active", true);
            if (activeNodes.isEmpty()) {
                triggerStun(48, 2.0, "§b✦ ลูกไฟวิญญาณถูกทำลายครบทั้ง " + orbs + " ลูก! Soul-Eater Hound ถูกแรงสะท้อนจนมึนงง!");
                return;
            }
            int pulse = (actionTick - 20) % 25;
            if (pulse >= 13) {
                drawCircleTelegraph(at, 6.0, Color.fromRGB(65, 230, 255), Particle.SOUL);
            } else if (pulse == 0) {
                center.getWorld().playSound(at, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.3f, 0.7f);
                center.getWorld().spawnParticle(Particle.SOUL, at.clone().add(0, 0.6, 0), 40, 3.0, 0.3, 3.0, 0.05);
                for (Player p : players) {
                    if (p.getLocation().distanceSquared(at) <= 6.0 * 6.0 && Math.abs(p.getY() - at.getY()) <= 3.0) {
                        if (dealBossDamage(p, 8.5, DamageType.INDIRECT_MAGIC)) rider(p);
                        knockback(p, at, 0.9, 0.3);
                    }
                }
            }
        }
        if (actionTick == 91) {
            int remaining = activeNodes.size();
            for (DestructibleNode orb : activeNodes) {
                hotZones.add(new HotZone(new Location(center.getWorld(), orb.loc.getX(), center.getY(), orb.loc.getZ()), 3.0, 120));
            }
            clearActiveNodes();
            double waveDmg = 9.0 + remaining * 3.0;
            center.getWorld().playSound(at, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.4f, 0.75f);
            activeRings.add(new ExpandingRing(at, 2.5, arena.radius(), 1.2, ThreadLocalRandom.current().nextDouble(0, Math.PI * 2), 2, Math.toRadians(20), waveDmg, Color.fromRGB(65, 230, 255)));
            if (remaining > 0) {
                for (Player p : players) afflict(p, PotionEffectType.DARKNESS, 60, 0);
            }
            playClip("ultimate_recover", true);
        }
        if (actionTick >= 110) {
            finishAction(26);
        }
    }

    private void spawnSoulFireOrbs(int count, int travelTicks) {
        clearActiveNodes();
        nodeTotal = count;
        World w = center.getWorld();
        double r = Math.min(16.0, arena.radius() - 5.0);
        double base = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        for (int i = 0; i < count; i++) {
            double ang = base + i * (2 * Math.PI / count);
            Location loc = center.clone().add(Math.cos(ang) * r, 1.35, Math.sin(ang) * r);
            Vector vel = center.clone().add(0, 1.35, 0).toVector().subtract(loc.toVector()).normalize().multiply(r / (travelTicks + 4.0));
            DestructibleNode orb = new DestructibleNode(loc, vel, 10.0);
            orb.display = w.spawn(loc, BlockDisplay.class, bd -> {
                bd.setBlock(Bukkit.createBlockData(Material.SOUL_LANTERN));
                bd.setGlowing(true);
                bd.setGlowColorOverride(Color.fromRGB(70, 240, 255));
                bd.setPersistent(false);
                bd.setTeleportDuration(2);
                bd.setTransformation(new Transformation(
                    new Vector3f(-0.45f, -0.45f, -0.45f),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(0.9f, 0.9f, 0.9f),
                    new AxisAngle4f(0, 0, 1, 0)
                ));
                bd.getPersistentDataContainer().set(plugin.key("world_boss_entity"), PersistentDataType.BYTE, (byte) 1);
            });
            orb.hitbox = w.spawn(loc, Interaction.class, inter -> {
                inter.setInteractionWidth(1.7f);
                inter.setInteractionHeight(1.9f);
                inter.setResponsive(true);
                inter.setPersistent(false);
                inter.getPersistentDataContainer().set(plugin.key("world_boss_entity"), PersistentDataType.BYTE, (byte) 1);
            });
            activeNodes.add(orb);
        }
    }

    private void beginHoundRend(Player target, int tell) {
        struck.clear();
        stepStart = actionTick;
        dashTell = tell;
        if (target != null) faceLocation(target.getLocation());
        lockedDir = directionFromYaw(at.getYaw());
        playClip("attack_windup", true);
        center.getWorld().playSound(at, Sound.ENTITY_RAVAGER_ATTACK, 1.3f, comboStep == 0 ? 0.6f : 0.8f);
    }

    private void tickHoundRend(List<Player> players) {
        // Bone Rend: 120-degree claw sweeps out to 6.5 blocks, 2 (3 from phase 2); 16t first tell, 12t after,
        // turning to the nearest player between sweeps. Escape sideways or behind; jumping does not help.
        int sweeps = phaseTwo ? 3 : 2;
        double reach = 6.5;
        double halfAngle = Math.toRadians(60);
        int rel = actionTick - stepStart;
        if (comboStep >= sweeps) {
            if (rel >= 14) finishAction(18);
            return;
        }
        if (rel <= dashTell) {
            drawConeTelegraph(at, lockedDir, reach, halfAngle, Color.fromRGB(90, 225, 255));
        } else if (rel == dashTell + 1) {
            playClip("attack_active", true);
            World w = center.getWorld();
            w.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, 0.6f);
            double base = Math.atan2(lockedDir.getZ(), lockedDir.getX());
            for (int i = -3; i <= 3; i++) {
                double a = base + halfAngle * i / 3.0;
                w.spawnParticle(Particle.SWEEP_ATTACK, at.clone().add(Math.cos(a) * reach * 0.6, 1.0, Math.sin(a) * reach * 0.6), 1, 0, 0, 0, 0);
            }
            for (Player p : players) {
                if (Math.abs(p.getY() - at.getY()) <= 3.0 && inCone(p, at, lockedDir, reach, halfAngle) && firstContact(p)) {
                    if (dealBossDamage(p, 10.5, DamageType.INDIRECT_MAGIC)) rider(p);
                    knockback(p, at, 0.6, 0.25);
                }
            }
        } else if (rel >= dashTell + 6) {
            comboStep++;
            if (comboStep < sweeps) {
                beginHoundRend(nearestTarget(players), 12);
            } else {
                stepStart = actionTick;
                playClip("attack_recover", true);
            }
        }
    }

    // =========================================================================
    // 3. CRYSTAL MOTH QUEEN SKILLS
    // =========================================================================

    private void beginMothDive(Player target, int tell) {
        struck.clear();
        stepStart = actionTick;
        dashTell = tell;
        if (target != null) faceLocation(target.getLocation());
        lockedOrigin = at.clone();
        lockedDir = directionFromYaw(at.getYaw());
        playClip("attack_windup", true);
        center.getWorld().playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, comboStep == 0 ? 0.8f : 1.0f);
    }

    private void tickMothWingSlash(List<Player> players) {
        // Prism Dive: 2 swoops along crystal lanes (3 from phase 2), 14t first tell then 12t; phase 2 swoops shed
        // crystal shards. Only the last swoop settles into the 34t low hover that builds the Wing Crack gauge.
        int dives = phaseTwo ? 3 : 2;
        int rel = actionTick - stepStart;
        if (comboStep >= dives) {
            if (rel >= 31) finishAction(20);
            return;
        }
        if (rel <= dashTell) {
            drawLineTelegraphWithSky(lockedOrigin, lockedDir, 22.0, 2.6, Color.fromRGB(245, 130, 255), false);
        } else if (rel == dashTell + 1) {
            playClip("attack_active", true);
            center.getWorld().playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.4f, 0.75f);
        }
        if (rel > dashTell && rel <= dashTell + 8) {
            Location before = at.clone();
            performLockedDashStep(players, lockedDir, 2.75, 2.6, 10.5, false);
            center.getWorld().spawnParticle(Particle.END_ROD, at.clone().add(0, 1.8, 0), 8, 1.2, 0.5, 1.2, 0.05);
            if (phaseTwo && (rel - dashTell) % 2 == 0) hotZones.add(new HotZone(before, 1.8, 70));
        } else if (rel > dashTell + 10) {
            comboStep++;
            if (comboStep < dives) {
                beginMothDive(nearestTarget(players), 12);
            } else {
                stepStart = actionTick;
                playClip("attack_recover", true);
                lowHoverWindow = true;
                lowHoverTicks = 34;
                for (Player p : players) {
                    p.sendActionBar(Component.text("§d✦ Crystal Moth Queen ร่อนต่ำลงใกล้พื้น — โจมตีเพื่อสะสมเกจ 'ปีกแตกร้าว'!"));
                }
            }
        }
    }

    private void tickMothScaleRain(List<Player> players) {
        // Scale Tempest: 7-lane fans, 2 waves (3 from phase 2, 4 final), 16t first tell then 11t.
        // Every other wave shifts half a lane so the safe gaps move; phase 2 re-aims each wave.
        int waves = phaseThree ? 4 : phaseTwo ? 3 : 2;
        int rel = actionTick - stepStart;
        if (comboStep >= waves) {
            if (rel == 8) playClip("skill_recover", true);
            else if (rel >= 22) finishAction(20);
            return;
        }
        int tell = comboStep == 0 ? 16 : 11;
        double baseRad = Math.toRadians(scaleFanBaseYaw) + Math.toRadians(comboStep % 2 == 0 ? 0 : 9);
        if (rel == 1 && comboStep > 0) playClip("skill_windup", true);
        if (rel <= tell) {
            drawScaleFanTelegraph(baseRad);
        } else {
            playClip("skill_active", true);
            struck.clear();
            fireScaleFanWave(players, baseRad);
            comboStep++;
            stepStart = actionTick;
            if (phaseTwo && comboStep < waves) {
                Player t = nearestTarget(players);
                if (t != null) faceLocation(t.getLocation());
                scaleFanBaseYaw = at.getYaw();
            }
        }
    }

    private void drawScaleFanTelegraph(double baseYawRad) {
        for (int i = -3; i <= 3; i++) {
            Vector dir = vectorFromYawRad(baseYawRad + i * Math.toRadians(18));
            drawLineTelegraphWithSky(at, dir, 24.0, 1.15, Color.fromRGB(235, 130, 255), false);
        }
    }

    private void fireScaleFanWave(List<Player> players, double baseYawRad) {
        World w = center.getWorld();
        w.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.5f, 1.25f);
        for (int i = -3; i <= 3; i++) {
            Vector dir = vectorFromYawRad(baseYawRad + i * Math.toRadians(18));
            for (double d = 2.0; d <= 24.0; d += 1.6) {
                Location pt = at.clone().add(dir.clone().multiply(d));
                w.spawnParticle(Particle.END_ROD, pt.clone().add(0, 1.1, 0), 2, 0.15, 0.15, 0.15, 0.02);
                w.spawnParticle(Particle.DUST, pt.clone().add(0, 0.3, 0), 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(245, 110, 255), 1.4f), true);
            }
            for (Player p : players) {
                if (Math.abs(p.getY() - at.getY()) <= 3.5 && distanceToSegment2D(p.getLocation(), at, dir, 24.0) <= 1.25 && firstContact(p)) {
                    if (dealBossDamage(p, 10.0, DamageType.INDIRECT_MAGIC)) rider(p);
                }
            }
        }
    }

    private void spawnPrismCrystals(int count, double orbitSpeed) {
        clearActiveNodes();
        nodeTotal = count;
        World w = center.getWorld();
        double r = Math.min(13.0, arena.radius() - 6.0);
        double base = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        for (int i = 0; i < count; i++) {
            double ang = base + i * (2 * Math.PI / count);
            Location loc = center.clone().add(Math.cos(ang) * r, 0.0, Math.sin(ang) * r);
            DestructibleNode pylon = new DestructibleNode(loc, new Vector(0, 0, 0), 14.0);
            pylon.orbitSpeed = orbitSpeed;
            pylon.display = w.spawn(loc, BlockDisplay.class, bd -> {
                bd.setBlock(Bukkit.createBlockData(Material.AMETHYST_CLUSTER));
                bd.setGlowing(true);
                bd.setGlowColorOverride(Color.fromRGB(240, 110, 255));
                bd.setPersistent(false);
                bd.setTeleportDuration(2);
                bd.setTransformation(new Transformation(
                    new Vector3f(-0.6f, 0.0f, -0.6f),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(1.2f, 2.2f, 1.2f),
                    new AxisAngle4f(0, 0, 1, 0)
                ));
                bd.getPersistentDataContainer().set(plugin.key("world_boss_entity"), PersistentDataType.BYTE, (byte) 1);
            });
            pylon.hitbox = w.spawn(loc, Interaction.class, inter -> {
                inter.setInteractionWidth(1.8f);
                inter.setInteractionHeight(2.5f);
                inter.setResponsive(true);
                inter.setPersistent(false);
                inter.getPersistentDataContainer().set(plugin.key("world_boss_entity"), PersistentDataType.BYTE, (byte) 1);
            });
            activeNodes.add(pylon);
        }
    }

    private void tickMothPrismCrystals(List<Player> players) {
        // Prism Lattice: every intact pylon links to every other (3 pylons, 4 from phase 2, orbiting from phase 2).
        // 24t warning lines, then 40t live beams; break pylons to cut the lattice short.
        if (actionTick < 65 && activeNodes.size() < 2) {
            clearActiveNodes();
            playClip("skill_recover", true);
            finishAction(18);
            return;
        }
        if (actionTick <= 24) {
            drawPrismPylonLinks(false, players);
        } else if (actionTick == 25) {
            playClip("skill_active", true);
            center.getWorld().playSound(at, Sound.BLOCK_BEACON_DEACTIVATE, 1.4f, 1.4f);
        }
        if (actionTick >= 25 && actionTick <= 64) {
            drawPrismPylonLinks(true, players);
        } else if (actionTick == 65) {
            clearActiveNodes();
            playClip("skill_recover", true);
        } else if (actionTick >= 80) {
            finishAction(20);
        }
    }

    private void drawPrismPylonLinks(boolean activeDamage, List<Player> players) {
        World w = center.getWorld();
        var dust = new Particle.DustOptions(activeDamage ? Color.fromRGB(255, 65, 225) : Color.fromRGB(230, 165, 255), activeDamage ? 1.8f : 1.2f);
        Set<UUID> zapped = new HashSet<>();
        for (int i = 0; i < activeNodes.size(); i++) {
            for (int j = i + 1; j < activeNodes.size(); j++) {
                Location a = activeNodes.get(i).loc;
                Location b = activeNodes.get(j).loc;
                Vector diff = b.toVector().subtract(a.toVector());
                double len = diff.length();
                if (len < 0.5) continue;
                Vector dir = diff.clone().normalize();
                for (double d = 0; d <= len; d += 1.2) {
                    Location pt = a.clone().add(dir.clone().multiply(d)).add(0, 1.0, 0);
                    w.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, dust, true);
                    if (activeDamage && d % 2.4 < 1.2) w.spawnParticle(Particle.END_ROD, pt, 1, 0.05, 0.05, 0.05, 0.01);
                }
                if (activeDamage && actionTick % 6 == 0) {
                    for (Player p : players) {
                        if (Math.abs(p.getY() - center.getY()) <= 3.2 && distanceToSegment2D(p.getLocation(), a, dir, len) <= 1.35 && zapped.add(p.getUniqueId())) {
                            if (dealRepeatedDamage(p, 11.0, DamageType.INDIRECT_MAGIC)) rider(p);
                        }
                    }
                }
            }
        }
    }

    private void tickMothChrysalis(List<Player> players) {
        // Prismatic Chrysalis: 2 detonations (3 from phase 2) with 30 / 26 / 22t warnings. After each one the safe
        // pair jumps to a random new pair of sectors. Then the wings burst open and she settles low (42t).
        int bursts = phaseTwo ? 3 : 2;
        int rel = actionTick - stepStart;
        if (comboStep < bursts) {
            int warn = comboStep == 0 ? 30 : comboStep == 1 ? 26 : 22;
            if (rel <= warn) {
                drawChrysalisSectors(safeSectorIndex);
            } else {
                detonateChrysalisSectors(players, safeSectorIndex);
                comboStep++;
                stepStart = actionTick;
                if (comboStep < bursts) {
                    safeSectorIndex = (safeSectorIndex + 1 + ThreadLocalRandom.current().nextInt(2)) % 6;
                    broadcast(players, "§d✦ ช่องปลอดภัยย้ายตำแหน่ง! ขยับตามแสงสีเขียวมรกตเดี๋ยวนี้!");
                } else {
                    playClip("ultimate_active", true);
                }
            }
            return;
        }
        if (rel == 14) {
            playClip("ultimate_recover", true);
            lowHoverWindow = true;
            lowHoverTicks = 42;
            broadcast(players, "§a✦ ดักแด้ปริซึมคลายออก! Crystal Moth Queen ร่อนต่ำลงใกล้พื้น — โอกาสโจมตีทำลายปีก!");
        } else if (rel >= 54) {
            finishAction(26);
        }
    }

    private void drawChrysalisSectors(int safeSector) {
        World w = center.getWorld();
        var safeDust = new Particle.DustOptions(Color.fromRGB(70, 255, 165), 1.6f);
        var warnDust = new Particle.DustOptions(Color.fromRGB(245, 70, 210), 1.3f);
        for (int s = 0; s < 6; s++) {
            boolean safe = (s == safeSector) || (s == (safeSector + 3) % 6);
            double a0 = s * (Math.PI / 3.0);
            double aMid = a0 + Math.PI / 6.0;
            for (double r = 4.0; r <= arena.radius() - 2; r += 2.8) {
                w.spawnParticle(Particle.DUST, center.clone().add(Math.cos(a0) * r, 0.2, Math.sin(a0) * r), 1, 0, 0, 0, 0, safe ? safeDust : warnDust, true);
                w.spawnParticle(Particle.DUST, center.clone().add(Math.cos(aMid) * r, 0.2, Math.sin(aMid) * r), 1, 0, 0, 0, 0, safe ? safeDust : warnDust, true);
            }
        }
    }

    private void detonateChrysalisSectors(List<Player> players, int safeSector) {
        World w = center.getWorld();
        w.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.6f, 0.6f);
        w.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.1f, 1.25f);
        for (int s = 0; s < 6; s++) {
            boolean safe = (s == safeSector) || (s == (safeSector + 3) % 6);
            if (safe) continue;
            double aMid = s * (Math.PI / 3.0) + Math.PI / 6.0;
            for (double r = 5.0; r <= arena.radius() - 3; r += 5.0) {
                w.spawnParticle(Particle.END_ROD, center.clone().add(Math.cos(aMid) * r, 0.8, Math.sin(aMid) * r), 8, 1.5, 0.5, 1.5, 0.05);
            }
        }
        for (Player p : players) {
            double dx = p.getX() - center.getX(), dz = p.getZ() - center.getZ();
            double ang = Math.atan2(dz, dx);
            if (ang < 0) ang += Math.PI * 2;
            int sector = (int) Math.floor(ang / (Math.PI / 3.0)) % 6;
            boolean safe = (sector == safeSector) || (sector == (safeSector + 3) % 6);
            if (!safe) {
                if (dealHeavyDamage(p, 11.5, DamageType.INDIRECT_MAGIC)) afflict(p, PotionEffectType.SLOWNESS, 40, 1);
            }
        }
    }

    private void tickMothShardNova(List<Player> players) {
        // Shard Nova: 14t ring tell around the Queen and a point-blank crystal burst, then shard volleys every 10t
        // (3, 4 from phase 2) that fall 16t later on every challenger; phase 2 leads moving players.
        if (actionTick <= 14) {
            drawCircleTelegraph(at, 5.5, Color.fromRGB(245, 120, 255), Particle.END_ROD);
        } else if (actionTick == 15) {
            playClip("skill_active", true);
            World w = center.getWorld();
            w.playSound(at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.6f, 0.6f);
            w.spawnParticle(Particle.END_ROD, at.clone().add(0, 1.2, 0), 60, 2.6, 0.6, 2.6, 0.12);
            for (Player p : players) {
                if (p.getLocation().distanceSquared(at) <= 5.5 * 5.5 && Math.abs(p.getY() - at.getY()) <= 3.5) {
                    if (dealBossDamage(p, 9.5, DamageType.INDIRECT_MAGIC)) rider(p);
                    knockback(p, at, 1.0, 0.4);
                }
            }
        }
        int volleys = phaseTwo ? 4 : 3;
        int k = actionTick - 18;
        if (k >= 0 && k % 10 == 0 && k / 10 < volleys) {
            center.getWorld().playSound(at, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.4f, 0.8f);
            for (Player p : players) {
                spawnFallingStrike(strikeTarget(p, phaseTwo && (k / 10) % 2 == 1, 16), 3.0, 16, 10.0, Material.AMETHYST_BLOCK, 0.0);
            }
            if (phaseTwo) {
                for (int i = 0; i < 2; i++) spawnFallingStrike(randomArenaPoint(5.0), 3.0, 16, 10.0, Material.AMETHYST_BLOCK, 0.0);
            }
        }
        int end = 18 + (volleys - 1) * 10 + 18;
        if (actionTick == end) playClip("skill_recover", true);
        if (actionTick >= end + 16) finishAction(20);
    }

    // =========================================================================
    // 4. UNLIT SUN SKILLS
    // =========================================================================

    private void beginSolarGaze(Player target) {
        struck.clear();
        stepStart = actionTick;
        if (target != null) faceLocation(target.getLocation());
        lockedOrigin = at.clone();
        lockedDir = directionFromYaw(at.getYaw());
        playClip("attack_windup", true);
        center.getWorld().playSound(at, Sound.BLOCK_BEACON_AMBIENT, 1.4f, 1.25f);
    }

    private void tickSunSolarGaze(List<Player> players) {
        // Solar Gaze: tracks for 6t, then a 12t locked golden line and a 30-block beam. 1 beam, 2 from phase 2,
        // 3 final, each re-aimed. Every beam leaves the eye open (bonus damage window) for 48t.
        int beams = phaseThree ? 3 : phaseTwo ? 2 : 1;
        int rel = actionTick - stepStart;
        if (comboStep >= beams) {
            if (rel == 8) {
                playClip("attack_recover", true);
                for (Player p : players) {
                    p.sendActionBar(Component.text("§e✦ ดวงตาสุริยะเปิดค้างอยู่ — โจมตีบอสตอนนี้เพื่อรับโบนัสดาเมจ!"));
                }
            } else if (rel >= 39) {
                finishAction(22);
            }
            return;
        }
        if (rel == 1 && comboStep > 0) playClip("attack_windup", true);
        if (rel <= 18) {
            if (rel <= 6) {
                Player t = nearestTarget(players);
                if (t != null) {
                    faceLocation(t.getLocation());
                    lockedDir = directionFromYaw(at.getYaw());
                }
            }
            drawLineTelegraphWithSky(lockedOrigin, lockedDir, 30.0, 2.3, Color.fromRGB(255, 215, 65), false);
        } else {
            playClip("attack_active", true);
            eyeOpenWindow = true;
            eyeOpenTicks = 48;
            World w = center.getWorld();
            w.playSound(at, Sound.ENTITY_GUARDIAN_ATTACK, 1.5f, 0.7f);
            w.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 1.4f, 1.5f);
            for (double d = 1.5; d <= 30.0; d += 1.4) {
                Location pt = lockedOrigin.clone().add(lockedDir.clone().multiply(d)).add(0, 1.1, 0);
                w.spawnParticle(Particle.END_ROD, pt, 3, 0.25, 0.25, 0.25, 0.02);
                w.spawnParticle(Particle.FLAME, pt, 4, 0.3, 0.3, 0.3, 0.01);
            }
            for (Player p : players) {
                if (Math.abs(p.getY() - center.getY()) <= 3.6 && distanceToSegment2D(p.getLocation(), lockedOrigin, lockedDir, 30.0) <= 2.35 && firstContact(p)) {
                    if (dealBossDamage(p, 11.5, DamageType.INDIRECT_MAGIC)) rider(p);
                }
            }
            comboStep++;
            stepStart = actionTick;
            struck.clear();
            lockedOrigin = at.clone();
        }
    }

    private void tickSunRingStorm(List<Player> players) {
        // Corona Rings: 20t windup, then a jump-or-gap ring every 14t: 2 rings, 3 from phase 2 (2 narrower gaps,
        // faster), 4 final.
        int rings = phaseThree ? 4 : phaseTwo ? 3 : 2;
        int gaps = phaseTwo ? 2 : 3;
        double gapHalf = Math.toRadians(phaseTwo ? 15 : 18);
        double speed = phaseTwo ? 1.2 : 1.05;
        if (actionTick <= 20) {
            drawCircleTelegraph(at, 4.5, Color.fromRGB(255, 200, 70), Particle.END_ROD);
        }
        int k = actionTick - 21;
        if (k >= 0 && k % 14 == 0 && k / 14 < rings) {
            playClip("skill_active", true);
            center.getWorld().playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.4f, 0.85f + 0.1f * (k / 14));
            double gapAngle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            Color color = (k / 14) % 2 == 0 ? Color.fromRGB(255, 205, 65) : Color.fromRGB(185, 110, 255);
            activeRings.add(new ExpandingRing(at, 3.0, arena.radius(), speed, gapAngle, gaps, gapHalf, 11.0, color));
        }
        int end = 21 + (rings - 1) * 14;
        if (actionTick == end + 10) playClip("skill_recover", true);
        if (actionTick >= end + 28) finishAction(22);
    }

    private void tickSunMonoliths(List<Player> players) {
        // Monolith Judgment: 3 steps (4 from phase 2, 5 final), 16t tell each, the axis turning every step.
        // Lanes alternate between the flank pair (centre safe) and centre + far edges (flanks safe).
        int totalSteps = phaseThree ? 5 : phaseTwo ? 4 : 3;
        int stepDuration = 22;
        int tell = 16;
        int currentStep = (actionTick - 1) / stepDuration;
        int stepTick = (actionTick - 1) % stepDuration;

        if (currentStep < totalSteps) {
            double angle = eclipseAxisAngle + currentStep * (Math.PI / totalSteps);
            Vector dir = new Vector(Math.cos(angle), 0, Math.sin(angle));
            Vector perp = new Vector(-dir.getZ(), 0, dir.getX());
            double half = arena.radius() - 3;
            double length = half * 2.0;
            double[] offsets = currentStep % 2 == 0 ? new double[]{7.0, -7.0} : new double[]{0.0, 14.0, -14.0};
            if (stepTick < tell) {
                for (double o : offsets) {
                    Location lane = center.clone().add(perp.clone().multiply(o)).subtract(dir.clone().multiply(half));
                    drawLineTelegraphWithSky(lane, dir, length, 3.0, Color.fromRGB(180, 85, 255), false);
                }
            } else if (stepTick == tell) {
                playClip("skill_active", true);
                struck.clear();
                for (double o : offsets) {
                    Location lane = center.clone().add(perp.clone().multiply(o)).subtract(dir.clone().multiply(half));
                    fireMonolithBeam(players, lane, dir, length, 3.0, 11.5);
                }
            }
        } else if (actionTick == totalSteps * stepDuration + 1) {
            playClip("skill_recover", true);
        } else if (actionTick >= totalSteps * stepDuration + 16) {
            finishAction(22);
        }
    }

    private void fireMonolithBeam(List<Player> players, Location start, Vector dir, double length, double width, double dmg) {
        World w = center.getWorld();
        w.playSound(center, Sound.ENTITY_SHULKER_SHOOT, 1.4f, 0.65f);
        for (double d = 0; d <= length; d += 1.8) {
            Location pt = start.clone().add(dir.clone().multiply(d)).add(0, 1.0, 0);
            w.spawnParticle(Particle.PORTAL, pt, 5, 0.4, 0.3, 0.4, 0.1);
            w.spawnParticle(Particle.END_ROD, pt, 2, 0.2, 0.2, 0.2, 0.02);
        }
        for (Player p : players) {
            if (Math.abs(p.getY() - center.getY()) <= 3.6 && distanceToSegment2D(p.getLocation(), start, dir, length) <= width && firstContact(p)) {
                if (dealBossDamage(p, dmg, DamageType.INDIRECT_MAGIC)) rider(p);
            }
        }
    }

    private void tickSunTotalEclipse(List<Player> players) {
        // Total Eclipse: alternating halves detonate (2, 3 from phase 2) after 34 / 28 / 24t warnings,
        // then a 16t-telegraphed sonic core pulse and an extended Opened-Eye window.
        eyeOpenWindow = true;
        eyeOpenTicks = 60;
        int swaps = phaseTwo ? 3 : 2;
        int rel = actionTick - stepStart;
        if (comboStep < swaps) {
            int warn = comboStep == 0 ? 34 : comboStep == 1 ? 28 : 24;
            if (rel <= warn) {
                drawEclipseHalves(eclipseAxisAngle, eclipseShadowSafe);
            } else {
                detonateEclipseHalf(players, eclipseAxisAngle, eclipseShadowSafe);
                eclipseShadowSafe = !eclipseShadowSafe;
                comboStep++;
                stepStart = actionTick;
                if (comboStep < swaps) {
                    broadcast(players, eclipseShadowSafe
                        ? "§e✦ สลับขั้วสุริยุปราคา! วิ่งข้ามเส้นกลางกลับเข้า §bฝั่งเงามืด §eเดี๋ยวนี้!"
                        : "§e✦ สลับขั้วสุริยุปราคา! วิ่งข้ามเส้นกลางเข้าสู่ §6ฝั่งแสงสุริยะ §eเดี๋ยวนี้!");
                } else {
                    playClip("ultimate_active", true);
                }
            }
            return;
        }
        if (rel <= 16) {
            drawCircleTelegraph(center, 7.0, Color.fromRGB(255, 190, 50), Particle.END_ROD);
        } else if (rel == 17) {
            center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.7f);
            center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center.clone().add(0, 1.5, 0), 1, 0, 0, 0, 0);
            for (Player p : players) {
                if (p.getLocation().distanceSquared(center) <= 7.0 * 7.0) {
                    dealBossDamage(p, 16.0, DamageType.SONIC_BOOM);
                    knockback(p, center, 0.9, 0.35);
                }
            }
            playClip("ultimate_recover", true);
            eyeOpenWindow = true;
            eyeOpenTicks = 56;
            broadcast(players, "§a✦ สุริยุปราคาสิ้นสุด! แกนดวงอาทิตย์เปิดออกและลดระดับลงต่ำ — โจมตีรับโบนัสดาเมจ!");
        } else if (rel >= 66) {
            finishAction(28);
        }
    }

    private void drawEclipseHalves(double axisAngle, boolean shadowSafe) {
        World w = center.getWorld();
        Vector normal = new Vector(-Math.sin(axisAngle), 0, Math.cos(axisAngle));
        Vector lineDir = new Vector(Math.cos(axisAngle), 0, Math.sin(axisAngle));
        var safeDust = new Particle.DustOptions(Color.fromRGB(70, 255, 170), 1.6f);
        var lightDust = new Particle.DustOptions(Color.fromRGB(255, 205, 55), 1.4f);
        var shadowDust = new Particle.DustOptions(Color.fromRGB(150, 70, 255), 1.4f);

        for (double d = -(arena.radius() - 2); d <= arena.radius() - 2; d += 2.2) {
            Location mid = center.clone().add(lineDir.clone().multiply(d)).add(0, 0.2, 0);
            w.spawnParticle(Particle.END_ROD, mid, 1, 0, 0, 0, 0);
            Location sideSafe = mid.clone().add(normal.clone().multiply(shadowSafe ? -6.0 : 6.0));
            Location sideDanger = mid.clone().add(normal.clone().multiply(shadowSafe ? 6.0 : -6.0));
            w.spawnParticle(Particle.DUST, sideSafe, 1, 0, 0, 0, 0, safeDust, true);
            w.spawnParticle(Particle.DUST, sideDanger, 1, 0, 0, 0, 0, shadowSafe ? lightDust : shadowDust, true);
        }
    }

    private void detonateEclipseHalf(List<Player> players, double axisAngle, boolean shadowSafe) {
        World w = center.getWorld();
        w.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.3f, 0.85f);
        Vector normal = new Vector(-Math.sin(axisAngle), 0, Math.cos(axisAngle));
        for (Player p : players) {
            Vector rel = p.getLocation().toVector().subtract(center.toVector());
            double side = rel.getX() * normal.getX() + rel.getZ() * normal.getZ();
            // side >= 0 is Light side; side < 0 is Shadow side
            boolean inSafeHalf = shadowSafe ? (side <= 0.5) : (side >= -0.5);
            if (!inSafeHalf) {
                if (dealHeavyDamage(p, 12.0, DamageType.INDIRECT_MAGIC)) afflict(p, PotionEffectType.DARKNESS, 50, 0);
            }
        }
    }

    private void tickSunFallingStars(List<Player> players) {
        // Dying Stars: a volley every 10t (4, 5 from phase 2); each star lands 18t later on every challenger,
        // every other volley aimed where moving players are heading, plus two strays.
        int volleys = phaseTwo ? 5 : 4;
        if (actionTick == 10) playClip("skill_active", true);
        int k = actionTick - 10;
        if (k >= 0 && k % 10 == 0 && k / 10 < volleys) {
            center.getWorld().playSound(at, Sound.ENTITY_BLAZE_SHOOT, 1.3f, 1.2f);
            for (Player p : players) {
                spawnFallingStrike(strikeTarget(p, (k / 10) % 2 == 1, 18), 3.2, 18, 10.5, Material.SHROOMLIGHT, 0.0);
            }
            for (int i = 0; i < 2; i++) spawnFallingStrike(randomArenaPoint(5.0), 3.2, 18, 10.5, Material.SHROOMLIGHT, 0.0);
        }
        int end = 10 + (volleys - 1) * 10 + 20;
        if (actionTick == end) playClip("skill_recover", true);
        if (actionTick >= end + 16) finishAction(22);
    }

    // =========================================================================
    // FINAL PHASE (30% HP) — arena-wide pressure that runs alongside every move
    // =========================================================================

    private void tickFinalPhase(List<Player> players) {
        if (!phaseThree || action == Action.TRANSITION || action == Action.SPAWN || players.isEmpty()) return;
        ambientTicks++;
        switch (kind) {
            case CINDER_COLOSSUS -> {
                if (ambientTicks % 60 == 0) {
                    center.getWorld().playSound(at, Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.5f);
                    for (Player p : players) spawnFallingStrike(strikeTarget(p, true, 20), 3.0, 20, 9.0, Material.MAGMA_BLOCK, 0.0);
                }
            }
            case SOUL_HOUND -> {
                if (ambientTicks % 70 == 0) {
                    center.getWorld().playSound(at, Sound.PARTICLE_SOUL_ESCAPE, 1.5f, 0.6f);
                    for (Player p : players) hotZones.add(new HotZone(groundAt(p), 3.0, 80, 16, 4.0));
                }
            }
            case CRYSTAL_MOTH -> {
                if (ambientTicks % 50 == 0) {
                    center.getWorld().playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.4f, 0.5f);
                    for (Player p : players) spawnFallingStrike(strikeTarget(p, true, 18), 2.8, 18, 9.0, Material.AMETHYST_BLOCK, 0.0);
                }
            }
            case UNLIT_SUN -> tickCoronaSweep(players);
        }
    }

    private void tickCoronaSweep(List<Player> players) {
        // Two arms sweep the ring once every 16s; a pale line runs 12 degrees ahead. Jump over an arm as it passes.
        coronaAngle += Math.PI * 2 / 320.0;
        World w = center.getWorld();
        double reach = arena.radius() - 1.0;
        var warn = new Particle.DustOptions(Color.fromRGB(255, 240, 150), 1.1f);
        var beam = new Particle.DustOptions(Color.fromRGB(255, 170, 40), 1.7f);
        for (int arm = 0; arm < 2; arm++) {
            double a = coronaAngle + arm * Math.PI;
            double lead = a + Math.toRadians(12);
            for (double r = 3.0; r <= reach; r += 1.5) {
                w.spawnParticle(Particle.DUST, center.clone().add(Math.cos(a) * r, 0.45, Math.sin(a) * r), 1, 0, 0, 0, 0, beam, true);
                if (tick % 2 == 0) w.spawnParticle(Particle.DUST, center.clone().add(Math.cos(lead) * r, 0.2, Math.sin(lead) * r), 1, 0, 0, 0, 0, warn, true);
            }
            if (tick % 3 == 0) {
                w.spawnParticle(Particle.FLAME, center.clone().add(Math.cos(a) * reach * 0.6, 0.6, Math.sin(a) * reach * 0.6), 4, 1.5, 0.2, 1.5, 0.01);
            }
            Vector dir = new Vector(Math.cos(a), 0, Math.sin(a));
            for (Player p : players) {
                if (p.getY() - center.getY() > 1.25) continue;
                if (Math.hypot(p.getX() - center.getX(), p.getZ() - center.getZ()) < 2.5) continue;
                if (distanceToSegment2D(p.getLocation(), center, dir, reach) > 1.1) continue;
                Long last = coronaHits.get(p.getUniqueId());
                if (last != null && tick - last < 20) continue;
                coronaHits.put(p.getUniqueId(), tick);
                if (dealBossDamage(p, 9.0, DamageType.INDIRECT_MAGIC)) rider(p);
            }
        }
    }

    // =========================================================================
    // SUB-SYSTEM TICKS (Hot Zones, Falling Strikes, Destructible Orbs/Pylons, Rings)
    // =========================================================================

    private Color kindColor() {
        return switch (kind) {
            case CINDER_COLOSSUS -> Color.fromRGB(255, 95, 20);
            case SOUL_HOUND -> Color.fromRGB(65, 230, 255);
            case CRYSTAL_MOTH -> Color.fromRGB(240, 110, 255);
            case UNLIT_SUN -> Color.fromRGB(255, 200, 60);
        };
    }

    private void tickHotZones(List<Player> players) {
        Color tone = kindColor();
        var dust = new Particle.DustOptions(tone, 1.4f);
        Set<UUID> burned = new HashSet<>();
        for (var it = hotZones.iterator(); it.hasNext(); ) {
            HotZone z = it.next();
            if (z.armTicks > 0) {
                // Armed zones flash white first and only burn once the warning ends
                z.armTicks--;
                if (tick % 2 == 0) drawCircleTelegraph(z.center, z.radius, Color.fromRGB(255, 255, 255), null);
                continue;
            }
            z.remainingTicks--;
            if (z.remainingTicks <= 0) {
                it.remove();
                continue;
            }
            if (tick % 4 == 0) {
                for (int n = 0; n < 12; n++) {
                    double a = n * Math.PI / 6.0;
                    z.center.getWorld().spawnParticle(Particle.DUST,
                        z.center.clone().add(Math.cos(a) * z.radius, 0.18, Math.sin(a) * z.radius), 1, 0, 0, 0, 0, dust, true);
                }
            }
            if (tick % 20 == 0) {
                for (Player p : players) {
                    if (Math.abs(p.getY() - z.center.getY()) <= 2.2 && p.getLocation().distanceSquared(z.center) <= z.radius * z.radius
                            && burned.add(p.getUniqueId())) {
                        if (dealLingeringDamage(p, z.damage, DamageType.INDIRECT_MAGIC)) lingerRider(p);
                    }
                }
            }
        }
    }

    private void spawnFallingStrike(Location target, double radius, int delayTicks, double damage, Material material, double emberRadius) {
        RubbleStrike strike = new RubbleStrike(target, radius, delayTicks, damage, emberRadius);
        BlockData data = Bukkit.createBlockData(material);
        strike.boulder = target.getWorld().spawn(target.clone().add(0, delayTicks * 0.55, 0), BlockDisplay.class, bd -> {
            bd.setBlock(data);
            bd.setPersistent(false);
            bd.setTeleportDuration(2);
            bd.setTransformation(new Transformation(
                new Vector3f(-0.75f, -0.75f, -0.75f),
                new AxisAngle4f(0.4f, 1, 1, 0),
                new Vector3f(1.5f, 1.5f, 1.5f),
                new AxisAngle4f(0, 0, 1, 0)
            ));
            bd.getPersistentDataContainer().set(plugin.key("world_boss_entity"), PersistentDataType.BYTE, (byte) 1);
        });
        rubbleStrikes.add(strike);
    }

    private void tickRubbleStrikes(List<Player> players) {
        Color warn = switch (kind) {
            case CRYSTAL_MOTH -> Color.fromRGB(240, 130, 255);
            case UNLIT_SUN -> Color.fromRGB(255, 215, 90);
            default -> Color.fromRGB(255, 135, 35);
        };
        Set<UUID> crushed = new HashSet<>();
        for (var it = rubbleStrikes.iterator(); it.hasNext(); ) {
            RubbleStrike s = it.next();
            s.timer--;
            drawCircleTelegraph(s.target, s.radius, warn, Particle.SMOKE);
            if (s.boulder != null && s.boulder.isValid()) {
                double h = Math.max(0.4, s.timer * 0.55);
                s.boulder.teleport(s.target.clone().add(0, h, 0));
            }
            if (s.timer <= 0) {
                if (s.boulder != null) s.boulder.remove();
                World w = s.target.getWorld();
                switch (kind) {
                    case CRYSTAL_MOTH -> {
                        w.playSound(s.target, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.3f, 0.8f);
                        w.spawnParticle(Particle.END_ROD, s.target.clone().add(0, 0.6, 0), 18, 1.0, 0.3, 1.0, 0.06);
                    }
                    case UNLIT_SUN -> {
                        w.playSound(s.target, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.3f);
                        w.spawnParticle(Particle.FLAME, s.target.clone().add(0, 0.5, 0), 24, 1.0, 0.3, 1.0, 0.04);
                    }
                    default -> {
                        w.playSound(s.target, Sound.BLOCK_NETHER_BRICKS_BREAK, 1.3f, 0.65f);
                        w.spawnParticle(Particle.EXPLOSION, s.target.clone().add(0, 0.5, 0), 2, 0.6, 0.2, 0.6, 0.02);
                    }
                }
                for (Player p : players) {
                    if (Math.abs(p.getY() - s.target.getY()) <= 3.0 && p.getLocation().distanceSquared(s.target) <= s.radius * s.radius
                            && crushed.add(p.getUniqueId())) {
                        if (dealBossDamage(p, s.damage, DamageType.INDIRECT_MAGIC)) rider(p);
                    }
                }
                if (s.emberRadius > 0) hotZones.add(new HotZone(s.target, s.emberRadius, 70));
                it.remove();
            }
        }
    }

    private void tickDestructibleNodes(List<Player> players) {
        for (var it = activeNodes.iterator(); it.hasNext(); ) {
            DestructibleNode node = it.next();
            if (!node.alive || node.hp <= 0) {
                node.remove();
                it.remove();
                continue;
            }
            boolean moved = false;
            if (node.velocity.lengthSquared() > 1e-6) {
                node.loc.add(node.velocity);
                moved = true;
            }
            if (node.orbitSpeed != 0.0) {
                double dx = node.loc.getX() - center.getX(), dz = node.loc.getZ() - center.getZ();
                double c = Math.cos(node.orbitSpeed), s = Math.sin(node.orbitSpeed);
                node.loc.setX(center.getX() + dx * c - dz * s);
                node.loc.setZ(center.getZ() + dx * s + dz * c);
                moved = true;
            }
            if (moved) {
                if (node.display != null && node.display.isValid()) node.display.teleport(node.loc);
                if (node.hitbox != null && node.hitbox.isValid()) node.hitbox.teleport(node.loc);
            }
            if (tick % 3 == 0) {
                node.loc.getWorld().spawnParticle(
                    kind == RealmBosses.Kind.SOUL_HOUND ? Particle.SOUL_FIRE_FLAME : Particle.END_ROD,
                    node.loc.clone().add(0, 0.8, 0), 4, 0.25, 0.25, 0.25, 0.02);
            }
        }
    }

    private void tickExpandingRings(List<Player> players) {
        var safeDust = new Particle.DustOptions(Color.fromRGB(90, 255, 220), 1.4f);
        for (var it = activeRings.iterator(); it.hasNext(); ) {
            ExpandingRing ring = it.next();
            ring.radius += ring.speed;
            if (ring.radius > ring.maxRadius) {
                it.remove();
                continue;
            }
            World w = ring.center.getWorld();
            var ringDust = new Particle.DustOptions(ring.color, 1.6f);
            for (int n = 0; n < 72; n++) {
                double a = n * Math.PI / 36.0;
                boolean inGap = isAngleInSafeLane(a, ring.gapBaseAngle, ring.gapCount, ring.gapHalfWidthRad * 2);
                w.spawnParticle(Particle.DUST,
                    ring.center.clone().add(Math.cos(a) * ring.radius, 0.35, Math.sin(a) * ring.radius),
                    1, 0, 0, 0, 0, inGap ? safeDust : ringDust, true);
            }
            for (Player p : players) {
                if (ring.hitPlayers.contains(p.getUniqueId())) continue;
                // Jumpable low wave (height <= 1.25 blocks above floor)
                if (p.getY() - ring.center.getY() > 1.25) continue;
                double dx = p.getX() - ring.center.getX(), dz = p.getZ() - ring.center.getZ();
                double dist = Math.hypot(dx, dz);
                if (Math.abs(dist - ring.radius) <= 1.35) {
                    double ang = Math.atan2(dz, dx);
                    if (!isAngleInSafeLane(ang, ring.gapBaseAngle, ring.gapCount, ring.gapHalfWidthRad * 2)) {
                        ring.hitPlayers.add(p.getUniqueId());
                        if (dealBossDamage(p, ring.damage, DamageType.INDIRECT_MAGIC)) rider(p);
                    }
                }
            }
        }
    }

    // =========================================================================
    // TARGETING & STRIKE HELPERS
    // =========================================================================

    /** One landing per player per strike; each new strike in a combo clears the set. */
    private boolean firstContact(Player p) {
        return struck.add(p.getUniqueId());
    }

    private Location groundAt(Player p) {
        return new Location(center.getWorld(), p.getX(), center.getY(), p.getZ());
    }

    /** Where to drop a strike: the player's spot, or (lead) where their recent movement carries them. */
    private Location strikeTarget(Player p, boolean lead, int delayTicks) {
        Location loc = groundAt(p);
        Vector step = motion.get(p.getUniqueId());
        if (lead && step != null) {
            Vector ahead = step.clone().multiply(delayTicks * 0.8);
            if (ahead.lengthSquared() > 36.0) ahead.normalize().multiply(6.0);
            loc.add(ahead);
        }
        clampToArena(loc);
        return loc;
    }

    private Location randomArenaPoint(double minRadius) {
        double ang = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
        double r = ThreadLocalRandom.current().nextDouble(minRadius, Math.max(minRadius + 1.0, arena.radius() - 5.0));
        return center.clone().add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
    }

    private Player farthestTarget(List<Player> players, Location from) {
        Player best = null;
        double bestDist = -1;
        for (Player p : players) {
            double d = p.getLocation().distanceSquared(from);
            if (d > bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    /** Smoothed per-tick ground movement, used to lead falling strikes. */
    private void trackMotion(List<Player> players) {
        for (Player p : players) {
            Location now = p.getLocation();
            Location before = lastSeen.put(p.getUniqueId(), now);
            if (before == null || before.getWorld() != now.getWorld()) continue;
            Vector step = now.toVector().subtract(before.toVector()).setY(0);
            if (step.lengthSquared() > 4.0) step.zero(); // teleports and knockback spikes
            motion.merge(p.getUniqueId(), step, (old, cur) -> old.multiply(0.6).add(cur.multiply(0.4)));
        }
    }

    private boolean inCone(Player p, Location origin, Vector dir, double radius, double halfAngle) {
        double dx = p.getX() - origin.getX(), dz = p.getZ() - origin.getZ();
        double d = Math.hypot(dx, dz);
        if (d > radius) return false;
        if (d < 1.2) return true; // under the jaws
        return (dx * dir.getX() + dz * dir.getZ()) / d >= Math.cos(halfAngle);
    }

    private void drawConeTelegraph(Location origin, Vector dir, double radius, double halfAngle, Color color) {
        World w = origin.getWorld();
        var dust = new Particle.DustOptions(color, 1.35f);
        double base = Math.atan2(dir.getZ(), dir.getX());
        int arcPoints = Math.max(8, (int) Math.round(radius * halfAngle * 2 / 0.9));
        for (int i = 0; i <= arcPoints; i++) {
            double a = base - halfAngle + (2 * halfAngle) * i / arcPoints;
            w.spawnParticle(Particle.DUST, origin.clone().add(Math.cos(a) * radius, 0.18, Math.sin(a) * radius), 1, 0, 0, 0, 0, dust, true);
        }
        for (double r = 1.0; r < radius; r += 1.0) {
            for (double a : new double[]{base - halfAngle, base + halfAngle}) {
                w.spawnParticle(Particle.DUST, origin.clone().add(Math.cos(a) * r, 0.18, Math.sin(a) * r), 1, 0, 0, 0, 0, dust, true);
            }
        }
    }

    /** Kind-flavoured rider on a landed direct strike. */
    private void rider(Player p) {
        switch (kind) {
            case CINDER_COLOSSUS -> ignite(p, 60);
            case SOUL_HOUND -> afflict(p, PotionEffectType.WITHER, 60, 0);
            case CRYSTAL_MOTH -> afflict(p, PotionEffectType.SLOWNESS, 30, 1);
            case UNLIT_SUN -> ignite(p, 50);
        }
    }

    private void lingerRider(Player p) {
        switch (kind) {
            case CINDER_COLOSSUS -> ignite(p, 40);
            case SOUL_HOUND -> afflict(p, PotionEffectType.WITHER, 40, 0);
            case CRYSTAL_MOTH -> afflict(p, PotionEffectType.SLOWNESS, 20, 0);
            case UNLIT_SUN -> ignite(p, 30);
        }
    }

    private void afflict(Player p, PotionEffectType type, int ticks, int amplifier) {
        if (!p.isOnline() || p.isDead()) return;
        PotionEffect current = p.getPotionEffect(type);
        if (current == null || current.getAmplifier() < amplifier
                || (current.getAmplifier() == amplifier && current.getDuration() < ticks)) {
            BossDamage.effect(p,new PotionEffect(type, ticks, amplifier, false, true, true),kind.title);
        }
    }

    private void ignite(Player p, int ticks) {
        if (!p.isOnline() || p.isDead()) return;
        int before = p.getFireTicks();
        BossDamage.ignite(p,ticks,kind.title);
        if (kind == RealmBosses.Kind.UNLIT_SUN && plugin.realmSanctuaryEncounters() != null)
            plugin.realmSanctuaryEncounters().trackSunFire(p, before, ticks);
    }

    // =========================================================================
    // DAMAGE & HITBOX ROUTING
    // =========================================================================

    public boolean tryHitDestructibleNode(Player player, Entity targetEntity, double rayReach) {
        // 1. Direct entity match
        if (targetEntity != null) {
            for (DestructibleNode node : activeNodes) {
                if (node.alive && (targetEntity.equals(node.hitbox) || targetEntity.equals(node.display))) {
                    damageDestructibleNode(player, node, 14.0);
                    return true;
                }
            }
        }
        // 2. Raytrace look ray against active orbs/crystals
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        for (DestructibleNode node : activeNodes) {
            if (!node.alive || node.loc.getWorld() != eye.getWorld()) continue;
            Location centerNode = node.loc.clone().add(0, 0.9, 0);
            Vector toNode = centerNode.toVector().subtract(eye.toVector());
            double proj = toNode.dot(dir);
            if (proj < 0 || proj > rayReach) continue;
            Vector closest = eye.toVector().add(dir.clone().multiply(proj));
            if (closest.distanceSquared(centerNode.toVector()) <= 1.55 * 1.55) {
                damageDestructibleNode(player, node, 14.0);
                return true;
            }
        }
        return false;
    }

    private void damageDestructibleNode(Player player, DestructibleNode node, double amount) {
        node.hp -= amount;
        World w = node.loc.getWorld();
        w.playSound(node.loc, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.3f, 1.25f);
        w.spawnParticle(Particle.CRIT, node.loc.clone().add(0, 0.9, 0), 15, 0.35, 0.35, 0.35, 0.1);
        if (node.hp <= 0) {
            node.remove();
            activeNodes.remove(node);
            if (player != null) {
                player.sendActionBar(Component.text(kind == RealmBosses.Kind.SOUL_HOUND
                    ? "§b✦ ทำลายลูกไฟวิญญาณสำเร็จ! (เหลือ " + activeNodes.size() + " ลูก)"
                    : "§d✦ ทำลายเสาคริสตัลสะท้อนแสงสำเร็จ!"));
            }
        }
    }

    /** Returns nearest hit distance along player's look ray if within melee reach, or Double.POSITIVE_INFINITY. */
    public double meleeRayDistance(Player player, double reach, long swingTick) {
        if (dead || hiddenUnderground || carrier == null || !carrier.isValid()) return Double.POSITIVE_INFINITY;
        double d = BossHitbox.ray(player, kind.model, currentClip, clipSeconds(swingTick), renderLocation(), modelScale(), reach);
        if (Double.isFinite(d)) return d;

        // Generous cylinder check for ground melee & airborne Mace dives
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        double radius = switch (kind) {
            case CINDER_COLOSSUS -> 2.8;
            case SOUL_HOUND -> 2.2;
            case CRYSTAL_MOTH -> 2.6;
            case UNLIT_SUN -> 2.7;
        };
        double height = switch (kind) {
            case CINDER_COLOSSUS -> 5.8;
            case SOUL_HOUND -> 3.4;
            case CRYSTAL_MOTH -> 5.2;
            case UNLIT_SUN -> 5.4;
        };
        BoundingBox box = new BoundingBox(
            at.getX() - radius, at.getY() - 0.5, at.getZ() - radius,
            at.getX() + radius, at.getY() + height, at.getZ() + radius
        );
        if (box.contains(eye.toVector())) return 0.0;
        var hit = box.rayTrace(eye.toVector(), dir, reach + 0.65);
        return hit != null ? eye.toVector().distance(hit.getHitPosition()) : Double.POSITIVE_INFINITY;
    }

    /** First contact of an arrow's travelled segment with the visible boss body. */
    public double projectileRayDistance(Location from, Vector direction, double distance) {
        if (dead || hiddenUnderground || carrier == null || !carrier.isValid()
                || from.getWorld() != at.getWorld() || distance <= 0 || direction.lengthSquared() < 1.0e-9) {
            return Double.POSITIVE_INFINITY;
        }
        Vector dir = direction.clone().normalize();
        var wall = from.getWorld().rayTraceBlocks(from, dir, distance, FluidCollisionMode.NEVER, true);
        if (wall != null) distance = Math.min(distance, from.toVector().distance(wall.getHitPosition()));
        double radius = switch (kind) {
            case CINDER_COLOSSUS -> 2.8;
            case SOUL_HOUND -> 2.2;
            case CRYSTAL_MOTH -> 2.6;
            case UNLIT_SUN -> 2.7;
        };
        double height = switch (kind) {
            case CINDER_COLOSSUS -> 5.8;
            case SOUL_HOUND -> 3.4;
            case CRYSTAL_MOTH -> 5.2;
            case UNLIT_SUN -> 5.4;
        };
        BoundingBox body = new BoundingBox(at.getX() - radius, at.getY() - 0.5, at.getZ() - radius,
            at.getX() + radius, at.getY() + height, at.getZ() + radius);
        if (body.contains(from.toVector())) return 0.0;
        var hit = body.rayTrace(from.toVector(), dir, distance);
        double fallback = hit == null ? Double.POSITIVE_INFINITY : from.toVector().distance(hit.getHitPosition());
        double animated = BossHitbox.ray(from, dir, kind.model, currentClip, clipSeconds(tick), renderLocation(), modelScale(), distance);
        return Math.min(animated, fallback);
    }

    private boolean realMelee(EntityDamageEvent event, Player attacker) {
        return attacker != null && event instanceof EntityDamageByEntityEvent by && by.getDamager() == attacker
            && event.getDamageSource().getDirectEntity() == attacker
            && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
            && UniqueAbilityListener.hasAttackStart(attacker, event.getEntity());
    }

    /** Check before other hit effects run; rejected swings do not move the accepted-hit window. */
    public boolean rejectRapidMelee(EntityDamageEvent event, Player attacker) {
        if (!realMelee(event, attacker)) return false;
        Integer previous = lastMeleeHit.get(attacker.getUniqueId());
        return previous != null && Bukkit.getCurrentTick() - previous < 10;
    }

    public void handleIncomingDamage(EntityDamageEvent event, Player attacker) {
        event.setCancelled(true);
        if (dead || health <= 0.0 || hiddenUnderground || attacker == null) return;
        if (rejectRapidMelee(event, attacker)) return;
        if (!fighting) {
            List<Player> initial = presentPlayers();
            awaken(initial.isEmpty() ? List.of(attacker) : initial);
        }
        double raw = Math.clamp(event.getFinalDamage(), 1.0, 180.0);
        // Mace smash still rewards extra falling height, but its extreme burst is
        // compressed before weak-point multipliers and interruption progress.
        if (realMelee(event, attacker) && event.getDamageSource().getDamageType() == DamageType.MACE_SMASH) {
            raw = Math.min(raw, 60.0) + Math.max(0.0, raw - 60.0) * 0.25;
        }
        if (realMelee(event, attacker)) lastMeleeHit.put(attacker.getUniqueId(), Bukkit.getCurrentTick());
        if (perchHunt != null && realMelee(event, attacker) && event.getDamageSource().getDamageType() == DamageType.MACE_SMASH
                && perchHunt.skyfall(attacker)) {
            skyfall(attacker);
        }
        contributors.add(attacker.getUniqueId());
        lastAttack.put(attacker.getUniqueId(), tick);
        if (participants.add(attacker.getUniqueId())) recalculatePartyScaling();

        double multiplier = 1.0;
        if (action == Action.STUN) {
            multiplier = stunDamageMultiplier;
        } else {
            switch (kind) {
                case CINDER_COLOSSUS -> {
                    // Check if attacker is flanking behind the Colossus
                    Vector bossFacing = directionFromYaw(at.getYaw());
                    Vector toAttacker = attacker.getLocation().toVector().subtract(at.toVector()).setY(0);
                    double dot = toAttacker.lengthSquared() > 1e-4 ? toAttacker.normalize().dot(bossFacing) : 1.0;
                    if (dot < -0.15 || coreExposed) {
                        multiplier = coreExposed ? 1.65 : 1.35;
                        if (coreExposed) {
                            coreInterruptDamage += raw * multiplier;
                            attacker.playSound(at, Sound.BLOCK_ANVIL_PLACE, 0.8f, 1.55f);
                            if (coreInterruptDamage >= coreInterruptThreshold) {
                                triggerStun(44, 2.0, "§a✦ ทำลายจังหวะ 'เตาหลอมปะทุ' สำเร็จ! แกนหลังระเบิดทำให้ Cinder Colossus ทรุดลง!");
                            }
                        }
                    } else if (dot > 0.35) {
                        multiplier = 0.85;
                    }
                }
                case CRYSTAL_MOTH -> {
                    double gaugeGain = lowHoverWindow ? 19.0 : 6.5;
                    if (lowHoverWindow) multiplier = 1.25;
                    wingCrackGauge = Math.min(100.0, wingCrackGauge + gaugeGain);
                    if (wingCrackGauge >= 100.0) {
                        wingCrackGauge = 0.0;
                        lowHoverWindow = false;
                        triggerStun(46, 1.85, "§d✦ เกจ 'ปีกแตกร้าว' เต็ม 100%! Crystal Moth Queen ปีกร้าวร่วงลงพื้นเปิดช่องโจมตีใหญ่!");
                    }
                }
                case UNLIT_SUN -> {
                    if (eyeOpenWindow) {
                        multiplier = 1.55;
                        attacker.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.9f, 1.4f);
                    }
                }
                default -> {}
            }
        }

        double dealt = raw * multiplier;
        // Warded hits deal nothing, so they never feed the adaptation chain.
        if (phaseThree && !warded) dealt = adaptation.phaseThree(plugin, event, attacker, dealt, BossAdaptation.REALM, thaiName(), at.clone().add(0, 3, 0));
        if (warded) {
            // Phase wall: the hit lands visually but the boss cannot be burst through its phase skill.
            if (Bukkit.getCurrentTick() % 20 == 0 || !lastWardNotice.containsKey(attacker.getUniqueId())
                    || Bukkit.getCurrentTick() - lastWardNotice.get(attacker.getUniqueId()) > 40) {
                lastWardNotice.put(attacker.getUniqueId(), Bukkit.getCurrentTick());
                attacker.sendActionBar(Component.text("§e🛡 " + thaiName() + " กำลังปลดปล่อยพลังเฟส — เกราะเฟสกันดาเมจจนกว่าสกิลจะจบ!"));
            }
            attacker.playSound(at, Sound.ITEM_SHIELD_BLOCK, 0.8f, 0.7f);
            dealt = 0.0;
        }
        // Phase floors: no burst can skip a phase; the transition (and its wall) always plays first.
        double floor = !phaseTwo ? maxHealth * 0.65 : !phaseThree ? maxHealth * 0.30 : 0.0;
        dealt = Math.min(dealt, Math.max(0.0, health - floor));
        health = Math.max(0.0, health - dealt);
        boolean nativeSmash = realMelee(event, attacker) && event.getEntity() == carrier
            && event.getDamageSource().getDamageType() == DamageType.MACE_SMASH;
        if (nativeSmash) {
            // Let Paper finish its real smash: it resets fall damage and applies
            // Wind Burst only after a successful native hit. Virtual HP above is
            // authoritative; the tiny proxy damage is reset by syncTransforms.
            event.setDamage(0.001);
            event.setCancelled(false);
        }
        if (carrier != null && carrier.isValid()) {
            carrier.playHurtAnimation(0);
            carrier.setHealth(1024.0);
            carrier.setNoDamageTicks(0);
            ArkhaiaHitFeedback.confirm(attacker, carrier, event, dealt, multiplier > 1.15);
        }
        center.getWorld().playSound(at, Sound.ENTITY_IRON_GOLEM_HURT, 0.85f, 0.9f);
        if (bossBar != null) {
            bossBar.name(barTitle());
            bossBar.progress((float) Math.clamp(health / Math.max(1.0, maxHealth), 0.0, 1.0));
        }
        if (health <= 0.0) {
            // Keep the proxy alive until Paper finishes the smash (including a
            // killing blow), then remove it and issue rewards on the next tick.
            if (nativeSmash) Bukkit.getScheduler().runTask(plugin, this::defeat);
            else defeat();
        }
    }

    /** Skyfall: a Mace dive from a perch shakes off the hunt and the diver's Realm Brand. */
    private void skyfall(Player diver) {
        brands.remove(diver.getUniqueId());
        diver.sendActionBar(Component.text("§b✦ ดิ่งฟ้าฟาด! หลุดจากการล่าและล้างตราแห่งมิติ"));
        World w = center.getWorld();
        w.playSound(at, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.4f, 0.8f);
        var dust = new Particle.DustOptions(kindColor(), 1.7f);
        for (int i = 0; i < 28; i++) {
            double a = i * (2 * Math.PI / 28);
            w.spawnParticle(Particle.DUST, at.clone().add(Math.cos(a) * 3.4, 0.3, Math.sin(a) * 3.4), 1, 0, 0, 0, 0, dust, true);
        }
        w.spawnParticle(Particle.END_ROD, at.clone().add(0, 1.2, 0), 24, 1.2, 0.8, 1.2, 0.12);
    }

    private void defeat() {
        if (dead) return;
        dead = true;
        fighting = false;
        clearActiveNodes();
        brands.clear();
        hotZones.clear();
        for (RubbleStrike s : rubbleStrikes) if (s.boulder != null) s.boulder.remove();
        rubbleStrikes.clear();
        activeRings.clear();
        hideBossBar();
        World w = center.getWorld();
        w.playSound(at, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 1.1f);
        w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.8f);
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, at.clone().add(0, 2.0, 0), 80, 1.4, 1.5, 1.4, 0.2);
        closeEntities();
        onDefeat.accept(new Defeat(kind, site.kind(), Set.copyOf(contributors)));
    }

    // =========================================================================
    // TELEGRAPH & DAMAGE HELPERS
    // =========================================================================

    private boolean dealBossDamage(Player player, double baseAmount, DamageType type) {
        return dealBossDamage(player, baseAmount, type, 2.8, 0.5, true);
    }

    private boolean dealHeavyDamage(Player player, double baseAmount, DamageType type) {
        return dealBossDamage(player, baseAmount, type, 3.1, 0.6, true);
    }

    private boolean dealRepeatedDamage(Player player, double baseAmount, DamageType type) {
        return dealBossDamage(player, baseAmount, type, 2.0, 0.3, true);
    }

    private boolean dealLingeringDamage(Player player, double baseAmount, DamageType type) {
        return dealBossDamage(player, baseAmount, type, 1.5, 0.15, false);
    }

    /**
     * Magic skips armor points but Protection still reduces it, and late-game Protection (Limit Break included)
     * caps out at a few percent. So a slice of every magic hit also lands as a separate sonic "rend" that
     * ignores armor and Protection (Resistance, absorption and Totems still apply). Each landed strike adds
     * a Realm Brand stack: +10% damage from this boss per stack, up to 5, lapsing 7s after the last hit.
     */
    private boolean dealBossDamage(Player player, double baseAmount, DamageType type, double magicPower, double rendRatio, boolean brand) {
        if (player == null || !player.isOnline() || player.isDead() || carrier == null || !carrier.isValid()) return false;
        boolean magic = type == DamageType.INDIRECT_MAGIC;
        double scale = damageScale() * brandMultiplier(player)
            * (phaseThree ? BossAdaptation.damageMultiplier(plugin, BossAdaptation.REALM) : 1.0);
        double scaled = Math.max(1.0, baseAmount * (magic ? magicPower : 1.0) * scale);
        boolean landed = BossDamage.hit(player, scaled, source(type), true);
        double rend = magic ? baseAmount * rendRatio * rendScale() * scale : 0.0;
        if (rend >= 0.5 && player.isOnline() && !player.isDead()) {
            // The rend is its own impact, so the magic hit's invulnerability frames must not swallow it
            player.setNoDamageTicks(0);
            landed |= BossDamage.hit(player, rend, source(DamageType.SONIC_BOOM), true);
        }
        if (landed && brand) addBrand(player);
        return landed;
    }

    private DamageSource source(DamageType type) {
        return DamageSource.builder(type)
            .withCausingEntity(carrier)
            .withDirectEntity(carrier)
            .withDamageLocation(at)
            .build();
    }

    private double rendScale() {
        double percent = plugin.getConfig().getDouble("dimensional-sanctuaries.bosses.rend-percent", 100.0);
        return Double.isFinite(percent) ? Math.clamp(percent / 100.0, 0.0, 5.0) : 1.0;
    }

    private double brandMultiplier(Player player) {
        Brand b = brands.get(player.getUniqueId());
        return b == null || b.expires < tick ? 1.0 : 1.0 + 0.1 * b.stacks;
    }

    private void addBrand(Player player) {
        Brand b = brands.computeIfAbsent(player.getUniqueId(), id -> new Brand());
        if (b.expires < tick) b.stacks = 0;
        b.stacks = Math.min(MAX_BRAND, b.stacks + 1);
        b.expires = tick + BRAND_TICKS;
        if (b.stacks >= 2) {
            player.sendActionBar(Component.text("§c✦ ตราแห่งมิติ x" + b.stacks + " — โดนบอสแรงขึ้น +" + b.stacks * 10
                + "% · หลบให้พ้น " + BRAND_TICKS / 20 + " วินาทีเพื่อล้างตรา"));
        }
    }

    private void knockback(Player p, Location from, double horizontal, double vertical) {
        Vector v = p.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (v.lengthSquared() < 1e-4) v = new Vector(0, 0, 1);
        v.normalize().multiply(horizontal).setY(vertical);
        p.setVelocity(v);
    }

    private void drawCircleTelegraph(Location c, double radius, Color color, Particle accent) {
        World w = c.getWorld();
        var dust = new Particle.DustOptions(color, 1.4f);
        int points = Math.max(16, (int) Math.round(radius * 5));
        for (int i = 0; i < points; i++) {
            double a = i * (2 * Math.PI / points);
            w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * radius, 0.18, Math.sin(a) * radius), 1, 0, 0, 0, 0, dust, true);
        }
        if (accent != null && tick % 2 == 0) {
            w.spawnParticle(accent, c.clone().add(0, 0.25, 0), 3, radius * 0.4, 0.1, radius * 0.4, 0.01);
        }
    }

    private void drawLineTelegraphWithSky(Location start, Vector dir, double length, double width, Color color, boolean skyWarning) {
        World w = start.getWorld();
        var dust = new Particle.DustOptions(color, 1.35f);
        Vector perp = new Vector(-dir.getZ(), 0, dir.getX()).normalize();
        for (double d = 1.5; d <= length; d += 1.8) {
            Location mid = start.clone().add(dir.clone().multiply(d)).add(0, 0.18, 0);
            w.spawnParticle(Particle.DUST, mid, 1, 0, 0, 0, 0, dust, true);
            w.spawnParticle(Particle.DUST, mid.clone().add(perp.clone().multiply(width)), 1, 0, 0, 0, 0, dust, true);
            w.spawnParticle(Particle.DUST, mid.clone().add(perp.clone().multiply(-width)), 1, 0, 0, 0, 0, dust, true);
            if (skyWarning && ((int) d) % 4 == 0) {
                // Overhead markers up to Y+10 so Wind Charge + Mace airborne players see the fissure lanes from above
                for (double h = 3.0; h <= 9.0; h += 3.0) {
                    w.spawnParticle(Particle.DUST, mid.clone().add(0, h, 0), 1, 0, 0, 0, 0, dust, true);
                }
            }
        }
    }

    private static Vector directionFromYaw(float yawDeg) {
        double rad = Math.toRadians(yawDeg);
        return new Vector(-Math.sin(rad), 0, Math.cos(rad)).normalize();
    }

    private static Vector vectorFromYawRad(double yawRad) {
        return new Vector(-Math.sin(yawRad), 0, Math.cos(yawRad)).normalize();
    }

    private static double wrapRadians(double angle) {
        while (angle <= -Math.PI) angle += Math.PI * 2;
        while (angle > Math.PI) angle -= Math.PI * 2;
        return angle;
    }

    private static double distanceToSegment2D(Location p, Location start, Vector unitDir, double length) {
        double vx = p.getX() - start.getX();
        double vz = p.getZ() - start.getZ();
        double proj = Math.clamp(vx * unitDir.getX() + vz * unitDir.getZ(), 0.0, length);
        double cx = start.getX() + unitDir.getX() * proj;
        double cz = start.getZ() + unitDir.getZ() * proj;
        return Math.hypot(p.getX() - cx, p.getZ() - cz);
    }

    private void clampToArena(Location loc) {
        double dx = loc.getX() - center.getX(), dz = loc.getZ() - center.getZ();
        double d = Math.hypot(dx, dz), maxR = Math.max(4.0, arena.radius() - 3.0);
        if (d > maxR) {
            loc.setX(center.getX() + dx * (maxR / d));
            loc.setZ(center.getZ() + dz * (maxR / d));
        }
    }

    private void broadcast(List<Player> players, String msg) {
        for (Player p : players) plugin.message(p, msg);
    }

    private void clearActiveNodes() {
        for (DestructibleNode n : activeNodes) n.remove();
        activeNodes.clear();
    }

    private void hideBossBar() {
        if (bossBar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(bossBar);
            bossBar = null;
        }
    }

    private void closeEntities() {
        if (endAntiAir != null) endAntiAir.reset();
        if (netherAntiAir != null) netherAntiAir.reset();
        if (perchHunt != null) perchHunt.reset();
        clearActiveNodes();
        for (RubbleStrike s : rubbleStrikes) if (s.boulder != null) s.boulder.remove();
        rubbleStrikes.clear();
        hotZones.clear();
        activeRings.clear();
        if (rig != null) {
            rig.close();
            rig = null;
        }
        if (aura != null) {
            aura.close();
            aura = null;
        }
        if (bedrockCarrier != null) {
            if (plugin.arkhaiaBedrock() != null) plugin.arkhaiaBedrock().unregister(bedrockCarrier.getUniqueId());
            bedrockCarrier.remove();
            bedrockCarrier = null;
        }
        if (carrier != null) {
            carrier.remove();
            carrier = null;
        }
        if (anchor != null) {
            anchor.remove();
            anchor = null;
        }
    }

    @Override
    public void close() {
        hideBossBar();
        closeEntities();
    }
}
