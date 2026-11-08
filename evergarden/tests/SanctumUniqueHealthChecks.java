import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Sanctum uniques: Evergarden makes one in five Tier VI-VIII mobs an EvergardenForge elite and keeps authority over
 * its numbers. Forge 0.7.3 began re-applying elite multipliers every 10 ticks and rescaling health by percentage,
 * while the sanctum strips those multipliers and clamps health to its own maximum; together they refilled a unique
 * after almost every hit. A real Tier VI encounter, a connectionless player in the arena, real damage over time.
 */
public final class SanctumUniqueHealthChecks extends JavaPlugin {
    private interface Step { void run() throws Exception; }
    private record Timed(long delay, Step step) {}
    private final List<String> passed = new ArrayList<>();
    private final List<Timed> steps = new ArrayList<>();
    private static final NamespacedKey ELITE = new NamespacedKey("evergardenforge", "elite");
    private static final NamespacedKey TARGET_HP = new NamespacedKey("voidscape", "target_hp");

    private Plugin garden, forge;
    private Object dungeons;
    private World world;
    private Class<?> siteClass, kindClass, encounterClass;
    private Player player;
    private Object site, encounter;
    private Mob unique;
    private LivingEntity control;
    private double max, afterHit;
    private int hits;

    private void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        passed.add(name);
        getLogger().info("PASS: " + name);
    }

    @Override
    public void onEnable() {
        plan();
        next(0);
    }

    private void next(int index) {
        if (index >= steps.size()) {
            finish("PASS " + passed.size() + " sanctum unique health checks\n" + String.join("\n", passed));
            return;
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                steps.get(index).step().run();
            } catch (Throwable error) {
                java.io.StringWriter trace = new java.io.StringWriter();
                error.printStackTrace(new java.io.PrintWriter(trace));
                getLogger().severe(trace.toString());
                finish("FAIL " + error + "\npassed before failure: " + passed);
                return;
            }
            next(index + 1);
        }, Math.max(1, steps.get(index).delay()));
    }

    private void finish(String text) {
        try { Files.writeString(Path.of("sanctum-unique-result.txt"), text); } catch (Exception ignored) {}
        Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 20L);
    }

    private void at(long delay, Step step) { steps.add(new Timed(delay, step)); }

    private String describe(LivingEntity mob) {
        var attribute = mob.getAttribute(Attribute.MAX_HEALTH);
        List<String> modifiers = new ArrayList<>();
        for (AttributeModifier modifier : attribute.getModifiers()) modifiers.add(modifier.getKey() + "=" + modifier.getAmount());
        return String.format(Locale.ROOT, "%s %.1f/%.1f base %.1f modifiers %s", mob.getType(), mob.getHealth(), attribute.getValue(),
            attribute.getBaseValue(), modifiers);
    }

    private static boolean forgeModifier(LivingEntity mob, Attribute attribute) {
        var instance = mob.getAttribute(attribute);
        return instance != null && instance.getModifiers().stream().anyMatch(m -> m.getKey().getNamespace().equals("evergardenforge"));
    }

    private void hit(double amount) {
        unique.setNoDamageTicks(0);
        unique.damage(amount, player);
        hits++;
    }

    @SuppressWarnings("unchecked")
    private void plan() {
        at(60, () -> {
            garden = Bukkit.getPluginManager().getPlugin("Evergarden");
            forge = Bukkit.getPluginManager().getPlugin("EvergardenForge");
            check(garden != null && garden.isEnabled() && forge != null && forge.isEnabled(),
                "Evergarden " + garden.getPluginMeta().getVersion() + " and EvergardenForge " + forge.getPluginMeta().getVersion() + " enabled");
            dungeons = call(garden, "dungeons");
            world = (World) call(garden, "world");
            ClassLoader loader = garden.getClass().getClassLoader();
            siteClass = Class.forName("com.example.voidscape.world.DungeonLayout$Site", true, loader);
            kindClass = Class.forName("com.example.voidscape.world.DungeonLayout$Kind", true, loader);
            encounterClass = Class.forName("com.example.voidscape.dungeon.DungeonManager$Encounter", true, loader);
            site = site("SANCTUM_DARK", 30000, 30000, 6);
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                world.getChunkAt((sx(site) >> 4) + dx, (sz(site) >> 4) + dz).addPluginChunkTicket(this);
            player = fake(world, new Location(world, sx(site) + 0.5, 97, sz(site) + 0.5));
            check(player.isOnline(), "a player stands in the Tier VI arena");
        });
        at(5, () -> {
            encounter = encounter(site);
            Set<UUID> uniques = (Set<UUID>) field(encounter, "uniques");
            for (int wave = 0; wave < 16 && uniques.isEmpty(); wave++) call(dungeons, "startWave", encounter, 2);
            for (UUID id : ((Map<UUID, Object>) field(encounter, "mobs")).keySet())
                if (Bukkit.getEntity(id) instanceof Mob mob) mob.setAware(false);   // nobody fights back during the measurement
            for (UUID id : uniques) if (Bukkit.getEntity(id) instanceof Mob mob && mob.isValid() && !(mob instanceof Ravager)) unique = mob;
            check(unique != null, "the Tier VI waves include a Forge unique");
            check(unique.getPersistentDataContainer().has(ELITE, PersistentDataType.STRING), "the unique carries the Forge elite mark");
        });
        // Let both plugins settle: Forge's 10-tick aura and the sanctum's one-second encounter loop.
        at(60, () -> {
            max = unique.getAttribute(Attribute.MAX_HEALTH).getValue();
            check(unique.getHealth() > 0 && Math.abs(unique.getHealth() - max) < 0.01, "the settled unique is at full health: " + describe(unique));
            hit(max * 0.2);
            afterHit = unique.getHealth();
            check(afterHit < max - 1, "a hit for 20% of its health lands: " + describe(unique));
        });
        at(50, () -> check(unique.isValid() && unique.getHealth() <= afterHit + 0.01,
            "2.5 seconds later the damage is still there (no refill): " + String.format(Locale.ROOT, "%.1f -> %.1f", afterHit, unique.getHealth())
                + " · " + describe(unique)));
        for (int i = 0; i < 8; i++) at(15, () -> { if (unique.isValid() && !unique.isDead()) hit(max * 0.25); });
        at(30, () -> check(!unique.isValid() || unique.isDead(), "the unique dies after " + hits + " hits of a quarter of its health"));
        // The sanctum owns a unique's numbers and name; Forge adds its grade, aura, moves and rewards.
        at(1, () -> {
            Map<UUID, Object> mobs = (Map<UUID, Object>) field(encounter, "mobs");
            Set<UUID> uniques = (Set<UUID>) field(encounter, "uniques");
            Mob other = null;
            for (UUID id : uniques) if (Bukkit.getEntity(id) instanceof Mob mob && mob.isValid() && !mob.isDead() && mobs.containsKey(id)) other = mob;
            if (other == null) {
                for (int wave = 0; wave < 16 && other == null; wave++) {
                    call(dungeons, "startWave", encounter, 2);
                    for (UUID id : uniques) if (Bukkit.getEntity(id) instanceof Mob mob && mob.isValid() && !mob.isDead()) other = mob;
                }
            }
            check(other != null, "another unique to inspect");
            unique = other;
            unique.setAware(false);
        });
        at(40, () -> {
            Double target = unique.getPersistentDataContainer().get(TARGET_HP, PersistentDataType.DOUBLE);
            check(!forgeModifier(unique, Attribute.MAX_HEALTH) && !forgeModifier(unique, Attribute.ATTACK_DAMAGE),
                "Forge leaves the sanctum's health and damage numbers alone: " + describe(unique));
            check(target != null && Math.abs(unique.getAttribute(Attribute.MAX_HEALTH).getValue() - target) < 0.01,
                "the unique keeps the sanctum's maximum health (" + target + "): " + describe(unique));
            String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(unique.customName());
            check(name.contains("[Tier VI]") && name.contains("✦"), "the unique keeps the sanctum's grade name: " + name);
        });
        // A Forge elite made from an ordinary mob is still boosted by Forge.
        at(1, () -> {
            World overworld = Bukkit.getWorlds().getFirst();
            control = (LivingEntity) overworld.spawnEntity(overworld.getSpawnLocation().add(4, 0, 4), EntityType.ZOMBIE);
            Class<?> api = Class.forName("com.example.forge.ForgeApi", true, forge.getClass().getClassLoader());
            check((boolean) api.getMethod("makeElite", LivingEntity.class, String.class).invoke(null, control, "RARE"), "an ordinary zombie becomes a RARE elite");
        });
        at(25, () -> {
            check(forgeModifier(control, Attribute.MAX_HEALTH) && control.getAttribute(Attribute.MAX_HEALTH).getValue() > 21
                && Math.abs(control.getHealth() - control.getAttribute(Attribute.MAX_HEALTH).getValue()) < 0.01,
                "an ordinary elite keeps Forge's health multiplier: " + describe(control));
            control.remove();
            ((Map<String, Object>) field(dungeons, "active")).remove(call(site, "id"));
        });
    }

    private static Object call(Object target, String name, Object... args) throws Exception {
        for (Class<?> type = target instanceof Class<?> c ? c : target.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
                boolean fits = true;
                Class<?>[] params = method.getParameterTypes();
                for (int i = 0; i < args.length; i++) {
                    Class<?> p = params[i].isPrimitive() ? box(params[i]) : params[i];
                    if (args[i] != null && !p.isInstance(args[i])) fits = false;
                }
                if (!fits) continue;
                method.setAccessible(true);
                return method.invoke(target instanceof Class<?> ? null : target, args);
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static Class<?> box(Class<?> primitive) {
        return primitive == int.class ? Integer.class : primitive == long.class ? Long.class : primitive == double.class ? Double.class
            : primitive == boolean.class ? Boolean.class : primitive == float.class ? Float.class : primitive;
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object site(String kind, int x, int z, int tier) throws Exception {
        Constructor<?> constructor = siteClass.getConstructor(kindClass, int.class, int.class, long.class, int.class);
        return constructor.newInstance(Enum.valueOf((Class) kindClass, kind), x, z, 7L, tier);
    }

    private int sx(Object site) throws Exception { return (int) call(site, "x"); }
    private int sz(Object site) throws Exception { return (int) call(site, "z"); }

    @SuppressWarnings("unchecked")
    private Object encounter(Object site) throws Exception {
        Constructor<?> constructor = encounterClass.getDeclaredConstructor(siteClass, int.class);
        constructor.setAccessible(true);
        Object enc = constructor.newInstance(site, 5);
        ((Map<String, Object>) field(dungeons, "active")).put((String) call(site, "id"), enc);
        return enc;
    }

    private static Player fake(World world, Location at) {
        var server = MinecraftServer.getServer();
        var level = ((CraftWorld) world).getHandle();
        var profile = new GameProfile(UUID.randomUUID(), "UniqueHunter");
        var actor = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        connection.channel = new EmbeddedChannel();
        connection.address = new InetSocketAddress("127.0.0.1", 1);
        actor.connection = new ServerGamePacketListenerImpl(server, connection, actor, CommonListenerCookie.createInitial(profile, false)) {
            @Override public boolean hasClientLoaded() { return true; }
        };
        actor.setPos(at.getX(), at.getY(), at.getZ());
        server.getPlayerList().getPlayers().add(actor);
        server.getPlayerList().getPlayersByUUID().put(profile.id(), actor);
        level.addNewPlayer(actor);
        Player player = actor.getBukkitEntity();
        player.setGravity(false);
        player.setGameMode(GameMode.SURVIVAL);
        player.setInvulnerable(true);   // the waves are asleep; this only guards against stray fire
        return player;
    }
}
