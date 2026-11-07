import com.example.forge.Charm;
import com.example.forge.CharmEffects;
import com.example.forge.CharmService;
import com.example.forge.ForgePlugin;
import com.example.forge.Rarity;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Captures real outgoing particle packets while the real CharmEffects tick and combat handlers run. */
public final class ForgeCharmParticleProbe extends JavaPlugin {
    private final List<String> passed = new ArrayList<>();
    private final List<String> metrics = new ArrayList<>();
    private final List<Entity> spawned = new ArrayList<>();
    private ForgePlugin forge;
    private CharmService charms;
    private CharmEffects effects;
    private Method tickMethod, stateMethod;
    private World world;
    private Location home;
    private Actor owner, observer;
    private boolean snapshot, finished;
    private String context = "";

    private record Actor(Player player, Recorder recorder) {}

    private static final class Recorder extends ServerGamePacketListenerImpl {
        final List<ClientboundLevelParticlesPacket> particles = new ArrayList<>();
        Recorder(MinecraftServer server, Connection connection, ServerPlayer actor, GameProfile profile) {
            super(server, connection, actor, CommonListenerCookie.createInitial(profile, false));
        }
        @Override public boolean hasClientLoaded() { return true; }
        @Override public void send(Packet<?> packet) {
            if (packet instanceof ClientboundLevelParticlesPacket particle) particles.add(particle);
        }
        int total() { return particles.stream().mapToInt(packet -> Math.max(1, particleCount(packet))).sum(); }
    }

