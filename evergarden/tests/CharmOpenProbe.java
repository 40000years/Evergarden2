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
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Opens real Tier VI-VIII Vaults 100 times per tier through RelicService.rollVaultRewards(site, player), so the
 * hoard's dry-streak counter (player PDC) runs exactly as it does on a server. Reports how many charms came out
 * and the longest run of openings without one.
 */
public final class CharmOpenProbe extends JavaPlugin {
    private static final int OPENINGS = 100;

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            String text;
            try {
                text = run();
            } catch (Throwable error) {
                java.io.StringWriter trace = new java.io.StringWriter();
                error.printStackTrace(new java.io.PrintWriter(trace));
                getLogger().severe(trace.toString());
                text = "FAIL " + error;
            }
            try {
                Files.writeString(Path.of("charm-open-result.txt"), text);
            } catch (Exception ignored) {
            }
            Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 20L);
        }, 100L);
    }

    private String run() throws Exception {
        Plugin garden = Bukkit.getPluginManager().getPlugin("Evergarden");
        Plugin forge = Bukkit.getPluginManager().getPlugin("EvergardenForge");
        if (garden == null || !garden.isEnabled()) return "FAIL Evergarden is not enabled";
        if (forge == null || !forge.isEnabled()) return "FAIL EvergardenForge is not enabled, so no charm can drop";
        ClassLoader loader = garden.getClass().getClassLoader();
        Object relics = call(garden, "relics");
        World world = (World) call(garden, "world");
        Class<?> siteClass = Class.forName("com.example.voidscape.world.DungeonLayout$Site", true, loader);
        Class<?> kindClass = Class.forName("com.example.voidscape.world.DungeonLayout$Kind", true, loader);
        Class<?> api = Class.forName("com.example.forge.ForgeApi", true, forge.getClass().getClassLoader());
        boolean forgeReady = (boolean) call(api, "available");
        String version = forge.getPluginMeta().getVersion();

        StringBuilder report = new StringBuilder();
        int totalCharms = 0;
        Object[][] plan = {{"SANCTUM_DARK", 6, 0.10}, {"SANCTUM_ASTRAL", 7, 0.15}, {"SANCTUM_TIME", 8, 0.22}};
        for (Object[] row : plan) {
            String kind = (String) row[0];
            int tier = (int) row[1];
            double chance = (double) row[2];
            Object site = siteClass.getConstructor(kindClass, int.class, int.class, long.class, int.class)
                .newInstance(Enum.valueOf((Class) kindClass, kind), 100, 100, 7L, tier);
            Player opener = fake(world, world.getSpawnLocation());
            int charms = 0, longest = 0, dry = 0, items = 0;
            Map<String, Integer> grades = new TreeMap<>();
            Map<String, Integer> temples = new TreeMap<>();
            for (int i = 0; i < OPENINGS; i++) {
                @SuppressWarnings("unchecked")
                List<ItemStack> rewards = (List<ItemStack>) call(relics, "rollVaultRewards", site, opener);
                items += rewards.size();
                String charm = null;
                for (ItemStack item : rewards) {
                    String id = charmOf(item);
                    if (id == null) continue;
                    charm = id;
                    grades.merge(String.valueOf(gradeOf(item)), 1, Integer::sum);
                }
                if (charm != null) {
                    charms++;
                    totalCharms++;
                    temples.merge(String.valueOf(charm), 1, Integer::sum);
                    dry = 0;
                } else {
                    dry++;
                    longest = Math.max(longest, dry);
                }
            }
            int dryAtEnd = opener.getPersistentDataContainer().getOrDefault(
                new NamespacedKey("voidscape", "hoard_dry"), PersistentDataType.INTEGER, -1);
            report.append(String.format(Locale.ROOT,
                "Tier %d %s: %d openings, charms %d (%.1f%%, base chance %.0f%%), longest dry run %d, items/opening %.2f, stored dry counter %d%n",
                tier, kind, OPENINGS, charms, 100.0 * charms / OPENINGS, chance * 100, longest, items / (double) OPENINGS, dryAtEnd));
            report.append("  grades ").append(grades).append(" charms ").append(temples).append('\n');
        }
        return "PASS " + (OPENINGS * plan.length) + " Vault openings, " + totalCharms + " charms, EvergardenForge "
            + version + " (ForgeApi available " + forgeReady + ")\n" + report;
    }

    private static String charmOf(ItemStack item) {
        return item == null || !item.hasItemMeta() ? null
            : item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey("evergardenforge", "charm"), PersistentDataType.STRING);
    }

    private static String gradeOf(ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey("evergardenforge", "charm_grade"), PersistentDataType.STRING);
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

    private static Player fake(World world, Location at) {
        var server = MinecraftServer.getServer();
        var level = ((CraftWorld) world).getHandle();
        var profile = new GameProfile(UUID.randomUUID(), "CharmRunner");
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
        player.setInvulnerable(false);
        return player;
    }
}