    @Override public void onEnable() {
        snapshot = Boolean.getBoolean("forge.particle.snapshot");
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                begin();
                passiveDensity();
                treasureMarkers();
                familiarCombat();
                orbitCombat();
                legionCombat();
                if (!snapshot) disabledPassiveParticles();
                finish("PASS " + passed.size() + " checks\n" + String.join("\n", metrics)
                    + "\n" + String.join("\n", passed) + "\n");
            } catch (Throwable error) {
                StringWriter detail = new StringWriter();
                error.printStackTrace(new PrintWriter(detail));
                finish("FAIL " + detail + "\n" + String.join("\n", metrics)
                    + "\nPassed before failure:\n" + String.join("\n", passed));
            }
        }, 40);
    }

    private void begin() throws Exception {
        forge = (ForgePlugin) Bukkit.getPluginManager().getPlugin("EvergardenForge");
        check(forge != null && forge.isEnabled(), "Forge enabled");
        charms = forge.charms();
        effects = forge.charmEffects();
        ((BukkitTask) get(effects, "ticker")).cancel(); // Test drives complete server ticks synchronously.
        tickMethod = CharmEffects.class.getDeclaredMethod("tick");
        tickMethod.setAccessible(true);
        stateMethod = CharmEffects.class.getDeclaredMethod("state", Player.class);
        stateMethod.setAccessible(true);
        world = Bukkit.getWorlds().getFirst();
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setStorm(false);
        world.setTime(18000);
        Location spawn = world.getSpawnLocation();
        int x = spawn.getBlockX() + 20, z = spawn.getBlockZ() + 20;
        home = new Location(world, x + .5, world.getHighestBlockYAt(x, z) + 1, z + .5);
        world.getChunkAt(home).addPluginChunkTicket(forge);
        owner = actor("CharmVisual", home);
        observer = actor("CharmViewer", home.clone().add(4, 0, 0));
        check(owner.player().isOnline() && observer.player().isOnline(), "wearer and nearby observer are real online server players");
    }

    private Actor actor(String name, Location at) {
        MinecraftServer server = MinecraftServer.getServer();
        var level = ((CraftWorld) world).getHandle();
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        ServerPlayer actor = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        connection.channel = new EmbeddedChannel();
        connection.address = new InetSocketAddress("127.0.0.1", 1);
        Recorder recorder = new Recorder(server, connection, actor, profile);
        actor.connection = recorder;
        actor.setPos(at.getX(), at.getY(), at.getZ());
        actor.setOnGround(true);
        server.getPlayerList().getPlayers().add(actor);
        server.getPlayerList().getPlayersByUUID().put(profile.id(), actor);
        level.addNewPlayer(actor);
        Player player = actor.getBukkitEntity();
        player.setGravity(false);
        player.setGameMode(GameMode.SURVIVAL);
        player.setInvulnerable(false);
        return new Actor(player, recorder);
    }

    private void passiveDensity() throws Exception {
        wear(Charm.ORBIT, Charm.STAR_FAMILIAR);
        ticks(20);
        density("Orbit + Star Familiar", 108, 32);
        check(owner.recorder().particles.stream().noneMatch(ClientboundLevelParticlesPacket::alwaysShow),
            "passive particles respect the client's particle visibility settings");
        if (!snapshot) {
            check(owner.recorder().particles.stream().allMatch(packet -> particleCount(packet) <= 1),
                "Orbit + Star Familiar emit single passive points instead of dense clouds");
        }

        wear(Charm.ORBIT, Charm.SOUL_LEGION);
        Object state = state();
        seedSpirits(state, 4, home.clone().add(0, 1.7, 0));
        ticks(20);
        density("Orbit + four Soul Legion spirits", 235, 70);
        @SuppressWarnings("unchecked") List<Object> spirits = (List<Object>) get(state, "spirits");
        check(spirits.size() == 4, "all four Divine spirits remain alive during visual throttling");
        check(((Location) get(spirits.getFirst(), "at")).distance(home.clone().add(0, 1.7, 0)) > .1,
            "Soul Legion spirits keep moving between visual frames");

        wear(Charm.OVERCLOCK);
        state = state();
        long until = System.currentTimeMillis() + 10000;
        set(state, "overclockUntil", until);
        ticks(20);
        density("Overclock active aura", 24, 7);
        check(((Number) get(state, "overclockUntil")).longValue() == until,
            "reducing the Overclock aura does not shorten its active time");
        set(state, "overclockUntil", System.currentTimeMillis() - 1);
        ticks(1);
        check(((Number) get(state, "overclockUntil")).longValue() == 0,
            "Overclock still expires between visual frames");
    }

    private void treasureMarkers() throws Exception {
        wear(Charm.EYE_OF_ABYSS);
        Object state = state();
        // Seed discovery results in reverse distance order to exercise the visible-marker selection.
        // All discoveries stay available for the ability's real treasure count/navigation.
        set(state, "eyeAt", System.currentTimeMillis() + 60000);
        @SuppressWarnings("unchecked") List<Location> treasure = (List<Location>) get(state, "treasure");
        for (int i = 10; i >= 3; i--) treasure.add(home.clone().add(i, 0, 5));
        ticks(20);
        int total = owner.recorder().total();
        metrics.add("METRIC Eye of the Abyss with eight chests: wearer=" + total
            + ", nearby=" + observer.recorder().total() + " particles/20 ticks; previous=240; reduction="
            + Math.round(100.0 * (1.0 - total / 240.0)) + "%");
        check(total > 0, "Eye of the Abyss still marks discovered treasure");
        check(observer.recorder().total() == 0, "Eye of the Abyss treasure markers stay private to their wearer");
        check(treasure.size() == 8, "Eye of the Abyss retains all eight discoveries for navigation");
        if (!snapshot) {
            check(total <= 30, "Eye of the Abyss reduces crowded treasure particles by at least 87%");
            check(owner.recorder().particles.stream().allMatch(packet -> particleCoordinate(packet, "x") <= home.getX() + 5.6),
                "Eye of the Abyss draws its nearest three treasure markers");
            check(owner.recorder().particles.stream().allMatch(packet -> particleCoordinate(packet, "y") <= home.getY() + 4),
                "Eye of the Abyss uses low markers instead of twelve-block pillars");
        }
    }

    private void density(String name, int oldPerSecond, int maximum) {
        int total = owner.recorder().total();
        metrics.add("METRIC " + name + ": wearer=" + total + ", nearby=" + observer.recorder().total()
            + " particles/20 ticks; previous=" + oldPerSecond + "; reduction="
            + Math.round(100.0 * (1.0 - total / (double) oldPerSecond)) + "%");
        check(total > 0, name + " remains visible to its wearer");
        check(total == observer.recorder().total(), name + " has the same reduced density for a nearby observer");
        if (!snapshot) check(total <= maximum, name + " reduces passive particles by at least 70%: " + total);
    }

    private void familiarCombat() throws Exception {
        wear(Charm.STAR_FAMILIAR);
        Husk target = target(home.clone().add(3, 0, 0));
        ticks(1); // First tick has no passive visual due, but the familiar can already attack.
        near(target.getHealth(), 95, "Star Familiar still deals Divine damage on a nonvisual tick");
        long next = ((Number) get(state(), "familiarAt")).longValue();
        check(next >= System.currentTimeMillis() + 1300 && next <= System.currentTimeMillis() + 1500,
            "Star Familiar keeps its 1.5 second attack interval");
        ticks(19);
        near(target.getHealth(), 95, "extra visual ticks do not bypass the familiar cooldown");
        check(owner.recorder().total() > 0, "Star Familiar attack beam remains visible");
        target.remove();
    }

    @SuppressWarnings("removal")
    private void orbitCombat() throws Exception {
        wear(Charm.ORBIT);
        Husk target = target(home.clone().add(0, 0, 0));
        Method point = CharmEffects.class.getDeclaredMethod("orbitPoint", Player.class, int.class, long.class);
        point.setAccessible(true);
        Location at = (Location) point.invoke(effects, owner.player(), 0, System.currentTimeMillis());
        target.teleport(at.clone().subtract(0, .8, 0));
        ticks(4);
        near(target.getHealth(), 96, "Orbit contact still deals Divine damage on tick four");
        ticks(16);
        near(target.getHealth(), 96, "Orbit retains its one second contact damage cooldown");
        target.remove();

        long[] charges = (long[]) get(state(), "orbitReady");
        Arrays.fill(charges, 0);
        for (int i = 0; i < 4; i++) {
            Arrow arrow = world.spawn(home.clone().add(0, 1, 2), Arrow.class);
            spawned.add(arrow);
            EntityDamageByEntityEvent hit = new EntityDamageByEntityEvent(arrow, owner.player(),
                EntityDamageEvent.DamageCause.PROJECTILE, DamageSource.builder(DamageType.ARROW).build(), 2);
            Bukkit.getPluginManager().callEvent(hit);
            check(hit.isCancelled() == (i < 3), "Orbit projectile " + (i + 1) + " consumes the correct number of star charges");
            if (i < 3) {
                check(!arrow.isValid(), "Orbit removes blocked projectile " + (i + 1));
                check(charges[i] >= System.currentTimeMillis() + 9800, "Orbit star " + (i + 1) + " retains its ten second recharge");
            }
            arrow.remove();
        }
    }

    private void legionCombat() throws Exception {
        wear(Charm.SOUL_LEGION);
        Husk target = target(home.clone().add(3, 0, 0));
        Object state = state();
        seedSpirits(state, 1, target.getLocation().add(0, 1.2, 0));
        ticks(2);
        near(target.getHealth(), 96, "Soul Legion still attacks for four damage on a nonvisual tick");
        @SuppressWarnings("unchecked") List<Object> spirits = (List<Object>) get(state, "spirits");
        long next = ((Number) get(spirits.getFirst(), "nextHit")).longValue();
        check(next >= System.currentTimeMillis() + 600 && next <= System.currentTimeMillis() + 800,
            "Soul Legion retains its 800 ms attack interval");
        ticks(18);
        near(target.getHealth(), 96, "Soul Legion visuals do not bypass its attack cooldown");
        set(state, "legionUntil", System.currentTimeMillis() - 1);
        ticks(2);
        check(spirits.isEmpty(), "Soul Legion still expires independently of visual cadence");
        target.remove();
    }

    private void disabledPassiveParticles() throws Exception {
        forge.getConfig().set("charms.visuals.passive-particles", false);
        context = "passive-particles=false: ";
        try {
            wear(Charm.ORBIT, Charm.STAR_FAMILIAR);
            ticks(20);
            check(owner.recorder().total() == 0 && observer.recorder().total() == 0,
                "Orbit and Star Familiar stop idle particles for wearer and neighbours");

            wear(Charm.ORBIT, Charm.SOUL_LEGION);
            seedSpirits(state(), 4, home.clone().add(0, 1.7, 0));
            ticks(20);
            check(owner.recorder().total() == 0 && observer.recorder().total() == 0,
                "all four Soul Legion spirits stop idle particles for wearer and neighbours");

            wear(Charm.OVERCLOCK);
            set(state(), "overclockUntil", System.currentTimeMillis() + 10000);
            ticks(20);
            check(owner.recorder().total() == 0 && observer.recorder().total() == 0,
                "Overclock stops idle particles for wearer and neighbours");

            // Run the real attacks again with the cosmetic switch disabled: timing, damage,
            // Orbit projectile protection and the familiar's attack beam must all survive.
            familiarCombat();
            orbitCombat();
            legionCombat();
        } finally {
            context = "";
            forge.getConfig().set("charms.visuals.passive-particles", true);
        }
    }

    private Husk target(Location at) {
        Husk target = world.spawn(at, Husk.class, mob -> {
            mob.setAI(false);
            mob.setGravity(false);
            mob.setPersistent(true);
            mob.setRemoveWhenFarAway(false);
            mob.getEquipment().clear();
            mob.getAttribute(Attribute.MAX_HEALTH).setBaseValue(100);
            mob.getAttribute(Attribute.ARMOR).setBaseValue(0);
            mob.setHealth(100);
        });
        spawned.add(target);
        return target;
    }

    private void seedSpirits(Object state, int count, Location at) throws Exception {
        Class<?> type = Class.forName("com.example.forge.CharmEffects$Spirit");
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        @SuppressWarnings("unchecked") List<Object> spirits = (List<Object>) get(state, "spirits");
        spirits.clear();
        for (int i = 0; i < count; i++) {
            Object spirit = constructor.newInstance();
            set(spirit, "at", at.clone());
            spirits.add(spirit);
        }
        set(state, "legionUntil", System.currentTimeMillis() + 15000);
    }

    private void wear(Charm... worn) throws Exception {
        while (!charms.wornItems(owner.player()).isEmpty()) charms.unequip(owner.player(), 0);
        @SuppressWarnings("unchecked") Map<UUID, ?> states = (Map<UUID, ?>) get(effects, "states");
        states.clear();
        set(effects, "tick", 0L);
        for (Charm charm : worn) {
            check(charms.equip(owner.player(), charms.item(charm, Rarity.MYTHIC)) == CharmService.EquipResult.OK,
                "equip " + charm.title + " with normal six-slot capacity");
        }
        owner.recorder().particles.clear();
        observer.recorder().particles.clear();
    }

    private Object state() throws Exception { return stateMethod.invoke(effects, owner.player()); }
    private void ticks(int count) throws Exception { for (int i = 0; i < count; i++) tickMethod.invoke(effects); }
    private static int particleCount(ClientboundLevelParticlesPacket packet) {
        // The 26.3 server made the 26.2 packet a record; support both cached templates.
        try {
            Method accessor;
            try { accessor = packet.getClass().getMethod("count"); }
            catch (NoSuchMethodException older) { accessor = packet.getClass().getMethod("getCount"); }
            return ((Number) accessor.invoke(packet)).intValue();
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private static double particleCoordinate(ClientboundLevelParticlesPacket packet, String axis) {
        try {
            Method accessor;
            try { accessor = packet.getClass().getMethod(axis); }
            catch (NoSuchMethodException older) { accessor = packet.getClass().getMethod("get" + axis.toUpperCase()); }
            return ((Number) accessor.invoke(packet)).doubleValue();
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private static Object get(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
    private static void set(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(context + message);
        passed.add(context + message);
    }
    private void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-6, message + ": " + actual + " (expected " + expected + ")");
    }
    private void finish(String result) {
        if (finished) return;
        finished = true;
        spawned.forEach(entity -> { if (entity.isValid()) entity.remove(); });
        try { Files.writeString(Path.of("forge-charm-particles-result.txt"), result); }
        catch (Exception error) { getLogger().severe("Cannot write result: " + error); }
        getLogger().info(result.lines().findFirst().orElse(result));
        Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 2);
    }
}
